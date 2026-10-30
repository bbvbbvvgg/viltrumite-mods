# Thor + Hulkbuster ZIP v2 — integration audit

Target: main after PR #84, commit `cef4eabd6f130d69c7a956138d98e5ada91bc4b7`.
Archive: `thor-hulkbuster-fixes-v2.zip` (2026-10-30), base `5cc4fe4ec5b647d4749c523413ed3837a4b76d9a`.
Archive contains 17 original commits and 141 changed paths; their SHAs are **not** available on the remote.

## Implemented in this draft
- Alt page switching skips empty pages; resets on hero changes and repairs old saves.
- Hulkbuster's ground jump fires on key press when jet charge is unavailable; the step bonus is centralized.
- Unit tests for both fixes.
- Mark 48 first-person arms moved down and out from the reticle (not yet visually verified).
- Shared hero body dimensions, level-triggered hitbox healing, hero-data update refresh,
  consistent Hulkbuster snapshot sizing, dismount bounds and F3+B self-debug box.

## Not yet integrated (do not merge this PR as a complete ZIP integration)
- Shared body dimensions and camera refresh: HeroBodyTracker, HeroBodySize, HeroPlayer,
  EntityPoseBoxMixin, LivingEntityPoseBoundsMixin, PlayerHeroMixin, IronManHero snapshot sizing,
  HeroHitboxDebug, and transition tests.
- Thor: ankle bones and rebaked animation/geometry JSON, distance-driven gait, phased throw,
  pickup move and hammer interactions, FP lifting, translations, tests.
- Hulkbuster: first-person renderer, exit and suit-up scenes, shell/VFX, 56 CC0 sound assets,
  animation JSON and related tests.
- Scripts, credit metadata, design notes and other archive changes.

## Critical integration notes
- Keep all 8 heroes from PR #84; preserve core 1.54.0 and protocol 14.
- Do not overwrite current `HeroRegistry`, `HeroPlayer`, localization or the full repo with
  the archive's `files/` directory. Port changes against current main.
- ThorMove.PICKUP must be appended to the enum (wire compatibility).
- The archive reports 2042 tests with one AWT-only failure and one skipped preview test, but
  this draft has **not** been built or playtested.
- Agent explicitly reports no client run; grip on lift_thor differs from the two-hand requirement;
  block-adjacent freezes are only tentatively fixed.

This is a staged code integration and audit, **not** the complete patch.
