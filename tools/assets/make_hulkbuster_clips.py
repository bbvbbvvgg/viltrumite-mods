#!/usr/bin/env python3
"""Hulkbuster Mark 48 clips (rework spec §3, plan H2) on the rig of rig_hulkbuster.py.

Reinhard lessons applied: legs and torso move first and the arm last; easeIn into a contact, easeOut
out of it; a short overshoot and settle (servo stop, not organic softness); every clip starts and ends
on the live base (STANCE) so the layer blend never pops; one clip per step, its length = the server
step (HulkbusterClipsTest checks lengths and that each strike's contact key sits on the contact tick).

Rotation keys are Bedrock degrees ADDED to the rig's rest rotation:
  limbs: X < 0 swings forward/up; torso/chest X > 0 leans forward; chest Y > 0 brings the LEFT
  shoulder forward (winds the right arm back); right arm Z > 0 / left arm Z < 0 lift sideways;
  fingers_l Z < 0 curls the fist, thumb_l X > 0 closes; piston_r position Y < 0 fires the fist.
Positions are model px (Sind units). Run from the repo root:  python3 tools/assets/make_hulkbuster_clips.py
"""
import json
import math
import os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(REPO, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore", "animations", "ironman", "hulkbuster",
                   "mark48.animation.json")

LEG = 34.0  # hip-to-ankle length (px) for the squat solver

IO = "easeInOutSine"
IN = "easeInCubic"
OUTQ = "easeOutQuad"
OUTC = "easeOutCubic"
LIN = "linear"
BACK = "easeOutBack"


def squat(drop, side_bias=0.0):
    """Planted-foot crouch: pelvis down by drop px, knees bend to keep the feet on the ground."""
    drop = max(0.0, drop)
    a = math.degrees(math.acos(max(-1.0, min(1.0, 1.0 - drop / LEG)))) if drop > 0 else 0.0
    out = {"pelvis": {"position": [0, -drop, 0]}}
    for side, bias in (("l", -side_bias), ("r", side_bias)):
        k = a * (1.0 + bias)
        out["thigh_" + side] = {"rotation": [-k, 0, 0]}
        out["shin_" + side] = {"rotation": [2 * k, 0, 0]}
        out["foot_" + side] = {"rotation": [-k, 0, 0]}
    return out


def merge(*poses):
    """Later poses add their channels on top (vectors summed per bone channel)."""
    out = {}
    for p in poses:
        for bone, chans in p.items():
            dst = out.setdefault(bone, {})
            for ch, v in chans.items():
                old = dst.get(ch, [0, 0, 0])
                dst[ch] = [old[i] + v[i] for i in range(3)]
    return out


def r(**bones):
    """Rotation-only pose: r(chest=(x, y, z), ...)."""
    return {b: {"rotation": list(v)} for b, v in bones.items()}


def p(**bones):
    return {b: {"position": list(v)} for b, v in bones.items()}


FIST = r(fingers_l=(0, 0, -75), thumb_l=(45, 0, 0))
OPEN = r(fingers_l=(0, 0, 30), thumb_l=(-35, 0, 0))
STANCE = merge(
    squat(1.5),
    r(torso=(5, 0, 0), chest=(3, 0, 0), head=(-6, 0, 0),
      upper_arm_l=(-16, 0, -7), upper_arm_r=(-16, 0, 7),
      forearm_l=(-32, 0, 0), forearm_r=(-28, 0, 0)),
    FIST)


class Clip:
    def __init__(self, name, length, loop=False):
        self.name = name
        self.length = round(length, 4)
        self.loop = loop
        self.keys = {}

    def at(self, t, pose, ease=IO):
        t = round(min(self.length, max(0.0, t)), 4)
        for bone, chans in pose.items():
            for ch, v in chans.items():
                self.keys.setdefault(bone, {}).setdefault(ch, {})[t] = (v, ease)
        return self

    def json(self):
        bones = {}
        for bone, chans in self.keys.items():
            out = {}
            for ch, keys in chans.items():
                times = sorted(keys)
                # Every channel is closed at 0 and at the end: a missing end key would hold an older value.
                if times[0] > 0:
                    keys[0.0] = (STANCE.get(bone, {}).get(ch, [1, 1, 1] if ch == "scale" else [0, 0, 0]), IO)
                if times[-1] < self.length:
                    keys[self.length] = (keys[times[-1]][0], IO)
                out[ch] = {f"{t:.4f}".rstrip("0").rstrip("."): {"vector": [round(x, 3) for x in keys[t][0]], "easing": keys[t][1]}
                           for t in sorted(keys)}
            bones[bone] = out
        j = {"animation_length": self.length, "bones": bones}
        if self.loop:
            j["loop"] = True
        return j


def full(pose):
    """A pose that also names every STANCE channel (so a clip owns the whole base)."""
    return merge({b: {c: [0, 0, 0] for c in ch} for b, ch in STANCE.items()}, pose)


CLIPS = []


def clip(name, length, loop=False):
    c = Clip(name, length, loop)
    CLIPS.append(c)
    return c


def T(ticks):
    return ticks / 20.0


# ------------------------------------------------------------------ locomotion
def idle():
    c = clip("idle", 5.0, loop=True)
    base = STANCE
    breathe_in = merge(base, r(chest=(-1.2, 0, 0), head=(-1, 0, 0)), p(chest=(0, 0.3, 0)))
    c.at(0, base).at(1.2, breathe_in).at(2.4, base)
    # Servo look-around: a quick head turn and hold (mechanical, step-like ease).
    c.at(2.6, merge(base, r(head=(-4, 9, 0))), OUTC).at(3.3, merge(base, r(head=(-4, 9, 0))), IO)
    c.at(3.6, merge(breathe_in, r(head=(0, 0, 0))), OUTC)
    # Finger flex of the left hand at 4.0 (open a little and re-clench).
    c.at(3.9, merge(base, r(fingers_l=(0, 0, 25), thumb_l=(-15, 0, 0))), OUTQ).at(4.2, base, BACK)
    c.at(5.0, base)


def gait(name, length, thigh, shin, bob, lean, arm, twist):
    c = clip(name, length, loop=True)
    half = length / 2
    for phase, side, other in ((0.0, "l", "r"), (half, "r", "l")):
        # Contact: the front leg lands heavy (pelvis dips), the back leg pushes.
        contact = merge(
            STANCE,
            r(torso=(lean, 0, 0)),
            {"thigh_" + side: {"rotation": [-thigh, 0, 0]}, "shin_" + side: {"rotation": [shin * 0.25, 0, 0]},
             "foot_" + side: {"rotation": [thigh * 0.5, 0, 0]},
             "thigh_" + other: {"rotation": [thigh * 0.8, 0, 0]}, "shin_" + other: {"rotation": [shin * 0.4, 0, 0]},
             "foot_" + other: {"rotation": [-thigh * 0.3, 0, 0]}},
            p(pelvis=(0, -bob, 0)),
            r(chest=(0, twist if side == "l" else -twist, 0)),
            {"upper_arm_" + side: {"rotation": [arm, 0, 0]}, "upper_arm_" + other: {"rotation": [-arm, 0, 0]}},
        )
        # Passing: the swing leg lifts high (knee up), the body rises.
        passing = merge(
            STANCE,
            r(torso=(lean, 0, 0)),
            {"thigh_" + other: {"rotation": [-thigh * 0.55, 0, 0]}, "shin_" + other: {"rotation": [shin, 0, 0]},
             "foot_" + other: {"rotation": [-shin * 0.3, 0, 0]},
             "thigh_" + side: {"rotation": [thigh * 0.15, 0, 0]}, "shin_" + side: {"rotation": [shin * 0.1, 0, 0]}},
            p(pelvis=(0, bob * 0.35, 0)),
            r(chest=(0, 0, -1.5 if side == "l" else 1.5)),
        )
        c.at(phase, contact, OUTQ)
        # The dip right after the strike: the weight lands (servo absorb), then rises.
        c.at(phase + length * 0.08, merge(contact, p(pelvis=(0, -bob * 0.6, 0))), IO)
        c.at(phase + half * 0.5, passing, IO)
    c.at(length, c_first(c))
    return c


def c_first(c):
    pose = {}
    for bone, chans in c.keys.items():
        for ch, keys in chans.items():
            if 0.0 in keys:
                pose.setdefault(bone, {})[ch] = keys[0.0][0]
    return pose


def locomotion():
    idle()
    gait("walk", 1.6, thigh=20, shin=38, bob=1.6, lean=4, arm=9, twist=4)
    gait("run", 1.2, thigh=30, shin=55, bob=2.6, lean=13, arm=24, twist=7)
    ram = gait("ram", 0.8, thigh=32, shin=58, bob=2.4, lean=20, arm=6, twist=0)
    # The ram leads with the left shoulder: lowered chest, arms tucked.
    for t in sorted(ram.keys["pelvis"]["position"]):
        ram.at(t, r(chest=(14, 24, 0), upper_arm_l=(-40, 0, 12), forearm_l=(-70, 0, 0), upper_arm_r=(-8, 0, 18), forearm_r=(-60, 0, 0), head=(-14, -18, 0)))
    c = clip("turn", 0.8, loop=True)
    for t, side in ((0.0, "l"), (0.4, "r")):
        c.at(t, STANCE).at(t + 0.15, merge(STANCE, {"thigh_" + side: {"rotation": [-14, 0, 0]}, "shin_" + side: {"rotation": [26, 0, 0]},
                                                     "foot_" + side: {"rotation": [-8, 0, 0]}}, p(pelvis=(0, 0.6, 0))), OUTQ)
        c.at(t + 0.32, merge(STANCE, p(pelvis=(0, -1.0, 0))), IN)
    c.at(0.8, STANCE)
    # Air: arms out for balance, knees ready.
    fall = merge(STANCE, r(upper_arm_l=(-30, 0, -28), upper_arm_r=(-30, 0, 28), forearm_l=(-20, 0, 0), forearm_r=(-20, 0, 0),
                           thigh_l=(-18, 0, -4), thigh_r=(-8, 0, 4), shin_l=(30, 0, 0), shin_r=(22, 0, 0), head=(14, 0, 0)))
    c = clip("fall", 1.0, loop=True)
    c.at(0, fall).at(0.5, merge(fall, r(upper_arm_l=(-4, 0, -4), upper_arm_r=(-4, 0, 4)))).at(1.0, fall)
    charge = merge(squat(6.5), r(torso=(16, 0, 0), chest=(6, 0, 0), head=(-14, 0, 0), upper_arm_l=(28, 0, -18), upper_arm_r=(28, 0, 18),
                                 forearm_l=(-30, 0, 0), forearm_r=(-30, 0, 0)), FIST)
    c = clip("jet_charge", 1.5)
    c.at(0, STANCE).at(0.35, charge, OUTC)
    for i in range(6):
        c.at(0.5 + i * 0.16, merge(charge, r(chest=(0.8 if i % 2 else -0.8, 0, 0)), p(pelvis=(0, -0.3 if i % 2 else 0.0, 0))), LIN)
    c.at(1.5, merge(charge, r(chest=(0.8, 0, 0))))
    rise = merge(r(torso=(-6, 0, 0), head=(-18, 0, 0), upper_arm_l=(26, 0, -14), upper_arm_r=(26, 0, 14), forearm_l=(-10, 0, 0),
                   forearm_r=(-10, 0, 0), thigh_l=(6, 0, -2), thigh_r=(6, 0, 2), shin_l=(8, 0, 0), shin_r=(8, 0, 0), foot_l=(20, 0, 0),
                   foot_r=(20, 0, 0)), FIST)
    clip("jet_rise", 0.6).at(0, charge).at(0.18, rise, OUTC).at(0.6, rise)
    hover = merge(r(torso=(4, 0, 0), upper_arm_l=(-18, 0, -22), upper_arm_r=(-18, 0, 22), forearm_l=(-15, 0, 0), forearm_r=(-15, 0, 0),
                    hand_l=(40, 0, 0), thigh_l=(-12, 0, -3), thigh_r=(-4, 0, 3), shin_l=(24, 0, 0), shin_r=(16, 0, 0), foot_l=(18, 0, 0),
                    foot_r=(18, 0, 0), head=(10, 0, 0)), OPEN)
    c = clip("hover", 1.0, loop=True)
    c.at(0, hover).at(0.5, merge(hover, p(root=(0, 0.6, 0)), r(upper_arm_l=(-3, 0, -3), upper_arm_r=(-3, 0, 3)))).at(1.0, hover)
    dive = merge(r(pelvis=(70, 0, 0), torso=(14, 0, 0), head=(-40, 0, 0), upper_arm_r=(-150, 0, 6), forearm_r=(-6, 0, 0),
                   upper_arm_l=(-120, 0, -16), forearm_l=(-20, 0, 0), thigh_l=(-40, 0, -6), thigh_r=(-30, 0, 6), shin_l=(70, 0, 0),
                   shin_r=(60, 0, 0)), FIST)
    clip("dive", 0.5).at(0, fall).at(0.25, dive, OUTC).at(0.5, dive)
    land = merge(squat(9.0), r(torso=(24, 0, 0), chest=(6, 0, 0), head=(-20, 0, 0), upper_arm_l=(-30, 0, -32), upper_arm_r=(-30, 0, 32),
                                forearm_l=(-24, 0, 0), forearm_r=(-24, 0, 0)), FIST)
    clip("land", 0.5).at(0, land, LIN).at(0.06, merge(land, p(pelvis=(0, -1.5, 0))), OUTQ).at(0.3, merge(land, squat(-4.0)), IO).at(0.5, STANCE, BACK)
    stagger = merge(STANCE, r(torso=(-16, 0, 0), chest=(-8, 12, 0), head=(-20, -10, 0), upper_arm_l=(-60, 0, -40), upper_arm_r=(10, 0, 36),
                              forearm_l=(-20, 0, 0)), p(pelvis=(0, -2, 0)), OPEN)
    clip("stagger", 1.0).at(0, STANCE).at(0.1, stagger, OUTC).at(0.35, merge(stagger, r(torso=(6, 0, 0))), IO).at(1.0, STANCE, IO)
    # Dash: windup 6 t (lean against, squat), burst 8 t (lean into it, arms back), recovery 6 t (skid, settle).
    leans = {"dash_forward": ((14, 0, 0), (-8, 0, 0)), "dash_back": ((-10, 0, 0), (8, 0, 0)), "dash_left": ((0, 0, 10), (0, 0, -6)),
             "dash_right": ((0, 0, -10), (0, 0, 6))}
    for name, (into, against) in leans.items():
        wind = merge(squat(5.0), r(torso=against, upper_arm_l=(-30, 0, -10), upper_arm_r=(-30, 0, 10)), FIST)
        burst = merge(squat(3.0), r(torso=into, chest=(into[0] * 0.4, 0, into[2] * 0.4), upper_arm_l=(40, 0, -16), upper_arm_r=(40, 0, 16),
                                    forearm_l=(-20, 0, 0), forearm_r=(-20, 0, 0), head=(-into[0] * 0.6, 0, -into[2] * 0.5)), FIST)
        skid = merge(squat(6.0), r(torso=(into[0] * 0.5, 0, into[2] * 0.6), upper_arm_l=(-24, 0, -30), upper_arm_r=(-24, 0, 30)), FIST)
        clip(name, 1.0).at(0, STANCE).at(T(6), wind, OUTC).at(T(8), burst, OUTQ).at(T(14), skid, IO).at(1.0, STANCE, IO)


# ------------------------------------------------------------------ strikes (lengths = HulkMove)
def strikes():
    # combo 1: right hook (jackhammer arm), 24 t, contact 11 t.
    wind = merge(squat(2.5, side_bias=0.2), r(chest=(4, 26, 0), torso=(6, 10, 0), upper_arm_r=(-42, 0, 38), forearm_r=(-80, 0, 0),
                                              upper_arm_l=(-30, 0, -4), forearm_l=(-70, 0, 0), head=(-6, -18, 0)), FIST)
    hit = merge(squat(2.0), r(chest=(6, -26, 0), torso=(8, -12, 0), upper_arm_r=(-92, 0, 12), forearm_r=(-42, 0, 0), thigh_l=(-10, 0, 0),
                              upper_arm_l=(-24, 0, -10), forearm_l=(-60, 0, 0), head=(-6, 12, 0)), p(piston_r=(0, -4.5, 0)), FIST)
    over = merge(hit, r(chest=(0, -6, 0)), p(piston_r=(0, 4.5, 0)))
    c = clip("combo_1", T(24))
    c.at(0, STANCE).at(T(7), wind, OUTC).at(T(10), merge(wind, r(chest=(0, -12, 0), upper_arm_r=(-20, 0, 6))), IN).at(T(11), hit, OUTQ)
    c.at(T(13), over, IO).at(T(18), merge(STANCE, r(chest=(0, -8, 0))), IO).at(T(24), STANCE, IO)
    # combo 2: left straight, 26 t, contact 12 t.
    wind = merge(squat(2.5), r(chest=(4, -22, 0), torso=(4, -8, 0), upper_arm_l=(-30, 0, -14), forearm_l=(-92, 0, 0), upper_arm_r=(-34, 0, 10),
                               forearm_r=(-70, 0, 0)), FIST)
    hit = merge(squat(2.0), r(chest=(8, 24, 0), torso=(10, 10, 0), upper_arm_l=(-96, 0, -4), forearm_l=(-8, 0, 0), thigh_r=(-14, 0, 0),
                              upper_arm_r=(-30, 0, 14), forearm_r=(-80, 0, 0), head=(-8, -10, 0)), FIST)
    c = clip("combo_2", T(26))
    c.at(0, STANCE).at(T(8), wind, OUTC).at(T(11), merge(wind, r(chest=(0, 14, 0), upper_arm_l=(-30, 0, 0))), IN).at(T(12), hit, OUTQ)
    c.at(T(14), merge(hit, r(chest=(0, 4, 0))), IO).at(T(20), merge(STANCE, r(chest=(0, 6, 0))), IO).at(T(26), STANCE, IO)
    # combo 3: double hammer from above, 36 t, contact 18 t (hyper armour in MAIN).
    up = merge(squat(0.5), p(pelvis=(0, 1.0, 0)), r(torso=(-12, 0, 0), chest=(-6, 0, 0), head=(-14, 0, 0), upper_arm_l=(-172, 0, 10),
                                                    upper_arm_r=(-172, 0, -10), forearm_l=(-42, 0, 0), forearm_r=(-42, 0, 0)), FIST)
    slam = merge(squat(6.0), r(torso=(30, 0, 0), chest=(10, 0, 0), head=(-26, 0, 0), upper_arm_l=(-44, 0, 12), upper_arm_r=(-44, 0, -12),
                               forearm_l=(-18, 0, 0), forearm_r=(-18, 0, 0)), p(piston_r=(0, -3.5, 0)), FIST)
    c = clip("combo_3", T(36))
    c.at(0, STANCE).at(T(12), up, OUTC).at(T(15), merge(up, r(upper_arm_l=(-6, 0, 0), upper_arm_r=(-6, 0, 0))), IO).at(T(18), slam, IN)
    c.at(T(19), merge(slam, p(pelvis=(0, -1.2, 0))), OUTQ).at(T(26), merge(slam, p(piston_r=(0, 3.5, 0))), IO).at(T(36), STANCE, IO)
    # Charged punch: windup holds the elbow thruster (60 t hold), release 24 t (contact at its 0, MAIN 6).
    cock = merge(squat(3.5, side_bias=0.25), r(chest=(4, 36, 0), torso=(8, 12, 0), upper_arm_r=(30, 0, 30), forearm_r=(-104, 0, 0),
                                               upper_arm_l=(-50, 0, -6), forearm_l=(-60, 0, 0), head=(-6, -24, 0)), p(piston_r=(0, 2.5, 0)), FIST)
    c = clip("charged_windup", T(60))
    c.at(0, STANCE).at(T(9), cock, OUTC)
    for i in range(1, 25):
        k = min(1.0, i / 12.0)
        c.at(T(9 + i * 2), merge(cock, r(chest=(0, 0.9 * k if i % 2 else -0.9 * k, 0), upper_arm_r=(1.2 * k if i % 2 else -1.2 * k, 0, 0))), LIN)
    c.at(T(60), cock)
    punch = merge(squat(2.0), r(chest=(8, -32, 0), torso=(14, -12, 0), upper_arm_r=(-92, 0, 6), forearm_r=(-4, 0, 0), upper_arm_l=(-10, 0, -20),
                                forearm_l=(-70, 0, 0), thigh_l=(-26, 0, 0), shin_l=(30, 0, 0), thigh_r=(18, 0, 0), head=(-10, 16, 0)),
                  p(piston_r=(0, -6.5, 0)), FIST)
    c = clip("charged_release", T(24))
    c.at(0, merge(cock, r(chest=(0, -18, 0))), IN).at(T(2), punch, OUTQ).at(T(6), merge(punch, r(chest=(0, -4, 0))), IO)
    c.at(T(12), merge(punch, p(piston_r=(0, 6.5, 0))), IO).at(T(24), STANCE, IO)
    # Dash strike: 24 t, contact 6 t (carried by the dash).
    c = clip("dash_strike", T(24))
    c.at(0, merge(squat(3.0), r(torso=(14, 0, 0), chest=(0, 20, 0), upper_arm_r=(10, 0, 24), forearm_r=(-90, 0, 0)), FIST), IO)
    c.at(T(6), punch, IN).at(T(9), merge(punch, r(chest=(0, -4, 0))), OUTQ).at(T(16), merge(punch, p(piston_r=(0, 6.5, 0))), IO).at(T(24), STANCE, IO)
    # Counter: elbow from the block, 20 t, contact 6 t.
    block = merge(squat(3.0), r(torso=(8, 0, 0), chest=(4, 0, 0), head=(6, 0, 0), upper_arm_l=(-84, -8, 26), upper_arm_r=(-84, 8, -26),
                                forearm_l=(-96, 0, 0), forearm_r=(-96, 0, 0)), FIST)
    elbow = merge(squat(2.5), r(torso=(10, -10, 0), chest=(6, -28, 0), upper_arm_r=(-88, 0, -10), forearm_r=(-128, 0, 0), upper_arm_l=(-40, 0, -10),
                                forearm_l=(-80, 0, 0), thigh_l=(-14, 0, 0)), FIST)
    c = clip("counter", T(20))
    c.at(0, block, IN).at(T(6), elbow, OUTQ).at(T(9), merge(elbow, r(chest=(0, -6, 0))), IO).at(T(20), STANCE, IO)
    # Block: raise 4 t, hold loop, lower 6 t.
    clip("block_raise", T(4)).at(0, STANCE).at(T(4), block, OUTC)
    c = clip("block_hold", 1.0, loop=True)
    c.at(0, block).at(0.5, merge(block, r(chest=(0.8, 0, 0)), p(pelvis=(0, -0.3, 0)))).at(1.0, block)
    clip("block_lower", T(6)).at(0, block).at(T(6), STANCE, IO)


# ------------------------------------------------------------------ grab chain
def grab():
    reach = merge(squat(2.5), r(chest=(6, 22, 0), torso=(8, 8, 0), upper_arm_l=(-92, 0, -2), forearm_l=(-6, 0, 0), upper_arm_r=(-30, 0, 12),
                                forearm_r=(-70, 0, 0), head=(-6, -10, 0)), OPEN)
    cock = merge(squat(2.0), r(chest=(2, -10, 0), upper_arm_l=(-22, 0, -8), forearm_l=(-84, 0, 0)), OPEN)
    c = clip("grab_reach", T(16))
    c.at(0, STANCE).at(T(4), cock, OUTC).at(T(10), reach, OUTQ).at(T(16), merge(reach, r(upper_arm_l=(2, 0, 0))), IO)
    miss = merge(reach, r(fingers_l=(0, 0, -105), thumb_l=(80, 0, 0)))
    c = clip("grab_miss", T(24))
    c.at(0, reach).at(T(3), miss, OUTQ).at(T(10), merge(STANCE, r(chest=(0, 8, 0), head=(0, 12, 0))), IO).at(T(24), STANCE, IO)
    hold = merge(squat(2.0), r(chest=(4, 12, 0), torso=(6, 4, 0), upper_arm_l=(-112, 0, -4), forearm_l=(-24, 0, 0), hand_l=(-10, 0, 0),
                               upper_arm_r=(-32, 0, 20), forearm_r=(-84, 0, 0), head=(-10, -6, 0)), FIST)
    clip("grab_lift", T(8)).at(0, reach).at(T(3), merge(reach, FIST), OUTQ).at(T(8), hold, OUTC)
    c = clip("grab_hold", 2.0, loop=True)
    c.at(0, hold).at(0.7, merge(hold, r(upper_arm_l=(-2, 0, 0), chest=(0.6, 0, 0)))).at(1.2, hold).at(1.6, merge(hold, r(fingers_l=(0, 0, -6)))).at(2.0, hold)
    # Face punches: four escalating steps (18 / 17 / 16 / 15 t, contact 10 / 9 / 9 / 8 t).
    for i, (length, contact) in enumerate(((18, 10), (17, 9), (16, 9), (15, 8))):
        k = 1.0 + 0.12 * i
        wind = merge(hold, r(chest=(0, 14 * k, 0), upper_arm_r=(-48, 0, 30), forearm_r=(-112, 0, 0)), p(piston_r=(0, 2.0, 0)))
        hit = merge(hold, r(chest=(4, -12 * k, 0), torso=(4, -4, 0), upper_arm_r=(-102, 0, 4), forearm_r=(-22, 0, 0)), p(piston_r=(0, -5.0 * k, 0)))
        c = clip(f"grab_punch_{i + 1}", T(length))
        c.at(0, hold).at(T(contact - 3), wind, OUTC).at(T(contact), hit, IN).at(T(contact + 1), merge(hit, p(piston_r=(0, 1.5, 0))), OUTQ)
        c.at(T(length), hold, IO)
    # Finisher: 40 t, contact 26 t.
    big = merge(hold, squat(1.5), r(chest=(0, 40, 0), torso=(4, 14, 0), upper_arm_r=(40, 0, 44), forearm_r=(-124, 0, 0), head=(-4, -20, 0)),
                p(piston_r=(0, 3.0, 0)))
    hit = merge(squat(3.5), r(chest=(10, -36, 0), torso=(16, -14, 0), upper_arm_r=(-100, 0, 2), forearm_r=(-10, 0, 0), upper_arm_l=(-80, 0, -20),
                              forearm_l=(-30, 0, 0), thigh_l=(-18, 0, 0), head=(-10, 18, 0)), p(piston_r=(0, -7.0, 0)), OPEN)
    c = clip("grab_finish", T(40))
    c.at(0, hold).at(T(14), big, OUTC)
    for i in range(5):
        c.at(T(16 + i * 2), merge(big, r(chest=(0, 1.2 if i % 2 else -1.2, 0))), LIN)
    c.at(T(24), merge(big, r(chest=(0, -16, 0))), IN).at(T(26), hit, OUTQ).at(T(28), merge(hit, r(chest=(0, -5, 0))), IO)
    c.at(T(32), merge(hit, p(piston_r=(0, 7.0, 0))), IO).at(T(40), STANCE, IO)
    # Throw: 28 t, release 12 t.
    back = merge(squat(2.5), r(chest=(-4, -26, 0), torso=(-6, -10, 0), upper_arm_l=(-150, 0, -22), forearm_l=(-30, 0, 0), head=(-14, 10, 0)), FIST)
    out = merge(squat(2.0), r(chest=(10, 30, 0), torso=(12, 12, 0), upper_arm_l=(-62, 0, -6), forearm_l=(-6, 0, 0), thigh_r=(-12, 0, 0)), OPEN)
    c = clip("grab_throw", T(28))
    c.at(0, hold).at(T(8), back, OUTC).at(T(12), out, IN).at(T(14), merge(out, r(chest=(0, 6, 0))), OUTQ).at(T(28), STANCE, IO)
    # Slam: 32 t, contact 14 t.
    high = merge(p(pelvis=(0, 1.0, 0)), r(torso=(-12, 0, 0), chest=(-6, 10, 0), upper_arm_l=(-176, 0, -6), forearm_l=(-12, 0, 0), upper_arm_r=(-60, 0, 14),
                                          forearm_r=(-60, 0, 0), head=(-20, 0, 0)), FIST)
    down = merge(squat(7.0), r(torso=(36, 0, 0), chest=(10, 8, 0), upper_arm_l=(-30, 0, -10), forearm_l=(-6, 0, 0), upper_arm_r=(-20, 0, 26),
                               head=(-26, 0, 0)), OPEN)
    c = clip("grab_slam", T(32))
    c.at(0, hold).at(T(9), high, OUTC).at(T(14), down, IN).at(T(15), merge(down, p(pelvis=(0, -1.5, 0))), OUTQ).at(T(22), down, IO).at(T(32), STANCE, IO)


# ------------------------------------------------------------------ abilities and the ultimate
def abilities():
    # Heavy repulsors: 60 t (charge 30, beam 16, recoil 14).
    aim = merge(squat(4.0), r(torso=(6, 0, 0), chest=(-2, 0, 0), upper_arm_l=(-88, 0, 8), upper_arm_r=(-88, 0, -8), forearm_l=(-6, 0, 0),
                              forearm_r=(-6, 0, 0), hand_l=(62, 0, 0), hand_r=(50, 0, 0), head=(-4, 0, 0)), OPEN)
    c = clip("heavy_repulsors", T(60))
    c.at(0, STANCE).at(T(8), aim, OUTC)
    for i in range(10):
        k = i / 10.0
        c.at(T(10 + i * 2), merge(aim, r(chest=(0.6 * k if i % 2 else -0.6 * k, 0, 0))), LIN)
    beam = merge(aim, r(torso=(-8, 0, 0), chest=(-4, 0, 0)), p(pelvis=(0, -1.0, 2.0)))
    c.at(T(30), beam, OUTQ)
    for i in range(7):
        c.at(T(32 + i * 2), merge(beam, r(chest=(-1.2 if i % 2 else 1.2, 0, 0))), LIN)
    c.at(T(46), beam).at(T(60), STANCE, IO)
    # Seismic rift: 50 t, fists into the ground at 24 t.
    up = merge(p(pelvis=(0, 1.5, 0)), r(torso=(-16, 0, 0), chest=(-8, 0, 0), head=(-12, 0, 0), upper_arm_l=(-170, 0, 8), upper_arm_r=(-170, 0, -8),
                                          forearm_l=(-30, 0, 0), forearm_r=(-30, 0, 0)), FIST)
    smash = merge(squat(11.0), r(torso=(48, 0, 0), chest=(12, 0, 0), head=(-40, 0, 0), upper_arm_l=(-60, 0, 10), upper_arm_r=(-60, 0, -10),
                                 forearm_l=(-8, 0, 0), forearm_r=(-8, 0, 0)), p(piston_r=(0, -4.0, 0)), FIST)
    c = clip("seismic_rift", T(50))
    c.at(0, STANCE).at(T(16), up, OUTC).at(T(20), merge(up, r(upper_arm_l=(-6, 0, 0), upper_arm_r=(-6, 0, 0))), IO).at(T(24), smash, IN)
    c.at(T(25), merge(smash, p(pelvis=(0, -1.5, 0))), OUTQ).at(T(36), merge(smash, p(piston_r=(0, 4.0, 0))), IO).at(T(50), STANCE, IO)
    # Veronica cage: 24 t, the mark at 8 t (the left hand points at the sky, then at the spot).
    point = merge(STANCE, r(chest=(-4, 18, 0), upper_arm_l=(-150, 0, -14), forearm_l=(-8, 0, 0), head=(-26, -8, 0)), OPEN)
    mark = merge(STANCE, r(chest=(4, 20, 0), upper_arm_l=(-96, 0, -4), forearm_l=(-4, 0, 0), head=(-4, -10, 0)), OPEN)
    clip("veronica_cage", T(24)).at(0, STANCE).at(T(5), point, OUTC).at(T(8), mark, IN).at(T(14), mark).at(T(24), STANCE, IO)
    # Spare arm: 50 t; the broken right arm drops at 20 t, the new one flies in and docks at 40 t.
    limp = merge(STANCE, r(upper_arm_r=(14, 0, 18), forearm_r=(-6, 0, 0), chest=(0, -10, 6), head=(10, 24, 0)))
    c = clip("spare_arm", T(50))
    c.at(0, merge(STANCE, {"upper_arm_r": {"scale": [1, 1, 1]}})).at(T(8), limp, OUTC).at(T(14), merge(limp, r(upper_arm_r=(6, 0, 4))), IO)
    c.at(T(20), merge(limp, r(upper_arm_r=(30, 0, 30)), p(upper_arm_r=(-4, -10, 0)), {"upper_arm_r": {"scale": [1, 1, 1]}}), IN)
    gone = merge(STANCE, r(chest=(-6, -16, 0), head=(-30, 30, 0)), {"upper_arm_r": {"scale": [0.001, 0.001, 0.001], "position": [-10, -40, 0], "rotation": [60, 0, 40]}})
    c.at(T(24), gone, LIN).at(T(30), gone, "step")
    c.at(T(31), merge(STANCE, r(chest=(-6, -16, 0), head=(-30, 30, 0)), {"upper_arm_r": {"scale": [1, 1, 1], "position": [-14, 70, 0], "rotation": [0, 0, 30]}}), OUTC)
    c.at(T(40), merge(STANCE, r(chest=(0, -8, 0)), {"upper_arm_r": {"scale": [1, 1, 1], "position": [0, 0, 0]}}), OUTC)
    c.at(T(41), merge(STANCE, r(chest=(0, -12, 0)), p(pelvis=(0, -1.0, 0))), OUTQ).at(T(50), STANCE, IO)
    # Hero landing (suit-up end): fall loop, impact at 0 (MAIN 2 t), slow rise to 28 t.
    fall = merge(r(torso=(10, 0, 0), head=(26, 0, 0), upper_arm_l=(-40, 0, -34), upper_arm_r=(-60, 0, 30), forearm_l=(-20, 0, 0), forearm_r=(-30, 0, 0),
                   thigh_l=(-36, 0, -4), thigh_r=(-12, 0, 4), shin_l=(50, 0, 0), shin_r=(30, 0, 0)), FIST)
    c = clip("hero_fall", 0.6, loop=True)
    c.at(0, fall).at(0.3, merge(fall, r(upper_arm_l=(-4, 0, -3), upper_arm_r=(-4, 0, 3)))).at(0.6, fall)
    kneel = merge(squat(13.0, side_bias=0.25), r(thigh_l=(10, 0, -6), shin_l=(40, 0, 0), torso=(44, 0, 0), chest=(8, 0, 0), head=(-34, 0, 0),
                                                upper_arm_r=(-38, 0, 8), forearm_r=(-4, 0, 0), upper_arm_l=(-20, 0, -40), forearm_l=(-30, 0, 0)), FIST)
    c = clip("hero_landing", T(28))
    c.at(0, kneel, LIN).at(T(1), merge(kneel, p(pelvis=(0, -1.5, 0))), OUTQ).at(T(10), kneel, IO)
    c.at(T(16), merge(kneel, r(head=(30, 0, 0))), IO).at(T(28), STANCE, IO)
    # Jet ram: 160 t (ignite 30, dash 30-46, candle 46-90, dive 90-110, contact 110, tail to 160).
    ignite = merge(squat(6.5), r(torso=(22, 0, 0), chest=(6, 0, 0), head=(-20, 0, 0), upper_arm_l=(36, 0, -22), upper_arm_r=(36, 0, 22),
                                 forearm_l=(-30, 0, 0), forearm_r=(-30, 0, 0)), FIST)
    dash = merge(r(pelvis=(30, 0, 0), torso=(16, 0, 0), chest=(6, 20, 0), head=(-34, 0, 0), upper_arm_l=(-60, 0, 14), forearm_l=(-70, 0, 0),
                   upper_arm_r=(-88, 0, 8), forearm_r=(-6, 0, 0), thigh_l=(24, 0, 0), thigh_r=(36, 0, 0), shin_l=(30, 0, 0), shin_r=(20, 0, 0)), FIST)
    carry = merge(r(torso=(-4, 0, 0), chest=(2, 12, 0), head=(-6, -6, 0), upper_arm_l=(-112, 0, -4), forearm_l=(-24, 0, 0), upper_arm_r=(-32, 0, 20),
                    forearm_r=(-84, 0, 0), thigh_l=(-10, 0, -3), thigh_r=(2, 0, 3), shin_l=(20, 0, 0), shin_r=(12, 0, 0), foot_l=(24, 0, 0),
                    foot_r=(24, 0, 0)), FIST)
    blow = merge(carry, r(chest=(4, -12, 0), upper_arm_r=(-100, 0, 4), forearm_r=(-20, 0, 0)), p(piston_r=(0, -5.0, 0)))
    divep = merge(r(pelvis=(110, 0, 0), torso=(10, 0, 0), head=(-60, 0, 0), upper_arm_r=(-160, 0, 4), forearm_r=(-4, 0, 0), upper_arm_l=(-130, 0, -10),
                    forearm_l=(-20, 0, 0), thigh_l=(-20, 0, -4), thigh_r=(-10, 0, 4), shin_l=(40, 0, 0), shin_r=(30, 0, 0)), FIST)
    impact = merge(squat(13.0), r(torso=(52, 0, 0), chest=(10, 0, 0), head=(-40, 0, 0), upper_arm_r=(-52, 0, 6), forearm_r=(-6, 0, 0),
                                  upper_arm_l=(-58, 0, -10), forearm_l=(-8, 0, 0)), p(piston_r=(0, -6.0, 0)), OPEN)
    c = clip("jet_ram", T(160))
    c.at(0, STANCE).at(T(14), ignite, OUTC)
    for i in range(7):
        c.at(T(16 + i * 2), merge(ignite, r(chest=(0.8 if i % 2 else -0.8, 0, 0))), LIN)
    c.at(T(30), merge(ignite, r(torso=(8, 0, 0))), IN).at(T(34), dash, OUTQ).at(T(46), dash).at(T(52), carry, IO)
    for k in range(4):
        t0 = 51 + k * 10
        c.at(T(t0 - 3), merge(carry, r(chest=(0, 10, 0), upper_arm_r=(-14, 0, 8)), p(piston_r=(0, 2.0, 0))), OUTC)
        c.at(T(t0), blow, IN).at(T(t0 + 2), merge(blow, p(piston_r=(0, 5.0, 0))), OUTQ).at(T(t0 + 6), carry, IO)
    c.at(T(90), carry).at(T(98), divep, IO).at(T(109), divep).at(T(110), impact, IN).at(T(111), merge(impact, p(pelvis=(0, -1.5, 0))), OUTQ)
    c.at(T(130), impact, IO).at(T(142), merge(impact, r(head=(30, 0, 0))), IO).at(T(160), STANCE, IO)


# ------------------------------------------------------------------ suit-up frame and exit
def scene():
    # The open frame (spec §4.2): legs apart, arms spread, chest doors and the back flap open, helmet split.
    opened = r(thigh_l=(0, 0, -12), thigh_r=(0, 0, 12), shin_l=(4, 0, 0), shin_r=(4, 0, 0),
               upper_arm_l=(-10, 0, -72), upper_arm_r=(-10, 0, 72), forearm_l=(-10, 0, 0), forearm_r=(-10, 0, 0),
               chest_flap_l=(0, -78, 0), chest_flap_r=(0, 78, 0), back_flap=(105, 0, 0),
               shoulder_cap_l=(0, 0, -40), shoulder_cap_r=(0, 0, 40),
               visor=(-70, 0, 0), helmet_l=(0, -55, 0), helmet_r=(0, 55, 0), head=(-10, 0, 0), fingers_l=(0, 0, 30))
    closed = r(thigh_l=(0, 0, 0), thigh_r=(0, 0, 0), shin_l=(0, 0, 0), shin_r=(0, 0, 0), upper_arm_l=(0, 0, 0), upper_arm_r=(0, 0, 0),
               forearm_l=(0, 0, 0), forearm_r=(0, 0, 0), chest_flap_l=(0, 0, 0), chest_flap_r=(0, 0, 0), back_flap=(0, 0, 0),
               shoulder_cap_l=(0, 0, 0), shoulder_cap_r=(0, 0, 0), visor=(0, 0, 0), helmet_l=(0, 0, 0), helmet_r=(0, 0, 0), head=(0, 0, 0),
               fingers_l=(0, 0, 0))
    c = clip("suit_assemble", T(50))
    c.at(0, opened)
    groups = [  # (lock tick, bones)
        (8, ("thigh_l", "thigh_r", "shin_l", "shin_r")),
        (18, ("chest_flap_l", "chest_flap_r", "back_flap")),
        (28, ("upper_arm_l", "upper_arm_r", "forearm_l", "forearm_r", "fingers_l")),
        (36, ("shoulder_cap_l", "shoulder_cap_r")),
        (46, ("visor", "helmet_l", "helmet_r", "head")),
    ]
    for lock, bones in groups:
        o = {b: opened[b] for b in bones}
        cl = {b: closed[b] for b in bones}
        over = {b: {"rotation": [-v * 0.06 for v in opened[b]["rotation"]]} for b in bones}
        c.at(T(max(0, lock - 6)), o, IN).at(T(lock), over, OUTQ).at(T(min(50, lock + 2)), cl, IO)
    c.at(T(50), merge(closed, STANCE))
    # Exit (spec §3: 2.5 s): the back opens, the chest doors crack, the empty machine settles.
    c = clip("exit", T(50))
    c.at(0, STANCE).at(T(12), merge(STANCE, r(back_flap=(105, 0, 0), chest_flap_l=(0, -30, 0), chest_flap_r=(0, 30, 0), torso=(6, 0, 0))), OUTC)
    c.at(T(30), merge(STANCE, squat(3.0), r(back_flap=(105, 0, 0), chest_flap_l=(0, -30, 0), chest_flap_r=(0, 30, 0), head=(16, 0, 0),
                                              upper_arm_l=(10, 0, 6), upper_arm_r=(10, 0, -6))), IO)
    c.at(T(50), merge(STANCE, squat(3.0), r(back_flap=(105, 0, 0), chest_flap_l=(0, -30, 0), chest_flap_r=(0, 30, 0), head=(20, 0, 0),
                                              upper_arm_l=(12, 0, 6), upper_arm_r=(12, 0, -6))))


def main():
    locomotion()
    strikes()
    grab()
    abilities()
    scene()
    data = {"format_version": "1.8.0", "animations": {c.name: c.json() for c in CLIPS}}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
        f.write("\n")
    print(len(CLIPS), "clips")


if __name__ == "__main__":
    main()
