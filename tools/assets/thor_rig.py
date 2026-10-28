"""Thor rework R2b: CPU pose of Thor's geo models, exactly in AnimRenderer's order.

Mirror of the game path (BakedGeoModel parse + AnimationController.seek + AnimRenderer.renderBone)
and of the test-side ThorRig / ThorIntersection, used by make_thor_clips.py to author clips by IK
and to check them before the Java tests run. Internal frame = BakedGeoModel's: Bedrock x mirrored
(+x = Thor's right), y up, front -Z; lengths in px (1/16 block).

Blockbench values <-> internal: rotation (x, y, z) deg -> radians (-x, -y, z) added to the bone's
rest rotation; position (x, y, z) px -> translate (-x, y, z).
"""
import json
import math

import numpy as np

JOINT_TRIM = 3.0
TOLERANCE = 0.25


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def rzyx(e):
    """Rz(e[2]) . Ry(e[1]) . Rx(e[0]) (internal radians), the renderer's order."""
    return rz(e[2]) @ ry(e[1]) @ rx(e[0])


def euler_zyx(m):
    """Internal (x, y, z) radians with rzyx(e) == m (one branch)."""
    sy = -m[2, 0]
    sy = max(-1.0, min(1.0, sy))
    y = math.asin(sy)
    if abs(sy) < 0.999999:
        x = math.atan2(m[2, 1], m[2, 2])
        z = math.atan2(m[1, 0], m[0, 0])
    else:
        x = math.atan2(-m[1, 2], m[1, 1])
        z = 0.0
    return np.array([x, y, z])


def euler_near(m, ref):
    """The Euler triple of m closest to ref (both branches, 2*pi unwrapped)."""
    e = euler_zyx(m)
    alt = np.array([e[0] + math.pi, math.pi - e[1], e[2] + math.pi])
    best, dist = None, None
    for c in (e, alt):
        c = c.copy()
        for i in range(3):
            c[i] = ref[i] + ((c[i] - ref[i] + math.pi) % (2 * math.pi) - math.pi)
        d = float(np.sum((c - ref) ** 2))
        if dist is None or d < dist:
            best, dist = c, d
    return best


def mat4(r=None, t=None):
    m = np.eye(4)
    if r is not None:
        m[:3, :3] = r
    if t is not None:
        m[:3, 3] = t
    return m


def trans(v):
    return mat4(t=np.asarray(v, dtype=float))


class Bone:
    def __init__(self, name, parent, pivot, init, cubes):
        self.name, self.parent, self.pivot, self.init, self.cubes = name, parent, pivot, init, cubes
        self.children = []


class Model:
    """One geo file: bones (internal frame, px) and cubes as (min, max, cube pivot, cube rotation)."""

    def __init__(self, path):
        g = json.load(open(path))["minecraft:geometry"][0]
        self.bones = {}
        self.order = []
        for b in g["bones"]:
            p = b.get("pivot", [0, 0, 0])
            r = b.get("rotation", [0, 0, 0])
            cubes = []
            for c in b.get("cubes", []):
                o, s, inf = c["origin"], c["size"], c.get("inflate", 0.0)
                lo = np.array([-(o[0] + s[0]) - inf, o[1] - inf, o[2] - inf])
                hi = np.array([-o[0] + inf, o[1] + s[1] + inf, o[2] + s[2] + inf])
                cp = c.get("pivot", [0, 0, 0])
                cr = c.get("rotation", [0, 0, 0])
                cubes.append((lo, hi, np.array([-cp[0], cp[1], cp[2]], float),
                              np.radians([-cr[0], -cr[1], cr[2]])))
            bone = Bone(b["name"], b.get("parent"), np.array([-p[0], p[1], p[2]], float),
                        np.radians([-r[0], -r[1], r[2]]), cubes)
            self.bones[bone.name] = bone
            self.order.append(bone.name)
        for bone in self.bones.values():
            if bone.parent:
                self.bones[bone.parent].children.append(bone.name)

    def subtree(self, root):
        out, stack = [], [root]
        while stack:
            n = stack.pop()
            out.append(n)
            stack.extend(reversed(self.bones[n].children))
        return out


class Pose:
    """Bone rotations (internal radians, absolute = rest + anim) and positions (Blockbench px)."""

    def __init__(self, model):
        self.model = model
        self.rot = {n: b.init.copy() for n, b in model.bones.items()}
        self.pos = {n: np.zeros(3) for n in model.bones}
        self._cache = {}

    def copy(self):
        p = Pose(self.model)
        p.rot = {k: v.copy() for k, v in self.rot.items()}
        p.pos = {k: v.copy() for k, v in self.pos.items()}
        return p

    def touch(self):
        self._cache = {}

    def set_bb(self, bone, rot=None, pos=None):
        """Blockbench animation values on top of the rest pose."""
        b = self.model.bones[bone]
        if rot is not None:
            self.rot[bone] = b.init + np.radians([-rot[0], -rot[1], rot[2]])
        if pos is not None:
            self.pos[bone] = np.array(pos, float)
        self.touch()

    def bb(self, bone):
        """(rotation deg, position px) Blockbench animation values of a bone."""
        b = self.model.bones[bone]
        d = np.degrees(self.rot[bone] - b.init)
        return np.array([-d[0], -d[1], d[2]]), self.pos[bone].copy()

    def local(self, name):
        b = self.model.bones[name]
        p = self.pos[name]
        return trans([-p[0], p[1], p[2]]) @ trans(b.pivot) @ mat4(rzyx(self.rot[name])) @ trans(-b.pivot)

    def frame(self, name):
        if name not in self._cache:
            b = self.model.bones[name]
            parent = self.frame(b.parent) if b.parent else np.eye(4)
            self._cache[name] = parent @ self.local(name)
        return self._cache[name]

    def parent_frame(self, name):
        b = self.model.bones[name]
        return self.frame(b.parent) if b.parent else np.eye(4)

    def pivot_frame(self, name):
        return self.frame(name) @ trans(self.model.bones[name].pivot)

    def point(self, name):
        return self.pivot_frame(name)[:3, 3].copy()

    def world_rot(self, name):
        return self.frame(name)[:3, :3].copy()

    def set_world_rot(self, name, r, ref=None):
        """Sets the bone's local rotation so its model-space rotation becomes r."""
        parent = self.parent_frame(name)[:3, :3]
        local = parent.T @ r
        self.rot[name] = euler_near(local, self.rot[name] if ref is None else ref)
        self.touch()


class Box:
    __slots__ = ("model", "bone", "center", "axes", "half", "pivot")

    def __init__(self, model, bone, center, axes, half, pivot):
        self.model, self.bone, self.center, self.axes, self.half, self.pivot = model, bone, center, axes, half, pivot

    @property
    def id(self):
        return self.model + ":" + self.bone

    def corners(self):
        out = []
        for sx in (-1, 1):
            for sy in (-1, 1):
                for sz in (-1, 1):
                    out.append(self.center + self.axes[0] * self.half[0] * sx + self.axes[1] * self.half[1] * sy
                               + self.axes[2] * self.half[2] * sz)
        return out


def boxes(label, pose, base=None, bones=None, root_local=True):
    """Posed boxes of a model. bones = the drawn subtree (default all); base = model-space frame of the draw."""
    base = np.eye(4) if base is None else base
    model = pose.model
    names = model.order if bones is None else bones
    out = []
    frames = {}

    def frame_of(n):
        if n in frames:
            return frames[n]
        b = model.bones[n]
        if bones is not None and (b.parent is None or b.parent not in bones):
            f = base @ pose.local(n)
        else:
            f = (frame_of(b.parent) if b.parent else base) @ pose.local(n)
        frames[n] = f
        return f

    for n in names:
        b = model.bones[n]
        f = frame_of(n)
        pivot = (f @ np.append(b.pivot, 1))[:3]
        for lo, hi, cp, cr in b.cubes:
            m = f
            if np.any(cr != 0):
                m = f @ trans(cp) @ mat4(rzyx(cr)) @ trans(-cp)
            c = (lo + hi) * 0.5
            h = (hi - lo) * 0.5
            axes, half = [], []
            for a in range(3):
                d = m[:3, a]
                s = np.linalg.norm(d)
                axes.append(d / s if s > 1e-9 else np.eye(3)[a])
                half.append(h[a] * s)
            out.append(Box(label, n, (m @ np.append(c, 1))[:3], axes, np.array(half), pivot))
    return out


def trim(box):
    off = box.pivot - box.center
    axis, best = -1, 0.5
    for a in range(3):
        h = box.half[a]
        ratio = abs(off @ box.axes[a]) / h if h > 1e-6 else 0.0
        if ratio > best:
            best, axis = ratio, a
    if axis < 0:
        return box
    u = box.axes[axis]
    h = box.half[axis]
    cut = min(JOINT_TRIM, max(0.0, 2 * h - 1.0))
    half = box.half.copy()
    half[axis] = h - cut * 0.5
    side = math.copysign(1.0, off @ u) if off @ u != 0 else 0.0
    return Box(box.model, box.bone, box.center - u * side * cut * 0.5, box.axes, half, box.pivot)


def sat(a, b):
    t = b.center - a.center
    axes = list(a.axes) + list(b.axes)
    for i in range(3):
        for j in range(3):
            c = np.cross(a.axes[i], b.axes[j])
            n = np.linalg.norm(c)
            if n > 1e-4:
                axes.append(c / n)
    best = math.inf
    for l in axes:
        ra = sum(a.half[i] * abs(a.axes[i] @ l) for i in range(3))
        rb = sum(b.half[i] * abs(b.axes[i] @ l) for i in range(3))
        o = ra + rb - abs(t @ l)
        if o < best:
            best = o
            if best <= 0:
                return best
    return best


class Rules:
    """ThorIntersection: skip same bone / same attachment, allowed pairs, rest baseline, joint trim."""

    def __init__(self, parents=None):
        self.allowed = set()
        self.baseline = {}
        self.parents = parents or {}

    def allow(self, a, b):
        self.allowed.add((a, b))
        self.allowed.add((b, a))
        return self

    def skip(self, a, b):
        if a.model == b.model and (a.bone == b.bone or a.model != "body"):
            return True
        # Neighbours at one joint (upper arm / forearm, forearm / fist, thigh / shin) share the joint.
        if a.model == "body" and b.model == "body" and (self.parents.get(a.bone) == b.bone or self.parents.get(b.bone) == a.bone):
            return True
        al = self.allowed
        return ((a.id, b.id) in al or (a.model + ":*", b.id) in al or (a.id, b.model + ":*") in al
                or (a.model + ":*", b.model + ":*") in al)

    def depth(self, a, b):
        if a.model == "body" and b.model == "body":
            return sat(trim(a), trim(b))
        return sat(a, b)

    def pairs(self, bs):
        for i in range(len(bs)):
            for j in range(i + 1, len(bs)):
                if not self.skip(bs[i], bs[j]):
                    yield i, j

    def rest(self, bs):
        self.baseline = {}
        for i, j in self.pairs(bs):
            d = self.depth(bs[i], bs[j])
            if d > 0:
                self.baseline[(i, j)] = d
        return self

    def check(self, bs):
        hits = []
        for i, j in self.pairs(bs):
            a, b = bs[i], bs[j]
            # cheap sphere reject
            ra = float(np.linalg.norm(a.half)); rb = float(np.linalg.norm(b.half))
            if np.linalg.norm(a.center - b.center) > ra + rb:
                continue
            d = self.depth(a, b) - self.baseline.get((i, j), 0.0)
            if d > TOLERANCE:
                hits.append((a.id, b.id, d))
        hits.sort(key=lambda h: -h[2])
        return hits
