# THOR — реальный API-контракт v4.2 (проверен по Java на BASE_SHA)

**Единый BASE_SHA для сессий A/B/C:** `3c233edc3e480aa84d916e4a39b1507945ec34e1`  
**Репозиторий:** `bbvbbvvgg/viltrumite-mods` · **ветка фундамента:** `feat/thor-foundation`  
**GitHub:** https://github.com/bbvbbvvgg/viltrumite-mods/commit/3c233edc3e480aa84d916e4a39b1507945ec34e1

Этот документ заменяет технические предположения старого `thor_foundation_api_contract_v4_1.md`. Проверены Java-исходники на указанном SHA, а не только отчёт агента. Механики и управление определяет `thor_gowr_spec_v2_4_strict_canon.md` (решения №1–220); API-контракт не добавляет способностей.

## 1. Что уже существует (не реализовывать заново)

Пакет ниже, если не указано иное: `dev.baranhan.viltrumitecore.hero.thor.bridge`.

| Назначение | Реальный API на SHA |
|---|---|
| Регистрация приёма | `ThorMoves.register(String owner, ThorMove move, ThorMoveDefinition definition, ThorMoveHandler handler)`; `authored(move)`, `definition(move)`, `handler(move)`, `owner(move)`, `resetForTests()` |
| Тайминги | `new ThorMoveDefinition(int intro, int main, int finish, @Nullable MovePhase cancellableFrom, int maxSteps)`; длительность должна быть >0 |
| Обработчик | `ThorMoveHandler.onStart(ThorMoveContext)`, `onPhase(ThorMoveContext, StagedTimeline.Event)`, `onRelease(ThorMoveContext, int heldTicks)`, `onStop(ThorMoveContext)`; `NONE` |
| Контекст приёма | `ThorMoveContext(@Nullable ServerPlayer player, ThorState state, ThorMove move, @Nullable LivingEntity target)` |
| СКМ | `dev.baranhan.viltrumitecore.hero.thor.ThorControls.hammer(ThorContext)` — **единственный резолвер**: GRAB → RECALL → HAMMER_STRIKE → HAMMER_THROW |
| Запрос ближнего удара СКМ | **Не отдельное событие**. B регистрирует `ThorMove.HAMMER_STRIKE` в `ThorMoves`, получает цель через `ctx.target()` при `onStart` |
| Доступ к молоту | `ThorBridge.installHammerAccess(String owner, HammerAccess access)` — A устанавливает ровно один провайдер; `ThorBridge.hammer(UUID, @Nullable ThorState)` и `ThorBridge.hammer(Player)` — чтение B/C |
| Контракт провайдера | `@FunctionalInterface HammerAccess.view(UUID thor, @Nullable ThorState state)` возвращает `HammerView` |
| Снимок молота | `HammerView(@Nullable UUID logicalId, @Nullable UUID activeOwnerId, HammerState phase, boolean inHand, @Nullable ResourceKey<Level> dimension, @Nullable Vec3 position)`; `flying()` = `phase == THROWN` |
| Состояние Тора | `ThorBridge.state(ThorState)` / `state(Player)` → `ThorStateView(mode, move, phase, moveElapsed, grabWindowTicks, sceneLockOwner, cameraLeaseOwner)` |
| Окно захвата | `ThorBridge.openGrabWindow(ThorState, int ticks)`, `closeGrabWindow(ThorState)`, `grabWindowActive(ThorState)` |
| Сценическая блокировка | `ThorBridge.lockScene(ThorState, String owner)`, `unlockScene(ThorState, String owner)`, `sceneLocked(ThorState)` |
| Аренда камеры | `ThorBridge.acquireCamera(ThorState, String owner)`, `releaseCamera(ThorState, String owner)`, `cameraLeased(ThorState)`; клиент получает `ThorFlags.CAMERA_LEASE` (бит 4) |
| Шина событий | `ThorEvents.subscribe(String owner, ThorEventType type, ThorEvents.Listener listener)`, `post(ThorEvent)`, `nextId()`; `post` отбрасывает повторный eventId (окно 512) |
| Событие | `ThorEvent(long eventId, ThorEventType type, UUID attackerId, @Nullable UUID targetId, @Nullable ThorMove move, int attackId, boolean hammerInHand, float amount, boolean success)` |
| Типы событий | `ThorEventType.MELEE_HIT`, `LETHAL_HIT`, `COMBO_END`, `THROW`, `RECALL`, `CATCH`, `HAMMER_STATE_CHANGED` |
| Dev-дерево | `dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands.registerChild(String owner, String path, UnaryOperator<LiteralArgumentBuilder<CommandSourceStack>> leaf)`; `paths()`, `owner(path)` |

**Существующие тестовые команды фундамента:** `/thor foundation state`, `/thor foundation hammer <place>`, `/thor foundation mode <mode>`, `/thor foundation grab_window <ticks>`, `/thor foundation release_locks`. Доступны только в dev/test окружении оператору уровня 2. Не создавать второй корень `/thor`.

**Не перепутать API с описательными названиями:** `onMmbMeleeRequested`, `onMeleeHit`, `onLethalHit`, `onComboEnd`, `onHammerStateChanged` — **не Java-методы**. Их реальные аналоги: обработчик `HAMMER_STRIKE` и `ThorEvents.post/subscribe` с соответствующими `ThorEventType`. Нет класса `ThorIntegrationBridge`.

## 2. Владение и обязательные точки подключения

### Сессия A — молот, СКМ, Y, превращение

- Только A создаёт мировую сущность/`SavedData`, гарантирует **один логический UUID во всём сохранении** и не более одного активного Тора; реализует мировое падение, бросок, возврат, ловлю, Y-магнит и превращение.
- Только A вызывает `ThorBridge.installHammerAccess("A", access)` (владелец-строка согласуется с остальными регистрациями A); B/C читают `ThorBridge.hammer(...)`, не пишут мировой молот.
- Единый `ThorControls.hammer(context)` уже существует. A **уточняет его работу с мировым состоянием**, не создаёт второго резолвера; базовый приоритет GRAB > RECALL > MELEE > THROW не менять. В текущем enum нет RESTING, поэтому нельзя полагаться лишь на `ThorState.hammer.away()` при отзыве лежащего молота.
- A регистрирует только свои `HAMMER_THROW`, `RECALL`, `HAMMER_PULL` и нужные собственные технические движения, публикует `THROW`, `RECALL`, `CATCH`, `HAMMER_STATE_CHANGED` с `ThorEvents.nextId()`.
- Заряд СКМ — `ThorMoveHandler.onRelease(ctx, heldTicks)`; выбор приёма зафиксирован на press. Dev-команды — `/thor debug hammer ...` через `registerChild`.
- **Критические оставшиеся проблемы фундамента:** `ThorState.load()` возвращает брошенный молот в руку; `ThorHero.enter()` сбрасывает молот на пояс; `ThorActions.advance()` вызывает `settle()` и меняет молот при занятой руке; всё это A должна заменить/адаптировать так, чтобы глобальный store был единственным писателем. `ThorFlags` хранит 4 старых значения `HammerState` в 2 битах: не менять ordinals, дополнительные мировые фазы — отдельно.

### Сессия B — бой и молнии

- B регистрирует `HAMMER_STRIKE`, `HAMMER_COMBO`, `FIST_COMBO`, `ELECTRIC_COMBO`, `ELECTRIC_MODE`, `WRATH` и свои **дополнительные утверждённые** движения, если нужны, через `ThorMoves`. Не регистрировать retired ID.
- СКМ-меле приходит через `ThorMoveHandler.onStart(ctx)` для `HAMMER_STRIKE`; `ctx.target()` может быть null в чистых тестах, проверять.
- B публикует `MELEE_HIT`, `LETHAL_HIT` (до окончательной смерти; `hammerInHand` фиксируется на момент удара), `COMBO_END` (`success=true` только при завершённом успешном электрическом комбо). **Автоматическая небесная молния принадлежит B, а не C**, даже если комментарий в коде `ThorEventType` ошибочно упоминает C.
- `ThorControls.resolve(STANCE)` пока возвращает `null`: **ПКМ ещё не маршрутизирована**, её подключает B в существующей центральной системе без второго input pipeline и с проверкой конфликтов с A. Для электрического режима: ЛКМ разряд, ПКМ волна, удержание ПКМ гроза, как в каноне. Не вводить блок/парирование.
- При необходимости B интегрирует урон через реальные хуки `HeroDefinition`/`HeroDamage`, проверяя до commit смерти. Dev-команды `/thor debug combat ...` через `registerChild`.

### Сессия C — захваты, фаталити, камеры, полёт, V и двойное WASD

- C регистрирует `GRAB`, `FATALITY`, `TELEPORT_SELECT` и свои утверждённые сценические действия. СКМ не переназначает: только `ThorBridge.openGrabWindow(...)` для приоритета GRAB.
- C подписывается на `LETHAL_HIT` через `ThorEvents.subscribe(...)`, проверяет `hammerInHand`, тип жертвы и предотвращает двойную смерть. Не запускает фаталити от `THROW`/`CATCH` и не реализует небесную молнию.
- Для V-телепорта и фаталити C использует `acquireCamera/releaseCamera` и `lockScene/unlockScene`; **реального клиентского camera controller в фундаменте нет**. При scene lock общие нажатия отклоняются — кнопки внутри захвата и подтверждение V должны проходить по отдельному разрешённому сценическому контексту, без обхода серверных проверок.
- C интегрирует существующий Viltrumite Flight, двойное WASD, hover, ограничения full flight; Y-магнит — A. Dev-команды `/thor grab <target>`, `/thor fatality <target> <1-3>`, `/thor debug grab-window ...` через `registerChild`.

## 3. Что уже исправлено в фундаменте

- `ThorMove` — стабильные ordinal wire ID, append-only; старые `GUARD`, `AIR_STRIKE`, `LIGHTNING_DASH`, `TELEPORT_STRIKE`, `DIVE_SLAM`, ручной `LIGHTNING_CALL`, `GRAB_SLAM` имеют `retired=true` и не могут быть зарегистрированы. `DRAW`/`HOLSTER` технические, без пользовательских кнопок.
- `SHIPPED_STAGE` удалён. Незарегистрированный приём не стартует; тайминги берутся из `ThorMoveDefinition`.
- R → `ELECTRIC_MODE`; Y → `HAMMER_PULL` только при `THROWN`; V → `TELEPORT_SELECT`; Z — **временный дефолт** ульты. СКМ — единственный контекстный резолвер.
- `ThorHero.handleInput(..., pressed=false)` доставляет отпускание к выбранному на press приёму; `ThorFlags` передаёт grab-window и camera lease.
- Базовые обработчики событий **не публикуют боевые события автоматически**; A/B/C подключают собственные точки. Само наличие event bus не означает готового фаталити.

## 4. Проверки и статус

- **Подтверждено по GitHub:** `feat/thor-foundation` HEAD равен `3c233edc3e480aa84d916e4a39b1507945ec34e1` на момент проверки. Реальные Java API перечислены выше.
- **По отчёту исполнителя**, а не независимо воспроизведённым локальным запуском: `:viltrumitecore:test` 1656 тестов, `:viltrumiteflight:test` 12 тестов, `:viltrumitecore:build` прошёл.
- Клиент не проверен; тестовый сервер после последнего коммита не запускался. Каждая сессия обязана заново проверить сборку и поведение своей ветки.
- Все три независимые ветки создаются от **точного SHA `3c233edc3e480aa84d916e4a39b1507945ec34e1`**, а не от плавающего `feat/thor-foundation`, старого SHA или `main`.
- Если работа уже начата от старого SHA, **не выбрасывать изменения**: сохранить коммиты, перенести только собственные коммиты на новый BASE_SHA, устранить конфликты и заново прогнать тесты. Просто поменять SHA в промпте недостаточно.

## 5. Никаких самовольных механик

**Не реализовывать** `GUARD`, `AIR_STRIKE`, `LIGHTNING_DASH`, `TELEPORT_STRIKE`, `DIVE_SLAM`, ручной `LIGHTNING_CALL`, `GRAB_SLAM`; не назначать СКМ на DRAW/HOLSTER. `THUNDER`, `ONSLAUGHT`, `WRATH` — внутренние id, не утверждённые названия способностей. Полный канон — №1–220. Открытые решения остаются открытыми; временные числа/последовательности отмечать `PLACEHOLDER`.
