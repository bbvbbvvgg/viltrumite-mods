# Memory bank — Atheria living city (branch feat/atheria-living-city, core 1.57.0)

## Shape
- Package `city/`: `CityManager` (one per server, only for a world that has `viltrumite_city/profile.json`),
  `CityPopulation`, `CityCommands` (`/city`), `CityEvents` (Forge bus), `citizen/`, `danger/`, `profile/`, `restore/`.
  A world without that folder has no city: every hook returns at once.
- City catastrophes that use the city systems: Godzilla (`/godzilla`, core 1.70.0, protocol 28) — see
  `godzilla.md` (the `project.md` index is over the 64 KB GitHub-tool request limit, so it is linked from here).
- The map is not in the repository. The Atheria author (Tazluh, PlanetMinecraft) gives no redistribution licence.
  The repository holds only the tools (`tools/atheria/`) that turn the user's own download into a 1.20.1 world.
- Citizens (`viltrumitecore:citizen`) are `noSave`: they never go to disk. The city always starts OFF after a
  restart; only the density survives (`viltrumite_city/state.json`). No autospawn without `/city enable`.
- Citizens implement `entity/Bystander`. Heroes can hit them, but automatic target searches skip them:
  `HeroTargets.canAutoTarget` (= canHit && !Bystander) or `Bystander.is` in TargetLockManager, ThorTargets,
  Iron Man FlyBy, Sinclair ActScene, Homelander Focus. A new auto-target search must do the same.
- Citizen looks come from one synced int seed (`CitizenEntity.SKIN`): `CitizenLook.of(seed)` decodes sex, build,
  height, skin/eye/hair colours, 14 hair styles, 10 tops, 6 bottoms, 5 shoes, hats, bags, eyewear, beard, scarf.
  `CitizenTraits.of(seed)` uses other salts (bravery, curiosity, pace, reaction, sociability). No texture files:
  `CitizenPainter` paints 128x64 (left half = vanilla skin layout, right half = extra-part UVs, constants in the
  painter). Client cache `CitizenSkins`: LRU 320 textures keyed by `CitizenLook.textureKey()`, max 10 uploads per
  tick (an over-budget citizen borrows a cached texture for a tick).

## Living City 2.0 (core 1.63.0, branch feat/living-city-civilians-20261031)
- `DangerMap` zones have a danger `radius` and a larger `notice` radius (`defaultNotice = min(112, 2r+8)`).
  Radius 0 = curiosity only (people stop and look, never flee; never merged into a danger). `track(sourceId, ...)`
  moves the zone of a moving source instead of leaving a trail. `blast()` / `blastAt()` = shock waves that knock
  people down for `BLAST_WINDOW` ticks (explosions, heavy hits >= 20, combat noise >= 24, lightning, big bursts).
- `ThreatScan` (every 10 ticks, 88/96 around each in-arena player; without players around the crowd itself, <= 6
  anchors 64 apart) turns *kinds* of things into tracked zones: any `Enemy` (sized by `ThreatRules.hostile`;
  giants >= 4 blocks are projected onto the street under them and seen up to 112), projectiles >= 0.45 b/t (zone
  led a few ticks ahead), lightning, a `Mob` with a live target within 24 (legion suits, summons) and fast low
  player flybys (curiosity only). New hostile mods need no city code as long as they implement `Enemy`.
- `CitizenState` (synced byte) drives server behaviour and client poses: IDLE/WALKING/TALKING/ALERT/STARTLED/
  FLEEING/COVER/FALLEN/GETTING_UP/RECOVERING. `CitizenBrain.think` runs every 5 ticks: contagion through
  `CrowdIndex` (8-block buckets rebuilt every 5 ticks; also counts flee-target claims so crowds spread out),
  1200-tick danger memory, flee paths scored away from the danger with cover raycasts.
- Client poses: `client/city/CitizenPoser` on the shared `PoseRig` (one weight per state, exponential smoothing on
  the age clock, `rig.finish()` once); knock-down tilt is in `CitizenRenderer.setupRotations` (seed bit 29 = back
  or front). Model `CitizenModel` = PlayerModel + child cubes (hair, hats, skirt, bags, shoes, collar).
- Population rules: citizens in chunks that stopped ticking entities (beyond the simulation distance, inside
  FAR=100) are removed while someone is in the level — they froze mid-flight and stayed "fleeing" forever.
  Lowering the density trims the automatic crowd above per-player x players (+25% + 4), farthest first, never
  within 40 blocks of a player (`CityPopulation.trimCount`). Console `/city populate` spawns at the world spawn.
- `CitizenNodeEvaluator` keeps the air right above a lower grid cell (<= max fall distance) open, so paths can
  step/drop down off raised cells (benches, planters, log tops are PREFERRED cells too). Before, 16 of 98 citizens
  spawned there and never moved. Going up still needs a standable cell one block higher (vanilla jump).
- `/kill` on a Leviathan: command and void kills bypass its per-hit cap (`ChitauriRules.leviathanHit`).
- The Chitauri portal opening adds radius-0 curiosity zones (people stop and watch, ~95 of 106 alarmed in QA).
  During a full invasion no citizen spawns where a danger is noticed, so the streets around the fight empty out.
- Config: `cityCrowdScale` (2.25, multiplies perPlayer and the profile cap) and `cityMaxCitizens` (480, hard cap);
  `CityManager.ABSOLUTE_MAX` = 640. `/city status` prints a crowd line (calm/alarmed/fleeing/hiding/threats).

## Change journal and reset
- Hook: `CityJournalMixin` on `LevelChunk.setBlockState` HEAD → `CityManager.onSetBlock`. It sees every block change
  of loaded chunks (players, explosions, hero powers, meteors, commands), not world generation (ProtoChunk).
- The journal keeps the first original state (+ block entity NBT) per position. Later changes of the same position
  are ignored. Restore writes with `ChangeJournal.suppressed` set, so it does not journal itself.
- Same-block changes where only ambient properties differ (power, lit, distance, age, moisture, ...: list
  `CityManager.AMBIENT`) are not journaled. Reason: daylight detectors and redstone made ~1000 entries a minute on an
  untouched map. Only block replacements feed the danger bursts (>= 6 per chunk per tick), so ambient updates never
  make citizens flee.
- Journal writes: every 1200 ticks when dirty, soon after a journaled chunk unloads, on every `LevelEvent.Save`,
  and synchronously on stop. An interrupted reset sets `restoring` and resumes 100 ticks after the next start.
- Verified on a dedicated server: /fill + TNT + interrupted reset → after resume, 0 differing blocks against the
  untouched copy (`tools/atheria/analysis/compare.py`). Entities (item frames, paintings, armor stands) are not
  journaled and are not restored.
- `MeteorSpawnEvent` (random, config `meteorSpawnChancePercent`) also hits the city; reset repairs the craters.
- Crash safety (settling): a restored chunk keeps its originals in the journal's `settling` set until the 2nd
  `LevelEvent.Save` after its restore (Forge posts the event after the chunk save, the 1st one may race). A start
  with settling originals runs a quiet recovery `RestoreQueue` 100 ticks after load (`journal.recovery()` skips
  positions with newer main records). Changes of positions being restored are not journaled.
  Verified on Atheria: `kill -9` 6 s after "Map restored" (world on disk still destroyed) → recovery put back
  15895 blocks, 0 real differences; reset interrupted by `stop` → resumed on the next start, 0 real differences;
  signs/banners/pots got their NBT back, a chest placed on original air vanished without dropping its items.
- Ambient noise on an untouched map: grass→dirt, tall grass→air (a few dozen blocks the first minutes) are journaled;
  redstone ore `lit`, daylight detector `power`, leaves `distance` are AMBIENT and not restored (compare shows them).

## Walk network
- Tiles `viltrumite_city/walk/r.X.Z.bin`: int magic 0x56435731 ("VCW1"), int version 1, then zlib of a 128-byte
  chunk bitmap (32x32 chunks of the region) + 256 big-endian u16 per present chunk, index z*16+x.
  Cell value = (h2 + 512) | class << 13; h2 = standing height in half blocks; class 1 WALK (road), 2 PREFERRED.
- `walk.py` keeps only the built-up area (`urban_mask`: 16x16 tiles with >= 25% road/pavement and >= 10% road
  within two tiles, grown 32 blocks). Without the road condition the bare stone mountains count as pavement.
- Districts: anchors in `tools/atheria/districts.json`, read from the map's signs (`analysis/signs.py`). Blocks
  further than 900 from every anchor get the city name.

## Language
- City strings live in their own namespace `assets/viltrumite_city/lang/<locale>.json` (all 12 locales together).
  Reason: the core lang files are past the 64 KB GitHub MCP request cap.
- A dedicated server translates only from data packs (Forge `LanguageHook` reads `data/<ns>/lang/en_us.json`).
  `processResources` mirrors the city `en_us.json` there, so console and RCON show text, not keys.

## Testing
- `./gradlew runServer` does not forward stdin to the server: use RCON (`enable-rcon=true` in `run/server.properties`).
- `./gradlew :viltrumitecore:runClient -Pquickplay=<save>` opens a save directly (dev property).
- Smoke test without the licensed map: a flat world plus a synthetic `viltrumite_city/profile.json` and walk tiles
  (`tools/city/smoke_flat_city.py`, RCON client `tools/city/rcon.py`; flat surface feet y = -60, h2 = -120). A world with no players stops ticking
  entities after 300 ticks: `/forceload add` the test area before `/city populate`.
- QA on the real map (2 CPU / 4.2 GB box): direct server + GUI client launched from captured Gradle argv; run
  them from a copy of the compiled classes, recompiling under a running game crashes it (ClassNotFoundException).
  Server (1.8 GB RSS) + one client fit; a second client or Gradle next to both gets the server OOM-killed.
  Measured (1 player, Downtown): density 1 → ~100 citizens 10–13 ms/tick; 2.5 → ~270, 17–20 ms; 300 populated
  19–21 ms; full Chitauri invasion worst mean 31–37 ms, always 20 TPS.
- The prepared world has a custom dimension height (datapack `atheria_height`): Minecraft shows the
  "experimental settings" warning once per world. This is expected.
