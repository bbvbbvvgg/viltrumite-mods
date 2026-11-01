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
| I `ARRIVAL` | start | 900 t (45 s) | 75 % | Sanctuary II descends (shadow, rumble, clouds, lights); Thanos alone, stone showcase (each stone first) |
| II `BLACK_ORDER` | 1500 t (1:15) or ≤ 75 % | 1000 t | 50 % | Ebony Maw and Cull Obsidian land by deploy beams; coordinated attacks; the ship answers Thanos's call |
| III `INVASION` | 3200 t (2:40) or ≤ 50 % | 1000 t | 25 % | ship portals + Chitauri waves (capped), bombardment with warnings, city emergency, blackout, partial collapses |
| IV `GAUNTLET` | 5000 t (4:10) or ≤ 25 % | — | none | full Gauntlet: stronger stones, multi-stage combos (Time Volley, Moon Fall, Infinity); no new army |

* One phase at a time; a phase advances when its gate (time or health) is reached **and** the minimum stay of the
  current phase has passed. Until then the health floor holds (the gauntlet visibly absorbs the hit), so Thanos can
  only be defeated in phase IV and every phase is seen. Admin damage (`/kill`, AdminKill) ignores the floor.
* Each change plays a **command beat** (`ThanosMove.COMMAND`, 60 t): II — two deploy beams; III — the ship's horn,
  portals open, sirens; IV — he closes the fist, all six stones flare (knockback ring, no damage).
* The last minute keeps its countdown and the final music (independent of the phase).

## New Thanos moves (append-only after `COMBO_ETERNITY`; ordinal = network id)

| Move | Stones | From | Shape | Counterplay |
|---|---|---|---|---|
| `SHIP_CALL` | — | II | he points; the ship fires a lance column at the marked spot after a 40 t warning ring (reaches flyers in the column) | leave the ring |
| `POWER_RUPTURE` | Power | I | a line of violet eruptions runs from him to the target | step sideways |
| `SPACE_BARRAGE` | Space | II | 4 small portals around the target (also in the air) fire shards in turn, each telegraphed by a line | keep moving |
| `REALITY_REWRITE` | Reality | II | a red sphere at the target lifts everything, then slams it down (flyers are thrown to the ground) | run out of the sphere |
| `TIME_ANOMALY` | Time | II | a green mark at the target's feet; 2.5 s later the target is snapped back to it, hurt if far away | stay near the mark |
| `MIND_COMPULSION` | Mind | II | a yellow thread drags the target towards him for 2 s (anti-ranged); ends in melee range | break line of sight |
| `SOUL_HARVEST` | Soul | III | soul phantoms rise and home in; each hit hurts and heals him a little | dodge them |
| `COMBO_TIME_VOLLEY` | Time+Space+Power | IV | time stops around the targets (heavy slow, ledger-capped), 6 portals open in a ring, a charged volley fires when time resumes | crawl out of the convergence point; 6 % damage interrupts |
| `COMBO_MOON_FALL` | Power+Space | IV | a sky portal drops 10–14 rocks on warned spots near the heroes | leave the red circles |
| `COMBO_INFINITY` | all six | IV | six stages: space pull → time slow → reality fracture → mind daze → soul drain → power blast | 5 % damage during the channel staggers him and cancels the rest |
| `COMMAND` | — | — | the phase beat (never chosen by the brain) | — |

Phase IV boosts stone damage ×1.2 and keeps the old FINAL sharpening (telegraphs ×0.8, cooldowns ×0.7).

## Smarter AI

* **Threat board** (`ThanosThreat`, pure): per hero a decaying score from damage dealt to him, distance, flight and
  ranged play; the target changes only when another hero clearly leads (×1.3) and after 40 t on a target.
* **Brain** (`ThanosBrain`): the old hard rules (cooldowns, stone gap, no repeat, no stone 3×, teleport gap) plus a
  6-move history with damping, a phase pool (I: unused stones first; II: moves that set up the allies; III: ship
  and army; IV: the new combos dominate), anti-flyer and anti-ranged bonuses, and a **defensive tactic** for 10 s
  after heavy damage (≥ 5 % in 4 s: defence, distance, Soul Harvest, ship calls; the allies cover him).
* **Control ledger** (`ThanosControlLedger`, pure): every new control (Maw's grab, Cull's grab, compulsion, the
  time volley, Infinity's pull) asks it for a duration: full, half, quarter within 10 s, then 6 s of immunity. No
  infinite stuns or chain grabs.
* **Coordination** (`ThanosWar`): Thanos announces his big moves; Maw holds the target for a Power Punch, Cull
  charges heroes into a Power wave, Maw shields Thanos from projectiles when he channels.

## Sanctuary II (`war/SanctuaryShip` server, `client/thanos/SanctuaryShip*`)

* Server: state ABSENT → ARRIVING (300 t) → HOLDING → (III) BOMBARD → LEAVING/GONE; anchor 150 blocks above the
  arena, offset 60 blocks; strikes are queued with a warning (40 t lance, 30 t per barrage step), at most 2 live
  warnings per hero, a per-minute budget, a minimum spacing, a radius-5 damage zone and a capped crater.
* Client: procedural mesh (hub, outer ring, four blade arms, spines, underside vents), three LODs chosen by the
  projected size, uploaded once into static VBOs (body with baked directional light, emissive pass additive), drawn
  at `AFTER_SKY` with depth so the city occludes it; the sky darkens (SkyTint), a long rumble shakes the camera
  during the arrival, cloud banks part around it, searchlights sweep the streets, the deploy beams and the landing
  portals are effects of the war FX.

## Black Order (`entity/thanos/`)

Own entities, geo models, clips, sounds and boss bars; never saved, allied with Thanos and the Chitauri (they never
hurt each other), discarded when the event ends.

* **Ebony Maw** (`EbonyMawEntity`, 2.2 blocks, floats): debris volley (blocks ripped from nearby buildings within
  the budget; conjured rubble where griefing is off), shard storm (5–8 objects at once), projectile interception
  (catches hero projectiles near him or Thanos and throws them away), debris barrier (a wall that stops projectiles),
  telekinetic grab (lift + throw, ledger-capped, respects `HeroRegistry.allowsImpulse` and pause protection), rubble
  spikes (the street lifts in a line). Keeps distance from melee heroes.
* **Cull Obsidian** (`CullObsidianEntity`, 3.6 blocks): ground slam (ring + crater), hammer sweep, charge through
  walls (breaks soft blocks on the way, stops at hard ones), grab and throw (ledger-capped), chain hammer (yanks a
  flyer down), roar (allies harden for 5 s, citizens flee). **Hulkbuster reaction**: a target with the combat tag
  `body.mech.heavy` (or `trait.superstrong`) is preferred and met with a clash (both lock hands, a shove-out).
* Telekinetic debris (`TelekineticDebrisEntity`): synced block state, orbit / flight / impact; at most 24 alive.

## Army (`war/ThanosArmy`)

Waves from the ship's landing portals during phase III only: soldiers, heavies, captors, one commander per wave and
up to 2 chariots (anti-air when heroes fly). Alive cap `10 + 4 × (heroes − 1)` (max 26), total cap 90 per event,
waves every 12–18 s, spawned only in entity-ticking chunks. The UUIDs are tracked; on any end every unit is recalled
(discarded with a portal flash). No new units in phase IV.

## City (`war/ThanosCityFront`, no rewrite of the city systems)

* Alarm leases: I `WARNING`, II `ACTIVE`, III–IV `CRITICAL`, refreshed while the event runs, released at the end.
* Strikes and big impacts: `StructuralDamage.run(CollapseCause.custom("thanos", 1.0F), …)` around every block
  removal (so collapses can follow), `StructuralDamage.impact`, `CityInfrastructure.damage` (blackout around the
  bombardment, severity 0.9 for lances), `CityAftermath.registerDamage` (cause `destruction`) — rate limited.
* Block budget per event 6000, per impact ≤ 220, flying debris ≤ 40; mobGriefing off removes nothing.

## Assets

* Sounds: `tools/sfx/thanos_war.py` (own numpy synthesis, CC0) → `sounds/thanos_war/*.ogg.b64`, overlay
  `sound-overlays/thanos_war.json`, subtitles in all 12 locales; no vanilla sounds.
* Models/clips: `tools/assets/black_order_model.py`, `tools/assets/black_order_clips.py`, Thanos's new clips in
  `animations/thanos/thanos_war.animation.json` (same rig).
* Lang: `lang-overlays/thanos_war/<locale>.json` (Russian subtitles and every locale), generated by
  `tools/assets/make_thanos_war_lang.py`.

## Network (protocol 28)

* `ThanosFxS2CPacket` kinds 29+ (ship, portals, strikes, Black Order, new moves).
* `ThanosWarS2CPacket` appended last: ship state/position/arrival clock, flags (blackout, bombardment, gauntlet),
  Black Order ids, army count.

## QA commands

`/thanos phase <arrival|black_order|invasion|gauntlet>` (op: jump, ignores the minimum stay), `/thanos ship strike`,
`/thanos order move <maw|cull> <move>`, `/thanos army wave`, `/thanos war status`.

## Manual test instructions

See `docs/design/2026-11-05-thanos-total-war-qa.md` (filled in during the playtest).

## Known limits

Filled in at the end of the work (see the PR description and `.memory-bank/thanos.md`).
