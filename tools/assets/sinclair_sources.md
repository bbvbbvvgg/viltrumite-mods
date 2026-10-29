# Sinclair asset sources

Every Sinclair asset is **own work** made by the generator scripts below (Notion AI for grebeshok105,
2026-10-28). No external archive, recording, model, texture or vanilla sound/particle is used.
Licence of the generated files: CC0-1.0 (sounds) / repository licence (art). Re-run the script to rebuild.

## Models, textures, clips, icons

| Committed file | Source | Script | Notes |
|---|---|---|---|
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair/sinclair.geo.json` | own work | `tools/assets/make_sinclair_model.py` (+ `sinclair_geo.py`) | 32 px mannequin: body, head, hat, lapels, vest hem, medallion, two 2-segment coat tails, arm → forearm → hand → thumb/index/middle/ring (2 segments each), knees, `wand` + `wand_tip`, `card`, `held_hat` |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair/sinclair_arms.geo.json` | own work | same | first-person arms (same bone names, arms top-level) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair/savannah.geo.json` | own work | same | ghost assistant: hair chain, gloves, three dress tiers, three-bone smoke trail |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair/rabbit.geo.json` | own work | same | stage rabbit with two-segment ears and a tiny top hat |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/sinclair/{top_hat,chest,sword,card,curtain}.geo.json` | own work | same | act props |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/sinclair/{sinclair,savannah,rabbit,props}[_glow].png.b64` | own work | same (+ `sinclair_paint.py`) | procedural 16×16 material swatches (grain, weave, satin, velvet, gold…) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/animations/sinclair/*.animation.json` | own work | `tools/assets/make_sinclair_clips.py` | body, first-person, Savannah, rabbit and prop clips (split into files under 64 KB) |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/ability/sinclair/*.png.b64` | own work | `tools/assets/make_sinclair_icons.py` | five 64×64 ability icons |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/sinclair/{hero,emblem,act_card}.png.b64` | own work | same | hero badge 32×32, panel emblem 16×16, act card 16×16 |

## Sounds

All 31 sounds: original deterministic synthesis by `tools/sfx/sinclair.py` (seeded numpy oscillators,
noise, Karplus-Strong plucks, a small Schroeder room), CC0-1.0. No recording, no download.
Mono 44.1 kHz, Ogg Vorbis q5, peak-normalised per recipe. `--check` rebuilds and compares decoded PCM.
Not auditioned by a human; the in-game balance still needs a listening pass.

| Committed file | Event | Subtitle (en_us) | Recipe |
|---|---|---|---|
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/wand_shot.ogg.b64` | `viltrumitecore:sinclair.wand_shot` | Wand fires | `wand_shot()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/spark_hit.ogg.b64` | `viltrumitecore:sinclair.spark_hit` | Spark hits | `spark_hit()`, peak -5.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/wand_reload.ogg.b64` | `viltrumitecore:sinclair.wand_reload` | Wand twirls | `wand_reload()`, peak -5.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/bolt_cast.ogg.b64` | `viltrumitecore:sinclair.bolt_cast` | Vexing Bolt flicked | `bolt_cast()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/bolt_snap.ogg.b64` | `viltrumitecore:sinclair.bolt_snap` | Bolt redirected | `bolt_snap()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/bolt_hit.ogg.b64` | `viltrumitecore:sinclair.bolt_hit` | Vexing Bolt bursts | `bolt_hit()`, peak -2.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/assistant_appear.ogg.b64` | `viltrumitecore:sinclair.assistant_appear` | Savannah appears | `assistant_appear()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/swap.ogg.b64` | `viltrumitecore:sinclair.swap` | Places swapped | `swap()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/assistant_leave.ogg.b64` | `viltrumitecore:sinclair.assistant_leave` | Savannah takes her leave | `assistant_leave()`, peak -4.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/hex_cast.ogg.b64` | `viltrumitecore:sinclair.hex_cast` | Hat flourish | `hex_cast()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/hex_poof.ogg.b64` | `viltrumitecore:sinclair.hex_poof` | Turned into a rabbit | `hex_poof()`, peak -2.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/hex_end.ogg.b64` | `viltrumitecore:sinclair.hex_end` | Rabbit hex wears off | `hex_end()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/snap.ogg.b64` | `viltrumitecore:sinclair.snap` | Sinclair snaps and vanishes | `snap()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/decoy_reveal.ogg.b64` | `viltrumitecore:sinclair.decoy_reveal` | Decoy vanishes | `decoy_reveal()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/tada.ogg.b64` | `viltrumitecore:sinclair.tada` | Ta-da! | `tada()`, peak -2.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/refuse.ogg.b64` | `viltrumitecore:sinclair.refuse` | No volunteer | `refuse()`, peak -5.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/ovation.ogg.b64` | `viltrumitecore:sinclair.ovation` | Ovation | `ovation()`, peak -4.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/ult_capture.ogg.b64` | `viltrumitecore:sinclair.ult_capture` | Spotlight on a volunteer | `ult_capture()`, peak -2.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/ult_card.ogg.b64` | `viltrumitecore:sinclair.ult_card` | A card flies out | `ult_card()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_drumroll.ogg.b64` | `viltrumitecore:sinclair.act_drumroll` | Drumroll | `drumroll()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_beam.ogg.b64` | `viltrumitecore:sinclair.act_beam` | Footlight beam | `act_beam()`, peak -2.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_nova_whirl.ogg.b64` | `viltrumitecore:sinclair.act_nova_whirl` | Hat whirls | `act_nova_whirl()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_nova_burst.ogg.b64` | `viltrumitecore:sinclair.act_nova_burst` | Hat bursts into cards | `act_nova_burst()`, peak -1.5 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_cut_saw.ogg.b64` | `viltrumitecore:sinclair.act_cut_saw` | Sawing trick | `act_cut_saw()`, peak -2.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/card_hit.ogg.b64` | `viltrumitecore:sinclair.card_hit` | Card strikes | `card_hit()`, peak -4.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_card_volley.ogg.b64` | `viltrumitecore:sinclair.act_card_volley` | Card volley | `act_card_volley()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_curtain.ogg.b64` | `viltrumitecore:sinclair.act_curtain` | Curtain falls | `act_curtain()`, peak -2.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_curtain_open.ogg.b64` | `viltrumitecore:sinclair.act_curtain_open` | Curtain flies open | `act_curtain_open()`, peak -2.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_chest.ogg.b64` | `viltrumitecore:sinclair.act_chest` | Magic chest lands | `act_chest()`, peak -1.5 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_sword.ogg.b64` | `viltrumitecore:sinclair.act_sword` | Sword through the chest | `act_sword()`, peak -3.0 dBFS |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair/act_bow.ogg.b64` | `viltrumitecore:sinclair.act_bow` | Sinclair takes a bow | `act_bow()`, peak -2.0 dBFS |
