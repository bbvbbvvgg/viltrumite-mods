# Thor rework — integration branch `feat/thor-integration`

Review branch that assembles every Thor stage without touching `main` or the stage PRs.
Stage PRs (#72 R1, #67 R2a, #69 R2b, #66 R3, #71 R4, #70 R5) stay open against `feat/thor-rework`.

## Real history (checked 2026-10-28)

- `feat/thor-rework` = spec only. R3 (#66) is 3 commits on it.
- R1 (#72) contains R3. R2a (#67) is 6 commits on `feat/thor-rework` (no R3).
- R2b (#69) already contains R3 + R2a + R1 (merged twice) — it is the natural base.
- R4 (#71) and R5 (#70) are independent of R1/R2/R3.

## Reproducible merge order

1. `git checkout -b feat/thor-integration origin/feat/thor-r2b-clips`  (R3 + R2a + R1 + R2b)
2. `git merge --no-ff origin/feat/thor-r4-vfx`
   - conflict: `client/thor/ThorParts.java` deleted by R1, edited by R4 (in-flight Mjölnir spin + anchors).
   - resolution: ThorParts stays deleted; R4's spin (MjolnirBody only, 6 translucent ghosts) and the
     ThorAnchors capture are ported into `ThorRenderer.drawHeldMjolnir` for the 3P body and the FP arms.
3. `git merge --no-ff origin/feat/thor-r5-sound`
   - `HeroImpactFx` bolt: R4 layered `ThorFx.BOLT` preset **and** R5's hero thunder (vanilla pair only without one).
   - `WrathMove` contact: R4 `WRATH_STRIKE` preset **and** R5 `WRATH_WINDUP` stop + `WRATH_CONTACT` cue.
4. Later R2b commits arrive by `git merge origin/feat/thor-r2b-clips` (no cherry-picks).

Never merge this branch into `main` without the owner.
