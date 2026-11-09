# Плейтест: smoke-heroes — PASS

- Запуск: 2026-11-09T15:47:20, длительность 2.4 мин, коммит `bc758de0`
- Сервер: выделенный, heap 1024 МБ, мир `fresh`, старт 19.2 с
- Клиент **Alpha**: heap 1200 МБ, вход на сервер 30.1 с
- Проверки: 22 ✅, 0 ❌, 0 ⚠️

## Метрики

| Точка | TPS | MSPT | Alpha FPS / 1% low / p99 мс | RSS сервер / клиенты, МБ | Свободно, МБ |
|---|---|---|---|---|---|
| jinwoo | 20.0 | 4.15 | 21.1 / 7.5 / 120.1 | 1205 / 1320 | 1416 |
| captain | 20.0 | 5.63 | 16.7 / 4.1 / 210.7 | 1207 / 1343 | 1385 |
| red_hulk | 20.0 | 4.14 | 23.6 / 13.4 / 71.0 | 1208 / 1349 | 1379 |
| ghost_rider | 20.0 | 3.65 | 22.6 / 9.6 / 96.6 | 1209 / 1356 | 1386 |
| venom | 20.0 | 3.03 | 30.4 / 17.2 / 51.2 | 1209 / 1385 | 1338 |

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

- Герой jinwoo · jinwoo · Alpha: [001-jinwoo-Alpha.png](shots/001-jinwoo-Alpha.png)
- Герой captain · captain · Alpha: [002-captain-Alpha.png](shots/002-captain-Alpha.png)
- Герой red_hulk · red_hulk · Alpha: [003-red_hulk-Alpha.png](shots/003-red_hulk-Alpha.png)
- Герой ghost_rider · ghost_rider · Alpha: [004-ghost_rider-Alpha.png](shots/004-ghost_rider-Alpha.png)
- Герой venom · venom · Alpha: [005-venom-Alpha.png](shots/005-venom-Alpha.png)

## Все проверки

| # | Раздел | Проверка | Итог | t, с |
|---|---|---|---|---|
| 1 | Подготовка | dev-команды /playtest включены | ✅ | 0.0 |
| 2 | Подготовка | список героев | ✅ | 0.0 |
| 3 | Герой jinwoo | jinwoo: вход в героя | ✅ | 1.7 |
| 4 | Герой jinwoo | jinwoo: сервер видит героя | ✅ | 6.4 |
| 5 | Герой jinwoo | jinwoo: без ошибок на сервере | ✅ | 29.7 |
| 6 | Герой jinwoo | jinwoo: без ошибок на клиентах | ✅ | 29.7 |
| 7 | Герой captain | captain: вход в героя | ✅ | 29.7 |
| 8 | Герой captain | captain: сервер видит героя | ✅ | 34.4 |
| 9 | Герой captain | captain: без ошибок на сервере | ✅ | 57.8 |
| 10 | Герой captain | captain: без ошибок на клиентах | ✅ | 57.8 |
| 11 | Герой red_hulk | red_hulk: вход в героя | ✅ | 57.8 |
| 12 | Герой red_hulk | red_hulk: сервер видит героя | ✅ | 62.5 |
| 13 | Герой red_hulk | red_hulk: без ошибок на сервере | ✅ | 85.6 |
| 14 | Герой red_hulk | red_hulk: без ошибок на клиентах | ✅ | 85.6 |
| 15 | Герой ghost_rider | ghost_rider: вход в героя | ✅ | 85.6 |
| 16 | Герой ghost_rider | ghost_rider: сервер видит героя | ✅ | 90.3 |
| 17 | Герой ghost_rider | ghost_rider: без ошибок на сервере | ✅ | 113.4 |
| 18 | Герой ghost_rider | ghost_rider: без ошибок на клиентах | ✅ | 113.4 |
| 19 | Герой venom | venom: вход в героя | ✅ | 113.4 |
| 20 | Герой venom | venom: сервер видит героя | ✅ | 118.1 |
| 21 | Герой venom | venom: без ошибок на сервере | ✅ | 141.1 |
| 22 | Герой venom | venom: без ошибок на клиентах | ✅ | 141.1 |
