# Performance (issue #150, branch perf/atheria-extreme-optimization, core 1.71.1)

Docs: `docs/performance/` (BENCHMARKS, BOTTLENECKS, MOD_COMPATIBILITY, RECOMMENDED_MODPACK, VISUAL_REGRESSION,
`modpack-manifest.json`). Tools: `tools/perf/` (fetch_mods.py, build_client_zip.py, atheria_bench.sh, mixin_scan.py,
smoke-overdraw-bench/).

## Decisions and invariants
- Smoke FPS loss is translucent **fill rate**: 25-block camera-facing quads, overdraw 50–100× at a ruin. Never "fix"
  it by fewer/smaller/fainter puffs — #150 forbids any visual change.
- `SpriteTrim` + `MeteorSmokeParticle.renderTrimmed`: draw only the sprite's visible rectangle (alpha>0, padded 1
  texel, measured from the loaded texture, cached per `SpriteContents`). Same corner order and UV per plane point as
  vanilla `SingleQuadParticle.render`.
- Fog is per-vertex in vanilla shaders → a smaller quad changes fog inside the puff (seen live: up to 20/255 in a
  collapse cloud at RD 4). Two paths keep it exact: `fogNeutral` (whole puff before fog start or past fog end) →
  plain 4-vertex trimmed quad; otherwise `TrimGeometry` cuts the rectangle along the vanilla triangle diagonal
  (y = x; QUADS 0-1-2/2-3-0) and every vertex carries the fog distance the original triangle interpolates there,
  packed into lightmap bits 8..14 of both UV2 halves (14-bit code, 1/64 block, code 0 = none, max 255.98 → beyond
  that the full quad is drawn). `soft_particle.vsh` decodes `(UV2 >> 8) & 0x7F` and masks the light with `& 0xFF`.
  Aftermath fog (density 0.14 → fog start ≈ far·0.3) puts most collapse dust inside the ramp — that is why the
  simple guard alone trimmed only 0.4 % there. Keep both if the trim shape ever changes.
- Trim only while our soft program is bound (`MeteorParticleRenderTypes.softSheetActive()`); shader packs/mipmaps
  keep the original path. Kill switch `-Dviltrumitecore.smoke.trim=false`; QA `smoketrim on|off` at runtime.
- Particle mods (AsyncParticles overwrites `ParticleEngine.render`; Particle Core / Smart Particles cull) are
  incompatible by design; see MOD_COMPATIBILITY.

## QA harness additions (SubtitlePlaytest)
- `perfreset` / `perfstate <label>` (FrameProbe: avg FPS, 1 %/0.1 % low, p50/p99/max ms, GC, heap, particles,
  entities, `smokeQuads=trimmed/total`), `freeze on|off` (client world pause → identical frames for A/B and pixel
  diff), `smoketrim on|off`, `clouds on|off` (clouds animate on wall-clock time even when frozen → off for pixel diffs), `rd <n>`, `tp x y z yaw pitch`.
- Lines are read every 5 client ticks; at <1 FPS a line written <4 s after the previous one can be lost.
- Repeatable scene: `tools/perf/atheria_bench.sh <label> [rd]` (pyramid at `1384 -44 -226`, ruin zone 22 blocks
  ahead, base cut `1368 -44 -244 .. 1400 -42 -210`). Killing the client without saving keeps Atheria intact; restore
  touched region files from the map ZIP otherwise.
- External mods in the dev client: `./gradlew :viltrumitecore:runClient -PperfMods=/abs/dir` (fg.deobf). Works for
  Embeddium, ModernFix, FerriteCore. Fails only in dev: ImmediatelyFast (SRG names in annotations, no refmap) and
  EntityCulling (jar-in-jar TRansition is not deobfuscated) — not production incompatibilities.
- Sandbox has no GPU (llvmpipe): absolute FPS are meaningless; only same-frame A/B ratios are.
