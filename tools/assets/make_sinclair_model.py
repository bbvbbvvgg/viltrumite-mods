#!/usr/bin/env python3
"""Generates every Sinclair model and texture (own work, no external sources).

Outputs (repo-relative):
  resources geo/sinclair/{sinclair,sinclair_arms,savannah,rabbit,top_hat,chest,sword,card,curtain}.geo.json
  binassets textures/entity/sinclair/{sinclair,savannah,rabbit,props}[_glow].png.b64
  plus plain PNG previews of the atlases under build/sinclair-assets/ (not committed).

Body: 32 px mannequin (vanilla pivots: body 12, head 24, arms 22, legs 12), black tailcoat
with two-segment spring tails, crimson waistcoat, white shirtfront and bow tie, gold
medallion, top hat as its own bone (plus a hidden copy in the left hand), full finger
chains (thumb/index/middle/ring+pinky, two segments each), knees, wand and card bones.
Run: python3 tools/assets/make_sinclair_model.py [--check]
"""
import argparse
import base64
import io
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sinclair_geo import Atlas, Model, mirror_x, mirror_rot  # noqa: E402
from sinclair_paint import paint  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GEO = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair")
TEX = os.path.join(ROOT, "viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/sinclair")
PREVIEW = os.path.join(ROOT, "build/sinclair-assets")

INK = "#15121A"
IVORY = "#F3EBDD"
GOLD = "#F2C14E"
CRIMSON = "#8E1B2C"
VIOLET = "#9B6BFF"
LAVENDER = "#C9B6FF"
SAVANNAH = "#B07CFF"

BODY_MATS = {
    "wood": (IVORY, "grain", False),
    "wood_joint": ("#E2D6C2", "joint", False),
    "coat": (INK, "weave", False),
    "coat_satin": ("#2A2433", "satin", False),
    "lining": ("#5E1322", "satin", False),
    "vest": (CRIMSON, "velvet", False),
    "shirt": ("#FAF7F2", "pleat", False),
    "collar": ("#FFFFFF", "flat", False),
    "bow": ("#FBF8F3", "satin", False),
    "stud": ("#1C1822", "stud", False),
    "gold": (GOLD, "gold", True),
    "medallion": (GOLD, "medallion", True),
    "ribbon": (VIOLET, "satin", False),
    "mustache": ("#100D14", "gloss", False),
    "trousers": ("#1B1720", "felt", False),
    "stripe": ("#2C2632", "satin", False),
    "shoe": ("#0E0C11", "gloss", False),
    "spat": ("#EDE6DA", "felt", False),
    "hat": ("#121016", "felt", False),
    "hat_top": ("#1A1720", "gloss", False),
    "hat_band": (VIOLET, "satin", True),
    "wand": ("#0F0D12", "gloss", False),
    "wand_tip": ("#FFFDF6", "flat", True),
    "card_face": (IVORY, "card_face", False),
    "card_back": (CRIMSON, "card_back", False),
}

SAVANNAH_MATS = {
    "skin": (LAVENDER, "ghost", True),
    "skin_dark": (SAVANNAH, "ghost", True),
    "eye": ("#FFFFFF", "eye", True),
    "lips": ("#7B4FD6", "flat", True),
    "hair": ("#5B3AA8", "hair", True),
    "hair_light": ("#8A63E0", "hair", True),
    "glove": ("#E4DAFF", "satin", True),
    "bodice": ("#7A4FD9", "satin", True),
    "tier1": ("#9466F0", "velvet", True),
    "tier2": (SAVANNAH, "velvet", True),
    "tier3": ("#C29BFF", "velvet", True),
    "hem": (LAVENDER, "trim", True),
    "trail": ("#B892FF", "wisp", True),
    "pin": (GOLD, "gold", True),
}

RABBIT_MATS = {
    "fur": ("#F7F4EE", "fur", False),
    "fur_shade": ("#DCD5CA", "fur", False),
    "ear_inner": ("#F2B8C6", "flat", False),
    "nose": ("#E58AA0", "flat", False),
    "eye": ("#1A0F14", "eye", False),
    "hat": ("#121016", "felt", False),
    "hat_band": (VIOLET, "satin", True),
    "bow": (CRIMSON, "satin", False),
}

PROP_MATS = {
    "hat": ("#121016", "felt", False),
    "hat_top": ("#1A1720", "gloss", False),
    "hat_band": (VIOLET, "satin", True),
    "hat_inner": ("#3B1530", "velvet", False),
    "gold": (GOLD, "gold", True),
    "chest": (CRIMSON, "velvet", False),
    "chest_wood": ("#4A2A1C", "grain", False),
    "chest_inner": ("#1D0E24", "felt", False),
    "star": (GOLD, "star", True),
    "blade": ("#D9DEE6", "steel", True),
    "grip": (CRIMSON, "trim", False),
    "card_face": (IVORY, "card_face", False),
    "card_back": (CRIMSON, "card_back", False),
    "curtain": (CRIMSON, "velvet", False),
    "curtain_dark": ("#6A1222", "velvet", False),
    "fringe": (GOLD, "trim", True),
}


# --------------------------------------------------------------------------- body
def arm_chain(m, side, parent="body"):
    """One arm: shoulder -> forearm (elbow) -> hand (wrist) -> fingers. side: 'right' (-x) / 'left' (+x)."""
    right = side == "right"

    def C(bone, o, s, mat, **kw):
        if not right:
            o = mirror_x(o, s)
            if kw.get("rotation") is not None:
                kw["rotation"] = mirror_rot(kw["rotation"])
            if kw.get("pivot") is not None:
                kw["pivot"] = [-kw["pivot"][0], kw["pivot"][1], kw["pivot"][2]]
        bone.cube(o, s, mat, **kw)

    def P(p):
        return p if right else [-p[0], p[1], p[2]]

    def R(r):
        return r if right else mirror_rot(r)

    n = side
    arm = m.bone(n + "_arm", parent, P([-4.9, 21.4, 0.0]))
    C(arm, [-6.35, 19.9, -1.35], [2.0, 2.1, 2.7], "coat")  # shoulder pad
    C(arm, [-6.05, 16.4, -1.0], [1.55, 3.8, 2.0], "coat")
    fore = m.bone(n + "_forearm", n + "_arm", P([-5.25, 16.6, 0.0]))
    C(fore, [-5.98, 16.0, -0.98], [1.46, 1.0, 1.96], "coat")  # elbow
    C(fore, [-5.95, 13.3, -0.92], [1.4, 3.0, 1.84], "coat")
    C(fore, [-6.07, 12.45, -1.04], [1.64, 0.95, 2.08], "collar")  # cuff
    C(fore, [-6.17, 12.7, -0.25], [0.12, 0.42, 0.42], "gold")  # cufflink
    hand = m.bone(n + "_hand", n + "_forearm", P([-5.25, 12.45, 0.0]))
    C(hand, [-5.62, 11.95, -0.52], [0.74, 0.6, 1.04], "wood_joint")
    C(hand, [-5.85, 10.35, -0.92], [1.2, 1.65, 1.84], "wood")
    # fingers hang from the palm, front (-z) to back: index, middle, ring+pinky; thumb front-inner
    fingers = [
        ("index", -0.62, 0.48, 1.05, 0.95),
        ("middle", -0.08, 0.5, 1.15, 1.0),
        ("ring", 0.5, 0.78, 0.95, 0.8),
    ]
    for fname, fz, fd, l1, l2 in fingers:
        b1 = m.bone(n + "_" + fname, n + "_hand", P([-5.25, 10.4, fz]))
        C(b1, [-5.55, 10.4 - l1, fz - fd / 2], [0.6, l1, fd], "wood")
        b2 = m.bone(n + "_" + fname + "2", n + "_" + fname, P([-5.25, 10.4 - l1, fz]))
        C(b2, [-5.52, 10.4 - l1 - l2, fz - fd / 2 + 0.03], [0.54, l2, fd - 0.06], "wood")
    t1 = m.bone(n + "_thumb", n + "_hand", P([-4.85, 11.2, -0.8]), R([18.0, 0.0, -14.0]))
    C(t1, [-5.1, 10.3, -1.06], [0.52, 0.95, 0.52], "wood")
    t2 = m.bone(n + "_thumb2", n + "_thumb", P([-4.85, 10.32, -0.8]))
    C(t2, [-5.08, 9.5, -1.04], [0.48, 0.85, 0.48], "wood")
    return hand


def _fix(bone):
    """Normalises cubes written as (origin, mat, size) or (origin, size, mat)."""
    for c in bone.cubes:
        vals = [c["origin"], c["size"], c["mat"]]
        s = next((v for v in vals[1:] if isinstance(v, list)), None)
        mat = next((v for v in vals[1:] if isinstance(v, str)), None)
        c["size"], c["mat"] = s, mat


def build_body(atlas, ident="sinclair", arms_only=False):
    m = Model(ident, atlas)
    if not arms_only:
        body = m.bone("body", None, [0, 12, 0])
        # torso core: crimson waistcoat below, massive white shirtfront above
        body.cube([-2.9, 12.4, -1.6], [5.8, 3.2, 3.2], "vest")
        body.cube([-3.5, 15.4, -2.0], [7.0, 2.0, 4.0], "vest")
        body.cube([-4.1, 17.2, -2.35], [8.2, 4.5, 4.5], "shirt")
        body.cube([-4.75, 20.5, -2.25], [9.5, 1.7, 4.45], "coat")  # shoulder yoke
        # coat shell: back, sides, two front panels leaving the shirtfront open
        body.cube([-4.4, 13.6, 1.05], [8.8, 7.2, 1.35], "coat")
        body.cube([-3.2, 12.2, 1.0], [6.4, 1.6, 1.1], "coat")
        body.cube([4.05, 15.0, -2.3], [0.7, 5.6, 4.4], "coat")
        body.cube([-4.75, 15.0, -2.3], [0.7, 5.6, 4.4], "coat")
        body.cube([2.85, 12.3, -1.75], [0.55, 3.0, 3.3], "coat")
        body.cube([-3.4, 12.3, -1.75], [0.55, 3.0, 3.3], "coat")
        body.cube([1.35, 15.0, -2.75], [3.4, 5.6, 0.5], "coat")
        body.cube([-4.75, 15.0, -2.75], [3.4, 5.6, 0.5], "coat")
        # waistcoat buttons and shirt studs
        for y in (13.2, 14.4, 15.6):
            body.cube([-0.2, y, -1.75], [0.4, 0.4, 0.2], "gold")
        for y in (17.6, 18.8, 19.9):
            body.cube([-0.18, y, -2.5], [0.36, 0.36, 0.18], "stud")
        # wing collar and bow tie
        body.cube([-1.5, 21.6, -2.4], [3.0, 0.9, 1.0], "collar")
        body.cube([-0.42, 21.0, -2.82], [0.84, 0.72, 0.5], "bow")
        body.cube([-1.75, 20.82, -2.72], [1.3, 1.1, 0.4], "bow", rotation=[0, 0, -8], pivot=[-0.4, 21.35, -2.5])
        body.cube([0.45, 20.82, -2.72], [1.3, 1.1, 0.4], "bow", rotation=[0, 0, 8], pivot=[0.4, 21.35, -2.5])
        # neck (wood)
        body.cube([-0.8, 22.0, -0.85], [1.6, 1.6, 1.6], "wood")
        # lapels (satin) on their own bones so the poser can lift them with the torso lean
        lap_r = m.bone("lapel_right", "body", [-1.6, 21.6, -2.7])
        lap_r.cube([-2.8, 17.0, -3.05], [1.35, 4.6, 0.32], "coat_satin", rotation=[0, 0, 16], pivot=[-1.6, 21.6, -2.9])
        lap_l = m.bone("lapel_left", "body", [1.6, 21.6, -2.7])
        lap_l.cube([1.45, 17.0, -3.05], [1.35, 4.6, 0.32], "coat_satin", rotation=[0, 0, -16], pivot=[1.6, 21.6, -2.9])
        hem = m.bone("vest_hem", "body", [0, 12.6, -1.6])
        hem.cube([-2.95, 11.7, -1.75], [2.9, 0.9, 0.5], "vest", rotation=[0, 0, 10], pivot=[0, 12.6, -1.6])
        hem.cube([0.05, 11.7, -1.75], [2.9, 0.9, 0.5], "vest", rotation=[0, 0, -10], pivot=[0, 12.6, -1.6])
        # medallion on a violet ribbon
        med = m.bone("medallion", "body", [0, 21.4, -2.6])
        med.cube([-1.5, 19.2, -2.62], [0.32, 2.2, 0.14], "ribbon", rotation=[0, 0, -22], pivot=[-1.2, 21.3, -2.6])
        med.cube([1.18, 19.2, -2.62], [0.32, 2.2, 0.14], "ribbon", rotation=[0, 0, 22], pivot=[1.2, 21.3, -2.6])
        med.cube([-0.7, 17.5, -2.85], [1.4, 1.4, 0.32], "gold", faces={"north": "medallion"})
        # coat tails: two-segment spring bones with crimson lining inside
        for side, x0 in (("right", -3.1), ("left", 0.2)):
            px = -1.7 if side == "right" else 1.7
            t1 = m.bone("coat_tail_" + side, "body", [px, 12.9, 1.9])
            t1.cube([x0, 8.6, 1.45], [2.9, 4.4, 0.7], "coat", faces={"north": "lining"})
            t2 = m.bone("coat_tail_" + side + "2", "coat_tail_" + side, [px, 8.65, 1.8])
            t2.cube([x0 + 0.15, 4.6, 1.5], [2.6, 4.1, 0.6], "coat", faces={"north": "lining"})
            t2.cube([x0 + 0.5, 3.8, 1.55], [1.9, 0.8, 0.5], "coat", faces={"north": "lining"})
            t2.cube([x0 + 1.1, 6.2, 2.08], [0.7, 0.7, 0.12], "gold")  # tail button
        # head: egg-shaped mannequin, nose ridge, thin curled mustache, no eyes or mouth
        head = m.bone("head", "body", [0, 23.4, 0])
        head.cube([-2.35, 24.2, -2.6], [4.7, 5.5, 4.8], "wood")
        head.cube([-1.95, 29.7, -2.2], [3.9, 0.7, 4.0], "wood")
        head.cube([-1.75, 23.35, -2.25], [3.5, 0.9, 3.9], "wood")
        head.cube([-2.55, 25.0, -2.2], [0.25, 3.8, 3.8], "wood")
        head.cube([2.3, 25.0, -2.2], [0.25, 3.8, 3.8], "wood")
        head.cube([-0.32, 25.7, -2.95], [0.64, 1.9, 0.4], "wood")
        head.cube([-0.55, 25.05, -2.98], [1.1, 0.42, 0.42], "mustache")
        for sgn in (-1, 1):
            o = [0.45, 24.98, -2.92]
            s = [1.25, 0.42, 0.36]
            tip_o = [1.55, 25.22, -2.86]
            tip_s = [0.42, 0.62, 0.3]
            if sgn < 0:
                o, tip_o = mirror_x(o, s), mirror_x(tip_o, tip_s)
            head.cube(o, s, "mustache", rotation=[0, 0, 12 * sgn], pivot=[0.45 * sgn, 25.2, -2.8])
            head.cube(tip_o, tip_s, "mustache", rotation=[0, 0, 28 * sgn], pivot=[1.6 * sgn, 25.3, -2.8])
        hat = m.bone("hat", "head", [0, 30.3, 0], [-4.0, 0.0, 6.0])
        top_hat(hat, 30.3)
    # arms
    for side in ("right", "left"):
        arm_chain(m, side, None if arms_only else "body")
    rh = m.by_name["right_hand"]
    # wand "Prestige" continues the hand's long axis out of the fist; tip locator for VFX
    wand = m.bone("wand", "right_hand", [-5.25, 9.9, -0.1])
    wand.cube([-5.44, 3.6, -0.29], [0.38, 7.5, 0.38], "wand")
    wand.cube([-5.47, 2.6, -0.32], [0.44, 1.0, 0.44], "wand_tip")
    wand.cube([-5.5, 8.15, -0.35], [0.5, 0.3, 0.5], "gold")
    wand.cube([-5.5, 11.1, -0.35], [0.5, 0.3, 0.5], "gold")
    m.bone("wand_tip", "wand", [-5.25, 2.6, -0.1])
    card = m.bone("card", "left_hand", [5.25, 9.9, -0.1])
    card.cube([5.2, 8.3, -1.9], [0.1, 2.8, 2.0], "card_face", faces={"east": "card_face", "west": "card_back"})
    # top hat held by the brim: crown towards hand +z, so a forward-raised arm presents it opening up
    held = m.bone("held_hat", "left_hand", [5.25, 9.4, -0.1])
    top_hat(held, 0.0, axis=(5.25, 6.0, -0.3))
    if not arms_only:
        for side in ("right", "left"):
            leg_chain(m, side)
    del rh
    return m


def top_hat(bone, y, center=(0.0, 0.0), axis=None):
    """Top hat with the crown along +y from height y. axis=(cx, cy, z0) instead lays the crown
    along +z starting at z0, centred on (cx, cy) - the copy held in the hand."""
    cx, cz = center
    parts = [
        ([cx - 3.6, y, cz - 3.6], [7.2, 0.4, 7.2], "hat"),
        ([cx - 2.5, y + 0.4, cz - 2.5], [5.0, 5.4, 5.0], "hat"),
        ([cx - 2.7, y + 5.6, cz - 2.7], [5.4, 0.6, 5.4], "hat_top"),
        ([cx - 2.55, y + 0.4, cz - 2.55], [5.1, 1.25, 5.1], "hat_band"),
        ([cx - 0.5, y + 0.45, cz - 2.72], [1.0, 1.15, 0.2], "gold"),
    ]
    for o, s, mat in parts:
        if axis is not None:
            ax, ay, z0 = axis
            o, s = [ax + o[0], ay - (o[2] + s[2]), z0 + o[1]], [s[0], s[2], s[1]]
        bone.cube(o, s, mat)


def leg_chain(m, side):
    def X(o, s):
        return o if side == "right" else mirror_x(o, s)

    px = -1.6 if side == "right" else 1.6
    leg = m.bone(side + "_leg", None, [px, 12.0, 0.0])
    leg.cube(X([-2.55, 6.6, -1.1], [1.95, 5.6, 2.05]), [1.95, 5.6, 2.05], "trousers", faces={"west" if side == "right" else "east": "stripe"})
    knee = m.bone(side + "_knee", side + "_leg", [px, 6.7, 0.0])
    knee.cube(X([-2.5, 5.9, -1.15], [1.85, 1.0, 2.1]), [1.85, 1.0, 2.1], "trousers")
    knee.cube(X([-2.45, 1.2, -1.0], [1.75, 4.8, 1.9]), [1.75, 4.8, 1.9], "trousers", faces={"west" if side == "right" else "east": "stripe"})
    knee.cube(X([-2.55, 0.85, -1.15], [1.95, 0.75, 2.2]), [1.95, 0.75, 2.2], "spat")
    knee.cube(X([-2.6, 0.0, -2.7], [2.05, 0.95, 3.7]), [2.05, 0.95, 3.7], "shoe")
    knee.cube(X([-2.45, 0.0, -3.1], [1.75, 0.6, 0.5]), [1.75, 0.6, 0.5], "shoe")
    _fix(leg)
    _fix(knee)


# --------------------------------------------------------------------------- savannah
def build_savannah(atlas):
    m = Model("savannah", atlas)
    root = m.bone("root", None, [0, 0, 0])
    torso = m.bone("torso", "root", [0, 14.0, 0])
    torso.cube([-1.6, 14.0, -1.1], [3.2, 2.6, 2.2], "bodice")
    torso.cube([-2.0, 16.4, -1.3], [4.0, 2.9, 2.6], "bodice")
    torso.cube([-1.2, 17.0, -1.45], [2.4, 1.6, 0.3], "skin")  # neckline
    torso.cube([-2.6, 19.1, -1.1], [5.2, 0.9, 2.2], "skin")  # shoulders
    torso.cube([-0.5, 19.8, -0.5], [1.0, 0.9, 1.0], "skin")  # neck
    head = m.bone("head", "torso", [0, 20.4, 0])
    head.cube([-1.7, 20.5, -1.8], [3.4, 3.7, 3.4], "skin")
    head.cube([-1.3, 20.2, -1.5], [2.6, 0.5, 2.8], "skin")
    head.cube([-1.05, 22.1, -1.86], [0.7, 0.32, 0.1], "eye")
    head.cube([0.35, 22.1, -1.86], [0.7, 0.32, 0.1], "eye")
    head.cube([-0.4, 21.0, -1.86], [0.8, 0.25, 0.1], "lips")
    hair = m.bone("hair", "head", [0, 24.0, 0.6])
    hair.cube([-1.95, 23.5, -2.0], [3.9, 1.0, 3.9], "hair")
    hair.cube([-1.95, 21.6, -2.0], [0.5, 2.1, 3.6], "hair")
    hair.cube([1.45, 21.6, -2.0], [0.5, 2.1, 3.6], "hair")
    hair.cube([-1.0, 23.9, -2.25], [2.6, 0.7, 0.6], "hair_light", rotation=[0, 0, -10], pivot=[0.3, 24.2, -2.0])
    hair.cube([1.1, 23.7, -1.0], [0.6, 0.6, 0.6], "pin")
    hair.cube([-1.95, 19.4, 0.55], [3.9, 4.4, 1.3], "hair")
    mid = m.bone("hair_mid", "hair", [0, 19.6, 1.2])
    mid.cube([-1.8, 15.9, 0.75], [3.6, 3.8, 1.15], "hair")
    tip = m.bone("hair_tip", "hair_mid", [0, 16.0, 1.3])
    tip.cube([-1.5, 12.9, 0.85], [3.0, 3.2, 0.95], "hair_light")
    tip.cube([-0.9, 11.9, 0.95], [1.8, 1.1, 0.7], "hair_light")
    for side in ("right", "left"):
        sgn = -1 if side == "right" else 1

        def X(o, s, side=side):
            return o if side == "right" else mirror_x(o, s)

        arm = m.bone(side + "_arm", "torso", [2.5 * sgn, 19.3, 0])
        arm.cube(X([-3.35, 16.1, -0.6], [1.1, 3.5, 1.2]), [1.1, 3.5, 1.2], "skin")
        _fix(arm)
        fore = m.bone(side + "_forearm", side + "_arm", [2.8 * sgn, 16.2, 0])
        fore.cube(X([-3.35, 12.9, -0.62], [1.1, 3.4, 1.24]), [1.1, 3.4, 1.24], "glove")
        fore.cube(X([-3.45, 15.6, -0.72], [1.3, 0.5, 1.44]), [1.3, 0.5, 1.44], "hem")
        _fix(fore)
        hand = m.bone(side + "_hand", side + "_forearm", [2.8 * sgn, 12.95, 0])
        hand.cube(X([-3.3, 11.75, -0.55], [1.0, 1.25, 1.1]), [1.0, 1.25, 1.1], "glove")
        _fix(hand)
        fing = m.bone(side + "_fingers", side + "_hand", [2.8 * sgn, 11.8, 0])
        fing.cube(X([-3.25, 10.75, -0.5], [0.9, 1.05, 1.0]), [0.9, 1.05, 1.0], "glove")
        _fix(fing)
        th = m.bone(side + "_thumb", side + "_hand", [2.55 * sgn, 12.3, -0.55])
        th.cube(X([-2.85, 11.3, -0.85], [0.45, 1.0, 0.45]), [0.45, 1.0, 0.45], "glove")
        _fix(th)
    # three-tier dress (ribbons) and a smoke trail instead of legs
    t1 = m.bone("tier1", "torso", [0, 14.2, 0])
    t1.cube([-2.3, 11.0, -1.6], [4.6, 3.3, 3.2], "tier1")
    t1.cube([-2.4, 11.0, -1.7], [4.8, 0.5, 3.4], "hem")
    t2 = m.bone("tier2", "tier1", [0, 11.2, 0])
    t2.cube([-3.0, 7.6, -2.1], [6.0, 3.7, 4.2], "tier2")
    t2.cube([-3.1, 7.6, -2.2], [6.2, 0.5, 4.4], "hem")
    t3 = m.bone("tier3", "tier2", [0, 7.8, 0])
    t3.cube([-3.6, 4.2, -2.6], [7.2, 3.7, 5.2], "tier3")
    t3.cube([-3.7, 4.2, -2.7], [7.4, 0.5, 5.4], "hem")
    tr = m.bone("trail", "tier3", [0, 4.4, 0])
    tr.cube([-2.4, 1.6, -1.8], [4.8, 2.8, 3.6], "trail")
    tr2 = m.bone("trail2", "trail", [0, 1.8, 0])
    tr2.cube([-1.4, -0.6, -0.9], [2.8, 2.4, 2.2], "trail")
    tr3 = m.bone("trail3", "trail2", [0, -0.4, 0])
    tr3.cube([-0.6, -2.0, -0.3], [1.2, 1.6, 1.2], "trail")
    del root
    return m


# --------------------------------------------------------------------------- rabbit
def build_rabbit(atlas):
    m = Model("rabbit", atlas)
    body = m.bone("body", None, [0, 3.0, 0])
    body.cube([-1.5, 1.0, -1.9], [3.0, 2.9, 4.2], "fur")
    body.cube([-1.3, 3.6, -1.5], [2.6, 0.6, 3.2], "fur_shade")
    tail = m.bone("tail", "body", [0, 2.6, 2.3])
    tail.cube([-0.6, 2.0, 2.2], [1.2, 1.2, 0.9], "fur")
    for side in ("right", "left"):
        sgn = -1 if side == "right" else 1

        def X(o, s, side=side):
            return o if side == "right" else mirror_x(o, s)

        hind = m.bone(side + "_haunch", "body", [1.3 * sgn, 2.2, 1.0])
        hind.cube(X([-2.0, 0.6, -0.1], [0.9, 2.2, 2.3]), [0.9, 2.2, 2.3], "fur")
        hind.cube(X([-1.95, 0.0, -1.2], [0.85, 0.6, 2.6]), [0.85, 0.6, 2.6], "fur_shade")
        _fix(hind)
        paw = m.bone(side + "_paw", "body", [0.8 * sgn, 1.6, -1.5])
        paw.cube(X([-1.15, 0.0, -2.05], [0.7, 1.7, 0.7]), [0.7, 1.7, 0.7], "fur")
        _fix(paw)
    head = m.bone("head", "body", [0, 3.9, -1.6])
    head.cube([-1.3, 3.4, -3.7], [2.6, 2.5, 2.5], "fur")
    head.cube([-0.75, 3.55, -4.15], [1.5, 1.0, 0.55], "fur_shade")
    head.cube([-0.25, 4.3, -4.22], [0.5, 0.32, 0.12], "nose")
    head.cube([-1.36, 4.6, -3.2], [0.1, 0.5, 0.5], "eye")
    head.cube([1.26, 4.6, -3.2], [0.1, 0.5, 0.5], "eye")
    head.cube([-0.65, 3.25, -3.0], [1.3, 0.35, 0.9], "bow")  # crimson bow tie
    for side in ("right", "left"):
        sgn = -1 if side == "right" else 1
        e1 = m.bone("ear_" + side, "head", [0.75 * sgn, 5.8, -2.4], [-10.0, 0.0, 18.0 * sgn])
        o = [0.25, 5.8, -2.7] if sgn > 0 else mirror_x([0.25, 5.8, -2.7], [0.8, 2.3, 0.5])
        e1.cube(o, [0.8, 2.3, 0.5], "fur", faces={"north": "ear_inner"})
        e2 = m.bone("ear_" + side + "2", "ear_" + side, [0.65 * sgn, 8.1, -2.45])
        o = [0.3, 8.1, -2.68] if sgn > 0 else mirror_x([0.3, 8.1, -2.68], [0.7, 1.6, 0.46])
        e2.cube(o, [0.7, 1.6, 0.46], "fur", faces={"north": "ear_inner"})
    hat = m.bone("hat", "head", [0, 5.9, -2.3], [-6.0, 0.0, 10.0])
    hat.cube([-0.95, 5.9, -3.25], [1.9, 0.22, 1.9], "hat")
    hat.cube([-0.6, 6.12, -2.9], [1.2, 1.35, 1.2], "hat")
    hat.cube([-0.65, 6.12, -2.95], [1.3, 0.35, 1.3], "hat_band")
    return m


# --------------------------------------------------------------------------- props
def build_props(atlas):
    out = {}
    hat = Model("sinclair_top_hat", atlas)
    b = hat.bone("hat", None, [0, 0, 0])
    top_hat(b, 0.0)
    b.cube([-2.4, 0.42, -2.4], [4.8, 0.05, 4.8], "hat_inner")
    out["top_hat"] = hat

    chest = Model("sinclair_chest", atlas)
    base = chest.bone("chest", None, [0, 0, 0])
    base.cube([-8, 0, -6], [16, 10, 12], "chest", faces={"up": "chest_inner"})
    for x in (-8.3, 7.1):
        for z in (-6.3, 5.1):
            base.cube([x, -0.2, z], [1.2, 10.4, 1.2], "gold")
    base.cube([-1.5, 0.8, -6.6], [3.0, 3.0, 0.4], "star")
    base.cube([-8.2, 0, -6.2], [16.4, 1.0, 12.4], "gold")
    lid = chest.bone("lid", "chest", [0, 10, 6])
    lid.cube([-8.3, 10, -6.3], [16.6, 3.6, 12.6], "chest")
    lid.cube([-8.4, 13.2, -6.4], [16.8, 0.8, 12.8], "gold")
    lid.cube([-1.0, 8.6, -6.8], [2.0, 2.6, 0.6], "gold")
    out["chest"] = chest

    sword = Model("sinclair_sword", atlas)
    s = sword.bone("sword", None, [0, 0, 0])
    s.cube([-0.45, 1.6, -0.15], [0.9, 18.0, 0.3], "blade")
    s.cube([-0.25, 19.6, -0.12], [0.5, 1.4, 0.24], "blade")
    s.cube([-2.2, 0.9, -0.55], [4.4, 0.8, 1.1], "gold")
    s.cube([-0.4, -2.4, -0.4], [0.8, 3.3, 0.8], "grip")
    s.cube([-0.6, -3.2, -0.6], [1.2, 0.9, 1.2], "gold")
    out["sword"] = sword

    card = Model("sinclair_card", atlas)
    c = card.bone("card", None, [0, 0, 0])
    c.cube([-1.25, -1.75, -0.06], [2.5, 3.5, 0.12], "card_face", faces={"south": "card_back"})
    out["card"] = card

    curtain = Model("sinclair_curtain", atlas)
    rod = curtain.bone("curtain", None, [0, 0, 0])
    rod.cube([-8.5, 30.0, -0.5], [17.0, 1.0, 1.0], "gold")
    for i in range(8):
        x = -8 + i * 2
        depth = 0.0 if i % 2 == 0 else 0.9
        fold = curtain.bone("fold%d" % i, "curtain", [x + 1, 30.0, depth])
        fold.cube([x, 1.2, -0.6 + depth], [2.0, 28.8, 1.0], "curtain" if i % 2 == 0 else "curtain_dark")
        fold.cube([x, 0.0, -0.7 + depth], [2.0, 1.2, 1.2], "fringe")
    out["curtain"] = curtain
    return out


# --------------------------------------------------------------------------- io
def b64_png(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return base64.b64encode(buf.getvalue()).decode("ascii")


def generate():
    """Returns {repo-relative path: text} for every generated file and the atlas images."""
    files = {}
    images = {}

    def add_model(name, model):
        files[os.path.join(GEO, name + ".geo.json")] = json.dumps(model.to_json(), separators=(",", ":")) + "\n"

    def add_tex(name, atlas, mats, glow=True):
        tex, glo = paint(atlas, mats)
        images[name] = tex
        files[os.path.join(TEX, name + ".png.b64")] = b64_png(tex)
        if glow:
            images[name + "_glow"] = glo
            files[os.path.join(TEX, name + "_glow.png.b64")] = b64_png(glo)

    a = Atlas(128)
    for mat in BODY_MATS:  # fixed slot order keeps the texture stable when cubes change
        a.slot(mat)
    add_model("sinclair", build_body(a))
    add_model("sinclair_arms", build_body(a, "sinclair_arms", arms_only=True))
    add_tex("sinclair", a, BODY_MATS)

    sa = Atlas(64)
    for mat in SAVANNAH_MATS:
        sa.slot(mat)
    add_model("savannah", build_savannah(sa))
    add_tex("savannah", sa, SAVANNAH_MATS)

    ra = Atlas(64)
    for mat in RABBIT_MATS:
        ra.slot(mat)
    add_model("rabbit", build_rabbit(ra))
    add_tex("rabbit", ra, RABBIT_MATS)

    pa = Atlas(64)
    for mat in PROP_MATS:
        pa.slot(mat)
    for name, model in build_props(pa).items():
        add_model(name, model)
    add_tex("props", pa, PROP_MATS)
    return files, images


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--check", action="store_true", help="fail if committed files differ")
    args = ap.parse_args()
    files, images = generate()
    stale = []
    for path, text in files.items():
        if args.check:
            if not os.path.isfile(path) or open(path, encoding="utf-8").read() != text:
                stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)
    if args.check:
        for p in stale:
            print("stale:", p)
        return 1 if stale else 0
    os.makedirs(PREVIEW, exist_ok=True)
    for name, img in images.items():
        img.save(os.path.join(PREVIEW, name + ".png"))
    print("wrote %d files; atlas previews in %s" % (len(files), os.path.relpath(PREVIEW, ROOT)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
