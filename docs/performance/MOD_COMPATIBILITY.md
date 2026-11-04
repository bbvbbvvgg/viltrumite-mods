# Viltrumite × optimization mods — compatibility (MC 1.20.1, Forge 47.3.0, Java 17)

Scope: Viltrumite Core 1.71.1 (this branch) and Viltrumite Flight, GeckoLib 4.4.9 at runtime.
Falbiks Heroes was **not available** in the test environment (no JAR in the repo or in Atheria), so every
"✅" below means *with Viltrumite only*; re-check with the friend's mod before publishing a pack.

Exact versions, URLs, licences and SHA-256/SHA-512 are in [`modpack-manifest.json`](modpack-manifest.json).

## How this was checked

1. **Static mixin scan** (`tools/perf/mixin_scan.py`): every `@Mixin` class of every candidate JAR (incl. jar-in-jar)
   and of our own compiled classes was parsed; targets and injector kinds were intersected per *class + method*.
   Raw data: [`data/mixin-overlap.json`](data/mixin-overlap.json).
2. **For each same-method overlap** the concrete injection point was compared (a `@Redirect` on the same call is a
   hard crash, `@Inject` + `@Inject` is fine, `@Overwrite` silently drops other mods' logic or makes it run on
   different code).
3. **In-game** (Atheria v2.06, dev client launched with `-PperfMods=<dir>`, see [BENCHMARKS.md](BENCHMARKS.md)):
   load, enter the world, collapse/aftermath scenarios, A/B screenshots.

Legend: ✅ tested in game (Atheria, dev client, RD 6, collapse + ruin smoke + idle), no problem · 🟡 statically compatible, not run in game · ⚠️ works but has a caveat ·
❌ do not use with Viltrumite.

## Verdicts

| Mod | Ver. | Side | Verdict | Why |
|---|---|---|---|---|
| Embeddium | 0.3.31+mc1.20.1 | client | ✅ recommended | Chunk renderer + entity/particle fast paths. Its `SingleQuadParticle.render` hook is not used by our trimmed smoke path (we emit 4 vertices ourselves), untrimmed puffs still go through it. Same-method overlaps with us: `GameRenderer.render` (Inject+Inject), `ClientLevel.getSkyColor` (their Redirect inside, our Inject at HEAD), `LevelRenderer.renderClouds/renderSnowAndRain` (our ModifyVariable on args vs their Inject/Redirect inside) — all composable. **Never together with Rubidium / Xenon / TexTrue's Embeddium Options** (declared incompatible). |
| ModernFix | 5.27.91 | both | ✅ recommended | Startup/RAM + bugfixes. Overlaps: `GameRenderer.render` Inject+Inject; `LivingEntityRenderer.render` — their Redirect targets `PoseStack.popPose`/event post, ours `EntityModel.setupAnim` → different call sites. |
| FerriteCore | 6.0.1 | both | ✅ recommended | Pure memory dedup of block states/models; no overlap with any Viltrumite mixin. |
| ImmediatelyFast | 1.5.5+1.20.4 | client | 🟡 recommended, not runnable in the dev client | Batches HUD/text/map rendering. Declares 1.20–1.20.4; the Forge 1.20.1 build is this one. Touches `Gui`/`ForgeGui` (we only Inject) — statically composable. In the ForgeGradle dev client it fails with `InvalidInjectionException` on `GuiGraphics.fill` (`m_285795_`) because its mixins use SRG names without a refmap; that happens only in a deobfuscated dev runtime, not in a normal launcher. **Check once in a real launcher** before shipping the pack. |
| EntityCulling | 1.11.3 | client | 🟡 recommended (download only), not runnable in the dev client | Skips entities/BEs behind walls. Overlap: `LevelRenderer.renderEntity`, `ClientLevel.tickNonPassenger` (Inject+Inject). Kaiju/heroes visible through open air are never culled; the async ray-cast only hides fully occluded ones. Dev client: its jar-in-jar Transition library is not remapped → `NoSuchMethodError m_91087_` (dev-only). **Licence forbids redistribution** → link, never bundle. |
| Dynamic FPS | 3.11.4 | client | 🟡 optional | Only throttles when the window is unfocused/minimised. |
| Oculus | 1.8.0 | client | ⚠️ shaders only | Hooks the same methods as Viltrumite (`GameRenderer.renderLevel`, `ItemInHandRenderer.renderArmWithItem`, `CapeLayer.render`, `EntityRenderDispatcher.render/renderShadow`, `EntityRenderer.shouldRender`, `ForgeGui.render`) — all Inject/ModifyArg, composable, and the existing code already detects shader packs (`ThanosDissolve.shaderPackInUse`). With a pack active our smoke falls back to the vanilla particle program and full quads (no trim gain) by design. A shader pack costs far more FPS than anything here saves. |
| spark | 1.10.53 | both | tool | Profiler. Use `/spark profiler --timeout 60` during a collapse to send us a report; remove afterwards. |
| AllTheLeaks | 1.1.4 | both | 🟡 optional | CurseForge-only, not hash-pinned. Fixes leaks on world change; only helps long sessions with many world switches. |
| Canary | mc1.20.1-0.3.3 | both | 🟡 optional, untested | Lithium port. Overlaps our collapse/city hooks: `LevelChunk.setBlockState` (their Redirect is `onPlace`/heightmap, ours `onRemove` → different calls), `Level.tickBlockEntities`, `ServerLevel.tickChunk`, `Entity.collide`, `Explosion.explode` (Redirect/ModifyConstant inside vs our Inject at HEAD). Statically composable, **not verified** with the collapse journal → only after a dedicated server test. Never with Radium/Harium. |
| Krypton (FNP) | 26.1.1 | both | 🟡 untested | Netty pipeline; no mixin overlap. |
| Saturn | 0.1.3 | both | 🟡 untested | Allocation fixes; no meaningful overlap. |
| Gnetum | 2.5.1 | client | 🟡 not needed | HUD frame skipping; duplicates ImmediatelyFast's job and touches `Gui` like we do. |
| Kerria | 1.3.2 | client | 🟡 not needed | No measurable effect on our scenarios. |
| Ixeris | 4.6.11 | client | 🟡 untested | Moves GLFW polling off-thread and hooks `MouseHandler` (we hook it too for hero controls). |
| BadOptimizations | 2.4.1 | client | ❌ skip | Hooks `ClientLevel`/`GameRenderer`/`LevelRenderer`/`LocalPlayer`/`Entity` around the same methods; gains (lightmap/sky caching) are below measurement noise and its particle-manager option conflicts with particle mods. |
| Flerovium | 1.2.19 | client | ❌ skip | Redirects inside `ParticleEngine.render` and `ClientLevel`, overlaps Embeddium; our smoke cost is fill rate, which it does not touch. |
| Lightspeed | 2.0.5 | client | ❌ skip | Entity render dispatch hooks overlapping Embeddium/EntityCulling and our `EntityRenderDispatcher`/`EntityRenderer` hooks. |
| ThreatenGL / OBE | 2.0.4 / 1.2.1 | client | ❌ skip | GL state / LevelRenderer hooks duplicating Embeddium; untested with our custom render types. |
| Radium / Harium | 0.12.4 / 2.0 | server/both | ❌ skip | Lithium ports with the same targets as Canary; pick at most one (Canary) and only after a server test. |
| ServerCore | 1.5.2 | server | ❌ skip | Activation-range/tick throttling Redirects in `ServerLevel.tickNonPassenger/tickPassenger/tickChunk`, `Entity.push`, `handleMovePlayer` — exactly where Viltrumite's time-freeze and AI hooks are. Changes NPC/kaiju timing = gameplay change. |
| FastEvent / Mixin Booster | 1.1.1 / 0.1.3 | both | ❌ skip | Replace event-bus dispatch / startup only; ModernFix already covers startup. |
| Memory Leak Fix | 1.1.5 | both | ❌ skip | Abandoned (Jan 2024); ModernFix covers the 1.20.1 leaks. |
| **AsyncParticles** | 20.1.4.4 | client | ❌ **incompatible** | `@Overwrite ParticleEngine.render` + async particle tick. Our `SOFT_SMOKE` render type (soft shader, linear atlas, custom blend) and the time-freeze `ModifyVariable` on `render` end up on foreign code; spawners in `AftermathFx`/`CollapseDust` call `ParticleEngine.add` from the render thread while it ticks elsewhere. Risk of wrong blending, flicker, CME. |
| **Particle Core** | 0.3.3 | client | ❌ **incompatible** | `WrapWithCondition`/`Inject` on `ParticleEngine.render/tick` to cull/limit particles → smoke puffs disappear (visible change). Also pulls fzzy-config + Kotlin for Forge. |
| **Smart Particles** | 20.1.10 | client | ❌ **incompatible** | Same idea (particle limits/culling on `ParticleEngine.tick`) → visible loss of dust/smoke. |

Not downloaded on purpose: **Rubidium** (dead, replaced by Embeddium), **Xenon** (Embeddium fork, conflicts),
**Starlight** (no 1.20.1 Forge build; 1.20.1's light engine already contains the rewrite), **Immersive Optimization**
and **AI Improvements** (change entity tick/AI → gameplay change; ARR licence), **C2ME** (alpha on Forge 1.20.1).

## Why no particle mod is recommended

Profiling (see [BOTTLENECKS.md](BOTTLENECKS.md)) shows the smoke cost is **fragment fill rate** of large translucent
quads, not particle count or CPU tick. Particle mods win FPS by drawing *fewer* puffs — that is exactly the visual
degradation the task forbids. The fix that keeps every pixel is in Viltrumite itself (trimmed quads).

## Falbiks Heroes (friend's mod) — not tested

The JAR (`falbiksheroes-PRE-BETA.jar`, modId `falbiksheroes` 0.1.0, sha256 `75d793e3…`, see
[`docs/venom/spiderman-compatibility.md`](../venom/spiderman-compatibility.md)) is **not in the repo or in this
environment**, so it was not loaded together with the optimisation mods. From its manifest:

| Its mixins | Who else touches it | Risk |
|---|---|---|
| `BlockEntity`, `LevelChunk`, `ServerLevel` | Canary/Radium (Lithium ports), ModernFix | low with set 1 (ModernFix only fixes allocation there); **unknown with Canary** → another reason to keep Canary out |
| `Player.attack` / `sweepAttack`, `ServerPlayer` (crit), `LivingEntity` (death) | none of the recommended mods | none expected |
| client `HumanoidModel.setupAnim`, `AbstractClientPlayer.getSkinTextureLocation/getModelName` | Embeddium does not touch models; EntityCulling only decides *whether* an entity is drawn | low |
| client `ClientLevel`, `LivingEntity` (poof particles) | Embeddium (`ClientLevel.getSkyColor`), EntityCulling (`tickNonPassenger`) | low; a particle-limiting mod would hide the poof → another reason for ❌ on particle mods |

Before publishing a pack with Falbiks Heroes: start once with set 1 + Falbiks + Viltrumite, become Spider-Man,
web a mob, collapse a building, and check the log for `Mixin apply failed` / `InvalidInjectionException`.
