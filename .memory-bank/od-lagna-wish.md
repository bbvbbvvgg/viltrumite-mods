# Od Lagna's Wish (PR #88, branch feat/od-lagna-wish-20261030)

- Design: `docs/design/2026-10-30-od-lagna-wish-design.md`. One item `viltrumitecore:od_lagna_wish`, own tab `od_lagna_tab`.
- Registry: `hero/wish/OdLagnaWishes` (ids, hero keys, scene style, `WishGrant`). A new hero = one `register(...)` line +
  `wish.viltrumitecore.<id>` / `.echo` in `src/main/lang-overlays/od_lagna/*.json` + optionally a `WishStyle`.
- Server: `WishRitual` (validate → root → START; tick; SWAP at 40 = `HeroRegistry.changeHero` via the grant; END at 64).
  Refused in a time stop / under control; aborted on death, logout, dimension change. The item is NOT given on login
  (removed on request): creative tab / `/give` only. First choice (`!hasChosenRace`) needs no item.
- Hulkbuster wish = `HulkbusterWish` (Iron Man + `HulkbusterLayer.grantByWish()`), refuses with no room.
- Client: `client/wish/WishScreen` (GUI), `WishSceneClient` (beats, camera, input lock, HUD), `WishSceneFx` (VFX/sounds),
  pure `WishLayout`/`WishCamera`/`WishTimeline` (tested). `OpenRaceScreenS2CPacket` opens `WishScreen(first=true)`.
- Network: `WishChoiceC2SPacket`, `WishSceneS2CPacket` appended last; PROTOCOL_VERSION 15.
- Assets: lang via lang-overlays (no edits to the big locale files); sounds in namespace `od_lagna`, client-only events.
