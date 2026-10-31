"""Hulk (The Avengers, 2012) — own rig and model on the shaded pipeline. Own work, CC0.

Own skeleton (same bone names as the player rig for the shared clip code, plus chest, jaw, thumbs and feet), drawn
x1.15 by the renderer. Volume from separate muscle blocks: pecs with a lower rim, six separate abdominal blocks,
serratus steps, obliques, lats, trapezius, deltoid caps, biceps with a peak, triceps, forearm bulges, quads,
calves; fists with four separate fingers and a thumb; feet with four toes. Head forward of the shoulders: heavy
brow ridge, broad nose, cheekbones, ears, messy dark hair in tufts, the jaw on its own bone with bared teeth. Olive
green skin with cool teal shadows, warm yellow-green lights, muscle definition lines, veins on the forearms and
biceps; torn purple trousers with a ragged hem.
"""
import numpy as np

from avengers_common import Mat, hair_strands, near, side_x, sname
from clean_geo import CleanModel

D = 2
SKIN, SKIN_HI, SKIN_LO, LINE = '#5f8537', '#729a45', '#4a6a2b', '#36531f'
PANTS, PANTS_LO, PANTS_HI = '#58367e', '#3c2158', '#71489c'
HAIR = '#2a2c27'

HULK_FACE = [
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'gggggggggggggggg',
    'gggggggggggggggg',
    'gggggggggggggggg',
    'gDDDDDggggDDDDDg',
    'gdKwIKdggdKIwKdg',
    'gddDDdnggndDDddg',
    'gllgggnlnngggllg',
    'gglggnnlnnnggglg',
    'gdgggnNNNNngggdg',
    'gddggglllllgggdd',
    'gdddgggggggggddd',
    'ggddddgggggddddg',
    'gggggggggggggggg',
]
PAL = {'H': (HAIR, 0.0), 'g': (SKIN, 0.0), 'l': (SKIN, 0.07), 'd': (SKIN, -0.1), 'D': (SKIN, -0.2),
       'K': ('#26341a', 0.0), 'w': ('#dfe6c8', 0.0), 'I': ('#47b02e', 0.0), 'n': (SKIN, -0.13),
       'N': ('#2f4b1b', 0.0)}
JAW_FRONT = [
    'ggddLLLLLLddgg',
    'gdLTTTTTTTTLdg',
    'gdMMMMMMMMMMdg',
    'gdLTTTTTTTTLdg',
    'ggdddddddddddg',
]
JAW_PAL = {'g': (SKIN, 0.0), 'd': (SKIN, -0.1), 'L': ('#3a5622', 0.0), 'T': ('#ece9da', 0.0),
           'M': ('#3b1416', 0.0)}


# ------------------------------------------------------------------------------------------------ painters
def head_fn(ctx):
    if ctx.name == 'north':
        ctx.pixels(HULK_FACE, PAL)
        for r, line in enumerate(HULK_FACE[:ctx.h]):
            for col, ch in enumerate(line[:ctx.w]):
                if ch == 'I':
                    ctx.emit((slice(r, r + 1), slice(col, col + 1)), '#5ae03a', 130)
    elif ctx.name in ('east', 'west'):
        hair_strands(ctx, (ctx.y > 43.4) | ((ctx.y > 41.0) & (ctx.z > -2.0)), HAIR)
        ctx.light(near(ctx.y, 40.0, 0.4) & (ctx.z < -5.5), 0.05)       # cheekbone
    elif ctx.name == 'south':
        hair_strands(ctx, ctx.y > 41.6, HAIR)
    elif ctx.name == 'up':
        hair_strands(ctx, ctx.all(), HAIR)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.12)


def jaw_fn(ctx):
    if ctx.name == 'north':
        ctx.pixels(JAW_FRONT, JAW_PAL)
    elif ctx.name == 'up':
        ctx.put(ctx.all(), '#5a1d22')
        ctx.put((slice(-2, None), slice(None)), '#ece9da')
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.08)


def ab_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.v < 0.3, 0.08)
        ctx.light(ctx.v > 0.75, -0.06)


def pec_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.v < 0.25, 0.08)
        ctx.light(ctx.v > 0.8, -0.1)
        inner = np.abs(ctx.x) < 1.2
        ctx.light(inner, -0.05)


def chest_fn(ctx):
    if ctx.name == 'south':
        ctx.light(near(ctx.x, 0.0, 0.3), -0.1)          # spine groove
        for s in (-1, 1):
            ctx.light(near(ctx.x, s * 4.5, 0.4) & (ctx.y > 30.5), -0.06)   # shoulder blades
    if ctx.name == 'north':
        ctx.light(near(ctx.x, 0.0, 0.3), -0.1)


def torso_fn(ctx):
    if ctx.name == 'south':
        ctx.light(near(ctx.x, 0.0, 0.3), -0.1)
    if ctx.name in ('east', 'west'):
        ctx.light(near(ctx.y, 24.0, 0.3) & (ctx.z < 0), -0.05)


def vein(ctx, x0, slope, mask):
    """A meandering vein: a lit line with a dark shadow texel beside it."""
    xs = x0 + slope * ctx.y + 0.35 * np.sin(ctx.y * 1.7)
    coord = ctx.u * ctx.w
    line = np.abs(coord - xs) < 0.5
    ctx.light(mask & line, 0.08)
    ctx.light(mask & (np.abs(coord - xs - 1.0) < 0.5), -0.06)


def biceps_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.v < 0.35, 0.06)
        vein(ctx, ctx.w * 0.55, 0.0, ctx.v > 0.4)


def forearm_fn(ctx):
    if ctx.side and ctx.w >= 8:
        vein(ctx, ctx.w * 0.3, 0.05, (ctx.y > 15.5) & (ctx.y < 19.5))
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.06)


def muscle_fn(ctx):
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.1)
    if ctx.side:
        ctx.light(ctx.v < 0.2, 0.04)


def delt_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.3, 0.06)
        ctx.light(np.abs(ctx.u - 0.5) < 0.04, -0.06)    # front / side head separation
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.12)


def finger_fn(ctx):
    if ctx.side:
        ctx.light(near(ctx.y, 9.8, 0.4), -0.06)          # middle knuckle crease
        ctx.light(ctx.v < 0.2, 0.06)
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.06)


def toe_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.v < 0.4, 0.06)
        ctx.put(ctx.v > 0.75, '#4a6a2a', 0.5)          # nail line


def pants_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.12, 0.06)
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        ctx.light(((rr + cc) % 7 == 0) & (ctx.v > 0.3), -0.04)        # worn fabric
    if ctx.name in ('east', 'west'):
        ctx.light(near(ctx.z, 0.0, 0.3), -0.08)


def torn_alpha(face, rs):
    """Ragged hem: a deterministic zig-zag of uneven teeth on the side faces, open bottom."""
    a = np.full((face.h, face.w), 255, np.uint8)
    if face.name == 'down':
        return np.zeros_like(a)
    if face.name in ('north', 'south', 'east', 'west'):
        h, w = a.shape
        r = np.random.RandomState(face.w * 31 + face.h * 7 + sum(map(ord, face.name)))
        for col in range(w):
            depth = 1 + (col % 3 == 1) + r.randint(0, 2)
            a[max(0, h - depth):, col] = 0
    return a


def torn_fn(ctx):
    pants_fn(ctx)
    if ctx.side:
        ctx.light(ctx.v > 0.45, -0.06)       # frayed, darker edge above the teeth
        ctx.put(ctx.v > 0.55, PANTS_LO, 0.4)


def brow_fn(ctx):
    """Heavy brow ridge: a furrow of vertical creases between the eyes, lit top edge, shadowed underside."""
    if ctx.side:
        ctx.light(ctx.v > 0.6, -0.1)
    if ctx.name == 'north':
        for x in (-0.55, 0.55):
            ctx.light(near(ctx.x, x, 0.26), -0.14)
        ctx.light(near(ctx.x, 0.0, 0.25), 0.06)
        for s in (-1, 1):
            ctx.light(near(ctx.x, s * 2.6, 0.5) & (ctx.v < 0.5), 0.06)


def hair_fn(ctx):
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.15)


M = {
    'head': Mat(SKIN, 'skin', head_fn, ao=0.5, cool=(0.78, 0.9, 1.1), warm=(1.06, 1.04, 0.86)),
    'jaw': Mat(SKIN, 'skin', jaw_fn, ao=0.45, cool=(0.78, 0.9, 1.1)),
    'nose': Mat(SKIN, 'skin', lambda c: c.light(c.v > 0.6, -0.08) if c.name == 'north' else None),
    'brow': Mat(SKIN_LO, 'skin', brow_fn, cool=(0.78, 0.9, 1.1)),
    'cheek': Mat(SKIN, 'skin'),
    'ear': Mat(SKIN_LO, 'skin'),
    'hair': Mat(HAIR, 'hair', hair_fn, cool=(0.9, 0.95, 1.1), spec=0.2),
    'skin': Mat(SKIN, 'skin', muscle_fn, cool=(0.78, 0.9, 1.1), warm=(1.06, 1.04, 0.86)),
    'torso': Mat(SKIN, 'skin', torso_fn, cool=(0.78, 0.9, 1.1), warm=(1.06, 1.04, 0.86)),
    'chest': Mat(SKIN, 'skin', chest_fn, cool=(0.78, 0.9, 1.1), warm=(1.06, 1.04, 0.86)),
    'skin_lo': Mat(SKIN_LO, 'skin', muscle_fn, cool=(0.78, 0.9, 1.1)),
    'bulge': Mat(SKIN_HI, 'skin', muscle_fn, cool=(0.78, 0.9, 1.1), warm=(1.06, 1.04, 0.86)),
    'delt': Mat(SKIN_HI, 'skin', delt_fn, cool=(0.78, 0.9, 1.1), warm=(1.06, 1.04, 0.86)),
    'biceps': Mat(SKIN_HI, 'skin', biceps_fn, cool=(0.78, 0.9, 1.1), warm=(1.06, 1.04, 0.86)),
    'forearm': Mat(SKIN, 'skin', forearm_fn, cool=(0.78, 0.9, 1.1), warm=(1.06, 1.04, 0.86)),
    'ab': Mat(SKIN, 'skin', ab_fn, ao=0.7, cool=(0.78, 0.9, 1.1), warm=(1.06, 1.04, 0.86)),
    'pec': Mat(SKIN, 'skin', pec_fn, ao=0.6, cool=(0.78, 0.9, 1.1), warm=(1.06, 1.04, 0.86)),
    'finger': Mat(SKIN, 'skin', finger_fn, ao=0.6, cool=(0.78, 0.9, 1.1)),
    'toe': Mat(SKIN_LO, 'skin', toe_fn, ao=0.6, cool=(0.78, 0.9, 1.1)),
    'pants': Mat(PANTS, 'cloth', pants_fn),
    'pants_torn': Mat(PANTS, 'cloth', torn_fn, alpha_fn=torn_alpha),
    'waistband': Mat(PANTS_LO, 'cloth'),
}


for _k, _m in M.items():
    if _m.kind == 'skin':
        _m.cyl, _m.grad = 0.24, 0.2
        _m.ao = max(_m.ao, 0.65)
M['hair'].micro = 1.3


def build_model():
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
    # hips: trousers top and waistband
    c('waist', [-7.6, 15.4, -4.6], [15.2, 4.8, 9.2], M['pants'])
    c('waist', [-7.8, 19.0, -4.8], [15.6, 1.1, 9.6], M['waistband'])
    # abdomen: core, six separate abdominal blocks, obliques, serratus steps, lower lats
    c('torso', [-8.2, 19.9, -4.75], [16.4, 8.6, 9.5], M['torso'])
    for row, y0 in enumerate((20.4, 22.75, 25.1)):
        for s in (-1, 1):
            c('torso', [side_x(s, 0.18, 3.25 - 0.15 * row), y0, -5.35 - 0.05 * row], [3.25 - 0.15 * row, 2.15, 0.65], M['ab'])
    for s in (-1, 1):
        c('torso', [side_x(s, 7.6, 1.2), 20.8, -3.8], [1.2, 6.0, 7.2], M['skin_lo'])        # obliques
        for i in range(3):
            c('torso', [side_x(s, 5.2 + 0.6 * i, 1.7), 25.2 + 0.9 * i, -5.0 - 0.06 * i], [1.7, 0.8, 0.5], M['skin'])
    c('torso', [-7.4, 23.6, 4.7], [14.8, 4.8, 0.7], M['skin'])                               # lower lats
    # chest: huge rib cage, pecs with a lower rim, trapezius, upper back / lats
    c('chest', [-11.0, 28.0, -6.4], [22.0, 9.6, 12.4], M['chest'])
    for s in (-1, 1):
        c('chest', [side_x(s, 0.35, 9.2), 29.6, -7.25], [9.2, 6.2, 0.9], M['pec'])
        c('chest', [side_x(s, 0.7, 8.3), 28.9, -7.0], [8.3, 0.75, 0.7], M['pec'])
    c('chest', [-7.0, 36.8, -3.6], [14.0, 3.2, 8.0], M['skin'])
    c('chest', [-8.6, 29.6, 5.6], [17.2, 7.0, 1.6], M['bulge'])
    # head forward of the shoulders: face, brow ridge, nose, cheekbones, ears, hair tufts; jaw on its own bone
    c('head', [-4.0, 37.2, -7.5], [8.0, 8.4, 8.0], M['head'])
    c('head', [-4.3, 42.0, -8.1], [8.6, 1.3, 1.0], M['brow'])
    c('head', [-0.95, 40.0, -8.0], [1.9, 1.9, 0.55], M['nose'])
    for s in (-1, 1):
        c('head', [side_x(s, 1.9, 2.0), 39.9, -7.8], [2.0, 0.9, 0.35], M['cheek'])
        c('head', [side_x(s, 3.95, 0.6), 39.8, -4.2], [0.6, 1.9, 1.3], M['ear'])
    c('head', [-4.3, 44.6, -7.8], [8.6, 1.6, 8.6], M['hair'])
    c('head', [-4.4, 43.4, -8.25], [8.8, 1.3, 1.0], M['hair'])
    c('head', [-4.25, 40.4, 0.45], [8.5, 4.4, 0.6], M['hair'])
    tufts = ((-3.9, -7.6, 2.4, 0.8, 3.0, -25, 8), (-1.4, -7.9, 2.6, 1.0, 3.2, -30, -4), (1.2, -7.7, 2.5, 0.9, 3.0, -22, -9),
             (-3.0, -4.0, 2.8, 0.6, 3.4, -12, 6), (0.0, -3.6, 3.0, 0.7, 3.6, -10, -5), (-2.0, -0.6, 3.2, 0.5, 3.0, 8, 3))
    for x0, z0, w, hgt, d, rx, rz in tufts:
        c('head', [x0, 46.1, z0], [w, hgt, d], M['hair'], rotation=[rx, 0, rz], pivot=[x0 + w / 2, 46.2, z0 + d / 2],
          faces=('north', 'south', 'east', 'west', 'up'))
    c('jaw', [-3.6, 36.4, -8.0], [7.2, 2.4, 6.0], M['jaw'])
    for s in (-1, 1):
        side = sname(s)
        ax = s * 13.5
        # shoulder, deltoid cap, biceps with a peak, triceps
        c(f'{side}_arm', [ax - 4.3, 25.0, -4.3], [8.6, 10.0, 8.6], M['skin'])
        c(f'{side}_arm', [ax - 5.0, 31.0, -5.0], [10.0, 6.4, 10.0], M['delt'])
        c(f'{side}_arm', [ax - 3.4, 25.8, -5.1], [6.8, 4.8, 0.9], M['biceps'])
        c(f'{side}_arm', [ax - 2.5, 26.5, -5.55], [5.0, 3.3, 0.5], M['biceps'])
        c(f'{side}_arm', [ax - 3.6, 26.2, 4.2], [7.2, 4.8, 0.9], M['skin'])
        # forearm with the big upper muscle, fist with four fingers, thumb
        c(f'{side}_forearm', [ax - 4.0 + s * 0.3, 15.0, -4.2], [8.0, 10.0, 8.4], M['forearm'])
        c(f'{side}_forearm', [ax - 4.5 + s * 0.3, 19.6, -4.7], [9.0, 5.0, 9.4], M['bulge'])
        c(f'{side}_hand', [ax - 3.2 + s * 0.5, 11.0, -3.4], [6.4, 4.2, 6.8], M['skin'])
        for z0 in (-3.25, -1.6, 0.05, 1.7):
            c(f'{side}_fingers', [ax - 3.0 + s * 0.5, 7.6, z0], [6.0, 3.6, 1.55], M['finger'])
        c(f'{side}_thumb', [side_x(s, 10.9, 2.2), 9.8, -4.5], [2.2, 3.6, 2.2], M['skin'])
        # legs: thigh with the quad bulge, torn hem over the calf, bare foot with four toes
        lx = s * 4.3
        c(f'{side}_leg', [lx - 4.0, 8.5, -4.2], [8.0, 9.0, 8.4], M['pants'])
        c(f'{side}_leg', [lx - 3.4, 10.0, -4.7], [6.8, 6.0, 0.6], M['pants'])
        c(f'{side}_shin', [lx - 3.9, 5.4, -4.0], [7.8, 3.2, 8.0], M['pants_torn'])
        c(f'{side}_shin', [lx - 3.4, 2.6, -3.4], [6.8, 6.2, 6.6], M['skin'])
        c(f'{side}_shin', [lx - 3.0, 4.0, 3.15], [6.0, 3.6, 1.0], M['bulge'])
        c(f'{side}_foot', [lx - 3.5, 0.0, -5.4], [7.0, 2.8, 8.2], M['skin_lo'])
        for i, x0 in enumerate((-3.45, -1.7, 0.05, 1.8)):
            c(f'{side}_foot', [lx + x0, 0.0, -6.5 + 0.1 * (i % 2)], [1.6, 1.5, 1.1 - 0.1 * (i % 2)], M['toe'])
    return m


def build():
    return [('hulk', build_model())]
