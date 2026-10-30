#!/usr/bin/env python3
"""Regulus Reborn clips (own work), evaluated by the mod's anim core.

Times are authored in server ticks (20 per second) and written in seconds. Rotations use the
project's Bedrock degree convention (see regulus_rig.py): hanging limbs swing forward with x-,
the torso and head lean forward/down with x+, right-side z+ swings outward, y+ turns towards the
model's right. Left-side values are authored in right-hand terms and mirrored (x, -y, -z).

Legs are never hand-keyed: every leg key comes from a two-bone IK (regulus_rig.solve_leg) of
feet planted on the ground or stepping on an arc, so stance feet do not slide and soles stay on
the ground (no tiptoe). Locomotion cycles are authored per walked distance: one walk cycle covers
WALK_STRIDE blocks, the poser advances them by the real horizontal distance.

Clip names of timed moves equal RegulusMove.clip(); their lengths equal RegulusMove.length()
(RegulusClipsTest). Run: python3 tools/assets/make_regulus_clips.py [--check] [--report]
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import regulus_rig as rig  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ANIM = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore/animations/regulus")

# blocks per full gait cycle (two steps); mirrored in RegulusPoser.WALK/RUN/SNEAK_STRIDE
WALK_STRIDE = 1.25
RUN_STRIDE = 1.9
SNEAK_STRIDE = 0.9
LEG_BONES = ("hips", "right_thigh", "right_shin", "right_foot", "right_toe", "left_thigh", "left_shin", "left_foot", "left_toe")
Z = [0, 0, 0]

# finger poses of the right hand; z- curls towards the palm (inwards), thumb x- folds across
HANDS = {
    "relaxed": {"index": [0, 0, -16], "index2": [0, 0, -20], "fingers": [0, 0, -24], "fingers2": [0, 0, -26], "thumb": [0, 0, 0], "thumb2": [0, 0, -8]},
    "open": {"index": [-6, 0, 4], "index2": [0, 0, 2], "fingers": [6, 0, 2], "fingers2": [0, 0, 2], "thumb": [-16, 0, 20], "thumb2": [0, 0, 6]},
    "fist": {"index": [0, 0, -84], "index2": [0, 0, -78], "fingers": [0, 0, -88], "fingers2": [0, 0, -82], "thumb": [26, 0, -36], "thumb2": [0, 0, -30]},
    "point": {"index": [0, 0, 0], "index2": [0, 0, 0], "fingers": [0, 0, -88], "fingers2": [0, 0, -82], "thumb": [20, 0, -30], "thumb2": [0, 0, -24]},
    "flick_set": {"index": [0, 0, -46], "index2": [0, 0, -40], "fingers": [0, 0, -84], "fingers2": [0, 0, -76], "thumb": [14, 0, -40], "thumb2": [0, 0, -14]},
    "flick_hit": {"index": [0, 0, 8], "index2": [0, 0, 4], "fingers": [0, 0, -84], "fingers2": [0, 0, -76], "thumb": [22, 0, -26], "thumb2": [0, 0, -10]},
    "palm": {"index": [-4, 0, 10], "index2": [0, 0, 6], "fingers": [4, 0, 10], "fingers2": [0, 0, 6], "thumb": [-24, 0, 26], "thumb2": [0, 0, 8]},
    "scoop": {"index": [0, 0, -30], "index2": [0, 0, -26], "fingers": [0, 0, -34], "fingers2": [0, 0, -30], "thumb": [-8, 0, -10], "thumb2": [0, 0, -6]},
    "claw": {"index": [0, 0, -40], "index2": [0, 0, -50], "fingers": [0, 0, -44], "fingers2": [0, 0, -54], "thumb": [-10, 0, -20], "thumb2": [0, 0, -24]},
}


def mirror(v):
    return [v[0], -v[1], -v[2]]


def sec(tick):
    return ("%.4f" % (tick / 20.0)).rstrip("0").rstrip(".") if tick else "0.0"


def lerp(a, b, f):
    return a + (b - a) * f


def smooth(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def hermite(p0, m0, p1, m1, s):
    s2, s3 = s * s, s * s * s
    return (2 * s3 - 3 * s2 + 1) * p0 + (s3 - 2 * s2 + s) * m0 + (-2 * s3 + 3 * s2) * p1 + (s3 - s2) * m1


class Clip:
    def __init__(self, length, loop=False):
        self.length = length
        self.loop = loop
        self.tracks = {}

    def key(self, bone, channel, tick, value):
        rows = self.tracks.setdefault(bone, {}).setdefault(channel, {})
        rows[tick] = [round(float(x), 2) for x in value]
        return self

    def pose(self, tick, pose):
        """pose: bone -> rotation; 'R:'/'L:' prefixed keys are arm-side bones (left mirrored);
        'hand_r'/'hand_l' a HANDS name; '<bone>@pos' a position."""
        for name, v in pose.items():
            if name in ("hand_r", "hand_l"):
                side = "right" if name == "hand_r" else "left"
                for suffix, rot in HANDS[v].items():
                    self.key(side + "_" + suffix, "rotation", tick, rot if side == "right" else mirror(rot))
            elif name.endswith("@pos"):
                self.key(name[:-4], "position", tick, v)
            elif name.startswith("R:"):
                self.key("right_" + name[2:], "rotation", tick, v)
            elif name.startswith("L:"):
                self.key("left_" + name[2:], "rotation", tick, mirror(v))
            else:
                self.key(name, "rotation", tick, v)
        return self

    def poses(self, rows):
        for t, p in rows:
            self.pose(t, p)
        return self

    def to_json(self):
        bones = {}
        for bone, chans in self.tracks.items():
            out = {}
            for chan, rows in chans.items():
                rows = dict(rows)
                ticks = sorted(rows)
                if self.loop and ticks[-1] < self.length:
                    rows[self.length] = rows[ticks[0]]
                    ticks.append(self.length)
                nd = 1 if chan == "rotation" else 2
                fmt = lambda v: [round(x, nd) + 0.0 for x in v]
                if all(fmt(rows[t]) == fmt(rows[ticks[0]]) for t in ticks):
                    out[chan] = {"0.0": fmt(rows[ticks[0]])}
                    continue
                dense = len(ticks) >= 6 and max(b - a for a, b in zip(ticks, ticks[1:])) <= 2
                if dense:  # IK / gait samples: linear between kept keys within a small tolerance
                    tol = 0.12 if chan == "rotation" else 0.015
                    keep = [ticks[0]]
                    for i in range(1, len(ticks) - 1):
                        a, b = keep[-1], ticks[i + 1]
                        ok = True
                        for j in ticks[ticks.index(a) + 1:i + 2]:
                            if j >= b:
                                break
                            f = (j - a) / (b - a)
                            if any(abs(lerp(x, y, f) - z) > tol for x, y, z in zip(rows[a], rows[b], rows[j])):
                                ok = False
                                break
                        if not ok:
                            keep.append(ticks[i])
                    keep.append(ticks[-1])
                    out[chan] = {sec(t): fmt(rows[t]) for t in keep}
                else:  # authored poses: catmull-rom; interior keys of a held value are redundant
                    kept = [t for i, t in enumerate(ticks)
                            if i == 0 or i == len(ticks) - 1 or not (fmt(rows[ticks[i - 1]]) == fmt(rows[t]) == fmt(rows[ticks[i + 1]]))]
                    out[chan] = {sec(t): {"post": fmt(rows[t]), "lerp_mode": "catmullrom"} for t in kept}
            bones[bone] = out
        clip = {"animation_length": self.length / 20.0, "bones": bones}
        if self.loop:
            clip["loop"] = True
        return clip

    def sample(self, bone, channel, tick):
        """Catmull-Rom sample like KeyframeStack (for the report)."""
        rows = self.tracks.get(bone, {}).get(channel)
        if not rows:
            return [0.0, 0.0, 0.0]
        ticks = sorted(rows)
        if self.loop and ticks[-1] < self.length:
            rows = dict(rows)
            rows[self.length] = rows[ticks[0]]
            ticks.append(self.length)
        if tick <= ticks[0]:
            return rows[ticks[0]]
        if tick >= ticks[-1]:
            return rows[ticks[-1]]
        i = max(j for j in range(len(ticks)) if ticks[j] <= tick)
        t0, t1 = ticks[i], ticks[i + 1]
        f = (tick - t0) / (t1 - t0)
        p1, p2 = rows[t0], rows[t1]
        p0 = rows[ticks[i - 1]] if i > 0 else p1
        p3 = rows[ticks[i + 2]] if i + 2 < len(ticks) else p2
        return [0.5 * (2 * b + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (-a + 3 * b - 3 * c + d) * f ** 3)
                for a, b, c, d in zip(p0, p1, p2, p3)]


# ------------------------------------------------------------------ poses
def arm(side, sh=None, a=None, f=None, h=None):
    p = "R:" if side == "right" else "L:"
    out = {}
    for bone, v in (("shoulder", sh), ("arm", a), ("forearm", f), ("hand", h)):
        if v is not None:
            out[p + bone] = v
    return out


def P(*parts, **bones):
    out = {}
    for part in parts:
        out.update(part)
    out.update(bones)
    return out


# the calm, upright stance: chest out, chin up, arms loose and slightly away from the coat
UPPER_REST = P(arm("right", Z, [3, 4, 7], [-10, 0, 0], [0, 6, 0]), arm("left", Z, [3, 4, 7], [-10, 0, 0], [0, 6, 0]),
               torso=[-2, 0, 0], chest=[-2, 0, 0], neck=[2, 0, 0], head=[-5, 0, 0], hand_r="relaxed", hand_l="relaxed")
# hands folded behind the back (trample, a king does not need his hands)
BEHIND = P(arm("right", [0, 0, -2], [24, 18, 10], [-62, 0, 0], [0, 40, -20]), arm("left", [0, 0, -2], [26, 18, 10], [-58, 0, 0], [0, 40, -20]),
           hand_r="relaxed", hand_l="claw")


def rest(**over):
    p = dict(UPPER_REST)
    p.update(over)
    return p


# ------------------------------------------------------------------ legs: planted feet + steps
class Stance:
    """Feet on the ground (geo px, forward = -z) and the hips over time; sampled into IK keys."""

    def __init__(self, right=(-2.0, -0.6), left=(2.0, 0.6), hips_pos=(0, -0.2, 0), hips_rot=(0, 0, 0)):
        # right/left: (x, forward) of the ankle; heel/ball stay on the ground unless pitched
        self.feet = {"right": [(0, right[0], right[1], 0.0, 0.0)], "left": [(0, left[0], left[1], 0.0, 0.0)]}
        self.hips = [(0, list(hips_pos), list(hips_rot))]

    def step(self, side, t0, t1, x, fwd, lift=1.6):
        """The foot leaves at t0 and is planted again at t1 at (x, fwd)."""
        self.feet[side].append((t0, None, None, 0.0, 0.0))
        self.feet[side].append((t1, x, fwd, lift, 0.0))
        return self

    def heel(self, side, t, pitch):
        """Rolls the planted foot onto the ball (pitch > 0, heel up) or the heel (< 0) from t."""
        self.feet[side].append((t, None, None, 0.0, pitch))
        return self

    def hip(self, t, pos, rot=(0, 0, 0)):
        self.hips.append((t, list(pos), list(rot)))
        return self

    def _hips_at(self, t):
        rows = self.hips
        if t <= rows[0][0]:
            return rows[0][1], rows[0][2]
        for (ta, pa, ra), (tb, pb, rb) in zip(rows, rows[1:]):
            if ta <= t <= tb:
                f = smooth((t - ta) / (tb - ta)) if tb > ta else 1.0
                return [lerp(a, b, f) for a, b in zip(pa, pb)], [lerp(a, b, f) for a, b in zip(ra, rb)]
        return rows[-1][1], rows[-1][2]

    def _foot_at(self, side, t):
        """(ankle geo point, pitch) of one foot at tick t."""
        rows = self.feet[side]
        x, fwd, pitch = rows[0][1], rows[0][2], 0.0
        state = None
        for i, (tk, kx, kf, lift, kp) in enumerate(rows):
            if tk > t:
                break
            if kx is None and kp == 0.0 and i + 1 < len(rows) and rows[i + 1][1] is not None:
                state = (tk, rows[i + 1], (x, fwd))
            elif kx is None:
                pitch = kp
                pitch_from = rows[i - 1][4] if i > 0 else 0.0
                state = ("pitch", tk, pitch_from, kp)
            else:
                x, fwd = kx, kf
                state = None
        if state and state[0] != "pitch":
            t0, (t1, nx, nf, lift, _), (ox, of) = state
            f = smooth((t - t0) / (t1 - t0))
            u = max(0.0, min(1.0, (t - t0) / (t1 - t0)))
            y = rig.ANKLE_H + lift * math.sin(math.pi * u) ** 1.2
            pitch = 8.0 * math.sin(math.pi * u) - 10.0 * math.sin(math.pi * u) ** 3 * 0.0
            return (lerp(ox, nx, f), y, -lerp(of, nf, f)), pitch, 0.0
        if state and state[0] == "pitch":
            _, tk, p0, p1 = state
            f = smooth((t - tk) / 4.0)
            pitch = lerp(p0, p1, f)
        return self._pitched(x, fwd, pitch), pitch, -pitch if pitch > 0 else 0.0

    @staticmethod
    def _pitched(x, fwd, pitch):
        """Ankle of a foot rolled about its ball (pitch > 0) or heel (pitch < 0), contact fixed."""
        p = math.radians(pitch)
        if pitch > 0:  # ball fixed at fwd + 1.5
            bf, by = fwd + 1.5, 0.0
            vf, vy = -1.5, rig.ANKLE_H
        elif pitch < 0:
            bf, by = fwd - 1.2, 0.0
            vf, vy = 1.2, rig.ANKLE_H
        else:
            return (x, rig.ANKLE_H, -fwd)
        f2 = vf * math.cos(p) + vy * math.sin(p)
        y2 = -vf * math.sin(p) + vy * math.cos(p)
        return (x, by + y2, -(bf + f2))

    def bake(self, clip, ticks, coat=True):
        for t in ticks:
            hp, hr = self._hips_at(t)
            clip.key("hips", "position", t, hp)
            clip.key("hips", "rotation", t, hr)
            for side in ("right", "left"):
                ankle, pitch, toe = self._foot_at(side, t)
                rots, _ = rig.solve_leg(side, hp, hr, ankle, pitch)
                for b, v in rots.items():
                    clip.key(b, "rotation", t, v)
                clip.key(side + "_toe", "rotation", t, [toe, 0, 0])
            if coat:
                coat_follow(clip, t)
        return self


def coat_follow(clip, t):
    """Coat panels clear the thighs: a leg swung forward pushes its front panel, both legs bent
    open the back panel (the poser's springs add sway on top)."""
    rt = clip.tracks["right_thigh"]["rotation"][t][0]
    lt = clip.tracks["left_thigh"]["rotation"][t][0]
    clip.key("coat_front_r", "rotation", t, [min(0.0, rt) * 0.85, 0, 2])
    clip.key("coat_front_l", "rotation", t, [min(0.0, lt) * 0.85, 0, -2])
    back = max(0.0, max(rt, lt)) * 0.7 + max(0.0, -min(rt, lt) - 20) * 0.35
    clip.key("coat_back", "rotation", t, [back, 0, 0])
    clip.key("coat_back_lower", "rotation", t, [back * 0.3, 0, 0])


def every(t0, t1, step=2):
    out = list(range(t0, t1, step))
    if out[-1] != t1:
        out.append(t1)
    return out


# ------------------------------------------------------------------ locomotion (per distance)
def gait_foot(u, duty, stride, lift, strike, off, center):
    """Ankle (forward px rel. to the hip), height and pitch of one foot at cycle phase u.
    Stance: the ground under the foot moves back at exactly the walking speed (stride px per
    cycle): heel strike rolls onto the flat foot, then onto the ball (heel off)."""
    contact = lambda s: stance_point(s, duty, stride, strike, off, center)
    if u < duty:
        return contact(u / duty)
    s = (u - duty) / (1 - duty)
    f0, y0, p0, _ = contact(1.0)
    f1, y1, p1, _ = contact(0.0)
    eps = 1e-3
    fa, ya, pa, _ = contact(1.0 - eps)
    fb, yb, pb, _ = contact(eps)
    span = (1 - duty) / duty  # swing duration in stance-s units
    m0f, m0y, m0p = (f0 - fa) / eps * span, (y0 - ya) / eps * span, (p0 - pa) / eps * span
    m1f, m1y, m1p = (fb - f1) / eps * span, (yb - y1) / eps * span, (pb - p1) / eps * span
    f = hermite(f0, m0f, f1, m1f, s)
    y = hermite(y0, m0y, y1, m1y, s) + lift * math.sin(math.pi * s) ** 2 * (1.0 + 0.35 * math.cos(math.pi * s))
    p = hermite(p0, m0p, p1, m1p, s) + 10.0 * math.sin(math.pi * s)
    toe = -max(0.0, p) * (1 - smooth(s / 0.5))
    return f, y, p, toe


def stance_point(s, duty, stride, strike, off, center):
    travel = duty * stride
    g = center + travel * (0.5 - s)  # body-frame offset of the ground point under the flat ankle
    h1, h2 = 0.16, 0.66
    if s < h1:  # heel rocker: heel fixed on the ground
        p = lerp(strike, 0.0, smooth(s / h1))
        heel_f = g - 1.2
        pr = math.radians(p)
        f2 = 1.2 * math.cos(pr) + rig.ANKLE_H * math.sin(pr)
        y2 = -1.2 * math.sin(pr) + rig.ANKLE_H * math.cos(pr)
        return heel_f + f2, y2, p, 0.0
    if s <= h2:
        return g, rig.ANKLE_H, 0.0, 0.0
    p = off * ((s - h2) / (1 - h2)) ** 1.5
    ball_f = g + 1.5
    pr = math.radians(p)
    f2 = -1.5 * math.cos(pr) + rig.ANKLE_H * math.sin(pr)
    y2 = 1.5 * math.sin(pr) + rig.ANKLE_H * math.cos(pr)
    return ball_f + f2, y2, p, -p


def gait(stride_blocks, duty, lift, strike, off, drop, bob, lean, swing, keys=24, run=False, width=2.05, sneak=False):
    """One locomotion cycle keyed `keys` times; the clip length is `keys` ticks (time = phase)."""
    c = Clip(keys, loop=True)
    stride = stride_blocks * rig.PX_PER_BLOCK
    hip_f = -0.4 if not sneak else -1.2
    center = hip_f + (0.6 if run else 0.0)
    samples = []
    for i in range(keys):
        u = i / keys
        feet = {}
        for side, ph in (("right", u), ("left", (u + 0.5) % 1.0)):
            feet[side] = gait_foot(ph, duty, stride, lift, strike, off, center)
        # pelvis: bob (walk: high at mid-stance; run: low at mid-stance), yaw with the stride
        mid = math.cos(4 * math.pi * (u - duty / 2))
        nominal = 11.6 + drop + (bob * mid if not run else -bob * mid)
        limit = min(rig.max_hip_height(feet[s][1], feet[s][0], hip_f) for s in feet if (feet[s][1] < rig.ANKLE_H + 1.0))
        samples.append([u, feet, min(nominal, limit - 0.05)])
    # smooth the hip height around the cycle but never above the reach limit
    ys = [s[2] for s in samples]
    for _ in range(3):
        ys = [min(ys[i], (ys[i - 1] + 2 * ys[i] + ys[(i + 1) % keys]) / 4 + 0.0) for i in range(keys)]
    for i, (u, feet, _) in enumerate(samples):
        yaw = 5.0 * math.sin(2 * math.pi * u) * (0.6 if sneak else 1.0)
        roll = -1.6 * math.sin(4 * math.pi * u)
        hp = [0.0, ys[i] - 11.6, -hip_f]
        hr = [0.0, yaw, roll * 0.5]
        c.key("hips", "position", i, hp)
        c.key("hips", "rotation", i, hr)
        for side in ("right", "left"):
            f, y, p, toe = feet[side]
            x = -width if side == "right" else width
            rots, _ = rig.solve_leg(side, hp, hr, (x, y, -f), p)
            for b, v in rots.items():
                c.key(b, "rotation", i, v)
            c.key(side + "_toe", "rotation", i, [toe, 0, 0])
        coat_follow(c, i)
        # upper body: counter-rotate the pelvis yaw, arms swing against the legs
        sw = math.sin(2 * math.pi * u)
        c.key("torso", "rotation", i, [lean, -yaw * 0.8, 0])
        c.key("chest", "rotation", i, [-2 + (2.0 if run else 0.0), -yaw * 0.4, roll * 0.3])
        c.key("neck", "rotation", i, [2, 0, 0])
        c.key("head", "rotation", i, [-5 - lean * 0.8, 0, -roll * 0.2])
        bend = -14 - (30 if run else 0)
        c.pose(i, P(arm("right", [0, 0, 0], [3 + swing * sw, 4, 7], [bend - (6 if run else 2) * max(0.0, -sw), 0, 0], [0, 6, 0]),
                    arm("left", [0, 0, 0], [3 - swing * sw, 4, 7], [bend - (6 if run else 2) * max(0.0, sw), 0, 0], [0, 6, 0])))
    c.pose(0, {"hand_r": "relaxed", "hand_l": "relaxed"})
    return c


def locomotion():
    C = {}
    C["walk"] = gait(WALK_STRIDE, 0.55, 1.5, -20.0, 40.0, -0.9, 0.2, 1.0, 9.0)
    C["run"] = gait(RUN_STRIDE, 0.36, 2.6, -16.0, 44.0, -1.8, 0.4, 6.0, 26.0, keys=16, run=True)
    sneak = gait(SNEAK_STRIDE, 0.62, 1.1, -12.0, 26.0, -2.7, 0.15, 14.0, 5.0, keys=24, sneak=True)
    C["crouch_walk"] = sneak
    # standing poses: idle (breath, weight on the right leg), crouch, air
    c = Clip(80, loop=True)
    st = Stance(hips_pos=(0.25, -0.3, 0), hips_rot=(0, 0, 1.4))
    st.hip(40, (0.32, -0.36, 0), (0, 0, 1.6))
    st.hip(80, (0.25, -0.3, 0), (0, 0, 1.4))
    st.bake(c, (0, 20, 40, 60))
    c.poses([(0, rest()), (40, rest(chest=[-3, 0, 0], head=[-6, 0, 1], **{"R:arm": [2, 4, 8], "L:arm": [2, 4, 8]}))])
    C["idle"] = c
    c = Clip(20, loop=True)
    Stance(right=(-2.3, -1.4), left=(2.3, 1.0), hips_pos=(0, -2.9, 1.0)).bake(c, (0,))
    c.pose(0, P(arm("right", Z, [-6, 4, 10], [-24, 0, 0], [0, 6, 0]), arm("left", Z, [-6, 4, 10], [-24, 0, 0], [0, 6, 0]),
                torso=[14, 0, 0], chest=[2, 0, 0], neck=[0, 0, 0], head=[-14, 0, 0]))
    C["crouch"] = c
    c = Clip(40, loop=True)
    for t, k in ((0, 0.0), (20, 1.0)):
        c.pose(t, P(arm("right", Z, [-8 - 4 * k, 6, 14 + 4 * k], [-22, 0, 0], [0, 6, 0]), arm("left", Z, [-6 - 4 * k, 6, 16 + 3 * k], [-20, 0, 0], [0, 6, 0]),
                    torso=[-1, 0, 0], chest=[-3, 0, 0], head=[-4, 0, 0],
                    right_thigh=[-26, 0, 3], right_shin=[38, 0, 0], right_foot=[-6, 0, 0],
                    left_thigh=[-8, 0, -3], left_shin=[20, 0, 0], left_foot=[-8, 0, 0]))
        c.key("hips", "position", t, [0, 0, 0])
    C["air"] = c
    # land: additive knee give (the difference of a lowered stance and the plain one)
    base = Clip(1)
    Stance().bake(base, (0,), coat=False)
    low = Clip(1)
    Stance(hips_pos=(0, -2.2, 0.4)).bake(low, (0,), coat=False)
    c = Clip(8)
    for t, k in ((0, 0.0), (2, 1.0), (8, 0.0)):
        for b in LEG_BONES:
            for chan in ("rotation", "position"):
                if chan in low.tracks[b]:
                    a, d = base.tracks[b][chan][0], low.tracks[b][chan][0]
                    c.key(b, chan, t, [(y - x) * k for x, y in zip(a, d)])
        c.key("torso", "rotation", t, [6 * k, 0, 0])
        c.key("head", "rotation", t, [-4 * k, 0, 0])
    C["land"] = c
    # turn in place: the lower body catches up with two small steps (poser plays it on a yaw lag)
    for name, sgn in (("turn_l", 1), ("turn_r", -1)):
        c = Clip(10)
        st = Stance()
        first, second = ("left", "right") if sgn > 0 else ("right", "left")
        st.step(first, 0, 5, 2.0 if first == "left" else -2.0, 0.6 if first == "left" else -0.6, lift=1.2)
        st.step(second, 4, 10, -2.0 if second == "right" else 2.0, -0.6 if second == "right" else 0.6, lift=1.0)
        st.hip(5, (0, -0.35, 0)).hip(10, (0, -0.2, 0))
        st.bake(c, every(0, 10, 1))
        C[name] = c
    return C


# ------------------------------------------------------------------ moves
def stance_default():
    return Stance(right=(-2.0, -0.6), left=(2.0, 0.6))


def lion_on():
    c = Clip(8)
    st = stance_default().hip(4, (0, 0.05, 0)).hip(8, (0, -0.2, 0))
    st.bake(c, every(0, 8, 2))
    c.poses([(0, rest()),
             (3, rest(chest=[-6, 0, 0], head=[-10, 0, 0], **arm("right", [0, 0, 4], [-4, -10, 22], [-18, 0, 0], [0, -30, 0]),
                      **arm("left", [0, 0, 4], [-4, -10, 22], [-18, 0, 0], [0, -30, 0]), hand_r="open", hand_l="open")),
             (5, rest(chest=[-7, 0, 0], head=[-9, 0, 0], **arm("right", [0, 0, 4], [-6, -12, 26], [-16, 0, 0], [0, -34, 0]),
                      **arm("left", [0, 0, 4], [-6, -12, 26], [-16, 0, 0], [0, -34, 0]))),
             (8, rest())])
    return c


def lion_off():
    c = Clip(10)
    stance_default().hip(5, (0, -0.4, 0)).hip(10, (0, -0.2, 0)).bake(c, every(0, 10, 2))
    c.poses([(0, rest()), (4, rest(chest=[3, 0, 0], neck=[4, 0, 0], head=[2, 8, 3], **{"R:shoulder": [0, 0, -4], "L:shoulder": [0, 0, -4]})),
             (10, rest())])
    return c


def seizure():
    """The heart stutters: a hand to the chest, knees give, then an irritated recovery."""
    c = Clip(30)
    st = stance_default()
    st.hip(3, (0, -0.8, 0.3), (0, 0, -2)).hip(9, (0, -2.0, 0.8), (0, 0, -3)).hip(18, (0, -1.6, 0.6), (0, 0, -1))
    st.step("left", 8, 13, 2.4, -1.6, lift=0.9).hip(26, (0, -0.3, 0)).hip(30, (0, -0.2, 0))
    st.bake(c, every(0, 30, 2))
    clutch = arm("right", [0, 0, -6], [-50, 34, -6], [-96, 0, 0], [0, -20, -10])
    c.poses([(0, rest()),
             (3, rest(torso=[10, 0, 0], chest=[8, 6, 0], neck=[6, 0, 0], head=[8, -6, 0], **clutch, hand_r="claw",
                      **arm("left", [0, 0, 0], [-10, 0, 22], [-30, 0, 0], [0, 0, -10]), hand_l="open")),
             (9, rest(torso=[20, 0, 0], chest=[12, 8, 0], neck=[8, 0, 0], head=[14, -8, 4], **clutch, hand_r="fist",
                      **arm("left", [0, 0, 0], [-40, 8, 18], [-40, 0, 0], [0, 0, -14]), hand_l="claw")),
             (14, rest(torso=[16, 0, 0], chest=[10, 6, 0], neck=[6, 0, 0], head=[10, -4, -4], **clutch, hand_r="fist",
                       **arm("left", [0, 0, 0], [-30, 8, 18], [-40, 0, 0], [0, 0, -14]))),
             (20, rest(torso=[8, 0, 0], chest=[2, 0, 0], head=[0, 14, 0], **arm("right", [0, 0, 0], [-30, 30, 0], [-80, 0, 0], [0, -20, 0]),
                       hand_r="relaxed")),
             (24, rest(head=[-6, -10, 0])), (30, rest())])
    return c


def touch_backhand():
    """Right backhand across the body, the left foot steps in, hips and chest drive it."""
    c = Clip(12)
    st = stance_default().step("left", 0, 4, 2.2, -1.2, lift=0.8)
    st.hip(3, (0.2, -0.5, 0.2), (0, 4, 0)).hip(6, (-0.2, -0.6, -0.3), (0, -6, 0)).hip(12, (0, -0.25, 0), (0, 0, 0))
    st.bake(c, every(0, 12, 1))
    c.poses([(0, rest()),
             (3, rest(torso=[2, 18, 0], chest=[-2, 14, 0], head=[-5, -26, 0], **arm("right", [0, 0, 0], [-62, 64, -20], [-96, 0, 0], [0, 30, 0]),
                      **arm("left", Z, [-20, 0, 18], [-40, 0, 0], [0, 0, 0]), hand_r="open")),
             (5, rest(torso=[2, -10, 0], chest=[-2, -18, 0], head=[-6, 0, 0], **arm("right", [0, 0, 6], [-80, -26, 40], [-16, 0, 0], [0, -30, -10]),
                      **arm("left", Z, [12, -10, 10], [-30, 0, 0], [0, 0, 0]), hand_r="palm")),
             (7, rest(torso=[1, -14, 0], chest=[-2, -20, 0], head=[-6, 6, 0], **arm("right", [0, 0, 6], [-62, -40, 46], [-14, 0, 0], [0, -34, -10]))),
             (12, rest())])
    return c


def touch_palm():
    """Left palm pushed out from the chest with a forward step and a straight back."""
    c = Clip(13)
    st = stance_default().step("left", 1, 5, 2.1, -3.2, lift=0.9)
    st.hip(4, (0, -0.8, -1.0), (0, -6, 0)).hip(7, (0, -0.9, -1.6), (0, -10, 0)).hip(13, (0, -0.3, -0.4), (0, -2, 0))
    st.bake(c, every(0, 13, 1))
    push = arm("left", [0, 0, 0], [-90, 4, 0], [-4, 0, 0], [-62, 0, 0])
    c.poses([(0, rest()),
             (3, rest(torso=[4, 12, 0], chest=[-2, 8, 0], head=[-6, -10, 0], **arm("left", [0, 0, 0], [-40, -30, 8], [-110, 0, 0], [-40, 0, 0]),
                      **arm("right", Z, [8, 0, 8], [-20, 0, 0], [0, 0, 0]), hand_l="palm")),
             (6, rest(torso=[6, -14, 0], chest=[0, -12, 0], head=[-6, 14, 0], **push, **arm("right", Z, [18, 4, 14], [-16, 0, 0], [0, 0, 0]), hand_l="palm")),
             (8, rest(torso=[5, -12, 0], chest=[0, -10, 0], head=[-6, 12, 0], **push)),
             (13, rest(torso=[0, -2, 0]))])
    return c


def touch_flick():
    """The finisher: the right hand rises to eye height, a slow set, then the flick."""
    c = Clip(16)
    st = stance_default().step("right", 0, 5, -2.0, -2.0, lift=0.7)
    st.hip(5, (0, -0.4, -0.6), (0, 6, 0)).hip(9, (0, -0.55, -0.9), (0, 8, 0)).hip(16, (0, -0.25, -0.2), (0, 2, 0))
    st.bake(c, every(0, 16, 1))
    c.poses([(0, rest()),
             (4, rest(torso=[-2, 10, 0], chest=[-4, 8, 0], head=[-8, -10, 4], **arm("right", [0, 0, 6], [-104, -6, 10], [-70, 0, 0], [20, 0, 0]),
                      **arm("left", [0, 0, -2], [24, 18, 10], [-62, 0, 0], [0, 40, -20]), hand_r="flick_set", hand_l="relaxed")),
             (7, rest(torso=[-2, 12, 0], chest=[-4, 10, 0], head=[-8, -12, 4], **arm("right", [0, 0, 6], [-108, -8, 10], [-66, 0, 0], [26, 0, 0]),
                      hand_r="flick_set")),
             (8, rest(torso=[0, 4, 0], chest=[-3, 0, 0], head=[-8, -4, 2], **arm("right", [0, 0, 6], [-112, -12, 8], [-40, 0, 0], [-10, 0, 0]),
                      hand_r="flick_hit")),
             (11, rest(torso=[0, 4, 0], chest=[-3, 0, 0], head=[-7, -2, 0], **arm("right", [0, 0, 4], [-96, -10, 8], [-50, 0, 0], [-4, 0, 0]),
                       hand_r="flick_hit")),
             (16, rest(**arm("left", [0, 0, -2], [24, 18, 10], [-62, 0, 0], [0, 40, -20])))])
    return c


def sand():
    """Kneels into a crouch, scoops a handful (left hand cupped under), rises and scatters it in
    one flat sweep at the release; the grains stop where they hang."""
    c = Clip(24)
    st = stance_default().step("right", 0, 4, -2.3, -2.4, lift=0.9)
    st.hip(5, (0, -3.0, -0.6), (0, 8, 0)).hip(8, (0, -3.2, -0.6), (0, 8, 0)).hip(11, (0, -0.8, -0.4), (0, 0, 0))
    st.hip(13, (0, -0.6, -0.8), (0, -14, 0)).hip(18, (0, -0.4, -0.6), (0, -12, 0)).hip(24, (0, -0.25, 0), (0, 0, 0))
    st.bake(c, every(0, 24, 1))
    low = P(torso=[30, 6, 0], chest=[12, 6, 0], neck=[2, 0, 0], head=[-20, -4, 0])
    c.poses([(0, rest()),
             (5, rest(**low, **arm("right", Z, [-36, 10, 10], [-30, 0, 0], [30, 0, 0]), **arm("left", Z, [-50, -20, 4], [-30, 0, 0], [0, 0, 20]),
                      hand_r="scoop", hand_l="palm")),
             (8, rest(**low, **arm("right", Z, [-30, 10, 6], [-36, 0, 0], [40, 0, 0]), **arm("left", Z, [-46, -24, 2], [-34, 0, 0], [0, 0, 24]),
                      hand_r="fist", hand_l="scoop")),
             (11, rest(torso=[4, 20, 0], chest=[0, 16, 0], head=[-8, -20, 0], **arm("right", [0, 0, 4], [-70, 70, -20], [-80, 0, 0], [0, 20, 0]),
                       **arm("left", Z, [-30, -10, 10], [-50, 0, 0], [0, 0, 10]), hand_r="fist", hand_l="relaxed")),
             (12, rest(torso=[4, 4, 0], chest=[0, -6, 0], head=[-8, -6, 0], **arm("right", [0, 0, 6], [-86, 10, 30], [-16, 0, 0], [-10, -20, 0]),
                       hand_r="open")),
             (14, rest(torso=[4, -14, 0], chest=[-2, -18, 0], head=[-8, 8, 0], **arm("right", [0, 0, 6], [-80, -40, 50], [-8, 0, 0], [-10, -30, 0]),
                       **arm("left", Z, [10, -4, 12], [-24, 0, 0], [0, 0, 0]), hand_r="open")),
             (19, rest(torso=[2, -10, 0], chest=[-2, -12, 0], head=[-7, 6, 0], **arm("right", [0, 0, 4], [-70, -44, 46], [-10, 0, 0], [0, -20, 0]),
                       hand_r="palm")),
             (24, rest())])
    return c


def air_cut(vertical):
    """Severance of the air. Horizontal: both hands cross at the chest and part in one flat cut.
    Vertical: the right hand rises above the head and cuts straight down, the left pulls back."""
    c = Clip(26)
    st = stance_default()
    if vertical:
        st.step("left", 2, 8, 2.1, -2.6, lift=1.0).hip(8, (0, -0.4, -0.4), (0, -4, 0)).hip(13, (0, -0.3, -0.5), (0, -4, 0))
        st.hip(16, (0, -1.4, -1.4), (0, -2, 0)).hip(20, (0, -1.2, -1.2), (0, -2, 0)).hip(26, (0, -0.3, -0.4), (0, 0, 0))
    else:
        st.step("right", 2, 8, -2.6, -1.4, lift=0.9).step("left", 3, 9, 2.6, 1.0, lift=0.7)
        st.hip(9, (0, -1.4, 0), (0, 10, 0)).hip(14, (0, -1.6, 0), (0, 12, 0)).hip(16, (0, -1.7, -0.4), (0, -12, 0))
        st.hip(21, (0, -1.4, -0.3), (0, -10, 0)).hip(26, (0, -0.35, 0), (0, 0, 0))
    st.bake(c, every(0, 26, 1))
    if vertical:
        up = rest(torso=[-6, 6, 0], chest=[-6, 6, 0], head=[-10, -6, 0], **arm("right", [0, 0, 10], [-168, 0, 10], [-24, 0, 0], [0, -90, 0]),
                  **arm("left", Z, [-40, 0, 30], [-30, 0, 0], [0, 0, 0]), hand_r="palm", hand_l="open")
        c.poses([(0, rest()), (9, up), (13, rest(**{**up, "R:arm": [-178, 0, 8], "R:forearm": [-20, 0, 0]})),
                 (15, rest(torso=[16, -2, 0], chest=[8, -2, 0], head=[-6, 0, 0], **arm("right", [0, 0, 4], [-40, 0, 10], [-8, 0, 0], [0, -90, 0]),
                           **arm("left", Z, [26, 0, 14], [-30, 0, 0], [0, 0, 0]), hand_r="palm", hand_l="relaxed")),
                 (19, rest(torso=[14, -2, 0], chest=[6, -2, 0], head=[-6, 0, 0], **arm("right", [0, 0, 2], [-26, 0, 12], [-10, 0, 0], [0, -90, 0]))),
                 (26, rest())])
    else:
        cross = rest(torso=[2, 16, 0], chest=[-2, 10, 0], head=[-6, -14, 0], **arm("right", [0, 0, 0], [-84, 50, -10], [-60, 0, 0], [0, -60, 0]),
                     **arm("left", [0, 0, 0], [-80, 60, -14], [-66, 0, 0], [0, -60, 0]), hand_r="palm", hand_l="palm")
        c.poses([(0, rest()), (10, cross), (14, rest(**{**cross, "torso": [2, 20, 0], "chest": [-2, 14, 0]})),
                 (15, rest(torso=[2, -6, 0], chest=[-3, -8, 0], head=[-7, 2, 0], **arm("right", [0, 0, 8], [-86, -50, 60], [-6, 0, 0], [0, -80, 0]),
                           **arm("left", [0, 0, 8], [-86, -50, 60], [-6, 0, 0], [0, -80, 0]))),
                 (17, rest(torso=[2, -12, 0], chest=[-3, -12, 0], head=[-7, 6, 0], **arm("right", [0, 0, 8], [-84, -64, 64], [-4, 0, 0], [0, -80, 0]),
                           **arm("left", [0, 0, 8], [-80, -40, 66], [-6, 0, 0], [0, -80, 0]))),
                 (21, rest(torso=[1, -8, 0], chest=[-3, -8, 0], **arm("right", [0, 0, 4], [-50, -40, 54], [-10, 0, 0], [0, -40, 0]),
                           **arm("left", [0, 0, 4], [-50, -40, 54], [-10, 0, 0], [0, -40, 0]), hand_r="relaxed", hand_l="relaxed")),
                 (26, rest())])
    return c


def trample():
    """Trample: the right knee rises high and the heel comes down at tick 8; the whole body
    sinks into the stamp, the coat and hair jump with the shock, hands stay behind the back."""
    c = Clip(22)
    st = stance_default().step("right", 1, 8, -2.1, -1.2, lift=5.6)
    st.hip(5, (0, 0.2, 0.3), (0, -4, 0)).hip(8, (0, -0.9, -0.5), (0, 2, 0)).hip(10, (0, -1.6, -0.6), (0, 2, 0))
    st.hip(13, (0, -1.4, -0.5), (0, 2, 0)).hip(19, (0, -0.4, -0.2), (0, 0, 0)).hip(22, (0, -0.2, 0))
    st.bake(c, every(0, 22, 1))
    lift = rest(torso=[-6, -4, 0], chest=[-4, -2, 0], neck=[2, 0, 0], head=[-10, 4, 0], **BEHIND)
    stamp = rest(torso=[10, 2, 0], chest=[6, 0, 0], neck=[0, 0, 0], head=[-14, 0, 0], **BEHIND)
    c.poses([(0, rest()), (4, lift), (7, rest(**{**lift, "torso": [-2, -2, 0]})), (9, stamp),
             (13, rest(**{**stamp, "torso": [7, 2, 0], "head": [-12, 0, 0]})), (22, rest())])
    # the shock: coat panels and hair jump on top of the leg-following coat keys
    jolt = {0: 0, 7: -0.3, 8: 0.4, 9: 1, 10: 0.8, 12: 0.5, 16: 0.15, 22: 0}
    for t in range(0, 23):
        ks = sorted(jolt)
        k = next(jolt[a] + (jolt[b] - jolt[a]) * (t - a) / (b - a) for a, b in zip(ks, ks[1:]) if a <= t <= b)
        for bone, amp in (("coat_back", 22), ("coat_back_lower", 16), ("coat_front_r", 8), ("coat_front_l", 8)):
            r = c.tracks[bone]["rotation"][t]
            c.key(bone, "rotation", t, [r[0] + amp * k, r[1], r[2]])
    for t, k in ((0, 0), (7, -0.3), (9, 1), (12, 0.5), (16, 0.15), (22, 0)):
        c.key("hair", "rotation", t, [-6 * k, 0, 0]).key("hair_back", "rotation", t, [12 * k, 0, 0])
    return c


def reply():
    """Right of Reply: an open right palm raised at the offender, the left hand on the hip."""
    c = Clip(16)
    st = stance_default().step("right", 0, 4, -2.2, -1.6, lift=0.6)
    st.hip(4, (0, -0.6, -0.3), (0, 6, 0)).hip(14, (0, -0.65, -0.3), (0, 6, 0)).hip(16, (0, -0.25, 0), (0, 0, 0))
    st.bake(c, every(0, 16, 2))
    hold = rest(torso=[-2, 6, 0], chest=[-4, 4, 0], head=[-9, -8, 3], **arm("right", [0, 0, 6], [-96, -6, 8], [-8, 0, 0], [-70, 0, 0]),
                **arm("left", [0, 0, -2], [8, -20, 30], [-90, 0, 0], [0, 30, 20]), hand_r="palm", hand_l="claw")
    c.poses([(0, rest()), (4, hold), (14, rest(**{**hold, "head": [-10, -8, 4]})), (16, rest())])
    return c


def reply_strike():
    c = Clip(10)
    st = stance_default().step("right", 0, 3, -2.2, -2.8, lift=0.7)
    st.hip(3, (0, -0.9, -1.2), (0, 10, 0)).hip(6, (0, -0.9, -1.4), (0, 12, 0)).hip(10, (0, -0.3, -0.2), (0, 2, 0))
    st.bake(c, every(0, 10, 1))
    c.poses([(0, rest(torso=[-2, 6, 0], chest=[-4, 4, 0], **arm("right", [0, 0, 6], [-96, -6, 8], [-8, 0, 0], [-70, 0, 0]), hand_r="palm")),
             (2, rest(torso=[2, 12, 0], chest=[0, 8, 0], head=[-8, -10, 0], **arm("right", [0, 0, 2], [-60, 20, 0], [-90, 0, 0], [-60, 0, 0]))),
             (4, rest(torso=[8, -14, 0], chest=[2, -14, 0], head=[-8, 10, 0], **arm("right", [0, 0, 8], [-94, -4, 0], [-2, 0, 0], [-66, 0, 0]),
                      **arm("left", Z, [20, 4, 16], [-30, 0, 0], [0, 0, 0]), hand_r="palm")),
             (6, rest(torso=[7, -12, 0], chest=[2, -12, 0], head=[-8, 10, 0], **arm("right", [0, 0, 8], [-92, -4, 0], [-4, 0, 0], [-60, 0, 0]))),
             (10, rest())])
    return c


def little_king():
    """Little King: the right hand reaches out, closes gently at the bind and draws the heart
    home to the chest; the head tilts, almost kind."""
    c = Clip(16)
    st = stance_default().step("right", 0, 5, -2.0, -1.8, lift=0.6)
    st.hip(5, (0, -0.4, -0.5), (0, 4, 0)).hip(12, (0, -0.35, -0.3), (0, 2, 0)).hip(16, (0, -0.2, 0))
    st.bake(c, every(0, 16, 2))
    c.poses([(0, rest()),
             (6, rest(torso=[2, 4, 0], chest=[-2, 4, 0], head=[-4, -4, 8], **arm("right", [0, 0, 6], [-88, -4, 6], [-10, 0, 0], [-20, -60, 0]),
                      hand_r="open")),
             (10, rest(torso=[2, 4, 0], chest=[-2, 4, 0], head=[-4, -4, 8], **arm("right", [0, 0, 6], [-84, -4, 6], [-16, 0, 0], [-16, -60, 0]),
                       hand_r="scoop")),
             (13, rest(torso=[-1, 2, 0], chest=[-4, 2, 0], head=[-6, 0, 4], **arm("right", [0, 0, -2], [-40, 40, -4], [-100, 0, 0], [0, -20, 0]),
                       hand_r="fist")),
             (16, rest())])
    return c


def refuse():
    c = Clip(10)
    stance_default().bake(c, (0, 10))
    c.poses([(0, rest()),
             (3, rest(head=[-8, 18, 0], chest=[-2, 6, 0], **arm("right", [0, 0, 4], [-50, -30, 30], [-50, 0, 0], [0, -40, -20]), hand_r="open")),
             (6, rest(head=[-8, 22, 0], chest=[-2, 8, 0], **arm("right", [0, 0, 4], [-40, -60, 50], [-40, 0, 0], [0, -60, 20]), hand_r="open")),
             (10, rest())])
    return c


def ult():
    """Kingdom of One (150 t). ACCUSE 0-23 points the verdict at the target; SCOOP 24-49 gathers
    the stilled air in both hands and lifts it; SENTENCE 50-105 speaks with a hand on every
    wave (58, 68, 78, 88, 98); FLICK 106-125 sets and flicks at 112; RESOLVE 126-149 lowers the
    hands and straightens the collar."""
    c = Clip(150)
    st = stance_default()
    st.step("right", 2, 8, -2.0, -1.6, lift=0.6).hip(8, (0, -0.3, -0.3), (0, 4, 0)).hip(22, (0, -0.3, -0.3), (0, 4, 0))
    st.step("left", 26, 32, 2.6, 0.4, lift=0.7).hip(32, (0, -2.2, 0.4), (0, 0, 0)).hip(40, (0, -2.4, 0.4)).hip(48, (0, -0.4, 0.2))
    st.hip(56, (0, -0.3, 0), (0, 2, 0)).hip(100, (0, -0.35, 0), (0, -2, 0))
    st.step("right", 102, 108, -2.2, -2.4, lift=0.7).hip(108, (0, -0.8, -0.9), (0, 10, 0)).hip(112, (0, -1.0, -1.3), (0, 8, 0))
    st.hip(122, (0, -0.6, -0.6), (0, 4, 0)).step("right", 128, 136, -2.0, -0.6, lift=0.5).hip(136, (0, -0.25, 0)).hip(150, (0, -0.2, 0))
    st.bake(c, every(0, 150, 2))
    point = arm("right", [0, 0, 6], [-94, -4, 6], [-6, 0, 0], [0, 0, 0])
    behind_l = arm("left", [0, 0, -2], [26, 18, 10], [-58, 0, 0], [0, 40, -20])
    rows = [(0, rest()),
            (6, rest(torso=[-2, 4, 0], chest=[-4, 4, 0], head=[-10, -4, 4], **point, **behind_l, hand_r="point", hand_l="claw")),
            (20, rest(torso=[-2, 4, 0], chest=[-4, 4, 0], head=[-11, -4, 6], **point, **behind_l, hand_r="point")),
            (26, rest(torso=[8, 0, 0], chest=[2, 0, 0], head=[-4, 0, 0], **arm("right", Z, [-30, 10, 16], [-30, 0, 0], [20, 0, 0]),
                      **arm("left", Z, [-30, 10, 16], [-30, 0, 0], [20, 0, 0]), hand_r="scoop", hand_l="scoop")),
            (34, rest(torso=[24, 0, 0], chest=[8, 0, 0], head=[-14, 0, 0], **arm("right", Z, [-46, 20, 10], [-20, 0, 0], [30, -60, 0]),
                      **arm("left", Z, [-46, 20, 10], [-20, 0, 0], [30, -60, 0]), hand_r="scoop", hand_l="scoop")),
            (42, rest(torso=[6, 0, 0], chest=[-4, 0, 0], head=[-14, 0, 0], **arm("right", [0, 0, 8], [-120, 20, 20], [-30, 0, 0], [0, -80, 0]),
                      **arm("left", [0, 0, 8], [-120, 20, 20], [-30, 0, 0], [0, -80, 0]), hand_r="open", hand_l="open")),
            (48, rest(torso=[-4, 0, 0], chest=[-8, 0, 0], head=[-16, 0, 0], **arm("right", [0, 0, 12], [-150, 10, 30], [-20, 0, 0], [0, -90, 0]),
                      **arm("left", [0, 0, 12], [-150, 10, 30], [-20, 0, 0], [0, -90, 0]), hand_r="palm", hand_l="palm")),
            (54, rest(torso=[-3, 0, 0], chest=[-4, 0, 0], head=[-9, 0, 0], **arm("left", [0, 0, -2], [26, 18, 10], [-58, 0, 0], [0, 40, -20]),
                      **arm("right", Z, [-40, 0, 14], [-60, 0, 0], [0, -40, 0]), hand_r="open", hand_l="claw"))]
    # sentence: a gesture landing on each wave, alternating an open palm and a raised finger
    for i, w in enumerate((58, 68, 78, 88, 98)):
        g = (rest(torso=[-2, 6, 0], chest=[-4, 6, 0], head=[-9, -6, 4 - 2 * i], **arm("right", [0, 0, 6], [-70, -30, 30], [-30, 0, 0], [0, -70, 0]),
                  **behind_l, hand_r="palm") if i % 2 == 0 else
             rest(torso=[-2, -2, 0], chest=[-4, -2, 0], head=[-8, 4, -2], **arm("right", [0, 0, 6], [-110, 0, 10], [-50, 0, 0], [0, -10, 0]),
                  **behind_l, hand_r="point"))
        rows.append((w - 4, rest(**{**g, "R:arm": [g["R:arm"][0] + 18, g["R:arm"][1], g["R:arm"][2]]})))
        rows.append((w, g))
    rows += [(104, rest(torso=[-2, 8, 0], chest=[-4, 8, 0], head=[-9, -10, 4], **arm("right", [0, 0, 6], [-104, -6, 10], [-70, 0, 0], [20, 0, 0]),
                        **behind_l, hand_r="flick_set")),
             (110, rest(torso=[-1, 12, 0], chest=[-4, 12, 0], head=[-9, -12, 4], **arm("right", [0, 0, 6], [-108, -8, 10], [-66, 0, 0], [28, 0, 0]),
                        hand_r="flick_set")),
             (112, rest(torso=[2, 0, 0], chest=[-2, -4, 0], head=[-8, -2, 2], **arm("right", [0, 0, 8], [-114, -14, 8], [-34, 0, 0], [-14, 0, 0]),
                        hand_r="flick_hit")),
             (118, rest(torso=[2, -2, 0], chest=[-2, -4, 0], head=[-8, 0, 0], **arm("right", [0, 0, 6], [-100, -12, 8], [-44, 0, 0], [-6, 0, 0]),
                        hand_r="flick_hit")),
             (126, rest(**behind_l, hand_r="relaxed")),
             (134, rest(head=[-2, 0, 0], chest=[0, 0, 0], **arm("right", [0, 0, 0], [-40, 30, -4], [-110, 0, 0], [0, -10, 0]), hand_r="fist", **behind_l)),
             (140, rest(head=[-4, 6, 0], **arm("right", [0, 0, 0], [-46, 34, -4], [-116, 0, 0], [0, -10, 0]), hand_r="flick_set")),
             (150, rest())]
    c.poses(rows)
    return c


def moves():
    return {"lion_on": lion_on(), "lion_off": lion_off(), "seizure": seizure(), "touch_backhand": touch_backhand(),
            "touch_palm": touch_palm(), "touch_flick": touch_flick(), "sand": sand(), "air_cut": air_cut(False),
            "air_cut_vertical": air_cut(True), "trample": trample(), "reply": reply(), "reply_strike": reply_strike(),
            "little_king": little_king(), "refuse": refuse(), "ult": ult()}


# ------------------------------------------------------------------ first person (arms model)
FP_R = ([-80, 8, 0], [-10, 0, 0], [4, 0, 0])
FP_L = ([-32, 0, 10], [-30, 0, 0], Z)


def fp(side, a, f, h, sh=None):
    return arm(side, sh, a, f, h)


def fp_rest(**over):
    p = P(fp("right", *FP_R), fp("left", *FP_L), hand_r="relaxed", hand_l="relaxed")
    p.update(over)
    return p


def fp_clips():
    C = {}
    c = Clip(80, loop=True)
    c.poses([(0, fp_rest()), (40, fp_rest(**fp("right", [-82, 8, 0], [-12, 0, 0], [6, 0, 0]), **fp("left", [-33, 0, 10], [-31, 0, 0], Z)))])
    C["fp_idle"] = c

    def mk(name, length, rows):
        clip = Clip(length)
        clip.poses(rows)
        C["fp_" + name] = clip

    mk("lion_on", 8, [(0, fp_rest()), (4, fp_rest(**fp("right", [-60, -10, 26], [-20, 0, 0], [0, -30, 0]), **fp("left", [-60, -10, 26], [-20, 0, 0], [0, -30, 0]),
                                                   hand_r="open", hand_l="open")), (8, fp_rest())])
    mk("lion_off", 10, [(0, fp_rest()), (5, fp_rest(**fp("right", [-60, 8, 0], [-10, 0, 0], [10, 0, 0]))), (10, fp_rest())])
    mk("seizure", 30, [(0, fp_rest()), (4, fp_rest(**fp("right", [-60, 40, -10], [-100, 0, 0], [0, -20, 0]), hand_r="claw")),
                       (10, fp_rest(**fp("right", [-50, 44, -12], [-110, 0, 0], [0, -20, 0]), **fp("left", [-50, 8, 18], [-40, 0, 0], Z), hand_r="fist",
                                    hand_l="claw")), (22, fp_rest(**fp("right", [-56, 30, -6], [-80, 0, 0], Z))), (30, fp_rest())])
    mk("touch_backhand", 12, [(0, fp_rest()), (3, fp_rest(**fp("right", [-80, 60, -16], [-90, 0, 0], [0, 30, 0]), hand_r="open")),
                              (5, fp_rest(**fp("right", [-90, -24, 36], [-14, 0, 0], [0, -30, -10]), hand_r="palm")),
                              (7, fp_rest(**fp("right", [-80, -40, 44], [-12, 0, 0], [0, -34, -10]))), (12, fp_rest())])
    mk("touch_palm", 13, [(0, fp_rest()), (3, fp_rest(**fp("left", [-60, -30, 8], [-100, 0, 0], [-40, 0, 0]), hand_l="palm")),
                          (6, fp_rest(**fp("left", [-92, 8, 0], [-4, 0, 0], [-62, 0, 0]), hand_l="palm")),
                          (8, fp_rest(**fp("left", [-90, 8, 0], [-6, 0, 0], [-60, 0, 0]))), (13, fp_rest())])
    mk("touch_flick", 16, [(0, fp_rest()), (4, fp_rest(**fp("right", [-100, -4, 8], [-60, 0, 0], [20, 0, 0]), hand_r="flick_set")),
                           (7, fp_rest(**fp("right", [-102, -6, 8], [-58, 0, 0], [26, 0, 0]), hand_r="flick_set")),
                           (8, fp_rest(**fp("right", [-104, -8, 6], [-34, 0, 0], [-10, 0, 0]), hand_r="flick_hit")),
                           (11, fp_rest(**fp("right", [-96, -8, 6], [-40, 0, 0], [-4, 0, 0]), hand_r="flick_hit")), (16, fp_rest())])
    mk("sand", 24, [(0, fp_rest()), (5, fp_rest(**fp("right", [-40, 10, 10], [-30, 0, 0], [30, 0, 0]), **fp("left", [-46, -16, 4], [-30, 0, 0], [0, 0, 20]),
                                                hand_r="scoop", hand_l="palm")),
                    (8, fp_rest(**fp("right", [-36, 10, 6], [-36, 0, 0], [40, 0, 0]), hand_r="fist")),
                    (11, fp_rest(**fp("right", [-80, 60, -16], [-70, 0, 0], [0, 20, 0]), hand_r="fist")),
                    (12, fp_rest(**fp("right", [-90, 8, 26], [-14, 0, 0], [-10, -20, 0]), hand_r="open")),
                    (15, fp_rest(**fp("right", [-84, -36, 44], [-8, 0, 0], [-10, -30, 0]))), (24, fp_rest())])
    mk("air_cut", 26, [(0, fp_rest()), (10, fp_rest(**fp("right", [-86, 44, -10], [-56, 0, 0], [0, -60, 0]), **fp("left", [-84, 52, -14], [-60, 0, 0], [0, -60, 0]),
                                                    hand_r="palm", hand_l="palm")),
                       (14, fp_rest(**fp("right", [-86, 50, -10], [-56, 0, 0], [0, -60, 0]), **fp("left", [-84, 56, -14], [-60, 0, 0], [0, -60, 0]),
                                    hand_r="palm", hand_l="palm")),
                       (16, fp_rest(**fp("right", [-88, -50, 56], [-6, 0, 0], [0, -80, 0]), **fp("left", [-88, -50, 56], [-6, 0, 0], [0, -80, 0]))),
                       (21, fp_rest(**fp("right", [-60, -40, 50], [-10, 0, 0], [0, -40, 0]), **fp("left", [-60, -40, 50], [-10, 0, 0], [0, -40, 0]))),
                       (26, fp_rest())])
    mk("air_cut_vertical", 26, [(0, fp_rest()), (9, fp_rest(**fp("right", [-150, 0, 8], [-24, 0, 0], [0, -90, 0]), hand_r="palm")),
                                (13, fp_rest(**fp("right", [-160, 0, 8], [-20, 0, 0], [0, -90, 0]), hand_r="palm")),
                                (15, fp_rest(**fp("right", [-50, 0, 8], [-8, 0, 0], [0, -90, 0]), **fp("left", [-20, 0, 14], [-30, 0, 0], Z))),
                                (19, fp_rest(**fp("right", [-40, 0, 10], [-10, 0, 0], [0, -90, 0]))), (26, fp_rest())])
    brace = fp_rest(**fp("right", [-30, 14, 10], [-36, 0, 0], Z), **fp("left", [-30, 4, 14], [-36, 0, 0], Z))
    mk("trample", 22, [(0, fp_rest()), (6, brace), (8, fp_rest(**fp("right", [-22, 18, 16], [-30, 0, 0], Z), **fp("left", [-22, 8, 20], [-30, 0, 0], Z))),
                       (12, brace), (22, fp_rest())])
    hold = fp_rest(**fp("right", [-94, -4, 6], [-8, 0, 0], [-70, 0, 0]), hand_r="palm")
    mk("reply", 16, [(0, fp_rest()), (4, hold), (14, hold), (16, fp_rest())])
    mk("reply_strike", 10, [(0, hold), (2, fp_rest(**fp("right", [-66, 20, 0], [-80, 0, 0], [-60, 0, 0]), hand_r="palm")),
                            (4, fp_rest(**fp("right", [-96, -4, 0], [-2, 0, 0], [-66, 0, 0]), hand_r="palm")),
                            (6, fp_rest(**fp("right", [-94, -4, 0], [-4, 0, 0], [-60, 0, 0]), hand_r="palm")), (10, fp_rest())])
    mk("little_king", 16, [(0, fp_rest()), (6, fp_rest(**fp("right", [-90, -4, 6], [-10, 0, 0], [-20, -60, 0]), hand_r="open")),
                           (10, fp_rest(**fp("right", [-86, -4, 6], [-16, 0, 0], [-16, -60, 0]), hand_r="scoop")),
                           (13, fp_rest(**fp("right", [-60, 36, -4], [-90, 0, 0], [0, -20, 0]), hand_r="fist")), (16, fp_rest())])
    mk("refuse", 10, [(0, fp_rest()), (3, fp_rest(**fp("right", [-60, -30, 30], [-50, 0, 0], [0, -40, -20]), hand_r="open")),
                      (6, fp_rest(**fp("right", [-50, -60, 50], [-40, 0, 0], [0, -60, 20]), hand_r="open")), (10, fp_rest())])
    rows = [(0, fp_rest()), (6, fp_rest(**fp("right", [-94, -4, 6], [-6, 0, 0], Z), hand_r="point")),
            (20, fp_rest(**fp("right", [-94, -4, 6], [-6, 0, 0], Z), hand_r="point")),
            (34, fp_rest(**fp("right", [-40, 20, 10], [-20, 0, 0], [30, -60, 0]), **fp("left", [-40, 20, 10], [-20, 0, 0], [30, -60, 0]), hand_r="scoop", hand_l="scoop")),
            (48, fp_rest(**fp("right", [-130, 10, 26], [-20, 0, 0], [0, -90, 0]), **fp("left", [-130, 10, 26], [-20, 0, 0], [0, -90, 0]), hand_r="palm", hand_l="palm")),
            (54, fp_rest())]
    for i, w in enumerate((58, 68, 78, 88, 98)):
        g = fp_rest(**fp("right", [-74, -26, 26], [-30, 0, 0], [0, -70, 0]), hand_r="palm") if i % 2 == 0 else \
            fp_rest(**fp("right", [-104, 0, 10], [-46, 0, 0], [0, -10, 0]), hand_r="point")
        rows += [(w - 4, fp_rest()), (w, g)]
    rows += [(104, fp_rest(**fp("right", [-100, -4, 8], [-60, 0, 0], [20, 0, 0]), hand_r="flick_set")),
             (110, fp_rest(**fp("right", [-102, -6, 8], [-58, 0, 0], [28, 0, 0]), hand_r="flick_set")),
             (112, fp_rest(**fp("right", [-106, -10, 6], [-30, 0, 0], [-14, 0, 0]), hand_r="flick_hit")),
             (118, fp_rest(**fp("right", [-96, -10, 6], [-40, 0, 0], [-6, 0, 0]), hand_r="flick_hit")), (126, fp_rest()),
             (150, fp_rest())]
    mk("ult", 150, rows)
    return C


# ------------------------------------------------------------------ output + report
def files():
    out = {}

    def add(name, clips):
        data = {"format_version": "1.8.0", "animations": {k: v.to_json() for k, v in clips.items()}}
        out[os.path.join(ANIM, name + ".animation.json")] = json.dumps(data, separators=(",", ":")) + "\n"

    loco = locomotion()
    mv = moves()
    f = fp_clips()
    add("regulus_walk", {k: loco[k] for k in ("walk",)})
    add("regulus_run", {k: loco[k] for k in ("run", "crouch_walk")})
    add("regulus_base", {k: v for k, v in loco.items() if k not in ("walk", "run", "crouch_walk")})
    add("regulus_moves_a", {k: mv[k] for k in ("lion_on", "lion_off", "seizure")})
    add("regulus_touch", {k: mv[k] for k in ("touch_backhand", "touch_palm", "touch_flick")})
    add("regulus_moves_b", {k: mv[k] for k in ("sand", "air_cut", "air_cut_vertical")})
    add("regulus_moves_c", {k: mv[k] for k in ("trample", "reply", "reply_strike", "little_king", "refuse")})
    add("regulus_ult", {"ult": mv["ult"]})
    fp_a = ("fp_idle", "fp_lion_on", "fp_lion_off", "fp_seizure", "fp_touch_backhand", "fp_touch_palm", "fp_touch_flick", "fp_sand")
    add("regulus_fp", {k: f[k] for k in fp_a})
    add("regulus_fp_b", {k: v for k, v in f.items() if k != "fp_ult" and k not in fp_a})
    add("regulus_fp_ult", {"fp_ult": f["fp_ult"]})
    return out, loco, mv


def json_sample(chan, tick):
    """KeyframeStack.sample on a written channel: catmull-rom where keyed so, else linear."""
    keys = sorted((float(k) * 20.0, v) for k, v in chan.items())
    vals = [(t, v["post"] if isinstance(v, dict) else v, isinstance(v, dict)) for t, v in keys]
    if tick <= vals[0][0]:
        return vals[0][1]
    if tick >= vals[-1][0]:
        return vals[-1][1]
    i = max(j for j in range(len(vals)) if vals[j][0] <= tick)
    (t0, p1, sm), (t1, p2, _) = vals[i], vals[i + 1]
    f = (tick - t0) / (t1 - t0)
    if not sm:
        return [lerp(a, b, f) for a, b in zip(p1, p2)]
    p0 = vals[i - 1][1] if i > 0 else p1
    p3 = vals[i + 2][1] if i + 2 < len(vals) else p2
    return [0.5 * (2 * b + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (-a + 3 * b - 3 * c + d) * f ** 3)
            for a, b, c, d in zip(p0, p1, p2, p3)]


def leg_pose(clip, t):
    js = clip.to_json()["bones"]
    return {b: {ch: json_sample(js[b][ch], t) for ch in js[b]} for b in rig.CHAIN if b in js}


def report(loco, mv):
    """Foot-contact audit on the sampled clips (forward kinematics like the engine): for every
    stance foot, how far its ground contact drifts (slip) and how far the sole floats or sinks."""
    lines = []
    for name, stride_b in (("walk", WALK_STRIDE), ("run", RUN_STRIDE), ("crouch_walk", SNEAK_STRIDE)):
        clip = loco[name]
        stride = stride_b * rig.PX_PER_BLOCK
        n = 96
        worst_slip, worst_sink, flat = 0.0, 0.0, 0
        for side in ("right", "left"):
            anchor = {}
            for i in range(n + 1):
                t = clip.length * i / n
                mats = rig.fk(leg_pose(clip, t))
                pts = dict(zip(("heel", "ball", "tip"), rig.foot_points(mats, side)))
                travel = stride * i / n
                worst_sink = max(worst_sink, -min(p[1] for p in pts.values()))
                if pts["heel"][1] < 0.12 and pts["ball"][1] < 0.12:
                    flat += 1
                for k in ("heel", "ball"):
                    p = pts[k]
                    if p[1] < 0.12:
                        g = (-p[2] + travel, p[0])
                        a = anchor.setdefault(k, g)
                        worst_slip = max(worst_slip, math.hypot(g[0] - a[0], g[1] - a[1]))
                    else:
                        anchor.pop(k, None)
        lines.append("%s: max grounded-contact slip %.2f px (%.4f blocks), max sink %.2f px, flat-foot samples %d/%d"
                     % (name, worst_slip, worst_slip / rig.PX_PER_BLOCK, worst_sink, flat, 2 * (n + 1)))
    for name, clip in list(mv.items()) + [("idle", loco["idle"]), ("crouch", loco["crouch"])]:
        worst = 0.0
        sink = 0.0
        for side in ("right", "left"):
            prev = None
            for t in range(clip.length + 1):
                mats = rig.fk(leg_pose(clip, t))
                heel, ball, tip = rig.foot_points(mats, side)
                low = min(heel[1], ball[1])
                sink = max(sink, -min(low, tip[1]))
                planted = heel[1] < 0.2 and ball[1] < 0.2
                if planted and prev is not None:
                    worst = max(worst, math.hypot(ball[0] - prev[0], ball[2] - prev[2]))
                prev = ball if planted else None
        lines.append("%s: planted-foot drift per tick %.2f px, max sink %.2f px" % (name, worst, sink))
    return lines


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--report", action="store_true")
    args = ap.parse_args()
    out, loco, mv = files()
    stale = []
    for path, text in out.items():
        if args.check:
            if not os.path.isfile(path) or open(path, encoding="utf-8").read() != text:
                stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)
    for p in stale:
        print("stale:", p)
    if args.report:
        for line in report(loco, mv):
            print(line)
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
