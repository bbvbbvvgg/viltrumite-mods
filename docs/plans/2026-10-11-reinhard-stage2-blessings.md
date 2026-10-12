# Reinhard — Stage 2 (Blessings) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Overview: `docs/plans/2026-10-11-reinhard-overview.md`. Visual tasks follow `.agents/skills/animation-system/SKILL.md`.

**Goal:** all eight passive blessings of spec §4, active in every mode: Holy Sword parry, Phoenix, Arrow Protection, Cloudy Sky (5 air steps + super jump), Wind Reading, Detoxification, Mana Absorption, Protection Acquisition — and the divine layer order Adaptation → Phoenix → death.

**Architecture:** one class per blessing in `hero/reinhard/blessing/` (`Phoenix`, `Adaptation`, `Parry`, `ArrowWard`, `CloudStep`, `WindReading`, `Detox`, `ManaAbsorption`), each with a pure rule part and a thin Minecraft part, all wired from `ReinhardHero` hooks. Shared seams of this stage: `PersistentCooldowns`, `blocksEffect`, `projectileResponse` + `HeroProjectiles`, `airJumps` + `HeroAirJump` + `AirJumpClient`, `foreignResourceRegenFactor` + `HeroResourceRegen`.

**Tech Stack:** Forge events `LivingAttackEvent`, `LivingDamageEvent`, `ProjectileImpactEvent`, `MobEffectEvent.Applicable`, `TickEvent.PlayerTickEvent`; `OutlineTargets`, `HeroFx`, `PixelVfx`.

**Spec:** §3.2, §4.1–§4.8, §11.3, §11.4.

## Global Constraints

See the overview. Stage-specific:
- Blessings do not depend on the mode (spec §3.1 last line).
- Divine damage is never parried, resisted or floored; it only meets Adaptation and Phoenix (spec §3.2).

## Review Focus

1. Phoenix cooldown: dies to divine damage → revives; dies again within 10 min online → real death; relog / hero change / death do not reset the timer; offline time does not count — `PhoenixTest.offlineDoesNotTick`, `survivesHeroChange`.
2. Two Reinhards near one Iron Man: regen is ×0.5, not ×0.25 — `HeroResourceRegenTest.strongestWins`.
3. Parry vs a player behind Reinhard hitting him: no parry (only the front) — `ParryTest.backHitNotParried`.
4. Ghast fireballs, tridents, snowballs, potions and llama spit are deflected too, not only arrows; a lingering potion still never applies an effect (Detox) — `ArrowWardTest.allProjectileKinds`.
5. Air steps: leaving the ground by walking off a ledge allows 5 steps; touching water or a ladder resets the count — `CloudStepTest.groundOrClimbResets`.

---

### Task 1: Persistent cooldowns, Phoenix, Protection Acquisition (divine layers)

**Files:**
- Create: `core/hero/PersistentCooldowns.java`, `core/hero/reinhard/blessing/Phoenix.java`, `core/hero/reinhard/blessing/Adaptation.java`, `core/client/render/vfx/PhoenixFlameVfx.java`
- Modify: `core/hero/HeroEvents.java` (tick `PersistentCooldowns` for every online `ServerPlayer` in `PlayerTickEvent` END, server side), `core/hero/reinhard/ReinhardHero.java` (`absorbIncoming`, `clampFinalDamage` for divine sources; `snapshot` `extraCooldowns[EXTRA_PHOENIX]`), `core/hero/reinhard/ReinhardState.java` (adaptation state; saved, cleared on death through `cloneHeroState` not copying it), `core/client/reinhard/ReinhardHud.java` (Phoenix indicator `blessing_bearer.png`, grey while cooling), `res/sounds.json` (`reinhard.phoenix` → placeholder `item.totem.use`), lang
- Test: `test/hero/PersistentCooldownsTest.java`, `test/hero/reinhard/PhoenixTest.java`, `test/hero/reinhard/AdaptationTest.java`

**Interfaces:**
- `PersistentCooldowns`: data in `player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("ViltrumiteCooldowns")` (Forge copies `PERSISTED_NBT_TAG` on respawn; survives relog and hero change by design). `static int get(Player, String key)`, `static void set(ServerPlayer, String key, int ticks)`, `static void tick(ServerPlayer)` (decrements every key, removes at 0). Pure core: `static CompoundTag tickTag(CompoundTag)`.
- `ReinhardRules`: `PHOENIX_COOLDOWN = 12000` (10 min), `PHOENIX_KEY = "reinhard:phoenix"`, `PHOENIX_MIN_DIST = 6`, `PHOENIX_MAX_DIST = 10`, `ADAPT_HITS = 3`.
- `Adaptation` (pure): `record Adaptation(ResourceLocation streakType, int streak, Set<ResourceLocation> immune)`; `Adaptation onDivineHit(ResourceLocation type)` — same type → streak+1, reaching `ADAPT_HITS` adds the type to `immune` (the 3rd hit itself still lands); other type → streak 1 of the new type; `boolean immuneTo(type)`. Ordinary damage never calls it. NBT key `Adaptation`.
- Order inside `ReinhardHero` for a divine source: `absorbIncoming` → `Adaptation.immuneTo(type)` → `ABSORBED` (spark FX `HeroFx.flash`); otherwise register the hit, `PASS`. `clampFinalDamage` → when `amount >= health` and `Phoenix.ready(player)`: return 0 and call `Phoenix.revive(player, attacker)` (full HP, teleport, flames, cooldown set); not ready → amount unchanged (death).
- `Phoenix.revive`: target point = `SafeSpot` style search (Stage 3 Task 8 class is not available yet → local pure helper `Phoenix.pickPoint(origin, awayFrom, IntBiPredicate fits)`: ring 6–10 blocks, prefer the direction away from the attacker; fallback = origin); `setHealth(max)`, `clearFire`, fire-immunity 3 s, `PhoenixFlameVfx` (gold-red pixel flames rising for 30 t, sent as `HeroFx.flash` + `launch` with `material` null and a new `HeroFxS2CPacket` kind only if the existing kinds cannot show flames — first try `HeroFx.launch` with a fire block material).
- HUD: `blessing_bearer.png` 12×12 under the mode icons, alpha 40% while `extraCooldowns[EXTRA_PHOENIX] > 0` (seconds left as small text).

- [x] **Step 1: Write the failing tests** — `PersistentCooldownsTest.tickRemovesAtZero`, `keysIndependent`; `PhoenixTest.revivesWhenReady`, `secondDeathInsideCooldownKills`, `cooldownIs12000`, `offlineDoesNotTick` (no `tick` calls = no change), `survivesHeroChange` (store outside hero state), `pickPointAwayFromAttacker`, `pickPointFallsBackToOrigin`; `AdaptationTest.thirdHitGrantsImmunity`, `thirdHitStillLands`, `otherTypeResetsStreak`, `ordinaryDamageDoesNotTouch`, `clearedOnDeath` (clone does not copy).
- [x] **Step 2: Run** `./gradlew :viltrumitecore:test --tests '*PersistentCooldowns*' --tests '*Phoenix*' --tests '*Adaptation*' --no-daemon` → FAIL.
- [x] **Step 3: Implement**. Dev command check: `/viltrumite divine @s 100` → revive with flames at a point nearby; again → death; `/viltrumite divine @s 5` ×3 → the 4th does nothing.
- [x] **Step 4: Run** → PASS.
- [x] **Step 5: Commit** `feat(hero): persistent cooldowns` and `feat(reinhard): phoenix and protection acquisition`.

### Task 2: Blessing of the Holy Sword — front parry

**Files:**
- Create: `core/hero/reinhard/blessing/Parry.java`, clip `parry` in `tools/assets/make_reinhard_clips.py`
- Modify: `ReinhardHero.absorbIncoming` (before resist), `ReinhardPoses` (overlay `parry` while flag `PARRY` is set), `ReinhardRenderer` first person (short block flick of the main arm), `res/sounds.json` (`reinhard.parry` → placeholder `item.shield.block`, pitch 1.4)
- Test: `test/hero/reinhard/ParryTest.java`

**Interfaces:**
- `ReinhardRules`: `PARRY_CONE_COS = 0.5f` (±60° in front, horizontal), `PARRY_POSE_TICKS = 8`.
- `Parry.isMelee(boolean directIsAttacker, boolean projectile, boolean explosion, boolean divine)` → true only for a direct melee hit that is not divine. `Parry.inFront(Vec2 lookXZ, Vec2 toAttackerXZ)` → `dot ≥ PARRY_CONE_COS`.
- On parry: `DamageAbsorb.ABSORBED`, flag `PARRY` for 8 t (snapshot), `HeroFx.flash` at the contact point (between the two bodies, chest height) + gold-white pixel sparks (`HeroFx.shards` with `material` = gold block), sound. The attacker gets no knockback from the parry (spec says nothing; keep it pure defence).

- [x] **Step 1: Write the failing tests** — `frontMeleeParried`, `backHitNotParried`, `sideAt70DegreesNotParried`, `projectileNotParried`, `divineNotParried`.
- [x] **Step 2: Run** → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS. Manual: second player hits from the front (parry pose 3rd person, arm flick 1st person, spark, no HP loss) and from behind (normal resisted hit).
- [x] **Step 5: Commit** `feat(reinhard): holy sword parry`.

### Task 3: Projectile seam and Arrow Protection

**Files:**
- Create: `core/hero/ProjectileResponse.java`, `core/hero/HeroProjectiles.java`, `core/mixin/AbstractArrowAccessor.java` (invoker `getPickupItem`; common config), `core/hero/ProjectileMath.java` (pure), `core/hero/reinhard/blessing/ArrowWard.java`
- Modify: `core/hero/HeroDefinition.java`, `core/hero/HeroEvents.java` (`ProjectileImpactEvent`), `ReinhardHero`
- Test: `test/hero/HeroProjectilesTest.java`, `test/hero/reinhard/ArrowWardTest.java`

**Interfaces:**
- `enum ProjectileResponse { PASS, DEFLECT, CATCH, REFLECT }` (append only).
- `default ProjectileResponse projectileResponse(ServerPlayer self, Projectile projectile) { return PASS; }`.
- `HeroProjectiles.onImpact(ProjectileImpactEvent)`: hit result is an `EntityHitResult` on a `ServerPlayer` → ask the hook; non-PASS → `event.setImpactResult(SKIP_ENTITY)` (Forge 47 `ProjectileImpactEvent.ImpactResult`) and apply:
  - `DEFLECT`: new velocity = old speed × 0.6 turned 60–90° sideways (random side, away from the player's body centre), `hasImpulse = true`; the projectile keeps flying (no damage). `AbstractHurtingProjectile` (fireballs, wither skulls, dragon fireballs) also accelerates by its own `xPower/yPower/zPower` every tick, so its power vector gets the **same** rotation and the same × 0.6 (pure `ProjectileMath.rotateAndScale(Vec3, yawDeg, scale)` applied to both vectors); otherwise the fireball turns away and then curves back onto its old line.
  - `CATCH`: `AbstractArrow` with `pickup != CREATIVE_ONLY` → `getPickupItem()` copy to the inventory (drop if full). Fact: arrows shot by mobs (skeleton, stray, pillager) are `pickup == DISALLOWED`, so the old `== ALLOWED` rule would never give a skeleton arrow; `CREATIVE_ONLY` (Infinity, multishot side arrows, creative shooters) is excluded so catching never duplicates free ammo. Tipped/spectral arrows keep their item (from `getPickupItem`). `ThrownTrident` → the trident item (the entity is discarded, so no duplicate; loyalty is irrelevant after the catch). Any other projectile → discarded. Sound + hand FX (Stage 3 Task 4 adds the pose).
  - `REFLECT`: velocity toward the owner's eye position with the same speed (no owner → straight back), `setOwner(self)`; `AbstractHurtingProjectile` also gets its `xPower/yPower/zPower` reversed.
  - pure helpers: `static Vec3 deflected(Vec3 v, Vec3 toBody, boolean left)`, `static Vec3 reflected(Vec3 v, Vec3 from, @Nullable Vec3 ownerEye)`.
- `ArrowWard.response(boolean catchOpen, boolean reflectOpen)` → REFLECT if reflect open, else CATCH if catch open, else DEFLECT (spec §4.3: windows win over the passive; reflect beats catch when both are open — they cannot be open together in practice, but the function is total). Windows come from Stage 3; now both are false.

- [x] **Step 1: Write the failing tests** — `deflectTurnsSideways` (angle between old and new ≥ 60°), `deflectSlower`, `reflectAimsAtOwner`, `reflectWithoutOwnerGoesBack`, `deflectRotatesPowerVector` (fireball: angle(power, velocity) unchanged by deflect; simulate 20 ticks → no curve back), `catchSkeletonArrow` (DISALLOWED → item), `catchInfinityArrowGivesNothing` (CREATIVE_ONLY), `catchTippedKeepsPotion`, `ArrowWardTest.priority` (all 4 combinations), `allProjectileKinds` (response does not depend on the projectile class).
- [x] **Step 2: Run** → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS. Manual: skeleton, second player's bow, trident, ghast fireball, snowball, splash potion — all fly off sideways without damage or effect; other heroes still get hit.
- [x] **Step 5: Commit** `feat(hero): projectile response seam` and `feat(reinhard): arrow protection`.

### Task 4: Blessing of the Cloudy Sky — air steps and super jump

**Files:**
- Create: `core/hero/HeroAirJump.java`, `core/hero/AirJumpRules.java` (pure, shared by client and server), `core/network/packet/AirJumpSyncS2CPacket.java` (register in `CoreMessages`), `core/client/hero/AirJumpClient.java`, `core/hero/reinhard/blessing/CloudStep.java`, `core/client/render/vfx/AirStepVfx.java` (white pixel ring under the feet)
- Modify: `core/hero/HeroDefinition.java` (`airJumps`, `airJumpVelocity`), `core/hero/HeroAction.java` (append `AIR_STEP`), `core/network/packet/HeroInputC2SPacket.java` (route `AIR_STEP` to `HeroAirJump.onInput` like `JUMP`), `core/client/ViltrumiteCoreClient.java` (`AirJumpClient.tick`), `ReinhardHero` (`airJumps` 5, `airJumpVelocity`, `superJumpVelocity`, `onSuperJump`), `res/sounds.json` (`reinhard.air_step` from Stage 1, `reinhard.super_jump` → placeholder `entity.firework_rocket.launch`)
- Test: `test/hero/HeroAirJumpTest.java`, `test/hero/reinhard/CloudStepTest.java`

**Interfaces:**
- `default int airJumps(Player p) { return 0; }`, `default double airJumpVelocity(Player p) { return 0; }` (both sides).
- `AirJumpRules` (pure, the **same** rules on both sides): `static boolean resets(boolean onGround, boolean inWater, boolean inLava, boolean onClimbable, boolean flying, boolean passenger)` → true if any; `static boolean allow(int used, int max, boolean resetState, boolean anchored, boolean canAct)` → `!resetState && !anchored && canAct && used < max`.
- Predict → validate → confirm/correct (overview Global Constraints):
  1. `AirJumpClient.tick(Minecraft)`: jump key edge this tick (after vanilla consumed the ground jump), `AirJumpRules.allow(localUsed, airJumps, resets(...), anchored, canAct)` → **predict**: `deltaMovement.y = airJumpVelocity` (horizontal kept), `localUsed++`, `pendingSeq++`; send the press through the generic path `HeroInputC2SPacket(AIR_STEP, true)` (one packet, no second channel). `localUsed = 0` whenever `resets(...)`.
  2. `HeroAirJump.onInput` (server): own `used` counter per player, reset in the player tick by the same `AirJumpRules.resets(...)` from server-side state (`onGround()`, `isInWater()`, `isInLava()`, `onClimbable()`, `getAbilities().flying`, `isPassenger()`).
  3. Accepted → server sets `deltaMovement.y = airJumpVelocity`, `hurtMarked = true` (vanilla sends the authoritative `ClientboundSetEntityMotionPacket`; it matches the prediction, so no visible correction), `used++`, `fallDistance = 0`, FX `AirStepVfx` via `HeroFx.launch` at the feet, sound.
  4. Refused → no FX/sound; server sends `ClientboundSetEntityMotionPacket` with the server's current velocity (cancels the predicted boost) and `AirJumpSyncS2CPacket(used)` so the client counter matches the server again.
  - The server is the only source of the boost a cheating client can get: an extra AIR_STEP packet is refused and its velocity corrected; a client that boosts **without** sending the packet is plain movement cheating, which vanilla's movement checks cover like any fly hack (out of scope, noted in the PR).
- `ReinhardRules`: `AIR_STEPS = 5`, `AIR_STEP_VELOCITY = 0.62` (≈ 1.9 blocks each), `SUPER_JUMP_VELOCITY = 2.2` (≈ 20 blocks via `HeroSuperJump.apex`).
- `CloudStep.fallDistance` reset on each step (no fall damage anyway, but it keeps the landing shockwave of other systems quiet).

- [ ] **Step 1: Write the failing tests** — `HeroAirJumpTest.refusesAboveMax`, `refusesAnchored`, `AirJumpRulesTest.sameResetsBothSides` (water, lava, ladder, ground, flying, riding), `refusalSendsCorrection` (fake sender: refused press → one motion packet + one sync packet, no FX), `acceptedSetsServerVelocity`, `sixthPacketRefused` (5 accepted then refused while airborne), `CloudStepTest.fiveSteps`, `groundOrClimbResets`, `superJumpApexAbout20` (`HeroSuperJump.apex(2.2)` in 18–24), `stepHeightAbout2`.
- [ ] **Step 2: Run** → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS. Manual both views: 5 steps then nothing; in water / on a ladder the counter resets on both sides (6th step after touching a ladder works); with 200 ms simulated latency (`/forge` is not needed; LAN over a lagged proxy or a dev delay flag in `AirJumpClient`) the predicted step does not snap; super jump key; Regulus/Homelander/Iron Man jump unchanged (their `airJumps` = 0).
- [ ] **Step 5: Commit** `feat(hero): air jump seam` and `feat(reinhard): cloudy sky blessing`.

### Task 5: Blessing of Wind Reading — outlines

**Files:**
- Create: `core/hero/reinhard/blessing/WindReading.java`, `core/client/reinhard/WindReadingClient.java`
- Modify: `ReinhardHero.tick` (every 5 t), `ReinhardClient` (`OutlineTargets.register(WindReadingClient::colorOf)`)
- Test: `test/hero/reinhard/WindReadingTest.java`

**Interfaces:**
- `ReinhardRules`: `WIND_RADIUS = 32`, `WIND_BEHIND_COS = 0f` (behind = horizontal dot < 0), `WIND_RECENT_ATTACK_TICKS = 100`, `WIND_COLOR = 0xD8F4FF`.
- Pure `WindReading.isMarked(boolean invisible, boolean behind, boolean hostileToMe)`: `invisible || (behind && hostileToMe)`. `hostileToMe` = mob whose `getTarget() == self`, or a player who hit Reinhard within `WIND_RECENT_ATTACK_TICKS` (tracked from `onHurt`), or a projectile owner of a projectile flying toward him.
- Server pushes the marked entity ids to `OwnerSection.THREATS` (reused section; only the owner sees it; push `Section.EMPTY` before re-sending the same content to restart). Client: `colorOf(entity)` = `WIND_COLOR` if the local player is Reinhard and the id is in `ClientHeroData.section(THREATS)`, else 0 (pass to the next source).

- [ ] **Step 1: Write the failing tests** — `invisibleAlwaysMarked`, `behindAttackerMarked`, `behindPassiveNotMarked`, `frontAttackerNotMarked`, `outsideRadiusIgnored`.
- [ ] **Step 2: Run** → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS. Manual: invisible player in front and behind (outlined), zombie chasing from behind (outlined), cow behind (not); Iron Man scan and Homelander focus outlines still work for their owners.
- [ ] **Step 5: Commit** `feat(reinhard): wind reading blessing`.

### Task 6: Blessing of Detoxification — effect seam

**Files:**
- Create: `core/hero/reinhard/blessing/Detox.java`
- Modify: `core/hero/HeroDefinition.java` (`blocksEffect`), `core/hero/HeroEvents.java` (`MobEffectEvent.Applicable` → `Result.DENY` when the hook says so), `ReinhardHero` (`blocksEffect`, `enter` removes current harmful effects)
- Test: `test/hero/reinhard/DetoxTest.java`

**Interfaces:**
- `default boolean blocksEffect(Player self, MobEffectInstance effect) { return false; }`.
- `Detox.blocks(MobEffectCategory category, boolean isPoisonLike)` → `category == HARMFUL || isPoisonLike` (poison and wither are HARMFUL already; `isPoisonLike` covers mod effects tagged `forge:poison` if present). `HeroPassiveEffect` (hidden ambient effects) are BENEFICIAL and pass.

- [ ] **Step 1: Write the failing tests** — `harmfulBlocked`, `beneficialPasses`, `neutralPasses` (e.g. Glowing from a spectral arrow is NEUTRAL in 1.20.1 → passes, so outlines from others still work).
- [ ] **Step 2: Run** → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS. Manual: poison potion, wither skeleton, Warden darkness, pufferfish, suspicious stew — nothing applies; speed potion applies.
- [ ] **Step 5: Commit** `feat(hero): effect immunity hook` and `feat(reinhard): detox blessing`.

### Task 7: Blessing of Mana Absorption — resource regen seam

**Files:**
- Create: `core/hero/HeroResourceRegen.java`
- Modify: `core/hero/HeroDefinition.java` (`foreignResourceRegenFactor`), `core/hero/ironman/Energy.java` (`tick(float regenScale)`; the old `tick()` delegates with 1), `core/hero/ironman/IronManHero.java` (passes `HeroResourceRegen.scale(player)`), `core/hero/homelander/EyeHeat.java` (cooling × scale), `core/hero/homelander/HomelanderHero.java`, `ReinhardHero` (`foreignResourceRegenFactor`), `ReinhardRenderer` or `ReinhardAuraVfx` (faint blue motes drifting from an affected enemy to Reinhard — visual feedback, owner and target only)
- Test: `test/hero/HeroResourceRegenTest.java`, extend `test/hero/ironman/EnergyTest.java`, `test/hero/homelander/EyeHeatTest.java` (if present; else create)

**Interfaces:**
- `default float foreignResourceRegenFactor(Player self, Player other) { return 1f; }`.
- `HeroResourceRegen.scale(ServerPlayer player)`: minimum over all other players in the same level within `HeroResourceRegen.MAX_RADIUS = 64` of their hero's `foreignResourceRegenFactor(them, player)`; cached per player per server tick. Pure core `static float combine(float[] factors)` = min, clamped 0..1 (two Reinhards = ×0.5, not ×0.25).
- `ReinhardHero.foreignResourceRegenFactor(self, other)`: `0.5f` when `other != self`, distance ≤ `ReinhardRules.MANA_RADIUS = 24`, and not allied (`self.isAlliedTo(other)` false); else 1.
- Iron Man: regen part only (spend/drain unchanged). Homelander: heat cooling only.

- [ ] **Step 1: Write the failing tests** — `strongestWins`, `noReinhardIsOne`, `alliesUnaffected`, `outsideRadiusUnaffected`, `EnergyTest.regenScaled` (scale 0.5 → +5/s instead of +10/s), `EnergyTest.drainNotScaled`, `EyeHeatTest.coolingScaled`.
- [ ] **Step 2: Run** → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS; all Iron Man and Homelander tests green. Manual: Iron Man at 10 and 30 blocks from Reinhard — energy refills twice slower only inside 24.
- [ ] **Step 5: Commit** `feat(hero): foreign resource regen hook` and `feat(reinhard): mana absorption blessing`.

### Task 8: All modes, check, docs, PR

**Files:**
- Test: `test/hero/reinhard/BlessingModeMatrixTest.java`
- Modify: `.agents/skills/add_hero/references/hero-seam.md`, `shared-toolkit.md` (`HeroProjectiles`, `HeroAirJump`, `HeroResourceRegen`, `PersistentCooldowns`), `.memory-bank/project.md`, `SESSION.md`

- [ ] **Step 1: Write the failing test** — `everyBlessingIgnoresMode`: for each `ReinhardMode`, parry/arrow/detox/mana/adaptation/phoenix pure decisions give the same result.
- [ ] **Step 2:** Run → FAIL → make the hooks not read the mode → PASS.
- [ ] **Step 3:** Full verification list; dedicated server start (new common mixin `AbstractArrowAccessor`).
- [ ] **Step 4:** In-game list §4 in all three modes, both views.
- [ ] **Step 5:** Commit `docs(reinhard): stage 2 notes`; push `feat/reinhard-stage2`; PR «Рейнхард, этап 2: благословения».

Acceptance criteria closed: §11.3 (full), §11.4.

## Risks and pitfalls

- `ProjectileImpactEvent` in Forge 47 uses `ImpactResult` (not `setCanceled`); a cancelled impact that keeps the projectile inside the player re-hits next tick — move the deflected projectile 0.6 blocks out of the bounding box.
- Air steps are client-side movement; the server must not set velocity for them (double jump). Vanilla "moved too quickly" checks allow 0.62 b/t.
- `PERSISTED_NBT_TAG` is copied by Forge on respawn only if written inside that sub-compound; writing to the root persistent data is lost on death.
- Owner section `THREATS` is also used by Iron Man; it is per owner, so there is no clash, but the client must read it only when the local hero is Reinhard.
