#!/usr/bin/env python3
"""Sinclair, Savannah, rabbit and prop clips (own work), evaluated by the existing anim core.

Times are authored in server ticks (20 per second) and written in seconds. Rotations use
the project's ModelPart degree convention (as rig_reinhard / make_reinhard_clips): arms and
legs hang, so x- swings them forward; the torso and head stand, so x+ leans/looks down;
right-side z+ swings outward. Left-side values are written in the same right-hand terms
and mirrored (x, -y, -z). Clip names for timed moves equal SinclairMove.clip(); their
lengths equal SinclairMove.length() (checked by SinclairClipsTest). Locomotion loops are
driven by walked distance, idles by real time, everything else by synced ticks.
Run: python3 tools/assets/make_sinclair_clips.py [--check]
"""
import argparse
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ANIM = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore/animations/sinclair")

FINGERS = ("index", "middle", "ring")
# finger poses for the right hand: {bone suffix: rotation}; z- curls towards the palm
POSES = {
    "relaxed": {"index": [0, 0, -14], "index2": [0, 0, -18], "middle": [0, 0, -18], "middle2": [0, 0, -20],
                "ring": [0, 0, -24], "ring2": [0, 0, -24], "thumb": [0, 0, 0], "thumb2": [0, 0, -8]},
    "open": {"index": [-8, 0, 6], "index2": [0, 0, 2], "middle": [0, 0, 4], "middle2": [0, 0, 2],
             "ring": [10, 0, 2], "ring2": [0, 0, 0], "thumb": [-18, 0, 22], "thumb2": [0, 0, 6]},
    "grip": {"index": [0, 0, -78], "index2": [0, 0, -70], "middle": [0, 0, -84], "middle2": [0, 0, -76],
             "ring": [0, 0, -88], "ring2": [0, 0, -80], "thumb": [24, 0, -34], "thumb2": [0, 0, -30]},
    "point": {"index": [0, 0, 0], "index2": [0, 0, 0], "middle": [0, 0, -86], "middle2": [0, 0, -80],
              "ring": [0, 0, -90], "ring2": [0, 0, -82], "thumb": [20, 0, -30], "thumb2": [0, 0, -24]},
    "snap_set": {"index": [0, 0, -34], "index2": [0, 0, -28], "middle": [0, 0, -38], "middle2": [0, 0, -26],
                 "ring": [0, 0, -76], "ring2": [0, 0, -70], "thumb": [-12, 0, -44], "thumb2": [0, 0, -10]},
    "snap_hit": {"index": [0, 0, -12], "index2": [0, 0, -6], "middle": [0, 0, -98], "middle2": [0, 0, -86],
                 "ring": [0, 0, -80], "ring2": [0, 0, -72], "thumb": [-26, 0, 14], "thumb2": [0, 0, 8]},
    "fan": {"index": [-22, 0, -16], "index2": [0, 0, -10], "middle": [0, 0, -14], "middle2": [0, 0, -8],
            "ring": [22, 0, -14], "ring2": [0, 0, -10], "thumb": [-34, 0, 16], "thumb2": [0, 0, 4]},
    "pinch": {"index": [0, 0, -40], "index2": [0, 0, -30], "middle": [0, 0, -50], "middle2": [0, 0, -40],
              "ring": [0, 0, -70], "ring2": [0, 0, -66], "thumb": [-10, 0, -36], "thumb2": [0, 0, -16]},
}


def mirror(v):
    return [v[0], -v[1], -v[2]]


def sec(tick):
    return ("%.4f" % (tick / 20.0)).rstrip("0").rstrip(".") if tick else "0.0"


class Clip:
    def __init__(self, length, loop=False):
        self.length = length
        self.loop = loop
        self.tracks = {}

    def key(self, bone, channel, tick, value):
        rows = self.tracks.setdefault(bone, {}).setdefault(channel, {})
        rows[tick] = [round(float(x), 3) for x in value]
        return self

    def rot(self, bone, rows):
        for t, v in rows:
            self.key(bone, "rotation", t, v)
        return self

    def pos(self, bone, rows):
        for t, v in rows:
            self.key(bone, "position", t, v)
        return self

    def scale(self, bone, rows):
        for t, v in rows:
            self.key(bone, "scale", t, v if isinstance(v, list) else [v, v, v])
        return self

    def arm(self, side, rows, prefix=""):
        """rows: (tick, arm, forearm, hand) in right-hand terms; None keeps a joint unkeyed."""
        for t, a, f, h in rows:
            for bone, v in ((side + "_arm", a), (side + "_forearm", f), (side + "_hand", h)):
                if v is not None:
                    self.key(prefix + bone, "rotation", t, v if side == "right" else mirror(v))
        return self

    def hand(self, side, rows, prefix=""):
        for t, kind in rows:
            for suffix, v in POSES[kind].items():
                self.key(prefix + side + "_" + suffix, "rotation", t, v if side == "right" else mirror(v))
        return self

    def to_json(self):
        bones = {}
        for bone, chans in self.tracks.items():
            out = {}
            for chan, rows in chans.items():
                ticks = sorted(rows)
                if self.loop and ticks[-1] < self.length:
                    rows[self.length] = rows[ticks[0]]
                    ticks.append(self.length)
                out[chan] = {sec(t): {"post": rows[t], "lerp_mode": "catmullrom"} for t in ticks}
            bones[bone] = out
        clip = {"animation_length": self.length / 20.0, "bones": bones}
        if self.loop:
            clip["loop"] = True
        return clip


Z = [0, 0, 0]


# ------------------------------------------------------------------ shared body pieces
def legs_still(c, ticks):
    for t in ticks:
        for b in ("right_leg", "left_leg", "right_knee", "left_knee"):
            c.key(b, "rotation", t, Z)


def bow(c, start, end, hat=True, legs=True):
    """Stage bow: step back, deep bend, hat swept out in the left hand, wand across the waist."""
    down, up = start + 6, end - 6
    c.rot("body", [(start, [2, 0, 0]), (down, [42, 4, 0]), (up, [40, 4, 0]), (end, [0, 0, 0])])
    c.rot("head", [(start, Z), (down, [22, 0, 0]), (up, [20, 0, 0]), (end, Z)])
    c.arm("right", [(start, [-30, 0, 8], [-40, 0, 0], Z), (down, [-38, 0, -30], [-78, 0, 0], [0, 0, -10]),
                    (up, [-38, 0, -30], [-76, 0, 0], [0, 0, -10]), (end, [-8, 0, 6], [-30, 0, 0], Z)])
    c.arm("left", [(start, [-60, 0, 30], [-30, 0, 0], Z), (down, [-28, 0, 72], [-10, 0, 0], [0, 0, 20]),
                   (up, [-26, 0, 70], [-10, 0, 0], [0, 0, 20]), (end, [-4, 0, 6], [-16, 0, 0], Z)])
    c.hand("right", [(start, "grip"), (end, "grip")])
    c.hand("left", [(start, "pinch" if hat else "open"), (end, "relaxed")])
    if legs:
        c.rot("right_leg", [(start, Z), (down, [-10, 0, 4]), (up, [-10, 0, 4]), (end, Z)])
        c.rot("left_leg", [(start, Z), (down, [22, 0, -4]), (up, [22, 0, -4]), (end, Z)])
        c.rot("right_knee", [(start, Z), (down, [6, 0, 0]), (up, [6, 0, 0]), (end, Z)])
        c.rot("left_knee", [(start, Z), (down, [24, 0, 0]), (up, [24, 0, 0]), (end, Z)])


def body_clips():
    C = {}
    # idle: breathing, wand resting forward-down, left hand loose; real-time loop
    c = Clip(60, loop=True)
    c.rot("body", [(0, Z), (30, [-1.2, 0, 0])])
    c.pos("body", [(0, Z), (30, [0, 0.12, 0])])
    c.arm("right", [(0, [-14, 0, 7], [-44, 0, 0], [0, 0, 0]), (30, [-16, 0, 8], [-46, 0, 0], [4, 0, 0])])
    c.arm("left", [(0, [-4, 0, 6], [-18, 0, 0], Z), (30, [-5, 0, 7], [-20, 0, 0], Z)])
    c.rot("wand", [(0, Z), (20, [6, 0, 0]), (40, [-4, 0, 0])])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "relaxed")])
    C["idle"] = c

    def gait(length, amp, knee, arm_amp, lean, bob):
        g = Clip(length, loop=True)
        h = length // 2
        q = length // 4
        g.rot("right_leg", [(0, [-amp, 0, 0]), (q, [0, 0, 0]), (h, [amp, 0, 0]), (h + q, [0, 0, 0])])
        g.rot("left_leg", [(0, [amp, 0, 0]), (q, [0, 0, 0]), (h, [-amp, 0, 0]), (h + q, [0, 0, 0])])
        g.rot("right_knee", [(0, [6, 0, 0]), (q, [8, 0, 0]), (h, [14, 0, 0]), (h + q * 0.6, [knee, 0, 0]), (h + q * 1.5, [20, 0, 0])])
        g.rot("left_knee", [(0, [14, 0, 0]), (q * 0.6, [knee, 0, 0]), (q * 1.5, [20, 0, 0]), (h, [6, 0, 0]), (h + q, [8, 0, 0])])
        g.rot("body", [(0, [lean, -3, 0]), (q, [lean + 1, 0, 0]), (h, [lean, 3, 0]), (h + q, [lean + 1, 0, 0])])
        g.pos("body", [(0, Z), (q, [0, bob, 0]), (h, Z), (h + q, [0, bob, 0])])
        g.arm("right", [(0, [arm_amp * 0.5 - 14, 0, 7], [-44, 0, 0], Z), (h, [-arm_amp * 0.5 - 14, 0, 7], [-50, 0, 0], Z)])
        g.arm("left", [(0, [-arm_amp, 0, 5], [-22, 0, 0], Z), (h, [arm_amp, 0, 5], [-12, 0, 0], Z)])
        g.hand("right", [(0, "grip")])
        g.hand("left", [(0, "relaxed")])
        return g

    C["walk"] = gait(20, 26, 46, 22, 2, 0.35)
    C["run"] = gait(14, 42, 78, 46, 10, 0.6)
    # decoy: the "running magician" - holds the hat on with one hand, wand pumping, high knees
    d = gait(12, 48, 92, 56, 14, 0.8)
    d.arm("left", [(0, [-160, 0, 28], [-96, 0, 0], [0, 0, 10]), (6, [-156, 0, 30], [-92, 0, 0], [0, 0, 10])])
    d.hand("left", [(0, "open")])
    d.rot("head", [(0, [-6, 0, 4]), (6, [-6, 0, -4])])
    C["decoy_run"] = d
    c = Clip(40, loop=True)
    c.rot("body", [(0, [24, 0, 0]), (20, [25, 0, 0])])
    c.pos("body", [(0, [0, -1.6, -1.2]), (20, [0, -1.7, -1.2])])
    c.rot("right_leg", [(0, [-30, 0, 4])])
    c.rot("left_leg", [(0, [-6, 0, -4])])
    c.rot("right_knee", [(0, [52, 0, 0])])
    c.rot("left_knee", [(0, [34, 0, 0])])
    c.arm("right", [(0, [-30, 0, 8], [-50, 0, 0], Z)])
    c.arm("left", [(0, [-22, 0, 10], [-30, 0, 0], Z)])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "relaxed")])
    C["crouch"] = c
    c = Clip(20, loop=True)
    c.rot("right_leg", [(0, [-22, 0, 3]), (10, [-24, 0, 3])])
    c.rot("left_leg", [(0, [12, 0, -3]), (10, [14, 0, -3])])
    c.rot("right_knee", [(0, [30, 0, 0])])
    c.rot("left_knee", [(0, [48, 0, 0])])
    c.arm("left", [(0, [-30, 0, 38], [-24, 0, 0], Z), (10, [-34, 0, 42], [-26, 0, 0], Z)])
    c.hand("left", [(0, "open")])
    C["air"] = c

    # aim: wand arm straight towards the crosshair (the poser adds the look pitch)
    c = Clip(20, loop=True)
    c.arm("right", [(0, [-88, 4, 0], [-4, 0, 0], [6, 0, 0])])
    c.hand("right", [(0, "grip")])
    c.rot("wand", [(0, Z)])
    C["aim"] = c
    # shot: additive wrist flick per spark (5 ticks)
    c = Clip(5)
    c.arm("right", [(0, Z, Z, Z), (1, [-4, 0, 0], [-10, 0, 0], [-24, 0, 0]), (5, Z, Z, Z)])
    c.rot("body", [(0, Z), (1, [-1.5, -3, 0]), (5, Z)])
    C["shot"] = c
    # reload (32): wand flipped twice through the fingers, left hand taps the tip on the brim
    c = Clip(32)
    c.arm("right", [(0, [-50, 0, -6], [-60, 0, 0], Z), (6, [-44, 0, -18], [-92, 0, 0], [0, 0, -10]),
                    (24, [-46, 0, -16], [-90, 0, 0], [0, 0, -10]), (32, [-50, 0, -6], [-60, 0, 0], Z)])
    c.rot("wand", [(0, Z), (6, [0, 0, 0]), (11, [-180, 0, 0]), (16, [-360, 0, 0]), (21, [-540, 0, 0]),
                   (26, [-720, 0, 0]), (32, [-720, 0, 0])])
    c.arm("left", [(0, [-6, 0, 6], [-18, 0, 0], Z), (10, [-150, 0, -10], [-70, 0, 0], Z),
                   (14, [-152, 0, -12], [-64, 0, 0], [0, 0, 8]), (18, [-150, 0, -10], [-70, 0, 0], Z),
                   (32, [-6, 0, 6], [-18, 0, 0], Z)])
    c.hand("right", [(0, "grip"), (8, "pinch"), (24, "pinch"), (28, "grip")])
    c.hand("left", [(0, "relaxed"), (10, "point"), (18, "point"), (26, "relaxed")])
    c.rot("head", [(0, Z), (8, [10, 0, 0]), (24, [10, 0, 0]), (32, Z)])
    C["reload"] = c

    # bolt_cast (14): left hand draws a card from the sleeve, flicks it on tick 8 (BOLT_WINDUP)
    c = Clip(14)
    c.arm("left", [(0, [-8, 0, 6], [-20, 0, 0], Z), (3, [-30, 0, -40], [-110, 0, 0], [0, 0, -20]),
                   (6, [-58, 10, -36], [-96, 0, 0], [30, 0, -10]), (8, [-100, -6, 4], [-6, 0, 0], [-30, 0, 0]),
                   (10, [-96, -4, 6], [-8, 0, 0], [-10, 0, 0]), (14, [-8, 0, 6], [-20, 0, 0], Z)])
    c.hand("left", [(0, "relaxed"), (3, "pinch"), (8, "open"), (14, "relaxed")])
    c.rot("body", [(0, Z), (6, [-2, -14, 0]), (8, [3, 10, 0]), (14, Z)])
    C["bolt_cast"] = c
    # bolt_snap (8): left-hand finger snap steers the bolt
    c = Clip(8)
    c.arm("left", [(0, [-20, 0, 10], [-40, 0, 0], Z), (2, [-108, 0, 22], [-70, 0, 0], [0, 0, 6]),
                   (5, [-110, 0, 24], [-68, 0, 0], [-8, 0, 6]), (8, [-20, 0, 10], [-40, 0, 0], Z)])
    c.hand("left", [(0, "relaxed"), (2, "snap_set"), (3, "snap_hit"), (6, "snap_hit"), (8, "relaxed")])
    C["bolt_snap"] = c
    # summon (12): both arms sweep open - "presenting... Savannah!" (appears on tick 8)
    c = Clip(12)
    c.arm("right", [(0, [-20, 0, -10], [-60, 0, 0], Z), (4, [-50, 0, -30], [-80, 0, 0], Z),
                    (8, [-80, 0, 56], [-10, 0, 0], [0, 0, 20]), (12, [-74, 0, 50], [-14, 0, 0], [0, 0, 16])])
    c.arm("left", [(0, [-20, 0, -10], [-60, 0, 0], Z), (4, [-50, 0, -30], [-80, 0, 0], Z),
                   (8, [-82, 0, 58], [-8, 0, 0], [0, 0, 20]), (12, [-76, 0, 52], [-12, 0, 0], [0, 0, 18])])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "pinch"), (8, "open"), (12, "open")])
    c.rot("body", [(0, Z), (4, [6, 0, 0]), (8, [-7, 0, 0]), (12, [-5, 0, 0])])
    c.rot("head", [(0, Z), (8, [-10, 0, 0]), (12, [-8, 0, 0])])
    C["summon"] = c
    # swap (6): a full spin inside a card flash (teleport on tick 3)
    c = Clip(6)
    c.rot("body", [(0, Z), (2, [0, 160, 0]), (3, [0, 200, 0]), (6, [0, 360, 0])])
    c.arm("right", [(0, [-20, 0, 10], [-40, 0, 0], Z), (2, [-60, 0, 70], [-10, 0, 0], Z), (6, [-20, 0, 10], [-40, 0, 0], Z)])
    c.arm("left", [(0, [-20, 0, 10], [-40, 0, 0], Z), (2, [-60, 0, 70], [-10, 0, 0], Z), (6, [-20, 0, 10], [-40, 0, 0], Z)])
    c.hand("left", [(0, "open")])
    C["swap"] = c
    # hex_cast (10): lift the hat (in hand ticks 1-8), present it at the volunteer on tick 6
    c = Clip(10)
    c.arm("left", [(0, [-20, 0, 8], [-30, 0, 0], Z), (1, [-158, 0, -8], [-38, 0, 0], Z),
                   (3, [-150, 0, 18], [-30, 0, 0], [0, 0, 8]), (6, [-94, -6, 2], [-2, 0, 0], [64, 0, 0]),
                   (8, [-92, -6, 2], [-2, 0, 0], [60, 0, 0]), (10, [-20, 0, 8], [-30, 0, 0], Z)])
    c.hand("left", [(0, "relaxed"), (1, "pinch"), (8, "pinch"), (10, "relaxed")])
    c.arm("right", [(0, [-20, 0, 8], [-44, 0, 0], Z), (4, [-40, 0, 20], [-60, 0, 0], Z), (6, [-70, 0, 10], [-30, 0, 0], Z),
                    (10, [-20, 0, 8], [-44, 0, 0], Z)])
    c.hand("right", [(0, "grip")])
    c.rot("head", [(0, Z), (1, [-6, 0, 0]), (6, [4, 0, 0]), (10, Z)])
    c.rot("body", [(0, Z), (3, [-4, 0, 0]), (6, [6, -8, 0]), (10, Z)])
    C["hex_cast"] = c
    # snap (8): Misdirection - a raised finger snap and he is gone (tick 4)
    c = Clip(8)
    c.arm("left", [(0, [-30, 0, 10], [-50, 0, 0], Z), (2, [-120, 0, 30], [-74, 0, 0], [0, 0, 8]),
                   (4, [-124, 0, 32], [-72, 0, 0], [-10, 0, 8]), (8, [-120, 0, 30], [-74, 0, 0], Z)])
    c.hand("left", [(0, "relaxed"), (2, "snap_set"), (4, "snap_hit"), (8, "snap_hit")])
    c.rot("body", [(0, Z), (4, [-4, 8, 0]), (8, [-4, 8, 0])])
    C["snap"] = c
    # capture (20): wand points the volunteer out, the other hand beckons and catches the card (14)
    c = Clip(20)
    c.arm("right", [(0, [-40, 0, 6], [-40, 0, 0], Z), (5, [-96, 4, 0], [-2, 0, 0], [8, 0, 0]),
                    (20, [-94, 4, 0], [-2, 0, 0], [8, 0, 0])])
    c.hand("right", [(0, "grip")])
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (5, [-70, 0, 30], [-80, 0, 0], [-30, 0, 0]),
                   (8, [-70, 0, 30], [-100, 0, 0], [20, 0, 0]), (11, [-70, 0, 30], [-80, 0, 0], [-30, 0, 0]),
                   (14, [-66, 0, 8], [-60, 0, 0], [40, 0, 0]), (20, [-46, 0, 4], [-72, 0, 0], [30, 0, 0])])
    c.hand("left", [(0, "relaxed"), (5, "open"), (8, "point"), (11, "open"), (14, "pinch"), (20, "pinch")])
    c.rot("body", [(0, Z), (5, [-5, -6, 0]), (20, [-3, -4, 0])])
    C["capture"] = c
    # window (loop): the captured card floats at the left palm
    c = Clip(40, loop=True)
    c.arm("left", [(0, [-46, 0, 4], [-72, 0, 0], [30, 0, 0]), (20, [-48, 0, 4], [-74, 0, 0], [26, 0, 0])])
    c.hand("left", [(0, "pinch")])
    C["window"] = c
    # refuse (8): no volunteer - a wagging finger
    c = Clip(8)
    c.arm("left", [(0, [-20, 0, 8], [-40, 0, 0], Z), (2, [-60, 0, 10], [-96, 0, 0], [0, 0, 0]),
                   (4, [-60, 0, 10], [-96, 0, 0], [0, 0, 22]), (6, [-60, 0, 10], [-96, 0, 0], [0, 0, -22]),
                   (8, [-20, 0, 8], [-40, 0, 0], Z)])
    c.hand("left", [(0, "relaxed"), (2, "point"), (7, "point"), (8, "relaxed")])
    c.rot("head", [(0, Z), (4, [0, 8, 0]), (6, [0, -8, 0]), (8, Z)])
    C["refuse"] = c

    # ---------------------------------------------------------------- the six acts
    # BEAM (70): card raised as a mirror (0-12), footlight beam 12-42, frozen pause 42-50, bow 50-70
    c = Clip(70)
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (6, [-96, -10, -4], [-8, 0, 0], [80, 0, 0]),
                   (12, [-92, -14, -6], [-6, 0, 0], [86, 0, 0]), (42, [-92, -14, -6], [-6, 0, 0], [86, 0, 0]),
                   (50, [-92, -14, -6], [-6, 0, 0], [86, 0, 0])])
    c.arm("right", [(0, [-20, 0, 8], [-40, 0, 0], Z), (8, [-76, 0, -24], [-40, 0, 0], [0, 0, -20]),
                    (12, [-84, 0, -14], [-26, 0, 0], [-10, 0, -10]), (27, [-86, 0, -12], [-24, 0, 0], [-10, 0, -10]),
                    (42, [-84, 0, -14], [-26, 0, 0], [-10, 0, -10]), (50, [-84, 0, -14], [-26, 0, 0], [-10, 0, -10])])
    c.hand("left", [(0, "relaxed"), (6, "pinch"), (50, "pinch")])
    c.hand("right", [(0, "grip"), (50, "grip")])
    c.rot("body", [(0, Z), (12, [-6, 0, 0]), (20, [-5, 1, 0]), (30, [-6, -1, 0]), (42, [-6, 0, 0]), (50, [-6, 0, 0])])
    c.rot("right_leg", [(0, Z), (10, [-14, 0, 8]), (50, [-14, 0, 8])])
    c.rot("left_leg", [(0, Z), (10, [16, 0, -8]), (50, [16, 0, -8])])
    c.rot("left_knee", [(0, Z), (10, [18, 0, 0]), (50, [18, 0, 0])])
    c.rot("right_knee", [(0, Z), (10, [6, 0, 0]), (50, [6, 0, 0])])
    bow(c, 50, 70)
    C["act_beam"] = c
    # NOVA (64): hat thrown (10), conducted inward (10-24), burst (24), pause 24-40, catch (44), bow 44-64
    c = Clip(64)
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (2, [-160, 0, -10], [-30, 0, 0], Z), (5, [30, 0, 20], [-40, 0, 0], [20, 0, 0]),
                   (10, [-112, 0, 4], [-4, 0, 0], [-30, 0, 0]), (14, [-130, 0, 34], [-20, 0, 0], [0, 0, 10]),
                   (20, [-118, 0, 20], [-50, 0, 0], [-20, 0, 0]), (24, [-96, 0, 84], [0, 0, 0], [0, 0, 30]),
                   (40, [-94, 0, 82], [0, 0, 0], [0, 0, 30]), (43, [-168, 0, 6], [-10, 0, 0], Z), (44, [-160, 0, 8], [-14, 0, 0], Z)])
    c.arm("right", [(0, [-20, 0, 8], [-40, 0, 0], Z), (10, [-60, 0, 20], [-40, 0, 0], Z), (14, [-130, 0, 34], [-20, 0, 0], Z),
                    (20, [-118, 0, 20], [-50, 0, 0], Z), (24, [-96, 0, 84], [0, 0, 0], [0, 0, 30]),
                    (40, [-94, 0, 82], [0, 0, 0], [0, 0, 30]), (44, [-40, 0, 20], [-40, 0, 0], Z)])
    c.hand("left", [(0, "relaxed"), (2, "pinch"), (10, "open"), (24, "open"), (40, "open"), (43, "open"), (44, "pinch")])
    c.hand("right", [(0, "grip")])
    c.rot("body", [(0, Z), (5, [-6, 20, 0]), (10, [10, -10, 0]), (14, [-4, 0, 0]), (20, [2, 0, 0]),
                   (24, [-8, 0, 0]), (40, [-8, 0, 0]), (44, [-2, 0, 0])])
    c.rot("head", [(0, Z), (14, [-14, 0, 0]), (24, [-6, 0, 0]), (40, [-6, 0, 0]), (43, [-22, 0, 0]), (44, Z)])
    bow(c, 44, 64)
    C["act_nova"] = c
    # CUT (60): a card blade grows from the wand (0-10), raised over the head (10-20), saw stroke (20),
    # pause 20-36, recover, bow 40-60
    c = Clip(60)
    c.arm("right", [(0, [-20, 0, 8], [-40, 0, 0], Z), (6, [-100, 0, 10], [-20, 0, 0], Z), (10, [-172, 0, 6], [-12, 0, 0], [-10, 0, 0]),
                    (19, [-176, 0, 4], [-14, 0, 0], [-14, 0, 0]), (20, [-24, 0, -6], [-4, 0, 0], [20, 0, 0]),
                    (36, [-24, 0, -6], [-4, 0, 0], [20, 0, 0]), (40, [-30, 0, 8], [-40, 0, 0], Z)])
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (10, [-84, -6, 4], [-4, 0, 0], Z), (19, [-80, -6, 6], [-4, 0, 0], Z),
                   (20, [10, 0, 30], [-10, 0, 0], Z), (36, [10, 0, 30], [-10, 0, 0], Z), (40, [-10, 0, 6], [-20, 0, 0], Z)])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "relaxed"), (10, "point"), (19, "point"), (20, "open"), (40, "relaxed")])
    c.rot("body", [(0, Z), (10, [-10, 0, 0]), (19, [-12, 0, 0]), (20, [26, -6, 0]), (36, [26, -6, 0]), (40, Z)])
    c.pos("body", [(0, Z), (19, Z), (20, [0, -1.2, -0.6]), (36, [0, -1.2, -0.6]), (40, Z)])
    c.rot("right_leg", [(0, Z), (19, [-6, 0, 6]), (20, [-36, 0, 6]), (36, [-36, 0, 6]), (40, Z)])
    c.rot("left_leg", [(0, Z), (19, [6, 0, -6]), (20, [24, 0, -6]), (36, [24, 0, -6]), (40, Z)])
    c.rot("right_knee", [(0, Z), (20, [40, 0, 0]), (36, [40, 0, 0]), (40, Z)])
    c.rot("left_knee", [(0, Z), (20, [30, 0, 0]), (36, [30, 0, 0]), (40, Z)])
    bow(c, 40, 60)
    C["act_cut"] = c
    # BARRAGE (62): a fan of nine cards (0-10), three conducted volleys (10/18/26), pause 34-42, bow 42-62
    c = Clip(62)
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (6, [-84, -20, 30], [-40, 0, 0], [40, 0, 0]),
                   (10, [-88, -24, 34], [-36, 0, 0], [46, 0, 0]), (34, [-86, -24, 32], [-36, 0, 0], [46, 0, 0]),
                   (42, [-86, -24, 32], [-36, 0, 0], [46, 0, 0])])
    rows = [(0, [-20, 0, 8], [-40, 0, 0], Z), (6, [-70, 0, 30], [-40, 0, 0], Z)]
    for v in (10, 18, 26):
        rows += [(v - 2, [-120, 0, 34], [-30, 0, 0], [-20, 0, 0]), (v, [-84, 0, -12], [-4, 0, 0], [16, 0, 0]),
                 (v + 4, [-96, 0, 10], [-14, 0, 0], Z)]
    rows += [(34, [-92, 0, 6], [-10, 0, 0], Z), (42, [-92, 0, 6], [-10, 0, 0], Z)]
    c.arm("right", rows)
    c.hand("left", [(0, "relaxed"), (6, "fan"), (42, "fan")])
    c.hand("right", [(0, "grip")])
    c.rot("body", [(0, Z)] + [(v, [4, -10, 0]) for v in (10, 18, 26)] + [(v + 4, [0, 4, 0]) for v in (10, 18, 26)] +
          [(34, [-2, 0, 0]), (42, [-2, 0, 0])])
    bow(c, 42, 62)
    C["act_barrage"] = c
    # AEGIS (78): velvet curtain falls (0-8), slow turn inside (8-44), hush 44-50, burst open (50), bow 58-78
    c = Clip(78)
    c.arm("right", [(0, [-20, 0, 8], [-40, 0, 0], Z), (6, [-150, 0, 60], [-10, 0, 0], Z), (12, [-60, 0, -40], [-90, 0, 0], Z),
                    (44, [-60, 0, -40], [-90, 0, 0], Z), (49, [-40, 0, -50], [-110, 0, 0], Z), (50, [-100, 0, 92], [0, 0, 0], [0, 0, 20]),
                    (58, [-96, 0, 88], [0, 0, 0], [0, 0, 20])])
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (6, [-150, 0, 60], [-10, 0, 0], Z), (12, [-60, 0, -40], [-90, 0, 0], Z),
                   (44, [-60, 0, -40], [-90, 0, 0], Z), (49, [-40, 0, -50], [-110, 0, 0], Z), (50, [-100, 0, 92], [0, 0, 0], [0, 0, 20]),
                   (58, [-96, 0, 88], [0, 0, 0], [0, 0, 20])])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "relaxed"), (6, "open"), (12, "relaxed"), (49, "relaxed"), (50, "open"), (58, "open")])
    c.rot("body", [(0, Z), (6, [-8, 0, 0]), (12, [6, 0, 0]), (20, [6, 90, 0]), (28, [6, 180, 0]), (36, [6, 270, 0]),
                   (44, [6, 360, 0]), (49, [14, 360, 0]), (50, [-10, 360, 0]), (58, [-6, 360, 0])])
    c.rot("head", [(0, Z), (12, [16, 0, 0]), (49, [20, 0, 0]), (50, [-14, 0, 0]), (58, [-8, 0, 0])])
    bow(c, 58, 78)
    c.rot("body", [(78, [0, 360, 0])])
    C["act_aegis"] = c
    # FINALE (72): wand calls the chest down (0-12), three sword thrusts (18/26/34), reveal (44), bow 52-72
    c = Clip(72)
    rows = [(0, [-20, 0, 8], [-40, 0, 0], Z), (6, [-174, 0, 8], [-6, 0, 0], Z), (12, [-70, 0, 4], [-6, 0, 0], [20, 0, 0])]
    for s in (18, 26, 34):
        rows += [(s - 3, [-60, 0, 30], [-80, 0, 0], Z), (s, [-96, 0, -4], [0, 0, 0], [10, 0, 0]), (s + 3, [-90, 0, 4], [-10, 0, 0], Z)]
    rows += [(40, [-50, 0, 10], [-40, 0, 0], Z), (44, [-150, 0, 50], [-4, 0, 0], Z), (52, [-146, 0, 48], [-4, 0, 0], Z)]
    c.arm("right", rows)
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (12, [-30, 0, 30], [-20, 0, 0], Z), (18, [20, 0, 26], [-10, 0, 0], Z),
                   (34, [20, 0, 26], [-10, 0, 0], Z), (40, [-10, 0, 6], [-20, 0, 0], Z), (44, [-150, 0, 50], [-4, 0, 0], Z),
                   (52, [-146, 0, 48], [-4, 0, 0], Z)])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "relaxed"), (12, "open"), (52, "open")])
    body = [(0, Z), (6, [-8, 0, 0]), (12, [6, 0, 0])]
    for s in (18, 26, 34):
        body += [(s - 3, [-4, 14, 0]), (s, [16, -10, 0]), (s + 3, [8, 0, 0])]
    body += [(40, Z), (44, [-10, 0, 0]), (52, [-8, 0, 0])]
    c.rot("body", body)
    legs = [(0, Z)]
    for s in (18, 26, 34):
        legs += [(s - 3, [-4, 0, 4]), (s, [-38, 0, 4]), (s + 3, [-30, 0, 4])]
    c.rot("right_leg", legs + [(40, Z), (52, Z)])
    c.rot("right_knee", [(0, Z)] + [(s, [36, 0, 0]) for s in (18, 26, 34)] + [(s + 3, [30, 0, 0]) for s in (18, 26, 34)] + [(40, Z), (52, Z)])
    c.rot("left_leg", [(0, Z), (18, [20, 0, -4]), (34, [20, 0, -4]), (40, Z), (52, Z)])
    c.rot("left_knee", [(0, Z), (18, [24, 0, 0]), (34, [24, 0, 0]), (40, Z), (52, Z)])
    bow(c, 52, 72)
    C["act_finale"] = c
    return C


# ------------------------------------------------------------------ first person
FP_R = ([-82, 6, 0], [-8, 0, 0], [4, 0, 0])
FP_L = ([-30, 0, 10], [-30, 0, 0], Z)


def fp_rest(c, ticks, left=True):
    for t in ticks:
        c.arm("right", [(t,) + FP_R])
        if left:
            c.arm("left", [(t,) + FP_L])


def fp_clips():
    C = {}
    c = Clip(60, loop=True)
    c.arm("right", [(0,) + FP_R, (30, [-84, 6, 0], [-9, 0, 0], [6, 0, 0])])
    c.arm("left", [(0,) + FP_L, (30, [-31, 0, 10], [-31, 0, 0], Z)])
    c.rot("wand", [(0, Z), (30, [3, 0, 0])])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "relaxed")])
    C["fp_idle"] = c
    c = Clip(5)
    c.arm("right", [(0, Z, Z, Z), (1, [-6, 0, 0], [-6, 0, 0], [-26, 0, 0]), (5, Z, Z, Z)])
    C["fp_shot"] = c
    c = Clip(32)
    c.arm("right", [(0,) + FP_R, (6, [-62, -20, -10], [-60, 0, 0], [0, 0, -10]), (26, [-62, -20, -10], [-60, 0, 0], [0, 0, -10]), (32,) + FP_R])
    c.rot("wand", [(0, Z), (6, Z), (11, [-180, 0, 0]), (16, [-360, 0, 0]), (21, [-540, 0, 0]), (26, [-720, 0, 0]), (32, [-720, 0, 0])])
    c.arm("left", [(0,) + FP_L, (10, [-80, 20, -16], [-60, 0, 0], Z), (14, [-84, 22, -18], [-56, 0, 0], [0, 0, 10]),
                   (18, [-80, 20, -16], [-60, 0, 0], Z), (32,) + FP_L])
    c.hand("right", [(0, "grip"), (8, "pinch"), (24, "pinch"), (28, "grip")])
    c.hand("left", [(0, "relaxed"), (10, "point"), (18, "point"), (26, "relaxed")])
    C["fp_reload"] = c
    c = Clip(14)
    fp_rest(c, (0, 14), left=False)
    c.arm("left", [(0,) + FP_L, (3, [-50, 20, -30], [-90, 0, 0], [0, 0, -20]), (6, [-70, 10, -20], [-60, 0, 0], [30, 0, 0]),
                   (8, [-92, -4, 4], [-4, 0, 0], [-30, 0, 0]), (10, [-90, -2, 6], [-6, 0, 0], [-10, 0, 0]), (14,) + FP_L])
    c.hand("left", [(0, "relaxed"), (3, "pinch"), (8, "open"), (14, "relaxed")])
    C["fp_bolt_cast"] = c
    for name, hit, length in (("fp_bolt_snap", 3, 8), ("fp_snap", 4, 8)):
        c = Clip(length)
        c.arm("left", [(0,) + FP_L, (2, [-96, 18, -10], [-50, 0, 0], [0, 0, 6]), (hit, [-98, 18, -10], [-48, 0, 0], [-8, 0, 6]),
                       (length, [-96, 18, -10], [-50, 0, 0], Z) if name == "fp_snap" else (length,) + FP_L])
        c.hand("left", [(0, "relaxed"), (2, "snap_set"), (hit, "snap_hit"), (length, "snap_hit" if name == "fp_snap" else "relaxed")])
        C[name] = c
    c = Clip(12)
    c.arm("right", [(0,) + FP_R, (4, [-70, -10, -10], [-50, 0, 0], Z), (8, [-80, 0, 40], [-6, 0, 0], [0, 0, 20]), (12, [-80, 0, 36], [-8, 0, 0], [0, 0, 16])])
    c.arm("left", [(0,) + FP_L, (4, [-70, -10, -10], [-50, 0, 0], Z), (8, [-80, 0, 40], [-6, 0, 0], [0, 0, 20]), (12, [-80, 0, 36], [-8, 0, 0], [0, 0, 16])])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "relaxed"), (8, "open"), (12, "open")])
    C["fp_summon"] = c
    c = Clip(6)
    c.arm("right", [(0,) + FP_R, (3, [-40, 0, 30], [-10, 0, 0], Z), (6,) + FP_R])
    c.arm("left", [(0,) + FP_L, (3, [-40, 0, 30], [-10, 0, 0], Z), (6,) + FP_L])
    C["fp_swap"] = c
    c = Clip(10)
    fp_rest(c, (0, 10), left=False)
    c.arm("left", [(0,) + FP_L, (1, [-120, 10, -10], [-40, 0, 0], Z), (3, [-110, 10, 10], [-30, 0, 0], Z),
                   (6, [-88, -4, 2], [-2, 0, 0], [64, 0, 0]), (8, [-86, -4, 2], [-2, 0, 0], [60, 0, 0]), (10,) + FP_L])
    c.hand("left", [(0, "relaxed"), (1, "pinch"), (8, "pinch"), (10, "relaxed")])
    C["fp_hex_cast"] = c
    c = Clip(20)
    c.arm("right", [(0,) + FP_R, (5, [-90, 4, 0], [-2, 0, 0], [6, 0, 0]), (20, [-90, 4, 0], [-2, 0, 0], [6, 0, 0])])
    c.arm("left", [(0,) + FP_L, (5, [-70, 10, 10], [-70, 0, 0], [-30, 0, 0]), (8, [-70, 10, 10], [-90, 0, 0], [20, 0, 0]),
                   (11, [-70, 10, 10], [-70, 0, 0], [-30, 0, 0]), (14, [-66, 14, 0], [-50, 0, 0], [40, 0, 0]), (20, [-58, 14, 0], [-56, 0, 0], [30, 0, 0])])
    c.hand("left", [(0, "relaxed"), (5, "open"), (8, "point"), (11, "open"), (14, "pinch"), (20, "pinch")])
    C["fp_capture"] = c
    c = Clip(40, loop=True)
    c.arm("left", [(0, [-58, 14, 0], [-56, 0, 0], [30, 0, 0]), (20, [-60, 14, 0], [-58, 0, 0], [26, 0, 0])])
    c.hand("left", [(0, "pinch")])
    C["fp_window"] = c
    c = Clip(8)
    c.arm("left", [(0,) + FP_L, (2, [-80, 16, 0], [-70, 0, 0], Z), (4, [-80, 16, 0], [-70, 0, 0], [0, 0, 22]),
                   (6, [-80, 16, 0], [-70, 0, 0], [0, 0, -22]), (8,) + FP_L])
    c.hand("left", [(0, "relaxed"), (2, "point"), (7, "point"), (8, "relaxed")])
    C["fp_refuse"] = c
    # acts in first person: the hands frame the trick, the bow lowers them out of view
    def fp_act(length, beats, bow_start):
        a = Clip(length)
        rows_r = [(0,) + FP_R] + [(t,) + r for t, r, _ in beats] + [(bow_start,) + beats[-1][1], (bow_start + 6, [-20, 0, -20], [-60, 0, 0], Z),
                                                                    (length - 6, [-20, 0, -20], [-60, 0, 0], Z), (length,) + FP_R]
        rows_l = [(0,) + FP_L] + [(t,) + l for t, _, l in beats] + [(bow_start,) + beats[-1][2], (bow_start + 6, [-10, 0, 40], [-10, 0, 0], Z),
                                                                    (length - 6, [-10, 0, 40], [-10, 0, 0], Z), (length,) + FP_L]
        a.arm("right", rows_r)
        a.arm("left", rows_l)
        a.hand("right", [(0, "grip")])
        return a

    MIR = ([-90, -14, -6], [-6, 0, 0], [86, 0, 0])
    c = fp_act(70, [(6, ([-76, 0, -24], [-40, 0, 0], [0, 0, -20]), MIR), (12, ([-84, 0, -14], [-26, 0, 0], [-10, 0, -10]), MIR),
                    (42, ([-84, 0, -14], [-26, 0, 0], [-10, 0, -10]), MIR)], 50)
    c.hand("left", [(0, "relaxed"), (6, "pinch"), (50, "pinch"), (60, "relaxed")])
    C["fp_act_beam"] = c
    WIDE = ([-90, 0, 70], [0, 0, 0], [0, 0, 30])
    c = fp_act(64, [(5, ([-60, 0, 20], [-40, 0, 0], Z), ([-20, 0, 20], [-40, 0, 0], Z)), (10, ([-60, 0, 20], [-40, 0, 0], Z), ([-104, 0, 4], [-4, 0, 0], [-30, 0, 0])),
                    (14, ([-120, 0, 30], [-20, 0, 0], Z), ([-120, 0, 30], [-20, 0, 0], Z)), (24, WIDE, WIDE), (40, WIDE, WIDE),
                    (44, ([-40, 0, 20], [-40, 0, 0], Z), ([-150, 0, 8], [-14, 0, 0], Z))], 44)
    c.hand("left", [(0, "relaxed"), (2, "pinch"), (10, "open"), (43, "open"), (44, "pinch")])
    C["fp_act_nova"] = c
    UP = ([-160, 0, 6], [-12, 0, 0], [-10, 0, 0])
    DOWN = ([-40, 0, -6], [-4, 0, 0], [20, 0, 0])
    PNT = ([-84, -6, 4], [-4, 0, 0], Z)
    c = fp_act(60, [(10, UP, PNT), (19, UP, PNT), (20, DOWN, FP_L), (36, DOWN, FP_L)], 40)
    c.hand("left", [(0, "relaxed"), (10, "point"), (19, "point"), (20, "relaxed")])
    C["fp_act_cut"] = c
    FAN = ([-80, -20, 26], [-36, 0, 0], [46, 0, 0])
    beats = [(6, ([-70, 0, 30], [-40, 0, 0], Z), FAN)]
    for v in (10, 18, 26):
        beats += [(v - 2, ([-110, 0, 30], [-30, 0, 0], [-20, 0, 0]), FAN), (v, ([-84, 0, -12], [-4, 0, 0], [16, 0, 0]), FAN),
                  (v + 4, ([-92, 0, 10], [-14, 0, 0], Z), FAN)]
    beats += [(34, ([-90, 0, 6], [-10, 0, 0], Z), FAN)]
    c = fp_act(62, beats, 42)
    c.hand("left", [(0, "relaxed"), (6, "fan"), (42, "fan"), (50, "relaxed")])
    C["fp_act_barrage"] = c
    CROSS = ([-60, 0, -40], [-90, 0, 0], Z)
    c = fp_act(78, [(6, ([-140, 0, 50], [-10, 0, 0], Z), ([-140, 0, 50], [-10, 0, 0], Z)), (12, CROSS, CROSS), (49, CROSS, CROSS),
                    (50, WIDE, WIDE), (58, WIDE, WIDE)], 58)
    c.hand("left", [(0, "relaxed"), (6, "open"), (12, "relaxed"), (50, "open")])
    C["fp_act_aegis"] = c
    beats = [(6, UP, FP_L), (12, ([-70, 0, 4], [-6, 0, 0], [20, 0, 0]), FP_L)]
    for s in (18, 26, 34):
        beats += [(s - 3, ([-60, 0, 30], [-80, 0, 0], Z), FP_L), (s, ([-94, 0, -4], [0, 0, 0], [10, 0, 0]), FP_L), (s + 3, ([-88, 0, 4], [-10, 0, 0], Z), FP_L)]
    beats += [(44, ([-140, 0, 44], [-4, 0, 0], Z), ([-140, 0, 44], [-4, 0, 0], Z))]
    c = fp_act(72, beats, 52)
    c.hand("left", [(0, "relaxed"), (44, "open")])
    C["fp_act_finale"] = c
    return C


# ------------------------------------------------------------------ savannah
def savannah_clips():
    C = {}

    def arms(c, rows):
        for t, r, l in rows:
            c.arm("right", [(t,) + r])
            c.arm("left", [(t,) + l])

    PRESENT_R = ([-26, 0, 26], [-44, 0, 0], [-24, 0, 0])
    PRESENT_L = ([-22, 0, 22], [-40, 0, 0], [-20, 0, 0])
    c = Clip(60, loop=True)
    c.pos("root", [(0, Z), (15, [0, 0.6, 0]), (30, [0, 0.9, 0]), (45, [0, 0.4, 0])])
    c.rot("torso", [(0, [0, -2, 0]), (30, [-2, 2, 0])])
    c.rot("head", [(0, [0, 0, 3]), (30, [-3, 0, -2])])
    c.rot("tier1", [(0, [2, 0, 0]), (15, [0, 0, 2]), (30, [-2, 0, 0]), (45, [0, 0, -2])])
    c.rot("tier2", [(0, [0, 0, -3]), (15, [3, 0, 0]), (30, [0, 0, 3]), (45, [-3, 0, 0])])
    c.rot("tier3", [(0, [-4, 0, 0]), (15, [0, 0, -4]), (30, [4, 0, 0]), (45, [0, 0, 4])])
    c.rot("trail", [(0, [6, 0, 4]), (20, [10, 0, -4]), (40, [4, 0, 0])])
    c.rot("trail2", [(0, [8, 0, -6]), (20, [4, 0, 6]), (40, [12, 0, 0])])
    c.rot("trail3", [(0, [10, 0, 8]), (20, [16, 0, -8]), (40, [6, 0, 0])])
    c.rot("hair_mid", [(0, [6, 0, 0]), (30, [10, 0, 2])])
    c.rot("hair_tip", [(0, [8, 0, 0]), (30, [14, 0, -2])])
    arms(c, [(0, PRESENT_R, PRESENT_L), (30, ([-30, 0, 30], [-46, 0, 0], [-28, 0, 0]), ([-26, 0, 26], [-42, 0, 0], [-24, 0, 0]))])
    for side in ("right", "left"):
        c.rot(side + "_fingers", [(0, [0, 0, -10]), (30, [0, 0, -16])] if side == "right" else [(0, [0, 0, 10]), (30, [0, 0, 16])])
    C["sav_idle"] = c
    # appear (16): spins out of a card, unfolding from a glowing seed
    c = Clip(16)
    c.scale("root", [(0, [0.05, 0.05, 0.05]), (6, [0.7, 1.2, 0.7]), (11, [1.08, 0.96, 1.08]), (16, [1, 1, 1])])
    c.rot("root", [(0, [0, -540, 0]), (11, [0, -20, 0]), (16, Z)])
    c.pos("root", [(0, [0, -4, 0]), (11, [0, 0.6, 0]), (16, Z)])
    arms(c, [(0, ([-170, 0, 10], [0, 0, 0], Z), ([-170, 0, 10], [0, 0, 0], Z)), (11, ([-150, 0, 40], [-10, 0, 0], Z), ([-150, 0, 40], [-10, 0, 0], Z)),
             (16, PRESENT_R, PRESENT_L)])
    c.rot("tier3", [(0, [0, 0, 0]), (8, [-20, 0, 0]), (16, Z)])
    c.rot("hair_tip", [(0, [60, 0, 0]), (16, [8, 0, 0])])
    C["sav_appear"] = c
    # attack (10): a whip of the right hand flings a spark
    c = Clip(10)
    arms(c, [(0, PRESENT_R, PRESENT_L), (3, ([-130, 0, 30], [-30, 0, 0], [-20, 0, 0]), PRESENT_L),
             (5, ([-72, 0, -10], [-4, 0, 0], [30, 0, 0]), ([-10, 0, 30], [-30, 0, 0], Z)), (10, PRESENT_R, PRESENT_L)])
    c.rot("torso", [(0, Z), (3, [-4, 16, 0]), (5, [6, -14, 0]), (10, Z)])
    c.rot("hair_tip", [(0, [8, 0, 0]), (5, [24, 0, 10]), (10, [8, 0, 0])])
    C["sav_attack"] = c
    # cast (12): a two-handed push
    c = Clip(12)
    PUSH = ([-92, 0, -4], [-2, 0, 0], [70, 0, 0])
    arms(c, [(0, PRESENT_R, PRESENT_L), (3, ([-60, 0, 10], [-90, 0, 0], [40, 0, 0]), ([-60, 0, 10], [-90, 0, 0], [40, 0, 0])),
             (5, PUSH, PUSH), (9, PUSH, PUSH), (12, PRESENT_R, PRESENT_L)])
    c.rot("torso", [(0, Z), (3, [-6, 0, 0]), (5, [10, 0, 0]), (12, Z)])
    c.rot("tier3", [(0, Z), (5, [-14, 0, 0]), (12, Z)])
    C["sav_cast"] = c
    # swap out / in (6): whirls into a card and back
    c = Clip(6)
    c.rot("root", [(0, Z), (3, [0, 360, 0]), (6, [0, 720, 0])])
    c.scale("root", [(0, [1, 1, 1]), (3, [0.6, 1.1, 0.6]), (6, [0.02, 0.4, 0.02])])
    arms(c, [(0, PRESENT_R, PRESENT_L), (3, ([-170, 0, 4], [0, 0, 0], Z), ([-170, 0, 4], [0, 0, 0], Z))])
    C["sav_swap_out"] = c
    c = Clip(6)
    c.rot("root", [(0, [0, -720, 0]), (3, [0, -360, 0]), (6, Z)])
    c.scale("root", [(0, [0.02, 0.4, 0.02]), (3, [0.6, 1.1, 0.6]), (6, [1, 1, 1])])
    arms(c, [(0, ([-170, 0, 4], [0, 0, 0], Z), ([-170, 0, 4], [0, 0, 0], Z)), (6, PRESENT_R, PRESENT_L)])
    C["sav_swap_in"] = c
    # act (loop 20): applause and card tossing during the number
    c = Clip(20, loop=True)
    CLAP_O = ([-66, 0, 20], [-56, 0, 0], [0, 0, -20])
    CLAP_I = ([-70, 0, -8], [-50, 0, 0], [0, 0, -30])
    arms(c, [(0, CLAP_O, CLAP_O), (4, CLAP_I, CLAP_I), (8, CLAP_O, CLAP_O), (12, CLAP_I, CLAP_I), (16, ([-150, 0, 40], [-10, 0, 0], Z), ([-150, 0, 40], [-10, 0, 0], Z))])
    c.pos("root", [(0, Z), (4, [0, 0.4, 0]), (8, Z), (12, [0, 0.4, 0]), (16, [0, 1.2, 0])])
    c.rot("head", [(0, [-6, 0, 0]), (16, [-14, 0, 0])])
    C["sav_act"] = c
    # hurt (8)
    c = Clip(8)
    c.rot("torso", [(0, Z), (2, [-16, 0, 6]), (8, Z)])
    c.rot("head", [(0, Z), (2, [-14, 0, 0]), (8, Z)])
    arms(c, [(0, PRESENT_R, PRESENT_L), (2, ([-110, 0, 10], [-90, 0, 0], Z), ([-110, 0, 10], [-90, 0, 0], Z)), (8, PRESENT_R, PRESENT_L)])
    c.pos("root", [(0, Z), (2, [0, 0, 1.2]), (8, Z)])
    C["sav_hurt"] = c
    # leave (20): a curtsy, then she dissolves
    c = Clip(20)
    SKIRT = ([-6, 0, 46], [-10, 0, 0], [0, 0, 10])
    arms(c, [(0, PRESENT_R, PRESENT_L), (5, SKIRT, SKIRT), (12, SKIRT, SKIRT), (20, ([-170, 0, 10], [0, 0, 0], Z), ([-170, 0, 10], [0, 0, 0], Z))])
    c.rot("torso", [(0, Z), (5, [24, 0, 0]), (12, [22, 0, 0]), (16, [-6, 0, 0]), (20, Z)])
    c.rot("head", [(0, Z), (5, [18, 0, 0]), (12, [16, 0, 0]), (20, [-10, 0, 0])])
    c.pos("root", [(0, Z), (5, [0, -1.4, 0]), (12, [0, -1.4, 0]), (20, [0, 3, 0])])
    c.scale("root", [(0, [1, 1, 1]), (12, [1, 1, 1]), (16, [0.8, 1.3, 0.8]), (20, [0.02, 1.8, 0.02])])
    c.scale("tier3", [(0, [1, 1, 1]), (5, [1.18, 0.85, 1.18]), (12, [1.18, 0.85, 1.18]), (16, [1, 1, 1])])
    C["sav_leave"] = c
    # death (20): she comes apart into ribbons
    c = Clip(20)
    c.rot("torso", [(0, Z), (4, [-24, 0, 10]), (20, [-40, 0, 20])])
    c.pos("tier1", [(0, Z), (20, [-2, 2, 1])])
    c.pos("tier2", [(0, Z), (20, [3, 1, -2])])
    c.pos("tier3", [(0, Z), (20, [-1, -3, 3])])
    c.rot("tier1", [(0, Z), (20, [0, 120, 30])])
    c.rot("tier2", [(0, Z), (20, [40, -160, 0])])
    c.rot("tier3", [(0, Z), (20, [-30, 200, -20])])
    c.scale("root", [(0, [1, 1, 1]), (10, [1, 1, 1]), (20, [0.05, 0.05, 0.05])])
    arms(c, [(0, PRESENT_R, PRESENT_L), (4, ([-150, 0, 60], [0, 0, 0], Z), ([-150, 0, 60], [0, 0, 0], Z)), (20, ([-170, 0, 90], [0, 0, 0], Z), ([-170, 0, 90], [0, 0, 0], Z))])
    C["sav_death"] = c
    # bow (20): curtsy beside Sinclair at the end of a number
    c = Clip(20)
    arms(c, [(0, PRESENT_R, PRESENT_L), (6, SKIRT, SKIRT), (14, SKIRT, SKIRT), (20, PRESENT_R, PRESENT_L)])
    c.rot("torso", [(0, Z), (6, [26, 0, 0]), (14, [24, 0, 0]), (20, Z)])
    c.rot("head", [(0, Z), (6, [16, 0, 0]), (14, [16, 0, 0]), (20, Z)])
    c.pos("root", [(0, Z), (6, [0, -1.6, 0]), (14, [0, -1.6, 0]), (20, Z)])
    c.scale("tier3", [(0, [1, 1, 1]), (6, [1.2, 0.84, 1.2]), (14, [1.2, 0.84, 1.2]), (20, [1, 1, 1])])
    C["sav_bow"] = c
    return C


def rabbit_clips():
    C = {}
    c = Clip(40, loop=True)
    c.rot("head", [(0, Z), (6, [-4, 0, 0]), (8, [2, 0, 0]), (10, [-3, 0, 0]), (12, Z), (28, [0, 10, 0]), (34, Z)])
    c.rot("tail", [(0, Z), (20, [0, 10, 0])])
    c.scale("body", [(0, [1, 1, 1]), (20, [1.03, 0.98, 1.03])])
    C["rab_idle"] = c
    c = Clip(10, loop=True)
    c.pos("body", [(0, Z), (3, [0, 1.6, -0.4]), (6, [0, 0.8, -0.2]), (8, Z)])
    c.rot("body", [(0, [6, 0, 0]), (2, [-14, 0, 0]), (5, [4, 0, 0]), (8, [8, 0, 0])])
    for side in ("right", "left"):
        c.rot(side + "_haunch", [(0, [-10, 0, 0]), (2, [50, 0, 0]), (5, [10, 0, 0]), (8, [-14, 0, 0])])
        c.rot(side + "_paw", [(0, [10, 0, 0]), (2, [-30, 0, 0]), (6, [-40, 0, 0]), (8, [10, 0, 0])])
    c.rot("head", [(0, [-4, 0, 0]), (3, [8, 0, 0]), (8, [-4, 0, 0])])
    C["rab_hop"] = c
    c = Clip(8)
    c.scale("body", [(0, [0.1, 0.1, 0.1]), (4, [1.25, 0.8, 1.25]), (6, [0.92, 1.1, 0.92]), (8, [1, 1, 1])])
    C["rab_appear"] = c
    return C


def prop_clips():
    C = {}
    c = Clip(8)
    c.rot("lid", [(0, Z), (3, [-40, 0, 0]), (6, [-118, 0, 0]), (8, [-108, 0, 0])])
    C["chest_open"] = c
    c = Clip(40, loop=True)
    for i in range(8):
        ph = (i * 5) % 40
        c.rot("fold%d" % i, [(0, [2.5 * (1 if i % 2 else -1), 0, 0]), (20, [-2.5 * (1 if i % 2 else -1), 0, 0])])
        del ph
    C["curtain_wave"] = c
    c = Clip(10)
    for i in range(8):
        c.rot("fold%d" % i, [(0, Z), (4, [0, (i - 3.5) * 22, 0]), (10, [0, (i - 3.5) * 26, 0])])
        c.scale("fold%d" % i, [(0, [1, 1, 1]), (10, [0.2, 1, 1])])
    C["curtain_open"] = c
    return C


def files():
    out = {}

    def add(name, clips):
        data = {"format_version": "1.8.0", "animations": {k: v.to_json() for k, v in clips.items()}}
        out[os.path.join(ANIM, name + ".animation.json")] = json.dumps(data, separators=(",", ":")) + "\n"

    # split so every file stays small (reviewable, and under the tooling's upload limit)
    body = body_clips()
    fp = fp_clips()
    base = ("idle", "walk", "run", "decoy_run", "crouch", "air", "aim", "shot", "reload")
    acts_a = ("act_beam", "act_nova", "act_cut")
    add("sinclair_base", {k: body[k] for k in base})
    add("sinclair_moves", {k: v for k, v in body.items() if k not in base and not k.startswith("act_")})
    add("sinclair_acts_a", {k: body[k] for k in acts_a})
    add("sinclair_acts_b", {k: v for k, v in body.items() if k.startswith("act_") and k not in acts_a})
    add("sinclair_fp", {k: v for k, v in fp.items() if not k.startswith("fp_act_")})
    add("sinclair_fp_acts", {k: v for k, v in fp.items() if k.startswith("fp_act_")})
    add("savannah", savannah_clips())
    add("rabbit", rabbit_clips())
    add("props", prop_clips())
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    stale = []
    for path, text in files().items():
        if args.check:
            if not os.path.isfile(path) or open(path, encoding="utf-8").read() != text:
                stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)
    for p in stale:
        print("stale:", p)
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
