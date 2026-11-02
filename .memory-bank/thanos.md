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

## HUD next to a hero HUD
- `ThanosHudLayout` (pure, `ThanosHudLayoutTest`): the timer panel stays centred under the boss bar unless the local
  player's hero HUD (bottom-right: ability bar column, emblem, page badge, and the style's attachments from
  `PanelStyle.attachmentsLeft/Up`, default 80/24, Reinhard 168/30 for his mouse hint) reaches its rows. That happens at a
  GUI height near 240: the automatic GUI scale at 1280×720, 1920×1200, 2560×1440 and 3840×2160. Then the panel moves left
  of it, the warning strip narrows (left of it), wraps its hint (up to 3 lines) and shrinks a long move name.
- Before (2026-10-31): at 320×240 and 426×240 the stone row crossed Reinhard's hint and the strip covered gauges and
  portraits. Verified in game after the fix: 320×240 (Captain en, Reinhard ru), 426×240 (Reinhard ru), 640×360 (centred,
  one-line strip as before).
- Shared code must not branch on hero ids (`NoHeroBranchTest`): hero-specific HUD extents go through `PanelStyle`.

## QA
- Op commands: `/thanos start|stop|status`, `/thanos skip <seconds>` (sets the time left), `/thanos move <move>`
  (starts it as his next move instead of the brain's choice). Logs: logger `viltrumitecore/thanos`; INFO for arrival, phase and end,
  DEBUG for every move (target, distance, line of sight, telegraph).
- Interplay tests use the existing QA switches on a dedicated server: `-Dviltrumitecore.dio.qa=true`
  (`/dioqa foreignstop <ticks>`), `-Dviltrumitecore.reinhard.qa=true`, `-Dviltrumitecore.subtitles.qa=true`
  (`/subtitleqa hero <targets> <hero>` sets any hero).

## Known limits (2026-10-31)
- One native client crash (SIGSEGV in libc, Render thread) during the tests; not reproduced; cause unknown (unverified).
- Not verified in game: every hero's parry/guard against every move, large hero crowds, live-player dodging of each move.

## THANOS 2.0 — Total War (2026-11-05, core 1.70.0 / protocol 28, branch `feat/thanos-total-war-rework`)
- Design: `docs/design/2026-11-05-thanos-total-war-design.md`; attack list, manual QA, results, known limits:
  `docs/design/2026-11-05-thanos-total-war-qa.md`; assets: `tools/assets/thanos_war_sources.md`.
- Server code: `worldevent/thanos/war/` (`ThanosWar` owns ship/army/order/holds/city/destruction per event),
  `war/order/` (Maw/Cull brains), entities `entity/thanos/{EbonyMawEntity,CullObsidianEntity,WarDebrisEntity}`.
  New `ThanosMove`s and FX kinds (29–58) are append-only (ordinal = network id); `ThanosWarS2CPacket` is last.
- Invariants: the phase floor (`ThanosPhase.floor` = next gate) is enforced in `ThanosEntity.setHealth` after the hit
  cap; held-off damage feeds `takeAbsorbed` → absorb FX. Every hold asks `ThanosControlLedger` (200 t window:
  full/half/quarter, then 120 t immunity). Every removed block goes through `ThanosWarDestruction` (canDestroy,
  budget 3200, cause `thanos`). Thanos, the Order and the Chitauri never hurt each other.
- End of the war (any outcome): Order discarded, army recalled, ship leaves, and `ThanosArmy.clear` also sweeps
  `chitauri_bolt` within 224 blocks; `ChitauriBolt.shouldBeSaved()` is false. Found in MP QA: missed bolts flew out of
  the server's simulation distance, froze in non-ticking chunks and outlived the war.
- Time Anomaly hurts only heroes farther than `ANOMALY_SAFE` (2 blocks) from the mark (staying by it is the counter).
- `/thanos phase <p>` for a phase already reached answers `commands.viltrumitecore.thanos.phase_already`.
- Client harness (`-Dviltrumitecore.subtitles.playtest=true`, `<gameDir>/subtitle_qa.txt`): `warstate`, `lookship`,
  `fly`, `holdshot <name> [secs]`; log lines `SUBPT … sound …` and `SUBPT … strip <MOVE>|held|none`.
- QA recipe that worked in the 4 GB sandbox: dedicated server -Xmx1000m (`--launchTarget forgeserveruserdev --nogui`,
  RCON, flat, allow-flight, ops) + two clients -Xmx850m with separate gameDirs. Never compile while a dev JVM runs
  (it loads classes from `build/classes`). Do not delete tracked `viltrumitecore/logs/*.log.gz`.
- Known limits: city alarm/blackouts/collapses not seen without the Atheria map; FPS only under llvmpipe; Hulkbuster
  clash only unit-tested (no summon command; "heavy" = bbox h ≥ 2.6 or w ≥ 1.1).
