"""Script of the Atheria street conversations and calls (issue #136): one source of truth for the voice
recordings (tools/sfx/city_life.py), the subtitles (lang overlays) and the line table the server plays
(CityLifeCatalog.java, generated). Text in Russian (spoken) and English (subtitle translation).

``{male|female}`` picks the form that agrees with the speaker (Russian past tense), so the female voices say
"видела" and get their own subtitle. Nothing addresses the listener by gender.
"""

# mood buckets the server picks a dialogue from (CityMood): calm talk, uneasy talk after a scare nearby,
# shaken talk after destruction; ``when`` narrows calm talk to a time of day / weather
CALM, WARY, AFTER = "calm", "wary", "after"
ANY, MORNING, DAY, EVENING, RAIN, CLEAR = "any", "morning", "day", "evening", "rain", "clear"

LINES = {
    # --- calm
    "greet_1": ("Привет! Сто лет тебя не {видел|видела}!", "Hi! Haven't seen you in ages!"),
    "greet_2": ("Привет! Всё работа, ты же знаешь.", "Hi! Work, work, you know how it is."),
    "greet_3": ("Ну, не пропадай. Ещё увидимся!", "Well, don't disappear. See you around!"),
    "weather_1": ("Ну и погода сегодня, а? Красота!", "What weather today, huh? Lovely!"),
    "weather_2": ("Да, наконец-то солнце. Хоть погулять можно.", "Yes, sun at last. A good day for a walk."),
    "market_1": ("Ты на рынок? Говорят, свежий хлеб привезли.", "Off to the market? They say fresh bread came in."),
    "market_2": ("Как раз туда. Тебе взять батон?", "Heading there now. Shall I get you a loaf?"),
    "market_3": ("Возьми, пожалуйста. Спасибо!", "Yes, please. Thanks!"),
    "heroes_1": ("Ты в курсе? Над площадью опять кто-то летал.", "Have you heard? Someone was flying over the square again."),
    "heroes_2": ("Да уж. Только бы без разрушений в этот раз.", "Oh yes. Let's hope nothing gets wrecked this time."),
    "heroes_3": ("И не говори. Окна с прошлого раза ещё меняют.", "Tell me about it. They're still replacing the windows from last time."),
    "work_1": ("Как на новой работе?", "How's the new job?"),
    "work_2": ("Привыкаю потихоньку. Начальник строгий, но честный.", "Getting used to it. The boss is strict but fair."),
    "work_3": ("Главное, что нравится.", "What matters is you like it."),
    "lunch_1": ("Пойдём пообедаем? Тут рядом кафе открылось.", "Shall we grab lunch? A café opened nearby."),
    "lunch_2": ("Давай. Только недолго, у меня встреча в два.", "Sure. Not for long though, I have a meeting at two."),
    "way_1": ("Извините, не подскажете, как пройти к вокзалу?", "Excuse me, how do I get to the station?"),
    "way_2": ("Прямо до перекрёстка, потом налево. Минут пять.", "Straight on to the crossroads, then left. About five minutes."),
    "way_3": ("Спасибо большое!", "Thank you so much!"),
    "kids_1": ("Как дети?", "How are the kids?"),
    "kids_2": ("Растут! Младший в этом году в школу пошёл.", "Growing up! The youngest started school this year."),
    "kids_3": ("Ого! Как время летит.", "Wow! Time flies."),
    "match_1": ("Как тебе вчерашний матч?", "What did you think of yesterday's match?"),
    "match_2": ("Последняя минута — просто чудо!", "That last minute was a miracle!"),
    "match_3": ("Весь двор кричал. Я чуть с дивана не {упал|упала}!", "The whole block was cheering. I nearly fell off the couch!"),
    "tower_1": ("Смотри, на той башне опять леса поставили.", "Look, they've put scaffolding on that tower again."),
    "tower_2": ("Ремонт, наверное. Всё никак не доделают.", "Repairs, probably. They never finish."),
    "phone_1": ("Слушай, где мой телефон? Нигде не могу найти.", "Listen, where's my phone? I can't find it anywhere."),
    "phone_2": ("Он у тебя в руке.", "It's in your hand."),
    "phone_3": ("Ой... Точно. Спасибо.", "Oh... Right. Thanks."),
    "prices_1": ("Цены опять подняли. Скоро на хлеб не хватит.", "Prices went up again. Soon we won't afford bread."),
    "prices_2": ("И не говори. Зато зарплату обещают поднять.", "Tell me about it. At least they promise a raise."),
    "tourist_1": ("Какой красивый город! Я здесь первый раз.", "What a beautiful city! It's my first time here."),
    "tourist_2": ("Добро пожаловать! Загляните обязательно на главную площадь.", "Welcome! Be sure to visit the main square."),
    "morning_1": ("Доброе утро! Ты сегодня рано.", "Good morning! You're early today."),
    "morning_2": ("Доброе! Хочу всё успеть до обеда.", "Morning! I want to get it all done before lunch."),
    "evening_1": ("Поздно уже, пора по домам.", "It's getting late, time to head home."),
    "evening_2": ("Да, завтра рано вставать. Спокойной ночи!", "Yes, early start tomorrow. Good night!"),
    "rain_1": ("Опять этот дождь. Промокнем же.", "This rain again. We'll get soaked."),
    "rain_2": ("Бежим под навес!", "Let's run under the awning!"),
    # --- wary
    "hear_1": ("Ты это слышишь? Опять грохочет.", "Do you hear that? Rumbling again."),
    "hear_2": ("Где-то далеко. Давай лучше держаться подальше.", "Somewhere far off. Let's keep our distance."),
    "uneasy_1": ("Что-то мне здесь неспокойно.", "I don't feel safe here."),
    "uneasy_2": ("Мне тоже. Пойдём отсюда.", "Me neither. Let's go."),
    "news_1": ("По радио сказали не выходить из дома.", "The radio said to stay indoors."),
    "news_2": ("А я только за хлебом...", "And I only went out for bread..."),
    # --- aftermath
    "okay_1": ("Как ты? Всё в порядке?", "How are you? Are you all right?"),
    "okay_2": ("Вроде да... Только руки трясутся.", "I think so... My hands are shaking."),
    "okay_3": ("Держись. Главное, что живы.", "Hang in there. What matters is we're alive."),
    "what_1": ("Что это вообще было?", "What on earth was that?"),
    "what_2": ("Не знаю. Никогда такого не {видел|видела}.", "I don't know. I've never seen anything like it."),
    "home_1": ("Там же был мой дом...", "My home was right there..."),
    "home_2": ("Пойдём. Здесь опасно оставаться.", "Come on. It's not safe to stay here."),
    "rescue_1": ("Кто-нибудь вызвал спасателей?", "Has anyone called the rescue service?"),
    "rescue_2": ("Звоню, звоню, а там всё занято.", "I keep calling, but the line is busy."),
    "where_1": ("Куда все побежали?", "Where did everyone run?"),
    "where_2": ("К площади. Там вроде безопаснее.", "To the square. It seems safer there."),
    "rubble_1": ("Помоги разобрать завал, там кто-то есть!", "Help me clear the rubble, someone's in there!"),
    "rubble_2": ("Иду! Осторожно, всё шатается.", "Coming! Careful, it's all shaking."),
    # --- calls (one citizen, no partner)
    "startle_1": ("Что это?!", "What's that?!"),
    "startle_2": ("Смотрите! Там, наверху!", "Look! Up there!"),
    "startle_3": ("Ой, мамочки...", "Oh, my goodness..."),
    "flee_1": ("Бегите!", "Run!"),
    "flee_2": ("Все в укрытие!", "Everyone, take cover!"),
    "flee_3": ("Сюда, быстрее!", "This way, hurry!"),
    "flee_4": ("Осторожно!", "Watch out!"),
    "flee_5": ("Не толкайтесь!", "Stop pushing!"),
    "help_1": ("Помогите!", "Help!"),
    "help_2": ("Кто-нибудь, помогите!", "Somebody, help!"),
    "help_3": ("Помогите, я не могу встать!", "Help, I can't get up!"),
    "help_4": ("Мама! Где ты?", "Mum! Where are you?"),
    "survivor_1": ("Есть кто живой?", "Is anyone alive?"),
    "survivor_2": ("Сюда! Здесь раненый!", "Over here! Someone's hurt!"),
}

# (id, mood, when, weight, [(role, line)]): role 0 is the one who starts, 1 the partner
DIALOGUES = [
    ("greet", CALM, ANY, 3, [(0, "greet_1"), (1, "greet_2"), (0, "greet_3")]),
    ("weather", CALM, CLEAR, 2, [(0, "weather_1"), (1, "weather_2")]),
    ("market", CALM, DAY, 2, [(0, "market_1"), (1, "market_2"), (0, "market_3")]),
    ("heroes", CALM, ANY, 3, [(0, "heroes_1"), (1, "heroes_2"), (0, "heroes_3")]),
    ("work", CALM, ANY, 2, [(0, "work_1"), (1, "work_2"), (0, "work_3")]),
    ("lunch", CALM, DAY, 2, [(0, "lunch_1"), (1, "lunch_2")]),
    ("way", CALM, ANY, 2, [(0, "way_1"), (1, "way_2"), (0, "way_3")]),
    ("kids", CALM, ANY, 2, [(0, "kids_1"), (1, "kids_2"), (0, "kids_3")]),
    ("match", CALM, ANY, 2, [(0, "match_1"), (1, "match_2"), (0, "match_3")]),
    ("tower", CALM, DAY, 1, [(0, "tower_1"), (1, "tower_2")]),
    ("phone", CALM, ANY, 1, [(0, "phone_1"), (1, "phone_2"), (0, "phone_3")]),
    ("prices", CALM, ANY, 2, [(0, "prices_1"), (1, "prices_2")]),
    ("tourist", CALM, DAY, 1, [(0, "tourist_1"), (1, "tourist_2")]),
    ("morning", CALM, MORNING, 4, [(0, "morning_1"), (1, "morning_2")]),
    ("evening", CALM, EVENING, 4, [(0, "evening_1"), (1, "evening_2")]),
    ("rain", CALM, RAIN, 5, [(0, "rain_1"), (1, "rain_2")]),
    ("hear", WARY, ANY, 3, [(0, "hear_1"), (1, "hear_2")]),
    ("uneasy", WARY, ANY, 3, [(0, "uneasy_1"), (1, "uneasy_2")]),
    ("news", WARY, ANY, 2, [(0, "news_1"), (1, "news_2")]),
    ("okay", AFTER, ANY, 3, [(0, "okay_1"), (1, "okay_2"), (0, "okay_3")]),
    ("what", AFTER, ANY, 3, [(0, "what_1"), (1, "what_2")]),
    ("home", AFTER, ANY, 2, [(0, "home_1"), (1, "home_2")]),
    ("rescue", AFTER, ANY, 2, [(0, "rescue_1"), (1, "rescue_2")]),
    ("where", AFTER, ANY, 2, [(0, "where_1"), (1, "where_2")]),
    ("rubble", AFTER, ANY, 2, [(0, "rubble_1"), (1, "rubble_2")]),
]

# single calls by occasion (CityLifeVoices on the server picks one)
CALLS = {
    "startle": ["startle_1", "startle_2", "startle_3"],
    "flee": ["flee_1", "flee_2", "flee_3", "flee_4", "flee_5"],
    "help": ["help_1", "help_2", "help_3", "help_4"],
    "survivor": ["survivor_1", "survivor_2"],
}

# voice id -> (Piper voice, F0 factor, spectral warp, speed) ; f* are the male recordings re-pitched with WORLD
VOICES = {
    "m1": ("denis", 1.0, 1.0, 1.0),
    "m2": ("dmitri", 1.0, 1.0, 1.0),
    "f1": ("denis", 1.72, 1.17, 0.97),
    "f2": ("dmitri", 1.62, 1.13, 0.98),
}


def text_for(line, female):
    """Spoken/subtitle Russian text of a line for a speaker ({male|female} resolved)."""
    import re
    ru = LINES[line][0]
    return re.sub(r"\{([^|}]*)\|([^}]*)\}", lambda m: m.group(2) if female else m.group(1), ru)


def gendered(line):
    return "{" in LINES[line][0]


def call_lines():
    return {l for ls in CALLS.values() for l in ls}

# subtitle translations (de, es, tr, zh) of the spoken Russian lines; es_* locales share the Spanish text
I18N = {
    "greet_1": ("Hallo! Lange nicht gesehen!", "¡Hola! ¡Cuánto tiempo sin verte!", "Merhaba! Seni görmeyeli yıllar oldu!", "你好！好久不见！"),
    "greet_2": ("Hallo! Immer nur Arbeit, du weißt ja.", "¡Hola! Todo es trabajo, ya sabes.", "Merhaba! Hep iş, biliyorsun.", "你好！总是忙工作，你懂的。"),
    "greet_3": ("Na, lass dich mal blicken. Bis bald!", "Bueno, no desaparezcas. ¡Nos vemos!", "Kaybolma ama. Görüşürüz!", "那别消失啊，回头见！"),
    "weather_1": ("Was für ein Wetter heute, was? Herrlich!", "¡Qué tiempo hace hoy! ¡Precioso!", "Bugün hava ne güzel, değil mi?", "今天天气真好，是吧？"),
    "weather_2": ("Ja, endlich Sonne. Gut zum Spazierengehen.", "Sí, por fin sol. Buen día para pasear.", "Evet, nihayet güneş. Yürüyüş için harika.", "是啊，终于出太阳了，适合散步。"),
    "market_1": ("Zum Markt? Es soll frisches Brot geben.", "¿Vas al mercado? Dicen que llegó pan fresco.", "Pazara mı? Taze ekmek gelmiş diyorlar.", "去市场吗？听说来了新鲜面包。"),
    "market_2": ("Genau dahin. Soll ich dir ein Brot mitbringen?", "Justo voy allí. ¿Te traigo una barra?", "Tam oraya gidiyorum. Sana da ekmek alayım mı?", "正要去。要我给你带一条吗？"),
    "market_3": ("Ja, bitte. Danke!", "Sí, por favor. ¡Gracias!", "Evet, lütfen. Teşekkürler!", "好的，麻烦了，谢谢！"),
    "heroes_1": ("Schon gehört? Über dem Platz ist wieder jemand geflogen.", "¿Te enteraste? Alguien volvió a volar sobre la plaza.", "Duydun mu? Meydanın üstünde yine biri uçmuş.", "听说了吗？又有人飞过广场了。"),
    "heroes_2": ("Oh ja. Hoffentlich geht diesmal nichts kaputt.", "Pues sí. Ojalá esta vez no rompan nada.", "Evet. İnşallah bu sefer bir yer yıkılmaz.", "是啊，希望这次别再搞破坏。"),
    "heroes_3": ("Frag nicht. Die Fenster vom letzten Mal werden noch ersetzt.", "Ni me lo digas. Aún cambian las ventanas de la última vez.", "Sorma. Geçen seferki camlar hâlâ değişiyor.", "别提了，上次的窗户还在换呢。"),
    "work_1": ("Wie ist die neue Arbeit?", "¿Qué tal el trabajo nuevo?", "Yeni iş nasıl?", "新工作怎么样？"),
    "work_2": ("Ich gewöhne mich dran. Der Chef ist streng, aber fair.", "Me voy acostumbrando. El jefe es estricto pero justo.", "Alışıyorum yavaş yavaş. Patron sert ama adil.", "慢慢习惯了。老板严格但公正。"),
    "work_3": ("Hauptsache, es gefällt dir.", "Lo importante es que te guste.", "Önemli olan sevmen.", "喜欢就好。"),
    "lunch_1": ("Gehen wir essen? Hier hat ein Café aufgemacht.", "¿Vamos a comer? Abrieron un café aquí cerca.", "Yemeğe gidelim mi? Yakında bir kafe açılmış.", "去吃午饭吗？附近新开了家咖啡馆。"),
    "lunch_2": ("Gern. Aber nicht lange, ich habe um zwei ein Treffen.", "Vale. Pero poco rato, tengo una reunión a las dos.", "Olur. Ama kısa, saat ikide toplantım var.", "行，不过别太久，我两点有个会。"),
    "way_1": ("Entschuldigung, wie komme ich zum Bahnhof?", "Disculpe, ¿cómo llego a la estación?", "Affedersiniz, istasyona nasıl giderim?", "请问，去车站怎么走？"),
    "way_2": ("Geradeaus bis zur Kreuzung, dann links. Fünf Minuten.", "Recto hasta el cruce y luego a la izquierda. Unos cinco minutos.", "Kavşağa kadar düz, sonra sola. Beş dakika.", "直走到路口，然后左转，大概五分钟。"),
    "way_3": ("Vielen Dank!", "¡Muchas gracias!", "Çok teşekkürler!", "太感谢了！"),
    "kids_1": ("Wie geht's den Kindern?", "¿Cómo están los niños?", "Çocuklar nasıl?", "孩子们怎么样？"),
    "kids_2": ("Sie wachsen! Der Jüngste ist dieses Jahr in die Schule gekommen.", "¡Creciendo! El pequeño empezó el colegio este año.", "Büyüyorlar! Küçük bu yıl okula başladı.", "长大了！小的今年上学了。"),
    "kids_3": ("Wow! Wie die Zeit vergeht.", "¡Vaya! Cómo pasa el tiempo.", "Vay! Zaman nasıl da geçiyor.", "哇！时间过得真快。"),
    "match_1": ("Wie fandest du das Spiel gestern?", "¿Qué te pareció el partido de ayer?", "Dünkü maç nasıldı sence?", "你觉得昨天的比赛怎么样？"),
    "match_2": ("Die letzte Minute war ein Wunder!", "¡El último minuto fue un milagro!", "Son dakika tam bir mucizeydi!", "最后一分钟简直是奇迹！"),
    "match_3": ("Der ganze Hof hat geschrien. Ich bin fast vom Sofa gefallen!", "Todo el barrio gritaba. ¡Casi me caigo del sofá!", "Bütün mahalle bağırıyordu. Neredeyse kanepeden düşüyordum!", "整个院子都在喊，我差点从沙发上摔下来！"),
    "tower_1": ("Schau, an dem Turm steht wieder ein Gerüst.", "Mira, otra vez pusieron andamios en esa torre.", "Bak, o kuleye yine iskele kurmuşlar.", "看，那座塔又搭上脚手架了。"),
    "tower_2": ("Reparaturen wohl. Die werden nie fertig.", "Reparaciones, supongo. Nunca terminan.", "Tamirat herhalde. Bir türlü bitiremiyorlar.", "大概在维修吧，总也修不完。"),
    "phone_1": ("Sag mal, wo ist mein Handy? Ich finde es nirgends.", "Oye, ¿dónde está mi móvil? No lo encuentro.", "Baksana, telefonum nerede? Hiçbir yerde bulamıyorum.", "哎，我手机呢？到处找不到。"),
    "phone_2": ("Das hast du in der Hand.", "Lo tienes en la mano.", "Elinde.", "就在你手里。"),
    "phone_3": ("Oh... Stimmt. Danke.", "Ay... Es verdad. Gracias.", "Ay... Doğru. Sağ ol.", "哦……对哦，谢谢。"),
    "prices_1": ("Die Preise sind schon wieder gestiegen. Bald reicht es nicht mal fürs Brot.", "Otra vez subieron los precios. Pronto no alcanzará ni para pan.", "Fiyatlar yine artmış. Yakında ekmeğe bile yetmeyecek.", "又涨价了，很快连面包都买不起了。"),
    "prices_2": ("Frag nicht. Dafür soll es mehr Lohn geben.", "Ni me lo digas. Al menos prometen subir los sueldos.", "Sorma. Hiç değilse maaşlara zam sözü var.", "别提了。不过说要涨工资了。"),
    "tourist_1": ("Was für eine schöne Stadt! Ich bin zum ersten Mal hier.", "¡Qué ciudad tan bonita! Es mi primera vez aquí.", "Ne güzel bir şehir! İlk kez geliyorum.", "多美的城市啊！我第一次来。"),
    "tourist_2": ("Willkommen! Schauen Sie unbedingt auf dem Hauptplatz vorbei.", "¡Bienvenido! No deje de visitar la plaza principal.", "Hoş geldiniz! Ana meydana mutlaka uğrayın.", "欢迎！一定要去中心广场看看。"),
    "morning_1": ("Guten Morgen! Du bist heute früh dran.", "¡Buenos días! Hoy madrugaste.", "Günaydın! Bugün erkencisin.", "早上好！你今天真早。"),
    "morning_2": ("Morgen! Ich will alles vor dem Mittag schaffen.", "¡Buenos días! Quiero acabarlo todo antes de comer.", "Günaydın! Öğleden önce her şeyi bitirmek istiyorum.", "早！我想午饭前把事情都办完。"),
    "evening_1": ("Es ist schon spät, Zeit nach Hause zu gehen.", "Ya es tarde, hora de irse a casa.", "Geç oldu, eve dönme vakti.", "很晚了，该回家了。"),
    "evening_2": ("Ja, morgen früh raus. Gute Nacht!", "Sí, mañana madrugo. ¡Buenas noches!", "Evet, yarın erken kalkacağım. İyi geceler!", "是啊，明天要早起。晚安！"),
    "rain_1": ("Schon wieder dieser Regen. Wir werden klatschnass.", "Otra vez esta lluvia. Nos vamos a empapar.", "Yine bu yağmur. Sırılsıklam olacağız.", "又下雨了，要淋透了。"),
    "rain_2": ("Schnell unter das Vordach!", "¡Corre, bajo el toldo!", "Hadi, saçağın altına!", "快到雨棚下面去！"),
    "hear_1": ("Hörst du das? Es grollt schon wieder.", "¿Oyes eso? Otra vez retumba.", "Duyuyor musun? Yine gümbürdüyor.", "你听到了吗？又在轰隆响。"),
    "hear_2": ("Irgendwo weit weg. Halten wir lieber Abstand.", "Muy lejos. Mejor mantengamos la distancia.", "Uzaklarda bir yerde. En iyisi uzak duralım.", "在远处。我们还是离远点吧。"),
    "uneasy_1": ("Mir ist hier nicht wohl.", "Aquí no me siento seguro.", "Burada içim rahat değil.", "我在这里心里不踏实。"),
    "uneasy_2": ("Mir auch nicht. Gehen wir.", "Yo tampoco. Vámonos.", "Benim de. Gidelim buradan.", "我也是。我们走吧。"),
    "news_1": ("Im Radio hieß es, man soll drinnen bleiben.", "En la radio dijeron que no saliéramos de casa.", "Radyoda evden çıkmayın dediler.", "广播里说不要出门。"),
    "news_2": ("Und ich wollte nur Brot holen...", "Y yo que solo salí a por pan...", "Ben de sadece ekmek almaya çıkmıştım...", "我只是出来买个面包……"),
    "okay_1": ("Wie geht's dir? Alles in Ordnung?", "¿Cómo estás? ¿Todo bien?", "Nasılsın? İyi misin?", "你怎么样？没事吧？"),
    "okay_2": ("Ich glaube schon... Nur meine Hände zittern.", "Creo que sí... Solo me tiemblan las manos.", "Sanırım... Sadece ellerim titriyor.", "应该没事……就是手在发抖。"),
    "okay_3": ("Halte durch. Hauptsache, wir leben.", "Aguanta. Lo importante es que estamos vivos.", "Dayan. Önemli olan hayatta olmamız.", "挺住。活着就好。"),
    "what_1": ("Was um Himmels willen war das?", "¿Qué demonios fue eso?", "O da neydi öyle?", "那到底是什么？"),
    "what_2": ("Keine Ahnung. So etwas habe ich noch nie gesehen.", "No lo sé. Nunca había visto algo así.", "Bilmiyorum. Hiç böyle bir şey görmedim.", "不知道，我从没见过这种事。"),
    "home_1": ("Da war doch mein Zuhause...", "Ahí estaba mi casa...", "Evim tam oradaydı...", "我家就在那儿……"),
    "home_2": ("Komm. Hier zu bleiben ist gefährlich.", "Vamos. Quedarse aquí es peligroso.", "Gel. Burada kalmak tehlikeli.", "走吧，待在这里很危险。"),
    "rescue_1": ("Hat jemand den Rettungsdienst gerufen?", "¿Alguien llamó a los servicios de rescate?", "Kurtarma ekibini arayan oldu mu?", "有人叫救援了吗？"),
    "rescue_2": ("Ich rufe und rufe, aber es ist immer besetzt.", "Llamo y llamo, pero siempre comunica.", "Arıyorum arıyorum, hat hep meşgul.", "我一直在打，可总是占线。"),
    "where_1": ("Wohin sind alle gerannt?", "¿Adónde corrió todo el mundo?", "Herkes nereye koştu?", "大家都跑哪儿去了？"),
    "where_2": ("Zum Platz. Dort ist es wohl sicherer.", "A la plaza. Allí parece más seguro.", "Meydana. Orası daha güvenli galiba.", "去广场了，那边好像安全些。"),
    "rubble_1": ("Hilf mir, die Trümmer wegzuräumen, da ist jemand drin!", "¡Ayúdame a quitar los escombros, hay alguien dentro!", "Enkazı kaldırmama yardım et, içeride biri var!", "帮我清开废墟，里面有人！"),
    "rubble_2": ("Ich komme! Vorsicht, alles wackelt.", "¡Voy! Cuidado, todo se tambalea.", "Geliyorum! Dikkat, her şey sallanıyor.", "来了！小心，到处都在晃。"),
    "startle_1": ("Was ist das?!", "¡¿Qué es eso?!", "Bu ne?!", "那是什么？！"),
    "startle_2": ("Seht! Da oben!", "¡Mirad! ¡Allí arriba!", "Bakın! Orada, yukarıda!", "看！在上面！"),
    "startle_3": ("Ach du meine Güte...", "Ay, Dios mío...", "Aman Allahım...", "天哪……"),
    "flee_1": ("Lauft!", "¡Corred!", "Kaçın!", "快跑！"),
    "flee_2": ("Alle in Deckung!", "¡Todos a cubierto!", "Herkes siper alsın!", "大家快找掩护！"),
    "flee_3": ("Hierher, schnell!", "¡Por aquí, rápido!", "Buraya, çabuk!", "这边，快！"),
    "flee_4": ("Vorsicht!", "¡Cuidado!", "Dikkat!", "小心！"),
    "flee_5": ("Nicht drängeln!", "¡No empujéis!", "İtmeyin!", "别挤！"),
    "help_1": ("Hilfe!", "¡Socorro!", "İmdat!", "救命！"),
    "help_2": ("Hilft mir denn keiner?!", "¡Que alguien me ayude!", "Biri yardım etsin!", "谁来帮帮我！"),
    "help_3": ("Hilfe, ich kann nicht aufstehen!", "¡Socorro, no puedo levantarme!", "İmdat, kalkamıyorum!", "救命，我起不来了！"),
    "help_4": ("Mama! Wo bist du?", "¡Mamá! ¿Dónde estás?", "Anne! Neredesin?", "妈妈！你在哪儿？"),
    "survivor_1": ("Ist da noch jemand am Leben?", "¿Hay alguien vivo?", "Yaşayan var mı?", "还有人活着吗？"),
    "survivor_2": ("Hierher! Hier ist ein Verletzter!", "¡Aquí! ¡Hay un herido!", "Buraya! Burada yaralı var!", "这边！这里有伤员！"),
}

# subtitles of the city sounds that are not speech: key -> (en, ru, de, es, tr, zh)
SOUND_SUBTITLES = {
    "crowd": ("Crowd murmurs", "Гул толпы", "Menschenmenge murmelt", "Murmullo de la multitud", "Kalabalık uğulduyor", "人群低语"),
    "steps": ("Footsteps of passers-by", "Шаги прохожих", "Schritte von Passanten", "Pasos de transeúntes", "Yayaların ayak sesleri", "行人的脚步声"),
    "running": ("People run", "Люди бегут", "Menschen rennen", "La gente corre", "İnsanlar koşuyor", "人们在奔跑"),
    "street": ("Street noise", "Шум улицы", "Straßenlärm", "Ruido de la calle", "Sokak gürültüsü", "街道喧闹"),
    "night": ("Night city", "Ночной город", "Nächtliche Stadt", "Ciudad nocturna", "Gece şehri", "夜晚的城市"),
    "wind": ("Wind between buildings", "Ветер между домами", "Wind zwischen den Häusern", "Viento entre los edificios", "Binalar arasında rüzgâr", "楼宇间的风声"),
    "panic": ("Panic in the streets", "Паника на улицах", "Panik auf den Straßen", "Pánico en las calles", "Sokaklarda panik", "街头恐慌"),
    "hush": ("Stunned silence", "Оцепеневшая тишина", "Betroffene Stille", "Silencio atónito", "Donuk sessizlik", "惊魂未定的寂静"),
    "dog": ("Dog barks in the distance", "Вдалеке лает собака", "Hund bellt in der Ferne", "Un perro ladra a lo lejos", "Uzakta bir köpek havlıyor", "远处狗吠"),
    "bike_bell": ("Bicycle bell", "Велосипедный звонок", "Fahrradklingel", "Timbre de bicicleta", "Bisiklet zili", "自行车铃声"),
    "pigeons": ("Pigeons take off", "Взлетают голуби", "Tauben fliegen auf", "Las palomas alzan el vuelo", "Güvercinler havalanıyor", "鸽子飞起"),
    "laugh": ("People laugh", "Люди смеются", "Leute lachen", "La gente ríe", "İnsanlar gülüyor", "人们在笑"),
    "bells": ("Distant bells", "Далёкий колокольный звон", "Ferne Glocken", "Campanas lejanas", "Uzaktan çan sesleri", "远处的钟声"),
    "scream": ("Citizen screams", "Кричит житель", "Bürger schreit", "Un ciudadano grita", "Bir vatandaş çığlık atıyor", "市民尖叫"),
    "cough": ("Someone coughs", "Кто-то кашляет", "Jemand hustet", "Alguien tose", "Biri öksürüyor", "有人咳嗽"),
    "sob": ("Someone sobs", "Кто-то плачет", "Jemand schluchzt", "Alguien solloza", "Biri hıçkıra hıçkıra ağlıyor", "有人在抽泣"),
}

# /city life (status of the city's mood and voices), columns as SOUND_SUBTITLES: en, ru, de, es, tr, zh
COMMAND_TEXT = {
    "viltrumitecore.city.help.life": (
        "- the mood of the streets here and what the citizens said",
        "- настроение улиц здесь и что говорили жители",
        "- die Stimmung der Straßen hier und was die Bewohner sagten",
        "- el ánimo de las calles aquí y lo que dijeron los vecinos",
        "- buradaki sokakların havası ve sakinlerin söyledikleri",
        "- 此处街道的气氛与市民说过的话"),
    "viltrumitecore.city.life.status": (
        "Streets here: %s (alarm %s, trauma %s)",
        "Настроение улиц здесь: %s (тревога %s, пережитое %s)",
        "Straßen hier: %s (Alarm %s, Schock %s)",
        "Calles aquí: %s (alarma %s, trauma %s)",
        "Buradaki sokaklar: %s (alarm %s, travma %s)",
        "此处街道：%s（警觉 %s，创伤 %s）"),
    "viltrumitecore.city.life.mood.calm": ("calm", "спокойно", "ruhig", "tranquilas", "sakin", "平静"),
    "viltrumitecore.city.life.mood.wary": ("wary", "настороженно", "beunruhigt", "inquietas", "tedirgin", "不安"),
    "viltrumitecore.city.life.mood.panic": ("panic", "паника", "Panik", "pánico", "panik", "恐慌"),
    "viltrumitecore.city.life.mood.after": ("aftermath", "после беды", "nach dem Unglück", "tras la catástrofe", "felaket sonrası", "灾后"),
    "viltrumitecore.city.life.talks": (
        "Chats: %s voiced, %s silent, %s going on now; %s lines spoken",
        "Разговоры: озвучено %s, молча %s, идёт сейчас %s; сказано реплик: %s",
        "Gespräche: %s vertont, %s stumm, %s laufen gerade; %s Sätze gesprochen",
        "Charlas: %s con voz, %s en silencio, %s en curso; %s frases dichas",
        "Sohbetler: %s sesli, %s sessiz, şu an %s; %s replik",
        "对话：有声 %s，无声 %s，进行中 %s；已说台词 %s"),
    "viltrumitecore.city.life.calls": (
        "Calls %s, screams %s, coughs %s, sobs %s (held back by the limits: %s)",
        "Возгласы: %s, крики: %s, кашель: %s, плач: %s (придержано ограничениями: %s)",
        "Rufe %s, Schreie %s, Husten %s, Schluchzen %s (von den Grenzen zurückgehalten: %s)",
        "Gritos de aviso %s, alaridos %s, toses %s, sollozos %s (retenidos por los límites: %s)",
        "Seslenme %s, çığlık %s, öksürük %s, hıçkırık %s (sınırlarca tutulan: %s)",
        "呼喊 %s，尖叫 %s，咳嗽 %s，抽泣 %s（被上限拦下：%s）"),
    "viltrumitecore.city.life.reset": (
        "The mood of the streets was reset: the city is calm again",
        "Настроение улиц сброшено: город снова спокоен",
        "Die Stimmung der Straßen wurde zurückgesetzt: die Stadt ist wieder ruhig",
        "Se restableció el ánimo de las calles: la ciudad vuelve a estar tranquila",
        "Sokakların havası sıfırlandı: şehir yeniden sakin",
        "街道气氛已重置：城市恢复平静"),
}
