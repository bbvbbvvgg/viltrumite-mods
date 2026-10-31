#!/usr/bin/env python3
"""Chitauri Leviathan: three geo models drawn by one renderer (ChitauriLeviathanRenderer):
  head    - armoured skull with crest plates, long snout, hinged lower jaw with teeth and tusks, glowing eyes,
            cheek armour, gill fins and bio-mechanical rods (bones skull, jaw, crest, eye_l/r, fin_l/r)
  segment - one body ring: dorsal scale plates, ribbed belly, side fins, dorsal spikes, metal collar with
            lights (bones body, fin_l/r, spikes); drawn ten times, scaled by LeviathanTrail.SCALE
  tail    - tapering tail root and a swept horizontal fluke (bones tail, fluke)
Each has its own atlas + glow (keeps every PNG small). Units are real model pixels (1/16 block), front -Z,
origin at the piece centre (head: entity centre = position + CENTRE_Y). Clips: head idle / bite / roar / hurt /
dead, segment swim / dead (played with a per-segment phase offset), tail swim / dead. Own work, CC0.

Run: python3 tools/assets/chitauri_leviathan.py [--preview]
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import chitauri_lib as L  # noqa: E402
from chitauri_lib import Model, Mat, Clip, write_anims  # noqa: E402

OUT = {
    'head': ('geo/chitauri/leviathan_head.geo.json', 'textures/entity/chitauri/leviathan_head.png',
             'textures/entity/chitauri/leviathan_head_glow.png'),
    'segment': ('geo/chitauri/leviathan_segment.geo.json', 'textures/entity/chitauri/leviathan_segment.png',
                'textures/entity/chitauri/leviathan_segment_glow.png'),
    'tail': ('geo/chitauri/leviathan_tail.geo.json', 'textures/entity/chitauri/leviathan_tail.png',
             'textures/entity/chitauri/leviathan_tail_glow.png'),
}
ANIM = 'animations/chitauri/leviathan.animation.json'

HIDE = '#5a544c'
BELLY = '#8b8273'
PLATE = '#4c463e'
PLATE_LIGHT = '#6e6558'
GUM = '#4a2622'


def plate_fn(face, rgb, glow, rs):
    """Heavy scale plate: darker trailing edge, worn ridges, pits."""
    h, w = rgb.shape[:2]
    u, v = face.grid()
    if face.name not in ('up', 'down'):
        rgb *= (1.08 - 0.25 * v)[..., None]
    else:
        rgb *= (1.06 - 0.2 * v)[..., None]
    for _ in range(max(1, (w * h) // 90)):
        x, y = rs.randint(0, w), rs.randint(0, h)
        rgb[y, x] *= 0.6
    if w >= 6:
        for _ in range(rs.randint(1, 3)):
            x = rs.randint(1, w - 1)
            rgb[:, x] *= 0.82
    L._scratches(face, rgb, rs, 2, 1.25)


def hide_fn(face, rgb, glow, rs):
    """Thick hide: deep horizontal folds, mottled patches."""
    h, w = rgb.shape[:2]
    for _ in range(max(1, h // 2)):
        y = rs.randint(0, h)
        x0 = rs.randint(0, max(1, w))
        ln = rs.randint(1, max(2, w // 2 + 1))
        rgb[y, x0:x0 + ln] *= 0.74
    blot = rs.uniform(0.86, 1.1, (max(1, h // 4 + 1), max(1, w // 4 + 1)))
    rgb *= np.kron(blot, np.ones((4, 4)))[:h, :w, None]


def belly_fn(face, rgb, glow, rs):
    """Ribbed belly: bands across the length (along v for side faces, along the long axis for caps)."""
    h, w = rgb.shape[:2]
    if face.name in ('up', 'down'):
        for y in range(h):
            if y % 3 == 0:
                rgb[y, :] *= 0.72
    else:
        for x in range(w):
            if x % 3 == 0:
                rgb[:, x] *= 0.78


def tooth_fn(face, rgb, glow, rs):
    u, v = face.grid()
    rgb *= (1.1 - 0.35 * v)[..., None]


def eye_fn(face, rgb, glow, rs):
    u, v = face.grid()
    d = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2) * 2
    col = np.array([255, 220, 150], float)[None, None, :] * (1 - d[..., None]) + np.array([255, 120, 40], float) * d[..., None]
    rgb[...] = col * 0.5
    if glow is not None:
        glow[..., :3] = col
        glow[..., 3] = np.clip(255 * (1.3 - d), 0, 255)


def mats():
    m = L.mats()
    m.update({
        'hide': Mat(HIDE, noise=8, edge=0.12, fn=hide_fn, grain=2),
        'hide_dark': Mat('#403b35', noise=6, edge=0.12, fn=hide_fn, grain=2),
        'belly': Mat(BELLY, noise=6, edge=0.14, fn=belly_fn),
        'plate': Mat(PLATE, noise=7, edge=0.3, top=1.18, bottom=0.66, fn=plate_fn),
        'plate_light': Mat(PLATE_LIGHT, noise=7, edge=0.3, top=1.15, bottom=0.68, fn=plate_fn),
        'tooth': Mat('#cfc5ad', noise=8, edge=0.12, fn=tooth_fn),
        'gum': Mat(GUM, noise=8, edge=0.1),
        'leye': Mat('#2a1a10', noise=0, edge=0, fn=eye_fn),
    })
    return m


M = mats()


def mk(name, density):
    m = Model('chitauri_leviathan_' + name, density=density, width=256, seed=hash(name) & 0xffff)

    def box(bone, a, b, mat, **kw):
        x0, y0, z0 = a
        x1, y1, z1 = b
        return m.cube(bone, [min(x0, x1), min(y0, y1), min(z0, z1)], [abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)], M[mat], **kw)

    def pair(bone, a, b, mat, rotation=None, pivot=None):
        """Box on the left (+X) and its mirror on the right."""
        box(bone, a, b, mat, rotation=rotation, pivot=pivot)
        ra = (-b[0], a[1], a[2])
        rb = (-a[0], b[1], b[2])
        rr = None if rotation is None else [rotation[0], -rotation[1], -rotation[2]]
        rp = None if pivot is None else [-pivot[0], pivot[1], pivot[2]]
        box(bone, ra, rb, mat, rotation=rr, pivot=rp)
    return m, box, pair


# ---- head -----------------------------------------------------------------------------------------------
def build_head():
    """Wedge-shaped armoured head: broad at the neck, sloping brow, long tapering snout and jaw."""
    m, box, pair = mk('head', 0.39)
    m.bone('root', None, [0, 0, 0])
    m.bone('skull', 'root', [0, 0, 30])
    # neck collar ring (overlaps the first segment)
    box('skull', (-30, -24, 28), (30, 24, 44), 'hide_dark')
    box('skull', (-28, 22, 26), (28, 30, 42), 'plate', rotation=[8, 0, 0], pivot=[0, 26, 26])
    pair('skull', (27, -16, 28), (34, 16, 42), 'plate')
    # cranium: rear block, front block, sloped armour over the brow, dome
    box('skull', (-34, -16, -6), (34, 28, 30), 'hide')
    box('skull', (-30, -14, -40), (30, 24, -6), 'hide')
    box('skull', (-26, 26, -16), (26, 34, 26), 'plate')                                                     # dome
    box('skull', (-28, 20, -44), (28, 28, -4), 'plate_light', rotation=[12, 0, 0], pivot=[0, 26, -4])     # sloping brow plate
    pair('skull', (22, 22, -10), (34, 30, 28), 'plate', rotation=[0, 0, -34], pivot=[28, 26, 0])          # dome bevels
    pair('skull', (20, 16, -42), (30, 24, -8), 'plate', rotation=[10, 0, -30], pivot=[26, 22, -8])
    box('skull', (-28, -30, -30), (28, -14, 30), 'belly')                                                   # throat
    pair('skull', (22, -30, -28), (34, -16, 28), 'belly', rotation=[0, 0, 30], pivot=[28, -22, 0])
    # cheek armour narrowing toward the snout, bio-mechanical rods with lights
    pair('skull', (33, -14, -40), (39, 22, 26), 'plate', rotation=[0, 10, 0], pivot=[36, 0, 26])
    pair('skull', (38, -8, -28), (42, 16, 16), 'plate_light', rotation=[0, 10, 0], pivot=[36, 0, 26])
    pair('skull', (39, -2, -22), (43.5, 1.5, 22), 'alloy', rotation=[0, 10, 0], pivot=[36, 0, 26])
    for z in (-14, 0, 14):
        pair('skull', (43, -1.4, z), (44.2, 1.0, z + 3), 'energy', rotation=[0, 10, 0], pivot=[36, 0, 26])
    # snout (upper jaw): two tapering blocks, plates stepping down toward the nose
    box('skull', (-24, -8, -70), (24, 18, -38), 'hide')
    box('skull', (-19, -7, -99), (19, 13, -68), 'hide')
    box('skull', (-21, 16, -72), (21, 22, -36), 'plate', rotation=[7, 0, 0], pivot=[0, 19, -36])
    box('skull', (-15, 10, -100), (15, 16, -68), 'plate', rotation=[6, 0, 0], pivot=[0, 13, -68])
    box('skull', (-9, 20, -64), (9, 24, -40), 'plate_light', rotation=[7, 0, 0], pivot=[0, 19, -36])     # nasal ridge
    box('skull', (-6, 13, -96), (6, 17, -70), 'plate_light', rotation=[6, 0, 0], pivot=[0, 13, -68])
    pair('skull', (22, -8, -70), (27, 14, -40), 'plate', rotation=[0, 8, 0], pivot=[24, 0, -40])          # snout side plates
    pair('skull', (17, -7, -98), (21, 10, -70), 'plate', rotation=[0, 6, 0], pivot=[19, 0, -70])
    pair('skull', (5, 0, -100), (10, 5, -99), 'hide_dark')                                                  # nostrils
    box('skull', (-21, -10, -96), (21, -8, -38), 'gum')                                                     # palate
    for k in range(9):                                                                                       # upper teeth
        z = -96 + k * 6.4
        w = 19 if z < -68 else 22
        pair('skull', (w - 4, -16, z), (w, -8, z + 2.6), 'tooth', rotation=[8, 0, 0], pivot=[w - 2, -8, z + 1.3])
    for x in (-12, -6, 0, 6, 12):
        box('skull', (x - 1.4, -15, -99), (x + 1.4, -7, -96.5), 'tooth', rotation=[-6, 0, 0], pivot=[x, -7, -97])
    # crest: three plates fanning up toward the back of the dome, with spines
    m.bone('crest', 'skull', [0, 34, 0])
    box('crest', (-17, 32, -14), (17, 37, 6), 'plate_light', rotation=[6, 0, 0], pivot=[0, 33, -14])
    box('crest', (-13, 33, 2), (13, 39, 20), 'plate', rotation=[12, 0, 0], pivot=[0, 33, 2])
    box('crest', (-9, 33, 16), (9, 40, 34), 'plate_light', rotation=[18, 0, 0], pivot=[0, 33, 16])
    for z, h in ((-10, 8), (6, 11), (22, 13)):
        box('crest', (-1.5, 30, z), (1.5, 36 + h, z + 6), 'bronze_dark', rotation=[-26, 0, 0], pivot=[0, 33, z + 3])
    # eyes in armoured sockets
    for side, sx in (('l', 1), ('r', -1)):
        m.bone('eye_' + side, 'skull', [sx * 25.5, 7, -50])
    box('eye_l', (24, 4, -54), (27.4, 10, -46), 'leye')
    box('eye_r', (-27.4, 4, -54), (-24, 10, -46), 'leye')
    pair('skull', (22, 10, -58), (29.5, 14, -42), 'plate_light', rotation=[0, 0, -12], pivot=[25, 12, -50])  # brow over the eye
    pair('skull', (23, 0, -58), (28, 4, -42), 'plate', rotation=[0, 0, 8], pivot=[25, 2, -50])
    # gill fins behind the cheeks
    for side, sx in (('l', 1), ('r', -1)):
        m.bone('fin_' + side, 'skull', [sx * 40, 2, 8])
    for sx, b in ((1, 'fin_l'), (-1, 'fin_r')):
        rot = [0, -24 * sx, -20 * sx]
        piv = [sx * 40, 2, 8]

        def fx(x0, x1):
            return (x0, x1) if sx > 0 else (-x1, -x0)
        a, c = fx(40, 64)
        m.cube(b, [a, 0, -4], [c - a, 3, 26], M['plate'], rotation=rot, pivot=piv)
        a, c = fx(60, 70)
        m.cube(b, [a, 0.4, 0], [c - a, 2.2, 18], M['plate_light'], rotation=rot, pivot=piv)
        for z in (2, 10, 18):
            a, c = fx(42, 62)
            m.cube(b, [a, 3, z], [c - a, 0.8, 1.5], M['bronze_dark'], rotation=rot, pivot=piv)
        a, c = fx(42, 60)
        m.cube(b, [a, 3.0, 6], [c - a, 0.6, 1.2], M['energy_soft'], rotation=rot, pivot=piv)
    # lower jaw with a metal hinge
    m.bone('jaw', 'skull', [0, -10, -30])
    pair('jaw', (22, -18, -36), (30, -6, -24), 'alloy')
    pair('jaw', (29.5, -14, -32), (30.5, -10, -28), 'energy')
    box('jaw', (-23, -30, -66), (23, -10, -28), 'hide')
    box('jaw', (-18, -28, -98), (18, -10, -64), 'hide')
    box('jaw', (-22, -35, -66), (22, -29, -30), 'plate', rotation=[-4, 0, 0], pivot=[0, -32, -30])
    box('jaw', (-17, -32, -96), (17, -27, -64), 'plate', rotation=[-6, 0, 0], pivot=[0, -30, -64])
    box('jaw', (-14, -30, -101), (14, -14, -95), 'plate_light')                                              # chin
    pair('jaw', (22, -28, -64), (26, -12, -32), 'plate', rotation=[0, 6, 0], pivot=[24, -20, -32])
    pair('jaw', (17, -26, -96), (20, -12, -64), 'plate', rotation=[0, 5, 0], pivot=[18, -20, -64])
    box('jaw', (-18, -12, -94), (18, -10, -32), 'gum')                                                       # tongue bed
    box('jaw', (-9, -11, -80), (9, -8, -40), 'gum')                                                          # tongue
    for k in range(9):                                                                                       # lower teeth
        z = -93 + k * 6.4
        w = 17 if z < -64 else 21
        pair('jaw', (w - 4, -10, z), (w, -3, z + 2.6), 'tooth', rotation=[-8, 0, 0], pivot=[w - 2, -10, z + 1.3])
    pair('jaw', (11, -27, -114), (16, -21, -96), 'tooth', rotation=[-22, 6, 0], pivot=[13, -24, -96])          # tusks
    pair('jaw', (12, -27, -98), (17, -20, -90), 'bronze_dark')
    return m


# ---- body segment ---------------------------------------------------------------------------------------------
def build_segment():
    """Octagonal armoured ring; the dorsal scale overhangs backward onto the next segment."""
    m, box, pair = mk('segment', 0.62)
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, 0, 0])
    box('body', (-26, -22, -22), (26, 20, 22), 'hide')
    box('body', (-20, 18, -23), (20, 27, 26), 'plate', rotation=[3, 0, 0], pivot=[0, 22, -23])             # dorsal scale
    box('body', (-13, 26, -20), (13, 30, 20), 'plate_light', rotation=[3, 0, 0], pivot=[0, 22, -23])
    pair('body', (16, 10, -23), (30, 22, 24), 'plate', rotation=[0, 0, -45], pivot=[24, 16, 0])            # upper bevels
    pair('body', (25, -14, -22), (32, 12, 22), 'plate_light')                                               # flank plates
    pair('body', (29, -10, -18), (34, 8, 8), 'plate', rotation=[0, 6, 0], pivot=[32, 0, 8])
    pair('body', (16, -26, -21), (30, -14, 21), 'belly', rotation=[0, 0, 40], pivot=[24, -20, 0])          # lower bevels
    box('body', (-20, -29, -21), (20, -21, 21), 'belly')                                                     # belly
    # metal collar ring at the front with lights
    box('body', (-22, 18, -25), (22, 22, -22), 'alloy')
    box('body', (-18, -27, -25), (18, -22, -22), 'alloy')
    pair('body', (24, -12, -25), (28, 10, -22), 'alloy')
    pair('body', (27.5, -4, -25.6), (29, 4, -22), 'energy')
    box('body', (-3, 21.5, -25.6), (3, 24, -24.6), 'energy_soft')
    pair('body', (10, -27.5, -25.6), (15, -24.5, -24.6), 'energy_soft')
    m.bone('spikes', 'body', [0, 28, 0])
    box('spikes', (-3, 24, -16), (3, 42, -9), 'bronze_dark', rotation=[-30, 0, 0], pivot=[0, 28, -12])
    box('spikes', (-2, 24, 4), (2, 36, 9), 'bronze_dark', rotation=[-30, 0, 0], pivot=[0, 28, 6])
    box('spikes', (-1.2, 40, -14.5), (1.2, 45, -10.5), 'gold_dark', rotation=[-30, 0, 0], pivot=[0, 28, -12])
    for side, sx in (('l', 1), ('r', -1)):
        m.bone('fin_' + side, 'body', [sx * 32, -4, 0])
    for sx, b in ((1, 'fin_l'), (-1, 'fin_r')):
        rot = [0, -16 * sx, -24 * sx]
        piv = [sx * 32, -4, 0]

        def fx(x0, x1):
            return (x0, x1) if sx > 0 else (-x1, -x0)
        a, c = fx(30, 36)
        m.cube(b, [a, -8, -10], [c - a, 8, 18], M['plate'], rotation=rot, pivot=piv)                      # fin root
        a, c = fx(34, 58)
        m.cube(b, [a, -5.5, -14], [c - a, 3, 26], M['plate'], rotation=rot, pivot=piv)
        a, c = fx(56, 62)
        m.cube(b, [a, -5, -10], [c - a, 2, 16], M['plate_light'], rotation=rot, pivot=piv)
        for z in (-10, -2, 6):
            a, c = fx(36, 56)
            m.cube(b, [a, -2.5, z], [c - a, 0.8, 1.6], M['bronze_dark'], rotation=rot, pivot=piv)
        a, c = fx(36, 56)
        m.cube(b, [a, -2.6, -6], [c - a, 0.7, 1.4], M['energy_soft'], rotation=rot, pivot=piv)
    return m


# ---- tail -------------------------------------------------------------------------------------------------
def build_tail():
    """Tapering tail root (same ring language as the segments) and a swept horizontal fluke."""
    m, box, pair = mk('tail', 0.62)
    m.bone('root', None, [0, 0, 0])
    m.bone('tail', 'root', [0, 0, -16])
    box('tail', (-22, -19, -22), (22, 17, 12), 'hide')
    box('tail', (-17, 15, -23), (17, 23, 12), 'plate', rotation=[4, 0, -0], pivot=[0, 19, -23])
    pair('tail', (14, 8, -22), (26, 19, 12), 'plate', rotation=[0, 0, -45], pivot=[20, 13, 0])
    pair('tail', (20, -12, -21), (26, 10, 10), 'plate_light')
    pair('tail', (13, -22, -20), (25, -11, 10), 'belly', rotation=[0, 0, 40], pivot=[20, -17, 0])
    box('tail', (-17, -25, -20), (17, -18, 10), 'belly')
    box('tail', (-15, -13, 10), (15, 11, 34), 'hide')
    box('tail', (-12, 10, 8), (12, 15, 32), 'plate', rotation=[6, 0, 0], pivot=[0, 12, 8])
    pair('tail', (12, -8, 10), (17, 8, 30), 'plate', rotation=[0, -6, 0], pivot=[15, 0, 10])
    box('tail', (-10, -10, 32), (10, 8, 44), 'hide_dark')
    box('tail', (-2, 16, -16), (2, 32, -10), 'bronze_dark', rotation=[-32, 0, 0], pivot=[0, 20, -13])
    box('tail', (-1.5, 10, 12), (1.5, 24, 17), 'bronze_dark', rotation=[-32, 0, 0], pivot=[0, 14, 15])
    box('tail', (-20, 15, -25), (20, 19, -22), 'alloy')
    box('tail', (-15, -24, -25), (15, -20, -22), 'alloy')
    pair('tail', (21, -10, -25), (25, 8, -22), 'alloy')
    pair('tail', (24.5, -4, -25.6), (26, 4, -22), 'energy')
    m.bone('fluke', 'tail', [0, 0, 40])
    box('fluke', (-12, -3, 40), (12, 3, 66), 'plate')
    pair('fluke', (10, -2.5, 42), (44, 2.5, 62), 'plate', rotation=[0, -24, 0], pivot=[10, 0, 44])
    pair('fluke', (40, -2, 50), (50, 2, 64), 'plate_light', rotation=[0, -24, 0], pivot=[10, 0, 44])
    pair('fluke', (12, 2.5, 48), (40, 3.2, 50), 'energy_soft', rotation=[0, -24, 0], pivot=[10, 0, 44])
    for x in (16, 26, 36):
        pair('fluke', (x, 2.5, 44), (x + 1.4, 3.4, 60), 'bronze_dark', rotation=[0, -24, 0], pivot=[10, 0, 44])
    box('fluke', (-4, -3.5, 62), (4, 3.5, 70), 'plate_light')
    return m


def build_clips():
    clips = []
    idle = Clip('head_idle', 3.0, loop=True)
    for i in range(7):
        t = i * 0.5
        w = math.sin(2 * math.pi * t / 3.0)
        idle.key(t, {'jaw': (4 + 3 * w, 0, 0), 'fin_l': (4 * w, 0, -6 * w), 'fin_r': (4 * w, 0, 6 * w),
                     'crest': (1.5 * w, 0, 0), 'skull': (0, 0, 0)})
    clips.append(idle)
    bite = Clip('head_bite', 0.9)
    for t, j, s in ((0.0, 4, 0), (0.3, 40, -8), (0.42, 44, -10), (0.52, 0, 6), (0.62, 2, 3), (0.9, 4, 0)):
        bite.key(t, {'jaw': (j, 0, 0), 'skull': (s, 0, 0)})
    clips.append(bite)
    roar = Clip('head_roar', 2.4)
    keys = [(0.0, 4, 0, 0), (0.25, 48, -12, 0), (0.5, 52, -14, 3), (0.8, 50, -13, -3), (1.1, 53, -14, 3), (1.4, 50, -13, -3),
            (1.7, 52, -12, 2), (2.0, 30, -6, 0), (2.4, 4, 0, 0)]
    for t, j, s, r in keys:
        roar.key(t, {'jaw': (j, 0, 0), 'skull': (s, 0, r), 'crest': (-4 if j > 20 else 0, 0, 0),
                     'fin_l': (0, 0, -18 if j > 20 else 0), 'fin_r': (0, 0, 18 if j > 20 else 0)})
    clips.append(roar)
    hurt = Clip('head_hurt', 0.6)
    for t, j, s, r in ((0.0, 4, 0, 0), (0.08, 22, 10, 8), (0.25, 14, 4, -4), (0.6, 4, 0, 0)):
        hurt.key(t, {'jaw': (j, 0, 0), 'skull': (s, 0, r)})
    clips.append(hurt)
    dead = Clip('head_dead', 1.0, loop=True)
    dead.key(0.0, {'jaw': (30, 0, 4), 'fin_l': (0, 0, 30), 'fin_r': (0, 0, -30), 'crest': (0, 0, 0), 'skull': (0, 0, 0)})
    dead.key(1.0, {'jaw': (30, 0, 4), 'fin_l': (0, 0, 30), 'fin_r': (0, 0, -30), 'crest': (0, 0, 0), 'skull': (0, 0, 0)})
    clips.append(dead)
    swim = Clip('segment_swim', 2.0, loop=True)
    for i in range(9):
        t = i * 0.25
        w = math.sin(2 * math.pi * t / 2.0)
        c = math.cos(2 * math.pi * t / 2.0)
        swim.key(t, {'fin_l': (6 * c, 0, -18 * w), 'fin_r': (6 * c, 0, 18 * w), 'spikes': (3 * w, 0, 0)})
    clips.append(swim)
    sdead = Clip('segment_dead', 1.0, loop=True)
    for t in (0.0, 1.0):
        sdead.key(t, {'fin_l': (0, 0, 34), 'fin_r': (0, 0, -34), 'spikes': (0, 0, 0)})
    clips.append(sdead)
    tswim = Clip('tail_swim', 2.0, loop=True)
    for i in range(9):
        t = i * 0.25
        w = math.sin(2 * math.pi * t / 2.0)
        tswim.key(t, {'fluke': (16 * w, 0, 0), 'tail': (4 * w, 0, 0)})
    clips.append(tswim)
    tdead = Clip('tail_dead', 1.0, loop=True)
    for t in (0.0, 1.0):
        tdead.key(t, {'fluke': (-6, 0, 10), 'tail': (0, 0, 0)})
    clips.append(tdead)
    return clips


def main():
    models = {'head': build_head(), 'segment': build_segment(), 'tail': build_tail()}
    for name, m in models.items():
        geo, tex, glow = OUT[name]
        m.write(geo, tex, glow)
        n = sum(len(b.cubes) for b in m.bones)
        size = os.path.getsize(os.path.join(L.BIN, tex + '.b64'))
        print('leviathan %s: %d bones, %d cubes, atlas %dx%d, b64 %d bytes' % (name, len(m.bones), n, m.width, m.height, size))
    size = write_anims(ANIM, build_clips())
    print('leviathan clips: %d bytes' % size)
    if '--preview' in sys.argv:
        import json
        out = os.path.join(L.ROOT, 'build', 'chitauri_preview')
        geos = {}
        for name in models:
            with open(os.path.join(L.RES, OUT[name][0])) as fh:
                geos[name] = json.load(fh)

        def item(name, pose):
            return (geos[name], pose, os.path.join(L.ROOT, 'build', 'jinwoo_preview', OUT[name][1]),
                    os.path.join(L.ROOT, 'build', 'jinwoo_preview', OUT[name][2]))
        L.preview(os.path.join(out, 'leviathan_head.png'), [item('head', {})], views=('front', 'q_front', 'side', 'q_back'), scale=2)
        clips = {c.name: c for c in build_clips()}
        L.preview(os.path.join(out, 'leviathan_head_roar.png'), [item('head', L.sample_clip(clips['head_roar'], 0.5))],
                  views=('q_front', 'side'), scale=2)
        L.preview(os.path.join(out, 'leviathan_segment.png'), [item('segment', {}), ], views=('front', 'q_front', 'side', 'top'), scale=3)
        L.preview(os.path.join(out, 'leviathan_tail.png'), [item('tail', {})], views=('q_front', 'side', 'top'), scale=3)
        # whole body on an S-curve path (same spacing / scales as LeviathanTrail)
        SCALE = [1.0, 0.98, 0.95, 0.9, 0.85, 0.79, 0.72, 0.65, 0.58, 0.51]
        offs = [3.6]
        for k in range(9):
            offs.append(offs[-1] + 2.3 * (SCALE[k] + SCALE[k + 1]) * 0.5)
        offs.append(offs[-1] + 1.5)

        def path(d):  # blocks behind the head -> model px (x, y, z)
            return (60 * math.sin(d / 7.0), 26 * math.sin(d / 5.0), d * 16)
        items = [item('head', {'root': {'rot': [0, 0, 0]}})]
        for k, d in enumerate(offs):
            a, b = path(d - 0.4), path(d + 0.4)
            dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
            yaw = math.degrees(math.atan2(dx, dz))
            pitch = math.degrees(math.atan2(dy, math.hypot(dx, dz)))
            sc = SCALE[k] if k < 10 else 0.46
            name = 'tail' if k == 10 else 'segment'
            px, py, pz = path(d)
            items.append(item(name, {'root': {'pos': [px, py, pz], 'rot': [pitch, -yaw, 0], 'scale': [sc, sc, sc]}}))
        L.preview(os.path.join(out, 'leviathan_chain.png'), items, views=('side', 'q_front', 'top'), scale=0.8)


if __name__ == '__main__':
    main()
