# Плейтест-стенд: плейтест за 10 минут

Стенд `tools/playtest/rig.py` одной командой поднимает выделенный сервер и клиентов Minecraft
(Forge 1.20.1, мод из этого репозитория) на машине без интернета и без видеокарты, сам
подключает клиентов к серверу, гоняет JSON-сценарии и пишет отчёт со скриншотами и метриками.

Работает на офлайн-наборе (`kit`): JDK, Gradle-кэш, Xvfb + Mesa llvmpipe, xdotool.

## Быстрый старт

```bash
cd ~/kit/project
# сборка + сервер + 2 клиента (Alpha, Bravo), каждый на своём Xvfb
python3 tools/playtest/rig.py up
# на машине с 4 ГБ RAM второй клиент не помещается - запускайте одного
python3 tools/playtest/rig.py up --clients 1 --no-build

# сценарий на уже поднятом стенде
python3 tools/playtest/rig.py run tools/playtest/scenarios/quick.json --reuse --keep

# или всё сразу: up -> сценарий -> down
python3 tools/playtest/rig.py run tools/playtest/scenarios/smoke-heroes.json

python3 tools/playtest/rig.py status        # процессы, память, дисплеи
python3 tools/playtest/rig.py down          # остановить всё
python3 tools/playtest/rig.py down --all    # то же + добить зависшие JVM/Xvfb после сбоя
```

Типовые времена на 2 ядрах / 4 ГБ: сборка ~3.7 мин, старт сервера ~20 с, вход клиента
~28 с, `quick.json` ~30 с, `smoke-heroes.json` ~7 мин.

Отчёт: `<kit>/rig/reports/<время>-<сценарий>/report.md` (+ `report.json`, `shots/`, `logs/`).

## Как это устроено

1. **Сборка** - `run.sh build` из набора (пропускается с `--no-build`; `--skip-tests` = `assemble`).
2. **Захват команды запуска.** Один раз запускается `:viltrumitecore:runServer` / `runClient`
   через Gradle, стенд находит JVM игры (новый процесс `java` с `BootstrapLauncher`) и
   сохраняет её argv, cwd и переменные окружения в `rig/launch-{server,client}.json`, после
   чего убивает всё дерево процессов Gradle. Дальше игра запускается **напрямую из JVM** без
   Gradle-демона - это экономит ~2 ГБ памяти и десятки секунд. Захват повторяется сам, если
   jar/classpath новее сохранённого.
3. **Сервер** - отдельный каталог `rig/server/`, `server.properties` пишется стендом
   (offline-mode, RCON на 25575, пароль `rig`, плоский мир, view-distance 6). Готовность -
   строка `Done (...)` и ответ RCON.
4. **Клиенты** - у каждого свой каталог `rig/clients/<ник>/`, свой Xvfb (дисплеи 100-199,
   TCP + Xauthority-cookie), свой ник (`--names`), `options.txt` без экрана спецвозможностей,
   `--quickPlayMultiplayer 127.0.0.1:<порт>` - автоподключение. Вход считается по строке
   `<ник> joined the game` в логе сервера.
5. **Сторож памяти** (`rig.py guard`) - отдельный процесс: если `MemAvailable` падает ниже
   `--guard-mb` (350 МБ), он убивает самого нового клиента, затем сервер. На машине без swap
   нехватка памяти может перезапустить всю ВМ, поэтому лучше потерять клиента, чем всё.
6. Перед стартом каждого клиента проверяется `--min-free-mb` (1500): если памяти не хватает,
   клиент не запускается и это пишется в отчёт (`--strict` превращает это в ошибку).

## Параметры `up`

| Флаг | По умолчанию | Смысл |
|---|---|---|
| `--clients N` | 2 | сколько клиентов |
| `--names A,B` | Alpha,Bravo,... | ники |
| `--no-build` / `--skip-tests` | - | без сборки / сборка без тестов |
| `--world fresh\|keep\|<dir\|zip>` | fresh | новый плоский мир, прошлый мир или готовый (например Atheria) |
| `--server-heap` / `--client-heap` | 1024 / 1200 МБ | `-Xmx` |
| `--min-free-mb` | 1500 | минимум свободной памяти перед запуском клиента |
| `--guard-mb` | 350 | порог сторожа памяти, 0 - выключить |
| `--port` / `--rcon-port` | 25565 / 25575 | порты |
| `--render-distance` | 5 | дальность прорисовки клиентов |
| `--lang` | en_us | язык клиентов (`ru_ru` для проверки переводов) |
| `--server-prop k=v` | - | дописать в server.properties |
| `--jvm ARG` | - | лишний аргумент JVM всем процессам |
| `--no-op` | - | не выдавать игрокам op |

## Другие команды

```bash
rig.py rcon "playtest who"            # команда на сервер
rig.py qa Alpha "press 1 20"          # строка в QA-канал клиента (subtitle_qa.txt)
rig.py key Alpha Escape               # клавиши через xdotool
rig.py shot Alpha /tmp/a.png          # скриншот экрана клиента
rig.py capture [all|server|client]    # пересохранить команды запуска
```

## Dev-команды `/playtest`

Включаются только флагом JVM `-Dviltrumitecore.playtest=true` (стенд ставит его сам), уровень
прав 2. В обычной игре команды нет.

| Команда | Ответ |
|---|---|
| `/playtest ping` | `playtest pong` |
| `/playtest heroes` | список ключей героев |
| `/playtest who [игроки]` | `playtest who <ник> hero=<ключ> hp=<hp> dim=<измерение> pos=x,y,z` |
| `/playtest hero <игроки> <герой>` | `playtest hero <ник> <было> -> <стало> ok\|same\|deferred\|refused` |

`viltrumite` - старое имя `homelander` (как `HeroId.fromKey`), в ответе будет `homelander`.

## Формат сценария

```json
{
  "name": "quick",
  "description": "...",
  "up": {"clients": 1, "world": "fresh"},
  "vars": {"hero": "thor"},
  "steps": [
    {"section": "Подключение"},
    {"server": "playtest ping", "expect": "playtest pong", "name": "dev-команды", "fatal": true},
    {"qa": "close", "client": "*", "pause": 1},
    {"expect": {"log": "*", "regex": "SUBPT t=\\d+ state level=true", "timeout_s": 20, "name": "в мире"}},
    {"shot": "join"},
    {"metrics": "idle", "seconds": 15, "min_tps": 18},
    {"repeat": ["human", "thor"], "as": "h", "steps": [ {"server": "playtest hero @a ${h}"} ]}
  ]
}
```

Шаги: `section`, `server` (строка или список RCON-команд, `expect` - regex по ответу),
`qa` / `key` (клиент или `*`), `expect.log` (`server`, ник или `*`), `shot`, `metrics`
(TPS/MSPT сервера, FPS/1% low/p99 клиента через `perfstate`, RSS, свободная память),
`wait`, `repeat`, `no_errors` (новые ERROR/исключения в логах с начала шага). Общие поля:
`name`, `pause`, `fatal`, `timeout_s`. Переменные: `${имя}` из `vars` или `--var имя=значение`.

Готовые сценарии в `tools/playtest/scenarios/`:

- `quick.json` - 30 секунд: подключение, `/playtest`, смена героя, скриншот, метрики;
- `smoke-heroes.json` - все 14 героев: вход, сервер видит героя, нет ошибок, метрики, скриншот;
- `world-events.json` - каждое мировое событие (`conquest`, `trio`): старт, работа, стоп;
- `city-atheria.json` - карта Atheria (`up --world <zip>`), `/city status`, `/city reset`.

Списки героев и событи��: `tools/playtest/data/*.json`, пересобираются из исходников
`python3 tools/playtest/gen_data.py`.

## Если что-то пошло не так

| Симптом | Причина и что делать |
|---|---|
| Машина перезапустилась, `/data` откатился | на песочнице агента ВМ перезапускается при нехватке памяти (нет swap) и, по наблюдениям, примерно раз в 15 минут. Один клиент на 4 ГБ, не собирать при запущенном клиенте, не убирать `--guard-mb`, результаты сразу выгружать. |
| `Xvfb ... libbz2.so.1.0: cannot open shared object file` | старый `rig.py`: в `LD_LIBRARY_PATH` попадал хвост `${LD_LIBRARY_PATH:+...}` из `client.sh`. Исправлено. |
| Клиент: `GLX: Failed to find a suitable GLXFBConfig`, `Timed out trying to setup the Game Window` | Xvfb из набора (Debian) ищет `swrast_dri.so` только в `/usr/lib/x86_64-linux-gnu/dri`. Стенд сам делает копию `client-runtime/usr/bin/Xvfb.rig` с относительным путём к драйверам набора. Проверка: `glxinfo -B` должен показать `llvmpipe`. |
| Захват не находит JVM игры | игра стартует внутри Gradle-демона в другой сессии процессов - стенд ищет любой новый `java` с `BootstrapLauncher`; смотрите `rig/capture-*.log`. |
| `client session already exists` у `client.sh` | удалить `<kit>/.client-session` после сбоя. Стенд этот каталог не использует. |
| В логах `UnknownHostException`, `yggdrasil`, `flite`, `narrator` | норма для офлайн-машины, в отчёте считается фоном. |
| Висят процессы после сбоя | `rig.py down --all`. Не используйте `pkill -f` с шаблоном из той же команды - он убьёт и саму оболочку. |

Метрики на llvmpipe (программный рендер на CPU) не равны игре на видеокарте: FPS клиента
сравнивайте только между прогонами на той же машине. TPS/MSPT сервера показательны.