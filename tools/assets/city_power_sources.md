# City infrastructure (Living City 3.0) sound sources

All recordings are **CC0 1.0** (Creative Commons Zero, https://creativecommons.org/publicdomain/zero/1.0/) from
Freesound. Every landing page was opened and checked by hand: licence link = CC0, author, description (field
recordings or own synthesis, no film, game or anime rips). Only the hq preview MP3s were used; they live in
`~/.cache/viltrumite-sfx/work/raw/` and are **never committed**. No vanilla Minecraft sound is used or copied.

Recipe: `tools/sfx/city_power.py` (seeded, reproducible; `--sources` prints file -> ids). Packaged as mono Vorbis
`.ogg.b64` (split into `.b64.partN` below 45 KB) in
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/city_power/`; core's `decodeBinaryAssets` turns
them into `.ogg` at build time. Events: `viltrumitecore/src/main/sound-overlays/city_power.json` (22 events,
category `ambient`), registry `city/power/CityPowerSounds`, subtitles in all 12 locales
(`src/main/lang-overlays/city_power/`). Playback is client side only (`client/city/power/CityPowerAudio`).

**Not auditioned by ear.** The sandbox has no audio output: loudness (loudest 100 ms RMS per class), peaks, loop
seams and spectra were measured, nothing was listened to. The mix needs a listening pass.

## Recordings

| Key | Freesound | Creator | Original title | Attribution |
|---|---|---|---|---|
| `prague_siren` | [382611](https://freesound.org/people/nooly/sounds/382611/) | nooly | Air raid siren (Prague) | "Air raid siren (Prague)" by nooly is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `sentry_siren` | [714426](https://freesound.org/people/vyclops/sounds/714426/) | vyclops | air raid / tornado siren Sentry 20v2T alert warning signal | "air raid / tornado siren Sentry 20v2T alert warning signal" by vyclops is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `mega_siren` | [58927](https://freesound.org/people/HootOwl/sounds/58927/) | HootOwl | 3 Toned Mega Siren of Doom.wav | "3 Toned Mega Siren of Doom.wav" by HootOwl is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `wind_down` | [118754](https://freesound.org/people/maurcs/sounds/118754/) | maurcs | Tornado Siren winding down.aiff | "Tornado Siren winding down.aiff" by maurcs is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `klaxon` | [555465](https://freesound.org/people/alphatone/sounds/555465/) | alphatone | Papatone Pictures Klaxon Rapid.wav | "Papatone Pictures Klaxon Rapid.wav" by alphatone is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `glorb_arcs` | [867585](https://freesound.org/people/bassimat/sounds/867585/) | bassimat | Electric Arcs, Relays and Transformer Hum | "Electric Arcs, Relays and Transformer Hum" by bassimat is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `arc_buzz` | [776446](https://freesound.org/people/pryanic/sounds/776446/) | pryanic | ELECArc_Electric arc hum and buzz | "ELECArc_Electric arc hum and buzz" by pryanic is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `sparks_35kv` | [396470](https://freesound.org/people/kev_durr/sounds/396470/) | kev_durr | Electric Sparks.wav | "Electric Sparks.wav" by kev_durr is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `kv_spark` | [549878](https://freesound.org/people/therover/sounds/549878/) | therover | etincelle-spark.wav | "etincelle-spark.wav" by therover is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `steady_sparks` | [438500](https://freesound.org/people/craigsmith/sounds/438500/) | craigsmith | G32-16-Steady Sparks.wav | "G32-16-Steady Sparks.wav" by craigsmith is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `power_lines` | [347046](https://freesound.org/people/johnaudiotech/sounds/347046/) | johnaudiotech | 765 kilo-volt transmission lines buzzing and crackling | "765 kilo-volt transmission lines buzzing and crackling" by johnaudiotech is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `transformer_hum` | [641208](https://freesound.org/people/ideaph/sounds/641208/) | ideaph | transformer at night.wav | "transformer at night.wav" by ideaph is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `electro_explosion` | [730489](https://freesound.org/people/STRANGEFOLD6541100/sounds/730489/) | STRANGEFOLD6541100 | Electro_explosion 0 | "Electro_explosion 0" by STRANGEFOLD6541100 is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `massive_discharge` | [438494](https://freesound.org/people/craigsmith/sounds/438494/) | craigsmith | G32-10-Massive Electrical Discharges.wav | "G32-10-Massive Electrical Discharges.wav" by craigsmith is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `deep_boom` | [539968](https://freesound.org/people/Za-Games/sounds/539968/) | Za-Games | Deep Boom | "Deep Boom" by Za-Games is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `distant_metal` | [324277](https://freesound.org/people/Kostrava/sounds/324277/) | Kostrava | distant explosion | "distant explosion" by Kostrava is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `distant_series` | [320788](https://freesound.org/people/Kostrava/sounds/320788/) | Kostrava | distant explosions | "distant explosions" by Kostrava is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `kill_switch` | [131599](https://freesound.org/people/EchoCinematics/sounds/131599/) | EchoCinematics | Kill Switch (Large Breaker Switch) .WAV | "Kill Switch (Large Breaker Switch) .WAV" by EchoCinematics is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `breaker` | [130153](https://freesound.org/people/sribubba/sounds/130153/) | sribubba | Circuit Breaker 2 DP.wav | "Circuit Breaker 2 DP.wav" by sribubba is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `collapse` | [712918](https://freesound.org/people/greyfeather/sounds/712918/) | greyfeather | building collapse / demolition | "building collapse / demolition" by greyfeather is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `creak_slow` | [172411](https://freesound.org/people/EagleStealthTeam/sounds/172411/) | EagleStealthTeam | Creaking Metal - Slow | "Creaking Metal - Slow" by EagleStealthTeam is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `metal_groan` | [496836](https://freesound.org/people/hinchinbrook/sounds/496836/) | hinchinbrook | Metallic Groan | "Metallic Groan" by hinchinbrook is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `city_air` | [427841](https://freesound.org/people/leonelmail/sounds/427841/) | leonelmail | Ambience City Quiet Night Air Tone | "Ambience City Quiet Night Air Tone" by leonelmail is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `alarm_panel` | [874698](https://freesound.org/people/loganzsound/sounds/874698/) | loganzsound | Fire Alarm Panel Repeated Beeps | "Fire Alarm Panel Repeated Beeps" by loganzsound is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |

## Cues

Loops are cut from steady parts of a recording and closed with an equal-power crossfade over the seam. Far siren
variants are low-passed and get a synthetic late reverb (own processing). Every cue is normalised on its loudest
100 ms to a per-class target and soft-limited under -1 dBFS.

| Event (`viltrumitecore:city_power.*`) | Files | Sources (keys above) | Rate, Hz |
|---|---|---|---|
| `siren_warning` | `siren_warning.ogg` | `prague_siren` | 32000 |
| `siren_warning_far` | `siren_warning_far.ogg` | `prague_siren` | 22050 |
| `siren_attack` | `siren_attack.ogg` | `sentry_siren` | 32000 |
| `siren_attack_far` | `siren_attack_far.ogg` | `sentry_siren` | 22050 |
| `siren_critical` | `siren_critical.ogg` | `mega_siren` | 32000 |
| `siren_critical_far` | `siren_critical_far.ogg` | `mega_siren` | 22050 |
| `siren_all_clear` | `siren_all_clear.ogg` | `sentry_siren`, `wind_down` | 22050 |
| `klaxon` | `klaxon.ogg` | `klaxon` | 32000 |
| `alarm_beeps` | `alarm_beeps.ogg` | `alarm_panel` | 32000 |
| `arc` | `arc_1..4.ogg` | `kv_spark`, `sparks_35kv`, `glorb_arcs`, `massive_discharge` | 44100 |
| `transformer_blow` | `transformer_blow_1..3.ogg` | `breaker`, `kv_spark`, `electro_explosion`, `deep_boom`, `steady_sparks` | 44100 |
| `transformer_blow_far` | `transformer_blow_far_1..2.ogg` | `breaker`, `kv_spark`, `electro_explosion`, `deep_boom`, `steady_sparks` | 22050 |
| `spark_loop` | `spark_loop.ogg` | `steady_sparks` | 44100 |
| `line_hum` | `line_hum.ogg` | `transformer_hum`, `power_lines` | 32000 |
| `power_down` | `power_down.ogg` | `kill_switch`, `transformer_hum`, `breaker` | 44100 |
| `power_up` | `power_up.ogg` | `breaker`, `transformer_hum`, `glorb_arcs` | 44100 |
| `surge` | `surge_1..2.ogg` | `arc_buzz`, `sparks_35kv` | 44100 |
| `cascade` | `cascade_1..2.ogg` | `massive_discharge`, `deep_boom`, `creak_slow` | 44100 |
| `metal_groan` | `metal_groan_1..2.ogg` | `metal_groan`, `creak_slow` | 32000 |
| `low_impact` | `low_impact_1..2.ogg` | `deep_boom` | 22050 |
| `distant_destruction` | `distant_destruction_1..3.ogg` | `distant_series`, `distant_metal`, `collapse` | 22050 |
| `disaster_bed` | `disaster_bed.ogg` | `city_air`, `distant_series` | 22050 |

Loops: sirens (warning, attack, critical and their far variants), klaxon, alarm beeps, spark loop, line hum,
disaster bed. One-shots: all-clear, arcs, transformer blows, power down/up, surges, cascades, metal groans, low
impacts, distant destruction.
