"""Keyframe authoring for geo clips, baked to dense linear bedrock keys.

A clip is a list of (tick, pose, ease) keys; ease shapes the segment that ENDS
at that key (ease-in into a contact key = fastest at contact; ease-out after it
= follow-through). Poses are {bone: [x, y, z]} degrees in Blockbench convention
plus optional {"@pos": {bone: [x, y, z]}} in model units. Bones missing from a
key inherit the previous key's value so channels never snap to zero.
Baking samples every BAKE_STEP ticks plus every exact key tick, so the authored
contact frame is always an exact key.
"""
import math

BAKE_STEP = 1.0  # ticks (20 Hz); exact key ticks are always added


def lin(t): return t
def ein(t): return t * t * t
def ein2(t): return t * t
def eout(t): return 1 - (1 - t) ** 3
def eout2(t): return 1 - (1 - t) ** 2
def eio(t): return 3 * t * t - 2 * t * t * t
def eback(t):
    c = 1.4
    return 1 + (c + 1) * (t - 1) ** 3 + c * (t - 1) ** 2


def merge(*poses):
    """Additive merge: later poses add to earlier ones per channel."""
    out = {}
    pos = {}
    for p in poses:
        for k, v in p.items():
            if k == "@pos":
                for b, w in v.items():
                    o = pos.setdefault(b, [0, 0, 0])
                    pos[b] = [o[i] + w[i] for i in range(3)]
                continue
            o = out.setdefault(k, [0, 0, 0])
            out[k] = [o[i] + v[i] for i in range(3)]
    if pos:
        out["@pos"] = pos
    return out


def lerp_pose(a, b, f):
    keys = set(a) | set(b)
    out = {}
    for k in keys:
        if k == "@pos":
            pa, pb = a.get("@pos", {}), b.get("@pos", {})
            out["@pos"] = {n: [pa.get(n, [0, 0, 0])[i] + (pb.get(n, [0, 0, 0])[i] - pa.get(n, [0, 0, 0])[i]) * f for i in range(3)] for n in set(pa) | set(pb)}
        else:
            va, vb = a.get(k, [0, 0, 0]), b.get(k, [0, 0, 0])
            out[k] = [va[i] + (vb[i] - va[i]) * f for i in range(3)]
    return out


class Clip:
    def __init__(self, name, length, loop=False, hold=False):
        self.name = name
        self.length = length
        self.loop = loop
        self.hold = hold
        self.keys = []
        self.layers = []  # functions tick -> pose (additive, e.g. breathing)

    def key(self, tick, pose, ease=eio):
        self.keys.append((float(tick), pose, ease))
        return self

    def layer(self, fn):
        self.layers.append(fn)
        return self

    def _resolved_keys(self):
        keys = sorted(self.keys, key=lambda k: k[0])
        out = []
        prev = {}
        for t, p, e in keys:
            full = {k: v for k, v in prev.items() if k != "@pos"}
            full.update({k: v for k, v in p.items() if k != "@pos"})
            pp = dict(prev.get("@pos", {}))
            pp.update(p.get("@pos", {}))
            if pp:
                full["@pos"] = pp
            out.append((t, full, e))
            prev = full
        # every key carries every bone ever animated
        allb = set()
        allp = set()
        for _, p, _ in out:
            allb |= {k for k in p if k != "@pos"}
            allp |= set(p.get("@pos", {}))
        for _, p, _ in out:
            for b in allb:
                p.setdefault(b, [0, 0, 0])
            if allp:
                pp = p.setdefault("@pos", {})
                for b in allp:
                    pp.setdefault(b, [0, 0, 0])
        return out

    def pose_at(self, tick):
        keys = self._resolved_keys()
        if tick <= keys[0][0]:
            base = keys[0][1]
        elif tick >= keys[-1][0]:
            base = keys[-1][1]
        else:
            for i in range(len(keys) - 1):
                t0, p0, _ = keys[i]
                t1, p1, e1 = keys[i + 1]
                if t0 <= tick <= t1:
                    f = 0 if t1 == t0 else (tick - t0) / (t1 - t0)
                    base = lerp_pose(p0, p1, e1(f))
                    break
        if self.layers:
            base = merge(base, *[fn(tick) for fn in self.layers])
        return base

    def bake(self):
        keys = self._resolved_keys()
        ticks = set(k[0] for k in keys)
        t = 0.0
        while t <= self.length + 1e-6:
            ticks.add(round(t, 4))
            t += BAKE_STEP
        ticks.add(float(self.length))
        ticks = sorted(x for x in ticks if 0 <= x <= self.length)
        bones = {}
        for tk in ticks:
            p = self.pose_at(tk)
            sec = "%.4f" % (tk / 20.0)
            for b, v in p.items():
                if b == "@pos":
                    for n, w in v.items():
                        bones.setdefault(n, {}).setdefault("position", {})[sec] = [round(x, 2) for x in w]
                    continue
                bones.setdefault(b, {}).setdefault("rotation", {})[sec] = [round(x, 2) for x in v]
        # drop channels that never move (keeps files small and channel ownership honest)
        for b in list(bones):
            for ch in list(bones[b]):
                vals = list(bones[b][ch].values())
                if all(v == vals[0] for v in vals):
                    if all(abs(x) < 1e-6 for x in vals[0]):
                        del bones[b][ch]
                    else:
                        bones[b][ch] = {"0.0": vals[0]}
            if not bones[b]:
                del bones[b]
        loop = "loop" if self.loop else ("hold_on_last_frame" if self.hold else False)
        out = {"animation_length": round(self.length / 20.0, 4), "bones": bones}
        out["loop"] = loop if loop else False
        return out


def wave(period, amp_pose, phase=0.0):
    """Additive sine layer: amp_pose scaled by sin(2pi (t / period + phase))."""
    def fn(t):
        s = math.sin(2 * math.pi * (t / period + phase))
        out = {}
        for k, v in amp_pose.items():
            if k == "@pos":
                out["@pos"] = {n: [x * s for x in w] for n, w in v.items()}
            else:
                out[k] = [x * s for x in v]
        return out
    return fn
