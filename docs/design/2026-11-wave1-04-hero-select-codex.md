# viltrumite 2.0 · Волна 1 · Сессия 04 — Выбор героя и Кодекс 2.0

Ветка `wave1/04-hero-select-codex` → PR в `integration/viltrumite-2.0`. Зона: `codex/`, `client/codex/`, новые экраны в `client/gui/`. Логику героев не трогаем.

## 1. Видение для игрока

- **Экран выбора героя** (клавиша `H` и команда `/heroselect`): слева сетка портретов всех героев (масштабируется до 25+), сверху поиск и фильтры по роли и стилю. Справа крупное 3D-превью выбранного героя: модель медленно крутится и «дышит» в idle-позе, её можно повернуть мышью. Под превью — роль, сложность (звёзды), стиль, короткий лор и список способностей с описаниями и **реальными назначенными клавишами**. Кнопка «Стать героем» показывает перезарядку и причину отказа, если смена сейчас запрещена.
- **Кодекс 2.0** — энциклопедия с вкладками «Герои», «Боссы», «События», «Измерения», «Комбо», «Секреты». Записи открываются по мере игры: увидел босса, пережил событие, попал в измерение, сделал комбо. Закрытая запись — силуэт и «???» с подсказкой. Открытие — тост «Новая запись Кодекса» со звуком. Сверху — прогресс по каждой категории.
- **Обучение**: при первом выборе героя — подсказка на 3 шага (например, «нажми R — удар», «нажми V — рывок», «открой K — меню способностей»). Шаг засчитывается, когда игрок нажал нужную клавишу. Шаги берутся из данных героя.

## 2. Механики и контракты

### 2.1 Данные (data-driven)

Все данные — JSON в jar мода, папка `assets/viltrumitecore/codex2/`. Их читает общий загрузчик `codex/data/CodexData` **на обеих сторонах**: он обходит папку через файл мода (`ModList…getModFileById("viltrumitecore").getFile().findResource(...)`, `Files.walk`). Так dedicated server знает условия открытия записей, а клиент — оформление. Ошибки JSON пишутся в лог и не роняют игру.

- `codex2/entries/<категория>/<id>.json` — запись Кодекса:
  - `id` (уникальный, `snake_case`), `category` (`heroes|bosses|events|dimensions|combos|secrets`), `accent` (`#RRGGBB`), `order`;
  - `visual`: `{"type":"hero","hero":"thor"}` | `{"type":"entity","entity":"viltrumitecore:thanos"}` | `{"type":"skin","texture":"…","slim":false}` | `{"type":"icon","texture":"…"}` | `{"type":"halo"}`;
  - `sections`: ключи разделов (`lore`, `abilities`, `stages`, …). Текст раздела — lang-ключ `codex2.viltrumitecore.<id>.<section>`; заголовок — `codex2.viltrumitecore.<id>.title`; подсказка закрытой записи — `….hint`;
  - `related`: id связанных записей;
  - `unlock`: условия (любое открывает): `always`, `hero_chosen{hero}`, `entity_seen{entity,radius}`, `entity_killed{entity}`, `dimension{dimension}`, `world_event{event}`, `interaction{interaction}`, `manual` (только API/команда);
  - `secret: true` — до открытия скрыт полностью.
- `codex2/heroes/<hero_key>.json` — карточка героя для экрана выбора: `hero`, `role` (`tank|brawler|blaster|speedster|controller|support|assassin|summoner`), `styles` (`melee`, `ranged`, `flight`, `magic`, `tech`, `cosmic`, `summons`, `time`, `transform`), `difficulty` 1–5, `accent`, `tutorial` (ровно 3 шага `{"text":"lang.key","key":"key.viltrumitecore.ability_1"}`).
- **Новый герой без карточки всё равно появляется** на экране: список героев берётся из `HeroId`/`HeroRegistry`, способности — из `HeroDefinition.defaultLoadout()`/`panelAbilities()`, клавиши — из `KeyLabels`. Карточка только добавляет роль, стиль, сложность и обучение.

### 2.2 Прогресс (сервер — источник истины)

- `codex/CodexProgress` получает список `Entries` в том же NBT `ViltrumiteCodex` (старые `Heroes` и `Interactions` не трогаем — миграция не нужна) и список `Tutorials`.
- Публичный API: `CodexProgress.unlock(ServerPlayer, String entryId)` → `true`, если запись открылась впервые. Событие Forge `CodexUnlockEvent` — для достижений (сессия 29) и саги (30).
- Автоматические триггеры `codex/unlock/CodexUnlocks`: выбор героя, смена измерения, убийство сущности, старт мирового события, «увидел сущность» — опрос раз в 40 тиков в радиусе ≤ 48 блоков и только по типам из условий (A13).
- Синхронизация — свой канал `viltrumitecore:codex` (`CodexNetwork`), версия 1 → 2. **`CoreMessages` и `PROTOCOL_VERSION` не трогаем.**

### 2.3 Смена героя на сервере

- C2S `HeroSelectRequest(heroKey)` на канале Кодекса. Сервер проверяет: игрок жив; права (`heroSelect.permissionLevel`, по умолчанию 0 = все); перезарядка (`heroSelect.cooldownSeconds`, по умолчанию 30); герой существует; затем `HeroRegistry.changeHero` (он сам проверяет `admitsEntry`, откладывание сцен и т. п.).
- S2C `HeroSelectResult(status, heroKey, cooldownTicksLeft)`: `OK`, `COOLDOWN`, `NO_PERMISSION`, `REFUSED`, `SAME_HERO`, `DISABLED`.
- Перезарядка хранится в persisted NBT и переживает перезаход.
- Остальные клиенты видят смену через существующую синхронизацию героя.

### 2.4 Экраны (клиент)

- `client/gui/heroselect/HeroSelectScreen` + чистые `HeroSelectLayout` и `HeroSelectFilter` (юнит-тесты).
- 3D-превью: ванильный рендер сущности в GUI с существующими скинами героев, поворот и idle-покачивание. Новых анимационных систем нет (A5).
- `CodexScreen` получает новые категории; старые записи `CodexEntries` переносятся в JSON.
- Тост открытия `CodexUnlockToast`, обучение `HeroTutorialOverlay`.
- Звуки — `sound-overlays/codex2.json`, языки — `lang-overlays/codex2/`.

## 3. План срезов

1. Дизайн и черновик PR.
2. Данные: `CodexData`, модели JSON, тесты; перенос героев, боссов и событий; новые категории.
3. Прогресс: `Entries`, `unlock`, `CodexUnlockEvent`, триггеры, команды, сеть v2, тост.
4. Кодекс 2.0: вкладки, счётчики, закрытые записи.
5. Экран выбора героя: сетка, поиск, фильтры, 3D-превью, способности и клавиши, смена с правами и перезарядкой.
6. Обучение на 3 шага.
7. Контракт `docs/architecture/codex-2.0.md`, память.
8. Плейтест.

## 4. Риски

- 4 ГБ RAM — второй клиент, скорее всего, не поднимется; отметим честно.
- `CodexScreen` — 1115 строк; правки только аддитивные.
- ��онфликты с другими сессиями: `CoreMessages` не трогаем, `HeroId` только читаем.
