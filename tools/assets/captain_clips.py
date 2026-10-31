"""Third-person clips of Captain America (Steve Rogers, Avengers 2012) — own work, CC0.
Lengths and contact ticks follow CaptainMove / CaptainRules (the server clock drives the clip; times in seconds).
Shield: rendered on the empty bone left_shield (left forearm). Shield poses were solved with tools/assets/captain_preview.py:
left_arm (-70, 75, 0) + left_forearm (-60, 0, 0) = shield square in front of the chest (block),
left_arm (-10, 75, 15) + left_forearm (-30, 0, 0) = shield carried forward at the hip.
Outputs animations/captain/captain_stances, captain_combat, captain_skills .animation.json."""
from jinwoo_clips import clip, breathe, T  # noqa: F401
from jinwoo_geo import write_anims
from jinwoo_poses import P, low, mirror, FIST, RELAX, OPEN, OPEN_L, OPEN_R  # noqa: F401

# ---- key poses (Bedrock degrees, see jinwoo_poses conventions) ---------------------------------------
SH_BLOCK = dict(left_arm=(-70, 75, 0), left_forearm=(-60, 0, 0), left_hand=(0, 0, 0))
SH_CARRY = dict(left_arm=(-10, 75, 15), left_forearm=(-30, 0, 0), left_hand=(0, 0, 0))
SH_GUARD = dict(left_arm=(-42, 75, 8), left_forearm=(-48, 0, 0), left_hand=(0, 0, 0))

# soldier at ease: upright, chin level, shield on the forearm at the hip
CALM = P(root=low(0), waist=(0, 0, 0), torso=(-3, 0, 0), head=(2, 0, 0),
         right_arm=(2, 0, 4), right_forearm=(-10, 0, 0), right_hand=(0, 0, 0),
         right_leg=(0, 2, 1), right_shin=(0, 0, 0), left_leg=(0, -2, -1), left_shin=(0, 0, 0),
         **SH_CARRY, **RELAX)
# fighting stance: left shoulder (shield) forward, right fist cocked by the chin, weight low
COMBAT = P(root=low(-1.2), waist=(4, 16, 0), torso=(8, 6, 0), head=(-10, -20, 0),
           right_arm=(-40, -20, 12), right_forearm=(-90, 0, 0), right_hand=(0, 0, 0),
           right_leg=(14, 18, 6), right_shin=(22, 0, 0), left_leg=(-24, 14, -4), left_shin=(18, 0, 0),
           **SH_GUARD, **FIST)
CROUCH = P(COMBAT, root=low(-3.0), waist=(16, 14, 0), torso=(14, 4, 0), head=(-28, -18, 0),
           right_leg=(-10, 18, 6), right_shin=(62, 0, 0), left_leg=(-50, 14, -4), left_shin=(66, 0, 0))
AIR = P(COMBAT, root=low(0), waist=(-4, 8, 0), torso=(2, 2, 0), head=(-4, -10, 0),
        right_arm=(-30, -10, 36), right_forearm=(-40, 0, 0),
        right_leg=(-36, 6, 4), right_shin=(64, 0, 0), left_leg=(-8, -6, -4), left_shin=(36, 0, 0))
# sprint: chest forward, shield tucked against the forearm, right arm pumping
RUN_A = P(root=low(-0.6), waist=(14, 0, 0), torso=(12, 0, 0), head=(-22, 0, 0),
          right_arm=(46, 0, 10), right_forearm=(-50, 0, 0), right_hand=(0, 0, 0),
          left_arm=(-36, 60, 10), left_forearm=(-60, 0, 0), left_hand=(0, 0, 0),
          right_leg=(-54, 0, 0), right_shin=(24, 0, 0), left_leg=(40, 0, 0), left_shin=(80, 0, 0), **FIST)
RUN_B = P(RUN_A, right_arm=(-50, 0, 10), right_forearm=(-80, 0, 0), left_arm=(-20, 60, 10),
          right_leg=(40, 0, 0), right_shin=(80, 0, 0), left_leg=(-54, 0, 0), left_shin=(24, 0, 0))
RUN_MID_A = P(RUN_A, root=low(0.6), right_leg=(-10, 0, 0), right_shin=(14, 0, 0), left_leg=(6, 0, 0), left_shin=(96, 0, 0))
RUN_MID_B = P(RUN_B, root=low(0.6), right_leg=(6, 0, 0), right_shin=(96, 0, 0), left_leg=(-10, 0, 0), left_shin=(14, 0, 0))
SWIM_A = P(COMBAT, root=low(0), waist=(0, 0, 0), torso=(0, 0, 0), head=(-60, 0, 0),
           right_arm=(-170, 0, 30), right_forearm=(0, 0, 0), left_arm=(-60, 60, -20), left_forearm=(-40, 0, 0),
           right_leg=(10, 0, 0), right_shin=(10, 0, 0), left_leg=(-10, 0, 0), left_shin=(10, 0, 0))
SWIM_B = P(SWIM_A, right_arm=(-40, 0, 80), right_leg=(-10, 0, 0), left_leg=(10, 0, 0))

BLOCK = P(COMBAT, root=low(-1.8), waist=(6, 18, 0), torso=(8, 10, 0), head=(4, -24, 0),
          right_arm=(-24, -10, 14), right_forearm=(-80, 0, 0), **SH_BLOCK)
# no shield: forearms crossed in front of the face
BLOCK_ARMS = P(COMBAT, root=low(-1.6), waist=(6, 0, 0), torso=(10, 0, 0), head=(6, 0, 0),
               right_arm=(-80, 30, 0), right_forearm=(-80, 0, 0), left_arm=(-80, -30, 0), left_forearm=(-80, 0, 0))


def stances():
    out = []
    out.append(clip('idle_calm', 60, [(0, CALM), (30, breathe(CALM, 1.0)), (60, CALM)], loop=True))
    out.append(clip('idle_combat', 40, [(0, COMBAT), (20, P(breathe(COMBAT, 2.0), root=low(-1.6))), (40, COMBAT)], loop=True))
    out.append(clip('crouch', 40, [(0, CROUCH), (20, breathe(CROUCH, 1.5)), (40, CROUCH)], loop=True))
    out.append(clip('air', 20, [(0, AIR), (10, P(AIR, right_leg=(-30, 6, 4), left_leg=(-14, -6, -4))), (20, AIR)], loop=True))
    out.append(clip('run', 12, [(0, RUN_A), (3, RUN_MID_A), (6, RUN_B), (9, RUN_MID_B), (12, RUN_A)], loop=True))
    wa = P(CALM, right_leg=(-24, 0, 0), right_shin=(6, 0, 0), left_leg=(22, 0, 0), left_shin=(20, 0, 0),
           right_arm=(16, 0, 4), waist=(0, -3, 0))
    wb = P(CALM, right_leg=(22, 0, 0), right_shin=(20, 0, 0), left_leg=(-24, 0, 0), left_shin=(6, 0, 0),
           right_arm=(-14, 0, 4), waist=(0, 3, 0))
    wm = P(CALM, root=low(0.4), right_arm=(0, 0, 4), right_leg=(0, 0, 0), right_shin=(28, 0, 0), left_leg=(0, 0, 0), left_shin=(4, 0, 0))
    wn = P(CALM, root=low(0.4), right_arm=(0, 0, 4), right_leg=(0, 0, 0), right_shin=(4, 0, 0), left_leg=(0, 0, 0), left_shin=(28, 0, 0))
    out.append(clip('walk', 20, [(0, wa), (5, wm), (10, wb), (15, wn), (20, wa)], loop=True))
    ca = P(COMBAT, right_leg=(-2, 18, 6), right_shin=(30, 0, 0), left_leg=(-34, 14, -4), left_shin=(24, 0, 0))
    cb = P(COMBAT, right_leg=(24, 18, 6), right_shin=(20, 0, 0), left_leg=(-12, 14, -4), left_shin=(40, 0, 0))
    out.append(clip('walk_combat', 20, [(0, ca), (10, cb), (20, ca)], loop=True))
    out.append(clip('swim', 24, [(0, SWIM_A), (12, SWIM_B), (24, SWIM_A)], loop=True))
    out.append(clip('block', 20, [(0, BLOCK), (10, P(BLOCK, root=low(-2.0))), (20, BLOCK)], loop=True))
    out.append(clip('block_arms', 20, [(0, BLOCK_ARMS), (10, P(BLOCK_ARMS, root=low(-1.8))), (20, BLOCK_ARMS)], loop=True))
    return out


def combat():
    out = []
    # 1: shield bash — shoulder pulled back, then the face of the shield punched forward (9 t, contact 4)
    s1a = P(COMBAT, waist=(2, -10, 0), torso=(4, -20, 0), head=(-8, 10, 0), left_arm=(-30, 20, -30), left_forearm=(-80, 0, 0))
    s1b = P(COMBAT, root=low(-1.6), waist=(8, 30, 0), torso=(14, 20, 0), head=(-14, -40, 0),
            left_arm=(-86, 64, 0), left_forearm=(-16, 0, 0), left_leg=(-40, 14, -4), left_shin=(26, 0, 0))
    out.append(clip('shield_1', 9, [(0, COMBAT), (2, s1a), (4, s1b), (6, s1b), (9, COMBAT)]))
    # 2: backhand with the rim — across the chest, sweeping out to the left (10 t, contact 5)
    s2a = P(COMBAT, waist=(4, 34, 0), torso=(8, 24, 0), head=(-10, -40, 0), left_arm=(-80, 100, 0), left_forearm=(-70, 0, 0))
    s2b = P(COMBAT, waist=(6, -24, 0), torso=(10, -20, 0), head=(-10, 20, 0), left_arm=(-84, -30, -30), left_forearm=(-12, 0, 0),
            left_hand=(0, 0, 0))
    out.append(clip('shield_2', 10, [(0, COMBAT), (3, s2a), (5, s2b), (7, P(s2b, left_arm=(-80, -50, -36))), (10, COMBAT)]))
    # 3: shield uppercut — low and under, rising through the chin (11 t, contact 6)
    s3a = P(CROUCH, root=low(-3.4), torso=(22, 10, 0), left_arm=(10, 60, 0), left_forearm=(-50, 0, 0))
    s3b = P(COMBAT, root=low(0.6), waist=(-6, 20, 0), torso=(-12, 14, 0), head=(-20, -20, 0),
            left_arm=(-150, 50, 0), left_forearm=(-40, 0, 0), right_leg=(20, 18, 6), right_shin=(10, 0, 0))
    out.append(clip('shield_3', 11, [(0, COMBAT), (3, s3a), (6, s3b), (8, s3b), (11, COMBAT)]))
    # 4: "Freedom Crusher" — crouch, leap with a turn, two-handed shield driven down (18 t, contact 12)
    s4a = P(CROUCH, root=low(-4.0), torso=(26, 0, 0), left_arm=(10, 60, 0), right_arm=(10, 0, 20))
    s4b = P(AIR, root={'rot': (0, -120, 0), 'pos': (0, 10, 0)}, waist=(-10, 0, 0), torso=(-14, 0, 0), head=(-20, 0, 0),
            left_arm=(-170, 50, 0), left_forearm=(-40, 0, 0), right_arm=(-170, -20, 0), right_forearm=(-30, 0, 0))
    s4c = P(AIR, root={'rot': (0, -300, 0), 'pos': (0, 12, 0)}, waist=(-12, 0, 0), torso=(-18, 0, 0), head=(-24, 0, 0),
            left_arm=(-176, 60, 0), left_forearm=(-50, 0, 0), right_arm=(-176, -40, 0), right_forearm=(-60, 0, 0))
    s4d = P(CROUCH, root={'rot': (0, -360, 0), 'pos': (0, -5, 0)}, waist=(30, 0, 0), torso=(36, 0, 0), head=(-40, 0, 0),
            left_arm=(-60, 60, 0), left_forearm=(-30, 0, 0), right_arm=(-60, -30, 0), right_forearm=(-40, 0, 0),
            right_leg=(10, 18, 6), right_shin=(90, 0, 0), left_leg=(-60, 14, -4), left_shin=(70, 0, 0))
    out.append(clip('shield_4', 18, [(0, COMBAT), (3, s4a), (6, s4b), (10, s4c), (12, s4d), (15, s4d),
                                    (18, P(COMBAT, root={'rot': (0, -360, 0), 'pos': (0, -1.2, 0)}))]))
    # boxing without the shield: jab (7/3), cross (8/4), spinning heel kick (12/7)
    FREE = P(COMBAT, left_arm=(-60, 20, -8), left_forearm=(-80, 0, 0))
    j1 = P(FREE, waist=(4, 24, 0), torso=(8, 16, 0), head=(-10, -30, 0), left_arm=(-92, 16, 0), left_forearm=(-4, 0, 0))
    out.append(clip('fist_1', 7, [(0, FREE), (2, P(FREE, left_arm=(-70, 18, -6))), (3, j1), (4, j1), (7, FREE)]))
    c2 = P(FREE, root=low(-1.6), waist=(8, -26, 0), torso=(12, -26, 0), head=(-14, 34, 0),
           right_arm=(-92, -12, 0), right_forearm=(-4, 0, 0), right_leg=(30, 18, 6), right_shin=(30, 0, 0))
    out.append(clip('fist_2', 8, [(0, FREE), (2, P(FREE, waist=(4, 30, 0), right_arm=(-40, -20, 20))), (4, c2), (5, c2), (8, FREE)]))
    k1 = P(FREE, root={'rot': (0, 120, 0), 'pos': (0, 0, 0)}, waist=(0, 0, 0), torso=(-10, 0, 0))
    k2 = P(FREE, root={'rot': (0, 300, 0), 'pos': (0, 1, 0)}, waist=(0, 0, -20), torso=(-20, 0, -14), head=(10, 0, 10),
           right_leg=(-30, 0, 90), right_shin=(0, 0, 0), left_leg=(0, 0, 0), left_shin=(10, 0, 0))
    out.append(clip('fist_3', 12, [(0, FREE), (3, P(CROUCH, root=low(-2))), (5, k1), (7, k2), (9, P(k2, root={'rot': (0, 350, 0), 'pos': (0, 0, 0)})),
                                   (12, P(FREE, root={'rot': (0, 360, 0), 'pos': (0, -1.2, 0)}))]))
    # counter after a parry: shield edge swung up and over into a heavy downward chop (13 t, contact 6)
    ct1 = P(COMBAT, waist=(-6, 30, 0), torso=(-14, 24, 0), head=(-16, -36, 0), left_arm=(-176, 40, 0), left_forearm=(-50, 0, 0))
    ct2 = P(CROUCH, root=low(-3.2), waist=(28, -10, 0), torso=(30, -10, 0), head=(-34, 10, 0),
            left_arm=(-40, 20, -20), left_forearm=(-6, 0, 0), right_arm=(-20, -10, 30))
    out.append(clip('counter', 13, [(0, BLOCK), (3, ct1), (6, ct2), (9, ct2), (13, COMBAT)]))
    # return strike after the catch: spin with the shield, horizontal sweep at full extension (12 t, contact 6)
    rs1 = P(COMBAT, root={'rot': (0, 160, 0), 'pos': (0, -1, 0)}, left_arm=(-80, 100, 0), left_forearm=(-60, 0, 0))
    rs2 = P(COMBAT, root={'rot': (0, 360, 0), 'pos': (0, -2, 0)}, waist=(6, -30, 0), torso=(10, -24, 0), head=(-10, 40, 0),
            left_arm=(-88, -50, -20), left_forearm=(-6, 0, 0), right_arm=(-60, 0, 40), right_forearm=(-20, 0, 0))
    out.append(clip('return_strike', 12, [(0, COMBAT), (3, rs1), (6, rs2), (9, rs2), (12, P(COMBAT, root={'rot': (0, 360, 0), 'pos': (0, -1.2, 0)}))]))
    # spinning sweep kick out of the tactical roll (12 t, contact 6)
    sw1 = P(CROUCH, root={'rot': (0, 0, 0), 'pos': (0, -6, 0)}, right_arm=(-40, 0, 60), left_arm=(-30, 60, -30))
    sw2 = P(sw1, root={'rot': (0, 220, 0), 'pos': (0, -7, 0)}, right_leg=(-80, 0, 40), right_shin=(0, 0, 0), left_leg=(-60, 0, 0), left_shin=(110, 0, 0))
    out.append(clip('roll_sweep', 12, [(0, sw1), (3, P(sw1, root={'rot': (0, 60, 0), 'pos': (0, -6, 0)})), (6, sw2),
                                       (9, P(sw2, root={'rot': (0, 340, 0), 'pos': (0, -6, 0)})),
                                       (12, P(COMBAT, root={'rot': (0, 360, 0), 'pos': (0, -1.2, 0)}))]))
    # parry: sharp shield snap forward and out (8 t, contact 0)
    pa = P(BLOCK, waist=(8, 26, 0), left_arm=(-80, 60, -10), left_forearm=(-40, 0, 0))
    out.append(clip('parry', 8, [(0, pa), (2, P(pa, left_arm=(-86, 40, -20))), (5, BLOCK), (8, BLOCK)]))
    return out


def skills():
    out = []
    # Ricochet: frisbee throw from across the body (10 t, release at THROW_RELEASE_TICK = 4)
    th1 = P(COMBAT, waist=(4, 40, 0), torso=(6, 30, 0), head=(-10, -50, 0), left_arm=(-84, 110, 10), left_forearm=(-80, 0, 0))
    th2 = P(COMBAT, root=low(-1.6), waist=(8, -30, 0), torso=(12, -20, 0), head=(-10, 10, 0),
            left_arm=(-88, -50, -10), left_forearm=(0, 0, 0), left_hand=(0, 0, 20), left_leg=(-40, 14, -4), **OPEN_L)
    out.append(clip('throw', 10, [(0, COMBAT), (3, th1), (4, th2), (6, P(th2, left_arm=(-80, -70, -20))), (10, P(COMBAT, **OPEN_L))]))
    # aim hold (bank throw): shield drawn back across the chest, right hand points the line
    aim = P(th1, right_arm=(-90, -10, 0), right_forearm=(0, 0, 0), **OPEN_R)
    out.append(clip('aim', 20, [(0, aim), (10, P(aim, root=low(-1.4))), (20, aim)], loop=True))
    # catch: arm whips out, impact rocks the body back (9 t, contact 0)
    cat = P(COMBAT, waist=(-6, 20, 0), torso=(-10, 14, 0), left_arm=(-90, 50, -10), left_forearm=(-20, 0, 0), **OPEN_L)
    out.append(clip('catch', 9, [(0, cat), (1, P(cat, root=low(-2), torso=(-16, 20, 0))), (4, P(COMBAT, left_arm=(-50, 75, 8))), (9, COMBAT)]))
    # recall: open hand stretched out, fist closes (7 t)
    out.append(clip('recall', 7, [(0, COMBAT), (2, P(COMBAT, left_arm=(-100, 0, -20), left_forearm=(0, 0, 0), **OPEN_L)),
                                 (5, P(COMBAT, left_arm=(-96, 0, -20), left_forearm=(-10, 0, 0))), (7, COMBAT)]))
    # Shield Rush: shoulder behind the shield, driving charge
    rush_a = P(RUN_A, waist=(24, 30, 0), torso=(20, 20, 0), head=(-30, -30, 0), **SH_BLOCK)
    rush_b = P(RUN_B, waist=(24, 30, 0), torso=(20, 20, 0), head=(-30, -30, 0), **SH_BLOCK)
    out.append(clip('rush', 12, [(0, P(COMBAT, **SH_BLOCK)), (2, rush_a), (5, rush_b), (8, rush_a), (11, rush_b), (12, rush_a)]))
    bash = P(COMBAT, root=low(-2), waist=(14, 40, 0), torso=(16, 30, 0), head=(-20, -40, 0), left_arm=(-90, 60, 0), left_forearm=(-10, 0, 0))
    out.append(clip('rush_bash', 9, [(0, rush_a), (2, bash), (5, bash), (9, COMBAT)]))
    # Vibranium Slam: leap with the shield raised (rise 9 t, dive), impact kneeling, shield driven into the ground
    up = P(AIR, root=low(2), waist=(-10, 0, 0), torso=(-14, 0, 0), head=(-20, 0, 0),
           left_arm=(-170, 60, 0), left_forearm=(-30, 0, 0), right_arm=(-150, -20, 0), right_forearm=(-40, 0, 0),
           right_leg=(-60, 0, 0), right_shin=(100, 0, 0), left_leg=(-20, 0, 0), left_shin=(80, 0, 0))
    dive = P(up, waist=(20, 0, 0), torso=(30, 0, 0), head=(-40, 0, 0), left_arm=(-120, 60, 0), right_arm=(-120, -20, 0),
             right_leg=(-30, 0, 0), right_shin=(60, 0, 0), left_leg=(-10, 0, 0), left_shin=(40, 0, 0))
    out.append(clip('slam_leap', 50, [(0, P(CROUCH, root=low(-4))), (3, up), (9, up), (14, dive), (50, dive)]))
    kneel = P(CROUCH, root=low(-7), waist=(30, 0, 0), torso=(34, 0, 0), head=(-36, 0, 0),
              left_arm=(-50, 60, 0), left_forearm=(-20, 0, 0), right_arm=(-30, 0, 40), right_forearm=(-20, 0, 0), **OPEN_R,
              right_leg=(-80, 10, 0), right_shin=(90, 0, 0), left_leg=(10, -10, 0), left_shin=(100, 0, 0))
    out.append(clip('slam_impact', 14, [(0, kneel), (6, kneel), (10, P(CROUCH, root=low(-3))), (14, COMBAT)]))
    # Tactical Roll: tucked into a ball, the shield over the back. The 360 itself is not keyed here: CaptainRenderer
    # spins the whole body about the tuck centroid (ROLL_PIVOT) with the floor-contact lift table solved by
    # captain_preview.py, so the body never sinks into the floor and the fade-out never unwinds a 360 backwards.
    tuck = P(CROUCH, root=low(0), waist=(50, 0, 0), torso=(45, 0, 0), head=(30, 0, 0), right_leg=(-130, 0, 0), right_shin=(140, 0, 0),
             left_leg=(-130, 0, 0), left_shin=(140, 0, 0), left_arm=(-60, 60, 0), left_forearm=(-80, 0, 0),
             right_arm=(-80, 0, 10), right_forearm=(-90, 0, 0))
    out.append(clip('roll', 10, [(0, P(CROUCH, root=low(-2))), (1, tuck), (8, tuck), (10, CROUCH)]))
    # superhero landing: drop to a knee, fist and shield to the ground, rise
    land = P(kneel, root=low(-8))
    out.append(clip('landing', 12, [(0, land), (5, land), (12, COMBAT)]))
    # Assemble: shield raised to the sky, right arm sweeping the rally (30 t, shout at 8)
    call1 = P(CALM, torso=(-8, 0, 0), head=(-16, 0, 0), left_arm=(-160, 40, -20), left_forearm=(-20, 0, 0),
              right_arm=(-40, -30, 40), right_forearm=(-20, 0, 0), **OPEN_R)
    call2 = P(call1, right_arm=(-100, -60, 30), right_forearm=(-10, 0, 0), head=(-22, -10, 0))
    out.append(clip('assemble_call', 30, [(0, COMBAT), (5, call1), (8, call2), (20, call2), (30, P(COMBAT, head=(-26, -20, 0)))]))
    # waiting: guard, chin up following the Quinjet / the arrivals
    wait = P(COMBAT, head=(-34, -20, 0), torso=(-2, 6, 0))
    out.append(clip('assemble_wait', 120, [(0, P(COMBAT, head=(-26, -20, 0))), (30, wait), (60, P(wait, head=(-24, 10, 0))),
                                           (90, P(wait, head=(-30, -30, 0))), (120, COMBAT)]))
    # the circle: shield up, the iconic 360 (80 t, contact 10)
    hero = P(CALM, root=low(0), torso=(-6, 0, 0), head=(-6, 0, 0), right_arm=(6, 0, 12), right_forearm=(-10, 0, 0), **SH_BLOCK)
    hero2 = P(hero, root=low(-0.8), torso=(2, 10, 0), head=(-4, -10, 0), left_arm=(-80, 70, 0), left_forearm=(-56, 0, 0))
    out.append(clip('assemble_raise', 80, [(0, COMBAT), (10, hero), (40, hero2), (70, hero2), (80, COMBAT)]))
    # joint move (rework §15): Mjolnir lands on the raised shield — Steve sinks under the blow with the shield held
    # toward Thor, slides a little and straightens up (18 t, started at the impact)
    brace = P(BLOCK, root=low(-5), waist=(14, 18, 0), torso=(14, 10, 0), head=(-6, -24, 0),
              right_leg=(-50, 10, 6), right_shin=(70, 0, 0), left_leg=(-20, -10, -6), left_shin=(60, 0, 0))
    out.append(clip('team_brace', 18, [(0, P(brace, root=low(-3))), (2, brace), (6, P(brace, root=low(-4.6))), (12, P(BLOCK, root=low(-3))),
                                       (18, BLOCK)]))
    return out


def main():
    groups = {'captain_stances': stances(), 'captain_combat': combat(), 'captain_skills': skills()}
    for name, clips in groups.items():
        size = write_anims(f'animations/captain/{name}.animation.json', clips)
        print(name, len(clips), 'clips', size, 'bytes')


if __name__ == '__main__':
    main()
