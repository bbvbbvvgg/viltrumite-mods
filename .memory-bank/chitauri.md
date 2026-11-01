# Chitauri invasion (core 1.59.0, branch feat/chitauri-invasion)

Plan: `docs/plans/2026-10-31-chitauri-invasion.md`. Sources/cue sheet: `tools/assets/chitauri_sources.md`.

## Shape
- Server: `worldevent/chitauri/` — `ChitauriInvasion` (director, memory-only `Session`, phase clock, spawn
  queue drained ≤ 2 per tick, boss bars, chat/titles), `ChitauriRules` (pure timeline: OPENING 700, waves,
  caps 24/8/2, total 8620 ticks = 431 s), `ChitauriDestruction` (smash/crack/glass through `HeroDestruction`,
  cap 40 000, respects mobGriefing), `ChitauriTerrain` (loaded/ticking/surface — never loads chunks),
  `ChitauriCommands` (`/chitauri start [force]|end|stop|status|wave|phase <key>`, op 2), `ChitauriSounds`.
- Entities `entity/chitauri/`: soldier (Monster, squads, cover via `CoverScore`, rides chariots), chariot
  (own steering `ChariotSteering`, seats), Leviathan (head Mob + `LeviathanPart`s sampled from
  `LeviathanTrail` by arc length; waypoints prefer tall columns so runs tear through towers), bolt.
- Client `client/chitauri/`: `ChitauriPortalClient` (portal, beam, storm puffs, sky tint), `ChitauriLoops`
  (portal hum, 4+1 engine loops with rank hysteresis, Leviathan rumble, fly-by whoosh), `ChitauriVfx`.
- Packets `ChitauriPortalS2CPacket`, `ChitauriFxS2CPacket` (FX kinds 0..13, contiguous) — PROTOCOL 17.
- `WorldEventConflicts.mayStart` — invasion vs meteor rain vs trio/conquest; `force` overrides.

## Gotchas
- `/chitauri phase leviathan` jumps straight in: the OPENING Leviathan beat (tick 560) never fires, so only
  the phase wave's single Leviathan comes. Status "N still in the portal" counts queued chariots/Leviathans
  waiting for caps (they drop after 60 s).
- Grass under test towers turns to dirt by random ticks: the city journal records it (not the invasion).
- `ChitauriSoldier` overrides fall sounds (own step), `ChitauriChariot.getDeathSound` is null — the event's
  code must never reference `SoundEvents.` (`ChitauriAssetsTest`). Glass breaking uses the block's sound.

## QA harness (worked in this sandbox: 2 CPU, 4 GB, Xvfb :99, llvmpipe)
- SP client: `runClient` with `-Dviltrumitecore.subtitles.playtest=true[.world=true]`; drive it through
  `<gameDir>/subtitle_qa.txt` lines (`cmd …`, `shot name`, `look yaw pitch`, `state`, `leave`).
- Audio: `~/.alsoftrc` `drivers=wave` + `[wave] file=…` makes OpenAL write the whole mix to a WAV; the SUBPT
  log lists every non-minecraft sound played. Per client: `ALSOFT_CONF=<file>`.
- Dedicated server without Gradle: take the client java argv (`/proc/<pid>/cmdline`), swap
  `--launchTarget forgeserveruserdev`, drop `--version/--assetIndex/--assetsDir`, keep the classpath file
  (client-extra must stay), add `--nogui`, set env `MOD_CLASSES`/`MCP_MAPPINGS`. RCON via a 20-line python
  client; `forge tps minecraft:overworld` for tick times. Clients: same argv + `--gameDir`, `--username`,
  `--quickPlayMultiplayer localhost:25565`. Server -Xmx1000m + 2 clients -Xmx850m fit in 4 GB.
- City QA profile: `viltrumite_city/profile.json` in the save (arena box), towers compared with a
  force-loaded reference copy through `execute if blocks … all` after `/city reset confirm`.

## Evolution (core 1.65.0, branch feat/chitauri-evolution)
Plan/report: `docs/reports/2026-11-04-chitauri-evolution.md`. Assets: `tools/assets/chitauri_units.py`
(+`_geo.py`, captor/heavy/commander), `chitauri_craft.py` (interceptor/siege/beacon), sounds `tools/sfx/chitauri_units.py`.
- War layer `worldevent/chitauri/ChitauriWar` lives in the invasion `Session` (memory-only, like the session):
  sectors (`war/SectorPlanner`: city districts when a Living City exists, else rings of ≤ 40 sectors),
  `war/WarSector` state machine UNKNOWN→SCOUTED→CONTESTED→SEIZED→LIBERATED (only loaded columns become known),
  `war/WarDirector` (pure: personality from the seed, 8 doctrines scored + softmax on the seeded stream,
  budget 30 + captive bonus ≤ 6, unit costs, learned per-doctrine bias), `war/WarOrder`, `war/WarRules`.
  Director logs one `Chitauri director t=… doctrine=… mix=… scores=[…]` line per decision (every 300 ticks).
- Civilians: `ChitauriCaptives` + `war/CaptiveLedger` + `city.citizen.CitizenCaptivity` — a captive is never
  discarded: the captor carries it (passenger, `CaptivePose` client), pylons (`ChitauriBeacon`) hold ≤ 6, any end
  of the war / captor death / pylon down releases it in place with its state; the ledger is `SavedData`
  (`ChitauriCaptives.Data`), so a captive whose chunk unloaded (or a save/quit mid-war) walks free on load. During a war soldiers do
  not auto-target civilians (`ChitauriMob.civilianTarget`) — only captors go for them.
- Units: `ChitauriCaptor`, `ChitauriHeavy` (armor plate + vent weak point), `ChitauriCommander` (≤ 2 calls,
  each gap 400 ticks, paid from the budget), `ChitauriInterceptor` (air-only hunter), `ChitauriSiege` (telegraphed
  lance with ground marker). Renderers `ChitauriEliteRenderer` (shared, per-unit clip sets via the existing
  `client/anim` geo/AnimCache system), `ChitauriInterceptorRenderer`, `ChitauriSiegeRenderer`, `ChitauriBeaconRenderer`.
- FX kinds 14..23 (contiguous, `ChitauriPacketsTest`), PROTOCOL 23. Subtitle voice `chitauri` → `ChitauriPortrait`.
- `/chitauri start force seed <n>` makes a run reproducible; `/chitauri spawn <unit>` for QA (op 2).
