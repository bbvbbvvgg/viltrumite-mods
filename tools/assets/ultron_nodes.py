#!/usr/bin/env python3
"""Ultron network nodes: one geo with the shared tower (octagonal plinth with four anchor claws that bite into the
ground, a ribbed pylon in two sections with red light strips and Ultron's face cast on its front, a collar) and
six kind modules on top, the renderer shows the one the node is: CORE (a red heart in three counter-rotating
gyroscope rings held by four prongs), UPLINK (a mast with a turning dish, antenna crowns and a beacon), FOUNDRY
(a fabricator head with two articulated welding arms over a glowing pad), BULWARK (four shield fins around an
emitter crystal), SENSOR (a giant optic with lids in a turning gimbal) and REPAIR (a turning ring of nozzle pods
and two crane arms). Clips: build (seeked by build progress), idle_<kind>, disabled, boot, collapse (40 ticks),
hit (additive). Also the missile geo (fins, nose light, flame) with its fly clip. Own work, CC0.

Run: python3 tools/assets/ultron_nodes.py [--preview]
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ultron_lib as U  # noqa: E402
from ultron_lib import UClip  # noqa: E402
from ultron_engineer import strut  # noqa: E402

GEO = 'geo/ultron/node.geo.json'
ANIM = 'animations/ultron/node.animation.json'
TEX = 'textures/entity/ultron/node.png'
GLOW = 'textures/entity/ultron/node_glow.png'
MGEO = 'geo/ultron/missile.geo.json'
MANIM = 'animations/ultron/missile.animation.json'
MTEX = 'textures/entity/ultron/missile.png'
MGLOW = 'textures/entity/ultron/missile_glow.png'
KINDS = ('core', 'uplink', 'foundry', 'bulwark', 'sensor', 'repair')
T8 = math.tan(math.radians(22.5))


def octo(b, bone, c, apothem, h, mat, axis='y', jitter=0.01):
    """A regular octagonal slab: four strips turned 0/45/90/135 degrees whose union is exactly the octagon."""
    cx, cy, cz = c
    a, w = apothem, apothem * T8
    for k in range(4):
        e = jitter * k
        if axis == 'y':
            b.rbox(bone, (cx - a, cy - h / 2 - e, cz - w), (cx + a, cy + h / 2 + e, cz + w), mat, (0, 45 * k, 0), (cx, cy, cz))
        elif axis == 'z':
            b.rbox(bone, (cx - a, cy - w, cz - h / 2 - e), (cx + a, cy + w, cz + h / 2 + e), mat, (0, 0, 45 * k), (cx, cy, cz))
        else:
            b.rbox(bone, (cx - h / 2 - e, cy - a, cz - w), (cx + h / 2 + e, cy + a, cz + w), mat, (45 * k, 0, 0), (cx, cy, cz))


def ring(b, bone, c, radius, thick, h, mat, axis_rot=(0, 0, 0), n=8, mat2=None):
    """A ring of n plates around the Y axis through c, the whole ring turned by axis_rot (applied to every plate
    about c, so the bone can spin it)."""
    cx, cy, cz = c
    w = 2 * radius * math.tan(math.pi / n) * 1.04
    for i in range(n):
        th = 360.0 * i / n
        rx, ry, rz = axis_rot
        b.rbox(bone, (cx - w / 2, cy - h / 2, cz - radius - thick / 2), (cx + w / 2, cy + h / 2, cz - radius + thick / 2),
               mat if (mat2 is None or i % 2 == 0) else mat2, (rx, ry + th, rz), (cx, cy, cz))


def build():
    m = U.Model('ultron_node', density=2, width=512, seed=2001)
    b = U.Builder(m, U.mats())

    def pair(bone, a, c, mat, rot=None, pivot=None):
        if rot:
            b.rbox(bone, a, c, mat, rot, pivot)
            pv = None if pivot is None else (-pivot[0], pivot[1], pivot[2])
            b.rbox(bone, (-c[0], a[1], a[2]), (-a[0], c[1], c[2]), mat, (rot[0], -rot[1], -rot[2]), pv)
        else:
            b.box(bone, a, c, mat)
            b.box(bone, (-c[0], a[1], a[2]), (-a[0], c[1], c[2]), mat)

    b.bone('root', None, [0, 0, 0])
    # ---- plinth --------------------------------------------------------------------------------------
    b.bone('plinth', 'root', [0, 0, 0])
    octo(b, 'plinth', (0, 1.6, 0), 15.0, 3.2, 'steel_dark')
    octo(b, 'plinth', (0, 4.3, 0), 12.0, 2.2, 'chrome_dark')
    for k in range(8):
        th = 45 * k + 22.5
        b.rbox('plinth', (-1.6, 3.4, -12.35), (1.6, 3.9, -11.9), 'glow_soft', (0, th, 0), (0, 4.3, 0))
        b.rbox('plinth', (-4.0, 0.4, -15.3), (4.0, 2.6, -14.8), 'steel_bolt' if k % 2 else 'hazard', (0, th - 22.5, 0), (0, 1.6, 0))
    for k in range(4):
        an = 'anchor_%d' % k
        th = 45 + 90 * k
        dx, dz = math.sin(math.radians(th)), -math.cos(math.radians(th))
        p0 = (dx * 10.5, 4.0, dz * 10.5)
        b.bone(an, 'plinth', list(p0))
        strut(b, an, p0, (dx * 16.5, 2.4, dz * 16.5), 2.4, 2.0, 'chrome')
        strut(b, an, (dx * 16.0, 2.6, dz * 16.0), (dx * 18.2, -1.2, dz * 18.2), 1.8, 1.6, 'steel_dark', ext=0.3)
        b.box(an, (p0[0] - 1.2, 3.2, p0[2] - 1.2), (p0[0] + 1.2, 5.4, p0[2] + 1.2), 'gun_lit')
    # ---- pylon (two sections, the upper one topples in the collapse) --------------------------------------
    b.bone('pylon', 'plinth', [0, 5.4, 0])
    octo(b, 'pylon', (0, 15.0, 0), 5.0, 19.2, 'gun')
    for y in (8.0, 14.0, 20.0):
        octo(b, 'pylon', (0, y, 0), 5.7, 1.4, 'chrome' if y != 14.0 else 'steel')
    for k in range(4):
        th = 90 * k + 45
        b.rbox('pylon', (-0.45, 6.0, -5.25), (0.45, 23.8, -4.85), 'glow_soft', (0, th, 0), (0, 15.0, 0))
        b.rbox('pylon', (-1.2, 5.4, -9.0), (1.2, 7.4, -4.6), 'chrome_seam', (0, th - 45, 0), (0, 6.4, 0))      # buttress feet
        b.rbox('pylon', (-0.9, 7.2, -7.6), (0.9, 16.0, -6.0), 'chrome_dark', (-14, th - 45, 0), (0, 11.0, -6.6))
    pair('pylon', (-3.6, 5.6, 4.6), (-2.6, 22.0, 5.6), 'cable')
    b.bone('pylon_top', 'pylon', [0, 24.4, 0])
    octo(b, 'pylon_top', (0, 33.0, 0), 4.6, 17.4, 'gun')
    for y in (26.0, 39.0):
        octo(b, 'pylon_top', (0, y, 0), 5.4, 1.4, 'chrome')
    for k in range(4):
        th = 90 * k + 45
        b.rbox('pylon_top', (-0.4, 27.0, -4.85), (0.4, 38.0, -4.45), 'glow_soft', (0, th, 0), (0, 33.0, 0))
    # Ultron's face cast into the front of the upper pylon
    b.box('pylon_top', (-3.2, 28.0, -5.6), (3.2, 36.4, -4.2), 'chrome_light')
    b.rbox('pylon_top', (-3.5, 34.0, -6.2), (3.5, 35.6, -4.6), 'chrome', (-10, 0, 0))
    pair('pylon_top', (-2.8, 32.6, -5.95), (-0.5, 33.5, -5.5), 'eye', (0, 0, -12), (-1.6, 33.0, -5.7))
    b.box('pylon_top', (-1.9, 29.0, -5.9), (1.9, 29.8, -5.5), 'mouth')
    pair('pylon_top', (-3.4, 28.2, -5.8), (-2.2, 32.0, -4.8), 'steel', (0, -16, 6), (-2.8, 30.0, -5.2))
    octo(b, 'pylon_top', (0, 42.0, 0), 7.4, 3.0, 'chrome_dark')                                    # collar
    for k in range(8):
        b.rbox('pylon_top', (-0.8, 41.0, -7.75), (0.8, 43.0, -7.25), 'gun_lit', (0, 45 * k + 22.5, 0), (0, 42.0, 0))
    top = 43.5
    # ---- CORE ------------------------------------------------------------------------------------------
    b.bone('mod_core', 'pylon_top', [0, top, 0])
    for k in range(4):
        th = 45 + 90 * k
        dx, dz = math.sin(math.radians(th)), -math.cos(math.radians(th))
        strut(b, 'mod_core', (dx * 5.5, top, dz * 5.5), (dx * 9.5, top + 10.0, dz * 9.5), 1.8, 1.8, 'chrome_bolt')
        strut(b, 'mod_core', (dx * 9.5, top + 10.0, dz * 9.5), (dx * 5.0, top + 21.0, dz * 5.0), 1.5, 1.5, 'chrome')
        b.box('mod_core', (dx * 5.0 - 0.6, top + 20.4, dz * 5.0 - 0.6), (dx * 5.0 + 0.6, top + 22.0, dz * 5.0 + 0.6), 'core_box')
    b.bone('core_heart', 'mod_core', [0, top + 12.0, 0])
    b.box('core_heart', (-3.2, top + 8.8, -3.2), (3.2, top + 15.2, 3.2), 'core')
    b.rbox('core_heart', (-3.0, top + 9.0, -3.0), (3.0, top + 15.0, 3.0), 'core_box', (45, 45, 0), (0, top + 12.0, 0))
    for name, rad, rot in (('ring_a', 8.0, (0, 0, 0)), ('ring_b', 6.8, (90, 0, 0)), ('ring_c', 5.6, (0, 0, 90))):
        b.bone(name, 'mod_core', [0, top + 12.0, 0])
        ring(b, name, (0, top + 12.0, 0), rad, 0.8, 1.2, 'chrome', rot, n=12, mat2='chrome_dark')
    # ---- UPLINK ----------------------------------------------------------------------------------------
    b.bone('mod_uplink', 'pylon_top', [0, top, 0])
    octo(b, 'mod_uplink', (0, top + 11.0, 0), 1.7, 22.0, 'steel')
    for y, r in ((top + 6.0, 6.5), (top + 14.0, 5.0)):
        for k in range(6):
            th = 60 * k
            dx, dz = math.sin(math.radians(th)), -math.cos(math.radians(th))
            strut(b, 'mod_uplink', (dx * 1.5, y, dz * 1.5), (dx * r, y + 3.5, dz * r), 0.6, 0.6, 'chrome_light')
            b.box('mod_uplink', (dx * r - 0.4, y + 3.3, dz * r - 0.4), (dx * r + 0.4, y + 4.1, dz * r + 0.4), 'gun_lit')
    b.bone('dish', 'mod_uplink', [0, top + 18.0, 0])
    b.box('dish', (-1.0, top + 17.0, -1.0), (1.0, top + 19.0, 3.0), 'gun')
    octo(b, 'dish', (0, top + 18.0, -2.0), 5.0, 0.8, 'chrome_light', axis='z')
    octo(b, 'dish', (0, top + 18.0, -2.6), 3.2, 0.6, 'chrome_dark', axis='z')
    b.box('dish', (-0.3, top + 17.7, -6.6), (0.3, top + 18.3, -2.6), 'steel')
    b.box('dish', (-0.6, top + 17.4, -7.2), (0.6, top + 18.6, -6.4), 'core_box')
    b.bone('beacon', 'mod_uplink', [0, top + 22.0, 0])
    b.box('beacon', (-1.0, top + 22.0, -1.0), (1.0, top + 24.0, 1.0), 'core')
    b.box('beacon', (-1.3, top + 21.6, -1.3), (1.3, top + 22.2, 1.3), 'chrome')
    # ---- FOUNDRY ----------------------------------------------------------------------------------------
    b.bone('mod_foundry', 'pylon_top', [0, top, 0])
    b.box('mod_foundry', (-9.0, top, -6.0), (9.0, top + 7.0, 6.0), 'chrome_seam')
    b.box('mod_foundry', (-9.6, top + 6.4, -6.6), (9.6, top + 7.8, 6.6), 'chrome_dark')
    b.box('mod_foundry', (-8.4, top + 1.0, -6.4), (8.4, top + 3.0, -6.0), 'vent_glow')
    for x in (-5.5, 5.5):
        octo(b, 'mod_foundry', (x, top + 11.0, 3.0), 1.6, 7.0, 'gun_light')
        b.box('mod_foundry', (x - 1.1, top + 14.3, 1.9), (x + 1.1, top + 14.9, 4.1), 'vent_glow')
    b.box('mod_foundry', (-6.0, -38.0 + top, -15.0), (6.0, -37.6 + top, -9.0), 'glow_soft')         # fabrication pad on the plinth
    b.box('mod_foundry', (-6.6, -38.4 + top, -15.6), (6.6, -37.9 + top, -8.4), 'gun')
    b.bone('farm_r', 'mod_foundry', [-7.0, top + 2.0, -5.0])
    b.oct('farm_r', (-7.0, top + 2.0, -5.0), 1.2, 1.6, 'x', 'gun')
    strut(b, 'farm_r', (-7.0, top + 2.0, -5.0), (-7.0, top - 6.0, -12.0), 1.6, 1.6, 'hazard')
    b.bone('ffore_r', 'farm_r', [-7.0, top - 6.0, -12.0])
    b.oct('ffore_r', (-7.0, top - 6.0, -12.0), 1.0, 1.8, 'x', 'gun_light')
    strut(b, 'ffore_r', (-7.0, top - 6.0, -12.0), (-5.0, top - 16.0, -12.5), 1.2, 1.2, 'chrome')
    b.box('ffore_r', (-5.6, top - 17.6, -13.1), (-4.4, top - 16.0, -11.9), 'gun')
    b.box('ffore_r', (-5.3, top - 18.6, -12.8), (-4.7, top - 17.4, -12.2), 'amber')
    # ---- BULWARK ----------------------------------------------------------------------------------------
    b.bone('mod_bulwark', 'pylon_top', [0, top, 0])
    octo(b, 'mod_bulwark', (0, top + 2.0, 0), 5.0, 4.0, 'chrome_bolt')
    b.bone('crystal', 'mod_bulwark', [0, top + 11.0, 0])
    b.rbox('crystal', (-2.4, top + 8.6, -2.4), (2.4, top + 13.4, 2.4), 'core_box', (45, 0, 45), (0, top + 11.0, 0))
    b.rbox('crystal', (-1.8, top + 9.2, -1.8), (1.8, top + 12.8, 1.8), 'core', (0, 45, 0), (0, top + 11.0, 0))
    for k in range(4):
        fn = 'fin_%d' % k
        th = 90 * k
        b.bone(fn, 'mod_bulwark', [0, top + 3.0, 0])
        b.rbox(fn, (-3.2, top + 3.0, -6.6), (3.2, top + 19.0, -5.4), 'chrome', (-12, th, 0), (0, top + 3.0, 0))
        b.rbox(fn, (-3.6, top + 3.0, -6.9), (-2.8, top + 19.5, -5.2), 'glow_soft', (-12, th, 0), (0, top + 3.0, 0))
        b.rbox(fn, (2.8, top + 3.0, -6.9), (3.6, top + 19.5, -5.2), 'glow_soft', (-12, th, 0), (0, top + 3.0, 0))
        b.rbox(fn, (-2.0, top + 5.0, -6.8), (2.0, top + 9.0, -6.4), 'steel_bolt', (-12, th, 0), (0, top + 3.0, 0))
    # ---- SENSOR -----------------------------------------------------------------------------------------
    b.bone('mod_sensor', 'pylon_top', [0, top, 0])
    octo(b, 'mod_sensor', (0, top + 1.5, 0), 4.6, 3.0, 'chrome_dark')
    b.bone('gimbal', 'mod_sensor', [0, top + 3.0, 0])
    b.box('gimbal', (-1.6, top + 3.0, -1.6), (1.6, top + 5.0, 1.6), 'gun')
    pair('gimbal', (-7.4, top + 4.0, -1.2), (-6.0, top + 15.0, 1.2), 'chrome_bolt')
    b.box('gimbal', (-7.4, top + 3.6, -1.2), (7.4, top + 5.0, 1.2), 'chrome')
    b.bone('eyeball', 'gimbal', [0, top + 11.0, 0])
    b.box('eyeball', (-5.6, top + 6.0, -4.6), (5.6, top + 16.0, 4.0), 'chrome')
    pair('eyeball', (-6.0, top + 10.0, -0.8), (-5.4, top + 12.0, 0.8), 'gun')
    b.box('eyeball', (-4.2, top + 7.6, -5.0), (4.2, top + 14.4, -4.5), 'black')
    b.box('eyeball', (-3.4, top + 7.8, -5.2), (3.4, top + 14.2, -4.9), 'lens')
    b.box('eyeball', (-0.4, top + 16.0, -2.0), (0.4, top + 19.0, -1.2), 'chrome_light')
    for name, y0, y1, piv in (('lid_top', top + 11.2, top + 14.8, top + 15.2), ('lid_bot', top + 7.2, top + 10.8, top + 6.8)):
        b.bone(name, 'eyeball', [0, piv, -4.6])
        b.box(name, (-4.6, y0, -5.6), (4.6, y1, -5.1), 'chrome_dark')
    # ---- REPAIR -----------------------------------------------------------------------------------------
    b.bone('mod_repair', 'pylon_top', [0, top, 0])
    octo(b, 'mod_repair', (0, top + 6.0, 0), 2.6, 12.0, 'steel')
    octo(b, 'mod_repair', (0, top + 12.6, 0), 3.4, 1.4, 'chrome')
    b.box('mod_repair', (-1.1, top + 13.2, -1.1), (1.1, top + 16.2, 1.1), 'amber')
    b.bone('repair_ring', 'mod_repair', [0, top + 5.0, 0])
    ring(b, 'repair_ring', (0, top + 5.0, 0), 7.0, 0.8, 1.6, 'chrome_dark', n=10)
    for k in range(4):
        th = 90 * k + 45
        dx, dz = math.sin(math.radians(th)), -math.cos(math.radians(th))
        b.box('repair_ring', (dx * 7.0 - 1.2, top + 3.6, dz * 7.0 - 1.2), (dx * 7.0 + 1.2, top + 6.4, dz * 7.0 + 1.2), 'gun')
        b.box('repair_ring', (dx * 7.0 - 0.6, top + 2.6, dz * 7.0 - 0.6), (dx * 7.0 + 0.6, top + 3.7, dz * 7.0 + 0.6), 'amber')
    b.bone('rarm_r', 'mod_repair', [-2.6, top + 9.0, 0])
    strut(b, 'rarm_r', (-2.6, top + 9.0, 0), (-9.0, top + 12.0, -3.0), 1.4, 1.4, 'hazard')
    b.bone('rnoz_r', 'rarm_r', [-9.0, top + 12.0, -3.0])
    strut(b, 'rnoz_r', (-9.0, top + 12.0, -3.0), (-10.0, top + 6.0, -5.0), 1.0, 1.0, 'chrome')
    b.box('rnoz_r', (-10.6, top + 4.8, -5.6), (-9.4, top + 6.2, -4.4), 'amber_slit')
    U.mirror_r(m)
    return m


MODS = {k: 'mod_' + k for k in KINDS}


def base(build=1.0, sink=0.0, tilt=(0, 0, 0), top_tilt=(0, 0, 0), anchors=0.0):
    p = {'root': {'rot': (0.0, 0.0, 0.0), 'pos': (0.0, -sink, 0.0)},
         'pylon': {'rot': tuple(tilt), 'scale': (1.0, max(0.02, build), 1.0)},
         'pylon_top': tuple(top_tilt)}
    for k in range(4):
        p['anchor_%d' % k] = (anchors, 0.0, 0.0)
    return p


def clips():
    out = []
    # build (seeked by build progress): plinth rises, anchors bite, pylon telescopes up, modules unfold
    c = UClip('build', 4.0, review=2.8)
    mods0 = {v: {'rot': (0.0, 0.0, 0.0), 'scale': (0.05, 0.05, 0.05)} for v in MODS.values()}
    mods1 = {v: {'rot': (0.0, 0.0, 0.0), 'scale': (1.0, 1.0, 1.0)} for v in MODS.values()}
    k0 = dict(base(build=0.02, sink=6.0, anchors=-50), **mods0)
    k1 = dict(base(build=0.02, sink=0.0, anchors=-50), **mods0)
    k2 = dict(base(build=0.02, sink=0.0, anchors=12), **mods0)
    k3 = dict(base(build=1.0, sink=0.0, anchors=0), **mods0)
    k4 = dict(base(build=1.0, sink=0.0, anchors=0), **{v: {'rot': (0.0, 0.0, 0.0), 'scale': (1.12, 1.12, 1.12)} for v in MODS.values()})
    k5 = dict(base(), **mods1)
    c.key(0, k0).key(0.9, k1, 'easeOutBack').key(1.25, k2, 'easeInOutSine').key(1.4, dict(base(build=0.02, anchors=0), **mods0), 'easeInOutSine') \
        .key(2.7, k3, 'easeOutBack').key(3.6, k4, 'easeOutBack').key(4.0, k5)
    out.append(c)
    # idles per kind
    c = UClip('idle_core', 4.0, loop=True)
    for i in range(9):
        t = i * 0.5
        f = i / 8
        c.key(t, {'ring_a': (0.0, 360.0 * f, 0.0), 'ring_b': (360.0 * f * 2, 0.0, 0.0), 'ring_c': (0.0, 0.0, -360.0 * f),
                  'core_heart': {'rot': (0.0, 90.0 * f * 4, 0.0), 'pos': (0.0, 0.6 * math.sin(2 * math.pi * f * 2), 0.0),
                                 'scale': tuple([1.0 + 0.06 * math.sin(2 * math.pi * f * 4)] * 3)}})
    c.mode = 'linear'
    out.append(c)
    c = UClip('idle_uplink', 4.0, loop=True)
    for i in range(9):
        t = i * 0.5
        f = i / 8
        c.key(t, {'dish': (6 * math.sin(2 * math.pi * f * 2), 360.0 * f, 0.0),
                  'beacon': {'rot': (0.0, 0.0, 0.0), 'scale': tuple([1.0 + 0.15 * (i % 2)] * 3)}})
    c.mode = 'linear'
    out.append(c)
    c = UClip('idle_foundry', 3.0, loop=True)
    for i, (a, b2, a2, b3) in enumerate(((0, 0, 0, 0), (-14, 20, 10, -18), (-6, 30, -8, 24), (-18, 10, 14, 6), (0, 0, 0, 0))):
        c.key(i * 0.75, {'farm_r': (a, 6 * math.sin(i), 0.0), 'ffore_r': (b2, 0.0, 0.0), 'farm_l': (a2, -6 * math.sin(i), 0.0), 'ffore_l': (b3, 0.0, 0.0)})
    out.append(c)
    c = UClip('idle_bulwark', 2.4, loop=True)
    for i in range(5):
        t = i * 0.6
        f = i / 4
        q = {'crystal': {'rot': (0.0, 360.0 * f, 0.0), 'pos': (0.0, 0.5 * math.sin(2 * math.pi * f), 0.0)}}
        for k in range(4):
            q['fin_%d' % k] = (-4 - 3 * math.sin(2 * math.pi * f + k * 0.8), 0.0, 0.0)
        c.key(t, q)
    out.append(c)
    c = UClip('idle_sensor', 6.0, loop=True)
    for i, (yaw, pitch, lids) in enumerate(((0, 0, 0), (70, -8, 0), (70, -8, 30), (70, -8, 0), (-60, 6, 0), (-60, 10, 0),
                                            (-20, -14, 0), (0, 0, 0))):
        t = 6.0 * i / 7
        c.key(t, {'gimbal': (0.0, yaw, 0.0), 'eyeball': (pitch, 0.0, 0.0), 'lid_top': (-lids, 0.0, 0.0), 'lid_bot': (lids, 0.0, 0.0)},
              'easeInOutSine')
    out.append(c)
    c = UClip('idle_repair', 4.0, loop=True)
    for i in range(9):
        t = i * 0.5
        f = i / 8
        c.key(t, {'repair_ring': (0.0, -360.0 * f, 0.0), 'rarm_r': (8 * math.sin(2 * math.pi * f), 10 * math.sin(2 * math.pi * f * 2), 0.0),
                  'rarm_l': (8 * math.sin(2 * math.pi * f + 1.5), -10 * math.sin(2 * math.pi * f * 2 + 1.0), 0.0),
                  'rnoz_r': (14 * math.sin(2 * math.pi * f * 2), 0.0, 0.0), 'rnoz_l': (14 * math.sin(2 * math.pi * f * 2 + 2), 0.0, 0.0)})
    c.mode = 'linear'
    out.append(c)
    # disabled (hacked / unpowered): the upper pylon sags, modules droop, slow broken twitch
    sag = {'pylon_top': (6.0, 0.0, 4.0), 'ring_a': (20.0, 0.0, 10.0), 'ring_b': (70.0, 0.0, 0.0), 'ring_c': (0.0, 0.0, 60.0),
           'dish': (30.0, 40.0, 0.0), 'farm_r': (30.0, 0.0, 0.0), 'ffore_r': (40.0, 0.0, 0.0), 'farm_l': (24.0, 0.0, 0.0), 'ffore_l': (46.0, 0.0, 0.0),
           'fin_0': (14.0, 0.0, 0.0), 'fin_1': (18.0, 0.0, 0.0), 'fin_2': (12.0, 0.0, 0.0), 'fin_3': (20.0, 0.0, 0.0),
           'eyeball': (30.0, 0.0, 0.0), 'lid_top': (-40.0, 0.0, 0.0), 'lid_bot': (40.0, 0.0, 0.0), 'rarm_r': (30.0, 0.0, 20.0), 'rarm_l': (30.0, 0.0, -20.0),
           'core_heart': {'rot': (0.0, 0.0, 0.0), 'pos': (0.0, -2.0, 0.0), 'scale': (0.85, 0.85, 0.85)}}
    c = UClip('disabled', 2.0, loop=True)
    c.key(0, sag).key(0.9, sag).key(1.0, U.add(sag, {'pylon_top': (1.5, 0.0, -1.0), 'eyeball': (-6.0, 0.0, 0.0), 'dish': (-4.0, 0.0, 0.0)})) \
        .key(1.1, sag).key(2.0, sag)
    out.append(c)
    # boot (30 ticks): jolts upright from the sag, everything snaps back into place
    c = UClip('boot', 1.5, review=0.5)
    zero = {k: ((0.0, 0.0, 0.0) if not isinstance(v, dict) else {'rot': (0.0, 0.0, 0.0), 'pos': (0.0, 0.0, 0.0), 'scale': (1.0, 1.0, 1.0)})
            for k, v in sag.items()}
    c.key(0, sag).key(0.35, U.add(sag, {'pylon_top': (-4.0, 0.0, -3.0)}), 'easeInCubic').key(0.6, dict(zero, pylon_top=(-3.0, 0.0, 0.0)), 'easeOutBack') \
        .key(0.9, dict(zero, pylon_top=(1.0, 0.0, 0.0))).key(1.5, zero)
    out.append(c)
    # collapse (40 ticks): shudders, the upper pylon snaps and topples, the plinth sinks, anchors pop
    c = UClip('collapse', 2.0, review=1.2)
    c.key(0, base(), 'easeOutCubic').key(0.15, base(top_tilt=(4, 0, -3)), 'easeInOutSine').key(0.3, base(top_tilt=(-3, 0, 4))) \
        .key(0.5, base(top_tilt=(8, 0, 2), sink=0.4, tilt=(2, 0, 0)), 'easeInQuad') \
        .key(1.3, base(top_tilt=(78, 0, 12), sink=2.0, tilt=(8, 0, 4), anchors=30), 'easeOutBounce') \
        .key(2.0, base(top_tilt=(84, 0, 14), sink=5.0, tilt=(10, 0, 6), anchors=40), 'easeInQuad')
    out.append(c)
    c = UClip('hit', 0.3)
    c.key(0, {'pylon': (0.0, 0.0, 0.0), 'pylon_top': (0.0, 0.0, 0.0)}).key(0.05, {'pylon': (1.5, 0.0, 1.0), 'pylon_top': (2.5, 0.0, -2.0)}, 'easeOutCubic') \
        .key(0.3, {'pylon': (0.0, 0.0, 0.0), 'pylon_top': (0.0, 0.0, 0.0)})
    out.append(c)
    return out


def build_missile():
    m = U.Model('ultron_missile', density=4, width=128, seed=2101)
    b = U.Builder(m, U.mats())
    b.bone('root', None, [0, 0, 0])
    b.bone('body', 'root', [0, 3.0, 0])
    b.oct('body', (0, 3.0, 0.5), 1.2, 7.0, 'z', 'chrome', 'chrome_dark')
    b.box('body', (-0.8, 2.2, -4.0), (0.8, 3.8, -3.0), 'steel')
    b.box('body', (-0.5, 2.5, -4.6), (0.5, 3.5, -3.9), 'core_box')
    b.box('body', (-1.4, 2.7, -1.6), (1.4, 3.3, -0.8), 'glow_soft')
    for k in range(4):
        b.rbox('body', (-0.15, 3.0, 2.0), (0.15, 5.6, 4.2), 'steel_dark', (0, 0, 45 + 90 * k), (0, 3.0, 3.0))
    b.box('body', (-0.9, 2.1, 4.0), (0.9, 3.9, 4.6), 'gun')
    b.bone('flame', 'body', [0, 3.0, 4.6])
    b.box('flame', (-0.7, 2.3, 4.6), (0.7, 3.7, 8.6), 'flame')
    b.box('flame', (-0.35, 2.65, 8.4), (0.35, 3.35, 10.4), 'flame')
    return m


def missile_clips():
    c = UClip('fly', 0.4, loop=True)
    for i in range(5):
        c.key(i * 0.1, {'body': (0.0, 0.0, 90.0 * i), 'flame': {'rot': (0.0, 0.0, 0.0), 'scale': (1.0, 1.0, 1.0 + 0.25 * (i % 2))}})
    c.mode = 'linear'
    return [c]


def main():
    m = build()
    m.write(GEO, TEX, GLOW)
    n = sum(len(bn.cubes) for bn in m.bones)
    cl = clips()
    size = U.write_anims(ANIM, cl)
    print('node: %d bones, %d cubes, atlas %dx%d; %d clips (%d bytes)' % (len(m.bones), n, m.width, m.height, len(cl), size))
    mm = build_missile()
    mm.write(MGEO, MTEX, MGLOW)
    mc = missile_clips()
    U.write_anims(MANIM, mc)
    print('missile: %d bones, %d cubes' % (len(mm.bones), sum(len(bn.cubes) for bn in mm.bones)))
    U.finalize_assets([GEO, ANIM, MGEO, MANIM], [TEX, GLOW, MTEX, MGLOW])
    if '--preview' in sys.argv:
        U.preview_all('node', GEO, TEX, GLOW, cl)
        U.preview_all('missile', MGEO, MTEX, MGLOW, mc)


if __name__ == '__main__':
    main()
