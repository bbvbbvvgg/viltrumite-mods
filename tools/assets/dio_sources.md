# DIO asset sources

Every DIO asset is **own work** (CC0-1.0), generated deterministically by scripts in this repository. No external
samples, models, textures or recordings were downloaded, traced or converted. Raw intermediate files are never committed.

## Models, textures, animations

| Committed files | Generator | Notes |
|---|---|---|
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/dio/the_world.geo.json`, `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/dio/the_world{,_glow}.png.b64` | `tools/assets/make_dio_assets.py` (`dio_lib/the_world.py`, `dio_lib/paint.py`) | The World: 42 bones (torso, neck, head, shoulders, elbows, forearms, hands, 4 fingers ×2 joints + thumb per hand, pelvis, knees, feet, hoses), 130 cubes, per-face atlas; gold plates + teal, painted procedurally |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/animations/dio/the_world_*.animation.json` (12 clips) | `tools/assets/make_dio_assets.py` (`dio_lib/the_world_clips.py`) | idle, ready, summon, dismiss, jab, cross, hook, barrage, finisher, guard, time_stop, roller_barrage; contact keys equal `DioRules` contact ticks |
| `geo/dio/dio_knife.geo.json`, `textures/entity/dio/dio_knife.png.b64` | same (`dio_lib/props.py`) | throwing knife |
| `geo/dio/road_roller.geo.json`, `textures/entity/dio/road_roller{,_glow}.png.b64` | same | volumetric road roller: polygonal cylindrical drums, yoke, body, hood, cab roof |
| `geo/dio/dio_outfit.geo.json`, `textures/entity/dio/dio_outfit.png.b64` | same | DIO's outfit overlay on the player bones: headband with heart, gold collar and shoulder pads, jacket stripes, heart knee and toe caps |
| `textures/entity/hero/dio.png.b64` | same (`dio_lib/skin.py`) | 64×64 DIO player skin |
| `textures/gui/ability/dio/{the_world,muda,knives,time_stop,drain,road_roller}.png.b64` | same | ability icons |

`python3 tools/assets/make_dio_assets.py --check` regenerates everything and fails on any byte difference.

## Sounds

All 27 cues are original procedural synthesis by `tools/sfx/dio.py` (seeded numpy noise, oscillators, inharmonic partials,
STFT filters and a synthetic noise reverb; CC0-1.0), mono 44.1 kHz Vorbis (`-q:a 2`, bitexact), packed as `.ogg.b64` in
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/dio/`. `python3 tools/sfx/dio.py --check` re-synthesises
and compares decoded PCM. No voice recording: `wryyy` is a synthetic saw-glottis screech with moving formants.

| Event | Trigger (server unless noted) | Length | Loop / stop |
|---|---|---|---|
| `dio.stand_summon` | stand appears (`DioCombat.summon`) | 1.0 s | one-shot |
| `dio.stand_dismiss` | stand recalled | 0.7 s | one-shot |
| `dio.stand_whoosh` | jab / cross / hook / finisher swing | 0.28 s | one-shot |
| `dio.stand_hit` | jab and cross contact tick | 0.32 s | one-shot |
| `dio.stand_hook` | hook contact tick | 0.5 s | one-shot |
| `dio.barrage_hit` | every second MUDA hit tick | 0.14 s | one-shot |
| `dio.barrage_rush` | barrage start, then every 12 ticks while held | 0.7 s | re-triggered, ends with the barrage |
| `dio.finisher` | finisher contact | 1.1 s | one-shot |
| `dio.knife_draw` | knife fan start | 0.4 s | one-shot |
| `dio.knife_throw` | knife release tick | 0.4 s | one-shot |
| `dio.knife_hit` | knife hits an entity / block | 0.25 s | one-shot |
| `dio.knife_stop` | knife freezes in stopped time | 0.6 s | one-shot |
| `dio.time_stop` | ZA WARUDO cast and ROAD ROLLER start | 3.2 s | one-shot |
| `dio.time_tick` | client: clock loop inside a stopped field; server: once at the release | 1.0 s seamless | client `TimeStopClient` loop, stopped when the field ends, on death, logout or dimension change |
| `dio.time_resume` | field resume | 1.4 s | one-shot |
| `dio.ledger_burst` | deferred ledger paid out | 0.6 s | one-shot |
| `dio.drain_grab` | vampire drain grabs | 0.45 s | one-shot |
| `dio.drain_pulse` | every drain pulse (10 ticks) | 0.6 s | one-shot |
| `dio.roller_appear` | ROAD ROLLER tick 24 | 1.8 s | one-shot |
| `dio.roller_slam` | ROAD ROLLER tick 44 | 1.5 s | one-shot |
| `dio.roller_punch` | every 4 ticks on top of the roller | 0.2 s | one-shot |
| `dio.roller_explode` | ROAD ROLLER tick 108 (time resumes) | 2.6 s | one-shot |
| `dio.wryyy` | ROAD ROLLER tick 96 | 1.9 s | one-shot |
| `dio.guard` | stand guard raised | 0.35 s | one-shot |
| `dio.deflect` | guard deflects a projectile | 0.35 s | one-shot |
| `dio.refuse` | ability refused (blood / cooldown) | 0.22 s | one-shot |
| `dio.sun_sizzle` | sunlight burn tick | 0.9 s | one-shot |

Measured with `ffprobe`/PCM statistics only: **not auditioned by ear**; final balance needs an in-game listening pass.
