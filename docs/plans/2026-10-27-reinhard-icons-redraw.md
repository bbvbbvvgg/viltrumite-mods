# Reinhard ability icons redraw

Goal: redraw all 17 Reinhard ability icons in the style of the Earth status icon
(`textures/gui/reinhard/blessing_bearer.png`, copied from the user archive and left unchanged).

- [x] Start from PR #52 (`integrate/reinhard-pr50-pr51`, core 1.42.0).
- [x] Rewrite `tools/assets/reinhard_icon_art.py`: 32×32 grid (one art pixel = one screen pixel at GUI scale 2), Earth frame pixels reused and re-tinted per mode (Restrained silver, Knight blue, Holy Sword gold), charcoal radial backdrop, navy outlines, top-left ramp shading, plus-shaped sparkles, Earth + Endesga 64 colours. Hand-drawn ASCII sprites (fists, palm, boot, knife hand, Reinhard figures, opponent) plus procedural strokes, bursts and scenes. Saved 2× nearest as 64×64.
- [x] One concept per ability (see the PR body); no icon reuses another's subject.
- [x] Regenerate with `python3 tools/assets/make_reinhard_icons.py`; mode icons and copied archive files are unchanged.
- [x] Core 1.42.0 → 1.43.0 (release assertion updated); flight unchanged.
- [x] `./gradlew build --no-daemon` passed locally. In-game look not checked: no display in this environment.
