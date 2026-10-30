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

## DIO (polish, core 1.55.0)
- `tools/sfx/dio.py`: 30 events / 36 files, all own synthesis (CC0). Loudness is matched by role (`LOUDNESS`: loudest
  100 ms RMS, heavy -8..-10 dBFS, hits -10..-12, motion -11..-15, utility/loop -15..-17), soft-limited at -1.5 dBFS.
  Peak normalisation alone had left a 13 dB spread. Hits rotate variants (`stand_hit`, `barrage_hit`, `knife_hit`,
  `roller_punch`).
- Vanilla sends a variable-range event only to 16 x volume blocks: `DioSounds.playWide` broadcasts with an explicit
  range (time stop / resume to the whole 40-block field, roller cues).
- `dio.time_tick` loops on the client (`TimeStopClock`) while the camera is inside a running field.
- Openverse API timed out (504) for the whole polish session: no CC0 recordings were added. Not auditioned.
