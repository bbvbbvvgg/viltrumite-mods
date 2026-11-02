#!/usr/bin/env python3
"""Black Order clips (own work, CC0): Ebony Maw and Cull Obsidian.

geo/thanos/ebony_maw.geo.json     -> animations/black_order/ebony_maw{,_moves}.animation.json
geo/thanos/cull_obsidian.geo.json -> animations/black_order/cull_obsidian{,_moves}.animation.json

Timing contract (ticks, written in seconds):
* every move clip is <telegraph><active><recovery> long with the MawMove / CullMove numbers; the server
  plays them unscaled, so the renderer starts the clip at the move start and the hit lands on the key;
* beam_in = 20 t (BlackOrderEntity.STATE_BEAM_IN), beam_out = 24 t (STATE_BEAM_OUT), death = 20 t (vanilla
  deathTime; the renderer extends EntityRenderer, so the fall is in the clip), hurt = 8 t ADDITIVE (deltas),
  stagger = 30 t (cut by the next move);
* Ebony Maw always floats (EbonyMawEntity.HOVER): idle (hover, fingers working) and glide (leans into the
  flight, coat trailing) blended by the walk factor; legs dangle;
* Cull Obsidian: idle (hunched, breathing) and walk = ONE stride cycle sampled from the entity's gait.
Maw's grab and barrier clips follow the server's hold timing (throw at telegraph + active - 8 for Maw,
telegraph + active - 4 for Cull's overhead throw); Cull's CHAIN hides weapon_head in the renderer while
the chain FX carries it (active window).
Run: python3 tools/assets/black_order_clips.py            # writes both files
     python3 tools/assets/black_order_clips.py preview maw|cull CLIP [N]   # build/thanos-assets/ sheet
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_geo import write_anims  # noqa: E402
from thanos_clips import P, add, clip, low  # noqa: E402

# two files per fighter (base: locomotion + scenes, moves), each below 46 KB so one GitHub request carries it
FILES = {
    'maw': {'animations/black_order/ebony_maw.animation.json': ('idle', 'glide', 'hurt', 'stagger', 'beam_in', 'beam_out', 'death'),
            'animations/black_order/ebony_maw_moves.animation.json': ('spear', 'volley', 'grab', 'barrier', 'topple', 'blink')},
    'cull': {'animations/black_order/cull_obsidian.animation.json': ('idle', 'walk', 'hurt', 'stagger', 'beam_in', 'beam_out', 'death'),
             'animations/black_order/cull_obsidian_moves.animation.json': ('swing', 'slam', 'charge', 'chain', 'grab', 'roar', 'clash')},
}


def shake(pose, k, bones, amp=1.4):
    """Deterministic tremble on held channels (k = key index): telekinetic strain, heavy holds."""
    s = amp if k % 2 else -amp
    out = dict(pose)
    for b in bones:
        if b in out and not isinstance(out[b], dict):
            out[b] = add(out[b], (s, -s * 0.5, s * 0.4))
    return out


# ================================================================== Ebony Maw
def rf(z):
    return (0, 0, -z)  # right fingers: positive = curl toward the palm


def lf(z):
    return (0, 0, z)


MAW_FLOAT = dict(root=low(0), waist=(0, 0, 0), robe_front=(-2, 0, 0), robe_back=(4, 0, 0), torso=(5, 0, 0),
                 collar=(0, 0, 0), neck=(3, 0, 0), head=(-5, 0, 7),
                 right_arm=(-18, 0, 16), right_forearm=(-38, 0, 0), right_hand=(-18, 0, 0), right_fingers=rf(12),
                 left_arm=(-12, 0, -14), left_forearm=(-50, 0, 0), left_hand=(-8, 0, 0), left_fingers=lf(18),
                 right_leg=(-6, 0, 3), right_shin=(14, 0, 0), left_leg=(4, 0, -3), left_shin=(22, 0, 0))
MAW_TK = ('right_arm', 'right_forearm', 'left_arm', 'left_forearm', 'torso', 'head')


def maw_loco():
    F = MAW_FLOAT
    out = [clip('idle', 60, [(0, F), (30, P(F, root=low(0.9), torso=(3, 0, 0), head=(-7, 0, 11), right_fingers=rf(24),
                                            left_fingers=lf(6), right_forearm=(-44, 0, 0), left_forearm=(-45, 0, 0),
                                            robe_front=(0, 0, 0), robe_back=(2, 0, 0), right_shin=(18, 0, 0),
                                            left_shin=(18, 0, 0))), (60, F)], loop=True)]
    glide = P(F, root=low(0, 0, (8, 0, 0)), torso=(16, 0, 0), neck=(0, 0, 0), head=(-14, 0, 4), robe_front=(18, 0, 0),
              robe_back=(26, 0, 0), right_arm=(14, 0, 16), right_forearm=(-18, 0, 0), right_hand=(10, 0, 0),
              left_arm=(14, 0, -16), left_forearm=(-20, 0, 0), left_hand=(10, 0, 0), right_leg=(16, 0, 3),
              right_shin=(22, 0, 0), left_leg=(22, 0, -3), left_shin=(26, 0, 0))
    out.append(clip('glide', 40, [(0, glide), (20, P(glide, root=low(0.6, 0, (6, 0, 0)), robe_front=(22, 0, 0),
                                                     robe_back=(31, 0, 0), right_leg=(20, 0, 3), left_leg=(18, 0, -3))),
                                  (40, glide)], loop=True))
    return out


def maw_moves():
    F = MAW_FLOAT
    out = []
    # SPEAR 14|8|12: draws a shard from the rubble with a pinch, thrusts the palm
    draw = P(F, right_arm=(-128, 10, -12), right_forearm=(-58, 0, 0), right_hand=(-26, 0, 0), right_fingers=rf(48),
             torso=(0, 22, 0), waist=(0, 8, 0), head=(-10, -18, 4), left_arm=(-30, -20, -20), left_forearm=(-70, 0, 0),
             robe_front=(-4, 0, 0))
    thrust = P(F, right_arm=(-94, 22, 6), right_forearm=(-4, 0, 0), right_hand=(-64, 0, 0), right_fingers=rf(-12),
               torso=(12, -26, 0), waist=(2, -8, 0), head=(-10, 22, 2), left_arm=(-10, 10, -30), left_forearm=(-40, 0, 0),
               robe_front=(8, 0, 0), robe_back=(14, 0, 0))
    out.append(clip('spear', 34, [(0, F), (10, draw), (14, shake(draw, 1, MAW_TK)), (17, thrust), (22, shake(thrust, 0, MAW_TK)),
                                  (34, F)]))
    # VOLLEY 24|30|16: lifts the debris ring with both hands, then alternating palm pushes
    up = P(F, right_arm=(-150, 0, -26), left_arm=(-150, 0, 26), right_forearm=(-26, 0, 0), left_forearm=(-26, 0, 0),
           right_hand=(-30, 0, 0), left_hand=(-30, 0, 0), right_fingers=rf(-12), left_fingers=lf(-12), torso=(-10, 0, 0),
           head=(-24, 0, 0), root=low(1.5), robe_front=(-6, 0, 0), robe_back=(8, 0, 0))
    push_r = P(F, right_arm=(-96, 20, 4), right_forearm=(-6, 0, 0), right_hand=(-62, 0, 0), right_fingers=rf(-12),
               left_arm=(-130, 0, 16), left_forearm=(-40, 0, 0), left_hand=(-30, 0, 0), left_fingers=lf(20),
               torso=(10, -22, 0), head=(-12, 18, 0), root=low(1.0))
    push_l = P(F, left_arm=(-96, -20, -4), left_forearm=(-6, 0, 0), left_hand=(-62, 0, 0), left_fingers=lf(-12),
               right_arm=(-130, 0, -16), right_forearm=(-40, 0, 0), right_hand=(-30, 0, 0), right_fingers=rf(20),
               torso=(10, 22, 0), head=(-12, -18, 0), root=low(1.0))
    out.append(clip('volley', 70, [(0, F), (14, up), (20, shake(up, 1, MAW_TK)), (24, shake(up, 0, MAW_TK)), (27, push_r),
                                   (33, push_l), (39, push_r), (45, push_l), (51, push_r), (54, shake(push_r, 1, MAW_TK)),
                                   (62, P(F, torso=(8, 0, 0))), (70, F)]))
    # GRAB 16|50|14: reach, close the hand, lift the hero high, strain, hurl (server throws at 16 + 42 = 58)
    reach = P(F, right_arm=(-98, 14, 10), right_forearm=(-6, 0, 0), right_hand=(-40, 0, 0), right_fingers=rf(-14),
              torso=(10, -18, 0), head=(-10, 16, 2), left_arm=(-24, 0, -24), left_forearm=(-60, 0, 0), robe_front=(6, 0, 0))
    lift = P(F, right_arm=(-150, 6, -14), right_forearm=(-20, 0, 0), right_hand=(-20, 0, 0), right_fingers=rf(50),
             torso=(-8, -10, 0), head=(-26, 6, 4), left_arm=(-40, 0, -30), left_forearm=(-50, 0, 0), left_fingers=lf(-10),
             root=low(1.2))
    hurl = P(F, right_arm=(-50, -20, 8), right_forearm=(-10, 0, 0), right_hand=(-30, 0, 0), right_fingers=rf(-14),
             torso=(18, -24, 0), waist=(4, -8, 0), head=(-12, 20, 0), robe_front=(10, 0, 0), robe_back=(18, 0, 0))
    out.append(clip('grab', 80, [(0, F), (12, reach), (16, P(reach, right_fingers=rf(52))), (28, lift), (36, shake(lift, 1, MAW_TK)),
                                 (44, shake(lift, 0, MAW_TK)), (52, shake(lift, 1, MAW_TK)),
                                 (55, P(lift, right_arm=(-168, 10, -20), torso=(-12, 14, 0))), (59, hurl),
                                 (66, shake(hurl, 0, MAW_TK)), (80, F)]))
    # BARRIER 12|100|10: both palms out, the shell of rubble holds while his hands tremble
    shield = P(F, right_arm=(-82, 40, 10), left_arm=(-82, -40, -10), right_forearm=(-14, 0, 0), left_forearm=(-14, 0, 0),
               right_hand=(-62, 0, 0), left_hand=(-62, 0, 0), right_fingers=rf(-12), left_fingers=lf(-12), torso=(2, 0, 0),
               head=(-8, 0, 0), robe_front=(-4, 0, 0), robe_back=(6, 0, 0))
    keys = [(0, F), (8, P(shield, right_arm=(-96, 50, 12), left_arm=(-96, -50, -12))), (12, shield)]
    for i, t in enumerate(range(30, 111, 20)):
        keys.append((t, shake(P(shield, root=low(0.6 if i % 2 else 0.0)), i, MAW_TK, amp=1.0)))
    keys += [(112, shield), (122, F)]
    out.append(clip('barrier', 122, keys))
    # TOPPLE 30|20|16: tears a piece of the city free overhead, then hurls it down onto the target
    tear = P(F, right_arm=(-168, 0, -20), left_arm=(-168, 0, 20), right_forearm=(-34, 0, 0), left_forearm=(-34, 0, 0),
             right_hand=(-20, 0, 0), left_hand=(-20, 0, 0), right_fingers=rf(46), left_fingers=lf(46), torso=(-14, 0, 0),
             head=(-30, 0, 0), root=low(2.0), robe_front=(-8, 0, 0), robe_back=(10, 0, 0))
    down = P(F, right_arm=(-58, 0, 14), left_arm=(-58, 0, -14), right_forearm=(-8, 0, 0), left_forearm=(-8, 0, 0),
             right_hand=(-30, 0, 0), left_hand=(-30, 0, 0), right_fingers=rf(30), left_fingers=lf(30), torso=(26, 0, 0),
             head=(-20, 0, 0), root=low(0.0), robe_front=(14, 0, 0), robe_back=(20, 0, 0))
    out.append(clip('topple', 66, [(0, F), (12, P(tear, right_fingers=rf(-10), left_fingers=lf(-10))), (18, tear),
                                   (24, shake(tear, 1, MAW_TK, 2.0)), (30, shake(tear, 0, MAW_TK, 2.0)), (34, down), (40, down),
                                   (50, shake(down, 1, MAW_TK)), (66, F)]))
    # BLINK 10|4|6: folds into himself, vanishes, unfolds where he lands
    fold = P(F, right_arm=(-62, -40, -10), left_arm=(-62, 40, 10), right_forearm=(-110, 0, 0), left_forearm=(-110, 0, 0),
             torso=(18, 0, 0), head=(4, 0, 0), right_leg=(-30, 0, 3), left_leg=(-30, 0, -3), right_shin=(60, 0, 0),
             left_shin=(60, 0, 0), robe_front=(-20, 0, 0), root=low(1.5))
    unfold = P(F, right_arm=(-40, 0, 70), left_arm=(-40, 0, -70), right_forearm=(-10, 0, 0), left_forearm=(-10, 0, 0),
               torso=(-8, 0, 0), head=(-12, 0, 0), right_fingers=rf(-12), left_fingers=lf(-12))
    out.append(clip('blink', 20, [(0, F), (8, fold), (10, fold), (12, unfold), (14, unfold), (20, F)]))
    return out


def maw_scenes():
    F = MAW_FLOAT
    out = []
    zero = {b: (0, 0, 0) for b in ('torso', 'head', 'right_arm', 'left_arm')}
    zero['root'] = low(0)
    out.append(clip('hurt', 8, [(0, zero), (2, dict(torso=(-8, 5, 0), head=(-10, 0, 4), right_arm=(-5, 0, 8), left_arm=(-5, 0, -8),
                                                    root=low(0, 1.5))), (8, zero)]))
    jolt = P(F, torso=(-18, 0, 0), head=(-22, 0, 6), right_arm=(-30, 0, 50), left_arm=(-30, 0, -50), right_forearm=(-20, 0, 0),
             left_forearm=(-20, 0, 0), right_fingers=rf(-12), left_fingers=lf(-12), root=low(-1, 3, (-14, 0, 0)),
             robe_front=(-16, 0, 0), robe_back=(-6, 0, 0), right_leg=(-20, 0, 3), left_leg=(-14, 0, -3))
    curl = P(F, torso=(22, 0, 0), head=(8, 0, 0), right_arm=(-10, 10, 12), left_arm=(-10, -10, -12), root=low(-1.5, 1, (10, 0, 0)),
             robe_front=(8, 0, 0))
    out.append(clip('stagger', 30, [(0, F), (4, jolt), (12, curl), (20, P(F, head=(-2, 10, 6))), (30, F)]))
    bowed = P(F, torso=(20, 0, 0), head=(18, 0, 0), right_arm=(-6, 0, 30), left_arm=(-6, 0, -30), right_forearm=(-6, 0, 0),
              left_forearm=(-6, 0, 0), right_fingers=rf(-12), left_fingers=lf(-12), root=low(-4))
    out.append(clip('beam_in', 20, [(0, bowed), (10, P(bowed, torso=(8, 0, 0), head=(4, 0, 0), root=low(-1))),
                                    (16, P(F, head=(-10, 0, 12), right_arm=(-30, 0, 24))), (20, F)]))
    bow = P(F, torso=(24, 0, 0), head=(14, 0, 0), right_arm=(-50, 30, -4), right_forearm=(-90, 0, 0), left_arm=(10, 0, -12),
            left_forearm=(-10, 0, 0))
    out.append(clip('beam_out', 24, [(0, F), (8, bow), (24, P(bow, root=low(2)))]))
    hit = P(F, torso=(-20, 0, 0), head=(-30, 0, 0), right_arm=(-40, 0, 60), left_arm=(-40, 0, -60), right_fingers=rf(-14),
            left_fingers=lf(-14), root=low(0, 1, (-10, 0, 0)))
    falling = P(F, torso=(-10, 0, 0), head=(-20, 0, 10), right_arm=(-150, 0, -30), left_arm=(-120, 0, 24),
                root=low(1, 0, (-60, 0, 0)), right_leg=(-30, 0, 3), left_leg=(-10, 0, -3))
    lying = P(F, root=low(2.5, 0, (-88, 0, 0)), torso=(0, 0, 0), head=(-10, 0, 20), right_arm=(-170, 0, -40),
              left_arm=(-170, 0, 34), right_forearm=(-10, 0, 0), left_forearm=(-14, 0, 0), right_leg=(-10, 0, 6),
              left_leg=(-4, 0, -6), right_shin=(10, 0, 0), left_shin=(24, 0, 0), robe_front=(-30, 0, 0), robe_back=(0, 0, 0),
              right_fingers=rf(20), left_fingers=lf(20))
    out.append(clip('death', 20, [(0, F), (3, hit), (10, falling), (16, lying), (20, lying)]))
    return out


# ================================================================== Cull Obsidian
CULL_STAND = dict(root=low(0), waist=(0, 0, 0), torso=(14, 0, 0), neck=(-4, 0, 0), head=(-12, 0, 0), jaw=(0, 0, 0),
                  right_arm=(-14, 0, 10), right_forearm=(-36, 0, 0), right_hand=(0, 0, 0), weapon=(80, 0, 0),
                  weapon_head=(0, 0, 0), left_arm=(-6, 0, -12), left_forearm=(-24, 0, 0), left_hand=(0, 0, 0),
                  right_leg=(-6, 4, 4), right_shin=(14, 0, 0), right_foot=(-8, 0, 0),
                  left_leg=(6, -4, -4), left_shin=(12, 0, 0), left_foot=(-10, 0, 0))
CULL_HOLD = ('torso', 'head', 'right_arm', 'left_arm', 'right_forearm', 'left_forearm')


def legs_crouch(drop=-4.0):
    return dict(root=low(drop), right_leg=(-30, 4, 4), right_shin=(50, 0, 0), right_foot=(-20, 0, 0),
                left_leg=(-24, -4, -4), left_shin=(44, 0, 0), left_foot=(-20, 0, 0))


def cull_loco():
    F = CULL_STAND
    out = [clip('idle', 50, [(0, F), (25, P(F, torso=(11, 0, 0), head=(-10, 0, 0), jaw=(8, 0, 0), right_arm=(-14, 0, 12),
                                            left_arm=(-6, 0, -14), root=low(0.6))), (50, F)], loop=True)]
    wa = P(F, root=low(-1.2), right_leg=(-26, 4, 4), right_shin=(8, 0, 0), right_foot=(8, 0, 0), left_leg=(20, -4, -4),
           left_shin=(18, 0, 0), left_foot=(-16, 0, 0), waist=(0, -6, 0), torso=(16, 6, 0), right_arm=(6, 0, 10),
           left_arm=(-20, 0, -12))
    wm = P(F, root=low(0.5), right_leg=(-4, 4, 4), right_shin=(10, 0, 0), left_leg=(-10, -4, -4), left_shin=(48, 0, 0),
           left_foot=(-20, 0, 0), waist=(0, 0, 0), torso=(14, 0, 0))
    wb = P(F, root=low(-1.2), left_leg=(-26, -4, -4), left_shin=(8, 0, 0), left_foot=(8, 0, 0), right_leg=(20, 4, 4),
           right_shin=(18, 0, 0), right_foot=(-16, 0, 0), waist=(0, 6, 0), torso=(16, -6, 0), right_arm=(-30, 0, 10),
           left_arm=(4, 0, -12))
    wn = P(F, root=low(0.5), right_leg=(-10, 4, 4), right_shin=(48, 0, 0), right_foot=(-20, 0, 0), left_leg=(-4, -4, -4),
           left_shin=(10, 0, 0), waist=(0, 0, 0), torso=(14, 0, 0))
    out.append(clip('walk', 28, [(0, wa), (7, wm), (14, wb), (21, wn), (28, wa)], loop=True))
    return out


def cull_moves():
    F = CULL_STAND
    out = []
    # SWING 12|6|14: the hammer-axe sweeps right to left
    wind = P(F, torso=(10, 34, 0), waist=(0, 12, 0), head=(-12, -24, 0), right_arm=(-84, 50, 10), right_forearm=(-30, 0, 0),
             weapon=(64, 0, 0), left_arm=(-30, -20, -20), left_forearm=(-40, 0, 0), right_leg=(-10, 4, 4), left_leg=(10, -4, -4))
    strike = P(F, torso=(14, -10, 0), waist=(0, -4, 0), head=(-14, 6, 0), right_arm=(-88, -10, 14), right_forearm=(-10, 0, 0),
               weapon=(84, 0, 0))
    follow = P(F, torso=(16, -34, 0), waist=(0, -12, 0), head=(-14, 26, 0), right_arm=(-76, -64, 10), right_forearm=(-16, 0, 0),
               weapon=(80, 0, 0), left_arm=(-10, 10, -30))
    out.append(clip('swing', 32, [(0, F), (9, wind), (12, shake(wind, 1, CULL_HOLD)), (15, strike), (18, follow),
                                  (24, P(follow, torso=(16, -24, 0), head=(-14, 20, 0), right_arm=(-50, -44, 10),
                                         right_forearm=(-30, 0, 0), weapon=(76, 0, 0))), (32, F)]))
    # SLAM 22|6|20: both hands overhead, the hammer face crashes into the street
    top = P(F, right_arm=(-172, 0, -6), right_forearm=(-40, 0, 0), weapon=(10, 0, 0), left_arm=(-160, 0, 10),
            left_forearm=(-50, 0, 0), torso=(-12, 0, 0), head=(-20, 0, 0), root=low(1.0), jaw=(14, 0, 0),
            right_leg=(-4, 4, 4), left_leg=(4, -4, -4))
    smash = P(F, right_arm=(-62, 10, -4), right_forearm=(-10, 0, 0), weapon=(96, 0, 0), left_arm=(-60, -10, 4),
              left_forearm=(-14, 0, 0), torso=(40, 0, 0), waist=(10, 0, 0), head=(-30, 0, 0), jaw=(10, 0, 0),
              **legs_crouch(-6))
    out.append(clip('slam', 48, [(0, F), (16, top), (22, shake(top, 1, CULL_HOLD)), (25, smash),
                                 (28, P(smash, torso=(42, 0, 0), right_arm=(-50, 10, -4), left_arm=(-48, -10, 4))),
                                 (38, P(F, torso=(26, 0, 0), **legs_crouch(-3))), (48, F)]))
    # CHARGE 20|30|24: paws the ground, head down, runs through whatever stands in the way, skids
    paw = P(F, torso=(36, 0, 0), waist=(8, 0, 0), head=(-30, 0, 0), jaw=(16, 0, 0), right_arm=(10, -10, 24),
            right_forearm=(-30, 0, 0), left_arm=(-20, 10, -30), left_forearm=(-60, 0, 0), root=low(-4),
            right_leg=(-30, 4, 4), right_shin=(50, 0, 0), right_foot=(-20, 0, 0), left_leg=(10, -4, -4),
            left_shin=(40, 0, 0), left_foot=(-30, 0, 0))
    ra = P(F, torso=(40, 0, 0), head=(-34, 0, 0), jaw=(12, 0, 0), root=low(-2, 0, (6, 0, 0)), right_leg=(-50, 4, 4),
           right_shin=(30, 0, 0), left_leg=(36, -4, -4), left_shin=(50, 0, 0), right_arm=(30, 0, 20), right_forearm=(-20, 0, 0),
           left_arm=(-50, 0, -30), left_forearm=(-80, 0, 0))
    rb = P(ra, root=low(-0.5, 0, (6, 0, 0)), right_leg=(36, 4, 4), right_shin=(50, 0, 0), left_leg=(-50, -4, -4),
           left_shin=(30, 0, 0))
    keys = [(0, F), (14, paw), (17, P(paw, left_leg=(30, -4, -4), left_foot=(10, 0, 0))), (20, shake(paw, 1, CULL_HOLD))]
    for i, t in enumerate(range(22, 51, 5)):
        keys.append((t, ra if i % 2 == 0 else rb))
    keys += [(56, P(F, torso=(-6, 0, 0), head=(-16, 0, 0), root=low(-3, 0, (-6, 0, 0)), right_leg=(-40, 4, 4),
                    right_shin=(20, 0, 0), left_leg=(20, -4, -4), left_shin=(30, 0, 0))),
             (64, P(F, torso=(20, 0, 0))), (74, F)]
    out.append(clip('charge', 74, keys))
    # CHAIN 16|24|14: whirls the weapon back, flings the head on its chain, reels the hero in
    whirl = P(F, right_arm=(-150, 30, -20), right_forearm=(-50, 0, 0), weapon=(20, 0, 0), torso=(-4, 28, 0), waist=(0, 10, 0),
              head=(-14, -20, 0), left_arm=(-40, -20, -20))
    fling = P(F, right_arm=(-90, 24, 10), right_forearm=(-4, 0, 0), weapon=(90, 0, 0), torso=(18, -24, 0), waist=(4, -8, 0),
              head=(-16, 20, 0), root=low(-2), right_leg=(-26, 4, 4), right_shin=(30, 0, 0), left_leg=(18, -4, -4))
    reel = P(fling, right_arm=(-70, 0, 14), right_forearm=(-60, 0, 0), torso=(4, 0, 0), head=(-12, 0, 0))
    out.append(clip('chain', 54, [(0, F), (8, P(whirl, right_arm=(-150, -30, -20))), (12, whirl), (16, shake(whirl, 1, CULL_HOLD)),
                                  (19, fling), (26, shake(fling, 0, CULL_HOLD)), (32, reel),
                                  (38, P(reel, right_forearm=(-90, 0, 0), right_arm=(-40, 10, 20), torso=(-6, 10, 0))),
                                  (46, P(F, torso=(10, 0, 0))), (54, F)]))
    # GRAB 12|30|16: both hands seize the hero, hoist him overhead, hurl him (server throws at 12 + 26 = 38)
    reach = P(F, right_arm=(-90, 14, 16), left_arm=(-96, -14, -16), right_forearm=(-14, 0, 0), left_forearm=(-10, 0, 0),
              left_hand=(-30, 0, 0), torso=(28, 0, 0), head=(-24, 0, 0), root=low(-2), right_leg=(-20, 4, 4), right_shin=(26, 0, 0))
    hoist = P(F, right_arm=(-166, 10, -10), left_arm=(-166, -10, 10), right_forearm=(-36, 0, 0), left_forearm=(-36, 0, 0),
              weapon=(30, 0, 0), torso=(-10, 0, 0), head=(-26, 0, 0), jaw=(14, 0, 0), root=low(1.0))
    hurl = P(F, right_arm=(-60, 0, 10), left_arm=(-60, 0, -10), right_forearm=(-10, 0, 0), left_forearm=(-10, 0, 0),
             torso=(34, 0, 0), waist=(6, 0, 0), head=(-26, 0, 0), jaw=(18, 0, 0), **legs_crouch(-3))
    out.append(clip('grab', 58, [(0, F), (10, reach), (12, P(reach, left_hand=(0, 0, 0))), (20, hoist), (28, shake(hoist, 1, CULL_HOLD)),
                                 (34, P(hoist, torso=(-16, 0, 0), right_arm=(-176, 10, -10), left_arm=(-176, -10, 10))), (38, hurl),
                                 (44, shake(hurl, 0, CULL_HOLD)), (58, F)]))
    # ROAR 10|20|10: chest out, arms wide, the jaw drops
    roar = P(F, torso=(-18, 0, 0), waist=(-4, 0, 0), head=(-36, 0, 0), jaw=(34, 0, 0), right_arm=(-40, 0, 60), left_arm=(-40, 0, -60),
             right_forearm=(-50, 0, 0), left_forearm=(-50, 0, 0), root=low(1.0))
    out.append(clip('roar', 40, [(0, F), (8, P(F, torso=(-6, 0, 0), head=(-6, 0, 0), right_arm=(-20, 0, 30), left_arm=(-20, 0, -30),
                                               root=low(0.5))), (12, roar), (20, shake(roar, 1, CULL_HOLD, 2.0)),
                                 (28, shake(roar, 0, CULL_HOLD, 2.0)), (30, roar), (40, F)]))
    # CLASH 18|24|30: braces, rams the heavy hero, locks arms and pushes, shoves him off
    brace = P(F, torso=(30, 0, 0), head=(-26, 0, 0), jaw=(16, 0, 0), right_arm=(-40, -20, 30), left_arm=(-40, 20, -30),
              right_forearm=(-70, 0, 0), left_forearm=(-70, 0, 0), root=low(-4), right_leg=(-30, 4, 4), right_shin=(50, 0, 0),
              left_leg=(20, -4, -4), left_shin=(40, 0, 0))
    lunge = P(F, torso=(34, 0, 0), head=(-30, 0, 0), right_arm=(-96, 10, 10), left_arm=(-96, -10, -10), right_forearm=(-20, 0, 0),
              left_forearm=(-20, 0, 0), right_hand=(-30, 0, 0), left_hand=(-30, 0, 0), root=low(-3, -4), right_leg=(-44, 4, 4),
              right_shin=(30, 0, 0), left_leg=(36, -4, -4), left_shin=(20, 0, 0))
    push = P(lunge, right_forearm=(-50, 0, 0), left_forearm=(-50, 0, 0), torso=(38, 0, 0))
    shove = P(F, right_arm=(-80, -30, 30), left_arm=(-80, 30, -30), right_forearm=(-6, 0, 0), left_forearm=(-6, 0, 0),
              torso=(20, 0, 0), head=(-22, 0, 0), root=low(-2, -2), jaw=(20, 0, 0))
    out.append(clip('clash', 72, [(0, F), (14, brace), (18, shake(brace, 1, CULL_HOLD)), (22, lunge), (26, push),
                                  (32, shake(push, 1, CULL_HOLD, 2.0)), (38, shake(push, 0, CULL_HOLD, 2.0)),
                                  (42, shake(push, 1, CULL_HOLD, 2.0)), (46, shove), (56, P(F, torso=(20, 0, 0))), (72, F)]))
    return out


def cull_scenes():
    F = CULL_STAND
    out = []
    zero = {b: (0, 0, 0) for b in ('torso', 'head', 'right_arm', 'left_arm')}
    zero['root'] = low(0)
    out.append(clip('hurt', 8, [(0, zero), (2, dict(torso=(-6, 4, 0), head=(-8, 0, 4), right_arm=(-4, 0, 6), left_arm=(-4, 0, -6),
                                                    root=low(0, 1.0))), (8, zero)]))
    jolt = P(F, torso=(-14, 0, 0), head=(-20, 0, 0), jaw=(14, 0, 0), right_arm=(-30, 0, 40), left_arm=(-30, 0, -40),
             root=low(-1, 3, (-6, 0, 0)), right_leg=(16, 4, 4), left_leg=(-12, -4, -4))
    down = P(F, torso=(34, 0, 0), head=(0, 0, 0), left_arm=(-60, 0, -20), left_forearm=(-10, 0, 0), **legs_crouch(-4))
    out.append(clip('stagger', 30, [(0, F), (4, jolt), (12, down), (22, P(F, torso=(22, 0, 0), head=(-10, 12, 0))), (30, F)]))
    landed = P(F, torso=(40, 0, 0), head=(-20, 0, 0), left_arm=(-50, 0, -10), left_forearm=(-10, 0, 0), **legs_crouch(-6))
    out.append(clip('beam_in', 20, [(0, landed), (12, landed), (16, P(F, torso=(6, 0, 0), head=(-26, 0, 0), jaw=(20, 0, 0))),
                                    (20, F)]))
    up = P(F, torso=(4, 0, 0), head=(-30, 0, 0), jaw=(0, 0, 0), right_arm=(-6, 0, 8), left_arm=(-2, 0, -8))
    out.append(clip('beam_out', 24, [(0, F), (8, up), (24, up)]))
    hit = P(F, torso=(-14, 0, 0), head=(-24, 0, 0), jaw=(30, 0, 0), right_arm=(-30, 0, 40), left_arm=(-30, 0, -40), root=low(-1, 2))
    knees = P(F, torso=(20, 0, 0), head=(10, 0, 0), root=low(-8), right_leg=(0, 4, 4), right_shin=(88, 0, 0), right_foot=(10, 0, 0),
              left_leg=(0, -4, -4), left_shin=(88, 0, 0), left_foot=(10, 0, 0), right_arm=(-10, 0, 20), left_arm=(-10, 0, -20),
              weapon=(110, 0, 0))
    face_down = P(knees, root=low(-6, -2, (80, 0, 0)), torso=(10, 0, 0), head=(-20, 0, 0), right_arm=(-160, 0, 30),
                  left_arm=(-170, 0, -30), right_shin=(20, 0, 0), left_shin=(24, 0, 0), right_leg=(0, 4, 4), left_leg=(0, -4, -4))
    out.append(clip('death', 20, [(0, F), (4, hit), (10, knees), (14, knees), (20, face_down)]))
    return out


# ================================================================== output
EXPECTED = {
    'maw': ('idle', 'glide', 'spear', 'volley', 'grab', 'barrier', 'topple', 'blink', 'hurt', 'stagger', 'beam_in', 'beam_out', 'death'),
    'cull': ('idle', 'walk', 'swing', 'slam', 'charge', 'chain', 'grab', 'roar', 'clash', 'hurt', 'stagger', 'beam_in', 'beam_out',
             'death'),
}


def build():
    return {'maw': maw_loco() + maw_moves() + maw_scenes(), 'cull': cull_loco() + cull_moves() + cull_scenes()}


def main():
    clips = build()
    for who, files in FILES.items():
        by_name = {c.name: c for c in clips[who]}
        assert tuple(by_name) == EXPECTED[who], list(by_name)
        listed = [n for names in files.values() for n in names]
        assert sorted(listed) == sorted(by_name), (who, sorted(set(by_name) - set(listed)))
        for rel, names in files.items():
            size = write_anims(rel, [by_name[n] for n in names])
            assert size < 46000, '%s is %d bytes' % (rel, size)
            print('%s: %d clips, %d bytes' % (rel, len(names), size))


def preview(argv):
    import json
    import numpy as np
    from PIL import Image
    import jinwoo_preview as jp
    root = os.path.join(os.path.dirname(__file__), '..', '..')
    res = os.path.join(root, 'viltrumitecore/src/main/resources/assets/viltrumitecore')
    plain = os.path.join(root, 'build/jinwoo_preview/textures/entity/thanos')
    out = os.path.join(root, 'build/thanos-assets')
    os.makedirs(out, exist_ok=True)
    who, name = argv[0], argv[1]
    n = int(argv[2]) if len(argv) > 2 else 6
    yaw = float(argv[3]) if len(argv) > 3 else -35
    model = 'ebony_maw' if who == 'maw' else 'cull_obsidian'
    geo = jp.load(os.path.join(res, 'geo/thanos/%s.geo.json' % model))
    tex = np.array(Image.open(os.path.join(plain, model + '.png')).convert('RGBA'))
    glow = np.array(Image.open(os.path.join(plain, model + '_glow.png')).convert('RGBA'))
    anims = {}
    for rel in FILES[who]:
        anims.update(json.load(open(os.path.join(res, rel)))['animations'])
    a = anims[name]
    length = a['animation_length']
    frames = []
    for i in range(n):
        t = length * i / max(1, n - 1)
        frames.append(('%s %.2fs' % (name, t), jp.pose_from_clip(a, t), yaw, -6))
    h = 2.4 if who == 'maw' else 3.4
    jp.sheet(geo, tex, glow, frames, size=260, cols=min(n, 6), center=(0, h / 2, 0), scale=260 / (h * 1.5)).save(
        os.path.join(out, '%s_%s.png' % (who, name)))
    print(os.path.join(out, '%s_%s.png' % (who, name)))


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'preview':
        preview(sys.argv[2:])
    else:
        main()
