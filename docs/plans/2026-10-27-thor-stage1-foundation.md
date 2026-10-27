# Thor — Stage 1 (foundation) and roadmap

Spec: `docs/design/2026-10-27-thor-design.md`. Branch `feat/thor-foundation` from `main` 1dccda7. Core 1.45.0 → 1.46.0, protocol unchanged.

## Stage 1 (done)
- Shared: `HeroId.THOR` (appended), `HeroAction.CONTEXT_POWER/CONTEXT_MOBILITY/ULTIMATE` (appended), `HeroDefinition.combatPresence` (default NONE), `hero/combat/` (StagedTimeline, StagedMove, MovePhase, InterruptReason, CombatTags, CombatPresence, CombatStance, InteractionRule(s), InteractionKind).
- `hero/thor/`: ThorHero, ThorRules, ThorState, ThorMove (18 moves, wire id = ordinal, append only), ThorInput, ThorContext, ThorControls, ThorActions (pure `start`/`advance`), ThorAbilities, ThorFlags, ThorFeedback, ThorCombat, ThorHands, HammerState, MjolnirPlace, ThorMode.
- `client/thor/`: ThorClient, ThorParts, ThorPoser, ThorClips, ThorView; `client/mixin/ThorModelMixin`.
- Assets: `tools/assets/convert_thor.py`, `tools/assets/make_thor_icons.py`.

## Bridge (pre-flight for sessions A/B/C)
Approved layout replaces the old contexts (see design §4). `ThorRules.SHIPPED_STAGE` and `ThorMove.stage()` are gone: a move can start only once registered.

| Need | FQCN / signature |
|---|---|
| Author a move | `dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves.register(String owner, ThorMove move, ThorMoveDefinition def, ThorMoveHandler handler)` |
| Timing | `...bridge.ThorMoveDefinition(int intro, int main, int finish, @Nullable MovePhase cancellableFrom, int maxSteps)` |
| Behaviour | `...bridge.ThorMoveHandler`: `onStart(ThorMoveContext)`, `onPhase(ThorMoveContext, StagedTimeline.Event)`, `onRelease(ThorMoveContext, int heldTicks)`, `onStop(ThorMoveContext)` |
| MMB melee callback | handler of `ThorMove.HAMMER_STRIKE`; `ThorMoveContext.target()` = nearest enemy within 4 blocks |
| Hammer snapshot | `...bridge.ThorBridge.view(ThorState)` / `view(Player)` → `HammerView(hammer, mode, move, phase, moveElapsed, grabWindowTicks, sceneLockOwner)` |
| Grab window | `ThorBridge.openGrabWindow(ThorState, int ticks)`, `closeGrabWindow`, `grabWindowOpen`; synced as `ThorFlags.GRAB_WINDOW` (bit 3) |
| Scene lock | `ThorBridge.lockScene(ThorState, String owner)`, `unlockScene`, `sceneLocked` |
| Events | `...bridge.ThorEvents.subscribe(String owner, ThorEventType, Listener)`, `post(ThorEvent)` (false = duplicate id), `nextId()`; `ThorEvent(long id, ThorEventType type, UUID source, @Nullable UUID target, @Nullable ThorMove move, float amount)`; types HIT, LETHAL, COMBO_END, THROW, RECALL, CATCH |
| Dev fixtures | `...hero.thor.dev.ThorDevCommands`: `/thor state`, `hammer <holstered|in_hand|thrown|returning>`, `mode <normal|electric>`, `grab_window <ticks>`, `unlock_scene`; dev env + op 2 only |

Appended ids: `HeroAction.CONTEXT_TELEPORT`; `ThorMove.HAMMER_STRIKE`, `HAMMER_PULL`, `TELEPORT_SELECT`; `ThorInput.TELEPORT`; panel slot `thor:teleport` (slot 4, V).

Not ready / risks: nothing produces THROWN, ELECTRIC, grab window or events yet; no player path puts Mjölnir in the hand (DRAW/HOLSTER have no key; dev `/thor hammer in_hand`); RMB is not routed; double-tap WASD dash and the automatic sky lightning are outside the slot routing; ultimate key not approved (Z default); `thor_foundation_api_contract_v4_1.md` was not available when the bridge was written — signatures are this repo's, align the A/B/C specs to them.

## Where to start next
1. Author a move: `ThorMoves.register(owner, move, definition, handler)` from the session's own class; effects live in the handler; register its clip in `ThorClips` (test checks clip length == server length).
2. Combat wiring: publish stance (ACTIVE in MAIN of strikes) in `ThorCombat`, resolve hits through `InteractionRules`, post `ThorEvents` (HIT / LETHAL / COMBO_END).

## Roadmap
Superseded: the work is split into sessions A/B/C by their own specs, starting from the bridge SHA. Only mechanics traceable to approved decisions №1–220 are built; retired ids stay unregistered. Kratos comes after Thor on the same `hero/combat/` (tag rules only).
