# Memory bank — Ghost Rider (Johnny Blaze) — branch feat/ghost-rider-hell-on-wheels

- Design: `docs/design/2026-11-02-ghost-rider-design.md`; playtest report: `docs/reports/2026-11-02-ghost-rider.md`.
  Core **1.70.0**, protocol **28**.
- Order: `HeroId.GHOST_RIDER` = 12 (after RED_HULK, last); `HeroAction` GHOSTRIDER_CYCLE..GHOSTRIDER_STARE (6,
  contiguous, after REDHULK_OVERHEAT); packets `HellCycleControlC2SPacket`, `HellfireS2CPacket` appended after
  `AftermathS2CPacket`; entity type `hell_cycle`. `HeroRosterIntegrationTest` pins all of them.
- Choosing: Od Lagna's wish `vengeance` (`OdLagnaWishes`, style `hellfire` → `WishStyle.HELLFIRE`,
  `WishSceneFx.hellfire`: flame ring, two coiling chains, fire column + skull flare on the swap). The legacy
  `RaceSelectionScreen` also lists him (test guard), but that screen no longer opens on login.
- Server `hero/ghostrider/*`: `GhostRiderRules` (all numbers, pure), `GhostRiderMove`, `GhostRiderState`
  (cooldowns, form, NBT), `GhostRiderCombat` (LMB 3-hit lash, RMB hook/yank, bike lash/hook/tow, `aimed()` =
  exact crosshair hit first, else nearest to the line of sight), `GhostRiderSkills` (six abilities),
  `GhostRiderTargets`, `Hellfire` (own burn, not vanilla fire), `PenanceLedger` (SavedData of deeds: kills of and
  harm to players/innocents; players also read vanilla stats), `GhostRiderHero`, `GhostRiderEvents`, `GhostRiderLines`.
- Bike `hero/ghostrider/bike/*`: `HellCyclePhysics` (pure, tested: throttle, brake/reverse, lean, drift, slopes,
  jumps, crashes), `HellCycleRules` (numbers), `HellCycleEntity` (driver-authoritative like a vanilla boat: the
  controlling client simulates, others lerp; flags synced), `HellCycleContacts` (speed hits on hostiles only,
  nudges for innocents). The bike never breaks blocks.
- Innocents: every automatic or area hit skips `PenanceLedger.innocent` (villagers, traders, city folk, pets, allays)
  and `Bystander` unless they have sins; the on-foot lash hits an innocent only when aimed within 12°.
  Penance Stare: sins = 5·player kills + 3·innocent kills + ⌊harm/10⌋; damage 6 + 2.5·sins, cap 40, bosses
  min(60, 20 % HP); the verdict releases the stare freeze before the damage.
- Client `client/ghostrider/*`: renderer/poser/gear/anchors (skull, jacket, chains), `GhostRiderCamera` (FOV,
  root-slow compensation, stare), `GhostRiderVfx`/`GhostRiderFxRecipes`/`HellfireDraw` (pixel hellfire),
  `GhostRiderChains`, panel style, head preview; `bike/*`: `HellCycleRenderer`, `HellCycleCockpit` (first-person
  bars and shoulders), `HellCycleRiderPose`, `HellCycleTrails` (fire trail), `HellCycleEngineSound` (rpm loops),
  `HellCycleClient` (controls → `HellCycleControlC2SPacket`, `qaSummary()`).
- Assets are generated: `tools/assets/ghost_rider_{bike,body,fx,icons}.py`, `tools/sfx/ghost_rider.py` (own
  synthesis, CC0). Strings `src/main/lang-overlays/ghost_rider/<12 locales>.json` (all translated), wish lines in
  `lang-overlays/od_lagna`; sounds `src/main/sound-overlays/ghost_rider.json`.
- Tests: `GhostRiderRulesTest`, `HellCyclePhysicsTest`, `client/ghostrider/GhostRiderClientRulesTest`,
  `HellCycleCockpitTest`; `OdLagnaWishesTest` / `WishClientMathTest` cover the wish and the style.
- QA harness (`SubtitlePlaytest`): `bike` logs `HellCycleClient.qaSummary`; `key <name> <ticks>` holds keys
  (concurrent), `press 1..6` = R/Y/Z/V/B/N; `look yaw pitch`; `cam back|front|fp`. Use husks with
  `PersistenceRequired:1b` as targets (no sun burn, no despawn); `/damage <villager> 100 minecraft:mob_attack by <mob>`
  makes a mob guilty.
- `.memory-bank/project.md` is not updated on this branch (bigger than the push tool's per-file limit). Topic line
  to add on merge: "- `ghost-rider.md`: Ghost Rider + Hell Cycle (core 1.70.0, protocol 28): bike physics,
  innocent-sparing combat, Penance Stare from real deeds."
