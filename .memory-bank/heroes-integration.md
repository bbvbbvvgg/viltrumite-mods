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
  1.53.0 / 13 (#82 alone), 1.54.0 / 14 (both). Pick the next free pair for the next merge.

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
