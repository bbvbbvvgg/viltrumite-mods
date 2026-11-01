# City disaster aftermath (core 1.66.0, protocol 24)

Contract for parallel branches: `docs/city-aftermath-integration.md`. Code: `worldevent/aftermath/` (server), `client/aftermath/` (client).

## Invariants and decisions
- Aftermath never places or repairs blocks. Restoring is the city journal's job (`/city reset`); `CityManager` calls `CityAftermath.onCityReset` on start and on a resumed reset.
- Zones live in `SavedData` `viltrumitecore_aftermath` per level; age is game time, so stages continue after restart (checked live: 10 zones, ids, power flags and ages survived `stop` + relaunch).
- Hits merge into the nearest zone (`AftermathRules.merges`); a declared event id always maps back to its zone. A small hit on a big old ruin rejuvenates only part of it.
- `CityAftermath.hit` drops points outside the world (`AftermathRules.inWorld`: 16 below min, 32 above max, NaN). Found live: a `/summon`ed Leviathan without an invasion dives through the floor and reports its crash at y≈-1971.
- Parallel branches (`feat/city-infrastructure-disasters`, `feat/city-structural-collapse`) must call only `CityAftermath.registerDamage/registerCollapse/setPower/hint`; no aftermath class imports theirs.

## Client gotchas
- The first SYNC after login/respawn can arrive before the next client tick notices the new `ClientLevel`; `AftermathClient.followLevel` runs in the packet handler too, otherwise that tick wipes the zones and static zones are never re-sent (server view thinks the client has them).
- The vanilla particle shader discards fragments under 10 % alpha: faint big smoke puffs (alpha 0.12–0.5) became stair-stepped pixel blobs. `MeteorSmokeParticle` (all styles) uses `MeteorParticleRenderTypes.SOFT_SMOKE` = shader `viltrumitecore:soft_particle` (discard < 0.004) + linear atlas filter via `setBlurMipmap/restoreLastBlurMipmap`, UVs inset 1 %. Vanilla program when Iris/Oculus pack is active (`ThanosDissolve.shaderPackInUse`).
- Particle cost is overdraw: `AftermathFx` caps the live estimate (spawns in the last 256 ticks) at 2400 for "All"; the estimate runs high because crumbs live shorter (conservative).
- After a fresh hit a ruin's one-shots wait `ClientZone.holdTicks` (16–28 ticks): meteor impact + shockwave + fire roars already peak near 0 dBFS in the capture.

## QA harness
- `-Dviltrumitecore.aftermath.log=true`: server `AMQA server` (cost per server tick, all levels), `AMQA hit`; client `AMQA client` (zones, emitters, beds, one-shots, alive~, fps, cost, particles). Client lines appear only while it knows zones.
- Direct launcher without Gradle daemons (unverified outside this sandbox): copy `build/classes` + `build/resources` to a snapshot, reuse the runClient argv/env, override gameDir; ALSOFT `drivers=wave` captures float WAV.
- Software GL (llvmpipe) FPS is dominated by translucent overdraw; do not read it as GPU FPS.
