# Combat & Interaction Rework

Base: PR #40, `381850ab312369aceb2c8f65db556c0c6226da88`. Branch: `rework/reinhard-combat-interaction`. PR base must be `fix/reinhard-strike-fluidity`.

## Audit
- Primary currently selects FistCombo only in Knight; Holy Sword incorrectly selects LightPunch even with Reid drawn.
- Mouse seam has authoritative revalidation. RMB only finishes an existing grab. GrabThrow already owns ControlManager acquisition/release and launch; reuse it.
- Slot defaults are R/Y/Z/V/B/N. Restrained uses Y dash, Z catch. Knight uses Y kick, Z grab, V behind, B stomp, N reflect. Holy uses R Reid, Y chop, Z diagonal, V rain, B ground, N erasure.
- StepDash and DashBehind have independent client predictors. A merged server-selected context must not run both predictors.
- ArrowWard + HeroProjectiles already handle impact interception, deflection acceleration, reflection ownership, delayed reapplication. CatchWindow/ReflectWindow currently overwrite the main action. Automatic reactions must not do that.
- EyeLasers is continuous hitscan (playerAttack source every four ticks), not a projectile. Iron Man repulsors/missiles are entity projectiles; unibeam is a ray and uses playerAttack too. Generic melee source classification cannot distinguish beams without a narrow source-context hook.
- Both scenes play WORLD_CUT_BLAST on tick 80 (destruction begins), and CUT_RUMBLE at volume 2.8 repeatedly during work. DONE only plays the quieter tail. The accent must move to DONE; background rumble must be reduced. DONE is queue completion, not a client timer.
- DiagonalCutGeometry scans a full axis-aligned volume around a thin tilted slab. Every reject spends CarveJobs budget. Replace with dominant-normal raster columns, with a finite fixed candidate count per column. Keep every visit budgeted and exact contains mask. Do not increase shared budget.
- Diagonal fade is 90 ticks versus ground 70. Equalize only after DONE, independently of server work.
- Gray sheet is one translucent quad across the whole span, with bright lip quads crossing its normal. Replace with opaque dark recessed interior, side walls and thin irregular luminous lips; retain bounded pixel accents.
- PR40 sampler, hand solver, authored frames, axial roll and recovery must remain intact.

## Layout (default keys; remapping remains supported)
| Action | Old | New |
|---|---|---|
| Primary | LMB jab/combo | LMB jab/combo; drawn Reid sword strokes |
| Grab/throw | Knight Z / RMB finish | Empty-hand RMB on eligible target / RMB finish |
| Kick/stomp | Knight Y / B | Knight Y / crouch+Y |
| Movement | Restrained Y; Knight V | Z in all modes; Knight crosshair target = behind, otherwise step |
| Catch/reflect | Z / N | Automatic; no active slots |
| Reid | Holy R | Holy R |
| Lethal chop | Holy Y | Holy Y (when sheathed) |
| Rain | Holy V | Holy V |
| World cuts | Holy B / Z | Holy B / crouch+B |
| Erasure | Holy N | Holy N (separate deliberate action) |

RMB must not claim block interactions, held items, allies or invalid targets. Do not silently fall back from a rejected behind dash to a different move. Preserve existing registry IDs and ability ordinals.

## Implementation sequence
1. Context policy, routing, loadout migration, panel/HUD/languages and input regressions.
2. Automatic defense state layered over actions, held beam reaction and physically correct projectile treatment; no fake reflections.
3. Drawn Reid primary sword strokes with bounded sweep contacts and shared FP/TP clips/grip.
4. DONE-only finishing accent, bounded concurrent audio, quiet work bed.
5. Dominant-normal raster, exact coverage tests, recessed wound rendering and equal fade duration.
6. Java 17 Forge build, all tests, final-bone continuity, work-count benchmarks, network/input review. Attempt runClient; report actual limitations rather than claiming visual acceptance.

## Acceptance reporting
Track each stage separately. A draft/incomplete implementation is not the requested full rework. Record failed/unavailable checks and do not ship an unbuilt JAR as final.

## Continuation: close the draft gates
- Continue PR41, retaining PR40 ancestry. Integrate Unibeam/PulseUnibeam through a narrow generic server ray-defense clip; truncate real damage and synced endpoint, not just a visual beam.
- Use a server-owned local palm target in Reinhard's existing snapshot actionTarget and a free-hand bit. Solve the existing arm chain after weapon constraints; keep weapon-owning hand unchanged. Client final-hand contact cache feeds beam endpoints and held-arrow rendering in FP/TP, never C2S gameplay authority.
- Classify blast/splash as bracing, not projectile reflection. Inventory attacks by execution path and add source/behavior regression coverage. Beam refresh runs each tick, damage cadence unchanged.
- Add deterministic final-bone/matrix checks for defense and recovery, explicit FP evade, physical-arrow pickup regression, bounded concurrent DONE/tail audio with mastered PCM headroom.
- Add opt-in phase telemetry and run actual dedicated-server/client scenes, including two jobs. Report true server ticks/wall time and software-render FPS separately from the synthetic queue model. Capture actual audio output if available; digital loopback is not human listening.

## Implemented continuation
Unibeam/Pulse and execution-path attack matrix, free-hand final contact and FP evade, final-bone/head/weapon/wire/projection regressions, corrected physical-arrow pickup order, mastered/crossfaded DONE audio, tight-row exact raster, real single/concurrent carve/DONE/MSPT/FPS and OpenAL loopback checks are implemented. Native testing found and corrected pickup reset and camera/GUI projection bugs; do not label an earlier pass as final acceptance. See the updated report and machine-readable evidence. Human audio approval, GPU/shader acceptance and exhaustive movement/input combinations remain explicitly separate, not missing code hidden by green tests.

## 1.36.2 correction, user-confirmed audio selection
- Sound 1 = original WORLD_CUT_TAIL, louder and only after authoritative DONE. Keep the full eight-second recording and line-wide nearest-point placement/range320. No impact paired at DONE.
- Sound 2 = procedural WORLD_CUT_BLAST only as quiet 0.8-second pulses while the rupture works, every40ticks from tick96 (not the initial destruction tick), shared gate. Remove sound3 CUT_RUMBLE from both cuts.
- Check default crouch+B through real input, not only QA force-cast. Preview the diagonal icon and actual cooldown while crouching. Y remains Chop; do not add another active key.
- Add routing/timing/HUD regressions; full build, audio-format tests and native DONE/audio capture. Version1.36.2; publish to existing stacked PR41.
