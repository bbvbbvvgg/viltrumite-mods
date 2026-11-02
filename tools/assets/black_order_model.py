#!/usr/bin/env python3
"""Black Order (own work, CC0, MCU-inspired look, no external sources): Ebony Maw and Cull Obsidian.

Ebony Maw: 34 model px tall (2.1 blocks, entity box 0.7 x 2.1): very thin, long-limbed, pale grey
skin, a high elongated skull swept back, small dark eyes, long fingers; a floor-length dark coat with
a high stiff collar, silver-grey trims and a dark red sash. He floats (legs hang under the coat).
Cull Obsidian: 53 px tall (3.3 blocks, box 1.5 x 3.3): a hunched giant, dark obsidian-brown hide
with tan bony plates, a small head under a heavy bony crest with two horns, glowing orange eyes,
huge arms in banded bracers; the right hand holds his hammer-axe (bone weapon > weapon_head, the
head flies on the chain in the CHAIN move); a strap harness over the chest.

Rigs (bone names are a contract with black_order_clips.py and BlackOrderRenderer):
Maw: root > waist > {robe_front, robe_back, torso > {neck > head, collar, right_arm > right_forearm
     > right_hand > right_fingers, left_arm > left_forearm > left_hand > left_fingers}};
     root > {right,left}_leg > _shin.
Cull: root > waist > torso > {neck > head > jaw, right_arm > right_forearm > right_hand > weapon >
     weapon_head, left_arm > left_forearm > left_hand}; root > {right,left}_leg > _shin > _foot.
Front is north (-z), like the Thanos model. Conventions as thanos_model.py.
Outputs: geo/thanos/{ebony_maw,cull_obsidian}.geo.json, binassets textures/entity/thanos/<name>.png(.b64)
and <name>_glow.png(.b64). Run: python3 tools/assets/black_order_model.py
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_geo import Mat, Model, put  # noqa: E402
from thanos_model import write_indexed  # noqa: E402

SIDE = ('north', 'south', 'east', 'west')


def vgrad(face, rgb, top=1.06, bottom=0.86):
    if face.name in SIDE:
        _, v = face.grid()
        rgb *= (top + (bottom - top) * v)[..., None]


def speckle(face, rgb, rs, color, p=0.04, a=0.35):
    put(rgb, rs.uniform(0, 1, (face.h, face.w)) < p, color, a)


# ================================================================== Ebony Maw
M_SKIN, M_SKIN_DK, M_SKIN_HI = '#aaa59b', '#7b766d', '#c9c5bc'
M_ROBE, M_ROBE_HI, M_ROBE_DK = '#26211e', '#3a332e', '#141110'
M_TRIM, M_TRIM_DK = '#8d8a83', '#55534e'
M_SASH = '#5a1f1c'
TK = (150, 190, 255)


def maw_skin(face, rgb, glow, rs):
    vgrad(face, rgb, 1.06, 0.88)
    speckle(face, rgb, rs, M_SKIN_DK, 0.05, 0.25)
    if face.cube.tag == 'fingers':
        _, v = face.grid()
        put(rgb, np.abs(v - 0.5) < 0.12, M_SKIN_DK, 0.4)  # knuckle line
        if face.name in ('down', 'north', 'south', 'east', 'west'):
            tip = v > 0.8
            glow[tip] = [TK[0] // 3, TK[1] // 3, TK[2] // 3, 255]  # telekinesis gathers at the fingertips


def maw_head(face, rgb, glow, rs):
    vgrad(face, rgb, 1.08, 0.9)
    speckle(face, rgb, rs, M_SKIN_DK, 0.04, 0.2)
    x, y, z = face.world()
    ax = np.abs(x)
    n = face.name
    tag = face.cube.tag
    if tag == 'face' and n == 'north':
        put(rgb, (y > 31.2) & (y < 32.2) & (ax > 0.5) & (ax < 1.9), '#0d0b0a', 0.95)  # small dark eyes
        put(rgb, (y > 32.1) & (y < 32.6) & (ax > 0.3) & (ax < 2.2), M_SKIN_DK, 0.55)  # brow ridge
        put(rgb, (ax < 0.35) & (y > 29.6) & (y < 31.3), M_SKIN_DK, 0.45)  # flat nose ridge
        put(rgb, (np.abs(y - 28.8) < 0.22) & (ax < 1.1), '#3a2f2c', 0.85)  # thin mouth
        put(rgb, (np.abs(ax - 2.0) < 0.2) & (y < 30.0), M_SKIN_DK, 0.35)  # cheek lines
    if tag == 'skull' and n in SIDE:
        for yy in (34.0, 35.5, 37.0):
            put(rgb, np.abs(y - yy) < 0.2, M_SKIN_DK, 0.3)  # ridges of the long skull


def maw_robe(face, rgb, glow, rs):
    vgrad(face, rgb, 1.1, 0.82)
    speckle(face, rgb, rs, M_ROBE_DK, 0.06, 0.4)
    tag = face.cube.tag
    if face.name in SIDE:
        u, v = face.grid()
        if tag in ('skirt', 'coat'):
            folds = (np.floor(u * face.w).astype(int) % 3 == 0)
            put(rgb, folds, M_ROBE_HI, 0.25)
        if tag == 'skirt':
            put(rgb, v > 0.92, M_TRIM, 0.8)  # silver hem
        if tag == 'coat' and face.name == 'north':
            put(rgb, np.abs(u - 0.5) < 0.06, M_TRIM, 0.7)  # front closure


def maw_trim(face, rgb, glow, rs):
    vgrad(face, rgb, 1.15, 0.8)
    speckle(face, rgb, rs, M_TRIM_DK, 0.08, 0.5)


def maw_sash(face, rgb, glow, rs):
    vgrad(face, rgb, 1.05, 0.85)
    speckle(face, rgb, rs, '#2e0f0e', 0.06, 0.4)


MAW_MAT = {
    'skin': Mat(M_SKIN, noise=4, edge=0.1, fn=maw_skin),
    'head': Mat(M_SKIN, noise=4, edge=0.08, fn=maw_head),
    'robe': Mat(M_ROBE, noise=5, edge=0.18, fn=maw_robe),
    'trim': Mat(M_TRIM, noise=6, edge=0.2, fn=maw_trim, grain=1),
    'sash': Mat(M_SASH, noise=5, edge=0.15, fn=maw_sash),
}


def mbox(m, bone, x0, y0, z0, x1, y1, z1, mat, **kw):
    return m.cube(bone, [x0, y0, z0], [x1 - x0, y1 - y0, z1 - z0], MAW_MAT[mat], **kw)


def build_maw():
    m = Model('ebony_maw', density=4, width=256, seed=23)
    m.bone('root', None, [0, 0, 0])
    m.bone('waist', 'root', [0, 15, 0])
    mbox(m, 'waist', -3.2, 13.5, -1.9, 3.2, 16.5, 1.9, 'robe', tag='coat')
    mbox(m, 'waist', -3.4, 14.8, -2.1, 3.4, 15.8, 2.1, 'sash')
    # the long coat: front and back panels hang from the waist (they sway), side panels stay
    m.bone('robe_front', 'waist', [0, 15, -2.0])
    mbox(m, 'robe_front', -3.3, 1.0, -2.4, 3.3, 15.0, -1.8, 'robe', tag='skirt')
    m.bone('robe_back', 'waist', [0, 15, 2.0])
    mbox(m, 'robe_back', -3.5, 0.6, 1.8, 3.5, 15.0, 2.5, 'robe', tag='skirt')
    mbox(m, 'waist', -3.6, 2.0, -1.8, -3.0, 14.0, 1.8, 'robe', tag='skirt')
    mbox(m, 'waist', 3.0, 2.0, -1.8, 3.6, 14.0, 1.8, 'robe', tag='skirt')
    # torso: narrow chest in the coat
    m.bone('torso', 'waist', [0, 16, 0])
    mbox(m, 'torso', -3.0, 16.0, -1.7, 3.0, 24.5, 1.7, 'robe', tag='coat')
    mbox(m, 'torso', -3.6, 22.0, -2.0, 3.6, 25.0, 2.0, 'robe', tag='coat')  # shoulders
    mbox(m, 'torso', -0.9, 19.5, -1.95, 0.9, 24.2, -1.7, 'trim')  # silver chest plate
    mbox(m, 'torso', -3.7, 24.6, -2.1, 3.7, 25.2, 2.1, 'trim')  # shoulder seam
    # the high stiff collar (behind and beside the neck)
    m.bone('collar', 'torso', [0, 25, 0])
    mbox(m, 'collar', -2.6, 24.8, 1.2, 2.6, 29.5, 2.0, 'robe', tag='collar')
    mbox(m, 'collar', -2.9, 24.8, -1.2, -2.2, 28.5, 1.6, 'robe', tag='collar')
    mbox(m, 'collar', 2.2, 24.8, -1.2, 2.9, 28.5, 1.6, 'robe', tag='collar')
    mbox(m, 'collar', -2.7, 29.2, 1.15, 2.7, 29.7, 2.1, 'trim')
    m.bone('neck', 'torso', [0, 25, 0])
    mbox(m, 'neck', -0.8, 24.6, -0.9, 0.8, 27.4, 0.7, 'skin')
    # head: narrow face, the skull rises high and back
    m.bone('head', 'neck', [0, 27, 0])
    mbox(m, 'head', -2.3, 27.0, -2.4, 2.3, 33.0, 1.6, 'head', tag='face')
    mbox(m, 'head', -2.0, 32.5, -1.8, 2.0, 37.5, 2.4, 'head', tag='skull', rotation=[-18, 0, 0], pivot=[0, 32.5, 0])
    mbox(m, 'head', -1.5, 36.0, 0.2, 1.5, 38.6, 3.4, 'head', tag='skull', rotation=[-28, 0, 0], pivot=[0, 36, 1])
    mbox(m, 'head', -1.2, 26.6, -2.2, 1.2, 27.6, 0.4, 'head', tag='face')  # narrow chin
    mbox(m, 'head', -2.5, 29.5, -0.6, -2.3, 31.5, 0.8, 'head')  # small ears
    mbox(m, 'head', 2.3, 29.5, -0.6, 2.5, 31.5, 0.8, 'head')
    # arms: long and thin, coat sleeves to the wrist
    for side, s in (('right', -1), ('left', 1)):
        def X(a, b, s=s):
            return (min(s * a, s * b), max(s * a, s * b))
        arm, fore, hand, fing = side + '_arm', side + '_forearm', side + '_hand', side + '_fingers'
        m.bone(arm, 'torso', [s * 4.2, 24, 0])
        x0, x1 = X(3.2, 5.2)
        mbox(m, arm, x0, 16.4, -1.0, x1, 24.6, 1.0, 'robe', tag='sleeve')
        m.bone(fore, arm, [s * 4.2, 16.6, 0])
        x0, x1 = X(3.3, 5.1)
        mbox(m, fore, x0, 9.8, -0.9, x1, 16.8, 0.9, 'robe', tag='sleeve')
        x0, x1 = X(3.15, 5.25)
        mbox(m, fore, x0, 9.6, -1.05, x1, 10.6, 1.05, 'trim')  # cuff
        m.bone(hand, fore, [s * 4.2, 9.7, 0])
        x0, x1 = X(3.5, 4.9)
        mbox(m, hand, x0, 7.6, -0.8, x1, 9.8, 0.8, 'skin')
        m.bone(fing, hand, [s * 4.2, 7.7, 0])
        x0, x1 = X(3.6, 4.8)
        mbox(m, fing, x0, 4.4, -0.75, x1, 7.8, 0.75, 'skin', tag='fingers')
        x0, x1 = X(3.2, 3.7)
        mbox(m, hand, x0, 7.2, -1.3, x1, 9.0, -0.5, 'skin', tag='fingers')  # thumb
    # legs under the coat (seen when he floats)
    for side, s in (('right', -1), ('left', 1)):
        def X(a, b, s=s):
            return (min(s * a, s * b), max(s * a, s * b))
        leg, shin = side + '_leg', side + '_shin'
        m.bone(leg, 'root', [s * 1.4, 14, 0])
        x0, x1 = X(0.4, 2.4)
        mbox(m, leg, x0, 7.5, -0.9, x1, 14.2, 0.9, 'robe')
        m.bone(shin, leg, [s * 1.4, 7.5, 0])
        x0, x1 = X(0.5, 2.3)
        mbox(m, shin, x0, 0.8, -0.8, x1, 7.7, 0.8, 'robe')
        x0, x1 = X(0.4, 2.4)
        mbox(m, shin, x0, 0.0, -1.8, x1, 1.0, 0.9, 'trim')  # pointed shoes
    return m


# ================================================================== Cull Obsidian
C_HIDE, C_HIDE_DK, C_HIDE_HI = '#3d322c', '#251d19', '#57483f'
C_PLATE, C_PLATE_DK, C_PLATE_HI = '#7a6955', '#4d4234', '#a08d74'
C_STRAP = '#2b211c'
C_METAL, C_METAL_HI, C_METAL_DK = '#6e6c6b', '#b3b0aa', '#3e3c3b'
C_EYE = (255, 140, 48)


def cull_hide(face, rgb, glow, rs):
    vgrad(face, rgb, 1.07, 0.85)
    speckle(face, rgb, rs, C_HIDE_DK, 0.08, 0.45)
    speckle(face, rgb, rs, C_HIDE_HI, 0.04, 0.35)
    x, y, z = face.world()
    tag = face.cube.tag
    if tag == 'chest' and face.name == 'north':
        put(rgb, (np.abs(np.abs(x) - 4.5) < 0.3) & (y > 34), C_HIDE_DK, 0.5)  # pectoral split
        for yy in (27.0, 29.5, 32.0):
            put(rgb, (np.abs(y - yy) < 0.25) & (np.abs(x) < 4.5), C_HIDE_DK, 0.45)  # belly bands
    if tag == 'arm' and face.name in SIDE:
        put(rgb, (np.floor(y * 1.5).astype(int) % 4 == 0), C_HIDE_DK, 0.25)


def cull_plate(face, rgb, glow, rs):
    vgrad(face, rgb, 1.12, 0.8)
    speckle(face, rgb, rs, C_PLATE_DK, 0.07, 0.5)
    speckle(face, rgb, rs, C_PLATE_HI, 0.05, 0.4)
    if face.w > 2 and face.h > 2:
        put(rgb, np.s_[-1, :], C_PLATE_DK, 0.5)


def cull_head(face, rgb, glow, rs):
    vgrad(face, rgb, 1.05, 0.88)
    speckle(face, rgb, rs, C_HIDE_DK, 0.07, 0.4)
    x, y, z = face.world()
    ax = np.abs(x)
    if face.cube.tag == 'face' and face.name == 'north':
        eye = (y > 44.0) & (y < 45.0) & (ax > 1.0) & (ax < 2.6)
        put(rgb, eye, '#ffb070')
        glow[eye] = [C_EYE[0], C_EYE[1], C_EYE[2], 255]
        put(rgb, (y > 45.0) & (y < 45.8) & (ax < 3.2), C_PLATE_DK, 0.8)  # heavy brow
        put(rgb, (ax < 0.8) & (y > 42.0) & (y < 44.2), C_HIDE_DK, 0.5)  # broad nose


def cull_jaw(face, rgb, glow, rs):
    vgrad(face, rgb, 1.0, 0.82)
    speckle(face, rgb, rs, C_HIDE_DK, 0.08, 0.45)
    if face.name == 'north':
        u, v = face.grid()
        put(rgb, v < 0.3, '#1b1411', 0.85)  # mouth
        for k in range(1, 6):
            put(rgb, (v < 0.3) & (np.abs(u - k / 6.0) < 0.04), '#d9cdb6', 0.9)  # teeth


def cull_strap(face, rgb, glow, rs):
    vgrad(face, rgb, 1.1, 0.85)
    speckle(face, rgb, rs, '#120d0b', 0.08, 0.5)


def cull_metal(face, rgb, glow, rs):
    vgrad(face, rgb, 1.18, 0.78)
    speckle(face, rgb, rs, C_METAL_DK, 0.07, 0.5)
    speckle(face, rgb, rs, C_METAL_HI, 0.04, 0.6)
    if face.w > 2 and face.h > 2:
        put(rgb, np.s_[0, :], C_METAL_HI, 0.45)
    if face.cube.tag == 'edge':
        put(rgb, np.s_[:, :], C_METAL_HI, 0.35)
    if face.cube.tag == 'bands' and face.name in SIDE:
        _, v = face.grid()
        put(rgb, (np.floor(v * face.h).astype(int) % 3 == 0), C_METAL_DK, 0.55)


CULL_MAT = {
    'hide': Mat(C_HIDE, noise=6, edge=0.12, fn=cull_hide),
    'plate': Mat(C_PLATE, noise=8, edge=0.2, fn=cull_plate),
    'head': Mat(C_HIDE, noise=5, edge=0.1, fn=cull_head),
    'jaw': Mat(C_HIDE, noise=5, edge=0.12, fn=cull_jaw),
    'strap': Mat(C_STRAP, noise=5, edge=0.18, fn=cull_strap),
    'metal': Mat(C_METAL, noise=9, edge=0.22, fn=cull_metal, grain=1),
}


def cbox(m, bone, x0, y0, z0, x1, y1, z1, mat, **kw):
    return m.cube(bone, [x0, y0, z0], [x1 - x0, y1 - y0, z1 - z0], CULL_MAT[mat], **kw)


def build_cull():
    m = Model('cull_obsidian', density=2, width=256, seed=31)
    m.bone('root', None, [0, 0, 0])
    m.bone('waist', 'root', [0, 21, 0])
    cbox(m, 'waist', -7.5, 18.0, -5.0, 7.5, 24.0, 5.0, 'hide', tag='hips')
    cbox(m, 'waist', -8.0, 21.5, -5.5, 8.0, 24.0, 5.5, 'strap')  # belt
    cbox(m, 'waist', -3.0, 16.0, -6.0, 3.0, 23.5, -5.3, 'plate')  # loin plate
    # torso: huge, hunched forward (the bone leans in the clips)
    m.bone('torso', 'waist', [0, 23, 0])
    cbox(m, 'torso', -8.5, 23.0, -5.5, 8.5, 31.0, 5.5, 'hide', tag='chest')
    cbox(m, 'torso', -11.0, 30.0, -7.0, 11.0, 42.0, 6.5, 'hide', tag='chest')
    cbox(m, 'torso', -9.0, 41.5, -4.0, 9.0, 44.5, 6.0, 'hide')  # traps
    cbox(m, 'torso', -8.0, 32.0, 6.5, 8.0, 43.0, 8.0, 'plate')  # back plates
    cbox(m, 'torso', -6.0, 38.0, 8.0, 6.0, 44.0, 9.2, 'plate')
    cbox(m, 'torso', -12.0, 38.5, -7.5, -6.0, 44.0, 6.8, 'plate', rotation=[0, 0, 12], pivot=[-9, 41, 0])  # pauldrons
    cbox(m, 'torso', 6.0, 38.5, -7.5, 12.0, 44.0, 6.8, 'plate', rotation=[0, 0, -12], pivot=[9, 41, 0])
    # strap harness across the chest
    cbox(m, 'torso', -10.0, 33.0, -7.4, 10.0, 34.5, -6.9, 'strap', rotation=[0, 0, 28], pivot=[0, 34, -7])
    cbox(m, 'torso', -10.0, 33.0, -7.5, 10.0, 34.5, -7.0, 'strap', rotation=[0, 0, -28], pivot=[0, 34, -7])
    cbox(m, 'torso', -2.0, 32.5, -7.8, 2.0, 35.5, -7.2, 'metal')  # buckle
    m.bone('neck', 'torso', [0, 42, -3])
    cbox(m, 'neck', -3.5, 40.5, -6.5, 3.5, 44.0, -1.0, 'hide')
    # head: small, low, forward, under the crest
    m.bone('head', 'neck', [0, 43, -5])
    cbox(m, 'head', -3.5, 41.5, -9.5, 3.5, 47.0, -3.5, 'head', tag='face')
    cbox(m, 'head', -4.8, 45.5, -8.5, 4.8, 48.0, -1.0, 'plate', tag='crest')  # brow crest
    cbox(m, 'head', -5.5, 47.0, -6.5, 5.5, 50.0, 2.0, 'plate', tag='crest', rotation=[-22, 0, 0], pivot=[0, 47, -4])  # the great crest
    cbox(m, 'head', -1.0, 48.0, -10.5, 1.0, 49.5, -7.0, 'plate', rotation=[-30, 0, 0], pivot=[0, 48, -8])  # nose horn
    for s in (-1, 1):
        x0, x1 = (min(s * 3.6, s * 5.0), max(s * 3.6, s * 5.0))
        cbox(m, 'head', x0, 47.0, -11.0, x1, 48.6, -6.0, 'plate', rotation=[-24, s * -12, 0], pivot=[s * 4.3, 47.8, -7])  # brow horns
    m.bone('jaw', 'head', [0, 42.5, -5])
    cbox(m, 'jaw', -3.6, 40.0, -10.0, 3.6, 42.6, -4.0, 'jaw')
    # arms
    for side, s in (('right', -1), ('left', 1)):
        def X(a, b, s=s):
            return (min(s * a, s * b), max(s * a, s * b))
        arm, fore, hand = side + '_arm', side + '_forearm', side + '_hand'
        m.bone(arm, 'torso', [s * 12.5, 40, 0])
        x0, x1 = X(9.0, 16.0)
        cbox(m, arm, x0, 28.0, -3.8, x1, 42.0, 3.8, 'hide', tag='arm')
        x0, x1 = X(9.5, 15.5)
        cbox(m, arm, x0, 33.0, -4.3, x1, 39.0, 4.3, 'hide', tag='arm')  # biceps
        m.bone(fore, arm, [s * 12.5, 28.5, 0])
        x0, x1 = X(9.0, 16.0)
        cbox(m, fore, x0, 17.0, -4.0, x1, 29.0, 4.0, 'hide', tag='arm')
        x0, x1 = X(8.6, 16.4)
        cbox(m, fore, x0, 17.5, -4.4, x1, 24.5, 4.4, 'metal', tag='bands')  # banded bracer
        m.bone(hand, fore, [s * 12.5, 17.5, 0])
        x0, x1 = X(9.5, 15.5)
        cbox(m, hand, x0, 12.5, -3.2, x1, 17.8, 3.2, 'hide')
        x0, x1 = X(9.4, 15.6)
        cbox(m, hand, x0, 11.0, -3.0, x1, 13.0, 3.0, 'plate')  # knuckle plates
    # the hammer-axe in the right hand: haft along -z (forward) when the arm hangs, head at the end
    m.bone('weapon', 'right_hand', [-12.5, 14.5, 0])
    cbox(m, 'weapon', -13.3, 13.7, -16.0, -11.7, 15.3, 6.0, 'metal', tag='bands')  # haft
    cbox(m, 'weapon', -13.6, 13.4, 5.0, -11.4, 15.6, 7.0, 'metal', tag='edge')  # pommel
    m.bone('weapon_head', 'weapon', [-12.5, 14.5, -15])
    cbox(m, 'weapon_head', -14.5, 12.0, -21.0, -10.5, 17.0, -14.0, 'metal', tag='head')  # hammer block
    cbox(m, 'weapon_head', -14.0, 17.0, -20.0, -11.0, 24.0, -15.5, 'metal', tag='edge')  # axe blade up
    cbox(m, 'weapon_head', -13.8, 22.5, -21.0, -11.2, 24.5, -15.0, 'metal', tag='edge')
    cbox(m, 'weapon_head', -14.8, 13.0, -22.0, -10.2, 16.0, -21.0, 'metal')  # hammer face
    # legs
    for side, s in (('right', -1), ('left', 1)):
        def X(a, b, s=s):
            return (min(s * a, s * b), max(s * a, s * b))
        leg, shin, foot = side + '_leg', side + '_shin', side + '_foot'
        m.bone(leg, 'root', [s * 4.5, 20, 0])
        x0, x1 = X(0.8, 8.4)
        cbox(m, leg, x0, 10.0, -4.2, x1, 21.0, 4.2, 'hide', tag='leg')
        m.bone(shin, leg, [s * 4.5, 10.5, 0])
        x0, x1 = X(1.2, 8.0)
        cbox(m, shin, x0, 2.5, -3.8, x1, 11.0, 3.8, 'hide', tag='leg')
        x0, x1 = X(1.0, 8.2)
        cbox(m, shin, x0, 5.0, -4.3, x1, 11.5, -3.6, 'plate')  # shin guard
        m.bone(foot, shin, [s * 4.5, 2.5, 0])
        x0, x1 = X(0.6, 8.6)
        cbox(m, foot, x0, 0.0, -7.0, x1, 3.0, 4.0, 'hide')
        for k in range(3):
            tx = s * (1.8 + k * 2.4)
            cbox(m, foot, tx - 0.9, 0.0, -8.2, tx + 0.9, 1.6, -6.8, 'plate')  # claws
    return m


def main():
    out = {}
    for build, name in ((build_maw, 'ebony_maw'), (build_cull, 'cull_obsidian')):
        m = build()
        base, glow = m.paint()
        m.write('geo/thanos/%s.geo.json' % name, 'textures/entity/thanos/%s.png' % name, 'textures/entity/thanos/%s_glow.png' % name, base, glow)
        size = write_indexed('textures/entity/thanos/%s.png' % name, base)
        cubes = sum(len(b.cubes) for b in m.bones)
        out[name] = (len(m.bones), cubes, m.width, m.height, size)
        print('%s: %d bones, %d cubes, atlas %dx%d, indexed %d bytes' % (name, len(m.bones), cubes, m.width, m.height, size))
    return out


if __name__ == '__main__':
    main()
