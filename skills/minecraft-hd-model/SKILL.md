---
name: minecraft-hd-model
description: Build high-quality Minecraft-style (Bedrock/GeckoLib geo.json) character and prop models with baked pixel-art shading — multi-part volumetric geometry, hand-designed faces, AO/hue-shifted textures, lint-clean UVs, previews and real-client verification. Use when creating or reworking any hero/mob/prop model in viltrumite-mods (Captain America, Avengers, Thor, Hulk, shields, weapons, vehicles).
---

# Minecraft HD model skill (shaded pixel-art pipeline)

Proven on Thor, Black Widow, Hawkeye, Hulk and Captain America / the vibranium shield in `bbvbbvvgg/viltrumite-mods`.
Everything is procedural Python (own work, CC0): geometry + painters -> `geo.json` + base64 PNG atlases.

## 0. Ground rules
- Never claim a check you did not run (lint, build, preview, real Minecraft client). Say what is unverified.
- Git checkpoint at least every 5 minutes: `git status` -> commit -> push -> verify local SHA == remote SHA. No force-push.
- Textures live as base64: `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/**.png.b64`
  (plain copies for previews go to `build/jinwoo_preview/textures/...`). Geometry: `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/**`.
- Run generators from the repo root with `PYTHONHASHSEED=0` (deterministic output).
- Do not touch other characters' assets unless asked. Keep bone names / pivots used by Java and animation clips.

## 1. Pipeline files (in the repo)
| File | Role |
|---|---|
| `tools/assets/jinwoo_geo.py` | `Model/Bone/Cube/Face` base, Bedrock JSON writer, `write_png` (b64) |
| `tools/assets/clean_geo.py` | `CleanModel` (gutter-padded atlas packing + edge dilation), `Flat` material, `write_model`, `star_mask`, `poly_mask` |
| `tools/assets/pixel_shade.py` | `Mat` shaded material, `Ctx` painter context, `finish()` bake: form light, bevel edges, micro texture, AO, Bayer-quantised tone steps, hue shift, spec |
| `tools/assets/avengers_common.py` | `rig()` player skeleton, `build_body()` (split arms, 4 fingers + thumb), seams/stitches/rim/studs/`hair_strands`, `side_x`, `sname` |
| `tools/assets/geo_lint.py` | UV bounds, island overlap, gutters, coplanar z-fighting check per model group (add your group to `GROUPS`) |
| `tools/assets/captain_preview.py`, `jinwoo_preview.py` | offline software renderer mirroring the game's bone math |
| `tools/assets/avengers_thor.py`, `avengers_hulk.py`, `avengers_black_widow.py`, `avengers_hawkeye.py`, `captain_model.py` | reference implementations |

Skill scripts: `scripts/render_views.py` (4-view sheet from geo + b64 textures, any commit), `scripts/compare.sh`
(before/after sheets via a temporary worktree), `scripts/checkpoint.sh`, `scripts/run_hero.py`. Template: `templates/hero_template.py`.

## 2. Coordinate conventions (get these right first)
- Model space: x, y up, z. **Front = -Z (north face).** Seen from the front, **+X is the viewer's right** (the renderer
  mirrors X), i.e. the character's LEFT limbs are at +X. Face pixel maps: column 0 = x min = viewer's left.
- `Face.world()` gives per-texel unrotated local coords; `north`: u -> +x, v top->bottom; `east`: u -> +z;
  `west`: u -> -z; `up`: v=0 at the back (+z).
- Texel size of a face = `ceil(size * density)`. **Use sizes that are multiples of 1/density** (0.5 px at density 2) for
  faces carrying pixel maps, otherwise texels stretch unevenly.
- Cube rotation (pixel_shade.rot_model == game): `R = Rz(-rz) · Ry(ry) · Rx(-rx)`.
  * `rx > 0` with the pivot at the front end lifts the back end (wings, feathers, straps sweeping back/up).
  * `rz > 0` with the pivot at the top swings the bottom towards -X (viewer's left) — e.g. the two legs of a letter "A".
  * A cube has ONE pivot. For "rotate around the shield axis AND tilt", put the Z rotation on a **bone** (pivot at the
    origin) and the tilt on the cube (pivot at its inner edge). Bone rest rotation is supported by the writer, preview, AO and lint.
- `side_x(s, x0, w)` mirrors a part spanning [x0, x0+w] on the left (s=1) to the right (s=-1). Outer face: `east` if s>0 else `west`.

## 3. Materials and painters
```python
Mat(base, kind, fn=None, ao=0.5, hue=1.0, quant=0.05, cool=None, warm=None, alpha_fn=None, sheen=None,
    edges=True, micro=1.0, light=True, grad=None, cyl=None, spec=None, locks='v')
```
- `kind` in KIND: cloth, suit, leather, metal, skin, hair, paint, rubber, glass, flat -> (grad, cyl, edge_hi, edge_lo, edge_side, micro, spec).
- Painter `fn(ctx)`: `ctx.put(mask, colour, amount)`, `ctx.light(mask, delta)` (**delta is a scalar**; for per-texel
  arrays write `ctx.L += array`), `ctx.emit(mask, colour)` (glow layer), `ctx.pixels(rows, pal)` with pal values
  `colour` or `(colour, light_offset)`, `ctx.all()`, attributes `name, w, h, u, v, x, y, z, side, rs, face, mat`.
- Painter light values are **offsets** (finish adds 1.0). Final L is clipped to [0.35, 1.45].
- Glowing texels are kept unshaded in the base texture unless `mat.keep_glow = False` (use for charge glows that are only drawn sometimes).
- `alpha_fn(face, rs)` -> uint8 mask (cut-outs; render types are cutout-no-cull, so alpha 0/255 only).
- Skin defaults to warm-red shadows; hair: blond shadows should lean warm brown (`cool=(0.96, 0.80, 0.74)`), not grey.
- AO comes from real geometry: set `body.occluders = [gear]; gear.occluders = [body]` so meshes shade each other.
  Thin decals / alpha-cut quads must not occlude: `c(...).no_occlude = True`.

## 4. Quality standard (the bar the user accepted)
Geometry — multi-part, volumetric, readable silhouettes:
- split limbs (upper arm / forearm / hand / 4 fingers / thumb; thigh / shin), layered armour (two-tier pauldrons,
  plates over cloth), belts with buckle + pouches (body + flap + snap), boots (sole, shell, straps + buckles, fold
  cuff, toe cap, heel), back details (harness, cape folds at staggered depths, hair locks), side details.
- hair: crown + raised strands + fringe + side locks + framing locks + two-tier back locks, each a separate cube.
- faces: hand-designed 16x16 (or 14x14) pixel map; add real geometry for nose (bridge + tip), chin, cheekbones,
  beard when relevant. Keep heads proportional: helmet/hair outer width ~ torso width or less, face opening
  >= ~85 % of the head width; a visible neck in suit colour connecting head and collar.
- rounded forms in a blocky style: stepped tiers (dome = 3-4 shrinking layers), "plus" footprints (two crossing
  boxes + a corner box = octagon), chamfer layers; avoid one big box per part.
Textures — no large flat areas:
- several tone steps per material, soft dithered transitions (quant 0.03-0.05), AO in creases, lit top edges,
  cylindrical limb shading, hue shift (cool shadows / warm lights), controlled micro noise per material (weave,
  leather mottle, brushed metal, hair locks), seams / stitches / quilting rows / rivets / studs painted in model space.
- whites: soften shadows with `cool=(0.86, 0.9, 1.08)`; metal: spec 0.5-0.6; paint: 0.3-0.4.
Hygiene:
- no coplanar same-direction overlapping faces (offset >= 0.03-0.05; lint tolerance ~0.01). Opposite-facing touching faces are fine.
- drop faces that are always hidden (inside other cubes) via `faces=(...)` — smaller atlas, fewer artefacts.
- 1 px gutters are automatic (CleanModel); never hand-pack UVs.

## 5. Workflow per model (repeat until it passes)
1. Study the reference (film design): list parts, colours, silhouette features, proportions.
2. Plan in numbers: bone list (keep existing names/pivots), every cube origin/size, which faces, which material.
3. Write the module from `templates/hero_template.py` (palette -> pixel maps -> painters -> `M`/`G` dicts -> `body()`/`gear()` -> `build()`).
4. Generate: `PYTHONHASHSEED=0 python3 tools/assets/<module>.py` (or `scripts/run_hero.py <kind>`).
5. Lint: `python3 tools/assets/geo_lint.py <group>` -> 0 problems.
6. Preview 4 views (front / side / back / 3-4) + head close-up: `python3 skills/minecraft-hd-model/scripts/render_views.py ...`.
   Preview colours look brighter than texels (Lambert) — judge values numerically if unsure. If an image viewer refuses a PNG, re-save it as RGB.
7. Fix, regenerate, re-preview. Make before/after sheets with `scripts/compare.sh <old_sha>` for reworks.
8. Real client: `./gradlew build` (JAVA 17), run the client under Xvfb, check the model under game lighting and in
   motion (idle, walk, attacks, abilities), first person, multiplayer if possible; screenshot into
   `docs/reports/screens/<date>-<topic>/` (base64 if binaries are not wanted in git).
9. Checkpoint (commit + push + verify SHA) — at least every 5 minutes throughout.

## 6. Gotchas learned the hard way
- A painter that writes absolute light values instead of offsets washes the whole texture out.
- Alpha-cut discs as AO occluders darken empty corners: mark them `no_occlude` or use `ao=0` for that model.
- Rotated thin plates (feathers, straps) read as "sticks" if too long/thick: keep them short, 0.15-0.25 thick, fanned and overlapping.
- Big flat helmets/heads look like boxes: shrink 15-20 %, round the top with tiers, widen the face opening.
- Flat stacked discs give no side silhouette: convex surfaces need tilted ring segments with thickness
  (24 bones rotated about Z, one tilted segment per ring per bone) so the side view shows the curve and the back is concave.
- Face pixel maps: young faces need few dark texels (no under-eye bags, no nasolabial lines); mouth = one thin dark
  line + lighter lower lip; nostrils = subtle shade, not dark red blobs.
- Keep `left_shield`-style empty bones, FP arm models (`arms_only`) and vanilla 64x64 skins in sync after a rewrite.
- `geo_lint` groups pair body + gear: overlaps across the two meshes count too.
