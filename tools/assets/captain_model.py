"""Captain America (Steve Rogers, The Avengers 2012 suit) — body, gear, first-person arms and the vibranium shield.
Own procedural pixel art, CC0 (no film frames, no third-party textures). Rebuilt from scratch on the clean builder
(tools/assets/clean_geo.py) with Reinhard's model as the quality bar: many shaped parts instead of skin boxes, flat
saturated colours, 1 px gutters (no bleeding), no coplanar faces (geo_lint.py), per-face atlas.

Skeleton (same names / pivots as the player rig, so the renderer, the FP offset and the clip tools keep working):
root > waist > torso > head | right_arm > right_forearm > right_hand > right_fingers, right_thumb | left_* mirrored;
root > right_leg > right_shin, left_leg > left_shin. Gear repeats the names; `left_shield` (gear, left forearm) is
the empty bone the renderer draws the shield on.
Outputs geo/captain/{captain,captain_arms,captain_gear,captain_arms_gear,captain_shield}.geo.json and
textures/entity/captain/{captain,captain_skin,captain_gear,captain_gear_glow,captain_shield,captain_shield_glow}.png
(captain_skin.png = vanilla 64x64 layout for the vanilla hand / HeroSkins).
"""
import copy
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from clean_geo import (CleanModel, Flat, Face, box_mask, glow_put, mix, poly_mask, put, sides, star_mask,  # noqa: E402
                       write_json, write_model, write_png, RES)

D = 2
# ---- palette: bright, saturated 2012 suit --------------------------------------------------------------
BLUE, BLUE_HI, BLUE_LO, BLUE_DK = '#2c54b0', '#4470cc', '#1f3f8a', '#162d66'
RED, RED_HI, RED_LO = '#cc2331', '#e0414d', '#991a25'
WHITE, WHITE_LO = '#f3f4f8', '#ccd2dd'
SILVER, SILVER_LO = '#c8cfdb', '#98a1b0'
LEATHER, LEATHER_HI, LEATHER_LO, LEATHER_DK = '#7d4b27', '#9a6339', '#5d351a', '#432512'
SKIN, SKIN_HI, SKIN_LO, SKIN_SH = '#efc4a0', '#f7d4b6', '#d9a582', '#c38e6c'
SCLERA, IRIS, PUPIL, LASH = '#f8f8fa', '#3d72c9', '#1b2a4a', '#5a3b28'
LIP, BROW = '#b9766a', '#8a6238'
STEEL, STEEL_HI, STEEL_LO = '#9aa4b2', '#c3cbd6', '#6f7887'
GLOVE_ARMS = ('fingers', 'hand', 'thumb')

LIGHT = (32.0, 0.0, 0.05)  # model-wide vertical light: +5 % at the crown .. -5 % at the soles


# ============================================================================================ painters
FACE_MAP = [  # 16 x 16 px front of the head (rows 0-6 sit under the helmet); see FACE_PAL
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    'ssssssssssssssss',
    'sssbbbbssbbbbsss',
    'ssssWIssssIWssss',
    'ssssWPssssPWssss',
    'ssssssssssssssss',
    'sssssssnnsssssss',
    'ssssssLLLLssssss',
    'ssssssssssssssss',
    'sssjjjjjjjjjjsss',
]
FACE_PAL = {'s': None, '.': None, 'b': BROW, 'W': SCLERA, 'I': IRIS, 'P': PUPIL, 'h': SKIN_HI, 'n': SKIN_SH,
            'm': '#9a5a50', 'L': LIP, 'l': SKIN_LO, 'j': SKIN_LO}


def pixel_map(face, rgb, rows, pal):
    """Paints a character map onto a face whose pixel grid matches the map (rows top -> bottom)."""
    h, w = rgb.shape[:2]
    for r, line in enumerate(rows):
        for col, ch in enumerate(line):
            colr = pal.get(ch)
            if colr is not None and r < h and col < w:
                put(rgb, np.s_[r:r + 1, col:col + 1], colr)


def face_fn(face, rgb, glow, rs):
    """Steve's face (front of the head): brows, blue eyes, nose, mouth, jaw; the helmet covers the rest."""
    if face.name != 'north':
        return
    x, y, z = face.world()
    put(rgb, np.abs(x) > 3.0, SKIN_LO, 0.45)
    pixel_map(face, rgb, FACE_MAP, FACE_PAL)


A_GLYPH = ['.###.', '#...#', '#...#', '#####', '#...#', '#...#']


def letter_alpha_px(face, rs):
    a = np.zeros((face.h, face.w), np.uint8)
    if face.name == 'north':
        for r, line in enumerate(A_GLYPH):
            for col, ch in enumerate(line):
                if ch == '#' and r < face.h and col < face.w:
                    a[r, col] = 255
    return a


def head_fn(face, rgb, glow, rs):
    if face.name == 'north':
        face_fn(face, rgb, glow, rs)
    elif face.name == 'down':
        rgb *= 0.92
    else:
        put(rgb, np.ones(rgb.shape[:2], bool), BLUE_DK)  # under the cowl


def chest_fn(face, rgb, glow, rs):
    """Torso suit: blue; the abdomen stripes are a raised panel (abs_fn), the star a chest plate (plate_fn)."""
    x, y, z = face.world()
    if face.name == 'south':
        put(rgb, (np.abs(x) < 0.25) & (y > 14) & (y < 23), BLUE_LO, 0.35)  # spine seam
    elif face.name in ('east', 'west'):
        put(rgb, (y < 18.0) & (np.abs(z) < 0.5), BLUE_LO, 0.25)


def abs_fn(face, rgb, glow, rs):
    """Abdomen panel: seven vertical stripes, 2 px each, red at the centre and both edges (R W R W R W R)."""
    x, y, z = face.world()
    if face.name in ('north', 'up', 'down'):
        col = np.floor((x + 3.5) * D).astype(int) // 2
        white = (col % 2) == 1
        put(rgb, ~white, RED)
        put(rgb, white, WHITE)
        if face.name == 'north':
            put(rgb, ~white & (y > 17.75), RED_LO, 0.35)   # shadow under the chest plate
            put(rgb, white & (y > 17.75), WHITE_LO, 0.6)
    else:
        put(rgb, np.ones(rgb.shape[:2], bool), RED_LO)


def plate_fn(face, rgb, glow, rs):
    """Chest plate: blue with a silver-rimmed white star."""
    if face.name != 'north':
        return
    x, y, z = face.world()
    rim = star_mask(x, y, 0.0, 20.9, 2.55, inner=0.42)
    star = star_mask(x, y, 0.0, 20.9, 2.05, inner=0.42)
    put(rgb, rim, SILVER_LO)
    put(rgb, star, WHITE)
    put(rgb, star & (x > 0.35) & (y < 20.6), WHITE_LO, 0.6)


def sleeve_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if sides(face):
        put(rgb, y > 23.5, BLUE_HI, 0.5)  # shoulder cap catches the light


def glove_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if sides(face):
        put(rgb, y > 14.6, LEATHER_HI, 0.35)
    if face.name == 'down':
        rgb[:] = rgb * 0.85


def leg_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if sides(face):
        put(rgb, y > 11.4, BLUE_LO, 0.45)  # shadow under the belt
        if face.name == 'north':
            put(rgb, (y > 6.3) & (y < 7.0), BLUE_LO, 0.35)  # knee seam


def cuff_fn(face, rgb, glow, rs):
    if sides(face):
        put(rgb, np.s_[:1, :], LEATHER_HI, 0.6)
        put(rgb, np.s_[-1:, :], LEATHER_DK, 0.5)


def belt_fn(face, rgb, glow, rs):
    if sides(face):
        put(rgb, np.s_[:1, :], LEATHER_HI, 0.5)
        put(rgb, np.s_[-1:, :], LEATHER_DK, 0.45)


def buckle_fn(face, rgb, glow, rs):
    if face.name == 'north':
        put(rgb, np.s_[1:-1, 1:-1], STEEL_HI, 1.0)
        put(rgb, np.s_[1:-1, face.w // 2 - 1:face.w // 2 + 1], STEEL, 1.0)


def pouch_fn(face, rgb, glow, rs):
    if sides(face):
        put(rgb, np.s_[:2, :], LEATHER_HI, 0.5)        # flap
        put(rgb, np.s_[2:3, :], LEATHER_DK, 0.6)
        if face.name == 'north' and face.w >= 3:
            put(rgb, np.s_[1:2, face.w // 2:face.w // 2 + 1], STEEL_HI, 1.0)  # snap


def boot_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if sides(face):
        put(rgb, y < 0.6, LEATHER_DK, 0.7)              # sole
        put(rgb, (y > 2.6) & (y < 3.1), LEATHER_LO, 0.6)  # strap
        if face.name == 'north':
            put(rgb, (y > 2.6) & (y < 3.1) & (np.abs(x - x.mean()) < 0.6), STEEL_HI, 1.0)  # strap buckle
    if face.name == 'down':
        rgb[:] = np.array([60, 36, 20], float)


def helmet_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'up':
        put(rgb, np.abs(x) < 0.5, BLUE_HI, 0.35)        # ridge highlight
    elif sides(face):
        put(rgb, y > 31.2, BLUE_HI, 0.35)


def helmet_alpha(face, rs):
    """Eye window cut out of the helmet front (the face shows through)."""
    a = np.full((face.h, face.w), 255, np.uint8)
    if face.name == 'north':
        x, y, z = face.world()
        win = (np.abs(x) < 3.0) & (y < 28.45)
        a[win] = 0
    return a


def a_mask(x, y, cx, cy, h=2.6, w=2.4, t=0.62):
    """Letter A (outline polygon with the counter cut and a cross bar)."""
    top = cy + h / 2
    bot = cy - h / 2
    outer = poly_mask(x, y, [(cx - w / 2, bot), (cx - t / 2, top), (cx + t / 2, top), (cx + w / 2, bot),
                             (cx + w / 2 - t, bot), (cx, top - t * 1.35), (cx - w / 2 + t, bot)])
    bar = (np.abs(x - cx) < w * 0.32) & (y > cy - h * 0.22) & (y < cy - h * 0.22 + 0.5)
    return outer | (bar & poly_mask(x, y, [(cx - w / 2, bot), (cx, top), (cx + w / 2, bot)]))


def letter_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.ones(rgb.shape[:2], bool), WHITE)


def letter_alpha(cx, cy):
    def fn(face, rs):
        x, y, z = face.world()
        a = np.zeros((face.h, face.w), np.uint8)
        if face.name in ('north', 'south'):
            a[a_mask(x, y, cx, cy)] = 255
        return a
    return fn


def disc_alpha(cx, cy, r, axis='x'):
    """Round cut-out on the outer faces (ear pieces)."""
    def fn(face, rs):
        x, y, z = face.world()
        a = np.zeros((face.h, face.w), np.uint8)
        if face.name in ('east', 'west'):
            a[(z - cx) ** 2 + (y - cy) ** 2 <= r * r] = 255
        return a
    return fn


def ear_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name in ('east', 'west'):
        rr = np.sqrt((z - 0.4) ** 2 + (y - 27.2) ** 2)
        put(rgb, rr > 0.85, SILVER_LO, 0.8)
        put(rgb, rr < 0.45, SILVER_LO, 0.5)


def wing_fn(face, rgb, glow, rs):
    if sides(face) or face.name == 'up':
        put(rgb, np.s_[-1:, :], WHITE_LO, 0.8)


# ============================================================================================ materials
M = {
    'head': Flat(SKIN, head_fn),
    'neck': Flat(SKIN_LO),
    'torso': Flat(BLUE, chest_fn),
    'plate': Flat(BLUE, plate_fn),
    'collar': Flat(BLUE_LO),
    'sleeve': Flat(BLUE, sleeve_fn),
    'delt': Flat(BLUE, sleeve_fn),
    'glove': Flat(LEATHER, glove_fn),
    'leg': Flat(BLUE, leg_fn),
    'yoke': Flat(BLUE_LO),
    'knuckle': Flat(LEATHER_LO),
    'shin': Flat(BLUE),
    # gear
    'helmet': Flat(BLUE, helmet_fn, alpha_fn=helmet_alpha),
    'helmet_back': Flat(BLUE, helmet_fn),
    'helmet_top': Flat(BLUE, helmet_fn),
    'cheek': Flat(BLUE),
    'strap': Flat(LEATHER_LO),
    'chin': Flat(LEATHER_LO),
    'brow': Flat(BLUE_HI),
    'ridge': Flat(BLUE_HI),
    'abs': Flat(RED, abs_fn, light=False),
    'letter': Flat(WHITE, letter_fn),
    'wing': Flat(WHITE, wing_fn),
    'ear': Flat(SILVER, ear_fn),
    'belt': Flat(LEATHER, belt_fn),
    'buckle': Flat(STEEL, buckle_fn),
    'pouch': Flat(LEATHER, pouch_fn),
    'cuff': Flat(LEATHER, cuff_fn),
    'boot': Flat(LEATHER, boot_fn),
    'toe': Flat(LEATHER_LO),
    'knee': Flat(BLUE_LO),
}


# ============================================================================================ skeleton
ELBOW_Y, WRIST_Y, KNUCKLE_Y, KNEE_Y = 18, 15, 13, 6


def skeleton(m, gear=False):
    m.bone('root', None, [0, 0, 0])
    m.bone('waist', 'root', [0, 12, 0])
    m.bone('torso', 'waist', [0, 12, 0])
    m.bone('collar', 'torso', [0, 24, 0])
    m.bone('head', 'torso', [0, 24, 0])
    for side, s in (('right', -1), ('left', 1)):
        m.bone(f'{side}_arm', 'torso', [s * 5, 22, 0])
        m.bone(f'{side}_forearm', f'{side}_arm', [s * 6, ELBOW_Y, 0])
        m.bone(f'{side}_hand', f'{side}_forearm', [s * 6, WRIST_Y, 0])
        m.bone(f'{side}_fingers', f'{side}_hand', [s * 4, KNUCKLE_Y, 0])
        m.bone(f'{side}_thumb', f'{side}_hand', [s * 4, 14, -1])
        if gear and side == 'left':
            m.bone('left_shield', 'left_forearm', [8.75, 16.4, 0])
    for side, s in (('right', -1), ('left', 1)):
        m.bone(f'{side}_leg', 'root', [s * 1.9, 12, 0])
        m.bone(f'{side}_shin', f'{side}_leg', [s * 1.9, KNEE_Y, 0])


def side_x(s, x0, w):
    """Origin x of a part spanning [x0, x0 + w] on the left side (s = 1) or its mirror on the right (s = -1)."""
    return x0 if s > 0 else -(x0 + w)


def body_model():
    m = CleanModel('captain', density=D, width=128, seed=2012, light=LIGHT)
    skeleton(m)
    c = m.cube
    # head + neck
    c('head', [-4, 24, -4], [8, 8, 8], M['head'])
    c('torso', [-1.6, 23.2, -1.6], [3.2, 1.0, 3.2], M['neck'])
    # torso: suit body, chest plate (star), collar ring, lats
    c('torso', [-4, 12, -2], [8, 12, 4], M['torso'])
    c('torso', [-3.7, 18.2, -2.55], [7.4, 5.3, 0.7], M['plate'])
    c('torso', [-3.5, 13.3, -2.25], [7.0, 4.95, 0.4], M['abs'])
    c('torso', [-2.6, 23.4, -2.2], [5.2, 0.9, 4.4], M['collar'])
    c('torso', [-4.15, 21.6, 2.0], [8.3, 2.3, 0.25], M['yoke'], faces=('south', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        # arms: sleeve (upper arm), deltoid cap, forearm, glove (hand + fingers)
        ax0 = side_x(s, 4.0, 4.0)
        c(f'{"left" if s > 0 else "right"}_arm', [ax0, ELBOW_Y, -2], [4, 24 - ELBOW_Y, 4], M['sleeve'])
        c(f'{"left" if s > 0 else "right"}_arm', [side_x(s, 4.15, 4.15), 21.0, -2.25], [4.15, 3.25, 4.5], M['delt'])
        c(f'{"left" if s > 0 else "right"}_forearm', [ax0 + (0.1 if s > 0 else 0.1), WRIST_Y, -1.9], [3.8, ELBOW_Y - WRIST_Y, 3.8], M['sleeve'])
        c(f'{"left" if s > 0 else "right"}_hand', [ax0, KNUCKLE_Y, -2], [4, WRIST_Y - KNUCKLE_Y, 4], M['glove'])
        c(f'{"left" if s > 0 else "right"}_fingers', [ax0 + 0.1, 12, -1.9], [3.8, 1, 3.8], M['glove'])
        c(f'{"left" if s > 0 else "right"}_hand', [side_x(s, 4.6, 3.0), 13.2, -2.25], [3.0, 1.5, 0.25], M['knuckle'],
          faces=('north', 'east', 'west', 'up', 'down'))
        c(f'{"left" if s > 0 else "right"}_thumb', [side_x(s, 3.6, 1.0), 12.9, -2.45], [1.0, 1.6, 1.1], M['glove'])
        # legs: thigh, shin
        side = 'left' if s > 0 else 'right'
        c(f'{side}_leg', [side_x(s, 0.05, 3.95), KNEE_Y, -2], [3.95, 12 - KNEE_Y, 4], M['leg'])
        c(f'{side}_shin', [side_x(s, 0.15, 3.75), 0.1, -1.9], [3.75, KNEE_Y - 0.1, 3.8], M['shin'])
    return m


def gear_model():
    m = CleanModel('captain_gear', density=D, width=128, seed=1942, light=LIGHT)
    skeleton(m, gear=True)
    c = m.cube
    # ---- helmet (2012): shell with the eye window, cheek guards, chin cup, "A", wings, ear pieces
    c('head', [-4.35, 26.6, -4.35], [8.7, 5.75, 8.7], M['helmet'], faces=('north', 'east', 'west', 'up'))
    c('head', [-3.9, 32.35, -3.9], [7.8, 0.4, 7.8], M['helmet_top'], faces=('north', 'east', 'west', 'up', 'south'))
    c('head', [-4.35, 24.0, -4.25], [8.7, 8.35, 8.6], M['helmet_back'], faces=('south',))
    c('head', [-3.4, 28.45, -4.55], [6.8, 0.45, 0.4], M['brow'], faces=('north', 'down', 'up', 'east', 'west'))
    c('head', [-0.45, 32.75, -3.5], [0.9, 0.3, 6.8], M['ridge'], faces=('north', 'south', 'east', 'west', 'up'))
    c('head', [-4.35, 24.0, -4.35], [8.7, 2.6, 8.7], M['cheek'], faces=('east', 'west'))
    for s in (-1, 1):
        c('head', [side_x(s, 3.0, 1.45), 24.3, -4.45], [1.45, 2.3, 1.3], M['cheek'], faces=('north', 'east', 'west', 'down', 'up'))
        # ear piece disc and three wing feathers swept back
        c('head', [side_x(s, 4.4, 0.47), 25.95, -0.9], [0.47, 2.5, 2.6], Flat(SILVER, ear_fn, alpha_fn=disc_alpha(0.4, 27.2, 1.2)),
          faces=('east', 'west'))
        for i, (y0, z0, L, ang) in enumerate(((29.4, -2.8, 4.2, 14), (28.6, -2.4, 3.6, 8), (27.9, -2.0, 2.8, 2))):
            ox = side_x(s, 4.36 + 0.06 * i, 0.32)
            c('head', [ox, y0, z0], [0.32, 0.62, L], M['wing'], rotation=[ang, 0, 0], pivot=[ox, y0, z0])
    c('head', [-1.25, 29.0, -4.62], [2.5, 3.0, 0.2], Flat(WHITE, letter_fn, alpha_fn=letter_alpha_px, light=False),
      faces=('north',))
    # ---- belt with buckle and pouches (torso, so it follows the hips' parent)
    c('torso', [-4.3, 11.9, -2.3], [8.6, 1.5, 4.6], M['belt'])
    c('torso', [-1.05, 11.95, -2.55], [2.1, 1.4, 0.3], M['buckle'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        c('torso', [side_x(s, 2.0, 1.5), 11.2, -2.75], [1.5, 2.0, 0.55], M['pouch'])
        c('torso', [side_x(s, 4.3, 0.6), 11.1, -1.2], [0.6, 2.1, 2.2], M['pouch'])
        c('torso', [side_x(s, 1.2, 1.9), 11.2, 2.3], [1.9, 2.0, 0.6], M['pouch'])
    # ---- gloves: bell cuffs; boots: shell, cuff, toe cap; knee pads
    for s in (-1, 1):
        side = 'left' if s > 0 else 'right'
        ax0 = side_x(s, 3.7, 4.6)
        c(f'{side}_forearm', [ax0, 14.7, -2.3], [4.6, 1.9, 4.6], M['cuff'])
        c(f'{side}_shin', [side_x(s, -0.05, 4.2), 0.0, -2.15], [4.2, 3.7, 4.3], M['boot'])
        c(f'{side}_shin', [side_x(s, -0.15, 4.4), 3.55, -2.25], [4.4, 0.85, 4.5], M['cuff'])
        c(f'{side}_shin', [side_x(s, 0.1, 3.8), 0.0, -2.75], [3.8, 1.5, 0.65], M['toe'])
    return m


# ============================================================================================ shield
SH_RED, SH_RED_HI, SH_RED_LO = '#c81d2b', '#e2424d', '#8f141f'
SH_WHITE, SH_WHITE_LO = '#eef0f4', '#c4cad5'
SH_BLUE, SH_BLUE_HI = '#1f45a3', '#3561c4'
SH_BACK, SH_BACK_HI, SH_BACK_LO = '#8d96a3', '#aab2bd', '#6c7480'
R_OUT, R_WHITE, R_RED2, R_BLUE, R_STAR = 6.6, 5.15, 3.85, 2.6, 2.25


def ring_colour(r):
    if r <= R_BLUE:
        return SH_BLUE
    if r <= R_RED2:
        return SH_RED
    if r <= R_WHITE:
        return SH_WHITE
    return SH_RED


def gloss(x, y, rgb, mask, amount=0.16):
    """Soft directional highlight (upper left) so every ring reads as a curved, polished surface."""
    ang = np.arctan2(y, -x)
    k = np.cos(ang - math.radians(135))
    lift = (k * amount)[..., None]
    sel = mask[..., None]
    rgb[:] = np.where(sel, rgb * (1 + lift), rgb)


def level_fn(r_lo, r_hi):
    def fn(face, rgb, glow, rs):
        x, y, z = face.world()
        r = np.hypot(x, y)
        if face.name == 'north':
            for lo, hi in ((R_RED2, R_WHITE), (R_BLUE, R_RED2), (0, R_BLUE), (R_WHITE, R_OUT + 0.3)):
                put(rgb, (r > lo) & (r <= hi), ring_colour((lo + hi) / 2))
            put(rgb, (r > r_hi - 0.5) & (r <= r_hi), mix(ring_colour(r_hi - 0.01), '#ffffff', 0.25))  # lip
            if r_lo == 0:
                star = star_mask(x, y, 0.0, 0.0, R_STAR, inner=0.40)
                put(rgb, star, SH_WHITE)
            gloss(x, y, rgb, r <= r_hi)
        elif face.name == 'south':
            put(rgb, np.ones(r.shape, bool), SH_BACK)
            gloss(x, y, rgb, r <= r_hi, 0.08)
            put(rgb, (r > R_OUT - 0.6), SH_BACK_LO, 0.6)
    return fn


def level_alpha(r_hi):
    def fn(face, rs):
        x, y, z = face.world()
        a = np.zeros((face.h, face.w), np.uint8)
        a[np.hypot(x, y) <= r_hi] = 255
        return a
    return fn


def star_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.ones(rgb.shape[:2], bool), SH_WHITE)
    # bevel: the lower-right half of every point slightly darker (raised, lit from upper left)
    pts = []
    for i in range(5):
        a = i * 2 * math.pi / 5
        pts.append(a)
    ang = np.arctan2(x, y)
    sector = np.mod(ang, 2 * math.pi / 5) > (math.pi / 5)
    put(rgb, sector, SH_WHITE_LO, 0.55)
    glow_put(glow, star_mask(x, y, 0, 0, R_STAR * 1.04, inner=0.4), (150, 200, 255, 255))


def star_alpha(r, inner=0.40):
    def fn(face, rs):
        x, y, z = face.world()
        a = np.zeros((face.h, face.w), np.uint8)
        a[star_mask(x, y, 0.0, 0.0, r, inner=inner)] = 255
        return a
    return fn


def wall_fn(colour, hi):
    def fn(face, rgb, glow, rs):
        put(rgb, np.ones(rgb.shape[:2], bool), colour)
        if face.name == 'up':
            put(rgb, np.ones(rgb.shape[:2], bool), hi, 0.4)
    return fn


def shield_glow(face, glow, rs):
    """Kinetic charge: rim and ring lips glow pale blue (only while empowered)."""
    x, y, z = face.world()
    if face.name == 'north':
        r = np.hypot(x, y)
        for rr in (R_OUT, R_WHITE, R_RED2, R_BLUE):
            glow_put(glow, np.abs(r - rr + 0.25) < 0.3, (110, 170, 255, 255))


def shield_model():
    """Terraced vibranium dome: four stepped levels (cut-out discs) with real side walls, a raised star in three
    stacked layers, a thick rolled rim of 24 segments and the back with two straps and a grip. Front = -Z."""
    m = CleanModel('captain_shield', density=4, width=256, seed=1941)
    m.bone('shield', None, [0, 0, 0])
    m.bone('rim', 'shield', [0, 0, 0])
    c = m.cube
    levels = ((0.0, R_OUT - 0.2, 0.0), (R_RED2, R_WHITE, -0.22), (R_BLUE, R_RED2, -0.44), (0.0, R_BLUE, -0.64))
    for i, (r_lo, r_hi, zf) in enumerate(levels):
        faces = ('north', 'south') if i == 0 else ('north',)
        depth = 0.62 if i == 0 else 0.2
        c('shield', [-r_hi, -r_hi, zf], [2 * r_hi, 2 * r_hi, depth if i == 0 else 0.22],
          Flat(SH_RED, level_fn(r_lo, r_hi), glow=shield_glow if i == 0 else None, alpha_fn=level_alpha(r_hi),
               light=False), faces=faces)
    # step walls: 16 thin segments per step (rotated about Z), only their outward/inward sides
    for r, zf, colour, hi in ((R_WHITE, -0.22, SH_WHITE_LO, SH_WHITE), (R_RED2, -0.44, SH_RED_LO, SH_RED),
                              (R_BLUE, -0.64, SH_BLUE, SH_BLUE_HI)):
        n = 16
        seg = 2 * r * math.tan(math.pi / n) + 0.06
        for k in range(n):
            ang = 360.0 * k / n
            c('shield', [-seg / 2, r - 0.16, zf], [seg, 0.16, -zf if zf > -0.22 else 0.22 + 0.001],
              Flat(colour, wall_fn(colour, hi), light=False), rotation=[0, 0, ang], pivot=[0, 0, 0],
              faces=('up', 'down'))
    # raised star: three stacked cut-out layers (bevel reads as thickness)
    for k, (zz, rr, col) in enumerate(((-0.70, R_STAR, SH_WHITE_LO), (-0.76, R_STAR * 0.98, SH_WHITE),
                                        (-0.82, R_STAR * 0.96, SH_WHITE))):
        c('shield', [-rr, -rr, zz], [2 * rr, 2 * rr, 0.04], Flat(col, star_fn, alpha_fn=star_alpha(rr), light=False),
          faces=('north',))
    # rolled rim: 24 segments, red face with a bright lip, steel outer edge
    n = 24
    seg = 2 * R_OUT * math.tan(math.pi / n) + 0.05
    for k in range(n):
        ang = 360.0 * k / n
        dz = 0.02 * (k % 2)  # odd tiles stand proud by a hair: overlapping tile corners never share a plane
        c('rim', [-seg / 2, R_OUT - 0.42 - dz, -0.34 - dz], [seg, 0.62 + 2 * dz, 1.04 + 2 * dz], Flat(SH_RED, rim_fn, glow=rim_glow, light=False),
          rotation=[0, 0, ang], pivot=[0, 0, 0], faces=('north', 'south', 'up', 'down'))
    # back: two leather straps and the grip bar
    for y0 in (1.6, -2.4):
        c('shield', [-3.6, y0, 0.66], [7.2, 0.8, 0.28], Flat('#6b4224', strap_fn, light=False))
    c('shield', [-0.5, -1.7, 0.7], [1.0, 3.6, 0.42], Flat('#4b2f1a', light=False))
    return m


def rim_fn(face, rgb, glow, rs):
    if face.name == 'north':
        put(rgb, np.s_[-1:, :], SH_RED_HI, 0.8)   # inner lip catches the light
        put(rgb, np.s_[:1, :], SH_RED_LO, 0.5)
    elif face.name == 'up':
        put(rgb, np.ones(rgb.shape[:2], bool), STEEL_HI)
        put(rgb, np.s_[:, :1], STEEL, 1.0)
    elif face.name == 'down':
        put(rgb, np.ones(rgb.shape[:2], bool), SH_RED_LO)
    elif face.name == 'south':
        put(rgb, np.ones(rgb.shape[:2], bool), SH_BACK_LO)


def rim_glow(face, glow, rs):
    if face.name in ('north', 'up'):
        glow[:] = (90, 150, 255, 255)


def strap_fn(face, rgb, glow, rs):
    if sides(face):
        put(rgb, np.s_[:1, :], '#86542f', 0.7)


# ============================================================================================ outputs
ARM_BONES = ('arm', 'forearm', 'hand', 'fingers', 'thumb', 'shield')
FP_TOP = {'right_arm': 'armorRightArm', 'left_arm': 'armorLeftArm'}


def arms_only(src, ident):
    """First-person arms: the arm chains of a model (same atlas / UVs), top bones renamed for the FP clips."""
    fp = CleanModel(ident, density=src.density, width=src.width)
    fp.height = src.height
    for b in src.bones:
        parts = b.name.split('_', 1)
        if parts[0] not in ('right', 'left') or len(parts) < 2 or parts[1] not in ARM_BONES:
            continue
        nb = fp.bone(FP_TOP.get(b.name, b.name), None if b.name in FP_TOP else FP_TOP.get(b.parent, b.parent), b.pivot, b.rotation)
        nb.cubes = [copy.copy(c) for c in b.cubes]
    return fp


class _Box:
    def __init__(self, origin, size):
        self.origin, self.size = origin, size


VANILLA_BOXES = (  # origin, size, uv, material
    ([-4, 24, -4], [8, 8, 8], (0, 0), 'head'),
    ([-4, 12, -2], [8, 12, 4], (16, 16), 'torso'),
    ([-8, 12, -2], [4, 12, 4], (40, 16), 'arm'),
    ([4, 12, -2], [4, 12, 4], (32, 48), 'arm'),
    ([-4, 0, -2], [4, 12, 4], (0, 16), 'leg'),
    ([0, 0, -2], [4, 12, 4], (16, 48), 'leg'),
)


def vanilla_skin():
    """64x64 vanilla-layout skin in the same colours (vanilla hand in first person, HeroSkins)."""
    img = np.zeros((64, 64, 4), np.uint8)
    rng = random.Random(7)

    def arm_mat(face, rgb, glow, rs):
        x, y, z = face.world()
        put(rgb, y < WRIST_Y + 0.0, LEATHER)
        put(rgb, (y >= WRIST_Y) & (y < WRIST_Y + 1.0), LEATHER_LO)

    def leg_mat(face, rgb, glow, rs):
        x, y, z = face.world()
        put(rgb, y < 4.0, LEATHER)
        put(rgb, y < 0.6, LEATHER_DK)

    def head_mat(face, rgb, glow, rs):
        x, y, z = face.world()
        if face.name == 'north':
            face_fn(face, rgb, glow, rs)
            put(rgb, (y >= 28.45) | (np.abs(x) >= 3.0) & (y >= 26.6), BLUE)
            put(rgb, a_mask(x, y, 0.0, 30.55), WHITE)
        else:
            put(rgb, np.ones(rgb.shape[:2], bool), BLUE)

    mats = {'head': Flat(SKIN, head_mat, light=False), 'torso': Flat(BLUE, lambda f, r, g, s: (chest_fn(f, r, g, s), plate_fn(f, r, g, s)), light=False),
            'arm': Flat(BLUE, arm_mat, light=False), 'leg': Flat(BLUE, leg_mat, light=False)}
    for origin, size, (u, v), key in VANILLA_BOXES:
        w, h, d = size
        rects = {'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'east': (u + d + w, v + d, d, h),
                 'south': (u + d + w + d, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}
        box = _Box(origin, size)
        for name, (x, y, rw, rh) in rects.items():
            face = Face(box, name, rw, rh)
            col, _ = mats[key].paint(face, rng)
            img[y:y + rh, x:x + rw] = col
    return img


def main():
    body = body_model()
    write_model(body, 'geo/captain/captain.geo.json', 'textures/entity/captain/captain.png')
    write_json(os.path.join(RES, 'geo/captain/captain_arms.geo.json'), arms_only(body, 'captain_arms').geo_json())
    gear = gear_model()
    write_model(gear, 'geo/captain/captain_gear.geo.json', 'textures/entity/captain/captain_gear.png',
                'textures/entity/captain/captain_gear_glow.png')
    write_json(os.path.join(RES, 'geo/captain/captain_arms_gear.geo.json'), arms_only(gear, 'captain_arms_gear').geo_json())
    shield = shield_model()
    write_model(shield, 'geo/captain/captain_shield.geo.json', 'textures/entity/captain/captain_shield.png',
                'textures/entity/captain/captain_shield_glow.png')
    write_png('textures/entity/captain/captain_skin.png', vanilla_skin())
    n = lambda mm: sum(len(b.cubes) for b in mm.bones)  # noqa: E731
    print('captain cubes body', n(body), 'gear', n(gear), 'shield', n(shield),
          'atlas', (body.width, body.height), (gear.width, gear.height), (shield.width, shield.height))


if __name__ == '__main__':
    main()
