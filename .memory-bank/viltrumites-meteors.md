# Viltrumite squad NPCs, manual world events, meteor rain (core 1.58.0)

## Squad NPC identity (fixed in the #93-#95 integration)
- Root cause of the "Viltrumites became Homelander" bug: `ViltrumiteFakePlayer` called
  `core.setViltrumite(true)`; `HeroId.legacySet` maps the old flag to HOMELANDER (Homelander replaced the
  race), so every squad NPC got the Homelander lifecycle and `HeroSkins`' Homelander provider overrode the
  NPC cosmetic skin/model/cape in `AbstractClientPlayerMixin`.
- Now the NPC identity is `HeroId.VILTRUMITE` through `ViltrumiteHero.becomeSquadNpc(ServerPlayer)`
  (identity only: no lifecycle, greeting, loadout), called after the dummy connection exists, then
  `HeroFlightGrant.sync` + `HeroRegistry.syncSnapshot`. `NoHeroBranchTest` forbids `HeroId.VILTRUMITE`
  outside the hero places, so keep it inside `ViltrumiteHero`.
- `CosmeticLoader` never extracted `skin_thula.png`/`skin_vidor.png` (same in the original 1.10.3 jar);
  both are in the extraction list now. `cape_viltrumite`/`cape_conquest` never existed (only red.png).
- `HeroSpeech.isRealPlayer` excludes `ViltrumiteFakePlayer` (they are in `level.players()` via a dummy
  connection and were speakers/listeners of hero lines).
- NPCs are not `@e[type=player]` matches in RCON counts here; use `@e[name=Lucan]` etc.

## World events are manual only
- No random scheduler in `CoreEvents.onLevelTick`; config `worldEventsAutoStart` removed;
  `WorldEventManager.keepsEvent` keeps manual events only; `load` does not resume saved events, so a
  restart ends every event (verified: trio+conquest with 600 s countdown before stop, none after start).
  `worldEventChancePercent`/`worldEventCooldownMinutes` remain unused because GUI/packet reference them.
- `/viltrumite event start <trio|conquest> [seconds]`, `stop <id>`, `stopall`, `list` (list drops a
  stopped event on the next tick).

## Meteor rain
- `worldevent/meteor/MeteorRainRules` (pure, tested) + `MeteorRain` (memory-only sessions per player on
  ServerTickEvent) + `command/MeteorRainCommands`: `/meteor start [targets]` (op 2), `/meteor stop`
  (self), `/meteor stop <targets>|all` (op 2), `/meteor status`.
- Uses the existing `MeteorSpawnEvent.spawnPoint()/launch()` and `MeteorEntity` unchanged (explosion 12,
  magma crater, VFX); rain meteors set `dropsLoot=false` (NBT `NoLoot`), so no infinity-gun parts.
- Bounds: delay 4-24 ticks, 20% bursts of 2-3, ring 6-56 blocks, 30% within 14, max 10 in flight per
  session, max age 600 ticks. Never loads chunks (hasChunkAt + entity-ticking at spawn and impact).
- Stops on stop command, logout, level unload, server stopping; nothing persisted. Stop = no new
  launches, in-flight meteors finish. Launches nothing while `TimeFreeze.worldStopped` (Reinhard N).
- The living city journals meteor damage like any other; `/city reset` restores it.

## Runtime check harness (2 CPU / 4 GB sandbox)
- Dedicated server `:viltrumitecore:runServer -Dorg.gradle.jvmargs=-Xmx700m`, RCON on, plus one client
  `runClient` (-Xmx1800m) under Xvfb with `--quickPlayMultiplayer localhost:25565`. After a server
  restart reconnect with Direct Connection `localhost:25565` (the LAN entry is IPv6 link-local and fails).
- QA commands need JVM flags on the server: `-Dviltrumitecore.reinhard.qa=true` (`/reinhardqa`),
  `-Dviltrumitecore.subtitles.qa=true` (`/subtitleqa hero <p> <hero>` also repairs the loadout).
- Ability slots start empty after `changeHero`: assign World Erasure to N in the K menu (drag). Reid
  sheathes after 60 s idle (`REID_IDLE_TICKS`), so run `/reinhardqa <p> mode_holy` right before N.
- Synthetic test city: tools/atheria `columns.py` + `walk.py`; light_gray_* used to be classified as road
  (fixed), which gave 0 preferred cells and no citizens.
