# THANOS 2.0 — TOTAL WAR (design)

Spec: the user's task «THANOS 2.0 — TOTAL WAR» (2026-11-05). Branch `feat/thanos-total-war-rework` from main after
#128 (core 1.69.0 / protocol 27) → core **1.70.0** / protocol **28**. The six-stones event
(`docs/design/2026-10-31-thanos-six-stones-design.md`) stays the base: the 6-minute timer, the snap, the world
dissolve, Od Lagna's speech and the restore, the victory when Thanos falls before the timer ends. This document
records what the rework adds and the decisions behind it.

## Goal

The event becomes a war for Atheria in four phases that play differently: Thanos arrives with the Sanctuary II
above the city, the Black Order lands, the Chitauri pour out of the ship's portals while the ship bombards the
city, and in the end Thanos fights with the full Gauntlet. Everything is server-authoritative, bounded (entity
caps, block budgets, strike budgets) and leaves nothing behind when the event ends.

## Phases (`ThanosPhase`, pure, unit-tested)

| Phase | Enters at (time **or** health) | Minimum stay | Health floor while in it | What changes |
|---|---|---|---|---|
| I `ARRIVAL` | start | 900 t (45 s) | 75 % | Sanctuary II glides in (shadow, rumble, cloud bank); Thanos alone, the first stones and Power Rupture |
| II `BLACK_ORDER` | 1500 t (1:15) or ≤ 75 % | 1000 t (50 s) | 50 % | command beat → two deploy beams: Ebony Maw and Cull Obsidian; the ship strikes where Thanos points; the new stone moves |
| III `INVASION` | 3200 t (2:40) or ≤ 50 % | 1000 t (50 s) | 25 % | the ship's horn, landing portals and Chitauri waves (capped), street bombardment with warnings, city alarm critical, blackouts |
| IV `GAUNTLET` | 5000 t (4:10) or ≤ 25 % | — | none | the fist closes, all six stones flare (knockback ring, no damage); combos Time Volley, Moon Fall, Infinity; stone damage ×1.2, telegraphs ×0.8, cooldowns ×0.7; the army keeps coming, but slower |

* One phase at a time; a phase advances when its gate (time or health) is reached **and** the minimum stay of the
  current phase has passed. Until then the health floor holds: damage that would take him under it is absorbed by
  the Gauntlet (flare + `thanos_war.absorb`, one action-bar hint per event), so Thanos can only be defeated in
  phase IV and every phase is seen. Admin damage (`/kill`, `AdminKill`) ignores the floor.
* Health: 3000 × (1 + 0.6 × (heroes − 1)), heroes counted up to 8, rescaled when more join early; one hit takes at
  most 2.5 % (never less than 40) and the per-second cap still applies (`ThanosDamageCap`).
* Each change plays a **command beat** (`ThanosMove.COMMAND`, 20 t telegraph + 30 t active, never chosen by the
  brain): II — two deploy beams; III — the ship's horn, portals open, sirens; IV — the fist and the stone flare.
* The last minute keeps its countdown and the final music (independent of the phase).

## New Thanos moves (append-only after `COMBO_ETERNITY`; ordinal = network id)

| Move | Stones | From | Shape | Counterplay |
|---|---|---|---|---|
| `SHIP_CALL` | — | II | he points; the ship fires a lance at the marked spot after a 40 t warning ring (32 t in IV) plus two side lances 7 blocks away (three in IV) at 70 % damage | leave the rings (the lance also hits flyers in the column) |
| `POWER_RUPTURE` | Power | I | 3 lines (5 in IV) of violet eruptions run ~25 blocks from him, radius 1.8, launch upwards | step sideways; more than 3 blocks above the ground is safe |
| `SPACE_BARRAGE` | Space | II | 6 gates open around him and fire a shard every 5 t with a little lead | keep moving |
| `REALITY_REWRITE` | Reality | II | a red sphere (r 6) at the target lifts everything for 18 t (flyers are pulled down), then slams it into a crater | run out of the sphere |
| `TIME_ANOMALY` | Time | II | a green mark at the target's feet; 40 t later whoever is more than 2 blocks from it is torn back to it (if holdable) and hurt; within 2 blocks nothing happens | stay by the mark |
| `MIND_COMPULSION` | Mind | II | a yellow thread drags the target towards him (0.28 blocks/tick) for 40 t, ends 3 blocks from him; a control-immune hero gets a Mind flash instead | an ally staggers Thanos (≥ 6 % of his health) and the hold breaks |
| `SOUL_HARVEST` | Soul | III | 2 phantoms per hero + 1 (≤ 6) hunt the heroes for 3 s; each that reaches one heals him by 0.4 % | outrun them; a channel, 6 % damage interrupts |
| `COMBO_TIME_VOLLEY` | Time+Space+Power | IV | time stops for every hero within 32 blocks for 30 t (through the control ledger), 6 gates open around each; when time runs again each gate fires 3 shards | leave the convergence point as soon as time runs |
| `COMBO_MOON_FALL` | Power+Space | IV | a sky gate drops ~12 rocks (one every 6 t), each warned by a red circle (r 3.6) 26 t before impact, ⅔ aimed at heroes with lead | watch the circles and move |
| `COMBO_INFINITY` | all six | IV | six stages of 15 t: Power wave → Space pull → Reality zones → Soul nova → Mind pulse → Time dome and crush | survive each stage; ≥ 6 % of his health during the channel staggers him and cancels the rest |
| `COMMAND` | — | phase change | the phase beat | hit him while the arm is raised |

Channels interrupted by 6 % of his max health within the active part: Soul Tether, Soul Mirage, Eternity, Mind
Pulse, Soul Harvest, Infinity (he staggers, his holds release the heroes). Full tables (telegraph / active /
cooldown / damage) are in the QA document, section 2.

## Smarter AI

* **Threat board** (`ThanosThreat`, pure): per hero a decaying score (half-life 100 t) from damage dealt to him,
  distance, flight and ranged play (≥ 55 % of a hero's damage from 9+ blocks or by projectile); the target is kept
  at least 40 t and changes only when another hero leads by ×1.3.
* **Brain** (`ThanosBrain`): the old hard rules (cooldowns, stone gap, no repeat, no stone 3× in a row, teleport
  gap) plus a 6-move history with damping and weights by situation: new moves of the phase ×1.4, combos ×1.0/1.4/1.8
  by phase, Infinity ×2 under 15 % health; against a flyer Compulsion and Barrage ×2, Anomaly and Gravity Grip ×1.7,
  Reality Rewrite ×1.5, Ship Call and Time Volley ×1.4; against a sniper Compulsion ×2.5, grab ×2, gap closers
  ×1.8; a target held by an ally makes Power Punch ×3 and Hammer / Portal Punch / Rupture ×2; a control-immune
  target makes control ×0.15.
* **Defensive tactic**: after ≥ 5 % of his health within 80 t, 200 t of defence ×3, mobility ×2, melee ×0.6, Ship
  Call and Anomaly ×1.5, the army covers him.
* **Control ledger** (`ThanosControlLedger`, pure): every hold (Maw's grab, Cull's grab and chain, Compulsion, the
  Time Volley stop, the Anomaly snap) asks it for a duration per hero: within a 200 t window the first hold is
  full, the second half, the third a quarter (never under 4 t), then 120 t of immunity. No infinite stuns or chain
  grabs. A hold ends when the holder falls, staggers or lets go; the held player gets the «Вы схвачены» card
  (`ThanosWarS2CPacket.HOLD`, kind FREEZE 7FE8FF / CARRY 9FB8FF / DRAG FFD630) in the warning slot under the timer.
* **Coordination** (`ThanosWar`): the command beats call the Order, the army and the bombardment; Maw raises his
  debris barrier around Thanos when projectiles come at him and catches hero projectiles near both of them; the
  brain prefers finishers on a hero an ally holds; Thanos, the Order and the Chitauri never hurt each other.

## Sanctuary II (`war/SanctuaryShip` + `war/ShipPath` server, `client/thanos/war/Sanctuary*` client)

* States `NONE → ARRIVING → HOVER → LEAVING → GONE`: 40 t after the start it glides 420 t from a point 720 blocks
  out and 90 blocks higher to the hover point 112 blocks above the arena (offset 64, span 180 blocks, bob 1.6 blocks
  over 220 t); it leaves in 260 t after any end. Client and server follow the same `ShipPath` from the synced
  start/hover points and the ticks into the state.
* Strikes: warning ring 40 t (32 t in IV), radius 4.5, damage 18 at the centre falling off to ×0.35 at the edge,
  crater 2.6 (hardness cap 3), at most 6 live warnings. From phase III the ship bombards the streets every 150 t
  (260 t in IV) within 44 blocks, **never closer than 5 blocks to a hero**.
* Client: procedural mesh (`SanctuaryMesh`, no asset file) built once into vertex buffers at three levels of
  detail (measured 13.6k / 1.2k quads for LOD0 / LOD1), baked light plus emissive lights, hull shader
  `sanctuary_hull` (daylight tint, aerial haze, gun glow); drawn after solid blocks with depth so the city occludes
  it; its shadow and the cloud bank are drawn after translucent terrain; the sky tint, the hum loop and the camera
  rumble of the arrival are client effects.

## Black Order (`entity/thanos/`, brains in `war/order/`)

Own entities, geo models, clips, sounds and boss bars (one row under Thanos's bar); never saved, allied with Thanos
and the Chitauri, discarded when the event ends. Health Maw 620 / Cull 980, +45 % per hero beyond the first
(counted up to 8), one hit takes at most 6 %; they arrive by 50 t deploy beams and are leashed 72 blocks to Thanos.

* **Ebony Maw** (`EbonyMawEntity`, 2.1 blocks, floats, keeps distance): Spear (a ripped block), Volley (blocks of
  nearby buildings circle and fly one by one, shared between heroes), telekinetic Grab (lift + throw, ledger-capped,
  respects the hero's impulse protection), Barrier (a debris ring around Thanos or himself that catches
  projectiles), Topple (a chunk of a building above the target breaks off and falls), Blink (away from a hero in
  melee range); passively catches hero projectiles near himself and Thanos. Grab ×1.8 against flyers, ×1.5 against
  snipers. The flying blocks are `WarDebrisEntity` (synced block state; orbit / hold / fly / fall / barrier).
* **Cull Obsidian** (`CullObsidianEntity`, 3.3 blocks): Swing, Slam (shock ring 7.5 blocks, ×2.2 when two heroes are
  close), Charge (head down through walls within the block budget), Chain (the hammer head on its chain hooks and
  pulls, ×2.5 against flyers), Grab (lift and throw, ledger-capped), Roar (hardens; on seeing a heavy hero or under
  50 % health). **Hulkbuster reaction**: a "heavy" hero — bounding box at least 2.6 high or 1.1 wide
  (`BlackOrderEntity.heavy`, the Hulkbuster's frame or anything that size) — is preferred and met with Clash (he
  crashes in, locks and shoves; damage breaks the lock).

## Army (`war/ThanosArmy`)

Waves through landing portals on the streets (up to 34 blocks from the fight, portals live 220 t) every 400 t in
phase III and every 640 t in phase IV; every second wave a chariot comes through a sky portal (at most 3). Wave size
min(10, 4 + 2 × (heroes − 1) + min(2, wave / 3)); soldiers, from the third wave a heavy now and then. Alive cap
12 + 4 per hero beyond the first (≤ 30), total cap 64 per event, portals only at loaded spots with open sky 10–34
blocks from Thanos and not on top of a hero. The units
are the existing Chitauri entities, tracked by UUID; on any end every unit is recalled and every Chitauri bolt within
224 blocks of Thanos is swept (bolts are never saved, so a stray one cannot outlive the war in an unloaded chunk).

## City (`war/ThanosCityFront`, `war/ThanosWarDestruction`; no rewrite of the city systems)

* Alarm leases through `CityInfrastructure` (source `thanos_war`, lease 200 t, radius 160): I `WARNING`, II
  `ACTIVE`, III–IV `CRITICAL`, refreshed while the event runs, all-clear at the end. Where the city grid is not
  available the clients play the war's own siren (the `ALARM` flag).
* Blackouts from phase III: the first 80 t into the phase, then every 300 + random 120 t, severity 0.7, radius 18,
  near the fight (`CityInfrastructure.damage`).
* Every removed block goes through `HeroDestruction.canDestroy` (mobGriefing, unbreakable blocks, loaded chunks
  only, no block entities or fluids), under the collapse cause `thanos`, so buildings that lose support collapse
  through `StructuralDamage` / `CityCollapseEvent` on their own; big hits are reported to the city grid and the
  disaster aftermath. One block budget per event: 3200 (ship strike ≤ 44, rock ≤ 26, charge ≤ 54, topple ≤ 14).

## Assets

* Sounds: 40 cues synthesised by `tools/sfx/thanos_war.py` (own numpy synthesis, CC0) → core binassets
  `sounds/thanos_war/*.ogg.b64`, overlay `sound-overlays/thanos_war.json`, subtitles in all 12 locales; no vanilla
  sounds (`--check` proves the committed files).
* Models/clips: `tools/assets/black_order_model.py`, `tools/assets/black_order_clips.py`, Thanos's 11 new clips in
  `animations/thanos/thanos_war{,_stones,_combos}.animation.json` (same rig); sources in
  `tools/assets/thanos_war_sources.md`.
* Lang: `lang-overlays/thanos_war/<locale>.json` (speakers, voice lines, HUD, hold card, absorb notice, entity names,
  sound subtitles) from `tools/assets/make_thanos_war_lang.py`; move names, hints, phases and commands from
  `tools/assets/make_thanos_lang.py` (+ `thanos_lang_moves.py`). Russian and English first, de/es/tr/zh tables in the
  same scripts (the seven Spanish locales share the neutral Spanish column).

## Network (protocol 28)

* `ThanosFxS2CPacket` kinds 29–58 appended (ship warning/lance, the new moves, absorb, command, beams, army portals,
  telekinesis, Cull's moves, debris impact, clash, `CLEAR_WAR`).
* `ThanosWarS2CPacket` appended last: `STATE` (phase, ship path state, start/hover points, ticks into the state,
  flags `ALARM / SMOKE / GAUNTLET / ORDER / ARMY / HIDE_SHIP / BOMBARD`), `NONE` (the war is over, drop
  everything), `HOLD` (the receiving player is held: ticks and kind; his movement input is off meanwhile).

## QA commands (op)

`/thanos start [force]`, `/thanos stop`, `/thanos status`, `/thanos skip <seconds>`, `/thanos move <move>`,
`/thanos phase <arrival|black_order|invasion|gauntlet>` (forward only, ignores the minimum stay; asking for a phase
already reached answers `commands.viltrumitecore.thanos.phase_already`), `/thanos order` (the Black Order now),
`/thanos army` (a Chitauri wave now), `/thanos ship strike` (a ship strike at the caller), `/thanos war` (phase,
ship, strikes, army, Order health, alarm, blackouts, holds, destroyed blocks). Client playtest harness
(`-Dviltrumitecore.subtitles.playtest=true`, `<gameDir>/subtitle_qa.txt`): `warstate`, `lookship`, `fly`,
`holdshot <name> [secs]`, `shot`, `cmd`, …; the client log prints `SUBPT` lines for voice lines, sounds and the
warning-slot changes (`strip <MOVE>|held|none`).

## Manual test instructions and results

`docs/design/2026-11-05-thanos-total-war-qa.md`: the full attack list (section 2), the QA commands (3), manual
scenarios (4), the results of the single-player, dedicated-server + 2-client and performance runs (5).

## Known limits

See section 6 of the QA document: the city alarm/blackouts/collapses need the Atheria map (not available in the
dev sandbox), FPS was measured only under software rendering (llvmpipe), multiplayer was tested on one machine,
the Hulkbuster clash is unit-tested only, the hold card shares the warning slot, no recorded voice acting, the
non-Russian/English translations are not reviewed by native speakers.
