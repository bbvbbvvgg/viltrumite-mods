"""Red Hulk (General Thaddeus "Thunderbolt" Ross) — body, first-person arms and the vanilla skin. Own work, CC0.

A new model built from scratch on the shaded pixel-art pipeline (tools/assets/pixel_shade.py: baked occlusion from
the model's own geometry, form light, bevelled edges, hue-shifted tone steps, ordered dither). It shares no
geometry, texture or rig with the Avengers Hulk (tools/assets/avengers_hulk.py): own skeleton with wider shoulder
and hip pivots, a head carried forward and low between the trapezius, a V-shaped torso and gorilla-long arms.

Volume: pectorals (main slab + upper clavicular head + lower rim), six raised abdominal blocks, serratus steps,
obliques, flared lats, trapezius rising to the skull, rhomboid / erector masses on the back, deltoid caps with a top
dome, biceps peak, triceps horseshoe, an outer arm head, elbow, a huge tapering forearm with the brachioradialis
bulge, hands with a knuckle ridge, four separate fingers (one bone, fist curl) and a thumb; quads, calves, feet with
four toes, torn charcoal trousers with a ragged hem, a wide belt with a steel buckle, Ross's dog tags on a chain.

Face (head cubes at 6 px per unit, the body at 3): flat-top black hair, a cranium, two brow bones (left_brow /
right_brow: rage expressions rotate them), a glabella furrow, deep sockets under the overhanging brow, slanted
glowing yellow eyes without pupils, cheekbones, a broad nose with a bridge and wings, an upper-lip bone (lip_upper:
the snarl lifts it), always-bared teeth (upper row on the skull, lower row on the jaw), a dark mouth cavity, and a
massive square jaw with its own chin on the `jaw` bone (hinge near the ears: roar / breathing / rage open it).

Heat: veins and fissures on the forearms, biceps, chest, neck and temples are painted dark ember in the colour
texture and orange in red_hulk_heat.png (drawn with RenderType.eyes, brightness = temperature); the eyes are in
red_hulk_eyes.png (always lit).

Outputs geo/red_hulk/{red_hulk,red_hulk_arms}.geo.json and textures/entity/red_hulk/{red_hulk,red_hulk_eyes,
red_hulk_heat,red_hulk_skin}.png. Run from the repository root with PYTHONHASHSEED=0; check with
tools/assets/geo_lint.py red_hulk red_hulk_arms.
"""
import copy
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from avengers_common import Mat, near, side_x, sname  # noqa: E402
from clean_geo import CleanModel, write_json, write_png, RES  # noqa: E402

D = 3            # body texel density (px per model unit)
DF = 6           # face density
LIGHT = (33.0, 0.0, 0.05)

# ---- palette ------------------------------------------------------------------------------------------
SKIN, SKIN_HI, SKIN_LO, SKIN_DK = '#c22d1f', '#df4a30', '#8f1c18', '#5e0f12'
LINE = '#6a1214'
HAIR, HAIR_HI = '#16131a', '#2c2731'
PANTS, PANTS_HI, PANTS_LO = '#2c2b31', '#3b3a42', '#1b1a1f'
BELT, BELT_HI = '#2a1c16', '#3f2a20'
STEEL, STEEL_HI = '#a9afb9', '#d8dde4'
TOOTH, TOOTH_LO = '#eee4cb', '#b9ac8e'
LIP, MOUTH = '#7e1613', '#250607'
EMBER = '#6e1410'            # vein colour in the base texture (reads as a dark scar when cold)
HEAT, HEAT_CORE = '#ff6d1a', '#ffc457'
EYE, EYE_CORE, EYE_RIM = '#ffd21f', '#fff7b8', '#ff9a12'
SHADOW = (0.74, 0.60, 0.88)  # skin shadows lean to a deep magenta-maroon
WARMLT = (1.08, 1.0, 0.84)   # lights lean orange

HEAT_A, EYE_A = 254, 255     # glow alpha tags: heat layer / eyes layer (split in write_outputs)


class RHModel(CleanModel):
    """CleanModel with a per-cube texel density (the face is painted finer than the body)."""

    def _face_px(self, c, f):
        d = getattr(c, 'density', None) or self.density
        sx, sy, sz = c.size
        w, h = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy),
                'up': (sx, sz), 'down': (sx, sz)}[f]
        return max(1, int(np.ceil(w * d - 1e-6))), max(1, int(np.ceil(h * d - 1e-6)))


# ============================================================================================ painter helpers
def is_outer(ctx):
    return ctx.name in ('east', 'west') and ((ctx.name == 'east') == (float(np.mean(ctx.x)) > 0))


def heat(ctx, mask, core=False):
    m = np.asarray(mask, bool)
    ctx.put(m, EMBER)
    ctx.emit(m, HEAT_CORE if core else HEAT, HEAT_A)


def walk_lines(ctx, n, vertical=True, branch=0.25, wander=0.55, margin=1, length=None):
    """Random-walk vein lines in face pixel space (1 px wide), mostly along v (vertical) or u."""
    h, w = ctx.h, ctx.w
    m = np.zeros((h, w), bool)
    rs = ctx.rs
    major, minor = (h, w) if vertical else (w, h)
    if minor <= 2 * margin + 1 or major < 4:
        return m
    for _ in range(n):
        pos = rs.uniform(margin, minor - 1 - margin)
        start = rs.randint(0, max(1, major // 3))
        steps = length or rs.randint(major // 2, major)
        k = start
        lines = [(pos, k, steps)]
        while lines:
            p, k, steps = lines.pop()
            for _ in range(steps):
                if k >= major:
                    break
                pi = int(round(p))
                if margin <= pi < minor - margin:
                    if vertical:
                        m[k, pi] = True
                    else:
                        m[pi, k] = True
                p += rs.uniform(-wander, wander)
                if rs.uniform() < branch * 0.12 and steps > 4:
                    lines.append((p, k + 1, steps // 2))
                k += 1
    return m


def cracks(ctx, cx, cy, n, r, rs_jitter=0.7):
    """Jagged fissures radiating from a face pixel (cx, cy)."""
    h, w = ctx.h, ctx.w
    m = np.zeros((h, w), bool)
    rs = ctx.rs
    for i in range(n):
        a = rs.uniform(0, 2 * np.pi)
        x, y = float(cx), float(cy)
        for _ in range(int(r)):
            xi, yi = int(round(x)), int(round(y))
            if 0 <= xi < w and 0 <= yi < h:
                m[yi, xi] = True
            a += rs.uniform(-rs_jitter, rs_jitter)
            x += np.cos(a)
            y += np.sin(a)
    return m


def striations(ctx, period=3, amt=0.035, vertical=True):
    """Muscle fibre stripes (soft light / dark alternating columns)."""
    idx = np.arange(ctx.w if vertical else ctx.h)
    s = np.where((idx // max(1, period // 2)) % 2 == 0, amt, -amt * 0.6)
    ctx.L += s[None, :] if vertical else s[:, None]


def crease_h(ctx, y0, dark=-0.12, lit=0.05, mask=None):
    a = near(ctx.y, y0, 0.18)
    b = near(ctx.y, y0 - 0.34, 0.16) & ~a
    if mask is not None:
        a, b = a & mask, b & mask
    ctx.light(a, dark)
    ctx.light(b, lit)


def crease_x(ctx, x0, dark=-0.12, lit=0.04, mask=None):
    a = near(ctx.x, x0, 0.18)
    if mask is not None:
        a = a & mask
    ctx.light(a, dark)


# ============================================================================================ painters
def skin_plain(ctx):
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.05)


def abdomen_fn(ctx):
    if ctx.name == 'north':
        crease_x(ctx, 0.0, -0.14)
    elif ctx.name == 'south':
        crease_x(ctx, 0.0, -0.12)                       # spine groove
        striations(ctx, 4, 0.02, vertical=False)


def ab_fn(ctx):
    if ctx.name == 'north':
        ctx.light((slice(0, 1), slice(None)), 0.06)
        ctx.light((slice(-1, None), slice(None)), -0.08)


def chest_fn(ctx):
    n = ctx.name
    if n == 'north':
        crease_x(ctx, 0.0, -0.12)
    elif n == 'south':
        crease_x(ctx, 0.0, -0.14)                       # spine
        for s in (-1, 1):                               # shoulder blades
            blade = near(ctx.y, 21.2 - 0.5 * np.abs(ctx.x - s * 3.0), 0.2) & (s * ctx.x > 0.8)
            ctx.light(blade, -0.1)
        heat(ctx, walk_lines(ctx, 3, branch=0.5) & (np.abs(ctx.x) > 1.0) & (ctx.y > 20.0))
    elif n == 'up':
        ctx.light(ctx.all(), 0.03)


def pec_fn(ctx):
    if ctx.name == 'north':
        ax = np.abs(ctx.x)
        ctx.light(ctx.y > 23.4, 0.05)                    # upper chest catches the light
        ctx.light(near(ctx.y, 20.0 + 0.18 * ax, 0.22), -0.08)
        striations(ctx, 4, 0.018, vertical=False)
        # glowing fissures over the heart side and the right pec (stronger with heat in game)
        h, w = ctx.h, ctx.w
        cx = int(w * (0.72 if float(np.mean(ctx.x)) > 0 else 0.28))
        heat(ctx, cracks(ctx, cx, int(h * 0.55), 4, h * 0.45))
        heat(ctx, cracks(ctx, cx, int(h * 0.55), 1, 2), core=True)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.12)


def oblique_fn(ctx):
    if ctx.side:
        striations(ctx, 3, 0.03, vertical=False)


def serratus_fn(ctx):
    if ctx.side:
        ctx.light((slice(-1, None), slice(None)), -0.1)


def lat_fn(ctx):
    if ctx.side:
        striations(ctx, 4, 0.025)
    if ctx.name == 'south':
        heat(ctx, walk_lines(ctx, 1, branch=0.2))


def trap_fn(ctx):
    if ctx.name == 'up':
        ctx.light(ctx.all(), 0.05)
        striations(ctx, 3, 0.03)


def neck_fn(ctx):
    if ctx.name == 'north':
        for s in (-1, 1):                               # sternocleidomastoid bands
            band = near(ctx.x, s * (1.6 - 0.35 * (ctx.y - 24.4)), 0.25)
            ctx.light(band, 0.06)
            ctx.light(near(ctx.x, s * (1.15 - 0.35 * (ctx.y - 24.4)), 0.18), -0.08)
        ctx.light(near(ctx.y, 25.0, 0.2) & (np.abs(ctx.x) < 0.8), -0.1)    # throat notch
    if ctx.name in ('east', 'west'):
        heat(ctx, walk_lines(ctx, 1, branch=0.3))


def delt_fn(ctx):
    if ctx.side:
        striations(ctx, 3, 0.03)
        if is_outer(ctx):
            heat(ctx, walk_lines(ctx, 1, branch=0.35, length=ctx.h // 2 + 2))
    elif ctx.name == 'up':
        ctx.light(ctx.all(), 0.05)


def upper_arm_fn(ctx):
    if ctx.side:
        striations(ctx, 4, 0.02)


def bicep_fn(ctx):
    if ctx.name == 'north':
        ctx.light((slice(None), slice(ctx.w // 3, 2 * ctx.w // 3)), 0.05)
        heat(ctx, walk_lines(ctx, 2, branch=0.45))
    elif ctx.side:
        ctx.light(ctx.all(), -0.02)


def tricep_fn(ctx):
    if ctx.name == 'south':
        h = ctx.h
        rr, cc = np.mgrid[0:h, 0:ctx.w]
        horse = (rr > h * 0.45) & (np.abs(cc - ctx.w / 2) < ctx.w * 0.18)
        ctx.light(horse, -0.07)                          # the horseshoe tendon


def elbow_fn(ctx):
    if ctx.name == 'south':
        ctx.light(ctx.all(), -0.04)
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        ctx.light(((rr + cc) % 4 == 0) & (rr > 1), -0.05)  # wrinkled elbow skin


def forearm_fn(ctx):
    if ctx.side:
        striations(ctx, 3, 0.03)
        heat(ctx, walk_lines(ctx, 3 if is_outer(ctx) or ctx.name == 'north' else 1, branch=0.6, wander=0.6))


def wrist_fn(ctx):
    if ctx.side:
        ctx.light((slice(-1, None), slice(None)), -0.06)
        heat(ctx, walk_lines(ctx, 1, branch=0.2))


def hand_fn(ctx):
    if ctx.side:
        ctx.light((slice(-1, None), slice(None)), -0.05)
    if is_outer(ctx):                                    # back of the hand: tendons + a vein
        striations(ctx, 3, 0.03, vertical=True)
        heat(ctx, walk_lines(ctx, 1, branch=0.2))
    elif ctx.name in ('east', 'west'):
        ctx.light(ctx.all(), -0.06)                      # palm
        ctx.light(near(ctx.y, 8.2, 0.16), -0.1)          # palm crease


def knuckle_fn(ctx):
    if ctx.side:
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        ctx.light(rr == 0, 0.08)
        ctx.light((cc % 3 == 2), -0.06)
    if ctx.name == 'down':
        heat(ctx, (np.add.outer(np.arange(ctx.h), np.arange(ctx.w)) % 3 == 0) & (ctx.rs.uniform(0, 1, (ctx.h, ctx.w)) > 0.55))


def finger_fn(ctx):
    if ctx.side:
        ctx.light(near(ctx.y, 6.2, 0.14), -0.1)          # knuckle joint crease
        ctx.light((slice(0, 1), slice(None)), 0.05)
    if ctx.name == 'down':
        ctx.put(ctx.all(), '#d9796a', 0.35)             # nails / finger pads


def thumb_fn(ctx):
    if ctx.side:
        ctx.light((slice(0, 1), slice(None)), 0.05)


def quad_fn(ctx):
    if ctx.name == 'north':
        striations(ctx, 4, 0.02)
        ctx.light(near(ctx.x, float(np.mean(ctx.x)), 0.22), -0.05)


def pants_fn(ctx):
    if ctx.side:
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        ctx.light(((rr * 2 + cc) % 7 == 0), -0.06)       # stretched fabric folds
        if is_outer(ctx):
            ctx.light(near(ctx.z, 0.0, 0.18), -0.1)     # outer seam
    if ctx.name == 'up':
        ctx.light(ctx.all(), -0.05)


def hem_alpha(face, rs):
    """Ragged trouser hem: a jagged lower edge on the side faces."""
    a = np.full((face.h, face.w), 255, np.uint8)
    if face.name in ('north', 'south', 'east', 'west'):
        col = 0
        while col < face.w:
            wdt = rs.randint(1, 4)
            depth = rs.randint(0, max(1, int(face.h * 0.45)))
            if depth:
                a[face.h - depth:, col:col + wdt] = 0
            col += wdt
    elif face.name == 'down':
        a[:] = 0
    return a


def hem_fn(ctx):
    pants_fn(ctx)
    if ctx.side:
        ctx.light((slice(ctx.h // 2, None), slice(None)), -0.06)
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        ctx.put((rr > ctx.h * 0.4) & (ctx.rs.uniform(0, 1, (ctx.h, ctx.w)) > 0.8), PANTS_HI, 0.6)   # frayed threads


def calf_fn(ctx):
    if ctx.name == 'south':
        ctx.light((slice(None), slice(ctx.w // 2 - 1, ctx.w // 2 + 1)), -0.06)
    if is_outer(ctx):
        heat(ctx, walk_lines(ctx, 1, branch=0.2))


def foot_fn(ctx):
    if ctx.name == 'down':
        ctx.put(ctx.all(), SKIN_DK, 0.5)
    if ctx.name == 'up':
        striations(ctx, 3, 0.03, vertical=False)


def toe_fn(ctx):
    if ctx.name == 'north':
        ctx.put((slice(0, 1), slice(None)), '#d9796a', 0.4)


def belt_fn(ctx):
    if ctx.side:
        ctx.light((slice(0, 1), slice(None)), 0.07)
        ctx.light((slice(-1, None), slice(None)), -0.08)
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        ctx.put((rr == 1) & (cc % 2 == 0), BELT_HI, 0.5)  # stitching
        ctx.put((rr == ctx.h - 2) & (cc % 2 == 0), BELT_HI, 0.5)


def buckle_fn(ctx):
    if ctx.name == 'north':
        ctx.light((slice(1, -1), slice(1, -1)), -0.16)
        ctx.light((slice(2, -2), slice(2, -2)), 0.18)


def tag_fn(ctx):
    if ctx.name == 'north':
        ctx.light((slice(1, 2), slice(1, -1)), -0.1)    # stamped lines
        ctx.light((slice(3, 4), slice(1, -2)), -0.1)


# ---- head / face (6 px per unit) ----------------------------------------------------------------------
def cranium_fn(ctx):
    n = ctx.name
    if n in ('east', 'west'):
        hair = ctx.y > 30.6 - 0.15 * (ctx.z + 6.4)       # short sides (high-and-tight)
        ctx.put(hair, HAIR, 0.85)
        heat(ctx, walk_lines(ctx, 1, branch=0.4, length=ctx.h // 2) & (ctx.y < 30.0) & (ctx.z < -3.2))  # temple vein
    elif n == 'south':
        ctx.put(ctx.y > 29.6, HAIR, 0.85)
        ctx.light(near(ctx.y, 28.2, 0.2), -0.08)        # skull base fold
    elif n == 'north':
        ctx.light(ctx.y < 30.4, -0.04)
        # forehead furrows (rage)
        for y0 in (31.0, 31.5):
            ctx.light(near(ctx.y, y0, 0.08) & (np.abs(ctx.x) < 2.4), -0.08)


def hair_fn(ctx):
    if ctx.name == 'up':
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        ctx.light((cc % 3 == 0), -0.06)
        ctx.light((cc % 3 == 1), 0.05)
    elif ctx.side:
        ctx.light((slice(-1, None), slice(None)), -0.08)


def brow_fn(ctx):
    if ctx.name == 'north':
        ctx.light((slice(0, 2), slice(None)), 0.08)      # lit top of the ridge
        ctx.put((slice(ctx.h - 3, ctx.h - 1), slice(1, None)), '#2a0f12', 0.85)   # dark eyebrow
        ctx.light((slice(ctx.h - 1, None), slice(None)), -0.14)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.2)


def glabella_fn(ctx):
    if ctx.name == 'north':
        ctx.light((slice(None), slice(ctx.w // 2 - 1, ctx.w // 2 + 1)), -0.16)   # vertical frown furrow


def socket_fn(ctx):
    """Eye socket front with the slanted glowing eye (inner corner low: an angry eye)."""
    if ctx.name != 'north':
        ctx.put(ctx.all(), SKIN_DK)
        return
    ctx.put(ctx.all(), SKIN_DK)
    ctx.light(ctx.all(), -0.05)
    left = float(np.mean(ctx.x)) > 0
    h, w = ctx.h, ctx.w
    rr, cc = np.mgrid[0:h, 0:w]
    inner = (w - 1 - cc) if left else cc                 # distance from the nose side (0 = inner corner)
    t = inner / max(1, w - 1)                            # 0 inner .. 1 outer
    top = 0.6 + 1.6 * (1 - t) ** 1.2                    # upper lid: drops towards the nose
    bot = h - 1.0 - 0.5 * np.abs(t - 0.55) * 2           # lower lid: gentle curve
    shape = (rr >= top) & (rr <= bot) & (t > 0.04) & (t < 0.96)
    shape &= ~((t > 0.86) & (rr > bot - 1.0))            # tapered outer corner
    ring = shape & ~((rr >= top + 1) & (rr <= bot - 1) & (t > 0.12) & (t < 0.88))
    core = shape & (np.abs(rr - (top + bot) / 2) < 0.9) & (t > 0.25) & (t < 0.7)
    ctx.put(shape, EYE)
    ctx.put(ring, EYE_RIM)
    ctx.put(core, EYE_CORE)
    ctx.emit(shape, EYE, EYE_A)
    ctx.emit(ring, EYE_RIM, EYE_A)
    ctx.emit(core, EYE_CORE, EYE_A)
    ctx.light((slice(0, 1), slice(None)), -0.15)


def cheek_fn(ctx):
    if ctx.name == 'north':
        ctx.light((slice(0, 2), slice(None)), 0.09)
        ctx.light((slice(-1, None), slice(None)), -0.12)
    if ctx.name == 'up':
        ctx.light(ctx.all(), -0.1)


def maxilla_fn(ctx):
    n = ctx.name
    if n == 'north':
        for s in (-1, 1):                                # nasolabial folds
            fold = near(ctx.x, s * (1.5 + 0.55 * (28.6 - ctx.y)), 0.16) & (ctx.y < 28.6)
            ctx.light(fold, -0.14)
    elif n in ('east', 'west'):
        ctx.put(ctx.y > 28.6, HAIR, 0.5)                # sideburn stubble
        ctx.light(near(ctx.z, -2.0, 0.2), -0.06)


def ear_fn(ctx):
    if ctx.side and ctx.w >= 3 and ctx.h >= 3:
        ctx.light((slice(1, -1), slice(1, -1)), -0.15)
        ctx.light((slice(2, -2), slice(2, -2)), 0.06)


def nose_fn(ctx):
    if ctx.name == 'north':
        ctx.light((slice(None), slice(ctx.w // 2 - 1, ctx.w // 2 + 1)), 0.07)   # lit ridge
    elif ctx.name in ('east', 'west'):
        ctx.light(ctx.all(), -0.06)


def nose_tip_fn(ctx):
    if ctx.name == 'north':
        h, w = ctx.h, ctx.w
        ctx.light((slice(0, 2), slice(w // 3, 2 * w // 3)), 0.1)
        ctx.put((slice(h - 2, h), slice(1, w // 3)), '#3c0a0c', 0.9)        # nostrils
        ctx.put((slice(h - 2, h), slice(2 * w // 3, w - 1)), '#3c0a0c', 0.9)
    elif ctx.name == 'down':
        ctx.put(ctx.all(), '#3c0a0c', 0.7)


def lip_fn(ctx):
    ctx.put(ctx.all(), LIP)
    if ctx.name == 'north':
        ctx.light((slice(0, 1), slice(None)), 0.1)
        ctx.light((slice(-1, None), slice(None)), -0.12)
        # snarl: the corners pulled up and back
        ctx.light((slice(None), slice(0, 2)), -0.1)
        ctx.light((slice(None), slice(-2, None)), -0.1)


def teeth_fn(ctx):
    ctx.put(ctx.all(), TOOTH)
    if ctx.name == 'north':
        h, w = ctx.h, ctx.w
        cc = np.arange(w)
        gaps = np.zeros((h, w), bool)
        gaps[:, (cc % 3 == 2)] = True
        ctx.put(gaps, TOOTH_LO, 0.9)
        ctx.light(gaps, -0.1)
        ctx.put((slice(None), slice(0, 1)), '#3a0b0c', 0.8)    # mouth corners
        ctx.put((slice(None), slice(w - 1, w)), '#3a0b0c', 0.8)


def mouth_fn(ctx):
    ctx.put(ctx.all(), MOUTH)


def jaw_fn(ctx):
    n = ctx.name
    if n == 'north':
        ctx.light((slice(0, 1), slice(None)), -0.08)
        for s in (-1, 1):
            ctx.light(near(ctx.x, s * 2.3, 0.18) & (ctx.y < 25.6), -0.08)    # jaw corners
    elif n in ('east', 'west'):
        ctx.light(near(ctx.y, 24.9 + 0.35 * (ctx.z + 6.9), 0.2), -0.1)       # jaw line
        ctx.put(ctx.y > 25.3, HAIR, 0.25)                                    # stubble
    elif n == 'down':
        ctx.light(ctx.all(), -0.14)


def chin_fn(ctx):
    if ctx.name == 'north':
        ctx.light((slice(None), slice(ctx.w // 2 - 1, ctx.w // 2 + 1)), -0.08)   # cleft
        ctx.light((slice(0, 2), slice(None)), 0.06)


def lower_lip_fn(ctx):
    ctx.put(ctx.all(), LIP)
    if ctx.name == 'north':
        ctx.light((slice(0, 1), slice(None)), -0.08)
        ctx.light((slice(1, 2), slice(1, -1)), 0.08)


def crease_fn(ctx):
    ctx.put(ctx.all(), SKIN_DK)
    ctx.light(ctx.all(), -0.06)


def nape_fn(ctx):
    if ctx.name == 'south':
        ctx.put(ctx.y > 29.0, HAIR, 0.85)
        ctx.light(near(ctx.y, 27.2, 0.2), -0.08)
    elif ctx.side:
        ctx.put(ctx.y > 29.6, HAIR, 0.8)


# ============================================================================================ materials
def sk(fn=None, ao=0.55, cyl=None, **kw):
    return Mat(SKIN, 'skin', fn or skin_plain, ao=ao, cool=SHADOW, warm=WARMLT, hue=1.2, quant=0.05, cyl=cyl, **kw)


M = {
    'abdomen': sk(abdomen_fn, cyl=0.12), 'ab': sk(ab_fn, ao=0.7), 'chest': sk(chest_fn, cyl=0.1),
    'pec': sk(pec_fn, ao=0.6), 'pec_hi': sk(None, ao=0.5), 'oblique': sk(oblique_fn), 'serratus': sk(serratus_fn, ao=0.7),
    'lat': sk(lat_fn), 'trap': sk(trap_fn), 'neck': sk(neck_fn, cyl=0.2), 'back': sk(chest_fn),
    'delt': sk(delt_fn, cyl=0.22), 'delt_top': sk(None), 'upper_arm': sk(upper_arm_fn, cyl=0.22),
    'bicep': sk(bicep_fn, cyl=0.2), 'tricep': sk(tricep_fn, cyl=0.2), 'arm_outer': sk(upper_arm_fn),
    'elbow': sk(elbow_fn), 'forearm': sk(forearm_fn, cyl=0.22), 'brachio': sk(forearm_fn, cyl=0.2),
    'wrist': sk(wrist_fn, cyl=0.2), 'hand': sk(hand_fn, cyl=0.12), 'knuckle': sk(knuckle_fn, ao=0.4),
    'finger': sk(finger_fn, ao=0.45), 'thumb': sk(thumb_fn),
    'calf': sk(calf_fn, cyl=0.2), 'foot': sk(foot_fn), 'toe': sk(toe_fn, ao=0.45),
    'pants': Mat(PANTS, 'cloth', pants_fn, ao=0.55, cyl=0.2), 'pants_quad': Mat(PANTS, 'cloth', quad_fn, ao=0.5),
    'hem': Mat(PANTS, 'cloth', hem_fn, ao=0.45, cyl=0.2, alpha_fn=hem_alpha),
    'belt': Mat(BELT, 'leather', belt_fn, ao=0.4), 'buckle': Mat(STEEL, 'metal', buckle_fn, ao=0.2),
    'chain': Mat(STEEL, 'metal', None, ao=0.1, edges=False, micro=0.0), 'tag': Mat(STEEL_HI, 'metal', tag_fn, ao=0.15, edges=False),
    # head
    'cranium': sk(cranium_fn, ao=0.5, cyl=0.1), 'hair': Mat(HAIR, 'hair', hair_fn, ao=0.35, locks='h'),
    'brow': sk(brow_fn, ao=0.35), 'glabella': sk(glabella_fn, ao=0.4), 'socket': sk(socket_fn, ao=0.0, edges=False),
    'cheek': sk(cheek_fn, ao=0.4), 'maxilla': sk(maxilla_fn, ao=0.45), 'ear': sk(ear_fn, ao=0.4),
    'nose': sk(nose_fn, ao=0.35), 'nose_tip': sk(nose_tip_fn, ao=0.35),
    'lip': Mat(LIP, 'skin', lip_fn, ao=0.3, cool=SHADOW), 'lower_lip': Mat(LIP, 'skin', lower_lip_fn, ao=0.3, cool=SHADOW),
    'teeth': Mat(TOOTH, 'paint', teeth_fn, ao=0.25, edges=False, micro=0.0), 'mouth': Mat(MOUTH, 'flat', mouth_fn, ao=0.0),
    'jaw': sk(jaw_fn, ao=0.45), 'crease': sk(crease_fn, ao=0.2, edges=False), 'chin': sk(chin_fn, ao=0.35), 'nape': sk(nape_fn, ao=0.45),
}


# ============================================================================================ skeleton
ARM_X, ARM_Y = 7.4, 23.2          # shoulder pivot
ELBOW = (8.9, 15.6)
WRIST = (8.9, 9.7)
FINGER_Y = 7.1
LEG_X = 2.9
BROW_V = 11


def skeleton(m):
    m.bone('root', None, [0, 0, 0])
    m.bone('waist', 'root', [0, 12, 0])
    m.bone('torso', 'waist', [0, 13, 0])
    m.bone('head', 'torso', [0, 25.6, -2.4])
    m.bone('jaw', 'head', [0, 27.2, -2.6])
    m.bone('lip_upper', 'head', [0, 27.4, -6.9])
    m.bone('left_brow', 'head', [0.3, 30.4, -7.0])
    m.bone('right_brow', 'head', [-0.3, 30.4, -7.0])
    for s in (-1, 1):
        side = sname(s)
        m.bone(f'{side}_arm', 'torso', [s * ARM_X, ARM_Y, 0])
        m.bone(f'{side}_forearm', f'{side}_arm', [s * ELBOW[0], ELBOW[1], 0])
        m.bone(f'{side}_hand', f'{side}_forearm', [s * WRIST[0], WRIST[1], 0])
        m.bone(f'{side}_fingers', f'{side}_hand', [s * 8.9, FINGER_Y, 0])
        m.bone(f'{side}_thumb', f'{side}_hand', [s * 7.2, 8.6, -2.0])
    for s in (-1, 1):
        side = sname(s)
        m.bone(f'{side}_leg', 'root', [s * LEG_X, 12, 0])
        m.bone(f'{side}_shin', f'{side}_leg', [s * LEG_X, 6.2, 0])


def body_model():
    m = RHModel('red_hulk', density=D, width=256, seed=2008, light=LIGHT)
    skeleton(m)

    def c(bone, origin, size, mat, d=None, **kw):
        cube = m.cube(bone, origin, size, M[mat], **kw)
        cube.density = d
        return cube

    # ---- pelvis, belt, trousers top
    c('waist', [-6.0, 9.4, -3.3], [12.0, 3.4, 6.6], 'pants')
    c('waist', [-2.2, 8.6, -3.0], [4.4, 1.0, 6.0], 'pants', faces=('north', 'south', 'east', 'west', 'down'))
    c('waist', [-6.3, 12.6, -3.6], [12.6, 1.7, 7.2], 'belt')
    c('waist', [-1.5, 12.45, -3.95], [3.0, 2.0, 0.35], 'buckle', faces=('north', 'east', 'west', 'up', 'down'))
    # ---- torso: abdomen, six-pack, obliques, serratus, ribcage, pecs, lats, back, traps, neck
    c('torso', [-4.7, 14.0, -2.9], [9.4, 5.4, 5.9], 'abdomen')
    for i, (y0, hgt) in enumerate(((14.3, 1.5), (16.0, 1.45), (17.65, 1.4))):
        for s in (-1, 1):
            c('torso', [side_x(s, 0.18, 2.9 - 0.2 * i), y0, -3.35], [2.9 - 0.2 * i, hgt, 0.45], 'ab',
              faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        c('torso', [side_x(s, 4.7, 0.9), 14.0, -2.3], [0.9, 5.0, 4.6], 'oblique', faces=('north', 'south', 'east', 'west', 'down'))
        for k in range(3):
            c('torso', [side_x(s, 4.3 - 0.25 * k, 1.5), 17.4 + 0.9 * k, -3.05 + 0.05 * k], [1.5, 0.75, 0.6], 'serratus',
              faces=('north', 'east', 'west', 'down'))
    c('torso', [-6.6, 18.9, -3.4], [13.2, 6.3, 7.0], 'chest')
    for s in (-1, 1):
        c('torso', [side_x(s, 0.15, 6.2), 19.3, -4.55], [6.2, 5.2, 1.15], 'pec')
        c('torso', [side_x(s, 0.4, 5.0), 23.6, -4.95], [5.0, 1.15, 0.4], 'pec_hi', faces=('north', 'east', 'west', 'up', 'down'))
        c('torso', [side_x(s, 5.4, 1.9), 15.2, -2.2], [1.9, 8.2, 5.4], 'lat', rotation=[0, 0, s * 9], pivot=[s * 5.4, 23.4, 0],
          faces=('north', 'south', 'east', 'west', 'down'))
        c('torso', [side_x(s, 0.35, 3.0), 13.9, 2.95], [3.0, 5.1, 0.7], 'back', faces=('south', 'east', 'west', 'up', 'down'))
        c('torso', [side_x(s, 1.4, 5.2), 24.6, -1.6], [5.2, 2.6, 4.6], 'trap', rotation=[0, 0, s * 18], pivot=[s * 1.4, 26.2, 0])
    c('torso', [-6.0, 18.6, 3.55], [12.0, 6.2, 0.9], 'back', faces=('south', 'east', 'west', 'up', 'down'))
    c('torso', [-2.7, 24.3, -3.5], [5.4, 3.2, 4.9], 'neck')
    # dog tags on a chain (Ross's general past)
    for s in (-1, 1):
        c('torso', [side_x(s, 0.4, 0.22), 21.6, -4.75], [0.22, 3.3, 0.18], 'chain', rotation=[0, 0, s * 24], pivot=[s * 0.5, 25.0, -4.7],
          faces=('north', 'east', 'west'))
    c('torso', [-1.05, 20.3, -4.82], [1.0, 1.55, 0.14], 'tag', d=6, faces=('north', 'east', 'west', 'up', 'down'))
    c('torso', [-0.25, 20.0, -4.98], [1.0, 1.55, 0.14], 'tag', d=6, rotation=[0, 0, -14], pivot=[0.25, 21.5, -4.9],
      faces=('north', 'east', 'west', 'up', 'down'))

    # ---- arms
    for s in (-1, 1):
        side = sname(s)
        a, f, h = f'{side}_arm', f'{side}_forearm', f'{side}_hand'
        c(a, [side_x(s, 5.9, 5.5), 20.3, -2.9], [5.5, 5.0, 5.8], 'delt')
        c(a, [side_x(s, 6.4, 4.5), 25.3, -2.35], [4.5, 0.8, 4.7], 'delt_top', faces=('north', 'south', 'east', 'west', 'up'))
        c(a, [side_x(s, 6.6, 4.4), 15.4, -2.1], [4.4, 5.0, 4.2], 'upper_arm', faces=('east', 'west', 'down'))
        c(a, [side_x(s, 6.9, 3.8), 16.2, -2.85], [3.8, 4.3, 0.75], 'bicep', faces=('north', 'east', 'west', 'up', 'down'))
        c(a, [side_x(s, 6.7, 4.0), 16.4, 2.1], [4.0, 4.0, 0.75], 'tricep', faces=('south', 'east', 'west', 'down'))
        c(a, [side_x(s, 11.0, 0.55), 16.6, -1.7], [0.55, 3.9, 3.4], 'arm_outer',
          faces=(('east' if s > 0 else 'west'), 'north', 'south', 'down'))
        # forearm (huge, tapering) + elbow + brachioradialis
        c(f, [side_x(s, 6.8, 4.15), 13.9, -1.9], [4.15, 1.9, 4.3], 'elbow', faces=('north', 'south', 'east', 'west', 'up'))
        c(f, [side_x(s, 6.15, 5.5), 11.4, -2.75], [5.5, 3.2, 5.5], 'forearm')
        c(f, [side_x(s, 8.0, 3.2), 11.9, -3.3], [3.2, 2.9, 0.55], 'brachio', faces=('north', 'east', 'west', 'up', 'down'))
        c(f, [side_x(s, 6.6, 4.6), 9.5, -2.3], [4.6, 1.9, 4.6], 'wrist', faces=('north', 'south', 'east', 'west', 'down'))
        # hand: palm block, knuckle ridge, four fingers (one bone), thumb
        c(h, [side_x(s, 7.2, 3.4), 7.1, -2.45], [3.4, 2.4, 4.9], 'hand')
        c(h, [side_x(s, 7.1, 3.6), 7.0, -2.55], [3.6, 0.6, 5.1], 'knuckle', faces=('north', 'south', 'east', 'west', 'down'))
        for i in range(4):
            z0 = -2.45 + i * 1.25
            ln = 2.55 if i in (1, 2) else 2.3
            c(f'{side}_fingers', [side_x(s, 7.4, 2.9), FINGER_Y - ln, z0], [2.9, ln, 1.12], 'finger', d=4)
        c(f'{side}_thumb', [side_x(s, 6.45, 1.55), 6.6, -3.05], [1.55, 2.2, 1.45], 'thumb', d=4)

    # ---- legs: trousers (thigh, quad panel), torn hem, calves, feet, toes
    for s in (-1, 1):
        side = sname(s)
        lg, sh = f'{side}_leg', f'{side}_shin'
        c(lg, [side_x(s, 0.35, 5.3), 6.0, -2.75], [5.3, 6.6, 5.5], 'pants')
        c(lg, [side_x(s, 0.8, 4.3), 7.0, -3.2], [4.3, 4.6, 0.45], 'pants_quad', faces=('north', 'east', 'west', 'up', 'down'))
        c(sh, [side_x(s, 0.55, 4.9), 3.1, -2.5], [4.9, 3.3, 4.9], 'hem', faces=('north', 'south', 'east', 'west'))
        c(sh, [side_x(s, 0.95, 4.1), 1.2, -2.05], [4.1, 3.6, 4.0], 'calf', faces=('north', 'south', 'east', 'west', 'up'))
        c(sh, [side_x(s, 1.35, 3.3), 2.0, 1.95], [3.3, 2.9, 0.6], 'calf', faces=('south', 'east', 'west', 'down', 'up'))
        c(sh, [side_x(s, 0.6, 5.0), 0.0, -4.3], [5.0, 1.45, 6.4], 'foot')
        for i in range(4):
            c(sh, [side_x(s, 0.75 + i * 1.2, 1.05), 0.0, -4.95], [1.05, 0.95, 0.65], 'toe', d=4,
              faces=('north', 'east', 'west', 'up'))

    # ---- head (6 px per unit)
    hd = 'head'
    c(hd, [-3.5, 27.4, -6.4], [7.0, 4.4, 6.0], 'cranium', d=DF)
    c(hd, [-3.6, 31.45, -6.3], [7.2, 1.3, 6.1], 'hair', d=DF, faces=('north', 'south', 'east', 'west', 'up'))
    c(hd, [-3.3, 26.0, -1.4], [6.6, 4.6, 1.5], 'nape', d=DF, faces=('south', 'east', 'west', 'up', 'down'))
    c(hd, [-0.62, 29.55, -7.0], [1.24, 0.95, 0.65], 'glabella', d=DF, faces=('north', 'east', 'west', 'down'))
    # brows: a V (inner ends pulled down) even at rest; the rage expressions rotate the brow bones further
    c('left_brow', [0.3, 29.85, -7.45], [3.1, 1.05, 1.15], 'brow', d=DF, rotation=[0, 0, BROW_V], pivot=[3.4, 30.4, -7.0],
      faces=('north', 'east', 'west', 'up', 'down'))
    c('right_brow', [-3.4, 29.85, -7.45], [3.1, 1.05, 1.15], 'brow', d=DF, rotation=[0, 0, -BROW_V], pivot=[-3.4, 30.4, -7.0],
      faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        c(hd, [side_x(s, 0.5, 2.55), 28.95, -6.55], [2.55, 0.95, 0.15], 'socket', d=DF, faces=('north',))
        c(hd, [side_x(s, 1.35, 2.15), 28.05, -6.95], [2.15, 0.9, 0.55], 'cheek', d=DF, faces=('north', 'east', 'west', 'up', 'down'))
        c(hd, [side_x(s, 3.5, 0.45), 27.7, -3.6], [0.45, 1.6, 1.1], 'ear', d=DF, faces=(('east' if s > 0 else 'west'), 'north', 'south', 'up', 'down'))
    c(hd, [-3.25, 26.3, -6.6], [6.5, 1.75, 5.2], 'maxilla', d=DF, faces=('north', 'east', 'west', 'down'))
    c(hd, [-0.55, 28.25, -7.05], [1.1, 1.35, 0.6], 'nose', d=DF, faces=('north', 'east', 'west'))
    c(hd, [-1.15, 27.55, -7.4], [2.3, 0.85, 0.95], 'nose_tip', d=DF, faces=('north', 'east', 'west', 'up', 'down'))
    c(hd, [-1.75, 26.55, -6.78], [3.5, 0.5, 0.22], 'teeth', d=DF, faces=('north', 'down'))
    c(hd, [-1.6, 25.2, -6.45], [3.2, 1.35, 0.2], 'mouth', d=DF, faces=('north',))
    c('lip_upper', [-2.0, 27.0, -6.98], [4.0, 0.6, 0.45], 'lip', d=DF, faces=('north', 'east', 'west', 'up', 'down'))
    # jaw (hinge near the ears): massive square jaw, chin, lower teeth, lower lip
    c('jaw', [-3.4, 24.35, -6.9], [6.8, 1.95, 5.5], 'jaw', d=DF)
    c('jaw', [-1.85, 24.05, -7.2], [3.7, 1.45, 0.4], 'chin', d=DF, faces=('north', 'east', 'west', 'down'))
    c('jaw', [-1.65, 26.3, -6.75], [3.3, 0.42, 0.2], 'teeth', d=DF, faces=('north', 'up'))
    for s in (-1, 1):   # lower lip in two halves, corners pulled down (a snarl, not a grin)
        c('jaw', [side_x(s, 0.0, 1.95), 25.85, -7.0 - 0.01 * (s + 1)], [1.95, 0.42, 0.25], 'lower_lip', d=DF, rotation=[0, 0, s * 9],
          pivot=[0, 26.06, -7.0], faces=('north', 'east', 'west', 'up', 'down'))
    # canines a little longer than the incisors
    for s in (-1, 1):
        c(hd, [side_x(s, 1.05, 0.42), 26.35, -6.82], [0.42, 0.4, 0.1], 'teeth', d=DF, faces=('north', 'east', 'west', 'down'))
        c('jaw', [side_x(s, 1.0, 0.4), 26.66, -6.8], [0.4, 0.22, 0.08], 'teeth', d=DF, faces=('north', 'east', 'west', 'up'))
        # frown creases from the mouth corners down to the jaw
        c(hd, [side_x(s, 1.95, 0.28), 25.7, -6.62], [0.28, 1.2, 0.12], 'crease', d=DF, rotation=[0, 0, -s * 22],
          pivot=[s * 2.1, 26.9, -6.6], faces=('north', 'east', 'west'))
    return m


# ============================================================================================ outputs
ARM_BONES = ('arm', 'forearm', 'hand', 'fingers', 'thumb')
FP_TOP = {'right_arm': 'armorRightArm', 'left_arm': 'armorLeftArm'}


def arms_only(src, ident):
    """First-person arms: the arm chains of the body (same atlas / UVs), top bones renamed for the FP clips."""
    fp = RHModel(ident, density=src.density, width=src.width)
    fp.height = src.height
    for b in src.bones:
        parts = b.name.split('_', 1)
        if parts[0] not in ('right', 'left') or len(parts) < 2 or parts[1] not in ARM_BONES:
            continue
        nb = fp.bone(FP_TOP.get(b.name, b.name), None if b.name in FP_TOP else FP_TOP.get(b.parent, b.parent), b.pivot, b.rotation)
        nb.cubes = [copy.copy(cb) for cb in b.cubes]
    return fp


VANILLA_BOXES = (
    ([-4, 24, -4], [8, 8, 8], (0, 0), 'head'),
    ([-4, 12, -2], [8, 12, 4], (16, 16), 'torso'),
    ([-8, 12, -2], [4, 12, 4], (40, 16), 'arm'),
    ([4, 12, -2], [4, 12, 4], (32, 48), 'arm'),
    ([-4, 0, -2], [4, 12, 4], (0, 16), 'leg'),
    ([0, 0, -2], [4, 12, 4], (16, 48), 'leg'),
)
V_HEAD = ['HHHHHHHH', 'HsssssssH'[:8], 'BBBssBBB', 'sYYssYYs', 'ssssnsss', 'ssLLLLss', 'sTTTTTTs', 'jjjccjjj']
VPAL = {'H': HAIR, 's': SKIN, 'B': '#2a0f12', 'Y': EYE, 'n': SKIN_LO, 'L': LIP, 'T': TOOTH, 'j': SKIN_LO, 'c': SKIN,
        'S': SKIN, 'P': PANTS, 'K': BELT, 'D': SKIN_DK, 'G': STEEL}


def vanilla_rows(part, name, w, h):
    if part == 'head':
        if name == 'north':
            return V_HEAD
        if name == 'up':
            return ['H' * w] * h
        if name in ('east', 'west', 'south'):
            return ['H' * w] * 2 + ['s' * w] * (h - 2)
        return ['s' * w] * h
    if part == 'torso':
        if name == 'north':
            return ['SSSSSSSS', 'SSSGGSSS', 'SSSGSSSS', 'DSSSSSSD', 'SSDSSDSS', 'SSSDDSSS', 'SDSSSSDS', 'SSSDDSSS', 'SDSSSSDS',
                    'SSSDDSSS', 'KKKGGKKK', 'PPPPPPPP']
        return ['S' * w] * (h - 2) + ['K' * w, 'P' * w]
    if part == 'arm':
        return ['S' * w] * h if name != 'down' else ['D' * w] * h
    if name == 'up':
        return ['P' * w] * h
    if name == 'down':
        return ['D' * w] * h
    return ['P' * w] * 8 + ['S' * w] * 3 + ['D' * w]


def vanilla_skin():
    img = np.zeros((64, 64, 4), np.uint8)
    for origin, size, (u, v), part in VANILLA_BOXES:
        w, h, d = size
        rects = {'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'east': (u + d + w, v + d, d, h),
                 'south': (u + d + w + d, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}
        for name, (x, y, rw, rh) in rects.items():
            keys = vanilla_rows(part, name, rw, rh)
            for r in range(rh):
                for col in range(rw):
                    ch = keys[r][col] if r < len(keys) and col < len(keys[r]) else 's'
                    cc = np.array([int(VPAL[ch][i:i + 2], 16) for i in (1, 3, 5)], float)
                    shade = 1.0 + (0.06 if name == 'up' else -0.1 if name == 'down' else 0.05 - 0.1 * r / max(1, rh - 1))
                    img[y + r, x + col, :3] = np.clip(cc * shade, 0, 255).astype(np.uint8)
                    img[y + r, x + col, 3] = 255
    return img


def split_glow(glow):
    """Glow alpha tags -> (eyes, heat) premultiplied eyes-pass textures."""
    out = []
    for tag in (EYE_A, HEAT_A):
        g = np.zeros_like(glow, dtype=float)
        mask = glow[..., 3] == tag
        g[mask, :3] = glow[mask, :3]
        g[..., 3] = np.where(mask, 255, 0)
        out.append(g.astype(np.uint8))
    return out


def write_outputs(body):
    base, glow = body.paint()
    write_json(os.path.join(RES, 'geo/red_hulk/red_hulk.geo.json'), body.geo_json())
    write_png('textures/entity/red_hulk/red_hulk.png', base)
    eyes, hot = split_glow(glow)
    write_png('textures/entity/red_hulk/red_hulk_eyes.png', eyes)
    write_png('textures/entity/red_hulk/red_hulk_heat.png', hot)
    write_json(os.path.join(RES, 'geo/red_hulk/red_hulk_arms.geo.json'), arms_only(body, 'red_hulk_arms').geo_json())
    write_png('textures/entity/red_hulk/red_hulk_skin.png', vanilla_skin())
    split_b64('textures/entity/red_hulk/red_hulk.png')
    return base


def split_b64(rel, limit=45000):
    """Splits a binasset that would not fit one GitHub push request into <rel>.b64.part0..N at line
    boundaries (joined back by decodeBinaryAssets in viltrumitecore/build.gradle)."""
    import glob
    from jinwoo_geo import BIN
    path = os.path.join(BIN, rel + '.b64')
    for old in glob.glob(path + '.part*'):
        os.remove(old)
    text = open(path).read()
    if len(text) <= limit:
        return
    parts, cur = [], ''
    for line in text.splitlines(keepends=True):
        if len(cur) + len(line) > limit:
            parts.append(cur)
            cur = ''
        cur += line
    parts.append(cur)
    for i, part in enumerate(parts):
        with open('%s.part%d' % (path, i), 'w') as fh:
            fh.write(part)
    os.remove(path)


def main():
    body = body_model()
    write_outputs(body)
    print('red hulk cubes', sum(len(b.cubes) for b in body.bones), 'bones', len(body.bones), 'atlas', (body.width, body.height))


if __name__ == '__main__':
    main()
