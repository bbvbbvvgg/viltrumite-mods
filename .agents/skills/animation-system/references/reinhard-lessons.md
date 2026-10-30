# Reinhard animation and cinematic lessons

> Implementation notes from the active Reinhard PR chain through #43 (2026-10-24). Read `../SKILL.md` first. These are project-specific observations, not a blanket mandate to copy Reinhard's renderer. CPU tests and CI are not native visual acceptance. The owner considers the animation quality still mixed; do not label it visually approved without a fresh in-game review.

## New in PR #43: The Hand shader on ordinary World Cut

- `client/reinhard/cut/WorldCutErasure` implements `client/render/erasure/ErasureRenderer.Provider`. `ReinhardClient.init` registers it once. The provider reads existing `WorldCutScene.frames()` and `WorldCutS2CPacket` state. It adds no server mechanic or new network packet.
- The 200-block cut is divided into 12 overlapping original-proportion strokes at scale 7.5. Each stroke follows the terrain-smoothed cut axis, opens with the authoritative visual front and closes on its own short timeline. The leading stroke owns the fireball. Multiple casts are keyed by UUID. The cached path is invalidated with the scene.
- `ErasureRenderer` draws at `RenderLevelStageEvent.Stage.AFTER_LEVEL` with a registered `thehand_erasure` core shader. It copies the finished scene before sampling it, keeps depth and stencil compatible, restores GL state and frees render targets on unload/reload. It caps visible strokes and distance and skips the shadow pass.
- Oculus/Iris shader packs: the erasure is deliberately hidden while a shader pack is active. Do not describe this as shader-pack support. Fabulous, driver GLSL, frame-copy correctness, resource reload, GPU cost and native first/third-person visuals still require manual testing.
- The Roundabout source has license and attribution requirements. Preserve `CREDITS.md`, `docs/licenses/roundabout/LICENSE.txt` and shader headers. Never treat one approved port as permission to add a second general-purpose VFX engine.
- Preserve the existing World Cut smoke, sound, damage, carve budget, shake and diagonal cut. Test both cuts before changing the provider.

## REINHARD OVERHAUL 3b playtest fixes (core 1.41.0)

- `RenderArmEvent` fires BEFORE `renderHand` calls `setupAnim`. The shared `PlayerModel` arm still holds the last third-person pose of whichever player was drawn. Never read that pose in first person: set the camera rest pose (`ReinhardFirstPersonRig.rest`). Reading it made the camera arms jitter with other players and with the third-person animation.
- One first-person frame, two hand passes: the sword pass publishes its blade in the hand base space (`ReinhardFirstPersonFrame`); the support pass carries its arm to the hilt (`ReinhardFirstPersonRig.supportShift`) and solves the palm on the pommel. Never offset one camera arm alone for a two-handed pose.
- Support-hand ownership is one rule (`ReinhardHands`): a released support hand follows its own clip. Never send it to the empty scabbard mouth. A clip where the FREE hand acts (`chop_left`) has no `reid_grip` rows: grip rows there swing the sword with the wrong hand.
- First-person World Cuts are one rigid view-space motion of the whole two-handed rig about the guard palm (`ReinhardFirstPersonPose.cutView`, keys `CUT_*`), on the third-person beats. `cutDip` lowers the rig while it turns, so the arm roots stay out of the view. Check arm roots, palm residual, depth and 240 Hz turn steps (`WorldCutStrokeTest`).
- A strike must be fastest ON the contact. In `make_reinhard_clips.py` an easing shapes the segment that LEAVES its key: `easeInCubic` into the contact key, `easeOutCubic` out of it. A default smooth segment lands at zero speed.
- Anticipation (coil): let the chest arch and a small blade tilt carry it. Moving the grip higher or further back puts the blade or the right forearm through the head.
- `ReinhardEquipmentTest` truncates its collision lists (40 entries). One clip can hide another clip's collisions: probe each clip separately before you conclude a clip is clear.
- Detached projections that follow a grip (`ReinhardTitanBlade`) must stop at the contact pose (`poseTick`); the follow-through of the hands must not drive the projection into the ground.

## What the project learned

- A mathematically smooth clip can still produce a violent wrist/forearm flip after IK. Check the **final constrained bones**, weapon mesh and palm, not just source keyframes.
- Interpolation can destroy animation: easing every dense walk sample stops limbs at each key; a generic incoming low-pass blurs hits and makes handovers late. Smooth the intended trajectory and recovery only.
- Grip and equipment must share final parent-aware matrices. A shoulder-only item anchor fails with elbows; matrix-element interpolation shears geometry. Use quaternion rotation interpolation and full mesh clearance checks.
- An authored sword motion can look wrong when the hand solver changes the weapon after the fact. Keep weapon motion authored; solve reachable hands to the same stable grip targets. Separate sheathed rest contact, draw, one-hand and two-hand ownership.
- Contact must be synchronized to the server action. Use `ActionClock.sample` for opt-in continuous playback and never move the server hit tick to hide a visual defect.
- Recovery must return to the appropriate live base (ready guard, stance or previous action). Do not flash through idle between catch/reflect/throw, draw/sheath or action transitions.
- A held projectile and its contact VFX need one physical object and one final-palm projection. Check view-space vs world-space conventions, camera forward sign and FOV. Do not create a duplicate arrow just for first person.
- Test 30/60/144/240 Hz samples, 240 Hz final-joint deltas, all contact transitions and high/low camera angles. CPU tests are regression gates only; actual first/third-person play and shader passes remain mandatory.

## Reinhard Stage 5 timed World Cut
- DRAWN idle applies before the action overlay; draw/sheathe still apply after it. This prevents idle_holy_drawn from erasing the sword-arm action. During World Cut, RenderHandEvent routes both physical arms through the empty-arm/RenderArmEvent path even when a slot holds an item; slot contents are unchanged and item visuals return afterward.
- `world_cut` is a 5 s action overlay (contact on the BLAST tick 80). Its third-person beats and the first-person `ReinhardFirstPersonPose.cutView` are described in "REINHARD OVERHAUL 3b" below. No new pose mixin or animation engine.
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

### World Erasure cinematic polish (core 1.57.0, supersedes the 0–150 timeline above)
- ONE scene clock, 205 ticks from the N press (`ReinhardRules.ERASE_*`): 0–20 absolute stop + line; 20–80 slow crack growth with branching; 80–90 critical rupture; 90 shatter (`ErasureGlass` shards with mass, tumble, depth) and the void; 92 white light; 112–150 the target dissolves on its real model in its exact frozen pose at its world position (`ErasureDissolveRenderer`); 150 server removal; 160 reality pulls back; 168–192 six shard rings snap home (`ErasureGlass.SNAPS`), the last one is the final impact; 192–205 cracks close, shake settles, world resumes.
- The glass, shards and dissolve are geometry drawn from `GameRendererDashMixin` renderLevel TAIL **after** the dash_impact grade (`ErasureScene.renderSeam`). The shader only grades (tension drain/vignette/shimmer, rupture flash + chromatic split, void drain, rebuild shock ring). Do not put crack/shard geometry back into the fragment shader.
- While the scene draws the target, the level pass skips it (`client/render/scene/SceneEntityHide` + `SceneEntityHideMixin` on `LevelRenderer.renderEntity`, live predicate). The target's own first-person view gets a white vignette instead of its model over the camera.
- Shake: `ErasureShake` is a pure function of scene time (rupture jolt, rebuild rumble, one impulse per ring, the final one hardest, exactly 0 at 205), applied to rendered camera angles and the glass plane only, scaled by `reinhardWorldCutShake`. The player's yaw/pitch are never written.
- HUD: `client/hero/CinematicHud` cancels `RenderGuiEvent.Pre` (HIGHEST) and `RenderHandEvent` while a registered predicate holds and draws only hero subtitles + scene layers. No option writes, no latch: every end path restores the HUD on the next frame.
- Sounds (original, procedural, `tools/sfx/reinhard_erasure.py`): tension 20, crack+shatter 90, dissolve 112, inhale 160, snaps 168–187, restore 192; MASTER duck from 0 to 192. Late joins skip any beat more than 3 ticks late (`ErasureScene.LATE`); aborted scenes stop their own sounds. New lang keys go to `lang-overlays/reinhard_erasure/`, new sound events to `sound-overlays/reinhard_erasure.json` (base files are past the 64 KB push limit).

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

### World-cut audio identity
- Two separate contracts. Do not mix them: the normal (ground) World Cut uses `WorldCutAudio` + `WorldCutPulse`; only Diagonal World Cut keeps the Core 1.36.2 `CutFinishSound` cues below.
- Diagonal World Cut (Core 1.36.2): the loud completion is the original eight-second WORLD_CUT_TAIL recording, started only after server DONE, nearest-line placement, range320, never looped during carve. WORLD_CUT_BLAST is only a quiet WORK pulse there: tick96 then every40ticks while working, voice16ticks, never at DONE.
- Normal World Cut (Core 1.38+): no WORLD_CUT_SILENCE/BLAST/TAIL. One looping `world_cut_hum` per cast from BLAST (follows scene fade after DONE), one `world_cut_pulse` voice per beat, one `world_cut_impact` at BLAST and one `world_cut_blade` ring at tick8. Both accents are exempt from the two MASTER ducks (tick8..BLAST).
- Pulse clock: `WorldCutPulse` FIRST=96, PERIOD=50, LENGTH=24. Voice, hum swell, +2.4 shake and the dust ring read one beat per scene: a scene admits a beat once, on time or at most CATCH_UP=3 ticks late; a scene created later in the pulse (late join from a WORK packet) skips that whole beat. The `acceptedUntil` cap (DONE elapsed − 1) lets an in-flight beat finish and rejects later beats for every component.
- One-shot accents use the same bounded window (tick..tick+3): a late viewer never hears BLAST or the blade ring at join time.
- Dust ring renders from the fractional `phase` (partial ticks); sound triggers stay on whole ticks.
- Hum level: the engine clamps a voice to 1.0, so the swell crest is 1.0 and the resting level is 1/(1+SWELL)=0.69. Never put the resting gain at the ceiling — the +45 % swell would be clipped away.
- The pulse release is baked into the PCM (`tools/sfx/reinhard_worldcut.py`); the runtime voice keeps a constant gain for the whole 1.35 s asset (27 ticks). Never add a second runtime decay.
- Neither world-cut scene plays CUT_RUMBLE.
- Tail mastering is reproducible through tools/sfx/reinhard.py finish_master(); begin with the original bed master, not the already-mastered output. Keep original licensing and duration. The ground-cut assets come from tools/sfx/reinhard_worldcut.py; its decoded-loop seam gate (delta ≤ 0.01, ratio ≤ 6) must pass before a regenerated hum is accepted.
