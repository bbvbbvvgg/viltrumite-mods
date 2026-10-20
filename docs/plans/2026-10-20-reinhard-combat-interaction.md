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
