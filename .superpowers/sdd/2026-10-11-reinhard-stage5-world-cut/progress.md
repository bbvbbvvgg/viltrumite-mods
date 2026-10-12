# SDD ledger — plan: docs/plans/2026-10-11-reinhard-stage5-world-cut.md

Branch `feat/reinhard-stage5` from `feat/reinhard-stage4`, base `2a49a715a7190614b5b84c3ac8ce0ef7e41e8677`. Inline executing-plans, TDD. No game / server runs (user instruction). Final fresh-context branch review is required. Ledger remains committed (user instruction takes precedence over skill workspace deletion).

## Rulings

- Ruling: use a fresh clone on the requested feature branch as the isolated workspace, rather than another nested worktree — no existing checkout is changed — cost if wrong: disk space only.
- Ruling: keep the committed ledger after completion, as on stage 4; use task briefs/logs in temporary scratch — user explicitly requests this journal — cost if wrong: history size only.
- Ruling (Task 1): geometry math lives in the dependency-free WorldCutGeometry record; WorldCutShape is the required Minecraft adapter. All dimensions come from ReinhardRules. This permits real JUnit geometry tests during Forge bootstrap without mocking Minecraft primitives — cost if wrong: one extra adapter boundary (covered by WorldCutShapeTest once Gradle is available).
- Ruling (Task 1): terrain and world border enter wave via probes; the default wave uses origin Y - 1. The runtime will freeze loaded centre-column heights at cast — cost if wrong: terrain changed during the silence is cut against the original surface.
- Ruling (Task 1): volume() reports the specified continuous 200 × 17 × depth volume. Centre-sampled whole voxels vary by up to about 4.1% at diagonal yaw (lattice alignment); the additional rotated-wave test uses a 6% tolerance and asserts uniqueness/order. A 2% assumption in that extra test failed at 45° (164832 mathematical / 171523 voxel positions), so it was corrected rather than distorting the 17-block Euclidean width — cost if wrong: diagonal casts can take several additional budgeted ticks; budget itself is unchanged.
- Ruling (verification environment): Java cannot reach dependency hosts directly in this executor. Use temporary Gradle init scripts outside the repository: a curl-backed local Maven cache and a curl URL transport limited to public artifact hosts. Minecraft jars/mappings were SHA1-checked against the official manifest. Production build scripts, dependencies and versions are unchanged — cost if wrong: local verification could differ from clean GitHub CI; CI must also be checked.

## Pre-flight

- Tasks 1 → 2/3/4: geometry supplies ordered positions, surface heights and hit volumes. A three-field pure record cannot query terrain; provide a surface-height/border probe to wave and hit geometry, sample only loaded centre columns at cast, carry the same heights to clients. Pending implementation/test verification.
- Tasks 2 → 3: CarveJobs.Job has step(level, budget), priority(), remainingWork(); frontAlong reports actual consumed slices; level-wide budget remains shared.
- Tasks 3 → 4: packet scene clock must survive missing caster, carry cast UUID, fixed shape, height profile and start game time; client effects never depend on the owner snapshot.
- Task 5 replaces live TPS runs with pure budget tests by user instruction; constants stay unchanged.

## Tasks

Baseline: checker 0 new / 4 known; git diff --check clean. Gradle bootstrap ongoing: Java network/proxy unavailable, curl can fetch official artifacts; temporary local Maven cache outside repository. No production implementation yet.

Setup evidence: JDK 17.0.16 downloaded; Forge 47.3.0 `ViewportEvent.ComputeFov` verified with javap (`getFOV`, `setFOV`). Baseline attempt 4 reached Forge configuration but MCPRepo failed with `UnknownHostException: piston-meta.mojang.com`; official Minecraft 1.20.1 client/server jars and mappings downloaded and SHA1 matched, copied into Forge caches for retry. No task test has run; no RED/GREEN claimed.

Task 1 in progress: WorldCutGeometryTest compile RED (only missing geometry class, JUnit available), then standalone JUnit GREEN 7/7. The initial pure test fixture used feet instead of the helper's documented start point; fixed fixture to z=2.5. WorldCutShapeTest 7 tests await the actual Minecraft classpath; full Gradle verification pending. No commit made.

Task 1 additional evidence: rotatedWaveHasUniqueVoxels caught reversed-axis edge exclusion at yaw 180 (15827 vs 164832 blocks); widened candidate bounds and kept projection half-open, RED→GREEN. waveCountsClippedWork RED (missing remainingWork/frontAlong) → GREEN. Standalone geometry suite 10/10; checker 0 new / 4 known; diff check clean. Minecraft shape tests and full build still pending.

Pre-flight refinement, Tasks 3 → 4: the packet handler needs a client data receiver before the visual task exists. Task 3 will introduce the minimal scene-data holder, Task 4 will add the visuals. Shipping stage 5 waits for Task 4 so a playable slot never lacks its clip/first-person pose.

- [x] Task 1 — Trench geometry and wave order. Reinhard suite 298/298 GREEN; full build 955/955, 0 failures, 0 skipped; checker 0 new / 4 known; git diff --check clean. New tests: WorldCutGeometryTest 10, WorldCutShapeTest 7. RED observed for missing pure geometry, missing wave progress API and reversed-axis raster regression; GREEN in standalone JUnit and Gradle. Not covered: live terrain scanning/chunk load/world writes (later tasks), in-game trench look.
Task 1: complete — verification: Gradle Reinhard suite and build; commits recorded by GitHub task commit below.
