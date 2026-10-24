---
name: animation-system
description: Mandatory for all animation, pose, player model, item/entity animation, VFX, shader, camera, and visual-reference work in viltrumite-mods. Choose the existing renderer, author synchronized motion, and verify actual first/third-person quality. Includes Regulus, Homelander, Iron Man and Reinhard patterns.
---

# Animation system — production workflow

Read this skill **before** changing any animation, pose, renderer, VFX, animated equipment or screen effect. Read `AGENTS.md` and `.memory-bank/project.md` first. Use the current source code as the implementation authority. Read only the reference sections needed for the task:

- [Implementation catalog](references/system-catalog.md): original player mixins, `PoseRig`, geo parser/controller, flight, Iron Man parts, shaders and shared VFX.
- [Reinhard lessons](references/reinhard-lessons.md): full-body renderer, clip composition, grip/IK, continuity regressions, World Cut, World Erasure and the PR #43 The Hand shader.
- [Shared hero toolkit](../add_hero/references/shared-toolkit.md): server-owned impacts, hit-stop, debris, shockwaves, `HeroFx`, `CameraShake` and `PixelVfx`.

This file is a **decision and acceptance procedure**, not a list of poses to copy. Existing characters are references with known limitations. Reinhard has extensive tests but still needs native visual acceptance.

## 1. First decide what owns the visual

| Target | Existing owner / reference | Use when |
| --- | --- | --- |
| Legacy player body | `client/mixin/*ModelMixin`, `client/anim/pose/PoseRig` | Vanilla player model, existing Viltrumite actions |
| New hero on vanilla body | `client/regulus/RegulusPoser`, `client/homelander/HomelanderPoser`, `client/ironman/IronManPoser` | Skin, normal joints and additive combat poses |
| Custom full-body geo | `client/reinhard/ReinhardRenderer` | Hero replaces vanilla body, needs custom elbows, clothing, attachments and clips |
| Large suit replacement | `client/ironman/hulkbuster/HulkbusterRenderer` | A separate body model replaces player rendering |
| First-person arms and held item | `client/anim/pose/FirstPersonArm`, `client/ironman/IronManFirstPerson`, `client/reinhard/ReinhardFirstPerson` | Camera-space arms, real hand anchors, both physical hands |
| Parts attached to a player | `client/anim/render/PlayerGeoLayer`, `PlayerBoneMap` | Armor, helmet, weapon pieces, effects following final posed bones |
| Animated geo item/entity | `client/anim/animation/AnimationController`, `AnimCache`, `AnimRenderer` | Blockbench geo and animation JSON, entity or item clips |
| Ordinary world feedback | `hero/fx/HeroFx`, `client/render/vfx/HeroImpactFx`, `PixelVfx`, `CameraShake` | Impact, sparks, dust, blade trails, shockwaves, shake |
| Special scene material | `client/render/erasure/ErasureRenderer` + `client/reinhard/cut/WorldCutErasure` | The **already approved** The Hand World Cut shader only |
| Screen effects | Existing `dash_impact` post program, `FlashOverlay`, `SoundDuck`; flight `sonic_boom` | Full-screen tint, crack, erasure, flash, ducking |

Do not add PlayerAnimator, Emotecraft, GeckoLib animation, Veil, a second pose/keyframe engine or a generic VFX pipeline. GeckoLib assets may be read by the project's **own** geo loader. Do not use GeckoLib as a new runtime animation engine. A special shader is not permission to build another general renderer. If the current system lacks a feature, extend its existing seam and update this skill.

Every active hero ability needs recognizable animation and visual feedback in each relevant view. Cover both first and third person for visible player actions. If a passive or non-visual action has no meaningful pose, record the intentional exception in its design and still provide clear player feedback.

Choose **one pose owner** for each body and view. A hero can have layered actions, but should not have several competing mixins that independently own the same arm. Check mixin registration, Forge event order and render cancellation before adding a hook.

## 2. Four reference implementations: copy the lesson, not the character

| Hero | Read first | Reusable lesson | Do not copy blindly |
| --- | --- | --- | --- |
| **Regulus** | `client/render/animation/RegulusAnimationManager`, `client/regulus/RegulusPoser`, `client/anim/pose/PoseRig` | Shared snapshot clock; separate TP/FP and per-hand weights; keyed actions, mirroring and pivot restoration | Legacy action-specific mixin count and hero-specific branches |
| **Homelander** | `client/homelander/HomelanderPoser`, `HomelanderVfx`, `client/mixin/FirstPersonHomelanderMixin` | Compact snapshot-driven poses; independent view fades; eyes/beam VFX and state cleanup | Fixed-duration roar or laser pose for unrelated abilities |
| **Iron Man** | `client/ironman/anim/IronManAnimation`, `IronManPoser`, `IronManFirstPerson`, `IronManPartsProvider` | One state machine for layered combat, guard, equipment, FP/TP; quaternion aiming; skin/part reveal | Separate signature mixins, redundant clocks or a new suit renderer for each mark |
| **Reinhard** | `client/reinhard/ReinhardRenderer`, `ReinhardLayers`, `ReinhardPlayback`, `ReinhardGrip` | Full geo body, stance/gait/action composition, real elbow and weapon matrices, continuity gates | The unverified look, brittle grip solutions, cinematic complexity or specialized shader for ordinary hits |

The project design references are `docs/design/2026-10-05-regulus-design.md`, `docs/design/2026-10-08-homelander-design.md`, `docs/design/2026-10-09-ironman-design.md` and `docs/design/2026-10-11-reinhard-design.md`. For external art/audio sources also inspect `tools/assets/ironman_sources.md` and `tools/assets/reinhard_sources.md` where applicable. These specs express intent; the current code shows what actually shipped.

Use a source-material reference **and** the nearest working code example. For a new hero, prefer the smallest suitable existing architecture. Do not use Reinhard's full-body replacement merely because it has more bones.

## 3. Visual-reference contract (before implementation)

For a new move or major rework, prepare a short animation brief in the existing hero design/plan:

1. Identify the canonical clip, stills, user-supplied video or approved original art. Record the source, time range and asset license. Do not silently invent a different visual identity or copy unlicensed assets into the repo.
2. Define the **readable silhouette** at idle, anticipation, impact and recovery. Record the camera angle and whether the move is one-hand, two-hand, grounded, flying or mounted.
3. Mark 3–6 motion beats with approximate times **and the authoritative contact tick**. Include the lead limb, hips, head, follow-through, secondary motion, sound and VFX beat.
4. State what stays attached: weapon, sheath, held item, arrow, armor, effects. Specify each hand's ownership at every phase.
5. Define transitions: start from idle/walk/flight, action interruption, guard/catch, cancellation, death, hero change, relog and dimension unload.
6. State the acceptance shots: FP and TP, front/side/back, left/right hand, low/high camera, 30 and high FPS. A screenshot of a single good pose is not acceptance.

If reference footage is ambiguous, write down the interpretation. Preserve established visuals unless the user requests a change. A more elaborate effect that changes the character's identity is a regression.

## 4. Timing and state are shared across every view

- **Gameplay is server-authoritative.** Read `HeroPublicSnapshot`, synced action/phase/elapsed/length/flags and existing owner sections. Do not decide damage, contact, ability success or target ownership in a renderer.
- Action length is **not always 20 ticks**. The 20-tick countdown is a legacy Punch/Chop pattern. JSON animation times are seconds; gameplay clocks are ticks (20 ticks/s). Define per-action durations and contact points.
- Use one action clock per player/action across TP body, FP arms, elbows, items and world projections. `RegulusAnimationManager` and `IronManAnimation` demonstrate shared state. `ReinhardPlayback.actionTicks` uses opt-in `ActionClock.sample(action, phase, elapsed, length, clientTicks)`.
- `ActionClock.sample` is **opt-in**, not a global replacement for old `tick/time`. It is monotonic inside a segment, reconciles at 0.75–1.25x, predicts at most three ticks and clamps to the server length. Sample `tickCount + partialTick`; repeated view/shadow passes at the same timestamp must be idempotent. Reset on hero exit.
- For one-shot cinematic progress, use packet start game time or synced elapsed. A local render counter causes drift on pause, reconnect or dimension catch-up. Looped clips must wrap explicitly where needed: `AnimationController.seek/overlay/blend` do **not** wrap LOOP time for Reinhard; `ReinhardPlayback.time` does.
- Use real-time delta for on/off weights, e.g. `1 - exp(-k * dt)` with bounded `dt`. Do not apply a fixed per-render `Mth.lerp(k, ...)`. Cache by entity **and view/hand** when they need independent weights. Never advance a filter twice for the same timestamp or in a shader shadow pass.
- Keep impact keys sharp. Blend **recovery** to the live stance/guard when an action ends. Do not add an incoming low-pass that erases contact, stalls a dense gait or delays a handoff.

## 5. Compose motion by ownership

Recommended order for a layered geo hero (adapt it to the actual action):

1. Reset shared model bones. Sample base stance and its transition.
2. Apply locomotion from continuous walk distance; handle run, flight, takeoff, landing and crouch without double offsets.
3. Apply active action, guard or held stance with an explicit priority and ownership mask.
4. Add defense/contact overlays only to free limbs. Main action and two-hand weapon ownership take precedence.
5. Resolve final elbow, wrist, palm and weapon frames from the **same** posed parent chain.
6. Add secondary pieces, particles and sound/VFX at the matching authoritative beat.
7. Render cutout/translucent, outline and glow in the correct passes; restore all GL state.

For vanilla bodies, use `PoseRig` and the existing `PlayerModel` seams. Keep the vanilla base pose and copy outer skin layers. Restore modified pivots before the next frame. Do not zero unrelated joints and destroy flight/crouch pose. Check the actual effective mixin order; a numeric priority alone does not prove which pose wins.

For geo bodies, use `AnimCache`, `AnimationController` and the `ReinhardLayers` channel-ownership policy. A clip that omits a channel must **not** reset that channel to zero. Use quaternion slerp for partial rotations and preserve authored exact keys at full weight. Do not lerp matrix elements. Keep position and scale owned only by the clip's declared channels. The shared cached `BakedGeoModel` must be reset/reposed immediately before drawing; do not retain it across reload.

For IK or two-hand weapons, author the weapon trajectory first. Solve reachable palms to its stable grip/guard/pommel anchors; use parent-aware final transforms, not shoulder-only positions. Preserve elbow poles and directed forearm twist. A wrist that rotates 180° to reach a mathematically correct grip is still broken. Check the entire blade mesh, sheath clearance, hand penetration and intermediate frames.

## 6. First person and attached equipment

- FP is a separate camera-space composition. Reuse the same action **time**, not necessarily the same TP shoulder Euler angles. Use `FirstPersonArm` once at the correct outer transform; do not apply a second vanilla swing to an authored attack.
- Treat `MAIN_HAND` / `OFF_HAND` as inventory hands and `LEFT` / `RIGHT` as physical arms. Map them through the player's main-arm setting. Test left-handed mode and both held-item slots.
- An offhand that must remain visible uses the existing `AlwaysVisibleOffhandMixin` policy. A cancelled `RenderHandEvent` path must render the intended empty physical arm itself; the normal item-render mixin will not run for it.
- Never change inventory to hide a sword or occupied item during a cinematic. Hide only the conflicting render, and restore it after the action. Reinhard's `RenderHandEvent` / `RenderArmEvent` path is the reference.
- `PlayerGeoLayer.partFrame` and the hero's final bone/hand frames must agree. A bent elbow changes the item transform. The same attachment matrix must drive TP equipment, FP equipment and contact VFX.
- If a hero cancels `RenderPlayerEvent.Pre`, it owns **everything** the vanilla renderer used to draw: held items, name tags, invisibility, glowing outline, armor/layers, first-person arms, shadow-pass behavior and state cleanup. Read `ReinhardRenderer` and `HulkbusterRenderer` before doing this.
- For world contact derived from FP, convert camera view to world with the correct forward convention and current perspective. Do not trust an unrelated GUI projection. Test FOV changes and contact under movement.

## 7. VFX and shaders: reuse first, protect compatibility

- Shared hits: `HeroFx` on the server, `HeroImpactFx` on clients. Reuse `PixelVfx` for painted particles/shells, `CameraShake` for shakes, `FlashOverlay` for flashes, `SoundDuck` for cinematic ducking and the existing `dash_impact` post program for screen-space effects.
- Ordinary world particles should match the mod's paint-pixel style. An approved reference-driven special effect may differ, but must be isolated. Never replace an established effect to make a refactor easier.
- **The Hand World Cut is an explicit exception:** `WorldCutErasure` feeds the existing specialized `ErasureRenderer` with a registered `thehand_erasure` core shader. It does not add a second animation engine or network protocol. Preserve its source attribution and license. It is deliberately disabled with active Oculus/Iris shader packs; do not claim shader-pack compatibility.
- In a render callback, use the event camera and the correct coordinate space. Skip shadow passes when the effect is view-only. Preserve depth, cull, blend, depth mask, texture bindings, model-view stack and render targets with `try/finally`.
- A screen effect must set all uniforms every active pass, include itself in the early-out condition and not leak into other heroes. A world effect must cap geometry, distance and lifetime. Clean up on logout, unload and F3+T resource reload.
- Audio/VFX are part of the motion: align anticipation, impact, follow-through and silence. A correct file duration or waveform is not proof that the sound works in game.

## 8. Quality gates (required for every changed move)

**Source and tests**
- Verify geo bone names, pivots, UVs, texture/clip paths, animation length, loop and interpolation mode. Check missing-channel behavior, resource reload and left/right mirroring.
- Test timeline edges: start, contact-1, contact, contact+1, end, cancel, phase handoff, relog catch-up. Preserve server contact ticks.
- Sample position **and velocity** across every walk/run cycle. Dense gait keys must not stop at each sample. For final constrained joints, scan quaternion angular jumps and mesh clearance at high sample rates (240 Hz is the Reinhard regression precedent).
- Check 30/60/144/240 Hz render sampling and repeated timestamps. A shadow pass or GUI preview must not advance a pose. Validate FP/TP clocks and weapon matrices against the same snapshot.
- Run targeted JUnit tests and `./gradlew build`. If the task touches client/common separation, also verify a dedicated server starts.

**Native visual review**
- Run `./gradlew :viltrumitecore:runClient` or `./gradlew runClient`. Inspect **first person and third person** at normal speed and in slow motion, including other players on LAN when synchronization matters.
- Check idle→move→attack→recovery, consecutive attacks, stance changes, airborne/crouched/flight, both hands, hit and miss, interrupted action, long sessions, shader shadow pass, F3+T and world unload.
- Inspect from front, side, rear, above and below. Verify silhouette, wrist/elbow twist, two-hand grip, blade clearance, item/FX attachment, hit timing, sound, outline and glow. A still frame is not enough.
- For special shaders also check shader pack fallback, Fabulous, resize, multiple scenes, GL errors and FPS/GPU load. PR #43 The Hand shader still needs native driver and visual checks.

Report checks in three categories: **verified in code/tests**, **verified in running game**, **not verified**. Never call a CPU matrix test, green CI or a rendered diagnostic JSON proof of visual acceptance. If the game cannot run, state the missing acceptance clearly in the PR.

## 9. Recipe: add or rework an animation

1. Read this skill, the relevant reference files, current hero implementation and the hero's design spec. Identify the real animation owner and nearest working reference.
2. Write the motion brief (section 3), including reference timestamps, phase table, contact tick, both views, equipment and cancellation.
3. Reuse the existing synced action fields. Extend only the necessary client pose/geo/VFX seams. Prefer one hero poser/compositor to one mixin per move.
4. Author the base pose and contact frames first. Add anticipation, follow-through, recovery and transitions. Then solve equipment, secondary motion and FP separately on the shared clock.
5. Integrate shared impact FX and sound. Add a specialized effect only if the design requires it and the existing approved pipeline can support it.
6. Add tests for math, clocks, loops, phase handoffs, full-mesh clearance and final constrained joints. Compare with old visuals for regressions.
7. Run the build, then native review. Record screenshots/video observations, limitations and checks. Update this skill/reference only for durable discoveries; ship a focused PR.

## 10. Frequent failure modes

- **"All abilities are 20 ticks."** Legacy-only assumption. Use each action's authoritative timeline.
- **"One mixin per ability."** Legacy implementation detail. Prefer central hero composition when several moves share joints.
- **"The keyframes are smooth, so the motion is smooth."** IK, Euler conversion, equipment and dense-key easing can introduce discontinuities after sampling.
- **"Blend everything toward idle."** Catch, reflect, guard and weapon handoffs must recover to the live parent pose.
- **"The sword grip point matches."** The hand, blade, sheath and elbow can still intersect or flip. Inspect the final meshes.
- **"Third-person pose works in first person."** Camera transforms, hand mapping, held items and view-space projections differ.
- **"The shader compiled, so the effect is correct."** Driver output, render order, shader packs, depth copy, F3+T and performance still need native checks.
- **"CI is green, so the animation is done."** Compilation and pure tests do not establish visual quality.

Keep the reference catalogs current when code changes. Do not grow this entrypoint with a chronological PR diary; add durable implementation details to the relevant reference file instead.
