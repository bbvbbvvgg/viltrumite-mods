# Red Hulk — sources and licenses

Everything shipped for the Red Hulk is **own work**, released as **CC0-1.0**. No third-party assets, no recordings,
no film / game / mod audio or textures, no vanilla Minecraft sounds, no synthesised voice (no TTS). His spoken lines
are text subtitles only (`RedHulkLines`, 12 locales); every vocal sound is a non-verbal beast growl.

| Asset | Generator | Method |
|---|---|---|
| Body model `geo/red_hulk/red_hulk.geo.json` (22 bones, 119 cubes, 256×512) | `tools/assets/red_hulk_model.py` | Built from scratch; shares no geometry with the Avengers Hulk |
| First-person arms `geo/red_hulk/red_hulk_arms.geo.json` (10 bones, 34 cubes) | `tools/assets/red_hulk_model.py` | Same muscle layout as the body arms |
| Textures `red_hulk.png`, `red_hulk_eyes.png`, `red_hulk_heat.png`, `red_hulk_skin.png` | `tools/assets/red_hulk_model.py` (`pixel_shade.py`) | Procedural shaded pixel art: baked occlusion, form light, bevels, ordered dither |
| Clips `animations/red_hulk/*.animation.json` (stances, combat, skills, ult, face, fp) | `tools/assets/red_hulk_clips.py` | Hand-keyed poses in code, timings = `RedHulkMove` |
| Ability icons `textures/gui/ability/red_hulk/*.png`, hero badge and HUD emblem | `tools/assets/red_hulk_icons.py` | Drawn on a pixel grid in code |
| Strings `lang-overlays/red_hulk/*.json` | `tools/assets/red_hulk_lang.py` | Own texts |
| 28 sounds `sounds/red_hulk/*.ogg` | `tools/sfx/red_hulk.py --package` | Deterministic procedural synthesis (numpy, seeded RNG), ffmpeg libvorbis mono 44.1 kHz |

## Sound recipes (`tools/sfx/red_hulk.py`)

Shared primitives: `tools/sfx/jinwoo.py` and `tools/sfx/captain.py` (own synthesis). Heat palette: sub-bass impacts,
fire crackle, furnace roar (brown noise in a low flickering band), steam hiss, molten sizzle on hot hits; growls are a
sawtooth buzz through moving formant bands plus breath noise — never words.

| Event | File | Recipe |
|---|---|---|
| `red_hulk.swing` | `swing` | pink whoosh 220→1400 Hz + brown-noise air (0.45 s) |
| `red_hulk.swing_heavy` | `swing_heavy` | brown whoosh 140→900 Hz + high whoosh + fire crackle, short reverb (0.7 s) |
| `red_hulk.punch` | `punch` | punch core (thud + click + sub) with a molten sizzle, reverb (0.6 s) |
| `red_hulk.punch_heavy` | `punch_heavy` | deeper punch core + 70→30 Hz sub + crackle, longer reverb (0.9 s) |
| `red_hulk.hammer` | `hammer` | ground boom 80→24 Hz + sub + crackle + sizzle, reverb (1.6 s) |
| `red_hulk.grab` | `grab` | fleshy thud 160→70 Hz + pink-noise slap (0.5 s) |
| `red_hulk.squeeze` | `squeeze` | pulsing brown-noise strain (5 Hz) + crackle + sizzle (0.9 s) |
| `red_hulk.throw` | `throw` | pink whoosh + low non-verbal heave growl (formants "a-o") (0.8 s) |
| `red_hulk.throw_impact` | `throw_impact` | body impact boom + sub, reverb (1.1 s) |
| `red_hulk.clap` | `clap` | broadband crack + thud + boom + furnace burst + crackle, long reverb (1.6 s) |
| `red_hulk.seismic` | `seismic` | boom 70→20 Hz + sub 48→18 Hz + rolling brown rumble + stone crackle (2.2 s) |
| `red_hulk.leap_launch` | `leap_launch` | boom + rising brown whoosh (1.0 s) |
| `red_hulk.leap_land` | `leap_land` | huge boom 75→18 Hz + sub + crackle + furnace puff, long reverb (2.2 s) |
| `red_hulk.blast_charge` | `blast_charge` | swelling furnace + 55/82.5 Hz drone + rising noise sweep (0.75 s) |
| `red_hulk.blast_fire` | `blast_fire` | flame-jet furnace with 10 Hz flutter + drone + crackle (1.1 s) |
| `red_hulk.fury_roar` | `fury_roar` | long non-verbal formant roar ("u-a-a-a-o", 58→92 Hz) + furnace + sub (2.2 s) |
| `red_hulk.overheat` | `overheat` | steam hiss 1.8→7 kHz + short boom + crackle + falling metallic groan (1.8 s) |
| `red_hulk.vent` | `vent` | big steam hiss + brown-noise exhale (1.8 s) |
| `red_hulk.ult_charge` | `ult_charge` | 3 s rising furnace + 41/61.5/82 Hz drone + noise sweep + crackle + accelerating heartbeat thuds |
| `red_hulk.ult_release` | `ult_release` | detonation boom 60→14 Hz + sub + fire wall + crackle + late hiss, long reverb (3.2 s) |
| `red_hulk.ult_recover` | `ult_recover` | falling cooling hiss + three panting breaths + fading crackle (2.5 s) |
| `red_hulk.landing` | `landing` | heavy body thud + brown-noise dust (0.9 s) |
| `red_hulk.footstep` | `footstep` | soft low thump + filtered click (0.4 s) |
| `red_hulk.heat_up` | `heat_up` | furnace swell + rising 90→150 Hz tone + crackle (0.9 s, tier up) |
| `red_hulk.heat_hum` | `heat_hum` | seamless 2 s loop: 45/90/135/180 Hz drone with 1.5 Hz pulse + periodic low noise (client loop that follows the heat) |
| `red_hulk.refuse` | `refuse` | short hiss + low thud (0.5 s, ability refused) |
| `red_hulk.enter` | `enter` | transformation: non-verbal growl rising into a roar ("u-a-a-o") + boom + crackle (2.4 s) |
| `red_hulk.hurt` | `hurt` | short pained non-verbal growl ("a-o") + thud (0.6 s) |

Checked by numbers and spectrograms; **not auditioned by ear** — the in-game mix needs a listening pass.
Red Hulk (Thaddeus "Thunderbolt" Ross) is a Marvel character; this is a non-commercial fan implementation.
