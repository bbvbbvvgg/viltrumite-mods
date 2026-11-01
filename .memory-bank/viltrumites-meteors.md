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

## Meteor Rain 2.0 (core 1.65.0): visuals, city fire, red sky, sound
- Gameplay is untouched: `MeteorRainRules`, `MeteorSpawnEvent.spawnPoint/launch`, speed, explosion 12, crater,
  no loot, commands. Only rain meteors (`MeteorEntity.ignitesCity`, NBT `CityFire`) start city fire.
- Renderer `client/render/MeteorRenderer`: magma core + additive heat shell + camera-facing glow halo + tapered
  plasma tail. Its render types (`Types`) are additive, no cull and **fog free** (an output-state shard sets
  `RenderSystem.setShaderFogStart(MAX)`): vanilla `eyes` fades additive colour to zero near the fog end and hid
  a meteor 200 blocks up. `shouldRender` expands the box along the tail; `shouldRenderAtSqrDistance` < 256².
- Particles are spawned with `mc.particleEngine.createParticle(...)` + `Particle.scale(f)`: it skips the
  vanilla 32-block cut of `addParticle` and lets one particle type have many sizes. Additive particle render
  type: `client/particle/MeteorParticleRenderTypes.ADDITIVE`. Budgets: trail 220 particles/tick for all
  meteors, zone smoke 48/tick, crater emitters ≤ 16 for 600 ticks.
- City fire `worldevent/meteor/fire/CityFire` (+ pure `FireRules`, `FireMaterial`): spots per dimension, a
  visit budget and a write budget per tick, loaded chunks only, every write is a normal `setBlock` (the city
  journal restores it on `/city reset`; a reset start clears the fire), fire ignores `doFireTick` (Atheria sets
  it false). `MeteorFireBlock` (`viltrumitecore:meteor_fire`) is kept alive by scheduled ticks only while
  `CityFire.sustains` it; rain puts it out. Fires outlive `/meteor stop` and burn out by fuel. Zones (10-block
  cells) go to the client every second (`MeteorRainFxS2CPacket.FIRE_ZONES`) and into the citizens' DangerMap.
- Red sky: `MeteorRainSky` (server) sends SKY on/off to players within 192 blocks (horizontal) of a rain owner
  in the same dimension, refresh every 100 ticks; `client/meteor/MeteorRainClient` pushes `SkyTint` priority 3
  (every hero scene wins) and pulls the fog in by up to 22 %. The tint lapses after 260 ticks without refresh.
  `SkyTint.Tint` has a cover colour (`cr, cg, cb, cover`): hue replaced at the vanilla luma, so night stays dark.
- Sound: 12 `meteor_rain.*` events (`MeteorRainSounds`, overlay `sound-overlays/meteor_rain_fx.json`), all
  played on the client (`MeteorRainAudio`, `CityFireClient`) with manual distance curves (attenuation NONE),
  speed-of-sound delay, impact voice limit, ≤ 3 meteor roars, ≤ 2 fire roars, ≤ 8 crackles/s. Recipe
  `tools/sfx/meteor_rain.py`, sources `tools/assets/meteor_rain_sources.md`. Push requests are capped at 64 KB,
  so every `.ogg.b64` stays < 48 KB (`MeteorRainAssetsTest`).
- Openverse in this sandbox: most searches timed out (> 150 s); "explosion" and "fire crackling" worked;
  downloads work. Cache the search JSON and do not retry in a loop.
- QA: `tools/city/meteor_fire_block.py` builds a synthetic block (concrete, stone, glass, brick, wood) over RCON;
  `-Dviltrumitecore.cityfire.log=true` logs spot counts and per-tick cost.
- Runtime QA findings (2026-11-01, Xvfb client, synthetic block): large particles must have smooth alpha and
  enough texels (smoke 64 px, flames 32 px, halo 128 px); stepped 16/32 px pixel art drawn 6-15 blocks wide
  became a mosaic of hard squares. Additive halos/flames fade out near the camera (`MeteorRenderer.haloFade`),
  otherwise a passing meteor whites out the screen. Long run: ~1600 spots (cap) after 5 min, TPS 20, city fire
  ~0.6 ms/tick; `/city reset` cleared the spots and restored ~27k blocks in one pass.
- Fire spots are not saved: after a server restart flames go out within seconds (by design). Spectator mode
  pauses the rain launches (gameplay, untouched). A hard kill (OOM) can leave charred blocks that the journal
  never saved; a clean run restores fully.
- Audio QA without speakers: `ALSOFT_DRIVERS=wave` + `ALSOFT_CONF` with `[wave] file=...` makes OpenAL write
  the real mix to a float WAV (analyse peaks/spectrum; peak > 1.0 = clipping). Impact layers carry
  `IMPACT_HEADROOM` 0.7 after a capture peaked at 1.8.
- The sandbox has ~4.2 GB RAM: running gradle while the client is up gets the client OOM-killed.
