# Hulkbuster Mark 48 — cue sheet (H6)

Source of truth for timings: `HulkMove` phase ticks (20 t/s) and `HulkbusterCombat` / `HulkbusterMovement` /
`HulkbusterSuitUp` / `HulkbusterCage` / `HulkbusterUltimate` call sites. Every server cue goes through
`HulkbusterSounds.Cue` (one `playSound` for everyone around, no client duplicate). Loops are client
`HulkbusterLoops` instances with an explicit stop. Assets: `tools/sfx/hulkbuster.py` (own synthesis, CC0).
No vanilla `SoundEvents`. JARVIS lines are untouched.

Layer rule (spec §10): strike = servo wind-up → whoosh → steel plate → sub bass → metal tail, baked into one
file so the layers can never drift apart; the contact transient sits at the file's `contact` time, and the
server plays the file at the move's contact tick minus that offset (pre-roll is 0.08–0.62 s).

## One-shots

| Cue | Event / files | Trigger (tick) | Len | Vol | Pitch spread | Audience |
|---|---|---|---|---|---|---|
| STEP | `step` ×4: hydraulic hiss + foot thud + plate rattle | each stride while walking / running (`HulkbusterMovement`), heavy landing tail | 0.9 s | 0.9 | ±0.08 | all |
| SCREECH | `screech` ×5: stick-slip steel on steel, gliding | turns, combo 3 wind-up, seismic rift start, spare arm, crater rise, suit-up end, escape | 0.7–1.3 s | 0.8 | ±0.12 | all |
| SWING | `swing` ×3: servo whine + whoosh + push | INTRO of COMBO / GRAB_REACH / GRAB_THROW / GRAB_SLAM / DASH_STRIKE / COUNTER; whiff | 0.75 s, whoosh peak 0.36 s | 0.9 | ±0.1 | all |
| PUNCH | `punch` ×3: full strike chain | combo 1/2 contact (tick 11 / 12), ram hit | 1.4 s, contact 0.30 s | 1.0 | ±0.08 | all |
| HAMMER | `hammer` ×3: jackhammer stroke, 3 piston bursts | combo 3 contact (tick 18), counter | 1.0 s, contact 0.10 s | 1.2 | ±0.06 | all |
| FACE_PUNCH | `jackhammer` ×3: piston ×4 + bone crunch, short tail | each GRAB_PUNCH contact (ticks 10/9/9/8), pitch +0.04 per punch; Jet Ram pummel | 0.85 s, contact 0.08 s | 1.1 | ±0.06 | all |
| FINISH_PUNCH | `finish_punch`: long wind-up, piston ×5, crunch, boom | GRAB_FINISH contact (tick 26) | 2.4 s, contact 0.62 s | 1.4 | ±0.03 | all |
| CHARGE | `charge`: elbow thruster spool into a lock | CHARGED INTRO start (held up to `CHARGE_HOLD_MAX`) | 1.6 s | 0.9 | 0 | all |
| CHARGED_PUNCH | `charged_punch`: strike + thrust + ring | CHARGED contact | 2.2 s, contact 0.16 s | 1.3 | ±0.05 | all |
| GRAB | `grab` ×2: servo, fingers, clench, hiss | grab connects | 0.9 s, clench 0.16 s | 1.0 | ±0.05 | all |
| THROW | `throw` | GRAB_THROW release | 1.0 s | 1.1 | ±0.05 | all |
| SLAM / RIFT / DIVE_IMPACT | `slam` ×2: thud, crack, debris, rumble | GRAB_SLAM contact, SEISMIC_RIFT contact, dive landing | 2.2 s | 1.3–1.5 | ±0.05 | all |
| BLOCK / CLAMP / CAGE_LAND / ARM_DOCK | `clamp` ×4: hydraulic push, steel clang, latch | block raise, each of 5 suit-up plates (pitch 0.9 + 0.05·i), cage lock, spare arm dock | 0.9 s, clang 0.12 s | 0.8–1.4 | ±0.06 | all |
| BLOCK_HIT | `block_hit` ×3 | hit absorbed while blocking | 1.0 s | 1.0 | ±0.12 | all |
| REPULSOR_CHARGE | `repulsor_charge` | HEAVY_REPULSORS INTRO (30 t) | 1.3 s | 1.0 | 0 | all |
| REPULSOR_BEAM | `repulsor_beam` | HEAVY_REPULSORS MAIN start | 1.8 s | 1.3 | ±0.03 | all |
| DASH | `dash` ×2: thruster burst, kick, skid | double-tap dash start | 0.9 s | 1.1 | ±0.06 | all |
| JET_LAUNCH | `hop`: blast + ground kick | jet jump release, suit-up lift-off (pitch 1.2), hover re-light (0.8) | 1.6 s | 1.2 | ±0.05 | all |
| COUGH | `cough` ×2: sputter | jet refused (heat), hover out of fuel | 0.9 s | 0.9 | ±0.1 | all |
| LAND_HEAVY / HERO_LANDING | `land` ×2 | heavy landing, HERO_LANDING contact | 1.8 s | 1.1 / 1.6 | ±0.06 / 0 | all |
| ULT_IGNITE | `ult_ignite`: reactor overdrive + spool | JET_RAM INTRO (30 t) | 2.6 s | 1.4 | 0 | all |
| ULT_IMPACT | `ult_impact`: 4.5 s blast + debris + rumble tail | JET_RAM contact (~tick 110) | 4.5 s | 2.0 | 0 | all |
| POD_FALL / CAGE_FALL | `drop`: 2.6 s whistle into a thud | pod / cage spawn | 3.0 s | 1.4 / 1.2 | 0 | all |
| POD_OPEN / EXIT | `exit`: vent, hinges, 3 clanks | pod opens; suit exit | 1.8 s | 1.1 / 1.0 | 0 | all |
| REACTOR | `assemble`: reactor spin-up + lock | torso closed | 2.0 s | 1.0 | 0 | all |
| BREAK / ARM_DETACH | `break`: tear, rupture, sparks, collapse | suit destroyed; spare arm detach | 2.6 s | 1.2 / 0.9 | 0 | all |
| REFUSE | `refuse` | refused input | 0.4 s | 0.7 | 0 | owner only |
| GRAB_MISS / CAGE_MARK | `servo` (stage 5 file, kept) | grab whiff, cage marker | — | 0.7–0.8 | — | all |

## Client loops (`HulkbusterLoops`)

| Loop | Event | Wanted while | Volume / pitch | Stop |
|---|---|---|---|---|
| Servo hum | `servo_loop` (2 s, seamless) | machine is the body (ACTIVE / EXITING) | 0.06 idle → 0.40 at speed 0.28 b/t (or acting); pitch 0.85–1.15 | target 0: not big, dead, removed, hero change |
| Thrusters | `thruster_loop` (3 s) | JET_CHARGE (grows with ticks), JET_RISE, HOVER, DASH, RAM, DIVE, JET_RAM until FINISH | 0.25–1.0; pitch 0.7–1.35 | motion state ends / cancel |
| Charge | `charge_loop` (1 s) | INTRO of CHARGED or HEAVY_REPULSORS | 0.35–0.8 by charge level; pitch 0.8–1.25 | INTRO ends (release, cancel, hit) |
| Sparks | `spark_loop` (2 s) | damage stage ≥ 1 | 0.26 / 0.40 | suit repaired / exited |

Every loop fades (+0.08 / −0.12 per tick), never goes below volume 0.001 while alive (the engine drops a
0-volume channel), stops 3 ticks after its target and level both reach 0, is replaced after 20 t if the
engine dropped it (heartbeat older than 10 t), and is cleared on level change and logout.

## Not verified

The sandbox has no audio output: nothing here was auditioned by ear. Loudness numbers (100 ms K-weighted
momentary max per class, true peak) and seam scores are measured by `tools/sfx/hulkbuster.py`.
