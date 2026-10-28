# Thor asset sources

Archive `GoW_Thor_Kratos_assets` (given by the user): God of War HeroPack 3.0.0 for Fisk's Superheroes by SymbioteSpidey, with a Bedrock-geo conversion under `forge_1.20.1/assets/gowhp`. The raw archive is never committed. Permission of the pack authors: **not verified** (like Iron Man, "permission pending").

| Committed file (under `viltrumitecore/src/main/`) | Original path (`forge_1.20.1/assets/gowhp/`) | Author | Conversion |
|---|---|---|---|
| `binassets/.../textures/entity/hero/thor.png.b64` | `textures/heroes/thor/thor_layer1.png` + `thor_layer2.png` | TechnoGhosty (skin), SymbioteSpidey (pack) | layer2 composited over layer1, base regions opaque; `tools/assets/convert_thor.py` |
| `binassets/.../textures/entity/thor/mjolnir.png.b64` | `textures/heroes/thor/mjolnir.png` (512) | Szombie | 512 → 256, premultiplied 2×2 box; same script |
| `binassets/.../textures/entity/thor/mjolnir_glow.png.b64` | `textures/heroes/thor/mjolnir_lights.png` (512) | Szombie | same as above |
| `binassets/.../textures/entity/thor/beard.png.b64` | `textures/heroes/thor/thorbeard.png` | FatherKhimsky | copied byte for byte |
| `binassets/.../textures/entity/thor/belly.png.b64` | `textures/heroes/thor/thorbelly.png` | FatherKhimsky | copied byte for byte |
| `resources/.../geo/thor/mjolnir_hand.geo.json`, `mjolnir_belt.geo.json` | `geo/mjolnir.geo.json` | Szombie | scale 0.45 baked, box UV → per-face UV (mirror baked), re-rooted on `armorRightArm` / `armorBody`, compact JSON |
| ~~`resources/.../geo/thor/beard.geo.json`, `belly.geo.json`~~ | `geo/thorbeard.geo.json`, `geo/thorbelly.geo.json` | FatherKhimsky | removed in rework R1: superseded by the beard / belly bones of `thor.geo.json` |
| `resources/.../geo/thor/thor.geo.json`, `thor_arms.geo.json`, `animations/thor/thor.animation.json`, `binassets/.../textures/entity/thor/thor_body.png.b64` | derived from the skin, beard and belly textures above | own work over those textures | `tools/assets/make_thor_body.py` (rework R1 body: 17 bones, 128×64 atlas = skin + beard + belly sliced onto the new cubes) |
| `binassets/.../textures/gui/ability/thor/{thunder,onslaught,wrath}.png.b64` | own work | Notion AI for the owner (CC0) | `tools/assets/make_thor_icons.py`, 32 grid ×2 nearest |

Not imported: lightning eyes, concept skins, cloak, older Mjölnir, head display, all Kratos assets, sounds (license unclear), `.fsk` animations.
