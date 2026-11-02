"""SCP-096 ("Shy Guy") model and texture for viltrumitecore. Own work, CC0.

Tall (2.4 blocks), emaciated humanoid after the SCP: Containment Breach look: narrow shoulders and hips, visible
ribs and spine, a sunken belly, very long thin arms (fingertips at mid-shin when they hang straight), long fingers
with dried blood, a bald elongated head with black hollow eye sockets and a big lower jaw that drops wide open.
No SCP:CB file is used: the shapes and the painting are made here from the description (geometry and texture
are procedural, see paint functions below).

Writes geo/scp096/scp096.geo.json and textures/entity/scp096/scp096.png (as .b64 for Gradle). Units are model
pixels, front -Z, the character's right side -X, Bedrock degrees. Run from the repo root:
    python3 tools/assets/scp096_model.py
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from jinwoo_geo import Mat, Model, put, hexc  # noqa: E402

GEO_REL = 'geo/scp096/scp096.geo.json'
TEX_REL = 'textures/entity/scp096/scp096.png'

SKIN = '#d8d5cc'
SHADE = (150, 145, 136)
DEEP = (92, 87, 80)
LIGHT = (236, 234, 228)
BLOOD = (78, 12, 9)
BLOOD_WET = (120, 22, 16)
TEETH = (222, 214, 190)
GAP = (58, 40, 34)
GUM = (122, 64, 58)
MOUTH = (30, 8, 7)
MOUTH_RED = (70, 16, 13)
SOCKET = (8, 7, 7)


def _mottle(face, rgb, rs, amount=7.0, cell=3):
    """Uneven pale skin: soft grey-blue blotches."""
    h, w = rgb.shape[:2]
    cells = rs.uniform(-1, 1, ((h + cell - 1) // cell + 1, (w + cell - 1) // cell + 1))
    yy, xx = np.mgrid[0:h, 0:w]
    fy, fx = yy / cell, xx / cell
    y0, x0 = fy.astype(int), fx.astype(int)
    ty, tx = fy - y0, fx - x0
    n = (cells[y0, x0] * (1 - tx) * (1 - ty) + cells[y0, x0 + 1] * tx * (1 - ty)
         + cells[y0 + 1, x0] * (1 - tx) * ty + cells[y0 + 1, x0 + 1] * tx * ty)
    rgb += n[..., None] * np.array([amount, amount, amount * 1.25])


def _shade(rgb, mask, colour, k):
    put(rgb, mask, colour, k)


def _blood(face, rgb, rs, density, wet=0.35):
    """Dried blood blotches (dark brown-red, a few wetter spots)."""
    h, w = rgb.shape[:2]
    m = rs.uniform(0, 1, (h, w)) < density
    # grow the speckles into small blotches
    g = m.copy()
    g[1:, :] |= m[:-1, :] & (rs.uniform(0, 1, (h - 1, w)) < 0.7)
    g[:, 1:] |= m[:, :-1] & (rs.uniform(0, 1, (h, w - 1)) < 0.6)
    put(rgb, g, BLOOD, 0.85)
    put(rgb, g & (rs.uniform(0, 1, (h, w)) < wet), BLOOD_WET, 0.7)


def skin_fn(extra=None, mottle=7.0):
    def fn(face, rgb, glow, rs):
        _mottle(face, rgb, rs, mottle)
        if extra is not None:
            extra(face, rgb, rs)
    return fn


# ---- region painters (world coordinates of each face pixel) -------------------------------------------------
def ribs(face, rgb, rs):
    X, Y, Z = face.world()
    n = face.name
    if n == 'north':
        for k in range(5):
            yk = 25.5 + k * 0.82
            curve = yk - 0.16 * X * X
            line = np.abs(Y - curve) < 0.16
            _shade(rgb, line & (np.abs(X) > 0.35), SHADE, 0.75)
            _shade(rgb, (np.abs(Y - curve - 0.28) < 0.12) & (np.abs(X) > 0.35), LIGHT, 0.35)
        _shade(rgb, (np.abs(X) < 0.28) & (Y > 26.0), LIGHT, 0.45)          # sternum
        _shade(rgb, (np.abs(X) < 0.12) & (Y > 26.0), SHADE, 0.35)
        _shade(rgb, Y < 25.3, SHADE, 0.35)                                  # rib flare shadow over the belly
        _shade(rgb, (np.abs(X) > 2.35), SHADE, 0.3)
    elif n in ('east', 'west'):
        for k in range(6):
            yk = 25.3 + k * 0.8
            curve = yk + 0.35 * (Z + 1.5)
            _shade(rgb, np.abs(Y - curve) < 0.15, SHADE, 0.7)
        _shade(rgb, Y < 25.2, SHADE, 0.3)
    elif n == 'south':
        _shade(rgb, np.abs(X) < 0.5, SHADE, 0.25)
        for s in (-1, 1):
            blade = ((X * s > 0.7) & (X * s < 2.6) & (Y > 27.2) & (Y < 30.0))
            _shade(rgb, blade, LIGHT, 0.25)
    elif n == 'down':
        rgb *= 0.8


def clavicle(face, rgb, rs):
    X, Y, Z = face.world()
    if face.name == 'north':
        ridge = 30.25 + 0.22 * np.abs(X)
        _shade(rgb, (np.abs(Y - ridge) < 0.18) & (np.abs(X) > 0.35), LIGHT, 0.55)
        _shade(rgb, (np.abs(Y - ridge + 0.32) < 0.14) & (np.abs(X) > 0.35), SHADE, 0.6)
        _shade(rgb, (np.abs(X) < 0.35) & (Y < 30.6), SHADE, 0.55)            # jugular notch
    elif face.name == 'up':
        _shade(rgb, np.abs(X) > 2.6, LIGHT, 0.2)


def belly(face, rgb, rs):
    X, Y, Z = face.world()
    if face.name == 'north':
        sunk = np.clip(1.0 - np.abs(X) / 2.1, 0, 1) * np.clip((Y - 20.9) / 4.0, 0, 1)
        rgb[:] = rgb * (1 - 0.18 * sunk[..., None])
        _shade(rgb, np.abs(X) < 0.1, SHADE, 0.3)
        _shade(rgb, ((X / 0.28) ** 2 + ((Y - 22.3) / 0.3) ** 2) < 1, DEEP, 0.75)   # navel
        _shade(rgb, (np.abs(X) > 1.75) & (Y > 23.0), SHADE, 0.35)            # flank shadow under the ribs
    elif face.name in ('east', 'west'):
        _shade(rgb, Y > 24.4, SHADE, 0.3)


def pelvis_fn(face, rgb, rs):
    X, Y, Z = face.world()
    if face.name == 'north':
        for s in (-1, 1):
            crest = (np.abs(X - s * 2.0) < 0.3) & (Y > 19.8)
            _shade(rgb, crest, LIGHT, 0.45)
            _shade(rgb, np.abs(Y - (20.6 - 0.9 * (X * s - 1.4))) < 0.12, SHADE, 0.4)
        _shade(rgb, Y < 18.8, SHADE, 0.3)
    elif face.name == 'down':
        rgb *= 0.75


def spine_fn(face, rgb, rs):
    X, Y, Z = face.world()
    bumps = (np.mod(Y, 0.75) < 0.38)
    _shade(rgb, bumps, LIGHT, 0.45)
    _shade(rgb, ~bumps, SHADE, 0.3)


def neck_fn(face, rgb, rs):
    X, Y, Z = face.world()
    if face.name == 'north':
        _shade(rgb, (np.abs(np.abs(X) - 0.45) < 0.15), SHADE, 0.45)          # tendons
        _shade(rgb, (np.abs(X) < 0.25) & (Y > 32.2) & (Y < 32.9), LIGHT, 0.4)  # throat
    if face.name in ('east', 'west'):
        _shade(rgb, np.abs(Y - (31.4 + 0.8 * (Z + 0.6))) < 0.18, SHADE, 0.5)


def head_fn(face, rgb, rs):
    X, Y, Z = face.world()
    n = face.name
    if n == 'north':
        _shade(rgb, (Y > 35.6) & (Y < 37.4) & (np.abs(X) > 0.25) & (np.abs(X) < 1.85), SHADE, 0.22)
        for s in (-1, 1):
            cx, cy = s * 0.92, 36.55
            # droopy sockets: the outer corner sags
            yy = Y - cy + 0.18 * np.clip((X * s - 0.92) / 0.6, -1, 1)
            d = ((X - cx) / 0.6) ** 2 + (yy / 0.72) ** 2
            _shade(rgb, d < 2.2, SHADE, 0.45)
            _shade(rgb, d < 1.45, DEEP, 0.8)
            _shade(rgb, d < 1.0, SOCKET, 1.0)
            # tear streaks down the cheeks
            xs = cx + s * 0.1 + 0.05 * np.sin((Y - 34.0) * 3.0 + s)
            streak = (np.abs(X - xs) < 0.12) & (Y < cy - 0.55) & (Y > 34.7)
            _shade(rgb, streak, (128, 126, 123), 0.38)
            _shade(rgb, (np.abs(X - s * 1.45) < 0.4) & (np.abs(Y - 35.75) < 0.13), LIGHT, 0.3)
        _shade(rgb, (np.abs(X) < 0.2) & (Y > 35.6) & (Y < 37.2), LIGHT, 0.35)    # nose bridge
        _shade(rgb, Y < 34.75, SHADE, 0.25)
        _shade(rgb, Y > 38.2, LIGHT, 0.2)
        for yk in (38.05, 38.45):
            _shade(rgb, (np.abs(Y - yk) < 0.06) & (np.abs(X) < 1.2), SHADE, 0.18)
    elif n in ('east', 'west'):
        _shade(rgb, (Z < -1.6) & (Y < 35.3), SHADE, 0.3)
        _shade(rgb, (np.abs(Z + 1.9) < 0.25) & (Y > 35.6) & (Y < 37.4), SHADE, 0.2)   # temple hollow
    elif n == 'up':
        _shade(rgb, np.ones_like(X, bool), LIGHT, 0.2)
    elif n == 'down':
        rgb *= 0.7


def brow_fn(face, rgb, rs):
    if face.name == 'down':
        rgb *= 0.45
    elif face.name == 'north':
        X, Y, Z = face.world()
        _shade(rgb, np.ones_like(X, bool), LIGHT, 0.25)
        _shade(rgb, Y < 37.55, SHADE, 0.35)


def nose_fn(face, rgb, rs):
    X, Y, Z = face.world()
    if face.name == 'down':
        _shade(rgb, (np.abs(np.abs(X) - 0.18) < 0.12), (20, 12, 12), 0.95)
    elif face.name == 'north':
        _shade(rgb, Y < 35.45, SHADE, 0.4)


def ear_fn(face, rgb, rs):
    X, Y, Z = face.world()
    if face.name in ('east', 'west'):
        d = ((Y - Y.mean()) / 0.4) ** 2 + ((Z - Z.mean()) / 0.25) ** 2
        _shade(rgb, d < 1, SHADE, 0.45)


def teeth_band(rgb, X, Y, y0, y1, upper, pitch=0.75):
    """A row of narrow teeth (2 texels) with dark gaps and a dark red gum line at the root."""
    band = (Y >= y0) & (Y <= y1)
    rgb[band] = np.array(TEETH, float)
    gaps = band & (np.mod(X + 0.13, pitch) < 0.26)
    put(rgb, gaps, GAP, 0.95)
    tip = band & ((Y < y0 + 0.13) if upper else (Y > y1 - 0.13))
    put(rgb, tip & ~gaps, (200, 190, 158), 0.7)
    root = band & ((Y > y1 - 0.12) if upper else (Y < y0 + 0.12))
    put(rgb, root, GUM, 0.8)


def maxilla_fn(face, rgb, rs):
    X, Y, Z = face.world()
    n = face.name
    if n == 'north':
        teeth_band(rgb, X, Y, 33.5, 34.0, True)
        _shade(rgb, (Y > 34.0) & (Y < 34.18), (170, 142, 134), 0.6)          # thin pale lip
    elif n in ('east', 'west'):
        teeth_band(rgb, Z, Y, 33.5, 33.95, True)
        _shade(rgb, (Y >= 33.5) & (Y <= 33.95) & (Z > -1.3), MOUTH, 0.95)
    elif n == 'down':
        rgb[:] = np.array(MOUTH_RED, float)
        rim = (np.abs(X) > 1.45) | (Z < -2.35)
        rgb[rim] = np.array(TEETH, float)
        put(rgb, rim & (np.mod(X + Z + 0.13, 0.75) < 0.26), GAP, 0.9)
        put(rgb, (np.abs(X) < 1.1) & (Z > -1.9), MOUTH, 0.7)


def jaw_fn(face, rgb, rs):
    X, Y, Z = face.world()
    n = face.name
    if n == 'north':
        teeth_band(rgb, X, Y, 33.05, 33.5, False)
        _shade(rgb, (Y < 33.05) & (Y > 32.88), (164, 136, 128), 0.55)
        _shade(rgb, Y < 32.2, SHADE, 0.25)
    elif n in ('east', 'west'):
        teeth_band(rgb, Z, Y, 33.1, 33.5, False)
        _shade(rgb, (Y >= 33.1) & (Z > -1.2), MOUTH, 0.95)
        _shade(rgb, Y < 32.1, SHADE, 0.3)
        _shade(rgb, np.abs(Y - (31.75 + 0.3 * (Z + 2.75))) < 0.1, SHADE, 0.4)
    elif n == 'up':
        rgb[:] = np.array(MOUTH_RED, float)
        tongue = ((X / 1.05) ** 2 + ((Z + 0.9) / 1.4) ** 2) < 1
        put(rgb, tongue, (96, 30, 28), 0.9)
        rim = (np.abs(X) > 1.35) | (Z < -2.4)
        rgb[rim] = np.array(TEETH, float)
        put(rgb, rim & (np.mod(X + Z + 0.13, 0.75) < 0.26), GAP, 0.9)
    elif n == 'down':
        rgb *= 0.72


def chin_fn(face, rgb, rs):
    if face.name == 'down':
        rgb *= 0.72
    elif face.name == 'north':
        X, Y, Z = face.world()
        _shade(rgb, Y < 32.1, SHADE, 0.35)


def mouth_fn(face, rgb, rs):
    rgb[:] = np.array(MOUTH, float)
    X, Y, Z = face.world()
    if face.name == 'north':
        throat = ((X / 0.8) ** 2 + ((Y - 33.1) / 0.6) ** 2) < 1
        put(rgb, throat, (6, 2, 2), 1.0)
        put(rgb, (Y < 32.7) & ~throat, MOUTH_RED, 0.6)


def limb_fn(kind):
    def fn(face, rgb, rs):
        X, Y, Z = face.world()
        if kind == 'elbow':
            _shade(rgb, np.mod(Y * 3.1, 1.0) < 0.22, SHADE, 0.45)
            if face.name == 'south':
                _shade(rgb, np.ones_like(X, bool), DEEP, 0.15)
        elif kind == 'forearm':
            if face.name in ('south', 'north'):
                _shade(rgb, np.abs(np.sin(Y * 1.3 + X * 4.0)) < 0.06, (122, 132, 152), 0.35)   # veins
            _blood(face, rgb, rs, 0.006 + 0.03 * float(np.mean(Y < 14.0)), 0.3)
            _shade(rgb, Y < 12.9, SHADE, 0.25)
        elif kind == 'upper':
            _shade(rgb, Y > 28.8, LIGHT, 0.15)
        elif kind == 'knee':
            if face.name == 'north':
                d = ((Y - 10.5) / 0.75) ** 2 + ((X - X.mean()) / 0.8) ** 2
                _shade(rgb, d < 1, LIGHT, 0.4)
                _shade(rgb, (d > 1) & (d < 1.6), SHADE, 0.55)
        elif kind == 'shin':
            if face.name == 'north':
                _shade(rgb, np.abs(X - X.mean()) < 0.18, LIGHT, 0.35)
            _shade(rgb, Y < 2.4, SHADE, 0.3)
        elif kind == 'thigh':
            if face.name in ('east', 'west'):
                _shade(rgb, np.abs(Z) < 0.15, SHADE, 0.25)
    return fn


def hand_fn(part):
    def fn(face, rgb, rs):
        X, Y, Z = face.world()
        if part == 'palm':
            inner = face.name in ('east', 'west') and ((face.name == 'east') == (X.mean() < 0))
            if inner:
                put(rgb, np.ones_like(X, bool), (214, 190, 184), 0.35)
                _blood(face, rgb, rs, 0.09, 0.4)
                _shade(rgb, np.abs(Y - 10.3) < 0.12, SHADE, 0.5)
            else:
                _blood(face, rgb, rs, 0.035, 0.25)
                for k in range(4):
                    _shade(rgb, np.abs(Z - (-0.45 + 0.5 * k)) < 0.1, SHADE, 0.35)   # tendons
        elif part == 'knuckle':
            _shade(rgb, np.ones_like(X, bool), SHADE, 0.25)
            _blood(face, rgb, rs, 0.08, 0.3)
        elif part == 'finger':
            _shade(rgb, np.mod(Y, 1.0) < 0.15, SHADE, 0.5)
            _blood(face, rgb, rs, 0.12, 0.35)
        elif part == 'tip':
            frac = np.clip((6.8 - Y) / 1.9, 0, 1)
            put(rgb, frac > 0.35, BLOOD, 0.75)
            _blood(face, rgb, rs, 0.25, 0.45)
            put(rgb, frac > 0.82, (40, 8, 6), 0.85)                              # nails caked dark
        elif part == 'thumb':
            _blood(face, rgb, rs, 0.15, 0.35)
            put(rgb, Y < 9.0, BLOOD, 0.6)
    return fn


def foot_fn(part):
    def fn(face, rgb, rs):
        X, Y, Z = face.world()
        if face.name == 'down':
            rgb[:] = rgb * 0.55 + np.array([40, 36, 33]) * 0.45                 # dirty soles
        if part == 'toes' and face.name in ('north', 'up'):
            _shade(rgb, np.mod(X + 0.05, 0.52) < 0.1, DEEP, 0.7)
            if face.name == 'up':
                _shade(rgb, Z < -3.85, (120, 112, 104), 0.5)
        if part == 'foot' and face.name == 'up':
            for k in range(4):
                _shade(rgb, np.abs(X - (X.mean() - 0.6 + 0.4 * k)) < 0.07, SHADE, 0.45)
    return fn


def mats():
    def m(extra=None, base=SKIN, noise=3, edge=0.07, mottle=7.0):
        return Mat(base, noise=noise, edge=edge, top=1.04, bottom=0.8, fn=skin_fn(extra, mottle), grain=1)
    return {
        'skin': m(),
        'ribs': m(ribs),
        'clav': m(clavicle),
        'belly': m(belly, edge=0.03),
        'pelvis': m(pelvis_fn, edge=0.03),
        'spine': m(spine_fn, edge=0.12),
        'neck': m(neck_fn),
        'head': m(head_fn, mottle=6.0),
        'brow': m(brow_fn),
        'nose': m(nose_fn),
        'ear': m(ear_fn),
        'maxilla': m(maxilla_fn, mottle=3.0),
        'jaw': m(jaw_fn, mottle=4.0),
        'chin': m(chin_fn),
        'mouth': Mat('#1e0807', noise=2, edge=0.0, fn=lambda f, rgb, g, rs: mouth_fn(f, rgb, rs)),
        'upper': m(limb_fn('upper')),
        'elbow': m(limb_fn('elbow'), edge=0.12),
        'forearm': m(limb_fn('forearm')),
        'palm': m(hand_fn('palm')),
        'knuckle': m(hand_fn('knuckle'), edge=0.1),
        'finger': m(hand_fn('finger'), edge=0.1),
        'tip': m(hand_fn('tip'), edge=0.1),
        'thumb': m(hand_fn('thumb'), edge=0.1),
        'thigh': m(limb_fn('thigh')),
        'knee': m(limb_fn('knee'), edge=0.1),
        'shin': m(limb_fn('shin')),
        'foot': m(foot_fn('foot')),
        'toes': m(foot_fn('toes'), edge=0.12),
    }


def build():
    M = mats()
    md = Model('viltrumitecore.scp096', density=4, width=256, seed=96)
    md.bone('root', None, [0, 0, 0])
    md.bone('pelvis', 'root', [0, 19.6, 0])
    md.cube('pelvis', [-2.6, 18.4, -1.3], [5.2, 2.8, 2.7], M['pelvis'])
    # torso: sunken belly, ribcage wider than the belly, narrow shoulder girdle, bony spine and shoulder blades
    md.bone('belly', 'pelvis', [0, 21.0, 0.2])
    md.cube('belly', [-2.15, 20.9, -0.9], [4.3, 4.4, 2.3], M['belly'])
    md.cube('belly', [-0.35, 21.0, 1.35], [0.7, 4.2, 0.45], M['spine'])
    md.bone('chest', 'belly', [0, 25.0, 0.4])
    md.cube('chest', [-2.9, 24.8, -1.5], [5.8, 5.2, 3.3], M['ribs'])
    md.cube('chest', [-3.3, 29.8, -1.1], [6.6, 1.5, 2.7], M['clav'])
    md.cube('chest', [-0.3, 25.0, 1.75], [0.6, 5.0, 0.45], M['spine'])
    md.cube('chest', [-2.6, 27.3, 1.75], [1.9, 2.8, 0.4], M['skin'])
    md.cube('chest', [0.7, 27.3, 1.75], [1.9, 2.8, 0.4], M['skin'])
    md.bone('neck', 'chest', [0, 31.2, 0.2])
    md.cube('neck', [-0.75, 30.9, -0.6], [1.5, 3.2, 1.5], M['neck'])
    # head: elongated bald skull, brow ledge over the black sockets, tiny nose, the upper teeth on the maxilla,
    # a dark mouth cavity behind them and the big lower jaw on its own bone
    md.bone('head', 'neck', [0, 33.6, -0.3])
    md.cube('head', [-2.0, 34.5, -2.4], [4.0, 4.5, 4.5], M['head'])
    md.cube('head', [-1.75, 39.0, -2.0], [3.5, 0.45, 3.7], M['head'])
    md.cube('head', [-1.75, 35.0, 2.1], [3.5, 3.4, 0.6], M['head'])
    md.cube('head', [-1.9, 37.35, -2.62], [3.8, 0.4, 0.3], M['brow'])
    md.cube('head', [-0.3, 35.3, -2.65], [0.6, 1.2, 0.3], M['nose'])
    md.cube('head', [-2.25, 35.4, -0.2], [0.3, 1.3, 0.8], M['ear'])
    md.cube('head', [1.95, 35.4, -0.2], [0.3, 1.3, 0.8], M['ear'])
    md.cube('head', [-1.75, 33.5, -2.65], [3.5, 1.1, 2.6], M['maxilla'])
    md.cube('head', [-1.5, 32.3, -2.3], [3.0, 2.2, 2.4], M['mouth'])
    md.bone('jaw', 'head', [0, 34.2, 0.5])
    md.cube('jaw', [-1.7, 31.6, -2.75], [3.4, 1.9, 3.3], M['jaw'])
    md.cube('jaw', [-1.15, 31.2, -2.9], [2.3, 0.7, 1.2], M['chin'])
    # right arm (mirrored below): shoulder ball, long thin upper arm, bony elbow, forearm, palm, two-joint fingers
    md.bone('arm_r', 'chest', [-3.4, 30.4, 0.3])
    md.cube('arm_r', [-4.2, 29.4, -0.55], [1.6, 1.8, 1.7], M['skin'])
    md.cube('arm_r', [-4.4, 21.1, -0.4], [1.3, 8.6, 1.35], M['upper'])
    md.cube('arm_r', [-4.5, 20.4, -0.35], [1.5, 1.2, 1.35], M['elbow'])
    md.bone('forearm_r', 'arm_r', [-3.75, 21.0, 0.3])
    md.cube('forearm_r', [-4.35, 12.4, -0.35], [1.2, 8.4, 1.3], M['forearm'])
    md.cube('forearm_r', [-4.3, 11.7, -0.3], [1.1, 0.9, 1.2], M['forearm'])
    md.bone('hand_r', 'forearm_r', [-3.85, 11.9, 0.3])
    md.cube('hand_r', [-4.3, 9.0, -0.75], [0.8, 2.9, 2.05], M['palm'])
    md.cube('hand_r', [-4.35, 8.75, -0.75], [0.9, 0.5, 2.05], M['knuckle'])
    md.bone('fingers_r', 'hand_r', [-3.85, 9.0, 0.3])
    md.bone('tips_r', 'fingers_r', [-3.85, 6.85, 0.3])
    for k, z in enumerate((-0.68, -0.17, 0.34, 0.85)):
        ln = (2.15, 2.3, 2.25, 1.9)[k]
        md.cube('fingers_r', [-4.15, 9.0 - ln, z], [0.45, ln, 0.42], M['finger'])
        tl = (1.85, 2.0, 1.95, 1.6)[k]
        top = 9.0 - ln
        md.cube('tips_r', [-4.12, top - tl, z + 0.02], [0.4, tl, 0.38], M['tip'])
    md.bone('thumb_r', 'hand_r', [-3.8, 10.9, -0.55])
    md.cube('thumb_r', [-4.05, 8.5, -1.2], [0.45, 2.5, 0.45], M['thumb'])
    # right leg: thin thigh in two widths, knobbly knee, shin with a slight calf, long foot
    md.bone('thigh_r', 'pelvis', [-1.55, 19.2, 0])
    md.cube('thigh_r', [-2.73, 15.0, -1.15], [2.35, 4.6, 2.3], M['thigh'])
    md.cube('thigh_r', [-2.58, 10.6, -1.0], [2.05, 4.6, 2.0], M['thigh'])
    md.bone('shin_r', 'thigh_r', [-1.55, 10.4, -0.2])
    md.cube('shin_r', [-2.55, 9.6, -1.25], [2.0, 1.8, 1.6], M['knee'])
    md.cube('shin_r', [-2.4, 1.3, -0.85], [1.7, 8.6, 1.75], M['shin'])
    md.cube('shin_r', [-2.3, 5.2, 0.6], [1.5, 3.6, 0.6], M['shin'])
    md.bone('foot_r', 'shin_r', [-1.55, 1.3, 0.1])
    md.cube('foot_r', [-2.55, 0.0, -3.2], [2.0, 1.3, 4.3], M['foot'])
    md.cube('foot_r', [-2.6, 0.0, -4.1], [2.1, 0.7, 0.9], M['toes'])
    md.cube('foot_r', [-2.45, 0.0, 1.0], [1.8, 1.0, 0.55], M['foot'])
    md.mirror_x('_r', '_l')
    return md


ARM_BONES = ('arm', 'forearm', 'hand', 'fingers', 'tips', 'thumb')
LEG_BONES = ('thigh', 'shin', 'foot')
SPINE = ('root', 'pelvis', 'belly', 'chest', 'neck', 'head', 'jaw')


def all_bones():
    out = list(SPINE)
    for side in ('r', 'l'):
        out += ['%s_%s' % (b, side) for b in ARM_BONES + LEG_BONES]
    return out


def main():
    md = build()
    base, glow = md.write(GEO_REL, TEX_REL)
    print('atlas', md.width, 'x', md.height, 'bones', len(md.bones), 'cubes', sum(len(b.cubes) for b in md.bones))
    return md


if __name__ == '__main__':
    main()
