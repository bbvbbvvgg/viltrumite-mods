#!/usr/bin/env python3
"""Thanos event strings for the 12 shipped locales -> src/main/lang-overlays/thanos/<locale>.json.

The base locale files are past what one push carries, so the event's keys live in this overlay
(merged by processResources). One row per key: (en, ru, de, es, tr, zh); every Spanish locale uses
the neutral Spanish column, as the other overlays do. Run: python3 tools/assets/make_thanos_lang.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "viltrumitecore/src/main/lang-overlays/thanos"
LOCALES = {"en_us": 0, "ru_ru": 1, "de_de": 2, "es_es": 3, "es_mx": 3, "es_ar": 3, "es_cl": 3, "es_ec": 3, "es_uy": 3, "es_ve": 3,
           "tr_tr": 4, "zh_cn": 5}

T = {
    "entity.viltrumitecore.thanos": ("Thanos", "Танос", "Thanos", "Thanos", "Thanos", "灭霸"),
    # chat
    "event.viltrumitecore.thanos.arrived": (
        "Thanos has arrived with all six Infinity Stones. Defeat him before time runs out — or he will snap his fingers!",
        "Танос явился со всеми шестью Камнями Бесконечности. Одолейте его, пока не вышло время, — иначе он щёлкнет пальцами!",
        "Thanos ist mit allen sechs Infinity-Steinen erschienen. Besiegt ihn, bevor die Zeit abläuft – sonst schnippt er mit den Fingern!",
        "Thanos ha llegado con las seis Gemas del Infinito. ¡Hay que derrotarlo antes de que se acabe el tiempo, o chasqueará los dedos!",
        "Thanos altı Sonsuzluk Taşı'nın hepsiyle geldi. Süre dolmadan onu yenin — yoksa parmaklarını şıklatacak!",
        "灭霸带着全部六颗无限宝石降临了。在时间耗尽前击败他——否则他将打响指！"),
    "event.viltrumitecore.thanos.phase.escalation": (
        "Thanos stops holding back: the stones begin to combine.",
        "Танос больше не сдерживается: камни начинают действовать вместе.",
        "Thanos hält sich nicht mehr zurück: Die Steine beginnen zusammenzuwirken.",
        "Thanos ya no se contiene: las gemas empiezan a combinarse.",
        "Thanos artık kendini tutmuyor: taşlar birlikte çalışmaya başladı.",
        "灭霸不再保留：宝石开始联合发动。"),
    "event.viltrumitecore.thanos.phase.onslaught": (
        "Onslaught: Thanos unleashes the full might of the Gauntlet!",
        "Натиск: Танос обрушивает всю мощь Перчатки!",
        "Ansturm: Thanos entfesselt die volle Macht des Handschuhs!",
        "Embestida: ¡Thanos desata todo el poder del Guantelete!",
        "Saldırı: Thanos Eldiven'in tüm gücünü serbest bırakıyor!",
        "猛攻：灭霸释放了手套的全部力量！"),
    "event.viltrumitecore.thanos.phase.final": (
        "Final minute! All six stones blaze — the snap is coming!",
        "Последняя минута! Все шесть камней пылают — щелчок близко!",
        "Letzte Minute! Alle sechs Steine lodern – das Schnippen naht!",
        "¡Último minuto! Las seis gemas arden: el chasquido está cerca.",
        "Son dakika! Altı taşın hepsi alev alev — şıklatma yaklaşıyor!",
        "最后一分钟！六颗宝石同时燃起——响指即将来临！"),
    "event.viltrumitecore.thanos.victory": (
        "Thanos falls! The Infinity Gauntlet goes dark.",
        "Танос повержен! Перчатка Бесконечности гаснет.",
        "Thanos fällt! Der Infinity-Handschuh erlischt.",
        "¡Thanos cae! El Guantelete del Infinito se apaga.",
        "Thanos düştü! Sonsuzluk Eldiveni sönüyor.",
        "灭霸倒下了！无限手套的光芒熄灭了。"),
    "event.viltrumitecore.thanos.time_up": (
        "Time is up. Thanos raises the Gauntlet…",
        "Время вышло. Танос поднимает Перчатку…",
        "Die Zeit ist um. Thanos hebt den Handschuh …",
        "Se acabó el tiempo. Thanos alza el Guantelete…",
        "Süre doldu. Thanos Eldiven'i kaldırıyor…",
        "时间到了。灭霸举起了手套……"),
    "event.viltrumitecore.thanos.gone_victory": (
        "Thanos is defeated. The universe is safe.",
        "Танос побеждён. Вселенная в безопасности.",
        "Thanos ist besiegt. Das Universum ist sicher.",
        "Thanos ha sido derrotado. El universo está a salvo.",
        "Thanos yenildi. Evren güvende.",
        "灭霸被击败了。宇宙安全了。"),
    "event.viltrumitecore.thanos.restored": (
        "Od Lagna restored the universe. Thanos has been erased from existence.",
        "Од Лагуна восстановила вселенную. Танос стёрт из бытия.",
        "Od Lagna hat das Universum wiederhergestellt. Thanos wurde aus dem Dasein getilgt.",
        "Od Lagna restauró el universo. Thanos fue borrado de la existencia.",
        "Od Lagna evreni yeniden kurdu. Thanos varoluştan silindi.",
        "奥德·拉古纳修复了宇宙。灭霸已从存在中被抹去。"),
    "event.viltrumitecore.thanos.abandoned": (
        "No hero remained to face Thanos. He left the battlefield.",
        "Против Таноса не осталось ни одного героя. Он покинул поле боя.",
        "Kein Held stellte sich Thanos mehr. Er hat das Schlachtfeld verlassen.",
        "Ningún héroe quedó para enfrentarse a Thanos. Abandonó el campo de batalla.",
        "Thanos'a karşı duracak kahraman kalmadı. Savaş alanını terk etti.",
        "没有英雄留下来对抗灭霸。他离开了战场。"),
    "event.viltrumitecore.thanos.stopped": (
        "The Thanos event was stopped.", "Событие «Танос» остановлено.", "Das Thanos-Ereignis wurde beendet.",
        "El evento de Thanos se detuvo.", "Thanos etkinliği durduruldu.", "灭霸事件已停止。"),
    "event.viltrumitecore.thanos.lost": (
        "Thanos vanished from the world. The event has ended.",
        "Танос исчез из мира. Событие завершено.",
        "Thanos ist aus der Welt verschwunden. Das Ereignis ist beendet.",
        "Thanos desapareció del mundo. El evento terminó.",
        "Thanos dünyadan kayboldu. Etkinlik sona erdi.",
        "灭霸从世界中消失了。事件已结束。"),
    # commands
    "commands.viltrumitecore.thanos.started": ("Thanos is coming…", "Танос приближается…", "Thanos naht …", "Thanos se acerca…", "Thanos geliyor…", "灭霸正在到来……"),
    "commands.viltrumitecore.thanos.creative_hint": (
        "Thanos ignores players in creative and spectator mode. Switch to survival to fight him.",
        "Танос не замечает игроков в творческом режиме и в режиме наблюдателя. Переключитесь на выживание, чтобы сразиться.",
        "Thanos ignoriert Spieler im Kreativ- und Zuschauermodus. Wechsle in den Überlebensmodus, um gegen ihn zu kämpfen.",
        "Thanos ignora a los jugadores en modo creativo y espectador. Cambia al modo supervivencia para enfrentarlo.",
        "Thanos yaratıcı ve izleyici modundaki oyuncuları görmezden gelir. Onunla savaşmak için hayatta kalma moduna geç.",
        "灭霸会无视创造模式和旁观模式的玩家。切换到生存模式才能与他战斗。"),
    "commands.viltrumitecore.thanos.already": (
        "The Thanos event is already running.", "Событие «Танос» уже идёт.", "Das Thanos-Ereignis läuft bereits.",
        "El evento de Thanos ya está en curso.", "Thanos etkinliği zaten sürüyor.", "灭霸事件已在进行中。"),
    "commands.viltrumitecore.thanos.paused": (
        "Time is stopped right now. Start the event once the world is moving again.",
        "Сейчас время остановлено. Запустите событие, когда мир снова придёт в движение.",
        "Die Zeit steht gerade still. Starte das Ereignis, sobald sich die Welt wieder bewegt.",
        "El tiempo está detenido ahora mismo. Inicia el evento cuando el mundo vuelva a moverse.",
        "Şu anda zaman durmuş durumda. Dünya yeniden hareket edince etkinliği başlat.",
        "现在时间已停止。请在世界恢复流动后再开始事件。"),
    "commands.viltrumitecore.thanos.no_space": (
        "There is no room for Thanos to land here.", "Здесь Таносу негде появиться.", "Hier ist kein Platz, an dem Thanos landen kann.",
        "Aquí no hay espacio para que aparezca Thanos.", "Burada Thanos'un inebileceği yer yok.", "这里没有足够的空间让灭霸降临。"),
    "commands.viltrumitecore.thanos.not_running": (
        "The Thanos event is not running.", "Событие «Танос» не запущено.", "Das Thanos-Ereignis läuft nicht.",
        "El evento de Thanos no está en curso.", "Thanos etkinliği çalışmıyor.", "灭霸事件未在运行。"),
    "commands.viltrumitecore.thanos.stopped": (
        "The Thanos event was stopped.", "Событие «Танос» остановлено.", "Das Thanos-Ereignis wurde beendet.",
        "El evento de Thanos se detuvo.", "Thanos etkinliği durduruldu.", "灭霸事件已停止。"),
    "commands.viltrumitecore.thanos.none": (
        "No Thanos event is running.", "Событие «Танос» сейчас не идёт.", "Es läuft kein Thanos-Ereignis.",
        "No hay ningún evento de Thanos en curso.", "Şu anda Thanos etkinliği yok.", "当前没有灭霸事件。"),
    "commands.viltrumitecore.thanos.not_fighting": (
        "Thanos is not fighting right now.", "Танос сейчас не сражается.", "Thanos kämpft gerade nicht.",
        "Thanos no está luchando ahora.", "Thanos şu anda savaşmıyor.", "灭霸现在没有在战斗。"),
    "commands.viltrumitecore.thanos.skipped": (
        "Timer set: %s left until the snap.", "Таймер переставлен: до щелчка %s.", "Timer gesetzt: noch %s bis zum Schnippen.",
        "Temporizador ajustado: quedan %s para el chasquido.", "Zamanlayıcı ayarlandı: şıklatmaya %s kaldı.", "计时器已调整：距离响指还剩 %s。"),
    "commands.viltrumitecore.thanos.queued": (
        "Thanos uses %s next.", "Следующим приёмом Таноса будет %s.", "Als Nächstes setzt Thanos %s ein.",
        "Lo próximo que usará Thanos es %s.", "Thanos'un sıradaki hamlesi: %s.", "灭霸的下一招将是 %s。"),
    "commands.viltrumitecore.thanos.status": (
        "Thanos: %1$s, %2$s until the snap, health %3$s, heroes %4$s, phase: %5$s, moves: %6$s",
        "Танос: %1$s, до щелчка %2$s, здоровье %3$s, героев: %4$s, фаза: %5$s, приёмов: %6$s",
        "Thanos: %1$s, noch %2$s bis zum Schnippen, Leben %3$s, Helden: %4$s, Phase: %5$s, Angriffe: %6$s",
        "Thanos: %1$s, %2$s para el chasquido, salud %3$s, héroes: %4$s, fase: %5$s, ataques: %6$s",
        "Thanos: %1$s, şıklatmaya %2$s, can %3$s, kahraman: %4$s, evre: %5$s, hamle: %6$s",
        "灭霸：%1$s，距离响指 %2$s，生命值 %3$s，英雄：%4$s，阶段：%5$s，招式：%6$s"),
    "commands.viltrumitecore.thanos.stage.intro": ("arriving", "появляется", "erscheint", "llegando", "geliyor", "降临中"),
    "commands.viltrumitecore.thanos.stage.fight": ("fighting", "сражается", "kämpft", "luchando", "savaşıyor", "战斗中"),
    "commands.viltrumitecore.thanos.stage.victory": ("defeated", "повержен", "besiegt", "derrotado", "yenildi", "已被击败"),
    "commands.viltrumitecore.thanos.stage.snap_wait": ("raising the Gauntlet", "поднимает Перчатку", "hebt den Handschuh", "alzando el Guantelete",
                                                       "Eldiven'i kaldırıyor", "举起手套"),
    "commands.viltrumitecore.thanos.stage.snap": ("the snap", "щелчок", "das Schnippen", "el chasquido", "şıklatma", "响指"),
    "commands.viltrumitecore.thanos.phase.opening": ("opening", "начало", "Auftakt", "apertura", "açılış", "开局"),
    "commands.viltrumitecore.thanos.phase.escalation": ("escalation", "нарастание", "Eskalation", "escalada", "tırmanış", "升级"),
    "commands.viltrumitecore.thanos.phase.onslaught": ("onslaught", "натиск", "Ansturm", "embestida", "saldırı", "猛攻"),
    "commands.viltrumitecore.thanos.phase.final": ("final minute", "последняя минута", "letzte Minute", "último minuto", "son dakika", "最后一分钟"),
    # HUD
    "hud.viltrumitecore.thanos.until_snap": ("UNTIL THE SNAP", "ДО ЩЕЛЧКА", "BIS ZUM SCHNIPPEN", "HASTA EL CHASQUIDO", "ŞIKLATMAYA", "距离响指"),
    "hud.viltrumitecore.thanos.final": ("FINAL MINUTE", "ПОСЛЕДНЯЯ МИНУТА", "LETZTE MINUTE", "ÚLTIMO MINUTO", "SON DAKİKA", "最后一分钟"),
    # Od Lagna's speech in the snap scene (six lines, five seconds each)
    "scene.viltrumitecore.thanos.od_lagna": ("Od Lagna", "Од Лагуна", "Od Lagna", "Od Lagna", "Od Lagna", "奥德·拉古纳"),
    "scene.viltrumitecore.thanos.line.1": (
        "Enough. You clenched your fist — and the stars went out like candles.",
        "Довольно. Ты сжал кулак — и звёзды погасли, как свечи.",
        "Genug. Du hast die Faust geballt – und die Sterne erloschen wie Kerzen.",
        "Basta. Cerraste el puño, y las estrellas se apagaron como velas.",
        "Yeter. Yumruğunu sıktın — ve yıldızlar mumlar gibi söndü.",
        "够了。你握紧拳头——群星便如烛火般熄灭。"),
    "scene.viltrumitecore.thanos.line.2": (
        "You called it balance. But it was only one deciding for all that lives.",
        "Ты назвал это равновесием. Но это лишь один решил за всё живое.",
        "Du nanntest es Gleichgewicht. Doch es war nur einer, der für alles Leben entschied.",
        "Lo llamaste equilibrio. Pero no fue más que uno decidiendo por todo lo que vive.",
        "Buna denge dedin. Oysa bu, tüm canlılar adına tek birinin verdiği bir karardı.",
        "你称之为平衡。可那不过是一个人替所有生命做了决定。"),
    "scene.viltrumitecore.thanos.line.3": (
        "Listen, Titan. Every world you erased was a voice — and I am the place where every voice returns.",
        "Слушай, Титан. Каждый стёртый тобой мир был голосом — а я то место, куда возвращаются все голоса.",
        "Hör zu, Titan. Jede Welt, die du ausgelöscht hast, war eine Stimme – und ich bin der Ort, an den alle Stimmen zurückkehren.",
        "Escucha, Titán. Cada mundo que borraste era una voz, y yo soy el lugar al que vuelven todas las voces.",
        "Dinle, Titan. Sildiğin her dünya bir sesti — ve ben, bütün seslerin döndüğü yerim.",
        "听着，泰坦。你抹去的每一个世界都是一个声音——而我，是所有声音归来之处。"),
    "scene.viltrumitecore.thanos.line.4": (
        "Your stones are shards of me. They obey the one who made them, not the one who stole them.",
        "Твои камни — осколки меня. Они подчиняются тому, кто их создал, а не тому, кто их украл.",
        "Deine Steine sind Splitter von mir. Sie gehorchen dem, der sie erschuf, nicht dem, der sie stahl.",
        "Tus gemas son fragmentos de mí. Obedecen a quien las creó, no a quien las robó.",
        "Taşların benim parçalarım. Onlar, onları çalana değil, yaratana itaat eder.",
        "你的宝石是我的碎片。它们服从创造它们的存在，而不是窃取它们的人。"),
    "scene.viltrumitecore.thanos.line.5": (
        "I will not undo what was fought for. The scars remain — they belong to those who stood against you.",
        "Я не отменю того, за что сражались. Шрамы останутся — они принадлежат тем, кто встал против тебя.",
        "Ich mache nicht ungeschehen, wofür gekämpft wurde. Die Narben bleiben – sie gehören denen, die sich dir entgegenstellten.",
        "No desharé aquello por lo que se luchó. Las cicatrices quedarán: pertenecen a quienes se alzaron contra ti.",
        "Uğruna savaşılanı geri almayacağım. Yaralar kalacak — onlar sana karşı duranlara ait.",
        "我不会抹去人们为之奋战的一切。伤痕会留下——它们属于那些站出来对抗你的人。"),
    "scene.viltrumitecore.thanos.line.6": (
        "Now — return. Let existence remember itself… and forget only you.",
        "А теперь — вернись. Пусть бытие вспомнит себя… и пусть забудет лишь тебя.",
        "Und nun – kehre zurück. Möge das Sein sich an sich selbst erinnern … und nur dich vergessen.",
        "Y ahora, regresa. Que la existencia se recuerde a sí misma… y que solo te olvide a ti.",
        "Ve şimdi — geri dön. Varoluş kendini hatırlasın… ve yalnızca seni unutsun.",
        "现在——回来吧。让存在记起自己……只让它忘记你。"),
}

# ability warnings: name, counterplay (ThanosHud.moveKey / hintKey)
MOVES = {
    "cleave": (("Backhand", "Step out of his reach"), ("Удар наотмашь", "Отойдите из зоны досягаемости"),
               ("Rückhandschlag", "Geh aus seiner Reichweite"), ("Revés", "Sal de su alcance"),
               ("Ters El Darbesi", "Menzilinden çekil"), ("反手横扫", "退出他的攻击范围")),
    "hammer": (("Hammer Fists", "Jump the shock ring"), ("Молот кулаков", "Перепрыгните ударное кольцо"),
               ("Hammerfäuste", "Spring über den Schockring"), ("Puños de martillo", "Salta el anillo de choque"),
               ("Çekiç Yumruklar", "Şok halkasının üzerinden atla"), ("双拳锤击", "跳过冲击环")),
    "power_punch": (("Power Punch", "Dodge sideways out of the cone"), ("Удар Силы", "Уйдите вбок из конуса удара"),
                    ("Kraftschlag", "Weiche seitlich aus dem Kegel aus"), ("Puñetazo de Poder", "Esquiva hacia un lado del cono"),
                    ("Güç Yumruğu", "Koniden yana kaç"), ("力量重拳", "向侧面闪出锥形范围")),
    "power_wave": (("Power Shockwave", "Jump or fly over the ring"), ("Волна Силы", "Перепрыгните или перелетите кольцо"),
                   ("Kraftwelle", "Spring oder flieg über den Ring"), ("Onda de Poder", "Salta o vuela sobre el anillo"),
                   ("Güç Dalgası", "Halkanın üzerinden atla ya da uç"), ("力量冲击波", "跳过或飞过冲击环")),
    "space_step": (("Space Step", "He comes out behind you"), ("Шаг сквозь пространство", "Он выйдет у вас за спиной"),
                   ("Raumschritt", "Er taucht hinter dir auf"), ("Paso espacial", "Aparecerá a tu espalda"),
                   ("Uzay Adımı", "Arkandan çıkacak"), ("空间跃迁", "他会出现在你身后")),
    "space_pull": (("Gravity Grab", "You are pulled to his fist — break line of sight"),
                   ("Гравитационный захват", "Вас тянет к его кулаку — уйдите из поля зрения"),
                   ("Schwerkraftgriff", "Du wirst zu seiner Faust gezogen – brich die Sichtlinie"),
                   ("Agarre gravitatorio", "Te arrastra hacia su puño: rompe la línea de visión"),
                   ("Yerçekimi Kavrayışı", "Yumruğuna çekiliyorsun — görüş hattını kes"), ("引力攫取", "你会被拉向他的拳头——切断视线")),
    "space_ward": (("Space Ward", "Projectiles are turned back — strike in melee"),
                   ("Пространственный щит", "Снаряды отражаются — бейте вблизи"),
                   ("Raumschild", "Geschosse werden zurückgeworfen – greif im Nahkampf an"),
                   ("Escudo espacial", "Devuelve los proyectiles: ataca cuerpo a cuerpo"),
                   ("Uzay Kalkanı", "Mermiler geri döner — yakından vur"), ("空间护盾", "投射物会被反弹——用近战攻击")),
    "reality_zone": (("Reality Fracture", "Leave the red zones under your feet"), ("Разлом реальности", "Уйдите из красных зон под ногами"),
                     ("Realitätsriss", "Verlass die roten Zonen unter deinen Füßen"), ("Fractura de realidad", "Sal de las zonas rojas bajo tus pies"),
                     ("Gerçeklik Yarığı", "Ayağının altındaki kırmızı bölgelerden çık"), ("现实裂隙", "离开脚下的红色区域")),
    "reality_mirage": (("Mirage", "Find the real Thanos — the copies burst"), ("Мираж", "Найдите настоящего Таноса — копии взрываются"),
                       ("Trugbild", "Finde den echten Thanos – die Kopien zerplatzen"), ("Espejismo", "Encuentra al Thanos real: las copias estallan"),
                       ("Serap", "Gerçek Thanos'u bul — kopyalar patlar"), ("幻象", "找出真正的灭霸——分身会爆裂")),
    "time_dome": (("Time Dome", "Get out of the green ring before it closes"), ("Купол времени", "Выйдите из зелёного круга, пока он не замкнулся"),
                  ("Zeitkuppel", "Verlass den grünen Ring, bevor er sich schließt"), ("Cúpula del tiempo", "Sal del anillo verde antes de que se cierre"),
                  ("Zaman Kubbesi", "Yeşil halka kapanmadan dışarı çık"), ("时间穹顶", "在绿色圆环闭合前离开")),
    "time_rewind": (("Rewind", "He returns to where he stood"), ("Перемотка", "Он вернётся туда, где стоял"),
                    ("Zurückspulen", "Er kehrt dorthin zurück, wo er stand"), ("Retroceso", "Vuelve al lugar donde estaba"),
                    ("Geri Sarma", "Durduğu yere geri döner"), ("时间回溯", "他会回到之前站的位置")),
    "mind_gaze": (("Mind Gaze", "Look away from him!"), ("Взгляд Разума", "Отвернитесь от него!"),
                  ("Blick des Geistes", "Schau von ihm weg!"), ("Mirada de la Mente", "¡Aparta la vista de él!"),
                  ("Zihin Bakışı", "Gözlerini ondan çevir!"), ("心灵凝视", "把视线从他身上移开！")),
    "mind_pulse": (("Mind Pulse", "Jump the yellow rings"), ("Импульс Разума", "Перепрыгивайте жёлтые кольца"),
                   ("Geistespuls", "Spring über die gelben Ringe"), ("Pulso de la Mente", "Salta los anillos amarillos"),
                   ("Zihin Darbesi", "Sarı halkaların üzerinden atla"), ("心灵脉冲", "跳过黄色圆环")),
    "soul_tether": (("Soul Tether", "Run out of reach or stagger him to break it"),
                    ("Узы Души", "Убегите подальше или оглушите его, чтобы разорвать связь"),
                    ("Seelenfessel", "Lauf außer Reichweite oder betäube ihn, um sie zu lösen"),
                    ("Lazo del Alma", "Aléjate o atúrdelo para romper el lazo"),
                    ("Ruh Bağı", "Bağı koparmak için menzilden kaç ya da onu sersemlet"), ("灵魂锁链", "跑出范围或击晕他来扯断锁链")),
    "soul_nova": (("Soul Nova", "Get far from him — the blast throws you out"), ("Взрыв Души", "Отойдите подальше — взрыв отбрасывает"),
                  ("Seelennova", "Halte Abstand – die Explosion schleudert dich weg"), ("Nova del Alma", "Aléjate de él: la explosión te lanza lejos"),
                  ("Ruh Patlaması", "Ondan uzaklaş — patlama seni fırlatır"), ("灵魂新星", "远离他——爆炸会把你震飞")),
    "combo_portal_punch": (("Portal Punch", "His fist comes out of the portal beside you — move!"),
                           ("Удар сквозь портал", "Кулак вылетит из портала рядом с вами — двигайтесь!"),
                           ("Portalschlag", "Seine Faust kommt aus dem Portal neben dir – beweg dich!"),
                           ("Puñetazo de portal", "Su puño sale del portal a tu lado: ¡muévete!"),
                           ("Portal Yumruğu", "Yumruğu yanındaki portaldan çıkacak — hareket et!"),
                           ("传送门重拳", "他的拳头会从你身旁的传送门里打出——快移动！")),
    "combo_time_crush": (("Time Crush", "Leave the dome before it is crushed"), ("Сокрушение времени", "Покиньте купол, пока его не раздавило"),
                         ("Zeitzermalmung", "Verlass die Kuppel, bevor sie zermalmt wird"),
                         ("Aplastamiento temporal", "Sal de la cúpula antes de que la aplaste"),
                         ("Zaman Ezmesi", "Kubbe ezilmeden dışarı çık"), ("时间碾压", "在穹顶被碾碎前离开")),
    "combo_soul_mirage": (("Soul Mirage", "Pop the images before their tethers bind you"),
                          ("Мираж душ", "Лопайте призраков, пока их узы не связали вас"),
                          ("Seelentrugbild", "Zerstöre die Bilder, bevor ihre Fesseln dich binden"),
                          ("Espejismo de almas", "Revienta las imágenes antes de que sus lazos te atrapen"),
                          ("Ruh Serabı", "Bağları seni sarmadan görüntüleri patlat"), ("灵魂幻象", "在锁链缠住你之前击破幻象")),
    "combo_mind_step": (("Mind Step", "Look away, then turn — he appears behind you"),
                        ("Шаг Разума", "Отвернитесь, затем развернитесь — он появится сзади"),
                        ("Geistesschritt", "Schau weg, dann dreh dich um – er erscheint hinter dir"),
                        ("Paso de la Mente", "Aparta la vista y luego gírate: aparecerá detrás de ti"),
                        ("Zihin Adımı", "Bakışını çevir, sonra dön — arkanda belirecek"), ("心灵跃迁", "先移开视线，再转身——他会出现在你身后")),
    "combo_trinity": (("Trinity", "Jump the star wave, hide from the shards"),
                      ("Триединство", "Перепрыгните звёздную волну и укройтесь от осколков"),
                      ("Dreieinigkeit", "Spring über die Sternwelle und geh vor den Splittern in Deckung"),
                      ("Trinidad", "Salta la onda en estrella y cúbrete de las esquirlas"),
                      ("Üçleme", "Yıldız dalgasının üzerinden atla, kıymıklardan saklan"), ("三位一体", "跳过星形冲击波，躲避碎片")),
    "combo_eternity": (("Eternity", "Look away and run far from the dome"), ("Вечность", "Отвернитесь и бегите подальше от купола"),
                       ("Ewigkeit", "Schau weg und lauf weit weg von der Kuppel"), ("Eternidad", "Aparta la vista y huye lejos de la cúpula"),
                       ("Sonsuzluk", "Gözlerini çevir ve kubbeden uzağa koş"), ("永恒", "移开视线，远离穹顶")),
}

SUBTITLES = {
    "arrival": ("Thanos arrives", "Танос прибывает", "Thanos erscheint", "Llega Thanos", "Thanos geliyor", "灭霸降临"),
    "step": ("Thanos stomps", "Тяжёлые шаги Таноса", "Thanos stampft", "Pisadas de Thanos", "Thanos ağır adımlarla yürüyor", "灭霸沉重的脚步"),
    "swing": ("Gauntlet swings", "Взмах Перчатки", "Handschuh schwingt", "El Guantelete se balancea", "Eldiven savruluyor", "手套挥动"),
    "slam": ("Ground shatters", "Земля раскалывается", "Boden zerbirst", "El suelo se quiebra", "Zemin parçalanıyor", "地面碎裂"),
    "hit": ("Gauntlet strikes", "Удар Перчатки", "Handschuh trifft", "Golpe del Guantelete", "Eldiven vuruyor", "手套击中"),
    "hurt": ("Thanos is hurt", "Танос ранен", "Thanos wird verletzt", "Thanos herido", "Thanos yaralanıyor", "灭霸受伤"),
    "stun": ("Thanos staggers", "Танос пошатнулся", "Thanos taumelt", "Thanos se tambalea", "Thanos sendeliyor", "灭霸踉跄"),
    "power_charge": ("Power Stone charges", "Камень Силы заряжается", "Kraftstein lädt auf", "La Gema del Poder se carga", "Güç Taşı şarj oluyor", "力量宝石充能"),
    "power_impact": ("Power Stone detonates", "Взрыв Камня Силы", "Kraftstein explodiert", "Estalla la Gema del Poder", "Güç Taşı patlıyor", "力量宝石爆发"),
    "power_wave": ("Shockwave rolls", "Катится ударная волна", "Schockwelle rollt", "Avanza la onda de choque", "Şok dalgası yayılıyor", "冲击波扩散"),
    "space_open": ("Portal tears open", "Открывается портал", "Portal reißt auf", "Se abre un portal", "Portal açılıyor", "传送门撕开"),
    "space_step": ("Thanos teleports", "Танос телепортируется", "Thanos teleportiert sich", "Thanos se teletransporta", "Thanos ışınlanıyor", "灭霸传送"),
    "space_pull": ("Space bends", "Пространство искривляется", "Raum krümmt sich", "El espacio se curva", "Uzay bükülüyor", "空间扭曲"),
    "space_ward": ("Space ward hums", "Гудит пространственный щит", "Raumschild summt", "Zumba el escudo espacial", "Uzay kalkanı uğulduyor", "空间护盾嗡鸣"),
    "reality_warp": ("Reality warps", "Реальность искажается", "Realität verzerrt sich", "La realidad se deforma", "Gerçeklik bükülüyor", "现实扭曲"),
    "reality_mirage": ("Mirages appear", "Появляются миражи", "Trugbilder erscheinen", "Aparecen espejismos", "Seraplar beliriyor", "幻象出现"),
    "time_dome": ("Time slows", "Время замедляется", "Zeit verlangsamt sich", "El tiempo se ralentiza", "Zaman yavaşlıyor", "时间变慢"),
    "time_rewind": ("Time rewinds", "Время отматывается назад", "Zeit spult zurück", "El tiempo retrocede", "Zaman geri sarılıyor", "时间倒流"),
    "mind_gaze": ("Mind Stone stares", "Взгляд Камня Разума", "Geiststein starrt", "Mirada de la Gema de la Mente", "Zihin Taşı bakıyor", "心灵宝石凝视"),
    "mind_pulse": ("Mind Stone pulses", "Импульс Камня Разума", "Geiststein pulsiert", "Pulso de la Gema de la Mente", "Zihin Taşı titreşiyor", "心灵宝石脉冲"),
    "soul_tether": ("Soul tether binds", "Узы Души стягиваются", "Seelenfessel bindet", "El lazo del alma aprieta", "Ruh bağı sarıyor", "灵魂锁链缠绕"),
    "soul_nova": ("Soul nova bursts", "Вспышка Камня Души", "Seelennova birst", "Estalla la nova del alma", "Ruh patlaması", "灵魂新星爆发"),
    "stones_flare": ("All six stones flare", "Вспыхивают все шесть камней", "Alle sechs Steine lodern", "Brillan las seis gemas", "Altı taş birden parlıyor", "六颗宝石同时闪耀"),
    "phase": ("The Gauntlet surges", "Перчатка набирает силу", "Handschuh erstarkt", "El Guantelete se intensifica", "Eldiven güçleniyor", "手套力量涌动"),
    "combo_portal_punch": ("Fist through a portal", "Кулак сквозь портал", "Faust durch ein Portal", "Puño a través de un portal", "Portaldan yumruk", "拳头穿过传送门"),
    "combo_time_crush": ("Time crushes down", "Время сокрушает", "Zeit zermalmt", "El tiempo aplasta", "Zaman eziyor", "时间碾压"),
    "combo_soul_mirage": ("Ghosts sing", "Поют призраки", "Geister singen", "Cantan los fantasmas", "Hayaletler şarkı söylüyor", "幽魂吟唱"),
    "combo_mind_step": ("Thanos blinks behind you", "Танос возникает за спиной", "Thanos erscheint hinter dir", "Thanos aparece a tu espalda",
                        "Thanos arkanda beliriyor", "灭霸出现在你身后"),
    "combo_trinity": ("Three stones detonate", "Взрываются три камня", "Drei Steine explodieren", "Estallan tres gemas", "Üç taş patlıyor", "三颗宝石爆炸"),
    "combo_eternity": ("Eternity erupts", "Извергается Вечность", "Ewigkeit bricht aus", "Erupciona la Eternidad", "Sonsuzluk patlıyor", "永恒爆发"),
    "world_stop": ("The world stops", "Мир останавливается", "Die Welt steht still", "El mundo se detiene", "Dünya duruyor", "世界静止"),
    "gauntlet_raise": ("Gauntlet rises", "Перчатка поднимается", "Handschuh hebt sich", "Se alza el Guantelete", "Eldiven yükseliyor", "手套举起"),
    "stone_light": ("A stone ignites", "Загорается камень", "Ein Stein leuchtet auf", "Se enciende una gema", "Bir taş tutuşuyor", "一颗宝石亮起"),
    "fingers": ("Metal fingers fold", "Сгибаются металлические пальцы", "Metallfinger krümmen sich", "Se doblan dedos de metal", "Metal parmaklar bükülüyor", "金属手指弯曲"),
    "snap_click": ("THE SNAP", "ЩЕЛЧОК", "DAS SCHNIPPEN", "EL CHASQUIDO", "ŞIKLATMA", "响指"),
    "dissolve": ("The world turns to dust", "Мир рассыпается в прах", "Die Welt zerfällt zu Staub", "El mundo se vuelve polvo", "Dünya toza dönüşüyor", "世界化为尘埃"),
    "void": ("Silence of the void", "Тишина пустоты", "Stille der Leere", "Silencio del vacío", "Boşluğun sessizliği", "虚空的寂静"),
    "od_lagna_arrive": ("Od Lagna appears", "Появляется Од Лагуна", "Od Lagna erscheint", "Aparece Od Lagna", "Od Lagna beliriyor", "奥德·拉古纳现身"),
    "od_lagna_line": ("Od Lagna speaks", "Говорит Од Лагуна", "Od Lagna spricht", "Habla Od Lagna", "Od Lagna konuşuyor", "奥德·拉古纳开口"),
    "restore": ("The world returns", "Мир возвращается", "Die Welt kehrt zurück", "El mundo regresa", "Dünya geri dönüyor", "世界回归"),
    "victory": ("Thanos falls", "Танос падает", "Thanos fällt", "Thanos cae", "Thanos düşüyor", "灭霸倒下"),
    "countdown": ("Clock ticks", "Тикают часы", "Uhr tickt", "Tic-tac del reloj", "Saat tıkırdıyor", "时钟滴答"),
}


def table():
    rows = dict(T)
    for move, cols in MOVES.items():
        rows["hud.viltrumitecore.thanos.move." + move] = tuple(c[0] for c in cols)
        rows["hud.viltrumitecore.thanos.hint." + move] = tuple(c[1] for c in cols)
    for cue, cols in SUBTITLES.items():
        rows["subtitles.viltrumitecore.thanos." + cue] = cols
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
