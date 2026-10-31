# Contextual interactions (core 1.63.0, protocol 20 — no packet change)

Branch `feat/contextual-interactions-20261031`. Catalog: `docs/contextual-interactions.json` (must equal
`ContextualInteractions.builtins()`, checked by `ContextualInteractionCatalogTest`; ids, order, trigger, priority,
cooldown/aftermath, every bilingual field, `code_refs` = `path#method` that must exist).

## Architecture (`viltrumitecore/.../interaction/`)
- `ContextualInteraction` (spec / matches / apply / onRegister) + `InteractionSpec(id, trigger, priority, cooldownTicks, aftermathTicks)`.
  Registry `ContextualInteractions`: `bootstrap()` from common setup (after ThorScenes), sorted by priority (stable), first
  matching interaction that *fires* wins. Errors are caught and logged per interaction; trace with
  `-Dviltrumitecore.interactions.trace=true`.
- Trigger points (all server): `HeroEvents.onLivingAttack` → `onAttack` (HIT, after notifyAttack, before the hero
  damage layers; cancel = event cancelled), `onLivingHurtLayers` → `onHurt` (HURT, scale/cancel the amount),
  `onProjectileImpact` → `onProjectile` (PROJECTILE, SKIP_ENTITY), Thor `MeleeMoves.landStrike` → `onFriendlyContact`
  (spared allies touched by a strike: cooperative combos with PvP off). `onServerStopped` → `reset()`.
- `InteractionGuard`: reentrancy depth (an interaction's own damage never triggers another), cooldown per (id, pair),
  aftermath per pair (swallows the rest of a stroke that already collided), free throttles.
- Hero seam, no hero ids in shared code (NoHeroBranchTest): `HeroDefinition.combatPresence` (tags), `guardFaces(self, from)`,
  `react(self, InteractionReaction, ctx)`. A defender that returns false = the interaction falls through to the normal mechanic.
- Tags added: `weapon.shield`, `trait.annihilating`, `armor`, `armor.powered`. Presences: Captain `CaptainHero.SHIELD_GUARD`
  (raised intact shield, GUARD), Reinhard `REID_DRAWN`/`REID_STROKE` (ACTIVE during REID_STRIKE), Iron Man
  `POWERED_ARMOR` (flight suit worn, not the Hulkbuster). Presences are server-side (client NONE except Thor/Hulkbuster).
- FX: `InteractionFx` presets net ids 600-649 (shared FxPreset packet), recipes `client/interaction/InteractionFxRecipes`.
  Sounds `InteractionSounds` (`interaction.*`), overlay `sound-overlays/interactions.json`, subtitles + Captain lines in
  `lang-overlays/interactions/` (12 locales). Sound mixes `tools/sfx/interactions.py`, ledger `tools/assets/interaction_sources.md`.

## Built-ins (21, priority order = `ContextualInteractions.builtins()`; matrix `docs/contextual-interactions-matrix.md`)
Stage 1: reid_shatters_vibranium (A), mjolnir_meets_vibranium (B), divine_weapon_clash, vibranium_stops_meteor,
mjolnir_shatters_meteor, thunder_overcharges_armor, legion_vs_chitauri, titan_scatters_summons.
Stage 2: reid_strains_lions_heart (Regulus `LION_STRAINED` +420 strain), mjolnir_rebounds_off_shield (`GUARD_DEFLECTED`),
viltrumite_fist_meets_vibranium, repulsor_splits_on_vibranium, time_stone_rewinds_ledger (`DioTimeStop.paying()`),
reid_cleaves_meteor, viltrumite_hurls_meteor (`METEOR_CAUGHT`), shadow_exchange_meteor, titan_breaks_guard
(`GUARD_CRUSHED`, line `CaptainLines.ALL_DAY`), meteor_downs_leviathan, thunder_charges_legion,
lightning_runs_the_leviathan, heavy_hitter_halts_leviathan (`ChitauriLeviathan.staggerBy`). Helpers `Leviathans`,
`MeteorBursts.burst(@Nullable stopper)`. Presence tags: `CombatTags.BODY_VILTRUMITE` (`ViltrumiteHero.VILTRUMITE_BODY`).
FX net ids 600-621, 21 `interaction.*` sounds. Tests: `StageTwoInteractionRulesTest` (priorities, conflicts, ally guard).
Known gaps: hurled meteor's vanilla explosion is not ally-filtered; allied lightning is filtered so
thunder_charges_legion mostly fires in PvP; Homelander laser has no interaction yet.

## Captain side
`CaptainState.shieldBroken` (ticks, not persisted), `CaptainFlags.SHIELD_BROKEN = 10`, move `CaptainMove.SHIELD_SHATTERED`
(append only, 16 t, roots), clip `shield_shattered` in TP skills + FP files, `CaptainCombat.shatterGuard`, guard refused
while broken (REFUSE sound), GUARD_UP cue when it heals. Combo B reuses `CaptainCombat.teamBrace`.

## Adding an interaction
1. Class in `interaction/builtin` (or any mod: `ContextualInteractions.register`), snake_case id, priority, timings.
2. Prefer tag rules (`InteractionRule` + `Duels.directed`) over entity classes; never `HeroId`.
3. Hero-specific state goes through `react`. 4. Catalog entry in `docs/contextual-interactions.json` (test fails otherwise).

## Not verified
No in-game client / multiplayer playtest was run (no display in the agent sandbox): only unit tests, full gradle build
and a dedicated-server boot smoke (`runServer`), which proves registration/loading, not gameplay. Player-free runtime
check: with `JAVA_TOOL_OPTIONS=-Dviltrumitecore.interactions.trace=true`, RCON `summon viltrumitecore:meteor` above a
NoAI `chitauri_leviathan` logs `INTERACTION meteor_downs_leviathan fired` (HP 1024 -> 917.4). Hero combos need players.
