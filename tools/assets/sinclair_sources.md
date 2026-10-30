# Sinclair asset sources

Every Sinclair model, texture, clip and icon is **own work** made by the generator scripts below (Notion AI for
grebeshok105, 2026-10-28). Since sound pass 2 (2026-11-02) the sounds also layer Kenney CC0 1.0 recordings
(see Sounds). No vanilla sound/particle is used. Licence of the generated files: CC0-1.0 (sounds) /
repository licence (art). Re-run the script to rebuild.

## Models, textures, clips, icons

| Committed file | Source | Script | Notes |
|---|---|---|---|
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair/sinclair.geo.json` | own work | `tools/assets/make_sinclair_model.py` (+ `sinclair_geo.py`) | 32 px mannequin: body, head, hat, lapels, vest hem, medallion, two 2-segment coat tails, arm → forearm → hand → thumb/index/middle/ring (2 segments each), knees, `wand` + `wand_tip`, `card`, `held_hat` |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair/sinclair_arms.geo.json` | own work | same | first-person arms (same bone names, arms top-level) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair/savannah.geo.json` | own work | same | ghost assistant: hair chain, gloves, three dress tiers, three-bone smoke trail |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair/rabbit.geo.json` | own work | same | stage rabbit with two-segment ears and a tiny top hat |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair/{top_hat,chest,sword,card,curtain}.geo.json` | own work | same | act props |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/sinclair/{sinclair,savannah,rabbit,props}[_glow].png.b64` | own work | same (+ `sinclair_paint.py`) | procedural 16×16 material swatches (grain, weave, satin, velvet, gold…) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/animations/sinclair/*.animation.json` | own work | `tools/assets/make_sinclair_clips.py` | body, first-person, Savannah, rabbit and prop clips (split into files under 64 KB) |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/ability/sinclair/*.png.b64` | own work | `tools/assets/make_sinclair_icons.py` | five 64×64 ability icons |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/sinclair/{hero,emblem,act_card}.png.b64` | own work | same | hero badge 32×32, panel emblem 16×16, act card 16×16 |

## Sounds

Sound pass 2 (2026-11-02, design `docs/design/2026-11-02-sinclair-cinematic-polish.md` §4): all 45 events are
built by `tools/sfx/sinclair_cc0.py KENNEY_ROOT` — organic layers from four Kenney CC0 1.0 packs (kenney.nl:
**Casino Audio**, **Impact Sounds**, **Interface Sounds**, **RPG Audio**; public domain, credit given in
`CREDITS.md`) plus own numpy synthesis (the primitives of `tools/sfx/sinclair.py`, CC0-1.0) for the magic
(shimmer grains, pads, risers, the beam body, humanised drumroll and applause). No vanilla Minecraft sound.
Mono 44.1 kHz, Ogg Vorbis q5, committed as `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/<event>.ogg.b64`.

Mastering (every event): 35 Hz high-pass, trailing silence trimmed at −54 dB, raised-cosine release over the
last 18 % (80–350 ms), loudness matched per class on the active-part RMS — frequent −21 dB, detail −20,
ability −18, act −16.5, signature −15 — and a soft limiter with a −1 dBFS ceiling. `--audit` prints the
committed files' duration, peak, RMS and tail level. Pass-1 audit (31 files): 14 hard cut-offs, 15 bare
sine-tone sounds, an 11 dB RMS spread; pass 2: no tail above −30 dB, RMS −20.9…−15.2 dB by class.
Checked by numbers and spectrograms only — **not auditioned by ear**; the in-game mix still needs a listening pass.

Status: *replaced* = new recording-based recipe; *reworked* = pass-1 synthesis kept with new tails/room/loudness;
*new* = new event (copy cinematic, longer acts).

| Event `viltrumitecore:sinclair.*` | Subtitle (en_us) | Status | Class | Kenney CC0 layers | Sync / notes |
|---|---|---|---|---|---|
| `wand_shot` | Wand fires | replaced | frequent | Interface `glass_002` |  |
| `spark_hit` | Spark hits | replaced | frequent | Impact `impactGlass_light_001` |  |
| `wand_reload` | Wand twirls | replaced | detail | RPG `metalClick` |  |
| `bolt_cast` | Vexing Bolt flicked | replaced | ability | Casino `card-slide-3` |  |
| `bolt_snap` | Bolt redirected | replaced | ability | Interface `switch_002` |  |
| `bolt_hit` | Vexing Bolt bursts | replaced | ability | Impact `impactGlass_medium_002` |  |
| `assistant_appear` | Savannah appears | replaced | ability | RPG `cloth2` |  |
| `swap` | Places swapped | replaced | ability | RPG `cloth1` |  |
| `assistant_leave` | Savannah takes her leave | replaced | detail | RPG `cloth3` |  |
| `hex_cast` | Hat flourish | replaced | ability | RPG `cloth4` |  |
| `hex_poof` | Turned into a rabbit | replaced | ability | Impact `impactSoft_medium_002` |  |
| `hex_end` | Rabbit hex wears off | replaced | detail | Impact `impactSoft_medium_000` |  |
| `snap` | Sinclair snaps and vanishes | reworked | ability | — |  |
| `decoy_reveal` | Decoy vanishes | replaced | ability | RPG `cloth1` |  |
| `tada` | Ta-da! | reworked | act | — |  |
| `refuse` | No volunteer | replaced | detail | Impact `impactWood_light_002` | Muted 'wah-wah': two falling brass notes through a closing filter, then a soft knock. |
| `ovation` | Ovation | reworked | act | — |  |
| `ult_capture` | Spotlight on a volunteer | replaced | signature | Impact `impactMetal_heavy_001`, RPG `metalLatch` | Follow-spot switched on: heavy latch, warm lamp hum (filtered, not a sine), shimmer rising into the light. |
| `ult_card` | A card flies out | replaced | act | Casino `card-slide-5` |  |
| `act_drumroll` | Drumroll | reworked | act | — | ActScene.begin (t = 0): a 1.4 s roll that swells into each act's main beat and dies away under it |
| `act_footlights` | Footlights flare up | new | detail | Impact `impactMetal_light_00N`, Interface `switch_00N` | BEAM t = 2: five footlights clunk on, converging (t 2..18), lamp hum swells until the ray (t 28) |
| `act_beam` | Footlight beam | replaced | act | Impact `impactGlass_light_00N` | BEAM t = 28..70 (2.1 s): the ray with an accent on each damage pulse (every 5 ticks = 0.25 s, 9 pulses) |
| `act_beam_charge` | Beam charges | new | act | — | BEAM t = 70..78 (0.4 s): the card is drawn back and the light gathers into it |
| `act_beam_burst` | Beam overdrive | new | signature | Impact `impactGlass_heavy_002` | BEAM t = 78: overdrive flash down the beam (shove), 1.8 s of falling glitter |
| `act_hat_spin` | Hat spins | new | detail | RPG `cloth1` | NOVA t = 3..20 (0.85 s): the top hat spun on a fingertip, faster and faster, flicked off at t = 20 |
| `act_nova_whirl` | Hat whirls | replaced | act | RPG `cloth4` | NOVA t = 20 (throw) and t = 38 (second whirl over the target) |
| `act_nova_burst` | Hat bursts into cards | replaced | signature | Casino `card-fan-2`, Casino `card-shuffle` | NOVA t = 52: the hat bursts into cards (damage + knock-back) |
| `act_cut_saw` | Sawing trick | replaced | act | Casino `card-place-N`, RPG `drawKnife3` | CUT t = 14: the line of standing cards is laid out (t 14..24), then the blade sings once |
| `act_saw_rasp` | Saw rasps | new | act | Impact `impactWood_light_001`, RPG `knifeSlice` | CUT t = 30 and 38: one saw stroke along the line (1 damage) |
| `act_cut_slash` | Grand cut | new | signature | RPG `drawKnife1`, Impact `impactWood_heavy_001` | CUT t = 50: the grand cut along the whole line (6 damage) |
| `card_hit` | Card strikes | replaced | frequent | Casino `card-place-2` |  |
| `act_card_fan` | Cards fan out | new | detail | Casino `card-fan-1` | BARRAGE t = 1 (fan of twelve opens) and t = 42 (pitch 1.25, the last three gather overhead) |
| `act_card_volley` | Card volley | replaced | act | Casino `card-shove-N` | BARRAGE t = 20 / 28 / 36: three cards thrown |
| `act_card_finale` | Finale volley | new | signature | Casino `card-fan-2`, Casino `card-place-4`, Casino `card-shuffle`, Impact `impactPunch_medium_002` | BARRAGE t = 50: the finale volley thrown down from above the head |
| `act_curtain` | Curtain falls | replaced | act | Casino `chips-collide-N`, RPG `cloth3`, Impact `impactSoft_heavy_001` | AEGIS t = 0: the velvet curtain falls around him |
| `act_curtain_rustle` | Curtain rustles | new | detail | RPG `cloth2`, RPG `cloth4` | AEGIS t = 14 and 30: the curtain stirs while he turns inside |
| `act_curtain_open` | Curtain flies open | replaced | signature | RPG `cloth4` | AEGIS t = 50 (blast) and FINALE t = 58 (pitch 1.2, the chest flies open) |
| `act_chest` | Magic chest lands | replaced | act | RPG `creak1`, Impact `impactPlank_medium_002`, Impact `impactWood_heavy_003`, RPG `metalLatch` | FINALE t = 24: the magic chest drops and lands |
| `act_sword` | Sword through the chest | replaced | act | RPG `drawKnife2`, Impact `impactWood_medium_002` | FINALE t = 30 / 38 / 46 (pitch steps up): a sword is pushed through the chest |
| `act_chest_rattle` | Chest rattles | new | detail | Impact `impactWood_light_00N`, RPG `metalPot2` | FINALE t = 52..58 (0.3 s): the chest trembles just before the reveal |
| `act_bow` | Sinclair takes a bow | reworked | act | — |  |
| `copy_sting` | Magic sting: the act is copied | new | signature | Impact `impactBell_heavy_001` | COPY t = 0: the world stops - a bell-like card sting over a low 'time halt' |
| `copy_unfold` | Card unfolds | new | detail | RPG `bookFlip2`, Casino `cards-pack-open-1` | COPY t = 8..18: the card unfolds towards the camera |
| `copy_fold` | Card folds away | new | detail | RPG `bookClose`, Casino `card-slide-7` | COPY t = 44..52: the card folds and flies to its owner |
| `copy_return` | Time flows again | new | detail | — | COPY t = 52..60: time flows again (reverse air rising, a soft release) |
