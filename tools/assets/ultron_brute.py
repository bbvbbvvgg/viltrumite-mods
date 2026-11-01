#!/usr/bin/env python3
"""Ultron heavy assault robot ("brute"): a hunched three-block war machine. Barrel chest with a caged red reactor,
huge layered pauldrons, gorilla arms ending in sledgehammer fists (three jointed fingers and a thumb each), a
small visored head sunk between the shoulders, twin four-tube missile pods on the shoulder frame with hinged
hatches, back exhaust stacks, a shield emitter on the left forearm and short pistoned legs. Geo (211 cubes),
metal atlas + red glow atlas, and every clip: idle, combat_idle, walk, run, accel/decel/turn_l/turn_r
(additive), slam, swing, missiles, charge, shield_up, shield_hold, hurt (additive), heavy_hit, land, fall,
death, fabricate. Contact frames match UltronMove (SLAM 30 ticks: hop 9, impact 17; SWING 18: hit 9;
MISSILES 34: launches 14 left pod, 18 right, 22 left, 26 right; CHARGE 14; SHIELD_UP 12; death 40). Own work, CC0.

Run: python3 tools/assets/ultron_brute.py [--preview]
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ultron_lib as U  # noqa: E402
from ultron_lib import UClip, humanoid, plus  # noqa: E402

GEO = 'geo/ultron/brute.geo.json'
ANIM = 'animations/ultron/brute.animation.json'
TEX = 'textures/entity/ultron/brute.png'
GLOW = 'textures/entity/ultron/brute_glow.png'
FINGERS_R = [('f1a_r', 'f1b_r'), ('f2a_r', 'f2b_r'), ('f3a_r', 'f3b_r')]
FINGERS_L = [tuple(n[:-2] + '_l' for n in ch) for ch in FINGERS_R]
THUMB_R = ('thumb_a_r', 'thumb_b_r')
THUMB_L = tuple(n[:-2] + '_l' for n in THUMB_R)


def build():
    m = U.Model('ultron_brute', density=2, width=512, seed=1701)
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
    # ---- pelvis ------------------------------------------------------------------------------------
    b.bone('hips', 'root', [0, 17.0, 1.0])
    b.box('hips', (-6.0, 14.2, -3.0), (6.0, 19.0, 4.2), 'gun')
    b.rbox('hips', (-2.6, 12.4, -4.2), (2.6, 18.2, -2.8), 'chrome_bolt', (-10, 0, 0), (0, 18.2, -3.4))
    pair('hips', (-7.4, 13.4, -3.2), (-5.6, 19.2, 4.0), 'chrome_dark', (0, 0, 8), (-6.4, 19.2, 0.4))
    b.box('hips', (-4.2, 14.0, 4.0), (4.2, 18.6, 5.4), 'steel_bolt')
    b.oct('hips', (0, 19.3, 0.6), 3.4, 1.2, 'y', 'ribs')
    # ---- abdomen -------------------------------------------------------------------------------------
    b.bone('abdomen', 'hips', [0, 19.6, 1.0])
    b.box('abdomen', (-3.4, 19.2, -1.6), (3.4, 24.6, 3.6), 'ribs')
    for i, y in enumerate((19.6, 21.3, 23.0)):
        w = 5.2 - 0.3 * i
        b.box('abdomen', (-w, y, -3.4 + 0.3 * i), (w, y + 1.4, 3.0), 'steel' if i % 2 == 0 else 'chrome_dark')
    pair('abdomen', (-5.8, 19.4, -0.6), (-4.8, 24.4, 1.8), 'cable')
    pair('abdomen', (-4.4, 19.6, 3.2), (-1.0, 24.4, 4.6), 'gun_light')
    # ---- chest: barrel torso, caged reactor, collar, back stacks ---------------------------------
    b.bone('chest', 'abdomen', [0, 24.4, 1.0])
    b.box('chest', (-8.6, 24.2, -5.4), (8.6, 36.6, 6.2), 'gun')
    pair('chest', (-9.4, 30.0, -7.0), (-0.6, 36.4, -4.6), 'chrome', (8, 14, 0), (-5.0, 33.2, -5.8))      # upper chest plates
    pair('chest', (-8.8, 24.6, -6.2), (-1.2, 30.4, -4.2), 'chrome_seam', (-6, 10, 0), (-5.0, 27.5, -5.2))   # lower chest plates
    pair('chest', (-10.0, 25.0, -4.6), (-8.2, 35.8, 5.4), 'steel_bolt')                                 # flank armour
    b.box('chest', (-2.4, 28.0, -6.6), (2.4, 33.6, -5.4), 'gun_dark')                                   # reactor housing
    b.oct('chest', (0, 30.8, -7.0), 1.85, 0.8, 'z', 'core')
    for x in (-1.9, -0.65, 0.65, 1.9):                                                                 # cage bars
        b.box('chest', (x - 0.24, 27.8, -8.3), (x + 0.24, 33.8, -7.6), 'chrome_light')
    b.box('chest', (-2.6, 33.4, -7.8), (2.6, 34.2, -5.4), 'chrome_dark')
    b.box('chest', (-2.6, 27.4, -7.8), (2.6, 28.2, -5.4), 'chrome_dark')
    b.box('chest', (-7.0, 36.2, -4.6), (7.0, 38.4, 5.6), 'chrome_dark')                                 # collar
    pair('chest', (-8.4, 36.0, 1.0), (-3.4, 40.2, 6.4), 'chrome_bolt', (-14, 0, -8), (-5.9, 38.0, 3.6))  # neck guards
    b.box('chest', (-7.6, 24.8, 5.8), (7.6, 36.0, 7.4), 'chrome_seam')                                  # back plate
    for x in (-4.0, 4.0):                                                                               # exhaust stacks
        b.oct('chest', (x, 37.5, 8.2), 1.5, 9.0, 'y', 'gun_light')
        b.box('chest', (x - 1.1, 41.6, 7.1), (x + 1.1, 42.3, 9.3), 'vent_glow')
        b.box('chest', (x - 1.75, 34.0, 6.6), (x + 1.75, 35.0, 9.8), 'steel')
    pair('chest', (-10.6, 32.4, -2.2), (-8.6, 36.4, 2.6), 'gun')                                       # shoulder sockets
    pair('chest', (-14.2, 38.2, -1.0), (-8.0, 39.4, 4.2), 'steel_dark')                                 # pod frame
    # ---- missile pods with hatches (pod centre = server launch point local(+-0.75, 2.75, -0.1)) -------
    b.bone('pod_r', 'chest', [-12.0, 39.4, 1.6])
    b.box('pod_r', (-15.2, 39.4, -1.6), (-8.8, 44.6, 4.8), 'chrome_dark')
    b.box('pod_r', (-15.6, 40.0, -1.9), (-8.4, 41.0, 5.1), 'steel')
    b.box('pod_r', (-14.8, 44.0, -1.2), (-9.2, 44.8, 4.4), 'gun_dark')
    for (x, z) in ((-13.6, 0.0), (-10.4, 0.0), (-13.6, 3.2), (-10.4, 3.2)):
        b.box('pod_r', (x - 0.9, 44.2, z - 0.9), (x + 0.9, 44.9, z + 0.9), 'black')
        b.box('pod_r', (x - 0.5, 44.3, z - 0.5), (x + 0.5, 45.3, z + 0.5), 'steel')
        b.box('pod_r', (x - 0.25, 45.2, z - 0.25), (x + 0.25, 45.5, z + 0.25), 'glow_soft')
    b.box('pod_r', (-15.4, 41.6, -2.1), (-14.6, 43.6, -1.4), 'gun_lit')
    b.bone('hatch_r', 'pod_r', [-12.0, 44.8, -1.6])
    b.box('hatch_r', (-15.4, 44.8, -1.8), (-8.6, 45.8, 5.0), 'chrome_bolt')
    b.box('hatch_r', (-12.4, 45.7, -1.6), (-11.6, 46.2, 4.8), 'steel')
    # ---- head: visored, sunk between the shoulders ---------------------------------------------------
    b.bone('neck', 'chest', [0, 36.4, -2.6])
    b.box('neck', (-2.0, 35.6, -4.4), (2.0, 38.4, -1.0), 'ribs')
    b.box('neck', (-0.8, 35.6, -1.2), (0.8, 38.6, 1.0), 'cable')
    b.bone('head', 'neck', [0, 37.6, -4.0])
    b.box('head', (-3.3, 37.4, -9.4), (3.3, 42.0, -3.4), 'chrome')                     # helm
    b.box('head', (-3.6, 40.4, -9.9), (3.6, 42.6, -4.4), 'chrome_light')               # brow ridge
    pair('head', (-3.9, 37.6, -9.0), (-3.0, 41.4, -3.8), 'steel')                       # cheek guards
    b.box('head', (-3.0, 39.2, -9.7), (3.0, 40.4, -9.3), 'black')                       # visor slot
    b.box('head', (-2.7, 39.45, -9.85), (2.7, 40.15, -9.5), 'eye')
    b.box('head', (-0.5, 42.4, -9.6), (0.5, 43.2, -4.0), 'steel_dark')                  # crest
    pair('head', (-3.4, 38.0, -3.6), (-1.0, 41.6, -2.2), 'gun_lit')
    b.bone('jaw', 'head', [0, 38.2, -5.0])
    b.box('jaw', (-2.8, 35.8, -9.6), (2.8, 38.0, -5.2), 'chrome_dark')
    b.box('jaw', (-2.0, 36.0, -9.95), (2.0, 37.4, -9.5), 'vent_glow')
    # ---- right arm (mirrored): pauldron, upper arm, piston, forearm, fist -------------------------------
    b.bone('arm_r', 'chest', [-10.6, 34.2, 0.2])
    b.oct('arm_r', (-11.4, 34.2, 0.2), 2.4, 2.4, 'x', 'gun')
    b.box('arm_r', (-16.4, 32.6, -4.6), (-8.6, 38.6, 5.0), 'chrome_bolt')                # pauldron dome
    b.rbox('arm_r', (-17.2, 30.4, -4.2), (-15.8, 36.6, 4.6), 'chrome_dark', (0, 0, 12), (-15.0, 36.0, 0))
    b.box('arm_r', (-15.6, 38.4, -3.8), (-9.4, 39.2, 4.2), 'steel')
    b.box('arm_r', (-16.0, 33.6, -5.0), (-9.0, 34.6, 5.4), 'chrome_light')
    b.box('arm_r', (-14.2, 24.6, -2.6), (-8.6, 32.6, 2.6), 'gun')                       # upper arm
    b.box('arm_r', (-14.8, 25.6, -3.2), (-11.0, 31.6, 1.6), 'steel')
    b.box('arm_r', (-12.0, 25.2, 2.2), (-9.0, 31.2, 3.2), 'cable')
    b.bone('forearm_r', 'arm_r', [-11.4, 24.4, 0.0])
    b.oct('forearm_r', (-11.4, 24.2, 0), 2.1, 2.4, 'x', 'gun_light')
    b.box('forearm_r', (-15.4, 13.4, -3.8), (-7.4, 23.4, 3.8), 'gun')
    b.box('forearm_r', (-16.0, 14.0, -4.2), (-11.4, 22.8, 4.2), 'chrome_seam')          # outer guard
    b.box('forearm_r', (-11.6, 15.0, -4.4), (-7.0, 22.4, -2.2), 'chrome')
    b.box('forearm_r', (-11.2, 15.6, 3.4), (-7.6, 21.6, 4.4), 'steel_dark')
    b.box('forearm_r', (-15.8, 12.6, -3.6), (-7.0, 14.0, 3.6), 'chrome_dark')           # wrist cuff
    b.box('forearm_r', (-16.3, 17.0, -1.0), (-15.8, 20.0, 1.0), 'vent_glow')
    b.bone('hand_r', 'forearm_r', [-11.2, 12.8, 0.0])
    b.box('hand_r', (-14.2, 7.8, -3.2), (-8.2, 12.8, 3.2), 'steel_bolt')                 # palm block
    b.box('hand_r', (-14.8, 8.6, -3.4), (-13.8, 12.4, 3.4), 'chrome')                    # knuckle guard
    b.box('hand_r', (-8.4, 9.4, -1.0), (-8.0, 11.6, 1.0), 'core')                        # palm emitter
    for i, z in enumerate((-2.1, 0.0, 2.1)):
        U.finger_chain(b, 'r', 'hand_r', (-11.6, 7.8, z), FINGERS_R[i], (2.6, 2.2), 1.8, mat='chrome', joint='gun')
    b.bone('thumb_a_r', 'hand_r', [-8.6, 11.0, -3.0])
    b.box('thumb_a_r', (-9.6, 8.4, -4.2), (-7.8, 11.2, -2.4), 'chrome')
    b.bone('thumb_b_r', 'thumb_a_r', [-8.7, 8.4, -3.3])
    b.box('thumb_b_r', (-9.5, 6.4, -4.1), (-7.9, 8.5, -2.5), 'chrome_dark')
    # ---- right leg (mirrored) ------------------------------------------------------------------------
    b.bone('leg_r', 'hips', [-4.4, 15.8, 0.8])
    b.oct('leg_r', (-4.4, 15.6, 0.8), 2.1, 2.6, 'x', 'gun')
    b.box('leg_r', (-7.0, 8.8, -2.2), (-1.8, 15.2, 3.4), 'gun')
    b.box('leg_r', (-7.4, 9.6, -3.0), (-1.6, 14.8, -1.4), 'chrome')
    b.box('leg_r', (-7.8, 9.8, -1.8), (-6.6, 14.6, 3.0), 'steel_bolt')
    b.box('leg_r', (-6.4, 10.2, 3.0), (-2.4, 14.4, 4.2), 'chrome_dark')
    b.bone('shin_r', 'leg_r', [-4.4, 8.8, 0.6])
    b.oct('shin_r', (-4.4, 8.7, 0.6), 1.9, 2.8, 'x', 'gun_light')
    b.rbox('shin_r', (-6.6, 7.0, -3.4), (-2.2, 10.6, -1.8), 'chrome_light', (-10, 0, 0))   # knee cap
    b.box('shin_r', (-6.6, 2.4, -1.8), (-2.2, 8.2, 2.8), 'gun')
    b.box('shin_r', (-7.0, 3.0, -2.6), (-1.8, 7.6, -1.4), 'chrome')
    b.box('shin_r', (-6.4, 3.4, 2.6), (-2.4, 7.8, 4.0), 'steel')
    U.Builder.piston(b, 'shin_r', 'shin_r', (-4.4, 8.0, 3.6), (-4.4, 2.6, 3.6), r=0.7)
    b.bone('foot_r', 'shin_r', [-4.4, 2.4, 0.4])
    b.oct('foot_r', (-4.4, 2.3, 0.4), 1.5, 2.6, 'x', 'gun')
    b.box('foot_r', (-7.4, 0.0, -3.6), (-1.4, 2.2, 3.8), 'steel_bolt')
    b.box('foot_r', (-7.2, 1.6, -3.8), (-1.6, 2.8, 1.4), 'chrome')
    b.box('foot_r', (-6.6, 0.0, 3.4), (-2.2, 1.6, 4.8), 'gun_light')
    b.bone('toe_r', 'foot_r', [-4.4, 1.0, -3.6])
    b.box('toe_r', (-7.6, 0.0, -6.4), (-1.2, 1.9, -3.6), 'chrome_dark')
    b.box('toe_r', (-7.2, 0.0, -6.9), (-1.6, 0.9, -6.2), 'steel')
    U.mirror_r(m)
    # ---- shield emitter on the left forearm only -----------------------------------------------------
    b.bone('emitter_l', 'forearm_l', [16.6, 18.4, 0.0])
    b.oct('emitter_l', (16.4, 18.4, 0.0), 2.0, 0.8, 'x', 'chrome_dark')
    b.oct('emitter_l', (16.9, 18.4, 0.0), 1.3, 0.5, 'x', 'core')
    b.box('emitter_l', (15.8, 20.4, -0.5), (16.6, 21.4, 0.5), 'gun_lit')
    return m


# ---- poses ------------------------------------------------------------------------------------------------
def P(**kw):
    return kw


def pose(p, pods=(0.0, 0.0), hatches=(0.0, 0.0), emitter=0.0):
    q = humanoid(p, FINGERS_R, FINGERS_L, THUMB_R, THUMB_L)
    q['pod_r'] = (pods[0], 0.0, 0.0)
    q['pod_l'] = (pods[1], 0.0, 0.0)
    q['hatch_r'] = (-hatches[0], 0.0, 0.0)
    q['hatch_l'] = (-hatches[1], 0.0, 0.0)
    q['emitter_l'] = (0.0, 0.0, 0.0) if not emitter else {'rot': (emitter * 360.0, 0.0, 0.0), 'scale': (1.0, 1.0 + 0.25 * min(1.0, emitter), 1.0 + 0.25 * min(1.0, emitter))}
    return q


# Hunched rest: arms hang forward of the body, knees soft
RELAX = P(hips=(6, 0, 0), spine=(6, 0, 0), chest=(8, 0, 0), head=(-16, 0, 0), arm_r=(-6, 0, 6), arm_l=(-6, 0, -6),
          elbow_r=18, elbow_l=18, grip_r=0.55, grip_l=0.55, leg_r=(-6, 0, 3), leg_l=(-6, 0, -3), knee_r=12, knee_l=12,
          foot_r=(-6, 0, -3), foot_l=(-6, 0, 3))
GUARD = P(pos=(0, -1.2, 0), hips=(10, -4, 0), spine=(8, 0, 0), chest=(10, -8, 0), head=(-24, 10, 0),
          leg_r=(-18, 8, 6), knee_r=24, foot_r=(-6, 0, -6), leg_l=(-4, 4, -6), knee_l=20, foot_l=(-14, 0, 6),
          arm_r=(-30, -6, 18), elbow_r=70, hand_r=(0, 0, -6), grip_r=1.0,
          arm_l=(-46, 6, -14), elbow_l=78, hand_l=(0, 0, 6), grip_l=1.0)


def stride(ph, run=False):
    """Heavy locomotion at gait phase ph (0 = right foot strikes in front): wide stance, hard heel strike, the
    torso rolls over the planted leg, the arms swing low and heavy."""
    c, s = math.cos(2 * math.pi * ph), math.sin(2 * math.pi * ph)
    amp = 28 if run else 20

    def leg(phase):
        cc = math.cos(2 * math.pi * phase)
        rx = -amp * cc - 4
        f = phase % 1.0
        if f < 0.5:
            knee = (18 if run else 12) + (10 if run else 8) * math.sin(math.pi * f / 0.5)
            foot = -12 * (1 - f / 0.14) if f < 0.14 else 16 * max(0.0, (f - 0.32) / 0.18)
        else:
            knee = (18 if run else 12) + (70 if run else 46) * math.sin(math.pi * (f - 0.5) / 0.5)
            foot = 12 * math.sin(math.pi * (f - 0.5) / 0.5) - 8 * (f - 0.5) / 0.5
        return rx, knee, foot

    rr, kr, fr = leg(ph)
    rl, kl, fl = leg(ph + 0.5)
    impact = max(0.0, math.cos(4 * math.pi * ph)) ** 3       # the jolt as each foot lands
    bob = (0.9 if run else 0.6) * -math.cos(4 * math.pi * ph) - (0.8 if run else 0.2) - 0.35 * impact
    lean = 18 if run else 8
    return P(pos=(0, bob, 0), hips=(lean * 0.5, 8 * c, 5 * s), spine=(lean * 0.3, -3 * c, -2 * s),
             chest=(lean * 0.4 + 2.5 * impact, -7 * c, -4 * s), head=(-lean - 10 + 3 * impact, 5 * c, 3 * s),
             leg_r=(rr, 0, 4), knee_r=kr, foot_r=(fr, 0, -3), leg_l=(rl, 0, -4), knee_l=kl, foot_l=(fl, 0, 3),
             arm_r=((26 if run else 16) * c - 10, 0, 8), elbow_r=(60 if run else 22) + 10 * max(0.0, -c),
             arm_l=(-(26 if run else 16) * c - 10, 0, -8), elbow_l=(60 if run else 22) + 10 * max(0.0, c),
             grip_r=1.0 if run else 0.7, grip_l=1.0 if run else 0.7)


def clips():
    out = []
    c = UClip('idle', 4.0, loop=True)
    for i in range(9):
        t = i * 0.5
        ph = t / 4.0
        br = math.sin(2 * math.pi * ph * 2)
        c.key(t, pose(plus(RELAX, P(chest=(1.6 * br, 0, 0), spine=(0.8 * br, 0, 0), head=(-1.2 * br, 12 * math.sin(2 * math.pi * ph), 0),
                                   pos=(0, 0.2 * br, 0), hips=(0, 0, 1.0 * math.sin(2 * math.pi * ph)),
                                   arm_r=(2.0 * br, 0, 0.6 * br), arm_l=(2.0 * br, 0, -0.6 * br), grip_r=0.1 * br, grip_l=-0.1 * br)),
                      hatches=(0, 0)))
    out.append(c)
    c = UClip('combat_idle', 1.8, loop=True)
    for i in range(5):
        t = i * 0.45
        ph = t / 1.8
        bb = math.sin(2 * math.pi * ph)
        c.key(t, pose(plus(GUARD, P(pos=(0, 0.35 * bb, 0), chest=(2 * bb, 3 * math.cos(2 * math.pi * ph), 0),
                                    arm_r=(2.5 * bb, 0, 0), arm_l=(-2.5 * bb, 0, 0), head=(0, 4 * math.cos(2 * math.pi * ph), 0)))))
    out.append(c)
    c = UClip('walk', 1.3, loop=True)
    U.loop_keys(c, lambda ph: pose(stride(ph)), 8)
    out.append(c)
    c = UClip('run', 0.8, loop=True)
    U.loop_keys(c, lambda ph: pose(stride(ph, run=True)), 8)
    out.append(c)
    for name, p in (('accel', P(hips=(10, 0, 0), spine=(6, 0, 0), chest=(6, 0, 0), head=(-14, 0, 0), arm_r=(14, 0, 0), arm_l=(14, 0, 0))),
                    ('decel', P(hips=(-9, 0, 0), spine=(-5, 0, 0), chest=(-5, 0, 0), head=(9, 0, 0), knee_r=14, knee_l=14,
                                arm_r=(-16, 0, 8), arm_l=(-16, 0, -8))),
                    ('turn_l', P(hips=(0, -7, 4), chest=(0, -12, 3), head=(0, -20, 0), leg_l=(0, -8, 0), leg_r=(0, -4, 0))),
                    ('turn_r', P(hips=(0, 7, -4), chest=(0, 12, -3), head=(0, 20, 0), leg_r=(0, 8, 0), leg_l=(0, 4, 0)))):
        c = UClip(name, 1.0)
        q = humanoid(p)
        q = {k: v for k, v in q.items() if (v if not isinstance(v, dict) else v.get('rot', (0, 0, 0))) != (0.0, 0.0, 0.0)}
        c.key(0, q).key(1.0, q)
        out.append(c)
    # slam (30 ticks): crouch 0-0.3, hop at 0.45 with both fists raised overhead, hammer down, impact 0.85, hold, rise
    crouch = plus(GUARD, P(pos=(0, -3.0, 0.4), hips=(22, 0, 0), chest=(14, 0, 0), head=(-20, 0, 0), knee_r=60, knee_l=56,
                           leg_r=(-40, 0, 6), leg_l=(-30, 0, -6), foot_r=(-20, 0, 0), foot_l=(-24, 0, 0),
                           arm_r=(-10, 0, 30), elbow_r=40, arm_l=(-10, 0, -30), elbow_l=40))
    up = P(pos=(0, 1.0, 0.6), hips=(-8, 0, 0), spine=(-10, 0, 0), chest=(-16, 0, 0), head=(-6, 0, 0),
           arm_r=(-172, -14, -4), elbow_r=36, arm_l=(-172, 14, 4), elbow_l=36, grip_r=1, grip_l=1,
           leg_r=(-30, 0, 4), knee_r=50, foot_r=(20, 0, 0), leg_l=(-20, 0, -4), knee_l=60, foot_l=(24, 0, 0))
    smash = P(pos=(0, -5.0, -2.4), hips=(30, 0, 0), spine=(18, 0, 0), chest=(22, 0, 0), head=(-34, 0, 0),
              arm_r=(-58, -10, -10), elbow_r=6, arm_l=(-58, 10, 10), elbow_l=6, grip_r=1, grip_l=1,
              leg_r=(-56, 0, 10), knee_r=80, foot_r=(-22, 0, 0), leg_l=(14, 0, -10), knee_l=64, foot_l=(-16, 0, 0))
    c = UClip('slam', 1.5, review=0.85)
    c.key(0, pose(GUARD), 'easeOutCubic').key(0.3, pose(crouch), 'easeOutQuad').key(0.6, pose(up), 'easeInQuart') \
        .key(0.85, pose(smash), 'easeOutQuad').key(1.05, pose(plus(smash, P(pos=(0, 0.4, 0), chest=(-3, 0, 0))))) \
        .key(1.5, pose(GUARD))
    out.append(c)
    # swing (18 ticks, hit 0.45): right haymaker - coil back, torso unwinds, fist crosses at contact, carry through
    coil = plus(GUARD, P(chest=(0, 30, 0), hips=(0, 12, 0), spine=(0, 8, 0), arm_r=(-20, 40, 50), elbow_r=100, head=(-4, -14, 0),
                         pos=(0, -0.6, 0.6), leg_r=(-6, 6, 8), knee_r=34))
    hit = plus(GUARD, P(chest=(8, -34, 0), hips=(6, -16, 0), spine=(4, -8, 0), arm_r=(-84, -46, 8), elbow_r=14, hand_r=(0, 0, -6),
                        leg_r=(10, 0, 4), knee_r=16, foot_r=(16, 0, 0), head=(-10, 22, 0), pos=(0, -1.0, -1.6)))
    thru = plus(hit, P(chest=(4, -14, 0), hips=(0, -6, 0), arm_r=(10, -24, -6), elbow_r=10))
    c = UClip('swing', 0.9, review=0.45)
    c.key(0, pose(GUARD), 'easeOutQuad').key(0.3, pose(coil), 'easeInCubic').key(0.45, pose(hit), 'easeOutCubic') \
        .key(0.58, pose(thru), 'easeInOutSine').key(0.9, pose(GUARD))
    out.append(c)
    # missiles (34 ticks): brace wide, pods tilt up, hatches open; launches 0.70 (left pod), 0.90 (right),
    # 1.10 (left), 1.30 (right) each with a recoil kick on that side; hatches close, recover
    brace = P(pos=(0, -1.6, 0), hips=(6, 0, 0), spine=(-4, 0, 0), chest=(-8, 0, 0), head=(-6, 0, 0),
              leg_r=(-14, 0, 12), knee_r=26, foot_r=(-10, 0, -10), leg_l=(4, 0, -12), knee_l=22, foot_l=(-12, 0, 10),
              arm_r=(-4, 0, 34), elbow_r=24, hand_r=(0, 0, -10), arm_l=(-4, 0, -34), elbow_l=24, hand_l=(0, 0, 10), grip_r=1, grip_l=1)
    c = UClip('missiles', 1.7, review=0.7)
    c.key(0, pose(GUARD), 'easeOutCubic').key(0.4, pose(brace, pods=(-14, -14), hatches=(0, 0)), 'easeOutBack') \
        .key(0.6, pose(brace, pods=(-16, -16), hatches=(105, 105)))
    for i, t in enumerate((0.7, 0.9, 1.1, 1.3)):
        left = i % 2 == 0
        kick = P(chest=(-3, 4 if left else -4, -3 if left else 3), pos=(0, -0.3, 0.4))
        c.key(t - 0.04, pose(brace, pods=(-16, -16), hatches=(105, 105)))
        c.key(t, pose(plus(brace, kick), pods=(-16 + (6 if not left else 0), -16 + (6 if left else 0)), hatches=(105, 105)), 'easeOutCubic')
        c.key(t + 0.1, pose(brace, pods=(-16, -16), hatches=(105, 105)), 'easeInOutSine')
    c.key(1.5, pose(brace, pods=(-8, -8), hatches=(0, 0)), 'easeInCubic').key(1.7, pose(GUARD))
    out.append(c)
    # charge (14 ticks): lowers the shoulder (0-0.15) and drives forward, arms tucked, legs pumping
    ram = P(pos=(0, -2.2, -1.2), hips=(26, -14, 0), spine=(14, -6, 0), chest=(16, -20, 0), head=(-36, 16, 0),
            arm_r=(-20, 0, 48), elbow_r=110, grip_r=1, arm_l=(-62, 0, -8), elbow_l=96, grip_l=1)
    c = UClip('charge', 0.7, review=0.4)
    c.key(0, pose(plus(GUARD, P(pos=(0, -1.6, 0.6), hips=(18, 0, 0), knee_r=40, knee_l=40, leg_r=(-26, 0, 6), leg_l=(-14, 0, -6)))), 'easeOutQuad')
    for i in range(5):
        t = 0.15 + i * 0.1375
        st = stride(i / 2.0 % 1.0, run=True)
        legs = {k: v for k, v in st.items() if k.startswith(('leg', 'knee', 'foot'))}
        c.key(t, pose(plus(ram, legs, P(pos=(0, 0.6 * (-1) ** i, 0)))))
    out.append(c)
    # shield_up (12 ticks): left forearm swings up across the body, the emitter spins up; shield_hold loops
    hold = plus(GUARD, P(arm_l=(-84, -34, -20), elbow_l=88, hand_l=(0, 0, 26), chest=(0, -8, 0), head=(-18, 6, 0)))
    c = UClip('shield_up', 0.6, review=0.4)
    c.key(0, pose(GUARD), 'easeOutQuad').key(0.25, pose(plus(hold, P(arm_l=(10, 10, 6), elbow_l=20))), 'easeInCubic') \
        .key(0.4, pose(plus(hold, P(arm_l=(-4, 0, 0))), emitter=1.0), 'easeOutBack').key(0.6, pose(hold, emitter=1.0))
    out.append(c)
    c = UClip('shield_hold', 1.2, loop=True)
    c.key(0, pose(hold, emitter=0.001)).key(0.6, pose(plus(hold, P(arm_l=(-1.5, 0, 0), pos=(0, 0.2, 0))), emitter=0.5)) \
        .key(1.2, pose(hold, emitter=1.0))
    out.append(c)
    c = UClip('hurt', 0.4)
    c.key(0, humanoid(P())).key(0.07, humanoid(P(chest=(-8, 6, 4), head=(-12, 0, 6), spine=(-3, 0, 0), arm_r=(8, 0, 6), arm_l=(8, 0, -6))),
                                 'easeOutCubic').key(0.4, humanoid(P()))
    out.append(c)
    # heavy_hit (20 ticks): rocked back on its heels, a stomp to catch itself
    rock = P(pos=(0, -0.6, 2.4), hips=(-14, 6, 4), spine=(-8, 0, 0), chest=(-14, 8, 4), head=(-4, -10, 6),
             arm_r=(-50, 0, 44), elbow_r=40, arm_l=(-36, 0, -50), elbow_l=40, grip_r=0.4, grip_l=0.4,
             leg_r=(-24, 0, 6), knee_r=14, foot_r=(-20, 0, 0), leg_l=(12, 0, -4), knee_l=30)
    stomp = P(pos=(0, -2.4, 2.8), hips=(14, 0, 0), spine=(8, 0, 0), chest=(12, 0, 0), head=(-22, 0, 0),
              arm_r=(-24, 0, 30), elbow_r=50, arm_l=(-24, 0, -30), elbow_l=50,
              leg_r=(14, 0, 8), knee_r=50, foot_r=(-10, 0, 0), leg_l=(-46, 0, -8), knee_l=60, foot_l=(-6, 0, 0))
    c = UClip('heavy_hit', 1.0, review=0.15)
    c.key(0, pose(GUARD), 'easeOutCubic').key(0.15, pose(rock), 'easeInOutSine').key(0.5, pose(stomp), 'easeOutQuad') \
        .key(0.75, pose(plus(GUARD, P(pos=(0, -0.8, 0))))).key(1.0, pose(GUARD))
    out.append(c)
    low = P(pos=(0, -4.6, 0), hips=(24, 0, 0), chest=(12, 0, 0), head=(-26, 0, 0), leg_r=(-44, 0, 10), knee_r=80, foot_r=(-36, 0, 0),
            leg_l=(-44, 0, -10), knee_l=80, foot_l=(-36, 0, 0), arm_r=(-40, 0, 30), elbow_r=30, arm_l=(-40, 0, -30), elbow_l=30)
    c = UClip('land', 0.5, review=0.12)
    c.key(0, pose(P(knee_r=20, knee_l=20, leg_r=(-10, 0, 0), leg_l=(-10, 0, 0))), 'easeOutCubic').key(0.12, pose(low), 'easeInOutSine') \
        .key(0.5, pose(RELAX))
    out.append(c)
    c = UClip('fall', 0.8, loop=True)
    f0 = P(arm_r=(-40, 0, 50), arm_l=(-40, 0, -50), elbow_r=30, elbow_l=30, leg_r=(-24, 0, 6), knee_r=34, leg_l=(4, 0, -6), knee_l=44,
           head=(-6, 0, 0), chest=(-6, 0, 0))
    c.key(0, pose(f0)).key(0.4, pose(plus(f0, P(arm_r=(-12, 0, 8), arm_l=(-12, 0, -8), leg_r=(10, 0, 0), leg_l=(-10, 0, 0))))).key(0.8, pose(f0))
    out.append(c)
    # death (40 ticks): reactor fails, staggers, drops to its knees, pitches forward onto its fists then face
    sp = P(pos=(0, -0.8, 0), chest=(-16, -12, 10), head=(-6, 30, 16), arm_r=(-30, 0, 46), elbow_r=40, arm_l=(6, 0, -24), elbow_l=24,
           grip_r=0.9, grip_l=0.2, knee_r=16, hips=(-6, 0, 0))
    kneel = P(pos=(0, -9.0, -1.0), hips=(18, 0, 0), spine=(14, 0, 0), chest=(18, 0, 0), head=(-4, -10, 0),
              leg_r=(-30, 0, 6), knee_r=110, foot_r=(40, 0, 0), leg_l=(-12, 0, -6), knee_l=100, foot_l=(30, 0, 0),
              arm_r=(-14, 0, 14), elbow_r=24, arm_l=(-14, 0, -14), elbow_l=24, grip_r=0.6, grip_l=0.6)
    fists = plus(kneel, P(pos=(0, -1.6, -3.0), hips=(22, 0, 0), chest=(16, 0, 0), arm_r=(-40, 0, 10), arm_l=(-40, 0, -10), elbow_r=10, elbow_l=10,
                          head=(10, 10, 0)))
    down = P(pos=(0, -15.5, -12.0), root=(80, 0, 0), chest=(4, 0, 0), head=(16, 24, 0),
             leg_r=(-6, 0, 6), knee_r=14, foot_r=(30, 0, 0), leg_l=(4, 0, -8), knee_l=24, foot_l=(30, 0, 0),
             arm_r=(-150, 0, 20), elbow_r=20, arm_l=(-40, 0, -40), elbow_l=50, grip_r=0.4, grip_l=0.6)
    c = UClip('death', 2.0, review=0.9)
    c.key(0, pose(RELAX), 'easeOutCubic').key(0.2, pose(sp)).key(0.4, pose(plus(sp, P(head=(10, -50, 0), chest=(6, 24, -14))))) \
        .key(0.8, pose(kneel, pods=(10, 6)), 'easeInQuad').key(1.15, pose(fists, pods=(14, 10)), 'easeOutQuad') \
        .key(1.5, pose(down, pods=(20, 16)), 'easeInQuad').key(1.62, pose(plus(down, P(pos=(0, 1.0, 0))), pods=(24, 18))) \
        .key(2.0, pose(down, pods=(22, 18)))
    c.keep.update({'root'})
    out.append(c)
    # fabricate (60 ticks): crouched folded frame, arms locked against the chest, then the legs push it up, the
    # arms drop with a clang, the pods click up and the visor lights
    fold = P(pos=(0, -10.0, 0), hips=(40, 0, 0), spine=(24, 0, 0), chest=(24, 0, 0), head=(40, 0, 0),
             leg_r=(-96, 0, 12), knee_r=140, foot_r=(-44, 0, 0), leg_l=(-96, 0, -12), knee_l=140, foot_l=(-44, 0, 0),
             arm_r=(-30, 0, 12), elbow_r=130, arm_l=(-30, 0, -12), elbow_l=130, grip_r=1, grip_l=1)
    c = UClip('fabricate', 3.0, review=1.5)
    c.key(0, pose(fold, pods=(30, 30), hatches=(0, 0))).key(0.9, pose(fold, pods=(30, 30)), 'easeInOutSine') \
        .key(1.8, pose(P(pos=(0, -3.0, 0), hips=(20, 0, 0), chest=(14, 0, 0), head=(36, 0, 0), knee_r=46, knee_l=46, leg_r=(-30, 0, 6),
                         leg_l=(-30, 0, -6), foot_r=(-16, 0, 0), foot_l=(-16, 0, 0), arm_r=(-20, 0, 10), arm_l=(-20, 0, -10),
                         elbow_r=90, elbow_l=90), pods=(30, 30)), 'easeInOutSine') \
        .key(2.2, pose(plus(RELAX, P(head=(30, 0, 0))), pods=(30, 30)), 'easeInCubic') \
        .key(2.35, pose(plus(RELAX, P(head=(26, 0, 0), pos=(0, -0.5, 0))), pods=(0, 0)), 'easeOutBack') \
        .key(2.6, pose(plus(RELAX, P(head=(-6, 0, 0)))), 'easeOutCubic').key(3.0, pose(RELAX))
    out.append(c)
    return out


def main():
    m = build()
    m.write(GEO, TEX, GLOW)
    n = sum(len(bn.cubes) for bn in m.bones)
    cl = clips()
    size = U.write_anims(ANIM, cl)
    print('brute: %d bones, %d cubes, atlas %dx%d; %d clips (%d bytes)' % (len(m.bones), n, m.width, m.height, len(cl), size))
    U.finalize_assets([GEO, ANIM], [TEX, GLOW])
    if '--preview' in sys.argv:
        U.preview_all('brute', GEO, TEX, GLOW, cl)


if __name__ == '__main__':
    main()
