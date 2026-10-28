"""Third-person clips of Sung Jinwoo (own work, CC0). Lengths follow JinwooMove / JinwooRules ticks
(design §9) so the server clock (actionElapsed) drives them; times are written in seconds.
Outputs animations/jinwoo/jinwoo_stances.animation.json, jinwoo_combat.animation.json,
jinwoo_skills.animation.json."""
from jinwoo_geo import Clip, write_anims
from jinwoo_poses import *  # noqa: F401,F403

T = 20.0


def clip(name, ticks, keys, loop=False, mode='monotone'):
    """keys: [(tick, pose)], each pose merged over the previous one (carry-forward), so every
    bone the clip touches is keyed at every key."""
    c = Clip(name, ticks / T, loop=loop, smooth=True)
    cur = {}
    bones = []
    for _, pose in keys:
        for b in pose:
            if b not in bones:
                bones.append(b)
    first = keys[0][1]
    for b in bones:
        cur[b] = first.get(b, (0, 0, 0))
    for t, pose in keys:
        cur = dict(cur)
        cur.update(pose)
        c.key(t / T, {b: _norm(cur[b]) for b in bones})
    c.mode = mode
    return c


def _norm(v):
    return v if isinstance(v, dict) else {'rot': v}


def breathe(base, amp=1.5):
    return P(base, torso=_add(base.get('torso', (0, 0, 0)), (-amp, 0, 0)), head=_add(base.get('head', (0, 0, 0)), (amp, 0, 0)),
             right_arm=_add(base.get('right_arm', (0, 0, 0)), (0, 0, amp * 0.6)),
             left_arm=_add(base.get('left_arm', (0, 0, 0)), (0, 0, -amp * 0.6)))


def _add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def sw(pose, **kw):
    return P(pose, **kw)


# ---- stances / locomotion -----------------------------------------------------------------------------
def stances():
    out = []
    out.append(clip('idle_calm', 60, [(0, CALM), (30, breathe(CALM)), (60, CALM)], loop=True))
    out.append(clip('idle_combat', 40, [(0, COMBAT), (20, P(breathe(COMBAT, 2.5), root=low(-1.9))), (40, COMBAT)], loop=True))
    out.append(clip('crouch', 40, [(0, CROUCH), (20, breathe(CROUCH, 2)), (40, CROUCH)], loop=True))
    out.append(clip('air', 20, [(0, AIR), (10, P(AIR, right_leg=(-34, 6, 4), left_leg=(-16, -6, -4))), (20, AIR)], loop=True))
    out.append(clip('run', 12, [(0, RUN_A), (3, RUN_MID_A), (6, RUN_B), (9, RUN_MID_B), (12, RUN_A)], loop=True))
    # calm walk: hands stay in the pockets, relaxed stride
    wa = P(CALM, right_leg=(-26, 0, 0), right_shin=(8, 0, 0), left_leg=(24, 0, 0), left_shin=(22, 0, 0),
           waist=(0, -4, 0), torso=(-2, 3, 0))
    wb = P(CALM, right_leg=(24, 0, 0), right_shin=(22, 0, 0), left_leg=(-26, 0, 0), left_shin=(8, 0, 0),
           waist=(0, 4, 0), torso=(-2, -3, 0))
    wm = P(CALM, root=low(0.4), right_leg=(0, 0, 0), right_shin=(30, 0, 0), left_leg=(0, 0, 0), left_shin=(4, 0, 0))
    wn = P(CALM, root=low(0.4), right_leg=(0, 0, 0), right_shin=(4, 0, 0), left_leg=(0, 0, 0), left_shin=(30, 0, 0))
    out.append(clip('walk', 20, [(0, wa), (5, wm), (10, wb), (15, wn), (20, wa)], loop=True))
    # combat walk: guard kept, short stalking steps
    ca = P(COMBAT, right_leg=(-38, 22, 4), right_shin=(30, 0, 0), left_leg=(12, -10, -8), left_shin=(40, 0, 0))
    cb = P(COMBAT, right_leg=(-14, 22, 4), right_shin=(50, 0, 0), left_leg=(28, -10, -8), left_shin=(18, 0, 0))
    out.append(clip('walk_combat', 20, [(0, ca), (10, cb), (20, ca)], loop=True))
    out.append(clip('swim', 24, [(0, SWIM_A), (12, SWIM_B), (24, SWIM_A)], loop=True))
    return out


# ---- dagger dance ---------------------------------------------------------------------------------------
def combat():
    out = []
    # 1: right Fang horizontal slash right -> left (6 t, contact 3)
    c1a = P(COMBAT, waist=(4, 10, 0), torso=(8, 18, 0), head=(-12, 0, 0),
            right_arm=(-80, 70, 20), right_forearm=(-50, 0, 0), right_hand=(0, 0, 0), right_dagger=D_FWD)
    c1b = P(COMBAT, waist=(6, -30, 0), torso=(10, -24, 0), head=(-12, 40, 0),
            right_arm=(-84, -40, 0), right_forearm=(-6, 0, 0), right_hand=(0, -20, 0), right_dagger=D_FWD)
    c1c = P(c1b, right_arm=(-70, -60, -10), right_forearm=(-18, 0, 0), torso=(10, -28, 0))
    out.append(clip('combo_1', 6, [(0, COMBAT), (2, c1a), (3, c1b), (5, c1c), (6, P(COMBAT, waist=(4, -26, 0)))]))
    # 2: left Knight Killer reverse-grip backhand left -> right
    c2a = P(COMBAT, waist=(4, -40, 0), torso=(8, -20, 0), head=(-12, 50, 0),
            left_arm=(-70, -60, -10), left_forearm=(-70, 0, 0), left_hand=(0, 0, 0), left_dagger=REV,
            right_arm=(-40, 10, 20), right_forearm=(-50, 0, 0))
    c2b = P(c2a, waist=(6, 10, 0), torso=(10, 22, 0), head=(-12, -16, 0),
            left_arm=(-86, 50, -4), left_forearm=(-20, 0, 0), left_hand=(0, 20, 0))
    out.append(clip('combo_2', 6, [(0, COMBAT), (2, c2a), (3, c2b), (5, P(c2b, left_arm=(-74, 64, -10))), (6, COMBAT)]))
    # 3: cross slash, both daggers from high outside down to an X (10 t, contact 5)
    c3a = P(COMBAT, root=low(0), waist=(-4, 0, 0), torso=(-8, 0, 0), head=(-4, 0, 0),
            right_arm=(-160, 30, 30), right_forearm=(-30, 0, 0), right_hand=(0, 0, 0), right_dagger=D_FWD,
            left_arm=(-160, -30, -30), left_forearm=(-30, 0, 0), left_hand=(0, 0, 0), left_dagger=D_FWD,
            right_leg=(-10, 10, 4), left_leg=(10, -10, -4), right_shin=(10, 0, 0), left_shin=(10, 0, 0))
    c3b = P(c3a, root=low(-2.5), waist=(14, 0, 0), torso=(22, 0, 0), head=(-30, 0, 0),
            right_arm=(-40, -36, -10), right_forearm=(-10, 0, 0), left_arm=(-40, 36, 10), left_forearm=(-10, 0, 0),
            right_leg=(-40, 10, 4), right_shin=(50, 0, 0), left_leg=(20, -10, -4), left_shin=(40, 0, 0))
    out.append(clip('combo_3', 10, [(0, COMBAT), (3, c3a), (5, c3b), (8, P(c3b, root=low(-2))), (10, COMBAT)]))
    # 4: full spin with both daggers out (9 t, contact 5)
    spin = P(COMBAT, right_arm=(-80, 0, 70), right_forearm=(-10, 0, 0), right_dagger=D_FWD,
             left_arm=(-80, 0, -70), left_forearm=(-10, 0, 0), left_dagger=D_FWD, waist=(0, 0, 0), torso=(6, 0, 0),
             head=(-8, 0, 0))
    out.append(clip('combo_4', 9, [(0, P(COMBAT, root=low(-1.4, (0, 0, 0)))), (2, P(spin, root=low(-2, (0, 40, 0)))),
                                   (4, P(spin, root=low(-2, (0, -140, 0)))), (5, P(spin, root=low(-2, (0, -220, 0)))),
                                   (7, P(spin, root=low(-1.6, (0, -330, 0)))), (9, P(COMBAT, root=low(-1.4, (0, -360, 0))))]))
    # 5: finisher — leap, both daggers overhead, crashing double downward stab (14 t, contact 9)
    c5a = P(COMBAT, root=low(-3), waist=(20, 0, 0), torso=(20, 0, 0), right_arm=(30, 10, 30), left_arm=(30, -10, -30),
            right_leg=(-50, 10, 0), right_shin=(80, 0, 0), left_leg=(-30, -10, 0), left_shin=(80, 0, 0))
    c5b = P(c5a, root=low(4), waist=(-14, 0, 0), torso=(-14, 0, 0), head=(4, 0, 0),
            right_arm=(-170, 10, 20), right_forearm=(-20, 0, 0), right_dagger=REV,
            left_arm=(-170, -10, -20), left_forearm=(-20, 0, 0), left_dagger=REV,
            right_leg=(-20, 0, 0), right_shin=(40, 0, 0), left_leg=(10, 0, 0), left_shin=(60, 0, 0))
    c5c = P(c5b, root=low(-4.5), waist=(30, 0, 0), torso=(34, 0, 0), head=(-40, 0, 0),
            right_arm=(-50, -10, 10), right_forearm=(-30, 0, 0), left_arm=(-50, 10, -10), left_forearm=(-30, 0, 0),
            right_leg=(-70, 10, 6), right_shin=(100, 0, 0), left_leg=(10, -10, -6), left_shin=(90, 0, 0))
    out.append(clip('combo_5', 14, [(0, COMBAT), (3, c5a), (6, c5b), (9, c5c), (12, c5c), (14, COMBAT)]))
    # low sweep: drop under the guard, Fang slicing at the legs (8 t, contact 4)
    lsa = P(CROUCH, root=low(-5), right_arm=(-30, 70, 40), right_forearm=(-20, 0, 0), right_dagger=D_FWD)
    lsb = P(lsa, waist=(20, -40, 0), torso=(24, -20, 0), right_arm=(-50, -60, 10), right_forearm=(-5, 0, 0))
    out.append(clip('low_sweep', 8, [(0, COMBAT), (2, lsa), (4, lsb), (6, lsb), (8, COMBAT)]))
    # aerial slashes
    a1a = P(AIR, right_arm=(-170, 30, 10), right_forearm=(-30, 0, 0), right_dagger=D_FWD, torso=(-10, 10, 0))
    a1b = P(AIR, right_arm=(-40, -30, -10), right_forearm=(-10, 0, 0), right_dagger=D_FWD, torso=(20, -10, 0), waist=(10, 0, 0))
    out.append(clip('air_1', 6, [(0, AIR), (2, a1a), (3, a1b), (6, AIR)]))
    a2a = P(AIR, left_arm=(-90, -80, -10), left_forearm=(-40, 0, 0), left_dagger=REV, torso=(4, -30, 0))
    a2b = P(AIR, left_arm=(-90, 60, 0), left_forearm=(-10, 0, 0), left_dagger=REV, torso=(4, 30, 0))
    out.append(clip('air_2', 6, [(0, AIR), (2, a2a), (3, a2b), (6, AIR)]))
    # dive: head-down plunge, both daggers reversed below
    dv = P(AIR, root=low(0, (0, 0, 0)), waist=(30, 0, 0), torso=(30, 0, 0), head=(-50, 0, 0),
           right_arm=(-150, 0, 14), right_forearm=(-10, 0, 0), right_dagger=REV,
           left_arm=(-150, 0, -14), left_forearm=(-10, 0, 0), left_dagger=REV,
           right_leg=(10, 0, 4), right_shin=(30, 0, 0), left_leg=(20, 0, -4), left_shin=(40, 0, 0))
    out.append(clip('dive', 40, [(0, AIR), (4, dv), (40, dv)]))
    land = P(CROUCH, root=low(-6), waist=(30, 0, 0), torso=(30, 0, 0), head=(-44, 0, 0),
             right_arm=(-40, 0, 20), right_forearm=(-10, 0, 0), right_dagger=REV,
             left_arm=(-40, 0, -20), left_forearm=(-10, 0, 0), left_dagger=REV,
             right_leg=(-70, 14, 6), right_shin=(110, 0, 0), left_leg=(-10, -14, -6), left_shin=(110, 0, 0))
    out.append(clip('dive_land', 8, [(0, land), (3, land), (8, COMBAT)]))
    # riposte: guard broken open, instant Fang thrust (6 t, contact 2)
    rp = P(COMBAT, waist=(8, -30, 0), torso=(14, -14, 0), head=(-20, 40, 0), right_arm=(-90, -6, 0), right_forearm=(0, 0, 0),
           right_hand=(-10, 0, 0), right_dagger=(90, 0, 0), right_leg=(-44, 22, 4), right_shin=(30, 0, 0))
    out.append(clip('riposte', 6, [(0, BLOCK), (2, rp), (4, rp), (6, COMBAT)]))
    out.append(clip('block', 10, [(0, BLOCK), (5, P(BLOCK, root=low(-2.3))), (10, BLOCK)], loop=True))
    # draw: hands leave the pockets, shadows gather into the daggers (12 t)
    dr1 = P(CALM, right_arm=(-20, 0, 30), right_forearm=(-30, 0, 0), left_arm=(-20, 0, -30), left_forearm=(-30, 0, 0),
            right_fingers=(0, 0, 0), left_fingers=(0, 0, 0))
    dr2 = P(COMBAT, right_arm=(-50, 30, 40), left_arm=(-30, -30, -40))
    out.append(clip('draw', 12, [(0, CALM), (4, dr1), (8, dr2), (12, COMBAT)]))
    out.append(clip('sheath', 8, [(0, COMBAT), (4, dr1), (8, CALM)]))
    # shadow step: a low lunge, body almost horizontal
    st = P(RUN_A, root=low(-3), waist=(30, 0, 0), torso=(24, 0, 0), head=(-40, 0, 0),
           right_leg=(-60, 0, 0), right_shin=(50, 0, 0), left_leg=(40, 0, 0), left_shin=(30, 0, 0))
    out.append(clip('step', 8, [(0, COMBAT), (2, st), (6, st), (8, COMBAT)]))
    return out


BLOCK = P(COMBAT, root=low(-1.8), waist=(8, 0, 0), torso=(12, 0, 0), head=(-14, 0, 0),
          right_arm=(-90, -40, 0), right_forearm=(-60, 0, 0), right_hand=(0, 0, 0), right_dagger=(0, 0, 90),
          left_arm=(-90, 40, 0), left_forearm=(-60, 0, 0), left_hand=(0, 0, 0), left_dagger=(0, 0, -90))


# ---- skills ---------------------------------------------------------------------------------------------
def skills():
    out = []
    # Mutilation: approach (4 t), 8 alternating hits (16 t), final crossing cut (+6)
    keys = [(0, COMBAT), (4, P(RUN_A, root=low(-2)))]
    for i in range(8):
        t = 4 + i * 2
        if i % 2 == 0:
            k = P(COMBAT, waist=(8, -20 + i, 0), torso=(12, -20, 0), head=(-16, 30, 0), right_arm=(-90, -40 + i * 4, 0),
                  right_forearm=(-10, 0, 0), right_dagger=D_FWD, left_arm=(-30, -10, -30), left_forearm=(-60, 0, 0))
        else:
            k = P(COMBAT, waist=(8, 20 - i, 0), torso=(12, 20, 0), head=(-16, -20, 0), left_arm=(-90, 40 - i * 4, 0),
                  left_forearm=(-10, 0, 0), left_dagger=REV, right_arm=(-30, 10, 30), right_forearm=(-60, 0, 0))
        keys.append((t + 1, k))
    fin = P(COMBAT, root=low(-3), waist=(20, 0, 0), torso=(24, 0, 0), head=(-34, 0, 0),
            right_arm=(-60, -50, -10), right_forearm=(-5, 0, 0), right_dagger=D_FWD,
            left_arm=(-60, 50, 10), left_forearm=(-5, 0, 0), left_dagger=D_FWD)
    keys += [(21, P(COMBAT, right_arm=(-150, 40, 30), left_arm=(-150, -40, -30), right_dagger=D_FWD, left_dagger=D_FWD)),
             (22, fin), (26, fin)]
    out.append(clip('mutilation', 26, keys))
    # Ruler's Authority: left palm thrust out, fingers clawing the air; slam drives it down
    grip = P(COMBAT, root=low(-1), waist=(0, 20, 0), torso=(0, 14, 0), head=(-6, -30, 0),
             left_arm=(-96, -6, -6), left_forearm=(-4, 0, 0), left_hand=(-40, 0, 0), left_dagger=(0, 0, 0),
             right_arm=(-20, 10, 16), right_forearm=(-40, 0, 0), **CLAW_L)
    tremble = P(grip, left_arm=(-98, -4, -6), left_hand=(-44, 0, 0), **{'left_fingers': (0, 0, 70)})
    out.append(clip('authority_grip', 60, [(0, COMBAT), (5, P(grip, **OPEN_L)), (10, grip), (35, tremble), (60, grip)]))
    slam = P(grip, root=low(-3), waist=(20, 20, 0), torso=(20, 14, 0), head=(-26, -30, 0),
             left_arm=(-30, -6, -10), left_forearm=(-10, 0, 0), left_hand=(-10, 0, 0), **{'left_fingers': (0, 0, 100)})
    out.append(clip('authority_slam', 10, [(0, P(grip, left_arm=(-160, -6, -6))), (4, slam), (10, COMBAT)]))
    # Extraction: right palm over the remnant, then rising "Arise" (30 t channel + 6)
    ex = P(CALM, root=low(-0.5), waist=(6, 0, 0), torso=(10, 0, 0), head=(16, 0, 0),
           right_arm=(-60, 10, 6), right_forearm=(-10, 0, 0), right_hand=(40, 0, 0), right_fingers=(0, 0, 0))
    ex2 = P(ex, right_arm=(-56, 10, 6), right_hand=(46, 0, 0), right_fingers=(0, 0, -20))
    rise = P(CALM, waist=(-4, 0, 0), torso=(-6, 0, 0), head=(-10, 0, 0), right_arm=(-140, 6, 10), right_forearm=(-10, 0, 0),
             right_hand=(-30, 0, 0), right_fingers=(0, 0, 0))
    out.append(clip('extraction', 36, [(0, CALM), (6, ex), (18, ex2), (28, ex), (31, rise), (36, rise)]))
    # Shadow Exchange: sink into the shadow (10 t) and rise elsewhere (10 t)
    sink = P(CROUCH, root=low(-30), head=(-10, 0, 0))
    out.append(clip('exchange', 20, [(0, COMBAT), (3, CROUCH), (10, sink), (11, P(sink, root=low(-30))), (17, P(CROUCH, root=low(-2))), (20, COMBAT)]))
    # Ultimate "Arise": straighten (0-20), arms spread wide raising the legion (20-60), point forward (60-75), hold
    stand = P(CALM, root=low(0), waist=(-2, 0, 0), torso=(-6, 0, 0), head=(-8, 0, 0))
    spread = P(stand, right_arm=(-30, 0, 70), right_forearm=(-10, 0, 0), right_hand=(0, 0, -30),
               left_arm=(-30, 0, -70), left_forearm=(-10, 0, 0), left_hand=(0, 0, 30), **OPEN)
    raise_ = P(spread, right_arm=(-70, 0, 100), left_arm=(-70, 0, -100), torso=(-12, 0, 0), head=(-18, 0, 0))
    point = P(stand, torso=(6, 0, 0), head=(-6, 0, 0), right_arm=(-96, -10, 0), right_forearm=(0, 0, 0), right_hand=(0, 0, 0),
              right_fingers=(0, 0, -40), left_arm=(-10, 0, -20), left_forearm=(-20, 0, 0))
    out.append(clip('arise', 100, [(0, COMBAT), (10, stand), (20, stand), (24, spread), (45, raise_), (58, raise_),
                                   (62, point), (75, point), (88, P(point, right_arm=(-70, -10, 10))), (100, COMBAT)]))
    # Army: summon (palm sweeps down, 16 t), recall (fist closes, 14 t)
    sm1 = P(COMBAT, right_arm=(-150, 0, 40), right_forearm=(-20, 0, 0), right_hand=(0, 0, 0), right_dagger=(0, 0, 0), **OPEN_R)
    sm2 = P(COMBAT, root=low(-2.4), waist=(10, 0, 0), torso=(16, 0, 0), right_arm=(-40, 0, 50), right_forearm=(0, 0, 0),
            right_hand=(30, 0, 0), **OPEN_R)
    out.append(clip('summon', 16, [(0, COMBAT), (4, sm1), (6, sm2), (12, sm2), (16, COMBAT)]))
    rc1 = P(COMBAT, right_arm=(-90, 30, 30), right_forearm=(0, 0, 0), right_hand=(0, 0, 0), **OPEN_R)
    rc2 = P(COMBAT, right_arm=(-70, -10, 0), right_forearm=(-80, 0, 0), right_hand=(0, 0, 0), right_fingers=(0, 0, -100))
    out.append(clip('recall', 14, [(0, COMBAT), (4, rc1), (9, rc2), (14, COMBAT)]))
    # commands: attack (dagger thrust forward), guard (arm sweep around), sentinel (point then down)
    ca = P(COMBAT, waist=(6, -20, 0), torso=(10, -10, 0), head=(-10, 30, 0), right_arm=(-96, 4, 0), right_forearm=(0, 0, 0),
           right_hand=(0, 0, 0), right_dagger=(90, 0, 0))
    out.append(clip('command_attack', 12, [(0, COMBAT), (3, P(ca, right_arm=(-120, 4, 10))), (4, ca), (9, ca), (12, COMBAT)]))
    cg1 = P(COMBAT, waist=(0, 30, 0), torso=(0, 10, 0), right_arm=(-80, 80, 10), right_forearm=(-10, 0, 0), **OPEN_R)
    cg2 = P(COMBAT, waist=(0, -20, 0), torso=(0, -10, 0), right_arm=(-80, -50, 0), right_forearm=(-10, 0, 0), **OPEN_R)
    out.append(clip('command_guard', 12, [(0, COMBAT), (2, cg1), (6, cg2), (9, cg2), (12, COMBAT)]))
    cs1 = P(COMBAT, right_arm=(-100, 0, 0), right_forearm=(0, 0, 0), right_hand=(0, 0, 0), right_fingers=(0, 0, -40))
    cs2 = P(cs1, right_arm=(-40, 0, 10), right_hand=(30, 0, 0), right_fingers=(0, 0, 0))
    out.append(clip('command_sentinel', 12, [(0, COMBAT), (3, cs1), (6, cs1), (8, cs2), (12, COMBAT)]))
    # Monarch's Charge: wind-up low (16 t charge) then explosive lunge with both daggers forward (+6)
    mc1 = P(CROUCH, root=low(-4), waist=(26, 0, 0), torso=(26, 0, 0), head=(-40, 0, 0), right_arm=(50, 0, 30),
            left_arm=(50, 0, -30), right_dagger=(90, 0, 0), left_dagger=(90, 0, 0))
    mc2 = P(RUN_A, root=low(-2), waist=(36, 0, 0), torso=(30, 0, 0), head=(-50, 0, 0),
            right_arm=(-110, -10, 10), right_forearm=(0, 0, 0), right_dagger=(90, 0, 0),
            left_arm=(-110, 10, -10), left_forearm=(0, 0, 0), left_dagger=(90, 0, 0),
            right_leg=(-40, 0, 0), right_shin=(30, 0, 0), left_leg=(50, 0, 0), left_shin=(20, 0, 0))
    out.append(clip('monarch_charge', 22, [(0, COMBAT), (6, mc1), (15, P(mc1, root=low(-4.6))), (16, mc2), (20, mc2), (22, COMBAT)]))
    return out


def main():
    groups = {'jinwoo_stances': stances(), 'jinwoo_combat': combat(), 'jinwoo_skills': skills()}
    for name, clips in groups.items():
        size = write_anims(f'animations/jinwoo/{name}.animation.json', clips)
        print(name, len(clips), 'clips', size, 'bytes')


if __name__ == '__main__':
    main()
