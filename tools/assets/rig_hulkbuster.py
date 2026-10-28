#!/usr/bin/env python3
"""Hulkbuster Mark 48 rig (rework spec 2026-10-28 §2, plan H1).

Reads the unchanged Sind conversion in tools/assets/hulkbuster_src/ and writes:
- geo/ironman/hulkbuster/mark48.geo.json      one rigged body: semantic joints, the
  Sind jackhammer arm mirrored onto the RIGHT side (left hand grabs, right fist pounds),
  the left hand split into palm / fingers / thumb, thruster flames as flame_* bones;
- geo/ironman/hulkbuster/mark48_fp.geo.json   the two arm chains on the vanilla arm pivots;
- textures/entity/ironman/hulkbuster.png      one atlas: body 214 px + jackhammer 114 px;
- textures/entity/ironman/hulkbuster_glow.png the emissive mask, built ONLY from the
  emitting parts (visor, reactor, thruster soles, palm, piston glow). The Sind glow had six
  lit pixels in the UV of the upper chest plates (cube_17 / cube_21): RenderType.eyes drew
  them as two bright squares on the chest (playtest 2026-10-28). They are no longer painted.

Model space: Sind units (68 px tall), Bedrock axes (front = -Z, x to the model's left).
Static plates keep their own rest rotation in child bones; joints carry no rest rotation
unless the Sind joint had one, so clip keys are plain deltas. Run from the repo root:
    python3 tools/assets/rig_hulkbuster.py [--report]
"""
import base64
import copy
import io
import json
import math
import os
import sys

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(REPO, "tools", "assets", "hulkbuster_src")
RES = os.path.join(REPO, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore")
BIN = os.path.join(REPO, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore")
sys.path.insert(0, os.path.join(REPO, "tools"))
import preview_geo as pg  # noqa: E402  (same transforms as BakedGeoModel)

BODY_W, JACK_W, ATLAS_H = 214, 114, 214
ATLAS_W = BODY_W + JACK_W
FIRE_PX = 64
SIND_UNITS = 32.0 / 68.0
FP_SCALE = 1.7 * SIND_UNITS
VANILLA_ARM = {"shoulder_r": [-5, 22, 0], "shoulder_l": [5, 22, 0]}


# ---------- texture faces exactly as BakedGeoModel reads them ----------
FACES = ("north", "south", "west", "east", "up", "down")


def java_rects(cube):
    """Face rects [r0, r1, r2, r3] in pixels after the box/per-face path, swap and mirror (BakedGeoModel.buildCube)."""
    sx, sy, sz = cube["size"]
    uv = cube.get("uv", [0, 0])
    if isinstance(uv, dict):
        def face(name):
            f = uv.get(name)
            if not f:
                return None
            u, v = f["uv"]
            su, sv = f.get("uv_size", [0, 0])
            return [u, v, u + su, v + sv]
        r = {"north": face("north"), "south": face("south"), "up": face("up"), "down": face("down"),
             "east": face("west"), "west": face("east")}
    else:
        u, v = uv
        r = {
            "down": [u + sz, v, u + sz + sx, v + sz],
            "up": [u + sz + sx, v + sz, u + sz + sx + sx, v],
            "north": [u + sz, v + sz, u + sz + sx, v + sz + sy],
            "south": [u + sz + sx + sz, v + sz, u + sz + sx + sz + sx, v + sz + sy],
            "east": [u, v + sz, u + sz, v + sz + sy],
            "west": [u + sz + sx, v + sz, u + sz + sx + sz, v + sz + sy],
        }
    if cube.get("mirror"):
        r = {k: (None if x is None else [x[2], x[1], x[0], x[3]]) for k, x in r.items()}
        r["east"], r["west"] = r["west"], r["east"]
    return r


def emit(cube, rects):
    """Cube with explicit per-face UV reproducing these java rects (mirror off)."""
    out = {k: v for k, v in cube.items() if k not in ("uv", "mirror")}
    js = {}
    for name in FACES:
        r = rects.get(name)
        if r is None:
            continue
        key = {"east": "west", "west": "east"}.get(name, name)
        js[key] = {"uv": [round(r[0], 4), round(r[1], 4)], "uv_size": [round(r[2] - r[0], 4), round(r[3] - r[1], 4)]}
    out["uv"] = js
    return out


def java_box(cube):
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    return -(ox + sx), -ox, oy, oy + sy, oz, oz + sz


def quad_corners(face, b):
    """(a, b, d) java positions of a face (BakedGeoModel.addQuad order)."""
    x1, x2, y1, y2, z1, z2 = b
    return {
        "north": ((x1, y2, z1), (x2, y2, z1), (x1, y1, z1)),
        "south": ((x2, y2, z2), (x1, y2, z2), (x2, y1, z2)),
        "west": ((x1, y2, z2), (x1, y2, z1), (x1, y1, z2)),
        "east": ((x2, y2, z1), (x2, y2, z2), (x2, y1, z1)),
        "up": ((x1, y2, z2), (x2, y2, z2), (x1, y2, z1)),
        "down": ((x1, y1, z1), (x2, y1, z1), (x1, y1, z2)),
    }[face]


def face_map(face, box, rect):
    """Affine uv(p) of one face: a -> (r2, r1), b -> (r0, r1), d -> (r2, r3) (FLIP_U)."""
    a, b, d = quad_corners(face, box)
    ab = [b[i] - a[i] for i in range(3)]
    ad = [d[i] - a[i] for i in range(3)]
    lab = sum(c * c for c in ab) or 1.0
    lad = sum(c * c for c in ad) or 1.0

    def uv(p):
        rel = [p[i] - a[i] for i in range(3)]
        s = sum(rel[i] * ab[i] for i in range(3)) / lab
        t = sum(rel[i] * ad[i] for i in range(3)) / lad
        return rect[2] + s * (rect[0] - rect[2]), rect[1] + t * (rect[3] - rect[1])
    return uv


OPPOSITE_PLANE = {"x": ("west", "east"), "y": ("down", "up"), "z": ("north", "south")}


def split(cube, axis, at):
    """Two cubes cut at Bedrock coordinate ``at`` along axis; every face keeps its texels."""
    i = "xyz".index(axis)
    lo = copy.deepcopy(cube)
    hi = copy.deepcopy(cube)
    o, s = cube["origin"][i], cube["size"][i]
    assert o < at < o + s, (axis, at, o, s)
    lo["size"][i] = at - o
    hi["origin"][i] = at
    hi["size"][i] = o + s - at
    parent = java_rects(cube)
    pbox = java_box(cube)
    maps = {f: face_map(f, pbox, parent[f]) for f in FACES if parent.get(f)}
    out = []
    for piece in (lo, hi):
        box = java_box(piece)
        rects = {}
        for f in FACES:
            m = maps.get(f)
            if m is None:
                continue
            a, b, d = quad_corners(f, box)
            ua, va = m(a)
            ub, _ = m(b)
            _, vd = m(d)
            rects[f] = [ub, va, ua, vd]
        out.append(emit(piece, rects))
    return out


def mirror_cube(cube):
    c = copy.deepcopy(cube)
    rects = java_rects(c)
    ox = c["origin"][0]
    c["origin"][0] = -(ox + c["size"][0])
    if "pivot" in c:
        c["pivot"][0] = -c["pivot"][0]
    if "rotation" in c:
        rx, ry, rz = c["rotation"]
        c["rotation"] = [rx, -ry, -rz]
    mr = {k: (None if r is None else [r[2], r[1], r[0], r[3]]) for k, r in rects.items()}
    mr["east"], mr["west"] = mr["west"], mr["east"]
    return emit(c, mr)


def shifted(cube, du=0.0, ku=1.0, kv=1.0):
    rects = java_rects(cube)
    return emit(cube, {k: (None if r is None else [r[0] * ku + du, r[1] * kv, r[2] * ku + du, r[3] * kv]) for k, r in rects.items()})


# ---------- skeleton helpers ----------
def load(name):
    with open(os.path.join(SRC, name), encoding="utf-8") as f:
        return json.load(f)["minecraft:geometry"][0]["bones"]


def by_name(bones):
    return {b["name"]: b for b in bones}


def world(bones, name, p):
    """Rest model-space position of point p given in bone ``name``'s own space."""
    table = by_name(bones)
    chain = []
    n = name
    while n in table:
        chain.append(table[n])
        n = table[n].get("parent")
    m = pg.IDENTITY
    for b in reversed(chain):
        m = pg.mat_mul(m, pg.rotation(b.get("pivot", [0, 0, 0]), b.get("rotation")))
    return list(pg.apply(m, p))


def cube_point(bones, bone, cube, fx, fy, fz):
    """Rest model-space point at fractions of a cube's box (cube rotation included)."""
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    p = (ox + fx * sx, oy + fy * sy, oz + fz * sz)
    if cube.get("rotation"):
        p = pg.apply(pg.rotation(cube.get("pivot", [0, 0, 0]), cube["rotation"]), p)
    return world(bones, bone, p)


def bone(name, parent, pivot, rotation=None, cubes=None):
    b = {"name": name, "pivot": [round(v, 4) for v in pivot]}
    if parent:
        b["parent"] = parent
    if rotation and any(rotation):
        b["rotation"] = rotation
    if cubes:
        b["cubes"] = cubes
    return b


def centre_y(src, b):
    ys = []
    for c in b.get("cubes", []):
        for fy in (0, 1):
            ys.append(cube_point(src, b["name"], c, 0.5, fy, 0.5)[1])
    return sum(ys) / len(ys) if ys else None


def centre(src, b):
    pts = [cube_point(src, b["name"], c, fx, fy, fz) for c in b.get("cubes", []) for fx in (0, 1) for fy in (0, 1) for fz in (0, 1)]
    return [sum(p[i] for p in pts) / len(pts) for i in range(3)] if pts else None


# ---------- the rig ----------
def build():
    src = load("mark48_sind.geo.json")
    jack = load("jackhammer_sind.geo.json")
    fire = load("fire_sind.geo.json")
    jfire = load("jackhammer_fire_sind.geo.json")
    S = by_name(src)
    out = []
    used = set()

    def take(name, parent, rename=None, cubes=None):
        b = copy.deepcopy(S[name])
        used.add(name)
        nb = bone(rename or name, parent, b["pivot"], b.get("rotation"), cubes if cubes is not None else b.get("cubes"))
        out.append(nb)
        return nb

    def take_tree(name, parent, prefix):
        """A Sind subtree with its rest rotations; bones renamed prefix + index for uniqueness."""
        kids = {}
        for b in src:
            kids.setdefault(b.get("parent"), []).append(b["name"])
        count = [0]

        def walk(n, p):
            count[0] += 1
            new = take(n, p, rename=f"{prefix}{count[0]}")
            for k in kids.get(n, []):
                walk(k, new["name"])
        walk(name, parent)

    # Root at the feet, pelvis at the hips, waist, chest.
    out.append(bone("root", None, [0, 0, 0]))
    out.append(bone("pelvis", "root", [0, 37.0, 3.0]))
    out.append(bone("torso", "pelvis", [0, 43.0, 3.0]))
    out.append(bone("chest", "torso", [0, 51.0, 3.0]))
    used.update({"bone", "bodyBuster"})

    # Body plates of bodyBuster (Sind keeps each rotated cube as its own bone) sorted by height.
    back_names = []
    for b in src:
        if b.get("parent") != "bodyBuster" or not b["name"].startswith("cube"):
            continue
        cy = centre_y(src, b)
        cz = centre(src, b)[2]
        if cy < 42.0:
            parent = "pelvis"
        elif cy < 50.0:
            parent = "torso"
        elif cz > 8.5:
            parent = "back_flap"
            back_names.append(b["name"])
        else:
            parent = "chest"
        take(b["name"], parent, rename=parent + "_" + b["name"].replace("cube_", "p"))

    # Back flap hinges at the top of the back (opens up for entry and exit, §4).
    out.append(bone("back_flap", "chest", [0, 64.5, 10.0]))
    # Back thrusters (Sind backpack), chest flaps (doors hinged at the outer edge), reactor, shoulder caps.
    out.append(bone("back_thrusters", "chest", S["backpack"]["pivot"]))
    used.add("backpack")
    for k in [n for n in S if S[n].get("parent") == "backpack"]:
        take(k, "back_thrusters", rename="back_thrusters_" + k.replace("cube_", "p"))
    for side, name in (("l", "chestLeft"), ("r", "chestRight")):
        out.append(bone("chest_flap_" + side, "chest", S[name]["pivot"]))
        used.add(name)
        for k in [n for n in S if S[n].get("parent") == name]:
            take(k, "chest_flap_" + side, rename=f"chest_flap_{side}_" + k.replace("cube_", "p"))
    out.append(bone("reactor_housing", "chest", S["centreChest"]["pivot"]))
    used.add("centreChest")
    for k in [n for n in S if S[n].get("parent") == "centreChest"]:
        take(k, "reactor_housing", rename="reactor" if k == "reactor" else "reactor_housing_" + k.replace("cube_", "p"))
    for side, name in (("l", "shoulderLeft"), ("r", "shoulderRight")):
        out.append(bone("shoulder_cap_" + side, "chest", S[name]["pivot"]))
        used.add(name)
        for k in [n for n in S if S[n].get("parent") == name]:
            take(k, "shoulder_cap_" + side, rename=f"shoulder_cap_{side}_" + k.replace("cube_", "p"))

    # Head: Sind rest tilt 15 deg kept on the joint; visor and side plates are their own bones.
    head = take("headBuster", "chest", rename="head")
    take("facePlate", "head", rename="visor")
    take("cube", "visor", rename="visor_p0")
    take("cube_1", "visor", rename="visor_p1")
    take("leftPlate", "head", rename="helmet_l")
    take("cube_2", "helmet_l", rename="helmet_l_p2")
    take("rightPlate", "head", rename="helmet_r")
    take("cube_3", "head", rename="head_p3")
    take("cube_4", "head", rename="head_p4")
    assert head

    # Legs: hip joint (animated) -> Sind rest shell -> knee -> ankle -> sole thruster.
    for side, sname in (("r", "Right"), ("l", "Left")):
        hip = S[f"{side == 'r' and 'right' or 'left'}LegBuster"]
        used.add(hip["name"])
        out.append(bone("thigh_" + side, "pelvis", hip["pivot"]))
        take(f"{side == 'r' and 'right' or 'left'}LegBusterFake", "thigh_" + side, rename=f"thigh_{side}_shell")
        for k in [n for n in S if S[n].get("parent") == f"{side == 'r' and 'right' or 'left'}LegBusterFake" and n.startswith(("cube", "l_cube"))]:
            take(k, f"thigh_{side}_shell", rename=f"thigh_{side}_" + k.replace("cube_", "p"))
        take(f"lower{sname}Leg", f"thigh_{side}_shell", rename="shin_" + side)
        for k in [n for n in S if S[n].get("parent") == f"lower{sname}Leg" and n.startswith("cube")]:
            take(k, "shin_" + side, rename=f"shin_{side}_" + k.replace("cube_", "p"))
        take(f"foot{sname}", "shin_" + side, rename="foot_" + side)
        for k in [n for n in S if S[n].get("parent") == f"foot{sname}" and n.startswith("cube")]:
            take(k, "foot_" + side, rename=f"foot_{side}_" + k.replace("cube_", "p"))
        take(f"repulsorFoot{sname}", "foot_" + side, rename="foot_thruster_" + side)
        for k in [n for n in S if S[n].get("parent") == f"repulsorFoot{sname}"]:
            take(k, "foot_thruster_" + side, rename=f"foot_thruster_{side}_p")

    # Left arm (normal Sind hand: grabs). shoulder joint -> upper arm -> elbow -> wrist -> fingers / thumb.
    used.update({"pivotLeft", "leftArmBuster", "lowerLeftArm", "repulsorFistLeft", "armLeft", "shoulderPadLeft"})
    out.append(bone("shoulder_l", "chest", S["pivotLeft"]["pivot"]))
    out.append(bone("upper_arm_l", "shoulder_l", S["leftArmBuster"]["pivot"]))
    for k in ("l_cube", "l_cube_1", "l_cube_3"):
        take(k, "upper_arm_l", rename="upper_arm_l_" + k.replace("l_cube", "p").replace("p_", "p"))
    out.append(bone("shoulder_pad_l", "upper_arm_l", S["shoulderPadLeft"]["pivot"]))
    take("l_cube_2", "shoulder_pad_l", rename="shoulder_pad_l_p")
    fore = S["l_cube_4"]
    fore_cube = fore["cubes"][0]
    shell_cube = S["l_cube_5"]["cubes"][0]
    elbow = cube_point(src, "l_cube_4", fore_cube, 0.5, 1.0, 0.5)
    elbow[1] -= 1.5
    wrist_y = fore_cube["origin"][1] + 7.0
    knuckle_y = fore_cube["origin"][1] + 4.0
    wrist = cube_point(src, "l_cube_4", dict(fore_cube, origin=[fore_cube["origin"][0], wrist_y, fore_cube["origin"][2]], size=[fore_cube["size"][0], 0.0001, fore_cube["size"][2]]), 0.5, 0, 0.5)
    knuckle = cube_point(src, "l_cube_4", dict(fore_cube, origin=[fore_cube["origin"][0], knuckle_y, fore_cube["origin"][2]], size=[fore_cube["size"][0], 0.0001, fore_cube["size"][2]]), 0.15, 0, 0.5)
    out.append(bone("forearm_l", "upper_arm_l", elbow))
    rest_l = dict(pivot=fore["pivot"], rotation=fore["rotation"])
    upper, lower = split(fore_cube, "y", wrist_y)[::-1]
    shell_upper, shell_lower = split(shell_cube, "y", wrist_y)[::-1]
    out.append(bone("forearm_l_shell", "forearm_l", rest_l["pivot"], rest_l["rotation"], [upper, shell_upper]))
    for k in ("l_cube_6", "l_cube_7"):
        take(k, "forearm_l", rename="forearm_l_" + k.replace("l_cube_", "p"))
    out.append(bone("hand_l", "forearm_l", wrist))
    palm_top, fingers = split(lower, "y", knuckle_y)[::-1]
    shell_palm, shell_fingers = split(shell_lower, "y", knuckle_y)[::-1]
    out.append(bone("hand_l_shell", "hand_l", rest_l["pivot"], rest_l["rotation"], [palm_top, shell_palm]))
    # Thumb: the front 3 px of the finger slab (palm faces the thigh, the thumb leads).
    thumb_z = fingers["origin"][2] + 3.0
    thumb, digits = split(fingers, "z", thumb_z)
    shell_thumb, shell_digits = split(shell_fingers, "z", thumb_z)
    out.append(bone("fingers_l", "hand_l", knuckle))
    out.append(bone("fingers_l_shell", "fingers_l", rest_l["pivot"], rest_l["rotation"], [digits, shell_digits]))
    thumb_root = cube_point(src, "l_cube_4", dict(fore_cube, origin=[fore_cube["origin"][0], knuckle_y, fore_cube["origin"][2]], size=[fore_cube["size"][0], 0.0001, 3.0]), 0.5, 0, 0.5)
    out.append(bone("thumb_l", "hand_l", thumb_root))
    out.append(bone("thumb_l_shell", "thumb_l", rest_l["pivot"], rest_l["rotation"], [thumb, shell_thumb]))
    used.update({"l_cube_4", "l_cube_5"})

    # Right arm: the Sind jackhammer arm, mirrored. Piston = the rods, rings and the fist that fire forward.
    J = by_name(jack)

    def jcubes(name):
        return [shifted(mirror_cube(c), du=BODY_W) for c in J[name].get("cubes", [])]

    def jpivot(name):
        p = list(J[name]["pivot"])
        p[0] = -p[0]
        return p

    def jrot(name):
        r = J[name].get("rotation")
        return None if not r else [r[0], -r[1], -r[2]]

    out.append(bone("shoulder_r", "chest", jpivot("pivotLeft")))
    out.append(bone("upper_arm_r", "shoulder_r", jpivot("leftArmBuster")))
    for k in ("cube", "cube_1", "cube_2", "cube_3", "cube_4", "cube_5", "cube_8"):
        out.append(bone("upper_arm_r_" + k.replace("cube_", "p").replace("cube", "p0"), "upper_arm_r", jpivot(k), jrot(k), jcubes(k)))
    out.append(bone("shoulder_pad_r", "upper_arm_r", jpivot("shoulderPadLeft")))
    for k in ("cube_6", "cube_7"):
        out.append(bone("shoulder_pad_r_" + k.replace("cube_", "p"), "shoulder_pad_r", jpivot(k), jrot(k), jcubes(k)))
    jelbow = [-v if i == 0 else v for i, v in enumerate(world(jack, "repulsorFistLeft", (22.65, 43.52, 6.45)))]
    jelbow[1] -= 0.5
    out.append(bone("forearm_r", "upper_arm_r", jelbow))
    out.append(bone("forearm_r_bars", "forearm_r", jpivot("upperBars"), jrot("upperBars"), jcubes("upperBars")))
    out.append(bone("forearm_r_shell", "forearm_r", jpivot("repulsorFistLeft"), jrot("repulsorFistLeft"), jcubes("repulsorFistLeft")))
    out.append(bone("piston_r", "forearm_r_shell", jpivot("foreLeftArm")))
    out.append(bone("piston_r_ring", "piston_r", jpivot("foreLeftLower"), None, jcubes("foreLeftLower")))
    out.append(bone("piston_r_rods", "piston_r", jpivot("bars"), None, jcubes("bars") + jcubes("cube_9")))
    out.append(bone("wrist_r", "piston_r", jpivot("wrist"), None, jcubes("wrist")))
    out.append(bone("hand_r", "wrist_r", jpivot("wholeHandLeft")))
    out.append(bone("hand_r_ring", "hand_r", jpivot("innerRingLeft"), None, jcubes("innerRingLeft")))
    out.append(bone("fist_r", "hand_r_ring", jpivot("handLeft"), None, jcubes("cube_10") + jcubes("cube_11")))

    # Thruster flames (repulsor fire texture, drawn in their own pass; hidden in the armour pass).
    F = by_name(fire)
    JF = by_name(jfire)
    ku, kv = ATLAS_W / FIRE_PX, ATLAS_H / FIRE_PX

    def fcubes(table, name, mirror=False):
        cubes = []
        for c in table[name].get("cubes", []):
            c = mirror_cube(c) if mirror else copy.deepcopy(c)
            cubes.append(shifted(c, ku=ku, kv=kv))
        return cubes

    out.append(bone("flame_hand_l", "hand_l", F["repulsorArmLeft"]["pivot"], F["repulsorArmLeft"].get("rotation")))
    for k in ("l_cube", "l_cube_1", "l_cube_2"):
        b = F[k]
        out.append(bone("flame_hand_l_" + k.replace("l_cube", "p").replace("p_", "p"), "flame_hand_l", b["pivot"], b.get("rotation"), fcubes(F, k)))
    for k in ("cube", "cube_1", "cube_2"):
        b = JF[k]
        p = list(b["pivot"])
        p[0] = -p[0]
        r = b.get("rotation")
        out.append(bone("flame_fist_r_" + k.replace("cube_", "p").replace("cube", "p0"), "fist_r", p, [r[0], -r[1], -r[2]] if r else None, fcubes(JF, k, mirror=True)))
    for side, leg, names in (("r", "repulsorLegRight", ("cube_3", "cube_4", "cube_5")), ("l", "repulsorLegLeft", ("cube_6", "cube_7", "cube_8"))):
        out.append(bone("flame_foot_" + side, "foot_thruster_" + side, F[leg]["pivot"], F[leg].get("rotation")))
        for k in names:
            b = F[k]
            out.append(bone(f"flame_foot_{side}_" + k.replace("cube_", "p"), "flame_foot_" + side, b["pivot"], b.get("rotation"), fcubes(F, k)))

    # Everything of the Sind body must be placed (right arm replaced by the jackhammer arm).
    right = {"pivotRight", "rightArmBuster", "cube_90", "cube_91", "shoulderPadRight", "cube_92", "cube_93", "lowerRightArm",
             "repulsorFistRight", "cube_94", "cube_95", "armRight", "cube_96", "cube_97"}
    missing = [b["name"] for b in src if b["name"] not in used and b["name"] not in right and b.get("cubes")]
    assert not missing, missing
    return out, wrist, knuckle, elbow


def flame(name):
    return name.startswith("flame_")


EMISSIVE = {
    # bone prefix -> faces that glow (None = every face)
    "visor_p0": ("north",),
    "reactor": None,
    "foot_thruster_": ("down",),
    "hand_l_shell": ("north",),
    "fingers_l_shell": ("north",),
    "thumb_l_shell": ("north",),
    "flame_": None,
}


def glow_mask(bones, glow):
    """Keep the Sind glow only inside the UV of emitting faces; everything else black."""
    keep = Image.new("RGBA", glow.size, (0, 0, 0, 0))
    allowed = set()
    blocked = set()
    for b in bones:
        rule = None
        for prefix, faces in EMISSIVE.items():
            if b["name"] == prefix or b["name"].startswith(prefix) and prefix.endswith("_"):
                rule = (prefix, faces)
        for c in b.get("cubes", []):
            for face, r in java_rects(c).items():
                if r is None:
                    continue
                u0, u1 = sorted((r[0], r[2]))
                v0, v1 = sorted((r[1], r[3]))
                cells = {(x, y) for x in range(int(math.floor(u0)), int(math.ceil(u1))) for y in range(int(math.floor(v0)), int(math.ceil(v1)))}
                if rule is not None and (rule[1] is None or face in rule[1]):
                    allowed |= cells
                else:
                    blocked |= cells
    px = glow.load()
    out = keep.load()
    shared = 0
    for (x, y) in allowed:
        if 0 <= x < glow.size[0] and 0 <= y < glow.size[1]:
            out[x, y] = px[x, y]
            if (x, y) in blocked and px[x, y][3] and sum(px[x, y][:3]):
                shared += 1
    return keep, shared


def write_geo(rel, bones, tw, th, ident):
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": ident, "texture_width": tw, "texture_height": th},
        "bones": bones}]}
    path = os.path.join(RES, rel)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=1)
        f.write("\n")


def b64(name):
    with open(os.path.join(SRC, name), encoding="ascii") as f:
        return Image.open(io.BytesIO(base64.b64decode(f.read()))).convert("RGBA")


def write_png(rel, image):
    buf = io.BytesIO()
    image.save(buf, format="PNG", optimize=True)
    path = os.path.join(BIN, rel + ".b64")
    with open(path, "w", encoding="ascii") as f:
        f.write(base64.b64encode(buf.getvalue()).decode("ascii"))
        f.write("\n")


def fp_arms(bones):
    """The two arm chains on the vanilla arm pivots (PlayerGeoLayer.renderArmPart), uniform scale."""
    kids = {}
    for b in bones:
        kids.setdefault(b.get("parent"), []).append(b["name"])
    table = by_name(bones)
    fp = []
    for root, target in VANILLA_ARM.items():
        anchor = table[root]["pivot"]
        names, todo = [], [root]
        while todo:
            n = todo.pop()
            names.append(n)
            todo.extend(kids.get(n, []))

        def at(p):
            return [round((p[i] - anchor[i]) * FP_SCALE + target[i], 4) for i in range(3)]
        for n in names:
            b = copy.deepcopy(table[n])
            b["pivot"] = at(b["pivot"])
            if n == root:
                b.pop("parent", None)
            cubes = []
            for c in b.get("cubes", []):
                c = emit(c, java_rects(c))
                c["origin"] = at(c["origin"])
                c["size"] = [round(x * FP_SCALE, 4) for x in c["size"]]
                if c.get("inflate"):
                    c["inflate"] = round(c["inflate"] * FP_SCALE, 4)
                if c.get("pivot"):
                    c["pivot"] = at(c["pivot"])
                cubes.append(c)
            if cubes:
                b["cubes"] = cubes
            fp.append(b)
    return fp


def main():
    report = "--report" in sys.argv
    bones, wrist, knuckle, elbow = build()
    write_geo("geo/ironman/hulkbuster/mark48.geo.json", bones, ATLAS_W, ATLAS_H, "geometry.viltrumitecore.mark48")
    write_geo("geo/ironman/hulkbuster/mark48_fp.geo.json", fp_arms(bones), ATLAS_W, ATLAS_H, "geometry.viltrumitecore.mark48_fp")
    atlas = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    atlas.paste(b64("hulkbuster_sind.png.b64"), (0, 0))
    atlas.paste(b64("hulkbuster_jackhammer_sind.png.b64"), (BODY_W, 0))
    write_png("textures/entity/ironman/hulkbuster.png", atlas)
    glow = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    glow.paste(b64("hulkbuster_glow_sind.png.b64"), (0, 0))
    glow.paste(b64("hulkbuster_jackhammer_glow_sind.png.b64"), (BODY_W, 0))
    mask, shared = glow_mask([b for b in bones if not flame(b["name"])], glow)
    write_png("textures/entity/ironman/hulkbuster_glow.png", mask)
    if report:
        print("bones", len(bones), "elbow_l", [round(v, 2) for v in elbow], "wrist_l", [round(v, 2) for v in wrist],
              "knuckle_l", [round(v, 2) for v in knuckle], "glow texels shared with plain faces", shared)


if __name__ == "__main__":
    main()
