# T20 — reconcile DangerMap zones left behind by collapse / aftermath / reset

Branch: `swarm/danger-reconcile` (worktree /data/home/work/swarm/t20-danger-reconcile)
Status: implementing

## Problem (from the brief)
`CityDisasterLinks.reconcilePower` runs every 100 ticks, but danger zones (`DangerMap`) created by a
collapse stay "hanging" after `/city reset` mid-event: citizens keep fleeing from destruction that no
longer exists.

## Where collapse danger zones come from (verified)
- `CollapseDirector` (start, L268): `city.danger(cx, groundY+1, cz, radius+10, lifetime 460)` + blast
- main fall cue (L489): `city.danger(center, radius+8, 400)`
- impact (L548): `city.danger(landing, radius+6, 600)`
- Also `city.blast(...)` → `DangerMap.blast` (BLAST_WINDOW 6 ticks, prune-only expiry)
- Aftermath fires: `CityAftermath.citizens()` every 100 ticks re-adds danger zones for IMPACT/SMOLDER/
  LATE stages, each with lifetime 120 — they expire on their own ≤ 120 ticks after the last re-add, but
  if zones are cleared the re-adds stop (zones list is empty after reset).

## Failure paths (verified against code)
1. `/city reset` mid-collapse → `startReset()` → `CollapseDirector.abortAll(level)` → `Finished(aborted)`
   → `CityDisasterLinks.onCollapseFinished` removes the ACTIVE entry but touches no danger zone. The
   400–600-tick zones keep telling citizens to flee for ~20–30 s after the map is restored.
2. Reset does not clear the DangerMap at all (`startReset` clears population/power/aftermath, not danger).
3. An aborted collapse (level unload, `/collapse clear`) posts `Finished(aborted)` — zones from phases
   that never happened (main fall, impact) or already cooled stay live.

## Fix (small, in the two files named by the brief)
- `DangerMap.clearNear(x, y, z, radius)`: removes zones whose centre is within `radius` of the point
  (dangers and curiosities, tracked too — a tracked source that stopped must not keep its zone) and
  clears fresh blasts near the point. Returns the count. Pure, unit-testable.
- `CityDisasterLinks.onCollapseFinished`: when `event.aborted()`, clear the danger map near the collapse
  centre with a radius that covers the whole event (centre radius + landing spread). A finished
  (real) collapse keeps its zones: the ruin actually exists and its danger must age out naturally.
- `CityDisasterLinks.onCityReset`: clears all `grid:` aftermath outages today; now also clears the
  whole city danger map (reset = map restored, no destruction exists any more).
- `CityManager.startReset` keeps its current call order (abortAll first → events fire → the listener
  clears the aborted collapse's zones); `onCityReset` is called later in the same method and wipes
  whatever is left (other events' zones — Godzilla etc. — die too, their sources are stopped/reset by
  the same method).
- The resume path (`resumeIn`, CityManager.tick) already calls `CityDisasterLinks.onCityReset` after
  `CityAftermath.onCityReset`; with the fix it also clears the danger map after a restart-resumed reset.

## Radius choice for the aborted-collapse clear
The director adds zones at the centre (radius+10) and at the landing centre (radius+6). Clear radius =
`info.radius() + LANDING_MARGIN` centred between the collapse centre and the landing centre is wrong —
the landing box centre may be offset by the fall direction. Simplest correct: clear near the collapse
centre AND near the landing box centre, each with `radius + 16` (covers radius+10 and radius+6 zone
radii plus drift). Blasts near the same points go too.

## Tests (pure, like CityPureTest)
`CityDisasterLinksDangerTest` (city/links):
1. abort clears zones near centre and landing, keeps a far zone (Godzilla elsewhere) — via direct call
   `CityDisasterLinks.clearCollapseDanger(...)` (package-private static, called from the listener);
2. a finished (non-aborted) collapse does NOT clear zones;
3. `DangerMap.clearNear` removes dangers, curiosities and tracked zones inside the radius, keeps those
   outside, clears blasts inside the radius;
4. wiring pin: `onCollapseFinished` calls the clear for aborted events; `onCityReset` clears the map
   (source-level pin like `CityDisasterLinksTest.systemsAreWiredTogether`, plus `CityManager.startReset`
   still aborts before aftermath clear).

Acceptance from the brief: "collapse + reset → DangerMap holds no zone of a finished/aborted event" —
covered by 1 + 3 + the reset pin.
