#!/usr/bin/env python3
"""Thanos clips (own work, CC0) for geo/thanos/thanos.geo.json -> animations/thanos/thanos_<group>.animation.json (FILES).

Timing contract (ticks, written in seconds):
* every move clip is <telegraph><active><recovery> long with the BASE numbers of ThanosMove; the
  renderer stretches the telegraph part to the phase-scaled telegraph (ThanosRules.telegraph), so
  the strike lands exactly when the server applies it;
* intro = ThanosRules.INTRO_TICKS (80): portal 0-20, steps out 20-60, clenches the gauntlet 60-80;
* stun = 40 t (ThanosCombat stun), hurt = 8 t and is played ADDITIVELY (deltas only);
* snap follows ThanosSceneTimeline: raise 0-40, stones 44-80, faces the heroes 80-110, folds the
  fingers 110-148, the click at 150, holds to 165; snap_hold loops after it;
* defeat follows the victory scene: stagger 0-30, kneel 30-60, stones dim 60-120, crumble 120-200;
* victory = his triumph loop while the snap waits for another world pause (SNAP_WAIT).
Locomotion: idle (calm), idle_fight, walk (calm, arms swing), walk_fight (legs, waist and root
only, over idle_fight), turn_left / turn_right (on-the-spot shuffle).
Run: python3 tools/assets/thanos_clips.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_geo import Clip, write_anims  # noqa: E402

T = 20.0


def P(base=None, **bones):
    out = dict(base or {})
    out.update(bones)
    return out


def low(y, z=0.0, rot=(0, 0, 0)):
    """Root offset in model px (y < 0 drops the body, z > 0 moves it back) and a whole-body turn."""
    return {'rot': rot, 'pos': (0, y, z)}


def _norm(v):
    return v if isinstance(v, dict) else {'rot': v}


def clip(name, ticks, keys, loop=False, only=None, mode='monotone'):
    """keys: [(tick, pose)], each pose merged over the previous one (carry-forward), so every bone the
    clip touches is keyed at every key. only: restrict the clip to these bones (layered clips)."""
    c = Clip(name, ticks / T, loop=loop, smooth=True)
    bones = []
    for _, pose in keys:
        for b in pose:
            if b not in bones and (only is None or b in only):
                bones.append(b)
    cur = {b: keys[0][1].get(b, (0, 0, 0)) for b in bones}
    for t, pose in keys:
        cur = dict(cur)
        cur.update({b: v for b, v in pose.items() if b in cur})
        c.key(t / T, {b: _norm(cur[b]) for b in bones})
    c.mode = mode
    c.keep = set(bones)  # constant channels stay, so an overlay poses every bone of the clip
    return c


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


# ------------------------------------------------------------------ hands
def gfing(i, it, m, mt, r, rt, p, pt, th, tht):
    return dict(left_index=i, left_index_tip=it, left_middle=m, left_middle_tip=mt, left_ring=r, left_ring_tip=rt,
                left_pinky=p, left_pinky_tip=pt, left_thumb=th, left_thumb_tip=tht)


def zc(a):
    return (0, 0, a)


G_RELAX = gfing(zc(18), zc(16), zc(20), zc(18), zc(22), zc(20), zc(25), zc(22), (-10, 0, 10), zc(8))
G_FIST = gfing(zc(95), zc(100), zc(95), zc(100), zc(95), zc(100), zc(95), zc(100), (30, 0, 55), zc(40))
G_HALF = gfing(zc(55), zc(60), zc(55), zc(60), zc(58), zc(62), zc(60), zc(64), (15, 0, 35), zc(25))
G_OPEN = gfing((-12, 0, -6), zc(-4), (-4, 0, -6), zc(-4), (4, 0, -6), zc(-4), (12, 0, -6), zc(-4), (-35, 0, -12), zc(-6))
G_CLAW = gfing(zc(45), zc(60), zc(45), zc(60), zc(48), zc(62), zc(50), zc(64), (-10, 0, 30), zc(30))
G_SNAP_READY = gfing(zc(30), zc(25), zc(55), zc(45), zc(75), zc(80), zc(85), zc(85), (28, 0, 48), zc(22))
G_SNAP_DONE = gfing(zc(35), zc(30), zc(100), zc(70), zc(80), zc(85), zc(90), zc(90), (-8, 0, 18), zc(6))
R_RELAX = dict(right_fingers=zc(-25), right_fingers_tip=zc(-20), right_thumb=(-8, 0, -10))
R_FIST = dict(right_fingers=zc(-95), right_fingers_tip=zc(-100), right_thumb=(30, 0, -55))
R_OPEN = dict(right_fingers=zc(5), right_fingers_tip=zc(0), right_thumb=(-30, 0, 12))
R_HALF = dict(right_fingers=zc(-55), right_fingers_tip=zc(-60), right_thumb=(15, 0, -35))

# ------------------------------------------------------------------ stances
STAND = P(dict(root=low(0), waist=(0, 0, 0), torso=(3, 0, 0), neck=(0, 0, 0), head=(-3, 0, 0),
               kilt_front=(0, 0, 0), kilt_back=(0, 0, 0),
               right_arm=(2, 0, 7), right_forearm=(-12, 0, 0), right_hand=(0, 0, 0),
               left_arm=(2, 0, -8), left_forearm=(-14, 0, 0), left_hand=(0, 0, 0),
               right_leg=(0, 0, 3), right_shin=(2, 0, 0), right_foot=(-2, 0, -3),
               left_leg=(0, 0, -3), left_shin=(2, 0, 0), left_foot=(-2, 0, 3)), **G_RELAX, **R_RELAX)

FIGHT = P(STAND, root=low(-1.5), waist=(0, 8, 0), torso=(8, 6, 0), head=(-10, -14, 0),
          right_arm=(-25, 0, 18), right_forearm=(-75, 0, 0), right_hand=(0, 0, 0),
          left_arm=(-38, 0, -12), left_forearm=(-55, 0, 0), left_hand=(0, -30, 0),
          right_leg=(12, 10, 5), right_shin=(16, 0, 0), right_foot=(-12, 0, -3),
          left_leg=(-16, -6, -5), left_shin=(22, 0, 0), left_foot=(-6, 0, 3),
          kilt_front=(-6, 0, 0), kilt_back=(6, 0, 0), **G_FIST, **R_FIST)

LEGS = ('root', 'waist', 'right_leg', 'right_shin', 'right_foot', 'left_leg', 'left_shin', 'left_foot',
        'kilt_front', 'kilt_back')


def breathe(base, amp=1.5):
    return P(base, torso=add(base['torso'], (-amp, 0, 0)), head=add(base['head'], (amp * 0.7, 0, 0)),
             right_arm=add(base['right_arm'], (0, 0, amp * 0.6)), left_arm=add(base['left_arm'], (0, 0, -amp * 0.6)))


def kneel_right(root_drop=-13.0):
    """Down on the right knee, left foot planted forward (hips ~16 px)."""
    return dict(root=low(root_drop), right_leg=(18, 6, 4), right_shin=(72, 0, 0), right_foot=(-20, 0, 0),
                left_leg=(-78, -8, -6), left_shin=(80, 0, 0), left_foot=(-4, 0, 0),
                kilt_front=(-30, 0, 0), kilt_back=(10, 0, 0))


def crouch(drop=-5.0):
    return dict(root=low(drop), right_leg=(-26, 10, 6), right_shin=(48, 0, 0), right_foot=(-22, 0, -3),
                left_leg=(-34, -8, -6), left_shin=(56, 0, 0), left_foot=(-22, 0, 3),
                kilt_front=(-22, 0, 0), kilt_back=(14, 0, 0))


def lunge_left(drop=-2.5, z=-3.0):
    """Long step with the left (gauntlet) leg forward."""
    return dict(root=low(drop, z), left_leg=(-38, -5, -6), left_shin=(38, 0, 0), left_foot=(-2, 0, 3),
                right_leg=(30, 8, 6), right_shin=(12, 0, 0), right_foot=(-16, 0, -3),
                kilt_front=(-16, 0, 0), kilt_back=(12, 0, 0))


PULL_BACK = dict(left_arm=(28, 0, -22), left_forearm=(-112, 0, 0), left_hand=(0, 0, 0),
                 torso=(5, -30, 0), waist=(0, -10, 0), head=(-8, 32, 0),
                 right_arm=(-55, 18, 12), right_forearm=(-80, 0, 0))
PUNCH_OUT = dict(left_arm=(-92, -50, -2), left_forearm=(-4, 0, 0), left_hand=(0, 0, 0),
                 torso=(14, 40, 0), waist=(5, 14, 0), head=(-12, -48, 0),
                 right_arm=(-10, 0, 28), right_forearm=(-60, 0, 0))
GAUNTLET_UP = dict(left_arm=(-176, 0, -8), left_forearm=(-14, 0, 0), left_hand=(0, -90, 0),
                   torso=(-14, 10, 0), head=(-26, -6, 0), right_arm=(-12, 0, 26), right_forearm=(-24, 0, 0))
SNAP_POSE = dict(left_arm=(-78, -32, -6), left_forearm=(-98, 0, 0), left_hand=(0, -90, 0))


def jitter(pose, k, bones=('torso', 'left_arm', 'left_forearm', 'head')):
    """A small deterministic tremble for held channels (k = key index)."""
    s = 1.2 if k % 2 else -1.2
    out = dict(pose)
    for b in bones:
        if b in out and not isinstance(out[b], dict):
            out[b] = add(out[b], (s, -s * 0.6, s * 0.4))
    return out


# ------------------------------------------------------------------ locomotion
def locomotion():
    out = []
    out.append(clip('idle', 80, [(0, STAND), (40, P(breathe(STAND, 1.8), **gfing(zc(26), zc(22), zc(28), zc(24),
                                                                                   zc(30), zc(26), zc(32), zc(28),
                                                                                   (-6, 0, 16), zc(12)))),
                                 (80, STAND)], loop=True))
    out.append(clip('idle_fight', 40, [(0, FIGHT), (20, P(breathe(FIGHT, 2.4), root=low(-2.1))), (40, FIGHT)], loop=True))
    # heavy calm walk, 32 t per two steps; the root dips on each heel strike
    wa = P(STAND, root=low(-1.0), right_leg=(-24, 0, 3), right_shin=(6, 0, 0), right_foot=(10, 0, -3),
           left_leg=(20, 0, -3), left_shin=(16, 0, 0), left_foot=(-14, 0, 3), waist=(0, -5, 0), torso=(4, 4, 0),
           right_arm=(16, 0, 7), right_forearm=(-10, 0, 0), left_arm=(-16, 0, -8), left_forearm=(-24, 0, 0),
           kilt_front=(-8, 0, 0), kilt_back=(4, 0, 0))
    wm = P(STAND, root=low(0.4), right_leg=(-2, 0, 3), right_shin=(6, 0, 0), right_foot=(-4, 0, -3),
           left_leg=(-6, 0, -3), left_shin=(40, 0, 0), left_foot=(-16, 0, 3), waist=(0, 0, 0), torso=(4, 0, 0),
           right_arm=(2, 0, 7), left_arm=(0, 0, -8), kilt_front=(-3, 0, 0), kilt_back=(2, 0, 0))

    def mirror(p):
        o = {}
        for k, v in p.items():
            nk = k.replace('right_', '#').replace('left_', 'right_').replace('#', 'left_')
            if isinstance(v, dict):
                o[nk] = v
            else:
                o[nk] = (v[0], -v[1], -v[2])
        return o
    wb = P(wa, **mirror({k: v for k, v in wa.items() if 'leg' in k or 'shin' in k or 'foot' in k or k.endswith('_arm')}),
           waist=(0, 5, 0), torso=(4, -4, 0))
    wn = P(wm, **mirror({k: v for k, v in wm.items() if 'leg' in k or 'shin' in k or 'foot' in k}))
    # the gauntlet arm keeps its own (heavier) swing and the relaxed hand
    wb['left_forearm'] = (-12, 0, 0)
    wb['right_forearm'] = (-26, 0, 0)
    out.append(clip('walk', 32, [(0, wa), (8, wm), (16, wb), (24, wn), (32, wa)], loop=True))
    fa = P(FIGHT, root=low(-2.4), right_leg=(-20, 10, 5), right_shin=(14, 0, 0), right_foot=(6, 0, -3),
           left_leg=(14, -6, -5), left_shin=(18, 0, 0), left_foot=(-14, 0, 3), waist=(0, 2, 0))
    fm = P(FIGHT, root=low(-1.2), right_leg=(0, 10, 5), right_shin=(10, 0, 0), right_foot=(-4, 0, -3),
           left_leg=(-4, -6, -5), left_shin=(42, 0, 0), left_foot=(-18, 0, 3), waist=(0, 8, 0))
    fb = P(FIGHT, root=low(-2.4), right_leg=(16, 10, 5), right_shin=(20, 0, 0), right_foot=(-14, 0, -3),
           left_leg=(-22, -6, -5), left_shin=(12, 0, 0), left_foot=(8, 0, 3), waist=(0, 14, 0))
    fn = P(FIGHT, root=low(-1.2), right_leg=(-4, 10, 5), right_shin=(42, 0, 0), right_foot=(-18, 0, -3),
           left_leg=(0, -6, -5), left_shin=(10, 0, 0), left_foot=(-4, 0, 3), waist=(0, 8, 0))
    out.append(clip('walk_fight', 28, [(0, fa), (7, fm), (14, fb), (21, fn), (28, fa)], loop=True, only=LEGS))
    for name, sgn in (('turn_left', -1), ('turn_right', 1)):
        lead, trail = ('right', 'left') if sgn > 0 else ('left', 'right')
        a = P(FIGHT, waist=(0, 8 + 10 * sgn, 0), torso=(8, 6 + 8 * sgn, 0), head=(-10, -14 + 10 * sgn, 0))
        step1 = P(a, **{lead + '_leg': (-14, 14 * sgn, 6 * sgn), lead + '_shin': (34, 0, 0), lead + '_foot': (-14, 0, 0)},
                  root=low(-0.8))
        step2 = P(a, **{trail + '_leg': (-12, 12 * sgn, -6 * sgn), trail + '_shin': (32, 0, 0), trail + '_foot': (-14, 0, 0)},
                  root=low(-0.8))
        out.append(clip(name, 20, [(0, a), (5, step1), (10, a), (15, step2), (20, a)], loop=True, only=LEGS + ('torso', 'head')))
    return out


# ------------------------------------------------------------------ moves (telegraph | active | recovery)
def moves():
    out = []
    F = FIGHT
    # CLEAVE 12|6|14: right backfist sweep, left to right
    wind = P(F, torso=(6, -30, 0), waist=(0, -10, 0), head=(-8, 22, 0), right_arm=(-86, -58, 10),
             right_forearm=(-36, 0, 0), right_hand=(0, 0, 0))
    out.append(clip('cleave', 32, [(0, F), (8, wind), (12, P(wind, right_arm=(-88, -64, 10), torso=(6, -33, 0))),
                                   (15, P(F, torso=(8, 0, 0), waist=(0, 0, 0), head=(-10, 0, 0), right_arm=(-88, 8, 10),
                                          right_forearm=(-20, 0, 0))),
                                   (18, P(F, torso=(10, 32, 0), waist=(0, 14, 0), head=(-10, -28, 0), right_arm=(-80, 62, 16),
                                          right_forearm=(-14, 0, 0))),
                                   (24, P(F, torso=(8, 22, 0), waist=(0, 12, 0), head=(-10, -24, 0), right_arm=(-60, 50, 20),
                                          right_forearm=(-30, 0, 0))),
                                   (32, F)]))
    # HAMMER 16|6|18: both fists overhead, slam
    top = P(F, right_arm=(-168, 8, -14), left_arm=(-168, -8, 14), right_forearm=(-32, 0, 0), left_forearm=(-32, 0, 0),
            left_hand=(0, 0, 0), torso=(-12, 0, 0), waist=(0, 0, 0), head=(-6, 0, 0), root=low(0.6))
    slam = P(F, right_arm=(-62, 0, -6), left_arm=(-62, 0, 6), right_forearm=(-12, 0, 0), left_forearm=(-12, 0, 0),
             left_hand=(0, 0, 0), torso=(36, 0, 0), waist=(10, 0, 0), head=(-34, 0, 0), **crouch(-5))
    out.append(clip('hammer', 40, [(0, F), (10, top), (16, P(top, torso=(-16, 0, 0))), (19, slam),
                                   (22, P(slam, torso=(38, 0, 0), right_arm=(-48, 0, -6), left_arm=(-48, 0, 6))),
                                   (32, P(F, torso=(22, 0, 0), **crouch(-2.5))), (40, F)]))
    # POWER_PUNCH 20|8|20: pull the gauntlet back, lunge, straight punch
    pb = P(F, **PULL_BACK)
    po = P(F, **PUNCH_OUT, **lunge_left())
    out.append(clip('power_punch', 48, [(0, F), (14, pb), (20, jitter(pb, 1)), (23, po), (28, jitter(po, 0)),
                                        (38, P(F, torso=(10, 24, 0), left_arm=(-70, -24, -8), left_forearm=(-30, 0, 0),
                                               **lunge_left(-2, -1.5))),
                                        (48, F)]))
    # POWER_WAVE 24|30|16: gauntlet up, slam into the ground on one knee, hold while the waves run
    up = P(F, **GAUNTLET_UP, root=low(0.8))
    down = P(F, left_arm=(-34, 0, -8), left_forearm=(-6, 0, 0), left_hand=(0, 0, 0), torso=(52, 0, 0), waist=(10, 0, 0),
             head=(-46, 0, 0), right_arm=(-24, 0, 34), right_forearm=(-30, 0, 0), **kneel_right())
    out.append(clip('power_wave', 70, [(0, F), (16, up), (24, jitter(up, 1)), (27, down), (40, jitter(down, 1)),
                                       (54, jitter(down, 0)), (62, P(F, torso=(26, 0, 0), **crouch(-5))), (70, F)]))
    # SPACE_STEP 14|4|8: sweep the open gauntlet (portal), step through, land
    sw = P(F, left_arm=(-80, -42, -20), left_forearm=(-10, 0, 0), left_hand=(0, -90, 0), torso=(0, -16, 0), head=(-6, 12, 0),
           **G_OPEN)
    out.append(clip('space_step', 26, [(0, F), (10, sw), (14, P(sw, left_arm=(-86, 22, -10), torso=(5, 16, 0), head=(-8, -14, 0))),
                                       (18, P(F, torso=(16, 10, 0), **lunge_left(-2, -2), **G_OPEN)), (26, F)]))
    # SPACE_PULL 18|16|12: reach out, grab, yank back
    reach = P(F, left_arm=(-90, -20, -5), left_forearm=(-5, 0, 0), left_hand=(0, 0, 0), torso=(5, 22, 0), head=(-6, -22, 0),
              **G_OPEN)
    yank = P(F, left_arm=(12, 0, -16), left_forearm=(-100, 0, 0), left_hand=(0, 0, 0), torso=(-5, -24, 0), waist=(0, -8, 0),
             head=(-6, 26, 0), root=low(-2))
    out.append(clip('space_pull', 46, [(0, F), (14, reach), (18, P(jitter(reach, 1), **G_CLAW)), (22, yank),
                                       (34, jitter(yank, 0)), (46, F)]))
    # SPACE_WARD 6|30|6: open palm forward, the portal shield in front
    ward = P(F, left_arm=(-96, -14, -5), left_forearm=(-12, 0, 0), left_hand=(-58, 0, 0), torso=(0, 22, 0), head=(-6, -22, 0),
             right_arm=(-20, 0, 22), right_forearm=(-60, 0, 0), **G_OPEN)
    out.append(clip('space_ward', 42, [(0, F), (6, ward), (16, jitter(ward, 1)), (26, jitter(ward, 0)), (36, ward), (42, F)]))
    # REALITY_ZONE 20|10|14: low sweep of the open gauntlet over the ground
    rz0 = P(F, left_arm=(-58, -58, -30), left_forearm=(-14, 0, 0), left_hand=(0, 0, 0), torso=(14, -20, 0), head=(-8, 16, 0),
            **G_OPEN)
    out.append(clip('reality_zone', 44, [(0, F), (14, rz0), (20, jitter(rz0, 1)),
                                         (25, P(rz0, left_arm=(-58, 42, -10), torso=(16, 26, 0), head=(-10, -22, 0))),
                                         (30, P(rz0, left_arm=(-52, 50, -12), torso=(16, 30, 0), head=(-10, -24, 0))),
                                         (44, F)]))
    # REALITY_MIRAGE 16|50|12: gauntlet to the face, flick, arms spread while the copies hold
    face = P(F, left_arm=(-72, -20, -14), left_forearm=(-72, 0, 0), left_hand=(0, -90, 0), head=(-4, 8, 0), **G_CLAW)
    spread = P(F, left_arm=(-22, 0, -72), right_arm=(-22, 0, 72), left_forearm=(-20, 0, 0), right_forearm=(-20, 0, 0),
               left_hand=(0, 0, 0), torso=(-6, 0, 0), waist=(0, 0, 0), head=(-12, 0, 0), **G_OPEN, **R_OPEN)
    out.append(clip('reality_mirage', 78, [(0, F), (10, face), (16, P(face, **G_OPEN)), (20, spread), (40, jitter(spread, 1)),
                                           (66, jitter(spread, 0)), (78, F)]))
    # TIME_DOME 22|10|12: palm up to the sky, the dome drops with the hand
    sky = P(F, left_arm=(-150, 0, -24), left_forearm=(-40, 0, 0), left_hand=(0, 0, 0), head=(-26, 8, 0), torso=(-8, 10, 0),
            **G_OPEN)
    press = P(F, left_arm=(-100, 0, -30), left_forearm=(-20, 0, 0), left_hand=(0, -90, 0), torso=(12, 0, 0), head=(-12, 0, 0),
              **G_OPEN)
    out.append(clip('time_dome', 44, [(0, F), (12, sky), (22, P(sky, left_hand=(0, -60, 0), left_forearm=(-46, 0, 0))),
                                      (26, press), (32, jitter(press, 1)), (44, F)]))
    # TIME_REWIND 10|20|10: gauntlet before the chest, the wrist turns circles
    rw = P(F, left_arm=(-70, 30, -10), left_forearm=(-80, 0, 0), left_hand=(0, 0, 0), **G_OPEN)
    keys = [(0, F), (8, rw)]
    for i, t in enumerate(range(10, 31, 5)):
        keys.append((t, P(rw, left_hand=((0, -40, 20) if i % 2 == 0 else (0, 40, -20)))))
    keys.append((40, F))
    out.append(clip('time_rewind', 40, keys))
    # MIND_GAZE 24|6|14: back of the hand (Mind) to the target, thrust
    gz = P(F, left_arm=(-96, 26, -10), left_forearm=(-56, 0, 0), left_hand=(0, -90, 0), head=(-8, -2, 0), torso=(0, 20, 0))
    out.append(clip('mind_gaze', 44, [(0, F), (16, gz), (24, jitter(gz, 1)),
                                      (26, P(gz, left_arm=(-96, 10, -5), left_forearm=(-22, 0, 0), torso=(8, 26, 0))),
                                      (30, P(gz, left_arm=(-94, 10, -5), left_forearm=(-24, 0, 0), torso=(8, 26, 0))),
                                      (44, F)]))
    # MIND_PULSE 18|40|12: gauntlet to the temple, then both palms out while the rings pulse
    temple = P(F, left_arm=(-62, 40, -20), left_forearm=(-122, 0, 0), left_hand=(0, -40, 0), head=(6, -10, 0), **G_CLAW)
    palms = P(F, left_arm=(-80, 0, -40), left_forearm=(-10, 0, 0), left_hand=(0, 0, 0), right_arm=(-32, 0, 40),
              right_forearm=(-14, 0, 0), torso=(-5, 0, 0), waist=(0, 0, 0), head=(-12, 0, 0), **G_OPEN, **R_OPEN)
    out.append(clip('mind_pulse', 70, [(0, F), (12, temple), (18, jitter(temple, 1)), (22, palms), (40, jitter(palms, 1)),
                                       (58, jitter(palms, 0)), (70, F)]))
    # SOUL_TETHER 18|80|14: fingers splayed at the targets, the hand slowly closes while it drains
    st = P(F, left_arm=(-88, -18, -8), left_forearm=(-5, 0, 0), left_hand=(0, 0, 0), torso=(-2, 22, 0), head=(-6, -22, 0),
           **G_OPEN)
    out.append(clip('soul_tether', 112, [(0, F), (14, st), (18, P(st, **G_CLAW)), (60, P(jitter(st, 1), left_forearm=(-20, 0, 0),
                                                                                        **G_HALF)),
                                         (98, P(jitter(st, 0), left_forearm=(-24, 0, 0), **G_HALF)), (112, F)]))
    # SOUL_NOVA 30|8|20: crouch with the arms crossed, burst open
    cr = P(F, torso=(30, 0, 0), waist=(0, 0, 0), head=(-26, 0, 0), left_arm=(-60, 50, -10), left_forearm=(-100, 0, 0),
           left_hand=(0, 0, 0), right_arm=(-60, -50, 10), right_forearm=(-100, 0, 0), **crouch(-5))
    burst = P(STAND, root=low(1), torso=(-18, 0, 0), head=(-26, 0, 0), left_arm=(-30, 0, -110), right_arm=(-30, 0, 110),
              left_forearm=(-10, 0, 0), right_forearm=(-10, 0, 0), kilt_front=(-12, 0, 0), kilt_back=(12, 0, 0), **G_OPEN, **R_OPEN)
    out.append(clip('soul_nova', 58, [(0, F), (24, cr), (30, jitter(cr, 1)), (33, burst), (38, burst), (58, F)]))
    # combos ------------------------------------------------------------------------------------
    # PORTAL_PUNCH 22|14|22: the punch goes into a portal and comes out at the target
    out.append(clip('combo_portal_punch', 58, [(0, F), (16, pb), (22, jitter(pb, 1)), (25, po), (36, jitter(po, 0)),
                                               (46, P(F, torso=(10, 24, 0), left_arm=(-70, -24, -8), left_forearm=(-30, 0, 0),
                                                      **lunge_left(-2, -1.5))), (58, F)]))
    # TIME_CRUSH 26|30|20: slow raise (time slows) then the slam
    out.append(clip('combo_time_crush', 76, [(0, F), (18, up), (26, jitter(up, 1)), (29, down), (44, jitter(down, 1)),
                                             (56, jitter(down, 0)), (66, P(F, torso=(26, 0, 0), **crouch(-5))), (76, F)]))
    # SOUL_MIRAGE 22|70|16: arms spread (copies), then the gauntlet closes on the tethers
    out.append(clip('combo_soul_mirage', 108, [(0, F), (12, face), (22, P(face, **G_OPEN)), (26, spread),
                                               (50, P(st, **G_CLAW)), (76, P(jitter(st, 1), **G_HALF)), (92, P(st, **G_HALF)),
                                               (108, F)]))
    # MIND_STEP 24|16|14: gaze, step through, right cross
    cross = P(F, right_arm=(-90, 44, 4), right_forearm=(-6, 0, 0), torso=(14, -36, 0), waist=(4, -12, 0), head=(-12, 40, 0),
              left_arm=(-20, 0, -26), left_forearm=(-70, 0, 0), left_hand=(0, 0, 0),
              root=low(-2.5, -3), right_leg=(-36, 8, 6), right_shin=(36, 0, 0), right_foot=(-2, 0, -3),
              left_leg=(28, -6, -6), left_shin=(12, 0, 0), left_foot=(-16, 0, 3))
    out.append(clip('combo_mind_step', 54, [(0, F), (18, gz), (24, jitter(gz, 1)), (28, cross), (40, jitter(cross, 0)), (54, F)]))
    # TRINITY 34|50|24: gauntlet overhead (three stones flare), thrust forward and hold the blast
    thrust = P(F, left_arm=(-94, -34, -4), left_forearm=(-6, 0, 0), left_hand=(-60, 0, 0), torso=(12, 30, 0), waist=(4, 10, 0),
               head=(-10, -36, 0), right_arm=(-30, 0, 30), right_forearm=(-50, 0, 0), **lunge_left(-2.5, -1), **G_OPEN)
    out.append(clip('combo_trinity', 108, [(0, F), (20, up), (34, jitter(up, 1)), (38, thrust), (60, jitter(thrust, 1)),
                                           (88, jitter(thrust, 0)), (108, F)]))
    # ETERNITY 36|60|24: fist to the sky, the open hand presses down, the aura holds
    sky2 = P(F, **GAUNTLET_UP, root=low(0.6), **G_FIST)
    presd = P(F, left_arm=(-62, 0, -18), left_forearm=(-20, 0, 0), left_hand=(0, -90, 0), torso=(18, 6, 0), head=(-18, -6, 0),
              right_arm=(-20, 0, 40), right_forearm=(-20, 0, 0), **crouch(-3.5), **G_OPEN)
    out.append(clip('combo_eternity', 120, [(0, F), (24, sky2), (36, jitter(sky2, 1)), (40, presd), (66, jitter(presd, 1)),
                                            (96, jitter(presd, 0)), (120, F)]))
    return out


# ------------------------------------------------------------------ reactions and scenes
def scenes():
    out = []
    F = FIGHT
    # hurt: additive deltas only (played over whatever pose he is in)
    zero = {b: (0, 0, 0) for b in ('torso', 'head', 'right_arm', 'left_arm')}
    zero['root'] = low(0)
    out.append(clip('hurt', 8, [(0, zero), (2, dict(torso=(-8, 5, 0), head=(-10, 0, 4), right_arm=(-5, 0, 8), left_arm=(-5, 0, -8),
                                                    root=low(0, 1.5))), (8, zero)]))
    # stun 40 t: jolt back, hunch, shake the head, recover
    jolt = P(F, torso=(-15, 0, 0), head=(-20, 0, 0), left_arm=(-20, 0, -30), right_arm=(-20, 0, 30), root=low(-1, 3),
             **G_OPEN, **R_OPEN)
    hunch = P(F, torso=(34, 0, 0), waist=(4, 0, 0), head=(10, 0, 0), left_arm=(0, 0, -12), right_arm=(0, 0, 12),
              left_forearm=(-12, 0, 0), right_forearm=(-12, 0, 0), left_hand=(0, 0, 0), **crouch(-3), **G_RELAX, **R_RELAX)
    out.append(clip('stun', 40, [(0, F), (4, jolt), (10, hunch), (20, P(hunch, head=(10, 16, 0))), (26, P(hunch, head=(10, -16, 0))),
                                 (32, P(F, torso=(20, 0, 0))), (40, F)]))
    # intro 80 t: steps out of the portal (behind him), clenches the gauntlet
    s0 = P(STAND, root=low(0, 20))
    st1 = P(STAND, root=low(-1, 12), right_leg=(-24, 0, 3), right_shin=(6, 0, 0), left_leg=(20, 0, -3), left_shin=(16, 0, 0),
            right_arm=(16, 0, 7), left_arm=(-16, 0, -8))
    st2 = P(STAND, root=low(-1, 4), left_leg=(-24, 0, -3), left_shin=(6, 0, 0), right_leg=(20, 0, 3), right_shin=(16, 0, 0),
            right_arm=(-14, 0, 7), left_arm=(16, 0, -8))
    look = P(STAND, left_arm=(-62, 18, -10), left_forearm=(-84, 0, 0), left_hand=(0, -90, 0), head=(4, -18, 0), torso=(4, 8, 0),
             **G_OPEN)
    out.append(clip('intro', 80, [(0, s0), (20, s0), (32, st1), (46, st2), (60, P(STAND, root=low(0, 0))), (66, look),
                                  (72, P(look, **G_FIST)), (76, P(jitter(look, 1), **G_FIST)), (80, F)]))
    # victory (his triumph while the snap waits): gauntlet at eye level, admiring it
    vic = P(STAND, left_arm=(-80, 14, -12), left_forearm=(-96, 0, 0), left_hand=(0, -90, 0), head=(2, -16, 0), torso=(-2, 8, 0),
            right_arm=(4, 0, 9), **G_HALF)
    out.append(clip('victory', 40, [(0, vic), (20, P(breathe(vic, 1.6), **G_FIST)), (40, vic)], loop=True))
    # snap, synchronised with ThanosSceneTimeline
    stand_tall = P(STAND, head=(-4, 0, 0))
    raise1 = P(stand_tall, **SNAP_POSE, head=(-2, -14, 0), torso=(-2, 6, 0), **G_RELAX)
    lit = P(raise1, **G_OPEN)
    faced = P(lit, head=(-2, 0, 0))
    ready = P(faced, **G_SNAP_READY)
    done = P(faced, **G_SNAP_DONE, left_forearm=(-102, 0, 0))
    out.append(clip('snap', 165, [(0, F), (14, P(stand_tall, left_arm=(-40, -10, -10), left_forearm=(-60, 0, 0))), (40, raise1),
                                  (44, raise1), (62, P(jitter(lit, 1), **G_OPEN)), (80, lit), (96, faced), (110, faced),
                                  (140, P(ready, left_forearm=(-97, 0, 0))), (148, ready), (150, done), (152, done),
                                  (165, P(done, head=(4, 0, 0), torso=(2, 6, 0)))]))
    out.append(clip('snap_hold', 40, [(0, P(done, head=(4, 0, 0), torso=(2, 6, 0))), (20, P(done, head=(5, 0, 0), torso=(3, 6, 0))),
                                      (40, P(done, head=(4, 0, 0), torso=(2, 6, 0)))], loop=True))
    # defeat (the heroes' victory scene): stagger, one knee, the stones dim, he crumbles
    hit = P(F, torso=(-18, 0, 0), head=(-24, 0, 0), root=low(-1, 2), left_arm=(-10, 0, -34), right_arm=(-10, 0, 34),
            **G_OPEN, **R_OPEN)
    back = P(STAND, torso=(-8, 0, 0), head=(-12, 0, 0), root=low(-1.5, 6), right_leg=(22, 0, 3), right_shin=(14, 0, 0),
             left_leg=(-10, 0, -3), left_arm=(-6, 0, -24), right_arm=(-6, 0, 20), **G_RELAX, **R_RELAX)
    knee = P(STAND, torso=(30, 0, 0), waist=(6, 0, 0), head=(4, 0, 0), left_arm=(-34, -10, -6), left_forearm=(-8, 0, 0),
             left_hand=(0, 0, 0), right_arm=(-22, 4, 10), right_forearm=(-42, 0, 0), **kneel_right(), **G_HALF, **R_RELAX)
    stare = P(knee, head=(24, -24, 0), left_arm=(-58, 10, -8), left_forearm=(-70, 0, 0), left_hand=(0, -90, 0))
    out.append(clip('defeat', 200, [(0, F), (6, hit), (18, back), (30, P(back, torso=(4, 0, 0))), (44, P(knee, root=low(-8))),
                                    (60, knee), (72, stare), (96, P(stare, head=(26, -20, 0), **G_RELAX)),
                                    (120, P(stare, head=(30, -12, 0), **G_RELAX)),
                                    (160, P(knee, head=(36, 0, 0), torso=(42, 0, 0), left_arm=(-20, 0, -8))),
                                    (200, P(knee, head=(40, 0, 0), torso=(48, 0, 0), left_arm=(-10, 0, -10), right_arm=(-20, 10, 12)))]))
    return out


FILES = {  # file -> clip groups; each file stays below 55 KB (one GitHub request per file)
    'thanos_base': ('idle', 'idle_fight', 'walk', 'walk_fight', 'turn_left', 'turn_right', 'hurt', 'stun'),
    'thanos_melee': ('cleave', 'hammer', 'power_punch', 'power_wave', 'space_step', 'space_pull', 'space_ward',
                     'reality_zone', 'reality_mirage'),
    'thanos_stones': ('time_dome', 'time_rewind', 'mind_gaze', 'mind_pulse', 'soul_tether', 'soul_nova'),
    'thanos_combos': ('combo_portal_punch', 'combo_time_crush', 'combo_soul_mirage', 'combo_mind_step', 'combo_trinity',
                      'combo_eternity'),
    'thanos_scene': ('intro', 'victory', 'snap', 'snap_hold', 'defeat'),
}


def main():
    clips = {c.name: c for c in locomotion() + moves() + scenes()}
    listed = [n for names in FILES.values() for n in names]
    missing = sorted(set(clips) - set(listed))
    assert not missing, 'clips without a file: %s' % missing
    old = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore/src/main/resources/assets/viltrumitecore/animations/thanos/thanos.animation.json')
    if os.path.exists(old):
        os.remove(old)
    for f, names in FILES.items():
        size = write_anims('animations/thanos/%s.animation.json' % f, [clips[n] for n in names])
        assert size < 55000, '%s is %d bytes' % (f, size)
        print('%s: %d clips, %d bytes' % (f, len(names), size))


if __name__ == '__main__':
    main()
