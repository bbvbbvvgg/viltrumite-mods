# Hero ability icons redraw (Regulus, Homelander, Iron Man)

Goal: bring every hero ability icon to the art level of the Reinhard icons (PR #53, `tools/assets/reinhard_icon_art.py`).
The Reinhard icons are the reference and stay byte-identical. Mechanics, controls, balance and slot layouts are unchanged.

## Reference audit: what makes the Reinhard icons work

- 32×32 art grid saved 2× nearest as 64×64: one art pixel is one screen pixel at GUI scale 2 (the HUD draws icons at 16 GUI px).
- Earth (`blessing_bearer`) frame: a bevelled square frame re-tinted per state, charcoal radial backdrop, navy `#172038` outlines.
- Small palette (Endesga 64 + Earth colours, 22–52 colours per icon), top-left ramp shading, plus-shaped sparkles, no smooth gradients.
- One concept per icon, subject large and centred; the action itself is drawn (impact bursts, arcs, motion lines, the target).
- No two icons share a subject, so each one reads at 16 px by silhouette and accent colour.

## Audit of the other heroes (before)

| Hero | Icons | Problems |
| --- | --- | --- |
| Regulus | lions_heart, debris_kick, mania, greeds_embrace, counter, punch | 16×16 source (4× blockier than the house grid), 188–218 colours of noisy downscaled renders, full-bleed saturated backgrounds (yellow, brown, purple, blue-grey, red), no frame. At HUD size the subjects are unreadable smears; the mechanic is not visible. Five PNGs in `resources/`, punch already in `binassets/`. **Needs a full redraw.** |
| Homelander | punch, dash, lasers, focus, roar, thunderclap; flight bar | 64×64 but 970–1570 colours: one rendered skin figure repeated in every icon on smooth gradient backgrounds. The mechanic (fist, beam, roar, clap) is a small detail; punch/roar/thunderclap/focus look alike at 16 px. The flight bar fell back to the legacy 16×16 Viltrumite takeoff/supersonic art. |
| Iron Man | 8 kit, 7 mark signatures, 3 Hulkbuster | 64×64 but 900–2000 colours: rendered 3D armour figure repeated in most icons on coloured gradients, effects at the edge of the frame; the Hulkbuster trio and several signatures are indistinguishable at HUD size. |
| Viltrumite (legacy `textures/gui/ability/*.png`) | block, grab, chop, … | Original JAR art. Not shown for any current hero in the HUD or ability screen (every hero overrides `abilityIcon` for owned abilities); left untouched. |

## Decisions

- [x] Shared house kit `tools/assets/icon_art.py` (palette, Earth frame re-tinted via `FRAMES`, canvas, masks, outlines, sprites, foe, arrows, glow lines, registry). `reinhard_icon_art.py` now imports it; all 17 Reinhard icons regenerate byte-identical.
- [x] Per-hero art modules: `regulus_icon_art.py`, `homelander_icon_art.py`, `ironman_icon_art.py`; one driver `tools/assets/make_hero_icons.py` (`--hero`, `--only`, `--check`, previews in `build/hero_icons/`).
- [x] Frames: Regulus white-gold (Counter: crimson "madness" frame), Homelander royal blue, Iron Man red kit / gold mark signatures / graphite Hulkbuster. Mark signatures use colours sampled from each mark skin (Mark 7, 42, 15, 39, 17, Iron Heart, War Machine).
- [x] Regulus: 16×16 → 64×64; the five PNGs move from `resources/` to `binassets/*.png.b64` like every other generated icon (same resource path `textures/gui/ability/regulus/<name>.png`).
- [x] Homelander owns the flight-bar ids `viltrumite:fast_takeoff` / `viltrumite:supersonic_flight`: two new icons; `HomelanderAbilities.icon` no longer skips UTILITY ids (`speed_lock` has no icon and still falls back).
- [x] Iron Man: all 18 icons from `ironman_icon_art.py`; `make_ironman_icons_v3.py` kept for history and now refuses to overwrite the icons without `--legacy-overwrite`. `ironman_sources.md` updated.
- [x] Concepts (one per mechanic):
  - Regulus — Lion's Heart: heart with a flat pulse inside a stopped-time sphere, frozen arrows; Debris Kick: Regulus kicking a swing arc of grass/dirt blocks; Mania: fist on a gold chain pulling a foe; Greed's Embrace: dotted gold lattice dome holding a foe, stored-damage shards; Counter: teleport flash, fist slamming a flattened foe into a crater, uppercut trail; Punch: fist cracking a stone block, gold burst and hearts.
  - Homelander — Sonic Punch: glove fist with a shock cone shattering chunks; Dash: flying through a brick wall; Heat Vision: profile with two red beams and scorch; Focus: glowing irises over an x-ray wall with outlined creatures; Roar: open mouth, ring cone, foe blown back; Thunderclap: two gloves, burst, cracked ground; Fast Take-Off: fist-up launch with a ground ring; Cruise Flight: flight over clouds with vapour cone.
  - Iron Man — Unibeam, missile fan to lock brackets, nano blade + hammer, flares diverting a missile, scan brackets + card, Veronica pod on its fire trail, helmet with faceplate up, half-Tony/half-suit, the seven mark signatures in their mark colours, Hulkbuster grab/slam/hop each with a distinct motion (throw arc, crater, hop arc).
- [x] Core 1.45.0 → 1.46.0 (content); flight unchanged. Thor branches (#55–#63) also use 1.46.0+; whoever lands second rebumps.

## Verification

- [x] `python3 tools/assets/make_hero_icons.py --check` (32 icons match the committed b64); `make_reinhard_icons.py` output unchanged.
- [x] `HeroAbilityIconsTest`: every Homelander (8), Iron Man (18) and Regulus (6) icon id resolves to a 64×64 RGBA texture with transparent corners and an opaque centre; `RegulusVisualContractTest` checks 64×64.
- [x] `python3 tools/assets/check_hero_resources.py`: 0 new problems (6 known legacy).
- [x] `./gradlew build --no-daemon`: 1562 core + 12 flight = 1574 tests, 0 failures.
- [x] HUD mock at real size (GUI scale 2 and 3, day and dark backgrounds, per-hero panel style) in `tools/assets/icon_preview.py`; before/after sheets in `docs/reports/hero-icons/`.
- [ ] In-game look not checked: no display in this environment.
