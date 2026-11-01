# Thanos event — asset sources

Every Thanos file is own work released under CC0-1.0 (drawn, modelled, animated or synthesised by the
scripts below; nothing was downloaded, traced or recorded). The one derivative is Od Lagna's scene skin,
a recolour of Reinhard's skin from the user archive (see the last table row).

| Committed file | Source | How |
|---|---|---|
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/thanos/thanos.geo.json` | `tools/assets/thanos_model.py` | 38 bones / 101 cubes, 60 px tall (3.75 blocks): MCU-style Thanos, left Infinity Gauntlet with two-segment fingers and thumb, six stone bones (`stone_time` on the thumb, `stone_space` index, `stone_reality` middle, `stone_power` ring, `stone_soul` little finger, `stone_mind` back of the hand), each a socket + two crossed gem cubes + two glow-only halo shells |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/thanos/thanos.png.b64` | `tools/assets/thanos_model.py` | procedural pixel-art atlas 256×512 (2 texels per model px), stored as an indexed PNG (256 colours, alpha kept) |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/thanos/thanos_glow.png.b64` | `tools/assets/thanos_model.py` | additive glow layer: irises and the six gems/halos (premultiplied, black = no glow) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/animations/thanos/thanos_{base,melee,stones,combos,scene}.animation.json` | `tools/assets/thanos_clips.py` | 34 keyframed clips (idle, walk, turns, every move, hurt, stun, intro, victory, snap, snap_hold, defeat); move clips use the base telegraph/active/recovery ticks of `ThanosMove`, scene clips the beats of `ThanosSceneTimeline` |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/thanos/{od_lagna_scene,od_lagna_scene_glow}.png.b64` | `tools/assets/make_thanos_od_lagna.py` from `textures/entity/reinhard/{reinhard,reinhard_glow}.png.b64` (grebeshok105, user archive `reinhard_assets.zip`, see `reinhard_sources.md`) | luminance remap onto a white-gold ramp (hair pale gold, eyes amber), soft self-light glow; same 128×128 UV layout, so the Reinhard geo is reused unchanged; Reinhard's own textures are untouched |

Sounds: see the «Sounds» section below.

| Committed file | Source | How |
|---|---|---|
| `viltrumitecore/src/main/resources/assets/viltrumitecore/animations/thanos/thanos_od_lagna.animation.json` | `tools/assets/make_thanos_od_lagna.py clips` | 9 clips on the reused Reinhard rig: `od_lagna_form` (2 s), `od_lagna_idle` (4 s loop), `od_lagna_line_1..6` (5 s each, one gesture per line), `od_lagna_restore` (6 s) |
| `viltrumitecore/src/main/lang-overlays/thanos/<locale>.json` (12 locales) | `tools/assets/make_thanos_lang.py` | every Thanos string (entity, chat, commands, HUD move names and hints, scene lines, subtitles), merged into the locale files at build |

## Sounds

All 48 files are own synthesis by `tools/sfx/thanos.py` (numpy, no samples, no vanilla sound stands in;
CC0-1.0), mono 44.1 kHz Vorbis stored as base64 in core binassets (`.ogg.b64`, or `.ogg.b64.part0..N` for
the long loops). Each stone has its own timbre (power: distorted growl and arcs; space: glass and flanged
air; reality: ring-modulated saws; time: ticks, bells, reversed tape; mind: beating high sines; soul:
formant choir), reused by the combos that mix it. Loudness is matched per role (`LOUDNESS` in the script);
`python3 tools/sfx/thanos.py --check` proves the committed files are what the script makes. Events and
subtitles: `viltrumitecore/src/main/sound-overlays/thanos.json` (music and the void loop stream, no
subtitle for music).

| Cue (`assets/viltrumitecore/sounds/thanos/`) | Trigger |
|---|---|
| `arrival` | intro: the portal opens and he steps through (`ThanosEvent`, heard 160 blocks) |
| `step, step_2` | footfalls, client-side from the walk cycle (`ThanosFx`); the event alternates the two takes |
| `swing` | telegraph of CLEAVE / HAMMER and other melee moves; a Power swing that misses |
| `slam` | HAMMER ground strike; blocks he breaks while walking through walls (unstuck) |
| `hit` | a melee or portal punch / pull that connects |
| `hurt` | he is wounded (client-side, rate-limited) |
| `stun` | he is staggered (stun window) |
| `power_charge` | telegraph of POWER_PUNCH, POWER_WAVE and the Time+Power crush |
| `power_impact` | POWER_PUNCH impact; the Time+Power crush (pitched down) |
| `power_wave` | POWER_WAVE release |
| `space_open` | a portal opens: SPACE_PULL telegraph, both ends of SPACE_STEP, portal punch, portal ring |
| `space_step` | he arrives out of a portal (SPACE_STEP) |
| `space_pull` | the grab of SPACE_PULL, played where the victim is |
| `space_ward` | SPACE_WARD raised (projectiles turned back) |
| `reality_warp` | telegraph of REALITY_MIRAGE / Soul+Reality; a reality zone forming |
| `reality_mirage` | a mirage image appears or is popped |
| `time_dome` | telegraph and casting of TIME_DOME, TIME_REWIND, Time eternity |
| `time_rewind` | he rewinds himself to an earlier position (TIME_REWIND) |
| `mind_gaze` | MIND_GAZE / MIND_PULSE telegraph |
| `mind_pulse` | MIND_PULSE wave |
| `soul_tether` | SOUL_TETHER / SOUL_NOVA telegraph; a soul tether lashes out |
| `soul_nova` | SOUL_NOVA blast |
| `combo_portal_punch, combo_time_crush, combo_soul_mirage, combo_mind_step, combo_trinity, combo_eternity, combo_time_volley, combo_moon_fall, combo_infinity` | the combo's own two/three-stone cue, started `ThanosSounds.comboLead` ticks before the active tick so its strike lands with the hit |
| `phase` | phase change OPENING → ESCALATION → ONSLAUGHT |
| `stones_flare` | the FINAL phase: all six stones flare |
| `world_stop, gauntlet_raise, stone_light (×6, pitched per stone), fingers, snap_click, dissolve` | the snap scene beats of `ThanosSceneTimeline` (client, `ThanosSceneClient`) |
| `void` | loop under the empty-universe part of the scene (8 s, seamless) |
| `od_lagna_arrive, od_lagna_line` | Od Lagna appears; a soft chime under each of her six lines |
| `restore` | the universe is restored |
| `victory` | the heroes win (he falls) |
| `countdown` | UI tick in the last 10 seconds before the snap, rising pitch |
| `music_battle` | battle loop (100 BPM, 19.2 s) during OPENING–ONSLAUGHT, music category |
| `music_final` | final-phase loop (128 BPM, 15 s), music category |

Previews (not committed): `python3 tools/assets/thanos_preview.py rest|head|hand|clip NAME [frames] [yaw]` →
`build/thanos-assets/`.
