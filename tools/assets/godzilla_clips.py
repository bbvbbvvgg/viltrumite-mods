"""Godzilla clips (Bedrock degrees, geo px). Shared by every LOD. Own work, CC0.

Layering in GodzillaRenderer: idle (loop) -> walk (loop, sampled by the synced gait phase, weighted by the walk
amount) -> the current move clip (faded in/out) -> procedural tail yaw chain, head look, hips twist, fall roll.
Timings match GodzillaMove: contact poses sit on the server's contact ticks. The walk plants each foot on the
gait phase the server counts a footfall (left 0, right 0.5); claw sweeps put the hand on the server's hit arc
(GodzillaActions.clawHand); legs are solved so planted feet stay on the ground through crouches and lunges.
"""
import copy
import math

import numpy as np

import chitauri_lib as CL
import godzilla_geo as GG
import godzilla_lib as G
from godzilla_lib import Clip, F

SR = 20.0  # ticks per second
LIMBS = ('arm', 'forearm', 'hand', 'leg', 'shin', 'foot', 'toes')
BODY = ['root', 'pelvis', 'hips', 'waist', 'chest', 'neck', 'head', 'jaw'] + ['%s_%s' % (b, s) for b in LIMBS for s in 'lr'] + \
       ['tail_%d' % k for k in range(7)]
ANKLE_L = GG.ANKLE.copy()
GEO = None


def geo():
    global GEO
    if GEO is None:
        m = GG.build(2)
        m.pack()
        GEO = m.geo_json()
    return GEO


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def ease_out(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


def ease_in(t):
    t = max(0.0, min(1.0, t))
    return t ** 3


def P(d=None):
    """New pose dict {bone: {'rot': [..], 'pos': [..]}}."""
    out = {}
    for k, v in (d or {}).items():
        if isinstance(v, dict):
            out[k] = {kk: list(vv) for kk, vv in v.items()}
        else:
            out[k] = {'rot': list(v)}
    return out


def rot(p, bone, r, add=True):
    e = p.setdefault(bone, {}).setdefault('rot', [0.0, 0.0, 0.0])
    for i in range(3):
        e[i] = e[i] + r[i] if add else r[i]


def pos(p, bone, v, add=True):
    e = p.setdefault(bone, {}).setdefault('pos', [0.0, 0.0, 0.0])
    for i in range(3):
        e[i] = e[i] + v[i] if add else v[i]


def mix(a, b, f):
    out = {}
    for bone in set(a) | set(b):
        ea, eb = a.get(bone, {}), b.get(bone, {})
        o = {}
        for ch, zero in (('rot', [0, 0, 0]), ('pos', [0, 0, 0])):
            if ch in ea or ch in eb:
                va, vb = ea.get(ch, zero), eb.get(ch, zero)
                o[ch] = [x + (y - x) * f for x, y in zip(va, vb)]
        out[bone] = o
    return out


def mirror(p):
    out = {}
    for bone, e in p.items():
        nb = bone[:-2] + ('_r' if bone.endswith('_l') else '_l') if bone.endswith(('_l', '_r')) else bone
        o = {}
        if 'rot' in e:
            o['rot'] = [e['rot'][0], -e['rot'][1], -e['rot'][2]]
        if 'pos' in e:
            o['pos'] = [-e['pos'][0], e['pos'][1], e['pos'][2]]
        out[nb] = o
    return out


# ---- inverse kinematics ------------------------------------------------------------------------------------
def leg_ik(p, side, ankle, foot_pitch=0.0, toes=0.0):
    """Solves leg_<side> / shin_<side> so the ankle reaches `ankle` (geo px), then turns the foot so its world
    pitch is foot_pitch (Bedrock rx: + = toes down) and bends the toes."""
    g = geo()
    s = '_' + side
    q, err = CL.solve_chain(g, p, 'leg' + s, 'shin' + s, ankle, (0.0, 0.0, -1.0), end_bone='shin' + s,
                            end_local=CL.pivot_of(g, 'foot' + s), w_pole=0.3, iters=40, elbow_range=(2.0, 150.0))
    p.update(q)
    Rs = CL.bone_matrix(g, p, 'shin' + s)[:3, :3]
    a = math.radians(-foot_pitch)
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    p['foot' + s] = {'rot': list(CL.euler_of(Rs.T @ Rx))}
    p['toes' + s] = {'rot': [toes, 0.0, 0.0]}
    return err


def ankle_rest(side):
    a = ANKLE_L.copy()
    if side == 'r':
        a[0] = -a[0]
    return a


def plant(p, dz_l=0.0, dz_r=0.0, wide=0.0, lift_l=0.0, lift_r=0.0, pitch_l=0.0, pitch_r=0.0, toes_l=0.0, toes_r=0.0):
    for side, dz, lift, pitch, toes in (('l', dz_l, lift_l, pitch_l, toes_l), ('r', dz_r, lift_r, pitch_r, toes_r)):
        a = ankle_rest(side)
        a[0] += wide if side == 'l' else -wide
        a[1] += lift
        a[2] += dz
        leg_ik(p, side, a, pitch, toes)


def hand_to(p, side, target, pole=None):
    g = geo()
    s = '_' + side
    sx = 1.0 if side == 'l' else -1.0
    q, err = CL.solve_chain(g, p, 'arm' + s, 'forearm' + s, target, pole or (sx * 0.8, -0.6, 0.5), iters=50)
    p.update(q)
    return err


def body_point(l, up, f):
    return np.array([l * F, up * F, -f * F])


# ---- base poses --------------------------------------------------------------------------------------------
def stance(crouch=1.5):
    p = P({
        'waist': (5, 0, 0), 'chest': (2, 0, 0), 'neck': (-5, 0, 0), 'head': (5, 0, 0), 'jaw': (0, 0, 0),
        'arm_l': (-16, 0, -9), 'forearm_l': (-30, 0, 3), 'hand_l': (-14, 0, 0),
        'arm_r': (-16, 0, 9), 'forearm_r': (-30, 0, -3), 'hand_r': (-14, 0, 0),
        'tail_0': (3, 0, 0), 'tail_1': (1, 0, 0), 'tail_2': (0, 0, 0),
    })
    pos(p, 'pelvis', (0, -crouch, 0))
    plant(p)
    return p


STANCE = None


def base():
    global STANCE
    if STANCE is None:
        STANCE = stance()
    return copy.deepcopy(STANCE)


def keyed(name, length_ticks, frames, loop=False, full=False, mode='catmullrom'):
    """frames: [(tick, pose)] -> Clip (seconds)."""
    c = Clip(name, length_ticks / SR, loop=loop)
    c.mode = mode
    for t, p in frames:
        c.key(round(t / SR, 4), {b: {k: tuple(v) for k, v in e.items()} for b, e in p.items()})
    if full:
        c.keep = set(BODY)
    return c


# ---- idle and walk -----------------------------------------------------------------------------------------
def idle():
    frames = []
    n = 16
    for i in range(n + 1):
        u = i / n
        w = math.sin(2 * math.pi * u)
        w2 = math.sin(2 * math.pi * u * 2 + 0.6)
        p = base()
        rot(p, 'chest', (-1.4 * w, 0, 0))
        pos(p, 'chest', (0, 0.5 * w, 0))
        rot(p, 'neck', (0.8 * w, 1.5 * math.sin(2 * math.pi * u + 1.1), 0))
        rot(p, 'head', (0.7 * w2, 2.0 * math.sin(2 * math.pi * u + 2.0), 0.8 * w))
        rot(p, 'jaw', (1.2 + 1.2 * max(0.0, w), 0, 0))
        for s, sx in (('l', 1), ('r', -1)):
            rot(p, 'arm_' + s, (1.5 * w, 0, -sx * 1.0 * w))
            rot(p, 'forearm_' + s, (-2.0 * w, 0, 0))
            rot(p, 'hand_' + s, (-3.0 * w2, 0, 0))
        for k in range(7):
            rot(p, 'tail_%d' % k, (0.8 * math.sin(2 * math.pi * u - 0.5 * k), 0, 0))
        frames.append((i * 80 / n, p))
    return keyed('idle', 80, frames, loop=True)


STRIDE = G.FOOT_SWING * F  # 16.3 px each way


def walk(name='walk', length=100, lift=13.0, lean=0.0, arms=1.0):
    frames = []
    n = 24
    for i in range(n + 1):
        ph = i / n
        p = base()
        bob = 1.3 * math.cos(4 * math.pi * (ph - 0.3)) - 1.6
        pos(p, 'pelvis', (0, bob, 0))
        rot(p, 'pelvis', (0, 3.5 * math.cos(2 * math.pi * ph), 2.2 * math.sin(2 * math.pi * ph)))
        # step shock: a short dip of the torso right after each footfall
        shock = math.exp(-((ph % 0.5) / 0.06) ** 2) * 2.2
        rot(p, 'waist', (lean + shock, -2.0 * math.cos(2 * math.pi * ph), -1.6 * math.sin(2 * math.pi * ph)))
        rot(p, 'chest', (0.6 * shock, -1.6 * math.cos(2 * math.pi * ph), -0.6 * math.sin(2 * math.pi * ph)))
        rot(p, 'neck', (-0.8 * shock - 0.5 * bob, 0.6 * math.cos(2 * math.pi * ph), 0.8 * math.sin(2 * math.pi * ph)))
        rot(p, 'head', (-0.6 * shock, 0.4 * math.cos(2 * math.pi * ph), 0.6 * math.sin(2 * math.pi * ph)))
        for side, off in (('l', 0.0), ('r', 0.5)):
            f = (ph + off) % 1.0
            if f < 0.5:
                u = f / 0.5
                dz = -STRIDE + 2 * STRIDE * u
                lift_y = 0.0
                pitch = -6.0 * (1 - smooth(u / 0.12)) + 18.0 * smooth((u - 0.72) / 0.28)
                lift_y += 4.5 * smooth((u - 0.72) / 0.28)
                toes = -14.0 * smooth((u - 0.72) / 0.28)
            else:
                u = (f - 0.5) / 0.5
                dz = STRIDE - 2 * STRIDE * smooth(u)
                lift_y = lift * math.sin(math.pi * u) ** 0.9 + 4.5 * (1 - smooth(u / 0.25))
                pitch = 18.0 * (1 - smooth(u / 0.5)) - 6.0 * smooth((u - 0.6) / 0.4)
                toes = -14.0 * (1 - smooth(u / 0.3))
            a = ankle_rest(side)
            a[1] += lift_y
            a[2] += dz
            leg_ik(p, side, a, pitch, toes)
            # arms swing against the legs (the left arm goes back when the left foot is forward)
            sw = math.cos(2 * math.pi * (ph + off))
            sx = 1 if side == 'l' else -1
            rot(p, 'arm_' + side, (7.0 * arms * sw, 0, -sx * 1.5))
            rot(p, 'forearm_' + side, (-4.0 * arms * max(0.0, -sw), 0, 0))
        for k in range(7):
            rot(p, 'tail_%d' % k, (1.2 * math.cos(4 * math.pi * (ph - 0.3) - 0.6 * k), 0, 0))
        frames.append((i * length / n, p))
    return keyed(name, length, frames, loop=True)


# ---- roar --------------------------------------------------------------------------------------------------
def roar():
    b = base()
    inhale = copy.deepcopy(b)
    rot(inhale, 'neck', (8, 0, 0))
    rot(inhale, 'head', (10, 0, 0))
    rot(inhale, 'chest', (-4, 0, 0))
    pos(inhale, 'chest', (0, 1.0, 1.0))
    for s, sx in (('l', 1), ('r', -1)):
        rot(inhale, 'arm_' + s, (6, 0, sx * 4))
    out = copy.deepcopy(b)
    rot(out, 'waist', (-9, 0, 0))
    rot(out, 'chest', (-10, 0, 0))
    rot(out, 'neck', (-22, 0, 0))
    rot(out, 'head', (-30, 0, 0))
    rot(out, 'jaw', (42, 0, 0))
    for s, sx in (('l', 1), ('r', -1)):
        rot(out, 'arm_' + s, (14, sx * 8, -sx * 30))
        rot(out, 'forearm_' + s, (12, 0, 0))
        rot(out, 'hand_' + s, (20, 0, 0))
    for k in range(7):
        rot(out, 'tail_%d' % k, (5 - 0.6 * k, 0, 0))
    pos(out, 'pelvis', (0, 0.5, 1.5))
    plant(out)
    plant(inhale)
    frames = [(0, b), (14, inhale), (20, inhale)]
    for t in range(24, 73, 4):
        q = copy.deepcopy(out)
        tr = math.sin(t * 1.7) * 1.2
        rot(q, 'head', (tr, 0.8 * math.sin(t * 0.9), 0))
        rot(q, 'jaw', (math.sin(t * 2.3) * 2.0, 0, 0))
        rot(q, 'chest', (0.6 * tr, 0, 0))
        frames.append((t, q))
    frames += [(84, mix(out, b, 0.55)), (100, b)]
    return keyed('roar', 100, frames)


# ---- atomic breath -----------------------------------------------------------------------------------------
def brace():
    p = base()
    pos(p, 'pelvis', (0, -4.5, 1.0))
    rot(p, 'waist', (8, 0, 0))
    for s, sx in (('l', 1), ('r', -1)):
        rot(p, 'arm_' + s, (4, 0, -sx * 14))
        rot(p, 'forearm_' + s, (-8, 0, 0))
        rot(p, 'hand_' + s, (8, 0, 0))
    for k in range(7):
        rot(p, 'tail_%d' % k, (-3.0 if k < 3 else -1.0, 0, 0))
    plant(p, wide=3.0, dz_l=-4.0, dz_r=5.0)
    return p


def fire_pose():
    p = brace()
    rot(p, 'chest', (6, 0, 0))
    rot(p, 'neck', (10, 0, 0))
    rot(p, 'head', (-12, 0, 0))
    rot(p, 'jaw', (36, 0, 0))
    pos(p, 'neck', (0, 0, -2.0))
    return p


def breath_charge():
    b = base()
    br = brace()
    low = copy.deepcopy(br)
    rot(low, 'neck', (8, 0, 0))
    rot(low, 'head', (12, 0, 0))
    rot(low, 'chest', (3, 0, 0))
    back = copy.deepcopy(br)
    rot(back, 'chest', (-9, 0, 0))
    pos(back, 'chest', (0, 1.0, 1.5))
    rot(back, 'neck', (-18, 0, 0))
    rot(back, 'head', (-20, 0, 0))
    rot(back, 'jaw', (10, 0, 0))
    coil = copy.deepcopy(back)
    rot(coil, 'neck', (-8, 0, 0))
    rot(coil, 'head', (-8, 0, 0))
    rot(coil, 'jaw', (14, 0, 0))
    fire = fire_pose()
    frames = [(0, b), (16, br), (22, low)]
    for t in (28, 34, 40, 46):
        q = copy.deepcopy(low)
        rot(q, 'chest', (-1.5 * math.sin(t * 0.8), 0, 0))
        rot(q, 'head', (1.2 * math.sin(t * 1.3), 0, 0))
        frames.append((t, q))
    frames += [(58, mix(low, back, 0.6)), (70, back), (78, back), (90, coil), (96, mix(coil, fire, 0.7)), (100, fire)]
    return keyed('breath_charge', 100, frames)


def breath_fire():
    fp = fire_pose()
    frames = []
    for i in range(9):
        q = copy.deepcopy(fp)
        u = i / 8.0
        rot(q, 'head', (0.9 * math.sin(2 * math.pi * u * 3), 0.5 * math.sin(2 * math.pi * u * 2), 0))
        rot(q, 'chest', (0.6 * math.sin(2 * math.pi * u * 2 + 0.4), 0, 0))
        rot(q, 'jaw', (1.5 * math.sin(2 * math.pi * u * 4), 0, 0))
        pos(q, 'pelvis', (0, 0.3 * math.sin(2 * math.pi * u * 3), 0))
        frames.append((i * 20 / 8.0, q))
    return keyed('breath_fire', 20, frames, loop=True)


def breath_end():
    fp = fire_pose()
    b = base()
    tired = copy.deepcopy(b)
    rot(tired, 'neck', (10, 0, 0))
    rot(tired, 'head', (14, 0, 0))
    rot(tired, 'jaw', (8, 0, 0))
    rot(tired, 'chest', (4, 0, 0))
    return keyed('breath_end', 36, [(0, fp), (10, mix(fp, tired, 0.7)), (16, tired),
                                    (22, mix(tired, P({'head': (4, 8, 0)}), 0.3)), (36, b)])


# ---- melee -------------------------------------------------------------------------------------------------
def claw(side='r'):
    move_side = -1 if side == 'r' else 1
    contact, contact_end = 20, 27
    s = side
    other = 'l' if side == 'r' else 'r'
    b = base()

    def arc_point(t):
        u = (t - contact + 3.0) / (contact_end - contact + 6.0)
        a = math.radians(55.0 - 105.0 * smooth(u))
        uu = max(0.0, min(1.0, (t - contact) / float(contact_end - contact)))
        arc = 0.30
        l = move_side * (G.SHOULDER_L[0] + math.sin(a) * arc * 0.86)
        f = G.SHOULDER_L[2] + math.cos(a) * arc * 0.86
        up = 0.56 - 0.2 * uu
        return body_point(l, up, f)

    def torso(p, twist, lean):
        rot(p, 'waist', (lean * 0.5, twist * 0.4, 0))
        rot(p, 'chest', (lean * 0.5, twist * 0.6, -move_side * abs(twist) * 0.1))
        rot(p, 'neck', (0, -twist * 0.5, 0))
        rot(p, 'head', (0, -twist * 0.3, 0))

    frames = [(0, b)]
    wind = copy.deepcopy(b)
    torso(wind, -move_side * 22.0, -6.0)   # turn the striking shoulder back
    rot(wind, 'jaw', (16, 0, 0))
    rot(wind, 'arm_' + other, (-10, 0, 0))
    plant(wind, dz_l=-3.0 * move_side, dz_r=3.0 * move_side)
    hand_to(wind, s, arc_point(17) + np.array([move_side * 6.0, 10.0, 6.0]))
    frames += [(10, wind), (15, wind)]
    for t in (17, 19, 21, 23, 25, 27, 29, 31):
        q = copy.deepcopy(b)
        k = smooth((t - 17) / 14.0)
        torso(q, -move_side * 22.0 + move_side * 46.0 * k, -6.0 + 16.0 * k)
        rot(q, 'jaw', (16 + 10 * math.sin(math.pi * k), 0, 0))
        rot(q, 'arm_' + other, (-10 + 18 * k, 0, 0))
        plant(q, dz_l=-3.0 * move_side * (1 - 2 * k), dz_r=3.0 * move_side * (1 - 2 * k))
        hand_to(q, s, arc_point(t))
        rot(q, 'hand_' + s, (20 * k, 0, 0))
        frames.append((t, q))
    follow = copy.deepcopy(frames[-1][1])
    frames += [(36, follow), (44, b)]
    return keyed('claw_' + side, 44, frames)


def tail_strike():
    """Side +1 (the tail whips to the left): brace, the body turns into it (the renderer adds the hips twist and
    the whip of the tail chain), arms out for balance, the head watches the strike."""
    b = base()
    br = copy.deepcopy(b)
    pos(br, 'pelvis', (0, -4.0, 0))
    rot(br, 'waist', (10, 0, 0))
    for s, sx in (('l', 1), ('r', -1)):
        rot(br, 'arm_' + s, (-6, 0, -sx * 24))
        rot(br, 'forearm_' + s, (-10, 0, 0))
    plant(br, wide=4.0, dz_l=-6.0, dz_r=6.0)
    wind = copy.deepcopy(br)
    rot(wind, 'chest', (0, 10, 0))
    rot(wind, 'head', (0, -14, 0))
    whip = copy.deepcopy(br)
    rot(whip, 'chest', (4, -12, -4))
    rot(whip, 'neck', (0, 10, 0))
    rot(whip, 'head', (-6, 26, 0))
    rot(whip, 'jaw', (22, 0, 0))
    for k in range(7):
        rot(whip, 'tail_%d' % k, (3.0, 0, 0))
    frames = [(0, b), (10, wind), (24, mix(wind, whip, 0.5)), (32, whip), (46, whip), (58, mix(whip, b, 0.6)), (70, b)]
    return keyed('tail_strike', 70, frames)


def stomp():
    """Left foot (move arg >= 0): weight onto the right leg, the left knee up high, a slam on tick 32 with the
    body dropping into it, then the recovery."""
    b = base()
    lift = copy.deepcopy(b)
    pos(lift, 'pelvis', (-2.0, 0.5, 0))
    rot(lift, 'pelvis', (0, 0, -4))
    rot(lift, 'waist', (-6, 0, 2))
    rot(lift, 'chest', (-4, 0, 0))
    rot(lift, 'head', (-8, 0, 0))
    rot(lift, 'jaw', (18, 0, 0))
    for s, sx in (('l', 1), ('r', -1)):
        rot(lift, 'arm_' + s, (-22, 0, -sx * 22))
        rot(lift, 'forearm_' + s, (-18, 0, 0))
    for k in range(7):
        rot(lift, 'tail_%d' % k, (4.0, 0, 0))
    high = copy.deepcopy(lift)
    plant(lift, lift_l=30.0, dz_l=-8.0, pitch_l=10.0)
    plant(high, lift_l=40.0, dz_l=-10.0, pitch_l=14.0)
    slam = copy.deepcopy(b)
    pos(slam, 'pelvis', (0, -7.0, 0))
    rot(slam, 'waist', (16, 0, 0))
    rot(slam, 'chest', (6, 0, 0))
    rot(slam, 'neck', (8, 0, 0))
    rot(slam, 'head', (6, 0, 0))
    rot(slam, 'jaw', (30, 0, 0))
    for s, sx in (('l', 1), ('r', -1)):
        rot(slam, 'arm_' + s, (10, 0, -sx * 18))
        rot(slam, 'forearm_' + s, (-6, 0, 0))
    for k in range(7):
        rot(slam, 'tail_%d' % k, (-2.0, 0, 0))
    plant(slam, wide=2.0)
    settle = copy.deepcopy(slam)
    pos(settle, 'pelvis', (0, 3.0, 0))
    rot(settle, 'waist', (-6, 0, 0))
    plant(settle, wide=2.0)
    frames = [(0, b), (10, lift), (20, high), (27, high), (32, slam), (36, settle), (44, mix(settle, b, 0.5)), (56, b)]
    return keyed('stomp', 56, frames)


def body_slam():
    b = base()
    wind = copy.deepcopy(b)
    pos(wind, 'pelvis', (0, -3.0, 3.0))
    rot(wind, 'waist', (-6, 14, 0))
    rot(wind, 'chest', (-4, 12, 0))
    rot(wind, 'neck', (0, -12, 0))
    rot(wind, 'head', (0, -10, 0))
    rot(wind, 'jaw', (20, 0, 0))
    rot(wind, 'arm_r', (24, 0, 18))
    rot(wind, 'arm_l', (-30, 0, -10))
    plant(wind, dz_l=-10.0, dz_r=6.0)
    ram = copy.deepcopy(b)
    pos(ram, 'pelvis', (0, -5.0, -4.0))
    rot(ram, 'waist', (24, -16, 0))
    rot(ram, 'chest', (12, -16, -6))
    rot(ram, 'neck', (-14, 14, 0))
    rot(ram, 'head', (-10, 10, 0))
    rot(ram, 'jaw', (34, 0, 0))
    rot(ram, 'arm_r', (-50, 0, 30))
    rot(ram, 'forearm_r', (-40, 0, 0))
    rot(ram, 'arm_l', (14, 0, -26))
    for k in range(7):
        rot(ram, 'tail_%d' % k, (5.0, 0, 0))
    plant(ram, dz_l=12.0, dz_r=-14.0, pitch_l=12.0)
    frames = [(0, b), (16, wind), (24, wind), (34, ram)]
    for t in (37, 40, 43, 46, 50):
        q = copy.deepcopy(ram)
        rot(q, 'chest', (1.6 * math.sin(t * 2.1), 1.2 * math.sin(t * 1.3), 0))
        rot(q, 'head', (1.4 * math.sin(t * 1.7), 0, 0))
        frames.append((t, q))
    frames += [(62, mix(ram, b, 0.55)), (80, b)]
    return keyed('body_slam', 80, frames)


def hurt():
    b = base()
    hit = copy.deepcopy(b)
    pos(hit, 'pelvis', (0, -1.0, 3.0))
    rot(hit, 'waist', (-10, 6, 3))
    rot(hit, 'chest', (-8, 6, 0))
    rot(hit, 'neck', (-14, 10, 0))
    rot(hit, 'head', (-14, 12, 6))
    rot(hit, 'jaw', (34, 0, 0))
    for s, sx in (('l', 1), ('r', -1)):
        rot(hit, 'arm_' + s, (-34, 0, -sx * 16))
        rot(hit, 'forearm_' + s, (-24, 0, 0))
    for k in range(7):
        rot(hit, 'tail_%d' % k, (4.0, 0, 0))
    plant(hit, dz_r=4.0)
    return keyed('hurt', 40, [(0, b), (4, hit), (10, mix(hit, b, 0.25)), (22, mix(hit, b, 0.7)), (40, b)])


# ---- fall, down, rise, death (the renderer rolls the body onto its left side by GodzillaRules.fallTilt) -----
def lying(limp=0.0):
    p = base()
    pos(p, 'pelvis', (0, -2.0, 0))
    rot(p, 'waist', (6, 0, -4))
    rot(p, 'chest', (4, 0, -6))
    rot(p, 'neck', (-6, 0, -16))
    rot(p, 'head', (4, 0, -14))
    rot(p, 'jaw', (8 + 10 * limp, 0, 0))
    rot(p, 'arm_l', (-58, 0, -40))
    rot(p, 'forearm_l', (-30, 0, 0))
    rot(p, 'hand_l', (10, 0, 0))
    rot(p, 'arm_r', (-30 + 20 * limp, 0, 22))
    rot(p, 'forearm_r', (-40 + 20 * limp, 0, 0))
    rot(p, 'leg_l', (-26, 0, -6))
    rot(p, 'shin_l', (34, 0, 0))
    rot(p, 'foot_l', (-6, 0, 0))
    rot(p, 'leg_r', (-38 + 10 * limp, 0, 10))
    rot(p, 'shin_r', (46, 0, 0))
    rot(p, 'foot_r', (-10, 0, 0))
    for k in range(7):
        rot(p, 'tail_%d' % k, (2.0, 0, -3.0 - 2.0 * k))
    for bone in ('leg_l', 'shin_l', 'foot_l', 'leg_r', 'shin_r', 'foot_r', 'toes_l', 'toes_r'):
        e = p[bone]['rot']
        if bone.startswith('leg'):
            pass
    return p


def fall():
    b = base()
    stagger = copy.deepcopy(b)
    pos(stagger, 'pelvis', (0, -3.0, 2.0))
    rot(stagger, 'waist', (-8, -8, 6))
    rot(stagger, 'chest', (-6, -6, 4))
    rot(stagger, 'neck', (-18, -10, 0))
    rot(stagger, 'head', (-16, -14, 0))
    rot(stagger, 'jaw', (40, 0, 0))
    rot(stagger, 'arm_l', (-40, 0, -46))
    rot(stagger, 'arm_r', (-70, 0, 30))
    rot(stagger, 'forearm_r', (-30, 0, 0))
    plant(stagger, dz_l=-8.0, dz_r=8.0, wide=4.0)
    tip = mix(stagger, lying(), 0.6)
    down = lying()
    impact = copy.deepcopy(down)
    rot(impact, 'head', (8, 0, 6))
    rot(impact, 'jaw', (10, 0, 0))
    rot(impact, 'arm_r', (10, 0, 0))
    frames = [(0, b), (8, stagger), (20, tip), (34, impact), (40, mix(impact, down, 0.5)), (50, down)]
    return keyed('fall', 50, frames, full=True)


def down():
    d = lying()
    frames = []
    for i in range(9):
        u = i / 8.0
        q = copy.deepcopy(d)
        w = math.sin(2 * math.pi * u)
        rot(q, 'chest', (-2.0 * w, 0, 0))
        pos(q, 'chest', (0, 0.8 * w, 0))
        rot(q, 'head', (2.0 * math.sin(2 * math.pi * u + 1.0), 0, 0))
        rot(q, 'jaw', (6 + 6 * max(0.0, w), 0, 0))
        rot(q, 'arm_r', (4 * math.sin(2 * math.pi * u + 2.0), 0, 0))
        for k in range(7):
            rot(q, 'tail_%d' % k, (0, 2.0 * math.sin(2 * math.pi * u - 0.6 * k), 0))
        frames.append((i * 70 / 8.0, q))
    return keyed('down', 70, frames, loop=True, full=True)


def rise():
    d = lying()
    b = base()
    push = copy.deepcopy(d)
    rot(push, 'arm_l', (20, 0, 30))
    rot(push, 'forearm_l', (16, 0, 0))
    rot(push, 'neck', (-14, 0, 10))
    rot(push, 'head', (-12, 0, 10))
    rot(push, 'leg_r', (20, 0, -6))
    kneel = mix(push, b, 0.5)
    rot(kneel, 'waist', (12, 0, 0))
    rot(kneel, 'arm_l', (-10, 0, -10))
    up = copy.deepcopy(b)
    rot(up, 'neck', (-18, 0, 0))
    rot(up, 'head', (-24, 0, 0))
    rot(up, 'jaw', (36, 0, 0))
    for s, sx in (('l', 1), ('r', -1)):
        rot(up, 'arm_' + s, (10, 0, -sx * 24))
    shake = copy.deepcopy(b)
    rot(shake, 'head', (0, 12, 6))
    rot(shake, 'neck', (0, 6, 0))
    frames = [(0, d), (12, push), (30, mix(push, kneel, 0.7)), (40, kneel), (46, up), (58, up), (66, shake),
              (72, mix(shake, b, 0.5)), (80, b)]
    return keyed('rise', 80, frames, full=True)


def death():
    b = base()
    reel = copy.deepcopy(b)
    pos(reel, 'pelvis', (0, -2.0, 3.0))
    rot(reel, 'waist', (-10, 0, 4))
    rot(reel, 'chest', (-8, 0, 0))
    rot(reel, 'neck', (-22, 0, 0))
    rot(reel, 'head', (-26, 0, 4))
    rot(reel, 'jaw', (38, 0, 0))
    for s, sx in (('l', 1), ('r', -1)):
        rot(reel, 'arm_' + s, (-20, 0, -sx * 26))
        rot(reel, 'forearm_' + s, (-16, 0, 0))
    plant(reel, wide=3.0, dz_r=6.0)
    sag = copy.deepcopy(reel)
    rot(sag, 'neck', (26, 0, 0))
    rot(sag, 'head', (18, 0, 0))
    rot(sag, 'jaw', (-16, 0, 0))
    rot(sag, 'arm_l', (14, 0, 10))
    rot(sag, 'arm_r', (14, 0, -10))
    pos(sag, 'pelvis', (0, -6.0, 0))
    plant(sag, wide=3.0)
    limp = lying(1.0)
    impact = copy.deepcopy(limp)
    rot(impact, 'head', (8, 0, 8))
    last = copy.deepcopy(limp)
    rot(last, 'jaw', (6, 0, 0))
    rot(last, 'head', (4, 0, -4))
    frames = [(0, b), (8, reel), (16, reel), (24, sag), (40, mix(sag, limp, 0.5)), (62, impact), (70, limp), (100, last),
              (120, limp), (160, limp)]
    return keyed('death', 160, frames, full=True)


def all_clips():
    clips = [idle(), walk(), roar(), breath_charge(), breath_fire(), breath_end(), claw('r'), claw('l'), tail_strike(),
             stomp(), body_slam(), hurt(), fall(), down(), rise(), death()]
    mirrored = []
    for name in ('tail_strike', 'stomp'):
        src = next(c for c in clips if c.name == name)
        m = Clip(name + '_m', src.length, loop=src.loop)
        m.mode = getattr(src, 'mode', 'catmullrom')
        m.keep = set(src.keep)
        for t, p in src.frames:
            m.key(t, mirror(P(p)))
        mirrored.append(m)
    return clips + mirrored
