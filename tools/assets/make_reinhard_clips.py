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
