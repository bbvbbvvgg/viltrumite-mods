"""First-person clips (arms + sleeves + daggers model jinwoo_arms*.geo.json), own work, CC0.
Same clip names and lengths as the third-person set so both views read one server clock.
The renderer places the FP model at FP_OFFSET (blocks, camera space) — keep in sync with
client/jinwoo/JinwooFirstPerson.FP_OFFSET."""
from jinwoo_clips import clip, T  # noqa: F401
from jinwoo_geo import write_anims

FP_OFFSET = (0.0, -1.66, -0.30)
REV = (180, 0, 0)


def F(base=None, **kw):
    """FP pose with short names: ra/rf/rh/rd/rfi = right arm/forearm/hand/dagger/fingers, l* = left."""
    names = {'ra': 'armorRightArm', 'rf': 'right_forearm', 'rh': 'right_hand', 'rd': 'right_dagger', 'rfi': 'right_fingers',
             'la': 'armorLeftArm', 'lf': 'left_forearm', 'lh': 'left_hand', 'ld': 'left_dagger', 'lfi': 'left_fingers'}
    out = dict(base or {})
    for k, v in kw.items():
        out[names[k]] = v
    return out


HIDDEN = F(ra=(10, 0, 0), rf=(0, 0, 0), rh=(0, 0, 0), rd=(0, 0, 0), rfi=(0, 0, -25),
           la=(10, 0, 0), lf=(0, 0, 0), lh=(0, 0, 0), ld=REV, lfi=(0, 0, 25))
GUARD = F(ra=(-58, -14, 0), rf=(-40, 0, 0), rh=(-10, 0, 0), rd=(60, 0, 0), rfi=(0, 0, -95),
          la=(-50, 14, 0), lf=(-50, 0, 0), lh=(0, 0, 0), ld=(70, 0, 0), lfi=(0, 0, 95))
GUARD_B = F(GUARD, ra=(-60, -14, 0), la=(-52, 14, 0))
BLOCK = F(GUARD, ra=(-78, -30, 0), rf=(-40, 0, 0), rd=(0, 0, 90), la=(-78, 30, 0), lf=(-40, 0, 0), ld=(0, 0, -90))
RUN = F(GUARD, ra=(-30, -6, 0), rf=(-50, 0, 0), la=(-24, 6, 0), lf=(-55, 0, 0))
OPEN_R = (0, 0, 0)


def fp_clips():
    c = []
    c.append(clip('idle_calm', 60, [(0, HIDDEN), (60, HIDDEN)], loop=True))
    c.append(clip('idle_combat', 40, [(0, GUARD), (20, GUARD_B), (40, GUARD)], loop=True))
    c.append(clip('run', 12, [(0, RUN), (6, F(RUN, ra=(-26, -6, 0), la=(-30, 6, 0))), (12, RUN)], loop=True))
    c.append(clip('block', 10, [(0, BLOCK), (5, F(BLOCK, ra=(-80, -30, 0))), (10, BLOCK)], loop=True))
    mid = F(GUARD, ra=(-30, 0, 0), la=(-30, 0, 0))
    c.append(clip('draw', 12, [(0, HIDDEN), (4, F(mid, rfi=OPEN_R)), (8, F(GUARD, ra=(-64, 10, 0), la=(-56, -10, 0))), (12, GUARD)]))
    c.append(clip('sheath', 8, [(0, GUARD), (4, mid), (8, HIDDEN)]))
    # combo: right horizontal R->L, left backhand L->R, cross X, spin (arms wide), finisher double stab
    c.append(clip('combo_1', 6, [(0, GUARD), (2, F(GUARD, ra=(-80, 40, 20), rf=(-20, 0, 0), rd=(80, 0, 0))),
                                 (3, F(GUARD, ra=(-82, -40, 0), rf=(-10, 0, 0), rd=(80, 0, 0))),
                                 (5, F(GUARD, ra=(-70, -60, 0), rf=(-20, 0, 0), rd=(80, 0, 0))), (6, GUARD)]))
    c.append(clip('combo_2', 6, [(0, GUARD), (2, F(GUARD, la=(-84, -40, -20), lf=(-20, 0, 0))),
                                 (3, F(GUARD, la=(-84, 40, 0), lf=(-10, 0, 0))), (5, F(GUARD, la=(-70, 60, 0))), (6, GUARD)]))
    high = F(GUARD, ra=(-150, 20, 10), rf=(-20, 0, 0), rd=(80, 0, 0), la=(-150, -20, -10), lf=(-20, 0, 0), ld=(80, 0, 0))
    low = F(high, ra=(-50, -30, 0), la=(-50, 30, 0), rf=(-10, 0, 0), lf=(-10, 0, 0))
    c.append(clip('combo_3', 10, [(0, GUARD), (3, high), (5, low), (8, low), (10, GUARD)]))
    wide = F(GUARD, ra=(-80, 60, 30), rf=(-10, 0, 0), rd=(80, 0, 0), la=(-80, -60, -30), lf=(-10, 0, 0), ld=(80, 0, 0))
    c.append(clip('combo_4', 9, [(0, GUARD), (2, wide), (5, F(wide, ra=(-80, -40, 0), la=(-80, -100, -20))), (7, wide), (9, GUARD)]))
    over = F(GUARD, ra=(-170, -10, 0), rf=(-10, 0, 0), rd=REV, la=(-170, 10, 0), lf=(-10, 0, 0), ld=REV)
    stab = F(over, ra=(-60, -14, 0), la=(-60, 14, 0), rf=(-30, 0, 0), lf=(-30, 0, 0))
    c.append(clip('combo_5', 14, [(0, GUARD), (4, F(GUARD, ra=(-20, 0, 0), la=(-20, 0, 0))), (7, over), (9, stab), (12, stab), (14, GUARD)]))
    c.append(clip('low_sweep', 8, [(0, GUARD), (2, F(GUARD, ra=(-40, 60, 20), rd=(80, 0, 0))), (4, F(GUARD, ra=(-44, -60, 0), rd=(80, 0, 0))),
                                   (6, F(GUARD, ra=(-40, -70, 0), rd=(80, 0, 0))), (8, GUARD)]))
    c.append(clip('air_1', 6, [(0, GUARD), (2, F(GUARD, ra=(-160, 20, 0), rd=(80, 0, 0))), (3, F(GUARD, ra=(-50, -20, 0), rd=(80, 0, 0))), (6, GUARD)]))
    c.append(clip('air_2', 6, [(0, GUARD), (2, F(GUARD, la=(-90, -60, 0))), (3, F(GUARD, la=(-90, 50, 0))), (6, GUARD)]))
    dive = F(GUARD, ra=(-100, -10, 0), rf=(-10, 0, 0), rd=REV, la=(-100, 10, 0), lf=(-10, 0, 0), ld=REV)
    c.append(clip('dive', 40, [(0, GUARD), (4, dive), (40, dive)]))
    c.append(clip('dive_land', 8, [(0, F(dive, ra=(-60, -10, 0), la=(-60, 10, 0))), (3, F(dive, ra=(-60, -10, 0), la=(-60, 10, 0))), (8, GUARD)]))
    thrust = F(GUARD, ra=(-90, -8, 0), rf=(0, 0, 0), rh=(-10, 0, 0), rd=(90, 0, 0))
    c.append(clip('riposte', 6, [(0, BLOCK), (2, thrust), (4, thrust), (6, GUARD)]))
    c.append(clip('step', 8, [(0, GUARD), (2, RUN), (6, RUN), (8, GUARD)]))
    keys = [(0, GUARD), (4, RUN)]
    for i in range(8):
        k = F(GUARD, ra=(-86, -40 + i * 6, 0), rf=(-10, 0, 0), rd=(80, 0, 0)) if i % 2 == 0 else \
            F(GUARD, la=(-86, 40 - i * 6, 0), lf=(-10, 0, 0))
        keys.append((5 + i * 2, k))
    keys += [(21, high), (22, low), (26, low)]
    c.append(clip('mutilation', 26, keys))
    grip = F(GUARD, la=(-92, -4, 0), lf=(-4, 0, 0), lh=(-40, 0, 0), ld=(0, 0, 0), lfi=(0, 0, 55), ra=(-30, -6, 0), rf=(-50, 0, 0))
    c.append(clip('authority_grip', 60, [(0, GUARD), (5, F(grip, lfi=(0, 0, 0))), (10, grip), (35, F(grip, la=(-94, -4, 0), lfi=(0, 0, 70))), (60, grip)]))
    c.append(clip('authority_slam', 10, [(0, F(grip, la=(-150, -4, 0))), (4, F(grip, la=(-40, -4, 0), lfi=(0, 0, 100))), (10, GUARD)]))
    ex = F(HIDDEN, ra=(-62, -8, 0), rf=(-10, 0, 0), rh=(40, 0, 0), rfi=(0, 0, 0))
    rise = F(ex, ra=(-130, -8, 0), rh=(-30, 0, 0))
    c.append(clip('extraction', 36, [(0, HIDDEN), (6, ex), (18, F(ex, rfi=(0, 0, -20))), (28, ex), (31, rise), (36, rise)]))
    c.append(clip('exchange', 20, [(0, GUARD), (10, F(GUARD, ra=(-20, 0, 0), la=(-20, 0, 0))), (20, GUARD)]))
    spread = F(HIDDEN, ra=(-60, 40, 40), la=(-60, -40, -40), rfi=OPEN_R, lfi=OPEN_R)
    point = F(HIDDEN, ra=(-92, -6, 0), rf=(0, 0, 0), rfi=(0, 0, -40))
    c.append(clip('arise', 100, [(0, GUARD), (10, HIDDEN), (24, spread), (45, F(spread, ra=(-90, 50, 50), la=(-90, -50, -50))),
                                 (58, F(spread, ra=(-90, 50, 50), la=(-90, -50, -50))), (62, point), (75, point), (100, GUARD)]))
    c.append(clip('summon', 16, [(0, GUARD), (4, F(GUARD, ra=(-140, 0, 0), rd=(0, 0, 0), rfi=OPEN_R)),
                                 (6, F(GUARD, ra=(-50, 0, 0), rh=(30, 0, 0), rfi=OPEN_R)), (12, F(GUARD, ra=(-50, 0, 0), rfi=OPEN_R)), (16, GUARD)]))
    c.append(clip('recall', 14, [(0, GUARD), (4, F(GUARD, ra=(-90, 30, 0), rfi=OPEN_R)), (9, F(GUARD, ra=(-60, -10, 0), rf=(-70, 0, 0), rfi=(0, 0, -100))), (14, GUARD)]))
    c.append(clip('command_attack', 12, [(0, GUARD), (3, F(thrust, ra=(-120, -8, 0))), (4, thrust), (9, thrust), (12, GUARD)]))
    c.append(clip('command_guard', 12, [(0, GUARD), (2, F(GUARD, ra=(-80, 70, 0), rfi=OPEN_R)), (6, F(GUARD, ra=(-80, -50, 0), rfi=OPEN_R)), (9, F(GUARD, ra=(-80, -50, 0))), (12, GUARD)]))
    c.append(clip('command_sentinel', 12, [(0, GUARD), (3, F(GUARD, ra=(-96, 0, 0), rf=(0, 0, 0), rfi=(0, 0, -40))), (6, F(GUARD, ra=(-96, 0, 0), rf=(0, 0, 0))),
                                           (8, F(GUARD, ra=(-50, 0, 0), rh=(30, 0, 0), rfi=OPEN_R)), (12, GUARD)]))
    lunge = F(GUARD, ra=(-96, -6, 0), rf=(0, 0, 0), rd=(90, 0, 0), la=(-96, 6, 0), lf=(0, 0, 0), ld=(90, 0, 0))
    c.append(clip('monarch_charge', 22, [(0, GUARD), (6, F(RUN, ra=(-10, 0, 0), la=(-10, 0, 0))), (15, F(RUN, ra=(-6, 0, 0), la=(-6, 0, 0))),
                                         (16, lunge), (20, lunge), (22, GUARD)]))
    return c


def main():
    print('fp', write_anims('animations/jinwoo/jinwoo_fp.animation.json', fp_clips()), 'bytes')


if __name__ == '__main__':
    main()
