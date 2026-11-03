# SCP-106 sound sources

All 25 SCP-106 sound events (`scp106.*`, `scp106.pocket.*`; 44 files under
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/scp106/`) are mixed by `tools/sfx/scp106.py`
from the CC0 1.0 Freesound recordings below, found through Openverse. No vanilla Minecraft sound and no
SCP – Containment Breach audio is used. Every license is CC0 1.0
(https://creativecommons.org/publicdomain/zero/1.0/), checked by the download helper's license gate against the
Openverse record that points at the Freesound page. The HQ preview MP3s are downloaded with
`python3 .agents/skills/sound-design/scripts/openverse_sfx.py download <uuid>` (uuids in `SOURCES` of the
script) and never committed.

Processing (all in `tools/sfx/scp106.py`, ffmpeg + numpy): segment cut, varispeed (mostly 0.4-0.9x, lower and
slower), occasional reversal, high/low-pass, layering, a synthetic decaying-noise room, soft saturation, fades,
peak normalisation; the three beds (`hum`, `void`, `water`) are stereo loops with the tail crossfaded into the
head. Encoded as 44.1 kHz Ogg Vorbis q5 by the sound-design helper.

| Key | Freesound | Creator | Original title | License | Used in |
|---|---|---|---|---|---|
| `zombie_breath` | [189281](https://freesound.org/people/Huminaatio/sounds/189281) | Huminaatio | Zombie breathing | CC0 1.0 | `breath_1`, `breath_2`, `breath_3`, `scare` |
| `slow_breath` | [271045](https://freesound.org/people/Kodack/sounds/271045) | Kodack | Male Breathing Slow | CC0 1.0 | `breath_1`, `breath_2`, `draft_1`, `emerge`, `escape`, `phase` |
| `heavy_breath` | [163383](https://freesound.org/people/Under7dude/sounds/163383) | Under7dude | Heavy Breathing | CC0 1.0 | `breath_3`, `draft_2` |
| `steps_concrete` | [205748](https://freesound.org/people/Yuval/sounds/205748) | Yuval | footsteps concrete | CC0 1.0 | `far_steps_1`, `far_steps_2`, `step_1`, `step_2`, `step_3` |
| `squelch` | [167927](https://freesound.org/people/billox30/sounds/167927) | billox30 | Squelch.flac | CC0 1.0 | `capture`, `emerge`, `scare`, `sink`, `step_1`, `step_2`, `step_3` |
| `mud_bubbles` | [352904](https://freesound.org/people/ProductionNow/sounds/352904) | ProductionNow | Bubbling Mud with Steam | CC0 1.0 | `breach`, `capture`, `emerge`, `escape`, `sink` |
| `feet_squelch` | [683520](https://freesound.org/people/eqavox/sounds/683520) | eqavox | Feet Squelch | CC0 1.0 | `capture`, `sink`, `step_1`, `step_2`, `step_3` |
| `squelching` | [547446](https://freesound.org/people/tiddles451/sounds/547446) | tiddles451 | squelching.mp3 | CC0 1.0 | `capture`, `emerge`, `phase` |
| `miser_laugh` | [545479](https://freesound.org/people/Elenalostale/sounds/545479) | Elenalostale | OLD MISER LAUGH | CC0 1.0 | `capture`, `laugh_1`, `laugh_3`, `scare` |
| `old_laugh_4` | [166133](https://freesound.org/people/deleted_user_2104797/sounds/166133) | deleted_user_2104797 | Laugh_old_man_4.wav | CC0 1.0 | `laugh_2` |
| `dark_drone_1` | [579259](https://freesound.org/people/nomiqbomi/sounds/579259) | nomiqbomi | Dark Drone 1 | CC0 1.0 | `hum`, `sigil` |
| `dark_drone_amb` | [500326](https://freesound.org/people/LulSayer/sounds/500326) | LulSayer | Dark Drone Ambient | CC0 1.0 | `breach`, `capture`, `emerge`, `phase`, `shift`, `sink`, `void` |
| `dark_drone_4` | [579260](https://freesound.org/people/nomiqbomi/sounds/579260) | nomiqbomi | Dark Drone 4 | CC0 1.0 | `escape`, `void` |
| `cave_drips` | [645761](https://freesound.org/people/Duisterwho/sounds/645761) | Duisterwho | Dripping cave water drops - ambient atmosphere deep mysterious.wav | CC0 1.0 | `drip_3`, `water` |
| `cave_loop` | [553080](https://freesound.org/people/Nox_Sound/sounds/553080) | Nox_Sound | Ambiance_Atmosphere_Cave_Loop_Stereo.ogg | CC0 1.0 | `draft_1`, `draft_2`, `water` |
| `drips` | [234554](https://freesound.org/people/Electroviolence/sounds/234554) | Electroviolence | Dripping water | CC0 1.0 | `drip_1`, `drip_2` |
| `drip_echo` | [135150](https://freesound.org/people/mlsprovideos/sounds/135150) | mlsprovideos | WATER DRIPPING ECHO.wav | CC0 1.0 | `water` |
| `acid_burn` | [382313](https://freesound.org/people/bottles/sounds/382313) | bottles | Sizzle / Acid Burn | CC0 1.0 | `corrode_1`, `corrode_2` |
| `sizzle_drops` | [726473](https://freesound.org/people/Canakinsound/sounds/726473) | Canakinsound | Sizzle single drops to multiple drops | CC0 1.0 | `corrode_1` |
| `acid_bubbling` | [202094](https://freesound.org/people/spookymodem/sounds/202094) | spookymodem | Acid Bubbling.wav | CC0 1.0 | `corrode_2`, `phase` |
| `factory_buzz` | [537769](https://freesound.org/people/janbezouska/sounds/537769) | janbezouska | Factory Fluorescent light buzz | CC0 1.0 | `hum` |
| `backrooms` | [626096](https://freesound.org/people/Resaural/sounds/626096) | Resaural | Backrooms Ambience | CC0 1.0 | `hum` |
| `hull_creak` | [452104](https://freesound.org/people/kyles/sounds/452104) | kyles | wood creaks deep door or ship hull close slow.flac | CC0 1.0 | `breach`, `creak_1`, `shift` |
| `door_creak` | [637484](https://freesound.org/people/kyles/sounds/637484) | kyles | door wood creak open slow.flac | CC0 1.0 | `creak_2`, `locked` |
| `wood_creaks` | [269722](https://freesound.org/people/seth-m/sounds/269722) | seth-m | wood creaks | CC0 1.0 | `creak_3` |
| `pipe_basement` | [104373](https://freesound.org/people/RutgerMuller/sounds/104373) | RutgerMuller | Metallic Pipe Hits on Concrete in Basement.aif | CC0 1.0 | `metal_1`, `shift` |
| `hollow_pipe` | [271170](https://freesound.org/people/Zott820/sounds/271170) | Zott820 | Hollow Metal Pipe Hits | CC0 1.0 | `key`, `metal_2`, `scare` |
| `long_metal` | [337832](https://freesound.org/people/newagesoup/sounds/337832) | newagesoup | long-metal-hit-01.wav | CC0 1.0 | `breach`, `escape`, `key`, `metal_2`, `metal_3`, `sigil` |
| `creepy_whisper` | [234044](https://freesound.org/people/RaspberryTickle/sounds/234044) | RaspberryTickle | Creepy Whispering | CC0 1.0 | `whisper_1` |
| `unfa_whispers` | [207725](https://freesound.org/people/unfa/sounds/207725) | unfa | Whispers | CC0 1.0 | `whisper_2` |
| `whisper_trail` | [22329](https://freesound.org/people/Black%20Boe/sounds/22329) | Black Boe | whisper trail 2.ogg | CC0 1.0 | `whisper_3` |
| `triple_knock` | [351144](https://freesound.org/people/Tombombadil1988/sounds/351144) | Tombombadil1988 | Triple Knock on Door | CC0 1.0 | `knock_1` |
| `door_knocking` | [458007](https://freesound.org/people/Fabrizio84/sounds/458007) | Fabrizio84 | Knocking on Door | CC0 1.0 | `knock_2` |
| `metal_door_knock` | [679321](https://freesound.org/people/gamer500/sounds/679321) | gamer500 | hard knocking metal door | CC0 1.0 | `knock_3`, `locked` |
| `wood_knock` | [553297](https://freesound.org/people/Alex-Fullam/sounds/553297) | Alex-Fullam | Knock on Door/Wood | Knocking 3 Times on Wood.mp3 | CC0 1.0 | `locked` |
