# Memory bank — Captain America (Steve Rogers, 2012) — branch feat/captain-america-2012

- Design: `docs/design/2026-11-03-captain-america-design.md`. Core **1.57.0**, protocol **17**.
- Order: `HeroId.CAPTAIN` = 10 (after Jinwoo); `HeroAction` CAPTAIN_THROW..CAPTAIN_ASSEMBLE (5, contiguous, last);
  `CaptainFxS2CPacket` appended last (`HeroRosterIntegrationTest` pins all three).
- Server: `hero/captain/*` (rules, combat, skills, flags, sounds, lines), `hero/captain/assemble/AssembleScene` (ultimate
  timeline, SafeSpot circle 3.4 → 2.2, sky/no-sky fallbacks, deadlines), entities `entity/captain/ThrownShieldEntity`,
  `entity/avengers/{AvengerEntity,AvengerBrain,AvengerKind,AvengerProjectileEntity,QuinjetEntity,AvengersTeam}`.
  Allies' AI is off until the circle ends (PHASE_*), they are invulnerable during the ceremony.
- Client: `client/captain/*` — `CaptainRenderer` (TP body/gear/glow + shield on bone `left_shield` via BoneHook; FP arms),
  `CaptainPose` (stances + block overlay + move clip), `CaptainVfx`/`CaptainFxClient`/`CaptainMotion`, `AvengerRenderer`
  (rigs avenger/avenger_slim/hulk, props), `QuinjetRenderer`, `ThrownShieldRenderer`, `AvengerProjectileRenderer`,
  `CaptainPanelStyle` (kinetic bar, shield-away marker, team timer), `CaptainPortrait`, `CaptainHeadPreview`.
  Entry: `CaptainClient.init()` from `ViltrumiteCoreClient`; renderers in `CaptainClientSetup` (mod bus).
- Assets are generated, never hand-edited: `tools/assets/captain_model.py`, `avengers_model.py`, `captain_clips.py`,
  `captain_fp_clips.py`, `avengers_clips.py`, `captain_icons.py`, `captain_lang.py`; sounds `tools/sfx/captain.py --package`.
- Strings: `src/main/lang-overlays/captain/<12 locales>.json` (76 keys, generated); wish strings in `od_lagna/*`
  (non-RU locales keep the wish phrase in English, like every other wish). Sounds: `src/main/sound-overlays/captain.json`
  (52 events). Speech lines are text only (`CaptainLines`), no synthesised voice.
- Key names on the ability panel/screen are English on every language since 1.57.0 (`KeyLabels.english`), for all heroes.
- Wish: `OdLagnaWishes` "shield" → captain, `WishStyle.SHIELD` (blue tint, `captain.charged` → `captain.parry`), motif
  `WishSceneFx.shield` (turning star shield of pixels, star flash, ground ring).
- Tight spaces: `FormationPlanner.plan` falls back to `SafeSpot` with `SeenProbe` (line of sight to Steve's chest) before
  the blind search, so a sealed room keeps the team inside; Hulk (3.1 high) cannot fit under a 3-block ceiling and
  stands outside. Verified in game (5×3×5 stone box) on 2026-11-03.

## Сессия 2026-11-03 (запись для SESSION.md)
SESSION.md (~250 КБ) не обновлён: инструмент пуша принимает запросы до 64 КБ. Запись сессии:
- Ветка feat/captain-america-2012 (от db636a40), продолжение: клиент (рендеры щита/Мстителей/снарядов/квинджета, панель `CaptainPanelStyle`, портрет, превью головы, VFX), иконки способностей, 52 собственных звука (`tools/sfx/captain.py`, без нарезок из фильма), строки на 12 локалей, желание Од Лагуны «Щит», имена клавиш в окне способностей всегда на английском (`KeyLabels.english`, для всех героев), версия core 1.57.0.
- Исправлено по итогам игры: в замкнутой комнате запасные точки круга (`FormationPlanner.plan`) сначала ищутся в прямой видимости Стива (`SeenProbe`), иначе союзники вставали за стенами.
- Проверки: полный `./gradlew build --offline` — BUILD SUCCESSFUL, 2248 тестов, 0 падений, 1 пропущен; `check_hero_resources.py --hero captain` — 0 новых проблем.
- В игре (выделенный сервер + 1 клиент по сети, Xvfb, программный рендер, звук на устройстве No Output): выбор через предмет-желание, все способности, ультимейт в поле (квинджет, 5 прибытий, круг, бой 60 с, уход) и в каменной коробке 5×3×5 без неба (без квинджета, четверо в комнате, Халк 3,1 бл не помещается под потолок 3 бл и стоит снаружи). Сервер держит 20 TPS (проверено по gametime). Второй клиент не запускался: два клиента + сервер не помещаются в 4 ГБ (OOM). Звуки на слух не прослушаны.
