"""Thor rework R2b: the clip acceptance scan, the same as the Java tests (ThorRig.depth / ThorClipMeshTest).

Every cube of a moving part is sampled on a 0.35 px grid (cube-local, boundary included) and the
deepest sample inside a fixed part's cube (min distance to its faces) is the penetration in px. No
baseline, no joint trim: what the player sees. The pairs (ThorClipMesh in the tests):

* arms (upper arm, forearm, fist, both sides) into the torso (body, belly, head, beard, thighs,
  shins); the upper arm against the body is the shoulder joint itself and is skipped (R1);
* Mjölnir in the right fist into the torso and the left arm; the left fist may close on the
  handle group (shaft, bands, wrap, pommel, strap), the right forearm may touch the pommel group;
* the belt hammer (when not in the fist) into both arms and both legs; the right fist may close on
  its handle group (the draw / holster grab);
* the shins into the belly and the body (deep squats).

Tolerance 0.5 px (the sampling grid plus overlay shells touching), as R1's mesh scan.
"""
import math

import numpy as np

import thor_rig as R

STEP = 0.35
TOLERANCE = 0.5
TORSO = ("body", "belly", "head", "beard", "right_leg", "left_leg", "right_shin", "left_shin")
ARMS = ("right_arm", "right_forearm", "right_hand", "left_arm", "left_forearm", "left_hand")
LEGS = ("right_leg", "right_shin", "left_leg", "left_shin")
# Mjölnir's handle group: the shaft, its bands, the leather wrap, the pommel and the strap (z > -5.7 in the hand geo).
HANDLE = ("handle", "handle_plains", "bottom", "side1", "side2") + tuple("cube_%d" % i for i in range(5, 8)) \
    + tuple("cube_%d" % i for i in range(10, 32))
POMMEL = tuple(b for b in HANDLE if b not in ("handle",) + tuple("cube_%d" % i for i in range(5, 8)))

_GRID = {}


def _grid(lo, hi, step=STEP):
    key = (tuple(np.round(lo, 5)), tuple(np.round(hi, 5)), step)
    g = _GRID.get(key)
    if g is None:
        axes = []
        for a in range(3):
            n = max(1, int(math.ceil((hi[a] - lo[a]) / step - 1e-9)))
            axes.append(lo[a] + (hi[a] - lo[a]) * np.arange(n + 1) / n)
        g = np.stack(np.meshgrid(*axes, indexing="ij"), -1).reshape(-1, 3)
        g = np.hstack([g, np.ones((len(g), 1))])
        _GRID[key] = g
    return g


def cube_frames(model, bone, frame):
    """[(4x4 cube frame, lo, hi)] of one bone's own cubes under its model-space frame."""
    out = []
    for lo, hi, cp, cr in model.bones[bone].cubes:
        m = frame
        if np.any(cr != 0):
            m = frame @ R.trans(cp) @ R.mat4(R.rzyx(cr)) @ R.trans(-cp)
        out.append((m, lo, hi))
    return out


def points(model, bone, frame, step=STEP):
    """Model-space samples (N, 3) of one bone's cubes."""
    ps = [(_grid(lo, hi, step) @ m.T)[:, :3] for m, lo, hi in cube_frames(model, bone, frame)]
    return np.vstack(ps) if ps else np.zeros((0, 3))


class Part:
    """A posed part: its sample points and its cubes (for being penetrated)."""

    def __init__(self, label, model, bone, frame, step=STEP):
        self.label, self.bone, self.step = label, bone, step
        self.cubes = cube_frames(model, bone, frame)
        self._pts = None
        self.model, self.frame = model, frame
        if self.cubes:
            cs = []
            for m, lo, hi in self.cubes:
                for x in (lo[0], hi[0]):
                    for y in (lo[1], hi[1]):
                        for z in (lo[2], hi[2]):
                            cs.append((m @ np.array([x, y, z, 1.0]))[:3])
            cs = np.array(cs)
            self.lo, self.hi = cs.min(0), cs.max(0)
        else:
            self.lo = self.hi = None

    @property
    def pts(self):
        if self._pts is None:
            self._pts = points(self.model, self.bone, self.frame, self.step)
        return self._pts

    @property
    def id(self):
        return self.label + ":" + self.bone


def depth(moving, fixed):
    """Deepest penetration (px) of the moving part's samples into the fixed part's cubes."""
    if moving.lo is None or fixed.lo is None:
        return 0.0
    if np.any(moving.lo > fixed.hi) or np.any(fixed.lo > moving.hi):
        return 0.0
    best = 0.0
    pts = None
    for m, lo, hi in fixed.cubes:
        if pts is None:
            pts = np.hstack([moving.pts, np.ones((len(moving.pts), 1))])
        inv = np.linalg.inv(m)
        loc = (pts @ inv.T)[:, :3]
        d = np.minimum(loc - lo, hi - loc).min(axis=1)
        v = float(d.max()) if len(d) else 0.0
        best = max(best, v)
    return best


def body_parts(pose, step=STEP):
    return {n: Part("body", pose.model, n, pose.frame(n), step) for n in pose.model.order}


def hammer_parts(hammer_model, frame, label, step=STEP):
    """Mjölnir's subtree (the hand / belt geo's 'mjolnir' bone) drawn with that bone's pivot on `frame`."""
    hp = R.Pose(hammer_model)
    root = "mjolnir"
    base = frame @ R.trans(-hammer_model.bones[root].pivot)
    out = {}
    frames = {}
    for n in hammer_model.subtree(root):
        b = hammer_model.bones[n]
        parent = frames.get(b.parent, base) if n != root else base
        frames[n] = parent @ hp.local(n)
        if b.cubes:
            out[n] = Part(label, hammer_model, n, frames[n], step)
    return out


GRAB = 2.5  # px: the right fist this close to the belt hammer's grip point is taking it / putting it back


def scan(pose, hammer_model=None, grip="mjolnir_grip", belt_model=None, belt="mjolnir_belt", tolerance=TOLERANCE, belt_grip=None):
    """All hits [(moving id, fixed id, depth px)] deeper than tolerance, deepest first. belt_grip = the belt
    hammer's grip point in the mjolnir_belt locator frame (where the drawn hammer's grip coincides with it)."""
    body = body_parts(pose)
    hits = []

    def test(m, f, allowed):
        d = depth(m, f)
        if d > allowed:
            hits.append((m.id, f.id, d))

    for limb in ARMS:
        for fixed in TORSO:
            if limb.endswith("_arm") and fixed == "body":
                continue
            test(body[limb], body[fixed], tolerance)
    for shin in ("right_shin", "left_shin"):
        for fixed in ("belly", "body"):
            test(body[shin], body[fixed], tolerance)
    if hammer_model is not None:
        for bone, part in hammer_parts(hammer_model, pose.pivot_frame(grip), "mjolnir").items():
            for fixed in TORSO + ("left_arm", "left_forearm", "left_hand", "right_arm", "right_forearm"):
                if fixed == "left_hand" and bone in HANDLE:
                    continue
                if fixed == "right_forearm" and bone in POMMEL:
                    continue
                test(part, body[fixed], tolerance)
    if belt_model is not None:
        frame = pose.pivot_frame(belt)
        grabbing = belt_grip is not None and float(np.linalg.norm(
            (frame @ np.append(belt_grip, 1.0))[:3] - pose.point(grip))) <= GRAB
        for bone, part in hammer_parts(belt_model, frame, "belt").items():
            for fixed in ARMS + LEGS:
                # The fist closing on the belt hammer's handle holds it as it holds the drawn one.
                if grabbing and fixed == "right_hand" and bone in HANDLE:
                    continue
                if grabbing and fixed == "right_forearm" and bone in POMMEL:
                    continue
                test(part, body[fixed], tolerance)
    hits.sort(key=lambda h: -h[2])
    return hits


def feet(pose):
    """Lowest point (px above the ground) of each shin."""
    out = []
    for shin in ("right_shin", "left_shin"):
        lo = math.inf
        for m, a, b in cube_frames(pose.model, shin, pose.frame(shin)):
            for x in (a[0], b[0]):
                for y in (a[1], b[1]):
                    for z in (a[2], b[2]):
                        lo = min(lo, (m @ np.array([x, y, z, 1.0]))[1])
        out.append(lo)
    return out


def belt_depth(pose, belt_model, fixed=("right_leg", "right_shin"), step=0.7):
    """Deepest penetration (px, coarse grid) of the belt hammer into the given body bones."""
    body = {n: Part("body", pose.model, n, pose.frame(n), step) for n in fixed}
    best = 0.0
    for part in hammer_parts(belt_model, pose.pivot_frame("mjolnir_belt"), "belt", step).values():
        for f in fixed:
            best = max(best, depth(part, body[f]))
    return best
