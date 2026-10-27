# Thor — Stage 1 (foundation) and roadmap

Spec: `docs/design/2026-10-27-thor-design.md`. Branch `feat/thor-foundation` from `main` 1dccda7. Core 1.45.0 → 1.46.0, protocol unchanged.

## Stage 1 (done)
- Shared: `HeroId.THOR` (appended), `HeroAction.CONTEXT_POWER/CONTEXT_MOBILITY/ULTIMATE` (appended), `HeroDefinition.combatPresence` (default NONE), `hero/combat/` (StagedTimeline, StagedMove, MovePhase, InterruptReason, CombatTags, CombatPresence, CombatStance, InteractionRule(s), InteractionKind).
- `hero/thor/`: ThorHero, ThorRules, ThorState, ThorMove (18 moves, wire id = ordinal, append only), ThorInput, ThorContext, ThorControls, ThorActions (pure `start`/`advance`), ThorAbilities, ThorFlags, ThorFeedback, ThorCombat, ThorHands, HammerState, MjolnirPlace, ThorMode.
- `client/thor/`: ThorClient, ThorParts, ThorPoser, ThorClips, ThorView; `client/mixin/ThorModelMixin`.
- Assets: `tools/assets/convert_thor.py`, `tools/assets/make_thor_icons.py`.

## Where to start next
1. Author a move: give it phase ticks in `ThorMove`, set `ThorRules.SHIPPED_STAGE`, add effects in `ThorActions.apply` (ENTER_MAIN/ENTER_FINISH), register its clip in `ThorClips` (test checks clip length == server length).
2. Combat wiring: publish stance (ACTIVE in MAIN of strikes, GUARD while guarding) in `ThorCombat`, resolve hits through `InteractionRules`.

## Roadmap
| Stage | Content |
|---|---|
| 2 | Hammer/fist combos (LMB), guard (RMB), interaction rules wired to hits; LMB/RMB claimed |
| 3 | Throw / recall (hammer entity, THROWN/RETURNING), electric mode, lightning call, electric combo |
| 4 | Flight, air strike, lightning dash, teleport strike, dive slam |
| 5 | Grabs (paired scenes), grab slam |
| 6 | Fatalities |
| 7 | Wrath (ultimate), sounds |
| 8 | Kratos and Thor × Kratos interactions (tag rules only) |
