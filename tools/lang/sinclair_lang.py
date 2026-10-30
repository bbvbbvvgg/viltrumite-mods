#!/usr/bin/env python3
"""Sinclair's lang files for all 12 locales (plan step 8). Run from the repo root; idempotent.

Columns: en, ru, de, es (all es_* share one text, like the other heroes), tr, zh.
`--check` exits 1 when any locale is out of date.
"""
import json, os, sys

# Own lang pack namespace: vanilla ClientLanguage merges lang/<code>.json from every namespace, so these
# keys load exactly like the shared files. Kept apart so parallel hero branches do not conflict in the
# shared lang files (and ru_ru.json stays under the 64 KB single-call push limit of the GitHub tooling).
OUT = "viltrumitecore/src/main/resources/assets/viltrumitecore_sinclair/lang"
P = "viltrumitecore"

ROWS = {
 f"hero.{P}.sinclair": ("The Magnificent Sinclair", "Великолепный Синклер", "Der Großartige Sinclair", "El Magnífico Sinclair", "Muhteşem Sinclair", "了不起的辛克莱"),
 f"gui.{P}.race_selection.become_sinclair": ("Become Sinclair", "Синклер", "Sinclair werden", "Convertirse en Sinclair", "Sinclair ol", "成为辛克莱"),

 f"ability.{P}.sinclair_vexing_bolt.name": ("Vexing Bolt", "Досадная молния", "Ärgerlicher Blitz", "Rayo Fastidioso", "Can Sıkıcı Şimşek", "恼人闪电"),
 f"ability.{P}.sinclair_vexing_bolt.desc": (
   "Flick a slow phantom bolt: 3 to 7 damage the longer it flies, bursts in a 2-block radius and slows by 25 %. Press again to redirect it to your aim. Savannah throws a second one.",
   "Бросьте медленную призрачную молнию: урон растёт от 3 до 7 за время полёта, взрыв радиусом 2 блока и −25 % скорости. Повторное нажатие разворачивает её к прицелу. Саванна бросает вторую.",
   "Wirf einen langsamen Geisterblitz: 3 bis 7 Schaden je nach Flugzeit, Explosion mit 2 Blöcken Radius und −25 % Tempo. Erneut drücken lenkt ihn zum Fadenkreuz um. Savannah wirft einen zweiten.",
   "Lanza un rayo fantasma lento: de 3 a 7 de daño según el vuelo, estalla en un radio de 2 bloques y ralentiza un 25 %. Pulsa de nuevo para desviarlo hacia la mira. Savannah lanza otro.",
   "Yavaş bir hayalet şimşek fırlat: uçuş süresine göre 3–7 hasar, 2 blok yarıçaplı patlama ve %25 yavaşlatma. Tekrar basınca nişana döner. Savannah ikincisini atar.",
   "掷出缓慢的幽灵闪电：飞行越久伤害越高（3→7），在 2 格半径内爆炸并减速 25%。再次按下可转向准星。萨凡娜会掷出第二道。"),
 f"ability.{P}.sinclair_spectral_assistant.name": ("Spectral Assistant", "Призрачная ассистентка", "Geisterhafte Assistentin", "Asistente Espectral", "Hayalet Asistan", "幽灵助手"),
 f"ability.{P}.sinclair_spectral_assistant.desc": (
   "Present Savannah at your aim for 10 s (12 HP): she fires with you and repeats Vexing Bolt. Press again to swap places with her (2 swaps, refills the wand).",
   "Представьте Саванну в точке прицела на 10 с (12 ОЗ): она стреляет вместе с вами и повторяет «Досадную молнию». Повторное нажатие — обмен местами (2 раза, палочка перезаряжается).",
   "Präsentiere Savannah 10 s lang am Fadenkreuz (12 LP): Sie schießt mit dir und wiederholt den Ärgerlichen Blitz. Erneut drücken tauscht die Plätze (2-mal, lädt den Stab nach).",
   "Presenta a Savannah en la mira durante 10 s (12 PV): dispara contigo y repite el Rayo Fastidioso. Pulsa de nuevo para intercambiar posiciones (2 veces, recarga la varita).",
   "Savannah'yı nişan noktasında 10 sn sahneye çıkar (12 can): seninle ateş eder ve Can Sıkıcı Şimşek'i tekrarlar. Tekrar basınca yer değiştirirsiniz (2 kez, asa dolar).",
   "在准星处请出萨凡娜 10 秒（12 生命）：她与你一同射击并复制恼人闪电。再次按下与她交换位置（2 次，魔杖装满）。"),
 f"ability.{P}.sinclair_rabbit_hex.name": ("Rabbit Hex", "Кроличье проклятие", "Hasenfluch", "Maleficio del Conejo", "Tavşan Laneti", "兔子咒"),
 f"ability.{P}.sinclair_rabbit_hex.desc": (
   "A phantom top hat spins over the target point for 0.9 s, then everyone within 3.5 blocks turns into a rabbit: players 2.5 s, mobs 3.5 s. Rabbits are fast but cannot attack, use items or abilities.",
   "Над точкой 0,9 с вращается призрачный цилиндр, затем все в радиусе 3,5 блока становятся кроликами: игроки на 2,5 с, мобы на 3,5 с. Кролики быстрые, но не могут атаковать, пользоваться предметами и способностями.",
   "Ein Geisterzylinder kreist 0,9 s über dem Zielpunkt, dann wird jeder im Umkreis von 3,5 Blöcken zum Hasen: Spieler 2,5 s, Mobs 3,5 s. Hasen sind schnell, können aber weder angreifen noch Gegenstände oder Fähigkeiten nutzen.",
   "Una chistera fantasma gira 0,9 s sobre el punto y luego todos en 3,5 bloques se vuelven conejos: jugadores 2,5 s, criaturas 3,5 s. Los conejos son rápidos, pero no pueden atacar ni usar objetos o habilidades.",
   "Hedefin üstünde 0,9 sn hayalet bir silindir şapka döner, ardından 3,5 blok içindeki herkes tavşana dönüşür: oyuncular 2,5 sn, yaratıklar 3,5 sn. Tavşanlar hızlıdır ama saldıramaz, eşya ya da yetenek kullanamaz.",
   "幽灵礼帽在目标点上方旋转 0.9 秒，随后 3.5 格内的所有人变成兔子：玩家 2.5 秒，生物 3.5 秒。兔子跑得快，但无法攻击、使用物品或技能。"),
 f"ability.{P}.sinclair_misdirection.name": ("Misdirection", "Отвлечение внимания", "Ablenkung", "Distracción", "Dikkat Dağıtma", "障眼法"),
 f"ability.{P}.sinclair_misdirection.desc": (
   "Snap your fingers: a running double keeps your path while you vanish under a veil for 2.5 s and mobs chase the double. Your first attack from the veil is a Ta-da!: +3 damage and dazzle.",
   "Щелчок пальцами: бегущий двойник продолжает ваш путь, а вы исчезаете под вуалью на 2,5 с; мобы гонятся за двойником. Первая атака из вуали — «Та-да!»: +3 урона и ослепление.",
   "Fingerschnippen: Ein rennender Doppelgänger läuft deinen Weg weiter, während du 2,5 s unter einem Schleier verschwindest; Mobs jagen den Doppelgänger. Dein erster Angriff aus dem Schleier ist ein Ta-da!: +3 Schaden und Blendung.",
   "Chasquea los dedos: un doble corre por tu camino mientras desapareces bajo un velo 2,5 s y las criaturas persiguen al doble. Tu primer ataque desde el velo es un ¡Tachán!: +3 de daño y deslumbramiento.",
   "Parmak şıklat: koşan bir ikizin yolunu sürdürürken sen 2,5 sn peçenin altında kaybolursun; yaratıklar ikizi kovalar. Peçeden ilk saldırın bir Ta-da!: +3 hasar ve göz kamaştırma.",
   "打个响指：奔跑的替身沿你的路线继续前进，你在面纱下隐身 2.5 秒，生物会追逐替身。面纱中的第一击是“铛铛！”：+3 伤害并致眩。"),
 f"ability.{P}.sinclair_audience_participation.name": ("Audience Participation", "Участие зрителей", "Publikumsbeteiligung", "Participación del Público", "Seyirci Katılımı", "观众互动"),
 f"ability.{P}.sinclair_audience_participation.desc": (
   "Spotlight a volunteer and pull a card from them that holds their ultimate as a stage act. Within 12 s press again to perform it: Footlight Beam, Hat Whirl, Sawing Trick, Card Fan, Curtain or the spare Metamorphosis.",
   "Осветите добровольца прожектором и вытяните из него карту с его ультой в виде сценического номера. В течение 12 с нажмите снова, чтобы исполнить: «Луч рампы», «Шляпный вихрь», «Распил», «Карточный веер», «Занавес» или запасную «Метаморфозу».",
   "Beleuchte einen Freiwilligen und zieh ihm eine Karte mit seinem Ultimate als Bühnennummer. Drück innerhalb von 12 s erneut, um sie aufzuführen: Rampenstrahl, Hutwirbel, Zersägen, Kartenfächer, Vorhang oder die Ersatznummer Metamorphose.",
   "Ilumina a un voluntario y sácale una carta con su definitiva convertida en número de escena. En 12 s pulsa de nuevo para ejecutarla: Rayo de Candilejas, Torbellino de Sombrero, Serrucho, Abanico de Cartas, Telón o la Metamorfosis de reserva.",
   "Bir gönüllüyü spot ışığına al ve ondan nihai yeteneğini sahne numarası olarak taşıyan bir kart çek. 12 sn içinde tekrar basarak sergile: Rampa Işını, Şapka Girdabı, Testere Numarası, Kart Yelpazesi, Perde ya da yedek Başkalaşım.",
   "用聚光灯照亮一名志愿者，从其身上抽出一张承载其终极技能的舞台节目牌。12 秒内再次按下即可表演：脚灯光束、礼帽旋风、锯人术、纸牌扇、幕布，或备用节目“变形术”。"),

 f"act.{P}.sinclair.beam": ("Footlight Beam", "Луч рампы", "Rampenstrahl", "Rayo de Candilejas", "Rampa Işını", "脚灯光束"),
 f"act.{P}.sinclair.nova": ("Hat Whirl", "Шляпный вихрь", "Hutwirbel", "Torbellino de Sombrero", "Şapka Girdabı", "礼帽旋风"),
 f"act.{P}.sinclair.cut": ("Sawing Trick", "Распил", "Zersägen", "Serrucho", "Testere Numarası", "锯人术"),
 f"act.{P}.sinclair.barrage": ("Card Fan", "Карточный веер", "Kartenfächer", "Abanico de Cartas", "Kart Yelpazesi", "纸牌扇"),
 f"act.{P}.sinclair.aegis": ("Curtain", "Занавес", "Vorhang", "Telón", "Perde", "幕布"),
 f"act.{P}.sinclair.finale": ("Metamorphosis", "Метаморфоза", "Metamorphose", "Metamorfosis", "Başkalaşım", "变形术"),

 f"hud.{P}.sinclair.hexed": ("You are a rabbit! %s s", "Вы — кролик! %s с", "Du bist ein Hase! %s s", "¡Eres un conejo! %s s", "Tavşansın! %s sn", "你变成了兔子！%s 秒"),
 f"hud.{P}.sinclair.window": ("Perform: %s s", "Исполнить: %s с", "Aufführen: %s s", "Ejecutar: %s s", "Sergile: %s sn", "表演：%s 秒"),
 f"hud.{P}.sinclair.veil": ("Veil: %s s", "Вуаль: %s с", "Schleier: %s s", "Velo: %s s", "Peçe: %s sn", "面纱：%s 秒"),
 f"hud.{P}.sinclair.assistant": ("Savannah: %s s · swaps: %s", "Саванна: %s с · обменов: %s", "Savannah: %s s · Tausche: %s", "Savannah: %s s · intercambios: %s", "Savannah: %s sn · değişim: %s", "萨凡娜：%s 秒 · 换位：%s"),
 f"hud.{P}.sinclair.ovation": ("Ovation! Ultimate −3 s", "Овация! Ульта −3 с", "Ovation! Ultimate −3 s", "¡Ovación! Definitiva −3 s", "Alkış! Nihai −3 sn", "掌声！终极技能 −3 秒"),
 f"hud.{P}.sinclair.fallback": ("%s (spare act)", "%s (запасной номер)", "%s (Ersatznummer)", "%s (número de reserva)", "%s (yedek numara)", "%s（备用节目）"),

 f"subtitle.{P}.speaker.sinclair": ("Sinclair", "Синклер", "Sinclair", "Sinclair", "Sinclair", "辛克莱"),
 f"subtitle.{P}.sinclair.capture_a": ("A volunteer from the audience!", "Доброволец из зала!", "Ein Freiwilliger aus dem Publikum!", "¡Un voluntario del público!", "Seyircilerden bir gönüllü!", "有请一位观众志愿者！"),
 f"subtitle.{P}.sinclair.capture_b": ("You, yes, you! Step into the light.", "Вы, да-да, вы! Прошу на свет.", "Du, ja, du! Tritt ins Licht.", "¡Tú, sí, tú! Pasa a la luz.", "Sen, evet, sen! Işığa gel.", "你，对，就是你！请到光下来。"),
 f"subtitle.{P}.sinclair.act_beam": ("Mind the footlights!", "Осторожно, рампа!", "Vorsicht, Rampenlicht!", "¡Cuidado con las candilejas!", "Rampa ışıklarına dikkat!", "小心脚灯！"),
 f"subtitle.{P}.sinclair.act_nova": ("Keep your eyes on the hat…", "Следите за шляпой…", "Behaltet den Hut im Auge…", "No le quiten el ojo al sombrero…", "Gözünüz şapkada olsun…", "盯紧这顶帽子……"),
 f"subtitle.{P}.sinclair.act_cut": ("And now — sawing a volunteer in half!", "А теперь — распил добровольца!", "Und nun — wir zersägen einen Freiwilligen!", "¡Y ahora, partiremos a un voluntario en dos!", "Ve şimdi — gönüllüyü ikiye biçiyoruz!", "接下来——把志愿者锯成两半！"),
 f"subtitle.{P}.sinclair.act_barrage": ("Pick a card. Any card!", "Выберите карту. Любую!", "Zieh eine Karte. Irgendeine!", "Elige una carta. ¡Cualquiera!", "Bir kart seç. Herhangi birini!", "抽一张牌，随便哪张！"),
 f"subtitle.{P}.sinclair.act_aegis": ("Curtain, please!", "Занавес, пожалуйста!", "Vorhang, bitte!", "¡Telón, por favor!", "Perde, lütfen!", "请落幕！"),
 f"subtitle.{P}.sinclair.act_finale": ("Into the chest, and… metamorphosis!", "В сундук — и… метаморфоза!", "Ab in die Truhe und… Metamorphose!", "Al baúl y… ¡metamorfosis!", "Sandığa, ve… başkalaşım!", "进箱子……变形！"),
 f"subtitle.{P}.sinclair.bow": ("Thank you, thank you, you're too kind!", "Благодарю, благодарю, вы слишком добры!", "Danke, danke, ihr seid zu gütig!", "¡Gracias, gracias, son muy amables!", "Teşekkürler, teşekkürler, çok naziksiniz!", "谢谢，谢谢，各位太客气了！"),
 f"subtitle.{P}.sinclair.window_lost": ("The audience grows restless…", "Публика заскучала…", "Das Publikum wird unruhig…", "El público se impacienta…", "Seyirci sabırsızlanıyor…", "观众开始坐不住了……"),
 f"subtitle.{P}.sinclair.summon": ("Savannah, my dear, the stage is yours!", "Саванна, дорогая, сцена твоя!", "Savannah, meine Liebe, die Bühne gehört dir!", "¡Savannah, querida, el escenario es tuyo!", "Savannah, canım, sahne senin!", "萨凡娜，亲爱的，舞台交给你了！"),
 f"subtitle.{P}.sinclair.hex": ("Abracadabra… a rabbit!", "Абракадабра… кролик!", "Abrakadabra… ein Hase!", "Abracadabra… ¡un conejo!", "Abrakadabra… bir tavşan!", "阿布拉卡达布拉……变兔子！"),
 f"subtitle.{P}.sinclair.snap": ("Now you see me…", "Сейчас вы меня видите…", "Jetzt seht ihr mich…", "Ahora me ven…", "Şimdi beni görüyorsunuz…", "现在你看得见我……"),
 f"subtitle.{P}.sinclair.tada": ("Ta-da!", "Та-да!", "Ta-da!", "¡Tachán!", "Ta-da!", "铛铛！"),

 f"entity.{P}.sinclair_spark": ("Prestige Spark", "Искра «Престижа»", "Prestige-Funke", "Chispa de Prestigio", "Prestij Kıvılcımı", "“荣耀”火花"),
 f"entity.{P}.sinclair_vexing_bolt": ("Vexing Bolt", "Досадная молния", "Ärgerlicher Blitz", "Rayo Fastidioso", "Can Sıkıcı Şimşek", "恼人闪电"),
 f"entity.{P}.sinclair_assistant": ("Savannah", "Саванна", "Savannah", "Savannah", "Savannah", "萨凡娜"),
 f"entity.{P}.sinclair_decoy": ("Sinclair's Double", "Двойник Синклера", "Sinclairs Doppelgänger", "Doble de Sinclair", "Sinclair'in İkizi", "辛克莱的替身"),
 f"entity.{P}.sinclair_hex_hat": ("Phantom Top Hat", "Призрачный цилиндр", "Geisterzylinder", "Chistera Fantasma", "Hayalet Silindir Şapka", "幽灵礼帽"),
 f"entity.{P}.sinclair_hex_mark": ("Rabbit Hex", "Кроличье проклятие", "Hasenfluch", "Maleficio del Conejo", "Tavşan Laneti", "兔子咒"),
 f"entity.{P}.sinclair_act_prop": ("Stage Prop", "Реквизит", "Requisite", "Utilería", "Sahne Aksesuarı", "舞台道具"),
 f"entity.{P}.sinclair_act_card": ("Playing Card", "Игральная карта", "Spielkarte", "Naipe", "Oyun Kartı", "扑克牌"),
}

SOUNDS = {  # subtitles.viltrumitecore.sinclair_<name>; en mirrors tools/sfx/sinclair.py RECIPES
 "wand_shot": ("Wand fires", "Палочка стреляет", "Zauberstab feuert", "La varita dispara", "Asa ateşliyor", "魔杖射击"),
 "spark_hit": ("Spark hits", "Искра попадает", "Funke trifft", "La chispa impacta", "Kıvılcım isabet ediyor", "火花命中"),
 "wand_reload": ("Wand twirls", "Палочка вращается", "Zauberstab wirbelt", "La varita gira", "Asa dönüyor", "魔杖旋转"),
 "bolt_cast": ("Vexing Bolt flicked", "Брошена досадная молния", "Ärgerlicher Blitz geworfen", "Rayo Fastidioso lanzado", "Can Sıkıcı Şimşek fırlatıldı", "掷出恼人闪电"),
 "bolt_snap": ("Bolt redirected", "Молния развёрнута", "Blitz umgelenkt", "Rayo desviado", "Şimşek yön değiştirdi", "闪电转向"),
 "bolt_hit": ("Vexing Bolt bursts", "Досадная молния взрывается", "Ärgerlicher Blitz zerplatzt", "Estalla el Rayo Fastidioso", "Can Sıkıcı Şimşek patlıyor", "恼人闪电爆裂"),
 "assistant_appear": ("Savannah appears", "Появляется Саванна", "Savannah erscheint", "Aparece Savannah", "Savannah beliriyor", "萨凡娜登场"),
 "swap": ("Places swapped", "Обмен местами", "Plätze getauscht", "Posiciones intercambiadas", "Yer değiştirildi", "交换位置"),
 "assistant_leave": ("Savannah takes her leave", "Саванна уходит со сцены", "Savannah verabschiedet sich", "Savannah se despide", "Savannah sahneden çekiliyor", "萨凡娜谢幕离场"),
 "hex_cast": ("Hat flourish", "Взмах цилиндром", "Hutschwenk", "Floreo de sombrero", "Şapka gösterisi", "礼帽一挥"),
 "hex_poof": ("Turned into a rabbit", "Превращение в кролика", "In einen Hasen verwandelt", "Convertido en conejo", "Tavşana dönüştü", "变成兔子"),
 "hex_end": ("Rabbit hex wears off", "Кроличье проклятие спадает", "Hasenfluch lässt nach", "Se disipa el maleficio", "Tavşan laneti geçiyor", "兔子咒消散"),
 "snap": ("Sinclair snaps and vanishes", "Синклер щёлкает и исчезает", "Sinclair schnippt und verschwindet", "Sinclair chasquea y desaparece", "Sinclair şıklatıp kayboluyor", "辛克莱响指隐身"),
 "decoy_reveal": ("Decoy vanishes", "Двойник исчезает", "Doppelgänger verschwindet", "El doble desaparece", "İkiz kayboluyor", "替身消失"),
 "tada": ("Ta-da!", "Та-да!", "Ta-da!", "¡Tachán!", "Ta-da!", "铛铛！"),
 "refuse": ("No volunteer", "Нет добровольца", "Kein Freiwilliger", "Sin voluntario", "Gönüllü yok", "没有志愿者"),
 "ovation": ("Ovation", "Овация", "Ovation", "Ovación", "Alkış", "掌声"),
 "ult_capture": ("Spotlight on a volunteer", "Прожектор на добровольца", "Scheinwerfer auf einen Freiwilligen", "Foco sobre un voluntario", "Gönüllüye spot ışığı", "聚光灯照向志愿者"),
 "ult_card": ("A card flies out", "Вылетает карта", "Eine Karte fliegt heraus", "Sale volando una carta", "Bir kart fırlıyor", "一张牌飞出"),
 "act_drumroll": ("Drumroll", "Барабанная дробь", "Trommelwirbel", "Redoble de tambor", "Trampet sesi", "击鼓"),
 "act_beam": ("Footlight beam", "Луч рампы", "Rampenstrahl", "Rayo de candilejas", "Rampa ışını", "脚灯光束"),
 "act_nova_whirl": ("Hat whirls", "Шляпа вращается", "Hut wirbelt", "El sombrero gira", "Şapka dönüyor", "礼帽旋转"),
 "act_nova_burst": ("Hat bursts into cards", "Шляпа взрывается картами", "Hut zerplatzt in Karten", "El sombrero estalla en cartas", "Şapka kartlara patlıyor", "礼帽炸成纸牌"),
 "act_cut_saw": ("Sawing trick", "Распил", "Zersägetrick", "Truco del serrucho", "Testere numarası", "锯人术"),
 "card_hit": ("Card strikes", "Карта бьёт", "Karte trifft", "La carta golpea", "Kart çarpıyor", "纸牌击中"),
 "act_card_volley": ("Card volley", "Залп карт", "Kartensalve", "Ráfaga de cartas", "Kart yaylımı", "纸牌齐射"),
 "act_curtain": ("Curtain falls", "Опускается занавес", "Vorhang fällt", "Cae el telón", "Perde iniyor", "幕布落下"),
 "act_curtain_open": ("Curtain flies open", "Занавес распахивается", "Vorhang fliegt auf", "El telón se abre de golpe", "Perde açılıyor", "幕布猛然拉开"),
 "act_chest": ("Magic chest lands", "Падает волшебный сундук", "Zaubertruhe landet", "Cae el baúl mágico", "Sihirli sandık düşüyor", "魔术箱落地"),
 "act_sword": ("Sword through the chest", "Меч сквозь сундук", "Schwert durch die Truhe", "Espada a través del baúl", "Kılıç sandığı deliyor", "剑刺穿箱子"),
 "act_bow": ("Sinclair takes a bow", "Синклер кланяется", "Sinclair verbeugt sich", "Sinclair hace una reverencia", "Sinclair selam veriyor", "辛克莱鞠躬"),
}
for name, row in SOUNDS.items():
    ROWS[f"subtitles.{P}.sinclair_{name}"] = row

# Copy cinematic card (design 2026-11-02 §2.3) and the /sinclair cinematic debug command
C = f"copy.{P}.sinclair."
COPY = {
 "header": ("Act copied!", "Номер скопирован!", "Nummer kopiert!", "¡Número copiado!", "Numara kopyalandı!", "节目已复制！"),
 "header.volunteer": ("Your act — borrowed!", "Ваш номер — позаимствован!", "Deine Nummer — geborgt!", "¡Tu número, prestado!", "Numaran ödünç alındı!", "你的节目被借走了！"),
 "header.fallback": ("Spare act", "Запасной номер", "Ersatznummer", "Número de reserva", "Yedek numara", "备用节目"),
 "origin.fallback": ("Nothing to borrow — a trick of his own", "Копировать нечего — собственный фокус", "Nichts zu borgen — ein eigener Trick", "Nada que copiar: un truco propio", "Ödünç alınacak bir şey yok — kendi numarası", "无可借用——这是他自己的戏法"),
 "origin.contract": ("Staged from the volunteer's signature move", "Поставлено по коронному приёму добровольца", "Inszeniert nach dem Markenzeichen des Freiwilligen", "Montado a partir del movimiento estrella del voluntario", "Gönüllünün imza hareketinden sahnelendi", "依照志愿者的招牌招式编排"),
 "origin.homelander": ("after Heat Vision", "по мотивам «Теплового зрения»", "nach dem Hitzeblick", "inspirado en la Visión Calorífica", "Isı Görüşü'nden uyarlandı", "改编自热视线"),
 "origin.reinhard": ("after World Cut", "по мотивам «Рассечения мира»", "nach dem Weltenschnitt", "inspirado en el Corte del Mundo", "Dünya Kesiği'nden uyarlandı", "改编自世界斩"),
 "origin.ironman": ("after the Micro-missiles", "по мотивам «Микроракет»", "nach den Mikroraketen", "inspirado en los Micromisiles", "Mikro füzelerden uyarlandı", "改编自微型导弹"),
 "origin.regulus": ("after an untouchable defence", "по мотивам неуязвимой защиты", "nach einer unantastbaren Abwehr", "inspirado en una defensa intocable", "dokunulmaz bir savunmadan uyarlandı", "改编自无懈可击的防御"),
 "origin.mob.beam": ("after a creature's glare", "по мотивам взгляда существа", "nach dem Blick einer Kreatur", "inspirado en la mirada de una criatura", "bir yaratığın bakışından uyarlandı", "改编自生物的凝视"),
 "origin.mob.nova": ("after a creature's blast", "по мотивам взрыва существа", "nach der Explosion einer Kreatur", "inspirado en el estallido de una criatura", "bir yaratığın patlamasından uyarlandı", "改编自生物的爆炸"),
 "origin.mob.cut": ("after a creature's blade", "по мотивам клинка существа", "nach der Klinge einer Kreatur", "inspirado en la hoja de una criatura", "bir yaratığın bıçağından uyarlandı", "改编自生物的利刃"),
 "origin.mob.barrage": ("after a creature's volley", "по мотивам залпа существа", "nach der Salve einer Kreatur", "inspirado en la ráfaga de una criatura", "bir yaratığın yaylımından uyarlandı", "改编自生物的齐射"),
 "origin.mob.aegis": ("after a creature's shell", "по мотивам панциря существа", "nach dem Panzer einer Kreatur", "inspirado en el caparazón de una criatura", "bir yaratığın kabuğundan uyarlandı", "改编自生物的甲壳"),
 "desc.beam": ("A footlight ray from the card, pulsing for two seconds, then an overdrive flash.", "Луч рампы из карты пульсирует две секунды, затем — вспышка на пределе.", "Ein Rampenstrahl aus der Karte pulsiert zwei Sekunden, dann ein greller Höhepunkt.", "Un rayo de candilejas sale de la carta durante dos segundos y termina en un destello.", "Karttan çıkan rampa ışını iki saniye atar, ardından son bir parlama.", "纸牌射出脚灯光束，脉动两秒后迸发强光。"),
 "desc.nova": ("The top hat flies out, gathers everyone in and bursts into cards.", "Цилиндр улетает, стягивает всех к себе и взрывается картами.", "Der Zylinder fliegt los, zieht alle heran und zerplatzt in Karten.", "La chistera sale volando, atrae a todos y estalla en cartas.", "Silindir şapka uçar, herkesi içine çeker ve kartlara patlar.", "礼帽飞出，把众人吸拢，随后炸成纸牌。"),
 "desc.cut": ("Two saw strokes along a line of cards, then the grand cut.", "Два взмаха пилы вдоль ряда карт — и большой распил.", "Zwei Sägezüge entlang einer Kartenreihe, dann der große Schnitt.", "Dos pasadas de sierra por una fila de cartas y luego el gran corte.", "Bir kart sırası boyunca iki testere darbesi, ardından büyük kesik.", "沿着一排纸牌锯两下，然后是致命一斩。"),
 "desc.barrage": ("Three card volleys, then a finale volley from above.", "Три залпа картами и финальный залп сверху.", "Drei Kartensalven, dann eine Schlusssalve von oben.", "Tres ráfagas de cartas y una ráfaga final desde arriba.", "Üç kart yaylımı, ardından yukarıdan final yaylımı.", "三轮纸牌齐射，最后从头顶落下终幕一击。"),
 "desc.aegis": ("A velvet curtain shields him, then flies open in a blast.", "Бархатный занавес укрывает его и распахивается взрывом.", "Ein Samtvorhang schützt ihn und fliegt dann krachend auf.", "Un telón de terciopelo lo protege y luego se abre de golpe.", "Kadife bir perde onu korur, sonra patlayarak açılır.", "天鹅绒幕布护住他，随后猛然炸开。"),
 "desc.finale": ("A chest falls, three swords pierce it — and the volunteer is transformed.", "Падает сундук, его пронзают три меча — и доброволец преображается.", "Eine Truhe fällt, drei Schwerter durchbohren sie — und der Freiwillige verwandelt sich.", "Cae un baúl, tres espadas lo atraviesan y el voluntario se transforma.", "Bir sandık düşer, üç kılıç onu deler — ve gönüllü dönüşür.", "箱子落下，三把剑刺穿它——志愿者随之变形。"),
 "volunteer": ("A volunteer from the audience", "Доброволец из зала", "Ein Freiwilliger aus dem Publikum", "Un voluntario del público", "Seyircilerden bir gönüllü", "一位观众志愿者"),
 "hero.unknown": ("A mysterious guest", "Таинственный гость", "Ein geheimnisvoller Gast", "Un invitado misterioso", "Gizemli bir konuk", "神秘来宾"),
 "hero.human": ("Human", "Человек", "Mensch", "Humano", "İnsan", "人类"),
 "hero.viltrumite": ("Viltrumite", "Вилтрумит", "Viltrumit", "Viltrumita", "Viltrumit", "维尔特鲁姆人"),
 "hero.regulus": ("Regulus", "Регулус", "Regulus", "Regulus", "Regulus", "雷古勒斯"),
 "hero.homelander": ("Homelander", "Хоумлендер", "Homelander", "Homelander", "Homelander", "祖国人"),
 "hero.ironman": ("Iron Man", "Железный человек", "Iron Man", "Iron Man", "Iron Man", "钢铁侠"),
 "hero.reinhard": ("Reinhard van Astrea", "Рейнхард ван Астрея", "Reinhard van Astrea", "Reinhard van Astrea", "Reinhard van Astrea", "莱因哈鲁特·范·阿斯特雷亚"),
 "hero.thor": ("Thor", "Тор", "Thor", "Thor", "Thor", "雷神"),
 "hero.dio": ("DIO", "ДИО", "DIO", "DIO", "DIO", "DIO"),
 "hero.sinclair": ("The Magnificent Sinclair", "Великолепный Синклер", "Der Großartige Sinclair", "El Magnífico Sinclair", "Muhteşem Sinclair", "了不起的辛克莱"),
 "hero.jinwoo": ("Sung Jinwoo", "Сон Джину", "Sung Jinwoo", "Sung Jinwoo", "Sung Jinwoo", "成振宇"),
}
for key, row in COPY.items():
    ROWS[C + key] = row

K = f"commands.{P}.sinclair.cinematic."
for key, row in {
 "on": ("Sinclair's copy cinematic is on.", "Кинематографичное копирование Синклера включено.", "Sinclairs Kopier-Kinematik ist an.", "La cinemática de copia de Sinclair está activada.", "Sinclair'in kopyalama sineması açık.", "辛克莱的复制过场已开启。"),
 "off": ("Sinclair's copy cinematic is off: copies happen instantly, without the card, camera or pause.", "Кинематографичное копирование Синклера выключено: копия мгновенная, без карты, камеры и паузы.", "Sinclairs Kopier-Kinematik ist aus: Kopien geschehen sofort, ohne Karte, Kamera und Pause.", "La cinemática de copia de Sinclair está desactivada: se copia al instante, sin carta, cámara ni pausa.", "Sinclair'in kopyalama sineması kapalı: kopya anında olur, kart, kamera ve duraklama olmadan.", "辛克莱的复制过场已关闭：立即复制，不显示卡牌、镜头和停顿。"),
 "status": ("Sinclair's copy cinematic: %s", "Кинематографичное копирование Синклера: %s", "Sinclairs Kopier-Kinematik: %s", "Cinemática de copia de Sinclair: %s", "Sinclair'in kopyalama sineması: %s", "辛克莱的复制过场：%s"),
 "state_on": ("on", "включено", "an", "activada", "açık", "开启"),
 "state_off": ("off", "выключено", "aus", "desactivada", "kapalı", "关闭"),
}.items():
    ROWS[K + key] = row

NEW_SOUNDS = {
 "copy_sting": ("Magic sting: the act is copied", "Волшебный аккорд: номер скопирован", "Zauberakkord: Nummer kopiert", "Acorde mágico: número copiado", "Sihirli akor: numara kopyalandı", "魔法和弦：节目被复制"),
 "copy_unfold": ("Card unfolds", "Карта раскрывается", "Karte entfaltet sich", "La carta se despliega", "Kart açılıyor", "纸牌展开"),
 "copy_fold": ("Card folds away", "Карта складывается", "Karte faltet sich zusammen", "La carta se pliega", "Kart katlanıyor", "纸牌收起"),
 "copy_return": ("Time flows again", "Время идёт дальше", "Die Zeit läuft weiter", "El tiempo vuelve a correr", "Zaman yeniden akıyor", "时间恢复流动"),
 "act_footlights": ("Footlights flare up", "Вспыхивает рампа", "Rampenlicht flammt auf", "Se encienden las candilejas", "Rampa ışıkları parlıyor", "脚灯亮起"),
 "act_beam_charge": ("Beam charges", "Луч набирает силу", "Strahl lädt auf", "El rayo se carga", "Işın güçleniyor", "光束蓄能"),
 "act_beam_burst": ("Beam overdrive", "Луч на пределе", "Strahl-Überladung", "Rayo a máxima potencia", "Işın son güçte", "光束超载"),
 "act_hat_spin": ("Hat spins", "Шляпа крутится", "Hut dreht sich", "El sombrero da vueltas", "Şapka dönüyor", "礼帽打转"),
 "act_saw_rasp": ("Saw rasps", "Скрежет пилы", "Säge kratzt", "Raspa la sierra", "Testere hırıldıyor", "锯子刮擦"),
 "act_cut_slash": ("Grand cut", "Большой распил", "Großer Schnitt", "Gran corte", "Büyük kesik", "致命一斩"),
 "act_card_fan": ("Cards fan out", "Карты раскрываются веером", "Karten fächern sich auf", "Las cartas se abren en abanico", "Kartlar yelpaze gibi açılıyor", "纸牌展开成扇"),
 "act_card_finale": ("Finale volley", "Финальный залп", "Schlusssalve", "Ráfaga final", "Final yaylımı", "终幕齐射"),
 "act_curtain_rustle": ("Curtain rustles", "Шуршит занавес", "Vorhang raschelt", "Cruje el telón", "Perde hışırdıyor", "幕布沙沙作响"),
 "act_chest_rattle": ("Chest rattles", "Сундук дрожит", "Truhe rappelt", "El baúl tiembla", "Sandık sarsılıyor", "箱子晃动"),
}
for name, row in NEW_SOUNDS.items():
    ROWS[f"subtitles.{P}.sinclair_{name}"] = row

COL = {"en_us": 0, "ru_ru": 1, "de_de": 2, "tr_tr": 4, "zh_cn": 5}
for es in ("es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve"):
    COL[es] = 3


def main():
    check = "--check" in sys.argv
    stale = []
    os.makedirs(OUT, exist_ok=True)
    for locale, col in sorted(COL.items()):
        path = os.path.join(OUT, locale + ".json")
        text = json.dumps({key: row[col] for key, row in ROWS.items()}, ensure_ascii=False, indent=2) + "\n"
        old = open(path, encoding="utf-8").read() if os.path.isfile(path) else None
        if old != text:
            stale.append(locale)
            if not check:
                with open(path, "w", encoding="utf-8") as fh:
                    fh.write(text)
    print(("stale: " if check else "updated: ") + (", ".join(stale) or "none"), f"({len(ROWS)} keys)")
    return 1 if check and stale else 0


if __name__ == "__main__":
    sys.exit(main())
