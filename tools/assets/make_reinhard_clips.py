#!/usr/bin/env python3
"""Reinhard pose clips -> animations/reinhard/reinhard.animation.json.

Angles are degrees in the archive/ModelPart convention: the parser negates x and y and
BakedGeoModel negates them again, so the numbers read like a Java ModelPart. For a limb
that hangs down, NEGATIVE x swings it forward; for the body (mass above the pivot)
POSITIVE x leans it forward.

Every clip is authored in four beats (wind-up, strike, contact hold, recovery) and moves
the whole body: torso, head, legs and both arms where the ability allows it. Heavier
contacts hold 2-3 ticks; the clip lengths equal the action lengths in ReinhardRules.

Clips:
- stances (3 s loops, all six bones): idle_restrained, idle_knight, idle_holy.
- knight_left_arm_hold: idle_knight's left arm alone (weighted overlay over the walk), so
  the off hand really stays behind the back while he moves.
- additive body states: crouch, sit (vanilla numbers).
- reactions: parry (0.4 s), swim (1 s loop).
- walk: vanilla-compatible limb phase plus a subtle torso bob and counter-rotation.
- every stage 3-6 ability clip (see ReinhardClips for the names).
- stage 4: Reid draw/sheathe, holy drawn idle, both chop sweeps.

Run: python3 tools/assets/make_reinhard_clips.py
"""
import json
import math
import os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(REPO, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore",
                   "animations", "reinhard", "reinhard.animation.json")
ZERO = [0, 0, 0]


def clip(length, bones, loop="hold_on_last_frame"):
    return {"loop": loop, "animation_length": length, "bones": bones}


def loop(bones, length=3.0):
    return {"loop": True, "animation_length": length, "bones": bones}


def rot(keys):
    return {"rotation": keys}


def rotpos(keys, pos):
    return {"rotation": keys, "position": pos}


def breathe(start, peak, length=3.0):
    """rest -> peak -> rest, the stance breathing loop."""
    return {"0.0": start, str(length / 2): peak, str(length): start}


def still(value, length=3.0):
    return {"0.0": value, str(length): value}


# --- stances -------------------------------------------------------------------------

# The off hand folded behind the back: 65 deg back, a 16 deg inward roll so the hand sits
# near the spine at chest height, half a pixel down and two back.
KNIGHT_LEFT_ARM = {
    "rotation": breathe([65, 12, 16], [66, 12, 15]),
    "position": still([0, -0.5, 1.5]),
}

IDLE_RESTRAINED = loop({
    "body": rotpos(breathe([1, 0, 0], [2, 0, 1]), breathe([0, 0, 0], [0, 0.25, 0])),
    "head": rot(breathe([0, 0, 0], [-1, 2, 1])),
    "right_arm": rot(breathe([-4, 0, 4], [-6, 0, 6])),
    "left_arm": rot(breathe([-4, 0, -4], [-6, 0, -6])),
    "right_leg": rot(still([2, 0, 1])),
    "left_leg": rot(still([-2, 0, -1])),
})

IDLE_KNIGHT = loop({
    "body": rotpos(breathe([3, 8, 0], [4, 9, 1]), breathe([0, 0, 0], [0, 0.2, 0])),
    "head": rot(breathe([-2, -8, 0], [-3, -9, 0])),
    # the fighting hand: a loose guard at chest height, never fully extended
    "right_arm": rot(breathe([-38, -10, 8], [-41, -11, 10])),
    "left_arm": KNIGHT_LEFT_ARM,
    "right_leg": rot(still([5, 0, 2])),
    "left_leg": rot(still([-4, 0, -2])),
})

IDLE_HOLY = loop({
    "body": rotpos(breathe([2, 5, 0], [3, 6, 1]), breathe([0, 0, 0], [0, 0.2, 0])),
    "head": rot(breathe([2, -6, 0], [3, -7, 0])),
    # the left hand rests on the hilt at the left hip
    "left_arm": rotpos(breathe([-26, 26, 20], [-28, 27, 21]), still([0, -0.5, 0])),
    "right_arm": rot(breathe([-5, 0, 4], [-7, 0, 6])),
    "right_leg": rot(still([3, 0, 1])),
    "left_leg": rot(still([-3, 0, -1])),
})

KNIGHT_HOLD = loop({"left_arm": KNIGHT_LEFT_ARM})

CROUCH = loop({
    "body": {"rotation": {"0.0": [28.6479, 0, 0]}, "position": {"0.0": [0, -3.2, 0]}},
    "head": {"position": {"0.0": [0, -4.2, 0]}},
    "right_arm": {"rotation": {"0.0": [22.9183, 0, 0]}, "position": {"0.0": [0, -3.2, 0]}},
    "left_arm": {"rotation": {"0.0": [22.9183, 0, 0]}, "position": {"0.0": [0, -3.2, 0]}},
    "right_leg": {"position": {"0.0": [0, -0.2, 4]}},
    "left_leg": {"position": {"0.0": [0, -0.2, 4]}},
}, 1.0)

SIT = loop({
    "right_arm": rot(still([-36, 0, 0], 1.0)),
    "left_arm": rot(still([-36, 0, 0], 1.0)),
    "right_leg": rot(still([-81, 18, 4.5], 1.0)),
    "left_leg": rot(still([-81, -18, -4.5], 1.0)),
}, 1.0)


def swim_clip():
    def quad(x):
        return -65.0 * x + x * x

    right, left, rleg, lleg = {}, {}, {}, {}
    for unit in range(27):
        f = float(unit % 26) if unit < 26 else 26.0
        if f < 14.0:
            k = quad(f) / quad(14.0)
            x, rz, lz = 0.0, math.pi - 1.8707964 * k, math.pi + 1.8707964 * k
        elif f < 22.0:
            k = (f - 14.0) / 8.0
            x, rz, lz = math.pi / 2 * k, 1.2707963 + 1.8707964 * k, 5.012389 - 1.8707964 * k
        else:
            k = (f - 22.0) / 4.0
            x, rz, lz = math.pi / 2 - math.pi / 2 * k, math.pi, math.pi
        t = ("%.4f" % (unit / 26.0)).rstrip("0").rstrip(".") if unit else "0.0"
        right[t] = [round(math.degrees(x), 3), 180, round(math.degrees(rz), 3)]
        left[t] = [round(math.degrees(x), 3), 180, round(math.degrees(lz), 3)]
        phase = 2.0 * math.pi * unit / 26.0
        rleg[t] = [round(math.degrees(0.3 * math.cos(phase)), 3), 0, 0]
        lleg[t] = [round(math.degrees(0.3 * math.cos(phase + math.pi)), 3), 0, 0]
    return loop({"right_arm": rot(right), "left_arm": rot(left), "right_leg": rot(rleg), "left_leg": rot(lleg)}, 1.0)


PARRY = clip(0.4, {
    "right_arm": rot({"0.0": [-75, -40, 12], "0.08": [-58, -32, 2], "0.4": [-50, -26, 0]}),
    "body": rot({"0.0": [2, -12, 0], "0.4": [1, -6, 0]}),
    "head": rot({"0.0": [-4, 8, 0], "0.4": [0, 4, 0]}),
})


def walk_clip():
    """Vanilla limb phase (right arm -31.5 deg at 0 s, parser-positive 31.5) + torso life."""
    bones = {"right_arm": {"rotation": {}}, "left_arm": {"rotation": {}},
             "right_leg": {"rotation": {}}, "left_leg": {"rotation": {}},
             "body": {"rotation": {}, "position": {}}, "head": {"rotation": {}}}
    samples = 20
    for i in range(samples + 1):
        t = i / samples
        key = "%.3f" % t if i else "0.0"
        if i == samples:
            key = str(1)
        p = 2.0 * math.pi * t
        arm = 31.5127 * math.cos(p)
        leg = 22.0 * math.cos(p)
        bones["right_arm"]["rotation"][key] = [round(-arm, 4), 0, round(3 + 1.5 * math.cos(p), 4)]
        bones["left_arm"]["rotation"][key] = [round(arm, 4), 0, round(-3 + 1.5 * math.cos(p), 4)]
        bones["right_leg"]["rotation"][key] = [round(leg, 4), 0, 0.2865]
        bones["left_leg"]["rotation"][key] = [round(-leg, 4), 0, -0.2865]
        bones["body"]["rotation"][key] = [round(1.5 + 1.0 * math.cos(2 * p), 4), round(3.0 * math.sin(p), 4),
                                          round(1.2 * math.cos(p), 4)]
        bones["body"]["position"][key] = [0, round(0.35 * math.sin(p) * math.sin(p), 4), 0]
        bones["head"]["rotation"][key] = [round(-1.0 * math.cos(2 * p), 4), round(-2.0 * math.sin(p), 4), 0]
    return loop(bones, 1.0)


# --- stage 3 abilities ---------------------------------------------------------------

# Light Punch: 8 t. Quick step-in, straight right jab on tick 3 (0.15), 3 t contact
# hold, snap back; the left shoulder counter-rotates and the lead leg takes the weight.
LIGHT_PUNCH = clip(0.4, {
    "right_arm": rotpos({"0.0": [-18, -6, 6], "0.1": [-42, -8, 8], "0.15": [-92, 2, 2],
                         "0.25": [-92, 2, 2], "0.4": [-30, 0, 4]},
                        {"0.0": [0, 0, 0], "0.15": [0, 0, -2.2], "0.25": [0, 0, -2.2], "0.4": [0, 0, 0]}),
    "left_arm": rot({"0.0": [-10, 0, -6], "0.15": [-18, 0, -10], "0.25": [-18, 0, -10], "0.4": [-6, 0, -4]}),
    "body": rot({"0.0": [2, -10, 0], "0.1": [1, -16, 0], "0.15": [4, 16, 0],
                 "0.25": [4, 16, 0], "0.4": [1, 4, 0]}),
    "head": rot({"0.0": [0, 10, 0], "0.15": [2, -10, 0], "0.25": [2, -10, 0], "0.4": [0, 0, 0]}),
    "right_leg": rot({"0.0": [0, 0, 2], "0.15": [-16, 0, 2], "0.25": [-16, 0, 2], "0.4": [0, 0, 2]}),
    "left_leg": rot({"0.0": [0, 0, -2], "0.15": [12, 0, -2], "0.4": [0, 0, -2]}),
})

# Step Dash: 6 t low forward lean, arms swept back, legs split, then up again.
STEP_DASH = clip(0.3, {
    "body": rotpos({"0.0": [6, 0, 0], "0.1": [26, 0, 0], "0.2": [10, 0, 0], "0.3": [2, 0, 0]},
                   {"0.0": [0, 0, 0], "0.1": [0, -1.2, 0], "0.3": [0, 0, 0]}),
    "head": rot({"0.0": [-4, 0, 0], "0.1": [-20, 0, 0], "0.3": [-2, 0, 0]}),
    "right_arm": rot({"0.0": [12, 0, 8], "0.1": [46, 0, 14], "0.3": [6, 0, 4]}),
    "left_arm": rot({"0.0": [12, 0, -8], "0.1": [46, 0, -14], "0.3": [6, 0, -4]}),
    "right_leg": rot({"0.0": [-10, 0, 0], "0.1": [-34, 0, 2], "0.3": [-2, 0, 0]}),
    "left_leg": rot({"0.0": [12, 0, 0], "0.1": [36, 0, -2], "0.3": [2, 0, 0]}),
})

# Catch: the palm opens in front of the face; the snap closes it and pulls the hand in.
CATCH_READY = clip(0.5, {
    "right_arm": rotpos({"0.0": [-30, -12, 4], "0.1": [-108, -20, 2], "0.5": [-110, -18, 2]},
                        {"0.0": [0, 0, 0], "0.1": [0, 0, -1.5], "0.5": [0, 0, -1.5]}),
    "left_arm": rot({"0.0": [-8, 0, -6], "0.1": [-24, 0, -16], "0.5": [-24, 0, -16]}),
    "body": rot({"0.0": [1, 0, 0], "0.1": [3, 10, 0], "0.5": [3, 10, 0]}),
    "head": rot({"0.0": [0, 0, 0], "0.1": [-4, -8, 0], "0.5": [-4, -8, 0]}),
})
CATCH = clip(0.3, {
    "right_arm": rotpos({"0.0": [-108, -20, 2], "0.06": [-88, -4, 10], "0.16": [-102, -14, 4], "0.3": [-98, -12, 2]},
                        {"0.0": [0, 0, -1.5], "0.06": [0, 0, -0.6], "0.3": [0, 0, -1.0]}),
    "body": rot({"0.0": [3, 10, 0], "0.06": [-2, 16, 0], "0.3": [0, 8, 0]}),
    "head": rot({"0.0": [-4, -8, 0], "0.3": [-3, -6, 0]}),
    "left_leg": rot({"0.0": [0, 0, -2], "0.06": [4, 0, -2], "0.3": [0, 0, -2]}),
})

# Fist Combo: three beats. 1 straight (contact 0.15), 2 hook from outside (0.15),
# 3 heavy overhand with the whole body dropping (contact 0.25).
COMBO_1 = clip(0.4, {
    "right_arm": rotpos({"0.0": [-20, -10, 6], "0.08": [-46, -12, 8], "0.15": [-94, 4, 2],
                         "0.25": [-94, 4, 2], "0.4": [-34, 0, 4]},
                        {"0.0": [0, 0, 0], "0.15": [0, 0, -2.0], "0.25": [0, 0, -2.0], "0.4": [0, 0, 0]}),
    "body": rot({"0.0": [2, -8, 0], "0.15": [4, 18, 0], "0.25": [4, 18, 0], "0.4": [1, 6, 0]}),
    "head": rot({"0.0": [0, 8, 0], "0.15": [2, -12, 0], "0.4": [0, 0, 0]}),
    "right_leg": rot({"0.0": [0, 0, 2], "0.15": [-12, 0, 2], "0.4": [0, 0, 2]}),
})
COMBO_2 = clip(0.4, {
    "right_arm": rot({"0.0": [-40, 56, 44], "0.08": [-62, 64, 48], "0.15": [-92, -34, 6],
                      "0.25": [-92, -34, 6], "0.4": [-28, -6, 4]}),
    "body": rot({"0.0": [0, 20, -2], "0.15": [3, -20, 2], "0.25": [3, -20, 2], "0.4": [1, -4, 0]}),
    "head": rot({"0.0": [0, -10, 0], "0.15": [2, 12, 0], "0.4": [0, 0, 0]}),
    "left_leg": rot({"0.0": [0, 0, -2], "0.15": [10, 0, -4], "0.4": [0, 0, -2]}),
})
COMBO_3 = clip(0.6, {
    "right_arm": rotpos({"0.0": [-30, 0, 10], "0.15": [-168, 0, 14], "0.25": [-58, 0, 4],
                         "0.4": [-54, 0, 4], "0.6": [-16, 0, 4]},
                        {"0.0": [0, 0, 0], "0.15": [0, 1.5, 0], "0.25": [0, -2.0, -2.0], "0.6": [0, 0, 0]}),
    "left_arm": rot({"0.0": [-12, 0, -6], "0.15": [-24, 0, -10], "0.25": [10, 0, -14], "0.6": [-6, 0, -4]}),
    "body": rotpos({"0.0": [4, 0, 0], "0.15": [-12, 6, 0], "0.25": [26, -6, 0], "0.4": [22, -6, 0], "0.6": [4, 0, 0]},
                   {"0.0": [0, 0, 0], "0.15": [0, 1.0, 0], "0.25": [0, -2.4, -1.0], "0.6": [0, 0, 0]}),
    "head": rot({"0.0": [0, 0, 0], "0.15": [-10, 0, 0], "0.25": [-16, 0, 0], "0.6": [-2, 0, 0]}),
    "right_leg": rot({"0.0": [0, 0, 2], "0.25": [-24, 0, 4], "0.6": [-4, 0, 2]}),
    "left_leg": rot({"0.0": [0, 0, -2], "0.25": [18, 0, -4], "0.6": [2, 0, -2]}),
})

# Sky Kick: toss (0-6 t, the leg chambers), kick contact at 0.3 s, hold through the
# hit-stop, launch, recovery.
KICK_TOSS = clip(0.3, {
    "right_arm": rot({"0.0": [-10, 0, 6], "0.15": [-96, 0, 4], "0.3": [-58, 0, 8]}),
    "left_arm": rot({"0.0": [-8, 0, -6], "0.15": [-30, 0, -12], "0.3": [-22, 0, -10]}),
    "body": rotpos({"0.0": [6, 0, 0], "0.15": [-6, 0, 0], "0.3": [-10, 0, 0]},
                   {"0.0": [0, 0, 0], "0.15": [0, 0.6, 0], "0.3": [0, 0.4, 0]}),
    "head": rot({"0.0": [8, 0, 0], "0.3": [-16, 0, 0]}),
    "right_leg": rot({"0.0": [0, 0, 0], "0.2": [-14, 0, 0], "0.3": [-52, 0, 4]}),
    "left_leg": rot({"0.0": [0, 0, 0], "0.3": [8, 0, -2]}),
})
KICK = clip(0.5, {
    "right_leg": rot({"0.0": [-142, 0, 6], "0.12": [-146, 0, 6], "0.25": [-64, 0, 4], "0.5": [-4, 0, 0]}),
    "left_leg": rot({"0.0": [12, 0, -2], "0.12": [12, 0, -2], "0.5": [2, 0, 0]}),
    "body": rotpos({"0.0": [-22, 0, 0], "0.12": [-24, 0, 0], "0.5": [-2, 0, 0]},
                   {"0.0": [0, 1.0, 0], "0.12": [0, 1.0, 0], "0.5": [0, 0, 0]}),
    "right_arm": rot({"0.0": [-28, 0, 30], "0.12": [-28, 0, 30], "0.5": [-4, 0, 6]}),
    "left_arm": rot({"0.0": [16, 0, -22], "0.12": [16, 0, -22], "0.5": [-4, 0, -4]}),
    "head": rot({"0.0": [-26, 0, 0], "0.3": [-32, 0, 0], "0.5": [-8, 0, 0]}),
})

# Grab & Throw: hand closes on the target and holds; the throw cocks back, then drives
# through; the body turns with it and the back leg pushes.
GRAB_HOLD = clip(0.3, {
    "right_arm": rot({"0.0": [-16, 0, 6], "0.1": [-98, -8, 2], "0.3": [-92, -6, 2]}),
    "left_arm": rot({"0.0": [-10, 0, -6], "0.3": [-28, 0, -14]}),
    "body": rot({"0.0": [0, 0, 0], "0.3": [5, -10, 0]}),
    "head": rot({"0.0": [0, 0, 0], "0.3": [8, 0, 0]}),
    "right_leg": rot({"0.0": [0, 0, 2], "0.3": [-10, 0, 2]}),
    "left_leg": rot({"0.0": [0, 0, -2], "0.3": [10, 0, -2]}),
})
THROW = clip(0.6, {
    "right_arm": rot({"0.0": [-152, 0, 10], "0.15": [-162, 0, 10], "0.25": [-38, 0, 4],
                      "0.4": [-18, 0, 2], "0.6": [-4, 0, 3]}),
    "left_arm": rot({"0.0": [-24, 0, -12], "0.25": [12, 0, -18], "0.6": [-6, 0, -4]}),
    "body": rot({"0.0": [-12, 20, 0], "0.15": [-14, 24, 0], "0.25": [18, -16, 0], "0.6": [2, 0, 0]}),
    "head": rot({"0.0": [-8, 0, 0], "0.25": [8, 0, 0], "0.6": [0, 0, 0]}),
    "right_leg": rot({"0.0": [12, 0, 2], "0.25": [-24, 0, 2], "0.6": [-2, 0, 2]}),
    "left_leg": rot({"0.0": [-12, 0, -2], "0.25": [20, 0, -2], "0.6": [2, 0, -2]}),
})

# Dash Behind: vanish lean, the body untwists as he lands turned to the target's back.
DASH_BEHIND = clip(0.4, {
    "body": rotpos({"0.0": [10, 0, 0], "0.08": [30, -16, 0], "0.15": [6, 20, 0], "0.4": [2, 0, 0]},
                   {"0.0": [0, 0, 0], "0.08": [0, -1.4, 0], "0.15": [0, 0.4, 0], "0.4": [0, 0, 0]}),
    "head": rot({"0.0": [-8, 0, 0], "0.08": [-24, 12, 0], "0.15": [-4, -14, 0], "0.4": [0, 0, 0]}),
    "right_arm": rot({"0.0": [22, 0, 10], "0.08": [50, 0, 18], "0.15": [-38, -10, 6], "0.4": [-20, 0, 4]}),
    "left_arm": rot({"0.0": [20, 0, -10], "0.08": [46, 0, -18], "0.15": [-20, 8, -8], "0.4": [-10, 0, -4]}),
    "right_leg": rot({"0.0": [-14, 0, 0], "0.08": [-32, 0, 0], "0.15": [-10, 0, 4], "0.4": [-2, 0, 0]}),
    "left_leg": rot({"0.0": [16, 0, 0], "0.08": [34, 0, 0], "0.15": [12, 0, -4], "0.4": [2, 0, 0]}),
})

# Stomp: the right leg rises (0-0.35), slams on 0.4 s, the body drops into it, recovery.
STOMP = clip(0.7, {
    "right_leg": rot({"0.0": [0, 0, 0], "0.3": [-86, 0, 8], "0.35": [-90, 0, 8], "0.4": [8, 0, 0], "0.7": [0, 0, 0]}),
    "left_leg": rot({"0.0": [0, 0, -2], "0.3": [10, 0, -4], "0.4": [-8, 0, -2], "0.7": [0, 0, -2]}),
    "body": rotpos({"0.0": [0, 0, 0], "0.3": [-10, 0, 2], "0.4": [18, 0, -1], "0.55": [8, 0, 0], "0.7": [0, 0, 0]},
                   {"0.0": [0, 0, 0], "0.3": [0, 0.8, 0], "0.4": [0, -1.6, 0], "0.7": [0, 0, 0]}),
    "head": rot({"0.0": [0, 0, 0], "0.3": [8, 0, 0], "0.4": [-12, 0, 0], "0.7": [0, 0, 0]}),
    "right_arm": rot({"0.0": [0, 0, 6], "0.3": [-22, 0, 44], "0.4": [12, 0, 26], "0.7": [0, 0, 4]}),
    "left_arm": rot({"0.0": [0, 0, -6], "0.3": [18, 0, -40], "0.4": [-8, 0, -22], "0.7": [0, 0, -4]}),
})

# Reflect: back of the hand across the chest for the window, a swat on each reflection.
REFLECT_READY = clip(1.0, {
    "right_arm": rot({"0.0": [-28, 0, 0], "0.12": [-98, 32, -22], "1.0": [-95, 30, -20]}),
    "left_arm": rot({"0.0": [-8, 0, -6], "0.12": [-30, 0, -18], "1.0": [-30, 0, -18]}),
    "body": rot({"0.0": [0, 0, 0], "0.12": [1, -16, 0], "1.0": [1, -14, 0]}),
    "head": rot({"0.0": [0, 0, 0], "0.12": [-2, 8, 0], "1.0": [-2, 8, 0]}),
    "left_leg": rot({"0.0": [0, 0, -2], "0.12": [6, 0, -2], "1.0": [6, 0, -2]}),
})
REFLECT = clip(0.3, {
    "right_arm": rot({"0.0": [-95, 30, -20], "0.1": [-90, -50, 32], "0.3": [-95, 22, -12]}),
    "body": rot({"0.0": [1, -14, 0], "0.1": [2, 22, 0], "0.3": [1, -6, 0]}),
    "head": rot({"0.0": [-2, 8, 0], "0.3": [-2, 4, 0]}),
})

# --- stage 4-6: Reid ----------------------------------------------------------------

# Draw (6 s): the right hand crosses to the hilt, pulls it up along the sheath axis
# (the blade slides out), then lifts it overhead; the feet plant, the torso turns with
# the pull.
REID_DRAW = clip(6.0, {
    "right_arm": rotpos({"0.0": [0, 0, 3], "0.6": [-28, 32, -40], "2.0": [-52, 26, -30],
                         "4.0": [-92, 16, -12], "5.6": [-134, 8, 4], "6.0": [-152, 0, 14]},
                        {"0.0": [0, 0, 0], "3.0": [0, 0.5, -0.5], "6.0": [0, 0, 0]}),
    "left_arm": rot({"0.0": [-4, 0, -4], "0.6": [-24, 24, 18], "4.0": [-26, 26, 18], "6.0": [-12, 6, -2]}),
    "body": rot({"0.0": [0, 0, 0], "0.6": [4, 16, 0], "3.0": [2, 10, 0], "5.6": [-6, 2, 0], "6.0": [-8, 0, 0]}),
    "head": rot({"0.0": [0, 0, 0], "0.6": [20, 16, 0], "4.0": [4, 8, 0], "6.0": [-24, 0, 0]}),
    "right_leg": rot({"0.0": [0, 0, 2], "0.6": [-6, 0, 8], "6.0": [-6, 0, 8]}),
    "left_leg": rot({"0.0": [0, 0, -2], "0.6": [6, 0, -8], "6.0": [6, 0, -8]}),
})
# Sheathe (1 s): the reverse, calm.
REID_SHEATHE = clip(1.0, {
    "right_arm": rot({"0.0": [-152, 0, 14], "0.35": [-70, 24, -26], "0.75": [-30, 32, -40], "1.0": [0, 0, 3]}),
    "left_arm": rot({"0.0": [-12, 6, -2], "0.5": [-24, 24, 18], "1.0": [-4, 0, -4]}),
    "body": rot({"0.0": [-8, 0, 0], "0.35": [2, 12, 0], "0.75": [4, 14, 0], "1.0": [0, 0, 0]}),
    "head": rot({"0.0": [-24, 0, 0], "0.35": [10, 12, 0], "0.75": [18, 12, 0], "1.0": [0, 0, 0]}),
    "right_leg": rot({"0.0": [-6, 0, 8], "1.0": [0, 0, 2]}),
    "left_leg": rot({"0.0": [6, 0, -8], "1.0": [0, 0, -2]}),
})
# Reid drawn idle: the blade held low and forward, the arm breathing with it.
IDLE_HOLY_DRAWN = loop({
    "right_arm": rot(breathe([-44, 0, 10], [-47, 0, 12])),
    "body": rot(breathe([1, 2, 0], [2, 3, 0])),
}, 3.0)


def chop(side):
    """Lethal Chop, 20 t: wind-up out and up (0.25), flat sweep (0.35), hold, return."""
    m = 1 if side == "right" else -1
    arm = side + "_arm"
    other = "left_arm" if side == "right" else "right_arm"
    return clip(1.0, {
        arm: rot({"0.0": [0, 0, 3 * m], "0.25": [-98, 62 * m, 22 * m], "0.35": [-98, -58 * m, -10 * m],
                  "0.5": [-92, -52 * m, -8 * m], "0.65": [-92, -52 * m, -8 * m], "0.9": [0, 0, 3 * m]}),
        other: rot({"0.0": [0, 0, -3 * m], "0.25": [-18, -20 * m, -8 * m], "0.35": [8, 22 * m, 10 * m],
                    "0.9": [0, 0, -3 * m]}),
        "body": rot({"0.0": [0, 0, 0], "0.25": [-4, 28 * m, 0], "0.35": [6, -30 * m, 0],
                     "0.65": [4, -22 * m, 0], "0.9": [0, 0, 0]}),
        "head": rot({"0.0": [0, 0, 0], "0.25": [-2, -12 * m, 0], "0.35": [4, 10 * m, 0], "0.9": [0, 0, 0]}),
        "right_leg": rot({"0.0": [0, 0, 2], "0.35": [-12, 0, 4], "0.9": [0, 0, 2]}),
        "left_leg": rot({"0.0": [0, 0, -2], "0.35": [10, 0, -4], "0.9": [0, 0, -2]}),
    })


# Sheath Strike (0.6 s): both hands on the sheathed sword, one flat arc left to right.
SHEATH_STRIKE = clip(0.6, {
    "right_arm": rot({"0.0": [0, 0, 3], "0.15": [-62, 42, -22], "0.3": [-88, -52, 12],
                      "0.4": [-82, -62, 14], "0.6": [0, 0, 3]}),
    "left_arm": rot({"0.0": [0, 0, -3], "0.15": [-52, 46, 0], "0.3": [-82, -38, 16],
                     "0.4": [-78, -48, 16], "0.6": [0, 0, -3]}),
    "body": rotpos({"0.0": [0, 0, 0], "0.15": [-2, 32, 0], "0.3": [4, -32, 0], "0.4": [4, -34, 0], "0.6": [0, 0, 0]},
                   {"0.0": [0, 0, 0], "0.3": [0, -0.8, 0], "0.6": [0, 0, 0]}),
    "head": rot({"0.0": [0, 0, 0], "0.15": [0, -16, 0], "0.3": [2, 14, 0], "0.6": [0, 0, 0]}),
    "right_leg": rot({"0.0": [0, 0, 2], "0.3": [-12, 0, 4], "0.6": [0, 0, 2]}),
    "left_leg": rot({"0.0": [0, 0, -2], "0.3": [10, 0, -4], "0.6": [0, 0, -2]}),
})

# Rain of Cuts (1 s): three fast swings, the body whipping with each; the belt turns.
CUT_RAIN = clip(1.0, {
    "right_arm": rot({"0.0": [-44, 0, 10], "0.15": [-104, 54, 22], "0.3": [-98, -58, -10],
                      "0.45": [-104, 54, 22], "0.6": [-98, -58, -10], "0.75": [-104, 48, 18], "1.0": [-44, 0, 10]}),
    "left_arm": rot({"0.0": [0, 0, -4], "0.3": [-20, 0, -18], "0.6": [-18, 0, -14], "1.0": [0, 0, -4]}),
    "body": rot({"0.0": [2, 0, 0], "0.15": [4, 22, 0], "0.3": [4, -22, 0], "0.45": [4, 22, 0],
                 "0.6": [4, -22, 0], "0.75": [4, 16, 0], "1.0": [2, 0, 0]}),
    "head": rot({"0.0": [0, 0, 0], "0.3": [0, 8, 0], "0.6": [0, -8, 0], "1.0": [0, 0, 0]}),
    "right_leg": rot({"0.0": [0, 0, 2], "0.3": [-8, 0, 3], "0.6": [-8, 0, 3], "1.0": [0, 0, 2]}),
})

# World Cut: one-second overhead downswing, then a three-second low hold.
WORLD_CUT = clip(4.0, {
    "right_arm": rot({"0.0": [-44, 0, 10], "0.5": [-172, 0, 16], "1.0": [-56, 0, 8], "4.0": [-56, 0, 8]}),
    "left_arm": rot({"0.0": [0, 0, -4], "0.5": [-164, -12, -16], "1.0": [-52, -12, -8], "4.0": [-52, -12, -8]}),
    "body": rotpos({"0.0": [2, 0, 0], "0.5": [-14, 0, 0], "1.0": [14, 0, 0], "4.0": [14, 0, 0]},
                   {"0.0": [0, 0, 0], "0.5": [0, 1.2, 0], "1.0": [0, -2.0, 0], "4.0": [0, -2.0, 0]}),
    "head": rot({"0.0": [0, 0, 0], "0.5": [10, 0, 0], "1.0": [-10, 0, 0], "4.0": [-10, 0, 0]}),
    "right_leg": rot({"0.0": [0, 0, 2], "1.0": [-16, 0, 4], "4.0": [-16, 0, 4]}),
    "left_leg": rot({"0.0": [0, 0, -2], "1.0": [14, 0, -4], "4.0": [14, 0, -4]}),
})

# World Erasure: Reid rises before the face, waits, one cut down/across, then the hold.
WORLD_ERASURE = clip(7.5, {
    "right_arm": rot({"0.0": [-44, 0, 10], "0.75": [-78, -20, 12], "1.5": [-78, -20, 12],
                      "2.0": [-52, 44, 36], "7.5": [-52, 44, 36]}),
    "left_arm": rot({"0.0": [0, 0, -4], "0.75": [-42, 12, -10], "1.5": [-42, 12, -10],
                     "2.0": [-26, -10, -6], "7.5": [-26, -10, -6]}),
    "body": rotpos({"0.0": [2, 0, 0], "1.5": [2, 0, 0], "2.0": [10, 12, 0], "7.5": [10, 12, 0]},
                   {"0.0": [0, 0, 0], "1.5": [0, 0.4, 0], "2.0": [0, 0, 0], "7.5": [0, 0, 0]}),
    "head": rot({"0.0": [0, 0, 0], "1.5": [0, 0, 0], "2.0": [-4, -6, 0], "7.5": [-4, -6, 0]}),
    "right_leg": rot({"0.0": [0, 0, 2], "2.0": [-10, 0, 4], "7.5": [-10, 0, 4]}),
    "left_leg": rot({"0.0": [0, 0, -2], "2.0": [8, 0, -4], "7.5": [8, 0, -4]}),
})


CLIPS = {
    "idle_restrained": IDLE_RESTRAINED,
    "idle_knight": IDLE_KNIGHT,
    "idle_holy": IDLE_HOLY,
    "knight_left_arm_hold": KNIGHT_HOLD,
    "crouch": CROUCH,
    "sit": SIT,
    "swim": swim_clip(),
    "parry": PARRY,
    "walk": walk_clip(),
    "light_punch": LIGHT_PUNCH,
    "step_dash": STEP_DASH,
    "catch_ready": CATCH_READY,
    "catch": CATCH,
    "combo_1": COMBO_1,
    "combo_2": COMBO_2,
    "combo_3": COMBO_3,
    "kick_toss": KICK_TOSS,
    "kick": KICK,
    "grab_hold": GRAB_HOLD,
    "throw": THROW,
    "dash_behind": DASH_BEHIND,
    "stomp": STOMP,
    "reflect_ready": REFLECT_READY,
    "reflect": REFLECT,
    "reid_draw": REID_DRAW,
    "reid_sheathe": REID_SHEATHE,
    "idle_holy_drawn": IDLE_HOLY_DRAWN,
    "chop_right": chop("right"),
    "chop_left": chop("left"),
    "sheath_strike": SHEATH_STRIKE,
    "cut_rain": CUT_RAIN,
    "world_cut": WORLD_CUT,
    "world_erasure": WORLD_ERASURE,
}


def main():
    data = {"format_version": "1.8.0", "animations": {}}
    if os.path.exists(OUT):
        with open(OUT, encoding="utf-8") as f:
            data = json.load(f)
    data["animations"].update(CLIPS)
    data["animations"] = dict(sorted(data["animations"].items()))
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print("wrote", os.path.relpath(OUT, REPO), "clips:", ", ".join(data["animations"]))


if __name__ == "__main__":
    main()
