#!/usr/bin/env python3
"""Thanos (MCU look, own work, CC0, no external sources): body model + texture atlas + glow.

60 model px tall (3.75 blocks, the entity box is 1.6 x 3.8). Massive build, bare purple arms,
ridged chin, dark navy armoured vest with a gold V trim, gold belt and buckle, navy tassets with
gold edges, gold knee guards, boots with gold toe caps and a gold bracer on the right wrist. The
left hand wears the Infinity Gauntlet: gold palm, cuff and knuckle band, four two-segment fingers
and a two-segment thumb, and the six stones in the MCU sockets: Time on the thumb, Space on the
index, Reality on the middle, Power on the ring, Soul on the little finger, Mind on the back of
the hand. Every stone is its own bone (stone_<key>) holding a faceted gem (two crossed cubes) and
a glow-only halo shell, so the renderer can light each stone with its own intensity.

Rig (bone names are a contract with thanos_clips.py and ThanosRenderer):
root > waist > {kilt_front, kilt_back, torso > {neck > head, right_arm > right_forearm >
right_hand > {right_fingers > right_fingers_tip, right_thumb}, left_arm > left_forearm >
left_hand > {stone_mind, left_<finger> > left_<finger>_tip (+ stone), left_thumb >
left_thumb_tip (+ stone_time)}}}; root > {right,left}_leg > _shin > _foot.
Conventions (Bedrock degrees, like jinwoo_poses): arm X- = forward raise, right arm Z+ / left
arm Z- = outward, forearm X- = elbow bend, torso X+ = lean forward, Y+ = turn right, leg X- =
forward swing, shin X+ = knee bend; left fingers Z+ / right fingers Z- curl into the palm.

Outputs: resources geo/thanos/thanos.geo.json, binassets textures/entity/thanos/thanos.png(.b64)
and thanos_glow.png(.b64); a plain copy of the textures and previews go to build/ (not committed).
Run: python3 tools/assets/thanos_model.py [--preview]
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_geo import Mat, Model, put, ROOT  # noqa: E402

SIDE = ('north', 'south', 'east', 'west')
SKIN, SKIN_DK, SKIN_HI = '#7d6390', '#5a416d', '#a68abb'
GOLD, GOLD_HI, GOLD_DK = '#c9a227', '#f2d870', '#86650f'
SUIT, SUIT_HI, SUIT_DK = '#2b3140', '#434c62', '#181c26'
PANTS = '#232937'
BOOT = '#1c2029'
EYE = (111, 183, 255)
STONES = {  # ThanosStone order and colours
    'time': (0x35, 0xF0, 0x7A),
    'space': (0x3C, 0x8C, 0xFF),
    'reality': (0xF0, 0x28, 0x3A),
    'power': (0xA8, 0x3C, 0xFF),
    'soul': (0xFF, 0x94, 0x26),
    'mind': (0xFF, 0xD6, 0x30),
}


def vgrad(face, rgb, top=1.06, bottom=0.86):
    if face.name in SIDE:
        _, v = face.grid()
        rgb *= (top + (bottom - top) * v)[..., None]


def speckle(face, rgb, rs, color, p=0.04, a=0.35):
    put(rgb, rs.uniform(0, 1, (face.h, face.w)) < p, color, a)


# ------------------------------------------------------------------ skin
def skin_fn(face, rgb, glow, rs):
    vgrad(face, rgb, 1.07, 0.84)
    speckle(face, rgb, rs, SKIN_DK, 0.05, 0.3)
    tag = face.cube.tag
    x, y, z = face.world()
    if tag == 'upper_arm' and face.name in SIDE:
        put(rgb, np.abs(y - 41.6) < 0.26, SKIN_DK, 0.35)  # deltoid / biceps split
    if tag == 'forearm' and face.name in ('north', 'south'):
        put(rgb, (np.abs(np.abs(x) - 16.0) < 0.26) & (y > 31), SKIN_DK, 0.25)
    if tag == 'neck' and face.name in SIDE:
        put(rgb, np.abs(np.abs(x) - 2.2) < 0.26, SKIN_DK, 0.3)  # neck tendons


def head_fn(face, rgb, glow, rs):
    vgrad(face, rgb, 1.08, 0.9)
    speckle(face, rgb, rs, SKIN_DK, 0.04, 0.25)
    x, y, z = face.world()
    ax = np.abs(x)
    n = face.name
    if n == 'north':
        sock = (y > 53.4) & (y < 55.7) & (ax > 0.8) & (ax < 4.4)
        rgb[sock] *= 0.72
        eye = (y > 54.0) & (y < 55.0) & (ax > 1.4) & (ax < 3.6)
        put(rgb, eye, '#a9bad3')
        iris = eye & (ax > 2.0) & (ax < 3.0)
        put(rgb, iris, '#3368c0')
        glow[iris] = [EYE[0] // 2, EYE[1] // 2, EYE[2] // 2, 255]
        brow = (y > 55.5) & (y < 56.5) & (ax > 0.6) & (ax < 4.8)
        put(rgb, brow, SKIN_DK, 0.75)
        for yy in (57.5, 58.5):
            put(rgb, (np.abs(y - yy) < 0.26) & (ax < 3.2), SKIN_DK, 0.35)  # forehead creases
    elif n in ('east', 'west'):
        front = -z
        put(rgb, (front > 3.8) & (y < 53.0), SKIN_DK, 0.2)  # cheek shadow
        put(rgb, (np.abs(front - 1.2) < 0.26) & (y > 50.4) & (y < 52.6), SKIN_DK, 0.35)  # jaw hinge
    elif n == 'south':
        for yy in (50.6, 51.6):
            put(rgb, (np.abs(y - yy) < 0.26) & (ax < 3.8), SKIN_DK, 0.4)  # neck creases
    elif n == 'up':
        put(rgb, (np.abs(z + 1.0) < 2.2) & (ax < 2.0), SKIN_HI, 0.2)  # bald highlight


def chin_fn(face, rgb, glow, rs):
    vgrad(face, rgb, 1.0, 0.82)
    x, y, z = face.world()
    ax = np.abs(x)
    tag = face.cube.tag
    if tag == 'jaw' and face.name == 'north':
        fold = (y > 51.4) & (y < 53.5) & (np.abs(ax - (1.4 + (53.5 - y) * 0.75)) < 0.28)
        put(rgb, fold, SKIN_DK, 0.55)  # nasolabial folds
        put(rgb, (np.abs(y - 52.25) < 0.26) & (ax < 2.4), '#35203f', 0.85)  # mouth
        put(rgb, (np.abs(y - 52.75) < 0.26) & (ax < 1.8), SKIN_HI, 0.2)  # upper lip
        put(rgb, (np.abs(y - 51.75) < 0.26) & (ax < 2.0), SKIN_DK, 0.25)  # lower lip shadow
        for gx in (-1.3, 0.0, 1.3):
            put(rgb, (np.abs(x - gx) < 0.26) & (y < 51.2), SKIN_DK, 0.5)
    if tag == 'jaw' and face.name in ('east', 'west'):
        put(rgb, (np.abs(-z - 1.0) < 0.26) & (y < 52.5), SKIN_DK, 0.35)  # jaw hinge
    if tag == 'chin' and face.name in ('north', 'down'):
        for gx in (-2.6, -1.3, 0.0, 1.3, 2.6):
            put(rgb, np.abs(x - gx) < 0.26, SKIN_DK, 0.7)  # the ridged chin
    if tag == 'chin' and face.name in ('east', 'west'):
        put(rgb, y < 49.4, SKIN_DK, 0.3)


def ear_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], SKIN_DK, 0.25)
    if face.name in ('east', 'west') and face.w > 2 and face.h > 2:
        put(rgb, np.s_[1:-1, 1:-1], SKIN_DK, 0.4)


# ------------------------------------------------------------------ cloth / armour
def suit_fn(face, rgb, glow, rs):
    vgrad(face, rgb, 1.08, 0.82)
    speckle(face, rgb, rs, SUIT_DK, 0.05, 0.35)
    tag = face.cube.tag
    x, y, z = face.world()
    n = face.name
    if tag == 'chest' and n == 'north':
        ax = np.abs(x)
        depth = y - 41.0
        vee = (depth > 0) & (ax < depth * 0.95)
        put(rgb, vee, SKIN)
        put(rgb, vee & (y > 46.5), SKIN_DK, 0.25)
        trim = (depth > -0.6) & (np.abs(ax - depth * 0.95) < 0.62)
        put(rgb, trim, GOLD)
        put(rgb, trim & (np.abs(ax - depth * 0.95) < 0.25), GOLD_HI, 0.6)
        pec = (np.abs(y - 40.3) < 0.26) & (ax > 1.5) & (ax < 10.5)
        put(rgb, pec, SUIT_DK, 0.6)
        put(rgb, (ax < 0.26) & (y < 41), SUIT_DK, 0.6)
    if tag == 'chest' and n == 'south':
        put(rgb, np.abs(np.abs(x) - 5.0) < 0.26, SUIT_DK, 0.45)  # shoulder-blade seams
    if tag == 'chest' and n in ('east', 'west'):
        put(rgb, np.abs(y - 40.3) < 0.26, SUIT_DK, 0.4)
    if tag == 'abdomen' and n in SIDE:
        ribs = (np.floor(y * 2).astype(int) % 3) == 0
        put(rgb, ribs, SUIT_DK, 0.55)
        put(rgb, (np.abs(x) < 0.26) & (n == 'north'), SUIT_DK, 0.6)
    if tag == 'kilt' and n in SIDE:
        u, v = face.grid()
        edge = v > 0.88
        put(rgb, edge, GOLD)
        put(rgb, edge & (v > 0.93), GOLD_DK, 0.5)
        put(rgb, (np.floor(u * face.w).astype(int) % 4 == 0) & ~edge, SUIT_DK, 0.35)  # pleats
    if tag == 'pants' and n in SIDE:
        u, _ = face.grid()
        put(rgb, np.abs(u - 0.5) < 0.5 / max(1, face.w), SUIT_DK, 0.35)  # seam


def boot_fn(face, rgb, glow, rs):
    vgrad(face, rgb, 1.1, 0.8)
    speckle(face, rgb, rs, '#0e1016', 0.06, 0.4)
    if face.cube.tag == 'boot_top' and face.name in SIDE:
        _, v = face.grid()
        put(rgb, v < 0.16, GOLD)
        put(rgb, v < 0.07, GOLD_HI, 0.5)


def gold_fn(face, rgb, glow, rs):
    vgrad(face, rgb, 1.14, 0.8)
    if face.w > 2 and face.h > 2:
        put(rgb, np.s_[0, :], GOLD_HI, 0.55)
        put(rgb, np.s_[-1, :], GOLD_DK, 0.55)
    speckle(face, rgb, rs, GOLD_HI, 0.035, 0.7)
    speckle(face, rgb, rs, GOLD_DK, 0.03, 0.4)
    tag = face.cube.tag
    x, y, z = face.world()
    if tag == 'ribbed' and face.name in SIDE:
        put(rgb, (np.floor(y * 2).astype(int) % 3) == 0, GOLD_DK, 0.5)
    if tag == 'gauntlet' and face.name == 'east':
        u, v = face.grid()  # back of the hand: engraved border and rays round the Mind socket
        border = (np.abs(u - 0.5) > 0.4) | (np.abs(v - 0.5) > 0.4)
        put(rgb, border & ~((np.abs(u - 0.5) > 0.46) | (np.abs(v - 0.5) > 0.46)), GOLD_DK, 0.6)
        ray = (np.abs(np.abs(z) - np.abs(y - 24.5)) < 0.22) & (np.abs(z) > 1.3) & (np.abs(z) < 3.1)
        put(rgb, ray, GOLD_DK, 0.5)
    if tag == 'knuckles' and face.name in SIDE:
        for kz in (-2.1, 0.0, 2.1):
            put(rgb, np.abs(z - kz) < 0.2, GOLD_DK, 0.6)
    if tag == 'finger' and face.name in SIDE:
        _, v = face.grid()
        put(rgb, v > 0.84, GOLD_DK, 0.45)  # joint line
    if tag == 'buckle' and face.name == 'north':
        u, v = face.grid()
        put(rgb, (np.abs(u - 0.5) < 0.3) & (np.abs(v - 0.5) < 0.28), GOLD_DK, 0.55)
        put(rgb, (np.abs(u - 0.5) < 0.18) & (np.abs(v - 0.5) < 0.14), GOLD_HI, 0.7)
    if tag == 'knee' and face.name == 'north':
        u, v = face.grid()
        put(rgb, np.abs(u - 0.5) < 0.08, GOLD_HI, 0.5)


def socket_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], GOLD_DK, 0.55)
    if face.w > 2 and face.h > 2:
        put(rgb, np.s_[1:-1, 1:-1], '#3b2c08', 0.75)


def gem_mat(rgb_t):
    base = '#%02x%02x%02x' % tuple(int(c * 0.62) for c in rgb_t)

    def fn(face, rgb, glow, rs):
        u, v = face.grid()
        put(rgb, (u + v) < 0.9, '#%02x%02x%02x' % rgb_t, 0.55)
        put(rgb, (u + v) > 1.45, '#000000', 0.35)
        rgb[0, 0] = rgb[0, 0] * 0.3 + np.array([255, 255, 255]) * 0.7  # specular glint
        glow[..., :3] = np.array(rgb_t, float) * 0.8
        glow[..., 3] = 255
        glow[(u + v) > 1.45, 3] = 170
    return Mat(base, noise=4, edge=0.1, fn=fn, grain=1)


def halo_mat(rgb_t, k=0.34):
    def fn(face, rgb, glow, rs):
        glow[..., :3] = np.array(rgb_t, float) * k
        glow[..., 3] = 255
    return Mat('#000000', noise=0, edge=0.0, fn=fn, alpha=0)


MAT = {
    'skin': Mat(SKIN, noise=5, edge=0.1, fn=skin_fn),
    'head': Mat(SKIN, noise=4, edge=0.08, fn=head_fn),
    'chin': Mat(SKIN, noise=4, edge=0.12, fn=chin_fn),
    'ear': Mat(SKIN, noise=3, edge=0.1, fn=ear_fn),
    'suit': Mat(SUIT, noise=5, edge=0.18, fn=suit_fn),
    'pants': Mat(PANTS, noise=5, edge=0.15, fn=suit_fn),
    'boot': Mat(BOOT, noise=4, edge=0.2, fn=boot_fn),
    'gold': Mat(GOLD, noise=9, edge=0.22, fn=gold_fn, grain=1),
    'socket': Mat(GOLD_DK, noise=3, edge=0.0, fn=socket_fn, grain=1),
}
for _k, _c in STONES.items():
    MAT['gem_' + _k] = gem_mat(_c)
    MAT['halo_' + _k] = halo_mat(_c, 0.26)
    MAT['haze_' + _k] = halo_mat(_c, 0.12)


# ------------------------------------------------------------------ geometry
def box(m, bone, x0, y0, z0, x1, y1, z1, mat, **kw):
    """Cube from two corners (model px)."""
    return m.cube(bone, [x0, y0, z0], [x1 - x0, y1 - y0, z1 - z0], MAT[mat] if isinstance(mat, str) else mat, **kw)


def stone(m, parent, key, cy, cz, cx, size=(1.5, 1.4), depth=0.9):
    """Socket + faceted gem + glow halo on the +X (back) face of the gauntlet at x = cx."""
    b = 'stone_' + key
    m.bone(b, parent, [cx, cy, cz])
    sy, sz = size
    box(m, b, cx - 0.05, cy - sy / 2 - 0.3, cz - sz / 2 - 0.3, cx + 0.35, cy + sy / 2 + 0.3, cz + sz / 2 + 0.3, 'socket')
    box(m, b, cx, cy - sy / 2, cz - sz / 2, cx + depth, cy + sy / 2, cz + sz / 2, 'gem_' + key)
    d = min(sy, sz) * 0.72
    box(m, b, cx, cy - d / 2, cz - d / 2, cx + depth * 0.82, cy + d / 2, cz + d / 2, 'gem_' + key,
        rotation=[45, 0, 0], pivot=[cx, cy, cz])
    for pad, mat in ((0.25, 'halo_'), (0.55, 'haze_')):
        box(m, b, cx - 0.1, cy - sy / 2 - pad, cz - sz / 2 - pad, cx + depth + pad, cy + sy / 2 + pad, cz + sz / 2 + pad,
            mat + key)


def build():
    m = Model('thanos', density=2, width=256, seed=11)
    m.bone('root', None, [0, 0, 0])
    # ---- waist, belt, tassets
    m.bone('waist', 'root', [0, 29, 0])
    box(m, 'waist', -9.5, 24, -5.25, 9.5, 31, 5.25, 'pants', tag='pants')
    box(m, 'waist', -9.75, 28.5, -5.75, 9.75, 31.5, 5.75, 'gold', tag='ribbed')
    box(m, 'waist', -2.5, 28, -6.4, 2.5, 32, -5.75, 'gold', tag='buckle')
    box(m, 'waist', -10.25, 22, -4, -9.25, 29, 4, 'suit', tag='kilt')
    box(m, 'waist', 9.25, 22, -4, 10.25, 29, 4, 'suit', tag='kilt')
    m.bone('kilt_front', 'waist', [0, 29, -5.75])
    box(m, 'kilt_front', -6.5, 20, -6.25, -0.4, 29, -5.5, 'suit', tag='kilt')
    box(m, 'kilt_front', 0.4, 20, -6.25, 6.5, 29, -5.5, 'suit', tag='kilt')
    m.bone('kilt_back', 'waist', [0, 29, 5.75])
    box(m, 'kilt_back', -8, 19.5, 5.5, 8, 29, 6.25, 'suit', tag='kilt')
    # ---- torso
    m.bone('torso', 'waist', [0, 32, 0])
    box(m, 'torso', -9, 31, -5.5, 9, 38, 5.5, 'suit', tag='abdomen')
    box(m, 'torso', -12, 37.5, -7, 12, 48, 6.5, 'suit', tag='chest')
    box(m, 'torso', -10, 47.5, -4, 10, 49.5, 4.5, 'suit', tag='traps')
    box(m, 'torso', -2, 38, 6.5, 2, 47, 7.0, 'gold', tag='ribbed')  # spine plate
    box(m, 'torso', -11.5, 47.6, -7.2, -8.5, 49.8, 6.8, 'gold')  # shoulder straps
    box(m, 'torso', 8.5, 47.6, -7.2, 11.5, 49.8, 6.8, 'gold')
    m.bone('neck', 'torso', [0, 48.5, -0.5])
    box(m, 'neck', -4.5, 47, -4.5, 4.5, 50.5, 3.5, 'skin', tag='neck')
    # ---- head (small on the huge body, the famous chin)
    m.bone('head', 'neck', [0, 49.5, -0.5])
    box(m, 'head', -5, 51, -5.5, 5, 60, 4.25, 'head')
    box(m, 'head', -4.5, 59.75, -4.5, 4.5, 60.5, 3.5, 'head')  # rounded crown
    box(m, 'head', -5.5, 49.5, -5.75, 5.5, 53.5, 1.5, 'chin', tag='jaw')  # wide jaw, mouth
    box(m, 'head', -3.75, 48.25, -6.6, 3.75, 51, -3, 'chin', tag='chin')  # ridged chin
    box(m, 'head', -4.75, 55.5, -6.0, 4.75, 56.5, -5.5, 'chin', tag='brow')  # heavy brow
    box(m, 'head', -0.9, 52.75, -6.1, 0.9, 55.25, -5.5, 'head', tag='nose')
    box(m, 'head', -5.5, 52.5, -0.5, -5.0, 55.5, 1.5, 'ear')
    box(m, 'head', 5.0, 52.5, -0.5, 5.5, 55.5, 1.5, 'ear')
    # ---- arms
    for side, s in (('right', -1), ('left', 1)):
        def X(a, b, s=s):
            return (min(s * a, s * b), max(s * a, s * b))
        arm, fore, hand = side + '_arm', side + '_forearm', side + '_hand'
        m.bone(arm, 'torso', [s * 16.5, 46, 0])
        x0, x1 = X(12.25, 20.75)
        box(m, arm, x0, 36, -4.25, x1, 47, 4.25, 'skin', tag='upper_arm')
        x0, x1 = X(12.0, 21.0)
        box(m, arm, x0, 43, -4.5, x1, 48.5, 4.5, 'skin', tag='upper_arm')  # deltoid
        x0, x1 = X(13.5, 19.5)
        box(m, arm, x0, 38.5, -4.75, x1, 44.5, -4.25, 'skin', tag='upper_arm')  # biceps
        x0, x1 = X(13.0, 21.5)
        box(m, arm, x0, 46.8, -4.8, x1, 48.3, 4.8, 'gold', rotation=[0, 0, -s * 14], pivot=[s * 16.5, 47.5, 0])
        m.bone(fore, arm, [s * 16.5, 36.5, 0])
        x0, x1 = X(12.75, 20.25)
        box(m, fore, x0, 27.5, -3.75, x1, 37, 3.75, 'skin', tag='forearm')
        x0, x1 = X(12.5, 20.5)
        box(m, fore, x0, 32, -4, x1, 36.5, 4, 'skin', tag='forearm')
        if side == 'left':  # gauntlet cuff with a flared rim
            box(m, fore, 12.5, 27, -4.1, 20.5, 31.5, 4.1, 'gold', tag='ribbed')
            box(m, fore, 12.2, 30.8, -4.4, 20.8, 31.8, 4.4, 'gold')
        else:  # gold bracer
            box(m, fore, -20.6, 28.2, -3.95, -12.4, 31.8, 3.95, 'gold', tag='ribbed')
        m.bone(hand, fore, [s * 16.5, 27, 0])
    # right hand: bare, fingers in two blocks, thumb
    box(m, 'right_hand', -18.5, 21.5, -3.75, -14.5, 27, 3.75, 'skin')
    m.bone('right_fingers', 'right_hand', [-16.5, 21.5, 0])
    box(m, 'right_fingers', -18.25, 18.5, -3.6, -14.75, 21.5, 3.6, 'skin')
    m.bone('right_fingers_tip', 'right_fingers', [-16.5, 18.5, 0])
    box(m, 'right_fingers_tip', -18.1, 16.3, -3.5, -14.9, 18.5, 3.5, 'skin')
    m.bone('right_thumb', 'right_hand', [-16.0, 24.5, -3.75])
    box(m, 'right_thumb', -17.25, 21, -5.5, -14.75, 24.5, -3.5, 'skin')
    # left hand: the Infinity Gauntlet
    box(m, 'left_hand', 14.25, 21, -4.25, 18.75, 27, 4.25, 'gold', tag='gauntlet')
    box(m, 'left_hand', 14.0, 20.6, -4.4, 19.0, 22.0, 4.4, 'gold', tag='knuckles')
    box(m, 'left_hand', 18.75, 22.2, -3.5, 19.25, 26.6, 3.5, 'gold')  # back plate
    stone(m, 'left_hand', 'mind', 24.5, 0.0, 19.25, size=(2.0, 2.0), depth=1.0)
    fingers = (('index', -3.15, 3.0, 2.6, 'space'), ('middle', -1.05, 3.2, 2.8, 'reality'),
               ('ring', 1.05, 3.1, 2.7, 'power'), ('pinky', 3.15, 2.6, 2.3, 'soul'))
    for name, cz, l1, l2, key in fingers:
        b = 'left_' + name
        m.bone(b, 'left_hand', [16.5, 21, cz])
        box(m, b, 14.75, 21 - l1, cz - 1.0, 18.25, 21, cz + 1.0, 'gold', tag='finger')
        m.bone(b + '_tip', b, [16.5, 21 - l1, cz])
        box(m, b + '_tip', 14.9, 21 - l1 - l2, cz - 0.9, 18.1, 21 - l1, cz + 0.9, 'gold', tag='finger')
        stone(m, b, key, 21 - l1 * 0.48, cz, 18.25, size=(1.5, 1.4), depth=0.85)
    m.bone('left_thumb', 'left_hand', [16.0, 24.5, -4.0])
    box(m, 'left_thumb', 14.75, 20.5, -5.9, 17.25, 24.5, -3.9, 'gold', tag='finger')
    m.bone('left_thumb_tip', 'left_thumb', [16.0, 20.5, -4.9])
    box(m, 'left_thumb_tip', 14.9, 17.9, -5.8, 17.1, 20.5, -4.0, 'gold', tag='finger')
    stone(m, 'left_thumb', 'time', 22.4, -4.9, 17.25, size=(1.5, 1.3), depth=0.85)
    # ---- legs
    for side, s in (('right', -1), ('left', 1)):
        def X(a, b, s=s):
            return (min(s * a, s * b), max(s * a, s * b))
        leg, shin, foot = side + '_leg', side + '_shin', side + '_foot'
        m.bone(leg, 'root', [s * 5.5, 29, 0])
        x0, x1 = X(1.5, 9.5)
        box(m, leg, x0, 16, -4, x1, 29, 4, 'pants', tag='pants')
        m.bone(shin, leg, [s * 5.5, 16, 0])
        x0, x1 = X(1.75, 9.25)
        box(m, shin, x0, 3, -3.75, x1, 16.5, 3.75, 'pants', tag='pants')
        x0, x1 = X(1.5, 9.5)
        box(m, shin, x0, 3, -4, x1, 9.5, 4, 'boot', tag='boot_top')
        x0, x1 = X(2.25, 8.75)
        box(m, shin, x0, 14, -4.5, x1, 18, -3.75, 'gold', tag='knee')
        x0, x1 = X(2.75, 8.25)
        box(m, shin, x0, 18, -4.25, x1, 18.75, -3.5, 'gold')
        m.bone(foot, shin, [s * 5.5, 3, 0])
        x0, x1 = X(1.5, 9.5)
        box(m, foot, x0, 0.5, -7, x1, 3.5, 4, 'boot')
        x0, x1 = X(1.4, 9.6)
        box(m, foot, x0, 0, -7.2, x1, 0.75, 4.2, 'boot')
        x0, x1 = X(2.0, 9.0)
        box(m, foot, x0, 0.75, -7.45, x1, 3.0, -6.5, 'gold')
    return m


def write_indexed(rel, arr):
    """Palette PNG (<= 256 colours, alpha kept): the noisy atlas drops from ~93 KB to ~20 KB, so the
    base64 file fits one GitHub request. Minecraft decodes indexed PNGs to RGBA (stb_image)."""
    import base64
    import io
    from PIL import Image
    from jinwoo_geo import BIN
    img = Image.fromarray(arr, 'RGBA')
    q = img.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    buf = io.BytesIO()
    q.save(buf, 'PNG', optimize=True)
    back = np.array(Image.open(io.BytesIO(buf.getvalue())).convert('RGBA'))
    assert ((back[..., 3] > 0) == (arr[..., 3] > 0)).all(), 'alpha changed by the palette'
    out = os.path.join(BIN, rel + '.b64')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w') as fh:
        fh.write(base64.encodebytes(buf.getvalue()).decode('ascii'))
    plain = os.path.join(ROOT, 'build', 'jinwoo_preview', rel)
    os.makedirs(os.path.dirname(plain), exist_ok=True)
    Image.fromarray(back, 'RGBA').save(plain)
    return len(buf.getvalue())


def main():
    m = build()
    base, glow = m.paint()
    m.write('geo/thanos/thanos.geo.json', 'textures/entity/thanos/thanos.png', 'textures/entity/thanos/thanos_glow.png', base, glow)
    size = write_indexed('textures/entity/thanos/thanos.png', base)
    print('indexed atlas: %d bytes' % size)
    cubes = sum(len(b.cubes) for b in m.bones)
    print('thanos: %d bones, %d cubes, atlas %dx%d' % (len(m.bones), cubes, m.width, m.height))
    if '--preview' in sys.argv:
        preview()


def preview():
    import subprocess
    out = os.path.join(ROOT, 'build', 'thanos-assets')
    os.makedirs(out, exist_ok=True)
    geo = os.path.join(ROOT, 'viltrumitecore/src/main/resources/assets/viltrumitecore/geo/thanos/thanos.geo.json')
    tex = os.path.join(ROOT, 'build/jinwoo_preview/textures/entity/thanos/thanos.png')
    glow = os.path.join(ROOT, 'build/jinwoo_preview/textures/entity/thanos/thanos_glow.png')
    subprocess.run([sys.executable, os.path.join(ROOT, 'tools/preview_geo.py'), os.path.join(out, 'thanos_rest.png'),
                    '%s:%s:%s' % (geo, tex, glow), '--scale', '6', '--views', 'front,side,back'], check=True)


if __name__ == '__main__':
    main()
