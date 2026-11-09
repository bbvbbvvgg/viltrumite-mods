# Performance budgets 2.0 (wave 1 / session 03)

One shared vocabulary for "how much work may this tick / this frame do", so that a collapse, a city fire, debris
and every hero's effects together never exceed the server tick or the client frame.

## Priorities (`perf/PerfPriority`)

| Priority | Share of a budget | LOD floor far away | Use for |
|---|---|---|---|
| CRITICAL | unbounded, never denied | 1.0 | the hit that landed, a telegraph, a boss silhouette |
| HIGH | 1.0 | 0.5 | signature hero/event effects near the camera, **smoke** |
| NORMAL | 0.85 | 0.25 | regular event effects |
| LOW | 0.6 | 0.1 | dust, sparks, embers, crumbs, city fire writes |
| AMBIENT | 0.4 | 0.0 | pure atmosphere |

A priority may only fill its share; the rest stays reserved for the priorities above it, whatever the order of the
requests (`TickBudget.remaining`). `TickBudget.occupy(n)` marks a live count (entities alive) as used at the lowest
priority without counting it as granted.

## Server

- `ServerPerf`: TPS from tick spacing, avg / p95 / max tick time over 100 ticks, `pressure()` 0..1 between
  `msptStart` (35 ms) and `msptFull` (50 ms).
- `BlockUpdateBudget`: block writes per dimension and tick, 1536 when healthy, down to 256 at full pressure.
  Users: `CollapseDirector` (HIGH), `CityFire` (LOW, at most 40 per tick, unused units are refunded),
  `/vperf stress blocks` (LOW). Godzilla keeps its own `CarveBudget` (it already adapts to the average tick time;
  putting it under a second throttle would make the rampage visibly stall).
- `EntityBudget` ("debris" in `/vperf status`): live cap of purely visual entities (`SUIT_DEBRIS`), 600 when
  healthy, 160 at full pressure. Debris within 48 blocks of a player may fill 85 %, far debris 60 %. Entities loaded
  from disk always pass; gameplay entities (`WAR_DEBRIS`, mobs) are never touched.

## Client

- `EffectQuality` (`/vfx quality low|medium|high|ultra`, saved in `ViltrumiteClientConfig.effectQuality`):
  spawn rate, LOD distance, live cap, the lowest frame-pressure factor, whether LOD applies at all and whether the
  frame-time adaptation is on (off for ULTRA).
- `ParticleBudget`: 1500 new particles per tick, 8000 alive (scaled by quality and frame pressure). Gate:
  `ParticleEngineBudgetMixin` on `ParticleEngine.add`. Emitters may wrap their spawns in
  `ParticleBudget.push(priority, origin)` / `pop()` or `run(...)`; `pushPrethinned` for emitters that already thin.
- `ParticleLod`: full detail up to 96 blocks, the priority's floor at 320.
- Smoke is never thinned (memory bank `performance.md`): particle classes whose name contains "smoke" are HIGH.
- `PerfOverlay` (`/vfx overlay`, `/vperf overlay`): FPS, frame avg / p95, particles spawned / denied / alive,
  server TPS, tick avg / p95 / max, load and every server budget's fill.

## Commands

- `/vperf status | overlay [on|off] | reset` (permission 2).
- `/vperf stress particles [perTick] [seconds] | entities [count] [seconds] | blocks [perTick] [seconds] |
  chaos [seconds] | stop`, and `/vperf stress` for the progress. Particles go half near (6..40 blocks), half far
  (60..240); debris is suit debris (expires on its own); blocks toggle white glass only where there was air, in a
  48x12x48 box 24 blocks above the player. Everything is put back on `stop`, at the end and on server stop.
- `/vfx`, `/vfx quality <q>`, `/vfx overlay` (client).

## Switches

Server: `-Dviltrumitecore.perf.blocksPerTick` (1536), `.blocksFloor` (256), `.msptStart` (35), `.msptFull` (50),
`.noServerPressure`, `.cosmeticEntities` (600), `.cosmeticEntitiesFloor` (160), `.entities=false`.
Client: `.adaptive=false`, `.particles=false`, `.particlesPerTick` (1500), `.liveParticles` (8000),
`.frameStart` (33.3 ms), `.frameFull` (100 ms), `.overlay`.
Unchanged: `-Dviltrumitecore.smoke.trim=false`.

## Network

Protocol 32: `PerfStatsS2CPacket` is registered last, after SCP-106 (v31).
