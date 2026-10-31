"""Captain America (Steve Rogers, The Avengers 2012 suit) — body, gear, first-person arms and the vibranium shield.

Own procedural pixel art, CC0 (no film frames, no third-party textures). Rebuilt from scratch on the shaded
pixel-art pipeline (tools/assets/pixel_shade.py: baked occlusion from the model's own geometry, form light, bevelled
edges, hue-shifted tone steps with ordered dither, per-material micro texture) with Reinhard's model as the bar.

Body: Steve's face (heavy brows, blue eyes, straight nose, firm mouth, cleft chin) framed by the helmet; the suit is
split into a raised chest plate carrying a two-layer star (silver rim + white star with bevelled points), a quilted
abdomen panel with four raised red stripes on white, dark ribbed side panels, a high collar, a quilted back yoke,
two-tier padded shoulders, sleeves with seams, leather gloves (knuckle pad, four fingers, thumb), thighs with seams
and quilted knee pads.
Gear: helmet (crown, top cap, ridge, brow lip, cheek guards with flaps, nape guard with a flared lip), the letter
"A" built from three blocks, three-feather wings per side, ear discs with bosses, a chin strap rising to the ears;
utility belt with a buckle, rivets and six pouches (flaps, snaps); flared gauntlet cuffs (band, bell, rolled rim);
knee-high boots (sole, shell, two straps with buckles, fold-over cuff, toe cap, heel counter); a back harness with
a clamp ring.
Shield: terraced dome (four levels, real step walls, three-layer bevelled star, 24-tile rolled rim, straps, grip),
lathe-turned rings and a polished highlight painted in.

Skeleton (same names / pivots as the player rig, so the renderer, the FP offset and the clip tools keep working):
root > waist > torso > head | right_arm > right_forearm > right_hand > right_fingers, right_thumb | left_* mirrored;
root > right_leg > right_shin, left_leg > left_shin. Gear repeats the names; `left_shield` (gear, left forearm) is
the empty bone the renderer draws the shield on.
Outputs geo/captain/{captain,captain_arms,captain_gear,captain_arms_gear,captain_shield}.geo.json and
textures/entity/captain/{captain,captain_skin,captain_gear,captain_gear_glow,captain_shield,captain_shield_glow}.png
(captain_skin.png = vanilla 64x64 layout for the vanilla hand / HeroSkins).
Run from the repository root with PYTHONHASHSEED=0; check with tools/assets/geo_lint.py captain captain_arms shield.
"""
import copy
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from avengers_common import Mat, build_body, near, rig, side_x, sname, stitches  # noqa: E402
from clean_geo import CleanModel, Face, Flat, put, star_mask, write_json, write_model, write_png, RES  # noqa: E402

D = 2
LIGHT = (32.0, 0.0, 0.05)  # model-wide vertical light: +5 % at the crown .. -5 % at the soles
ELBOW_Y, WRIST_Y, KNUCKLE_Y, KNEE_Y = 18, 15, 13, 6

# ---- palette: bright, saturated 2012 suit ---------------------------------------------------------------
BLUE, BLUE_HI, BLUE_LO, BLUE_DK = '#2f58b6', '#4a76d2', '#23438f', '#18306b'
RED, RED_LO = '#cf2433', '#9c1a26'
WHITE, WHITE_LO = '#f2f3f7', '#cdd3de'
SILVER, SILVER_HI = '#c3cad6', '#e4e9f0'
LEATHER, LEATHER_HI, LEATHER_LO, LEATHER_DK = '#7f4c28', '#9c653a', '#5f371c', '#442714'
SKIN, BROW = '#edc29f', '#8d6640'
SOLE = '#2e1d12'

# ============================================================================================ face
# 16 x 16 front of the head. Rows 0-6 sit under the crown and the brow lip, columns 0-1 / 14-15 under the cheek
# guards (columns 2 / 13 of rows 12-15 under the guard flaps): the visible face is brows, eyes, nose, mouth, chin.
FACE = ['CCCCCCCCCCCCCCCC'] * 7 + [
    'ddsbBBBllBBBbsdd',   # 7  brows (thick inner ends), lit glabella
    'ddDkIIksskIIkDdd',   # 8  upper lids, iris
    'ddswipwlswpiwsdd',   # 9  sclera, iris, pupils towards the nose; lit bridge of the nose
    'ddlseeslnseesldd',   # 10 cheekbones, soft under-eye, nose
    'ddlsssslnssssldd',   # 11 cheeks, nose
    'dddsssnDDnsssddd',   # 12 nose tip shadow / nostrils
    'dddssseuuesssddd',   # 13 philtrum, upper lip
    'dddssmMMMMmssddd',   # 14 mouth line
    'ddddsdlLLldsdddd',   # 15 lower lip, chin
]
FPAL = {'C': (BLUE_DK, 0.0), 's': (SKIN, 0.0), 'l': (SKIN, 0.07), 'h': (SKIN, 0.12), 'e': (SKIN, -0.05),
        'd': (SKIN, -0.10), 'D': (SKIN, -0.2), 'b': (BROW, 0.06), 'B': (BROW, -0.05), 'k': ('#4a3226', 0.0),
        'I': ('#2e5bab', 0.0), 'i': ('#5f9be5', 0.0), 'p': ('#18223d', 0.0), 'w': ('#efede9', 0.0),
        'n': (SKIN, -0.13), 'u': ('#d79c86', 0.0), 'M': ('#93503f', 0.0), 'm': (SKIN, -0.16),
        'L': ('#e0a891', 0.04)}
assert all(len(r) == 16 for r in FACE) and len(FACE) == 16

STAR_C = (0.0, 20.85)


def outer_face(s):
    return 'east' if s > 0 else 'west'


def inner_face(s):
    return 'west' if s > 0 else 'east'


def is_outer(ctx):
    """True on the outer side face (+X on the left limbs, -X on the right ones)."""
    return ctx.name in ('east', 'west') and ((ctx.name == 'east') == (float(np.mean(ctx.x)) > 0))


def rows(ctx, lo, hi):
    """Mask of texel rows lo..hi (negative = from the bottom) of a face."""
    m = np.zeros((ctx.h, ctx.w), bool)
    m[slice(lo, hi), :] = True
    return m


def quilt_rows(ctx, ys, dark=-0.09, lit=0.045, mask=None):
    """Padded quilting: a dark seam row with a lit row under it at each world y."""
    for y0 in ys:
        a = near(ctx.y, y0, 0.26)
        b = near(ctx.y, y0 - 0.5, 0.24) & ~a
        if mask is not None:
            a, b = a & mask, b & mask
        ctx.light(a, dark)
        ctx.light(b, lit)


def star_bevel(ctx, cx, cy, amount=0.13):
    """Raised five-point star: every point has a ridge from the centre to the tip; each of its two facets takes the
    light from the upper left (as seen from the front: +x is the viewer's right)."""
    th = np.arctan2(ctx.x - cx, ctx.y - cy)
    tip = np.round(th / (2 * np.pi / 5)) * (2 * np.pi / 5)
    facet = tip + np.sign(th - tip + 1e-9) * (np.pi / 2)
    ctx.L += amount * np.cos(facet + np.pi / 4)
    r = np.hypot(ctx.x - cx, ctx.y - cy)
    ctx.L += 0.05 * (r < 0.45)          # the centre of the star is the highest point


def star_alpha(r, inner=0.42, cx=STAR_C[0], cy=STAR_C[1]):
    def fn(face, rs):
        x, y, z = face.world()
        a = np.zeros((face.h, face.w), np.uint8)
        a[star_mask(x, y, cx, cy, r, inner=inner)] = 255
        return a
    return fn


# ============================================================================================ body painters
def head_fn(ctx):
    if ctx.name == 'north':
        ctx.pixels(FACE, FPAL)
    elif ctx.name == 'down':
        ctx.put(ctx.z > -1.8, BLUE_DK)                   # throat inside the cowl; the chin stays skin
        ctx.light(ctx.all(), -0.1)
    else:
        ctx.put(ctx.all(), BLUE_DK)                      # cowl fabric under the helmet


def torso_fn(ctx):
    n = ctx.name
    if n == 'north':
        side = np.abs(ctx.x) > 3.5
        ctx.put(side, BLUE_LO)                           # dark side panels framing the stripes
        ctx.light(side & near(np.abs(ctx.x), 3.75, 0.26), 0.04)
    elif n in ('east', 'west'):
        ctx.put(ctx.all(), BLUE_LO)
        for z0 in (-1.25, 1.25):                         # front / back panel seams
            ctx.light(near(ctx.z, z0, 0.26), -0.11)
            ctx.light(near(ctx.z, z0 + 0.5, 0.24), 0.05)
        quilt_rows(ctx, (14.0, 15.6, 17.2, 18.8, 20.4), -0.07, 0.035, np.abs(ctx.z) < 1.0)   # ribbed side
    elif n == 'south':
        low = ctx.y < 19.3                               # under the back yoke
        ctx.light(near(ctx.x, 0.0, 0.26) & low, -0.11)   # spine seam
        ctx.light(near(ctx.x, 0.5, 0.24) & low, 0.05)
        for s in (-1, 1):                                # shoulder-blade panels
            blade = near(ctx.y, 18.6 - 0.45 * np.abs(ctx.x - s * 2.0), 0.26) & (s * ctx.x > 0.4)
            ctx.light(blade, -0.08)
        quilt_rows(ctx, (13.6, 15.0, 16.4), -0.05, 0.03, np.abs(ctx.x) > 0.6)
    elif n == 'up':
        ctx.light(ctx.all(), 0.03)


def plate_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.y > 22.6, 0.05)                    # the upper chest catches the light
        ax = np.abs(ctx.x)
        crease = near(ctx.y, 18.75 + 0.22 * ax, 0.26) & (ax > 1.2)
        ctx.light(crease, -0.1)                          # under-pec line
        ctx.light(near(ctx.y, 19.25 + 0.22 * ax, 0.24) & (ax > 1.2) & ~crease, 0.05)
        ctx.light(near(ctx.x, 0.0, 0.3) & (ctx.y < 18.7), -0.06)    # sternum groove under the star
    elif ctx.name in ('east', 'west'):
        ctx.light(ctx.all(), -0.03)


def star_rim_fn(ctx):
    star_bevel(ctx, *STAR_C, amount=0.08)


def star_fn(ctx):
    star_bevel(ctx, *STAR_C, amount=0.12)


def stripe_fn(ctx):
    if ctx.name == 'north':
        quilt_rows(ctx, (14.3, 15.7, 17.1), -0.1, 0.05)  # padded panel


def collar_fn(ctx):
    if ctx.side:
        ctx.light(rows(ctx, 0, 1), 0.06)
        if ctx.h >= 2:
            stitches(ctx, rows(ctx, -1, None), BLUE_HI, 2, 0.4)


def yoke_fn(ctx):
    if ctx.name == 'south':
        quilt_rows(ctx, (20.6, 21.8, 23.0), -0.08, 0.04)
        ctx.light(near(ctx.x, 0.0, 0.26), -0.06)
        stitches(ctx, rows(ctx, -1, None), BLUE_HI, 2, 0.35)
    elif ctx.name in ('east', 'west'):
        ctx.light(ctx.all(), -0.03)


def sleeve_fn(ctx):
    if is_outer(ctx):
        ctx.light(near(ctx.z, 0.0, 0.26), -0.1)          # outer seam
        stitches(ctx, near(ctx.z, 0.5, 0.25), BLUE_HI, 2, 0.35)
    if ctx.name == 'north':
        ctx.light((np.abs(ctx.u - 0.5) < 0.3) & (ctx.y > 19.0), 0.04)   # biceps
    if ctx.side:
        ctx.light(near(ctx.y, 18.3, 0.26), -0.06)        # elbow seam


def delt_fn(ctx):
    if ctx.side:
        f = ctx.face
        ys = np.arange(f.oy + 0.55, f.oy + f.sy - 0.3, 0.85)
        quilt_rows(ctx, ys, -0.08, 0.04)                 # padded shoulder ribs
        ctx.put(rows(ctx, -1, None), BLUE_HI, 0.35)      # piping along the lower edge
    elif ctx.name == 'up':
        ctx.light(ctx.all(), 0.04)
        ctx.light(near(ctx.z, 0.0, 0.26), -0.06)


def forearm_fn(ctx):
    if is_outer(ctx):
        ctx.light(near(ctx.z, 0.0, 0.26), -0.08)


def glove_fn(ctx):
    if ctx.side:
        ctx.light(near(ctx.y, 14.55, 0.26), -0.08)       # wrist seam under the cuff band
        ctx.light(near(ctx.y, 13.35, 0.26), -0.05)       # knuckle crease
    if ctx.name in ('east', 'west') and not is_outer(ctx):
        ctx.light(ctx.all(), -0.05)                      # palm
        ctx.light(near(ctx.y, 14.0, 0.26) & (np.abs(ctx.z) < 1.4), -0.06)
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.06)


def finger_fn(ctx):
    if ctx.side:
        ctx.light(rows(ctx, 0, 1), 0.05)                 # knuckle row
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.05)


def knuckle_fn(ctx):
    if is_outer(ctx):
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        bump = (rr == ctx.h - 1) & (cc % 2 == 1)
        ctx.light(bump, 0.1)                             # four knuckle bumps
        ctx.light(rows(ctx, 0, 1), -0.04)
        stitches(ctx, rr == 1, LEATHER_HI, 2, 0.35)


def thigh_fn(ctx):
    n = ctx.name
    if n in ('east', 'west'):
        ctx.light(near(ctx.z, 0.0, 0.26), -0.1)          # side seams
        if is_outer(ctx):
            stitches(ctx, near(ctx.z, 0.5, 0.25), BLUE_HI, 2, 0.35)
    elif n == 'north':
        ctx.light(near(ctx.y, 11.3, 0.3), -0.07)         # crease under the belt
        ribs = (near(ctx.u, 0.3, 0.07) | near(ctx.u, 0.7, 0.07)) & (ctx.y > 8.0) & (ctx.y < 11.0)
        ctx.light(ribs, -0.05)                           # quilted thigh ribs
        ctx.light((np.abs(ctx.u - 0.5) < 0.17) & (ctx.y > 8.4) & (ctx.y < 10.6), 0.04)
    elif n == 'south':
        ctx.light(near(ctx.y, 10.8, 0.3), -0.05)


def shin_fn(ctx):
    if ctx.name in ('east', 'west'):
        ctx.light(near(ctx.z, 0.0, 0.26), -0.08)


def knee_fn(ctx):
    if ctx.name == 'north':
        quilt_rows(ctx, (7.0, 6.2), -0.09, 0.045)
        ctx.light(near(ctx.x, ctx.face.ox + ctx.face.sx / 2, 0.26) & (ctx.y < 6.6), -0.06)
        ctx.light(rows(ctx, 0, 1), 0.04)


# ============================================================================================ gear painters
def crown_fn(ctx):
    n = ctx.name
    if n == 'up':
        ctx.light(near(ctx.x, 0.0, 0.7), 0.04)
    elif ctx.side:
        ctx.light(near(ctx.y, 31.3, 0.3), 0.07)          # glossy band of the shell
        ctx.light(near(ctx.y, 30.8, 0.24), 0.03)
        ctx.light(rows(ctx, -1, None), -0.05)
        if n == 'north':
            ctx.light(near(ctx.y, 29.0, 0.3), -0.04)     # soft dip above the brow lip


def brow_fn(ctx):
    if ctx.name == 'north':
        ctx.light(rows(ctx, 0, 1), 0.06)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.08)


def guard_fn(ctx):
    if ctx.side:
        ctx.light(near(ctx.y, 28.25, 0.26), -0.07)       # seam under the crown
    if ctx.name == 'north':
        inner = (ctx.x > 0) == (ctx.u < 0.5)
        ctx.light(inner & (np.abs(ctx.u - 0.5) > 0.3), 0.06)   # lit inner edge around the face


def nape_fn(ctx):
    if ctx.side:
        ctx.light(near(ctx.y, 26.2, 0.26), -0.07)        # seam of the neck guard
        ctx.light(near(ctx.y, 25.7, 0.24), 0.04)
        ctx.light(near(ctx.y, 28.25, 0.26), -0.06)


def ear_fn(ctx):
    if ctx.name in ('east', 'west'):
        r = np.hypot(ctx.z + 0.3, ctx.y - 26.5)
        ctx.light((r > 0.75) & ((ctx.y - 26.5) - (ctx.z + 0.3) * (1 if ctx.name == 'east' else -1) > 0), 0.08)
        ctx.light((r > 0.75) & ((ctx.y - 26.5) - (ctx.z + 0.3) * (1 if ctx.name == 'east' else -1) < 0), -0.08)
        ctx.light(near(r, 0.62, 0.15), -0.1)             # groove around the boss


def wing_fn(ctx):
    if ctx.name in ('east', 'west'):
        k = np.mod(ctx.u * ctx.w * (1 if ctx.name == 'east' else -1) + (np.arange(ctx.h)[:, None]), 3.0)
        ctx.light(k < 1.0, -0.06)                        # barbs of the feather
        ctx.light(rows(ctx, 0, 1), 0.06)
        ctx.put(rows(ctx, -1, None), WHITE_LO, 0.5)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.08)


def letter_fn(ctx):
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.08)


def belt_fn(ctx):
    if ctx.side and ctx.h >= 3:
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        stitches(ctx, rr == 0, LEATHER_HI, 2, 0.5)
        stitches(ctx, rr == ctx.h - 1, LEATHER_DK, 2, 0.5)
        rivet = (rr == ctx.h // 2) & (cc % 4 == 1)
        ctx.put(rivet, SILVER)
        ctx.light(rivet, 0.1)


def buckle_fn(ctx):
    if ctx.name == 'north' and ctx.w >= 3 and ctx.h >= 3:
        ctx.light((slice(1, -1), slice(1, -1)), -0.18)  # open frame
        ctx.put((slice(1, -1), slice(ctx.w // 2, ctx.w // 2 + 1)), SILVER_HI)   # prong
        ctx.light((slice(0, 1), slice(None)), 0.08)


def pouch_fn(ctx):
    if ctx.side:
        ctx.light(rows(ctx, 0, 1), -0.1)                 # shadow under the flap
        if ctx.h >= 3:
            stitches(ctx, rows(ctx, -1, None), LEATHER_HI, 2, 0.35)


def flap_fn(ctx):
    if ctx.side:
        ctx.light(rows(ctx, 0, 1), 0.05)
        ctx.light(rows(ctx, -1, None), -0.06)


def band_fn(ctx):
    if ctx.side:
        ctx.light(rows(ctx, 0, 1), -0.06)
        if is_outer(ctx):
            b = near(ctx.z, 0.0, 0.4)
            ctx.put(b & rows(ctx, 0, None), SILVER, 0.9)  # small buckle of the wrist strap
            ctx.light(b, 0.08)


def cuff_fn(ctx):
    if ctx.side:
        ctx.light(near(ctx.y, 16.3, 0.26), -0.07)        # stitched seam around the bell
        stitches(ctx, near(ctx.y, 15.85, 0.25), LEATHER_HI, 2, 0.4)
        ctx.light(rows(ctx, 0, 1), 0.05)


def rim_fn_leather(ctx):
    if ctx.side:
        ctx.light(rows(ctx, -1, None), -0.06)
    elif ctx.name == 'up':
        ctx.light(ctx.all(), 0.05)


def boot_fn(ctx):
    if ctx.side:
        ctx.light(near(ctx.y, 1.1, 0.26), -0.06)         # ankle crease
        ctx.light(near(ctx.y, 2.7, 0.26), -0.05)
    if ctx.name == 'north':
        ctx.light((np.abs(ctx.u - 0.5) < 0.18) & (ctx.y > 2.2), 0.06)   # shin ridge
    if ctx.name == 'south':
        ctx.light(near(ctx.x, ctx.face.ox + ctx.face.sx / 2, 0.26), -0.08)   # back seam
        stitches(ctx, near(ctx.x, ctx.face.ox + ctx.face.sx / 2 + 0.5, 0.25), LEATHER_HI, 2, 0.35)


def boot_cuff_fn(ctx):
    if ctx.side:
        ctx.light(rows(ctx, 0, 1), 0.07)                 # folded-over top edge
        ctx.light(rows(ctx, -1, None), -0.08)
        if ctx.h >= 3:
            stitches(ctx, rows(ctx, -2, -1), LEATHER_LO, 2, 0.4)


def strap_fn(ctx):
    if ctx.side:
        ctx.light(rows(ctx, 0, 1), 0.05)


def toe_fn(ctx):
    if ctx.name == 'north':
        ctx.light(rows(ctx, 0, 1), 0.06)
    elif ctx.name == 'up':
        ctx.light(ctx.all(), 0.05)


def sole_fn(ctx):
    if ctx.name == 'down':
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        ctx.light((rr % 2 == 0), -0.08)                  # tread
    elif ctx.side:
        ctx.light(rows(ctx, 0, 1), 0.05)


def harness_fn(ctx):
    if ctx.side and ctx.w >= 2 and ctx.h >= 3:
        cc = np.arange(ctx.w)[None, :] * np.ones((ctx.h, 1))
        stitches(ctx, (cc == 0) | (cc == ctx.w - 1), LEATHER_HI, 2, 0.4)


def ring_fn(ctx):
    if ctx.name == 'south' and ctx.w >= 3 and ctx.h >= 3:
        ctx.light((slice(1, -1), slice(1, -1)), -0.2)    # hole of the clamp ring
        ctx.light((slice(0, 1), slice(None)), 0.08)


# ============================================================================================ materials
M = {
    'head': Mat(SKIN, 'skin', head_fn, ao=0.6, micro=0.5),
    'neck': Mat(BLUE_LO, 'cloth'),
    'torso': Mat(BLUE, 'cloth', torso_fn, cyl=0.12),
    'plate': Mat(BLUE, 'cloth', plate_fn, spec=0.18),
    'star_rim': Mat(SILVER, 'metal', star_rim_fn, ao=0.25, edges=False, micro=0.0, alpha_fn=star_alpha(2.72)),
    'star': Mat(WHITE, 'paint', star_fn, ao=0.25, edges=False, micro=0.3, alpha_fn=star_alpha(2.25)),
    'panel': Mat(WHITE, 'cloth', stripe_fn, ao=0.35, cool=(0.86, 0.9, 1.08)),
    'stripe': Mat(RED, 'cloth', stripe_fn, ao=0.45),
    'collar': Mat(BLUE_LO, 'cloth', collar_fn),
    'yoke': Mat(BLUE_LO, 'cloth', yoke_fn),
    'arm': Mat(BLUE, 'cloth', sleeve_fn, cyl=0.22),
    'delt': Mat(BLUE, 'cloth', delt_fn, cyl=0.22),
    'forearm': Mat(BLUE, 'cloth', forearm_fn, cyl=0.22),
    'hand': Mat(LEATHER, 'leather', glove_fn, cyl=0.15),
    'fingers': Mat(LEATHER_LO, 'leather', finger_fn),
    'knuckle': Mat(LEATHER_DK, 'leather', knuckle_fn),
    'leg': Mat(BLUE, 'cloth', thigh_fn, cyl=0.22),
    'shin': Mat(BLUE, 'cloth', shin_fn, cyl=0.2),
    'knee': Mat(BLUE_LO, 'cloth', knee_fn),
}

G = {
    'crown': Mat(BLUE, 'paint', crown_fn, spec=0.35, cyl=0.15),
    'crown_top': Mat(BLUE, 'paint', crown_fn, spec=0.35),
    'ridge': Mat(BLUE_HI, 'paint', spec=0.35),
    'brow': Mat(BLUE, 'paint', brow_fn, spec=0.3),
    'guard': Mat(BLUE, 'paint', guard_fn, spec=0.3),
    'nape': Mat(BLUE, 'paint', nape_fn, spec=0.3, cyl=0.15),
    'nape_lip': Mat(BLUE_LO, 'paint', spec=0.3),
    'ear': Mat(BLUE_LO, 'paint', ear_fn, spec=0.35),
    'ear_boss': Mat(SILVER, 'metal'),
    'wing': Mat(WHITE, 'paint', wing_fn, ao=0.3, cool=(0.86, 0.9, 1.08)),
    'letter': Mat(WHITE, 'paint', letter_fn, ao=0.25, cool=(0.86, 0.9, 1.08)),
    'chin': Mat(LEATHER_LO, 'leather'),
    'belt': Mat(LEATHER, 'leather', belt_fn),
    'buckle': Mat(SILVER, 'metal', buckle_fn),
    'pouch': Mat(LEATHER, 'leather', pouch_fn),
    'flap': Mat(LEATHER_HI, 'leather', flap_fn),
    'snap': Mat(SILVER_HI, 'metal', edges=False),
    'cuff_band': Mat(LEATHER_LO, 'leather', band_fn),
    'cuff': Mat(LEATHER, 'leather', cuff_fn, cyl=0.18),
    'cuff_rim': Mat(LEATHER_HI, 'leather', rim_fn_leather),
    'sole': Mat(SOLE, 'rubber', sole_fn),
    'boot': Mat(LEATHER, 'leather', boot_fn, cyl=0.2),
    'toe': Mat(LEATHER_LO, 'leather', toe_fn),
    'heel': Mat(LEATHER_LO, 'leather'),
    'boot_strap': Mat(LEATHER_DK, 'leather', strap_fn),
    'boot_buckle': Mat(SILVER, 'metal', edges=False),
    'boot_cuff': Mat(LEATHER_HI, 'leather', boot_cuff_fn, cyl=0.18),
    'harness': Mat(LEATHER_LO, 'leather', harness_fn),
    'ring': Mat(SILVER, 'metal', ring_fn),
}


# ============================================================================================ geometry
def skeleton(m, gear=False):
    rig(m)
    if gear:
        m.bone('left_shield', 'left_forearm', [8.75, 16.4, 0])


def body_model():
    m = CleanModel('captain', density=D, width=128, seed=2012, light=LIGHT)
    skeleton(m)
    build_body(m, M)
    c = m.cube
    # suit: chest plate with the two-layer star, quilted abdomen panel (white) with four raised red stripes,
    # high collar, quilted back yoke
    c('torso', [-3.8, 18.0, -2.5], [7.6, 5.6, 0.5], M['plate'])
    c('torso', [-2.75, 18.1, -2.56], [5.5, 5.5, 0.01], M['star_rim'], faces=('north',)).no_occlude = True
    c('torso', [-2.3, 18.55, -2.62], [4.6, 4.6, 0.01], M['star'], faces=('north',)).no_occlude = True
    c('torso', [-3.5, 12.95, -2.3], [7.0, 5.25, 0.3], M['panel'], faces=('north', 'east', 'west', 'down'))
    for x0 in (-3.5, -1.5, 0.5, 2.5):
        c('torso', [x0, 13.0, -2.38], [1.0, 5.15, 0.08], M['stripe'], faces=('north', 'east', 'west', 'down'))
    c('torso', [-2.7, 23.3, -2.45], [5.4, 1.0, 4.9], M['collar'], faces=('north', 'south', 'east', 'west', 'up'))
    c('torso', [-4.15, 19.2, 2.0], [8.3, 4.5, 0.3], M['yoke'], faces=('south', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        side = sname(s)
        # two-tier padded shoulder (upper cap wider than the lower ring: stepped silhouette)
        c(f'{side}_arm', [side_x(s, 3.85, 4.4), 22.55, -2.3], [4.4, 1.75, 4.6], M['delt'])
        c(f'{side}_arm', [side_x(s, 3.95, 4.25), 20.9, -2.2], [4.25, 1.75, 4.4], M['delt'],
          faces=('north', 'south', 'east', 'west', 'down'))
        # glove: padded knuckle plate on the back of the hand
        c(f'{side}_hand', [side_x(s, 8.0, 0.25), 13.05, -1.6], [0.25, 1.6, 3.2], M['knuckle'],
          faces=(outer_face(s), 'north', 'south', 'up', 'down'))
        # quilted knee pad (thigh bone, overlapping the boot cuff)
        c(f'{side}_leg', [side_x(s, 0.4, 3.3), 5.5, -2.36], [3.3, 2.1, 0.4], M['knee'],
          faces=('north', 'east', 'west', 'up', 'down'))
    return m


def gear_model():
    m = CleanModel('captain_gear', density=D, width=128, seed=1942, light=LIGHT)
    skeleton(m, gear=True)
    c = m.cube
    # ---- helmet: stepped dome (crown, upper crown, top cap, ridge), brow lip, cheek guards + flaps, nape guard
    c('head', [-4.4, 28.5, -4.4], [8.8, 3.35, 8.8], G['crown'], faces=('north', 'south', 'east', 'west', 'up'))
    c('head', [-4.1, 31.85, -4.1], [8.2, 0.7, 8.2], G['crown_top'], faces=('north', 'south', 'east', 'west', 'up'))
    c('head', [-3.6, 32.55, -3.6], [7.2, 0.35, 7.2], G['crown_top'], faces=('north', 'south', 'east', 'west', 'up'))
    c('head', [-0.35, 32.9, -2.8], [0.7, 0.2, 5.6], G['ridge'], faces=('north', 'south', 'east', 'west', 'up'))
    c('head', [-3.75, 28.5, -4.75], [7.5, 0.55, 0.4], G['brow'], faces=('north', 'east', 'west', 'up', 'down'))
    c('head', [-4.4, 24.0, -0.3], [8.8, 4.5, 4.7], G['nape'], faces=('south', 'east', 'west', 'down'))
    c('head', [-4.5, 23.6, 0.6], [9.0, 0.55, 4.05], G['nape_lip'], faces=('south', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        o = outer_face(s)
        c('head', [side_x(s, 3.0, 1.4), 24.2, -4.4], [1.4, 4.3, 4.1], G['guard'], faces=('north', 'east', 'west', 'down'))
        c('head', [side_x(s, 2.45, 0.55), 24.2, -4.4], [0.55, 1.8, 1.0], G['guard'],
          faces=('north', inner_face(s), 'up', 'down'))
        # ear disc (two crossed plates = rounded outline) with a silver boss
        c('head', [side_x(s, 4.4, 0.3), 25.65, -1.45], [0.3, 1.7, 2.3], G['ear'], faces=(o, 'north', 'south', 'up', 'down'))
        c('head', [side_x(s, 4.4, 0.26), 25.2, -1.05], [0.26, 2.6, 1.5], G['ear'], faces=(o, 'north', 'south', 'up', 'down'))
        c('head', [side_x(s, 4.7, 0.2), 26.1, -0.7], [0.2, 0.8, 0.8], G['ear_boss'], faces=(o, 'north', 'south', 'up', 'down'))
        # wing: three feathers fanning up and back out of the ear disc (top one longest)
        for i, (y0, z0, L, ang) in enumerate(((28.7, -2.2, 3.6, 24), (28.15, -2.0, 3.1, 18), (27.6, -1.8, 2.5, 12))):
            t = 0.24 - 0.02 * i
            ox = side_x(s, 4.38, t)
            c('head', [ox, y0, z0], [t, 0.6, L], G['wing'], rotation=[ang, 0, 0], pivot=[ox, y0, z0],
              faces=(o, 'north', 'south', 'up', 'down'))
    c('head', [-4.0, 23.62, -4.3], [8.0, 0.4, 1.4], G['chin'], faces=('north', 'east', 'west', 'down'))
    # the letter A: two slanted legs (pivot at the apex) and a cross bar, white blocks standing off the forehead
    for sg, dz in ((1, 0.0), (-1, 0.04)):
        c('head', [-0.3, 29.3, -4.68 + dz], [0.6, 2.5, 0.26 - dz], G['letter'], rotation=[0, 0, sg * 17],
          pivot=[0.0, 31.8, -4.6], faces=('north', 'east', 'west', 'up', 'down'))
    c('head', [-0.7, 29.9, -4.6], [1.4, 0.45, 0.18], G['letter'], faces=('north', 'up', 'down'))
    # ---- utility belt: belt with rivets, buckle, six pouches (body, flap, snap)
    c('torso', [-4.45, 11.55, -2.65], [8.9, 1.45, 5.3], G['belt'])
    c('torso', [-1.1, 11.45, -2.95], [2.2, 1.65, 0.35], G['buckle'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        c('torso', [side_x(s, 1.6, 1.6), 10.9, -3.15], [1.6, 1.85, 0.55], G['pouch'], faces=('north', 'east', 'west', 'down'))
        c('torso', [side_x(s, 1.55, 1.7), 12.0, -3.25], [1.7, 0.8, 0.35], G['flap'])
        c('torso', [side_x(s, 2.2, 0.4), 12.1, -3.33], [0.4, 0.35, 0.1], G['snap'], faces=('north',))
        c('torso', [side_x(s, 4.4, 0.6), 10.9, -1.2], [0.6, 1.85, 2.0], G['pouch'], faces=(outer_face(s), 'north', 'south', 'down'))
        c('torso', [side_x(s, 4.45, 0.7), 12.0, -1.25], [0.7, 0.8, 2.1], G['flap'])
        c('torso', [side_x(s, 0.9, 1.9), 10.9, 2.6], [1.9, 1.85, 0.6], G['pouch'], faces=('south', 'east', 'west', 'down'))
        c('torso', [side_x(s, 0.85, 2.0), 12.0, 2.65], [2.0, 0.8, 0.65], G['flap'])
    # ---- back harness: two straps (over the yoke, down to the belt, over the shoulders), cross strap, clamp ring
    for s in (-1, 1):
        x0 = side_x(s, 2.7, 0.95)
        c('torso', [x0, 19.2, 2.32], [0.95, 4.75, 0.25], G['harness'], faces=('south', 'east', 'west', 'up'))
        c('torso', [x0, 13.0, 2.02], [0.95, 6.3, 0.25], G['harness'], faces=('south', 'east', 'west', 'down'))
        c('torso', [x0, 23.95, 0.3], [0.95, 0.25, 2.27], G['harness'], faces=('up', 'north', 'east', 'west'))
    c('torso', [-3.0, 16.3, 2.08], [6.0, 0.8, 0.26], G['harness'], faces=('south', 'up', 'down'))
    c('torso', [-0.7, 16.0, 2.34], [1.4, 1.4, 0.2], G['ring'], faces=('south', 'east', 'west', 'up', 'down'))
    # ---- flared gauntlet cuffs and knee-high boots
    for s in (-1, 1):
        side = sname(s)
        c(f'{side}_forearm', [side_x(s, 3.8, 4.4), 14.6, -2.2], [4.4, 1.0, 4.4], G['cuff_band'])
        c(f'{side}_forearm', [side_x(s, 3.65, 4.7), 15.5, -2.35], [4.7, 1.75, 4.7], G['cuff'])
        c(f'{side}_forearm', [side_x(s, 3.55, 4.9), 17.15, -2.45], [4.9, 0.4, 4.9], G['cuff_rim'])
        c(f'{side}_shin', [side_x(s, 0.0, 4.2), 0.0, -2.65], [4.2, 0.5, 5.05], G['sole'])
        c(f'{side}_shin', [side_x(s, 0.06, 4.08), 0.5, -2.15], [4.08, 4.2, 4.3], G['boot'])
        c(f'{side}_shin', [side_x(s, 0.25, 3.7), 0.5, -2.55], [3.7, 1.3, 0.45], G['toe'], faces=('north', 'east', 'west', 'up'))
        c(f'{side}_shin', [side_x(s, 0.3, 3.6), 0.5, 2.1], [3.6, 1.5, 0.3], G['heel'], faces=('south', 'east', 'west', 'up'))
        for y0 in (1.5, 3.1):
            c(f'{side}_shin', [side_x(s, 0.02, 4.16), y0, -2.22], [4.16, 0.5, 4.44], G['boot_strap'])
            c(f'{side}_shin', [side_x(s, 4.18, 0.14), y0 - 0.1, -0.55], [0.14, 0.7, 0.9], G['boot_buckle'],
              faces=(outer_face(s), 'north', 'south', 'up', 'down'))
        c(f'{side}_shin', [side_x(s, 0.0, 4.2), 4.6, -2.28], [4.2, 1.05, 4.56], G['boot_cuff'])
    return m


# ============================================================================================ shield
SH_RED, SH_RED_LO = '#c81d2b', '#8f141f'
SH_WHITE, SH_WHITE_LO = '#eef0f4', '#c4cad5'
SH_BLUE, SH_BLUE_HI = '#1f45a3', '#3561c4'
SH_BACK, SH_BACK_LO = '#8d96a3', '#6c7480'
SH_STEEL = '#b7bfca'
R_OUT, R_WHITE, R_RED2, R_BLUE, R_STAR = 6.6, 5.15, 3.85, 2.6, 2.25
CHARGE = (110, 170, 255)
LIT = 3 * math.pi / 4          # light from the upper left of the shield front (+x = the viewer's right)


def ring_colour(r):
    if r <= R_BLUE:
        return SH_BLUE
    if r <= R_RED2:
        return SH_RED
    if r <= R_WHITE:
        return SH_WHITE
    return SH_RED


def lathe(ctx, r, amt=0.012, period=0.5):
    """Concentric lathe marks: alternating rings of slightly lighter / darker metal."""
    band = np.floor(r / period).astype(int) % 2
    ctx.L += np.where(band == 0, amt, -amt)


def polish(ctx, r, amount=0.14):
    """Polished dome: the upper left of every ring catches the light, the lower right falls off."""
    ang = np.arctan2(ctx.y, ctx.x)
    ctx.L += amount * np.cos(ang - LIT) * np.clip(r / R_OUT, 0.25, 1.0)


def sh_level_fn(vis_lo, r_hi, outer):
    def fn(ctx):
        r = np.hypot(ctx.x, ctx.y)
        if ctx.name == 'north':
            for lo, hi in ((R_RED2, R_WHITE), (R_BLUE, R_RED2), (0.0, R_BLUE), (R_WHITE, R_OUT + 0.3)):
                ctx.put((r > lo) & (r <= hi), ring_colour((lo + hi) / 2))
            ctx.light((r > r_hi - 0.45) & (r <= r_hi), 0.1)                   # polished lip of the level
            ctx.light((r > r_hi - 0.9) & (r <= r_hi - 0.45), 0.03)
            if vis_lo > 0:
                ctx.light((r > vis_lo) & (r < vis_lo + 0.35), -0.09)            # foot of the next step
            lathe(ctx, r)
            polish(ctx, r)
            ang = np.arctan2(ctx.y, ctx.x)
            glint = (np.abs(ang - LIT) < 0.26) & (r > vis_lo + 0.4) & (r < r_hi - 0.5)
            ctx.light(glint, 0.08)                                              # specular glint
            ctx.emit((r > r_hi - 0.35) & (r <= r_hi), CHARGE)                  # kinetic charge (empowered only)
        elif ctx.name == 'south' and outer:
            ctx.put(ctx.all(), SH_BACK)
            lathe(ctx, r, 0.03, 0.6)
            polish(ctx, r, 0.07)
            ctx.light(r > R_OUT - 0.6, -0.1)
    return fn


def level_alpha(r_hi):
    def fn(face, rs):
        x, y, z = face.world()
        a = np.zeros((face.h, face.w), np.uint8)
        a[np.hypot(x, y) <= r_hi] = 255
        return a
    return fn


def sh_star_fn(ctx):
    star_bevel(ctx, 0.0, 0.0, 0.14)
    ctx.emit(ctx.all(), (150, 200, 255))


def sh_star_alpha(r, inner=0.40):
    return star_alpha(r, inner, 0.0, 0.0)


def facing(ang_deg):
    """Light factor of a radial face turned by ang_deg about Z (its outward direction is 90 - ang degrees)."""
    return math.cos(math.radians(90.0 - ang_deg) - LIT)


def sh_wall_fn(colour, k):
    def fn(ctx):
        ctx.put(ctx.all(), colour)
        ctx.light(ctx.all(), 0.1 * k if ctx.name == 'up' else -0.12)
        ctx.light(rows(ctx, 0, 1) if ctx.h > 1 else ctx.all(), 0.03)
    return fn


def sh_rim_fn(k):
    def fn(ctx):
        n = ctx.name
        if n == 'north':
            ctx.light(ctx.all(), 0.08 * k)
            ctx.light(rows(ctx, -1, None), 0.1)              # inner lip catches the light
            ctx.light(rows(ctx, 0, 1), -0.06)
            ctx.emit(ctx.all(), (90, 150, 255))
        elif n == 'up':                                      # bare vibranium edge
            ctx.put(ctx.all(), SH_STEEL)
            ctx.light(ctx.all(), 0.12 * k)
            ctx.light(rows(ctx, 0, 1), 0.06)
            ctx.emit(ctx.all(), (90, 150, 255))
        elif n == 'down':
            ctx.put(ctx.all(), SH_RED_LO)
        elif n == 'south':
            ctx.put(ctx.all(), SH_BACK_LO)
            ctx.light(ctx.all(), 0.05 * k)
    return fn


def sh_strap_fn(ctx):
    if ctx.side:
        ctx.light(rows(ctx, 0, 1), 0.05)
    if ctx.name == 'south' and ctx.h >= 3:
        stitches(ctx, rows(ctx, 0, 1) | rows(ctx, -1, None), '#8a5a34', 2, 0.5)


def sh_grip_fn(ctx):
    if ctx.side or ctx.name == 'south':
        s = np.mod(ctx.y * 1.6, 1.0)
        ctx.light(s < 0.25, -0.12)                           # wrapped grip
        ctx.light((s > 0.3) & (s < 0.5), 0.05)


def sh_mat(base, fn, ao=0.0, alpha_fn=None, kind='flat', spec=0.45):
    m = Mat(base, kind, fn, ao=ao, quant=0.03, alpha_fn=alpha_fn, edges=False, micro=0.0, light=False, spec=spec)
    m.keep_glow = False          # glow (charge) texels stay shaded in the base texture
    return m


def shield_model():
    """Terraced vibranium dome: four stepped levels (cut-out discs) with real side walls, a raised star in three
    stacked layers, a thick rolled rim of 24 segments and the back with two straps and a grip. Front = -Z."""
    m = CleanModel('captain_shield', density=4, width=256, seed=1941)
    m.bone('shield', None, [0, 0, 0])
    m.bone('rim', 'shield', [0, 0, 0])
    c = m.cube
    levels = ((R_WHITE, R_OUT - 0.2, 0.0), (R_RED2, R_WHITE, -0.22), (R_BLUE, R_RED2, -0.44), (0.0, R_BLUE, -0.64))
    for i, (vis_lo, r_hi, zf) in enumerate(levels):
        faces = ('north', 'south') if i == 0 else ('north',)
        c('shield', [-r_hi, -r_hi, zf], [2 * r_hi, 2 * r_hi, 0.62 if i == 0 else 0.22],
          sh_mat(SH_RED, sh_level_fn(vis_lo, r_hi, i == 0), alpha_fn=level_alpha(r_hi)), faces=faces)
    # step walls: 16 thin segments per step (rotated about Z), only their outward / inward sides
    for r, zf, colour in ((R_WHITE, -0.22, SH_WHITE_LO), (R_RED2, -0.44, SH_RED_LO), (R_BLUE, -0.64, SH_BLUE)):
        n = 16
        seg = 2 * r * math.tan(math.pi / n) + 0.06
        for k in range(n):
            ang = 360.0 * k / n
            c('shield', [-seg / 2, r - 0.16, zf], [seg, 0.16, 0.221], sh_mat(colour, sh_wall_fn(colour, facing(ang))),
              rotation=[0, 0, ang], pivot=[0, 0, 0], faces=('up', 'down'))
    # raised star: three stacked cut-out layers (the bevel reads as thickness)
    for zz, rr, col in ((-0.70, R_STAR, SH_WHITE_LO), (-0.76, R_STAR * 0.98, SH_WHITE), (-0.82, R_STAR * 0.96, SH_WHITE)):
        c('shield', [-rr, -rr, zz], [2 * rr, 2 * rr, 0.04], sh_mat(col, sh_star_fn, alpha_fn=sh_star_alpha(rr)),
          faces=('north',))
    # rolled rim: 24 segments, red face with a bright inner lip, bare metal outer edge
    n = 24
    seg = 2 * R_OUT * math.tan(math.pi / n) + 0.05
    for k in range(n):
        ang = 360.0 * k / n
        dz = 0.02 * (k % 2)  # odd tiles stand proud by a hair: overlapping tile corners never share a plane
        c('rim', [-seg / 2, R_OUT - 0.42 - dz, -0.34 - dz], [seg, 0.62 + 2 * dz, 1.04 + 2 * dz],
          sh_mat(SH_RED, sh_rim_fn(facing(ang))), rotation=[0, 0, ang], pivot=[0, 0, 0],
          faces=('north', 'south', 'up', 'down'))
    # back: two leather straps with rivets and the wrapped grip bar
    for y0 in (1.6, -2.4):
        c('shield', [-3.6, y0, 0.66], [7.2, 0.8, 0.28], sh_mat('#6b4224', sh_strap_fn, ao=0.3, kind='leather', spec=0.25))
        for x0 in (-3.35, 2.95):
            c('shield', [x0, y0 + 0.2, 0.94], [0.4, 0.4, 0.06], sh_mat(SH_STEEL, None, kind='metal', spec=0.6),
              faces=('south', 'east', 'west', 'up', 'down'))
    c('shield', [-0.5, -1.7, 0.7], [1.0, 3.6, 0.42], sh_mat('#4b2f1a', sh_grip_fn, ao=0.3, kind='leather', spec=0.2))
    return m


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


# ---- vanilla 64 x 64 skin (vanilla hand in first person, HeroSkins): same suit, one texel per pixel
VANILLA_BOXES = (  # origin, size, uv, part
    ([-4, 24, -4], [8, 8, 8], (0, 0), 'head'),
    ([-4, 12, -2], [8, 12, 4], (16, 16), 'torso'),
    ([-8, 12, -2], [4, 12, 4], (40, 16), 'arm'),
    ([4, 12, -2], [4, 12, 4], (32, 48), 'arm'),
    ([-4, 0, -2], [4, 12, 4], (0, 16), 'leg'),
    ([0, 0, -2], [4, 12, 4], (16, 48), 'leg'),
)
V_HEAD = ['HHHAAHHH', 'HHAHHAHH', 'HHAAAAHH', 'hhhhhhhh', 'GwIssIwG', 'GssnnssG', 'GsmuumsG', 'GssccssG']
V_HEAD_SIDE = ['HHHHHHHH', 'HHHHHHHH', 'HAAAAHHH', 'HHAAAHHH', 'HHHSSHHH', 'HHHSSHHH', 'HHHHHHHH', 'hhhhhhhh']
V_TORSO = ['BBBBBBBB', 'BBBWWBBB', 'BWWWWWWB', 'BBWWWWBB', 'BBWBBWBB', 'BBBBBBBB'] + ['bRWRRWRb'] * 5 + ['LLLSSLLL']
VPAL = {'H': BLUE, 'A': WHITE, 'h': BLUE_LO, 'G': BLUE, 'w': '#efede9', 'I': '#2e5bab', 's': SKIN, 'n': '#d9a582',
        'm': '#86493d', 'u': '#b06d5e', 'c': '#dcae8b', 'B': BLUE, 'b': BLUE_LO, 'W': WHITE, 'R': RED, 'L': LEATHER,
        'S': SILVER, 'K': LEATHER_HI, 'D': LEATHER_DK}


def vanilla_rows(part, name, w, h):
    if part == 'head':
        if name == 'north':
            return V_HEAD
        if name == 'east':
            return V_HEAD_SIDE
        if name == 'west':
            return [r[::-1] for r in V_HEAD_SIDE]
        return ['H' * w] * h
    if part == 'torso':
        if name == 'north':
            return V_TORSO
        if name in ('up', 'down'):
            return ['B' * w] * h
        return [('b' if name in ('east', 'west') else 'B') * w] * (h - 1) + ['L' * w]
    if part == 'arm':
        if name == 'up':
            return ['B' * w] * h
        if name == 'down':
            return ['L' * w] * h
        return ['B' * w] * 7 + ['K' * w] * 2 + ['L' * w] * 3
    if name == 'up':
        return ['B' * w] * h
    if name == 'down':
        return ['D' * w] * h
    return ['b' * w] + ['B' * w] * 4 + ['b' * w] + ['K' * w] + ['L' * w] * 4 + ['D' * w]


def vanilla_skin():
    """64x64 vanilla-layout skin in the same colours (vanilla hand in first person, HeroSkins)."""
    img = np.zeros((64, 64, 4), np.uint8)
    for origin, size, (u, v), part in VANILLA_BOXES:
        w, h, d = size
        rects = {'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'east': (u + d + w, v + d, d, h),
                 'south': (u + d + w + d, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}
        for name, (x, y, rw, rh) in rects.items():
            keys = vanilla_rows(part, name, rw, rh)
            for r in range(rh):
                for col in range(rw):
                    c = np.array([int(VPAL[keys[r][col]][i:i + 2], 16) for i in (1, 3, 5)], float)
                    shade = 1.0 + (0.06 if name == 'up' else -0.1 if name == 'down' else 0.05 - 0.1 * r / max(1, rh - 1))
                    img[y + r, x + col, :3] = np.clip(c * shade, 0, 255).astype(np.uint8)
                    img[y + r, x + col, 3] = 255
    return img


def build():
    b, g = body_model(), gear_model()
    b.occluders, g.occluders = [g], [b]
    return b, g


def main():
    body, gear = build()
    write_model(body, 'geo/captain/captain.geo.json', 'textures/entity/captain/captain.png')
    write_json(os.path.join(RES, 'geo/captain/captain_arms.geo.json'), arms_only(body, 'captain_arms').geo_json())
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
