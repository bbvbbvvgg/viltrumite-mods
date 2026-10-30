#!/usr/bin/env python3
"""Regulus Reborn lang: writes viltrumitecore/src/main/lang-overlays/regulus/<locale>.json for all 12
locales (every base locale file is past the one-request push size; processResources merges the
overlays, and an overlay key wins over the base file). Run: python3 tools/lang/regulus_reborn_lang.py
Languages: en, ru, de, es (all seven es_* locales), tr, zh.
"""
import json
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../viltrumitecore/src/main/lang-overlays/regulus")
LOCALES = {"en_us": "en", "ru_ru": "ru", "de_de": "de", "tr_tr": "tr", "zh_cn": "zh",
           "es_ar": "es", "es_cl": "es", "es_ec": "es", "es_es": "es", "es_mx": "es", "es_uy": "es", "es_ve": "es"}

L = {}


def k(key, en, ru, de, es, tr, zh):
    L[key] = {"en": en, "ru": ru, "de": de, "es": es, "tr": tr, "zh": zh}


def desc(cd, kind, body, mode=None):
    """Ability tooltip in the house format: cooldown, type, body, hero tag."""
    return {lang: "<grey>%s: <lime>%s\n<grey>%s: <yellow>%s\n\n%s\n\n<blue>%s" % (
        HDR[lang][0], cd[lang], HDR[lang][1], kind[lang], "\n".join("<grey>" + line for line in body[lang]), HDR[lang][2])
        for lang in HDR}


HDR = {"en": ("Cooldown", "Type", "Regulus"), "ru": ("Перезарядка", "Тип", "Регулус"), "de": ("Abklingzeit", "Typ", "Regulus"),
       "es": ("Enfriamiento", "Tipo", "Regulus"), "tr": ("Bekleme", "Tür", "Regulus"), "zh": ("冷却", "类型", "雷古勒斯")}


def T(en, ru, de, es, tr, zh):
    return {"en": en, "ru": ru, "de": de, "es": es, "tr": tr, "zh": zh}


def ability(key, name, cd, kind, body):
    for lang, text in name.items():
        L.setdefault("ability.viltrumitecore.regulus_%s.name" % key, {})[lang] = "<gold>" + text
    for lang, text in desc(cd, kind, body).items():
        L.setdefault("ability.viltrumitecore.regulus_%s.desc" % key, {})[lang] = text


TOGGLE = T("Toggle", "Переключение", "Umschalten", "Alternar", "Aç/Kapa", "切换")
CAST = T("Cast", "Применение", "Wirken", "Lanzamiento", "Kullanım", "施放")
STANCE = T("Stance", "Стойка", "Haltung", "Postura", "Duruş", "架势")
ULT = T("Ultimate", "Ультимейт", "Ultimative", "Definitiva", "Nihai", "终极技")
COMBO = T("Combo (LMB, empty hand)", "Комбо (ЛКМ, пустая рука)", "Kombo (LMT, leere Hand)", "Combo (clic izq., mano vacía)",
          "Kombo (sol tık, boş el)", "连击（左键，空手）")
KEY = T("Heart key (MMB)", "Клавиша сердца (СКМ)", "Herztaste (MMT)", "Tecla del corazón (clic central)", "Kalp tuşu (orta tık)", "心脏键（中键）")

k("hero.viltrumitecore.regulus", "Regulus Corneas", "Регулус Корнеас", "Regulus Corneas", "Regulus Corneas", "Regulus Corneas", "雷古勒斯·柯尼亚斯")
k("subtitle.viltrumitecore.speaker.regulus", "Regulus", "Регулус", "Regulus", "Regulus", "Regulus", "雷古勒斯")
# key.viltrumitecore.assign_heart stays in the base files: the key is shared by every hero and
# "mark heart carrier" is still what Little King does. Overlays never redefine base keys.
k("scan.viltrumitecore.regulus.stopped_time", "Lion's Heart: his time is stopped — nothing reaches him",
  "Сердце льва: его время остановлено — до него ничто не доходит", "Löwenherz: seine Zeit steht still — nichts erreicht ihn",
  "Corazón de León: su tiempo está detenido; nada lo alcanza", "Aslan Yüreği: zamanı durmuş — hiçbir şey ona ulaşamaz",
  "狮子之心：他的时间已停止——任何东西都碰不到他")

ability("lions_heart", T("Lion's Heart", "Сердце льва", "Löwenherz", "Corazón de León", "Aslan Yüreği", "狮子之心"),
        T("1s", "1 с", "1 s", "1 s", "1 sn", "1秒"), TOGGLE, T(
    ["Stop your own time. No damage, push or effect", "reaches you; blows recoil 30% onto the attacker,", "projectiles stop dead in the air.",
     "Your heart stops too: without a living host", "(Little King) strain rises and after ~5 s", "the heart seizes (Lion locked for 8 s)."],
    ["Остановите собственное время. До вас не доходят", "урон, толчки и эффекты; 30% удара возвращается", "нападающему, снаряды замирают в воздухе.",
     "Сердце тоже стоит: без живого носителя", "(Маленький король) растёт нагрузка, и через ~5 с", "сердце схватывает (Лев закрыт на 8 с)."],
    ["Halte deine eigene Zeit an. Kein Schaden, Stoß", "oder Effekt erreicht dich; 30% eines Schlags prallen", "auf den Angreifer zurück, Geschosse erstarren.",
     "Auch dein Herz steht still: ohne lebenden Wirt", "(Kleiner König) steigt die Last, nach ~5 s", "verkrampft das Herz (Löwe 8 s gesperrt)."],
    ["Detén tu propio tiempo. Ningún daño, empuje o efecto", "te alcanza; el 30% del golpe rebota al atacante,", "los proyectiles se detienen en el aire.",
     "Tu corazón también se detiene: sin un huésped vivo", "(Pequeño Rey) la tensión sube y tras ~5 s", "el corazón sufre un espasmo (León bloqueado 8 s)."],
    ["Kendi zamanını durdur. Hiçbir hasar, itme ya da etki", "sana ulaşmaz; darbenin %30'u saldırana döner,", "mermiler havada donar.",
     "Kalbin de durur: canlı bir taşıyıcı olmadan", "(Küçük Kral) yük artar ve ~5 sn sonra", "kalp kasılır (Aslan 8 sn kilitli)."],
    ["停止自己的时间。任何伤害、击退或效果", "都无法触及你；攻击者承受30%反弹，", "投射物在空中静止。",
     "你的心脏也会停止：没有活着的宿主", "（小王）负荷上升，约5秒后", "心脏痉挛（狮子锁定8秒）。"]))

ability("stopped_sand", T("Stopped Sand", "Застывший песок", "Erstarrter Sand", "Arena detenida", "Donmuş Kum", "静止之沙"),
        T("6s", "6 с", "6 s", "6 s", "6 sn", "6秒"), CAST, T(
    ["Scoop the dust at your feet and throw it:", "9 grains, each stopped in time, fly 40 blocks,", "pierce up to 3 enemies (4, in Lion 6)",
     "and break soft blocks where the world allows."],
    ["Зачерпните пыль под ногами и бросьте её:", "9 песчинок, остановленных во времени, летят на 40 блоков,", "пробивают до 3 врагов (4, во Льве 6)",
     "и ломают мягкие блоки, где это разрешает мир."],
    ["Schöpfe den Staub zu deinen Füßen und wirf ihn:", "9 in der Zeit erstarrte Körner fliegen 40 Blöcke,", "durchbohren bis zu 3 Feinde (4, im Löwen 6)",
     "und brechen weiche Blöcke, wo die Welt es erlaubt."],
    ["Recoge el polvo a tus pies y lánzalo:", "9 granos detenidos en el tiempo vuelan 40 bloques,", "atraviesan hasta 3 enemigos (4, en León 6)",
     "y rompen bloques blandos donde el mundo lo permite."],
    ["Ayağının dibindeki tozu avuçla ve fırlat:", "zamanda durmuş 9 tane 40 blok uçar,", "en fazla 3 düşmanı deler (4, Aslan'da 6)",
     "ve dünyanın izin verdiği yerde yumuşak blokları kırar."],
    ["抓起脚下的尘土掷出：", "9粒在时间中静止的沙粒飞行40格，", "最多贯穿3名敌人（4，狮子中6）",
     "并在世界规则允许时击碎软方块。"]))

ability("air_severance", T("Air Severance", "Рассечение воздуха", "Luftschnitt", "Corte del aire", "Hava Yarığı", "断空"),
        T("9s", "9 с", "9 s", "9 s", "9 sn", "9秒"), CAST, T(
    ["Fling a sheet of stopped air thin as glass:", "it widens from 3 to 9 blocks over 30 blocks and cuts", "through enemies (12, in Lion 18) and blocks up to stone.",
     "Sneak for a vertical cut."],
    ["Бросьте лист остановленного воздуха, тонкий как стекло:", "он расширяется с 3 до 9 блоков на 30 блоках и режет", "врагов (12, во Льве 18) и блоки до камня.",
     "Присядьте для вертикального разреза."],
    ["Schleudere eine Scheibe erstarrter Luft, dünn wie Glas:", "sie wächst auf 30 Blöcken von 3 auf 9 Blöcke und schneidet", "Feinde (12, im Löwen 18) und Blöcke bis Stein.",
     "Schleichen: senkrechter Schnitt."],
    ["Lanza una lámina de aire detenido, fina como el vidrio:", "se ensancha de 3 a 9 bloques en 30 y corta", "enemigos (12, en León 18) y bloques hasta la piedra.",
     "Agáchate para un corte vertical."],
    ["Cam kadar ince bir durmuş hava tabakası fırlat:", "30 blokta 3'ten 9 bloğa genişler ve", "düşmanları (12, Aslan'da 18) ve taşa kadar blokları keser.",
     "Dikey kesim için eğil."],
    ["掷出一片薄如玻璃的静止空气：", "在30格内从3格扩展到9格，", "切开敌人（12，狮子中18）和石头以下的方块。",
     "潜行时为竖斩。"]))

ability("procession", T("Procession", "Шествие", "Prozession", "Procesión", "Alay", "行进"),
        T("12s", "12 с", "12 s", "12 s", "12 sn", "12秒"), CAST, T(
    ["Lion only. Walk straight ahead for 2.5 s at your own", "unhurried pace: whatever stands in the way is struck (10)", "and blocks up to obsidian-weak break where allowed."],
    ["Только во Льве. 2,5 с идите прямо своим неспешным", "шагом: всё на пути получает удар (10),", "а блоки ломаются, где это разрешено."],
    ["Nur im Löwen. Gehe 2,5 s in deinem gemächlichen Schritt", "geradeaus: alles im Weg wird getroffen (10),", "Blöcke brechen, wo es erlaubt ist."],
    ["Solo en León. Camina recto 2,5 s a tu paso sin prisa:", "todo lo que esté en el camino recibe un golpe (10)", "y los bloques se rompen donde se permita."],
    ["Yalnızca Aslan'da. Kendi acelesiz adımınla 2,5 sn", "düz yürü: yoldaki her şey darbe alır (10),", "izin verilen yerde bloklar kırılır."],
    ["仅限狮子状态。以从容的步伐直行2.5秒：", "挡路的一切都会受到打击（10），", "允许时方块会被破坏。"]))

ability("right_of_reply", T("Right of Reply", "Право на ответ", "Recht auf Antwort", "Derecho de réplica", "Cevap Hakkı", "答辩之权"),
        T("6s / 10s", "6 / 10 с", "6 / 10 s", "6 / 10 s", "6 / 10 sn", "6 / 10秒"), STANCE, T(
    ["Raise a pane of stopped air for half a second:", "the first blow stops against it and is answered", "with a strike (8 + half the blocked damage)."],
    ["Поднимите на полсекунды стекло остановленного воздуха:", "первый удар замирает о него, и вы отвечаете", "ударом (8 + половина остановленного урона)."],
    ["Hebe für eine halbe Sekunde eine Scheibe erstarrter Luft:", "der erste Schlag erstarrt daran und wird mit", "einem Hieb beantwortet (8 + halber geblockter Schaden)."],
    ["Alza medio segundo un cristal de aire detenido:", "el primer golpe se detiene en él y respondes", "con un golpe (8 + la mitad del daño bloqueado)."],
    ["Yarım saniyeliğine durmuş havadan bir cam kaldır:", "ilk darbe ona çarpıp durur ve sen", "bir darbeyle cevap verirsin (8 + engellenen hasarın yarısı)."],
    ["升起半秒的静止空气之壁：", "第一次攻击被它挡住，你以", "一击回应（8 + 被挡伤害的一半）。"]))

ability("kingdom_of_one", T("Kingdom of One", "Королевство одного", "Königreich des Einen", "Reino de uno", "Tek Kişilik Krallık", "一人之国"),
        T("100s", "100 с", "100 s", "100 s", "100 sn", "100秒"), ULT, T(
    ["Judge one target within 32 blocks: it freezes, the air", "around it is gathered into a dome of stopped grains,", "five waves strike it (4 each), then one flick of a finger",
     "(16) launches it and cracks the ground. 7.5 s; ends", "safely if the target dies, escapes or leaves."],
    ["Осудите одну цель в 32 блоках: она застывает, воздух", "вокруг собирается в купол остановленных песчинок,", "пять волн бьют её (по 4), затем один щелчок пальцем",
     "(16) отбрасывает её и раскалывает землю. 7,5 с; сцена", "безопасно прерывается, если цель умерла, ушла или исчезла."],
    ["Richte ein Ziel im Umkreis von 32 Blöcken: es erstarrt, die Luft", "darum wird zu einer Kuppel erstarrter Körner,", "fünf Wellen treffen es (je 4), dann schleudert ein Fingerschnippen",
     "(16) es fort und spaltet den Boden. 7,5 s; endet sicher,", "wenn das Ziel stirbt, flieht oder verschwindet."],
    ["Juzga a un objetivo a 32 bloques: se congela, el aire", "a su alrededor forma una cúpula de granos detenidos,", "cinco oleadas lo golpean (4 cada una) y un chasquido de dedo",
     "(16) lo lanza y agrieta el suelo. 7,5 s; termina sin riesgo", "si el objetivo muere, escapa o desaparece."],
    ["32 blok içindeki bir hedefi yargıla: donar, etrafındaki", "hava durmuş tanelerden bir kubbeye toplanır,", "beş dalga ona vurur (her biri 4), sonra tek bir fiske",
     "(16) onu fırlatır ve zemini çatlatır. 7,5 sn; hedef ölür,", "kaçar ya da kaybolursa güvenle biter."],
    ["审判32格内的一个目标：它被冻结，周围的空气", "聚成静止沙粒的穹顶，", "五波攻击命中它（每波4），随后一记弹指",
     "（16）将其击飞并震裂地面。7.5秒；目标死亡、", "逃脱或消失时安全结束。"]))

ability("greeds_touch", T("Greed's Touch", "Касание жадности", "Berührung der Gier", "Toque de la Codicia", "Açgözlülüğün Dokunuşu", "贪婪之触"),
        T("—", "—", "—", "—", "—", "—"), COMBO, T(
    ["A bored touch in stopped time: backhand, palm, flick.", "Up to 3 targets in front of you; in Lion the flick", "leaves a rupture inside the body (8 after 0.5 s)."],
    ["Скучающее касание в остановленном времени: тыльная сторона,", "ладонь, щелчок. До 3 целей перед вами; во Льве щелчок", "оставляет разрыв внутри тела (8 через 0,5 с)."],
    ["Eine gelangweilte Berührung in erstarrter Zeit: Handrücken,", "Handfläche, Schnippen. Bis zu 3 Ziele vor dir; im Löwen", "hinterlässt das Schnippen einen Riss im Körper (8 nach 0,5 s)."],
    ["Un toque aburrido en el tiempo detenido: revés, palma,", "chasquido. Hasta 3 objetivos delante; en León el chasquido", "deja una ruptura en el cuerpo (8 tras 0,5 s)."],
    ["Durmuş zamanda sıkılmış bir dokunuş: el tersi, avuç, fiske.", "Önündeki en fazla 3 hedef; Aslan'da fiske", "bedende bir yırtık bırakır (0,5 sn sonra 8)."],
    ["静止时间中的一次厌倦触碰：手背、掌、弹指。", "最多命中前方3个目标；狮子状态下弹指", "会在体内留下破裂（0.5秒后8点）。"]))

ability("little_king", T("Little King", "Маленький король", "Kleiner König", "Pequeño Rey", "Küçük Kral", "小王"),
        T("—", "—", "—", "—", "—", "—"), KEY, T(
    ["Place your heart in a living being within 6 blocks", "(up to 4 subjects). The heart lives in one host and moves", "on every 20 s; while it lives the Lion has no time limit.",
     "Press again on a subject to free it; sneak to free all."],
    ["Поместите своё сердце в живое существо в 6 блоках", "(до 4 подданных). Сердце живёт в одном носителе и переходит", "дальше каждые 20 с; пока он жив, у Льва нет предела времени.",
     "Нажмите на подданного ещё раз, чтобы отпустить; присед — всех."],
    ["Lege dein Herz in ein Lebewesen im Umkreis von 6 Blöcken", "(bis zu 4 Untertanen). Das Herz lebt in einem Wirt und", "wandert alle 20 s weiter; solange er lebt, hat der Löwe kein Zeitlimit.",
     "Erneut auf einen Untertan: freilassen; schleichen: alle."],
    ["Coloca tu corazón en un ser vivo a 6 bloques", "(hasta 4 súbditos). El corazón vive en un huésped y pasa", "al siguiente cada 20 s; mientras viva, el León no tiene límite.",
     "Pulsa de nuevo sobre un súbdito para liberarlo; agáchate: todos."],
    ["Kalbini 6 blok içindeki canlı bir varlığa yerleştir", "(en fazla 4 tebaa). Kalp bir taşıyıcıda yaşar ve", "her 20 sn'de bir sonrakine geçer; o yaşadıkça Aslan'ın süre sınırı yoktur.",
     "Serbest bırakmak için tebaaya tekrar bas; eğilirsen hepsi."],
    ["将你的心脏放入6格内的生物体内", "（最多4名臣民）。心脏寄宿于一个宿主，", "每20秒转移一次；宿主存活时狮子没有时间限制。",
     "再次对臣民按下即释放；潜行释放全部。"]))

# spoken lines (subtitles); a calm, arrogant man who never raises his voice
P = "subtitle.viltrumitecore.regulus."
k(P + "ult_accuse", "You have been infringing on my rights all this time. Do you even understand that?",
  "Ты всё это время посягал на мои права. Ты хоть понимаешь это?",
  "Du hast die ganze Zeit meine Rechte verletzt. Verstehst du das überhaupt?",
  "Has estado violando mis derechos todo este tiempo. ¿Siquiera lo entiendes?",
  "Bunca zamandır haklarımı çiğniyorsun. Bunun farkında mısın?",
  "你一直在侵犯我的权利。你明白吗？")
k(P + "ult_sentence", "I am satisfied with what I have. That is why I will not let you take a grain of it.",
  "Я доволен тем, что имею. Поэтому не позволю тебе отнять ни песчинки.",
  "Ich bin zufrieden mit dem, was ich habe. Darum nimmst du mir kein einziges Korn.",
  "Estoy satisfecho con lo que tengo. Por eso no dejaré que me quites ni un grano.",
  "Sahip olduklarımla yetiniyorum. Bu yüzden bir tanesini bile almana izin vermem.",
  "我满足于我所拥有的。所以我连一粒沙也不会让你夺走。")
k(P + "ult_flick", "That is all. A single individual, complete in himself.",
  "Вот и всё. Одна личность, полная в самой себе.",
  "Das ist alles. Ein einzelnes Wesen, in sich vollkommen.",
  "Eso es todo. Un solo individuo, completo en sí mismo.",
  "Hepsi bu. Kendi içinde tamam, tek bir birey.",
  "到此为止。一个自身即完满的个体。")
k(P + "lion", "My time is mine alone.", "Моё время принадлежит только мне.", "Meine Zeit gehört mir allein.", "Mi tiempo es solo mío.",
  "Zamanım yalnızca bana ait.", "我的时间只属于我。")
k(P + "seizure", "...My heart. Not now.", "…Сердце. Не сейчас.", "…Mein Herz. Nicht jetzt.", "…Mi corazón. Ahora no.", "…Kalbim. Şimdi olmaz.", "……我的心脏。不是现在。")
k(P + "king", "Be grateful. You may carry my heart.", "Будь благодарна. Тебе позволено носить моё сердце.",
  "Sei dankbar. Du darfst mein Herz tragen.", "Agradécelo. Puedes llevar mi corazón.", "Minnettar ol. Kalbimi taşımana izin veriyorum.",
  "心怀感激吧。你有资格承载我的心脏。")
k(P + "reply", "I have the right to answer that.", "У меня есть право на ответ.", "Darauf habe ich ein Recht zu antworten.",
  "Tengo derecho a responder a eso.", "Buna cevap verme hakkım var.", "我有权回应这一下。")
k(P + "host_lost", "How inconsiderate. To die while carrying my heart.", "Как невежливо. Умереть, нося моё сердце.",
  "Wie rücksichtslos. Zu sterben, während du mein Herz trägst.", "Qué desconsiderado. Morir llevando mi corazón.",
  "Ne kadar düşüncesiz. Kalbimi taşırken ölmek.", "真是不体贴。带着我的心脏死去。")

# HUD
H = "hud.viltrumitecore.regulus."
k(H + "host", "Heart: %s", "Сердце: %s", "Herz: %s", "Corazón: %s", "Kalp: %s", "心脏：%s")
k(H + "host_far", "Heart: far away", "Сердце: далеко", "Herz: weit weg", "Corazón: lejos", "Kalp: uzakta", "心脏：远处")
k(H + "hostless", "Hostless: %ss", "Без носителя: %s с", "Ohne Wirt: %s s", "Sin huésped: %s s", "Taşıyıcısız: %s sn", "无宿主：%s秒")
k(H + "lion_locked", "Lion rests: %ss", "Лев отдыхает: %s с", "Löwe ruht: %s s", "El León descansa: %s s", "Aslan dinleniyor: %s sn", "狮子休息：%s秒")
k(H + "seized", "Heart seized", "Сердце схвачено", "Herz verkrampft", "Corazón en espasmo", "Kalp kasıldı", "心脏痉挛")
k(H + "reply", "Reply", "Ответ", "Antwort", "Réplica", "Cevap", "答辩")
k(H + "judging", "Judging: %s", "Суд над: %s", "Urteil über: %s", "Juicio a: %s", "Yargılanan: %s", "审判：%s")

# sound subtitles (subtitles.viltrumitecore.regulus_<cue>)
S = "subtitles.viltrumitecore.regulus_"
for cue, en, ru, de, es, tr, zh in [
    ("lion_on", "Time stops around Regulus", "Время вокруг Регулуса останавливается", "Die Zeit um Regulus steht still", "El tiempo se detiene alrededor de Regulus", "Regulus'un çevresinde zaman durur", "雷古勒斯周围的时间停止"),
    ("lion_off", "Regulus's time resumes", "Время Регулуса снова идёт", "Regulus' Zeit läuft wieder", "El tiempo de Regulus se reanuda", "Regulus'un zamanı yeniden akar", "雷古勒斯的时间恢复"),
    ("lion_deny", "The Lion's Heart refuses", "Сердце льва не отзывается", "Das Löwenherz verweigert", "El Corazón de León se niega", "Aslan Yüreği reddeder", "狮子之心拒绝"),
    ("recoil", "A blow stops against Regulus", "Удар замирает о Регулуса", "Ein Schlag erstarrt an Regulus", "Un golpe se detiene contra Regulus", "Bir darbe Regulus'ta durur", "攻击在雷古勒斯身上静止"),
    ("projectile_stop", "A projectile stops dead", "Снаряд замирает", "Ein Geschoss erstarrt", "Un proyectil se detiene en seco", "Bir mermi olduğu yerde durur", "投射物骤然静止"),
    ("heartbeat", "A stopped heart beats", "Бьётся остановленное сердце", "Ein angehaltenes Herz schlägt", "Late un corazón detenido", "Durmuş bir kalp atar", "静止的心脏跳动"),
    ("strain", "Regulus's heart strains", "Сердце Регулуса надрывается", "Regulus' Herz ächzt", "El corazón de Regulus se tensa", "Regulus'un kalbi zorlanır", "雷古勒斯的心脏负荷"),
    ("seizure", "Regulus's heart seizes", "Сердце Регулуса схватывает", "Regulus' Herz verkrampft", "El corazón de Regulus sufre un espasmo", "Regulus'un kalbi kasılır", "雷古勒斯心脏痉挛"),
    ("touch_swish", "Regulus swipes", "Регулус взмахивает рукой", "Regulus wischt", "Regulus barre el aire", "Regulus el savurur", "雷古勒斯挥手"),
    ("touch_hit", "Greed's Touch strikes", "Касание жадности бьёт", "Berührung der Gier trifft", "El Toque de la Codicia golpea", "Açgözlülüğün Dokunuşu çarpar", "贪婪之触命中"),
    ("rupture", "Something ruptures inside", "Что-то рвётся внутри", "Etwas reißt im Inneren", "Algo se rompe por dentro", "İçeride bir şey yırtılır", "体内有什么破裂了"),
    ("sand_scoop", "Regulus scoops up dust", "Регулус зачерпывает пыль", "Regulus schöpft Staub", "Regulus recoge polvo", "Regulus toz avuçlar", "雷古勒斯抓起尘土"),
    ("sand_release", "Stopped sand flies", "Летит застывший песок", "Erstarrter Sand fliegt", "Vuela la arena detenida", "Donmuş kum uçar", "静止之沙飞出"),
    ("grain_hit", "A stopped grain pierces", "Песчинка пробивает", "Ein erstarrtes Korn durchbohrt", "Un grano detenido atraviesa", "Durmuş bir tane deler", "静止沙粒贯穿"),
    ("grain_stop", "A grain stops in the air", "Песчинка замирает в воздухе", "Ein Korn erstarrt in der Luft", "Un grano se detiene en el aire", "Bir tane havada durur", "沙粒停在空中"),
    ("sever_charge", "The air goes still", "Воздух замирает", "Die Luft erstarrt", "El aire se queda quieto", "Hava durgunlaşır", "空气凝滞"),
    ("sever_release", "Severed air flies", "Летит рассечённый воздух", "Geschnittene Luft fliegt", "Vuela el aire cortado", "Yarılmış hava uçar", "断空飞出"),
    ("sever_cut", "Stopped air cuts", "Остановленный воздух режет", "Erstarrte Luft schneidet", "El aire detenido corta", "Durmuş hava keser", "静止空气切割"),
    ("procession_step", "Regulus walks through everything", "Регулус идёт сквозь всё", "Regulus geht durch alles", "Regulus camina a través de todo", "Regulus her şeyin içinden yürür", "雷古勒斯穿行一切"),
    ("procession_ram", "Regulus walks into something", "Регулус врезается во что-то", "Regulus läuft in etwas hinein", "Regulus choca con algo", "Regulus bir şeye çarpar", "雷古勒斯撞上了什么"),
    ("reply_stance", "Regulus waits for an answer", "Регулус ждёт ответа", "Regulus wartet auf eine Antwort", "Regulus espera una respuesta", "Regulus bir cevap bekler", "雷古勒斯等待回应"),
    ("reply_strike", "Regulus replies", "Регулус отвечает", "Regulus antwortet", "Regulus responde", "Regulus cevap verir", "雷古勒斯回击"),
    ("king_bind", "A heart is given", "Сердце отдано", "Ein Herz wird verliehen", "Se entrega un corazón", "Bir kalp verilir", "心脏被赐予"),
    ("king_hop", "The heart moves on", "Сердце переходит", "Das Herz wandert weiter", "El corazón se traslada", "Kalp yer değiştirir", "心脏转移"),
    ("king_release", "A borrowed heart breaks free", "Чужое сердце вырывается", "Ein geliehenes Herz kommt frei", "Un corazón prestado se libera", "Ödünç bir kalp kurtulur", "寄宿的心脏脱离"),
    ("refuse", "Regulus can't act", "Регулус не может действовать", "Regulus kann nicht handeln", "Regulus no puede actuar", "Regulus hareket edemez", "雷古勒斯无法行动"),
    ("ult_accuse", "Regulus passes judgement", "Регулус выносит приговор", "Regulus fällt ein Urteil", "Regulus dicta sentencia", "Regulus hüküm verir", "雷古勒斯宣判"),
    ("ult_scoop", "The air itself is gathered", "Сам воздух собирается", "Die Luft selbst wird gesammelt", "El propio aire se reúne", "Havanın kendisi toplanır", "空气本身被聚拢"),
    ("ult_wave", "Stopped grains strike", "Бьют застывшие песчинки", "Erstarrte Körner schlagen ein", "Golpean granos detenidos", "Durmuş taneler vurur", "静止沙粒袭击"),
    ("ult_flick", "One flick of a finger", "Один щелчок пальцем", "Ein Fingerschnippen", "Un chasquido de dedo", "Tek bir fiske", "一记弹指"),
    ("ult_resolve", "The judgement is over", "Приговор исполнен", "Das Urteil ist vollstreckt", "El juicio ha terminado", "Yargı sona erdi", "审判结束"),
    ("step", "Regulus's footsteps", "Шаги Регулуса", "Regulus' Schritte", "Pasos de Regulus", "Regulus'un ayak sesleri", "雷古勒斯的脚步声"),
]:
    k(S + cue, en, ru, de, es, tr, zh)


def main():
    os.makedirs(OUT, exist_ok=True)
    for locale, lang in LOCALES.items():
        data = {key: v[lang] for key, v in L.items()}
        with open(os.path.join(OUT, locale + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("regulus overlays: %d keys x %d locales" % (len(L), len(LOCALES)))


if __name__ == "__main__":
    main()
