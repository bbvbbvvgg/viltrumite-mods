# Hero gallery / encyclopedia (Кодекс) — design

Base: `main` @ `cb5d602` (MC 1.20.1, Forge 47.3.0, core 1.62.0, protocol 20).
Branch: `feat/hero-gallery-encyclopedia-20261031`. Ships as core 1.63.0 (protocol stays 20).

## Goal
An in-game gallery and encyclopedia of the mod: heroes, NPC and bosses, world events and contextual
interactions. It opens from the Od Lagna wish menu and never changes how the wish itself works.

## Allowed changes to the wish menu (and nothing else)
1. A small gallery button in the top-left corner of `WishScreen` (18×18 px at 6,6, book glyph,
   tooltip). It is disabled while the wish is being sent/closing. Closing the gallery (Esc or "back")
   returns to the same `WishScreen` instance, so scroll, selection and animation state are kept.
2. A small hero portrait badge (20 px, the subtitle portrait visual) on a wish card, shown only when
   *this* player has really chosen that hero at least once. The card text right edge moves left only
   on badged cards. Badge alpha follows the card (×0.6 on fulfilled cards).

Regression: `WishCodexButtonTest` checks the button rectangle does not overlap any card or header and
that the badge rule depends only on the player's own record.

## Sections
| Tab | Content | Source |
|---|---|---|
| Heroes | 10 playable heroes: portrait, role, abilities, controls, lore; "chosen by you" mark | `CodexEntries` + lang |
| NPC & bosses | 16 entries, 3 of them bosses (Conquest, Chitauri Leviathan, Thanos) | `CodexEntries` + lang |
| World events | 6 events: Conquest arrival, Viltrumite squad, meteor fall, meteor rain, Chitauri invasion, Thanos snap | `CodexEntries` + lang |
| Contextual interactions | entries of `docs/contextual-interactions.json` | other agent's catalog |

Every article: portrait/preview, short description, "how it works" block, lore, related entries.
Long texts scroll; long list names are cut with "…".

## Progress and multiplayer
- Server-authoritative record per player in `Player.PERSISTED_NBT_TAG/ViltrumiteCodex`
  (`Heroes`, `Interactions`). Copied on death by vanilla, saved with the player file.
- `HeroRegistry.changeHero` → `CodexProgress.heroChosen`. No per-hero branches.
- `CodexNetwork` (channel `viltrumitecore:codex`, version 1) sends the record only to its owner on
  login, respawn, dimension change and on every change. Other players never receive it, so badges and
  discoveries are isolated. Core protocol number stays 20.
- Interactions: `CodexProgress.discoverInteraction(player, id)` is the public hook for the contextual
  interaction engine. Until that engine calls it, discovery is only possible with `/codex discover`.

## Contextual interaction catalog
- Owned by the contextual-interactions branch. This branch only reads it: `processResources` packs
  `docs/contextual-interactions.json` (if present) as `assets/viltrumitecore/codex/contextual_interactions.json`.
- `CatalogParser` accepts schema 1 (`title_ru/_en`, `description_*`, `lore_*`, `discovery_hint_*`,
  `gameplay_result`, `visual_fx`, `sound`, free-text `participants`) and a nested `{ru, en}` text form.
  A free-text participant links every article it names ("Iron Man or an Iron Legion suit" → two chips).
  Bad entries are skipped and logged; the screen shows "catalog unavailable" if nothing loads.
- `secret: true` entries show "???" and the discovery hint until the player discovers them. The
  current real catalog (21 entries) has no secret flag, so all entries are shown open.
- Nothing is invented: if the catalog file is absent the tab says so.
- QA: `-Dviltrumitecore.codex.catalog=<file>` loads another catalog; its entries carry a TEST DATA badge.
  Test fixtures in `src/test/resources/codex/` are labelled TEST DATA.

## Localisation
Russian is primary, English mandatory and complete; other shipped locales fall back to English.
All keys are English (`codex.viltrumitecore.*`). Overlays: `src/main/lang-overlays/codex/`.

## Debug commands (op 2)
`/codex discover <targets> <id>`, `/codex forget <targets> <id>`, `/codex reset <targets>`, `/codex list`.
