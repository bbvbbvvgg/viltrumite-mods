"""Clips of the five summoned Avengers (own work, CC0) — one file per kind, clip names = AvengerAction.clip()
plus the locomotion set (idle, walk, run, fly). Lengths follow the brains (start(action, ticks)) and the
assemble scene (LAND 20, CIRCLE 80, DEPART 90); variable-length actions (leap) hold their last key.
Outputs animations/avengers/<kind>.animation.json and quinjet.animation.json."""
from jinwoo_clips import clip, breathe
from jinwoo_geo import write_anims
from jinwoo_poses import P, low, mirror, FIST, RELAX, OPEN, OPEN_L, OPEN_R  # noqa: F401


def spin(base, bone, t0, t1, axis, turns, rest=(0, 0, 0)):
    """Keys spinning one bone `turns` full revolutions between t0 and t1 (180 degrees per key: GeckoLib lerps the
    Euler angles, so evenly spaced keys give an even spin with half the keys of a 90-degree step)."""
    keys = []
    n = int(turns * 2)
    for i in range(n + 1):
        t = t0 + (t1 - t0) * i / n
        a = -180 * i
        r = list(rest)
        r[axis] += a
        keys.append((t, P(base, **{bone: tuple(r)})))
    # whole turns are the rest angle again: jump there right away so the next key never unwinds the spin
    keys.append((t1 + 0.1, P(base, **{bone: tuple(rest)})))
    return keys


def ss(x):
    x = min(max(x, 0.0), 1.0)
    return x * x * (3 - 2 * x)


def wheel(base, t0, t1, deg, h, cz=0.0, drop=0.0, step=2, lift=0.0):
    """Keys turning the whole body `deg` degrees of pitch (+ = forward) about its centre (h px above the feet,
    cz px along z; the root pivot is at the feet, so the position compensates and the body tumbles about its
    centre instead of sinking its head into the floor). `drop` lowers the whole ball (a tuck lifts the feet),
    `lift` arcs it up mid-turn. Dense keys keep the centre still between keys."""
    import math
    keys = []
    n = max(1, int(round((t1 - t0) / step)))
    for i in range(n + 1):
        f = i / n
        a = deg * ss(f)
        r = math.radians(-a)
        ry = h * math.cos(r) - cz * math.sin(r)
        rz = h * math.sin(r) + cz * math.cos(r)
        up = lift * math.sin(math.pi * f) + drop * (1 if 0 < i < n else 0.5)
        keys.append((t0 + (t1 - t0) * f, P(base, root={'rot': (round(a, 1), 0, 0), 'pos': (0, round(h - ry + up, 2), round(cz - rz, 2))})))
    return keys


def unwind(c, eps=0.005):
    """A clip that ends a full turn away (360 pitch, 360 spin, 3 hammer turns) jumps to the equivalent
    angle right after the turn ends, so the release / hand-off fades never unwind the turn backwards."""
    def wrap(v):
        return tuple(((x + 180.0) % 360.0) - 180.0 if abs(x) > 180.0 else x for x in v)
    bones = []
    for _, pose in c.frames:
        for b in pose:
            if b not in bones:
                bones.append(b)
    extra = []
    for b in bones:
        idx = [i for i, (_, pose) in enumerate(c.frames) if b in pose and 'rot' in pose[b]]
        if not idx:
            continue
        last = c.frames[idx[-1]][1][b]['rot']
        w = wrap(last)
        if tuple(w) == tuple(last):
            continue
        hold = len(idx) - 1
        while hold > 0 and tuple(c.frames[idx[hold - 1]][1][b]['rot']) == tuple(last):
            hold -= 1
        if hold < len(idx) - 1:
            t_hold = c.frames[idx[hold]][0]
            for i in idx[hold + 1:]:
                t, pose = c.frames[i]
                pose[b] = dict(pose[b], rot=w)
            extra.append((t_hold + eps, {b: {'rot': w}}))
        else:
            t_end, pose = c.frames[idx[-1]]
            pose[b] = dict(pose[b], rot=w)
            extra.append((max(0.0, t_end - eps), {b: {'rot': last}}))
    c.frames.extend(extra)
    c.frames.sort(key=lambda f: f[0])
    return c


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
    brake = P(hover, root={'rot': (-14, 0, 0), 'pos': (0, 0, 0)}, head=(10, 0, 0), right_arm=(-50, 0, 30), right_forearm=(-10, 0, 0),
              right_hand=(-80, 0, 0), left_arm=(-50, 0, -30), left_forearm=(-10, 0, 0), left_hand=(-80, 0, 0), back_flaps=(-50, 0, 0),
              right_leg=(-20, 0, 2), right_shin=(30, 0, 0), left_leg=(6, 0, -2), left_shin=(20, 0, 0), **OPEN)
    sup2 = P(sup, right_arm=(26, 0, 14), left_arm=(26, 0, -14), head=(-64, 0, 0))
    out.append(clip('arrive', 200, [(0, sup), (14, sup2), (28, sup), (40, sup2), (50, P(hover, root={'rot': (24, 0, 0), 'pos': (0, 0, 0)})),
                                    (60, brake), (80, P(brake, root={'rot': (-6, 0, 0), 'pos': (0, 0, 0)})), (110, hover), (200, hover)]))
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
    # ground blast: drops onto one knee, both palms to the ground (24 t, burst at 8)
    gb = P(hover, **LAND3, right_arm=(-50, 0, 10), right_forearm=(0, 0, 0), right_hand=(-80, 0, 0), left_arm=(-50, 0, -10),
           left_forearm=(0, 0, 0), left_hand=(-80, 0, 0), back_flaps=(-40, 0, 0), **OPEN)
    out.append(clip('special_3', 24, [(0, hover), (5, P(gb, root=low(-4))), (8, gb), (16, gb), (24, idle)]))
    # barrel roll dodge on the thrusters (12 t)
    out.append(clip('dodge', 12, [(0, hover), (3, P(hover, root={'rot': (0, 0, 60), 'pos': (0, 1, 0)}, right_arm=(10, 0, 60), left_arm=(10, 0, -60))),
                                  (6, P(hover, root={'rot': (0, 0, 180), 'pos': (0, 2, 0)}, right_arm=(10, 0, 60), left_arm=(10, 0, -60))),
                                  (9, P(hover, root={'rot': (0, 0, 300), 'pos': (0, 1, 0)})), (12, P(hover, root={'rot': (0, 0, 360), 'pos': (0, 0, 0)}))]))
    # boost take-off: crouch, thrusters lit, launch with the arms down (40 t)
    out.append(clip('leap', 40, [(0, idle), (5, P(idle, root=low(-3), waist=(14, 0, 0), right_leg=(-30, 0, 0), right_shin=(50, 0, 0),
                                                    left_leg=(-30, 0, 0), left_shin=(50, 0, 0), right_hand=(-80, 0, 0), left_hand=(-80, 0, 0))),
                                 (10, P(hover, head=(-24, 0, 0), right_arm=(16, 0, 10), left_arm=(16, 0, -10), right_hand=(70, 0, 0), left_hand=(70, 0, 0),
                                        back_flaps=(-40, 0, 0))), (40, hover)]))
    out.append(clip('shoot', 10, [(0, hover), (2, rp), (7, rp), (10, hover)]))
    # joint move (rework §15), Thor's lightning into the arc reactor: arms flung wide and back, chest out to the
    # arc, the suit shuddering under the charge (36 t) ...
    take = P(hover, waist=(-12, 0, 0), torso=(-22, 0, 0), head=(14, 0, 0), right_arm=(30, 0, 70), right_forearm=(-10, 0, 0), right_hand=(40, 0, 0),
             left_arm=(30, 0, -70), left_forearm=(-10, 0, 0), left_hand=(40, 0, 0), back_flaps=(-40, 0, 0),
             right_leg=(10, 0, 6), right_shin=(20, 0, 0), left_leg=(-4, 0, -6), left_shin=(14, 0, 0), **OPEN)
    jolt = P(take, torso=(-26, 0, 3), head=(18, 0, -4), right_arm=(34, 0, 76), left_arm=(34, 0, -64))
    keys = [(0, hover), (6, take)]
    for i, tt in enumerate(range(10, 34, 3)):
        keys.append((tt, jolt if i % 2 == 0 else take))
    keys += [(36, take), (40, take)]
    out.append(clip('team_1', 40, keys))
    # ... then the charged unibeam: braced in the air, fists back, everything into the chest (30 t)
    cb = P(ub, waist=(-14, 0, 0), torso=(-26, 0, 0), head=(14, 0, 0), right_arm=(50, 0, 36), left_arm=(50, 0, -36), right_forearm=(-30, 0, 0),
           left_forearm=(-30, 0, 0), right_leg=(-10, 0, 4), right_shin=(30, 0, 0), left_leg=(14, 0, -4), left_shin=(20, 0, 0), back_flaps=(-50, 0, 0))
    out.append(clip('team_2', 30, [(0, take), (4, cb), (10, P(cb, torso=(-29, 0, 0))), (16, cb), (22, P(cb, torso=(-28, 0, 0))), (27, cb), (30, hover)]))
    out.append(clip('team_3', 24, [(0, hover), (24, hover)]))  # no third joint move for Tony
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
    dep += [(34, P(spin_up, hair=(30, 0, 0))), (44, P(spin_up, hair=(46, 0, 4))), (54, P(spin_up, hair=(32, 0, 0))), (90, P(spin_up, hair=(40, 0, 0)))]
    common(out, combat, wa, wb, ra, rb, hurt, dep)
    # flight: hair and cape stream behind him and flutter in the wind (rework §7, hair animated in flight)
    fly_a = P(spin_up, hair=(34, 0, 4), cape=(42, 0, 0), cape_lower=(22, 0, 0))
    fly_b = P(spin_up, hair=(48, 0, -4), cape=(56, 0, 0), cape_lower=(34, 0, 0))
    out.append(clip('fly', 20, [(0, fly_a), (5, fly_b), (10, fly_a), (15, fly_b), (20, fly_a)], loop=True))
    # inside the bolt: Mjolnir up, cape whipping, lands hard at the end of the scene's bolt window
    bolt = P(spin_up, cape=(50, 0, 0), cape_lower=(30, 0, 0), right_leg=(-30, 0, 0), right_shin=(50, 0, 0), left_leg=(6, 0, 0), left_shin=(30, 0, 0))
    bolt = P(bolt, hair=(46, 0, 0))
    gust = P(bolt, cape=(60, 0, 0), cape_lower=(40, 0, 0), hair=(58, 0, 5))
    out.append(clip('arrive', 200, [(0, P(spin_up, root=low(0), hair=(20, 0, 0)))] + [(t, gust if (t // 8) % 2 else bolt) for t in range(8, 57, 8)]
                    + [(200, bolt)]))
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
    # thunder clap: Mjolnir driven into the ground with both hands (40 t, shock at 12)
    clap_up = P(combat, root=low(1), waist=(-10, 0, 0), torso=(-16, 0, 0), head=(-24, 0, 0), right_arm=(-170, 0, 6), right_forearm=(-20, 0, 0),
                left_arm=(-170, 0, -6), left_forearm=(-20, 0, 0), hammer=(150, 0, 0), cape=(30, 0, 0))
    clap_dn = P(combat, root=low(-7), waist=(36, 0, 0), torso=(30, 0, 0), head=(-44, 0, 0), right_arm=(-60, 0, 6), right_forearm=(0, 0, 0),
                left_arm=(-60, 0, -6), left_forearm=(0, 0, 0), hammer=(90, 0, 0), cape=(-30, 0, 0), cape_lower=(-20, 0, 0),
                right_leg=(-70, 10, 0), right_shin=(80, 0, 0), left_leg=(20, -10, 0), left_shin=(90, 0, 0))
    out.append(clip('special_3', 40, [(0, combat), (8, clap_up), (12, clap_dn), (26, clap_dn), (40, combat)]))
    # hammer flight onto a far target (60 t): spin, then pulled forward by the arm
    pull = P(idle, root={'rot': (60, 0, 0), 'pos': (0, 0, 0)}, head=(-50, 0, 0), right_arm=(-170, 0, 0), right_forearm=(0, 0, 0),
             hammer=(180, 0, 0), cape=(60, 0, 0), cape_lower=(30, 0, 0), right_leg=(10, 0, 0), left_leg=(-10, 0, 0), hair=(62, 0, 0))
    pull2 = P(pull, hair=(74, 0, 6), cape=(70, 0, 0), cape_lower=(42, 0, 0))
    out.append(clip('leap', 60, spin(P(combat, right_arm=(-100, 0, 40), right_forearm=(0, 0, 0)), 'hammer', 0, 10, 1, 3) +
                    [(14, pull), (24, pull2), (34, pull), (44, pull2), (50, pull), (56, P(land, hammer=(90, 0, 0))), (60, P(land, hammer=(90, 0, 0)))]))
    # side-step with a cape swirl (12 t)
    out.append(clip('dodge', 12, [(0, combat), (3, P(combat, root=low(-2), waist=(6, -40, 0), cape=(50, 0, 0), right_leg=(-30, 0, 20), left_leg=(10, 0, -10))),
                                  (8, P(combat, waist=(6, -10, 0), cape=(20, 0, 0))), (12, combat)]))
    # lightning from the raised hammer at range (10 t)
    out.append(clip('shoot', 10, [(0, combat), (3, P(combat, right_arm=(-150, 0, 20), right_forearm=(0, 0, 0), hammer=(160, 0, 0), head=(-20, 0, 0))),
                                  (7, P(combat, right_arm=(-100, -10, 10), right_forearm=(0, 0, 0), hammer=(100, 0, 0))), (10, combat)]))
    # joint move (rework §15), the charge: Mjolnir to the sky for the bolt (10), then levelled at Tony overhead,
    # the arc streaming into his chest (14-36), the arm shaking with it
    point = P(sky, torso=(-14, 0, 0), head=(-28, 0, 0), right_arm=(-132, 0, 26), right_forearm=(-6, 0, 0), hammer=(140, 0, 0),
              left_arm=(-30, 10, -30), cape=(24, 0, 0), hair=(10, 0, 0),
              right_leg=(-10, 0, 8), right_shin=(12, 0, 0), left_leg=(14, 0, -8), left_shin=(10, 0, 0))
    shake = P(point, torso=(-16, 0, 2), right_arm=(-136, 0, 29), hair=(14, 0, 0))
    keys = [(0, combat), (6, P(sky, hair=(10, 0, 0))), (10, P(sky, torso=(-12, 0, 0), hair=(20, 0, 0))), (12, P(sky, hair=(10, 0, 0))), (15, point)]
    for i, tt in enumerate(range(18, 36, 4)):
        keys.append((tt, shake if i % 2 == 0 else point))
    keys += [(36, point), (40, combat)]
    out.append(clip('team_1', 40, keys))
    # the shield strike, call: Mjolnir raised and held, feet planted, lightning gathering until Steve raises the
    # shield (held up to SHIELD_WINDOW 80 t, so the clip is long)
    hold = P(sky, root=low(-1), torso=(-8, 0, 0), head=(-14, 0, 0), right_leg=(16, 0, 10), right_shin=(14, 0, 0), left_leg=(-16, 0, -10),
             left_shin=(14, 0, 0), left_arm=(-20, 0, -40), left_forearm=(-20, 0, 0), cape=(26, 0, 0), hair=(6, 0, 0))
    keys = [(0, combat), (8, hold)]
    for i in range(1, 8):
        keys.append((8 + i * 12, P(hold, right_arm=(-178, 0, 2), torso=(-9, 0, 0)) if i % 2 else hold))
    keys += [(100, hold)]
    out.append(clip('team_2', 100, keys))
    # the blow: from the raised hammer down onto the shield in front of him (impact at SHIELD_IMPACT 7), the hop
    # into it, the recoil and back to the stance (24 t)
    up = P(hold, waist=(-10, 0, 0), torso=(-18, 0, 0), right_arm=(-176, 0, 6), right_forearm=(-30, 0, 0), left_arm=(-150, 0, -6),
           left_forearm=(-40, 0, 0), hammer=(150, 0, 0), cape=(30, 0, 0))
    blow = P(combat, root=low(-2.4), waist=(22, 0, 0), torso=(22, 0, 0), head=(-28, 0, 0), right_arm=(-86, 0, 4), right_forearm=(0, 0, 0),
             left_arm=(-70, 0, -6), left_forearm=(-20, 0, 0), hammer=(90, 0, 0), cape=(-24, 0, 0), cape_lower=(-14, 0, 0), hair=(-16, 0, 0),
             right_leg=(-44, 0, 4), right_shin=(44, 0, 0), left_leg=(22, 0, -4), left_shin=(24, 0, 0))
    recoil = P(blow, root=low(-2.0), torso=(16, 0, 0), right_arm=(-100, 0, 6), hammer=(100, 0, 0), hair=(6, 0, 0))
    out.append(clip('team_3', 24, [(0, hold), (4, up), (7, blow), (10, recoil), (15, blow), (24, combat)]))
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
    # mid-leap: arms windmilling at the top, then both fists clasped overhead for the smash landing
    flail = P(airp, right_arm=(-170, 0, 50), left_arm=(-120, 0, -50), right_leg=(-50, 0, 0), right_shin=(70, 0, 0), left_leg=(0, 0, 0), left_shin=(40, 0, 0))
    clasp = P(airp, waist=(-8, 0, 0), torso=(-14, 0, 0), head=(-30, 0, 0), right_arm=(-172, 0, 12), left_arm=(-172, 0, -12),
              right_forearm=(-40, 0, 0), left_forearm=(-40, 0, 0), right_leg=(-60, 0, 0), right_shin=(80, 0, 0), left_leg=(-40, 0, 0), left_shin=(70, 0, 0))
    out.append(clip('arrive', 200, [(0, P(crouch, root=low(-4))), (6, airp), (16, flail), (26, P(flail, right_arm=(-120, 0, 50), left_arm=(-170, 0, -50))),
                                    (36, clasp), (200, clasp)]))
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
    # taunt: chest pounding, then arms flung wide in a roar (40 t) — pulls the mobs onto Hulk
    taunt = [(0, idle), (4, P(idle, torso=(0, 0, 0)))]
    for i in range(4):
        taunt += [(6 + i * 5, beat_r if i % 2 == 0 else beat_l), (8 + i * 5, P(idle, torso=(0, 0, 0)))]
    wide = P(roar, right_arm=(-30, 0, 80), left_arm=(-30, 0, -80), right_forearm=(-30, 0, 0), left_forearm=(-30, 0, 0))
    taunt += [(28, wide), (36, P(wide, head=(-40, 0, 0))), (40, idle)]
    out.append(clip('special_3', 40, taunt))
    out.append(clip('leap', 40, [(0, idle), (8, crouch), (14, airp), (24, flail), (32, clasp), (40, clasp)]))
    # heavy side hop (12 t)
    hop = P(crouch, root=low(-2), waist=(26, 0, 0), right_arm=(10, 0, 50), left_arm=(10, 0, -50))
    out.append(clip('dodge', 12, [(0, idle), (3, P(crouch, root=low(-5))), (6, hop), (10, P(crouch, root=low(-4))), (12, idle)]))
    # hurls a chunk of the ground: both hands under it, heave overhead, throw (20 t, release at 11)
    heave = P(up2, right_forearm=(-50, 0, 0), left_forearm=(-50, 0, 0), right_arm=(-160, 0, 20), left_arm=(-160, 0, -20))
    throw = P(idle, waist=(30, 0, 0), torso=(26, 0, 0), head=(-30, 0, 0), right_arm=(-70, 0, 10), left_arm=(-70, 0, -10),
              right_forearm=(0, 0, 0), left_forearm=(0, 0, 0), **OPEN)
    out.append(clip('shoot', 20, [(0, idle), (4, P(crouch, right_arm=(-40, 0, 0), left_arm=(-40, 0, 0))), (8, heave), (11, throw), (14, throw), (20, idle)]))
    for name, ticks in (('team_1', 40), ('team_2', 30), ('team_3', 24)):  # Hulk has no joint move (yet)
        out.append(clip(name, ticks, [(0, idle), (ticks, idle)]))
    return [hulk_face_feet(c) for c in out]


def hulk_foot(pose, side, extra=0.0):
    """Foot pitch that keeps the sole flat under the leg chain (+ toes down)."""
    leg = pose.get(f'{side}_leg', (0, 0, 0))
    shin = pose.get(f'{side}_shin', (0, 0, 0))
    leg = leg['rot'] if isinstance(leg, dict) else leg
    shin = shin['rot'] if isinstance(shin, dict) else shin
    return (round(-(leg[0] + shin[0]) + extra, 1), 0, 0)


def foot_roll(name, pose, side):
    """Walk / run foot roll on top of the flat sole: the trailing foot pushes off on its toes (up to +25), the
    leading foot strikes with the heel, toes up (down to -8). Leg +X = swung back, -X = forward."""
    if name not in ('walk', 'run'):
        return 0.0
    leg = pose.get(f'{side}_leg', (0, 0, 0))
    leg = (leg['rot'] if isinstance(leg, dict) else leg)[0]
    if leg > 0:
        return min(1.0, leg / 20.0) * 25.0
    return max(-1.0, leg / 20.0) * 8.0


def hulk_face_feet(c):
    """Hulk's jaw, chest and feet (spec §8): the jaw opens in the roars, the chest heaves on the beats and the
    roars, the feet stay flat on the ground (toes down when airborne). Added to every key of every clip from the
    pose it already has, so the clips above stay readable."""
    roar = c.name in ('special_2',) or c.name == 'circle'
    for i, (t, pose) in enumerate(c.frames):
        torso = pose.get('torso', (0, 0, 0))
        torso = torso['rot'] if isinstance(torso, dict) else torso
        head = pose.get('head', (0, 0, 0))
        head = head['rot'] if isinstance(head, dict) else head
        # a roar throws the head back: the jaw drops with it, a growl otherwise
        jaw = 4.0
        if head[0] <= -29 and (roar or c.name in ('special_3', 'depart', 'arrive')):
            jaw = 30.0
        elif c.name == 'hurt' and 0 < t < 0.4:
            jaw = 16.0
        elif c.name in ('attack_3', 'special_1', 'shoot') and torso[0] >= 25:
            jaw = 20.0
        pose.setdefault('jaw', (jaw, 0, 0))
        # beats / roars: the chest swells
        swell = torso[0] <= 0 and c.name in ('circle', 'special_3', 'special_2')
        # (the clip writer keeps one decimal: the chest pushes 10 % forward, width and height stay)
        pose.setdefault('chest', {'rot': (-4 if swell else 0, 0, 0), 'scale': (1, 1, 1.1) if swell else (1, 1, 1)})
        root = pose.get('root', {})
        rleg = pose.get('right_leg', (0, 0, 0))
        rleg = rleg['rot'] if isinstance(rleg, dict) else rleg
        air = c.name in ('arrive', 'leap', 'depart') and isinstance(root, dict) and root.get('pos', (0, 0, 0))[1] >= 0 and rleg[0] <= -20
        for side in ('right', 'left'):
            pose.setdefault(f'{side}_foot', (30, 0, 0) if air else hulk_foot(pose, side, foot_roll(c.name, pose, side)))
    return c


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
    spread = P(idle, root={'rot': (360, 0, 0), 'pos': (0, 0, 0)}, waist=(0, 0, 0), torso=(-6, 0, 0), head=(-14, 0, 0),
               right_arm=(-90, 0, 60), left_arm=(-90, 0, -60), right_forearm=(-10, 0, 0), left_forearm=(-10, 0, 0),
               right_leg=(-30, 0, 4), right_shin=(50, 0, 0), left_leg=(10, 0, -4), left_shin=(30, 0, 0), hair=(-40, 0, 0))
    fall = [(0, P(idle, root={'rot': (0, 0, 0), 'pos': (0, 0, 0)}, hair=(0, 0, 0)))] + wheel(P(tuck, hair=(-30, 0, 0)), 2, 18, 360, 15.3, cz=-10.7, step=1.5)
    fall += [(24, spread), (40, P(spread, right_arm=(-100, 0, 50), left_arm=(-80, 0, -70), hair=(-50, 0, 0))), (60, spread), (200, spread)]
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
    # double bite: both wrists forward, a crackle (30 t)
    bite2 = P(idle, waist=(4, 0, 0), torso=(8, 0, 0), right_arm=(-90, -14, 0), right_forearm=(0, 0, 0), right_hand=(-30, 0, 0),
              left_arm=(-90, 14, 0), left_forearm=(0, 0, 0), left_hand=(-30, 0, 0), right_fingers=(0, 0, 0), left_fingers=(0, 0, 0))
    out.append(clip('special_2', 30, [(0, idle), (4, bite2), (20, P(bite2, torso=(12, 0, 0))), (30, idle)]))
    # low sweep: drops into a crouch and spins on one hand, the leg scything round (40 t)
    low_ = P(idle, root=low(-7), waist=(30, 0, 0), torso=(20, 0, 0), head=(-20, 0, 0), right_arm=(-60, 0, 20), right_forearm=(0, 0, 0),
             left_arm=(10, 0, -40), right_leg=(-10, 0, 80), right_shin=(0, 0, 0), left_leg=(-90, 0, 0), left_shin=(130, 0, 0))
    sw = [(0, idle), (4, P(low_, root={'rot': (0, 0, 0), 'pos': (0, -7, 0)}))]
    for i in range(1, 5):
        sw.append((4 + i * 3, P(low_, root={'rot': (0, 90 * i, 0), 'pos': (0, -7, 0)})))
    sw += [(24, P(low_, root={'rot': (0, 360, 0), 'pos': (0, -7, 0)})), (32, P(idle, root={'rot': (0, 360, 0), 'pos': (0, -1, 0)})),
           (40, P(idle, root={'rot': (0, 360, 0), 'pos': (0, -1, 0)}))]
    out.append(clip('special_3', 40, sw))
    # pistols: alternating shots (18 t)
    gun = P(aim, right_arm=(-90, 10, 0), left_arm=(-90, -10, 0), head=(-6, 0, 0), waist=(0, 0, 0))
    out.append(clip('shoot', 18, [(0, idle), (3, gun), (5, P(gun, right_arm=(-104, 10, 0))), (8, gun), (10, P(gun, left_arm=(-104, -10, 0))),
                                  (13, gun), (15, P(gun, right_arm=(-104, 10, 0))), (18, gun)]))
    # acrobatic leap-kick: flip, both feet forward
    kick = P(idle, root={'rot': (360, 0, 0), 'pos': (0, 0, 0)}, right_leg=(-90, 0, 0), left_leg=(-80, 0, 0), right_shin=(0, 0, 0), left_shin=(0, 0, 0),
             torso=(-30, 0, 0), hair=(-50, 0, 0))
    flip = [(0, idle), (4, P(idle, root=low(-3)))] + wheel(P(tuck, hair=(-30, 0, 0)), 5, 16, 360, 15.3, cz=-10.7, drop=-5, step=1.5, lift=8)
    flip += [(20, kick), (40, kick)]
    out.append(clip('leap', 40, flip))
    # back-flip dodge (14 t)
    bf = [(0, idle), (2, P(idle, root=low(-3)))] + wheel(P(tuck, hair=(30, 0, 0)), 3, 12, -360, 15.3, cz=-10.7, drop=-5, step=1.5, lift=6)
    bf += [(14, P(idle, root={'rot': (-360, 0, 0), 'pos': (0, -1, 0)}))]
    out.append(clip('dodge', 14, bf))
    # joint move (rework §15), the vault: squared up to Clint, deep crouch, hands cupped low in front for his foot
    # (held while he runs in, up to VAULT_RUN_TICKS) ...
    cup = P(idle, root=low(-5), waist=(26, 0, 0), torso=(18, 0, 0), head=(-34, 0, 0), right_arm=(-44, 0, -16), right_forearm=(-36, 0, 0),
            right_hand=(0, 0, 0), left_arm=(-44, 0, 16), left_forearm=(-36, 0, 0), left_hand=(0, 0, 0), right_fingers=(0, 0, -40),
            left_fingers=(0, 0, 40), right_leg=(-56, 0, 8), right_shin=(80, 0, 0), left_leg=(-30, 0, -8), left_shin=(70, 0, 0), hair=(10, 0, 0))
    out.append(clip('team_1', 40, [(0, idle), (6, cup), (22, P(cup, root=low(-5.4), head=(-36, 0, 0))), (40, cup)]))
    # ... and the heave: the whole body uncoils, arms thrown up over the head, Clint launched (20 t)
    heave = P(idle, root=low(1), waist=(-8, 0, 0), torso=(-14, 0, 0), head=(-40, 0, 0), right_arm=(-170, 0, -10), right_forearm=(-10, 0, 0),
              left_arm=(-170, 0, 10), left_forearm=(-10, 0, 0), right_leg=(0, 0, 4), right_shin=(0, 0, 0), left_leg=(0, 0, -4), left_shin=(0, 0, 0),
              hair=(-30, 0, 0), **OPEN)
    out.append(clip('team_2', 20, [(0, cup), (3, P(cup, root=low(-3), right_arm=(-90, 0, -16), left_arm=(-90, 0, 16))), (6, heave), (11, heave),
                                   (20, idle)]))
    out.append(clip('team_3', 24, [(0, idle), (24, idle)]))  # no third joint move for Natasha
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
    out.append(clip('shoot', 18, shot))
    # explosive arrow: lobbed high (bow raised), leaning back into the draw
    hi = dict(left_arm=(-130, 70, 0), torso=(-14, -20, 0), head=(-30, 74, 0), waist=(-6, -60, 0))
    out.append(clip('special_1', 18, [(0, idle), (4, P(raise_, **hi)), (10, P(drawn, **hi)), (12, P(loose, **hi)), (15, P(loose, **hi)),
                                      (18, P(raise_, arrow=(0, 0, 0)))]))
    # shock arrow: long careful draw, a pause on the anchor
    out.append(clip('special_2', 18, [(0, idle), (3, raise_), (7, drawn), (11, P(drawn, right_arm=(-90, 84, -20), head=(-4, 76, 0))), (12, loose),
                                      (15, loose), (18, P(raise_, arrow=(0, 0, 0)))]))
    # three arrows at once: bow canted flat, string hand fanned
    cant = dict(bow=(0, 0, -70), left_arm=(-90, 70, -20), torso=(4, -24, 0))
    out.append(clip('special_3', 18, [(0, idle), (4, P(raise_, **cant)), (10, P(drawn, **cant, right_fingers=(0, 0, -30))), (12, P(loose, **cant)),
                                      (15, P(loose, **cant)), (18, P(raise_, arrow=(0, 0, 0)))]))
    # circle: nocks and draws pointing up and out over the circle, holds
    up = P(drawn, left_arm=(-130, 70, 0), torso=(-10, -20, 0), head=(-30, 74, 0))
    out.append(clip('circle', 80, [(0, idle), (10, raise_), (24, drawn), (34, up), (72, up), (80, idle)]))
    st = P(idle, waist=(6, -30, 0), torso=(8, -20, 0), left_arm=(-90, -40, -20), left_forearm=(-10, 0, 0), bow=(0, 0, 90))
    out.append(clip('attack_1', 10, [(0, idle), (3, P(idle, waist=(4, 30, 0), left_arm=(-80, 80, 0), bow=(0, 0, 90))), (5, st), (7, st), (10, idle)]))
    # backhand with the bow limb (10 t, hit at 4)
    bh = P(idle, waist=(6, 40, 0), torso=(8, 24, 0), left_arm=(-90, 60, -30), left_forearm=(-10, 0, 0), bow=(0, 0, -90))
    out.append(clip('attack_2', 10, [(0, idle), (3, P(idle, waist=(4, -30, 0), left_arm=(-60, -40, -20), bow=(0, 0, -90))), (5, bh), (7, bh), (10, idle)]))
    # front kick that shoves the mob away, bow held back (16 t, hit at 6)
    fk = P(idle, root=low(1), torso=(-16, 0, 0), right_leg=(-95, 0, 0), right_shin=(0, 0, 0), left_shin=(10, 0, 0), left_arm=(30, 0, -20))
    out.append(clip('attack_3', 16, [(0, idle), (3, P(idle, right_leg=(-70, 0, 0), right_shin=(100, 0, 0))), (6, fk), (9, fk), (16, idle)]))
    out.append(clip('dodge', 12, [(0, idle), (3, P(idle, root=low(-2), torso=(-10, 0, 0), right_leg=(30, 0, 0), left_leg=(20, 0, 0))), (12, idle)]))
    # grapple: fires the line up (right arm), then hangs on it pulled up
    hang = P(idle, right_arm=(-170, 0, 10), right_forearm=(0, 0, 0), right_fingers=(0, 0, -95), head=(-30, 0, 0),
             right_leg=(-20, 0, 0), right_shin=(40, 0, 0), left_leg=(10, 0, 0), left_shin=(20, 0, 0))
    out.append(clip('leap', 40, [(0, idle), (4, P(hang, right_fingers=(0, 0, 0))), (10, hang), (34, hang), (40, idle)]))
    # joint move (rework §15), the vault: a step onto Natasha's hands, a tucked front flip on the way up, and the
    # bow comes up at the top, leaning out over the enemies (18 t; the rise is ~13 t)
    air = dict(waist=(24, -60, 0), torso=(16, -20, 0), right_leg=(-44, 0, 4), right_shin=(70, 0, 0), left_leg=(-14, 0, -4), left_shin=(44, 0, 0))
    tuck = P(idle, waist=(40, 0, 0), torso=(30, 0, 0), head=(-20, 0, 0), right_leg=(-110, 0, 0), right_shin=(120, 0, 0), left_leg=(-110, 0, 0),
             left_shin=(120, 0, 0), right_arm=(-60, 0, 20), left_arm=(-60, 0, -20))
    step = P(idle, root=low(-2), waist=(14, 0, 0), right_leg=(-60, 0, 0), right_shin=(60, 0, 0), left_leg=(20, 0, 0), left_shin=(30, 0, 0))
    a_raise, a_drawn, a_loose = P(raise_, **air), P(drawn, **air), P(loose, **air)
    flip = [(0, step), (2, P(idle, root=low(0), right_leg=(-10, 0, 0), right_shin=(10, 0, 0), left_leg=(30, 0, 0), left_shin=(40, 0, 0)))]
    flip += wheel(tuck, 3, 11, 360, 15.3, cz=-10.7, step=1)
    flip += [(13, P(a_raise, root={'rot': (360, 0, 0), 'pos': (0, 0, 0)})), (16, P(a_drawn, root={'rot': (360, 0, 0), 'pos': (0, 0, 0)})),
             (18, P(a_drawn, root={'rot': (360, 0, 0), 'pos': (0, 0, 0)}))]
    out.append(clip('team_1', 18, flip))
    # the shot from the air, back to back every 12 t while he hangs (draw, loose at VAULT_AIR_RELEASE 8)
    out.append(clip('team_2', 12, [(0, a_raise), (5, a_drawn), (8, a_loose), (10, a_loose), (12, P(a_raise, arrow=(0, 0, 0)))]))
    # the drop beside Natasha: bow arm out for balance, legs ready for the landing
    drop = P(idle, waist=(6, 0, 0), right_arm=(-120, 0, 40), right_forearm=(-20, 0, 0), left_arm=(-60, 0, -50), left_forearm=(-10, 0, 0),
             right_leg=(-30, 0, 4), right_shin=(50, 0, 0), left_leg=(-6, 0, -4), left_shin=(30, 0, 0), **OPEN)
    out.append(clip('team_3', 30, [(0, a_loose), (5, drop), (18, P(drop, right_arm=(-130, 0, 46), left_arm=(-56, 0, -56))), (30, drop)]))
    return out


def quinjet():
    c = []
    c.append(clip('flight', 40, [(0, {'body': (0, 0, 0), 'engine_right': (0, 0, 0), 'engine_left': (0, 0, 0)}),
                                  (20, {'body': (-2, 0, 1), 'engine_right': (0, 0, 0), 'engine_left': (0, 0, 0)}),
                                  (40, {'body': (0, 0, 0), 'engine_right': (0, 0, 0), 'engine_left': (0, 0, 0)})], loop=True))
    # rear ramp hinged at the cabin floor: closed = the rear wall, open = a walkway sloping down behind the jet
    c.append(clip('ramp_open', 24, [(0, {'ramp': (0, 0, 0)}), (8, {'ramp': (-30, 0, 0)}), (18, {'ramp': (-88, 0, 0)}),
                                    (24, {'ramp': (-100, 0, 0)})]))
    c.append(clip('hover', 20, [(0, {'engine_right': (-80, 0, 0), 'engine_left': (-80, 0, 0)}),
                                (20, {'engine_right': (-80, 0, 0), 'engine_left': (-80, 0, 0)})], loop=True))
    return c


def main():
    for name, clips in (('iron_man', iron_man()), ('thor', thor()), ('hulk', hulk()), ('black_widow', widow()), ('hawkeye', hawkeye()),
                        ('quinjet', quinjet())):
        size = write_anims(f'animations/avengers/{name}.animation.json', [unwind(c) for c in clips])
        print(name, len(clips), 'clips', size, 'bytes')


if __name__ == '__main__':
    main()
