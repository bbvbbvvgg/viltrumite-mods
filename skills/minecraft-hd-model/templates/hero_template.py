"""<Hero> — body + gear on the shaded pixel-art pipeline (own work, CC0). Copy to tools/assets/avengers_<kind>.py."""
import numpy as np

from avengers_common import D, Mat, build_body, near, rig, side_x, sname, stitches
from clean_geo import CleanModel

SKIN, SUIT, SUIT_LO, LEATHER, METAL = '#e9c09e', '#2f58b6', '#23438f', '#7f4c28', '#c3cad6'

FACE = [  # 16 x 16, column 0 = viewer's left; keep dark texels few for young faces
    'HHHHHHHHHHHHHHHH', 'HHHHHHHHHHHHHHHH', 'HHHHHHHHHHHHHHHH', 'HHHHHHHHHHHHHHHH',
    'HsssssssssssssH.', 'ssssssssssssssss', 'ssbBBBsllsBBBbss', 'sskIIksssskIIkss',
    'sswipwlsswpiwsss', 'ssssssslnsssssss', 'sssssssnnsssssss', 'ssssssssssssssss',
    'sssssmMMMMmsssss', 'ssssssLLLLssssss', 'ssssssssssssssss', 'ssssssssssssssss',
]
PAL = {'H': ('#6b4a2c', 0.0), 's': (SKIN, 0.0), 'l': (SKIN, 0.07), 'n': (SKIN, -0.13), 'b': ('#8d6640', 0.05),
       'B': ('#8d6640', -0.05), 'k': ('#4a3226', 0.0), 'I': ('#2e5bab', 0.0), 'i': ('#5f9be5', 0.0),
       'p': ('#18223d', 0.0), 'w': ('#efede9', 0.0), 'M': ('#93503f', 0.0), 'm': (SKIN, -0.16), 'L': ('#e0a891', 0.04)}


def head_fn(ctx):
    if ctx.name == 'north':
        ctx.pixels(FACE, PAL)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.1)


def suit_fn(ctx):
    if ctx.name in ('east', 'west'):
        ctx.light(near(ctx.z, 0.0, 0.26), -0.1)            # side seam
        stitches(ctx, near(ctx.z, 0.5, 0.25), '#4a76d2', 2, 0.35)


M = {
    'head': Mat(SKIN, 'skin', head_fn, ao=0.6, micro=0.5), 'neck': Mat(SUIT_LO, 'cloth'),
    'torso': Mat(SUIT, 'cloth', suit_fn), 'arm': Mat(SUIT, 'cloth', suit_fn, cyl=0.22),
    'forearm': Mat(SUIT, 'cloth', cyl=0.22), 'hand': Mat(LEATHER, 'leather'), 'fingers': Mat('#5f371c', 'leather'),
    'leg': Mat(SUIT, 'cloth', suit_fn, cyl=0.22), 'shin': Mat(SUIT, 'cloth', cyl=0.2),
    'belt': Mat(LEATHER, 'leather'), 'buckle': Mat(METAL, 'metal'),
}


def body():
    m = CleanModel('hero', density=D, width=128, seed=1, light=(32.0, 0.0, 0.05))
    rig(m)
    build_body(m, M)
    return m


def gear():
    m = CleanModel('hero_gear', density=D, width=128, seed=2, light=(32.0, 0.0, 0.05))
    rig(m)
    c = m.cube
    c('torso', [-4.3, 11.8, -2.3], [8.6, 1.5, 4.6], M['belt'])
    c('torso', [-1.05, 11.85, -2.6], [2.1, 1.4, 0.3], M['buckle'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        side = sname(s)
        c(f'{side}_forearm', [side_x(s, 3.75, 4.5), 15.2, -2.25], [4.5, 2.6, 4.5], M['belt'])
    return m


def build():
    b, g = body(), gear()
    b.occluders, g.occluders = [g], [b]
    return b, g
