# Memory bank — Regulus Reborn (core 1.55.0, protocol 15, PR #89)

## Shape
- Hero id stays `regulus` (`HeroId.REGULUS` = 2). The whole kit lives in `hero/regulus/` (server, pure rules in
  `RegulusRules`, wire layout in `RegulusFlags`) and `client/regulus/` (view, poser, renderer, VFX, HUD, ult scene).
  Design: `docs/design/2026-10-30-regulus-reborn-design.md` (section numbers are quoted in code comments).
- Slots (cooldown index = `RegulusFlags.COOLDOWN_*` = `RegulusAbilities.OWN` order): lions_heart, stopped_sand,
  air_severance, procession, right_of_reply, kingdom_of_one. LMB = Greed's Touch combo, MMB (heart key) = Little King.
  Actions reuse the old `HeroAction` ordinals (DEBRIS_KICK → sand, MANIA → severance, GREEDS_EMBRACE → procession,
  COUNTER → reply, ULTIMATE → ult): no HeroAction was added or removed.
- `RegulusMove` ordinal = snapshot `actionId` (append only); its length is the clip length. `RegulusAssetsTest` pins
  every body clip and `fp_` arms clip to it. Clips and rig are generated: `tools/assets/make_regulus_clips.py`,
  `make_regulus_model.py`, `regulus_rig.py` (`--check` on each).
- One S2C packet `hero/regulus/net/RegulusFxS2CPacket` (kind + entity ids + vec) carries every VFX/camera cue;
  `client/regulus/RegulusFx` dispatches. Remote views read only the snapshot (flags/extras in `RegulusFlags`).

## Invariants
- Lion's Heart = `isAbsolutelyInvulnerable` (divine still pierces, see project.md). Strain only grows while the
  Lion runs without a living Little King host; at `STRAIN_MAX` the seizure locks the Lion. `RegulusRules` is pure and
  unit-tested; keep balance numbers there.
- Every block break goes through `RegulusCombat.breakBlock` (hardness limit, budget, adventure mode, `BlockEvent.BreakEvent` for claims,
  `mobGriefing`, spawn protection via `mayInteract`, no block entities, no fluids). Hits are validated on the server (reach, line of
  sight, allies and creative players are skipped) — the client never reports a hit.
- DIO's The World: frozen hosts are invalid (`LittleKing.hostValid`), grains and blades hold still inside a stop
  (`RegulusCombat.timeStoppedAt`), the ult target hold yields to `WorldPause.blocksMovement`
  (`WorldPauseMovementGuardTest` reads `KingdomOfOne.java`).
- Ult cameras: `RegulusUltScene` drives a client-only `Marker` camera for the caster and the target; it is released
  on scene end, death, level change and when the snapshot stops showing the ult (5 ticks). Cut blends are
  `ULT_BLEND` ticks, the only hard cut is `ULT_FLICK_CONTACT`; `RegulusClientMathTest` checks continuity.
- Persistence: NBT tag `RegulusReborn` (cooldowns, strain, lion lock). The first kit's `RegulusCooldowns` tag is
  migrated once (`RegulusState.migrateLegacy`) and removed on the next save; ability ids in saved loadouts are mapped
  by `RegulusAbilities.migrate` (`regulus:punch` is dropped). Death keeps only the cooldowns.

## Assets
- Sounds: own synthesis `tools/sfx/regulus_reborn.py` (CC0, deterministic, `--check`) → `binassets/.../sounds/regulus/reborn/`.
  Events live in `src/main/sound-overlays/regulus.json`, merged into sounds.json by processResources (an overlay may
  not redefine a base event). The old `regulus_*` events stay because Reinhard reuses some of them.
- Strings: `tools/lang/regulus_reborn_lang.py` → `src/main/lang-overlays/regulus/<12 locales>.json`; base lang files
  still hold the unused first-kit keys (too large to edit through the GitHub API).
- Icons: `regulus_icon_art.py` (8 names); old first-kit icon b64s are unused.
