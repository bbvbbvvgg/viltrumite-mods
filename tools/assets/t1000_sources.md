# T-1000 (liquid-metal infiltrator) sound sources

All recordings are **CC0 1.0** (Creative Commons Zero, https://creativecommons.org/publicdomain/zero/1.0/) from
Freesound, found through the Openverse API (`license=cc0`, source `freesound`). Every Freesound landing page was
opened and checked: licence link = CC0, author and description (own field/foley recordings — squeezed stress balls,
oobleck, jello, mud, a slug, a magazine rack, a shed door, swords, ice with a piezo element, a bamboo stick; no film,
game or anime rips). The newagesoup scrapes are cut from "SFX Metal Shed Door - Hits, Clangs, Rattle" by EpicWizard
(freesound.org/s/264889), also CC0 1.0. Only the hq preview MP3s were used; they live in
`~/.cache/viltrumite-sfx/work/raw/` and are **never committed**. No vanilla Minecraft sound is used or copied.

Recipe: `tools/sfx/t1000.py` (layers, varispeed, band limits, reverse, fades, level match to the loudest 100 ms RMS
per event under a -1 dBFS ceiling with a look-ahead limiter of at most 9 dB). Packaged by the sound-design helper as
mono 44.1 kHz Vorbis `.ogg.b64` in `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/t1000/`; core's
`decodeBinaryAssets` turns them into `.ogg` at build time. Events: `viltrumitecore/src/main/sound-overlays/t1000.json`
(16 events, category `hostile`, 33 files), registry `t1000/T1000Sounds`, subtitles in all 12 locales
(`src/main/lang-overlays/t1000/`). Playback: server side `Entity#playSound` from `T1000Entity` / `T1000Brain`.

**Not auditioned by ear.** The sandbox has no audio output: takes were chosen by metadata, onset/energy analysis and
spectrograms; loudness and peaks were measured. The mix needs a listening pass.

## Recordings

| Key | Freesound | Creator | Original title | Openverse id | Used in | Attribution |
|---|---|---|---|---|---|---|
| `slime13` | [433812](https://freesound.org/people/Archos/sounds/433812) | Archos | Slime 13.wav | `0ef058e3-eb33-4322-9264-268ccdf82bd5` | impact, morph, regen | "Slime 13.wav" by Archos is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `slime16` | [433817](https://freesound.org/people/Archos/sounds/433817) | Archos | Slime 16.wav | `c5d7acf0-4afe-4855-bfa8-b9f12a4fc1db` | reassemble, regen, stretch | "Slime 16.wav" by Archos is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `slime19` | [433820](https://freesound.org/people/Archos/sounds/433820) | Archos | Slime 19.wav | `9baaff7a-0d90-4acb-8193-aaa14ad6a4cc` | flow, regen, splash | "Slime 19.wav" by Archos is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `slime27` | [433840](https://freesound.org/people/Archos/sounds/433840) | Archos | Slime 27.wav | `78c1dc77-86aa-4427-b723-f9dd1be1ec1b` | blade_hit, deform, impact, regen, splash | "Slime 27.wav" by Archos is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `slug` | [447930](https://freesound.org/people/Breviceps/sounds/447930) | Breviceps | Step on a slug (Splat!) 2 | `a11fb938-8457-4f17-aa86-90c85e454356` | blade_hit, deform, impact, morph, reassemble, splash, step | "Step on a slug (Splat!) 2" by Breviceps is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `oobleck` | [221089](https://freesound.org/people/AlaskaRobotics/sounds/221089) | AlaskaRobotics | squishy oobleck goo | `07847f2f-a7c3-4707-a210-b9757b37b5eb` | destroy, flow, reassemble, reveal, splash, stretch | "squishy oobleck goo" by AlaskaRobotics is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `muddy` | [447928](https://freesound.org/people/Breviceps/sounds/447928) | Breviceps | Muddy Boots | `c0e232b4-7bb2-4d3e-8735-05e53e593142` | blade_hit, impact, step | "Muddy Boots" by Breviceps is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `jello` | [559768](https://freesound.org/people/Fugeni/sounds/559768) | Fugeni | Jello Squish | `bf88a6c7-6b42-469f-b510-1f75ebe50fbd` | destroy, flow | "Jello Squish" by Fugeni is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `clang` | [362625](https://freesound.org/people/kermite607/sounds/362625) | kermite607 | Metal Clang sound | `6afade61-ff10-493f-b420-48978c4b237c` | blade_hit, clang, crack, shatter | "Metal Clang sound" by kermite607 is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `clang_long` | [568786](https://freesound.org/people/tomwilkinson/sounds/568786) | tomwilkinson | Metallic clang | `45c01a17-af00-4c1f-93d1-2ca594cceacb` | clang | "Metallic clang" by tomwilkinson is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `hollow` | [486280](https://freesound.org/people/craigsmith/sounds/486280) | craigsmith | R15-07-Metal Clangs and Hollow Hits.wav | `5f77a8d3-2522-4808-b70c-73f912bcd34d` | blade_hit, clang, deform, destroy, impact, reassemble, shatter, step | "R15-07-Metal Clangs and Hollow Hits.wav" by craigsmith is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `scrape1` | [422431](https://freesound.org/people/BehanSean/sounds/422431) | BehanSean | Metal scrape 1.wav | `5b9afc06-6776-4bd0-a758-e5edf2556d32` | morph | "Metal scrape 1.wav" by BehanSean is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `scrape_s2` | [337842](https://freesound.org/people/newagesoup/sounds/337842) | newagesoup | small-metal-scrape-02.wav | `d19c4cf1-d402-4dc1-a6e3-5a5b473609d6` | deform, reveal, stretch | "small-metal-scrape-02.wav" by newagesoup is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `scrape_s4` | [337841](https://freesound.org/people/newagesoup/sounds/337841) | newagesoup | small-metal-scrape-04.wav | `77193be1-ff92-4cb9-b190-636099fad3f3` | stretch | "small-metal-scrape-04.wav" by newagesoup is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `slide` | [673329](https://freesound.org/people/tyballer92/sounds/673329) | tyballer92 | Metallic Scrape Slide | `2204ba42-c96d-4ff3-9b6b-c7fa0f45e096` | deform, destroy, morph, reassemble | "Metallic Scrape Slide" by tyballer92 is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `metal` | [52199](https://freesound.org/people/sentryx86/sounds/52199) | sentryx86 | Metal Scraping Metal.wav | `a1369abe-d98f-457c-a22f-1f8e5dd9db5c` | destroy, morph, reveal, shatter, swish | "Metal Scraping Metal.wav" by sentryx86 is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `ice1` | [262635](https://freesound.org/people/j_p_higgins/sounds/262635) | j_p_higgins | Ice Crack 1 | `ea33aaae-0977-44bd-b455-ab73205da0c2` | crack | "Ice Crack 1" by j_p_higgins is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `ice2` | [262634](https://freesound.org/people/j_p_higgins/sounds/262634) | j_p_higgins | Ice Crack 2 | `3727718c-564f-43b2-8995-d4695a69d762` | crack | "Ice Crack 2" by j_p_higgins is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `ice5` | [177217](https://freesound.org/people/ecfike/sounds/177217) | ecfike | Ice Crack 5.wav | `d0d5da51-a072-45ae-9e30-4b85a4404bcb` | shatter | "Ice Crack 5.wav" by ecfike is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `ice9` | [177219](https://freesound.org/people/ecfike/sounds/177219) | ecfike | Ice Crack 9.wav | `f0e06f5e-62d8-4c80-91c1-6b92b60f4b73` | crack, shatter | "Ice Crack 9.wav" by ecfike is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `swoosh` | [60026](https://freesound.org/people/qubodup/sounds/60026) | qubodup | Swosh swoosh whoosh air sound, free, high quality | `f1dc6045-7965-41de-87f8-4731d2583fd2` | swish | "Swosh swoosh whoosh air sound, free, high quality" by qubodup is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `swoosh15` | [60002](https://freesound.org/people/qubodup/sounds/60002) | qubodup | Swoosh 15 Windy | `7843f159-6886-426c-9d36-85f0d8d775f9` | swish | "Swoosh 15 Windy" by qubodup is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `swoosh09` | [59996](https://freesound.org/people/qubodup/sounds/59996) | qubodup | swoosh wind 09.flac | `a8e87f60-537e-4982-ba57-5b755dfbc77e` | blade_hit | "swoosh wind 09.flac" by qubodup is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `swoosh3` | [607250](https://freesound.org/people/D4XX/sounds/607250) | D4XX | Swoosh 3 | `d81d654b-d66e-44a8-98a3-3b97cadd3f8e` | swish | "Swoosh 3" by D4XX is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `swoosh1` | [607252](https://freesound.org/people/D4XX/sounds/607252) | D4XX | Swoosh 1 | `2068f6d9-dd45-47a7-9556-8a2b033fe683` | blade_hit | "Swoosh 1" by D4XX is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `radiator` | [413476](https://freesound.org/people/flood-mix/sounds/413476) | flood-mix | RADIATOR - Metallic clanging, water spouting | `7b350c80-e10d-48e2-82cb-9364f0c969cf` | splash | "RADIATOR - Metallic clanging, water spouting" by flood-mix is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |

## Events

| Event | Variants | Level (loudest 100 ms, dBFS) |
|---|---|---|
| `t1000.flow` | 3 | -20 |
| `t1000.step` | 3 | -16 |
| `t1000.stretch` | 2 | -16 |
| `t1000.morph` | 2 | -15 |
| `t1000.swish` | 3 | -14 |
| `t1000.blade_hit` | 3 | -12 |
| `t1000.clang` | 2 | -13 |
| `t1000.impact` | 3 | -12 |
| `t1000.deform` | 2 | -12 |
| `t1000.regen` | 2 | -19 |
| `t1000.reveal` | 1 | -16 |
| `t1000.splash` | 2 | -13 |
| `t1000.reassemble` | 1 | -15 |
| `t1000.crack` | 2 | -17 |
| `t1000.shatter` | 1 | -11 |
| `t1000.destroy` | 1 | -13 |
