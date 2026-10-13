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

- Ruling (Task 2): Replace hero state with the hero's exact death-copy policy after world-side cleanup; the ServerPlayer identity stays the same — safer than enumerating forgotten future fields — cost if wrong: retained fields follow existing clone semantics, not an undocumented expectation.
- Ruling (Task 2): BodyLoss is a hero-neutral sequencing seam with pure state/fake-body tests; real ServerPlayer adapters remain manual — no game/server — cost if wrong: event or inventory/flight adapter faults can escape unit tests.
- Ruling (Task 2): Keep death-surviving dome-owned STASIS and launched world jobs; ERASED uses CASTER_DEATH, not hero-change semantics — spec/Stage 4 world ownership — cost if wrong: a world-owned effect survives its erased caster until its natural end.
- Ruling (Task 2): Refresh the consumed life totem on ERASED while preserving session UUID/Phoenix/PersistentCooldowns — death clone normally refreshes the new life; do not refresh on ordinary End-return — cost if wrong: one extra life after erasure instead of exhausted totem retention.

- Ruling (Task 3): Vanilla EndDragonFight.setDragonKilled creates a portal, gateway and first-kill egg (verified bytecode); use narrow common accessors to mark defeat/hide bossbar without generating blocks/rewards — spec "world blocks never change" outranks that plan step — cost if wrong: an erased first dragon does not create an exit portal/gateway/egg; test End exit options manually before using it there.
- Ruling (Task 3): Packet carries pause UUID and server age in addition to the original fixed start/target data — every dimension and late joiner uses the same elapsed scene even if local game times differ — cost if wrong: one packet latency of visual clock offset.
- Ruling (Task 3): Player destination is an explicit bindable transfer port until Task 4; no player cast is accepted without it, and SHIPPED_STAGE remains 5 — intermediate commits cannot erase an inventory without a destination — cost if wrong: slot 6/player targets unavailable until its complete wiring.
- Ruling (Task 3): Disappearance makes exactly one removal attempt at tick 90; targets absent then are not erased after rejoin, while visuals finish with frozen verdict — cost if wrong: a logout before removal can escape erasure, as specified.
- Ruling (Task 3): Caster stands at the captured position; controlled/canceled action also ends its pause using OWNER_LEFT — keep the clip and no-flight post priority coherent — cost if wrong: owner cannot walk during the 7.5-second cast.

## CleanupReason audit (Task 2)
| Existing check / consumer | Explicit ERASED decision |
|---|---|
| HeroEvents death / logout cleanup | Existing DEATH / DISCONNECT unchanged; prepareErasure is the new ERASED call. |
| HeroRegistry HERO_CHANGE cleanup | Unchanged; only a real hero transition removes chassis/loadout. |
| ViltrumiteHero HERO_CHANGE | Chassis removal only HERO_CHANGE; ERASED resets kit actions without strength modifier removal. |
| HomelanderHero HERO_CHANGE | Passive removal only HERO_CHANGE; ERASED ends channels then uses fresh state, including regen/heat/roar. |
| RegulusHero HERO_CHANGE | Passive removal only HERO_CHANGE; ERASED world cleanup + death-copy cooldowns. |
| RegulusState HERO_CHANGE | ERASED also clears action; carrierLevels and all combat fields drop; cooldowns kept. |
| ControlManager reason != DEATH | !losesBody(): ERASED preserves world-owned domes. |
| ControlManager STASIS death link | losesBody(): ERASED keeps only dome-linked STASIS. |
| ControlManager cleanupReleaseReason switch | DEATH and ERASED → CASTER_DEATH. |
| IronManHero Hulkbuster switch | DEATH and ERASED → onDeath; grab release/break semantics unchanged. |
| IronManHero three != DISCONNECT checks | ERASED clears owner sections/armour and refreshes body size. |
| IronManHero HERO_CHANGE | Passive removal only HERO_CHANGE. |
| IronManState switch | DEATH and ERASED clear suit/overdraft/overheat; ERASED then death-copy energy/default channels; documented cooldowns/roster/pod kept. |
| ReinhardHero != DISCONNECT | ERASED clears Wind Reading sections. |
| ReinhardHero HERO_CHANGE | Chassis/page removal only HERO_CHANGE; ERASED keeps mode/cooldowns, Reid SHEATHED, adaptation/combos/windows/grabs gone. |
| RegulusHearts/GreedsEmbrace/Evangelium reason consumers | No conditional reason branches; normal release/modifier cleanup applies to ERASED too. |
| Generic held inputs / target control / projectiles / grab tag / flight / owner sections | Explicit reset on same entity; persistent cooldown NBT untouched. |

## Tasks
Task 1: complete (BASE f93f27f; commit 4a800b3 feat(control): refresh and server-wide world pause). RED: missing APIs; GREEN: WorldPause/refresh 20/20; selected core 377/377; full build 1020/1020, Reinhard 335/335; checker 0 new / 4 known; git diff --check clean. No runtime checks.
Task 2: complete (BASE 4a800b3; commit a1d689a feat(hero): erased cleanup reason and fresh body path). RED missing APIs → GREEN lifecycle 14/14 + existing control 11/11; selected core 391/391; build 1034/1034, Reinhard 335/335; checker 0 new / 4 known; git diff --check clean. Same-body domain states of all five heroes compared recursively to fresh states; runtime adapters not run.
Task 3: complete (BASE a1d689a; commit feat(reinhard): world erasure timeline). RED missing APIs → GREEN 26/26 new erasure tests; selected core 417/417; build 1060/1060; Reinhard 361/361; checker 0 new / 4 known; git diff --check clean. Dragon bytecode audited; narrow fight accessor compiled/refmap generated, no runtime mixin check.
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
