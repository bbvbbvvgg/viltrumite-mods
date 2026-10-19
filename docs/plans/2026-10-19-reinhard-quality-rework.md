# Reinhard quality rework

Base: `feat/reinhard-stage7` at `add8a0d`. Scope: Reinhard only. The approved
2026-10-11 design stays authoritative; the new request overrides grab input
(Z acquires, RMB throws). No new animation engine or shader chain.

## Audit findings
- `ReinhardRenderer` calls stateless seek/overlay with unbounded time. Those
  methods sample the last key, not a loop. Walk and breathing stop after a cycle.
- The replacement body has no vanilla attack overlay. A vanilla LMB swing is
  invisible; the custom R light punch is a separate server timeline.
- Grab uses repeated Z and server teleports every tick. Its anchor has no lateral
  hand offset. Compare the legacy grab's hand positioning, but keep ControlManager.
- Reid is scaled to 0.35 and tilted 50 degrees to avoid the ground. Raising the
  mouth and changing its orientation must accompany the requested doubled scale.
- Current action overlays abruptly replace the underlying locomotion. Sky Kick
  jumps from a chambered leg to full extension exactly at contact.
- Regulus uses one bounded action clock in both views and independent weights.
  Iron Man uses full-body anticipation/contact/recovery, not an isolated arm.

## Implementation status
**Draft / not accepted as the full requested rework.** Code and art are published
for review/testing, not as a release-quality claim. The remaining native checks
and unresolved weapon/carry transitions below are blockers, not optional polish.

## Work sequence
1. [x] Regression tests for loop sampling, bounded time, contact continuity, elbow/item anchor, behind-back hand and input. Post-cancel recovery clock remains unimplemented.
2. [x] Fix Reinhard-only loop timing; retain shared controller behaviour for other heroes.
3. [ ] Rebuild stance/locomotion/action clips with eased keys and contact-aligned
   timing. Add an ordinary melee swing to the replacement renderer. Widen FP arms.
4. [ ] Refit Reid on hip and in hand at 2x current scale. Check draw/sheathe paths.
5. [ ] Change grab to Z acquire / RMB throw; match the hand-side anchor and preserve
   ownership, release, control and cooldown rules. Audit all 14 ability paths.
6. [x] Author 16 native-64px white/gold/blue/steel ability icons; individually inspect the PNGs. In-game HUD acceptance is still pending.
7. [ ] Build, tests, resource checker, dedicated startup, first/third-person client
   testing, input/contact verification and non-Reinhard regression testing.
8. [ ] Code and icons pushed in separate commits; publish documentation and draft
   PR against the source branch with exact proof
   boundaries. Do not mark full rework accepted without native visual/play testing.

## Native acceptance matrix (not yet run)
- Every mode: idle, walk, sprint, strafe, crouch, jump, transition, LMB, both main arms.
- Restrained R/Y/Z: quick non-destructive contact, dash prediction, catch window.
- Knight R/Y/Z/V/B/N: 3-hit wave, vertical kick with visible contact, carry/throw,
  safe behind-dash refusal, stomp leg/contact, reflect to shooter.
- Holy R/Y/Z/V/B/N: 120t draw + 20t sheathe; Chop in both sword states; sheathed-only
  strike; drawn-only rain/cut/erasure. Auto-sheathe and mode exit.
- Reid: standing/moving/crouch, full draw path, hand grip, body/ground clipping.
- World Cut and Erasure: real GPU shader, other player/dimension, cancellation,
  pause/unload/reload, FPS/TPS, mobGriefing/bedrock and divine/absolute immunity.
- Regulus, Iron Man, Homelander: no changed animations, input or Chop visuals.

## Implemented candidate
- New authored idle/action/gait clips with real forearms, Knight back-hand hold,
  sprint selection, visible vanilla melee overlay and eased action entry/recovery.
  Crouch/sit/vanilla crawl recipes are retained; their full redesign is not done.
- FP poses now use existing PoseKeys anticipation/contact/recovery; arms 13% wider.
  Both views and cinematic arms use identical bounded HitStop-adjusted server time.
- Z acquires/keeps the grab; RMB throws. Server/client mouse claims agree, and
  the server flags now include GRAB_HOLDING. The hand-side anchor is corrected.
- Reid scale 0.35 → 0.70; candidate hip mount; extraction axis corrected. Static
  sheath minimum Y at X tilt 56 was -0.959 px; tilt 60 gives +0.740 px clearance.
  This is a geometric candidate, **not** proof of a natural moving hip fit.
- Sheath Strike moves the assembled weapon to the hand in FP/TP without leaving
  a duplicate on the hip. Slot item visuals hide without changing inventory.
- New 16-icon set. Catch uses an open palm, combo has three fist echoes, and kick
  has a rising boot/target, to distinguish abilities rather than repeat one fist.

## Verification ledger
- Java 17 (Corretto), `./gradlew build --no-daemon`: passed after the source changes.
  Latest complete suite: core 1225 + flight 12 tests, zero failures/errors/skips.
  Repeated after the 1.32.0 version/test contract bump; final jar resource bytes
  match the committed base64 icons, and mods.toml declares 1.32.0.
- The stale Stage-5 animation digest assertion was replaced because the owner
  explicitly ordered replacing old Reinhard animations. Replacement checks retain
  every action clip, 120/20t Reid lengths and continuous toss → kick contact.
- Hero resource checker: 0 new problems; 4 known legacy problems (not waived/new).
- Software model inspection: Knight idle, light-punch contact, kick contact, stomp,
  Reid draw and World Cut lift; front/side/back. A front-of-body Knight hand was
  found and fixed; wrist placement has an engine-matrix regression test. These
  previews omit world rendering and are **not** gameplay acceptance.
- All 16 icons inspected individually, nearest-neighbour enlarged from native64.
  Rain's right edge was brought inward after inspection. Native HUD size untested.
- `runClient`: attempted, failed at Forge graphics initialization (`glfwInit failed`).
  No world opened and no gameplay/GPU/effects test was performed.
- `:viltrumitecore:runServer`: reached EULA requirement and exited. EULA was not
  accepted on behalf of the owner. This does **not** prove a full server startup.
- Shared animation engine, other-hero sources/assets, flight sources are unchanged.
  Their test suite passing is not a substitute for native regression testing.

## Blocking work still required before acceptance
- Native FP/TP checks of every input, mode change, contact, moving stance and VFX.
- Finish/verify continuous blade-to-hand and sheath-to-hand transfer (the current
  draw/strike attachment changes are not guaranteed seamless); refine the large
  sword's moving hip angle/ground clearance against the player's intended look.
- Remove/validate player-victim grab jitter: existing per-tick teleport remains.
  A lateral offset and RMB finish alone do not solve this networking behavior.
- Finish crouch/sit/swim/first-person elbow and offhand transitions, including
  interruption and return to locomotion. No post-cancel action recovery clock yet.
- Native audit of all 14 handlers plus Lethal Chop, six-second draw, World Cut and
  World Erasure. Existing world-scene VFX and sounds were retained, not rebuilt or
  claimed fixed. Audio placeholders/license questions from Stage 7 remain open.
- Native non-Reinhard regression and a complete dedicated-server startup.
- Only after those checks may this be called the full quality rework. Core 1.32.0
  is a **test artifact** for this branch, not a release-quality acceptance.

