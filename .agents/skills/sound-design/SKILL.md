---
name: sound-design
description: Design, source, edit, integrate, and audit Minecraft Forge sound effects in viltrumite-mods. Use when a hero or ability needs SFX, a sound is missing or weak, audio timing or loops need fixes, Openverse/Freesound audio is requested, or sound assets and credits need review. Not for animation-only changes.
compatibility: Minecraft Forge 1.20.1; Python 3.10+; ffmpeg and ffprobe for audio packaging; network access for Openverse searches.
metadata:
  version: "1.0.0"
---

# Sound design — Viltrumite Mods

Follow `AGENTS.md` first. Code and approved design decisions override this skill. This skill covers **development-time** audio only; the shipped mod never contacts Openverse. If a task also changes animations/VFX, follow `animation-system`.

## 1. Audit before sourcing

1. Inspect the target ability, animation timings, existing playback calls, `sounds.json`, sound registries, and client `SoundInstance` classes. Identify what is missing: anticipation, movement, impact, tail, loop, or stop.
2. Check existing sounds and recipes before searching. See `tools/sfx/ironman_cc0.py`, `tools/sfx/ironman_stage1.sh`, `CREDITS.md`, and `tools/assets/<hero>_sources.md`. Reuse a suitable CC0/own asset or derive a variant before downloading a new file.
3. Respect user-approved sounds and deliberate silence. **Never replace** a signature sound (for example, Reinhard World Cut) or unmute Iron Man beeps/voice events without an explicit request. Avoid unrelated sound changes.
4. Make a short cue sheet: event, source or synthesis plan, trigger tick, duration, volume, pitch, listener scope, and stop/loop behavior. Align to the actual code and 20 ticks/second. For major effects, consider attack/impact/tail layers rather than one generic clip.

## 2. Find missing sounds (Openverse)

Read [Openverse reference](references/openverse.md) when searching or handling licenses, quotas, or API errors. Use the bundled helper instead of hand-written curl loops:

```bash
python .agents/skills/sound-design/scripts/openverse_sfx.py search "metal sword whoosh" --limit 10
python .agents/skills/sound-design/scripts/openverse_sfx.py download <result-uuid>
```

- **Repo license policy: CC0 or own sounds only.** The article's `license=cc0,by,pdm` filter is too broad for this repository. Do not commit CC BY or PDM material without the owner's explicit license-policy change. Check the source landing page if license metadata looks inconsistent.
- Default search uses `license=cc0&source=freesound`. **Do not add `category=sound_effect`**: real Freesound results may have `category: null` and disappear under that filter.
- Search in English with concrete acoustic terms (`heavy metal latch`, `fast blade swish`). Make **one search per sound family**, inspect its results, and cache it. Change the query only if no candidate fits; never brute-force pages or bypass API limits.
- Shortlist up to three candidates by sound, duration, dynamics, and recording quality. **Audition audio** when playback is available. Metadata and filenames do not prove a clip sounds right. If playback is unavailable, say so and avoid claiming a listening test.
- `url` may point to a Freesound **preview MP3**, not the original master. Check quality and provenance before committing. Never scrape the source site to bypass its terms.
- Preserve the API's `attribution` field **verbatim** in the local `credits.txt`. Also record the landing URL, creator, CC0 license URL, original filename, and processing steps in `tools/assets/<hero>_sources.md`; update `CREDITS.md` where appropriate.

## 3. Build the cue

1. Work with downloaded sources in a local scratch folder (the helper defaults to `~/.cache/viltrumite-sfx/work`). Do not commit raw downloads, previews, cache, or temporary mixes.
2. Edit with ffmpeg or an audio editor: trim silence, choose the attack point, layer complementary textures, adjust pitch, EQ and volume, and add short fades where appropriate. Keep important transient detail. Avoid hard clipping and unnecessarily loud or long sounds.
3. For positional one-shots, prefer **mono OGG Vorbis, 44.1 kHz**. Use stereo only for a justified non-positional sound. Make loops seamless and provide a real stop condition; `stream: true` alone does not make a sound loop.
4. Package the reviewed mix using the helper. This writes the repository's required **base64 text asset**, not a raw `.ogg`:

```bash
python .agents/skills/sound-design/scripts/openverse_sfx.py package \
  --input /path/to/reviewed.wav --module core --name regulus/blade_whoosh \
  --event regulus_blade_whoosh --source-id <result-uuid>
```

For a fully original sound, use `--own` instead of `--source-id`. Use `--stereo` only if justified. The helper refuses to overwrite assets by default. See [Minecraft integration](references/minecraft-audio.md) for exact paths and event registration.

## 4. Integrate into the game

1. Core: `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/<hero>/<name>.ogg.b64`; flight: the corresponding `viltrumiteflight` path. `decodeBinaryAssets` generates `.ogg` during Gradle builds. **Do not commit a duplicate `.ogg` in resources.**
2. Register the resource in the correct module's `src/main/resources/assets/<modid>/sounds.json`, then add/reuse the proper `SoundEvent` registry entry in Java. Inspect current registration patterns instead of inventing a second registry.
3. Connect playback to the actual event/tick, choose the correct side and audience, and prevent duplicate client/server playback. One-shots can use server-level playback; continuous flight/charge sounds usually need a client sound instance and explicit stop handling.
4. Add subtitle translation keys in every existing locale when adding a subtitle key. Avoid hardcoded English subtitle text for new events.
5. For variants, use one event with multiple sound entries and controlled pitch/volume variation. Do not trigger multiple copies of the same loud impact at the same tick without design intent.

## 5. Verify and report

- Inspect output with `ffprobe` (Vorbis codec, channels, duration). Confirm no missing file, silent clip, clipping, accidental long tail, abrupt loop boundary, or wrong event ID.
- Run `python -m unittest discover -s .agents/skills/sound-design/tests -v` for helper changes, `git diff --check`, and `./gradlew build` for integrated assets or Java changes. Test in game when playback behavior changes. If game or listening tests cannot run, state that explicitly.
- Check resource path, `sounds.json`, registry, subtitle locales, credits, and `tools/assets/<hero>_sources.md` against the actual delivered files. Never claim a listening test based only on `ffprobe`.
- Report: changed cues and their trigger ticks, reused/new sources and licenses, generated paths, test results, and anything not heard or tested.

## Gotchas

- The `freesound` Openverse dataset often has `category: null`. The `category=sound_effect` filter can return zero results for a valid query.
- `source=freesound,jamendo` is not useful as a default for effects: Jamendo is mostly music, often under licenses the repo does not accept.
- A CC0 **label** is metadata, not proof of authorship. Check the source for suspicious uploads, especially film/anime/game sound rips.
- This repo stores many audio files as `.ogg.b64` under `binassets`. Putting a new binary in the wrong resource directory can pass review yet fail to ship correctly.
- Minecraft positional sound and GUI/ambience sound have different channel and playback needs. Do not force stereo or streaming on short positional impacts.
- The user's intentional silence and approved signature audio take priority over automated search quality scores.
