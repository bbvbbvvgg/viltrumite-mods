# Memory bank — Thanos six-stones event (worldevent/thanos)

- Design: `docs/design/2026-10-31-thanos-six-stones-design.md`; plan: `docs/plans/2026-10-31-thanos-six-stones-event.md`;
  asset sources: `tools/assets/thanos_sources.md`. Version pair **1.61.0 / protocol 19** (after main's 1.60.0 / 18).
  The branch's pre-merge builds said 1.58.0 / 17; they were never shipped and protocol 19 refuses them.
- Packets: `ThanosFxS2CPacket`, `ThanosStateS2CPacket` append after `CaptainFxS2CPacket` (`HeroRosterIntegrationTest` pins it).
- Merge recipe used (2026-10-31): push files cannot make a merge commit. Move the branch's registrations off main's
  insertion points, push, open the PR, then GitHub MCP `update_pull_request_branch` creates the real merge commit.
  Then restore the final order and bump the versions in a normal commit.

## Server rules (invariants)
- One tick, in this order: all damage of the tick → defeat check → clock advance → expiry check. Defeat wins ties
  (`ThanosOutcome.resolve`). After an outcome Thanos is invulnerable and the other outcome can never start.
- The clock holds while any world pause or DIO time stop holds the world (`ThanosClock`); it also holds during a hero
  `WorldPause` started mid-fight. The snap waits (`SNAP_WAIT`) while another pause runs; during the snap other
  pauses are refused (`WorldPause.beginEvent` is an ownerless session that the event ends itself).
- Max health above 1024: vanilla clamps the attribute, and `getMaxHealth()` is final. The value is synced in the entity
  and `ThanosMaxHealthMixin` returns it. Without the mixin the boss bar and the caps use 1024.
- Damage caps (`ThanosDamageCap`): 2.5 % of max per hit (at least 40) and 2.5 % per second. `/kill` and
  `/damage ... generic_kill` (admin) still kill. Thanos has the `divine` tag: Annihilation, World Erasure, the world cut
  and Reid's strike deal 0 (by design). `forge:bosses`: grabs do not take hold.
- Health scaling counts participants at the start (max 8); heroes who join in the first 30 s raise it (fraction kept),
  later heroes do not.
- `/thanos start` places him with `ThanosPlacement.near` around `ThanosEvent.groundBelow(starter)`: the feet of a
  grounded starter, or the first surface up to `START_DROP` (64) below a flying one. Without that a flying starter
  got "no room".

## Client sync
- The server sends state every 10 ticks to viewers (same level, within `ThanosTargets.FX_RANGE`) in the fight and every
  5 ticks to every player during `SNAP_WAIT`/`SNAP`.
- Watchdog (`ThanosClient`): 3 s in the fight (this is also how the HUD leaves a hero who walked out of range or
  changed dimension: the server just stops sending), 10 s in the scene (the server sends the scene to everyone, so
  silence there is a lag spike).
- Stage clock: `ThanosSceneTimeline.estimate` runs at most 30 ticks past the last packet. The scene clock
  (`ThanosSceneTimeline.follow`) jumps only forward (behind by more than 12 ticks). Ahead of the server it holds and
  never rewinds. Verified: a 4.6 s server freeze mid-scene holds the frame, then the scene goes on (no drop to the
  world, no replay).
- A late join or a reconnect during the scene starts the scene at the server's tick (`ThanosSceneClient.start`).
  A client that played the scene to its end ignores more scene packets of that run (`done`).
- The scene camera is a client `Marker`. Vanilla then draws the local player, so `ThanosSceneClient` cancels the own
  body render when the camera is inside the hitbox (`hidesSelf`) and denies the own name tag (`RenderNameTagEvent`).

## QA
- Op commands: `/thanos start|stop|status`, `/thanos skip <seconds>` (sets the time left), `/thanos move <move>`
  (starts it as his next move instead of the brain's choice). Logs: logger `viltrumitecore/thanos`; INFO for arrival, phase and end,
  DEBUG for every move (target, distance, line of sight, telegraph).
- Interplay tests use the existing QA switches on a dedicated server: `-Dviltrumitecore.dio.qa=true`
  (`/dioqa foreignstop <ticks>`), `-Dviltrumitecore.reinhard.qa=true`, `-Dviltrumitecore.subtitles.qa=true`
  (`/subtitleqa hero <targets> <hero>` sets any hero).

## Known limits (2026-10-31)
- 640×480 window only: Reinhard's mouse-hint line overlaps the timer's stone row. No overlap at 1280×720.
- One native client crash (SIGSEGV in libc, Render thread) during the tests; not reproduced; cause unknown (unverified).
- Not verified in game: every hero's parry/guard against every move, large hero crowds, live-player dodging of each move.
