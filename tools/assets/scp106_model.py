"""SCP-106 ("the Old Man") model and texture for viltrumitecore. Own work, CC0.

A hunched, very old man (about 2 blocks tall standing straight) after the SCP description: skin black-brown and
rotting, wet and glossy like tar, deep wrinkles and sagging folds, a bald skull with a heavy brow, sunken cheeks,
small pale eyes deep in the sockets and a wide lipless grin of yellow teeth on its own jaw bone; long gnarled
arms and hooked fingers dripping corrosion; the rags of a grey work uniform (torn trousers, a shredded shirt) with
holes and frayed hems (alpha cutout) over the skin. Shapes and painting are procedural, nothing is copied.

Bone names and the hierarchy follow the SCP-096 rig (root/pelvis/belly/chest/neck/head/jaw, arm/forearm/hand/
fingers/tips/thumb and thigh/shin/foot per side) so the IK helpers of the clip script work unchanged.
Writes geo/scp106/scp106.geo.json, textures/entity/scp106/scp106.png and the eyes pass scp106_glow.png (both as
.b64 for Gradle). Units are model pixels, front -Z, the character's right side -X. Run from the repo root:
    python3 tools/assets/scp106_model.py [--preview]
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from jinwoo_geo import Mat, Model, put  # noqa: E402

GEO_REL = 'geo/scp106/scp106.geo.json'
TEX_REL = 'textures/entity/scp106/scp106.png'
GLOW_REL = 'textures/entity/scp106/scp106_glow.png'

SKIN = '#2b2219'
SKIN_RGB = (43, 34, 25)
SHEEN = (96, 80, 58)
WET = (138, 118, 86)
DEEP = (10, 8, 6)
FOLD = (20, 15, 11)
ROT = (44, 32, 21)
OOZE = (16, 12, 8)
TEETH = (170, 150, 98)
TEETH_DARK = (96, 78, 44)
GUM = (52, 18, 14)
MOUTH = (8, 4, 3)
EYE = (214, 208, 178)
CLOTH = '#4b4c45'
CLOTH_DARK = (40, 39, 33)
CLOTH_LIGHT = (86, 84, 72)
STAIN = (30, 22, 14)


def _noise(shape, rs, cell):
    h, w = shape
    cells = rs.uniform(-1, 1, ((h + cell - 1) // cell + 2, (w + cell - 1) // cell + 2))
    yy, xx = np.mgrid[0:h, 0:w]
    fy, fx = yy / cell, xx / cell
    y0, x0 = fy.astype(int), fx.astype(int)
    ty, tx = fy - y0, fx - x0
    return (cells[y0, x0] * (1 - tx) * (1 - ty) + cells[y0, x0 + 1] * tx * (1 - ty)
            + cells[y0 + 1, x0] * (1 - tx) * ty + cells[y0 + 1, x0 + 1] * tx * ty)


def tar(face, rgb, rs, sheen=0.55, wrinkle=1.0, drips=0.0):
    """Rotting tar-like skin: blotches of rot, glossy sheen streaks (light from above), wrinkles, drips."""
    h, w = rgb.shape[:2]
    n = _noise((h, w), rs, 3)
    rgb += n[..., None] * np.array([7.0, 5.5, 4.0])
    rot = _noise((h, w), rs, 5) > 0.45
    put(rgb, rot, ROT, 0.3)
    # wet sheen: short vertical highlights, stronger on faces turned up and to the front
    k = {'up': 1.0, 'north': 0.75, 'east': 0.55, 'west': 0.55, 'south': 0.4, 'down': 0.05}[face.name] * sheen
    hi = (rs.uniform(0, 1, (h, w)) < 0.05 * k)
    hi[1:, :] |= hi[:-1, :] & (rs.uniform(0, 1, (h - 1, w)) < 0.8)
    put(rgb, hi, SHEEN, 0.7)
    put(rgb, hi & (rs.uniform(0, 1, (h, w)) < 0.35), WET, 0.8)
    if wrinkle and face.name in ('north', 'south', 'east', 'west') and h > 3:
        X, Y, Z = face.world()
        across = X if face.name in ('north', 'south') else Z
        lines = np.abs(np.sin(Y * 3.1 + 0.6 * np.sin(across * 1.7) + rs.uniform(0, 6))) < 0.12 * wrinkle
        put(rgb, lines, FOLD, 0.55)
    if drips and face.name in ('north', 'south', 'east', 'west'):
        for _ in range(int(drips * w)):
            x = rs.randint(0, w)
            top = rs.randint(0, max(1, h // 2))
            ln = rs.randint(2, max(3, h - top))
            put(rgb, (np.arange(h)[:, None] >= top) & (np.arange(h)[:, None] < top + ln) & (np.arange(w)[None, :] == x), OOZE, 0.9)
            if top + ln < h:
                rgb[min(h - 1, top + ln - 1), x] = np.array(WET, float) * 0.7
    if face.name == 'down':
        rgb *= 0.6


def skin(sheen=0.55, wrinkle=1.0, drips=0.0, extra=None):
    def fn(face, rgb, glow, rs):
        tar(face, rgb, rs, sheen, wrinkle, drips)
        if extra is not None:
            extra(face, rgb, glow, rs)
    return fn


def head_fn(face, rgb, glow, rs):
    X, Y, Z = face.world()
    n = face.name
    if n == 'north':
        # deep sockets under the brow, tiny pale eyes, sunken cheeks, nasolabial folds
        for s in (-1, 1):
            cx, cy = s * 0.95, 35.45
            d = ((X - cx) / 0.75) ** 2 + ((Y - cy) / 0.55) ** 2
            put(rgb, d < 1.0, DEEP, 0.92)
            put(rgb, (d >= 1.0) & (d < 1.6), FOLD, 0.5)
            eye = ((X - cx - s * 0.08) / 0.22) ** 2 + ((Y - cy + 0.05) / 0.17) ** 2 < 1.0
            put(rgb, eye, EYE, 0.9)
            glow[eye] = np.array([*EYE, 255], float)
            cheek = ((X - s * 1.35) / 0.55) ** 2 + ((Y - 34.1) / 0.8) ** 2 < 1.0
            put(rgb, cheek, DEEP, 0.4)
            fold = np.abs((X - s * 0.65) - s * 0.35 * (34.9 - Y)) < 0.1
            put(rgb, fold & (Y < 34.9) & (Y > 33.3), FOLD, 0.8)
        # forehead wrinkles
        for yk in (36.55, 36.95, 37.35):
            put(rgb, (np.abs(Y - yk - 0.08 * np.sin(X * 2.2)) < 0.07) & (np.abs(X) < 1.6), FOLD, 0.7)
    elif n == 'up':
        put(rgb, rs.uniform(0, 1, rgb.shape[:2]) < 0.06, WET, 0.6)
    elif n in ('east', 'west'):
        put(rgb, (np.abs(Y - 35.6) < 0.3) & (Z < -1.8), DEEP, 0.4)


def brow_fn(face, rgb, glow, rs):
    if face.name == 'down':
        rgb[:] = np.array(DEEP, float)
    elif face.name == 'north':
        X, Y, Z = face.world()
        put(rgb, np.abs(X) < 0.2, FOLD, 0.6)


def nose_fn(face, rgb, glow, rs):
    if face.name == 'down':
        X, Y, Z = face.world()
        rgb *= 0.5
        for s in (-1, 1):
            put(rgb, ((X - s * 0.22) / 0.14) ** 2 < 1, DEEP, 0.9)


def ear_fn(face, rgb, glow, rs):
    if face.name in ('east', 'west'):
        X, Y, Z = face.world()
        d = ((Y - Y.mean()) / 0.55) ** 2 + ((Z - Z.mean()) / 0.25) ** 2
        put(rgb, d < 1, DEEP, 0.7)


def teeth(rgb, U, Y, y0, y1, upper, pitch=0.6, rs=None):
    band = (Y >= y0) & (Y <= y1)
    rgb[band] = np.array(TEETH, float)
    gaps = band & (np.mod(U + 0.11, pitch) < 0.19)
    put(rgb, gaps, TEETH_DARK, 0.9)
    tip = band & ((Y < y0 + 0.12) if upper else (Y > y1 - 0.12))
    put(rgb, tip, TEETH_DARK, 0.4)
    root = band & ((Y > y1 - 0.1) if upper else (Y < y0 + 0.1))
    put(rgb, root, GUM, 0.8)
    if rs is not None:
        missing = band & (np.floor((U + 0.11) / pitch) % 5 == 3)
        put(rgb, missing, MOUTH, 0.95)


def maxilla_fn(face, rgb, glow, rs):
    X, Y, Z = face.world()
    n = face.name
    if n == 'north':
        teeth(rgb, X, Y, 32.45, 32.95, True, rs=rs)
    elif n in ('east', 'west'):
        teeth(rgb, Z, Y, 32.45, 32.9, True)
        put(rgb, Z > -1.7, SKIN_RGB, 1.0)
    elif n == 'down':
        rgb[:] = np.array(GUM, float)
        rim = (np.abs(X) > 1.35) | (Z < -2.2)
        rgb[rim] = np.array(TEETH, float)


def jaw_fn(face, rgb, glow, rs):
    X, Y, Z = face.world()
    n = face.name
    if n == 'north':
        teeth(rgb, X, Y, 31.95, 32.4, False, rs=rs)
        put(rgb, (np.abs(Y - 31.3) < 0.08) & (np.abs(X) < 0.9), FOLD, 0.8)
    elif n in ('east', 'west'):
        teeth(rgb, Z, Y, 32.0, 32.4, False)
        put(rgb, Z > -1.8, SKIN_RGB, 1.0)
    elif n == 'up':
        rgb[:] = np.array(GUM, float)
        rim = (np.abs(X) > 1.25) | (Z < -2.25)
        rgb[rim] = np.array(TEETH, float)
    elif n == 'down':
        rgb *= 0.55


def mouth_fn(face, rgb, glow, rs):
    rgb[:] = np.array(MOUTH, float)


def chest_fn(face, rgb, glow, rs):
    X, Y, Z = face.world()
    if face.name == 'north':
        for k in range(4):
            yk = 25.6 + k * 0.95
            put(rgb, (np.abs(Y - yk + 0.12 * X * X) < 0.13) & (np.abs(X) > 0.5), FOLD, 0.6)
        put(rgb, (np.abs(X) < 0.15) & (Y > 26.0), FOLD, 0.5)
        sag = (np.abs(X) > 0.6) & (np.abs(X) < 2.5) & (np.abs(Y - 27.4 - 0.25 * np.abs(X)) < 0.12)
        put(rgb, sag, DEEP, 0.6)


def belly_fn(face, rgb, glow, rs):
    X, Y, Z = face.world()
    if face.name == 'north':
        for yk in (21.6, 22.5, 23.4):
            put(rgb, (np.abs(Y - yk - 0.1 * np.cos(X * 1.4)) < 0.1), FOLD, 0.65)
        put(rgb, ((X / 0.25) ** 2 + ((Y - 22.9) / 0.3) ** 2) < 1, DEEP, 0.8)


def spine_fn(face, rgb, glow, rs):
    X, Y, Z = face.world()
    put(rgb, np.mod(Y, 0.8) < 0.3, SHEEN, 0.35)


def nail_fn(face, rgb, glow, rs):
    X, Y, Z = face.world()
    frac = np.clip((Y.max() - Y) / max(0.01, Y.max() - Y.min()), 0, 1)
    put(rgb, frac > 0.55, (70, 60, 40), 0.75)
    put(rgb, frac > 0.8, (120, 108, 80), 0.6)


# ---- the rags (alpha cutout) ------------------------------------------------------------------------------
def cloth_fn(face, rgb, glow, rs):
    h, w = rgb.shape[:2]
    n = _noise((h, w), rs, 4)
    rgb += n[..., None] * 6.0
    X, Y, Z = face.world()
    seams = np.abs(np.mod(Y, 3.3) - 0.1) < 0.08
    put(rgb, seams, CLOTH_DARK, 0.5)
    stain = _noise((h, w), rs, 6) > 0.35
    put(rgb, stain, STAIN, 0.35)
    put(rgb, Y < Y.min() + 1.2, STAIN, 0.3)
    put(rgb, rs.uniform(0, 1, (h, w)) < 0.05, CLOTH_LIGHT, 0.35)
    if face.name == 'down':
        rgb *= 0.6


def rag_alpha(hem_top=None, holes=0.06, fray=1.2, bottom=None):
    """Alpha of a ragged shell: torn holes and a frayed lower hem (world Y below `bottom` + noise is cut)."""
    def fn(face, rs):
        h, w = face.h, face.w
        a = np.full((h, w), 255, np.uint8)
        if face.name in ('up', 'down'):
            if face.name == 'down' and bottom is not None:
                a[:] = 0
            return a
        X, Y, Z = face.world()
        hole = _noise((h, w), rs, 2) > 1.0 - holes * 4
        a[hole] = 0
        if bottom is not None:
            edge = bottom + fray * (0.5 + 0.5 * _noise((h, w), rs, 1))
            a[Y < edge] = 0
        if hem_top is not None:
            edge = hem_top - fray * (0.5 + 0.5 * _noise((h, w), rs, 1))
            a[Y > edge] = 0
        return a
    return fn


def mats():
    def m(fn, base=SKIN, noise=3, edge=0.08):
        return Mat(base, noise=noise, edge=edge, top=1.06, bottom=0.7, fn=fn, grain=1)
    rags = dict(noise=2, edge=0.05, top=1.0, bottom=0.7, fn=lambda f, rgb, g, rs: cloth_fn(f, rgb, g, rs), grain=1)
    return {
        'skin': m(skin()),
        'drip': m(skin(drips=0.35)),
        'head': m(skin(0.7, 0.5, extra=head_fn)),
        'brow': m(skin(0.8, 0.0, extra=brow_fn)),
        'nose': m(skin(0.8, 0.0, extra=nose_fn)),
        'ear': m(skin(0.4, 0.0, extra=ear_fn)),
        'maxilla': m(skin(0.3, 0.0, extra=maxilla_fn)),
        'jaw': m(skin(0.5, 0.3, drips=0.25, extra=jaw_fn)),
        'mouth': Mat('#080403', noise=1, edge=0.0, fn=mouth_fn),
        'chest': m(skin(0.6, 0.6, extra=chest_fn)),
        'belly': m(skin(0.5, 0.4, extra=belly_fn)),
        'spine': m(skin(0.9, 0.0, extra=spine_fn), edge=0.15),
        'limb': m(skin(0.5, 1.2)),
        'joint': m(skin(0.8, 1.6), edge=0.15),
        'hand': m(skin(0.6, 1.4, drips=0.3)),
        'finger': m(skin(0.7, 1.0), edge=0.12),
        'nail': m(skin(0.9, 0.0, extra=nail_fn), edge=0.1),
        'foot': m(skin(0.4, 0.8)),
        'shirt': Mat(CLOTH, alpha_fn=rag_alpha(holes=0.16, bottom=21.0, fray=1.6), **rags),
        'sleeve': Mat(CLOTH, alpha_fn=rag_alpha(holes=0.14, bottom=24.4, fray=1.8), **rags),
        'trouser': Mat(CLOTH, alpha_fn=rag_alpha(holes=0.12, bottom=None), **rags),
        'trouser_hem': Mat(CLOTH, alpha_fn=rag_alpha(holes=0.14, bottom=5.6, fray=2.2), **rags),
    }


def build():
    M = mats()
    md = Model('viltrumitecore.scp106', density=4, width=256, seed=106)
    md.bone('root', None, [0, 0, 0])
    md.bone('pelvis', 'root', [0, 18.8, 0])
    md.cube('pelvis', [-2.7, 17.5, -1.4], [5.4, 3.0, 2.9], M['skin'])
    md.cube('pelvis', [-2.95, 17.3, -1.65], [5.9, 3.4, 3.4], M['trouser'])
    # torso: a sagging old belly, flat wrinkled chest, rounded upper back (the hunch is in the clips)
    md.bone('belly', 'pelvis', [0, 20.4, 0.2])
    md.cube('belly', [-2.4, 20.3, -1.35], [4.8, 4.4, 2.8], M['belly'])
    md.cube('belly', [-0.35, 20.5, 1.45], [0.7, 4.1, 0.4], M['spine'])
    md.cube('belly', [-2.65, 20.1, -1.6], [5.3, 4.8, 3.3], M['shirt'])
    md.bone('chest', 'belly', [0, 24.6, 0.4])
    md.cube('chest', [-3.0, 24.5, -1.5], [6.0, 4.9, 3.4], M['chest'])
    md.cube('chest', [-3.5, 28.9, -1.2], [7.0, 1.5, 3.0], M['skin'])
    md.cube('chest', [-0.35, 24.6, 1.9], [0.7, 4.8, 0.45], M['spine'])
    md.cube('chest', [-2.4, 26.6, 1.9], [4.8, 3.0, 0.6], M['skin'])
    md.cube('chest', [-3.25, 24.3, -1.75], [6.5, 5.4, 3.9], M['shirt'])
    md.bone('neck', 'chest', [0, 30.2, 0.1])
    md.cube('neck', [-0.9, 29.9, -0.9], [1.8, 2.6, 1.8], M['drip'])
    # head: bald, wrinkled, heavy brow over deep sockets, hooked nose, sagging ears, the upper teeth on the maxilla
    # and a separate lower jaw (the grin opens on the 'jaw' bone)
    md.bone('head', 'neck', [0, 32.3, -0.2])
    md.cube('head', [-2.1, 33.4, -2.4], [4.2, 4.6, 4.5], M['head'])
    md.cube('head', [-1.8, 38.0, -2.0], [3.6, 0.45, 3.7], M['head'])
    md.cube('head', [-1.8, 33.8, 2.1], [3.6, 3.6, 0.6], M['head'])
    md.cube('head', [-2.0, 36.05, -2.72], [4.0, 0.5, 0.4], M['brow'])
    md.cube('head', [-0.35, 33.95, -2.95], [0.7, 1.9, 0.6], M['nose'])
    md.cube('head', [-0.25, 33.7, -3.2], [0.5, 0.6, 0.35], M['nose'])
    md.cube('head', [-2.4, 33.6, -0.4], [0.35, 1.9, 0.9], M['ear'])
    md.cube('head', [2.05, 33.6, -0.4], [0.35, 1.9, 0.9], M['ear'])
    md.cube('head', [-1.75, 32.4, -2.55], [3.5, 1.15, 2.5], M['maxilla'])
    md.cube('head', [-1.5, 31.4, -2.2], [3.0, 2.0, 2.3], M['mouth'])
    md.cube('head', [-1.85, 32.0, -0.1], [3.7, 1.5, 2.6], M['head'])
    md.bone('jaw', 'head', [0, 33.0, 0.4])
    md.cube('jaw', [-1.7, 30.9, -2.6], [3.4, 1.55, 3.2], M['jaw'])
    md.cube('jaw', [-1.0, 30.5, -2.75], [2.0, 0.6, 1.3], M['jaw'])
    # right arm (mirrored): bony shoulder, long upper arm in a torn sleeve, knobbly elbow, long forearm, big hand,
    # four hooked two-joint fingers with dark nails, a thumb
    md.bone('arm_r', 'chest', [-3.6, 29.4, 0.3])
    md.cube('arm_r', [-4.5, 28.3, -0.75], [1.8, 1.9, 1.9], M['joint'])
    md.cube('arm_r', [-4.6, 20.6, -0.55], [1.45, 8.2, 1.5], M['limb'])
    md.cube('arm_r', [-4.85, 23.6, -0.8], [1.95, 5.6, 2.0], M['sleeve'])
    md.cube('arm_r', [-4.75, 19.9, -0.5], [1.7, 1.25, 1.5], M['joint'])
    md.bone('forearm_r', 'arm_r', [-3.9, 20.5, 0.25])
    md.cube('forearm_r', [-4.55, 12.6, -0.5], [1.35, 7.9, 1.45], M['drip'])
    md.cube('forearm_r', [-4.5, 11.9, -0.45], [1.25, 0.9, 1.35], M['limb'])
    md.bone('hand_r', 'forearm_r', [-3.9, 12.1, 0.25])
    md.cube('hand_r', [-4.4, 9.2, -0.85], [0.95, 3.0, 2.25], M['hand'])
    md.cube('hand_r', [-4.45, 8.9, -0.85], [1.05, 0.5, 2.25], M['joint'])
    md.bone('fingers_r', 'hand_r', [-3.9, 9.1, 0.25])
    md.bone('tips_r', 'fingers_r', [-3.9, 7.0, 0.25])
    for k, z in enumerate((-0.78, -0.24, 0.3, 0.84)):
        ln = (2.1, 2.3, 2.2, 1.8)[k]
        md.cube('fingers_r', [-4.2, 9.1 - ln, z], [0.48, ln, 0.45], M['finger'])
        tl = (1.7, 1.9, 1.8, 1.45)[k]
        md.cube('tips_r', [-4.17, 9.1 - ln - tl, z + 0.02], [0.43, tl, 0.41], M['nail'])
    md.bone('thumb_r', 'hand_r', [-3.85, 11.0, -0.65])
    md.cube('thumb_r', [-4.1, 8.7, -1.3], [0.48, 2.4, 0.48], M['nail'])
    # right leg: thin thigh in ragged trousers, knobbly knee, the trouser leg torn off below the shin, a long foot
    md.bone('thigh_r', 'pelvis', [-1.55, 18.5, 0])
    md.cube('thigh_r', [-2.75, 10.4, -1.15], [2.4, 8.4, 2.3], M['limb'])
    md.cube('thigh_r', [-3.0, 10.2, -1.4], [2.9, 8.5, 2.8], M['trouser'])
    md.bone('shin_r', 'thigh_r', [-1.55, 10.3, -0.15])
    md.cube('shin_r', [-2.6, 9.4, -1.25], [2.1, 1.8, 1.7], M['joint'])
    md.cube('shin_r', [-2.45, 1.2, -0.9], [1.8, 8.4, 1.8], M['limb'])
    md.cube('shin_r', [-2.75, 4.8, -1.2], [2.4, 6.0, 2.4], M['trouser_hem'])
    md.bone('foot_r', 'shin_r', [-1.55, 1.2, 0.05])
    md.cube('foot_r', [-2.55, 0.0, -3.1], [2.0, 1.25, 4.2], M['foot'])
    md.cube('foot_r', [-2.6, 0.0, -3.9], [2.1, 0.65, 0.8], M['joint'])
    md.cube('foot_r', [-2.45, 0.0, 1.0], [1.8, 1.0, 0.5], M['foot'])
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
    base, glow = md.write(GEO_REL, TEX_REL, GLOW_REL)
    write_indexed(TEX_REL, base)
    print('atlas', md.width, 'x', md.height, 'bones', len(md.bones), 'cubes', sum(len(b.cubes) for b in md.bones))
    if '--preview' in sys.argv:
        preview_rest(base, glow)
    return md


def write_indexed(rel, arr, colors=96):
    """Rewrites the texture as an indexed PNG (palette + transparency): the dark noisy skin compresses badly as
    RGBA, and the game reads paletted PNGs the same way (decoded to RGBA on load)."""
    import base64
    import io
    from PIL import Image
    from jinwoo_geo import BIN
    img = Image.fromarray(arr, 'RGBA').quantize(colors=colors, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    buf = io.BytesIO()
    img.save(buf, 'PNG', optimize=True)
    with open(os.path.join(BIN, rel + '.b64'), 'w') as fh:
        fh.write(base64.encodebytes(buf.getvalue()).decode('ascii'))
    return len(buf.getvalue())


def preview_rest(base, glow, pose=None, name='rest.png'):
    import json
    from PIL import Image
    import chitauri_lib as cl
    from jinwoo_geo import RES, ROOT
    with open(os.path.join(RES, GEO_REL)) as fh:
        geo = json.load(fh)
    out = os.path.join(ROOT, 'build', 'scp106_preview')
    os.makedirs(out, exist_ok=True)
    Image.fromarray(base, 'RGBA').save(os.path.join(out, 'tex.png'))
    g = glow.astype(float)
    g[..., :3] *= g[..., 3:4] / 255.0
    Image.fromarray(g.astype(np.uint8), 'RGBA').save(os.path.join(out, 'glow.png'))
    cl.preview(os.path.join(out, name), [(geo, pose or {}, os.path.join(out, 'tex.png'), os.path.join(out, 'glow.png'))],
               views=('front', 'side', 'back', 'q_front'), scale=10)


if __name__ == '__main__':
    main()
