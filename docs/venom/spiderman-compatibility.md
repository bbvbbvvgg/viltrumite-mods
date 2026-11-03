# Совместимость Venom ↔ Человек-паук из Falbiks Heroes

Ветка `feat/venom-living-symbiote`, core 1.71.0. Документ описывает, что найдено в JAR друга, как с ним
работает слой `SpiderCompat` и что сломается, если друг поменяет свой мод.

JAR не меняется, не перепаковывается и не попадает в репозиторий. Мы читаем его только через рефлексию
в рантайме. В тестах используются заглушки с такими же сигнатурами.

## 1. Что изучено

| Поле | Значение |
|---|---|
| Файл | `falbiksheroes-PRE-BETA.jar` (прислал друг) |
| SHA-256 | `75d793e38f41081f0ce1a7d6dd9f3864c5e598355f7444252f47b8d4cf48b90a` |
| modId / версия | `falbiksheroes` / `0.1.0` (`Implementation-Version`), сборка `2026-10-31T21:45:34Z` |
| Автор / лицензия | `stravberry123 (Falbiks)`, «All Rights Reserved» → код и ассеты не копируем |
| Платформа | Forge `[47,)`, Minecraft `[1.20.1,1.21)`, Java 17 |
| Имена | члены Minecraft в SRG (`m_20182_`), свои классы и члены — обычные (`SpiderState.swinging`) |
| Миксины | `falbiksheroes.mixins.json`: `BlockEntity`, `LevelChunk`, `ServerLevel`, `Player.attack/sweepAttack`, `ServerPlayer` (крит), `LivingEntity` (смерть); клиент: `HumanoidModel.setupAnim`, `AbstractClientPlayer.getSkinTextureLocation/getModelName`, `ClientLevel`, `LivingEntity` (poof) |
| Связь с нами | в `mods.toml` необязательная зависимость `viltrumitecore [1.44.0,)`, `ordering="AFTER"`; мост `dev.falbiks.heroes.compat.Viltrumite` работает через рефлексию |

Как изучали: распаковали JAR, декомпилировали Vineflower и прочитали `hero/*`, `heroes/spider/*`,
`net/*`, `effect/FHEffects`, `compat/*`, `client/render/geo/HeroBodyRender`. Байткод не меняли.

## 2. Механизмы Человека-паука (`falbiksheroes:spider`)

`dev.falbiks.heroes.heroes.spider.SpiderHero`, уровень 3, своё состояние `SpiderState extends HeroState`.

| Слот | Способность | Как работает | Кулдаун |
|---|---|---|---|
| `ABILITY_1` | Swing (качание) | удержание; луч до якоря на 36 блоках; сервер (`tickSwing`) обрывает нить, если исчез блок якоря, игрок приземлился, прошло 160 т или нить растянута больше `rope + 8`; физику качания считает **клиент** | 6 т |
| `ABILITY_2` | Net (сеть) | снаряд `SpiderWeb` вида `NET` → `netBurst`: кокон на 50 т | 160 т |
| `ABILITY_3` | Yank (рывок) | 26 блоков; тянет цель, если `Viltrumite.allowsControl(цель,"PULL")`, иначе тянет самого паука | 120 т |
| `ULTIMATE` | Storm | 34 т, до 10 разрядов паутины и кокон на 80 т | 1400 т |
| `SUMMON` | Flip | прыжок от стены или сальто назад; нужна опора или качание | 50 т |
| ЛКМ | удар / выстрел | комбо из 3 ударов (дальность 3.2, урон 4/4/6.5, откидывание при `IMPULSE`) или паутинные пули (обойма 10, перезарядка 40 т) | — |

Пассивки: автоуклонение в `blocksAttack` (раз в 240 т, окно 6 т), паучье чутьё (снаряды на курсе
перехвата, мобы, которые целятся в паука), нет урона от падения во время качания и на стене.

Важные детали для взаимодействия:

- **Состояние качания** — публичные поля `SpiderState`: `swinging`, `ax/ay/az` (якорь), `rope`,
  `swingClock`, `swingGame`, `noFallUntil`, `revision`. После изменения сервер сравнивает `revision`
  и отправляет `HeroSyncPacket`; клиент паука видит `swing=false` и отпускает нить (`letGo`).
- `static void endSwing(ServerPlayer, SpiderState, boolean)` — **package-private**. Официально обрезать
  нить снаружи нельзя.
- `public static Vec3 hand(Player)` — точка руки, откуда идёт нить. `anchor(Player)` — это **поиск нового**
  якоря лучом, а не текущий якорь.
- **Снаряд паутины** `SpiderWeb extends Projectile`, тип сущности `falbiksheroes:spider_web`,
  вид в synched data (`PELLET=0`, `NET=1`), `public int kind()`, без гравитации (сеть слегка падает), живёт
  26/40 т, NBT-ключи `kind` и `life`. Перед попаданием вызывает `ForgeEventFactory.onProjectileImpact`,
  поэтому `ProjectileImpactEvent` (в Forge 47 он `@Cancelable`) позволяет перехватить снаряд.
- **Метки на целях** (persistent data): `falbiksheroes_spider_web` `{n, until}` (стаки липкости),
  `falbiksheroes_spider_cocoon` (до какого тика кокон), `falbiksheroes_pulled`. Публичные
  `SpiderHero.cocooned/cocoon/webStacks`.
- **Оглушение** `falbiksheroes:stun` (MobEffect). Для игрока клиент блокирует движение и рисует оверлей
  «stunned», а `HeroManager.disabled()` запрещает нажатия.
- **Ввод**: канал `falbiksheroes:main` (протокол `"4"`), `AbilityInputPacket(slot, down, move)` →
  `HeroManager.handleInput(ServerPlayer, AbilitySlot, boolean)` / `handlePrimary(ServerPlayer, boolean)`.
  Оба метода публичные и сами проверяют кулдауны, занятость и `disabled`.
- **Рендер**: `HeroBodyRender` добавляет свой `BodyLayer` первым слоем рендера игрока и оборачивает
  слои брони и предметов в `Gate`. Модель паука — `spider_body.geo.json` (кости `head/body/arms/legs`).
  Нити рисуются в `RenderLevelStageEvent`.
- Публичных событий Forge, IMC и стабильного API у мода нет. Всё, что ниже, держится на его
  внутренних именах.

## 3. Как мод друга использует нас (это нельзя ломать)

`compat/Viltrumite.java` ищет по имени:

- `hero.HeroId` (`key()`, `fromKey`), `hero.HeroPlayer.getHeroId()`, `viltrumitecore$setHeroId`,
  `viltrumitecore$setSyncedSnapshot`, `HeroPublicSnapshot.EMPTY` и его record-компоненты;
- `HeroRegistry.changeHero(ServerPlayer, HeroId)`, `HeroRegistry.get(Player)`,
  `HeroRegistry.allowsExternalControl(LivingEntity, ControlKind)` (ищет `ControlKind` по имени `PULL`,
  `FREEZE`, `IMPULSE`), `HeroDamage.isAnchored(LivingEntity)`;
- `HeroDefinition.isDivine`, `isAbsolutelyInvulnerable`, `saveHeroState/loadHeroState` (перемотка
  времени Мандома), `allowsFlight`.

`ExternalHero.registerAll` добавляет каждый наш `HeroId` (кроме `human`) в их список героев как
`viltrumitecore:<key>` с названием из `hero.viltrumitecore.<key>`. Поэтому Venom появится у них сам.

Что мы сделали, чтобы мост не сломался:

- `HeroId.VENOM` и `ControlKind.POSSESS` добавлены **в конец** enum'ов. Старые имена и порядковые
  номера не изменились.
- Сигнатуры, которые они читают, не менялись. `HeroRosterIntegrationTest` и `SpiderCompatTest` проверяют их отражением.
- Ключ `hero.viltrumitecore.venom` есть во всех 12 локалях.
- `saveHeroState/loadHeroState` для Venom сохраняют только форму, голод и кулдауны. Вселение там
  **никогда** не сохраняется (§6), поэтому перемотка Мандома не может «вернуть» вселение.

## 4. Слой `SpiderCompat`

Пакет `dev.baranhan.viltrumitecore.compat.spider`. Ни одного import'а классов Falbiks, ни одного миксина
в их классы, ни одной ссылки на их классы в сигнатурах.

```
SpiderCompat (фасад, единственная точка входа)
 ├─ SpiderFeature        enum возможностей (стабильный контракт для нашего кода)
 ├─ SpiderBridge         интерфейс адаптера (стабильный контракт)
 ├─ FalbiksSpiderV0      адаптер для falbiksheroes 0.1.x (рефлексия)
 ├─ NoSpiderBridge       null-объект: мода нет или адаптер не подошёл
 └─ ReflectionProbe      поиск классов и членов с проверкой типов и журналом пропусков
```

### 4.1 Обнаружение

1. `ModList.isLoaded("falbiksheroes")` и версия из `IModInfo`. Если мода нет, сразу `NoSpiderBridge`,
   классы Falbiks даже не загружаются.
2. Из списка адаптеров (сейчас один, `FalbiksSpiderV0`, диапазон `[0.1,0.2)`) выбирается подходящий
   по версии. Если версия неизвестна, всё равно пробуем последний адаптер, но в режиме «проверить всё»,
   и сообщаем об этом в лог.
3. Адаптер через `ReflectionProbe` ищет **каждый** член с точными типами параметров и результата
   (`Class.forName(name, false, loader)`, статические инициализаторы не запускаются). Найденное
   включает соответствующую `SpiderFeature`. Не найденное отключает только эту возможность и пишет
   одну строку `WARN`:

```
[SpiderCompat] falbiksheroes 0.1.0 → adapter falbiks-v0: 9/10 features
[SpiderCompat]   CUT_LINE: fallback (endSwing(ServerPlayer,SpiderState,boolean) missing) → field write
[SpiderCompat]   DRIVE_ABILITIES: disabled (HeroManager.handleInput(ServerPlayer,AbilitySlot,boolean) missing)
```

4. Любое исключение во время вызова (`LinkageError`, `ReflectiveOperationException`, `ClassCastException`,
   `RuntimeException` из их кода) отключает **эту** возможность до перезапуска и пишет один `ERROR` со
   стеком. Повторно не спамит. Venom продолжает работать в базовом режиме.
5. Конфиг `compat.spider.enabled=false` выключает слой целиком.
6. Команда `/viltrumite compat spider` печатает версию, адаптер и состояние каждой возможности.

### 4.2 Возможности (`SpiderFeature`)

| Возможность | Что даёт Venom | Чем пользуется адаптер | Запасной путь |
|---|---|---|---|
| `DETECT_HERO` | понять, что игрок — паук | `HeroData.get(Player).heroId()` = `falbiksheroes:spider` | нет → паук считается обычным игроком |
| `LINE_STATE` | видеть нить: рука → якорь, длина | поля `SpiderState.swinging/ax/ay/az/rope` + `SpiderHero.hand(Player)` | рука = глаза − 0.35 |
| `CUT_LINE` | перерубить нить когтем или щупальцем | `SpiderHero.endSwing(sp, state, true)` (setAccessible) | запись полей `swinging=false`, `swingGame`, `noFallUntil`, `revision++`, `startCooldown("swing",6)` |
| `WEB_PROJECTILE` | ловить, есть и отбрасывать паутину | тип `falbiksheroes:spider_web` из реестра + `kind()` | без `kind()` вид = `PELLET` |
| `WEB_STATUS` | видеть стаки липкости и кокон | persistent data `falbiksheroes_spider_web/_cocoon` | — |
| `TEAR_WEB` | сорвать паутину и кокон | удаление этих ключей + снятие `falbiksheroes:stun` по реестру эффектов | — |
| `INPUT_RELEASE` | при вселении отпустить зажатые кнопки паука | `HeroManager.handleInput(sp, slot, false)` для всех слотов, `handlePrimary(sp,false)` | — |
| `DRIVE_ABILITIES` | управлять способностями паука во время вселения | `HeroManager.handleInput/handlePrimary` | базовый режим: только движение и наши удары |
| `INPUT_GATE` | паук-жертва не жмёт свои способности сам | серверный фильтр Netty для канала `falbiksheroes:main` от жертвы | клиентский фильтр их `KeyMapping` |
| `WALL_STATE` | видеть, что паук на стене | `SpiderHero.wallNormal(Player)` | луч по сторонам |

`NoSpiderBridge` отвечает `false/empty` на всё. Код Venom **всегда** проверяет возможность, прежде
чем её использовать (`SpiderCompat.has(LINE_STATE)`), и имеет поведение без неё.

### 4.3 Что гарантирует стабильный API

Стабильны и покрыты тестами **наши** контракты:

- `SpiderFeature`: новые значения только в конец, старые не удаляются.
- Методы `SpiderCompat` (`isSpider`, `line`, `cutLine`, `webKind`, `tearWeb`, `releaseInputs`, `drive`,
  `gate`, `diagnostics`): никогда не бросают исключения, никогда не возвращают `null`, без мода дают
  нейтральный ответ.
- `SpiderLine` (record: рука, якорь, длина) и `WebKind` (`PELLET`, `NET`, `UNKNOWN`) — наши типы.
- Отключённая возможность всегда даёт базовое поведение Venom (§5).

**Не** гарантировано и требует нового адаптера, если друг это поменяет:

- переименование или перенос пакетов `dev.falbiks.heroes.*`;
- изменение `HeroData.get/heroId/state`, полей `SpiderState`, `SpiderHero.hand/endSwing/wallNormal`,
  `HeroManager.handleInput/handlePrimary`, enum `AbilitySlot`;
- переименование типа `spider_web`, ключей persistent data, эффекта `stun`, канала `falbiksheroes:main`;
- перенос физики качания с клиента на сервер (нужно будет обрезать нить по-другому);
- появление у них настоящего API или событий: тогда новый адаптер должен перейти на него.

Как добавить адаптер: новый класс `FalbiksSpiderV1 implements SpiderBridge` с диапазоном версий,
регистрация в `SpiderCompat.ADAPTERS` перед `V0`, заглушки новой версии в
`SpiderCompatFixtures` и тест «текущий JAR» с новым SHA. Старые адаптеры не трогать.

### 4.4 Тесты (`SpiderCompatTest`)

- **JAR нет**: пустой загрузчик классов → `NoSpiderBridge`, все возможности выключены, вызовы не бросают.
- **Текущий JAR (0.1.0)**: в тестовом загрузчике компилируются заглушки с теми же именами и сигнатурами,
  что в JAR → все 10 возможностей включены, обрезание нити меняет поля и `revision`, ввод уходит в
  `HeroManager`.
- **Нет членов / члены изменились**: заглушки без `endSwing` (обрезание идёт через поля), с другим
  типом `rope`, без `HeroManager.handleInput`, без `HeroData`, с методом, который бросает исключение →
  отключается ровно нужная возможность, остальные работают, диагностика называет пропавший член.
- **Сверка с настоящим JAR**: `FalbiksJarSignatureTest` запускается, если задан `-Dfalbiks.jar=<путь>`.
  Он загружает настоящие классы в изолированный загрузчик (без инициализации) и проверяет, что адаптер
  находит все члены. В CI JAR нет, тест пропускается и пишет причину. Локальный прогон описан в отчёте.

## 5. Venom без Falbiks

Мода нет → `NoSpiderBridge`. Venom целиком работает: форма, трансформация, щупальца, когти, панцирь,
регенерация, слабости, ярость, захват носителя (жертва — любой игрок, если разрешено конфигом),
босс-NPC. Пропадают только реакции на паутину и нити паука.

## 6. Интеграция Venom ↔ Паук (настоящие механики)

| Механика | Как работает | Возможности |
|---|---|---|
| Перерубить нить | взмах когтя или щупальца проверяет пересечение дуги удара с отрезком «рука → якорь» (сервер, капсула 0.6) → `cutLine` → паук падает, звук «рвущейся нити» | `LINE_STATE`, `CUT_LINE` |
| Взобраться по нити | Venom цепляется щупальцем за нить паука и по отрезку подтягивается к якорю (или дёргает паука к себе) | `LINE_STATE` |
| Перехват паутины | `ProjectileImpactEvent`: пуля в Venom → симбиот её «съедает» (+голод, без урона); в панцире или в замахе щупальца паутина ловится в полёте и летит обратно в паука (владелец меняется на Venom, `life` сбрасывается через NBT) | `WEB_PROJECTILE` |
| Сеть и кокон | кокон на Venom держится 15 т вместо 50: симбиот прорывает его (`tearWeb`) и становится злее | `WEB_STATUS`, `TEAR_WEB` |
| Контр-захваты | захват щупальцем паука, который качается или сидит на стене, срывает его (`cutLine`) и даёт бросок; паук, пойманный в кокон, получает от Venom «Объятие» с усиленным уроном | `LINE_STATE`, `WALL_STATE`, `WEB_STATUS` |
| Схватки в воздухе и на фасаде | если оба в воздухе или на стене ближе 3 блоков, удар Venom включает «клинч»: оба зависают на 8 т, обмен ударами, обоих отбрасывает в стороны | `DETECT_HERO`, `LINE_STATE`, `WALL_STATE` |
| Реакция симбиота | рядом с пауком тело Venom «тянется» к нему (клиент), эмблема пульсирует, звучит шёпот; паучье чутьё не видит захват щупальцем, потому что захват не наносит урон | `DETECT_HERO` |
| Вселение в паука | Venom ведёт паука: качание, рывок, сеть через `drive`; если `DRIVE_ABILITIES` нет — базовый режим: только движение, прыжки и удары симбиота | `INPUT_RELEASE`, `DRIVE_ABILITIES`, `INPUT_GATE` |

Автоуклонение паука честно срабатывает против когтей Venom: это урон, и мы его не обходим.

## 7. Риски

| Риск | Вероятность | Что будет | Мера |
|---|---|---|---|
| Друг переименует внутренности | высокая (PRE-BETA) | возможность отключится | адаптер проверяет каждый член, лог называет пропажу, Venom в базовом режиме |
| `endSwing` станет private или сменит сигнатуру | средняя | обрезание через поля | запасной путь + тест «изменённые члены» |
| Физика качания уйдёт на сервер | средняя | обрезание сработает, но паук на миг «дёрнется» | новый адаптер |
| Их миксин в `HumanoidModel.setupAnim` и `BodyLayer` поверх модели игрока | есть | наша «шуба» симбиота на захваченном пауке может перекрыть их тело | рисуем шубу своим слоем после всех слоёв и «раздуваем» модель; работает с любым телом |
| Канал `falbiksheroes:main` переименуют | низкая | фильтр ввода не сработает | клиентский фильтр, предупреждение в логе |
| Их `Targets.enemy` при сменённом владельце паутины | средняя | отброшенная паутина может не попасть | при отказе просто уничтожаем снаряд |
| Рефлексия на чужом классе ломает загрузку | низкая | — | `Class.forName(..., false, ...)`, всё в `try/catch(LinkageError)` |
| Мандом перематывает время во время вселения | средняя | вселение могло бы «вернуться» | вселение не попадает в `saveHeroState`, перемотка снимает его |
| Лицензия «All Rights Reserved» | — | — | ни строчки их кода и ни одного ассета; только имена для рефлексии |
