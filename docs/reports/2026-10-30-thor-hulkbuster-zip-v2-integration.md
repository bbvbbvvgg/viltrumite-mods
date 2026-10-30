# Thor + Hulkbuster ZIP v2 — integration report

Target: `main` after PR #84 (`cef4eabd6f130d69c7a956138d98e5ada91bc4b7`, 8 heroes: Homelander, Regulus,
Iron Man/Hulkbuster, Reinhard, Thor, DIO, Sinclair, Sung Jinwoo; core 1.54.0, protocol 14).
Archive: `thor-hulkbuster-fixes-v2.zip` (2026-10-30), base `5cc4fe4ec5b647d4749c523413ed3837a4b76d9a`
(an ancestor of `main`), 17 original commits (their SHAs are not on the remote), 141 changed paths, no deletions.

**Status: the whole archive is integrated. Not played in a Minecraft client** (the sandbox has no GPU / display /
audio output). Ready for a manual playtest; do not merge before it.

## Method
- Every archive path was merged 3-way against the archive base: base `5cc4fe4e`, ours = this branch, theirs =
  `files/` of the archive. `changes.patch` was first checked to reproduce `files/` exactly from the base for all
  141 paths, and the 17 per-commit patches to equal `changes.patch` (plus 3 `viltrumitecore/logs/*.gz` test-run
  logs, which were dropped as junk, as the archive's INTEGRATION.md says).
- 105 paths were untouched on `main` since the base → taken as-is; 1 already identical (`PlayerHeroMixin`);
  35 changed on both sides → merged:
  - clean merges keeping both sides: 11 `lang/*.json` (pickup + 7 Hulkbuster system-check keys next to the
    PR #82 heroes' keys; JSON valid, no duplicate keys), `CREDITS.md`, `tools/assets/ironman_sources.md`,
    `.memory-bank/project.md`, `system-catalog.md`, `viltrumitecore.mixins.json` (`LivingEntityPoseBoundsMixin`
    listed once), `ThorMoveTest` (main's `HeroAction` change + PICKUP).
  - `HeroRegistry`: archive version (page reset/settle on hero change) + main's DIO / Sinclair / Sung Jinwoo
    registrations; its diff against `main` only adds the page reset/settle.
  - The earlier commits of this PR (b14f6f34, f2178cef, 5df2cf55) were condensed re-writes of parts of the archive
    (Alt pages, heavy jump, body size, FP arms). Their files were replaced by the full archive versions (supersets,
    including the full archive tests), so no behaviour of the archive is lost.
- `ThorMove.PICKUP` is the last enum constant (main added no ThorMove values) — wire ids unchanged.
- `thor.animation.json` / `thor.geo.json`: main did not change Thor clips since the base, so the archive bake is
  taken as-is (INTEGRATION.md step 3); `make_hulkbuster_clips.py` regenerates `mark48.animation.json`
  byte-identically.
- Added beyond the archive (it missed Russian, which lives in `lang-overlays/`):
  `lang-overlays/thor/ru_ru.json` += `hero.viltrumitecore.thor.move.pickup`,
  new `lang-overlays/hulkbuster/ru_ru.json` with the 7 `hud.viltrumitecore.ironman.hulkbuster.check.*` lines.

## Archive items → status in this branch
"In branch" = code/assets/tests of the item are present and green; "not in game" = never seen in a client.

| Spec item | Status | Notes |
|---|---|---|
| 1 Hulkbuster FP arms | in branch, not in game | `HulkbusterFirstPerson` (arms low at the edges, centre free, fist ≤4 ticks near contact); `HulkbusterFirstPersonCoverageTest` is a CPU copy of the render, not a screenshot |
| 2 Suit-up / exit scenes | in branch, not in game | `HulkbusterExit`, `HulkbusterShells`, suit-up HUD system check; `HulkbusterExitTest`, `HulkbusterSuitUpSceneTest`. Remnant shell is not sent to clients that join later |
| 3 Sounds (PR #45 rules) | in branch, **not auditioned** | 56 `.ogg.b64` / 31 events (`sounds.json` unchanged), 28 CC0 Freesound sources, credits in `CREDITS.md` / `ironman_sources.md`; `tools/sfx/hulkbuster.py --check` re-rendered all 56: ok (correlation ≥ 0.976) |
| 4 Hulkbuster jump | in branch, not in game | jump on press when no jet charge can follow; `HulkbusterJumpTest` |
| 5/6/8 Hitbox / camera after hero change, Shift | in branch, not in game | level-triggered `HeroBodyTracker`, `HeroBodySize`, `onSyncedDataUpdated`, Hulkbuster by snapshot on both sides, `EntityPoseBoxMixin` + new `LivingEntityPoseBoundsMixin` (applies on the dedicated server), F3+B self box (`HeroHitboxDebug`); `HeroBodyTransitionsTest` |
| 7 Alt | in branch, not in game | page reset on hero change, empty pages skipped, old saves repaired; `AbilityPagesTest` |
| 9 Freezes near blocks | **partial** (as in the archive) | no profiling; probable cause (client/server size mismatch) removed by 5/6/8 — needs an in-game check |
| 10 Thor TP clips | in branch, not in game | rebaked clips, `ThorClipBodyTest.KNOWN` empty; generator `--check` result below. Deviation: see `lift_thor` |
| 11 Mjolnir pickup TP | in branch, not in game | see extra 3 / 4 |
| 12 FP arms while lifting | in branch, not in game | `client/hero/HeroLiftArms`; Mark 48 arm scale in the lift scene **unverified** |
| 13 Walk | in branch, not in game | see extra 6 |

| Owner extra | Status | Notes |
|---|---|---|
| 1 Flat feet | in branch | ankle bones `right_foot` / `left_foot` in `thor.geo.json`, soles flat in clips |
| 2 Throw | in branch, not in game | wind-up 10 t / looped hold / release 8 t |
| 3 Hammer pickup | in branch, not in game | `ThorMove.PICKUP` (last enum value), step-in, bend, IK, contact-tick handover, `lift_thor`; `MjolnirPickupTest`; keys in all 11 languages + ru overlay |
| 4 Grip | **partial** (as in the archive) | right fist keeps its held orientation; not seen in game |
| 5 Hitbox / camera | in branch, not in game | = 5/6/8 |
| 6 Walk / run | in branch, not in game | distance-driven gait (`ThorClipPlayer.Gait`); `ThorGaitTest` |
| 7 Crouch head | in branch | head compensation −10.3° |
| 8 Legs on walking attacks | in branch, not in game | |
| 9 Alt | in branch, not in game | = 7 |

Also integrated: generators and tools (`make_thor_clips*.py`, `thor_clipcore.py`, `thor_clipsheet.py`,
`thor_mesh.py`, `make_thor_body.py`, `make_hulkbuster_clips.py`, `hulkbuster_lang.py`, `tools/sfx/hulkbuster.py`),
design notes (`docs/design/2026-10-28-*`, `docs/plans/2026-10-28-hulkbuster-cues.md`), `.memory-bank`
(project, sound) and the animation-system catalog. Not taken: the 3 `viltrumitecore/logs/*.gz` junk files and
`previews/` (solver contact sheets / CPU FP preview, not game screenshots).

## `lift_thor` two-hand grip (spec 11) — still deviates
Spec 11 asks for both palms on the handle. As in the archive, `lift_thor` (the heavy heave) holds the hammer with
the right hand; the left hand braces the knee. Both hands are on the handle during the PICKUP bend / contact
(`ThorLift` IK) and through the fade-out, and `lift_squat` is two-handed. Two fixes were tried here and dropped:
- runtime IK of the left fist onto the risen handle for the first heave ticks: the fist misses by 13–18 px and
  the left forearm passes 3.5–5 px through the torso at every tick;
- re-solving the `lift_thor` key poses in `make_thor_clips.py` (right-arm yaw −24…−8°, pitch −40…−55°, reach
  17.5–19.5, torso lean 16–34°, several handle heights): best variant still has 2.1 px left-forearm/body
  penetration and a 0.9–1.3 px miss, which `ThorClipBodyTest` rejects.
The generator and `ThorLift` are left exactly as in the archive. A clean two-hand heave needs a different heave
choreography (e.g. hammer held in front of the body instead of on the right side) — an owner decision.

## Verification (no Minecraft client)
- `./gradlew clean build`: BUILD SUCCESSFUL (core `reforgedcodex-core-1.54.0.jar`, flight `1.10.1`).
- Core tests: 2199 tests in 360 classes, 0 failures, 0 errors, 1 skipped (`ThorClipPreviewTest.export`, flag-only).
  `RegulusVisualContractTest` (the archive's one AWT failure) passes here.
- `make_thor_clips.py --check` (full, 58 clips, 621 s): 0 PROBLEM, 0 penetration ticks in every clip; output identical to the committed `thor.animation.json`.
- `make_hulkbuster_clips.py`: regenerated `mark48.animation.json` is byte-identical (46 clips).
- `tools/sfx/hulkbuster.py --check`: 56/56 ok.
- Dedicated server (`:viltrumitecore:runServer`, offline flat world): reached `Done (4.933s)`; `LivingEntityPoseBoundsMixin` applied to `LivingEntity`; no mod errors (only the usual dev-environment refmap / netty / offline-mode warnings). No player joined — the server cannot exercise client code.

## Not verified — needs the manual playtest
Thor walk/run from the side (sliding), crouch, throw with short and long hold, hammer pickup from different sides
(and the right-hand `lift_thor` heave), attacks while walking, F3+B before/after hero change, Shift, respawn,
`/tp` to the Nether, Alt Iron Man → Thor → Iron Man, Hulkbuster FP at FOV 70/110, exit under open sky and under
a roof, suit-up, listening to all Hulkbuster sounds, Mark 48 arms in the lift scene, block-adjacent freezes,
multiplayer (second client joining after an exit — remnant shell is not synced to late joiners).
