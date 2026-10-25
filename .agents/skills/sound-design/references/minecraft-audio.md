# Minecraft Forge audio integration in this repository

## Asset locations

For `viltrumitecore`:

- Packed binary text: `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/<hero>/<name>.ogg.b64`
- Event definitions: `viltrumitecore/src/main/resources/assets/viltrumitecore/sounds.json`
- Java: find existing `SoundEvent` registration in the core module and follow its pattern.

For `viltrumiteflight`:

- Packed binary text: `viltrumiteflight/src/main/binassets/assets/viltrumiteflight/sounds/<name>.ogg.b64`
- Event definitions: `viltrumiteflight/src/main/resources/assets/viltrumiteflight/sounds.json`
- Registry example: `viltrumiteflight/src/main/java/dev/baranhan/viltrumiteflight/registry/ModSounds.java`

Both modules use Gradle `decodeBinaryAssets` to decode `.b64` into generated resources. Check the module's current Gradle file before changing this pipeline.

## Example sounds.json entry

```json
{
  "regulus_blade_whoosh": {
    "category": "player",
    "subtitle": "subtitles.viltrumitecore.regulus_blade_whoosh",
    "sounds": ["viltrumitecore:regulus/blade_whoosh"]
  }
}
```

This is a fragment to merge into the existing `sounds.json`, not a replacement file. For a long streamed sound, a sound entry can use `{ "name": "viltrumiteflight:wind_loop", "stream": true }`. **Streaming does not implement looping.** Inspect `FlightWindSoundInstance` or `ThrusterSound` for loop lifecycle patterns.

## Java and playback

- Inspect current `SoundEvent` registration. Flight uses `DeferredRegister<SoundEvent>` and `SoundEvent.createVariableRangeEvent`.
- Use the actual event ID from `sounds.json`. An unregistered or mismatched event will not play as intended.
- For world-audible one-shots, existing `IronManSounds.play` calls `player.level().playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch)` from server code. Do not also play the same one-shot locally for the acting player.
- For continuous sounds, study `client/ironman/ThrusterSound.java` and `viltrumiteflight/client/sound/FlightWindSoundInstance.java`. Stop when the ability stops, hero changes, player dies, or sound context disappears.
- Tie sounds to actual ticks or state transitions, not frame callbacks. If a cue is tied to an animation, confirm its tick offset against code. A cue at tick 8 begins ~0.4 seconds after the trigger at 20 ticks/second.
- New subtitle keys go into **all** locale files for the relevant module.

## Provenance and verification

- Keep source mapping in `tools/assets/<hero>_sources.md` and overview in `CREDITS.md`. Record each layer, not just the output.
- Do not overwrite signature cues without explicit user approval. In particular, Iron Man deliberately mutes some beeps/voice events; see `tools/sfx/ironman_cc0.py` and `CREDITS.md`.
- Use `ffprobe` to verify Vorbis codec, channels, and duration. Test resource loading and the correct listener scope in `./gradlew runClient` when available. `./gradlew build` is necessary but not sufficient for sound playback verification.
