# THANOS 2.0 (the war for Atheria) — asset sources

Every file of the war is own work released under CC0-1.0: modelled, drawn, animated or synthesised by the
scripts below (MCU-inspired look, nothing downloaded, traced, sampled or recorded; no vanilla sound and no
vanilla texture stands in). Thanos' own model, clips and the stone sounds are in `thanos_sources.md`.

| Committed file | Source | How |
|---|---|---|
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/thanos/ebony_maw.geo.json` | `tools/assets/black_order_model.py` | 20 bones / 39 cubes, 34 px tall (2.1 blocks): thin long-limbed alien, swept-back skull, floor-length coat with a stiff collar, long fingers; floats |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/thanos/cull_obsidian.geo.json` | `tools/assets/black_order_model.py` | 20 bones / 53 cubes, 53 px tall (3.3 blocks): hunched giant, bony crest and horns, banded bracers, hammer-axe (`weapon` > `weapon_head`, the head flies on its chain) |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/thanos/{ebony_maw,cull_obsidian}{,_glow}.png.b64` | `tools/assets/black_order_model.py` | procedural pixel-art atlases (Maw 256×256, Cull 256×512) and additive glow layers (eyes, Cull's weapon edge) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/animations/black_order/{ebony_maw,cull_obsidian}{,_moves}.animation.json` | `tools/assets/black_order_clips.py` | 26 keyframed clips: idle, glide/walk, hurt (additive), stagger, beam_in, beam_out, death and every `MawMove` / `CullMove` (telegraph + active + recovery ticks, unscaled) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/animations/thanos/thanos_war{,_stones,_combos}.animation.json` | `tools/assets/thanos_clips.py` | Thanos' 11 new clips: ship_call, power_rupture, space_barrage, reality_rewrite, command, time_anomaly, mind_compulsion, soul_harvest, combo_time_volley, combo_moon_fall, combo_infinity |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/shaders/core/sanctuary_hull.{json,vsh,fsh}` | hand-written | hull shader of the Sanctuary II: daylight tint, aerial haze toward the fog colour, the glow of its guns and of the burning city on the belly |
| Sanctuary II mesh | `client/thanos/war/SanctuaryMesh.java` | procedural in code (no asset file), built once into vertex buffers at three levels of detail (about 10k / 1.5k / 400 quads) with baked light and separate emissive lights |
| `viltrumitecore/src/main/lang-overlays/thanos_war/<locale>.json` (12 locales) | `tools/assets/make_thanos_war_lang.py` | every war string: entity names, speaker names, voice lines, HUD, the absorb notice, sound subtitles |

## Sounds

All 40 files are own synthesis by `tools/sfx/thanos_war.py` (numpy, CC0-1.0), mono 44.1 kHz Vorbis stored
as base64 in core binassets (`sounds/thanos_war/<name>.ogg.b64`, or `.ogg.b64.part0..N` past 56000 bytes).
The stone timbres are imported from `tools/sfx/thanos.py`, so a war move still sounds like its stone.
Loudness is matched per role; `python3 tools/sfx/thanos_war.py --check` proves the committed files and the
overlay `viltrumitecore/src/main/sound-overlays/thanos_war.json` are what the script makes. The two loops
(`ship_hum`, `siren`) are rendered circularly and stream.

| Cue | Trigger |
|---|---|
| `ship_arrival` | the Sanctuary II starts its glide out of the clouds (`SanctuaryShip`, heard by the whole city) |
| `ship_hum` | client loop while the ship is in the sky, louder the closer the camera (`ThanosWarClient`) |
| `ship_horn` | the ship reaches its hover point; again, lower, when Phase IV starts |
| `ship_charge` | an orbital strike is marked on the ground (the warning ring), at the marked spot |
| `ship_lance` | the lance lands |
| `ship_beam` | a beam from the hull to the street (the Black Order is beamed down) |
| `ship_depart` | the ship turns and climbs away after a victory |
| `portal_open` | a Chitauri landing portal opens (street or sky) |
| `siren` | client loop while the city alarm is up (Phase III onwards) |
| `command` | Thanos' COMMAND move (calls the Black Order, the army, the bombardment) |
| `absorb` | the gauntlet absorbs a hero's energy attack |
| `rupture` | POWER_RUPTURE erupts |
| `barrage` | SPACE_BARRAGE gates fire |
| `rewrite` | REALITY_REWRITE marks its zone |
| `anomaly` | TIME_ANOMALY bubble forms |
| `compulsion` | MIND_COMPULSION takes a hero |
| `harvest` | SOUL_HARVEST starts |
| `time_stop` | COMBO_TIME_VOLLEY stops time around him |
| `volley` | time resumes and the gates fire |
| `rock_impact` | a COMBO_MOON_FALL shard lands |
| `infinity_stage` | each of the six COMBO_INFINITY stages (pitched up per stage) |
| `maw_voice` | Ebony Maw arrives |
| `maw_lift`, `maw_throw` | Maw lifts debris (volley, topple) / throws it |
| `maw_grab`, `maw_barrier`, `maw_catch` | telekinetic grip / debris shield / a projectile stopped mid-air |
| `maw_hurt`, `maw_death` | Maw hurt / killed |
| `cull_roar`, `cull_swing`, `cull_slam` | Cull's roar (arrival, Hulkbuster, phase), weapon swing, ground slam |
| `cull_charge`, `cull_wall` | the charge starts / a wall breaks in front of him |
| `cull_chain`, `cull_grab` | the chain-blade flies / he grabs a hero |
| `cull_hurt`, `cull_death`, `cull_step` | Cull hurt / killed / his footsteps |
| `debris_impact` | a thrown or dropped piece of debris lands |
