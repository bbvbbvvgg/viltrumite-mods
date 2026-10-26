# Animation system: implementation catalog

> Technical reference extracted from the previous skill on 2026-10-24. Read `../SKILL.md` first. This file maps existing code, not a universal recipe. Some early entries describe legacy Viltrumite behavior. When code and this catalog disagree, code wins. Modern hero patterns and verification rules are in the entry skill. The Reinhard history is in `reinhard-lessons.md`.

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

Legacy pattern: one mixin per ability on `PlayerModel.setupAnim(LivingEntity;FFFFF)V` at `@At("TAIL")`. Modern heroes can use a central poser or a Forge renderer instead.

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

Do not infer the final pose order from numeric priority alone. Inspect the active mixin configs, overlapping injectors and actual rendered result. Grab and Block can override earlier body poses.

### 3.2 Rules for every pose mixin

1. Legacy mixins read `ViltrumiteCorePlayer`. Modern heroes gate by `HeroPublicSnapshot` and their active action/flags. Never pose an unrelated hero.
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

- Legacy Punch/Chop abilities use a 20-tick countdown. `t = clamp((20 - (ticks - partialTick)) / 20, 0, 1)`.
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
- For legacy paths, check how the render event supplies partial ticks. Modern hero timelines use their shared snapshot clock. Never mix different clocks across body, arms and equipment.

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

- Legacy actions use individual `FirstPersonXMixin` hooks (Barrage, Block, Chop, Chop2, Grab, Gun, Punch, Thunderclap). Modern heroes can share one first-person compositor (Homelander; Iron Man: `IronManFirstPersonShieldMixin` → `IronManFirstPerson` for every combat pose and the shield guard). Read the nearest one before you write a new one. Copy its structure.
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

### 7.4 Whole-body replacement (Hulkbuster, Reinhard): renderer reference

Iron Man stage 5 replaces the whole player body with the Mark 48 model. Reinhard uses the same Forge event family but has its own complete rendering path. Study both before implementing a replacement:

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
- Stage 4 (Holy Sword): `reid_draw` (6 s) / `reid_sheathe` (1 s) are driven by the synced Reid phase (`ReinhardPoses.reidClip`, clock `ReidClock`), not by the action timeline; `idle_holy_drawn` loops over the Holy Sword stance while Reid is out; `chop_left` / `chop_right` play for the kit chop (Lethal Chop, synced chop side); `sheath_strike` (0.6 s) is an ordinary action overlay; `saw_sphere` (13 s, item 19, replaced `cut_rain`) is a one-handed authoritative overlay sampled on the scene clock, its body yaw spun by `SawSphereClient.modelYaw` and its camera forced to third person back. Reid in the hand: `ReinhardSword.drawInHand` in vanilla's held-item frame (`HeldItemAnchor`), the slot item of that hand is not drawn while Reid is out (first person: `RenderHandEvent` cancel + `ItemInHandRendererInvoker`). First-person arm offsets for the new actions live in `ReinhardFirstPersonPose`.
- Art from OBJ: `tools/obj2geo.py` (rotated boxes → cube `rotation`/`pivot`; `--skip-material-suffix _glow` for emissive overlay copies; `--uv-top-origin` when vt is measured from the top). Note: `GeoBone.resetToDefault` resets `hidden` now (item 7 is kept for old code paths).

Art: Sind Hulkbuster from the user's archive, `tools/assets/convert_ironman_sind.py` (geo via `tools/tabula2geo.py`, clips baked from the Sind `.fsk` scripts via `tools/fsk2anim.py`). The model is 68 px tall: the renderer scale is `HulkbusterLayer.SCALE × 32/68`. The jackhammer arm is a separate geo with its own texture; it replaces the body bone `pivotLeft` while the jackhammer tool is selected. Thruster fire is a separate geo posed with the same clips and drawn as glow. Tests: `HulkbusterAssetsParseTest` (parse, bones, clip lengths), `HulkbusterPosesTest`, `HulkbusterAssemblyTest`.

## 8. World VFX

Pattern: a `@EventBusSubscriber(value = Dist.CLIENT, bus = FORGE)` class that renders in `RenderLevelStageEvent` at `AFTER_LEVEL` or `AFTER_PARTICLES`.
- Geometry: `Tesselator`, `QUADS`, `POSITION_COLOR`, position-color shader.
- Usual blend: additive (`SRC_ALPHA, ONE`), cull off, depth test on, `depthMask(false)`.
- Camera space: push the model-view stack, `XP(camXRot)`, `YP(camYRot + 180)`, translate `worldPos - cameraPos`.
- Style: the "paint pixel" ring (`drawPaintPixel`) and billboard crosses. Shared impact effects default to this pixel style. The approved The Hand erasure is a specialized shader exception; see `references/reinhard-lessons.md`.
- ALWAYS restore GL state: `depthMask(true)`, `enableCull`, `defaultBlendFunc`, `disableBlend`, pop the stack.

Two driver types:
- One-shot: static list + `spawnX(pos, ...)` (`BlockVFXManager`, `MeteorImpactVFXManager`).
- State-driven: read synced ticks every frame (`PunchVFXManager`, `ChopSweepVFXManager`), or detect a tick threshold in `ClientTickEvent` (`ThunderclapVFXManager`).

For new effects, age by ticks with partialTick (Thunderclap pattern). Do not use per-frame `age++`: it depends on FPS.

### Regulus pose and world-space helpers

- Use `RegulusAnimationManager.castTime(entity, partialTick)` for the Regulus cast in both views. The client-ticked `RegulusActionClock` follows synced action elapsed/length; do not use raw snapshot ticks for one view and the shared clock for the other. This is distinct from the opt-in `ActionClock.sample` policy used by Reinhard.
- Use `RegulusAnimationManager.weight(entity, layer, active)` for third person and `firstPersonWeight(entity, layer, active, mainHand)` for first person. For casts use `castWeight` / `firstPersonCastWeight`. The `mainHand` argument gives each physical view its own weight. Tick inactive weights too, so they fade out.
- `RegulusAnimationManager.onClientTick` removes an entity's clock and view weights when its snapshot stops being Regulus; a level change clears all clocks/weights. Do not call a nonexistent `reset(entity)` API.
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
Current uniforms (besides `ProjMat`/`OutSize`): `DashIntensity, PunchIntensity, ShakeIntensity, GrabIntensity, LockIntensity, Time, ChopIntensity, ChopType, ChopIsLeft, BarrageIntensity, BarrageIsLeft, ScourgeIntensity, ScourgeTurn, RegulusOverheat, RegulusHeartFlash, RegulusMadness, RegulusPulse, EraseCrack, EraseShatter, EraseDark, EraseRebuild, EraseSeed`. `ScreenEffectCatalogTest` fails when this list drifts from `program/dash_impact.json` or the post pass.

To add a screen effect:
1. Declare the uniform in `program/dash_impact.json` AND in the pass `uniforms` of `post/dash_impact.json`.
2. Clamp it in `dash_impact.fsh`.
3. Add it to the early-out check. If you do not, the effect never shows alone.
4. Colour effects are an exclusive chain: chop > barrage > dash/punch. Put a new one in the chain or as an overlay after it, like lock.
5. Compute the intensity in `GameRendererDashMixin`. Use distance falloff for other players.
6. Do not create a second post chain. Flight `sonic_boom` already replaces other post effects when it loads; during World Erasure core shuts an already-loaded sonic chain down and cancels sonic loads (`GameRendererDashMixin`).
