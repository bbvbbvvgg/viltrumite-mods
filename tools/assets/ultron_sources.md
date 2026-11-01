# ULTRON – ВОССТАНИЕ МАШИН: asset sources

Models, atlases and animation clips are **own work** (CC0-1.0), generated deterministically by the scripts in
`tools/assets/` (`ultron_lib.py` on top of `jinwoo_geo.py` and `chitauri_lib.py`). Original designs: no film,
game or mod model, texture or clip was downloaded, traced or converted. Sounds are layered from **CC0 1.0**
recordings (three Kenney packs) and own synthesis by `tools/sfx/ultron.py`. No vanilla Minecraft sound is used by
the event, and there is **no voice**: Ultron speaks only through subtitles (`UltronVoice`, 12 locales); a line
gets nothing but the short `transmission` blip. Raw recordings and intermediate files are never committed.

## Models, atlases, clips

| Machine | Committed files | Generator | Notes |
|---|---|---|---|
| Scout drone | `geo/ultron/drone.geo.json`, `animations/ultron/drone.animation.json`, `textures/entity/ultron/drone{,_glow}.png` | `tools/assets/ultron_drone.py` | 14 bones, 139 cubes, atlas 256x256; chrome sphere with one red optic, four ducted rotors on struts, whip antenna, folding twin-prong zapper. Clips (13): accel, death, decel, fabricate, fly, heavy_hit, hover, hurt, mark, scan, turn_l, turn_r, zap |
| Combat robot | `geo/ultron/sentry.geo.json`, `animations/ultron/sentry.animation.json`, `textures/entity/ultron/sentry{,_glow}.png` | `tools/assets/ultron_sentry.py` | 39 bones, 160 cubes, atlas 256x256; slender 2.2-block humanoid: layered chest and pauldron plates, ribbed abdomen and neck, pistons, jointed fingers, palm repulsors, back thrusters. Clips (20): accel, blast, combat_idle, death, decel, fabricate, fall, guard, heavy_hit, hurt, idle, land, lunge, run, strike_a, strike_b, strike_c, turn_l, turn_r, walk |
| Heavy assault robot | `geo/ultron/brute.geo.json`, `animations/ultron/brute.animation.json`, `textures/entity/ultron/brute{,_glow}.png` | `tools/assets/ultron_brute.py` | 42 bones, 211 cubes, atlas 512x256; hunched 3-block war machine: caged reactor, huge pauldrons, sledgehammer fists, twin four-tube missile pods with hatches, forearm shield emitter. Clips (20): accel, charge, combat_idle, death, decel, fabricate, fall, heavy_hit, hurt, idle, land, missiles, run, shield_hold, shield_up, slam, swing, turn_l, turn_r, walk |
| Flying interceptor | `geo/ultron/interceptor.geo.json`, `animations/ultron/interceptor.animation.json`, `textures/entity/ultron/interceptor{,_glow}.png` | `tools/assets/ultron_interceptor.py` | 16 bones, 55 cubes, atlas 256x128; arrowhead hunter with Ultron's eyes on the nose, swept wings with flaps, wing-root cannons, vectoring nacelles, V-tail, belly missile bay. Clips (14): accel, boost, burst, death, decel, dive, fabricate, fly, heavy_hit, hover, hurt, missile, turn_l, turn_r |
| Engineer-repairer | `geo/ultron/engineer.geo.json`, `animations/ultron/engineer.animation.json`, `textures/entity/ultron/engineer{,_glow}.png` | `tools/assets/ultron_engineer.py` | 25 bones, 98 cubes, atlas 256x256; four-legged walker: turret head, welding arm with torch, claw arm with data probe, seed canister and seed-driver spike. Clips (14): accel, death, decel, fabricate, hack, heavy_hit, hurt, idle, plant, run, turn_l, turn_r, walk, weld |
| Ultron Prime (boss) | `geo/ultron/prime.geo.json`, `animations/ultron/prime.animation.json`, `textures/entity/ultron/prime{,_glow}.png` | `tools/assets/ultron_prime.py` | 66 bones, 248 cubes, atlas 512x256; 2.9-block machined humanoid: layered head with moving lids, brows and hinged jaw, two-stage mechanical neck, breathing chest plates, jointed hands, back thrusters. Clips (32): absorb, accel, ascend, assemble, backhand, beam, combat_idle, dash, death, decel, dive, fall, flare, fly, hover, hurt, idle, jab_cross, land, overdrive_idle, palm_blast, run, slam, stagger, summon, talk, transition_air, transition_overdrive, turn_l, turn_r, uppercut, walk |
| Network nodes | `geo/ultron/node.geo.json`, `animations/ultron/node.animation.json`, `textures/entity/ultron/node{,_glow}.png` | `tools/assets/ultron_nodes.py` | 38 bones, 294 cubes, atlas 512x512; shared tower (plinth with anchor claws, ribbed pylon with Ultron's face) + six kind modules: core, uplink, foundry, bulwark, sensor, repair. Clips (11): boot, build, collapse, disabled, hit, idle_bulwark, idle_core, idle_foundry, idle_repair, idle_sensor, idle_uplink |
| Missile | `geo/ultron/missile.geo.json`, `animations/ultron/missile.animation.json`, `textures/entity/ultron/missile{,_glow}.png` | `tools/assets/ultron_nodes.py` | 3 bones, 12 cubes, atlas 128x64; body, fins and a stretched exhaust flame. Clips (1): fly |

Paths are relative to `viltrumitecore/src/main/resources/assets/viltrumitecore/` or, for large files, split under
`viltrumitecore/src/main/binassets/assets/viltrumitecore/` as `<file>.part0..N` (text) and `<file>.b64[.partN]`
(atlases), joined by Gradle at build time. Beams, tethers, shockwaves, sparks, scan cones, upload columns and the
HUD are drawn in code (`client/ultron/UltronVfx`, `UltronVfxParts`, `UltronWorldFx`, `UltronHudOverlay`).
`UltronAssetsTest` checks every clip animates bones of its model, every move and renderer finds its clip and bones,
and every atlas matches its model's UV size.

## Sounds

77 events, 89 files (steps, strikes, impacts, debris and shield hits rotate 2-3 variants), mono
44.1 kHz Vorbis (bitexact), packed as `.ogg.b64` in
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/ultron/`. Events:
`viltrumitecore/src/main/sound-overlays/ultron.json`; registry `worldevent/ultron/UltronSounds`; subtitles in all 12
locales (`src/main/lang-overlays/ultron/`). Loops are pure synthesis, periodic over exactly one loop length
(seamless), and only the client loop manager (`client/ultron/UltronLoops`) plays them. Levels are measured per role
(loudest 100 ms RMS, soft ceiling), not auditioned: the in-game mix still needs a listening pass.
`python3 tools/sfx/ultron.py --fetch` downloads the packs into `~/.cache/viltrumite-sfx/ultron/kenney`;
`python3 tools/sfx/ultron.py` rebuilds everything; `--check` re-mixes and compares the decoded PCM.

Openverse / Freesound was the first choice for the physical layers, but its API was degraded while the kit was
made (searches timed out at the proxy), so the recorded layers come from the Kenney CC0 packs instead
([Sci-Fi Sounds](https://kenney.nl/assets/sci-fi-sounds), [Impact Sounds](https://kenney.nl/assets/impact-sounds),
[RPG Audio](https://kenney.nl/assets/rpg-audio); each pack's `License.txt`: Creative Commons Zero, CC0 1.0 —
credit not required, given here). Changes: trimmed, pitched, filtered, layered with synthesis, enveloped,
level-matched.

### Cue sheet

| Event `ultron.*` | Files (length, attenuation) | Trigger | Type | Subtitle (en / ru) |
|---|---|---|---|---|
| `drone_hover` | drone_hover (2.00s att 16) | client loop per nearby drone (`UltronLoops`, nearest few, pitch follows speed) | loop | Ultron drone hums / Гудит дрон Альтрона |
| `drone_zap` | drone_zap (0.50s) | drone zap (`DRONE_ZAP`, contact tick 9) | one-shot | Drone zaps / Дрон бьёт разрядом |
| `drone_scan` | drone_scan (1.25s) | drone scan sweep (`DRONE_SCAN`) | one-shot | Drone scans / Дрон сканирует |
| `drone_mark` | drone_mark (0.75s) | drone paints a target for the others (`DRONE_MARK`) | one-shot | Drone marks a target / Дрон помечает цель |
| `drone_hurt` | drone_hurt (0.40s) | drone takes damage | one-shot | Drone damaged / Дрон повреждён |
| `drone_death` | drone_death (1.50s) | drone destroyed (breakup) | one-shot | Drone destroyed / Дрон разбит |
| `sentry_step` | sentry_step (0.45s att 18), sentry_step_2 (0.45s att 18), sentry_step_3 (0.45s att 18) | client footsteps of a walking sentry (gait phase in `UltronLoops`) | one-shot | Sentry footsteps / Шаги боевого робота |
| `sentry_servo` | sentry_servo (0.60s) | sentry joints drive a strike, the blast or the lunge | one-shot | Servos whir / Жужжат сервоприводы |
| `sentry_swing` | sentry_swing (0.45s) | sentry strike swing (A/B/C) | one-shot | Sentry swings / Боевой робот замахивается |
| `sentry_strike` | sentry_strike (0.70s), sentry_strike_2 (0.70s) | melee contact of a sentry (also brute swing, interceptor dive hit) | one-shot | Sentry strikes / Удар боевого робота |
| `sentry_blast` | sentry_blast (0.95s) | sentry palm repulsor blast (`SENTRY_BLAST`) | one-shot | Palm blast / Выстрел из ладони |
| `sentry_hurt` | sentry_hurt (0.45s) | sentry takes damage | one-shot | Sentry damaged / Боевой робот повреждён |
| `sentry_death` | sentry_death (1.80s) | sentry destroyed (breakup) | one-shot | Sentry destroyed / Боевой робот разрушен |
| `brute_step` | brute_step (0.75s att 28), brute_step_2 (0.75s att 28), brute_step_3 (0.75s att 28) | client footsteps of a walking brute | one-shot | Heavy brute footsteps / Тяжёлые шаги штурмовика |
| `brute_servo` | brute_servo (0.95s) | brute arms wind up (slam, swing, missile pods open) | one-shot | Brute hydraulics groan / Стонет гидравлика штурмовика |
| `brute_slam` | brute_slam (1.90s) | brute ground slam contact (shockwave) | one-shot | Brute slams the ground / Штурмовик бьёт о землю |
| `brute_swing` | brute_swing (0.70s) | brute hammer swing | one-shot | Brute swings / Штурмовик замахивается |
| `brute_missiles` | brute_missiles (1.50s) | brute shoulder pods fire a missile salvo | one-shot | Missile salvo / Залп ракет |
| `brute_charge` | brute_charge (1.30s) | brute charge run-up | one-shot | Brute charges / Штурмовик идёт на таран |
| `brute_shield` | brute_shield (1.00s) | brute raises its forearm shield | one-shot | Deflector raised / Поднят дефлектор |
| `brute_hurt` | brute_hurt (0.55s) | brute takes damage | one-shot | Brute damaged / Штурмовик повреждён |
| `brute_death` | brute_death (2.40s) | brute destroyed (breakup) | one-shot | Brute destroyed / Штурмовик разрушен |
| `interceptor_jet` | interceptor_jet (2.40s att 40) | client loop per nearby interceptor | loop | Interceptor engines roar / Ревут двигатели перехватчика |
| `interceptor_burst` | interceptor_burst (0.75s) | interceptor wing-gun burst (3 shots) | one-shot | Interceptor fires a burst / Очередь перехватчика |
| `interceptor_dive` | interceptor_dive (1.50s) | interceptor dive strike | one-shot | Interceptor dives / Перехватчик пикирует |
| `interceptor_boost` | interceptor_boost (1.00s) | interceptor evasive boost (after a hit) and the dive ignition | one-shot | Interceptor boosts / Форсаж перехватчика |
| `interceptor_hurt` | interceptor_hurt (0.40s) | interceptor takes damage | one-shot | Interceptor damaged / Перехватчик повреждён |
| `interceptor_death` | interceptor_death (1.80s) | interceptor destroyed (breakup) | one-shot | Interceptor shot down / Перехватчик сбит |
| `engineer_step` | engineer_step (0.22s att 14), engineer_step_2 (0.22s att 14), engineer_step_3 (0.22s att 14) | client footsteps of a walking engineer; brace before planting a seed | one-shot | Engineer legs clatter / Стучат лапы инженера |
| `engineer_weld` | engineer_weld (1.50s) | engineer welds a node (repair) | one-shot | Welding / Сварка |
| `engineer_hack` | engineer_hack (1.10s) | engineer hacks a golem or a Legion suit (`ENGINEER_HACK`) | one-shot | Engineer hacks / Инженер взламывает |
| `engineer_plant` | engineer_plant (1.20s) | engineer drives a node seed into the ground | one-shot | Engineer lays a node / Инженер закладывает узел |
| `engineer_hurt` | engineer_hurt (0.35s) | engineer takes damage | one-shot | Engineer damaged / Инженер повреждён |
| `engineer_death` | engineer_death (1.40s) | engineer destroyed (breakup) | one-shot | Engineer destroyed / Инженер разрушен |
| `prime_assemble` | prime_assemble (5.32s) | Ultron Prime assembles from the core (wide, 160 blocks) | one-shot | Ultron Prime assembles / Альтрон Прайм собирается |
| `prime_step` | prime_step (0.80s att 40), prime_step_2 (0.80s att 40) | client footsteps of Ultron Prime on foot | one-shot | Ultron's footsteps / Шаги Альтрона |
| `prime_whoosh` | prime_whoosh (0.55s) | Prime punch / slam wind-up whoosh | one-shot | Ultron swings / Альтрон замахивается |
| `prime_impact` | prime_impact (0.95s), prime_impact_2 (0.95s) | Prime melee contact (jab-cross, uppercut, backhand) | one-shot | Ultron strikes / Удар Альтрона |
| `prime_palm` | prime_palm (1.10s) | Prime palm blast release | one-shot | Ultron's palm blast / Импульс из ладони Альтрона |
| `prime_beam_charge` | prime_beam_charge (1.60s) | Prime charges the palm blast or the eye beam | one-shot | Ultron charges a beam / Альтрон заряжает луч |
| `prime_beam` | prime_beam (2.60s) | Prime eye beam pulses | one-shot | Ultron's beam / Луч Альтрона |
| `prime_slam` | prime_slam (2.00s) | Prime ground slam / dive landing (wide, 96 blocks) | one-shot | Ultron slams down / Альтрон обрушивается на землю |
| `prime_dash` | prime_dash (0.80s) | Prime thruster dash; take-off and dive ignition | one-shot | Ultron dashes / Рывок Альтрона |
| `prime_transition` | prime_transition (3.84s) | Prime changes stage (aerial, overdrive; wide) | one-shot | Ultron reconfigures / Альтрон перестраивается |
| `prime_hurt` | prime_hurt (0.60s) | Prime takes damage | one-shot | Ultron damaged / Альтрон повреждён |
| `prime_death` | prime_death (6.08s) | Prime destroyed (wide, 160 blocks) | one-shot | Ultron Prime destroyed / Альтрон Прайм уничтожен |
| `prime_ascend` | prime_ascend (2.20s) | Prime ascends at the end of the upload (wide) | one-shot | Ultron takes off / Альтрон взлетает |
| `prime_summon` | prime_summon (1.90s) | Prime calls the foundries (`PRIME_SUMMON`, wide) | one-shot | Ultron calls reinforcements / Альтрон зовёт подкрепление |
| `prime_absorb` | prime_absorb (2.30s) | Prime drains a live node for health | one-shot | Ultron drains a node / Альтрон пьёт энергию узла |
| `prime_jet` | prime_jet (2.00s att 48) | client loop while Prime flies | loop | Ultron's repulsors hum / Гудят репульсоры Альтрона |
| `node_hum` | node_hum (3.00s att 20) | client loop per nearby live node (nearest few) | loop | Ultron node hums / Гудит узел Альтрона |
| `node_build` | node_build (1.50s) | a node seed starts building | one-shot | Node under construction / Строится узел |
| `node_online` | node_online (3.20s) | a node comes online (wide) | one-shot | Node comes online / Узел выходит в сеть |
| `node_shield_hit` | node_shield_hit (0.60s), node_shield_hit_2 (0.60s) | a hit on a node shield | one-shot | Node shield struck / Удар по щиту узла |
| `node_shield_break` | node_shield_break (1.40s) | a node shield breaks | one-shot | Node shield breaks / Щит узла пробит |
| `node_disabled` | node_disabled (1.80s) | a node is disabled by a player hack (wide) | one-shot | Node disabled / Узел отключён |
| `node_reboot` | node_reboot (1.60s) | a disabled node reboots | one-shot | Node reboots / Узел перезагружается |
| `node_destroyed` | node_destroyed (3.76s) | a node is destroyed (wide, 128 blocks) | one-shot | Node destroyed / Узел уничтожен |
| `node_hack` | node_hack (0.60s) | player hack progress ticks on a node (pitch rises with progress) | one-shot | Node being hacked / Узел взламывают |
| `node_tether` | node_tether (2.00s att 64) | client loop on the tether beams between live nodes and Prime | loop | Node feeds Ultron / Узел питает Альтрона |
| `bolt_impact` | bolt_impact (0.50s), bolt_impact_2 (0.50s) | energy bolt hits; hack zaps; capture spark | one-shot | Energy bolt hits / Попадание энергозаряда |
| `missile_flight` | missile_flight (1.50s att 32) | missile launch / flight | one-shot | Missile whistles / Свист ракеты |
| `missile_explode` | missile_explode (1.80s) | missile explodes | one-shot | Missile explodes / Взрыв ракеты |
| `deflect` | deflect (0.50s) | a hit bounces off armour or a shield (no damage) | one-shot | Hit deflected / Удар отражён |
| `debris` | debris (1.40s), debris_2 (1.40s) | machine falls / breaks apart (fall sounds, debris) | one-shot | Debris falls / Падают обломки |
| `splash` | splash (1.00s) | a machine enters water | one-shot | Splash / Всплеск |
| `fabricate` | fabricate (1.80s) | a foundry fabricates a machine (pitch per kind) | one-shot | Foundry fabricates a machine / Литейная выпускает машину |
| `event_start` | event_start (5.24s) | the uprising starts (cue for every player in range) | one-shot | Ultron's network awakens / Сеть Альтрона пробуждается |
| `phase_change` | phase_change (4.44s) | phase change (cue) | one-shot | The uprising enters a new phase / Восстание переходит в новую фазу |
| `protocol` | protocol (2.32s) | Ultron activates or renews an adaptation protocol (cue) | one-shot | Ultron adapts / Альтрон адаптируется |
| `transmission` | transmission (0.60s) | short blip that marks one of Ultron's subtitle lines; relay chatter at nodes | one-shot | Ultron's transmission / Передача Альтрона |
| `capture` | capture (1.80s) | Ultron takes over a golem or a Legion suit | one-shot | Ultron seizes a machine / Альтрон захватывает машину |
| `release` | release (2.40s) | a captured golem or suit is freed | one-shot | Machine freed / Машина освобождена |
| `discover` | discover (1.74s) | a player discovers a hidden node (notify sound) | one-shot | Node discovered / Обнаружен узел |
| `upload_alarm` | upload_alarm (2.20s) | the upload starts / reaches its last stretch (cue) | one-shot | Alarm: upload in progress / Тревога: идёт загрузка |
| `victory` | victory (5.80s) | victory stinger | one-shot | Ultron's network falls / Сеть Альтрона пала |
| `defeat` | defeat (5.64s) | defeat stinger | one-shot | Upload complete / Загрузка завершена |

### Recordings used

| Recording (Kenney pack / file) | Licence | Used in |
|---|---|---|
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/footstep_concrete_000.ogg` | CC0 1.0 | sentry_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/footstep_concrete_001.ogg` | CC0 1.0 | engineer_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/footstep_concrete_002.ogg` | CC0 1.0 | sentry_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/footstep_concrete_003.ogg` | CC0 1.0 | prime_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/footstep_concrete_004.ogg` | CC0 1.0 | sentry_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactGlass_heavy_001.ogg` | CC0 1.0 | node_shield_break |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactGlass_light_000.ogg` | CC0 1.0 | node_destroyed |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_heavy_001.ogg` | CC0 1.0 | brute_hurt, prime_impact, sentry_strike |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_heavy_003.ogg` | CC0 1.0 | prime_hurt, sentry_strike |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_light_000.ogg` | CC0 1.0 | debris, drone_death, engineer_death, interceptor_hurt, node_build, prime_assemble, sentry_death |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_light_001.ogg` | CC0 1.0 | drone_hurt, engineer_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_light_002.ogg` | CC0 1.0 | engineer_death, engineer_step, interceptor_death |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_light_003.ogg` | CC0 1.0 | drone_death, engineer_hurt, node_destroyed, prime_death |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_light_004.ogg` | CC0 1.0 | brute_death, deflect, engineer_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_medium_000.ogg` | CC0 1.0 | interceptor_death, prime_transition, sentry_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_medium_001.ogg` | CC0 1.0 | prime_assemble, sentry_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_medium_002.ogg` | CC0 1.0 | brute_death, node_destroyed, prime_death, sentry_death |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_medium_003.ogg` | CC0 1.0 | prime_assemble, sentry_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMetal_medium_004.ogg` | CC0 1.0 | debris, interceptor_hurt |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMining_000.ogg` | CC0 1.0 | brute_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMining_001.ogg` | CC0 1.0 | brute_charge, debris |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMining_002.ogg` | CC0 1.0 | brute_step |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMining_003.ogg` | CC0 1.0 | brute_slam, prime_slam |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactMining_004.ogg` | CC0 1.0 | brute_step, debris |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_heavy_000.ogg` | CC0 1.0 | brute_charge, prime_step, sentry_strike |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_heavy_001.ogg` | CC0 1.0 | brute_step, fabricate |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_heavy_002.ogg` | CC0 1.0 | prime_step, sentry_death, sentry_strike |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_heavy_003.ogg` | CC0 1.0 | brute_step, phase_change, prime_slam |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_heavy_004.ogg` | CC0 1.0 | brute_death, brute_slam, brute_step, prime_assemble |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_light_000.ogg` | CC0 1.0 | prime_transition |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_light_001.ogg` | CC0 1.0 | node_destroyed, sentry_death |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_light_002.ogg` | CC0 1.0 | debris, prime_assemble |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_light_003.ogg` | CC0 1.0 | brute_death |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_light_004.ogg` | CC0 1.0 | prime_death |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_medium_000.ogg` | CC0 1.0 | brute_hurt |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPlate_medium_003.ogg` | CC0 1.0 | prime_death |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPunch_heavy_000.ogg` | CC0 1.0 | prime_impact, sentry_strike |
| [impact-sounds](https://kenney.nl/assets/impact-sounds) / `Audio/impactPunch_heavy_001.ogg` | CC0 1.0 | prime_impact, sentry_strike |
| [rpg-audio](https://kenney.nl/assets/rpg-audio) / `Audio/metalClick.ogg` | CC0 1.0 | brute_missiles, engineer_death, engineer_step, prime_assemble, sentry_servo, transmission |
| [rpg-audio](https://kenney.nl/assets/rpg-audio) / `Audio/metalLatch.ogg` | CC0 1.0 | brute_servo, capture, node_build, node_disabled, node_online, prime_assemble, prime_transition |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/computerNoise_000.ogg` | CC0 1.0 | defeat, event_start |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/computerNoise_001.ogg` | CC0 1.0 | drone_scan, upload_alarm |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/computerNoise_002.ogg` | CC0 1.0 | engineer_hack |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/computerNoise_003.ogg` | CC0 1.0 | node_reboot |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/doorClose_000.ogg` | CC0 1.0 | engineer_plant |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/doorClose_002.ogg` | CC0 1.0 | prime_assemble |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/doorOpen_001.ogg` | CC0 1.0 | brute_servo |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/doorOpen_002.ogg` | CC0 1.0 | fabricate |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/engineCircular_000.ogg` | CC0 1.0 | brute_charge |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/explosionCrunch_000.ogg` | CC0 1.0 | drone_death, engineer_death, node_shield_break |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/explosionCrunch_001.ogg` | CC0 1.0 | missile_explode, sentry_death |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/explosionCrunch_002.ogg` | CC0 1.0 | interceptor_death |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/explosionCrunch_003.ogg` | CC0 1.0 | brute_death, node_destroyed, prime_death |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/explosionCrunch_004.ogg` | CC0 1.0 | prime_death |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/forceField_000.ogg` | CC0 1.0 | brute_shield, node_shield_break |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/forceField_001.ogg` | CC0 1.0 | node_shield_hit, prime_summon |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/forceField_002.ogg` | CC0 1.0 | deflect, engineer_weld, prime_absorb |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/forceField_003.ogg` | CC0 1.0 | node_online, prime_assemble, release |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/forceField_004.ogg` | CC0 1.0 | node_shield_hit, prime_transition |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/impactMetal_000.ogg` | CC0 1.0 | engineer_plant, prime_impact |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/impactMetal_002.ogg` | CC0 1.0 | drone_death |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/impactMetal_003.ogg` | CC0 1.0 | prime_impact, sentry_hurt |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/laserLarge_001.ogg` | CC0 1.0 | sentry_blast |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/laserLarge_003.ogg` | CC0 1.0 | prime_palm |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/laserLarge_004.ogg` | CC0 1.0 | prime_beam |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/laserRetro_001.ogg` | CC0 1.0 | drone_mark |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/laserRetro_002.ogg` | CC0 1.0 | bolt_impact |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/laserSmall_000.ogg` | CC0 1.0 | interceptor_burst |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/laserSmall_001.ogg` | CC0 1.0 | interceptor_burst |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/laserSmall_002.ogg` | CC0 1.0 | drone_zap, interceptor_burst |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/laserSmall_003.ogg` | CC0 1.0 | bolt_impact, interceptor_burst |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/lowFrequency_explosion_000.ogg` | CC0 1.0 | brute_death, node_destroyed, prime_death, prime_slam |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/lowFrequency_explosion_001.ogg` | CC0 1.0 | brute_slam, missile_explode, prime_assemble, prime_impact, prime_transition |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/spaceEngineLarge_001.ogg` | CC0 1.0 | prime_beam_charge |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/thrusterFire_000.ogg` | CC0 1.0 | brute_missiles |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/thrusterFire_001.ogg` | CC0 1.0 | brute_missiles, prime_ascend, prime_dash |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/thrusterFire_002.ogg` | CC0 1.0 | brute_missiles, interceptor_boost |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/thrusterFire_003.ogg` | CC0 1.0 | brute_missiles, interceptor_dive |
| [sci-fi-sounds](https://kenney.nl/assets/sci-fi-sounds) / `Audio/thrusterFire_004.ogg` | CC0 1.0 | missile_flight |

Pure own synthesis (no recording): brute_swing, discover, drone_hover, interceptor_jet, node_hack, node_hum, node_tether, prime_jet, prime_whoosh, protocol, sentry_swing, splash, victory
