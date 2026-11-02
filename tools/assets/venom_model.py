"""Venom (the living symbiote) — body, first-person arms, mark / eye / pulse layers, the vanilla skin, the coat frames
of the staged transformation and the tendril strip. Own work, CC0.

A new model on the shaded pixel-art pipeline (tools/assets/pixel_shade.py: baked occlusion, form light, bevelled
edges, hue-shifted tone steps, ordered dither) in glossy blue-black: highlights lean cold blue, shadows sink into a
deep navy, every muscle catches a specular streak so the black reads in any light.

Silhouette: a 34 px hunched brute: huge deltoids with caps, trapezius rising to the skull, massive pectorals, a narrow
waist with a six-pack, flared lats, gorilla-long arms ending in four clawed fingers and a clawed thumb, heavy thighs,
calves and three-toed clawed feet. The torso leans forward, the head is carried low and forward.

Head (6 px per unit): a long smooth skull; the huge maw splits it ear to ear: an upper jaw with a palate, rows of
cut-out fangs (front and both sides, long canines), a hinged lower jaw (`jaw`, hinge behind the mouth) with its own
rows and fangs, a dark red mouth and throat, and a six-bone tongue (tongue_0..5) that lolls out over the chin.
The white eyes and the spider emblem are NOT in the base atlas: they are venom_marks.png (a translucent pass: they
form at the transformation's FACE stage); venom_eyes.png is their faint glow; venom_pulse.png holds the vein strands
of the living surface (RenderType.eyes, brightness = pulse).

Outputs geo/venom/{venom,venom_arms}.geo.json and textures/entity/venom/{venom,venom_marks,venom_eyes,venom_pulse,
venom_tendril,venom_skin,venom_skin_pulse}.png + coat/coat_0..16.png. Run from the repository root with
PYTHONHASHSEED=0; check with tools/assets/geo_lint.py venom venom_arms.
"""
import copy
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from avengers_common import Mat, near, side_x, sname  # noqa: E402
from clean_geo import write_json, write_png, RES  # noqa: E402
from red_hulk_model import RHModel, walk_lines, striations, crease_x, split_b64  # noqa: E402

D = 3            # body texel density (px per model unit)
DF = 6           # head density
LIGHT = (33.0, 0.0, 0.06)

# ---- palette ------------------------------------------------------------------------------------------
SYM, SYM_HI, SYM_LO, SYM_DK = '#15161f', '#2e3754', '#0b0b12', '#050508'
SHEEN = '#4d5f8c'            # specular streak colour (cold blue)
VEIN = '#1d2236'             # vein strands in the base (a faint ridge)
PULSE = '#c9b8ff'            # vein strands in the pulse layer (tinted violet in game)
MOUTH, GUM, PALATE = '#2a0710', '#6b1626', '#4a0f1b'
TOOTH, TOOTH_LO = '#efe9da', '#b9b09a'
TONGUE, TONGUE_HI, TONGUE_LO = '#b8324c', '#e2607a', '#7a1a30'
CLAW, CLAW_HI = '#262833', '#9aa3bd'
WHITE, WHITE_LO = '#f4f6fb', '#c7ccd9'
EYE_GLOW = '#9fb4ff'
SHADOW = (0.78, 0.84, 1.28)  # shadows sink to navy
COLDLT = (0.94, 1.0, 1.16)   # lights lean cold blue-white

MARK_EYE_A, MARK_A, PULSE_A = 255, 253, 252    # glow tags: eye marks (marks + glow), emblem marks, pulse strands


class VenomModel(RHModel):
    pass


# ============================================================================================ painter helpers
def is_outer(ctx):
    return ctx.name in ('east', 'west') and ((ctx.name == 'east') == (float(np.mean(ctx.x)) > 0))


def pulse(ctx, mask):
    m = np.asarray(mask, bool)
    ctx.put(m, VEIN)
    ctx.light(m, 0.06)
    ctx.emit(m, PULSE, PULSE_A)


def veins(ctx, n, vertical=True, branch=0.35, length=None):
    pulse(ctx, walk_lines(ctx, n, vertical=vertical, branch=branch, wander=0.6, length=length))


def sheen(ctx, mask, amt=0.22):
    """A specular streak: the glossy symbiote catches a cold highlight along each form."""
    m = np.asarray(mask, bool)
    ctx.put(m, SHEEN, 0.35)
    ctx.light(m, amt)


def streak_v(ctx, at=0.33, width=0.12, amt=0.2):
    sheen(ctx, np.abs(ctx.u - at) < width, amt)


def streak_h(ctx, at=0.3, width=0.12, amt=0.2):
    sheen(ctx, np.abs(ctx.v - at) < width, amt)


# ---- the spider emblem, defined around the torso: (theta deg from the front, y px) --------------------------
LEGS = (
    ((4, 22.9), (24, 25.5), (58, 26.9), (100, 27.0), (140, 25.2), (178, 22.9)),
    ((6, 21.7), (34, 23.4), (80, 23.9), (126, 23.2), (176, 21.7)),
    ((6, 20.1), (34, 18.6), (80, 18.0), (126, 18.6), (176, 20.1)),
    ((4, 18.9), (24, 16.3), (58, 15.0), (100, 15.0), (140, 16.4), (178, 18.9)),
)


def _seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = np.clip(((px - ax) * dx + (py - ay) * dy) / max(1e-6, dx * dx + dy * dy), 0, 1)
    return np.hypot(px - (ax + t * dx), py - (ay + t * dy)), t


def emblem_mask(x, y, z):
    """The white spider: a body on the sternum and one on the back, four legs a side wrapping round the torso."""
    theta = np.degrees(np.arctan2(np.abs(x), -(z - 0.4)))     # 0 front .. 180 back, mirrored
    # scale degrees to px along the surface (the torso is ~40 px round): 1 deg ~ 0.11 px
    k = 0.11
    m = np.zeros(np.shape(x), bool)
    for front in (True, False):
        th = theta if front else 180.0 - theta
        tx = th * k
        # cephalothorax, abdomen, head (ellipses in (tx, y))
        for cy, ry, rx in ((21.3, 2.6, 1.7), (17.9, 1.45, 1.05), (24.1, 1.0, 0.9)):
            m |= (tx / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0
    for leg in LEGS:
        pts = [(a * k, b) for a, b in leg]
        for i in range(len(pts) - 1):
            (ax, ay), (bx, by) = pts[i], pts[i + 1]
            d, t = _seg_dist(theta * k, y, ax, ay, bx, by)
            frac = (i + t) / (len(pts) - 1)
            width = 0.62 - 0.36 * np.sin(np.pi * frac)       # thick at both bodies, thin in the middle
            m |= d <= width
    return m


def emblem(ctx):
    m = emblem_mask(ctx.x, ctx.y, ctx.z)
    if np.any(m):
        ctx.emit(m, WHITE, MARK_A)


# ---- the eyes: big, slanted, sweeping round the skull's corners ---------------------------------------------
EYE = ((0.42, 29.75), (0.5, 30.6), (1.5, 31.55), (2.9, 32.3), (4.4, 32.75), (5.5, 32.55), (5.0, 31.95), (4.0, 31.05),
       (3.0, 30.25), (1.9, 29.7), (1.0, 29.5))
SKULL_HALF, SKULL_FRONT = 3.5, -7.6


def eye_mask(x, y, z):
    from clean_geo import poly_mask
    ax = np.abs(x)
    a = np.where(ax < SKULL_HALF - 0.01, ax, SKULL_HALF + np.maximum(0.0, z - SKULL_FRONT))
    return poly_mask(a, y, EYE)


def eyes(ctx):
    m = eye_mask(ctx.x, ctx.y, ctx.z)
    if np.any(m):
        ctx.put(m, SYM_DK)
        ctx.emit(m, WHITE, MARK_EYE_A)


# ============================================================================================ painters
def plain_fn(ctx):
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.05)
    elif ctx.side:
        streak_v(ctx, 0.3, 0.1, 0.12)


def torso_fn(ctx):
    n = ctx.name
    if n == 'north':
        crease_x(ctx, 0.0, -0.14)
        streak_v(ctx, 0.28, 0.07, 0.16)
        streak_v(ctx, 0.72, 0.07, 0.12)
    elif n == 'south':
        crease_x(ctx, 0.0, -0.16)                       # spine groove
        striations(ctx, 4, 0.02, vertical=False)
        veins(ctx, 3, branch=0.5)
    elif ctx.side:
        streak_v(ctx, 0.4, 0.1, 0.15)
        veins(ctx, 1)
    emblem(ctx)


def ab_fn(ctx):
    if ctx.name == 'north':
        ctx.light((slice(0, 1), slice(None)), 0.14)
        ctx.put((slice(0, 1), slice(None)), SHEEN, 0.3)
        ctx.light((slice(-1, None), slice(None)), -0.1)
    emblem(ctx)


def pec_fn(ctx):
    if ctx.name == 'north':
        ax = np.abs(ctx.x)
        sheen(ctx, near(ctx.y, 23.6 - 0.12 * ax, 0.35) & (ax > 0.8), 0.24)     # the upper chest catches the light
        ctx.light(near(ctx.y, 20.3 + 0.2 * ax, 0.22), -0.1)
        striations(ctx, 4, 0.02, vertical=False)
        veins(ctx, 1, vertical=False, branch=0.4)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.14)
    emblem(ctx)


def lat_fn(ctx):
    if ctx.side:
        striations(ctx, 4, 0.025)
        streak_v(ctx, 0.5, 0.08, 0.12)
    if ctx.name == 'south':
        veins(ctx, 1, branch=0.2)
    emblem(ctx)


def trap_fn(ctx):
    if ctx.name == 'up':
        sheen(ctx, np.abs(ctx.v - 0.4) < 0.15, 0.2)
        striations(ctx, 3, 0.03)
    emblem(ctx)


def neck_fn(ctx):
    if ctx.name == 'north':
        for s in (-1, 1):                               # the neck cords
            ctx.light(near(ctx.x, s * (1.5 - 0.3 * (ctx.y - 25.4)), 0.25), 0.08)
            ctx.light(near(ctx.x, s * (1.05 - 0.3 * (ctx.y - 25.4)), 0.18), -0.1)
    if ctx.side:
        veins(ctx, 1, branch=0.3)


def delt_fn(ctx):
    if ctx.side:
        striations(ctx, 3, 0.03)
        streak_v(ctx, 0.35, 0.1, 0.22)
        if is_outer(ctx):
            veins(ctx, 2, branch=0.4, length=ctx.h // 2 + 3)
    elif ctx.name == 'up':
        sheen(ctx, ctx.all(), 0.12)


def arm_fn(ctx):
    if ctx.side:
        striations(ctx, 4, 0.02)
        streak_v(ctx, 0.3, 0.1, 0.16)
        if ctx.name in ('north', 'south') or is_outer(ctx):
            veins(ctx, 1, branch=0.5)


def bicep_fn(ctx):
    if ctx.name == 'north':
        sheen(ctx, (np.abs(ctx.u - 0.45) < 0.16) & (np.abs(ctx.v - 0.4) < 0.3), 0.26)
        veins(ctx, 2, branch=0.45)
    elif ctx.side:
        ctx.light(ctx.all(), -0.02)


def forearm_fn(ctx):
    if ctx.side:
        striations(ctx, 3, 0.025)
        streak_v(ctx, 0.3, 0.1, 0.2)
        veins(ctx, 2, branch=0.5)


def hand_fn(ctx):
    if ctx.name == 'up':
        ctx.light(ctx.all(), 0.04)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.1)
    elif ctx.side:
        streak_h(ctx, 0.25, 0.12, 0.14)


def finger_fn(ctx):
    if ctx.side:
        ctx.light((slice(None), slice(0, 1)), 0.08)


def claw_alpha(face, rs):
    """Claws taper to a point toward the cube's bottom (down the fingers)."""
    h, w = face.h, face.w
    a = np.full((h, w), 255, np.uint8)
    if face.name in ('up', 'down'):
        return a
    for r in range(h):
        keep = max(1, int(round(w * (1.0 - r / max(1, h)) ** 0.8)))
        lo = (w - keep) // 2
        a[r, :lo] = 0
        a[r, lo + keep:] = 0
    return a


def claw_fn(ctx):
    if ctx.side:
        ctx.put(ctx.v > 0.55, CLAW_HI, 0.35)
        ctx.light((slice(None), slice(0, 1)), 0.18)


def leg_fn(ctx):
    if ctx.side:
        striations(ctx, 4, 0.02)
        streak_v(ctx, 0.32, 0.1, 0.16)
        veins(ctx, 1 if ctx.name in ('east', 'west') else 2, branch=0.45)


def quad_fn(ctx):
    if ctx.name == 'north':
        for s in (-1, 1):
            ctx.light(near(ctx.u, 0.5 + s * 0.22, 0.05), -0.1)
        sheen(ctx, (np.abs(ctx.u - 0.35) < 0.1) & (ctx.v < 0.6), 0.2)


def foot_fn(ctx):
    if ctx.name == 'up':
        streak_v(ctx, 0.4, 0.15, 0.14)
        veins(ctx, 1)


def toe_fn(ctx):
    if ctx.name == 'up':
        ctx.light(ctx.all(), 0.06)


# ---- head ---------------------------------------------------------------------------------------------
def cranium_fn(ctx):
    n = ctx.name
    if n == 'up':
        sheen(ctx, np.abs(ctx.u - 0.45) < 0.12, 0.24)          # a long highlight over the skull
        veins(ctx, 2, branch=0.5)
    elif n == 'south':
        veins(ctx, 1, branch=0.5)
    elif ctx.side:
        sheen(ctx, ctx.v < 0.18, 0.12)
    eyes(ctx)


def brow_fn(ctx):
    if ctx.name in ('north', 'up'):
        sheen(ctx, ctx.all(), 0.1)
    eyes(ctx)


def maxilla_fn(ctx):
    n = ctx.name
    if n == 'down':                                   # the palate: ridged dark red
        ctx.put(ctx.all(), PALATE)
        ctx.light(near(np.mod(ctx.z, 0.8), 0.0, 0.12), -0.12)
        ctx.flat[:] = False
    elif n == 'north':
        ctx.light(ctx.v > 0.7, -0.06)
        sheen(ctx, ctx.v < 0.2, 0.14)
    elif ctx.side:
        sheen(ctx, ctx.v < 0.25, 0.1)
    eyes(ctx)


def mouth_fn(ctx):
    ctx.put(ctx.all(), MOUTH)
    ctx.L += -0.08 * ctx.v


def gum_fn(ctx):
    if ctx.name == 'up':                              # the mouth floor
        ctx.put(ctx.all(), GUM)
        ctx.light(near(ctx.x, 0.0, 0.5), -0.12)
    elif ctx.name == 'north':
        sheen(ctx, ctx.v > 0.7, 0.12)
    elif ctx.name == 'down':
        veins(ctx, 1, vertical=False)


def tooth_alpha(up):
    """A row of cut-out fangs: points down (upper row) or up (lower row), one tooth every 5 px."""
    def fn(face, rs):
        h, w = face.h, face.w
        a = np.zeros((h, w), np.uint8)
        if face.name in ('up', 'down'):
            a[:] = 255
            return a
        period = 5 if w > 6 else max(3, w | 1)
        for col in range(w):
            p = (col % period) / max(1, period - 1)          # 0..1 across one tooth
            tip = 1.0 - abs(p - 0.5) * 2.0                    # 1 at the tooth's middle
            length = int(round(h * (0.12 + 0.88 * tip ** 1.3) * (0.8 + 0.2 * rs.uniform())))
            if up:
                a[h - length:, col] = 255
            else:
                a[:length, col] = 255
        return a
    return fn


def fang_alpha(up):
    def fn(face, rs):
        h, w = face.h, face.w
        a = np.zeros((h, w), np.uint8)
        if face.name in ('up', 'down'):
            a[:] = 255
            return a
        for r in range(h):
            f = r / max(1, h - 1)
            f = f if not up else 1 - f                         # 0 at the root .. 1 at the tip
            keep = max(1, int(round(w * (1 - f) ** 0.9)))
            lo = (w - keep) // 2
            a[r, lo:lo + keep] = 255
        return a
    return fn


def tooth_fn(ctx):
    ctx.light(ctx.v < 0.3, 0.06)
    ctx.put(ctx.v > 0.75, TOOTH_LO, 0.4)


def tongue_fn(ctx):
    if ctx.name == 'up':
        ctx.light(near(ctx.x, 0.0, 0.18), -0.12)               # the groove down the middle
        sheen(ctx, near(np.abs(ctx.x), 0.45, 0.15), 0.08)
        ctx.put(near(np.abs(ctx.x), 0.45, 0.15), TONGUE_HI, 0.4)
    elif ctx.name == 'down':
        ctx.put(ctx.all(), TONGUE_LO, 0.6)


def nape_fn(ctx):
    if ctx.side:
        veins(ctx, 1, branch=0.4)
        streak_v(ctx, 0.5, 0.12, 0.1)


# ============================================================================================ materials
def sym(fn=None, ao=0.55, cyl=None, **kw):
    mat = Mat(SYM, 'paint', fn or plain_fn, ao=ao, cool=SHADOW, warm=COLDLT, hue=1.3, quant=0.05, cyl=cyl, spec=0.45, **kw)
    mat.keep_glow = False      # the pulse / marks tags must not freeze the black's shading
    return mat


M = {
    'pelvis': sym(torso_fn, cyl=0.12), 'abdomen': sym(torso_fn, cyl=0.12), 'ab': sym(ab_fn, ao=0.7), 'oblique': sym(lat_fn),
    'rib': sym(torso_fn, cyl=0.1), 'chest': sym(torso_fn, cyl=0.1), 'pec': sym(pec_fn, ao=0.6), 'lat': sym(lat_fn),
    'trap': sym(trap_fn), 'neck': sym(neck_fn, cyl=0.2), 'back': sym(torso_fn),
    'delt': sym(delt_fn, cyl=0.24), 'delt_top': sym(delt_fn), 'arm': sym(arm_fn, cyl=0.22), 'bicep': sym(bicep_fn, cyl=0.2),
    'tricep': sym(arm_fn, cyl=0.2), 'elbow': sym(arm_fn), 'forearm': sym(forearm_fn, cyl=0.24), 'wrist': sym(forearm_fn, cyl=0.2),
    'hand': sym(hand_fn, cyl=0.12), 'knuckle': sym(hand_fn, ao=0.4), 'finger': sym(finger_fn, ao=0.45),
    'claw': Mat(CLAW, 'metal', claw_fn, ao=0.2, edges=False, micro=0.0, alpha_fn=claw_alpha),
    'thigh': sym(leg_fn, cyl=0.2), 'quad': sym(quad_fn, ao=0.5), 'calf': sym(leg_fn, cyl=0.22), 'knee': sym(plain_fn),
    'foot': sym(foot_fn), 'toe': sym(toe_fn, ao=0.45), 'glute': sym(torso_fn),
    # head
    'cranium': sym(cranium_fn, ao=0.5, cyl=0.12), 'brow': sym(brow_fn, ao=0.35), 'nape': sym(nape_fn, ao=0.45),
    'maxilla': sym(maxilla_fn, ao=0.45), 'jaw': sym(gum_fn, ao=0.45), 'chin': sym(gum_fn, ao=0.35), 'throat': sym(plain_fn, ao=0.4),
    'mouth': Mat(MOUTH, 'flat', mouth_fn, ao=0.0),
    'teeth_up': Mat(TOOTH, 'paint', tooth_fn, ao=0.15, edges=False, micro=0.0, alpha_fn=tooth_alpha(False)),
    'teeth_lo': Mat(TOOTH, 'paint', tooth_fn, ao=0.15, edges=False, micro=0.0, alpha_fn=tooth_alpha(True)),
    'fang_up': Mat(TOOTH, 'paint', tooth_fn, ao=0.1, edges=False, micro=0.0, alpha_fn=fang_alpha(False)),
    'fang_lo': Mat(TOOTH, 'paint', tooth_fn, ao=0.1, edges=False, micro=0.0, alpha_fn=fang_alpha(True)),
    'tongue': Mat(TONGUE, 'skin', tongue_fn, ao=0.25, cool=(0.8, 0.7, 0.9), quant=0.05, spec=0.35),
    'tongue_tip': Mat(TONGUE, 'skin', tongue_fn, ao=0.2, cool=(0.8, 0.7, 0.9), quant=0.05, spec=0.35, alpha_fn=fang_alpha(False)),
}


# ============================================================================================ skeleton
ARM_X, ARM_Y = 8.0, 24.2          # shoulder pivot
ELBOW = (9.4, 16.4)
WRIST = (9.6, 10.0)
FINGER_Y = 7.5
LEG_X = 3.0
TORSO_LEAN, HEAD_UP = 10, -12
ARM_OUT = 7
TONGUE = ((0, 26.65, -3.2), (0, 26.65, -5.0), (0, 26.65, -6.7), (0, 26.65, -8.3), (0, 26.65, -9.8), (0, 26.65, -11.2))
TONGUE_REST = (0, -14, 24, 28, 22, 14)
JAW_OPEN = 22


def skeleton(m):
    m.bone('root', None, [0, 0, 0])
    m.bone('waist', 'root', [0, 12.5, 0])
    m.bone('torso', 'waist', [0, 14.6, 0.3], [TORSO_LEAN, 0, 0])
    m.bone('chest', 'torso', [0, 21.0, 0])
    m.bone('head', 'torso', [0, 26.6, -1.6], [HEAD_UP, 0, 0])
    m.bone('jaw', 'head', [0, 27.6, -1.8], [JAW_OPEN, 0, 0])
    parent = 'jaw'
    for i, p in enumerate(TONGUE):
        m.bone(f'tongue_{i}', parent, list(p), [TONGUE_REST[i], 0, 0])
        parent = f'tongue_{i}'
    for s in (-1, 1):
        side = sname(s)
        m.bone(f'{side}_arm', 'torso', [s * ARM_X, ARM_Y, 0], [0, 0, s * ARM_OUT])
        m.bone(f'{side}_forearm', f'{side}_arm', [s * ELBOW[0], ELBOW[1], -0.2])
        m.bone(f'{side}_hand', f'{side}_forearm', [s * WRIST[0], WRIST[1], -0.3])
        m.bone(f'{side}_fingers', f'{side}_hand', [s * 9.6, FINGER_Y, -0.5])
        m.bone(f'{side}_thumb', f'{side}_hand', [s * 8.0, 8.8, -2.0])
    for s in (-1, 1):
        side = sname(s)
        m.bone(f'{side}_leg', 'root', [s * LEG_X, 12.5, 0])
        m.bone(f'{side}_shin', f'{side}_leg', [s * 3.2, 6.6, -0.2])
        m.bone(f'{side}_foot', f'{side}_shin', [s * 3.2, 1.6, -0.6])


def body_model():
    m = VenomModel('venom', density=D, width=256, seed=1988, light=LIGHT)
    skeleton(m)

    def c(bone, origin, size, mat, d=None, **kw):
        cube = m.cube(bone, origin, size, M[mat], **kw)
        cube.density = d
        return cube

    SIDES = ('north', 'south', 'east', 'west')
    # ---- pelvis
    c('waist', [-4.6, 11.2, -2.8], [9.2, 3.6, 5.6], 'pelvis')
    c('waist', [-4.2, 10.4, 1.9], [8.4, 3.2, 1.3], 'glute', faces=('south', 'east', 'west', 'down'))
    # ---- torso: a narrow waist, six-pack, obliques, ribcage, flared lats, back
    c('torso', [-3.7, 14.4, -2.6], [7.4, 4.8, 5.2], 'abdomen')
    for i, (y0, hgt) in enumerate(((14.6, 1.45), (16.2, 1.4), (17.75, 1.35))):
        for s in (-1, 1):
            c('torso', [side_x(s, 0.2, 2.7 - 0.15 * i), y0, -3.05], [2.7 - 0.15 * i, hgt, 0.45], 'ab',
              faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        c('torso', [side_x(s, 3.7, 0.8), 14.4, -2.1], [0.8, 4.6, 4.2], 'oblique', faces=('north', 'south', 'east', 'west', 'down'))
        c('torso', [side_x(s, 5.0, 2.4), 16.0, -2.2], [2.4, 8.0, 5.2], 'lat', rotation=[0, 0, s * 17], pivot=[s * 5.4, 24.0, 0],
          faces=('north', 'south', 'east', 'west', 'down'))
        c('torso', [side_x(s, 0.35, 3.4), 14.2, 2.6], [3.4, 5.0, 0.7], 'back', faces=('south', 'east', 'west', 'up', 'down'))
    c('torso', [-5.8, 19.0, -3.0], [11.6, 3.0, 6.4], 'rib')
    # ---- chest (breathes with the pulse): upper chest, pecs, traps, neck, upper back
    c('chest', [-6.9, 21.6, -3.4], [13.8, 4.6, 7.4], 'chest')
    for s in (-1, 1):
        c('chest', [side_x(s, 0.18, 6.5), 19.9, -4.8], [6.5, 5.4, 1.4], 'pec')
        c('chest', [side_x(s, 1.0, 6.2), 25.0, -2.3], [6.2, 3.4, 5.6], 'trap', rotation=[0, 0, s * 22], pivot=[s * 1.0, 26.8, 0])
    c('chest', [-6.4, 20.6, 4.0], [12.8, 5.4, 0.9], 'back', faces=('south', 'east', 'west', 'up', 'down'))
    c('chest', [-2.8, 25.2, -3.5], [5.6, 3.0, 4.7], 'neck')

    # ---- arms
    for s in (-1, 1):
        side = sname(s)
        a, f, h, fi, th = f'{side}_arm', f'{side}_forearm', f'{side}_hand', f'{side}_fingers', f'{side}_thumb'
        c(a, [side_x(s, 6.0, 6.6), 19.9, -3.5], [6.6, 6.4, 6.9], 'delt')
        c(a, [side_x(s, 6.6, 5.6), 26.3, -2.9], [5.6, 1.0, 5.8], 'delt_top', faces=SIDES + ('up',))
        c(a, [side_x(s, 7.0, 4.7), 15.8, -2.3], [4.7, 4.6, 4.5], 'arm', faces=('east', 'west', 'down'))
        c(a, [side_x(s, 7.35, 4.0), 16.5, -3.1], [4.0, 4.0, 0.8], 'bicep', faces=('north', 'east', 'west', 'up', 'down'))
        c(a, [side_x(s, 7.2, 4.3), 16.6, 2.2], [4.3, 4.0, 0.85], 'tricep', faces=('south', 'east', 'west', 'down'))
        # forearm: elbow, a huge tapering forearm, wrist
        c(f, [side_x(s, 7.4, 4.2), 14.4, -2.1], [4.2, 1.9, 4.2], 'elbow', faces=SIDES + ('up',))
        c(f, [side_x(s, 7.0, 5.2), 11.6, -2.7], [5.2, 2.9, 5.2], 'forearm')
        c(f, [side_x(s, 7.5, 4.2), 9.8, -2.2], [4.2, 1.9, 4.2], 'wrist', faces=SIDES + ('down',))
        # hand: palm, knuckles, four clawed fingers (one bone), a clawed thumb
        c(h, [side_x(s, 7.9, 3.4), 7.5, -2.3], [3.4, 2.4, 4.4], 'hand')
        c(h, [side_x(s, 7.8, 3.6), 7.4, -2.4], [3.6, 0.6, 4.6], 'knuckle', faces=SIDES + ('down',))
        for i in range(4):
            z0 = -2.3 + i * 1.12
            ln = 2.3 if i in (1, 2) else 2.0
            c(fi, [side_x(s, 8.1, 2.6), FINGER_Y - ln, z0], [2.6, ln, 0.95], 'finger', d=4)
            c(fi, [side_x(s, 8.5, 1.6), FINGER_Y - ln - 2.4, z0 + 0.15], [1.6, 2.4, 0.65], 'claw', d=5,
              faces=SIDES)
        c(th, [side_x(s, 7.2, 1.4), 6.8, -3.0], [1.4, 2.0, 1.4], 'finger', d=4)
        c(th, [side_x(s, 7.4, 1.0), 4.9, -2.85], [1.0, 1.9, 0.9], 'claw', d=5, faces=SIDES)

    # ---- legs: thighs with a quad panel, knees, calves, feet with three clawed toes
    for s in (-1, 1):
        side = sname(s)
        lg, sh, ft = f'{side}_leg', f'{side}_shin', f'{side}_foot'
        c(lg, [side_x(s, 0.3, 5.4), 6.4, -2.9], [5.4, 6.4, 5.6], 'thigh')
        c(lg, [side_x(s, 0.8, 4.4), 7.4, -3.25], [4.4, 4.8, 0.45], 'quad', faces=('north', 'east', 'west', 'up', 'down'))
        c(sh, [side_x(s, 1.2, 3.6), 5.0, -3.0], [3.6, 1.5, 0.8], 'knee', faces=('north', 'east', 'west', 'up', 'down'))
        c(sh, [side_x(s, 0.8, 4.4), 1.4, -2.2], [4.4, 5.4, 4.4], 'calf', faces=SIDES + ('up',))
        c(sh, [side_x(s, 1.2, 3.6), 2.6, 2.2], [3.6, 3.0, 0.7], 'calf', faces=('south', 'east', 'west', 'down', 'up'))
        c(ft, [side_x(s, 0.7, 5.0), 0.0, -4.4], [5.0, 1.6, 6.4], 'foot')
        for i in range(3):
            c(ft, [side_x(s, 0.85 + i * 1.65, 1.35), 0.0, -5.8], [1.35, 1.1, 1.4], 'toe', d=4,
              faces=('north', 'east', 'west', 'up'))
            c(ft, [side_x(s, 1.05 + i * 1.65, 0.95), 0.0, -6.9], [0.95, 0.7, 1.1], 'claw', d=5,
              faces=('east', 'west', 'up'))

    # ---- head (6 px per unit): long skull, the maw, teeth rows, the hinged jaw, the tongue
    hd = 'head'
    c(hd, [-3.5, 28.8, -7.6], [7.0, 4.4, 7.0], 'cranium', d=DF)
    c(hd, [-3.3, 32.15, -7.9], [6.6, 0.85, 0.3], 'brow', d=DF, faces=('north', 'east', 'west', 'up'))
    c(hd, [-3.0, 27.0, -1.4], [6.0, 4.6, 1.6], 'nape', d=DF, faces=('south', 'east', 'west', 'up', 'down'))
    c(hd, [-3.4, 27.0, -8.1], [6.8, 1.8, 6.4], 'maxilla', d=DF, faces=('north', 'east', 'west', 'down'))
    c(hd, [-3.05, 24.9, -2.3], [6.1, 2.1, 0.2], 'mouth', d=DF, faces=('north',))             # throat wall
    # upper teeth: front row, side rows, long canines
    c(hd, [-3.25, 25.6, -8.05], [6.5, 1.4, 0.22], 'teeth_up', d=DF, faces=('north', 'south'))
    for s in (-1, 1):
        c(hd, [side_x(s, 3.05, 0.22), 25.7, -7.8], [0.22, 1.3, 5.2], 'teeth_up', d=DF, faces=('east', 'west'))
        c(hd, [side_x(s, 2.1, 0.62), 24.6, -8.0], [0.62, 2.4, 0.3], 'fang_up', d=DF, faces=('north', 'south', 'east', 'west'))
        c(hd, [side_x(s, 2.9, 0.3), 25.2, -6.2], [0.3, 1.8, 0.5], 'fang_up', d=DF, faces=('north', 'south', 'east', 'west'))
    # the lower jaw (hinge behind the mouth): jaw, chin, gums, lower teeth, fangs, throat
    jw = 'jaw'
    c(jw, [-3.3, 24.4, -8.0], [6.6, 1.9, 6.2], 'jaw', d=DF, faces=('north', 'east', 'west', 'up', 'down'))
    c(jw, [-2.2, 23.9, -8.25], [4.4, 1.0, 1.1], 'chin', d=DF, faces=('north', 'east', 'west', 'down'))
    c(jw, [-2.7, 23.5, -6.2], [5.4, 1.0, 4.6], 'throat', d=DF, faces=('east', 'west', 'down', 'south'))
    c(jw, [-3.0, 26.3, -7.85], [6.0, 1.3, 0.22], 'teeth_lo', d=DF, faces=('north', 'south'))
    for s in (-1, 1):
        c(jw, [side_x(s, 2.85, 0.22), 26.3, -7.6], [0.22, 1.2, 5.0], 'teeth_lo', d=DF, faces=('east', 'west'))
        c(jw, [side_x(s, 1.5, 0.5), 26.3, -7.8], [0.5, 1.9, 0.26], 'fang_lo', d=DF, faces=('north', 'south', 'east', 'west'))
    # the tongue: six bones lolling out of the maw, the last one pointed
    for i, (px, py, pz) in enumerate(TONGUE):
        nxt = TONGUE[i + 1][2] if i + 1 < len(TONGUE) else pz - 1.5
        w = 1.7 - 0.16 * i
        c(f'tongue_{i}', [-w / 2, py - 0.3, nxt], [w, 0.72 - 0.04 * i, pz - nxt + (0.05 if i + 1 < len(TONGUE) else 0)],
          'tongue_tip' if i == len(TONGUE) - 1 else 'tongue', d=DF,
          faces=('north', 'east', 'west', 'up', 'down') if i == len(TONGUE) - 1 else ('east', 'west', 'up', 'down'))
    return m


# ============================================================================================ outputs
ARM_BONES = ('arm', 'forearm', 'hand', 'fingers', 'thumb')
FP_TOP = {'right_arm': 'armorRightArm', 'left_arm': 'armorLeftArm'}


def arms_only(src, ident):
    """First-person arms: the arm chains of the body (same atlas / UVs), top bones renamed for the FP clips."""
    fp = VenomModel(ident, density=src.density, width=src.width)
    fp.height = src.height
    for b in src.bones:
        parts = b.name.split('_', 1)
        if parts[0] not in ('right', 'left') or len(parts) < 2 or parts[1] not in ARM_BONES:
            continue
        nb = fp.bone(FP_TOP.get(b.name, b.name), None if b.name in FP_TOP else FP_TOP.get(b.parent, b.parent), b.pivot, b.rotation)
        nb.cubes = [copy.copy(cb) for cb in b.cubes]
    return fp


def layer(glow, tags, colour=None, keep_alpha=True):
    out = np.zeros_like(glow, dtype=np.uint8)
    mask = np.isin(glow[..., 3], tags)
    if colour is None:
        out[mask, :3] = glow[mask, :3]
    else:
        out[mask, :3] = np.array([int(colour[i:i + 2], 16) for i in (1, 3, 5)], np.uint8)
    out[..., 3] = np.where(mask, 255, 0)
    return out


def marks_texture(glow):
    """White marks; the lowest texel row of each mark is a cool grey."""
    out = layer(glow, (MARK_EYE_A, MARK_A))
    m = out[..., 3] > 0
    low = m & ~np.roll(m, -1, axis=0)                  # the lowest texel row of each mark sits in shadow
    out[low, :3] = np.array([int(WHITE_LO[i:i + 2], 16) for i in (1, 3, 5)], np.uint8)
    return out


def write_outputs(body):
    base, glow = body.paint()
    write_json(os.path.join(RES, 'geo/venom/venom.geo.json'), body.geo_json())
    write_png('textures/entity/venom/venom.png', base)
    write_png('textures/entity/venom/venom_marks.png', marks_texture(glow))
    write_png('textures/entity/venom/venom_eyes.png', layer(glow, (MARK_EYE_A,), EYE_GLOW))
    write_png('textures/entity/venom/venom_pulse.png', layer(glow, (PULSE_A,)))
    write_json(os.path.join(RES, 'geo/venom/venom_arms.geo.json'), arms_only(body, 'venom_arms').geo_json())
    split_b64('textures/entity/venom/venom.png')
    return base, glow


def main():
    body = body_model()
    write_outputs(body)
    import venom_skin
    venom_skin.main()
    print('venom cubes', sum(len(b.cubes) for b in body.bones), 'bones', len(body.bones), 'atlas', (body.width, body.height))


if __name__ == '__main__':
    main()
