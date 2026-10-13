# Reinhard — Stage 7 (Sounds and Polish) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Overview: `docs/plans/2026-10-11-reinhard-overview.md`.

**Goal:** replace every vanilla placeholder with the 14 Freesound sources from spec §10.1 (trimmed, layered and normalized by a reproducible script), record the source and license of each, run the full acceptance pass §11.1–§11.12 in both views and on a dedicated server, tune balance numbers only where the pass shows a problem, and release.

**Architecture:** no new gameplay code. `tools/sfx/reinhard.py` turns the user's source files into `binassets/assets/viltrumitecore/sounds/reinhard/*.ogg.b64`; `res/sounds.json` event names stay exactly as added in Stages 1–6 (only the `sounds` arrays change), so no Java changes for sounds.

**Tech Stack:** Python 3 + ffmpeg (`atrim`, `afade`, `adelay`, `amix`, `alimiter`, two-pass `loudnorm`), the existing `binassets` b64 pipeline in `viltrumitecore/build.gradle`.

**Spec:** §10, §10.1, §10.2, §11, §12.

## Global Constraints

See the overview. Stage-specific:
- **License rule of this hero (spec §10):** CC-licensed Freesound sounds are allowed (owner decision), but only CC0 and CC-BY. A source that turns out to be CC-BY-NC / Sampling+ / other is **not** committed; it is reported to the user with 1–2 alternative candidates (a question, never a silent swap).
- Raw sources are not committed (they stay with the user and are passed to the script as a folder); only the processed `.ogg` (as `.ogg.b64`) and the script are committed.
- Mono, 44.1 kHz, Vorbis q5; integrated loudness per class: impacts −14 LUFS, beds/ambience −20 LUFS, UI/refusal −18 LUFS.
- No synthesis (spec §10): the script only trims, fades, layers, pitches and normalizes.
- Per-hero sources file convention of `CREDITS.md`: `tools/assets/reinhard_sources.md` (already exists from Stage 1; this stage only appends).

## Review Focus

1. Every committed sound has a row in `tools/assets/reinhard_sources.md` and spec §10.1 with URL, author, license; every CC-BY author is in `CREDITS.md` — `ReinhardSoundsTest.everySoundCredited`.
2. `sounds.json`: every `reinhard.*` event points to an existing file; no vanilla placeholder is left except the §10.2 ones — `ReinhardSoundsTest.noPlaceholdersLeft`.
3. The script is reproducible: two runs on the same sources give identical decoded PCM — `python3 tools/sfx/reinhard.py --check`.
4. Mix: the World Cut blast does not clip, the erasure silence is real silence with the final files, mode chords are not louder than a punch — listening protocol Task 3.
5. The acceptance pass covers both views and a dedicated server for every §11 item; a balance change is only a `ReinhardRules` constant plus its test.

---

### Task 1: License check and sources file

Stage 7 execution note (2026-10-13): license questions are deferred to sources/PR under the no-pause instruction. Native checks remain unchecked; see progress.md.

**Files:**
- Modify: `tools/assets/reinhard_sources.md` (created in Stage 1 Task 8; add a «Sounds» section), `docs/design/2026-10-11-reinhard-design.md` (§10.1: columns «Автор», «Лицензия»), `CREDITS.md` (section `## Reinhard van Astrea`)

- [x] Step 1: for each of the 14 URLs in §10.1 open the Freesound page; record author, sound name, license and version, check date, and the matching file name in the user's `sounds.zip`.
- [x] Step 2: any source not CC0/CC-BY → that sound keeps its placeholder; write the question with alternatives to the user.
- [x] Step 3: extend the sources file, the spec table and `CREDITS.md` (CC-BY: author, link, license, change note "trimmed / layered / normalized").
- [x] Step 4: commit `docs(reinhard): sound sources and licenses`.

### Task 2: `tools/sfx/reinhard.py` and the sound files

**Files:**
- Create: `tools/sfx/reinhard.py`, `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/reinhard/*.ogg.b64`, `test/hero/reinhard/ReinhardSoundsTest.java`
- Modify: `res/sounds.json` (`reinhard.*` events: `sounds` arrays and `stream` only)

**Interfaces:**
- CLI: `python3 tools/sfx/reinhard.py --src <folder> [--check]`. One table in the script maps output → source + operations (offsets are constants found by ear and written in the table):
  - `mode_restrained`, `mode_knight`, `mode_holy` — three short fragments (~1.5–2.5 s) of klankbeeld 398218, fade in 30 ms / out 400 ms.
  - `reid_choir` — the long good moment of the same file (~6 s, the draw event), fade out 1 s, `stream: true`.
  - `reid_unsheathe` (Vrymaa 770035), `reid_rumble` (CHallSmith 870684, loop-friendly trim).
  - `world_cut_silence` (reathance 503809), `world_cut_blast` = Alliare 825864 + CGEffex 93835 from its ~2 s point at about −8 dB (constant), `amix` then limiter −1 dBTP; `world_cut_tail` (hykenfreak 248150, `stream: true`).
  - `erase_crack` (Greg_Surr 554564), `erase_inhale` (hykenfreak 331620), `limbo_heartbeat` (daandraait 249716, seamless loop).
  - `sky_kick` (qubodup 182050), `stomp` (dingus23 275809), `cut_rain` (Robinhood76 378199), `sheath_ring` (519413 — only the ring cut out).
- Writes `.ogg`, then `.ogg.b64` into binassets; `--check` rebuilds into a temp dir and compares decoded PCM hashes (encoder builds differ between machines).
- `ReinhardSoundsTest`: `everySoundCredited` (sources file vs `sounds.json`), `noPlaceholdersLeft`, `everyFileExists`.

- [x] Step 1: write `ReinhardSoundsTest`; assertion RED (3/6 missing reference/file/stream). Python converter RED observed too.
- [x] Step 2a: implement the reproducible script, process 14 allowed outputs; source digests, two-pass normalization, format/peak gates and decoded-PCM `--check` pass.
- [ ] Step 2b: listen to every output (not performed; no-game/no-runtime instruction, artistic offsets unaccepted).
- [x] Step 3: update only the 14 allowed `sounds` arrays; all 33 event IDs unchanged. Blocked blast/rain and unspecified legacy cues explicitly retained.
- [x] Step 4: PASS; `./gradlew build` (b64 decode), 1215/1215 JVM tests + 9/9 Python tests.
- [x] Step 5: commit `feat(reinhard): final sounds`.


### Task 3: Mix and in-game listening

- [x] Available automatic portion: decoded format/duration/LUFS/peaks/PCM checks 14/14, reference file punch4 measured (not perceived runtime balance). No mix change without listening; no empty fix commit. Native Steps 1–2 below remain unchecked.

**Files:**
- Modify: `res/sounds.json` (`volume`, `pitch`, `attenuation_distance` only), script constants if a re-cut is needed

- [ ] Step 1: in game, both views, each event at 2, 16 and 64 blocks; Reid draw and World Cut also at 150 blocks.
- [ ] Step 2: checks: mode chord ≤ punch loudness; choir audible over the rumble; World Cut silence quiet, blast without clipping, tail until the dust settles; erasure crack → true silence → inhale; limbo heartbeat not tiring over 30 s; §10.2 sounds unchanged and still fit.
- [ ] Step 3: commit `fix(reinhard): sound mix` if anything changed.

### Task 4: Full acceptance pass and balance

- [x] Available automatic portion: full regression 1215/1215, NoHeroBranch 1/1, Python 9/9, checker 0 new / 4 known, Java17 build and diff-check. SESSION.md separates native NOT RUN from automatic evidence. No balance constants changed.

**Files:**
- Modify: `ReinhardRules` constants and their tests only if the pass fails; `SESSION.md` (results table)

- [ ] Step 1: dedicated server (`./gradlew :viltrumitecore:runServer`) + 2 clients; run every §11.1–§11.12 scenario from its stage plan in first and third person; record pass/fail with a note.
- [ ] Step 2: cross-hero matrix: vs Viltrumite (chop both ways), vs Regulus (Lion's Heart: Reid strike, Erasure, grab), vs Homelander (laser vs the 1 HP floor; §11.11 his chop unchanged), vs Iron Man (Hulkbuster, marks, Veronica).
- [ ] Step 3: balance only where the pass fails (spec §12 "баланс всех чисел"); each change = constant + test.
- [ ] Step 4: regression of the other four heroes: full test suite, `NoHeroBranchTest`, `python3 .agents/skills/add_hero/scripts/check_hero_resources.py --hero reinhard`, short manual run.
- [x] Step 5: commit `test(reinhard): acceptance pass` and `balance(reinhard): …` if any.

### Task 5: Release

**Files:**
- Modify: core version (minor +1), `.memory-bank/project.md`, `.agents/skills/add_hero/SKILL.md` (Reinhard as the example of a CC-BY sound hero, `WorldPause`, `CarveJobs`), `SESSION.md`

- [ ] Step 1: clean `./gradlew build`, all tests, dedicated server start, `git diff --check`.
- [ ] Step 2: update docs and the memory bank (final constants; spec §12 leftovers: "worthy souls" list stays open).
- [ ] Step 3: commit `chore(reinhard): release`; push `feat/reinhard-stage7`; PR «Рейнхард, этап 7: звуки и полировка».

Acceptance criteria **NOT closed**: native final confirmation of §11.1–§11.12 remains unchecked under the explicit no-game/no-server instruction.

## Risks and pitfalls

- The Freesound page at the check date is the license source of truth (old downloads may differ) — record the date.
- Single-pass `loudnorm` changes dynamics; use two-pass with the measured values stored in the table.
- Long sounds must be `"stream": true`, otherwise the client loads them fully on first play (hitch at the Reid draw).
