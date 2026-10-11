# Reinhard — Stage 1 (Foundation: hero, chassis, divine seam, modes) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Overview, shared seams, coverage table and open questions: `docs/plans/2026-10-11-reinhard-overview.md`. Read it first. Stage 1 ships no jar: the model and poses come in Stage 1b (`AGENTS.md` §3.4).

**Goal:** Reinhard can be picked on the race screen, has his own chassis (20 HP, 99.9% resist, 1 HP floor that also holds against void and `generic_kill`, `/kill` still kills, void rescue, 1 HP/s regen, no fall damage, no knockback), the shared `divine` damage seam with a dev command, three modes on MMB with a 3 s cooldown and one sound per mode, one ability page per mode, mode icons next to the panel and his own knight `PanelStyle`.

**Architecture:** `hero/reinhard/` (`ReinhardHero`, `ReinhardRules`, `ReinhardState`, `ReinhardMode`, `ReinhardFlags`, `ReinhardAbility`, `ReinhardAbilities`, `ReinhardSounds`) + `client/reinhard/` (`ReinhardClient`, `ReinhardPanelStyle`, `ReinhardHud`). Shared seams of this stage: `clampUnblockableDamage` + `AdminKill`, `isDivine` / `isAbsolutelyInvulnerable` / `DivineTargets` / `DivineDamage`, `PanelStyle.drawAttachments`.

**Tech Stack:** Forge 47.3.0, MC 1.20.1, Java 17, Mixin 0.8.5, JUnit 5.9.3, Python 3 + Pillow (icons).

**Spec:** `docs/design/2026-10-11-reinhard-design.md` §2, §3, §5, §9.1.

## Global Constraints

See the overview. Stage-specific:
- `ReinhardHero.allowsLegacyAbilities` = false, `allowsFlight` = false, `grantsFlightAbility` = false, `usesLegacyRegen` irrelevant (kit off).
- Other heroes keep exact damage behaviour: the new unblockable hook defaults to "unchanged", `/kill` kills everyone as before.

## Review Focus

1. A `bypasses_invulnerability` hit on a non-Reinhard hero is unchanged (void kills Iron Man, Homelander, Regulus as before) — `HeroDamageLayersTest.unblockableDefaultUnchanged`.
2. `/kill` on Reinhard in survival kills him; `/kill @e` also kills; a void tick right after a `/kill` scope does not leak the admin flag — `AdminKillTest.scopeClosesOnThrow`.
3. A hit that is huge (`Float.MAX_VALUE`, e.g. `generic_kill` from another mod) leaves Reinhard at exactly 1 HP and never NaN — `ReinhardDamageTest.floorHandlesMaxValue`.
4. Mode switch spam: presses inside the 3 s cooldown give refusal feedback and do not change the page — `ModeCycleTest.cooldownRefuses`.
5. Hero change away from Reinhard removes the knockback-resistance modifier and resets the page to 0 — `ReinhardHeroTest.cleanupRemovesChassis`.

---

### Task 1: Hero id, skeleton, race button, branch test

**Files:**
- Modify: `core/hero/HeroId.java` (append `REINHARD("reinhard")` last), `core/hero/HeroRegistry.java` (`registerDefaults`), `core/client/gui/RaceSelectionScreen.java` (4th hero button `become_reinhard` → `HeroChoiceC2SPacket("reinhard")`; head preview = vanilla skin until Stage 1b Task 6), `core/client/ViltrumiteCoreClient.java` (`ReinhardClient.init()`), `res/lang/*.json`, `test/architecture/NoHeroBranchTest.java` (add `REINHARD` to `BRANCH`, `reinhard` to the hero-named path group, `Reinhard` to the mixin/vfx prefixes; allowlist unchanged)
- Create: `core/hero/reinhard/ReinhardHero.java`, `core/client/reinhard/ReinhardClient.java`
- Test: `test/hero/reinhard/ReinhardHeroTest.java`, extend `test/hero/HeroIdMigrationTest.java`

**Interfaces:**
- Produces: `HeroId.REINHARD`; `ReinhardHero implements HeroDefinition` with `INSTANCE` declared after its static fields; `allowsFlight`/`grantsFlightAbility`/`allowsLegacyAbilities` false; `hasAbilityPanel` true; `allowsAbilityPages` false; `allowsExternalControl(target, kind)` = true for every kind (spec gives no immunity to control; knockback is handled by the attribute in Task 3).
- Lang: `hero.viltrumitecore.reinhard` = "Reinhard van Astrea", `gui.viltrumitecore.race_selection.become_reinhard` = "Become Reinhard" (locales without a translation get the English text).

- [x] **Step 1: Write the failing tests** — `reinhardIsLastOrdinal` (`HeroId.values()[length-1] == REINHARD`), `fromKeyReinhard` (`fromKey("reinhard") == REINHARD`), `legacySetKeepsReinhard` (`legacySet(REINHARD, true) == REINHARD`), `noKitNoFlight` (`allowsLegacyAbilities`, `allowsFlight`, `grantsFlightAbility` all false for a null-safe stub player), `registered` (`HeroRegistry.get(REINHARD)` is `ReinhardHero.INSTANCE`).
- [x] **Step 2: Run** `./gradlew :viltrumitecore:test --tests '*Reinhard*' --tests '*HeroIdMigration*' --no-daemon` → FAIL (`REINHARD` not defined).
- [x] **Step 3: Implement** the enum value, the skeleton (empty `tick`, `handleInput`, `enter`, `cleanup`; `snapshot` returns `HeroPublicSnapshot.EMPTY` with `heroId` REINHARD), registration, race button and lang in all 11 locales; update `NoHeroBranchTest` patterns.
- [x] **Step 4: Run** the tests → PASS; `./gradlew build --no-daemon` green; `NoHeroBranchTest` green.
- [x] **Step 5: Commit** `feat(reinhard): hero id, skeleton and race button`.

### Task 2: Rules, mode, flags, state

**Files:**
- Create: `core/hero/reinhard/ReinhardRules.java`, `ReinhardMode.java`, `ReinhardFlags.java`, `ReinhardState.java`
- Test: `test/hero/reinhard/ReinhardRulesTest.java`, `ReinhardFlagsTest.java`, `ReinhardStateTest.java`

**Interfaces:**
- `ReinhardMode` (append only, saved by `key()`): `RESTRAINED("restrained")`, `KNIGHT("knight")`, `HOLY_SWORD("holy_sword")`; `next()` cycles; `page()` = ordinal; `fromKey(String)` → RESTRAINED on unknown.
- `ReinhardRules` stage-1 constants: `MAX_HEALTH = 20`, `RESIST = 0.999f`, `HP_FLOOR = 1f`, `REGEN_PER_SECOND = 1f`, `REGEN_INTERVAL = 20`, `KNOCKBACK_RESISTANCE = 1.0`, `MODE_COOLDOWN = 60`, `CHOP_BASE_DAMAGE = 20f` (used in Stage 4). Pure functions: `float resist(float raw)` = `raw * (1 - RESIST)`; `float floor(float health, float amount)` = `max(0, min(amount, health - HP_FLOOR))`, NaN/∞ safe.
- `ReinhardFlags` (bit layout of `heroFlags` for all stages; later stages only use reserved bits): 0–1 mode, 2 Reid drawn, 3 Reid drawing, 4 Reid sheathing, 5 parry pose, 6 catch window, 7 reflect window, 8 air step pulse, 9 grab holding, 10 hit-stop, 11 erasure caster, 12 chop left-forced, 13–31 reserved. `int mode(int flags)`, `int withMode(int flags, ReinhardMode m)`, `boolean has(int flags, int bit)`.
- Snapshot indexes (constants in `ReinhardFlags`): `EXTRA_MODE_CD = 14`, `EXTRA_PHOENIX = 15`; ability cooldowns at `ReinhardAbility.ordinal()` (0–13, Task 7).
- `ReinhardState`: `ReinhardMode mode`, `int modeCooldown`, `int regenTicks`, `int[] cooldowns` (length 14), `void tick()` (counts down), `save/load(CompoundTag)` under key `Reinhard` (`Mode` string, `ModeCd`, `Cooldowns` int array). `of(Player)` / `ensure(Player)` through `HeroPlayer.viltrumitecore$getHeroState` like `HomelanderState`.

- [x] **Step 1: Write the failing tests** — `resistLeavesOneThousandth` (`resist(1000) == 1.0f ± 1e-4`), `floorKeepsOneHp` (`floor(20, 50) == 19`), `floorAtOneHpIsZero` (`floor(1, 5) == 0`), `floorHandlesMaxValue` (`floor(20, Float.MAX_VALUE) == 19`, `floor(20, NaN) == 0`), `modeCycles` (R→K→H→R), `modeKeyRoundTrip`, `unknownModeKeyIsRestrained`, `flagsDoNotOverlap`, `withModeKeepsOtherBits`, `stateSaveLoadRoundTrip`, `cooldownsTickDown`.
- [x] **Step 2: Run** → FAIL.
- [x] **Step 3: Implement** the four classes with the values above.
- [x] **Step 4: Run** → PASS.
- [x] **Step 5: Commit** `feat(reinhard): rules, modes, flags and state`.

### Task 3: Chassis — HP, 99.9% resist, 1 HP floor, regen, knockback, fall

**Files:**
- Modify: `core/hero/HeroDefinition.java` (new `default void onRespawn(ServerPlayer player) {}`), `core/hero/HeroEvents.java` (`onPlayerRespawn` calls `HeroRegistry.get(player).onRespawn(player)` after the existing baseline/flight/snapshot lines; the Regulus block stays as it is), `core/hero/reinhard/ReinhardHero.java` (`enter`, `onRespawn`, `tick`, `cleanup`, `absorbIncoming`, `clampFinalDamage`, `cancelsFallDamage`, `save/load/cloneHeroState`), `.agents/skills/add_hero/references/hero-seam.md` (hooks table: `onRespawn`)
- Test: `test/hero/reinhard/ReinhardDamageTest.java`, `test/hero/HeroRespawnHookTest.java`

**Interfaces:**
- Consumes: `ReinhardRules.resist/floor`, `HeroDamageLayers.Target`, `DamageAbsorb.reduceTo`, `DivineDamage.isDivine(DamageSource)` (Task 5; until then a private `false` stub removed in Task 5).
- `absorbIncoming(self, src, raw)`: non-divine → `DamageAbsorb.reduceTo(ReinhardRules.resist(raw))`; divine → `PASS`.
- `clampFinalDamage(self, src, afterArmor)`: non-divine → `ReinhardRules.floor(self.getHealth(), afterArmor)`; divine → unchanged.
- Both run on the clean path too (`HeroDamage.applyCleanDamage` → `HeroDamageLayers.onClean`), so "clean" and "% of max HP" damage obey the same numbers (spec §3.1).
- `enter`: max-health base stays 20 (remove nothing, add no health modifier), add `Attributes.KNOCKBACK_RESISTANCE` modifier with a fixed constant `ReinhardHero.KNOCKBACK_MODIFIER_ID` (new random UUID, written once) amount 1.0, set health to max. `cleanup(HERO_CHANGE)` removes it. Fact: `PlayerEvent.Clone` runs `restoreHero()` + `cloneHeroState()`, and `restoreHero()` deliberately does **not** call `enter`, so nothing re-applies a chassis on the new body today. Therefore `ReinhardHero.onRespawn(player)` (new generic hook, Files) re-applies the modifier idempotently (`removeModifier(ID)` then `addPermanentModifier`) and sets health to max; `enter` and `onRespawn` share one private `applyChassis(player)`. `onRespawn` also runs on the end-portal return (`Clone` with `isWasDeath() == false` → `PlayerRespawnEvent` fires too) — idempotent, so harmless.
- `tick`: `regenTicks++`; every `REGEN_INTERVAL` → `heal(REGEN_PER_SECOND)` when below max.
- `cancelsFallDamage` = true.

- [x] **Step 1: Write the failing tests** (pure pipeline with a fake `Target` built from `ReinhardHero`'s two methods, the same harness as `HeroDamageLayersTest`): `swordHitLosesOneThousandth` (raw 10 → passOn 0.01), `percentDamageAlsoResisted` (raw = 50% of 20 → 0.01), `cleanPathResistedAndFloored` (`HeroDamageLayers.clean(target, NO_ATTACKER, false, 1e6f)` at health 1.5 → 0.5), `floorNeverBelowOne`, `divinePassesUnchanged` (divine flag → passOn raw, clamp raw), `HeroRespawnHookTest.defaultIsNoOp` (other heroes unchanged), `ReinhardHeroTest.respawnReappliesKnockbackOnce` (call `onRespawn` twice on a fresh fake attribute map → exactly one modifier).
- [x] **Step 2: Run** → FAIL.
- [x] **Step 3: Implement** the hooks, regen and modifier.
- [x] **Step 4: Run** → PASS. Manual (dev client, survival): zombie, sword from a second player (LAN), Iron Man Unibeam, `/damage @s 1000` — HP drops ≤ 0.1% per hit and never below 1; regen visible 1 HP/s; fall from 100 blocks — no damage; zombie hit — no knockback; die and respawn — still no knockback, `/attribute @s minecraft:generic.knockback_resistance get` = 1.
- [x] **Step 5: Commit** `feat(reinhard): chassis with resist, hp floor and regen`.

### Task 4: Unblockable seam, `/kill` scope, void rescue

Fact: `HeroDamageLayers.bypassesLayers` (`source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)`) makes `attack(...)` and `finalDamage(...)` return early, so void (`fell_out_of_world`) and `generic_kill` never reach a hero hook. `/kill` (`KillCommand.kill`) calls `Entity.kill()`, which for a `LivingEntity` deals `generic_kill` with `Float.MAX_VALUE`.

**Files:**
- Create: `core/hero/AdminKill.java`, `core/mixin/KillCommandMixin.java` (register in `viltrumitecore.mixins.json`), `core/hero/reinhard/VoidRescue.java`
- Modify: `core/hero/HeroDefinition.java` (new default), `core/hero/HeroDamageLayers.java` (`finalDamage` / `onDamage` / `clean`), `core/hero/reinhard/ReinhardHero.java` (`clampUnblockableDamage`, void check in `tick`)
- Test: `test/hero/HeroDamageLayersTest.java` (extend), `test/hero/AdminKillTest.java`, `test/hero/reinhard/VoidRescueTest.java`

**Interfaces:**
- `default float clampUnblockableDamage(ServerPlayer self, DamageSource source, float amount) { return amount; }` in `HeroDefinition` — runs only for bypassing sources, in `LivingDamageEvent` and on the clean path, never inside an `AdminKill` scope.
- `HeroDamageLayers.finalDamage(Target, boolean bypassesLayers, boolean adminKill, float)`: bypassing and not admin → `target.clampUnblockable(amount)` (new `Target` method, `NO_LAYERS` returns the amount); non-bypassing path unchanged. `Target.clampUnblockable` is wired to the hook in `target(self, source)`.
- `AdminKill`: `static void run(Runnable)` sets a thread-local depth counter (try/finally), `static boolean active()`.
- `KillCommandMixin`: `@Redirect` of the `Entity.kill()` call inside `KillCommand.kill(CommandSourceStack, Collection)` → `AdminKill.run(entity::kill)`.
- `ReinhardHero.clampUnblockableDamage` = `ReinhardRules.floor(health, amount)`.
- `VoidRescue`: `static OptionalInt surfaceY(int minY, int maxY, IntPredicate solidAt)` (pure, top-down scan of one column); `static BlockPos target(ServerLevel, BlockPos fallXZ)`: the column above the fall point; if empty, the nearest column with a top block within `ReinhardRules.VOID_SEARCH_RADIUS = 64` (spiral, step 4); if none, the level's spawn (End: the obsidian platform `ServerLevel.END_SPAWN_POINT`). `ReinhardHero.tick`: `y < level.getMinBuildHeight() - ReinhardRules.VOID_RESCUE_DEPTH (= 8)` → `teleportTo` 1 block above the surface, zero velocity, `fallDistance = 0`, air-step FX (`HeroFx.launch` + Stage 2 air-step sound slot `reinhard.air_step`, vanilla placeholder now).

- [x] **Step 1: Write the failing tests** — `unblockableDefaultUnchanged` (`NO_LAYERS`, bypass → amount unchanged), `unblockableHookFloors` (fake target floors to `health-1`), `adminKillSkipsHook`, `nonBypassPathUnchanged` (existing tests stay green), `AdminKillTest.scopeNests`, `scopeClosesOnThrow` (runnable throws → `active()` false after), `VoidRescueTest.findsTopBlock`, `emptyColumnReturnsEmpty`, `searchesNearestColumn` (pure spiral helper `nearestColumn(int r, BiPredicate<Integer,Integer> hasTop)`).
- [x] **Step 2: Run** `./gradlew :viltrumitecore:test --tests '*HeroDamageLayers*' --tests '*AdminKill*' --tests '*VoidRescue*' --no-daemon` → FAIL.
- [x] **Step 3: Implement**; update the `HeroDamageLayers` javadoc ("Sources that bypass invulnerability skip absorb and clamp; they reach only `clampUnblockableDamage`, except inside `AdminKill`").
- [x] **Step 4: Run** → PASS; all existing damage tests green. Manual: Reinhard jumps into the Overworld void → stays at ≥ 1 HP, is lifted onto the surface above; End void with no island above → platform; `/kill @s` kills; Homelander in the void dies as before.
- [x] **Step 5: Commit** `feat(hero): unblockable damage hook and admin kill scope` and `feat(reinhard): void rescue`.

### Task 5: Divine seam, absolute invulnerability, dev command

**Files:**
- Create: `data/viltrumitecore/damage_type/divine.json` (`message_id` `viltrumitecore.divine`, `exhaustion` 0, `scaling` `never`), `data/viltrumitecore/tags/damage_type/divine.json` (values: `viltrumitecore:divine`), `data/minecraft/tags/damage_type/bypasses_armor.json`, `bypasses_shield.json`, `bypasses_enchantments.json`, `bypasses_effects.json` (each `replace:false`, adds `viltrumitecore:divine`), `data/viltrumitecore/tags/entity_types/divine.json` (empty `values`), `core/hero/DivineTargets.java`, `core/hero/DivineDamage.java`
- Modify: `core/hero/HeroDefinition.java` (`isDivine`, `isAbsolutelyInvulnerable`), `core/hero/regulus/RegulusHero.java` (`isAbsolutelyInvulnerable` = `RegulusState.lionActive`), `core/hero/reinhard/ReinhardHero.java` (`isDivine` true; use `DivineDamage.isDivine`), `core/command/CoreCommands.java` (`/viltrumite divine <targets> <amount> [type]`, `requires(hasPermission(2))`), `res/lang/*.json` (`death.attack.viltrumitecore.divine`, `.player`, command feedback)
- Test: `test/hero/DivineTargetsTest.java`, `test/hero/reinhard/ReinhardDivineTest.java`

**Interfaces:**
- `default boolean isDivine(Player p) { return false; }`, `default boolean isAbsolutelyInvulnerable(Player p) { return false; }`.
- `DivineTargets`: `static final TagKey<EntityType<?>> DIVINE_ENTITIES` (`viltrumitecore:divine`); `static boolean isDivine(Entity e)` (player → hook; `PartEntity` → parent; else tag); `static boolean isAbsolutelyInvulnerable(Entity e)` (player → hook, else false); pure core `static Verdict verdict(boolean divine, boolean absolute)` → `DIVINE | ABSOLUTE | MORTAL` (used by Stage 4 `Annihilation` and Stage 6 Erasure).
- `DivineDamage`: `static final TagKey<DamageType> DIVINE_TYPES` (`viltrumitecore:divine`), `static final ResourceKey<DamageType> DIVINE`; `static boolean isDivine(DamageSource)`; `static DamageSource source(ServerLevel, @Nullable Entity attacker, ResourceKey<DamageType> type)`.
- The dev command applies `target.hurt(DivineDamage.source(level, sourceEntity, type), amount)`; `type` must be in `DIVINE_TYPES`, else error message. Until Stage 2, a lethal divine hit kills Reinhard (spec §3.2 order: Adaptation → Phoenix → death; the first two arrive in Stage 2 Task 1).

- [x] **Step 1: Write the failing tests** — `verdictOrder` (divine wins over absolute), `mortalByDefault`, `reinhardIsDivine`, `defaultHeroNotDivine`, `regulusAbsoluteOnlyInLion` (state stub), `divineSkipsResistAndFloor` (Task 3 harness with divine flag).
- [x] **Step 2: Run** → FAIL.
- [x] **Step 3: Implement** JSON, helpers, hooks, command, lang.
- [x] **Step 4: Run** → PASS. Manual: `/viltrumite divine @s 5` on Reinhard → −5 HP (no resist); `… 100` → death with the divine death message; on Iron Man in a mark → armor and mark ignored; on a zombie → normal damage; non-op player → command not available.
- [x] **Step 5: Commit** `feat(hero): divine damage seam and absolute invulnerability hook`.

### Task 6: Modes on MMB, cooldown, sounds, page

**Files:**
- Create: `core/hero/reinhard/ModeCycle.java`, `core/hero/reinhard/ReinhardSounds.java`
- Modify: `core/hero/HeroAction.java` (append `MODE_CYCLE`), `core/hero/reinhard/ReinhardHero.java` (`mouseAction`, `handleInput`, `canAct`, `onInputRefused`, `snapshot`, `enter`, `cleanup`), `core/ViltrumiteCore.java` or the sound registry used by `IronManSounds` (register events), `res/sounds.json`
- Test: `test/hero/reinhard/ModeCycleTest.java`

**Interfaces:**
- `mouseAction(MIDDLE, p)` = `MODE_CYCLE` for Reinhard (both sides; `HeroMouseInput` already cancels pick-block for a claimed MIDDLE). Other buttons null.
- `ModeCycle`: pure `static Result press(ReinhardMode current, int cooldown, boolean busy)` → `record Result(boolean accepted, ReinhardMode next)`; refused when `cooldown > 0` or `busy` (busy = Reid drawing / sheathing, World Cut or Erasure cast — wired in Stages 4–6 through `ReinhardState.busy()`, false now).
- Accepted: `state.mode = next`, `modeCooldown = MODE_COOLDOWN`, `setActivePage(next.page())`, sound `reinhard.mode_<key>` at the player (`playSound(null, …)`, everyone hears). No pose (spec §5). Leaving HOLY_SWORD calls `ReinhardState.onLeaveHolySword()` (no-op now; Stage 4 sheathes Reid there).
- Refused → `onInputRefused` plays `reinhard.refuse` (placeholder vanilla `block.note_block.bass`, pitch 0.6).
- `enter` → mode RESTRAINED, page 0; `cleanup(HERO_CHANGE)` → page 0. Login → page = saved mode (`loadHeroState`).
- `sounds.json` (final slot names, vanilla placeholders as `"type": "event"` entries): `reinhard.mode_restrained` → `block.amethyst_block.chime`, `reinhard.mode_knight` → `block.beacon.power_select`, `reinhard.mode_holy_sword` → `block.beacon.activate`, `reinhard.refuse`, `reinhard.air_step` → `entity.ender_dragon.flap` (pitch via code). Subtitles keys in lang.

- [x] **Step 1: Write the failing tests** — `pressCyclesAll`, `cooldownRefuses`, `busyRefuses`, `cooldownIsSixtyTicks`, `pageMatchesMode`.
- [x] **Step 2: Run** → FAIL.
- [x] **Step 3: Implement**.
- [x] **Step 4: Run** → PASS. Manual: MMB on a block — no pick-block, mode changes R→K→H→R, page indicator follows, presses inside 3 s → refusal sound and no change; page key does nothing; relog keeps the mode.
- [x] **Step 5: Commit** `feat(reinhard): mode cycle on middle mouse`.

### Task 7: Ability table, loadout, panel registration

**Files:**
- Create: `core/hero/reinhard/ReinhardAbility.java`, `core/hero/reinhard/ReinhardAbilities.java`
- Modify: `core/hero/reinhard/ReinhardHero.java` (`ownsAbility`, `heroInputSlots`, `heroActionFor`, `panelAbilities`, `abilityIcon`), `core/hero/HeroAction.java` (append the 14 actions below), `core/hero/HeroPublicSnapshot.java` (`availableActions` `int` → `long`: record component, all constructors, `actionAvailable` = `(availableActions & (1L << action.ordinal())) != 0`, `encode` writes the long, `decode` uses `Long.parseLong` so old int encodings still parse, `equals/hashCode` via `Long.hashCode`), constructor call sites (today every producer passes a literal `0`; the compiler accepts it for `long`, so they stay unchanged); new helper `static long actionMask(HeroAction... actions)` using `1L << ordinal` — Reinhard is the first producer of a real mask
- Test: `test/hero/reinhard/ReinhardAbilitiesTest.java`, `test/hero/HeroPublicSnapshotTest.java` (extend)

**Interfaces:**
- `HeroAction` append: `LIGHT_PUNCH, STEP_DASH, CATCH_PROJECTILE, FIST_COMBO, SKY_KICK, GRAB_THROW, DASH_BEHIND, STOMP, REFLECT_PROJECTILE, REID, SHEATH_STRIKE, CUT_RAIN, WORLD_CUT, WORLD_ERASURE` (14; `AIR_STEP` is appended in Stage 2). Current `HeroAction` has 25 values (`MODE_CYCLE` from Task 6 is ordinal 25), so these take ordinals 26–39 and `AIR_STEP` 40 → the `long` mask is required in this task, not later. A guard test fails when `HeroAction.values().length > 64`.
- `ReinhardAbility` enum (append only; ordinal = `extraCooldowns` index): `LIGHT_PUNCH, STEP_DASH, CATCH, FIST_COMBO, SKY_KICK, GRAB_THROW, DASH_BEHIND, STOMP, REFLECT, REID, SHEATH_STRIKE, CUT_RAIN, WORLD_CUT, WORLD_ERASURE`; fields `String id` (`reinhard:<snake>`), `ReinhardMode mode`, `int slot` (0-based, spec §6/§7.1 row order), `HeroAction action`, `int stage` (stage that implements it).
- `ReinhardAbilities.LOADOUT` (18): page 0 `light_punch, step_dash, catch, "", "", ""`; page 1 `fist_combo, sky_kick, grab_throw, dash_behind, stomp, reflect`; page 2 `reid, viltrumite:chop, sheath_strike, cut_rain, world_cut, world_erasure`. `defaultLoadout()` includes only ids with `stage <= ReinhardAbilities.SHIPPED_STAGE` (constant bumped by every stage; 1 now → empty pages) plus `viltrumite:chop` from Stage 4.
- `ownsAbility(id)` = id is in the shipped default loadout.
- `panelAbilities()` returns one entry per shipped ability: icon `textures/gui/ability/reinhard/<name>.png`, lang `ability.viltrumitecore.reinhard_<name>.name/.desc`, grey-out = `extraCooldowns[ordinal] > 0` or the stage condition (Stage 4 adds Reid conditions).

- [x] **Step 1: Write the failing tests** — `slotsFollowSpecOrder` (every enum value's `mode.page()*6 + slot` equals its index in `LOADOUT`), `idsUnique`, `actionsUnique`, `onlyShippedOwned` (SHIPPED_STAGE 1 → owns nothing), `chopOnHolySwordSlot2` (`LOADOUT[13] == "viltrumite:chop"`), `ordinalFitsExtraCooldowns` (`values().length <= ReinhardFlags.EXTRA_MODE_CD`), `HeroPublicSnapshotTest.highOrdinalDoesNotAliasBitZero` (only `WORLD_ERASURE` available → `LIONS_HEART` not available), `decodesLegacyIntMask`, `encodeRoundTripLong`, `actionCountFitsMask` (≤ 64).
- [x] **Step 2: Run** → FAIL.
- [x] **Step 3: Implement**.
- [x] **Step 4: Run** → PASS; `check_hero_resources.py --hero reinhard` → 0 new problems (no shipped ability yet, so no icons required).
- [x] **Step 5: Commit** `feat(reinhard): ability table and loadout per mode`.

### Task 8: Knight panel style, mode icons, HUD attachments

**Files:**
- Create: `core/client/reinhard/ReinhardPanelStyle.java`, `core/client/reinhard/ReinhardHud.java`, `tools/assets/make_reinhard_icons.py`, `tools/assets/reinhard_sources.md`, `bin/textures/gui/reinhard/{emblem,mode_restrained,mode_knight,mode_holy_sword,hero}.png.b64`
- Modify: `core/client/hero/PanelStyle.java` (new default), `core/client/hero/DefaultPanelStyle.java` (nothing), the panel drawer that calls the style (`ViltrumiteInGameHudMixin`: call `drawAttachments` after the bars), `core/client/reinhard/ReinhardClient.java` (`PanelStyles.register(REINHARD, ReinhardPanelStyle.INSTANCE)`)
- Test: `test/client/reinhard/ReinhardHudTest.java`

**Interfaces:**
- `PanelStyle.default void drawAttachments(GuiGraphics g, Bounds abilityBar, Player player, HeroPublicSnapshot snapshot) {}` — draws only outside the fixed layout.
- `ReinhardPanelStyle`: white frames, gold (`0xE8C15A`) rim, blue (`0x2F5DA8`) cells, `knight_emblem.png` (16×16 from the archive, copied unchanged) at the top of the ability bar; `accentColor` gold. `drawAttachments` → `ReinhardHud.draw`.
- `ReinhardHud`: pure layout `static Layout layout(Bounds abilityBar)` → three 12×12 icon rects to the right of the bar, top-aligned, 2 px gap, and a 40×3 bar rect under them; `draw(...)`: active mode icon full colour with a gold outline, others 40% alpha; the Reid bar is drawn only when `snapshot.resource() > 0` (Stage 4), fill = `resource / REID_IDLE_TICKS`.
- `make_reinhard_icons.py` (reads the user archive path given by `--assets DIR`): copies `knight_emblem.png` and `reinhard.png`; draws the three 12×12 mode icons in the house pixel style (closed fist on white, single gauntlet on blue, sword hilt on gold); later stages add ability icons to the same script (`--only <name>` to rebuild one). Writes PNG + `.b64`. `reinhard_sources.md` lists every committed asset: source file in the archive, transformation, script.

- [x] **Step 1: Write the failing test** — `layoutRightOfBar` (rects start at `bar.x + bar.width + 2`), `iconsDoNotOverlap`, `barUnderIcons`, `reidBarHiddenAtZero`.
- [x] **Step 2: Run** `./gradlew :viltrumitecore:test --tests '*ReinhardHud*' --no-daemon` → FAIL.
- [x] **Step 3: Implement**; run `python3 tools/assets/make_reinhard_icons.py --assets /path/to/reinhard_assets`.
- [x] **Step 4: Run** → PASS. Manual: panel in knight style, three mode icons next to it, the active one lit, switching with MMB updates them; other heroes' panels unchanged (Iron Man holographic, default).
- [x] **Step 5: Commit** `feat(reinhard): knight panel style and mode hud`.

### Task 9: Stage check, docs, PR

**Files:**
- Modify: `.agents/skills/add_hero/references/hero-seam.md` (hooks `clampUnblockableDamage`, `isDivine`, `isAbsolutelyInvulnerable`; `PanelStyle.drawAttachments`), `.memory-bank/project.md` (facts: unblockable hook + `AdminKill`; divine tags; Reinhard page = mode), `SESSION.md` (Russian entry)

- [x] **Step 1:** Full verification list from the overview (test, build, checker, `git diff --check`). Start the dedicated server once: `./gradlew :viltrumitecore:runServer --no-daemon` reaches "Done" (new mixin `KillCommandMixin` is common, new client classes only in `client/`).
- [x] **Step 2:** In-game list (first and third person where visible): race button; HP/resist/floor/regen/fall/knockback; void rescue; `/kill`; dev divine command; MMB modes + sounds + cooldown; HUD; other heroes unchanged (Homelander void death, Iron Man panel, Regulus heart key on MMB still works).
- [x] **Step 3:** Commit `docs(reinhard): stage 1 notes`. Push with `push_files` to `feat/reinhard-stage1`; PR «Рейнхард, этап 1: выбор героя, живучесть, режимы» (body starts with «Для игрока»).

Acceptance criteria closed: §11.2, §11.5, §11.3 (death part; Phoenix/Adaptation in Stage 2), §11.12 (partial). §11.1 is partial (button only; model in 1b).

## Risks and pitfalls

- `KillCommandMixin` must target the exact `KillCommand.kill` descriptor in 1.20.1 Mojang names; `defaultRequire` is 1, a bad target crashes the server. Verify with `javap` on the mapped Minecraft jar in the Gradle cache.
- `MIDDLE` is the Regulus heart key mapping; it is shared. `HeroMouseInput` already routes MIDDLE per hero (`mouseAction`), so Regulus keeps it. Do not add a second key mapping.
- `ACTIVE_PAGE` is synced entity data; set it only on the server.
- `PanelStyle.INSTANCE` static order (overview risks).
