# Hulkbuster Mark 48 — cue sheet (H6)

Source of truth for timings: `HulkMove` phase ticks (20 t/s) and `HulkbusterCombat` / `HulkbusterMovement` /
`HulkbusterSuitUp` / `HulkbusterCage` / `HulkbusterUltimate` call sites. Every server cue goes through
`HulkbusterSounds.Cue` (one `playSound` for everyone around, no client duplicate). Loops are client
`HulkbusterLoops` instances with an explicit stop. Assets: `tools/sfx/hulkbuster.py` (CC0 1.0 Freesound recordings carry every cue, own synthesis as the support layer; spec item 3, 2026-10-30).
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

## Fix spec item 3 — CC0 re-source pass (round 2, 2026-10-29): plan and blocker

Status (round 3, 2026-10-30): **done, see "Round 3" at the end.** The plan and blocker below are kept as the record of round 2.

### Blocker (measured)
- `openverse_sfx.py search` → `Openverse request failed: The read operation timed out` (3 tries); a direct
  `GET https://api.openverse.org/v1/audio/?q=metal%20clang&license=cc0&source=freesound` → HTTP 504 after 61 s.
- Without the Openverse record there is no Openverse UUID and no API `attribution` field: the helper's `package
  --source-id` refuses, and `credits.txt` cannot hold the attribution *verbatim* (skill §2). Packaging a recording with
  `--own` would misstate its provenance. So no recording was committed.
- What does work from the sandbox: Freesound landing pages (licence check) and `cdn.freesound.org` previews. The
  candidates below were checked on their landing pages (CC0 1.0 link present). Freesound search pages were not scraped.

### Cue list additions by real ticks (this round's scenes; all through `HulkbusterSounds`, server, everyone around)
| Cue (event) | Trigger | Code |
|---|---|---|
| EXIT (`exit`) | exit tick 0 (vent hiss, hinges, clanks) at the chest | `HulkbusterExit.begin` |
| CLAMP pitch 0.8 | exit tick 8, chest doors unlatch | `HulkbusterExit.tick` |
| SCREECH pitch 0.85 | exit tick 24, shell starts to kneel | same |
| STEP pitch 0.85 | exit tick 32, knee hits the ground | same |
| CLAMP pitch 0.9 | exit tick 42, doors close on the empty shell | same |
| CLAMP 1.15 + JET_LAUNCH 1.25/1.10/0.95 | shell t+10 / t+24 / t+38: arms / legs / torso module unlatch and lift off (open sky) | `HulkbusterExit.shellTick` |
| BREAK pitch 0.9 | shell t+200: crumbles (no open sky) | same |
| SWING pitch 0.75–0.91 | suit-up: each sky segment released (scene tick = arrive − 12) | `HulkbusterSuitUp.tick` |
| CLAMP ×5 (existing) | each `PART_LOCK` | same |
| REACTOR (existing) | assemble 20 | same |
| (flash only) | assemble 48, reactor flash | same |
Removed: the second EXIT at the exit end (it played the hinge file twice).

### Layer plan per family (synthesis stays as a support layer, recordings carry the body)
| Family | Events | Recording layer (attack / body) | Synth layer kept |
|---|---|---|---|
| steel clang | clamp ×4, block_hit ×3, cage/arm dock | clang transient + ring (cand. A, D, E), pitched −3…−7 st for mass | hydraulic push, latch tick, sub thud |
| metal impact | punch ×3, hammer ×3, jackhammer ×3 | heavy impact body (cand. B, C) at the contact offset | servo wind-up, whoosh, sub bass, tail |
| hydraulic / air | exit, step ×4 (hiss), pod open, steam beats | air release (cand. F) + hydraulic ram (cand. G) | hinge stick-slip, clanks |
| heavy thud on ground | step ×4, land ×2, slam ×2 | metal on concrete (cand. C) | 44–60 Hz thud, rattle |
| thrusters / servo | servo_loop, thruster_loop, hop, dash | still to find (queries: "jet engine burst", "rocket thruster", "servo motor whine") | loops stay spectral-freeze seamless (skill gotcha) |
Process when Openverse answers: one search per family, `download <uuid>` (verbatim attribution into credits.txt),
layer in `tools/sfx/hulkbuster.py` as a `SOURCES` table like `tools/sfx/reinhard_strikes.py`, level per class
(100 ms K-weighted, true-peak ceiling), package with `--source-id` per layer, then `tools/assets/ironman_sources.md`
+ `CREDITS.md` rows. Mono Vorbis 44.1 kHz `.ogg.b64`, variants as entries of one event.

### Candidates (CC0 1.0 confirmed on the landing page, 2026-10-29; preview URL from that page; NOT auditioned)
| Id | Landing page | Creator, title | Preview | Use |
|---|---|---|---|---|
| A | https://freesound.org/people/AardsReal/sounds/842171/ | AardsReal, Basic Metal Clang Free (mono 0.69 s) | https://cdn.freesound.org/previews/842/842171_13307919-hq.mp3 | clamp transient |
| B | https://freesound.org/people/RyanKingArt/sounds/620077/ | RyanKingArt, Metal Impact (1.37 s) | https://cdn.freesound.org/previews/620/620077_11069322-hq.mp3 | punch body |
| C | https://freesound.org/people/metrostock99/sounds/345080/ | metrostock99, FX METAL CLANKING ON CONCRETE 501.wav | https://cdn.freesound.org/previews/345/345080_1256155-hq.mp3 | step / land / slam |
| D | https://freesound.org/people/jasonLON/sounds/125404/ | jasonLON, Metallic Clang.aif (layered pan hits) | https://cdn.freesound.org/previews/125/125404_2279655-hq.mp3 | block_hit ring |
| E | https://freesound.org/people/editor_adp/sounds/198114/ | editor_adp, Clang 3 (railing, added reverb); licence seen only in a search excerpt, re-check the page | (not fetched) | clamp ring |
| F | https://freesound.org/people/brunoboselli/sounds/457294/ | brunoboselli, Air (or steam) pressure release | https://cdn.freesound.org/previews/457/457294_300738-hq.mp3 | exit vent, steam |
| G | https://freesound.org/people/qubodup/sounds/871228/ | qubodup, Short Machine Sound 4 (CC0 mix of kyles + zaphod1984, both stated CC0: check the parents) | https://cdn.freesound.org/previews/871/871228_71257-hq.mp3 | hydraulic ram |
Rejected while searching: mokasza 810747 (AI-generated, CC BY 4.0), jesabat 119741 (CC BY 3.0), Artninja 705134 /
791339 (credit requested, mixed CC BY-NC parents).

## Fix spec item 3 — round 3 (2026-10-30): done

All 56 files (31 events, same file and event names, same lengths and contact times, so no Java and no `sounds.json`
change) are now mixed by `tools/sfx/hulkbuster.py` from 28 CC0 1.0 Freesound recordings, with the H6 synthesis as a
support layer (by loudest 100 ms RMS 3–10 dB under the recordings, usually low-passed to its sub weight; equal or
above only in the energy cues noted below). Source table, per-file use and loudness: `tools/assets/ironman_sources.md`.

- Openverse was still unreachable, so sources were found on Freesound search pages (CC0 filter), each licence checked on
  its landing page, parents of derived works checked (qubodup 871228 → kyles 637811 + zaphod1984 186731; newagesoup
  337835 → EpicWizard 264889), AI-generated and non-CC0 takes rejected. Credits were composed from the landing page and
  say so; the hq MP3 previews are the audio. Packaging uses the helper's exact ffmpeg settings inside the script
  because the helper's `package` needs an Openverse `--source-id`.
- Candidate E (editor_adp 198114) was not used. Candidates A, B, C, D, F, G are used.
- Thrusters / jet: TiesWijnen 413312 (turbojet afterburner), surrey_film 162242 (Eurofighter afterburner ignition),
  AslakHostaker 395038 (flamethrower bursts), SyntaxSeed 244230 (gas torch, loop bed). Servo: derjuli 824121 (servo
  whir, loops and spools), Burningmonkey 322021 (servo sweeps, wind-ups). Hydraulic / air: qubodup 871228, reg7783
  131302, brunoboselli 457294.

| Family | Events | Recording body (Freesound ids) | Synthesis support |
|---|---|---|---|
| steel clang | clamp ×4, block_hit ×3, exit, assemble lock | 842171, 125404, 386129, 270589, 406197, 154447 latch | hydraulic push, latch, sub (≤300 Hz) |
| metal impact | punch ×3, hammer ×3, jackhammer ×3, finish_punch, charged_punch | 620077, 48980, 406197, 386129, 554012, 270589, 504626 crunch, 703247 debris | sub bass (≤250–300 Hz); jackhammer full band −10 dB for the pistons |
| hydraulic / air | step hiss, exit vent, grab, throw release, land | 131302, 871228, 457294 | hinge stick-slip, clanks |
| heavy ground thud | step ×4, land ×2, slam ×2, drop, break, ult_impact | 640204, 815039, 504626, 703247, 560510 | sub thud (≤150–220 Hz) |
| thrusters / jet | dash ×2, cough ×2, hop, charged_punch push, charge, ult_ignite, thruster_loop | 413312, 395038, 162242, 244230 | jet roar −6…−10 dB |
| servo | swing ×3, wind-ups, grab, servo_loop, charge_loop | 322021, 824121 | servo whine, hum |
| scrape / screech | screech ×5, dash skid, block_hit, break tear | 530075, 337835 | stick-slip −8 dB |
| energy (synthesis-led) | repulsor_charge, repulsor_beam, assemble, refuse, drop whistle, spark_loop zaps | 162242 / 824121 spool, 413312 blast, 386129 lock, 154447, 396240 sparks | the repulsor / reactor / whistle tone itself (support at −4…+4 dB) |

Loops: `servo_loop`, `thruster_loop`, `charge_loop` are spectral freezes over exactly 2 / 3 / 1 s (seeded phase, no
fades, modulation periodic in the loop); `spark_loop` folds 0.25 s of the welding recording onto its head (equal-power)
so the crackle stays, then is peak-limited circularly. Seam score (edge 2nd difference / RMS 2nd difference, ~1 = no
click) on the decoded files: 0.46 / 1.74 / 1.78 / 1.02.

Loudness (100 ms K-weighted momentary max, decoded committed files): step −12.0…−12.1, screech −14.1…−14.3,
swing −13.0…−13.1, impact −8.7…−8.9, heavy −7.9…−8.0, body −11.1…−11.3, riser −14.1…−14.3, ui −18.2, loop −18.1…−18.3
LUFS; true peak ≤ −1.0 dBTP (one-shots), ≤ −8.3 dBTP (loops). Versus the H6 synthesis kit: impact about the same
(−8.5 before), heavy about 1.8 dB louder (−9.8 before; the class target is −6.5 and now limits at −8), loops 1.6 dB
louder (−19.8 before), body 0.3 dB louder (−11.5 before).

**Not auditioned**: no audio output in the sandbox. Contact alignment was checked numerically (steepest envelope rise at
the file's contact time ± 5 ms for punch_1, hammer, jackhammer, finish_punch, clamp ×4, step, grab, block_hit, land,
slam, assemble, break, charge, swing), and spectrograms of six files were looked at; neither is a listening test. The
in-game balance (loudness per class against the Java volumes, the synthesis/recording ratio, and whether the
pitched-down previews read as a 3.4-block machine) still needs the owner's listening pass.
