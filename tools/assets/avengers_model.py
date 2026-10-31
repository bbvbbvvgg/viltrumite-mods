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
    """Plain red armour: the gold gauntlets and the shoulder pads are separate gear blocks."""
    return


def im_leg(face, rgb, glow, rs):
    """Red legs; the gold thigh plates and the silver boots are gear blocks; one gold shin panel at the front."""
    x, y, z = face.world()
    if face.name == 'north':
        cx = 2.0 if np.mean(x) > 0 else -2.0
        put(rgb, (y < 5.6) & (y > 3.2) & (np.abs(x - cx) < 1.3), IM_GOLD)


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
        gold = ((ax < 3.1) & (y < 30.4)) | ((ax < 2.0) & (y < 31.4))           # the gold face inside the red helmet
        put(rgb, ~gold, IM_RED)
        put(rgb, (ax < 1.6) & (np.abs(y - 25.3) < 0.14), IM_GOLD_LO)       # mouth slit
        put(rgb, eye, '#f4fbff')
        glow_put(glow, eye, (200, 240, 255, 255))
    elif face.name in ('up', 'east', 'west'):
        put(rgb, np.ones(rgb.shape[:2], bool), IM_RED)
    elif face.name == 'south':
        put(rgb, np.ones(rgb.shape[:2], bool), '#3a3d44')                   # inside of the faceplate


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
    m.cube('faceplate', [-4.35, 24.1, -4.5], [8.7, 8.35, 0.9], IM['faceplate'])
    m.cube('faceplate', [-1.2, 23.6, -4.65], [2.4, 1.2, 1.2], IM['gold'])
    m.cube('torso', [-1.05, 19.55, -2.35], [2.1, 2.1, 0.3], IM['reactor'], faces=('north',))
    m.cube('torso', [-4.25, 22.3, -2.25], [8.5, 1.9, 4.5], IM['plate'])
    for sx, bone in ((-1, 'shoulder_right'), (1, 'shoulder_left')):
        x0 = 4.1 if sx > 0 else -8.6
        m.cube(bone, [x0, 21.2, -2.4], [4.5, 3.2, 4.8], IM['plate'])
        m.cube(bone, [x0 + 0.4, 24.0, -2.0], [3.8, 0.7, 4.0], IM['gold'])
    m.cube('back_flaps', [-3.2, 15.5, 2.0], [2.6, 6.0, 0.6], IM['plate'])
    m.cube('back_flaps', [0.6, 15.5, 2.0], [2.6, 6.0, 0.6], IM['plate'])
    for side, cx in (('right', -6.0), ('left', 6.0)):
        m.cube(f'{side}_forearm', [cx - 2.0, 15.2, -2.0], [4.0, 3.0, 4.0], IM['gold'], inflate=0.3)
        m.cube(f'{side}_fingers', [cx - 0.9, 11.98, -0.9], [1.8, 0.02, 1.8], IM['repulsor'], faces=('down',))
        m.cube(f'{side}_hand', [cx + (-2.32 if side == 'left' else 2.02), 13.4, -0.9], [0.3, 1.8, 1.8], IM['repulsor'],
               faces=('east',) if side == 'left' else ('west',))
    for side, s in (('right', -1), ('left', 1)):
        cx = s * 2.0
        # boot shells and thigh plates stop short of the centre line: the two legs never share a face plane
        m.cube(f'{side}_shin', [side_x(s, 0.05, 4.25), -0.17, -2.25], [4.25, 3.1, 4.7], IM['silver'])
        m.cube(f'{side}_shin', [cx - 1.0, -0.25, -1.0], [2.0, 0.08, 2.0], IM['jet'], faces=('down',))
        m.cube(f'{side}_leg', [side_x(s, 0.1, 3.8), 7.0, -2.3], [3.8, 4.0, 0.4], IM['gold'])
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
        put(rgb, (y < 16.6) & chk, TH_MAIL_LO, 0.22)
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
        put(rgb, chk, TH_MAIL_LO, 0.22)


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
    m.cube(bone, [gx - hw / 2, hy0 + 0.3, gz - hl / 2 + 0.15], [hw, hh - 0.6, hl - 0.3], TH['hammer'])
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
    m.cube(bone, [gx - 0.1, gy + 1.78, gz + 0.5], [0.2, 0.22, 1.1], TH['strap'], rotation=[-25, 0, 0], pivot=[gx, gy + 1.9, gz + 0.5])


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
        c(f'{side}_shin', [side_x(s, 0.02, 4.2), 0.0, -2.2], [4.2, 6.2, 4.4], TH['boot'])
        c(f'{side}_shin', [side_x(s, 0.2, 3.7), 0.0, -2.75], [3.7, 1.4, 0.55], TH['boot'])           # toe cap
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
    c('hair', [-4.4, 29.6, -4.65], [8.8, 1.4, 0.9], TH['hair'])
    for s in (-1, 1):
        c('hair', [side_x(s, 4.05, 0.9), 22.2, -3.4], [0.9, 9.0, 7.6], TH['hair'])
        c('hair', [side_x(s, 3.25, 1.6), 22.6, -4.6], [1.6, 7.2, 0.95], TH['hair_lo'])
    c('hair', [-4.4, 21.4, 4.05], [8.8, 9.6, 0.9], TH['hair'])
    mjolnir(m, 'hammer', -6.0, 13.2, 0.0)
    return m
# ============================================================================================ BLACK WIDOW (2012)
BW_SUIT, BW_SUIT_HI, BW_SUIT_LO = '#1d1e25', '#2e3039', '#121318'
BW_SILVER, BW_SILVER_LO = '#b9c0ca', '#878f9b'
BW_RED, BW_RED_HI = '#c81f2b', '#e5434c'
BW_SKIN, BW_SKIN_LO, BW_SKIN_SH = '#f1cdb4', '#ddb092', '#c99a7d'
BW_HAIR, BW_HAIR_HI, BW_HAIR_LO = '#b2402a', '#cf5c3b', '#80291a'
BW_GUN, BW_GUN_HI = '#2b2d33', '#474b54'
BW_BLUE = (90, 185, 255)

WIDOW_FACE = [
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHhsssHH',
    'HHHHHHHHssssssHH',
    'HHHHHsssssssssHH',
    'HHsbbbsssssbbbsH',
    'HssWGPssssPGWssH',
    'HssWGGssssGGWssH',
    'Hsssssssssssssss',
    'ssssssssssssssss',
    'sssssssnssssssss',
    'sssssssLLsssssss',
    'ssssssLLLLssssss',
    'ssssssssssssssss',
    'sssssssssssssssS',
]
WIDOW_PAL = {'H': BW_HAIR, 'h': BW_HAIR_LO, 'b': '#6e2a1c', 'W': '#f7f5f2', 'G': '#4f8a5a', 'P': '#1f3324',
             'n': BW_SKIN_SH, 'L': '#b8323c', 'S': BW_SKIN_LO}


def bw_torso(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'north':
        put(rgb, (x > -0.26) & (x < 0.0) & (y > 18.5), '#3b3e48')          # the zip (upper chest only)
        put(rgb, (x > -0.26) & (x < 0.0) & (y > 22.6), BW_SILVER_LO)       # zip pull under the collar
    elif face.name in ('east', 'west'):
        put(rgb, np.abs(z) < 0.3, BW_SUIT_HI, 0.5)                         # side seam


def bw_glove(face, rgb, glow, rs):
    if face.name == 'down':
        put(rgb, np.ones(rgb.shape[:2], bool), BW_SUIT_LO)


def bw_boot(face, rgb, glow, rs):
    x, y, z = face.world()
    if sides(face):
        put(rgb, y < 0.7, BW_SUIT_LO)
    if face.name == 'down':
        put(rgb, np.ones(rgb.shape[:2], bool), BW_SUIT_LO)


def hourglass_fn(face, rgb, glow, rs):
    """Red hourglass on the round black buckle."""
    if face.name != 'north':
        return
    u, v = face.grid()
    put(rgb, np.hypot(u - 0.5, v - 0.5) > 0.42, BW_SILVER_LO)
    tri = (np.abs(u - 0.5) < np.abs(v - 0.5) * 0.9) & (np.abs(v - 0.5) < 0.3)
    put(rgb, tri, BW_RED)


def bite_fn(face, rgb, glow, rs):
    """Widow's Bite bracelet: silver band with three blue charge cells on the outside."""
    if not sides(face):
        return
    h, w = rgb.shape[:2]
    put(rgb, np.s_[:1, :], BW_SILVER_LO, 0.8)
    put(rgb, np.s_[-1:, :], BW_SILVER_LO, 0.8)
    if face.name in ('east', 'west', 'north'):
        for col in range(1, w - 1, 3):
            put(rgb, np.s_[1:h - 1, col:col + 1], '#bfe6ff')
            glow_put(glow, np.s_[1:h - 1, col:col + 1], BW_BLUE + (255,))


def curls_fn(face, rgb, glow, rs):
    """Red hair: flat colour, a lighter crown, two soft wave locks per side face, darker tips (no pattern)."""
    if face.name == 'up':
        put(rgb, np.ones(rgb.shape[:2], bool), BW_HAIR_HI, 0.3)
        return
    if not sides(face):
        return
    h, w = rgb.shape[:2]
    if w >= 6 and h >= 6:
        for col in (w // 3, (2 * w) // 3):
            put(rgb, np.s_[1:h - 2, col:col + 1], BW_HAIR_HI, 0.35)
    put(rgb, np.s_[-1:, :], BW_HAIR_LO, 0.7)


BW = {
    'head': face_material(BW_SKIN, WIDOW_FACE, WIDOW_PAL, sides_col=BW_HAIR, back_col=BW_HAIR, top_col=BW_HAIR),
    'neck': Flat(BW_SKIN_LO),
    'torso': Flat(BW_SUIT, bw_torso),
    'arm': Flat(BW_SUIT),
    'forearm': Flat(BW_SUIT),
    'hand': Flat(BW_SUIT_LO, bw_glove),
    'fingers': Flat(BW_SUIT_LO, bw_glove),
    'leg': Flat(BW_SUIT),
    'shin': Flat(BW_SUIT, bw_boot),
    'collar': Flat(BW_SUIT_HI),
    'belt': Flat('#3a3d46', lambda f, r, g, s: put(r, np.s_[:1, :], BW_SILVER_LO, 0.6) if sides(f) else None),
    'buckle': Flat('#15161b', hourglass_fn),
    'boot': Flat(BW_SUIT_LO, bw_boot),
    'shoulder': Flat(BW_SUIT_HI),
    'hair': Flat(BW_HAIR, curls_fn),
    'hair_curl': Flat(BW_HAIR_LO, curls_fn),
    'bite': Flat(BW_SILVER, bite_fn),
    'holster': Flat('#24252b', lambda f, r, g, s: put(r, np.s_[:1, :], '#3d3f47', 0.9) if sides(f) else None),
    'strap': Flat('#272930'),
    'gun': Flat(BW_GUN),
    'gun_hi': Flat(BW_GUN_HI, lambda f, r, g, s: put(r, np.s_[:1, :], '#6a6f7a', 0.7) if sides(f) else None),
}


def widow_body():
    m = CleanModel('black_widow', density=D, width=128, seed=21, light=(32.0, 0.0, 0.05))
    rig(m, slim=True)
    base_body(m, BW, slim=True)
    c = m.cube
    c('torso', [-2.6, 23.3, -2.25], [5.2, 1.0, 4.5], BW['collar'])                 # high suit collar
    c('torso', [-4.25, 12.2, -2.25], [8.5, 1.3, 4.5], BW['belt'])
    c('torso', [-1.1, 11.95, -2.55], [2.2, 1.8, 0.3], BW['buckle'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        side = sname(s)
        c(f'{side}_arm', [side_x(s, 4.1, 3.0), 21.6, -2.15], [3.0, 2.6, 4.3], BW['shoulder'])   # padded shoulder
        c(f'{side}_shin', [side_x(s, 0.02, 4.03), 0.0, -2.15], [4.03, 4.6, 4.3], BW['boot'])
        c(f'{side}_shin', [side_x(s, 0.35, 3.35), 0.0, -2.6], [3.35, 1.2, 0.45], BW['boot'])
    return m


def pistol(m, bone, gx, gy, gz):
    """Grip in the fist, barrel along hand -Z (forward when the arm is levelled)."""
    m.cube(bone, [gx - 0.35, gy - 0.95, gz - 0.42], [0.7, 1.9, 0.84], BW['gun'])
    m.cube(bone, [gx - 0.42, gy + 0.9, gz - 3.05], [0.84, 0.95, 3.7], BW['gun_hi'])
    m.cube(bone, [gx - 0.18, gy + 1.05, gz - 3.3], [0.36, 0.5, 0.3], BW['gun'])     # muzzle


def widow_gear():
    m = CleanModel('black_widow_gear', density=4, width=128, seed=23, light=(32.0, 0.0, 0.05))
    rig(m, slim=True)
    m.bone('hair', 'head', [0, 30, 0])
    m.bone('right_gun', 'right_hand', [-5.5, 12.6, 0])
    m.bone('left_gun', 'left_hand', [5.5, 12.6, 0])
    c = m.cube
    # 2012: shoulder-length red waves, side part on her left, volume at the sides, curled ends on the shoulders
    c('hair', [-4.5, 31.6, -4.5], [9.0, 1.0, 9.0], BW['hair'])                      # crown
    c('hair', [-4.7, 25.0, -1.2], [9.4, 6.9, 5.9], BW['hair'])                      # back
    c('hair', [-4.95, 22.4, 0.2], [9.9, 2.8, 4.9], BW['hair_curl'])                 # curls on the nape
    for s in (-1, 1):
        x0 = 4.05 if s > 0 else -5.05
        c('hair', [x0, 25.2, -4.3], [1.0, 6.6, 4.0], BW['hair'])                    # side volume
        c('hair', [x0 + (0.15 if s > 0 else -0.35), 22.6, -3.9], [1.2, 2.9, 3.8], BW['hair_curl'])
        c('hair', [x0 + (0.0 if s > 0 else -0.1), 24.8, 0.0], [1.1, 1.4, 3.6], BW['hair_curl'])
    c('hair', [-4.45, 29.7, -4.85], [5.6, 2.0, 0.8], BW['hair'], rotation=[0, 0, 9], pivot=[-1.6, 30.6, -4.4])
    c('hair', [1.6, 30.3, -4.75], [2.9, 1.5, 0.7], BW['hair'], rotation=[0, 0, -14], pivot=[3.0, 31.0, -4.4])
    c('hair', [-4.6, 27.4, -4.65], [0.9, 2.6, 0.55], BW['hair_curl'])
    for s in (-1, 1):
        side = sname(s)
        # Widow's Bite bracelets (glowing cells), thigh holster with two straps
        c(f'{side}_forearm', [side_x(s, 3.8, 3.4), 15.1, -2.1], [3.4, 1.4, 4.2], BW['bite'])
        x0 = side_x(s, 3.95, 0.6)
        c(f'{side}_leg', [x0, 6.6, -1.25], [0.6, 3.6, 2.5], BW['holster'])
        c(f'{side}_leg', [side_x(s, 0.02, 4.2), 9.3, -2.2], [4.2, 0.6, 4.4], BW['strap'])
    pistol(m, 'right_gun', -5.5, 12.6, 0)
    pistol(m, 'left_gun', 5.5, 12.6, 0)
    return m


# ============================================================================================ HAWKEYE (2012)
HK_VEST, HK_VEST_HI, HK_VEST_LO = '#22202a', '#363241', '#15141a'
HK_PURPLE, HK_PURPLE_HI = '#553374', '#6e4596'
HK_SKIN, HK_SKIN_LO, HK_SKIN_SH = '#e6bc98', '#d0a27f', '#bb8c6b'
HK_HAIR, HK_HAIR_LO = '#7a5a36', '#5a4026'
HK_PANTS, HK_PANTS_LO = '#25242b', '#18171c'
HK_LEATHER, HK_LEATHER_LO = '#3a2f2a', '#2a221e'

HAWKEYE_FACE = [
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHhHHHHHHHHhHHHH',
    'HssssssssssssssH',
    'ssssssssssssssss',
    'ssssssssssssssss',
    'ssbbbbsssbbbbsss',
    'sssWIPsssPIWssss',
    'ssssssssssssssss',
    'sssssssnssssssss',
    'ssssssnnnsssssss',
    'sseessssssssees s'[:16],
    'sseeeLLLLLeeeeSs'[:16],
    'sseeeeeeeeeeeess',
    'ssseeeeeeeeeesss',
    'sssseeeeeeeessss',
]
HAWKEYE_PAL = {'H': HK_HAIR, 'h': HK_HAIR_LO, 'b': '#5a4026', 'W': '#f4f2ee', 'I': '#5f7fa0', 'P': '#1d2633',
               'n': HK_SKIN_SH, 'L': '#a8685a', 'e': '#d8ad8b', 'S': HK_SKIN_LO}


def hk_vest(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'north':
        # purple panels over the shoulders running down to the chest, a dark centre closure
        put(rgb, (np.abs(x) > 1.4) & (np.abs(x) < 3.2) & (y > 18.5), HK_PURPLE)
        put(rgb, np.abs(x) < 0.25, HK_VEST_LO)
    elif face.name == 'south':
        put(rgb, (np.abs(x) > 1.4) & (np.abs(x) < 3.2) & (y > 19.5), HK_PURPLE)
    elif face.name == 'up':
        put(rgb, (np.abs(x) > 1.4) & (np.abs(x) < 3.2), HK_PURPLE)


def hk_boot(face, rgb, glow, rs):
    x, y, z = face.world()
    if sides(face):
        put(rgb, y < 0.7, HK_VEST_LO)
    if face.name == 'down':
        put(rgb, np.ones(rgb.shape[:2], bool), HK_VEST_LO)


def bracer_hk(face, rgb, glow, rs):
    if sides(face):
        put(rgb, np.s_[:1, :], HK_PURPLE, 0.9)
        put(rgb, np.s_[-1:, :], HK_PURPLE, 0.9)


def fletch_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], HK_PURPLE)
    if sides(face) and face.w > 2:
        put(rgb, np.s_[:, :1], HK_PURPLE_HI, 0.8)


HK = {
    'head': face_material(HK_SKIN, HAWKEYE_FACE, HAWKEYE_PAL, sides_col=HK_HAIR, back_col=HK_HAIR, top_col=HK_HAIR),
    'neck': Flat(HK_SKIN_LO),
    'torso': Flat(HK_VEST, hk_vest),
    'arm': Flat(HK_SKIN),
    'forearm': Flat(HK_SKIN),
    'hand': Flat(HK_SKIN),
    'fingers': Flat(HK_SKIN_LO),
    'leg': Flat(HK_PANTS),
    'shin': Flat(HK_PANTS, hk_boot),
    'collar': Flat(HK_VEST_HI),
    'shoulder': Flat(HK_PURPLE, lambda f, r, g, s: put(r, np.s_[-1:, :], HK_VEST_LO, 0.7) if sides(f) else None),
    'belt': Flat('#2d2a31', lambda f, r, g, s: put(r, np.s_[:1, :], '#4a4552', 0.7) if sides(f) else None),
    'buckle': Flat('#9aa1ab'),
    'boot': Flat(HK_VEST_LO, hk_boot),
    'bracer': Flat(HK_LEATHER, bracer_hk),
    'glove': Flat(HK_LEATHER_LO),
    'pouch': Flat('#2a2730'),
    'bow': Flat('#2a2b31', lambda f, r, g, s: put(r, np.s_[:1, :], '#4b4e57', 0.7) if sides(f) else None),
    'bow_hi': Flat('#55327a'),
    'cam': Flat('#8d949e'),
    'string': Flat('#d8dbe0', light=False),
    'shaft': Flat('#2a2b31'),
    'tip': Flat('#b8bec8'),
    'fletch': Flat(HK_PURPLE, fletch_fn),
    'quiver': Flat('#26252c', lambda f, r, g, s: put(r, np.s_[:1, :], HK_PURPLE, 0.9) if sides(f) else None),
    'guard': Flat(HK_LEATHER, bracer_hk),
}


def hawkeye_body():
    m = CleanModel('hawkeye', density=D, width=128, seed=31, light=(32.0, 0.0, 0.05))
    rig(m)
    base_body(m, HK)
    c = m.cube
    c('torso', [-2.8, 23.3, -2.25], [5.6, 1.0, 4.5], HK['collar'])
    c('torso', [-4.25, 11.9, -2.25], [8.5, 1.5, 4.5], HK['belt'])
    c('torso', [-0.9, 12.05, -2.55], [1.8, 1.2, 0.3], HK['buckle'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        side = sname(s)
        c(f'{side}_arm', [side_x(s, 4.1, 4.2), 21.7, -2.2], [4.2, 2.5, 4.4], HK['shoulder'])   # vest shoulder
        c(f'{side}_shin', [side_x(s, 0.02, 4.06), 0.0, -2.15], [4.06, 4.2, 4.3], HK['boot'])
        c(f'{side}_shin', [side_x(s, 0.3, 3.4), 0.0, -2.65], [3.4, 1.2, 0.5], HK['boot'])
        c(f'{side}_leg', [side_x(s, 3.95, 0.5), 7.4, -1.1], [0.5, 2.6, 2.2], HK['pouch'])     # thigh pouch
    # the bow arm (left) wears a long bracer, the draw hand (right) a shooting glove
    c('left_forearm', [3.8, 14.9, -2.2], [4.4, 3.0, 4.4], HK['bracer'])
    c('right_hand', [-8.2, 12.8, -2.2], [4.4, 2.4, 4.4], HK['glove'])
    return m


def hawkeye_gear():
    m = CleanModel('hawkeye_gear', density=4, width=128, seed=33, light=(32.0, 0.0, 0.05))
    rig(m)
    m.bone('bow', 'left_hand', [6.0, 13.6, 0])
    m.bone('bow_top', 'bow', [6.0, 13.6, -1.0])
    m.bone('bow_bottom', 'bow', [6.0, 13.6, 1.0])
    m.bone('arrow', 'right_hand', [-6.0, 12.8, 0])
    m.bone('quiver', 'torso', [0, 18, 2.4])
    c = m.cube
    # short spiky hair on top of the head skin
    c('head', [-4.3, 31.7, -4.3], [8.6, 0.8, 8.6], Flat(HK_HAIR, lambda f, r, g, s: put(r, np.s_[-1:, :], HK_HAIR_LO, 0.6) if sides(f) else None))
    c('head', [-4.35, 30.6, -4.6], [8.7, 1.3, 0.7], Flat(HK_HAIR))
    # compound recurve bow: riser in the fist, limbs along hand Z, cams at the tips, a taut string
    c('bow', [5.5, 12.4, -1.1], [1.0, 2.4, 2.2], HK['bow_hi'])
    c('bow', [5.6, 11.8, -0.6], [0.8, 0.5, 1.2], HK['bow'])                        # grip below the fist
    c('bow_top', [5.65, 13.1, -9.0], [0.7, 1.0, 8.0], HK['bow'], rotation=[-12, 0, 0], pivot=[6.0, 13.6, -1.0])
    c('bow_bottom', [5.65, 13.1, 1.0], [0.7, 1.0, 8.0], HK['bow'], rotation=[12, 0, 0], pivot=[6.0, 13.6, 1.0])
    c('bow_top', [5.55, 14.6, -9.5], [0.9, 1.4, 1.1], HK['cam'])
    c('bow_bottom', [5.55, 14.6, 8.4], [0.9, 1.4, 1.1], HK['cam'])
    c('bow', [5.95, 15.65, -8.8], [0.1, 0.1, 17.6], HK['string'])
    # nocked arrow in the right hand (shaft along hand -Z)
    c('arrow', [-6.15, 12.65, -10.0], [0.3, 0.3, 11.0], HK['shaft'])
    c('arrow', [-6.3, 12.5, -11.1], [0.6, 0.6, 1.2], HK['tip'])
    c('arrow', [-6.05, 12.2, 0.2], [0.1, 1.2, 1.6], HK['fletch'])
    c('arrow', [-6.6, 12.75, 0.25], [1.2, 0.1, 1.5], HK['fletch'])
    # quiver on the back, tilted over the right shoulder, with a fan of fletchings
    c('quiver', [-1.4, 13.4, 2.1], [2.8, 9.2, 1.9], HK['quiver'], rotation=[0, 0, -20], pivot=[0, 18, 2.4])
    for i, (dx, rz) in enumerate(((-1.6, -26), (-0.6, -21), (0.4, -16), (1.3, -11))):
        c('quiver', [dx + 1.6, 22.4, 2.45 + 0.1 * i], [0.5, 2.0, 1.0], HK['fletch'], rotation=[0, 0, rz], pivot=[0, 18, 2.4])
    # strap across the chest
    c('torso', [-4.15, 17.2, -2.35], [8.3, 0.7, 4.7], HK['quiver'], rotation=[0, 0, 32], pivot=[0, 18, 0])
    return m


# ============================================================================================ HULK
# Own rig, drawn x1.15 by the renderer (≈ 3.3 blocks). Same bone names as the player rig for the shared clip code,
# plus chest (breathing / roar), jaw (roar), thumbs (grabs) and feet (stomp). Volume comes from separate muscle
# blocks (deltoids, biceps, triceps, pecs, abs panel, quads, calves), not from painted shading.
HU, HU_HI, HU_LO, HU_LINE = '#6a9b3f', '#83b653', '#4d752d', '#3d6123'
HU_PANTS, HU_PANTS_LO, HU_PANTS_HI = '#5b3584', '#3d2159', '#70479b'
HU_HAIR = '#1c1e1b'

HULK_FACE = [
    'gggggggggggggggg',
    'gggggggggggggggg',
    'gggggggggggggggg',
    'gggggggggggggggg',
    'gggggggggggggggg',
    'gggggggggggggggg',
    'gggggggggggggggg',
    'gggddddggddddggg',
    'ggdWWIdggdIWWdgg',
    'gggddddggddddggg',
    'gggggggnnggggggg',
    'ggggggnNNngggggg',
    'ggcggggggggggcgg',
    'gggLLLLLLLLLLggg',
    'ggggTTTTTTTTgggg',
    'gggMMMMMMMMMMggg',
    'gggMMMMMMMMMMggg',
]
HULK_PAL = {'g': HU, 'd': HU_LINE, 'W': '#e8efd6', 'I': '#3f9a2b', 'n': HU_LO, 'N': '#2f4b1b', 'c': HU_LO,
            'L': '#3a5a22', 'T': '#f0eee2', 'M': '#3b1416'}


def hulk_head_fn(face, rgb, glow, rs):
    if face.name == 'north':
        pixel_map(face, rgb, HULK_FACE, HULK_PAL)
        h, w = rgb.shape[:2]
        for r, line in enumerate(HULK_FACE[:h]):
            for col, ch in enumerate(line[:w]):
                if ch == 'I':
                    glow_put(glow, np.s_[r:r + 1, col:col + 1], (90, 220, 70, 120))
    elif face.name in ('east', 'west'):
        x, y, z = face.world()
        put(rgb, y > 43.6, HU_HAIR)
    elif face.name in ('up', 'south'):
        x, y, z = face.world()
        put(rgb, y > 42.0 if face.name == 'south' else np.ones(rgb.shape[:2], bool), HU_HAIR)


def jaw_fn(face, rgb, glow, rs):
    if face.name == 'north':
        pixel_map(face, rgb, ['gggggggggggggg', 'ggLLLLLLLLLLgg', 'ggTTTTTTTTTTgg', 'ggLLLLLLLLLLgg', 'gggggggggggggg'],
                  HULK_PAL)
    elif face.name == 'up':
        put(rgb, np.ones(rgb.shape[:2], bool), '#5a1d22')                 # tongue / mouth floor
        put(rgb, np.s_[-2:, :], '#f0eee2')                                 # lower teeth along the front edge


def abs_fn(face, rgb, glow, rs):
    """Six-pack: 2 x 3 raised-looking blocks (lit top edge, 1 px definition gaps)."""
    if face.name != 'north':
        return
    h, w = rgb.shape[:2]
    put(rgb, np.s_[:, w // 2:w // 2 + 1], HU_LINE)
    for k in (1, 2):
        r = round(h * k / 3)
        put(rgb, np.s_[r:r + 1, :], HU_LINE)
    for k in (0, 1, 2):
        r = round(h * k / 3) + (1 if k else 0)
        put(rgb, np.s_[r:r + 1, :], HU_HI, 0.6)


def pec_fn(face, rgb, glow, rs):
    if face.name == 'north':
        put(rgb, np.s_[:1, :], HU_HI, 0.7)
        put(rgb, np.s_[-1:, :], HU_LINE, 0.8)


def muscle_fn(face, rgb, glow, rs):
    if face.name == 'down':
        put(rgb, np.ones(rgb.shape[:2], bool), HU_LO, 0.5)


def fist_fn(face, rgb, glow, rs):
    """Closed fist: finger gaps on the palm side (towards the body) and across the knuckle row underneath."""
    x, y, z = face.world()
    palm = (face.name == 'east' and np.mean(x) < 0) or (face.name == 'west' and np.mean(x) > 0)
    h, w = rgb.shape[:2]
    if palm:
        for k in (1, 2, 3):
            put(rgb, np.s_[:, round(w * k / 4):round(w * k / 4) + 1], HU_LINE, 0.7)
    elif face.name == 'down':
        for k in (1, 2, 3):
            put(rgb, np.s_[round(h * k / 4):round(h * k / 4) + 1, :], HU_LINE, 0.7)


def toes_fn(face, rgb, glow, rs):
    if face.name in ('north', 'up'):
        h, w = rgb.shape[:2]
        for k in (1, 2, 3):
            put(rgb, np.s_[:, round(w * k / 4):round(w * k / 4) + 1], HU_LINE, 0.8)


def pants_fn(face, rgb, glow, rs):
    if sides(face):
        put(rgb, np.s_[:1, :], HU_PANTS_HI, 0.5)


def torn_alpha(face, rs):
    """Ragged hem: a deterministic zig-zag of uneven teeth on the side faces, open bottom."""
    a = np.full((face.h, face.w), 255, np.uint8)
    if face.name == 'down':
        return np.zeros_like(a)
    if sides(face):
        h, w = a.shape
        r = np.random.RandomState(face.w * 31 + face.h * 7 + sum(map(ord, face.name)))
        for col in range(w):
            depth = 1 + (col % 3 == 1) + r.randint(0, 2)
            a[max(0, h - depth):, col] = 0
    return a


HM = {
    'head': Flat(HU, hulk_head_fn),
    'jaw': Flat(HU, jaw_fn),
    'brow': Flat(HU_LO, lambda f, r, g, s: put(r, np.s_[-1:, :], HU_LINE, 0.7) if sides(f) else None),
    'hair': Flat(HU_HAIR),
    'skin': Flat(HU, muscle_fn),
    'skin_lo': Flat(HU_LO, muscle_fn),
    'abs': Flat(HU, abs_fn),
    'pec': Flat(HU, pec_fn),
    'bulge': Flat(HU_HI, muscle_fn),
    'fist': Flat(HU, fist_fn),
    'toes': Flat(HU, toes_fn),
    'pants': Flat(HU_PANTS, pants_fn),
    'pants_torn': Flat(HU_PANTS, pants_fn, alpha_fn=torn_alpha),
    'waistband': Flat(HU_PANTS_LO),
    'mouth': Flat('#3b1416'),
}


def hulk_model():
    m = CleanModel('hulk', density=D, width=256, seed=41, light=(46.0, 0.0, 0.06))
    m.bone('root', None, [0, 0, 0])
    m.bone('waist', 'root', [0, 17, 0])
    m.bone('torso', 'waist', [0, 18, 0])
    m.bone('chest', 'torso', [0, 30, -1])
    m.bone('head', 'torso', [0, 38, -2.5])
    m.bone('jaw', 'head', [0, 38.6, -3.6])
    for s in (-1, 1):
        side = sname(s)
        m.bone(f'{side}_arm', 'torso', [s * 11.5, 35, 0])
        m.bone(f'{side}_forearm', f'{side}_arm', [s * 13.5, 25, 0])
        m.bone(f'{side}_hand', f'{side}_forearm', [s * 14.0, 15, 0])
        m.bone(f'{side}_fingers', f'{side}_hand', [s * 14.0, 11, 0])
        m.bone(f'{side}_thumb', f'{side}_hand', [s * 12.1, 13, -3.4])
    for s in (-1, 1):
        side = sname(s)
        m.bone(f'{side}_leg', 'root', [s * 4.2, 17, 0])
        m.bone(f'{side}_shin', f'{side}_leg', [s * 4.4, 8.5, 0])
        m.bone(f'{side}_foot', f'{side}_shin', [s * 4.4, 3.0, -0.5])
    c = m.cube
    # hips and torso: pants top, abdomen with the six-pack panel, a huge chest with pecs, trapezius, upper back
    c('waist', [-7.6, 15.4, -4.6], [15.2, 4.8, 9.2], HM['pants'])
    c('waist', [-7.8, 19.0, -4.8], [15.6, 1.1, 9.6], HM['waistband'])
    c('torso', [-8.2, 19.9, -4.75], [16.4, 8.6, 9.5], HM['skin'])
    c('torso', [-3.6, 20.6, -5.3], [7.2, 6.8, 0.7], HM['abs'])
    for s in (-1, 1):
        c('torso', [side_x(s, 7.6, 1.2), 21.0, -3.8], [1.2, 6.0, 7.2], HM['skin_lo'])    # obliques
    c('chest', [-11.0, 28.0, -6.4], [22.0, 9.6, 12.4], HM['skin'])
    for s in (-1, 1):
        c('chest', [side_x(s, 0.4, 9.2), 29.4, -7.2], [9.2, 6.4, 0.9], HM['pec'])
    c('chest', [-7.0, 36.8, -3.6], [14.0, 3.2, 8.0], HM['skin'])                     # trapezius
    c('chest', [-8.6, 29.6, 5.6], [17.2, 7.0, 1.6], HM['bulge'])                     # upper back
    # head forward of the shoulders: face, brow ridge, hair, ears, jaw (own bone)
    c('head', [-4.0, 37.2, -7.5], [8.0, 8.4, 8.0], HM['head'])
    c('head', [-4.3, 42.0, -8.1], [8.6, 1.3, 1.0], HM['brow'])
    c('head', [-4.3, 44.6, -7.8], [8.6, 1.6, 8.6], HM['hair'])
    c('head', [-4.4, 43.4, -8.25], [8.8, 1.3, 1.0], HM['hair'])
    c('head', [-4.25, 40.4, 0.45], [8.5, 4.4, 0.6], HM['hair'])
    for s in (-1, 1):
        c('head', [side_x(s, 3.95, 0.6), 39.8, -4.2], [0.6, 1.9, 1.3], HM['skin_lo'])  # ears
    c('jaw', [-3.6, 36.4, -8.0], [7.2, 2.4, 6.0], HM['jaw'])
    for s in (-1, 1):
        side = sname(s)
        ax = s * 13.5
        # shoulder, deltoid cap, biceps / triceps
        c(f'{side}_arm', [ax - 4.3, 25.0, -4.3], [8.6, 10.0, 8.6], HM['skin'])
        c(f'{side}_arm', [ax - 5.0, 31.0, -5.0], [10.0, 6.4, 10.0], HM['bulge'])
        c(f'{side}_arm', [ax - 3.4, 25.8, -5.1], [6.8, 4.8, 0.9], HM['bulge'])
        c(f'{side}_arm', [ax - 3.6, 26.2, 4.2], [7.2, 4.8, 0.9], HM['skin'])
        # forearm with the big upper muscle, fist, thumb
        c(f'{side}_forearm', [ax - 4.0 + s * 0.3, 15.0, -4.2], [8.0, 10.0, 8.4], HM['skin'])
        c(f'{side}_forearm', [ax - 4.5 + s * 0.3, 19.6, -4.7], [9.0, 5.0, 9.4], HM['bulge'])
        c(f'{side}_hand', [ax - 3.2 + s * 0.5, 11.0, -3.4], [6.4, 4.2, 6.8], HM['skin'])
        c(f'{side}_fingers', [ax - 3.0 + s * 0.5, 7.6, -3.2], [6.0, 3.6, 6.4], HM['fist'])
        c(f'{side}_thumb', [side_x(s, 10.9, 2.2), 9.8, -4.5], [2.2, 3.6, 2.2], HM['skin'])
        # legs: thigh with the quad bulge, torn hem over the calf, bare foot with toes
        lx = s * 4.3
        c(f'{side}_leg', [lx - 4.0, 8.5, -4.2], [8.0, 9.0, 8.4], HM['pants'])
        c(f'{side}_leg', [lx - 3.4, 10.0, -4.7], [6.8, 6.0, 0.6], HM['pants'])
        c(f'{side}_shin', [lx - 3.9, 5.4, -4.0], [7.8, 3.2, 8.0], HM['pants_torn'])
        c(f'{side}_shin', [lx - 3.4, 2.6, -3.4], [6.8, 6.2, 6.6], HM['skin'])
        c(f'{side}_shin', [lx - 3.0, 4.0, 3.15], [6.0, 3.6, 1.0], HM['bulge'])
        c(f'{side}_foot', [lx - 3.5, 0.0, -5.4], [7.0, 2.8, 8.2], HM['skin_lo'])
        c(f'{side}_foot', [lx - 3.4, 0.0, -6.5], [6.8, 1.5, 1.1], HM['toes'])
    return m


# ============================================================================================ PROPS
def props():
    out = []
    import avengers_thor
    out.append(('mjolnir', avengers_thor.thrown_mjolnir()))
    tips = {'arrow': Flat('#b8bec8'), 'arrow_explosive': Flat('#c0342c', glow=lambda f, g, s: glow_put(g, np.ones(g.shape[:2], bool), (255, 110, 60, 200))),
            'arrow_shock': Flat('#5ab8ff', glow=lambda f, g, s: glow_put(g, np.ones(g.shape[:2], bool), (110, 200, 255, 230)))}
    for key, tip in tips.items():
        a = CleanModel(key, density=6, width=64, seed=47)
        a.bone('arrow', None, [0, 0, 0])
        a.cube('arrow', [-0.15, -0.15, -5.5], [0.3, 0.3, 11.0], HK['shaft'])
        a.cube('arrow', [-0.35, -0.35, -6.9], [0.7, 0.7, 1.5], tip)
        a.cube('arrow', [-0.05, -0.6, 4.0], [0.1, 1.2, 1.6], HK['fletch'])
        a.cube('arrow', [-0.6, -0.05, 4.05], [1.2, 0.1, 1.5], HK['fletch'])
        out.append((key, a))
    mi = CleanModel('mini_missile', density=6, width=64, seed=53)
    mi.bone('missile', None, [0, 0, 0])
    mi.cube('missile', [-0.5, -0.5, -2.5], [1.0, 1.0, 5.0], IM['silver'])
    mi.cube('missile', [-0.4, -0.4, -3.2], [0.8, 0.8, 0.7], IM['plate'])
    mi.cube('missile', [-1.0, -0.05, 1.4], [2.0, 0.1, 1.0], IM['plate'])
    mi.cube('missile', [-0.05, -1.0, 1.45], [0.1, 2.0, 0.9], IM['plate'])
    mi.cube('missile', [-0.45, -0.45, 2.5], [0.9, 0.9, 0.2], IM['jet'], faces=('south',))
    out.append(('mini_missile', mi))
    return out


# ============================================================================================ QUINJET (quarter scale)
# Hollow cabin behind a rear ramp (hinged at the floor, closed = the rear wall; the cutscene opens it to drop Black
# Widow and Hawkeye): benches, seat backs, a ceiling light strip and a red jump light inside. Flat two-tone paint,
# no panel-line grid.
QJ, QJ_HI, QJ_LO = '#4f5864', '#636d7a', '#3a414a'
QJ_IN, QJ_FLOOR, QJ_BENCH, QJ_SEAT = '#6f7884', '#3b4149', '#2f353d', '#3e4b5e'


def glass_fn(face, rgb, glow, rs):
    put(rgb, np.ones(rgb.shape[:2], bool), '#13263a')
    if face.h > 2:
        put(rgb, np.s_[:1, :], '#4f7fa8', 0.7)
    glow_put(glow, np.ones(rgb.shape[:2], bool), (40, 90, 140, 90))


def exhaust_fn(face, rgb, glow, rs):
    u, v = face.grid()
    r = np.hypot((u - 0.5), (v - 0.5)) * 2
    put(rgb, np.ones(r.shape, bool), '#262a30')
    put(rgb, r < 0.78, '#9fdcff')
    glow_put(glow, r < 0.78, (130, 210, 255, 255))


def lamp(colour):
    def fn(face, rgb, glow, rs):
        put(rgb, np.ones(rgb.shape[:2], bool), colour)
        glow_put(glow, np.ones(rgb.shape[:2], bool), colour)
    return fn


A_MARK = ['..##..', '.#..#.', '#....#', '######', '#....#', '#....#']


def emblem_fn(face, rgb, glow, rs):
    """Own Avengers mark on the tail fins: a white ring with a pixel A."""
    if face.name not in ('east', 'west') or face.w < 8:
        return
    h, w = rgb.shape[:2]
    v, u = np.mgrid[0:h, 0:w]
    cx, cy = (w - 1) / 2, (h - 1) / 2
    rad = min(w, h) * 0.42
    r = np.hypot(u - cx, v - cy)
    put(rgb, (r < rad) & (r > rad - 1.3), '#d5dae1')
    g = len(A_MARK)
    s = max(1, int(round(rad * 1.2 / g)))
    x0, y0 = int(round(cx - s * g / 2 + 0.5)), int(round(cy - s * g / 2 + 0.5))
    for rr, line in enumerate(A_MARK):
        for cc, ch in enumerate(line):
            if ch == '#':
                put(rgb, np.s_[y0 + rr * s:y0 + (rr + 1) * s, x0 + cc * s:x0 + (cc + 1) * s], '#d5dae1')


def ramp_fn(face, rgb, glow, rs):
    if face.name == 'north':                       # inside face (the walkway once open)
        put(rgb, np.ones(rgb.shape[:2], bool), '#4a525c')
        put(rgb, np.s_[:, :1], QJ_HI, 0.6)
        put(rgb, np.s_[:, -1:], QJ_HI, 0.6)


def wall_fn(face, rgb, glow, rs):
    """Cabin walls: hull colour outside, light interior grey on the inner face."""
    x, y, z = face.world()
    inner = (face.name == 'east' and np.mean(x) < 0) or (face.name == 'west' and np.mean(x) > 0)
    if inner:
        put(rgb, np.ones(rgb.shape[:2], bool), QJ_IN)


QM = {
    'hull': Flat(QJ),
    'hull_hi': Flat(QJ_HI),
    'belly': Flat(QJ_LO),
    'wall': Flat(QJ, wall_fn),
    'roof': Flat(QJ, lambda f, r, g, s: put(r, np.ones(r.shape[:2], bool), QJ_IN) if f.name == 'down' else None),
    'floor': Flat(QJ_LO, lambda f, r, g, s: put(r, np.ones(r.shape[:2], bool), QJ_FLOOR) if f.name == 'up' else None),
    'bulkhead': Flat(QJ_IN),
    'bench': Flat(QJ_BENCH),
    'seat': Flat(QJ_SEAT),
    'strip': Flat('#e8f6ff', lamp('#cfeaff'), light=False),
    'jump': Flat('#ff3b30', lamp('#ff3b30'), light=False),
    'glass': Flat('#13263a', glass_fn, light=False),
    'engine': Flat(QJ_LO),
    'intake': Flat('#1b1e23'),
    'exhaust': Flat('#262a30', exhaust_fn, light=False),
    'ramp': Flat(QJ, ramp_fn),
    'emblem': Flat(QJ, emblem_fn),
    'red': Flat('#ff3030', lamp('#ff3030'), light=False),
    'green': Flat('#30ff60', lamp('#30ff60'), light=False),
}


def quinjet():
    m = CleanModel('quinjet', density=4, width=512, seed=61, light=(11.0, 2.0, 0.06))
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, 4, 0])
    m.bone('ramp', 'body', [0, 2.0, 15.0])
    m.bone('engine_right', 'body', [-7.5, 6.0, 1.0])
    m.bone('engine_left', 'body', [7.5, 6.0, 1.0])
    m.bone('fin_right', 'body', [-3.5, 9.4, 13.0])
    m.bone('fin_left', 'body', [3.5, 9.4, 13.0])
    c = m.cube
    # cockpit and nose
    c('body', [-3.6, 2.6, -19.0], [7.2, 4.6, 7.0], QM['hull'])
    c('body', [-2.4, 3.2, -24.0], [4.8, 3.2, 5.0], QM['hull_hi'])
    c('body', [-1.2, 3.8, -26.5], [2.4, 1.8, 2.5], QM['hull'])
    c('body', [-3.0, 7.2, -18.5], [6.0, 1.6, 7.0], QM['glass'], rotation=[-12, 0, 0], pivot=[0, 7.2, -11.5])
    c('body', [-2.2, 6.0, -23.0], [4.4, 1.2, 4.6], QM['glass'], rotation=[-16, 0, 0], pivot=[0, 6.0, -18.5])
    # hollow cabin: floor between the walls, walls, roof, hump, front bulkhead
    c('body', [-4.2, 2.0, -12.0], [8.4, 0.8, 26.4], QM['floor'])
    for s in (-1, 1):
        c('body', [side_x(s, 4.2, 0.8), 2.0, -12.0], [0.8, 7.0, 27.0], QM['wall'])
    c('body', [-5.0, 9.0, -12.0], [10.0, 0.8, 27.0], QM['roof'])
    c('body', [-4.0, 9.8, -9.0], [8.0, 1.4, 21.0], QM['hull_hi'])
    c('body', [-4.2, 2.8, -12.0], [8.4, 6.2, 0.6], QM['bulkhead'])
    c('body', [-4.6, 1.6, -11.0], [9.2, 0.4, 24.0], QM['belly'], faces=('down', 'north', 'south', 'east', 'west'))
    # cabin interior: benches with seat backs, a ceiling light strip, the red jump light over the ramp
    for s in (-1, 1):
        c('body', [side_x(s, 3.0, 1.2), 2.8, -9.0], [1.2, 1.3, 18.0], QM['bench'])
        c('body', [side_x(s, 3.8, 0.4), 4.1, -9.0], [0.4, 2.2, 18.0], QM['seat'])
    c('body', [-1.0, 8.85, -10.0], [2.0, 0.15, 23.0], QM['strip'], faces=('down', 'north', 'south', 'east', 'west'))
    c('body', [-0.6, 8.3, 13.6], [1.2, 0.55, 0.6], QM['jump'])
    # rear ramp (closed: the rear wall), hinged at the floor edge
    c('ramp', [-4.2, 2.0, 14.4], [8.4, 7.0, 0.6], QM['ramp'])
    # wings: swept main wings with tip lights, canards
    for s in (-1, 1):
        x0 = 5.0 if s > 0 else -20.0
        c('body', [x0, 4.4, -2.0], [15.0, 1.2, 10.0], QM['hull'], rotation=[0, s * -24, s * -4], pivot=[s * 5.0, 5.0, 0])
        c('body', [x0 + (10.9 if s > 0 else 0.1), 4.6, 4.0], [4.0, 1.1, 5.0], QM['hull_hi'],
          rotation=[0, s * -24, s * -4], pivot=[s * 5.0, 5.0, 0])
        c0 = 3.6 if s > 0 else -9.6
        c('body', [c0, 4.6, -16.0], [6.0, 0.8, 3.6], QM['hull'], rotation=[0, s * -30, 0], pivot=[s * 3.6, 5.0, -15.0])
        tip = 19.0 if s > 0 else -20.0
        c('body', [tip, 4.5, 2.0], [1.0, 1.0, 1.0], QM['green'] if s > 0 else QM['red'])
    # V-tail fins with the mark
    c('fin_right', [-4.0, 9.4, 9.0], [1.0, 7.0, 6.0], QM['emblem'], rotation=[-18, 0, -28], pivot=[-3.5, 9.4, 13.0])
    c('fin_left', [3.0, 9.4, 9.0], [1.0, 7.0, 6.0], QM['emblem'], rotation=[-18, 0, 28], pivot=[3.5, 9.4, 13.0])
    # tilting VTOL engine pods: shell, dark intake ring at the front, glowing exhaust at the back
    for side, s in (('right', -1), ('left', 1)):
        c(f'engine_{side}', [s * 7.5 - 2.2, 3.8, -3.0], [4.4, 4.4, 8.0], QM['engine'])
        c(f'engine_{side}', [s * 7.5 - 1.8, 4.2, -3.4], [3.6, 3.6, 0.4], QM['intake'], faces=('north', 'east', 'west', 'up', 'down'))
        c(f'engine_{side}', [s * 7.5 - 1.6, 4.4, 5.0], [3.2, 3.2, 0.4], QM['exhaust'], faces=('south', 'east', 'west', 'up', 'down'))
    return m


# ============================================================================================ OUTPUT
def main():
    rig_model = body_model(False)
    rig_model.identifier = 'avenger'
    for b in rig_model.bones:  # the player rig's legs overlap by 0.1 px at the centre line: split them cleanly
        if b.name in ('right_leg', 'right_shin', 'left_leg', 'left_shin'):
            for cube in b.cubes:
                cube.origin[0] += -0.1 if b.name.startswith('right') else 0.1
    write_json(os.path.join(RES, W('avenger.geo.json')), rig_model.geo_json())
    write_png(T('iron_man.png'), paint_skin(rig_model, IM_MATS))
    write_model(ironman_gear(), W('iron_man_gear.geo.json'), T('iron_man_gear.png'), T('iron_man_gear_glow.png'))
    import avengers_thor
    tb, tg = avengers_thor.build()
    write_model(tb, W('thor.geo.json'), T('thor.png'))
    write_model(tg, W('thor_gear.geo.json'), T('thor_gear.png'), T('thor_gear_glow.png'))
    # Widow, Hawkeye and Hulk live in their shaded-pipeline modules now; the clean-builder versions above
    # (widow_body, hawkeye_body, hulk_model) are kept for reference only and must not overwrite them.
    import avengers_black_widow
    import avengers_hawkeye
    import avengers_hulk
    for key, mod in (('black_widow', avengers_black_widow), ('hawkeye', avengers_hawkeye)):
        body, gear = mod.build()
        write_model(body, W(f'{key}.geo.json'), T(f'{key}.png'))
        write_model(gear, W(f'{key}_gear.geo.json'), T(f'{key}_gear.png'), T(f'{key}_gear_glow.png'))
    for key, m in avengers_hulk.build():
        write_model(m, W(f'{key}.geo.json'), T(f'{key}.png'), T(f'{key}_glow.png'))
    for key, m in props():
        write_model(m, W(f'{key}.geo.json'), T(f'{key}.png'), T(f'{key}_glow.png'))
    write_model(quinjet(), W('quinjet.geo.json'), T('quinjet.png'), T('quinjet_glow.png'))


if __name__ == '__main__':
    main()
