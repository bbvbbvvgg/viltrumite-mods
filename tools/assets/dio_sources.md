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
| `textures/gui/ability/dio/{the_world,muda_barrage,knives,time_stop,vampire_drain,road_roller}.png.b64` | same | ability icons |

`python3 tools/assets/make_dio_assets.py --check` regenerates everything and fails on any byte difference.

## Sounds

All 30 events (36 files: hits rotate 2-3 variants) are original procedural synthesis by `tools/sfx/dio.py` (seeded numpy noise, oscillators, inharmonic partials,
STFT filters and a synthetic noise reverb; CC0-1.0), mono 44.1 kHz Vorbis (`-q:a 2`, bitexact), packed as `.ogg.b64` in
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/dio/`. `python3 tools/sfx/dio.py --check` re-synthesises
and compares decoded PCM. No voice recording: `wryyy` is a synthetic saw-glottis screech with moving formants.

| Event | Trigger (server unless noted) | Length | Loop / stop |
|---|---|---|---|
| `dio.stand_summon` | stand appears (`DioCombat.summon`) | 1.0 s | one-shot |
| `dio.stand_dismiss` | stand recalled | 0.7 s | one-shot |
| `dio.stand_whoosh` | jab / cross / hook / finisher swing; ROAD ROLLER crouch (tick 14, pitch 0.6) | 0.3 s | one-shot |
| `dio.stand_hit` (`stand_hit`, `stand_hit_2`) | jab and cross contact tick | 0.32 s | one-shot, random variant |
| `dio.stand_hook` | hook contact tick | 0.5 s | one-shot |
| `dio.barrage_hit` (`barrage_hit`, `_2`, `_3`) | every second MUDA hit tick | 0.14 s | one-shot, random variant |
| `dio.barrage_rush` | barrage start, then every 12 ticks while held | 0.7 s | re-triggered, ends with the barrage |
| `dio.finisher` | finisher contact | 1.1 s | one-shot |
| `dio.knife_draw` | knife fan start | 0.4 s | one-shot |
| `dio.knife_throw` | knife release tick | 0.4 s | one-shot |
| `dio.knife_hit` (`knife_hit`, `knife_hit_2`) | knife hits an entity / block | 0.25 s | one-shot, random variant |
| `dio.knife_stop` | knife freezes in stopped time | 0.6 s | one-shot |
| `dio.time_stop` | ZA WARUDO cast and ROAD ROLLER start | 3.2 s | one-shot, sent to the whole field (`DioSounds.playWide`, radius + 24) |
| `dio.time_tick` | client: clock loop while the camera is inside a running field (`TimeStopClock`, volume 0.45, non-positional); server: once at the release (pitch 0.6) | 1.0 s seamless | client loop fades out when the field ends or the camera leaves; cleared on logout and level change |
| `dio.time_resume` | field resume | 1.4 s | one-shot, whole field (`playWide`) |
| `dio.ledger_burst` | deferred ledger paid out | 0.6 s | one-shot |
| `dio.drain_grab` | vampire drain grabs | 0.45 s | one-shot |
| `dio.drain_pulse` | every drain pulse (10 ticks) | 0.6 s | one-shot |
| `dio.roller_leap` | ROAD ROLLER tick 26 (DIO kicks off) | 0.95 s | one-shot |
| `dio.roller_appear` | ROAD ROLLER tick 46 (roller materialises over DIO) | 1.8 s | one-shot, `playWide` |
| `dio.roller_fall` | ROAD ROLLER tick 70 (fall from the apex), cuts at 1.28 s just before the slam | 1.32 s | one-shot, `playWide` |
| `dio.roller_slam` | ROAD ROLLER tick 96 | 1.5 s | one-shot, `playWide` |
| `dio.roller_punch` (`roller_punch`, `_2`, `_3`) | every 4 ticks on top of the roller (96..148) | 0.22 s | one-shot, random variant |
| `dio.roller_explode` | ROAD ROLLER tick 160 (time resumes) | 2.6 s | one-shot, `playWide` |
| `dio.roller_land` | ROAD ROLLER tick 174 (DIO lands beside the wreck) | 0.7 s | one-shot |
| `dio.wryyy` | ROAD ROLLER tick 148 | 1.9 s | one-shot |
| `dio.guard` | stand guard raised | 0.35 s | one-shot |
| `dio.deflect` | guard deflects a projectile | 0.35 s | one-shot |
| `dio.refuse` | ability refused (blood / cooldown) | 0.3 s | one-shot |
| `dio.sun_sizzle` | sunlight burn tick | 0.9 s | one-shot |

Loudness: every cue is set by `LOUDNESS` in `tools/sfx/dio.py` to a role target (loudest 100 ms RMS: heavy/signature
-8..-10 dBFS, hits -10..-12, motion -11..-15, utility and the clock loop -15..-17), soft-limited under -1.5 dBFS. Before the
2026 polish the cues were only peak-normalised (13 dB spread: `knife_hit`, `stand_whoosh`, `sun_sizzle`, `time_tick` buried).

CC0 recordings: the polish tried to layer vetted Openverse CC0 sources (the Reinhard set in `tools/assets/reinhard_sources.md`),
but api.openverse.org timed out / returned 504 for every search and detail request in that session, so DIO stays 100 % own
synthesis. Measured with PCM statistics and spectrograms only: **not auditioned by ear**; final balance needs an in-game listening pass.
