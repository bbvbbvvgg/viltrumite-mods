"""Clips of the five summoned Avengers (own work, CC0) — one file per kind, clip names = AvengerAction.clip()
plus the locomotion set (idle, walk, run, fly). Lengths follow the brains (start(action, ticks)) and the
assemble scene (LAND 20, CIRCLE 80, DEPART 90); variable-length actions (leap) hold their last key.
Outputs animations/avengers/<kind>.animation.json and quinjet.animation.json."""
from jinwoo_clips import clip, breathe
from jinwoo_geo import write_anims
from jinwoo_poses import P, low, mirror, FIST, RELAX, OPEN, OPEN_L, OPEN_R  # noqa: F401


def spin(base, bone, t0, t1, axis, turns, rest=(0, 0, 0)):
    """Keys spinning one bone `turns` full revolutions between t0 and t1 (90 degrees per key)."""
    keys = []
    n = int(turns * 4)
    for i in range(n + 1):
        t = t0 + (t1 - t0) * i / n
        a = -90 * i
        r = list(rest)
        r[axis] += a
        keys.append((t, P(base, **{bone: tuple(r)})))
    return keys


def common(out, idle, walk_a, walk_b, run_a, run_b, hurt, depart_keys, extra_idle=None):
    out.append(clip('idle', 60, [(0, idle), (30, breathe(idle, 1.5)), (60, idle)], loop=True))
    out.append(clip('walk', 20, [(0, walk_a), (10, walk_b), (20, walk_a)], loop=True))
    out.append(clip('run', 12, [(0, run_a), (6, run_b), (12, run_a)], loop=True))
    out.append(clip('hurt', 8, [(0, idle), (2, hurt), (8, idle)]))
    out.append(clip('depart', 90, depart_keys))


def legs(base, a, b, shin_a=8, shin_b=24):
    return (P(base, right_leg=(-a, 0, 0), right_shin=(shin_a, 0, 0), left_leg=(b, 0, 0), left_shin=(shin_b, 0, 0)),
            P(base, right_leg=(b, 0, 0), right_shin=(shin_b, 0, 0), left_leg=(-a, 0, 0), left_shin=(shin_a, 0, 0)))


def run_pair(base, lean=14):
    a = P(base, root=low(-0.6), waist=(lean, 0, 0), torso=(lean * 0.8, 0, 0), head=(-lean * 1.6, 0, 0),
          right_arm=(46, 0, 10), right_forearm=(-50, 0, 0), left_arm=(-46, 0, -10), left_forearm=(-60, 0, 0),
          right_leg=(-54, 0, 0), right_shin=(24, 0, 0), left_leg=(40, 0, 0), left_shin=(80, 0, 0))
    b = P(a, right_arm=(-46, 0, 10), right_forearm=(-60, 0, 0), left_arm=(46, 0, -10), left_forearm=(-50, 0, 0),
          right_leg=(40, 0, 0), right_shin=(80, 0, 0), left_leg=(-54, 0, 0), left_shin=(24, 0, 0))
    return a, b


LAND3 = dict(root=low(-8), waist=(30, 0, 0), torso=(30, 0, 0), head=(-34, 0, 0),
             right_leg=(-80, 10, 0), right_shin=(90, 0, 0), left_leg=(10, -10, 0), left_shin=(100, 0, 0))


# =================================================================================== IRON MAN
def iron_man():
    out = []
    hover = P(root=low(0), waist=(4, 0, 0), torso=(2, 0, 0), head=(-4, 0, 0),
              right_arm=(10, 0, 14), right_forearm=(-10, 0, 0), right_hand=(60, 0, 0),
              left_arm=(10, 0, -14), left_forearm=(-10, 0, 0), left_hand=(60, 0, 0),
              right_leg=(4, 0, 2), right_shin=(14, 0, 0), left_leg=(-6, 0, -2), left_shin=(6, 0, 0),
              faceplate=(0, 0, 0), shoulder_right=(0, 0, 0), shoulder_left=(0, 0, 0), back_flaps=(0, 0, 0), **OPEN)
    idle = P(hover, right_hand=(10, 0, 0), left_hand=(10, 0, 0), right_shin=(0, 0, 0), left_shin=(0, 0, 0), right_leg=(0, 0, 2),
             left_leg=(0, 0, -2), **RELAX)
    wa, wb = legs(idle, 22, 20)
    ra, rb = run_pair(idle)
    hurt = P(hover, torso=(-16, 0, 0), head=(-10, 0, 0), right_arm=(-20, 0, 30), left_arm=(-20, 0, -30))
    up = P(hover, torso=(-4, 0, 0), head=(-30, 0, 0), right_arm=(14, 0, 10), left_arm=(14, 0, -10), back_flaps=(-30, 0, 0))
    common(out, idle, wa, wb, ra, rb, hurt, [(0, hover), (10, P(LAND3, **{k: v for k, v in hover.items() if 'leg' not in k and 'shin' not in k and k != 'root'})),
                                            (20, up), (90, up)])
    # flight in the fight: hovering, palms down for the thrusters, gentle bob
    out.append(clip('fly', 40, [(0, hover), (20, P(hover, root=low(0.6), right_arm=(14, 0, 16), left_arm=(14, 0, -16))), (40, hover)], loop=True))
    # arrival: horizontal superman flight, arms back, then braking upright
    sup = P(hover, root={'rot': (70, 0, 0), 'pos': (0, 0, 0)}, head=(-60, 0, 0), right_arm=(20, 0, 10), left_arm=(20, 0, -10),
            right_hand=(70, 0, 0), left_hand=(70, 0, 0), back_flaps=(-40, 0, 0))
    out.append(clip('arrive', 200, [(0, sup), (40, sup), (54, P(hover, root={'rot': (20, 0, 0), 'pos': (0, 0, 0)})), (200, hover)]))
    # three-point landing: knee and right fist to the ground, palm repulsor of the left hand back
    land = P(hover, **LAND3, right_arm=(-30, 0, 20), right_forearm=(-10, 0, 0), right_hand=(0, 0, 0), left_arm=(40, 0, -30),
             left_forearm=(-10, 0, 0), right_fingers=(0, 0, -95))
    out.append(clip('land', 20, [(0, land), (8, land), (20, idle)]))
    # circle: stands up, the faceplate lifts (tick 20), a nod; hands at the sides
    out.append(clip('circle', 80, [(0, idle), (14, idle), (22, P(idle, faceplate=(-110, 0, 0))), (40, P(idle, faceplate=(-110, 0, 0), head=(6, 0, 0))),
                                   (80, P(idle, faceplate=(-110, 0, 0)))]))
    # repulsor: right palm out (10 t, shot at 3) / both palms (double)
    rp = P(hover, torso=(2, -16, 0), head=(-4, 16, 0), right_arm=(-90, -10, 0), right_forearm=(0, 0, 0), right_hand=(-70, 0, 0), **OPEN)
    out.append(clip('attack_1', 10, [(0, hover), (2, rp), (4, P(rp, right_arm=(-100, -10, 0))), (7, rp), (10, hover)]))
    dp = P(hover, right_arm=(-90, -10, 0), right_forearm=(0, 0, 0), right_hand=(-70, 0, 0), left_arm=(-90, 10, 0), left_forearm=(0, 0, 0),
           left_hand=(-70, 0, 0), **OPEN)
    out.append(clip('attack_2', 10, [(0, hover), (2, dp), (4, P(dp, right_arm=(-100, -10, 0), left_arm=(-100, 10, 0))), (7, dp), (10, hover)]))
    # thruster punch (12 t)
    pc = P(hover, root={'rot': (30, 0, 0), 'pos': (0, 0, 0)}, torso=(10, -20, 0), right_arm=(-96, 0, 0), right_forearm=(0, 0, 0),
           right_hand=(0, 0, 0), left_arm=(30, 0, -20), back_flaps=(-40, 0, 0), **FIST)
    out.append(clip('attack_3', 12, [(0, hover), (3, P(hover, right_arm=(20, 0, 20), right_forearm=(-90, 0, 0), **FIST)), (5, pc), (9, pc), (12, hover)]))
    # micro-missiles: shoulder pods open, arms spread (24 t)
    ms = P(hover, torso=(-6, 0, 0), right_arm=(-20, 0, 40), left_arm=(-20, 0, -40), shoulder_right=(0, 0, 0), shoulder_left=(0, 0, 0))
    pods = P(ms, shoulder_right={'rot': (-40, 0, 0), 'pos': (0, 1.2, 0)}, shoulder_left={'rot': (-40, 0, 0), 'pos': (0, 1.2, 0)})
    out.append(clip('special_1', 24, [(0, hover), (4, ms), (6, pods), (18, pods), (24, hover)]))
    # unibeam: chest thrust forward, arms swept back, fists clenched (44 t channel)
    ub = P(hover, waist=(-10, 0, 0), torso=(-20, 0, 0), head=(10, 0, 0), right_arm=(40, 0, 30), right_forearm=(-20, 0, 0),
           left_arm=(40, 0, -30), left_forearm=(-20, 0, 0), right_hand=(0, 0, 0), left_hand=(0, 0, 0), **FIST)
    out.append(clip('special_2', 44, [(0, hover), (6, ub), (38, P(ub, torso=(-22, 0, 0))), (44, hover)]))
    out.append(clip('special_3', 24, [(0, hover), (24, hover)]))
    out.append(clip('dodge', 12, [(0, hover), (4, P(hover, root={'rot': (0, 0, 40), 'pos': (0, 0, 0)})), (12, hover)]))
    out.append(clip('leap', 40, [(0, hover), (40, hover)]))
    out.append(clip('shoot', 10, [(0, hover), (2, rp), (7, rp), (10, hover)]))
    return out


# =================================================================================== THOR
def thor():
    out = []
    idle = P(root=low(0), waist=(0, 4, 0), torso=(-4, 0, 0), head=(2, -4, 0),
             right_arm=(-6, 0, 8), right_forearm=(-18, 0, 0), right_hand=(0, 0, 0), hammer=(0, 0, 0),
             left_arm=(0, 0, -6), left_forearm=(-10, 0, 0), left_hand=(0, 0, 0), cape=(6, 0, 0), cape_lower=(4, 0, 0),
             right_leg=(0, 4, 2), right_shin=(0, 0, 0), left_leg=(-4, -6, -3), left_shin=(4, 0, 0),
             right_fingers=(0, 0, -95), left_fingers=(0, 0, 25))
    combat = P(idle, root=low(-1.4), waist=(6, -20, 0), torso=(8, -10, 0), head=(-12, 26, 0),
               right_arm=(-40, 0, 20), right_forearm=(-60, 0, 0), hammer=(40, 0, 0),
               left_arm=(-30, 20, -20), left_forearm=(-40, 0, 0), left_fingers=(0, 0, 95),
               right_leg=(20, -10, 6), right_shin=(24, 0, 0), left_leg=(-26, 20, -6), left_shin=(24, 0, 0))
    wa, wb = legs(idle, 24, 22)
    ra, rb = run_pair(combat)
    ra, rb = P(ra, right_arm=(-30, 0, 20), right_forearm=(-60, 0, 0), hammer=(40, 0, 0)), P(rb, right_arm=(-30, 0, 20), right_forearm=(-60, 0, 0), hammer=(40, 0, 0))
    hurt = P(combat, torso=(-14, 0, 0), head=(-10, 0, 0))
    spin_up = P(idle, right_arm=(-170, 0, 10), right_forearm=(0, 0, 0), head=(-30, 0, 0), cape=(30, 0, 0))
    dep = [(0, idle), (6, P(idle, right_arm=(-100, 0, 40), right_forearm=(0, 0, 0)))]
    dep += spin(P(idle, right_arm=(-100, 0, 40), right_forearm=(0, 0, 0)), 'hammer', 6, 30, 1, 6)
    dep += [(34, spin_up), (90, spin_up)]
    common(out, combat, wa, wb, ra, rb, hurt, dep)
    out.append(clip('fly', 20, [(0, spin_up), (20, spin_up)], loop=True))
    out.append(clip('arrive', 200, [(0, P(spin_up, root=low(0))), (200, spin_up)]))
    # landing from the bolt: kneel, Mjolnir to the ground, rise slowly
    land = P(idle, **LAND3, right_arm=(-40, 0, 10), right_forearm=(-10, 0, 0), hammer=(70, 0, 0), left_arm=(10, 0, -30),
             cape=(-20, 0, 0), cape_lower=(-10, 0, 0))
    out.append(clip('land', 20, [(0, land), (10, land), (20, combat)]))
    # circle: the hammer spin by its strap at his side (ticks 10-60), lightning held up at the end
    side = P(idle, right_arm=(-30, 0, 30), right_forearm=(0, 0, 0))
    keys = [(0, combat), (8, side)] + spin(side, 'hammer', 10, 58, 0, 8) + [(66, P(spin_up, head=(-20, 0, 0))), (80, P(spin_up, head=(-20, 0, 0)))]
    out.append(clip('circle', 80, keys))
    # swings: right->left (12), back-hand (12), overhead smash (20)
    w1a = P(combat, waist=(4, 30, 0), torso=(6, 20, 0), right_arm=(-90, 80, 30), right_forearm=(-20, 0, 0), hammer=(80, 0, 0))
    w1b = P(combat, waist=(8, -40, 0), torso=(10, -30, 0), right_arm=(-90, -50, 10), right_forearm=(-10, 0, 0), hammer=(80, 0, 0))
    out.append(clip('attack_1', 12, [(0, combat), (3, w1a), (6, w1b), (9, w1b), (12, combat)]))
    out.append(clip('attack_2', 12, [(0, combat), (3, w1b), (6, w1a), (9, w1a), (12, combat)]))
    ov = P(combat, waist=(-8, 0, 0), torso=(-16, 0, 0), right_arm=(-176, 0, 10), right_forearm=(-30, 0, 0), left_arm=(-176, 0, -10),
           left_forearm=(-30, 0, 0), hammer=(80, 0, 0))
    sm = P(combat, root=low(-4), waist=(30, 0, 0), torso=(30, 0, 0), head=(-40, 0, 0), right_arm=(-60, 0, 0), right_forearm=(0, 0, 0),
           left_arm=(-60, 0, 0), left_forearm=(0, 0, 0), hammer=(90, 0, 0))
    out.append(clip('attack_3', 20, [(0, combat), (6, ov), (10, sm), (15, sm), (20, combat)]))
    # hammer throw: wind (0-6), release at THROW_RELEASE 7, open hand waiting, catch jolt
    wind = P(combat, waist=(0, -40, 0), torso=(-6, -20, 0), right_arm=(-160, -20, 30), right_forearm=(-30, 0, 0), hammer=(60, 0, 0))
    rel = P(combat, waist=(10, 20, 0), torso=(14, 20, 0), right_arm=(-90, 0, 0), right_forearm=(0, 0, 0), right_fingers=(0, 0, 0))
    wait = P(rel, right_arm=(-80, 10, 10), right_fingers=(0, 0, -30))
    out.append(clip('special_1', 90, [(0, combat), (5, wind), (7, rel), (14, wait), (80, wait), (90, combat)]))
    # lightning storm: Mjolnir raised to the sky (34 t)
    sky = P(idle, torso=(-10, 0, 0), head=(-36, 0, 0), right_arm=(-176, 0, 0), right_forearm=(0, 0, 0), hammer=(180, 0, 0),
            left_arm=(-20, 0, -30), cape=(20, 0, 0))
    out.append(clip('special_2', 34, [(0, combat), (6, sky), (28, sky), (34, combat)]))
    out.append(clip('special_3', 40, [(0, combat), (40, combat)]))
    # hammer flight onto a far target (60 t): spin, then pulled forward by the arm
    pull = P(idle, root={'rot': (60, 0, 0), 'pos': (0, 0, 0)}, head=(-50, 0, 0), right_arm=(-170, 0, 0), right_forearm=(0, 0, 0),
             hammer=(180, 0, 0), cape=(60, 0, 0), cape_lower=(30, 0, 0), right_leg=(10, 0, 0), left_leg=(-10, 0, 0))
    out.append(clip('leap', 60, spin(P(combat, right_arm=(-100, 0, 40), right_forearm=(0, 0, 0)), 'hammer', 0, 10, 1, 3) +
                    [(14, pull), (50, pull), (56, P(land, hammer=(90, 0, 0))), (60, P(land, hammer=(90, 0, 0)))]))
    out.append(clip('dodge', 12, [(0, combat), (12, combat)]))
    out.append(clip('shoot', 10, [(0, combat), (10, combat)]))
    return out


# =================================================================================== HULK
def hulk():
    out = []
    idle = P(root=low(0), waist=(14, 0, 0), torso=(16, 0, 0), head=(-26, 0, 0),
             right_arm=(-10, 0, 14), right_forearm=(-26, 0, 0), right_hand=(0, 0, 0),
             left_arm=(-10, 0, -14), left_forearm=(-26, 0, 0), left_hand=(0, 0, 0),
             right_leg=(-6, 6, 4), right_shin=(14, 0, 0), left_leg=(4, -6, -4), left_shin=(12, 0, 0), **FIST)
    wa = P(idle, waist=(14, -8, 0), right_leg=(-22, 6, 4), right_shin=(10, 0, 0), left_leg=(18, -6, -4), left_shin=(26, 0, 0),
           right_arm=(14, 0, 14), left_arm=(-24, 0, -14))
    wb = P(idle, waist=(14, 8, 0), right_leg=(18, 6, 4), right_shin=(26, 0, 0), left_leg=(-22, -6, -4), left_shin=(10, 0, 0),
           right_arm=(-24, 0, 14), left_arm=(14, 0, -14))
    ra, rb = run_pair(idle, lean=26)
    ra, rb = P(ra, right_arm=(-60, 0, 20), left_arm=(30, 0, -20)), P(rb, right_arm=(30, 0, 20), left_arm=(-60, 0, -20))
    hurt = P(idle, torso=(0, 0, 0), head=(-10, 0, 0))
    crouch = P(idle, root=low(-6), waist=(30, 0, 0), torso=(30, 0, 0), head=(-40, 0, 0), right_arm=(30, 0, 20), left_arm=(30, 0, -20),
               right_leg=(-50, 10, 6), right_shin=(80, 0, 0), left_leg=(-50, -10, -6), left_shin=(80, 0, 0))
    airp = P(idle, root=low(0), waist=(-6, 0, 0), torso=(-10, 0, 0), head=(-20, 0, 0), right_arm=(-150, 0, 30), left_arm=(-150, 0, -30),
             right_forearm=(-10, 0, 0), left_forearm=(-10, 0, 0), right_leg=(-30, 0, 0), right_shin=(60, 0, 0), left_leg=(10, 0, 0), left_shin=(30, 0, 0))
    common(out, idle, wa, wb, ra, rb, hurt, [(0, idle), (14, crouch), (20, airp), (90, airp)])
    out.append(clip('arrive', 200, [(0, airp), (200, airp)]))
    smashland = P(crouch, root=low(-8), right_arm=(-60, 0, 10), left_arm=(-60, 0, -10), right_forearm=(0, 0, 0), left_forearm=(0, 0, 0))
    out.append(clip('land', 20, [(0, smashland), (8, smashland), (20, idle)]))
    # circle: chest beat (alternating fists ticks 10-40), the roar (44-70)
    beat_r = P(idle, torso=(0, 0, 0), right_arm=(-70, 40, 0), right_forearm=(-90, 0, 0))
    beat_l = P(idle, torso=(0, 0, 0), left_arm=(-70, -40, 0), left_forearm=(-90, 0, 0))
    roar = P(idle, waist=(-6, 0, 0), torso=(-14, 0, 0), head=(-30, 0, 0), right_arm=(-40, 0, 60), left_arm=(-40, 0, -60),
             right_forearm=(-60, 0, 0), left_forearm=(-60, 0, 0))
    keys = [(0, idle), (8, P(idle, torso=(0, 0, 0)))]
    for i in range(6):
        keys.append((10 + i * 5, beat_r if i % 2 == 0 else beat_l))
        keys.append((12 + i * 5, P(idle, torso=(0, 0, 0))))
    keys += [(44, roar), (70, P(roar, head=(-36, 0, 0))), (80, idle)]
    out.append(clip('circle', 80, keys))
    hook_r = P(idle, waist=(16, -40, 0), torso=(16, -30, 0), right_arm=(-90, -40, 20), right_forearm=(-30, 0, 0))
    out.append(clip('attack_1', 13, [(0, idle), (3, P(idle, waist=(14, 30, 0), right_arm=(-40, 40, 40))), (6, hook_r), (9, hook_r), (13, idle)]))
    out.append(clip('attack_2', 13, [(0, idle)] + [(t, mirror(p)) for t, p in
                                                   [(3, P(idle, waist=(14, 30, 0), right_arm=(-40, 40, 40))), (6, hook_r), (9, hook_r)]] + [(13, idle)]))
    up2 = P(idle, waist=(-4, 0, 0), torso=(-10, 0, 0), right_arm=(-176, 0, 10), left_arm=(-176, 0, -10), right_forearm=(-20, 0, 0), left_forearm=(-20, 0, 0))
    dn2 = P(crouch, right_arm=(-70, 0, 0), left_arm=(-70, 0, 0), right_forearm=(0, 0, 0), left_forearm=(0, 0, 0))
    out.append(clip('attack_3', 18, [(0, idle), (6, up2), (10, dn2), (14, dn2), (18, idle)]))
    out.append(clip('special_1', 26, [(0, idle), (6, up2), (10, dn2), (14, up2), (18, dn2), (26, idle)]))  # ground pound
    out.append(clip('special_2', 30, [(0, idle), (6, roar), (24, P(roar, head=(-36, 0, 0))), (30, idle)]))  # roar
    out.append(clip('special_3', 30, [(0, idle), (30, idle)]))
    out.append(clip('leap', 40, [(0, idle), (8, crouch), (14, airp), (40, airp)]))
    out.append(clip('dodge', 12, [(0, idle), (12, idle)]))
    out.append(clip('shoot', 10, [(0, idle), (10, idle)]))
    return out


# =================================================================================== BLACK WIDOW
def widow():
    out = []
    idle = P(root=low(-1), waist=(4, -20, 0), torso=(6, -10, 0), head=(-8, 26, 0),
             right_arm=(-30, 0, 16), right_forearm=(-50, 0, 0), right_hand=(0, 0, 0), right_gun=(0, 0, 0),
             left_arm=(-40, 10, -10), left_forearm=(-70, 0, 0), left_hand=(0, 0, 0), left_gun=(0, 0, 0), hair=(0, 0, 0),
             right_leg=(16, -14, 6), right_shin=(24, 0, 0), left_leg=(-20, 16, -4), left_shin=(18, 0, 0))
    wa, wb = legs(idle, 24, 20)
    ra, rb = run_pair(idle)
    hurt = P(idle, torso=(-16, 0, 0), head=(-10, 0, 0))
    common(out, idle, wa, wb, ra, rb, hurt, [(0, idle), (90, idle)])
    # falling out of the Quinjet: tucked somersault, then spread for the landing
    tuck = P(idle, waist=(40, 0, 0), torso=(30, 0, 0), right_leg=(-110, 0, 0), right_shin=(120, 0, 0), left_leg=(-110, 0, 0), left_shin=(120, 0, 0),
             right_arm=(-60, 0, 20), left_arm=(-60, 0, -20))
    fall = [(0, P(idle, root={'rot': (0, 0, 0), 'pos': (0, 0, 0)}))]
    for i in range(1, 5):
        fall.append((i * 4, P(tuck, root={'rot': (90 * i, 0, 0), 'pos': (0, 0, 0)})))
    fall += [(22, P(idle, root={'rot': (360, 0, 0), 'pos': (0, 0, 0)}, right_arm=(-90, 0, 60), left_arm=(-90, 0, -60))),
             (200, P(idle, root={'rot': (360, 0, 0), 'pos': (0, 0, 0)}, right_arm=(-90, 0, 60), left_arm=(-90, 0, -60)))]
    out.append(clip('arrive', 200, fall))
    land = P(idle, **LAND3, right_arm=(-30, 0, 20), right_forearm=(-10, 0, 0), left_arm=(30, 0, -50), left_forearm=(0, 0, 0))
    out.append(clip('land', 20, [(0, land), (8, land), (20, idle)]))
    # circle: draws both pistols (tick 12), crossed aim outward at 30
    aim = P(idle, waist=(0, 0, 0), torso=(0, 0, 0), head=(-4, 0, 0), right_arm=(-90, 50, 0), right_forearm=(0, 0, 0),
            left_arm=(-90, -50, 0), left_forearm=(0, 0, 0))
    out.append(clip('circle', 80, [(0, idle), (10, P(idle, right_arm=(20, 0, 20), left_arm=(20, 0, -20))), (16, P(aim, right_arm=(-60, 30, 10), left_arm=(-60, -30, -10))),
                                   (24, aim), (70, aim), (80, idle)]))
    k1 = P(idle, root=low(1), torso=(-20, 0, 0), right_leg=(-100, 0, 0), right_shin=(0, 0, 0), left_shin=(10, 0, 0))
    out.append(clip('attack_1', 9, [(0, idle), (3, P(idle, right_leg=(-60, 0, 0), right_shin=(90, 0, 0))), (4, k1), (6, k1), (9, idle)]))
    el = P(idle, waist=(6, 30, 0), torso=(8, 24, 0), left_arm=(-90, -40, 0), left_forearm=(-110, 0, 0))
    out.append(clip('attack_2', 9, [(0, idle), (3, P(idle, waist=(4, -30, 0))), (4, el), (6, el), (9, idle)]))
    sp = P(idle, root={'rot': (0, 300, 0), 'pos': (0, 2, 0)}, torso=(-20, 0, -20), right_leg=(-30, 0, 90), right_shin=(0, 0, 0))
    out.append(clip('attack_3', 14, [(0, idle), (3, P(idle, root={'rot': (0, 120, 0), 'pos': (0, 1, 0)})), (6, sp), (9, P(sp, root={'rot': (0, 350, 0), 'pos': (0, 0, 0)})),
                                     (14, P(idle, root={'rot': (0, 360, 0), 'pos': (0, -1, 0)}))]))
    bite = P(idle, waist=(6, 20, 0), torso=(10, 16, 0), right_arm=(-90, -10, 0), right_forearm=(0, 0, 0), right_hand=(-30, 0, 0), right_fingers=(0, 0, 0))
    out.append(clip('special_1', 14, [(0, idle), (3, bite), (9, bite), (14, idle)]))
    out.append(clip('special_2', 30, [(0, idle), (30, idle)]))
    out.append(clip('special_3', 40, [(0, idle), (40, idle)]))
    # pistols: alternating shots (18 t)
    gun = P(aim, right_arm=(-90, 10, 0), left_arm=(-90, -10, 0), head=(-6, 0, 0), waist=(0, 0, 0))
    out.append(clip('shoot', 18, [(0, idle), (3, gun), (5, P(gun, right_arm=(-104, 10, 0))), (8, gun), (10, P(gun, left_arm=(-104, -10, 0))),
                                  (13, gun), (15, P(gun, right_arm=(-104, 10, 0))), (18, gun)]))
    # acrobatic leap-kick: flip, both feet forward
    flip = [(0, idle), (4, P(idle, root=low(-3)))]
    for i in range(1, 4):
        flip.append((4 + i * 3, P(tuck, root={'rot': (-120 * i, 0, 0), 'pos': (0, 2, 0)})))
    flip += [(16, P(idle, root={'rot': (-360, 0, 0), 'pos': (0, 0, 0)}, right_leg=(-90, 0, 0), left_leg=(-80, 0, 0), right_shin=(0, 0, 0), left_shin=(0, 0, 0), torso=(-30, 0, 0))),
             (40, P(idle, root={'rot': (-360, 0, 0), 'pos': (0, 0, 0)}, right_leg=(-90, 0, 0), left_leg=(-80, 0, 0), right_shin=(0, 0, 0), left_shin=(0, 0, 0), torso=(-30, 0, 0)))]
    out.append(clip('leap', 40, flip))
    # back-flip dodge (14 t)
    bf = [(0, idle), (2, P(idle, root=low(-3)))]
    for i in range(1, 4):
        bf.append((2 + i * 3, P(tuck, root={'rot': (-120 * i, 0, 0), 'pos': (0, 4, 2)})))
    bf += [(14, P(idle, root={'rot': (-360, 0, 0), 'pos': (0, -1, 0)}))]
    out.append(clip('dodge', 14, bf))
    return out


# =================================================================================== HAWKEYE
def hawkeye():
    out = []
    BOW_DOWN = (90, 0, 0)
    idle = P(root=low(0), waist=(0, 6, 0), torso=(-2, 0, 0), head=(2, -6, 0),
             right_arm=(-4, 0, 6), right_forearm=(-14, 0, 0), right_hand=(0, 0, 0), arrow=(0, 0, 0),
             left_arm=(-10, 0, -8), left_forearm=(-20, 0, 0), left_hand=(0, 0, 0), bow=BOW_DOWN, bow_top=(0, 0, 0), bow_bottom=(0, 0, 0),
             right_leg=(0, 4, 2), right_shin=(0, 0, 0), left_leg=(-4, -6, -3), left_shin=(4, 0, 0), **RELAX)
    wa, wb = legs(idle, 22, 20)
    ra, rb = run_pair(idle)
    ra, rb = P(ra, left_arm=(-20, 0, -10), left_forearm=(-40, 0, 0)), P(rb, left_arm=(-20, 0, -10), left_forearm=(-40, 0, 0))
    hurt = P(idle, torso=(-14, 0, 0), head=(-10, 0, 0))
    common(out, idle, wa, wb, ra, rb, hurt, [(0, idle), (90, idle)])
    fall = P(idle, right_arm=(-150, 0, 30), left_arm=(-60, 0, -40), right_leg=(-30, 0, 0), right_shin=(60, 0, 0), left_leg=(10, 0, 0), left_shin=(40, 0, 0))
    out.append(clip('arrive', 200, [(0, fall), (200, fall)]))
    land = P(idle, **LAND3, right_arm=(-30, 0, 20), right_forearm=(-10, 0, 0), left_arm=(20, 0, -40))
    out.append(clip('land', 20, [(0, land), (8, land), (20, idle)]))
    # draw: bow arm forward (bow vertical by the arm), string hand pulled to the chin; release at RELEASE 12
    raise_ = P(idle, waist=(0, -60, 0), torso=(0, -20, 0), head=(0, 74, 0), left_arm=(-90, 70, 0), left_forearm=(0, 0, 0), bow=(0, 0, 0),
               right_arm=(-90, 50, 0), right_forearm=(-20, 0, 0), arrow=(0, 0, 0), right_fingers=(0, 0, -60))
    drawn = P(raise_, right_arm=(-90, 80, -20), right_forearm=(-120, 0, 0), bow_top=(-8, 0, 0), bow_bottom=(8, 0, 0))
    loose = P(raise_, right_arm=(-80, 100, -30), right_forearm=(-80, 0, 0), right_fingers=(0, 0, 0), arrow={'rot': (0, 0, 0), 'scale': (0, 0, 0)})
    shot = [(0, idle), (4, raise_), (10, drawn), (12, loose), (15, loose), (18, P(raise_, arrow=(0, 0, 0)))]
    for name in ('shoot', 'special_1', 'special_2', 'special_3'):
        out.append(clip(name, 18, shot))
    # circle: nocks and draws pointing up and out over the circle, holds
    up = P(drawn, left_arm=(-130, 70, 0), torso=(-10, -20, 0), head=(-30, 74, 0))
    out.append(clip('circle', 80, [(0, idle), (10, raise_), (24, drawn), (34, up), (72, up), (80, idle)]))
    st = P(idle, waist=(6, -30, 0), torso=(8, -20, 0), left_arm=(-90, -40, -20), left_forearm=(-10, 0, 0), bow=(0, 0, 90))
    out.append(clip('attack_1', 10, [(0, idle), (3, P(idle, waist=(4, 30, 0), left_arm=(-80, 80, 0), bow=(0, 0, 90))), (5, st), (7, st), (10, idle)]))
    out.append(clip('attack_2', 10, [(0, idle), (10, idle)]))
    out.append(clip('attack_3', 16, [(0, idle), (16, idle)]))
    out.append(clip('dodge', 12, [(0, idle), (3, P(idle, root=low(-2), torso=(-10, 0, 0), right_leg=(30, 0, 0), left_leg=(20, 0, 0))), (12, idle)]))
    # grapple: fires the line up (right arm), then hangs on it pulled up
    hang = P(idle, right_arm=(-170, 0, 10), right_forearm=(0, 0, 0), right_fingers=(0, 0, -95), head=(-30, 0, 0),
             right_leg=(-20, 0, 0), right_shin=(40, 0, 0), left_leg=(10, 0, 0), left_shin=(20, 0, 0))
    out.append(clip('leap', 40, [(0, idle), (4, P(hang, right_fingers=(0, 0, 0))), (10, hang), (34, hang), (40, idle)]))
    return out


def quinjet():
    c = []
    c.append(clip('flight', 40, [(0, {'body': (0, 0, 0), 'engine_right': (0, 0, 0), 'engine_left': (0, 0, 0)}),
                                  (20, {'body': (-2, 0, 1), 'engine_right': (0, 0, 0), 'engine_left': (0, 0, 0)}),
                                  (40, {'body': (0, 0, 0), 'engine_right': (0, 0, 0), 'engine_left': (0, 0, 0)})], loop=True))
    c.append(clip('ramp_open', 20, [(0, {'ramp': (0, 0, 0)}), (20, {'ramp': (38, 0, 0)})]))
    c.append(clip('hover', 20, [(0, {'engine_right': (-80, 0, 0), 'engine_left': (-80, 0, 0)}),
                                (20, {'engine_right': (-80, 0, 0), 'engine_left': (-80, 0, 0)})], loop=True))
    return c


def main():
    for name, clips in (('iron_man', iron_man()), ('thor', thor()), ('hulk', hulk()), ('black_widow', widow()), ('hawkeye', hawkeye()),
                        ('quinjet', quinjet())):
        size = write_anims(f'animations/avengers/{name}.animation.json', clips)
        print(name, len(clips), 'clips', size, 'bytes')


if __name__ == '__main__':
    main()
