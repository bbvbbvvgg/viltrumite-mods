# Recommended performance setup for Viltrumite (MC 1.20.1 · Forge 47.3.0 · Java 17)

**Rule for everything here: no visual change.** Nothing below lowers particles, smoke, render distance, entity
detail or effects. Mods that get FPS by drawing fewer smoke puffs are listed as incompatible in
[MOD_COMPATIBILITY.md](MOD_COMPATIBILITY.md).

## 1. Client — `client` profile (recommended)

| Mod | Version | Licence | Get it |
|---|---|---|---|
| Embeddium | 0.3.31+mc1.20.1 | LGPL-3.0 | https://modrinth.com/mod/embeddium |
| ModernFix | 5.27.91+mc1.20.1 | LGPL-3.0 | https://modrinth.com/mod/modernfix |
| FerriteCore | 6.0.1 (forge) | MIT | https://modrinth.com/mod/ferrite-core |
| ImmediatelyFast ¹ | 1.5.5+1.20.4 (forge) | LGPL-3.0+ | https://modrinth.com/mod/immediatelyfast |
| EntityCulling ¹ | 1.11.3 (forge, mc1.20.1) | tr7zw Protective (no re-upload) | https://modrinth.com/mod/entityculling |

¹ Embeddium, ModernFix and FerriteCore were run in Atheria with Viltrumite (idle +70 % FPS, −54 % heap, collapse
p99 −39 %, see [BENCHMARKS.md](BENCHMARKS.md)). ImmediatelyFast and EntityCulling cannot start in the ForgeGradle dev
client (dev-only remapping issue) and are checked statically only: start the pack once in your launcher and look
for `Mixin apply failed` in `logs/latest.log` before sharing it.

Optional: **Dynamic FPS** 3.11.4 (only throttles a minimised/unfocused window) → profile `client-plus`.
Shader packs: add **Oculus** 1.8.0 (`client-shaders`); costs FPS, our smoke then renders with the vanilla
particle program.

Exact files and hashes: [`modpack-manifest.json`](modpack-manifest.json).

### Install (automatic, hash-verified)

```bash
# from a clone of this repository
python3 tools/perf/fetch_mods.py --list
python3 tools/perf/fetch_mods.py --profile client --out "<minecraft>/mods"
```
The script downloads only from the official Modrinth CDN, checks SHA-512 **and** SHA-256, refuses a mismatching
file, and records what it installed in `<mods>/.viltrumite-perf-installed.txt`.

### Install (manual)

Download the five files above (Forge 1.20.1 builds!), drop them into `mods/` next to
`viltrumitecore-*.jar`, `viltrumiteflight-*.jar`, `geckolib-forge-1.20.1-4.4.9.jar`.
**Remove Rubidium / Xenon / OptiFine** if present (Embeddium replaces them; OptiFine breaks Forge rendering mods).

### Rollback

```bash
python3 tools/perf/fetch_mods.py --uninstall --out "<minecraft>/mods"
```
removes exactly the recorded files (nothing else). Manually: delete those five JARs. No world or config migration
happens — the mods only create their own files in `config/` (`modernfix-mixins.properties`, `embeddium-options.json`,
`immediatelyfast.json`, `entityculling.json`) which can be deleted too.

### Client ZIP for friends

```bash
python3 tools/perf/build_client_zip.py --viltrumite-jars ./mod-jars --profile client --out viltrumite-client.zip
```
`mod-jars` = the CI artifact of this branch/PR. EntityCulling is **not** put into a shared ZIP (its licence forbids
re-uploading); the ZIP contains `DOWNLOAD-SEPARATELY.txt` with the link and hash, and `LICENSES-THIRD-PARTY.txt`.
`--personal` bundles it for your own use only.

### In-game settings that cost FPS but no looks (Embeddium "Video Settings")

Leave graphics, particles (**All**), render distance, clouds and smooth lighting as you like them. Safe switches:
* **VSync off**, **Max framerate** = monitor refresh or *Unlimited* (VSync caps you, it does not make smoke cheaper).
* Embeddium → Performance: *Use Block Face Culling*, *Use Fog Occlusion*, *Use Entity Culling*,
  *Animate only visible textures* = **on** (default). These are exact (hidden things only).
* Fullscreen instead of a large window on Windows — avoids the compositor copy.
* `Simulation distance` 8 is plenty for Atheria (it is not a visual setting; render distance stays).

### JVM (launcher → Java arguments)

```
-Xms4G -Xmx6G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=50 -XX:+UnlockExperimentalVMOptions
-XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M
-XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15
```
6 GB is enough for Atheria + Viltrumite with FerriteCore/ModernFix; more heap only makes GC pauses longer.

## 2. Dedicated server — `server` profile

* **ModernFix** + **FerriteCore** (both sides, safe).
* `server.properties`: `view-distance` as the players need, `simulation-distance=8`, `network-compression-threshold=256`.
* JVM: same G1 flags, `-Xms6G -Xmx6G` for the Atheria map.
* **spark** only while diagnosing (`/spark profiler --timeout 60` during a collapse, `/spark tickmonitor`).
* Not recommended without a dedicated test: Canary/Radium/Harium (Lithium ports touching the same chunk/entity
  methods as collapse/time-freeze), ServerCore (changes AI timing).

Measured server cost of the city systems is tiny (aftermath `mean 6–7 µs/server tick`; collapse support probe
`1.8 ms over 2 ticks` for a 61-column cut) — the lag during a collapse is client-side (see BENCHMARKS.md).

## 3. What Viltrumite itself now does (this branch)

* Smoke/dust puffs (aftermath ruins, collapse dust, plumes, fire smoke) are drawn only over the sprite's visible
  rectangle: ~42 % fewer fragments per puff, pixel-identical (see [VISUAL_REGRESSION.md](VISUAL_REGRESSION.md)).
  Kill switch for comparison/bug reports: JVM arg `-Dviltrumitecore.smoke.trim=false`.
* Nothing else changes; no config is needed.

## 4. Falbiks Heroes

Not available for testing. Before shipping a pack with it: start the client once with the `client` profile, check
`logs/latest.log` for `Mixin apply failed` / `InvalidInjectionException`, and run a hero power + a building collapse.
