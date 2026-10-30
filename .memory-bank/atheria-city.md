# Memory bank — Atheria living city (branch feat/atheria-living-city, core 1.57.0)

## Shape
- Package `city/`: `CityManager` (one per server, only for a world that has `viltrumite_city/profile.json`),
  `CityPopulation`, `CityCommands` (`/city`), `CityEvents` (Forge bus), `citizen/`, `danger/`, `profile/`, `restore/`.
  A world without that folder has no city: every hook returns at once.
- The map is not in the repository. The Atheria author (Tazluh, PlanetMinecraft) gives no redistribution licence.
  The repository holds only the tools (`tools/atheria/`) that turn the user's own download into a 1.20.1 world.
- Citizens (`viltrumitecore:citizen`) are `noSave`: they never go to disk. The city always starts OFF after a
  restart; only the density survives (`viltrumite_city/state.json`). No autospawn without `/city enable`.
- Citizens implement `entity/Bystander`. Heroes can hit them, but automatic target searches skip them:
  `HeroTargets.canAutoTarget` (= canHit && !Bystander) or `Bystander.is` in TargetLockManager, ThorTargets,
  Iron Man FlyBy, Sinclair ActScene, Homelander Focus. A new auto-target search must do the same.
- Citizen skins are painted in code (`CitizenSkinPainter`, 16 outfits, even = wide arms, odd = slim). No texture files.

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
- The prepared world has a custom dimension height (datapack `atheria_height`): Minecraft shows the
  "experimental settings" warning once per world. This is expected.
