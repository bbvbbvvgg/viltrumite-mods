# City structural collapse (core 1.68.0, protocol 26, branch feat/city-structural-collapse)

Contract for other systems: `docs/city-collapse-integration.md`. Sounds: `tools/assets/city_collapse_sources.md`.

## Map
- Server `world/collapse/`: `StructuralDamage` (damage scopes, explosion recording, `impact`, `volume`),
  `DamageLedger` (per-level memory, 8^3 cells, per-entry weight/tick/cause), `CollapseAnalyzer` (pure: building
  check, rules SEVERED / OVERHANG / LOCAL, truncation), `CollapseDirector` (settle → analyse → `Active` timeline,
  events, packets, limits, region cooldown), `CollapseRules` (all numbers), `CollapseEvents` (Forge wiring),
  `CollapseCommands` (`/collapse`), `CollapseSounds` (12 events), `CityCollapseEvent`, `CollapseInfo`.
- Client `client/collapse/`: `CollapseClient` (≤ 4 scenes, render AFTER_ENTITIES), `CollapseScene` (snapshot,
  cracks, shedding, LOD, phases), `CollapseMotion` (pure fragment grouping and tracks), `FragmentMesh` (baked VBO,
  inner faces culled), `CollapseDust`, `CollapseAudio` (pure gain/duck/pitch + cues with sound delay).
- Wiring: `CityJournalMixin` → `StructuralDamage.onSetBlock`; hero/Chitauri/Leviathan call sites use
  `run`/`call`/`orDefault`. Flight dash is not wired.

## Rules that matter
- A block change feeds the ledger only inside a scope; collapse changes run in `suppressed`.
- The collapse cause = most recorded weight around the spot (`DamageLedger.dominantCause`), not the level-wide
  last cause (bug found in game: two buildings in one tick shared one cause).
- `DamageLedger.settled` returns one candidate; other buildings' settled clusters stay dirty for the next ticks
  (bug found in game: the second building was dropped). At capacity the director does not call `settled`.
- Terrain vs building is counted from the damage centre up (bug found in game: a strike that also cratered the
  street was rejected as "terrain").
- `Finished` is always posted once per started collapse (also aborted).

## QA harness (worked, 2 CPU / 4 GB, Xvfb :99, llvmpipe)
- SP: `JAVA_TOOL_OPTIONS="-Xmx1700m -Dviltrumitecore.subtitles.playtest=true -Dviltrumitecore.subtitles.playtest.world=true
  -Dviltrumitecore.collapse.log=true" ./gradlew :viltrumitecore:runClient --no-daemon`; first start shows the
  accessibility onboarding (`button 3`), the experimental-world warning (`button 0`). Commands via
  `run/subtitle_qa.txt`: `cmd …`, `shot …`, `state`, `collapsestate` (scenes, drawn fragments, render ms, fps).
- Build towers with `/fill … stone_bricks hollow` on a stone plinth (one `/fill` ≤ 32768 blocks), stand on a
  glass pad (creative players fall after `/tp`). Superflat spawns slimes: set peaceful first.
- Dedicated server + client: copy the client argv from `/proc/<pid>/cmdline`, see `.memory-bank/chitauri.md`.
  Never `pkill -f` a pattern that also matches your own shell command (use `pgrep -f "[f]orgeclientuserdev"`).
- Measured (llvmpipe, not a GPU): one 966-block topple at 34 blocks: 48–52 fps → 25–34 while falling, 15–21 in
  the dust, fragments 2.8–3.4 ms/frame; 3 collapses at once: 33–40 → 5–8 fps; at 120 blocks 37 → 29–40 fps.
  Server: TPS 20.0 in every run, analysis 3.7–15 ms (first one after start 25–45 ms, cold JIT), worst tick 28–62 ms.
