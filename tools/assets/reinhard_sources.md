# Reinhard asset sources

One row per committed asset. Raw archives are never committed (`reinhard_assets.zip`, given by the user in the Stage 1 session).

| Committed file | Source archive | Original path | Author | Conversion |
|---|---|---|---|---|
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/reinhard/emblem.png.b64` | `reinhard_assets.zip` | `icons/knight_emblem.png` (16×16) | grebeshok105 (user archive) | none, copied byte for byte; `tools/assets/make_reinhard_icons.py` |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/reinhard/hero.png.b64` | `reinhard_assets.zip` | `icons/reinhard.png` (32×32) | grebeshok105 (user archive) | none, copied byte for byte; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/reinhard/{mode_restrained,mode_knight,mode_holy_sword}.png.b64` | own work | `tools/assets/make_reinhard_icons.py` | Notion AI for grebeshok105 | drawn by the script (12×12: fist on white, gauntlet on blue, sword hilt on gold) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/reinhard/reinhard.geo.json` | `reinhard_assets.zip` | `reinhard_model/reinhard.obj` | grebeshok105 (user archive) | `tools/assets/convert_reinhard.py` → `tools/obj2geo.py`: six bones, vanilla pivots, +24 px, rotated boxes as cube `rotation`/`pivot` |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/reinhard/reinhard_arms.geo.json` | `reinhard_assets.zip` | `reinhard_model/reinhard.obj` (arms) | grebeshok105 (user archive) | same script, arms only, bones `armorRightArm` / `armorLeftArm` |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/reinhard/sheath.geo.json` | `reinhard_assets.zip` | `sword/blade_sheath.obj` | grebeshok105 (user archive) | same script: ×16, origin at the hilt guard, one bone `sword`; `dragon_glow` overlay faces dropped, vt measured from the top |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/reinhard/blade.geo.json` | `reinhard_assets.zip` | `sword/training_blade_drawn.obj` (+ `training_blade.obj` for the sheathed offset) | grebeshok105 (user archive) | same as the sheath; empty bone `sheathed_offset` pivot (0, −14, 0) |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/reinhard/{reinhard,reinhard_glow}.png.b64` | `reinhard_assets.zip` | `reinhard_model/{reinhard,reinhard_glow}.png` (128×128) | grebeshok105 (user archive) | none, copied byte for byte; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/reinhard/{dragon_sword,dragon_sword_glow}.png.b64` | `reinhard_assets.zip` | `sword/{dragon_sword,dragon_sword_glow}.png` (128×128) | grebeshok105 (user archive) | none, copied byte for byte; same script |
