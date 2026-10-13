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
Task 3: automated portion complete; native listening/acceptance NOT complete (BASE a3d364b). --check 14/14 PCM, ffprobe 14/14 mono/44100/Vorbis durations, LUFS ±1/peaks ≤−1, Python 9/9. File punch4 I=-13.81 LUFS; mode files −18 LUFS are lower at unity gain. No runtime volume/attenuation/pitch change, no fix(reinhard): sound mix commit (nothing to mix without listening). File comparison is NOT perceived/runtime balance. Journal carried into Task 4 commit.
Task 4: automated portion complete; native acceptance NOT complete (BASE a3d364b; commit test(reinhard): acceptance pass; receipt in git/PR). NoHeroBranchTest 1/1 unchanged allowlist; full JVM 1215/1215 (core 1203 + flight 12), selected 468/468; Python 9/9; checker 0 new / 4 known; diff clean. No balance/gameplay/flight changes. Native table and full checklist in SESSION.md.
Task 5: pending.

## Limits and manual gaps
All §11.1–§11.12 native checks, both cameras, dedicated startup, cross-hero matrix, F3+T, shader packs, audio listening and FPS/TPS are unchecked. Complete checklist will be in SESSION.md and PR. File loudness/peak measurements are not playback, runtime silence or acceptance.

## Task 3 decoded-file audio measurements (not listening)

ffmpeg 7.0.2-static, Vorbis q5, mono 44100 Hz. Values are post-decode. Every file passes ±1 LUFS and ≤−1 dBTP/dBFS gates. `world_cut_silence` is a recorded pre-cut cue, NOT a silent file; real erasure silence is a runtime duck/timeline property and remains unchecked.

| File | Seconds | LUFS / target | True peak dBTP | Sample peak dBFS |
|---|---|---|---|---|
| mode_restrained | 1.800 | -18.01 / -18 | -4.83 | -4.849 |
| mode_knight | 2.000 | -18.01 / -18 | -5.16 | -5.161 |
| mode_holy | 2.200 | -17.98 / -18 | -3.38 | -3.382 |
| reid_choir | 6.000 | -20.02 / -20 | -4.93 | -4.931 |
| reid_unsheathe | 1.879 | -14.10 / -14 | -2.17 | -2.415 |
| reid_rumble | 6.000 | -20.04 / -20 | -1.87 | -1.872 |
| world_cut_silence | 3.000 | -20.33 / -20 | -7.10 | -7.099 |
| world_cut_tail | 8.000 | -20.73 / -20 | -2.58 | -2.578 |
| erase_crack | 1.500 | -14.02 / -14 | -3.89 | -4.071 |
| erase_inhale | 2.300 | -20.24 / -20 | -2.17 | -2.177 |
| limbo_heartbeat | 1.200 | -19.99 / -20 | -4.30 | -4.303 |
| sky_kick | 2.500 | -14.12 / -14 | -2.61 | -2.649 |
| stomp | 3.000 | -14.04 / -14 | -4.09 | -4.091 |
| sheath_ring | 1.100 | -14.10 / -14 | -2.06 | -2.781 |

Loop endpoint deltas: reid_rumble 0.000534 amplitude, limbo_heartbeat 0.000000106 amplitude (decoded normalized PCM). These measure click risk only, not seamless perceptual looping. Heartbeat file 1.2 s fits the existing 24-tick logical pulse; 50 bpm is unchanged. The licensed blast mix is blocked, so no claim of final-blast peak measurement.

### Полный ручной чек-лист — НЕ ВЫПОЛНЕН

Все игровые пункты проверить **от первого И третьего лица**, кастером и наблюдателями; зелёные pure/source tests не закрывают эти пункты.

- [ ] §11.1: четвёртая кнопка выбора расы; полная модель, FP-руки, ножны на поясе; оба вида/обе руки/занятые слоты/невидимость.
- [ ] §11.2: меч, Unibeam, %maxHP, clean, void/generic_kill не ниже 1 HP, 99.9% сопротивление; /kill действительно убивает, rescue из void, реген 1 HP/с, нет fall damage/knockback.
- [ ] §11.3: dev divine проходит Adaptation → Phoenix → смерть; три одинаковых типа, смена типа, offline/hero-change Phoenix cooldown.
- [ ] §11.4: все восемь благословений во всех трёх режимах: Parry pose/spark, Phoenix safe point, Arrow Ward/catch/reflect priority, пять air steps/reset/superjump, Wind Reading invisible/back, Detox, Mana Absorption r24 ×0.5, Adaptation.
- [ ] §11.5: MMB по кругу, отдельные звуки/страницы, без позы и pick-block; отказ/3 s cooldown, hero-change/loadout migration.
- [ ] §11.6: Restrained не ломает; Knight умеренно; Holy масштабно; mobGriefing false/bedrock/world border/build limits/unloaded chunks без force-load.
- [ ] §11.7: каждая способность §6–7 со своей анимацией/VFX в обоих видах, grounded/air/crouch, left/right hand, occupied hands; предсказание/отказ/lag, control/death/logout/hero/dimension cleanup.
- [ ] §11.8: Reid 6 s, shake/sky/weather/duck, lift 2–3 blocks r96 через общие leases, crack ring/pillar/funnel/flash/wave/chat; aura, sheathe 1 s, autoreturn 60 s без боя/при выходе Holy; overlapping draws/control/foreign gravity.
- [ ] §11.9: World Cut swing → 3 s quiet/white line → 200×17×45–50 trench wave; once-hit mortals, divine/absolute immune; budgets, detached caster death/logout/dimension, adaptive queue/no drops/XP/fluid cascade.
- [ ] §11.9 performance: render distance 12 flat/plains/forest/ocean/mountain, /forge tps каждую секунду 10 s после blast, все ≥15; два cuts/spike; native FPS и свет/жидкости. Pure budget не является TPS measurement.
- [ ] §11.10: erasure 7.5 s во всех измерениях; freeze всех non-caster, nearby Enemy; one cast/spam/second cast/no cooldown refusal; crack → real silence → inhale, verdict frozen, missing target at tick90, cancel на 89/90/149/150.
- [ ] §11.10 entities: mob без loot/XP/message, Wither bossbar, dragon quiet completion/crystal respawn. **Первый erased dragon не создаёт exit portal/gateway/egg — заранее обеспечить иной выход из End.**
- [ ] §11.10 player: inventory/armor/offhand/XP навсегда исчезли, ender chest/external storage сохранены; нет death message/DEATHS/drops; same-entity ERASED mid-actions всех пяти героев, fresh body/Evangelium return.
- [ ] §11.10 persistence: limbo 600 онлайн ticks/30 s, logout/rejoin и новый JVM/restart в середине/между pendingStrip и transfer; исходные NoGravity/NoAI false/true, participant logout BEFORE vanilla save, foreign pause/PULL/STASIS/legacy carry ownership/no terrain grind.
- [ ] §11.10 isolation/return: chat/F1/menu, black fog/no sky/no hands/HUD, five lines, no movement/flight/use/damage/tp/portal; safe shared spawn не bed/anchor/platform, full health/hunger/gravity/flight reset, missing-dimension rescue, pause↔limbo overlap.
- [ ] §11.10 menu/watchdog: full inventory/cursor/2×2/3×3 crafting и external chest на tick90 и pending-strip resume; modded menus (>36 returned nonmerge inputs) отдельно; long integrated pause/resume, lost-END timeout/late join/catch-up/stale-END.
- [ ] §11.11: Homelander Lethal Chop и прежние poses/VFX/sounds остальных героев визуально не изменились.
- [ ] §11.12: native dedicated startup/common-side classloading/Mixin/refmap/data codecs в old/new world, два clients matching core protocol2; /reload, F3+T, restart. Server/client НЕ запускались.
- [ ] Cross-hero: Viltrumite/Homelander legacy chop в обе стороны; Regulus Lion's Heart vs Reid strike/Erasure/grab (никакого пробития); Homelander laser vs 1 HP floor + его chop; Iron Man marks/Hulkbuster/Veronica/roster/cleanup.
- [ ] Все 33 sound events: оба вида на 2/16/64 blocks; Reid draw/World Cut также 150; caster/viewer/global/local категории и subtitles.
- [ ] Mix/listening: mode chords ≤ punch по восприятию в игре; choir слышен поверх rumble; World Cut quiet/blast не клиппит/tail до оседания dust; erasure crack → **настоящая runtime тишина** → inhale; heartbeat 30 s не утомляет/не обрывается/50 bpm; §10.2 подходят и не изменились.
- [ ] Выбранные начальные offsets: три музыкальных mode excerpts, «хороший» choir, только ring без scrape; loop rumble/heartbeat без слышимого стыка. Числа endpoint/peaks это не прослушивание.
- [ ] Oculus/shader packs/sonic priority, GPU GLSL, image idle parity, mesh/cutout/fallback/modded renderer silhouette/shards, F3+T/reload/level unload без duck/FOV/freeze leaks.
- [ ] Владелец выбирает CC0/CC-BY альтернативы для blast/rain и источники для 12 дополнительных legacy cues; варианты в sources, никаких silent swaps/NC excerpts.
- [ ] Реальная итоговая приёмка §11.1–§11.12 и баланс после измерений; worthy souls list остаётся открытым.
