"""Thor rework R2b: intent keys -> solved, checked, baked Blockbench clips (make_thor_clips.py's engine).

An intent key says what the body does, not bone numbers: pelvis offset, torso lean / twist / side
bend, whole-body spin, feet on the ground, each arm as a direction from its shoulder (yaw, pitch in
the torso frame) and a reach, the hammer's direction and the face of its head. Between keys the
intents are interpolated with the segment's easing and solved by IK every tick, so a swing follows
an arc, the planted feet stay planted, a hand on the handle stays on it, and the per-tick body check
sees exactly what the game plays. The solved bones are baked as linear keys (redundant keys dropped).
"""
import math

import numpy as np

import thor_clipkit as K
import thor_mesh as M
import thor_rig as R

TICK = 1.0 / 20.0
P = K.P
unit = K.unit

# Stance feet (internal px, ankle centres on the ground): a little wider than the rest pose.
FEET = {"r": K.FOOT["r"] + np.array([1.5, 0.0, 0.0]), "l": K.FOOT["l"] + np.array([-1.5, 0.0, 0.0])}
POMMEL_BONES = ("bottom", "cube_10", "cube_11", "cube_12", "cube_13", "cube_18", "cube_19", "cube_20", "cube_21",
                "cube_26", "cube_27", "cube_28", "cube_29", "cube_30", "cube_31", "handle", "handle_plains")
TP_BONES = ("body", "right_arm", "right_forearm", "right_hand", "mjolnir_grip", "left_arm", "left_forearm", "left_hand",
            "right_leg", "right_shin", "left_leg", "left_shin", "mjolnir_belt")
UPPER_BONES = ("body", "right_arm", "right_forearm", "right_hand", "mjolnir_grip", "left_arm", "left_forearm", "left_hand")
POS_BONES = ("body", "right_leg", "left_leg", "mjolnir_belt")
FP_BONES = ("armorRightArm", "right_forearm", "right_hand", "mjolnir_grip", "armorLeftArm", "left_forearm", "left_hand")
FP_LEFT_BONES = ("armorLeftArm", "left_forearm", "left_hand")
FP_POS_BONES = ("armorRightArm", "armorLeftArm")

BELT = R.Model(K.GEO + "/mjolnir_belt.geo.json")


def sph(yaw, pitch):
    """Direction from (yaw, pitch) degrees: yaw + = Thor's right, pitch + = up, (0, 0) = forward."""
    y, p = math.radians(yaw), math.radians(pitch)
    return np.array([math.sin(y) * math.cos(p), math.sin(p), -math.cos(y) * math.cos(p)])


def _belt_grip():
    """The belt hammer's grip (where the drawn hand hammer's grip coincides with it), in the belt locator frame."""
    rest = R.Pose(K.BODY)
    root = BELT.order[1] if BELT.order[0] == "armorBody" else BELT.order[0]
    frame = rest.pivot_frame("mjolnir_belt") @ R.trans(-BELT.bones[root].pivot)
    boxes = {b.bone: b for b in R.boxes("belt", R.Pose(BELT), frame, BELT.subtree(root))}
    handle = boxes["handle"]
    axis = unit(handle.axes[int(np.argmax(handle.half))])
    head = [b for b in R.boxes("belt", R.Pose(BELT), frame, BELT.subtree(root)) if b.bone == "hammer"]
    head_c = head[1].center
    if axis @ (head_c - handle.center) < 0:
        axis = -axis
    long_axis = unit(head[1].axes[int(np.argmax(head[1].half))])
    grip = handle.center - axis * 2.93
    inv = np.linalg.inv(rest.pivot_frame("mjolnir_belt"))
    return (inv @ np.append(grip, 1))[:3], inv[:3, :3] @ axis, inv[:3, :3] @ long_axis


BELT_GRIP, BELT_AXIS, BELT_FACE = _belt_grip()


# ------------------------------------------------------------------------------------------- intents
BASE = {
    "hip": (0.0, 0.0, 0.0), "tor": (0.0, 0.0, 0.0), "spin": 0.0,
    "rft": (0.0, 0.0, 0.0), "lft": (0.0, 0.0, 0.0), "rk": (0.1, 0.0, 1.0), "lk": (-0.1, 0.0, 1.0),
    "ra": (40.0, -82.0, 19.5), "rp": (1.0, -0.5, 0.6), "hm": None, "hf": None, "hr": 0.0, "rw": (0.0, 0.0, 0.0),
    "la": (-40.0, -82.0, 19.5), "lp": (-1.0, -0.5, 0.6), "lw": (0.0, 0.0, 0.0),
    "add": None,
    "bs": 0.0, "bp": (0.0, 0.0, 0.0),
}


def key(base, **kw):
    out = dict(base)
    for k, v in kw.items():
        out[k] = v
    return out


def _lerp(a, b, f):
    if a is None or b is None:
        return b if f >= 0.5 else a
    if isinstance(a, str) or isinstance(b, str) or (isinstance(a, tuple) and a and a[0] == "mix") \
            or (isinstance(b, tuple) and b and b[0] == "mix"):
        if a == b:
            return a
        return ("mix", a, b, f)
    if isinstance(a, dict) or isinstance(b, dict):
        a = a or {}
        b = b or {}
        return {k: tuple(np.asarray(a.get(k, (0, 0, 0)), float) * (1 - f) + np.asarray(b.get(k, (0, 0, 0)), float) * f)
                for k in set(a) | set(b)}
    if isinstance(a, (int, float)):
        return a + (b - a) * f
    return tuple(np.asarray(a, float) * (1 - f) + np.asarray(b, float) * f)


def interpolate(a, b, f, lift=3.0):
    out = {}
    for k in set(a) | set(b):
        out[k] = _lerp(a.get(k), b.get(k), f)
    # A foot that travels lifts off the ground on the way (an arc, not a slide).
    for foot in ("rft", "lft"):
        if a.get("arc") is False or b.get("arc") is False:
            break  # the keys carry the steps themselves (walk / run cycles)
        fa, fb = np.asarray(a[foot], float), np.asarray(b[foot], float)
        d = math.hypot(fb[0] - fa[0], fb[2] - fa[2])
        if d > 2.0:
            v = list(out[foot])
            v[1] += min(lift, 0.4 * d) * math.sin(math.pi * f)
            out[foot] = tuple(v)
    return out


# ------------------------------------------------------------------------------------------- solving
def _target(spec, pose, shoulder, Rt, ham_now=None):
    if isinstance(spec, tuple) and spec and spec[0] == "mix":
        a = _target(spec[1], pose, shoulder, Rt, ham_now)
        b = _target(spec[2], pose, shoulder, Rt, ham_now)
        return a + (b - a) * spec[3]
    if spec == "belt":
        return (pose.pivot_frame("mjolnir_belt") @ np.append(BELT_GRIP, 1))[:3]
    if isinstance(spec, str) and spec.startswith("belt+"):
        # Next to the belt grip: an offset (right, up, forward px) in the torso frame.
        off = P(*[float(v) for v in spec[5:].split(",")])
        return (pose.pivot_frame("mjolnir_belt") @ np.append(BELT_GRIP, 1))[:3] + Rt @ off
    if isinstance(spec, str) and spec.startswith("handle"):
        off = float(spec.split(":")[1]) if ":" in spec else K.HANDLE_SPACING
        return pose.point("mjolnir_grip") - K.hammer_axis(pose) * off
    yaw, pitch, reach = spec
    return pose.point(shoulder) + reach * (Rt @ sph(yaw, pitch))


def _dir(spec, pose, Rt):
    if spec is None:
        return None
    if isinstance(spec, tuple) and spec and spec[0] == "mix":
        a, b = _dir(spec[1], pose, Rt), _dir(spec[2], pose, Rt)
        if a is None or b is None:
            return a if b is None else b
        return slerp(a, b, spec[3])
    if spec == "belt":
        return unit(pose.pivot_frame("mjolnir_belt")[:3, :3] @ BELT_AXIS)
    if spec == "belt_face":
        return unit(pose.pivot_frame("mjolnir_belt")[:3, :3] @ BELT_FACE)
    return unit(Rt @ sph(*spec))


def slerp(a, b, f):
    a, b = unit(a), unit(b)
    c = max(-1.0, min(1.0, float(a @ b)))
    th = math.acos(c)
    if th < 1e-5:
        return a
    if th > math.pi - 1e-3:
        perp = unit(np.cross(a, [0, 1, 0] if abs(a[1]) < 0.9 else [1, 0, 0]))
        return unit(K.axis_angle(perp, th * f) @ a)
    return unit((math.sin((1 - f) * th) * a + math.sin(f * th) * b) / math.sin(th))


def roll_head(pose, face):
    """Rolls Mjölnir about its handle so the head's long axis (grip ±Y) lies along face (⊥ handle)."""
    pose.touch()
    G = pose.world_rot("mjolnir_grip")
    axis = unit(G @ np.array([0, 0, -1.0]))
    want = face - axis * (axis @ face)
    if np.linalg.norm(want) < 1e-6:
        return
    want = unit(want)
    cur = unit(G @ np.array([0, 1.0, 0]))
    if cur @ want < 0:
        want = -want  # the head is symmetric: the nearer face
    ang = math.atan2(float(np.cross(cur, want) @ axis), float(cur @ want))
    pose.set_world_rot("mjolnir_grip", K.axis_angle(axis, ang) @ G)


def _free(spec):
    """A direction + reach from the shoulder (not a point on the belt or the handle)."""
    if isinstance(spec, np.ndarray):
        return spec.shape == (3,)
    return isinstance(spec, (tuple, list)) and len(spec) == 3 and not isinstance(spec[0], str)


def reach(pose, chain, target, pole, capped, **kw):
    """solve_arm; a free reach the arm cannot make (hand turn included) is shortened along its line."""
    miss = K.solve_arm(pose, chain, target, pole, **kw)
    if not capped:
        return miss
    for _ in range(4):
        if miss <= 0.05:
            return 0.0
        S = pose.point(chain.upper)
        d = target - S
        n = float(np.linalg.norm(d))
        target = S + d * max(0.3, (n - miss - 0.05) / n)
        miss = K.solve_arm(pose, chain, target, pole, **kw)
    return miss


CLEAR_STEPS = 4
CLEAR_MARGIN = 0.6  # px kept between a swinging arm / the hammer and the body
CORE = ("body", "belly", "beard", "head", "right_leg", "left_leg", "right_shin", "left_shin")
ARM_PARTS = {"r": ("right_arm", "right_forearm", "right_hand"), "l": ("left_arm", "left_forearm", "left_hand")}


def clearance(pose, hammer, belt=False):
    """Per arm, the push (px, model space) that would clear its cubes (and Mjölnir for the right) off the core
    (and off the belt hammer when it hangs there)."""
    global RULES
    if RULES is None:
        RULES = tp_rules()
    bs = K.tp_boxes(pose, hammer)
    out = {"r": np.zeros(3), "l": np.zeros(3)}
    if belt:
        # The fist or forearm in the hanging belt hammer (mesh depth, coarse grid): out, away from its centre.
        d = M.belt_depth(pose, BELT, fixed=("right_forearm", "right_hand"), step=0.7)
        if d > 0.0:
            centre = np.mean([b.center for b in belt_boxes(pose)], axis=0)
            n = pose.point("right_hand") - centre
            n[1] = 0.0
            out["r"] += (d + 0.3) * unit(n)
    for i, j in RULES.pairs(bs):
        a, b = bs[i], bs[j]
        for x, y in ((a, b), (b, a)):
            if y.model != "body" or y.bone not in CORE:
                continue
            if x.model == "mjolnir":
                side = "r"
            elif x.model == "body":
                side = "r" if x.bone in ARM_PARTS["r"] else "l" if x.bone in ARM_PARTS["l"] else None
                if side is None or x.bone.endswith("_arm"):
                    continue
            else:
                continue
            if np.linalg.norm(x.center - y.center) > float(np.linalg.norm(x.half) + np.linalg.norm(y.half)) + CLEAR_MARGIN:
                continue
            d = RULES.depth(a, b) - RULES.baseline.get((i, j), 0.0)
            if d > -CLEAR_MARGIN:
                n = x.center - y.center
                out[side] += (d + CLEAR_MARGIN) * n / max(float(np.linalg.norm(n)), 1e-6)
    return out


def sole(pose, shin):
    """Lowest model-space y (px) of a shin's cubes (the foot)."""
    f = pose.frame(shin)
    low = math.inf
    for lo, hi, cp, cr in pose.model.bones[shin].cubes:
        m = f if not np.any(cr != 0) else f @ R.trans(cp) @ R.mat4(R.rzyx(cr)) @ R.trans(-cp)
        for x in (lo[0], hi[0]):
            for y in (lo[1], hi[1]):
                for z in (lo[2], hi[2]):
                    low = min(low, float(m[1, :3] @ np.array([x, y, z]) + m[1, 3]))
    return low


def orbit(S, target, push):
    """Moves a hand target by push across the shoulder's sphere (the reach stays)."""
    v = target - S
    r = float(np.linalg.norm(v))
    u = v / r
    perp = push - u * (u @ push)
    return S + r * unit(v + perp)


def belt_boxes(pose):
    """The belt hammer (MJOLNIR_BELT geo) on the mjolnir_belt locator, as ThorRenderer draws it."""
    root = "mjolnir"
    frame = pose.pivot_frame("mjolnir_belt") @ R.trans(-BELT.bones[root].pivot)
    return R.boxes("belt", R.Pose(BELT), frame, BELT.subtree(root))


BELT_SWAY_MAX = 40.0  # deg: the belt hammer swings out at most this far
BELT_SWAY_STEP = 2.0
BELT_SWAY_DECAY = 0.75  # per tick: a pushed hammer falls back, it does not snap back


def belt_sway(pose, start=0.0):
    """Rolls the belt locator outward (about the torso's forward axis) just enough that the thigh and the
    shin do not go through the hanging hammer: the hammer is pushed by the leg, then swings back."""
    b = pose.model.bones["mjolnir_belt"]
    ang = max(0.0, start)
    while True:
        pose.rot["mjolnir_belt"] = b.init + np.array([0.0, 0.0, math.radians(ang)])
        pose.touch()
        if ang >= BELT_SWAY_MAX or M.belt_depth(pose, BELT) <= 0.05:
            return ang
        ang = min(BELT_SWAY_MAX, ang + BELT_SWAY_STEP)


def solve(it, ref=None, hammer=False, belt=False):
    """Solves one intent; returns (pose, misses)."""
    pose = R.Pose(K.BODY)
    if ref is not None:
        pose.rot = {k: v.copy() for k, v in ref.rot.items()}
    misses = {}
    spin = float(it["spin"])
    hip = P(*it["hip"])
    lean, twist, side = it["tor"]
    pose.set_bb("body", rot=(lean, twist + spin, side), pos=(-hip[0], hip[1], hip[2]))
    pose.rot["body"] = R.euler_near(R.rzyx(pose.rot["body"]), ref.rot["body"] if ref is not None else np.zeros(3))
    Rs = R.ry(math.radians(-spin))
    legs = it.get("legs")
    for s, chain, leg in (("r", K.RIGHT_LEG, "right_leg"), ("l", K.LEFT_LEG, "left_leg")):
        L = K.BODY.bones[leg].pivot
        off = Rs @ L - L + hip
        pose.pos[leg] = np.array([-off[0], off[1], off[2]])
        pose.touch()
        if legs:
            # Free legs (flight): direct thigh / shin turns, no ground.
            pose.set_bb(leg, rot=legs[s + "leg"])
            pose.set_bb(chain.lower, rot=legs[s + "shin"])
            continue
        foot = Rs @ (FEET[s] + P(*it[s + "ft"]))
        pole = Rs @ unit(P(*it[s + "k"]))
        misses[s + "foot"] = K.solve_leg(pose, chain, foot, pole)
        # No ankle: a bent knee tilts the foot, so the lowest corner of the sole (not the ankle) goes on
        # the planned height (the ground, or the step's arc).
        want = foot[1] - FEET[s][1]
        for _ in range(3):
            low = sole(pose, chain.lower)
            if abs(low - want) < 0.05:
                break
            foot = foot + np.array([0.0, want - low, 0.0])
            K.solve_leg(pose, chain, foot, pole)
    if belt or it.get("ra") == "belt" or (isinstance(it.get("ra"), tuple) and it["ra"] and it["ra"][0] == "mix"):
        prev = 0.0
        if ref is not None:
            prev = math.degrees(float(ref.rot["mjolnir_belt"][2] - pose.model.bones["mjolnir_belt"].init[2]))
        # "bp" / "bs": the hand lifts the hammer off the hip on the belt (draw / holster grab): an offset
        # (right, up, forward px in the torso frame) and an outward roll (degrees).
        bp = P(*(it.get("bp") or (0.0, 0.0, 0.0)))
        pose.pos["mjolnir_belt"] = np.array([-bp[0], bp[1], bp[2]])
        pose.touch()
        belt_sway(pose, max(prev * BELT_SWAY_DECAY, float(it.get("bs") or 0.0)))
    T = pose.frame("body")
    Rt = T[:3, :3]
    held = _dir(it["hm"], pose, Rt)
    target = _target(it["ra"], pose, "right_arm", Rt)
    pole = Rt @ unit(np.asarray(it["rp"], float))
    la = it["la"]
    lpole = Rt @ unit(np.asarray(it["lp"], float))
    on_handle = isinstance(la, str) or (isinstance(la, tuple) and la and la[0] == "mix")

    def right(tgt):
        misses["ra"] = reach(pose, K.RIGHT_ARM, tgt, pole, _free(it["ra"]), ham=held, wrist=None if held is not None else it["rw"])
        if held is not None:
            K.aim_grip(pose, held)
            face = _dir(it["hf"], pose, Rt)
            if face is None:
                up, fwd = Rt @ np.array([0, 1.0, 0]), Rt @ np.array([0, 0, -1.0])
                face = (up - held * (held @ up)) + 0.35 * (fwd - held * (held @ fwd))
            roll_head(pose, face)
            if it.get("hr"):
                G = pose.world_rot("mjolnir_grip")
                pose.set_world_rot("mjolnir_grip", K.axis_angle(K.hammer_axis(pose), math.radians(it["hr"])) @ G)

    def left(tgt):
        if on_handle:
            ax = K.hammer_axis(pose)
            misses["la"] = K.solve_arm(pose, K.LEFT_ARM, tgt, lpole, ham=-ax if la != "free" else None)
        else:
            misses["la"] = reach(pose, K.LEFT_ARM, tgt, lpole, _free(la), wrist=it["lw"])

    right(target)
    ltarget = _target(la, pose, "left_arm", Rt)
    left(ltarget)
    # Clearance: a hand, forearm or the hammer that would touch the body swings out round its shoulder.
    for _ in range(CLEAR_STEPS):
        moved = False
        pushes = clearance(pose, hammer, belt)
        if _free(it["ra"]) and np.linalg.norm(pushes["r"]) > 1e-3:
            target = orbit(pose.point("right_arm"), target, pushes["r"])
            right(target)
            if on_handle:
                ltarget = _target(la, pose, "left_arm", Rt)
                left(ltarget)
            moved = True
        if _free(la) and np.linalg.norm(pushes["l"]) > 1e-3:
            ltarget = orbit(pose.point("left_arm"), ltarget, pushes["l"])
            left(ltarget)
            moved = True
        if not moved:
            break
    for bone, rot in (it.get("add") or {}).items():
        pose.rot[bone] = pose.rot[bone] + np.radians([-rot[0], -rot[1], rot[2]])
    pose.touch()
    # Continuity of every turn with the previous tick.
    if ref is not None:
        for b in TP_BONES:
            pose.rot[b] = R.euler_near(R.rzyx(pose.rot[b]), ref.rot[b])
        pose.touch()
    return pose, misses


# ------------------------------------------------------------------------------------------- checks
STRAP_BONES = tuple(b for b in POMMEL_BONES if b != "handle")


def tp_rules():
    parents = {n: b.parent for n, b in K.BODY.bones.items()}
    # The thighs are root bones in the geo, but their joint is the hip at the bottom of the torso.
    parents.update(right_leg="body", left_leg="body")
    rules = R.Rules(parents).allow("body:right_hand", "mjolnir:*")
    for h in POMMEL_BONES:
        rules.allow("body:left_hand", "mjolnir:" + h)
    # The strap and the butt of the handle lie against the wrist.
    for h in STRAP_BONES:
        rules.allow("body:right_forearm", "mjolnir:" + h)
    rules.allow("body:left_hand", "body:right_hand")
    # The belly rests on the thighs when the hips fold.
    for leg in ("right_leg", "left_leg"):
        rules.allow("body:belly", "body:" + leg)
    rules.rest(K.tp_boxes(R.Pose(K.BODY), False))
    return rules


RULES = None


def check_tp(pose, hammer):
    global RULES
    if RULES is None:
        RULES = tp_rules()
    return RULES.check(K.tp_boxes(pose, hammer))


# ------------------------------------------------------------------------------------------- baking
def beats(windup, contact, recovery):
    """ThorClipBeats.of: {name: tick}."""
    out = {}
    if windup > 0:
        loaded = windup - (2 if windup >= 6 else 1)
        out["start"] = 0
        out["gather"] = min(loaded - 1, int(math.floor(windup * 0.35 + 0.5)))
        out["loaded"] = loaded
    out["contact"] = windup
    chain = windup + contact
    if contact > 0 and recovery > 0:
        out["overswing"] = chain
    if recovery > 1:
        out["settle"] = chain + int(math.floor(recovery * 0.5 + 0.5))
    out["end"] = windup + contact + recovery
    return out


HEAVY_EASE = {"start": "easeInOutSine", "gather": "easeOutSine", "loaded": "easeInCubic", "contact": "easeOutCubic",
              "overswing": "easeInOutSine", "settle": "easeInOutSine"}


class Clip:
    """A clip as timed intents: keys [(tick, intent, easing of the segment from here)]."""

    def __init__(self, name, length, keys, loop=False, hammer=True, owner="full", contact=None, fp=None, beats=None,
                 noise=None, fp_spin=0.35):
        self.name, self.length, self.keys, self.loop, self.hammer = name, length, keys, loop, hammer
        self.owner, self.contact, self.fp, self.beats, self.noise, self.fp_spin = owner, contact, fp, beats, noise, fp_spin

    def hammer_at(self, t):
        """Is Mjölnir drawn in the right hand at tick t (draw / holster switch to the belt model)?"""
        span = getattr(self, "hand_span", None)
        if not self.hammer:
            return False
        return span is None or span[0] <= t <= span[1]

    def belt_at(self, t):
        """Is Mjölnir on the belt at tick t (not in the fist and not away: thrown, flying back)?"""
        return not self.hammer_at(t) and not getattr(self, "away", False)

    def intent(self, t):
        ks = self.keys
        if t <= ks[0][0]:
            it = dict(ks[0][1])
        elif t >= ks[-1][0]:
            it = dict(ks[-1][1])
        else:
            it = None
            for i in range(len(ks) - 1):
                t0, a, e = ks[i]
                t1, b, _ = ks[i + 1]
                if t0 <= t <= t1:
                    f = 0.0 if t1 <= t0 else (t - t0) / (t1 - t0)
                    it = interpolate(a, b, K.EASE[e](f))
                    break
        if self.noise is not None:
            it = self.noise(t, it)
        return it


def bake_tp(clip, step=1):
    """Solves every tick: [(tick, pose, misses)]."""
    out = []
    ref = None
    for t in range(0, clip.length + 1, step):
        pose, misses = solve(clip.intent(t), ref, clip.hammer_at(t), clip.belt_at(t))
        out.append((t, pose, misses))
        ref = pose
    return out


def channels(samples, bones, pos_bones):
    """{bone: {"rotation": [(t, v)], "position": [(t, v)]}} from solved poses (Blockbench values)."""
    ch = {}
    for b in bones:
        rot, pos = [], []
        for t, pose, _ in samples:
            r, p = pose.bb(b)
            rot.append((t, r))
            pos.append((t, p))
        ch[b] = {"rotation": rot}
        if b in pos_bones:
            ch[b]["position"] = pos
    return ch


def reduce(keys, tol, keep=()):
    """Drops keys that linear interpolation between the kept neighbours reproduces within tol."""
    if len(keys) <= 2:
        return keys
    out = [keys[0]]
    i = 0
    while i < len(keys) - 1:
        j = i + 1
        while j + 1 < len(keys) and keys[j][0] not in keep:
            t0, v0 = keys[i]
            t1, v1 = keys[j + 1]
            ok = True
            for k in range(i + 1, j + 1):
                tk, vk = keys[k]
                f = (tk - t0) / (t1 - t0)
                if np.max(np.abs(v0 + (v1 - v0) * f - vk)) > tol:
                    ok = False
                    break
            if not ok:
                break
            j += 1
        out.append(keys[j])
        i = j
    return out


def unwrap_end(keys, period=360.0):
    """A rotation that ends a whole turn away from its start (a spin) wraps back with a pre/post key."""
    if len(keys) < 2:
        return keys, None
    first, last = keys[0][1], keys[-1][1]
    n = np.round((last - first) / period)
    if not np.any(n != 0):
        return keys, None
    wraps = {}
    out = []
    shift = np.zeros(3)
    for idx, (t, v) in enumerate(keys):
        v = v - shift
        for a in range(3):
            if n[a] != 0 and t not in wraps and abs(v[a] - first[a]) >= period * 0.5 and shift[a] == 0:
                pre = v.copy()
                shift[a] = n[a] * period
                post = v.copy()
                post[a] -= shift[a]
                wraps[t] = (pre, post)
                v = post
        out.append((t, v))
    return out, wraps


def fmt(v):
    return [float("%.2f" % x) if abs(x) >= 0.005 else 0.0 for x in v]


def write_channels(ch, length_ticks, keep, tol_rot=0.2, tol_pos=0.03):
    bones = {}
    for b, chs in ch.items():
        out = {}
        for kind, keys in chs.items():
            keys = [(t, np.asarray(v, float)) for t, v in keys]
            wraps = None
            if kind == "rotation":
                keys, wraps = unwrap_end(keys)
            keys = reduce(keys, tol_rot if kind == "rotation" else tol_pos, keep=set(keep) | set((wraps or {}).keys()))
            if all(np.max(np.abs(v)) < 0.005 for _, v in keys) and kind == "position":
                continue
            d = {}
            for t, v in keys:
                s = "%.4g" % (t * TICK)
                if wraps and t in wraps:
                    d[s] = {"pre": fmt(wraps[t][0]), "post": fmt(wraps[t][1])}
                else:
                    d[s] = fmt(v)
            out[kind] = d
        if out:
            bones[b] = out
    return bones


# ------------------------------------------------------------------------------------------- first person
FP_HAMMER_R = np.array([0.40, -0.46, -0.80])
FP_HAMMER_DIR = unit(np.array([-0.28, 0.90, -0.33]))
FP_FIST_R = np.array([0.40, -0.44, -0.72])
FP_GUARD_R = np.array([0.36, -0.36, -0.70])
FP_GUARD_L = np.array([-0.36, -0.34, -0.74])
FP_HIDDEN_L = np.array([-0.62, -1.00, -0.66])
FP_GAIN = np.array([0.024, 0.022, 0.016])
HEAD_OFFSET = 8.6 * K.PX


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def clamp_view(v, near=0.42):
    v = np.array(v, float)
    v[2] = min(v[2], -near)
    z = -v[2]
    v[0] = max(-1.05 * z, min(1.05 * z, v[0]))
    v[1] = max(-0.95 * z, min(0.70 * z, v[1]))
    return v


def ray_hits(box):
    """Does the view's centre ray (0,0,0) -> -Z pass through this box?"""
    Rm = np.column_stack(box.axes)
    o = Rm.T @ (-box.center)
    d = Rm.T @ np.array([0, 0, -1.0])
    tmin, tmax = 0.0, 1e9
    for a in range(3):
        if abs(d[a]) < 1e-9:
            if abs(o[a]) > box.half[a]:
                return False
            continue
        t1, t2 = (-box.half[a] - o[a]) / d[a], (box.half[a] - o[a]) / d[a]
        if t1 > t2:
            t1, t2 = t2, t1
        tmin, tmax = max(tmin, t1), min(tmax, t2)
        if tmin > tmax:
            return False
    return True


class FpPlan:
    """How a clip's first-person version follows its third-person motion."""

    def __init__(self, hammer=True, guard=False, left="auto", contact="head", gain=1.0, rest=None, ham=None,
                 hold_left=None, custom=None):
        self.hammer, self.guard, self.left, self.contact, self.gain = hammer, guard, left, contact, gain
        self.rest, self.ham, self.hold_left, self.custom = rest, ham, hold_left, custom


def fp_bake(clip, tp_samples, plan, proxy_samples=None):
    """First-person poses per tick from the third-person ones (or plan.custom(t) -> (rt, ham, lt, lham, wr, wl))."""
    src = proxy_samples or tp_samples
    b = clip.beats or {}
    gather = b.get("gather", 0)
    settle = b.get("settle", clip.length)
    end = clip.length
    rest_r = plan.rest if plan.rest is not None else (FP_GUARD_R if plan.guard else (FP_HAMMER_R if plan.hammer else FP_FIST_R))
    rest_l = FP_GUARD_L if plan.guard else FP_HIDDEN_L
    rest_ham = plan.ham if plan.ham is not None else FP_HAMMER_DIR
    g0 = src[0][1].point("mjolnir_grip")
    l0 = src[0][1].point("left_grip")
    uses_left = plan.left == "always" or (plan.left == "auto" and max(
        np.linalg.norm(p.point("left_grip") - l0) for _, p, _ in src) > 4.0) or plan.hold_left is not None
    # The camera left arm is drawn only by clips that move it (ThorFirstPerson: it keys armorLeftArm).
    clip.fp_left = bool(uses_left)
    out = []
    prev = None
    targets = []
    for t, pose, _ in src:
        if clip.beats and "gather" in b:
            act = smooth(t / max(1, gather)) if t < gather else (1.0 - smooth((t - settle) / max(1, end - settle)) if t > settle else 1.0)
        else:
            act = 1.0
        g = pose.point("mjolnir_grip")
        rt = rest_r + act * plan.gain * FP_GAIN * (g - g0)
        ham = None
        if plan.hammer:
            ham = slerp(rest_ham, K.hammer_axis(pose), act)
        lt = rest_l + act * plan.gain * FP_GAIN * (pose.point("left_grip") - l0) if uses_left else rest_l.copy()
        targets.append([t, rt, ham, lt, act])
    # Contact: the head (or the striking fist) crosses the crosshair on the contact tick.
    if clip.contact is not None and plan.contact and clip.beats and "loaded" in b:
        tc, tl = clip.contact, b["loaded"]
        to = b.get("overswing", tc + 2)
        row = [r for r in targets if r[0] == tc][0]
        if plan.contact == "head" and row[2] is not None:
            p = row[1] + HEAD_OFFSET * row[2]
        elif plan.contact == "left":
            p = row[3]
        else:
            p = row[1]
        corr = np.array([-p[0], -0.04 - p[1], 0.0])
        for r in targets:
            t = r[0]
            if t <= tc:
                w = smooth((t - tl + 1) / (tc - tl + 1))
            else:
                w = 1.0 - smooth((t - tc) / max(1, to - tc + 1))
            if plan.contact == "left":
                r[3] = r[3] + corr * w
            else:
                r[1] = r[1] + corr * w
    for t, rt, ham, lt, act in targets:
        rt, lt = clamp_view(rt), clamp_view(lt, 0.40)
        if plan.custom is not None:
            rt, ham, lt = plan.custom(t, rt, ham, lt)
        pose = K.fp_rest()
        miss = K.solve_fp_arm(pose, False, rt, ham=ham)
        lham = None
        on_handle = False
        if plan.hold_left is not None and plan.hammer and ham is not None:
            hold = plan.hold_left(t)
            if hold > 0.5:
                lt = rt - ham * (K.HANDLE_SPACING * K.PX)
                lham = -ham
                on_handle = True
        miss_l = K.solve_fp_arm(pose, True, lt, ham=lham)
        if prev is not None:
            for bn in FP_BONES:
                pose.rot[bn] = R.euler_near(R.rzyx(pose.rot[bn]), prev.rot[bn])
            pose.touch()
        out.append((t, pose, {"fp_r": miss, "fp_l": miss_l, "on_handle": on_handle}))
        prev = pose
    return out


NEAR = 0.05  # GameRenderer's near plane (blocks)
# Half size of the view at the near plane: the hand pass's 70 degree vertical FOV, 16:9 window, plus a margin.
NEAR_HALF = (NEAR * math.tan(math.radians(35)) * 16 / 9 * 1.15, NEAR * math.tan(math.radians(35)) * 1.15)


def near_cut(box):
    """Does the near plane cut this box inside the visible rectangle (a cube sliced open on screen)?"""
    cs = box.corners()
    zs = [c[2] + NEAR for c in cs]  # > 0: between the near plane and the eye, or behind it (cut away)
    if max(zs) <= 0 or min(zs) >= 0:
        return False
    pts = []
    for i in range(8):
        for a in range(3):
            j = i ^ (1 << a)
            if j < i:
                continue
            zi, zj = zs[i], zs[j]
            if (zi > 0) != (zj > 0):
                t = zi / (zi - zj)
                pts.append(cs[i][:2] + t * (cs[j][:2] - cs[i][:2]))
    if len(pts) < 3:
        return False
    pts = np.array(pts)
    c = pts.mean(axis=0)
    pts = pts[np.argsort(np.arctan2(pts[:, 1] - c[1], pts[:, 0] - c[0]))]
    hw, hh = NEAR_HALF
    rect = np.array([[-hw, -hh], [hw, -hh], [hw, hh], [-hw, hh]])
    axes = [np.array([1.0, 0]), np.array([0, 1.0])]
    for k in range(len(pts)):
        e = pts[(k + 1) % len(pts)] - pts[k]
        if np.linalg.norm(e) > 1e-9:
            axes.append(np.array([-e[1], e[0]]) / np.linalg.norm(e))
    for ax in axes:
        a, b = pts @ ax, rect @ ax
        if a.max() < b.min() or b.max() < a.min():
            return False
    return True


def check_fp(pose, hammer, left=True):
    """(nearest corner z: info only, near plane visibly cuts a cube, the centre ray is covered)."""
    boxes = K.fp_boxes(pose, hammer, ("r", "l") if left else ("r",))
    near = max(c[2] for bx in boxes for c in bx.corners())
    cut = any(near_cut(bx) for bx in boxes)
    covered = any(ray_hits(bx) for bx in boxes)
    return near, cut, covered