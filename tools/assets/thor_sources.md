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

Not imported: lightning eyes, concept skins, cloak, older Mjölnir, head display, all Kratos assets, the pack's sounds (license unclear), `.fsk` animations.

## Sounds (R5, spec §8)

All 56 Thor sound files are **own work, CC0 1.0**: deterministic synthesis by `tools/sfx/thor.py` (seeded numpy noise, oscillators, modal "uru" metal partials, sub drops, a synthetic room; no recording, no sample, no download). The Openverse API timed out on 2026-10-28, so no third-party recording was layered; a later pass may swap single layers for CC0 recordings (sound-design skill) without changing event ids. Packaged by the sound-design helper (`openverse_sfx.py package --own`): mono 44.1 kHz Vorbis q5, stored as `binassets/assets/viltrumitecore/sounds/thor/*.ogg.b64`. Rebuild: `python3 tools/sfx/thor.py`; verify: `python3 tools/sfx/thor.py --check`. The cues were **not auditioned** (no playback in the sandbox): levels are measured (K-weighted M100 per class, true-peak ceiling), loops are seamless by construction.

Played only through `ThorSounds` (`hero/thor/ThorSounds.java`) by cue (`ThorCue`); client loops in `client/thor/ThorSoundLoops.java`. No vanilla `SoundEvents` in Thor code (`ThorSoundsTest`).

| Event (`sounds.json`) | Files | Length | Recipe |
|---|---|---|---|
| `thor.hammer_windup` | `hammer_windup_1`, `hammer_windup_2` | 0.85 s | uru creak + rising air band |
| `thor.hammer_swing` | `hammer_swing_1`, `hammer_swing_2`, `hammer_swing_3` | 0.65 s | heavy whoosh (swept band noise) |
| `thor.hammer_metal` | `hammer_metal_1`, `hammer_metal_2`, `hammer_metal_3` | 1.5 s | uru head ring: modal inharmonic partials, saturation, room |
| `thor.hammer_earth` | `hammer_earth_1`, `hammer_earth_2` | 1.7 s | ground break: debris grains, saturated noise burst |
| `thor.hammer_bass` | `hammer_bass_1`, `hammer_bass_2` | 2.0 s | sub drop (pitch-falling saturated sine) + brown noise |
| `thor.hammer_body` | `hammer_body_1`, `hammer_body_2` | 0.5 s | dense body thud |
| `thor.hammer_tail` | `hammer_tail_1`, `hammer_tail_2` | 2.6 s | long uru ring-out + falling debris |
| `thor.fist_windup` | `fist_windup` | 0.45 s | short air draw |
| `thor.fist_swing` | `fist_swing_1`, `fist_swing_2`, `fist_swing_3` | 0.4 s | fist whoosh |
| `thor.fist_hit` | `fist_hit_1`, `fist_hit_2`, `fist_hit_3` | 0.6 s | punch: sub thump + crunch grains |
| `thor.thunder_crack` | `thunder_crack_1`, `thunder_crack_2`, `thunder_crack_3` | 1.4 s | close crack: impulse train, saturation |
| `thor.thunder_impact` | `thunder_impact_1`, `thunder_impact_2` | 2.2 s | strike boom: sub drop + noise burst |
| `thor.thunder_rumble` | `thunder_rumble_1`, `thunder_rumble_2` | 7.0 s | 7 s roll: band-swept brown noise |
| `thor.electric_on` | `electric_on` | 1.3 s | arc ignition: buzz + crackle + sub |
| `thor.electric_off` | `electric_off` | 0.95 s | buzz falling away |
| `thor.electric_loop` | `electric_loop` | 3.0 s | 3 s seamless electric hum (circular) |
| `thor.electric_zap` | `electric_zap_1`, `electric_zap_2`, `electric_zap_3` | 0.6 s | short arc zap |
| `thor.electric_tail` | `electric_tail` | 1.5 s | crackle decay |
| `thor.electric_wave` | `electric_wave` | 1.9 s | electric shock wave: sub + swept noise + crackle |
| `thor.storm_charge` | `storm_charge` | 2.2 s | 2.2 s rising storm riser |
| `thor.wrath_windup` | `wrath_windup` | 5.0 s | 5 s rising riser (buzz, pink/brown wind, crackle) |
| `thor.hammer_spin_loop` | `hammer_spin_loop` | 1.0 s | 1 s seamless rotor whoosh (6 rev/s) |
| `thor.flight_loop` | `flight_loop` | 4.0 s | 4 s seamless wind |
| `thor.hammer_fly_loop` | `hammer_fly_loop` | 1.0 s | 1 s seamless hammer hum |
| `thor.hammer_throw` | `hammer_throw` | 0.95 s | throw whoosh + launch thump |
| `thor.hammer_catch` | `hammer_catch` | 1.1 s | palm catch: uru clank + sub |
| `thor.hammer_recall` | `hammer_recall` | 1.4 s | rising electric pull |
| `thor.hammer_draw` | `hammer_draw` | 1.1 s | draw: uru slide + clank |
| `thor.hammer_holster` | `hammer_holster` | 0.75 s | belt clank |
| `thor.lift_strain` | `lift_strain` | 1.7 s | strain: creak + grit |
| `thor.refuse` | `refuse` | 0.35 s | short dull tick |
| `thor.dash` | `dash` | 0.6 s | air burst |
| `thor.pull` | `pull` | 1.3 s | pulled-through-air whoosh |
| `thor.grab` | `grab` | 0.5 s | grip thud |
| `thor.bone_crunch` | `bone_crunch` | 0.75 s | bone crack grains |
| `thor.gore_squelch` | `gore_squelch` | 0.85 s | wet squelch |
| `thor.heartbeat` | `heartbeat` | 0.95 s | double low beat |

### Cue list (stable ids; reference for the attack table)

Phase cues start on move events, never on tick counts: windup on `onStart` / `CHAIN` (INTRO), contact on `ENTER_MAIN`, tail on `ENTER_FINISH`. A strike's cues come from `ThorCue.windup/hit/ground/tail(ThorStrike)` (light / heavy by `ThorStrike.heavy()`, ground by `shockRadius`), so a new strike row needs no sound code. Layer volume above 1 only widens the range (16 blocks per unit).

| Cue id | Layers | When |
|---|---|---|
| `hammer.windup` | hammer_swing | HAMMER_COMBO / HAMMER_STRIKE step start (onStart, CHAIN) — light strikes |
| `hammer.windup_heavy` | hammer_swing + hammer_swing (low, slow) | same, heavy strikes (`ThorStrike.heavy()`); swing only, delayed so the whoosh peak lands on the contact (`ThorCue.windupDelay`) |
| `hammer.hit` | hammer_metal + hammer_body + hammer_bass | ENTER_MAIN, a body struck (light) |
| `hammer.hit_heavy` | hammer_metal + hammer_body + hammer_bass | ENTER_MAIN, a body struck (heavy) |
| `hammer.slam` | hammer_metal + hammer_earth + hammer_bass | ENTER_MAIN at the shock centre (`shockRadius > 0`) |
| `hammer.tail` | hammer_tail | ENTER_FINISH of heavy strikes |
| `fist.windup` | fist_swing | FIST_COMBO step start — light |
| `fist.windup_heavy` | fist_swing | FIST_COMBO step start — heavy; delayed so the whoosh peak lands on the contact |
| `fist.hit` | fist_hit | ENTER_MAIN, struck (light) |
| `fist.hit_heavy` | fist_hit + hammer_bass | ENTER_MAIN, struck (heavy) |
| `fist.slam` | fist_hit + hammer_earth + hammer_bass | ENTER_MAIN, GROUND_POUND shock centre |
| `fist.tail` | hammer_tail | ENTER_FINISH of heavy fist strikes |
| `electric.on` | electric_on | ELECTRIC_MODE on |
| `electric.off` | electric_off | ELECTRIC_MODE off |
| `electric.discharge.windup` | electric_zap | ELECTRIC_COMBO step start (onStart, CHAIN) |
| `electric.discharge` | thunder_crack + electric_zap | ELECTRIC_COMBO ENTER_MAIN (from the hand) |
| `electric.tail` | electric_tail | ELECTRIC_COMBO / ELECTRIC_WAVE ENTER_FINISH |
| `electric.wave.windup` | electric_zap | ELECTRIC_WAVE step start |
| `electric.wave` | electric_wave + thunder_impact | ELECTRIC_WAVE ENTER_MAIN |
| `electric.storm.start` | thunder_crack | ELECTRIC_STORM onStart |
| `electric.storm.charge` | storm_charge | ELECTRIC_STORM onStart; stopped by the burst and by onStop |
| `electric.storm.stage` | thunder_crack + thunder_impact | storm stage up (volume grows with the stage) |
| `electric.storm.crackle` | electric_zap | storm hold texture (once a second) |
| `electric.storm.burst` | thunder_crack + thunder_impact + thunder_rumble + electric_wave | storm ENTER_FINISH / release |
| `electric.combo` | thunder_crack + electric_zap | electric combo recognised (effect start) |
| `electric.combo.slam` | thunder_impact + hammer_earth + hammer_bass | the combo's ground slam |
| `electric.bolt` | thunder_impact | every drawn bolt, server half (gated 2 t per level) |
| `electric.bolt.rumble` | thunder_rumble | after bolts, at most once per 30 t per level |
| `guard.up` | fist_windup | guard (F) raised |
| `guard.block` | hammer_metal + hammer_body | a blow absorbed by the hammer guard |
| `guard.block_fist` | fist_hit | a blow absorbed by the fist guard |
| `guard.parry` | hammer_metal + thunder_crack + electric_zap | parry (block in the first 5 t of the guard) with Mjölnir |
| `guard.parry_fist` | fist_hit + hammer_bass | parry with bare fists |
| `wrath.windup` | wrath_windup + electric_on | WRATH onStart; stopped at contact and onStop |
| `wrath.contact` | thunder_crack + thunder_impact + thunder_rumble + hammer_bass + hammer_earth | WRATH ENTER_MAIN at the aim |
| `wrath.tail` | electric_tail + hammer_tail | WRATH ENTER_FINISH |
| `hammer.throw.windup` | hammer_windup | HAMMER_THROW onStart (hammer in the hand) |
| `hammer.throw` | hammer_throw | release |
| `hammer.throw.charged` | hammer_throw + thunder_crack | charged release |
| `hammer.recall` | hammer_recall | RECALL started |
| `hammer.catch` | hammer_catch + hammer_metal | hammer reaches the palm |
| `hammer.pickup` | hammer_draw + hammer_earth | lifted from the ground |
| `hammer.pull` | pull + electric_zap | HAMMER_PULL start |
| `hammer.stick` | hammer_metal + hammer_earth + hammer_bass | thrown hammer sticks in a block |
| `hammer.rip` | hammer_earth + hammer_swing | ripped out of a block on recall |
| `hammer.land` | hammer_metal + hammer_earth + hammer_bass | world fall lands |
| `hammer.thrown_hit` | hammer_metal + hammer_body + electric_zap | flying hammer hits a body |
| `hammer.block_smash` | hammer_earth + hammer_bass | flying hammer breaks a block (gated) |
| `hammer.impact` | hammer_metal + hammer_earth + hammer_bass + thunder_impact + thunder_rumble | crater / death impact (range = impact volume) |
| `hammer.first_fall.thunder` | thunder_crack + thunder_rumble | first appearance, whole dimension |
| `hammer.first_fall.trail` | thunder_crack + electric_zap | first fall, every 30 t |
| `hammer.lift.strain` | lift_strain | somebody tries to lift Mjölnir |
| `hammer.lift.fail` | refuse + hammer_metal | the attempt fails |
| `hammer.draw` | hammer_draw | hammer to the hand |
| `hammer.holster` | hammer_holster | hammer to the belt |
| `ui.refuse` | refuse | refused press (only the player) |
| `scene.dash` | dash | double-tap dash |
| `scene.grab` | grab | grab starts |
| `scene.grab.action` | fist_swing | grab action chosen |
| `scene.grab.break` | grab + fist_hit | victim breaks free |
| `scene.teleport.charge` | storm_charge + electric_on | teleport aim starts; stopped on arrival |
| `scene.teleport.depart` | electric_zap + thunder_crack | teleport, departure point |
| `scene.teleport.arrive` | thunder_crack + thunder_impact | teleport, arrival point |
| `scene.slam` | hammer_earth + hammer_bass + fist_hit | grab slam |
| `scene.barrage.hit` | fist_hit | barrage punch (pitch rises per index) |
| `scene.barrage.finish` | fist_hit + hammer_bass | barrage finisher |
| `scene.body.impact` | hammer_earth + bone_crunch | thrown body meets a surface |
| `scene.body.hit` | hammer_body | thrown body hits somebody |
| `scene.fatality.head_crush` | hammer_metal + bone_crunch + gore_squelch | fatality beat |
| `scene.fatality.chest_punch` | fist_hit + bone_crunch + gore_squelch | fatality beat |
| `scene.fatality.heart_rip` | heartbeat + gore_squelch | fatality beat |
| `scene.fatality.heart_crush` | gore_squelch | fatality beat |
| `scene.fatality.uppercut` | fist_hit + bone_crunch | fatality beat |
| `scene.fatality.air_blow` | thunder_crack + thunder_impact + hammer_bass | fatality beat |
| `scene.fatality.ground_slam` | hammer_earth + hammer_bass | fatality beat |
| `scene.fatality.thor_land` | hammer_earth + hammer_bass | fatality beat |
| `loop.electric` | electric_loop | client loop: electric mode on |
| `loop.hammer_spin` | hammer_spin_loop | client loop: flight (any state) with Mjölnir in the hand, or HAMMER_THROW INTRO/MAIN |
| `loop.flight` | flight_loop | client loop: CRUISE / SONIC, volume & pitch by speed |
| `loop.hammer_fly` | hammer_fly_loop | client loop on the flying world hammer |
