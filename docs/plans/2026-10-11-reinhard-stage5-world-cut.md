# Reinhard — Stage 5 (World Cut) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Overview: `docs/plans/2026-10-11-reinhard-overview.md`. Visual tasks follow `.agents/skills/animation-system/SKILL.md`.

**Goal:** World Cut (Holy Sword slot 5, Reid drawn): 1 s overhead swing, 3 s of silence with a thin white line over the ground and the sky for 200 blocks, then a wave opens a 200 × 17 × 45–50 trench from Reinhard outward in ~1.5 s with a roar, a shockwave and a huge crater; everything living on the line gets the Reid strike; block removal keeps a hard per-tick budget (start 2000) that adapts so TPS stays ≥ 15; the client wave and cracks run on time, blocks catch up; cooldown 45 s.

**Architecture:** server `hero/reinhard/ability/WorldCut.java` (timeline, hits) + `hero/reinhard/cut/WorldCutShape.java` (pure geometry and wave order) + a `CarveJobs.Job` (`WorldCutCarve`, Stage 4 seam) + `HeroDebris` for a small share of flying blocks. Client `client/reinhard/cut/WorldCutScene.java` driven by `WorldCutS2CPacket` and game time. Camera: Reinhard's camera is never taken over; other players near the line get a slight FOV pull during the silence and a strong shake at the blast (spec §9.4).

**Tech Stack:** `CarveJobs`/`CarveBudget`, `HeroDestruction.clearBlock`, `HeroDebris.launchBlock`, `Annihilation`, `HeroFx`, `CameraShake`, `SoundDuck`, `PixelVfx`, `ViewportEvent.ComputeFov`.

**Spec:** §7.6, §9.4, §10.1 (world cut sounds), §11.9.

## Global Constraints

See the overview. Stage-specific:
- Budget: never more than `CarveBudget.perTick(2000, mspt)` blocks per tick for all carve jobs together; a job never blocks a tick.
- No drops, no neighbour updates, no fluid cascades from the carve itself (flags in `HeroDestruction.clearBlock`); `mobGriefing` false → no blocks change, but the VFX, sound and Reid strike still happen.
- Unloaded chunks: the carve skips positions in unloaded chunks (never force-loads); the line VFX is still drawn.

## Review Focus

1. Cast near the world border or toward the void / build limits: the shape is clipped, no exception, no write outside the world — `WorldCutShapeTest.clipsToWorld`.
2. Reinhard dies or logs out during the 3 s silence: the cut still resolves (the swing already happened) or is cancelled cleanly? Decision: the cut resolves from the cast point (the world remembers); clients get the explosion packet; no stuck duck/FOV — `WorldCutTest.resolvesWithoutCaster`.
3. Two World Cuts in a row by two Reinhards: jobs queue, budget is shared, TPS holds — `CarveJobsTest.sharedBudget` (Stage 4) + perf protocol Task 5.
4. Water and lava next to the trench: no endless fluid updates after the carve — perf protocol + manual (ocean cast).
5. A player standing on the line in a mark / Hulkbuster with a totem dies; Regulus in Lion's Heart and another Reinhard survive — `WorldCutTest.strikeVerdicts`.

---

### Task 1: Trench geometry and wave order

**Files:**
- Create: `core/hero/reinhard/cut/WorldCutShape.java`
- Test: `test/hero/reinhard/WorldCutShapeTest.java`

**Interfaces:**
- `ReinhardRules`: `CUT_LENGTH = 200`, `CUT_WIDTH = 17`, `CUT_DEPTH_MIN = 45`, `CUT_DEPTH_MAX = 50`, `CUT_ABOVE = 20` (air above the surface counted as "on the line" for hits), `CUT_DEBRIS_SHARE = 0.002`, `CUT_DEBRIS_MAX = 300`.
- `record WorldCutShape(Vec3 origin, Vec3 dirXZ, long seed)`: `int depthAt(int along)` = 45–50 (smooth value noise by `seed`), `boolean contains(int along, int across, int down)`, `Iterator<BlockPos> wave(int minY, int maxY)` — positions ordered by `along` (wave front from Reinhard outward), inside each slice top-down; `AABB hitBox(int along)`; `int volume()`.
- The trench starts 2 blocks in front of Reinhard's feet (he stands at the edge) and its top follows the surface height at each `along` (scan from the highest non-air block of the centre column down).

- [x] Step 1 tests: `volumeAbout153k` (200 × 17 × 45..50 → 153 000 ≤ v ≤ 170 000), `depthBetween45And50`, `waveIsMonotonicAlong`, `widthIs17`, `clipsToWorld` (minY/maxY and border predicate), `deterministicForSeed`. Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): world cut geometry`.

### Task 2: Carve job, adaptive wave, debris share

**Files:**
- Create: `core/hero/reinhard/cut/WorldCutCarve.java` (implements `CarveJobs.Job`)
- Test: `test/hero/reinhard/WorldCutCarveTest.java`

**Interfaces:**
- `step(level, budget)`: takes up to `budget` positions from `shape.wave(...)`, skips unloaded chunks and `!canDestroy`, `HeroDestruction.clearBlock`; every `1/CUT_DEBRIS_SHARE`-th removed solid block near the surface (top 4 layers) becomes `HeroDebris.launchBlock` (outward + up velocity) until `CUT_DEBRIS_MAX`; fluids touching the trench walls are not updated (flags) — after the job ends, one `level.updateNeighborsAt` pass is NOT done (keeps water still; this is the intended look of a clean cut).
- Wave front report: `int frontAlong()` for the server timeline (hits follow the blocks or the visual front, whichever is first; see Task 3).
- With the starting budget 2000 the 153k blocks take ~77 ticks (~3.8 s), matching the spec's "~4 s"; at high MSPT the job just takes longer (adaptive wave).

- [x] Step 1 tests: `neverAboveBudget`, `skipsUnbreakable` (probe), `debrisCapped`, `finishes` (fake level of 160k positions, budget 2000 → 80 steps), `slowerUnderLoad` (`perTick(2000, 55)` → more steps). Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): world cut carve job`.

### Task 3: Server timeline, Reid strike on the line, cooldown

**Files:**
- Create: `core/hero/reinhard/ability/WorldCut.java`, `core/hero/reinhard/cut/WorldCutManager.java` (level-owned runtime), `core/network/packet/WorldCutS2CPacket.java` (register in `CoreMessages`)
- Modify: `core/hero/HeroEvents.java` (`onLevelTick` **END** branch: `WorldCutManager.get(level).tick(level)` next to `CarveJobs.tick`; `START` stays `ControlManager` only; `ServerStoppingEvent` → `WorldCutManager.clearAll()`), `ReinhardActions`, `ReinhardState.busy()` (true until the explosion tick), `ReinhardAbilities.SHIPPED_STAGE = 5`, icon `world_cut`, lang, `res/sounds.json` (`reinhard.world_cut_silence`, `reinhard.world_cut_blast`, `reinhard.world_cut_tail` — placeholders `block.beacon.ambient`, `entity.generic.explode`, `entity.warden.sonic_boom` until Stage 7)
- Test: `test/hero/reinhard/WorldCutTest.java`

**Interfaces:**
- `ReinhardRules`: `WORLD_CUT_SWING = 20`, `WORLD_CUT_SILENCE = 60` (ticks 20–80), `WORLD_CUT_BLAST = 80`, `WORLD_CUT_WAVE_TICKS = 30` (visual front 200 blocks in 1.5 s), `WORLD_CUT_COOLDOWN = 900`, `WORLD_CUT_VIEW_RANGE = 320`.
- Only Reid DRAWN. Press: action timeline, cooldown starts; shape fixed at the press (origin, look yaw only — the cut is vertical; pitch ignored).
- Tick 0: packet `START` to every player within `WORLD_CUT_VIEW_RANGE` of any point of the line (same level). Tick 80: packet `BLAST`; submit `WorldCutCarve` (priority high); start hits: each tick from 80 to 110, entities in `shape.hitBox(along)` for `along ≤ visualFront(t)` → `Annihilation.strike` (once per entity; `ReinhardTargets`-free: "всё живое" = every living entity except Reinhard; divine and absolute verdicts handled by `Annihilation`).
- Runtime owner: `WorldCutManager` — `static WorldCutManager get(ServerLevel)` (a `WeakHashMap<ServerLevel, WorldCutManager>` like `ControlManager.INSTANCES`), `submit(WorldCut cut)`, `tick(ServerLevel)` advances every cut by `gameTime − startTime` (catch-up safe), removes finished ones, `clearAll()` on server stop. `ReinhardHero.tick` only presses/submits; it never ticks cuts, so a dead or offline caster does not stop the cut. Not persisted: a server stop during the 4 s cast drops the cut (the swing animation is gone too; documented). The cut stores `casterId` only for damage attribution (`Annihilation.strike` with a null attacker when the caster is offline).

- [x] Step 1 tests: `timelineTicks`, `onlyWhenDrawn`, `cooldown900`, `hitsFollowFront` (pure `visualFront(t)` 0 → 200 over 30 t), `everyEntityHitOnce`, `strikeVerdicts` (fake verdicts → kills only MORTAL), `resolvesWithoutCaster` (submit, remove the fake caster, tick the manager 110 times → blast packet sent, hits applied), `managerTickedOnEndPhaseOnly`, `twoCutsIndependent`. Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): world cut timeline and strike`.

### Task 4: Client scene — line, silence, dust, FOV pull, blast, crater

**Files:**
- Create: `core/client/reinhard/cut/WorldCutScene.java`, `core/client/reinhard/cut/WorldCutFov.java` (`ViewportEvent.ComputeFov` listener); clip `world_cut` (1 s overhead swing then hold low for 3 s); first person: both hands bring the blade down through the view
- Modify: `ReinhardClient` (register)
- Test: `test/client/reinhard/WorldCutSceneTest.java`

**Interfaces:**
- All by `(gameTime − start + pt)` from the packet:
  - 20–80 silence: a 1-pixel-thick white line of billboard pixels on the ground along the cut axis for 200 blocks and a matching line across the sky (projected up to cloud height), slow shimmer; `SoundDuck.duck(MASTER → 0.15)` for players within range; dust motes trembling on the ground near the line (pixel, grey-brown); `CameraShake.addAt(power 0.3)`; FOV pull for non-casters: `WorldCutFov.factor(distanceToLine)` +3% max, eased by real time.
  - 80 blast: release the duck; `CameraShake.addAt(power 5.0, range 200)` (not for the caster beyond his usual hit shake); `HeroImpactFx` shockwave rings along the line; the visual wave front runs 200 blocks in 30 t with white-gold fire pixels and cracks on both trench edges; dust columns; huge crater haze.
  - Blocks arrive later from the server; the scene never waits for them.
- Pure `WorldCutFov.factor(double dist)`: 1.03 at 0, 1.0 at ≥ 64, smooth.

- [ ] Step 1 tests: `fovOnlyForOthers` (caster → 1.0), `fovFallsWithDistance`, `frontReaches200At30`, `duckOnlyDuringSilence`. Step 2 FAIL. Step 3 implement. Step 4 PASS; manual: caster view first and third person; a LAN player at 30 and 150 blocks; shaders on. Step 5 commit `feat(reinhard): world cut client scene`.

### Task 5: Performance protocol

**Files:**
- Create: none (results go to the PR body and `SESSION.md`).
- Modify: `ReinhardRules` constants only if the numbers fail.

- [ ] Step 1: dedicated server (`./gradlew :viltrumitecore:runServer`) + one client, render distance 12, a flat world, a plains/forest world, an ocean cast and a mountain cast.
- [ ] Step 2: measure `/forge tps` every second for 10 s after the blast; requirement: overworld TPS ≥ 15 at every sample (spec §11.9). If not, lower `CUT_BASE_BUDGET` (it is shared with Rain of Cuts) or tighten `CarveBudget` thresholds; re-run.
- [ ] Step 3: verify no item entities (drops) appeared and that fluids next to the walls stay still.
- [ ] Step 4: commit only constant changes `perf(reinhard): tune carve budget` if any.

### Task 6: Check, docs, PR

- [ ] Step 1: full verification list; dedicated server start.
- [ ] Step 2: in game, both views; `mobGriefing false` (no blocks, still strike and VFX); bedrock under a shallow cast stays.
- [ ] Step 3: update `shared-toolkit.md` (`CarveJobs` usage example), `.memory-bank/project.md` (carve flags and the fluid behaviour), `SESSION.md`; commit `docs(reinhard): stage 5 notes`; push `feat/reinhard-stage5`; PR «Рейнхард, этап 5: Мировой разрез».

Acceptance criteria closed: §11.9, §11.7 (§7.6 part).

## Risks and pitfalls

- Light updates for 150k removed blocks are the real cost, not `setBlock`: watch the light engine queue; if TPS fails, budget down first.
- `ClientboundSectionBlocksUpdatePacket` batching per section is automatic; do not send per-block packets.
- `HeroDebris.launchBlock` sets the source block to air itself — do not also `clearBlock` that position.
- `ViewportEvent.ComputeFov` runs per frame: no allocation, ease by `System.nanoTime`.
