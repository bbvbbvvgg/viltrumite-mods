# Интеграция всех героев: #82 + #74 → `integration/all-heroes-final`

Дата: 2026-10-29. Ветка создана от головы #82 (`f41e49c5`), в неё обычным merge влит #74 (`feat/thor-hulkbuster-build`, `5cc4fe4e`) с сохранением истории. Без ours/theirs, без force-push; #74, #82 и их ветки не менялись, PR не закрывались, в main ничего не вливалось.

## 1. Как сделано слияние

1. **Подготовка от #82 (P1, коммиты `b8ce9cf0…7eb2b7a6`, 13 частей).** В 11 не-русских языковых файлах и в `sounds.json` блок #82 (140 ключей строк, 94 звуковых события: DIO / Sinclair / Jinwoo + субтитры/реплики) перенесён с хвоста файла в середину (lang — после ключа с индексом len-30; sounds — после `ironman_pulse_unibeam`). Те же ключи, те же значения. Цель — чтобы git не получал конфликт «оба дописали в конец» на 12 файлах. `project.md`: раздел «Core 1.50.0» поставлен перед «## Iron Man polish», добавлена ссылка на `heroes-integration.md`.
2. **P2 (`259d413d`, `e72be878`)** — заранее разрешённые общие «швы» (14 небольших файлов): CREDITS.md, build.gradle, ViltrumiteCore, ViltrumiteCoreClient, ClientModEvents, RaceSelectionScreen, ViltrumiteEntities, HeroAction, HeroId, HeroRegistry, CoreMessages, NoHeroBranchTest, ReinhardHeroTest, ReinhardReleaseTest.
3. **Вспомогательная ветка `integration/merge-helper-thor`** (от `feat/thor-hulkbuster-build` + те же 14 файлов: `2872b14b`, `af70903c`) → **PR #83**, влит методом merge → merge-коммит **`22b2fa4d`** (родители `e72be878` + `af70903c`). История #74 полностью сохранена; дерево совпадает с локальной симуляцией обычного `git merge` (`7e5fea1a`).
4. **После слияния:**
   - `227e0b61` fix(hero): `HeroPublicSnapshot` — маска действий 64-битная (действий стало 65; порядковые номера ≥64 доступны только в режиме «все» = -1L, `actionMask` для них бросает исключение) + `HeroPublicSnapshotTest`.
   - `198154d5` test(integration): `HeroRosterIntegrationTest` + `HeroIdMigrationTest`.
   - `3d0d4c64` fix(thor): русские строки Тора и Hulkbuster — `viltrumitecore/src/main/lang-overlays/thor/ru_ru.json` (155 ключей, плейсхолдеры проверены).
   - `c343b6f0` test(thor): хелпер `integration/ShippedLang.java` (ресурсы + lang-overlays); правки 4 тестов Тора (см. §8).
   - `196fc1cb` docs(memory): `.memory-bank/heroes-integration.md`.

## 2. Конфликты и как разрешены

Обычный `git merge` дал 27 конфликтных файлов. Принцип — объединение (union), ничего не выкинуто:

| Файл | Решение |
|---|---|
| HeroId | THOR сразу после REINHARD, затем DIO, SINCLAIR, JINWOO |
| HeroAction | 4 действия Тора сразу после DIAGONAL_WORLD_CUT, затем DIO / Sinclair / Jinwoo |
| HeroRegistry, ViltrumiteCore, ViltrumiteCoreClient, ClientModEvents | обе стороны, Тор первым |
| ViltrumiteEntities | MJOLNIR, DIO_KNIFE, SHADOW_SOLDIER |
| RaceSelectionScreen | 8 героев, become_thor после reinhard |
| CoreMessages | протокол "14"; пакеты ThorScene S2C/C2S, HulkbusterMotion C2S/S2C, ThorDashS2C перед HeroLineS2C, далее DioTime, DioFx, JinwooFx |
| build.gradle | версия 1.54.0, сохранён processResources с lang-overlays из #82 |
| CREDITS.md, project.md | объединение |
| 11 lang + sounds.json | объединение ключей (на GitHub конфликт снят переносом блока, §1.1) |
| NoHeroBranchTest | объединение шаблонов |
| ReinhardHeroTest | порядок 5..9 |
| ReinhardReleaseTest | версия по диапазону (как в #74) |

Автоматически слитые файлы, где менялись обе стороны, проверены вручную: HeldInputs (forcedRelease Тора + ActionLocks Sinclair), HeroInputC2SPacket (mouseOnly/anchored Тора + lock Sinclair), HeroDefinition (body/claims Тора + lines()/signatureAct() из #82), IronManAbilities (только комментарии #64).

## 3. Порядок героев, версия, протокол

THOR=6, DIO=7, SINCLAIR=8, JINWOO=9. Протокол сети **14**. Ядро **1.54.0** (flight 1.10.1). История версий: 1.46–1.49 / p8–10 — этапы Тора; 1.50.0 / 11 — субтитры; 1.52.1 / 12 — #74 отдельно; 1.53.0 / 13 — #82 отдельно; 1.54.0 / 14 — оба.

## 4. Автоматические проверки (чистая копия на `196fc1cb`)

- `./gradlew build --offline --no-daemon` — **BUILD SUCCESSFUL** (3 мин 14 с).
- core: **2166 тестов, 0 падений**, 1 пропущен (ThorClipPreviewTest — запускается только при переменной окружения); flight: 12 тестов, 0 падений.
- Jar: `reforgedcodex-core-1.54.0.jar`, `reforgedcodex-flight-1.10.1.jar`.
- `check_hero_resources.py`: 0 новых проблем, 6 известных старых.
- Проверка jar: 202 JSON валидны; 343 ogg, 250 png; 12 языков × 795 ключей, нет ни лишних, ни отсутствующих (ru с учётом overlays); sounds.json — 297 событий; только старые проблемы из main (нет `takeoff1.ogg`, 3 субтитра Viltrumite заданы литералом).
- Уникальность пакетов и порядок героев — тестами HeroRosterIntegrationTest / HeroIdMigrationTest / тестами CoreMessages.
- Безвредная строка лога: `Thor move table: client probe ABSENT ? refused` (DEBUG, раз в 5 с — это опрос статуса Forge, не клиент).

## 5. Проверка в игре (Forge 1.20.1, Xvfb, llvmpipe, **без звука**)

### Автоматически (скрипты)
- **Sinclair** (`-Dviltrumitecore.sinclair.playtest`): 143/143 шагов, 65 скриншотов, финал с субтитрами и портретом — без ошибок мода.
- **DIO** (`dio.qa` + `dio.qa.script`): «DIOQA done», 61 скриншот — комбо, шквал, ножи, каток, высасывание, остановка времени (серый экран), субтитр «There is no escape!» с портретом.

### Вручную (агент вводил клавиши/мышь через xdotool и смотрел скриншоты)
- **Выбор героя:** видны все 8 героев + «Остаться человеком». У Тора на превью лицо обычного скина игрока (так же в #74, косметика). Кнопка DIO подписана просто «DIO», у остальных «Become …» — не проверял, задумано ли так в #82.
- **Iron Man → Hulkbuster:** стр. 2, V — костюм онлайн → Y — капсула Veronica → меню → Hulkbuster Mk48 Deploy → «JARVIS: Hulkbuster online», HUD (Dash, Grab, Heavy repulsors, Seismic rift, Veronica cage, Jet ram).
  - Работает: ЛКМ комбо из 3 ударов по железному голему (замахи, вспышки, пыль, урон); ПКМ захват + 5 ЛКМ «отбойный молоток» (полоса Grip, обломки, перезарядка захвата); заряженный удар (держать ЛКМ ~2 с — огонь/взрыв, убил зомби); R тяжёлые репульсоры (заряд, энергия 100→65 %, перезарядка 13 с; луч сзади почти не виден); Y сейсмический разлом (удар, пыль, летящий блок, трещина, перезарядка 10 с); Z клетка Veronica (перезарядка 25 с, белая вспышка-куб и пыль в точке прицела — подтверждено частично).
  - **Найденные дефекты (код #74, слиянием не менялся):**
    1. **Jet Ram (ULTIMATE) недоступен** — B ничего не делает: ULTIMATE сопоставлен только с `HeroAction.SIGNATURE`, а ни одна клиентская клавиша его не шлёт (`IronManAbilities.actionFor` не выдаёт SIGNATURE).
    2. Иконки панели в Hulkbuster — старые иконки этапа 5 (hulk_grab / hulk_slam / hulk_hop, `IronManAbilities.icon`), хотя слоты запускают репульсоры / разлом / клетку.
    3. Вид от первого лица в основном закрыт геометрией костюма; камера от третьего лица внутри/за телом 3,4 блока (вид спереди залезает в мобов рядом).
    4. Изредка плоские белые квадраты-вспышки.
  - Во время проверки блока/рывка/реактивного прыжка/пикирования началось событие мира «Conquest» (NPC Viltrumite Thula, Vidor, Lucan) — местность разрушена, Hulkbuster пропал (игрок снова в повреждённом костюме). **Блок, рывок, реактивный прыжок, пикирование, запасная рука, выход из Hulkbuster надёжно НЕ проверены.**
- **Тор (короткая ручная проверка):**
  - Выбор Тора и подсказка управления в чате — есть.
  - ЛКМ: комбо молотом от первого и третьего лица — анимации идут, удары по хаску проходят (красная вспышка, крит-частицы, пыль).
  - R: переключение электрического режима «Electric mode: on/off» работает; электрический удар — молнии-дуги. R посреди комбо → «Not now» (отказ, ожидаемо).
  - СКМ рядом с врагом → удар молотом (HAMMER_STRIKE) — работает, урон по хаску.
  - **Бросок Мьёльнира (СКМ без врага в 4 блоках), Y — притяжение к молоту, СКМ — возврат: НЕ подтверждены.** Отойти от хаска не удалось (W во время проверки, похоже, не двигал игрока), поэтому СКМ всё время давал удар, а не бросок. Команды `/thor debug hammer state` и `/thor debug combat state` не показали вывода в чате — причина не выяснена.
  - Не проверены: V телепорт, Z «Гнев» (ультимейт), двойной WASD-рывок, захват, фаталити, подъём молота, бросок удержанием ПКМ.
- **Jinwoo** в этой сборке вручную не проверялся (только автотесты).
- Эпизод «Dev fell out of the world» — ошибка ввода команды `/kill` тестером, не баг мода.
- **Звук нигде не проверен** (нет аудиоустройства). Мультиплеер не проверялся.

Скриншоты сделаны в песочнице агента; бинарные файлы через доступный канал GitHub (только текст) не загружаются, к тому же часть папки скриншотов потеряна при перезапуске песочницы. Описание того, что на них, — в этом разделе.

## 6. Что реально проверено, а что нет

- **Проверено автоматически:** сборка, 2166+12 тестов, ресурсы, строки 12 языков, звуки, JSON, уникальность пакетов, порядок героев, сценарии Sinclair и DIO в клиенте.
- **Проверено вручную:** экран выбора (8 героев), основная часть набора Hulkbuster (см. выше), базовый бой Тора (комбо, электрорежим, удар СКМ).
- **Не проверено:** бросок/возврат/притяжение Мьёльнира, телепорт, ультимейт и фаталити Тора, подъём молота; блок/рывок/прыжок/пикирование/выход Hulkbuster; Jinwoo вручную; звук; мультиплеер.

## 7. Оставшиеся дефекты

- Известные из #74: 7 клипов Тора с проникновением в тело (px): electric_wave 4.96, lift_thor 2.58, hammer_thrust 2.39, fist_ground_pound 1.84, hammer_pommel 1.29, fist_elbow 1.08, hammer_heavy_reverse 0.71 (список KNOWN в ThorClipBodyTest). Не подтверждены в игре: бросок молота удержанием ПКМ, подъём молота, фаталити. Открытые пункты — `docs/design/2026-10-28-thor-hulkbuster-fix-spec.md`; карточка Veronica описывает набор этапа 5.
- Найдено сейчас (Hulkbuster): Jet Ram недоступен; старые иконки; камера/первое лицо; белые квадраты.
- Старое из main: нет `takeoff1.ogg`, 3 литеральных субтитра Viltrumite.
- Превью Тора на экране выбора — лицо обычного скина.

## 8. Какие файлы Тора изменены относительно #74

- Основной Java-код Тора (`hero/thor/**`, `client/thor/**`) — **не изменён**.
- Тесты Тора (4): `ThorHeroTest` (everyLocaleHasEveryThorText через ShippedLang; newHeroActionsKeepEveryExistingOrdinal: вместо «CONTEXT_TELEPORT последний» — `DIAGONAL_WORLD_CUT+4 == CONTEXT_TELEPORT`), `ThorIntegrationSeamsTest` (mergedLanguageFiles… — проверка дублей по каждому исходному файлу, равенство набора ключей после слияния), `ThorMoveTest` (heroActionAndHammerOrdinalsAreStable: окно `DIAGONAL_WORLD_CUT..+5` вместо «последних 5»), `combat/ThorCombatRound3Test` (lightningDamageType… через ShippedLang); удалены лишние импорты.
- Новый: `integration/ShippedLang.java` (тестовый хелпер), `viltrumitecore/src/main/lang-overlays/thor/ru_ru.json` (155 ключей).
- Общие «швы», отличающиеся от #74: HeroId, HeroAction, HeroRegistry, CoreMessages, ViltrumiteCore, ViltrumiteCoreClient, ClientModEvents, ViltrumiteEntities, RaceSelectionScreen, build.gradle, CREDITS.md, NoHeroBranchTest, ReinhardHeroTest, ReinhardReleaseTest, HeroPublicSnapshot (64-бит маска); в lang-файлах и sounds.json — блок #82 в середине.

## 9. Готовность к main

Сборка и тесты зелёные, содержимое обоих PR полностью внутри. **Но это не финальная приёмка:** Jet Ram у Hulkbuster недоступен, старые иконки, проблемы камеры, 7 клипов Тора и неподтверждённые действия, без проверки звука и мультиплеера. Рекомендация — держать PR черновиком до ZIP с исправлениями Тора и повторной ручной проверки.

## 10. Как накладывать ZIP с исправлениями Тора (он сделан на раскладке #74)

- Основной код Тора не трогался, поэтому патчи в `hero/thor/**`, `client/thor/**`, `tools/assets/thor_*`, `thor.animation.json` должны лечь чисто.
- Возможны конфликты в общих «швах»: HeroId / HeroAction / HeroRegistry / CoreMessages / ViltrumiteCore / ViltrumiteCoreClient / ViltrumiteEntities / RaceSelectionScreen / build.gradle (версия).
- **Языковые файлы:** если ZIP заменяет en_us и др. целиком — пропадёт блок #82. Накладывать как дифф ключей. Русские строки Тора живут только в `lang-overlays/thor/ru_ru.json`; новые ключи Тора — туда же для ru.
- **sounds.json:** события #82 в середине файла — добавлять события, а не заменять файл.
- 4 изменённых теста Тора: если ZIP их перезапишет — повторить правки ShippedLang и окна порядковых номеров.
- Обновлять список KNOWN в ThorClipBodyTest при исправлении клипов.
- Если меняются пакеты — следующий свободный протокол 15, версия 1.54.1+.
- В HeroAction блок Тора должен остаться сразу после DIAGONAL_WORLD_CUT; действий уже 65 — учитывать лимит маски (§1.4).
