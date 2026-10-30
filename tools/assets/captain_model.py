"""Captain America (Steve Rogers, The Avengers 2012 suit) — body rig, HD skin, gear and the vibranium shield.
Own procedural pixel art, CC0 (no film frames, no third-party textures).

Body: the split-limb player rig of tools/assets/jinwoo_model.py (Steve layout, 64-space UVs, 128x128 HD skin),
so elbows, knees, wrists and fingers bend. Gear (own atlas + glow): cowl wings and the "A", belt with pouches and
buckle, glove and boot cuffs, chin strap, and an empty `left_shield` bone on the outer left forearm that the
renderer uses to place the separate shield model. Shield: its own geo (terraced dome of three cut-out discs, a
20-segment rim and the back straps) and texture (+ glow used while the kinetic charge empowers it).
Outputs geo/captain/{captain,captain_arms,captain_gear,captain_arms_gear,captain_shield}.geo.json and
textures/entity/captain/{captain,captain_gear,captain_gear_glow,captain_shield,captain_shield_glow}.png.
"""
import copy
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_geo import Cube, Face, Mat, Model, put, write_json, write_png, RES  # noqa: E402
from jinwoo_model import body_model, skeleton  # noqa: E402

D = 2
# 2012 suit palette
NAVY, NAVY_HI, NAVY_LO = '#22386b', '#3a5590', '#16264a'
STEEL = '#c9d0dc'
RED, RED_LO = '#b3262c', '#7d1a1f'
WHITE = '#eceef3'
LEATHER, LEATHER_HI, LEATHER_LO = '#6e4626', '#8c5c35', '#432a16'
SKIN, SKIN_LO = '#e9c2a0', '#c99c7c'
HAIR = '#a2763f'
SILVER = '#b8bec8'


def sides(face):
    return face.name in ('north', 'south', 'east', 'west')


def star_mask(px, py, cx, cy, r, rot=0.0, inner=0.4):
    """Five-point star (point up) as a boolean mask over pixel coordinates (exact polygon test)."""
    from matplotlib.path import Path
    pts = []
    for i in range(10):
        a = rot + i * math.pi / 5
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + rr * math.sin(a), cy + rr * math.cos(a)))
    path = Path(pts)
    flat = np.c_[np.ravel(px), np.ravel(py)]
    return path.contains_points(flat).reshape(np.shape(px))


# ---- skin -----------------------------------------------------------------------------------------------
def seams(face, rgb, xs=(), ys=(), color=NAVY_LO, amount=0.55, w=0.18):
    x, y, z = face.world()
    for xx in xs:
        put(rgb, np.abs(x - xx) < w, color, amount)
    for yy in ys:
        put(rgb, np.abs(y - yy) < w, color, amount)


def head_fn(face, rgb, glow, rs):
    """Layer 0: Steve's face (the cowl itself is layer 1)."""
    x, y, z = face.world()
    if face.name in ('up', 'south'):
        put(rgb, np.s_[:, :], NAVY, 1.0)
        return
    if face.name == 'down':
        put(rgb, np.s_[:, :], SKIN_LO, 0.6)
        return
    if face.name in ('east', 'west'):
        put(rgb, y > 26.0, NAVY, 1.0)
        put(rgb, (y <= 26.0) & (z > 0.5), NAVY, 1.0)
        put(rgb, y < 25.0, SKIN_LO, 0.35)
        return
    ax = np.abs(x)
    put(rgb, y > 29.2, NAVY, 1.0)
    # brows, eyes (blue), nose shadow, mouth, square jaw shading, chin cleft
    put(rgb, (ax > 0.7) & (ax < 3.0) & (np.abs(y - 28.75) < 0.22), '#6d4a2c', 0.95)
    white = (ax > 0.85) & (ax < 2.85) & (y > 27.55) & (y < 28.35)
    put(rgb, white, '#f2efe9', 1.0)
    put(rgb, (ax > 1.35) & (ax < 2.3) & (y > 27.55) & (y < 28.35), '#3f74c4', 1.0)
    put(rgb, (ax > 1.6) & (ax < 2.05) & (y > 27.7) & (y < 28.15), '#162a52', 1.0)
    put(rgb, (ax < 0.55) & (y > 26.2) & (y < 27.6), SKIN_LO, 0.45)
    put(rgb, (ax < 0.85) & (np.abs(y - 26.15) < 0.2), SKIN_LO, 0.8)
    put(rgb, (ax < 1.7) & (np.abs(y - 25.35) < 0.2), '#9c5f50', 0.85)
    put(rgb, (ax > 2.8) & (y < 26.5), SKIN_LO, 0.4)
    put(rgb, (ax < 0.25) & (y < 24.7), SKIN_LO, 0.5)


def cowl_alpha(face, rs):
    x, y, z = face.world()
    a = np.full(x.shape, 255, np.uint8)
    if face.name == 'north':
        ax = np.abs(x)
        opening = (y < 27.2) & (ax < 3.35)
        eyes = (y >= 27.2) & (y < 28.55) & (ax > 0.6) & (ax < 3.15)
        a[opening | eyes] = 0
    elif face.name in ('east', 'west'):
        a[(y < 25.0) & (z < 0.4)] = 0  # jaw stays bare under the ear flap
    elif face.name == 'down':
        a[:] = 0
    return a


def cowl_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], NAVY, 0.92)
    if face.name == 'north':
        ax = np.abs(x)
        # the white "A": two legs and the bar, centred on the forehead
        ty = (y - 29.35) / 2.2
        legs = (ty >= 0) & (ty <= 1) & (np.abs(ax - (1.0 - ty * 0.85)) < 0.34)
        bar = (np.abs(y - 29.95) < 0.22) & (ax < 0.75)
        put(rgb, legs | bar, WHITE, 1.0)
        put(rgb, (np.abs(y - 28.6) < 0.18) & (ax > 0.55) & (ax < 3.3), NAVY_LO, 0.7)  # eye hole rim
    if face.name in ('east', 'west'):
        # painted wing feathers behind the 3D wing
        wing = (y > 28.2) & (y < 30.8) & (z > -2.6) & (z < 0.6) & ((y - 28.2) > (z + 2.6) * 0.35)
        put(rgb, wing, WHITE, 0.95)
        for k in range(3):
            put(rgb, wing & (np.abs(y - (28.9 + k * 0.6)) < 0.12), '#b9bfca', 0.9)
        put(rgb, (np.abs(z - 1.1) < 0.2) & (y < 28), NAVY_LO, 0.6)
    if face.name == 'up':
        put(rgb, np.abs(x) < 0.3, NAVY_HI, 0.35)


def torso_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], NAVY, 1.0)
    if face.name == 'north':
        put(rgb, y > 21.5, NAVY_HI, 0.25)
        # abdomen: red and white vertical stripes (the 2012 suit)
        ab = (y < 18.0) & (y > 12.9)
        stripe = np.floor((x + 4.0) / 1.0).astype(int) % 2 == 0
        put(rgb, ab & stripe, RED, 1.0)
        put(rgb, ab & ~stripe, WHITE, 0.92)
        put(rgb, ab & (np.abs(np.mod(x + 4.0, 1.0) - 0.5) > 0.42), '#5a1418', 0.25)
        put(rgb, np.abs(y - 18.05) < 0.2, '#0e1a36', 0.9)
        # silver-outlined white star on the chest
        px, py = x, y
        put(rgb, star_mask(px, py, 0.0, 20.9, 2.55, inner=0.4), STEEL, 1.0)
        put(rgb, star_mask(px, py, 0.0, 20.9, 2.1, inner=0.4), WHITE, 1.0)
        # chest plate edges and abdominal line
        seams(face, rgb, xs=(-3.1, 3.1), ys=(23.3,), amount=0.4)
    elif face.name == 'south':
        seams(face, rgb, xs=(0.0,), ys=(18.0, 21.0), amount=0.45)
        put(rgb, (np.abs(x) > 1.6) & (np.abs(x) < 3.5) & (y > 18.6) & (y < 22.6), NAVY_HI, 0.22)
    elif face.name in ('east', 'west'):
        put(rgb, (y < 18.0) & (y > 12.9), RED_LO, 0.55)
        seams(face, rgb, ys=(18.0,), amount=0.6)
    elif face.name == 'up':
        put(rgb, np.s_[:, :], NAVY_HI, 0.3)


def arm_in_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], NAVY, 1.0)
    # lighter shoulder panel with silver scale lines
    sh = y > 20.8
    put(rgb, sh, NAVY_HI, 0.55)
    if sides(face):
        for k in range(4):
            put(rgb, sh & (np.abs(y - (21.3 + k * 0.75)) < 0.12), STEEL, 0.35)
    seams(face, rgb, ys=(20.8,), amount=0.6)
    if face.name in ('north', 'south'):
        cx = face.ox + face.sx / 2
        put(rgb, (np.abs(x - cx) < 0.15) & (y < 20.8) & (y > 16), NAVY_LO, 0.5)
    # brown leather gloves from the wrist down, with a cuff
    glove = y < 15.6
    put(rgb, glove, LEATHER, 1.0)
    put(rgb, (y < 15.6) & (y > 14.9), LEATHER_LO, 0.75)
    put(rgb, (y < 13.2) & sides(face), LEATHER_HI, 0.35)
    if face.name == 'down':
        put(rgb, np.s_[:, :], LEATHER_LO, 0.8)
    # knuckle line
    if sides(face):
        put(rgb, np.abs(y - 13.0) < 0.14, LEATHER_LO, 0.8)


def leg_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], NAVY, 1.0)
    if face.name == 'north':
        cx = face.ox + face.sx / 2
        put(rgb, (np.abs(x - cx) < 1.3) & (y > 5.6) & (y < 8.2), NAVY_HI, 0.45)  # knee pad
        put(rgb, (np.abs(x - cx) < 1.3) & (np.abs(y - 8.2) < 0.14), NAVY_LO, 0.7)
    if face.name in ('east', 'west'):
        put(rgb, (np.abs(z) < 0.16) & (y > 5), NAVY_LO, 0.55)
    seams(face, rgb, ys=(11.2,), amount=0.35)
    boot = y < 5.4
    put(rgb, boot, LEATHER, 1.0)
    for yy in (2.0, 3.6):
        put(rgb, boot & (np.abs(y - yy) < 0.2), LEATHER_LO, 0.85)
    if face.name == 'north':
        cx = face.ox + face.sx / 2
        for yy in (2.0, 3.6):
            put(rgb, (np.abs(y - yy) < 0.25) & (np.abs(x - cx) < 0.4), SILVER, 0.9)
    put(rgb, y < 0.55, '#24160c', 1.0)
    if face.name == 'down':
        put(rgb, np.s_[:, :], '#24160c', 1.0)


def clear_alpha(face, rs):
    return np.zeros((face.h, face.w), np.uint8)


def boot_cuff_alpha(face, rs):
    x, y, z = face.world()
    a = np.where((y > 4.7) & (y < 5.9), 255, 0).astype(np.uint8)
    if face.name in ('up', 'down'):
        a[:] = 0
    return a


def boot_cuff_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], LEATHER_HI, 1.0)
    put(rgb, np.abs(y - 4.8) < 0.15, LEATHER_LO, 0.9)


def shoulder_alpha(face, rs):
    x, y, z = face.world()
    a = np.where(y > 21.4, 255, 0).astype(np.uint8)
    if face.name == 'down':
        a[:] = 0
    return a


def shoulder_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], NAVY_HI, 0.6)
    if sides(face):
        for k in range(3):
            put(rgb, np.abs(y - (21.9 + k * 0.7)) < 0.12, STEEL, 0.5)
        put(rgb, np.abs(y - 21.5) < 0.15, NAVY_LO, 0.9)


SKIN_MATS = {
    ('head', 0): Mat(SKIN, noise=2, edge=0.0, top=1.0, bottom=0.85, fn=head_fn),
    ('head', 1): Mat(NAVY, noise=5, edge=0.0, top=1.15, fn=cowl_fn, alpha_fn=cowl_alpha),
    ('torso', 0): Mat(NAVY, noise=4, edge=0.0, fn=torso_fn),
    ('torso', 1): Mat(NAVY, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('arm', 0): Mat(NAVY, noise=4, edge=0.0, fn=arm_in_fn),
    ('arm', 1): Mat(NAVY_HI, noise=4, edge=0.0, fn=shoulder_fn, alpha_fn=shoulder_alpha),
    ('leg', 0): Mat(NAVY, noise=4, edge=0.0, fn=leg_fn),
    ('leg', 1): Mat(LEATHER_HI, noise=3, edge=0.0, fn=boot_cuff_fn, alpha_fn=boot_cuff_alpha),
}


def group(bone):
    if bone in ('head', 'torso'):
        return bone
    return 'arm' if any(p in bone for p in ('arm', 'hand', 'fingers')) else 'leg'


class _Box:
    def __init__(self, origin, size):
        self.origin, self.size = origin, size


def paint_skin(model, mats, seed=2012):
    """Paints every full skin box of a split-limb body into a 128x128 HD skin (Steve layout)."""
    rng = random.Random(seed)
    img = np.zeros((64 * D, 64 * D, 4), np.uint8)
    for origin, size, (u, v), layer, bone in model.skin_boxes:
        mat = mats[(group(bone), layer)]
        w, h, d = size
        rects = {'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'east': (u + d + w, v + d, d, h),
                 'south': (u + d + w + d, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}
        box = _Box(origin, size)
        for name, (x, y, rw, rh) in rects.items():
            face = Face(box, name, int(round(rw * D)), int(round(rh * D)))
            col, _ = mat.paint(face, rng)
            img[int(y * D):int(y * D) + face.h, int(x * D):int(x * D) + face.w] = col
    return img


# ---- gear -----------------------------------------------------------------------------------------------
def leather_fn(face, rgb, glow, rs):
    if sides(face) and face.h > 2:
        put(rgb, np.s_[0, :], LEATHER_HI, 0.6)
        put(rgb, np.s_[-1, :], LEATHER_LO, 0.7)


def buckle_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], SILVER, 1.0)
    if face.w > 3 and face.h > 3:
        put(rgb, np.s_[1:-1, 1:-1], '#8d94a0', 0.8)
        put(rgb, np.s_[1:2, 1:-1], '#e6e9ee', 0.7)


def wing_fn(face, rgb, glow, rs):
    if face.name in ('east', 'west'):
        for k in range(1, max(2, face.h), 2):
            put(rgb, np.s_[k:k + 1, :], '#b5bcc8', 0.8)


def a_alpha(face, rs):
    if face.name != 'north':
        return np.zeros((face.h, face.w), np.uint8)
    h, w = face.h, face.w
    v, u = np.mgrid[0:h, 0:w]
    uu = (u + 0.5) / w * 2 - 1
    vv = 1 - (v + 0.5) / h
    legs = np.abs(np.abs(uu) - (1 - vv) * 0.85) < 0.22
    bar = (np.abs(vv - 0.3) < 0.12) & (np.abs(uu) < 0.45)
    return np.where(legs | bar, 255, 0).astype(np.uint8)


GEAR = {
    'leather': Mat(LEATHER, noise=5, edge=0.15, fn=leather_fn),
    'leather_dark': Mat(LEATHER_LO, noise=4, edge=0.18),
    'buckle': Mat(SILVER, noise=2, edge=0.0, fn=buckle_fn, grain=1),
    'wing': Mat(WHITE, noise=3, edge=0.1, fn=wing_fn, grain=1),
    'a': Mat(WHITE, noise=0, edge=0.0, alpha_fn=a_alpha, grain=1),
    'strap': Mat('#3e2c1c', noise=3, edge=0.1),
}


def gear_model():
    m = Model('captain_gear', density=4, width=128, seed=2012)
    skeleton(m, False)
    m.bone('left_shield', 'left_forearm', [8.75, 16.4, 0.0])
    # cowl: raised A plate and the two small temple wings
    m.cube('head', [-1.15, 29.25, -4.62], [2.3, 2.3, 0.08], GEAR['a'], faces=('north',))
    for sx in (-1, 1):
        x0 = 4.45 if sx > 0 else -4.45 - 0.4
        m.cube('head', [x0, 28.6, -2.4], [0.4, 1.2, 2.4], GEAR['wing'], rotation=[16, 0, 0], pivot=[x0, 29.2, -1.2])
        m.cube('head', [x0, 29.5, -1.9], [0.4, 1.0, 2.0], GEAR['wing'], rotation=[30, 0, 0], pivot=[x0, 30, -0.9])
    # chin strap under the jaw
    m.cube('head', [-3.6, 24.0, -0.7], [7.2, 0.55, 1.0], GEAR['strap'], inflate=0.5)
    # utility belt with silver buckle and pouches
    m.cube('torso', [-4.3, 11.9, -2.3], [8.6, 1.35, 4.6], GEAR['leather'])
    m.cube('torso', [-0.9, 11.75, -2.45], [1.8, 1.65, 0.2], GEAR['buckle'])
    for x0 in (-3.9, -2.4, 1.25, 2.75):
        m.cube('torso', [x0, 10.9, -2.75], [1.25, 1.9, 0.6], GEAR['leather_dark'])
    for x0 in (-3.0, 1.6):
        m.cube('torso', [x0, 10.8, 2.25], [1.4, 2.0, 0.7], GEAR['leather_dark'])
    # glove cuffs and wrist straps
    for side, cx in (('right', -6.0), ('left', 6.0)):
        m.cube(f'{side}_hand', [cx - 2.0, 14.55, -2.0], [4.0, 1.05, 4.0], GEAR['leather'], inflate=0.32)
        m.cube(f'{side}_forearm', [cx - 2.0, 16.6, -2.0], [4.0, 0.45, 4.0], GEAR['strap'], inflate=0.28)
    # boot straps
    for side, cx in (('right', -1.9), ('left', 1.9)):
        m.cube(f'{side}_shin', [cx - 2.0, 2.7, -2.0], [4.0, 0.5, 4.0], GEAR['strap'], inflate=0.3)
    # shield arm straps (visible under the shield edge)
    m.cube('left_forearm', [7.9, 15.6, -2.0], [0.4, 0.6, 4.0], GEAR['strap'], inflate=0.1)
    m.cube('left_forearm', [7.9, 17.6, -2.0], [0.4, 0.6, 4.0], GEAR['strap'], inflate=0.1)
    return m


# ---- shield ---------------------------------------------------------------------------------------------
SHIELD_R = 6.6  # model px (≈ 0.41 block radius; the MCU shield is ~76 cm across)


def ring_colour(r):
    """Front pattern by normalised radius: red / white / red rings, blue disc."""
    if r > 0.8:
        return RED
    if r > 0.6:
        return '#e7e9ee'
    if r > 0.4:
        return RED
    return '#24438a'


def shield_front_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    r = np.hypot(x, y) / SHIELD_R
    for lo, hi, col in ((0.8, 2, RED), (0.6, 0.8, '#e7e9ee'), (0.4, 0.6, RED), (-1, 0.4, '#24438a')):
        put(rgb, (r > lo) & (r <= hi), col, 1.0)
    # metallic sheen: lighter at the top-left, worn scratches
    shade = np.clip(1.08 - 0.16 * (r ** 2) + 0.08 * (y - x) / SHIELD_R, 0.75, 1.15)
    rgb[:] = rgb * shade[..., None]
    for edge in (0.8, 0.6, 0.4):
        put(rgb, np.abs(r - edge) < 0.018, '#2a1416', 0.35)
    put(rgb, star_mask(x, y, 0.0, 0.0, SHIELD_R * 0.385, inner=0.4), '#f3f4f7', 1.0)
    put(rgb, (r > 0.97), '#c8ccd6', 0.55)
    n = rs.uniform(0, 1, r.shape)
    put(rgb, n > 0.985, '#ffffff', 0.25)
    if glow is not None:
        g = star_mask(x, y, 0.0, 0.0, SHIELD_R * 0.385, inner=0.4)
        glow[g] = [190, 225, 255, 210]
        glow[(r > 0.96) & (r <= 1.0)] = [150, 200, 255, 170]
        for edge in (0.8, 0.6, 0.4):
            glow[np.abs(r - edge) < 0.02] = [120, 180, 255, 120]


def shield_back_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    r = np.hypot(x, y) / SHIELD_R
    put(rgb, np.s_[:, :], '#7c838e', 1.0)
    shade = np.clip(0.95 + 0.12 * r, 0.8, 1.1)
    rgb[:] = rgb * shade[..., None]
    put(rgb, np.abs(r - 0.93) < 0.02, '#5a606a', 0.8)
    # two leather straps and the grip
    for yy in (1.6, -1.6):
        put(rgb, (np.abs(y - yy) < 0.55) & (np.abs(x) < 4.4), LEATHER, 1.0)
        put(rgb, (np.abs(y - yy) < 0.55) & (np.abs(np.abs(x) - 4.1) < 0.35), SILVER, 1.0)
    put(rgb, (np.abs(x) < 0.5) & (np.abs(y) < 1.1), LEATHER_LO, 1.0)


def disc_alpha(radius):
    def fn(face, rs):
        x, y, z = face.world()
        return np.where(np.hypot(x, y) <= radius, 255, 0).astype(np.uint8)
    return fn


def rim_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], RED, 1.0)
    if face.name in ('up', 'down'):
        put(rgb, np.s_[:, :], '#d4d8df', 0.55)
    if glow is not None:
        glow[:] = [150, 200, 255, 120]


def shield_model():
    m = Model('captain_shield', density=8, width=256, seed=1941)
    m.bone('shield', None, [0, 0, 0])
    m.bone('rim', 'shield', [0, 0, 0])
    front = Mat(RED, noise=3, edge=0.0, fn=shield_front_fn, grain=1, alpha_fn=disc_alpha(SHIELD_R))
    m.cube('shield', [-SHIELD_R, -SHIELD_R, -0.32], [2 * SHIELD_R, 2 * SHIELD_R, 0.01], front, faces=('north',))
    mid = Mat(RED, noise=3, edge=0.0, fn=shield_front_fn, grain=1, alpha_fn=disc_alpha(SHIELD_R * 0.6))
    m.cube('shield', [-SHIELD_R * 0.6, -SHIELD_R * 0.6, -0.5], [1.2 * SHIELD_R, 1.2 * SHIELD_R, 0.01], mid, faces=('north',))
    top = Mat(RED, noise=3, edge=0.0, fn=shield_front_fn, grain=1, alpha_fn=disc_alpha(SHIELD_R * 0.4))
    m.cube('shield', [-SHIELD_R * 0.4, -SHIELD_R * 0.4, -0.62], [0.8 * SHIELD_R, 0.8 * SHIELD_R, 0.01], top, faces=('north',))
    back = Mat('#7c838e', noise=4, edge=0.0, fn=shield_back_fn, grain=1, alpha_fn=disc_alpha(SHIELD_R))
    m.cube('shield', [-SHIELD_R, -SHIELD_R, 0.3], [2 * SHIELD_R, 2 * SHIELD_R, 0.01], back, faces=('south',))
    rim = Mat(RED, noise=2, edge=0.0, fn=rim_fn, grain=1)
    n = 24
    seg = 2 * math.pi * SHIELD_R / n * 1.08
    for i in range(n):
        a = 360.0 * i / n
        # one rim tile at the top, rotated around Z to its place
        m.cube('rim', [-seg / 2, SHIELD_R - 0.35, -0.34], [seg, 0.5, 0.66], rim, rotation=[0, 0, a], pivot=[0, 0, 0])
    # rings stepping up (side bands of the inner discs)
    for frac, z0, depth in ((0.6, -0.5, 0.18), (0.4, -0.62, 0.12)):
        rr = SHIELD_R * frac
        k = 16
        sg = 2 * math.pi * rr / k * 1.1
        for i in range(k):
            a = 360.0 * i / k
            m.cube('shield', [-sg / 2, rr - 0.12, z0], [sg, 0.12, depth], Mat('#e1e4ea', noise=2, edge=0.0, grain=1),
                   rotation=[0, 0, a], pivot=[0, 0, 0], faces=('up', 'down', 'north'))
    return m


# ---- outputs --------------------------------------------------------------------------------------------
ARM_BONES = ('arm', 'forearm', 'hand', 'fingers', 'thumb', 'shield')
FP_TOP = {'right_arm': 'armorRightArm', 'left_arm': 'armorLeftArm'}


def arms_only(src, ident):
    fp = Model(ident, density=src.density, width=src.width)
    fp.height = src.height
    for b in src.bones:
        parts = b.name.split('_', 1)
        if parts[0] not in ('right', 'left') or len(parts) < 2 or parts[1] not in ARM_BONES:
            continue
        nb = fp.bone(FP_TOP.get(b.name, b.name), None if b.name in FP_TOP else FP_TOP.get(b.parent, b.parent), b.pivot, b.rotation)
        nb.cubes = [copy.copy(c) for c in b.cubes]
    return fp


def rename(model, ident):
    model.identifier = ident
    return model


def main():
    body = rename(body_model(False), 'captain')
    write_json(os.path.join(RES, 'geo/captain/captain.geo.json'), body.geo_json())
    write_json(os.path.join(RES, 'geo/captain/captain_arms.geo.json'), arms_only(body, 'captain_arms').geo_json())
    write_png('textures/entity/captain/captain.png', paint_skin(body, SKIN_MATS))
    gear = gear_model()
    gear.write('geo/captain/captain_gear.geo.json', 'textures/entity/captain/captain_gear.png',
               'textures/entity/captain/captain_gear_glow.png')
    write_json(os.path.join(RES, 'geo/captain/captain_arms_gear.geo.json'), arms_only(gear, 'captain_arms_gear').geo_json())
    shield = shield_model()
    shield.write('geo/captain/captain_shield.geo.json', 'textures/entity/captain/captain_shield.png',
                 'textures/entity/captain/captain_shield_glow.png')
    print('gear atlas', gear.width, gear.height, 'shield atlas', shield.width, shield.height)


if __name__ == '__main__':
    main()
