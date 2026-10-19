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
