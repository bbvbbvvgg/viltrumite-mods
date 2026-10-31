"""The summoned Avengers of «Avengers, assemble!» (2012 line-up) — own procedural models and pixel art, CC0.

Rebuilt on the clean builder (tools/assets/clean_geo.py): flat saturated colours, 1 px gutters, no coplanar faces
(tools/assets/geo_lint.py), hand-drawn 16 px faces. Thor, Black Widow (slim arms) and Hawkeye have their own bodies
on the player skeleton (same bone names / pivots as before, so the clips keep working) plus a gear model per hero
(same skeleton + props: Mjolnir, cape, hair, pistols, bow, quiver). Hulk has his own, larger body with extra bones
(jaw, thumbs, chest, feet). Iron Man keeps his Mark VII geometry (player rig skin + gear) and is only repainted
cleanly. Also Mjolnir (thrown), the arrows, the mini missile and the Quinjet (quarter scale, the renderer scales x4).
Writes geo/avengers/*.geo.json and textures/entity/avengers/*.png.
"""
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from clean_geo import (CleanModel, Flat, Face, glow_put, mix, poly_mask, put, sides, star_mask,  # noqa: E402
                       write_json, write_model, write_png, RES)
from jinwoo_model import body_model  # noqa: E402  (Iron Man keeps the player-rig skin layout)

D = 2


def W(x):
    return os.path.join('geo/avengers', x)


def T(x):
    return os.path.join('textures/entity/avengers', x)


def side_x(s, x0, w):
    """Origin x of a part spanning [x0, x0 + w] on the left (s = 1) or mirrored on the right (s = -1)."""
    return x0 if s > 0 else -(x0 + w)


def sname(s):
    return 'left' if s > 0 else 'right'


def pixel_map(face, rgb, rows, pal):
    h, w = rgb.shape[:2]
    for r, line in enumerate(rows):
        for col, ch in enumerate(line):
            colr = pal.get(ch)
            if colr is not None and r < h and col < w:
                put(rgb, np.s_[r:r + 1, col:col + 1], colr)


def solid(colour):
    def fn(face, rgb, glow, rs):
        put(rgb, np.ones(rgb.shape[:2], bool), colour)
    return fn


def disc_alpha(face, rs):
    """Round cut-out on the faces of a thin disc cube (whichever two faces are the large ones)."""
    u, v = face.grid()
    a = np.zeros((face.h, face.w), np.uint8)
    a[np.hypot(u - 0.5, v - 0.5) <= 0.5] = 255
    return a


# ============================================================================================ rig
def rig(m, slim=False):
    aw = 3 if slim else 4
    cx = 4 + aw / 2
    m.bone('root', None, [0, 0, 0])
    m.bone('waist', 'root', [0, 12, 0])
    m.bone('torso', 'waist', [0, 12, 0])
    m.bone('collar', 'torso', [0, 24, 0])
    m.bone('head', 'torso', [0, 24, 0])
    for s in (-1, 1):
        side = sname(s)
        m.bone(f'{side}_arm', 'torso', [s * 5, 22, 0])
        m.bone(f'{side}_forearm', f'{side}_arm', [s * cx, 18, 0])
        m.bone(f'{side}_hand', f'{side}_forearm', [s * cx, 15, 0])
        m.bone(f'{side}_fingers', f'{side}_hand', [s * 4, 13, 0])
        m.bone(f'{side}_thumb', f'{side}_hand', [s * 4, 14, -1])
    for s in (-1, 1):
        side = sname(s)
        m.bone(f'{side}_leg', 'root', [s * 1.9, 12, 0])
        m.bone(f'{side}_shin', f'{side}_leg', [s * 1.9, 6, 0])
    return aw


def base_body(m, mats, slim=False, thumbs=True):
    """Head, torso, split arms (upper / forearm / hand / fingers / thumb) and legs (thigh / shin)."""
    aw = 3 if slim else 4
    c = m.cube
    c('head', [-4, 24, -4], [8, 8, 8], mats['head'])
    c('torso', [-4, 12, -2], [8, 12, 4], mats['torso'])
    c('torso', [-1.6, 23.2, -1.6], [3.2, 1.0, 3.2], mats.get('neck', mats['head']))
    for s in (-1, 1):
        side = sname(s)
        x0 = side_x(s, 4.0, aw)
        c(f'{side}_arm', [x0, 18, -2], [aw, 6, 4], mats['arm'])
        c(f'{side}_forearm', [x0 + 0.1, 15, -1.9], [aw - 0.2, 3, 3.8], mats['forearm'])
        c(f'{side}_hand', [x0, 13, -2], [aw, 2, 4], mats['hand'])
        c(f'{side}_fingers', [x0 + 0.1, 12, -1.9], [aw - 0.2, 1, 3.8], mats.get('fingers', mats['hand']))
        if thumbs:
            c(f'{side}_thumb', [side_x(s, 3.6, 1.0), 12.9, -2.45], [1.0, 1.6, 1.1], mats.get('fingers', mats['hand']))
        c(f'{side}_leg', [side_x(s, 0.05, 3.95), 6, -2], [3.95, 6, 4], mats['leg'])
        c(f'{side}_shin', [side_x(s, 0.15, 3.75), 0.1, -1.9], [3.75, 5.9, 3.8], mats['shin'])
    return aw


def face_material(base, rows, pal, sides_col=None, back_col=None, top_col=None):
    def fn(face, rgb, glow, rs):
        if face.name == 'north':
            pixel_map(face, rgb, rows, pal)
        elif face.name == 'up' and top_col:
            put(rgb, np.ones(rgb.shape[:2], bool), top_col)
        elif face.name == 'south' and back_col:
            put(rgb, np.ones(rgb.shape[:2], bool), back_col)
        elif face.name in ('east', 'west') and sides_col:
            x, y, z = face.world()
            put(rgb, y > 27.5, sides_col)
            put(rgb, (y > 25.0) & (z > 0.5), sides_col)
    return Flat(base, fn)


# ============================================================================================ IRON MAN MARK VII
# Geometry unchanged (player-rig skin + the Mark VII gear: helmet + faceplate hinge, shoulders, back flaps,
# gauntlets, boots); only the paint is new: flat red / gold / silver blocks, no noise, no panel-line bands.
IM_RED, IM_RED_HI, IM_RED_LO = '#c2161d', '#de3a3f', '#8a0f14'
IM_GOLD, IM_GOLD_HI, IM_GOLD_LO = '#e0aa3e', '#f2c766', '#a87a24'
IM_SILVER, IM_SILVER_LO = '#b3bac4', '#8a929d'
TONY_SKIN, TONY_HAIR = '#e2b58f', '#2b1d14'


def im_head(face, rgb, glow, rs):
    """Tony Stark (seen while the faceplate is up): dark hair, brown eyes, goatee."""
    x, y, z = face.world()
    if face.name in ('up', 'south'):
        put(rgb, np.ones(rgb.shape[:2], bool), TONY_HAIR)
        return
    if face.name in ('east', 'west'):
        put(rgb, y > 29.0, TONY_HAIR)
        put(rgb, (y > 26.5) & (z > 0.5), TONY_HAIR)
        return
    if face.name != 'north':
        return
    pixel_map(face, rgb, [
        'HHHHHHHHHHHHHHHH',
        'HHHHHHHHHHHHHHHH',
        'HHHHHHHHHHHHHHHH',
        'HHsssssssssssHHH',
        'Hsssssssssssssss',
        'sssbbbbssbbbbsss',
        'ssssWEssssEWssss',
        'ssssWPssssPWssss',
        'ssssssssssssssss',
        'sssssssnnsssssss',
        'ssssssssssssssss',
        'ssssHHHHHHHHssss',
        'sssssHLLLLHsssss',
        'sssssHHHHHHsssss',
        'ssssssHHHHssssss',
        'sssssssHHsssssss',
    ], {'H': TONY_HAIR, 'b': TONY_HAIR, 'W': '#f4f0ea', 'E': '#6b4124', 'P': '#2a1a10', 'n': '#c79570', 'L': '#a8685a'})


def im_torso(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'north':
        put(rgb, (y < 17.0) & (np.abs(x) < 2.9), IM_GOLD)                 # gold abdomen
        put(rgb, (y < 17.0) & (np.abs(x) < 2.9) & (np.abs(x) > 2.4), IM_GOLD_LO)
        put(rgb, np.hypot(x, y - 20.6) < 1.6, IM_SILVER)                   # reactor housing
    elif face.name in ('east', 'west'):
        put(rgb, (y < 18.0) & (y > 13.0), IM_SILVER_LO)
    elif face.name == 'south':
        put(rgb, (y < 15.0), IM_RED_LO, 0.5)


def im_arm(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, (y < 17.8) & (y > 15.0), IM_GOLD)
    put(rgb, (y < 15.0) & (y > 13.4), IM_SILVER, 0.55)


def im_leg(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name in ('north', 'east', 'west'):
        put(rgb, (y > 7.2) & (y < 10.8), IM_GOLD)
    put(rgb, y < 2.8, IM_SILVER)
    if face.name == 'north':
        put(rgb, (y < 6.0) & (y > 4.2), IM_GOLD)


def clear_alpha(face, rs):
    return np.zeros((face.h, face.w), np.uint8)


IM_MATS = {
    ('head', 0): Flat(TONY_SKIN, im_head, light=False),
    ('head', 1): Flat(IM_RED, alpha_fn=clear_alpha, light=False),
    ('torso', 0): Flat(IM_RED, im_torso, light=False),
    ('torso', 1): Flat(IM_RED, alpha_fn=clear_alpha, light=False),
    ('arm', 0): Flat(IM_RED, im_arm, light=False),
    ('arm', 1): Flat(IM_RED, alpha_fn=clear_alpha, light=False),
    ('leg', 0): Flat(IM_RED, im_leg, light=False),
    ('leg', 1): Flat(IM_RED, alpha_fn=clear_alpha, light=False),
}


class _Box:
    def __init__(self, origin, size):
        self.origin, self.size = origin, size


def group(bone):
    if bone in ('head', 'torso'):
        return bone
    return 'arm' if any(p in bone for p in ('arm', 'hand', 'fingers')) else 'leg'


def paint_skin(model, mats, seed=2012):
    """Paints every full skin box of a split-limb player rig into a 128 x 128 HD skin (Steve layout)."""
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


def faceplate_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'north':
        ax = np.abs(x)
        eye = (ax > 0.8) & (ax < 2.8) & (np.abs(y - (28.2 + (ax - 0.8) * 0.12)) < 0.28)
        put(rgb, ax > 3.2, IM_RED)
        put(rgb, (ax < 1.6) & (np.abs(y - 25.3) < 0.14), IM_GOLD_LO)       # mouth slit
        put(rgb, eye, '#f4fbff')
        glow_put(glow, eye, (200, 240, 255, 255))


def helmet_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'up':
        put(rgb, np.abs(x) < 0.5, IM_RED_HI, 0.6)
    if face.name in ('east', 'west'):
        put(rgb, (np.abs(z) < 0.9) & (np.abs(y - 27.5) < 0.9), IM_SILVER)


def glow_disc(colour, rim_col=IM_SILVER):
    def fn(face, rgb, glow, rs):
        u, v = face.grid()
        r = np.hypot(u - 0.5, v - 0.5) * 2
        put(rgb, np.ones(r.shape, bool), '#d8efff')
        put(rgb, r > 0.75, rim_col)
        glow_put(glow, r < 0.85, tuple(colour) + (255,))
    return fn


IM = {
    'faceplate': Flat(IM_GOLD, faceplate_fn),
    'helmet': Flat(IM_RED, helmet_fn),
    'plate': Flat(IM_RED),
    'gold': Flat(IM_GOLD),
    'silver': Flat(IM_SILVER),
    'reactor': Flat('#d8efff', glow_disc((170, 230, 255)), light=False),
    'repulsor': Flat('#d8efff', glow_disc((170, 230, 255)), light=False),
    'jet': Flat('#2a2d33', glow_disc((255, 190, 120), '#2a2d33'), light=False),
}


def player_skeleton(m):
    rig(m, False)
    for side, s in (('right', -1), ('left', 1)):
        pass


def ironman_gear():
    m = CleanModel('ironman_mk7_gear', density=4, width=128, seed=7)
    rig(m, False)
    m.bone('helmet', 'head', [0, 24, 0])
    m.bone('faceplate', 'helmet', [0, 32.3, -4.4])
    m.bone('shoulder_right', 'torso', [-5.6, 23.6, 0])
    m.bone('shoulder_left', 'torso', [5.6, 23.6, 0])
    m.bone('back_flaps', 'torso', [0, 22, 2.2])
    m.cube('helmet', [-4.45, 24.0, -3.6], [8.9, 8.45, 8.05], IM['helmet'], faces=('south', 'east', 'west', 'up'))
    m.cube('helmet', [-4.5, 24.0, 4.0], [9.0, 1.2, 0.6], IM['silver'])
    m.cube('faceplate', [-4.35, 24.1, -4.5], [8.7, 8.1, 0.9], IM['faceplate'], faces=('north', 'east', 'west', 'down'))
    m.cube('faceplate', [-1.2, 23.6, -4.65], [2.4, 1.2, 1.2], IM['gold'])
    m.cube('torso', [-1.05, 19.55, -2.35], [2.1, 2.1, 0.3], IM['reactor'], faces=('north',))
    m.cube('torso', [-4.25, 22.3, -2.25], [8.5, 1.9, 4.5], IM['plate'])
    for sx, bone in ((-1, 'shoulder_right'), (1, 'shoulder_left')):
        x0 = 4.0 if sx > 0 else -8.6
        m.cube(bone, [x0, 21.2, -2.4], [4.6, 3.2, 4.8], IM['plate'])
        m.cube(bone, [x0 + 0.4, 24.0, -2.0], [3.8, 0.7, 4.0], IM['gold'])
    m.cube('back_flaps', [-3.2, 15.5, 2.0], [2.6, 6.0, 0.6], IM['plate'])
    m.cube('back_flaps', [0.6, 15.5, 2.0], [2.6, 6.0, 0.6], IM['plate'])
    for side, cx in (('right', -6.0), ('left', 6.0)):
        m.cube(f'{side}_forearm', [cx - 2.0, 15.2, -2.0], [4.0, 3.0, 4.0], IM['gold'], inflate=0.3)
        m.cube(f'{side}_fingers', [cx - 0.9, 11.98, -0.9], [1.8, 0.02, 1.8], IM['repulsor'], faces=('down',))
        m.cube(f'{side}_hand', [cx + (-2.32 if side == 'left' else 2.02), 13.4, -0.9], [0.3, 1.8, 1.8], IM['repulsor'],
               faces=('east',) if side == 'left' else ('west',))
    for side, cx in (('right', -1.9), ('left', 1.9)):
        m.cube(f'{side}_shin', [cx - 2.1, -0.02, -2.1], [4.2, 2.8, 4.4], IM['silver'], inflate=0.15)
        m.cube(f'{side}_shin', [cx - 1.0, -0.2, -1.0], [2.0, 0.2, 2.0], IM['jet'], faces=('down',))
        m.cube(f'{side}_leg', [cx - 2.0, 7.0, -2.25], [4.0, 4.0, 0.4], IM['gold'])
    return m


# ============================================================================================ THOR (2012)
TH_ARMOR, TH_ARMOR_HI, TH_ARMOR_LO = '#2b2e36', '#424652', '#1b1d23'
TH_MAIL, TH_MAIL_LO = '#7d838e', '#646a74'
TH_SILVER, TH_SILVER_LO = '#d3d8df', '#a2a9b3'
TH_RED, TH_RED_HI, TH_RED_LO = '#b3171f', '#cf2f33', '#7e0f16'
TH_SKIN, TH_SKIN_LO, TH_SKIN_SH = '#efc6a3', '#d9a885', '#c4906f'
TH_HAIR, TH_HAIR_HI, TH_HAIR_LO = '#e3bb5e', '#f3d488', '#b98f3c'
TH_BEARD = '#c29445'
TH_PANTS = '#30333b'
URU, URU_HI, URU_LO = '#8f959e', '#b5bbc3', '#666c75'
LEATHER_BROWN, LEATHER_BROWN_LO = '#6e4a2c', '#4f331d'

THOR_FACE = [
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'Hsssssssssssssss',
    'ssssssssssssssss',
    'ssbbbbbssbbbbbss',
    'ssssWIIssIIWssss',
    'ssssWIPssPIWssss',
    'sssssssssssssss s'[:16],
    'ssssssshhsssssss',
    'sBssssnnnnssssBs',
    'sBBBBsLLLLsBBBBs',
    'sBBBBBBBBBBBBBBs',
    'ssBBBBBBBBBBBBss',
    'sssBBBBBBBBBBsss',
]
THOR_PAL = {'H': TH_HAIR, 'b': TH_HAIR_LO, 'W': '#f6f6f8', 'I': '#4f86d6', 'P': '#1f3157', 'h': '#f8d6b6',
            'n': TH_SKIN_SH, 'L': '#b0705f', 'B': TH_BEARD}


def th_torso(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'north':
        put(rgb, y < 16.6, TH_MAIL)                         # mail skirt above the belt
        u, v = face.grid()
        chk = ((np.floor(u * face.w) + np.floor(v * face.h)) % 2) == 0
        put(rgb, (y < 16.6) & chk, TH_MAIL_LO, 0.35)
    elif face.name in ('east', 'west'):
        put(rgb, y < 16.6, TH_MAIL)


def th_plate(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'north':
        put(rgb, np.abs(x) < 0.3, TH_ARMOR_LO)               # centre seam between the two disc columns
        put(rgb, y > 23.0, TH_ARMOR_HI, 0.6)


def mail_fn(face, rgb, glow, rs):
    """Chain-mail sleeve: fine 1 px checker at low contrast (reads as mail, never as stripes)."""
    if sides(face):
        u, v = face.grid()
        chk = ((np.floor(u * face.w) + np.floor(v * face.h)) % 2) == 0
        put(rgb, chk, TH_MAIL_LO, 0.35)


def bracer_fn(face, rgb, glow, rs):
    if sides(face):
        put(rgb, np.s_[:1, :], TH_SILVER_LO, 0.9)
        put(rgb, np.s_[-1:, :], TH_SILVER_LO, 0.9)
        h, w = rgb.shape[:2]
        for col in range(1, w, 3):
            put(rgb, np.s_[h // 2:h // 2 + 1, col:col + 1], TH_SILVER)


def boot_fn_thor(face, rgb, glow, rs):
    x, y, z = face.world()
    if sides(face):
        put(rgb, y < 0.6, TH_ARMOR_LO)
        put(rgb, (y > 3.6) & (y < 4.1), TH_SILVER_LO)
    if face.name == 'down':
        put(rgb, np.ones(rgb.shape[:2], bool), TH_ARMOR_LO)


def cape_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'north':
        put(rgb, np.ones(rgb.shape[:2], bool), TH_RED_LO)   # inner side of the cape
    elif face.name == 'south':
        put(rgb, y < 3.0, TH_RED_LO, 0.35)
    elif face.name in ('east', 'west', 'down'):
        put(rgb, np.ones(rgb.shape[:2], bool), TH_RED_LO)


def hair_fn(lo, hi):
    def fn(face, rgb, glow, rs):
        x, y, z = face.world()
        if face.name == 'up':
            put(rgb, np.ones(rgb.shape[:2], bool), hi, 0.4)
        elif sides(face):
            put(rgb, np.s_[-1:, :], lo, 0.7)                  # tips
    return fn


def knot_fn(face, rgb, glow, rs):
    """Uru head: knot-work medallion on the long sides (one closed ring + cross), polished upper edge."""
    u, v = face.grid()
    if face.name in ('east', 'west', 'up', 'down') and face.w > 8 and face.h > 6:
        r = np.hypot((u - 0.5) * face.w / max(face.w, face.h), (v - 0.5) * face.h / max(face.w, face.h)) * 2
        ring = np.abs(r - 0.62) < 0.09
        cross = ((np.abs(u - 0.5) < 0.04) | (np.abs(v - 0.5) < 0.06)) & (r < 0.62)
        put(rgb, ring | cross, URU_HI, 0.85)
        put(rgb, np.abs(r - 0.62) < 0.18, URU_LO, 0.25)
    if sides(face):
        put(rgb, np.s_[:1, :], URU_HI, 0.5)


def face_inset_fn(face, rgb, glow, rs):
    if face.name in ('north', 'south'):
        put(rgb, np.s_[1:-1, 1:-1], URU_LO, 0.6)


def handle_fn(face, rgb, glow, rs):
    """Leather wrap: a low-contrast spiral (two tones), no hard bands."""
    if sides(face):
        x, y, z = face.world()
        u, v = face.grid()
        s = np.mod(y * 1.6 + u * 1.2, 1.0) < 0.5
        put(rgb, s, LEATHER_BROWN_LO, 0.35)


TH = {
    'head': face_material(TH_SKIN, THOR_FACE, THOR_PAL, sides_col=TH_HAIR, back_col=TH_HAIR, top_col=TH_HAIR),
    'neck': Flat(TH_SKIN_LO),
    'torso': Flat(TH_ARMOR, th_torso),
    'plate': Flat(TH_ARMOR, th_plate),
    'arm': Flat(TH_MAIL, mail_fn),
    'forearm': Flat(TH_SKIN),
    'hand': Flat(TH_SKIN),
    'fingers': Flat(TH_SKIN_LO),
    'leg': Flat(TH_PANTS),
    'shin': Flat(TH_PANTS),
    'shoulder': Flat(TH_ARMOR, lambda f, r, g, s: put(r, np.s_[:1, :], TH_SILVER_LO, 0.8) if sides(f) else None),
    'bracer': Flat(TH_ARMOR_LO, bracer_fn),
    'belt': Flat(TH_ARMOR_LO, lambda f, r, g, s: put(r, np.s_[:1, :], TH_SILVER_LO, 0.7) if sides(f) else None),
    'buckle': Flat(TH_SILVER),
    'boot': Flat(TH_ARMOR, boot_fn_thor),
    'collar': Flat(TH_ARMOR_HI),
    'disc': Flat(TH_SILVER, lambda f, r, g, s: put(r, np.hypot(*(np.array(f.grid()) - 0.5)) < 0.22, TH_SILVER_LO, 0.8),
                 alpha_fn=disc_alpha),
    'cape': Flat(TH_RED, cape_fn),
    'hair': Flat(TH_HAIR, hair_fn(TH_HAIR_LO, TH_HAIR_HI)),
    'hair_lo': Flat(TH_HAIR_LO, hair_fn(TH_HAIR_LO, TH_HAIR)),
    'hammer': Flat(URU, knot_fn),
    'hammer_face': Flat(URU, face_inset_fn),
    'hammer_rim': Flat(URU_HI),
    'hammer_silver': Flat(TH_SILVER),
    'handle': Flat(LEATHER_BROWN, handle_fn),
    'strap': Flat(LEATHER_BROWN_LO),
}


def mjolnir(m, bone, gx, gy, gz):
    """MCU Mjolnir, grip at (gx, gy, gz): the handle runs along Y through the fist with the pommel and the wrist
    strap above it and the head below, long axis front-back. Chamfered head (two crossed blocks), raised rims at
    both ends, recessed striking faces, knot-work medallions, a silver collar, leather-wrapped handle."""
    hy0, hh, hl, hw = gy - 7.9, 3.4, 5.8, 3.2
    m.cube(bone, [gx - hw / 2, hy0 + 0.3, gz - hl / 2 + 0.05], [hw, hh - 0.6, hl - 0.1], TH['hammer'])
    m.cube(bone, [gx - hw / 2 + 0.3, hy0, gz - hl / 2 + 0.05], [hw - 0.6, hh, hl - 0.1], TH['hammer'])
    for z0 in (gz - hl / 2 - 0.05, gz + hl / 2 - 0.55):
        m.cube(bone, [gx - hw / 2 - 0.12, hy0 + 0.18, z0], [hw + 0.24, hh - 0.36, 0.6], TH['hammer_rim'])
    for z0 in (gz - hl / 2 - 0.3, gz + hl / 2 + 0.05):
        m.cube(bone, [gx - hw / 2 + 0.45, hy0 + 0.45, z0], [hw - 0.9, hh - 0.9, 0.25], TH['hammer_face'])
    m.cube(bone, [gx - 0.8, hy0 + hh, gz - 0.8], [1.6, 0.45, 1.6], TH['hammer_silver'])
    m.cube(bone, [gx - 0.48, hy0 + hh + 0.45, gz - 0.48], [0.96, gy + 1.5 - (hy0 + hh + 0.45), 0.96], TH['handle'])
    m.cube(bone, [gx - 0.62, gy + 1.5, gz - 0.62], [1.24, 0.35, 1.24], TH['hammer_silver'])
    m.cube(bone, [gx - 0.52, gy + 1.85, gz - 0.52], [1.04, 0.55, 1.04], TH['hammer_silver'])
    m.cube(bone, [gx - 0.3, gy + 2.4, gz - 0.3], [0.6, 0.3, 0.6], TH['hammer_silver'])
    m.cube(bone, [gx - 0.12, gy - 0.9, gz + 0.55], [0.24, 2.95, 0.22], TH['strap'])
    m.cube(bone, [gx - 0.12, gy - 0.9, gz + 1.55], [0.24, 2.6, 0.22], TH['strap'])
    m.cube(bone, [gx - 0.12, gy - 1.12, gz + 0.77], [0.24, 0.22, 0.78], TH['strap'])
    m.cube(bone, [gx - 0.12, gy + 1.78, gz + 0.5], [0.24, 0.22, 1.1], TH['strap'], rotation=[-25, 0, 0], pivot=[gx, gy + 1.9, gz + 0.5])


def thor_body():
    m = CleanModel('thor', density=D, width=128, seed=11, light=(32.0, 0.0, 0.05))
    rig(m)
    base_body(m, TH)
    c = m.cube
    c('torso', [-3.9, 16.6, -2.5], [7.8, 7.0, 0.5], TH['plate'])
    c('torso', [-3.0, 23.4, -2.3], [6.0, 0.9, 4.6], TH['collar'])
    c('torso', [-4.3, 11.8, -2.3], [8.6, 1.5, 4.6], TH['belt'])
    c('torso', [-1.0, 11.85, -2.55], [2.0, 1.4, 0.3], TH['buckle'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        side = sname(s)
        c(f'{side}_arm', [side_x(s, 4.15, 4.15), 21.4, -2.25], [4.15, 2.85, 4.5], TH['shoulder'])
        c(f'{side}_forearm', [side_x(s, 3.75, 4.5), 14.6, -2.25], [4.5, 3.1, 4.5], TH['bracer'])
        c(f'{side}_shin', [side_x(s, -0.1, 4.3), 0.0, -2.2], [4.3, 6.2, 4.4], TH['boot'])
        c(f'{side}_shin', [side_x(s, 0.2, 3.7), 0.0, -2.7], [3.7, 1.4, 0.6], TH['boot'])
    return m


def thor_gear():
    m = CleanModel('thor_gear', density=D, width=128, seed=13, light=(32.0, 0.0, 0.05))
    rig(m)
    m.bone('cape', 'torso', [0, 23.6, 2.4])
    m.bone('cape_lower', 'cape', [0, 15, 2.7])
    m.bone('hair', 'head', [0, 30, 0])
    m.bone('hammer', 'right_hand', [-6, 13.2, 0])
    c = m.cube
    # six silver discs on the chest plate, two cape clasp discs on the collar bones
    for x in (-1.95, 1.95):
        for y in (21.2, 18.9, 16.9):
            c('torso', [x - 0.9, y - 0.9, -3.05], [1.8, 1.8, 0.3], TH['disc'], faces=('north', 'south'))
    for s in (-1, 1):
        c('torso', [side_x(s, 2.6, 1.6), 22.4, -2.75], [1.6, 1.6, 0.3], TH['disc'], faces=('north', 'south'))
    # cape: shoulder piece and three hanging folds
    c('cape', [-4.6, 15.0, 2.45], [9.2, 8.6, 0.45], TH['cape'])
    for x0, w, dz, y0 in ((-4.9, 3.3, 0.0, 1.6), (-1.6, 3.2, 0.15, 1.3), (1.6, 3.3, 0.0, 1.6)):
        c('cape_lower', [x0, y0, 2.6 + dz], [w, 15.0 - y0 + 0.4, 0.45], TH['cape'])
    # hair: crown, fringe, long sides, back, two front locks
    c('hair', [-4.45, 30.9, -4.45], [8.9, 1.7, 8.9], TH['hair'])
    c('hair', [-4.45, 29.6, -4.65], [8.9, 1.4, 0.9], TH['hair'])
    for s in (-1, 1):
        c('hair', [side_x(s, 4.05, 0.9), 22.2, -3.4], [0.9, 9.0, 7.6], TH['hair'])
        c('hair', [side_x(s, 3.25, 1.6), 22.6, -4.6], [1.6, 7.2, 0.95], TH['hair_lo'])
    c('hair', [-4.45, 21.4, 4.05], [8.9, 9.8, 0.9], TH['hair'])
    mjolnir(m, 'hammer', -6.0, 13.2, 0.0)
    return m
