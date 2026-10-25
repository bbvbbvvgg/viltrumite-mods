# Reinhard — Stage 3 (Restrained and Knight abilities) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Overview: `docs/plans/2026-10-11-reinhard-overview.md`. Every task with a pose or VFX follows `.agents/skills/animation-system/SKILL.md`.

**Goal:** the 9 abilities of spec §6 with their own third-person clip, first-person pose, VFX and sound: Light Punch, Step Dash, Catch Projectile (page 1, Restrained); Fist Combo, Sky Kick, Grab & Throw, Dash Behind, Stomp, Reflect Projectile (page 2, Knight).

**Architecture:** one class per ability in `hero/reinhard/ability/` with `tryStart(ServerPlayer, ReinhardState)`, `tick(ServerPlayer, ReinhardState)`, `cancel(ServerPlayer, ReinhardState)`. `ReinhardActions` is the router: `handleInput` → `ReinhardAbility` by action → gate (`canAct`, not anchored, mode = ability's mode, cooldown 0, not busy) → `tryStart`; refusal → sound. One action timeline at a time (`ReinhardState.action`: id, elapsed, length, target id) drives the snapshot `actionId/actionElapsed/actionLength/controlTargetId`, which drives the clips (`ReinhardPoses.overlay`) and first person. Destruction goes through `DestructionLevel` per mode.

**Tech Stack:** `HeroFx`, `HeroDebris`, `HeroShockwave`, `HeroDestruction`, `ControlManager`, `HeroRegistry.allowsImpulse`, `HeroProjectiles` (Stage 2), anim core clips, `PixelVfx`, `CameraShake`, new client `HitStop`.

**Spec:** §5 (destruction), §6, §9.2, §9.3, §10.2, §11.6, §11.7.

## Global Constraints

See the overview. Stage-specific:
- Restrained never breaks blocks and never pushes hard (knockback ≤ 0.6). Knight breaks moderately (top layers only, through `HeroDebris.erupt` with small specs). Both honour `mobGriefing` and unbreakable blocks.
- Every hit: `hurt(...)` first, knockback only if it returned true and `HeroRegistry.allowsImpulse(target)`.
- Ray targets: `PartEntity` → `getParent()`.
- Abilities without a cooldown in the spec (Light Punch, Fist Combo) are limited by their animation length (the action lock).

## Review Focus

1. Dash Behind into a 1-block corridor or under a low ceiling: never inside a wall, never above the void, never outside the world border; no spot → refusal sound and no cooldown — `SafeSpotTest` (all cases).
2. Throw a player with Lion's Heart or an anchored target: no grab, refusal; a grab that ends by death/logout/dimension change of either side releases the control — `GrabThrowTest.releaseOnEveryEnd`.
3. Sky Kick on a player: the client really flies 60+ blocks (velocity sent with `hurtMarked`), not only on the server — manual + `SkyKickTest.velocityWithinPacketCap`.
4. Step Dash refused by the server after the client already moved (cooldown desync): no FX, no cooldown reset, position accepted — `StepDashTest.serverRefusalHasNoSideEffects`.
5. Fist Combo chain broken by a mode switch or a hit-stop: the chain resets, no stuck pose — `FistComboTest.resetOnModeChange`.

---

### Task 1: Ability router, cooldowns, action timeline, destruction level, hit-stop

**Files:**
- Create: `core/hero/reinhard/ReinhardActions.java`, `core/hero/reinhard/ActionTimeline.java`, `core/hero/reinhard/DestructionLevel.java`, `core/hero/reinhard/ReinhardTargets.java`, `core/client/render/vfx/HitStop.java`
- Modify: `ReinhardHero` (`handleInput`, `tick`, `cleanup`, `canAct`, `snapshot`), `ReinhardState` (`ActionTimeline action`, `busy()`), `ReinhardAbilities.SHIPPED_STAGE = 3`, `ReinhardPoses` (overlay clip name from `actionId`), `res/sounds.json` (slots for every ability, vanilla placeholders; final ones in Stage 7 or §10.2)
- Test: `test/hero/reinhard/ReinhardActionsTest.java`, `ActionTimelineTest.java`, `DestructionLevelTest.java`, `ReinhardTargetsTest.java`, `test/client/reinhard/HitStopTest.java`

**Interfaces:**
- `ActionTimeline`: `start(ReinhardAbility a, int length, int targetId)`, `tick()` → `int elapsed()`, `boolean done()`, `cancel()`; `actionId` on the wire = `a.ordinal()`, −1 = none.
- `ReinhardActions.gate(Gate g)` pure: `record Gate(boolean canAct, boolean anchored, ReinhardMode mode, ReinhardMode abilityMode, int cooldown, boolean busy, boolean timelineRunning)` → `Refusal` enum `NONE, CONTROLLED, WRONG_MODE, COOLDOWN, BUSY`.
- `DestructionLevel.of(ReinhardMode)` → `NONE` (Restrained), `MODERATE` (Knight), `MASSIVE` (Holy Sword); `boolean mayBreak()`; `HeroDebris.Eruption scaled(HeroDebris.Eruption)` (NONE → null).
- `HitStop` (client, shared): `trigger(int entityId, int ticks)`; `float frozenPartial(Entity e, float partialTick)` returns the partial tick frozen at the trigger moment while active; used by `ReinhardRenderer` (clip clock) and by `RenderLivingEvent.Pre` for the target (translate back by the interpolation delta so the target holds still). Server side: hit-stop = the launch is applied `HIT_STOP_TICKS` after contact (true pause on both sides).
- `ReinhardTargets.canHit(ServerPlayer self, Entity e)`: living, not self, not spectator, not a creative player, not allied (`isAlliedTo`), players only when the server allows PvP; `PartEntity` → parent. Used by every AoE of Stages 3–6.
- `ReinhardRules.HIT_STOP_TICKS = 3`.

- [x] **Step 1: Write the failing tests** — gate cases (each refusal), `timelineRunsLength`, `cancelClears`, `restrainedNeverBreaks`, `knightModerate`, `ReinhardTargetsTest.skipsSelfAlliesSpectators` (pure predicate core), `HitStopTest.freezesForTicks`, `releasesAfter`.
- [x] **Step 2: Run** → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS.
- [x] **Step 5: Commit** `feat(reinhard): ability router and action timeline` and `feat(client): hit-stop helper`.

### Task 2: Light Punch (Restrained, slot 1)

**Files:**
- Create: `core/hero/reinhard/ability/LightPunch.java`; clip `light_punch` (8 t, right jab) in `make_reinhard_clips.py`; icon `light_punch` in `make_reinhard_icons.py`; lang
- Test: `test/hero/reinhard/LightPunchTest.java`

**Interfaces:**
- `ReinhardRules`: `LIGHT_PUNCH_DAMAGE = 4f`, `LIGHT_PUNCH_KNOCKBACK = 0.6`, `LIGHT_PUNCH_REACH = 3.5`, `LIGHT_PUNCH_TICKS = 8`, `LIGHT_PUNCH_HIT_TICK = 3`.
- Hit: ray `LIGHT_PUNCH_REACH` from the eyes at tick 3 → `hurt(playerAttack(self), 4)`; knockback 0.6 along look. No block breaking. Sound `viltrumitecore:punch_impact` (§10.2). FX: small `HeroFx.flash`. First person: jab of the right arm (offset on the `RenderArmEvent` stack).

- [x] Step 1 tests: `damageIsFour`, `knockbackIsPointSix`, `hitOnTick3`, `noDestruction` (`DestructionLevel.NONE`). Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): light punch`.
- [ ] Manual: both views on a zombie and a player — оставлено пользователю.

### Task 3: Step Dash (Restrained, slot 2) — client-predicted

**Files:**
- Create: `core/hero/reinhard/ability/StepDash.java`, `core/client/reinhard/StepDashClient.java`; clip `step_dash` (6 t lean); icon; lang
- Modify: none in the input path. The generic hero input loop already sends `HeroInputC2SPacket(STEP_DASH, true)` for the slot key; `StepDashClient` (a `ClientTickEvent` listener registered by `ReinhardClient`) watches the same key edge through `AbilityInputManager.isAbilityKeyDown` and **only applies the local movement** — it never sends a packet (otherwise the server gets two presses).
- Test: `test/hero/reinhard/StepDashTest.java`

**Interfaces:**
- `ReinhardRules`: `STEP_DISTANCE = 6`, `STEP_TICKS = 4`, `STEP_COOLDOWN = 20`.
- Direction: movement input (WASD) in world space; none → look (horizontal).
- Client: on press with local cooldown 0 and the gate passing on the mirrored snapshot → set horizontal velocity `StepDash.velocity(STEP_DISTANCE, STEP_TICKS, friction)` for `STEP_TICKS` ticks (re-applied each tick), stops at a wall (vanilla collision). No packet from the predictor (the generic input path sends the press once).
- Server: gate → cooldown 20, action timeline 6 t, the same velocity set server-side with `hurtMarked = true` (authoritative motion that matches the prediction), FX `HeroFx.launch` at the start feet + afterimage pixels, sound placeholder `entity.player.attack.sweep` pitch 1.6. Refused → no FX, no cooldown; server sends its current velocity (`ClientboundSetEntityMotionPacket`) to stop the predicted dash (the client predictor ends once the speed drops under `BLOCKED_SHARE`). As built, the server keeps its own velocity copy without `hurtMarked` on acceptance, and on refusal the position the client already reached stays (no rubber-band; `StepDash.Response.correctPosition = false`). Dash Behind, a teleport of up to 24 blocks, is snapped back to the server position instead.
- Pure `velocity(distance, ticks, friction)`: per-tick speed so the sum over `ticks` with ground friction 0.546 (0.91 × 0.6) ≈ distance.

- [x] Step 1 tests: `coversSixBlocks` (simulated sum within ±0.3), `cooldownTwentyTicks`, `serverRefusalHasNoSideEffects`, `directionFromInputThenLook`, `onePressOnePacket` (fake input loop + predictor for one key edge → exactly one `HeroInputC2SPacket`). Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): step dash`.
- [ ] Manual: on flat ground ~6 blocks, into a wall stops, both views — оставлено пользователю.

### Task 4: Catch Projectile (Restrained, slot 3)

**Files:**
- Create: `core/hero/reinhard/ability/CatchWindow.java`; clip `catch` (hand up and closing on impact); icon; lang
- Modify: `ReinhardHero.projectileResponse` (`ArrowWard.response(catchOpen, reflectOpen)`), flags `CATCH_WINDOW`
- Test: `test/hero/reinhard/CatchWindowTest.java`

**Interfaces:**
- `ReinhardRules`: `CATCH_WINDOW = 10`, `CATCH_COOLDOWN = 40`.
- Press opens the window (flag set, pose `catch_ready`); a caught projectile → `HeroProjectiles` CATCH (arrow to inventory), pose `catch` snap, sound placeholder `entity.item.pickup` pitch 0.8, `HeroFx.flash` at the hand; the window stays open until it ends (several catches allowed). Cooldown starts at the press.

- [x] Step 1 tests: `windowTenTicks`, `cooldownFortyFromPress`, `catchBeatsDeflectInWindow`, `deflectAfterWindow`. Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): catch projectile`.
- [ ] Manual: skeleton arrows caught (count in inventory), trident caught as item, fireball caught = vanishes; first person hand closes — оставлено пользователю.

### Task 5: Fist Combo (Knight, slot 1)

**Files:**
- Create: `core/hero/reinhard/ability/FistCombo.java`; clips `combo_1`, `combo_2`, `combo_3` (one-handed: the off arm stays behind the back); icon; lang
- Test: `test/hero/reinhard/FistComboTest.java`

**Interfaces:**
- `ReinhardRules`: `COMBO_DAMAGE = {10f, 10f, 16f}`, `COMBO_HIT_TICKS = {3, 3, 5}`, `COMBO_STEP_TICKS = {8, 8, 12}`, `COMBO_CHAIN_WINDOW = 20`, `COMBO_WAVE_RADIUS = 4`, `COMBO_WAVE_DAMAGE = 8f` (wave damage is not in the spec; starting value, open to balance), `COMBO_REACH = 3.5`.
- Input (open question 1, default): each press = next hit; a press within `COMBO_CHAIN_WINDOW` after the previous hit ends continues the chain, else it restarts at hit 1. Hit 3: `HeroFx.shockwave` r 4 + `HeroDebris.erupt` with a small Knight spec (moderate destruction) + damage in radius (targets through `ReinhardTargets.canHit`).
- Sounds: `viltrumitecore:regulus_punch_hit` (hits 1–2), `viltrumitecore:regulus_impact_heavy` (hit 3) — §10.2.

- [x] Step 1 tests: `damageSequence10_10_16`, `chainWindowTwenty`, `lateRestarts`, `waveOnlyOnThird`, `resetOnModeChange`. Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): fist combo`.
- [ ] Manual: both views, `mobGriefing false` → no blocks move — оставлено пользователю.

### Task 6: Sky Kick (Knight, slot 2)

**Files:**
- Create: `core/hero/reinhard/ability/SkyKick.java`, `core/client/render/vfx/AirTrailVfx.java` (white trail behind a launched entity + sonic ring), clips `kick_toss`, `kick`; icon; lang
- Test: `test/hero/reinhard/SkyKickTest.java`

**Interfaces:**
- `ReinhardRules`: `KICK_REACH = 3.5`, `KICK_TOSS_TICKS = 6`, `KICK_TOSS_VELOCITY = 0.45`, `KICK_VELOCITY = 3.9` (packet cap; apex ≈ 61), `KICK_DAMAGE = 20f`, `KICK_COOLDOWN = 200`.
- Timeline: t0 no wind-up: light toss (target `deltaMovement.y = 0.45`, sound `entity.player.attack.sweep` §10.2) → t6 kick contact: `hurt(20)` → hit-stop 3 t (target velocity zeroed, `HitStop` on both entities through a `HeroFx` flash packet carrying the ids — reuse `HeroFxS2CPacket` with a new kind `HIT_STOP` only if needed) → t9 launch `(0, 3.9, 0)` + `hurtMarked = true` (players get `ClientboundSetEntityMotionPacket`) → sonic ring (`AirTrailVfx.ring`: white horizontal pixel ring at the contact point) + trail on the target for 40 t. Kick sound placeholder `entity.generic.explode` pitch 1.8 until Stage 7 (`qubodup` sonic boom).
- Refuses targets that fail `allowsImpulse` (refusal sound, no cooldown).

- [x] Step 1 tests: `apexAtLeast60` (`HeroSuperJump.apex(KICK_VELOCITY) >= 60`), `velocityWithinPacketCap` (≤ 3.9), `timelineTossKickLaunch` (ticks 0/6/9), `cooldown200`, `impulseRefusedNoCooldown`. Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): sky kick`.
- [ ] Manual: zombie and a LAN player fly 60+ blocks, ring and trail, hit-stop visible, both views — оставлено пользователю.

### Task 7: Grab & Throw (Knight, slot 3)

**Files:**
- Create: `core/hero/reinhard/ability/GrabThrow.java`; clips `grab_hold` (one hand at chest height), `throw`; icon; lang
- Test: `test/hero/reinhard/GrabThrowTest.java`

**Interfaces:**
- `ReinhardRules`: `GRAB_REACH = 3.5`, `GRAB_MAX_TICKS = 100`, `THROW_DISTANCE = 120`, `THROW_SPEED = 3.9`, `THROW_SUSTAIN_TICKS`, `THROW_DAMAGE = 15f`, `GRAB_COOLDOWN = 240` (starts at the throw, or at a forced release).
- Press 1: target in reach, `allowsExternalControl(target, PULL)` → `ControlManager.get(level).tryAcquire(target, self.getUUID(), grabEffectId, ControlKind.PULL)` (`grabEffectId = UUID.randomUUID()` at the press, stored in `ReinhardState.grab`); anchor follows the hand point each tick (like `IronManHulkbuster` grab); flag `GRAB_HOLDING`. Press 2 (or `GRAB_MAX_TICKS`): release with `grabEffectId` (`ControlManager.get(level).release(target.getUUID(), grabEffectId, ReleaseReason.…)`; real signature `release(UUID targetId, UUID effectId, ReleaseReason)`), `hurt(15)`, hit-stop 3 t, then velocity along the look with a small up angle (+12°), re-applied every tick for `THROW_SUSTAIN_TICKS` (pure: `sustainTicks(distance, speed, airDrag 0.91)` so the flight covers ~120 blocks) unless the target hit a block or `allowsImpulse` turns false; sonic ring + white trail (Task 6 VFX), heavy sound `regulus_impact_heavy` on release (§10.2).
- Every end (death, logout, dimension change, hero change, mode change, control on Reinhard) → `GrabThrow.release(...)` with no throw.

- [x] Step 1 tests: `sustainCoversAbout120` (simulation within ±10), `damage15OnThrow`, `cooldownFromThrow`, `releaseOnEveryEnd` (each `CleanupReason` + mode change), `refusesUncontrollable`. Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): grab and throw`.
- [ ] Manual: zombie and a player thrown ~120 blocks, Regulus in Lion's Heart refused, both views — оставлено пользователю.

### Task 8: Dash Behind (Knight, slot 4) — safe spot, predicted

**Files:**
- Create: `core/hero/reinhard/ability/DashBehind.java`, `core/hero/SafeSpot.java` (shared, hero-neutral), `core/client/reinhard/DashBehindClient.java`, `core/network/packet/ReinhardDashBehindC2SPacket.java` (`int targetId`, `double x, y, z` of the candidate feet; register in `CoreMessages`, PLAY_TO_SERVER); clip `dash_behind` (vanish lean → land turned); icon; lang
- Test: `test/hero/SafeSpotTest.java`, `test/hero/reinhard/DashBehindTest.java`

**Interfaces:**
- `SafeSpot` pure: `interface Probe { boolean fits(Vec3 feet); boolean insideBorder(Vec3 feet); boolean groundBelow(Vec3 feet, int maxDrop); }`; `static Optional<Vec3> find(Vec3 wanted, Probe p, double radius, double step)` — wanted spot first; else spiral/shell search up to `radius` (3 blocks), nearest first; a spot needs `fits && insideBorder && groundBelow(…, 6)` (not over the void). Minecraft probe: `level.noCollision(entity, box at feet)`, `level.getWorldBorder().isWithinBounds(box)`, a solid block within 6 below and above `minBuildHeight`.
- `ReinhardRules`: `BEHIND_RANGE = 24`, `BEHIND_OFFSET = 1.5`, `BEHIND_SEARCH = 3`, `BEHIND_COOLDOWN = 60`.
- Target = the living entity under the crosshair within 24 (client and server compute it the same way). Wanted spot = target feet − target horizontal look × 1.5. Fact: `HeroInputC2SPacket` carries only `actionOrdinal` + `pressed`, so the candidate travels in the separate `ReinhardDashBehindC2SPacket`. The slot key of Dash Behind is **not** routed through `HeroInputC2SPacket` (`ReinhardHero.heroInputSlots` excludes it, so the generic loop sends nothing for it); `DashBehindClient` sends exactly one `ReinhardDashBehindC2SPacket` per key edge. Client: computes `find` with a client-level probe, teleports itself locally (`setPos` + facing the target's back), sends the packet. Server handler (`enqueueWork`): resolves `targetId` in the sender's level (missing / not living / > 24 + 2 tolerance → refusal), runs the same gate as any ability (`canAct`, mode Knight, cooldown), recomputes; accepts the client spot if it passes its own probe and is within 1 block of the server answer; else teleports to the server answer; none → refusal sound, no cooldown (client is snapped back with `teleportTo` to the start).
- FX: white pixel afterimage at the start, `HeroFx.flash` at the arrival, sound placeholder `entity.enderman.teleport` pitch 1.5.

- [x] Step 1 tests: `SafeSpotTest.wantedSpotFirst`, `blockedSearchesNearest`, `outsideBorderRejected`, `overVoidRejected`, `nothingWithin3Empty`; `DashBehindTest.refusalKeepsCooldown`, `rangeIs24`, `clientSpotAcceptedWithin1`, `clientSpotFarAwayReplaced`, `packetWithForeignTargetRefused` (id of an entity in another level / non-living), `nanCandidateRejected`. Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(hero): safe spot search` and `feat(reinhard): dash behind`.
- [ ] Manual: target against a wall, in a 1×2 tunnel, at the world border, on a pillar over the void, both views — оставлено пользователю.

### Task 9: Stomp (Knight, slot 5)

**Files:**
- Create: `core/hero/reinhard/ability/Stomp.java`; clip `stomp` (right leg up, slam); icon; lang
- Test: `test/hero/reinhard/StompTest.java`

**Interfaces:**
- `ReinhardRules`: `STOMP_RADIUS = 8`, `STOMP_DAMAGE = 12f`, `STOMP_COOLDOWN = 160`, `STOMP_TICKS = 14`, `STOMP_HIT_TICK = 8`, `STOMP_ERUPTION = new HeroDebris.Eruption(…)` (Knight: radius 4, depth 1, few flying blocks).
- Hit tick: entities within r 8 on the ground plane (±3 y) → `hurt(12)` + knockback outward 0.8 (allowsImpulse); `HeroFx.shockwave(origin, power, material, 8)` (ground ring + camera shake), `HeroDebris.erupt` with the moderate spec when `DestructionLevel.mayBreak()`. Sound placeholder `entity.generic.explode` until Stage 7 (`dingus23` earthquake).

- [x] Step 1 tests: `radius8Damage12`, `cooldown160`, `airborneTargetsAbove3Missed`, `moderateEruptionOnlyInKnight`. Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): stomp`.
- [ ] Manual: both views, `mobGriefing false` → no blocks move — оставлено пользователю.

### Task 10: Reflect Projectile (Knight, slot 6)

**Files:**
- Create: `core/hero/reinhard/ability/ReflectWindow.java`; clips `reflect_ready`, `reflect` (backhand swat); icon; lang
- Test: `test/hero/reinhard/ReflectWindowTest.java`

**Interfaces:**
- `ReinhardRules`: `REFLECT_WINDOW = 20`, `REFLECT_COOLDOWN = 80`.
- Same window mechanics as Task 4 with `REFLECT` response (Stage 2 `HeroProjectiles`); swat pose snaps on each reflection; sound placeholder `item.shield.block` pitch 0.7; white streak on the returned projectile (`AirTrailVfx`).

- [x] Step 1 tests: `windowTwentyTicks`, `cooldownEightyFromPress`, `reflectReturnsToShooter` (pure `HeroProjectiles.reflected`). Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): reflect projectile`.
- [ ] Manual: skeleton killed by its own arrow, ghast fireball back to the ghast, both views — оставлено пользователю.

### Task 11: Pose audit, check, docs, PR

**Files:**
- Modify: `make_reinhard_clips.py` (final pass), `.agents/skills/animation-system/SKILL.md` (Reinhard clip list + `HitStop`), `.agents/skills/add_hero/references/shared-toolkit.md` (`SafeSpot`, `HitStop`, `AirTrailVfx`), `.memory-bank/project.md`, `SESSION.md`
- Test: `test/client/reinhard/ReinhardClipsTest.java`

- [x] **Step 1: Write the failing test** — `everyAbilityHasClip` (for each `ReinhardAbility` with `stage <= 3`, the animation file has its clip name(s) and each clip length ≥ the action length / 20).
- [x] **Step 2:** Run → PASS on the first run (every clip was added in its task; see the ledger).
- [x] **Step 3:** Full verification list (tests, build, checker, diff check).
- [ ] Dedicated server start — not run (user rule), left to the user.
- [ ] **Step 4 (manual, оставлено пользователю):** In game, each ability in first and third person, grounded and in air where allowed, with `mobGriefing` true and false; old heroes unchanged.
- [x] **Step 5:** Commit `docs(reinhard): stage 3 notes`; push `feat/reinhard-stage3`; PR «Рейнхард, этап 3: Сдержанный и Рыцарь».

Acceptance criteria closed: §11.6 (Restrained, Knight), §11.7 (§6 part), §11.4 (windows part of §4.3).

## Risks and pitfalls

- Player knockback/launch: set `hurtMarked = true` after changing `deltaMovement`, or the client never receives it (client-authoritative movement).
- `ControlManager` PULL anchors teleport the target each tick; for a player target this fights client prediction — use the same anchor update rate as the Hulkbuster grab, which is already proven.
- `HeroDebris.erupt` is expensive for big radii; Knight specs must stay small (≤ 40 flying blocks).
- Clip names are strings: keep them as constants in `ReinhardClips` (generated by the script) to avoid typos.
