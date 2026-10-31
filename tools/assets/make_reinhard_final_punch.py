#!/usr/bin/env python3
"""Reinhard: THE FINAL PUNCH body clip (one 24 s take, FinalPunchRules timeline).

Writes
  viltrumitecore/src/main/resources/assets/viltrumitecore/animations/reinhard/final_punch.animation.json
  the fallback fist table of client/reinhard/finalpunch/FinalPunchFist.java (between its markers)
and prints the golem aim correction used by client/reinhard/FinalPunchPose.java.

The choreography is a list of pose keys (ticks). Between keys every channel follows an
ease-in-out curve; planted feet are solved with a two-bone leg IK on every sample (the sole
stays on its spot while the pelvis travels), the contact arm is solved onto the jaw point.
The JSON stores dense linear samples, so the game plays exactly what this script checks.
Forward kinematics replicate ReinhardRenderer.boneFrame (BakedGeoModel mirrors native X;
rotX = -kx, rotY = -ky, rotZ = kz; position translate(-px, py, pz) / 16), PLAYER_SCALE 0.9375.
FinalPunchPoseTest re-checks the result with the Java rig.

python3 tools/assets/make_reinhard_final_punch.py [--check]
"""
import json, math, re, sys
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'viltrumitecore/src/main/resources/assets/viltrumitecore'
GEO = RES / 'geo/reinhard/reinhard.geo.json'
OUT = RES / 'animations/reinhard/final_punch.animation.json'
FIST = ROOT / 'viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/client/reinhard/finalpunch/FinalPunchFist.java'
SCALE = 0.9375
LENGTH = 480
# FinalPunchRules
LEG_LIFT, LEG_UP, WINDUP_MAX, SILENCE, STRIKE, CONTACT, LAUNCH, LOWER = 150, 205, 330, 398, 412, 444, 450, 462
# Contact targets: the fist centre (right_hand pivot) on the jaw point, blocks (forward, up, right of the feet).
HUMAN_FIST = (0.87, 1.44, 0.13)
GOLEM_FIST = (0.87, 2.10, 0.13)
GOLEM_LIFT_PX = 8.5          # the jump into a golem's jaw (pelvis, body and legs)
GOLEM_HEAD_DEG = 14.0

# ------------------------------------------------------------------ rig
geo = json.loads(GEO.read_text())['minecraft:geometry'][0]
BONES = {}
for b in geo['bones']:
    p = b.get('pivot', [0, 0, 0])
    BONES[b['name']] = dict(parent=b.get('parent'), pivot=np.array([-p[0], p[1], p[2]], float))


def T(x, y, z):
    m = np.eye(4); m[:3, 3] = (x, y, z); return m


def Rx(a):
    c, s = math.cos(a), math.sin(a); m = np.eye(4); m[1, 1] = c; m[1, 2] = -s; m[2, 1] = s; m[2, 2] = c; return m


def Ry(a):
    c, s = math.cos(a), math.sin(a); m = np.eye(4); m[0, 0] = c; m[0, 2] = s; m[2, 0] = -s; m[2, 2] = c; return m


def Rz(a):
    c, s = math.cos(a), math.sin(a); m = np.eye(4); m[0, 0] = c; m[0, 1] = -s; m[1, 0] = s; m[1, 1] = c; return m


def frame(name, pose):
    b = BONES[name]
    m = np.eye(4)
    if b['parent']:
        m = frame(b['parent'], pose) @ T(*(-BONES[b['parent']]['pivot'] / 16))
    kr = pose.get(name, (0, 0, 0))
    kp = pose.get(name + '@', (0, 0, 0))
    m = m @ T(-kp[0] / 16, kp[1] / 16, kp[2] / 16) @ T(*(b['pivot'] / 16))
    return m @ Rz(math.radians(kr[2])) @ Ry(math.radians(-kr[1])) @ Rx(math.radians(-kr[0]))


def point(name, pose, p=None):
    """Baked model point (px) of bone `name` (default: its pivot), in baked blocks."""
    m = frame(name, pose)
    if p is None:
        return m[:3, 3].copy()
    v = m @ T(*(-BONES[name]['pivot'] / 16)) @ np.array([p[0] / 16, p[1] / 16, p[2] / 16, 1.0])
    return v[:3]


def world(v):
    """Baked blocks -> (forward, up, right) blocks of the feet frame."""
    return np.array([-v[2] * SCALE, v[1] * SCALE, v[0] * SCALE])


def fist(pose):
    return world(point('right_hand', pose))


# Sole of each foot (baked px, shin space): centre, toe, heel.
SOLE = {'right': [(2.02, 0.0, -0.45), (2.02, 0.0, -2.69), (2.02, 0.0, 1.79)],
        'left': [(-2.02, 0.0, -0.45), (-2.02, 0.0, -2.69), (-2.02, 0.0, 1.79)]}


def sole(side, pose):
    return [point(side + '_shin', pose, p) * 16 for p in SOLE[side]]


# ------------------------------------------------------------------ small solvers
def nelder_mead(f, x0, step, iters=600, tol=1e-9):
    n = len(x0)
    pts = [np.array(x0, float)]
    for i in range(n):
        x = np.array(x0, float); x[i] += step[i]; pts.append(x)
    vals = [f(p) for p in pts]
    for _ in range(iters):
        order = np.argsort(vals); pts = [pts[i] for i in order]; vals = [vals[i] for i in order]
        if abs(vals[-1] - vals[0]) < tol:
            break
        c = sum(pts[:-1]) / n
        xr = c + (c - pts[-1]); fr = f(xr)
        if fr < vals[0]:
            xe = c + 2 * (c - pts[-1]); fe = f(xe)
            pts[-1], vals[-1] = (xe, fe) if fe < fr else (xr, fr)
        elif fr < vals[-2]:
            pts[-1], vals[-1] = xr, fr
        else:
            xc = c + 0.5 * (pts[-1] - c); fc = f(xc)
            if fc < vals[-1]:
                pts[-1], vals[-1] = xc, fc
            else:
                pts = [pts[0] + 0.5 * (p - pts[0]) for p in pts]; vals = [f(p) for p in pts]
    i = int(np.argmin(vals)); return pts[i], vals[i]


def solve_leg(side, pose, target, guess=None, where=''):
    """Hip flexion and knee fold (kx) that put the lowest sole point of `side` on `target`
    (baked px: x ignored, y = ground height, z = sole centre). Knee forward only."""
    def err(x):
        q = dict(pose); q[side + '_leg'] = (x[0], 0, pose.get(side + '_leg', (0, 0, 0))[2]); q[side + '_shin'] = (x[1], 0, 0)
        pts = sole(side, q)
        low = min(p[1] for p in pts)
        e = (pts[0][2] - target[2]) ** 2 + (low - target[1]) ** 2
        return e + (max(0.0, -x[1]) * 0.5) ** 2 + 1e-6 * (x[0] ** 2 + x[1] ** 2)
    if guess is None:
        cands = [(err((a, b)), a, b) for a in range(-100, 61, 4) for b in range(0, 151, 4)]
        _, a, b = min(cands)
        guess, step = [a, b], [3, 3]
    else:
        step = [2, 2]
    best, v = nelder_mead(err, guess, step, iters=800, tol=1e-12)
    if v > 0.01:
        cands = [(err((a, b)), a, b) for a in range(-100, 61, 2) for b in range(0, 151, 2)]
        _, a, b = min(cands)
        best, v = nelder_mead(err, [a, b], [1.5, 1.5], iters=800, tol=1e-12)
    if best[1] < -0.5 or v > 0.05:
        raise SystemExit(f'{side} leg IK failed {where}: {best} err {v:.3f} target {target}')
    return best


def solve_arm(pose, target, guess):
    """Right arm (kx, ky, kz) and forearm kx placing the fist on `target` (forward, up, right):
    the arm nearly straight, the elbow a little out, the wrist in line."""
    def err(x):
        q = dict(pose); q['right_arm'] = (x[0], x[1], x[2]); q['right_forearm'] = (x[3], 0, 0)
        d = fist(q) - np.array(target)
        return (d @ d) * 1e4 + 0.002 * (x[3] + 9) ** 2 + 0.0005 * (x[1] ** 2 + (x[2] - 4) ** 2)
    best, v = nelder_mead(err, guess, [8, 8, 8, 8], iters=3000)
    return best


# ------------------------------------------------------------------ choreography
UPPER = ['head', 'body', 'right_arm', 'left_arm', 'right_forearm', 'left_forearm', 'right_hand', 'left_hand']
IDLE = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (0, 0, 3), 'left_arm': (0, 0, -3),
        'right_forearm': (-7, 0, 0), 'left_forearm': (-7, 0, 0), 'right_hand': (0, 0, 0), 'left_hand': (0, 0, 0),
        'pelvis': (0, 0, 0)}
REST_R = sole('right', {})[0]
REST_L = sole('left', {})[0]


def foot(rest, right=0.0, fwd=0.0, up=0.0):
    """Planted sole target (baked px) moved from its rest spot."""
    return (rest[0] + right, up, rest[2] - fwd)


# Each key: tick -> overrides (inherits the previous key). 'pelvis' = (right, up, forward) px.
# Legs: 'rfoot'/'lfoot' = planted target (IK) or 'right_leg'/'right_shin' angles (free leg).
# 'ease' = easing of the segment that STARTS at this key: sine (in-out), in, out, lin.
# 'lift' = a stepping foot's arc height (px) over the segment starting here.
KEYS = [
    (0, dict(IDLE, rfoot=foot(REST_R), lfoot=foot(REST_L))),
    # "Forgive me." — the head bows; nothing else moves yet.
    (10, dict(head=(15, 0, 0), body=(2, 0, 0), ease='sine')),
    (40, dict(head=(14, -3, 0), body=(2, 1, 0), ease='sine')),
    # The eyes come up to the target; the weight slides onto the right leg, the right arm starts back.
    (58, dict(head=(-3, -7, 0), body=(-2, 8, 0), right_arm=(12, 0, 10), right_forearm=(-32, 0, 0),
              left_arm=(-20, 0, -6), left_forearm=(-20, 0, 0), pelvis=(0.8, -0.2, 0), ease='sine')),
    (100, dict(head=(-4, -18, 1), body=(-4, 19, -1), right_arm=(22, 2, 22), right_forearm=(-72, 0, 0), right_hand=(-8, 0, 0),
               left_arm=(-48, 2, -8), left_forearm=(-24, 0, 0), pelvis=(1.3, -0.4, -0.1), ease='sine')),
    # LEG_LIFT: the left foot leaves the ground.
    (LEG_LIFT, dict(head=(-5, -27, 2), body=(-6, 30, -2), right_arm=(28, 4, 34), right_forearm=(-106, 0, 0), right_hand=(-14, 0, 0),
                    left_arm=(-66, 4, -10), left_forearm=(-22, 0, 0), pelvis=(1.7, -0.6, -0.2), ease='sine')),
    (LEG_UP, dict(head=(-6, -37, 3), body=(-12, 42, -3), right_arm=(34, 6, 42), right_forearm=(-122, 0, 0), right_hand=(-18, 0, 0),
                  left_arm=(-78, 6, -14), left_forearm=(-14, 0, 0), pelvis=(1.9, -1.2, -0.4),
                  lfoot=None, left_leg=(-72, 0, -4), left_shin=(80, 0, 0), ease='sine')),
    (270, dict(head=(-7, -43, 3), body=(-18, 50, -4), right_arm=(38, 8, 47), right_forearm=(-130, 0, 0), right_hand=(-20, 0, 0),
               left_arm=(-83, 6, -17), left_forearm=(-10, 0, 0), pelvis=(2.0, -1.6, -0.6),
               left_leg=(-82, 0, -5), left_shin=(90, 0, 0), ease='sine')),
    # WINDUP_MAX: the impossible coil; it trembles until the silence.
    (WINDUP_MAX, dict(head=(-8, -47, 4), body=(-22, 55, -5), right_arm=(40, 9, 50), right_forearm=(-134, 0, 0), right_hand=(-22, 0, 0),
                      left_arm=(-86, 6, -19), left_forearm=(-8, 0, 0), pelvis=(2.1, -1.9, -0.7),
                      left_leg=(-88, 0, -5), left_shin=(95, 0, 0), ease='sine')),
    (SILENCE, dict(body=(-23, 56, -5), ease='lin')),
    # The silence: absolutely still, then the slow uncoil.
    (STRIKE, dict(ease='in')),
    (424, dict(head=(-5, -30, 2), body=(-12, 32, -3), right_arm=(-8, 8, 26), right_forearm=(-124, 0, 0), right_hand=(-12, 0, 0),
               left_arm=(-60, 0, -14), left_forearm=(-40, 0, 0), pelvis=(1.6, -1.9, 0.6),
               left_leg=(-58, 0, -4), left_shin=(46, 0, 0), ease='sine')),
    (436, dict(head=(-1, 2, 1), body=(2, -8, -1), right_arm=(-58, 6, 10), right_forearm=(-62, 0, 0), right_hand=(-4, 0, 0),
               left_arm=(-8, -4, -14), left_forearm=(-96, 0, 0), pelvis=(1.0, -1.8, 2.2),
               lfoot=foot(REST_L, right=-0.8, fwd=6.0), left_leg=None, left_shin=None, ease='out')),
    # CONTACT: the fist on the jaw (arm solved below); the hit-stop holds it exactly.
    (CONTACT, dict(head=(1, 22, 0), body=(10, -24, 0), right_arm='SOLVE', right_hand=(0, 0, 0),
                   left_arm=(26, -6, -12), left_forearm=(-110, 0, 0), pelvis=(0.6, -1.7, 3.2),
                   lfoot=foot(REST_L, right=-0.8, fwd=6.5), ease='lin')),
    (LAUNCH, dict(ease='out')),
    # Follow-through, then everything settles back to the stance.
    (LOWER, dict(head=(0, 26, 0), body=(14, -30, 0), right_arm='FOLLOW', left_arm=(30, -6, -14), pelvis=(0.4, -1.6, 3.6), ease='sine', lift=2.2)),
    (LENGTH, dict(IDLE, rfoot=foot(REST_R), lfoot=foot(REST_L))),
]

EASE = {
    'sine': lambda w: 0.5 - 0.5 * math.cos(math.pi * w),
    'in': lambda w: w * w * w,
    'out': lambda w: 1 - (1 - w) ** 3,
    'lin': lambda w: w,
}


def resolve_keys():
    """Fill inheritance, solve the contact arm and the planted legs at every key."""
    keys = []
    state = {}
    for tick, over in KEYS:
        state = dict(state)
        for k, v in over.items():
            state[k] = v
        state.pop('lift', None) if 'lift' not in over else None
        if 'lift' not in over:
            state['lift'] = 0.0
        if 'ease' not in over:
            state['ease'] = 'sine'
        keys.append([tick, state])
    # contact arm
    for tick, st in keys:
        if st.get('right_arm') == 'SOLVE':
            pose = pose_of(st)
            x = solve_arm(pose, HUMAN_FIST, [-80, 8, 6, -10])
            st['right_arm'] = (x[0], x[1], x[2]); st['right_forearm'] = (x[3], 0, 0)
    contact = next(st for t, st in keys if t == CONTACT)
    for tick, st in keys:
        if st.get('right_arm') == 'SOLVE':
            st['right_arm'] = contact['right_arm']; st['right_forearm'] = contact['right_forearm']
        if st.get('right_arm') == 'FOLLOW':
            a = contact['right_arm']
            st['right_arm'] = (a[0] - 7, a[1] - 3, a[2]); st['right_forearm'] = (contact['right_forearm'][0] * 0.4, 0, 0)
    # keys after CONTACT that inherited the solved arm through 'SOLVE' copies
    for tick, st in keys:
        if isinstance(st.get('right_arm'), str):
            raise SystemExit(f'unresolved arm at {tick}')
    return keys


def pose_of(st, legs=None):
    pose = {}
    for b in UPPER:
        v = st.get(b)
        if isinstance(v, tuple):
            pose[b] = v
    px = st.get('pelvis', (0, 0, 0))
    # pelvis (right, up, forward) px -> position channel (native px: translate(-x, y, z))
    pos = (-px[0], px[1], -px[2])
    for b in ('body', 'right_leg', 'left_leg'):
        pose[b + '@'] = pos
    if legs:
        pose.update(legs)
    return pose


def legs_at(st, prev=None):
    """Leg angles for a resolved state: IK for planted feet, given angles for a free leg."""
    out = {}
    base = pose_of(st)
    for side, key in (('right', 'rfoot'), ('left', 'lfoot')):
        if st.get(key) is not None:
            x = solve_leg(side, base, st[key], None, f'key {st.get("_tick")}')
            out[side + '_leg'] = (x[0], 0, 0); out[side + '_shin'] = (x[1], 0, 0)
        else:
            out[side + '_leg'] = st[side + '_leg']; out[side + '_shin'] = st[side + '_shin']
    return out


def lerp(a, b, w):
    return tuple(a[i] + (b[i] - a[i]) * w for i in range(3))


def tremble(tick):
    """Deterministic shake of the coil (WINDUP_MAX..SILENCE), growing; 0 elsewhere."""
    if tick < WINDUP_MAX or tick >= SILENCE:
        return 0.0, 0.0, 0.0
    g = 0.5 + 1.1 * (tick - WINDUP_MAX) / (SILENCE - WINDUP_MAX)
    a = math.sin(tick * 1.91) * 0.6 + math.sin(tick * 3.37 + 1.3) * 0.4
    b = math.sin(tick * 2.53 + 0.4) * 0.6 + math.sin(tick * 4.11 + 2.1) * 0.4
    c = math.sin(tick * 1.37 + 2.9) * 0.5 + math.sin(tick * 3.83) * 0.5
    return a * g, b * g, c * g


def sample(keys, tick):
    """Full pose (all channels) at an integer or fractional tick."""
    for i in range(len(keys) - 1):
        t0, a = keys[i]; t1, b = keys[i + 1]
        if t0 <= tick <= t1:
            break
    w = 0.0 if t1 == t0 else (tick - t0) / (t1 - t0)
    e = EASE[a['ease']](w)
    st = {}
    for k in UPPER:
        st[k] = lerp(a[k], b[k], e)
    st['pelvis'] = lerp(a['pelvis'], b['pelvis'], e)
    legs = {}
    base = pose_of(st)
    la, lb = a['_legs'], b['_legs']
    for side, key in (('right', 'rfoot'), ('left', 'lfoot')):
        if a.get(key) is not None and b.get(key) is not None:
            tgt = list(lerp(a[key], b[key], e))
            if a.get('lift') and a[key] != b[key]:
                tgt[1] += a['lift'] * math.sin(math.pi * w)
            guess = [la[side + '_leg'][0] + (lb[side + '_leg'][0] - la[side + '_leg'][0]) * e,
                     la[side + '_shin'][0] + (lb[side + '_shin'][0] - la[side + '_shin'][0]) * e]
            x = solve_leg(side, base, tgt, guess, f'tick {tick}')
            legs[side + '_leg'] = (x[0], 0, 0); legs[side + '_shin'] = (x[1], 0, 0)
        else:
            legs[side + '_leg'] = lerp(la[side + '_leg'], lb[side + '_leg'], e)
            legs[side + '_shin'] = lerp(la[side + '_shin'], lb[side + '_shin'], e)
    st.update(legs)
    tr = tremble(tick)
    if any(tr):
        st['body'] = (st['body'][0] + tr[0] * 0.5, st['body'][1], st['body'][2] + tr[1] * 0.6)
        st['head'] = (st['head'][0] + tr[2] * 0.5, st['head'][1], st['head'][2])
        st['right_arm'] = (st['right_arm'][0] + tr[1], st['right_arm'][1] + tr[0], st['right_arm'][2])
        st['right_forearm'] = (st['right_forearm'][0] + tr[2] * 1.2, 0, 0)
        st['right_hand'] = (st['right_hand'][0] + tr[0] * 1.5, 0, st['right_hand'][2] + tr[1])
        st['left_arm'] = (st['left_arm'][0] + tr[2] * 0.6, st['left_arm'][1], st['left_arm'][2] + tr[0] * 0.5)
    return st


def full_pose(st):
    pose = pose_of(st)
    for k in ('right_leg', 'left_leg', 'right_shin', 'left_shin'):
        pose[k] = st[k]
    return pose


# ------------------------------------------------------------------ sampling plan
def ticks_for(bone):
    """Sample ticks of one bone: every key, every 4 ticks (2 in the strike and the tremble for the
    trembling bones), so linear samples follow the eased curves."""
    s = set(t for t, _ in KEYS)
    s.update(range(0, LENGTH + 1, 4))
    s.update(range(STRIKE, LOWER + 1, 2))
    if bone in ('body', 'head', 'right_arm', 'right_forearm', 'right_hand', 'left_arm'):
        s.update(range(WINDUP_MAX, SILENCE, 2))
    return sorted(s)


CHANNELS = {
    'head': ['rot'], 'body': ['rot', 'pos'], 'right_arm': ['rot'], 'left_arm': ['rot'], 'right_forearm': ['rot'],
    'left_forearm': ['rot'], 'right_hand': ['rot'], 'left_hand': ['rot'], 'right_leg': ['rot', 'pos'],
    'left_leg': ['rot', 'pos'], 'right_shin': ['rot'], 'left_shin': ['rot'],
}


def fmt(v):
    r = round(v, 2)
    return 0 if r == 0 else (int(r) if r == int(r) else r)


def build():
    keys = resolve_keys()
    prev = None
    for tick, st in keys:
        st['_tick'] = tick
        st['_legs'] = legs_at(st, prev)
        prev = {s: [st['_legs'][s + '_leg'][0], st['_legs'][s + '_shin'][0]] for s in ('right', 'left')}
    cache = {}

    def at(t):
        if t not in cache:
            cache[t] = sample(keys, t)
        return cache[t]

    bones = {}
    for bone, chans in CHANNELS.items():
        entry = {}
        ts = ticks_for(bone)
        if 'rot' in chans:
            entry['rotation'] = {f'{t / 20:g}': [fmt(x) for x in at(t)[bone]] for t in ts}
        if 'pos' in chans:
            def pos(t):
                p = at(t)['pelvis']
                return [fmt(-p[0]), fmt(p[1]), fmt(-p[2])]
            entry['position'] = {f'{t / 20:g}': pos(t) for t in ts}
        bones[bone] = entry
    clip = {'format_version': '1.8.0', 'animations': {'final_punch': {
        'animation_length': LENGTH / 20, 'loop': 'hold_on_last_frame', 'bones': bones}}}
    return keys, at, clip


def report(keys, at):
    errors = []
    c = fist(full_pose(at(CONTACT)))
    print('contact fist (fwd, up, right):', np.round(c, 3), 'target', HUMAN_FIST)
    if np.linalg.norm(c - np.array(HUMAN_FIST)) > 0.03:
        errors.append(f'contact fist off by {np.linalg.norm(c - np.array(HUMAN_FIST)):.3f}')
    contact = next(st for t, st in keys if t == CONTACT)
    print('contact arm', np.round(contact['right_arm'], 1), 'forearm', np.round(contact['right_forearm'], 1))
    # planted feet stay on the ground, free feet stay above it
    every = sorted(set().union(*[ticks_for(b) for b in CHANNELS]))
    for t in every:
        pose = full_pose(at(t))
        for side in ('right', 'left'):
            low = min(p[1] for p in sole(side, pose))
            if low < -0.35:
                errors.append(f'{side} foot {low:.2f} px under the ground at {t}')
    # the right sole does not slide while it is planted (whole scene)
    xs = [sole('right', full_pose(at(t)))[0] for t in every]
    drift = max(abs(x[2] - xs[0][2]) for x in xs)
    print(f'right sole drift {drift:.2f} px')
    if drift > 0.4:
        errors.append(f'right sole slides {drift:.2f} px')
    for t in (0, 60, 150, 205, 330, 398, 412, 424, 436, 444, 450, 462, 480):
        print(t, 'fist', np.round(fist(full_pose(at(t))), 3))
    return errors


def golem_pose(pose, raise_deg, forward_px):
    q = dict(pose)
    for b in ('body@', 'right_leg@', 'left_leg@'):
        p = q[b]; q[b] = (p[0], p[1] + GOLEM_LIFT_PX, p[2] - forward_px)
    a = q['right_arm']; q['right_arm'] = (a[0] - raise_deg, a[1], a[2])
    h = q['head']; q['head'] = (h[0] - GOLEM_HEAD_DEG, h[1], h[2])
    return q


def golem(keys, at):
    """The jump into a golem's jaw: GOLEM_LIFT_PX up, a forward drift (px) and an extra raise of the
    right arm (degrees) that put the fist on the golem's jaw point (FinalPunchPose.aim)."""
    pose = full_pose(at(CONTACT))

    def err(x):
        d = fist(golem_pose(pose, x[0], x[1])) - np.array(GOLEM_FIST)
        return d @ d + 1e-6 * x[1] ** 2
    best, _ = nelder_mead(err, [20.0, 2.0], [5.0, 1.0], iters=2000, tol=1e-14)
    f = fist(golem_pose(pose, best[0], best[1]))
    print(f'golem: lift {GOLEM_LIFT_PX} px, forward {best[1]:.2f} px, arm raise {best[0]:.2f} deg -> fist', np.round(f, 3), 'target', GOLEM_FIST)
    if np.linalg.norm(f - np.array(GOLEM_FIST)) > 0.06:
        raise SystemExit('golem aim does not reach the jaw')
    return best


def fist_table(at):
    ts = sorted(set(list(range(0, LENGTH + 1, 6)) + list(range(STRIKE - 8, LOWER + 8, 2)) + [CONTACT, LAUNCH, LENGTH]))
    rows = []
    for t in ts:
        f = fist(full_pose(at(t)))
        rows.append(f'      {{{t}, {f[0]:.3f}, {f[1]:.3f}, {f[2]:.3f}}},')
    return '\n'.join(rows)


def main():
    check = '--check' in sys.argv
    keys, at, clip = build()
    errors = report(keys, at)
    raise_deg, forward_px = golem(keys, at)
    text = json.dumps(clip, separators=(',', ':'))
    table = fist_table(at)
    java = FIST.read_text()
    new_java = re.sub(r'(// GENERATED-FIST-TABLE-BEGIN\n).*?(\n\s*// GENERATED-FIST-TABLE-END)',
                      lambda m: m.group(1) + '   static final double[][] KEYS = {\n' + table + '\n   };' + m.group(2), java, flags=re.S)
    print(f'clip {len(text)} bytes; FinalPunchPose: GOLEM_LIFT_PX = {GOLEM_LIFT_PX}F, GOLEM_FORWARD_PX = {forward_px:.2f}F, GOLEM_ARM_DEG = {raise_deg:.2f}F')
    if errors:
        print('\n'.join(errors)); sys.exit(1)
    if check:
        ok = OUT.exists() and OUT.read_text() == text and new_java == java
        print('up to date' if ok else 'OUT OF DATE'); sys.exit(0 if ok else 1)
    OUT.write_text(text)
    FIST.write_text(new_java)


if __name__ == '__main__':
    main()
