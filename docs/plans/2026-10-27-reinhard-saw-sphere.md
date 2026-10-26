# Reinhard item 19: Saw Sphere replaces Rain of Cuts

Goal: replace Rain of Cuts (Holy Sword slot 4, Reid drawn) with the Saw Sphere from the owner's spec
(`reinhard_point19_saw_sphere_spec.md`): a 12-block barrier sphere, 35 spinning blade-saws that sweep all of it in
13 s, Reinhard hovering and spinning at its heart, and a clean ending (shatter, descent) or an immediate stop.

Stacked on PR #53 (`feat/reinhard-icons-redraw`, core 1.43.0). Core 1.43.0 → 1.44.0, network protocol 6 → 7.

## Decisions
- One pure score, `hero/reinhard/saw/SawChoreography` (260 ticks): rise 0-24, shell 2-22, discs materialise 30-50,
  46 swings every 4 ticks from 44 to 224 (the first 35 launch one disc each, inner ring first, the facing disc of
  the ring), hold to 226, final swing 232, plunge 232-244, cut ends 246, shell cracks 234 and shatters 248,
  descent 246-258. Rings {home, count, start height}: {0,1,11.68}, {4,7,10.9}, {7,11,9}, {9.2,16,7}. Both sides
  derive every disc position from it; the network carries one start packet and one end packet per scene.
- Helical descent: a disc's angle is `PHI + KAPPA * (startY - height)` with `KAPPA = 0.12` rad per block, so every
  disc sinks along a spiral in the spin direction and its launch angle is exactly its home angle (the blade faces
  it). A first version orbited by time (0.03 rad/tick); because the launch order follows the facing, the arcs of
  neighbouring discs drifted apart and left 17 blocks uncut near the wall at y 2-5 and on ring 2 at y 8. The
  height-based turn ties every height to one angle per disc and closes those gaps; ring 1 starts at 10.9 (was 10.4)
  to reach the y = 11 blocks at radius 2.8-3.4 when the caster stands off the block centre.
- Barrier = `SawShell`: half-block cells the sphere surface passes through above Y0, enumerated from the centre
  in a fixed order (client and server agree without sending cells). Activity is decided once at activation by a
  flood fill from the caster over free cells (PARTIAL cells next to the air stay active), so the shell ends where
  it meets terrain and never heals. Collision: `SawShellCollisionMixin` adds active cells to `Entity#collide`'s
  shape list for every entity except the caster; teleports across the shell are cancelled (`EntityTeleportEvent`,
  commands exempt). Visual = collision: the renderer meshes exactly the active cells.
- Cutting: every live disc slab cuts the blocks it overlaps above Y0 through `HeroDestruction.setAir(CLEAR_FLAGS)`
  with `HeroDestruction.canDestroy`, `level.mayInteract` and a Forge `BlockEvent.BreakEvent`; liquids and blocks
  whose collision top is at or below Y0 are skipped. Budget `CarveBudget.perTick(640)` shared by all scenes of a
  level. Container / lectern / campfire / item-handler contents are extracted before the clear, merged into full
  stacks and dropped at most 4 entities per tick (the rest at the end), nothing duplicated.
- Hits: living entities in the sphere above Y0, gated 5 ticks per target, `ReinhardTargets.canHit`. Mortals
  (non-players and HUMAN players) through `Annihilation.strike`; heroes take 20 % of their max health per hit as
  `playerAttack(caster)` with i-frames reset (their own protections still apply).
- Caster: held on the hover curve (teleport back when > 0.35 blocks off), absolutely invulnerable
  (`ReinhardHero.isAbsolutelyInvulnerable`, absorb + damage clamps), no external control, no jump / air step, no
  attack or use keys, input zeroed; the own camera is forced to third person back and restored to the saved one.
- Lifecycle: `SawSphereManager` per level (tick phase `SAW_SPHERE` between `WORLD_CUT` and `CARVE`), region tickets
  over the covered chunks (timeout 300 ticks), watchdog every tick (online, alive, same level, not spectator,
  still Reinhard with this scene). Any break ends the scene at once (`end(true)`): loot flushed, tickets removed,
  shell unregistered, end packet to viewers, state cleared. Level unload / server stop drop all scenes.
- Start refused with fewer than 4 free blocks over the head, an unloaded covered chunk or a buried centre.
- Cooldown 160 ticks counted from the start (the 13 s scene is longer, so the slot is ready when it ends).
- One-handed clip `saw_sphere` (`make_reinhard_clips.py`): the left arm out for balance, the right hand alternates
  the proven `reid_slash_sweep` chest-high path forward / backward every 4 ticks, airborne legs, a deeper coil for
  the final swing; the renderer adds the spin yaw (`SawSphereClient.modelYaw`).
- Sounds: `saw_swing` reuses `cut_cinematic_sweep`; `saw_hum` (4 s seamless loop), `saw_grind`, `saw_finale`,
  `saw_shatter` are original synthesis (`tools/sfx/reinhard_saw_sphere.py`, CC0). Grind is rate-limited to one per
  3 ticks per scene, swings alternate pitch, the hum is one looping instance per scene.
- Loadout: `reinhard:cut_rain` migrates to `reinhard:saw_sphere`; untouched old defaults move to the new default.

## Coverage
`SawChoreographyTest.discsCutEveryBlockOfTheSphereOnFlatGround` (the shipped score with the server's own disc test,
cross-checked by a Python port during design): on flat ground every block whose centre lies inside the sphere above
Y0 is cut (100 %, about 3,600 blocks), for six anchor yaws (0, 13, 37, 90, 200, 300) and caster positions from the
block centre to its corners. The only exception are the columns under the caster's own hitbox up to y = 5: he
hovers there and the crown disc stays above his head (the headroom rule already needs 4 free blocks over it).

## Checklist
- [x] Server state machine, cleanup, barrier geometry and collision, disc paths, block and entity processing.
- [x] Network (start with late-join age, end), client scene, renderer (shell mesh, saws, cracks, shards), sounds.
- [x] Clip, first-person offsets, icon, lang (11 files), credits, migration.
- [x] Tests: choreography, shell, packets, hands, migration, sounds, release; existing Rain tests moved over.
- [ ] `runClient` visual / audio acceptance: no display or audio in this environment.
