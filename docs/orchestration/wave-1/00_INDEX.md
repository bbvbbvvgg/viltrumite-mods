# viltrumite 2.0 · Волна 1 · Карта сессий

База всех PR: `integration/viltrumite-2.0`. Служебная ветка оркестратора `ops/orchestrator-viltrumite-2.0` — не трогать. Доказательства плейтестов: `evidence/wave1-NN`.
Пути — относительно `viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/`.

| № | Сессия | Ветка | Зона владения |
|---|---|---|---|
| 01 | Масштаб ростера: маска действий | `wave1/01-roster-scale` | `HeroPublicSnapshot`, кодирование снимков, лимиты ростера |
| 02 | Автоматический плейтест-стенд | `wave1/02-playtest-rig` | `tools/playtest/`, dev-команды за флагом |
| 03 | Производительность под нагрузкой | `wave1/03-performance` | `perf/`, `client/perf/` |
| 04 | Выбор героя и Кодекс 2.0 | `wave1/04-hero-select-codex` | `codex/`, `client/codex/`, экраны `client/gui/` |
| 05 | Доктор Стрэндж | `wave1/05-doctor-strange` | `hero/strange/`, Зеркальное измерение |
| 06 | Годжо, Расширение территории, Сукуна | `wave1/06-gojo-domains` | `hero/gojo/`, `domain/`, `entity/sukuna/` |
| 07 | Флэш | `wave1/07-flash` | `hero/flash/` |
| 08 | Магнето | `wave1/08-magneto` | `hero/magneto/` |
| 09 | Гоку | `wave1/09-goku` | `hero/goku/` |
| 10 | Эрен: УПМ и Атакующий титан | `wave1/10-eren-titan` | `hero/eren/` |
| 11 | Кратос | `wave1/11-kratos` | `hero/kratos/` |
| 12 | Вторжение Империи Вилтрума и Траг | `wave1/12-viltrum-invasion` | `worldevent/viltrum/`, `entity/viltrum/` |
| 13 | Галактус, Пожиратель миров | `wave1/13-galactus` | `worldevent/galactus/`, `entity/galactus/` |
| 14 | Кналл: Король во тьме | `wave1/14-knull` | `worldevent/knull/`, `entity/knull/` |
| 15 | Врата и прорыв подземелий | `wave1/15-dungeon-gates` | `worldevent/gates/`, измерение подземелий, расширения Джину |
| 16 | Комплекс SCP: Зона-19 | `wave1/16-scp-site` | `scp/site/`, `scp/scp173/`, `scp/scp049/`, `scp/scp682/` |
| 17 | Космос и орбита | `wave1/17-space-orbit` | `dimension/space/`, космическое небо |
| 18 | Военный ответ и уровень угрозы | `wave1/18-military-response` | `faction/military/`, `entity/military/` |
| 19 | Трафик и машины | `wave1/19-traffic-vehicles` | `city/traffic/`, `entity/vehicle/` |
| 20 | Препарат V, Vought и Семёрка | `wave1/20-compound-v` | `faction/vought/`, `entity/supes/` |
| 21 | Подвиги, происшествия, репутация | `wave1/21-heroic-deeds` | `city/incidents/`, `reputation/` |
| 22 | Кинематографичность боя | `wave1/22-cinematic-combat` | `client/cinematic/`, `combat/feel/` |
| 23 | Столкновения: лучи, клинч, драка в небе | `wave1/23-clashes` | `hero/combat/clash/` |
| 24 | Командные комбо 2.0 | `wave1/24-team-combos` | `interaction/combo/`, записи `interaction/builtin/` |
| 25 | Разрушения 2.0 | `wave1/25-destruction` | `world/destruction/`, обрушения |
| 26 | Арена и турнир | `wave1/26-arena` | `minigame/arena/` |
| 27 | Играть за босса | `wave1/27-play-as-boss` | `minigame/bossplay/` |
| 28 | falbiksheroes: мост 2.0 | `wave1/28-falbiks-bridge` | `compat/falbiks/` |
| 29 | Секреты, легенды, достижения | `wave1/29-secrets` | `hero/secret/`, `secret/` |
| 30 | Режиссёр саги | `wave1/30-saga-director` | `saga/` |

## Контракты между сессиями
- 01 → все герои: маска действий без лимита; API проверки доступности не меняется.
- 03 → все: бюджеты частиц, сущностей и блок-апдейтов (`docs/performance/budgets-2.0.md`).
- 04 → все: data-файлы записей Кодекса и карточек выбора героя.
- 08 ↔ 19: тег сущности «металлический корпус» у машин.
- 05, 06, 09, 13 ↔ 23: интерфейс «луч» для борьбы лучей.
- 22 → 23, 24 и герои: API тряски, хит-стопа и кинокамеры.
- 25 → все разрушения: обломки и структурная целостность поверх журнала восстановления.
- 18, 19, 20, 21: угроза и репутация — только через события, без прямых зависимостей.
- 12, 13 ↔ 17: «отправить на орбиту над точкой».
- 27 → боссы: интерфейс «боссом может управлять игрок».
- 30 → события: data-запись места события в саге.
- 15 — единственная сессия, которая меняет Джину; 14 — только минимальные хуки в Веноме; 11 — без правок кода Тора.

## Окружение
- `00_SETUP.md` — офлайн-набор с Google Drive, сборка, сервер и клиент без интернета, работа с GitHub только через MCP. Читать первым.
