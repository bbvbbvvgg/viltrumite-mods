# Последствия городских катастроф — контракт интеграции

Ветка `feat/city-disaster-aftermath` (core 1.66.0, протокол 24), база `260f7551`. Документ для параллельных веток
`feat/city-infrastructure-disasters` и `feat/city-structural-collapse` (обе от той же базы) и для будущих событий
(Альтрон, частичные обрушения). Код aftermath **не импортирует** классы этих веток; они зовут только API ниже —
одни ванильные типы (`ServerLevel`, `BlockPos`, `BoundingBox`, `CompoundTag`) и record `AftermathEvent`.

Пакет: `dev.baranhan.viltrumitecore.worldevent.aftermath`.

## 1. Общие поля события (одинаковы во всех ветках)

| поле | тип | смысл |
|---|---|---|
| `eventId` | `String` | уникален на событие; повтор того же id освежает зону, а не считается дважды (`"collapse:1f3a"`) |
| `dimension` | `String` | id измерения, `"minecraft:overworld"` |
| `center` | `BlockPos` | центр разрушения |
| `radius` | `float` | блоки, обрезается до 1..48 |
| `severity` | `float` | 0..1: 0.2 — разбитый фасад, 0.5 — выгоревший дом, 1 — рухнувшая башня |
| `cause` | `String` | id причины (ниже); неизвестный → `other` |
| `affectedBlocks` | `List<BlockPos>` | разрушенные позиции, любое число; хранится выборка ≤ 48 (точки дыма/пыли) |
| `timestamp` | `long` | `level.getGameTime()` события |

Record: `AftermathEvent(eventId, dimension, center, radius, severity, cause, affectedBlocks, timestamp)`,
фабрика `AftermathEvent.of(level, eventId, center, radius, severity, AftermathCause, blocks)`,
тег `toTag()` / `fromTag(tag)` (ключи — ровно имена полей; `center` — int[3], `affectedBlocks` — long[] из
`BlockPos.asLong()`; вместо `center` можно `x`/`y`/`z`).

Причины (`AftermathCause`, id стабильны): `meteor`, `chitauri`, `leviathan`, `hero`, `explosion`, `destruction`,
`collapse`, `ultron`, `infrastructure`, `other`. Причина задаёт смесь каналов: `collapse` — почти чистая пыль и
обломки, без огня; `meteor` — огонь, жар, дым; `infrastructure` — искры и металл; `ultron` — металл и искры.

## 2. API (все методы — только на сервере, безопасно на выделенном сервере)

```java
// урон: зона рождается (или перезапускает свежую стадию) сейчас; возвращает id зоны, 0 = проигнорировано
int CityAftermath.registerDamage(ServerLevel level, AftermathEvent event);

// обрушение: как registerDamage, cause по умолчанию "collapse"; box — коробка рухнувшей части (может быть null).
// Пыль, дым, тление, обломки и звуки обрушения концентрируются ТОЛЬКО внутри box
// (без box — по affectedBlocks и сфере radius).
int CityAftermath.registerCollapse(ServerLevel level, AftermathEvent event, @Nullable BoundingBox box);

// инфраструктура: powered=false — регион без электричества, руины в нём перестают искрить проводами
// (остаются только искры самого удара); powered=true снимает. Сохраняется в мире, до 64 источников.
void CityAftermath.setPower(ServerLevel level, String sourceId, BlockPos center, float radius, boolean powered);
boolean CityAftermath.unpowered(ServerLevel level, double x, double y, double z);

// тег-форма (без record): поля §1 + "kind" = "damage" | "collapse" (+ int[6] "box") | "power" (+ "powered")
int CityAftermath.accept(ServerLevel level, CompoundTag tag);

// подсказка причины для обычной ломки блоков вокруг точки в ближайшие 60 тиков (дёшево, можно часто)
void CityAftermath.hint(ServerLevel level, AftermathCause cause, double x, double y, double z, float radius);

// чтение
List<AftermathZone> CityAftermath.zones(ServerLevel level);
AftermathZone CityAftermath.nearest(ServerLevel level, double x, double y, double z, double margin);
boolean CityAftermath.avoid(Level level, double x, double y, double z);   // жителю сюда нельзя
float CityAftermath.welcome(Level level, double x, double y, double z);   // 0..1, можно ли спавнить/гулять

// сброс
void CityAftermath.clear(ServerLevel level);        // все зоны уровня
void CityAftermath.onCityReset(ServerLevel level);  // уже вызывается из CityManager при /city reset
```

### Как подключиться (после слияния)

- **`feat/city-structural-collapse`**: в месте, где обрушение посчитано (известны блоки и коробка), один вызов
  `CityAftermath.registerCollapse(level, AftermathEvent.of(level, "collapse:" + id, center, r, sev, AftermathCause.COLLAPSE, blocks), box)`.
  Сами разрушенные блоки трекер всё равно увидит (хук `setBlock`); пачки рядом с объявленной зоной сливаются в неё
  (не плодят вторую), а повтор `eventId` не удваивает тяжесть.
- **`feat/city-infrastructure-disasters`**: отключение — `setPower(level, "grid:" + id, center, r, false)`,
  восстановление — `setPower(..., true)`. Если авария рушит блоки — `registerDamage` с `cause = "infrastructure"`.
- **Альтрон / будущие события**: `registerDamage` с `cause = "ultron"` (или `hint(...)` перед массовой ломкой).
- Без compile-зависимости (например, из скрипта/датапака через другой мод): тот же контракт тегом через `accept`.

## 3. Что делает aftermath с зоной

Стадии (`AftermathStage`, при множителе длительности 1, тяжесть 0.5; `f = (0.5 + severity) × aftermathDurationScale`):

| стадия | до | что видно и слышно |
|---|---|---|
| `impact` | 45 с × f | облако пыли, искры, осыпание крошки, плотный дым, горячее свечение, гул, обломки |
| `smolder` | 6 мин × f | пыль осела; локальный дым из окон и проломов, тление, треск, редкое осыпание, сирены вдали |
| `late` | 16 мин × f | руины стоят, дым почти исчез, ветер в руинах; жители возвращаются |
| `scar` | +2 ч × f | только память зоны (жители уже ходят как раньше), затем зона удаляется |

Огонь гаснет всегда: канал `FIRE` = 0 с 60 % стадии `smolder`, `SMOLDER` = 0 с 60 % стадии `late`.
Блоки aftermath **не ставит и не чинит**: восстановление остаётся за журналом города и `/city reset`.

Хранение: `SavedData` `viltrumitecore_aftermath` на уровень (зоны ≤ 48, отключения ≤ 64), переживает релог и
рестарт; возраст — по игровому времени. `/city reset` (и возобновлённый после рестарта сброс) стирает зоны.

## 4. Сеть и клиент

`AftermathS2CPacket` (SYNC / REMOVE / CLEAR, ≤ 16 зон на пакет), протокол 23 → 24. Каждый игрок получает
только зоны в 224 блоках, диффом раз в 40 тиков (сразу — при новом ударе). Клиент сам ведёт часы стадий,
сканирует руины порционно (6 точек за тик, повтор раз в 30 с) и находит окна, проломы, завалы, нависания и горячие
точки; бюджет частиц ≤ 72/тик (меньше при «Уменьшено»/«Минимум»), звуков — ≤ 7 одноразовых за 2 с на все руины.

## 5. Команды (оператор, уровень 2)

`/aftermath status | list | clear [nearest] | add <cause> [radius] [severity] | collapse [radius] [severity] |
stage <impact|smolder|late|scar> | power off [radius] | power on` — для QA и интеграции (`add`/`collapse` создают
зону через тот же контракт, `power` — через `setPower`).

## 6. Конфиг (`config/viltrumitecore.json`)

`aftermathEnabled = true` — выключатель всей системы; `aftermathDurationScale = 1.0` (0.1..10) — растягивает стадии.
