# Wave 1 · Session 23 · Clashes (beam struggle, clinch, sky brawl)

- Package: `hero/combat/clash/` (server), `client/clash/` (client). Overlays: `lang-overlays/clash/`, `sound-overlays/clash.json`.
- Beam contract: a source calls `ClashBeams.report(beam)` every server tick while the beam is on. An entry without a report for 2 ticks expires. No register/unregister pair: nothing leaks after a crash or a hero change.
- Struggle math is pure (`BeamMath`, `TugOfWar`) and has JUnit tests.
- The server owns every outcome. Client input is a pulse or a key; the server caps the rate and widens timing windows by half the player latency.
