#!/usr/bin/env python3
"""Thor rework R1: Thor's own geo body, camera arms, body atlas and base clips.

Writes (all generated, re-run after editing the numbers here):
  geo/thor/thor.geo.json         third-person body, 1 px = 1/16 block, rendered at scale 1
                                 (no PLAYER_SCALE): ~51 px = 3.2 blocks. Front -Z, right -X.
  geo/thor/thor_arms.geo.json    first-person arms on the vanilla arm pivots (armorRightArm /
                                 armorLeftArm > *_forearm > *_hand > grip locators).
  textures/entity/thor/thor_body.png(.b64)   128x64 atlas: skin 64x64 at (0,0), beard 16x16 at
                                 (64,0), painted beard fill at (64,16), belly 32x32 at (80,0).
  animations/thor/thor.animation.json        R1 base clips (crouch, sit, lift_squat).

Sources (decoded binassets): textures/entity/hero/thor.png (64x64 wide-arm skin),
textures/entity/thor/beard.png, textures/entity/thor/belly.png. The skin is sliced
per segment (upper arm / forearm / fist, thigh / shin / boot, chest / waist) with
per-face UVs that reproduce BakedGeoModel's own box-UV mapping.

Run: python3 tools/assets/make_thor_body.py [--decoded DIR]
"""
import argparse
import base64
import io
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore")
BIN = os.path.join(ROOT, "viltrumitecore/src/main/binassets/assets/viltrumitecore")
TEX_W, TEX_H = 128, 64
BEARD_AT = (64, 0)
BEARD_FILL = (64, 16)
BELLY_AT = (80, 0)

# Skin boxes (u, v, w, h, d): standard 64x64 layout, wide arms.
SKIN = {
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
    "right_arm": (40, 16, 4, 12, 4), "right_sleeve": (40, 32, 4, 12, 4),
    "left_arm": (32, 48, 4, 12, 4), "left_sleeve": (48, 48, 4, 12, 4),
    "right_leg": (0, 16, 4, 12, 4), "right_pants": (0, 32, 4, 12, 4),
    "left_leg": (16, 48, 4, 12, 4), "left_pants": (0, 48, 4, 12, 4),
}


def r(v):
    return round(v, 4)


def skin_faces(region, r0, r1, top="skin", bottom="skin"):
    """Per-face UVs equal to BakedGeoModel's box UV of `region`, sliced to rows r0..r1 of its
    height. top/bottom: "skin" = the region's own top/bottom face, "band" = one row of the side
    at the cut (a joint face), None = no face."""
    u, v, w, h, d = region
    faces = {
        "north": {"uv": [u + d, v + d + r0], "uv_size": [w, r1 - r0]},
        "south": {"uv": [u + d + w + d, v + d + r0], "uv_size": [w, r1 - r0]},
        # JSON west = character's right side = box strip 1; east = strip 2 (SWAP_EAST_WEST).
        "west": {"uv": [u, v + d + r0], "uv_size": [d, r1 - r0]},
        "east": {"uv": [u + d + w, v + d + r0], "uv_size": [d, r1 - r0]},
    }
    if top == "skin":
        faces["up"] = {"uv": [u + d + w, v + d], "uv_size": [w, -d]}
    elif top == "band":
        faces["up"] = {"uv": [u + d, v + d + r0], "uv_size": [w, 1]}
    if bottom == "skin":
        faces["down"] = {"uv": [u + d, v], "uv_size": [w, d]}
    elif bottom == "band":
        faces["down"] = {"uv": [u + d, v + d + r1 - 1], "uv_size": [w, 1]}
    return faces


def rect_faces(u, v, w, h, side=None):
    """Every face from one atlas rectangle (painted fills)."""
    side = side or (u, v, w, h)
    f = {"uv": [u, v], "uv_size": [w, h]}
    s = {"uv": [side[0], side[1]], "uv_size": [side[2], side[3]]}
    return {"north": f, "south": dict(f), "west": s, "east": dict(s), "up": dict(s), "down": dict(s)}


def cube(origin, size, uv, inflate=0.0, pivot=None, rotation=None):
    c = {"origin": [r(x) for x in origin], "size": [r(x) for x in size], "uv": uv}
    if inflate:
        c["inflate"] = inflate
    if rotation:
        c["pivot"] = [r(x) for x in pivot]
        c["rotation"] = rotation
    return c


def mirror_uv(uv, left_region, right_region):
    """Left-side copy of a skin-sliced face map: same rows, the left region's columns."""
    lu, lv = left_region[0], left_region[1]
    ru, rv = right_region[0], right_region[1]
    out = {}
    for face, f in uv.items():
        u, v = f["uv"]
        out[face] = {"uv": [u - ru + lu, v - rv + lv], "uv_size": list(f["uv_size"])}
    return out


def mirror_cube(c, uv):
    o, s = c["origin"], c["size"]
    m = {"origin": [r(-(o[0] + s[0])), o[1], o[2]], "size": list(s), "uv": uv}
    if "inflate" in c:
        m["inflate"] = c["inflate"]
    if "rotation" in c:
        p = c["pivot"]
        m["pivot"] = [r(-p[0]), p[1], p[2]]
        m["rotation"] = [c["rotation"][0], -c["rotation"][1], -c["rotation"][2]]
    return m


def bone(name, parent, pivot, cubes=(), rotation=None):
    b = {"name": name, "pivot": [r(x) for x in pivot]}
    if parent:
        b["parent"] = parent
    if rotation:
        b["rotation"] = rotation
    if cubes:
        b["cubes"] = list(cubes)
    return b


# ---------------------------------------------------------------- third-person body
# Heights (px): ground 0, knee 11, hip 21, chest 30, shoulder 37.5, neck 40.5, crown 50.5 (+0.6 hat).
LEG_X = 4.5          # hip joint x
HIP_Y = 21.0
KNEE_Y = 11.0
SHOULDER = (-12.25, 37.5, 0.0)
ELBOW = (-12.75, 28.5, 0.0)
WRIST = (-12.75, 19.5, 0.0)
FIST_CENTRE = (-12.75, 17.0, 0.0)
# Fist: 5.5 thick (palm normal, x), 5 long (y), 4.75 wide (index to pinky, z: the handle runs along it).
FIST = ((-15.5, 14.5, -2.375), (5.5, 5.0, 4.75))
REST_ABDUCTION = 4.0  # degrees, arms off the flanks at rest (Bedrock Z on right_arm)
BELT = (-10.75, 21.5, 0.5)
HEAD_PIVOT = (0.0, 40.5, -1.0)


def right_arm_parts(region, sleeve):
    """Shoulder cap + upper arm, forearm, fist of the right arm (Bedrock px)."""
    deltoid = cube((-16.5, 32.5, -4.0), (7.5, 6.5, 8.0), skin_faces(region, 0, 3, top="skin", bottom=None))
    upper = cube((-15.75, 27.5, -3.25), (6.25, 8.5, 6.5), skin_faces(region, 2, 6, top=None, bottom="band"))
    upper_o = cube((-15.75, 27.5, -3.25), (6.25, 8.5, 6.5), skin_faces(sleeve, 2, 6, top=None, bottom=None), inflate=0.35)
    fore = cube((-16.25, 19.0, -3.5), (7.0, 10.0, 7.0), skin_faces(region, 5, 10, top="band", bottom="band"))
    fore_o = cube((-16.25, 19.0, -3.5), (7.0, 10.0, 7.0), skin_faces(sleeve, 5, 10, top=None, bottom=None), inflate=0.35)
    fist = cube(FIST[0], FIST[1], skin_faces(region, 10, 12, top="band", bottom="skin"))
    return [deltoid, upper, upper_o], [fore, fore_o], [fist]


def leg_parts(region, pants):
    thigh = cube((-8.5, 10.5, -3.75), (8.0, 11.0, 7.5), skin_faces(region, 0, 6, top="skin", bottom="band"))
    thigh_o = cube((-8.5, 10.5, -3.75), (8.0, 11.0, 7.5), skin_faces(pants, 0, 6, top=None, bottom=None), inflate=0.35)
    shin = cube((-8.0, 2.5, -3.25), (7.0, 9.0, 6.5), skin_faces(region, 6, 10, top="band", bottom=None))
    shin_o = cube((-8.0, 2.5, -3.25), (7.0, 9.0, 6.5), skin_faces(pants, 6, 10, top=None, bottom=None), inflate=0.3)
    boot = cube((-8.25, 0.0, -4.75), (7.5, 3.0, 8.0), skin_faces(region, 9, 12, top="band", bottom="skin"))
    return [thigh, thigh_o], [shin, shin_o, boot]


def body_geo():
    bones = []
    body, jacket = SKIN["body"], SKIN["jacket"]
    waist = cube((-8.5, 19.0, -4.5), (17.0, 11.0, 9.0), skin_faces(body, 6, 12, top=None, bottom="skin"))
    waist_o = cube((-8.5, 19.0, -4.5), (17.0, 11.0, 9.0), skin_faces(jacket, 6, 12, top=None, bottom=None), inflate=0.35)
    chest = cube((-9.5, 30.0, -5.0), (19.0, 9.5, 10.0), skin_faces(body, 0, 6, top="skin", bottom="band"))
    chest_o = cube((-9.5, 30.0, -5.0), (19.0, 9.5, 10.0), skin_faces(jacket, 0, 6, top=None, bottom=None), inflate=0.35)
    traps = cube((-6.5, 38.5, -3.0), (13.0, 2.5, 7.0), skin_faces(body, 0, 2, top="skin", bottom=None))
    bones.append(bone("body", None, (0, HIP_Y, 0), [waist, waist_o, chest, chest_o, traps]))

    bu, bv = BELLY_AT
    def belly_uv(row):
        return {"north": {"uv": [bu + 3, bv + row], "uv_size": [9, 6]}, "south": {"uv": [bu + 15, bv + row], "uv_size": [9, 6]},
                "up": {"uv": [bu + 12, bv + row], "uv_size": [9, -3]}, "down": {"uv": [bu + 3, bv + row - 3], "uv_size": [9, 3]},
                "west": {"uv": [bu + 0, bv + row], "uv_size": [3, 6]}, "east": {"uv": [bu + 12, bv + row], "uv_size": [3, 6]}}
    belly = cube((-7.5, 19.5, -8.5), (15.0, 10.0, 4.5), belly_uv(12))
    belly_o = cube((-7.5, 19.5, -8.5), (15.0, 10.0, 4.5), belly_uv(3), inflate=0.4)
    bones.append(bone("belly", "body", (0, 29.5, -4.5), [belly, belly_o], rotation=[-6, 0, 0]))

    head = cube((-5.0, 40.5, -6.0), (10.0, 10.0, 10.0), skin_faces(SKIN["head"], 0, 8))
    hat = cube((-5.0, 40.5, -6.0), (10.0, 10.0, 10.0), skin_faces(SKIN["hat"], 0, 8), inflate=0.6)
    bones.append(bone("head", "body", HEAD_PIVOT, [head, hat]))

    fu, fv = BEARD_FILL
    mu, mv = BEARD_AT
    beard = cube((-5.25, 36.0, -7.75), (10.5, 6.5, 2.75), rect_faces(fu, fv, 16, 8, side=(fu, fv + 8, 4, 8)))
    jaw = cube((-5.4, 40.0, -6.6), (10.8, 3.5, 7.0), rect_faces(fu, fv + 8, 16, 3, side=(fu + 4, fv + 8, 8, 3)))
    moustache = cube((-3.75, 42.25, -7.0), (7.5, 1.25, 1.0), rect_faces(mu + 1, mv + 1, 14, 1))
    braid = cube((-1.5, 31.0, -7.5), (3.0, 5.25, 2.25), rect_faces(fu + 12, fv + 8, 4, 8), pivot=(0, 36, -6.5), rotation=[8, 0, 0])
    bones.append(bone("beard", "head", (0, 42.0, -6.0), [beard, jaw, moustache, braid]))

    for side in ("right", "left"):
        region, sleeve = SKIN["right_arm"], SKIN["right_sleeve"]
        upper, fore, fist = right_arm_parts(region, sleeve)
        sign = -1 if side == "right" else 1
        if side == "left":
            lr, ls = SKIN["left_arm"], SKIN["left_sleeve"]
            def m(cs):
                out = []
                for c in cs:
                    ref = sleeve if c.get("inflate") else region
                    tgt = ls if c.get("inflate") else lr
                    out.append(mirror_cube(c, mirror_uv(c["uv"], tgt, ref)))
                return out
            upper, fore, fist = m(upper), m(fore), m(fist)
        sx = lambda p: (sign * -p[0] if side == "left" else p[0], p[1], p[2])
        bones.append(bone(f"{side}_arm", "body", sx(SHOULDER), upper, rotation=[0, 0, REST_ABDUCTION * (1 if side == "right" else -1)]))
        bones.append(bone(f"{side}_forearm", f"{side}_arm", sx(ELBOW), fore))
        bones.append(bone(f"{side}_hand", f"{side}_forearm", sx(WRIST), fist))
        bones.append(bone("mjolnir_grip" if side == "right" else "left_grip", f"{side}_hand", sx(FIST_CENTRE)))

    bones.append(bone("mjolnir_belt", "body", BELT))

    for side in ("right", "left"):
        thigh, shin = leg_parts(SKIN["right_leg"], SKIN["right_pants"])
        if side == "left":
            lr, lp = SKIN["left_leg"], SKIN["left_pants"]
            def m(cs, ref_r=SKIN["right_leg"], ref_p=SKIN["right_pants"]):
                return [mirror_cube(c, mirror_uv(c["uv"], lp if c.get("inflate") else lr, ref_p if c.get("inflate") else ref_r)) for c in cs]
            thigh, shin = m(thigh), m(shin)
        x = -LEG_X if side == "right" else LEG_X
        bones.append(bone(f"{side}_leg", None, (x, HIP_Y, 0), thigh))
        bones.append(bone(f"{side}_shin", f"{side}_leg", (x, KNEE_Y, 0), shin))
    return geo("geometry.thor", bones, 6, 4)


# ---------------------------------------------------------------- first-person arms
FP_SHOULDER = (-5.0, 22.0, 0.0)
FP_ELBOW = (-6.0, 21.0, 0.0)
FP_WRIST = (-6.0, 14.0, 0.0)
FP_FIST_CENTRE = (-6.0, 12.0, 0.0)


def arms_geo():
    bones = []
    for side in ("right", "left"):
        region = SKIN["right_arm"] if side == "right" else SKIN["left_arm"]
        sleeve = SKIN["right_sleeve"] if side == "right" else SKIN["left_sleeve"]
        upper = [cube((-8.6, 20.5, -2.6), (5.2, 9.5, 5.2), skin_faces(region, 1, 5, top="skin", bottom=None)),
                 cube((-8.6, 20.5, -2.6), (5.2, 9.5, 5.2), skin_faces(sleeve, 1, 5, top=None, bottom=None), inflate=0.3)]
        fore = [cube((-8.9, 13.5, -2.9), (5.8, 8.0, 5.8), skin_faces(region, 5, 10, top="band", bottom="band")),
                cube((-8.9, 13.5, -2.9), (5.8, 8.0, 5.8), skin_faces(sleeve, 5, 10, top=None, bottom=None), inflate=0.3)]
        fist = [cube((-8.4, 10.0, -2.4), (4.8, 4.2, 4.8), skin_faces(region, 10, 12, top="band", bottom="skin"))]
        if side == "left":
            upper = [mirror_cube(c, c["uv"]) for c in upper]
            fore = [mirror_cube(c, c["uv"]) for c in fore]
            fist = [mirror_cube(c, c["uv"]) for c in fist]
        f = (lambda p: p) if side == "right" else (lambda p: (-p[0], p[1], p[2]))
        top = "armorRightArm" if side == "right" else "armorLeftArm"
        bones.append(bone(top, None, f(FP_SHOULDER), upper))
        bones.append(bone(f"{side}_forearm", top, f(FP_ELBOW), fore))
        bones.append(bone(f"{side}_hand", f"{side}_forearm", f(FP_WRIST), fist))
        bones.append(bone("mjolnir_grip" if side == "right" else "left_grip", f"{side}_hand", f(FP_FIST_CENTRE)))
    return geo("geometry.thor_arms", bones, 3, 2)


def geo(identifier, bones, width, height):
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": identifier, "texture_width": TEX_W, "texture_height": TEX_H,
                        "visible_bounds_width": width, "visible_bounds_height": height, "visible_bounds_offset": [0, height / 2, 0]},
        "bones": bones}]}


# ---------------------------------------------------------------- atlas
def atlas(decoded):
    skin = Image.open(os.path.join(decoded, "textures/entity/hero/thor.png")).convert("RGBA")
    beard = Image.open(os.path.join(decoded, "textures/entity/thor/beard.png")).convert("RGBA")
    belly = Image.open(os.path.join(decoded, "textures/entity/thor/belly.png")).convert("RGBA")
    out = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    out.paste(skin, (0, 0))
    out.paste(beard, BEARD_AT)
    out.paste(belly, BELLY_AT)
    # Beard fill: strands in the source beard's own palette, darker toward the tips.
    palette = sorted({p for p in beard.getdata() if p[3] > 0}, key=lambda c: sum(c[:3]))
    rng = random.Random(20261028)
    px = out.load()
    fu, fv = BEARD_FILL
    for x in range(16):
        base = rng.randrange(len(palette))
        for y in range(16):
            k = base + rng.choice((-1, 0, 0, 1))
            if y >= 6 and y < 8:
                k -= 2
            if y >= 8 and x >= 12:  # braid: alternating plait
                k = (len(palette) - 2) if (x + y) % 2 == 0 else 1
            c = palette[max(0, min(len(palette) - 1, k))]
            shade = 1.0 - 0.06 * max(0, y - 4) if y < 8 else 0.9
            px[fu + x, fv + y] = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade), 255)
    return out


# ---------------------------------------------------------------- clips
def key(v, easing="easeInOutSine"):
    return {"vector": v, "easing": easing}


# Lift (a bent-over pull, like a deadlift from the floor): the hips go back and down, the knees
# bend, the back leans in ~72° over the hammer and the head looks up at the handle. Fists low on a
# 0.8-block handle in front of a 3.2-block body need that lean; an upright sumo squat crushed the
# belly into the thighs. Feet stay flat on the ground (ThorBodyGeometryTest.feetOnTheGround).
# The arms are IK (ThorLift.solveThor); the clip only seeds them. Loop 1.2 s: strained tremble
# and a heave (at 0.6 s the hips rise and the back rounds a little more: a failed pull).
# Values were searched offline against the mesh scan (no arm through the body, fists on the
# handle) and then checked in tools/preview_thor.py.
LIFT = {"drop": 5.5, "back": 7.9, "lean": 71.9, "head": -50.0, "beard": -20.0, "thigh": 71.2, "spread": 18.4, "belly": -6.0, "shin": 84.4}
THIGH_PX, SHIN_PX, HIP_PX = 10.0, 11.0, 21.0
CROUCH = {"pos": [0, -3.77, 1.5], "lean": 10.3, "head": -30.0, "thigh": 63.6, "shin": 112.8, "arm": 0.0, "abduct": 1.8, "forearm": -12.0}
SIT = {"arm": -20.0, "abduct": 10.0, "forearm": -25.0}


def shin_for(drop, thigh, spread):
    """Shin rotation (deg, back) that puts the sole back on the ground for a hip `drop` px down."""
    hip = HIP_PX - drop
    knee = hip - THIGH_PX * math.cos(math.radians(thigh)) * math.cos(math.radians(spread))
    reach = max(-1.0, min(1.0, knee / (SHIN_PX * math.cos(math.radians(spread)))))
    return thigh + math.degrees(math.acos(reach))


def lift_squat():
    p = LIFT

    def shin_at(h):
        # The searched shin at rest; the heave changes it like the planar estimate does.
        base = shin_for(p["drop"], p["thigh"], p["spread"])
        return p["shin"] + shin_for(p["drop"] - 1.2 * h, p["thigh"] - 6.0 * h, p["spread"]) - base
    times = ("0.0", "0.3", "0.6", "0.9", "1.2")
    heave = (0.0, 0.25, 1.0, 0.25, 0.0)       # hips up at 0.6 s
    sway = (0.0, 0.8, 0.0, -0.8, 0.0)         # tremble side to side (deg)
    def series(f):
        return {t: key([round(v, 3) for v in f(h, w)]) for t, h, w in zip(times, heave, sway)}
    pos = series(lambda h, w: [0, -p["drop"] + 1.2 * h, p["back"]])
    bones = {
        "body": {"position": pos, "rotation": series(lambda h, w: [p["lean"] + 3.0 * h, w, 0])},
        "head": {"rotation": series(lambda h, w: [p["head"] - 4.0 * h, 0, 0])},
        "belly": rot([p["belly"], 0, 0]),
        "beard": rot([p["beard"], 0, 0]),
    }
    for side, sign in (("right", 1), ("left", -1)):
        bones[side + "_leg"] = {"position": pos, "rotation": series(
            lambda h, w, sign=sign: [-(p["thigh"] - 6.0 * h), 0, sign * p["spread"]])}
        bones[side + "_shin"] = {"rotation": series(
            lambda h, w, sign=sign: [shin_at(h), 0, -sign * p["spread"] * 0.4])}
        bones[side + "_arm"] = rot([-30, 0, sign * 12])
        bones[side + "_forearm"] = rot([-10, 0, 0])
    return {"loop": True, "animation_length": 1.2, "bones": bones}


def rot(v):
    return {"rotation": {"0.0": key(v)}}


def clips():
    still = lambda bones: {"loop": True, "animation_length": 0.05, "bones": bones}
    def rot(v):
        return {"rotation": {"0.0": key(v)}}
    crouch = still({
        # Additive over the vanilla pose bridge (which already leans the body by crouch's 28.6°):
        # hips sink a little, knees bend deep and the back leans in, so the crown stays under the
        # 2.625-block crouch box while the soles stay on the ground.
        "body": {"position": {"0.0": key(CROUCH["pos"])}, "rotation": {"0.0": key([CROUCH["lean"], 0, 0])}},
        "head": rot([CROUCH["head"], 0, 0]),
        "right_leg": {"position": {"0.0": key(CROUCH["pos"])}, "rotation": {"0.0": key([-CROUCH["thigh"], 0, 0])}},
        "left_leg": {"position": {"0.0": key(CROUCH["pos"])}, "rotation": {"0.0": key([-CROUCH["thigh"], 0, 0])}},
        "right_shin": rot([CROUCH["shin"], 0, 0]),
        "left_shin": rot([CROUCH["shin"], 0, 0]),
        "right_arm": rot([CROUCH["arm"], 0, CROUCH["abduct"]]),
        "left_arm": rot([CROUCH["arm"], 0, -CROUCH["abduct"]]),
        "right_forearm": rot([CROUCH["forearm"], 0, 0]),
        "left_forearm": rot([CROUCH["forearm"], 0, 0]),
    })
    # Additive over the bridge's riding pose (thighs forward): knees bend, shins hang, the arms
    # open a little and the forearms rest above the thighs instead of sinking into them.
    sit = still({"right_shin": rot([70, 0, 0]), "left_shin": rot([70, 0, 0]),
                 "right_arm": rot([SIT["arm"], 0, SIT["abduct"]]), "left_arm": rot([SIT["arm"], 0, -SIT["abduct"]]),
                 "right_forearm": rot([SIT["forearm"], 0, 0]), "left_forearm": rot([SIT["forearm"], 0, 0])})
    lift = lift_squat()
    return {"format_version": "1.8.0", "animations": {"crouch": crouch, "sit": sit, "lift_squat": lift}}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--decoded", default=os.path.join(ROOT, "viltrumitecore/build/generated/binassets/assets/viltrumitecore"),
                    help="decoded binassets (gradle task output or a folder with the same layout)")
    ap.add_argument("--png", help="also write the atlas PNG here (previews)")
    args = ap.parse_args()
    write_json(os.path.join(RES, "geo/thor/thor.geo.json"), body_geo())
    write_json(os.path.join(RES, "geo/thor/thor_arms.geo.json"), arms_geo())
    write_json(os.path.join(RES, "animations/thor/thor.animation.json"), clips())
    img = atlas(args.decoded)
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    with open(os.path.join(BIN, "textures/entity/thor/thor_body.png.b64"), "w", encoding="ascii") as f:
        f.write(base64.b64encode(buf.getvalue()).decode("ascii"))
    if args.png:
        img.save(args.png)
    print("thor body written; atlas", len(buf.getvalue()), "bytes")


if __name__ == "__main__":
    main()
