# ГОДЗИЛЛА — ГОРОДСКАЯ КАТАСТРОФА (Godzilla city catastrophe) — plan

Status: in progress on branch `feat/godzilla-city-catastrophe` (PR to `main`, never merged by the agent).
Core `1.69.0` → `1.70.0`, network PROTOCOL `27` → `28` (one packet, `GodzillaFxS2CPacket`, appended last).

## Goal

A manual world event for the Atheria city: a colossal kaiju walks in, explores the city, fights the heroes,
gets enraged, and at the end dies or retreats. Quality first: an original detailed model (not a scaled vanilla mob)
with three LOD levels and a full clip set, a multi-stage atomic breath with real server hit geometry, destruction
through the existing city systems, own layered sound with Russian subtitles. Everything uses the existing systems:
anim core (`client/anim`), `CameraShake`, mod particles, `StructuralDamage`, `CityInfrastructure`,
`CityAftermath`, `HeroDestruction` (city journal → `/city reset`), `CarveJobs`, `WorldEventConflicts`, the city
profile and danger map. No second animation or VFX system, no GeckoLib, no vanilla guardian beam, no vanilla
particles for the breath, no voice / TTS.

## Rules

- Server-authoritative; clients only draw and play sounds.
- Manual only: `/godzilla start [force]`. No natural spawn. One big event at a time through `WorldEventConflicts`
  (Godzilla joins the exclusive set next to the Chitauri invasion and the Ultron uprising).
- The session lives in memory only. The entity is `noSave`; a restart ends the event. Nothing force-loads chunks:
  the walker and the destruction skip unloaded chunks; the event pauses its timers when no player is near.
- `stop` removes only what the event owns: the kaiju and its hit zones, its debris entities, its alarm lease and
  the client effects (a CLEAR packet). City damage stays; `/city reset` restores it (city journal).
  `cleanup` also removes orphaned kaiju entities of a crashed session.
- Budgets (numbers in `GodzillaRules`): destroyed blocks per tick (normal / rage), per session cap, flying debris
  cap, FX packets per tick, aftermath and infrastructure reports throttled per area.

## Architecture

Server (`worldevent/godzilla/`):
- `GodzillaEvent`: facade (start / stop / status / cleanup), Forge server tick, logout / level unload, conflicts.
- `GodzillaSession`: phase machine ARRIVAL → EXPLORE ⇄ COMBAT → RAGE → FINALE (death or retreat), timers,
  waypoints (city districts), alarm lease, owned entity ids, statistics.
- `GodzillaBrain`: threat table (damage dealt, proximity, flight), target classification in the body frame
  (front / side / behind, ground / air, near / far) → move choice with cooldowns; health and threat tune it.
- `GodzillaBreath`: the atomic breath. Charge stages (stop → plates ignite at the tail → glow runs up the spine →
  energy in the mouth → fire). The beam is a ray from the mouth; the head turns towards the target at a limited
  rate. Each tick the ray marches to the first solid block and drills a capsule (block budget), hurts entities
  in a capsule along the beam, reports hot aftermath zones, infrastructure damage and structural impacts.
- `GodzillaDestruction`: every block the kaiju removes goes here (cause scope `godzilla`, mobGriefing,
  loaded chunks, budgets, debris cap, aftermath + infrastructure + danger reports).
- `GodzillaRules`: pure numbers and curves (scale, gait, move timelines, breath stages, damage caps, budgets,
  phase thresholds, head turn rate). Unit tested.
- `GodzillaMove`: moves with clip name, length and contact ticks.
- `GodzillaSounds`, `GodzillaSetup`, `GodzillaCommands`, `net/GodzillaFxS2CPacket`, `net/GodzillaNet`.

Entity (`entity/godzilla/`):
- `GodzillaEntity` (Mob, Enemy, noPhysics, noSave, no AI goals): position, yaw, gait phase, head yaw / pitch,
  move + move start, beam end, rage and charge synced. Walks on the street height (city walk grid, else a
  footprint heightmap minimum), wades through buildings.
- `GodzillaPart` (PartEntity): ~16 hit zones (head, neck, chest, belly, hips, two arms, two legs, seven tail
  segments). Tail segments follow a lagged yaw chain (same maths on both sides) — no entity chain of thousands.

Client (`client/godzilla/`):
- `GodzillaRenderer`: three LOD geo models (same atlas), clip layering (idle, walk by gait phase, turn leans,
  timed move, look), procedural tail inertia, per-bone tint for the travelling plate glow (extension
  `AnimRenderer.BoneTint`), part culling (bones of off-screen hit zones skip their cubes), mouth interior hidden
  while the jaw is closed, emissive pass, beam geometry (own textured scrolling layers, no guardian beam).
- `GodzillaClient`: FX packet kinds (steps, stomp waves, smashes, tail and claw hits, roar wave, fall impact,
  breath stages, beam impact, death, retreat, clear), particle budget, distance LOD, capped camera shake.
- `GodzillaLoops`: charge, beam and impact loops that follow the entity and the beam end.

Assets: `tools/assets/godzilla.py` (geo LOD0/1/2, atlas + glow, clips), `tools/sfx/godzilla.py` (own synthesis,
CC0), lang overlay `src/main/lang-overlays/godzilla/` (12 locales), sound overlay `src/main/sound-overlays/godzilla.json`,
ledger `tools/assets/godzilla_sources.md`.

## Acceptance checklist (in-game QA)

1. Build + unit tests green.
2. Model and clips: idle, walk, turns, roar, breath charge / fire / end, claw, tail strike, stomp, body slam,
   hurt, fall + rise, death, retreat — first and third person, near and far (LOD switch).
3. Scale against the Atheria skyscrapers.
4. Breath: all stages, head tracking limit, beam destroys blocks, hot zones and smoke.
5. Tail and claw hits on entities and buildings.
6. Real building destruction, partial collapses (StructuralDamage), grid damage and sirens, aftermath zones,
   citizens flee.
7. Fight with a ground hero and with a flying hero.
8. Rage phase; finale (death / retreat).
9. `stop` + `cleanup`; `/city reset` restores the city.
10. Dedicated server + client (multiplayer sync), FPS / TPS under heavy destruction.
