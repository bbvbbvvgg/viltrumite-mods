# Reinhard — Stage 4 (Holy Sword part 1: Reid, Lethal Chop, Sheath Strike, Rain of Cuts) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Overview: `docs/plans/2026-10-11-reinhard-overview.md`. Every visual task follows `.agents/skills/animation-system/SKILL.md`.

**Goal:** the Holy Sword page slots 1–4: drawing Reid (a 6 s world event), sheathing (1 s) and auto-return (60 s without combat, and when leaving the mode), the Reid strike contract, Lethal Chop as the kit chop with Reinhard's visual variant, Sheath Strike and Rain of Cuts.

**Architecture:** `hero/reinhard/reid/` (`Reid` state machine, `ReidDrawEvent` server scene, `GravityLift`), `hero/reinhard/ability/` (`SheathStrike`, `CutRain`), `client/reinhard/reid/` (`ReidDrawScene`, `ReidAura`). Shared seams of this stage: `Annihilation` (Reid strike), `ChopStyle` (kit chop variant), `CarveJobs` + `CarveBudget` (budgeted block removal), client `SkyTint` and `SoundDuck`.

**Tech Stack:** `HeroFx`, `CameraShake`, `PixelVfx`, `ControlManager` FREEZE (stun), anim core clips, new S2C `ReidSceneS2CPacket` through `CoreMessages`.

**Spec:** §1.3, §5 (leaving the mode), §7.1–§7.5, §9.1 (Reid bar), §9.2, §10.2 (chop), §11.7, §11.8, §11.11.

## Global Constraints

See the overview. Stage-specific:
- Homelander's Lethal Chop must look and sound exactly as before (§11.11): `ChopStyle.DEFAULT` reproduces today's constants and is the default of the hook.
- The Reid strike never pierces absolute invulnerability (Lion's Heart) and never touches divine targets (§3.3).
- The draw event lifts others only through `HeroRegistry.allowsImpulse`; it never changes blocks.

## Review Focus

1. Reinhard dies, logs out, changes hero or dimension during the 6 s draw: gravity of every lifted entity is restored, no entity is left floating with `noGravity`, the sky/sound recover on every client — `GravityLiftTest.restoreOnEveryEnd`, client `ReidDrawSceneTest.endsWithoutPacket` (timeout).
2. Two Reinhards draw at once near each other: lifts do not stack above 3 blocks — `GravityLiftTest.overlapCapsAtMax`.
3. Lethal Chop with Reid drawn always uses the left hand even if a forged packet says right — `ChopStyleTest.serverForcesLeft`.
4. Reid strike on a Hulkbuster, a totem holder, a mark, a creative player and a Regulus in Lion's Heart — `AnnihilationTest` (all verdicts) + manual.
5. Auto-return timer: hitting or being hit resets the 60 s; standing still for 60 s sheathes; the HUD bar matches the server — `ReidTest.combatResetsIdle`.

---

### Task 1: Reid strike contract — `Annihilation`

**Files:**
- Create: `core/hero/Annihilation.java`, `data/viltrumitecore/damage_type/annihilation.json`, add `viltrumitecore:annihilation` to `data/minecraft/tags/damage_type/{bypasses_invulnerability,bypasses_armor,bypasses_shield,bypasses_enchantments,bypasses_effects,bypasses_resistance}.json`, lang death messages
- Modify: `core/hero/HeroDamageLayers.java` (an `Annihilation.active()` scope also skips `clampUnblockableDamage`, same as `AdminKill`)
- Test: `test/hero/AnnihilationTest.java`

**Interfaces:**
- `enum Annihilation.Outcome { KILLED, BLOCKED_ABSOLUTE, IMMUNE_DIVINE, SKIPPED }`.
- Pure `static Outcome decide(DivineTargets.Verdict v, boolean creativeOrSpectator, boolean alive)`: DIVINE → IMMUNE_DIVINE; ABSOLUTE → BLOCKED_ABSOLUTE; creative/spectator or dead → SKIPPED; else KILLED.
- `static Outcome strike(ServerPlayer attacker, Entity target)`: resolves `PartEntity` → parent; KILLED → inside an `Annihilation` scope `target.hurt(source(annihilation, attacker), Float.MAX_VALUE)`; if still alive (a mod cancelled the event) → `setHealth(0)` + `die(source)`. Bypassing invulnerability means: armor, shields, every hero layer (Hulkbuster, marks, nano), totems (vanilla skips the totem for `bypasses_invulnerability`), Resistance — all ignored. BLOCKED_ABSOLUTE → a gold spark FX at the target (`HeroFx.flash`), no damage.

- [ ] Step 1 tests: every `decide` combination; `scopeSkipsUnblockableClamp` (layers test with a flooring fake target inside the scope → full amount). Step 2 FAIL. Step 3 implement. Step 4 PASS; manual with a temporary dev subcommand `/viltrumite annihilate <target>` (op 2; kept for testing future heroes) on: zombie, Wither (dies), Ender Dragon part, Iron Man Hulkbuster, player with a totem, Regulus in Lion's Heart (survives, spark), another Reinhard (immune). Step 5 commit `feat(hero): annihilation strike`.

### Task 2: Reid state machine, slot conditions, auto-return

**Files:**
- Create: `core/hero/reinhard/reid/Reid.java`
- Modify: `ReinhardState` (`Reid reid`; `busy()` = drawing/sheathing; `onLeaveHolySword()` → `reid.sheathe()`), `ReinhardHero` (`tick`, `cleanup`, `onHurt`, `modifyOutgoingDamage` notify combat, `snapshot` flags `REID_DRAWN/DRAWING/SHEATHING` + `resource` = idle ticks left, `panelAbilities` grey-out by Reid state), `ReinhardAbilities.SHIPPED_STAGE = 4`, `ReinhardActions` (REID toggle)
- Test: `test/hero/reinhard/ReidTest.java`

**Interfaces:**
- `enum Reid.Phase { SHEATHED, DRAWING, DRAWN, SHEATHING }` (saved as name; DRAWING/SHEATHING load as SHEATHED).
- `ReinhardRules`: `REID_DRAW_TICKS = 120`, `REID_SHEATHE_TICKS = 20`, `REID_IDLE_TICKS = 1200`, `boolean canDraw(...)` = true (spec §7.1: any time now; the "worthy" list is §12).
- `Reid`: `boolean toggle()` (SHEATHED → DRAWING; DRAWN → SHEATHING; others ignored), `void tick()` → `Event` (`DRAW_DONE`, `SHEATHE_DONE`, `AUTO_RETURN`, none), `void combat()` (idle back to 1200 while DRAWN), `void sheatheNow()` (mode exit: DRAWN → SHEATHING; DRAWING → cancel to SHEATHED with the scene ending early), `void reset()` (death/hero change: SHEATHED at once).
- Slot conditions (spec §7.1): Lethal Chop always; Sheath Strike only SHEATHED; Rain of Cuts, World Cut, World Erasure only DRAWN. Not allowed → refusal sound, grey icon.
- Combat = Reinhard dealt damage to a living entity, took non-environment damage, or started an ability.

- [ ] Step 1 tests: `drawTakes120`, `sheatheTakes20`, `autoReturnAfter1200Idle`, `combatResetsIdle`, `leaveModeSheathes`, `cancelDrawingOnLeave`, `slotConditions` (table), `transientPhasesLoadSheathed`. Step 2 FAIL. Step 3 implement. Step 4 PASS. Step 5 commit `feat(reinhard): reid state machine`.

### Task 3: Draw event — server scene

**Files:**
- Create: `core/hero/reinhard/reid/ReidDrawEvent.java`, `core/hero/reinhard/reid/GravityLift.java`, `core/network/packet/ReidSceneS2CPacket.java` (register in `CoreMessages`)
- Modify: `ReinhardHero.tick` (drive the scene while DRAWING), `canAct` (no other action while DRAWING), self root while DRAWING: `ReinhardClient` clears movement input in `MovementInputUpdateEvent` when the local flag `REID_DRAWING` is set, the server zeroes horizontal velocity (spec §7.3.1: he stands still), lang `chat.viltrumitecore.reinhard.reid_drawn` ("The Dragon Sword Reid is drawn"), `res/sounds.json` (`reinhard.reid_rumble`, `reinhard.reid_unsheathe`, `reinhard.reid_choir`, `reinhard.reid_far` — vanilla placeholders `ambient.basalt_deltas.mood`, `item.trident.riptide_1`, `block.beacon.activate`, `entity.wither.spawn` low volume)
- Test: `test/hero/reinhard/GravityLiftTest.java`, `ReidDrawTimelineTest.java`

**Interfaces:**
- Timeline (`ReinhardRules`, ticks from the press): 0 start (shake, lift, dark sky, silence, cracks begin) · 20–100 crack ring grows, pillar of light rises, cloud funnel opens · 110 "Reid trembles" peak · 120 final: flash, wave (no damage, knockback 1.2 outward within 24, `allowsImpulse`), choir, lift ends. All effects from tick 0 (spec §7.3).
- `GravityLift`: radius `REID_LIFT_RADIUS = 96`, max rise `REID_LIFT_MAX = 2.5` blocks over 120 t, profile `rise(t) = MAX · smoothstep(t / 100)`; per entity (items, mobs, players except Reinhard) remember start Y and the original `noGravity`; each tick set `noGravity(true)` and vertical velocity = `rise(t+1) − currentOffset` clamped 0..0.1; players get the velocity through `hurtMarked`; at the end or on any abort restore `noGravity` and give a tiny downward nudge. Entities entering the radius late start from their current Y. Pure core `static double velocity(int t, double currentOffset)`.
- `ReidSceneS2CPacket(int casterId, BlockPos origin, long startGameTime, int phase)` sent to every player within 96 blocks of the origin in the same level (start and end/abort). A late joiner inside the radius gets it on the next 20-tick refresh.
- Chat + far sound: at tick 120 every player on the server gets the chat line (`sendSystemMessage`) and the far sound at their own position (volume 0.4, private `ClientboundSoundPacket`).

- [ ] Step 1 tests: `riseNeverAboveMax`, `riseReachesAboutMax`, `velocityNonNegative`, `restoreOnEveryEnd` (abort at t 30 → saved flags restored), `overlapCapsAtMax` (two lifts → offset ≤ 3), `timelinePhases`. Step 2 FAIL. Step 3 implement. Step 4 PASS; manual: items, cows, a LAN player rise ~2.5 blocks and fall back; abort by death mid-scene. Step 5 commit `feat(reinhard): reid draw event server scene`.

### Task 4: Draw event — client scene (shake, sky, silence, cracks, pillar, funnel, flash)

**Files:**
- Create: `core/client/render/vfx/SkyTint.java` (shared: sources with priority; read by `client/mixin/SkyTintMixin` on `ClientLevel.getSkyColor` / `getCloudColor` and a `ViewportEvent.ComputeFogColor` handler; rain fade by `client/mixin/LevelRainMixin` on `Level.getRainLevel/getThunderLevel` guarded by `isClientSide`), `core/client/render/vfx/SoundDuck.java` (shared: per-category volume multiplier with fade; `client/mixin/SoundEngineVolumeMixin` on `SoundEngine.getVolume(SoundSource)`), `core/client/reinhard/reid/ReidDrawScene.java` (crack ring decals, pillar, funnel, flash), clips `reid_draw` (6 s slow draw, standing still), `reid_sheathe` (1 s reverse)
- Modify: `viltrumitecore.client.mixins.json`, `ReinhardRenderer` (blade position from draw progress: lerp from `SHEATHED_OFFSET` along the sheath axis to the right hand), first person (`RenderArmEvent`: right arm pulls the blade up in front of the view)
- Test: `test/client/render/SkyTintTest.java`, `test/client/render/SoundDuckTest.java`, `test/client/reinhard/ReidDrawSceneTest.java`

**Interfaces:**
- `SkyTint.push(Object owner, int priority, Supplier<Tint> tint)` / `remove(owner)`; `record Tint(float r, float g, float b, float darken, float rain)`; strongest priority wins, blended by its weight. Pure `static Tint mix(Tint base, Tint over, float w)`.
- `SoundDuck.duck(Object owner, SoundSource category, float target, int fadeTicks)` / `release(owner)`; volume factor = min over owners; pure `static float factor(List<Float> targets)`.
- `ReidDrawScene` (state-driven by the packet + game time; ends itself 40 t after the expected end if no end packet arrives): `CameraShake.addAt(origin, power 2.5 → 4.0, range 96)` every tick; SkyTint dark (`darken 0.75`, rain 0); SoundDuck MASTER except our own `reinhard.*` sounds (they are played on `SoundSource.PLAYERS` with an exempt flag in the mixin) to 0.25; crack ring: dark pixel crack lines on block tops around the origin, radius growing 2 → 14 (pixel style, `PixelVfx` quads on the ground, depth test on); pillar: white column of billboard pixels from the origin to y = cloud height + 40, width pulsing; funnel: a ring of grey cloud-coloured pixels at cloud height opening from r 0 to r 40 around the pillar; final flash: white screen-free flash = `HeroFx.flash` at the hilt + ring shockwave `HeroImpactFx` kind already sent by the server.
- Draw clip time = `(gameTime − start + pt) / 20` from the packet, so a relog mid-draw is correct.

- [ ] Step 1 tests: `strongestTintWins`, `tintFadesOut`, `duckMinOfOwners`, `releaseRestoresOne`, `sceneEndsWithoutPacket` (timeout), `crackRadiusGrows`. Step 2 FAIL. Step 3 implement. Step 4 PASS; manual both views, at 10 / 60 / 120 blocks (outside 96 → nothing but the chat line and far sound), Oculus on (sky tint may be overridden by the shader pack — record the result in the PR). Step 5 commit `feat(client): sky tint and sound duck helpers` and `feat(reinhard): reid draw scene`.

### Task 5: Reid drawn state — blade in hand, gold aura, sky tint, HUD bar, sheathing

**Files:**
- Create: `core/client/reinhard/reid/ReidAura.java`; clip `idle_holy_drawn` (sword in the right hand); icon `reid` (sheathed / drawn variants via `abilityIcon(id, player)`)
- Modify: `ReinhardRenderer` (blade bone follows the right hand when DRAWN; SHEATHING uses `reid_sheathe`), first person (blade in the right hand in view), `ReinhardHud` (Reid bar: `resource / REID_IDLE_TICKS`, gold), `ReinhardAuraVfx` (Holy Sword drawn → full gold aura instead of rare sparks)
- Test: `test/client/reinhard/ReidAuraTest.java`

**Interfaces:**
- `ReidAura`: gold `PixelVfx.bodyShell` shimmer (alpha only, skill §8) + rising gold motes; `SkyTint.push(this, priority 1, warm gold over the caster)` for players within 64 of the caster (weight by distance); removed when the flag clears.
- Sheathing: 1 s calm reverse clip, soft click sound placeholder `item.armor.equip_chain`.

- [ ] Step 1 tests: `auraOnlyWhenDrawn`, `tintWeightFallsWithDistance`, `barMatchesResource`. Step 2 FAIL. Step 3 implement. Step 4 PASS; manual both views: drawn idle, walk with the sword, auto-return after 60 s, MMB away from Holy Sword sheathes, bar empties correctly. Step 5 commit `feat(reinhard): reid drawn state visuals`.

### Task 6: Lethal Chop — kit chop with Reinhard's style (`ChopStyle` seam)

**Files:**
- Create: `core/hero/ChopStyle.java`, clips `chop_left`, `chop_right` (`ReinhardRenderer` overlay keyed by the synced chop ticks), icon `abilityIcon("viltrumite:chop")` → `reinhard/lethal_chop.png`
- Modify: `core/hero/HeroDefinition.java` (`chopStyle`), `ReinhardHero` (`allowsLegacyAbility(p, id)` = `LegacyKit.CHOP.equals(id)`; `chassisStats` = `new HeroChassis(ReinhardRules.CHOP_BASE_DAMAGE, 0, 0)`; `chopStyle`), `core/client/ViltrumiteCoreClient.java` (chop key loop: `style.forceLeft()` overrides the random side), `core/network/packet/ChopC2SPacket.java` (server applies `forceLeft`), `core/mixin/PlayerEntityCoreMixin.java` (`tickChop` sweep sound volume/pitch from the style), `core/util/ChopImpactManager.java` (crit sound volume/pitch from the style), `core/client/render/vfx/ChopSweepVFXManager.java` (arc scale, trail colours, air ripple when `style.ripple()`), `ReinhardAbilities.LOADOUT` already has `viltrumite:chop`
- Test: `test/hero/ChopStyleTest.java`, `test/hero/reinhard/LethalChopTest.java`

**Interfaces:**
- `record ChopStyle(boolean forceLeft, float arcScale, int trailInner, int trailOuter, boolean ripple, float soundVolume, float soundPitch)`; `ChopStyle.DEFAULT` = exactly the current values read from the code (arc 1.0, today's trail colours, no ripple, today's volumes and pitch 0.8 for the crit) — Step 1 pins them.
- `default ChopStyle chopStyle(Player p) { return ChopStyle.DEFAULT; }` (both sides).
- Reinhard: `forceLeft` = Reid DRAWN (spec §9.2), `arcScale 2.0`, trail white `0xFFFFFF` → crimson `0xB0142A`, `ripple true`, volume ×1.4, pitch ×0.8 ("louder and lower").
- Damage: `ChopImpactManager` already computes `getBaseDamage() × 1.75` = 20 × 1.75 = 35 via `chassisStats`; bleed 4 s unchanged.
- Third person: model mixins do not run for the replaced body, so `ReinhardRenderer` overlays `chop_left/right` from `getChopTicks/isLeftChop/getChopType`; first person: `FirstPersonChopMixin` keeps working on the event stack.

- [ ] Step 1 tests: `defaultEqualsLegacyConstants`, `homelanderUsesDefault`, `reinhardStyleValues`, `serverForcesLeft` (packet isLeft false + style forceLeft → left), `LethalChopTest.damage35` (`CHOP_BASE_DAMAGE * 1.75 == 35`), `onlyChopFromKit` (`allowsLegacyAbility` false for PUNCH/DASH/…), `noViltrumiteChassis` (`allowsLegacyAbilities` false). Step 2 FAIL. Step 3 implement. Step 4 PASS; manual: Reinhard chop sheathed (random side) and drawn (left), big white-crimson arc with ripple, heavier sound, bleed; Homelander chop side by side — identical to the previous jar (§11.11), both views. Step 5 commit `feat(hero): chop style hook` and `feat(reinhard): lethal chop`.

### Task 7: Sheath Strike (Holy Sword slot 3)

**Files:**
- Create: `core/hero/reinhard/ability/SheathStrike.java`; clip `sheath_strike` (sword in the sheath swung in an arc); icon; lang
- Test: `test/hero/reinhard/SheathStrikeTest.java`

**Interfaces:**
- `ReinhardRules`: `SHEATH_RADIUS = 6`, `SHEATH_STUN = 60`, `SHEATH_DAMAGE = 20f`, `SHEATH_COOLDOWN = 160`, `SHEATH_HIT_TICK = 6`.
- Only `Reid.Phase.SHEATHED`. Hit tick: targets within r 6 (`ReinhardTargets.canHit`) → `hurt(20)`; if `allowsExternalControl(target, FREEZE)` → `ControlManager.tryAcquire(target, self, "reinhard:stun", FREEZE, expiresAt = now + 60)`; stun visual: white pixel stars over the head (client from the control sync); FX `HeroFx.slam` r 6; sound placeholder `block.anvil.land` pitch 1.6 until Stage 7 (Iridiuss ring).

- [ ] Step 1 tests: `onlyWhenSheathed`, `radius6Damage20Stun60`, `cooldown160`, `uncontrollableTakesDamageOnly`. Step 2 FAIL. Step 3 implement. Step 4 PASS; manual both views. Step 5 commit `feat(reinhard): sheath strike`.

### Task 8: Rain of Cuts (Holy Sword slot 4) + `CarveJobs`

**Files:**
- Create: `core/hero/CarveJobs.java`, `core/hero/CarveBudget.java`, `core/hero/reinhard/ability/CutRain.java`; clip `cut_rain` (fast fan of slashes); icon; lang
- Modify: `core/hero/HeroEvents.java` (`onLevelTick` END → `CarveJobs.tick(level)`), `HeroDestruction` (new `clearBlock(level, pos)`: no drops, flags `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS`, only if `canDestroy`)
- Test: `test/hero/CarveBudgetTest.java`, `test/hero/CarveJobsTest.java`, `test/hero/reinhard/CutRainTest.java`

**Interfaces:**
- `CarveJobs`: `static void submit(ServerLevel, Job job)`, `interface Job { boolean step(ServerLevel level, int budget); /* returns true when done */ int priority(); }`; per level a queue; each tick total budget = `CarveBudget.perTick(base, mspt)` split by priority (FIFO inside); a job whose level unloads is dropped.
- `CarveBudget.perTick(int base, double mspt)` pure: `mspt ≤ 40` → base; 40–60 → linear to `base / 8`; ≥ 60 → `max(50, base / 20)`. `mspt` = `server.getAverageTickTime()` (verify the 1.20.1 name; fallback: mean of `server.tickTimes` / 1e6).
- `ReinhardRules`: `CUT_COUNT = 12`, `CUT_CONE_DEG = 30`, `CUT_RANGE = 40`, `CUT_COOLDOWN = 300`, `CUT_GROOVE = 1` (width) × `3` (height), `CUT_BASE_BUDGET = 2000` (shared with Stage 5).
- `CutRain`: directions = 12 evenly spread over ±15° yaw with small pitch jitter (deterministic from the cast tick seed); each cut = a segment of 40 blocks from the chest; every living entity whose box the segment crosses → `Annihilation.strike`; a groove `1×3` along the segment is submitted as one carve job (Holy Sword = massive destruction); VFX: 12 `HeroFx.blade` white-gold trails, staggered 1 t; sound placeholder `entity.player.attack.sweep` ×3 pitches until Stage 7 (Robinhood76 whoosh).

- [ ] Step 1 tests: `CarveBudgetTest.fullUnder40`, `scalesDown`, `floorAt50`, `CarveJobsTest.respectsBudget`, `fifoWithinPriority`, `CutRainTest.twelveCutsInCone`, `segmentHitsBox`, `cooldown300`, `onlyWhenDrawn`. Step 2 FAIL. Step 3 implement. Step 4 PASS; manual both views, `mobGriefing false` → no grooves, bedrock untouched. Step 5 commit `feat(hero): carve jobs with adaptive budget` and `feat(reinhard): rain of cuts`.

### Task 9: Check, docs, PR

**Files:**
- Modify: `.agents/skills/animation-system/SKILL.md` (§8: `SkyTint`, `SoundDuck`; Reid clips), `.agents/skills/add_hero/references/hero-seam.md` (`chopStyle`), `shared-toolkit.md` (`Annihilation`, `CarveJobs`, `SkyTint`, `SoundDuck`), `.memory-bank/project.md`, `SESSION.md`, core version (minor +1)

- [ ] Step 1: full verification list; dedicated server start (new client mixins only in the client config).
- [ ] Step 2: in game, both views: draw event with three LAN players at 10/60/120 blocks; auto-return; mode exit; chop (Reinhard and Homelander); Sheath Strike; Rain of Cuts; Reid strike targets list of Task 1.
- [ ] Step 3: commit `docs(reinhard): stage 4 notes`; push `feat/reinhard-stage4`; PR «Рейнхард, этап 4: Святой меч — Рейд, Lethal Chop, удар ножнами, дождь разрезов».

Acceptance criteria closed: §11.8, §11.11, §11.7 (§7.2–§7.5 part), §11.6 (Holy Sword part).

## Risks and pitfalls

- `Level.getRainLevel` is a common class: the mixin lives in the client config only and returns early when `!isClientSide`.
- Sound ducking must not duck our own scene sounds; keep the exempt list in `SoundDuck`, not in Reinhard code.
- `noGravity` restore: never lose the original flag (use the `ControlManager.SavedFlags` idea); an entity that unloads mid-lift is restored on load through a tag `ViltrumiteLifted` checked in `EntityJoinLevelEvent`.
- Oculus shader packs draw their own sky: `SkyTint` may show only through fog colour; record it, do not add a shader.
- `ChopSweepVFXManager` and `GameRendererDashMixin` read chop state for every player; the style lookup must be cheap (no allocation per frame).
