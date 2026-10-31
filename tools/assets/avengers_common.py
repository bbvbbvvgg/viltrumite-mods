"""Shared rig, body builder and painter helpers of the summoned Avengers (own work, CC0).

Every hero body uses the player skeleton (same bone names / pivots as the shared clips) built from split parts:
head, neck, torso, upper arm / forearm / hand / four separate fingers / thumb, thigh / shin. Materials are
pixel_shade.Mat (baked occlusion, form light, hue-shifted tone steps); painters receive a pixel_shade.Ctx.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from pixel_shade import Mat, hexc  # noqa: E402,F401

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


# ------------------------------------------------------------------------------------------------ rig / body
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


FINGER_Z = ((-1.9, 0.8), (-0.95, 0.8), (0.0, 0.8), (0.95, 0.8))


def build_body(m, mats, slim=False, legs=True):
    """Head, neck, torso, split arms (upper / forearm / hand / 4 fingers / thumb) and legs (thigh / shin)."""
    aw = 3 if slim else 4
    c = m.cube
    c('head', [-4, 24, -4], [8, 8, 8], mats['head'])
    c('torso', [-1.6, 23.2, -1.6], [3.2, 1.0, 3.2], mats.get('neck', mats['head']))
    c('torso', [-4, 12, -2], [8, 12, 4], mats['torso'])
    for s in (-1, 1):
        side = sname(s)
        x0 = side_x(s, 4.0, aw)
        c(f'{side}_arm', [x0, 18, -2], [aw, 6, 4], mats['arm'])
        c(f'{side}_forearm', [x0 + 0.1, 15, -1.9], [aw - 0.2, 3, 3.8], mats['forearm'])
        c(f'{side}_hand', [x0, 13, -2], [aw, 2, 4], mats['hand'])
        for z0, dz in FINGER_Z:
            c(f'{side}_fingers', [x0 + 0.15, 12, z0], [aw - 0.3, 1.0, dz], mats.get('fingers', mats['hand']))
        c(f'{side}_thumb', [side_x(s, 3.55, 1.0), 12.7, -2.5], [1.0, 1.7, 1.1], mats.get('fingers', mats['hand']))
        if legs:
            c(f'{side}_leg', [side_x(s, 0.05, 3.95), 6, -2], [3.95, 6, 4], mats['leg'])
            c(f'{side}_shin', [side_x(s, 0.15, 3.75), 0.1, -1.9], [3.75, 5.9, 3.8], mats['shin'])
    return aw


# ------------------------------------------------------------------------------------------------ painters
def near(a, v, half):
    return np.abs(a - v) < half


def seam_h(ctx, y0, dark=-0.12, lit=0.06, colour=None):
    """Horizontal seam on side faces at world y0: a dark texel row with a lit row under it (bevel)."""
    if not ctx.side:
        return
    t = 0.5 / max(1, ctx.h) * (ctx.face.sy if hasattr(ctx.face, 'sy') else 1)
    row = near(ctx.y, y0, 0.26)
    ctx.light(row, dark)
    if colour is not None:
        ctx.put(row, colour, 0.5)
    ctx.light(near(ctx.y, y0 - 0.5, 0.24) & ~row, lit)


def seam_x(ctx, x0, dark=-0.12, lit=0.05):
    """Vertical seam on north / south / up / down faces at world x0."""
    if ctx.name in ('east', 'west'):
        return
    col = near(ctx.x, x0, 0.26)
    ctx.light(col, dark)
    ctx.light(near(ctx.x, x0 + 0.5, 0.24) & ~col, lit)


def seam_z(ctx, z0, dark=-0.12, lit=0.05):
    if ctx.name not in ('east', 'west', 'up', 'down'):
        return
    col = near(ctx.z, z0, 0.26)
    ctx.light(col, dark)
    ctx.light(near(ctx.z, z0 + 0.5, 0.24) & ~col, lit)


def stitches(ctx, mask, colour, period=2, amount=0.7):
    """Dashed stitching along a mask (every other texel)."""
    rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
    dash = ((rr + cc) % period) == 0
    ctx.put(mask & dash, colour, amount)


def rim(ctx, colour, amount=0.6, rows=1):
    """Lighter/darker trim along the top and bottom rows of the side faces."""
    if ctx.side and ctx.h > 2 * rows:
        ctx.put((slice(0, rows), slice(None)), colour, amount)
        ctx.put((slice(-rows, None), slice(None)), colour, amount)


def studs(ctx, colour, every=3, row=None, light=0.12):
    """A row of round studs (1 texel + lit top) along the middle (or given) row of the side faces."""
    if not ctx.side or ctx.w < 3:
        return
    r = ctx.h // 2 if row is None else row
    for col in range(1, ctx.w - 1, every):
        ctx.put((slice(r, r + 1), slice(col, col + 1)), colour)
        ctx.light((slice(r, r + 1), slice(col, col + 1)), light)
        if r + 1 < ctx.h:
            ctx.light((slice(r + 1, r + 2), slice(col, col + 1)), -0.08)


def face_fn(rows, pal, hair=None, top=None, back=None, ear=None, skin_lo=None, extra=None):
    """Head cube painter: pixel face on the front; hair on top / back / upper sides; an ear on the sides."""
    def fn(ctx):
        if ctx.name == 'north':
            ctx.pixels(rows, pal)
        elif ctx.name == 'up':
            if top:
                ctx.put(ctx.all(), top)
        elif ctx.name == 'south':
            if back:
                ctx.put(ctx.all(), back)
        elif ctx.name in ('east', 'west'):
            if hair:
                ctx.put((ctx.y > 29.4) | ((ctx.y > 26.0) & (ctx.z > 1.2)), hair)
                ctx.put((ctx.y > 28.4) & (ctx.y <= 29.4) & (ctx.z > -2.6), hair)
            if ear:
                e = (np.abs(ctx.y - 27.0) < 1.0) & (np.abs(ctx.z - 0.3) < 0.7)
                ctx.put(e, ear)
                ctx.light(e & (ctx.z > 0.3), -0.08)
        elif ctx.name == 'down':
            ctx.light(ctx.all(), -0.1)
        if extra:
            extra(ctx)
    return fn
