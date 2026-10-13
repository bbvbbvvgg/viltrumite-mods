# Reinhard Stage 6 — independent Senior Code Review

## Scope and evidence

- Review seat: the original single independent review, recovered after interruption; no new reviewer or subagent.
- Checkout: `/data/viltrumite-mods`, `feat/reinhard-stage6`.
- Base: `f93f27fa2634268c9686250f4ad2d0892636f108`.
- Committed HEAD: `6988c821926f528406d2bcdaf26f97723a5c634a`, plus the current uncommitted Task 8 changes and untracked tests in the review inventory. HEAD and branch matched the package.
- Read the reviewer instructions, package, AGENTS, full Stage 6 plan and overview, binding design sections, full animation-system skill, project memory, and every progress-journal Ruling/cost. Read the snapshot selectively and the central current server/client implementations, tests, lifecycle consumers, and relevant existing adapters. Inspected cached, mapped Forge/Minecraft 1.20.1 bytecode for logout/save ordering, gravity serialization, container closure/overflow, XP setters, and dragon-fight bookkeeping.
- The parent's existing build evidence is GREEN: 1166 tests, zero failures/errors/skips; Reinhard 452; selected core 527 plus flight 12; checker 0 new / 4 known. The selected-suite count file reports 539 total. Build/suite logs end successfully. These are supplied/existing gates, not tests run by this reviewer.
- READ-ONLY respected: no repository/index/HEAD/branch changes, checkout, commit, push, Gradle/test launch, game/server launch, or subagent. Only this report was written.
- This is not a claim that every changed byte received equal inspection. The recovery request required immediate reporting; remaining source/asset and runtime limitations are explicitly listed under **Declined to judge / incomplete coverage**.

References below are relative to the checkout. `core/` means `viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/`; `test/` means `viltrumitecore/src/test/java/dev/baranhan/viltrumitecore/`.

## Strengths

1. **The central server timeline is clear and immutable.** `core/hero/reinhard/ability/WorldErasure.java:46–70,95–111,131–142` spends cooldown only after a successful pause, fixes the verdict at cast, resolves the original target UUID in the original level, makes one removal attempt, and ends at 150. A target absent at removal does not get a delayed second erasure.
2. **Refresh is genuinely expiry-only.** `core/hero/control/ControlManager.java:187–200` matches caster, effect, and kind and changes only `expiresAt`; it does not reconstruct a record, re-anchor, capture flags, or dirty structural sync. The captured-record tests exercise that actual helper and manager method, not a second implementation.
3. **Same-body cleanup has a useful shared sequencing seam.** `core/hero/BodyLoss.java:23–38` and `core/hero/HeroEvents.java:308–363` separate body loss from return. The four stateful hero implementations use their existing death-copy policies after world-side cleanup, preserve intentional persistent fields, sweep target/control/projectile ownership, and clear owner sections. Regulus carrier-level bookkeeping is explicitly cleared.
4. **The END queue and persistent pending-strip marker address real hazards.** `core/hero/ServerEndTasks.java:17–25` snapshot-drains work and isolates failing tasks. `core/hero/reinhard/limbo/Limbo.java:46–67,75–100` marks isolation before transfer, persists remaining online ticks and pending strip, and resumes rather than restarting the timer. Inventory/XP erasure does not access the ender chest.
5. **The dragon deviation is technically justified and prominently documented.** The inspected vanilla `EndDragonFight.setDragonKilled` really creates portal/gateway/egg terrain. The narrow accessors avoid that, and vanilla `saveData` includes `needsStateScanning`, `dragonKilled`, and `previouslyKilled`, so the chosen flags are not merely an unsaved current-session patch. The missing first-kill exit remains an acknowledged product cost, not an invisible change.
6. **The client architecture stays within the existing systems.** Five zero-default uniforms extend the existing dash program; the erasure block is inactive at zero and does not alter the old idle branch. The actual post configuration is one dash pass followed by two blits, not three applications of the erasure transform. Scene age is independent of dimension game time, repeated catch-up is monotonic, and the supplied `erase` bit—not current Lion's Heart state—controls silhouette reformation. Both physical-arm cinematic routes and held-item suppression are integrated into the existing renderer.

## Issues

### Critical — Must fix

**None established in the inspected evidence.** This does not certify the unrun native/runtime gates.

### Important — Should fix before merge

#### I1. A refused control is marked paused, but has no server-side positional freeze

**Files:** `core/hero/control/WorldPause.java:119–129,161–168`; `core/hero/control/ControlManager.java:160–184,658–689`; existing conflicting movement adapter `core/mixin/PlayerEntityCoreMixin.java:1126–1182`.

**Problem:** `Session.sync` puts every candidate UUID in `paused` regardless of the result of acquisition. `LiveBackend.freeze` attempts refresh/acquire and ignores refusal. The actual server anchor/zero-motion implementation exists only in an acquired FREEZE/STASIS record. A foreign PULL, a legacy `ViltrumiteGrabbed` owner, or a hero-policy refusal therefore receives input/action/protection marking but no independent positional pause.

**Concrete effect:** The legacy carry continues to run even when the grabber's FREEZE clears `tryingToGrab`: an already-held `currentTarget` is still repositioned to the grabber's hand, inherits motion, and can grind terrain. Turning the frozen grabber's view changes that hand position. Thus a supposedly frozen player can still be moved during the cinematic. PULL and policy-refusal cases likewise have no authoritative pause anchor; zeroing the ordinary client's input is not a server movement guarantee.

**Why Important:** The binding vision freezes every non-caster, not only those whose current control owner happens to accept a new record. Keeping foreign ownership is the right constraint, but retaining ownership is not proof that the victim is frozen.

**Fix:** Add a hero-neutral pause-position overlay or equivalent authoritative movement suspension for refused acquisitions. Preserve the foreign record/effect/kind and its saved flags; do not refresh, replace, or release it as if owned by this pause. Suspend conflicting PULL/legacy carry movement while paused, and restore only pause-owned state on end. Handle policy refusals without silently exempting them from the global cinematic freeze.

**Regression:** Seed real refusal categories (foreign PULL, foreign STASIS/FREEZE, Lion's Heart/control-policy rejection, legacy carry). Advance a movement-producing foreign adapter and change the grabber's look while the pause is active. Assert the non-caster's position stays fixed, no carry grind runs, and the foreign ownership/flags survive every end reason unchanged. The present `WorldPauseTest.foreignControlNotOverridden` (`test/hero/control/WorldPauseTest.java:20,26–35`) tests ownership in a map, not movement.

#### I2. A participant logging out during the pause can persist `NoGravity=true` across a server restart

**Files:** `core/hero/HeroEvents.java:228–237`; `core/hero/control/WorldPause.java:125–129,136–140`; `core/hero/control/ControlManager.java:50–59,250–254,464–473,483–493,658–662`.

**Problem:** The logout hook ends the pause only if the departing player is its owner; it does not restore a departing participant's pause-owned target flags before vanilla saves that player. Restoration after disappearance is held only in the static `PENDING_TARGET_RESTORE` map. Neither the five-tick expiry nor the watchdog survives a new JVM.

**Verified trigger:** Mapped Forge `PlayerList.remove` fires `PlayerLoggedOutEvent` at bytecode offset 1, calls `save` at 18, and removes the player from its level afterward. Vanilla entity save/load serializes `NoGravity`. A normal paused participant can therefore log out with the temporary flag saved, the later pause release can only defer the original value in process memory, and a clean server restart before that participant rejoins loses the restore. This does not require a crash.

**Effect:** A seven-and-a-half-second cinematic can leave the returning player with permanently disabled gravity. The same nonpersistent ownership pattern also cannot self-heal saved temporary mob flags after an abnormal restart.

**Fix:** Release/restore only this pause's participant record while the logout event still has a resolvable player, before the vanilla save. For unload/save/restart resilience, persist an original-flag recovery marker or equivalent lease, consume it on join, and distinguish it from intentional limbo gravity and foreign control ownership. Do not blindly set every departing player's gravity false.

**Regression:** Acquire on a player whose natural flag is false; simulate the actual event → save → removal ordering; stop and recreate the coordinator/manager process state; load that serialized player and join. Assert natural gravity is restored without the old static map. Also test an originally true flag, foreign ownership, and an isolated limbo player to avoid fixing this by breaking those cases.

#### I3. Closing a full container before stripping inventory can drop items that should be erased

**Files:** `core/hero/reinhard/limbo/Limbo.java:55–60,145–147`.

**Problem:** `resume` calls `closeContainer()` before inventory clearing, and the live port is ordinary `ServerPlayer.closeContainer()`. Vanilla menu removal returns the cursor stack to inventory; `Inventory.placeItemBackInInventory` invokes `Player.drop` when there is no room. Player-owned crafting/menu inputs can also be returned during removal. These are ordinary close callbacks, not death drops, so avoiding `die()` does not prevent them.

**Verified effect:** With a full inventory and a nonmergeable item on the cursor when tick 90 arrives, normal close emits an ItemEntity in the original dimension before the subsequent clear. Other players can recover it. The no-drops/inventory-erased contract fails even though the current fake's drop counter stays zero.

**Fix:** Make erase-specific container teardown consume the carried stack and player-owned transient menu inputs without invoking overflow-drop semantics. Arrange clearing/closure so returned inputs cannot survive or overflow; preserve external chest/ender storage rather than clearing all slots indiscriminately. Resume on logout/restart must use the same no-drop teardown.

**Regression:** Use a vanilla-faithful close/overflow fixture: full inventory, occupied cursor, and player crafting inputs. Assert zero drop calls/entities and no surviving player-owned stacks on both initial transfer and pending-strip resume; assert ender chest and unrelated external container contents remain intact. `test/hero/reinhard/LimboTest.java:12–18,27–42` currently makes `closeContainer` record only a string, so it cannot expose this adapter failure.

#### I4. The client pause watchdog advances during a real game pause and can destroy a still-valid scene

**Files:** `core/client/hero/WorldPauseClient.java:31,41–43`; compare `core/client/reinhard/erasure/ErasureScene.java:102–109` and `core/client/reinhard/limbo/LimboOverlay.java:71–76`.

**Problem:** The erasure scene and limbo text clocks explicitly stop when `Minecraft.isPaused()`, but the pause watchdog decrements on every client END tick without that check. Its expiry calls all end listeners, including clearing the erasure scene.

**Effect:** In an integrated/single-player game, opening the pause menu longer than the remaining watchdog duration clears a cinematic whose server timeline is also paused. Closing the menu resumes a live server cast with no client scene/input-pause state; there is no ordinary catch-up resend merely for leaving the menu. A short menu visit also consumes watchdog time out of step with the authoritative cast. This is not the intentional multiplayer case where Escape does not pause the server.

**Fix:** Age this watchdog on the same unpaused local tick basis as the scene, or otherwise separate local game pause from a missing-authoritative-end timeout. Keep the existing bounded cleanup when the client/world is actually running and a packet is lost.

**Regression:** Start at an intermediate scene age, simulate more than 190 client ticks with the game-pause flag true, then resume. Assert no scene end callback during the pause and that both remaining counters resume consistently. In a separate running-client case, assert the watchdog still clears a missing END. Native menu/event behavior remains an unrun manual confirmation, not a claimed reproduction.

### Minor — Nice to have / ledger if deferred

#### M1. Legacy same-body reset is narrower than the stated fresh-entity contract

**Files:** `core/hero/LegacyKit.java:48–61`; legacy retained fields `core/mixin/PlayerEntityCoreMixin.java:71–89,113–147,415–437`; test setup `test/hero/ErasedLifecycleTest.java:70–85,99–164`.

The reset clears the active timers/toggles but does not reset other clone-dropped kit values such as punch strength/side/type metadata and cached hand positions. The fake seeds only a few legacy flags, so its “fresh” result does not establish the full same-entity invariant. No immediate gameplay failure from these residual values was established; most are overwritten before the next action, hence Minor rather than promoting spec wording into severity.

**Fix/test:** Enumerate the legacy clone-dropped fields, seed their nondefault values in a read-back-capable fixture, and explicitly reset the transient ones while retaining chassis/session/cooldowns that are intentionally persistent. Include the generic legacy fields for Homelander and Reinhard as well as the VILTRUMITE test case.

#### M2. The development pause command's duration is `ticks + 40`, not the requested ticks

**Files:** `core/command/CoreCommands.java:82–86`; `core/hero/control/WorldPause.java:47–54,131–134`.

The command installs no listener, so `/viltrumite pause 100` has no normal completion at 100; only the watchdog ends it at 140. World Erasure itself has the listener that ends at 150, so this is a development-tool discrepancy, not an erasure timing defect.

**Fix/test:** Give listener-free development pauses a normal DONE at the requested duration, or explicitly label the argument as a watchdog budget. Add an exact-boundary command/session test, keeping the separate +40 emergency watchdog behavior for listener-owned cinematics.

## Explicit Review Focus coverage

| Focus | Source-level assessment and proof boundary |
|---|---|
| **1. Caster death/logout/dimension/hero change/reload; every exit unfreezes and ends scenes; target stays before 90** | Read the actual event/registry hooks and listener end path (`HeroEvents:85,231,369,432,445`; `HeroRegistry:94`; `WorldPause:47–74`; `WorldErasure:54–70,104–117`). They route owner interruptions to end and prevent later removal; UUID-matched client END clears the scene. The fake tests cover interruption at 60 and direct session end, not the actual event ordering at 89/90/149/150. **Not fully satisfied overall:** refused movement and participant logout/restart are I1/I2; real game-pause client cleanup is I4. Actual Forge and network execution remains unrun. |
| **2. Refresh does not recapture flags and refuses foreign caster/effect/kind** | **Supported by actual inspected implementation** (`ControlManager:187–200,637–651`) and captured-record/manager tests. No re-anchor/apply/restore/dirty mutation in refresh. Flag application/restoration is separately inspected; this positive conclusion does not cure I2's persistence gap. |
| **3. ERASED same entity; five heroes become transiently fresh after return** | Read all five real cleanup implementations, state replacement/death-copy policies, BodyLoss sequencing, generic reset, control/projectile/grab cleanup and shared return hook. The stateful hero copies are a strong implementation choice. Tests call real state helpers but fake world-side behavior; they do not execute actual five-hero adapters through 600 ticks. **Partial:** legacy residual fields are M1; native actual cleanup/return is not proven. No finding was invented for intentional death-surviving domes/jobs or retained Phoenix/session/cooldowns. |
| **4. Limbo shared overworld spawn, health, no message/DEATHS/drops, remaining-time persistence** | Shared-spawn path, no bed/anchor path, safe search/border/collision checks, online-only persisted timer, pending strip, travel gate, XP setters and same-body return were inspected. No `die()`/death-stat operation on this path was observed. **Not satisfied as a no-drop guarantee:** I3 demonstrates a vanilla close overflow. Real serialized restart/old-world dimension loading, safe return in a live world, and live death/stat/message observations remain unrun. I2 is a separate pause-participant persistence defect, not a claim that the limbo tick tag itself is absent. |
| **5. Idle shader neutral; Lion's Heart survives with spent cooldown and frozen “stays”** | Zero defaults, ZERO sampler, old early-out, shader override, server verdict assignment before pause, one attempted removal, packet bit, and client shape reformation were inspected (`ErasureUniforms:5–16`; fragment shader:95–105,318–344`; `WorldErasure:95–111`; `ErasureScene:90–99,125–130`). **Supported statically:** turning Lion's Heart off at 50 cannot change the frozen server/client verdict. Cooldown is spent for an accepted stays cast. GPU idle pixel equality/GLSL/native rendering was not run and is not asserted. |

## Recommendations for the parent's single RED → GREEN pass

1. Fix I1–I4 with tests at the actual seams above, not just additional `contains(...)` assertions or fakes whose methods assume the desired result.
2. Preserve matching-record ownership and the accepted ERASED death-copy policies; do not solve the pause by stealing controls or solve persistence by indiscriminately clearing gravity.
3. Use adversarial event sequences: 89/90 interruption boundaries, participant logout → vanilla save → fresh process, full-container closure, real game pause, and limbo return while another pause is active. The current pure sequence tests are useful but do not compose all of these adapters.
4. Keep all native/manual checkboxes unchecked. The green build and jar inspection are compilation/packaging evidence, not runtime acceptance. No second reviewer or re-review is assumed or requested here.

## Declined to judge / incomplete coverage

These are explicit limitations, not passes and not excuses for the findings above. There is no additional silent product-scope exclusion.

- **Actual Forge event ordering/network execution at exact cast ticks, including native pause/limbo overlap and gravity restoration:** traced statically and identified tests to add; not executed because the user forbids game/server/tests in this review. I1–I4 are not declined on that basis.
- **Dedicated-server startup, common-side class loading, common/client Mixin application and refmap validity:** compilation and source guards are not a startup proof; launch explicitly forbidden.
- **Real player/chunk NBT round-trip, clean restart after limbo logout, old-world data-dimension availability, and live remaining-time resume:** persistent tag/bytecode paths read; no real world or server was started. A CompoundTag fake is not full acceptance.
- **Live safe-spawn placement and health/message/DEATHS/drop observation in normal/obstructed worlds:** return logic read and I3's close/drop behavior established from bytecode; no native return was exercised. Invalid shared-spawn repair remains the disclosed owner responsibility rather than a terrain-platform change.
- **GPU GLSL compilation, F3+T behavior, rendered idle pixel equality, crack/shard direction/readability, first/third-person blade/arm placement, and unchanged legacy visual output:** shader/renderer integration read, but no native rendering or screenshots permitted.
- **Live audio duck/exemption interactions, logical heartbeat cadence, return flash presentation, chat/F1/menu rendering and sound-resource reload:** central scene/HUD/heartbeat/end code read; native playback and full shared sound implementation behavior were not established in this recovered pass.
- **Oculus/shader-pack ordering, optional/modded renderer compatibility and sonic-priority behavior in an actual combined client:** source-level intended priority was read; no native compatibility run. A caught RuntimeException is not proof of compatibility with arbitrary renderers.
- **Pixel-exact model cutout silhouettes and model-exact dust:** intentionally accepted solid-mesh/metadata approximation; not judged against a texture/readback fidelity requirement the chosen design deliberately does not implement. Unusual renderer behavior remains manual.
- **Measured TPS/FPS and hostile-scan/spawn-search scaling under large-server loads:** not benchmarked; pure geometry and source inspection cannot supply those measurements.
- **First-erased-dragon exit availability, crystal respawn playthrough and live bossbar disappearance:** bytecode supports the quiet bookkeeping; no native playthrough. The missing first portal/gateway/egg is an explicit unchanged-terrain tradeoff, not treated as an undisclosed regression or “fixed” by changing blocks.
- **Full generated-asset and locale review:** the shader uniforms/post shape and gameplay-facing rendering changes were inspected, but not every generated clip keyframe, icon pixel, sound JSON entry, or all eleven locale texts. Parent generator/idempotence/jar checks are reported as parent evidence, not independently repeated.
- **Detailed bodies of the two untracked Task 8 tests, all remaining ancillary changed tests/docs, full packet-registration table, and every unchanged legacy helper:** inventoried as part of the requested working-tree scope, but not all loaded before the interruption/recovery instruction to report immediately. They receive no blanket correctness endorsement. Central live packet handlers, generic lifecycle/control gates, action routing, and regression-sensitive paths cited above were read.

## Assessment

**Ready to merge: No (as inspected).**

The architecture and existing automated gates are strong, but the new pause has an authoritative-freeze refusal gap and a logout/restart gravity leak, limbo can leak items through vanilla close callbacks, and the client watchdog disagrees with the cinematic's real-pause clock. Fix the Important findings in the parent's single test-first pass; ledger deferred Minors and retain the explicit native/manual proof limits. No re-review is required or presumed by this report.
