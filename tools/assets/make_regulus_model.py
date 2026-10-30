#!/usr/bin/env python3
"""Generates the Regulus Reborn body, first-person arms and texture atlases (own work, no external sources).

Outputs (repo-relative):
  resources geo/regulus/{regulus,regulus_arms}.geo.json
  binassets textures/entity/regulus/{regulus,regulus_glow}.png.b64
  plain PNG previews under build/regulus-assets/ (not committed).

Body: a slim young man of 31 px (eyes at 26.9 px; vanilla eye height / PLAYER_SCALE = 27.6).
Rig (spec §3): root > hips > torso > chest > neck > head > hair/hair_back; chest > collar;
chest > {right,left}_shoulder > _arm > _forearm > _hand > _thumb/_thumb2, _index/_index2,
_fingers/_fingers2; hips > {right,left}_thigh > _shin > _foot > _toe; hips > coat_front_{r,l},
coat_back (> coat_back_lower) as spring bones. Look: snow-white hair, white long coat with gold
edging over a charcoal high-collared shirt, white trousers and shoes, pale gold eyes (glow pass).
Run: python3 tools/assets/make_regulus_model.py [--check]
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
GEO = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore/geo/regulus")
TEX = os.path.join(ROOT, "viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/regulus")
PREVIEW = os.path.join(ROOT, "build/regulus-assets")

WHITE = "#F4F1EA"
SHADE = "#DAD4C8"
GOLD = "#D9B45A"
INK = "#24222B"
SKIN = "#F0D8C4"
HAIR = "#F7F6F1"

MATS = {
    "coat": (WHITE, "weave", False),
    "coat_shade": (SHADE, "felt", False),
    "lining": ("#E9E2D2", "satin", False),
    "trim": (GOLD, "trim", True),
    "gold": (GOLD, "gold", True),
    "shirt": (INK, "satin", False),
    "collar": (INK, "felt", False),
    "trousers": ("#EEEBE3", "felt", False),
    "belt": ("#3A3640", "satin", False),
    "shoe": ("#F8F6F0", "gloss", False),
    "sole": ("#BDB6A8", "flat", False),
    "skin": (SKIN, "flat", False),
    "skin_shade": ("#E2C4AE", "flat", False),
    "lips": ("#C9958A", "flat", False),
    "sclera": ("#FBFAF7", "flat", False),
    "iris": ("#F0CF6A", "eye", True),
    "brow": ("#E6E5E0", "hair", False),
    "hair": (HAIR, "hair", False),
    "hair_shade": ("#D9D9DE", "hair", False),
}


def side_fns(right):
    def P(p):
        return list(p) if right else [-p[0], p[1], p[2]]

    def C(bone, o, s, mat, rotation=None, pivot=None, faces=None):
        if not right:
            o = mirror_x(o, s)
            rotation = mirror_rot(rotation)
            if pivot is not None:
                pivot = [-pivot[0], pivot[1], pivot[2]]
            if faces:
                swap = {"east": "west", "west": "east"}
                faces = {swap.get(k, k): v for k, v in faces.items()}
        bone.cube(o, s, mat, faces=faces, pivot=pivot, rotation=rotation)

    def R(r):
        return r if right else mirror_rot(r)

    return P, C, R


def arm_chain(m, side, parent):
    """Shoulder > arm > forearm > hand > thumb / index / fingers (two segments each)."""
    right = side == "right"
    P, C, R = side_fns(right)
    n = side
    sh = m.bone(n + "_shoulder", parent, P([-3.4, 21.6, 0.0]))
    C(sh, [-5.75, 20.5, -1.35], [2.3, 1.9, 2.7], "coat")  # shoulder of the coat
    arm = m.bone(n + "_arm", n + "_shoulder", P([-4.85, 21.2, 0.0]))
    C(arm, [-5.75, 16.6, -1.05], [1.85, 4.6, 2.1], "coat")
    fore = m.bone(n + "_forearm", n + "_arm", P([-4.85, 16.7, 0.0]))
    C(fore, [-5.68, 13.1, -0.98], [1.7, 3.8, 1.96], "coat")
    C(fore, [-5.82, 12.55, -1.08], [1.98, 0.8, 2.16], "trim")  # gold cuff
    C(fore, [-5.7, 12.3, -0.9], [1.74, 0.4, 1.8], "shirt")  # shirt cuff
    hand = m.bone(n + "_hand", n + "_forearm", P([-4.85, 12.35, 0.0]))
    C(hand, [-5.45, 10.55, -0.78], [1.15, 1.85, 1.56], "skin")  # palm, faces +x (inwards)
    idx = m.bone(n + "_index", n + "_hand", P([-4.85, 10.6, -0.5]))
    C(idx, [-5.35, 9.55, -0.78], [0.62, 1.08, 0.5], "skin")
    idx2 = m.bone(n + "_index2", n + "_index", P([-4.85, 9.55, -0.53]))
    C(idx2, [-5.32, 8.75, -0.76], [0.56, 0.82, 0.46], "skin_shade")
    fin = m.bone(n + "_fingers", n + "_hand", P([-4.85, 10.6, 0.25]))
    C(fin, [-5.36, 9.6, -0.22], [0.64, 1.0, 1.0], "skin")
    fin2 = m.bone(n + "_fingers2", n + "_fingers", P([-4.85, 9.6, 0.28]))
    C(fin2, [-5.33, 8.85, -0.2], [0.58, 0.78, 0.94], "skin_shade")
    th = m.bone(n + "_thumb", n + "_hand", P([-4.45, 11.6, -0.72]), R([16.0, 0.0, -18.0]))
    C(th, [-4.68, 10.75, -0.98], [0.5, 0.9, 0.52], "skin")
    th2 = m.bone(n + "_thumb2", n + "_thumb", P([-4.45, 10.78, -0.72]))
    C(th2, [-4.66, 10.05, -0.96], [0.46, 0.74, 0.48], "skin_shade")
    return hand


def leg_chain(m, side):
    right = side == "right"
    P, C, R = side_fns(right)
    n = side
    th = m.bone(n + "_thigh", "hips", P([-1.85, 11.6, 0.0]))
    C(th, [-2.95, 6.5, -1.15], [2.15, 5.4, 2.3], "trousers")
    sh = m.bone(n + "_shin", n + "_thigh", P([-1.85, 6.6, 0.0]))
    C(sh, [-2.88, 1.3, -1.05], [2.0, 5.5, 2.1], "trousers")
    C(sh, [-2.92, 1.05, -1.1], [2.08, 0.5, 2.2], "coat_shade")  # hem
    ft = m.bone(n + "_foot", n + "_shin", P([-1.85, 1.15, 0.0]))
    C(ft, [-2.95, 0.0, -1.55], [2.15, 1.3, 2.75], "shoe", faces={"down": "sole"})
    C(ft, [-2.98, 0.0, 1.05], [2.2, 0.35, 0.3], "gold")  # heel band
    toe = m.bone(n + "_toe", n + "_foot", P([-1.85, 0.0, -1.5]))
    C(toe, [-2.9, 0.0, -2.95], [2.05, 0.95, 1.45], "shoe", faces={"down": "sole"})


def build_body(atlas):
    m = Model("regulus", atlas)
    m.bone("root", None, [0, 0, 0])
    hips = m.bone("hips", "root", [0, 12.0, 0])
    hips.cube([-3.45, 10.4, -1.7], [6.9, 2.4, 3.4], "trousers")
    hips.cube([-3.6, 12.0, -1.85], [7.2, 0.8, 3.7], "belt")
    hips.cube([-0.45, 12.05, -1.98], [0.9, 0.7, 0.2], "gold")  # buckle
    torso = m.bone("torso", "hips", [0, 12.8, 0])
    torso.cube([-3.25, 12.8, -1.6], [6.5, 3.6, 3.2], "coat")
    torso.cube([-0.75, 12.8, -1.72], [1.5, 3.6, 0.2], "shirt")  # open front
    torso.cube([-0.95, 12.8, -1.78], [0.22, 3.6, 0.2], "trim")
    torso.cube([0.73, 12.8, -1.78], [0.22, 3.6, 0.2], "trim")
    chest = m.bone("chest", "torso", [0, 16.4, 0])
    chest.cube([-3.85, 16.4, -1.95], [7.7, 5.2, 3.9], "coat")
    chest.cube([-1.1, 16.4, -2.07], [2.2, 5.2, 0.2], "shirt")
    chest.cube([-1.32, 16.4, -2.13], [0.24, 5.0, 0.2], "trim", rotation=[0, 0, -6], pivot=[-1.2, 21.4, -2.1])
    chest.cube([1.08, 16.4, -2.13], [0.24, 5.0, 0.2], "trim", rotation=[0, 0, 6], pivot=[1.2, 21.4, -2.1])
    for y in (17.2, 18.6, 20.0):
        chest.cube([-0.18, y, -2.16], [0.36, 0.36, 0.14], "gold")  # shirt studs
    chest.cube([-3.6, 16.6, 1.92], [7.2, 4.8, 0.2], "coat_shade")  # back seam panel
    collar = m.bone("collar", "chest", [0, 21.6, 0])
    collar.cube([-1.75, 21.5, -1.55], [3.5, 1.7, 0.35], "collar")  # high collar front
    collar.cube([-1.9, 21.5, -1.55], [0.35, 1.9, 3.1], "collar")
    collar.cube([1.55, 21.5, -1.55], [0.35, 1.9, 3.1], "collar")
    collar.cube([-1.9, 21.5, 1.2], [3.8, 2.0, 0.35], "collar")
    collar.cube([-2.9, 21.2, -1.9], [1.1, 1.6, 3.6], "coat", rotation=[0, 0, 14], pivot=[-2.2, 21.4, 0])  # coat lapel collar
    collar.cube([1.8, 21.2, -1.9], [1.1, 1.6, 3.6], "coat", rotation=[0, 0, -14], pivot=[2.2, 21.4, 0])
    collar.cube([-2.95, 22.65, -1.95], [1.2, 0.2, 3.7], "trim", rotation=[0, 0, 14], pivot=[-2.2, 21.4, 0])
    collar.cube([1.75, 22.65, -1.95], [1.2, 0.2, 3.7], "trim", rotation=[0, 0, -14], pivot=[2.2, 21.4, 0])
    neck = m.bone("neck", "chest", [0, 21.8, 0])
    neck.cube([-0.85, 21.6, -0.85], [1.7, 2.4, 1.7], "skin")
    head = m.bone("head", "neck", [0, 23.9, 0])
    head.cube([-2.35, 24.2, -2.35], [4.7, 5.4, 4.7], "skin")
    head.cube([-1.9, 23.8, -2.0], [3.8, 0.5, 3.6], "skin_shade")  # jaw
    head.cube([-0.24, 25.55, -2.62], [0.48, 1.05, 0.3], "skin")  # nose
    head.cube([-0.5, 24.85, -2.42], [1.0, 0.18, 0.1], "lips")
    for sgn in (-1, 1):
        o = [0.45, 26.55, -2.42]
        s = [1.2, 0.55, 0.1]
        io_ = [0.75, 26.55, -2.47]
        is_ = [0.48, 0.55, 0.08]
        bo = [0.35, 27.32, -2.45]
        bs = [1.35, 0.24, 0.1]
        if sgn < 0:
            o, io_, bo = mirror_x(o, s), mirror_x(io_, is_), mirror_x(bo, bs)
        head.cube(o, s, "sclera")
        head.cube(io_, is_, "iris")
        head.cube(bo, bs, "brow", rotation=[0, 0, -7 * sgn], pivot=[sgn * 1.0, 27.44, -2.4])
        ear = [2.32, 25.6, -0.45] if sgn > 0 else [-2.6, 25.6, -0.45]
        head.cube(ear, [0.28, 1.25, 0.9], "skin_shade")
    hair = m.bone("hair", "head", [0, 29.0, 0])
    hair.cube([-2.6, 28.6, -2.6], [5.2, 1.35, 5.2], "hair")
    hair.cube([-2.2, 29.9, -2.1], [4.4, 0.55, 4.2], "hair_shade")
    hair.cube([-2.65, 25.6, -1.6], [0.45, 3.2, 4.2], "hair")  # sides
    hair.cube([2.2, 25.6, -1.6], [0.45, 3.2, 4.2], "hair")
    hair.cube([-2.6, 25.2, 1.8], [5.2, 3.6, 0.85], "hair")  # back
    # fringe: uneven locks falling over the forehead (the bored, unkempt cut)
    locks = [(-2.35, 1.35, 1.9, 14), (-1.05, 1.3, 2.25, 4), (0.2, 1.25, 1.75, -6), (1.25, 1.2, 2.05, -16)]
    for x, w, h, rz in locks:
        hair.cube([x, 29.2 - h, -2.78], [w, h, 0.5], "hair", rotation=[-8, 0, rz], pivot=[x + w / 2, 29.2, -2.6])
    hair.cube([-2.75, 26.0, -2.45], [0.55, 2.4, 1.0], "hair_shade", rotation=[0, 0, 6], pivot=[-2.5, 28.4, -2.0])
    hair.cube([2.2, 26.0, -2.45], [0.55, 2.4, 1.0], "hair_shade", rotation=[0, 0, -6], pivot=[2.5, 28.4, -2.0])
    hb = m.bone("hair_back", "hair", [0, 26.2, 2.3])
    hb.cube([-2.3, 23.2, 1.95], [4.6, 3.1, 0.8], "hair")
    hb.cube([-1.6, 22.4, 2.0], [3.2, 0.9, 0.65], "hair_shade")
    for side in ("right", "left"):
        arm_chain(m, side, "chest")
    # coat skirt: front panels wrap the sides, the back hangs in two segments (spring bones)
    for side in ("right", "left"):
        right = side == "right"
        P, C, R = side_fns(right)
        fr = m.bone("coat_front_" + ("r" if right else "l"), "hips", P([-1.9, 12.3, -1.8]))
        C(fr, [-3.8, 6.1, -2.12], [2.95, 6.2, 0.42], "coat", faces={"south": "lining"})
        C(fr, [-3.98, 6.1, -1.9], [0.42, 6.2, 2.0], "coat", faces={"east": "lining"})
        C(fr, [-0.98, 6.1, -2.2], [0.2, 6.2, 0.2], "trim")
        C(fr, [-3.8, 6.0, -2.2], [2.95, 0.22, 0.2], "trim")
    back = m.bone("coat_back", "hips", [0, 12.3, 1.85])
    back.cube([-3.9, 8.6, 1.62], [7.8, 3.8, 0.45], "coat", faces={"north": "lining"})
    back.cube([-3.98, 8.6, -0.1], [0.42, 3.8, 1.8], "coat")
    back.cube([3.56, 8.6, -0.1], [0.42, 3.8, 1.8], "coat")
    low = m.bone("coat_back_lower", "coat_back", [0, 8.65, 1.85])
    low.cube([-3.8, 5.6, 1.64], [7.6, 3.1, 0.42], "coat", faces={"north": "lining"})
    low.cube([-0.12, 5.6, 2.02], [0.24, 3.1, 0.12], "coat_shade")  # vent
    low.cube([-3.8, 5.45, 1.62], [7.6, 0.22, 0.46], "trim")
    for side in ("right", "left"):
        leg_chain(m, side)
    # VFX locators
    m.bone("right_palm", "right_hand", [-4.25, 10.9, 0.0])
    m.bone("left_palm", "left_hand", [4.25, 10.9, 0.0])
    m.bone("heart", "chest", [0.9, 19.0, -1.0])
    return m


def build_arms(atlas):
    m = Model("regulus_arms", atlas)
    for side in ("right", "left"):
        arm_chain(m, side, None)
    m.bone("right_palm", "right_hand", [-4.25, 10.9, 0.0])
    m.bone("left_palm", "left_hand", [4.25, 10.9, 0.0])
    return m


def b64png(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return base64.b64encode(buf.getvalue()).decode("ascii") + "\n"


def outputs():
    atlas = Atlas(128)
    body = build_body(atlas)
    arms = build_arms(atlas)
    body_json = json.dumps(body.to_json((3, 3)), separators=(",", ":")) + "\n"
    arms_json = json.dumps(arms.to_json((3, 3)), separators=(",", ":")) + "\n"
    tex, glow = paint(atlas, MATS, seed=31)  # after to_json: the atlas slots are assigned there
    files = {
        os.path.join(GEO, "regulus.geo.json"): body_json,
        os.path.join(GEO, "regulus_arms.geo.json"): arms_json,
        os.path.join(TEX, "regulus.png.b64"): b64png(tex),
        os.path.join(TEX, "regulus_glow.png.b64"): b64png(glow),
    }
    return files, (tex, glow), body


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="fail when the committed files differ")
    args = ap.parse_args()
    files, (tex, glow), body = outputs()
    bad = []
    for path, text in files.items():
        if args.check:
            if not os.path.exists(path) or open(path, encoding="utf-8").read() != text:
                bad.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)
    if args.check:
        if bad:
            print("stale:", *bad, sep="\n  ")
            sys.exit(1)
        print("regulus model up to date")
        return
    os.makedirs(PREVIEW, exist_ok=True)
    tex.save(os.path.join(PREVIEW, "regulus.png"))
    glow.save(os.path.join(PREVIEW, "regulus_glow.png"))
    print("bones:", len(body.bones), "materials:", len(MATS))


if __name__ == "__main__":
    main()
