# SDD ledger — plan: docs/plans/2026-10-11-reinhard-stage7-sounds-polish.md

Branch: feat/reinhard-stage7 from feat/reinhard-stage6; BASE d478f1fb8a98c8fb856c2054e90b2b68191063ab. Stage 6 reported 1209 tests (core 1197 + flight 12); fresh Java 17 build confirmed 1209/1209. No prior Stage 7 ledger or branch existed.

## Pre-flight shared interfaces
| Tasks | Producer → consumer | Finding |
|---|---|---|
| 1 → 2 | 14 licenses → converter inputs | 93835/378199 are NC; exclude them and retain blocked event placeholders. |
| 2 → 3 | final samples → mix | Measurements can prove format/level, not audibility/distance/camera/runtime silence. |
| 2 → 4 | preserved event IDs → regressions | §10.1 names 16 outputs, but 33 event IDs exist; unresolved legacy cues need explicit accounting. |
| 4 → 5 | acceptance → release docs | Native pass forbidden; release code/content is not accepted gameplay. Stage 6 exact-version test needs a historical minimum gate. |

## Rulings
- Ruling: Use upstream superpowers:executing-plans and its TDD/verification skills; helper scripts are not installed locally, so use equivalent per-task briefs, BASE, ledger and logged gates in this isolated fresh clone — task sequence and proof remain unchanged — cost if wrong: bookkeeping differs from upstream helpers.
- Ruling: Preserve the committed Stage 7 journal instead of deleting its workspace — user requires durable progress.md — cost if wrong: extra documentation only.
- Ruling: Do not run runClient, runServer, dedicated server or game; no balance/gameplay constants change — explicit user instruction overrides Tasks 3–5 native steps — cost if wrong: audio/runtime/mixin/network/TPS/FPS defects remain unproven until manual acceptance.
- Ruling: Live pages show CGEffex 93835 and Robinhood76 378199 are CC-BY-NC 4.0; exclude both, preserve exact world_cut_blast/cut_rain placeholders, and do not substitute Alliare alone — license rule outranks final-sounds goal — cost if wrong: these two effects remain unfinished until owner chooses an allowed replacement.
- Ruling: Record replacement questions/candidates in sources and PR, not an interactive pause — user requests continuous work/no questions — cost if wrong: license-blocked choices remain unresolved, not silently approved.
- Ruling: Preserve unspecified legacy cues and §10.2 unchanged; replace only the selected, allowed §10.1 events — approved sources do not cover all 33 IDs and no silent swaps are allowed — cost if wrong: twelve extra legacy events are not final custom audio; explicitly deferred.
- Ruling: Use objective initial trim constants, not claimed ear-selected offsets; no playback is available/requested — measurements and creative fit are distinct — cost if wrong: musical excerpts/loop/ring cuts may need recut after manual listening.
- Ruling: Use GitHub MCP text commits (existing environment has no git push credentials), fetch and compare remote HEAD before each push, then fetch/fast-forward local branch to publication receipt — same planned commit boundaries, no credential invention — cost if wrong: concurrent remote changes require reconciliation before any publication.

- Ruling: Selected *Reinhard* plus *reinhard* filters execute 462 baseline invocations (459 package tests plus 3 related tests outside the packages); report selected and full counts separately — case/name-only filtering covered only 92 — cost if wrong: no omitted regressions because the full 1209 suite also runs.
- Ruling: Restore tracked debug-*.log.gz and delete only freshly generated rotations before publication — registry bootstrap tests rotate logs already accidentally tracked on base — cost if wrong: generated diagnostics stay in local gate logs, not in source commits.

- Ruling (Task 2): Short high-crest crack/heartbeat/stomp/ring need recipe-specific peak limiting before two-pass loudnorm; pinned measurements are taken after this step, decoded LUFS stays within ±1 and TP/sample peaks ≤−1 — raw crack linear gain would exceed 0 dBTP (observed -17.37 instead of -14) — cost if wrong: transient timbre/dynamics require manual listening; no gameplay or runtime mixing tuned.
- Ruling (Task 2): Update the historical limbo sound assertion from Warden to the approved final file, retaining all 11 locale and 24-tick/50-bpm checks — selected gate exposed an obsolete placeholder expectation — cost if wrong: that test no longer pins a temporary vanilla timbre, replaced by final resource/decoded-audio tests.

## Tasks
Task 1: complete (BASE d478f1f; commit docs(reinhard): sound sources and licenses; receipt in git/PR). Live 14/14 pages retrieved; 12 allowed-license inputs, 2 excluded. 11 distinct used sources; 14 planned allowed outputs. Gates: exact *Reinhard* name filter 92/92; full build 1209/1209 (core 1197 + flight 12), checker 0 new / 4 known, diff clean. Package-inclusive Reinhard gate additionally run before publication (462 invocations: 459 package tests + 3 outside packages).
Task 2: complete (BASE d245602; commit feat(reinhard): final sounds; receipt in git/PR). Java sound tests assertion RED: 3/6 fail (missing file/reference/stream); 3 existing license/reuse checks already pass. Python converter RED: 8/8 missing-script assertions; extra committed-audio RED: missing output. Two-pass first render failed erase_crack -17.37 LUFS vs -14; diagnostic showed high crest prevented the simultaneous I/TP goal. Peak-conditioned recipe first-pass values remeasured; 14 outputs pass decoded gates and --check. Python GREEN 9/9. First selected suite found historical LimboOverlayTest still asserting the replaced Warden placeholder; expectation updated to the final resource, all locale/timing assertions preserved. Final gates GREEN: selected 468/468 (465 package + 3 related), full 1215/1215 (core 1203 + flight 12), checker 0 new / 4 known, diff clean. 14 b64 files (441364 text bytes), 33 IDs preserved; 14 final files / 2 blocked / 12 unresolved legacy / 5 §10.2 reuse events.
Task 3: pending (native listening prohibited).
Task 4: pending (native acceptance prohibited).
Task 5: pending.

## Limits and manual gaps
All §11.1–§11.12 native checks, both cameras, dedicated startup, cross-hero matrix, F3+T, shader packs, audio listening and FPS/TPS are unchecked. Complete checklist will be in SESSION.md and PR. File loudness/peak measurements are not playback, runtime silence or acceptance.
