#!/usr/bin/env python3
"""Reinhard pose clips -> animations/reinhard/reinhard.animation.json (merged with `walk`).

Angles in degrees in Java ModelPart convention (the parser's x/y negation and
BakedGeoModel's (-x, -y, z) cancel: same numbers = same turn as a ModelPart).
Positions in px: +y up, +z backwards (the model faces -z).

Stances (loop, all six bones, breathing 3 s):
- idle_restrained: relaxed, arms free with vanilla's idle bob range, slight head sway.
- idle_knight: left arm folded behind the back (rotX +25, rotZ -10, +2 px back), right arm loose.
- idle_holy: left hand forward/inward to the hilt at the left hip, right arm loose.
- knight_left_arm_hold: idle_knight's left arm alone (overlay over the walk in Knight).
Additive clips (added to the current pose, like vanilla's model code):
- crouch: vanilla HumanoidModel crouching (body 0.5 rad, arms +0.4 rad, body/arms 3.2 px down,
  head 4.2 px down, legs 4 px back and 0.2 px down).
- sit: vanilla riding (arms -36 deg, legs -81 deg, yaw +-18 deg, roll +-4.5 deg).
Reactions (hold on the last frame, overlay weighted by a real-time pulse in ReinhardRenderer):
- parry: Blessing of the Holy Sword (stage 2): the right arm snaps up and across the chest
  like vanilla's old sword block (-55 / -30 deg) with a slight torso twist, 0.4 s.
Swim (loop 1 s = vanilla's 26 walk-position units, overlay weighted by swimAmount):
- swim: HumanoidModel's crawl stroke sampled every unit (arms) and a leg flutter
  0.3 rad (vanilla's leg period 6 pi units is shown once per stroke).

Stage 4 (overlay driven by Reid's phase clock, ReinhardPoses.reidClip):
- chop_left / chop_right: Lethal Chop (the kit chop) sweep of each hand, 1 s.
- idle_holy_drawn: loop, the right arm holds Reid low and forward.
- reid_draw: 6 s slow draw from the left hip, standing still; reid_sheathe: 1 s reverse.

Run: python3 tools/assets/make_reinhard_clips.py
"""
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(REPO, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore",
                   "animations", "reinhard", "reinhard.animation.json")
BREATH = 3.0
ZERO = [0, 0, 0]


def breathe(rest, peak):
    """Loop rest -> peak -> rest over BREATH seconds."""
    return {"0.0": rest, str(BREATH / 2): peak, str(BREATH): rest}


def still(v):
    return {"0.0": v, str(BREATH): v}


def loop(bones, length=BREATH):
    return {"loop": True, "animation_length": length, "bones": bones}


KNIGHT_LEFT_ARM = {
    "rotation": breathe([25, 0, -10], [26, 0, -10]),
    "position": still([0, 0, 2]),
}

CLIPS = {
    "idle_restrained": loop({
        "head": {"rotation": breathe([0, 0, 0], [-1.5, 0, 1])},
        "body": {"rotation": still(ZERO)},
        "right_arm": {"rotation": breathe([0, 0, 3], [-1, 0, 5])},
        "left_arm": {"rotation": breathe([0, 0, -3], [-1, 0, -5])},
        "right_leg": {"rotation": still(ZERO)},
        "left_leg": {"rotation": still(ZERO)},
    }),
    "idle_knight": loop({
        "head": {"rotation": breathe([0, 0, 0], [-1, 0, 0])},
        "body": {"rotation": still(ZERO)},
        "right_arm": {"rotation": breathe([-2, 0, 3], [-3, 0, 4.5])},
        "left_arm": KNIGHT_LEFT_ARM,
        "right_leg": {"rotation": still(ZERO)},
        "left_leg": {"rotation": still(ZERO)},
    }),
    "idle_holy": loop({
        "head": {"rotation": breathe([0, 0, 0], [-1, 0, 0])},
        "body": {"rotation": still(ZERO)},
        "right_arm": {"rotation": breathe([0, 0, 3], [-1, 0, 5])},
        "left_arm": {"rotation": breathe([-15, 0, 5], [-16, 0, 5])},
        "right_leg": {"rotation": still(ZERO)},
        "left_leg": {"rotation": still(ZERO)},
    }),
    "knight_left_arm_hold": loop({"left_arm": KNIGHT_LEFT_ARM}),
    "crouch": loop({
        "body": {"rotation": [28.6479, 0, 0], "position": [0, -3.2, 0]},
        "head": {"position": [0, -4.2, 0]},
        "right_arm": {"rotation": [22.9183, 0, 0], "position": [0, -3.2, 0]},
        "left_arm": {"rotation": [22.9183, 0, 0], "position": [0, -3.2, 0]},
        "right_leg": {"position": [0, -0.2, 4]},
        "left_leg": {"position": [0, -0.2, 4]},
    }, 1.0),
    "sit": loop({
        "right_arm": {"rotation": [-36, 0, 0]},
        "left_arm": {"rotation": [-36, 0, 0]},
        "right_leg": {"rotation": [-81, 18, 4.5]},
        "left_leg": {"rotation": [-81, -18, -4.5]},
    }, 1.0),
}


def swim_clip():
    import math

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
        t = "%.4f" % (unit / 26.0)
        t = t.rstrip("0").rstrip(".") if unit else "0.0"
        right[t] = [round(math.degrees(x), 3), 180, round(math.degrees(rz), 3)]
        left[t] = [round(math.degrees(x), 3), 180, round(math.degrees(lz), 3)]
        phase = 2.0 * math.pi * unit / 26.0
        rleg[t] = [round(math.degrees(0.3 * math.cos(phase)), 3), 0, 0]
        lleg[t] = [round(math.degrees(0.3 * math.cos(phase + math.pi)), 3), 0, 0]
    return loop({"right_arm": {"rotation": right}, "left_arm": {"rotation": left},
                 "right_leg": {"rotation": rleg}, "left_leg": {"rotation": lleg}}, 1.0)


CLIPS["swim"] = swim_clip()
CLIPS["parry"] = {"loop": "hold_on_last_frame", "animation_length": 0.4, "bones": {
    "right_arm": {"rotation": {"0.0": [-75, -40, 12], "0.1": [-55, -30, 0], "0.4": [-50, -26, 0]}},
    "body": {"rotation": {"0.0": [0, -10, 0], "0.4": [0, -6, 0]}},
}}

# ---- stage 3 action overlays (one-shots; times in seconds, 20 t = 1 s) ----
# Light Punch: 8 t right jab, the contact on tick 3 (0.15 s).
CLIPS["light_punch"] = {"loop": "hold_on_last_frame", "animation_length": 0.4, "bones": {
    "right_arm": {"rotation": {"0.0": [-45, -12, 6], "0.15": [-90, 6, 0], "0.25": [-84, 4, 0], "0.4": [-20, 0, 3]},
                  "position": {"0.0": [0, 0, 0], "0.15": [0, 0, -1.5], "0.4": [0, 0, 0]}},
    "body": {"rotation": {"0.0": [0, -6, 0], "0.15": [0, 14, 0], "0.4": [0, 2, 0]}},
    "left_arm": {"rotation": {"0.0": [-30, 0, -6], "0.15": [-20, 0, -8], "0.4": [0, 0, -3]}},
    "head": {"rotation": {"0.0": [0, 4, 0], "0.15": [0, -10, 0], "0.4": [0, 0, 0]}},
}}

# Step Dash: 6 t forward lean, arms swept back, then upright again.
CLIPS["step_dash"] = {"loop": "hold_on_last_frame", "animation_length": 0.3, "bones": {
    "body": {"rotation": {"0.0": [8, 0, 0], "0.1": [24, 0, 0], "0.3": [4, 0, 0]}},
    "head": {"rotation": {"0.0": [-6, 0, 0], "0.1": [-18, 0, 0], "0.3": [-3, 0, 0]}},
    "right_arm": {"rotation": {"0.0": [20, 0, 8], "0.1": [42, 0, 12], "0.3": [8, 0, 4]}},
    "left_arm": {"rotation": {"0.0": [20, 0, -8], "0.1": [42, 0, -12], "0.3": [8, 0, -4]}},
    "right_leg": {"rotation": {"0.0": [-12, 0, 0], "0.1": [-28, 0, 0], "0.3": [-4, 0, 0]}},
    "left_leg": {"rotation": {"0.0": [14, 0, 0], "0.1": [30, 0, 0], "0.3": [4, 0, 0]}},
}}

# Catch Projectile: the palm held up in front of the face for the 10 t window...
CLIPS["catch_ready"] = {"loop": "hold_on_last_frame", "animation_length": 0.5, "bones": {
    "right_arm": {"rotation": {"0.0": [-60, -10, 0], "0.08": [-112, -18, 0], "0.5": [-110, -16, 0]}},
    "body": {"rotation": {"0.0": [0, 0, 0], "0.08": [0, 8, 0], "0.5": [0, 8, 0]}},
    "head": {"rotation": {"0.0": [0, 0, 0], "0.08": [-4, -6, 0], "0.5": [-4, -6, 0]}},
}}
# ...and the hand closing on the projectile: knocked back a little, then held.
CLIPS["catch"] = {"loop": "hold_on_last_frame", "animation_length": 0.3, "bones": {
    "right_arm": {"rotation": {"0.0": [-110, -16, 0], "0.05": [-92, -6, 8], "0.15": [-104, -12, 2], "0.3": [-100, -10, 0]}},
    "body": {"rotation": {"0.0": [0, 8, 0], "0.05": [-3, 12, 0], "0.3": [0, 8, 0]}},
    "head": {"rotation": {"0.0": [-4, -6, 0], "0.3": [-4, -6, 0]}},
}}

# Fist Combo (Knight, one-handed: the left arm stays behind the back, so these
# clips never touch it). Contacts: combo_1/2 on tick 3 (0.15 s), combo_3 on tick 5 (0.25 s).
CLIPS["combo_1"] = {"loop": "hold_on_last_frame", "animation_length": 0.4, "bones": {
    "right_arm": {"rotation": {"0.0": [-50, -14, 6], "0.15": [-92, 4, 0], "0.25": [-86, 2, 0], "0.4": [-30, 0, 4]},
                  "position": {"0.0": [0, 0, 0], "0.15": [0, 0, -1.5], "0.4": [0, 0, 0]}},
    "body": {"rotation": {"0.0": [0, -8, 0], "0.15": [0, 16, 0], "0.4": [0, 4, 0]}},
    "head": {"rotation": {"0.0": [0, 6, 0], "0.15": [0, -12, 0], "0.4": [0, 0, 0]}},
}}
CLIPS["combo_2"] = {"loop": "hold_on_last_frame", "animation_length": 0.4, "bones": {
    "right_arm": {"rotation": {"0.0": [-70, 50, 40], "0.15": [-90, -35, 10], "0.25": [-84, -40, 6], "0.4": [-30, -6, 4]}},
    "body": {"rotation": {"0.0": [0, 18, 0], "0.15": [0, -16, 0], "0.4": [0, -4, 0]}},
    "head": {"rotation": {"0.0": [0, -8, 0], "0.15": [0, 10, 0], "0.4": [0, 0, 0]}},
}}
CLIPS["combo_3"] = {"loop": "hold_on_last_frame", "animation_length": 0.6, "bones": {
    "right_arm": {"rotation": {"0.0": [-60, 0, 10], "0.15": [-165, 0, 14], "0.25": [-55, 0, 4], "0.4": [-50, 0, 4], "0.6": [-20, 0, 4]}},
    "body": {"rotation": {"0.0": [0, 0, 0], "0.15": [-10, 6, 0], "0.25": [22, -4, 0], "0.4": [18, -4, 0], "0.6": [4, 0, 0]}},
    "head": {"rotation": {"0.0": [0, 0, 0], "0.15": [-8, 0, 0], "0.25": [-14, 0, 0], "0.6": [-3, 0, 0]}},
    "right_leg": {"rotation": {"0.0": [0, 0, 0], "0.25": [-22, 0, 4], "0.6": [-6, 0, 0]}},
    "left_leg": {"rotation": {"0.0": [0, 0, 0], "0.25": [16, 0, -4], "0.6": [4, 0, 0]}},
}}

# Sky Kick: a light underhand toss (6 t, the leg chambers at the end) ...
CLIPS["kick_toss"] = {"loop": "hold_on_last_frame", "animation_length": 0.3, "bones": {
    "right_arm": {"rotation": {"0.0": [-15, 0, 6], "0.15": [-95, 0, 4], "0.3": [-60, 0, 8]}},
    "body": {"rotation": {"0.0": [6, 0, 0], "0.15": [-4, 0, 0], "0.3": [-6, 0, 0]}},
    "right_leg": {"rotation": {"0.0": [0, 0, 0], "0.2": [-10, 0, 0], "0.3": [-45, 0, 2]}},
    "left_leg": {"rotation": {"0.0": [0, 0, 0], "0.3": [6, 0, 0]}},
    "head": {"rotation": {"0.0": [8, 0, 0], "0.3": [-14, 0, 0]}},
}}
# ... then the kick: the leg snaps up at the contact (clip time 0), holds through the
# 3 t hit-stop, and comes back down by the end of the 16 t action.
CLIPS["kick"] = {"loop": "hold_on_last_frame", "animation_length": 0.5, "bones": {
    "right_leg": {"rotation": {"0.0": [-135, 0, 4], "0.15": [-138, 0, 4], "0.3": [-60, 0, 2], "0.5": [-6, 0, 0]}},
    "left_leg": {"rotation": {"0.0": [10, 0, 0], "0.15": [10, 0, 0], "0.5": [2, 0, 0]}},
    "body": {"rotation": {"0.0": [-18, 0, 0], "0.15": [-18, 0, 0], "0.5": [-3, 0, 0]}},
    "right_arm": {"rotation": {"0.0": [-30, 0, 24], "0.15": [-30, 0, 24], "0.5": [-6, 0, 6]}},
    "head": {"rotation": {"0.0": [-24, 0, 0], "0.3": [-30, 0, 0], "0.5": [-10, 0, 0]}},
}}

# Grab & Throw: the right hand reaches out and closes at chest height (the target hangs
# from it) and holds there for the whole grab ...
CLIPS["grab_hold"] = {"loop": "hold_on_last_frame", "animation_length": 0.3, "bones": {
    "right_arm": {"rotation": {"0.0": [-20, 0, 6], "0.15": [-95, -8, 4], "0.3": [-85, -6, 2]}},
    "body": {"rotation": {"0.0": [0, 0, 0], "0.3": [4, -8, 0]}},
    "head": {"rotation": {"0.0": [0, 0, 0], "0.3": [6, 0, 0]}},
    "right_leg": {"rotation": {"0.0": [0, 0, 0], "0.3": [-8, 0, 0]}},
    "left_leg": {"rotation": {"0.0": [0, 0, 0], "0.3": [8, 0, 0]}},
}}
# ... then the throw: cocked back over the 3 t hit-stop, a full overhead swing on the
# launch (0.15 s), follow-through and recovery by the end of the 12 t action.
CLIPS["throw"] = {"loop": "hold_on_last_frame", "animation_length": 0.6, "bones": {
    "right_arm": {"rotation": {"0.0": [-150, 0, 10], "0.15": [-160, 0, 10], "0.25": [-40, 0, 4], "0.4": [-20, 0, 2], "0.6": [-4, 0, 3]}},
    "body": {"rotation": {"0.0": [-10, 18, 0], "0.15": [-12, 22, 0], "0.25": [16, -14, 0], "0.6": [2, 0, 0]}},
    "head": {"rotation": {"0.0": [-6, 0, 0], "0.25": [6, 0, 0], "0.6": [0, 0, 0]}},
    "right_leg": {"rotation": {"0.0": [10, 0, 0], "0.25": [-22, 0, 0], "0.6": [-2, 0, 0]}},
    "left_leg": {"rotation": {"0.0": [-10, 0, 0], "0.25": [18, 0, 0], "0.6": [2, 0, 0]}},
}}

# Dash Behind (8 t): a low vanish lean forward, then he lands turned to the target's
# back (body twist unwinding) in a guard by the end of the action.
CLIPS["dash_behind"] = {"loop": "hold_on_last_frame", "animation_length": 0.4, "bones": {
    "body": {"rotation": {"0.0": [10, 0, 0], "0.08": [28, -14, 0], "0.15": [6, 18, 0], "0.4": [2, 0, 0]}},
    "head": {"rotation": {"0.0": [-8, 0, 0], "0.08": [-22, 10, 0], "0.15": [-4, -12, 0], "0.4": [0, 0, 0]}},
    "right_arm": {"rotation": {"0.0": [24, 0, 10], "0.08": [48, 0, 18], "0.15": [-36, -10, 6], "0.4": [-20, 0, 4]}},
    "right_leg": {"rotation": {"0.0": [-14, 0, 0], "0.08": [-30, 0, 0], "0.15": [-10, 0, 4], "0.4": [-2, 0, 0]}},
    "left_leg": {"rotation": {"0.0": [16, 0, 0], "0.08": [32, 0, 0], "0.15": [12, 0, -4], "0.4": [2, 0, 0]}},
}}

# Stomp (14 t): the right leg rises high (0-0.35 s), slams on tick 8 (0.4 s), the body
# drops into it; the right arm out for balance (the left stays behind the back), recovery by the end (0.7 s).
CLIPS["stomp"] = {"loop": "hold_on_last_frame", "animation_length": 0.7, "bones": {
    "right_leg": {"rotation": {"0.0": [0, 0, 0], "0.3": [-80, 0, 6], "0.35": [-84, 0, 6], "0.4": [6, 0, 0], "0.55": [2, 0, 0], "0.7": [0, 0, 0]}},
    "left_leg": {"rotation": {"0.0": [0, 0, 0], "0.3": [8, 0, -2], "0.4": [-6, 0, 0], "0.7": [0, 0, 0]}},
    "body": {"rotation": {"0.0": [0, 0, 0], "0.3": [-8, 0, 0], "0.4": [14, 0, 0], "0.55": [8, 0, 0], "0.7": [0, 0, 0]}},
    "head": {"rotation": {"0.0": [0, 0, 0], "0.3": [6, 0, 0], "0.4": [-10, 0, 0], "0.7": [0, 0, 0]}},
    "right_arm": {"rotation": {"0.0": [0, 0, 6], "0.3": [-20, 0, 40], "0.4": [10, 0, 24], "0.7": [0, 0, 4]}},
}}

# Reflect Projectile (Knight, the left arm stays behind the back): the back of the right
# hand raised across the chest for the 20 t window...
CLIPS["reflect_ready"] = {"loop": "hold_on_last_frame", "animation_length": 1.0, "bones": {
    "right_arm": {"rotation": {"0.0": [-30, 0, 0], "0.1": [-95, 30, -20], "1.0": [-92, 28, -18]}},
    "body": {"rotation": {"0.0": [0, 0, 0], "0.1": [0, -14, 0], "1.0": [0, -12, 0]}},
    "head": {"rotation": {"0.0": [0, 0, 0], "0.1": [-2, 6, 0], "1.0": [-2, 6, 0]}},
}}
# ...and the backhand swat out to the side on each reflection, back to the guard.
CLIPS["reflect"] = {"loop": "hold_on_last_frame", "animation_length": 0.3, "bones": {
    "right_arm": {"rotation": {"0.0": [-92, 28, -18], "0.1": [-88, -46, 30], "0.3": [-92, 20, -10]}},
    "body": {"rotation": {"0.0": [0, -12, 0], "0.1": [0, 20, 0], "0.3": [0, -6, 0]}},
    "head": {"rotation": {"0.0": [-2, 6, 0], "0.3": [-2, 4, 0]}},
}}


# Stage 4, Reid draw (6 s, standing still, overlay over the Holy Sword stance; the left hand keeps
# the sheath at the hip, so the left arm is not animated): the right hand crosses to the hilt at the
# left hip (0.6 s), pulls the blade up slowly along the sheath axis (to 5.6 s), the final tick lifts
# it overhead; feet planted wide, the head follows the hand.
CLIPS["reid_draw"] = {"loop": "hold_on_last_frame", "animation_length": 6.0, "bones": {
    "right_arm": {"rotation": {"0.0": [0, 0, 3], "0.6": [-28, 30, -38], "2.0": [-45, 26, -30],
                               "4.0": [-80, 18, -14], "5.6": [-120, 8, 4], "6.0": [-150, 0, 12]}},
    "body": {"rotation": {"0.0": [0, 0, 0], "0.6": [4, 14, 0], "4.0": [0, 8, 0], "5.6": [-4, 2, 0], "6.0": [-6, 0, 0]}},
    "head": {"rotation": {"0.0": [0, 0, 0], "0.6": [18, 14, 0], "4.0": [4, 6, 0], "6.0": [-22, 0, 0]}},
    "right_leg": {"rotation": {"0.0": [0, 0, 0], "0.6": [-4, 0, 7], "6.0": [-4, 0, 7]}},
    "left_leg": {"rotation": {"0.0": [0, 0, 0], "0.6": [4, 0, -7], "6.0": [4, 0, -7]}},
}}
# Lethal Chop (the kit chop, 20 t = 1 s, the standard 2-keyframe timeline of the animation skill):
# wind-up out to the side (0.25), the flat sweep across the front (0.35), hold (0.65), back (0.9).
def chop(side):
    m = 1 if side == "right" else -1
    arm = side + "_arm"
    return {"loop": "hold_on_last_frame", "animation_length": 1.0, "bones": {
        arm: {"rotation": {"0.0": [0, 0, 3 * m], "0.25": [-95, 60 * m, 20 * m], "0.35": [-95, -55 * m, -10 * m],
                           "0.5": [-90, -50 * m, -8 * m], "0.65": [-90, -50 * m, -8 * m], "0.9": [0, 0, 3 * m]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.25": [0, 25 * m, 0], "0.35": [0, -25 * m, 0], "0.65": [0, -20 * m, 0], "0.9": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.25": [0, -10 * m, 0], "0.35": [0, 8 * m, 0], "0.9": [0, 0, 0]}},
    }}


CLIPS["chop_right"] = chop("right")
CLIPS["chop_left"] = chop("left")
# Reid drawn (loop over the Holy Sword stance, right arm only: the left hand stays on the empty
# sheath at the hip): the sword held low and forward, the arm breathing with it.
CLIPS["idle_holy_drawn"] = loop({"right_arm": {"rotation": breathe([-40, 0, 10], [-42, 0, 11])}})
# Sheathe (1 s): the reverse, calm: the blade comes down across to the left hip and slides in.
CLIPS["reid_sheathe"] = {"loop": "hold_on_last_frame", "animation_length": 1.0, "bones": {
    "right_arm": {"rotation": {"0.0": [-40, 0, 10], "0.3": [-45, 26, -30], "0.75": [-28, 30, -38], "1.0": [0, 0, 3]}},
    "body": {"rotation": {"0.0": [0, 0, 0], "0.5": [3, 12, 0], "1.0": [0, 0, 0]}},
    "head": {"rotation": {"0.0": [0, 0, 0], "0.5": [14, 12, 0], "1.0": [0, 0, 0]}},
}}


def main():
    data = {"format_version": "1.8.0", "animations": {}}
    if os.path.exists(OUT):
        with open(OUT, encoding="utf-8") as f:
            data = json.load(f)
    data["animations"].update(CLIPS)
    data["animations"] = dict(sorted(data["animations"].items()))
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print("wrote", os.path.relpath(OUT, REPO), "clips:", ", ".join(data["animations"]))


if __name__ == "__main__":
    main()
