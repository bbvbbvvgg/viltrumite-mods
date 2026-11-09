# Wave 1 · Session 03 — Performance budgets 2.0 (design)

Branch `wave1/03-performance` → PR into `integration/viltrumite-2.0`.
Owned code: `perf/` (common), `client/perf/` (client). Shared files are touched only by small appends (listed in the PR).

## 1. Для игрока

Даже когда одновременно идут война Таноса, метеоритный дождь и вторжение читаури, рушатся небоскрёбы, а два
игрока кидаются ультимейтами, игра остаётся плавной. Эффекты не исчезают разом: дальние становятся проще, ближние
и важные (удар, луч, взрыв рядом с тобой) остаются во всей красе. В настройках есть качество эффектов —
Низкое, Среднее, Высокое, Ультра. Команда `/vperf` показывает живой оверлей: FPS, время кадра, TPS сервера и
заполнение бюджетов; `/vperf stress <профиль>` воспроизводимо нагружает игру для замеров.

## 2. Goals and non-goals

Goals (from `03_performance.md`):
1. Baseline measurements: FPS/frametime, TPS/MSPT, entity, particle and block-update counts in a stress scene.
2. Global budgets with a public API: client particles (priority + distance LOD), server event/debris entities,
   destruction block updates per tick. Graceful degradation, never a hard cut to zero.
3. Fix the top-5 hot spots in existing code with before/after numbers.
4. `/vperf` live overlay of budgets and counters; `/vperf stress <profile>` reproducible load.
5. Effect quality in the client config: LOW, MEDIUM, HIGH, ULTRA.
6. Contract `docs/performance/budgets-2.0.md`.

Non-goals:
- No gameplay changes to abilities or events, except targeted hot-spot fixes with measurements.
- No visual change at the default quality. `HIGH` is defined as "exactly the current behaviour"; this keeps the
  #150 invariant (no visible change to existing smoke) and AGENTS.md §3.5.
- No new particle engine or render system. Budgets gate *spawning*; rendering stays vanilla + existing VFX.

## 3. Architecture

### 3.1 Common core (`perf/`) — pure, unit-tested

| Class | Role |
|---|---|
| `PerfPriority` | `CRITICAL, HIGH, NORMAL, LOW, AMBIENT`. CRITICAL is never denied (gameplay-readable feedback: the hit you landed, a telegraph). |
| `TickBudget` | Per-tick allowance with priority reserve: lower priorities may only use the share left after higher ones. Pressure 0..1 is exposed for degradation. Resets each tick, no allocation. |
| `DistanceLod` | Pure LOD curve: full detail up to `near`, linear fall-off to `far`, then a floor (not zero) for HIGH+; AMBIENT drops to 0 past `far`. |
| `Degrade` | Pure helper: `keep(count, factor, rng)` — deterministic stochastic thinning, so 3.4 particles × 0.5 gives 1 or 2, not always 1. |
| `PerfCounters` | Named counters (spawned, denied, thinned) per budget channel, read by the overlay and `/vperf`. |

### 3.2 Server budgets (`perf/`)

| Class | Role |
|---|---|
| `BlockUpdateBudget` | Per-level, per-tick allowance of destruction block updates. Default 1536/tick at TPS ≥ 19; scales down with MSPT pressure to a floor of 256. Callers that cannot spend now defer (existing queues keep the work), never drop. |
| `EntityBudget` | Soft caps per category (`EVENT_MOB`, `DEBRIS`, `PROJECTILE`, `AMBIENT`) per level. Above the soft cap new spawns far from every player are denied first; CRITICAL (bosses, story actors) always pass. |
| `ServerPerf` | Rolling MSPT (avg / p95 / max over 100 ticks) and TPS from `TickEvent.ServerTickEvent` START/END. Pressure = clamp((mspt − 35) / 15). |
| `PerfCommands` | `/vperf` (op 2): `status`, `overlay [on|off]`, `stress <profile> [seconds]`, `stress stop`. |
| `StressProfiles` | Reproducible load (fixed seed, fixed position offsets): `particles`, `entities`, `blocks`, `chaos`. Block load goes through the city restore journal (rule A8) inside a marked sandbox box and is fully reverted on stop. |

### 3.3 Client budgets (`client/perf/`)

| Class | Role |
|---|---|
| `EffectQuality` | `LOW, MEDIUM, HIGH, ULTRA` → spawn scale, LOD distances, live-cap scale. HIGH = 1.0 (current). |
| `ParticleBudget` | Global client particle allowance per client tick, priority + distance LOD + frame-time pressure (from `FrameProbe`). API: `int allow(PerfPriority, double distSq, int wanted)` and `boolean allowOne(...)`. |
| `PerfOverlay` | HUD overlay: FPS, frame-time graph (last 240 frames), p99, particles, entities, budget fill bars, server TPS/MSPT (S2C stats while subscribed). |
| `PerfStatsClient` | Holds the latest server stats packet. |

### 3.4 Network
- One new S2C packet `PerfStatsS2CPacket` appended at the end of `CoreMessages` (rule A2), sent once per second
  only to players who enabled the overlay. `PROTOCOL_VERSION` bump + `HeroRosterIntegrationTest` update.

### 3.5 Config
- `ViltrumiteClientConfig`: append `effectQuality` (string, default `"high"`). Unknown values → HIGH.
- Server budget numbers: constants with system-property overrides (`-Dviltrumitecore.perf.*`), documented in the
  contract. No new server config file in wave 1 (keeps the shared-file footprint small).

## 4. Integration plan (vertical slices)

1. **Slice 1 — measurement**: `ServerPerf`, `PerfCounters`, `/vperf status`, overlay with client-only numbers.
2. **Slice 2 — budgets core**: `TickBudget`, `DistanceLod`, `Degrade`, `EffectQuality`, `ParticleBudget`; unit tests.
3. **Slice 3 — adoption in hot emitters**: route the busiest client emitters (aftermath smoke, collapse dust,
   Thanos war FX, meteor trails, Captain/Red Hulk VFX) through `ParticleBudget`; route destruction block writes
   through `BlockUpdateBudget` where a queue already exists; event/debris spawns through `EntityBudget`.
4. **Slice 4 — stress profiles**: `/vperf stress particles|entities|blocks|chaos`.
5. **Slice 5 — hot spots**: profile (JFR) under `chaos`, fix top 5, record before/after.
6. **Slice 6 — quality setting UI + contract doc + memory bank.**

## 5. Measurement method

- Server: JFR (`-XX:StartFlightRecording`) on the dedicated server under `/vperf stress chaos`, hot methods from
  `jdk.ExecutionSample`; MSPT from `ServerPerf`.
- Client: `FrameProbe` (existing) + JFR on the dev client. Sandbox renders on llvmpipe: absolute FPS are not
  representative; only same-scene A/B ratios are reported (see `docs/performance/BENCHMARKS.md`).
- Micro-benchmarks of pure hot paths as JUnit timing tests are reported, not asserted (no flaky thresholds).

## 6. Risks

| Risk | Mitigation |
|---|---|
| Visual regression of existing effects | HIGH = current behaviour bit-for-bit (budget never denies below the old caps at HIGH); CRITICAL never denied. |
| Conflicts with sessions 22, 25 and events | Only additive API + a contract; adoption in other sessions' new code is the orchestrator's job (A15). |
| Protocol number conflicts | Expected (A2); the orchestrator assigns final numbers. |
| Block budget delays restoration | Restore (`/city reset`) is never budgeted: it is CRITICAL. |
| Stress profile leaves garbage | Every profile tracks what it spawned and removes it on stop / server stop. |
