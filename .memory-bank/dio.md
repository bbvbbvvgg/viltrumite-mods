# Memory bank — DIO / The World and push limits

## DIO / The World (branch feat/hero-dio, wave base feat/heroes-wave-base)
- Hero `HeroId.DIO` (ordinal after REINHARD), spec `docs/design/2026-10-28-dio-design.md`, numbers in `DioRules`.
  Server: `hero/dio/` (DioHero, DioCombat, DioTimeStop + TimeStopField with a deferred hit ledger, DioKnifeEntity,
  DioAbilitiesRuntime). Client: `client/dio/` (TheWorldRenderer + pure StandMotion/TheWorldClips, TimeStopClient +
  pure TimeStopScreen, RoadRollerScene + pure RollerChoreo, DioFxClient own pixel motes, DioPoser, DioHud).
- Stand clips: `animations/dio/the_world_*.animation.json`; strikes reach their furthest key exactly on the server
  hit tick (TheWorldClipsTest). First person draws only the stand's arms and only for strikes/guard/roller barrage.
- Time-stop screen grade = uniforms TimeStopGrey/Invert/Ripple in the shared `dash_impact` post pass
  (GameRendererDashMixin → `TimeStopClient.screen`).
- Lang overlays: `viltrumitecore/src/main/lang-overlays/<part>/<locale>.json` is merged into
  `assets/viltrumitecore/lang/<locale>.json` by `processResources`; the checker and DioStateTest merge it too.
  Reason: one GitHub MCP request is capped at 65536 bytes and `ru_ru.json` (~62 KB, Cyrillic = 2 bytes/char plus
  JSON escaping) no longer fits. Put new keys for an oversized locale into an overlay, never compact the base file.
- Opt-in QA: server `/dioqa become|act <action> [pressed]|dummy [distance]|blood|reset|state`
  (`-Dviltrumitecore.dio.qa=true`); client script `client/dio/qa/DioQaScript` (`-Dviltrumitecore.dio.qa.script=true`)
  creates a flat world, plays every ability, saves `run/screenshots/dioqa_*.png` and quits. Pass both flags via
  `JAVA_TOOL_OPTIONS` to `./gradlew :viltrumitecore:runClient --no-daemon` under Xvfb + llvmpipe.
- Software rendering runs several ticks per frame: grab screenshots after the frame (RenderTickEvent END), not at
  tick end, or consecutive shots repeat one stale frame.
- `SESSION.md` (~79 KB) and `.memory-bank/project.md` (~62 KB) are past the 65536-byte GitHub MCP request cap: a session that can push only through MCP
  writes its Russian entry to `docs/reports/<date>-<topic>.md` and says so; append it to SESSION.md via git later.
