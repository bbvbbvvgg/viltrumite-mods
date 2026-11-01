# Meteor Rain 2.0 — visual, smoke, city fire, red sky, sound (plan)

Branch `feat/meteor-rain-visual-polish-20261101`. Design spec: the user's task of 2026-11-01.

## Invariants (must not change)
- `/meteor start|stop|stop all|status`, targets, manual start/stop, no end timer.
- `MeteorRainRules` numbers, `MeteorSpawnEvent.spawnPoint/launch`, `MeteorEntity` motion, hit test,
  explosion (power 12), magma crater, `NoLoot`, every `interaction/builtin/Meteor*` rule.
- `/city reset`, the change journal and the citizen AI stay as they are.

## Parts
1. **Meteor look** (`client/render/MeteorRenderer`): the magma core keeps its size and tumble; the 8 ghost
   copies are gone. New layers: a pulsing emissive heat shell, a camera-facing additive glow halo and a
   tapered plasma ribbon strictly along `-velocity`. Client particles (`client/render/vfx/MeteorTrailFx`):
   flame puffs, hot sparks with inertia and gravity, dark turbulent smoke lit orange near the head.
   Distance LOD so ten meteors stay cheap.
2. **Smoke** (`client/particle/MeteorSmokeParticle`, `MeteorEmberParticle`, `MeteorFlameParticle`): new
   procedural sprites (irregular puffs, 8 variants), roll, growth, fade-in/out, heat-lit colour that
   cools to dark grey. Impact (`client/render/vfx/MeteorImpactFx`): dust ring, dense billow, hot rising
   column, ballistic debris sparks, ash fall, smouldering craters (bounded list).
3. **City fire** (`worldevent/meteor/fire/`): server-side hot spots with heat, fuel and a material class.
   Rain meteors ignite several spots around the crater. Spots climb facades, cross roofs, throw burning
   brands across roads (delayed landing, client arc), merge into zones. Own block
   `viltrumitecore:meteor_fire` burns on any solid face (stone, concrete, glass) and dies out on its own
   through scheduled ticks, so an unloaded or forgotten flame never stays forever. Damage: glass and
   fragile parts break, mineral blocks char by stages, flammable blocks get vanilla fire. Budgets: spots
   processed per tick, block writes per tick, spot cap per level, brand cap; never loads chunks; drops
   spots of unloaded chunks; never touches unbreakable or block-entity blocks. All writes go through
   `Level.setBlock`, so the city journal records them and `/city reset` restores them; a reset (seen
   through `RestoreQueue.running/startedAt`) clears every spot and brand. Fire zones feed `DangerMap`.
4. **Red sky**: `SkyTint.Tint` gains an optional cover colour (old 5-arg tints unchanged). Server
   `MeteorRainSky` sends `MeteorRainSkyS2CPacket` to players in the rain's dimension within range of an
   active rain; client pushes priority 3 (cinematic scenes and bosses win), eases in and out.
5. **Sound**: client `MeteorRainAudio`: distance bands for flight (far roar / near roar + pass-by),
   impact (near crack, far boom delayed by the speed of sound, shockwave bass, debris, rubble), voice
   limiting for clustered impacts, fire roar loops of the nearest zones and flare one-shots. CC0
   Freesound sources through Openverse plus own synthesis (`tools/sfx/meteor_rain.py`).

## Verification
`./gradlew build` (Java 17), unit tests for pure rules, dedicated server + client under Xvfb on a
synthetic city block (concrete, stone, glass, wood), `/meteor start`, screenshots, TPS, `/city reset`.
