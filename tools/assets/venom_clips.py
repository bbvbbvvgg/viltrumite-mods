"""Third-person, face and first-person clips of Venom (own work, CC0).

Every timed move is authored as anticipation -> acceleration -> impact -> follow-through -> recovery; lengths and
contact ticks are MOVES below = VenomMove on the server (VenomClipsTest compares the JSON with the enum), so the
server clock (actionElapsed) drives the clip in both views. Looping held states (zip, hold, shell, possess) are short
cycles the client wraps. Conventions (jinwoo_poses): arm X- = raise forward, arm Y+ sweeps to the right, right arm
Z+ / left arm Z- = outward, forearm X- = elbow bend, torso X+ = lean forward, Y+ = turn right, leg X- = forward,
shin X+ = knee bend, jaw X+ = open, tongue X+ = droop (X- curls the tip up), fingers Z toward the palm = curl.
Values ADD to the rest pose of venom.geo.json (torso +10, head -12, jaw +22, arms 7 deg out, the tongue's curve).
Outputs animations/venom/venom_{stances,moves,lash,skills,face,fp}.animation.json (moves: claws, tendril, grabs,
throw; lash: the tentacle combo, the slam, the Spider clashes, the recoil; skills: transformation, shell, rage,
engulf, possession, reform)."""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_clips import clip  # noqa: E402
from jinwoo_geo import write_anims  # noqa: E402
from jinwoo_poses import P  # noqa: E402

# name: (length ticks, contact tick or -1) -- keep in sync with VenomMove / VenomRules
MOVES = {
    'transform': (46, 40), 'revert': (30, -1),
    'claw_1': (9, 4), 'claw_2': (9, 4), 'claw_3': (13, 6), 'tendril_shot': (7, 3),
    'pull': (12, -1), 'grab': (8, 3), 'throw': (12, 5), 'slam': (16, 8),
    'lash_sweep': (14, 6), 'lash_impale': (14, 6), 'lash_slam': (20, 11),
    'rage': (26, 10), 'engulf': (30, 18), 'recoil': (24, -1), 'reform': (14, -1), 'clash': (14, 3),
    'tear_web': (14, 7), 'grab_miss': (8, -1),
}
# looping held states: name -> cycle ticks (the server length is open-ended)
LOOPS = {'zip': 10, 'hold': 20, 'shell': 30, 'possess': 40}
STANCES = ('idle', 'idle_combat', 'idle_rage', 'walk', 'run', 'crouch', 'air_up', 'air_down', 'swim', 'wall_climb',
           'wall_hang', 'wall_run', 'jump', 'land', 'hurt')
FACES = ('face_idle', 'face_snarl', 'face_rage', 'face_hurt')
# move clips by file (VenomView.MOVE_FILES); each file stays under the repository's push batch size
FILES = {
    'moves': ('claw_1', 'claw_2', 'claw_3', 'tendril_shot', 'zip', 'pull', 'grab', 'hold', 'grab_miss', 'throw'),
    'lash': ('slam', 'lash_sweep', 'lash_impale', 'lash_slam', 'clash', 'tear_web', 'recoil'),
    'skills': ('transform', 'revert', 'shell', 'rage', 'engulf', 'possess', 'reform'),
}
TONGUE = tuple(f'tongue_{i}' for i in range(6))

CLAW = {'right_fingers': (0, 0, -30), 'left_fingers': (0, 0, 30), 'right_thumb': (0, 0, -14), 'left_thumb': (0, 0, 14)}
HOOK = {'right_fingers': (0, 0, -64), 'left_fingers': (0, 0, 64), 'right_thumb': (0, 0, -34), 'left_thumb': (0, 0, 34)}
FIST = {'right_fingers': (0, 0, -95), 'left_fingers': (0, 0, 95), 'right_thumb': (0, 0, -50), 'left_thumb': (0, 0, 50)}
SPREAD = {'right_fingers': (0, 0, 14), 'left_fingers': (0, 0, -14), 'right_thumb': (0, 0, 18), 'left_thumb': (0, 0, -18)}
FACE0 = {'jaw': (0, 0, 0), 'tongue_0': (0, 0, 0), 'tongue_1': (0, 0, 0), 'tongue_2': (0, 0, 0)}
MAW = {'jaw': (22, 0, 0), 'tongue_0': (-8, 0, 0), 'tongue_1': (-16, 0, 0), 'tongue_2': (-10, 0, 0)}
ROAR = {'jaw': (30, 0, 0), 'tongue_0': (-14, 0, 0), 'tongue_1': (-22, 0, 0), 'tongue_2': (-18, 0, 0)}


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


def legs(rx, rs, lx, ls, spread=6, ry=4, flat=True):
    """Both legs (hip X, knee bend) with the feet kept flat to the root unless flat=False."""
    out = {'right_leg': (rx, ry, spread), 'right_shin': (rs, 0, -spread * 0.5), 'left_leg': (lx, -ry, -spread),
           'left_shin': (ls, 0, spread * 0.5)}
    out['right_foot'] = (-(rx + rs) if flat else 0, 0, 0)
    out['left_foot'] = (-(lx + ls) if flat else 0, 0, 0)
    return out


def arms(r, rf, l, lf, rh=(0, 0, 0), lh=(0, 0, 0)):
    return {'right_arm': r, 'right_forearm': (rf, 0, 0), 'right_hand': rh, 'left_arm': l, 'left_forearm': (lf, 0, 0), 'left_hand': lh}


# ---- stances -----------------------------------------------------------------------------------------
# a predator's crouch: the mass hangs forward over bent knees, the arms wide and low with the claws half open, the
# head pushed forward under the shoulders.
CALM = P(root=rootp(-0.8), waist=(4, 0, 0), torso=(4, 0, 0), head=(-4, 0, 0), chest=(0, 0, 0),
         **arms((-8, 6, 6), -24, (-8, -6, -6), -24), **legs(-8, 16, -8, 16), **CLAW)
COMBAT = P(root=rootp(-2.0), waist=(8, 0, 0), torso=(12, 0, 0), head=(-18, 0, 0), chest=(0, 0, 0),
           **arms((-30, 14, 22), -40, (-30, -14, -22), -40, (-12, 0, 0), (-12, 0, 0)), **legs(-20, 36, -14, 32, 9), **CLAW)
RAGE = P(COMBAT, root=rootp(-2.6), waist=(10, 0, 0), torso=(18, 0, 0), head=(-26, 0, 0),
         **arms((-24, 18, 34), -52, (-24, -18, -34), -52, (-18, 0, 0), (-18, 0, 0)), **legs(-24, 44, -18, 40, 11), **SPREAD)
CROUCH = P(COMBAT, root=rootp(-5.0), waist=(20, 0, 0), torso=(22, 0, 0), head=(-36, 0, 0),
           **arms((-46, 10, 18), -50, (-46, -10, -18), -50), **legs(-50, 84, -50, 84, 10))
AIR_UP = P(COMBAT, root=rootp(0), waist=(-4, 0, 0), torso=(-2, 0, 0), head=(-6, 0, 0),
           **arms((-150, 12, 28), -30, (-150, -12, -28), -30), **legs(-34, 64, -10, 34, 8, flat=False), **SPREAD)
AIR_DOWN = P(AIR_UP, waist=(10, 0, 0), torso=(16, 0, 0), head=(-24, 0, 0),
             **arms((-56, 14, 54), -46, (-56, -14, -54), -46), **legs(-30, 50, -24, 44, 12, flat=False), **CLAW)
SWIM_A = P(CALM, root=rootp(0), waist=(0, 0, 0), torso=(0, 0, 0), head=(-60, 0, 0),
           **arms((-170, 0, 30), 0, (-40, 0, -80), -20), **legs(10, 10, -10, 10, 4, flat=False), **SPREAD)
SWIM_B = P(SWIM_A, **arms((-40, 0, 80), -20, (-170, 0, -30), 0), **legs(-10, 10, 10, 10, 4, flat=False))
# on a wall the body is pressed to the surface, knees and elbows wide like a spider's
HANG = P(COMBAT, root=rootp(-1.0, -1.2), waist=(14, 0, 0), torso=(16, 0, 0), head=(-34, 0, 0),
         **arms((-150, 0, 34), -40, (-150, 0, -34), -40, (-20, 0, 0), (-20, 0, 0)), **legs(-40, 70, -40, 70, 18, flat=False), **HOOK)


def breath(base, depth, ticks):
    inhale = add(base, torso=(-2.0 * depth, 0, 0), head=(1.6 * depth, 0, 0), right_arm=(0, 0, 1.6 * depth),
                 left_arm=(0, 0, -1.6 * depth), right_forearm=(-2 * depth, 0, 0), left_forearm=(-2 * depth, 0, 0),
                 root={'pos': (0, 0.3 * depth, 0)})
    sc = (1.0 + 0.03 * depth, 1.0 + 0.015 * depth, 1.0 + 0.035 * depth)
    return [(0, dict(base, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)})),
            (int(ticks * 0.45), dict(inhale, chest={'rot': (0, 0, 0), 'scale': sc})),
            (int(ticks * 0.6), dict(inhale, chest={'rot': (0, 0, 0), 'scale': sc})),
            (ticks, dict(base, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)}))]


def stances():
    out = []
    out.append(clip('idle', 60, breath(CALM, 1.0, 60), loop=True))
    # the combat idle sways like a stalking animal: weight shifts side to side, the head stays locked on
    ck = breath(COMBAT, 1.3, 40)
    ck[1] = (ck[1][0], add(ck[1][1], waist=(0, 5, 0), torso=(0, -4, 0), head=(0, -2, 0)))
    ck[2] = (ck[2][0], add(ck[2][1], waist=(0, 5, 0), torso=(0, -4, 0), head=(0, -2, 0)))
    out.append(clip('idle_combat', 40, ck, loop=True))
    rk = breath(RAGE, 2.2, 24)
    out.append(clip('idle_rage', 24, rk, loop=True))
    out.append(clip('crouch', 40, [(0, CROUCH), (20, add(CROUCH, torso=(-2, 0, 0), head=(2, 0, 0))), (40, CROUCH)], loop=True))
    out.append(clip('air_up', 20, [(0, AIR_UP), (10, add(AIR_UP, right_arm=(-6, 0, 4), left_arm=(-6, 0, -4))), (20, AIR_UP)], loop=True))
    out.append(clip('air_down', 20, [(0, AIR_DOWN), (10, add(AIR_DOWN, right_arm=(0, 0, 8), left_arm=(0, 0, -8))), (20, AIR_DOWN)], loop=True))
    out.append(clip('swim', 24, [(0, SWIM_A), (12, SWIM_B), (24, SWIM_A)], loop=True))

    # prowl: a low, rolling walk; the shoulders swing against the hips, the claws trail
    def walk_key(sign, phase):
        if phase == 'strike':
            return P(CALM, root=rootp(-1.4, 0, (0, 0, 2.0 * sign)), waist=(5, -8 * sign, 0), torso=(6, 10 * sign, -2 * sign),
                     head=(-6, -8 * sign, 2 * sign), **legs(-30 * sign, 10 if sign > 0 else 32, 28 * sign, 32 if sign > 0 else 10),
                     **arms((24 * sign - 8, 6, 8), -20 - 16 * max(0, sign), (-24 * sign - 8, -6, -8), -20 - 16 * max(0, -sign)))
        return P(CALM, root=rootp(0.2), waist=(4, 0, 0), torso=(4, 0, 0), head=(-4, 0, 0),
                 **legs(-4 if sign > 0 else 6, 8 if sign > 0 else 52, 6 if sign > 0 else -4, 52 if sign > 0 else 8),
                 **arms((-6, 6, 7), -30, (-6, -6, -7), -30))
    out.append(clip('walk', 28, [(0, walk_key(1, 'strike')), (7, walk_key(1, 'pass')), (14, walk_key(-1, 'strike')),
                                 (21, walk_key(-1, 'pass')), (28, walk_key(1, 'strike'))], loop=True))

    # sprint: almost on all fours, huge bounding strides, the arms clawing the ground forward in turn
    def run_key(sign, phase):
        if phase == 'reach':
            return P(COMBAT, root=rootp(-2.4, 0, (0, 0, 3 * sign)), waist=(16, -8 * sign, 0), torso=(22, 10 * sign, 0),
                     head=(-36, -8 * sign, 0), **legs(-58 * sign if sign > 0 else 40, 30 if sign > 0 else 70, 40 if sign > 0 else -58, 70 if sign > 0 else 30, 8),
                     **arms((-110 if sign < 0 else 30, 8, 24), -30 if sign < 0 else -70, (-110 if sign > 0 else 30, -8, -24), -30 if sign > 0 else -70),
                     **SPREAD)
        return P(COMBAT, root=rootp(0.6), waist=(18, 0, 0), torso=(24, 0, 0), head=(-38, 0, 0),
                 **legs(-10, 90 if sign > 0 else 20, -10, 20 if sign > 0 else 90, 8, flat=False),
                 **arms((-50, 8, 30), -60, (-50, -8, -30), -60), **CLAW)
    out.append(clip('run', 16, [(0, run_key(1, 'reach')), (4, run_key(1, 'fly')), (8, run_key(-1, 'reach')),
                                (12, run_key(-1, 'fly')), (16, run_key(1, 'reach'))], loop=True))

    # wall climb: hand over hand, the knees pulling up alternately (one cycle per 1.6 blocks of height)
    def climb_key(sign):
        return P(HANG, root=rootp(-1.0 + 0.6 * sign, -1.2, (0, 0, 3 * sign)), torso=(16, 6 * sign, 0),
                 **arms((-168 if sign > 0 else -110, 0, 30), -20 if sign > 0 else -80, (-110 if sign > 0 else -168, 0, -30), -80 if sign > 0 else -20,
                        (-30, 0, 0), (-30, 0, 0)),
                 **legs(-70 if sign < 0 else -20, 90 if sign < 0 else 40, -20 if sign < 0 else -70, 40 if sign < 0 else 90, 18, flat=False))
    out.append(clip('wall_climb', 20, [(0, climb_key(1)), (10, climb_key(-1)), (20, climb_key(1))], loop=True))
    out.append(clip('wall_hang', 40, [(0, HANG), (20, add(HANG, torso=(-3, 0, 0), head=(3, 0, 0), root={'pos': (0, 0.4, 0)},
                                                        right_forearm=(-4, 0, 0), left_forearm=(-4, 0, 0))), (40, HANG)], loop=True))

    # facade run: sideways along the wall, low and fast, the claws scraping it in turn
    def wrun_key(sign):
        return P(COMBAT, root=rootp(-2.4, 0, (0, 0, 4 * sign)), waist=(18, -10 * sign, 0), torso=(24, 12 * sign, 0), head=(-36, -10 * sign, 0),
                 **legs(-60 * sign if sign > 0 else 36, 40 if sign > 0 else 60, 36 if sign > 0 else -60, 60 if sign > 0 else 40, 10, flat=False),
                 **arms((-80 if sign < 0 else 10, 10, 40), -50, (-80 if sign > 0 else 10, -10, -40), -50), **HOOK)
    out.append(clip('wall_run', 14, [(0, wrun_key(1)), (7, wrun_key(-1)), (14, wrun_key(1))], loop=True))

    # overlays: the take-off coil, the landing absorb (the whole mass drops and splays), the flinch of a hit
    coil = P(COMBAT, root=rootp(-4.0), waist=(22, 0, 0), torso=(26, 0, 0), head=(-40, 0, 0),
             **arms((30, 10, 30), -40, (30, -10, -30), -40), **legs(-56, 90, -56, 90, 10))
    spring = P(AIR_UP, root=rootp(1.0))
    out.append(clip('jump', 8, [(0, coil), (3, spring), (8, AIR_UP)]))
    splat = P(COMBAT, root=rootp(-6.0), waist=(26, 0, 0), torso=(30, 0, 0), head=(-44, 0, 0),
              **arms((-40, 10, 60), -30, (-40, -10, -60), -30), **legs(-60, 100, -60, 100, 14), **SPREAD)
    out.append(clip('land', 12, [(0, splat), (4, add(splat, root={'pos': (0, -0.6, 0)})), (12, COMBAT)]))
    flinch = P(COMBAT, root=rootp(-1.4, 1.6), waist=(-8, 6, 0), torso=(-14, 10, 6), head=(10, 8, 0),
               **arms((-30, 10, 44), -20, (-14, -10, -40), -30), **SPREAD)
    out.append(clip('hurt', 10, [(0, COMBAT), (2, flinch), (5, add(flinch, torso=(6, 0, 0))), (10, COMBAT)]))
    return out


# ---- moves -------------------------------------------------------------------------------------------
def moves():
    out = []
    G = COMBAT
    # transform (46 t): the geo shows from STAGE_GROW (22). Curled up while the mass takes over, the shoulders
    # heave, the head comes up as the face forms (32), the roar at STAGE_POSE (40): arms flung wide, chest out.
    curl = P(G, root=rootp(-5.0), waist=(24, 0, 0), torso=(30, 0, 0), head=(-10, 0, 0),
             **arms((-60, -30, 0), -110, (-60, 30, 0), -110), **legs(-40, 80, -40, 80, 8), **HOOK, **FACE0)
    heave = add(curl, torso=(-8, 0, 4), right_arm=(10, 10, 10), left_arm=(10, -10, -10), root={'pos': (0, 0.8, 0)})
    rise = P(G, root=rootp(-2.0), waist=(8, 0, 0), torso=(10, 0, 0), head=(-24, 0, 0),
             **arms((-20, 30, 40), -60, (-20, -30, -40), -60), **SPREAD, jaw=(10, 0, 0))
    roar = P(G, root=rootp(-1.0), waist=(-6, 0, 0), torso=(-14, 0, 0), head=(-16, 0, 0), chest={'rot': (0, 0, 0), 'scale': (1.08, 1.03, 1.1)},
             **arms((26, 30, 46), -64, (26, -30, -46), -64, (-24, 0, 0), (-24, 0, 0)), **legs(-14, 26, -14, 26, 14), **SPREAD, **ROAR)
    out.append(clip('transform', 46, [(0, curl), (22, curl), (25, heave), (28, curl), (30, heave), (34, rise), (38, add(rise, torso=(4, 0, 0))),
                                      (40, roar), (42, add(roar, head=(-4, 0, 0))), (44, roar), (46, dict(G, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)}, **FACE0))]))
    # revert (30 t; the geo shows until REVERT_GEO_END 18): a shudder, then he folds in on himself and shrinks
    shud = add(G, torso=(-4, 0, 3), head=(6, 0, 0), right_arm=(0, 0, 8), left_arm=(0, 0, -8))
    out.append(clip('revert', 30, [(0, dict(G, **FACE0)), (3, dict(shud, **FACE0)), (6, dict(G, **FACE0)), (12, curl), (18, add(curl, torso=(6, 0, 0))), (30, curl)]))

    # claw 1: right diagonal rake, high outside -> low across the body
    c1w = P(G, waist=(4, 16, 0), torso=(4, 22, 0), head=(-12, -16, 0), **arms((-150, 30, 60), -30, (-30, -14, -22), -40), **SPREAD)
    c1h = P(G, root=rootp(-2.4, -1.2), waist=(14, -20, 0), torso=(20, -26, 0), head=(-30, 18, 0),
            **arms((-60, -50, 10), -10, (-20, -10, -30), -50), **CLAW, **MAW)
    out.append(clip('claw_1', 9, [(0, G), (2, c1w), (4, c1h), (6, add(c1h, right_arm=(10, -10, 0), torso=(0, -4, 0))), (9, G)]))
    # claw 2: left horizontal backhand rake
    c2w = P(G, waist=(4, -18, 0), torso=(6, -24, 0), head=(-14, 18, 0), **arms((-40, 14, 22), -40, (-90, -70, -40), -40), **SPREAD)
    c2h = P(G, root=rootp(-2.2, -1.0), waist=(10, 22, 0), torso=(16, 28, 0), head=(-26, -20, 0),
            **arms((-30, 14, 24), -50, (-86, 60, -6), -8), **CLAW, **MAW)
    out.append(clip('claw_2', 9, [(0, G), (2, c2w), (4, c2h), (6, add(c2h, left_arm=(0, 10, 0), torso=(0, 4, 0))), (9, G)]))
    # claw 3: both claws up high, then a crushing double rake down the front with a lunge
    c3w = P(G, root=rootp(-1.0), waist=(-6, 0, 0), torso=(-12, 0, 0), head=(-8, 0, 0),
            **arms((-170, 14, 20), -40, (-170, -14, -20), -40), **SPREAD, jaw=(14, 0, 0))
    c3h = P(G, root=rootp(-4.0, -2.4), waist=(24, 0, 0), torso=(30, 0, 0), head=(-40, 0, 0),
            **arms((-50, 10, 16), -10, (-50, -10, -16), -10), **legs(-50, 70, 10, 40, 10), **CLAW, **ROAR)
    out.append(clip('claw_3', 13, [(0, G), (3, c3w), (5, add(c3w, torso=(4, 0, 0))), (6, c3h), (9, add(c3h, root={'pos': (0, -0.4, 0)})), (13, G)]))

    # tendril shot: right palm thrust, the tendril leaves the hand at contact
    tsh = P(G, waist=(8, 14, 0), torso=(12, 18, 0), head=(-20, -14, 0), **arms((-92, 4, 4), -4, (-20, -14, -30), -50, (10, 0, 0)), **SPREAD)
    out.append(clip('tendril_shot', 7, [(0, G), (1, add(G, right_arm=(20, 0, 10), right_forearm=(-40, 0, 0), torso=(0, -10, 0))), (3, tsh),
                                        (5, add(tsh, right_arm=(4, 0, 0))), (7, G)]))
    # zip (loop): towed through the air by the tendril, arm out in front, the body streams behind
    zipa = P(G, root=rootp(0, 0, (14, 0, 0)), waist=(0, 0, 0), torso=(-4, 0, 0), head=(-10, 0, 0),
             **arms((-170, 0, 8), -6, (-20, -10, -40), -30), **legs(10, 20, 30, 40, 6, flat=False), **HOOK)
    out.append(clip('zip', LOOPS['zip'], [(0, zipa), (5, add(zipa, left_arm=(6, 0, -6), right_leg=(6, 0, 0), left_leg=(-6, 0, 0))), (10, zipa)], loop=True))
    # pull: the tendril yanked back hand over hand
    pa = P(G, **arms((-90, 6, 6), -6, (-40, -14, -22), -50), **HOOK)
    pb = P(G, root=rootp(-1.6, 1.0), waist=(-2, 20, 0), torso=(-6, 26, 0), head=(-14, -20, 0), **arms((20, 20, 30), -90, (-90, -6, -6), -10), **FIST)
    out.append(clip('pull', 12, [(0, pa), (3, pb), (6, add(pb, right_arm=(-30, 0, 0), left_arm=(30, 0, 0), torso=(0, -20, 0))), (9, pb), (12, G)]))

    # grab: both arms lunge, the claws close on the victim
    reach = P(G, root=rootp(-2.6, -2.0), waist=(16, 0, 0), torso=(20, 0, 0), head=(-30, 0, 0),
              **arms((-84, -6, 8), -6, (-84, 6, -8), -6), **SPREAD, **MAW)
    holdp = P(G, root=rootp(-1.4), waist=(6, 0, 0), torso=(8, 0, 0), head=(-18, 0, 0),
              **arms((-100, -8, 10), -30, (-60, 10, -16), -50), **HOOK, jaw=(12, 0, 0))
    out.append(clip('grab', 8, [(0, G), (2, add(G, right_arm=(-30, 0, 20), left_arm=(-30, 0, -20))), (3, reach), (5, add(reach, **HOOK)), (8, holdp)]))
    out.append(clip('hold', LOOPS['hold'], [(0, holdp), (10, add(holdp, right_arm=(-4, 0, 1), head=(0, 6, 4), jaw=(6, 0, 0))), (20, holdp)], loop=True))
    out.append(clip('grab_miss', 8, [(0, G), (2, reach), (4, add(reach, **FIST, root={'pos': (0, -0.6, -0.6)})), (8, G)]))
    # throw: the held body swung back over the shoulder and hurled
    tw = P(holdp, waist=(-6, 26, 0), torso=(-12, 30, 0), head=(-10, -20, 0), **arms((-170, 30, 30), -40, (-30, -14, -30), -50))
    tr = P(G, root=rootp(-3.0, -1.6), waist=(18, -24, 0), torso=(24, -30, 0), head=(-34, 20, 0),
           **arms((-70, -40, 6), -6, (-20, -14, -40), -50), **SPREAD, **MAW)
    out.append(clip('throw', 12, [(0, holdp), (3, tw), (5, tr), (8, add(tr, right_arm=(20, 0, 0))), (12, G)]))
    # slam: hoisted overhead with both arms, brought down into the ground in front
    up = P(holdp, root=rootp(0), waist=(-8, 0, 0), torso=(-16, 0, 0), head=(-6, 0, 0), **arms((-178, -6, 10), -20, (-178, 6, -10), -20), **legs(-14, 26, -14, 26, 12))
    down = P(G, root=rootp(-5.0, -1.0), waist=(28, 0, 0), torso=(34, 0, 0), head=(-46, 0, 0), **arms((-40, -6, 10), -10, (-40, 6, -10), -10),
             **legs(-50, 90, -40, 80, 12), **HOOK, **ROAR)
    out.append(clip('slam', 16, [(0, holdp), (4, up), (6, add(up, torso=(-4, 0, 0))), (8, down), (11, add(down, root={'pos': (0, -0.5, 0)})), (16, G)]))

    # tentacle combo (the tentacles from the back follow the body; the arms throw them)
    sw0 = P(G, waist=(4, 30, 0), torso=(8, 36, 0), head=(-18, -26, 0), **arms((-60, 40, 60), -40, (-60, -40, -60), -40), **SPREAD)
    sw1 = P(G, root=rootp(-2.4), waist=(10, -34, 0), torso=(14, -40, 0), head=(-24, 30, 0), **arms((-80, -60, 40), -10, (-40, -40, -70), -20), **SPREAD, **MAW)
    out.append(clip('lash_sweep', 14, [(0, G), (3, sw0), (5, add(sw0, torso=(0, 6, 0))), (6, sw1), (9, add(sw1, torso=(0, -6, 0))), (14, G)]))
    im0 = P(G, root=rootp(-1.0, 1.4), waist=(-4, 0, 0), torso=(-10, 0, 0), head=(-6, 0, 0), chest={'rot': (0, 0, 0), 'scale': (1.06, 1.02, 1.08)},
            **arms((30, 10, 50), -60, (30, -10, -50), -60), **SPREAD, jaw=(12, 0, 0))
    im1 = P(G, root=rootp(-3.2, -2.4), waist=(22, 0, 0), torso=(28, 0, 0), head=(-40, 0, 0), chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)},
            **arms((-30, 20, 70), -20, (-30, -20, -70), -20), **legs(-46, 64, 14, 30, 10), **SPREAD, **ROAR)
    out.append(clip('lash_impale', 14, [(0, dict(G, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)})), (4, im0), (6, im1), (9, im1), (14, dict(G, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)}))]))
    ls0 = P(G, root=rootp(1.0), waist=(-10, 0, 0), torso=(-20, 0, 0), head=(-4, 0, 0), **arms((-176, 30, 40), -20, (-176, -30, -40), -20),
            **legs(-10, 20, -10, 20, 12), **SPREAD, jaw=(16, 0, 0))
    ls1 = P(G, root=rootp(-6.0, -1.6), waist=(30, 0, 0), torso=(38, 0, 0), head=(-50, 0, 0), **arms((-30, 10, 30), -10, (-30, -10, -30), -10),
            **legs(-56, 100, -56, 100, 14), **CLAW, **ROAR)
    out.append(clip('lash_slam', 20, [(0, G), (5, ls0), (9, add(ls0, root={'pos': (0, 0.6, 0)})), (11, ls1), (15, add(ls1, root={'pos': (0, -0.4, 0)})), (20, G)]))

    # shell (loop): curled behind the crossed forearms, the hardened mass breathing
    sh = P(G, root=rootp(-4.0), waist=(20, 0, 0), torso=(24, 0, 0), head=(-20, 0, 0),
           **arms((-100, -40, 10), -110, (-100, 40, -10), -110), **legs(-44, 80, -44, 80, 10), **FIST, **FACE0)
    out.append(clip('shell', LOOPS['shell'], [(0, sh), (15, add(sh, torso=(-3, 0, 0), root={'pos': (0, 0.3, 0)})), (30, sh)], loop=True))
    # rage: gather, then the bellow: arms back, chest out, the maw wide
    gat = P(G, root=rootp(-4.0), waist=(22, 0, 0), torso=(30, 0, 0), head=(-20, 0, 0), **arms((-40, -30, 10), -100, (-40, 30, -10), -100), **FIST,
            **legs(-44, 80, -44, 80, 10), chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)}, jaw=(4, 0, 0))
    bel = P(roar, root=rootp(-1.4), torso=(-18, 0, 0), head=(-20, 0, 0), chest={'rot': (0, 0, 0), 'scale': (1.12, 1.04, 1.14)})
    rk = [(0, dict(G, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)})), (6, gat), (9, add(gat, torso=(4, 0, 0))), (10, bel)]
    for t in range(12, 21, 2):
        rk.append((t, add(bel, head=((-3 if t % 4 else 3), 0, 0), right_arm=(0, 0, 2), left_arm=(0, 0, -2))))
    rk.append((26, dict(RAGE, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)}, **FACE0)))
    out.append(clip('rage', 26, rk))
    # engulf: the arms spread wide like a cloak, the lunge, the maw closes over the victim at 18
    en0 = P(G, root=rootp(-1.0), waist=(0, 0, 0), torso=(-6, 0, 0), head=(-14, 0, 0), **arms((-60, 40, 80), -20, (-60, -40, -80), -20), **SPREAD, **MAW)
    en1 = P(G, root=rootp(-3.0, -3.0), waist=(20, 0, 0), torso=(26, 0, 0), head=(-36, 0, 0), **arms((-100, -40, 20), -60, (-100, 40, -20), -60), **HOOK, **ROAR)
    out.append(clip('engulf', 30, [(0, G), (8, en0), (14, add(en0, torso=(4, 0, 0))), (18, en1), (24, add(en1, jaw=(-20, 0, 0))), (30, add(en1, jaw=(-22, 0, 0)))]))
    # possess (loop): the emptied husk sags, the surface still crawling
    husk = P(G, root=rootp(-6.0), waist=(18, 0, 0), torso=(26, 0, 0), head=(-4, 0, 0),
             **arms((-50, 0, 14), -14, (-50, 0, -14), -14), **legs(-60, 110, -60, 110, 12), **SPREAD, jaw=(20, 0, 0))
    out.append(clip('possess', LOOPS['possess'], [(0, husk), (20, add(husk, torso=(-4, 0, 3), head=(4, 6, 0), root={'pos': (0, 0.4, 0)})), (40, husk)], loop=True))
    # recoil (fire / loud sound): the symbiote writhes, claws at its own head, shrieks
    rc0 = P(G, root=rootp(-2.0, 1.6), waist=(-10, 10, 0), torso=(-18, 14, 8), head=(14, 10, 10),
            **arms((-150, -50, 10), -120, (-150, 50, -10), -120), **SPREAD, **ROAR)
    rc1 = add(rc0, torso=(10, -24, -14), head=(-10, -20, -18), waist=(4, -16, 0))
    out.append(clip('recoil', 24, [(0, G), (3, rc0), (7, rc1), (11, rc0), (15, rc1), (19, add(rc0, torso=(10, 0, 0))), (24, dict(G, **FACE0))]))
    # reform: sagging after a heavy hit, the body pulls itself back into shape
    sag = P(G, root=rootp(-4.0), waist=(16, 0, 0), torso=(24, 0, 12), head=(6, 0, 10),
            **arms((-34, 0, 12), -6, (-44, 0, -6), -4), **legs(-40, 70, -30, 60, 10), **SPREAD, jaw=(16, 0, 0))
    out.append(clip('reform', 14, [(0, sag), (4, add(sag, torso=(-10, 0, -10), head=(-10, 0, -10))), (8, add(G, torso=(4, 0, 0))), (14, G)]))
    # clash with the spider: braced crossed forearms take the impact at 3, pushed back
    br = P(G, root=rootp(-2.4, -1.0), waist=(14, 0, 0), torso=(18, 0, 0), head=(-26, 0, 0), **arms((-110, -30, 10), -80, (-110, 30, -10), -80), **FIST, **MAW)
    out.append(clip('clash', 14, [(0, G), (2, br), (3, add(br, root={'pos': (0, 0, 2.0)}, torso=(-8, 0, 0))), (7, add(br, root={'pos': (0, 0, 1.0)})), (14, G)]))
    # tear web: claws sink into the strands in front, then rip outward
    tw0 = P(G, root=rootp(-1.0), **arms((-110, -14, 4), -30, (-110, 14, -4), -30), **HOOK)
    tw1 = P(G, root=rootp(-2.0), waist=(10, 0, 0), torso=(-6, 0, 0), head=(-18, 0, 0), chest={'rot': (0, 0, 0), 'scale': (1.06, 1.02, 1.08)},
            **arms((-80, 40, 80), -20, (-80, -40, -80), -20), **FIST, **MAW)
    out.append(clip('tear_web', 14, [(0, dict(G, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)})), (4, dict(tw0, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)})),
                                     (6, add(dict(tw0, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)}), right_arm=(0, 6, 4), left_arm=(0, -6, -4))), (7, tw1), (10, tw1),
                                     (14, dict(G, chest={'rot': (0, 0, 0), 'scale': (1, 1, 1)}))]))
    return out


# ---- face (jaw / tongue overlays; loop with micro motion) ---------------------------------------------
def face():
    out = []
    idle = dict(FACE0, jaw=(1.5, 0, 0))
    out.append(clip('face_idle', 80, [(0, idle), (40, dict(idle, jaw=(4, 0, 0), tongue_1=(-4, 0, 0))), (80, idle)], loop=True))
    snarl = {'jaw': (8, 0, 0), 'tongue_0': (-4, 0, 0), 'tongue_1': (-8, 0, 0), 'tongue_2': (-4, 0, 0)}
    out.append(clip('face_snarl', 30, [(0, snarl), (14, dict(snarl, jaw=(11, 0, 0), tongue_1=(-12, 0, 0))), (30, snarl)], loop=True))
    rage = {'jaw': (20, 0, 0), 'tongue_0': (-10, 0, 0), 'tongue_1': (-18, 0, 0), 'tongue_2': (-12, 0, 0)}
    out.append(clip('face_rage', 12, [(0, rage), (3, dict(rage, jaw=(24, 0, 0))), (6, rage), (9, dict(rage, jaw=(23, 0, 0))), (12, rage)], loop=True))
    hurt = {'jaw': (16, 0, 0), 'tongue_0': (6, 0, 0), 'tongue_1': (10, 0, 0), 'tongue_2': (6, 0, 0)}
    out.append(clip('face_hurt', 10, [(0, hurt), (5, dict(hurt, jaw=(19, 0, 0))), (10, hurt)], loop=True))
    return out


# ---- first person (arms model, FP_OFFSET / FP_SCALE in VenomRenderer) ----------------------------------
def F(base=None, **kw):
    names = {'ra': 'armorRightArm', 'rf': 'right_forearm', 'rh': 'right_hand', 'rfi': 'right_fingers', 'rt': 'right_thumb',
             'la': 'armorLeftArm', 'lf': 'left_forearm', 'lh': 'left_hand', 'lfi': 'left_fingers', 'lt': 'left_thumb'}
    out = dict(base or {})
    for k, v in kw.items():
        out[names[k]] = v
    return out


FCLAW = dict(rfi=(0, 0, -30), lfi=(0, 0, 30), rt=(0, 0, -14), lt=(0, 0, 14))
FHOOK = dict(rfi=(0, 0, -64), lfi=(0, 0, 64), rt=(0, 0, -34), lt=(0, 0, 34))
FFIST = dict(rfi=(0, 0, -95), lfi=(0, 0, 95), rt=(0, 0, -50), lt=(0, 0, 50))
FSPREAD = dict(rfi=(0, 0, 14), lfi=(0, 0, -14), rt=(0, 0, 18), lt=(0, 0, -18))
F_IDLE = F(ra=(4, 0, 4), rf=(-26, 0, 0), rh=(0, 0, 0), la=(4, 0, -4), lf=(-26, 0, 0), lh=(0, 0, 0), **FCLAW)
F_GUARD = F(ra=(-50, -6, 10), rf=(-56, 0, 0), rh=(-14, 0, 0), la=(-50, 6, -10), lf=(-56, 0, 0), lh=(-14, 0, 0), **FCLAW)
F_RUN = F(F_GUARD, ra=(-34, -6, 12), rf=(-70, 0, 0), la=(-30, 6, -12), lf=(-74, 0, 0))


def fp():
    c = []
    G = F_GUARD
    c.append(clip('idle', 60, [(0, F_IDLE), (27, F(F_IDLE, ra=(2, 0, 5), la=(2, 0, -5))), (60, F_IDLE)], loop=True))
    c.append(clip('idle_combat', 40, [(0, G), (18, F(G, ra=(-52, -6, 11), la=(-52, 6, -11))), (40, G)], loop=True))
    c.append(clip('run', 16, [(0, F(F_RUN, ra=(-70, -6, 12), la=(-14, 6, -12))), (8, F(F_RUN, ra=(-14, -6, 12), la=(-70, 6, -12))),
                              (16, F(F_RUN, ra=(-70, -6, 12), la=(-14, 6, -12)))], loop=True))
    low = F(G, ra=(-30, 0, 20), rf=(-80, 0, 0), la=(-30, 0, -20), lf=(-80, 0, 0), **FHOOK)
    roar = F(G, ra=(-60, 40, 50), rf=(-20, 0, 0), la=(-60, -40, -50), lf=(-20, 0, 0), **FSPREAD)
    c.append(clip('transform', 46, [(0, low), (22, low), (26, F(low, ra=(-40, 0, 24), la=(-40, 0, -24))), (30, low), (36, F(G, **FSPREAD)), (40, roar), (44, roar), (46, G)]))
    c.append(clip('revert', 30, [(0, G), (6, F(G, ra=(-46, -6, 16), la=(-46, 6, -16))), (18, low), (30, F_IDLE)]))
    rake = F(G, ra=(-150, 40, 40), rf=(-30, 0, 0), **FSPREAD)
    c.append(clip('claw_1', 9, [(0, G), (2, rake), (4, F(G, ra=(-70, -50, 10), rf=(-10, 0, 0))), (6, F(G, ra=(-60, -60, 10), rf=(-14, 0, 0))), (9, G)]))
    back = F(G, la=(-90, -70, -40), lf=(-40, 0, 0), **FSPREAD)
    c.append(clip('claw_2', 9, [(0, G), (2, back), (4, F(G, la=(-86, 60, -6), lf=(-8, 0, 0))), (6, F(G, la=(-80, 70, -6), lf=(-10, 0, 0))), (9, G)]))
    hi = F(G, ra=(-170, 14, 20), rf=(-40, 0, 0), la=(-170, -14, -20), lf=(-40, 0, 0), **FSPREAD)
    dn = F(G, ra=(-60, 10, 16), rf=(-10, 0, 0), la=(-60, -10, -16), lf=(-10, 0, 0))
    c.append(clip('claw_3', 13, [(0, G), (3, hi), (5, F(hi, ra=(-164, 14, 20), la=(-164, -14, -20))), (6, dn), (9, F(dn, ra=(-56, 10, 16), la=(-56, -10, -16))), (13, G)]))
    thrust = F(G, ra=(-92, 4, 4), rf=(-4, 0, 0), rh=(10, 0, 0), **dict((k, v) for k, v in FSPREAD.items() if k in ('rfi', 'rt')))
    c.append(clip('tendril_shot', 7, [(0, G), (1, F(G, ra=(-30, 0, 20), rf=(-90, 0, 0))), (3, thrust), (5, F(thrust, ra=(-88, 4, 4))), (7, G)]))
    zp = F(G, ra=(-150, 0, 8), rf=(-6, 0, 0), rh=(0, 0, 0), la=(-20, 0, -30), lf=(-40, 0, 0), **FHOOK)
    c.append(clip('zip', LOOPS['zip'], [(0, zp), (5, F(zp, la=(-24, 0, -34))), (10, zp)], loop=True))
    pa = F(G, ra=(-90, 6, 6), rf=(-6, 0, 0), **FHOOK)
    pb = F(G, ra=(10, 20, 30), rf=(-100, 0, 0), la=(-90, -6, -6), lf=(-10, 0, 0), **FFIST)
    c.append(clip('pull', 12, [(0, pa), (3, pb), (6, F(pb, ra=(-30, 20, 30), la=(-50, -6, -6))), (9, pb), (12, G)]))
    reach = F(G, ra=(-84, -6, 8), rf=(-6, 0, 0), la=(-84, 6, -8), lf=(-6, 0, 0), **FSPREAD)
    holdp = F(G, ra=(-96, -8, 10), rf=(-34, 0, 0), la=(-60, 10, -16), lf=(-50, 0, 0), **FHOOK)
    c.append(clip('grab', 8, [(0, G), (2, F(G, ra=(-60, 0, 30), la=(-60, 0, -30))), (3, reach), (5, F(reach, **FHOOK)), (8, holdp)]))
    c.append(clip('hold', LOOPS['hold'], [(0, holdp), (10, F(holdp, ra=(-99, -8, 11))), (20, holdp)], loop=True))
    c.append(clip('grab_miss', 8, [(0, G), (2, reach), (4, F(reach, **FFIST)), (8, G)]))
    c.append(clip('throw', 12, [(0, holdp), (3, F(holdp, ra=(-170, 30, 30), rf=(-40, 0, 0))), (5, F(G, ra=(-70, -40, 6), rf=(-6, 0, 0), **FSPREAD)),
                                (8, F(G, ra=(-50, -40, 6), rf=(-10, 0, 0), **FSPREAD)), (12, G)]))
    upf = F(holdp, ra=(-178, -6, 10), rf=(-20, 0, 0), la=(-178, 6, -10), lf=(-20, 0, 0))
    dnf = F(G, ra=(-40, -6, 10), rf=(-10, 0, 0), la=(-40, 6, -10), lf=(-10, 0, 0), **FHOOK)
    c.append(clip('slam', 16, [(0, holdp), (4, upf), (6, upf), (8, dnf), (11, dnf), (16, G)]))
    sw0 = F(G, ra=(-60, 40, 60), rf=(-40, 0, 0), la=(-60, -40, -60), lf=(-40, 0, 0), **FSPREAD)
    sw1 = F(G, ra=(-80, -60, 40), rf=(-10, 0, 0), la=(-40, -40, -70), lf=(-20, 0, 0), **FSPREAD)
    c.append(clip('lash_sweep', 14, [(0, G), (3, sw0), (6, sw1), (9, sw1), (14, G)]))
    im0 = F(G, ra=(30, 10, 50), rf=(-60, 0, 0), la=(30, -10, -50), lf=(-60, 0, 0), **FSPREAD)
    im1 = F(G, ra=(-40, 20, 70), rf=(-20, 0, 0), la=(-40, -20, -70), lf=(-20, 0, 0), **FSPREAD)
    c.append(clip('lash_impale', 14, [(0, G), (4, im0), (6, im1), (9, im1), (14, G)]))
    ls0 = F(G, ra=(-176, 30, 40), rf=(-20, 0, 0), la=(-176, -30, -40), lf=(-20, 0, 0), **FSPREAD)
    ls1 = F(G, ra=(-36, 10, 30), rf=(-10, 0, 0), la=(-36, -10, -30), lf=(-10, 0, 0), **FCLAW)
    c.append(clip('lash_slam', 20, [(0, G), (5, ls0), (9, ls0), (11, ls1), (15, ls1), (20, G)]))
    shf = F(G, ra=(-100, -40, 10), rf=(-110, 0, 0), la=(-100, 40, -10), lf=(-110, 0, 0), **FFIST)
    c.append(clip('shell', LOOPS['shell'], [(0, shf), (15, F(shf, ra=(-98, -40, 10), la=(-98, 40, -10))), (30, shf)], loop=True))
    gat = F(G, ra=(-40, -30, 10), rf=(-100, 0, 0), la=(-40, 30, -10), lf=(-100, 0, 0), **FFIST)
    c.append(clip('rage', 26, [(0, G), (6, gat), (10, roar), (20, roar), (26, G)]))
    en0 = F(G, ra=(-60, 40, 80), rf=(-20, 0, 0), la=(-60, -40, -80), lf=(-20, 0, 0), **FSPREAD)
    en1 = F(G, ra=(-100, -40, 20), rf=(-60, 0, 0), la=(-100, 40, -20), lf=(-60, 0, 0), **FHOOK)
    c.append(clip('engulf', 30, [(0, G), (8, en0), (14, en0), (18, en1), (30, en1)]))
    c.append(clip('possess', LOOPS['possess'], [(0, low), (20, F(low, ra=(-28, 0, 22), la=(-28, 0, -22))), (40, low)], loop=True))
    rc = F(G, ra=(-150, -50, 10), rf=(-120, 0, 0), la=(-150, 50, -10), lf=(-120, 0, 0), **FSPREAD)
    c.append(clip('recoil', 24, [(0, G), (3, rc), (7, F(rc, ra=(-140, -40, 20), la=(-160, 60, -4))), (11, rc), (15, F(rc, ra=(-160, -60, 4), la=(-140, 40, -20))), (19, rc), (24, G)]))
    c.append(clip('reform', 14, [(0, F(G, ra=(10, 0, 30), rf=(10, 0, 0), la=(6, 0, -10), lf=(0, 0, 0), **FSPREAD)), (8, F(G, ra=(-46, -6, 12), la=(-46, 6, -12))), (14, G)]))
    br = F(G, ra=(-110, -30, 10), rf=(-80, 0, 0), la=(-110, 30, -10), lf=(-80, 0, 0), **FFIST)
    c.append(clip('clash', 14, [(0, G), (2, br), (3, F(br, ra=(-100, -30, 10), la=(-100, 30, -10))), (7, br), (14, G)]))
    tw0 = F(G, ra=(-110, -14, 4), rf=(-30, 0, 0), la=(-110, 14, -4), lf=(-30, 0, 0), **FHOOK)
    tw1 = F(G, ra=(-80, 40, 80), rf=(-20, 0, 0), la=(-80, -40, -80), lf=(-20, 0, 0), **FFIST)
    c.append(clip('tear_web', 14, [(0, G), (4, tw0), (6, tw0), (7, tw1), (10, tw1), (14, G)]))
    return c


def check(clips, fp_file=False):
    by = {c.name: c for c in clips}
    for name, (ticks, _) in MOVES.items():
        assert name in by, name
        assert abs(by[name].length - ticks / 20.0) < 1e-6, (name, by[name].length, ticks)
    for name, ticks in LOOPS.items():
        assert name in by and by[name].loop, name
        assert abs(by[name].length - ticks / 20.0) < 1e-6, (name, by[name].length, ticks)


def main():
    st, mv, fc, f = stances(), moves(), face(), fp()
    assert [c.name for c in st] and set(STANCES) == {c.name for c in st}, sorted(c.name for c in st)
    assert set(FACES) == {c.name for c in fc}
    check(mv)
    check(f, True)
    print('stances', write_anims('animations/venom/venom_stances.animation.json', st))
    placed = [n for names in FILES.values() for n in names]
    assert sorted(placed) == sorted(c.name for c in mv), 'every move clip in exactly one file'
    for file, names in FILES.items():
        print(file, write_anims(f'animations/venom/venom_{file}.animation.json', [c for c in mv if c.name in names]))
    print('face', write_anims('animations/venom/venom_face.animation.json', fc))
    print('fp', write_anims('animations/venom/venom_fp.animation.json', f))


if __name__ == '__main__':
    main()
