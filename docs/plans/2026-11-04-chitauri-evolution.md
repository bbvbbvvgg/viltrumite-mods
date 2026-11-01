# Chitauri Invasion 2.0 — evolution plan (core 1.65.0)

Spec: the owner's task «CHITAURI INVASION 2.0 — ЭВОЛЮЦИЯ ВТОРЖЕНИЯ». Base: PR #112 (`main` 5d1286a).
Rule: extend the shipped invasion (`worldevent/chitauri`, `entity/chitauri`, `client/chitauri`); do not rewrite it.

## Kept as is
Portal, opening beats, phases and timeline (`ChitauriRules`), soldier/chariot/Leviathan models, clips, sounds and VFX,
Leviathan building destruction, `/chitauri` commands, end conditions (closing, sever, `stop`, empty level), packets.

## New server systems (`worldevent/chitauri/war/`)
- `WarSector` / `SectorPlanner` (pure): sectors from Atheria districts (`CityProfile.districts`) or, outside the city,
  rings of 34-block cells around the anchor. A sector is probed only through loaded chunks
  (`ChitauriTerrain`); unknown cells wait. State: UNKNOWN → SCOUTED → CONTESTED → HELD → LIBERATED.
- `WarDirector` (pure): adaptive doctrine choice. Inputs: player positions per sector, kill rate, lost sectors,
  freed captives, player count, air superiority, squad efficiency. Seeded personality (`seed`) + reactive utility,
  re-evaluated every 300 ticks; budgeted reinforcements (hard total caps, no endless waves).
- `ChitauriWar` (server): owns sectors, squads and orders (SCOUT, DROP, SEIZE, PATROL, REINFORCE, ENCIRCLE, DEFEND,
  RETREAT, ABDUCT, SIEGE), sector control, strongholds (beacon entity), subtitles for announcements.
- `ChitauriCaptives` + `CaptiveLedger` (SavedData): capture, carry, containment, release. A captive is never removed;
  the ledger releases every captive after a restart, `stop`, the end or an unloaded containment point.
- State: `ChitauriWarState` SavedData keeps the last seed, doctrine log and results; `/chitauri start [force] [seed]`
  reproduces a run.

## New units (`entity/chitauri/`)
| Unit | Base | Role |
|---|---|---|
| Captor `chitauri_captor` | `ChitauriSoldier` rig | stun tether, carry citizens to containment, drops containment beacons |
| Heavy Trooper `chitauri_heavy` | `ChitauriSoldier` rig, 1.32x, own armour and cannon | arm cannon splash, ground slam, hit cap |
| Commander `chitauri_commander` | `ChitauriSoldier` rig, crest helm, banner staff | orders squads, encircle, calls 2 drops, aura |
| Interceptor `chitauri_interceptor` | own flier class, soldier rig + wing jets | hunts flying heroes, strafing runs |
| Siege Lancer `chitauri_siege` | own hover class, own geo | charged lance on cover/strongholds, block damage |
| Beacon `chitauri_beacon` | static entity, own geo | stronghold or containment point |

## Visuals and audio
- Geo, atlases and clips: `tools/assets/chitauri_units.py` on the existing `chitauri_lib` / `jinwoo_geo` kit
  (same FK, IK reach and preview tools). Renderers use `AnimCache` + `AnimationController` + `AnimRenderer` only.
- Sounds: `tools/sfx/chitauri_units.py`, own procedural synthesis (CC0), same mastering chain as `tools/sfx/chitauri.py`.
  No speech; all meaning goes to subtitles (`HeroSpeech.announce`, speaker-less world lines).

## Balance (`BalanceTable`)
Soldier 26 HP / 6 armour → 14 HP / 2 armour. Elite units carry the danger with hit caps instead of huge HP.

## Verification
JUnit for sectors, director, captive rules and balance; `./gradlew build`; native client runs (Xvfb) with three
seeded invasions compared in `docs/reports/2026-11-04-chitauri-evolution.md`.
