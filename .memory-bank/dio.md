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

## DIO polish (branch feat/dio-polish, core 1.55.0, protocol 14)
- Stopped time is a shared seam: `hero/control/TimeFreeze` (Source registry, `isFrozen`, `lockedView`). DioTimeStop
  registers one source. Mixins skip the tick of frozen entities instead of editing their state:
  `TimeFreezeLevelMixin` (ServerLevel.tickNonPassenger/tickPassenger: mobs, Jin-Woo shadows, projectiles, TNT, items,
  hero entities), `TimeFreezePlayerMixin` (Player.tick of a frozen ServerPlayer: hero runtime, cooldowns, effects),
  `TimeFreezeMoveMixin` (server keeps the saved yaw/pitch), client `TimeFreezeClientLevelMixin` /
  `TimeFreezeRenderMixin` (no interpolation) / `TimeFreezeMouseMixin` + `client/hero/TimeFreezeView` (camera lock).
  A new hero gets frozen for free; it only needs code if it runs level-wide managers outside entity ticks.
- Saved motion (`TimeStopField.saved`) comes back at the end, then the ledger knockback is added. Dead entities are
  never frozen; respawn/rejoin = new entity id, pruned at the next rescan. Client watchdog: no field packet for
  60 ticks ends the field locally.
- Not frozen by design / known: level-wide scene managers (Thor scenes, Mjolnir events, Reinhard World Cut / Saw
  Sphere) tick outside entities; Thor is off-limits and Reinhard scenes would desync. Entities outside the radius
  are untouched (local field).
- ROAD ROLLER is 180 ticks (`DioRules` ROLLER_*): capture 10, crouch 14, leap 26, roller appears 46, apex 60, hang
  70, slam 96, 13 pulses to 148, resume 160, DIO lands 174. Local camera: pure `RollerCamera` (shots, blends,
  letterbox) applied by `RollerCinema` through `DioSceneCameraMixin`; the mouse is locked while it runs.
- Debug: `/dio blood fill [targets]`, `/dio blood infinite on|off [targets]` (op 2, `DioCommands`); infinite blood is
  `DioState.infiniteBlood` (NBT, kept on respawn) and snapshot flag `FLAG_INFINITE_BLOOD` (HUD "∞").
- QA: `-Dviltrumitecore.dio.qa.polish=true` runs the polish scenario (`/dioqa foreignstop <ticks>` = a FakePlayer
  DIO freezing the QA player, `/dioqa shadow`, `/dioqa probe <label>` logs positions/motions/frozen flags).
- Victim lifecycle: `TimeStopEvents` (LivingDeathEvent LOWEST / PlayerRespawnEvent → `DioTimeStop.releaseVictim(e, true)`
  = drop every record + ControlManager release + `TimeStopField.exempt`; PlayerChangedDimensionEvent → release only).
  A respawned ServerPlayer keeps UUID **and** entity id, so stale records would re-hold him without this.
- Absolute damage (`BYPASSES_INVULNERABILITY`: /kill, void) is never banked by the control queue
  (`HeroDamage.queuesUnderControl`); before, the 40 % deferred cap made a frozen victim unkillable.
- Forge `FakePlayer.canHarmPlayer()` returns false → a FakePlayer DIO exempts players; QA `foreignstop` overrides it.
- While `RollerCinema.active()` the vanilla HUD overlays, `ViltrumiteInGameHudMixin` panel and DioHud are hidden;
  `HeroSubtitles` moves the speech panel into the bottom letterbox.
- Sounds: `DioSounds.playWide` (explicit-range broadcast) for stop/resume + roller cues; `client/dio/TimeStopClock`
  loops `time_tick` while the camera is inside a running field. Synth `tools/sfx/dio.py` has per-cue `LOUDNESS` targets.
- **Oversized shared files**: every base locale (65–95 KB) and `sounds.json` (68 KB) exceed the 64 KB GitHub MCP
  request cap. DIO strings live in `src/main/lang-overlays/dio/<locale>.json` (all 12 locales); reworked/new DIO
  sound events live in `src/main/sound-overlays/dio/sounds.json` (whole events, merged by processResources; tests
  read it via `integration/ShippedSounds`; `check_hero_resources.py` merges it too). Never edit the base files.
