# Visual regression — trimmed smoke quads vs original full quads

Requirement (#150): **no visual degradation at all.** The change must produce the same image.

## Why it is exact by construction

* A puff is still the vanilla camera-facing quad: same centre, roll, size, colour, alpha, light, render type and
  submission order. Only its four corners move inward to the edge of the sprite's visible rectangle, and each
  corner gets the UV that the original quad had at that very point of the plane. The interpolated UV at any pixel
  inside the rectangle is therefore identical.
* Pixels outside the rectangle sampled only texels with α = 0 (rectangle padded by one texel, so linear filtering
  never mixes an edge texel in) → the soft shader discarded them anyway (`α < 0.004`).
* **Fog**: vanilla fog is computed per vertex and interpolated over the two triangles of the quad, so simply
  moving the corners would change fog inside a puff. Two exact paths:
  1. the whole puff is nearer than the fog start or entirely beyond the fog end (`fogNeutral`) → fog is the same
     on both quads → plain 4-vertex trimmed quad;
  2. otherwise (`TrimGeometry`) the rectangle is cut along the **same diagonal** as vanilla's triangles and every
     new vertex carries the fog distance the original triangle interpolates at that point; it is passed to
     `soft_particle.vsh` in unused lightmap bits (1/64 block resolution). The full quad is drawn only if a puff is
     more than 256 blocks away.
  Covered by `SmokeFogNeutralTest` (20 000 random quads, both fog shapes) and `TrimGeometryTest` (clip halves are
  exact and cover the rectangle; interpolated distance equals the original at random points; pack/unpack).
* Shader packs (Oculus) / mipmapped atlas: trimmed path disabled (`MeteorParticleRenderTypes.softSheetActive()`),
  the original code path runs unchanged.
* Resource packs: the rectangle is measured from the loaded texture at runtime, not hard-coded.

## Measured

Same frame rendered twice: client particle/entity ticks frozen (QA `freeze on`), camera fixed, only
`smoketrim on/off` toggled; screenshots (854×480 game window on a 1280×720 screen) compared channel-wise.

| Scene | Puffs | Max channel diff | Pixels with diff > 2/255 | Notes |
|---|---|---|---|---|
| Ruin smoke 22 m, RD 6 (fog guard on), run 1 | 388 | **2** | **0** | 122 px differ by 1–2 (float interpolation rounding) |
| Ruin smoke 22 m, RD 6, run 2 | 388 | **2** | **0** | |
| Noise floor: same setting captured twice | 388 | 1 | 0 | ≈1 000 px differ by 1 |
| Ruin smoke 22 m, RD 4, **before the fog guard** | 356 | 6 | 7 | puffs straddled the fog start (RD 4 → fog from ~58 m) |
| Collapse dust, RD 4, **before the fog guard**, thick fog | 994 | 20 | 97 884 | fog interpolation over 25-block quads — the reason the guard was added |
| Collapse scene, RD 6, clouds on | 574 | — | — | not comparable: falling fragments, clouds and block fire animate on wall-clock time even when frozen (on₁ vs on₂ differ by 151) |
| Ruin smoke 22 m, RD 6, set 1, **exact fog**, clouds off | 318 | 24 | **17** (0.004 %) | one 1-px line where a 25-block puff plane cuts the pyramid steps: depth-test precision along the intersection (same plane, but depth is interpolated from other vertices → last-bit rounding), the same thing a sub-pixel camera move does. Everything else ≤ 2 |
| Collapse dust in aftermath fog 0.14, RD 6, set 1, **exact fog** — inside the plume | 880 | **4** | 273 of 69 k (0.4 %) | before the exact-fog path the same kind of scene had max 20 and 97 884 px > 2 |
| same frame, whole image | 880 | — | — | the rest of the differences are an ember particle, fire-lit windows and fragments that keep animating while frozen (they differ between two shots of the same setting as well) |
| WebGL replica, 2400 puffs @40 m | 2400 | 1 | — | 21 392 channels differ by 1 (0.6 %) |
| WebGL replica, 1200 @25 m / 800 @60 m / 300 @80 m | — | 1 / 2 / 2 | — | |

Result: outside fog and with the exact-fog path inside fog the output is identical up to a few /255 of rounding
(below the frame-to-frame noise of the same setting). Not measurable here: a real GPU's rasteriser — please run
the check below once on a real card.

Screenshots: not committed (binary files cannot be pushed through the tool used for this PR). Re-create them with
`tools/perf/atheria_bench.sh <label>` → `run/perf-shots/<label>_C_on1.png` vs `<label>_C_off1.png`, and compare:
```bash
python3 - <<'PY'
from PIL import Image, ImageChops; import numpy as n
a, b = (Image.open(p).convert('RGB') for p in ('base_C_on1.png', 'base_C_off1.png'))
x = n.asarray(ImageChops.difference(a, b)).astype(int).max(axis=2); print(x.max(), (x > 2).sum())
PY
```

## How to check it yourself (real GPU)

1. Look at a ruin / collapse dust.
2. Restart with `-Dviltrumitecore.smoke.trim=false` (old full quads), same place → compare. Or in a dev client:
   QA harness `freeze on`, `smoketrim off`, `smoketrim on`.
3. Anything that looks different → report with both screenshots in #150.
