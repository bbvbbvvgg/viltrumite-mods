#!/usr/bin/env python3
"""Ultimate Hero Expansion roster generator.

Single source of truth for the 30 expansion heroes. Writes
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

assert len(H) == 30, len(H)
assert len({h['key'] for h in H}) == 30 and all(len(h['key']) <= 32 for h in H)

TYPE_EN = {"A": "Strike", "B": "Energy projectile", "C": "Dash", "D": "Power-up", "E": "Ultimate"}
TYPE_RU = {"A": "Удар", "B": "Энергетический снаряд", "C": "Рывок", "D": "Усиление", "E": "Ультимейт"}
BUFF_RU = {"strength": "сила", "speed": "скорость", "resistance": "сопротивление"}
SCENE_LINES = {
    "GOJO": ("Throughout heaven and earth, I alone am the honored one.", "Небеса и земля... лишь я достоин почитания."),
    "SUKUNA": ("Know your place, fool.", "Знай своё место, глупец."),
    "GOKU": ("Ka... me... ha... me... HA!", "Ка... ме... ха... ме... ХА!"),
    "SUPERMAN": ("This is for Earth.", "Это ради Земли."),
    "SHREK": ("WHAT ARE YOU DOING IN MY SWAMP?!", "ЧТО ВЫ ДЕЛАЕТЕ В МОЁМ БОЛОТЕ?!"),
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
               " * Ultimate Hero Expansion roster: 30 heroes on the shared budget chassis.\n */")
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
            f"         BudgetScene.{h['scene']})")
    out.append(",\n".join(rows))
    out.append("   };")
    out.append("}")
    return "\n".join(out) + "\n"


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
        "gui.viltrumitecore.expansion.title": "Viltrumite Mods - Ultimate Hero Expansion",
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
        "gui.viltrumitecore.expansion.title": "Viltrumite Mods - Ultimate Hero Expansion",
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
