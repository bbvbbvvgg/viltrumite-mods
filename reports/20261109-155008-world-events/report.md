# Плейтест: world-events — PASS

- Запуск: 2026-11-09T15:50:08, длительность 1.8 мин, коммит `bc758de0`
- Сервер: выделенный, heap 1024 МБ, мир `fresh`, старт 19.2 с
- Клиент **Alpha**: heap 1200 МБ, вход на сервер 30.1 с
- Проверки: 4 ✅, 0 ❌, 0 ⚠️

## Метрики

| Точка | TPS | MSPT | Alpha FPS / 1% low / p99 мс | RSS сервер / клиенты, МБ | Свободно, МБ |
|---|---|---|---|---|---|
| before_events | 20.0 | 3.08 | 29.2 / 18.9 / 50.8 | 1209 / 1387 | 1350 |
| conquest_active | 20.0 | 3.54 | 27.8 / 13.1 / 67.7 | 1212 / 1390 | 1329 |
| conquest_after | 20.0 | 3.34 | 17.3 / 10.3 / 96.0 | 1200 / 1376 | 1331 |

## Ошибки в логах

- **server**: 6 строк ERROR/исключений, уникальных 6
  - ×1 `java.net.ConnectException: null`
  - ×1 `Caused by: java.net.ConnectException`
  - ×1 `Caused by: java.nio.channels.UnresolvedAddressException`
  - ×1 `[15:46:05] [Yggdrasil Key Fetcher/ERROR] [mojang/YggdrasilServicesKeyInfo]: Failed to request yggdrasil public key`
  - ×1 `Caused by: java.net.UnknownHostException: api.minecraftservices.com`
  - ×1 `[15:46:06] [main/ERROR] [minecraft/DedicatedServerProperties]: No key layers in MapLike[{}]`
- **Alpha**: 9 строк ERROR/исключений, уникальных 9
  - ×1 `[15:46:42] [Yggdrasil Key Fetcher/ERROR] [mojang/YggdrasilServicesKeyInfo]: Failed to request yggdrasil public key`
  - ×1 `Caused by: java.net.UnknownHostException: api.minecraftservices.com`
  - ×1 `[15:46:46] [Render thread/ERROR] [mojang/Narrator]: Error while loading the narrator`
  - ×1 `Caused by: java.lang.UnsatisfiedLinkError: Unable to load library 'flite':`
  - ×1 `[15:46:46] [Worker-Main-1/ERROR] [minecraft/Util]: Invalid path in pack: viltrumitecore:sounds/regulus/KENNEY_LICENSE.txt, ignoring`
  - ×1 `java.net.ConnectException: null`
  - ×1 `Caused by: java.net.ConnectException`
  - ×1 `Caused by: java.nio.channels.UnresolvedAddressException`

## Скриншоты

- Событие conquest · conquest-start · Alpha: [001-conquest-start-Alpha.png](shots/001-conquest-start-Alpha.png)
- Событие conquest · conquest-active · Alpha: [002-conquest-active-Alpha.png](shots/002-conquest-active-Alpha.png)

## Все проверки

| # | Раздел | Проверка | Итог | t, с |
|---|---|---|---|---|
| 1 | Подготовка | dev-команды /playtest включены | ✅ | 0.0 |
| 2 | Событие conquest | TPS >= 15 (conquest_active) | ✅ | 92.4 |
| 3 | Событие conquest | conquest: без ошибок на сервере | ✅ | 97.8 |
| 4 | Событие conquest | conquest: без ошибок на клиентах | ✅ | 97.8 |
