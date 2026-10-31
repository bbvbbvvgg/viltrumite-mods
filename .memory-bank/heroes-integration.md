# Memory bank — all-heroes integration (Thor #74 + DIO/Sinclair/Jinwoo #82)

## Order and versions (integration/all-heroes-final, core 1.54.0, protocol 14)
- `HeroId`: HUMAN 0, VILTRUMITE 1, REGULUS 2, HOMELANDER 3, IRON_MAN 4, REINHARD 5, THOR 6, DIO 7, SINCLAIR 8, JINWOO 9. Saves store the hero key
  (`HeroSession` "Id"), never the ordinal; the ordinal travels only at runtime (entity data, packets), so a new
  order needs a new `CoreMessages.PROTOCOL_VERSION` (old clients must not join).
- `HeroAction`: … DIAGONAL_WORLD_CUT, then Thor (CONTEXT_POWER, CONTEXT_MOBILITY, ULTIMATE, CONTEXT_TELEPORT),
  then the DIO, Sinclair and Jinwoo blocks. A new hero appends after the last block.
- 65 actions > 64 bits: `HeroPublicSnapshot.availableActions` covers ordinals 0-63; an action at 64+ counts as
  available only under the "all" mask (-1L, what every hero sends); `actionMask` throws for it. Widen the mask
  (two longs) before a hero needs a per-action bit there.
- Packets: Thor/Hulkbuster (`ThorScene*`, `HulkbusterMotion*`, `ThorDashS2C`) before `HeroLineS2C`, then DIO
  and Jinwoo packets (`HeroRosterIntegrationTest` pins order and uniqueness).
- Version numbers taken: 1.46-1.49 / protocol 8-10 (Thor stages), 1.50.0 / 11 (subtitles), 1.52.1 / 12 (#74 alone),
  1.53.0 / 13 (#82 alone), 1.54.0 / 14 (both), 1.55.0 / 14 (DIO polish, no packet change). Pick the next free pair for the next merge.
  1.55.0 / 15 was claimed in parallel by #86 (Thor GUARD bit), #88 (wish packets) and #89 (`RegulusFxS2CPacket`).
  **1.56.0 / 16 = final integration of #85-#92** (all three tables together).
  **1.57.0 / 17 = Captain America 2012** (`feat/captain-america-2012`, topic file `.memory-bank/captain.md`); 1.57.1 / 17 = its polish (no packet change). 1.58.0 / 16 went to the Viltrumites/meteor integration (#97, main without Captain).
  **1.59.0 / 17 = Captain on top of 1.58.0** (`integ/captain-america-main`); **1.60.0 / 18 = Captain/Avengers finishing
  rework** (new `CaptainFx.TEAM` joint-move FX type, so the protocol moves). Pick the next free pair (1.61.0 / 19).

## Where strings and sounds live
- English and the 10 other base locales: `resources/assets/viltrumitecore/lang/<locale>.json` (Thor keys at
  the end, #82 keys in the middle: both branches appended at the end, so the #82 block was moved before the
  merge to keep the GitHub merge conflict-free; order inside a lang file means nothing).
- Russian: `resources/.../lang/ru_ru.json` (#65 base) + overlays `src/main/lang-overlays/{dio,jinwoo,thor}/ru_ru.json`.
  Thor's Russian strings exist only in `thor/ru_ru.json`. Tests read shipped strings through
  `integration/ShippedLang` (resources + overlays, as processResources merges them).
- `sounds.json`: one file; the #82 events (dio.*, sinclair.*, jinwoo.*) sit after `ironman_pulse_unibeam`,
  Thor/Hulkbuster events at the end.
- Report with the full merge account: `docs/reports/2026-10-29-all-heroes-integration.md`.

## Sinclair polish (branch fix/sinclair-polish-20261030, on top of PR #88)
- First person: `SinclairFirstPerson.onRenderArm` (RenderArmEvent) replaces every bare vanilla arm with the camera
  arms model's arm (no wand/card/hat) — no human arm while an item is held (alwaysRenderOffhand is on by default).
- Savannah: two spectral threads to Sinclair (`client/sinclair/SpectralTether`, drawn in `SinclairVfx.tether`).
- Lines: fixed durations (`SinclairLines.ULT_TICKS` 80 / `ABILITY_TICKS` 70); `SinclairLines.say/ability/tick`
  keep one Sinclair line from cutting another (ability lines skipped while busy, bow / window_lost wait).
- Repertoire: thor → NOVA (STORM), dio → BARRAGE (WORLD), jinwoo → CUT (SHADOW). viltrumite stays fallback.
- Rabbit Hex: 80 ticks for players and mobs.
- Copied-act card: `client/sinclair/ActCardPopup` (client tick + RenderGuiEvent.Post) — big readable card at the
  top of the screen when the capture card reaches the palm (act name in act colour, whose ultimate, `<act>.desc`,
  window bar). 6 t in / 120 t hold / 16 t out; performing closes it early. Lang: 86 Sinclair keys.

## Stopped time across heroes (DIO polish)
- `hero/control/TimeFreeze` is the seam every hero shares: a frozen entity's tick is skipped (server level, player
  tick, client level) so other heroes' entities, summons and projectiles stop without hero-specific code. A hero
  with level-wide timers (scene managers ticking from ServerTickEvent) must check `TimeFreeze.isFrozen` itself.

- Regulus Reborn (1.55.0 / 15): topic file `.memory-bank/regulus.md`; its strings and sounds live in the overlays
  `src/main/lang-overlays/regulus/` and `src/main/sound-overlays/regulus.json`.

## Final integration of #85-#92 (PR #85, core 1.56.0, protocol 16)
- Packets after the Jinwoo packets: `WishChoiceC2S`, `WishSceneS2C` (#88), then `RegulusFxS2C` (#89); Thor's GUARD
  state (#86) travels in the Thor snapshot. `HeroRosterIntegrationTest` pins the order, uniqueness and "16".
- Regulus Reborn replaced the first kit, so #87's Evangelium fix lives in Kingdom of One: a refused ult names its
  recharge (`message.viltrumitecore.regulus.ult_refused.cooldown`), an aborted scene recharges (cooldown ticks only
  with no scene) and opens again; the Evangelium item is an inert keepsake for old saves; the first-kit
  `RegulusCooldowns` tag is migrated once (`RegulusState`).
- `/hero cooldown reset` (#87) reaches the own timers of every hero: `resetCooldowns` overrides in Regulus,
  Homelander, Iron Man, Reinhard, Jinwoo, Thor v3 (`ThorCombatState.clearCooldowns`), DIO and Sinclair
  (`HeroResetCoverageTest`).
- Sinclair #91 + #92: `ActCardPopup` waits for the copy cinematic (no popup during CAPTURE / COPY, it opens with
  the window and runs its full 6 s hold); `SinclairFirstPerson` keeps #92's arm replacement and #91's scene camera.
- Report: `docs/reports/2026-10-30-all-pr-integration.md`.
