# Заметки ручного плейтеста all-heroes-final (2026-10-29)

Полный отчёт: `docs/reports/2026-10-29-all-heroes-integration.md`.

Среда: Forge 1.20.1 runClient, Xvfb :99, llvmpipe, без звука. Мир `sinclair_playtest`. Флаги: thor.qa / jinwoo.qa / dio.qa. Ввод — xdotool (keydown/keyup, mousedown 1/2/3, mousemove_relative), скриншоты `import -window root -crop 854x480+213+160`.

Полезное для следующих проверок:
- Остановить события мира: `/viltrumite event stopall`; список — `/viltrumite event list`. Conquest (Thula/Vidor/Lucan) ломает ландшафт и мешает тестам.
- Выбор героя: `/viltrumite choose`.
- Отладка Тора: `/thor debug hammer throw|recall|state|spawn`, `/thor debug combat melee|state|reset_cooldowns|sky|action <throw|recall>`, `/thor grab <target>`, `/thor fatality <target>`, `/thor debug scene-stop`. В этой сессии вывод `/thor debug … state` в чате не появился — проверить права/регистрацию.
- СКМ Тора: окно захвата > возврат > (молот на поясе: ничего) > враг в 4 блоках → удар > иначе бросок (`ThorControls.hammer`). Для проверки броска отойти дальше MELEE_ENEMY_RADIUS от всех мобов.
- Клавиши Hulkbuster: ЛКМ комбо/удар, держать ЛКМ — заряд, ПКМ захват/бросок, F блок, R/Y/Z/V слоты 1-4, Alt смена страницы (у Iron Man 3 страницы). B (ULTIMATE) — ничего не делает (Jet Ram недоступен).
- F5: первое лицо → сзади → спереди.
- `/kill` с селектором набирать аккуратно: в сессии игрок убил сам себя (fell out of the world).
- DEBUG `Thor move table: client probe ABSENT ? refused` раз в 5 с — норма.
