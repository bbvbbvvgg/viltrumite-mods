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
- QA harness (`SubtitlePlaytest`): `bike` logs `HellCycleClient.qaSummary`; `bikes` logs `HellCycleClient.qaAll`
  (every bike this client sees, remote ones too: rider, local, pos, yaw, v, lean, rpm, flags, dash, burn, park,
  loops + FX entries and playing loops); `key <name> <ticks>` holds keys (concurrent; a new hold of the same key
  replaces its pending release), `press 1..6` = R/Y/Z/V/B/N; `look yaw pitch`; `cam back|front|fp`. Use husks with
  `PersistenceRequired:1b` as targets (no sun burn, no despawn); `/damage <villager> 100 minecraft:mob_attack by <mob>`
  makes a mob guilty.
- Life cycle (verified on a dedicated server, 2 clients): `GhostRiderState.respawnCopy` keeps cooldowns **and the
  form** (spec §7; before, the Rider respawned as Johnny and `onRespawn` was dead code); `onRespawn` dresses a Rider
  in the chassis at full health (36 HP, armor 8). After a dimension change the player is already elsewhere, so the
  bike left behind burns on its own next tick (`serverTick`: owner not in its level); `onDimensionChange` cleanup
  ends chain/stare. Clients drop bike FX in `HellCycleEntity.onClientRemoval` (`ClientLevel` removes entities via
  `setRemoved`, not `remove()` — FX entries used to leak on every observer).
- Engine loops start on demand: a layer starts at target volume ≥ `AUDIBLE` 0.02 and leaves below `SILENT` 0.003
  (`HellCycleEngineSound.starts/leaves/target`) — no "Tyres screech"/"Hell Cycle roars" subtitles for a parked bike.
  Summon one-shots: `bike_form` 0.8, `ignition` 0.55 (at 1.0 they stacked to +2.5 dBFS next to a running bike).
- Hooked players are held by `place()` every tick at `GhostRiderState.chainHold` (a server-side zero velocity
  never reaches a player's own client); mobs keep `setDeltaMovement(0)`.
- MP QA notes: creative players are never targets (`GhostRiderTargets.mayHit`) — test PvP in survival. The first
  Nether visit stalls a 2-core server ~14 s (chunk generation): a bike "lingering" then is the stall, not a leak.
  Remote bikes trail ~3 ticks (vanilla 3-step lerp); a low-FPS rider client sends bursty vehicle moves (visible
  per-tick jitter on observers). Report: `docs/reports/2026-11-02-ghost-rider.md`.
- `.memory-bank/project.md` is not updated on this branch (bigger than the push tool's per-file limit). Topic line
  to add on merge: "- `ghost-rider.md`: Ghost Rider + Hell Cycle (core 1.70.0, protocol 28): bike physics,
  innocent-sparing combat, Penance Stare from real deeds."
