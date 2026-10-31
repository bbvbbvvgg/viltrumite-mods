#!/usr/bin/env python3
"""Ultimate Hero Expansion roster generator.

Single source of truth for the 48 expansion heroes and their factions. Writes
  - viltrumitecore/.../hero/budget/BudgetRoster.java  (server + client data table)
  - assets/viltrumite_expansion/lang/en_us.json and ru_ru.json

Move types: A punch, B energy beam, C dash, D buff, E ultimate.
Shape (E only): beam or ring. Buff (D only): strength, speed, resistance.
Run from the repository root:  python3 tools/budget/make_budget_roster.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
JAVA = os.path.join(ROOT, "viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/hero/budget/BudgetRoster.java")
LANG = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumite_expansion/lang")
HERO_ID = os.path.join(ROOT, "viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/hero/HeroId.java")
BEGIN = "   // BEGIN ULTIMATE HERO EXPANSION"
END = "   // END ULTIMATE HERO EXPANSION"

# key, ENUM, english, russian, look, moves, scene
# look: skin, hair, shirt, pants, shoes, eyes, face tokens, detail tokens
# move: (type, slug, english, russian, damage, cooldown ticks, color, extra)
H = []


def hero(key, enum, en, ru, look, moves, scene="NONE"):
    H.append(dict(key=key, enum=enum, en=en, ru=ru, look=look, moves=moves, scene=scene))


def look(skin, hair, shirt, pants, shoes, eyes="2b5cd6", face=(), details=()):
    return dict(skin=skin, hair=hair, shirt=shirt, pants=pants, shoes=shoes, eyes=eyes, face=list(face), details=list(details))


PALE, TAN, DARK = "f0c8a0", "d8a878", "8d5a3b"

hero("gojo", "GOJO", "Satoru Gojo", "Сатору Годзё",
     look(PALE, "f4f4f4", "1d1d2b", "1d1d2b", "111111", face=["blindfold:111111"], details=["spiky:f4f4f4"]),
     [("A", "infinity", "Infinity", "Бесконечность", 8, 20, "9b6bff", ""),
      ("B", "blue", "Blue", "Синий", 10, 40, "3d7bff", ""),
      ("D", "six_eyes", "Six Eyes", "Шесть глаз", 0, 300, "9fd8ff", "speed"),
      ("E", "hollow_purple", "Hollow Purple", "Пустотный фиолетовый", 30, 200, "a020f0", "beam")], "GOJO")
hero("sukuna", "SUKUNA", "Ryomen Sukuna", "Рёмен Сукуна",
     look(PALE, "f08aa0", "f2efe6", "f2efe6", "222222", eyes="c01010", face=["marks:202020"], details=["spiky:f08aa0"]),
     [("A", "cursed_strike", "Cursed Strike", "Проклятый удар", 9, 20, "b01030", ""),
      ("B", "dismantle", "Dismantle", "Рассечение", 12, 40, "e01020", ""),
      ("E", "malevolent_shrine", "Malevolent Shrine", "Гробница зла", 34, 240, "c00000", "ring")], "SUKUNA")
hero("itadori", "ITADORI", "Yuji Itadori", "Юдзи Итадори",
     look(PALE, "f39aa8", "1b1f3a", "1b1f3a", "c03030", eyes="5a3a20", details=["spiky:f39aa8"]),
     [("A", "divergent_fist", "Divergent Fist", "Расходящийся кулак", 10, 20, "4a6cff", ""),
      ("C", "sprint", "Sprint", "Рывок", 0, 40, "ffffff", ""),
      ("E", "black_flash", "Black Flash", "Чёрная молния", 22, 160, "200010", "beam")])
hero("megumi", "MEGUMI", "Megumi Fushiguro", "Мэгуми Фусигуро",
     look(PALE, "151515", "1b1f3a", "1b1f3a", "111111", eyes="203050", details=["spiky:151515"]),
     [("B", "divine_dogs", "Divine Dogs", "Божественные псы", 9, 40, "7a7a7a", ""),
      ("C", "shadow_step", "Shadow Step", "Шаг тени", 0, 50, "202020", ""),
      ("D", "ten_shadows", "Ten Shadows", "Десять теней", 0, 300, "303040", "resistance")])
hero("toji", "TOJI", "Toji Fushiguro", "Тодзи Фусигуро",
     look(TAN, "151515", "2a2a2a", "4a4a5a", "111111", eyes="303030", face=["scar:b06060"]),
     [("A", "heavenly_strike", "Heavenly Strike", "Небесный удар", 14, 24, "dddddd", ""),
      ("C", "restriction_dash", "Restriction Dash", "Рывок ограничения", 0, 30, "ffffff", ""),
      ("D", "physical_gift", "Physical Gift", "Физический дар", 0, 300, "cc3333", "strength")])
hero("geto", "GETO", "Suguru Geto", "Сугуру Гэто",
     look(PALE, "151515", "2b2b2b", "2b2b2b", "111111", eyes="303030", details=["bun:151515"]),
     [("B", "cursed_spirit", "Cursed Spirit", "Проклятый дух", 11, 40, "5a1a7a", ""),
      ("D", "manipulation", "Manipulation", "Манипуляция", 0, 300, "6a2a8a", "resistance"),
      ("E", "uzumaki", "Uzumaki", "Узумаки", 28, 220, "3a0a5a", "beam")])
hero("nanami", "NANAMI", "Kento Nanami", "Кэнто Нанами",
     look(PALE, "e8d070", "e8e0b0", "e8e0b0", "6a4a2a", face=["tie:4a6a3a"], details=["glasses:3a2a10"]),
     [("A", "ratio", "Ratio Technique", "Техника 7:3", 16, 30, "f0e080", ""),
      ("D", "overtime", "Overtime", "Сверхурочные", 0, 300, "e0a020", "strength")])
hero("mahito", "MAHITO", "Mahito", "Махито",
     look("b8c8d0", "6a8aa8", "3a4a5a", "3a4a5a", "222222", eyes="80a0c0", face=["stitches:303030"]),
     [("A", "idle_transfiguration", "Idle Transfiguration", "Праздная трансфигурация", 10, 20, "7aa0c0", ""),
      ("C", "soul_shift", "Soul Shift", "Сдвиг души", 0, 40, "aaccee", ""),
      ("E", "self_embodiment", "Self-Embodiment of Perfection", "Воплощение совершенства", 26, 220, "4a7aa0", "ring")])
hero("yuta", "YUTA", "Yuta Okkotsu", "Юта Оккоцу",
     look(PALE, "151515", "f0f0f0", "f0f0f0", "222222", eyes="203040", details=["katana_back:222222"]),
     [("A", "katana_slash", "Katana Slash", "Удар катаной", 11, 20, "e0e0ff", ""),
      ("B", "rika", "Rika", "Рика", 13, 50, "ffffff", ""),
      ("D", "copy", "Copy", "Копирование", 0, 300, "aaaaff", "speed"),
      ("E", "pure_love", "Pure Love", "Чистая любовь", 32, 240, "ff80c0", "beam")])
hero("choso", "CHOSO", "Choso", "Тёсо",
     look(PALE, "151515", "4a3a6a", "2a2a2a", "111111", eyes="602020", face=["blood_line:6a1010"], details=["bun:151515"]),
     [("B", "piercing_blood", "Piercing Blood", "Пронзающая кровь", 15, 50, "8a0a0a", ""),
      ("D", "blood_manipulation", "Blood Manipulation", "Манипуляция кровью", 0, 300, "aa1010", "resistance")])
hero("goku", "GOKU", "Goku", "Гоку",
     look(PALE, "121212", "f07a10", "f07a10", "2040a0", eyes="111111", face=["belt:2050c0"], details=["spiky:121212"]),
     [("A", "ki_punch", "Ki Punch", "Ки-удар", 10, 20, "ffe080", ""),
      ("B", "ki_blast", "Ki Blast", "Ки-взрыв", 12, 30, "80d0ff", ""),
      ("C", "instant_transmission", "Instant Transmission", "Мгновенное перемещение", 0, 40, "ffffff", ""),
      ("E", "kamehameha", "Kamehameha", "Камехамеха", 40, 240, "60c0ff", "beam")], "GOKU")
hero("vegeta", "VEGETA", "Vegeta", "Вегета",
     look(PALE, "121212", "2040b0", "2040b0", "f0f0f0", eyes="111111", face=["armor:f0f0f0"], details=["tall_spiky:121212"]),
     [("B", "galick_gun", "Galick Gun", "Галик-ган", 13, 40, "b060ff", ""),
      ("D", "saiyan_pride", "Saiyan Pride", "Гордость сайяна", 0, 300, "ffe040", "strength"),
      ("E", "final_flash", "Final Flash", "Последняя вспышка", 36, 240, "fff060", "beam")])
hero("naruto", "NARUTO", "Naruto", "Наруто",
     look(PALE, "f8d030", "f07a10", "f07a10", "2040a0", eyes="2060e0", face=["whiskers:5a3a20"], details=["spiky:f8d030", "headband:3060c0"]),
     [("A", "chakra_punch", "Chakra Punch", "Удар чакрой", 8, 16, "ffb040", ""),
      ("C", "shadow_clone_dash", "Shadow Clone Dash", "Рывок теневого клона", 0, 40, "ffffff", ""),
      ("E", "rasengan", "Rasengan", "Расенган", 24, 180, "60a0ff", "beam")])
hero("sasuke", "SASUKE", "Sasuke", "Саске",
     look(PALE, "15152a", "e8e8f0", "3a2a5a", "2a2a2a", eyes="c01010", details=["spiky:15152a", "sword_hip:2a2a2a"]),
     [("B", "chidori", "Chidori", "Чидори", 14, 40, "80e0ff", ""),
      ("C", "lightning_step", "Lightning Step", "Шаг молнии", 0, 40, "c0f0ff", ""),
      ("E", "amaterasu", "Amaterasu", "Аматэрасу", 27, 220, "100010", "beam")])
hero("madara", "MADARA", "Madara", "Мадара",
     look(PALE, "151515", "a01818", "3a1010", "2a2a2a", eyes="c01010", details=["long_hair:151515"]),
     [("A", "gunbai_strike", "Gunbai Strike", "Удар гунбаем", 12, 24, "d02020", ""),
      ("D", "susanoo", "Susanoo", "Сусаноо", 0, 300, "4060ff", "resistance"),
      ("E", "meteor", "Meteor", "Метеор", 38, 260, "ff7020", "ring")])
hero("luffy", "LUFFY", "Luffy", "Луффи",
     look(TAN, "151515", "d01818", "2050c0", "d0a060", eyes="111111", face=["scar:a04040"], details=["straw_hat:e8c860"]),
     [("A", "gum_gum_pistol", "Gum-Gum Pistol", "Резиновый пистолет", 9, 16, "ff8080", ""),
      ("C", "gear_second", "Gear Second", "Второй гир", 0, 30, "ffc0c0", ""),
      ("E", "gum_gum_gatling", "Gum-Gum Gatling", "Резиновый гатлинг", 20, 60, "ff4040", "ring")])
hero("zoro", "ZORO", "Zoro", "Зоро",
     look(TAN, "40a040", "f0f0f0", "202020", "202020", eyes="111111", face=["scar:802020"], details=["sword_hip:202020"]),
     [("A", "three_sword_style", "Three Sword Style", "Санторю", 13, 24, "c0ffc0", ""),
      ("B", "flying_slash", "Flying Slash", "Летящий разрез", 11, 40, "60e060", "")])
hero("sanji", "SANJI", "Sanji", "Санджи",
     look(PALE, "f0d040", "202020", "202020", "101010", eyes="2050c0", face=["tie:101010"]),
     [("A", "diable_jambe", "Diable Jambe", "Дьявольская нога", 12, 20, "ff7010", ""),
      ("C", "sky_walk", "Sky Walk", "Небесная поступь", 0, 40, "ffffff", "")])
hero("saitama", "SAITAMA", "Saitama", "Сайтама",
     look(PALE, PALE, "f0e030", "f0e030", "d02020", eyes="111111", face=["bald", "gloves:d02020", "belt:111111"], details=["cape:f8f8f8"]),
     [("A", "normal_punch", "Normal Punch", "Обычный удар", 40, 30, "ffffff", ""),
      ("C", "serious_sidestep", "Serious Sidestep", "Серьёзный шаг", 0, 40, "ffffff", ""),
      ("E", "serious_punch", "Serious Punch", "Серьёзный удар", 60, 300, "ffffa0", "beam")])
hero("genos", "GENOS", "Genos", "Генос",
     look(PALE, "f0d040", "202020", "202020", "505050", eyes="ffd000", face=["metal_arms:8a8a90"]),
     [("B", "incineration", "Incineration", "Испепеление", 14, 40, "ff8020", ""),
      ("C", "boost", "Boost", "Ускоритель", 0, 30, "ffa040", ""),
      ("D", "upgrade", "Upgrade", "Апгрейд", 0, 300, "a0a0b0", "resistance")])
hero("superman", "SUPERMAN", "Superman", "Супермен",
     look(PALE, "101020", "1a40c0", "1a40c0", "c01818", eyes="2050d0", face=["emblem:d01818", "belt:f0d020", "trunks:c01818"], details=["cape:c01818"]),
     [("A", "super_punch", "Super Punch", "Суперудар", 15, 24, "ffffff", ""),
      ("B", "heat_vision", "Heat Vision", "Тепловое зрение", 13, 40, "ff2010", ""),
      ("D", "solar_power", "Solar Power", "Солнечная сила", 0, 300, "ffe060", "resistance"),
      ("E", "divine_might", "Divine Might", "Божественная мощь", 36, 240, "ff3010", "beam")], "SUPERMAN")
hero("batman", "BATMAN", "Batman", "Бэтмен",
     look(PALE, "151515", "3a3a40", "3a3a40", "151515", eyes="f0f0f0", face=["cowl:151515", "emblem:151515", "belt:e0c020"], details=["ears:151515", "cape:151515"]),
     [("A", "martial_arts", "Martial Arts", "Боевые искусства", 8, 16, "a0a0a0", ""),
      ("B", "batarang", "Batarang", "Бэтаранг", 7, 30, "404040", ""),
      ("C", "grapple", "Grapple", "Гарпун", 0, 40, "808080", "")])
hero("flash", "FLASH", "Flash", "Флэш",
     look(PALE, "c01010", "c01010", "c01010", "f0d020", eyes="f0f0f0", face=["cowl:c01010", "emblem:f0d020", "belt:f0d020"], details=["bolts:f0d020"]),
     [("A", "speed_punch", "Speed Punch", "Скоростной удар", 7, 10, "ffe040", ""),
      ("C", "speed_force", "Speed Force", "Сила скорости", 0, 10, "fff080", ""),
      ("D", "lightning_speed", "Lightning Speed", "Молниеносность", 0, 200, "ffd020", "speed")])
hero("green_lantern", "GREEN_LANTERN", "Green Lantern", "Зелёный Фонарь",
     look(PALE, "3a2a1a", "20a040", "202020", "20a040", eyes="f0f0f0", face=["domino:20a040", "emblem:f0f0f0"], details=["ring:30ff60"]),
     [("B", "lantern_beam", "Lantern Beam", "Луч фонаря", 12, 30, "30ff60", ""),
      ("D", "willpower", "Willpower", "Сила воли", 0, 300, "40e070", "resistance"),
      ("E", "lanterns_oath", "Lantern's Oath", "Клятва фонаря", 25, 200, "20ff50", "beam")])
hero("aquaman", "AQUAMAN", "Aquaman", "Аквамен",
     look(TAN, "e0b040", "f09020", "209050", "209050", eyes="20a0a0", details=["long_hair:e0b040"]),
     [("A", "trident_strike", "Trident Strike", "Удар трезубцем", 13, 24, "40e0e0", ""),
      ("B", "tidal_wave", "Tidal Wave", "Приливная волна", 10, 40, "30a0ff", "")])
hero("spider_man", "SPIDER_MAN", "Spider-Man", "Человек-паук",
     look("d01818", "d01818", "d01818", "1a40c0", "d01818", eyes="f0f0f0", face=["mask:d01818", "emblem:101010"]),
     [("A", "web_punch", "Web Punch", "Удар с паутиной", 8, 16, "ffffff", ""),
      ("B", "web_shot", "Web Shot", "Выстрел паутиной", 5, 20, "f0f0f0", ""),
      ("C", "web_swing", "Web Swing", "Взмах на паутине", 0, 30, "ffffff", "")])
hero("deadpool", "DEADPOOL", "Deadpool", "Дэдпул",
     look("b01818", "b01818", "b01818", "b01818", "202020", eyes="f0f0f0", face=["mask:b01818", "patches:202020", "belt:202020"], details=["katana_back:303030"]),
     [("A", "katana_combo", "Katana Combo", "Комбо катанами", 11, 20, "d0d0d0", ""),
      ("B", "pistols", "Pistols", "Пистолеты", 9, 24, "404040", ""),
      ("D", "regeneration", "Regeneration", "Регенерация", 0, 300, "ff4040", "resistance")])
hero("wolverine", "WOLVERINE", "Wolverine", "Росомаха",
     look(TAN, "151515", "f0d020", "1a40c0", "1a40c0", eyes="303030", face=["sideburns:151515", "belt:d01818"], details=["claws:d0d0d8"]),
     [("A", "claw_slash", "Claw Slash", "Удар когтями", 12, 20, "e0e0f0", ""),
      ("C", "berserk_leap", "Berserk Leap", "Прыжок берсерка", 0, 40, "ffffff", ""),
      ("D", "healing_factor", "Healing Factor", "Исцеляющий фактор", 0, 300, "ffd040", "resistance")])
hero("kratos", "KRATOS", "Kratos", "Кратос",
     look("e8e4dc", "e8e4dc", "e8e4dc", "6a3a1a", "4a2a10", eyes="d0a020", face=["bald", "tattoo:b01010", "beard:3a2a1a"]),
     [("A", "blades_of_chaos", "Blades of Chaos", "Клинки хаоса", 14, 24, "ff6010", ""),
      ("B", "leviathan_throw", "Leviathan Throw", "Бросок Левиафана", 12, 40, "a0e0ff", ""),
      ("D", "spartan_rage", "Spartan Rage", "Ярость спартанца", 0, 300, "ff2010", "strength"),
      ("E", "god_slayer", "God Slayer", "Богоубийца", 34, 240, "ff4000", "ring")])
hero("shrek", "SHREK", "Shrek", "Шрек",
     look("8ab830", "8ab830", "f0e8c8", "6a4a2a", "4a3018", eyes="6a4a20", face=["bald", "vest:5a3a1a"], details=["ogre_ears:8ab830"]),
     [("A", "ogre_slap", "Ogre Slap", "Шлепок огра", 10, 20, "8ab830", ""),
      ("D", "swamp_power", "Swamp Power", "Сила болота", 0, 300, "6a8a20", "strength"),
      ("E", "ogre_roar", "Ogre Roar", "Рёв огра", 18, 200, "a0d040", "ring")], "SHREK")

# ---- MULTIVERSE EXPANSION PHASE II (heroes 31-36) ----------------------
hero("aizen", "AIZEN", "Sosuke Aizen", "Сосукэ Айдзен",
     look(PALE, "5a3a20", "f0f0f0", "f0f0f0", "202020", eyes="6a4a2a", details=["sword_hip:202020"]),
     [("A", "reiatsu_strike", "Reiatsu Strike", "Удар реяцу", 13, 24, "8a40d0", ""),
      ("B", "black_coffin", "Hado #90: Black Coffin", "Хадо №90: Чёрный гроб", 15, 50, "301040", ""),
      ("D", "kyoka_suigetsu", "Kyoka Suigetsu", "Кёка Суйгэцу", 0, 300, "b070ff", "resistance"),
      ("E", "hogyoku_ascension", "Hogyoku Ascension", "Вознесение Хогёку", 35, 240, "a020f0", "ring")], "AIZEN")
hero("ichigo", "ICHIGO", "Ichigo Kurosaki", "Ичиго Куросаки",
     look(PALE, "ff8020", "151515", "151515", "f0f0f0", eyes="5a3a20", details=["spiky:ff8020", "katana_back:151515"]),
     [("B", "getsuga_tensho", "Getsuga Tensho", "Гецуга Тэнсё", 14, 40, "60a0ff", ""),
      ("C", "shunpo", "Shunpo", "Сюнпо", 0, 30, "ffffff", ""),
      ("D", "bankai", "Bankai: Tensa Zangetsu", "Банкай: Тэнса Дзангэцу", 0, 300, "202030", "speed"),
      ("E", "mugetsu", "Mugetsu", "Мугэцу", 40, 260, "100010", "beam")], "ICHIGO")
hero("dante", "DANTE", "Dante", "Данте",
     look(PALE, "f0f0f0", "a01818", "2a2a2a", "3a2a1a", eyes="4080d0", details=["spiky:f0f0f0", "cape:a01818", "big_sword:c0c0c8"]),
     [("A", "rebellion", "Rebellion Slash", "Удар Ребеллиона", 13, 20, "e0e0f0", ""),
      ("B", "ebony_ivory", "Ebony & Ivory", "Эбони и Айвори", 8, 16, "ffd060", ""),
      ("D", "devil_trigger", "Devil Trigger", "Триггер дьявола", 0, 300, "e02020", "strength")])
hero("vergil", "VERGIL", "Vergil", "Вергилий",
     look(PALE, "e8e8f0", "2040a0", "202020", "202020", eyes="4080d0", details=["cape:2040a0", "sword_hip:303030"]),
     [("A", "yamato_slash", "Yamato Slash", "Удар Ямато", 12, 20, "80c0ff", ""),
      ("B", "judgement_cut", "Judgement Cut", "Разрез правосудия", 14, 40, "4060ff", ""),
      ("C", "air_trick", "Air Trick", "Воздушный трюк", 0, 30, "a0c0ff", ""),
      ("E", "judgement_cut_end", "Judgement Cut End", "Конец правосудия", 38, 260, "3050ff", "ring")], "VERGIL")
hero("guts", "GUTS", "Guts", "Гатс",
     look(TAN, "151515", "2a2a30", "2a2a30", "1a1a1a", eyes="303030", face=["scar:a04040", "metal_arms:6a6a70"], details=["big_sword:8a8a90"]),
     [("A", "dragon_slayer", "Dragon Slayer", "Убийца драконов", 20, 40, "b0b0b8", ""),
      ("B", "cannon_arm", "Cannon Arm", "Рука-пушка", 12, 60, "ff8030", ""),
      ("D", "berserker_armor", "Berserker Armor", "Доспех берсерка", 0, 300, "601010", "strength")])
hero("sans", "SANS", "Sans", "Санс",
     look("f0f0f0", "f0f0f0", "2a50b0", "202020", "ff80c0", eyes="f0f0f0", face=["bald"]),
     [("A", "bone_attack", "Bone Attack", "Костяная атака", 1, 10, "f0f0f0", ""),
      ("C", "shortcut", "Shortcut", "Короткий путь", 0, 20, "60c0ff", ""),
      ("E", "gaster_blaster", "Gaster Blaster", "Гастер-бластер", 30, 200, "ffffff", "beam")])
# ---- ULTIMATE MULTIVERSE EXPANSION PHASE II (heroes 37-48) -------------
hero("eren", "EREN", "Eren Yeager", "Эрен Йегер",
     look(PALE, "3a2a1a", "6a4a2a", "e8e0d0", "3a2a1a", eyes="30a080", details=["bun:3a2a1a"]),
     [("A", "titan_punch", "Attack Titan Punch", "Удар Атакующего титана", 16, 30, "d08060", ""),
      ("C", "odm_gear", "ODM Gear", "УПМ", 0, 30, "c0c0c0", ""),
      ("D", "titan_hardening", "Titan Hardening", "Затвердение титана", 0, 300, "a0e0ff", "resistance"),
      ("E", "the_rumbling", "The Rumbling", "Гул земли", 45, 300, "c06030", "ring")], "EREN")
hero("levi", "LEVI", "Levi Ackerman", "Леви Аккерман",
     look(PALE, "151515", "6a4a2a", "e8e0d0", "3a2a1a", eyes="6a6a70", face=["tie:f0f0f0"], details=["cape:2a6a3a"]),
     [("A", "spinning_slash", "Spinning Slash", "Вращающийся разрез", 15, 20, "e0e0e0", ""),
      ("C", "odm_maneuver", "ODM Maneuver", "Манёвр УПМ", 0, 20, "c0c0c0", ""),
      ("D", "ackerman_power", "Ackerman Awakening", "Пробуждение Аккерманов", 0, 300, "e0e0ff", "speed")])
hero("denji", "DENJI", "Denji", "Дэндзи",
     look(PALE, "e8c040", "f0f0f0", "151515", "3a2a1a", eyes="8a6a30", face=["tie:151515"], details=["chainsaw:c0c0c0"]),
     [("A", "chainsaw_rip", "Chainsaw Rip", "Разрыв бензопилой", 14, 20, "e02020", ""),
      ("C", "pull_cord", "Pull Cord", "Дёрнуть шнур", 0, 40, "ffa040", ""),
      ("D", "blood_frenzy", "Blood Frenzy", "Кровавое безумие", 0, 300, "c01010", "strength")])
hero("makima", "MAKIMA", "Makima", "Макима",
     look(PALE, "c86040", "f0f0f0", "151515", "151515", eyes="e0c020", face=["tie:151515"], details=["long_hair:c86040"]),
     [("B", "bang", "Bang", "Бах", 18, 40, "e01010", ""),
      ("D", "control_devil", "Control Devil", "Демон контроля", 0, 300, "e0c020", "resistance"),
      ("E", "domination", "Domination", "Господство", 30, 240, "a01010", "ring")])
hero("power", "POWER", "Power", "Пауэр",
     look(PALE, "f0a0b0", "f0f0f0", "151515", "151515", eyes="e0a020", face=["tie:151515"], details=["long_hair:f0a0b0", "horns:c01010"]),
     [("A", "blood_hammer", "Blood Hammer", "Кровавый молот", 17, 30, "c01010", ""),
      ("B", "blood_spear", "Blood Spear", "Кровавое копьё", 12, 30, "e01020", ""),
      ("D", "greatest", "I Am The Greatest", "Я величайшая", 0, 300, "ff4060", "strength")])
hero("alucard", "ALUCARD", "Alucard", "Алукард",
     look(PALE, "151515", "a01818", "151515", "151515", eyes="e01010", details=["wide_hat:a01818", "glasses:e08020", "cape:a01818"]),
     [("B", "jackal", "Jackal", "Шакал", 13, 24, "c0c0c0", ""),
      ("C", "shadow_form", "Shadow Form", "Теневая форма", 0, 40, "300010", ""),
      ("D", "release_restraint", "Release Restraint: Level Zero", "Снятие печати: нулевой уровень", 0, 300, "e01010", "strength"),
      ("E", "familiars", "Hellhound Familiars", "Адские фамильяры", 32, 240, "200010", "ring")])
hero("rimuru", "RIMURU", "Rimuru Tempest", "Римуру Темпест",
     look(PALE, "70b0e0", "202030", "202030", "151515", eyes="e0b020", details=["long_hair:70b0e0"]),
     [("B", "water_blade", "Water Blade", "Водяной клинок", 12, 30, "60b0ff", ""),
      ("D", "predator", "Predator", "Хищник", 0, 300, "4080d0", "resistance"),
      ("E", "beelzebuth", "Beelzebuth", "Вельзевул", 34, 260, "3040a0", "ring")])
hero("ainz", "AINZ", "Ainz Ooal Gown", "Айнз Оал Гоун",
     look("e8e4d0", "e8e4d0", "2a1040", "2a1040", "151515", eyes="e01010", face=["bald", "armor:d0b040"], details=["horns:d0b040", "cape:2a1040"]),
     [("B", "grasp_heart", "Grasp Heart", "Хватка сердца", 16, 50, "6a2a8a", ""),
      ("D", "dark_wisdom", "Dark Wisdom", "Тёмная мудрость", 0, 300, "8a40d0", "resistance"),
      ("E", "fallen_down", "Fallen Down", "Падение небес", 50, 300, "f0f0ff", "beam")], "AINZ")
hero("shadow", "SHADOW", "Cid Kagenou / Shadow", "Сид Кагэно / Шэдоу",
     look(PALE, "151515", "151515", "151515", "151515", eyes="c01010", face=["domino:202020"], details=["cape:151515", "sword_hip:202020"]),
     [("A", "slime_sword", "Slime Sword", "Слизневой меч", 13, 20, "6a40c0", ""),
      ("C", "eminence_step", "Eminence Step", "Шаг Тени", 0, 30, "4a2a8a", ""),
      ("E", "i_am_atomic", "I Am Atomic", "Я атомный", 45, 300, "9040ff", "ring")], "SHADOW")
hero("mob", "MOB", "Mob", "Моб",
     look(PALE, "151515", "151515", "151515", "151515", eyes="151515"),
     [("B", "psychic_blast", "Psychic Blast", "Психический взрыв", 12, 30, "c060ff", ""),
      ("D", "hundred_percent", "100%", "100%", 0, 300, "e080ff", "strength"),
      ("E", "question_percent", "???%", "???%", 48, 300, "ffffff", "ring")])
hero("escanor", "ESCANOR", "Escanor", "Эсканор",
     look(TAN, "151515", "f0f0f0", "6a4a2a", "3a2a1a", eyes="4a3a20", face=["beard:151515"], details=["big_sword:e0a020"]),
     [("A", "rhitta_strike", "Divine Axe Rhitta", "Божественный топор Рита", 18, 30, "ffb020", ""),
      ("D", "sunshine", "Sunshine", "Солнечный свет", 0, 300, "ffd040", "strength"),
      ("E", "cruel_sun", "Cruel Sun", "Жестокое солнце", 42, 280, "ff8010", "ring")], "ESCANOR")
hero("meliodas", "MELIODAS", "Meliodas", "Мелиодас",
     look(PALE, "f0d040", "f0f0f0", "202020", "202020", eyes="30a040", face=["marks:151515"], details=["spiky:f0d040", "sword_hip:3a3a3a"]),
     [("A", "full_counter", "Full Counter", "Полный контрудар", 14, 24, "a0a0ff", ""),
      ("B", "hellblaze", "Hellblaze", "Адское пламя", 13, 40, "202020", ""),
      ("D", "demon_mode", "Demon Mode", "Режим демона", 0, 300, "303030", "strength"),
      ("E", "revenge_counter", "Revenge Counter", "Ответный удар мести", 36, 240, "8080ff", "beam")])

# Five-faction war system: every playable hero (including the historical ones,
# matched by key, never by HeroId constant) belongs to one faction.
FACTION = {}
for _f, _keys in {
    "ANIME": ["itadori", "megumi", "yuta", "nanami", "goku", "vegeta", "naruto", "sasuke", "luffy", "zoro", "sanji",
              "genos", "ichigo", "levi", "denji", "rimuru", "mob"],
    "COMICS": ["superman", "batman", "flash", "green_lantern", "aquaman", "spider_man", "wolverine"],
    "DEMONS": ["sukuna", "geto", "mahito", "choso", "dante", "vergil", "makima", "power", "alucard", "ainz", "meliodas"],
    "GODS": ["gojo", "saitama", "kratos", "aizen", "escanor"],
    "CHAOS": ["toji", "madara", "deadpool", "shrek", "guts", "sans", "eren", "shadow"],
}.items():
    for _k in _keys:
        FACTION[_k] = _f

assert len(H) == 48, len(H)
assert len({h['key'] for h in H}) == 48 and all(len(h['key']) <= 32 for h in H)
assert all(h['key'] in FACTION for h in H), [h['key'] for h in H if h['key'] not in FACTION]
assert all(len({m[0] for m in h['moves']}) == len(h['moves']) for h in H), "one move per mechanic"

TYPE_EN = {"A": "Strike", "B": "Energy projectile", "C": "Dash", "D": "Power-up", "E": "Ultimate"}
TYPE_RU = {"A": "Удар", "B": "Энергетический снаряд", "C": "Рывок", "D": "Усиление", "E": "Ультимейт"}
BUFF_RU = {"strength": "сила", "speed": "скорость", "resistance": "сопротивление"}
SCENE_LINES = {
    "GOJO": ("Throughout heaven and earth, I alone am the honored one.", "Небеса и земля... лишь я достоин почитания."),
    "SUKUNA": ("Know your place, fool.", "Знай своё место, глупец."),
    "GOKU": ("Ka... me... ha... me... HA!", "Ка... ме... ха... ме... ХА!"),
    "SUPERMAN": ("This is for Earth.", "Это ради Земли."),
    "SHREK": ("WHAT ARE YOU DOING IN MY SWAMP?!", "ЧТО ВЫ ДЕЛАЕТЕ В МОЁМ БОЛОТЕ?!"),
    "AIZEN": ("Since when were you under the impression that I wasn't using Kyoka Suigetsu?",
              "С каких пор тебе казалось, что я не использую Кёка Суйгэцу?"),
    "ICHIGO": ("This is the final Getsuga Tensho.", "Это последняя Гецуга Тэнсё."),
    "VERGIL": ("You're not worthy as my opponent.", "Ты недостоин быть моим противником."),
    "EREN": ("I will destroy them all. Every last one.", "Я уничтожу их всех. До последнего."),
    "AINZ": ("Know despair.", "Познай отчаяние."),
    "SHADOW": ("I am... ATOMIC.", "Я... АТОМНЫЙ."),
    "ESCANOR": ("Who decided that?", "Кто так решил?"),
    "MULTIVERSE_RIFT": ("REALITY INTEGRITY: CRITICAL", "REALITY INTEGRITY: CRITICAL"),
    "MULTIVERSE_FLASH": ("DIMENSIONAL THREAT DETECTED", "DIMENSIONAL THREAT DETECTED"),
}


def desc_en(m):
    t, dmg, cd = m[0], m[4], m[5] / 20.0
    if t == "D":
        return f"<grey>Power-up: {m[7]} for 10 s. Cooldown {cd:g} s."
    if t == "C":
        return f"<grey>Dash forward. Cooldown {cd:g} s."
    extra = " (area)" if m[7] == "ring" else ""
    return f"<grey>{TYPE_EN[t]}{extra}: <yellow>{dmg:g}<grey> damage. Cooldown {cd:g} s."


def desc_ru(m):
    t, dmg, cd = m[0], m[4], m[5] / 20.0
    if t == "D":
        return f"<grey>Усиление: {BUFF_RU[m[7]]} на 10 с. Перезарядка {cd:g} с."
    if t == "C":
        return f"<grey>Рывок вперёд. Перезарядка {cd:g} с."
    extra = " (по площади)" if m[7] == "ring" else ""
    return f"<grey>{TYPE_RU[t]}{extra}: <yellow>{dmg:g}<grey> урона. Перезарядка {cd:g} с."


def jstr(s):
    return json.dumps(s, ensure_ascii=False)


def java():
    out = []
    out.append("package dev.baranhan.viltrumitecore.hero.budget;\n")
    out.append("import dev.baranhan.viltrumitecore.hero.HeroId;\n")
    out.append("/**\n * GENERATED by tools/budget/make_budget_roster.py - do not edit by hand.\n"
               " * Ultimate Hero Expansion roster: 48 heroes on the shared budget chassis.\n */")
    out.append("public final class BudgetRoster {")
    out.append("   private BudgetRoster() {\n   }\n")
    out.append("   public static final BudgetHeroSpec[] ALL = {")
    rows = []
    for h in H:
        l = h["look"]
        moves = []
        for m in h["moves"]:
            shape = "BudgetShape.RING" if m[7] == "ring" else "BudgetShape.BEAM"
            buff = {"strength": "BudgetBuff.STRENGTH", "speed": "BudgetBuff.SPEED", "resistance": "BudgetBuff.RESISTANCE"}.get(m[7], "BudgetBuff.NONE")
            moves.append(f"            new BudgetMove(BudgetMoveType.{m[0]}, {jstr(m[1])}, {float(m[4])}F, {m[5]}, 0x{m[6]}, {shape}, {buff})")
        face = ", ".join(jstr(x) for x in l["face"])
        det = ", ".join(jstr(x) for x in l["details"])
        rows.append(
            f"      new BudgetHeroSpec(HeroId.{h['enum']},\n"
            f"         new BudgetLook(0x{l['skin']}, 0x{l['hair']}, 0x{l['shirt']}, 0x{l['pants']}, 0x{l['shoes']}, 0x{l['eyes']},\n"
            f"            new String[]{{{face}}}, new String[]{{{det}}}),\n"
            f"         new BudgetMove[]{{\n" + ",\n".join(moves) + "\n         },\n"
            f"         BudgetScene.{h['scene']},\n"
            f"         BudgetFaction.{FACTION[h['key']]})")
    out.append(",\n".join(rows))
    out.append("   };")
    out.append("}")
    return "\n".join(out) + "\n"


# MULTIVERSE COLLAPSE / BOSS RUSH / FACTIONS (same lang files, en + ru)
BOSSES = {
    "king_of_curses": ("King of Curses", "Король проклятий"),
    "lord_of_illusions": ("Lord of Illusions", "Владыка иллюзий"),
    "lord_of_death": ("Lord of Death", "Повелитель смерти"),
    "titan_destroyer": ("Titan Destroyer", "Титан-разрушитель"),
    "absolute_power": ("Absolute Power", "Абсолютная сила"),
    "demon_king": ("Demon King", "Король демонов"),
    "atomic_shadow": ("Atomic Shadow", "Атомный Шэдоу"),
    "final_champion": ("Final Champion of the Multiverse", "Финальный чемпион мультивселенной"),
}
FACTION_NAMES = {
    "anime": ("Anime Universes", "Аниме-вселенные"),
    "comics": ("Comic Heroes", "Герои комиксов"),
    "demons": ("Demons & Curses", "Демоны и проклятия"),
    "gods": ("Gods & Legends", "Боги и легенды"),
    "chaos": ("Multiverse Chaos", "Хаос мультивселенной"),
}
MODIFIER_NAMES = {
    "double_trouble": ("Double Trouble", "Двойная угроза"),
    "boss_mayhem": ("Boss Mayhem", "Нашествие боссов"),
    "ultimate_chaos": ("Ultimate Chaos", "Абсолютный хаос"),
    "random_champions": ("Random Champions", "Случайные чемпионы"),
}
MV = {  # key: (en, ru)
    "entity.viltrumitecore.multiverse_fighter": ("Multiverse Fighter", "Боец мультивселенной"),
    "multiverse.viltrumitecore.title.initiated": ("MULTIVERSE COLLAPSE INITIATED", "MULTIVERSE COLLAPSE INITIATED"),
    "multiverse.viltrumitecore.sub.rift": ("Phase 1: Reality Rift", "Фаза 1: разлом реальности"),
    "multiverse.viltrumitecore.integrity.critical": ("REALITY INTEGRITY: CRITICAL", "REALITY INTEGRITY: CRITICAL"),
    "multiverse.viltrumitecore.integrity.restored": ("REALITY INTEGRITY: RESTORED", "REALITY INTEGRITY: RESTORED"),
    "multiverse.viltrumitecore.title.threat": ("DIMENSIONAL THREAT DETECTED", "DIMENSIONAL THREAT DETECTED"),
    "multiverse.viltrumitecore.sub.invasion": ("Phase 2: First Invasions", "Фаза 2: первые вторжения"),
    "multiverse.viltrumitecore.title.war": ("WAR OF THE MULTIVERSES", "WAR OF THE MULTIVERSES"),
    "multiverse.viltrumitecore.sub.war": ("Phase 3: The factions collide", "Фаза 3: фракции сталкиваются"),
    "multiverse.viltrumitecore.title.champions": ("CHAMPIONS HAVE ARRIVED", "CHAMPIONS HAVE ARRIVED"),
    "multiverse.viltrumitecore.sub.champions": ("Phase 4: Arrival of the Champions", "Фаза 4: прибытие чемпионов"),
    "multiverse.viltrumitecore.title.final": ("FINAL CHAMPION APPROACHING", "FINAL CHAMPION APPROACHING"),
    "multiverse.viltrumitecore.sub.final": ("Phase 5: Final Collapse", "Фаза 5: финальный коллапс"),
    "multiverse.viltrumitecore.title.saved": ("THE MULTIVERSE IS SAVED", "THE MULTIVERSE IS SAVED"),
    "multiverse.viltrumitecore.sub.closing": ("The rift is closing", "Разлом закрывается"),
    "multiverse.viltrumitecore.title.fallen": ("THE MULTIVERSE HAS FALLEN", "THE MULTIVERSE HAS FALLEN"),
    "multiverse.viltrumitecore.sub.fallen": ("No defenders remained near the rift", "У разлома не осталось защитников"),
    "multiverse.viltrumitecore.ended": ("Multiverse Collapse is over. The rift has closed.", "Коллапс мультивселенной завершён. Разлом закрыт."),
    "multiverse.viltrumitecore.failed": ("Multiverse Collapse failed. The rift swallowed everything.", "Коллапс мультивселенной проигран. Разлом поглотил всё."),
    "multiverse.viltrumitecore.stopped": ("Multiverse Collapse stopped by an operator.", "Коллапс мультивселенной остановлен оператором."),
    "multiverse.viltrumitecore.started": ("Multiverse Collapse started.", "Коллапс мультивселенной запущен."),
    "multiverse.viltrumitecore.already": ("A Multiverse Collapse is already running.", "Коллапс мультивселенной уже идёт."),
    "multiverse.viltrumitecore.none": ("Nothing is running.", "Ничего не запущено."),
    "multiverse.viltrumitecore.status": ("Phase %s (%s), enemies alive: %s, modifiers: %s", "Фаза %s (%s), живых противников: %s, модификаторы: %s"),
    "multiverse.viltrumitecore.stop_report": ("Stopped: event %s, boss rush runs %s, NPCs removed %s", "Остановлено: событие %s, забегов Boss Rush %s, удалено NPC %s"),
    "multiverse.viltrumitecore.modifiers": ("Chaos modifiers:%s", "Модификаторы хаоса:%s"),
    "multiverse.viltrumitecore.unknown_modifier": ("Unknown modifier: %s", "Неизвестный модификатор: %s"),
    "multiverse.viltrumitecore.unknown_boss": ("Unknown boss: %s", "Неизвестный босс: %s"),
    "multiverse.viltrumitecore.unknown_faction": ("Unknown faction", "Неизвестная фракция"),
    "multiverse.viltrumitecore.faction_arrives": ("%s enter the war!", "%s вступают в войну!"),
    "multiverse.viltrumitecore.faction_wins": ("%s win the faction war!", "%s побеждают в войне фракций!"),
    "multiverse.viltrumitecore.boss_arrives": ("%s has arrived!", "%s прибывает!"),
    "multiverse.viltrumitecore.clash": ("Faction clash: %s vs %s (%s fighters)", "Столкновение фракций: %s против %s (бойцов: %s)"),
    "multiverse.viltrumitecore.enraged": ("%s IS ENRAGED!", "%s В ЯРОСТИ!"),
    "multiverse.viltrumitecore.special.shrine": ("DOMAIN EXPANSION", "РАСШИРЕНИЕ ТЕРРИТОРИИ"),
    "multiverse.viltrumitecore.special.illusion": ("COMPLETE HYPNOSIS", "ПОЛНЫЙ ГИПНОЗ"),
    "multiverse.viltrumitecore.special.summon": ("RISE, MY SERVANTS", "ВОССТАНЬТЕ, СЛУГИ"),
    "multiverse.viltrumitecore.special.titan": ("TITAN STOMP", "ТОПОТ ТИТАНА"),
    "multiverse.viltrumitecore.special.punch_charge": ("SERIOUS SERIES...", "СЕРЬЁЗНАЯ СЕРИЯ..."),
    "multiverse.viltrumitecore.special.punch": ("SERIOUS PUNCH", "СЕРЬЁЗНЫЙ УДАР"),
    "multiverse.viltrumitecore.special.counter": ("FULL COUNTER", "ПОЛНЫЙ КОНТРУДАР"),
    "multiverse.viltrumitecore.special.atomic_charge": ("I AM...", "Я..."),
    "multiverse.viltrumitecore.special.atomic": ("ATOMIC", "АТОМНЫЙ"),
    "multiverse.viltrumitecore.title.bossrush": ("BOSS RUSH", "BOSS RUSH"),
    "multiverse.viltrumitecore.sub.bossrush": ("%s multiverse bosses. No mercy.", "Боссов мультивселенной: %s. Без пощады."),
    "multiverse.viltrumitecore.title.wave": ("NEW CHALLENGER", "NEW CHALLENGER"),
    "multiverse.viltrumitecore.sub.wave": ("Wave %s/%s: %s", "Волна %s/%s: %s"),
    "multiverse.viltrumitecore.wave": ("WAVE %s/%s - %s", "ВОЛНА %s/%s - %s"),
    "multiverse.viltrumitecore.wave_cleared": ("Wave %s/%s cleared!", "Волна %s/%s пройдена!"),
    "multiverse.viltrumitecore.next_boss": ("Next boss in %s s", "Следующий босс через %s с"),
    "multiverse.viltrumitecore.title.bossrush_done": ("MULTIVERSE CHAMPION", "MULTIVERSE CHAMPION"),
    "multiverse.viltrumitecore.sub.bossrush_done": ("All %s multiverse bosses defeated", "Все %s боссов мультивселенной повержены"),
    "multiverse.viltrumitecore.title.bossrush_failed": ("BOSS RUSH FAILED", "BOSS RUSH FAILED"),
    "multiverse.viltrumitecore.title.bossrush_cancelled": ("BOSS RUSH CANCELLED", "BOSS RUSH CANCELLED"),
    "multiverse.viltrumitecore.bossrush_already": ("Your Boss Rush is already running.", "Ваш Boss Rush уже идёт."),
    "multiverse.viltrumitecore.bossrush_stopped": ("Boss Rush stopped.", "Boss Rush остановлен."),
}


def multiverse_lang(ru):
    i = 1 if ru else 0
    d = {k: v[i] for k, v in MV.items()}
    for k, v in BOSSES.items():
        d[f"boss.viltrumitecore.multiverse.{k}"] = v[i]
    for k, v in FACTION_NAMES.items():
        d[f"faction.viltrumitecore.{k}"] = v[i]
    for k, v in MODIFIER_NAMES.items():
        d[f"multiverse.viltrumitecore.modifier.{k}"] = v[i]
    return d


def lang(ru):
    d = {}
    for h in H:
        d[f"hero.viltrumitecore.{h['key']}"] = h["ru"] if ru else h["en"]
        for m in h["moves"]:
            base = f"ability.viltrumitecore.budget_{h['key']}_{m[1]}"
            d[base + ".name"] = m[3] if ru else m[2]
            d[base + ".desc"] = desc_ru(m) if ru else desc_en(m)
    for scene, (en, r) in SCENE_LINES.items():
        d[f"scene.viltrumitecore.budget.{scene.lower()}"] = r if ru else en
    ui_en = {
        "gui.viltrumitecore.expansion.title": "Viltrumite Mods - Ultimate Multiverse Expansion",
        "gui.viltrumitecore.expansion.open": "Ultimate Hero Expansion >>",
        "gui.viltrumitecore.expansion.page": "Page %s / %s",
        "gui.viltrumitecore.expansion.prev": "< Prev",
        "gui.viltrumitecore.expansion.next": "Next >",
        "gui.viltrumitecore.expansion.back": "Back",
        "gui.viltrumitecore.expansion.hulk": "HULK - IN DEVELOPMENT",
        "gui.viltrumitecore.expansion.hulk_locked": "This character is in development. Hulk's code has not been edited.",
        "gui.viltrumitecore.expansion.count": "%s new heroes",
        "message.viltrumitecore.expansion.enter": "Ultimate Hero Expansion: %s",
        "message.viltrumitecore.expansion.cooldown": "%s: %s s",
        "commands.viltrumitecore.hero.set": "Hero set to %s",
        "commands.viltrumitecore.hero.unknown": "Unknown hero: %s",
    }
    ui_ru = {
        "gui.viltrumitecore.expansion.title": "Viltrumite Mods - Ultimate Multiverse Expansion",
        "gui.viltrumitecore.expansion.open": "Ultimate Hero Expansion >>",
        "gui.viltrumitecore.expansion.page": "Страница %s / %s",
        "gui.viltrumitecore.expansion.prev": "< Назад",
        "gui.viltrumitecore.expansion.next": "Далее >",
        "gui.viltrumitecore.expansion.back": "Вернуться",
        "gui.viltrumitecore.expansion.hulk": "HULK - IN DEVELOPMENT",
        "gui.viltrumitecore.expansion.hulk_locked": "Персонаж находится в разработке. Код Халка не был правлен",
        "gui.viltrumitecore.expansion.count": "Новых героев: %s",
        "message.viltrumitecore.expansion.enter": "Ultimate Hero Expansion: %s",
        "message.viltrumitecore.expansion.cooldown": "%s: %s с",
        "commands.viltrumitecore.hero.set": "Герой установлен: %s",
        "commands.viltrumitecore.hero.unknown": "Неизвестный герой: %s",
    }
    d.update(ui_ru if ru else ui_en)
    d.update(multiverse_lang(ru))
    return d


def hero_id():
    with open(HERO_ID, encoding="utf-8") as f:
        text = f.read()
    start = text.index(BEGIN)
    start = text.index("\n", start) + 1
    end = text.index(END)
    body = "".join(f'   /** Ultimate Hero Expansion: {h["en"]}. */\n   {h["enum"]}("{h["key"]}"),\n' for h in H)
    with open(HERO_ID, "w", encoding="utf-8") as f:
        f.write(text[:start] + body + text[end:])


def main():
    import sys
    if "--limit" in sys.argv:
        del H[int(sys.argv[sys.argv.index("--limit") + 1]):]
    hero_id()
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8") as f:
        f.write(java())
    os.makedirs(LANG, exist_ok=True)
    for code, ru in (("en_us", False), ("ru_ru", True)):
        with open(os.path.join(LANG, code + ".json"), "w", encoding="utf-8") as f:
            json.dump(lang(ru), f, ensure_ascii=False, indent=2)
            f.write("\n")
    moves = sum(len(h["moves"]) for h in H)
    print(f"{len(H)} heroes, {moves} abilities, {sum(1 for h in H if h['scene'] != 'NONE')} scenes")


if __name__ == "__main__":
    main()
