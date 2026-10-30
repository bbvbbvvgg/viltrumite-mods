# Final integration of PR #85–#92 (2026-10-30)

Target: draft PR #85, branch `fix/thor-hulkbuster-zip-v2-integration-20261030`, based on `main`
`cef4eabd6f130d69c7a956138d98e5ada91bc4b7` (unchanged; nothing was merged into `main`, no force-push).
Core **1.56.0**, network protocol **16**.

## Source PRs and final heads

| PR | Branch | Final HEAD | Base | In #85 |
|---|---|---|---|---|
| #85 | `fix/thor-hulkbuster-zip-v2-integration-20261030` | `1d8635ae` (before integration; backup branch `backup/pr85-1d8635ae`) | `main` | target |
| #86 | `feat/thor-rework-20261030` | `8afceb07d5e9d7d66747e3a6c0ed9424ff284262` | #85 | merge `10aaaa8a` |
| #87 | `fix/general-polish-15-20261030` | `2e6c419d94245ade3add89b0c86e5d457f6bb73b` | `main` | merge `a28a3776` |
| #88 | `feat/od-lagna-wish-20261030` | `2d050b7a0771efb083644f53c99e1cd5b0ad6e87` | #85 | merge `a2bd7260` |
| #89 | `feat/regulus-reborn` | `12e4df93f984cf442244160a114d1b652117a449` | `main` | merge `dcbd0433` |
| #90 | `feat/dio-polish` | `cd9c6fcdc814ccbb9fdcce8c0381a600083af2da` | `main` | merge `a8083627` |
| #91 | `feat/sinclair-cinematic-polish` | `b5d52e1728df52b8f2154b919c5dcf31c7662cc1` | `main` | merge `4292e773` |
| #92 | `fix/sinclair-polish-20261030` | `c3359c456cbbf89311c420110f684e84185881fe` | #88 | merge `ce6596d1` |

Every head above is an ancestor of the final HEAD (`git merge-base --is-ancestor`), no source branch received
commits after the integration started, and all source branches are kept.

## Dependency graph and merge order

```
main ── #85 ──┬── #86
              └── #88 ── #92
main ── #87, #89, #90, #91 (independent)
```

Order (real three-way merges, history kept): #86 → #88 → #92 → #90 → #87 → #89 → #91, then the integration
fixes. Child PRs were merged as branches (their shared ancestors were not re-applied as patches).

## Conflicts and how each was resolved

Every merge was re-run locally for this report: for all non-conflicted paths the branch equals git's own
three-way result byte for byte; only the conflicted paths below carry a manual resolution.

| Merge | Conflicted paths | Resolution |
|---|---|---|
| #86 | — | clean; Thor v3 (#86) supersedes #85's Thor where it reworked the same mechanic, #85's lift / gait / hitbox / camera fixes remain |
| #88 | `HeroRosterIntegrationTest` | both packet tables kept (wish packets + Thor GUARD) |
| #92 | — | clean |
| #90 | `.memory-bank/heroes-integration.md`, `.memory-bank/sound.md` | both texts kept |
| #87 | — | clean |
| #89 | `build.gradle`, `CoreMessages`, `AlwaysVisibleOffhandMixin`, `Evangelium.java`, `EvangeliumTest`, `RegulusState`, `EvangeliumItem`, `HeroRosterIntegrationTest`, memory bank (+ `ShippedSounds`) | see below |
| #91 | `SinclairClient`, `SinclairFirstPerson`, `SinclairLangTest`, `.memory-bank/sound.md` | both Sinclair series kept: #92's arm replacement + #91's scene camera/cinematic, both listener sets registered, lang test counts both key sets |

### #89 details
- **Protocol**: #86, #88 and #89 each claimed "15" with different tables. Final table: all older packets unchanged,
  then `WishChoiceC2SPacket`, `WishSceneS2CPacket` (#88), then `RegulusFxS2CPacket` (#89); Thor's GUARD state is in
  the Thor snapshot (#86). Version **16**, so a client from any single "15" branch is refused.
  `HeroRosterIntegrationTest` pins order, uniqueness and the version.
- **build.gradle sound overlays**: both layouts supported — `sound-overlays/<part>/sounds.json` (#90, may replace
  base events) and `sound-overlays/<part>.json` (#89, add only); an event defined by two overlays fails the build.
- **AlwaysVisibleOffhandMixin**: #87's `showsIdleOffhand` (one arm for an ordinary Human) + Regulus / Iron Man /
  Reinhard / Thor `wantsOffhand` hooks together.
- **Regulus**: #89 removed the first kit (`Evangelium.java`, `EvangeliumTest`); the old classes were not restored.
  `EvangeliumItem` is #89's inert keepsake (old saves keep their stacks), `RegulusState` is #89's with the
  one-time migration of the first-kit `RegulusCooldowns` tag.

## Fixes carried between architectures (integration commits)
- **#87 Evangelium fix → Regulus Reborn (Kingdom of One)**: a refused ult names its recharge in the action bar
  (`message.viltrumitecore.regulus.ult_refused.cooldown`, 12 locales via `tools/lang/regulus_reborn_lang.py`);
  the cooldown only runs while no scene holds it, so an aborted/finished judgement recharges and opens again.
- **`/hero cooldown reset` (#87) for every hero**: the auto-merged `RegulusHero.resetCooldowns` still referenced the
  first kit and did not compile; rewritten on `RegulusState.clearCooldowns()`. #87 predates Thor v3 and did not
  cover DIO / Sinclair, so `resetCooldowns` was added to Thor v3 (`ThorCombatState.clearCooldowns`), DIO and Sinclair.
- **Sinclair #91 + #92**: #92's big copied-act card opened at the capture card tick and then faded under #91's
  copy cinematic, reappearing after it. `ActCardPopup` now waits through CAPTURE/COPY and opens with the window,
  so the full 6 s hold is readable after the scene (without the cinematic: 6 ticks later than before).
- **Lang clash #87 + #89**: `hero.viltrumitecore.regulus` was defined by both the `polish` overlay ("Regulus") and
  the `regulus` overlay ("Regulus Corneas"); Regulus Reborn owns its name, the key was removed from `polish`.
- **SinclairLangTest**: the merged key count was 135; the real union is 78 + 8 (#92) + 51 (#91) = 137 (all present
  in all 12 locales; `tools/lang/sinclair_lang.py` regenerates the files unchanged).
- Core 1.56.0; memory bank updated.

New tests: `RegulusCooldownResetTest`, `HeroResetCoverageTest`, `ActCardPopupTest.waitsForTheCopyCinematic`.

## Build and tests
Java 17 (Corretto 17.0.21), Gradle 8.8, Forge 1.20.1-47.3.0: `./gradlew build` — **BUILD SUCCESSFUL**,
**2247 tests, 0 failures, 0 errors, 1 skipped** (pre-existing skip). Jars: `reforgedcodex-core-1.56.0.jar`,
`reforgedcodex-flight-1.10.1.jar`. processResources merges every lang overlay and both sound-overlay layouts
without a clash; `regulus_reborn_lang.py` and `sinclair_lang.py` regenerate the committed files; no conflict
markers in the tree. Before the integration fixes the branch did not compile (`RegulusHero.resetCooldowns`) and two
tests failed (`SinclairLangTest`, `ThorIntegrationSeamsTest` lang duplicates).

## In-game, dedicated server and multiplayer
**Not run.** The project owner asked not to launch the game for this pass. No client, dedicated-server or
two-client multiplayer checks were done in this integration; the cross-hero scenarios (DIO time stop vs. other
casts, Od Lagna refusals under control, Sinclair copying Thor/DIO/Jinwoo, camera/HUD recovery on death, logout and
dimension change, hitboxes on hero change) are covered only by unit/integration tests and need a manual playtest.

## Known limitations
- No manual playtest of any hero in this pass (see above).
- Sinclair card timing with the cinematic is verified by pure timing tests only.
