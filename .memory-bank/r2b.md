# Thor rework R2b (clips) — session notes

Branch `feat/thor-r2b-clips`, draft PR #69 → base `feat/thor-rework` (never merge into the base).

## Pushing
- `git push` has no credentials here. Push through the GitHub MCP `push_files` (one request < ~64 KB,
  files < 60 KB; split big commits into "(i/n)" parts), deletions through `delete_file`. Then
  `git fetch && git reset -q origin/feat/thor-r2b-clips` (mixed) to line the local branch up.
- Merging R1 (`feat/thor-r1-body`) in: set PR #69's base to `feat/thor-r1-body`, run
  `update_pull_request_branch`, set the base back to `feat/thor-rework`.
- Before ending a session: `git fetch` + `git diff origin/feat/thor-r2b-clips` must be empty.

## Clip pipeline (tools/assets)
- `make_thor_clips.py` (clip intents; R2a table parsed from `hero/thor/combat/ThorAttacks.java`),
  `make_thor_clips_run.py` (bake / check / write, `--check`, `--only a,b`, `--report f.json`),
  `thor_clipcore.py` (per-tick IK solve, clearance, bake to keys, FP derivation), `thor_clipkit.py`
  (IK, FP arm), `thor_rig.py` (CPU pose = AnimRenderer order, OBB for the solver only),
  `thor_mesh.py` (the acceptance scan = Java ThorClipMesh), `thor_clipsheet.py` (quick box sheets).
- R1's `crouch`, `sit`, `lift_squat` are copied from the R1 file as they are (never regenerated).
- Acceptance (python and Java the same): 0.35 px grid of the moving cubes, depth into the fixed cubes,
  0.5 px; arms → torso (skip upper arm ↔ body), hammer in hand → torso + arms (left fist may hold the
  handle group, right forearm may touch the pommel group), belt hammer → arms + legs (the right fist /
  forearm may hold it only within 2.5 px of the belt grip), shins → belly / body; soles within ±1 px
  of the ground (no ankle: the leg IK puts the lowest sole corner on the planned height).
- Belt hammer: the `mjolnir_belt` locator is keyed: outward roll (pushed by the thigh, decays back
  ×0.75 per tick) and, in draw / holster, an offset (the fist pulls it off the hip).
- Draw / holster: server switches the hammer place on the contact tick (ENTER_MAIN, tick 14):
  draw = belt 0–13, hand 14+; holster = hand 0–13, belt 14+. The fist snatches the grip in one
  tick (9 → 10 draw, 15 → 16 holster release) so no tick shows a fist half through the handle.
- FP: the camera left arm is keyed (and drawn) only by clips that use it.
