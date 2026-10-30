# Sound (topic file)

Durable sound knowledge. Main memory: `.memory-bank/project.md`. Skill: `.agents/skills/sound-design`.

## Thor (R5, core 1.50.0)
- One facade: `ThorSounds` (server `play` / `playAt` / `notify` / `stop` / `bolt`) plays a `ThorCue`. `ThorCue` is a pure enum: stable id, layers (event, volume, pitch, jitter), sound source, loop flag. Thor code never names vanilla `SoundEvents`. Only `ThorSounds` and `ThorSoundLoops` touch the sound engine (`ThorSoundsTest` scans `hero/thor` and `client/thor`).
- Event names: `ThorCue.EVENTS` (append only). `ThorSounds` registers each as `thor.<name>`. sounds.json, subtitles (`subtitles.viltrumitecore.thor_<name>`, 11 locales) and files are pinned by `ThorSoundsTest`.
- Phase cues: windup on onStart / CHAIN, contact on ENTER_MAIN, tail on ENTER_FINISH. Strikes map through `ThorCue.windup/hit/ground/tail(ThorStrike)`. A new strike row needs no sound code.
- Risers (`WRATH_WINDUP`, `STORM_CHARGE`, `TELEPORT_CHARGE`) stop with `ThorSounds.stop` (a `ClientboundStopSoundPacket` to the players in range). A stop cuts every instance of each layer event for those players, so keep a riser in its own cue.
- Bolts: `HeroFx.electricBolt(level, at, height, power, SoundEvent thunder)` lets a hero replace the vanilla client pair (null keeps vanilla). Thor sends `thunder_crack`. The server adds the impact (gate 2 ticks) and the rumble (gate 30 ticks) per level.
- Loops are client instances in `ThorSoundLoops`; rules and lifecycle are pure in `ThorLoopRules`: electric mode, hammer spin (flight with the hammer in the hand, throw charge), flight wind (CRUISE / SONIC), the flying world hammer. Each loop fades out when its rule ends and is cleared on a level change.
- Audio: 56 own CC0 files from `tools/sfx/thor.py` (`--check` compares with the committed `.ogg.b64`). They were not auditioned.

## Pushing without git credentials
- The GitHub connector refuses a request above 65536 bytes. Push in batches (`push_files`) under ~60 KB. Keep each `.ogg.b64` under ~60 KB (shorter or low-passed cue).
- A text file above ~60 KB (for example `SESSION.md`, `.memory-bank/project.md`) cannot be updated through the connector. Put new durable notes in a topic file instead.

## Hulkbuster (fix spec item 3, 2026-10-30)
- 56 files / 31 events from `tools/sfx/hulkbuster.py`: CC0 1.0 Freesound recordings (SOURCES table, 28 rows, hq MP3 previews in `~/.cache/viltrumite-sfx/work/raw/fs_<id>.mp3`, `--fetch` gets them) carry every cue; the H6 synthesis is the support layer. `--check` re-mixes and compares; `--sources` lists file -> ids. Not auditioned.
- Openverse was unreachable (504 / timeout): sources come from Freesound search pages (`/search/?q=...&f=license:"Creative Commons 0"`, scrapeable) and landing pages (licence link, description for AI / parents). Credits are composed from the landing page and say so. The helper's `package` needs an Openverse `--source-id`, so the script encodes with the same ffmpeg settings itself.
- Leveling lesson: recorded hits are crest-limited. Per-layer look-ahead limiting at 12 dB over the layer's loudest 100 ms RMS, a 38 Hz bus high-pass and a tanh bus knee (7–8 dB over RMS) let the impact class reach about −8.7 LUFS; after the limiter, each variant's gain is corrected so variants stay within 0.2 dB.
