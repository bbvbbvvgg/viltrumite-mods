# Hero gallery / encyclopedia ("Кодекс")

Branch `feat/hero-gallery-encyclopedia-20261031`, core 1.63.0, protocol 20 unchanged. Design: `docs/design/2026-10-31-hero-gallery-encyclopedia-design.md`.

## Entry point (the only two Od Lagna menu changes)
- `client/wish/WishScreen`: small book button top-left (`CODEX_X/Y = 6`, `CODEX_SIZE = 18`), tooltip `codex.viltrumitecore.gui.open`, disabled while the wish is closing/sent. Opens `new CodexScreen(this)`; Esc / back returns to the same WishScreen instance (state untouched).
- Per-card badge (`BADGE = 20`, hero portrait) only when `CodexClientState.hasHero(wish.heroKey())`; text right bound shrinks only on badged cards. Anything else in WishScreen must stay as it was (`WishCodexButtonTest`).

## Progress (server authoritative, per player)
- `codex/CodexProgress`: `Player.PERSISTED_NBT_TAG` → `ViltrumiteCodex` {`Heroes`, `Interactions`} string lists. Survives death, relog, dimension change.
- `HeroRegistry.changeHero` → `CodexProgress.heroChosen` (hero keys via `canonical(HeroId)` / `HeroId.fromKey`, no per-hero branches).
- Sync: own channel `viltrumitecore:codex` v1 (`CodexNetwork`), sent only to the owner (login / respawn / dimension / change). Core protocol (20) is not touched.
- Contextual interactions API: `CodexProgress.discoverInteraction(ServerPlayer, id)` — the contextual-interaction engine must call it when an interaction actually fired for that player (not wired yet: their engine is on another branch).
- Debug: `/codex discover|forget <targets> <id>`, `/codex reset <targets>`, `/codex list` (op 2).

## Content
- `client/codex/CodexEntries`: 10 heroes, 16 NPC (incl. bosses conquest, chitauri_leviathan, thanos), 6 world events. Texts only in lang (`codex.viltrumitecore.*`), keys English.
- Interactions: `docs/contextual-interactions.json` (owned by the contextual-interactions agent) is packed by `processResources` into `assets/viltrumitecore/codex/contextual_interactions.json` only if it exists. Parser `codex/catalog/CatalogParser` accepts schema 1 (`title_ru/_en`, `discovery_hint_*`, `gameplay_result`, `visual_fx`, free-text participants) and the nested `{"ru":..,"en":..}` form; problems are logged, never crash.
- Free-text participants: `CodexEntries.resolveAll` links every article a name mentions ("Iron Man or an Iron Legion suit", "the summoned NPC Thor", lists in parentheses); unknown names stay as raw catalog text (English only, the catalog has no localized participant names).
- Secret entries (`secret: true`) are shown as "???" + hint until discovered. The real schema-1 catalog (21 entries on 2026-10-31) has no secret flag → all shown open.
- QA override: `-Dviltrumitecore.codex.catalog=<abs file>` → entries marked TEST DATA. Fixtures: `src/test/resources/codex/test_catalog*.json`.

## Lang
- Overlays `src/main/lang-overlays/codex/<locale>.json` (12 locales, ru/en full, others English fallback); helper scripts `tools/codex/*.py`.

## Portraits
- `CodexPortraits`: heroes via `HeroPortraits.registered()` / `HeroSkins.provider()`; skinned NPC busts (`PlayerBustPortrait.renderSkin`); others via entity preview (`AvengerEntity.previewKind`), halo fallback. Clipped with `HeroPortraits.withOuterClip`.

## QA harness (SubtitlePlaytest, `-Dviltrumitecore.subtitles.qa=true`)
- `wish`, `click x y`, `mouse x y`, `scroll x y d`, `keypress esc|down|…|<glfw>`, `codex <tab> <index> [scroll]`, `guiscale n`, `window w h`, `lang code`, `codexstate`.

## Verified (2026-10-31)
- Dedicated server + 2 clients (Alice/Bob): Alice wish → Thor badge only for Alice; Bob → DIO only for Bob; `/codex discover Alice …` not visible to Bob; playerdata NBT holds separate records.
