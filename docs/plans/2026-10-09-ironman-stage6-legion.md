# Iron Man — Stage 6 (Iron Legion) Implementation Plan

> Executed by a single agent, task by task, with a git checkpoint (commit + push to `feat/iron-legion-stage6`)
> about every five minutes of work. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Page 2 slot 6 (index 11) "Железный легион" calls up to 3 autonomous marks from the Veronica roster.
They arrive from the sky on separate trajectories, take positions, play a per-mark ready animation and guard Tony.
A server squad coordinator and per-suit brains run threat analysis, roles and orders
(FOLLOW / GUARD / ATTACK / HOLD / RETURN). Durability lives in the roster.

**Design:** `docs/design/2026-10-31-ironman-legion-design.md` (replaces the old Task 0 gate).

**Architecture:**
- Ownership: `MarkLocation.LEGION` (append-only), all transitions via `MarkRoster.move`, `recallWorld` covers LEGION.
- Actor seam: `hero/ironman/legion/SuitActor` (entity, eye, look, hands, damage source, `canHit`, commander) with
  `SuitActor.of(ServerPlayer)`. Signature math that the Legion reuses moves into small shared kernels that take an
  actor; player signatures call the same kernels. No fake players.
- Projectiles: `RepulsorBlastEntity` / `MicroMissileEntity` accept a `LivingEntity` owner; ally filtering through one
  `LegionAllies` rule (owner, same-owner suits, protected allies, PvP rule).
- Entity: `entity/LegionSuitEntity` (PathfinderMob, no gravity, manual steering, not saved, synced mark/owner/phase/
  order/action/aim/durability). Brain: `legion/LegionSuitBrain`; coordinator: `legion/LegionSquad` (per owner,
  staggered). Pure logic: `LegionRules`, `LegionOrder`, `LegionRole`, `LegionProfile`, `LegionCall`, `ThreatScore`,
  `LegionTactics`, `LegionSteering`.
- Client: `client/ironman/legion/` screen, HUD, client store, renderer + poser on the existing `client/anim` core,
  VFX through existing helpers, engine loop sound instance, JARVIS lines in `JarvisVoice`.

**Tech Stack:** Forge 47.3.0, Minecraft 1.20.1, Java 17, Mixin 0.8.5, JUnit 5.9.3.

## Global Constraints

- All Stage 1–5 Global Constraints apply.
- A mark exists in the world once: WORN, EMPTY, IN_DELIVERY or LEGION.
- Server cost: max 3 suits per owner; suit brain every 2 t (slot-staggered); coordinator every 10 t (owner-staggered);
  obstacle feelers are short block raycasts; no ground pathfinding.
- Legion never hurts the owner, the owner's suits, protected allies; players only under the server PvP rule;
  villagers/neutral mobs only on an explicit ATTACK order.
- Lifecycle: owner death, logout, hero change, dimension change, server stop → suits gone, durability in roster.
- No hero branch: Legion code only in `hero/ironman/`, `client/ironman/`, `entity/`.

## Review Focus

1. No mark duplication: `legionUsesOwnershipTransitions`, `recallWorldReturnsLegion`.
2. Durability preserved across recall and break: `durabilityStoredOnRecall`, `breakStartsMarkCooldown`.
3. AI never targets owner/allies: `neverTargetsOwnerOrAllies`, `neutralOnlyOnAttackOrder`, `respectsPvpRule`.
4. Cleanup paths: `LegionLifecycleTest`.
5. Server cost: staggering verified by `LegionScheduleTest`.

---

### Task 1: Ownership and call rules
- [ ] `MarkLocation.LEGION`, `MarkRoster.legionCallable`, `recallWorld` incl. LEGION, `VeronicaScreen` status.
- [ ] `LegionRules`, `LegionCall.validate` (≤3, distinct, callable). Tests `LegionCallTest`, roster tests.

### Task 2: Pure AI and steering
- [ ] `LegionOrder`, `LegionRole`, `LegionProfile`, `ThreatScore`, `LegionTactics`, `LegionSteering`.
- [ ] Tests `LegionThreatTest`, `LegionTacticsTest`, `LegionSteeringTest`.

### Task 3: Actor seam and weapons
- [ ] `SuitActor`, actor-based `SignatureTrace` overloads, shared kernels used by player signatures.
- [ ] `RepulsorBlastEntity` / `MicroMissileEntity` with a `LivingEntity` owner; `LegionAllies` filter.

### Task 4: Entity, squad, brain, ability
- [ ] `LegionSuitEntity`, `LegionSquad`, `LegionSuitBrain`, `IronManLegion`; ability `ironman:legion` (slot 11,
  `HeroAction.LEGION`, icon), packets (`LegionMenuS2C`, `LegionCallC2S`, `LegionOrderC2S`, `LegionStatusS2C`).
- [ ] Lifecycle hooks in `IronManHero` / `IronManState`; damage rule; tests `LegionLifecycleTest`.

### Task 5: Client
- [ ] `LegionScreen`, `LegionHud`, `ClientLegion`, `LegionSuitRenderer`, `LegionPoser`, `legion_suit.animation.json`,
  VFX (trails, muzzle, sparks, beams), engine loop sound, JARVIS lines.

### Task 6: Sounds, lang, ship
- [ ] Synthesized SFX (`tools/sfx/ironman_legion.sh`), sounds.json, subtitles, lang in all 12 locales.
- [ ] `./gradlew build`, in-game check (runClient) where the sandbox allows, polish pass.
- [ ] Version bump (core minor), memory bank, PR «Железный человек — этап 6: Железный легион», body starts with «Для игрока».

---

## Review log

Plan reviewed against spec §6.2, §12.6, §19, §20 and Stage 4 roster interfaces (reviewer pass, 2026-10-09). Fixed:
1. Spec explicitly defers Legion design → design doc written (2026-10-31), Task 0 removed.
2. Mark duplication across worn / empty / Legion → one-instance rule + test.
3. Server cost of AI suits was unbounded → caps, tick rates, staggering.
4. `ViltrumiteAction` / `MarkSignature` need player classes → `SuitActor` seam + shared kernels (no fake players).
5. `markInWorldOnce` lives in Stage 4; Legion appends `LEGION` to `MarkLocation`.
