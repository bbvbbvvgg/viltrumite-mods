# Reinhard quality rework

Base: `feat/reinhard-stage7` at `add8a0d`. Work branch:
`feat/reinhard-quality-rework`, PR #36. The 2026-10-11 design stays authoritative.
The owner's new request overrides grab input: Z acquires, RMB throws.

## Completion boundary
The code completion pass is implemented. The owner explicitly waived in-game
verification. Do not treat that waiver, compilation, software previews or unit
tests as evidence of gameplay/GPU acceptance. Do not retry client/server startup
for this request. Core 1.33.0 supersedes the previously supplied 1.32.0 test JAR.
No new animation engine, shader chain, animation dependency or flight change.

## Audit and implementation sequence
1. [x] Regression tests for loop sampling, bounded server time and input claims.
   Stateless seek/overlay did not wrap loops; walk and breathing froze after a cycle.
2. [x] Replace stances, gait, sprint, all action clips and ordinary melee visuals.
   Articulate existing sleeve/hand cubes into elbows without changing their UVs.
   Replace crouch/sit/crawl recipes; swimming now has a bent-elbow breaststroke.
3. [x] Keep Knight's free hand behind the back during locomotion/combat.
   Use full-body anticipation/contact/recovery and continuous toss → kick contact.
   Preserve authoritative damage/contact times and all sword-state restrictions.
4. [x] Double Reid's scale, refit its hip frame and correct the extraction axis.
   Render both meshes through one path. Blend rigid guard frames with quaternion
   slerp and translation/scale interpolation, not a shearing matrix lerp.
   Draw transfers over ticks 96–120; sheathe returns to the sheath axis over 0–8.
   Sheath Strike transfers out over 0–4, contacts at 6 and returns over 8–12.
5. [x] Follow the lower arm for both FP/TP grips. FP arms are 13% wider.
   FP elbows sample the same action/Reid clips through PlayerGeoLayer; free-arm
   counterbalance and physical-right ability routing also work with a left main arm.
   Active right-arm abilities cannot disappear behind a held item in first person.
6. [x] Add per-player, per-view interruption recovery over the current moving base.
   Bone channels, first-person offsets and weapon frames retain the last displayed
   pose on a key change and release exponentially over at most 0.30 s. Extra render
   passes do not advance a clock. Hero exit drops the caches; weak keys bound lifetime.
7. [x] Z holds, RMB finishes. Replace per-tick player teleports with explicit hero
   held-target ownership: a shared yaw/neck anchor, main-thread movement-packet
   suppression, client local-body prediction and remote render interpolation.
   One release correction remains. PULL still owns lease priority, not attachment.
   World pause suspends this presentation/prediction; legacy grabs retain their path.
8. [x] Rebuild 16 native-64px icons in a consistent white/gold/blue/steel style.
   Individually inspect all icons, including open-palm Catch and distinct combo/kick.
9. [x] Rework feedback within existing FX: compact precise jab, combo contact stops,
   heavy Stomp slam, readable reflected-projectile streak, returning guard pose,
   Reid's rising halos, World Cut's tall white-air front and gold rim, Erasure's
   steel/white fracture edge and collapsing spiral dust. No additional post chain.
10. [x] Full build, tests, resource checker, matrix/timeline checks and self-review.
    Publish changes and updated scope in the existing PR. Native checks are waived,
    not silently marked as passed.

## Verification ledger
- Java 17 Corretto; `./gradlew build --no-daemon`: passed. Core 1233 + flight 12
  tests (1245 total), zero failures, errors or skipped tests.
- New tests cover rigid transfer endpoints/determinants/orthogonality, strike
  return, cancellation into moving locomotion, separate FP arms, repeated-pass
  stability, FP vanilla/forearm grip matrices, yaw-covariant neck anchors,
  bent-elbow body states and reflection's return to guard.
- Existing suites retain all 14 handlers plus Lethal Chop, sword gates, 120/20-tick
  Reid phases, control cleanup, cut budgets and erasure immunity/lifecycle checks.
- Hero resource checker: 0 new problems; 4 known legacy problems.
- Existing software model previews inspected at authored keys. Additional sit,
  crouch and swim previews are geometry aids only: they do not reproduce all
  positional channels, player rotations, world rendering or gameplay.
- Offline SwiftShader/WebGL2 check: the existing fragment shader, translated from
  desktop GLSL 150 to GLSL ES 300 syntax, compiles and links. A synthetic 128x128
  framebuffer is byte-identical before/after for idle, Punch and Regulus settings.
  Erasure settings produce changed fracture-edge pixels as intended. This is not
  proof of desktop-driver, Forge, Oculus or shader-pack compatibility.
- Earlier `runClient` attempt failed at GLFW initialization; no world opened.
  Earlier dedicated attempt stopped at EULA; no full server startup was proven.
  The owner subsequently waived in-game checks. Neither attempt is called a pass.
- Shared changes are opt-in: HeroDefinition defaults have no held target; the old
  grab branch is preserved; normal HeroFx.flash callers retain power 1; Erasure's
  shader changes run only in its existing active branch. No other hero source,
  model, animation or flight source was rewritten.

## Remaining proof boundaries (not unimplemented code claims)
- Natural moving hip fit and ground/body clearance of the doubled sword, actual
  input/contact feel, player-victim network smoothness, FP/TP visuals and scene
  readability have not been observed in Minecraft. The owner waived this check.
- Dedicated startup, native desktop GLSL/shader packs and non-Reinhard gameplay
  regression checks were not performed in this completion pass.
- Stage 7 audio source/license exclusions remain unchanged: no CC-BY-NC recordings
  were added, and existing approved/reused cues remain. This pass does not claim
  replacement recordings or listening acceptance for the prior placeholders.

## Post-handoff blocker correction: core 1.33.1
- The owner found a real blocker in the delivered 1.33.0: ViltrumiteCoreClient
  tracked only eight hero slot edges. Reinhard has thirteen generic input slots,
  so Reid, Sheath Strike, Rain of Cuts, World Cut and World Erasure never reached
  the generic packet sender. The prior code-completion claim was incorrect.
- PR #35 already contained the slot-id map fix (964d4a7) and context seeding
  (fbd910c). This correction adapts those ideas without importing unrelated
  animation or gameplay changes from that branch.
- HeroKeyEdges replaces the fixed array and bounded loop in the production tick.
  It tracks slot IDs, samples every declared slot, and emits the same validated
  HeroInputC2SPacket. Dash Behind keeps its prediction packet; Lethal Chop keeps
  the shared kit path. No packet protocol or server gate changes.
- Hero, active page, active six-slot binding layout and local-player identity
  own the state. New contexts sample held keys without firing them. Page/binding
  changes and focus loss release channels previously sent to the same hero;
  hero/player changes never replay an old release into a new hero or connection.
  Logout and a null local player clear the cache. Reordered or removed IDs do not
  inherit another slot's state.
- Nine new regression tests cover thirteen real Reinhard mappings through the
  production packet codec, all declared Homelander/Regulus/Iron Man slot edges,
  press/hold/release, page and binding changes, hero switches, respawn/reconnect,
  focus/GUI loss, duplicate/reordered/removed IDs and production tick wiring.
  These are automated routing tests, not observed Minecraft key-event gameplay.
- Native gameplay remains waived by the owner. The previous JAR must be replaced
  with 1.33.1; the 1.33.0 build/test result did not detect this input blocker.
- Full Java 17 `./gradlew build --no-daemon` passed after the fix: core 1242 +
  flight 12 = 1254 tests, zero failures/errors/skips. `git diff --check` passed.
  Standalone output: `reforgedcodex-core-1.33.1.jar`.

## Second pass: owner gameplay findings (1.34 candidate, native acceptance pending)
Diagnosis, before edits:
- AnimationController.overlay writes all channels of each named bone, including
  missing position channels. Combat overwrites crouch offsets. The archive body
  pivots at the neck, while head/shoulders are independent roots; torso leaning
  cannot carry them. This is not a transition-duration problem.
- Sprint selects a different clip in one frame; airborne legs still use ground
  walk speed. StanceBlend.mix discards the third weight during fast mode cycling.
- Recovery keys contain phase changes and smooth the attack's incoming pose,
  delaying contact; HitStop subtracts elapsed hold only while active, then returns
  zero, producing a multi-tick forward jump at release.
- FP clip overlays use body bone names; armorRightArm is not right_arm. Only
  matching forearms sample; shoulder offsets and body clips are different layers.
- The sword's local X is the broad axis, Z the thin axis; the 60-degree X mount
  raises the long Y axis toward horizontal. Its grip is local (0,-3.5,0) px.
- Legs have no knees. Sky Kick can only rotate an entire rigid leg through an arc.
- Light Punch/Fist Combo remain HUD keys; ordinary melee uses an unrelated vanilla
  swing clock with immediate damage. Existing cooldowns are 2-60 seconds.
Implementation sequence:
1. Preserve authored channel masks, attach upper body structurally, blend gait and
   body states, and remove contact-delaying and hit-stop clock discontinuities.
2. Orient sheath using measured axes; solve the two-bone arm to the actual grip
   after all body-state blending, and keep attachments in the same hierarchy.
3. Retire R actions while retaining wire ordinals; move Knight chain to claimed
   LMB. Use a server contact clock for empty-hand ordinary attacks.
4. Add bounded hero-opt-in air/super-jump motion, articulated thrust kick with
   collision-aware approach and one server hit plus a 40-degree launch.
5. Short technical cooldowns, active scene/job guards, HUD/locales, migration,
   regression tests and full build. Preserve Erasure shader/audio and scene logic.


### Second-pass implementation report

#### Confirmed causes, not transition-duration guesses
- Absent clip channels erased inherited offsets; the neck-root body could not
  carry independent head/shoulder roots. Crouch was composed inconsistently.
- Sprint/air selected discrete poses. Interrupted stance blending discarded one
  of three weights. Incoming recovery smoothing delayed the authored contact.
- Subtracting the transient external HitStop counter jumped forward on release.
- The nominal item anchor was above the actual glove. Point-equality tests did
  not prove blade clearance, natural elbow placement or a neutral wrist.
- Compound quaternion/Euler conversion in bundled JOML did not invert the
  renderer's RzRyRx chain. Reinhard now uses a local tested matrix inverse.
- The old ordinary attack used vanilla immediate damage and a different visual
  clock. Retired R abilities and long saved cooldowns remained visible/active.

#### Existing approaches retained and adapted
- Regulus/Iron Man: separate view weights, a shared authoritative action clock,
  PoseKeys contacts, real-time outgoing recovery rather than a second timeline.
- Existing forearm hierarchy, PlayerGeoLayer and AnimationController sampling;
  the shared animation engine and other heroes' pose composition are unchanged.
- Original Viltrumite impact/grab pipeline remains the common runtime boundary.
  Regulus's directed jump principle becomes bounded hero-opt-in acceleration,
  not a replacement of shared flight or a copy of his entire movement system.

#### Requested ten areas
1. ReinhardLayers preserves channel ownership; three stance weights, continuous
   gait/air weights, outgoing recovery and stable tick sampling remove diagnosed
   composition/clock discontinuities. No global transition-duration workaround.
2. Earlier owner correction: whole weapon scale 0.574 (18% below 0.70), with a
   separate 0.78 longitudinal proportion correction, 35-degree thin-edge hip
   mount and visible belt carrier. The later drawn-blade clarification DOES NOT
   change that accepted mount, sheath, sheathed orientation or either scale.
3. Waist pivot Y12, structural head/shoulder children and one relative crouch
   layer replace independent torso offsets and disconnected upper-body roots.
4. Ordinary attack: ten ticks, one authoritative vanilla attack at tick four,
   readable anticipation/recovery and sharp contact; no global attack slowdown.
5. Actual glove pivots, surface contact for the resting left palm and an authored
   reid_grip anchor replace the nominal item offset. Free-blade poses have two
   distinct grip points at the guard and pommel. Forearm pronation keeps the
   wrist continuous with the sleeve instead of imposing a 90-degree wrist flip.
6. Restrained R is absent from active input, HUD availability and hints. Enum
   ordinals remain for packet/save compatibility; Holy Sword R stays Reid.
7. Knight LMB advances one hit per click, with one buffered next click. Damage,
   third-hit shockwave and the vanilla double-damage guard are server-owned.
8. Extra jumps add bounded forward acceleration, directional steering and a
   vertical-look fallback. Only Reinhard opts into the new motion contract.
9. Sky Kick extends an articulated knee, approaches in collision-checked steps,
   hits once at tick six and launches at 40 degrees above the attack direction.
10. Short technical cooldowns, old-save clamping and real scene/job admission
    guards agree with HUD. World Cut cannot overlap a still-running carve job;
    waiting for the job does not extend its damaging wave.

#### Drawn-blade clarification and visual self-review
- A blind universal quarter-turn was rejected: the camera held-item basis and
  the model-space authored anchor are different. That trial exposed the edge in
  World Cut/Erasure and introduced crouched leg intersections.
- The camera path rolls ONLY the free blade around local Y, after extraction and
  reversed on sheathing. The sheath frame and sheathed blade remain unchanged.
- The TP ready pose presents the broad native X/Y blade face toward the front,
  with the narrow Z profile toward the side. The drawn guard/hand poses move in
  front of the chest for a reachable two-hand grip, including both chop sides,
  Rain of Cuts, World Cut and World Erasure. Positions/scales of the sheath do
  not depend on that free-blade roll or the constrained hands.
- Draw's early extraction path and six-second duration stay intact. Only the
  final transfer settles into the new two-hand free-blade pose; sheathing uses
  the reverse path. The two palms do not share a point or a single-handed anchor.
- Dense tests use real baked cubes and production equipment matrices, not just
  a matching point or a screen rectangle. CPU previews are diagnostic images,
  not a recording from Minecraft. Rejected previews are not acceptance proof.

#### Files and subsystems
- client/reinhard: Renderer, Poses, Sword, Grip, Layers, Motion, StanceBlend,
  Recovery and FirstPersonPose; tools/assets rig and clip generators; generated
  TP/FP geometry and animation JSON; optional geometry export/preview utility.
- hero: opt-in mouse/vanilla-hit/jump contracts; Reinhard actions, rules, state,
  ability admission and cooldown/HUD flags; LightPunch, SkyKick, CloudStep;
  WorldCut and CarveJobs queue ownership. Flight sources are unchanged.
- All eleven core locale files, core version 1.34.0, design/plan, animation skill,
  durable project memory and regression tests. No new dependency or engine.
- World Erasure's scene, shader, fracture effects and sounds are not rewritten.
  Existing sound-license exclusions and placeholder limitations remain.

#### Validation boundary
- Final build/test totals and artifact digest are recorded after the last run.
- Resource checker: zero new problems, four known legacy problems.
- Native client probe failed GLFW initialization. Dedicated probe stopped at
  EULA; no EULA was accepted. Neither is a successful gameplay/server check.
- Still required in Minecraft: FP and TP motion/interruptions at real frame rates,
  both mouse hands, draw/return continuity, HUD/keys, blocked kick approaches,
  repeated jumps/landing, PvP latency/damage, occupied slots, other heroes and
  Oculus/shader packs. CPU geometry cannot certify visually perfect animation.


### Owner correction: drawn-blade orientation-only audit

Status: acceptance blocked by native blade shape; no production orientation/stance/scale/sheath edit in this audit.

The exact TP path is `ReinhardRenderer.onRenderPlayerPre` → `ReinhardSword.equipment` → `drawEquipment` → `drawFrame` → `AnimRenderer.render`. The immutable blade frame comes from the sampled `reid_grip` bone; the wrist solve does not recompute it. `drawFrame` resets the asset bones and multiplies that frame once. No later quarter-turn cancels the blade orientation.

The blade asset has longitudinal Y, width X and broad-face normal Z. In the owner's raised World Erasure pose (tick 15), the final broad normal is already 10 degrees from character-front Z. Native blade geometry is a narrow straight blade, not the anime reference's wide diamond. At the native Y=20 cross-section, current front/side widths are 2.598725/0.997833 model px. A mesh-only +10-degree Y roll aligns the broad plane exactly with the front: 2.638814/0.665236 px. A +90-degree roll swaps them to 0.997834/2.598724 px and makes the frontal silhouette worse. These are actual transformed quad intersections, not bounding boxes including the guard.

`ReinhardDiagnosticTest.exportFixedPoseAxialAudit` exports unchanged body and sheath meshes with three blade matrices: production BEFORE, +90-degree trial and exact face-alignment trial. Camera, pose, mesh, length, scale and hilt location stay fixed. The diagnostic comparison must not be labelled a successful AFTER. It proves that axial roll alone cannot create the requested broad diamond silhouette. Reshaping only the unsheathed blade would be a real design change; owner approval is required because the current instruction forbids changing its size. No such geometry change has been made.

Verification: preceding complete build passed 1268 core and 12 flight tests (1280 total), zero failures/errors/skips. The added fixed-pose diagnostic export also passed. CPU renders are not native Minecraft acceptance. The correction is not complete and must not be reported as accepted.

Final audit verification: full `build` passed with 1269 core + 12 flight tests (1281 total), no failures/errors/skips; `git diff --check` passed. A 0.01-degree axial sweep over 180 degrees confirms the maximum frontal mid-blade cross-section at +10 degrees: 2.638814 model px versus 2.598725 before (only +1.54%). No production transform, stance, sheath, asset shape or scale changed in this audit.


### Final owner-selected visual: restore the bottom +90-degree audit variant

The owner cancelled the +35% drawn-blade silhouette, then explicitly circled the bottom axial-audit image: +90 degrees about native longitudinal Y. This overrides the previous broad-face-front direction. All silhouette changes were removed: generated drawn asset, optional polygon loader, asset selector, extra tests and tools. Original `blade.geo.json` and `sheath.geo.json` SHA256 checks match the pre-silhouette baseline. No new blade geometry or scale change remains.

Core 1.34.1 rolls only the TP final free-blade matrix after the existing grip solve. The native hilt points lie on Y and do not translate. Sheath frame is captured before that roll, Sheath Strike is excluded, and sheathed roll is zero. The unchanged draw/sheathe roll curve is reused; body poses, animation keys, hand constraints and FP transforms stay unchanged. The final production export must match the frozen +90-degree trial at World Erasure tick 15, including identical body and sheath vertices. Verification follows below.

Final restored-roll verification: `./gradlew build --no-daemon` passed with 1269 core + 12 flight tests (1281 total), zero failures/errors/skips. Production `world_erasure_15.json` equals the frozen selected +90-degree trial byte-for-byte as parsed JSON, including every body, sheath and blade vertex. Original blade/sheath asset checksums and `git diff --check` passed. The head collision test now checks each actual rotated head/hair cube, like the existing torso checks: the combined conservative head envelope reported false positives in empty space between rotated cubes after the selected roll. All sampled draw/sheathe/attack frames, standing and crouched, pass actual mesh clearance without moving any pose. Version assertion updated to 1.34.1. The attached comparison is a CPU geometry diagnostic, not a Minecraft screenshot; native acceptance remains outstanding as described above.

### Owner follow-up: bring the sheath closer without changing its angle (1.34.2)

Only the body-local mount translation changes: X 6.8 → 6.2 and Z -2 → -1 model px; height, tilt, rotation order, scale, sheath geometry and free-blade roll remain unchanged. A purely lateral move intersected the left thigh during World Cut. Measuring the two local horizontal axes allowed a closer mount without that collision. The authored grip's hip endpoints use the same translation so starting extraction and finishing sheathing do not jump; no rotation keys or drawn attack poses changed.

Verification: complete `build` passed, 1271 core + 12 flight tests (1283 total), zero failures/errors/skips. New tests preserve every mount basis vector/scale and assert exact assembled weapon continuity at extraction start. Existing sampled hand/head/torso/leg clearance, sheathed palm contact and gait/crouch tests pass. The control frame's drawn blade vertices are unchanged; every sheath vertex has the same displacement (no rotation or resize). `git diff --check` passed. The before/after comparison uses the same cameras and actual exported geometry, not native Minecraft; native acceptance remains pending.

### Owner follow-up: continuous animation playback (core 1.35.2)

Base: snapshot hotfix 2df721d, not main. Scope: Reinhard client poses only.

1. Add regression tests before implementation: gait velocity between keys and
   at the loop seam; snap-to-guard continuity; Reid boundaries in both views;
   ground/air weights and repeated render passes.
2. Bake walk/run at 80 linear samples per cycle. Preserve the original cosine
   trajectory and bend amplitudes. Do not restart an ease at each sample.
3. Compose catch/reflect snaps over the matching ready guard, so their outgoing
   weight reveals guard rather than idle. Use the actual action duration for
   the window envelope, not the shorter snap clip duration. Preserve every
   combat key and server contact time. Share this composition with FP elbows.
4. Fade Reid body channels into/out of the live underlying pose at the sheathed
   boundary. Keep reid_grip at full weight so weapon paths and grip stay exact.
   Return FP shoulder offsets to neutral before drawing finishes. Ease them
   into sheathing. Apply the same boundary policy to FP elbows.
5. Retain the interpolated gait in the air and cross-fade its weight with the
   existing real-time motion weights. Do not hard-cut it with onGround.
6. Keep shared animation code, other heroes, assets, gameplay, effects, sound,
   protocol and flight unchanged. Bump only core patch version.
7. Run targeted and full tests, resource checks, build and native client probe.
   Record environmental blockers honestly. Publish a separate PR with manual
   FP/TP, shaders, occupied-hands and frame-rate acceptance still explicit.

Verification: all four new regression methods failed on the original code
(1292 core tests, exactly four failures). The first fix run found signed-zero
camera offsets at a neutral boundary; reid now returns the canonical NONE.
Final Java 17 `build` passed: 1296 core + 12 flight tests, zero failures, errors
or skipped tests. Eight continuity regressions cover gait speed/position,
TP and FP snap/guard composition, camera Reid boundaries, unweighted grip,
actual window duration, and complementary motion weights at four frame rates.
Resource checker: zero new problems, four known legacy problems.

Only walk/run JSON clips changed. Every combat clip is identical to the base.
Blade/sheath assets, selected free-blade roll, six-second draw, protocol 4,
gameplay contacts, shared engine and flight sources are unchanged. Core patch
1.35.2 JAR includes the generated clips, metadata and attribution files.
SHA256: 514445133fc43aea610938a9436a7e47c01c0248807f6bdb932ca278d8613b41.

Native `:viltrumitecore:runClient` reached Forge's early graphics initialization,
then failed `glfwInit failed` with no display. The blocked probe was stopped.
No world, gameplay, native FPS, GPU, shader or multiplayer visual acceptance
was performed. Still check both views, all modes, crouch, occupied slots,
draw/sheath interruptions, catch/reflect, takeoff/landing, 30/60/144+ FPS and
Oculus/shadow passes in Minecraft. CPU tests do not certify visual perfection.

## Combat fluidity follow-up — 2026-10-20

Player rejected 1.35.2: locomotion fixes did not address punch/sword feel.
1. Add failing production-path regressions for authored action ownership of FP
   swing and for delivery duration. Add clock and continuous-sampler unit tests.
2. Extend existing ActionClock with an opt-in monotonic, bounded render sampler;
   keep legacy tick/time callers unchanged. Reinhard TP/FP/elbows/weapon share
   one per-player sample. Reset on action/phase/restart and cap extrapolation.
3. Extend existing animation sampler/PoseKeys with opt-in monotone cubic
   interpolation (nonuniform keys, exact contacts, no overshoot). Only Reinhard
   strikes opt in; legacy heroes, gait and protected draw scenes stay unchanged.
4. Widen punch/hook/hammer delivery and rapid-cut delivery before their existing
   contact ticks; retain server durations, damage, blade geometry and roll.
5. Reuse FirstPersonArm's view-space shoulder pivot as Iron Man does. Apply once
   outside the vanilla arm frame, covering both Forge-cancelled and normal item
   paths. Remove competing local Euler arm transform. Suppress vanilla swing
   only while the authored action owns that arm; keep idle/mining unchanged.
6. Run regression/full build/resource checks. Attempt native client with a
   software display; report actual verification limits, not visual perfection.
7. Bump core 1.35.3, update shared-sampler contract/notes, separate PR and JAR.

Additional production-path finding during the combat pass: a 240 FPS scan of
FINAL constrained arm transforms failed on light_punch left_forearm frame 13:
173.3269 degrees between adjacent samples. `paired` negated the directed handle
axis whenever a dot product changed sign. Removed that per-frame sign choice;
the weapon's authored direction now stays directed. The next scan exposed a
69.1809-degree left-wrist jump in sheath_strike: a hard +/-35 degree shortest-arc
wrist clamp changed branch at 180 degrees. The bounded wrist correction now
smoothly fades to neutral near that antipode. Neither fix moves the grip point,
weapon mesh/path, selected blade roll, authoritative contacts or damage. Extend
continuity coverage to final solved bones, draw/sheath and all sword scenes;
retain the existing full-mesh collision and distinct-palm gates unchanged.
The expanded scan also caught right forearm pronation being enabled at full
weight as soon as twoHandWeight became merely positive. Position ownership and
pronation weight are now separate, with the existing one-hand wrist frame below
that blend. Partial grip ownership blends the reachable palm target BEFORE the
existing constraint solve, rather than selecting independent shortest-angle
branches for joints afterward. The elbow pole stays outward/front (not along
the usual palm ray), blends from the authored elbow, and the geometric seed
preserves authored shoulder/forearm twist. Experimental alternative solvers
were discarded: the existing damped least-squares core remains. Full final-bone
240 FPS scan now covers ordinary jab, assembled strike, both chops, cut rain,
draw, sheathe and world-cut scenes, plus the wrist antipode regression.


Combat follow-up verification: full Java17 build/reobf succeeded, 1307 core +
12 flight = 1319 JVM tests, zero failures/errors/skips. Both full-mesh/head
gates and the final-bone continuity scan pass without weakening assertions.
The leading elbow pole additionally points down: a symmetric outward pole
was continuous but clipped the head on overhead cuts. Generator deterministic;
resource checker zero new/four known legacy problems. JAR1.35.3 contains new
classes/mixin and byte-identical generated assets. SHA256:
7aab8e983927be5caea728c0bf43fc30fa30a48cee4b46b14ba52e02636f87bf.

Final native probe: actual Forge47.3.0/Minecraft1.20.1 world via Xvfb/llvmpipe,
37 FPS observed at render distance2. Recorded drawn sword attacks in FP/TP
and ordinary jab/knight combo separately. Source clips and final constraints
were the release candidate. Do not confuse with earlier native recordings
before the final grip corrections. Native 60/144 FPS, shaders and multiplayer
remain unverified; CPU sampling at these rates is not native visual acceptance.

Final runClient saved the world and quit normally: exit0 / BUILD SUCCESSFUL
(7m11s), no animation/mixin render exceptions. Audio/narrator/offline Realms
and the existing license-path warning remain environment/legacy limitations.
