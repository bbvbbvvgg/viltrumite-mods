# Thor — Stage 1 (foundation) and roadmap

Spec: `docs/design/2026-10-27-thor-design.md`. Branch `feat/thor-foundation` from `main` 1dccda7. Core 1.45.0 → 1.46.0, protocol unchanged.

## Stage 1 (done)
- Shared: `HeroId.THOR` (appended), `HeroAction.CONTEXT_POWER/CONTEXT_MOBILITY/ULTIMATE` (appended), `HeroDefinition.combatPresence` (default NONE), `hero/combat/` (StagedTimeline, StagedMove, MovePhase, InterruptReason, CombatTags, CombatPresence, CombatStance, InteractionRule(s), InteractionKind).
- `hero/thor/`: ThorHero, ThorRules, ThorState, ThorMove (18 moves, wire id = ordinal, append only), ThorInput, ThorContext, ThorControls, ThorActions (pure `start`/`advance`), ThorAbilities, ThorFlags, ThorFeedback, ThorCombat, ThorHands, HammerState, MjolnirPlace, ThorMode.
- `client/thor/`: ThorClient, ThorParts, ThorPoser, ThorClips, ThorView; `client/mixin/ThorModelMixin`.
- Assets: `tools/assets/convert_thor.py`, `tools/assets/make_thor_icons.py`.

## Bridge (pre-flight for sessions A/B/C)
Approved layout replaces the old contexts (see design §4). `ThorRules.SHIPPED_STAGE` and `ThorMove.stage()` are gone: a move can start only once registered.

Contract mapping (`thor_foundation_api_contract_v4_1.md` §3), package `dev.baranhan.viltrumitecore.hero.thor.bridge` unless noted:

| Contract channel | FQCN / signature | Owner |
|---|---|---|
| Integration bridge | `ThorBridge` (static facade, server main thread) | foundation |
| Per-move gate / authoring | `ThorMoves.register(String owner, ThorMove move, ThorMoveDefinition def, ThorMoveHandler handler)`; `authored/definition/handler/owner`; retired ids throw | A/B/C, own moves only |
| Timing | `ThorMoveDefinition(int intro, int main, int finish, @Nullable MovePhase cancellableFrom, int maxSteps)` | |
| Behaviour + edge release | `ThorMoveHandler.onStart(ThorMoveContext)`, `onPhase(ThorMoveContext, StagedTimeline.Event)`, `onRelease(ThorMoveContext, int heldTicks)`, `onStop(ThorMoveContext)`; `ThorMoveContext(ServerPlayer player, ThorState state, ThorMove move, @Nullable LivingEntity target)` | |
| MMB dispatch | `hero.thor.ThorControls.hammer(ThorContext)`: GRAB > RECALL > HAMMER_STRIKE (enemy ≤ 4) > HAMMER_THROW; release via `HeldInputs` → `ThorHero.handleInput(..., false)` → `ThorActions.release` | A |
| onMmbMeleeRequested | handler of `ThorMove.HAMMER_STRIKE` (`onStart`, `context.target()` = nearest enemy) | B |
| HammerAccess / HammerView | `HammerAccess.view(UUID thor, @Nullable ThorState)`; `ThorBridge.installHammerAccess(String owner, HammerAccess)` (once), `ThorBridge.hammer(UUID, ThorState)` / `hammer(Player)` → `HammerView(@Nullable UUID logicalId, @Nullable UUID activeOwnerId, HammerState phase, boolean inHand, @Nullable ResourceKey<Level> dimension, @Nullable Vec3 position)`; default `HammerAccess.FOUNDATION` (no world hammer) | A writes, B/C read |
| Thor state view | `ThorBridge.state(ThorState)` / `state(Player)` → `ThorStateView(mode, move, phase, moveElapsed, grabWindowTicks, sceneLockOwner, cameraLeaseOwner)` | |
| grabWindowActive | `ThorBridge.openGrabWindow(ThorState, int ticks)`, `closeGrabWindow`, `grabWindowActive`; synced `ThorFlags.GRAB_WINDOW` (bit 3) | C |
| Scene lock | `ThorBridge.lockScene(ThorState, String owner)`, `unlockScene`, `sceneLocked` (all Thor presses refused while held) | C |
| Camera lease | `ThorBridge.acquireCamera(ThorState, String owner)`, `releaseCamera`, `cameraLeased`; synced `ThorFlags.CAMERA_LEASE` (bit 4); dropped by `ThorActions.stop` (logout, death, dimension, hero change) | C |
| Events | `ThorEvents.subscribe(String owner, ThorEventType, Listener)`, `post(ThorEvent)` (false = duplicate `eventId`, window 512), `nextId()`; `ThorEvent(long eventId, ThorEventType type, UUID attackerId, @Nullable UUID targetId, @Nullable ThorMove move, int attackId, boolean hammerInHand, float amount, boolean success)`; types `MELEE_HIT`, `LETHAL_HIT` (post before the death is final), `COMBO_END`, `THROW`, `RECALL`, `CATCH`, `HAMMER_STATE_CHANGED` | B: hits/combo; A: hammer |
| Dev registry | `hero.thor.dev.ThorDevCommands.registerChild(String owner, String path, UnaryOperator<LiteralArgumentBuilder<CommandSourceStack>> leaf)`; one `/thor` root, dev env + op 2 only; foundation fixtures `/thor foundation state|hammer|mode|grab_window|release_locks` | A/B/C |

Appended ids: `HeroAction.CONTEXT_TELEPORT`; `ThorMove.HAMMER_STRIKE`, `HAMMER_PULL`, `TELEPORT_SELECT`; `ThorInput.TELEPORT`; panel slot `thor:teleport` (slot 4, V).

Not ready / risks: nothing produces THROWN, ELECTRIC, grab window or events yet; no global SavedData hammer and no "one active Thor" rule (A); `ThorState.load/respawnCopy/enter` still reset the hammer and `ThorActions.settle` still writes `ThorState.hammer` (A must take over); no hit/lethal hook exists in the damage path yet (B posts from `HeroDefinition.onHurt/clampFinalDamage`); no player path puts Mjölnir in the hand (DRAW/HOLSTER have no key; dev `/thor hammer in_hand`); RMB is not routed; double-tap WASD dash and the automatic sky lightning are outside the slot routing; ultimate key not approved (Z default); Bridge aligned to `thor_foundation_api_contract_v4_1.md`.

## Where to start next
1. Author a move: `ThorMoves.register(owner, move, definition, handler)` from the session's own class; effects live in the handler; register its clip in `ThorClips` (test checks clip length == server length).
2. Combat wiring: publish stance (ACTIVE in MAIN of strikes) in `ThorCombat`, resolve hits through `InteractionRules`, post `ThorEvents` (HIT / LETHAL / COMBO_END).

## Roadmap
Superseded: the work is split into sessions A/B/C by their own specs, starting from the bridge SHA. Only mechanics traceable to approved decisions №1–220 are built; retired ids stay unregistered. Kratos comes after Thor on the same `hero/combat/` (tag rules only).
