# Reinhard N — World Erasure cinematic polish (plan)

Branch `feat/reinhard-n-erasure-polish`. Polish of the EXISTING ability: verdict (`DivineTargets`),
target rules, `Limbo`, cooldown, damage and the `world_erasure` clip are unchanged.

## Findings (old implementation)

| # | Symptom | Root cause |
|---|---|---|
| 1 | Glass breaks unconvincingly | `dash_impact.fsh` reveals a fixed 6-site Voronoi pattern with a growing radius over 30 ticks: a pre-made pattern fades in, nothing grows or branches. |
| 2 | Shards fall badly | Six screen-sized Voronoi cells slide straight down in 2D (`offset.y = -1.25*fall`), ±0.22 rad roll, no depth/tumble/mass, all similar. |
| 3 | HUD stays visible | Nothing suppresses HUD: Forge overlays, hero HUDs (`RenderGuiEvent.Post`) and the ability bar (`ViltrumiteInGameHudMixin`) keep drawing. |
| 4 | Things keep moving | `WorldPause` only FREEZEs players and hostiles within 32 blocks (ControlManager); passive mobs, projectiles, items, falling blocks, particles, block entities, fluids keep ticking, and every client keeps simulating/animating entities (lerp, walk/idle). |
| 5 | Camera not locked | Mouse lock exists only for DIO's `TimeFreezeView` (client frozen-id set) and Road Roller; WorldPause blocks keys only, and never the caster. |
| 6/7 | Target pose / display | The target is not frozen on clients; at 70–110 a GUI-space white silhouette (`GuiEntitySilhouette`, inventory-style render) is drawn at its projected point or screen centre: flat, re-posed, not in the world. |
| 8 | Sync | Speech pre-roll (70) + a separate 150-tick cinematic clock; the client only learns about the scene after the speech. Removal (90) happens mid "dark" with no visible dissolve. |

## Design

One scene clock = `WorldPause` elapsed (activation = tick 0), all beats in `ReinhardRules`:

| Ticks | Beat |
|---|---|
| 0 | N: absolute stop (server + clients), camera lock (all players incl. Reinhard), HUD hidden, world audio ducks; line page 1 |
| 20–80 | slow crack growth (60 t): first hairlines → rays extend → branches → concentric web → critical shimmer |
| 50 | Reinhard's existing `world_erasure` clip starts so its authored cut lands on the rupture |
| 80–90 | critical rupture (0.5 s): cracks race to the edges, brightness pulse |
| 90 | SHATTER: 3D textured shards of the frozen frame burst, tumble and fall into a void |
| 92–112 | target (real model, frozen pose, real world position) glows white |
| 112–150 | target dissolves texel by texel into rising light; 150 = server removal |
| 160–192 | rebuild: shards rewind into place in a cascade, very strong cinematic shake |
| 192–205 | cracks close, shake settles; 205 = pause ends, everything resumes |

Mechanisms (extend, no new engine):
- `TimeFreeze` gains a `WorldPause` source (all non-owner entities, server-wide) and a client "world frozen" flag; existing DIO mixins do the work. Small additions: particles, texture animation, rain/clouds, scheduled block/fluid ticks, block events and block entities pause while a world pause is active.
- View lock: `TimeFreeze.Source.lockedView` (server) + `TimeFreezeView` (client) for every player incl. the caster.
- `CinematicHud`: one client gate that cancels `RenderGuiEvent.Pre` and draws only subtitles + scene layers.
- Glass: pure `ErasureGlass` (seeded crack graph + shards), drawn after the existing `dash_impact` chain via the existing `GameRendererDashMixin` seam; shader keeps only full-screen grading/void/rebuild distortion.
- Dissolve: real-model geometry captured from the entity renderer (same idea as `GuiEntitySilhouette`), replayed in world space.
- Sound: existing crack/inhale kept; new original procedural cues for tension, shatter, dissolve and rebuild.
