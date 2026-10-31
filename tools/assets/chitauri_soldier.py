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

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import chitauri_lib as L  # noqa: E402
from chitauri_lib import Model, Clip, write_anims  # noqa: E402

GEO = 'geo/chitauri/soldier.geo.json'
ANIM = 'animations/chitauri/soldier.animation.json'
TEX = 'textures/entity/chitauri/soldier.png'
GLOW = 'textures/entity/chitauri/soldier_glow.png'

M = L.mats()
GRIP = (-4.6, 12.7, -0.35)   # right palm centre (rest), the rifle hangs from it along -Y


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
    box('hips', (-3.25, 15.5, -2.0), (3.25, 16.7, 2.05), 'gold_dark')            # belt
    box('hips', (-1.3, 13.0, -2.25), (1.3, 15.6, -1.6), 'gold')                   # groin plate
    box('hips', (-0.9, 13.4, 1.6), (0.9, 15.6, 2.3), 'alloy')                     # tail guard
    box('hips', (-3.5, 13.6, -1.2), (-2.9, 16.2, 1.4), 'gold', rotation=[0, 0, 8], pivot=[-3.2, 16.2, 0])   # hip plates
    box('hips', (2.9, 13.6, -1.2), (3.5, 16.2, 1.4), 'gold', rotation=[0, 0, -8], pivot=[3.2, 16.2, 0])
    # ---- right leg (mirrored) ------------------------------------------------------------------
    m.bone('leg_r', 'hips', [-1.75, 15.0, 0])
    box('leg_r', (-3.05, 8.6, -1.4), (-0.45, 15.2, 1.4), 'skin')
    box('leg_r', (-3.35, 10.0, -1.15), (-2.85, 14.6, 1.15), 'gold')               # outer thigh plate
    box('leg_r', (-2.85, 10.6, -1.75), (-0.65, 14.2, -1.25), 'gold_plain')        # front thigh plate
    box('leg_r', (-2.6, 9.0, 1.2), (-0.9, 12.6, 1.7), 'hose')                     # rear tendon hose
    m.bone('shin_r', 'leg_r', [-1.75, 8.7, -0.1])
    box('shin_r', (-2.85, 2.0, -1.05), (-0.65, 8.8, 1.05), 'skin')
    box('shin_r', (-3.0, 7.4, -1.6), (-0.5, 9.4, -0.8), 'gold')                   # knee cap
    box('shin_r', (-2.75, 2.8, -1.55), (-0.75, 7.4, -0.95), 'gold_plain')         # shin guard
    box('shin_r', (-2.6, 4.2, 0.9), (-0.9, 7.8, 1.6), 'skin_dark')                # calf
    box('shin_r', (-3.05, 3.2, -0.6), (-2.75, 6.6, 0.8), 'alloy')                 # outer strut
    m.bone('foot_r', 'shin_r', [-1.75, 2.0, 0.0])
    box('foot_r', (-2.85, 0.0, -2.6), (-0.65, 2.2, 1.6), 'alloy')
    box('foot_r', (-2.75, 0.0, -3.9), (-1.95, 0.9, -2.6), 'alloy_light')          # toe claws
    box('foot_r', (-1.55, 0.0, -3.9), (-0.75, 0.9, -2.6), 'alloy_light')
    box('foot_r', (-2.2, 0.2, 1.6), (-1.3, 1.6, 2.3), 'gold_dark')                # heel spur
    box('foot_r', (-2.95, 1.6, -2.2), (-0.55, 2.4, 0.6), 'gold_dark')             # ankle cuff
    # ---- abdomen / chest -----------------------------------------------------------------------
    m.bone('torso', 'hips', [0, 17.0, 0])
    box('torso', (-2.5, 16.8, -1.5), (2.5, 20.5, 1.5), 'skin')
    for i, y in enumerate((17.2, 18.3, 19.4)):                                     # ab ridges
        box('torso', (-1.9, y, -1.75), (1.9, y + 0.7, -1.4), 'skin_dark')
    box('torso', (-0.8, 16.8, 1.3), (0.8, 20.5, 2.1), 'alloy')                     # spine
    box('torso', (-2.9, 17.6, -1.0), (-2.4, 20.0, 1.0), 'hose')                    # side hoses
    box('torso', (2.4, 17.6, -1.0), (2.9, 20.0, 1.0), 'hose')
    m.bone('chest', 'torso', [0, 20.2, 0])
    box('chest', (-3.6, 20.2, -2.0), (3.6, 25.8, 2.1), 'skin')
    box('chest', (-3.25, 21.0, -2.5), (3.25, 25.2, -1.9), 'gold')                  # breast plate
    box('chest', (-0.6, 21.2, -2.75), (0.6, 24.8, -2.4), 'gold_dark')              # sternum keel
    box('chest', (-2.7, 25.2, -1.9), (2.7, 26.2, 1.9), 'gold_plain')               # collar
    box('chest', (-3.1, 20.8, 1.95), (3.1, 25.4, 2.7), 'gold')                     # back plate
    box('chest', (-1.5, 21.4, 2.6), (1.5, 25.0, 3.9), 'alloy_lit')                 # spine pack
    box('chest', (-0.5, 22.0, 3.85), (0.5, 24.4, 4.15), 'energy_soft')             # pack light
    box('chest', (-2.2, 21.0, -2.75), (-1.6, 24.6, -2.35), 'hose')                 # chest hoses up to the jaw
    box('chest', (1.6, 21.0, -2.75), (2.2, 24.6, -2.35), 'hose')
    box('chest', (-3.9, 20.6, -1.4), (-3.4, 24.0, 1.4), 'skin_dark')               # ribs flank
    box('chest', (3.4, 20.6, -1.4), (3.9, 24.0, 1.4), 'skin_dark')
    m.bone('pauldron_r', 'chest', [-4.0, 25.6, 0])
    box('pauldron_r', (-6.0, 23.8, -1.9), (-3.2, 26.0, 1.9), 'gold', rotation=[0, 0, 14], pivot=[-4.0, 25.6, 0])
    box('pauldron_r', (-6.3, 23.0, -1.6), (-5.6, 24.6, 1.6), 'gold_dark', rotation=[0, 0, 14], pivot=[-4.0, 25.6, 0])
    box('pauldron_r', (-5.2, 25.6, -1.2), (-3.8, 26.6, 1.2), 'gold_plain', rotation=[0, 0, 14], pivot=[-4.0, 25.6, 0])
    # ---- neck / head ---------------------------------------------------------------------------
    m.bone('neck', 'chest', [0, 25.8, -0.2])
    box('neck', (-1.1, 25.6, -1.2), (1.1, 27.6, 0.9), 'skin')
    box('neck', (-1.6, 25.8, -1.5), (-1.1, 27.4, -1.0), 'hose')
    box('neck', (1.1, 25.8, -1.5), (1.6, 27.4, -1.0), 'hose')
    box('neck', (-0.5, 25.8, 0.8), (0.5, 27.6, 1.5), 'alloy')
    m.bone('head', 'neck', [0, 27.3, -0.3])
    box('head', (-1.8, 27.4, -2.3), (1.8, 31.0, 1.2), 'skin')                       # skull
    box('head', (-1.4, 28.0, 1.1), (1.4, 30.6, 2.7), 'skin_dark')                    # elongated back skull
    box('head', (-1.95, 29.7, -2.6), (1.95, 30.5, -1.4), 'gold')                     # brow band
    box('head', (-0.65, 30.4, -1.9), (0.65, 31.8, 2.4), 'gold_plain', rotation=[-12, 0, 0], pivot=[0, 30.8, 0])  # crest
    box('head', (-0.4, 31.2, 0.8), (0.4, 32.2, 2.9), 'gold_dark', rotation=[-24, 0, 0], pivot=[0, 31.0, 1.0])    # crest fin
    box('head', (-2.05, 27.8, -2.1), (-1.55, 29.6, 0.2), 'gold')                     # cheek plates
    box('head', (1.55, 27.8, -2.1), (2.05, 29.6, 0.2), 'gold')
    box('head', (-0.35, 28.2, -2.6), (0.35, 29.7, -2.2), 'skin_dark')                # nose ridge
    box('head', (-1.55, 28.9, -2.42), (-0.5, 29.45, -2.25), 'eye')                    # eyes
    box('head', (0.5, 28.9, -2.42), (1.55, 29.45, -2.25), 'eye')
    box('head', (-2.1, 28.4, 0.2), (-1.7, 29.4, 1.0), 'hose')                        # ear tubes
    box('head', (1.7, 28.4, 0.2), (2.1, 29.4, 1.0), 'hose')
    m.bone('jaw', 'head', [0, 27.8, -0.9])
    box('jaw', (-1.55, 26.5, -2.55), (1.55, 27.9, -0.4), 'gold')                     # jaw plate
    box('jaw', (-1.2, 27.75, -2.62), (1.2, 28.2, -2.3), 'bone')                      # teeth
    box('jaw', (-0.5, 26.2, -2.3), (0.5, 26.6, -1.2), 'alloy')                       # chin spike
    # ---- right arm (mirrored) ------------------------------------------------------------------
    m.bone('arm_r', 'chest', [-4.4, 24.7, 0])
    box('arm_r', (-5.6, 19.2, -1.1), (-3.4, 24.9, 1.1), 'skin')
    box('arm_r', (-5.95, 20.4, -0.95), (-5.45, 23.8, 0.95), 'gold')                  # biceps plate
    box('arm_r', (-5.2, 19.6, 1.0), (-3.8, 22.8, 1.5), 'hose')
    m.bone('forearm_r', 'arm_r', [-4.5, 19.3, 0])
    box('forearm_r', (-5.45, 14.0, -1.0), (-3.55, 19.4, 1.0), 'skin')
    box('forearm_r', (-5.75, 14.5, -1.3), (-3.25, 18.0, 1.3), 'gold')                # bracer
    box('forearm_r', (-5.0, 18.4, 0.9), (-4.0, 19.8, 1.8), 'gold_dark')              # elbow spur
    box('forearm_r', (-5.9, 15.2, -0.5), (-5.6, 17.3, 0.5), 'energy_soft')           # bracer light
    m.bone('hand_r', 'forearm_r', [-4.5, 14.0, 0])
    box('hand_r', (-5.25, 12.1, -0.85), (-3.75, 14.1, 0.85), 'skin')
    box('hand_r', (-5.4, 12.9, -0.95), (-5.15, 14.0, 0.95), 'gold_dark')             # back of hand plate
    for k, z in enumerate((-0.62, 0.0, 0.62)):                                        # three fingers, two joints
        f = 'finger_r%d' % (k + 1)
        m.bone(f, 'hand_r', [-4.5, 12.15, z])
        box(f, (-4.77, 10.9, z - 0.27), (-4.23, 12.2, z + 0.27), 'skin')
        m.bone(f + 'b', f, [-4.5, 10.95, z])
        box(f + 'b', (-4.74, 9.8, z - 0.24), (-4.26, 11.0, z + 0.24), 'skin_dark')
        box(f + 'b', (-4.7, 9.55, z - 0.2), (-4.3, 9.85, z + 0.2), 'bone')           # claw
    m.bone('thumb_r', 'hand_r', [-4.1, 13.4, -0.8])
    box('thumb_r', (-4.32, 12.3, -1.32), (-3.82, 13.5, -0.82), 'skin', rotation=[0, 0, -18], pivot=[-4.1, 13.4, -0.8])
    m.bone('thumb_rb', 'thumb_r', [-3.75, 12.35, -1.07])
    box('thumb_rb', (-3.98, 11.4, -1.3), (-3.52, 12.4, -0.84), 'skin_dark')
    m.mirror_x('_r', '_l')
    # ---- the staff rifle (in the right hand, along -Y at rest: forward once the arm aims) --------
    gx, gy, gz = GRIP
    m.bone('rifle', 'hand_r', [gx, gy, gz])
    box('rifle', (gx - 0.75, gy - 3.2, gz - 3.0), (gx + 0.75, gy + 3.2, gz - 1.2), 'gold')            # receiver
    box('rifle', (gx - 0.5, gy - 0.7, gz - 1.25), (gx + 0.5, gy + 0.7, gz + 0.9), 'alloy')            # pistol grip
    box('rifle', (gx - 0.55, gy + 1.0, gz - 1.3), (gx + 0.55, gy + 1.5, gz + 0.3), 'alloy_light')     # trigger guard
    box('rifle', (gx - 0.55, gy + 3.2, gz - 2.6), (gx + 0.55, gy + 7.4, gz - 1.0), 'alloy')            # stock
    box('rifle', (gx - 0.7, gy + 7.2, gz - 2.9), (gx + 0.7, gy + 8.0, gz - 0.6), 'gold_dark')         # butt plate
    box('rifle', (gx - 0.45, gy - 13.2, gz - 2.45), (gx + 0.45, gy - 3.1, gz - 1.6), 'alloy')          # staff
    box('rifle', (gx - 0.62, gy - 6.6, gz - 2.7), (gx + 0.62, gy - 5.0, gz - 1.3), 'gold_dark')        # fore grip collar
    box('rifle', (gx - 0.66, gy - 9.8, gz - 3.0), (gx + 0.66, gy - 7.2, gz - 1.05), 'energy')          # energy cell
    box('rifle', (gx - 0.8, gy - 10.2, gz - 3.15), (gx + 0.8, gy - 9.7, gz - 0.9), 'gold')             # cell rings
    box('rifle', (gx - 0.8, gy - 7.3, gz - 3.15), (gx + 0.8, gy - 6.8, gz - 0.9), 'gold')
    box('rifle', (gx - 0.3, gy - 13.9, gz - 2.3), (gx + 0.3, gy - 13.1, gz - 1.75), 'energy')          # muzzle emitter
    box('rifle', (gx - 0.35, gy - 16.0, gz - 2.2), (gx + 0.35, gy - 12.4, gz - 1.7), 'gold',           # blade prong (upper)
        rotation=[14, 0, 0], pivot=[gx, gy - 12.4, gz - 1.9])
    box('rifle', (gx - 0.3, gy - 15.0, gz - 1.4), (gx + 0.3, gy - 12.6, gz - 0.95), 'gold_dark',       # lower prong
        rotation=[-18, 0, 0], pivot=[gx, gy - 12.6, gz - 1.2])
    box('rifle', (gx - 0.5, gy - 4.6, gz - 3.4), (gx + 0.5, gy - 1.2, gz - 2.95), 'gold_plain')        # top rail
    m.bone('muzzle', 'rifle', [gx, gy - 15.2, gz - 1.95])
    m.bone('grip_front', 'rifle', [gx, gy - 5.8, gz - 0.55])
    return m


# ---- poses ---------------------------------------------------------------------------------------------
def mirror(p):
    """Right-side pose entries mirrored onto the left side (rx kept, ry/rz negated)."""
    out = {}
    for k, v in p.items():
        if '_r' in k:
            nk = k.replace('_r', '_l')
            rot = v if not isinstance(v, dict) else v.get('rot')
            if rot is not None:
                out[nk] = (rot[0], -rot[1], -rot[2])
    return out


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


def as_pose(p):
    return {k: (v if isinstance(v, dict) else {'rot': list(v)}) for k, v in p.items()}


def off_hand(p, side='l'):
    """Solves the off arm so its palm meets the rifle fore grip; returns the merged pose."""
    g = geo()
    base = as_pose(p)
    target = L.point(g, base, 'grip_front', [GRIP[0], GRIP[1] - 5.8, GRIP[2] - 0.55])
    palm = [4.5, 12.9, 0.0] if side == 'l' else [-4.5, 12.9, 0.0]
    seed = json.loads(json.dumps(base))
    seed.setdefault('arm_l', {'rot': [-60, 10, -20]})
    seed.setdefault('forearm_l', {'rot': [-40, 0, 0]})
    solved, err = L.reach(g, seed, ('arm_l', 'forearm_l'), 'hand_l', palm, target)
    out = dict(p)
    out['arm_l'] = tuple(solved['arm_l']['rot'])
    out['forearm_l'] = tuple(solved['forearm_l']['rot'])
    out['hand_l'] = p.get('hand_l', (0, 0, 70))
    out.update(fist('l', 0.85))
    if err > 0.9:
        print('  off hand misses the fore grip by %.2f px' % err)
    return out


def low_ready(breath=0.0, look=0.0, bob=0.0):
    """Rifle across the body, muzzle forward-down, both hands on it."""
    p = {
        'chest': (4 + breath * 1.5, 0, 0),
        'torso': (3, 0, 0),
        'neck': (-4, look * 0.4, 0),
        'head': (-3 + bob, look, 0),
        'arm_r': (-24 + bob, 8, 10),
        'forearm_r': (-50, 0, 0),
        'hand_r': (16, 0, -6),
        'pauldron_r': (-6, 0, 0),
        'pauldron_l': (-8, 0, 0),
    }
    p.update(fist('r'))
    return p


def shouldered(sway=0.0, recoil=0.0):
    """Rifle at the shoulder, aiming straight ahead."""
    p = {
        'torso': (2, 4, 0),
        'chest': (-2 + recoil * 5, 10, 0),
        'neck': (2, -6, 0),
        'head': (4 - recoil * 3, -6, 0),
        'arm_r': (-82 - recoil * 6, 30 + sway, 8),
        'forearm_r': (-28 + recoil * 10, 0, 0),
        'hand_r': (24 - recoil * 6, 0, -4),
        'pauldron_r': (-14, 0, 0),
        'pauldron_l': (-10, 0, 0),
    }
    p.update(fist('r'))
    return p


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
        'torso': ((6 if big else 2), -6 * s, -2 * c),
    }
    return p


def build_clips():
    clips = []
    # idle: low ready, breathing, scanning the street, weight on the right leg
    idle = Clip('idle', 3.2, loop=True)
    for i in range(9):
        t = i * 0.4
        f = math.sin(2 * math.pi * t / 3.2)
        look = 18 * math.sin(2 * math.pi * t / 3.2 * 0.5 + 0.4)
        p = low_ready(breath=f, look=look)
        p.update({'hips': {'rot': [2, 3, 2 + f], 'pos': [0.3, -0.25 - 0.15 * f, 0]},
                  'leg_r': (-4, 0, 2), 'shin_r': (8, 0, 0), 'foot_r': (-4, 0, 0),
                  'leg_l': (6, -6, -5), 'shin_l': (14, 0, 0), 'foot_l': (-8, 0, 0),
                  'jaw': (4 + 3 * max(0, f), 0, 0), 'tail_guard': (0, 0, 0)})
        p = off_hand(p)
        idle.key(t, {k: v for k, v in p.items() if k != 'tail_guard'})
    clips.append(idle)
    # walk: rifle at low ready, steady stride
    walk = Clip('walk', 1.0, loop=True)
    for i in range(9):
        t = i / 8.0
        p = low_ready(bob=1.5 * math.cos(4 * math.pi * t))
        p.update(stride(t))
        p = off_hand(p)
        walk.key(t * 1.0, p)
    clips.append(walk)
    # run: leaning in, rifle held across the chest, long stride
    run = Clip('run', 0.62, loop=True)
    for i in range(9):
        t = i / 8.0
        p = low_ready(bob=3 * math.cos(4 * math.pi * t))
        p['chest'] = (12, 6 * math.sin(2 * math.pi * t), 0)
        p['arm_r'] = (-36 - 8 * math.sin(2 * math.pi * t), 12, 16)
        p['forearm_r'] = (-62, 0, 0)
        p['head'] = (-10, 0, 0)
        p.update(stride(t, big=True))
        p = off_hand(p)
        run.key(t * 0.62, p)
    clips.append(run)
    # aim: shouldered, breathing sway (upper body only)
    aim = Clip('aim', 2.0, loop=True)
    for i in range(5):
        t = i * 0.5
        p = shouldered(sway=1.2 * math.sin(2 * math.pi * t / 2.0))
        p = off_hand(p)
        p['jaw'] = (2, 0, 0)
        aim.key(t, p)
    clips.append(aim)
    # shoot: recoil kick and settle (played over aim)
    shoot = Clip('shoot', 0.36)
    for t, r in ((0.0, 0.0), (0.04, 1.0), (0.12, 0.55), (0.24, 0.15), (0.36, 0.0)):
        p = off_hand(shouldered(recoil=r))
        p['jaw'] = (2 + 10 * r, 0, 0)
        shoot.key(t, p)
    clips.append(shoot)
    # melee: wind up, blade slash forward-down, recover
    melee = Clip('melee', 0.72)
    keys = [
        (0.0, low_ready()),
        (0.18, {'torso': (-4, 28, 0), 'chest': (-10, 22, 0), 'arm_r': (-130, 20, 30), 'forearm_r': (-40, 0, 0), 'hand_r': (-10, 0, 0),
                'head': (-6, -14, 0), 'jaw': (14, 0, 0)}),
        (0.32, {'torso': (16, -26, 0), 'chest': (20, -22, 0), 'arm_r': (-40, -18, -10), 'forearm_r': (-12, 0, 0), 'hand_r': (30, 0, 0),
                'head': (10, 12, 0), 'jaw': (22, 0, 0)}),
        (0.5, {'torso': (10, -14, 0), 'chest': (10, -10, 0), 'arm_r': (-34, -6, 4), 'forearm_r': (-30, 0, 0), 'hand_r': (24, 0, 0),
               'head': (4, 6, 0), 'jaw': (8, 0, 0)}),
        (0.72, low_ready()),
    ]
    for t, p in keys:
        q = dict(low_ready())
        q.update(p)
        q.update(fist('r'))
        q.setdefault('arm_l', (-30, -10, -24))
        q.setdefault('forearm_l', (-50, 0, 0))
        q.update(fist('l', 0.7))
        melee.key(t, q)
    clips.append(melee)
    # hurt: flinch backwards, head snaps (additive-ish overlay on the upper body)
    hurt = Clip('hurt', 0.42)
    hurt.key(0.0, {'chest': (0, 0, 0), 'head': (0, 0, 0), 'neck': (0, 0, 0), 'torso': (0, 0, 0)})
    hurt.key(0.06, {'chest': (-16, 8, 6), 'head': (-22, 10, 8), 'neck': (-8, 0, 0), 'torso': (-8, 0, 0)})
    hurt.key(0.2, {'chest': (-6, 4, 2), 'head': (-8, 4, 2), 'neck': (-2, 0, 0), 'torso': (-3, 0, 0)})
    hurt.key(0.42, {'chest': (0, 0, 0), 'head': (0, 0, 0), 'neck': (0, 0, 0), 'torso': (0, 0, 0)})
    clips.append(hurt)
    # fall: thrown or dropped, limbs out, rifle still in hand
    fall = Clip('fall', 0.8, loop=True)
    for i in range(5):
        t = i * 0.2
        w = math.sin(2 * math.pi * t / 0.8)
        p = {'hips': (-8 + 4 * w, 0, 0), 'chest': (-14, 0, 4 * w), 'head': (-16, 0, 0), 'jaw': (18, 0, 0),
             'arm_r': (-110 + 20 * w, 10, 50), 'forearm_r': (-30, 0, 0),
             'arm_l': (-100 - 20 * w, -10, -60), 'forearm_l': (-24, 0, 0),
             'leg_r': (-30 + 14 * w, 0, 10), 'shin_r': (60, 0, 0), 'leg_l': (-10 - 14 * w, 0, -10), 'shin_l': (40, 0, 0)}
        p.update(fist('r'))
        p.update(relaxed('l'))
        fall.key(t, p)
    clips.append(fall)
    # land: crouch absorbs the drop, rises to low ready
    land = Clip('land', 0.6)
    for t, d in ((0.0, 1.0), (0.12, 1.0), (0.35, 0.45), (0.6, 0.0)):
        p = low_ready()
        p.update({'hips': {'rot': [26 * d, 0, 0], 'pos': [0, -4.2 * d, 0]}, 'torso': (12 * d, 0, 0), 'chest': (10 * d, 0, 0),
                  'leg_r': (-58 * d, 0, 6), 'shin_r': (92 * d, 0, 0), 'foot_r': (-34 * d, 0, 0),
                  'leg_l': (-44 * d, 0, -8), 'shin_l': (96 * d, 0, 0), 'foot_l': (-50 * d, 0, 0),
                  'head': (-14 * d, 0, 0)})
        land.key(t, off_hand(p))
    clips.append(land)
    # death: hit, knees buckle, collapses forward onto the street and lies still
    death = Clip('death', 3.5)
    death.key(0.0, merge(low_ready(), {'root': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}))
    death.key(0.12, merge(low_ready(), {'chest': (-18, 10, 8), 'head': (-24, 14, 0), 'jaw': (20, 0, 0), 'arm_r': (-40, 20, 40),
                                        'root': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}))
    death.key(0.45, {'root': {'rot': [12, 0, 0], 'pos': [0, -3.5, 0]}, 'hips': (20, 0, 0), 'leg_r': (-70, 0, 8), 'shin_r': (110, 0, 0),
                     'foot_r': (-30, 0, 0), 'leg_l': (-50, 0, -6), 'shin_l': (100, 0, 0), 'foot_l': (-40, 0, 0), 'chest': (20, 0, 0),
                     'head': (24, 0, 10), 'arm_r': (-20, 0, 30), 'forearm_r': (-30, 0, 0), 'arm_l': (-10, 0, -30), 'forearm_l': (-20, 0, 0),
                     'jaw': (24, 0, 0)})
    death.key(0.9, {'root': {'rot': [88, 0, 0], 'pos': [0, 1.5, -15]}, 'hips': (-6, 0, 0), 'leg_r': (-8, 0, 6), 'shin_r': (22, 0, 0),
                    'leg_l': (6, 0, -4), 'shin_l': (30, 0, 0), 'chest': (-6, 0, 0), 'head': (-30, 30, 0),
                    'arm_r': (-160, 10, 40), 'forearm_r': (-10, 0, 0), 'arm_l': (-20, 0, -50), 'forearm_l': (-40, 0, 0), 'jaw': (28, 0, 0)})
    death.key(1.05, {'root': {'rot': [84, 0, 0], 'pos': [0, 2.2, -15]}, 'head': (-34, 32, 0), 'arm_r': (-150, 10, 44)})
    death.key(1.25, {'root': {'rot': [90, 0, 0], 'pos': [0, 1.6, -15.5]}, 'head': (-30, 34, 0), 'arm_r': (-158, 10, 42)})
    death.key(3.5, {'root': {'rot': [90, 0, 0], 'pos': [0, 1.6, -15.5]}, 'hips': (-6, 0, 0), 'leg_r': (-8, 0, 6), 'shin_r': (22, 0, 0),
                    'leg_l': (6, 0, -4), 'shin_l': (30, 0, 0), 'chest': (-6, 0, 0), 'head': (-30, 34, 0),
                    'arm_r': (-158, 10, 42), 'forearm_r': (-10, 0, 0), 'arm_l': (-20, 0, -50), 'forearm_l': (-40, 0, 0), 'jaw': (28, 0, 0)})
    death.keep.update({'root'})
    clips.append(death)
    # sever: cut off from the hive: a jolt, convulsion, the body drops backwards
    sever = Clip('sever', 3.5)
    sever.key(0.0, merge(low_ready(), {'root': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}))
    sever.key(0.08, merge(low_ready(), {'chest': (-24, 0, 0), 'head': (-38, 0, 0), 'jaw': (34, 0, 0), 'arm_r': (-60, 30, 60),
                                        'arm_l': (-50, -30, -60), 'root': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}))
    sever.key(0.2, {'chest': (-8, 6, 10), 'head': (-14, -20, 6), 'jaw': (26, 0, 0), 'arm_r': (-30, 10, 30), 'arm_l': (-40, -10, -40),
                    'root': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}})
    sever.key(0.32, {'chest': (-22, -6, -10), 'head': (-30, 20, -6), 'jaw': (30, 0, 0), 'arm_r': (-50, 20, 50), 'arm_l': (-20, -20, -30),
                     'root': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}})
    sever.key(0.7, {'root': {'rot': [-30, 0, 0], 'pos': [0, -4, 2]}, 'hips': (10, 0, 0), 'leg_r': (-40, 0, 6), 'shin_r': (80, 0, 0),
                    'leg_l': (-30, 0, -6), 'shin_l': (70, 0, 0), 'chest': (-20, 0, 0), 'head': (-20, 0, 0), 'arm_r': (-40, 0, 60),
                    'arm_l': (-30, 0, -60), 'jaw': (30, 0, 0)})
    sever.key(1.0, {'root': {'rot': [-90, 0, 0], 'pos': [0, 1.6, 15.5]}, 'hips': (0, 0, 0), 'leg_r': (-20, 0, 8), 'shin_r': (30, 0, 0),
                    'leg_l': (-6, 0, -8), 'shin_l': (20, 0, 0), 'chest': (0, 0, 0), 'head': (20, -30, 0), 'arm_r': (-8, 0, 70),
                    'forearm_r': (-20, 0, 0), 'arm_l': (-10, 0, -80), 'forearm_l': (-30, 0, 0), 'jaw': (32, 0, 0)})
    sever.key(1.12, {'root': {'rot': [-86, 0, 0], 'pos': [0, 2.0, 15.5]}})
    sever.key(1.3, {'root': {'rot': [-90, 0, 0], 'pos': [0, 1.6, 15.5]}})
    sever.key(3.5, {'root': {'rot': [-90, 0, 0], 'pos': [0, 1.6, 15.5]}, 'hips': (0, 0, 0), 'leg_r': (-20, 0, 8), 'shin_r': (30, 0, 0),
                    'leg_l': (-6, 0, -8), 'shin_l': (20, 0, 0), 'chest': (0, 0, 0), 'head': (20, -30, 0), 'arm_r': (-8, 0, 70),
                    'forearm_r': (-20, 0, 0), 'arm_l': (-10, 0, -80), 'forearm_l': (-30, 0, 0), 'jaw': (32, 0, 0)})
    sever.keep.update({'root'})
    clips.append(sever)
    # mounted: braced on the chariot deck, off hand on the side rail, rifle up (troopers, gunner)
    mounted = Clip('mounted', 2.0, loop=True)
    for i in range(5):
        t = i * 0.5
        w = math.sin(2 * math.pi * t / 2.0)
        p = shouldered(sway=w)
        p.update({'hips': {'rot': [8, 0, 0], 'pos': [0, -1.6, 0]}, 'leg_r': (-26, 0, 6), 'shin_r': (44, 0, 0), 'foot_r': (-18, 0, 0),
                  'leg_l': (-10, -10, -12), 'shin_l': (36, 0, 0), 'foot_l': (-26, 0, 0),
                  'arm_l': (-22 + w, -6, -38), 'forearm_l': (-36, 0, 0), 'hand_l': (0, 0, 20)})
        p.update(fist('l'))
        mounted.key(t, p)
    clips.append(mounted)
    # mounted_pilot: both hands on the control bars, crouched over them
    pilot = Clip('mounted_pilot', 2.0, loop=True)
    for i in range(5):
        t = i * 0.5
        w = math.sin(2 * math.pi * t / 2.0)
        p = {'hips': {'rot': [16, 0, 0], 'pos': [0, -2.2, 0]}, 'torso': (12, 0, 0), 'chest': (12 + w, 0, 0), 'neck': (-14, 0, 0),
             'head': (-12, 4 * w, 0), 'jaw': (4, 0, 0),
             'leg_r': (-40, 0, 6), 'shin_r': (60, 0, 0), 'foot_r': (-20, 0, 0), 'leg_l': (-22, 0, -8), 'shin_l': (54, 0, 0), 'foot_l': (-30, 0, 0),
             'arm_r': (-64, 14, 12), 'forearm_r': (-38, 0, 0), 'hand_r': (10, 0, 0),
             'arm_l': (-64, -14, -12), 'forearm_l': (-38, 0, 0), 'hand_l': (10, 0, 0),
             'rifle': {'rot': [0, 0, 0], 'scale': [0.0, 0.0, 0.0]}}
        p.update(fist('r'))
        p.update(fist('l'))
        pilot.key(t, p)
    clips.append(pilot)
    return clips


def merge(a, b):
    out = dict(a)
    out.update(b)
    return out


def main():
    m = build()
    m.write(GEO, TEX, GLOW)
    n = sum(len(b.cubes) for b in m.bones)
    print('soldier: %d bones, %d cubes, atlas %dx%d' % (len(m.bones), n, m.width, m.height))
    global GEO_JSON
    GEO_JSON = None
    clips = build_clips()
    size = write_anims(ANIM, clips)
    print('soldier clips: %s (%d bytes)' % (', '.join(c.name for c in clips), size))
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
