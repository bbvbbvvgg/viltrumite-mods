# Плейтест: world-events — WARN

- Запуск: 2026-11-09T15:57:50, длительность 1.8 мин, коммит `bc758de0`
- Сервер: выделенный, heap 1024 МБ, мир `fresh`, старт 20.8 с
- Клиент **Alpha**: heap 1200 МБ, вход на сервер 31.3 с
- Проверки: 3 ✅, 0 ❌, 1 ⚠️

## Что не прошло

| # | Раздел | Проверка | Подробности |
|---|---|---|---|
| ⚠️ 4 | Событие trio | trio: без ошибок на клиентах | Exception |

## Метрики

| Точка | TPS | MSPT | Alpha FPS / 1% low / p99 мс | RSS сервер / клиенты, МБ | Свободно, МБ |
|---|---|---|---|---|---|
| before_events | 20.0 | 5.66 | 23.0 / 10.9 / 86.7 | 1125 / 1322 | 1497 |
| trio_active | 20.0 | 6.46 | 1.3 / 0.8 / 1223.4 | 1212 / 1373 | 1386 |
| trio_after | 20.0 | 3.40 | 3.0 / 2.8 / 355.0 | 1212 / 1374 | 1372 |

## Ошибки в логах

- **server**: 6 строк ERROR/исключений, уникальных 6
  - ×1 `java.net.ConnectException: null`
  - ×1 `Caused by: java.net.ConnectException`
  - ×1 `Caused by: java.nio.channels.UnresolvedAddressException`
  - ×1 `[15:56:51] [Yggdrasil Key Fetcher/ERROR] [mojang/YggdrasilServicesKeyInfo]: Failed to request yggdrasil public key`
  - ×1 `Caused by: java.net.UnknownHostException: api.minecraftservices.com`
  - ×1 `[15:56:52] [main/ERROR] [minecraft/DedicatedServerProperties]: No key layers in MapLike[{}]`
- **Alpha**: 12 строк ERROR/исключений, уникальных 9
  - ×4 `Caused by: java.net.UnknownHostException: sessionserver.mojang.com`
  - ×1 `[15:57:30] [Yggdrasil Key Fetcher/ERROR] [mojang/YggdrasilServicesKeyInfo]: Failed to request yggdrasil public key`
  - ×1 `Caused by: java.net.UnknownHostException: api.minecraftservices.com`
  - ×1 `[15:57:33] [Render thread/ERROR] [mojang/Narrator]: Error while loading the narrator`
  - ×1 `Caused by: java.lang.UnsatisfiedLinkError: Unable to load library 'flite':`
  - ×1 `[15:57:34] [Worker-Main-1/ERROR] [minecraft/Util]: Invalid path in pack: viltrumitecore:sounds/regulus/KENNEY_LICENSE.txt, ignoring`
  - ×1 `java.net.ConnectException: null`
  - ×1 `Caused by: java.net.ConnectException`

## Скриншоты

- Событие trio · trio-start · Alpha: [001-trio-start-Alpha.png](shots/001-trio-start-Alpha.png)
- Событие trio · trio-active · Alpha: [002-trio-active-Alpha.png](shots/002-trio-active-Alpha.png)

## Все проверки

| # | Раздел | Проверка | Итог | t, с |
|---|---|---|---|---|
| 1 | Подготовка | dev-команды /playtest включены | ✅ | 0.0 |
| 2 | Событие trio | TPS >= 15 (trio_active) | ✅ | 92.6 |
| 3 | Событие trio | trio: без ошибок на сервере | ✅ | 98.2 |
| 4 | Событие trio | trio: без ошибок на клиентах | ⚠️ | 98.2 |
