# Chitauri invasion asset sources

Every Chitauri asset is **own work** (CC0-1.0), generated deterministically by scripts in this repository. No
external samples, models, textures, recordings, film or game assets were downloaded, traced or converted, and no
vanilla Minecraft sound is used by the event. Raw intermediate files are never committed.

## Models, textures, animations

| Committed files | Generator | Notes |
|---|---|---|
| `geo/chitauri/soldier.geo.json`, `animations/chitauri/soldier.animation.json`, `animations/chitauri/soldier_states.animation.json`, `textures/entity/chitauri/soldier{,_glow}.png.b64` | `tools/assets/chitauri_soldier.py` (`chitauri_lib.py`, `jinwoo_geo.py`) | ~160 cubes, jointed fingers, armour plates, hoses, staff rifle with energy cell; clips idle, walk, run, aim, shoot, melee, hurt, fall, land, death, sever, mounted, mounted_shoot, mounted_pilot; off hand solved onto the rifle grip |
| `geo/chitauri/chariot.geo.json`, `animations/chitauri/chariot.animation.json`, `textures/entity/chitauri/chariot{,_glow}.png.b64` | `tools/assets/chitauri_chariot.py` | ~100 cubes: hull, insectoid prow, belly cannon, two engine pods, side blades, pilot console and grab bar; clips idle, cannon; top-level bones double as breakup pieces |
| `geo/chitauri/leviathan_{head,segment,tail}.geo.json`, `animations/chitauri/leviathan.animation.json`, `textures/entity/chitauri/leviathan_{head,segment,tail}{,_glow}.png.b64` | `tools/assets/chitauri_leviathan.py` | armoured head with hinged jaw, teeth, eyes and crest; one body ring drawn ten times (`LeviathanTrail.SCALE`); tail with fluke; clips head idle / bite / roar / hurt / dead, segment swim / dead, tail swim / dead |
| `geo/chitauri/{captor,heavy,commander}.geo.json`, `animations/chitauri/{captor,heavy,commander}{,_states,_special}.animation.json`, `textures/entity/chitauri/{captor,heavy,commander}{,_glow}.png.b64` | `tools/assets/chitauri_units.py` (+ `chitauri_units_geo.py`, shares `chitauri_soldier.py` joint layout and hand solvers) | elite infantry, 139-152 cubes each, own silhouettes/heads/weapons/special bones. Captor: snare lance with a forked clamp, back clamp arms, cuffs, padded shoulder for the captive. Heavy (renders x1.25): plasma drum cannon, reactor pack with vent flaps, breakable chest plate (`armor_chest`). Commander: gold crown, glaive with violet edge, 3-segment cape, antenna fins. Clips (21-23 per unit): idle, walk, run, turn_l, turn_r, aim, shoot, reload, melee, hurt, knockback, fall, land, death, sever, disembark, mounted, mounted_shoot + captor capture/carry/drop, heavy slam/vent/armor_break/stagger, commander idle_planted/signal/command/sweep/stagger |
| `geo/chitauri/{interceptor,siege,beacon}.geo.json`, `animations/chitauri/{interceptor,siege,beacon}.animation.json`, `textures/entity/chitauri/{interceptor,siege,beacon}{,_glow}.png.b64` | `tools/assets/chitauri_craft.py` | Interceptor (71 cubes): one-seat blade-winged craft with pilot, folding wings, nozzle glows; clips cruise, hover, boost, brake, takeoff, perch, turn_l, turn_r, accel, maneuver, fire, hit, death_fall. Siege lancer (76 cubes): hover sled with a ribbed lance rail and stabiliser legs; clips idle, move, deploy, charge, fire, stagger, dying. Containment pylon (66 cubes): tripod mast with a cage emitter; clips deploy, idle, hurt, down |

Paths are relative to `viltrumitecore/src/main/resources/assets/viltrumitecore/` (models, clips) and
`viltrumitecore/src/main/binassets/assets/viltrumitecore/` (textures). Portal, beam, storm clouds, bolts, sparks
and smoke are drawn in code (`client/chitauri/ChitauriPortalClient`, `GlowDraw`, `ChitauriVfx`) — no texture.

## Sounds

All 58 events (68 files; shots, impacts, chatter, steps and debris rotate 2-3 variants) are original procedural
synthesis by `tools/sfx/chitauri.py` (27 base events) and `tools/sfx/chitauri_units.py` (31 evolution events) (seeded numpy noise, oscillators, inharmonic partials, STFT filters,
synthetic reverb; CC0-1.0), mono 44.1 kHz Vorbis (bitexact), packed as `.ogg.b64` in
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/chitauri/`. Loops are built periodic (seamless).
`python3 tools/sfx/chitauri.py --check` (same for `chitauri_units.py`) re-synthesises and compares the decoded PCM. Events:
`viltrumitecore/src/main/sound-overlays/chitauri.json`; registry `worldevent/chitauri/ChitauriSounds`; subtitles in
all 12 locales (`src/main/lang-overlays/chitauri/`). `ChitauriAssetsTest` keeps registry, overlay, files and
subtitles in step and fails if the event's code references a vanilla `SoundEvents` constant.

| Event `chitauri.*` | Files (length, attenuation) | Trigger | Loop | Subtitle (en / ru) |
|---|---|---|---|---|
| `rifle_fire` | rifle_fire (0.50s), rifle_fire_2 (0.50s) | soldier shot (`ChitauriSoldier`, burst of 3) | one-shot | Chitauri rifle fires / Выстрел винтовки читаури |
| `rifle_charge` | rifle_charge (0.65s) | soldier charges the staff rifle before a burst | one-shot | Chitauri rifle charges / Винтовка читаури заряжается |
| `bolt_impact` | bolt_impact (0.55s), bolt_impact_2 (0.55s) | bolt hits a block or entity (`ChitauriBolt`), client FX echo | one-shot | Energy bolt hits / Попадание энергозаряда |
| `cannon_fire` | cannon_fire (0.95s) | gunship belly cannon (`ChitauriChariot`) | one-shot | Chariot cannon fires / Выстрел пушки колесницы |
| `explosion` | explosion (1.70s), explosion_2 (1.70s) | cannon bolt / chariot crash blast | one-shot | Energy explosion / Энергетический взрыв |
| `chariot_engine` | chariot_engine (2.00s attenuation_distance=56) | client loop per nearby chariot (`ChitauriLoops`, 4 nearest + 1 fade slot, rank hysteresis) | loop | Chariot engine whines / Гудит двигатель колесницы |
| `chariot_flyby` | chariot_flyby (1.90s attenuation_distance=32) | client: closest approach of a fast chariot; server: chariots leaving the portal | one-shot | Chariot flies past / Пролетает колесница |
| `chariot_hit` | chariot_hit (0.60s) | chariot takes damage | one-shot | Chariot armor clangs / Лязг брони колесницы |
| `chariot_explode` | chariot_explode (2.30s attenuation_distance=44) | chariot destroyed (breakup) | one-shot | Chariot explodes / Колесница взрывается |
| `soldier_chatter` | soldier_chatter (0.95s), soldier_chatter_2 (0.95s), soldier_chatter_3 (0.95s) | soldier ambient | one-shot | Chitauri chitters / Стрекот читаури |
| `soldier_hurt` | soldier_hurt (0.45s), soldier_hurt_2 (0.45s) | soldier hurt | one-shot | Chitauri hurts / Читаури ранен |
| `soldier_death` | soldier_death (1.20s) | soldier death | one-shot | Chitauri dies / Читаури погибает |
| `soldier_step` | soldier_step (0.20s), soldier_step_2 (0.20s), soldier_step_3 (0.20s) | soldier footsteps, also their fall/landing sounds | one-shot | Armored footsteps / Шаги в броне |
| `soldier_melee` | soldier_melee (0.50s) | bayonet strike | one-shot | Chitauri bayonet slashes / Удар штыком читаури |
| `leviathan_roar` | leviathan_roar (3.80s attenuation_distance=40) | Leviathan roar (shockwave), arrival title | one-shot | Leviathan roars / Рёв левиафана |
| `leviathan_loop` | leviathan_loop (4.00s attenuation_distance=150) | client loop per Leviathan within 150 blocks (`ChitauriLoops`) | loop | Leviathan rumbles / Гул левиафана |
| `leviathan_hit` | leviathan_hit (1.20s attenuation_distance=40) | Leviathan takes a big hit / stagger | one-shot | Leviathan armor booms / Гудит броня левиафана |
| `leviathan_smash` | leviathan_smash (2.00s attenuation_distance=42) | Leviathan ploughs through a building | one-shot | Leviathan smashes a building / Левиафан крушит здание |
| `leviathan_crash` | leviathan_crash (3.60s attenuation_distance=42) | Leviathan body hits the ground | one-shot | Leviathan crashes down / Левиафан рушится на землю |
| `leviathan_death` | leviathan_death (4.20s attenuation_distance=42) | Leviathan dies (falling arc starts) | one-shot | Leviathan dies / Левиафан погибает |
| `portal_open` | portal_open (4.60s attenuation_distance=34) | OPENING beam strike (sky tears) | one-shot | The sky tears open / Небо разрывается |
| `portal_loop` | portal_loop (5.00s attenuation_distance=64) | client loop while the portal is open (`ChitauriLoops.portalVolume`) | loop | Portal hums / Гудит портал |
| `portal_thunder` | portal_thunder (2.70s attenuation_distance=36) | portal lightning strikes, portal fully open | one-shot | Portal lightning cracks / Треск молний портала |
| `portal_close` | portal_close (4.20s attenuation_distance=34) | CLOSING / emergency stop collapse | one-shot | Portal collapses / Портал схлопывается |
| `debris` | debris (1.70s attenuation_distance=38), debris_2 (1.70s attenuation_distance=38) | rubble falling from Leviathan smashes | one-shot | Rubble falls / Падают обломки |
| `phase_horn` | phase_horn (3.20s attenuation_distance=40) | every phase change | one-shot | Chitauri war horn / Боевой рог читаури |
| `sever` | sever (2.70s attenuation_distance=34) | hive link cut at CLOSING: soldiers collapse, chariots and Leviathans fall | one-shot | Hive link breaks / Связь с ульем рвётся |
| `captor_grab` | captor_grab | Captor clamps a civilian (capture clip) | one-shot | Captor clamps a victim / Похититель хватает жертву |
| `captor_tether` | captor_tether | Captor snare lance shot / tether lash | one-shot | Energy tether lashes / Хлещет энергетический аркан |
| `captor_growl` | captor_growl | Captor ambient | one-shot | Captor growls / Рычит похититель |
| `heavy_cannon` | heavy_cannon | Heavy plasma cannon shot | one-shot | Heavy cannon fires / Выстрел тяжёлой пушки |
| `heavy_vent` | heavy_vent | Heavy overheat vent window (weak point open) | one-shot | Heavy vents steam / Тяжёлый стравливает пар |
| `armor_break` | armor_break | Heavy chest plate broken (damage reduction removed) | one-shot | Heavy armor shatters / Броня тяжёлого раскалывается |
| `heavy_slam` / `heavy_windup` | heavy_slam, heavy_windup | ground slam telegraph + impact | one-shot | Ground slam, Heavy winds up / Удар о землю, Тяжёлый замахивается |
| `heavy_hurt` / `heavy_death` / `heavy_growl` | heavy_hurt, heavy_death, heavy_growl | Heavy hurt / death / ambient | one-shot | Heavy grunts, collapses, breathes |
| `heavy_step` | heavy_step, heavy_step_2 | Heavy footsteps and landing | one-shot | Heavy footsteps / Тяжёлые шаги |
| `commander_staff` / `commander_sweep` | commander_staff, commander_sweep | Commander glaive bolt / melee sweep | one-shot | Commander staff fires, Staff sweeps |
| `commander_order` | commander_order | Commander rallies / re-targets the squad (no speech) | one-shot | Commander barks an order / Командир рявкает приказ |
| `commander_signal` | commander_signal (attenuation 160) | Commander calls a reinforcement drop (budgeted by the director) | one-shot | War horn calls reinforcements / Боевой рог зовёт подкрепление |
| `commander_growl` | commander_growl | Commander ambient | one-shot | Commander rumbles / Рокот командира |
| `interceptor_engine` | interceptor_engine (48) | client loop per nearby interceptor (`ChitauriLoops`, 3 nearest) | loop | Interceptor whines / Вой перехватчика |
| `interceptor_boost` / `interceptor_fire` / `interceptor_down` | interceptor_boost, interceptor_fire, interceptor_down | strafing run start / twin blasters / shot down | one-shot | Interceptor afterburner, blasters, goes down |
| `siege_hum` | siege_hum (64) | siege lancer idle hum | loop-like ambient | Siege lancer hums / Гул осадного копейщика |
| `siege_charge` / `siege_fire` / `siege_impact` | siege_charge (96), siege_fire (128), siege_impact (128) | lance charge telegraph (with ground marker), shot, shell blast | one-shot | Siege lance charging, fires, Siege shell explodes |
| `beacon_deploy` / `beacon_hum` / `beacon_down` | beacon_deploy (64), beacon_hum (24), beacon_down (96) | containment pylon planted in a seized sector / idle / destroyed (captives freed) | one-shot | Pylon deploys, hums, destroyed |
| `captive_freed` | captive_freed | civilian released (captor killed, pylon down, war end) | one-shot | Captive freed / Пленник освобождён |
| `sector_seized` / `sector_liberated` | sector_seized (200), sector_liberated (200) | district captured by the hive / retaken by players | one-shot | Sector falls to the hive, Sector liberated |

Known exceptions (block material sounds, not event sounds): a window hit by a bolt breaks through
`Level.destroyBlock`, so the glass plays its own block-break sound; debris blocks that land use their block's
place sound; a soldier falling into water splashes like any entity. The player's own hurt sound is vanilla.
