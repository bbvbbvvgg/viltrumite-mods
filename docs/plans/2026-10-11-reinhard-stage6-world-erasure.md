# Reinhard — Stage 6 (World Erasure) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Overview: `docs/plans/2026-10-11-reinhard-overview.md`. Visual tasks follow `.agents/skills/animation-system/SKILL.md`.

**Goal:** World Erasure (Holy Sword slot 6, Reid drawn): a 7.5 s cinematic felt by every player on the server in every dimension — screen cracks like glass, shatters into falling shards over darkness, the target's white silhouette crumbles to dust, shards fly back and rebuild the world without the target. Everyone except the caster is frozen. A mob/boss vanishes (no kill, no message, no loot, no XP); a player goes to a limbo dimension (inventory, armour, offhand and XP taken, ender chest untouched), reads a short text, and after 30 s respawns at the world spawn without a death message. Lion's Heart and divine targets survive. Cooldown 60 s. World blocks never change.

**Architecture:** shared server seam `hero/control/WorldPause.java` (global freeze over `ControlManager` FREEZE) + `client/hero/WorldPauseClient.java`; server `hero/reinhard/ability/WorldErasure.java` (timeline, verdicts) and `hero/reinhard/limbo/Limbo.java` (dimension transfer, strip, timer, return, persistence); data dimension `viltrumitecore:the_void`; client `client/reinhard/erasure/ErasureScene.java` (post-shader uniforms + HUD silhouette) and `client/reinhard/limbo/LimboScreen.java` (text, heartbeat, flash). One `ErasureS2CPacket` to all players.

**Tech Stack:** `ControlManager`, `DivineTargets.verdict`, `CleanupReason`, `dash_impact` post shader + `GameRendererDashMixin`, `ScreenProjector`, `SoundDuck`, `DimensionSpecialEffects`, `ServerPlayer.teleportTo(ServerLevel, …)`, `PlayerEvent.PlayerLoggedInEvent`, `PlayerEvent.Clone`.

**Spec:** §7.7, §9.5, §9.6, §10.1 (erasure, limbo sounds), §11.10.

## Global Constraints

See the overview. Stage-specific:
- **No second post chain.** The erasure screen effect is new uniforms in the existing `dash_impact` program (spec §9.5). Every new uniform defaults to 0 and the shader early-outs when all of them are 0.
- **World blocks never change.** Nothing in this stage calls `setBlock`.
- **One erasure at a time, server-wide.** `WorldPause.active()` refuses a second cast (refusal sound, no cooldown spent).
- **Freeze is always released.** Every path (end of timeline, caster death/logout, server stop, `/reload`, dimension change) ends with `WorldPause.end(...)`; a joining player during an active pause is frozen and gets the scene from its current tick; a pause can never outlive `ERASE_LENGTH + 40` ticks (watchdog).
- **Limbo state survives logout and restart** (player NBT under `viltrumitecore:limbo`), and the stripped items are not kept anywhere (canon: they are erased) — this is intentional and is said in the lang text and the PR.
- Default for overview Question 2: during the pause every player except the caster is invulnerable and hostile mobs within r 32 of any frozen player are frozen too. Both are behind `ReinhardRules.ERASE_PROTECT_FROZEN = true` so the alternative is a one-line change.

## Review Focus

1. Caster dies, logs out or changes dimension mid-cinematic: every player unfreezes, every client scene ends, the target stays (if the removal tick has not passed) — `WorldErasureTest.casterLeavesMidway`, `WorldPauseTest.endsOnEveryPath`.
2. Target player logs out at any tick, or is already in limbo / spectator / creative: refusal or clean outcome, never a stuck limbo or a duplicate strip — `WorldErasureTest.targetEdgeCases`, `LimboTest.persistsAcrossLogout`.
3. Limbo return: player lands at the overworld shared spawn (not bed, not respawn anchor), full health, no death message, no `deathCount` stat, no item drops at the old position, hero state cleaned with `CleanupReason.ERASED` — `LimboTest.returnToSpawn`.
4. Post shader: with no erasure the image is bit-identical to before (uniforms 0, early-out); with Iris/Oculus shaders on the effect is skipped like the dash impact and the HUD fallback still shows the silhouette — `ErasureSceneTest.uniformsZeroWhenIdle` + manual.
5. Lion's Heart Regulus / divine target / another Reinhard: the full cinematic plays for everyone, the target remains in place, the cooldown is spent — `WorldErasureTest.verdicts`.

---

### Task 1: `WorldPause` — server-wide cinematic freeze

**Files:**
- Create: `core/hero/control/WorldPause.java`, `core/client/hero/WorldPauseClient.java`
- Modify: `core/hero/HeroEvents.java` (login → `WorldPause.onJoin`, logout of the owner → `end`, `ServerStoppingEvent` → `end`, `LivingAttackEvent` → cancel for paused players when protection is on, `LivingEvent.LivingTickEvent` for protected mobs), `core/hero/control/ControlManager.java` (no logic change; a javadoc note that `WorldPause` is a FREEZE owner)
- Test: `test/hero/control/WorldPauseTest.java`

**Interfaces:**
- `ReinhardRules`: `ERASE_PROTECT_FROZEN = true`, `ERASE_MOB_FREEZE_RADIUS = 32`.
- `final class WorldPause` (one instance per `MinecraftServer`, stored in a `SavedData`-free static map keyed by server, cleared on stop):
  - `static boolean begin(MinecraftServer server, UUID owner, String sourceId, int maxTicks)` → false if one is active.
  - `static boolean active(MinecraftServer)`, `static boolean isPaused(Entity)`, `static UUID owner()`.
  - `static void tick(MinecraftServer)` — every server tick: for each online player except the owner, in every `ServerLevel`, keep `ControlManager.get(level).tryAcquire(player, owner, "viltrumite:world_pause", ControlKind.FREEZE, expiresAt = now + 5)` refreshed (short expiry = self-healing if the server forgets to end); when protection is on, also every `Monster` within r 32 of a paused player (`allowsExternalControl` respected; bosses freeze too, `PartEntity` → parent); watchdog ends after `maxTicks + 40`.
  - `static void end(MinecraftServer, EndReason)`; `enum EndReason { DONE, OWNER_LEFT, SERVER_STOP, WATCHDOG }` — releases every acquired control with `ControlManager` release, sends `WorldPauseS2CPacket(false)`.
  - `static void onJoin(ServerPlayer)` — a joiner during the pause is frozen at once and receives the scene catch-up from `WorldErasure` (Task 2).
- `WorldPauseClient`: on `WorldPauseS2CPacket(true)` blocks movement input (`MovementInputUpdateEvent` zeroes the input), attack/use (`InputEvent.InteractionKeyMappingTriggered` cancel), hides nothing; the existing FREEZE anchor visual is suppressed for this source id (no ice/stasis particles — the pause is "time stopped", not a spell on the player).
- Pure helper for the tests: `static Set<UUID> frozenSet(Collection<UUID> online, UUID owner)`.

- [ ] Step 1 tests: `ownerNeverFrozen`, `secondBeginRefused`, `joinerFrozen`, `endsOnEveryPath` (DONE, OWNER_LEFT, SERVER_STOP, WATCHDOG → no control left in any level), `controlsSelfExpire` (no tick for 5 t → released by `ControlManager` expiry), `mobsOnlyWhenProtected`. Step 2 run `./gradlew :viltrumitecore:test --tests '*WorldPauseTest'` → FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(control): server-wide world pause`.

### Task 2: Erasure server timeline, target, verdicts, `CleanupReason.ERASED`

**Files:**
- Create: `core/hero/reinhard/ability/WorldErasure.java`, `core/network/packet/ErasureS2CPacket.java` (register in `ModNetwork`)
- Modify: `core/hero/CleanupReason.java` (append `ERASED`), `ControlManager.cleanupReleaseReason` (`ERASED` → same as `DEATH`), every `HeroDefinition.cleanup` switch that is exhaustive (compiler finds them; `ERASED` behaves like `DEATH`), `ReinhardActions`, `ReinhardState.busy()` (true while the own erasure runs), `ReinhardAbilities.SHIPPED_STAGE = 6`, icon `world_erasure`, lang, `res/sounds.json` (`reinhard.erase_crack`, `reinhard.erase_inhale` — placeholders `block.glass.break` pitch 0.5 and `block.respawn_anchor.charge` until Stage 7)
- Test: `test/hero/reinhard/WorldErasureTest.java`

**Interfaces:**
- `ReinhardRules`: `ERASE_RANGE = 64`, `ERASE_LENGTH = 150` (7.5 s), phases `ERASE_CRACK_END = 30`, `ERASE_SHATTER_END = 70`, `ERASE_DARK_END = 110`, `ERASE_REMOVE_TICK = 90`, `ERASE_COOLDOWN = 1200`.
- Press (only Reid DRAWN): target = first living entity on the look ray ≤ 64 (`ProjectileUtil.getEntityHitResult`, blocks occlude; `PartEntity` → parent). Refusal without cooldown (sound `reinhard.refuse`) when: no target; `WorldPause.active`; target is a player in creative/spectator or already in limbo. Otherwise `WorldPause.begin(..., ERASE_LENGTH)`, cooldown starts, action timeline `world_erasure`.
- Packet `ErasureS2CPacket(Phase phase, long startGameTime, int targetId, ResourceKey<Level> targetLevel, Vec3 targetPos, boolean targetStays)` to **all** players (all dimensions); catch-up on join.
- Tick `ERASE_REMOVE_TICK` (in the dark): verdict = `DivineTargets.verdict(isDivine(target), isAbsolutelyInvulnerable(target))`:
  - `MORTAL` mob / boss → `target.discard()` (`Entity.RemovalReason.DISCARDED`: no death event, no loot, no XP, no death message; `EnderDragon` → its fight state updated with `EndDragonFight.updateDragon` only if the vanilla API allows a silent removal, otherwise documented exception: the dragon is a divine-tagged entity by default — decide in implementation and note in the PR).
  - `MORTAL` player → hero cleanup `ERASED`, `Limbo.send(player)` (Task 3).
  - `DIVINE` / `ABSOLUTE` → nothing; `targetStays = true` was already sent at the start (the verdict is computed at the press so the silhouette scene knows).
- End at tick 150 → `WorldPause.end(DONE)`; caster death/logout earlier → `end(OWNER_LEFT)` and, if before tick 90, nothing is removed.

- [ ] Step 1 tests: `refusalsSpendNoCooldown` (each refusal case), `secondCastRefusedWhileActive` (two casters), `removeTick90`, `mobDiscardedNoLoot` (fake: no `die` call, removal reason DISCARDED), `partEntityResolvesParent`, `verdicts` (divine / absolute / another Reinhard → stays, cd spent), `casterLeavesMidway`, `targetEdgeCases` (target logs out before 90 → nothing; logs in later → normal), `erasedCleanupLikeDeath`. Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): world erasure timeline`.

### Task 3: Limbo dimension, strip, timer, return

**Files:**
- Create: `core/hero/reinhard/limbo/Limbo.java`, `res/data/viltrumitecore/dimension_type/the_void.json` (fixed time 18000, no skylight, no ceiling, `has_raids false`, `respawn_anchor_works false`, `bed_works false`, `ambient_light 0`, `effects viltrumitecore:the_void`), `res/data/viltrumitecore/dimension/the_void.json` (flat generator with an empty layer list, biome `minecraft:the_void`)
- Modify: `HeroEvents` (login/clone/tick hooks for limbo players, `LivingAttackEvent` cancel inside limbo, `PlayerEvent.PlayerChangedDimensionEvent` guard: no portal leaves limbo), lang
- Test: `test/hero/reinhard/LimboTest.java`

**Interfaces:**
- `ReinhardRules`: `LIMBO_TICKS = 600`, `LIMBO_POS = (0.5, 128, 0.5)`.
- `Limbo.send(ServerPlayer p)`: close container; clear main inventory, armour, offhand (`Inventory.clearContent()` — the ender chest is a separate container and is untouched); `p.setExperienceLevels(0)`, `setExperiencePoints(0)`, `totalExperience = 0`; teleport to `the_void` at `LIMBO_POS`, `setNoGravity(true)`, velocity 0, game mode unchanged; persistent tag `viltrumitecore:limbo { until: gameTime + 600 }`; send `LimboS2CPacket(START, ticksLeft)`.
- `Limbo.tick(ServerPlayer)`: keep position locked (teleport back if moved > 0.1), invulnerable, hunger frozen; when the timer ends → `returnToSpawn`: overworld `level.getSharedSpawnPos()` with the vanilla spawn-radius search (`PlayerRespawnLogic`-equivalent safe spot), `setNoGravity(false)`, health/food full, remove tag, `LimboS2CPacket(END)`.
- Login with the tag: if time is left → back into limbo with the remaining ticks; if not → `returnToSpawn`. The timer counts game time, so it also passes while the player is offline (simplest, documented).
- No death message, no `Stats.DEATHS`, no drops: the player never dies.

- [ ] Step 1 tests (pure parts + fakes): `stripKeepsEnderChest`, `xpZeroed`, `returnsAfter600`, `returnToSpawn` (shared spawn, not bed), `persistsAcrossLogout` (tag round-trip; remaining time), `noPortalOut`, `immuneInLimbo`. Step 2 FAIL. Step 3 implement + datapack JSON (validate with `./gradlew :viltrumitecore:runData` not needed — load test in `runServer`). Step 4 PASS; manual: `/execute in viltrumitecore:the_void` exists after start; dedicated server log has no dimension errors. Step 5 commit `feat(reinhard): limbo dimension`.

### Task 4: Post-shader uniforms for the erasure

**Files:**
- Modify: `res/assets/viltrumitecore/shaders/program/dash_impact.fsh` / `.json`, `res/assets/viltrumitecore/shaders/post/dash_impact.json` (declare uniforms with default 0), `core/client/mixin/GameRendererDashMixin.java` (compute and set the uniforms; keep the chain active while the erasure scene runs)
- Create: `core/client/reinhard/erasure/ErasureUniforms.java` (pure)
- Test: `test/client/reinhard/ErasureUniformsTest.java`

**Interfaces:**
- Uniforms: `EraseCrack` (0..1, crack lines grow from the screen centre and from 5 seeded points — Voronoi-like edges, white-grey lines, slight refraction offset), `EraseShatter` (0..1, the image is split into seeded Voronoi cells; each cell moves down with gravity, rotates slightly and darkens; behind them black), `EraseDark` (0..1, full black mix), `EraseRebuild` (0..1, inverse of shatter: cells fly back up into place, ease-out), `EraseSeed` (float, from `startGameTime`). All clamped in the shader; the shader returns the original colour when `EraseCrack + EraseShatter + EraseDark + EraseRebuild == 0`.
- `ErasureUniforms.at(int t, float pt)` → record of the four values by phase (0–30 crack 0→1; 30–70 shatter 0→1 with crack 1; 70–110 dark 1; 110–150 rebuild 1→0 and dark 1→0 over the first 15 t).
- Risk: the flight `sonic_boom` chain replaces the post effect; during the pause no player flies (frozen), and the caster is standing (action timeline), so `GameRendererDashMixin` gives the erasure priority and does not start the sonic chain while the scene runs.

- [ ] Step 1 tests: `uniformsZeroWhenIdle`, `phaseCurves` (sample ticks 0, 15, 30, 50, 70, 90, 110, 130, 150), `rebuildMirrorsShatter`, `clamped`. Step 2 FAIL. Step 3 implement; shader compile check: start the client, `F3+T`, no shader errors in the log. Step 4 PASS; manual: screenshots per phase in both views (attached to the PR), vanilla image unchanged when idle (compare two screenshots). Step 5 commit `feat(reinhard): erasure post uniforms`.

### Task 5: Erasure client scene — silhouette dust, sounds, other dimensions

**Files:**
- Create: `core/client/reinhard/erasure/ErasureScene.java`
- Modify: `ReinhardClient` / client packet handler (start on `ErasureS2CPacket`, catch-up by game time), `WorldPauseClient` (end hook)
- Test: `test/client/reinhard/ErasureSceneTest.java`

**Interfaces:**
- Timeline by `(gameTime − start + pt)`, ends itself at 150 + 20 if no end arrives.
- Sound: tick 0 `reinhard.erase_crack` (UI-like, non-positional `SimpleSoundInstance.forUI`, so every dimension hears it); tick 30 `SoundDuck.duck(this, MASTER, 0.0, 6)` — full silence (our own erase sounds exempt as in Stage 4); tick 110 `reinhard.erase_inhale` + release the duck over 40 t.
- Silhouette (dark phase 70–110): white flat silhouette of the target drawn on the HUD layer: the target entity rendered into the GUI with a white fullbright `RenderType` (same path as the race-screen head preview, `InventoryScreen.renderEntityInInventory`-style) at its screen position from `ScreenProjector.project(targetPos)` when the viewer is in the target's level and it is on screen; otherwise centred, scaled to 40% of the screen height. From tick 80 the silhouette breaks into white pixels that drift up and fade (pure particle list in screen space, seeded); at 90 it is gone. If the target stays (`targetStays`), the silhouette re-forms at 100 instead of crumbling fully (the world "cannot erase" it) — and is visible in the rebuilt world normally.
- Caster view: same scene, plus the `world_erasure` clip (Task 7); he is not frozen but the scene still covers his screen.

- [ ] Step 1 tests: `silhouetteCentreFallbackOtherDimension`, `dustGoneAt90`, `staysReforms`, `duckReleasedOnEveryEnd`, `catchUpFromMidway` (join at t 60 → shatter state, no crack sound replay). Step 2 FAIL. Step 3 implement. Step 4 PASS; manual: two LAN clients, one in the Nether; target zombie, target player, target Regulus in Lion's Heart. Step 5 commit `feat(reinhard): erasure client scene`.

### Task 6: Limbo client — black void, text, heartbeat, white flash

**Files:**
- Create: `core/client/reinhard/limbo/LimboScreen.java` (HUD overlay, not a `Screen`, so chat and the pause menu still work), `core/client/reinhard/limbo/TheVoidEffects.java` (`DimensionSpecialEffects`: no sky, no clouds, fog colour black, `isFoggyAt` true), registration in `RegisterDimensionSpecialEffectsEvent`
- Modify: lang (`viltrumitecore.limbo.line1..5`, ru_ru and en_us), `res/sounds.json` (`reinhard.limbo_heartbeat` — placeholder `entity.warden.heartbeat` until Stage 7)
- Test: `test/client/reinhard/LimboScreenTest.java`

**Interfaces:**
- Text lines appear one by one at the screen centre, each fading in over 20 t, 100 t apart (5 lines, the last stays). Draft (ru): «Тебя стёрли из мира.» / «Рейнхард ван Астрея пересоздал вселенную без тебя.» / «Всё, что ты нёс, исчезло вместе с тобой.» / «Но сильнейшая сила этого мира сжалилась над тобой.» / «Тебе позволено переродиться.» — en_us equivalent. Final wording is reviewed with the user in the PR.
- Heartbeat: looping `reinhard.limbo_heartbeat` (a `TickableSoundInstance` that stops on `END` or on leaving the dimension), slow (~50 bpm).
- At `END`: full-screen white flash 10 t in, 30 t out, drawn over the first frames in the overworld.
- The HUD hides the hotbar, hand and crosshair while in limbo (`RenderGuiOverlayEvent.Pre` cancel for those overlays).

- [ ] Step 1 tests: `lineTimings`, `flashCurve`, `heartbeatStopsOnLeave`, `hudHiddenOnlyInLimbo`. Step 2 FAIL. Step 3 implement. Step 4 PASS; manual: full 30 s in limbo, chat works, F1, relog in the middle. Step 5 commit `feat(reinhard): limbo client`.

### Task 7: Clip, check, docs, PR

**Files:**
- Modify: `tools/assets/make_reinhard_clips.py` (clip `world_erasure`: Reid raised vertically before the face for 30 t, then a slow single cut down and to the side at t 30, hold; first person: blade rises into the view, cuts across at t 30), `ReinhardPoses`, `.agents/skills/add_hero/references/shared-toolkit.md` (`WorldPause`, `CleanupReason.ERASED`), `.agents/skills/animation-system/SKILL.md` (erasure uniforms), `.memory-bank/project.md` (limbo dimension, pause rules), `SESSION.md`, core version (minor +1)

- [ ] Step 1: regenerate clips (`python3 tools/assets/make_reinhard_clips.py`), check the diff is only the new clip.
- [ ] Step 2: full verification list (`./gradlew build`, all tests, `NoHeroBranchTest`), dedicated server start, `/reload` during an erasure (pause ends, no stuck freeze).
- [ ] Step 3: in game both views; 3 players (caster, target, observer in the Nether); mob, boss (Wither), player, Lion's Heart; spam the key during an active erasure (refusal, no cooldown); target logs out in limbo and back.
- [ ] Step 4: commit `docs(reinhard): stage 6 notes`; push `feat/reinhard-stage6`; PR «Рейнхард, этап 6: Стирание мира».

Acceptance criteria closed: §11.10, §11.7 (§7.7 part).

## Risks and pitfalls

- `Entity.discard()` on a boss: the Wither's boss bar is removed by `stopSeenByPlayer` — verify; the Ender Dragon has a fight state — see Task 2.
- `teleportTo` to another dimension during `PlayerTickEvent` can crash iteration: schedule via `server.execute(...)` at the end of the tick.
- A data dimension loads with the world; old worlds get it on the next start (it is in the mod's datapack). Removing the mod later leaves a harmless `dimensions/viltrumitecore/the_void` folder.
- Freezing players in other dimensions relies on `ControlManager.get(level)` for each level; a player who changes dimension during the pause is re-acquired in the next tick (the 5 t expiry covers the gap).
- `SoundDuck` MASTER to 0 also mutes the UI click sound for 4 s — acceptable for a cinematic.
