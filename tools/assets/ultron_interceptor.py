#!/usr/bin/env python3
"""Ultron flying interceptor: an arrowhead chrome hunter with Ultron's slanted red eyes on the nose, swept wings
with split flaps and red tip lights, wing-root laser cannons, two vectoring engine nacelles (glow flames that the
renderer stretches with thrust), a V-tail and a belly missile bay with doors. Geo (55 cubes), metal atlas +
red glow atlas, and every clip: hover, fly, accel/decel/turn_l/turn_r (additive), burst, missile, dive, boost,
hurt (additive), heavy_hit, death, fabricate. Contact frames match UltronMove (BURST 18 ticks: shots 6 left
gun, 9 right, 12 left; MISSILE 20: drop 10; DIVE 30: pull-up to 14, strike 22; BOOST 12; death 30). CC0.

Run: python3 tools/assets/ultron_interceptor.py [--preview]
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ultron_lib as U  # noqa: E402
from ultron_lib import UClip  # noqa: E402

GEO = 'geo/ultron/interceptor.geo.json'
ANIM = 'animations/ultron/interceptor.animation.json'
TEX = 'textures/entity/ultron/interceptor.png'
GLOW = 'textures/entity/ultron/interceptor_glow.png'
CY = 7.0


def build():
    m = U.Model('ultron_interceptor', density=3, width=256, seed=1801)
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
    b.bone('body', 'root', [0, CY, 0])
    # ---- fuselage: tapering arrowhead ------------------------------------------------------------------
    b.box('body', (-2.8, CY - 1.9, -5.0), (2.8, CY + 1.7, 7.5), 'gun')
    b.box('body', (-3.0, CY - 1.4, -4.0), (3.0, CY + 1.9, 6.0), 'chrome_seam')
    pair('body', (-3.3, CY - 1.2, -9.0), (-0.6, CY + 1.4, -3.4), 'chrome', (0, -16, 0), (-1.6, CY, -5.2))      # cheek panels
    b.box('body', (-2.0, CY - 1.3, -10.0), (2.0, CY + 1.3, -4.6), 'chrome_light')
    b.box('body', (-1.3, CY - 0.9, -13.0), (1.3, CY + 0.9, -9.8), 'chrome')
    b.box('body', (-0.6, CY - 0.5, -15.2), (0.6, CY + 0.5, -12.8), 'chrome_light')                           # needle nose
    b.rbox('body', (-2.1, CY + 0.9, -9.6), (2.1, CY + 2.0, -3.6), 'chrome_light', (-9, 0, 0), (0, CY + 1.4, -6.6))  # brow plate
    pair('body', (-2.0, CY + 0.15, -10.1), (-0.25, CY + 0.85, -9.75), 'black', (0, -14, -14), (-1.1, CY + 0.5, -9.9))
    pair('body', (-1.85, CY + 0.28, -10.25), (-0.4, CY + 0.72, -9.95), 'eye', (0, -14, -14), (-1.1, CY + 0.5, -10.1))
    b.box('body', (-0.5, CY + 1.9, -4.0), (0.5, CY + 2.8, 6.5), 'steel')                                      # spine
    b.box('body', (-1.4, CY + 1.8, -1.4), (1.4, CY + 2.4, 2.6), 'gun_lit')                                    # dorsal sensor
    b.box('body', (-2.2, CY - 2.6, -3.0), (2.2, CY - 1.8, 5.0), 'steel_dark')                                 # belly
    pair('body', (-3.4, CY - 0.8, -2.0), (-2.9, CY + 0.8, 4.0), 'vent_glow')                                  # intakes
    b.box('body', (-2.4, CY - 1.5, 7.4), (2.4, CY + 1.3, 8.6), 'gun_light')                                   # tail cone
    b.box('body', (-1.4, CY - 0.8, 8.5), (1.4, CY + 0.6, 9.0), 'thruster')
    # ---- belly bay doors (open for the missile drop) -------------------------------------------------
    b.bone('bay_r', 'body', [-0.1, CY - 2.6, 1.0])
    b.box('bay_r', (-2.1, CY - 2.9, -2.4), (-0.1, CY - 2.5, 4.4), 'chrome_dark')
    # ---- wing (mirrored): swept plate, split flap, tip light, wing-root cannon ---------------------------
    b.bone('wing_r', 'body', [-2.8, CY, 1.0])
    b.rbox('wing_r', (-12.5, CY - 0.45, -1.6), (-2.6, CY + 0.35, 3.4), 'chrome_bolt', (0, -24, -4), (-2.8, CY, 1.0))
    b.rbox('wing_r', (-9.0, CY - 0.6, -0.9), (-2.6, CY + 0.5, 1.6), 'steel', (0, -24, -4), (-2.8, CY, 1.0))
    b.rbox('wing_r', (-13.2, CY - 0.5, 1.2), (-12.2, CY + 0.4, 3.8), 'core_box', (0, -24, -4), (-2.8, CY, 1.0))
    b.bone('flap_r', 'wing_r', [-7.4, CY, 5.6])
    b.rbox('flap_r', (-11.6, CY - 0.3, 3.4), (-3.4, CY + 0.2, 5.0), 'chrome_dark', (0, -24, -4), (-2.8, CY, 1.0))
    b.bone('gun_r', 'wing_r', [-5.0, CY - 1.0, -2.0])
    b.box('gun_r', (-6.0, CY - 1.9, -3.2), (-4.0, CY - 0.2, 1.4), 'gun')
    b.box('gun_r', (-5.35, CY - 1.4, -9.4), (-4.65, CY - 0.7, -3.0), 'steel_dark')
    b.box('gun_r', (-5.55, CY - 1.6, -6.0), (-4.45, CY - 0.5, -4.6), 'chrome_light')
    b.box('gun_r', (-5.25, CY - 1.3, -9.7), (-4.75, CY - 0.8, -9.35), 'glow_soft')
    # ---- engine nacelle (mirrored): vectoring pod, intake, exhaust, flame (glow, stretched by thrust) ------
    b.bone('nacelle_r', 'body', [-4.4, CY - 0.2, 4.0])
    b.oct('nacelle_r', (-4.4, CY - 0.2, 6.0), 1.7, 6.6, 'z', 'chrome', 'chrome_dark')
    b.box('nacelle_r', (-5.5, CY - 1.3, 2.4), (-3.3, CY + 0.9, 2.9), 'black')
    b.box('nacelle_r', (-5.3, CY - 1.1, 9.2), (-3.5, CY + 0.7, 9.6), 'thruster')
    b.box('nacelle_r', (-4.65, CY + 1.3, 3.4), (-4.15, CY + 2.0, 8.0), 'steel')
    b.bone('flame_r', 'nacelle_r', [-4.4, CY - 0.2, 9.6])
    b.box('flame_r', (-5.0, CY - 0.8, 9.6), (-3.8, CY + 0.4, 13.6), 'flame')
    b.box('flame_r', (-4.7, CY - 0.5, 13.4), (-4.1, CY + 0.1, 15.4), 'flame')
    # ---- V-tail fin (mirrored) -----------------------------------------------------------------------
    b.bone('tail_r', 'body', [-1.2, CY + 1.6, 6.0])
    b.rbox('tail_r', (-1.6, CY + 1.4, 3.8), (-1.0, CY + 6.2, 8.4), 'chrome', (-18, 0, -32), (-1.2, CY + 1.6, 6.0))
    b.rbox('tail_r', (-1.7, CY + 5.4, 6.6), (-0.9, CY + 6.4, 8.6), 'core_box', (-18, 0, -32), (-1.2, CY + 1.6, 6.0))
    U.mirror_r(m)
    return m


def pose(body=(0, 0, 0), pos=(0, 0, 0), wings=(0, 0, 0), flaps=(0.0, 0.0), guns=(0.0, 0.0), nac=(0.0, 0.0), flame=1.0,
         tail=0.0, bay=0.0, root=(0, 0, 0), root_pos=(0, 0, 0)):
    """wings: (pitch, sweep, dihedral) of the right wing (left mirrors); flaps (right, left) deg down; guns
    (right, left) recoil px; nac (right, left) nacelle pitch (0 = thrust back, 90 = thrust down); flame scale."""
    p = {'root': {'rot': tuple(root), 'pos': tuple(root_pos)},
         'body': {'rot': tuple(body), 'pos': tuple(pos)},
         'wing_r': tuple(wings), 'wing_l': (wings[0], -wings[1], -wings[2]),
         'flap_r': (flaps[0], 0.0, 0.0), 'flap_l': (flaps[1], 0.0, 0.0),
         'gun_r': {'rot': (0.0, 0.0, 0.0), 'pos': (0.0, 0.0, guns[0])}, 'gun_l': {'rot': (0.0, 0.0, 0.0), 'pos': (0.0, 0.0, guns[1])},
         'nacelle_r': (-nac[0], 0.0, 0.0), 'nacelle_l': (-nac[1], 0.0, 0.0),
         'flame_r': {'rot': (0.0, 0.0, 0.0), 'scale': (1.0, 1.0, flame)}, 'flame_l': {'rot': (0.0, 0.0, 0.0), 'scale': (1.0, 1.0, flame)},
         'tail_r': (0.0, 0.0, tail), 'tail_l': (0.0, 0.0, -tail),
         'bay_r': (0.0, 0.0, -bay), 'bay_l': (0.0, 0.0, bay)}
    return p


HOVER_NAC = (78.0, 78.0)


def clips():
    out = []
    c = UClip('hover', 2.0, loop=True)
    for i in range(9):
        t = 2.0 * i / 8
        ph = 2 * math.pi * i / 8
        c.key(t, pose(body=(2 * math.sin(ph + 0.8), 0, 3 * math.sin(ph)), pos=(0, 0.6 * math.sin(2 * ph), 0),
                      wings=(0, 4, 3 + 2 * math.sin(ph)), flaps=(8 + 4 * math.sin(ph), 8 - 4 * math.sin(ph)),
                      nac=(HOVER_NAC[0] + 5 * math.sin(ph), HOVER_NAC[1] - 5 * math.sin(ph)), flame=0.7 + 0.1 * math.sin(3 * ph)))
    out.append(c)
    c = UClip('fly', 1.0, loop=True)
    for i in range(5):
        t = i * 0.25
        ph = 2 * math.pi * i / 4
        c.key(t, pose(body=(4 + 1.5 * math.sin(ph), 0, 2 * math.sin(ph)), pos=(0, 0.3 * math.sin(ph), 0), wings=(0, -6, -2),
                      flaps=(-2, -2), nac=(4, 4), flame=1.5 + 0.15 * math.sin(2 * ph), tail=-6))
    out.append(c)
    for name, p in (('accel', {'body': (8, 0, 0), 'wing_r': (0, -8, 0), 'wing_l': (0, 8, 0), 'nacelle_r': (10, 0, 0), 'nacelle_l': (10, 0, 0)}),
                    ('decel', {'body': (-14, 0, 0), 'flap_r': (30, 0, 0), 'flap_l': (30, 0, 0), 'nacelle_r': (-40, 0, 0), 'nacelle_l': (-40, 0, 0)}),
                    ('turn_l', {'body': (0, -6, -30), 'flap_r': (-16, 0, 0), 'flap_l': (16, 0, 0), 'tail_r': (0, 0, 8), 'tail_l': (0, 0, 8)}),
                    ('turn_r', {'body': (0, 6, 30), 'flap_r': (16, 0, 0), 'flap_l': (-16, 0, 0), 'tail_r': (0, 0, -8), 'tail_l': (0, 0, -8)})):
        c = UClip(name, 1.0)
        c.key(0, p).key(1.0, p)
        out.append(c)
    # burst (18 ticks): nose dips onto the target, cannons recoil 0.30 (left), 0.45 (right), 0.60 (left)
    aim = pose(body=(6, 0, 0), wings=(0, 2, 2), nac=(40, 40), flame=1.0, flaps=(4, 4))
    c = UClip('burst', 0.9, review=0.3)
    c.key(0, pose(nac=HOVER_NAC, flame=0.7), 'easeOutCubic').key(0.22, aim)
    for i, t in enumerate((0.3, 0.45, 0.6)):
        left = i % 2 == 0
        kick = pose(body=(3, 0, 1.5 if left else -1.5), pos=(0, 0.1, 0.5), wings=(0, 2, 2), nac=(40, 40), flaps=(4, 4),
                    guns=(0.0 if left else 1.6, 1.6 if left else 0.0))
        c.key(t - 0.03, aim).key(t, kick, 'easeOutCubic')
    c.key(0.66, aim, 'easeInOutSine').key(0.9, pose(nac=HOVER_NAC, flame=0.7))
    out.append(c)
    # missile (20 ticks): bay doors swing open, the craft lifts its nose, drop at 0.50, doors close
    c = UClip('missile', 1.0, review=0.5)
    c.key(0, pose(nac=HOVER_NAC, flame=0.7), 'easeOutCubic').key(0.3, pose(body=(-6, 0, 0), bay=80, nac=(70, 70), flame=0.8), 'easeInCubic') \
        .key(0.5, pose(body=(-12, 0, 0), pos=(0, 0.8, 0.6), bay=95, nac=(60, 60), flame=1.1), 'easeOutCubic') \
        .key(0.75, pose(body=(-3, 0, 0), bay=60, nac=(70, 70), flame=0.8), 'easeInOutSine').key(1.0, pose(nac=HOVER_NAC, flame=0.7))
    out.append(c)
    # dive (30 ticks): pull up and roll (0-0.7), tip over, wings swept back, strike at 1.10, pull out
    up = pose(body=(-34, 0, 24), pos=(0, 1.2, 0), wings=(0, 6, 8), flaps=(-20, 20), nac=(0, 0), flame=1.4, tail=8)
    tip = pose(body=(40, 0, 0), wings=(0, -26, -4), flaps=(-10, -10), nac=(0, 0), flame=2.0, tail=-10)
    strike = pose(body=(55, 0, 0), pos=(0, -0.6, -1.2), wings=(0, -34, -6), flaps=(-14, -14), nac=(-6, -6), flame=2.3, tail=-12)
    c = UClip('dive', 1.5, review=1.1)
    c.key(0, pose(nac=HOVER_NAC, flame=0.7), 'easeOutCubic').key(0.6, up, 'easeInOutSine').key(0.78, tip, 'easeInQuad') \
        .key(1.1, strike, 'easeOutCubic').key(1.25, pose(body=(-30, 0, 0), wings=(0, 8, 6), flaps=(30, 30), nac=(50, 50), flame=1.6), 'easeInOutSine') \
        .key(1.5, pose(nac=HOVER_NAC, flame=0.7))
    out.append(c)
    # boost (12 ticks): wings fold back, nacelles lock aft, flames flare, roll into the side-step
    c = UClip('boost', 0.6, review=0.2)
    c.key(0, pose(nac=HOVER_NAC, flame=0.7), 'easeOutQuad').key(0.12, pose(body=(10, 0, 0), wings=(0, -30, -6), nac=(-4, -4), flame=2.6, tail=-10),
                                                              'easeOutCubic') \
        .key(0.3, pose(body=(6, 0, 40), wings=(0, -26, -4), nac=(0, 0), flame=2.2), 'easeInOutSine') \
        .key(0.6, pose(nac=HOVER_NAC, flame=0.7))
    out.append(c)
    c = UClip('hurt', 0.35)
    c.key(0, {'body': (0, 0, 0)}).key(0.06, {'body': (-12, 8, 22)}, 'easeOutCubic').key(0.35, {'body': (0, 0, 0)})
    out.append(c)
    c = UClip('heavy_hit', 1.0, review=0.25)
    c.key(0, pose(nac=HOVER_NAC, flame=0.7), 'easeOutCubic').key(0.25, pose(body=(-30, 40, 120), pos=(0, 1.0, 3.0), flaps=(30, -30), nac=(30, 90),
                                                                           flame=0.4), 'easeInOutSine') \
        .key(0.55, pose(body=(10, -10, 300), pos=(0, -0.6, 2.0), nac=(60, 60), flame=1.2), 'easeInOutSine') \
        .key(0.8, pose(body=(-4, 0, 356), nac=(70, 70), flame=0.9), 'easeInOutSine').key(1.0, pose(body=(0, 0, 360), nac=HOVER_NAC, flame=0.7))
    out.append(c)
    # death (30 ticks): engine flames out on one side, spins down nose-first
    c = UClip('death', 1.5, review=0.7)
    c.key(0, pose(nac=HOVER_NAC, flame=0.7), 'easeOutCubic').key(0.15, pose(body=(-14, 10, 30), wings=(0, 0, 10), flaps=(40, -30), nac=(60, 20), flame=0.3)) \
        .key(1.3, pose(root=(55, 0, 0), root_pos=(0, -2.5, 0), body=(10, 600, 80), wings=(10, -10, 24), flaps=(40, -40), nac=(-20, 50), flame=0.05),
             'easeInQuad') \
        .key(1.5, pose(root=(60, 0, 0), root_pos=(0, -2.5, 0), body=(10, 640, 80), wings=(10, -10, 24), flaps=(40, -40), nac=(-20, 50), flame=0.0))
    c.keep.update({'root'})
    out.append(c)
    # fabricate (60 ticks): folded dart on the pad, wings swing out, tail fins pop, nacelles drop, lifts off
    folded = pose(pos=(0, -4.8, 0), wings=(0, -60, 30), flaps=(60, 60), nac=(-40, -40), tail=40, flame=0.01)
    c = UClip('fabricate', 3.0, review=1.6)
    c.key(0, folded).key(1.0, folded, 'easeInOutSine') \
        .key(1.5, pose(pos=(0, -4.8, 0), wings=(0, 4, 0), flaps=(60, 60), nac=(-40, -40), tail=40, flame=0.01), 'easeOutBack') \
        .key(1.8, pose(pos=(0, -4.8, 0), wings=(0, 4, 0), flaps=(10, 10), nac=(-40, -40), tail=0, flame=0.01), 'easeOutBack') \
        .key(2.2, pose(pos=(0, -4.6, 0), wings=(0, 4, 3), flaps=(10, 10), nac=HOVER_NAC, flame=0.3), 'easeInCubic') \
        .key(2.6, pose(pos=(0, 0.6, 0), body=(-4, 0, 0), nac=HOVER_NAC, flame=1.1), 'easeOutCubic').key(3.0, pose(nac=HOVER_NAC, flame=0.7))
    out.append(c)
    return out


def main():
    m = build()
    m.write(GEO, TEX, GLOW)
    n = sum(len(bn.cubes) for bn in m.bones)
    cl = clips()
    size = U.write_anims(ANIM, cl)
    print('interceptor: %d bones, %d cubes, atlas %dx%d; %d clips (%d bytes)' % (len(m.bones), n, m.width, m.height, len(cl), size))
    U.finalize_assets([GEO, ANIM], [TEX, GLOW])
    if '--preview' in sys.argv:
        U.preview_all('interceptor', GEO, TEX, GLOW, cl)


if __name__ == '__main__':
    main()
