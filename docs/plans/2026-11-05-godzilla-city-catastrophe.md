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
  `cleanup` also removes orphaned kaiju entities of a crashed session and tagged debris no session tracks.
- `/city reset` and the event never overlap: `/godzilla start` is refused while the map is being restored, and the
  reset stops a running kaiju before it rebuilds (its damage is in the journal and comes back with the rest).
- Reach first: a move is only chosen when it can touch the target (`GodzillaRules.reach`: claws, tail, stomp, slam
  sphere with its lunge, the beam line through the mouth within the ±60° head pitch); out of reach the kaiju roars
  and walks to where a move lands (`standOff`) or backs off from a hero over its head.
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

## Playtest 2026-11-02 (Atheria v2.06, Xvfb + llvmpipe, 2 CPU / 4.2 GB)

Three single-player sessions on the real map and one dedicated server with two clients. Sounds were recorded
(OpenAL Soft wave writer) and measured, not listened to.

| # | Check | Result |
|---|-------|--------|
| 1 | `./gradlew build` | green; Godzilla tests: Rules 21, Assets 15, Renderer 5 |
| 2 | Model, clips | idle / walk / turns / roar / charge → fire → end / claws / tail / stomp / slam / rage / retreat seen in game, both sides of the body in MP |
| 3 | Scale | 120 blocks tall, the head level with the tallest downtown towers; the body fills the view from 70 blocks |
| 4 | Breath | plates light from the tail up, mouth opens, beam leaves the mouth: down at a roof 40 up / 70 ahead (head pitch ≈ −47°), up at a flier 150 up / 60 ahead (≈ +51°); contacts every tick on the target |
| 5 | Claws, tail, stomp, slam | contacts on fliers in front of the chest (slam, claw), street targets (stomp), animals trampled |
| 6 | Destruction | 60 k blocks in the first 40 s, 900 k session cap reached on a long run, collapses, transformer blow-outs, power cascades, sirens, smoke and dust |
| 7 | AI | ground and flying heroes: only reachable moves (roar when out of reach), stand-off / back-off from a hero 141 up (`GZQA backoff … want=50`), breath where the beam points, slam at 0 / 61 / 40 |
| 8 | Rage, retreat | rage (red bar, blue plates, civil-defence alert on both clients), retreat → sink → summary line, 0 entities left |
| 9 | stop / cleanup / reset | `/city reset` restored 601 k blocks / 1 136 chunks in 21 s and 1.08 M blocks / 1 387 chunks in 22 s; a reset while the kaiju ran stopped it and restored 293 k blocks with no leftovers; a start during the restore is refused |
| 10 | Dedicated server + 2 clients | boss bar, chat, alerts, subtitles, poses and sounds the same on both clients (step 80/80, charge / beam 3/3); TPS 20.0 in every sample, mean tick 7–16 ms, kaiju tick ≤ 3.7 ms per 5 s (worst single 39.6 ms); no leaked entities after the end (0 kaiju, 0 debris) |

Bugs found in game and fixed on this branch: breath at targets the beam could not point at (and a beam aimed with the
head's axis while it leaves the mouth 5.4 blocks above it); no attack on a flier 60 up in front of the chest; debris
saved mid-air in unloaded chunks came back untracked (43 falling blocks hung in the air after a retreat, the cleanup saw
none); a start during `/city reset` left 609 blocks unrestored.

Known limits (not bugs of the event): vanilla tracks the entity only within (view distance − 1) × 16 blocks, so on a
server with view distance 10 the kaiju is drawn up to 144 blocks; llvmpipe FPS (1–7, MP 2–3) says nothing about GPUs;
dense rampage smoke can hide the body from 60–120 blocks; the full city mix (Godzilla + collapse + grid sounds) touched
+1.6 dBFS for ≤ 104 samples in the MP capture when a beam start met a stack of collapse cues.

