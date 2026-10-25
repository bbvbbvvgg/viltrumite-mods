# Reinhard — Stage 6 (World Erasure) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Overview: `docs/plans/2026-10-11-reinhard-overview.md`. Visual tasks follow `.agents/skills/animation-system/SKILL.md`.

**Goal:** World Erasure (Holy Sword slot 6, Reid drawn): a 7.5 s cinematic felt by every player on the server in every dimension — screen cracks like glass, shatters into falling shards over darkness, the target's white silhouette crumbles to dust, shards fly back and rebuild the world without the target. Everyone except the caster is frozen. A mob/boss vanishes (no kill, no message, no loot, no XP); a player goes to a limbo dimension (inventory, armour, offhand and XP taken, ender chest untouched), reads a short text, and after 30 s respawns at the world spawn without a death message. Lion's Heart and divine targets survive. Cooldown 60 s. World blocks never change.

**Architecture:** shared server seam `hero/control/WorldPause.java` (global freeze over `ControlManager` FREEZE + the new `ControlManager.refresh`) + `client/hero/WorldPauseClient.java`; server `hero/reinhard/ability/WorldErasure.java` (timeline, verdict frozen at the cast) and `hero/reinhard/limbo/Limbo.java` (transfer, strip, timer, return, persistence); data dimension `viltrumitecore:the_void`; shared lifecycle `CleanupReason.ERASED` + `HeroEvents.freshBody`; client `client/reinhard/erasure/ErasureScene.java` (post-shader uniforms + HUD silhouette) and `client/reinhard/limbo/LimboOverlay.java` (text, heartbeat, flash). Packets `WorldPauseS2CPacket`, `ErasureS2CPacket`, `LimboS2CPacket`, all registered in `network/CoreMessages.java`.

**Tech Stack:** `ControlManager`, `DivineTargets.verdict`, `CleanupReason`, `dash_impact` post shader + `GameRendererDashMixin`, `ScreenProjector`, `SoundDuck`, `DimensionSpecialEffects`, `ServerPlayer.teleportTo(ServerLevel, …)`, `OnDatapackSyncEvent`, `PlayerEvent.PlayerLoggedInEvent`, `RenderHandEvent`, `RenderGuiOverlayEvent`.

**Spec:** §7.7, §9.5, §9.6, §10.1 (erasure, limbo sounds), §11.10.

## Global Constraints

See the overview (paths `res/`, `mc/`, `data/`; `CoreMessages`; real `ControlManager` signatures; tick phases). Stage-specific:
- **No second post chain.** The erasure screen effect is new uniforms in the existing `dash_impact` program (spec §9.5). Every new uniform defaults to 0 and the shader early-outs when all of them are 0.
- **World blocks never change.** Nothing in this stage calls `setBlock`.
- **One erasure at a time, server-wide.** `WorldPause.active()` refuses a second cast (refusal sound, no cooldown spent).
- **Freeze is always released.** Every path — end of timeline, caster death / logout / dimension change / hero change, server stop, `/reload`, watchdog — ends with `WorldPause.end(...)`. A player joining during the pause is frozen and gets the scene from its current tick; a pause can never outlive `ERASE_LENGTH + 40` ticks.
- **One verdict.** The outcome (erase / stays) is computed **once**, at the successful cast, and that frozen value drives the packet, the client silhouette and the server removal. Later state changes (Lion's Heart turned on/off during the 7.5 s) do not change it.
- **`ERASED` is a living-entity death.** The same `ServerPlayer` entity survives, so every hero must reset all transient state explicitly (no clone does it for us), and the return from limbo runs the same "fresh body" path as a respawn.
- **Limbo state survives logout and restart** (player NBT `viltrumitecore:limbo`). The stripped items are not kept anywhere (canon: they are erased) — said in the lang text and the PR.
- Default for overview Question 2: during the pause every player except the caster is invulnerable and hostile mobs within r 32 of any frozen player are frozen too, behind `ReinhardRules.ERASE_PROTECT_FROZEN = true`.

## Review Focus

1. Caster dies, logs out, changes dimension or hero mid-cinematic, or an op runs `/reload`: every player unfreezes, every client scene ends, the target stays (if tick 90 has not passed) — `WorldErasureTest.casterLeavesMidway`, `WorldPauseTest.endsOnEveryPath`.
2. `ControlManager.refresh` never re-captures `noGravity`/`noAi` and refuses a foreign caster / effect / kind — `ControlManagerRefreshTest`.
3. `ERASED` on the same entity: before erase (Regulus mid-action, Homelander laser on, Iron Man in suit with a grab, Viltrumite grabbing, Reinhard mid-combo) → after limbo return every transient state equals a fresh respawn — `ErasedLifecycleTest.sameEntityFreshAfterReturn`.
4. Limbo: return at the overworld shared spawn (not bed/anchor), full health, no death message, no `Stats.DEATHS`, no drops, survives logout/restart with the remaining time — `LimboTest.*`.
5. Post shader: idle image unchanged (uniforms 0, early-out); Lion's Heart cast → cinematic plays, target stays, cd spent, and the client shows "stays" even if Lion's Heart drops at tick 50 — `ErasureUniformsTest.zeroWhenIdle`, `WorldErasureTest.verdictFrozenAtCast`.

---

### Task 1: `ControlManager.refresh` and `WorldPause`

**Files:**
- Create: `core/hero/control/WorldPause.java`, `core/network/packet/WorldPauseS2CPacket.java` (register in `CoreMessages`), `core/client/hero/WorldPauseClient.java`
- Modify: `core/hero/control/ControlManager.java` (new `refresh`), `core/hero/HeroEvents.java` (server tick END → `WorldPause.tick(server)`; `PlayerLoggedInEvent` → `WorldPause.onJoin`; owner logout / death / `PlayerChangedDimensionEvent` / hero change → `WorldPause.end(OWNER_LEFT)`; `ServerStoppingEvent` → `end(SERVER_STOP)`; `OnDatapackSyncEvent` with `getPlayer() == null` (fires once after `/reload`) → `end(RELOAD)`; existing `onLivingAttack` → cancel for `WorldPause.isProtected(entity)`)
- Test: `test/hero/control/ControlManagerRefreshTest.java`, `test/hero/control/WorldPauseTest.java`

**Interfaces:**
- `ControlManager.refresh(UUID targetId, UUID caster, UUID effectId, ControlKind kind, long expiresAt)` → `boolean`: only if a record exists for the target **and** `caster`, `effectId` and `kind` all match; sets `expiresAt` only — no `unapply/apply`, no re-anchor, no flag capture, `dirty` unchanged unless the sync needs the expiry. Pure precondition `static boolean mayRefresh(@Nullable Rec r, UUID caster, UUID effectId, ControlKind kind)`.
- `ReinhardRules`: `ERASE_PROTECT_FROZEN = true`, `ERASE_MOB_FREEZE_RADIUS = 32`.
- `WorldPause` (one per server, static holder, cleared on stop):
  - `static boolean begin(MinecraftServer, UUID owner, int maxTicks)` → false if one is active; creates `UUID pauseId = UUID.randomUUID()` — **one effect id for the whole pause** (records are keyed by target, so one id for all targets is valid; release by `(targetId, pauseId)`).
  - every server tick, for each online player except the owner (in whatever `ServerLevel` they are now): `ControlManager cm = ControlManager.get(level)`; if `cm.refresh(p.getUUID(), owner, pauseId, FREEZE, now + 5)` is false → `cm.tryAcquire(p, owner, pauseId, FREEZE, now + 5)` (first tick, joiners, after a dimension change). If `tryAcquire` fails because the player already carries a foreign control (e.g. Regulus stasis), the player stays under that control and is marked paused for the client input block and protection only — no override of another effect. With protection on, the same for every `Monster` within r 32 of a paused player (`PartEntity` → parent; `allowsExternalControl` respected by `tryAcquire`).
  - The short expiry (5 t) self-heals if the server forgets to end; the watchdog ends after `maxTicks + 40`.
  - `static void end(MinecraftServer, EndReason)`; `enum EndReason { DONE, OWNER_LEFT, SERVER_STOP, RELOAD, WATCHDOG }` → `release(targetId, pauseId, ReleaseReason.INTERRUPTED)` (or `EXPIRED` for `DONE`) in every level for every tracked target, `WorldPauseS2CPacket(false)` to all.
  - `static boolean active(MinecraftServer)`, `isPaused(Entity)`, `isProtected(Entity)` (paused and protection on).
- `WorldPauseClient`: while paused, zero movement input (`MovementInputUpdateEvent`) and cancel attack/use/pick (`InputEvent.InteractionKeyMappingTriggered`); the FREEZE anchor visual is suppressed for this pause (pure check: the control sync carries the effect id; `WorldPauseS2CPacket(true, pauseId)` tells the client which id to hide).

- [x] Step 1 tests: `ControlManagerRefreshTest.refreshKeepsSavedFlags` (acquire on a `noGravity=false` mob, refresh 10× → release restores `false`, never `true`), `refreshRefusesForeignCaster`, `refreshRefusesForeignEffect`, `refreshRefusesOtherKind`, `refreshMissingRecordFalse`; `WorldPauseTest.ownerNeverFrozen`, `secondBeginRefused`, `joinerFrozen`, `dimensionChangeReacquired`, `foreignControlNotOverridden`, `endsOnEveryPath` (DONE, OWNER_LEFT, SERVER_STOP, RELOAD, WATCHDOG → no `pauseId` record left in any level), `controlsSelfExpire`, `mobsOnlyWhenProtected`. Step 2 run `./gradlew :viltrumitecore:test --tests '*WorldPauseTest' --tests '*ControlManagerRefreshTest'` → FAIL. Step 3 implement. Step 4 PASS; manual: `/reload` during a test pause (dev command `/viltrumite pause 100`, op 2) → everyone moves again at once. Step 5 commit `feat(control): refresh and server-wide world pause`.


- [x] Automated Steps 1–5: RED → GREEN (20 tests), full build 1020 tests, checker 0 new / 4 known, diff check; task commit prepared.
- [ ] Manual Step 4: dev pause and `/reload` (not run; no server/game allowed).

### Task 2: `CleanupReason.ERASED` lifecycle audit and `freshBody`

**Files:**
- Modify: `core/hero/CleanupReason.java` (append `ERASED`; add `boolean losesBody()` = `this == DEATH || this == ERASED`), `core/hero/HeroEvents.java` (extract the body of `onPlayerRespawn` into `public static void freshBody(ServerPlayer)` — baseline, `restorePendingTarget`, flight sync, owner snapshot re-send, `hero.onRespawn` (Stage 1), and the existing Regulus Evangelium block moved verbatim, no new branch; `onPlayerRespawn` calls `freshBody`)
- Modify, every existing reason check (found by `grep -rn "CleanupReason\." core/`; each line gets an explicit decision, compile errors are not enough because most checks are plain `if`s):
  - `hero/control/ControlManager.java:281` (`reason != DEATH`), `:292` (STASIS dome link on DEATH), `:298` (`cleanupReleaseReason` switch) → `ERASED` like `DEATH` (`CASTER_DEATH`).
  - `hero/ViltrumiteHero.java:64`, `hero/homelander/HomelanderHero.java:223`, `hero/regulus/RegulusHero.java:426`, `hero/regulus/RegulusState.java:183` (`== HERO_CHANGE` branches) → keep for HERO_CHANGE only; add an `ERASED` branch that resets the same transient fields the death clone would drop (state fields not copied by each hero's `cloneHeroState`).
  - `hero/ironman/IronManHero.java:1005–1025` (switch + `!= DISCONNECT` checks) and `hero/ironman/IronManState.java:131–143` → `case ERASED` = `DEATH` behaviour (Hulkbuster `onDeath`, suit/channels reset).
  - Non-reason state on the same entity: `HeldInputs` of the player (clear), action timelines (clear), `ViltrumiteGrabbed` tag / legacy grab both directions (release), `ControlManager.releaseProjectilesFor(uuid)`, flight (`HeroFlightGrant.sync` after reset), owner sections (push `Section.EMPTY` then re-send), Reinhard `ReinhardState` (Reid → SHEATHED, mode kept, combo/grab cleared), Phoenix/`PersistentCooldowns` **kept**.
- Test: `test/hero/ErasedLifecycleTest.java`

**Interfaces:**
- Order on erase (server): `hero.cleanup(player, ERASED)` → generic resets above → `Limbo.send` (Task 4). On return: `Limbo.returnToSpawn` → `HeroEvents.freshBody(player)`.
- Rule written into `hero-seam.md`: "`ERASED` = death without a new entity. Treat like `DEATH`; never rely on the clone to drop state."

- [x] Step 1 tests: `losesBody` table; `cleanupReleaseReasonErasedIsDeath`; `ErasedLifecycleTest.sameEntityFreshAfterReturn` — for each hero (Viltrumite, Regulus, Homelander, Iron Man, Reinhard) build state "mid-everything" on one fake player, run `cleanup(ERASED)` + `freshBody`, assert transient state equals `newState()` except the documented kept fields; `noControlOwnedOrHeld`; `heldInputsEmpty`; `grabTagGone`. Step 2 FAIL. Step 3 implement. Step 4 PASS + full suite (other heroes' death tests unchanged). Step 5 commit `feat(hero): erased cleanup reason and fresh body path`.

- [x] Automated Steps 1–5: RED → GREEN (14 lifecycle tests), existing control tests unchanged, full build 1034 tests; checker 0 new / 4 known; diff check.

### Task 3: Erasure server timeline, frozen verdict, packet

**Files:**
- Create: `core/hero/reinhard/ability/WorldErasure.java`, `core/network/packet/ErasureS2CPacket.java` (register in `CoreMessages`, PLAY_TO_CLIENT)
- Modify: `ReinhardActions`, `ReinhardState.busy()` (true while the own erasure runs), `ReinhardAbilities.SHIPPED_STAGE = 6`, icon `world_erasure`, lang, `res/sounds.json` (`reinhard.erase_crack`, `reinhard.erase_inhale` — placeholders `block.glass.break` pitch 0.5 and `block.respawn_anchor.charge` until Stage 7)
- Test: `test/hero/reinhard/WorldErasureTest.java`

**Interfaces:**
- `ReinhardRules`: `ERASE_RANGE = 64`, `ERASE_LENGTH = 150` (7.5 s), `ERASE_CRACK_END = 30`, `ERASE_SHATTER_END = 70`, `ERASE_DARK_END = 110`, `ERASE_REMOVE_TICK = 90`, `ERASE_COOLDOWN = 1200`.
- Press (only Reid DRAWN): target = first living entity on the look ray ≤ 64 (`ProjectileUtil.getEntityHitResult`, blocks occlude; `PartEntity` → parent). Refusal without cooldown (`reinhard.refuse`): no target; `WorldPause.active`; target player in creative/spectator or already in limbo. Otherwise:
  - `Verdict verdict = DivineTargets.verdict(isDivine(target), isAbsolutelyInvulnerable(target))` — **frozen here**, stored in the `WorldErasure` instance; `boolean erase = verdict == MORTAL`.
  - `WorldPause.begin(server, caster, ERASE_LENGTH)`, cooldown starts (also when the target will stay, spec), action timeline `world_erasure`.
  - `ErasureS2CPacket(long startGameTime, ResourceKey<Level> targetLevel, int targetId, Vec3 targetPos, float bbWidth, float bbHeight, boolean humanoid, boolean erase)` to **all** players; re-sent to joiners with the same start (catch-up).
- Server tick of the instance (ticked from `WorldPause.tick`, not from the caster): at `ERASE_REMOVE_TICK`, if `erase` and the target is still present (same UUID, online for players):
  - mob / boss → `target.discard()` (no death event, loot, XP or message). Wither: its boss bar goes with `stopSeenByPlayer` — verified manually. Ender Dragon (`PartEntity` → parent): `discard()` alone would make `EndDragonFight` spawn a new dragon after it stops seeing one, so the erasure also calls `ServerLevel.getDragonFight().setDragonKilled(dragon)` (exit portal + gateway, no XP — the XP comes only from the death animation, which never runs); verify the method in the decompiled source; test with a fake fight.
  - player → Task 2 order, then `Limbo.send`.
  - target gone (logged out, unloaded) → nothing; clients still finish the scene with the frozen `erase` look.
  - `erase == false` → nothing, whatever the target's state now.
- End at 150 → `WorldPause.end(DONE)`; `WorldPause.end(...)` by any other reason before 90 cancels the removal.

- [x] Step 1 tests: `refusalsSpendNoCooldown` (each case), `secondCastRefusedWhileActive`, `removeTick90`, `mobDiscardedNoLoot` (fake: removal reason DISCARDED, no `die`), `dragonMarksFightKilled`, `partEntityResolvesParent`, `verdicts` (divine / absolute / another Reinhard → stays, cd spent), `verdictFrozenAtCast` (absolute at cast, mortal at 90 → stays; mortal at cast, absolute at 90 → erased), `casterLeavesMidway`, `targetEdgeCases` (target logs out before 90 → nothing; logs in later → normal). Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): world erasure timeline`.

- [x] Automated Steps 1–5: RED → GREEN (26 tests), full build 1060 tests, checker 0 new / 4 known; diff check. Shipping stage remains 5 until clip completion (Ruling).
- Ruling: quiet dragon defeat skips vanilla portal/gateway/egg generation to obey the unchanged-block spec; end-fight runtime/exit availability remains manual.

### Task 4: Limbo dimension, strip, timer, return

**Files:**
- Create: `core/hero/reinhard/limbo/Limbo.java`, `core/network/packet/LimboS2CPacket.java` (register in `CoreMessages`), `data/viltrumitecore/dimension_type/the_void.json`, `data/viltrumitecore/dimension/the_void.json`
- Modify: `HeroEvents` (player tick for limbo players, login/logout, `onLivingAttack` cancel inside limbo, `EntityTravelToDimensionEvent` cancel for limbo players except `Limbo` itself), lang
- Test: `test/hero/reinhard/LimboTest.java`, `test/hero/reinhard/TheVoidJsonTest.java`

**Interfaces:**
- `data/viltrumitecore/dimension_type/the_void.json` (complete 1.20.1 codec, every required field):
  ```json
  {
    "ultrawarm": false, "natural": false, "piglin_safe": false,
    "respawn_anchor_works": false, "bed_works": false, "has_raids": false,
    "has_skylight": false, "has_ceiling": false,
    "coordinate_scale": 1.0, "ambient_light": 0.0, "fixed_time": 18000,
    "min_y": 0, "height": 256, "logical_height": 256,
    "infiniburn": "#minecraft:infiniburn_overworld",
    "effects": "viltrumitecore:the_void",
    "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0
  }
  ```
- `data/viltrumitecore/dimension/the_void.json` (one air layer instead of an empty list; no features, no lakes, no structures):
  ```json
  {
    "type": "viltrumitecore:the_void",
    "generator": {
      "type": "minecraft:flat",
      "settings": {
        "biome": "minecraft:the_void",
        "layers": [ { "block": "minecraft:air", "height": 1 } ],
        "features": false, "lakes": false,
        "structure_overrides": []
      }
    }
  }
  ```
- `ReinhardRules`: `LIMBO_TICKS = 600`, `LIMBO_POS = (0.5, 128, 0.5)`.
- `Limbo.send(ServerPlayer p)` (scheduled with `server.execute` at the end of the tick): close container; `Inventory.clearContent()` (main, armour, offhand; the ender chest is a separate container — untouched); XP zeroed (`setExperienceLevels(0)`, `setExperiencePoints(0)`, `totalExperience = 0`); `teleportTo(theVoid, LIMBO_POS, yaw, 0)`; `setNoGravity(true)`, velocity 0; NBT `viltrumitecore:limbo { ticksLeft: 600 }`; `LimboS2CPacket(START, 600)`.
- **Timer semantics (decided):** `ticksLeft` is decremented only in the player's own tick while the player is online and in limbo. So the player always sees the full 30 s of text, independent of logout length or server downtime; nothing depends on `gameTime` or wall-clock. (Alternative, if the owner wants "30 s of real time even offline": store an epoch-millis deadline — one-line change, not the default.)
- `Limbo.tick(ServerPlayer)`: keep the position locked (teleport back if moved > 0.1), invulnerable, hunger frozen; `ticksLeft == 0` → `returnToSpawn`: overworld `getSharedSpawnPos()` with the vanilla spawn-radius safe-spot search, `setNoGravity(false)`, health/food full, remove the tag, `LimboS2CPacket(END)`, then `HeroEvents.freshBody(player)` (Task 2).
- Login with the tag → back into limbo (if not already there) with the stored `ticksLeft`; a tag with `ticksLeft <= 0` → `returnToSpawn`.
- No death message, no `Stats.DEATHS`, no drops: the player never dies.

- [x] Automated Step 1 tests: `TheVoidJsonTest.dimensionTypeDecodes` (parse with `DimensionType.DIRECT_CODEC` + `JsonOps` in the test — fails on any missing field), `flatSettingsDecode`; `LimboTest.stripKeepsEnderChest`, `xpZeroed`, `returnsAfter600OnlineTicks`, `offlineTimeDoesNotCount`, `returnToSpawn` (shared spawn, not bed), `persistsAcrossLogout`, `noPortalOut`, `immuneInLimbo`, `freshBodyCalledOnReturn`. Step 2 FAIL. Step 3 implement. Step 4 PASS (28 tests); Step 5 commit `feat(reinhard): limbo dimension`.
- [ ] Manual dedicated server start: no dimension codec errors; `/execute in viltrumitecore:the_void run tp @s 0 128 0` works (not run by instruction).
- Rulings: explicit server-END queue instead of possibly-inline execute; persistent pending strip; protection before all damage/queue paths; safe shared-spawn invoker; missing-dimension rescue. See progress journal for costs.

### Task 5: Post-shader uniforms for the erasure

**Files:**
- Modify: `mc/shaders/program/dash_impact.fsh` and `mc/shaders/program/dash_impact.json` (declare the uniforms), `res/shaders/post/dash_impact.json` (uniform defaults `0.0` next to `DashIntensity` …), `core/client/mixin/GameRendererDashMixin.java` (set the uniforms; keep the chain active while the erasure scene runs)
- Create: `core/client/reinhard/erasure/ErasureUniforms.java` (pure)
- Test: `test/client/reinhard/ErasureUniformsTest.java`

**Interfaces:**
- Uniforms: `EraseCrack` (0..1, Voronoi-edge crack lines from the screen centre and 5 seeded points, slight refraction), `EraseShatter` (0..1, image split into seeded Voronoi cells that fall with gravity, rotate slightly and darken; black behind), `EraseDark` (0..1, black mix), `EraseRebuild` (0..1, cells fly back into place, ease-out), `EraseSeed`. Clamped in the shader; the erase block is skipped when `EraseCrack + EraseShatter + EraseDark + EraseRebuild == 0`.
- `ErasureUniforms.at(int t, float pt)`: 0–30 crack 0→1; 30–70 shatter 0→1 (crack 1); 70–110 dark 1; 110–150 rebuild 1→0 and dark 1→0 over the first 15 t.
- The flight `sonic_boom` chain replaces the post effect; during the pause nobody flies (frozen) and the caster stands (action timeline), so `GameRendererDashMixin` gives the erasure priority and does not start the sonic chain while the scene runs.

- [x] Automated Step 1 tests: `zeroWhenIdle`, `phaseCurves` (ticks 0, 15, 30, 50, 70, 90, 110, 130, 150), `rebuildMirrorsShatter`, `clamped`, `postJsonDeclaresEraseUniforms` (reads both JSON files). Step 2 FAIL. Step 3 implement; Step 4 PASS (17 pure/JSON/priority checks). Step 5 commit `feat(reinhard): erasure post uniforms`.
- [ ] Manual: client startup, `F3+T`, GLSL compilation, both-view phase screenshots and unchanged idle image (not run; no screenshots fabricated).

### Task 6: Erasure client scene — silhouette, dust, sounds

**Files:**
- Create: `core/client/reinhard/erasure/ErasureScene.java`, `core/client/reinhard/erasure/GenericSilhouette.java`
- Modify: `ReinhardClient` / client packet handler (start on `ErasureS2CPacket`, catch-up by game time), `WorldPauseClient` (end hook)
- Test: `test/client/reinhard/ErasureSceneTest.java`

**Interfaces:**
- Timeline by `(gameTime − start + pt)`; ends itself at 150 + 20 if no end arrives.
- Sound: tick 0 `reinhard.erase_crack` (non-positional `SimpleSoundInstance.forUI`, heard in every dimension); tick 30 `SoundDuck.duck(this, MASTER, 0.0, 6, exempt = {reinhard.erase_crack, reinhard.erase_inhale})`; tick 110 `reinhard.erase_inhale` + release over 40 t.
- Silhouette (70–110), source chosen by a pure `SilhouetteSource.choose(boolean sameLevel, boolean entityResolved, boolean onScreen)`:
  - viewer in `targetLevel` and `level.getEntity(targetId)` resolves → the real entity rendered white fullbright into the GUI at its `ScreenProjector` position (centre if off screen);
  - otherwise (other dimension, unloaded, already removed) → `GenericSilhouette`: a flat white humanoid shape when `humanoid`, else a rounded box with the packet's `bbWidth : bbHeight` ratio, centred, 40% of the screen height. No model data over the network.
  - From 80 the silhouette breaks into seeded white pixels that drift up and fade; gone at 90. `erase == false` → it re-forms at 100 instead (the world "cannot erase" it).
- Caster: same scene over his screen, plus the clip (Task 8).

- [x] Automated Step 1 tests: `sourceGenericInOtherDimension`, `sourceRealWhenResolved`, `dustGoneAt90`, `staysReforms`, `duckReleasedOnEveryEnd`, `catchUpFromMidway` (join at t 60 → shatter, no crack sound replay). Step 2 FAIL. Step 3 implement. Step 4 PASS (21 new tests). Step 5 commit `feat(reinhard): erasure client scene`.
- [ ] Manual: two LAN clients (one Nether); zombie/player/Lion's Heart targets; real/generic model, sounds/dust and every cleanup path. Not run.

### Task 7: Limbo client — black void, text, heartbeat, flash, no hands

**Files:**
- Create: `core/client/reinhard/limbo/LimboOverlay.java` (HUD overlay, not a `Screen`, so chat and the pause menu still work), `core/client/reinhard/limbo/TheVoidEffects.java` (`DimensionSpecialEffects`: no sky, no clouds, black fog, `isFoggyAt` true; registered in `RegisterDimensionSpecialEffectsEvent` under `viltrumitecore:the_void`)
- Modify: lang (`viltrumitecore.limbo.line1..5` in all 11 files), `res/sounds.json` (`reinhard.limbo_heartbeat` — placeholder `entity.warden.heartbeat` until Stage 7)
- Test: `test/client/reinhard/LimboOverlayTest.java`

**Interfaces:**
- Five lines at the screen centre, fade-in 20 t, 100 t apart, the last stays. Draft (ru): «Тебя стёрли из мира.» / «Рейнхард ван Астрея пересоздал вселенную без тебя.» / «Всё, что ты нёс, исчезло вместе с тобой.» / «Но сильнейшая сила этого мира сжалилась над тобой.» / «Тебе позволено переродиться.» (en_us equivalent; wording reviewed with the user in the PR).
- Heartbeat: looping `TickableSoundInstance`, ~50 bpm, stops on `END` or on leaving the dimension.
- `END`: white flash 10 t in, 30 t out over the first overworld frames.
- While in limbo: `RenderHandEvent` cancelled for both hands (the HUD event does not control the world-pass hand), `RenderGuiOverlayEvent.Pre` cancelled for hotbar, crosshair, experience, health/food.

- [x] Automated Step 1 tests: `lineTimings`, `flashCurve`, `heartbeatStopsOnLeave`, `handsAndHudHiddenOnlyInLimbo` (pure predicate by dimension key). Step 2 FAIL. Step 3 implement. Step 4 PASS (25 new/expanded checks); Step 5 commit `feat(reinhard): limbo client`.
- [ ] Manual: full 30 s, no hands/HUD, chat/menu/F1, midpoint relog and overworld flash. Not run.
- Ruling: tickable pulses are scheduled as a 24-tick logical loop (50 bpm) instead of sample-duration-dependent looping delay; shared FlashOverlay has an attack/release envelope.

### Task 8: Clip, check, docs, PR

**Files:**
- Modify: `tools/assets/make_reinhard_clips.py` (clip `world_erasure`: Reid raised vertically before the face for 30 t, a slow single cut down and to the side at t 30, hold; first person: blade rises into view, cuts across at t 30), `ReinhardPoses`, `.agents/skills/add_hero/references/shared-toolkit.md` (`WorldPause`, `ControlManager.refresh`, `CleanupReason.ERASED`, `freshBody`), `.agents/skills/add_hero/references/hero-seam.md` (`ERASED` rule), `.agents/skills/animation-system/SKILL.md` (erasure uniforms), `.memory-bank/project.md` (limbo dimension, pause rules), `SESSION.md`, core version (minor +1)

- [x] Step 1: regenerate clips (`python3 tools/assets/make_reinhard_clips.py`), the diff is only the new clip.
- [x] Automated Step 2: full verification list and `NoHeroBranchTest` (final: 1209/1209 full, core 1197 + flight 12, selected core 570/570, Reinhard 459/459; checker 0 new / 4 known, diff clean; 4 Important fixed in one TDD pass, 2 Minor deferred).
- [ ] Manual Step 2: dedicated server start, `/reload` during an erasure (pause ends, no stuck freeze). Not run by user instruction.
- [ ] Step 3: in game both views; 3 players (caster, target, observer in the Nether); mob, Wither, player, Lion's Heart; spam the key during an active erasure (refusal, no cooldown); target logs out in limbo and back; erased Regulus gets the Evangelium back on return.
- [x] Step 4: commit `docs(reinhard): stage 6 notes`; immediate push `feat/reinhard-stage6`; PR «Рейнхард, этап 6: Стирание мира» against `feat/reinhard-stage5` (publication receipt/commit SHA in PR).

Acceptance targets: §11.10, §11.7 (§7.7 part). Runtime acceptance is not closed by pure tests; the unchecked manual steps above remain mandatory.

## Risks and pitfalls

- `teleportTo` to another dimension inside a tick iteration can crash: queue with `ServerEndTasks` for server END. Ruling: `server.execute(...)` may execute inline on the server thread, so it is not sufficient here.
- A data dimension loads with the world; old worlds get it on the next start. Removing the mod later leaves a harmless `dimensions/viltrumitecore/the_void` folder.
- Per-level `ControlManager`: a player who changes dimension during the pause leaves a record in the old level; it expires in ≤ 5 t and `end` releases it anyway; the new level gets a fresh acquire on the next tick.
- `SoundDuck` MASTER to 0 also mutes UI clicks for ~4 s — acceptable for a cinematic.
- The verdict is frozen on purpose: a Regulus who turns Lion's Heart on after the cast is erased. This is the readable rule ("the world already decided"); stated in the PR.
