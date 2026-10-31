#!/usr/bin/env python3
"""Chitauri foot soldier: geo (~160 cubes: jointed fingers and thumbs, armour plates, hoses, the
staff rifle with its energy cell), atlas + glow, and every clip (idle, walk, run, aim, shoot, melee,
hurt, fall, land, death, sever, mounted, mounted_pilot). The off hand is solved onto the rifle's
fore grip in every rifle pose (chitauri_lib.reach). Own work, CC0.

Run: python3 tools/assets/chitauri_soldier.py [--preview]
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import chitauri_lib as L  # noqa: E402
from chitauri_lib import Model, Clip, write_anims  # noqa: E402

GEO = 'geo/chitauri/soldier.geo.json'
ANIM = 'animations/chitauri/soldier.animation.json'            # locomotion + combat
ANIM_STATES = 'animations/chitauri/soldier_states.animation.json'   # fall, land, death, sever, mounted
STATES = ('fall', 'land', 'death', 'sever', 'mounted', 'mounted_shoot', 'mounted_pilot')
TEX = 'textures/entity/chitauri/soldier.png'
GLOW = 'textures/entity/chitauri/soldier_glow.png'

M = L.mats()
GRIP = (-4.6, 11.9, -0.35)   # right palm centre (rest); the rifle hangs from it along -Y, its top toward -Z
PALM_L = (4.5, 12.1, 0.0)    # left palm centre (rest)
BUTT = (0.0, 6.0, -1.75)     # butt plate centre, rifle-local offset from GRIP
FORE = (0.0, -4.1, -0.55)    # off-hand point under the fore grip collar, offset from GRIP


def build():
    m = Model('chitauri_soldier', density=2, width=256, seed=2012)

    def box(bone, a, b, mat, **kw):
        x0, y0, z0 = a
        x1, y1, z1 = b
        return m.cube(bone, [min(x0, x1), min(y0, y1), min(z0, z1)], [abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)], M[mat], **kw)

    m.bone('root', None, [0, 0, 0])
    # ---- pelvis --------------------------------------------------------------------------------
    m.bone('hips', 'root', [0, 15.2, 0])
    box('hips', (-3.0, 14.0, -1.7), (3.0, 17.0, 1.8), 'skin_dark')
    box('hips', (-3.25, 15.5, -2.0), (3.25, 16.7, 2.05), 'alloy')                   # belt
    box('hips', (-0.7, 15.6, -2.25), (0.7, 16.6, -1.95), 'gold')                    # buckle
    box('hips', (-1.3, 13.0, -2.25), (1.3, 15.6, -1.6), 'bronze')                   # groin plate
    box('hips', (-0.9, 13.4, 1.6), (0.9, 15.6, 2.3), 'alloy')                       # tail guard
    box('hips', (-3.5, 13.6, -1.2), (-2.9, 16.2, 1.4), 'bronze', rotation=[0, 0, 8], pivot=[-3.2, 16.2, 0])   # hip plates
    box('hips', (2.9, 13.6, -1.2), (3.5, 16.2, 1.4), 'bronze', rotation=[0, 0, -8], pivot=[3.2, 16.2, 0])
    # ---- right leg (mirrored) ------------------------------------------------------------------
    m.bone('leg_r', 'hips', [-1.75, 15.0, 0])
    box('leg_r', (-3.05, 8.6, -1.4), (-0.45, 15.2, 1.4), 'skin')
    box('leg_r', (-3.35, 10.0, -1.15), (-2.85, 14.6, 1.15), 'bronze')               # outer thigh plate
    box('leg_r', (-2.85, 10.6, -1.75), (-0.65, 14.2, -1.25), 'bronze_plain')        # front thigh plate
    box('leg_r', (-2.6, 9.0, 1.2), (-0.9, 12.6, 1.7), 'hose')                       # rear tendon hose
    m.bone('shin_r', 'leg_r', [-1.75, 8.7, -0.1])
    box('shin_r', (-2.85, 2.0, -1.05), (-0.65, 8.8, 1.05), 'skin')
    box('shin_r', (-3.0, 7.4, -1.6), (-0.5, 9.4, -0.8), 'bronze')                   # knee cap
    box('shin_r', (-2.75, 2.8, -1.55), (-0.75, 7.4, -0.95), 'bronze_plain')         # shin guard
    box('shin_r', (-2.6, 4.2, 0.9), (-0.9, 7.8, 1.6), 'skin_dark')                  # calf
    box('shin_r', (-3.05, 3.2, -0.6), (-2.75, 6.6, 0.8), 'alloy')                   # outer strut
    m.bone('foot_r', 'shin_r', [-1.75, 2.0, 0.0])
    box('foot_r', (-2.85, 0.0, -2.6), (-0.65, 2.2, 1.6), 'alloy')
    box('foot_r', (-2.75, 0.0, -3.9), (-1.95, 0.9, -2.6), 'alloy_light')            # toe claws
    box('foot_r', (-1.55, 0.0, -3.9), (-0.75, 0.9, -2.6), 'alloy_light')
    box('foot_r', (-2.2, 0.2, 1.6), (-1.3, 1.6, 2.3), 'bronze_dark')                # heel spur
    box('foot_r', (-2.95, 1.6, -2.2), (-0.55, 2.4, 0.6), 'bronze_dark')             # ankle cuff
    # ---- abdomen / chest -----------------------------------------------------------------------
    m.bone('torso', 'hips', [0, 17.0, 0])
    box('torso', (-2.5, 16.8, -1.5), (2.5, 20.5, 1.5), 'skin')
    for i, y in enumerate((17.2, 18.3, 19.4)):                                       # ab ridges
        box('torso', (-1.9, y, -1.75), (1.9, y + 0.7, -1.4), 'skin_dark')
    box('torso', (-0.8, 16.8, 1.3), (0.8, 20.5, 2.1), 'alloy')                       # spine
    box('torso', (-2.9, 17.6, -1.0), (-2.4, 20.0, 1.0), 'hose')                      # side hoses
    box('torso', (2.4, 17.6, -1.0), (2.9, 20.0, 1.0), 'hose')
    m.bone('chest', 'torso', [0, 20.2, 0])
    box('chest', (-3.6, 20.2, -2.0), (3.6, 25.8, 2.1), 'skin')
    box('chest', (-3.25, 21.0, -2.5), (3.25, 25.2, -1.9), 'bronze')                  # breast plate
    box('chest', (-0.6, 21.2, -2.75), (0.6, 24.8, -2.4), 'gold')                     # sternum keel
    box('chest', (-2.7, 25.2, -1.9), (2.7, 26.2, 1.9), 'bronze_plain')               # collar
    box('chest', (-3.1, 20.8, 1.95), (3.1, 25.4, 2.7), 'bronze')                     # back plate
    box('chest', (-1.5, 21.4, 2.6), (1.5, 25.0, 3.9), 'alloy_lit')                   # spine pack
    box('chest', (-0.5, 22.0, 3.85), (0.5, 24.4, 4.15), 'energy_soft')               # pack light
    box('chest', (-2.2, 21.0, -2.75), (-1.6, 24.6, -2.35), 'hose')                   # chest hoses up to the jaw
    box('chest', (1.6, 21.0, -2.75), (2.2, 24.6, -2.35), 'hose')
    box('chest', (-3.9, 20.6, -1.4), (-3.4, 24.0, 1.4), 'skin_dark')                 # ribs flank
    box('chest', (3.4, 20.6, -1.4), (3.9, 24.0, 1.4), 'skin_dark')
    m.bone('pauldron_r', 'chest', [-4.0, 25.6, 0])
    box('pauldron_r', (-6.0, 23.8, -1.9), (-3.2, 26.0, 1.9), 'bronze', rotation=[0, 0, 14], pivot=[-4.0, 25.6, 0])
    box('pauldron_r', (-6.3, 23.0, -1.6), (-5.6, 24.6, 1.6), 'bronze_dark', rotation=[0, 0, 14], pivot=[-4.0, 25.6, 0])
    box('pauldron_r', (-5.2, 25.6, -1.2), (-3.8, 26.6, 1.2), 'gold', rotation=[0, 0, 14], pivot=[-4.0, 25.6, 0])
    # ---- neck / head ---------------------------------------------------------------------------
    m.bone('neck', 'chest', [0, 25.8, -0.2])
    box('neck', (-1.1, 25.6, -1.2), (1.1, 27.6, 0.9), 'skin')
    box('neck', (-1.6, 25.8, -1.5), (-1.1, 27.4, -1.0), 'hose')
    box('neck', (1.1, 25.8, -1.5), (1.6, 27.4, -1.0), 'hose')
    box('neck', (-0.5, 25.8, 0.8), (0.5, 27.6, 1.5), 'alloy')
    m.bone('head', 'neck', [0, 27.3, -0.3])
    box('head', (-1.8, 27.4, -2.3), (1.8, 31.0, 1.2), 'skin')                         # skull
    box('head', (-1.4, 28.0, 1.1), (1.4, 30.6, 2.7), 'skin_dark')                      # elongated back skull
    box('head', (-1.95, 29.7, -2.6), (1.95, 30.5, -1.4), 'gold')                       # brow band
    box('head', (-0.65, 30.4, -1.9), (0.65, 31.8, 2.4), 'bronze_plain', rotation=[-12, 0, 0], pivot=[0, 30.8, 0])  # crest
    box('head', (-0.4, 31.2, 0.8), (0.4, 32.2, 2.9), 'gold_dark', rotation=[-24, 0, 0], pivot=[0, 31.0, 1.0])      # crest fin
    box('head', (-2.05, 27.8, -2.1), (-1.55, 29.6, 0.2), 'bronze')                     # cheek plates
    box('head', (1.55, 27.8, -2.1), (2.05, 29.6, 0.2), 'bronze')
    box('head', (-0.35, 28.2, -2.6), (0.35, 29.7, -2.2), 'skin_dark')                  # nose ridge
    box('head', (-1.55, 28.9, -2.42), (-0.5, 29.45, -2.25), 'eye')                      # eyes
    box('head', (0.5, 28.9, -2.42), (1.55, 29.45, -2.25), 'eye')
    box('head', (-2.1, 28.4, 0.2), (-1.7, 29.4, 1.0), 'hose')                          # ear tubes
    box('head', (1.7, 28.4, 0.2), (2.1, 29.4, 1.0), 'hose')
    m.bone('jaw', 'head', [0, 27.8, -0.9])
    box('jaw', (-1.55, 26.5, -2.55), (1.55, 27.9, -0.4), 'bronze')                     # jaw plate
    box('jaw', (-1.2, 27.75, -2.62), (1.2, 28.2, -2.3), 'bone')                        # teeth
    box('jaw', (-0.5, 26.2, -2.3), (0.5, 26.6, -1.2), 'alloy')                         # chin spike
    # ---- right arm (mirrored): long, lanky alien limbs -----------------------------------------
    m.bone('arm_r', 'chest', [-4.4, 24.7, 0])
    box('arm_r', (-5.6, 18.6, -1.1), (-3.4, 24.9, 1.1), 'skin')
    box('arm_r', (-5.95, 19.8, -0.95), (-5.45, 23.8, 0.95), 'bronze')                  # biceps plate
    box('arm_r', (-5.2, 19.2, 1.0), (-3.8, 22.6, 1.5), 'hose')
    m.bone('forearm_r', 'arm_r', [-4.5, 18.7, 0])
    box('forearm_r', (-5.45, 13.2, -1.0), (-3.55, 18.8, 1.0), 'skin')
    box('forearm_r', (-5.75, 13.7, -1.3), (-3.25, 17.3, 1.3), 'bronze')                # bracer
    box('forearm_r', (-5.0, 17.8, 0.9), (-4.0, 19.2, 1.8), 'bronze_dark')              # elbow spur
    box('forearm_r', (-5.85, 14.6, -0.25), (-5.65, 16.2, 0.25), 'energy_soft')           # bracer light
    m.bone('hand_r', 'forearm_r', [-4.5, 13.2, 0])
    box('hand_r', (-5.25, 11.3, -0.85), (-3.75, 13.3, 0.85), 'skin')
    box('hand_r', (-5.4, 12.1, -0.95), (-5.15, 13.2, 0.95), 'bronze_dark')             # back of hand plate
    for k, z in enumerate((-0.62, 0.0, 0.62)):                                          # three fingers, two joints
        f = 'finger_r%d' % (k + 1)
        m.bone(f, 'hand_r', [-4.5, 11.35, z])
        box(f, (-4.77, 10.1, z - 0.27), (-4.23, 11.4, z + 0.27), 'skin')
        m.bone(f + 'b', f, [-4.5, 10.15, z])
        box(f + 'b', (-4.74, 9.0, z - 0.24), (-4.26, 10.2, z + 0.24), 'skin_dark')
        box(f + 'b', (-4.7, 8.75, z - 0.2), (-4.3, 9.05, z + 0.2), 'bone')             # claw
    m.bone('thumb_r', 'hand_r', [-4.1, 12.6, -0.8])
    box('thumb_r', (-4.32, 11.5, -1.32), (-3.82, 12.7, -0.82), 'skin', rotation=[0, 0, -18], pivot=[-4.1, 12.6, -0.8])
    m.bone('thumb_rb', 'thumb_r', [-3.75, 11.55, -1.07])
    box('thumb_rb', (-3.98, 10.6, -1.3), (-3.52, 11.6, -0.84), 'skin_dark')
    m.mirror_x('_r', '_l')
    # ---- the staff rifle (in the right hand, along -Y at rest: forward once the arm aims) --------
    gx, gy, gz = GRIP
    m.bone('rifle', 'hand_r', [gx, gy, gz])
    box('rifle', (gx - 0.75, gy - 3.0, gz - 3.0), (gx + 0.75, gy + 3.2, gz - 1.2), 'bronze')            # receiver
    box('rifle', (gx - 0.8, gy - 1.6, gz - 3.1), (gx + 0.8, gy - 1.0, gz - 1.1), 'gold')                # receiver ring
    box('rifle', (gx - 0.5, gy - 0.7, gz - 1.25), (gx + 0.5, gy + 0.7, gz + 0.9), 'alloy')              # pistol grip
    box('rifle', (gx - 0.55, gy + 1.0, gz - 1.3), (gx + 0.55, gy + 1.5, gz + 0.3), 'alloy_light')       # trigger guard
    box('rifle', (gx - 0.55, gy + 3.2, gz - 2.6), (gx + 0.55, gy + 5.8, gz - 1.0), 'alloy')              # stock
    box('rifle', (gx - 0.7, gy + 5.6, gz - 2.9), (gx + 0.7, gy + 6.4, gz - 0.6), 'bronze_dark')         # butt plate
    box('rifle', (gx - 0.45, gy - 13.2, gz - 2.45), (gx + 0.45, gy - 3.0, gz - 1.6), 'alloy')            # staff
    box('rifle', (gx - 0.62, gy - 4.8, gz - 2.7), (gx + 0.62, gy - 3.4, gz - 1.3), 'bronze_dark')       # fore grip collar
    box('rifle', (gx - 0.62, gy - 8.6, gz - 2.85), (gx + 0.62, gy - 5.6, gz - 1.2), 'energy')           # energy cell
    box('rifle', (gx - 0.8, gy - 9.0, gz - 3.05), (gx + 0.8, gy - 8.5, gz - 1.0), 'gold')               # cell rings
    box('rifle', (gx - 0.8, gy - 5.7, gz - 3.05), (gx + 0.8, gy - 5.2, gz - 1.0), 'gold')
    box('rifle', (gx - 0.3, gy - 13.9, gz - 2.3), (gx + 0.3, gy - 13.1, gz - 1.75), 'energy')            # muzzle emitter
    box('rifle', (gx - 0.35, gy - 16.0, gz - 2.2), (gx + 0.35, gy - 12.4, gz - 1.7), 'gold',             # blade prong (upper)
        rotation=[14, 0, 0], pivot=[gx, gy - 12.4, gz - 1.9])
    box('rifle', (gx - 0.3, gy - 15.0, gz - 1.4), (gx + 0.3, gy - 12.6, gz - 0.95), 'gold_dark',         # lower prong
        rotation=[-18, 0, 0], pivot=[gx, gy - 12.6, gz - 1.2])
    box('rifle', (gx - 0.5, gy - 4.6, gz - 3.4), (gx + 0.5, gy - 1.8, gz - 2.95), 'bronze_plain')       # top rail
    m.bone('muzzle', 'rifle', [gx, gy - 15.2, gz - 1.95])
    m.bone('grip_front', 'rifle', [gx + FORE[0], gy + FORE[1], gz + FORE[2]])
    return m


# ---- poses ---------------------------------------------------------------------------------------------
def fist(side, curl=1.0):
    """Fingers closed around a grip (rz curls towards the palm; mirrored on the left)."""
    s = -1 if side == 'r' else 1
    p = {}
    for k in (1, 2, 3):
        p['finger_%s%d' % (side, k)] = (0, 0, s * 62 * curl)
        p['finger_%s%db' % (side, k)] = (0, 0, s * 74 * curl)
    p['thumb_%s' % side] = (0, 0, s * 26 * curl)
    p['thumb_%sb' % side] = (0, 0, s * 30 * curl)
    return p


def relaxed(side):
    return fist(side, 0.32)


GEO_JSON = None


def geo():
    global GEO_JSON
    if GEO_JSON is None:
        with open(os.path.join(L.RES, GEO)) as fh:
            GEO_JSON = json.load(fh)
    return GEO_JSON


def clip_pose(q):
    """FK pose -> clip pose (rotation-only bones as tuples)."""
    out = {}
    for k, v in q.items():
        if set(v.keys()) == {'rot'}:
            out[k] = tuple(round(x, 2) for x in v['rot'])
        else:
            out[k] = {kk: [round(x, 3) for x in vv] for kk, vv in v.items()}
    return out


def _xf(p, space, ref):
    """4x4 taking model-space values given for pose `ref` (or the bone's rest space) into pose p."""
    if space is None:
        return np.eye(4)
    m = L.bone_matrix(geo(), L.as_pose(p), space)
    if ref is not None:
        m = m @ np.linalg.inv(L.bone_matrix(geo(), L.as_pose(ref), space))
    return m


def hold(p, D, U, G=None, butt=None, space=None, ref=None, pole_r=(-1.0, -0.7, 0.5), pole_l=(1.0, -1.0, 0.1),
         roll=25.0, two_hands=True, curl_l=0.9, left_at=None, left_frame=None):
    """Both hands on the staff rifle. D / U: barrel direction and top; G: grip point (or butt: butt plate point in
    the chest's rest space). Values are model space, or relative to `space` bone (for pose `ref`, else its rest)."""
    g = geo()
    X = _xf(p, space, ref)
    D = X[:3, :3] @ L.unit(D)
    U = X[:3, :3] @ np.array(U, float)
    R = L.frame(D, U)
    if G is None:
        B = L.bone_matrix(g, L.as_pose(p), 'chest') @ np.array(list(butt) + [1.0])
        G = B[:3] - R @ np.array(BUTT)
    else:
        G = (X @ np.array(list(G) + [1.0]))[:3]
    q = L.as_pose(p)
    q.update(L.as_pose(fist('r')))
    q, er = L.place_hand(g, q, 'r', GRIP, G, R, pole_r)
    el = 0.0
    if two_hands:
        F = G + R @ np.array(FORE)
        d = R @ np.array([0, -1.0, 0])
        u = R @ np.array([0, 0, -1.0])
        side = np.cross(d, u)
        a = math.radians(roll)
        palm = L.unit(u * math.cos(a) - side * math.sin(a))
        Xh, Zh = -palm, -d
        q.update(L.as_pose(fist('l', curl_l)))
        q, el = L.place_hand(g, q, 'l', PALM_L, F, np.column_stack([Xh, np.cross(Zh, Xh), Zh]), pole_l)
    elif left_at is not None:
        q.update(L.as_pose(fist('l', curl_l)))
        q, el = L.place_hand(g, q, 'l', PALM_L, left_at, left_frame, pole_l)
    if er > 0.35 or el > 0.35:
        print('  hold misses: right %.2f px, left %.2f px' % (er, el))
    return clip_pose(q)


def body_low(breath=0.0, look=0.0, bob=0.0):
    return {
        'torso': (3, 8, 0),
        'chest': (4 + breath * 1.5, 12, 0),
        'neck': (-4, -8 + look * 0.4, 0),
        'head': (-2 + bob, -12 + look * 0.6, 0),
        'pauldron_r': (-6, 0, 0),
        'pauldron_l': (-8, 0, 0),
    }


REF_LOW = body_low()
LOW = dict(D=(0.26, -0.42, -0.87), U=(0.0, 1.0, 0.0), G=(-2.3, 18.6, -3.6))


def low_ready(base=None, breath=0.0, look=0.0, bob=0.0, over=None):
    """Rifle across the body, muzzle forward-down to the left, both hands on it; rigid with the chest."""
    p = dict(base or {})
    p.update(body_low(breath, look, bob))
    p.update(over or {})
    return hold(p, LOW['D'], LOW['U'], G=LOW['G'], space='chest', ref=REF_LOW)


def body_aim(sway=0.0, recoil=0.0):
    return {
        'torso': (4, 14, 0),
        'chest': (-2 + 0.6 * sway - 4 * recoil, 20, 0),
        'neck': (4, -12, 0),
        'head': (10 - 3 * recoil, -21, -8),
        'jaw': (2 + 10 * recoil, 0, 0),
        'pauldron_r': (-16, 0, 0),
        'pauldron_l': (-10, 0, 0),
    }


AIM_BUTT = (-1.8, 24.3, -3.1)


def shouldered(sway=0.0, recoil=0.0):
    """Rifle at the right shoulder, aiming straight ahead; recoil kicks the muzzle up and the butt back."""
    p = body_aim(sway, recoil)
    k = math.radians(1.2 * sway + 8.0 * recoil)
    D = (0.05, math.sin(k), -math.cos(k))
    butt = (AIM_BUTT[0], AIM_BUTT[1], AIM_BUTT[2] + 0.7 * recoil)
    return hold(p, D, (0, 1, 0), butt=butt, pole_r=(-1.0, -0.35, 0.35), pole_l=(0.7, -1.0, 0.0), roll=20)


def stride(phase, big=False):
    """Leg pose of the walk / run cycle at phase 0..1 (right leg forward at 0)."""
    a = 2 * math.pi * phase
    swing = 38 if big else 26
    knee = 70 if big else 40
    s = math.sin(a)
    c = math.cos(a)
    lift_r = max(0.0, -math.sin(a - 0.6))
    lift_l = max(0.0, math.sin(a - 0.6))
    p = {
        'leg_r': (-swing * s - (10 if big else 4), 0, 3),
        'shin_r': (knee * lift_r + 6, 0, 0),
        'foot_r': (swing * 0.35 * s - knee * 0.25 * lift_r, 0, 0),
        'leg_l': (swing * s - (10 if big else 4), 0, -3),
        'shin_l': (knee * lift_l + 6, 0, 0),
        'foot_l': (-swing * 0.35 * s - knee * 0.25 * lift_l, 0, 0),
        'hips': {'rot': [(14 if big else 4), 7 * s, 3 * c], 'pos': [0, -abs(c) * (1.2 if big else 0.6) - (0.8 if big else 0.2), 0]},
    }
    return p


UPPER = ('torso', 'chest', 'neck', 'head', 'jaw', 'pauldron_r', 'pauldron_l', 'arm_r', 'forearm_r', 'hand_r', 'arm_l',
         'forearm_l', 'hand_l', 'rifle') + tuple('finger_%s%d%s' % (s, k, b) for s in 'rl' for k in (1, 2, 3) for b in ('', 'b')) \
    + tuple('thumb_%s%s' % (s, b) for s in 'rl' for b in ('', 'b'))


def upper(p):
    return {k: v for k, v in p.items() if k in UPPER}


def build_clips():
    clips = []
    # idle: low ready, breathing, scanning the street, weight on the right leg
    idle = Clip('idle', 3.2, loop=True)
    for i in range(9):
        t = i * 0.4
        f = math.sin(2 * math.pi * t / 3.2)
        look = 18 * math.sin(2 * math.pi * t / 3.2 * 0.5 + 0.4) if i < 8 else 18 * math.sin(0.4)
        base = {'hips': {'rot': [2, 3, 2 + f], 'pos': [0.3, -0.25 - 0.15 * f, 0]},
                'leg_r': (-4, 0, 2), 'shin_r': (8, 0, 0), 'foot_r': (-4, 0, 0),
                'leg_l': (6, -6, -5), 'shin_l': (14, 0, 0), 'foot_l': (-8, 0, 0)}
        p = low_ready(base, breath=f, look=look)
        p['jaw'] = (4 + 3 * max(0, f), 0, 0)
        idle.key(t, p)
    clips.append(idle)
    # walk: rifle at low ready, steady stride, head level
    walk = Clip('walk', 1.0, loop=True)
    for i in range(9):
        t = i / 8.0
        p = low_ready(stride(t), bob=1.5 * math.cos(4 * math.pi * t),
                      over={'torso': (5, 8 - 6 * math.sin(2 * math.pi * t), -2 * math.cos(2 * math.pi * t))})
        walk.key(t * 1.0, p)
    clips.append(walk)
    # run: leaning in, rifle at port arms across the chest, long stride
    run = Clip('run', 0.62, loop=True)
    for i in range(9):
        t = i / 8.0
        sn = math.sin(2 * math.pi * t)
        p = stride(t, big=True)
        p.update({'torso': (8, 6 - 8 * sn, -2 * math.cos(2 * math.pi * t)), 'chest': (12, 8 + 6 * sn, 0),
                  'neck': (-12, -6, 0), 'head': (-12, -8 - 4 * sn, 0), 'pauldron_r': (-8, 0, 0), 'pauldron_l': (-8, 0, 0)})
        p = hold(p, (0.5, 0.9, -0.2), (0.1, -0.2, -1.0), G=(-2.5, 19.6, -4.0), space='chest',
                 pole_r=(-1.0, -1.0, 0.4), pole_l=(1.0, -0.6, 0.0), roll=-10)
        run.key(t * 0.62, p)
    clips.append(run)
    # aim: shouldered, breathing sway (upper body only, laid over idle / walk)
    aim = Clip('aim', 2.0, loop=True)
    for i in range(5):
        t = i * 0.5
        aim.key(t, upper(shouldered(sway=math.sin(2 * math.pi * t / 2.0))))
    clips.append(aim)
    # shoot: recoil kick and settle (laid over aim)
    shoot = Clip('shoot', 0.36)
    for t, r in ((0.0, 0.0), (0.04, 1.0), (0.12, 0.55), (0.24, 0.15), (0.36, 0.0)):
        shoot.key(t, upper(shouldered(recoil=r)))
    clips.append(shoot)
    # melee: chamber back over the right shoulder, slash the blade down across the body, recover
    melee = Clip('melee', 0.72)
    lr = upper(low_ready())
    melee.key(0.0, lr)
    wind = hold({'torso': (-6, 26, 0), 'chest': (-12, 24, 6), 'neck': (0, -18, 0), 'head': (-8, -24, 0), 'jaw': (14, 0, 0),
                 'pauldron_r': (-20, 0, 0), 'pauldron_l': (-6, 0, 0)},
                (-0.25, 0.8, -0.55), (0.6, 0.3, 0.75), G=(-3.6, 23.0, -1.0), pole_r=(-1.0, -0.4, 0.6), pole_l=(1.0, -1.0, -0.2), roll=0)
    melee.key(0.2, upper(wind))
    cut = hold({'torso': (14, -24, 0), 'chest': (18, -18, -4), 'neck': (-4, 14, 0), 'head': (-6, 18, 0), 'jaw': (22, 0, 0),
                'pauldron_r': (-8, 0, 0), 'pauldron_l': (-12, 0, 0)},
               (0.55, -0.6, -0.58), (-0.5, 0.3, -0.6), G=(-1.2, 19.0, -6.4), pole_r=(-1.0, -0.6, 0.3), pole_l=(1.0, -0.8, 0.4), roll=0)
    melee.key(0.34, upper(cut))
    follow = hold({'torso': (12, -30, 0), 'chest': (14, -22, -6), 'neck': (-4, 18, 0), 'head': (-4, 22, 0), 'jaw': (12, 0, 0),
                   'pauldron_r': (-6, 0, 0), 'pauldron_l': (-10, 0, 0)},
                  (0.8, -0.55, -0.2), (-0.4, 0.2, -0.9), G=(0.4, 17.6, -5.2), pole_r=(-0.6, -1.0, 0.0), pole_l=(1.0, -0.6, 0.6), roll=0)
    melee.key(0.5, upper(follow))
    melee.key(0.72, lr)
    clips.append(melee)
    # hurt: flinch backwards, head snaps (additive overlay on the upper body)
    hurt = Clip('hurt', 0.42)
    hurt.key(0.0, {'chest': (0, 0, 0), 'head': (0, 0, 0), 'neck': (0, 0, 0), 'torso': (0, 0, 0)})
    hurt.key(0.06, {'chest': (-16, 8, 6), 'head': (-22, 10, 8), 'neck': (-8, 0, 0), 'torso': (-8, 0, 0)})
    hurt.key(0.2, {'chest': (-6, 4, 2), 'head': (-8, 4, 2), 'neck': (-2, 0, 0), 'torso': (-3, 0, 0)})
    hurt.key(0.42, {'chest': (0, 0, 0), 'head': (0, 0, 0), 'neck': (0, 0, 0), 'torso': (0, 0, 0)})
    clips.append(hurt)
    # fall: thrown or dropped, limbs out, rifle still in the right hand
    fall = Clip('fall', 0.8, loop=True)
    for i in range(5):
        t = i * 0.2
        w = math.sin(2 * math.pi * t / 0.8)
        p = {'hips': (-8 + 4 * w, 0, 0), 'torso': (-6, 0, 0), 'chest': (-14, 0, 4 * w), 'neck': (-6, 0, 0), 'head': (-16, 0, 0),
             'jaw': (18, 0, 0),
             'arm_r': (-60 + 16 * w, 10, 72), 'forearm_r': (-34 - 10 * w, 0, 0), 'hand_r': (0, 0, 0),
             'arm_l': (-50 - 16 * w, -10, -78), 'forearm_l': (-28 + 10 * w, 0, 0), 'hand_l': (0, 0, 0),
             'leg_r': (-30 + 14 * w, 0, 10), 'shin_r': (60, 0, 0), 'foot_r': (0, 0, 0),
             'leg_l': (-10 - 14 * w, 0, -10), 'shin_l': (40, 0, 0), 'foot_l': (0, 0, 0),
             'pauldron_r': (0, 0, 0), 'pauldron_l': (0, 0, 0)}
        p.update(fist('r'))
        p.update(relaxed('l'))
        fall.key(t, p)
    clips.append(fall)
    # land: crouch absorbs the drop, rises to low ready
    land = Clip('land', 0.6)
    for t, d in ((0.0, 1.0), (0.12, 1.0), (0.35, 0.45), (0.6, 0.0)):
        base = {'hips': {'rot': [26 * d, 0, 0], 'pos': [0, -4.2 * d, 0]},
                'leg_r': (-58 * d, 0, 6), 'shin_r': (92 * d, 0, 0), 'foot_r': (-34 * d, 0, 0),
                'leg_l': (-44 * d, 0, -8), 'shin_l': (96 * d, 0, 0), 'foot_l': (-50 * d, 0, 0)}
        land.key(t, low_ready(base, over={'torso': (3 + 12 * d, 8, 0), 'head': (-2 - 14 * d, -12, 0)}))
    clips.append(land)
    # death: hit, knees buckle, collapses forward onto the street and lies still
    death = Clip('death', 3.5)
    z = {'root': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}
    death.key(0.0, merge(low_ready(), z))
    death.key(0.12, merge(low_ready(), {'chest': (-18, 10, 8), 'head': (-24, 14, 0), 'jaw': (20, 0, 0), 'arm_l': (-40, -20, -40),
                                        'forearm_l': (-30, 0, 0), 'hand_l': (0, 0, 0)}, z))
    death.key(0.45, {'root': {'rot': [12, 0, 0], 'pos': [0, -3.5, 0]}, 'hips': (20, 0, 0), 'leg_r': (-70, 0, 8), 'shin_r': (110, 0, 0),
                     'foot_r': (-30, 0, 0), 'leg_l': (-50, 0, -6), 'shin_l': (100, 0, 0), 'foot_l': (-40, 0, 0), 'torso': (6, 0, 0),
                     'chest': (20, 0, 0), 'neck': (0, 0, 0), 'head': (24, 0, 10), 'arm_r': (-20, 0, 30), 'forearm_r': (-30, 0, 0),
                     'hand_r': (0, 0, 0), 'arm_l': (-10, 0, -30), 'forearm_l': (-20, 0, 0), 'jaw': (24, 0, 0)})
    lie = {'root': {'rot': [88, 0, 0], 'pos': [0, 1.5, -15]}, 'hips': (-6, 0, 0), 'leg_r': (-8, 0, 6), 'shin_r': (22, 0, 0),
           'foot_r': (20, 0, 0), 'leg_l': (6, 0, -4), 'shin_l': (30, 0, 0), 'foot_l': (24, 0, 0), 'torso': (0, 0, 0),
           'chest': (-6, 0, 0), 'neck': (-10, 0, 0), 'head': (-24, 32, 0),
           'arm_r': (-160, 10, 40), 'forearm_r': (-10, 0, 0), 'hand_r': (0, 0, 0), 'arm_l': (-20, 0, -50), 'forearm_l': (-40, 0, 0),
           'jaw': (28, 0, 0)}
    death.key(0.9, lie)
    death.key(1.05, merge(lie, {'root': {'rot': [84, 0, 0], 'pos': [0, 2.2, -15]}, 'head': (-28, 30, 0), 'arm_r': (-150, 10, 44)}))
    death.key(1.25, merge(lie, {'root': {'rot': [90, 0, 0], 'pos': [0, 1.6, -15.5]}}))
    death.key(3.5, merge(lie, {'root': {'rot': [90, 0, 0], 'pos': [0, 1.6, -15.5]}}))
    death.keep.update({'root'})
    clips.append(death)
    # sever: cut off from the hive: a jolt, convulsion, the body drops backwards
    sever = Clip('sever', 3.5)
    sever.key(0.0, merge(low_ready(), z))
    sever.key(0.08, merge(low_ready(), {'chest': (-24, 0, 0), 'head': (-38, 0, 0), 'jaw': (34, 0, 0), 'arm_l': (-50, -30, -60),
                                        'forearm_l': (-20, 0, 0), 'hand_l': (0, 0, 0)}, z))
    sever.key(0.2, merge(low_ready(), {'chest': (-8, 6, 10), 'head': (-14, -20, 6), 'jaw': (26, 0, 0), 'arm_l': (-40, -10, -40),
                                       'forearm_l': (-30, 0, 0), 'hand_l': (0, 0, 0)}, z))
    sever.key(0.32, merge(low_ready(), {'chest': (-22, -6, -10), 'head': (-30, 20, -6), 'jaw': (30, 0, 0), 'arm_l': (-20, -20, -30),
                                        'forearm_l': (-10, 0, 0), 'hand_l': (0, 0, 0)}, z))
    sever.key(0.7, {'root': {'rot': [-30, 0, 0], 'pos': [0, -4, 2]}, 'hips': (10, 0, 0), 'leg_r': (-40, 0, 6), 'shin_r': (80, 0, 0),
                    'foot_r': (0, 0, 0), 'leg_l': (-30, 0, -6), 'shin_l': (70, 0, 0), 'foot_l': (0, 0, 0), 'torso': (0, 0, 0),
                    'chest': (-20, 0, 0), 'neck': (0, 0, 0), 'head': (-20, 0, 0), 'arm_r': (-40, 0, 60), 'forearm_r': (-20, 0, 0),
                    'hand_r': (0, 0, 0), 'arm_l': (-30, 0, -60), 'forearm_l': (-20, 0, 0), 'jaw': (30, 0, 0)})
    flat = {'root': {'rot': [-90, 0, 0], 'pos': [0, 1.6, 15.5]}, 'hips': (0, 0, 0), 'leg_r': (-20, 0, 8), 'shin_r': (30, 0, 0),
            'foot_r': (10, 0, 0), 'leg_l': (-6, 0, -8), 'shin_l': (20, 0, 0), 'foot_l': (14, 0, 0), 'torso': (0, 0, 0),
            'chest': (0, 0, 0), 'neck': (6, 0, 0), 'head': (20, -30, 0), 'arm_r': (-8, 0, 70), 'forearm_r': (-20, 0, 0),
            'hand_r': (0, 0, 0), 'arm_l': (-10, 0, -80), 'forearm_l': (-30, 0, 0), 'jaw': (32, 0, 0)}
    sever.key(1.0, flat)
    sever.key(1.12, merge(flat, {'root': {'rot': [-86, 0, 0], 'pos': [0, 2.0, 15.5]}}))
    sever.key(1.3, flat)
    sever.key(3.5, flat)
    sever.keep.update({'root'})
    clips.append(sever)
    # mounted: braced on the chariot deck, off hand on the grab bar, staff fired one-handed (troopers)
    bar = np.array([2.4, 16.0, -4.8])
    bar_frame = np.column_stack([(0, 0.87, -0.5), (0, 0.5, 0.87), (1.0, 0, 0)])
    mounted = Clip('mounted', 2.0, loop=True)
    mshoot = Clip('mounted_shoot', 0.36)

    def mount_pose(w, r=0.0):
        p = {'hips': {'rot': [8, 0, 0], 'pos': [0, -1.6, 0]}, 'leg_r': (-26, 0, 6), 'shin_r': (44, 0, 0), 'foot_r': (-18, 0, 0),
             'leg_l': (-10, -10, -12), 'shin_l': (36, 0, 0), 'foot_l': (-26, 0, 0),
             'torso': (6, 6, 0), 'chest': (4 + w - 3 * r, 8, 0), 'neck': (-4, -6, 0), 'head': (-4 - 2 * r, -8 + 3 * w, 0),
             'jaw': (3 + 8 * r, 0, 0), 'pauldron_r': (-12, 0, 0), 'pauldron_l': (-4, 0, 0)}
        k = math.radians(2 * w + 10 * r)
        D = (0.0, math.sin(k), -math.cos(k))
        G = (-3.3, 20.2 + 0.4 * r, -7.4 + 1.0 * r)
        return hold(p, D, (0, 1, 0), G=G, two_hands=False, left_at=bar, left_frame=bar_frame,
                    pole_r=(-1.0, -1.0, 0.6), pole_l=(1.0, -0.4, 0.6), curl_l=1.0)

    for i in range(5):
        t = i * 0.5
        mounted.key(t, mount_pose(math.sin(2 * math.pi * t / 2.0)))
    clips.append(mounted)
    for t, r in ((0.0, 0.0), (0.04, 1.0), (0.12, 0.55), (0.24, 0.15), (0.36, 0.0)):
        mshoot.key(t, upper(mount_pose(0.0, r)))
    clips.append(mshoot)
    # mounted_pilot: both hands on the control posts, crouched over them, glancing around
    pilot = Clip('mounted_pilot', 2.0, loop=True)
    post_frame = L.frame((0, 0, -1), (0, 1, 0))
    for i in range(5):
        t = i * 0.5
        w = math.sin(2 * math.pi * t / 2.0)
        p = {'hips': {'rot': [16, 0, 0], 'pos': [0, -2.2, 0]}, 'torso': (12, 0, 0), 'chest': (12 + w, 0, 0), 'neck': (-14, 0, 0),
             'head': (-12, 10 * w, 0), 'jaw': (4, 0, 0), 'pauldron_r': (-8, 0, 0), 'pauldron_l': (-8, 0, 0),
             'leg_r': (-40, 0, 6), 'shin_r': (60, 0, 0), 'foot_r': (-20, 0, 0), 'leg_l': (-22, 0, -8), 'shin_l': (54, 0, 0),
             'foot_l': (-30, 0, 0), 'rifle': {'rot': [0, 0, 0], 'scale': [0.0, 0.0, 0.0]}}
        q = L.as_pose(p)
        q.update(L.as_pose(fist('r')))
        q.update(L.as_pose(fist('l')))
        q, er = L.place_hand(geo(), q, 'r', (-4.5, 12.1, 0.0), (-3.0, 17.6, -7.6), post_frame, (-1.0, -1.0, 0.5))
        q, el = L.place_hand(geo(), q, 'l', PALM_L, (3.0, 17.6, -7.6), post_frame, (1.0, -1.0, 0.5))
        if er > 0.35 or el > 0.35:
            print('  pilot misses: %.2f / %.2f' % (er, el))
        pilot.key(t, clip_pose(q))
    clips.append(pilot)
    return clips


def merge(*ps):
    out = {}
    for p in ps:
        out.update(p)
    return out


def main():
    m = build()
    m.write(GEO, TEX, GLOW)
    n = sum(len(b.cubes) for b in m.bones)
    print('soldier: %d bones, %d cubes, atlas %dx%d' % (len(m.bones), n, m.width, m.height))
    global GEO_JSON
    GEO_JSON = None
    clips = build_clips()
    size = write_anims(ANIM, [c for c in clips if c.name not in STATES])
    size2 = write_anims(ANIM_STATES, [c for c in clips if c.name in STATES])
    print('soldier clips: %s (%d + %d bytes)' % (', '.join(c.name for c in clips), size, size2))
    if '--review' in sys.argv:
        # --review name:frac,name:frac [sheet_name]: one row of four views per entry, stacked
        i = sys.argv.index('--review')
        entries = [e.split(':') for e in sys.argv[i + 1].split(',')]
        sheet_name = sys.argv[i + 2] if len(sys.argv) > i + 2 else 'review'
        out = os.path.join(L.ROOT, 'build', 'chitauri_preview')
        tex = os.path.join(L.ROOT, 'build', 'jinwoo_preview', TEX)
        glow = os.path.join(L.ROOT, 'build', 'jinwoo_preview', GLOW)
        by = {c.name: c for c in clips}
        rows = []
        for e in entries:
            c = by[e[0]]
            frac = float(e[1]) if len(e) > 1 else 0.3
            pose = L.sample_clip(c, c.length * frac)
            rows.append(L.preview(os.path.join(out, 'review_%s.png' % c.name), [(geo(), pose, tex, glow)],
                                  views=('front', 'q_front', 'side', 'q_back'), scale=7))
        L.sheet(os.path.join(out, sheet_name + '.png'), rows, cols=1)
    if '--preview' in sys.argv:
        out = os.path.join(L.ROOT, 'build', 'chitauri_preview')
        tex = os.path.join(L.ROOT, 'build', 'jinwoo_preview', TEX)
        glow = os.path.join(L.ROOT, 'build', 'jinwoo_preview', GLOW)
        ims = []
        for c in clips:
            t = min(c.length * 0.3, c.length)
            pose = L.sample_clip(c, t)
            ims.append(L.preview(os.path.join(out, 'soldier_%s.png' % c.name), [(geo(), pose, tex, glow)], scale=5))
        L.preview(os.path.join(out, 'soldier_rest.png'), [(geo(), {}, tex, glow)], views=('front', 'side', 'back', 'top'), scale=8)
        L.sheet(os.path.join(out, 'soldier_clips.png'), ims, cols=4)


if __name__ == '__main__':
    main()
