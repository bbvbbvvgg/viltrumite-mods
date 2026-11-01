"""Third-person and first-person clips of the Red Hulk (own work, CC0).

Every timed move is authored as anticipation -> acceleration -> impact -> follow-through -> recovery; lengths and
contact ticks are MOVES below = RedHulkMove on the server (RedHulkClipsTest compares the JSON with the enum), so the
server clock (actionElapsed) drives the clip in both views. Conventions (jinwoo_poses): arm X- = raise forward,
arm Y+ sweeps to the right, right arm Z+ / left arm Z- = outward, forearm X- = elbow bend, torso X+ = lean
forward, Y+ = turn right, leg X- = forward, shin X+ = knee bend, jaw X+ = open, lip_upper X- = snarl (lift),
left_brow Z- / right_brow Z+ = brows pulled down (anger), the opposite = raised (pain).
Outputs animations/red_hulk/red_hulk_{stances,combat,skills,ult,face,fp}.animation.json."""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_clips import clip  # noqa: E402
from jinwoo_geo import write_anims  # noqa: E402
from jinwoo_poses import P, low, mirror  # noqa: E402,F401

# name: (length ticks, contact tick or -1) -- keep in sync with RedHulkMove
MOVES = {
    'jab_l': (9, 4), 'jab_r': (10, 5), 'hook': (12, 6), 'uppercut': (13, 7), 'hammer': (20, 12),
    'grab': (9, 4), 'hold': (20, -1), 'throw': (12, 6), 'grab_miss': (10, -1),
    'clap': (16, 9), 'seismic': (26, 15), 'leap_crouch': (10, 10), 'leap_air': (20, -1), 'leap_land': (16, 0),
    'blast': (32, 12), 'fury': (24, 10), 'ult_charge': (60, -1), 'ult_release': (18, 2), 'ult_recover': (50, -1),
    'vent': (30, 6), 'stagger': (14, -1), 'landing': (12, 0), 'enter': (30, 12),
}

FIST = {'right_fingers': (0, 0, -95), 'left_fingers': (0, 0, 95), 'right_thumb': (0, 0, -50), 'left_thumb': (0, 0, 50)}
HALF = {'right_fingers': (0, 0, -45), 'left_fingers': (0, 0, 45), 'right_thumb': (0, 0, -20), 'left_thumb': (0, 0, 20)}
OPEN = {'right_fingers': (0, 0, 8), 'left_fingers': (0, 0, -8), 'right_thumb': (0, 0, 10), 'left_thumb': (0, 0, -10)}
FACE0 = {'jaw': (0, 0, 0), 'lip_upper': (0, 0, 0), 'left_brow': (0, 0, 0), 'right_brow': (0, 0, 0)}
ROAR = {'jaw': (34, 0, 0), 'lip_upper': (-16, 0, 0), 'left_brow': (0, 0, -9), 'right_brow': (0, 0, 9)}
GRIT = {'jaw': (2, 0, 0), 'lip_upper': (-12, 0, 0), 'left_brow': (0, 0, -8), 'right_brow': (0, 0, 8)}
PANT = {'jaw': (14, 0, 0), 'lip_upper': (-4, 0, 0), 'left_brow': (0, 0, 3), 'right_brow': (0, 0, -3)}


def add(pose, **delta):
    out = dict(pose)
    for k, v in delta.items():
        base = out.get(k, (0, 0, 0))
        if isinstance(base, dict):
            nb = dict(base)
            if 'rot' in v:
                nb['rot'] = tuple(a + b for a, b in zip(base.get('rot', (0, 0, 0)), v['rot']))
            if 'pos' in v:
                nb['pos'] = tuple(a + b for a, b in zip(base.get('pos', (0, 0, 0)), v['pos']))
            out[k] = nb
        else:
            out[k] = tuple(a + b for a, b in zip(base, v))
    return out


def rootp(y=0.0, z=0.0, rot=(0, 0, 0)):
    return {'rot': rot, 'pos': (0, y, z)}


# ---- stances -----------------------------------------------------------------------------------------
LEGS = dict(right_leg=(-4, 6, 7), right_shin=(10, 0, -4), left_leg=(-4, -6, -7), left_shin=(10, 0, 4))
CALM = P(root=rootp(-0.6), waist=(4, 0, 0), torso=(12, 0, 0), head=(-14, 0, 0),
         right_arm=(-6, 4, 9), right_forearm=(-18, 0, 0), right_hand=(0, 0, 0),
         left_arm=(-6, -4, -9), left_forearm=(-18, 0, 0), left_hand=(0, 0, 0), **LEGS, **HALF)
RAGE = P(CALM, root=rootp(-1.6), waist=(8, 0, 0), torso=(20, 0, 0), head=(-24, 0, 0),
         right_arm=(-14, 6, 17), right_forearm=(-38, 0, 0), left_arm=(-14, -6, -17), left_forearm=(-38, 0, 0),
         right_leg=(-10, 8, 9), right_shin=(22, 0, -4), left_leg=(-10, -8, -9), left_shin=(22, 0, 4), **FIST)
GUARD = P(root=rootp(-1.4), waist=(6, 10, 0), torso=(16, 8, 0), head=(-20, -14, 0),
          right_arm=(-42, -16, 14), right_forearm=(-96, 0, 0), right_hand=(0, 0, 0),
          left_arm=(-54, 18, -12), left_forearm=(-102, 0, 0), left_hand=(0, 0, 0),
          right_leg=(12, 14, 8), right_shin=(22, 0, -2), left_leg=(-22, 10, -8), left_shin=(20, 0, 2), **FIST)
CROUCH = P(RAGE, root=rootp(-4.0), waist=(18, 0, 0), torso=(24, 0, 0), head=(-34, 0, 0),
           right_leg=(-44, 10, 10), right_shin=(76, 0, -4), left_leg=(-44, -10, -10), left_shin=(76, 0, 4),
           right_arm=(-22, 4, 14), right_forearm=(-30, 0, 0), left_arm=(-22, -4, -14), left_forearm=(-30, 0, 0))
AIR_UP = P(CALM, root=rootp(0), waist=(-4, 0, 0), torso=(4, 0, 0), head=(-8, 0, 0),
           right_arm=(-150, 10, 26), right_forearm=(-34, 0, 0), left_arm=(-150, -10, -26), left_forearm=(-34, 0, 0),
           right_leg=(-30, 4, 6), right_shin=(60, 0, 0), left_leg=(-14, -4, -6), left_shin=(40, 0, 0), **FIST)
AIR_DOWN = P(AIR_UP, torso=(14, 0, 0), head=(-18, 0, 0), right_arm=(-60, 10, 40), right_forearm=(-50, 0, 0),
             left_arm=(-60, -10, -40), left_forearm=(-50, 0, 0), right_leg=(-20, 6, 8), right_shin=(30, 0, 0),
             left_leg=(-10, -6, -8), left_shin=(24, 0, 0))
SWIM_A = P(CALM, root=rootp(0), waist=(0, 0, 0), torso=(0, 0, 0), head=(-60, 0, 0),
           right_arm=(-170, 0, 30), right_forearm=(0, 0, 0), left_arm=(-40, 0, -80), left_forearm=(-20, 0, 0),
           right_leg=(10, 0, 0), right_shin=(10, 0, 0), left_leg=(-10, 0, 0), left_shin=(10, 0, 0), **HALF)
SWIM_B = P(SWIM_A, right_arm=(-40, 0, 80), right_forearm=(-20, 0, 0), left_arm=(-170, 0, -30), left_forearm=(0, 0, 0),
           right_leg=(-10, 0, 0), left_leg=(10, 0, 0))


def stances():
    out = []
    for name, base, ticks, depth in (('idle', CALM, 64, 1.0), ('idle_rage', RAGE, 36, 1.6), ('idle_combat', GUARD, 40, 1.2)):
        inhale = add(base, torso=(-2.2 * depth, 0, 0), head=(1.6 * depth, 0, 0), right_arm=(0, 0, 1.8 * depth),
                     left_arm=(0, 0, -1.8 * depth), right_forearm=(-2 * depth, 0, 0), left_forearm=(-2 * depth, 0, 0))
        inhale['root'] = {'rot': (0, 0, 0), 'pos': (0, base['root']['pos'][1] + 0.35 * depth, 0)}
        k = [(0, dict(base, torso={'rot': base['torso'], 'scale': (1, 1, 1)})),
             (int(ticks * 0.45), dict(inhale, torso={'rot': inhale['torso'], 'scale': (1.0 + 0.025 * depth, 1.0 + 0.012 * depth, 1.0 + 0.03 * depth)})),
             (int(ticks * 0.6), dict(inhale, torso={'rot': inhale['torso'], 'scale': (1.0 + 0.025 * depth, 1.0 + 0.012 * depth, 1.0 + 0.03 * depth)})),
             (ticks, dict(base, torso={'rot': base['torso'], 'scale': (1, 1, 1)}))]
        out.append(clip(name, ticks, k, loop=True))
    out.append(clip('crouch', 40, [(0, CROUCH), (20, add(CROUCH, torso=(-2, 0, 0), head=(2, 0, 0))), (40, CROUCH)], loop=True))
    out.append(clip('air_up', 20, [(0, AIR_UP), (10, add(AIR_UP, right_arm=(-6, 0, 4), left_arm=(-6, 0, -4))), (20, AIR_UP)], loop=True))
    out.append(clip('air_down', 20, [(0, AIR_DOWN), (10, add(AIR_DOWN, right_arm=(0, 0, 8), left_arm=(0, 0, -8))), (20, AIR_DOWN)], loop=True))
    out.append(clip('swim', 24, [(0, SWIM_A), (12, SWIM_B), (24, SWIM_A)], loop=True))
    # heavy walk (one gait cycle): heel strike drops the whole mass (root -), the passing pose lifts it; the
    # shoulders roll against the hips, the arms swing heavy with the forearms lagging behind.
    def walk_key(sign, phase, base=CALM):
        if phase == 'strike':
            return P(base, root=rootp(-1.3, 0, (0, 0, 2.5 * sign)), waist=(6, -7 * sign, 0), torso=(14, 9 * sign, -2 * sign),
                     head=(-16, -6 * sign, 2 * sign),
                     right_leg=(-28 * sign + 0, 6, 7), right_shin=(8 if sign > 0 else 30, 0, -4),
                     left_leg=(26 * sign, -6, -7), left_shin=(30 if sign > 0 else 8, 0, 4),
                     right_arm=(26 * sign, 4, 10), right_forearm=(-14 - 14 * max(0, sign), 0, 0),
                     left_arm=(-26 * sign, -4, -10), left_forearm=(-14 - 14 * max(0, -sign), 0, 0))
        return P(base, root=rootp(0.5, 0, (0, 0, 0)), waist=(4, 0, 0), torso=(12, 0, 0), head=(-14, 0, 0),
                 right_leg=(-2 if sign > 0 else 4, 6, 7), right_shin=(6 if sign > 0 else 48, 0, -4),
                 left_leg=(4 if sign > 0 else -2, -6, -7), left_shin=(48 if sign > 0 else 6, 0, 4),
                 right_arm=(4, 4, 9), right_forearm=(-26, 0, 0), left_arm=(4, -4, -9), left_forearm=(-26, 0, 0))
    out.append(clip('walk', 28, [(0, walk_key(1, 'strike')), (7, walk_key(1, 'pass')), (14, walk_key(-1, 'strike')),
                                 (21, walk_key(-1, 'pass')), (28, walk_key(1, 'strike'))], loop=True))
    out.append(clip('walk_rage', 28, [(0, walk_key(1, 'strike', RAGE)), (7, walk_key(1, 'pass', RAGE)), (14, walk_key(-1, 'strike', RAGE)),
                                      (21, walk_key(-1, 'pass', RAGE)), (28, walk_key(1, 'strike', RAGE))], loop=True))
    # charge: chest far forward, fists pumping, long bounding strides
    def run_key(sign, phase):
        if phase == 'push':
            return P(RAGE, root=rootp(-1.0, 0, (0, 0, 3 * sign)), waist=(16, -9 * sign, 0), torso=(30, 12 * sign, 0), head=(-36, -8 * sign, 0),
                     right_leg=(-62 * sign if sign > 0 else 42, 6, 6), right_shin=(26 if sign > 0 else 84, 0, -3),
                     left_leg=(42 if sign > 0 else -62, -6, -6), left_shin=(84 if sign > 0 else 26, 0, 3),
                     right_arm=(52 * sign, 6, 16), right_forearm=(-60 if sign > 0 else -96, 0, 0),
                     left_arm=(-52 * sign, -6, -16), left_forearm=(-96 if sign > 0 else -60, 0, 0), **FIST)
        return P(RAGE, root=rootp(1.2, 0, (0, 0, 0)), waist=(14, 0, 0), torso=(28, 0, 0), head=(-34, 0, 0),
                 right_leg=(-12 if sign > 0 else 8, 6, 6), right_shin=(16 if sign > 0 else 100, 0, -3),
                 left_leg=(8 if sign > 0 else -12, -6, -6), left_shin=(100 if sign > 0 else 16, 0, 3),
                 right_arm=(0, 6, 18), right_forearm=(-80, 0, 0), left_arm=(0, -6, -18), left_forearm=(-80, 0, 0), **FIST)
    out.append(clip('run', 16, [(0, run_key(1, 'push')), (4, run_key(1, 'fly')), (8, run_key(-1, 'push')), (12, run_key(-1, 'fly')),
                                (16, run_key(1, 'push'))], loop=True))
    # one-shot overlays the client plays on its own events: jump take-off, landing squash
    out.append(clip('jump', 6, [(0, CALM), (2, P(CROUCH, root=rootp(-2.5))), (6, AIR_UP)]))
    land = P(CROUCH, root=rootp(-3.4), torso=(30, 0, 0), right_arm=(-30, 6, 30), left_arm=(-30, -6, -30))
    out.append(clip('land', 10, [(0, land), (3, P(land, root=rootp(-3.8))), (10, CALM)]))
    # hurt flinch (overlay while hurtTime runs): the torso recoils, the head snaps back, brows go up
    hurt = add(GUARD, torso=(-14, -10, 6), head=(16, 8, 0), right_arm=(10, 0, 14), left_arm=(10, 0, -14))
    hurt.update(jaw=(10, 0, 0), left_brow=(0, 0, 6), right_brow=(0, 0, -6))
    out.append(clip('hurt', 10, [(0, dict(GUARD, **FACE0)), (2, hurt), (10, dict(GUARD, **GRIT))]))
    return out


# ---- combat (LMB chain + grab / throw) -------------------------------------------------------------------
def combat():
    out = []
    G = dict(GUARD, **GRIT)
    # 1 left jab: shoulder pulled back -> snap forward with the hips -> hold -> back to guard
    j1a = add(G, torso=(2, -12, 0), waist=(0, -6, 0), left_arm=(18, 4, 0), left_forearm=(-14, 0, 0))
    j1b = P(G, root=rootp(-1.6, -1.2), waist=(8, 18, 0), torso=(20, 26, 0), head=(-22, -30, 0),
            left_arm=(-90, -8, 0), left_forearm=(-6, 0, 0), right_arm=(-50, -20, 18), right_forearm=(-104, 0, 0),
            left_leg=(-30, 10, -8), left_shin=(22, 0, 2))
    out.append(clip('jab_l', 9, [(0, G), (2, j1a), (4, j1b), (6, add(j1b, left_arm=(-2, 0, 0))), (9, G)]))
    # 2 right cross: deep wind-up behind the shoulder, the whole torso turns into it
    j2a = add(G, torso=(0, -22, 0), waist=(0, -10, 0), right_arm=(30, 26, 22), right_forearm=(-16, 0, 0), head=(0, 14, 0))
    j2b = P(G, root=rootp(-1.8, -1.6), waist=(10, -20, 0), torso=(22, -34, 0), head=(-22, 28, 0),
            right_arm=(-92, 10, -2), right_forearm=(-4, 0, 0), left_arm=(-40, 20, -16), left_forearm=(-110, 0, 0),
            right_leg=(18, 14, 8), right_shin=(10, 0, -2))
    out.append(clip('jab_r', 10, [(0, G), (3, j2a), (5, j2b), (7, add(j2b, right_arm=(-3, 0, 0))), (10, G)]))
    # 3 left hook: the arm swings wide, the torso whips round, follow-through across the body
    h_a = add(G, torso=(0, -26, 0), waist=(0, -10, 0), left_arm=(10, -60, -30), left_forearm=(10, 0, 0))
    h_b = P(G, root=rootp(-1.8, -0.8), waist=(10, 20, 0), torso=(22, 38, 0), head=(-22, -34, 0),
            left_arm=(-86, 40, -18), left_forearm=(-64, 0, 0), right_arm=(-40, -10, 22), right_forearm=(-108, 0, 0))
    h_c = add(h_b, torso=(0, 12, 0), left_arm=(4, 26, 6))
    out.append(clip('hook', 12, [(0, G), (3, h_a), (5, add(h_a, left_arm=(-30, 40, 0), torso=(0, 30, 0))), (6, h_b), (8, h_c), (12, G)]))
    # 4 right uppercut: dip low, then drive up from the legs onto the toes
    u_a = P(G, root=rootp(-3.4), waist=(14, -10, 0), torso=(30, -16, 0), head=(-30, 12, 0),
            right_arm=(30, -6, 22), right_forearm=(-96, 0, 0), right_leg=(-6, 14, 8), right_shin=(52, 0, -2),
            left_leg=(-34, 10, -8), left_shin=(48, 0, 2))
    u_b = P(G, root=rootp(1.0, -0.6), waist=(-4, -14, 0), torso=(-8, -22, 0), head=(-30, 18, 0),
            right_arm=(-168, -6, -4), right_forearm=(-36, 0, 0), left_arm=(-30, 20, -20), left_forearm=(-104, 0, 0),
            right_leg=(12, 14, 8), right_shin=(4, 0, -2), left_leg=(-20, 10, -8), left_shin=(6, 0, 2))
    out.append(clip('uppercut', 13, [(0, G), (4, u_a), (6, add(u_a, root={'pos': (0, 1.8, 0)}, right_arm=(-80, 0, 0))), (7, u_b),
                                     (9, add(u_b, right_arm=(6, 0, 0))), (13, G)]))
    # 5 two-hand hammer: both fists lifted high behind the head, the body arches back, then the whole mass
    # comes down: a deep squat at the impact, the shock held, a slow recovery
    hm_up = P(G, root=rootp(0.8, 1.0), waist=(-6, 0, 0), torso=(-16, 0, 0), head=(-26, 0, 0),
              right_arm=(-176, -18, 4), right_forearm=(-56, 0, 0), left_arm=(-176, 18, -4), left_forearm=(-56, 0, 0),
              right_leg=(4, 8, 9), right_shin=(8, 0, -4), left_leg=(-6, -8, -9), left_shin=(8, 0, 4), **ROAR)
    hm_dn = P(G, root=rootp(-4.6, -1.6), waist=(18, 0, 0), torso=(46, 0, 0), head=(-46, 0, 0),
              right_arm=(-44, -12, 2), right_forearm=(-12, 0, 0), left_arm=(-44, 12, -2), left_forearm=(-12, 0, 0),
              right_leg=(-44, 10, 10), right_shin=(70, 0, -4), left_leg=(-30, -10, -10), left_shin=(66, 0, 4), **GRIT)
    out.append(clip('hammer', 20, [(0, G), (4, add(hm_up, torso=(10, 0, 0), right_arm=(40, 0, 0), left_arm=(40, 0, 0))), (8, hm_up),
                                   (10, add(hm_up, torso=(30, 0, 0), right_arm=(70, 0, 0), left_arm=(70, 0, 0))), (12, hm_dn),
                                   (15, add(hm_dn, root={'pos': (0, -0.4, 0)})), (20, G)]))
    # grab: reach with the right hand open, the fingers close on the target, pulled in to the chest
    g_a = add(G, torso=(-4, -12, 0), right_arm=(10, 20, 16), right_forearm=(-30, 0, 0))
    g_b = P(G, root=rootp(-2.0, -1.8), waist=(10, -14, 0), torso=(26, -20, 0), head=(-26, 16, 0),
            right_arm=(-86, -6, -2), right_forearm=(-8, 0, 0), left_arm=(-40, 10, -26), left_forearm=(-80, 0, 0),
            right_leg=(16, 14, 8), right_shin=(14, 0, -2), left_leg=(-32, 10, -8), left_shin=(28, 0, 2), **OPEN)
    hold = P(G, root=rootp(-1.2), waist=(6, -8, 0), torso=(14, -12, 0), head=(-18, 10, 0),
             right_arm=(-96, -14, 6), right_forearm=(-34, 0, 0), left_arm=(-46, 16, -18), left_forearm=(-96, 0, 0), **FIST)
    out.append(clip('grab', 9, [(0, G), (2, g_a), (4, g_b), (6, dict(g_b, **FIST)), (9, hold)]))
    out.append(clip('hold', 20, [(0, hold), (5, add(hold, right_arm=(-3, 2, 0), torso=(-1, 0, 0))), (10, add(hold, right_arm=(2, -2, 0))),
                                 (15, add(hold, right_arm=(-2, 0, 1))), (20, hold)], loop=True))
    t_a = P(hold, root=rootp(-1.0, 1.2), waist=(-4, -22, 0), torso=(-10, -34, 0), head=(-14, 26, 0),
            right_arm=(-176, 26, 16), right_forearm=(-66, 0, 0), left_arm=(-70, -10, -30), left_forearm=(-30, 0, 0), **ROAR)
    t_b = P(hold, root=rootp(-2.2, -2.0), waist=(14, 18, 0), torso=(34, 26, 0), head=(-34, -20, 0),
            right_arm=(-62, -16, -6), right_forearm=(-8, 0, 0), left_arm=(-10, 0, -30), left_forearm=(-60, 0, 0),
            right_leg=(18, 14, 8), right_shin=(8, 0, -2), left_leg=(-38, 10, -8), left_shin=(32, 0, 2), **OPEN)
    out.append(clip('throw', 12, [(0, hold), (3, t_a), (5, add(t_a, right_arm=(60, 0, 0), torso=(20, 20, 0))), (6, t_b),
                                  (8, add(t_b, right_arm=(16, 0, 0))), (12, G)]))
    miss = add(g_b, torso=(10, 0, 0), root={'pos': (0, -0.6, -0.4)})
    out.append(clip('grab_miss', 10, [(0, G), (2, g_a), (4, g_b), (6, dict(miss, **FIST)), (10, G)]))
    return out


# ---- skills ------------------------------------------------------------------------------------------
def skills():
    out = []
    G = dict(GUARD, **GRIT)
    # Thermal Clap: arms thrown wide and back (chest open, inhale), then slammed together in front
    c_open = P(G, root=rootp(0.4, 0.8), waist=(-4, 0, 0), torso=(-10, 0, 0), head=(-20, 0, 0),
               right_arm=(-84, 62, 6), right_forearm=(-14, 0, 0), left_arm=(-84, -62, -6), left_forearm=(-14, 0, 0),
               right_leg=(0, 8, 9), right_shin=(10, 0, -4), left_leg=(-6, -8, -9), left_shin=(10, 0, 4), **OPEN, **ROAR)
    c_shut = P(G, root=rootp(-2.4, -1.6), waist=(12, 0, 0), torso=(26, 0, 0), head=(-30, 0, 0),
               right_arm=(-88, -10, 0), right_forearm=(-6, 0, 0), left_arm=(-88, 10, 0), left_forearm=(-6, 0, 0),
               right_leg=(10, 10, 9), right_shin=(30, 0, -4), left_leg=(-30, -10, -9), left_shin=(30, 0, 4), **OPEN, **GRIT)
    out.append(clip('clap', 16, [(0, G), (4, add(c_open, right_arm=(0, 10, 0), left_arm=(0, -10, 0))), (6, c_open),
                                 (8, add(c_open, right_arm=(0, -50, 0), left_arm=(0, 50, 0), torso=(20, 0, 0))), (9, c_shut),
                                 (12, add(c_shut, root={'pos': (0, -0.4, 0)})), (16, G)]))
    # Seismic Slam: fists locked overhead, a short hop, both fists driven into the ground in front
    s_up = P(G, root=rootp(1.6, 0.6), waist=(-6, 0, 0), torso=(-18, 0, 0), head=(-30, 0, 0),
             right_arm=(-178, -6, 2), right_forearm=(-28, 0, 0), left_arm=(-178, 6, -2), left_forearm=(-28, 0, 0),
             right_leg=(-6, 8, 9), right_shin=(12, 0, -4), left_leg=(-6, -8, -9), left_shin=(12, 0, 4), **ROAR)
    s_top = add(s_up, root={'pos': (0, 2.4, 0)}, right_leg=(-30, 0, 0), right_shin=(50, 0, 0), left_leg=(-30, 0, 0), left_shin=(50, 0, 0))
    s_dn = P(G, root=rootp(-6.0, -2.2), waist=(22, 0, 0), torso=(54, 0, 0), head=(-56, 0, 0),
             right_arm=(-30, -10, 4), right_forearm=(-4, 0, 0), left_arm=(-30, 10, -4), left_forearm=(-4, 0, 0),
             right_leg=(-56, 12, 12), right_shin=(96, 0, -4), left_leg=(-40, -12, -12), left_shin=(90, 0, 4), **GRIT)
    out.append(clip('seismic', 26, [(0, G), (6, s_up), (10, s_top), (13, add(s_top, torso=(34, 0, 0), right_arm=(80, 0, 0), left_arm=(80, 0, 0))),
                                    (15, s_dn), (19, add(s_dn, root={'pos': (0, -0.5, 0)})), (26, G)]))
    # Destruction Leap: wind up in a deep crouch, arms swung back; airborne arms overhead; a three-point landing
    l_c = P(G, root=rootp(-5.4, 1.0), waist=(24, 0, 0), torso=(40, 0, 0), head=(-46, 0, 0),
            right_arm=(44, 6, 22), right_forearm=(-20, 0, 0), left_arm=(44, -6, -22), left_forearm=(-20, 0, 0),
            right_leg=(-64, 10, 10), right_shin=(104, 0, -4), left_leg=(-60, -10, -10), left_shin=(100, 0, 4), **GRIT)
    l_go = P(G, root=rootp(1.6, -1.0), waist=(6, 0, 0), torso=(12, 0, 0), head=(-20, 0, 0),
             right_arm=(-150, 10, 20), right_forearm=(-20, 0, 0), left_arm=(-150, -10, -20), left_forearm=(-20, 0, 0),
             right_leg=(12, 6, 8), right_shin=(6, 0, -4), left_leg=(20, -6, -8), left_shin=(4, 0, 4), **ROAR)
    out.append(clip('leap_crouch', 10, [(0, G), (3, add(l_c, root={'pos': (0, 2.4, 0)})), (8, l_c), (10, l_go)]))
    air = P(G, root=rootp(0), waist=(-6, 0, 0), torso=(-2, 0, 0), head=(-14, 0, 0),
            right_arm=(-168, 14, 16), right_forearm=(-40, 0, 0), left_arm=(-168, -14, -16), left_forearm=(-40, 0, 0),
            right_leg=(-48, 6, 8), right_shin=(80, 0, -4), left_leg=(-24, -6, -8), left_shin=(56, 0, 4), **ROAR)
    out.append(clip('leap_air', 20, [(0, air), (10, add(air, right_arm=(8, 0, 6), left_arm=(8, 0, -6), right_leg=(6, 0, 0))), (20, air)], loop=True))
    l_land = P(G, root=rootp(-6.6, -1.4), waist=(26, 0, 0), torso=(48, 0, 0), head=(-52, 0, 0),
               right_arm=(-36, -6, 8), right_forearm=(-6, 0, 0), left_arm=(-20, -10, -54), left_forearm=(-30, 0, 0),
               right_leg=(-70, 12, 12), right_shin=(110, 0, -4), left_leg=(-10, -12, -16), left_shin=(118, 0, 4), **GRIT)
    out.append(clip('leap_land', 16, [(0, l_land), (2, add(l_land, root={'pos': (0, -0.6, 0)})), (8, l_land), (16, G)]))
    # Heat Blast: inhale (the chest swells, fists pulled back), hold the charge, both palms thrust forward with a roar
    b_in = P(G, root=rootp(-0.6, 1.0), waist=(-4, 0, 0), torso={'rot': (-14, 0, 0), 'scale': (1.06, 1.03, 1.08)}, head=(-24, 0, 0),
             right_arm=(36, 4, 26), right_forearm=(-80, 0, 0), left_arm=(36, -4, -26), left_forearm=(-80, 0, 0), **FIST, **GRIT)
    b_out = P(G, root=rootp(-2.0, -1.4), waist=(10, 0, 0), torso={'rot': (22, 0, 0), 'scale': (1.0, 1.0, 1.0)}, head=(-30, 0, 0),
              right_arm=(-92, -12, 0), right_forearm=(-6, 0, 0), right_hand=(-30, 0, 0), left_arm=(-92, 12, 0), left_forearm=(-6, 0, 0),
              left_hand=(-30, 0, 0), right_leg=(12, 12, 9), right_shin=(26, 0, -4), left_leg=(-30, -12, -9), left_shin=(30, 0, 4),
              **OPEN, **ROAR)
    g0 = dict(G, torso={'rot': G['torso'], 'scale': (1, 1, 1)}, right_hand=(0, 0, 0), left_hand=(0, 0, 0))
    out.append(clip('blast', 32, [(0, g0), (6, b_in), (9, add(b_in, right_arm=(1, 0, 1))), (11, add(b_in, right_arm=(-1, 0, -1), left_arm=(-1, 0, 1))),
                                  (12, b_out), (15, add(b_out, root={'pos': (0, 0, 0.5)}, right_arm=(3, 0, 0), left_arm=(3, 0, 0))),
                                  (18, b_out), (21, add(b_out, root={'pos': (0, 0, 0.5)}, right_arm=(3, 0, 0), left_arm=(3, 0, 0))),
                                  (24, b_out), (27, add(b_out, torso={'rot': (-6, 0, 0)}, right_arm=(30, 0, 0), left_arm=(30, 0, 0))),
                                  (32, g0)]))
    # Thermal Fury: gather (arms crossed low), then the most-muscular explosion with a roar; trembling hold
    f_in = P(G, root=rootp(-3.2), waist=(14, 0, 0), torso=(34, 0, 0), head=(-10, 0, 0),
             right_arm=(-36, -36, 6), right_forearm=(-40, 0, 0), left_arm=(-36, 36, -6), left_forearm=(-40, 0, 0), **FIST, **GRIT)
    f_out = P(G, root=rootp(0.4), waist=(-6, 0, 0), torso=(-18, 0, 0), head=(-38, 0, 0),
              right_arm=(-30, 0, 58), right_forearm=(-112, 0, 0), left_arm=(-30, 0, -58), left_forearm=(-112, 0, 0),
              right_leg=(-2, 8, 12), right_shin=(14, 0, -4), left_leg=(-2, -8, -12), left_shin=(14, 0, 4), **FIST, **ROAR)
    out.append(clip('fury', 24, [(0, G), (5, f_in), (8, add(f_in, torso=(4, 0, 0))), (10, f_out), (12, add(f_out, right_arm=(0, 0, 3), left_arm=(0, 0, -3))),
                                 (14, f_out), (16, add(f_out, right_arm=(0, 0, 3), left_arm=(0, 0, -3), torso=(-2, 0, 0))), (18, f_out), (24, G)]))
    # Critical Overheat: charge (rooted, arms locked over the chest, the body shakes harder), release (flung open,
    # roar), recovery (spent: hands on the knees, panting)
    k_in = P(G, root=rootp(-3.4), waist=(16, 0, 0), torso={'rot': (36, 0, 0), 'scale': (1, 1, 1)}, head=(-30, 0, 0),
             right_arm=(-62, -44, 4), right_forearm=(-118, 0, 0), left_arm=(-62, 44, -4), left_forearm=(-118, 0, 0),
             right_leg=(-30, 12, 12), right_shin=(56, 0, -4), left_leg=(-30, -12, -12), left_shin=(56, 0, 4), **FIST, **GRIT)
    keys = [(0, dict(G, torso={'rot': G['torso'], 'scale': (1, 1, 1)})), (10, k_in)]
    for i, t in enumerate(range(13, 58, 3)):
        amp = 0.6 + 2.2 * (t / 60)
        sgn = 1 if i % 2 == 0 else -1
        sc = 1.0 + 0.05 * (t / 60)
        keys.append((t, add(dict(k_in, torso={'rot': (36 + sgn * amp * 0.5, 0, 0), 'scale': (sc, 1.0 + 0.02 * t / 60, sc)}),
                            root={'pos': (sgn * amp * 0.15, 0, 0)}, right_arm=(0, 0, sgn * amp), left_arm=(0, 0, sgn * amp), head=(sgn * amp, 0, 0))))
    keys.append((60, dict(k_in, torso={'rot': (40, 0, 0), 'scale': (1.06, 1.03, 1.06)})))
    out.append(clip('ult_charge', 60, keys))
    r_out = P(G, root=rootp(1.0, 0.6), waist=(-8, 0, 0), torso={'rot': (-24, 0, 0), 'scale': (1.08, 1.04, 1.08)}, head=(-44, 0, 0),
              right_arm=(-40, 0, 84), right_forearm=(-20, 0, 0), left_arm=(-40, 0, -84), left_forearm=(-20, 0, 0),
              right_leg=(4, 8, 16), right_shin=(10, 0, -4), left_leg=(4, -8, -16), left_shin=(10, 0, 4), **OPEN, **ROAR)
    out.append(clip('ult_release', 18, [(0, dict(k_in, torso={'rot': (40, 0, 0), 'scale': (1.06, 1.03, 1.06)})), (2, r_out),
                                        (6, add(r_out, right_arm=(0, 0, 4), left_arm=(0, 0, -4))),
                                        (12, dict(r_out, torso={'rot': (-10, 0, 0), 'scale': (1, 1, 1)})),
                                        (18, dict(G, torso={'rot': G['torso'], 'scale': (1, 1, 1)}))]))
    spent = P(G, root=rootp(-3.6), waist=(18, 0, 0), torso=(44, 0, 0), head=(-34, 0, 0),
              right_arm=(-34, -12, 6), right_forearm=(-48, 0, 0), left_arm=(-34, 12, -6), left_forearm=(-48, 0, 0),
              right_leg=(-30, 10, 12), right_shin=(52, 0, -4), left_leg=(-26, -10, -12), left_shin=(50, 0, 4), **HALF, **PANT)
    pant_in = add(spent, torso=(-5, 0, 0), head=(4, 0, 0), right_arm=(0, 0, 2), left_arm=(0, 0, -2))
    pant_in = dict(pant_in, jaw=(20, 0, 0))
    rk = [(0, spent)]
    for t in range(5, 50, 10):
        rk.append((t, pant_in))
        rk.append((min(50, t + 5), spent))
    rk[-1] = (50, P(spent, **GRIT))
    out.append(clip('ult_recover', 50, rk))
    vk = [(0, G)]
    for t in range(4, 30, 8):
        vk.append((t, pant_in))
        vk.append((min(30, t + 4), spent))
    vk[-1] = (30, G)
    out.append(clip('vent', 30, vk))
    # heavy hit: the mass rocks back a step, arms flung, then plants again
    st = P(G, root=rootp(-0.6, 2.4), waist=(-10, 10, 0), torso=(-18, 14, 8), head=(14, 10, 0),
           right_arm=(-30, 10, 40), right_forearm=(-40, 0, 0), left_arm=(-10, -10, -46), left_forearm=(-30, 0, 0),
           right_leg=(16, 8, 9), right_shin=(20, 0, -4), left_leg=(8, -8, -9), left_shin=(30, 0, 4), **OPEN,
           jaw=(14, 0, 0), lip_upper=(-6, 0, 0), left_brow=(0, 0, 6), right_brow=(0, 0, -6))
    out.append(clip('stagger', 14, [(0, G), (3, st), (7, add(st, torso=(10, 0, 0), root={'pos': (0, 0, -1.0)})), (14, G)]))
    land = P(G, root=rootp(-4.4), waist=(18, 0, 0), torso=(32, 0, 0), head=(-36, 0, 0),
             right_arm=(-26, 6, 34), right_forearm=(-30, 0, 0), left_arm=(-26, -6, -34), left_forearm=(-30, 0, 0),
             right_leg=(-50, 10, 10), right_shin=(84, 0, -4), left_leg=(-46, -10, -10), left_shin=(80, 0, 4))
    out.append(clip('landing', 12, [(0, land), (3, add(land, root={'pos': (0, -0.6, 0)})), (12, G)]))
    # transformation roar when becoming the Red Hulk
    e_in = P(f_in, root=rootp(-4.0), torso=(44, 0, 0), head=(0, 0, 0), **FACE0)
    out.append(clip('enter', 30, [(0, e_in), (8, add(e_in, torso=(4, 0, 0))), (12, f_out), (14, add(f_out, right_arm=(0, 0, 3), left_arm=(0, 0, -3))),
                                  (18, f_out), (22, add(f_out, head=(6, 0, 0))), (30, dict(CALM, **FACE0))]))
    return out


# ---- face expressions (overlays on jaw / lip / brows; loop with micro motion) ------------------------
def face():
    out = []
    calm = dict(FACE0, jaw=(1.5, 0, 0), left_brow=(0, 0, -2), right_brow=(0, 0, 2))
    out.append(clip('face_calm', 80, [(0, calm), (40, dict(calm, jaw=(3, 0, 0))), (80, calm)], loop=True))
    snarl = dict(GRIT)
    out.append(clip('face_snarl', 30, [(0, snarl), (14, dict(snarl, lip_upper=(-15, 0, 0), jaw=(4, 0, 0))), (30, snarl)], loop=True))
    fury = dict(GRIT, jaw=(8, 0, 0), lip_upper=(-17, 0, 0), left_brow=(0, 0, -11), right_brow=(0, 0, 11))
    out.append(clip('face_fury', 12, [(0, fury), (3, dict(fury, jaw=(11, 0, 0))), (6, fury), (9, dict(fury, jaw=(10, 0, 0))), (12, fury)], loop=True))
    out.append(clip('face_roar', 20, [(0, ROAR), (10, dict(ROAR, jaw=(36, 0, 0))), (20, ROAR)], loop=True))
    out.append(clip('face_pant', 20, [(0, PANT), (10, dict(PANT, jaw=(22, 0, 0))), (20, PANT)], loop=True))
    return out


# ---- first person (arms model, FP_OFFSET / FP_SCALE in RedHulkRenderer) --------------------------------
FP_OFFSET = (0.0, -1.62, -0.34)
FP_SCALE = 0.82


def F(base=None, **kw):
    names = {'ra': 'armorRightArm', 'rf': 'right_forearm', 'rh': 'right_hand', 'rfi': 'right_fingers', 'rt': 'right_thumb',
             'la': 'armorLeftArm', 'lf': 'left_forearm', 'lh': 'left_hand', 'lfi': 'left_fingers', 'lt': 'left_thumb'}
    out = dict(base or {})
    for k, v in kw.items():
        out[names[k]] = v
    return out


FFIST = dict(rfi=(0, 0, -95), lfi=(0, 0, 95), rt=(0, 0, -50), lt=(0, 0, 50))
FOPEN = dict(rfi=(0, 0, 8), lfi=(0, 0, -8), rt=(0, 0, 10), lt=(0, 0, -10))
F_IDLE = F(ra=(6, 0, 6), rf=(-20, 0, 0), rh=(0, 0, 0), la=(6, 0, -6), lf=(-20, 0, 0), lh=(0, 0, 0),
           rfi=(0, 0, -45), lfi=(0, 0, 45), rt=(0, 0, -20), lt=(0, 0, 20))
F_GUARD = F(ra=(-58, -8, 4), rf=(-62, 0, 0), rh=(-6, 0, 0), la=(-62, 8, -4), lf=(-66, 0, 0), lh=(-6, 0, 0), **FFIST)
F_RUN = F(F_GUARD, ra=(-34, -6, 8), rf=(-74, 0, 0), la=(-30, 6, -8), lf=(-78, 0, 0))


def fp():
    c = []
    c.append(clip('idle', 64, [(0, F_IDLE), (30, F(F_IDLE, ra=(4, 0, 7), la=(4, 0, -7))), (64, F_IDLE)], loop=True))
    c.append(clip('idle_combat', 40, [(0, F_GUARD), (20, F(F_GUARD, ra=(-60, -8, 4), la=(-64, 8, -4))), (40, F_GUARD)], loop=True))
    c.append(clip('run', 16, [(0, F(F_RUN, ra=(-44, -6, 8), la=(-20, 6, -8))), (8, F(F_RUN, ra=(-20, -6, 8), la=(-44, 6, -8))),
                              (16, F(F_RUN, ra=(-44, -6, 8), la=(-20, 6, -8)))], loop=True))
    G = F_GUARD
    jab = F(G, la=(-90, -6, 0), lf=(-6, 0, 0))
    c.append(clip('jab_l', 9, [(0, G), (2, F(G, la=(-40, 6, -6), lf=(-90, 0, 0))), (4, jab), (6, F(jab, la=(-88, -6, 0))), (9, G)]))
    cross = F(G, ra=(-92, 6, 0), rf=(-4, 0, 0))
    c.append(clip('jab_r', 10, [(0, G), (3, F(G, ra=(-30, 14, 20), rf=(-100, 0, 0))), (5, cross), (7, F(cross, ra=(-89, 6, 0))), (10, G)]))
    hook = F(G, la=(-84, 46, -10), lf=(-60, 0, 0))
    c.append(clip('hook', 12, [(0, G), (3, F(G, la=(-60, -50, -30), lf=(-40, 0, 0))), (6, hook), (8, F(hook, la=(-80, 66, 0))), (12, G)]))
    upc = F(G, ra=(-150, 0, 0), rf=(-40, 0, 0))
    c.append(clip('uppercut', 13, [(0, G), (4, F(G, ra=(-10, 0, 10), rf=(-90, 0, 0))), (7, upc), (9, F(upc, ra=(-156, 0, 0))), (13, G)]))
    over = F(G, ra=(-172, -14, 0), rf=(-50, 0, 0), la=(-172, 14, 0), lf=(-50, 0, 0))
    down = F(G, ra=(-46, -10, 0), rf=(-14, 0, 0), la=(-46, 10, 0), lf=(-14, 0, 0))
    c.append(clip('hammer', 20, [(0, G), (4, F(G, ra=(-120, -10, 0), la=(-120, 10, 0))), (8, over), (10, F(over, ra=(-120, -14, 0), la=(-120, 14, 0))),
                                 (12, down), (15, F(down, ra=(-44, -10, 0), la=(-44, 10, 0))), (20, G)]))
    reach = F(G, ra=(-88, 4, 0), rf=(-8, 0, 0), **dict((k, v) for k, v in FOPEN.items() if k in ('rfi', 'rt')))
    holdp = F(G, ra=(-80, 6, 0), rf=(-40, 0, 0), **FFIST)
    c.append(clip('grab', 9, [(0, G), (2, F(G, ra=(-50, 10, 14), rf=(-60, 0, 0))), (4, reach), (6, F(reach, rfi=(0, 0, -95), rt=(0, 0, -50))), (9, holdp)]))
    c.append(clip('hold', 20, [(0, holdp), (10, F(holdp, ra=(-82, 6, 1))), (20, holdp)], loop=True))
    c.append(clip('throw', 12, [(0, holdp), (3, F(holdp, ra=(-160, 20, 10), rf=(-70, 0, 0))), (6, F(G, ra=(-70, -6, 0), rf=(-6, 0, 0), rfi=(0, 0, 8))),
                                (8, F(G, ra=(-50, -6, 0), rf=(-10, 0, 0), rfi=(0, 0, 8))), (12, G)]))
    c.append(clip('grab_miss', 10, [(0, G), (2, F(G, ra=(-50, 10, 14), rf=(-60, 0, 0))), (4, reach), (6, F(reach, rfi=(0, 0, -95))), (10, G)]))
    wide = F(G, ra=(-80, 50, 0), rf=(-10, 0, 0), la=(-80, -50, 0), lf=(-10, 0, 0), **FOPEN)
    shut = F(G, ra=(-86, -6, 0), rf=(-6, 0, 0), la=(-86, 6, 0), lf=(-6, 0, 0), **FOPEN)
    c.append(clip('clap', 16, [(0, G), (5, wide), (8, F(wide, ra=(-84, 20, 0), la=(-84, -20, 0))), (9, shut), (12, shut), (16, G)]))
    up = F(G, ra=(-176, -6, 0), rf=(-26, 0, 0), la=(-176, 6, 0), lf=(-26, 0, 0))
    smash = F(G, ra=(-30, -8, 0), rf=(-6, 0, 0), la=(-30, 8, 0), lf=(-6, 0, 0))
    c.append(clip('seismic', 26, [(0, G), (6, up), (10, up), (13, F(up, ra=(-110, -6, 0), la=(-110, 6, 0))), (15, smash), (19, smash), (26, G)]))
    c.append(clip('leap_crouch', 10, [(0, G), (8, F(G, ra=(20, 0, 20), la=(20, 0, -20))), (10, F(G, ra=(-150, 0, 10), la=(-150, 0, -10)))]))
    c.append(clip('leap_air', 20, [(0, F(G, ra=(-160, 0, 10), la=(-160, 0, -10))), (10, F(G, ra=(-150, 0, 14), la=(-150, 0, -14))),
                                   (20, F(G, ra=(-160, 0, 10), la=(-160, 0, -10)))], loop=True))
    c.append(clip('leap_land', 16, [(0, smash), (8, smash), (16, G)]))
    pull = F(G, ra=(10, 0, 20), rf=(-80, 0, 0), la=(10, 0, -20), lf=(-80, 0, 0))
    push = F(G, ra=(-90, -10, 0), rf=(-4, 0, 0), rh=(-30, 0, 0), la=(-90, 10, 0), lf=(-4, 0, 0), lh=(-30, 0, 0), **FOPEN)
    g0 = F(G, rh=(0, 0, 0), lh=(0, 0, 0))
    c.append(clip('blast', 32, [(0, g0), (6, pull), (11, pull), (12, push), (15, F(push, ra=(-86, -10, 0), la=(-86, 10, 0))), (18, push),
                                (21, F(push, ra=(-86, -10, 0), la=(-86, 10, 0))), (24, push), (27, F(push, ra=(-60, -10, 0), la=(-60, 10, 0))), (32, g0)]))
    flex = F(G, ra=(-30, 0, 60), rf=(-110, 0, 0), la=(-30, 0, -60), lf=(-110, 0, 0))
    c.append(clip('fury', 24, [(0, G), (5, F(G, ra=(-40, -40, 0), la=(-40, 40, 0))), (10, flex), (14, F(flex, ra=(-30, 0, 63))), (18, flex), (24, G)]))
    lock = F(G, ra=(-64, -40, 0), rf=(-118, 0, 0), la=(-64, 40, 0), lf=(-118, 0, 0))
    ck = [(0, G), (10, lock)]
    for i, t in enumerate(range(13, 58, 3)):
        a = (0.6 + 2.2 * t / 60) * (1 if i % 2 == 0 else -1)
        ck.append((t, F(lock, ra=(-64, -40, a), la=(-64, 40, a))))
    ck.append((60, lock))
    c.append(clip('ult_charge', 60, ck))
    fling = F(G, ra=(-40, 0, 80), rf=(-20, 0, 0), la=(-40, 0, -80), lf=(-20, 0, 0), **FOPEN)
    c.append(clip('ult_release', 18, [(0, lock), (2, fling), (8, fling), (18, G)]))
    rest = F(F_IDLE, ra=(-20, 0, 8), rf=(-50, 0, 0), la=(-20, 0, -8), lf=(-50, 0, 0))
    c.append(clip('ult_recover', 50, [(0, rest), (25, F(rest, ra=(-24, 0, 8), la=(-24, 0, -8))), (50, G)]))
    c.append(clip('vent', 30, [(0, G), (6, rest), (24, rest), (30, G)]))
    c.append(clip('stagger', 14, [(0, G), (3, F(G, ra=(-20, 0, 40), la=(-20, 0, -40), **FOPEN)), (14, G)]))
    c.append(clip('landing', 12, [(0, smash), (12, G)]))
    c.append(clip('enter', 30, [(0, lock), (12, flex), (22, flex), (30, F_IDLE)]))
    return c


def check(clips):
    by = {c.name: c for c in clips}
    for name, (ticks, _) in MOVES.items():
        assert name in by, name
        assert abs(by[name].length - ticks / 20.0) < 1e-6, (name, by[name].length, ticks)


def main():
    st, cb, sk, fc, f = stances(), combat(), skills(), face(), fp()
    check(cb + sk)
    check(f)
    print('stances', write_anims('animations/red_hulk/red_hulk_stances.animation.json', st))
    print('combat', write_anims('animations/red_hulk/red_hulk_combat.animation.json', cb))
    ult = [c for c in sk if c.name.startswith('ult_') or c.name == 'vent']
    sk = [c for c in sk if c not in ult]
    print('skills', write_anims('animations/red_hulk/red_hulk_skills.animation.json', sk))
    print('ult', write_anims('animations/red_hulk/red_hulk_ult.animation.json', ult))
    print('face', write_anims('animations/red_hulk/red_hulk_face.animation.json', fc))
    print('fp', write_anims('animations/red_hulk/red_hulk_fp.animation.json', f))


if __name__ == '__main__':
    main()
