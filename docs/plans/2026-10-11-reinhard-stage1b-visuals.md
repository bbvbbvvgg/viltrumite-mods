# Reinhard — Stage 1b (Visuals: model, sheath, walk, stances, auras) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Overview: `docs/plans/2026-10-11-reinhard-overview.md`. Read `.agents/skills/animation-system/SKILL.md` (§7, §7.4, §8, §11) before every task of this stage. This stage ships the first jar.

**Goal:** Reinhard's model from the user's archive replaces the player in third person and first person (arms from the model), the sheathed Dragon Sword hangs on the belt, the walk comes from `reinhard_walk.json`, each mode has its stance and aura, and the race screen shows his 3D head.

**Architecture:** Whole-body replacement through the only allowed pattern (skill §7.4): `client/reinhard/ReinhardRenderer` subscribes to `RenderPlayerEvent.Pre` (draws the geo body, cancels vanilla) and `RenderArmEvent` (draws the geo arm, cancels the vanilla arm). Pose = `AnimationController.blend(stance, walk, weight)` + `AnimationController.overlay(action clip)`; times from synced fields or walk distance, never a client counter. Pure pose selection lives in `client/reinhard/ReinhardPoses` (tested). Assets come from reproducible scripts: `tools/obj2geo.py`, `tools/assets/reinhard_walk2anim.py`, `tools/assets/make_reinhard_clips.py`.

**Tech Stack:** anim core (`BakedGeoModel`, `AnimationParser`, `AnimationController`, `AnimRenderer`, `PlayerGeoLayer.renderArmPart`), `PixelVfx`, Python 3 + Pillow.

**Spec:** §8, §9.2, §2 (head), §11.1.

## Global Constraints

See the overview. Stage-specific:
- `PlayerGeoLayer` is NOT used for the body (it draws over the vanilla model, spec §8). It is used only for its static `renderArmPart` helper in first person, the same as `HulkbusterRenderer`.
- Geo in armor space (skill §7.1 / §7.4.3): feet at y 0, vanilla pivots (head/body 0,24,0; arms ±5,22,0; legs ±1.9,12,0), x as in Bedrock (right = −x). Body frame: `mulPose(YP(180 − bodyYaw))`, scale 1 (the model is 32 px tall, like the player; hitbox unchanged, no `bodyScale`).
- Cancelling the vanilla render removes vanilla layers: armor, held items, name tag, cape, arrows, outline. This stage restores the ones a PvP player needs (name tag, held items, outline when glowing) and draws no vanilla armor (the knight model is his armor).

## Review Focus

1. Name tag of another Reinhard player is visible in multiplayer and hidden when sneaking behind a wall (vanilla rules) — manual list Task 3, invoker test `NameTagRulesTest.sneakDims`.
2. A Reinhard holding a block, a bow or food shows the item in the geo hand in third person — Task 3 `HeldItemAnchorTest` (hand bone → item transform) + manual.
3. Reinhard glowing (spectral arrow, Iron Man scan, Homelander focus) keeps an outline — Task 3 manual; renderer also draws into the outline buffer when `Minecraft.shouldEntityAppearGlowing` is true.
4. Sleeping, swimming, riding a boat/horse, elytra: the body lies / swims / sits instead of standing in the air — Task 8 `ReinhardPosesTest.bodyStateFromPose`.
5. Oculus shaders: no doubled glow, stance does not speed up with the shadow pass — Task 4 `StanceBlendTest.frameRateIndependent`.

---

### Task 1: `tools/obj2geo.py` and the converted model, arms and sword

**Files:**
- Create: `tools/obj2geo.py`, `tools/assets/convert_reinhard.py` (driver: runs obj2geo on the archive, copies textures, writes `.b64`), `res/geo/reinhard/reinhard.geo.json`, `res/geo/reinhard/reinhard_arms.geo.json`, `res/geo/reinhard/sheath.geo.json`, `res/geo/reinhard/blade.geo.json`, `bin/textures/entity/reinhard/{reinhard,reinhard_glow,dragon_sword,dragon_sword_glow}.png.b64`, `core/client/reinhard/ReinhardAssets.java`
- Modify: `tools/assets/reinhard_sources.md`
- Test: `test/client/reinhard/ReinhardAssetsParseTest.java`

**Interfaces:**
- `obj2geo.py IN.obj OUT.geo.json --texture W H [--bones head,body,...] [--pivots vanilla|none] [--armor-bones] [--scale S] [--identifier NAME]`:
  - every `o` object = one top-level bone; `--pivots vanilla` sets the vanilla pivot by bone name; `--armor-bones` renames to `armorHead/armorBody/armorRightArm/…` (for `PlayerGeoLayer.renderArmPart`).
  - OBJ space: y up, neck at y 0 (model spans y −24…8) → Bedrock y = OBJ y + 24; x and z kept; `--scale 16` for the sword (block units → pixels).
  - quads are grouped into axis-aligned boxes: the 4–6 quads that share one AABB and lie on its faces form one cube (`origin`, `size`); a lone quad becomes a zero-thickness cube with only that face. Non-axis-aligned quad → error with the face index.
  - per-face UV (`uv` object, faces `north/south/east/west/up/down`): from the quad's `vt` (v flipped: `v_px = (1 − vt) · H`), `uv` = the corner the face's top-left vertex maps to, `uv_size` signed so mirrored faces keep their orientation. Missing faces are omitted (BakedGeoModel returns `null` → not drawn; Step 1 asserts this).
  - prints a summary: bones, cubes, faces per bone; must match 473 faces for `reinhard.obj`.
- `convert_reinhard.py --assets DIR`: `reinhard.obj` → `reinhard.geo.json` (body bones `head, body, right_arm, left_arm, right_leg, left_leg`, vanilla pivots) and `reinhard_arms.geo.json` (arms only, armor bone names); sword: `blade_sheath.obj` → `sheath.geo.json`, `training_blade_drawn.obj` → `blade.geo.json` (both under one bone `sword`, pivot at the hilt guard). The sheathed blade offset = translation that maps the drawn blade onto the faces of `training_blade.obj` that are not in `blade_sheath.obj` (least-squares on matching vertices); written into `blade.geo.json` as bone `sheathed_offset` metadata and into `ReinhardAssets.SHEATHED_OFFSET` by hand once (the script prints the Java line). Uses `tools/preview_geo.py` to render `docs`-free previews into `/tmp` for the visual check against `previews/*.png`.
- `ReinhardAssets`: `ResourceLocation BODY, ARMS, SHEATH, BLADE, TEXTURE, GLOW, SWORD_TEXTURE, SWORD_GLOW, ANIMATIONS`; bone name constants; `Vector3f SHEATHED_OFFSET`.

- [x] **Step 1: Write the failing test** — `ReinhardAssetsParseTest`: `bodyHasSixBones` (names above), `armsHaveArmorBones`, `pivotsAreVanilla` (right_arm pivot (−5, 22, 0) after the parser's sign rules), `heightIs32px` (max y of head cubes = 32), `swordParses`, `missingFaceIsNull` (a hand-written geo with one face → the other five faces are null and nothing throws).
- [x] **Step 2: Run** `./gradlew :viltrumitecore:test --tests '*ReinhardAssetsParse*' --no-daemon` → FAIL (files missing).
- [x] **Step 3: Implement** `obj2geo.py`, the driver, run it on the archive; compare the `/tmp` previews with `previews/reinhard.png` and `sword_f.png` / `drawn_f.png` (front faces, colours, no mirrored text on the emblem).
- [x] **Step 4: Run** → PASS; `python3 tools/obj2geo.py … ` prints `faces: 473`.
- [x] **Step 5: Commit** `feat(tools): obj2geo converter` and `feat(reinhard): converted model and sword`.

### Task 2: Walk animation converter

**Files:**
- Create: `tools/assets/reinhard_walk2anim.py`, `res/animations/reinhard/reinhard.animation.json` (clip `walk`)
- Test: `test/client/reinhard/ReinhardWalkClipTest.java`

**Interfaces:**
- Input format (`reinhard_walk.json`): `{length: s, referenceAmount: a, tracks: {bone: [{time, rotation:[x,y,z]}]}}`, rotation in degrees in Java `ModelPart` convention. Output: format 1.8.0, `loop: true`, `animation_length` = `length`, per bone `rotation` keyframes `"t": [x, y, z]` with the same values (memory bank: BakedGeoModel's (−x, −y, z) equals a Java ModelPart turn, so angles carry over), linear.
- `referenceAmount` (0.55) is written as `ReinhardPoses.WALK_REFERENCE_AMOUNT`: walk weight = `clamp(limbSwingAmount / 0.55, 0, 1)`; clip time = `walkDistance · WALK_SECONDS_PER_BLOCK` (constant tuned so the feet do not slide; starting value `length / (2 · 0.6662)` matching vanilla's limb swing period).

- [x] **Step 1: Write the failing test** — `walkIsOneSecondLoop`, `walkAnimatesFourLimbs`, `rightArmStartsForward` (parsed rotX at 0 s equals the source −31.51° after the parser's X negation rule, i.e. the arm swings forward like a vanilla right arm at the same phase).
- [x] **Step 2: Run** → FAIL.
- [x] **Step 3: Implement** and run the converter.
- [x] **Step 4: Run** → PASS.
- [x] **Step 5: Commit** `feat(reinhard): walk clip from the archive`.

### Task 3: Whole-body renderer (third person)

**Files:**
- Create: `core/client/reinhard/ReinhardRenderer.java`, `core/client/reinhard/ReinhardPoses.java`, `core/client/reinhard/ReinhardView.java` (snapshot accessors: mode, Reid bits, action), `core/client/mixin/EntityRendererNameTagInvoker.java` (invoker for `EntityRenderer.renderNameTag` and `shouldShowName`; register in `viltrumitecore.client.mixins.json`)
- Test: `test/client/reinhard/ReinhardPosesTest.java`, `test/client/reinhard/NameTagRulesTest.java`, `test/client/reinhard/HeldItemAnchorTest.java`

**Interfaces:**
- `ReinhardPoses.Pose` record: `String stance`, `double stanceSeconds`, `float walk`, `double walkSeconds`, `@Nullable Clip overlay` (`record Clip(String animation, double seconds)`), `BodyState body` (`STAND, CROUCH, SIT, SLEEP, SWIM, FALL_FLY`), `float aura`.
- `static Pose pose(Input in)` where `Input` = `record Input(ReinhardMode mode, int flags, int actionId, int actionElapsed, int actionLength, float partialTick, float limbSwingAmount, float walkDistance, double realSeconds, net.minecraft.world.entity.Pose vanillaPose, boolean passenger)` — pure, no Minecraft client calls.
- `ReinhardRenderer.onRenderPlayerPre`: skip only when the player is not Reinhard (`ClientHeroData` hero id). An **invisible** Reinhard is still handled and the vanilla render is **always cancelled** (otherwise a glowing invisible Reinhard would show the vanilla `PlayerModel` outline): invisible → no body/glow/sword/items/name tag passes, only the outline pass when `Minecraft.shouldEntityAppearGlowing` (vanilla also outlines invisible glowing players), and `isInvisibleTo(localPlayer) == false` (spectator, same team with `seeFriendlyInvisibles`) → body with `entityTranslucent` at alpha 0.15 like vanilla; pure helper `ReinhardPoses.passes(boolean invisible, boolean visibleToViewer, boolean glowing)` → `{body, translucent, outline, extras}`. Then `pose(...)` → apply clips (`AnimationController.blend` stance/walk, then `overlay`) → head yaw/pitch on the `head` bone from `netHeadYaw/headPitch` (same math as `HulkbusterRenderer.applyHead`) → draw `BODY` with `entityCutoutNoCull(TEXTURE)`, then `eyes(GLOW)` unless `ShaderCompat.isShadowPass()` → sword parts (Task 7) → held items (main/off hand via `ItemRenderer.renderStatic(..., THIRD_PERSON_RIGHT_HAND/LEFT_HAND, ...)` at the hand bone's world transform, offset `HeldItemAnchor.offset(arm)`; the hand that holds Reid — right while DRAWN/DRAWING/SHEATHING, Stage 4 — draws **no** vanilla held item: pure `ReinhardPoses.drawsHeldItem(arm, reidPhase)`) → name tag through the invoker when `shouldShowName` → when the entity appears glowing, render `BODY` again into `Minecraft.renderBuffers().outlineBufferSource()` with the team colour → `event.setCanceled(true)`.
- Clip bone sets: stance clips animate all six bones; action overlays animate only the bones they need.

- [ ] **Step 1: Write the failing tests** — `idleWhenStill` (walk 0), `walkWeightFromSwing` (0.55 → 1, 0.275 → 0.5), `stanceFollowsMode` (R/K/H → `idle_restrained/idle_knight/idle_holy`), `overlayFromAction` (actionId ≠ −1 → overlay seconds = (elapsed + pt)/20, clamped to length), `NameTagRulesTest.sneakDims` (pure helper mirrors vanilla: sneaking → see-through alpha 0x20), `HeldItemAnchorTest.rightHandOffset` (anchor below the right_arm pivot by 10 px, matches vanilla `ItemInHandLayer` translate (−1/16, 0.4375, 0.0625) after the arm transform), `passesInvisibleGlowingOnlyOutline`, `passesInvisibleSpectatorTranslucent`, `passesVisibleAll`, `heldItemHiddenOnReidHand`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; register `ReinhardRenderer` on the Forge client bus (`@EventBusSubscriber(value = Dist.CLIENT, bus = FORGE)`).
- [ ] **Step 4: Run** → PASS. Manual third person (F5 both directions): model replaces the player, head follows the look, glow visible, no vanilla skin parts, name tag in LAN, held sword/block/bow visible, spectral arrow outline, invisibility potion + spectral arrow (outline of the knight shape, never a vanilla Steve), spectator view of an invisible Reinhard (translucent), Oculus on/off.
- [ ] **Step 5: Commit** `feat(reinhard): whole-body renderer`.

### Task 4: Stances per mode and stance blending

**Files:**
- Create: `tools/assets/make_reinhard_clips.py` (keyframe tables → `res/animations/reinhard/reinhard.animation.json`, merged with `walk`), `core/client/reinhard/StanceBlend.java`
- Modify: `core/client/reinhard/ReinhardRenderer.java`
- Test: `test/client/reinhard/StanceBlendTest.java`, extend `ReinhardWalkClipTest` (all clips parse)

**Interfaces:**
- Clips (loop, 2–4 s breathing): `idle_restrained` (relaxed, arms free, slight sway), `idle_knight` (left arm folded behind the back: left_arm rotX +25, rotZ −10, z −2 px; right arm loose fist), `idle_holy` (left hand on the hilt at the left hip; with Reid drawn the sword hand is the right one, Stage 4 adds `idle_holy_drawn`), `crouch`, `sit`.
- `StanceBlend`: per-player weights `WeakHashMap<Entity, State>`; `float[] weights(Entity e, ReinhardMode target, long nanoTime)`; ease `1 − exp(−k·dt)`, `k = 12`, dt clamped to 0.1 s; called once per entity per frame (skill §6). The renderer blends the old and new stance with `AnimationController.blend` while the weight < 0.999.
- Knight's walk keeps the left arm behind the back: `walk` is blended with `idle_knight` on `left_arm` only (`overlay` of a one-bone clip `knight_left_arm_hold`).

- [ ] **Step 1: Write the failing tests** — `blendReachesTarget` (after 1 s weight > 0.999), `frameRateIndependent` (60 calls at 16 ms ≈ 240 calls at 4 ms within 1e-3), `secondCallSameFrameDoesNotAdvance` (dt 0 → unchanged), `resetOnHeroChange`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; generate the clips.
- [ ] **Step 4: Run** → PASS. Manual third person: each MMB switch blends to the new stance in ~0.3 s; Knight walks with one arm behind the back.
- [ ] **Step 5: Commit** `feat(reinhard): stances per mode`.

### Task 5: First person arms

**Files:**
- Modify: `core/client/reinhard/ReinhardRenderer.java` (`onRenderArm`, `onRenderHand`), `core/client/mixin/AlwaysVisibleOffhandMixin.java` (one more `||` term in `shouldRender`: `ReinhardFirstPerson.wantsOffhand(player)`, the same pattern as `RegulusAnimationManager.wantsOffhand` / `IronManFirstPerson.wantsOffhand`), create `core/client/reinhard/ReinhardFirstPerson.java`
- Test: extend `ReinhardPosesTest`

**Interfaces:**
- `onRenderArm(RenderArmEvent)`: Reinhard → `PlayerGeoLayer.renderArmPart(stack, buffers, light, model, arm, Part(ARMS, [cutout(TEXTURE), glow(GLOW)], geo -> applyFirstPerson(geo, pose)))`, cancel. Existing first-person mixins that move the `PoseStack` before the arm (e.g. `FirstPersonChopMixin`) still apply because `RenderArmEvent` fires inside `renderPlayerArm`.
- `ReinhardPoses.firstPersonArmVisible(mode, arm, flags)`: Knight hides the off arm (it is behind the back); Holy Sword shows the left arm lowered to the hilt (draw offset only in third person; first person = vanilla rest position). Fact (skill): an **empty** off hand is drawn in first person only through `AlwaysVisibleOffhandMixin`; `RenderArmEvent` for it never fires otherwise. So `ReinhardFirstPerson.wantsOffhand(player)` = `firstPersonArmVisible(mode, offArm, flags) && (mode != KNIGHT)` plus any Reinhard action clip that uses the off hand (Stage 3 adds Catch, Fist Combo, Grab). `onRenderHand(RenderHandEvent)` (fires before vanilla draws the arm **with** its item): main hand while Reid is in it (Stage 4) → cancel and draw the geo arm + blade ourselves; otherwise vanilla. Limbo (Stage 6) cancels `RenderHandEvent` for both hands.

- [ ] **Step 1: Write the failing test** — `knightHidesOffArmInFirstPerson`, `restrainedShowsBoth`, `holyShowsBoth`, `wantsOffhandOnlyWhenVisible` (empty off hand, Restrained → true; Knight → false; other hero → false).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**.
- [ ] **Step 4: Run** → PASS. Manual first person: arms are the model's arms with glow, item in hand still drawn by vanilla on top, the **empty** left hand is visible in Restrained/Holy Sword, Knight shows one arm; other heroes' off-hand behaviour unchanged.
- [ ] **Step 5: Commit** `feat(reinhard): first person arms`.

### Task 6: 3D head on the race screen (`HeroHeadPreviews` seam)

**Files:**
- Create: `core/client/hero/HeroHeadPreviews.java`, `core/client/reinhard/ReinhardHeadPreview.java`
- Modify: `core/client/gui/RaceSelectionScreen.java` (draw heads through `HeroHeadPreviews.draw(id, graphics, x, y, size)`; the default = the current 2D skin face code moved into `HeroHeadPreviews.SKIN_FACE`), `core/client/reinhard/ReinhardClient.java`
- Test: `test/client/hero/HeroHeadPreviewsTest.java`

**Interfaces:**
- `HeroHeadPreviews`: `interface Preview { void draw(GuiGraphics g, int x, int y, int size, float partialTick); }`, `register(HeroId, Preview)`, `draw(...)` falls back to the skin face. Other heroes stay on the fallback (pixel-identical to today).
- `ReinhardHeadPreview`: renders only the `head` bone of `BODY` with GUI lighting (`Lighting.setupForEntityInInventory`), slow yaw sway ±15°, 24 px, then `reinhard.png` (archive icon, 32×32 scaled to 12) at the button's left as the hero badge (open question 5).

- [ ] **Step 1: Write the failing test** — `fallbackForUnregistered`, `registeredWins`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**.
- [ ] **Step 4: Run** → PASS. Manual: race screen shows four hero buttons; the old three heads look exactly as before; Reinhard's head is 3D.
- [ ] **Step 5: Commit** `feat(hero): head preview seam on the race screen`.

### Task 7: Sheath on the belt and mode auras

**Files:**
- Create: `core/client/reinhard/ReinhardSword.java` (draws sheath + sheathed blade on the body bone), `core/client/render/vfx/ReinhardAuraVfx.java` (`RenderLevelStageEvent` AFTER_PARTICLES)
- Modify: `core/client/reinhard/ReinhardRenderer.java`
- Test: `test/client/reinhard/AuraRulesTest.java`

**Interfaces:**
- `ReinhardSword.drawSheathed(PoseStack bodyFrame, BakedGeoModel body, …)`: attach to the `body` bone transform at the left hip (`HIP_OFFSET` = (+4.5, 11, 0) px, tilt rotZ −20°, rotX +10°), draw `SHEATH` + `BLADE` translated by `SHEATHED_OFFSET`, cutout + glow (glow skipped in the shadow pass). Drawn always (spec §8); Stage 4 moves the blade to the hand.
- `ReinhardAuraVfx` (pixel style, `PixelVfx.billboardPixel`, additive blend, GL state restored): Knight — 6–10 white wind motes circling the feet (radius 0.7, rise 0.4 then fade, 1.2 s life); Holy Sword — 1 gold spark every ~15 ticks near the body (`AuraRules.goldSparkChance`); none in Restrained. Ages by ticks + partialTick. Hidden for the local player in first person except a few motes at the feet below the view (skill §8 rule: local first-person aura filtering in the manager). Motes cleared on logout/level change.

- [ ] **Step 1: Write the failing test** — `restrainedHasNoAura`, `knightWindCount` (6–10), `holySparkRate` (expected sparks per 20 s within 20–30), `firstPersonFilter` (local + first person → feet motes only).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**.
- [ ] **Step 4: Run** → PASS. Manual 1st/3rd person, all three modes, walking and standing; shaders on.
- [ ] **Step 5: Commit** `feat(reinhard): sheath on the belt and mode auras`.

### Task 8: Body states, skill update, first jar

**Files:**
- Modify: `core/client/reinhard/ReinhardPoses.java` (`BodyState`), `ReinhardRenderer.java` (rotations for SLEEP/SWIM/FALL_FLY in the event stack before the body frame, like vanilla `setupRotations`), `make_reinhard_clips.py` (`sit`, `crouch` already; `swim` arm stroke), `.agents/skills/animation-system/SKILL.md` §7.4 (Reinhard as the second user; "pose clips must cover crouch/sit/sleep/swim because model mixins never run"; name tag / held items / outline restoration), `.memory-bank/project.md`, `SESSION.md`, `gradle.properties` or `viltrumitecore/build.gradle` (core minor version +1)
- Test: extend `ReinhardPosesTest`

- [ ] **Step 1: Write the failing tests** — `bodyStateFromPose` (CROUCHING → CROUCH, SLEEPING → SLEEP, SWIMMING → SWIM, FALL_FLYING → FALL_FLY, passenger → SIT, else STAND).
- [ ] **Step 2: Run** → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS.
- [ ] **Step 5:** Full verification list + dedicated server start (`./gradlew :viltrumitecore:runServer`): no client class loaded (renderer, invoker mixin only in client config).
- [ ] **Step 6:** In game, both views: walk, sprint, sneak, swim, boat, horse, bed, elytra (creative), all three stances, Homelander/Iron Man/Hulkbuster/Regulus visuals unchanged.
- [ ] **Step 7:** Commit `feat(reinhard): body states` and `docs(reinhard): stage 1b notes`; push `feat/reinhard-stage1b`; PR «Рейнхард, этап 1b: модель, ножны, стойки».

Acceptance criteria closed: §11.1 (full), §11.12 (partial).

## Risks and pitfalls

- OBJ UV orientation and mirrored faces are the most likely bug (Iron Man Sind conversion: "dict `east` = Bedrock +x side"). Check the emblem and the face in the preview before committing.
- `GeoBone.resetToDefault` does not reset `hidden` (skill §7.4.7): set `hidden` every frame.
- `RenderPlayerEvent.Pre` fires per render pass; with Oculus it fires in the shadow pass too: draw the body (shadow) but skip glow, aura and outline.
- `StanceBlend` and every weight: once per entity per frame; a second call zeroes the delta (same trap as `SilhouetteManager.getState`).
- Do not keep `BakedGeoModel` references across a resource reload (skill §7 invariants).
