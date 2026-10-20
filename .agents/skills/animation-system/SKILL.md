---
name: animation-system
description: Use for ALL work on animations, poses, visual effects, screen shaders, particles or animated items/entities in viltrumite-mods. Maps the existing animation and VFX system and gives the only allowed way to add an animation or effect for a new hero or ability.
---

# Animation system (viltrumite-mods)

Read this file before you change or add any animation, pose or visual effect.
The code is the source of truth. If this file and the code disagree, trust the code and update this file.

## 1. Hard rules

1. Do not break current visual effects. Every existing animation, VFX and shader must look the same after your change. Check in `./gradlew runClient`.
2. Use ONLY the systems in this file. Do not add PlayerAnimator, Emotecraft, GeckoLib animation, Veil, a new shader pipeline or any second animation engine. GeckoLib is `compileOnly` for one grab mixin only.
3. Every hero and every active ability MUST have animations: a third-person body pose, a first-person pose, and a visual effect. A hero without animations is not done.
4. Extend the existing classes and patterns. Do not copy a system to make a "better" one.
5. If a new effect needs a capability the system does not have, stop and ask the user before you add it.
6. Ease weights by real time (`System.nanoTime` dt with `1 - exp(-k·dt)`), never with a fixed per-call `Mth.lerp(k, …)`: pose code runs once per render pass (twice with the shader shadow pass) and per frame, so a fixed factor changes speed with the frame rate.

## 2. System map

The mod has several layers. Each layer has one job. Pick the layer by what you animate.

| What you animate | Layer | Where |
|---|---|---|
| Player body pose (third person) | `PlayerModel.setupAnim` TAIL mixins | `viltrumitecore/client/mixin/*ModelMixin*`, `viltrumiteflight/client/mixin/SlowFlyingModelMixin`, `PlayerModelMixin` |
| Whole-body lunge/spin/tilt | `PlayerRenderer.setupRotations` TAIL mixins | `*RendererCoreMixin*`, `SlowFlyingRendererMixin` |
| First-person arms/hands | `FirstPersonXMixin` + `PoseDataManager.FP` | `client/mixin/FirstPerson*Mixin`, flight `ItemInHandRendererMixin` |
| Smooth on/off blend of a pose | Weight managers | `client/render/animation/*AnimationManager`, `SilhouetteManager` |
| Animated items and geo entities | Anim core (Blockbench geo + animation JSON) | `viltrumitecore/client/anim/**`, `InfinityGunRenderer` |
| Whole-body replacement (Hulkbuster Mark 48) | `RenderPlayerEvent.Pre` cancel + `RenderArmEvent` (§7.4) | `client/ironman/hulkbuster/HulkbusterRenderer` |
| World effects (rings, beams, sparks, blood) | VFX managers on `RenderLevelStageEvent` | `client/render/vfx/*VFXManager`, `MeltedTunnelRenderer` |
| Screen effects (shake, blur, tint) | Post shader `dash_impact` | `ViltrumiteShaders`, `GameRendererDashMixin`, `shaders/post/dash_impact.json`, `assets/minecraft/shaders/program/dash_impact.*` |
| Flight screen effects | Post shader `sonic_boom` | flight `GameRendererMixin` |
| Overlay on the player model | `RenderLayer` | `AtmosphericHeatFeatureRenderer` via `PlayerFeatureRendererMixin`; hero layers in `ViltrumiteCoreClient.onAddLayers` |
| 3D parts on player parts (helmet, flames, gadgets) | `PlayerGeoLayer` + provider | `client/anim/render/PlayerGeoLayer`, `PlayerBoneMap` (§7.1) |
| Suit skin growing over the body | Baked reveal frames | `client/anim/render/RevealMask`, `client/ironman/IronManSuitTextures` (§7.2) |
| Particles, entity renderers | Forge registration | `ClientModEvents` |
| Hand position for VFX/grab | Tracker mixins | `HandPositionTrackerMixin`, `ChopHandTrackerMixin` |

Ability state comes from synced player data: `ViltrumiteCorePlayer` (core) and `ViltrumiteFlightPlayer` (flight). All visual layers READ this state. They do not own it.

## 3. Player body poses (third person)

Pattern: one mixin per ability on `PlayerModel.setupAnim(LivingEntity;FFFFF)V` at `@At("TAIL")`.

### 3.1 Order (mixin priority)

| Mixin | Priority |
|---|---|
| `DashModelMixin` | 1100 |
| `PunchModelMixin` | 1150 |
| `ChopModelMixin` | 1160 |
| `ChopModelMixin2`, `ThunderclapModelMixin` | 1170 |
| `BarrageModelMixin` | 1175 |
| `HomelanderModelMixin` | 1180 |
| `IronManModelMixin` (hover, glide, heavy-landing kneel, every Iron Man combat and mark-signature pose, suit walk-in rest pose) | 1190 |
| `GrabModelMixin` | 2000 |
| `BlockModelMixin` | 3000 |

A higher priority applies later and wins. Grab and Block lerp from the pose that earlier mixins left. Give a new ability a free priority that fits this order.

### 3.2 Rules for every pose mixin

1. Act only when the entity implements `ViltrumiteCorePlayer` and the ability is active (synced ticks > 0).
2. Skip when `entity == mc.player` and the camera is first person, EXCEPT when `ShaderCompat.isShadowPass()` is true.
3. Write pose constants in degrees. Convert with `Math.toRadians`.
4. Add position offsets to the base pivots:

| Part | X | Y standing | Y sneaking |
|---|---|---|---|
| Arms | ±5 | 2.0 | 5.2 |
| Body | 0 | 0 | 3.2 |
| Head | 0 | 0 | 4.2 |
| Legs | ±1.9 | 12 | 12 |

5. Mirror left/right with `m = isLeft ? -1 : 1` on yaw, roll and X.
6. Before you pose, set `head.zRot`, `body.yRot`, `body.zRot` to 0.
7. After you pose, ALWAYS call `copyFrom` for `hat`, `jacket`, `rightSleeve`, `leftSleeve`, `rightPants`, `leftPants`. If you skip this, the outer skin layer does not move.
8. Null-check `cloak` before you use it.

### 3.3 Timing

- Abilities use a 20-tick countdown. `t = clamp((20 - (ticks - partialTick)) / 20, 0, 1)`.
- Interpolate with piecewise `Mth.lerp`. Overshoot is `×1.15`.
- Standard 2-keyframe timeline (Punch, Chop):

| t | Segment |
|---|---|
| 0–0.25 | vanilla → S1 (wind-up) |
| 0.25–0.35 | S1 → S2×1.15 (strike) |
| 0.35–0.5 | settle → S2 |
| 0.5–0.65 | hold S2 |
| 0.65–0.9 | S2 → vanilla |

- Thunderclap and Barrage have their own timelines. Read their mixins if you need a multi-hit or charge ability.
- Use `Minecraft.getFrameTime()` for partial ticks in new code. (Old mixins mix `getFrameTime()` and `getPartialTick()`. Do not change them.)

### 3.4 Flight poses

`viltrumiteflight` poses run on the same `setupAnim` TAIL.
- `SlowFlyingRendererMixin` (`setupRotations` TAIL) computes `FlightAnimManager.AnimState.deltaSeconds` and body tilt. `SlowFlyingModelMixin` uses that value.
- Per-player state lives in `FlightAnimManager.AnimState`. Add a new factor field there. Move it with `±rate * deltaSeconds`. Blend with smoothstep `x²(3-2x)`.
- GUI frames must not advance state (`deltaSeconds = 0`).
- A core ability that runs in flight must look correct in BOTH grounded and flying states. Chop and Barrage use additive leg blending when grounded. Copy that pattern.

## 4. Whole-body motion

Use a `PlayerRenderer.setupRotations` TAIL mixin (`PunchRendererCoreMixin` 1500, `ChopRendererCoreMixin` 1510, `BarrageRendererCoreMixin` 1515, `ChopRendererCoreMixin2` 1520).
- Translate to a pivot Y, rotate, translate back.
- Lunge curve for strikes: 0 → -0.3 (t 0–0.25) → 1 (–0.35) → hold (–0.65) → 0 (–0.85).
- Existing lunges apply only in flight (`getFlightState() != NONE`).

## 5. First person

- Each ability has a `FirstPersonXMixin` (Barrage, Block, Chop, Chop2, Grab, Gun, Punch, Thunderclap; Homelander; Iron Man: `IronManFirstPersonShieldMixin` → `IronManFirstPerson` for every combat pose and the shield guard). Read the nearest one before you write a new one. Copy its structure.
- Iron Man (Regulus-style, PR #19): `client/ironman/anim/IronManAnimation` is the one client clock per player (shot edges per palm, stance hold, volleys, strike clock with forehand/backhand, nano weapon form/dissolve, missiles, unibeam, signatures); `State.weight(View, Layer, active)` gives separate third/first-person weights. `IronManPoser` keys layers on `PoseRig.begin(...).slerp()` and then aims arms with `client/anim/pose/ArmAim` (direction from the head axes, rotation-to quaternion → Euler ZYX); `IronManFirstPerson` uses `FirstPersonArm` (shoulder pivot) with the same layers. Do not add a separate signature mixin: signatures are a layer in these two classes.
- An arm that is not the main hand renders in first person only through `AlwaysVisibleOffhandMixin`; a hero adds its `wantsOffhand(player)` there.
- Flight applies `PoseDataManager.FP` in `ItemInHandRendererMixin` and resets arm pivots in `PlayerRendererMixin`. Do not fight these transforms.
- Blocking uses `FirstPersonBlockAnimationManager`, a separate weight manager. A pose that renders in both views needs a separate first-person weight.

## 6. Weight managers

Use one when a pose turns on and off by a boolean (block, grab, gun).
- Copy `GrabAnimationManager`: `static WeakHashMap<LivingEntity, State>`, `calculateWeight(entity, active)`.
- Wall-clock delta, clamped to 0.1 s. `weight = lerp(1 - exp(-k*dt), weight, target)`.
- k ≈ 10 soft fade, 12–15 body pose, 25 snappy weapon pose.
- Call it ONCE per frame per entity per view. The call also advances the state.
- The weight starts at 0 and never reaches exactly 1. Compare with an epsilon.
- Use from the render thread only.

## 7. Anim core (geo items and entities)

Use for Blockbench models: items, projectiles, summons, gadgets. Do not use it for the player body.

Assets:
- `assets/<ns>/geo/**.geo.json` (format `minecraft:geometry`, 1.12.0).
- `assets/<ns>/animations/**.animation.json` (format 1.8.0). Times are in seconds.
- `AnimCache` loads all namespaces on resource reload. Item helpers: `itemGeo(name)`, `itemAnimations(name)`, `itemTexture(name)`.

API:
- `AnimCache.model(loc)` returns null if missing. `AnimCache.animation(loc, name)`.
- `new AnimationController("main").transitionLength(ticks).onEvent(e -> ...)`.
- `play(anim)` does not restart the same name. `restart(anim)` always restarts.
- Per frame, in this order: `controller.play(anim)` → `controller.apply(model, AnimRenderer.time(pt))` → `AnimRenderer.render(..., boneHook)`.
- Stateless timeline form (no start clock, time comes from synced fields): `AnimationController.seek(anim, model, seconds)` resets and poses one clip; `overlay(anim, model, seconds)` poses only the bones the clip animates; `blend(model, a, sa, b, sb, weight)` lerps two clips per bone.
- `BoneHook` runs for every bone, also for hidden bones. Use it to attach effects or vanilla arms.

JSON support:
- `loop`: `true` → LOOP, `"hold_on_last_frame"`, otherwise PLAY_ONCE. Use `hold_on_last_frame` for one-shot actions.
- Keyframes: arrays, numbers, `{pre, post, easing, lerp_mode: "catmullrom"}`.
- Easings: `linear`, `step`, `easeIn/Out/InOut` + `Sine|Quad|Cubic|Quart|Quint|Expo|Circ|Back|Bounce`. Elastic: in and out only.
- Events: `sound_effects`, `particle_effects`, `timeline` → `Event.Type` SOUND, PARTICLE, INSTRUCTION.
- NO Molang. A Molang string becomes the default value and logs a warning. Bake numbers in Blockbench.
- Animation bone names must equal geo bone names. A missing bone is skipped without an error.

Invariants:
- One `BakedGeoModel` instance is shared per resource. Call `apply` directly before `render`.
- Do not keep model or animation references across a resource reload.
- `AnimRenderer.time` is wall clock × 20. It runs during pause.

Template: `InfinityGunRenderer` (BEWLR). It selects `shoot` / `reload` / `idle` from NBT tick timers and hides the `right_arm` / `left_arm` bones to draw vanilla arms in first person.

### 7.1 Geo parts on player parts (`PlayerGeoLayer`)

Approved extension (Iron Man stage 1b): Blockbench geo drawn ON the player
model, following every pose. Use it for armor pieces, flames, gadgets. The
body itself stays the player model with a skin.

- Register once on the client: `PlayerGeoLayer.register(provider)`. Each
  frame `Provider.collect(player, pt, firstPerson, out)` adds
  `Part(geoLocation, passes, pose)`.
- Top-level bone names map to model parts with `PlayerBoneMap`
  (`armorHead/Body/RightArm/LeftArm/RightLeg/LeftLeg`, plain names, GeckoLib
  `armor*Boot` → legs). Children follow. An unknown top bone with cubes is
  skipped with one warning. Write geo in Bedrock armor coordinates (feet at
  y 0, vanilla pivots: arms ±5/22, legs ±1.9/12).
- The layer applies the part's final pose (`translateAndRotate`) after all
  `setupAnim` TAIL mixins, then the bone's own pivot/rotation/scale.
- `pose` runs after `resetBones()` on the shared model: set `scaleX/Y/Z`,
  rotations, `hidden` there (flame length, flicker). Put a child bone pivot at
  the flame root so `scaleY` changes the length.
- Passes: `Pass.cutout(tex)` (lit, `entityCutoutNoCull`) and
  `Pass.glow(tex[, r, g, b])` (`eyes`, full bright, skipped in the shadow
  pass). Glow textures must be black where nothing glows (premultiply alpha).
- First person: `PlayerGeoHandMixin` (`renderHand` TAIL) draws the arm bones
  on the first-person arm (`firstPerson = true` in `collect`).
- Layer registration: `ViltrumiteCoreClient.onAddLayers` (both skins).

### 7.2 Suit skin reveal (baked frames)

Decision record: `docs/spikes/2026-10-ironman-reveal.md`. No custom shader
(Oculus safe).

- `RevealMask` gives every skin texel a distance 0..1 from an origin on the
  body surface; head texels come last. `frame(progress)` → 0..16.
- A client reload listener bakes 16 `DynamicTexture` frames (skin / cut /
  glow / rim) from two skins and a glow map; see `IronManSuitTextures`.
- The `skin` frame is returned by `HeroSkins.Provider.skinVariant`, so the
  model and the first-person arm use it. Glow and rim are an `eyes` layer
  on the parent model with head + hat hidden (`IronManSkinLayer`). A helmet
  is a geo part drawn with the `cut` frame.
- Progress must come from synced timeline fields (`actionElapsed/Length`),
  never from a client counter: relog mid-wave stays correct.

### 7.4 Whole-body replacement (Hulkbuster, Reinhard): allowed only through this renderer pattern

Iron Man stage 5 replaces the whole player body with the Mark 48 model. Only this pattern may do that:

1. Use Forge event hooks, not a model mixin. `RenderPlayerEvent.Pre` draws the big body and cancels the vanilla render while ACTIVE. `RenderArmEvent` draws the two big arms and cancels the vanilla arm in first person. Both are in `client/ironman/hulkbuster/HulkbusterRenderer`.
2. Pose = `AnimationController.blend` (idle or walk, blended by limb swing speed) + `AnimationController.overlay` (one clip for the action). Clip time comes from synced fields (`actionElapsed`, `actionLength`). Loops use wall time. The walk uses walk distance. Never use a client counter for progress.
3. Geo sits in the armor space of §7.1: feet at y 0, JSON x = vanilla x, pixels. Draw it in the entity frame: `mulPose(YP(180 - bodyYaw))`, then `scale(1.7)`. Do not flip. The entity frame already matches the armor space.
4. Cancelling the vanilla render also hides its layers, skin and mark pieces. EXITING does not cancel. Tony draws at normal size and steps back. The step is a translation applied in the `RenderPlayerEvent.Pre` stack before vanilla rotates it, so it is in the entity frame.
5. Locked docking parts use the `PlayerGeoLayer.Provider` `HulkbusterDocking` (the armor bones follow every pose). Flying parts draw in the world (`HulkbusterFlight`, `PartFlight`, AFTER_ENTITIES).
6. Glow is a `RenderType.eyes` pass, skipped in the shadow pass. Thruster flames are glow-only: their cutout texels are clear, and the bone scale is zero at rest.
7. `GeoBone.resetToDefault` does not reset `hidden`. A pose that hides bones must set `hidden` every frame.
8. Hitbox and eye height come from the body scale seam (`HeroDefinition.bodyScale`). This renderer does not change them.
9. No pose mixin was added for the Hulkbuster, so the priority table in §3.1 is unchanged.

Second user — Reinhard (stage 1b, `client/reinhard/ReinhardRenderer`), always on for the hero:
- Cancel the vanilla render for every Reinhard render, invisible included; otherwise a glowing invisible player outlines as a vanilla Steve. Passes come from `ReinhardPoses.passes(invisible, visibleToViewer, glowing)`: visible → body + glow + sword + held items (a glowing body outlines by itself because the event's buffer source is the outline source); invisible but visible to the viewer → body `entityTranslucent` alpha 0.15 only; invisible + glowing → `RenderType.outline(texture)` only. Outline and glow skipped in the shadow pass.
- Cancelling also skips what vanilla does after the model, so restore it: Forge's name tag step (fire `RenderNameTagEvent`, then vanilla `renderNameTag` / `shouldShowName` through `EntityRendererNameTagInvoker`), held items (`ItemInHandRenderer.renderItem` at the geo hand bone: `HeldItemAnchor.local(arm)` = rotZ 180 + vanilla `ItemInHandLayer` chain), hurt tint (`LivingEntityRenderer.getOverlayCoords`), player scale 0.9375.
- Pose clips must cover crouch/sit/sleep/swim/elytra because model mixins never run: `setupRotations` mirrors vanilla (bed, death tilt, elytra pitch + roll, swim pitch); `crouch` / `sit` are additive clips with vanilla's numbers (`AnimationController.additive`); `swim` is vanilla's 26-unit crawl stroke, overlay weighted by `getSwimAmount`.
- Layering: stance (`StanceBlend`, k 12, one per mode) → walk as a weighted `overlay` on the limbs (keeps the stance's head/body; `blend` would reset them) → Knight arm hold `overlay` by the Knight weight → additive body state → action overlay (clip = HeroAction name in lower case, missing = skipped).
- First person: `RenderArmEvent` → `PlayerGeoLayer.renderArmPart(ARMS)` with pose null (rest = vanilla's first-person arm); a stance-hidden arm is cancelled and not drawn; the empty off hand goes through `AlwaysVisibleOffhandMixin` + `ReinhardFirstPerson.wantsOffhand`.
- Stage 2 parry: `ParryPulse` (real-time weight, `K_IN` 40 snap in while the synced `ReinhardFlags.PARRY_POSE` bit is set, `K_OUT` 10 out; clip time restarts on each rise; separate `THIRD_PERSON` / `FIRST_PERSON` instances) drives the `parry` clip as an overlay after the action overlay (hold on the last frame). First person: no clip and no mixin — `onRenderArm` rotates the main-arm `ModelPart` by the flick angles × the pulse weight (right arm; mirrored for the left). The parry clip itself is right-arm only (left-handed third person is not mirrored).
- Stage 2 world VFX (pixel style, §8): `PhoenixFlameVfx` (state-driven gold-red flames after a revive, no new `HeroFxS2CPacket` kind), `AirStepVfx` (ring under the feet on an air jump, own view + observers via `AirJumpSyncS2CPacket`), mana motes in `ReinhardAuraVfx` (blue motes from drained players, from `OwnerSection.MANA_LINKS`).
- Stage 3 action clips (overlay over the stance, `ReinhardClips` holds the names, `tools/assets/make_reinhard_clips.py` generates them): Restrained `light_punch`, `step_dash`, `catch_ready` → `catch` (phase `PHASE_SNAP`); Knight `combo_1`/`combo_2`/`combo_3` (phase = hit), `kick_toss` → `kick` (from `KICK_TOSS_TICKS`), `grab_hold` (held) → `throw` (snap), `dash_behind`, `stomp`, `reflect_ready` → `reflect` (snap). Knight clips never touch the left arm (it stays behind the back). Each clip covers the action it plays over (`ReinhardClipsTest.everyAbilityHasClip`). First person: `ReinhardFirstPersonPose.of(ability, phase, ticks, length)` offsets the main arm (no clip). Heavy contacts (Sky Kick, throw) use the shared client `HitStop` (`HeroFx.hitStop`): the target freezes on screen and the renderer can hold its clip clock with `heldTicks`; the sonic ring / air trail are `AirTrailVfx`.
- Stage 4 (Holy Sword): `reid_draw` (6 s) / `reid_sheathe` (1 s) are driven by the synced Reid phase (`ReinhardPoses.reidClip`, clock `ReidClock`), not by the action timeline; `idle_holy_drawn` loops over the Holy Sword stance while Reid is out; `chop_left` / `chop_right` play for the kit chop (Lethal Chop, synced chop side); `sheath_strike` (0.6 s) and `cut_rain` (1 s) are ordinary action overlays. Reid in the hand: `ReinhardSword.drawInHand` in vanilla's held-item frame (`HeldItemAnchor`), the slot item of that hand is not drawn while Reid is out (first person: `RenderHandEvent` cancel + `ItemInHandRendererInvoker`). First-person arm offsets for the new actions live in `ReinhardFirstPersonPose`.
- Art from OBJ: `tools/obj2geo.py` (rotated boxes → cube `rotation`/`pivot`; `--skip-material-suffix _glow` for emissive overlay copies; `--uv-top-origin` when vt is measured from the top). Note: `GeoBone.resetToDefault` resets `hidden` now (item 7 is kept for old code paths).

Art: Sind Hulkbuster from the user's archive, `tools/assets/convert_ironman_sind.py` (geo via `tools/tabula2geo.py`, clips baked from the Sind `.fsk` scripts via `tools/fsk2anim.py`). The model is 68 px tall: the renderer scale is `HulkbusterLayer.SCALE × 32/68`. The jackhammer arm is a separate geo with its own texture; it replaces the body bone `pivotLeft` while the jackhammer tool is selected. Thruster fire is a separate geo posed with the same clips and drawn as glow. Tests: `HulkbusterAssetsParseTest` (parse, bones, clip lengths), `HulkbusterPosesTest`, `HulkbusterAssemblyTest`.

## 8. World VFX

Pattern: a `@EventBusSubscriber(value = Dist.CLIENT, bus = FORGE)` class that renders in `RenderLevelStageEvent` at `AFTER_LEVEL` or `AFTER_PARTICLES`.
- Geometry: `Tesselator`, `QUADS`, `POSITION_COLOR`, position-color shader.
- Usual blend: additive (`SRC_ALPHA, ONE`), cull off, depth test on, `depthMask(false)`.
- Camera space: push the model-view stack, `XP(camXRot)`, `YP(camYRot + 180)`, translate `worldPos - cameraPos`.
- Style: the "paint pixel" ring (`drawPaintPixel`) and billboard crosses. New effects MUST use this pixel style to match the mod.
- ALWAYS restore GL state: `depthMask(true)`, `enableCull`, `defaultBlendFunc`, `disableBlend`, pop the stack.

Two driver types:
- One-shot: static list + `spawnX(pos, ...)` (`BlockVFXManager`, `MeteorImpactVFXManager`).
- State-driven: read synced ticks every frame (`PunchVFXManager`, `ChopSweepVFXManager`), or detect a tick threshold in `ClientTickEvent` (`ThunderclapVFXManager`).

For new effects, age by ticks with partialTick (Thunderclap pattern). Do not use per-frame `age++`: it depends on FPS.

### Regulus pose and world-space helpers

- Use `RegulusAnimationManager.actionTime(entity, snapshot, partialTick)` in both views. It uses client game time and limits extrapolation to six ticks and the action length. Do not use raw snapshot ticks for one view and the shared clock for the other.
- Use `calculateWeight` for third person. Use `calculateFirstPersonWeight` for first person. For a held-book pose, pass the fourth `mainHand` argument so each hand has its own weight. Tick inactive weights too, so they fade out.
- Call `RegulusAnimationManager.reset(entity)` when the hero changes. It clears third-person weights, both first-person hands, and the action clock.
- `RegulusPoseTiming.keyedAngle` recovers to the supplied base rotation. End transient keys at zero. Add head/body offsets to vanilla aim and crouch. Mirror action limbs and yaw/roll for the main arm. Restore position offsets at `setupAnim` HEAD before vanilla uses the model again. Copy hat, jacket, sleeves and pants; do not copy body pivots to the cloak.
- `PixelVfx` emits camera-relative vertices. Call `rotateCamera` once per render pass. Do not translate by the camera again. Use `billboardPixel`, `domeShell` or `sphereShell` for floating points. Shell shimmer changes alpha, not position. Apply owner-only carrier filtering and local first-person aura filtering in the managers, not in the shape helper.

### Shared hero impact FX

- Server code sends impact FX with `hero/fx/HeroFx` (`shards`, `shockwave`, `slam`, `blade`, `launch`, `flash`). It sends `HeroFxS2CPacket`; `client/render/vfx/HeroImpactFx` draws it for every hero. Do not add a hero-specific packet for these shapes.
- Camera shake goes through `client/render/vfx/CameraShake` (`addAt` with distance falloff, `add` for local). Do not add a second shake accumulator.
- Full API list: `.agents/skills/add_hero/references/shared-toolkit.md`.

### 7.3 Mark skins and pieces (Iron Man stage 4)

- Whole mark on (all parts present): the body skin is the mark texture
  `textures/entity/hero/ironman_<mark key>.png`. Helmet open uses a baked copy
  with Tony's head (`MarkSkins`, reload listener, 64x64 layout).
- Partial, equipping, exiting, or Mark 42 with lost parts: Tony's skin plus one
  archive piece per `SuitPart` (`MarkParts.geo/passes`): Satsu `each_part`
  pieces (`geo/ironman/marks/parts/`) with the mark skin, Mark 42 Sind pieces
  (`Mark42Parts`, `geo/ironman/mark_42/`) with the Sind atlas. Drawn with
  `PlayerGeoLayer` in both views; in the world with `MarkParts.draw`.
- A whole Satsu mark also draws its own Satsu parts (`MarkExtras`: Mark 7 flaps,
  Mark 17 chest, War Machine pads, Iron Heart plates) with the raw suit texture.
- Piece geo has one top-level armor bone pivoted at the piece centre.
  `PartWrapAnimator.pose` scales that bone about the centre. Clear `resetBones()`
  before a direct draw of a shared piece model.
- Glow: `ironman_<key>_glow.png` as `Pass.glow`. Click flash = an extra glow pass of the skin.

- Screen-wide mood (Reinhard stage 4, hero-neutral): `client/render/vfx/SkyTint.INSTANCE.push(owner, priority, () -> Tint)` / `remove(owner)` — sky, fog and cloud colour multiplied, `darken`, rain level scaled (`SkyTintMixin`, `LevelRainMixin`, client mixin config only, early return off the client); the highest priority wins, eased in and out. `SoundDuck.INSTANCE.duck(owner, category, target, fadeTicks, exemptIds)` / `release(owner)` — lowers every other sound of a category (`SoundEngineVolumeMixin`), the caller's own sound ids are exempt (no hero names inside `SoundDuck`). Oculus shader packs draw their own sky: the tint may show only through fog (not checked).
- Stun stars (`client/reinhard/SheathStunStars`): white pixel stars over every target of a synced FREEZE control whose caster is a Reinhard (`ClientHeroData.controls()`), no new packet.

### 8.1 Flying parts and wrap

- Flying parts: `veronica/PartFlightVisuals` (`AFTER_ENTITIES`). Position =
  `PartFlight.position(launch, live anchor, progress)`. The anchor follows the
  moving player's body yaw. Tumble decays to 0, so the part meets the body
  pose exactly. Parts wait in the pod (no draw) until their launch tick.
- Wrap: after the flight, `MarkVisuals.collectParts` draws the same piece on the
  body (`PartWrapAnimator`: open, close, click flash). Locked parts stay as pieces.
- Sounds are local clips. `PartFlightVisuals.onClientTick` plays them at the synced launch and lock ticks.

### 8.2 Whole-entity renderers (pod, empty suit, debris)

- `VeronicaPodRenderer`: `veronica_pod.geo.json` with `open`/`close` clips
  (`AnimationController.restart` on each phase change). Doors swing open after landing.
- `EmptySuitRenderer`: opening shell (Satsu `full_body` + helmet cut into a back
  half and hinged front plates by `tools/assets/opening_shell.py`, mark skin),
  interior lining (`ironman_interior.png` 128 px grayscale + glow, tinted per mark with
  `SuitPalette.lining`), `open` (30 ticks) clip on the door bones. Entering is
  code-driven (`EmptySuitEntity.enterDoor`): plates open while the owner walks up,
  close legs → arms → chest → faceplate (4 t each, no overshoot); the shell hides
  once the owner's snapshot shows the mark on; the helmet box hides for the local
  first-person owner. The walk (approach, turn, step back) is scripted on the client
  by `SuitEntryDriver`, which also gives `restWeight` for `IronManPoser`. The old
  `enter` clip is unused. Mark extras only while standing closed. Body frame: `mulPose(YP(180 - yRot))`, scale 0.9375.
- `VeronicaPodRenderer`: Veronica after the Age of Ultron stills (twin hulls are
  the door bones), cutout + glow pass.
- `SuitDebrisRenderer`: one suit piece, tumbling, fades and shrinks in the last 20 ticks.
- Forearm micro-missile pods (`geo/ironman/missiles/forearm_launchers.geo.json` + `forearm_rockets`, own work, `tools/assets/make_ironman_forearm_launchers.py`): grayscale textures tinted with `SuitPalette` via `PlayerGeoLayer.Pass.cutout(tex, r, g, b)`; pods slide out of the forearm, petals open. Launch point `IronManCombat.forearm(player, right)`.
- War Machine rounds: `client/ironman/mark/sig/GunRounds` (travelling streaks, muzzle flash, whiz for bystanders, block/body impacts), drawn from `SignatureVfx`.
- Helmet open (iteration 3): only the iron faceplate opens, the rest of the helmet stays. Nano: `IronManSuitTextures` `cutOpen/glowOpen` (frames without the head front and hat front) plus `face(kind, step)` dissolve steps (8; nanites recede from the middle of the face to the edges, cyan rim). Marks: `MarkSkins` opens only the face region and `MarkFaceplate` (`geo/ironman/marks/faceplate.geo.json`) slides the plate up into the crown with a small outward tilt. `HelmetAnim.gesture` is the hand-to-helmet clock (22 t, smooth, only when the suit stays on — none on exit or retract).
- `MarkVfx`: pixel motes (fire trail, engine flames, sparks, dust ring) and a
  heat aura (`PixelVfx.bodyShell`). Motes age by client ticks and clear on logout.
- Animation sign convention: JSON X and Y are negated like `buildBone`, Z is not.
  Parsed-frame checks: door `rotY` -75 turns the +x door edge toward +z.
  Arm `rotZ` +40 on the +x arm is outward. Head `rotX` +35 tilts the top toward +z. Verify in game.
  For a geo hinge rotated about X (front = -z): a positive bone `rotX` (clip X negative) swings the lower edge forward and up. The empty-suit faceplate (hinge at the top centre) opens with clip X = -105; +105 swung it back through the helmet (fixed in PR #19 iteration 3).

## 9. Screen effects

Core shader: `viltrumitecore:shaders/post/dash_impact.json`, set in `GameRendererDashMixin` (`renderLevel` TAIL).
Current uniforms: `DashIntensity, PunchIntensity, ShakeIntensity, GrabIntensity, LockIntensity, Time, ChopIntensity, ChopType, ChopIsLeft, BarrageIntensity, BarrageIsLeft, ScourgeIntensity, ScourgeTurn`.

To add a screen effect:
1. Declare the uniform in `program/dash_impact.json` AND in the pass `uniforms` of `post/dash_impact.json`.
2. Clamp it in `dash_impact.fsh`.
3. Add it to the early-out check. If you do not, the effect never shows alone.
4. Colour effects are an exclusive chain: chop > barrage > dash/punch. Put a new one in the chain or as an overlay after it, like lock.
5. Compute the intensity in `GameRendererDashMixin`. Use distance falloff for other players.
6. Do not create a second post chain. Flight `sonic_boom` already replaces other post effects when it loads.

## 10. Recipe: new ability animation

1. Add synced state to `ViltrumiteCorePlayer` (20-tick countdown + side flag if needed).
2. Third-person pose: new `XModelMixin` (sections 3.1–3.3).
3. Whole-body motion if the move needs it: `XRendererCoreMixin` (section 4).
4. First-person pose: `FirstPersonXMixin` (section 5).
5. World effect: state-driven VFX manager (section 8).
6. Screen effect if the hit is heavy: new uniform (section 9).
7. Register every mixin in `viltrumitecore.client.mixins.json`. `defaultRequire` is 1, so a bad target crashes the game.
8. Test in `./gradlew runClient`: third person, first person, grounded, flying, sneaking, left and right side, shaders on (shadow pass). Check that old abilities look the same.

## 11. Known pitfalls

- `PlayerModelMixin` (flight) and `SlowFlyingModelMixin` inject at the same TAIL without priority. Do not depend on their order.
- `ChopRendererCoreMixin` does not check chop type, so it also runs for type 1.
- Many VFX lists are plain `ArrayList` and are not cleared on world change. Touch them only on the render thread.
- `MeteorImpactVFXManager` removes impacts at 600 ms, so its 2500 ms shake window is cut.
- `tickGrab` in `PlayerEntityCoreMixin` was rewritten by hand after decompile and is not confirmed in-game. Compare grab behaviour with the original JAR.
## Reinhard Stage 5 timed World Cut
- DRAWN idle applies before the action overlay; draw/sheathe still apply after it. This prevents idle_holy_drawn from erasing the sword-arm action. During World Cut, RenderHandEvent routes both physical arms through the empty-arm/RenderArmEvent path even when a slot holds an item; slot contents are unchanged and item visuals return afterward.
- `world_cut` is a 4 s action overlay: the weapon grip rises outside the head during the first second, then holds low through the silence. `ReinhardFirstPersonPose.worldCut` applies to both arms in RenderArmEvent. No new pose mixin or animation engine.
- `WorldCutScene` receives cast UUID, caster UUID, fixed yaw/origin, surface profile and start game time; entity disappearance cannot stop it. White ground/sky billboards, dust, edge cracks, white-gold front and haze use PixelVfx. Skip the shader shadow pass and restore render state.
- `WorldCutFov` reads a tick-refreshed array without allocating in ComputeFov; primitive finite-line distance, real-time exponential easing. Each scene excludes its own caster. SoundDuck owner is the cast UUID; release on blast, timeout and level unload.
- `HeroImpactFx.shockwaveVisual` adds only the existing rings. It deliberately does not add shake/dust; the scene excludes caster blast shake and uses the shared CameraShake for other viewers. Check both views, a LAN viewer at 30/150 blocks and shaders manually.

### GUI paint-pixels and model silhouettes (Stage 6)
- `PixelVfx.guiPixel` reuses the paint-pixel style through the existing GuiGraphics batching; no extra VFX engine or render target.
- `GuiEntitySilhouette` delegates to the real entity renderer with FULL_BRIGHT, but feeds NEW_ENTITY model streams into white POSITION_COLOR GUI quads. Font/leash/fire/glint streams are discarded; camera/shadow/lighting/pose state is restored in finally. Texture alpha is intentionally not part of this flat mesh silhouette.
- `SoundDuck.release(owner, ticks)` allows an independent out-fade; existing `release(owner)` callers keep their old symmetric step.

- `FlashOverlay.flashEnvelope(strength, inTicks, outTicks)` extends the SAME white overlay with an attack/release curve; old `flash`/`alpha` callers retain their original linear out-fade.
- Limbo heartbeat uses non-positional tickable vanilla-sound pulses in a 24-client-tick logical loop (50 bpm), not a sample-length-dependent SoundEngine delay. END/dimension leave/logout stop its owned instance.

## Reinhard Stage 6 World Erasure
- Existing `dash_impact` program/post passes add **EraseCrack, EraseShatter, EraseDark, EraseRebuild, EraseSeed**, all default 0 and updated every processed pass. The old zero-uniform early-out keeps the idle path unchanged. No second post chain or animation/VFX engine.
- Pure timeline: 0–30 crack; 30–70 six seeded Voronoi shards fall; 70–110 black world; 110–150 shards return with cubic ease-out and darkness fades over 15 ticks. During the scene, cinematic colour overrides competing **core** effects only. Core's existing chain runs independently at renderLevel TAIL; flight's vanilla `sonic` post effect could run afterward, so core stops an already-loaded sonic chain and refuses only sonic loads while erasure is active. Shader-pack ordering is still a manual check.
- Packet age plus the local tick clock drives every dimension and join catch-up. Same UUID rebases monotonically; it doesn't rewind or replay old UI sounds. Resource reload, unload, logout, pause END and watchdog clear owned state; authoritative destination catch-up can resume the remaining scene, including in limbo. At 30 duck MASTER to 0 over 6 ticks; at 110 inhale and independent 40-tick release. Early exits release in 6 ticks.
- `world_erasure` is one 7.5 s HOLD clip: Reid rises before the face, held to tick 30, one cut down/across by tick 40, then hold. DRAWN idle applies **before** the action overlay. First-person motion uses `ReinhardFirstPersonPose.worldErasure` on both physical arms; both views use the shared cinematic item policy, hiding occupied slot items without changing inventory (also fixes World Cut third-person item routing).
- `ErasureScene` paints a FULL_BRIGHT real-model white silhouette during 70–110 when the entity resolves in the viewer's dimension; otherwise the metadata humanoid/rounded-box fallback is centred at 40% GUI height. Geometry material omits texture cutout alpha. Cached metadata-shaped paint-pixels approximate dust, not GPU silhouette readback. Frozen `erase=false` reforms the silhouette at 95–100.
- Limbo is a HUD layer, not a Screen: chat/pause stay available, hands and gameplay HUD are hidden. Text uses vanilla F1 visibility; server remains authoritative if the client text reaches its last line early. Existing FlashOverlay supplies the return's 10-in/30-out white envelope after the overworld loads.
- Do not claim rendered acceptance from source/codec tests: dedicated startup/common Mixin targets, GPU GLSL compilation, first/third person, cross-dimension catch-up, F3+T and Oculus/shader packs remain manual (not launched in this stage).

### Reinhard quality rework (candidate, native acceptance pending)
- Stateless `AnimationController.seek/overlay/blend` do **not** wrap LOOP time. Reinhard uses `ReinhardPlayback.time` at every looping layer. Do not change the shared controller's semantics to fix one hero; verify long-session/multi-cycle samples in the actual renderer composition.
- Body/FP/cinematic action time uses `ReinhardPlayback.actionTicks`: synced elapsed + partial tick, clamped to action length. Reinhard passes zero external HitStop subtraction: its authored impact holds already supply the pause, and the shared hold counter returns zero on release (subtracting it creates a forward time jump). This is a sampling policy, not an independent progress counter. ReinhardRecovery blends cached displayed channels after cancellation; it never advances action progress. Body, FP arms/offsets and weapon frames have separate weak-key state.
- The archive sleeve/hand cubes are articulated by `tools/assets/rig_reinhard.py` into forearm children with absolute Bedrock elbow pivots. Preserve the cubes and UVs. The third-person item frame must pass through `ReinhardRenderer.handFrame`, not only the shoulder, or items detach when the elbow bends. First-person widths and elbow channels are applied through the existing PlayerGeoLayer pose callback. The FP item frame repeats PlayerGeoLayer's vanilla-to-geo conversion before handFrame. Neutral and bent-elbow matrix tests cover it.
- FP action phases use the existing `PoseKeys` utility. Keep server contact ticks and the toss → kick boundary continuous. Catch/reflect/throw continue an existing guard/hold: do not fade to idle on the snap's first frame.
- Sheath Strike renders the assembled sheathed weapon at the right hand in both views, hiding the hip copy and the occupied right-hand slot visual only. Inventory stays untouched. ReinhardSword.frames transfers the hip guard to the hand with quaternion slerp and translation/scale interpolation. Do not lerp matrix elements: that shears the weapon and invalidates normals. Native visual acceptance remains unverified.

- A held-target renderer must not infer attachments from PULL. HeroDefinition exposes
  heldTargetId/heldTargetPosition; HeroHeldTargets resolves only an explicit hold.
  Local prediction and remote render interpolation use the same neck anchor.
  The server rejects held-player movement on its main thread. Correct once at
  release, not with a teleport handshake each tick. World pause has priority.
- HeroFx.flash(source, at, power) opts into a compact/strong contact. The two-argument
  method stays power 1, preserving other heroes. Erasure fracture changes stay inside
  the existing Erase active branch and keep the other shader paths neutral.


### Reinhard second-pass composition and measured grip
- Keep AnimationController unchanged for other heroes. ReinhardLayers samples it,
  restores absent position/scale/rotation channels, and quaternion-slerps only
  owned rotations at partial weight. Weight 1 keeps the exact authored Euler pose.
  Missing channels do not own a base offset. Pose state samples use tick time;
  render weight filters advance once for a timestamp, not once per outline pass.
- The body pivots at waist Y12; head and shoulders inherit it. Apply crouch once
  as relative rotations, after combat layers, without independent root offsets.
  Forearms/shins retain archive cube geometry and UVs. Parent-aware attachment
  frames must undo the parent's absolute pivot before applying a child frame.
- Recovery is outgoing to idle, not a generic incoming low-pass over contacts or
  phase changes. Keep all three stance weights when interrupted during a blend.
- A grip-point equality is not proof of a natural hand or a clear blade. Measure
  the native glove, test full weapon cubes, and inspect intermediate frames.
  A resting palm is a surface contact, not a wielding pose. Keep its wrist
  neutral; do not align it to the sheath's roll. Limit active wrist deflection.
- TP Reid uses an empty reid_grip child of the body in the existing animation
  JSON. Sample it once, solve both arms, then draw the SAME equipment matrices.
  Left hand rests on the sheathed hilt, draws outside the head, hands off in front
  of the chest. The drawn ready/action poses use separate guard and pommel
  targets for both palms. A two-hand grip uses forearm pronation about the
  elbow-to-palm axis, not a 90-degree wrist flip. The sheathed resting wrist
  remains neutral. Camera and TP sword bases differ. The owner later selected the diagnostic
  +90-degree TP free-blade roll: front edge, side face. Apply it after the
  unchanged grip solve; never rotate the sheath, stance or sheathed blade.
  Do not recompute the weapon from the constrained arm or bind unreachable
  extraction paths by moving the weapon afterward. Keep the six-second event.
- Use a visible belt carrier outside the coat, not a floating attachment. Weapon
  thickness faces out. Its reduced length must clear ground at the waist mount.
- Check quaternion-to-RzRyRx round trips. In bundled JOML 1.10.5, the X component
  from getEulerAnglesZYX does not invert compound rotationZYX. Reinhard's local
  matrix decomposition avoids that drift; do not change other heroes blindly.
- FP elbow clips and camera shoulder offsets stay separate. Do not apply the TP
  shoulder clip twice. Camera Reid uses the measured palm and a forward geo axis
  before the existing vanilla-to-geo conversion. Native FP acceptance is pending.
- Optional native-matrix CPU export: REINHARD_DIAGNOSTICS=1 and
  ReinhardDiagnosticTest writes build/diagnostics/reinhard/*.json. These are
  diagnostic posed vertices, NOT Minecraft animation/VFX/network acceptance.


### Detached cinematic weapon projections
- Sample the existing AnimCache clip and the same body/grip ancestry as the real
  weapon. Clone sampled GeoBone state; do not mutate the shared cached skeleton
  from a world-render callback. Compare sampled matrices with AnimationController.
- Scale a giant weapon projection about its grip. Keep the physical weapon scale,
  sheath frame and hand solver unchanged. CPU matrix checks do not prove GL output.
- Keep a long terrain scene alive until the server queue reports DONE. Use bounded
  progress heartbeats and a client watchdog, not a hard-coded visual end tick.
  Fade atmosphere leases after DONE. Release them on unload and watchdog expiry.
- CameraShake.cinematicAt is an explicit strong contribution. Ordinary add/addAt
  retain the legacy 2.5 input cap. They must not truncate an active cinematic shake.
  A local intensity setting scales the cinematic contribution; shared decay remains.

### Reinhard continuity regression gates
- Walk/run bake the original periodic trajectories at 80 linear intervals per
  cycle. Never apply ease-in/out separately between dense gait samples: that
  stops the limbs at each intermediate key. Test sampled velocity and position,
  not only loop flags or the first pose. Keep combat contact keys unchanged.
- Catch/reflect snaps overlay their matching ready guard. Fade a snap back to
  guard, not idle. The window envelope uses the actual actionLength. Share this
  composition between TP and FP elbows; do not apply TP shoulders to FP twice.
- At the sheathed boundary, fade Reid body channels over four ticks against the
  current underlying pose. Keep reid_grip at full weight: fading it changes the
  weapon path. FP shoulder offsets return before draw completion and enter
  sheathing over four ticks. Do not delay server contact times or add a general
  incoming low-pass to every action.
- Keep interpolated walk input during takeoff. Its render weight is the
  complement of the existing rise/fall weights. Never gate gait with onGround.
  Test takeoff/landing and repeated timestamps at 30/60/144/240 Hz.
- These CPU regression gates do not prove native visual or shader acceptance.

### Opt-in continuous strike playback (core 1.35.3)
- `ActionClock.sample(action, phase, elapsed, length, clientTicks)` is an opt-in
  render policy anchored to the authoritative snapshot. It never rewinds a
  segment, reconciles at 0.75–1.25x, extrapolates no more than three ticks, and
  clamps to the server duration. Sample `tickCount + partial` (not wall time),
  share one player clock across body/FP/elbows/equipment, reset on hero exit.
  Legacy `tick/time` behavior is unchanged for existing hero callers.
- `lerp_mode: "monotone"` opts a key's outgoing segment into bounded nonuniform
  C1 Hermite interpolation. Tangents use neighboring secants; extrema, endpoints,
  explicit pre/post discontinuities and boundaries to legacy segments have zero
  tangent. This preserves exact key times/values and avoids repeated stops at
  non-extremum keys without Catmull overshoot. Existing linear/easing/Catmull
  clips are unchanged. `PoseKeys.sampleContinuous` exposes the same math for FP.
- Reinhard ordinary strikes opt in; draw/sheath/world cinematics do not. Contact
  ticks remain authoritative. Widen delivery BEFORE contact, not by delaying hits.
- FP authored action owns the right arm: cancel the vanilla swing on that path.
  Use shared `FirstPersonArm` once in view space, before vanilla's arm frame,
  not additional Euler rotations inside RenderArmEvent. A cancelled RenderHand
  path must apply it itself; the normal renderArmWithItem mixin only covers the
  non-cancelled path. Retain elbow sampling and weapon attachment inside the
  same outer transform. Native GPU/FP/TP/shader acceptance is separate from tests.
- Final constrained geometry is a separate continuity gate. A smooth source
  clip can still produce a 173-degree forearm flip: never choose a directed grip
  axis sign from a changing per-frame dot product. Preserve the authored sign.
  A hard shortest-arc wrist angle limit can also flip +limit to -limit at 180
  degrees. Fade ambiguous antipodal wrist correction to neutral, preserving the
  grip position and independent weapon matrix. Scan final world-space joint
  quaternions between high-frame-rate samples, not only source Euler keys.

- Partial grip weights blend the reachable palm target before the existing DLS
  solve, not separately wrapped Euler/quaternion joint solutions afterward.
  Preserve authored shoulder/forearm twist in the geometric seed; blend the
  elbow pole from its authored position. Support elbow points outward/front,
  leading elbow also down to clear overhead cuts. Keep full-mesh head and
  distinct-palm gates alongside final-bone continuity; neither alone is enough.
- Fade leading-forearm pronation with two-hand ownership, separately from its
  palm-position ownership. Keep the one-hand wrist correction below this fade.

### Reinhard interaction rework (core 1.36.0; draft acceptance)
- Automatic defense has a separate `DefenseReaction` channel in reserved flags
  15..26. Do not restart the main `ActionTimeline` for each projectile/beam tick.
  Commitment, held-beam refresh and client exponential recovery are independent.
  Main actions and drawn-Reid two-hand ownership take priority over defense arms.
  Existing clips are reused; this does NOT certify all new composed poses safe.
- Drawn-Reid primary uses internal REID_STRIKE and existing chop_right/chop_left
  clips normalized to 14/20 ticks, not LightPunch. Preserve the PR40 sampler,
  directed grip axis and outer FP transform. Server collision reads the bounded
  `data/viltrumitecore/reinhard/reid_melee.json` bake without client classes.
  After changing clips, grip, scale, axial roll or equipment transforms, run
  `REINHARD_BAKE=1 ./gradlew :viltrumitecore:test --tests '*ReidMeleeBakeTest'`,
  then rerun without that variable to verify the checked-in final-blade samples.
- Inspect final joints, full meshes and FP camera transforms with the defense
  channel enabled, including threat changes, movement and recovery. Existing
  clip/grip gates alone do not prove the defense overlay or physical arrow palm
  alignment. Native multi-angle visual/audio acceptance remains a separate gate.

### Defense contact continuation (Core 1.36.1)
- Final contact shares the actual equipment/arm render frames: ReinhardGrip.guard constrains the selected free hand after equipment; PlayerGeoLayer.partFrame is shared by FP rendering and palm sampling. Do not duplicate its pivot conversion as magic constants.
- HeroDefenseContacts stores TP final world points and FP view points plus the hand projection. Reproject to the current world projection before world-space beam/arrow presentation. Otherwise non-default FOV visibly misses the hand even when raw model coordinates agree. No network trust in this cache.
- Drawn Reid temporarily releases the left support hand for protection, retains the right weapon frame and recovers support continuously. Keep the PR40 directed forearm twist, grip/roll and action clock unchanged. Different contact wrist orientations are modest, never a wrist flip; rear threats evade instead of forcing a front palm through the torso.
- Keep hidden Knight FP arms visible until the response weight recovers below .01, not only while the snapshot flag is active. Otherwise the recovery disappears abruptly.
- New final-palm/head-clearance, composed attack/weapon ownership, 240Hz joint recovery, beam/hand switch and FP projection gates: ReinhardDefenseGeometryTest; wire fields: ReinhardDefenseWireTest. These do not replace native FP/TP review or human audio listening.

### Native acceptance corrections (1.36.1)
- Vanilla AbstractArrow.setOwner(Player) rewrites pickup to ALLOWED/CREATIVE_ONLY. Set DISALLOWED AFTER setOwner, not before it. Native three-mode repeat: held samples age4..12 have inventory0; release13, later pickup allowed. Keep this ordering regression.
- Minecraft Camera.rotation has +Z forward, while OpenGL view/hand space uses -Z. Convert with camera quaternion * Ry(pi), not camera quaternion alone; otherwise beam/arrow contact flips behind the camera. Test yaw/pitch roundtrip AND positive-forward dot, not roundtrip alone.
- Capture actual perspective from RenderLevelStageEvent, never use whichever RenderSystem projection is bound by an audio/tick caller (it may be GUI orthographic). TP event bone points are in camera-view coordinates; convert back before adding camera position. FP and TP caches stay separate.
- FP catch needs an unobscured shaft: raise ARROW contact above resting Reid, and turn the SAME held-arrow presentation crosswise in FP only. Do not create a duplicate entity/item. A frame captured on the first server holding tick can precede client snapshot/cache; inspect a settled held frame as well as ingress/release.
