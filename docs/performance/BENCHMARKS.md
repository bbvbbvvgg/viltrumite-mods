# Benchmarks — Atheria v2.06, smoke & collapse (issue #150)

> **Honest scope.** All numbers come from a cloud sandbox **without a GPU**: Mesa **llvmpipe** (software OpenGL 4.5,
> 2 vCPU, 4 GB RAM, `-Xmx1800m`) and Chrome **SwiftShader** for the WebGL replica. Absolute FPS is 10–50× lower
> than on any real graphics card. What transfers to a real GPU is the **relative** effect of things that reduce
> work of the same kind (fragments, chunk meshing, allocations). Please re-run the same script on a real PC
> (below) before quoting FPS to players.

## Setup

| | |
|---|---|
| Game | Minecraft 1.20.1, Forge 47.3.0, Java 17.0.21 (Temurin) |
| Mods | Viltrumite Core 1.71.1 (this branch), Viltrumite Flight, GeckoLib 4.4.9 (dev client, `./gradlew runClient`) |
| Map | **Atheria v2.06** (fresh copy before every run), city centre, player at `1384 -44 -250` |
| Video | 854×480 window (Xvfb 1280×720 screen), Fancy, particles *All*, FOV 70, VSync off, max FPS 260, clouds **on** except the fog runs |
| Render distance | 6 unless stated (4 and 8 also tried) |
| Driver | llvmpipe (LLVM 15.0.7, 256 bits), Mesa 24.2.6, Xvfb |
| Measurement | QA harness `perfreset` / `perfstate` (`FrameProbe`): avg FPS, 1 % low, p50 / p99 / max frame time, GC time, heap, particle count, `smokeQuads` = trimmed/total puff quads |
| Script | [`tools/perf/atheria_bench.sh`](../../tools/perf/atheria_bench.sh) — 20 s windows, every command ≥ 4 s apart |

### Scenarios

* **A — idle**: city centre, noon, clear weather, no smoke. Baseline for the optimisation mods.
* **C — destroyed building**: `aftermath collapse 14 1.0` 22 blocks ahead (impact-stage ruin smoke, 300–400 puffs).
* **B — collapse**: real collapse of the glass-pyramid base (`collapse cut 1368 -44 -244 1400 -42 -210`) watched
  from 34 blocks; first 30 s live (blocks falling, dust, chunk rebuilds), then frozen.
* **frozen A/B**: `freeze on` stops client particle/entity ticks, so *the very same particles* are drawn; only
  `smoketrim on/off` is toggled, twice each. This is the only fair way to compare a render path on a
  software renderer whose run-to-run noise is ±10 %.

## 1. Viltrumite smoke fix (trimmed quads) — in game

| Scene | RD | Mods | Puffs | trim **ON** FPS (p50 ms) | trim OFF FPS (p50 ms) | Gain |
|---|---|---|---|---|---|---|
| C ruin, frozen | 4 | — | 356 | 3.2 / 3.0 / 3.0 (308 / 322 / 331) | 2.2 / 2.4 / 2.3 (446 / 391 / 416) | **+33 %** |
| B collapse dust, frozen | 4 | — | 994 | 3.5 / 4.1 (271 / 239) | 2.7 / 3.0 (345 / 327) | **+34 %** |
| C ruin, frozen | 6 | — | 388 | 2.0 / 2.0 (483 / 464) | 1.8 / 1.6 (538 / 578) | **+18 %** |
| C ruin, frozen | 6 | set 1 | 312 | 3.0 / 2.9 (328 / 331) | 2.5 / 2.4 (395 / 415) | **+21 %** |
| B collapse dust, frozen, aftermath fog 0.14 | 6 | set 1, *first fog guard* | 915 | 2.3 / 2.4 (404 / 409) | 2.6 / 2.5 (383 / 389) | none — only 0.4 % of puffs trimmed |
| C ruin, frozen, clouds off | 6 | set 1, **exact fog** | 318 | 2.8 / 2.9 (339 / 332) | 2.3 / 2.4 (439 / 417) | **+22 %** |
| B collapse dust, frozen, clouds off | 6 | set 1, **exact fog** | 880 | 2.6 / 3.3 (367 / 307) | 2.7 / 2.4 (364 / 409) | **+16 %** avg (run 1 a tie), 100 % trimmed |
| C ruin, frozen | 8 | — | 1273 | 1.3 / 0.9 | 1.1 / 1.0 | noise (835 entities dominate) |

Run-to-run noise of the same setting on llvmpipe: ±0.2 FPS. *set 1* = Embeddium + ModernFix + FerriteCore. The RD 4 rows were measured before the fog guard (see
[VISUAL_REGRESSION.md](VISUAL_REGRESSION.md)); at RD 6 with thick aftermath fog the first fog guard fell back to
full quads (`smokeQuads` 135/32 265 trimmed, no gain) — that is why the exact-fog path was added: with it every puff in the collapse cloud is trimmed (`smokeQuads` 30 702/30 702) and the image stays the same.

## 2. Same fix — controlled WebGL replica (`tools/perf/smoke-overdraw-bench`)

Same shader maths, real `meteor_smoke_*` sprites, 1280×720, SwiftShader, 3 runs × 2 orders, raw data
`results-2026-11-04-swiftshader.json`.

| Scenario | Overdraw full → trimmed | Frame ms full → trimmed | Saving | Max pixel diff |
|---|---|---|---|---|
| C ruin, 2400 puffs @ 40 m | 77.3× → 47.0× | 4641 / 4373 → 2800 / 2626 | **−40 %** | 1 |
| C ruin, 1200 puffs @ 25 m | 98.3× → 60.1× | 4772 / 4313 → 2912 / 2492 | **−40 %** | 1 |
| B dust, 800 puffs @ 60 m | 14.1× → 8.7× | 668 / 624 → 345 / 371 | **−44 %** | 2 |
| A light haze, 300 @ 80 m | 1.16× → 0.71× | 49 / 72 → 37 / 46 | −25…−36 % | 2 |

On a real GPU the frame is fill-bound in exactly these scenes, so expect the same order (−30…−40 % smoke cost).

## 3. Optimisation mods — in game (RD 6, no shader pack)

| Scene | Vanilla + Viltrumite | + set 1 | Change |
|---|---|---|---|
| A idle FPS (p50 / p99 ms) | 3.7 (263 / 478) | **6.3** (153 / 222) | **+70 %** |
| A idle heap | 1285 MB | **592 MB** | −54 % |
| Start → in world (Atheria) | 64 s | **46 s** | −28 % |
| C ruin live FPS | 2.0 | **2.8** | +40 % |
| B collapse, first 30 s FPS (p50 / p99 ms) | 0.7 (1428 / 3075) | **1.1** (916 / 1876); with exact-fog trim **1.6** (369 / 1760) | +57 % / p99 −39 %; +130 % with the final trim* |

\* the collapse is not deterministic (fragments, dust spawn randomly), so live B numbers are ±20 %; the frozen
rows in §1 are the controlled comparison.

ImmediatelyFast and EntityCulling could **not** be loaded in the dev client (both ship SRG-named mixins/JiJ that the
ForgeGradle dev runtime does not remap; they load in a normal launcher). They are statically checked only — see
[MOD_COMPATIBILITY.md](MOD_COMPATIBILITY.md).

## 4. CPU / server side (why it is not the server)

| Probe | Cost |
|---|---|
| Server aftermath (ruin zones) | 6–7 µs per server tick |
| Collapse support probe | 1.78 ms once per collapse (2 ticks) |
| Client `AftermathFx` spawner | 29–53 µs per client tick |
| Collapse fragment rendering (`collapsestate renderMs`) | 1–4.5 ms average, 32 ms max |
| GC during smoke windows | 0–100 ms per 20 s |

## 5. Known spikes that remain

* **Moment of collapse** (B, first 30 s): hundreds of block changes → client chunk-section rebuilds + up to
  ~3 200 puffs at peak (0.3 FPS, p99 6.1 s on llvmpipe). Embeddium cuts it (p99 −39 %); the rest is chunk
  meshing on the CPU and is short.
* `B_after` (0.3–0.6 FPS, 3 400–7 600 particles) is a **test artefact**: while frozen the spawners keep queueing
  puffs, which all appear when the freeze ends. Not reachable in normal play.

## Reproduce on your PC

```bash
# dev client with the QA harness (fresh Atheria copy in run/saves/subtitle_playtest)
JAVA_TOOL_OPTIONS="-Dviltrumitecore.subtitles.playtest=true -Dviltrumitecore.subtitles.playtest.world=true" \
  ./gradlew :viltrumitecore:runClient [-PperfMods=/path/to/mod/jars]
# when in the world:
tools/perf/atheria_bench.sh mypc 6
grep -a 'SUBPT.*perf mypc_' viltrumitecore/run/logs/latest.log
```
Normal launcher (no harness): F3 + `spark profiler --timeout 60` while looking at a ruin, once with
`-Dviltrumitecore.smoke.trim=false` and once without.
