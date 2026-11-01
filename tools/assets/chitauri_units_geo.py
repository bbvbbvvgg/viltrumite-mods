#!/usr/bin/env python3
"""Chitauri elite infantry geometry + materials (Captor, Heavy, Commander), used by chitauri_units.py. Each one is its own geo,
atlas + glow and clip set on the soldier's joint layout (same pivots, so the rifle hand / off hand solvers of
chitauri_soldier work for every weapon), with its own armour, silhouette, head, weapon and special bones:

* captor:    lean, hunched hunter; snare lance with a forked clamp head (teal tether energy), restraint cuffs,
             a back rig whose two clamp arms unfold during the grab; padded left shoulder the captive lies on.
* heavy:     armoured bulk (renders x1.25); plasma cannon with a drum cell, reactor pack with vent flaps,
             helmet visor, and a breakable chest plate (bone armor_chest, hidden once the armour is broken).
* commander: tall, gold-crowned officer; war glaive with a violet energy edge, segmented cape (cape1..3),
             tabards, antenna fins that flare while it signals.

Clip sets (names used by ChitauriEliteRenderer): idle, walk, run, turn_l, turn_r, aim, shoot, reload, melee,
hurt | knockback, fall, land, death, sever, disembark, mounted, mounted_shoot | per unit specials
(captor: capture, carry, drop; heavy: slam, vent, armor_break, stagger; commander: signal, command,
sweep, stagger). Own work, CC0.

Run: python3 tools/assets/chitauri_units.py [--preview] [--only captor,heavy,commander]
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import chitauri_lib as L  # noqa: E402
import chitauri_soldier as S  # noqa: E402
from chitauri_lib import Model, Clip, Mat, write_anims  # noqa: E402

TETHER = (60, 230, 200)
TETHER_HOT = (205, 255, 245)
PLASMA = (255, 138, 48)
PLASMA_HOT = (255, 226, 170)
VIOLET = (165, 105, 255)
VIOLET_HOT = (236, 222, 255)


def eye_of(colour, alpha=150):
    def fn(face, rgb, glow, rs):
        rgb[...] = (16, 14, 20)
        if glow is not None and face.name == 'north':
            h, w = rgb.shape[:2]
            glow[..., :3] = colour
            glow[..., 3] = alpha
            glow[h // 2:, :, 3] = alpha // 2
    return fn


def vent_fn(colour):
    """Grill: dark slots with a faint inner glow on the front faces."""
    def fn(face, rgb, glow, rs):
        h, w = rgb.shape[:2]
        for y in range(0, h, 2):
            rgb[y, :] *= 0.45
            if glow is not None and face.name in ('north', 'south') and h >= 3:
                glow[y, 1:max(1, w - 1), :3] = colour
                glow[y, 1:max(1, w - 1), 3] = 90
    return fn


def mats():
    M = L.mats()
    M['tether'] = Mat('#2fbfa5', noise=0, edge=0, fn=L.energy_glow(TETHER, TETHER_HOT))
    M['tether_soft'] = Mat('#2a9f8c', noise=0, edge=0, fn=L.energy_glow(TETHER, TETHER_HOT, core=0.0, alpha=190))
    M['plasma'] = Mat('#d9782c', noise=0, edge=0, fn=L.energy_glow(PLASMA, PLASMA_HOT))
    M['plasma_soft'] = Mat('#b8642a', noise=0, edge=0, fn=L.energy_glow(PLASMA, PLASMA_HOT, core=0.0, alpha=190))
    M['violet'] = Mat('#8a5ae0', noise=0, edge=0, fn=L.energy_glow(VIOLET, VIOLET_HOT))
    M['violet_soft'] = Mat('#7046c0', noise=0, edge=0, fn=L.energy_glow(VIOLET, VIOLET_HOT, core=0.0, alpha=180))
    M['eye_teal'] = Mat('#101418', noise=0, edge=0, fn=eye_of(TETHER, 170))
    M['eye_amber'] = Mat('#141010', noise=0, edge=0, fn=eye_of(PLASMA, 160))
    M['eye_gold'] = Mat('#141210', noise=0, edge=0, fn=eye_of((255, 210, 120), 170))
    M['vent'] = Mat(L.ALLOY, noise=4, edge=0.2, fn=vent_fn(PLASMA))
    M['leather'] = Mat('#3a2f27', noise=7, edge=0.16, fn=L.hose_fn)
    M['gunmetal'] = Mat('#3a3d44', noise=5, edge=0.24, top=1.2, bottom=0.7, fn=L.armour_fn(seams=True, rivets=True, scratches=4))
    M['gunmetal_dark'] = Mat('#25272c', noise=5, edge=0.22, fn=L.armour_fn(seams=False, rivets=True, scratches=2))
    M['royal'] = Mat('#443a4e', noise=6, edge=0.2, fn=L.armour_fn(seams=False, rivets=False, scratches=1, trim=L.GOLD))
    M['cloth'] = Mat('#2c2236', noise=8, edge=0.12, top=1.05, bottom=0.85)
    M['cloth_trim'] = Mat('#6b5130', noise=6, edge=0.14)
    return M


M = mats()


# ---- shared joint layout (identical pivots to chitauri_soldier: the hand solvers rely on them) ----------------
def make(identifier, seed, density=2):
    m = Model(identifier, density=density, width=256, seed=seed)

    def box(bone, a, b, mat, **kw):
        x0, y0, z0 = a
        x1, y1, z1 = b
        return m.cube(bone, [min(x0, x1), min(y0, y1), min(z0, z1)], [abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)], M[mat], **kw)
    return m, box


def skeleton(m):
    m.bone('root', None, [0, 0, 0])
    m.bone('hips', 'root', [0, 15.2, 0])
    m.bone('leg_r', 'hips', [-1.75, 15.0, 0])
    m.bone('shin_r', 'leg_r', [-1.75, 8.7, -0.1])
    m.bone('foot_r', 'shin_r', [-1.75, 2.0, 0.0])
    m.bone('torso', 'hips', [0, 17.0, 0])
    m.bone('chest', 'torso', [0, 20.2, 0])
    m.bone('pauldron_r', 'chest', [-4.0, 25.6, 0])
    m.bone('neck', 'chest', [0, 25.8, -0.2])
    m.bone('head', 'neck', [0, 27.3, -0.3])
    m.bone('jaw', 'head', [0, 27.8, -0.9])
    m.bone('arm_r', 'chest', [-4.4, 24.7, 0])
    m.bone('forearm_r', 'arm_r', [-4.5, 18.7, 0])
    m.bone('hand_r', 'forearm_r', [-4.5, 13.2, 0])


def hand(m, box, skin='skin', dark='skin_dark', claw='bone', plate='bronze_dark', thick=0.0):
    """The soldier's three-fingered hand with two-joint fingers and a thumb (right side; mirrored later)."""
    t = thick
    box('hand_r', (-5.25 - t, 11.3, -0.85 - t), (-3.75 + t, 13.3, 0.85 + t), skin)
    box('hand_r', (-5.4 - t, 12.1, -0.95 - t), (-5.15 - t, 13.2, 0.95 + t), plate)
    for k, z in enumerate((-0.62, 0.0, 0.62)):
        f = 'finger_r%d' % (k + 1)
        m.bone(f, 'hand_r', [-4.5, 11.35, z])
        box(f, (-4.77, 10.1, z - 0.27), (-4.23, 11.4, z + 0.27), skin)
        m.bone(f + 'b', f, [-4.5, 10.15, z])
        box(f + 'b', (-4.74, 9.0, z - 0.24), (-4.26, 10.2, z + 0.24), dark)
        box(f + 'b', (-4.7, 8.6, z - 0.2), (-4.3, 9.05, z + 0.2), claw)
    m.bone('thumb_r', 'hand_r', [-4.1, 12.6, -0.8])
    box('thumb_r', (-4.32, 11.5, -1.32), (-3.82, 12.7, -0.82), skin, rotation=[0, 0, -18], pivot=[-4.1, 12.6, -0.8])
    m.bone('thumb_rb', 'thumb_r', [-3.75, 11.55, -1.07])
    box('thumb_rb', (-3.98, 10.6, -1.3), (-3.52, 11.6, -0.84), dark)


def weapon_bones(m, muzzle, fore):
    gx, gy, gz = S.GRIP
    m.bone('rifle', 'hand_r', [gx, gy, gz])
    m.bone('muzzle', 'rifle', [gx + muzzle[0], gy + muzzle[1], gz + muzzle[2]])
    m.bone('grip_front', 'rifle', [gx + fore[0], gy + fore[1], gz + fore[2]])


# ---- Captor ------------------------------------------------------------------------------------------------
CAPTOR = dict(name='captor', butt=(0.0, 4.6, -1.7), fore=(0.0, -3.9, -0.55), muzzle=(0.0, -17.2, -1.95))


def build_captor():
    m, box = make('chitauri_captor', 3301)
    skeleton(m)
    # pelvis: bare hide, leather harness, restraint cuffs hanging from the belt
    box('hips', (-2.7, 14.0, -1.6), (2.7, 17.0, 1.7), 'skin_dark')
    box('hips', (-2.95, 15.6, -1.85), (2.95, 16.5, 1.95), 'leather')
    box('hips', (-0.6, 15.5, -2.1), (0.6, 16.6, -1.8), 'gold_dark')
    box('hips', (-1.1, 13.2, -2.0), (1.1, 15.6, -1.5), 'bronze_dark')
    for x in (-2.4, 1.5):                                                               # cuff rings
        box('hips', (x, 14.2, -1.95), (x + 0.9, 15.6, -1.55), 'alloy_light')
        box('hips', (x + 0.2, 14.5, -2.05), (x + 0.7, 15.3, -1.9), 'tether_soft')
    box('hips', (-0.8, 12.4, 1.5), (0.8, 15.6, 2.2), 'leather', rotation=[12, 0, 0], pivot=[0, 15.6, 1.8])  # tail flap
    # legs: thin, long shins, back-swept spurs, long toe claws
    box('leg_r', (-2.8, 8.6, -1.2), (-0.7, 15.2, 1.2), 'skin')
    box('leg_r', (-2.95, 10.4, -1.45), (-0.55, 13.6, -1.05), 'leather')
    box('leg_r', (-3.05, 9.4, -0.9), (-2.7, 13.0, 0.9), 'hose')
    box('shin_r', (-2.6, 2.0, -0.9), (-0.9, 8.8, 0.9), 'skin')
    box('shin_r', (-2.8, 7.5, -1.4), (-0.7, 9.1, -0.7), 'bronze_dark')
    box('shin_r', (-2.2, 5.4, 0.8), (-1.3, 8.6, 2.0), 'bone', rotation=[-24, 0, 0], pivot=[-1.75, 8.4, 0.9])   # spur
    box('shin_r', (-2.5, 2.6, -1.25), (-1.0, 6.8, -0.85), 'leather')
    box('foot_r', (-2.6, 0.0, -2.2), (-0.9, 2.1, 1.3), 'skin_dark')
    for x in (-2.55, -1.75, -0.95):
        box('foot_r', (x - 0.05, 0.0, -4.4), (x + 0.65, 0.8, -2.2), 'bone')
    box('foot_r', (-2.1, 0.3, 1.2), (-1.4, 1.5, 2.6), 'bone')
    # abdomen: exposed ribs, crossed harness straps
    box('torso', (-2.2, 16.8, -1.3), (2.2, 20.5, 1.3), 'skin')
    for y in (17.3, 18.4, 19.5):
        box('torso', (-1.9, y, -1.55), (1.9, y + 0.55, -1.2), 'skin_dark')
    box('torso', (-0.7, 16.8, 1.15), (0.7, 20.5, 1.9), 'alloy')
    # chest: lean, a light breast plate, harness X, padded carry shoulder (left), back rig
    box('chest', (-3.3, 20.2, -1.8), (3.3, 25.6, 1.9), 'skin')
    box('chest', (-2.6, 21.6, -2.3), (2.6, 25.0, -1.7), 'bronze_dark')
    box('chest', (-0.45, 21.8, -2.5), (0.45, 24.6, -2.2), 'gold_dark')
    box('chest', (-3.4, 20.4, -2.1), (3.4, 21.0, 2.1), 'leather')                       # waist strap
    box('chest', (-0.4, 20.0, -2.45), (0.4, 26.4, -2.15), 'leather', rotation=[0, 0, 32], pivot=[0, 23.2, -2.3])
    box('chest', (-0.4, 20.0, -2.5), (0.4, 26.4, -2.2), 'leather', rotation=[0, 0, -32], pivot=[0, 23.2, -2.35])
    box('chest', (-0.55, 22.9, -2.75), (0.55, 23.6, -2.45), 'alloy_light')               # strap buckle
    box('chest', (-2.4, 25.0, -1.7), (2.4, 26.0, 1.7), 'bronze_plain')
    box('chest', (-2.8, 20.8, 1.8), (2.8, 25.2, 2.4), 'leather')
    for y in (20.9, 22.1, 23.3):
        box('chest', (-3.65, y, -1.2), (-3.25, y + 0.7, 1.2), 'skin_dark')
        box('chest', (3.25, y, -1.2), (3.65, y + 0.7, 1.2), 'skin_dark')
    m.bone('rig', 'chest', [0, 24.0, 2.6])
    box('rig', (-1.2, 20.6, 2.3), (1.2, 25.6, 3.3), 'alloy')
    box('rig', (-0.5, 21.4, 3.25), (0.5, 24.8, 3.55), 'tether_soft')
    box('rig', (-2.4, 24.6, 2.4), (2.4, 25.4, 3.2), 'alloy_light')
    m.bone('clamp_r', 'rig', [-2.2, 25.0, 2.9])
    box('clamp_r', (-2.6, 25.0, 2.5), (-1.8, 30.4, 3.3), 'alloy_light', rotation=[0, 0, 28], pivot=[-2.2, 25.0, 2.9])
    box('clamp_r', (-5.5, 29.2, 2.55), (-4.7, 31.0, 3.25), 'bone', rotation=[0, 0, 28], pivot=[-2.2, 25.0, 2.9])
    box('clamp_r', (-5.2, 28.4, 2.7), (-4.8, 29.2, 3.1), 'tether', rotation=[0, 0, 28], pivot=[-2.2, 25.0, 2.9])
    box('pauldron_r', (-5.4, 24.2, -1.5), (-3.3, 25.8, 1.5), 'bronze_dark', rotation=[0, 0, 12], pivot=[-4.0, 25.6, 0])
    # neck / head: narrow predatory skull, sensor cluster (four eyes), split mandibles, swept frill
    box('neck', (-1.0, 25.6, -1.1), (1.0, 27.6, 0.9), 'skin')
    box('neck', (-1.4, 25.8, -1.4), (-1.0, 27.4, -0.9), 'hose')
    box('neck', (1.0, 25.8, -1.4), (1.4, 27.4, -0.9), 'hose')
    box('head', (-1.5, 27.4, -2.6), (1.5, 30.6, 1.0), 'skin')
    box('head', (-1.2, 27.9, 0.9), (1.2, 30.2, 3.6), 'skin_dark', rotation=[-14, 0, 0], pivot=[0, 29.0, 1.0])  # long back skull
    box('head', (-1.65, 28.3, -2.95), (1.65, 30.0, -2.3), 'bronze_dark')                 # sensor mask
    for x, y in ((-1.25, 29.3), (0.45, 29.3), (-0.95, 28.55), (0.35, 28.55)):
        box('head', (x, y, -3.1), (x + 0.8, y + 0.5, -2.9), 'eye_teal')
    box('head', (-0.3, 30.2, -2.8), (0.3, 31.0, 2.6), 'bone', rotation=[-10, 0, 0], pivot=[0, 30.6, 0])     # frill spine
    for s in (-1, 1):
        box('head', (s * 1.2 - 0.25, 29.6, 0.6), (s * 1.2 + 0.25, 30.4, 3.4), 'bone', rotation=[-22, 0, s * 18], pivot=[s * 1.2, 30.0, 0.8])
    box('jaw', (-1.3, 26.6, -2.75), (-0.15, 27.9, -0.6), 'bronze_dark', rotation=[0, -8, 0], pivot=[-0.7, 27.8, -0.9])  # split mandible
    box('jaw', (0.15, 26.6, -2.75), (1.3, 27.9, -0.6), 'bronze_dark', rotation=[0, 8, 0], pivot=[0.7, 27.8, -0.9])
    box('jaw', (-1.0, 27.75, -2.82), (1.0, 28.15, -2.5), 'bone')
    # arms: long and wiry, leather wraps, restraint launcher on the forearm (both: mirrored)
    box('arm_r', (-5.4, 18.6, -0.95), (-3.6, 24.9, 0.95), 'skin')
    box('arm_r', (-5.6, 20.6, -1.05), (-3.4, 22.2, 1.05), 'leather')
    box('forearm_r', (-5.3, 13.2, -0.9), (-3.7, 18.8, 0.9), 'skin')
    box('forearm_r', (-5.55, 13.6, -1.15), (-3.45, 16.6, 1.15), 'alloy')
    box('forearm_r', (-5.7, 14.2, -0.35), (-5.5, 16.0, 0.35), 'tether_soft')
    box('forearm_r', (-4.9, 17.6, 0.8), (-4.1, 19.0, 1.6), 'bone')
    hand(m, box, claw='bone')
    m.mirror_x('_r', '_l')
    # the padded carry shoulder: thick hide pad + straps on the left
    box('pauldron_l', (3.2, 24.0, -2.0), (6.2, 26.6, 2.0), 'leather', rotation=[0, 0, -10], pivot=[4.0, 25.6, 0])
    box('pauldron_l', (3.4, 26.3, -1.6), (5.9, 26.9, 1.6), 'bronze_dark', rotation=[0, 0, -10], pivot=[4.0, 25.6, 0])
    box('pauldron_l', (5.8, 23.4, -1.4), (6.5, 25.4, 1.4), 'alloy_light', rotation=[0, 0, -10], pivot=[4.0, 25.6, 0])
    # snare lance: forked clamp head with a tether arc between the prongs, coil cell, short stock
    weapon_bones(m, CAPTOR['muzzle'], CAPTOR['fore'])
    gx, gy, gz = S.GRIP
    box('rifle', (gx - 0.6, gy - 2.4, gz - 2.8), (gx + 0.6, gy + 2.8, gz - 1.25), 'alloy')
    box('rifle', (gx - 0.5, gy - 0.7, gz - 1.3), (gx + 0.5, gy + 0.7, gz + 0.8), 'alloy_light')
    box('rifle', (gx - 0.5, gy + 2.8, gz - 2.4), (gx + 0.5, gy + 4.4, gz - 1.0), 'leather')
    box('rifle', (gx - 0.65, gy + 4.3, gz - 2.6), (gx + 0.65, gy + 5.0, gz - 0.8), 'bronze_dark')
    box('rifle', (gx - 0.4, gy - 14.0, gz - 2.35), (gx + 0.4, gy - 2.4, gz - 1.65), 'alloy')
    box('rifle', (gx - 0.62, gy - 4.6, gz - 2.65), (gx + 0.62, gy - 3.2, gz - 1.35), 'leather')
    for k in range(4):                                                                  # coil
        y = gy - 6.0 - k * 0.9
        box('rifle', (gx - 0.7, y - 0.4, gz - 2.75), (gx + 0.7, y, gz - 1.25), 'gold_dark')
    box('rifle', (gx - 0.5, gy - 9.6, gz - 2.6), (gx + 0.5, gy - 5.6, gz - 1.4), 'tether_soft')
    box('rifle', (gx - 1.6, gy - 14.6, gz - 2.4), (gx + 1.6, gy - 13.8, gz - 1.6), 'bronze_dark')   # fork yoke
    for s in (-1, 1):
        box('rifle', (gx + s * 1.25 - 0.3, gy - 18.0, gz - 2.3), (gx + s * 1.25 + 0.3, gy - 14.4, gz - 1.7), 'bronze_dark',
            rotation=[0, 0, s * 8], pivot=[gx + s * 1.25, gy - 14.4, gz - 2.0])
        box('rifle', (gx + s * 1.0 - 0.25, gy - 18.6, gz - 2.25), (gx + s * 1.0 + 0.25, gy - 17.8, gz - 1.75), 'bone',
            rotation=[0, 0, -s * 30], pivot=[gx + s * 1.0, gy - 17.8, gz - 2.0])
    box('rifle', (gx - 0.9, gy - 17.4, gz - 2.1), (gx + 0.9, gy - 16.9, gz - 1.9), 'tether')       # arc
    box('rifle', (gx - 0.25, gy - 16.6, gz - 2.2), (gx + 0.25, gy - 14.6, gz - 1.8), 'tether_soft')
    return m


# ---- Heavy -------------------------------------------------------------------------------------------------
HEAVY = dict(name='heavy', butt=(0.0, 6.6, -2.4), fore=(0.0, -4.7, -0.9), muzzle=(0.0, -16.4, -2.4))


def build_heavy():
    m, box = make('chitauri_heavy', 4402)
    skeleton(m)
    # pelvis: armoured skirt, heavy belt with cells
    box('hips', (-3.2, 13.8, -2.0), (3.2, 17.0, 2.0), 'skin_dark')
    box('hips', (-3.6, 15.4, -2.4), (3.6, 16.9, 2.4), 'gunmetal_dark')
    for x in (-2.6, -0.6, 1.4):
        box('hips', (x, 15.6, -2.7), (x + 1.2, 16.7, -2.35), 'plasma_soft')
    box('hips', (-1.8, 12.0, -2.7), (1.8, 15.5, -1.9), 'gunmetal', rotation=[8, 0, 0], pivot=[0, 15.5, -2.2])
    box('hips', (-1.8, 12.2, 1.9), (1.8, 15.5, 2.7), 'gunmetal', rotation=[-8, 0, 0], pivot=[0, 15.5, 2.2])
    box('hips', (-4.3, 12.4, -1.8), (-3.3, 16.4, 1.8), 'gunmetal', rotation=[0, 0, 10], pivot=[-3.6, 16.4, 0])
    box('hips', (3.3, 12.4, -1.8), (4.3, 16.4, 1.8), 'gunmetal', rotation=[0, 0, -10], pivot=[3.6, 16.4, 0])
    # legs: plated thighs, big knee guards, armoured shins, broad sabatons
    box('leg_r', (-3.2, 8.6, -1.6), (-0.3, 15.2, 1.6), 'skin')
    box('leg_r', (-3.6, 9.6, -1.95), (-0.2, 14.4, -1.4), 'gunmetal')
    box('leg_r', (-3.7, 9.8, -1.4), (-3.1, 14.2, 1.4), 'gunmetal_dark')
    box('leg_r', (-2.6, 9.2, 1.5), (-0.9, 13.0, 2.0), 'hose')
    box('shin_r', (-3.0, 2.0, -1.3), (-0.5, 8.8, 1.3), 'skin_dark')
    box('shin_r', (-3.4, 7.0, -2.2), (-0.1, 9.8, -0.9), 'gunmetal')                      # knee guard
    box('shin_r', (-2.6, 9.4, -2.35), (-0.9, 10.2, -1.6), 'bronze_dark')
    box('shin_r', (-3.3, 2.4, -1.9), (-0.2, 7.2, -1.1), 'gunmetal')
    box('shin_r', (-3.35, 2.6, -1.1), (-2.9, 7.0, 1.3), 'gunmetal_dark')
    box('shin_r', (-2.6, 3.6, 1.2), (-0.9, 7.4, 1.9), 'hose')
    box('foot_r', (-3.2, 0.0, -2.9), (-0.3, 2.4, 1.9), 'gunmetal_dark')
    box('foot_r', (-3.3, 1.6, -2.5), (-0.2, 2.8, 1.0), 'gunmetal')
    for x in (-3.0, -2.0, -1.0):
        box('foot_r', (x, 0.0, -4.2), (x + 0.8, 1.1, -2.9), 'alloy_light')
    box('foot_r', (-2.2, 0.0, 1.9), (-1.3, 1.4, 2.8), 'gunmetal_dark')
    # abdomen: segmented armour corset
    box('torso', (-2.8, 16.8, -1.7), (2.8, 20.5, 1.7), 'skin')
    for y in (16.9, 18.1, 19.3):
        box('torso', (-3.1, y, -2.2), (3.1, y + 1.05, -1.6), 'gunmetal')
        box('torso', (-3.1, y, 1.6), (3.1, y + 1.05, 2.2), 'gunmetal_dark')
    box('torso', (-3.3, 17.2, -1.4), (-2.8, 20.2, 1.4), 'hose')
    box('torso', (2.8, 17.2, -1.4), (3.3, 20.2, 1.4), 'hose')
    # chest: deep barrel chest; the breakable front plate is its own bone over a glowing core
    box('chest', (-3.9, 20.2, -2.2), (3.9, 25.8, 2.3), 'skin')
    box('chest', (-1.2, 21.6, -2.45), (1.2, 24.4, -2.15), 'plasma')                       # core (seen when broken)
    box('chest', (-3.6, 20.6, -2.35), (-1.6, 25.0, -2.05), 'gunmetal_dark')
    box('chest', (1.6, 20.6, -2.35), (3.6, 25.0, -2.05), 'gunmetal_dark')
    box('chest', (-3.3, 25.4, -2.4), (3.3, 26.8, 2.4), 'gunmetal')                        # gorget
    box('chest', (-2.4, 26.6, -2.0), (2.4, 27.5, 1.2), 'gunmetal_dark')
    box('chest', (-3.6, 20.4, 2.2), (3.6, 25.6, 3.0), 'gunmetal')                         # back plate
    m.bone('armor_chest', 'chest', [0, 23.0, -2.6])
    box('armor_chest', (-3.5, 20.6, -3.1), (3.5, 25.4, -2.3), 'gunmetal', inflate=0.05)
    box('armor_chest', (-2.7, 21.2, -3.45), (2.7, 24.8, -3.05), 'gunmetal_dark')
    box('armor_chest', (-0.6, 21.4, -3.75), (0.6, 24.6, -3.4), 'bronze')
    for y in (21.6, 23.4):
        box('armor_chest', (-3.3, y, -3.25), (-2.5, y + 1.2, -2.95), 'bronze_dark')
        box('armor_chest', (2.5, y, -3.25), (3.3, y + 1.2, -2.95), 'bronze_dark')
    m.bone('pack', 'chest', [0, 23.4, 3.0])
    box('pack', (-2.6, 20.6, 2.9), (2.6, 26.2, 5.6), 'gunmetal_dark')
    box('pack', (-1.6, 21.4, 5.5), (1.6, 25.2, 6.0), 'vent')
    box('pack', (-0.8, 22.4, 5.9), (0.8, 24.2, 6.2), 'plasma_soft')
    for s in (-1, 1):
        box('pack', (s * 2.6 - 0.5, 21.0, 3.2), (s * 2.6 + 0.5, 25.8, 5.2), 'hose')
    for s in (-1,):                                                                         # vent_l by mirror
        b = 'vent_r'
        m.bone(b, 'pack', [s * 1.8, 26.2, 4.2])
        box(b, (s * 1.8 - 0.9, 26.0, 3.2), (s * 1.8 + 0.9, 28.6, 5.0), 'gunmetal')
        box(b, (s * 1.8 - 0.6, 28.4, 3.5), (s * 1.8 + 0.6, 28.8, 4.7), 'vent')
    # massive layered pauldrons with grills
    for k, (y0, w) in enumerate(((23.4, 0.0), (22.4, 0.5), (21.4, 0.9))):
        box('pauldron_r', (-7.2 - w, y0, -2.6 + 0.2 * k), (-3.0, y0 + 3.4 - k * 0.6, 2.6 - 0.2 * k), 'gunmetal' if k == 0 else 'gunmetal_dark',
            rotation=[0, 0, 16], pivot=[-4.0, 25.6, 0])
    box('pauldron_r', (-6.2, 26.4, -1.6), (-4.0, 27.4, 1.6), 'vent', rotation=[0, 0, 16], pivot=[-4.0, 25.6, 0])
    box('pauldron_r', (-7.6, 23.2, -0.4), (-7.0, 25.8, 0.4), 'bronze', rotation=[0, 0, 16], pivot=[-4.0, 25.6, 0])
    # neck sunk in the gorget; helmet with a visor bone, the head bone keeps the jaw
    box('neck', (-1.3, 25.6, -1.3), (1.3, 27.6, 1.0), 'skin_dark')
    box('head', (-1.9, 27.2, -2.4), (1.9, 30.6, 1.5), 'skin_dark')
    box('head', (-2.3, 28.0, -2.6), (2.3, 31.2, 2.0), 'gunmetal', inflate=0.05)          # helmet shell
    box('head', (-1.7, 31.0, -2.0), (1.7, 31.8, 1.6), 'gunmetal_dark')
    box('head', (-0.5, 31.4, -2.4), (0.5, 32.2, 2.2), 'bronze_dark')                     # ridge
    box('head', (-2.5, 27.6, -1.4), (-2.1, 30.6, 1.6), 'gunmetal_dark')                  # cheek guards
    box('head', (2.1, 27.6, -1.4), (2.5, 30.6, 1.6), 'gunmetal_dark')
    m.bone('visor', 'head', [0, 29.4, -2.6])
    box('visor', (-2.1, 28.6, -2.95), (2.1, 30.0, -2.55), 'gunmetal_dark')
    box('visor', (-1.7, 29.05, -3.05), (1.7, 29.55, -2.9), 'eye_amber')
    box('jaw', (-1.7, 26.4, -2.7), (1.7, 28.0, -0.6), 'gunmetal_dark')                   # breather
    box('jaw', (-1.0, 26.8, -2.95), (1.0, 27.6, -2.65), 'vent')
    # arms: thick hide under rerebraces and vambraces, armoured gauntlets
    box('arm_r', (-5.8, 18.6, -1.3), (-3.2, 24.9, 1.3), 'skin')
    box('arm_r', (-6.2, 19.4, -1.5), (-5.5, 23.8, 1.5), 'gunmetal')
    box('arm_r', (-5.9, 19.0, -1.55), (-3.1, 20.4, 1.55), 'gunmetal_dark')
    box('forearm_r', (-5.6, 13.2, -1.2), (-3.4, 18.8, 1.2), 'skin')
    box('forearm_r', (-6.0, 13.5, -1.6), (-3.0, 17.6, 1.6), 'gunmetal')
    box('forearm_r', (-6.15, 14.4, -0.4), (-5.95, 16.6, 0.4), 'plasma_soft')
    box('forearm_r', (-5.2, 17.6, 1.0), (-3.8, 19.4, 2.2), 'gunmetal_dark')
    hand(m, box, plate='gunmetal_dark', claw='alloy_light', thick=0.12)
    m.mirror_x('_r', '_l')
    # plasma cannon: chunky receiver, drum cell, ringed barrel, muzzle brake, carry handle
    weapon_bones(m, HEAVY['muzzle'], HEAVY['fore'])
    gx, gy, gz = S.GRIP
    box('rifle', (gx - 1.1, gy - 3.6, gz - 3.6), (gx + 1.1, gy + 3.6, gz - 1.2), 'gunmetal')
    box('rifle', (gx - 0.55, gy - 0.8, gz - 1.3), (gx + 0.55, gy + 0.8, gz + 1.0), 'alloy')
    box('rifle', (gx - 0.8, gy + 3.4, gz - 3.2), (gx + 0.8, gy + 6.4, gz - 1.6), 'gunmetal_dark')
    box('rifle', (gx - 1.0, gy + 6.2, gz - 3.6), (gx + 1.0, gy + 7.2, gz - 1.0), 'alloy')
    box('rifle', (gx - 1.5, gy - 3.0, gz - 3.0), (gx + 1.5, gy + 0.6, gz - 1.0), 'gunmetal_dark', rotation=[0, 0, 0])
    box('rifle', (gx - 1.65, gy - 2.6, gz - 2.7), (gx + 1.65, gy + 0.2, gz - 1.3), 'plasma')                    # drum cell
    box('rifle', (gx - 0.75, gy - 14.6, gz - 3.25), (gx + 0.75, gy - 3.6, gz - 1.75), 'gunmetal_dark')
    for k in range(4):
        y = gy - 5.0 - k * 2.4
        box('rifle', (gx - 1.0, y - 0.7, gz - 3.5), (gx + 1.0, y, gz - 1.5), 'bronze_dark')
    box('rifle', (gx - 0.5, gy - 12.6, gz - 3.4), (gx + 0.5, gy - 5.2, gz - 3.15), 'plasma_soft')
    box('rifle', (gx - 1.25, gy - 16.6, gz - 3.6), (gx + 1.25, gy - 14.4, gz - 1.4), 'gunmetal')               # muzzle brake
    box('rifle', (gx - 0.55, gy - 16.8, gz - 2.85), (gx + 0.55, gy - 16.5, gz - 2.15), 'plasma')
    box('rifle', (gx - 0.4, gy - 4.4, gz - 5.0), (gx + 0.4, gy - 0.6, gz - 4.2), 'alloy_light')                # carry handle
    box('rifle', (gx - 0.4, gy - 4.4, gz - 4.4), (gx + 0.4, gy - 3.6, gz - 3.4), 'alloy_light')
    box('rifle', (gx - 0.4, gy - 1.4, gz - 4.4), (gx + 0.4, gy - 0.6, gz - 3.4), 'alloy_light')
    box('rifle', (gx - 0.7, gy - 6.2, gz - 2.0), (gx + 0.7, gy - 4.2, gz - 0.4), 'gunmetal_dark')              # fore grip
    return m


# ---- Commander ---------------------------------------------------------------------------------------------
COMMANDER = dict(name='commander', butt=(0.0, 10.4, -1.95), fore=(0.0, -4.4, -0.5), muzzle=(0.0, -19.6, -1.95))


def build_commander():
    m, box = make('chitauri_commander', 5503)
    skeleton(m)
    # pelvis: ornate belt, front / back tabards on their own bones (they swing)
    box('hips', (-3.0, 14.0, -1.7), (3.0, 17.0, 1.8), 'skin_dark')
    box('hips', (-3.3, 15.5, -2.05), (3.3, 16.8, 2.1), 'royal')
    box('hips', (-1.0, 15.3, -2.35), (1.0, 17.0, -1.95), 'gold')
    box('hips', (-0.45, 15.8, -2.5), (0.45, 16.5, -2.3), 'violet')
    m.bone('tabard_f', 'hips', [0, 15.6, -2.1])
    box('tabard_f', (-1.5, 8.6, -2.35), (1.5, 15.6, -1.95), 'cloth')
    box('tabard_f', (-1.6, 8.2, -2.45), (1.6, 8.9, -1.9), 'cloth_trim')
    box('tabard_f', (-0.4, 10.0, -2.5), (0.4, 14.6, -2.3), 'gold_dark')
    m.bone('tabard_b', 'hips', [0, 15.6, 2.1])
    box('tabard_b', (-1.8, 8.0, 1.95), (1.8, 15.6, 2.35), 'cloth')
    box('tabard_b', (-1.9, 7.6, 1.9), (1.9, 8.3, 2.45), 'cloth_trim')
    box('hips', (-3.7, 13.0, -1.3), (-3.0, 16.4, 1.5), 'royal', rotation=[0, 0, 8], pivot=[-3.3, 16.4, 0])
    box('hips', (3.0, 13.0, -1.3), (3.7, 16.4, 1.5), 'royal', rotation=[0, 0, -8], pivot=[3.3, 16.4, 0])
    # legs: long greaves with gold trims
    box('leg_r', (-3.0, 8.6, -1.35), (-0.5, 15.2, 1.35), 'skin')
    box('leg_r', (-3.3, 10.0, -1.7), (-0.6, 14.4, -1.2), 'royal')
    box('leg_r', (-3.35, 10.2, -1.1), (-2.85, 14.0, 1.1), 'bronze')
    box('shin_r', (-2.8, 2.0, -1.0), (-0.7, 8.8, 1.0), 'skin')
    box('shin_r', (-3.0, 7.2, -1.7), (-0.5, 9.6, -0.7), 'gold')
    box('shin_r', (-2.9, 2.6, -1.55), (-0.6, 7.4, -0.9), 'royal')
    box('shin_r', (-2.1, 6.0, -1.85), (-1.4, 9.2, -1.5), 'gold_dark')
    box('shin_r', (-2.5, 4.2, 0.9), (-1.0, 7.8, 1.5), 'skin_dark')
    box('foot_r', (-2.8, 0.0, -2.8), (-0.7, 2.2, 1.6), 'royal')
    box('foot_r', (-2.6, 0.0, -4.0), (-0.9, 0.8, -2.8), 'gold_dark')
    box('foot_r', (-2.2, 0.2, 1.6), (-1.3, 1.6, 2.4), 'gold')
    # abdomen / chest: ornate cuirass, sternum gem, high collar
    box('torso', (-2.5, 16.8, -1.5), (2.5, 20.5, 1.5), 'skin')
    for y in (17.1, 18.3, 19.5):
        box('torso', (-2.2, y, -1.85), (2.2, y + 0.85, -1.4), 'royal')
    box('torso', (-0.8, 16.8, 1.3), (0.8, 20.5, 2.1), 'gold_dark')
    box('chest', (-3.6, 20.2, -2.0), (3.6, 25.8, 2.1), 'skin')
    box('chest', (-3.35, 20.8, -2.55), (3.35, 25.4, -1.9), 'royal')
    box('chest', (-2.6, 21.4, -2.85), (2.6, 24.8, -2.45), 'bronze')
    box('chest', (-0.7, 22.4, -3.1), (0.7, 24.0, -2.8), 'violet')
    box('chest', (-1.4, 24.2, -3.0), (1.4, 24.7, -2.75), 'gold')
    box('chest', (-1.4, 21.8, -3.0), (1.4, 22.3, -2.75), 'gold')
    box('chest', (-3.0, 25.2, -2.2), (3.0, 27.4, -1.4), 'gold', rotation=[-12, 0, 0], pivot=[0, 25.2, -1.8])     # high collar
    box('chest', (-3.0, 25.2, 1.2), (3.0, 28.0, 2.2), 'royal')
    box('chest', (-3.2, 20.8, 1.95), (3.2, 25.4, 2.7), 'royal')
    for k, b in enumerate(('cape1', 'cape2', 'cape3')):
        y = 25.4 - k * 5.0
        m.bone(b, 'chest' if k == 0 else ('cape1', 'cape2')[k - 1], [0, y, 2.8 + k * 0.3])
        box(b, (-3.4 + k * 0.3, y - 5.1, 2.7 + k * 0.3), (3.4 - k * 0.3, y, 3.1 + k * 0.3), 'cloth')
        box(b, (-3.5 + k * 0.3, y - 5.1, 2.65 + k * 0.3), (-3.2 + k * 0.3, y, 3.15 + k * 0.3), 'cloth_trim')
        box(b, (3.2 - k * 0.3, y - 5.1, 2.65 + k * 0.3), (3.5 - k * 0.3, y, 3.15 + k * 0.3), 'cloth_trim')
    box('cape3', (-2.9, 10.0, 3.2), (2.9, 10.6, 3.8), 'gold_dark')
    box('cape1', (-1.2, 21.6, 3.05), (1.2, 24.4, 3.3), 'gold')                             # hive sigil
    box('cape1', (-0.4, 22.4, 3.25), (0.4, 23.6, 3.4), 'violet_soft')
    box('cape2', (-0.5, 16.0, 3.35), (0.5, 20.4, 3.6), 'gold_dark')
    # pauldrons: tall, spiked, gold-trimmed
    box('pauldron_r', (-6.4, 23.6, -2.1), (-3.1, 26.4, 2.1), 'royal', rotation=[0, 0, 18], pivot=[-4.0, 25.6, 0])
    box('pauldron_r', (-6.8, 22.6, -1.8), (-6.0, 24.8, 1.8), 'gold', rotation=[0, 0, 18], pivot=[-4.0, 25.6, 0])
    box('pauldron_r', (-5.6, 26.2, -0.4), (-4.8, 29.6, 0.4), 'gold_dark', rotation=[0, 0, 28], pivot=[-5.2, 26.2, 0])   # spike
    box('pauldron_r', (-5.4, 25.8, -1.4), (-3.6, 26.8, 1.4), 'gold', rotation=[0, 0, 18], pivot=[-4.0, 25.6, 0])
    # head: crowned skull, gold mask, antenna fins that flare when it signals
    box('neck', (-1.1, 25.6, -1.2), (1.1, 27.6, 0.9), 'skin')
    box('neck', (-0.5, 25.8, 0.8), (0.5, 27.6, 1.5), 'gold_dark')
    box('head', (-1.8, 27.4, -2.3), (1.8, 31.0, 1.2), 'skin')
    box('head', (-1.5, 28.0, 1.1), (1.5, 30.8, 3.0), 'skin_dark')
    box('head', (-1.95, 28.3, -2.65), (1.95, 30.2, -1.9), 'gold')                         # mask
    box('head', (-1.5, 29.0, -2.78), (-0.4, 29.55, -2.6), 'eye_gold')
    box('head', (0.4, 29.0, -2.78), (1.5, 29.55, -2.6), 'eye_gold')
    box('head', (-2.1, 30.4, -2.3), (2.1, 31.4, 1.5), 'gold_dark')                         # crown band
    for k, x in enumerate((-1.6, -0.6, 0.4)):
        hgt = (2.0, 3.0, 2.0)[k]
        box('head', (x, 31.2, -2.1), (x + 0.8 if k != 1 else x + 0.2, 31.2 + hgt, -1.5), 'gold')
    box('head', (-0.3, 31.2, -2.2), (0.3, 34.6, -1.4), 'gold')                             # crown centre spike
    box('head', (-0.25, 32.4, -2.3), (0.25, 33.0, -2.15), 'violet')
    for s, b in ((-1, 'antenna_r'),):                                                      # antenna_l by mirror
        m.bone(b, 'head', [s * 1.6, 30.6, 0.6])
        box(b, (s * 1.6 - 0.3, 30.4, 0.3), (s * 1.6 + 0.3, 31.2, 4.6), 'gold_dark', rotation=[-28, 0, s * 20], pivot=[s * 1.6, 30.6, 0.6])
        box(b, (s * 1.6 - 0.2, 30.5, 4.2), (s * 1.6 + 0.2, 31.1, 4.9), 'violet_soft', rotation=[-28, 0, s * 20], pivot=[s * 1.6, 30.6, 0.6])
    box('jaw', (-1.55, 26.5, -2.55), (1.55, 27.9, -0.4), 'royal')
    box('jaw', (-1.2, 27.75, -2.62), (1.2, 28.2, -2.3), 'bone')
    box('jaw', (-0.4, 25.6, -2.4), (0.4, 26.6, -1.4), 'gold')
    # arms: long, royal bracers with gold
    box('arm_r', (-5.6, 18.6, -1.1), (-3.4, 24.9, 1.1), 'skin')
    box('arm_r', (-6.0, 19.8, -1.0), (-5.45, 23.8, 1.0), 'royal')
    box('forearm_r', (-5.45, 13.2, -1.0), (-3.55, 18.8, 1.0), 'skin')
    box('forearm_r', (-5.8, 13.6, -1.35), (-3.2, 17.4, 1.35), 'royal')
    box('forearm_r', (-5.9, 16.8, -1.4), (-3.1, 17.5, 1.4), 'gold')
    box('forearm_r', (-5.95, 14.4, -0.3), (-5.75, 16.2, 0.3), 'violet_soft')
    hand(m, box, plate='gold_dark')
    m.mirror_x('_r', '_l')
    # war glaive: long staff, curved gold blade with a violet edge, counter-weight, banner ribbons
    weapon_bones(m, COMMANDER['muzzle'], COMMANDER['fore'])
    gx, gy, gz = S.GRIP
    box('rifle', (gx - 0.5, gy - 12.6, gz - 2.3), (gx + 0.5, gy + 9.6, gz - 1.6), 'bronze_plain')
    box('rifle', (gx - 0.55, gy - 0.8, gz - 2.4), (gx + 0.55, gy + 0.8, gz - 1.5), 'royal')
    box('rifle', (gx - 0.62, gy - 5.0, gz - 2.55), (gx + 0.62, gy - 3.6, gz - 1.35), 'gold_dark')
    box('rifle', (gx - 0.7, gy - 8.8, gz - 2.6), (gx + 0.7, gy - 6.0, gz - 1.3), 'violet')
    for y in (gy - 9.2, gy - 6.0):
        box('rifle', (gx - 0.85, y, gz - 2.75), (gx + 0.85, y + 0.5, gz - 1.15), 'gold')
    box('rifle', (gx - 0.8, gy + 9.0, gz - 2.8), (gx + 0.8, gy + 11.0, gz - 1.1), 'gold')                     # counter-weight
    box('rifle', (gx - 0.4, gy + 10.9, gz - 2.3), (gx + 0.4, gy + 11.6, gz - 1.6), 'violet_soft')
    box('rifle', (gx - 1.1, gy - 13.4, gz - 2.8), (gx + 1.1, gy - 12.4, gz - 1.1), 'gold')                    # blade socket
    box('rifle', (gx - 0.3, gy - 20.4, gz - 3.4), (gx + 0.3, gy - 13.2, gz - 1.6), 'gold', rotation=[8, 0, 0], pivot=[gx, gy - 13.2, gz - 2.0])
    box('rifle', (gx - 0.18, gy - 20.0, gz - 3.75), (gx + 0.18, gy - 13.6, gz - 3.35), 'violet', rotation=[8, 0, 0], pivot=[gx, gy - 13.2, gz - 2.0])
    box('rifle', (gx - 0.3, gy - 16.6, gz - 1.7), (gx + 0.3, gy - 13.4, gz - 0.6), 'gold_dark', rotation=[-30, 0, 0], pivot=[gx, gy - 13.4, gz - 1.4])
    box('rifle', (gx - 0.2, gy - 14.4, gz - 1.6), (gx + 0.2, gy - 10.6, gz - 1.2), 'cloth', rotation=[0, 0, 6], pivot=[gx, gy - 12.4, gz - 1.4])  # ribbon
    return m


# ---- poses -------------------------------------------------------------------------------------------------
fist, relaxed, merge, clip_pose = S.fist, S.relaxed, S.merge, S.clip_pose
