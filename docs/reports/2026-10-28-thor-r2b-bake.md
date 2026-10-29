# Thor R2b — first full bake of the clips (2026-10-28)

`python3 tools/assets/make_thor_clips.py --report docs/reports/2026-10-28-thor-r2b-clip-report.json` baked all 58 clip plans (+ FP twins, R1's crouch / sit / lift_squat kept) in 575 s into one `thor.animation.json` (119 clips). Before this the file held only R1's three clips, so every R2a attack and power ran without its clip (ThorClipContractTest: 82 violations). The old split into several `.animation.json` files is gone: AnimCache and the tests read only `thor.animation.json`.

## Result

- ThorClipContractTest (lengths = R2a server phases, contact ticks): **passes**.
- ThorClipBodyTest (Java mesh scan): **fails** — mostly belly ↔ upper leg 2–5 px in bent poses (also in R1's own crouch), plus the hand / hammer cases below.
- Python acceptance problems left (solver output, not hand-fixed):

```
PROBLEM lift_thor: 7 ticks with penetration, worst (5, 'body:left_forearm', 'body:body', 5.09)
PROBLEM lift_thor: la misses its target by 10.43 px
PROBLEM electric_wave: 13 ticks with penetration, worst (2, 'body:left_forearm', 'body:body', 4.84)
PROBLEM electric_wave: la misses its target by 2.76 px
PROBLEM wrath: 16 ticks with penetration, worst (72, 'body:left_shin', 'body:belly', 0.65)
PROBLEM wrath: lfoot misses its target by 1.41 px
PROBLEM hammer_swing_right: 8 ticks with penetration, worst (21, 'body:right_forearm', 'body:body', 1.59)
PROBLEM hammer_spin_fp: the near plane cuts a cube on screen at ticks [10]
PROBLEM hammer_spin: 1 ticks with penetration, worst (20, 'body:right_forearm', 'body:body', 1.12)
PROBLEM hammer_spin: ra misses its target by 4.64 px
PROBLEM hammer_spin: la misses its target by 4.86 px
PROBLEM hammer_heavy_spin_fp: the near plane cuts a cube on screen at ticks [10]
PROBLEM hammer_heavy_spin: 1 ticks with penetration, worst (20, 'body:right_forearm', 'body:body', 1.12)
PROBLEM hammer_heavy_spin: ra misses its target by 4.64 px
PROBLEM hammer_heavy_spin: la misses its target by 4.86 px
PROBLEM hammer_ground_slam: 5 ticks with penetration, worst (13, 'body:left_shin', 'body:belly', 0.68)
PROBLEM hammer_ground_slam: 3 ticks with the feet off the ground (px), e.g. (9, 1.21, 1.48)
PROBLEM hammer_ground_slam: rfoot misses its target by 3.63 px
PROBLEM hammer_ground_slam: lfoot misses its target by 3.61 px
PROBLEM hammer_strike_finisher: 5 ticks with penetration, worst (13, 'body:left_shin', 'body:belly', 0.68)
PROBLEM hammer_strike_finisher: 3 ticks with the feet off the ground (px), e.g. (9, 1.21, 1.48)
PROBLEM hammer_strike_finisher: rfoot misses its target by 3.63 px
PROBLEM hammer_strike_finisher: lfoot misses its target by 3.61 px
PROBLEM hammer_falling_slam: 4 ticks with penetration, worst (10, 'body:left_shin', 'body:belly', 0.68)
PROBLEM hammer_falling_slam: 3 ticks with the feet off the ground (px), e.g. (6, 1.19, 1.46)
PROBLEM hammer_falling_slam: rfoot misses its target by 3.63 px
PROBLEM hammer_falling_slam: lfoot misses its target by 3.61 px
PROBLEM hammer_heavy_down_fp: covers the crosshair 3 ticks in a row
PROBLEM hammer_strike_blow_fp: covers the crosshair 3 ticks in a row
PROBLEM hammer_heavy_reverse_fp: covers the crosshair 5 ticks in a row
PROBLEM hammer_heavy_reverse: 1 ticks with penetration, worst (3, 'mjolnir:handle', 'body:right_forearm', 0.71)
PROBLEM hammer_pommel: 14 ticks with penetration, worst (4, 'mjolnir:cube_40', 'body:right_arm', 3.45)
PROBLEM hammer_thrust: 26 ticks with penetration, worst (6, 'body:left_forearm', 'body:body', 5.1)
PROBLEM hammer_thrust: la misses its target by 6.31 px
PROBLEM hammer_lunge: 3 ticks with penetration, worst (12, 'body:right_forearm', 'body:body', 0.92)
PROBLEM hammer_lunge: lfoot misses its target by 0.61 px
PROBLEM hammer_low_sweep: 3 ticks with penetration, worst (12, 'mjolnir:plains', 'body:belly', 1.1)
PROBLEM fist_jab: 1 ticks with penetration, worst (12, 'body:left_forearm', 'body:body', 0.91)
PROBLEM fist_cross: 5 ticks with penetration, worst (8, 'belt:cube_19', 'body:right_leg', 0.85)
PROBLEM fist_hook: 4 ticks with penetration, worst (9, 'belt:cube_19', 'body:right_leg', 0.75)
PROBLEM fist_uppercut: 9 ticks with penetration, worst (10, 'belt:plains', 'body:right_leg', 1.15)
PROBLEM fist_haymaker: 6 ticks with penetration, worst (9, 'belt:cube_23', 'body:right_leg', 0.98)
PROBLEM fist_hammer_fist: 6 ticks with penetration, worst (9, 'belt:cube_23', 'body:right_leg', 2.28)
PROBLEM fist_hammer_fist_left: 5 ticks with penetration, worst (9, 'belt:handle_plains', 'body:right_leg', 1.88)
PROBLEM fist_ground_pound: 12 ticks with penetration, worst (10, 'belt:cube_23', 'body:right_leg', 3.32)
PROBLEM fist_ground_pound: 2 ticks with the feet off the ground (px), e.g. (7, 1.66, 1.97)
PROBLEM fist_ground_pound: rfoot misses its target by 3.50 px
PROBLEM fist_ground_pound: lfoot misses its target by 3.41 px
PROBLEM fist_body_blow: 12 ticks with penetration, worst (7, 'belt:cube_24', 'body:right_leg', 3.38)
PROBLEM fist_elbow: 5 ticks with penetration, worst (8, 'body:left_forearm', 'body:body', 1.67)
PROBLEM fist_push_kick: 15 ticks with penetration, worst (7, 'belt:cube_23', 'body:right_leg', 2.31)
PROBLEM fist_tackle: 17 ticks with penetration, worst (10, 'belt:cube_24', 'body:right_leg', 2.8)
PROBLEM fist_tackle_hook: 7 ticks with penetration, worst (9, 'belt:gold', 'body:right_leg', 1.43)
```

## Not verified

No in-game playback of these clips in this session (no GPU client run yet); no owner playtest.
