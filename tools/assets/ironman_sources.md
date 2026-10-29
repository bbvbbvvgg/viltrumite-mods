# Iron Man asset sources

One row per committed asset. Raw archives are never committed.

| Committed file | Source archive | Original path | Author | Conversion |
|---|---|---|---|---|
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/hero/ironman.png.b64` | own work | `tools/assets/make_tony_skin.py` | Notion AI for grebeshok105 | drawn by the script (64×64, classic arms); replaces the 1a file, which was a suit skin, not Tony |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/hero/ironman_mark_50.png.b64` | Satsu Iron Man Addon 3.6.1 (`Satsu_iron_man_addon-3.6.1.jar`; also in `IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `assets/satsu_iron_man_addon/textures/models/iron_man/no_light/mark_50_0.png` | Satsu | none (already a 64×64 player skin layout); `tools/assets/convert_ironman_stage1b.py` |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/hero/ironman_mark_50_glow.png.b64` | Satsu Iron Man Addon 3.6.1 (`Satsu_iron_man_addon-3.6.1.jar`; also in `IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `assets/satsu_iron_man_addon/textures/models/iron_man/light/mark_50_0.png` | Satsu | none; same script |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/mark_50/head_mask.geo.json` | Satsu Iron Man Addon 3.6.1 (`Satsu_iron_man_addon-3.6.1.jar`; also in `IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `assets/satsu_iron_man_addon/geo/armor_models/iron_man/nano_armors/marks/head_mask/main.geo.json` | Satsu | only the `armorHead` bone kept (empty bones dropped); same script |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/flames/feet.geo.json` | Satsu Iron Man Addon 3.6.1 (`Satsu_iron_man_addon-3.6.1.jar`; also in `IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `assets/satsu_iron_man_addon/geo/flames/below_flames_both_feet/flames_down.geo.json` | Satsu | leg cubes moved into child bones pivoted at the sole (length = bone scale); same script |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/flames/palms.geo.json` | Satsu Iron Man Addon 3.6.1 (`Satsu_iron_man_addon-3.6.1.jar`; also in `IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `assets/satsu_iron_man_addon/geo/flames/upper_flames_both_arms/flames.geo.json` | Satsu | arm cubes moved into child bones pivoted at the palm; same script |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/flames/stabilizer.geo.json` | Satsu Iron Man Addon 3.6.1 (`IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `geo/flames/stabilizer_flames/main.geo.json` | Satsu | the four flames on the nano stabilizer rod tips; each cube rotation moved to a child bone (scaleY = length); `tools/assets/convert_ironman_stage1b.py` |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/hero/ironman_flame_0..7.png.b64` | Satsu Iron Man Addon 3.6.1 (`Satsu_iron_man_addon-3.6.1.jar`; also in `IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `assets/satsu_iron_man_addon/textures/models/flames/new_thruster_white/0..7.png` | Satsu | alpha premultiplied into colour (additive `eyes` render type); same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/ability/ironman/*.png.b64` (18 ability icons: 8 kit, 7 mark signatures, 3 Hulkbuster) | own work | `tools/assets/ironman_icon_art.py` via `make_hero_icons.py` (house kit `icon_art.py`) | Notion AI for grebeshok105 | drawn by the script: 32×32 pixel art in the Reinhard icon style, saved 2× nearest as 64×64; replaces the make_ironman_icons / stage 2–5 / v3 icon art |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/ironman/helmet_frame.png.b64` | own work | `tools/assets/make_ironman_stage3_assets.py` | Notion AI for grebeshok105 | drawn by the script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/hero/ironman_interior{,_glow}.png.b64` | own work | `tools/assets/make_ironman_stage4_assets.py` | Hoplite for grebeshok105 | 128 px interior atlas of the opening shell (padding, spine, pistons, light strips) and its glow map; converted to grayscale (PR #19) and tinted per mark in the renderer (`make_ironman_forearm_launchers.py --lining`) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/nano/{nano_blade,nano_hammer,nano_shield,rocket_launcher,rocket_launcher_first_person,stabilizer}.geo.json` | Satsu Iron Man Addon 3.6.1 (`IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `geo/armor_models/iron_man/nano_armors/{nanokatar,nano_mallet,each_arms/right/rocket_launcher,each_arms/right/rocket_launcher_first_person,nano_stabilizer}.geo.json`, `additaments/shield/nano_shield.geo.json` | Satsu | right / left arm bones only, weapon cubes under a child bone at the fist; the shield is one 19 x 14 plate of the addon's shield faces on the forearm; `tools/assets/convert_ironman_stage2_parts.py` |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/ironman/nano/{nano_blade,nano_blade_glow,nano_hammer,nano_hammer_glow,nano_shield,rocket_launcher,stabilizer,stabilizer_glow}.png.b64` | Satsu Iron Man Addon 3.6.1 (`IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `textures/models/iron_man/nano_stuff/{nano_katars,nano_mallet,nano_rocket_launcher,nano_stabilizer}/{no_light,light}/mark_50_0.png`, `textures/models/shields/nano_shield.png` | Satsu | glow premultiplied; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/ironman/marks/energy_shield.png.b64` | Satsu Iron Man Addon 3.6.1 (`IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `textures/models/shields/nano_shield.png` (shape), `textures/models/electro_magnetic_shield/ark_force_field.png` (hexes) | Satsu | shield alpha filled with the tiled hexes in the repulsor colour, premultiplied; `tools/assets/convert_ironman_stage2_parts.py` |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/hero/ironman_{mark_7,mark_15,mark_17,mark_39,war_machine_mk2,iron_heart_mk3}{,_glow}.png.b64` | Satsu Iron Man Addon 3.6.1 (`IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `textures/models/{iron_man,war_machine,iron_heart}/{no_light,light}/…` suit textures and `…/mask/…` helmet textures (pairs from palladium render layers) | Satsu | `all_helmet` mask layers stacked into the head rows; glow premultiplied; `tools/bake_suit_skin.py` |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/marks/parts/*.geo.json (13 files)` | Satsu Iron Man Addon 3.6.1 (`IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `geo/armor_models/iron_man/each_part/*.geo.json` | Satsu | one armor bone pivoted at the piece centre; chest split into front / rear faces (`chest`, `back`); `*_arm_full` = arm + shoulder; `tools/assets/convert_ironman_stage4_marks.py` |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/marks/extras/{mark_7,mark_17,war_machine_mk2,iron_heart_mk3}.geo.json` | Satsu Iron Man Addon 3.6.1 (`IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `iron_man/mark_07/flaps`, `iron_man/heartbreakers/chest`, `war_machine/modules/war_machine_2_module` (turret bones dropped), `iron_heart/mark_03/{chest,right_shoulder,left_shoulder,right_leg,left_leg}` | Satsu | empty bones dropped, top bones lowercase with the empty-suit pivots; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/ironman/marks/{mark_7,mark_17,mark_39,war_machine_mk2,iron_heart_mk3}_suit{,_glow}.png.b64` | Satsu Iron Man Addon 3.6.1 (`IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | the raw suit textures of those marks | Satsu | glow premultiplied; same script |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/marks/{booster,booster_blast,turret}.geo.json, animations/ironman/marks/war_machine_turret.animation.json` | Satsu Iron Man Addon 3.6.1 (`IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `iron_man/mark_39/main.geo.json`, `geo/flames/marks/mark_39/main.geo.json`, `war_machine/torret/torret_mark_2.geo.json`, `animations/war_machine/torret_mark_02/torret_animation.geo.json` (`start_on`, `end`) | Satsu | flame child bone at the nozzles; turret under an aim bone; same script |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/marks/empty_suit{,_interior}.geo.json`, `animations/ironman/empty_suit.animation.json` | Satsu Iron Man Addon 3.6.1 (`IronMan_Hulkbuster_models.zip` → `source_original/Satsu`) | `iron_man/full_body/main.geo.json` + `all_helmet/main.geo.json` (`red` box) | Satsu | cut into a back half and hinged front plates with an interior lining (`tools/assets/opening_shell.py`), open / enter clips; `tools/assets/convert_ironman_stage4_marks.py` |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/mark_42/*.geo.json (14 pieces + 14 `_fire`)` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `models/tabula/mk42/*.tbl` | Sind | `tools/tabula2geo.py` (limb mode); helmet + faceplate; top bone pivot at the piece centre; `tools/assets/convert_ironman_sind.py` |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/ironman/mark_42/{pieces,pieces_glow}.png.b64` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `textures/heroes/mark42/{mark42,mark42_lights}.png` | Sind | glow premultiplied; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/hero/ironman_mark_42{,_glow}.png.b64` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `textures/heroes/mark42/mark42_layer2.png` + `mark42_layer1.png`; glow from `mark42_lights.png` | Sind | layer1 over layer2 (the renderer's "suit"); glow baked from the limb-shaped piece boxes; same script |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/marks/fist.geo.json` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `models/tabula/mk42/right_gauntlet.tbl` | Sind | centred on the origin; same script |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/marks/laser_emitters.geo.json, viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/ironman/marks/laser.png.b64` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `models/tabula/mk6/laser.tbl`, `textures/heroes/mark7/mark7_laser.png` | Sind | attached to the right arm; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/ironman/repulsor_fire_0..7.png.b64` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `textures/heroes/anim/repulsor_layer/{1,5,…,29}.png` | Sind | premultiplied; same script |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/hulkbuster/{mark48,jackhammer,fire,jackhammer_fire,fp_arm,parts}.geo.json` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `models/tabula/hulkbuster/{everything,left_arm,jackhammer,fire,fire_leftarm,jackhammer_fire,fp_arm}.tbl` | Sind | fp arms scaled 1.7 × 32/68 onto the vanilla arms; docking groups shrunk 32/68 onto the limbs; same script |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/animations/ironman/hulkbuster/mark48.animation.json` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `models/animations/hulkbuster/{hulkbuster_punches,hulkbuster_actions,hulkbuster_jackhammer,hulkbuster_smash_stomp,hulkbuster_summon}.fsk` | Sind | baked by `tools/fsk2anim.py` (drivers in the script); `grab_hold` = stage 5 own clip retargeted; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/ironman/{hulkbuster,hulkbuster_glow,hulkbuster_jackhammer_glow}.png.b64` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `textures/heroes/mark48/hulkbuster{,_lights}.png`, `textures/heroes/mark44/hulkbuster_arm_lights.png` | Sind | glow premultiplied; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/ironman/hulkbuster_jackhammer.png.b64` | `IronMan_Hulkbuster_models.zip` (prepared by the archive author) | `Sind_IronMan_Pack/hulkbuster/jackhammer_arm_mark48/obj/textures/jackhammer.png` | Sind (archive author's preparation) | as is; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/ironman/*.ogg.b64` except `thruster_loop`, `thruster_sonic` (59 files) | Kenney "Sci-Fi Sounds" 1.0 and "Impact Sounds" 1.0 (kenney.nl, CC0 1.0) | `Audio/*.ogg` of both packs (named per event in the script) | Kenney | layered, pitched, faded, loudness-normalized by `tools/sfx/ironman_cc0.py`; beeping events muted (empty lists in sounds.json) |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/ironman/{thruster_loop,thruster_sonic}.ogg.b64` | own work | `tools/sfx/ironman_stage1.sh` | Notion AI for grebeshok105 | ffmpeg synthesis (the only synthesized sounds left) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/missiles/{forearm_launchers,forearm_rockets}.geo.json`, `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/ironman/missiles/{forearm_launcher,forearm_rockets}.png.b64` | own work | `tools/assets/make_ironman_forearm_launchers.py` | Hoplite for grebeshok105 | forearm micro-missile pods with petals and rockets, grayscale (tinted to the mark in code); replace the Sind shoulder pods, which stay unused |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/ironman/missiles/{shoulder_launchers,shoulder_rockets,arm_rocket,missile}.geo.json`, `viltrumitecore/src/main/resources/assets/viltrumitecore/animations/ironman/missiles.animation.json` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `models/tabula/{cannons,rockets,armrocket}.tbl`, `models/animations/cannons.fsk` | Sind | pods and tips scaled 1.8x about each pod centre, panel clip baked and scaled; arm rocket 1.8x on the right forearm; projectile 2x; `tools/assets/convert_ironman_sind.py` |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/ironman/missiles/{launcher,rocket_tips,rocket}.png.b64` | Sind Iron Man pack 2.2.0 (`IronMan_Hulkbuster_models.zip` → `source_original/Sind`) | `textures/heroes/mark7/mark7_cannon.png`, `textures/heroes/rockets.png`, `textures/heroes/mark42/mark42_rocket.png` | Sind | as is; same script |


## Hulkbuster rework sounds (H6, 2026-10-28)

All own synthesis, CC0 1.0, no recording and no download: `tools/sfx/hulkbuster.py` (deterministic; `--check` re-synthesizes and compares). Files: `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/ironman/hulkbuster/<file>.ogg.b64` (mono Vorbis 44.1 kHz). The 11 stage 5 `sounds/ironman/hulkbuster_*.ogg.b64` files they replace are deleted; `hulkbuster_servo` stays (event `ironman_hulkbuster_servo`).

| File | Event | Class |
|---|---|---|
| `step_1` | `ironman_hulkbuster_step` | step |
| `step_2` | `ironman_hulkbuster_step` | step |
| `step_3` | `ironman_hulkbuster_step` | step |
| `step_4` | `ironman_hulkbuster_step` | step |
| `screech_1` | `ironman_hulkbuster_screech` | screech |
| `screech_2` | `ironman_hulkbuster_screech` | screech |
| `screech_3` | `ironman_hulkbuster_screech` | screech |
| `screech_4` | `ironman_hulkbuster_screech` | screech |
| `screech_5` | `ironman_hulkbuster_screech` | screech |
| `swing_1` | `ironman_hulkbuster_swing` | swing |
| `swing_2` | `ironman_hulkbuster_swing` | swing |
| `swing_3` | `ironman_hulkbuster_swing` | swing |
| `punch_1` | `ironman_hulkbuster_punch` | impact |
| `punch_2` | `ironman_hulkbuster_punch` | impact |
| `punch_3` | `ironman_hulkbuster_punch` | impact |
| `hammer_1` | `ironman_hulkbuster_hammer` | impact |
| `hammer_2` | `ironman_hulkbuster_hammer` | impact |
| `hammer_3` | `ironman_hulkbuster_hammer` | impact |
| `jackhammer_1` | `ironman_hulkbuster_jackhammer` | impact |
| `jackhammer_2` | `ironman_hulkbuster_jackhammer` | impact |
| `jackhammer_3` | `ironman_hulkbuster_jackhammer` | impact |
| `finish_punch` | `ironman_hulkbuster_finish_punch` | heavy |
| `charged_punch` | `ironman_hulkbuster_charged_punch` | heavy |
| `charge` | `ironman_hulkbuster_charge` | riser |
| `clamp_1` | `ironman_hulkbuster_clamp` | body |
| `clamp_2` | `ironman_hulkbuster_clamp` | body |
| `clamp_3` | `ironman_hulkbuster_clamp` | body |
| `clamp_4` | `ironman_hulkbuster_clamp` | body |
| `block_hit_1` | `ironman_hulkbuster_block_hit` | impact |
| `block_hit_2` | `ironman_hulkbuster_block_hit` | impact |
| `block_hit_3` | `ironman_hulkbuster_block_hit` | impact |
| `repulsor_charge` | `ironman_hulkbuster_repulsor_charge` | riser |
| `repulsor_beam` | `ironman_hulkbuster_repulsor_beam` | heavy |
| `dash_1` | `ironman_hulkbuster_dash` | swing |
| `dash_2` | `ironman_hulkbuster_dash` | swing |
| `cough_1` | `ironman_hulkbuster_cough` | body |
| `cough_2` | `ironman_hulkbuster_cough` | body |
| `land_1` | `ironman_hulkbuster_land` | impact |
| `land_2` | `ironman_hulkbuster_land` | impact |
| `slam_1` | `ironman_hulkbuster_slam` | heavy |
| `slam_2` | `ironman_hulkbuster_slam` | heavy |
| `grab_1` | `ironman_hulkbuster_grab` | body |
| `grab_2` | `ironman_hulkbuster_grab` | body |
| `throw` | `ironman_hulkbuster_throw` | swing |
| `hop` | `ironman_hulkbuster_hop` | impact |
| `ult_ignite` | `ironman_hulkbuster_ult_ignite` | riser |
| `ult_impact` | `ironman_hulkbuster_ult_impact` | heavy |
| `refuse` | `ironman_hulkbuster_refuse` | ui |
| `drop` | `ironman_hulkbuster_drop` | riser |
| `exit` | `ironman_hulkbuster_exit` | body |
| `assemble` | `ironman_hulkbuster_assemble` | riser |
| `break` | `ironman_hulkbuster_break` | heavy |
| `servo_loop` | `ironman_hulkbuster_servo_loop` | loop |
| `thruster_loop` | `ironman_hulkbuster_thruster_loop` | loop |
| `charge_loop` | `ironman_hulkbuster_charge_loop` | loop |
| `spark_loop` | `ironman_hulkbuster_spark_loop` | loop |
