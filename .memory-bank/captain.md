# Memory bank — Captain America (Steve Rogers, 2012) — branch feat/captain-america-2012

- Design: `docs/design/2026-11-03-captain-america-design.md`. Core **1.57.1** (polish, no packet change), protocol **17**.
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

## Сессия 2026-11-03, продолжение (1.57.1)
- Големы/нейтралы: `AvengerEntity.mayHit` щадит жителей всегда, `AbstractGolem` — пока тот не «повернулся против команды»
  (`turnedOnTeam`: цель — Стив/союзник, NeutralMob злится на Стива, недавно бил Стива, метка Стива). `AvengerBrain.hostile()`
  = Enemy || turnedOnTeam; чистая функция `AvengerBrain.hostileToTeam`, тест `AvengerGolemRuleTest`. В игре: злой голем
  убит за ~3 с, мирный не тронут.
- Кинематограф ультимейта (только у самого Стива): `client/captain/CaptainSceneShots` (чистые кадры CALL/WAIT/RAISE/RETURN,
  тест `CaptainSceneShotsTest`) + `CaptainUltScene` (камера-маркер, леттербокс 11% с красно-бело-синими линиями, HUD/рука/
  свой ник скрыты, FOV 58, камера не уходит в стены, отпускается при любой потере). Субтитры переезжают в нижнюю полосу.
- Звуки Халка (`hulk_{roar,punch,smash,chest}`) сведены из CC0-записей Freesound (через Openverse): `tools/sfx/hulk_cc0.py`;
  `tools/sfx/captain.py` их не перезаписывает; авторы — `CREDITS.md`, `tools/assets/captain_sources.md`.
- Текстуры (`tools/assets/avengers_model.py`, запускать с `PYTHONHASHSEED=0` — иначе меняется шум всех скинов):
  Мьёльнир как в MCU (`mjolnir()`, гранёный боёк, кельтский узел, кожаная рукоять, ремешок), волосы прядями `strands()`
  (Тор, Вдова — заново, Халк, Хокай), мускулатура Халка (грудные, пресс, лопатки, вены).
- Проверка в игре (Xvfb): ультимейт с камерой, новые модели/текстуры, ПКМ с желанием открывает окно желаний.

## Доработка 1.60.0 / протокол 18 (ветка integ/captain-america-main → main)
- Мстители рисуются своими моделями: `AvengerModels` (Тор, Вдова, Хокай — свои geo 128×256; Халк на 15 % выше, хитбокс
  тоже); Железный человек — Mark 42 героя Sind (`tools/assets/avengers_ironman_mk42.py`: части Mark 42 пересажены на
  кости рига Мстителя, UV — точная развёртка GeckoLib, зеркало запечено; забрало показывает Тони). Старые
  `iron_man_gear*.png` удалены. `geo_lint.py`: опция `reused` — перекрытия заимствованной модели как предупреждения.
- Щит (`captain_model.py --shield-only`): выпуклый купол — 6 наклонных колец по 24 сектора, скруглённая кромка, звезда
  на диске, задняя пластина с ремнями. Запуск без флага пересобирает тело/снаряжение и НЕ воспроизводит текущие —
  голову правит пост-проход `tools/assets/captain_head.py` (v3, от блобов коммита BASE 9f15f6c0): голова 7,5×8×7,5,
  лицо в стиле Рейнхарда 15×16 тексела (2 px/ед.), шлем шириной 8,0 начинается с y29 (над бровями), восьмигранная
  макушка, ступенчатые нащёчники |x| 3,25/3,0/2,5, тонкий ремешок. Причина старого «плохого лица»: шлем с уровня глаз,
  нащёчники по 1,3, толстый ремень — видно ~11×8 текселей, рот читался как усы, рельеф давал тёмные точки.
- Пальцы удалены у всех (капитан, руки капитана, мстители, Тор, Вдова, Хокай, Халк): `tools/assets/remove_fingers.py`,
  вызывается из `captain_head.py`. Генераторы (`captain_model.py` без флага, avengers_*.py) вернут пальцы — после них
  обязательно прогнать `remove_fingers.py`.
- Рикошет: цепочка 5 (заряженный 8) отскоков, мягкий доводчик (конус 135°, 10 бл., ±6 по высоте, поводок к Стиву,
  прямая видимость), 20°/тик, пропуск пролетевших целей (`CaptainRulesTest`).
- ИИ: жёсткий фильтр достижимости целей (видимость/высота/дистанция, бросить через 2 с) и бюджет урона команды —
  железный голем живёт ≈16 с против всей команды (`TeamDamage`, тесты `TeamDamageTest`, `AvengerReachTest`; в логе `TEAMKILL`).
- Совместные приёмы `TeamMoves`/`TeamRules`: Вдова + Хокай (прыжок с плеч, залп стрел 10/22/34/46 т.), Тор + Железный
  человек (разряд в репульсор, усиленный луч), Тор + Капитан (удар молотом по щиту, ударная волна + оглушение);
  `/hero team <vault|beam|shield>` для проверки. Строки и субтитры на 12 локалей (`captain_lang.py`).
- Кат-сцена: крупные планы Наташи (тик 92) и Клинта (тик 116) при высадке, 24 тика, смешивание 8 тиков
  (`CaptainSceneShots.closeUp`, тест); камера крупного плана спереди по `yHeadRot` (Наташа 38° слева, Клинт 50°
  справа — иначе рука с луком закрывает лицо); приветствие Стива при входе в героя (`CaptainLines.ENTER`).
- Места команды в бою (`CaptainRules.followBearing`): 60/145/265/215/100° от взгляда Стива — не впереди и не на линии
  камеры от 3-го лица (раньше Халк стоял в 4 блоках прямо перед игроком после ульты). В игре: голем 16,6 / 16,0 /
  16,25 с (`TEAMKILL` теперь пишется на любой смерти цели, которую била команда). Предмет в руке (напр. гнилая плоть с
  зомби) отдаёт ПКМ предмету — щит не поднимается; для тестов `gamerule doMobLoot false`.
- Анимации: волосы/плащ Тора в полёте, перекат стопы Халка, `spin()` ≤180° на ключ.
- Пуш без git-учётки: GitHub MCP `push_files`, лимит запроса 65 536 байт (thor.animation.json ≈64,7 КБ — клипы Тора
  держать компактными). `viltrumitecore/logs/*.gz` в индексе — тесты их меняют, не коммитить.
