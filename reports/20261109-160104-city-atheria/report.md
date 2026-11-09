# Плейтест: city-atheria — PASS

- Запуск: 2026-11-09T16:01:04, длительность 1.6 мин, коммит `bc758de0`
- Сервер: выделенный, heap 1024 МБ, мир `fresh`, старт 20.8 с
- Клиент **Alpha**: heap 1200 МБ, вход на сервер 31.3 с
- Проверки: 6 ✅, 0 ❌, 0 ⚠️

## Метрики

| Точка | TPS | MSPT | Alpha FPS / 1% low / p99 мс | RSS сервер / клиенты, МБ | Свободно, МБ |
|---|---|---|---|---|---|
| atheria_empty | 20.0 | 1.21 | 16.7 / 12.5 / 76.8 | 1212 / 1349 | 1400 |
| atheria_populated | 20.0 | 2.28 | 16.5 / 9.6 / 100.9 | 1212 / 1350 | 1397 |
| atheria_after_reset | 20.0 | 1.21 | 17.1 / 8.3 / 98.8 | 1212 / 1350 | 1395 |

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

- Подготовка · atheria-arrive · Alpha: [001-atheria-arrive-Alpha.png](shots/001-atheria-arrive-Alpha.png)
- Город включён · atheria-populated · Alpha: [002-atheria-populated-Alpha.png](shots/002-atheria-populated-Alpha.png)
- Сброс города · atheria-after-reset · Alpha: [003-atheria-after-reset-Alpha.png](shots/003-atheria-after-reset-Alpha.png)

## Все проверки

| # | Раздел | Проверка | Итог | t, с |
|---|---|---|---|---|
| 1 | Подготовка | dev-команды /playtest включены | ✅ | 0.0 |
| 2 | Город включён | TPS >= 15 (atheria_populated) | ✅ | 58.5 |
| 3 | Город включён | город: без ошибок на сервере | ✅ | 58.5 |
| 4 | Сброс города | TPS >= 18 (atheria_after_reset) | ✅ | 95.3 |
| 5 | Сброс города | сброс: без ошибок на сервере | ✅ | 95.4 |
| 6 | Сброс города | сброс: без ошибок на клиентах | ✅ | 95.4 |
