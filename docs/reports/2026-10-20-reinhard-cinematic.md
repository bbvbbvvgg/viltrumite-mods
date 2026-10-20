# Reinhard — Holy Sword cinematic overhaul: отчёт реализации

## База и границы

- Исходный актуальный HEAD PR #36: `69d094d0be5bb1e11c192266159c6df84a86cdc3`.
- Новая ветка: `feat/reinhard-holy-sword-cinematic`, непосредственно от этого SHA.
- PR #35 и stage7 не использовались как база. PR #36 не перезаписан.
- Core **1.35.0**, flight **1.10.1** без изменений. Core network protocol **3**:
  сервер и все клиенты должны обновить core вместе.
- Согласованные исходная узкая геометрия Рейда, длина, масштаб, final local-Y
  roll свободного клинка, ножны и крепление сохранены. `ReinhardSword.java`
  не изменён. Геометрии blade/sheath совпадают побайтно с базой.
- World Erasure, стеклянный shader, его клип и серверная механика не переписаны.
  Это сохранение уже сделанной реализации, а не новая версия World Erasure.

## 1. Реальные причины и технические решения

1. Сцена старого World Cut заканчивалась на фиксированном тике, хотя очередь
   разрушения могла ещё работать. Теперь сервер посылает START / BLAST / WORK /
   DONE. DONE зависит от фактического владения задачей в CarveJobs. Тьма,
   грохот и тряска продолжаются до DONE, затем затухают. Клиент имеет watchdog,
   а не бесконечный чёрный экран.
2. Очередь допускала только один разрез в измерении. Общая admission-проверка
   теперь разрешает два наземных/диагональных разреза суммарно. Их задачи
   используют quantum 250 и делят один адаптивный бюджет, не два независимых.
3. Форма земли была постоянной прямоугольной полосой. Серверный mask теперь
   имеет согласованные изменения ширины, глубины, уступы и конечное сужение.
   Hit-test также учитывает реальную ширину затронутого уступа.
4. Тайминг дождя не соответствовал числу движений клинка. Теперь 12 контактов
   на тиках 7, 10, …, 40; каждому соответствуют grip-keyframe, серверный удар,
   пространственная волна и звук. Финальный контакт имеет отдельный акцент.
5. Новая выразительная диагональная траектория первоначально оказалась
   недостижимой для кисти в середине перехода и пересекала голову предплечьем.
   Это обнаружили промежуточные full-mesh CPU/SAT-проверки. Изменена именно
   траектория оружия/корпуса, а не допуски тестов и не согласованная модель.
6. После добавления способности enum ordinal 14 сталкивался бы с wire-слотом
   cooldown смены режима. `snapshotIndex()` сохраняет mode/Phoenix на 14/15,
   новая способность использует 16. Старые action IDs не переставлены.
7. Обычный cap CameraShake 2.5 обрезал запросы экстремальной тряски. Добавлен
   явный cinematicAt в существующий аккумулятор. Обычные эффекты сохраняют cap,
   но не сбивают уже активный кинематографический вклад.

Сохранены existing AnimCache / AnimationController / grip-bone / ReinhardLayers,
PixelVfx, SkyTint, SoundDuck и CameraShake. Новый движок и второй post-chain
не добавлены. Сэмплирование гигантского меча использует копии костей и проверено
против AnimationController; world-render не мутирует общий скелет.

## 2. Выполнение пунктов задания

| Пункт | Что реализовано в коде |
|---|---|
| Двухручный хват | Сохранён согласованный solver и distinct palm targets; новые cut/rain clips используют общий reid_grip и обе руки. FP имеет собственные парные фазы, TP — реальные связанные кости. |
| Обнажение | Сохранены 120 тиков, лифт, волна, сообщение и прежние звуковые слои. Добавлены original 6-секундный слой, усиленное плавное затемнение, многослойный пульсирующий столп и бело-золотые выбросы у sampled hilt/blade. Финальные столп/воронка плавно исчезают. |
| Y Lethal Chop | Только полностью SHEATHED; DRAWING / DRAWN / SHEATHING запрещены сервером и HUD. Общий chop других героев не изменён. |
| V Rain of Cuts | 12 серверных контактов, 30°/40 блоков, фиксированное направление каста, 46-тактовый клип, новые волны/следы/контактные вспышки и звук каждого удара. UUID root dedup на один разрез, прежние ограниченные grooves через CarveJobs. |
| Z Diagonal World Cut | Новый ID, handler, 3D geometry, packet, clip, FP-поза, icon и локализации. Тёмная пространственная плоскость с золотыми границами, ветвящимися акцентами и фрагментами; после DONE щель сходится и гаснет. Удалённые блоки не восстанавливаются. |
| B Ground World Cut | Новый медленный двухручный подъём/удержание/удар/возврат. Actual Reid mesh как гигантская полупрозрачная проекция на том же grip-track. Контакт/BLAST/начало carve — тик 80. Неровный постоянный каньон. |
| Свет и звук | Пофазные гул, hush, оригинальный удар, продолжающийся грохот, хвост на DONE. SkyTint и bounded fog/dust curtain скрывают постепенную работу очереди; эффект плавно снимается, не меняет громкость в настройках игры. |
| Тряска | Нарастание, пик и непрерывный сильный вклад во время работы, включая кастера. Local reinhardWorldCutShake 0..2, default 1; max cinematic contribution 8, common legacy cap 2.5. |
| Root | Клиент очищает движение/прыжок, сервер удерживает cast anchor и сбрасывает velocity; JUMP/AIR_STEP и другие action starts запрещены обязательной фазой. Anchor очищается при cancel/cleanup; нет постоянного noGravity-флага. |
| HUD/раскладка | R/Y/Z/V/B/N обновлены; старый Sheath Strike не owned, не показан и не зарегистрирован как handler. Сохранены старые enum ID ради стабильности. repairLoadout убирает obsolete ID; в старом custom loadout возможен возврат к default раскладке. |
| Производительность | Loaded-only/build-height/border/mobGriefing/unbreakable checks, quiet removal без drops и финального neighbor sweep. Каждая попытка, включая air и mask reject, расходует общий бюджет. Ограничены сцены/волны/debris. |
| Регрессии | HeroKeyEdges production unchanged; существующие draw/sheath/Erasure clips unchanged. Общие системы opt-in, flight production unchanged. Полный автоматический suite зелёный; native cross-hero acceptance не заявляется. |

## 3. Геометрия

### Ground World Cut

Длина 200 блоков. Продольный coherent noise меняет глубину по 16-блочным
узлам, берега — по 12-блочным. Ширина имеет плавные конечные сужения,
детерминированные расширения/сужения. Пять уровней глубинных уступов постепенно
уменьшают поперечник. Нет независимого случайного удаления кубиков и оторванных
шумовых дыр. Высота поверхности фиксируется при старте из загруженных колонок;
карвинг учитывает build height и границу мира. В глубокой середине масштаб
сохраняет прежние 45–50 блоков глубины; начало/конец мельче.

### Diagonal World Cut

Это отдельный конечный наклонный OBB/plane volume, не WorldCutShape с другим yaw.
Оси строятся от направления взгляда: forward, диагональный span и normal.
Длина 200, half-span до 28, half-thickness 2.25 блока, пространственный mask
пересекает воздух, постройки и скалы независимо от heightmap. Pitch ограничен
±35°. По длине есть конечное сужение и небольшая согласованная неравномерность.
Итератор сканирует AABB лениво с O(1) cursor memory; даже точки вне mask
расходуют visits. Поэтому наклонная сцена в тяжёлом случае может длиться дольше
наземной, но не получает неограниченный CPU-проход за один тик.

## 4. Синхронизация и жизненный цикл

- Единый серверный game-time, постоянные origin/direction/seed/startTime.
- Наземный/диагональный контакты — тик 80; action recovery до тика 100.
- Задержка восстановления игрока не зависит от длительности серверного carve.
  Admission сцены продолжает учитывать ещё работающую очередь.
- Damaging wave ограничена собственным временем; медленный carve не оставляет
  невидимую убийственную область на всё время ожидания очереди.
- Detached WorldCutManager продолжает уже начатый разрез после смерти/выхода
  кастера. Его нет в качестве действующего attacker после ухода/смены измерения.
  Unload/stop уничтожают неперсистентные очередь/сцену.
- WORK heartbeat раз в 10 тиков, DONE по queue ownership. Клиентский watchdog
  140 тиков без heartbeat, общий safety timeout 12000; DONE fade 70 ground / 90
  diagonal. При паузе клиента локальный таймер не продвигается.
- Новые packet registrations добавлены в конец; core protocol повышен до 3.
- Нет индивидуального кастомного пакета на каждый удалённый блок. Стандартная
  синхронизация изменения блоков Minecraft, конечно, остаётся.

## 5. Изменённые подсистемы и ресурсы

- `hero/reinhard`: Abilities/Ability/Actions/State/Rules/Hero, Reid slot rules,
  CutRain, WorldCut, новый DiagonalWorldCut.
- `hero/reinhard/cut`: WorldCutGeometry/Shape/Carve, новая DiagonalCutGeometry.
- Shared: opt-in CarveJobs quantum/pending; append-only HeroAction;
  CameraShake cinematic contribution и local config.
- `client/reinhard`: clips/poses/first-person/renderer; новый TitanBlade;
  cut WorldCutScene/CutAtmosphere/DiagonalCutScene/CutRainWaves;
  ReidDrawInput/ReidDrawSceneVfx.
- `network`: два appended S2C пакета, WORK/DONE старого WorldCut packet.
- Генератор существующих animation JSON, new icon generator, все 11 locales,
  sounds.json, четыре procedural OGG assets, CREDITS/source ledger.
- Unit/integration tests, актуальная design note, implementation plan,
  animation/shared-toolkit procedures и durable memory.

## 6. Проверки

### Автоматические — выполнены

- Java 17, Forge 47.3.0: `./gradlew build --no-daemon --max-workers=2`
  с Gradle heap 2 GiB — **BUILD SUCCESSFUL**.
- **1292/1292 JVM tests:** core 1280, flight 12; 0 failures/errors/skips.
- Новые тесты 3D basis/finite mask/build-height/unique lazy cursor/наклонной
  раны в воздухе и препятствиях; два реальных carve job делят 2000 visits;
  packet round trips/validation; production clip sampling без мутации;
  damage по ширине фактических уступов.
- Расширенные full-mesh tests новых cut/rain фаз: palms reach distinct points,
  intermediate standing/crouched SAT checks against head/body/legs. Не ослаблены
  ради прохождения. Это геометрическая диагностика, не native visual acceptance.
- `check_hero_resources.py --hero reinhard`: **0 new, 4 known legacy**.
- `git diff --check`: clean.
- Blade/sheath hash equality с исходным SHA; `ReinhardSword`, HeroKeyEdges
  production и existing reid_draw/reid_sheathe/world_erasure clips unchanged.
- Reobfuscated JAR содержит четыре новые OGG, новую PNG и оба maintained
  credit/source notices. Оригинальный procedural audio не использует внешние
  записи; CC0, детерминированный генератор. Запрещённые NC assets не добавлены.
- Decoded Vorbis true peaks новых draw/impact/sweep ниже 0 dBFS. Это измерение
  файлов, не проверка суммарного игрового микса или субъективной громкости.
- CPU montage actual posed vertices сделан и просмотрен: front/side/back для
  hold/contact/recovery обоих разрезов и трёх Rain контактов. Это НЕ Minecraft.

### Native — барьеры среды, НЕ принятие результата

`runClient` повторно попробован на итоговом коде: Forge early display сообщает
`Unable to initialize GLFW` / `glfwInit failed`. До игрового окна дело не дошло;
проверки VFX, first-person, shader compatibility, FPS и звука в игре не выполнены.

`runServer` попробован отдельно: сервер сообщает `You need to agree to the EULA`
и завершает работу без загрузки игрового мира. EULA автоматически не принята. Успех Gradle task после EULA-stop не равен успешному
старту dedicated world и не доказывает common-side runtime compatibility.

## 7. Что остаётся проверить в Minecraft

- Полный FP/TP visual pass: ready/walk/sprint/turn, draw→ready→rain→ground/diagonal,
  recovery/interruption, крайние pitch камеры. CPU не доказывает естественность.
- Момент giant Reid contact и правильный native render/model-view/shader pass.
- Убедительность пространственной раны, её схлопывания и постоянного следа.
- Тьма/плотность пыли на разных расстояниях. Код содержит curtain, но отсутствие
  видимого поочерёдного удаления блоков непосредственно в игре не доказано.
- Listening: субъективные ~1.5× draw, мощность/динамика обоих cuts, attenuation,
  соседние сцены, headroom общего микса, нет обрывов и усталости от rumble.
- Два игрока/два одновременных cuts, matching protocol3, jitter/lag, выход/смерть,
  mode/hero/dimension switch, unload/reload, отсутствие утечки tint/duck/root.
- Равнина/гора/город/вода, world border, bedrock, mobGriefing=false, старый мир,
  реальные fluid callbacks, lighting, block updates и контейнеры.
- TPS/FPS под измеренной нагрузкой. **TPS≥15 не заявляется без замера.**
- Сохранённые World Erasure/glass, Regulus, Iron Man, Homelander и иммунитеты.

Код и автоматические проверки готовы к review. Художественная и игровая приёмка
по этим пунктам остаётся открытой; «визуально идеально» не заявляется.
