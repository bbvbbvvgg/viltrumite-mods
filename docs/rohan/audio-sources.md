# Heaven's Door — источники звука

Все 16 звуковых событий Рохана (17 файлов) смикшированы скриптом `tools/sfx/rohan.py` из записей Freesound под
лицензией [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) (атрибуция не требуется, указана из
благодарности). Никаких звуков, музыки и реплик из аниме/игр; голоса нет — фразы Рохана идут только субтитрами.

- Поиск: Openverse API (`api.openverse.org`) во время работы отвечал HTTP 504, сайт openverse.org — 403, поэтому записи
  найдены поиском Freesound с фильтром лицензии «Creative Commons 0». Лицензия каждой записи проверена на её странице
  Freesound 2026-11-02; атрибуция составлена по этой странице (не поле `attribution` Openverse).
- Записи «Close Book 1/2» (qubodup) отброшены: в описании сказано, что они сделаны «по образцу» звука из TES III.
- Сырые hq-MP3 превью кешируются в `~/.cache/viltrumite-sfx/rohan` (`python3 tools/sfx/rohan.py --fetch`) и не
  коммитятся. Обработка: обрезка по найденной атаке, ресемплинг для высоты, реверс (dismiss), FFT-фильтры, слои,
  фейды, нормализация пика; моно OGG Vorbis q5, 44.1 кГц → `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/rohan/*.ogg.b64`.
- Регистрация: `src/main/sound-overlays/rohan.json` (категория `player`, субтитры `subtitles.viltrumitecore.rohan.*`
  во всех 12 локалях), события — `hero/rohan/RohanSounds.java`. В JAR также кладётся
  `assets/viltrumitecore/sounds/rohan/SOURCES.md`.
- `python3 tools/sfx/rohan.py --check` пересобирает всё и падает при любом расхождении байтов.
- Прослушивания на слух не было (в среде нет вывода звука): проверены длительность, пики и RMS. Финальный баланс
  громкости нужно послушать в игре.

## События

| Событие | Когда звучит | Слои | Длина |
|---|---|---|---|
| `rohan.door_summon` | Heaven's Door appears (DOOR / SKETCH / MANUSCRIPT start), flutter of pages + soft shimmer | flutter ([468113](https://freesound.org/s/468113)), shimmer ([632344](https://freesound.org/s/632344)), whoosh ([449996](https://freesound.org/s/449996)) | 1.10 s |
| `rohan.door_touch` | the stand's finger touches the target, the face opens like a book | bookhit ([424479](https://freesound.org/s/424479)), shimmer ([632344](https://freesound.org/s/632344)), turn ([457767](https://freesound.org/s/457767)) | 0.70 s |
| `rohan.dismiss` | the stand fades out (reversed flutter) | flutter ([468113](https://freesound.org/s/468113)), shimmer ([632344](https://freesound.org/s/632344)) | 0.90 s |
| `rohan.book_open` | a target becomes a book: pages fan out | flutter ([468113](https://freesound.org/s/468113)), page2 ([119127](https://freesound.org/s/119127)) | 0.90 s |
| `rohan.page_flip` | page flip in the reading GUI and on the 3D book (2 variants) | page2 ([119127](https://freesound.org/s/119127)), page3 ([119128](https://freesound.org/s/119128)) | 0.75 s / 0.75 s |
| `rohan.write` | an instruction is written | pen ([337087](https://freesound.org/s/337087)) | 0.90 s |
| `rohan.book_close` | the book closes (end of window / reader closes) | bookhit ([424479](https://freesound.org/s/424479)), close ([156927](https://freesound.org/s/156927)) | 0.60 s |
| `rohan.page_rip` | a page is torn out (canon) | tear ([489968](https://freesound.org/s/489968)) | 1.00 s |
| `rohan.sketch` | air sketch drawn in front of Rohan | pen ([337087](https://freesound.org/s/337087)), scribble ([257518](https://freesound.org/s/257518)) | 1.10 s |
| `rohan.nib_throw` | pen nibs thrown | whoosh ([449996](https://freesound.org/s/449996)), woosh ([683101](https://freesound.org/s/683101)) | 0.40 s |
| `rohan.nib_hit` | a nib hits | knife ([259563](https://freesound.org/s/259563)), stab ([344404](https://freesound.org/s/344404)) | 0.40 s |
| `rohan.counter` | 'Faster than a punch': the page snaps up and stops the hit | folder ([178056](https://freesound.org/s/178056)), whoosh ([449996](https://freesound.org/s/449996)) | 0.70 s |
| `rohan.whoosh` | self-edit 'fly backwards' push | whoosh ([449996](https://freesound.org/s/449996)), woosh ([683101](https://freesound.org/s/683101)) | 0.80 s |
| `rohan.manuscript` | the manuscript is shown to everyone around | shake ([119847](https://freesound.org/s/119847)), sparkle ([844398](https://freesound.org/s/844398)) | 2.00 s |
| `rohan.resist` | a strong target resists / reflects (pages shake) | rumble ([431390](https://freesound.org/s/431390)), shake ([119847](https://freesound.org/s/119847)) | 1.30 s |
| `rohan.refuse` | a write is refused (lock, tier, cooldown) / 'But I refuse' subtitle | bookhit ([424479](https://freesound.org/s/424479)), folder ([178056](https://freesound.org/s/178056)) | 0.60 s |

## Записи

| Ключ | Запись | Лицензия |
|---|---|---|
| page2 | [esperri — Book Turn Page 2.wav](https://freesound.org/people/esperri/sounds/119127/) | CC0 1.0 |
| page3 | [esperri — Book Turn Page 3.wav](https://freesound.org/people/esperri/sounds/119128/) | CC0 1.0 |
| turn | [partheeban — turnPage.mp3](https://freesound.org/people/partheeban/sounds/457767/) | CC0 1.0 |
| flutter | [Aquafeniz — Flutter.wav](https://freesound.org/people/Aquafeniz/sounds/468113/) | CC0 1.0 |
| close | [tia666 — bkClose.wav](https://freesound.org/people/tia666/sounds/156927/) | CC0 1.0 |
| tear | [falcospizaetus — TearingPaper11](https://freesound.org/people/falcospizaetus/sounds/489968/) | CC0 1.0 |
| pen | [Anthousai — writing - pen - wide 01.wav](https://freesound.org/people/Anthousai/sounds/337087/) | CC0 1.0 |
| scribble | [waldram — Scribble_short.wav](https://freesound.org/people/waldram/sounds/257518/) | CC0 1.0 |
| woosh | [florianreichelt — quick woosh](https://freesound.org/people/florianreichelt/sounds/683101/) | CC0 1.0 |
| whoosh | [DJT4NN3R — whoosh_short_mid.wav](https://freesound.org/people/DJT4NN3R/sounds/449996/) | CC0 1.0 |
| knife | [nicktermer — knife throw.wav](https://freesound.org/people/nicktermer/sounds/259563/) | CC0 1.0 |
| stab | [jawbutch — Knife Stab Melon.wav](https://freesound.org/people/jawbutch/sounds/344404/) | CC0 1.0 |
| shimmer | [adh.dreaming — Shimmer](https://freesound.org/people/adh.dreaming/sounds/632344/) | CC0 1.0 |
| bookhit | [Shroke_Makee — Book Hit](https://freesound.org/people/Shroke_Makee/sounds/424479/) | CC0 1.0 |
| folder | [Eneasz — folder snapped shut.wav](https://freesound.org/people/Eneasz/sounds/178056/) | CC0 1.0 |
| sparkle | [SkySpeira — Mysterious Sparkle Flourish](https://freesound.org/people/SkySpeira/sounds/844398/) | CC0 1.0 |
| rumble | [epicdude959 — Short Increasing Rumble](https://freesound.org/people/epicdude959/sounds/431390/) | CC0 1.0 |
| shake | [kbnevel — Paper_Shake.aif](https://freesound.org/people/kbnevel/sounds/119847/) | CC0 1.0 |
