"""The summoned Avengers of «Avengers, assemble!» (2012 line-up) — own procedural models and pixel art, CC0.

Iron Man Mark VII, Thor (MCU 2012, not the God of War Thor), Black Widow and Hawkeye wear HD skins on the split-limb
player rig (tools/assets/jinwoo_model.py; Widow on the slim rig) plus a gear model per hero (same bone names, own
atlas + glow). Hulk has his own oversized body. Also Mjolnir, the arrows, the mini missile and the Quinjet (built at
quarter scale, the renderer scales it x4). Writes geo/avengers/*.geo.json and textures/entity/avengers/*.png.
"""
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_geo import Mat, Model, put, write_json, write_png, RES  # noqa: E402
from jinwoo_model import body_model, skeleton  # noqa: E402
from captain_model import paint_skin, sides, star_mask, clear_alpha  # noqa: E402


def W(x):
    return os.path.join('geo/avengers', x)


def T(x):
    return os.path.join('textures/entity/avengers', x)


def band(face, rgb, ys, color, w=0.2, amount=0.8):
    x, y, z = face.world()
    for yy in ys:
        put(rgb, np.abs(y - yy) < w, color, amount)


# =================================================================================== IRON MAN MARK VII
IM_RED, IM_RED_HI, IM_RED_LO = '#b3171c', '#d8343a', '#6e0d10'
IM_GOLD, IM_GOLD_LO = '#d6a23b', '#9c6f1f'
IM_SILVER = '#9aa1ab'
TONY_SKIN = '#e0b48f'


def im_head(face, rgb, glow, rs):
    """Layer 0 = Tony Stark's face (seen when the faceplate lifts): dark hair, goatee."""
    x, y, z = face.world()
    if face.name in ('up', 'south'):
        put(rgb, np.s_[:, :], '#2a1d14', 1.0)
        return
    if face.name in ('east', 'west'):
        put(rgb, y > 29.0, '#2a1d14', 1.0)
        put(rgb, (y > 26.5) & (np.abs(z) < 0.8), '#2a1d14', 0.9)
        return
    if face.name == 'down':
        return
    ax = np.abs(x)
    put(rgb, y > 30.0, '#2a1d14', 1.0)
    put(rgb, (ax > 0.8) & (ax < 3.0) & (np.abs(y - 28.85) < 0.2), '#2a1d14', 0.95)
    put(rgb, (ax > 0.9) & (ax < 2.8) & (y > 27.6) & (y < 28.4), '#f1ede6', 1.0)
    put(rgb, (ax > 1.4) & (ax < 2.2) & (y > 27.6) & (y < 28.4), '#5b3a22', 1.0)
    put(rgb, (ax < 1.3) & (y < 26.0) & (y > 24.0), '#2a1d14', 0.95)  # goatee
    put(rgb, (ax < 1.0) & (np.abs(y - 25.6) < 0.2), '#8a4c40', 0.9)
    put(rgb, (ax > 1.0) & (ax < 1.6) & (np.abs(y - 26.2) < 0.25), '#2a1d14', 0.9)  # moustache ends


def im_armor(face, rgb, glow, rs, gold_zone=None):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], IM_RED, 1.0)
    if gold_zone is not None:
        put(rgb, gold_zone(x, y, z), IM_GOLD, 1.0)
    if sides(face) and face.w > 3:
        put(rgb, np.s_[:, 0], IM_RED_LO, 0.5)
        put(rgb, np.s_[:, -1], IM_RED_LO, 0.5)
    n = rs.uniform(0, 1, x.shape)
    put(rgb, n > 0.97, IM_RED_HI, 0.35)


def im_torso(face, rgb, glow, rs):
    x, y, z = face.world()
    im_armor(face, rgb, glow, rs, lambda x, y, z: (y < 17.0) & (np.abs(x) < 2.8) & (face.name == 'north'))
    if face.name == 'north':
        band(face, rgb, (22.6, 19.4, 17.0, 14.6), IM_RED_LO, 0.16, 0.8)
        put(rgb, (np.abs(x) < 0.16) & (y < 19.2), IM_GOLD_LO, 0.7)
        put(rgb, (np.hypot(x, y - 20.6) < 1.5), IM_SILVER, 1.0)
    if face.name == 'south':
        put(rgb, (np.abs(x) > 0.8) & (np.abs(x) < 3.3) & (y > 15) & (y < 22.5), IM_RED_LO, 0.35)
        band(face, rgb, (19.0,), IM_RED_LO, 0.16, 0.8)
    if face.name in ('east', 'west'):
        put(rgb, y < 18, IM_SILVER, 0.65)


def im_arm(face, rgb, glow, rs):
    x, y, z = face.world()
    im_armor(face, rgb, glow, rs, lambda x, y, z: (y < 17.6) & (y > 15.0))
    band(face, rgb, (20.5, 17.6), IM_RED_LO, 0.16, 0.8)
    put(rgb, y < 15.0, IM_RED, 1.0)
    put(rgb, (y < 15.0) & (y > 13.3), IM_SILVER, 0.5)
    if face.name == 'down' and glow is not None:
        pass


def im_leg(face, rgb, glow, rs):
    x, y, z = face.world()
    im_armor(face, rgb, glow, rs, lambda x, y, z: (y > 7.0) & (y < 11.0) & (face.name in ('north', 'east', 'west')))
    band(face, rgb, (6.4, 11.4, 2.8), IM_RED_LO, 0.16, 0.8)
    put(rgb, y < 2.8, IM_SILVER, 0.55)
    put(rgb, (y < 6.2) & (y > 4.0) & (face.name == 'north'), IM_GOLD, 0.8)


IM_MATS = {
    ('head', 0): Mat(TONY_SKIN, noise=2, edge=0.0, fn=im_head),
    ('head', 1): Mat(IM_RED, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('torso', 0): Mat(IM_RED, noise=4, edge=0.0, fn=im_torso),
    ('torso', 1): Mat(IM_RED, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('arm', 0): Mat(IM_RED, noise=4, edge=0.0, fn=im_arm),
    ('arm', 1): Mat(IM_RED, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('leg', 0): Mat(IM_RED, noise=4, edge=0.0, fn=im_leg),
    ('leg', 1): Mat(IM_RED, noise=0, edge=0.0, alpha_fn=clear_alpha),
}


def faceplate_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], IM_GOLD, 1.0)
    if face.name == 'north':
        ax = np.abs(x)
        eye = (ax > 0.8) & (ax < 2.8) & (np.abs(y - (28.2 + (ax - 0.8) * 0.12)) < 0.28)
        put(rgb, eye, '#f4fbff', 1.0)
        put(rgb, (ax < 1.6) & (np.abs(y - 25.3) < 0.14), IM_GOLD_LO, 0.9)
        put(rgb, (np.abs(ax - 1.0) < 0.12) & (y < 27.0), IM_GOLD_LO, 0.6)
        put(rgb, (ax > 3.2), IM_RED, 1.0)
        if glow is not None:
            glow[eye] = [200, 240, 255, 255]


def helmet_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], IM_RED, 1.0)
    if face.name == 'up':
        put(rgb, np.abs(x) < 0.5, IM_RED_LO, 0.6)
    if face.name in ('east', 'west'):
        put(rgb, (np.abs(z) < 0.9) & (np.abs(y - 27.5) < 0.9), IM_SILVER, 0.6)


def glow_disc(color, radius_frac=0.5):
    def fn(face, rgb, glow, rs):
        h, w = face.h, face.w
        v, u = np.mgrid[0:h, 0:w]
        r = np.hypot((u + 0.5) / w - 0.5, (v + 0.5) / h - 0.5) * 2
        put(rgb, np.s_[:, :], '#cfe9ff', 1.0)
        put(rgb, r > 0.75, IM_SILVER, 1.0)
        if glow is not None:
            glow[r < 0.85] = list(color) + [255]
    return fn


IM = {
    'faceplate': Mat(IM_GOLD, noise=3, edge=0.1, fn=faceplate_fn, grain=1),
    'helmet': Mat(IM_RED, noise=4, edge=0.12, fn=helmet_fn),
    'plate': Mat(IM_RED, noise=4, edge=0.2),
    'gold': Mat(IM_GOLD, noise=4, edge=0.2),
    'silver': Mat(IM_SILVER, noise=4, edge=0.2),
    'reactor': Mat('#cfe9ff', noise=0, edge=0.0, fn=glow_disc((170, 230, 255)), grain=1),
    'repulsor': Mat('#cfe9ff', noise=0, edge=0.0, fn=glow_disc((170, 230, 255)), grain=1),
    'jet': Mat('#2a2d33', noise=2, edge=0.1, fn=glow_disc((255, 190, 120)), grain=1),
}


def ironman_gear():
    m = Model('ironman_mk7_gear', density=4, width=128, seed=7)
    skeleton(m, False)
    m.bone('helmet', 'head', [0, 24, 0])
    m.bone('faceplate', 'helmet', [0, 32.3, -4.4])
    m.bone('shoulder_right', 'torso', [-5.6, 23.6, 0])
    m.bone('shoulder_left', 'torso', [5.6, 23.6, 0])
    m.bone('back_flaps', 'torso', [0, 22, 2.2])
    # helmet shell (back + sides + crown), face plate on its own hinge
    m.cube('helmet', [-4.45, 24.0, -3.6], [8.9, 8.45, 8.05], IM['helmet'], faces=('south', 'east', 'west', 'up'))
    m.cube('helmet', [-4.45, 24.0, 4.0], [8.9, 1.2, 0.5], IM['silver'])
    m.cube('faceplate', [-4.35, 24.1, -4.5], [8.7, 8.1, 0.9], IM['faceplate'], faces=('north', 'east', 'west', 'down'))
    m.cube('faceplate', [-1.2, 23.6, -4.65], [2.4, 1.2, 1.2], IM['gold'])
    # chest: arc reactor + gold abdomen plates, collar
    m.cube('torso', [-1.05, 19.55, -2.35], [2.1, 2.1, 0.3], IM['reactor'], faces=('north',))
    m.cube('torso', [-4.25, 22.3, -2.25], [8.5, 1.9, 4.5], IM['plate'])
    for sx, bone in ((-1, 'shoulder_right'), (1, 'shoulder_left')):
        x0 = 4.0 if sx > 0 else -8.6
        m.cube(bone, [x0, 21.2, -2.4], [4.6, 3.2, 4.8], IM['plate'])
        m.cube(bone, [x0 + 0.4, 24.0, -2.0], [3.8, 0.7, 4.0], IM['gold'])
    m.cube('back_flaps', [-3.2, 15.5, 2.0], [2.6, 6.0, 0.6], IM['plate'])
    m.cube('back_flaps', [0.6, 15.5, 2.0], [2.6, 6.0, 0.6], IM['plate'])
    # forearm gauntlets, repulsor palms, boots with jets
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


# =================================================================================== THOR 2012
TH_BLACK, TH_BLACK_HI = '#1c1d22', '#33353d'
TH_MAIL = '#7c818a'
TH_SILVER = '#c3c8d0'
TH_RED, TH_RED_LO = '#a3161c', '#690c11'
TH_HAIR, TH_HAIR_LO = '#d8b25e', '#a3803a'
TH_SKIN = '#e7bf9a'


def th_head(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name in ('up', 'south'):
        put(rgb, np.s_[:, :], TH_HAIR, 1.0)
        return
    if face.name in ('east', 'west'):
        put(rgb, (y > 27.0) | (z > 0.5), TH_HAIR, 1.0)
        put(rgb, (y <= 27.0) & (z <= 0.5) & (y > 24.0), '#b58b55', 0.5)  # stubble
        return
    if face.name == 'down':
        put(rgb, np.s_[:, :], '#b58b55', 0.6)
        return
    ax = np.abs(x)
    put(rgb, y > 30.2, TH_HAIR, 1.0)
    put(rgb, ax > 3.4, TH_HAIR, 1.0)
    put(rgb, (ax > 0.8) & (ax < 3.0) & (np.abs(y - 28.9) < 0.22), '#9b7637', 1.0)
    put(rgb, (ax > 0.9) & (ax < 2.8) & (y > 27.6) & (y < 28.4), '#f3efe8', 1.0)
    put(rgb, (ax > 1.4) & (ax < 2.2) & (y > 27.6) & (y < 28.4), '#3b78c8', 1.0)
    put(rgb, (y < 26.4) & (ax < 3.3), '#b58b55', 0.55)  # beard stubble
    put(rgb, (ax < 1.5) & (np.abs(y - 25.5) < 0.18), '#8d4f43', 0.9)


def th_torso(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], TH_BLACK, 1.0)
    if face.name == 'north':
        # quilted leather, silver ribs at the waist
        put(rgb, (np.mod(x + y, 1.6) < 0.16) & (y > 18), TH_BLACK_HI, 0.6)
        band(face, rgb, (13.5, 14.5, 15.5, 16.5), TH_SILVER, 0.15, 0.55)
    if face.name in ('east', 'west', 'south'):
        put(rgb, np.mod(y, 1.4) < 0.16, TH_BLACK_HI, 0.6)


def th_arm(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], TH_MAIL, 1.0)
    ring = (np.mod(np.floor(y * 2) + np.floor(x * 2 + z * 2), 2) == 0)
    put(rgb, ring, '#5b6068', 0.55)
    put(rgb, y > 21.0, TH_BLACK, 1.0)
    put(rgb, (y < 17.5) & (y > 14.6), TH_BLACK_HI, 1.0)  # bracer
    band(face, rgb, (16.0,), TH_SILVER, 0.2, 0.8)
    put(rgb, y <= 14.6, TH_SKIN, 1.0)
    put(rgb, (y <= 14.6) & (y > 13.6), '#2d2e33', 0.9)


def th_leg(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], TH_BLACK, 1.0)
    put(rgb, np.mod(y, 1.2) < 0.14, TH_BLACK_HI, 0.5)
    put(rgb, y < 6.0, '#2b2522', 1.0)  # boots
    band(face, rgb, (6.0, 3.0), TH_SILVER, 0.18, 0.6)
    put(rgb, y < 0.5, '#100e0d', 1.0)


TH_MATS = {
    ('head', 0): Mat(TH_SKIN, noise=2, edge=0.0, fn=th_head),
    ('head', 1): Mat(TH_HAIR, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('torso', 0): Mat(TH_BLACK, noise=4, edge=0.0, fn=th_torso),
    ('torso', 1): Mat(TH_BLACK, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('arm', 0): Mat(TH_MAIL, noise=3, edge=0.0, fn=th_arm),
    ('arm', 1): Mat(TH_MAIL, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('leg', 0): Mat(TH_BLACK, noise=4, edge=0.0, fn=th_leg),
    ('leg', 1): Mat(TH_BLACK, noise=0, edge=0.0, alpha_fn=clear_alpha),
}


def disc_fn(face, rgb, glow, rs):
    h, w = face.h, face.w
    v, u = np.mgrid[0:h, 0:w]
    r = np.hypot((u + 0.5) / w - 0.5, (v + 0.5) / h - 0.5) * 2
    put(rgb, np.s_[:, :], TH_SILVER, 1.0)
    put(rgb, r < 0.55, '#e4e7ec', 1.0)
    put(rgb, r > 0.9, '#868b94', 1.0)


def cape_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if sides(face) and face.w > 3:
        for _ in range(max(1, face.w // 3)):
            c = rs.randint(0, face.w)
            put(rgb, np.s_[:, c:c + 1], TH_RED_LO, 0.45)
    put(rgb, np.s_[-1:, :], TH_RED_LO, 0.6)


def hammer_head_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], '#8f969f', 1.0)
    if face.w > 4 and face.h > 4:
        put(rgb, np.s_[1:-1, 1:-1], '#a8aeb6', 0.8)
        # Uru knotwork panel on the long faces
        cx, cy = face.w / 2, face.h / 2
        v, u = np.mgrid[0:face.h, 0:face.w]
        knot = (np.abs(np.hypot(u - cx, v - cy) - min(cx, cy) * 0.55) < 0.9)
        put(rgb, knot, '#6c727b', 0.9)
    put(rgb, np.s_[0, :], '#c6cbd2', 0.5)
    if glow is not None:
        glow[:] = [150, 200, 255, 0]


def handle_fn(face, rgb, glow, rs):
    if face.h > 2:
        for k in range(0, face.h, 2):
            put(rgb, np.s_[k:k + 1, :], '#3a2414', 0.7)


TH = {
    'disc': Mat(TH_SILVER, noise=0, edge=0.0, fn=disc_fn, grain=1),
    'cape': Mat(TH_RED, noise=5, edge=0.05, fn=cape_fn),
    'hair': Mat(TH_HAIR, noise=10, edge=0.08, grain=1),
    'hair_lo': Mat(TH_HAIR_LO, noise=8, edge=0.08, grain=1),
    'silver': Mat(TH_SILVER, noise=3, edge=0.15),
    'black': Mat(TH_BLACK, noise=3, edge=0.15),
    'hammer': Mat('#8f969f', noise=3, edge=0.1, fn=hammer_head_fn, grain=1),
    'handle': Mat('#5a3a22', noise=3, edge=0.05, fn=handle_fn, grain=1),
    'strap': Mat('#3a2414', noise=2, edge=0.0),
}


def mjolnir(m, bone, gx, gy, gz):
    """Handle through the fist along Y, the head below it (long axis front-back)."""
    m.cube(bone, [gx - 0.45, gy - 4.6, gz - 0.45], [0.9, 6.2, 0.9], TH['handle'])
    m.cube(bone, [gx - 0.55, gy + 1.5, gz - 0.55], [1.1, 0.7, 1.1], TH['silver'])
    m.cube(bone, [gx - 0.15, gy + 1.6, gz + 0.4], [0.3, 0.3, 1.6], TH['strap'])
    m.cube(bone, [gx - 1.35, gy - 7.4, gz - 2.35], [2.7, 2.8, 4.7], TH['hammer'])


def thor_gear():
    m = Model('thor_mcu_gear', density=4, width=128, seed=11)
    skeleton(m, False)
    m.bone('cape', 'torso', [0, 23.6, 2.4])
    m.bone('cape_lower', 'cape', [0, 15.0, 2.7])
    m.bone('hair', 'head', [0, 30, 0])
    m.bone('hammer', 'right_hand', [-6.0, 13.2, 0])
    for row, y in enumerate((21.4, 18.9, 16.4)):
        for sx in (-1, 1):
            m.cube('torso', [sx * 2.0 - 0.95, y - 0.95, -2.42], [1.9, 1.9, 0.4], TH['disc'])
    # shoulder pauldrons holding the cape
    for sx in (-1, 1):
        x0 = 3.6 if sx > 0 else -8.4
        m.cube('torso', [x0, 22.3, -2.3], [4.8, 2.0, 4.6], TH['silver'], rotation=[0, 0, -12 * sx], pivot=[sx * 6, 23, 0])
    m.cube('cape', [-4.6, 15.0, 2.3], [9.2, 8.6, 0.5], TH['cape'])
    m.cube('cape_lower', [-4.9, 4.2, 2.45], [9.8, 10.8, 0.5], TH['cape'])
    # long blond hair: crown volume and locks to the shoulders
    m.cube('hair', [-4.6, 30.4, -4.6], [9.2, 2.2, 9.2], TH['hair'])
    m.cube('hair', [-4.7, 23.0, 1.0], [9.4, 8.0, 3.8], TH['hair_lo'])
    for sx in (-1, 1):
        x0 = 4.05 if sx > 0 else -4.85
        m.cube('hair', [x0, 22.6, -3.8], [0.8, 8.6, 5.0], TH['hair'])
    m.cube('hair', [-4.3, 30.0, -4.75], [8.6, 1.6, 0.6], TH['hair'])
    mjolnir(m, 'hammer', -6.0, 13.2, 0)
    return m


# =================================================================================== BLACK WIDOW
BW_BLACK, BW_BLACK_HI = '#141519', '#2c2f38'
BW_SILVER = '#b8bec8'
BW_HAIR, BW_HAIR_LO = '#b3361f', '#7a2112'
BW_SKIN = '#efcdb4'


def bw_head(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name in ('up', 'south'):
        put(rgb, np.s_[:, :], BW_HAIR, 1.0)
        return
    if face.name in ('east', 'west'):
        put(rgb, (y > 26.0) | (z > 0.0), BW_HAIR, 1.0)
        return
    if face.name == 'down':
        return
    ax = np.abs(x)
    put(rgb, y > 30.0, BW_HAIR, 1.0)
    put(rgb, (ax > 3.2) & (y > 25.0), BW_HAIR, 1.0)
    put(rgb, (ax > 0.9) & (ax < 2.8) & (np.abs(y - 28.9) < 0.18), '#7a2a1a', 1.0)
    put(rgb, (ax > 0.95) & (ax < 2.7) & (y > 27.6) & (y < 28.4), '#f4f0ea', 1.0)
    put(rgb, (ax > 1.4) & (ax < 2.2) & (y > 27.6) & (y < 28.4), '#4f7a4a', 1.0)
    put(rgb, (ax > 0.8) & (ax < 2.9) & (np.abs(y - 28.42) < 0.12), '#1b0f0c', 1.0)  # lashes
    put(rgb, (ax < 1.3) & (np.abs(y - 25.5) < 0.22), '#b3454a', 1.0)


def bw_torso(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], BW_BLACK, 1.0)
    if face.name == 'north':
        put(rgb, (np.abs(x) < 0.15) & (y > 16), BW_SILVER, 0.8)  # zipper
        put(rgb, (np.abs(np.abs(x) - 2.5) < 0.14), BW_BLACK_HI, 0.7)
        band(face, rgb, (12.7,), BW_SILVER, 0.5, 1.0)  # belt
        hour = (np.abs(y - 12.7) < 0.5) & (np.abs(x) < 0.7) & (np.abs(x) < 0.2 + np.abs(y - 12.7) * 1.0)
        put(rgb, hour, '#d02020', 1.0)
    else:
        band(face, rgb, (12.7,), BW_SILVER, 0.5, 1.0)
        put(rgb, np.abs(y - 18) < 0.14, BW_BLACK_HI, 0.7)


def bw_arm(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], BW_BLACK, 1.0)
    put(rgb, np.abs(y - 20.5) < 0.14, BW_BLACK_HI, 0.8)
    put(rgb, (y < 16.4) & (y > 15.0), BW_SILVER, 1.0)  # Widow's Bite bracelets
    put(rgb, (np.abs(y - 15.7) < 0.2), '#7fc6ff', 1.0)
    if glow is not None:
        glow[(np.abs(y - 15.7) < 0.2)] = [120, 200, 255, 255]
    put(rgb, y < 13.0, '#1f2026', 1.0)


def bw_leg(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], BW_BLACK, 1.0)
    put(rgb, (np.abs(z) < 0.14) & (face.name in ('east', 'west')), BW_BLACK_HI, 0.7)
    put(rgb, y < 5.0, '#0b0b0e', 1.0)
    band(face, rgb, (5.0,), BW_BLACK_HI, 0.15, 0.8)


BW_MATS = {
    ('head', 0): Mat(BW_SKIN, noise=2, edge=0.0, fn=bw_head),
    ('head', 1): Mat(BW_HAIR, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('torso', 0): Mat(BW_BLACK, noise=3, edge=0.0, fn=bw_torso),
    ('torso', 1): Mat(BW_BLACK, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('arm', 0): Mat(BW_BLACK, noise=3, edge=0.0, fn=bw_arm),
    ('arm', 1): Mat(BW_BLACK, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('leg', 0): Mat(BW_BLACK, noise=3, edge=0.0, fn=bw_leg),
    ('leg', 1): Mat(BW_BLACK, noise=0, edge=0.0, alpha_fn=clear_alpha),
}


def curls_fn(face, rgb, glow, rs):
    if face.w > 2 and face.h > 2:
        for _ in range(max(2, face.w * face.h // 10)):
            px, py = rs.randint(0, face.w), rs.randint(0, face.h)
            put(rgb, np.s_[py:py + 1, px:px + 2], BW_HAIR_LO, 0.6)


BW = {
    'hair': Mat(BW_HAIR, noise=12, edge=0.05, fn=curls_fn, grain=1),
    'gun': Mat('#25272c', noise=3, edge=0.15),
    'gun_hi': Mat('#4a4e57', noise=2, edge=0.1),
    'holster': Mat('#111114', noise=2, edge=0.15),
    'bite': Mat('#b8bec8', noise=1, edge=0.0, fn=glow_disc((120, 200, 255), 0.6), grain=1),
}


def pistol(m, bone, gx, gy, gz):
    """Grip in the fist, barrel along hand -Z (forward when the arm is levelled)."""
    m.cube(bone, [gx - 0.35, gy - 0.9, gz - 0.4], [0.7, 1.9, 0.8], BW['gun'])
    m.cube(bone, [gx - 0.4, gy + 0.9, gz - 3.0], [0.8, 0.9, 3.6], BW['gun_hi'])


def widow_gear():
    m = Model('black_widow_gear', density=4, width=128, seed=13)
    skeleton(m, True)
    m.bone('hair', 'head', [0, 30, 0])
    m.bone('right_gun', 'right_hand', [-5.5, 12.6, 0])
    m.bone('left_gun', 'left_hand', [5.5, 12.6, 0])
    # 2012 shoulder-length curled red hair
    m.cube('hair', [-4.55, 30.2, -4.55], [9.1, 2.3, 9.1], BW['hair'])
    m.cube('hair', [-4.8, 23.2, -0.2], [9.6, 7.4, 4.9], BW['hair'])
    for sx in (-1, 1):
        x0 = 4.0 if sx > 0 else -4.9
        m.cube('hair', [x0, 23.4, -4.1], [0.9, 7.2, 4.0], BW['hair'])
    m.cube('hair', [-4.3, 29.6, -4.75], [5.2, 1.9, 0.7], BW['hair'], rotation=[0, 0, 8], pivot=[-1.7, 30.4, -4.4])
    # thigh holsters
    for side, cx in (('right', -1.9), ('left', 1.9)):
        x0 = cx - 2.55 if side == 'right' else cx + 2.0
        m.cube(f'{side}_leg', [x0, 6.5, -1.2], [0.55, 3.4, 2.4], BW['holster'])
    pistol(m, 'right_gun', -5.5, 12.6, 0)
    pistol(m, 'left_gun', 5.5, 12.6, 0)
    return m


# =================================================================================== HAWKEYE
HK_DARK, HK_DARK_HI = '#1d1a22', '#3a3346'
HK_PURPLE = '#4b2f63'
HK_SKIN = '#e3b996'


def hk_head(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name in ('up', 'south'):
        put(rgb, np.s_[:, :], '#8a6a46', 1.0)
        return
    if face.name in ('east', 'west'):
        put(rgb, y > 29.4, '#8a6a46', 1.0)
        return
    if face.name == 'down':
        return
    ax = np.abs(x)
    put(rgb, y > 30.4, '#8a6a46', 1.0)
    put(rgb, (ax > 0.7) & (ax < 3.2) & (y > 27.4) & (y < 28.7), '#141418', 1.0)  # tactical glasses
    put(rgb, (ax > 1.0) & (ax < 2.6) & (y > 27.9) & (y < 28.4), '#5b4a7a', 0.8)
    put(rgb, (ax < 1.4) & (np.abs(y - 25.5) < 0.18), '#9b5a4c', 0.9)


def hk_torso(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], HK_DARK, 1.0)
    if face.name == 'north':
        put(rgb, (x > -1.2) & (x < 4.0) & (y > 14), HK_PURPLE, 0.75)  # asymmetrical vest panel
        put(rgb, np.abs(x + 1.2) < 0.18, HK_DARK_HI, 1.0)
        band(face, rgb, (13.0,), '#2c2c30', 0.45, 1.0)
    else:
        band(face, rgb, (13.0,), '#2c2c30', 0.45, 1.0)
        put(rgb, np.abs(y - 19) < 0.15, HK_DARK_HI, 0.6)


def hk_arm(face, rgb, glow, rs):
    x, y, z = face.world()
    right = face.ox + face.sx / 2 < 0
    put(rgb, np.s_[:, :], HK_DARK, 1.0)
    if right:  # sleeveless right arm with an arm guard
        put(rgb, y < 21.0, HK_SKIN, 1.0)
        put(rgb, (y < 17.6) & (y > 14.6), '#2c2c30', 1.0)
    else:
        put(rgb, np.abs(y - 20.0) < 0.14, HK_DARK_HI, 0.8)
    put(rgb, y < 14.6, '#25222b', 1.0)  # gloves
    if right:
        put(rgb, (y < 13.0) & (face.name == 'north'), HK_SKIN, 0.9)  # shooting-glove fingers


def hk_leg(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], HK_DARK, 1.0)
    put(rgb, (np.abs(y - 8.5) < 1.2) & (face.name == 'north'), '#2c2a33', 1.0)
    put(rgb, y < 5.4, '#111015', 1.0)
    band(face, rgb, (5.4, 2.6), HK_DARK_HI, 0.15, 0.8)


HK_MATS = {
    ('head', 0): Mat(HK_SKIN, noise=2, edge=0.0, fn=hk_head),
    ('head', 1): Mat(HK_DARK, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('torso', 0): Mat(HK_DARK, noise=3, edge=0.0, fn=hk_torso),
    ('torso', 1): Mat(HK_DARK, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('arm', 0): Mat(HK_DARK, noise=3, edge=0.0, fn=hk_arm),
    ('arm', 1): Mat(HK_DARK, noise=0, edge=0.0, alpha_fn=clear_alpha),
    ('leg', 0): Mat(HK_DARK, noise=3, edge=0.0, fn=hk_leg),
    ('leg', 1): Mat(HK_DARK, noise=0, edge=0.0, alpha_fn=clear_alpha),
}


def fletch_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], HK_PURPLE, 1.0)
    if face.h > 2:
        put(rgb, np.s_[::2, :], '#2b1a3a', 0.6)


HK = {
    'bow': Mat('#26232b', noise=3, edge=0.1),
    'bow_hi': Mat('#5a4b6e', noise=2, edge=0.1),
    'string': Mat('#d8d8dc', noise=0, edge=0.0),
    'quiver': Mat('#2a2530', noise=3, edge=0.15),
    'shaft': Mat('#3c3c44', noise=1, edge=0.0),
    'fletch': Mat(HK_PURPLE, noise=2, edge=0.0, fn=fletch_fn, grain=1),
    'tip': Mat('#c4c9d2', noise=1, edge=0.0),
    'guard': Mat('#2c2c30', noise=2, edge=0.15),
}


def hawkeye_gear():
    m = Model('hawkeye_gear', density=4, width=128, seed=17)
    skeleton(m, False)
    m.bone('bow', 'left_hand', [6.0, 13.6, 0])
    m.bone('bow_top', 'bow', [6.0, 13.6, -1.0])
    m.bone('bow_bottom', 'bow', [6.0, 13.6, 1.0])
    m.bone('arrow', 'right_hand', [-6.0, 12.8, 0])
    m.bone('quiver', 'torso', [0, 18, 2.4])
    # recurve compound bow: riser in the fist, limbs along hand Z, cams at the tips
    m.cube('bow', [5.55, 12.6, -1.0], [0.9, 2.2, 2.0], HK['bow_hi'])
    m.cube('bow_top', [5.65, 13.1, -9.0], [0.7, 1.0, 8.0], HK['bow'], rotation=[-12, 0, 0], pivot=[6.0, 13.6, -1.0])
    m.cube('bow_bottom', [5.65, 13.1, 1.0], [0.7, 1.0, 8.0], HK['bow'], rotation=[12, 0, 0], pivot=[6.0, 13.6, 1.0])
    m.cube('bow_top', [5.6, 14.6, -9.4], [0.8, 1.4, 1.0], HK['bow_hi'])
    m.cube('bow_bottom', [5.6, 14.6, 8.4], [0.8, 1.4, 1.0], HK['bow_hi'])
    m.cube('bow', [5.95, 15.6, -8.8], [0.1, 0.1, 17.6], HK['string'])
    # nocked arrow in the right hand (shaft along hand -Z)
    m.cube('arrow', [-6.15, 12.65, -10.0], [0.3, 0.3, 11.0], HK['shaft'])
    m.cube('arrow', [-6.3, 12.5, -11.0], [0.6, 0.6, 1.2], HK['tip'])
    m.cube('arrow', [-6.05, 12.2, 0.2], [0.1, 1.2, 1.6], HK['fletch'])
    # quiver on the back with a fan of fletchings
    m.cube('quiver', [-1.4, 13.4, 2.1], [2.8, 9.2, 1.9], HK['quiver'], rotation=[0, 0, -20], pivot=[0, 18, 2.4])
    for i, (dx, rz) in enumerate(((-1.6, -26), (-0.6, -20), (0.4, -16), (1.3, -10))):
        m.cube('quiver', [dx + 1.6, 22.4, 2.4], [0.5, 2.0, 1.0], HK['fletch'], rotation=[0, 0, rz], pivot=[0, 18, 2.4])
    m.cube('right_forearm', [-8.0, 15.0, -2.0], [4.0, 2.6, 4.0], HK['guard'], inflate=0.25)
    return m


# =================================================================================== HULK
HU_GREEN, HU_GREEN_HI, HU_GREEN_LO = '#5f8a3a', '#7aa54b', '#3e5f26'
HU_PANTS, HU_PANTS_LO = '#4c2c6b', '#311b47'


def muscle_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], HU_GREEN, 1.0)
    if sides(face) and face.w > 4 and face.h > 4:
        u, v = face.grid()
        sh = 0.9 + 0.2 * np.sin(u * np.pi) * np.sin(v * np.pi)
        rgb[:] = rgb * sh[..., None]
        for _ in range(max(1, face.w // 6)):
            c = rs.randint(1, face.w - 1)
            put(rgb, np.s_[:, c:c + 1], HU_GREEN_LO, 0.25)
    if face.name == 'down':
        put(rgb, np.s_[:, :], HU_GREEN_LO, 0.6)


def hulk_torso_fn(face, rgb, glow, rs):
    muscle_fn(face, rgb, glow, rs)
    x, y, z = face.world()
    if face.name == 'north':
        cy = face.oy + face.sy
        put(rgb, (np.abs(x) < 0.2) & (y > face.oy + 3), HU_GREEN_LO, 0.6)  # sternum line
        put(rgb, np.abs(y - (cy - 6.0)) < 0.25, HU_GREEN_LO, 0.55)        # pectorals
        for yy in (cy - 9.0, cy - 11.5, cy - 14):
            put(rgb, (np.abs(y - yy) < 0.2) & (np.abs(x) < 3.5), HU_GREEN_LO, 0.4)  # abs


def hulk_head_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], HU_GREEN, 1.0)
    top = face.oy + face.sy
    if face.name in ('up', 'south'):
        put(rgb, np.s_[:, :], '#1a1712', 1.0)
        return
    if face.name in ('east', 'west'):
        put(rgb, y > top - 2.2, '#1a1712', 1.0)
        return
    if face.name != 'north':
        return
    ax = np.abs(x)
    put(rgb, y > top - 1.6, '#1a1712', 1.0)
    put(rgb, (ax > 0.6) & (ax < 3.2) & (np.abs(y - (top - 2.6)) < 0.35), HU_GREEN_LO, 1.0)  # heavy brow
    put(rgb, (ax > 1.0) & (ax < 2.7) & (y > top - 3.9) & (y < top - 3.0), '#e9e6c8', 1.0)
    put(rgb, (ax > 1.4) & (ax < 2.2) & (y > top - 3.9) & (y < top - 3.0), '#4f7a2a', 1.0)
    put(rgb, (ax < 2.4) & (y > top - 7.2) & (y < top - 6.2), '#2b1d16', 1.0)  # snarl
    put(rgb, (ax < 2.0) & (y > top - 6.9) & (y < top - 6.5), '#eae6d6', 1.0)  # teeth


def pants_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, np.s_[:, :], HU_PANTS, 1.0)
    if sides(face) and face.w > 2:
        for _ in range(max(1, face.w // 3)):
            c = rs.randint(0, face.w)
            put(rgb, np.s_[:, c:c + 1], HU_PANTS_LO, 0.5)


def torn_alpha(face, rs):
    h, w = face.h, face.w
    a = np.full((h, w), 255, np.uint8)
    if sides(face):
        for c in range(w):
            cut = rs.randint(0, max(1, h // 4))
            if cut:
                a[h - cut:, c] = 0
    return a


HU = {
    'skin': Mat(HU_GREEN, noise=5, edge=0.05, fn=muscle_fn),
    'torso': Mat(HU_GREEN, noise=5, edge=0.05, fn=hulk_torso_fn),
    'head': Mat(HU_GREEN, noise=3, edge=0.0, fn=hulk_head_fn, grain=1),
    'pants': Mat(HU_PANTS, noise=5, edge=0.05, fn=pants_fn),
    'pants_torn': Mat(HU_PANTS, noise=5, edge=0.05, fn=pants_fn, alpha_fn=torn_alpha),
    'hair': Mat('#1a1712', noise=6, edge=0.05, grain=1),
    'nail': Mat('#3b4d2a', noise=2, edge=0.0),
}


def hulk_model():
    """Hulk: own proportions (≈ 2.9 blocks), same bone names as the player rig so the clip code is shared."""
    m = Model('hulk', density=3, width=256, seed=19)
    m.bone('root', None, [0, 0, 0])
    m.bone('waist', 'root', [0, 17, 0])
    m.bone('torso', 'waist', [0, 18, 0])
    m.bone('head', 'torso', [0, 38, -2.5])
    m.bone('right_arm', 'torso', [-11.5, 35, 0])
    m.bone('right_forearm', 'right_arm', [-13.5, 25, 0])
    m.bone('right_hand', 'right_forearm', [-14.0, 15, 0])
    m.bone('right_fingers', 'right_hand', [-14.0, 11, 0])
    m.bone('left_arm', 'torso', [11.5, 35, 0])
    m.bone('left_forearm', 'left_arm', [13.5, 25, 0])
    m.bone('left_hand', 'left_forearm', [14.0, 15, 0])
    m.bone('left_fingers', 'left_hand', [14.0, 11, 0])
    m.bone('right_leg', 'root', [-4.2, 17, 0])
    m.bone('right_shin', 'right_leg', [-4.4, 8.5, 0])
    m.bone('left_leg', 'root', [4.2, 17, 0])
    m.bone('left_shin', 'left_leg', [4.4, 8.5, 0])
    # hunched torso: hips, waist, massive chest and trapezius
    m.cube('waist', [-7.5, 15.5, -4.5], [15, 4.5, 9], HU['pants'])
    m.cube('torso', [-8.5, 20, -5.0], [17, 8, 10], HU['torso'])
    m.cube('torso', [-11.0, 28, -6.5], [22, 9.5, 12.5], HU['torso'])
    m.cube('torso', [-6.5, 36.5, -3.5], [13, 3.0, 8], HU['skin'])  # trapezius
    # small head forward of the shoulders
    m.cube('head', [-4.0, 37.2, -7.5], [8, 8.4, 8], HU['head'])
    m.cube('head', [-4.3, 44.0, -7.8], [8.6, 1.8, 8.6], HU['hair'])
    m.cube('head', [-3.2, 36.8, -8.0], [6.4, 2.2, 1.0], HU['skin'])  # jaw
    for side, sx in (('right', -1), ('left', 1)):
        ax = sx * 13.5
        m.cube(f'{side}_arm', [ax - 4.5, 25.0, -4.5], [9, 12, 9], HU['skin'])          # deltoid + biceps
        m.cube(f'{side}_forearm', [ax - 4.0 + sx * 0.3, 15.0, -4.2], [8.0, 10.5, 8.4], HU['skin'])
        m.cube(f'{side}_hand', [ax - 3.2 + sx * 0.5, 11.0, -3.4], [6.4, 4.2, 6.8], HU['skin'])
        m.cube(f'{side}_fingers', [ax - 3.0 + sx * 0.5, 7.4, -3.2], [6.0, 3.8, 6.4], HU['skin'])
        lx = sx * 4.3
        m.cube(f'{side}_leg', [lx - 4.0, 8.5, -4.2], [8.0, 9.0, 8.4], HU['pants'])
        m.cube(f'{side}_shin', [lx - 3.7, 3.2, -3.8], [7.4, 5.6, 7.6], HU['pants_torn'])
        m.cube(f'{side}_shin', [lx - 3.4, 0.0, -4.6], [6.8, 3.4, 8.0], HU['skin'])     # bare feet
    return m


# =================================================================================== MJOLNIR (thrown), ARROWS, MISSILE
def projectiles():
    out = []
    m = Model('mjolnir_mcu', density=6, width=128, seed=23)
    m.bone('hammer', None, [0, 0, 0])
    m.cube('hammer', [-0.45, -1.4, -0.45], [0.9, 6.2, 0.9], TH['handle'])
    m.cube('hammer', [-0.55, 4.7, -0.55], [1.1, 0.7, 1.1], TH['silver'])
    m.cube('hammer', [-1.35, -4.2, -2.35], [2.7, 2.8, 4.7], TH['hammer'])
    out.append(('mjolnir', m))
    for key, tipmat in (('arrow', HK['tip']), ('arrow_explosive', Mat('#b02a2a', noise=1, edge=0.0)),
                        ('arrow_shock', Mat('#58b6ff', noise=1, edge=0.0))):
        a = Model(key, density=6, width=64, seed=29)
        a.bone('arrow', None, [0, 0, 0])
        a.cube('arrow', [-0.15, -0.15, -5.5], [0.3, 0.3, 11.0], HK['shaft'])
        a.cube('arrow', [-0.35, -0.35, -6.9], [0.7, 0.7, 1.5], tipmat)
        a.cube('arrow', [-0.05, -0.6, 4.0], [0.1, 1.2, 1.6], HK['fletch'])
        a.cube('arrow', [-0.6, -0.05, 4.0], [1.2, 0.1, 1.6], HK['fletch'])
        out.append((key, a))
    mi = Model('mini_missile', density=6, width=64, seed=31)
    mi.bone('missile', None, [0, 0, 0])
    mi.cube('missile', [-0.5, -0.5, -2.5], [1.0, 1.0, 5.0], IM['silver'])
    mi.cube('missile', [-0.4, -0.4, -3.2], [0.8, 0.8, 0.7], IM['plate'])
    for rz in (0, 90):
        mi.cube('missile', [-1.0, -0.05, 1.4], [2.0, 0.1, 1.0], IM['plate'], rotation=[0, 0, rz], pivot=[0, 0, 0])
    mi.cube('missile', [-0.45, -0.45, 2.5], [0.9, 0.9, 0.2], IM['jet'], faces=('south',))
    out.append(('mini_missile', mi))
    return out


# =================================================================================== QUINJET (quarter scale)
QJ, QJ_HI, QJ_LO = '#4a525c', '#68717d', '#30363e'


def hull_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if sides(face) or face.name in ('up', 'down'):
        if face.w > 6 and face.h > 4:
            for k in range(2, face.w, 6):
                put(rgb, np.s_[:, k:k + 1], QJ_LO, 0.35)
            for k in range(2, face.h, 5):
                put(rgb, np.s_[k:k + 1, :], QJ_LO, 0.25)
        put(rgb, rs.uniform(0, 1, x.shape) > 0.985, QJ_HI, 0.5)
    if face.name == 'down':
        put(rgb, np.s_[:, :], QJ_LO, 0.4)


def glass_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], '#0e1c2a', 1.0)
    if face.h > 2:
        put(rgb, np.s_[:1, :], '#5d8cb5', 0.6)
    if glow is not None:
        glow[:] = [40, 90, 140, 90]


def engine_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], '#262a30', 1.0)
    if face.name in ('down', 'south'):
        h, w = face.h, face.w
        v, u = np.mgrid[0:h, 0:w]
        r = np.hypot((u + 0.5) / w - 0.5, (v + 0.5) / h - 0.5) * 2
        put(rgb, r < 0.8, '#9fdcff', 1.0)
        if glow is not None:
            glow[r < 0.8] = [130, 210, 255, 255]


def emblem_fn(face, rgb, glow, rs):
    hull_fn(face, rgb, glow, rs)
    if face.name in ('east', 'west') and face.w > 6:
        h, w = face.h, face.w
        v, u = np.mgrid[0:h, 0:w]
        cx, cy = w * 0.5, h * 0.5
        r = np.hypot(u - cx, v - cy) / (min(w, h) * 0.38)
        put(rgb, (r < 1.0) & (r > 0.78), '#c9ced6', 1.0)  # S.H.I.E.L.D.-style roundel (own simple design)
        put(rgb, (r < 0.6) & (np.abs(u - cx) < (0.6 - r) * w * 0.4 + 0.5), '#c9ced6', 1.0)


def light_fn(color):
    def fn(face, rgb, glow, rs):
        put(rgb, np.s_[:, :], color, 1.0)
        if glow is not None:
            c = np.array([int(color[1:3], 16), int(color[3:5], 16), int(color[5:7], 16)])
            glow[:] = list(c) + [255]
    return fn


QM = {
    'hull': Mat(QJ, noise=4, edge=0.12, fn=hull_fn),
    'hull_hi': Mat(QJ_HI, noise=4, edge=0.12, fn=hull_fn),
    'emblem': Mat(QJ, noise=4, edge=0.12, fn=emblem_fn),
    'glass': Mat('#0e1c2a', noise=2, edge=0.0, fn=glass_fn, grain=1),
    'engine': Mat('#262a30', noise=2, edge=0.1, fn=engine_fn, grain=1),
    'ramp': Mat(QJ_LO, noise=4, edge=0.15, fn=hull_fn),
    'red': Mat('#ff3030', noise=0, edge=0.0, fn=light_fn('#ff3030'), grain=1),
    'green': Mat('#30ff60', noise=0, edge=0.0, fn=light_fn('#30ff60'), grain=1),
}


def quinjet():
    m = Model('quinjet', density=2, width=256, seed=37)
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, 4, 0])
    m.bone('ramp', 'body', [0, 2.0, 15.0])
    m.bone('engine_right', 'body', [-7.5, 6.0, 1.0])
    m.bone('engine_left', 'body', [7.5, 6.0, 1.0])
    m.bone('fin_right', 'body', [-3.5, 8.0, 13.0])
    m.bone('fin_left', 'body', [3.5, 8.0, 13.0])
    # fuselage: blended body, tapering nose, tail boom
    m.cube('body', [-5.0, 2.0, -12.0], [10.0, 6.0, 27.0], QM['hull'])
    m.cube('body', [-4.0, 8.0, -9.0], [8.0, 1.6, 22.0], QM['hull_hi'])
    m.cube('body', [-3.6, 2.6, -19.0], [7.2, 4.6, 7.0], QM['hull'])
    m.cube('body', [-2.4, 3.2, -24.0], [4.8, 3.2, 5.0], QM['hull_hi'])
    m.cube('body', [-1.2, 3.8, -26.5], [2.4, 1.8, 2.5], QM['hull'])
    m.cube('body', [-3.0, 7.2, -18.5], [6.0, 1.6, 7.0], QM['glass'], rotation=[-12, 0, 0], pivot=[0, 7.2, -11.5])
    m.cube('body', [-2.2, 6.0, -23.0], [4.4, 1.2, 4.6], QM['glass'], rotation=[-16, 0, 0], pivot=[0, 6.0, -18.5])
    # wings: swept main wings with intakes, smaller canards
    for sx in (-1, 1):
        x0 = 5.0 if sx > 0 else -20.0
        m.cube('body', [x0, 4.4, -2.0], [15.0, 1.2, 10.0], QM['hull'], rotation=[0, sx * -24, sx * -4], pivot=[sx * 5.0, 5.0, 0])
        m.cube('body', [x0 + (11.0 if sx > 0 else 0), 4.6, 4.0], [4.0, 1.0, 5.0], QM['hull_hi'],
               rotation=[0, sx * -24, sx * -4], pivot=[sx * 5.0, 5.0, 0])
        c0 = 3.6 if sx > 0 else -9.6
        m.cube('body', [c0, 4.6, -16.0], [6.0, 0.8, 3.6], QM['hull'], rotation=[0, sx * -30, 0], pivot=[sx * 3.6, 5.0, -15.0])
        tip = 19.0 if sx > 0 else -20.0
        m.cube('body', [tip, 4.4, 2.0], [1.0, 1.0, 1.0], QM['green'] if sx > 0 else QM['red'])
    # V-tail fins with the roundel
    m.cube('fin_right', [-4.0, 8.0, 9.0], [1.0, 8.0, 6.0], QM['emblem'], rotation=[-18, 0, -28], pivot=[-3.5, 8.0, 13.0])
    m.cube('fin_left', [3.0, 8.0, 9.0], [1.0, 8.0, 6.0], QM['emblem'], rotation=[-18, 0, 28], pivot=[3.5, 8.0, 13.0])
    # tilting engine pods (VTOL) and the rear ramp
    for side, sx in (('right', -1), ('left', 1)):
        m.cube(f'engine_{side}', [sx * 7.5 - 2.2, 3.8, -3.0], [4.4, 4.4, 8.0], QM['engine'])
        m.cube(f'engine_{side}', [sx * 7.5 - 1.6, 4.4, 5.0], [3.2, 3.2, 0.4], QM['engine'], faces=('south',))
    m.cube('ramp', [-3.6, 1.4, 6.0], [7.2, 0.6, 9.0], QM['ramp'])
    m.cube('body', [-3.8, 2.0, 5.8], [7.6, 0.3, 9.4], Mat('#1a1d22', noise=2, edge=0.0), faces=('down',))
    return m


# =================================================================================== OUTPUT
def main():
    rig = {False: body_model(False), True: body_model(True)}
    rig[False].identifier = 'avenger'
    write_json(os.path.join(RES, W('avenger.geo.json')), rig[False].geo_json())
    rig[True].identifier = 'avenger_slim'
    write_json(os.path.join(RES, W('avenger_slim.geo.json')), rig[True].geo_json())
    for key, mats, slim in (('iron_man', IM_MATS, False), ('thor', TH_MATS, False), ('black_widow', BW_MATS, True),
                            ('hawkeye', HK_MATS, False)):
        write_png(T(f'{key}.png'), paint_skin(rig[slim], mats, seed=hash(key) & 0xffff))
    for key, gear in (('iron_man', ironman_gear()), ('thor', thor_gear()), ('black_widow', widow_gear()),
                      ('hawkeye', hawkeye_gear())):
        gear.write(W(f'{key}_gear.geo.json'), T(f'{key}_gear.png'), T(f'{key}_gear_glow.png'))
    hulk_model().write(W('hulk.geo.json'), T('hulk.png'), T('hulk_glow.png'))
    for key, m in projectiles():
        m.write(W(f'{key}.geo.json'), T(f'{key}.png'), T(f'{key}_glow.png'))
    q = quinjet()
    q.write(W('quinjet.geo.json'), T('quinjet.png'), T('quinjet_glow.png'))
    print('quinjet atlas', q.width, q.height)


if __name__ == '__main__':
    main()
