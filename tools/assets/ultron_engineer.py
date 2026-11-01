#!/usr/bin/env python3
"""Ultron engineer-repairer: a low four-legged walker. Armoured chassis with hazard-striped flanks, a turret head
with the red optic and amber work lights, a jointed welding arm (glowing torch) on the right, a jointed claw arm
with two pincers and a data probe on the left, a seed canister with a red core on the back and a seed driver
spike under the nose. Spider legs (hip, thigh, shin, foot pad). Geo (~140 cubes), metal atlas + red/amber glow
atlas, and every clip: idle, walk, run, accel/decel/turn_l/turn_r (additive), weld (loop), hack, plant, hurt
(additive), heavy_hit, death, fabricate. Contact frames match UltronMove (HACK 40 ticks: capture 30; PLANT 24:
the spike lands at 14; death 30). Own work, CC0.

Run: python3 tools/assets/ultron_engineer.py [--preview]
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ultron_lib as U  # noqa: E402
from ultron_lib import UClip  # noqa: E402

GEO = 'geo/ultron/engineer.geo.json'
ANIM = 'animations/ultron/engineer.animation.json'
TEX = 'textures/entity/ultron/engineer.png'
GLOW = 'textures/entity/ultron/engineer_glow.png'
LEGS = ('f', 'b')


def strut(b, bone, p0, p1, w, h, mat, ext=0.0):
    """A w x h bar from p0 to p1 (any direction): one box along X turned onto the segment."""
    p0, p1 = np.array(p0, float), np.array(p1, float)
    d = p1 - p0
    L = float(np.linalg.norm(d))
    u = d / L
    c = (p0 + p1) / 2
    if u[0] >= 0:
        ry, rz = -math.asin(u[2]), math.atan2(-u[1], u[0])
    else:
        ry, rz = math.asin(u[2]), math.atan2(u[1], -u[0])
    half = L / 2 + ext
    b.rbox(bone, (c[0] - half, c[1] - h / 2, c[2] - w / 2), (c[0] + half, c[1] + h / 2, c[2] + w / 2), mat,
           (0, math.degrees(ry), math.degrees(rz)), tuple(c))


def build():
    m = U.Model('ultron_engineer', density=3, width=256, seed=1901)
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
    b.bone('body', 'root', [0, 10.0, 0])
    # ---- chassis ------------------------------------------------------------------------------------
    b.box('body', (-4.2, 8.0, -5.2), (4.2, 12.0, 4.6), 'gun')
    b.box('body', (-4.6, 11.4, -4.6), (4.6, 12.8, 4.2), 'chrome_seam')                        # top shell
    b.rbox('body', (-3.8, 11.2, -6.2), (3.8, 12.4, -4.2), 'chrome', (-24, 0, 0), (0, 11.8, -5.0))   # nose plate
    pair('body', (-5.0, 8.6, -4.4), (-4.2, 11.6, 3.8), 'hazard')                              # hazard flanks
    pair('body', (-5.2, 9.4, -3.6), (-4.8, 10.8, 3.0), 'gun_dark')
    b.box('body', (-3.6, 7.4, -4.4), (3.6, 8.2, 3.8), 'steel_dark')                           # belly
    b.box('body', (-3.4, 8.6, 4.4), (3.4, 11.6, 5.4), 'vent_glow')                            # rear vent
    pair('body', (-4.4, 8.4, -5.8), (-3.4, 9.4, -4.8), 'amber')                               # work lights
    for z in (-3.0, 2.4):
        pair('body', (-4.4, 9.0, z - 1.2), (-3.6, 11.0, z + 1.2), 'gun_light')                 # hip housings
    # seed canister (back) and driver spike (nose)
    b.bone('canister', 'body', [0, 12.8, 2.0])
    b.oct('canister', (0, 14.6, 2.0), 1.9, 3.8, 'y', 'chrome_dark', 'steel')
    b.box('canister', (-0.9, 13.3, 0.15), (0.9, 15.9, 0.6), 'core_box')
    b.box('canister', (-1.4, 16.4, 0.6), (1.4, 17.0, 3.4), 'chrome_light')
    b.box('canister', (-0.4, 16.9, 1.6), (0.4, 17.6, 2.4), 'glow_soft')
    b.bone('spike', 'body', [0, 8.2, -4.0])
    b.box('spike', (-0.9, 7.6, -4.9), (0.9, 9.0, -3.1), 'gun')
    b.box('spike', (-0.45, 3.8, -4.45), (0.45, 7.8, -3.55), 'chrome_light')
    b.box('spike', (-0.3, 2.9, -4.3), (0.3, 3.9, -3.7), 'core')
    # ---- turret head ------------------------------------------------------------------------------------
    b.bone('head', 'body', [0, 12.6, -3.6])
    b.box('head', (-2.3, 12.4, -7.0), (2.3, 15.2, -2.8), 'chrome')
    b.box('head', (-2.5, 14.8, -7.4), (2.5, 15.6, -3.2), 'chrome_light')
    b.box('head', (-1.4, 12.9, -7.3), (1.4, 14.7, -7.0), 'black')
    b.box('head', (-1.2, 13.1, -7.45), (1.2, 14.5, -7.15), 'lens')
    pair('head', (-2.6, 13.0, -6.6), (-2.2, 14.6, -3.4), 'steel')
    b.box('head', (-0.2, 15.5, -4.0), (0.2, 18.2, -3.6), 'chrome_light')                       # whip antenna
    b.box('head', (-0.35, 18.1, -4.15), (0.35, 18.6, -3.45), 'amber')
    # ---- welding arm (right front): shoulder, upper arm, forearm, torch ----------------------------------
    b.bone('welder', 'body', [-3.0, 11.0, -4.6])
    b.oct('welder', (-3.0, 11.0, -4.6), 1.0, 1.2, 'x', 'gun')
    b.box('welder', (-3.6, 11.0, -5.2), (-2.4, 16.0, -4.0), 'steel')
    b.box('welder', (-3.8, 12.0, -5.4), (-2.2, 15.0, -4.6), 'hazard')
    b.bone('welder_fore', 'welder', [-3.0, 16.0, -4.6])
    b.oct('welder_fore', (-3.0, 16.0, -4.6), 0.8, 1.4, 'x', 'gun_light')
    b.box('welder_fore', (-3.5, 15.6, -10.4), (-2.5, 16.5, -4.4), 'chrome_dark')
    b.box('welder_fore', (-3.2, 16.4, -9.0), (-2.8, 16.8, -5.0), 'cable')
    b.bone('torch', 'welder_fore', [-3.0, 16.0, -10.4])
    b.box('torch', (-3.6, 15.4, -11.4), (-2.4, 16.6, -10.2), 'gun')
    b.box('torch', (-3.25, 15.2, -12.8), (-2.75, 15.7, -11.2), 'steel_dark')
    b.box('torch', (-3.2, 15.0, -13.2), (-2.8, 15.4, -12.7), 'amber')
    # ---- claw arm (left front): shoulder, upper arm, forearm, pincers, data probe ------------------------
    b.bone('claw', 'body', [3.0, 11.0, -4.6])
    b.oct('claw', (3.0, 11.0, -4.6), 1.0, 1.2, 'x', 'gun')
    b.box('claw', (2.4, 11.0, -5.2), (3.6, 16.0, -4.0), 'steel')
    b.box('claw', (2.2, 12.0, -5.4), (3.8, 15.0, -4.6), 'chrome_dark')
    b.bone('claw_fore', 'claw', [3.0, 16.0, -4.6])
    b.oct('claw_fore', (3.0, 16.0, -4.6), 0.8, 1.4, 'x', 'gun_light')
    b.box('claw_fore', (2.5, 15.6, -10.0), (3.5, 16.5, -4.4), 'chrome')
    b.box('claw_fore', (2.4, 15.4, -10.6), (3.6, 16.7, -9.8), 'gun')
    b.bone('probe', 'claw_fore', [3.0, 16.0, -10.4])
    b.box('probe', (2.8, 15.85, -12.6), (3.2, 16.25, -10.4), 'chrome_light')
    b.box('probe', (2.75, 15.8, -13.0), (3.25, 16.3, -12.5), 'core_box')
    for name, y0, y1, rot in (('pincer_a', 16.5, 17.2, -14), ('pincer_b', 14.9, 15.5, 14)):
        b.bone(name, 'claw_fore', [3.0, 16.0, -10.4])
        b.rbox(name, (2.45, y0, -13.0), (3.55, y1, -10.4), 'chrome_light', (rot, 0, 0), (3.0, 16.0, -10.4))
    # ---- legs (mirrored): hip yoke, thigh up to a high knee, shin down to a foot pad --------------------
    for k, (z, kz, fz) in enumerate(((-3.0, -7.0, -8.2), (2.4, 6.4, 7.6))):
        hip, shin, foot = 'hip_%s_r' % LEGS[k], 'shin_%s_r' % LEGS[k], 'foot_%s_r' % LEGS[k]
        h0 = (-4.4, 10.0, z)
        kn = (-9.0, 13.2, kz)
        ft = (-10.4, 0.9, fz)
        b.bone(hip, 'body', list(h0))
        b.oct(hip, h0, 1.1, 1.4, 'x', 'gun')
        strut(b, hip, h0, kn, 1.6, 1.8, 'chrome')
        strut(b, hip, (h0[0] - 0.4, h0[1] + 1.1, h0[2]), (kn[0] + 0.3, kn[1] + 1.0, kn[2]), 0.7, 0.6, 'cable')
        b.bone(shin, hip, list(kn))
        b.oct(shin, kn, 1.0, 1.9, 'x', 'gun_light')
        strut(b, shin, kn, ft, 1.3, 1.5, 'steel', ext=0.2)
        strut(b, shin, (kn[0] - 0.7, kn[1] - 0.6, kn[2]), ((kn[0] + ft[0]) / 2 - 0.7, (kn[1] + ft[1]) / 2, (kn[2] + ft[2]) / 2), 1.0, 1.9, 'chrome_dark')
        b.box(shin, (kn[0] - 0.7, kn[1] + 0.6, kn[2] - 0.7), (kn[0] + 0.7, kn[1] + 1.3, kn[2] + 0.7), 'gun_lit')
        b.bone(foot, shin, list(ft))
        b.box(foot, (ft[0] - 1.2, 0.0, ft[2] - 1.2), (ft[0] + 1.2, 0.8, ft[2] + 1.2), 'gun')
        b.box(foot, (ft[0] - 0.8, 0.7, ft[2] - 0.8), (ft[0] + 0.8, 1.4, ft[2] + 0.8), 'chrome_dark')
    U.mirror_r(m)
    return m


# ---- poses ------------------------------------------------------------------------------------------------
def legs_pose(fr=(0, 0, 0, 0), fl=(0, 0, 0, 0), br=(0, 0, 0, 0), bl=(0, 0, 0, 0)):
    """Per leg (swing, lift, knee, foot): swing > 0 forward, lift > 0 raises the knee, knee > 0 tucks the foot in.
    Right legs extend to -X, left legs mirror (ry, rz signs flip)."""
    p = {}
    for name, (sw, lift, knee, ft) in (('f_r', fr), ('f_l', fl), ('b_r', br), ('b_l', bl)):
        s = 1 if name.endswith('_r') else -1
        p['hip_' + name] = (0.0, -s * sw, s * lift)
        p['shin_' + name] = (0.0, 0.0, -s * knee)
        p['foot_' + name] = (0.0, 0.0, -s * ft)
    return p


def pose(body=(0, 0, 0), pos=(0, 0, 0), head=(0, 0, 0), legs=None, welder=(0, 0, 0), welder_fore=(0, 0, 0), torch=(0, 0, 0),
         claw=(0, 0, 0), claw_fore=(0, 0, 0), pincers=0.0, probe=0.0, spike=3.6, canister=(0, 0, 0), root=(0, 0, 0), root_pos=(0, 0, 0)):
    """welder/claw: shoulder rotations (rx < 0 swings the upper arm forward); *_fore: elbow; pincers: opening deg;
    probe: extension px; spike: retraction px (0 = driven down)."""
    p = {'root': {'rot': tuple(root), 'pos': tuple(root_pos)},
         'body': {'rot': tuple(body), 'pos': tuple(pos)},
         'head': tuple(head),
         'welder': tuple(welder), 'welder_fore': tuple(welder_fore), 'torch': tuple(torch),
         'claw': tuple(claw), 'claw_fore': tuple(claw_fore),
         'pincer_a': (-pincers, 0.0, 0.0), 'pincer_b': (pincers, 0.0, 0.0),
         'probe': {'rot': (0.0, 0.0, 0.0), 'pos': (0.0, 0.0, -probe)},
         'spike': {'rot': (0.0, 0.0, 0.0), 'pos': (0.0, spike, 0.0)},
         'canister': tuple(canister)}
    p.update(legs or legs_pose())
    return p


# Folded arms at rest: upper arms raised (rx 0 = straight up), forearms pointing forward; tucked toward the body
REST_ARMS = dict(welder=(18, 0, 0), welder_fore=(30, 0, 0), claw=(18, 0, 0), claw_fore=(30, 0, 0))


def gait(ph, run=False):
    """Trot: diagonal pairs (front right + back left, front left + back right) swing together."""
    amp = 22 if run else 16

    def leg(phase):
        f = phase % 1.0
        sw = amp * math.cos(2 * math.pi * f)
        lift = (18 if run else 13) * max(0.0, math.sin(2 * math.pi * f))
        knee = (10 if run else 7) * max(0.0, math.sin(2 * math.pi * f))
        return (sw, lift, knee, -knee * 0.5)

    fr, bl = leg(ph), leg(ph)
    fl, br = leg(ph + 0.5), leg(ph + 0.5)
    bob = (0.5 if run else 0.3) * math.cos(4 * math.pi * ph)
    roll = 2.5 * math.sin(2 * math.pi * ph)
    return dict(body=(3 if run else 1, 0, roll), pos=(0, bob - (0.4 if run else 0.1), 0), legs=legs_pose(fr, fl, br, bl),
                head=(-3 if run else -1, 4 * math.sin(2 * math.pi * ph), 0),
                canister=(2 * math.sin(4 * math.pi * ph), 0, 0))


def clips():
    out = []
    c = UClip('idle', 4.0, loop=True)
    for i in range(9):
        t = i * 0.5
        ph = 2 * math.pi * i / 8
        c.key(t, pose(body=(0.8 * math.sin(2 * ph), 0, 0.6 * math.sin(ph)), pos=(0, 0.15 * math.sin(2 * ph), 0),
                      head=(4 * math.sin(ph * 2), 30 * math.sin(ph), 0), **REST_ARMS,
                      legs=legs_pose(fr=(0, 0, 0.5 * math.sin(ph), 0), br=(0, 0, -0.5 * math.sin(ph), 0)),
                      pincers=4 + 4 * math.sin(3 * ph)))
    out.append(c)
    c = UClip('walk', 0.9, loop=True)
    U.loop_keys(c, lambda ph: pose(**gait(ph), **REST_ARMS), 8)
    out.append(c)
    c = UClip('run', 0.55, loop=True)
    U.loop_keys(c, lambda ph: pose(**gait(ph, run=True), welder=(30, 0, 0), welder_fore=(20, 0, 0), claw=(30, 0, 0), claw_fore=(20, 0, 0)), 8)
    out.append(c)
    for name, p in (('accel', {'body': (6, 0, 0), 'head': (-6, 0, 0)}), ('decel', {'body': (-6, 0, 0), 'head': (5, 0, 0)}),
                    ('turn_l', {'body': (0, -6, 4), 'head': (0, -24, 0)}), ('turn_r', {'body': (0, 6, -4), 'head': (0, 24, 0)})):
        c = UClip(name, 1.0)
        c.key(0, p).key(1.0, p)
        out.append(c)
    # weld (loop while welding): torch arm reaches forward and up toward the work, small corrections, the claw
    # steadies, the head follows the torch
    reach = dict(welder=(-62, 0, 8), welder_fore=(-16, 0, 0), torch=(-10, 0, 0), claw=(-20, 0, 0), claw_fore=(20, 0, 0), pincers=18,
                 head=(-10, -6, 0), body=(4, 0, 0), pos=(0, -0.4, 0))
    c = UClip('weld', 1.6, loop=True)
    for i, (dw, df, dt) in enumerate(((0, 0, 0), (-4, 3, 6), (3, -2, -4), (-2, 4, 3), (0, 0, 0))):
        q = dict(reach)
        q['welder'] = (reach['welder'][0] + dw, 2 * math.sin(i), reach['welder'][2])
        q['welder_fore'] = (reach['welder_fore'][0] + df, 0, 0)
        q['torch'] = (reach['torch'][0] + dt, 0, 0)
        c.key(i * 0.4, pose(**q))
    out.append(c)
    # hack (40 ticks, capture at 1.5 s): crouch, claw reaches out, pincers open, probe extends and plugs in,
    # data jitter, the jolt of the capture, unplug, recover
    reach_c = dict(claw=(-74, 0, -6), claw_fore=(-10, 0, 0), welder=(10, 0, 0), welder_fore=(30, 0, 0), head=(-8, 8, 0),
                   body=(8, 0, 0), pos=(0, -1.0, -0.4))
    c = UClip('hack', 2.0, review=1.5)
    c.key(0, pose(**REST_ARMS), 'easeOutCubic').key(0.35, pose(**reach_c, pincers=40, probe=0.0), 'easeInOutSine') \
        .key(0.55, pose(**reach_c, pincers=40, probe=2.2), 'easeOutBack')
    for i, t in enumerate((0.7, 0.85, 1.0, 1.15, 1.3, 1.42)):
        j = (-1) ** i
        c.key(t, pose(**dict(reach_c, head=(-8 + 2 * j, 8 + 3 * j, 0)), pincers=30, probe=2.2 + 0.2 * j, canister=(3 * j, 0, 0)))
    c.key(1.5, pose(**dict(reach_c, body=(2, 0, 0), pos=(0, -0.6, 0.6)), pincers=46, probe=1.6), 'easeOutCubic') \
        .key(1.7, pose(**reach_c, pincers=20, probe=0.0), 'easeInOutSine').key(2.0, pose(**REST_ARMS))
    out.append(c)
    # plant (24 ticks, spike lands at 0.70): legs spread, chassis rises, then slams the driver spike down
    spread = legs_pose(fr=(6, -8, -10, 6), fl=(6, -8, -10, 6), br=(-6, -8, -10, 6), bl=(-6, -8, -10, 6))
    c = UClip('plant', 1.2, review=0.7)
    c.key(0, pose(**REST_ARMS), 'easeOutCubic').key(0.35, pose(**REST_ARMS, legs=spread, pos=(0, 1.2, 0), body=(-6, 0, 0), spike=3.6,
                                                             canister=(-20, 0, 0)), 'easeInCubic') \
        .key(0.55, pose(**REST_ARMS, legs=spread, pos=(0, 1.6, 0), body=(-8, 0, 0), spike=2.0, canister=(-30, 0, 0)), 'easeInQuart') \
        .key(0.7, pose(**REST_ARMS, legs=spread, pos=(0, -1.6, 0), body=(10, 0, 0), spike=-2.4, canister=(10, 0, 0)), 'easeOutQuad') \
        .key(0.85, pose(**REST_ARMS, legs=spread, pos=(0, -1.2, 0), body=(8, 0, 0), spike=-2.4)) \
        .key(1.2, pose(**REST_ARMS))
    out.append(c)
    c = UClip('hurt', 0.35)
    c.key(0, {'body': (0, 0, 0)}).key(0.06, {'body': (-10, 8, 12), 'head': (-14, 10, 0)}, 'easeOutCubic').key(0.35, {'body': (0, 0, 0), 'head': (0, 0, 0)})
    out.append(c)
    # heavy_hit (20 ticks): flipped half over, legs flail, rights itself
    c = UClip('heavy_hit', 1.0, review=0.2)
    flail = legs_pose(fr=(20, 30, 20, 0), fl=(-10, 40, 30, 0), br=(-20, 30, 20, 0), bl=(14, 36, 26, 0))
    c.key(0, pose(**REST_ARMS), 'easeOutCubic').key(0.2, pose(body=(-24, 10, 46), pos=(0, 3.0, 2.0), legs=flail, welder=(40, 0, 20),
                                                              claw=(40, 0, -20), head=(-20, 0, 0)), 'easeInOutSine') \
        .key(0.5, pose(body=(8, -6, -12), pos=(0, -0.8, 1.2), legs=legs_pose(fr=(0, -10, -10, 0), fl=(0, -10, -10, 0), br=(0, -10, -10, 0), bl=(0, -10, -10, 0)),
                       **REST_ARMS), 'easeOutQuad') \
        .key(1.0, pose(**REST_ARMS))
    out.append(c)
    # death (30 ticks): sparks, legs buckle one after another, chassis drops, arms sag, optic dies
    c = UClip('death', 1.5, review=0.9)
    c.key(0, pose(**REST_ARMS), 'easeOutCubic').key(0.2, pose(body=(-8, 10, 14), pos=(0, 0.8, 0), **REST_ARMS, head=(-20, 30, 0),
                                                              legs=legs_pose(fr=(10, 10, 10, 0)))) \
        .key(0.55, pose(body=(10, -6, 18), pos=(0, -3.0, 0), welder=(60, 0, 20), welder_fore=(40, 0, 0), claw=(20, 0, 0), claw_fore=(50, 0, 0),
                        head=(20, -20, 0), legs=legs_pose(fr=(0, -30, -30, 10), br=(0, -26, -28, 10))), 'easeInQuad') \
        .key(0.9, pose(body=(14, -6, -6), pos=(0, -7.2, 0), welder=(80, 0, 30), welder_fore=(60, 0, 0), claw=(70, 0, -30), claw_fore=(60, 0, 0),
                       head=(30, -26, 0), pincers=30, spike=3.6,
                       legs=legs_pose(fr=(4, -50, -46, 20), fl=(-6, -50, -46, 20), br=(6, -48, -44, 20), bl=(-4, -52, -46, 20))), 'easeOutQuad') \
        .key(1.0, pose(body=(12, -6, -6), pos=(0, -6.6, 0), welder=(80, 0, 30), welder_fore=(60, 0, 0), claw=(70, 0, -30), claw_fore=(60, 0, 0),
                       head=(34, -26, 0), pincers=30,
                       legs=legs_pose(fr=(4, -50, -46, 20), fl=(-6, -50, -46, 20), br=(6, -48, -44, 20), bl=(-4, -52, -46, 20)))) \
        .key(1.5, pose(body=(14, -6, -6), pos=(0, -7.2, 0), welder=(84, 0, 30), welder_fore=(64, 0, 0), claw=(72, 0, -30), claw_fore=(62, 0, 0),
                       head=(36, -26, 0), pincers=34,
                       legs=legs_pose(fr=(4, -52, -48, 20), fl=(-6, -52, -48, 20), br=(6, -50, -46, 20), bl=(-4, -54, -48, 20))))
    out.append(c)
    # fabricate (60 ticks): curled up, legs unfold pair by pair, stands, the head lifts and the optic opens
    curl = legs_pose(fr=(30, 60, 70, 0), fl=(30, 60, 70, 0), br=(-30, 60, 70, 0), bl=(-30, 60, 70, 0))
    c = UClip('fabricate', 3.0, review=1.6)
    c.key(0, pose(pos=(0, -6.5, 0), legs=curl, welder=(90, 0, 0), welder_fore=(80, 0, 0), claw=(90, 0, 0), claw_fore=(80, 0, 0), head=(30, 0, 0), spike=3.6)) \
        .key(1.0, pose(pos=(0, -6.5, 0), legs=curl, welder=(90, 0, 0), welder_fore=(80, 0, 0), claw=(90, 0, 0), claw_fore=(80, 0, 0), head=(30, 0, 0)), 'easeInOutSine') \
        .key(1.5, pose(pos=(0, -6.5, 0), legs=legs_pose(fr=(0, -20, -10, 0), fl=(0, -20, -10, 0), br=(-30, 60, 70, 0), bl=(-30, 60, 70, 0)),
                       welder=(90, 0, 0), welder_fore=(80, 0, 0), claw=(90, 0, 0), claw_fore=(80, 0, 0), head=(30, 0, 0)), 'easeOutBack') \
        .key(1.9, pose(pos=(0, -5.0, 0), legs=legs_pose(fr=(0, -30, -20, 0), fl=(0, -30, -20, 0), br=(0, -30, -20, 0), bl=(0, -30, -20, 0)),
                       welder=(90, 0, 0), welder_fore=(80, 0, 0), claw=(90, 0, 0), claw_fore=(80, 0, 0), head=(30, 0, 0)), 'easeOutBack') \
        .key(2.4, pose(pos=(0, 0.3, 0), **REST_ARMS, head=(20, 0, 0)), 'easeInOutSine') \
        .key(2.6, pose(**REST_ARMS, head=(-8, 0, 0)), 'easeOutCubic').key(3.0, pose(**REST_ARMS))
    out.append(c)
    return out


def main():
    m = build()
    m.write(GEO, TEX, GLOW)
    n = sum(len(bn.cubes) for bn in m.bones)
    cl = clips()
    size = U.write_anims(ANIM, cl)
    print('engineer: %d bones, %d cubes, atlas %dx%d; %d clips (%d bytes)' % (len(m.bones), n, m.width, m.height, len(cl), size))
    U.finalize_assets([GEO, ANIM], [TEX, GLOW])
    if '--preview' in sys.argv:
        U.preview_all('engineer', GEO, TEX, GLOW, cl)


if __name__ == '__main__':
    main()
