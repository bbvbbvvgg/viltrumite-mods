# Why smoke kills FPS — bottleneck analysis (Atheria, issue #150)

## TL;DR

* The FPS drop when looking at a destroyed building is **GPU fill rate (translucent overdraw)**, not particle
  count, not CPU tick, not the server.
* Every smoke/dust puff is a camera-facing quad up to ~25 blocks half-size. At 25–40 m a few hundred of them
  cover the screen **50–100 times per pixel** (measured overdraw 77–98× for a dense ruin).
* **~42 % of every puff quad is fully transparent texels.** The fragment shader still runs, samples the atlas,
  and only then discards them.
* Fix (this branch): draw each puff only over the visible rectangle of its sprite → overdraw −39 %, smoke frame
  time −35…−44 % in the controlled benchmark, +16…+34 % FPS in the frozen in-game A/B, same image (also inside
  the thick aftermath fog, see §6).

## 1. Where the smoke comes from

| Source (client) | Styles | Budget | Typical size |
|---|---|---|---|
| `client/aftermath/AftermathFx` (ruin zones after a collapse/impact) | RUIN, PLUME, HAZE, SETTLE, DUST, FIRE | 72 spawns/tick, live cap 2400, draw 224 m, detail 72 m | quadSize × growth up to ~25 blocks (PLUME/HAZE) |
| `client/collapse/CollapseDust` (falling building) | COLLAPSE_DUST, COLLAPSE_PLUME, street wall, chips | per collapse scene | 2–16 blocks |
| meteor trails / fire smoke | TRAIL, BILLOW, FIRE | small | 1–6 blocks |

All of them are `MeteorSmokeParticle` with the `SOFT_SMOKE` render type: shader `viltrumitecore:soft_particle`
(discards only `alpha < 0.004` so faint 12–45 % puffs keep their soft edge), linear atlas sampling, no depth
write, `SRC_ALPHA, ONE_MINUS_SRC_ALPHA`. Sprites: `meteor_smoke_0..7`, 64×64.

## 2. Measurements that point at fill rate

1. **Frozen-frame in game** (client particle/entity tick stopped with the QA `freeze on`, identical frame,
   only the render path toggled): with the same 356 puffs on screen the frame time drops from 418 → 320 ms
   (software GL) just by not rasterising transparent margins. Particle count, CPU work, draw calls and vertex
   count are identical in both runs — only the number of shaded fragments differs.
2. **Controlled WebGL benchmark** (`tools/perf/smoke-overdraw-bench`, same shader math, real sprites):
   frame time scales with overdraw, not with puff count — 1200 puffs at 25 m (98× overdraw) cost more than 2400
   puffs at 40 m (77×).
3. **CPU side is small**: `AftermathFx` tick `35–53 µs/tick` with 400–500 live puffs (`AMQA client cost=`),
   server aftermath `6–7 µs/tick`, collapse support probe `1.8 ms` once. Collapse fragment rendering
   (`collapsestate renderMs`) 1–4.5 ms average with ~120 fragments.

## 3. Sprite coverage (why trimming works)

| Sprite | texels α>0 | padded visible rect (share of quad) |
|---|---|---|
| meteor_smoke_0..7 | 25–42 % | 43–74 % (mean **57.9 %**) |

Convex hull would be 45 % (an octagon would save a bit more), but a rectangle keeps the exact vanilla UV
mapping with 4 vertices and zero extra CPU. The rectangle is computed **from the loaded texture at runtime**
(`SpriteTrim.of(sprite)`), padded by one texel so bilinear filtering never loses an edge texel, cached per
`SpriteContents` — resource packs that change the sprites are handled automatically.

## 4. Other bottlenecks found (not changed, documented)

| # | Finding | Impact | Recommendation |
|---|---|---|---|
| 1 | Moment of collapse: hundreds of `setBlock` → vanilla section rebuilds on the client (`B_live` window), plus 3–4.5 k puffs at peak | the 1–3 s hitch right at the collapse; on a real GPU mostly chunk-rebuild CPU | Embeddium (async, faster chunk meshing) is the only exact fix; the core already batches block removal |
| 2 | Particle frustum culling uses the vanilla bbox (0.2 × scale), so a 25-block puff whose centre is off-screen is culled while its edge is still visible | **visual pop-in at screen edges** when turning (pre-existing, not a perf regression) | enlarge the culling sphere to `quadSize·√2` (`shouldCull=false` + own sphere test); costs a few % more fill when turning, fixes the pop |
| 3 | Per-vertex `new Vector3f` in particle quads (vanilla does the same) | ~1–2 k short-lived objects/frame at peak; GC 0–80 ms per 20 s window in tests | negligible next to fill rate; left as is |
| 4 | Shader packs (Oculus): smoke uses the vanilla particle program and full quads | trim inactive by design (pack's G-buffer pipeline) | expected; shader packs cost more than they save |
| 5 | City population: 350–830 entities around the city centre at render distance 6–8 | entity rendering and AI ticks dominate the idle frame | EntityCulling (occluded only) + Embeddium; not a smoke issue |

## 5. What was **not** done on purpose

* No particle budget/LOD/count reduction, no shorter lifetime, no smaller puffs, no lower alpha — all would be
  visible.
* No change of fog, render distance, sky tint, or of the aftermath/collapse timing.

## 6. Aftermath fog — the case the first version missed

The ruin zone thickens the fog (`AftermathFx` fog 0.05–0.14 → fog start ≈ far·0.3, i.e. ~29 m at RD 6). A collapse
watched from 34 m is therefore *inside* the fog ramp. Fog is interpolated per vertex, so the first, simple guard
(trim only when fog is uniform over the puff) drew full quads for 99.6 % of the collapse cloud
(`smokeQuads` 135/32 265) — no gain exactly where it was needed. The final version reproduces vanilla's
per-triangle fog interpolation on the trimmed polygon (`TrimGeometry`, fog distance in spare lightmap bits) →
100 % trimmed in the collapse cloud, max difference 4/255 inside the plume.
