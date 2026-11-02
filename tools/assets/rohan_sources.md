# Rohan Kishibe / Heaven's Door asset sources

## Models, textures, animations — own work (CC0-1.0)

Generated deterministically by `tools/assets/make_rohan_assets.py` (`rohan_lib/*`, built on `dio_lib` geo/paint/clips).
No external model, texture, screenshot or fan art was downloaded, traced or converted; the look follows the written
canon description in `docs/rohan/heavens-door-design.md` §1.5.

| Committed files | Module | Notes |
|---|---|---|
| `geo/rohan/heavens_door.geo.json`, `textures/entity/rohan/heavens_door{,_glow}.png.b64` | `rohan_lib/heavens_door.py`, `rohan_lib/paint.py` | 29 bones (root, pelvis, waist, chest, neck, head, hair, hat band, see-through hat, bow, three coat panels, shoulder/forearm/hand/finger/thumb ×2, thigh/shin/foot ×2), 45 cubes, per-face atlas |
| `animations/rohan/heavens_door_{idle,appear,touch,write,guard,self,present,dismiss}.animation.json` | `rohan_lib/clips.py` | lengths and the touch contact tick follow `RohanRules` / `HeavensDoorClips` |
| `geo/rohan/rohan_outfit.geo.json`, `textures/entity/rohan/rohan_outfit.png.b64` | `rohan_lib/outfit.py` | side-swept hair, zigzag headband, pen-nib earrings, collar, hem, cuffs, belt, shoes, G-pen (`pen`), manuscript page (`page`) |
| `textures/entity/hero/rohan.png.b64` | `rohan_lib/skin.py` | 64×64 player skin (Part 4 palette) |
| `textures/entity/rohan/{page,page_ink,sketch,manuscript,streak}.png.b64`, `textures/gui/rohan/book.png.b64` | `rohan_lib/textures.py` | life-book page and its glowing ink, air sketch, manuscript, speed streak, GUI spread |
| `textures/gui/ability/rohan/*.png.b64` (6) | `rohan_lib/textures.py` | ability icons |

`python3 tools/assets/make_rohan_assets.py --check` regenerates everything and fails on any byte difference.
`rohan_lib/preview_player.py` renders review sheets (skin + outfit on a player rig).

## Sounds — CC0 1.0 Freesound recordings

See `docs/rohan/audio-sources.md` (cue sheet, per-event layers, per-recording links) and `tools/sfx/rohan.py`.
