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
FINALE_SWORDS = (30, 38, 46)  # ActScript.FINALE_SWORDS


# ------------------------------------------------------------------ shared body pieces
def legs_still(c, ticks):
    for t in ticks:
        for b in ("right_leg", "left_leg", "right_knee", "left_knee"):
            c.key(b, "rotation", t, Z)


def bow(c, start, end, hat=True, legs=True, yaw=0):
    """Stage bow: step back, deep bend, hat swept out in the left hand, wand across the waist.
    yaw keeps an act that turned the body a full circle (AEGIS) from unwinding during the bow."""
    down, up = start + 6, end - 6
    c.rot("body", [(start, [2, yaw, 0]), (down, [42, yaw + 4, 0]), (up, [40, yaw + 4, 0]), (end, [0, yaw, 0])])
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

    # ---------------------------------------------------------------- copy cinematic (60)
    # Starts from the capture/window pose; the card is raised high (8-18 unfold), presented beside the face
    # (18-44), folded back into the palm (44-52) and settles into the window pose (60). Design 2026-11-02 §2.
    WIN_L = ([-46, 0, 4], [-72, 0, 0], [30, 0, 0])
    c = Clip(60)
    c.arm("left", [(0,) + WIN_L, (6, [-70, 0, 10], [-90, 0, 0], [20, 0, 0]), (12, [-150, 0, 14], [-20, 0, 0], [10, 0, 0]),
                   (18, [-158, 0, 12], [-14, 0, 0], [6, 0, 0]), (30, [-154, 0, 14], [-16, 0, 0], [8, 0, 0]),
                   (44, [-156, 0, 12], [-14, 0, 0], [6, 0, 0]), (50, [-70, 0, 8], [-80, 0, 0], [24, 0, 0]), (60,) + WIN_L])
    c.arm("right", [(0, [-94, 4, 0], [-2, 0, 0], [8, 0, 0]), (8, [-40, 0, -10], [-70, 0, 0], Z),
                    (18, [-60, 0, -30], [-80, 0, 0], [0, 0, -10]), (44, [-58, 0, -30], [-82, 0, 0], [0, 0, -10]),
                    (60, [-14, 0, 7], [-44, 0, 0], Z)])
    c.hand("left", [(0, "pinch"), (10, "pinch"), (14, "open"), (44, "open"), (48, "pinch"), (60, "pinch")])
    c.hand("right", [(0, "grip")])
    c.rot("body", [(0, [-3, -4, 0]), (8, [2, 0, 0]), (18, [-8, 6, 0]), (44, [-7, 6, 0]), (52, [0, 0, 0]), (60, [-3, -4, 0])])
    c.rot("head", [(0, Z), (10, [-10, 6, 0]), (18, [-20, 10, 0]), (40, [-18, 8, 0]), (50, [6, 0, 0]), (60, Z)])
    legs_still(c, (0, 60))
    C["copy"] = c

    # ---------------------------------------------------------------- the six acts (design 2026-11-02 §3)
    # every act: tension -> main -> climax -> finish (pause + bow); beats equal ActScript
    MIRROR = ([-92, -14, -6], [-6, 0, 0], [86, 0, 0])
    WAND = ([-84, 0, -14], [-26, 0, 0], [-10, 0, -10])
    # BEAM (120): stance + card mirror raised (0-28), ray conducted with a recoil per pulse (28-70),
    # the card drawn back to charge (70-78), overdrive thrust (78), hold 88-96, bow 96-120
    c = Clip(120)
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (8, [-60, -6, 0], [-50, 0, 0], [40, 0, 0]), (18, [-96, -10, -4], [-8, 0, 0], [80, 0, 0]),
                   (28,) + MIRROR, (70,) + MIRROR, (76, [-74, -10, -10], [-44, 0, 0], [70, 0, 0]),
                   (78, [-104, -14, -6], [0, 0, 0], [88, 0, 0]), (88, [-102, -14, -6], [0, 0, 0], [88, 0, 0]),
                   (96, [-100, -14, -6], [-2, 0, 0], [86, 0, 0])])
    rows = [(0, [-20, 0, 8], [-40, 0, 0], Z), (10, [-50, 0, 10], [-60, 0, 0], Z), (20, [-120, 0, 20], [-30, 0, 0], [-10, 0, 0]),
            (28,) + WAND]
    for p in range(33, 70, 10):
        rows += [(p, [-90, 0, -10], [-22, 0, 0], [-12, 0, -8]), (p + 5, [-82, 0, -16], [-28, 0, 0], [-8, 0, -12])]
    rows += [(70,) + WAND, (76, [-60, 0, -30], [-70, 0, 0], [0, 0, -20]), (78, [-96, 0, -8], [-6, 0, 0], [-10, 0, 0]),
             (96, [-94, 0, -8], [-8, 0, 0], [-10, 0, 0])]
    c.arm("right", rows)
    c.hand("left", [(0, "relaxed"), (8, "pinch"), (96, "pinch")])
    c.hand("right", [(0, "grip"), (96, "grip")])
    body = [(0, Z), (12, [4, 0, 0]), (28, [-6, 0, 0])]
    for p in range(28, 70, 5):
        body += [(p + 1, [-4, 0, 0]), (p + 3, [-6, (1 if (p // 5) % 2 else -1), 0])]
    body += [(70, [-6, 0, 0]), (76, [8, 0, 0]), (78, [-12, 0, 0]), (88, [-11, 0, 0]), (96, [-10, 0, 0])]
    c.rot("body", body)
    c.pos("body", [(0, Z), (12, [0, -0.4, 0]), (28, Z), (70, Z), (76, [0, -0.6, 0.6]), (78, [0, -0.3, -0.8]), (96, [0, -0.3, -0.8])])
    c.rot("head", [(0, Z), (12, [10, 0, 0]), (28, [-4, 0, 0]), (76, [6, 0, 0]), (78, [-8, 0, 0]), (96, [-6, 0, 0])])
    c.rot("right_leg", [(0, Z), (12, [-14, 0, 8]), (76, [-14, 0, 8]), (78, [-30, 0, 8]), (96, [-30, 0, 8])])
    c.rot("left_leg", [(0, Z), (12, [16, 0, -8]), (76, [20, 0, -8]), (78, [30, 0, -8]), (96, [30, 0, -8])])
    c.rot("left_knee", [(0, Z), (12, [18, 0, 0]), (78, [26, 0, 0]), (96, [26, 0, 0])])
    c.rot("right_knee", [(0, Z), (12, [6, 0, 0]), (78, [24, 0, 0]), (96, [24, 0, 0])])
    bow(c, 96, 120)
    C["act_beam"] = c

    # NOVA (104): hat doffed (3) and spun on the fingertip, wind-up, throw (20); the flight is conducted with two
    # whirls (30, 38), the hands gather it in (46-52), burst (52) and hold, the hat returns and is caught (74),
    # bow 80-104
    c = Clip(104)
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (2, [-160, 0, -10], [-30, 0, 0], Z), (5, [-110, 0, 30], [-40, 0, 0], [0, 0, 10]),
                   (14, [-112, 0, 32], [-42, 0, 0], [0, 0, 10]), (17, [30, 0, 20], [-40, 0, 0], [20, 0, 0]),
                   (20, [-112, 0, 4], [-4, 0, 0], [-30, 0, 0]), (26, [-130, 0, 34], [-20, 0, 0], [0, 0, 10]),
                   (30, [-150, 0, 50], [-10, 0, 0], Z), (34, [-110, 0, 10], [-40, 0, 0], Z), (38, [-150, 0, 50], [-10, 0, 0], Z),
                   (46, [-118, 0, 20], [-50, 0, 0], [-20, 0, 0]), (51, [-80, 0, -10], [-100, 0, 0], Z),
                   (52, [-96, 0, 84], [0, 0, 0], [0, 0, 30]), (66, [-94, 0, 82], [0, 0, 0], [0, 0, 30]),
                   (72, [-168, 0, 6], [-10, 0, 0], Z), (74, [-160, 0, 8], [-14, 0, 0], Z), (78, [-150, 0, -30], [-40, 0, 0], Z),
                   (80, [-60, 0, 30], [-30, 0, 0], Z)])
    c.arm("right", [(0, [-20, 0, 8], [-40, 0, 0], Z), (5, [-30, 0, 10], [-40, 0, 0], Z), (17, [-60, 0, 20], [-40, 0, 0], Z),
                    (20, [-70, 0, 10], [-30, 0, 0], Z), (26, [-130, 0, 34], [-20, 0, 0], Z), (30, [-150, 0, 50], [-10, 0, 0], Z),
                    (34, [-110, 0, 10], [-40, 0, 0], Z), (38, [-150, 0, 50], [-10, 0, 0], Z), (46, [-118, 0, 20], [-50, 0, 0], Z),
                    (51, [-80, 0, -10], [-100, 0, 0], Z), (52, [-96, 0, 84], [0, 0, 0], [0, 0, 30]),
                    (66, [-94, 0, 82], [0, 0, 0], [0, 0, 30]), (74, [-40, 0, 20], [-40, 0, 0], Z), (80, [-30, 0, 8], [-40, 0, 0], Z)])
    c.hand("left", [(0, "relaxed"), (2, "pinch"), (5, "point"), (17, "pinch"), (20, "open"), (71, "open"), (74, "pinch"), (80, "pinch")])
    c.hand("right", [(0, "grip")])
    c.rot("body", [(0, Z), (5, [-4, 6, 0]), (14, [-4, 8, 0]), (17, [-6, 24, 0]), (20, [10, -12, 0]), (26, [-4, 0, 0]),
                   (30, [-8, 8, 0]), (34, [0, -6, 0]), (38, [-8, 8, 0]), (46, [2, 0, 0]), (51, [8, 0, 0]),
                   (52, [-10, 0, 0]), (66, [-8, 0, 0]), (74, [-2, 0, 0]), (80, Z)])
    c.rot("head", [(0, Z), (5, [-16, 10, 0]), (14, [-16, 10, 0]), (20, [-4, 0, 0]), (26, [-14, 0, 0]), (52, [-6, 0, 0]),
                   (66, [-6, 0, 0]), (72, [-26, 0, 0]), (74, [-20, 0, 0]), (80, Z)])
    c.rot("right_leg", [(0, Z), (17, [6, 0, 4]), (20, [-20, 0, 4]), (46, [-10, 0, 4]), (52, [-6, 0, 10]), (66, [-6, 0, 10]), (80, Z)])
    c.rot("left_leg", [(0, Z), (17, [-4, 0, -4]), (20, [18, 0, -4]), (46, [10, 0, -4]), (52, [6, 0, -10]), (66, [6, 0, -10]), (80, Z)])
    c.rot("right_knee", [(0, Z), (20, [20, 0, 0]), (51, [16, 0, 0]), (52, Z), (80, Z)])
    c.rot("left_knee", [(0, Z), (20, [14, 0, 0]), (51, [16, 0, 0]), (52, Z), (80, Z)])
    bow(c, 80, 104)
    C["act_nova"] = c

    # CUT (100): the blade grows from the wand (0-14), the line is shown (14), raised on the toes (14-24);
    # two saw strokes (30, 38), arched back overhead (44-50), the grand cut lunge (50), hold 50-66,
    # recover 66-76, bow 76-100
    UP = ([-172, 0, 6], [-12, 0, 0], [-10, 0, 0])
    c = Clip(100)
    rows = [(0, [-20, 0, 8], [-40, 0, 0], Z), (8, [-100, 0, 10], [-20, 0, 0], Z), (14, [-130, 0, 8], [-16, 0, 0], Z), (24,) + UP]
    for s in (30, 38):
        rows += [(s - 2, [-176, 0, 4], [-14, 0, 0], [-14, 0, 0]), (s, [-112, 0, -10], [-6, 0, 0], [16, 0, 0]), (s + 4, [-160, 0, 6], [-12, 0, 0], [-6, 0, 0])]
    rows += [(44, [-176, 0, 4], [-16, 0, 0], [-16, 0, 0]), (49, [-184, 0, 2], [-22, 0, 0], [-24, 0, 0]),
             (50, [-24, 0, -6], [-4, 0, 0], [20, 0, 0]), (66, [-24, 0, -6], [-4, 0, 0], [20, 0, 0]), (76, [-30, 0, 8], [-40, 0, 0], Z)]
    c.arm("right", rows)
    PNT = ([-84, -6, 4], [-4, 0, 0], Z)
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (14,) + PNT, (24, [-80, -6, 6], [-4, 0, 0], Z), (30, [-40, 0, 20], [-20, 0, 0], Z),
                   (38, [-40, 0, 20], [-20, 0, 0], Z), (44, [-84, -6, 4], [-4, 0, 0], Z), (49, [-80, -6, 6], [-4, 0, 0], Z),
                   (50, [10, 0, 30], [-10, 0, 0], Z), (66, [10, 0, 30], [-10, 0, 0], Z), (76, [-10, 0, 6], [-20, 0, 0], Z)])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "relaxed"), (14, "point"), (26, "open"), (42, "point"), (49, "point"), (50, "open"), (76, "relaxed")])
    body = [(0, Z), (14, [-6, 0, 0]), (24, [-10, 0, 0])]
    for s in (30, 38):
        body += [(s, [10, -4, 0]), (s + 4, [-6, 0, 0])]
    body += [(44, [-12, 0, 0]), (49, [-16, 0, 0]), (50, [26, -6, 0]), (66, [26, -6, 0]), (76, Z)]
    c.rot("body", body)
    c.pos("body", [(0, Z), (24, [0, 0.5, 0]), (44, [0, 0.4, 0]), (49, [0, 0.6, 0.3]), (50, [0, -1.2, -0.6]), (66, [0, -1.2, -0.6]), (76, Z)])
    c.rot("right_leg", [(0, Z), (24, [-6, 0, 6]), (49, [-6, 0, 6]), (50, [-36, 0, 6]), (66, [-36, 0, 6]), (76, Z)])
    c.rot("left_leg", [(0, Z), (24, [6, 0, -6]), (49, [6, 0, -6]), (50, [24, 0, -6]), (66, [24, 0, -6]), (76, Z)])
    c.rot("right_knee", [(0, Z), (49, Z), (50, [40, 0, 0]), (66, [40, 0, 0]), (76, Z)])
    c.rot("left_knee", [(0, Z), (49, Z), (50, [30, 0, 0]), (66, [30, 0, 0]), (76, Z)])
    bow(c, 76, 100)
    C["act_cut"] = c

    # BARRAGE (100): a fan of twelve cards opens (0-12) and is shown (12-20); three conducted volleys
    # (20/28/36); the wand is raised to gather the last three overhead (42-49) and thrown down (50);
    # hold 56-64, recover, bow 76-100
    FAN = ([-88, -24, 34], [-36, 0, 0], [46, 0, 0])
    c = Clip(100)
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (6, [-84, -20, 30], [-40, 0, 0], [40, 0, 0]), (12,) + FAN,
                   (18, [-100, -24, 40], [-30, 0, 0], [46, 0, 0]), (42,) + FAN, (49, [-50, -10, 20], [-40, 0, 0], [30, 0, 0]),
                   (56, [-50, -10, 20], [-40, 0, 0], [30, 0, 0]), (64, [-50, -10, 20], [-40, 0, 0], [30, 0, 0]),
                   (76, [-10, 0, 6], [-20, 0, 0], Z)])
    rows = [(0, [-20, 0, 8], [-40, 0, 0], Z), (6, [-70, 0, 30], [-40, 0, 0], Z), (14, [-110, 0, 30], [-30, 0, 0], Z)]
    for v in (20, 28, 36):
        rows += [(v - 2, [-120, 0, 34], [-30, 0, 0], [-20, 0, 0]), (v, [-84, 0, -12], [-4, 0, 0], [16, 0, 0]),
                 (v + 4, [-96, 0, 10], [-14, 0, 0], Z)]
    rows += [(42, [-120, 0, 10], [-20, 0, 0], Z), (49, [-178, 0, 4], [-10, 0, 0], [-10, 0, 0]),
             (50, [-70, 0, -14], [-4, 0, 0], [20, 0, 0]), (56, [-74, 0, -10], [-6, 0, 0], [16, 0, 0]),
             (64, [-74, 0, -10], [-6, 0, 0], [16, 0, 0]), (76, [-30, 0, 8], [-40, 0, 0], Z)]
    c.arm("right", rows)
    c.hand("left", [(0, "relaxed"), (6, "fan"), (42, "fan"), (49, "open"), (76, "relaxed")])
    c.hand("right", [(0, "grip")])
    body = [(0, Z), (12, [-4, 10, 0]), (18, [-4, 12, 0])]
    for v in (20, 28, 36):
        body += [(v, [4, -10, 0]), (v + 4, [0, 4, 0])]
    body += [(42, [-2, 0, 0]), (49, [-12, 0, 0]), (50, [14, -6, 0]), (64, [12, -6, 0]), (76, Z)]
    c.rot("body", body)
    c.rot("head", [(0, Z), (12, [10, 10, 0]), (18, [6, 6, 0]), (20, Z), (42, Z), (49, [-24, 0, 0]), (50, [6, 0, 0]), (64, [4, 0, 0]), (76, Z)])
    c.rot("right_leg", [(0, Z), (20, [-12, 0, 6]), (49, [-12, 0, 6]), (50, [-28, 0, 6]), (64, [-28, 0, 6]), (76, Z)])
    c.rot("left_leg", [(0, Z), (20, [10, 0, -6]), (49, [10, 0, -6]), (50, [20, 0, -6]), (64, [20, 0, -6]), (76, Z)])
    c.rot("right_knee", [(0, Z), (49, [6, 0, 0]), (50, [26, 0, 0]), (64, [26, 0, 0]), (76, Z)])
    c.rot("left_knee", [(0, Z), (49, [6, 0, 0]), (50, [18, 0, 0]), (64, [18, 0, 0]), (76, Z)])
    bow(c, 76, 100)
    C["act_barrage"] = c

    # AEGIS (96): the velvet curtain is swept down (0-12), a slow full turn inside (12-44, rustles at 14/30),
    # a crouched gather (44-50), burst open (50), hold 50-62, recover, bow 72-100 (facing forward again: 360)
    CROSS = ([-60, 0, -40], [-90, 0, 0], Z)
    WIDE = ([-100, 0, 92], [0, 0, 0], [0, 0, 20])
    c = Clip(96)
    for side in ("right", "left"):
        c.arm(side, [(0, [-20, 0, 8], [-40, 0, 0], Z), (6, [-150, 0, 60], [-10, 0, 0], Z), (12,) + CROSS, (44,) + CROSS,
                     (49, [-40, 0, -50], [-110, 0, 0], Z), (50,) + WIDE, (62, [-96, 0, 88], [0, 0, 0], [0, 0, 20]),
                     (72, [-30, 0, 8], [-40, 0, 0], Z)])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "relaxed"), (6, "open"), (12, "relaxed"), (49, "relaxed"), (50, "open"), (72, "relaxed")])
    c.rot("body", [(0, Z), (6, [-8, 0, 0]), (12, [6, 0, 0]), (20, [6, 80, 0]), (28, [6, 170, 0]), (36, [6, 270, 0]),
                   (44, [8, 360, 0]), (49, [16, 360, 0]), (50, [-10, 360, 0]), (62, [-6, 360, 0]), (72, [0, 360, 0])])
    c.rot("head", [(0, Z), (12, [16, 0, 0]), (49, [22, 0, 0]), (50, [-14, 0, 0]), (62, [-8, 0, 0]), (72, Z)])
    c.pos("body", [(0, Z), (44, Z), (49, [0, -1.0, 0]), (50, [0, 0.3, 0]), (62, Z)])
    c.rot("right_knee", [(0, Z), (44, Z), (49, [30, 0, 0]), (50, Z)])
    c.rot("left_knee", [(0, Z), (44, Z), (49, [30, 0, 0]), (50, Z)])
    bow(c, 72, 96, yaw=360)
    C["act_aegis"] = c

    # FINALE (100): the wand calls the chest down (0-20) and slams it to the floor (24); three sword thrusts
    # (30/38/46); a step back, palms trembling at the lid (52-58); reveal (58) and hold, bow 76-100
    c = Clip(100)
    rows = [(0, [-20, 0, 8], [-40, 0, 0], Z), (6, [-174, 0, 8], [-6, 0, 0], Z), (18, [-178, 0, 10], [-4, 0, 0], Z),
            (22, [-182, 0, 8], [-10, 0, 0], [-10, 0, 0]), (24, [-70, 0, 4], [-6, 0, 0], [20, 0, 0])]
    for s in FINALE_SWORDS:
        rows += [(s - 3, [-60, 0, 30], [-80, 0, 0], Z), (s, [-96, 0, -4], [0, 0, 0], [10, 0, 0]), (s + 3, [-90, 0, 4], [-10, 0, 0], Z)]
    rows += [(52, [-70, 0, 0], [-30, 0, 0], [0, 0, -20]), (57, [-72, 0, 0], [-30, 0, 0], [0, 0, -20]), (58, [-150, 0, 50], [-4, 0, 0], Z),
             (68, [-146, 0, 48], [-4, 0, 0], Z), (76, [-30, 0, 8], [-40, 0, 0], Z)]
    c.arm("right", rows)
    c.arm("left", [(0, [-10, 0, 6], [-20, 0, 0], Z), (12, [-30, 0, 30], [-20, 0, 0], Z), (24, [-20, 0, 30], [-20, 0, 0], Z),
                   (28, [20, 0, 26], [-10, 0, 0], Z), (46, [20, 0, 26], [-10, 0, 0], Z), (52, [-70, 0, 0], [-30, 0, 0], [0, 0, 20]),
                   (54, [-72, 0, 2], [-30, 0, 0], [0, 0, 26]), (56, [-70, 0, -2], [-30, 0, 0], [0, 0, 16]),
                   (57, [-72, 0, 0], [-30, 0, 0], [0, 0, 22]), (58, [-150, 0, 50], [-4, 0, 0], Z), (68, [-146, 0, 48], [-4, 0, 0], Z),
                   (76, [-10, 0, 6], [-20, 0, 0], Z)])
    c.hand("right", [(0, "grip")])
    c.hand("left", [(0, "relaxed"), (12, "open"), (68, "open"), (76, "relaxed")])
    body = [(0, Z), (6, [-8, 0, 0]), (18, [-10, 0, 0]), (24, [10, 0, 0])]
    for s in FINALE_SWORDS:
        body += [(s - 3, [-4, 14, 0]), (s, [16, -10, 0]), (s + 3, [8, 0, 0])]
    body += [(52, [4, 0, 0]), (57, [6, 0, 0]), (58, [-10, 0, 0]), (68, [-8, 0, 0]), (76, Z)]
    c.rot("body", body)
    c.pos("body", [(0, Z), (52, Z), (54, [0, 0, 0.8]), (58, [0, 0.2, 0.8]), (68, [0, 0, 0.8]), (76, Z)])
    legs = [(0, Z), (22, Z), (24, [-10, 0, 4])]
    for s in FINALE_SWORDS:
        legs += [(s - 3, [-4, 0, 4]), (s, [-38, 0, 4]), (s + 3, [-30, 0, 4])]
    c.rot("right_leg", legs + [(52, [10, 0, 4]), (68, [10, 0, 4]), (76, Z)])
    c.rot("right_knee", [(0, Z)] + [(s, [36, 0, 0]) for s in FINALE_SWORDS] + [(s + 3, [30, 0, 0]) for s in FINALE_SWORDS] +
          [(52, [10, 0, 0]), (76, Z)])
    c.rot("left_leg", [(0, Z), (28, [20, 0, -4]), (46, [20, 0, -4]), (52, [-4, 0, -4]), (68, [-4, 0, -4]), (76, Z)])
    c.rot("left_knee", [(0, Z), (28, [24, 0, 0]), (46, [24, 0, 0]), (52, [8, 0, 0]), (76, Z)])
    bow(c, 76, 100)
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
    c = fp_act(120, [(10, ([-60, 0, -10], [-50, 0, 0], Z), ([-60, 0, 10], [-40, 0, 0], [40, 0, 0])),
                     (20, ([-110, 0, 10], [-30, 0, 0], [-10, 0, 0]), MIR), (28, ([-84, 0, -14], [-26, 0, 0], [-10, 0, -10]), MIR),
                     (70, ([-84, 0, -14], [-26, 0, 0], [-10, 0, -10]), MIR),
                     (76, ([-60, 0, -30], [-70, 0, 0], [0, 0, -20]), ([-70, -10, -10], [-44, 0, 0], [70, 0, 0])),
                     (78, ([-94, 0, -8], [-6, 0, 0], [-10, 0, 0]), ([-100, -14, -6], [0, 0, 0], [88, 0, 0])),
                     (88, ([-92, 0, -8], [-8, 0, 0], [-10, 0, 0]), ([-98, -14, -6], [0, 0, 0], [88, 0, 0]))], 96)
    c.hand("left", [(0, "relaxed"), (8, "pinch"), (96, "pinch"), (106, "relaxed")])
    C["fp_act_beam"] = c
    WIDE = ([-90, 0, 70], [0, 0, 0], [0, 0, 30])
    SPIN = ([-100, 0, 24], [-40, 0, 0], [0, 0, 10])
    HIGH = ([-140, 0, 40], [-14, 0, 0], Z)
    IN = ([-110, 0, 10], [-40, 0, 0], Z)
    c = fp_act(104, [(5, ([-30, 0, 10], [-40, 0, 0], Z), SPIN), (14, ([-40, 0, 10], [-40, 0, 0], Z), SPIN),
                     (17, ([-60, 0, 20], [-40, 0, 0], Z), ([-20, 0, 20], [-40, 0, 0], Z)),
                     (20, ([-60, 0, 20], [-40, 0, 0], Z), ([-104, 0, 4], [-4, 0, 0], [-30, 0, 0])),
                     (30, HIGH, HIGH), (34, IN, IN), (38, HIGH, HIGH), (46, IN, IN),
                     (51, ([-80, 0, -10], [-90, 0, 0], Z), ([-80, 0, -10], [-90, 0, 0], Z)), (52, WIDE, WIDE), (66, WIDE, WIDE),
                     (74, ([-40, 0, 20], [-40, 0, 0], Z), ([-150, 0, 8], [-14, 0, 0], Z)),
                     (78, ([-36, 0, 14], [-40, 0, 0], Z), ([-130, 0, -20], [-40, 0, 0], Z))], 80)
    c.hand("left", [(0, "relaxed"), (2, "pinch"), (5, "point"), (17, "pinch"), (20, "open"), (71, "open"), (74, "pinch")])
    C["fp_act_nova"] = c
    UP = ([-160, 0, 6], [-12, 0, 0], [-10, 0, 0])
    SAW = ([-110, 0, -8], [-6, 0, 0], [16, 0, 0])
    DOWN = ([-40, 0, -6], [-4, 0, 0], [20, 0, 0])
    PNT = ([-84, -6, 4], [-4, 0, 0], Z)
    c = fp_act(100, [(8, ([-100, 0, 10], [-20, 0, 0], Z), FP_L), (14, ([-130, 0, 8], [-16, 0, 0], Z), PNT), (24, UP, PNT),
                     (28, UP, FP_L), (30, SAW, FP_L), (34, UP, FP_L), (38, SAW, FP_L), (42, UP, FP_L), (44, UP, PNT), (49, UP, PNT),
                     (50, DOWN, FP_L), (66, DOWN, FP_L)], 76)
    c.hand("left", [(0, "relaxed"), (14, "point"), (26, "relaxed"), (42, "point"), (49, "point"), (50, "relaxed")])
    C["fp_act_cut"] = c
    FAN = ([-80, -20, 26], [-36, 0, 0], [46, 0, 0])
    LOW = ([-50, -10, 20], [-40, 0, 0], [30, 0, 0])
    beats = [(6, ([-70, 0, 30], [-40, 0, 0], Z), FAN), (14, ([-110, 0, 30], [-30, 0, 0], Z), FAN)]
    for v in (20, 28, 36):
        beats += [(v - 2, ([-110, 0, 30], [-30, 0, 0], [-20, 0, 0]), FAN), (v, ([-84, 0, -12], [-4, 0, 0], [16, 0, 0]), FAN),
                  (v + 4, ([-92, 0, 10], [-14, 0, 0], Z), FAN)]
    beats += [(42, ([-120, 0, 10], [-20, 0, 0], Z), FAN), (49, ([-168, 0, 4], [-10, 0, 0], [-10, 0, 0]), LOW),
              (50, ([-74, 0, -14], [-4, 0, 0], [20, 0, 0]), LOW), (64, ([-76, 0, -10], [-6, 0, 0], [16, 0, 0]), LOW)]
    c = fp_act(100, beats, 76)
    c.hand("left", [(0, "relaxed"), (6, "fan"), (42, "fan"), (49, "open"), (80, "relaxed")])
    C["fp_act_barrage"] = c
    CROSS = ([-60, 0, -40], [-90, 0, 0], Z)
    c = fp_act(96, [(6, ([-140, 0, 50], [-10, 0, 0], Z), ([-140, 0, 50], [-10, 0, 0], Z)), (12, CROSS, CROSS), (44, CROSS, CROSS),
                    (49, ([-40, 0, -50], [-110, 0, 0], Z), ([-40, 0, -50], [-110, 0, 0], Z)), (50, WIDE, WIDE), (62, WIDE, WIDE)], 72)
    c.hand("left", [(0, "relaxed"), (6, "open"), (12, "relaxed"), (50, "open")])
    C["fp_act_aegis"] = c
    TREM = ([-70, 0, 0], [-30, 0, 0], [0, 0, 20])
    beats = [(6, UP, FP_L), (22, ([-170, 0, 8], [-10, 0, 0], [-10, 0, 0]), FP_L), (24, ([-70, 0, 4], [-6, 0, 0], [20, 0, 0]), FP_L)]
    for s in FINALE_SWORDS:
        beats += [(s - 3, ([-60, 0, 30], [-80, 0, 0], Z), FP_L), (s, ([-94, 0, -4], [0, 0, 0], [10, 0, 0]), FP_L), (s + 3, ([-88, 0, 4], [-10, 0, 0], Z), FP_L)]
    beats += [(52, TREM, TREM), (54, ([-72, 0, 2], [-30, 0, 0], [0, 0, 26]), ([-72, 0, 2], [-30, 0, 0], [0, 0, 26])), (57, TREM, TREM),
              (58, ([-140, 0, 44], [-4, 0, 0], Z), ([-140, 0, 44], [-4, 0, 0], Z)), (68, ([-138, 0, 44], [-4, 0, 0], Z), ([-138, 0, 44], [-4, 0, 0], Z))]
    c = fp_act(100, beats, 76)
    c.hand("left", [(0, "relaxed"), (52, "open")])
    C["fp_act_finale"] = c
    # copy cinematic in first person (normally hidden by the stage camera; kept for F1/odd camera mods)
    c = Clip(60)
    c.arm("left", [(0, [-58, 14, 0], [-56, 0, 0], [30, 0, 0]), (12, [-130, 10, 0], [-20, 0, 0], [10, 0, 0]),
                   (44, [-128, 10, 0], [-22, 0, 0], [10, 0, 0]), (52, [-70, 14, 0], [-56, 0, 0], [26, 0, 0]),
                   (60, [-58, 14, 0], [-56, 0, 0], [30, 0, 0])])
    c.arm("right", [(0,) + FP_R, (60,) + FP_R])
    c.hand("left", [(0, "pinch"), (14, "open"), (44, "open"), (48, "pinch"), (60, "pinch")])
    c.hand("right", [(0, "grip")])
    C["fp_copy"] = c
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
