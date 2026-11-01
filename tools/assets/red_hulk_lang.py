"""Red Hulk localization overlay: writes viltrumitecore/src/main/lang-overlays/red_hulk/<locale>.json for all 12 locales
(ru_ru is the source; the es_* locales share one Spanish text). Keys: hero / race button / speaker, the four subtitle
lines (text only, never voiced), the six abilities, the HUD heat tiers, the 28 sound subtitles and the codex entry.
Run: python3 tools/assets/red_hulk_lang.py"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore', 'src', 'main', 'lang-overlays', 'red_hulk')
SOUNDS = ['swing', 'swing_heavy', 'punch', 'punch_heavy', 'hammer', 'grab', 'squeeze', 'throw', 'throw_impact', 'clap', 'seismic',
          'leap_launch', 'leap_land', 'blast_charge', 'blast_fire', 'fury_roar', 'overheat', 'vent', 'ult_charge', 'ult_release',
          'ult_recover', 'landing', 'footstep', 'heat_up', 'heat_hum', 'refuse', 'enter', 'hurt']
ABILITIES = ['thermal_clap', 'seismic_slam', 'destruction_leap', 'heat_blast', 'thermal_fury', 'critical_overheat']

RU = {
    'hero': 'Красный Халк',
    'race': 'Стать Красным Халком',
    'speaker': 'Красный Халк',
    'lines': {
        'enter': 'Генерал Росс на поле боя. Отойдите.',
        'fury': 'Хватит сдерживаться!',
        'critical': 'Всё здесь сгорит!',
        'overheated': 'Слишком… горячо…',
    },
    'abilities': {
        'thermal_clap': ('Термохлопок',
                         'Хлопок ладонями перед собой: конус 7 блоков, 10 урона, отбрасывание и поджог на 3 с. Каждая задетая цель '
                         'добавляет жар. Перезарядка 8 с.'),
        'seismic_slam': ('Сейсмический удар',
                         'Замах обоими кулаками и удар в землю: волна радиусом 6 блоков, 12 урона в центре и 6 на краю, враги '
                         'подлетают и оглушаются. +100 жара. Перезарядка 12 с.'),
        'destruction_leap': ('Разрушительный прыжок',
                             'Присед и мощный прыжок вперёд. В полёте повторное нажатие — пике вниз. Приземление: кратер радиусом '
                             '5 блоков, 12 урона, без урона от падения. Перезарядка 10 с.'),
        'heat_blast': ('Тепловой выброс',
                       'Нужно 300+ жара. Грудь раскаляется, затем луч жара на 16 блоков: 7 импульсов, урон растёт с жаром, поджог. '
                       'Тратит 60 % жара. Повторное нажатие прерывает луч. Перезарядка 15 с.'),
        'thermal_fury': ('Термоярость',
                         'Рёв: враги в 8 блоках отброшены и ослаблены. 12 с ярости: +15 % урона, −15 % входящего урона, +10 % '
                         'скорости, жар растёт сам и не остывает. Перезарядка 45 с.'),
        'critical_overheat': ('Критический перегрев',
                              'Ультимейт. Нужно 700+ жара. 3 с накопления (входящий урон вдвое меньше), затем взрыв радиусом 12 '
                              'блоков: до 30 урона, поджог, разлёт. Жар сбрасывается в ноль, затем 2,5 с уязвимой передышки. '
                              'Перезарядка 90 с.'),
    },
    'hud': {'0': 'Холодный', '1': 'Тёплый', '2': 'Горячий', '3': 'Раскалённый', 'overheat': 'ПЕРЕГРЕВ'},
    'sounds': {
        'swing': 'Свист кулака', 'swing_heavy': 'Тяжёлый замах', 'punch': 'Удар кулаком', 'punch_heavy': 'Мощный удар',
        'hammer': 'Двуручный молот', 'grab': 'Захват', 'squeeze': 'Сжатие хваткой', 'throw': 'Бросок', 'throw_impact': 'Брошенное тело врезается',
        'clap': 'Термохлопок', 'seismic': 'Земля раскалывается', 'leap_launch': 'Мощный прыжок', 'leap_land': 'Приземление-кратер',
        'blast_charge': 'Грудь раскаляется', 'blast_fire': 'Луч жара', 'fury_roar': 'Яростный рёв', 'overheat': 'Тело перегревается',
        'vent': 'Сброс пара', 'ult_charge': 'Нарастает критический жар', 'ult_release': 'Взрыв критического перегрева',
        'ult_recover': 'Остывающее тело', 'landing': 'Тяжёлое приземление', 'footstep': 'Тяжёлые шаги', 'heat_up': 'Жар усиливается',
        'heat_hum': 'Гул раскалённого тела', 'refuse': 'Красный Халк: не сейчас', 'enter': 'Красный Халк появляется', 'hurt': 'Красный Халк рычит от боли',
    },
    'codex': {
        'name': 'Красный Халк',
        'role': 'Генерал Таддеус «Громовержец» Росс, живая печь',
        'lore': 'Старый солдат, который получил силу, за которой гонялся всю жизнь. Его тело не зеленеет от гнева — оно раскаляется: '
                'чем дольше бой, тем горячее кожа, ярче глаза и страшнее удары.',
        'gameplay': 'Шкала жара 0–1000: удары, комбо, способности и часть входящего урона нагревают тело; жар усиливает удары (до +30 %), '
                    'с 500 вокруг горит тепловая аура. На 1000 — перегрев: сброс пара, 6 с замедления без тепловых способностей (Выброс, Ярость, ультимейт) и +15 % входящего урона. Комбо ЛКМ из пяти ударов '
                    'с двуручным молотом, захват и бросок на ПКМ, Термохлопок, Сейсмический удар, Разрушительный прыжок, Тепловой выброс, '
                    'Термоярость и ультимейт «Критический перегрев». Огонь и лава ранят его слабее обычного — большую часть урона они превращают в жар.',
        'relations': 'Всю жизнь охотился на Халка, пока сам не стал таким же. Капитан Америка помнит его генералом.',
    },
}

EN = {
    'hero': 'Red Hulk', 'race': 'Become the Red Hulk', 'speaker': 'Red Hulk',
    'lines': {'enter': 'General Ross on the field. Stand back.', 'fury': 'No more holding back!', 'critical': 'Everything here burns!',
              'overheated': 'Too... hot...'},
    'abilities': {
        'thermal_clap': ('Thermal Clap', 'Claps his palms ahead: a 7-block cone, 10 damage, knockback and 3 s of fire. Every target hit '
                                         'adds heat. Cooldown 8 s.'),
        'seismic_slam': ('Seismic Slam', 'Raises both fists and hammers the ground: a 6-block wave, 12 damage at the centre and 6 at the '
                                         'edge, foes are launched and stunned. +100 heat. Cooldown 12 s.'),
        'destruction_leap': ('Destruction Leap', 'Crouches and leaps forward. Press again in the air to dive. The landing leaves a 5-block '
                                                 'crater: 12 damage, no fall damage. Cooldown 10 s.'),
        'heat_blast': ('Heat Blast', 'Needs 300+ heat. His chest glows, then a 16-block beam of heat: 7 pulses whose damage grows with '
                                     'heat, sets foes on fire. Spends 60 % of the heat. Press again to stop. Cooldown 15 s.'),
        'thermal_fury': ('Thermal Fury', 'A roar knocks back and weakens foes within 8 blocks. 12 s of fury: +15 % damage, -15 % damage '
                                         'taken, +10 % speed, heat rises by itself and never cools. Cooldown 45 s.'),
        'critical_overheat': ('Critical Overheat', 'Ultimate. Needs 700+ heat. 3 s of charging (half damage taken), then a 12-block '
                                                   'blast: up to 30 damage, fire, foes flung away. Heat drops to zero, then 2.5 s of '
                                                   'vulnerable recovery. Cooldown 90 s.'),
    },
    'hud': {'0': 'Cold', '1': 'Warm', '2': 'Hot', '3': 'Blazing', 'overheat': 'OVERHEAT'},
    'sounds': {
        'swing': 'Fist whooshes', 'swing_heavy': 'Heavy windup', 'punch': 'Fist hits', 'punch_heavy': 'Crushing blow',
        'hammer': 'Double-fist hammer', 'grab': 'Grab', 'squeeze': 'Crushing grip', 'throw': 'Throw', 'throw_impact': 'Thrown body crashes',
        'clap': 'Thermal Clap', 'seismic': 'Ground splits', 'leap_launch': 'Mighty leap', 'leap_land': 'Crater landing',
        'blast_charge': 'Chest heats up', 'blast_fire': 'Heat beam', 'fury_roar': 'Furious roar', 'overheat': 'Body overheats',
        'vent': 'Steam vents', 'ult_charge': 'Critical heat builds', 'ult_release': 'Critical Overheat blast', 'ult_recover': 'Body cools down',
        'landing': 'Heavy landing', 'footstep': 'Heavy footsteps', 'heat_up': 'Heat rises', 'heat_hum': 'Red-hot body hums',
        'refuse': 'Red Hulk: not now', 'enter': 'Red Hulk arrives', 'hurt': 'Red Hulk growls in pain',
    },
    'codex': {
        'name': 'Red Hulk', 'role': 'General Thaddeus "Thunderbolt" Ross, a living furnace',
        'lore': 'An old soldier who got the power he hunted all his life. His body does not turn green with anger, it heats up: the '
                'longer the fight, the hotter his skin, the brighter his eyes and the harder his blows.',
        'gameplay': 'Heat gauge 0-1000: blows, combos, abilities and part of the damage taken heat the body; heat empowers his blows '
                    '(up to +30 %), from 500 a heat aura burns around him. At 1000 he overheats: a steam vent, then 6 s slowed, without the heat abilities (Blast, Fury, ultimate) and taking +15 % damage. '
                    'A five-hit LMB combo ending in a double-fist hammer, grab and throw on RMB, Thermal Clap, Seismic Slam, '
                    'Destruction Leap, Heat Blast, Thermal Fury and the Critical Overheat ultimate. Fire and lava hurt him far less: most of that damage turns into heat.',
        'relations': 'He hunted the Hulk all his life until he became one. Captain America remembers him as a general.',
    },
}

DE = {
    'hero': 'Roter Hulk', 'race': 'Werde zum Roten Hulk', 'speaker': 'Roter Hulk',
    'lines': {'enter': 'General Ross ist auf dem Feld. Zurück.', 'fury': 'Schluss mit Zurückhaltung!', 'critical': 'Hier brennt alles!',
              'overheated': 'Zu... heiß...'},
    'abilities': {
        'thermal_clap': ('Thermoklatscher', 'Klatscht die Handflächen nach vorn: Kegel von 7 Blöcken, 10 Schaden, Rückstoß und 3 s Feuer. '
                                            'Jedes Ziel bringt Hitze. Abklingzeit 8 s.'),
        'seismic_slam': ('Seismischer Schlag', 'Hebt beide Fäuste und schlägt in den Boden: Welle mit 6 Blöcken Radius, 12 Schaden in der '
                                               'Mitte, 6 am Rand, Gegner fliegen hoch und sind betäubt. +100 Hitze. Abklingzeit 12 s.'),
        'destruction_leap': ('Zerstörungssprung', 'Geht in die Hocke und springt weit nach vorn. In der Luft erneut drücken für einen '
                                                  'Sturzflug. Landung: Krater mit 5 Blöcken, 12 Schaden, kein Fallschaden. Abklingzeit 10 s.'),
        'heat_blast': ('Hitzestrahl', 'Braucht 300+ Hitze. Die Brust glüht auf, dann ein Hitzestrahl über 16 Blöcke: 7 Impulse, Schaden '
                                      'wächst mit der Hitze, setzt in Brand. Verbraucht 60 % der Hitze. Erneut drücken bricht ab. Abklingzeit 15 s.'),
        'thermal_fury': ('Thermowut', 'Ein Brüllen stößt Gegner im Umkreis von 8 Blöcken zurück und schwächt sie. 12 s Wut: +15 % Schaden, '
                                      '-15 % erlittener Schaden, +10 % Tempo, die Hitze steigt von selbst. Abklingzeit 45 s.'),
        'critical_overheat': ('Kritische Überhitzung', 'Ultimative Fähigkeit. Braucht 700+ Hitze. 3 s Aufladen (halber Schaden), dann '
                                                       'eine Explosion mit 12 Blöcken Radius: bis zu 30 Schaden, Feuer, Gegner fliegen '
                                                       'weg. Hitze fällt auf null, danach 2,5 s verwundbare Erholung. Abklingzeit 90 s.'),
    },
    'hud': {'0': 'Kalt', '1': 'Warm', '2': 'Heiß', '3': 'Glühend', 'overheat': 'ÜBERHITZT'},
    'sounds': {
        'swing': 'Faust saust', 'swing_heavy': 'Schweres Ausholen', 'punch': 'Faustschlag', 'punch_heavy': 'Wuchtiger Schlag',
        'hammer': 'Doppelfaust-Hammer', 'grab': 'Griff', 'squeeze': 'Zerquetschender Griff', 'throw': 'Wurf', 'throw_impact': 'Geworfener Körper schlägt ein',
        'clap': 'Thermoklatscher', 'seismic': 'Boden bricht', 'leap_launch': 'Gewaltiger Sprung', 'leap_land': 'Krater-Landung',
        'blast_charge': 'Brust glüht auf', 'blast_fire': 'Hitzestrahl', 'fury_roar': 'Wütendes Brüllen', 'overheat': 'Körper überhitzt',
        'vent': 'Dampf entweicht', 'ult_charge': 'Kritische Hitze steigt', 'ult_release': 'Explosion der Überhitzung',
        'ult_recover': 'Körper kühlt ab', 'landing': 'Schwere Landung', 'footstep': 'Schwere Schritte', 'heat_up': 'Hitze steigt',
        'heat_hum': 'Glühender Körper brummt', 'refuse': 'Roter Hulk: nicht jetzt', 'enter': 'Roter Hulk erscheint', 'hurt': 'Roter Hulk knurrt vor Schmerz',
    },
    'codex': {
        'name': 'Roter Hulk', 'role': 'General Thaddeus „Thunderbolt“ Ross, ein lebender Hochofen',
        'lore': 'Ein alter Soldat, der die Macht bekam, die er sein Leben lang jagte. Sein Körper wird vor Wut nicht grün, er glüht: je '
                'länger der Kampf, desto heißer die Haut, heller die Augen und härter die Schläge.',
        'gameplay': 'Hitzeleiste 0-1000: Schläge, Kombos, Fähigkeiten und ein Teil des erlittenen Schadens heizen den Körper; Hitze '
                    'verstärkt seine Schläge (bis +30 %), ab 500 brennt eine Hitzeaura. Bei 1000 überhitzt er: Dampfstoß, dann 6 s verlangsamt, ohne Hitzefähigkeiten '
                    '(Strahl, Wut, Ultimative) und mit +15 % erlittenem Schaden. Fünfteilige LMT-Kombo mit Doppelfaust-Hammer, Griff und Wurf auf RMT, Thermoklatscher, Seismischer '
                    'Schlag, Zerstörungssprung, Hitzestrahl, Thermowut und die ultimative Kritische Überhitzung. Feuer und Lava schaden ihm weniger: der Großteil wird zu Hitze.',
        'relations': 'Er jagte den Hulk sein Leben lang, bis er selbst einer wurde. Captain America kennt ihn noch als General.',
    },
}

ES = {
    'hero': 'Hulk Rojo', 'race': 'Convertirse en Hulk Rojo', 'speaker': 'Hulk Rojo',
    'lines': {'enter': 'El general Ross está en el campo. Atrás.', 'fury': '¡Se acabó contenerse!', 'critical': '¡Todo aquí arderá!',
              'overheated': 'Demasiado... calor...'},
    'abilities': {
        'thermal_clap': ('Palmada térmica', 'Choca las palmas hacia delante: cono de 7 bloques, 10 de daño, empuje y 3 s de fuego. Cada '
                                            'objetivo alcanzado suma calor. Enfriamiento 8 s.'),
        'seismic_slam': ('Golpe sísmico', 'Alza ambos puños y golpea el suelo: onda de 6 bloques, 12 de daño en el centro y 6 en el borde; '
                                          'los enemigos salen volando y quedan aturdidos. +100 de calor. Enfriamiento 12 s.'),
        'destruction_leap': ('Salto destructor', 'Se agacha y salta con fuerza hacia delante. Pulsa otra vez en el aire para caer en picado. '
                                                 'Al aterrizar deja un cráter de 5 bloques: 12 de daño, sin daño por caída. Enfriamiento 10 s.'),
        'heat_blast': ('Descarga de calor', 'Requiere 300+ de calor. El pecho se enciende y lanza un rayo de calor de 16 bloques: 7 pulsos '
                                            'cuyo daño crece con el calor, prende fuego. Gasta el 60 % del calor. Pulsa otra vez para cortar. Enfriamiento 15 s.'),
        'thermal_fury': ('Furia térmica', 'Un rugido empuja y debilita a los enemigos en 8 bloques. 12 s de furia: +15 % de daño, -15 % de '
                                          'daño recibido, +10 % de velocidad, el calor sube solo y no se enfría. Enfriamiento 45 s.'),
        'critical_overheat': ('Sobrecalentamiento crítico', 'Definitiva. Requiere 700+ de calor. 3 s de carga (la mitad de daño recibido) y '
                                                            'una explosión de 12 bloques: hasta 30 de daño, fuego, enemigos despedidos. El calor '
                                                            'cae a cero y siguen 2,5 s de recuperación vulnerable. Enfriamiento 90 s.'),
    },
    'hud': {'0': 'Frío', '1': 'Tibio', '2': 'Caliente', '3': 'Ardiente', 'overheat': 'SOBRECALENTADO'},
    'sounds': {
        'swing': 'Silba un puño', 'swing_heavy': 'Impulso pesado', 'punch': 'Puñetazo', 'punch_heavy': 'Golpe demoledor',
        'hammer': 'Martillo a dos puños', 'grab': 'Agarre', 'squeeze': 'Agarre aplastante', 'throw': 'Lanzamiento', 'throw_impact': 'Choca un cuerpo lanzado',
        'clap': 'Palmada térmica', 'seismic': 'El suelo se parte', 'leap_launch': 'Salto poderoso', 'leap_land': 'Aterrizaje en cráter',
        'blast_charge': 'El pecho se calienta', 'blast_fire': 'Rayo de calor', 'fury_roar': 'Rugido furioso', 'overheat': 'El cuerpo se sobrecalienta',
        'vent': 'Sale vapor', 'ult_charge': 'Crece el calor crítico', 'ult_release': 'Explosión del sobrecalentamiento', 'ult_recover': 'El cuerpo se enfría',
        'landing': 'Aterrizaje pesado', 'footstep': 'Pasos pesados', 'heat_up': 'Sube el calor', 'heat_hum': 'Zumba el cuerpo al rojo',
        'refuse': 'Hulk Rojo: ahora no', 'enter': 'Llega Hulk Rojo', 'hurt': 'Hulk Rojo gruñe de dolor',
    },
    'codex': {
        'name': 'Hulk Rojo', 'role': 'General Thaddeus «Thunderbolt» Ross, un horno viviente',
        'lore': 'Un viejo soldado que obtuvo el poder que persiguió toda su vida. Su cuerpo no se vuelve verde de ira: se calienta. Cuanto '
                'más dura el combate, más arde su piel, más brillan sus ojos y más fuerte golpea.',
        'gameplay': 'Barra de calor 0-1000: golpes, combos, habilidades y parte del daño recibido calientan el cuerpo; el calor potencia '
                    'sus golpes (hasta +30 %) y desde 500 arde un aura de calor. A 1000 se sobrecalienta: expulsa vapor y pasa 6 s ralentizado, sin habilidades de '
                    'calor (Descarga, Furia, definitiva) y recibiendo +15 % de daño. Combo de cinco golpes con clic izquierdo que acaba en martillo, agarre y lanzamiento con clic derecho, '
                    'Palmada térmica, Golpe sísmico, Salto destructor, Descarga de calor, Furia térmica y la definitiva Sobrecalentamiento '
                    'crítico. El fuego y la lava le hacen mucho menos daño: la mayor parte se convierte en calor.',
        'relations': 'Persiguió a Hulk toda su vida hasta convertirse en uno. El Capitán América aún lo recuerda como general.',
    },
}

TR = {
    'hero': 'Kızıl Hulk', 'race': 'Kızıl Hulk ol', 'speaker': 'Kızıl Hulk',
    'lines': {'enter': 'General Ross sahada. Geri çekilin.', 'fury': 'Artık kendimi tutmayacağım!', 'critical': 'Burada her şey yanacak!',
              'overheated': 'Çok... sıcak...'},
    'abilities': {
        'thermal_clap': ('Termal Alkış', 'Avuçlarını önüne çarpar: 7 blokluk koni, 10 hasar, geri itme ve 3 sn yanma. Vurulan her hedef '
                                         'ısı katar. Bekleme 8 sn.'),
        'seismic_slam': ('Sismik Darbe', 'İki yumruğunu kaldırıp yere vurur: 6 blokluk dalga, merkezde 12, kenarda 6 hasar; düşmanlar '
                                         'havaya fırlar ve sersemler. +100 ısı. Bekleme 12 sn.'),
        'destruction_leap': ('Yıkım Sıçrayışı', 'Çömelip ileri güçlü bir sıçrayış yapar. Havada tekrar bas: dalış. İniş 5 blokluk krater '
                                                'açar: 12 hasar, düşme hasarı yok. Bekleme 10 sn.'),
        'heat_blast': ('Isı Patlaması', '300+ ısı gerekir. Göğsü kızarır, ardından 16 blokluk ısı ışını: hasarı ısıyla artan 7 darbe, '
                                        'tutuşturur. Isının %60\'ını harcar. Tekrar basınca durur. Bekleme 15 sn.'),
        'thermal_fury': ('Termal Öfke', 'Kükreme 8 blok içindeki düşmanları iter ve zayıflatır. 12 sn öfke: +%15 hasar, -%15 alınan hasar, '
                                        '+%10 hız; ısı kendiliğinden yükselir ve soğumaz. Bekleme 45 sn.'),
        'critical_overheat': ('Kritik Aşırı Isınma', 'Nihai yetenek. 700+ ısı gerekir. 3 sn yükleme (alınan hasar yarıya iner), ardından 12 '
                                                     'blokluk patlama: 30\'a kadar hasar, ateş, savrulan düşmanlar. Isı sıfırlanır, ardından '
                                                     '2,5 sn savunmasız toparlanma. Bekleme 90 sn.'),
    },
    'hud': {'0': 'Soğuk', '1': 'Ilık', '2': 'Sıcak', '3': 'Kor', 'overheat': 'AŞIRI ISINMA'},
    'sounds': {
        'swing': 'Yumruk savrulur', 'swing_heavy': 'Ağır hazırlık', 'punch': 'Yumruk darbesi', 'punch_heavy': 'Ezici darbe',
        'hammer': 'Çift yumruk çekici', 'grab': 'Kavrama', 'squeeze': 'Ezici kavrayış', 'throw': 'Fırlatma', 'throw_impact': 'Fırlatılan beden çarpar',
        'clap': 'Termal Alkış', 'seismic': 'Yer yarılır', 'leap_launch': 'Güçlü sıçrayış', 'leap_land': 'Krater inişi',
        'blast_charge': 'Göğüs kızarır', 'blast_fire': 'Isı ışını', 'fury_roar': 'Öfkeli kükreme', 'overheat': 'Beden aşırı ısınır',
        'vent': 'Buhar boşalır', 'ult_charge': 'Kritik ısı birikir', 'ult_release': 'Kritik ısınma patlaması', 'ult_recover': 'Beden soğur',
        'landing': 'Ağır iniş', 'footstep': 'Ağır adımlar', 'heat_up': 'Isı yükselir', 'heat_hum': 'Kızgın beden uğuldar',
        'refuse': 'Kızıl Hulk: şimdi değil', 'enter': 'Kızıl Hulk gelir', 'hurt': 'Kızıl Hulk acıyla hırlar',
    },
    'codex': {
        'name': 'Kızıl Hulk', 'role': 'General Thaddeus "Thunderbolt" Ross, yaşayan bir fırın',
        'lore': 'Ömrü boyunca peşinden koştuğu güce sonunda kavuşan yaşlı bir asker. Bedeni öfkeyle yeşermez, kızarır: savaş uzadıkça '
                'derisi ısınır, gözleri parlar, darbeleri ağırlaşır.',
        'gameplay': 'Isı göstergesi 0-1000: darbeler, kombolar, yetenekler ve alınan hasarın bir kısmı bedeni ısıtır; ısı darbelerini '
                    'güçlendirir (+%30\'a kadar), 500\'den itibaren etrafında ısı aurası yanar. 1000\'de aşırı ısınır: buhar boşalır, ardından 6 sn yavaşlar, '
                    'ısı yeteneklerini (Patlama, Öfke, nihai) kullanamaz ve %15 fazla hasar alır. Sol tıkla çekiçle biten beş vuruşluk kombo, sağ tıkla kavrayıp fırlatma, Termal Alkış, Sismik '
                    'Darbe, Yıkım Sıçrayışı, Isı Patlaması, Termal Öfke ve nihai Kritik Aşırı Isınma. Ateş ve lav ona çok daha az hasar verir: çoğu ısıya dönüşür.',
        'relations': 'Ömrü boyunca Hulk\'u avladı, sonunda kendisi de öyle oldu. Kaptan Amerika onu hâlâ bir general olarak hatırlar.',
    },
}

ZH = {
    'hero': '红浩克', 'race': '成为红浩克', 'speaker': '红浩克',
    'lines': {'enter': '罗斯将军到场。都退后。', 'fury': '不再忍了！', 'critical': '这里的一切都会燃尽！', 'overheated': '太……烫了……'},
    'abilities': {
        'thermal_clap': ('热能拍击', '双掌向前猛拍：7 格锥形范围，10 点伤害，击退并点燃 3 秒。每命中一个目标都会增加热量。冷却 8 秒。'),
        'seismic_slam': ('震地猛击', '高举双拳砸向地面：半径 6 格的冲击波，中心 12 点、边缘 6 点伤害，敌人被掀飞并眩晕。热量 +100。冷却 12 秒。'),
        'destruction_leap': ('毁灭跳跃', '下蹲后向前猛跃。空中再次按下可俯冲。落地砸出半径 5 格的陨坑：12 点伤害，免疫摔落伤害。冷却 10 秒。'),
        'heat_blast': ('热能冲击', '需要 300 以上热量。胸口发烫，随后射出 16 格热能光束：7 次脉冲，伤害随热量提升并点燃目标。消耗 60% 热量。再次按下可提前停止。冷却 15 秒。'),
        'thermal_fury': ('热能狂怒', '怒吼击退并削弱 8 格内的敌人。12 秒狂怒：伤害 +15%，受到伤害 -15%，速度 +10%，热量自动上升且不会冷却。冷却 45 秒。'),
        'critical_overheat': ('临界过热', '终极技能。需要 700 以上热量。蓄力 3 秒（受到伤害减半），随后引发半径 12 格的爆炸：最多 30 点伤害，点燃并炸飞敌人。热量归零，之后有 2.5 秒的脆弱恢复期。冷却 90 秒。'),
    },
    'hud': {'0': '冷却', '1': '温热', '2': '炽热', '3': '灼烧', 'overheat': '过热'},
    'sounds': {
        'swing': '拳风呼啸', 'swing_heavy': '沉重蓄力', 'punch': '拳击', 'punch_heavy': '粉碎重击', 'hammer': '双拳锤击', 'grab': '抓取',
        'squeeze': '碾压紧握', 'throw': '投掷', 'throw_impact': '被投出的身体撞击', 'clap': '热能拍击', 'seismic': '大地开裂',
        'leap_launch': '猛力起跳', 'leap_land': '陨坑落地', 'blast_charge': '胸口发烫', 'blast_fire': '热能光束', 'fury_roar': '狂怒咆哮',
        'overheat': '身体过热', 'vent': '蒸汽喷出', 'ult_charge': '临界热量积聚', 'ult_release': '临界过热爆炸', 'ult_recover': '身体冷却',
        'landing': '沉重落地', 'footstep': '沉重脚步', 'heat_up': '热量上升', 'heat_hum': '赤热身体嗡鸣', 'refuse': '红浩克：现在不行',
        'enter': '红浩克登场', 'hurt': '红浩克痛苦低吼',
    },
    'codex': {
        'name': '红浩克', 'role': '“雷霆”萨迪厄斯·罗斯将军，一座活着的熔炉',
        'lore': '一位老兵，终于得到了他追逐一生的力量。他的身体不会因愤怒变绿，而是不断升温：战斗越久，皮肤越烫，双眼越亮，拳头越重。',
        'gameplay': '热量条 0–1000：攻击、连击、技能以及部分所受伤害都会让身体升温；热量强化他的攻击（最多 +30%），500 起身边燃起热能光环。'
                    '达到 1000 时过热：喷出蒸汽，随后 6 秒减速、无法使用热能技能（冲击、狂怒、终极技），并承受 +15% 伤害。左键五连击以双拳锤击收尾，右键抓取并投掷，另有热能拍击、震地猛击、'
                    '毁灭跳跃、热能冲击、热能狂怒和终极技能“临界过热”。火焰与岩浆对他的伤害大减——大部分会转化为热量。',
        'relations': '他追捕浩克一辈子，最后自己也变成了浩克。美国队长仍记得他当将军时的样子。',
    },
}

LOCALES = {'ru_ru': RU, 'en_us': EN, 'de_de': DE, 'tr_tr': TR, 'zh_cn': ZH,
           **{k: ES for k in ('es_es', 'es_ar', 'es_cl', 'es_ec', 'es_mx', 'es_uy', 'es_ve')}}


def build(t):
    out = {
        'hero.viltrumitecore.red_hulk': t['hero'],
        'gui.viltrumitecore.race_selection.become_red_hulk': t['race'],
        'subtitle.viltrumitecore.speaker.red_hulk': t['speaker'],
    }
    for k, v in t['lines'].items():
        out[f'subtitle.viltrumitecore.red_hulk.{k}'] = v
    for a in ABILITIES:
        name, desc = t['abilities'][a]
        out[f'ability.viltrumitecore.red_hulk_{a}.name'] = name
        out[f'ability.viltrumitecore.red_hulk_{a}.desc'] = desc
    for k in ('0', '1', '2', '3'):
        out[f'hud.viltrumitecore.red_hulk.tier.{k}'] = t['hud'][k]
    out['hud.viltrumitecore.red_hulk.overheat'] = t['hud']['overheat']
    for s in SOUNDS:
        out[f'subtitles.viltrumitecore.red_hulk.{s}'] = t['sounds'][s]
    for k, v in t['codex'].items():
        out[f'codex.viltrumitecore.red_hulk.{k}'] = v
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
        with open(os.path.join(OUT, f'{loc}.json'), 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write('\n')
    print(len(LOCALES), 'locales,', len(keys), 'keys')


if __name__ == '__main__':
    main()
