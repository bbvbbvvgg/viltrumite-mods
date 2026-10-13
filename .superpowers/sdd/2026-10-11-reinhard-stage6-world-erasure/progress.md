# SDD ledger — plan: docs/plans/2026-10-11-reinhard-stage6-world-erasure.md

Branch: feat/reinhard-stage6 from feat/reinhard-stage5; BASE f93f27fa2634268c9686250f4ad2d0892636f108. Stage 5: 1000 tests GREEN, core 1.29.0.

## Pre-flight shared interfaces
| Tasks | Producer → consumer | Finding |
|---|---|---|
| 1 → 3,6 | WorldPause session → timeline / end / join scene callbacks | Add a hero-neutral listener; tick at server END, not per player/level. |
| 1 → 4 | FREEZE flags → target dimension transfer | Release old-dimension pause records and restore flags before a new acquire; never re-save frozen flags. |
| 2 → 3,4 | ERASED + freshBody → remove / return | Same entity needs explicit transient reset; kept persistent fields audited per hero. |
| 3 → 4 | Player erasure → Limbo.send | Player transfer hook introduced in Task 3; wired in Task 4 before shipping slot 6. |
| 3 → 5,6 | Frozen verdict, start/age packet → client timeline | One verdict and server age; client receipt clock avoids per-dimension /time offsets. |
| 5 → 6 | ErasureUniforms → post scene | One dash_impact chain; flight priority handled through an existing dependency direction. |
| 3,6 → 8 | Action → complete clip / first person | Keep SHIPPED_STAGE=5 until Task 8; no incomplete ability offered. |
| 4 → 7 | Persistent ticksLeft + START/END → overlay | Online ticks only; rejoin resumes text and heartbeat. |

## Rulings
- Ruling: Continue the Stage 5 workflow (one task/commit, immediate push, TDD and all four gates) for Stage 6 — user asked to start the next stage — cost if wrong: workflow preference only.
- Ruling: No runClient, runServer, dedicated server or in-game checks — user prohibition carries forward; all runtime acceptance stays unchecked — cost if wrong: rendering/mixin/dimension startup defects can remain until manual acceptance.
- Ruling: Preserve this committed progress journal instead of deleting the skill workspace at the end — user explicitly requires a durable journal; keep scratch logs outside git — cost if wrong: one documentation directory retained.
- Ruling: The supplied skill has no installed task-start/task-done scripts; use extracted per-task briefs, direct commands, and this ledger with equivalent evidence — cost if wrong: bookkeeping only.
- Ruling: Stage 4 CarveJobs/CarveBudget/LevelTickPhases rules remain unchanged; Stage 6 never carves blocks, WorldPause ticks at server END — cost if wrong: pause order must be rechecked in runtime.
- Ruling: Hold SHIPPED_STAGE at 5 through Tasks 1–7; enable slot 6 only with Task 8's complete clip and limbo wiring — cost if wrong: stage-6 ability is hidden in intermediate commits.

- Ruling (Task 1): Generic pause has configurable protection/radius (neutral defaults true/32); erasure passes ReinhardRules explicitly — shared code cannot depend on a concrete hero — cost if wrong: defaults and rules must stay pinned equal.
- Ruling (Task 1): Control snapshot adds effect UUID and core protocol becomes 2 with strict equality — old clients cannot decode the new wire shape safely — cost if wrong: mixed-version multiplayer refuses connection; use matching core builds.
- Ruling (Task 1): Release of an unreachable target defers saved flags; transfer releases before reacquisition — otherwise offline/dimension changes could capture noGravity=true forever — cost if wrong: rejoin flag restoration needs runtime verification.
- Ruling (Task 1): Tests exercise captured records and the real refresh plus a deterministic backend/session, not a live Mob/ServerPlayer — no game/server allowed — cost if wrong: runtime event wiring/flag restoration remains a manual gate.

- Ruling (Task 1): Pure target keys use dimension strings; ResourceKey/Level static initialization needs a running registry bootstrap in plain JUnit — live adapter resolves the exact dimension key — cost if wrong: typo in dimension mapping; actual keys come from server dimensions.

## Tasks
Task 1: complete (BASE f93f27f; commit feat(control): refresh and server-wide world pause). RED: missing APIs; GREEN: WorldPause/refresh 20/20; selected core 377/377; full build 1020/1020, Reinhard 335/335; checker 0 new / 4 known; git diff --check clean. No runtime checks.
Task 2: pending
Task 3: pending
Task 4: pending
Task 5: pending
Task 6: pending
Task 7: pending
Task 8: pending

## Manual acceptance (not run)
- [ ] /viltrumite pause 100; /reload, owner death/logout/dimension/hero change; all dimensions unfreeze.
- [ ] Dedicated startup and dimension codecs; old-world restart; no client class loading.
- [ ] First/third person, F3+T, idle image unchanged, phase screenshots, shader packs.
- [ ] Three players (one Nether), zombie/Wither/dragon/player/Lion's Heart/divine targets; second cast no cooldown.
- [ ] Limbo 30 online seconds: inventory/armour/offhand/XP gone, ender chest kept; logout/restart; shared spawn, no death/drop/stat/message; erased Regulus Evangelium returns.
- [ ] Limbo black fog, no hands/HUD, text/chat/F1/heartbeat/return flash; freeze and duck release on every end.
