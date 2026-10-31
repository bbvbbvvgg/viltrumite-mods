# Thanos six-stones event — implementation plan

Design: `docs/design/2026-10-31-thanos-six-stones-design.md`. Pushed in small increments to
`feat/thanos-six-stones-event` (never to `main`, no force-push, no merge).

1. Pure rules + tests: `ThanosRules`, `ThanosClock`, `ThanosOutcome`, `ThanosPhase`, `ThanosMove`,
   `ThanosStone`, `ThanosBrain`, `ThanosDamageCap`, `ThanosSceneTimeline` (JUnit 5, no Minecraft).
2. Server: `ThanosEntity` + registration, `ThanosEvent` (session, participants, boss bar, cleanup),
   stone executors, `ThanosCommands`, `WorldPause.beginEvent`, packets (protocol 16 -> 17), tags.
3. Assets tooling: `tools/assets/thanos_model.py` (geo + atlas + glow), `thanos_clips.py`
   (walk, turns, punches, each stone, hurt, stun, victory, defeat, snap), Od Lagna look texture,
   preview renders for review.
4. Client: renderer, stone VFX, HUD, music, scene (snap cameras, dissolve, void, Od Lagna,
   subtitles, restore), shader uniforms.
5. Sounds (`tools/sfx/thanos.py`) + overlay; lang overlays (12 locales); memory bank
   `.memory-bank/thanos.md`; skills notes.
6. Build + tests, runtime check (dedicated server + client), PR in Russian.
