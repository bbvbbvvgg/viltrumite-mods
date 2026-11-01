#!/usr/bin/env python3
"""Ultron combat robot ("sentry"): slender machined humanoid, 2.2 blocks. Geo (160 cubes: layered chest
and pauldron plates, ribbed abdomen and neck, pistons, three jointed fingers and a thumb per hand, palm
repulsors, back thruster pack), metal atlas + red glow atlas, and every clip: idle, combat_idle, walk, run,
accel/decel/turn_l/turn_r (additive leans), strike_a/b/c, blast, lunge, guard, hurt (additive), heavy_hit,
land, fall, death, fabricate. Contact frames match UltronMove. Own work, CC0.

Run: python3 tools/assets/ultron_sentry.py [--preview]
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ultron_lib as U  # noqa: E402
from ultron_lib import UClip, humanoid, mix, plus  # noqa: E402

GEO = 'geo/ultron/sentry.geo.json'
ANIM = 'animations/ultron/sentry.animation.json'
TEX = 'textures/entity/ultron/sentry.png'
GLOW = 'textures/entity/ultron/sentry_glow.png'
FINGERS_R = [('f1a_r', 'f1b_r'), ('f2a_r', 'f2b_r'), ('f3a_r', 'f3b_r')]
FINGERS_L = [tuple(n[:-2] + '_l' for n in ch) for ch in FINGERS_R]
THUMB_R = ('thumb_a_r', 'thumb_b_r')
THUMB_L = tuple(n[:-2] + '_l' for n in THUMB_R)


def build():
    m = U.Model('ultron_sentry', density=3, width=256, seed=1501)
    b = U.Builder(m, U.mats())

    def pair(bone, a, c, mat, rot=None, pivot=None):
        """A cube and its mirror on a centre bone."""
        if rot:
            b.rbox(bone, a, c, mat, rot, pivot)
            pv = None if pivot is None else (-pivot[0], pivot[1], pivot[2])
            b.rbox(bone, (-c[0], a[1], a[2]), (-a[0], c[1], c[2]), mat, (rot[0], -rot[1], -rot[2]), pv)
        else:
            b.box(bone, a, c, mat)
            b.box(bone, (-c[0], a[1], a[2]), (-a[0], c[1], c[2]), mat)

    b.bone('root', None, [0, 0, 0])
    # ---- pelvis ----------------------------------------------------------------------------------
    b.bone('hips', 'root', [0, 18.0, 0])
    b.box('hips', (-2.6, 16.6, -1.6), (2.6, 19.0, 1.6), 'gun')
    b.rbox('hips', (-1.4, 15.2, -2.1), (1.4, 18.6, -1.4), 'chrome', (-8, 0, 0), (0, 18.6, -1.6))   # groin plate
    pair('hips', (-3.25, 16.2, -1.5), (-2.5, 19.0, 1.5), 'chrome_dark', (0, 0, 6), (-2.8, 19.0, 0))   # hip plates
    b.box('hips', (-1.9, 16.5, 1.4), (1.9, 18.8, 2.05), 'steel')
    b.oct('hips', (0, 19.25, 0.1), 1.65, 1.0, 'y', 'ribs')
    # ---- abdomen ---------------------------------------------------------------------------------
    b.bone('abdomen', 'hips', [0, 19.6, 0])
    b.box('abdomen', (-1.0, 19.4, -0.6), (1.0, 23.2, 1.2), 'gun')
    for i, y in enumerate((19.8, 20.85, 21.9)):
        w = 2.1 - 0.12 * i
        b.box('abdomen', (-w, y, -1.45), (w, y + 0.8, 1.0), 'steel' if i % 2 == 0 else 'chrome_dark')
        b.box('abdomen', (-0.62, y + 0.05, 1.0), (0.62, y + 0.72, 1.75), 'chrome_dark')
    pair('abdomen', (-2.35, 19.6, -0.4), (-1.95, 23.0, 0.4), 'cable')
    # ---- chest -----------------------------------------------------------------------------------
    b.bone('chest', 'abdomen', [0, 23.0, 0])
    b.box('chest', (-3.4, 23.0, -1.8), (3.4, 28.6, 1.9), 'gun')
    pair('chest', (-3.65, 25.0, -2.45), (-0.25, 28.4, -1.4), 'chrome', (0, 12, 0), (-1.9, 26.7, -1.9))
    pair('chest', (-3.2, 23.2, -2.05), (-0.35, 25.05, -1.25), 'steel', (6, 8, 0), (-1.8, 24.1, -1.6))
    b.box('chest', (-0.5, 23.4, -2.65), (0.5, 28.1, -1.8), 'chrome_light')
    b.box('chest', (-0.32, 25.7, -2.8), (0.32, 27.1, -2.55), 'core_box')
    b.box('chest', (-2.6, 28.2, -1.6), (2.6, 29.2, 1.4), 'chrome_dark')
    pair('chest', (-3.45, 28.0, -1.2), (-1.2, 29.0, 1.4), 'steel', (0, 0, -10), (-2.3, 28.5, 0))
    b.box('chest', (-3.2, 23.4, 1.7), (3.2, 28.4, 2.55), 'chrome_seam')
    b.box('chest', (-1.8, 24.0, 2.45), (1.8, 27.8, 3.65), 'gun_light')               # thruster pack
    pair('chest', (-1.55, 23.15, 2.6), (-0.4, 24.2, 3.45), 'black')
    pair('chest', (-1.35, 22.95, 2.75), (-0.6, 23.2, 3.3), 'thruster')
    b.box('chest', (-0.5, 24.6, 3.6), (0.5, 27.2, 3.95), 'vent_glow')
    pair('chest', (-4.0, 26.6, -0.95), (-3.3, 28.2, 0.95), 'gun')                    # shoulder sockets
    # ---- neck / head -----------------------------------------------------------------------------
    b.bone('neck', 'chest', [0, 29.0, 0])
    b.box('neck', (-0.8, 28.8, -0.7), (0.8, 31.0, 0.9), 'ribs')
    pair('neck', (-1.25, 29.0, -0.25), (-0.9, 30.8, 0.2), 'chrome_light')
    b.box('neck', (-0.4, 29.0, 0.8), (0.4, 31.0, 1.45), 'cable')
    b.bone('head', 'neck', [0, 31.0, -0.2])
    b.box('head', (-1.9, 32.0, -1.2), (1.9, 35.4, 1.9), 'chrome')                       # cranium
    b.box('head', (-1.6, 31.4, 1.0), (1.6, 34.8, 2.5), 'chrome_dark')                   # back of the skull
    b.box('head', (-1.85, 33.0, -2.4), (1.85, 35.0, -1.15), 'chrome_light')             # upper face
    pair('head', (-1.95, 31.35, -2.3), (-0.45, 33.25, -1.0), 'chrome_light', (0, -14, 9), (-1.2, 32.3, -1.65))   # cheek plates
    pair('head', (-2.05, 34.05, -2.78), (-0.12, 34.72, -1.6), 'chrome_dark', (0, 0, -8), (-1.1, 34.4, -2.2))     # brows
    pair('head', (-1.7, 33.12, -2.6), (-0.32, 33.98, -2.45), 'black', (0, 0, -9), (-1.0, 33.55, -2.5))
    pair('head', (-1.58, 33.3, -2.7), (-0.44, 33.76, -2.56), 'eye', (0, 0, -9), (-1.0, 33.53, -2.63))
    b.box('head', (-0.36, 34.6, -2.62), (0.36, 35.75, 1.6), 'steel')                    # crest ridge
    pair('head', (-2.28, 33.0, -1.0), (-1.86, 35.0, 1.2), 'steel')                       # temple fins
    b.oct('head', (-2.2, 32.9, 0.35), 0.72, 0.5, 'x', 'gun_lit')
    b.oct('head', (2.2, 32.9, 0.35), 0.72, 0.5, 'x', 'gun_lit')
    b.box('head', (-0.95, 31.95, -2.46), (0.95, 32.28, -1.3), 'mouth')
    b.bone('jaw', 'head', [0, 32.2, -0.6])
    b.box('jaw', (-1.25, 30.8, -2.25), (1.25, 32.0, -0.5), 'chrome')
    b.box('jaw', (-0.72, 30.5, -2.5), (0.72, 31.25, -1.5), 'chrome_dark')
    # ---- right arm (mirrored) --------------------------------------------------------------------
    b.bone('arm_r', 'chest', [-4.0, 27.6, 0])
    b.oct('arm_r', (-4.4, 27.4, 0), 1.2, 1.4, 'x', 'gun')
    b.oct('arm_r', (-4.85, 27.55, 0), 1.55, 3.6, 'z', 'chrome')                          # rounded shoulder cap
    b.rbox('arm_r', (-6.7, 25.0, -1.6), (-5.75, 27.3, 1.6), 'chrome_dark', (0, 0, 14), (-4.6, 28.0, 0))
    b.box('arm_r', (-5.2, 21.8, -0.8), (-3.6, 26.4, 0.8), 'gun')
    b.box('arm_r', (-5.5, 22.6, -1.2), (-3.4, 25.8, 0.4), 'steel')
    b.box('arm_r', (-5.1, 22.4, 0.3), (-3.8, 25.4, 1.1), 'chrome_dark')
    b.bone('forearm_r', 'arm_r', [-4.4, 21.8, 0])
    b.oct('forearm_r', (-4.4, 21.6, 0), 0.9, 1.6, 'x', 'gun_light')
    b.box('forearm_r', (-5.2, 16.2, -0.8), (-3.6, 21.2, 0.8), 'gun')
    b.box('forearm_r', (-5.65, 16.8, -1.1), (-4.6, 20.8, 1.1), 'chrome')
    b.box('forearm_r', (-4.6, 17.6, -1.2), (-3.5, 20.6, -0.5), 'steel')
    b.box('forearm_r', (-5.3, 16.0, -0.95), (-3.5, 16.7, 0.95), 'chrome_dark')
    b.bone('hand_r', 'forearm_r', [-4.4, 16.0, 0])
    b.box('hand_r', (-4.9, 13.6, -1.0), (-3.9, 16.0, 1.0), 'steel')
    b.box('hand_r', (-5.15, 13.8, -0.9), (-4.85, 15.8, 0.9), 'chrome')
    b.box('hand_r', (-3.95, 14.3, -0.45), (-3.8, 15.2, 0.45), 'core')
    b.bone('muzzle_r', 'hand_r', [-3.5, 14.75, 0])
    for i, z in enumerate((-0.62, 0.0, 0.62)):
        U.finger_chain(b, 'r', 'hand_r', (-4.4, 13.6, z), FINGERS_R[i], (1.2, 1.0), 0.5, mat='chrome', joint='gun')
    b.bone('thumb_a_r', 'hand_r', [-3.95, 15.0, -0.9])
    b.box('thumb_a_r', (-4.2, 13.9, -1.25), (-3.7, 15.0, -0.75), 'chrome')
    b.bone('thumb_b_r', 'thumb_a_r', [-3.95, 13.9, -1.0])
    b.box('thumb_b_r', (-4.15, 13.0, -1.2), (-3.75, 13.95, -0.8), 'chrome_dark')
    # ---- right leg (mirrored) --------------------------------------------------------------------
    b.bone('leg_r', 'hips', [-2.0, 17.6, 0])
    b.oct('leg_r', (-2.0, 17.4, 0), 1.1, 1.4, 'x', 'gun')
    b.box('leg_r', (-2.9, 10.6, -0.9), (-1.1, 17.0, 0.9), 'gun')
    b.box('leg_r', (-3.0, 11.4, -1.4), (-1.0, 16.4, -0.6), 'chrome')
    b.box('leg_r', (-3.3, 11.8, -0.9), (-2.8, 16.6, 1.0), 'steel')
    b.box('leg_r', (-2.7, 12.0, 0.7), (-1.3, 15.8, 1.3), 'chrome_dark')
    b.bone('shin_r', 'leg_r', [-2.0, 10.4, 0])
    b.oct('shin_r', (-2.0, 10.3, 0), 0.95, 1.5, 'x', 'gun_light')
    b.rbox('shin_r', (-2.8, 9.3, -1.65), (-1.2, 11.4, -0.8), 'chrome_light', (-8, 0, 0))
    b.box('shin_r', (-2.7, 3.0, -0.7), (-1.3, 10.0, 0.9), 'gun')
    b.box('shin_r', (-2.9, 3.6, -1.3), (-1.1, 9.4, -0.5), 'chrome')
    b.box('shin_r', (-2.8, 4.4, 0.7), (-1.2, 8.6, 1.5), 'steel')
    b.box('shin_r', (-2.2, 4.0, 1.4), (-1.8, 9.0, 1.8), 'chrome_light')
    b.bone('foot_r', 'shin_r', [-2.0, 2.6, 0])
    b.oct('foot_r', (-2.0, 2.4, 0), 0.8, 1.4, 'x', 'gun')
    b.box('foot_r', (-3.0, 0.0, -1.8), (-1.0, 1.8, 1.6), 'steel')
    b.box('foot_r', (-2.9, 1.4, -2.0), (-1.1, 2.2, 0.6), 'chrome')
    b.box('foot_r', (-2.6, 0.0, 1.4), (-1.4, 1.2, 2.2), 'gun_light')
    b.bone('toe_r', 'foot_r', [-2.0, 0.8, -1.8])
    b.box('toe_r', (-2.9, 0.0, -3.4), (-1.1, 1.2, -1.8), 'chrome_dark')
    U.mirror_r(m)
    return m


# ---- poses ------------------------------------------------------------------------------------------------
def P(**kw):
    return kw


def pose(p):
    return humanoid(p, FINGERS_R, FINGERS_L, THUMB_R, THUMB_L)


RELAX = P(arm_r=(2, 0, 3), arm_l=(2, 0, -3), elbow_r=10, elbow_l=10, grip_r=0.35, grip_l=0.35)
GUARD = P(pos=(0, -0.7, 0), hips=(4, -6, 0), chest=(4, -10, 0), head=(-6, 14, 0),
          leg_r=(-14, 6, 4), knee_r=20, foot_r=(-6, 0, -4), leg_l=(-4, 4, -4), knee_l=16, foot_l=(-10, 0, 4),
          arm_r=(-38, -8, 16), elbow_r=92, hand_r=(0, 0, -8), grip_r=1.0,
          arm_l=(-52, 6, -10), elbow_l=100, hand_l=(0, 0, 8), grip_l=1.0)


def stride(ph, run=False):
    """Locomotion pose at gait phase ph (0 = right foot strikes in front)."""
    c, s = math.cos(2 * math.pi * ph), math.sin(2 * math.pi * ph)
    swing_amp = 34 if run else 22

    def leg(phase):
        cc = math.cos(2 * math.pi * phase)
        rx = -swing_amp * cc
        f = phase % 1.0
        if f < 0.5:      # stance
            knee = (14 if run else 7) + (8 if run else 5) * math.sin(math.pi * f / 0.5)
            foot = -10 * (1 - f / 0.12) if f < 0.12 else 14 * max(0.0, (f - 0.3) / 0.2)
        else:            # swing
            knee = (14 if run else 7) + (78 if run else 44) * math.sin(math.pi * (f - 0.5) / 0.5)
            foot = 10 * math.sin(math.pi * (f - 0.5) / 0.5) - 6 * (f - 0.5) / 0.5
        return rx, knee, foot

    rr, kr, fr = leg(ph)
    rl, kl, fl = leg(ph + 0.5)
    bob = (0.55 if run else 0.32) * -math.cos(4 * math.pi * ph) + (0.4 if run else 0.0)
    lean = 12 if run else 3
    arm = 30 if run else 18
    elb = 78 if run else 16
    return P(pos=(0, bob - (0.4 if run else 0.15), 0), hips=(lean * 0.4, 7 * c, 3 * s), spine=(lean * 0.3, -4 * c, 0),
             chest=(lean * 0.3 + 1.5 * math.cos(4 * math.pi * ph), -6 * c, -2 * s), head=(-lean * 0.8, 4 * c, 1.5 * s),
             leg_r=(rr, 0, 1), knee_r=kr, foot_r=(fr, 0, 0), leg_l=(rl, 0, -1), knee_l=kl, foot_l=(fl, 0, 0),
             arm_r=(arm * c - (8 if run else 0), 0, 5), elbow_r=elb + (14 if run else 8) * max(0.0, -c),
             arm_l=(-arm * c - (8 if run else 0), 0, -5), elbow_l=elb + (14 if run else 8) * max(0.0, c),
             grip_r=0.9 if run else 0.4, grip_l=0.9 if run else 0.4, hand_r=(-8 if run else 0, 0, 0), hand_l=(-8 if run else 0, 0, 0))


def clips():
    out = []
    # idle: breathing, a slow scan of the head, weight shifting
    c = UClip('idle', 4.0, loop=True)
    for i in range(9):
        t = i * 0.5
        ph = t / 4.0
        br = math.sin(2 * math.pi * ph * 2)
        c.key(t, pose(plus(RELAX, P(chest=(1.2 * br, 0, 0), spine=(0.6 * br, 0, 0), head=(-1.0 * br, 16 * math.sin(2 * math.pi * ph), 0),
                                   pos=(0, 0.12 * br, 0), hips=(0, 0, 1.2 * math.sin(2 * math.pi * ph)),
                                   arm_r=(1.5 * br, 0, 0), arm_l=(1.5 * br, 0, 0)))))
    out.append(c)
    c = UClip('combat_idle', 1.6, loop=True)
    for i in range(5):
        t = i * 0.4
        ph = t / 1.6
        bb = math.sin(2 * math.pi * ph)
        c.key(t, pose(plus(GUARD, P(pos=(0, 0.25 * bb, 0), chest=(1.5 * bb, 2 * math.cos(2 * math.pi * ph), 0),
                                    arm_r=(2 * bb, 0, 0), arm_l=(-2 * bb, 0, 0), head=(0, 3 * math.cos(2 * math.pi * ph), 0)))))
    out.append(c)
    c = UClip('walk', 1.0, loop=True)
    U.loop_keys(c, lambda ph: pose(stride(ph)), 8)
    out.append(c)
    c = UClip('run', 0.6, loop=True)
    U.loop_keys(c, lambda ph: pose(stride(ph, run=True)), 8)
    out.append(c)
    # additive leans (the renderer weights them by the smoothed acceleration and turn rate)
    for name, p in (('accel', P(hips=(9, 0, 0), spine=(5, 0, 0), chest=(5, 0, 0), head=(-12, 0, 0), arm_r=(10, 0, 0), arm_l=(10, 0, 0))),
                    ('decel', P(hips=(-8, 0, 0), spine=(-4, 0, 0), chest=(-4, 0, 0), head=(8, 0, 0), knee_r=10, knee_l=10,
                                arm_r=(-14, 0, 6), arm_l=(-14, 0, -6))),
                    ('turn_l', P(hips=(0, -6, 4), chest=(0, -10, 3), head=(0, -22, 0), leg_l=(0, -8, 0), leg_r=(0, -4, 0))),
                    ('turn_r', P(hips=(0, 6, -4), chest=(0, 10, -3), head=(0, 22, 0), leg_r=(0, 8, 0), leg_l=(0, 4, 0)))):
        c = UClip(name, 1.0)
        q = humanoid(p)
        q = {k: v for k, v in q.items() if (v if not isinstance(v, dict) else v.get('rot', (0, 0, 0))) != (0.0, 0.0, 0.0)}
        c.key(0, q).key(1.0, q)
        out.append(c)
    # strike_a: right hook. anticipation 0-0.18, accelerate into contact at 0.30, follow-through, recover by 0.70
    wind = plus(GUARD, P(chest=(0, 16, 0), hips=(0, 6, 0), arm_r=(-20, 30, 30), elbow_r=110, head=(-4, -8, 0), pos=(0, -0.3, 0)))
    hit = plus(GUARD, P(chest=(6, -30, 0), hips=(4, -14, 0), arm_r=(-88, -34, 10), elbow_r=18, hand_r=(0, 0, -4),
                        leg_r=(6, 0, 0), knee_r=10, foot_r=(14, 0, 0), head=(-8, 22, 0), pos=(0, -0.6, -0.8)))
    over = plus(hit, P(chest=(4, -6, 0), arm_r=(-4, -8, 0), elbow_r=-6))
    c = UClip('strike_a', 0.7, review=0.3)
    c.key(0, pose(GUARD), 'easeOutQuad').key(0.18, pose(wind), 'easeInCubic').key(0.3, pose(hit), 'easeOutCubic') \
        .key(0.4, pose(over), 'easeInOutSine').key(0.7, pose(GUARD))
    out.append(c)
    # strike_b: left backhand, quick
    wind = plus(GUARD, P(chest=(0, -12, 0), arm_l=(-30, 40, 40), elbow_l=120, head=(0, 6, 0)))
    hit = plus(GUARD, P(chest=(4, 26, 0), hips=(0, 10, 0), arm_l=(-84, -46, -30), elbow_l=14, hand_l=(0, 0, 20), head=(-6, -16, 0),
                        pos=(0, -0.4, -0.5)))
    c = UClip('strike_b', 0.55, review=0.2)
    c.key(0, pose(GUARD), 'easeOutQuad').key(0.12, pose(wind), 'easeInCubic').key(0.2, pose(hit), 'easeOutCubic') \
        .key(0.3, pose(plus(hit, P(chest=(0, 6, 0), arm_l=(0, -8, 0)))), 'easeInOutSine').key(0.55, pose(GUARD))
    out.append(c)
    # strike_c: double-fist overhead hammer: big wind-up, slam at 0.55, hold, recover by 1.0
    up = P(pos=(0, 0.4, 0.4), hips=(-6, 0, 0), spine=(-8, 0, 0), chest=(-14, 0, 0), head=(10, 0, 0),
           arm_r=(-168, -10, -6), elbow_r=40, arm_l=(-168, 10, 6), elbow_l=40, grip_r=1, grip_l=1,
           leg_r=(-6, 0, 3), knee_r=8, leg_l=(-6, 0, -3), knee_l=8)
    slam = P(pos=(0, -2.6, -1.4), hips=(22, 0, 0), spine=(14, 0, 0), chest=(18, 0, 0), head=(-26, 0, 0),
             arm_r=(-62, -12, -8), elbow_r=10, arm_l=(-62, 12, 8), elbow_l=10, grip_r=1, grip_l=1,
             leg_r=(-46, 0, 6), knee_r=62, foot_r=(-16, 0, 0), leg_l=(10, 0, -6), knee_l=50, foot_l=(-12, 0, 0))
    c = UClip('strike_c', 1.0, review=0.55)
    c.key(0, pose(GUARD), 'easeOutCubic').key(0.36, pose(up), 'easeInQuart').key(0.55, pose(slam), 'easeOutQuad') \
        .key(0.7, pose(plus(slam, P(pos=(0, -0.3, 0), chest=(3, 0, 0))))).key(1.0, pose(GUARD))
    out.append(c)
    # blast: right palm raised, left hand braces the forearm, charge (tremble), recoil at 0.60
    aim = P(pos=(0, -0.5, 0), hips=(0, -12, 0), chest=(0, -14, 0), head=(0, 24, 0),
            leg_r=(-10, 10, 6), knee_r=16, leg_l=(6, 6, -4), knee_l=12,
            arm_r=(-88, 26, -6), elbow_r=4, hand_r=(-82, 0, 88), grip_r=0.0,
            arm_l=(-52, 56, 26), elbow_l=20, hand_l=(0, 0, -20), grip_l=0.7)
    c = UClip('blast', 1.1, review=0.6)
    c.key(0, pose(GUARD), 'easeOutCubic').key(0.25, pose(aim))
    for i, t in enumerate((0.33, 0.41, 0.49, 0.56)):
        c.key(t, pose(plus(aim, P(arm_r=(0.8 * (-1) ** i, 0, 0.6 * (-1) ** i)))))
    c.key(0.6, pose(plus(aim, P(arm_r=(-14, 0, 0), elbow_r=14, chest=(-6, 0, 0), pos=(0, 0, 0.6)))), 'easeOutCubic') \
        .key(0.75, pose(plus(aim, P(arm_r=(-4, 0, 0))))).key(1.1, pose(GUARD))
    out.append(c)
    # lunge: crouch, spring into a shoulder ram at 0.40, recover
    crouch = plus(GUARD, P(pos=(0, -2.0, 0.6), hips=(16, 0, 0), chest=(10, 0, 0), knee_r=60, knee_l=56, leg_r=(-34, 0, 4), leg_l=(-20, 0, -4),
                           foot_r=(-20, 0, 0), foot_l=(-28, 0, 0)))
    ram = P(pos=(0, -0.4, -2.6), hips=(24, -10, 0), spine=(10, 0, 0), chest=(10, -24, 0), head=(-26, 18, 0),
            arm_r=(-30, 0, 40), elbow_r=110, grip_r=1, arm_l=(-70, 0, -10), elbow_l=90, grip_l=1,
            leg_r=(-56, 0, 4), knee_r=70, foot_r=(-8, 0, 0), leg_l=(30, 0, -4), knee_l=6, foot_l=(20, 0, 0))
    c = UClip('lunge', 0.6, review=0.4)
    c.key(0, pose(GUARD), 'easeOutQuad').key(0.22, pose(crouch), 'easeInCubic').key(0.4, pose(ram), 'easeOutCubic') \
        .key(0.48, pose(plus(ram, P(pos=(0, 0, -0.4))))).key(0.6, pose(GUARD))
    out.append(c)
    # guard (deflector stance): the left forearm raised across the face, the shield emitter forward
    g = plus(GUARD, P(arm_l=(-80, -30, -18), elbow_l=96, hand_l=(0, 0, 30), chest=(0, -4, 0)))
    c = UClip('guard', 1.0, loop=True)
    c.key(0, pose(g)).key(0.5, pose(plus(g, P(arm_l=(-1.5, 0, 0), pos=(0, 0.15, 0))))).key(1.0, pose(g))
    out.append(c)
    # hurt: additive flinch
    c = UClip('hurt', 0.35)
    c.key(0, humanoid(P())).key(0.06, humanoid(P(chest=(-10, 6, 4), head=(-14, 0, 6), spine=(-4, 0, 0), arm_r=(10, 0, 8), arm_l=(10, 0, -8))),
                                 'easeOutCubic').key(0.35, humanoid(P()))
    out.append(c)
    # heavy_hit: knocked back, stumbles two steps, catches itself
    hit1 = P(pos=(0, -0.4, 2.2), hips=(-18, 8, 6), spine=(-10, 0, 0), chest=(-16, 10, 6), head=(-22, -10, 8),
             arm_r=(-60, 0, 50), elbow_r=30, arm_l=(-40, 0, -60), elbow_l=40, grip_r=0.2, grip_l=0.2,
             leg_r=(-30, 0, 6), knee_r=10, foot_r=(-20, 0, 0), leg_l=(14, 0, -4), knee_l=30)
    stumble = P(pos=(0, -1.6, 3.4), hips=(10, 4, 0), spine=(8, 0, 0), chest=(10, 0, 0), head=(-14, 0, 0),
                arm_r=(-30, 0, 30), elbow_r=40, arm_l=(-30, 0, -30), elbow_l=40,
                leg_r=(16, 0, 6), knee_r=50, foot_r=(-10, 0, 0), leg_l=(-50, 0, -6), knee_l=60, foot_l=(-6, 0, 0))
    c = UClip('heavy_hit', 1.0, review=0.12)
    c.key(0, pose(GUARD), 'easeOutCubic').key(0.12, pose(hit1), 'easeInOutSine').key(0.45, pose(stumble), 'easeInOutSine') \
        .key(0.75, pose(plus(GUARD, P(pos=(0, -0.6, 0))))).key(1.0, pose(GUARD))
    out.append(c)
    # land: absorb and rise
    low = P(pos=(0, -3.2, 0), hips=(20, 0, 0), chest=(10, 0, 0), head=(-20, 0, 0), leg_r=(-40, 0, 8), knee_r=78, foot_r=(-38, 0, 0),
            leg_l=(-40, 0, -8), knee_l=78, foot_l=(-38, 0, 0), arm_r=(-30, 0, 34), elbow_r=40, arm_l=(-30, 0, -34), elbow_l=40)
    c = UClip('land', 0.5, review=0.12)
    c.key(0, pose(P(knee_r=20, knee_l=20, leg_r=(-10, 0, 0), leg_l=(-10, 0, 0))), 'easeOutCubic').key(0.12, pose(low), 'easeInOutSine') \
        .key(0.5, pose(RELAX))
    out.append(c)
    c = UClip('fall', 0.8, loop=True)
    f0 = P(arm_r=(-30, 0, 60), arm_l=(-30, 0, -60), elbow_r=20, elbow_l=20, leg_r=(-20, 0, 4), knee_r=30, leg_l=(6, 0, -4), knee_l=40,
           head=(10, 0, 0), chest=(-6, 0, 0))
    c.key(0, pose(f0)).key(0.4, pose(plus(f0, P(arm_r=(-10, 0, 8), arm_l=(-10, 0, -8), leg_r=(10, 0, 0), leg_l=(-10, 0, 0))))).key(0.8, pose(f0))
    out.append(c)
    # death (30 ticks): systems fail, knees buckle, falls forward onto its front
    sp = P(pos=(0, -0.5, 0), chest=(-12, -16, 8), head=(-30, 30, 14), arm_r=(-20, 0, 40), elbow_r=60, arm_l=(10, 0, -20), elbow_l=20,
           grip_r=0.8, grip_l=0.1, knee_r=10)
    kneel = P(pos=(0, -8.0, -1.0), hips=(20, 0, 0), spine=(14, 0, 0), chest=(20, 0, 0), head=(30, -10, 0),
              leg_r=(-30, 0, 4), knee_r=110, foot_r=(40, 0, 0), leg_l=(-10, 0, -4), knee_l=100, foot_l=(30, 0, 0),
              arm_r=(-10, 0, 10), elbow_r=20, arm_l=(-10, 0, -10), elbow_l=20, grip_r=0.3, grip_l=0.3)
    down = P(pos=(0, -13.5, -9.0), root=(82, 0, 0), hips=(0, 0, 0), chest=(4, 0, 0), head=(16, 20, 0),
             leg_r=(-6, 0, 4), knee_r=12, foot_r=(30, 0, 0), leg_l=(4, 0, -6), knee_l=22, foot_l=(30, 0, 0),
             arm_r=(-160, 0, 10), elbow_r=10, arm_l=(-30, 0, -30), elbow_l=40, grip_r=0.2, grip_l=0.4)
    c = UClip('death', 1.5, review=0.5)
    c.key(0, pose(RELAX), 'easeOutCubic').key(0.15, pose(sp)).key(0.25, pose(plus(sp, P(head=(10, -40, 0), chest=(4, 20, -10))))) \
        .key(0.55, pose(kneel), 'easeInQuad').key(0.95, pose(down), 'easeOutQuad').key(1.05, pose(plus(down, P(pos=(0, 0.6, 0))))) \
        .key(1.5, pose(down))
    c.keep.update({'root'})
    out.append(c)
    # fabricate (3 s): folded on the foundry pad, limbs lock in, stands, the head comes up and the eyes light
    fold = P(pos=(0, -11.0, 0), hips=(40, 0, 0), spine=(30, 0, 0), chest=(30, 0, 0), head=(50, 0, 0),
             leg_r=(-100, 0, 10), knee_r=150, foot_r=(-50, 0, 0), leg_l=(-100, 0, -10), knee_l=150, foot_l=(-50, 0, 0),
             arm_r=(-20, 0, 20), elbow_r=140, arm_l=(-20, 0, -20), elbow_l=140, grip_r=1, grip_l=1)
    c = UClip('fabricate', 3.0, review=1.2)
    c.key(0, pose(fold)).key(0.9, pose(plus(fold, P(arm_r=(0, 0, 10), arm_l=(0, 0, -10)))), 'easeInCubic') \
        .key(1.0, pose(fold), 'easeInOutSine') \
        .key(1.9, pose(P(pos=(0, -2.0, 0), hips=(16, 0, 0), chest=(10, 0, 0), head=(40, 0, 0), knee_r=40, knee_l=40, leg_r=(-24, 0, 4),
                         leg_l=(-24, 0, -4), foot_r=(-16, 0, 0), foot_l=(-16, 0, 0), arm_r=(0, 0, 8), arm_l=(0, 0, -8), elbow_r=30, elbow_l=30)),
                     'easeInOutSine') \
        .key(2.4, pose(plus(RELAX, P(head=(30, 0, 0)))), 'easeInCubic').key(2.6, pose(plus(RELAX, P(head=(-6, 0, 0)))), 'easeOutCubic') \
        .key(3.0, pose(RELAX))
    out.append(c)
    return out


def main():
    m = build()
    m.write(GEO, TEX, GLOW)
    n = sum(len(bn.cubes) for bn in m.bones)
    cl = clips()
    size = U.write_anims(ANIM, cl)
    print('sentry: %d bones, %d cubes, atlas %dx%d; %d clips (%d bytes)' % (len(m.bones), n, m.width, m.height, len(cl), size))
    U.finalize_assets([GEO, ANIM], [TEX, GLOW])
    if '--preview' in sys.argv:
        U.preview_all('sentry', GEO, TEX, GLOW, cl)


if __name__ == '__main__':
    main()
