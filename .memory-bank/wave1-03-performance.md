# Wave 1 / 03 — performance budgets (core 1.72.0, branch wave1/03-performance, PR #167)

Design: `docs/design/2026-11-wave1-03-performance.md`. Reference: `docs/performance/budgets-2.0.md`.

## Invariants
- CRITICAL is never denied or thinned. Smoke is never thinned (HIGH with share 1.0; see `performance.md`).
- Budget reserves must not depend on request order (`TickBudgetTest.theReserveHoldsWhateverTheOrder`).
- New users of block writes take from `BlockUpdateBudget` and **refund** what they did not spend in the same tick.
- Only purely visual entities may be refused by `EntityBudget`; never gameplay entities, never `loadedFromDisk`.
- `/vperf stress` only ever replaces air and restores every touched block; keep it that way.
- `PerfStatsS2CPacket` stays registered after SCP-106; a new packet appends after it and bumps the protocol.
- New lang keys of this slice go to `src/main/lang-overlays/perf/<locale>.json` for all 12 locales.

## Gotchas
- `TickBudget.lastUsed/lastDenied` are snapshots taken in `reset()`; read them, not `used()`, from other systems.
- On llvmpipe (no GPU) the frame time is always high: use `-Dviltrumitecore.perf.adaptive=false` or a higher
  `frameStart` for A/B comparisons, or the client throttles itself from the first frame.
