#!/usr/bin/env python3
"""Generates the Regulus Reborn body model and its skin atlas (own work, CC0, no external sources).

Style: a Minecraft hero skin, like Reinhard and Sung Jinwoo: vanilla player proportions (8 px head,
8x12x4 torso, slim 3 px arms, 4 px legs, 32 px tall), flat pixel-art faces at 2 texels per model
px, an inflated "hat" layer for the hair. The look: snow-white wavy hair, pale gold half-lidded eyes,
an open white long coat with gold edging over a charcoal high-collared shirt, white trousers and shoes.

Rig (bone names and the leg joints are a contract with make_regulus_clips.py / regulus_rig.py, the
poser and the VFX anchors): root > hips > torso > chest > neck > head > hair > hair_back;
chest > collar; chest > {right,left}_shoulder > _arm > _forearm > _hand > _thumb/_thumb2,
_index/_index2, _fingers/_fingers2; hips > {right,left}_thigh > _shin > _foot > _toe;
hips > coat_front_{r,l}, coat_back > coat_back_lower (spring bones); locators right_palm,
left_palm, heart. Leg joints stay where the IK expects them (hip 11.6, knee 6.6, ankle 1.15,
ball of the foot z -1.5); cube edges sit on the 0.5 px grid.

Every texel is painted from its rest-pose position on the body (paint(x, y, z, normal)), so a
surface split over several bones stays continuous across the joints.

The first-person arms are NOT generated here: geo/regulus/regulus_arms.geo.json and
textures/entity/regulus/regulus_fp(_glow).png.b64 are the frozen first Reborn arms (pinned by
RegulusAssetsTest); --check verifies they are untouched.

Outputs: resources geo/regulus/regulus.geo.json, binassets textures/entity/regulus/
{regulus,regulus_glow}.png.b64, previews under build/regulus-assets/ (not committed).
Run: python3 tools/assets/make_regulus_model.py [--check]
"""
import argparse
import base64
import hashlib
import io
import json
import math
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GEO = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore/geo/regulus")
TEX = os.path.join(ROOT, "viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/regulus")
PREVIEW = os.path.join(ROOT, "build/regulus-assets")

D = 2  # texels per model px
ATLAS = 128

FROZEN = {  # first-person arms: owner-approved, never regenerated
    os.path.join(GEO, "regulus_arms.geo.json"): "17e7358e8ec51dcb4a61aa35b43c34039537af9c0c84ec946261beda0840c7fa",
    os.path.join(TEX, "regulus_fp.png.b64"): "2c222b0d9e06ad764f17a1ffe777e5140e44e6dbca1520a3727b1e07f4e1d35e",
    os.path.join(TEX, "regulus_fp_glow.png.b64"): "df025314ab932457f9c9c77098d12ee19fcf725480dc431944ee77feb741085d",
}


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(3)) + (c1[3] if len(c1) > 3 else 255,)


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


CLEAR = (0, 0, 0, 0)
# palette
COAT = hexc("F3F0E9")
COAT_SH = hexc("DCD6CA")
COAT_DK = hexc("C4BDAF")
LINING = hexc("E3DACA")
GOLD = hexc("D9B04E")
GOLD_HI = hexc("F2D687")
GOLD_DK = hexc("A37D2C")
SHIRT = hexc("2B2932")
SHIRT_HI = hexc("3B3844")
TROUS = hexc("ECE9E2")
TROUS_SH = hexc("D3CEC4")
SHOE = hexc("F7F5F0")
SOLE = hexc("B7B0A3")
SKIN = hexc("F3DDCC")
SKIN_SH = hexc("E3C4AF")
SKIN_DK = hexc("D2AE98")
HAIR = hexc("F5F4F1")
HAIR_MID = hexc("DCDBE6")
HAIR_SH = hexc("BDBBD0")
HAIR_DK = hexc("9C99B3")
LASH = hexc("3A3140")
SCLERA = hexc("FBFAF6")
IRIS = hexc("E9BE45")
IRIS_DK = hexc("B07F22")
IRIS_HI = hexc("FFF1B8")
MOUTH = hexc("B98478")


def noise(x, y, z, seed=0):
    """Deterministic per-texel jitter in [-1, 1] (pixel-art grain, no RNG state)."""
    h = hashlib.blake2b(("%d,%d,%d,%d" % (int(math.floor(x * D)), int(math.floor(y * D)), int(math.floor(z * D)), seed)).encode(),
                        digest_size=2).digest()
    return (h[0] * 256 + h[1]) / 32767.5 - 1.0


def grain(c, x, y, z, amount=0.015, seed=0):
    return shade(c, 1.0 + amount * noise(x, y, z, seed))


# ------------------------------------------------------------------ face (front of the head, 16x16 texels)
# col 0 is the model's right (x = -4), row 0 is the top (y = 32).
FACE = [
    "HHHHHHHHHHHHHHHH",
    "HHHHHHHHHHHHHHHH",
    "HHHHHHHHHHHHHHHH",
    "HHHHHHHHHHHHHHHH",
    "HHHHHHHHHHHHHHHH",
    "HHhSSHHHHSShHHHH",
    "hSSSSSSSSSSSSSSh",
    "SSBBBSSSSSSBBBSS",
    "SLLLLLSSSSLLLLLS",
    "SWYYyLSSSSLyYYWS",
    "SSyYYSSSSSSYYySS",
    "SSSSSSSNSSSSSSSS",
    "SSSSSSSSSSSSSSSS",
    "SSSSSSSMMMmSSSSS",
    "SSSSSSSSSSSSSSSS",
    "SSSSSSSSSSSSSSSS",
]
FACE_COL = {"H": HAIR, "h": HAIR_MID, "S": SKIN, "B": HAIR_SH, "L": LASH, "W": SCLERA,
            "Y": IRIS, "y": IRIS_DK, "N": SKIN_SH, "M": MOUTH, "m": SKIN_DK}
FACE_GLOW = {"Y": (96, 70, 16, 255), "y": (40, 28, 6, 255)}


def head_paint(x, y, z, n, glow):
    if n == (0, 0, -1):
        col = min(15, max(0, int(math.floor((x + 4) * D))))
        row = min(15, max(0, int(math.floor((32 - y) * D))))
        ch = FACE[row][col]
        if glow:
            return FACE_GLOW.get(ch, CLEAR)
        c = FACE_COL[ch]
        return strand(x, y, z, n) if ch in "Hh" else grain(c, x, y, z, 0.012) if ch == "S" else c
    if glow:
        return CLEAR
    if n == (0, -1, 0):
        return SKIN_SH
    if n == (0, 1, 0) or n == (0, 0, 1):
        return hair_paint(x, y, z, n, False)
    # sides: hair above the temples and behind the ear, skin + ear in front
    front = -z  # 4 at the face
    if y >= 29.5 or front < 0.5 or (y >= 27.0 and front < 2.5) or (front >= 3.0 and y >= 27.5):
        return hair_paint(x, y, z, n, False)
    if -0.5 <= z <= 1.0 and 25.5 <= y < 27.5:
        return SKIN_SH if (z > 0.5 or y < 26.0) else SKIN  # ear
    return grain(SKIN, x, y, z, 0.02)


def strand(x, y, z, n):
    """White hair in locks: each lock ~1.5 px wide with a lavender parting line, darker towards the tips."""
    if n[1] != 0:
        u = x + z * 0.5
    else:
        u = (x if n[0] == 0 else z) + 0.25 * math.sin(y * 1.3)
    k = int(math.floor(u * D))
    lock = k // 3
    c = HAIR if lock % 2 == 0 else hexc("ECEBF0")
    if k % 3 == 0:
        c = HAIR_MID
    if n[1] == 0 and y < 27.5:
        c = mix(c, HAIR_SH, min(1.0, (27.5 - y) / 2.5))
    if n[1] == 0 and k % 3 == 1 and 30.0 < y < 31.5:
        c = hexc("FFFFFF")  # sheen band
    return grain(c, x, y, z, 0.01, 3)


def hair_paint(x, y, z, n, glow):
    return CLEAR if glow else strand(x, y, z, n)


# hat layer: which texels of the inflated hair shell are hair (the rest is see-through)
def hat_mask(x, y, z, n):
    if n == (0, 1, 0):
        return True
    if n == (0, -1, 0):
        return False
    if n == (0, 0, -1):  # fringe: uneven locks over the forehead, parted slightly to his right
        col = int(math.floor((x + 4.5) * D))
        tips = [27.0, 27.5, 28.0, 29.5, 28.5, 28.0, 29.0, 30.0, 30.5, 29.5, 28.5, 28.0, 28.5, 29.5, 29.0, 28.0, 27.5, 27.0]
        return y >= tips[min(len(tips) - 1, max(0, col))]
    if n == (0, 0, 1):  # back: covers the head and curls under
        col = int(math.floor((x + 4.5) * D))
        return y >= (24.0 if col % 3 else 24.5)
    # sides: over the ear, longer at the back, flicks out at the bottom
    front = -z
    edge = 26.5 if front > 2.5 else 25.5 if front > 0.5 else 24.5
    return y >= edge


def hat_paint(x, y, z, n, glow):
    if not hat_mask(x, y, z, n):
        return CLEAR
    if glow:
        return CLEAR
    c = strand(x, y, z, n)
    if n[1] == 0:
        if not hat_mask(x, y - 0.5, z, n):
            c = HAIR_SH  # tip of a lock
        elif not hat_mask(x, y - 1.0, z, n):
            c = mix(c, HAIR_SH, 0.5)
        if n == (0, 0, -1):
            col = int(math.floor((x + 4.5) * D))
            if col % 3 == 2 and y < 30.5:
                c = HAIR_DK if y < 29.5 else HAIR_SH  # gap between fringe locks
    return c


def tuft_paint(x, y, z, n, glow):
    if glow:
        return CLEAR
    c = strand(x, y, z, n)
    return shade(c, 0.93) if n == (0, -1, 0) else c


# ------------------------------------------------------------------ clothes
def lapel_half(y):
    """Half width of the open front of the coat (the shirt shows): wide at the collar, narrow at the waist."""
    return 0.75 + (y - 13.0) / 11.0 * 0.75


def torso_paint(x, y, z, n, glow):
    if n == (0, 1, 0):  # shoulders/top: collar ring
        if glow:
            return CLEAR
        return SHIRT if abs(x) < 1.75 and z < 1.0 else COAT_SH
    if n == (0, -1, 0):
        return CLEAR if glow else TROUS_SH
    if glow:
        return CLEAR
    if 12.0 <= y < 13.0:  # belt (only in the open front; the coat covers the rest)
        if n == (0, 0, -1) and abs(x) < lapel_half(13.0):
            return GOLD if abs(x) < 0.5 else hexc("3A3640")
    if y < 12.0:
        return TROUS
    if n == (0, 0, -1):
        h = lapel_half(y)
        if abs(x) < h:
            if y >= 23.0:
                return SHIRT_HI  # high collar
            if abs(x) < 0.25 and int(math.floor(y * D)) % 4 == 1:
                return GOLD  # studs
            return SHIRT
        if abs(x) < h + 0.5:
            return GOLD_HI if y >= 22.0 else GOLD
        if y >= 23.5:
            return COAT_SH
        c = COAT if abs(x) < 3.5 else COAT_SH
        if 15.0 <= y < 15.5 and abs(x) > 2.0:
            c = COAT_DK  # pocket flap seam
        return grain(c, x, y, z)
    if n == (0, 0, 1):
        if abs(x) < 0.25 and y < 17.0:
            return COAT_DK  # back vent seam
        if 22.5 <= y < 23.0:
            return COAT_SH  # yoke seam
        return grain(COAT if abs(x) < 3.5 else COAT_SH, x, y, z)
    # sides
    if abs(z) < 0.25:
        return COAT_SH
    return grain(COAT_SH if z > 1.5 else COAT, x, y, z)


def sleeve_paint(x, y, z, n, glow):
    """Slim arm in the coat: sleeve to the gold cuff, a charcoal shirt cuff, then the hand."""
    if y < 14.5:
        return hand_paint(x, y, z, n, glow)
    if glow:
        return CLEAR
    if n == (0, 1, 0):
        return COAT_SH
    if 14.5 <= y < 15.0:
        return SHIRT
    if 15.0 <= y < 15.75:
        return GOLD_HI if y >= 15.5 else GOLD
    if n[1] == -1:
        return SHIRT
    inner = n[0] != 0 and (n[0] > 0) == (x < 0)  # face towards the body
    c = COAT_SH if inner else COAT
    if 21.5 <= y < 22.0 and n[1] == 0:
        c = COAT_SH  # shoulder seam
    if 17.5 <= y < 18.0 and n == (0, 0, 1):
        c = COAT_SH  # elbow crease
    return grain(c, x, y, z)


def hand_paint(x, y, z, n, glow):
    if glow:
        return CLEAR
    c = SKIN
    if n[1] == -1 or y < 11.75:
        c = SKIN_SH
    if n == (0, 0, 1) or ((n[0] > 0) == (x < 0) and n[0] != 0):
        c = SKIN_SH if c is SKIN else SKIN_DK
    return c


def trousers_paint(x, y, z, n, glow):
    if y < 1.5 and n != (0, 1, 0):
        return shoe_paint(x, y, z, n, glow)
    if glow:
        return CLEAR
    if n == (0, 1, 0):
        return TROUS_SH
    inner = n[0] != 0 and ((n[0] > 0) == (x < 0))
    c = TROUS_SH if inner else TROUS
    if n == (0, 0, -1) and abs(abs(x) - 1.95) < 0.25 and y > 2.0:
        c = TROUS_SH  # front crease
    if n == (0, 0, -1) and 6.5 <= y < 7.0:
        c = TROUS_SH  # knee fold
    if 1.5 <= y < 2.0:
        c = TROUS_SH  # hem
    return grain(c, x, y, z, 0.03)


def shoe_paint(x, y, z, n, glow):
    if glow:
        return CLEAR
    if n == (0, -1, 0):
        return SOLE
    if y < 0.5 and n[1] == 0:
        return SOLE
    if n == (0, 0, 1) and 0.5 <= y < 1.0:
        return GOLD  # heel band
    if n == (0, 1, 0):
        return SHOE
    return shade(SHOE, 0.96) if n[0] != 0 else SHOE


def skirt_paint(x, y, z, n, glow, kind):
    """Coat skirt panels: white outside, cream lining inside, gold hem and front edges."""
    bottom = 5.0
    hem = y < bottom + 0.5
    edge = kind == "front" and abs(x) < 1.1 and n[0] == 0
    if glow:
        return CLEAR
    outward = (kind == "front" and n == (0, 0, -1)) or (kind == "back" and n == (0, 0, 1)) or \
              (n[0] != 0 and ((n[0] > 0) == (x > 0)))
    if n[1] != 0:
        return GOLD if n[1] < 0 else COAT_SH
    if not outward:
        return LINING
    if hem or edge:
        return GOLD_HI if hem and y >= bottom + 0.25 else GOLD
    if kind == "back" and abs(x) < 0.25:
        return COAT_DK  # vent
    c = COAT
    if y < 7.0:
        c = mix(COAT, COAT_SH, (7.0 - y) / 4.0)  # folds towards the hem
    if kind == "front" and int(math.floor(x * D)) % 5 == 0:
        c = shade(c, 0.97)
    return grain(c, x, y, z)


def lapel_paint(x, y, z, n, glow):
    if glow:
        return CLEAR
    if n == (0, 0, -1):
        if abs(x) < 1.75:
            return GOLD
        return grain(COAT, x, y, z)
    if n == (0, 0, 1):
        return LINING
    return COAT_SH


def skin_paint(x, y, z, n, glow):
    return CLEAR if glow else SKIN_SH


MATERIALS = {
    "head": head_paint, "hat": hat_paint, "tuft": tuft_paint, "torso": torso_paint, "sleeve": sleeve_paint,
    "hand": hand_paint, "trousers": trousers_paint, "shoe": shoe_paint, "lapel": lapel_paint, "neck": skin_paint,
    "skirt_front": lambda *a: skirt_paint(*a, "front"), "skirt_back": lambda *a: skirt_paint(*a, "back"),
}

# ------------------------------------------------------------------ geometry
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def face_corners(o, s, face):
    """TL, TR, BR, BL of a face as the engine maps uv (u0,v0)..(u1,v1) (see tools/preview_geo.py)."""
    x0, y0, z0 = o
    x1, y1, z1 = o[0] + s[0], o[1] + s[1], o[2] + s[2]
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)],
        "down": [(x1, y0, z0), (x0, y0, z0), (x0, y0, z1), (x1, y0, z1)],
    }[face]


def face_dims(s, face):
    w, h, d = s
    return {"north": (w, h), "south": (w, h), "west": (d, h), "east": (d, h), "up": (w, d), "down": (w, d)}[face]


class Model:
    def __init__(self, name):
        self.name = name
        self.bones = []
        self.by = {}

    def bone(self, name, parent, pivot, rotation=None):
        assert name not in self.by, name
        assert parent is None or parent in self.by, (name, parent)
        b = {"name": name, "parent": parent, "pivot": [round(v, 4) for v in pivot], "rotation": rotation, "cubes": []}
        self.bones.append(b)
        self.by[name] = b
        return b

    def cube(self, bone, origin, size, mat, inflate=0.0, faces=None):
        self.by[bone]["cubes"].append({"origin": list(origin), "size": list(size), "mat": mat, "inflate": inflate,
                                        "faces": faces or list(NORMALS)})


def mirror(o, s):
    return [-(o[0] + s[0]), o[1], o[2]]


def build():
    m = Model("regulus")
    m.bone("root", None, [0, 0, 0])
    m.bone("hips", "root", [0, 12.0, 0])
    m.cube("hips", [-4, 11.5, -2], [8, 1.5, 4], "torso")
    m.bone("torso", "hips", [0, 12.8, 0])
    m.cube("torso", [-4, 13.0, -2], [8, 3.5, 4], "torso")
    m.bone("chest", "torso", [0, 16.4, 0])
    m.cube("chest", [-4, 16.5, -2], [8, 7.5, 4], "torso")
    m.bone("collar", "chest", [0, 23.0, 0])
    for sgn in (-1, 1):  # coat lapels standing off the chest
        o, s = [1.25, 20.5, -2.5], [2.25, 3.5, 0.5]
        m.cube("collar", o if sgn > 0 else mirror(o, s), s, "lapel")
    m.bone("neck", "chest", [0, 22.5, 0])
    m.cube("neck", [-1.5, 22.5, -1.5], [3, 1.75, 3], "neck", faces=["north", "south", "west", "east"])
    m.bone("head", "neck", [0, 24.0, 0])
    m.cube("head", [-4, 24, -4], [8, 8, 8], "head")
    m.bone("hair", "head", [0, 29.0, 0])
    m.cube("hair", [-4, 24, -4], [8, 8, 8], "hat", inflate=0.5)
    # a little volume on top and the soft flicks at the sides
    for o, s in (([-3.0, 32.5, -2.5], [3.0, 0.5, 4.0]), ([0.5, 32.5, -1.5], [2.5, 0.5, 3.5]), ([-1.5, 32.5, 1.0], [3.5, 0.5, 2.5]),
                 ([-4.5, 25.5, 0.5], [0.5, 1.0, 3.0]), ([4.0, 25.0, 1.0], [0.5, 1.5, 2.5])):
        m.cube("hair", [o[0], o[1] + 0.5 * 0 , o[2]], s, "tuft")
    m.bone("hair_back", "hair", [0, 26.5, 4.0])
    m.cube("hair_back", [-2.5, 23.5, 3.75], [5.0, 1.5, 0.75], "tuft")

    for side in ("right", "left"):
        r = side == "right"
        sx = -1 if r else 1

        def P(x, y, z):
            return [x * (1 if r else -1), y, z]

        def C(bone, o, s, mat, **kw):
            m.cube(bone, o if r else mirror(o, s), s, mat, **kw)

        n = side
        m.bone(n + "_shoulder", "chest", P(-4.0, 22.5, 0))
        C(n + "_shoulder", [-7, 22, -2], [3, 2, 4], "sleeve")
        m.bone(n + "_arm", n + "_shoulder", P(-5.0, 22.0, 0))
        C(n + "_arm", [-7, 18, -2], [3, 4, 4], "sleeve", faces=["north", "south", "west", "east"])
        m.bone(n + "_forearm", n + "_arm", P(-5.5, 18.0, 0))
        C(n + "_forearm", [-7, 14.5, -2], [3, 3.5, 4], "sleeve", faces=["north", "south", "west", "east", "down"])
        m.bone(n + "_hand", n + "_forearm", P(-5.5, 14.5, 0))
        C(n + "_hand", [-6.75, 13.0, -1.75], [2.5, 1.5, 3.5], "hand", faces=["north", "south", "west", "east"])
        m.bone(n + "_index", n + "_hand", P(-5.5, 13.0, -1.0))
        C(n + "_index", [-6.0, 12.0, -1.75], [1.5, 1.0, 1.25], "hand", faces=["north", "south", "west", "east"])
        m.bone(n + "_index2", n + "_index", P(-5.5, 12.0, -1.0))
        C(n + "_index2", [-5.95, 11.25, -1.7], [1.4, 0.75, 1.15], "hand")
        m.bone(n + "_fingers", n + "_hand", P(-5.5, 13.0, 0.75))
        C(n + "_fingers", [-6.75, 12.0, -0.5], [2.5, 1.0, 2.25], "hand", faces=["north", "south", "west", "east"])
        m.bone(n + "_fingers2", n + "_fingers", P(-5.5, 12.0, 0.75))
        C(n + "_fingers2", [-6.7, 11.25, -0.45], [2.4, 0.75, 2.15], "hand")
        m.bone(n + "_thumb", n + "_hand", P(-4.75, 13.75, -1.75), [16.0, 0.0, -18.0 * (1 if r else -1)] if True else None)
        C(n + "_thumb", [-5.0, 13.0, -2.25], [0.75, 1.0, 0.75], "hand")
        m.bone(n + "_thumb2", n + "_thumb", P(-4.75, 13.0, -1.75))
        C(n + "_thumb2", [-4.95, 12.35, -2.2], [0.65, 0.65, 0.65], "hand")

        # legs (joints pinned by regulus_rig.py)
        m.bone(n + "_thigh", "hips", P(-1.85, 11.6, 0))
        C(n + "_thigh", [-3.9, 6.5, -2], [3.9, 5.5, 4], "trousers", faces=["north", "south", "west", "east"])
        m.bone(n + "_shin", n + "_thigh", P(-1.85, 6.6, 0))
        C(n + "_shin", [-3.9, 1.0, -2], [3.9, 5.5, 4], "trousers", faces=["north", "south", "west", "east"])
        m.bone(n + "_foot", n + "_shin", P(-1.85, 1.15, 0))
        C(n + "_foot", [-3.75, 0.0, -1.5], [3.6, 1.0, 3.4], "shoe")
        m.bone(n + "_toe", n + "_foot", P(-1.85, 0.0, -1.5))
        C(n + "_toe", [-3.7, 0.0, -3.0], [3.5, 0.9, 1.5], "shoe", faces=["north", "west", "east", "up", "down"])

    # coat skirt: open front panels wrapping the sides, the back hangs in two segments (spring bones)
    for side in ("right", "left"):
        r = side == "right"

        def C(bone, o, s, mat):
            m.cube(bone, o if r else mirror(o, s), s, mat)

        b = "coat_front_" + ("r" if r else "l")
        m.bone(b, "hips", [-1.9 if r else 1.9, 12.3, -2.2])
        C(b, [-4.4, 5.0, -2.5], [3.9, 7.5, 0.5], "skirt_front")
        C(b, [-4.4, 5.0, -2.0], [0.5, 7.5, 2.0], "skirt_front")
    m.bone("coat_back", "hips", [0, 12.3, 2.2])
    m.cube("coat_back", [-4.4, 8.5, 2.0], [8.8, 4.0, 0.5], "skirt_back")
    m.cube("coat_back", [-4.4, 8.5, 0.0], [0.5, 4.0, 2.0], "skirt_back")
    m.cube("coat_back", [3.9, 8.5, 0.0], [0.5, 4.0, 2.0], "skirt_back")
    m.bone("coat_back_lower", "coat_back", [0, 8.5, 2.3])
    m.cube("coat_back_lower", [-4.4, 5.0, 2.0], [8.8, 3.5, 0.5], "skirt_back")
    m.cube("coat_back_lower", [-4.4, 5.0, 0.0], [0.5, 3.5, 2.0], "skirt_back")
    m.cube("coat_back_lower", [3.9, 5.0, 0.0], [0.5, 3.5, 2.0], "skirt_back")

    # VFX locators
    m.bone("right_palm", "right_hand", [-5.5, 13.2, 0.0])
    m.bone("left_palm", "left_hand", [5.5, 13.2, 0.0])
    m.bone("heart", "chest", [1.25, 19.5, -2.0])
    return m


# ------------------------------------------------------------------ atlas + paint
BEVEL = {"torso", "sleeve", "trousers", "skirt_front", "skirt_back"}

def layout(model):
    """Shelf-pack every face of every cube; returns [(cube, face, (u, v, w, h))]."""
    rects = []
    for b in model.bones:
        for c in b["cubes"]:
            for f in c["faces"]:
                fw, fh = face_dims(c["size"], f)
                w, h = max(1, int(round(fw * D))), max(1, int(round(fh * D)))
                rects.append((c, f, w, h))
    order = sorted(range(len(rects)), key=lambda i: (-rects[i][3], -rects[i][2], i))
    placed = [None] * len(rects)
    x = y = row_h = 0
    for i in order:
        c, f, w, h = rects[i]
        if x + w > ATLAS:
            x, y, row_h = 0, y + row_h, 0
        if y + h > ATLAS:
            raise SystemExit("atlas full")
        placed[i] = (c, f, (x, y, w, h))
        x += w
        row_h = max(row_h, h)
    return placed, y + row_h


def paint(placed):
    tex = Image.new("RGBA", (ATLAS, ATLAS), CLEAR)
    glow = Image.new("RGBA", (ATLAS, ATLAS), CLEAR)
    tp, gp = tex.load(), glow.load()
    for c, f, (u, v, w, h) in placed:
        tl, tr, br, bl = face_corners(c["origin"], c["size"], f)
        mat = c["mat"]
        fn = MATERIALS[mat]
        n = NORMALS[f]
        for j in range(h):
            t = (j + 0.5) / h
            for i in range(w):
                s = (i + 0.5) / w
                p = tuple(tl[k] * (1 - s) * (1 - t) + tr[k] * s * (1 - t) + br[k] * s * t + bl[k] * (1 - s) * t for k in range(3))
                col = fn(*p, n, False)
                if col[3] and n[1] == 0 and (i == 0 or i == w - 1) and mat in BEVEL:
                    col = shade(col, 0.94)  # soft vertical edge: reads as volume like a hand-drawn skin
                tp[u + i, v + j] = col
                g = fn(*p, n, True)
                if g[3]:
                    gp[u + i, v + j] = g
    return tex, glow


def to_json(model, placed):
    uv = {}
    for c, f, r in placed:
        uv.setdefault(id(c), {})[f] = r
    bones = []
    for b in model.bones:
        out = {"name": b["name"], "pivot": b["pivot"]}
        if b["parent"]:
            out["parent"] = b["parent"]
        if b["rotation"] and any(abs(v) > 1e-6 for v in b["rotation"]):
            out["rotation"] = [round(v, 3) for v in b["rotation"]]
        cubes = []
        for c in b["cubes"]:
            faces = {}
            for f, (u, v, w, h) in uv[id(c)].items():
                faces[f] = {"uv": [u, v], "uv_size": [w, h]}
            cube = {"origin": [round(v, 4) for v in c["origin"]], "size": [round(v, 4) for v in c["size"]], "uv": faces}
            if c["inflate"]:
                cube["inflate"] = c["inflate"]
            cubes.append(cube)
        if cubes:
            out["cubes"] = cubes
        bones.append(out)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.regulus", "texture_width": ATLAS, "texture_height": ATLAS,
                        "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}


def b64png(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return base64.b64encode(buf.getvalue()).decode("ascii") + "\n"


def outputs():
    model = build()
    placed, used = layout(model)
    tex, glow = paint(placed)
    body = json.dumps(to_json(model, placed), separators=(",", ":")) + "\n"
    files = {
        os.path.join(GEO, "regulus.geo.json"): body,
        os.path.join(TEX, "regulus.png.b64"): b64png(tex),
        os.path.join(TEX, "regulus_glow.png.b64"): b64png(glow),
    }
    return files, (tex, glow), model, used


def frozen_ok():
    bad = []
    for path, digest in FROZEN.items():
        if not os.path.exists(path) or hashlib.sha256(open(path, "rb").read()).hexdigest() != digest:
            bad.append(os.path.relpath(path, ROOT))
    return bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="fail when the committed files differ")
    args = ap.parse_args()
    files, (tex, glow), model, used = outputs()
    bad = frozen_ok()
    if bad:
        print("first-person arms changed (they are frozen):", *bad, sep="\n  ")
        sys.exit(1)
    if args.check:
        stale = [os.path.relpath(p, ROOT) for p, t in files.items() if not os.path.exists(p) or open(p, encoding="utf-8").read() != t]
        if stale:
            print("stale:", *stale, sep="\n  ")
            sys.exit(1)
        print("regulus model up to date")
        return
    for path, text in files.items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)
    os.makedirs(PREVIEW, exist_ok=True)
    tex.save(os.path.join(PREVIEW, "regulus.png"))
    glow.save(os.path.join(PREVIEW, "regulus_glow.png"))
    print("bones:", len(model.bones), "cubes:", sum(len(b["cubes"]) for b in model.bones), "atlas rows used:", used)


if __name__ == "__main__":
    main()
