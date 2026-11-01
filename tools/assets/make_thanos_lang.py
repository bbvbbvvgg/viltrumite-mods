#!/usr/bin/env python3
"""Thanos event strings for the 12 shipped locales -> src/main/lang-overlays/thanos/<locale>.json.

The base locale files are past what one push carries, so the event's keys live in this overlay
(merged by processResources). One row per key: (en, ru, de, es, tr, zh); every Spanish locale uses
the neutral Spanish column, as the other overlays do. Run: python3 tools/assets/make_thanos_lang.py
"""
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from thanos_lang_moves import MOVES  # noqa: E402

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
    "event.viltrumitecore.thanos.phase.black_order": (
        "Phase II: Thanos calls the Black Order down from the Sanctuary II!",
        "Фаза II: Танос призывает Чёрный Орден с борта Санктуария II!",
        "Phase II: Thanos ruft den Schwarzen Orden von der Sanctuary II herab!",
        "Fase II: ¡Thanos llama a la Orden Negra desde el Sanctuary II!",
        "Aşama II: Thanos, Kara Düzen'i Sanctuary II'den indiriyor!",
        "第二阶段：灭霸从圣殿二号召下了黑曜五将！"),
    "event.viltrumitecore.thanos.phase.invasion": (
        "Phase III: the sky opens. The Chitauri invade Atheria!",
        "Фаза III: небо разверзлось. Читаури вторгаются в Атерию!",
        "Phase III: Der Himmel öffnet sich. Die Chitauri fallen in Atheria ein!",
        "Fase III: el cielo se abre. ¡Los chitauri invaden Atheria!",
        "Aşama III: gökyüzü açıldı. Chitauri Atheria'yı istila ediyor!",
        "第三阶段：天空洞开，齐塔瑞人入侵阿瑟利亚！"),
    "event.viltrumitecore.thanos.phase.gauntlet": (
        "Phase IV: Thanos unleashes the full power of the Gauntlet!",
        "Фаза IV: Танос обрушивает всю мощь Перчатки!",
        "Phase IV: Thanos entfesselt die volle Macht des Handschuhs!",
        "Fase IV: ¡Thanos desata todo el poder del Guantelete!",
        "Aşama IV: Thanos Eldiven'in tüm gücünü serbest bırakıyor!",
        "第四阶段：灭霸释放了手套的全部力量！"),
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
    "commands.viltrumitecore.thanos.phase.arrival": ("I, arrival", "I, прибытие", "I, Ankunft", "I, llegada", "I, varış", "一，降临"),
    "commands.viltrumitecore.thanos.phase.black_order": ("II, the Black Order", "II, Чёрный Орден", "II, der Schwarze Orden", "II, la Orden Negra",
                                                         "II, Kara Düzen", "二，黑曜五将"),
    "commands.viltrumitecore.thanos.phase.invasion": ("III, invasion", "III, вторжение", "III, Invasion", "III, invasión", "III, istila", "三，入侵"),
    "commands.viltrumitecore.thanos.phase.gauntlet": ("IV, the full Gauntlet", "IV, полная Перчатка", "IV, der volle Handschuh",
                                                      "IV, el Guantelete completo", "IV, tam Eldiven", "四，完整手套"),
    "commands.viltrumitecore.thanos.phase_set": (
        "War phase set: %s.", "Фаза войны: %s.", "Kriegsphase gesetzt: %s.", "Fase de la guerra: %s.", "Savaş aşaması: %s.", "战争阶段已设为：%s。"),
    "commands.viltrumitecore.thanos.phase_refused": (
        "No phase change now: the war is not being fought (or the snap has begun), or the phase is unknown.",
        "Сменить фазу сейчас нельзя: война не идёт (или начался щелчок), либо фаза неизвестна.",
        "Jetzt kein Phasenwechsel: Der Krieg läuft nicht (oder das Schnippen hat begonnen), oder die Phase ist unbekannt.",
        "Ahora no se puede cambiar de fase: la guerra no está en curso (o empezó el chasquido), o la fase no existe.",
        "Şu an aşama değiştirilemez: savaş sürmüyor (ya da şıklatma başladı) veya aşama bilinmiyor.",
        "现在无法切换阶段：战争未在进行（或响指已开始），或阶段不存在。"),
    "commands.viltrumitecore.thanos.order_called": (
        "The Black Order is beamed down.", "Чёрный Орден спускается с корабля.", "Der Schwarze Orden wird herabgestrahlt.",
        "La Orden Negra desciende de la nave.", "Kara Düzen gemiden indiriliyor.", "黑曜五将从战舰降下。"),
    "commands.viltrumitecore.thanos.wave_called": (
        "A Chitauri wave is called (%s invaders alive).", "Вызвана волна Читаури (живых захватчиков: %s).",
        "Eine Chitauri-Welle wird gerufen (%s Invasoren am Leben).", "Se llama a una oleada chitauri (%s invasores vivos).",
        "Bir Chitauri dalgası çağrıldı (hayatta %s istilacı).", "已召唤一波齐塔瑞人（存活入侵者：%s）。"),
    "commands.viltrumitecore.thanos.ship_strike": (
        "Orbital strike ordered on your position. Move!", "Орбитальный удар по вашей позиции. Уходите!",
        "Orbitalschlag auf deine Position befohlen. Weg da!", "Ataque orbital ordenado sobre tu posición. ¡Muévete!",
        "Konumuna yörünge saldırısı emredildi. Kaç!", "已对你的位置下令轨道打击。快跑！"),
    "commands.viltrumitecore.thanos.ship_busy": (
        "The Sanctuary II cannot fire now (not over the city yet, or every gun is busy).",
        "Санктуарий II сейчас не может стрелять (ещё не над городом или все орудия заняты).",
        "Die Sanctuary II kann jetzt nicht feuern (noch nicht über der Stadt oder alle Geschütze belegt).",
        "El Sanctuary II no puede disparar ahora (aún no está sobre la ciudad o todos sus cañones están ocupados).",
        "Sanctuary II şu an ateş edemez (henüz şehrin üstünde değil ya da tüm topları meşgul).",
        "圣殿二号现在无法开火（尚未抵达城市上空，或所有炮位都在使用中）。"),
    "commands.viltrumitecore.thanos.war": (
        "War: phase %1$s; ship %2$s, %3$s strikes fired; army %4$s alive, %5$s waves, %6$s portals; Ebony Maw %7$s, Cull Obsidian %8$s; "
        "city alarm %9$s, %10$s blackouts; %11$s holds; %12$s blocks destroyed",
        "Война: фаза %1$s; корабль %2$s, ударов: %3$s; армия: живых %4$s, волн %5$s, порталов %6$s; Эбони Мо %7$s, Кулл Обсидиан %8$s; "
        "тревога в городе: %9$s, отключений света: %10$s; удержаний: %11$s; разрушено блоков: %12$s",
        "Krieg: Phase %1$s; Schiff %2$s, %3$s Schläge gefeuert; Armee %4$s am Leben, %5$s Wellen, %6$s Portale; Ebony Maw %7$s, Cull Obsidian %8$s; "
        "Stadtalarm %9$s, %10$s Stromausfälle; %11$s Griffe; %12$s Blöcke zerstört",
        "Guerra: fase %1$s; nave %2$s, %3$s ataques disparados; ejército: %4$s vivos, %5$s oleadas, %6$s portales; Ebony Maw %7$s, Cull Obsidian %8$s; "
        "alarma de la ciudad %9$s, %10$s apagones; %11$s agarres; %12$s bloques destruidos",
        "Savaş: aşama %1$s; gemi %2$s, %3$s saldırı yapıldı; ordu: hayatta %4$s, %5$s dalga, %6$s geçit; Ebony Maw %7$s, Cull Obsidian %8$s; "
        "şehir alarmı %9$s, %10$s elektrik kesintisi; %11$s tutuş; %12$s blok yıkıldı",
        "战争：阶段 %1$s；战舰 %2$s，已发射 %3$s 次打击；军队存活 %4$s，%5$s 波，%6$s 个传送门；乌木喉 %7$s，黑矮星 %8$s；"
        "城市警报 %9$s，停电 %10$s 次；控制 %11$s 个；摧毁方块 %12$s 个"),
    "commands.viltrumitecore.thanos.ship.none": ("not in the sky yet", "ещё не в небе", "noch nicht am Himmel", "aún no está en el cielo",
                                                 "henüz gökte değil", "尚未出现"),
    "commands.viltrumitecore.thanos.ship.arriving": ("arriving", "приближается", "im Anflug", "llegando", "yaklaşıyor", "抵达中"),
    "commands.viltrumitecore.thanos.ship.hover": ("over the city", "над городом", "über der Stadt", "sobre la ciudad", "şehrin üstünde", "悬停于城市上空"),
    "commands.viltrumitecore.thanos.ship.leaving": ("leaving", "улетает", "zieht ab", "marchándose", "ayrılıyor", "离去中"),
    "commands.viltrumitecore.thanos.ship.gone": ("gone", "улетел", "fort", "se ha ido", "gitti", "已离去"),
    "commands.viltrumitecore.thanos.general.down": ("down", "повержен", "besiegt", "abatido", "düştü", "已倒下"),
    "commands.viltrumitecore.thanos.general.waiting": ("not called yet", "ещё не призван", "noch nicht gerufen", "aún no convocado",
                                                       "henüz çağrılmadı", "尚未召唤"),
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

# ability warnings: name, counterplay (ThanosHud.moveKey / hintKey), in thanos_lang_moves.py
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
    "combo_time_volley": ("Time freezes a volley", "Время замораживает залп", "Zeit friert eine Salve ein", "El tiempo congela una descarga",
                          "Zaman bir yaylımı donduruyor", "时间冻结齐射"),
    "combo_moon_fall": ("Moon shards fall", "Падают осколки луны", "Mondsplitter stürzen herab", "Caen fragmentos lunares",
                        "Ay parçaları düşüyor", "月球碎片坠落"),
    "combo_infinity": ("All six stones unleashed", "Высвобождены все шесть камней", "Alle sechs Steine entfesselt", "Se desatan las seis gemas",
                       "Altı taşın hepsi serbest", "六颗宝石尽数释放"),
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
