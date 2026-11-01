#!/usr/bin/env python3
"""THANOS 2.0 (the war for Atheria) strings for the 12 shipped locales -> src/main/lang-overlays/thanos_war/<locale>.json.

The war's own keys (speakers, voice lines, HUD, the absorb notice, entity names, sound subtitles); the
commands, phase announcements and move names stay with the event in make_thanos_lang.py. One row per key:
(en, ru, de, es, tr, zh); every Spanish locale uses the neutral Spanish column, as the other overlays do.
Run: python3 tools/assets/make_thanos_war_lang.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "viltrumitecore/src/main/lang-overlays/thanos_war"
LOCALES = {"en_us": 0, "ru_ru": 1, "de_de": 2, "es_es": 3, "es_mx": 3, "es_ar": 3, "es_cl": 3, "es_ec": 3, "es_uy": 3, "es_ve": 3,
           "tr_tr": 4, "zh_cn": 5}

T = {
    # entities and speakers
    "entity.viltrumitecore.ebony_maw": ("Ebony Maw", "Эбони Мо", "Ebony Maw", "Ebony Maw", "Ebony Maw", "乌木喉"),
    "entity.viltrumitecore.cull_obsidian": ("Cull Obsidian", "Кулл Обсидиан", "Cull Obsidian", "Cull Obsidian", "Cull Obsidian", "黑矮星"),
    "entity.viltrumitecore.war_debris": ("Telekinetic Debris", "Телекинетический обломок", "Telekinetische Trümmer", "Escombro telequinético",
                                         "Telekinetik Enkaz", "念力碎块"),
    "subtitle.viltrumitecore.speaker.thanos": ("Thanos", "Танос", "Thanos", "Thanos", "Thanos", "灭霸"),
    "subtitle.viltrumitecore.speaker.ebony_maw": ("Ebony Maw", "Эбони Мо", "Ebony Maw", "Ebony Maw", "Ebony Maw", "乌木喉"),
    "subtitle.viltrumitecore.speaker.cull_obsidian": ("Cull Obsidian", "Кулл Обсидиан", "Cull Obsidian", "Cull Obsidian", "Cull Obsidian", "黑矮星"),
    # chat and HUD
    "event.viltrumitecore.thanos_war.absorb": (
        "The Gauntlet drinks energy attacks! Fight him up close, or stagger him first.",
        "Перчатка поглощает энергетические атаки! Бейте его вблизи или сначала собейте с ног.",
        "Der Handschuh schluckt Energieangriffe! Kämpft im Nahkampf oder bringt ihn erst ins Wanken.",
        "¡El Guantelete absorbe los ataques de energía! Lucha cuerpo a cuerpo o derríbalo primero.",
        "Eldiven enerji saldırılarını emiyor! Onunla yakından dövüşün ya da önce sendeletin.",
        "手套会吸收能量攻击！近身作战，或先让他踉跄。"),
    "hud.viltrumitecore.thanos_war.held": (
        "HELD — staggering the one holding you breaks the grip",
        "ВЫ СХВАЧЕНЫ — оглушите того, кто держит, и хватка ослабнет",
        "GEPACKT — bringt den Halter ins Wanken, um euch zu befreien",
        "ATRAPADO — aturde a quien te sujeta para liberarte",
        "YAKALANDIN — seni tutanı sendelet, tutuş çözülür",
        "被控制——让控制者踉跄即可挣脱"),
    "hud.viltrumitecore.thanos_war.phase.arrival": ("Phase I — Arrival", "Фаза I — Прибытие", "Phase I — Ankunft", "Fase I — Llegada",
                                                    "Aşama I — Varış", "第一阶段——降临"),
    "hud.viltrumitecore.thanos_war.phase.black_order": ("Phase II — The Black Order", "Фаза II — Чёрный Орден", "Phase II — Der Schwarze Orden",
                                                        "Fase II — La Orden Negra", "Aşama II — Kara Düzen", "第二阶段——黑曜五将"),
    "hud.viltrumitecore.thanos_war.phase.invasion": ("Phase III — Invasion", "Фаза III — Вторжение", "Phase III — Invasion", "Fase III — Invasión",
                                                     "Aşama III — İstila", "第三阶段——入侵"),
    "hud.viltrumitecore.thanos_war.phase.gauntlet": ("Phase IV — The Full Gauntlet", "Фаза IV — Полная Перчатка", "Phase IV — Der volle Handschuh",
                                                     "Fase IV — El Guantelete completo", "Aşama IV — Tam Eldiven", "第四阶段——完整手套"),
    "hud.viltrumitecore.thanos_war.army": ("Invaders: %s", "Захватчиков: %s", "Invasoren: %s", "Invasores: %s", "İstilacılar: %s", "入侵者：%s"),
    "hud.viltrumitecore.thanos_war.strikes": ("Orbital strikes incoming: %s", "Орбитальных ударов на подходе: %s", "Orbitalschläge im Anflug: %s",
                                              "Ataques orbitales en camino: %s", "Gelen yörünge saldırısı: %s", "来袭的轨道打击：%s"),
}

# voice lines: subtitle.viltrumitecore.thanos_war.<voice>.<line>
LINES = {
    "thanos.arrival": (
        "Atheria. Look up, heroes. Your sky belongs to me now.",
        "Атерия. Поднимите глаза, герои. Ваше небо теперь принадлежит мне.",
        "Atheria. Seht nach oben, Helden. Euer Himmel gehört jetzt mir.",
        "Atheria. Mirad arriba, héroes. Vuestro cielo ahora me pertenece.",
        "Atheria. Başınızı kaldırın, kahramanlar. Gökyüzünüz artık benim.",
        "阿瑟利亚。抬头看吧，英雄们。你们的天空如今属于我。"),
    "thanos.call_order": (
        "Maw. Obsidian. Bring me their heroes.",
        "Мо. Обсидиан. Приведите мне их героев.",
        "Maw. Obsidian. Bringt mir ihre Helden.",
        "Maw. Obsidian. Traedme a sus héroes.",
        "Maw. Obsidian. Kahramanlarını bana getirin.",
        "乌木喉。黑矮星。把他们的英雄带到我面前。"),
    "thanos.call_army": (
        "Open the sky. Leave nothing standing.",
        "Откройте небо. Не оставляйте ничего на ногах.",
        "Öffnet den Himmel. Lasst nichts stehen.",
        "Abrid el cielo. Que no quede nada en pie.",
        "Göğü açın. Ayakta hiçbir şey bırakmayın.",
        "打开天空。片瓦不留。"),
    "thanos.gauntlet": (
        "Enough. I will do this myself, with all six.",
        "Довольно. Я сделаю это сам — всеми шестью.",
        "Genug. Ich erledige das selbst — mit allen sechs.",
        "Basta. Lo haré yo mismo, con las seis.",
        "Yeter. Bunu kendim yapacağım, altısıyla birden.",
        "够了。我亲自来——用全部六颗。"),
    "thanos.pressed": (
        "You fight well. It changes nothing.",
        "Вы хорошо сражаетесь. Это ничего не меняет.",
        "Ihr kämpft gut. Es ändert nichts.",
        "Lucháis bien. No cambia nada.",
        "İyi dövüşüyorsunuz. Hiçbir şeyi değiştirmez.",
        "你们打得不错。但这改变不了什么。"),
    "thanos.ship": (
        "The Sanctuary is above you. There is nowhere left to run.",
        "Санктуарий над вами. Бежать больше некуда.",
        "Die Sanctuary steht über euch. Es gibt keinen Ort mehr zur Flucht.",
        "El Sanctuary está sobre vosotros. Ya no queda adónde huir.",
        "Sanctuary tepenizde. Kaçacak yer kalmadı.",
        "圣殿号就在你们头顶。已无处可逃。"),
    "thanos.ship_call": (
        "Sanctuary. Fire.",
        "Санктуарий. Огонь.",
        "Sanctuary. Feuer.",
        "Sanctuary. Fuego.",
        "Sanctuary. Ateş.",
        "圣殿号。开火。"),
    "thanos.wave": (
        "More. Send them all.",
        "Ещё. Отправьте всех.",
        "Mehr. Schickt sie alle.",
        "Más. Enviadlos a todos.",
        "Daha fazla. Hepsini gönderin.",
        "再来。全部派下去。"),
    "thanos.lost_maw": (
        "Maw... Your devotion will be remembered.",
        "Мо... Твою преданность не забудут.",
        "Maw... Deine Ergebenheit wird nicht vergessen.",
        "Maw... Tu devoción será recordada.",
        "Maw... Bağlılığın unutulmayacak.",
        "乌木喉……你的忠诚将被铭记。"),
    "thanos.lost_cull": (
        "Obsidian has fallen. Then I will finish what he started.",
        "Обсидиан пал. Значит, я закончу то, что он начал.",
        "Obsidian ist gefallen. Dann beende ich, was er begonnen hat.",
        "Obsidian ha caído. Entonces terminaré lo que él empezó.",
        "Obsidian düştü. O hâlde onun başladığını ben bitireceğim.",
        "黑矮星倒下了。那就由我来完成他未竟之事。"),
    "thanos.infinity": (
        "Behold the full power of the stones!",
        "Узрите полную мощь Камней!",
        "Seht die ganze Macht der Steine!",
        "¡Contemplad todo el poder de las gemas!",
        "Taşların tüm gücüne tanık olun!",
        "见证宝石的全部力量吧！"),
    "ebony_maw.arrive": (
        "Rejoice, Atherians. The Great Titan has chosen you for salvation.",
        "Возрадуйтесь, атерийцы. Великий Титан избрал вас для спасения.",
        "Frohlockt, Atherianer. Der Große Titan hat euch zur Erlösung erwählt.",
        "Regocijaos, atherianos. El Gran Titán os ha elegido para la salvación.",
        "Sevinin, Atherialılar. Yüce Titan sizi kurtuluş için seçti.",
        "欢欣吧，阿瑟利亚人。伟大的泰坦选中了你们，赐予救赎。"),
    "ebony_maw.volley": (
        "Your city makes such fine weapons.",
        "Ваш город — такое прекрасное оружие.",
        "Eure Stadt gibt so feine Waffen ab.",
        "Vuestra ciudad da armas tan magníficas.",
        "Şehriniz ne güzel silahlar çıkarıyor.",
        "你们的城市，真是绝佳的武器。"),
    "ebony_maw.grab": (
        "Be still. Your struggle is only noise.",
        "Замри. Твоя борьба — лишь шум.",
        "Halt still. Dein Ringen ist nur Lärm.",
        "Quieto. Tu forcejeo no es más que ruido.",
        "Kıpırdama. Çırpınışın yalnızca gürültü.",
        "别动。你的挣扎不过是噪音。"),
    "ebony_maw.death": (
        "A pity... I so enjoyed your little world.",
        "Как жаль... Мне так нравился ваш маленький мир.",
        "Wie schade... Eure kleine Welt hat mir so gefallen.",
        "Qué lástima... Disfrutaba tanto de vuestro pequeño mundo.",
        "Ne yazık... Küçük dünyanızdan öyle hoşlanmıştım ki.",
        "真可惜……我那么喜欢你们这小小的世界。"),
    "cull_obsidian.arrive": (
        "I will crush your little city!",
        "Я раздавлю ваш городишко!",
        "Ich zermalme eure kleine Stadt!",
        "¡Aplastaré vuestra pequeña ciudad!",
        "Küçük şehrinizi ezeceğim!",
        "我要碾碎你们这座小城！"),
    "cull_obsidian.hulkbuster": (
        "Metal man! Come, fight me!",
        "Железный человечек! Иди сюда, дерись!",
        "Blechmann! Komm her und kämpf!",
        "¡Hombre de metal! ¡Ven, pelea conmigo!",
        "Metal adam! Gel, dövüş benimle!",
        "铁皮人！来啊，跟我打！"),
    "cull_obsidian.charge": (
        "Out of my way!",
        "С дороги!",
        "Aus dem Weg!",
        "¡Fuera de mi camino!",
        "Çekil yolumdan!",
        "滚开！"),
    "cull_obsidian.clash": (
        "Strong! Not strong enough!",
        "Силён! Но недостаточно!",
        "Stark! Aber nicht stark genug!",
        "¡Fuerte! ¡Pero no lo bastante!",
        "Güçlüsün! Ama yeterince değil!",
        "有力气！但还不够！"),
    "cull_obsidian.chain": (
        "Nowhere to run!",
        "Не убежишь!",
        "Kein Entkommen!",
        "¡No tienes adónde huir!",
        "Kaçacak yer yok!",
        "无处可逃！"),
    "cull_obsidian.death": (
        "For... the... Titan...",
        "За... Титана...",
        "Für... den... Titanen...",
        "Por... el... Titán...",
        "Titan... için...",
        "为了……泰坦……"),
}

# sound subtitles: subtitles.viltrumitecore.thanos_war.<cue>
SUBTITLES = {
    "ship_arrival": ("Sanctuary II descends", "Спускается Санктуарий II", "Sanctuary II sinkt herab", "Desciende el Sanctuary II",
                     "Sanctuary II alçalıyor", "圣殿二号降临"),
    "ship_hum": ("Warship hums overhead", "Над головой гудит корабль", "Kriegsschiff brummt über euch", "Zumba la nave de guerra",
                 "Savaş gemisi uğulduyor", "战舰在头顶嗡鸣"),
    "ship_horn": ("Warship horn blares", "Ревёт горн корабля", "Kriegsschiffhorn dröhnt", "Brama el cuerno de la nave",
                  "Gemi borusu böğürüyor", "战舰号角轰鸣"),
    "ship_charge": ("Orbital strike charges", "Заряжается орбитальный удар", "Orbitalschlag lädt", "Se carga un ataque orbital",
                    "Yörünge saldırısı şarj oluyor", "轨道打击充能"),
    "ship_lance": ("Orbital strike lands", "Падает орбитальный удар", "Orbitalschlag schlägt ein", "Impacta un ataque orbital",
                   "Yörünge saldırısı iniyor", "轨道打击落地"),
    "ship_beam": ("Beam descends from the ship", "С корабля бьёт луч", "Strahl fährt vom Schiff herab", "Un rayo baja de la nave",
                  "Gemiden ışın iniyor", "光束从战舰降下"),
    "ship_depart": ("Warship departs", "Корабль улетает", "Kriegsschiff zieht ab", "La nave de guerra se marcha",
                    "Savaş gemisi ayrılıyor", "战舰离去"),
    "portal_open": ("Invasion portal tears open", "Разрывается портал вторжения", "Invasionsportal reißt auf", "Se abre un portal de invasión",
                    "İstila geçidi yırtılıyor", "入侵传送门撕开"),
    "siren": ("Air-raid sirens wail", "Воют сирены воздушной тревоги", "Luftschutzsirenen heulen", "Aúllan las sirenas antiaéreas",
              "Hava saldırısı sirenleri", "防空警报长鸣"),
    "command": ("Thanos commands", "Танос отдаёт приказ", "Thanos befiehlt", "Thanos da una orden", "Thanos emrediyor", "灭霸下令"),
    "absorb": ("Gauntlet absorbs energy", "Перчатка поглощает энергию", "Handschuh absorbiert Energie", "El Guantelete absorbe energía",
               "Eldiven enerjiyi emiyor", "手套吸收能量"),
    "rupture": ("Ground ruptures", "Земля разверзается", "Boden bricht auf", "El suelo se resquebraja", "Zemin yarılıyor", "大地崩裂"),
    "barrage": ("Space gates fire", "Стреляют пространственные врата", "Raumtore feuern", "Disparan los portales espaciales",
                "Uzay geçitleri ateşliyor", "空间之门齐射"),
    "rewrite": ("Reality rewrites itself", "Реальность переписывается", "Realität schreibt sich um", "La realidad se reescribe",
                "Gerçeklik yeniden yazılıyor", "现实被改写"),
    "anomaly": ("Time stutters", "Время сбивается", "Die Zeit stockt", "El tiempo titubea", "Zaman takılıyor", "时间紊乱"),
    "compulsion": ("Mind Stone compels", "Камень Разума подчиняет", "Geiststein zwingt", "La Gema de la Mente obliga",
                   "Zihin Taşı zorluyor", "心灵宝石操控"),
    "harvest": ("Souls are harvested", "Души собираются", "Seelen werden geerntet", "Se cosechan almas", "Ruhlar toplanıyor", "灵魂被收割"),
    "time_stop": ("Time stops", "Время останавливается", "Die Zeit steht still", "El tiempo se detiene", "Zaman duruyor", "时间停止"),
    "volley": ("Frozen volley fires", "Срывается застывший залп", "Erstarrte Salve feuert", "Se dispara la descarga congelada",
               "Donmuş yaylım ateşleniyor", "凝固的齐射发出"),
    "rock_impact": ("Moon shard crashes down", "Падает осколок луны", "Mondsplitter schlägt ein", "Cae un fragmento lunar",
                    "Ay parçası çakılıyor", "月球碎片坠落"),
    "infinity_stage": ("Infinity Stone erupts", "Вспыхивает Камень Бесконечности", "Infinity-Stein bricht aus", "Estalla una Gema del Infinito",
                       "Sonsuzluk Taşı patlıyor", "无限宝石爆发"),
    "maw_voice": ("Ebony Maw whispers", "Шепчет Эбони Мо", "Ebony Maw flüstert", "Ebony Maw susurra", "Ebony Maw fısıldıyor", "乌木喉低语"),
    "maw_lift": ("Debris rises", "Поднимаются обломки", "Trümmer erheben sich", "Se elevan escombros", "Enkaz yükseliyor", "碎块升起"),
    "maw_throw": ("Debris is hurled", "Летят обломки", "Trümmer werden geschleudert", "Se lanzan escombros", "Enkaz fırlatılıyor", "碎块被掷出"),
    "maw_grab": ("Telekinetic grip", "Телекинетическая хватка", "Telekinetischer Griff", "Agarre telequinético", "Telekinetik kavrayış",
                 "念力擒拿"),
    "maw_barrier": ("Debris shield forms", "Смыкается щит из обломков", "Trümmerschild formt sich", "Se forma un escudo de escombros",
                    "Enkaz kalkanı oluşuyor", "碎块护盾成形"),
    "maw_catch": ("Projectile stopped mid-air", "Снаряд замер в воздухе", "Geschoss in der Luft gestoppt", "Proyectil detenido en el aire",
                  "Mermi havada durdu", "飞弹被定在半空"),
    "maw_hurt": ("Ebony Maw hurts", "Эбони Мо ранен", "Ebony Maw wird verletzt", "Ebony Maw herido", "Ebony Maw yaralanıyor", "乌木喉受伤"),
    "maw_death": ("Ebony Maw falls", "Эбони Мо повержен", "Ebony Maw fällt", "Ebony Maw cae", "Ebony Maw düşüyor", "乌木喉倒下"),
    "cull_roar": ("Cull Obsidian roars", "Ревёт Кулл Обсидиан", "Cull Obsidian brüllt", "Cull Obsidian ruge", "Cull Obsidian kükrüyor",
                  "黑矮星咆哮"),
    "cull_swing": ("Heavy weapon swings", "Взмах тяжёлого оружия", "Schwere Waffe schwingt", "Se balancea un arma pesada",
                   "Ağır silah savruluyor", "重武器挥舞"),
    "cull_slam": ("Ground slam", "Удар о землю", "Bodenstampfer", "Golpe contra el suelo", "Yere vuruş", "重击地面"),
    "cull_charge": ("Cull Obsidian charges", "Кулл Обсидиан идёт на таран", "Cull Obsidian stürmt los", "Cull Obsidian embiste",
                    "Cull Obsidian hücum ediyor", "黑矮星冲锋"),
    "cull_wall": ("Wall bursts apart", "Рушится стена", "Wand birst", "Una pared revienta", "Duvar parçalanıyor", "墙壁崩碎"),
    "cull_chain": ("Chain blade flies", "Летит клинок на цепи", "Kettenklinge fliegt", "Vuela la hoja encadenada", "Zincirli bıçak uçuyor",
                   "链刃飞出"),
    "cull_grab": ("Cull Obsidian grabs", "Кулл Обсидиан хватает", "Cull Obsidian packt zu", "Cull Obsidian agarra", "Cull Obsidian yakalıyor",
                  "黑矮星抓取"),
    "cull_hurt": ("Cull Obsidian hurts", "Кулл Обсидиан ранен", "Cull Obsidian wird verletzt", "Cull Obsidian herido", "Cull Obsidian yaralanıyor",
                  "黑矮星受伤"),
    "cull_death": ("Cull Obsidian falls", "Кулл Обсидиан повержен", "Cull Obsidian fällt", "Cull Obsidian cae", "Cull Obsidian düşüyor",
                   "黑矮星倒下"),
    "cull_step": ("Heavy footsteps", "Тяжёлые шаги", "Schwere Schritte", "Pasos pesados", "Ağır adımlar", "沉重的脚步"),
    "debris_impact": ("Debris crashes", "Падают обломки", "Trümmer krachen", "Se estrellan escombros", "Enkaz çarpıyor", "碎块砸落"),
}


def table():
    rows = dict(T)
    for line, cols in LINES.items():
        rows["subtitle.viltrumitecore.thanos_war." + line] = cols
    for cue, cols in SUBTITLES.items():
        rows["subtitles.viltrumitecore.thanos_war." + cue] = cols
    for key, cols in rows.items():
        assert len(cols) == 6 and all(c.strip() for c in cols), key
    return rows


def main():
    rows = table()
    OUT.mkdir(parents=True, exist_ok=True)
    for locale, col in LOCALES.items():
        data = {k: v[col] for k, v in rows.items()}
        (OUT / (locale + ".json")).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("%d keys x %d locales" % (len(rows), len(LOCALES)))


if __name__ == "__main__":
    main()
