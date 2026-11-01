#!/usr/bin/env python3
"""Ultron Prime, the boss: 2.9-block machined humanoid. Geo (~330 cubes): a layered head (cranium, crown
crest with back fins, flaring temple fins with ear discs, forehead plate, titanium face plates, nose ridge,
recessed glowing eyes under moving lids and brows, cheek plates, upper lip, mouth corners, a hinged jaw with
lower lip and chin, the red mouth light behind), a mechanical two-stage neck (ribbed vertebrae, telescoping
side pistons, braided cables), segmented armour (overlapping pectoral plates that breathe, three rows of
abdominal scales, layered back plates and spine ridge, layered pauldrons that flare), jointed hands (four
two-segment fingers and a thumb, palm repulsors), back thruster vanes and sole thrusters (separate glow
bones the renderer shows only in flight), the chest core. Clips: idle, combat_idle, overdrive_idle, walk,
run, accel/decel/turn_l/turn_r (additive), hover, fly, land, fall, talk (additive face), flare (additive
armour), hurt (additive), stagger, jab_cross, uppercut, backhand, palm_blast, beam, slam, dive, dash, summon,
absorb, transition_air, transition_overdrive, assemble, death, ascend. Contacts match UltronMove. CC0.

Run: python3 tools/assets/ultron_prime.py [--preview]
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ultron_lib as U  # noqa: E402
from ultron_lib import UClip, humanoid, plus  # noqa: E402

GEO = 'geo/ultron/prime.geo.json'
ANIM = 'animations/ultron/prime.animation.json'
TEX = 'textures/entity/ultron/prime.png'
GLOW = 'textures/entity/ultron/prime_glow.png'
FINGERS_R = [('fi_a_r', 'fi_b_r'), ('fm_a_r', 'fm_b_r'), ('fr_a_r', 'fr_b_r'), ('fp_a_r', 'fp_b_r')]
FINGERS_L = [tuple(n[:-2] + '_l' for n in ch) for ch in FINGERS_R]
THUMB_R = ('th_a_r', 'th_b_r')
THUMB_L = tuple(n[:-2] + '_l' for n in THUMB_R)
#: glow-only bones the renderer hides unless he flies (thruster flames)
JETS = ('jetflame_r', 'jetflame_l', 'sole_r', 'sole_l')


def build():
    m = U.Model('ultron_prime', density=3, width=512, seed=2015)
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
    b.bone('hips', 'root', [0, 23.6, 0])
    b.box('hips', (-3.0, 22.0, -1.9), (3.0, 25.0, 1.9), 'gun')
    b.box('hips', (-3.3, 24.0, -2.2), (3.3, 25.2, 2.2), 'chrome_dark')
    b.rbox('hips', (-1.5, 20.6, -2.55), (1.5, 24.2, -1.8), 'chrome', (-10, 0, 0), (0, 24.2, -2.1))
    b.box('hips', (-0.5, 21.2, -2.85), (0.5, 23.8, -2.4), 'steel')
    pair('hips', (-3.85, 20.8, -1.8), (-2.9, 24.6, 1.8), 'chrome', (0, 0, 8), (-3.3, 24.6, 0))
    pair('hips', (-4.05, 21.4, -1.2), (-3.7, 23.8, 1.2), 'steel_dark', (0, 0, 8), (-3.3, 24.6, 0))
    b.box('hips', (-2.4, 21.6, 1.7), (2.4, 24.6, 2.5), 'steel')
    b.oct('hips', (0, 25.6, 0.1), 2.0, 1.4, 'y', 'ribs')
    # ---- abdomen: three rows of overlapping scales -------------------------------------------------
    b.bone('abdomen', 'hips', [0, 25.8, 0])
    b.box('abdomen', (-1.4, 25.4, -0.8), (1.4, 29.8, 1.4), 'gun')
    for i, y in enumerate((25.9, 27.1, 28.3)):
        w = 2.45 - 0.1 * i
        pair('abdomen', (-w, y, -1.95), (-0.18, y + 1.05, -0.95), 'chrome' if i != 1 else 'chrome_dark', (8, 4, 0), (-1.2, y + 1.0, -1.4))
        b.box('abdomen', (-0.85, y + 0.1, 1.2), (0.85, y + 0.95, 2.05), 'chrome_dark')
    b.box('abdomen', (-0.16, 25.8, -1.7), (0.16, 29.4, -1.0), 'black')
    pair('abdomen', (-2.75, 25.8, -1.0), (-2.25, 29.4, 1.0), 'steel_dark')
    pair('abdomen', (-2.6, 26.0, 1.0), (-2.1, 29.2, 1.5), 'cable')
    # ---- chest -------------------------------------------------------------------------------------
    b.bone('chest', 'abdomen', [0, 29.5, 0])
    b.box('chest', (-4.0, 29.4, -2.2), (4.0, 36.8, 2.4), 'gun')
    pair('chest', (-3.95, 29.8, -2.75), (-0.45, 31.9, -1.7), 'chrome_dark', (8, 8, 0), (-2.2, 30.8, -2.2))
    b.box('chest', (-0.55, 34.6, -3.25), (0.55, 36.2, -2.2), 'chrome_light')
    b.box('chest', (-0.55, 29.8, -3.15), (0.55, 32.0, -2.2), 'chrome_light')
    b.box('chest', (-1.25, 32.05, -3.2), (1.25, 34.55, -2.85), 'gun_dark')
    b.box('chest', (-0.92, 32.38, -3.38), (0.92, 34.22, -3.1), 'core')
    pair('chest', (-3.6, 36.2, -2.0), (-0.6, 37.4, 1.6), 'steel', (0, 0, -12), (-2.1, 36.8, 0))
    pair('chest', (-4.6, 35.6, -1.4), (-2.2, 37.0, 1.8), 'chrome_dark', (0, 0, -14), (-3.4, 36.3, 0))
    pair('chest', (-4.5, 30.0, -1.6), (-3.9, 34.6, 1.6), 'steel_dark')
    b.box('chest', (-3.8, 31.8, 2.2), (3.8, 36.4, 3.0), 'chrome_seam')
    b.box('chest', (-3.2, 29.8, 2.0), (3.2, 32.2, 2.8), 'chrome_dark')
    b.box('chest', (-0.6, 29.6, 2.8), (0.6, 36.0, 3.55), 'steel')
    for y in (30.4, 32.0, 33.6, 35.2):
        b.box('chest', (-0.85, y, 3.4), (0.85, y + 0.6, 3.8), 'chrome_dark')
    pair('chest', (-5.2, 34.2, -1.0), (-4.2, 36.2, 1.0), 'gun')
    for s in ('r', 'l'):
        sg = -1 if s == 'r' else 1
        pn = 'pec_' + s
        b.bone(pn, 'chest', [sg * 2.0, 35.0, -2.4])
        x0, x1 = sorted((sg * 4.3, sg * 0.3))
        b.rbox(pn, (x0, 31.6, -3.05), (x1, 36.2, -1.9), 'chrome', (0, -sg * 10, 0), (sg * 2.2, 33.9, -2.4))
        x0, x1 = sorted((sg * 3.9, sg * 1.2))
        b.rbox(pn, (x0, 35.1, -3.2), (x1, 35.6, -2.7), 'chrome_light', (0, -sg * 10, 0), (sg * 2.2, 33.9, -2.4))
        jn = 'jet_' + s
        b.bone(jn, 'chest', [sg * 2.2, 35.0, 3.0])
        x0, x1 = sorted((sg * 3.6, sg * 1.2))
        b.rbox(jn, (x0, 30.4, 2.9), (x1, 35.0, 3.75), 'chrome', (0, 0, -sg * 8), (sg * 2.2, 35.0, 3.3))
        x0, x1 = sorted((sg * 2.95, sg * 1.75))
        b.box(jn, (x0, 29.9, 3.0), (x1, 31.0, 3.95), 'black')
        b.bone('jetflame_' + s, jn, [sg * 2.35, 29.9, 3.45])
        x0, x1 = sorted((sg * 2.8, sg * 1.9))
        b.box('jetflame_' + s, (x0, 29.6, 3.05), (x1, 29.95, 3.85), 'thruster')
    # ---- neck: two ribbed stages, telescoping pistons, cables -----------------------------------------
    b.bone('neck_lower', 'chest', [0, 37.2, 0.2])
    b.box('neck_lower', (-1.1, 36.8, -0.6), (1.1, 38.4, 1.2), 'ribs')
    pair('neck_lower', (-1.95, 36.8, -0.95), (-1.4, 38.8, -0.4), 'steel_dark')
    pair('neck_lower', (-0.95, 36.8, 1.1), (-0.3, 39.0, 1.75), 'cable')
    b.box('neck_lower', (-1.5, 36.9, -0.4), (1.5, 37.5, 1.5), 'gun_light')
    b.bone('neck_upper', 'neck_lower', [0, 38.9, 0.1])
    b.box('neck_upper', (-1.0, 38.6, -0.5), (1.0, 40.5, 1.1), 'ribs')
    b.box('neck_upper', (-1.3, 39.4, -0.8), (1.3, 39.9, 1.3), 'gun_light')
    pair('neck_upper', (-1.82, 38.3, -0.82), (-1.52, 40.3, -0.52), 'chrome_light')
    b.box('neck_upper', (-0.5, 38.8, 1.0), (0.5, 40.6, 1.6), 'cable')
    # ---- head ------------------------------------------------------------------------------------------
    b.bone('head', 'neck_upper', [0, 40.6, -0.2])
    b.box('head', (-2.4, 41.6, -1.6), (2.4, 46.4, 2.2), 'chrome')
    b.box('head', (-2.1, 41.0, 1.8), (2.1, 45.6, 3.0), 'chrome_dark')
    b.box('head', (-1.6, 41.6, 2.8), (1.6, 44.8, 3.55), 'steel')
    b.rbox('head', (-2.3, 44.6, -2.85), (2.3, 46.2, -1.4), 'chrome_light', (-6, 0, 0), (0, 45.4, -2.0))
    b.box('head', (-2.0, 42.6, -2.92), (2.0, 44.6, -1.8), 'titan')
    b.box('head', (-0.35, 42.2, -3.2), (0.35, 44.45, -2.8), 'titan')
    pair('head', (-2.0, 43.62, -3.02), (-0.33, 44.55, -2.86), 'black', (0, 0, -10), (-1.15, 44.1, -2.95))
    pair('head', (-1.84, 43.84, -3.12), (-0.47, 44.36, -2.96), 'eye_hot', (0, 0, -10), (-1.15, 44.1, -3.04))
    b.box('head', (-1.28, 41.45, -2.92), (1.28, 42.08, -1.8), 'mouth')
    for i in range(5):
        x = -1.0 + i * 0.5
        b.box('head', (x - 0.06, 41.5, -2.95), (x + 0.06, 42.02, -2.86), 'gun_dark')
    b.bone('crown', 'head', [0, 46.2, 0])
    b.box('crown', (-2.0, 46.2, -1.8), (2.0, 46.9, 2.0), 'chrome_light')
    b.box('crown', (-0.45, 46.6, -2.65), (0.45, 47.45, 2.6), 'steel')
    b.rbox('crown', (-1.6, 45.4, 2.4), (1.6, 46.6, 3.7), 'chrome', (-20, 0, 0), (0, 46.0, 2.6))
    b.rbox('crown', (-1.1, 44.6, 3.2), (1.1, 45.6, 4.2), 'chrome_dark', (-32, 0, 0), (0, 45.2, 3.4))
    pair('crown', (-1.9, 46.5, -1.2), (-1.0, 47.0, 1.6), 'chrome_dark', (0, 0, 10), (-1.4, 46.7, 0))
    for s in ('r', 'l'):
        sg = -1 if s == 'r' else 1
        tn = 'temple_' + s
        b.bone(tn, 'head', [sg * 2.4, 44.2, 0])
        x0, x1 = sorted((sg * 2.92, sg * 2.36))
        b.rbox(tn, (x0, 42.6, -1.4), (x1, 45.8, 1.8), 'chrome_dark', (0, 0, sg * 4), (sg * 2.6, 44.2, 0))
        x0, x1 = sorted((sg * 3.05, sg * 2.9))
        b.box(tn, (x0, 44.8, -0.8), (x1, 46.2, 1.4), 'steel')
        b.oct(tn, (sg * 2.75, 43.2, 0.6), 1.0, 0.6, 'x', 'gun_lit')
        x0, x1 = sorted((sg * 3.1, sg * 3.02))
        b.box(tn, (x0, 42.95, 0.35), (x1, 43.45, 0.85), 'core')
        ln = 'lid_' + s
        b.bone(ln, 'head', [sg * 1.15, 44.62, -3.05])
        x0, x1 = sorted((sg * 2.05, sg * 0.28))
        b.rbox(ln, (x0, 44.36, -3.22), (x1, 44.72, -2.9), 'chrome_dark', (0, 0, sg * 10), (sg * 1.15, 44.54, -3.05))
        bn = 'brow_' + s
        b.bone(bn, 'head', [sg * 0.4, 44.95, -3.1])
        x0, x1 = sorted((sg * 2.25, sg * 0.18))
        b.rbox(bn, (x0, 44.72, -3.38), (x1, 45.28, -2.4), 'chrome', (0, 0, sg * 12), (sg * 1.2, 45.0, -2.9))
        cn = 'cheek_' + s
        b.bone(cn, 'head', [sg * 1.9, 43.0, -2.4])
        x0, x1 = sorted((sg * 2.38, sg * 0.98))
        b.rbox(cn, (x0, 41.6, -2.95), (x1, 43.5, -1.2), 'titan', (0, sg * 16, -sg * 8), (sg * 1.7, 42.6, -2.1))
        mn = 'mouth_' + s
        b.bone(mn, 'head', [sg * 1.36, 41.9, -2.7])
        x0, x1 = sorted((sg * 1.62, sg * 1.1))
        b.box(mn, (x0, 41.4, -2.98), (x1, 42.2, -2.3), 'chrome_dark')
    b.bone('lip_upper', 'head', [0, 42.4, -2.8])
    b.box('lip_upper', (-1.3, 42.02, -3.18), (1.3, 42.5, -2.6), 'titan')
    b.bone('jaw', 'head', [0, 42.0, -0.4])
    b.box('jaw', (-1.9, 40.0, -2.8), (1.9, 41.6, -0.6), 'titan')
    b.box('jaw', (-1.25, 41.38, -3.08), (1.25, 41.8, -2.62), 'titan')
    b.box('jaw', (-1.0, 39.7, -3.12), (1.0, 40.6, -2.0), 'chrome')
    pair('jaw', (-2.26, 40.4, -1.6), (-1.82, 42.2, 0.2), 'chrome_dark')
    b.box('jaw', (-1.1, 40.9, -2.85), (1.1, 41.4, -1.0), 'gun_dark')
    # ---- right arm (mirrored) ------------------------------------------------------------------------
    b.bone('arm_r', 'chest', [-6.2, 35.6, 0])
    b.oct('arm_r', (-6.4, 35.4, 0), 1.6, 1.6, 'x', 'gun')
    b.bone('pauldron_r', 'arm_r', [-5.8, 36.6, 0])
    b.oct('pauldron_r', (-6.5, 36.0, 0), 2.05, 4.8, 'z', 'chrome')
    b.rbox('pauldron_r', (-8.55, 33.0, -2.3), (-7.15, 36.0, 2.3), 'chrome_dark', (0, 0, 16), (-6.2, 36.6, 0))
    b.rbox('pauldron_r', (-8.25, 31.6, -2.0), (-7.3, 33.5, 2.0), 'steel', (0, 0, 10), (-6.2, 36.6, 0))
    b.box('pauldron_r', (-7.8, 35.0, -2.7), (-5.2, 37.4, -2.25), 'chrome_light')
    b.box('pauldron_r', (-7.8, 35.0, 2.25), (-5.2, 37.0, 2.7), 'chrome_dark')
    b.box('arm_r', (-7.3, 29.0, -1.1), (-5.3, 34.2, 1.1), 'gun')
    b.box('arm_r', (-7.7, 29.8, -1.6), (-5.0, 33.6, 0.4), 'chrome')
    b.box('arm_r', (-7.4, 29.6, 0.2), (-5.4, 33.4, 1.55), 'chrome_dark')
    b.box('arm_r', (-7.98, 30.4, -0.8), (-7.6, 33.2, 0.8), 'steel')
    b.bone('forearm_r', 'arm_r', [-6.4, 28.8, 0])
    b.oct('forearm_r', (-6.4, 28.6, 0), 1.2, 1.9, 'x', 'gun_light')
    b.box('forearm_r', (-7.4, 27.6, 0.6), (-5.4, 29.8, 1.65), 'chrome_dark')
    b.box('forearm_r', (-7.3, 22.6, -1.0), (-5.5, 28.2, 1.0), 'gun')
    b.box('forearm_r', (-7.85, 23.2, -1.3), (-6.6, 27.8, 1.3), 'chrome')
    b.box('forearm_r', (-6.8, 23.6, -1.48), (-5.3, 27.6, -0.6), 'chrome_light')
    b.box('forearm_r', (-6.6, 23.4, 0.5), (-5.4, 27.4, 1.38), 'steel')
    b.box('forearm_r', (-7.5, 22.0, -1.15), (-5.3, 23.0, 1.15), 'chrome_dark')
    b.box('forearm_r', (-7.95, 24.4, -0.25), (-7.8, 26.8, 0.25), 'glow_soft')
    b.bone('hand_r', 'forearm_r', [-6.4, 22.0, 0])
    b.box('hand_r', (-7.0, 19.0, -1.3), (-5.8, 22.0, 1.3), 'steel')
    b.box('hand_r', (-7.3, 19.2, -1.2), (-6.95, 21.8, 1.2), 'chrome')
    b.box('hand_r', (-7.2, 18.8, -1.25), (-6.2, 19.4, 1.25), 'chrome_dark')
    b.box('hand_r', (-5.86, 19.7, -0.62), (-5.66, 21.0, 0.62), 'core')
    b.bone('muzzle_r', 'hand_r', [-5.2, 20.35, 0])
    for i, z in enumerate((-0.95, -0.32, 0.32, 0.95)):
        U.finger_chain(b, 'r', 'hand_r', (-6.45, 18.9, z), FINGERS_R[i], (1.35 - 0.1 * abs(i - 1.5), 1.05), 0.55, mat='chrome', joint='gun')
    b.bone('th_a_r', 'hand_r', [-5.9, 21.2, -1.25])
    b.box('th_a_r', (-6.2, 19.6, -1.6), (-5.6, 21.2, -1.0), 'chrome')
    b.bone('th_b_r', 'th_a_r', [-5.9, 19.6, -1.3])
    b.box('th_b_r', (-6.15, 18.4, -1.55), (-5.65, 19.65, -1.05), 'chrome_dark')
    # ---- right leg (mirrored) ------------------------------------------------------------------------
    b.bone('leg_r', 'hips', [-2.7, 23.0, 0])
    b.oct('leg_r', (-2.7, 22.8, 0), 1.5, 1.8, 'x', 'gun')
    b.box('leg_r', (-3.9, 14.4, -1.2), (-1.5, 22.2, 1.2), 'gun')
    b.box('leg_r', (-4.0, 15.4, -1.92), (-1.4, 21.6, -0.9), 'chrome')
    b.box('leg_r', (-4.42, 15.8, -1.2), (-3.8, 21.8, 1.3), 'chrome_dark')
    b.box('leg_r', (-1.6, 16.4, -1.0), (-1.2, 21.0, 1.0), 'steel_dark')
    b.box('leg_r', (-3.6, 15.8, 0.9), (-1.8, 20.8, 1.72), 'steel')
    b.bone('shin_r', 'leg_r', [-2.7, 14.0, 0])
    b.oct('shin_r', (-2.7, 13.8, 0), 1.25, 2.0, 'x', 'gun_light')
    b.rbox('shin_r', (-3.7, 12.6, -2.25), (-1.7, 15.2, -1.0), 'chrome_light', (-10, 0, 0))
    b.box('shin_r', (-3.6, 4.0, -0.9), (-1.8, 13.2, 1.1), 'gun')
    b.box('shin_r', (-3.9, 4.8, -1.72), (-1.5, 12.4, -0.6), 'chrome')
    b.box('shin_r', (-3.7, 5.8, 0.8), (-1.7, 11.6, 1.9), 'chrome_dark')
    b.box('shin_r', (-4.06, 5.0, -0.6), (-3.76, 11.0, 0.9), 'steel')
    b.box('shin_r', (-2.95, 5.2, 1.8), (-2.45, 11.0, 2.32), 'chrome_light')
    b.bone('foot_r', 'shin_r', [-2.7, 3.4, 0])
    b.oct('foot_r', (-2.7, 3.2, 0), 1.0, 1.8, 'x', 'gun')
    b.box('foot_r', (-3.9, 0.0, -2.2), (-1.5, 2.2, 2.0), 'steel')
    b.rbox('foot_r', (-3.8, 1.6, -2.4), (-1.6, 2.8, 0.8), 'chrome', (-8, 0, 0))
    b.box('foot_r', (-3.4, 0.0, 1.8), (-2.0, 1.6, 2.85), 'gun_light')
    b.bone('sole_r', 'foot_r', [-2.7, 0.0, 0])
    b.box('sole_r', (-3.3, -0.08, -1.2), (-2.1, 0.02, 1.2), 'thruster')
    b.bone('toe_r', 'foot_r', [-2.7, 1.1, -2.2])
    b.box('toe_r', (-3.8, 0.0, -4.0), (-1.6, 1.4, -2.2), 'chrome_dark')
    b.box('toe_r', (-3.6, 0.0, -4.45), (-1.8, 0.8, -3.95), 'steel')
    U.mirror_r(m)
    return m


# ---- poses ---------------------------------------------------------------------------------------------
def P(**kw):
    return kw


def face(jaw=0.0, snarl=0.0, brow=0.0, squint=0.0, flare=0.0, tilt=0.0):
    """Face rig: jaw open (deg), snarl 0..1 (upper lip and cheeks up, mouth corners out), brow (+ angry
    frown, - raised), squint 0..1 (lids down), flare 0..1 (crown and temple fins lift)."""
    return {
        'jaw': (jaw, 0.0, 0.0),
        'lip_upper': {'pos': (0.0, 0.25 * snarl, 0.0)},
        'cheek_r': {'pos': (0.0, 0.3 * snarl, 0.0), 'rot': (0.0, 0.0, -6 * snarl)},
        'cheek_l': {'pos': (0.0, 0.3 * snarl, 0.0), 'rot': (0.0, 0.0, 6 * snarl)},
        'mouth_r': {'pos': (-0.15 * snarl, -0.2 * jaw / 20.0, 0.0)},
        'mouth_l': {'pos': (0.15 * snarl, -0.2 * jaw / 20.0, 0.0)},
        'brow_r': (0.0, 0.0, -brow),
        'brow_l': (0.0, 0.0, brow),
        'lid_r': {'pos': (0.0, -0.22 * squint, 0.0)},
        'lid_l': {'pos': (0.0, -0.22 * squint, 0.0)},
        'crown': (-10 * flare, 0.0, 0.0),
        'temple_r': (0.0, 0.0, 14 * flare),
        'temple_l': (0.0, 0.0, -14 * flare),
    }


def pose(p, fc=None, extra=None):
    out = humanoid(p, FINGERS_R, FINGERS_L, THUMB_R, THUMB_L)
    # the neck splits the head pitch over its two stages
    nk = U.V(p.get('neck', U.ZERO))
    out['neck_lower'] = tuple(v * 0.5 for v in nk)
    out['neck_upper'] = tuple(v * 0.5 for v in nk)
    out.pop('neck', None)
    breath = p.get('breath', 0.0)
    out['pec_r'] = {'pos': (0.0, 0.0, -0.12 * breath), 'rot': (-1.5 * breath, 0.0, 0.0)}
    out['pec_l'] = {'pos': (0.0, 0.0, -0.12 * breath), 'rot': (-1.5 * breath, 0.0, 0.0)}
    pf = p.get('pauldron', 0.0)
    out['pauldron_r'] = (0.0, 0.0, 10 * pf)
    out['pauldron_l'] = (0.0, 0.0, -10 * pf)
    jet = p.get('jets', 0.0)
    out['jet_r'] = (-20 * jet, 0.0, -12 * jet)
    out['jet_l'] = (-20 * jet, 0.0, 12 * jet)
    out.update(face(**(fc or {})))
    if extra:
        out.update(extra)
    return out


RELAX = P(arm_r=(2, 0, 4), arm_l=(2, 0, -4), elbow_r=12, elbow_l=12, grip_r=0.45, grip_l=0.45, hand_r=(0, -10, 0), hand_l=(0, 10, 0))
STANCE = P(pos=(0, -0.8, 0), hips=(4, -8, 0), spine=(2, 0, 0), chest=(4, -8, 0), neck=(-4, 8, 0), head=(-2, 8, 0),
           leg_r=(-16, 8, 5), knee_r=22, foot_r=(-6, -8, -5), leg_l=(-2, 4, -5), knee_l=16, foot_l=(-14, 0, 5),
           arm_r=(-30, -8, 18), elbow_r=78, hand_r=(0, 0, -6), grip_r=0.85,
           arm_l=(-42, 8, -16), elbow_l=88, hand_l=(0, 0, 6), grip_l=0.85)
OVER = plus(STANCE, P(pos=(0, -1.2, -0.3), hips=(6, 0, 0), spine=(6, 0, 0), chest=(8, 0, 0), neck=(-10, 0, 0), head=(-6, 0, 0),
                      arm_r=(10, 0, 14), elbow_r=-10, arm_l=(10, 0, -14), elbow_l=-14, grip_r=0.15, grip_l=0.15, pauldron=0.6))
HOVER = P(pos=(0, 0, 0), hips=(-4, 0, 0), chest=(-2, 0, 0), head=(2, 0, 0), jets=1.0,
          leg_r=(-6, 0, 4), knee_r=18, foot_r=(28, 0, 0), leg_l=(4, 0, -3), knee_l=28, foot_l=(30, 0, 0),
          arm_r=(-8, 0, 26), elbow_r=22, arm_l=(-8, 0, -26), elbow_l=22, grip_r=0.3, grip_l=0.3, hand_r=(0, 0, -10), hand_l=(0, 0, 10))
FLY = P(pos=(0, 0, 0), root=(62, 0, 0), hips=(0, 0, 0), chest=(-6, 0, 0), neck=(-26, 0, 0), head=(-30, 0, 0), jets=1.0,
        leg_r=(6, 0, 2), knee_r=8, foot_r=(40, 0, 0), leg_l=(10, 0, -2), knee_l=14, foot_l=(40, 0, 0),
        arm_r=(18, 0, 10), elbow_r=10, arm_l=(18, 0, -10), elbow_l=10, grip_r=0.6, grip_l=0.6)


def stride(ph, run=False):
    c, s = math.cos(2 * math.pi * ph), math.sin(2 * math.pi * ph)
    amp = 30 if run else 20

    def leg(phase):
        cc = math.cos(2 * math.pi * phase)
        f = phase % 1.0
        if f < 0.5:
            knee = (14 if run else 6) + (8 if run else 4) * math.sin(math.pi * f / 0.5)
            foot = -10 * (1 - f / 0.12) if f < 0.12 else 14 * max(0.0, (f - 0.3) / 0.2)
        else:
            knee = (14 if run else 6) + (74 if run else 40) * math.sin(math.pi * (f - 0.5) / 0.5)
            foot = 10 * math.sin(math.pi * (f - 0.5) / 0.5) - 6 * (f - 0.5) / 0.5
        return -amp * cc, knee, foot

    rr, kr, fr = leg(ph)
    rl, kl, fl = leg(ph + 0.5)
    bob = (0.6 if run else 0.3) * -math.cos(4 * math.pi * ph) + (0.4 if run else 0.0)
    lean = 12 if run else 2
    arm = 28 if run else 14
    elb = 76 if run else 18
    return P(pos=(0, bob - (0.45 if run else 0.15), 0), hips=(lean * 0.4, 6 * c, 2.5 * s), spine=(lean * 0.3, -3 * c, 0),
             chest=(lean * 0.3 + 1.2 * math.cos(4 * math.pi * ph), -5 * c, -1.5 * s), neck=(-lean * 0.4, 2 * c, 0), head=(-lean * 0.4, 2 * c, 1.2 * s),
             leg_r=(rr, 0, 1), knee_r=kr, foot_r=(fr, 0, 0), leg_l=(rl, 0, -1), knee_l=kl, foot_l=(fl, 0, 0),
             arm_r=(arm * c - (8 if run else 0), 0, 5), elbow_r=elb + (14 if run else 8) * max(0.0, -c),
             arm_l=(-arm * c - (8 if run else 0), 0, -5), elbow_l=elb + (14 if run else 8) * max(0.0, c),
             grip_r=0.9 if run else 0.5, grip_l=0.9 if run else 0.5, hand_r=(-6 if run else 0, -10, 0), hand_l=(-6 if run else 0, 10, 0),
             breath=0.5 * math.sin(4 * math.pi * ph))


def additive(name, p, fc=None, length=1.0):
    """Constant additive pose (renderer weights it): only the channels it moves."""
    q = humanoid(p)
    nk = U.V(p.get('neck', U.ZERO))
    q['neck_lower'] = tuple(v * 0.5 for v in nk)
    q['neck_upper'] = tuple(v * 0.5 for v in nk)
    q.pop('neck', None)
    if fc:
        q.update(face(**fc))
    q = {k: v for k, v in q.items() if not _zero(v)}
    c = UClip(name, length)
    c.key(0, q).key(length, q)
    return c


def _zero(v):
    if isinstance(v, dict):
        return all(all(abs(x) < 1e-6 for x in vv) for vv in v.values())
    return all(abs(x) < 1e-6 for x in v)


def strike(name, length, contact, guard, wind, hit, over, t_wind, t_over, fw=None, fh=None, fo=None, extra=()):
    """Anticipation -> acceleration -> contact -> follow-through -> recovery with eased segments."""
    c = UClip(name, length, review=contact)
    c.key(0, pose(guard), 'easeOutQuad')
    c.key(t_wind, pose(wind, fw), 'easeInCubic')
    c.key(contact, pose(hit, fh), 'easeOutCubic')
    c.key(t_over, pose(over, fo or fh), 'easeInOutSine')
    for t, p, f in extra:
        c.key(t, pose(p, f), 'easeInOutSine')
    c.key(length, pose(guard))
    return c


ANGRY = dict(brow=10, squint=0.4, snarl=0.3)
SHOUT = dict(jaw=16, brow=14, squint=0.5, snarl=0.9)


def clips():
    out = []
    # ---- idles -----------------------------------------------------------------------------------------
    c = UClip('idle', 5.0, loop=True)
    for i in range(11):
        t = i * 0.5
        ph = t / 5.0
        br = math.sin(2 * math.pi * ph * 2)
        look = math.sin(2 * math.pi * ph)
        c.key(t, pose(plus(RELAX, P(chest=(1.0 * br, 0, 0), spine=(0.5 * br, 0, 0), neck=(-1.0 * br, 8 * look, 0), head=(0, 6 * look, -3 * look),
                                    pos=(0, 0.1 * br, 0), hips=(0, 0, 1.0 * look), breath=br,
                                    grip_r=0.15 * math.sin(2 * math.pi * ph * 3), grip_l=0.1 * math.sin(2 * math.pi * ph * 2 + 1))),
                      dict(brow=2 + 2 * max(0.0, look), squint=0.15)))
    out.append(c)
    c = UClip('combat_idle', 1.8, loop=True)
    for i in range(7):
        t = i * 0.3
        ph = t / 1.8
        bb = math.sin(2 * math.pi * ph)
        c.key(t, pose(plus(STANCE, P(pos=(0, 0.22 * bb, 0), chest=(1.2 * bb, 2 * math.cos(2 * math.pi * ph), 0), breath=bb,
                                     arm_r=(2 * bb, 0, 0), arm_l=(-2 * bb, 0, 0))), ANGRY))
    out.append(c)
    c = UClip('overdrive_idle', 1.2, loop=True)
    for i in range(7):
        t = i * 0.2
        ph = t / 1.2
        bb = math.sin(2 * math.pi * ph)
        jit = math.sin(2 * math.pi * ph * 3)
        c.key(t, pose(plus(OVER, P(pos=(0, 0.3 * bb, 0), chest=(1.8 * bb, 0, 0), breath=1.5 * bb, head=(0, 2 * jit, 2 * jit),
                                   grip_r=0.2 * jit, grip_l=-0.2 * jit)), dict(jaw=3 + 2 * bb, brow=14, squint=0.55, snarl=0.6, flare=1.0)))
    out.append(c)
    # ---- locomotion ------------------------------------------------------------------------------------
    c = UClip('walk', 1.1, loop=True)
    U.loop_keys(c, lambda ph: pose(stride(ph), dict(brow=4)), 8)
    out.append(c)
    c = UClip('run', 0.62, loop=True)
    U.loop_keys(c, lambda ph: pose(stride(ph, run=True), ANGRY), 8)
    out.append(c)
    out.append(additive('accel', P(hips=(9, 0, 0), spine=(5, 0, 0), chest=(5, 0, 0), neck=(-8, 0, 0), head=(-6, 0, 0), arm_r=(10, 0, 0), arm_l=(10, 0, 0))))
    out.append(additive('decel', P(hips=(-8, 0, 0), spine=(-4, 0, 0), chest=(-4, 0, 0), neck=(6, 0, 0), knee_r=10, knee_l=10,
                                   arm_r=(-12, 0, 6), arm_l=(-12, 0, -6))))
    out.append(additive('turn_l', P(hips=(0, -6, 4), chest=(0, -10, 3), neck=(0, -14, 0), head=(0, -10, 0), leg_l=(0, -8, 0), leg_r=(0, -4, 0))))
    out.append(additive('turn_r', P(hips=(0, 6, -4), chest=(0, 10, -3), neck=(0, 14, 0), head=(0, 10, 0), leg_r=(0, 8, 0), leg_l=(0, 4, 0))))
    c = UClip('hover', 2.4, loop=True)
    for i in range(9):
        t = i * 0.3
        ph = t / 2.4
        bb = math.sin(2 * math.pi * ph)
        c.key(t, pose(plus(HOVER, P(pos=(0, 0.6 * bb, 0), leg_r=(3 * bb, 0, 0), leg_l=(-3 * bb, 0, 0), knee_l=4 * bb, arm_r=(0, 0, 2 * bb),
                                    arm_l=(0, 0, -2 * bb), breath=bb)), dict(brow=6, squint=0.2)))
    out.append(c)
    c = UClip('fly', 1.6, loop=True)
    for i in range(9):
        t = i * 0.2
        ph = t / 1.6
        bb = math.sin(2 * math.pi * ph)
        c.key(t, pose(plus(FLY, P(leg_r=(4 * bb, 0, 0), leg_l=(-4 * bb, 0, 0), arm_r=(0, 0, 2 * bb), arm_l=(0, 0, -2 * bb))), dict(brow=8, squint=0.3)))
    out.append(c)
    low = P(pos=(0, -4.5, 0), hips=(22, 0, 0), chest=(12, 0, 0), neck=(-18, 0, 0), head=(-8, 0, 0),
            leg_r=(-44, 0, 8), knee_r=82, foot_r=(-38, 0, 0), leg_l=(-30, 0, -8), knee_l=96, foot_l=(-10, 0, 0),
            arm_r=(-20, 0, 40), elbow_r=30, arm_l=(-40, 0, -30), elbow_l=40, hand_r=(0, 0, -30), grip_r=0.2, grip_l=0.8)
    c = UClip('land', 0.6, review=0.14)
    c.key(0, pose(HOVER), 'easeInQuad').key(0.14, pose(low, ANGRY), 'easeOutCubic').key(0.32, pose(plus(low, P(pos=(0, 0.6, 0)))), 'easeInOutSine') \
        .key(0.6, pose(STANCE, ANGRY))
    out.append(c)
    c = UClip('fall', 0.8, loop=True)
    f0 = P(arm_r=(-30, 0, 50), arm_l=(-30, 0, -50), elbow_r=20, elbow_l=20, leg_r=(-20, 0, 4), knee_r=30, leg_l=(6, 0, -4), knee_l=40, chest=(-4, 0, 0))
    c.key(0, pose(f0)).key(0.4, pose(plus(f0, P(arm_r=(-8, 0, 6), arm_l=(-8, 0, -6))))).key(0.8, pose(f0))
    out.append(c)
    # ---- face and armour layers ------------------------------------------------------------------------
    c = UClip('talk', 1.6, loop=True)
    seq = [(0.0, 0, 0.1, 2), (0.15, 12, 0.3, 4), (0.3, 4, 0.2, 2), (0.45, 15, 0.5, 6), (0.62, 2, 0.1, 3), (0.8, 10, 0.35, 5),
           (0.95, 6, 0.2, 2), (1.12, 14, 0.4, 7), (1.3, 3, 0.15, 3), (1.45, 8, 0.3, 4), (1.6, 0, 0.1, 2)]
    for t, j, sn, br in seq:
        f = face(jaw=j, snarl=sn, brow=br)
        c.key(t, {k: f[k] for k in ('jaw', 'lip_upper', 'cheek_r', 'cheek_l', 'mouth_r', 'mouth_l', 'brow_r', 'brow_l')})
    out.append(c)
    out.append(additive('flare', P(pauldron=0.0), dict(flare=1.0), 1.0))
    out[-1].frames = [(t, dict(p, pauldron_r=(0.0, 0.0, 12.0), pauldron_l=(0.0, 0.0, -12.0), jet_r=(-14.0, 0.0, -10.0), jet_l=(-14.0, 0.0, 10.0)))
                      for t, p in out[-1].frames]
    c = UClip('hurt', 0.35)
    q0 = humanoid(P())
    q1 = humanoid(P(chest=(-8, 5, 4), head=(-12, 0, 5), spine=(-3, 0, 0), arm_r=(8, 0, 6), arm_l=(8, 0, -6)))
    q1.update({k: v for k, v in face(jaw=8, brow=12, squint=0.8).items() if k in ('jaw', 'brow_r', 'brow_l', 'lid_r', 'lid_l')})
    c.key(0, {k: v for k, v in q0.items() if k in q1}).key(0.06, q1, 'easeOutCubic').key(0.35, {k: v for k, v in q0.items() if k in q1})
    out.append(c)
    hit1 = P(pos=(0, -0.6, 2.4), hips=(-14, 8, 6), spine=(-8, 0, 0), chest=(-14, 10, 6), neck=(-14, -8, 6), head=(-8, -6, 6),
             arm_r=(-50, 0, 46), elbow_r=30, arm_l=(-34, 0, -54), elbow_l=40, grip_r=0.3, grip_l=0.3,
             leg_r=(-26, 0, 6), knee_r=12, foot_r=(-18, 0, 0), leg_l=(14, 0, -4), knee_l=30)
    brace = P(pos=(0, -2.2, 3.6), hips=(14, 4, 0), spine=(8, 0, 0), chest=(12, 0, 0), neck=(-14, 0, 0),
              arm_r=(-26, 0, 30), elbow_r=40, arm_l=(-26, 0, -30), elbow_l=40, grip_r=1, grip_l=1,
              leg_r=(16, 0, 6), knee_r=50, foot_r=(-10, 0, 0), leg_l=(-50, 0, -6), knee_l=66, foot_l=(-8, 0, 0))
    c = UClip('stagger', 1.2, review=0.14)
    c.key(0, pose(STANCE, ANGRY), 'easeOutCubic').key(0.14, pose(hit1, dict(jaw=14, brow=-6, squint=0.0)), 'easeInOutSine') \
        .key(0.5, pose(brace, SHOUT), 'easeInOutSine').key(0.9, pose(plus(STANCE, P(pos=(0, -0.6, 0))), ANGRY)).key(1.2, pose(STANCE, ANGRY))
    out.append(c)
    # ---- melee -----------------------------------------------------------------------------------------
    # jab_cross (20 ticks; contacts 6 and 13): left jab, right cross
    jab_w = plus(STANCE, P(chest=(0, -6, 0), arm_l=(-30, 10, -10), elbow_l=110, pos=(0, -0.2, 0)))
    jab = plus(STANCE, P(chest=(2, 16, 0), hips=(0, 6, 0), arm_l=(-90, 20, -10), elbow_l=8, head=(0, -10, 0), pos=(0, -0.4, -0.6)))
    cross_w = plus(STANCE, P(chest=(0, 22, 0), hips=(0, 10, 0), arm_r=(-20, 20, 30), elbow_r=118, arm_l=(-60, 10, -16), elbow_l=60))
    cross = plus(STANCE, P(chest=(6, -30, 0), hips=(4, -16, 0), arm_r=(-90, -30, 6), elbow_r=6, head=(-6, 20, 0), neck=(-4, 6, 0),
                           leg_r=(8, 0, 0), knee_r=12, foot_r=(16, 0, 0), pos=(0, -0.8, -1.2)))
    c = UClip('jab_cross', 1.0, review=0.65)
    c.key(0, pose(STANCE, ANGRY), 'easeOutQuad').key(0.15, pose(jab_w, ANGRY), 'easeInCubic').key(0.3, pose(jab, ANGRY), 'easeOutCubic') \
        .key(0.48, pose(cross_w, ANGRY), 'easeInCubic').key(0.65, pose(cross, SHOUT), 'easeOutCubic') \
        .key(0.76, pose(plus(cross, P(chest=(2, -6, 0), arm_r=(-4, -6, 0))), SHOUT), 'easeInOutSine').key(1.0, pose(STANCE, ANGRY))
    out.append(c)
    # uppercut (22 ticks; contact 11)
    wind = plus(STANCE, P(pos=(0, -2.2, 0), hips=(10, 14, 0), chest=(14, 18, 6), knee_r=40, knee_l=34, leg_r=(-28, 8, 5), leg_l=(-10, 4, -5),
                          arm_r=(16, 10, 14), elbow_r=100, hand_r=(0, 0, 0), head=(-14, -10, 0)))
    hit = plus(STANCE, P(pos=(0, 1.0, -0.8), hips=(-6, -10, 0), spine=(-6, 0, 0), chest=(-12, -16, -6), neck=(10, 8, 0), head=(6, 6, 0),
                         arm_r=(-150, -20, -6), elbow_r=40, leg_r=(4, 0, 4), knee_r=4, foot_r=(20, 0, 0), leg_l=(-10, 0, -4), knee_l=6))
    over = plus(hit, P(pos=(0, 0.6, 0), arm_r=(-10, 0, 0), chest=(-4, 0, 0)))
    out.append(strike('uppercut', 1.1, 0.55, STANCE, wind, hit, over, 0.36, 0.68, ANGRY, SHOUT))
    # backhand (18 ticks; contact 9): a sweeping right backhand across
    wind = plus(STANCE, P(chest=(0, -30, 0), hips=(0, -12, 0), arm_r=(-70, 50, -30), elbow_r=80, head=(0, 20, 0)))
    hit = plus(STANCE, P(chest=(4, 36, 0), hips=(2, 16, 0), arm_r=(-92, -56, 20), elbow_r=10, hand_r=(0, 0, 24), head=(-6, -24, 0),
                         pos=(0, -0.6, -0.6)))
    over = plus(hit, P(chest=(0, 10, 0), arm_r=(0, -14, 0)))
    out.append(strike('backhand', 0.9, 0.45, STANCE, wind, hit, over, 0.28, 0.56, ANGRY, SHOUT))
    # palm_blast (26 ticks; contact 15): right palm forward, the left hand braces, recoil
    aim = P(pos=(0, -0.6, 0), hips=(0, -12, 0), chest=(0, -14, 0), neck=(0, 12, 0), head=(0, 12, 0),
            leg_r=(-10, 10, 6), knee_r=18, leg_l=(6, 6, -4), knee_l=12,
            arm_r=(-88, 26, -6), elbow_r=4, hand_r=(-82, 0, 88), grip_r=0.0,
            arm_l=(-52, 56, 26), elbow_l=20, hand_l=(0, 0, -20), grip_l=0.7)
    c = UClip('palm_blast', 1.3, review=0.62)
    c.key(0, pose(STANCE, ANGRY), 'easeOutCubic').key(0.3, pose(aim, dict(brow=12, squint=0.7)))
    for i, t in enumerate((0.4, 0.5, 0.6, 0.7)):
        c.key(t, pose(plus(aim, P(arm_r=(0.7 * (-1) ** i, 0, 0.5 * (-1) ** i))), dict(brow=12, squint=0.7, jaw=2)))
    c.key(0.75, pose(plus(aim, P(arm_r=(-14, 0, 0), elbow_r=14, chest=(-6, 0, 0), pos=(0, 0, 0.8))), SHOUT), 'easeOutCubic') \
        .key(0.92, pose(plus(aim, P(arm_r=(-4, 0, 0))), ANGRY)).key(1.3, pose(STANCE, ANGRY))
    out.append(c)
    # beam (50 ticks; pulses 16..41): head forward, jaw opens, eyes pour the beam, a slow sweep
    base = plus(STANCE, P(pos=(0, -1.0, 0), hips=(6, 0, 0), chest=(8, 0, 0), neck=(-12, 0, 0), head=(-8, 0, 0),
                          arm_r=(-10, 0, 30), elbow_r=40, grip_r=1, arm_l=(-10, 0, -30), elbow_l=40, grip_l=1, pauldron=0.4))
    c = UClip('beam', 2.5, review=1.2)
    c.key(0, pose(STANCE, ANGRY), 'easeOutCubic').key(0.5, pose(plus(base, P(head=(14, 0, 0), neck=(6, 0, 0), chest=(-6, 0, 0))), dict(brow=16, squint=0.9, jaw=4)),
                                                  'easeInCubic') \
        .key(0.8, pose(plus(base, P(head=(0, -16, 0))), dict(jaw=12, brow=16, squint=0.2, snarl=0.6, flare=0.6))) \
        .key(1.5, pose(plus(base, P(head=(0, 4, 0), chest=(0, 8, 0))), dict(jaw=14, brow=16, squint=0.2, snarl=0.7, flare=0.7))) \
        .key(2.05, pose(plus(base, P(head=(0, 16, 0), chest=(0, 12, 0))), dict(jaw=12, brow=16, squint=0.2, snarl=0.6, flare=0.6)), 'easeInOutSine') \
        .key(2.5, pose(STANCE, ANGRY))
    out.append(c)
    # slam (34 ticks; contact 20): leaps, both fists overhead, hammers the ground
    up = P(pos=(0, 6.0, -1.0), hips=(-10, 0, 0), spine=(-8, 0, 0), chest=(-14, 0, 0), neck=(8, 0, 0), jets=0.6,
           arm_r=(-170, -10, -6), elbow_r=40, arm_l=(-170, 10, 6), elbow_l=40, grip_r=1, grip_l=1,
           leg_r=(-30, 0, 3), knee_r=60, foot_r=(30, 0, 0), leg_l=(-10, 0, -3), knee_l=80, foot_l=(30, 0, 0))
    slam = P(pos=(0, -4.0, -2.0), hips=(24, 0, 0), spine=(14, 0, 0), chest=(18, 0, 0), neck=(-20, 0, 0), head=(-10, 0, 0),
             arm_r=(-64, -12, -8), elbow_r=10, arm_l=(-64, 12, 8), elbow_l=10, grip_r=1, grip_l=1,
             leg_r=(-50, 0, 6), knee_r=74, foot_r=(-20, 0, 0), leg_l=(16, 0, -6), knee_l=60, foot_l=(-14, 0, 0))
    crouch = plus(STANCE, P(pos=(0, -3.0, 0), knee_r=60, knee_l=60, leg_r=(-34, 0, 4), leg_l=(-30, 0, -4), foot_r=(-26, 0, 0), foot_l=(-30, 0, 0),
                            hips=(14, 0, 0), arm_r=(20, 0, 20), arm_l=(20, 0, -20)))
    c = UClip('slam', 1.7, review=1.0)
    c.key(0, pose(STANCE, ANGRY), 'easeOutQuad').key(0.3, pose(crouch, ANGRY), 'easeOutCubic').key(0.7, pose(up, SHOUT), 'easeInQuart') \
        .key(1.0, pose(slam, SHOUT), 'easeOutQuad').key(1.2, pose(plus(slam, P(pos=(0, -0.4, 0))), SHOUT), 'easeInOutSine').key(1.7, pose(STANCE, ANGRY))
    out.append(c)
    # dive (36 ticks; contact 24): from the air, head-first fist dive, lands in a crouch
    dive = P(pos=(0, 2.0, 0), root=(70, 0, 0), chest=(-4, 0, 0), neck=(-30, 0, 0), head=(-20, 0, 0), jets=1.0,
             arm_r=(-170, 0, -4), elbow_r=4, arm_l=(-40, 0, -20), elbow_l=60, grip_r=1, grip_l=1,
             leg_r=(10, 0, 4), knee_r=10, foot_r=(40, 0, 0), leg_l=(16, 0, -4), knee_l=24, foot_l=(40, 0, 0))
    impact = plus(slam, P(arm_r=(-20, 0, 0), arm_l=(30, 0, -20)))
    c = UClip('dive', 1.8, review=1.2)
    c.key(0, pose(HOVER, ANGRY), 'easeOutCubic').key(0.5, pose(plus(HOVER, P(pos=(0, 2.0, 0), arm_r=(-140, 0, 0), elbow_r=60, chest=(-10, 0, 0))), SHOUT),
                                                     'easeInCubic') \
        .key(0.9, pose(dive, SHOUT), 'easeInQuad').key(1.2, pose(impact, SHOUT), 'easeOutCubic') \
        .key(1.45, pose(plus(impact, P(pos=(0, 0.6, 0))), ANGRY), 'easeInOutSine').key(1.8, pose(STANCE, ANGRY))
    c.keep.update({'root'})
    out.append(c)
    # dash (14 ticks; contact 7): low burst forward, shoulder first, right fist drives
    lowd = P(pos=(0, -1.5, -1.0), root=(26, 0, 0), chest=(4, -10, 0), neck=(-20, 6, 0), jets=1.0,
             arm_r=(-70, -10, 10), elbow_r=30, grip_r=1, arm_l=(30, 0, -20), elbow_l=40, grip_l=1,
             leg_r=(-40, 0, 4), knee_r=50, foot_r=(10, 0, 0), leg_l=(30, 0, -4), knee_l=30, foot_l=(30, 0, 0))
    c = UClip('dash', 0.7, review=0.35)
    c.key(0, pose(STANCE, ANGRY), 'easeOutQuad').key(0.12, pose(plus(STANCE, P(pos=(0, -1.4, 0.6), knee_r=40, knee_l=40, chest=(10, 14, 0))), ANGRY),
                                                     'easeInCubic') \
        .key(0.35, pose(lowd, SHOUT), 'easeOutCubic').key(0.48, pose(plus(lowd, P(arm_r=(-16, 0, 0))), SHOUT), 'easeInOutSine') \
        .key(0.7, pose(STANCE, ANGRY))
    c.keep.update({'root'})
    out.append(c)
    # summon (30 ticks; signal at 18): the right hand rises, fingers spread, a command pulse
    raise_ = plus(STANCE, P(pos=(0, 0.4, 0), chest=(-8, 0, 0), neck=(8, 0, 0), head=(6, 0, 0), arm_r=(-160, -14, 10), elbow_r=20, grip_r=0.0,
                            hand_r=(0, 0, -10)))
    snap = plus(raise_, P(arm_r=(14, 0, 0), elbow_r=20, grip_r=1.0, chest=(4, 0, 0)))
    c = UClip('summon', 1.5, review=0.9)
    c.key(0, pose(STANCE, ANGRY), 'easeOutCubic').key(0.6, pose(raise_, dict(brow=6, squint=0.1, jaw=6)), 'easeInCubic') \
        .key(0.9, pose(snap, SHOUT), 'easeOutCubic').key(1.1, pose(snap, ANGRY), 'easeInOutSine').key(1.5, pose(STANCE, ANGRY))
    out.append(c)
    # absorb (40 ticks; at 20): arms spread, palms up, draws power in, chest swells
    wide = P(pos=(0, 0.6, 0), hips=(-4, 0, 0), chest=(-12, 0, 0), neck=(16, 0, 0), head=(14, 0, 0), breath=2.0, pauldron=0.8,
             arm_r=(-30, 0, 80), elbow_r=20, hand_r=(0, -80, 0), grip_r=0.0, arm_l=(-30, 0, -80), elbow_l=20, hand_l=(0, 80, 0), grip_l=0.0)
    pull = plus(wide, P(arm_r=(-30, 0, -40), elbow_r=70, arm_l=(-30, 0, 40), elbow_l=70, grip_r=1, grip_l=1, chest=(16, 0, 0), neck=(-20, 0, 0)))
    c = UClip('absorb', 2.0, review=1.0)
    c.key(0, pose(STANCE, ANGRY), 'easeOutCubic').key(0.6, pose(wide, dict(jaw=10, brow=-4, squint=0.0, flare=1.0)), 'easeInOutSine') \
        .key(0.95, pose(plus(wide, P(arm_r=(-4, 0, 6), arm_l=(-4, 0, -6))), dict(jaw=12, brow=-6, flare=1.0)), 'easeInCubic') \
        .key(1.0, pose(pull, SHOUT), 'easeOutCubic').key(1.4, pose(pull, dict(brow=14, squint=0.6, snarl=0.6, flare=0.6)), 'easeInOutSine') \
        .key(2.0, pose(STANCE, ANGRY))
    out.append(c)
    # transition_air (80 ticks; lift-off at 40): crouch, thrusters ignite, rises into a hover
    c = UClip('transition_air', 4.0, review=2.0)
    c.key(0, pose(STANCE, ANGRY), 'easeOutCubic').key(1.0, pose(crouch, dict(brow=10, squint=0.6)), 'easeInOutSine') \
        .key(1.8, pose(plus(crouch, P(jets=1.0, pauldron=0.5, arm_r=(0, 0, 30), arm_l=(0, 0, -30))), dict(brow=14, squint=0.8, flare=0.5)), 'easeInCubic') \
        .key(2.0, pose(plus(HOVER, P(pos=(0, 3.0, 0), chest=(-14, 0, 0), arm_r=(-10, 0, 40), arm_l=(-10, 0, -40), head=(16, 0, 0))), SHOUT), 'easeOutCubic') \
        .key(3.0, pose(plus(HOVER, P(pos=(0, 1.5, 0), arm_r=(-20, 0, 60), arm_l=(-20, 0, -60), chest=(-10, 0, 0), head=(10, 0, 0))),
                       dict(jaw=6, brow=8, flare=0.6)), 'easeInOutSine') \
        .key(4.0, pose(HOVER, ANGRY))
    out.append(c)
    # transition_overdrive (80 ticks; the burst at 44): doubles over, armour splits open, a roar
    hunch = plus(STANCE, P(pos=(0, -2.0, 0), hips=(20, 0, 0), spine=(14, 0, 0), chest=(18, 0, 0), neck=(16, 0, 0), head=(10, 0, 0),
                           arm_r=(10, 0, 10), elbow_r=90, grip_r=1, arm_l=(10, 0, -10), elbow_l=90, grip_l=1))
    roar = P(pos=(0, 0.6, 0.4), hips=(-8, 0, 0), spine=(-10, 0, 0), chest=(-18, 0, 0), neck=(18, 0, 0), head=(14, 0, 0), breath=2.5, pauldron=1.2,
             jets=0.8, arm_r=(-20, 0, 70), elbow_r=40, grip_r=1, arm_l=(-20, 0, -70), elbow_l=40, grip_l=1,
             leg_r=(-10, 0, 8), knee_r=14, leg_l=(-4, 0, -8), knee_l=12)
    c = UClip('transition_overdrive', 4.0, review=2.25)
    c.key(0, pose(STANCE, ANGRY), 'easeOutCubic').key(0.8, pose(hunch, dict(brow=14, squint=1.0, jaw=2)))
    for i, t in enumerate((1.1, 1.35, 1.6, 1.85)):
        c.key(t, pose(plus(hunch, P(chest=(2 * (-1) ** i, 2 * (-1) ** i, 0), head=(0, 4 * (-1) ** i, 3 * (-1) ** i))),
                      dict(brow=14, squint=1.0, jaw=2 + i, flare=0.15 * i)))
    c.key(2.05, pose(hunch, dict(brow=16, squint=1.0, jaw=4, flare=0.6)), 'easeInCubic') \
        .key(2.2, pose(roar, dict(jaw=22, brow=16, squint=0.0, snarl=1.0, flare=1.2)), 'easeOutCubic') \
        .key(3.1, pose(plus(roar, P(chest=(4, 0, 0))), dict(jaw=20, brow=16, snarl=1.0, flare=1.2)), 'easeInOutSine') \
        .key(4.0, pose(OVER, dict(jaw=3, brow=14, squint=0.55, snarl=0.6, flare=1.0)))
    out.append(c)
    # assemble (140 ticks; complete at 120): the parts converge in order (legs, pelvis, torso, arms, head)
    c = UClip('assemble', 7.0, review=4.0)
    scatter = {'root': {'pos': (0.0, -20.0, 0.0)}, 'leg_r': {'pos': (-6.0, -8.0, 4.0), 'rot': (40.0, 0.0, 30.0)}, 'leg_l': {'pos': (6.0, -8.0, -3.0), 'rot': (-30.0, 0.0, -40.0)},
               'abdomen': {'pos': (0.0, 6.0, 6.0), 'rot': (60.0, 30.0, 0.0)}, 'chest': {'pos': (0.0, 10.0, -6.0), 'rot': (-40.0, 0.0, 30.0)},
               'arm_r': {'pos': (-12.0, 4.0, 0.0), 'rot': (0.0, 0.0, 120.0)}, 'arm_l': {'pos': (12.0, 2.0, 0.0), 'rot': (0.0, 0.0, -120.0)},
               'head': {'pos': (0.0, 14.0, 4.0), 'rot': (90.0, 0.0, 0.0)}}
    order = [('root', 0.4, 1.4), ('leg_r', 1.2, 2.2), ('leg_l', 1.5, 2.5), ('abdomen', 2.4, 3.4), ('chest', 3.0, 4.0), ('arm_r', 3.8, 4.8),
             ('arm_l', 4.1, 5.1), ('head', 4.9, 5.9)]
    times = sorted({0.0, 7.0} | {t for _, a, z in order for t in (a, z)} | {6.0, 6.2, 6.5})

    def assembled(t):
        p = pose(P(), dict(squint=1.0))
        for bone, a, z in order:
            f = U.smooth_t((t - a) / (z - a))
            sc = scatter[bone]
            spec = {}
            if 'pos' in sc:
                spec['pos'] = tuple(v * (1 - f) for v in sc['pos'])
            spec['rot'] = tuple(v * (1 - f) for v in sc.get('rot', (0.0, 0.0, 0.0)))
            p[bone] = spec
        if t >= 6.0:
            p.update(face(**(dict(squint=1.0 - U.smooth_t((t - 6.0) / 0.4), brow=12 * U.smooth_t((t - 6.0) / 0.5), jaw=4 if 6.1 < t < 6.6 else 0))))
        return p
    for t in times:
        c.key(t, assembled(t), 'easeOutCubic' if t in {a for _, a, _ in order} else None)
    c.keep.update({'root'})
    out.append(c)
    # death (120 ticks): systems crash, knees buckle, reaches out, falls, the head turns, the eyes fade (renderer)
    sp = P(pos=(0, -0.5, 0), chest=(-12, -16, 8), neck=(-16, 20, 10), head=(-14, 10, 6), arm_r=(-20, 0, 40), elbow_r=60, arm_l=(10, 0, -20),
           elbow_l=20, grip_r=0.8, grip_l=0.1, knee_r=10)
    kneel = P(pos=(0, -10.0, -1.0), hips=(18, 0, 0), spine=(12, 0, 0), chest=(16, 0, 0), neck=(20, -10, 0), head=(10, -6, 0),
              leg_r=(-30, 0, 4), knee_r=112, foot_r=(40, 0, 0), leg_l=(-10, 0, -4), knee_l=100, foot_l=(30, 0, 0),
              arm_r=(-60, 0, 10), elbow_r=10, hand_r=(0, 0, 20), grip_r=0.2, arm_l=(-10, 0, -10), elbow_l=20, grip_l=0.3)
    down = P(pos=(0, -19.0, -12.0), root=(84, 0, 0), chest=(4, 0, 0), neck=(10, 30, 0), head=(6, 20, 0),
             leg_r=(-6, 0, 4), knee_r=12, foot_r=(30, 0, 0), leg_l=(4, 0, -6), knee_l=22, foot_l=(30, 0, 0),
             arm_r=(-160, 0, 10), elbow_r=10, grip_r=0.3, arm_l=(-30, 0, -30), elbow_l=40, grip_l=0.4)
    c = UClip('death', 6.0, review=1.6)
    c.key(0, pose(STANCE, SHOUT), 'easeOutCubic').key(0.25, pose(sp, dict(jaw=18, brow=-8, squint=0.0)))
    for i, t in enumerate((0.45, 0.65, 0.85)):
        c.key(t, pose(plus(sp, P(head=(6 * (-1) ** i, -16 * (-1) ** i, 0), chest=(2, 10 * (-1) ** i, -6 * (-1) ** i))), dict(jaw=10 + 4 * i, brow=-6)))
    c.key(1.6, pose(kneel, dict(jaw=6, brow=4, squint=0.3)), 'easeInQuad').key(2.6, pose(plus(kneel, P(arm_r=(-20, 0, 0), head=(-8, 0, 0))),
                                                                                          dict(jaw=8, brow=6, squint=0.4)), 'easeInOutSine') \
        .key(3.4, pose(down, dict(jaw=4, squint=0.6)), 'easeOutQuad').key(3.55, pose(plus(down, P(pos=(0, 0.6, 0))), dict(jaw=4, squint=0.6))) \
        .key(4.6, pose(plus(down, P(head=(0, -10, 0))), dict(jaw=2, squint=0.8)), 'easeInOutSine').key(6.0, pose(down, dict(jaw=0, squint=1.0)))
    c.keep.update({'root'})
    out.append(c)
    # ascend (120 ticks): the defeat ending, he rises with arms open, the head back
    c = UClip('ascend', 6.0, review=3.0)
    c.key(0, pose(STANCE, ANGRY), 'easeInOutSine').key(1.0, pose(plus(HOVER, P(pos=(0, 2.0, 0))), dict(brow=6))) \
        .key(3.0, pose(plus(HOVER, P(pos=(0, 6.0, 0), chest=(-16, 0, 0), neck=(14, 0, 0), head=(12, 0, 0), arm_r=(-20, 0, 70), arm_l=(-20, 0, -70),
                                     elbow_r=10, elbow_l=10, grip_r=0, grip_l=0, pauldron=0.8)), dict(jaw=6, brow=-4, flare=1.0)), 'easeInOutSine') \
        .key(6.0, pose(plus(HOVER, P(pos=(0, 10.0, 0), chest=(-18, 0, 0), neck=(16, 0, 0), head=(14, 0, 0), arm_r=(-24, 0, 84), arm_l=(-24, 0, -84),
                                     pauldron=1.0)), dict(jaw=4, brow=-4, flare=1.0)))
    out.append(c)
    return out


def main():
    m = build()
    m.write(GEO, TEX, GLOW)
    n = sum(len(bn.cubes) for bn in m.bones)
    cl = clips()
    size = U.write_anims(ANIM, cl)
    print('prime: %d bones, %d cubes, atlas %dx%d; %d clips (%d bytes)' % (len(m.bones), n, m.width, m.height, len(cl), size))
    U.finalize_assets([GEO, ANIM], [TEX, GLOW])
    if '--preview' in sys.argv:
        U.preview_all('prime', GEO, TEX, GLOW, cl)


if __name__ == '__main__':
    main()
