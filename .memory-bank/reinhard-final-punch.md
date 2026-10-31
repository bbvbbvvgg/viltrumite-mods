# Reinhard — THE FINAL PUNCH (secret, Shift+N)

Contract: `docs/design/2026-10-31-reinhard-final-punch.md`. Branch `feat/reinhard-final-punch-20261031` (no PR).

- Server: `hero/reinhard/finalpunch/` (FinalPunch handler, Rules timeline, Targets registry + pick, Lines).
- Client: `client/reinhard/finalpunch/` — Client (scenes per caster, packets, lifecycle), Input (Shift+N),
  Cues (pure timeline of layers/sounds), Shots + Camera (+ `ReinhardSceneCameraMixin`), Audio (loops + duck),
  Vfx/Draw (soft additive shapes, no square particles), Jaw (flying jaw piece + wound), Screen (bars + post
  grade merged in GameRendererDashMixin), Fist (fallback table, generated).
- Body: `client/reinhard/FinalPunchPose` plays `animations/reinhard/final_punch.animation.json` on the scene
  clock. Regenerate: `python3 tools/assets/make_reinhard_final_punch.py` (~4 min, IK on every sample),
  `--check` in review. Print the golem constants into FinalPunchPose if the contact pose changes.
- Tests: FinalPunchRulesTest, FinalPunchTargetsTest, FinalPunchCuesTest (layers, cues ↔ 13 sound events,
  camera continuity/clearance, chord, jaw), FinalPunchPoseTest (Java rig: jaw contact, golem, hit-stop,
  stance at both ends, fist table), ReinhardClipsTest (+ final_punch file).
- QA: `-Dviltrumitecore.reinhard.qa=true`, `/reinhardqa <player> mode_restrained`, spawn villager/iron golem,
  look at it, Shift+N (xdotool: hold shift, tap n). Sounds are procedural CC0 and were not auditioned.
