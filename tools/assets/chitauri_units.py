#!/usr/bin/env python3
"""Chitauri elite infantry: Captor (snatcher), Heavy (siege trooper) and Commander. Each one is its own geo,
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

from chitauri_units_geo import *  # noqa: E402,F401,F403  (materials, colours, build_* model builders)
from chitauri_units_geo import M, CAPTOR, HEAVY, COMMANDER, build_captor, build_heavy, build_commander  # noqa: E402

Z = {'root': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}


def hands(curl_r=1.0, curl_l=1.0):
    return merge(fist('r', curl_r), fist('l', curl_l))


class Rig:
    """Per-unit stance and weapon handling on top of chitauri_soldier.hold (geo + weapon offsets swapped in)."""
    cfg = None
    walk_len, run_len = 1.0, 0.62
    swing, knee = 26, 40
    run_swing, run_knee = 38, 70
    crouch = 0.0          # extra knee bend / hip drop of the stance (captor hunts low)
    width = 0.0           # extra leg spread (heavy)
    lean = 0.0            # forward lean of the upper body
    LOW = dict(D=(0.26, -0.42, -0.87), U=(0.0, 1.0, 0.0), G=(-2.3, 18.6, -3.6))
    AIM_BUTT = (-1.8, 24.3, -3.1)
    kick = 8.0

    def __init__(self, geo):
        self.geo = geo

    def bind(self):
        S.GEO_JSON = self.geo
        S.BUTT = self.cfg['butt']
        S.FORE = self.cfg['fore']

    # stance pieces
    def legs_idle(self, f=0.0):
        c, w = self.crouch, self.width
        return {'hips': {'rot': [2 + 6 * c, 3, 2 + f], 'pos': [0.3, -0.25 - 0.15 * f - 1.4 * c, 0]},
                'leg_r': (-4 - 18 * c, 0, 2 + w), 'shin_r': (8 + 34 * c, 0, 0), 'foot_r': (-4 - 16 * c, 0, 0),
                'leg_l': (6 - 14 * c, -6, -5 - w), 'shin_l': (14 + 30 * c, 0, 0), 'foot_l': (-8 - 16 * c, 0, 0)}

    def body_low(self, breath=0.0, look=0.0, bob=0.0):
        e = self.lean
        return {'torso': (3 + 6 * e, 8, 0), 'chest': (4 + breath * 1.5 + 6 * e, 12, 0), 'neck': (-4 - 6 * e, -8 + look * 0.4, 0),
                'head': (-2 + bob - 6 * e, -12 + look * 0.6, 0), 'pauldron_r': (-6, 0, 0), 'pauldron_l': (-8, 0, 0)}

    def body_aim(self, sway=0.0, recoil=0.0):
        e = self.lean
        return {'torso': (4 + 4 * e, 14, 0), 'chest': (-2 + 0.6 * sway - 4 * recoil + 3 * e, 20, 0), 'neck': (4 - 3 * e, -12, 0),
                'head': (10 - 3 * recoil - 4 * e, -21, -8), 'jaw': (2 + 10 * recoil, 0, 0), 'pauldron_r': (-16, 0, 0),
                'pauldron_l': (-10, 0, 0)}

    def stride(self, phase, big=False):
        p = S.stride(phase, big)
        c, w = self.crouch, self.width
        sw = (self.run_swing if big else self.swing) / (38.0 if big else 26.0)
        out = {}
        for k, v in p.items():
            if isinstance(v, dict):
                out[k] = v
                continue
            x, y, z = v
            if k.startswith('leg'):
                x = x * sw - 14 * c
                z += w if k.endswith('_r') else -w
            elif k.startswith('shin'):
                x = x * (self.run_knee if big else self.knee) / (70.0 if big else 40.0) + 24 * c
            elif k.startswith('foot'):
                x -= 10 * c
            out[k] = (x, y, z)
        hp = p['hips']
        out['hips'] = {'rot': [hp['rot'][0] + 6 * c, hp['rot'][1], hp['rot'][2]], 'pos': [0, hp['pos'][1] - 1.2 * c, 0]}
        return out

    def ref_low(self):
        return self.body_low()

    def low_ready(self, base=None, breath=0.0, look=0.0, bob=0.0, over=None):
        p = dict(base or {})
        p.update(self.body_low(breath, look, bob))
        p.update(over or {})
        return S.hold(p, self.LOW['D'], self.LOW['U'], G=self.LOW['G'], space='chest', ref=self.ref_low())

    def shouldered(self, sway=0.0, recoil=0.0, base=None):
        p = dict(base or {})
        p.update(self.body_aim(sway, recoil))
        k = math.radians(1.2 * sway + self.kick * recoil)
        D = (0.05, math.sin(k), -math.cos(k))
        b = self.AIM_BUTT
        butt = (b[0], b[1], b[2] + 0.7 * recoil)
        return S.hold(p, D, (0, 1, 0), butt=butt, pole_r=(-1.0, -0.35, 0.35), pole_l=(0.7, -1.0, 0.0), roll=20)

    def one_hand(self, p, D, U, G, left_at=None, left_frame=None, pole_r=(-1.0, -0.8, 0.4), pole_l=(1.0, -1.0, 0.2), curl_l=1.0):
        """Weapon in the right hand only; the left hand either placed (left_at / left_frame) or left to the pose."""
        if left_at is None:
            return S.hold(p, D, U, G=G, two_hands=False, pole_r=pole_r)
        return S.hold(p, D, U, G=G, two_hands=False, left_at=np.array(left_at, float), left_frame=left_frame,
                      pole_r=pole_r, pole_l=pole_l, curl_l=curl_l)

    # specials hook
    def special_clips(self):
        return []

    def reload_clip(self):
        c = Clip('reload', 1.0)
        c.key(0.0, S.upper(self.low_ready()))
        c.key(1.0, S.upper(self.low_ready()))
        return c


def hand_frame(d, u):
    """World orientation for a hand: fingers along d, back of the hand toward u."""
    return L.frame(L.unit(d), np.array(u, float))


def common_clips(R):
    """Locomotion, combat and state clips every elite has (in the unit's own stance and weapon handling)."""
    clips = []
    idle = Clip('idle', 3.2, loop=True)
    for i in range(9):
        t = i * 0.4
        f = math.sin(2 * math.pi * t / 3.2)
        look = 18 * math.sin(2 * math.pi * t / 3.2 * 0.5 + 0.4) if i < 8 else 18 * math.sin(0.4)
        p = R.low_ready(R.legs_idle(f), breath=f, look=look)
        p['jaw'] = (4 + 3 * max(0, f), 0, 0)
        idle.key(t, p)
    clips.append(idle)
    walk = Clip('walk', R.walk_len, loop=True)
    for i in range(9):
        t = i / 8.0
        p = R.low_ready(R.stride(t), bob=1.5 * math.cos(4 * math.pi * t),
                        over={'torso': (5 + 6 * R.lean, 8 - 6 * math.sin(2 * math.pi * t), -2 * math.cos(2 * math.pi * t))})
        walk.key(t * R.walk_len, p)
    clips.append(walk)
    run = Clip('run', R.run_len, loop=True)
    for i in range(9):
        t = i / 8.0
        sn = math.sin(2 * math.pi * t)
        p = R.stride(t, big=True)
        p.update({'torso': (8 + 8 * R.lean, 6 - 8 * sn, -2 * math.cos(2 * math.pi * t)), 'chest': (12 + 4 * R.lean, 8 + 6 * sn, 0),
                  'neck': (-12 - 4 * R.lean, -6, 0), 'head': (-12 - 4 * R.lean, -8 - 4 * sn, 0), 'pauldron_r': (-8, 0, 0),
                  'pauldron_l': (-8, 0, 0)})
        p = S.hold(p, (0.5, 0.9, -0.2), (0.1, -0.2, -1.0), G=(-2.5, 19.6, -4.0), space='chest',
                   pole_r=(-1.0, -1.0, 0.4), pole_l=(1.0, -0.6, 0.0), roll=-10)
        run.key(t * R.run_len, p)
    clips.append(run)
    # turning on the spot: short shuffle steps, the hips lead and the torso follows
    for name, s in (('turn_l', 1), ('turn_r', -1)):
        turn = Clip(name, 0.6, loop=True)
        for i in range(5):
            t = i / 4.0
            a = 2 * math.pi * t
            lift_r = max(0.0, math.sin(a))
            lift_l = max(0.0, -math.sin(a))
            base = R.legs_idle()
            base['hips'] = {'rot': [2 + 6 * R.crouch, s * 10 * math.sin(a), 0], 'pos': [0, -0.25 - 1.4 * R.crouch - 0.3 * abs(math.sin(a)), 0]}
            base['leg_r'] = (-4 - 18 * R.crouch - 16 * lift_r, s * 6 * lift_r, 2 + R.width)
            base['shin_r'] = (8 + 34 * R.crouch + 30 * lift_r, 0, 0)
            base['leg_l'] = (-4 - 14 * R.crouch - 16 * lift_l, s * 6 * lift_l, -2 - R.width)
            base['shin_l'] = (8 + 30 * R.crouch + 30 * lift_l, 0, 0)
            turn.key(t * 0.6, R.low_ready(base, over={'torso': (3 + 6 * R.lean, 8 + s * 8, 0), 'head': (-2 - 6 * R.lean, -12 + s * 14, 0)}))
        clips.append(turn)
    aim = Clip('aim', 2.0, loop=True)
    for i in range(5):
        t = i * 0.5
        aim.key(t, S.upper(R.shouldered(sway=math.sin(2 * math.pi * t / 2.0))))
    clips.append(aim)
    shoot = Clip('shoot', 0.36 if R.kick < 12 else 0.5)
    ks = ((0.0, 0.0), (0.04, 1.0), (0.12, 0.55), (0.24, 0.15), (0.36, 0.0)) if R.kick < 12 else \
        ((0.0, 0.0), (0.05, 1.0), (0.16, 0.7), (0.32, 0.25), (0.5, 0.0))
    for t, r in ks:
        shoot.key(t, S.upper(R.shouldered(recoil=r)))
    clips.append(shoot)
    clips.append(R.reload_clip())
    clips.append(R.melee_clip())
    hurt = Clip('hurt', 0.42)
    zero = {'chest': (0, 0, 0), 'head': (0, 0, 0), 'neck': (0, 0, 0), 'torso': (0, 0, 0)}
    hurt.key(0.0, zero)
    hurt.key(0.06, {'chest': (-16, 8, 6), 'head': (-22, 10, 8), 'neck': (-8, 0, 0), 'torso': (-8, 0, 0)})
    hurt.key(0.2, {'chest': (-6, 4, 2), 'head': (-8, 4, 2), 'neck': (-2, 0, 0), 'torso': (-3, 0, 0)})
    hurt.key(0.42, zero)
    clips.append(hurt)
    # knockback: thrown back off balance, staggers two steps, recovers to low ready (full body)
    kb = Clip('knockback', 0.9)
    kb.key(0.0, R.low_ready(R.legs_idle()))
    back = {'hips': {'rot': [-14, 0, 0], 'pos': [0, -0.6, 1.2]}, 'leg_r': (-36, 0, 6), 'shin_r': (30, 0, 0), 'foot_r': (10, 0, 0),
            'leg_l': (20, 0, -6), 'shin_l': (20, 0, 0), 'foot_l': (-10, 0, 0)}
    kb.key(0.1, R.low_ready(back, over={'torso': (-12, 0, 0), 'chest': (-16, 4, 4), 'neck': (-6, 0, 0), 'head': (-20, 6, 0), 'jaw': (20, 0, 0)}))
    step = {'hips': {'rot': [-4, 0, 0], 'pos': [0, -1.0, 0.6]}, 'leg_r': (10, 0, 6), 'shin_r': (24, 0, 0), 'foot_r': (-10, 0, 0),
            'leg_l': (-30, 0, -6), 'shin_l': (50, 0, 0), 'foot_l': (-10, 0, 0)}
    kb.key(0.35, R.low_ready(step, over={'torso': (-4, 0, 0), 'chest': (-6, 2, 2), 'head': (-8, 0, 0)}))
    kb.key(0.6, R.low_ready(merge(R.legs_idle(), {'hips': {'rot': [10, 0, 0], 'pos': [0, -1.6, 0]}, 'shin_r': (40, 0, 0), 'shin_l': (44, 0, 0)}),
                            over={'torso': (10, 8, 0)}))
    kb.key(0.9, R.low_ready(R.legs_idle()))
    clips.append(kb)
    fall = Clip('fall', 0.8, loop=True)
    for i in range(5):
        t = i * 0.2
        w = math.sin(2 * math.pi * t / 0.8)
        p = {'hips': (-8 + 4 * w, 0, 0), 'torso': (-6, 0, 0), 'chest': (-14, 0, 4 * w), 'neck': (-6, 0, 0), 'head': (-16, 0, 0),
             'jaw': (18, 0, 0), 'arm_r': (-60 + 16 * w, 10, 72), 'forearm_r': (-34 - 10 * w, 0, 0), 'hand_r': (0, 0, 0),
             'arm_l': (-50 - 16 * w, -10, -78), 'forearm_l': (-28 + 10 * w, 0, 0), 'hand_l': (0, 0, 0),
             'leg_r': (-30 + 14 * w, 0, 10), 'shin_r': (60, 0, 0), 'foot_r': (0, 0, 0),
             'leg_l': (-10 - 14 * w, 0, -10), 'shin_l': (40, 0, 0), 'foot_l': (0, 0, 0), 'pauldron_r': (0, 0, 0), 'pauldron_l': (0, 0, 0)}
        p.update(fist('r'))
        p.update(relaxed('l'))
        p.update(R.fall_extra(w))
        fall.key(t, p)
    clips.append(fall)
    land = Clip('land', 0.6 if R.width < 1 else 0.8)
    for t, d in ((0.0, 1.0), (0.12, 1.0), (0.35, 0.45), (land.length, 0.0)):
        base = {'hips': {'rot': [26 * d, 0, 0], 'pos': [0, -4.2 * d, 0]},
                'leg_r': (-58 * d, 0, 6 + R.width), 'shin_r': (92 * d, 0, 0), 'foot_r': (-34 * d, 0, 0),
                'leg_l': (-44 * d, 0, -8 - R.width), 'shin_l': (96 * d, 0, 0), 'foot_l': (-50 * d, 0, 0)}
        land.key(t, R.low_ready(base, over={'torso': (3 + 12 * d, 8, 0), 'head': (-2 - 14 * d, -12, 0)}))
    clips.append(land)
    # disembark: crouch on the deck, spring off it, tuck, reach for the street (the drop then plays fall / land)
    dis = Clip('disembark', 0.8)
    dis.key(0.0, R.low_ready(R.legs_idle()))
    crouch = {'hips': {'rot': [30, 0, 0], 'pos': [0, -4.0, 0]}, 'leg_r': (-66, 0, 6), 'shin_r': (100, 0, 0), 'foot_r': (-34, 0, 0),
              'leg_l': (-60, 0, -6), 'shin_l': (104, 0, 0), 'foot_l': (-44, 0, 0)}
    dis.key(0.22, R.low_ready(crouch, over={'torso': (18, 6, 0), 'chest': (10, 8, 0), 'head': (-22, -8, 0)}))
    spring = {'hips': {'rot': [-6, 0, 0], 'pos': [0, 0.4, 0]}, 'leg_r': (18, 0, 4), 'shin_r': (6, 0, 0), 'foot_r': (30, 0, 0),
              'leg_l': (10, 0, -4), 'shin_l': (10, 0, 0), 'foot_l': (34, 0, 0)}
    dis.key(0.36, R.low_ready(spring, over={'torso': (-6, 4, 0), 'head': (-4, -6, 0)}))
    tuck = {'hips': {'rot': [10, 0, 0], 'pos': [0, 0, 0]}, 'leg_r': (-50, 0, 8), 'shin_r': (80, 0, 0), 'foot_r': (-20, 0, 0),
            'leg_l': (-40, 0, -8), 'shin_l': (90, 0, 0), 'foot_l': (-24, 0, 0)}
    dis.key(0.6, R.low_ready(tuck, over={'torso': (8, 6, 0), 'head': (-14, -8, 0)}))
    dis.key(0.8, R.low_ready(merge(tuck, {'leg_r': (-30, 0, 8), 'shin_r': (40, 0, 0), 'leg_l': (-20, 0, -8), 'shin_l': (44, 0, 0)})))
    clips.append(dis)
    clips.extend(R.death_clips())
    # mounted: braced on the chariot deck, off hand on the grab bar, weapon one-handed
    bar = np.array([2.4, 16.0, -4.8])
    bar_frame = np.column_stack([(0, 0.87, -0.5), (0, 0.5, 0.87), (1.0, 0, 0)])
    mounted = Clip('mounted', 2.0, loop=True)
    mshoot = Clip('mounted_shoot', 0.36)

    def mount_pose(w, r=0.0):
        p = {'hips': {'rot': [8, 0, 0], 'pos': [0, -1.6, 0]}, 'leg_r': (-26, 0, 6 + R.width), 'shin_r': (44, 0, 0), 'foot_r': (-18, 0, 0),
             'leg_l': (-10, -10, -12 - R.width), 'shin_l': (36, 0, 0), 'foot_l': (-26, 0, 0),
             'torso': (6, 6, 0), 'chest': (4 + w - 3 * r, 8, 0), 'neck': (-4, -6, 0), 'head': (-4 - 2 * r, -8 + 3 * w, 0),
             'jaw': (3 + 8 * r, 0, 0), 'pauldron_r': (-12, 0, 0), 'pauldron_l': (-4, 0, 0)}
        k = math.radians(2 * w + 10 * r)
        D = (0.0, math.sin(k), -math.cos(k))
        G = (-3.3, 20.2 + 0.4 * r, -7.4 + 1.0 * r)
        return S.hold(p, D, (0, 1, 0), G=G, two_hands=False, left_at=bar, left_frame=bar_frame,
                      pole_r=(-1.0, -1.0, 0.6), pole_l=(1.0, -0.4, 0.6), curl_l=1.0)

    for i in range(5):
        t = i * 0.5
        mounted.key(t, mount_pose(math.sin(2 * math.pi * t / 2.0)))
    clips.append(mounted)
    for t, r in ((0.0, 0.0), (0.04, 1.0), (0.12, 0.55), (0.24, 0.15), (0.36, 0.0)):
        mshoot.key(t, S.upper(mount_pose(0.0, r)))
    clips.append(mshoot)
    clips.extend(R.special_clips())
    return clips


STATES = ('knockback', 'fall', 'land', 'death', 'sever', 'disembark', 'mounted', 'mounted_shoot')


def rig_fall_extra(self, w):
    return {}


def rig_melee(self):
    """Blade / butt strike: chamber over the right shoulder, cut across, recover (soldier timing)."""
    melee = Clip('melee', 0.72)
    lr = S.upper(self.low_ready())
    melee.key(0.0, lr)
    wind = S.hold({'torso': (-6, 26, 0), 'chest': (-12, 24, 6), 'neck': (0, -18, 0), 'head': (-8, -24, 0), 'jaw': (14, 0, 0),
                   'pauldron_r': (-20, 0, 0), 'pauldron_l': (-6, 0, 0)},
                  (-0.25, 0.8, -0.55), (0.6, 0.3, 0.75), G=(-3.6, 23.0, -1.0), pole_r=(-1.0, -0.4, 0.6), pole_l=(1.0, -1.0, -0.2), roll=0)
    melee.key(0.2, S.upper(wind))
    cut = S.hold({'torso': (14, -24, 0), 'chest': (18, -18, -4), 'neck': (-4, 14, 0), 'head': (-6, 18, 0), 'jaw': (22, 0, 0),
                  'pauldron_r': (-8, 0, 0), 'pauldron_l': (-12, 0, 0)},
                 (0.55, -0.6, -0.58), (-0.5, 0.3, -0.6), G=(-1.2, 19.0, -6.4), pole_r=(-1.0, -0.6, 0.3), pole_l=(1.0, -0.8, 0.4), roll=0)
    melee.key(0.34, S.upper(cut))
    follow = S.hold({'torso': (12, -30, 0), 'chest': (14, -22, -6), 'neck': (-4, 18, 0), 'head': (-4, 22, 0), 'jaw': (12, 0, 0),
                     'pauldron_r': (-6, 0, 0), 'pauldron_l': (-10, 0, 0)},
                    (0.8, -0.55, -0.2), (-0.4, 0.2, -0.9), G=(0.4, 17.6, -5.2), pole_r=(-0.6, -1.0, 0.0), pole_l=(1.0, -0.6, 0.6), roll=0)
    melee.key(0.5, S.upper(follow))
    melee.key(0.72, lr)
    return melee


def rig_deaths(self):
    """death: struck, knees buckle, falls forward and lies still; sever: hive cut, convulses, drops backwards."""
    death = Clip('death', 3.5)
    death.key(0.0, merge(self.low_ready(), Z))
    death.key(0.12, merge(self.low_ready(), {'chest': (-18, 10, 8), 'head': (-24, 14, 0), 'jaw': (20, 0, 0), 'arm_l': (-40, -20, -40),
                                             'forearm_l': (-30, 0, 0), 'hand_l': (0, 0, 0)}, Z))
    death.key(0.45, {'root': {'rot': [12, 0, 0], 'pos': [0, -3.5, 0]}, 'hips': (20, 0, 0), 'leg_r': (-70, 0, 8), 'shin_r': (110, 0, 0),
                     'foot_r': (-30, 0, 0), 'leg_l': (-50, 0, -6), 'shin_l': (100, 0, 0), 'foot_l': (-40, 0, 0), 'torso': (6, 0, 0),
                     'chest': (20, 0, 0), 'neck': (0, 0, 0), 'head': (24, 0, 10), 'arm_r': (-20, 0, 30), 'forearm_r': (-30, 0, 0),
                     'hand_r': (0, 0, 0), 'arm_l': (-10, 0, -30), 'forearm_l': (-20, 0, 0), 'jaw': (24, 0, 0)})
    lie = {'root': {'rot': [88, 0, 0], 'pos': [0, 1.5, -15]}, 'hips': (-6, 0, 0), 'leg_r': (-8, 0, 6), 'shin_r': (22, 0, 0),
           'foot_r': (20, 0, 0), 'leg_l': (6, 0, -4), 'shin_l': (30, 0, 0), 'foot_l': (24, 0, 0), 'torso': (0, 0, 0),
           'chest': (-6, 0, 0), 'neck': (-10, 0, 0), 'head': (-24, 32, 0), 'arm_r': (-160, 10, 40), 'forearm_r': (-10, 0, 0),
           'hand_r': (0, 0, 0), 'arm_l': (-20, 0, -50), 'forearm_l': (-40, 0, 0), 'jaw': (28, 0, 0)}
    death.key(0.9, lie)
    death.key(1.05, merge(lie, {'root': {'rot': [84, 0, 0], 'pos': [0, 2.2, -15]}, 'head': (-28, 30, 0), 'arm_r': (-150, 10, 44)}))
    death.key(1.25, merge(lie, {'root': {'rot': [90, 0, 0], 'pos': [0, 1.6, -15.5]}}))
    death.key(3.5, merge(lie, {'root': {'rot': [90, 0, 0], 'pos': [0, 1.6, -15.5]}}))
    death.keep.update({'root'})
    sever = Clip('sever', 3.5)
    sever.key(0.0, merge(self.low_ready(), Z))
    for t, ch, hd, al in ((0.08, (-24, 0, 0), (-38, 0, 0), (-50, -30, -60)), (0.2, (-8, 6, 10), (-14, -20, 6), (-40, -10, -40)),
                          (0.32, (-22, -6, -10), (-30, 20, -6), (-20, -20, -30))):
        sever.key(t, merge(self.low_ready(), {'chest': ch, 'head': hd, 'jaw': (30, 0, 0), 'arm_l': al, 'forearm_l': (-20, 0, 0),
                                              'hand_l': (0, 0, 0)}, Z))
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
    return [death, sever]


Rig.fall_extra = rig_fall_extra
Rig.melee_clip = rig_melee
Rig.death_clips = rig_deaths


# ---- Captor clips ------------------------------------------------------------------------------------------
class CaptorRig(Rig):
    cfg = CAPTOR
    walk_len, run_len = 0.9, 0.5
    swing, knee, run_swing, run_knee = 28, 46, 44, 82
    crouch, lean = 0.45, 0.7
    LOW = dict(D=(0.2, -0.55, -0.81), U=(0.0, 1.0, 0.0), G=(-2.4, 18.0, -4.0))
    AIM_BUTT = (-1.8, 24.0, -3.0)

    def fall_extra(self, w):
        return {'clamp_r': (0, 0, 20 * w), 'clamp_l': (0, 0, -20 * w)}

    def reload_clip(self):
        """Recharges the snare coil: slaps the cell with the off hand, the coil hisses, back to ready."""
        c = Clip('reload', 0.9)
        lr = self.low_ready()
        c.key(0.0, S.upper(lr))
        G = (-2.2, 18.4, -4.2)
        D = (0.1, 0.35, -0.93)
        base = self.body_low()
        base.update({'chest': (12, 18, 0), 'head': (-4, -2, 0)})
        cell = np.array([-2.0, 18.4, -9.0])
        p = self.one_hand(base, D, (0, 1, 0), G, left_at=cell, left_frame=hand_frame((0, -0.3, -1), (0, 1, 0)))
        c.key(0.3, S.upper(p))
        p2 = self.one_hand(merge(base, {'chest': (14, 16, 2)}), D, (0, 1, 0), G, left_at=cell + np.array([0, -1.2, 0.4]),
                           left_frame=hand_frame((0, -0.5, -1), (0, 1, 0)))
        c.key(0.45, S.upper(p2))
        c.key(0.9, S.upper(lr))
        return c

    def melee_clip(self):
        """Lance jab: pull back, drive the fork forward at chest height, twist, recover."""
        c = Clip('melee', 0.66)
        lr = S.upper(self.low_ready())
        c.key(0.0, lr)
        back = S.hold({'torso': (2, 22, 0), 'chest': (0, 22, 0), 'neck': (0, -14, 0), 'head': (-4, -20, 0), 'jaw': (10, 0, 0),
                       'pauldron_r': (-10, 0, 0)}, (0.05, -0.1, -1.0), (0, 1, 0), G=(-3.4, 19.6, 1.0), pole_r=(-1.0, -0.6, 0.6),
                      pole_l=(1.0, -1.0, 0.0), roll=15)
        c.key(0.18, S.upper(back))
        jab = S.hold({'torso': (14, -6, 0), 'chest': (16, -8, 0), 'neck': (-8, 4, 0), 'head': (-10, 6, 0), 'jaw': (26, 0, 0),
                      'pauldron_r': (-4, 0, 0)}, (0.0, -0.12, -1.0), (0, 1, 0), G=(-2.2, 18.6, -9.6), pole_r=(-1.0, -0.6, 0.3),
                     pole_l=(1.0, -0.8, 0.2), roll=15)
        c.key(0.3, S.upper(jab))
        twist = S.hold({'torso': (14, -10, 0), 'chest': (16, -10, -6), 'neck': (-8, 6, 0), 'head': (-10, 8, 0), 'jaw': (18, 0, 0)},
                       (0.1, -0.1, -1.0), (0.7, 0.7, 0), G=(-2.0, 18.4, -9.0), pole_r=(-1.0, -0.6, 0.3), pole_l=(1.0, -0.8, 0.2), roll=15)
        c.key(0.42, S.upper(twist))
        c.key(0.66, lr)
        return c

    def lift_pose(self, base, left_at, frame_d, frame_u, D=(0.15, -0.85, -0.5), G=(-4.6, 14.6, -1.8)):
        return self.one_hand(base, D, (0, 0.3, -1.0), G, left_at=left_at, left_frame=hand_frame(frame_d, frame_u),
                             pole_r=(-1.0, -0.6, 0.6), pole_l=(1.0, -0.6, 0.4))

    def carry_pose(self, w=0.0):
        base = {'torso': (8, -6, 4), 'chest': (6 + w, -8, 6), 'neck': (-4, 6, -2), 'head': (-6, 10 + 4 * w, -4), 'jaw': (6, 0, 0),
                'pauldron_r': (-6, 0, 0), 'pauldron_l': (-2, 0, -10)}
        # off hand clamps the captive's legs at the front of the left shoulder
        return self.lift_pose(base, (5.8, 26.2 + 0.2 * w, -3.0), (0, -0.4, -1.0), (1.0, 0.2, 0))

    def special_clips(self):
        out = []
        # capture (1.3 s = CAPTURE_TICKS): lunge, both hands reach low, the rig clamps unfold, hoist to the shoulder
        cap = Clip('capture', 1.3)
        cap.key(0.0, merge(self.low_ready(self.legs_idle()), {'clamp_r': (0, 0, 0), 'clamp_l': (0, 0, 0)}))
        lunge = {'hips': {'rot': [26, 0, 0], 'pos': [0, -3.2, -1.2]}, 'leg_r': (-64, 0, 6), 'shin_r': (70, 0, 0), 'foot_r': (-8, 0, 0),
                 'leg_l': (10, 0, -6), 'shin_l': (50, 0, 0), 'foot_l': (-20, 0, 0), 'torso': (22, 0, 0), 'chest': (16, 0, 0),
                 'neck': (-20, 0, 0), 'head': (-20, 0, 0), 'jaw': (30, 0, 0), 'pauldron_r': (-14, 0, 0), 'pauldron_l': (-14, 0, 0)}
        reach = self.one_hand(lunge, (0.3, -0.9, -0.2), (0, 0, -1), (-5.0, 10.6, -6.0), left_at=(3.0, 8.4, -11.0),
                              left_frame=hand_frame((0, -0.6, -0.8), (1, 0, 0)), pole_r=(-1.0, -0.4, 0.8), pole_l=(1.0, -0.4, 0.2), curl_l=0.2)
        cap.key(0.3, merge(reach, {'clamp_r': (-40, 0, -30), 'clamp_l': (-40, 0, 30)}))
        grab = dict(reach)
        grab.update(fist('l', 1.0))
        cap.key(0.5, merge(grab, {'clamp_r': (-62, 0, -44), 'clamp_l': (-62, 0, 44)}))
        rise = {'hips': {'rot': [14, 0, 0], 'pos': [0, -1.6, -0.4]}, 'leg_r': (-30, 0, 6), 'shin_r': (44, 0, 0), 'foot_r': (-12, 0, 0),
                'leg_l': (-4, 0, -6), 'shin_l': (36, 0, 0), 'foot_l': (-20, 0, 0), 'torso': (10, -8, 6), 'chest': (2, -10, 8),
                'neck': (-6, 6, 0), 'head': (-8, 10, -4), 'jaw': (24, 0, 0), 'pauldron_r': (-8, 0, 0), 'pauldron_l': (6, 0, -14)}
        cap.key(0.85, merge(self.lift_pose(rise, (6.6, 28.6, -1.6), (0, 0.4, -1.0), (1, 0, 0)), {'clamp_r': (-30, 0, -20), 'clamp_l': (-30, 0, 20)}))
        settle = merge(self.legs_idle(), self.carry_pose())
        cap.key(1.3, merge(settle, {'clamp_r': (0, 0, 0), 'clamp_l': (0, 0, 0)}))
        out.append(cap)
        carry = Clip('carry', 1.6, loop=True)
        for i in range(5):
            t = i * 0.4
            carry.key(t, S.upper(self.carry_pose(math.sin(2 * math.pi * t / 1.6))))
        out.append(carry)
        # drop: shrug the shoulder, the off hand flings the captive down to the left
        drop = Clip('drop', 0.7)
        drop.key(0.0, S.upper(self.carry_pose()))
        shrug = {'torso': (6, 10, -6), 'chest': (4, 14, -10), 'neck': (-4, -6, 4), 'head': (-6, -10, 6), 'jaw': (20, 0, 0),
                 'pauldron_l': (-10, 0, 10)}
        drop.key(0.25, S.upper(self.lift_pose(shrug, (7.8, 20.0, -5.0), (0.4, -0.8, -0.4), (1, 0, 0))))
        drop.key(0.7, S.upper(self.low_ready()))
        out.append(drop)
        return out


# ---- Heavy clips -------------------------------------------------------------------------------------------
class HeavyRig(Rig):
    cfg = HEAVY
    walk_len, run_len = 1.25, 0.8
    swing, knee, run_swing, run_knee = 22, 34, 30, 54
    crouch, width, lean = 0.2, 3.0, 0.1
    LOW = dict(D=(0.12, -0.2, -0.97), U=(0.0, 1.0, 0.0), G=(-3.4, 16.6, -4.6))
    AIM_BUTT = (-2.0, 22.8, -3.0)
    kick = 15.0

    def stride(self, phase, big=False):
        p = Rig.stride(self, phase, big)
        h = p['hips']
        a = 2 * math.pi * phase
        h['rot'][2] += 4 * math.sin(a)          # heavy side-to-side roll of the gait
        h['pos'][1] -= 0.5 * abs(math.cos(a))
        return p

    def fall_extra(self, w):
        return {'vent_r': (-20 * abs(w), 0, 0), 'vent_l': (-20 * abs(w), 0, 0)}

    def reload_clip(self):
        """Swaps the drum cell: off hand pulls it, knocks a fresh one in, slaps it home."""
        c = Clip('reload', 1.3)
        lr = self.low_ready()
        c.key(0.0, S.upper(lr))
        G = (-3.6, 16.0, -4.0)
        D = (0.25, 0.2, -0.95)
        base = self.body_low()
        base.update({'chest': (14, 16, 0), 'head': (-8, 0, 0)})
        drum = np.array([-1.6, 18.6, -7.0])
        p1 = self.one_hand(base, D, (0, 1, 0), G, left_at=drum, left_frame=hand_frame((-0.4, -0.2, -1), (0, 1, 0)))
        c.key(0.25, S.upper(p1))
        p2 = self.one_hand(merge(base, {'chest': (10, 24, 0)}), D, (0, 1, 0), G, left_at=drum + np.array([5.0, -2.0, 2.0]),
                           left_frame=hand_frame((0.3, -0.6, -1), (0, 1, 0)))
        c.key(0.55, S.upper(p2))
        c.key(0.85, S.upper(p1))
        p3 = self.one_hand(merge(base, {'chest': (16, 12, 0)}), D, (0, 1, 0), G, left_at=drum + np.array([0, 0.6, 0]),
                           left_frame=hand_frame((-0.4, -0.2, -1), (0, 1, 0)))
        c.key(0.95, S.upper(p3))
        c.key(1.3, S.upper(lr))
        return c

    def melee_clip(self):
        """Butt stroke: the cannon's stock swung across at head height."""
        c = Clip('melee', 0.8)
        lr = S.upper(self.low_ready())
        c.key(0.0, lr)
        wind = S.hold({'torso': (-4, 30, 0), 'chest': (-6, 26, 0), 'neck': (0, -20, 0), 'head': (-6, -26, 0), 'jaw': (10, 0, 0)},
                      (-0.7, -0.2, 0.6), (0, 1, 0), G=(-5.0, 20.0, 1.0), pole_r=(-1.0, -0.6, 0.6), pole_l=(1.0, -1.0, 0.0), roll=10)
        c.key(0.26, S.upper(wind))
        hit = S.hold({'torso': (10, -28, 0), 'chest': (12, -24, 0), 'neck': (-4, 16, 0), 'head': (-6, 20, 0), 'jaw': (26, 0, 0)},
                     (0.8, -0.1, 0.55), (0, 1, 0), G=(-2.0, 21.0, -6.0), pole_r=(-1.0, -0.6, 0.0), pole_l=(1.0, -0.6, 0.6), roll=10)
        c.key(0.42, S.upper(hit))
        c.key(0.8, lr)
        return c

    def death_clips(self):
        """death: armour sags, drops to both knees, topples forward with a crash; sever: locks up, falls back."""
        death = Clip('death', 3.5)
        death.key(0.0, merge(self.low_ready(), Z))
        death.key(0.15, merge(self.low_ready(), {'chest': (-14, 6, 6), 'head': (-20, 10, 0), 'jaw': (24, 0, 0)}, Z))
        kneel = {'root': {'rot': [0, 0, 0], 'pos': [0, -6.2, 0]}, 'hips': (16, 0, 0), 'leg_r': (-84, 0, 8), 'shin_r': (120, 0, 0),
                 'foot_r': (-30, 0, 0), 'leg_l': (-80, 0, -8), 'shin_l': (118, 0, 0), 'foot_l': (-32, 0, 0), 'torso': (10, 0, 0),
                 'chest': (18, 0, 0), 'neck': (6, 0, 0), 'head': (20, 0, 0), 'arm_r': (-20, 0, 18), 'forearm_r': (-20, 0, 0),
                 'hand_r': (0, 0, 0), 'arm_l': (-14, 0, -18), 'forearm_l': (-20, 0, 0), 'jaw': (20, 0, 0), 'vent_r': (-50, 0, 0),
                 'vent_l': (-50, 0, 0)}
        death.key(0.6, kneel)
        death.key(1.0, merge(kneel, {'root': {'rot': [14, 0, 0], 'pos': [0, -6.6, -1]}, 'chest': (26, 0, 0), 'head': (30, 0, 8)}))
        lie = {'root': {'rot': [86, 0, 0], 'pos': [0, 2.0, -14]}, 'hips': (-4, 0, 0), 'leg_r': (-20, 0, 8), 'shin_r': (40, 0, 0),
               'foot_r': (10, 0, 0), 'leg_l': (-10, 0, -8), 'shin_l': (46, 0, 0), 'foot_l': (12, 0, 0), 'torso': (0, 0, 0),
               'chest': (-4, 0, 0), 'neck': (-8, 0, 0), 'head': (-20, 26, 0), 'arm_r': (-150, 10, 34), 'forearm_r': (-10, 0, 0),
               'hand_r': (0, 0, 0), 'arm_l': (-30, 0, -40), 'forearm_l': (-30, 0, 0), 'jaw': (26, 0, 0), 'vent_r': (-60, 0, 0),
               'vent_l': (-60, 0, 0)}
        death.key(1.4, lie)
        death.key(1.52, merge(lie, {'root': {'rot': [82, 0, 0], 'pos': [0, 2.8, -14]}}))
        death.key(1.7, merge(lie, {'root': {'rot': [90, 0, 0], 'pos': [0, 2.0, -14.5]}}))
        death.key(3.5, merge(lie, {'root': {'rot': [90, 0, 0], 'pos': [0, 2.0, -14.5]}}))
        death.keep.update({'root'})
        sever = Clip('sever', 3.5)
        sever.key(0.0, merge(self.low_ready(), Z))
        lock = merge(self.low_ready(), {'chest': (-20, 0, 0), 'head': (-30, 0, 0), 'jaw': (34, 0, 0), 'vent_r': (-70, 0, 0),
                                        'vent_l': (-70, 0, 0)}, Z)
        sever.key(0.1, lock)
        sever.key(0.6, merge(lock, {'chest': (-26, 4, 6), 'head': (-36, -10, 0)}))
        flat = {'root': {'rot': [-90, 0, 0], 'pos': [0, 2.0, 15]}, 'hips': (0, 0, 0), 'leg_r': (-14, 0, 10), 'shin_r': (24, 0, 0),
                'foot_r': (10, 0, 0), 'leg_l': (-6, 0, -10), 'shin_l': (20, 0, 0), 'foot_l': (14, 0, 0), 'torso': (0, 0, 0),
                'chest': (0, 0, 0), 'neck': (6, 0, 0), 'head': (20, -24, 0), 'arm_r': (-8, 0, 60), 'forearm_r': (-20, 0, 0),
                'hand_r': (0, 0, 0), 'arm_l': (-10, 0, -70), 'forearm_l': (-30, 0, 0), 'jaw': (32, 0, 0), 'vent_r': (-70, 0, 0),
                'vent_l': (-70, 0, 0)}
        sever.key(1.2, flat)
        sever.key(1.32, merge(flat, {'root': {'rot': [-86, 0, 0], 'pos': [0, 2.8, 15]}}))
        sever.key(1.5, flat)
        sever.key(3.5, flat)
        sever.keep.update({'root'})
        return [death, sever]

    def special_clips(self):
        out = []
        # slam (1.7 s = SLAM_TICKS, contact at 0.7 s = SLAM_CONTACT): cannon raised overhead, driven butt-first
        # into the street with a crouch, then hauled back up
        slam = Clip('slam', 1.7)
        slam.key(0.0, merge(self.low_ready(self.legs_idle()), {'vent_r': (0, 0, 0), 'vent_l': (0, 0, 0)}))
        up = {'hips': {'rot': [-6, 0, 0], 'pos': [0, 0.2, 0]}, 'leg_r': (-6, 0, 6), 'shin_r': (8, 0, 0), 'foot_r': (-2, 0, 0),
              'leg_l': (4, 0, -6), 'shin_l': (10, 0, 0), 'foot_l': (-6, 0, 0), 'torso': (-12, 0, 0), 'chest': (-14, 0, 0),
              'neck': (6, 0, 0), 'head': (-6, 0, 0), 'jaw': (30, 0, 0), 'pauldron_r': (-30, 0, 0), 'pauldron_l': (-30, 0, 0)}
        raised = S.hold(up, (0.0, -1.0, 0.15), (0, 0, -1), G=(-1.6, 36.0, -2.0), pole_r=(-1.0, 0.2, 0.6), pole_l=(1.0, 0.2, 0.6), roll=0)
        slam.key(0.45, merge(raised, {'vent_r': (-40, 0, 0), 'vent_l': (-40, 0, 0)}))
        down = {'hips': {'rot': [30, 0, 0], 'pos': [0, -4.4, -0.6]}, 'leg_r': (-70, 0, 8), 'shin_r': (96, 0, 0), 'foot_r': (-26, 0, 0),
                'leg_l': (-20, 0, -8), 'shin_l': (84, 0, 0), 'foot_l': (-50, 0, 0), 'torso': (24, 0, 0), 'chest': (20, 0, 0),
                'neck': (-12, 0, 0), 'head': (-18, 0, 0), 'jaw': (36, 0, 0), 'pauldron_r': (-10, 0, 0), 'pauldron_l': (-10, 0, 0)}
        hit = S.hold(down, (0.0, -1.0, -0.1), (0, 0, -1), G=(-1.2, 10.4, -9.0), pole_r=(-1.0, -0.2, 0.4), pole_l=(1.0, -0.2, 0.4), roll=0)
        slam.key(0.7, merge(hit, {'vent_r': (-10, 0, 0), 'vent_l': (-10, 0, 0)}))
        slam.key(0.86, merge(hit, {'chest': (24, 0, 0), 'vent_r': (-60, 0, 0), 'vent_l': (-60, 0, 0)}))
        slam.key(1.3, merge(self.low_ready(merge(self.legs_idle(), {'hips': {'rot': [12, 0, 0], 'pos': [0, -1.8, 0]}})),
                            {'vent_r': (-40, 0, 0), 'vent_l': (-40, 0, 0)}))
        slam.key(1.7, merge(self.low_ready(self.legs_idle()), {'vent_r': (0, 0, 0), 'vent_l': (0, 0, 0)}))
        out.append(slam)
        # vent: overheated: flaps blow open, it hunches and lowers the cannon while the pack dumps heat
        vent = Clip('vent', 1.4)
        lr = self.low_ready()
        vent.key(0.0, merge(S.upper(lr), {'vent_r': (0, 0, 0), 'vent_l': (0, 0, 0), 'pack': (0, 0, 0)}))
        hunch = self.low_ready(over={'torso': (14, 4, 0), 'chest': (16, 6, 0), 'head': (-4, 24, 0), 'jaw': (26, 0, 0)})
        vent.key(0.18, merge(S.upper(hunch), {'vent_r': (-75, 0, -10), 'vent_l': (-75, 0, 10), 'pack': (4, 0, 0)}))
        vent.key(1.0, merge(S.upper(hunch), {'vent_r': (-70, 0, -8), 'vent_l': (-70, 0, 8), 'pack': (2, 0, 0)}))
        vent.key(1.4, merge(S.upper(lr), {'vent_r': (0, 0, 0), 'vent_l': (0, 0, 0), 'pack': (0, 0, 0)}))
        out.append(vent)
        # armor_break: the chest plate is blown off (flies forward and spins), it reels back roaring
        br = Clip('armor_break', 0.9)
        br.key(0.0, merge(self.low_ready(), {'armor_chest': {'rot': [0, 0, 0], 'pos': [0, 0, 0], 'scale': [1, 1, 1]}}))
        reel = self.low_ready(merge(self.legs_idle(), {'hips': {'rot': [-12, 0, 0], 'pos': [0, -0.8, 1.0]}, 'leg_r': (10, 0, 6)}),
                              over={'torso': (-14, 0, 0), 'chest': (-22, 0, 0), 'neck': (-8, 0, 0), 'head': (-30, 0, 0), 'jaw': (40, 0, 0)})
        br.key(0.12, merge(reel, {'armor_chest': {'rot': [-40, 20, 0], 'pos': [0, 3, -8], 'scale': [1, 1, 1]}}))
        br.key(0.4, merge(reel, {'armor_chest': {'rot': [-160, 60, 20], 'pos': [1, 1, -20], 'scale': [0.8, 0.8, 0.8]}}))
        br.key(0.5, merge(reel, {'armor_chest': {'rot': [-200, 70, 20], 'pos': [1, -4, -24], 'scale': [0, 0, 0]}}))
        br.key(0.9, merge(self.low_ready(), {'armor_chest': {'rot': [0, 0, 0], 'pos': [0, 0, 0], 'scale': [0, 0, 0]}}))
        br.keep.update({'armor_chest'})
        out.append(br)
        st = Clip('stagger', 0.8)
        st.key(0.0, self.low_ready(self.legs_idle()))
        st.key(0.12, self.low_ready(merge(self.legs_idle(), {'hips': {'rot': [-10, 14, 6], 'pos': [0, -1.2, 1.0]}, 'leg_r': (16, 0, 10),
                                                              'shin_r': (20, 0, 0), 'leg_l': (-24, 0, -10), 'shin_l': (50, 0, 0)}),
                                    over={'torso': (-10, 10, 8), 'chest': (-14, 10, 6), 'head': (-22, 18, 0), 'jaw': (30, 0, 0)}))
        st.key(0.45, self.low_ready(merge(self.legs_idle(), {'hips': {'rot': [16, 0, 0], 'pos': [0, -2.4, 0]}, 'shin_r': (50, 0, 0),
                                                              'shin_l': (56, 0, 0)}), over={'torso': (16, 6, 0), 'head': (-14, -8, 0)}))
        st.key(0.8, self.low_ready(self.legs_idle()))
        out.append(st)
        return out


# ---- Commander clips ---------------------------------------------------------------------------------------
class CommanderRig(Rig):
    cfg = COMMANDER
    walk_len, run_len = 1.1, 0.66
    swing, knee, run_swing, run_knee = 24, 36, 36, 64
    crouch, lean = 0.0, -0.25
    LOW = dict(D=(0.32, -0.5, -0.8), U=(0.0, 1.0, 0.0), G=(-2.0, 18.4, -3.4))
    AIM_BUTT = (-1.6, 24.6, -3.2)

    def shouldered(self, sway=0.0, recoil=0.0, base=None):
        """The glaive levelled like a spear at chest height (its long butt rules out a shoulder stock)."""
        p = dict(base or {})
        p.update(self.body_aim(sway, recoil))
        k = math.radians(1.2 * sway + self.kick * recoil)
        D = (0.04, math.sin(k), -math.cos(k))
        G = (-2.9, 21.4 + 0.3 * recoil, -6.6 + 1.2 * recoil)
        return S.hold(p, D, (0, 1, 0), G=G, pole_r=(-1.0, -0.6, 0.4), pole_l=(0.8, -1.0, 0.0), roll=20)

    def cape(self, sway=0.0, lift=0.0):
        return {'cape1': (8 + 18 * lift + 3 * sway, 0, 2 * sway), 'cape2': (-4 + 10 * lift + 4 * sway, 0, 3 * sway),
                'cape3': (-4 + 8 * lift + 5 * sway, 0, 4 * sway), 'tabard_f': (-4 * lift - 2 * sway, 0, 0),
                'tabard_b': (6 * lift + 2 * sway, 0, 0)}

    def planted(self, base, breath=0.0, look=0.0):
        """Glaive planted upright at its right side, the off hand resting on the belt."""
        p = dict(base)
        p.update({'torso': (-2, 4, 0), 'chest': (-3 + 1.2 * breath, 4, 0), 'neck': (2, -4 + 0.3 * look, 0),
                  'head': (-4, -6 + 0.6 * look, 0), 'pauldron_r': (-4, 0, 0), 'pauldron_l': (-4, 0, 0)})
        q = self.one_hand(p, (0.0, 1.0, 0.0), (0, 0, -1), (-5.6, 14.4, -2.8), left_at=(3.2, 16.4, -2.4),
                          left_frame=hand_frame((-0.7, -0.6, -0.3), (0.3, 0, -1)), pole_r=(-1.0, -0.6, 0.6), pole_l=(1.0, -0.5, 0.4), curl_l=0.6)
        return q

    def fall_extra(self, w):
        return merge(self.cape(w, 1.0), {'antenna_r': (10 * w, 0, 0), 'antenna_l': (10 * w, 0, 0)})

    def melee_clip(self):
        c = rig_melee(self)
        for i, (t, pose) in enumerate(c.frames):
            pose = dict(pose)
            pose.update(self.cape((-1, 0.4, 1.0, 0.6, -0.2)[min(i, 4)], 0.3))
            c.frames[i] = (t, pose)
        return c

    def reload_clip(self):
        """Recharges the glaive: the off hand runs along the blade, the edge flares, a twirl back to ready."""
        c = Clip('reload', 1.1)
        lr = self.low_ready()
        c.key(0.0, S.upper(lr))
        base = {'torso': (0, 10, 0), 'chest': (0, 12, 0), 'neck': (0, -6, 0), 'head': (8, -10, 0), 'pauldron_r': (-8, 0, 0)}
        D = (0.0, 0.85, -0.5)
        G = (-3.4, 17.0, -5.0)
        p1 = self.one_hand(base, D, (0, 0.5, 0.85), G, left_at=(-2.6, 23.0, -8.6), left_frame=hand_frame((0, 0.5, -0.9), (1, 0, 0)),
                           curl_l=0.4)
        c.key(0.3, S.upper(p1))
        p2 = self.one_hand(base, D, (0, 0.5, 0.85), G, left_at=(-2.6, 28.6, -11.6), left_frame=hand_frame((0, 0.5, -0.9), (1, 0, 0)),
                           curl_l=0.4)
        c.key(0.6, S.upper(p2))
        c.key(1.1, S.upper(lr))
        return c

    def special_clips(self):
        out = []
        idle2 = Clip('idle_planted', 3.6, loop=True)
        for i in range(10):
            t = i * 0.4
            f = math.sin(2 * math.pi * t / 3.6)
            look = 24 * math.sin(2 * math.pi * t / 3.6 + 0.3) if i < 9 else 24 * math.sin(0.3)
            idle2.key(t, merge(self.planted(self.legs_idle(f), f, look), self.cape(f * 0.5)))
        out.append(idle2)
        # signal (2.2 s = SIGNAL_TICKS): plants the glaive, raises the off hand, antennae flare, roars the order
        sig = Clip('signal', 2.2)
        sig.key(0.0, merge(self.low_ready(self.legs_idle()), self.cape(), {'antenna_r': (0, 0, 0), 'antenna_l': (0, 0, 0)}))
        plant = self.planted(self.legs_idle())
        sig.key(0.35, merge(plant, self.cape(0.4, 0.2), {'antenna_r': (-10, 0, 0), 'antenna_l': (-10, 0, 0)}))
        roar_base = merge(self.legs_idle(), {'hips': {'rot': [-4, 0, 0], 'pos': [0, -0.2, 0]}, 'torso': (-8, 0, 0), 'chest': (-12, -6, 0),
                                             'neck': (-6, 0, 0), 'head': (-24, 8, 0), 'jaw': (42, 0, 0), 'pauldron_l': (-30, 0, -20)})
        roar = self.one_hand(roar_base, (0.0, 1.0, 0.0), (0, 0, -1), (-6.0, 14.0, -3.0), left_at=(6.0, 36.0, -3.0),
                             left_frame=hand_frame((0, 1, 0), (0, 0, 1)), pole_r=(-1.0, -0.6, 0.6), pole_l=(1.0, 0.0, 0.6), curl_l=1.0)
        sig.key(0.7, merge(roar, self.cape(-0.6, 0.6), {'antenna_r': (-40, 0, -20), 'antenna_l': (-40, 0, 20)}))
        sig.key(1.5, merge(roar, {'head': (-20, -8, 0), 'jaw': (36, 0, 0)}, self.cape(0.6, 0.5),
                           {'antenna_r': (-44, 0, -24), 'antenna_l': (-44, 0, 24)}))
        sig.key(1.85, merge(plant, self.cape(0.2, 0.1), {'antenna_r': (-10, 0, 0), 'antenna_l': (-10, 0, 0)}))
        sig.key(2.2, merge(self.low_ready(self.legs_idle()), self.cape(), {'antenna_r': (0, 0, 0), 'antenna_l': (0, 0, 0)}))
        out.append(sig)
        # command: the glaive thrust forward one-handed at the objective, the off hand sweeps the squad on
        cmd = Clip('command', 1.0)
        lr = S.upper(self.low_ready())
        cmd.key(0.0, merge(lr, self.cape()))
        point_b = {'torso': (2, -10, 0), 'chest': (0, -14, 0), 'neck': (0, 8, 0), 'head': (-6, 12, 0), 'jaw': (26, 0, 0),
                   'pauldron_r': (-20, 0, 0)}
        pnt = self.one_hand(point_b, (0.05, 0.08, -1.0), (0, 1, 0), (-4.8, 24.0, -9.4), left_at=(9.6, 22.0, -3.0),
                            left_frame=hand_frame((1, -0.2, -0.3), (0, 1, 0)), pole_r=(-1.0, -0.2, 0.4), pole_l=(1.0, -0.6, 0.6), curl_l=0.2)
        cmd.key(0.25, merge(S.upper(pnt), self.cape(0.6, 0.2)))
        cmd.key(0.65, merge(S.upper(pnt), {'head': (-4, 4, 0), 'jaw': (12, 0, 0)}, self.cape(-0.3, 0.1)))
        cmd.key(1.0, merge(lr, self.cape()))
        out.append(cmd)
        # sweep: a full spin with the glaive held out flat (root yaw turns 360, kept by the renderer only for the clip)
        sw = Clip('sweep', 0.8)
        sw.key(0.0, merge(self.low_ready(self.legs_idle()), Z, self.cape()))
        coil = merge(self.legs_idle(), {'hips': {'rot': [10, 30, 0], 'pos': [0, -1.6, 0]}, 'leg_r': (-30, 0, 8), 'shin_r': (50, 0, 0),
                                        'leg_l': (-10, 0, -10), 'shin_l': (40, 0, 0)})
        coil.update({'torso': (6, 30, 0), 'chest': (4, 30, 0), 'head': (-6, -40, 0), 'jaw': (20, 0, 0)})
        wound = S.hold(coil, (-0.9, -0.1, 0.4), (0, 1, 0), G=(-6.4, 19.0, 2.0), pole_r=(-1.0, -0.4, 0.6), pole_l=(1.0, -0.6, 0.0), roll=0)
        sw.key(0.16, merge(wound, {'root': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}, self.cape(0.5, 0.3)))
        flat = merge(self.legs_idle(), {'hips': {'rot': [14, 0, 0], 'pos': [0, -2.0, 0]}, 'shin_r': (50, 0, 0), 'shin_l': (50, 0, 0)})
        flat.update({'torso': (8, -10, 0), 'chest': (6, -12, 0), 'head': (-10, 10, 0), 'jaw': (32, 0, 0)})
        out_g = S.hold(flat, (-0.15, -0.05, -1.0), (0, 1, 0), G=(-4.0, 19.6, -8.0), pole_r=(-1.0, -0.4, 0.2), pole_l=(1.0, -0.6, 0.4), roll=0)
        for t, yaw in ((0.3, -120.0), (0.42, -240.0), (0.54, -360.0)):
            sw.key(t, merge(out_g, {'root': {'rot': [0, yaw, 0], 'pos': [0, 0, 0]}}, self.cape(1.0, 1.0)))
        sw.key(0.8, merge(self.low_ready(self.legs_idle()), {'root': {'rot': [0, -360, 0], 'pos': [0, 0, 0]}}, self.cape(-0.4, 0.2)))
        sw.keep.update({'root'})
        out.append(sw)
        st = Clip('stagger', 0.8)
        st.key(0.0, merge(self.low_ready(self.legs_idle()), self.cape()))
        st.key(0.14, merge(self.low_ready(merge(self.legs_idle(), {'hips': {'rot': [-12, -10, -6], 'pos': [0, -1.0, 1.0]}, 'leg_l': (16, 0, -8),
                                                                    'shin_l': (24, 0, 0), 'leg_r': (-24, 0, 8), 'shin_r': (50, 0, 0)}),
                                          over={'torso': (-12, -10, -8), 'chest': (-16, -8, -6), 'head': (-24, -16, 0), 'jaw': (30, 0, 0)}),
                           self.cape(-1.0, 0.8)))
        st.key(0.5, merge(self.low_ready(merge(self.legs_idle(), {'hips': {'rot': [14, 0, 0], 'pos': [0, -2.0, 0]}, 'shin_r': (44, 0, 0),
                                                                   'shin_l': (50, 0, 0)}), over={'torso': (12, 4, 0)}), self.cape(0.6, 0.3)))
        st.key(0.8, merge(self.low_ready(self.legs_idle()), self.cape()))
        out.append(st)
        return out

    def death_clips(self):
        """death: staggers, drops to one knee leaning on the glaive, then slumps sideways; sever: generic."""
        deaths = rig_deaths(self)
        death = Clip('death', 3.5)
        death.key(0.0, merge(self.low_ready(), Z, self.cape()))
        death.key(0.15, merge(self.low_ready(), {'chest': (-18, 10, 8), 'head': (-26, 14, 0), 'jaw': (30, 0, 0)}, Z, self.cape(-0.8, 0.5)))
        knee = {'root': {'rot': [0, 0, 0], 'pos': [0, -5.4, 0]}, 'hips': (6, 0, 0), 'leg_r': (-90, 0, 6), 'shin_r': (90, 0, 0),
                'foot_r': (0, 0, 0), 'leg_l': (-10, 0, -6), 'shin_l': (110, 0, 0), 'foot_l': (-60, 0, 0), 'torso': (14, 0, 0),
                'chest': (18, 6, 0), 'neck': (10, 0, 0), 'head': (24, 0, 0), 'jaw': (26, 0, 0)}
        kneel = self.one_hand(knee, (0.0, -1.0, 0.05), (0, 0, -1), (-6.6, 12.0, -6.0), left_at=(3.0, 13.0, -3.0),
                              left_frame=hand_frame((0, -1, -0.3), (0, 0, -1)), curl_l=0.5)
        death.key(0.6, merge(kneel, self.cape(0.3, 0.2)))
        death.key(1.3, merge(kneel, {'head': (34, 0, 10), 'chest': (24, 6, 6)}, self.cape(0.1, 0.0)))
        side = {'root': {'rot': [0, 0, -88], 'pos': [12, 2.0, 0]}, 'hips': (0, 0, 0), 'leg_r': (-40, 0, 6), 'shin_r': (60, 0, 0),
                'foot_r': (0, 0, 0), 'leg_l': (-20, 0, -6), 'shin_l': (70, 0, 0), 'foot_l': (-10, 0, 0), 'torso': (10, 0, 0),
                'chest': (10, 0, 0), 'neck': (0, 0, 0), 'head': (10, 0, -20), 'arm_r': (-60, 0, 20), 'forearm_r': (-40, 0, 0),
                'hand_r': (0, 0, 0), 'arm_l': (-30, 0, -20), 'forearm_l': (-50, 0, 0), 'jaw': (30, 0, 0)}
        death.key(1.8, merge(side, self.cape(0.0, 0.6)))
        death.key(1.95, merge(side, {'root': {'rot': [0, 0, -84], 'pos': [12, 2.6, 0]}}, self.cape(0.2, 0.7)))
        death.key(2.1, merge(side, {'root': {'rot': [0, 0, -90], 'pos': [12.5, 2.0, 0]}}, self.cape(0.0, 0.6)))
        death.key(3.5, merge(side, {'root': {'rot': [0, 0, -90], 'pos': [12.5, 2.0, 0]}}, self.cape(0.0, 0.6)))
        death.keep.update({'root'})
        return [death, deaths[1]]


# ---- writing / review --------------------------------------------------------------------------------------
UNITS = {'captor': (build_captor, CaptorRig), 'heavy': (build_heavy, HeavyRig), 'commander': (build_commander, CommanderRig)}
LIMIT = 58_000


def paths(name):
    return {'geo': 'geo/chitauri/%s.geo.json' % name, 'tex': 'textures/entity/chitauri/%s.png' % name,
            'glow': 'textures/entity/chitauri/%s_glow.png' % name,
            'anims': ['animations/chitauri/%s.animation.json' % name, 'animations/chitauri/%s_states.animation.json' % name,
                      'animations/chitauri/%s_special.animation.json' % name]}


def write_unit(name):
    build, rig_cls = UNITS[name]
    P = paths(name)
    m = build()
    m.write(P['geo'], P['tex'], P['glow'])
    n = sum(len(b.cubes) for b in m.bones)
    with open(os.path.join(L.RES, P['geo'])) as fh:
        g = json.load(fh)
    R = rig_cls(g)
    R.bind()
    clips = common_clips(R)
    specials = {c.name for c in R.special_clips.__func__(R)} if False else None
    groups = ([], [], [])
    base_names = {'idle', 'walk', 'run', 'turn_l', 'turn_r', 'aim', 'shoot', 'reload', 'melee', 'hurt'}
    for c in clips:
        groups[0 if c.name in base_names else 1 if c.name in STATES else 2].append(c)
    sizes = [write_anims(P['anims'][i], groups[i]) for i in range(3)]
    geo_size = os.path.getsize(os.path.join(L.RES, P['geo']))
    print('%s: %d bones, %d cubes, atlas %dx%d, geo %d B, clips %d (%s B)' % (name, len(m.bones), n, m.width, m.height, geo_size,
                                                                            len(clips), '/'.join(map(str, sizes))))
    assert geo_size < LIMIT and all(s < LIMIT for s in sizes), 'asset file over the push limit'
    return g, clips


def review(name, g, clips, out_dir, frac_default=0.3):
    P = paths(name)
    tex = os.path.join(L.ROOT, 'build', 'jinwoo_preview', P['tex'])
    glow = os.path.join(L.ROOT, 'build', 'jinwoo_preview', P['glow'])
    ims = []
    for c in clips:
        t = min(c.length * frac_default, c.length)
        pose = L.sample_clip(c, t)
        ims.append(L.preview(os.path.join(out_dir, '%s_%s.png' % (name, c.name)), [(g, pose, tex, glow)], views=('q_front', 'side'), scale=4))
    L.preview(os.path.join(out_dir, '%s_rest.png' % name), [(g, {}, tex, glow)], views=('front', 'q_front', 'side', 'back'), scale=8)
    L.sheet(os.path.join(out_dir, '%s_clips.png' % name), ims, cols=6)


def main():
    only = None
    if '--only' in sys.argv:
        only = sys.argv[sys.argv.index('--only') + 1].split(',')
    out = os.path.join(L.ROOT, 'build', 'chitauri_preview')
    for name in UNITS:
        if only and name not in only:
            continue
        g, clips = write_unit(name)
        if '--preview' in sys.argv:
            review(name, g, clips, out)
        if '--review' in sys.argv:
            i = sys.argv.index('--review')
            by = {c.name: c for c in clips}
            P = paths(name)
            tex = os.path.join(L.ROOT, 'build', 'jinwoo_preview', P['tex'])
            glow = os.path.join(L.ROOT, 'build', 'jinwoo_preview', P['glow'])
            rows = []
            for e in sys.argv[i + 1].split(','):
                cn, fr = (e.split(':') + ['0.3'])[:2]
                if cn in by:
                    c = by[cn]
                    rows.append(L.preview(os.path.join(out, 'rv_%s_%s.png' % (name, cn)), [(g, L.sample_clip(c, c.length * float(fr)), tex, glow)],
                                          views=('front', 'q_front', 'side', 'q_back'), scale=6))
            if rows:
                L.sheet(os.path.join(out, 'review_%s.png' % name), rows, cols=1)


if __name__ == '__main__':
    main()
