# Stage 7 — single independent whole-branch review

## Scope and method

- Base: `d478f1fb8a98c8fb856c2054e90b2b68191063ab`.
- Committed HEAD: `a5e6324bd1ac6966440a8de76c56b98ddbfc4af7`.
- Also reviewed the complete final Task 5 worktree and untracked `ReinhardReleaseTest.java`. All 30 entries in the supplied SHA-256 manifest matched before inspection and after the file checks.
- Read the review package/reviewer guidance, complete Stage 7 plan, overview, specification §§10–12 (and relevant §9 audio context), AGENTS, full animation-system skill, project memory, and **every Ruling**. Inspected the complete non-generated diff, all new/modified tests, converter, provenance records, credits, all 14 encoded assets, relevant unchanged playback callers, and the built release JAR.
- Generated debug-log rotations were explicitly excluded from source review, as instructed. No checkout/index/HEAD/branch/log mutation, Gradle, JUnit/unittest execution, native client/server/game, browser, subagent, second review seat, or re-review was performed.
- The only executable verification was an isolated file-only ffmpeg/ffprobe audit under a separate `/data` directory. It parsed converter constants without executing/importing repository code, independently applied the documented recipes twice, and compared decoded PCM with the committed assets. This is codec/file evidence, **not playback or native acceptance**.

**Finding totals:** 0 Critical, 4 Important, 1 Minor. The actual committed audio passed the file checks; the test findings below are concrete holes in the safeguards, not claims that the current audio is wrong.

## Strengths

1. **License exclusion is real, not a renamed or hidden substitution.** Saved live-page evidence agrees with the documented CC-BY-NC 4.0 classifications for 93835 and 378199. Neither appears in the converter's 11 used sources/14 recipes; Alliare is also not silently used alone. The blocked blast/rain arrays are identical to base, and neither blocked custom asset is packaged. References: `tools/sfx/reinhard.py:28–60,63–234`; `tools/assets/reinhard_sources.md:25–43`; `CREDITS.md:49–50`. [^https://freesound.org/people/CGEffex/sounds/93835] [^https://freesound.org/people/Robinhood76/sounds/378199/]

2. **The 33-ID accounting is correct in the actual branch.** Independent complete-object comparison found exactly 14 changed Reinhard entries, no ID additions/removals, no unrelated sound changes, and exact preservation of the 12 unspecified legacy entries, two blocked entries, and five §10.2 reuse entries. The `mode_holy_sword` → `mode_holy` and `sheath_strike` → `sheath_ring` distinctions are documented rather than registry renames. References: `tools/assets/reinhard_sources.md:45–47`; `viltrumitecore/src/main/resources/assets/viltrumitecore/sounds.json:641–941`.

3. **The converter really measures and performs a second normalization pass.** Source bytes are digest-checked before processing. Conditioning occurs before the first-pass measurement; measured values are checked against the pinned recipe and then explicitly supplied to the second loudnorm invocation. The four high-crest recipes have specific conditioning, not a blanket relaxation of the output ceiling. References: `tools/sfx/reinhard.py:244–272,310–336`; `.superpowers/sdd/2026-10-11-reinhard-stage7-sounds-polish/progress.md:26`.

4. **The final files, not just encoder settings, satisfy the numerical contract.** On the recorded ffmpeg 7.0.2-static toolchain, all 11 source digests matched; all 14 assets were mono/44,100 Hz/Vorbis with the intended durations, within ±1 LUFS, and below the −1 dB true/sample-peak ceilings. Maximum absolute loudness error was 0.73 LUFS; highest true peak was −1.87 dBTP and highest sample peak approximately −1.872 dBFS. All pinned first-pass fields matched exactly at their stored precision. Both independent renders of every recipe matched each other, the committed PCM, and the Task 3 PCM log. This supports same-toolchain reproducibility without claiming arbitrary cross-encoder identity. References: `tools/sfx/reinhard.py:284–307,319–350`; isolated `file-audit.json` evidence.

5. **The existing binary-asset build path works for this release.** The built `reforgedcodex-core-1.31.0.jar` contains exactly the 14 expected custom Ogg files, byte-for-byte equal to the decoded committed b64 assets. The long choir/rumble/tail entries use streaming. Source gameplay, balance constants, protocol, flight, renderers and mixins are unchanged by Stage 7. References: `viltrumitecore/build.gradle:79–112`; `ReinhardSoundsTest.java:73–75`.

6. **The recorded build/TDD evidence is credible and correctly bounded.** The supplied sound RED log shows genuine assertion failures (3/6), the Python logs distinguish missing-script RED from missing-output RED and 9-test GREEN, and the release RED is specifically 1.31.0 versus 1.30.0. The now-completed clean-build log contains 1,216 passing invocations, matching the final gate summary's core 1,204 + flight 12; selected core coverage is 469, checker 0 new/4 known. The completed Task 5 logs also confirm Python 9/9, `--check` 14/14, and JAR verification of all 14 assets/33 IDs/manifest and mods.toml version 1.31.0. I read these records rather than rerunning tests. My read-only `git diff --check` also passed.

7. **Native-check honesty is unusually good.** The plan, ledger, session and skill distinguish file measurement/pure/source tests from perceived mix, runtime silence, dedicated startup, GLSL, persistence and FPS/TPS. They do not falsely close §11 acceptance or claim artistic offsets were heard. The historical Stage 6 minimum-version gate plus the new exact Stage 7 release gate is a justified release-policy adjustment. References: `SESSION.md:276–319`; plan `:64–104`; `WorldErasureDocumentationTest.java:9`; `ReinhardReleaseTest.java:10–13`.

## Issues

### Critical (Must Fix)

**None found in the reviewed scope.** This is not a certification of the forbidden native checks.

### Important (Should Fix)

#### I1 — The release JAR does not carry the new CC-BY attribution

- **Files/lines:** `viltrumitecore/build.gradle:72–77,100–112`; `CREDITS.md:39–44`; supporting existing metadata `viltrumitecore/src/main/resources/META-INF/mods.toml:12,25–32`.
- **Problem:** The repository's new CC-BY credits are good, but neither `CREDITS.md`, the source ledger, equivalent third-party notices, nor a discoverable credit/license URL is included in the inspected 1.31.0 JAR. Its active metadata still says only “All Rights Reserved,” with no active homepage/credits fields. Vorbis comments contain encoder metadata, not the authors/license notices. Thus the binary artifact contains the choir, tail, inhale and stomp recordings without carrying their attribution.
- **Effect:** A user or modpack distributor receiving only the JAR loses the supplied author/source/license/change information. Repository-only credit is not a robust release mechanism for newly introduced attribution-required assets; klankbeeld explicitly requests attribution to both the author and Freesound in the work. This is an artifact-distribution defect, not a claim that NC audio slipped in, and not a comprehensive legal opinion. [^https://freesound.org/people/klankbeeld/sounds/398218/]
- **Fix:** Package a clearly named Reinhard third-party-notices resource containing the existing credits, source/license links and modification notices (copying the maintained credits/source ledger during resource processing is fine). Preserve the code's license if desired, but make the audio exception discoverable. A stable attribution URL in release presentation is useful too; it should not be the only untested assumption about a standalone JAR.
- **Test-first target:** Assert that the built/resource artifact actually includes the notices and the four used CC-BY source links/three authors, rather than only asserting that a repository file contains names. No game/server run is needed.

#### I2 — Sound tests check only the first array item and do not pin the 12 legacy contents

- **File/lines:** `viltrumitecore/src/test/java/dev/baranhan/viltrumitecore/hero/reinhard/ReinhardSoundsTest.java:31–43,65–79`.
- **Problem:** `entry()` always selects `sounds[0]`. Consequently `noPlaceholdersLeft`, the blocked-placeholder test and §10.2 test ignore all additional choices. The 12 unspecified legacy events are checked for **presence**, not their complete arrays or attributes. The count/known-ID combination does protect the ID set; it does not protect the content as the comments imply.
- **Concrete escape:** Append a second valid vanilla-event choice to any “final” array: its first item remains the expected custom file, so these assertions still pass. Or change `reinhard.air_step`'s reference/pitch/volume while retaining its ID: none of these sound tests compare its content with base. The Python asset tests do not read `sounds.json` and cannot close this hole.
- **Effect:** A future edit can reintroduce randomly selected placeholders, unresolved references, or unapproved changes to deliberately preserved cues while the advertised no-placeholder/legacy gates remain green. The current branch's 19 exception arrays really are unchanged; the defect is failure to enforce that contract.
- **Fix:** Validate every element of every actual array. Pin all 19 preserved exception entries' full relevant content to an explicit baseline fixture, separated into blocked/legacy/reuse categories; require the 14 selected final arrays to contain only their approved file mapping and intended stream attributes. Resolve every actual file reference, not just values of the independently maintained FINAL map.
- **Test-first target:** Negative fixtures for an extra vanilla choice after index 0, a missing extra file, and changed content/attributes in one of the 12 legacy entries; retain positive tests for the exact approved exceptions.

#### I3 — `everySoundCredited` does not establish per-output provenance or credit completeness

- **File/lines:** `viltrumitecore/src/test/java/dev/baranhan/viltrumitecore/hero/reinhard/ReinhardSoundsTest.java:53–64`; provenance contract at `tools/assets/reinhard_sources.md:23–38,55–70`.
- **Problem:** The test merely searches the entire source document for output-name substrings and the entire credits/spec documents for three author substrings. It never binds an output to its source ID, exact input/digest, URL, author and allowed license, nor binds each used CC-BY source link/license to its credit entry.
- **Concrete escape:** Delete the complete sound-source table's 14 data rows (`reinhard_sources.md:25–38`) but leave the processed-output table and exclusion prose. Every assertion in `everySoundCredited` still has its required substring. Similarly, an author's name can remain while their actual source/license links disappear. The malformed spec table in M1 also passes this gate.
- **Effect:** The gate can report success after losing the information that makes the new licensed assets auditable and distributable. It does not fulfill the plan's “every committed sound has a source/author/license row” safeguard, although inspection confirms the current source ledger and repository credits contain the correct information.
- **Fix:** Parse/bind structured rows (or one canonical provenance manifest) and join every referenced output to its recipe/source and corresponding spec record. Require nonempty author, exact source URL, license/version, digest, and the linked CC-BY credit/change entry for every used CC-BY source. Keep excluded sources explicitly separate from used sources.
- **Test-first target:** Missing source row, mismatched output→source association, missing/wrong URL or license, missing credit link, and malformed/blank author/license cells must fail independently of unrelated occurrences elsewhere in a document.

#### I4 — The Python suite never exercises PCM-mismatch or pinned-measurement-drift rejection

- **File/lines:** `tools/sfx/tests/test_reinhard.py:19–89`, especially `:71–89`; protected implementation `tools/sfx/reinhard.py:319–330,339–350`.
- **Problem:** All nine tests inspect metadata, validate missing/changed inputs, invoke help, inspect the existing good outputs, or exercise only the missing-committed-file branch. None calls `build()` or checks a valid-but-different committed/rebuilt PCM pair. No assertion exercises first-pass drift rejection. The loudness test delegates its LUFS decision to the same `inspect()` implementation without independently asserting measured integrated loudness.
- **Concrete escape:** Removing the PCM inequality rejection at converter `:347–348`, or the measured-versus-pinned rejection at `:323–325`, leaves every current Python test satisfied. The ordinary successful `--check` on matching files would not detect deletion of its own mismatch rejection either. Dropping the LUFS rejection also goes undetected on the current positive-only fixture set.
- **Effect:** Maintainers have no failing regression test for the integrity checks that distinguish reproducible normalization from unchecked source/filter drift. This matters because the source archive is deliberately external and these safeguards are the evidence chain for future regeneration. Current implementation and both independent renders were correct; this finding concerns durable regression protection.
- **Fix:** Add adversarial temporary-file cases for valid different PCM (must reject) and identical PCM in a different container (must accept), and targeted drift cases outside/inside the documented tolerance that exercise the real build/validation path. Add wrong-level and wrong-format/peak fixtures against the rejection paths, with an independent LUFS assertion for positive committed assets. Existing processed assets can supply small file fixtures; raw archives need not be committed.
- **Test-first target:** Show RED when each rejection is bypassed, not merely when the script or input file is absent. Preserve the real file-level ffmpeg checks and both peak ceilings.

**Adversarial examples above were established by reading the exact assertion/control flow; no mutated checkout or mutation-test run was performed.**

### Minor (Nice to Have)

#### M1 — All sound-source rows in specification §10.1 have one missing table delimiter

- **File/lines:** `docs/design/2026-10-11-reinhard-design.md:238–254`.
- **Problem:** The header declares four columns, but all 15 data rows contain only three cells. The author is appended to the sound cell without a `|`, so the license link renders under “Автор” and the “Лицензия” cell is empty.
- **Effect:** The required source/author/license table is misleading to readers and cannot be reliably consumed as its declared schema. The separate source ledger and credits retain the information, so this is not evidence of an uncredited/NC current asset.
- **Fix/test:** Insert the delimiter before each author; assert exactly four nonempty cells for each source row, with an author cell and correct license URL in the final cell. This can naturally be covered while fixing I3.

## Plan alignment and Ruling dispositions

| Decision/deviation | Review disposition |
|---|---|
| Exclude both live NC inputs; retain exact blast/rain; do not use Alliare alone | Correct. Verified in recipe/input set, base comparison and packaged outputs; final replacement approval remains pending. |
| Retain 12 unspecified legacy entries and five §10.2 reuse entries | Correct explicit accounting, not “all sounds final.” Actual objects are unchanged; I2 asks tests to enforce the same result. |
| Record candidates/questions instead of interrupting continuous execution | Consistent with the recorded no-pause instruction; proposals are not approval or substitution. |
| Use objective offsets rather than claiming ear-selected trims | Honest initial implementation; artistic acceptance remains open. |
| Pre-condition four short high-crest clips before pinned measurement | Technically justified and reproduced exactly. Decoded gates were not weakened; audible dynamics remain unjudged. |
| No runtime/game/server/balance/flight changes or launches | Honored. Automatic work is partial acceptance, not closure of Tasks 3–5 native steps. |
| Update obsolete Warden assertion; historical Stage 6 version becomes a minimum, Stage 7 exact version added | Appropriate: final resource mapping, locales and 24-tick/50-bpm assertions remain intact; core 1.31.0 is pinned without changing flight. |
| Separate package-inclusive selected counts from full counts | Correct. Historical 1,215/468 records remain historical; the final available release records are 1,216/469. |
| Preserve durable ledger, keep generated rotations out of publication, use remote text-commit receipts | Reasonable process choices. Checkout source manifest stayed stable; final publication/cleanup is executor work, not inferred from local logs. |
| Review stable HEAD plus final worktree in one seat before one release/fix commit | Followed. No additional reviewer/subagent/re-review was used or requested. |

## Recommendations

Use the executor's **one test-first correction pass** for I1–I4 and the inexpensive table repair. Do not address these by weakening decoded gates, silently approving an alternative recording, changing gameplay constants, or treating native acceptance as a unit-test assertion. After that pass, the executor should run the authorized existing release/file gates and update final counts/publication notes. Keep the owner decisions and native checklist explicitly open; no re-review is requested.

## Declined to judge

Each item below was considered and intentionally left ungraded for the stated reason. They are retained acceptance boundaries, not silently dropped requirements.

1. **Excerpt/music/ring creative quality, including whether sheath_ring excludes scrape** — no listening performed; objective trim constants and file meters cannot establish artistic fit.
2. **Perceptually seamless rumble/heartbeat looping and 30-second fatigue** — endpoint metrics/tick-period tests are not playback. The logical 24-tick/50-bpm cadence remains source-verified, not heard.
3. **Mode-chord versus punch perceived loudness, choir over rumble, scene-tail balance** — file LUFS at nominal gain omits caller gains/pitches, category settings, overlap and spatialization; native mix checks are forbidden.
4. **World Cut blast clipping/finished licensed blast mix** — the specified layered mix is license-blocked and unimplemented; no final blast exists to measure, and runtime playback of the retained vanilla event was not run.
5. **Erasure's genuine runtime silence** — `ErasureScene` ducks MASTER at the timeline boundary with exemptions; no zero-sample file or decoded-file result proves SoundEngine/mixin/category behavior in game.
6. **World Cut's quiet interval** — the committed `world_cut_silence` is an intentional recorded cue, not a silent file; native ducking and its overlap with other sounds were not heard.
7. **All 33 events' camera/distance/category/subtitle behavior at 2/16/64/150 blocks** — no first-/third-person caster/viewer clients were launched.
8. **§11.1 selection UI, replacement body/FP arms, handedness, occupied hands and invisibility** — native renderer/input inspection is forbidden; no Stage 7 implementation change supplies new proof.
9. **§11.2 actual damage sources, 1-HP floor, regeneration, knockback, /kill and void rescue** — existing pure/source regressions are not Forge event/world behavior; no gameplay session.
10. **§11.3 divine command, Adaptation → Phoenix → death, repeat types and persistent cooldowns** — no native command/event/lifecycle execution.
11. **§11.4 all eight blessings in every mode** — automatic regressions do not replace the prescribed in-game scenario matrix.
12. **§11.5 MMB cycle/refusal/pick-block, cooldown and loadout/hero-change input behavior** — no native input/network checks.
13. **§11.6 destruction scaling, mobGriefing/bedrock/borders/build heights/unloaded chunks** — source rules are unchanged; no world execution or terrain inspection.
14. **§11.7 each ability's two-view poses/VFX, prediction/reconciliation/lag and cleanup** — no native clients, and extending the review to certify earlier-stage runtime was not authorized.
15. **§11.8 Reid timing, sky/weather/shake/lifts, overlap ownership, far sound/chat and auto-sheathe** — unchanged callers can be read but not used to claim a native scene was accepted.
16. **§11.9 actual trench/wave geometry, once-hit combat, detached-caster continuation, fluid/light/drop behavior** — no live world/server scenario; pure budgets and geometry are insufficient.
17. **§11.9 TPS≥15/FPS and terrain/performance matrix, concurrent cuts/spikes** — no profiling/game/server; throughput cannot be inferred from test counts.
18. **§11.10 server-wide/cross-dimension freeze, one-cast/refusal semantics, frozen verdict and missing/cancelled targets** — no multiplayer/event/packet scenario.
19. **§11.10 silent mob/boss/dragon removal, bossbar/respawn behavior, first-dragon lack of portal/gateway/egg** — earlier-stage limitation is honestly documented, but native outcomes were not exercised; not silently treated as fixed.
20. **§11.10 inventory/armor/offhand/XP loss, ender/external storage preservation, menu/cursor/crafting edge cases** — no destructive native inventory test; source assertions are not transactional proof.
21. **§11.10 same-entity ERASED/fresh-body/Evangelium cleanup across all five heroes** — unchanged existing lifecycle tests are not proof of real server-player event ordering.
22. **§11.10 logout/rejoin/restart/pending-strip recovery, NoGravity/NoAI originals and foreign pause/control/carry ownership** — no server, new JVM or save/restart test.
23. **§11.10 limbo isolation, chat/F1/menu/five lines, safe-spawn return and missing-dimension rescue** — no native dimension/UI/teleport test.
24. **§11.10 integrated pause/watchdog, lost/stale END, late join and cross-dimension catch-up** — no native networking/pause lifecycle; codec/pure clock tests alone do not close it.
25. **§11.11 Homelander Chop and other heroes' actual visual/audio parity** — relevant sound objects and Stage 7 runtime sources are unchanged, but no visual/playback regression session.
26. **§11.12 dedicated startup, common-side classloading, Mixin targets/refmaps and native data codecs in old/new worlds** — the clean build/reobf/JVM counts do not constitute startup; explicitly forbidden.
27. **Cross-hero combat matrix, especially Lion's Heart, legacy chops, laser and Hulkbuster/marks/Veronica** — no native multiplayer/combat checks; no balance constants were altered without measurements.
28. **GPU GLSL compilation, Oculus/shader ordering, silhouettes/cutout/model fallback, idle image parity and actual render-state restoration** — source/codec inspection is not a GPU or shader-pack test.
29. **/reload, F3+T, resource/level unload and runtime duck/FOV/freeze/cache leak behavior** — no running native client/server; file checks cannot exercise those hooks.
30. **Final balance and worthy-souls policy** — explicit §12 deferrals; this branch intentionally supplies no native balance evidence or new product decision.
31. **Which permitted blast/rain alternatives or 12 legacy source selections should be approved** — an owner creative/product choice, not a code-review decision; no silent approval.
32. **Reproducibility across arbitrary ffmpeg/Vorbis builds, architectures and floating-point environments** — two matching renders prove the documented current toolchain only; PCM comparison does not make different encoders intrinsically identical.
33. **Complete legal title/performer/composition rights and future release-page attribution presentation** — engineering review verified supplied page licenses, source hashes, exclusions and visible current attribution, not legal chain of title or an unpublished future page. I1 is the concrete current artifact gap, not legal certification.
34. **Remote publication, PR wording/required checks, final release/fix commit, push race resolution and generated-log cleanup** — Task 5 publication is still executor work and was not contacted or mutated in this read-only review.
35. **Unchanged prior-stage metadata/dev-command limitations and broad earlier-stage implementation re-audit** — outside this Stage 7 diff; retain their existing journal/PR caveats rather than suggesting they were re-reviewed or repaired here.

## Assessment

**Ready to merge? With fixes.**

**Reasoning:** The scoped audio implementation is reproducible on the supplied toolchain, excludes the prohibited inputs, preserves the existing sound/hero contracts in the actual branch, and has credible green build evidence. Fix release attribution and the material acceptance-test holes before merging it as an explicitly incomplete code/content release; it is **not** a sign-off that full Stage 7 polish or §11.1–§11.12 native acceptance is complete.

One independent whole-branch review completed. Findings are handed to the executor for re-grading and the already-authorized single test-first fix pass; **no re-review requested**.
