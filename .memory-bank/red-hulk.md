# Memory bank — Red Hulk (Thaddeus Ross) — branch feat/red-hulk

- Design: `docs/design/2026-11-04-red-hulk-design.md`. Core **1.65.0**, protocol **23**.
- Order: `HeroId.RED_HULK` = 11 (after CAPTAIN, last); `HeroAction` REDHULK_CLAP..REDHULK_OVERHEAT (6, contiguous,
  after FINAL_PUNCH); FX preset net ids 700–712 (`RedHulkFx.PRESETS`). `HeroRosterIntegrationTest` pins them.
- Server: `hero/redhulk/*` — `RedHulkRules` (all numbers, pure), `RedHulkMove` (timeline, lengths = clip lengths),
  `RedHulkState` (heat, cooldowns NBT), `RedHulkCombat` (LMB 5-hit chain, RMB grab/hold/throw), `RedHulkSkills`
  (six abilities, overheat vent), `RedHulkHero` (heat gain/cooling, aura, fall, snapshot flags `RedHulkFlags`).
- Client: `client/redhulk/*` — renderer (body ×2.52, eyes + heat emissive passes via RenderType.eyes, FP arms),
  `RedHulkPose` (stances, face blend `faceFor`, overlays), `RedHulkPanelStyle` (heat gauge), `RedHulkVfx` (presets,
  state particles, footsteps, heat hum loop, beam), `RedHulkFxRecipes`, portrait, head preview. Entry `RedHulkClient.init()`.
- Assets are generated, never hand-edited: `tools/assets/red_hulk_model.py`, `red_hulk_clips.py`, `red_hulk_icons.py`,
  `red_hulk_lang.py`, `red_hulk_preview.py`; sounds `tools/sfx/red_hulk.py --package` (28 events, own synthesis, no voice).
- Strings: `src/main/lang-overlays/red_hulk/<12 locales>.json` (52 keys); codex article in `tools/codex/codex_text_heroes.py` → `lang-overlays/codex` (ru/en, other locales English like every article). Sounds: `src/main/sound-overlays/red_hulk.json`.
- Balance: `BalanceTable.RAGE_TITAN` (A), `BalanceModel.RED_HULK` (48 HP, 10/4).
- Tests: `RedHulkRulesTest`, `RedHulkAssetsTest`, `client/redhulk/RedHulkClipsTest`, `RedHulkClientRulesTest`.
- Not verified in game (no client available in the agent sandbox): sound mix, VFX readability, hit feel.
- `.memory-bank/project.md` (~64 KB) is not updated on this branch: the push tool accepts requests up to 64 KB. Add the
  topic line on merge: "- `red-hulk.md`: Red Hulk (core 1.65.0, protocol 23): heat system, six abilities, generated assets."
