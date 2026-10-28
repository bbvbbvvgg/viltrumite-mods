"""Key poses of Sung Jinwoo on the player rig (Bedrock degrees).
Conventions: arm X- = forward raise; arm Y+ sweeps a raised arm to the character's right;
right arm Z+ / left arm Z- = outward; forearm X- = elbow bend; torso X+ = lean forward;
torso/waist Y+ = turn right; leg X- = forward swing; shin X+ = knee bend.
Daggers: blade along hand -Z (with the forearm level it points up); D_FWD lays it forward,
REV = reverse (ice-pick) grip."""

REV = (180, 0, 0)
D_FWD = (70, 0, 0)
FIST = {'right_fingers': (0, 0, -95), 'left_fingers': (0, 0, 95)}
RELAX = {'right_fingers': (0, 0, -25), 'left_fingers': (0, 0, 25)}
OPEN = {'right_fingers': (0, 0, 0), 'left_fingers': (0, 0, 0)}
CLAW_R = {'right_fingers': (0, 0, -55)}
CLAW_L = {'left_fingers': (0, 0, 55)}
OPEN_R = {'right_fingers': (0, 0, 0)}
OPEN_L = {'left_fingers': (0, 0, 0)}

ZERO_BONES = ('root', 'waist', 'torso', 'head', 'right_arm', 'right_forearm', 'right_hand', 'right_dagger',
              'left_arm', 'left_forearm', 'left_hand', 'left_dagger', 'right_leg', 'right_shin', 'left_leg',
              'left_shin', 'right_fingers', 'left_fingers')


def P(base=None, **bones):
    out = dict(base or {})
    out.update(bones)
    return out


def low(y, rot=(0, 0, 0)):
    """Root offset (body drop in model px) with an optional whole-body rotation."""
    return {'rot': rot, 'pos': (0, y, 0)}


def mirror(pose):
    """Swaps right/left and mirrors Y/Z rotations (the same pose on the other side)."""
    out = {}
    for k, v in pose.items():
        nk = k.replace('right_', '#').replace('left_', 'right_').replace('#', 'left_')
        if isinstance(v, dict):
            out[nk] = {c: ((vv[0], -vv[1], -vv[2]) if c == 'rot' else (-vv[0], vv[1], vv[2]) if c == 'pos' else vv)
                       for c, vv in v.items()}
        else:
            out[nk] = (v[0], -v[1], -v[2])
    return out


# ---- stances -------------------------------------------------------------------------------------------
# hands in the trouser pockets, weight on the right leg, chin slightly down
CALM = P(
    root=low(0), waist=(0, 4, 0), torso=(-2, -3, 1), head=(5, 5, 0),
    right_arm=(-5, 4, -5), right_forearm=(-14, 0, 0), right_hand=(8, 0, 0),
    left_arm=(-4, -4, 4), left_forearm=(-13, 0, 0), left_hand=(8, 0, 0),
    right_leg=(0, 5, 2), right_shin=(0, 0, 0), left_leg=(-4, -8, -3), left_shin=(7, 0, 0),
    right_fingers={'rot': (0, 0, 0), 'scale': (0.4, 0.4, 0.4)}, left_fingers={'rot': (0, 0, 0), 'scale': (0.4, 0.4, 0.4)},
    right_dagger=(0, 0, 0), left_dagger=(0, 0, 0))

# low guard: right Fang forward at chest height, left Knight Killer reverse-grip low behind
COMBAT = P(
    root=low(-1.4), waist=(4, -22, 0), torso=(10, -6, 0), head=(-12, 26, 0),
    right_arm=(-62, 6, 6), right_forearm=(-24, 0, 0), right_hand=(-12, 0, 0), right_dagger=(55, 0, 0),
    left_arm=(-18, -14, -20), left_forearm=(-58, 0, 0), left_hand=(18, 0, 0), left_dagger=REV,
    right_leg=(-30, 22, 4), right_shin=(34, 0, 0), left_leg=(20, -10, -8), left_shin=(26, 0, 0),
    **FIST)

CROUCH = P(COMBAT, root=low(-3.2), waist=(14, -16, 0), torso=(16, -4, 0), head=(-26, 20, 0),
           right_leg=(-48, 18, 6), right_shin=(70, 0, 0), left_leg=(-6, -16, -8), left_shin=(62, 0, 0))

AIR = P(COMBAT, root=low(0), waist=(-6, -8, 0), torso=(4, 0, 0), head=(-4, 8, 0),
        right_arm=(-30, 10, 34), right_forearm=(-30, 0, 0), right_hand=(0, 0, 0), right_dagger=(40, 0, 0),
        left_arm=(-20, -10, -40), left_forearm=(-40, 0, 0), left_hand=(10, 0, 0),
        right_leg=(-40, 6, 4), right_shin=(70, 0, 0), left_leg=(-10, -6, -4), left_shin=(40, 0, 0))

# sprint: torso pitched forward, arms swept back, daggers trailing
RUN_A = P(
    root=low(-0.6), waist=(16, 0, 0), torso=(16, 0, 0), head=(-28, 0, 0),
    right_arm=(48, 0, 22), right_forearm=(-20, 0, 0), right_hand=(10, 0, 0), right_dagger=(90, 0, 0),
    left_arm=(52, 0, -22), left_forearm=(-20, 0, 0), left_hand=(10, 0, 0), left_dagger=(90, 0, 0),
    right_leg=(-52, 0, 0), right_shin=(22, 0, 0), left_leg=(38, 0, 0), left_shin=(78, 0, 0), **FIST)
RUN_B = P(RUN_A, right_leg=(38, 0, 0), right_shin=(78, 0, 0), left_leg=(-52, 0, 0), left_shin=(22, 0, 0))
RUN_MID_A = P(RUN_A, root=low(0.6), right_leg=(-10, 0, 0), right_shin=(14, 0, 0), left_leg=(6, 0, 0), left_shin=(96, 0, 0))
RUN_MID_B = P(RUN_A, root=low(0.6), right_leg=(6, 0, 0), right_shin=(96, 0, 0), left_leg=(-10, 0, 0), left_shin=(14, 0, 0))

SWIM_A = P(COMBAT, root=low(0), waist=(0, 0, 0), torso=(0, 0, 0), head=(-60, 0, 0),
           right_arm=(-170, 0, 30), right_forearm=(0, 0, 0), left_arm=(-170, 0, -30), left_forearm=(0, 0, 0),
           right_leg=(10, 0, 0), right_shin=(10, 0, 0), left_leg=(-10, 0, 0), left_shin=(10, 0, 0))
SWIM_B = P(SWIM_A, right_arm=(-40, 0, 80), left_arm=(-40, 0, -80), right_leg=(-10, 0, 0), left_leg=(10, 0, 0))
