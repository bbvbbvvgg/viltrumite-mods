"""Venom localization overlay: writes viltrumitecore/src/main/lang-overlays/venom/<locale>.json for all 12 locales
(ru_ru is the source; the es_* locales share one Spanish text). Keys: hero / race button / speakers, the spoken lines and
the host's eight inner voices (subtitles only, never voiced), the six abilities, the HUD (panel states, the takeover
bars and hints), the takeover refusals, the boss NPC and its command, and the 25 sound subtitles. The codex article is
in tools/codex/codex_text_heroes.py.
Run: python3 tools/assets/venom_lang.py"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore', 'src', 'main', 'lang-overlays', 'venom')
SOUNDS = ['transform', 'revert', 'roar', 'squirm', 'reform', 'shriek', 'claw', 'claw_hit', 'tendril_shot', 'tendril_stick', 'zip',
          'lash', 'slam', 'grab', 'throw', 'shell', 'web_tear', 'web_eat', 'clash', 'engulf', 'heartbeat', 'breath', 'whisper',
          'release', 'refuse']
ABILITIES = ['symbiosis', 'tendril', 'tentacle_lash', 'symbiote_shell', 'feeding_frenzy', 'host_takeover']
LINES = ['enter', 'transform', 'pull', 'rage', 'takeover', 'spider', 'recoil']
WHISPERS = 8
HUD = ['controls', 'controls_spider', 'held', 'resist', 'riding', 'state.frenzy', 'state.host', 'state.recoil', 'state.riding',
       'state.shell']
REFUSALS = ['disabled', 'no_pvp', 'no_rage', 'cooldown', 'hunger', 'no_target', 'immune', 'not_weakened']

RU = {
    'hero': 'Веном', 'race': 'Стать Веномом', 'speaker': 'Веном', 'inner': 'Голос внутри',
    'lines': {
        'enter': 'Мы — Веном.', 'transform': 'Мы голодны.', 'pull': 'Иди сюда!', 'rage': 'Голод!', 'takeover': 'Теперь ты — это мы.',
        'spider': 'Паучок…', 'recoil': 'Огонь! Больно!',
    },
    'whispers': [
        'Не сопротивляйся. Вместе нам тепло.', 'Твоё сердце бьётся так быстро…', 'Мы смотрим твоими глазами.', 'Ты слабый. Мы — нет.',
        'Отдай нам руки. Отдай нам ноги.', 'Тише… тише…', 'Мы знаем, чего ты боишься.', 'Борись. Нам это нравится.',
    ],
    'abilities': {
        'symbiosis': ('Симбиоз',
                      'Симбиот покрывает тело за 2,3 с: руки и ноги, торс и голова, рост и мышцы, пасть, глаза и эмблема — и стойка. '
                      'В облике: 40 здоровья, броня, +18 % скорости, прыжок ~11 блоков, бег по стенам и фасадам, регенерация. ЛКМ — '
                      'когти (комбо из трёх), ПКМ — схватить щупальцами (затем ЛКМ — бросок, ПКМ — удар о землю). Повторное нажатие — '
                      'симбиот отступает. Перезарядка 2 с.'),
        'tendril': ('Щупальце',
                    'Щупальце на 28 блоков. В стену — рывок к ней; во врага — подтягивает и держит (к боссу, великану или с Shift — '
                    'рывок к нему); в паутину Паука — обвивает нить и взбирается по ней к опоре. Перезарядка 1,3 с.'),
        'tentacle_lash': ('Хлёст щупалец',
                          'Комбо из трёх ударов щупальцами: круговой хлёст (4,6 блока, 8 урона), пронзание вперёд (7,5 блока, 12 '
                          'урона) и удар сверху (радиус 5, 13 урона). Сочетается с когтями и рвёт паутину. Перезарядка 3,5 с после '
                          'третьего удара.'),
        'symbiote_shell': ('Панцирь симбиота',
                           'Симбиот твердеет в шипастый панцирь: входящий урон −60 %, движение медленнее, голод тратится. До 6 с; '
                           'повторное нажатие снимает панцирь. Перезарядка 5 с.'),
        'feeding_frenzy': ('Бешеный голод',
                           'Нужно 500+ голода (его дают удары, съеденная паутина и победы). Рёв отбрасывает мобов в 8 блоках, '
                           'тело растёт: +30 % урона, +20 % скорости, вдвое быстрее регенерация, прыжок выше; голод тает. До 15 с. '
                           'Перезарядка 20 с.'),
        'host_takeover': ('Захват носителя',
                          'Только в Бешеном голоде, при PvP на сервере и если захват разрешён настройкой; тратит 250 голода. Симбиот '
                          'обволакивает ослабленного игрока (меньше 60 % здоровья или в захвате) и до 10 с ведёт его тело: ходьба, '
                          'удары, у Человека-паука — его приёмы. Жертва видит вены и вспышки и может вырваться. Повторное нажатие — '
                          'отпустить. Перезарядка 60 с, у жертвы иммунитет 60 с.'),
    },
    'hud': {
        'controls': 'WASD — вести носителя · ЛКМ — его удар · «Захват носителя» — отпустить',
        'controls_spider': 'WASD — вести · ЛКМ — паутина · 1–4 — приёмы Паука · «Захват носителя» — отпустить',
        'held': 'Симбиот держит тебя ещё %s с',
        'resist': 'Жми ЛКМ, ПКМ или пробел — сопротивляйся!',
        'riding': 'Носитель: %s',
        'state.frenzy': 'Бешеный голод', 'state.host': 'Человеческий облик', 'state.recoil': 'Симбиот отпрянул!',
        'state.riding': 'Захват носителя', 'state.shell': 'Панцирь',
    },
    'refusals': {
        'disabled': 'Захват носителя отключён на этом сервере.',
        'no_pvp': 'Захват носителя возможен только при включённом PvP.',
        'no_rage': 'Сначала нужен Бешеный голод.',
        'cooldown': 'Симбиот ещё не готов к новому захвату.',
        'hunger': 'Мало голода: для захвата нужно 250.',
        'no_target': 'Рядом нет подходящего носителя.',
        'immune': 'Этот носитель только что освободился — у него иммунитет.',
        'not_weakened': 'Носитель слишком силён: ослабь его (меньше 60 % здоровья) или схвати щупальцами.',
    },
    'boss': 'Веном в Бешеном голоде',
    'boss_cmd': 'Веном вырвался на свободу!',
    'sounds': {
        'transform': 'Симбиот покрывает тело', 'revert': 'Симбиот отступает', 'roar': 'Рёв Венома', 'squirm': 'Шевелится живая масса',
        'reform': 'Симбиот срастается', 'shriek': 'Симбиот визжит', 'claw': 'Взмах когтей', 'claw_hit': 'Когти рвут',
        'tendril_shot': 'Выстрел щупальцем', 'tendril_stick': 'Щупальце прилипает', 'zip': 'Рывок на щупальце', 'lash': 'Хлёст щупалец',
        'slam': 'Удар о землю', 'grab': 'Щупальца хватают', 'throw': 'Бросок', 'shell': 'Панцирь твердеет', 'web_tear': 'Паутина рвётся',
        'web_eat': 'Симбиот поглощает паутину', 'clash': 'Сшибка с Пауком', 'engulf': 'Симбиот обволакивает носителя',
        'heartbeat': 'Сердцебиение', 'breath': 'Тяжёлое дыхание', 'whisper': 'Шёпот в голове', 'release': 'Симбиот отпускает носителя',
        'refuse': 'Симбиот не слушается',
    },
}

EN = {
    'hero': 'Venom', 'race': 'Become Venom', 'speaker': 'Venom', 'inner': 'Voice inside',
    'lines': {
        'enter': 'We are Venom.', 'transform': 'We are hungry.', 'pull': 'Come here!', 'rage': 'Hungry!', 'takeover': 'Now you are us.',
        'spider': 'Little spider…', 'recoil': 'Fire! It hurts!',
    },
    'whispers': [
        "Don't fight it. We are warm together.", 'Your heart beats so fast…', 'We see through your eyes.', 'You are weak. We are not.',
        'Give us your arms. Give us your legs.', 'Hush… hush…', 'We know what you fear.', 'Struggle. We like it.',
    ],
    'abilities': {
        'symbiosis': ('Symbiosis',
                      'The symbiote covers the body in 2.3 s: arms and legs, torso and head, height and muscle, the mouth, eyes and '
                      'emblem, then the stance. In form: 40 health, armor, +18% speed, ~11-block jump, wall and facade running, '
                      'regeneration. LMB: claws (three-hit combo); RMB: grab with tendrils (then LMB throws, RMB slams). Press again '
                      'and the symbiote recedes. Cooldown 2 s.'),
        'tendril': ('Tendril',
                    'A 28-block tendril. Into a wall: zip to it; into an enemy: yank and hold it (to a boss, a giant or with Shift: '
                    'zip to it); into a Spider\'s web: wrap the line and climb it to the anchor. Cooldown 1.3 s.'),
        'tentacle_lash': ('Tentacle Lash',
                          'A three-hit tentacle combo: a circular sweep (4.6 blocks, 8 damage), a forward impale (7.5 blocks, 12 '
                          'damage) and an overhead slam (radius 5, 13 damage). Mixes with the claws and cuts webs. Cooldown 3.5 s '
                          'after the third hit.'),
        'symbiote_shell': ('Symbiote Shell',
                           'The symbiote hardens into a spiked shell: -60% damage taken, slower movement, drains hunger. Up to 6 s; '
                           'press again to drop it. Cooldown 5 s.'),
        'feeding_frenzy': ('Feeding Frenzy',
                           'Needs 500+ hunger (from hits, eaten webs and kills). A roar knocks back mobs within 8 blocks and the body '
                           'grows: +30% damage, +20% speed, twice the regeneration, a higher jump; hunger melts away. Up to 15 s. '
                           'Cooldown 20 s.'),
        'host_takeover': ('Host Takeover',
                          'Only in the Frenzy, with server PvP on and the takeover allowed in the config; costs 250 hunger. The '
                          'symbiote engulfs a weakened player (below 60% health or held) and drives their body for up to 10 s: '
                          'walking, strikes and, on Spider-Man, his moves. The victim sees veins and flashes and can break free. '
                          'Press again to let go. Cooldown 60 s; the victim is immune for 60 s.'),
    },
    'hud': {
        'controls': 'WASD: steer the host · LMB: their strike · Host Takeover: let go',
        'controls_spider': 'WASD: steer · LMB: web · 1-4: Spider moves · Host Takeover: let go',
        'held': 'The symbiote holds you for %s more s',
        'resist': 'Press LMB, RMB or Space to resist!',
        'riding': 'Host: %s',
        'state.frenzy': 'Feeding Frenzy', 'state.host': 'Human form', 'state.recoil': 'The symbiote recoils!',
        'state.riding': 'Host Takeover', 'state.shell': 'Shell',
    },
    'refusals': {
        'disabled': 'Host takeover is disabled on this server.',
        'no_pvp': 'Host takeover needs PvP to be enabled.',
        'no_rage': 'The Feeding Frenzy comes first.',
        'cooldown': 'The symbiote is not ready for another takeover yet.',
        'hunger': 'Not hungry enough: a takeover needs 250.',
        'no_target': 'No suitable host nearby.',
        'immune': 'That host was just released and is immune.',
        'not_weakened': 'The host is too strong: weaken them (below 60% health) or hold them with the tendrils.',
    },
    'boss': 'Venom in a Feeding Frenzy',
    'boss_cmd': 'Venom has broken loose!',
    'sounds': {
        'transform': 'Symbiote covers the body', 'revert': 'Symbiote recedes', 'roar': 'Venom roars', 'squirm': 'Living mass squirms',
        'reform': 'Symbiote knits back together', 'shriek': 'Symbiote shrieks', 'claw': 'Claws swing', 'claw_hit': 'Claws tear',
        'tendril_shot': 'Tendril shoots', 'tendril_stick': 'Tendril sticks', 'zip': 'Tendril zip', 'lash': 'Tentacles lash',
        'slam': 'Ground slam', 'grab': 'Tendrils grab', 'throw': 'Throw', 'shell': 'Shell hardens', 'web_tear': 'Web tears',
        'web_eat': 'Symbiote devours the web', 'clash': 'Clash with the Spider', 'engulf': 'Symbiote engulfs the host',
        'heartbeat': 'Heartbeat', 'breath': 'Heavy breathing', 'whisper': 'Whisper inside the head', 'release': 'Symbiote lets go of the host',
        'refuse': 'Symbiote will not obey',
    },
}

DE = {
    'hero': 'Venom', 'race': 'Venom werden', 'speaker': 'Venom', 'inner': 'Stimme im Inneren',
    'lines': {
        'enter': 'Wir sind Venom.', 'transform': 'Wir sind hungrig.', 'pull': 'Komm her!', 'rage': 'Hunger!', 'takeover': 'Jetzt bist du wir.',
        'spider': 'Kleine Spinne…', 'recoil': 'Feuer! Es tut weh!',
    },
    'whispers': [
        'Wehr dich nicht. Zusammen ist es warm.', 'Dein Herz schlägt so schnell…', 'Wir sehen durch deine Augen.', 'Du bist schwach. Wir nicht.',
        'Gib uns deine Arme. Gib uns deine Beine.', 'Still… still…', 'Wir wissen, wovor du Angst hast.', 'Kämpf ruhig. Das gefällt uns.',
    ],
    'abilities': {
        'symbiosis': ('Symbiose',
                      'Der Symbiont bedeckt den Körper in 2,3 s: Arme und Beine, Rumpf und Kopf, Größe und Muskeln, Maul, Augen und '
                      'Emblem, dann die Haltung. In Gestalt: 40 Leben, Rüstung, +18 % Tempo, Sprung ~11 Blöcke, Lauf an Wänden und '
                      'Fassaden, Regeneration. LMT: Klauen (Dreierkombo), RMT: mit Tentakeln packen (dann LMT werfen, RMT '
                      'schmettern). Erneut drücken: der Symbiont zieht sich zurück. Abklingzeit 2 s.'),
        'tendril': ('Tentakel',
                    'Ein Tentakel über 28 Blöcke. In eine Wand: Sprint dorthin; in einen Gegner: heranziehen und festhalten (zu '
                    'einem Boss, einem Riesen oder mit Shift: Sprint zu ihm); in das Netz einer Spinne: den Faden umschlingen und '
                    'zum Anker hinaufklettern. Abklingzeit 1,3 s.'),
        'tentacle_lash': ('Tentakelpeitsche',
                          'Eine Dreierkombo mit Tentakeln: Rundumhieb (4,6 Blöcke, 8 Schaden), Durchbohren nach vorn (7,5 Blöcke, '
                          '12 Schaden) und Schmettern von oben (Radius 5, 13 Schaden). Lässt sich mit den Klauen mischen und zerreißt '
                          'Netze. Abklingzeit 3,5 s nach dem dritten Treffer.'),
        'symbiote_shell': ('Symbiontenpanzer',
                           'Der Symbiont härtet zu einem stacheligen Panzer: −60 % erlittener Schaden, langsamer, verbraucht Hunger. '
                           'Bis zu 6 s; erneut drücken beendet ihn. Abklingzeit 5 s.'),
        'feeding_frenzy': ('Fressrausch',
                           'Braucht 500+ Hunger (durch Treffer, gefressene Netze und Siege). Ein Brüllen stößt Mobs im Umkreis von '
                           '8 Blöcken zurück, der Körper wächst: +30 % Schaden, +20 % Tempo, doppelte Regeneration, höherer Sprung; '
                           'der Hunger schwindet. Bis zu 15 s. Abklingzeit 20 s.'),
        'host_takeover': ('Wirtsübernahme',
                          'Nur im Fressrausch, mit PvP auf dem Server und wenn die Konfiguration es erlaubt; kostet 250 Hunger. Der '
                          'Symbiont umhüllt einen geschwächten Spieler (unter 60 % Leben oder gepackt) und lenkt seinen Körper bis zu '
                          '10 s: Laufen, Schläge und bei Spider-Man dessen Fähigkeiten. Das Opfer sieht Adern und Blitze und kann sich '
                          'losreißen. Erneut drücken lässt los. Abklingzeit 60 s; das Opfer ist 60 s immun.'),
    },
    'hud': {
        'controls': 'WASD: Wirt lenken · LMT: sein Schlag · Wirtsübernahme: loslassen',
        'controls_spider': 'WASD: lenken · LMT: Netz · 1–4: Spinnen-Fähigkeiten · Wirtsübernahme: loslassen',
        'held': 'Der Symbiont hält dich noch %s s',
        'resist': 'Drück LMT, RMT oder Leertaste – wehr dich!',
        'riding': 'Wirt: %s',
        'state.frenzy': 'Fressrausch', 'state.host': 'Menschengestalt', 'state.recoil': 'Der Symbiont weicht zurück!',
        'state.riding': 'Wirtsübernahme', 'state.shell': 'Panzer',
    },
    'refusals': {
        'disabled': 'Die Wirtsübernahme ist auf diesem Server deaktiviert.',
        'no_pvp': 'Die Wirtsübernahme braucht aktiviertes PvP.',
        'no_rage': 'Zuerst braucht es den Fressrausch.',
        'cooldown': 'Der Symbiont ist noch nicht bereit für eine neue Übernahme.',
        'hunger': 'Zu wenig Hunger: eine Übernahme braucht 250.',
        'no_target': 'Kein geeigneter Wirt in der Nähe.',
        'immune': 'Dieser Wirt wurde gerade erst freigelassen und ist immun.',
        'not_weakened': 'Der Wirt ist zu stark: schwäche ihn (unter 60 % Leben) oder pack ihn mit den Tentakeln.',
    },
    'boss': 'Venom im Fressrausch',
    'boss_cmd': 'Venom hat sich losgerissen!',
    'sounds': {
        'transform': 'Symbiont bedeckt den Körper', 'revert': 'Symbiont zieht sich zurück', 'roar': 'Venom brüllt', 'squirm': 'Lebende Masse windet sich',
        'reform': 'Symbiont wächst wieder zusammen', 'shriek': 'Symbiont kreischt', 'claw': 'Klauenhieb', 'claw_hit': 'Klauen reißen',
        'tendril_shot': 'Tentakel schießt', 'tendril_stick': 'Tentakel haftet', 'zip': 'Tentakelsprint', 'lash': 'Tentakel peitschen',
        'slam': 'Bodenschmetterer', 'grab': 'Tentakel packen', 'throw': 'Wurf', 'shell': 'Panzer härtet', 'web_tear': 'Netz reißt',
        'web_eat': 'Symbiont verschlingt das Netz', 'clash': 'Zusammenprall mit der Spinne', 'engulf': 'Symbiont umhüllt den Wirt',
        'heartbeat': 'Herzschlag', 'breath': 'Schweres Atmen', 'whisper': 'Flüstern im Kopf', 'release': 'Symbiont lässt den Wirt los',
        'refuse': 'Symbiont gehorcht nicht',
    },
}

TR = {
    'hero': 'Venom', 'race': 'Venom ol', 'speaker': 'Venom', 'inner': 'İçerideki ses',
    'lines': {
        'enter': 'Biz Venom\'uz.', 'transform': 'Açız.', 'pull': 'Gel buraya!', 'rage': 'Açlık!', 'takeover': 'Artık sen de bizsin.',
        'spider': 'Küçük örümcek…', 'recoil': 'Ateş! Acıyor!',
    },
    'whispers': [
        'Direnme. Birlikte sıcağız.', 'Kalbin ne kadar hızlı atıyor…', 'Senin gözlerinle görüyoruz.', 'Sen zayıfsın. Biz değiliz.',
        'Kollarını bize ver. Bacaklarını bize ver.', 'Şşş… şşş…', 'Neden korktuğunu biliyoruz.', 'Çırpın. Hoşumuza gidiyor.',
    ],
    'abilities': {
        'symbiosis': ('Ortakyaşam',
                      'Simbiyot bedeni 2,3 sn\'de kaplar: kollar ve bacaklar, gövde ve kafa, boy ve kaslar, ağız, gözler ve amblem, '
                      'ardından duruş. Bu formda: 40 can, zırh, +%18 hız, ~11 blok zıplama, duvarlarda ve cephelerde koşma, yenilenme. '
                      'Sol tık: pençeler (üçlü kombo), sağ tık: dokunaçlarla yakala (sonra sol tık fırlatır, sağ tık yere çarpar). '
                      'Tekrar basınca simbiyot çekilir. Bekleme 2 sn.'),
        'tendril': ('Dokunaç',
                    '28 blokluk bir dokunaç. Duvara: oraya atıl; düşmana: çekip tut (bossa, deve ya da Shift ile: ona atıl); '
                    'Örümcek ağına: ipe sarılıp çapaya kadar tırman. Bekleme 1,3 sn.'),
        'tentacle_lash': ('Dokunaç Kırbacı',
                          'Üç vuruşluk dokunaç kombosu: dairesel savuruş (4,6 blok, 8 hasar), ileri saplama (7,5 blok, 12 hasar) ve '
                          'yukarıdan çarpma (5 yarıçap, 13 hasar). Pençelerle birleşir ve ağları keser. Üçüncü vuruştan sonra '
                          'bekleme 3,5 sn.'),
        'symbiote_shell': ('Simbiyot Kabuğu',
                           'Simbiyot dikenli bir kabuğa dönüşür: alınan hasar -%60, hareket yavaşlar, açlık harcanır. En fazla 6 sn; '
                           'tekrar basınca kabuk kalkar. Bekleme 5 sn.'),
        'feeding_frenzy': ('Açlık Çılgınlığı',
                           '500+ açlık gerekir (vuruşlar, yenen ağlar ve galibiyetlerden). Kükreme 8 blok içindeki yaratıkları geri '
                           'iter, beden büyür: +%30 hasar, +%20 hız, iki kat yenilenme, daha yüksek zıplama; açlık erir. En fazla '
                           '15 sn. Bekleme 20 sn.'),
        'host_takeover': ('Konak Ele Geçirme',
                          'Yalnızca Açlık Çılgınlığında, sunucuda PvP açıkken ve ayarlarda izin varsa; 250 açlık harcar. Simbiyot '
                          'zayıflamış bir oyuncuyu (%60 canın altında ya da yakalanmış) sarar ve bedenini 10 sn\'ye kadar yönetir: '
                          'yürüme, vuruşlar, Örümcek Adam\'da onun hareketleri. Kurban damarlar ve parlamalar görür ve kurtulabilir. '
                          'Tekrar basınca bırakır. Bekleme 60 sn; kurban 60 sn bağışıktır.'),
    },
    'hud': {
        'controls': 'WASD: konağı yönet · Sol tık: onun vuruşu · Konak Ele Geçirme: bırak',
        'controls_spider': 'WASD: yönet · Sol tık: ağ · 1-4: Örümcek hareketleri · Konak Ele Geçirme: bırak',
        'held': 'Simbiyot seni %s sn daha tutuyor',
        'resist': 'Sol tık, sağ tık ya da Boşluk – diren!',
        'riding': 'Konak: %s',
        'state.frenzy': 'Açlık Çılgınlığı', 'state.host': 'İnsan formu', 'state.recoil': 'Simbiyot geri çekildi!',
        'state.riding': 'Konak Ele Geçirme', 'state.shell': 'Kabuk',
    },
    'refusals': {
        'disabled': 'Konak ele geçirme bu sunucuda kapalı.',
        'no_pvp': 'Konak ele geçirme için PvP açık olmalı.',
        'no_rage': 'Önce Açlık Çılgınlığı gerekir.',
        'cooldown': 'Simbiyot yeni bir ele geçirmeye henüz hazır değil.',
        'hunger': 'Açlık yetersiz: ele geçirme 250 ister.',
        'no_target': 'Yakında uygun bir konak yok.',
        'immune': 'Bu konak az önce serbest kaldı, bağışık.',
        'not_weakened': 'Konak çok güçlü: onu zayıflat (%60 canın altı) ya da dokunaçlarla yakala.',
    },
    'boss': 'Açlık Çılgınlığındaki Venom',
    'boss_cmd': 'Venom serbest kaldı!',
    'sounds': {
        'transform': 'Simbiyot bedeni kaplıyor', 'revert': 'Simbiyot çekiliyor', 'roar': 'Venom kükrüyor', 'squirm': 'Canlı kütle kıvranıyor',
        'reform': 'Simbiyot yeniden birleşiyor', 'shriek': 'Simbiyot çığlık atıyor', 'claw': 'Pençe savruluyor', 'claw_hit': 'Pençeler parçalıyor',
        'tendril_shot': 'Dokunaç fırlıyor', 'tendril_stick': 'Dokunaç yapışıyor', 'zip': 'Dokunaçla atılma', 'lash': 'Dokunaçlar kırbaçlıyor',
        'slam': 'Yere çarpma', 'grab': 'Dokunaçlar yakalıyor', 'throw': 'Fırlatma', 'shell': 'Kabuk sertleşiyor', 'web_tear': 'Ağ yırtılıyor',
        'web_eat': 'Simbiyot ağı yutuyor', 'clash': 'Örümcekle çarpışma', 'engulf': 'Simbiyot konağı sarıyor',
        'heartbeat': 'Kalp atışı', 'breath': 'Ağır nefes', 'whisper': 'Kafanın içinde fısıltı', 'release': 'Simbiyot konağı bırakıyor',
        'refuse': 'Simbiyot söz dinlemiyor',
    },
}

ES = {
    'hero': 'Venom', 'race': 'Convertirse en Venom', 'speaker': 'Venom', 'inner': 'Voz interior',
    'lines': {
        'enter': 'Somos Venom.', 'transform': 'Tenemos hambre.', 'pull': '¡Ven aquí!', 'rage': '¡Hambre!', 'takeover': 'Ahora tú eres nosotros.',
        'spider': 'Arañita…', 'recoil': '¡Fuego! ¡Duele!',
    },
    'whispers': [
        'No te resistas. Juntos estamos calientes.', 'Tu corazón late tan rápido…', 'Vemos con tus ojos.', 'Tú eres débil. Nosotros no.',
        'Danos tus brazos. Danos tus piernas.', 'Calla… calla…', 'Sabemos lo que temes.', 'Lucha. Nos gusta.',
    ],
    'abilities': {
        'symbiosis': ('Simbiosis',
                      'El simbionte cubre el cuerpo en 2,3 s: brazos y piernas, torso y cabeza, altura y músculos, la boca, los ojos '
                      'y el emblema, y luego la postura. En esta forma: 40 de vida, armadura, +18 % de velocidad, salto de ~11 '
                      'bloques, carrera por paredes y fachadas, regeneración. Clic izquierdo: garras (combo de tres); clic derecho: '
                      'agarrar con tentáculos (después el izquierdo lanza y el derecho estrella). Pulsa otra vez y el simbionte se '
                      'retira. Enfriamiento 2 s.'),
        'tendril': ('Tentáculo',
                    'Un tentáculo de 28 bloques. A una pared: impulso hacia ella; a un enemigo: lo atrae y lo sujeta (a un jefe, un '
                    'gigante o con Mayús: impulso hacia él); a la telaraña de una Araña: envuelve el hilo y trepa hasta el anclaje. '
                    'Enfriamiento 1,3 s.'),
        'tentacle_lash': ('Latigazo de tentáculos',
                          'Combo de tres golpes con tentáculos: barrido circular (4,6 bloques, 8 de daño), empalamiento al frente '
                          '(7,5 bloques, 12 de daño) y golpe desde arriba (radio 5, 13 de daño). Se combina con las garras y corta '
                          'telarañas. Enfriamiento 3,5 s tras el tercer golpe.'),
        'symbiote_shell': ('Caparazón simbionte',
                           'El simbionte se endurece en un caparazón con púas: −60 % de daño recibido, movimiento más lento, gasta '
                           'hambre. Hasta 6 s; pulsa otra vez para quitarlo. Enfriamiento 5 s.'),
        'feeding_frenzy': ('Frenesí de hambre',
                           'Requiere 500+ de hambre (de golpes, telarañas devoradas y victorias). Un rugido empuja a las criaturas a '
                           '8 bloques y el cuerpo crece: +30 % de daño, +20 % de velocidad, el doble de regeneración, salto más alto; '
                           'el hambre se consume. Hasta 15 s. Enfriamiento 20 s.'),
        'host_takeover': ('Posesión del huésped',
                          'Solo en el Frenesí, con PvP activado en el servidor y si la configuración lo permite; cuesta 250 de '
                          'hambre. El simbionte envuelve a un jugador debilitado (menos del 60 % de vida o sujeto) y controla su '
                          'cuerpo hasta 10 s: caminar, golpes y, en Spider-Man, sus movimientos. La víctima ve venas y destellos y '
                          'puede liberarse. Pulsa otra vez para soltarlo. Enfriamiento 60 s; la víctima es inmune 60 s.'),
    },
    'hud': {
        'controls': 'WASD: guiar al huésped · Clic izq.: su golpe · Posesión del huésped: soltar',
        'controls_spider': 'WASD: guiar · Clic izq.: telaraña · 1-4: movimientos de Araña · Posesión del huésped: soltar',
        'held': 'El simbionte te retiene %s s más',
        'resist': '¡Pulsa clic izquierdo, derecho o Espacio para resistir!',
        'riding': 'Huésped: %s',
        'state.frenzy': 'Frenesí de hambre', 'state.host': 'Forma humana', 'state.recoil': '¡El simbionte retrocede!',
        'state.riding': 'Posesión del huésped', 'state.shell': 'Caparazón',
    },
    'refusals': {
        'disabled': 'La posesión del huésped está desactivada en este servidor.',
        'no_pvp': 'La posesión del huésped requiere PvP activado.',
        'no_rage': 'Primero hace falta el Frenesí de hambre.',
        'cooldown': 'El simbionte aún no está listo para otra posesión.',
        'hunger': 'No hay hambre suficiente: la posesión requiere 250.',
        'no_target': 'No hay ningún huésped adecuado cerca.',
        'immune': 'Ese huésped acaba de liberarse y es inmune.',
        'not_weakened': 'El huésped es demasiado fuerte: debilítalo (menos del 60 % de vida) o sujétalo con los tentáculos.',
    },
    'boss': 'Venom en Frenesí de hambre',
    'boss_cmd': '¡Venom se ha liberado!',
    'sounds': {
        'transform': 'El simbionte cubre el cuerpo', 'revert': 'El simbionte se retira', 'roar': 'Venom ruge', 'squirm': 'La masa viva se retuerce',
        'reform': 'El simbionte se recompone', 'shriek': 'El simbionte chilla', 'claw': 'Zarpazo', 'claw_hit': 'Las garras desgarran',
        'tendril_shot': 'Disparo de tentáculo', 'tendril_stick': 'El tentáculo se adhiere', 'zip': 'Impulso con tentáculo', 'lash': 'Latigazo de tentáculos',
        'slam': 'Golpe contra el suelo', 'grab': 'Los tentáculos agarran', 'throw': 'Lanzamiento', 'shell': 'El caparazón se endurece',
        'web_tear': 'La telaraña se rompe', 'web_eat': 'El simbionte devora la telaraña', 'clash': 'Choque con la Araña',
        'engulf': 'El simbionte envuelve al huésped', 'heartbeat': 'Latido', 'breath': 'Respiración pesada', 'whisper': 'Susurro dentro de la cabeza',
        'release': 'El simbionte suelta al huésped', 'refuse': 'El simbionte no obedece',
    },
}

ZH = {
    'hero': '毒液', 'race': '成为毒液', 'speaker': '毒液', 'inner': '体内的声音',
    'lines': {
        'enter': '我们是毒液。', 'transform': '我们饿了。', 'pull': '过来！', 'rage': '饿！', 'takeover': '现在你就是我们。',
        'spider': '小蜘蛛……', 'recoil': '火！好痛！',
    },
    'whispers': [
        '别抵抗。我们在一起很温暖。', '你的心跳得好快……', '我们正用你的眼睛看。', '你很弱。我们不是。',
        '把你的手臂给我们。把你的腿给我们。', '嘘……嘘……', '我们知道你害怕什么。', '挣扎吧。我们喜欢。',
    ],
    'abilities': {
        'symbiosis': ('共生',
                      '共生体在 2.3 秒内覆盖全身：四肢、躯干和头部、身高与肌肉、巨口、双眼和徽记，最后摆出架势。变身后：40 点生命、'
                      '护甲、速度 +18%、约 11 格跳跃、在墙面和外墙上奔跑、再生。左键：利爪（三连击）；右键：用触手抓取（之后左键投掷，'
                      '右键砸地）。再次按下，共生体退去。冷却 2 秒。'),
        'tendril': ('触手',
                    '射出 28 格触手。射中墙壁：冲向墙面；射中敌人：拉过来并抓住（对首领、巨型目标或按住 Shift：冲向对方）；射中蜘蛛的'
                    '蛛丝：缠住丝线并攀到锚点。冷却 1.3 秒。'),
        'tentacle_lash': ('触手鞭笞',
                          '三段触手连击：环形横扫（4.6 格，8 点伤害）、向前穿刺（7.5 格，12 点伤害）和自上而下的重砸（半径 5，13 '
                          '点伤害）。可与利爪混合连招，并能切断蛛丝。第三击后冷却 3.5 秒。'),
        'symbiote_shell': ('共生体甲壳',
                           '共生体硬化成带刺的甲壳：受到伤害 -60%，移动变慢，消耗饥饿值。最长 6 秒；再次按下解除。冷却 5 秒。'),
        'feeding_frenzy': ('饥饿狂暴',
                           '需要 500 以上饥饿值（来自攻击、吞下的蛛丝和击杀）。怒吼击退 8 格内的生物，身体变大：伤害 +30%，速度 '
                           '+20%，再生翻倍，跳得更高；饥饿值逐渐消耗。最长 15 秒。冷却 20 秒。'),
        'host_takeover': ('宿主占据',
                          '仅限饥饿狂暴中、服务器开启 PvP 且配置允许时使用；消耗 250 饥饿值。共生体包裹一名虚弱的玩家（生命低于 60% '
                          '或被抓住），并操控其身体最多 10 秒：行走、攻击，若宿主是蜘蛛侠，还能使用他的招式。受害者会看到血管与闪现，'
                          '并可以挣脱。再次按下松开。冷却 60 秒；受害者获得 60 秒免疫。'),
    },
    'hud': {
        'controls': 'WASD：操控宿主 · 左键：宿主出拳 · 宿主占据：松开',
        'controls_spider': 'WASD：操控 · 左键：蛛丝 · 1-4：蜘蛛招式 · 宿主占据：松开',
        'held': '共生体还会控制你 %s 秒',
        'resist': '按左键、右键或空格——反抗！',
        'riding': '宿主：%s',
        'state.frenzy': '饥饿狂暴', 'state.host': '人类形态', 'state.recoil': '共生体退缩了！',
        'state.riding': '宿主占据', 'state.shell': '甲壳',
    },
    'refusals': {
        'disabled': '此服务器已禁用宿主占据。',
        'no_pvp': '宿主占据需要开启 PvP。',
        'no_rage': '需要先进入饥饿狂暴。',
        'cooldown': '共生体还没准备好再次占据。',
        'hunger': '饥饿值不足：占据需要 250。',
        'no_target': '附近没有合适的宿主。',
        'immune': '这个宿主刚刚被释放，目前免疫。',
        'not_weakened': '宿主太强了：先削弱他（生命低于 60%）或用触手抓住他。',
    },
    'boss': '饥饿狂暴的毒液',
    'boss_cmd': '毒液挣脱了束缚！',
    'sounds': {
        'transform': '共生体覆盖身体', 'revert': '共生体退去', 'roar': '毒液咆哮', 'squirm': '活体物质蠕动', 'reform': '共生体重新愈合',
        'shriek': '共生体尖叫', 'claw': '利爪挥动', 'claw_hit': '利爪撕裂', 'tendril_shot': '触手射出', 'tendril_stick': '触手粘住',
        'zip': '触手牵引冲刺', 'lash': '触手鞭笞', 'slam': '砸向地面', 'grab': '触手抓取', 'throw': '投掷', 'shell': '甲壳硬化',
        'web_tear': '蛛丝断裂', 'web_eat': '共生体吞噬蛛丝', 'clash': '与蜘蛛交锋', 'engulf': '共生体包裹宿主', 'heartbeat': '心跳',
        'breath': '沉重的呼吸', 'whisper': '脑中的低语', 'release': '共生体放开宿主', 'refuse': '共生体不听使唤',
    },
}

LOCALES = {'ru_ru': RU, 'en_us': EN, 'de_de': DE, 'tr_tr': TR, 'zh_cn': ZH,
           **{k: ES for k in ('es_es', 'es_ar', 'es_cl', 'es_ec', 'es_mx', 'es_uy', 'es_ve')}}


def build(t):
    out = {
        'hero.viltrumitecore.venom': t['hero'],
        'gui.viltrumitecore.race_selection.become_venom': t['race'],
        'subtitle.viltrumitecore.speaker.venom': t['speaker'],
        'subtitle.viltrumitecore.speaker.venom_inner': t['inner'],
    }
    for k in LINES:
        out[f'subtitle.viltrumitecore.venom.{k}'] = t['lines'][k]
    assert len(t['whispers']) == WHISPERS
    for i, w in enumerate(t['whispers']):
        out[f'subtitle.viltrumitecore.venom.whisper_{i}'] = w
    for a in ABILITIES:
        name, desc = t['abilities'][a]
        out[f'ability.viltrumitecore.venom_{a}.name'] = name
        out[f'ability.viltrumitecore.venom_{a}.desc'] = desc
    for k in HUD:
        out[f'hud.viltrumitecore.venom.{k}'] = t['hud'][k]
    for k in REFUSALS:
        out[f'message.viltrumitecore.venom.takeover.{k}'] = t['refusals'][k]
    out['entity.viltrumitecore.venom_boss'] = t['boss']
    out['commands.viltrumitecore.venom.boss'] = t['boss_cmd']
    for s in SOUNDS:
        out[f'subtitles.viltrumitecore.venom.{s}'] = t['sounds'][s]
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    keys = None
    for loc, t in LOCALES.items():
        data = build(t)
        if keys is None:
            keys = set(data)
        assert set(data) == keys, loc
        assert all(v.strip() for v in data.values()), loc
        for k in ('hud.viltrumitecore.venom.held', 'hud.viltrumitecore.venom.riding'):
            assert data[k].count('%s') == 1, (loc, k)
        with open(os.path.join(OUT, f'{loc}.json'), 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write('\n')
    print(len(LOCALES), 'locales,', len(keys), 'keys')


if __name__ == '__main__':
    main()
