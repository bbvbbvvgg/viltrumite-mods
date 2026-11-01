# Godzilla — City Catastrophe asset sources

Every asset of the event is **own work** (CC0-1.0), generated deterministically by scripts in this repository. No
external samples, models, textures, recordings, film or game assets were downloaded, traced, sampled or converted,
and the event uses no vanilla Minecraft sound, particle texture or model. Raw intermediate files are never committed.
Godzilla is a character of Toho Co., Ltd.; this is a non-commercial fan implementation with an original design.

## Model, textures, animations

| Committed files | Generator | Notes |
|---|---|---|
| `geo/godzilla/godzilla.geo.json` (LOD0, binassets `.partN`), `geo/godzilla/godzilla_lod1.geo.json` (binassets `.partN`), `geo/godzilla/godzilla_lod2.geo.json` (resources) | `tools/assets/godzilla.py` (`godzilla_lib.py`, `godzilla_geo.py`) | Original kaiju design built in code from lofted cross-sections and boxes: pillar legs with toes and claws, heavy thighs, barrel chest, long neck, head with brow ridge, jaw, teeth, gills and eye cubes (bone `eyes`), 7-joint tail (`tail_0`-`tail_6`); three rows of dorsal plates (`plate_SSSS_<c\|l\|r>idx`) sized along the spine. LOD0 70 bones / 428 cubes, LOD1 231 cubes, LOD2 54 bones / 49 cubes (shadow and far view). |
| `textures/entity/godzilla/godzilla.png` (b64 parts), `textures/entity/godzilla/godzilla_glow.png` | `tools/assets/godzilla.py` | Swatch atlas: charcoal hide with scale noise, darker plates and claws, pale teeth; the glow map lights only plates, mouth and eyes (drawn additively with a per-bone tint by `GodzillaRenderer`). |
| `animations/godzilla/godzilla.animation.json` | `tools/assets/godzilla_clips.py` | 18 clips: idle, walk, roar, breath_charge, breath_fire (loop), breath_end, claw_r, claw_l, tail_strike, tail_strike_m, stomp, stomp_m, body_slam, hurt, fall, down (loop), rise, death. Contact poses sit on the `GodzillaMove` contact ticks. Gait phase, tail inertia, hip twist and head look are layered in code. |

Paths are relative to `viltrumitecore/src/main/resources/assets/viltrumitecore/` and
`viltrumitecore/src/main/binassets/assets/viltrumitecore/`. The atomic beam, the mouth charge, plate bloom, hot
ground glow, sparks, smoke, dust and embers are drawn in code (`client/godzilla/GodzillaRenderer`, `GodzillaClient`,
`GlowDraw`) and use the event's own particle classes — no guardian beam, no vanilla particle.

## Sounds

All 22 events are original procedural synthesis by `tools/sfx/godzilla.py` (seeded numpy noise and oscillators,
stick-slip "bowed" voice, inharmonic partials, STFT formant masks, synthetic convolution room; primitives shared with
`tools/sfx/chitauri.py`; CC0-1.0), mono Vorbis (bitexact; far and sub-heavy cues at 22.05 / 32 kHz), packed as
`.ogg.b64` (or `.ogg.b64.partN` when over 56 KB) in `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/godzilla/`.
Loops are built periodic (seamless). `python3 tools/sfx/godzilla.py --check` re-synthesises and compares the decoded
PCM. Events: `viltrumitecore/src/main/sound-overlays/godzilla.json` (category hostile); registry
`worldevent/godzilla/GodzillaSounds`; playback `client/godzilla/GodzillaAudio` (own distance curve, speed-of-sound
delay, impact rate limit); subtitles in all 12 locales (`src/main/lang-overlays/godzilla/`). `GodzillaAssetsTest`
keeps registry, overlay, files, subtitles and this sheet in step.

| Event | File | Content | Trigger |
|---|---|---|---|
| `godzilla.roar` | `roar` | Signature roar (~6.8 s): throat intake, the long rising bowed screech, then the falling bellow; city room. | move ROAR at +16 t (also the phase changes that roar); near layer, 0-420 blocks |
| `godzilla.roar_far` | `roar_far` | The same roar across the city (~7.6 s): no top, the bellow carries, long late echoes off the towers. | with every roar, 140-1100 blocks (far curve); also under HURT at half level |
| `godzilla.growl` | `growl` | Short threat growl (~2.4 s): throat and fry swelling, a faint screech overtone, a snort of breath. | SLAM at +4 t; random idle growls every 13-30 s while it is near |
| `godzilla.step` | `step` | A footfall up close (~2 s): the crushing slam, the ground thud and the debris pattering back. | FX STEP packet (each footfall, synced to the gait), 0-220 blocks |
| `godzilla.step_far` | `step_far` | A footfall far away (~2.6 s): a felt sub boom rolling in, the city rattling faintly. | FX STEP/STOMP/FALL packets, 90-1000 blocks (felt boom) |
| `godzilla.stomp` | `stomp` | The deliberate stomp (~3.2 s): a bigger slam, the shockwave crack running out, air pushed away, debris. | FX STOMP packet at the STOMP contact tick |
| `godzilla.tail_swish` | `tail_swish` | The tail swinging through the air (~1.9 s): a slow, enormous whoosh with a low pressure wave. | move TAIL at +20 t |
| `godzilla.tail_hit` | `tail_hit` | The tail lands on a building (~2.9 s): boom, the concrete cracking, a crunch cascade, glass and rubble. | FX TAIL_HIT packet (tail contact on a building or a hero), rate limited |
| `godzilla.claw_swish` | `claw_swish` | A claw swipe (~1.2 s): a faster, brighter whoosh. | moves CLAW_R / CLAW_L at +15 t |
| `godzilla.claw_hit` | `claw_hit` | Claws through a facade (~2.3 s): the hit, steel tearing with a falling screech, concrete crumbling. | FX CLAW_HIT packet at the claw contact, rate limited |
| `godzilla.charge` | `charge` | Breath charge (4.2 s): each plate igniting with a zap from tail to head, the hum rising, the throat crackling. | move CHARGE at CHARGE_BRACE (16 t), lasts exactly to the release (tick 100) |
| `godzilla.beam_start` | `beam_start` | Breath release (~2.6 s): the blast leaving the mouth, an energy tear sweeping down, the beam roar blooming. | move FIRE starts (beam release) |
| `godzilla.beam_loop` | `beam_loop` | The beam (3 s, seamless): a dense roaring energy core, an electric buzz, a shimmering whine, sizzle. | loop at the mouth while the beam is active (fades in 4 t, out 8 t) |
| `godzilla.beam_impact` | `beam_impact` | Where the beam hits (3 s, seamless): roaring fire, melting stone hissing, pops, deep stone cracks. | loop at the beam end point while it hits something |
| `godzilla.beam_end` | `beam_end` | The beam dying (~2.8 s): a falling whine, the energy sputtering out, the plates ticking as they cool. | move BREATH_END |
| `godzilla.smash` | `smash` | The body ploughing through a building (~3.6 s): impact, fracture, glass, steel groan, a long rubble cascade. | FX SMASH packet (body or limb through a building), volume by size, rate limited |
| `godzilla.hurt` | `hurt` | A heavy hit lands (~2.6 s): a short, high, ragged bellow that cracks and falls. | move HURT at +2 t; FALL at 0 |
| `godzilla.fall` | `fall` | The body hitting the ground (~4.4 s): two huge impacts, the ground roar, a crack run and debris. | FX FALL packet (body hits the ground; also the death fall) |
| `godzilla.rise` | `rise` | Getting up (~3.6 s): a strained, swelling growl, debris sliding off the back, the planted foot at 1.9 s. | move RISE at +8 t (the planted foot lands on the RISE contact) |
| `godzilla.death` | `death` | The last roar (~8 s): rises, cracks into a rattling subharmonic, falls away into a long, low exhale. | move DEATH at +4 t, heard up to 700 blocks (at least 25 % everywhere in the event) |
| `godzilla.arrive` | `arrive` | The arrival (~8 s): the ground trembling closer, the city rattling, then a far roar rolling over the towers. | FX ARRIVE packet when the event starts (everyone in the event range) |
| `godzilla.retreat` | `retreat` | The final retreat (~7.4 s): a long, lower, mournful call as it turns away, half lost to the city. | FX RETREAT packet when the retreat ends |

Levels: loudest 100 ms RMS per role (`LOUDNESS` in the script), soft-limited under -1.5 dBFS. Checked by numbers and
spectrograms only (pitch track of the roar, loop seams, peak and loudness); **not auditioned by ear** — the final mix
needs a listening pass.
