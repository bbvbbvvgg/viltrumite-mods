"""SCP-096 clips for viltrumitecore (own work, CC0): see Scp096Renderer for how they are layered.

Poses are written as Bedrock degrees per bone ({bone: (rx, ry, rz)} plus root position); the hands-on-face poses
and every planted foot are solved with the small IK kit of chitauri_lib (two-bone reach + wrist/foot orientation)
on the generated geo, so the palms really sit on the face and the soles on the ground in every key.
Conventions (checked on the previews): spine/head rx > 0 bends forward, jaw rx > 0 opens, arm/thigh rx < 0 swings
forward, forearm rx < 0 flexes the elbow, shin rx > 0 flexes the knee, right arm rz > 0 lifts it outward.

    python3 tools/assets/scp096_anims.py [--preview]
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
import chitauri_lib as cl  # noqa: E402
import scp096_model as sm  # noqa: E402
from jinwoo_geo import Clip, write_anims, RES, ROOT  # noqa: E402

ANIM_REL = 'animations/scp096/scp096.animation.json'
PREVIEW = os.path.join(ROOT, 'build', 'scp096_preview')
BONES = sm.all_bones()
GEO = None


def geo():
    global GEO
    if GEO is None:
        with open(os.path.join(RES, sm.GEO_REL)) as fh:
            GEO = json.load(fh)
    return GEO


# ---- pose algebra ------------------------------------------------------------------------------------------
def P(d=None, **kw):
    """Pose from {bone: (rx, ry, rz)} (+ 'root_pos'): FK dict {bone: {'rot': [...], 'pos': [...]}}."""
    src = dict(d or {})
    src.update(kw)
    out = {}
    for k, v in src.items():
        if k == 'root_pos':
            out.setdefault('root', {})['pos'] = list(v)
        else:
            out.setdefault(k, {})['rot'] = list(v)
    return out


def full(p):
    out = {}
    for b in BONES:
        e = p.get(b, {})
        out[b] = {'rot': list(e.get('rot', [0.0, 0.0, 0.0]))}
        if b == 'root':
            out[b]['pos'] = list(e.get('pos', [0.0, 0.0, 0.0]))
    return out


def copy(p):
    return json.loads(json.dumps(p))


def mix(a, b, f):
    a, b = full(a), full(b)
    out = {}
    for k in BONES:
        out[k] = {}
        for ch in a[k]:
            out[k][ch] = [x + (y - x) * f for x, y in zip(a[k][ch], b[k][ch])]
    return out


def add(base, delta):
    """base + delta (rotations and the root position add up)."""
    out = full(base)
    for k, v in delta.items():
        for ch in ('rot', 'pos'):
            if ch in v:
                cur = out.setdefault(k, {}).setdefault(ch, [0.0, 0.0, 0.0])
                out[k][ch] = [x + y for x, y in zip(cur, v[ch])]
    return out


def mirror(p):
    """Left/right swap: right and left limbs trade poses, yaw and roll flip sign."""
    out = {}
    for k, v in full(p).items():
        nk = k[:-2] + ('_l' if k.endswith('_r') else '_r') if k.endswith(('_r', '_l')) else k
        e = {'rot': [v['rot'][0], -v['rot'][1], -v['rot'][2]]}
        if 'pos' in v:
            e['pos'] = [-v['pos'][0], v['pos'][1], v['pos'][2]]
        out[nk] = e
    return out


def set_rot(p, bone, rot):
    p.setdefault(bone, {})['rot'] = [float(x) for x in rot]


# ---- kinematics --------------------------------------------------------------------------------------------
def world_R(p, bone):
    return cl.bone_matrix(geo(), p, bone)[:3, :3]


def wpoint(p, bone, local):
    return np.array(cl.point(geo(), p, bone, local))


def orient(p, bone, R_world):
    """Sets the bone's own rotation so its world rotation becomes R_world."""
    parent = cl.bones_of(geo())[bone].get('parent')
    Rp = world_R(p, parent) if parent else np.eye(3)
    set_rot(p, bone, cl.euler_of(Rp.T @ R_world))


def min_y(p, bones=None):
    quads, _, _ = cl.posed_quads(geo(), p)
    ys = [c[1] for corners, _ in quads for c in corners]
    return min(ys)


def grounded(p):
    """Shifts the root so the lowest point of the model touches y = 0."""
    p = full(p)
    p['root']['pos'][1] -= min_y(p)
    return p


def rot_axis(axis, deg):
    a = math.radians(deg)
    x, y, z = cl.unit(axis)
    c, s = math.cos(a), math.sin(a)
    C = 1 - c
    return np.array([[c + x * x * C, x * y * C - z * s, x * z * C + y * s],
                     [y * x * C + z * s, c + y * y * C, y * z * C - x * s],
                     [z * x * C - y * s, z * y * C + x * s, c + z * z * C]])


def plant_foot(p, side, target, yaw=0.0, pitch=0.0, knee_pole=None):
    """Two-bone leg IK: the foot pivot onto target (world px), the sole turned flat (plus pitch/yaw)."""
    thigh, shin, foot = 'thigh_' + side, 'shin_' + side, 'foot_' + side
    s = -1 if side == 'r' else 1
    pole = knee_pole if knee_pole is not None else (s * 0.15, 0.2, -1.0)
    q, err = cl.solve_chain(geo(), p, thigh, shin, target, pole, end_bone=shin,
                            end_local=cl.pivot_of(geo(), foot), elbow_range=(1.0, 165.0))
    p.update(q)
    R = rot_axis((0, 1, 0), yaw * s) @ rot_axis((1, 0, 0), -pitch)
    orient(p, foot, R)
    return err


HAND_GRIP = {'r': (-3.5, 10.55, 0.28), 'l': (3.5, 10.55, 0.28)}


def hand_on(p, side, bone, local, d_local, u_local, pole, curl=0.0, tip_curl=None, thumb=0.0):
    """Puts the palm of hand_<side> at a point given in another bone's space (e.g. the head), the fingers along
    d_local and the thumb side toward u_local (both in that bone's axes); curls the fingers toward the palm."""
    Mb = cl.bone_matrix(geo(), p, bone)
    G = Mb[:3, :3] @ np.array(local, float) + Mb[:3, 3]
    R_hand = Mb[:3, :3] @ cl.frame(d_local, u_local)
    q, err = cl.place_hand(geo(), p, side, HAND_GRIP[side], G, R_hand, pole)
    p.update(q)
    s = -1 if side == 'r' else 1
    set_rot(p, 'fingers_' + side, (0, 0, s * curl))
    set_rot(p, 'tips_' + side, (0, 0, s * (curl if tip_curl is None else tip_curl)))
    set_rot(p, 'thumb_' + side, (0, 0, s * thumb))
    return err


def cover_face(p, low=0.0, spread=0.0, curl=26.0, pole=(0.22, -1.0, -0.7)):
    """Both palms pressed on the face, fingers up over the eyes and onto the forehead; pole = elbow direction
    (right side; mirrored for the left)."""
    errs = []
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        local = (s * (0.95 + spread), 35.15 - low, -2.95)
        d = (-s * 0.32, 1.0, -0.12)
        u = (s * 1.0, 0.2, 0.0)
        errs.append(hand_on(p, side, 'head', local, d, u, (s * pole[0], pole[1], pole[2]), curl=curl, thumb=10))
    return max(errs)


def claw_face(p, drag=0.0, spread=0.25):
    """Fingers hooked into the face (hysteria): palms a little off the face, fingertips digging in, dragged down."""
    errs = []
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        local = (s * (1.25 + spread), 35.6 - drag, -3.55)
        d = (-s * 0.25, 1.0, 0.35)
        u = (s * 1.0, 0.1, -0.2)
        errs.append(hand_on(p, side, 'head', local, d, u, (s * 0.8, -0.6, -0.3), curl=48.0, tip_curl=62.0, thumb=25))
    return max(errs)


# ---- base poses --------------------------------------------------------------------------------------------
def stand_pose(t=0.0):
    """Hunched shy stance; t in 0..1 is the sway phase."""
    w = math.sin(t * 2 * math.pi)
    c = math.cos(t * 2 * math.pi)
    p = P({
        'root': (0, 0, 1.4 * w),
        'pelvis': (3, 0, -0.8 * w), 'belly': (6, 0.5 * c, 0), 'chest': (10, 1.5 * c, 0.6 * w),
        'neck': (18, 0, -1.0 * w), 'head': (-23 + 1.5 * c, 3.0 * c, 4.0 + 3.0 * w), 'jaw': (7 + 1.5 * c, 0, 0),
        'arm_r': (-24 + 2.5 * w, 0, 5 - 1.4 * w), 'forearm_r': (-10 - 2 * c, 0, 0),
        'fingers_r': (0, 0, -12), 'tips_r': (0, 0, -16), 'thumb_r': (0, 0, -8),
        'arm_l': (-22 - 2.5 * w, 0, -5 - 1.4 * w), 'forearm_l': (-12 + 2 * c, 0, 0),
        'fingers_l': (0, 0, 12), 'tips_l': (0, 0, 16), 'thumb_l': (0, 0, 8),
    }, root_pos=(-0.3 * w, 0, 0))
    p = full(p)
    for side, x in (('r', -1.75), ('l', 1.75)):
        plant_foot(p, side, (x + (0.25 * w if side == 'r' else 0.25 * w), 1.3, -0.4 + (0.6 if side == 'l' else 0.0)), yaw=6)
    return p


def cover_pose(t=0.0, low=0.0):
    w = math.sin(t * 2 * math.pi)
    p = stand_pose(t * 0.5)
    for k, v in {'belly': (9, 0, 0), 'chest': (15, 0, 0.5 * w), 'neck': (20, 0, 0), 'head': (-6, 2.0 * w, 3.0), 'jaw': (10, 0, 0)}.items():
        set_rot(p, k, v)
    cover_face(p, low=low)
    return p


def sit_pose(t=0.0, peek=0.0):
    """Sitting on the ground, knees drawn up, elbows on the knees and the face buried in the hands (peek 1: the
    head comes up and the hands slide down onto the shins)."""
    rock = math.sin(t * 2 * math.pi)
    p = P({
        'pelvis': (-24 + 2.0 * rock, 0, 0),
        'belly': (16 + 2.5 * rock - 6 * peek, 0, 0), 'chest': (12 + 1.5 * rock - 4 * peek, 0, 0),
        'neck': (20 - 4 * peek, 0, 0), 'head': (14 - 36 * peek, 0, 4 * (1 - peek)), 'jaw': (6 + 4 * peek, 0, 0),
    })
    p = full(p)
    p['root']['pos'] = [0.0, -16.9, 0.0]
    for side, x in (('r', -2.2), ('l', 2.2)):
        plant_foot(p, side, (x, 1.3, -7.4), yaw=10, knee_pole=(0.1 if side == 'l' else -0.1, 1.0, -0.6))
    if peek < 0.5:
        cover_face(p, low=0.2, curl=30, pole=(0.12, -0.75, -1.0))
    else:
        for side in ('r', 'l'):
            s = -1 if side == 'r' else 1
            knee = wpoint(p, 'shin_' + side, cl.pivot_of(geo(), 'shin_' + side))
            target = (knee[0] - s * 0.5, knee[1] - 1.6 - p['root']['pos'][1], knee[2] - 3.2)
            hand_on(p, side, 'root', target, (s * 0.1, -1, -0.45), (-s * 0.25, 0, -1), (s * 0.25, -1.0, 0.3), curl=30)
    return p


def upper_only(p):
    """Spine above the hips, arms and head (overlay clips that leave the legs to the base layer)."""
    keep = ('belly', 'chest', 'neck', 'head', 'jaw') + tuple('%s_%s' % (b, s) for s in 'rl' for b in sm.ARM_BONES)
    return {k: v for k, v in full(p).items() if k in keep}


def legs_grounded(p, lift=0.0):
    p = grounded(p)
    p['root']['pos'][1] += lift
    return p


def spine(p, **kw):
    for k, v in kw.items():
        set_rot(p, k, v)
    return p


# ---- walking: slow, unsure shuffle (phase 0..1 = one stride, right heel strike at 0) --------------------------
WALK_SHIN = [(0, 5), (0.12, 12), (0.35, 6), (0.5, 12), (0.64, 36), (0.8, 30), (0.92, 10), (1.0, 5)]
WALK_FOOT = [(0, -10), (0.1, 0), (0.4, 5), (0.55, 20), (0.7, 6), (0.9, -8), (1.0, -10)]


def curve(keys, t):
    t = t % 1.0
    for (t0, a), (t1, b) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0)
            f = f * f * (3 - 2 * f)
            return a + (b - a) * f
    return keys[-1][1]


def walk_pose(t):
    c = math.cos(2 * math.pi * t)
    sn = math.sin(2 * math.pi * t)
    d = {
        'root': (0, 0, 2.2 * math.sin(2 * math.pi * (t - 0.25)) * -1),
        'pelvis': (4, 5 * c, 0), 'belly': (7, -1.5 * c, 0), 'chest': (12, -4 * c, 1.5 * sn),
        'neck': (19, 0, 0), 'head': (-21 + 2.5 * math.cos(4 * math.pi * t), -2 * c, 4 * math.sin(2 * math.pi * t + 0.8)),
        'jaw': (8, 0, 0),
        'arm_r': (-24 + 10 * c, 0, 5), 'forearm_r': (-10 - 7 * math.sin(2 * math.pi * t - 0.7), 0, 0),
        'arm_l': (-24 - 10 * c, 0, -5), 'forearm_l': (-10 + 7 * math.sin(2 * math.pi * t - 0.7), 0, 0),
        'fingers_r': (0, 0, -12), 'tips_r': (0, 0, -16), 'thumb_r': (0, 0, -8),
        'fingers_l': (0, 0, 12), 'tips_l': (0, 0, 16), 'thumb_l': (0, 0, 8),
    }
    for side, ph in (('r', 0.0), ('l', 0.5)):
        tt = (t + ph) % 1.0
        d['thigh_' + side] = (-4 - 17 * math.cos(2 * math.pi * tt), 0, -1.5 if side == 'r' else 1.5)
        d['shin_' + side] = (curve(WALK_SHIN, tt), 0, 0)
        d['foot_' + side] = (curve(WALK_FOOT, tt), 0, 0)
    p = full(P(d, root_pos=(0.35 * math.sin(2 * math.pi * t), 0, 0)))
    return grounded(p)


# ---- run: long strides, torso thrown forward, the long arms flailing, jaw hanging open ------------------------
RUN_THIGH = [(0, -58), (0.25, -18), (0.45, 30), (0.6, 10), (0.8, -45), (1.0, -58)]
RUN_SHIN = [(0, 16), (0.15, 38), (0.38, 22), (0.55, 70), (0.72, 112), (0.88, 55), (1.0, 16)]
RUN_FOOT = [(0, -12), (0.2, 2), (0.42, 28), (0.6, 40), (0.85, -4), (1.0, -12)]


def run_pose(t):
    c = math.cos(2 * math.pi * t)
    sn = math.sin(2 * math.pi * t)
    d = {
        'root': (0, 0, -2.5 * sn),
        'pelvis': (14, 7 * c, 0), 'belly': (12, -2 * c, 0), 'chest': (14, -9 * c, 2 * sn),
        'neck': (6, 0, 0), 'head': (-30 + 4 * math.cos(4 * math.pi * t), 3 * c, -3 * sn),
        'jaw': (50 + 7 * math.sin(4 * math.pi * t + 1.0), 0, 0),
    }
    for side, ph in (('r', 0.0), ('l', 0.5)):
        s = -1 if side == 'r' else 1
        tt = (t + ph) % 1.0
        d['thigh_' + side] = (curve(RUN_THIGH, tt), 0, s * 3)
        d['shin_' + side] = (curve(RUN_SHIN, tt), 0, 0)
        d['foot_' + side] = (curve(RUN_FOOT, tt), 0, 0)
        ca = math.cos(2 * math.pi * tt)
        d['arm_' + side] = (-48 + 58 * ca, s * -6, -s * (20 + 12 * math.sin(2 * math.pi * tt + 0.9)))
        d['forearm_' + side] = (-28 - 26 * (0.5 + 0.5 * math.cos(2 * math.pi * (tt - 0.12))), 0, 0)
        d['hand_' + side] = (-10 + 15 * ca, 0, 0)
        d['fingers_' + side] = (0, 0, s * 22)
        d['tips_' + side] = (0, 0, s * 34)
        d['thumb_' + side] = (0, 0, s * 14)
    p = full(P(d))
    p = grounded(p)
    # flight phases between the contacts
    p['root']['pos'][1] += 0.9 * max(0.0, math.sin(4 * math.pi * (t - 0.25)))
    return p


def rage_pose(t):
    """Waiting crouch of the chase: knees bent, claws out, head up and twitching, panting with the jaw open."""
    w = math.sin(2 * math.pi * t)
    j = math.sin(2 * math.pi * t * 4)
    d = {
        'pelvis': (10, 0, 0), 'belly': (12 + 2 * w, 0, 0), 'chest': (18 + 2 * w, 4 * math.sin(2 * math.pi * t * 2), 0),
        'neck': (10, 0, 0), 'head': (-42 + 3 * w, 9 * math.sin(2 * math.pi * t * 3 + 0.4), 4 * j), 'jaw': (52 + 6 * j, 0, 0),
    }
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        d['arm_' + side] = (-42 + 4 * w, s * 8, -s * 28)
        d['forearm_' + side] = (-48 - 4 * w, 0, 0)
        d['hand_' + side] = (-15, 0, 0)
        d['fingers_' + side] = (0, 0, s * 40)
        d['tips_' + side] = (0, 0, s * 55)
        d['thumb_' + side] = (0, 0, s * 25)
    p = full(P(d))
    p['root']['pos'] = [0.0, -2.0, 0.0]
    for side, x, z in (('r', -2.6, -1.6), ('l', 2.6, 1.0)):
        plant_foot(p, side, (x, 1.3, z), yaw=14)
    return grounded(p)


def leap_pose(t):
    """Airborne: stretched toward the target, arms reaching ahead, legs trailing and kicking."""
    w = math.sin(2 * math.pi * t)
    d = {
        'root': (8, 0, 0),
        'pelvis': (22, 0, 0), 'belly': (8, 0, 0), 'chest': (8, 0, 0), 'neck': (4, 0, 0), 'head': (-42, 0, 0),
        'jaw': (62 + 4 * w, 0, 0),
        'arm_r': (-158 + 8 * w, 0, 14), 'forearm_r': (-14, 0, 0), 'fingers_r': (0, 0, -18), 'tips_r': (0, 0, -30),
        'arm_l': (-150 - 8 * w, 0, -14), 'forearm_l': (-20, 0, 0), 'fingers_l': (0, 0, 18), 'tips_l': (0, 0, 30),
        'thigh_r': (18 + 14 * w, 0, -4), 'shin_r': (75 - 20 * w, 0, 0), 'foot_r': (30, 0, 0),
        'thigh_l': (-8 - 14 * w, 0, 4), 'shin_l': (95 + 20 * w, 0, 0), 'foot_l': (30, 0, 0),
    }
    return full(P(d, root_pos=(0, 4.0, 0)))


def climb_pose(t):
    """Scaling a wall in front: arms alternately reach up and haul, knees pull up, head tipped back."""
    c = math.cos(2 * math.pi * t)
    d = {
        'pelvis': (-6, 0, 0), 'belly': (6, 0, 0), 'chest': (6, 4 * c, 0), 'neck': (8, 0, 0), 'head': (-38, 0, 0),
        'jaw': (40, 0, 0),
    }
    for side, ph in (('r', 0.0), ('l', 0.5)):
        s = -1 if side == 'r' else 1
        k = math.cos(2 * math.pi * (t + ph))
        d['arm_' + side] = (-130 - 40 * k, 0, -s * 12)
        d['forearm_' + side] = (-55 + 40 * k, 0, 0)
        d['hand_' + side] = (-20, 0, 0)
        d['fingers_' + side] = (0, 0, s * 45)
        d['tips_' + side] = (0, 0, s * 55)
        d['thigh_' + side] = (-45 + 30 * k, 0, s * 6)
        d['shin_' + side] = (70 - 35 * k, 0, 0)
        d['foot_' + side] = (-10, 0, 0)
    return full(P(d, root_pos=(0, 2.0, -1.0)))


def smash_keys():
    r = rage_pose(0.0)
    wind = copy(r)
    spine(wind, belly=(2, 0, 0), chest=(-8, 0, 0), head=(-34, 0, 0), jaw=(40, 0, 0))
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        spine(wind, **{'arm_' + side: (-168, 0, -s * 18), 'forearm_' + side: (-45, 0, 0), 'hand_' + side: (10, 0, 0)})
    slam = copy(r)
    spine(slam, belly=(18, 0, 0), chest=(34, 0, 0), head=(4, 0, 0), jaw=(64, 0, 0))
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        spine(slam, **{'arm_' + side: (-62, 0, -s * 6), 'forearm_' + side: (-8, 0, 0), 'hand_' + side: (-25, 0, 0),
                       'fingers_' + side: (0, 0, s * 60), 'tips_' + side: (0, 0, s * 70)})
    follow = copy(slam)
    spine(follow, chest=(28, 0, 0), head=(-6, 0, 0))
    for side in ('r', 'l'):
        set_rot(follow, 'arm_' + side, (-38, 0, -(-1 if side == 'r' else 1) * 10))
    return [(0.0, r), (0.16, wind), (0.3, slam), (0.42, follow), (0.6, r)]


def attack_keys():
    r = rage_pose(0.0)
    reach = copy(r)
    spine(reach, belly=(16, 0, 0), chest=(20, 0, 0), head=(-30, 0, 0), jaw=(68, 0, 0))
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        spine(reach, **{'arm_' + side: (-96, s * 10, -s * 40), 'forearm_' + side: (-12, 0, 0), 'hand_' + side: (0, 0, 0),
                        'fingers_' + side: (0, 0, s * 5), 'tips_' + side: (0, 0, s * 10)})
    grab = copy(reach)
    spine(grab, chest=(26, 0, 0), head=(-20, 0, 0))
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        spine(grab, **{'arm_' + side: (-88, -s * 8, -s * 4), 'forearm_' + side: (-32, 0, 0),
                       'fingers_' + side: (0, 0, s * 72), 'tips_' + side: (0, 0, s * 85)})
    tear = copy(grab)
    spine(tear, chest=(4, 0, 0), belly=(6, 0, 0), head=(-36, 0, 0), jaw=(62, 0, 0))
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        spine(tear, **{'arm_' + side: (-70, s * 12, -s * 58), 'forearm_' + side: (-40, 0, 0)})
    return [(0.0, r), (0.12, reach), (0.24, grab), (0.4, tear), (0.55, r)]


# ---- hysteria ------------------------------------------------------------------------------------------------
def hyst_base(bend=1.0, jaw=45, knees=1.0):
    p = full(P({'pelvis': (8 * bend, 0, 0), 'belly': (14 * bend, 0, 0), 'chest': (20 * bend, 0, 0), 'neck': (10, 0, 0),
                'head': (14 * bend - 6, 0, 0), 'jaw': (jaw, 0, 0)}))
    p['root']['pos'] = [0.0, -1.5 * knees, 0.0]
    for side, x, z in (('r', -2.4, -1.0), ('l', 2.4, 0.8)):
        plant_foot(p, side, (x, 1.3, z), yaw=12)
    return p


def hysteria_keys():
    h0 = hyst_base(1.0, 46)
    claw_face(h0, drag=0.0)
    h1 = hyst_base(-0.35, 64, knees=0.4)
    spine(h1, head=(-38, 0, 0), neck=(0, 0, 0))
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        spine(h1, **{'arm_' + side: (-58, s * 10, -s * 56), 'forearm_' + side: (-48, 0, 0), 'hand_' + side: (-20, 0, 0),
                     'fingers_' + side: (0, 0, s * 55), 'tips_' + side: (0, 0, s * 70), 'thumb_' + side: (0, 0, s * 30)})
    h2 = hyst_base(1.3, 52, knees=1.4)
    spine(h2, head=(24, 0, 0))
    claw_face(h2, drag=0.8)
    h2b = copy(h2)
    plant_foot(h2b, 'r', (-2.4, 4.2, -2.2), pitch=10)       # stamping foot up
    h3 = hyst_base(0.9, 50)
    spine(h3, head=(4, 26, 6))
    claw_face(h3, drag=0.4)
    h4 = hyst_base(0.9, 56)
    spine(h4, head=(6, -26, -6))
    claw_face(h4, drag=0.2)
    plant_foot(h4, 'l', (2.4, 4.0, -1.0), pitch=10)
    h5 = hyst_base(-0.1, 60, knees=0.6)
    spine(h5, head=(-30, 12, 0))
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        hand_on(h5, side, 'head', (s * 1.9, 38.8, 0.2), (-s * 0.6, 0.4, 0.6), (s * 0.2, 1.0, -0.4), (s * 1.0, -0.2, -0.2),
                curl=50, tip_curl=60, thumb=20)
    h6 = hyst_base(1.5, 55, knees=1.6)
    spine(h6, head=(30, -8, 0))
    claw_face(h6, drag=1.1)
    keys = [(0.0, h0), (0.3, h1), (0.55, h2), (0.7, h2b), (0.9, h3), (1.1, h4), (1.35, h5), (1.7, h6), (2.0, h0)]
    return [(t, grounded(p)) for t, p in keys]


def gaze_keys(from_sit):
    """The face was seen: a startled jerk toward the viewer, a frozen trembling stare, then the hands fly up."""
    if from_sit:
        a = sit_pose(0.0)
        b = sit_pose(0.0, peek=1.0)
        spine(b, head=(-30, 0, 0), neck=(18, 0, 0), jaw=(16, 0, 0))
        for side in ('r', 'l'):
            s = -1 if side == 'r' else 1
            set_rot(b, 'fingers_' + side, (0, 0, s * 5))
            set_rot(b, 'tips_' + side, (0, 0, s * 5))
        b2 = add(b, P({'head': (2, 3, 2), 'chest': (1, 0, 0), 'arm_r': (2, 0, 2), 'arm_l': (-2, 0, -2)}))
        b3 = add(b, P({'head': (-2, -3, -2), 'chest': (-1, 0, 0), 'arm_r': (-2, 0, -2), 'arm_l': (2, 0, 2)}))
        c = sit_pose(0.0)
        spine(c, head=(6, 0, 0), jaw=(30, 0, 0))
        cover_face(c, low=0.2, curl=40, pole=(0.12, -0.75, -1.0))
        return [(0.0, a), (0.1, b), (0.3, b2), (0.45, b3), (0.6, b2), (0.75, b3), (1.05, c), (1.3, c)]
    a = stand_pose(0.0)
    b = copy(a)
    spine(b, chest=(4, 0, 0), neck=(12, 0, 0), head=(-36, 0, 0), jaw=(14, 0, 0))
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        spine(b, **{'arm_' + side: (-16, 0, -s * 12), 'forearm_' + side: (-22, 0, 0), 'fingers_' + side: (0, 0, s * 4),
                    'tips_' + side: (0, 0, s * 4)})
    b2 = add(b, P({'head': (2, 3, 2), 'chest': (1, 0, 0), 'forearm_r': (-3, 0, 0), 'forearm_l': (3, 0, 0)}))
    b3 = add(b, P({'head': (-2, -3, -2), 'chest': (-1, 0, 0), 'forearm_r': (3, 0, 0), 'forearm_l': (-3, 0, 0)}))
    up = copy(b)
    spine(up, chest=(10, 0, 0), head=(-20, 6, 0), jaw=(22, 0, 0))
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        spine(up, **{'arm_' + side: (-60, 0, -s * 12), 'forearm_' + side: (-95, 0, 0), 'fingers_' + side: (0, 0, s * 30),
                     'tips_' + side: (0, 0, s * 40)})
    cov = copy(b)
    spine(cov, belly=(10, 0, 0), chest=(16, 0, 0), neck=(16, 0, 0), head=(-2, -8, 0), jaw=(30, 0, 0))
    cover_face(cov, curl=34)
    claw = hyst_base(1.0, 40)
    claw_face(claw, drag=0.0)
    keys = [(0.0, a), (0.08, b), (0.22, b2), (0.34, b3), (0.46, b2), (0.6, up), (0.84, cov), (1.3, claw)]
    return [(t, grounded(p)) for t, p in keys]


def rise_keys():
    """From the floor to the hysteria stance in one violent motion, the hands never leaving the face."""
    a = sit_pose(0.0)
    spine(a, jaw=(34, 0, 0))
    claw_face(a, drag=0.2)
    kneel = full(P({'pelvis': (10, 0, 0), 'belly': (18, 0, 0), 'chest': (16, 0, 0), 'neck': (14, 0, 0), 'head': (6, 0, 0),
                    'jaw': (44, 0, 0)}))
    kneel['root']['pos'] = [0.0, -8.5, 0.0]
    plant_foot(kneel, 'r', (-2.2, 1.3, -5.5), yaw=8)
    plant_foot(kneel, 'l', (2.3, 0.6, 3.8), pitch=55, knee_pole=(0.1, -1.0, -0.4))
    claw_face(kneel, drag=0.4)
    crouch = full(P({'pelvis': (16, 0, 0), 'belly': (18, 0, 0), 'chest': (20, 0, 0), 'neck': (10, 0, 0), 'head': (8, 0, 0),
                     'jaw': (50, 0, 0)}))
    crouch['root']['pos'] = [0.0, -4.5, 0.0]
    for side, x, z in (('r', -2.4, -2.0), ('l', 2.4, 0.4)):
        plant_foot(crouch, side, (x, 1.3, z), yaw=12)
    claw_face(crouch, drag=0.6)
    end = hysteria_keys()[0][1]
    keys = [(0.0, a), (0.45, kneel), (0.9, crouch), (1.5, end)]
    return [(t, grounded(p)) for t, p in keys]


def crouch_pose(hands='knees'):
    p = full(P({'pelvis': (30, 0, 0), 'belly': (18, 0, 0), 'chest': (14, 0, 0), 'neck': (16, 0, 0), 'head': (-24, 0, 0),
                'jaw': (8, 0, 0)}))
    p['root']['pos'] = [0.0, -8.0, 0.0]
    for side, x in (('r', -2.2), ('l', 2.2)):
        plant_foot(p, side, (x, 1.3, -2.6), yaw=10)
    if hands == 'face':
        cover_face(p, low=0.1, curl=30, pole=(0.15, -0.8, -1.0))
    else:
        for side in ('r', 'l'):
            s = -1 if side == 'r' else 1
            hand_on(p, side, 'root', (s * 3.0, 9.0, -5.8), (0, -1, -0.2), (-s * 0.3, 0, -1), (s * 0.4, -0.3, 1.0), curl=25)
    return grounded(p)


def stand_up_keys():
    a = sit_pose(0.0)
    b = sit_pose(0.0, peek=1.0)
    spine(b, head=(-10, 0, 0))
    c = crouch_pose()
    d = stand_pose(0.0)
    return [(0.0, a), (0.45, b), (0.95, c), (1.6, d)]


def sit_down_keys():
    a = stand_pose(0.0)
    c = crouch_pose()
    b = sit_pose(0.0, peek=1.0)
    spine(b, head=(-6, 0, 0))
    d = sit_pose(0.0)
    return [(0.0, a), (0.55, c), (1.1, b), (1.6, d)]


def calm_keys():
    """The targets are gone: the rage drains away, the hands go back to the face, it sinks down and sobs."""
    r = rage_pose(0.0)
    slow = stand_pose(0.0)
    spine(slow, jaw=(24, 0, 0), head=(-14, 0, 0), chest=(16, 0, 0))
    cov = cover_pose(0.0)
    up = mix(slow, cov, 0.55)
    cov2 = cover_pose(0.25, low=0.1)
    crouch = crouch_pose(hands='face')
    sit = sit_pose(0.0)
    keys = [(0.0, r), (0.9, slow), (1.6, up), (2.3, cov), (3.3, cov2), (4.4, crouch), (5.3, sit), (6.0, sit)]
    return [(t, grounded(p)) for t, p in keys]


def sob_keys():
    z = {}
    h1 = P({'belly': (1.5, 0, 0), 'chest': (3.5, 0, 0), 'neck': (2, 0, 0), 'head': (3, 0, 1), 'jaw': (4, 0, 0),
            'arm_r': (0, 0, -1.5), 'arm_l': (0, 0, 1.5), 'forearm_r': (-2, 0, 0), 'forearm_l': (-2, 0, 0)})
    back = P({'chest': (-1, 0, 0), 'head': (-1, 0, 0)})
    h2 = P({'belly': (1, 0, 0), 'chest': (3, 0, 0), 'neck': (1.5, 0, 0), 'head': (2.5, 0, -1), 'jaw': (3, 0, 0),
            'arm_r': (0, 0, -1), 'arm_l': (0, 0, 1)})
    breath = P({'chest': (-1.5, 0, 0), 'head': (-1, 0, 0), 'jaw': (1.5, 0, 0)})
    return [(0.0, z), (0.12, h1), (0.3, back), (0.45, h2), (0.62, z), (1.1, breath), (1.8, z)]


# ---- clip assembly ---------------------------------------------------------------------------------------------
def clip_from(name, length, keys, loop=False, base=True, bones=None):
    c = Clip(name, length, loop=loop)
    for t, p in keys:
        fp = full(p) if base else p
        if bones is not None:
            fp = {k: v for k, v in fp.items() if k in bones}
        c.key(round(t, 4), {k: dict(v) for k, v in fp.items()})
    if base:
        c.keep = set(BONES)
    return c


def wrap180(a):
    return (a + 180.0) % 360.0 - 180.0


def euler_twins(rot):
    """Both Euler triples of one rotation (R = Rz(-rz) Ry(ry) Rx(-rx)), each wrapped to (-180, 180]."""
    a = [wrap180(float(v)) for v in rot]
    b = [wrap180(float(rot[0]) + 180.0), wrap180(180.0 - float(rot[1])), wrap180(float(rot[2]) + 180.0)]
    return a, b


def near(rot, prev):
    """The per-axis 360-degree shift of rot closest to prev."""
    return [v + 360.0 * round((p - v) / 360.0) for v, p in zip(rot, prev)]


def tidy(clip):
    """Rewrites every key rotation without changing the pose: the IK hands back arbitrary Euler triples (an arm at
    (131, -161, -145) is the same as (-49, -19, 35)), and the game interpolates and blends Euler angles per axis, so
    a 360-degree jump between two keys spins the limb through the body. The first key of a bone takes the smaller
    of its two triples (so every clip agrees and crossfades stay short), later keys the triple nearest the key
    before (continuous curves)."""
    prev = {}
    for _, pose in sorted(clip.frames, key=lambda f: f[0]):
        for bone, spec in pose.items():
            if not isinstance(spec, dict) or 'rot' not in spec:
                continue
            a, b = euler_twins(spec['rot'])
            if bone not in prev:
                r = a if sum(v * v for v in a) <= sum(v * v for v in b) else b
            else:
                ca, cb = near(a, prev[bone]), near(b, prev[bone])
                da = sum((x - y) ** 2 for x, y in zip(ca, prev[bone]))
                db = sum((x - y) ** 2 for x, y in zip(cb, prev[bone]))
                r = ca if da <= db else cb
            spec['rot'] = [round(v, 3) for v in r]
            prev[bone] = spec['rot']


def cycle(fn, n, length, name):
    keys = [(length * i / n, fn(i / n)) for i in range(n + 1)]
    return clip_from(name, length, keys, loop=True)


FILES = {
    'animations/scp096/scp096_calm.animation.json': ('sit', 'sit_peek', 'sob', 'stand', 'cover', 'walk', 'stand_up', 'sit_down'),
    'animations/scp096/scp096_rage.animation.json': ('gaze', 'gaze_sit', 'hysteria', 'rise', 'calm'),
    'animations/scp096/scp096_chase.animation.json': ('run', 'rage', 'leap', 'climb', 'smash', 'attack'),
}


def build_clips():
    clips = {}
    clips['sit'] = cycle(lambda t: sit_pose(t), 4, 4.0, 'sit')
    clips['sit_peek'] = cycle(lambda t: add(sit_pose(0.0, peek=1.0), P({'head': (2 * math.sin(2 * math.pi * t), 10 * math.sin(2 * math.pi * t), 0)})),
                              4, 3.0, 'sit_peek')
    sob = Clip('sob', 1.8, loop=True)
    for t, p in sob_keys():
        sob.key(t, {k: dict(v) for k, v in p.items()})
    clips['sob'] = sob
    clips['stand'] = cycle(stand_pose, 8, 4.0, 'stand')
    clips['cover'] = cycle(lambda t: cover_pose(t), 4, 3.0, 'cover')
    clips['walk'] = cycle(walk_pose, 8, 1.0, 'walk')
    clips['stand_up'] = clip_from('stand_up', 1.6, stand_up_keys())
    clips['sit_down'] = clip_from('sit_down', 1.6, sit_down_keys())
    clips['gaze'] = clip_from('gaze', 1.3, gaze_keys(False))
    clips['gaze_sit'] = clip_from('gaze_sit', 1.3, gaze_keys(True))
    clips['hysteria'] = clip_from('hysteria', 2.0, hysteria_keys(), loop=True)
    clips['rise'] = clip_from('rise', 1.5, rise_keys())
    clips['calm'] = clip_from('calm', 6.0, calm_keys())
    clips['run'] = cycle(run_pose, 8, 0.6, 'run')
    clips['rage'] = cycle(rage_pose, 8, 1.6, 'rage')
    clips['leap'] = cycle(leap_pose, 4, 0.8, 'leap')
    clips['climb'] = cycle(climb_pose, 6, 0.9, 'climb')
    upper = set(upper_only({}).keys())
    clips['smash'] = clip_from('smash', 0.6, smash_keys(), bones=upper)
    clips['smash'].keep = upper
    clips['attack'] = clip_from('attack', 0.55, attack_keys(), bones=upper)
    clips['attack'].keep = upper
    for c in clips.values():
        tidy(c)
    return clips


def write_all():
    clips = build_clips()
    sizes = {}
    for rel, names in FILES.items():
        sizes[rel] = write_anims(rel, [clips[n] for n in names])
    return clips, sizes


def show(name, poses, views=('front', 'side', 'q_front'), scale=8):
    os.makedirs(PREVIEW, exist_ok=True)
    tex = os.path.join(ROOT, 'build', 'jinwoo_preview', sm.TEX_REL)
    imgs = []
    for p in poses:
        imgs.append(cl.preview(os.path.join(PREVIEW, '_tmp.png'), [(geo(), p, tex, None)], views=views, scale=scale))
    out = os.path.join(PREVIEW, name + '.png')
    cl.sheet(out, imgs, cols=1)
    return out


if __name__ == '__main__':
    if '--poses' not in sys.argv:
        _, sizes = write_all()
        for rel, n in sizes.items():
            print(rel, n)
    if '--poses' in sys.argv:
        show('stand', [stand_pose(0.0), stand_pose(0.25)])
        show('cover', [cover_pose(0.0)])
        show('sit', [sit_pose(0.0), sit_pose(0.0, peek=1.0)])


def preview_clips(names, samples=5, views=('q_front', 'side'), scale=4, tag='clips'):
    """Sheet: one row per clip, columns = evenly spaced sample times (linear sampling, review only)."""
    from PIL import Image, ImageDraw
    clips = build_clips()
    tex = os.path.join(ROOT, 'build', 'jinwoo_preview', sm.TEX_REL)
    rows = []
    for n in names:
        c = clips[n]
        imgs = []
        for i in range(samples):
            t = c.length * i / (samples - 1 if not c.loop else samples)
            pose = cl.sample_clip(c, t)
            if n in ('smash', 'attack', 'sob'):
                base = full(rage_pose(0.0)) if n != 'sob' else full(sit_pose(0.0))
                if n == 'sob':
                    pose = add(base, pose)
                else:
                    base.update(pose)
                    pose = base
            imgs.append(cl.preview(os.path.join(PREVIEW, '_tmp.png'), [(geo(), pose, tex, None)], views=views, scale=scale))
        w = sum(i.size[0] for i in imgs) + 4 * len(imgs)
        h = max(i.size[1] for i in imgs) + 14
        row = Image.new('RGBA', (w, h), (20, 20, 24, 255))
        x = 0
        for im in imgs:
            row.paste(im, (x, 14))
            x += im.size[0] + 4
        ImageDraw.Draw(row).text((4, 1), n, fill=(255, 255, 255, 255))
        rows.append(row)
    W = max(r.size[0] for r in rows)
    H = sum(r.size[1] for r in rows)
    sheet = Image.new('RGBA', (W, H), (20, 20, 24, 255))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y))
        y += r.size[1]
    out = os.path.join(PREVIEW, tag + '.png')
    sheet.save(out)
    return out
