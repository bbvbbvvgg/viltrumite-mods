"""First-person clips of Captain America (arms + shield model captain_arms*.geo.json), own work, CC0.
Same clip names/lengths as the third-person set (one server clock). The renderer draws the FP model at
CaptainRenderer.FP_OFFSET = (0, -1.72, -0.38) blocks in camera space. Shield poses solved with the preview
math: armorLeftArm (-90, 90, 15) + left_forearm (-90) = shield square in front of the camera (block)."""
from jinwoo_clips import clip
from jinwoo_geo import write_anims

FP_OFFSET = (0.0, -1.72, -0.38)


def F(base=None, **kw):
    names = {'ra': 'armorRightArm', 'rf': 'right_forearm', 'rh': 'right_hand', 'rfi': 'right_fingers',
             'la': 'armorLeftArm', 'lf': 'left_forearm', 'lh': 'left_hand', 'lfi': 'left_fingers'}
    out = dict(base or {})
    for k, v in kw.items():
        out[names[k]] = v
    return out


FISTS = dict(rfi=(0, 0, -95), lfi=(0, 0, 95))
CALM = F(ra=(10, 0, 0), rf=(0, 0, 0), rh=(0, 0, 0), la=(-30, 60, -60), lf=(-105, 0, 0), lh=(0, 0, 0), rfi=(0, 0, -25), lfi=(0, 0, 25))
GUARD = F(ra=(-56, -6, 0), rf=(-50, 0, 0), rh=(-10, 0, 0), la=(-40, 66, -50), lf=(-100, 0, 0), lh=(0, 0, 0), **FISTS)
GUARD_B = F(GUARD, ra=(-58, -6, 0), la=(-42, 66, -50))
BLOCK = F(GUARD, ra=(-30, -6, 0), rf=(-70, 0, 0), la=(-90, 90, 15), lf=(-90, 0, 0))
ARMS = F(GUARD, ra=(-80, -30, 0), rf=(-80, 0, 0), la=(-80, 30, 0), lf=(-80, 0, 0))
FREE = F(GUARD, la=(-54, 6, 0), lf=(-55, 0, 0))
RUN = F(GUARD, ra=(-30, -6, 0), rf=(-60, 0, 0), la=(-30, 60, -50), lf=(-100, 0, 0))
OPEN_L = (0, 0, 0)


def fp_clips():
    c = []
    c.append(clip('idle_calm', 60, [(0, CALM), (30, F(CALM, la=(-28, 60, -60))), (60, CALM)], loop=True))
    c.append(clip('idle_combat', 40, [(0, GUARD), (20, GUARD_B), (40, GUARD)], loop=True))
    c.append(clip('run', 12, [(0, RUN), (6, F(RUN, ra=(-26, -6, 0), la=(-34, 60, -50))), (12, RUN)], loop=True))
    c.append(clip('block', 20, [(0, BLOCK), (10, F(BLOCK, la=(-92, 90, 15))), (20, BLOCK)], loop=True))
    c.append(clip('block_arms', 20, [(0, ARMS), (10, F(ARMS, ra=(-82, -30, 0))), (20, ARMS)], loop=True))
    # shield combo
    bash = F(GUARD, la=(-88, 80, 10), lf=(-30, 0, 0))
    c.append(clip('shield_1', 9, [(0, GUARD), (2, F(GUARD, la=(-30, 70, -60))), (4, bash), (6, bash), (9, GUARD)]))
    rim_a = F(GUARD, la=(-80, 110, 0), lf=(-70, 0, 0))
    rim_b = F(GUARD, la=(-84, -20, -30), lf=(-20, 0, 0))
    c.append(clip('shield_2', 10, [(0, GUARD), (3, rim_a), (5, rim_b), (7, F(rim_b, la=(-80, -40, -40))), (10, GUARD)]))
    upc = F(GUARD, la=(-150, 60, 0), lf=(-40, 0, 0))
    c.append(clip('shield_3', 11, [(0, GUARD), (3, F(GUARD, la=(0, 60, -20), lf=(-50, 0, 0))), (6, upc), (8, upc), (11, GUARD)]))
    over = F(GUARD, la=(-170, 60, 0), lf=(-40, 0, 0), ra=(-170, -20, 0), rf=(-40, 0, 0))
    down = F(GUARD, la=(-50, 60, 0), lf=(-30, 0, 0), ra=(-50, -20, 0), rf=(-40, 0, 0))
    c.append(clip('shield_4', 18, [(0, GUARD), (4, F(GUARD, la=(-10, 60, -30))), (8, over), (11, over), (12, down), (15, down), (18, GUARD)]))
    jab = F(FREE, la=(-90, 14, 0), lf=(-4, 0, 0))
    c.append(clip('fist_1', 7, [(0, FREE), (2, F(FREE, la=(-70, 14, 0))), (3, jab), (4, jab), (7, FREE)]))
    cross = F(FREE, ra=(-90, -14, 0), rf=(-4, 0, 0))
    c.append(clip('fist_2', 8, [(0, FREE), (2, F(FREE, ra=(-40, -14, 20))), (4, cross), (5, cross), (8, FREE)]))
    c.append(clip('fist_3', 12, [(0, FREE), (4, F(FREE, ra=(-20, 0, 40), la=(-20, 0, -40))), (7, F(FREE, ra=(-10, 0, 60), la=(-10, 0, -60))),
                                 (12, FREE)]))
    chop = F(GUARD, la=(-40, 30, -20), lf=(-6, 0, 0))
    c.append(clip('counter', 13, [(0, BLOCK), (3, F(GUARD, la=(-176, 40, 0), lf=(-50, 0, 0))), (6, chop), (9, chop), (13, GUARD)]))
    sweep = F(GUARD, la=(-88, -40, -20), lf=(-6, 0, 0))
    c.append(clip('return_strike', 12, [(0, GUARD), (3, rim_a), (6, sweep), (9, sweep), (12, GUARD)]))
    c.append(clip('roll_sweep', 12, [(0, FREE), (6, F(FREE, ra=(-10, 0, 60), la=(-10, 0, -60))), (12, FREE)]))
    c.append(clip('parry', 8, [(0, F(BLOCK, la=(-90, 70, 0))), (2, F(BLOCK, la=(-88, 50, -10))), (5, BLOCK), (8, BLOCK)]))
    # skills
    wind = F(GUARD, la=(-84, 120, 10), lf=(-80, 0, 0))
    rel = F(GUARD, la=(-88, -40, -10), lf=(0, 0, 0), lfi=OPEN_L)
    c.append(clip('throw', 10, [(0, GUARD), (3, wind), (4, rel), (6, F(rel, la=(-80, -60, -20))), (10, F(FREE, lfi=OPEN_L))]))
    aim = F(wind, ra=(-90, -10, 0), rf=(0, 0, 0), rfi=(0, 0, 0))
    c.append(clip('aim', 20, [(0, aim), (10, F(aim, ra=(-92, -10, 0))), (20, aim)], loop=True))
    catch = F(FREE, la=(-92, 40, -10), lf=(-20, 0, 0), lfi=OPEN_L)
    c.append(clip('catch', 9, [(0, catch), (1, F(catch, la=(-80, 50, -10))), (4, F(GUARD, la=(-50, 70, -40))), (9, GUARD)]))
    c.append(clip('recall', 7, [(0, FREE), (2, F(FREE, la=(-100, 0, -10), lf=(0, 0, 0), lfi=OPEN_L)), (5, F(FREE, la=(-96, 0, -10))), (7, FREE)]))
    c.append(clip('rush', 12, [(0, BLOCK), (3, F(BLOCK, la=(-88, 90, 15))), (6, BLOCK), (9, F(BLOCK, la=(-92, 90, 15))), (12, BLOCK)]))
    c.append(clip('rush_bash', 9, [(0, BLOCK), (2, F(BLOCK, la=(-90, 80, 10), lf=(-30, 0, 0))), (5, F(BLOCK, la=(-90, 80, 10), lf=(-30, 0, 0))), (9, GUARD)]))
    lift = F(GUARD, la=(-170, 60, 0), lf=(-30, 0, 0), ra=(-150, -20, 0), rf=(-40, 0, 0))
    c.append(clip('slam_leap', 50, [(0, GUARD), (3, lift), (9, lift), (14, F(lift, la=(-120, 60, 0), ra=(-120, -20, 0))), (50, F(lift, la=(-120, 60, 0), ra=(-120, -20, 0)))]))
    c.append(clip('slam_impact', 14, [(0, F(GUARD, la=(-40, 60, 0), lf=(-20, 0, 0), ra=(-30, 0, 30))), (6, F(GUARD, la=(-40, 60, 0), lf=(-20, 0, 0))), (14, GUARD)]))
    c.append(clip('roll', 10, [(0, F(GUARD, la=(-20, 60, -40), ra=(-20, 0, 20))), (10, GUARD)]))
    c.append(clip('landing', 12, [(0, F(GUARD, ra=(-20, 0, 30), la=(-20, 60, -40))), (5, F(GUARD, ra=(-20, 0, 30), la=(-20, 60, -40))), (12, GUARD)]))
    raise_ = F(GUARD, la=(-160, 40, -20), lf=(-20, 0, 0), ra=(-100, -60, 30), rf=(-10, 0, 0), rfi=(0, 0, 0))
    c.append(clip('assemble_call', 30, [(0, GUARD), (5, raise_), (20, raise_), (30, GUARD)]))
    c.append(clip('assemble_wait', 120, [(0, GUARD), (60, GUARD_B), (120, GUARD)]))
    c.append(clip('assemble_raise', 80, [(0, GUARD), (10, BLOCK), (70, BLOCK), (80, GUARD)]))
    # Thor's hammer on the shield (joint move): the shield is driven down toward the camera, then held up again
    c.append(clip('team_brace', 18, [(0, F(BLOCK, la=(-78, 90, 15), lf=(-96, 0, 0))), (3, F(BLOCK, la=(-82, 90, 15), lf=(-94, 0, 0))),
                                     (9, F(BLOCK, la=(-88, 90, 15))), (18, BLOCK)]))
    return c


def main():
    size = write_anims('animations/captain/captain_fp.animation.json', fp_clips())
    print('captain_fp', size, 'bytes')


if __name__ == '__main__':
    main()
