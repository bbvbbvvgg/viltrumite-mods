# План реализации: Сон Джину

Спека: `docs/design/2026-10-28-jinwoo-design.md`. Ветка `feat/hero-sung-jinwoo` от `feat/heroes-wave-base`
(PR #65). Каждый завершённый шаг — отдельный коммит и push.

## Изоляция и общие точки
Код героя: `hero/jinwoo/`, `client/jinwoo/`, `entity/jinwoo/`, ассеты `*/jinwoo/`, генераторы
`tools/assets/jinwoo_*.py`, `tools/sfx/jinwoo.py`. Минимальные правки общих файлов (возможные конфликты
с параллельными ветками героев — дописывание в конец):

| Общий файл | Правка |
|---|---|
| `hero/HeroId` | `JINWOO("jinwoo")` в конец |
| `hero/HeroAction` | 8 значений в конец (`DAGGER_RUSH`, `SHADOW_EXTRACTION`, `MONARCH_DOMAIN`, `ARMY_SUMMON`, `ARMY_ATTACK`, `ARMY_GUARD`, `ARMY_SENTINEL`, `MONARCH_CHARGE`); остальные входы переиспользуют общие (`PRIMARY_ATTACK`, `SECONDARY_USE`, `MODE_CYCLE`, `STEP_DASH`, `GRAB_THROW`, `DASH_BEHIND`). Итого 50 из 64 бит маски `availableActions` — при слиянии с другими героями волны проверить `actionCountFitsMask` |
| `hero/HeroRegistry.registerDefaults` | одна строка регистрации |
| `network/CoreMessages` | один пакет `JinwooFxS2CPacket` в конец; `PROTOCOL_VERSION` не меняется |
| `entity/ViltrumiteEntities` | `SHADOW_SOLDIER` |
| `ViltrumiteCore` / `ViltrumiteCoreClient` | `JinwooSounds.init()`, `JinwooClient.init()` |
| `client/gui/RaceSelectionScreen` | кнопка + 3D-голова |
| `assets/viltrumitecore/sounds.json`, 12 lang-файлов | ключи `jinwoo.*` |

Версия core (1.50.0) и протокол ("11") не меняются (условие задачи). Новый пакет в конце списка
требует одинаковых клиента и сервера той же сборки — при слиянии волны это нужно учесть (бамп протокола
делает тот, кто сливает).

## Шаги
1. Спека и план (этот документ).
2. Сервер: `JinwooRules` (чистые числа/функции + тесты), `JinwooState`, `JinwooAbilities` (панель),
   `JinwooHero`, `JinwooLines`, способности по классам, ростер армии, останки.
3. Сущность тени: `ShadowSoldierEntity`, `ShadowKind`, цели/строй/страж, атрибуты, лимиты, очистка.
4. Ассеты: генераторы модели Джину (тело, FP-руки, кинжалы), 4 видов теней, клипов, текстур, иконок;
   офлайн-превью кадров клипов.
5. Клиент: рендер тела (TP/FP), рендер теней (LOD), VFX (следы кинжалов, дым, лужи, Владения,
   послеобразы, нити телекинеза), камера ульты, HUD маны, стиль панели, портрет, превью головы.
6. Звуки: синтез семейств, упаковка, `sounds.json`, `jinwoo_sources.md`, `CREDITS.md`.
7. Lang ×12.
8. Проверки: `./gradlew build`, `check_hero_resources.py --hero jinwoo`, `git diff --check`.
9. Плейтест в `runClient` (Xvfb + llvmpipe, звук через OpenAL wave-writer): сценарии из спеки §11,
   кадры и видео; исправления найденного.
10. PR в `feat/heroes-wave-base`.
