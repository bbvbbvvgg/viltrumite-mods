# Thanos with six Infinity Stones — city event (design)

Spec: the user's task «Танос с шестью камнями бесконечности» (2026-10-31). Branch
`feat/thanos-six-stones-event`, core 1.58.0 -> 1.59.0. This file records the decisions; the
step-by-step plan is `docs/plans/2026-10-31-thanos-six-stones-event.md`.

## Goal

A manual event (`/thanos start|stop|status`, op 2): one MCU-style Thanos with the complete Infinity
Gauntlet (no army, minions or summons) fights the heroes for six minutes. If he is still alive
when the timer runs out he snaps: the whole world stops, the visible universe dissolves into
glowing fragments, Od Lagna appears in the void, speaks for 30 s, and returns the world
(Thanos's pre-snap destruction is kept: no `/city reset`, no rebuild). If the heroes defeat him
first, a separate victory scene plays and the timer stops. Nothing persists: a restart, crash,
logout or `/thanos stop` leaves no freeze, camera or input lock.

## Server model (authoritative)

| Piece | Where | Notes |
|---|---|---|
| Rules | `worldevent/thanos/ThanosRules` | every number (timer 7200 ticks, phases, HP scaling, caps, stone timings), pure |
| Clock + outcome | `ThanosClock`, `ThanosOutcome` | counts *active* event ticks; paused while any other world pause stops the world; last-tick rule below |
| Phases | `ThanosPhase` | OPENING / ESCALATION / ONSLAUGHT / FINAL by health fraction **and** elapsed time (whichever is later) |
| Moves | `ThanosMove`, `ThanosStone` | melee + 6 stones x 2 moves + 6 combos (2-3 stones), telegraph / active / recovery / cooldown / range |
| Brain | `ThanosBrain` | pure weighted choice: range band, target count, flying targets, incoming projectiles, time-stop reaction, strong-ability reaction, phase gates, per-move cooldowns, anti-repeat (no move 2x in a row, no stone 3x in a row) |
| Entity | `entity/thanos/ThanosEntity` | `PathfinderMob`, `noSave`, 3.8 blocks, synced move/phase/tick/glow; damage caps; tags `viltrumitecore:divine`, `forge:bosses` |
| Event | `ThanosEvent` | single session, participants, boss bar, HUD packets, move execution, cleanup |
| Stones | `worldevent/thanos/stone/*` | one class per stone, executed by the event on the move's active ticks |
| Scene | `ThanosScene` | snap -> freeze -> dissolve -> Od Lagna -> restore timeline (server ticks), event-owned `WorldPause` |

### Timer and the last tick

`ThanosClock` advances once per server tick at `ServerTickEvent` END while the fight runs and the
world is not stopped by another pause (Reinhard N's world stop, a dev pause). Order inside one
tick: entity ticks (all damage of the tick lands) -> event END: (1) defeat check, (2) clock
advance, (3) expiry check. So damage dealt on the very tick the clock reaches 7200 still counts:
**defeat wins ties** (`ThanosOutcome.resolve`, unit-tested). After the outcome is decided Thanos
is invulnerable and the other branch can never start (no snap after a victory, one snap per event).

### Health and scaling

`maxHealth = 3000 * (1 + 0.6 * (heroes - 1))`, heroes = participants at the start (1..8 counted),
re-scaled upward (keeping the health fraction) if more heroes join in the first 30 s.
Per-hit cap 2.5 % of max health (≥ 40), per-second cap 2.5 % of max (a solo kill takes at
least 40 s of perfect damage): one-shot abilities
(`HeroDamage.applyCleanDamage`, Annihilation's MAX hurt, `setHealth(0)` paths) are clamped in
`setHealth`/`die` overrides; `/kill` (`AdminKill`) still works. Thanos is `divine` (Annihilation
and World Erasure spare him) and a `forge:bosses` member (too big to grab). Knockback resistance
1.0; impulses from heroes are refused except the scripted ones.

### Moves (telegraph / active / recovery ticks, cooldown)

| Move | Stone(s) | Shape |
|---|---|---|
| CLEAVE | — | wide hook, 4 blocks |
| HAMMER | — | two-hand overhead slam, small crater |
| POWER_PUNCH | Power | charged punch (purple fist), cone shockwave, knockback, fissures |
| POWER_WAVE | Power | ground pound: expanding ring with block destruction |
| SPACE_STEP | Space | blue portal under him, exits behind a target |
| SPACE_PULL | Space | ring at a target, spatial grab drags it to him |
| REALITY_ZONE | Reality | red unstable-matter zones (slow + damage, deflect shots), safe: no block writes |
| REALITY_MIRAGE | Reality | three false images; the real one strikes |
| TIME_DOME | Time | green dome: heroes inside slowed to a crawl, projectiles slowed |
| TIME_REWIND | Time | rewinds his own position and part of the last 4 s of damage |
| MIND_GAZE | Mind | yellow gaze: heroes looking at him are disoriented (look away to resist) |
| MIND_PULSE | Mind | energy rings to jump/dash over, false targets on the HUD |
| SOUL_TETHER | Soul | orange tethers to up to 3 heroes, life drain, break by distance |
| SOUL_NOVA | Soul | orange AoE with ghostly silhouettes |
| COMBO_PORTAL_PUNCH | Space+Power | portal behind the target, the charged fist comes out of it |
| COMBO_TIME_CRUSH | Time+Power | dome, then a power pound inside it |
| COMBO_SOUL_MIRAGE | Reality+Soul | mirages each fire a tether |
| COMBO_MIND_STEP | Mind+Space | gaze, then he blinks behind the dazed hero |
| COMBO_TRINITY | Power+Space+Reality | portals ring, matter shards, power wave |
| COMBO_ETERNITY | Time+Mind+Soul | dome + gaze + nova (final minute) |

Phases unlock moves: OPENING single stones; ESCALATION adds the first combos; ONSLAUGHT the
three-stone combos; FINAL (last 60 s) shortens telegraphs 20 %, cooldowns 30 %, stones glow
brighter and the music switches. Every move has a readable telegraph (pose + stone colour + sound)
of at least 12 ticks (10 in FINAL). No move kills by a non-damage mechanic: drain, slow and
disorientation only deal `HeroDamage`-style damage with the normal invulnerability rules.

### Hero compatibility

- Damage goes through `DamageSource`s of the entity (`mobAttack`) so every hero's defence/parry,
  `WorldPause.isProtected` and invulnerability frames still apply.
- Auto-target scans skip `Bystander` citizens and other Thanos-owned things.
- DIO's time stop and Reinhard's world stop freeze him like any entity (the TimeFreeze mixins skip
  his tick; the event does nothing for a frozen Thanos). Reaction: right after being released he
  prefers TIME_REWIND / SPACE_STEP (brain input `wasTimeStopped`).
- Grabs: bosses tag -> too big; control: FREEZE from others is accepted, his own scripted motion
  is suspended while controlled.
- Strong abilities: a hit taking > 4 % of max health in one blow raises `threat`, the brain answers
  with a defensive move (SPACE_STEP, TIME_REWIND, REALITY_MIRAGE).

## Snap and the universe scene

Server timeline (ticks after the outcome SNAP, `ThanosRules.Scene`):

| Ticks | Beat |
|---|---|
| 0 | event-owned `WorldPause` (no owner: every entity, projectile and world process stops) |
| 0-40 | Thanos straightens, raises the gauntlet (wide shot) |
| 40-80 | the six stones light up one by one (hand close-up) |
| 80-110 | face close-up |
| 110-150 | the fingers fold slowly |
| 150 | SNAP: sound, flash, shock ring |
| 165-325 | dissolve: from the gauntlet outward, characters, buildings, ground and sky become glowing fragments; the light dies |
| 325-365 | near-empty void |
| 365-405 | Od Lagna forms out of white-gold light |
| 405-1005 | six 5 s lines with portrait subtitles and gradual reveal, synced gestures |
| 1005-1125 | restore: reverse dissolve with a golden front from Od Lagna; Thanos is discarded at 1005 and does not come back |
| 1145 | pause ends, cameras return, event over |

The world itself is never rewritten (no chunk or block changes): the dissolve is a depth-based
world-space effect in the shared `dash_impact` post program (new `Snap*` uniforms and a copy of
the level depth taken at `AFTER_LEVEL`), entities dissolve through a multi-entity generalisation of
World Erasure's model-cell splitter (`client/render/dissolve`), plus a bounded CPU pool of
fragments sampled from visible block surfaces (≤ 1200 alive). Reinhard's own classes are not
modified.

`WorldPause.beginEvent` (new): an ownerless session the event ends itself; it keeps the watchdog
and the SERVER_STOP / RELOAD ends. A pause that is already running (another hero's) delays the
snap until it ends; while the Thanos pause runs, other pauses are refused as before.

## Client

- `client/thanos/ThanosRenderer` (shared `client/anim` stack: AnimCache, AnimationController,
  BakedGeoModel), emissive stones via `RenderType.eyes`, procedural finger curl over the clips.
- `ThanosFx` (one packet `ThanosFxS2CPacket`, kinds per stone) drives HeroFx/PixelVfx-style quads,
  CameraShake, FlashOverlay, SkyTint.
- `ThanosHud`: countdown and six stone pips for every participant; final minute pulses red.
- `ThanosSceneClient`: cameras (client `Marker` like the wish scene), Od Lagna figure (Reinhard's
  geo with its own white-gold texture generated by `tools/assets/make_thanos_od_lagna.py`; the
  Reinhard texture/model files are untouched), subtitles with portraits and typewriter reveal,
  watchdog that drops the scene if the server stops sending.
- Sounds: own synthesis `tools/sfx/thanos.py` (CC0), packaged as `.ogg.b64`, sound overlay
  `src/main/sound-overlays/thanos.json`. Lang: overlay `src/main/lang-overlays/thanos/*.json`,
  all 12 locales.

## Safety

- Everything is memory-only (`noSave` entity, no saved data). Server stop / level unload /
  `/thanos stop` -> `ThanosEvent.stop` ends the pause, removes Thanos, sends END to clients.
- Logout: the scene client clears on `LoggingOut`; `WorldPauseClient` already does.
- Client watchdog: no state packet for 3 s during the scene -> drop it, restore the camera.
- One event per server; `/thanos start` refuses while another Thanos event or a world pause runs.
