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

## Status and remaining work (resumed at 2415714d)

Done: absolute world freeze (server + client), view lock for everyone, one 205-tick scene clock
(`ReinhardRules`), grading curves (`ErasureUniforms`), pure glass model (`ErasureGlass`) and its
renderer, pure dissolve curves (`ErasureDissolve`) and the real-model dissolve renderer.

Remaining, in order:
1. `ErasureScene` on the one clock: glass seeded per scene, origin = target on screen; sound cues
   on the beats; capture of the level projection/view; glass + dissolve drawn after the post chain
   (`GameRendererDashMixin` renderLevel TAIL); target hidden from the level pass while the scene
   owns it; scene-time camera shake (camera angles only, never the player's view); cleanup of the
   frame copy and cells on every end reason.
2. `CinematicHud` (client/hero): one gate that cancels `RenderGuiEvent.Pre` (vanilla, Forge and
   hero HUDs, ability bar) and hands, and draws only hero subtitles + scene layers. Predicate-based:
   nothing to restore, so cancel/death/logout/dimension change cannot leave the HUD hidden.
3. `dash_impact.fsh`: the old 6-cell Voronoi crack/shard code is replaced by grading only
   (tension drain, rupture flash + chromatic split, void drain, rebuild shock).
4. Sounds: original procedural cues (tension build, shatter, dissolve shimmer, rebuild + final
   impact) through the `.ogg.b64` pipeline, subtitles in every locale.
5. Tests (timeline beats, cue order, catch-up, cleanup, glass/dissolve curves), `./gradlew build`,
   runtime check (single player + dedicated server with two clients), PR.

## Status (core 1.57.0)

Items 1–4 are implemented: `ErasureScene` (one clock, beats, glass origin at the target, render
seam after the grade, scene-owned target, `ErasureShake`, cleanup on every end reason),
`CinematicHud`, grade-only `dash_impact.fsh`, five original sounds via the lang/sound overlays.
Item 5: unit tests in `ErasureSceneTest`, `CinematicHudTest`, `SceneEntityHideTest`; build and runtime
checks are recorded in the PR.

## Runtime QA (dev client, Forge 1.20.1, llvmpipe, no audio device)

- Single player (integrated server): full 205-tick scene on a husk; camera and movement locked
  (mouse + W held during the scene change nothing), HUD and hand hidden, subtitles shown, husk
  removed, HUD back at the end. Pause menu mid-scene freezes the scene; it resumes from the same
  point. Third-person (F5) view locked as well.
- Multiplayer (dedicated server + 2 clients): caster and witness play the same beats in sync,
  glass origin at the target in each view, the witness has no HUD during the scene. A witness
  reconnecting mid-scene (joined during the dissolve) catches up and ends together with the caster.
- Player target (MP): the target's camera and movement stay locked (mouse + S held), it lights
  up and dissolves in both views, goes to Limbo at 150 and returns after the Limbo stay.
- Moving target: a walking husk freezes mid-step and is erased in that pose.
- Abort: the caster's client killed mid-scene (shatter) -> the witness's scene ends at once
  (CANCEL), HUD back, the target survives.
- Bug found and fixed: an erased player was kicked for "floating too long" in the Limbo void
  (and a player frozen mid-air would be after 80 ticks): `WorldPause.holdsAirborne` resets the
  vanilla floating counter in `AntiCheatBypassMixin`.
- Bug found and fixed: the last dissolve motes (head) hung as a white blob through the rebuild
  (mote age was the gap to a stopped front); the body bloom stayed on after the removal.
- Not verifiable here: audio (OpenAL device unavailable), real GPU performance.
