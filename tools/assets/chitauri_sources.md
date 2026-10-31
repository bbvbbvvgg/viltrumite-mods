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

Paths are relative to `viltrumitecore/src/main/resources/assets/viltrumitecore/` (models, clips) and
`viltrumitecore/src/main/binassets/assets/viltrumitecore/` (textures). Portal, beam, storm clouds, bolts, sparks
and smoke are drawn in code (`client/chitauri/ChitauriPortalClient`, `GlowDraw`, `ChitauriVfx`) — no texture.

## Sounds

All 27 events (36 files; shots, impacts, chatter, steps and debris rotate 2-3 variants) are original procedural
synthesis by `tools/sfx/chitauri.py` (seeded numpy noise, oscillators, inharmonic partials, STFT filters,
synthetic reverb; CC0-1.0), mono 44.1 kHz Vorbis (bitexact), packed as `.ogg.b64` in
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/chitauri/`. Loops are built periodic (seamless).
`python3 tools/sfx/chitauri.py --check` re-synthesises and compares the decoded PCM. Events:
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

Known exceptions (block material sounds, not event sounds): a window hit by a bolt breaks through
`Level.destroyBlock`, so the glass plays its own block-break sound; debris blocks that land use their block's
place sound; a soldier falling into water splashes like any entity. The player's own hurt sound is vanilla.
