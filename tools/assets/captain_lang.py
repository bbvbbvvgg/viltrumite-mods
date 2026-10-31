"""Captain America (2012) strings for every locale -> viltrumitecore/src/main/lang-overlays/captain/<locale>.json.
Run: python3 tools/assets/captain_lang.py"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore', 'src', 'main', 'lang-overlays', 'captain')
ABIL = ['ricochet', 'shield_rush', 'vibranium_slam', 'tactical_roll', 'assemble']
CAP_SFX = ['shield_swing', 'shield_hit', 'fist_swing', 'fist_hit', 'kick', 'finisher', 'counter', 'guard_up', 'block', 'parry',
           'throw', 'shield_fly', 'ricochet', 'catch', 'recall', 'rush', 'rush_hit', 'slam_leap', 'slam_impact', 'slam_empowered',
           'roll', 'landing', 'refuse', 'charged', 'assemble']
AV_SFX = ['quinjet_flyby', 'quinjet_ramp', 'ironman_thrust', 'ironman_land', 'ironman_repulsor', 'ironman_missile', 'explosion',
          'ironman_unibeam', 'ironman_faceplate', 'hulk_roar', 'hulk_smash', 'hulk_punch', 'hulk_chest', 'thor_thunder',
          'thor_hammer_swing', 'thor_hammer_hit', 'thor_hammer_spin', 'widow_shot', 'widow_bite', 'widow_strike', 'acrobat',
          'hawkeye_draw', 'hawkeye_release', 'hawkeye_arrow_hit', 'shock', 'heavy_land', 'depart']
KINDS = ['iron_man', 'thor', 'hulk', 'black_widow', 'hawkeye']

L = {}
L['en_us'] = dict(
    hero='Captain America', become='Become Captain America', speaker='Captain America',
    assemble_line='Avengers, assemble!', all_day='I can do this all day.',
    names=['Ricochet', 'Shield Rush', 'Vibranium Slam', 'Tactical Roll', 'Avengers, Assemble!'],
    descs=[
        'Throw the shield at the target in your sights: it ricochets between up to 5 enemies (8 with 50+ kinetic charge, '
        'spends 50) for 9 damage, -15% per bounce, and flies back to you. Hold to aim a bank shot off walls (up to 4 bounces). '
        'Press again in flight to recall it, hitting enemies on the way. Catch it during a combo for a Return Strike.',
        'Charge up to 9 blocks behind the shield: enemies on the path take 6 and are swept aside; the first big enemy or a wall '
        'stops you with a 10-damage bash and a 1.25 s stun. Your next attack continues the combo from the uppercut. '
        'Without the shield: a shoulder charge for 8.',
        'Leap up and forward and drive the shield into the ground: a radius-4 wave for 10 damage that launches enemies. '
        'With 50+ kinetic charge (spends 50): radius 6, 14 damage and a white-blue star flash. Needs the shield in hand.',
        'Roll 4.5 blocks the way you move (or back), untouchable for a moment. Attack right after for a sweeping kick. '
        'Rolling toward the flying shield catches it sooner.',
        'Ultimate. Raise the shield and call the team: the Quinjet drops Black Widow and Hawkeye, Iron Man lands from the sky, '
        'Thor arrives in lightning and the Hulk leaps in. The six of you stand in a circle, then the Avengers fight at your side '
        'for 60 seconds and pull off joint moves: Widow vaults Hawkeye into the air, Thor overcharges Iron Man\'s unibeam, '
        'and Thor strikes your raised shield for a shockwave. Cooldown 5 minutes.'],
    shield='Captain America\'s Shield', avenger='Avenger', projectile='Avenger Projectile', quinjet='Quinjet',
    kinds=['Iron Man', 'Thor', 'Hulk', 'Black Widow', 'Hawkeye'],
    cap=['Shield swings', 'Shield strikes', 'Punch whooshes', 'Punch lands', 'Kick lands', 'Shield crashes down',
         'Captain counters', 'Shield raised', 'Shield blocks', 'Shield parries', 'Shield thrown', 'Shield whirs',
         'Shield ricochets', 'Shield caught', 'Shield returns', 'Captain charges', 'Shield bash', 'Captain leaps',
         'Shield slams the ground', 'Vibranium shockwave', 'Captain rolls', 'Captain lands', 'Captain: not now',
         'Vibranium charged', 'Rally fanfare'],
    av=['Quinjet roars overhead', 'Quinjet ramp opens', 'Repulsor thrusters', 'Iron Man lands', 'Repulsor blast',
        'Missile launched', 'Explosion', 'Unibeam fires', 'Faceplate lifts', 'Hulk roars', 'Hulk smashes', 'Hulk punches',
        'Hulk beats his chest', 'Thunder strikes', 'Mjölnir swings', 'Mjölnir strikes', 'Mjölnir spins', 'Pistol shot',
        'Widow\'s Bite crackles', 'Black Widow strikes', 'Acrobatic flip', 'Bowstring drawn', 'Arrow loosed', 'Arrow hits',
        'Electric shock', 'Heavy landing', 'Avenger departs'])

L['ru_ru'] = dict(
    hero='Капитан Америка', become='Стать Капитаном Америкой', speaker='Капитан Америка',
    assemble_line='Мстители, общий сбор!', all_day='Я могу так весь день.',
    names=['Рикошет', 'Таран щитом', 'Удар вибраниума', 'Тактический перекат', 'Мстители, общий сбор!'],
    descs=[
        'Бросок щита в цель под прицелом: щит рикошетит между 5 врагами (8 при кинетическом заряде от 50, тратит 50), '
        '9 урона, −15 % за каждый отскок, и возвращается. Удержание — прицел броска с отскоками от стен (до 4). '
        'Повторное нажатие в полёте — отзыв: щит летит назад и задевает врагов. Поймай щит во время комбо — «Удар с возврата».',
        'Рывок до 9 блоков за щитом: враги на пути получают 6 урона и отлетают в сторону; первый крупный враг или стена '
        'останавливает рывок ударом на 10 и оглушением на 1,25 с. Следующий удар продолжает комбо с апперкота. '
        'Без щита — таран плечом на 8.',
        'Прыжок вверх-вперёд и удар щитом в землю: волна радиусом 4, 10 урона, враги подлетают. '
        'При заряде от 50 (тратит 50): радиус 6, 14 урона и бело-синяя вспышка звезды. Нужен щит в руке.',
        'Перекат на 4,5 блока в сторону движения (или назад), короткая неуязвимость. Удар сразу после — подсечка. '
        'Перекат навстречу летящему щиту ловит его раньше.',
        'Ультимейт. Стив поднимает щит и зовёт команду: квинджет высаживает Чёрную вдову и Соколиного глаза, '
        'Железный человек приземляется с неба, Тор является в молнии, Халк прилетает прыжком. Шестеро встают в круг, '
        'затем Мстители 60 секунд сражаются рядом с тобой и проводят совместные приёмы: Вдова подбрасывает Соколиного глаза, '
        'Тор заряжает юнилуч Старка молнией, а Тор бьёт Мьёльниром в поднятый щит — ударная волна. Перезарядка 5 минут.'],
    shield='Щит Капитана Америки', avenger='Мститель', projectile='Снаряд Мстителя', quinjet='Квинджет',
    kinds=['Железный человек', 'Тор', 'Халк', 'Чёрная вдова', 'Соколиный глаз'],
    cap=['Взмах щитом', 'Удар щитом', 'Свист кулака', 'Удар кулаком', 'Удар ногой', 'Щит обрушивается сверху',
         'Капитан контратакует', 'Щит поднят', 'Щит блокирует', 'Щит парирует', 'Бросок щита', 'Щит со свистом летит',
         'Щит рикошетит', 'Щит пойман', 'Щит возвращается', 'Капитан идёт на таран', 'Таран щитом', 'Капитан прыгает',
         'Щит бьёт о землю', 'Ударная волна вибраниума', 'Капитан делает перекат', 'Капитан приземляется',
         'Капитан: не сейчас', 'Вибраниум заряжен', 'Фанфара сбора'],
    av=['Рёв квинджета над головой', 'Рампа квинджета открывается', 'Репульсорные двигатели', 'Железный человек приземляется',
        'Залп репульсора', 'Запуск ракеты', 'Взрыв', 'Выстрел юнилуча', 'Забрало поднимается', 'Халк ревёт', 'Халк крушит',
        'Удар Халка', 'Халк бьёт себя в грудь', 'Удар грома', 'Взмах Мьёльниром', 'Удар Мьёльнира', 'Мьёльнир вращается',
        'Выстрел пистолета', 'Треск «Укуса вдовы»', 'Удар Чёрной вдовы', 'Акробатический переворот', 'Натяжение тетивы',
        'Выстрел из лука', 'Попадание стрелы', 'Электрический разряд', 'Тяжёлое приземление', 'Мститель улетает'])

L['de_de'] = dict(
    hero='Captain America', become='Captain America werden', speaker='Captain America',
    assemble_line='Avengers, sammeln!', all_day='Das kann ich den ganzen Tag.',
    names=['Querschläger', 'Schildansturm', 'Vibranium-Schlag', 'Taktische Rolle', 'Avengers, sammeln!'],
    descs=[
        'Wirf den Schild auf das Ziel im Visier: Er prallt zwischen bis zu 5 Gegnern ab (8 ab 50 kinetischer Ladung, '
        'verbraucht 50), 9 Schaden, −15 % pro Abpraller, und fliegt zurück. Halten zielt einen Bandenwurf über Wände '
        '(bis zu 4 Abpraller). Erneut drücken ruft ihn zurück und trifft Gegner auf dem Weg. Fang ihn im Combo für einen Rückkehrschlag.',
        'Stürme bis zu 9 Blöcke hinter dem Schild vor: Gegner auf dem Weg erleiden 6 und werden beiseitegestoßen; der erste '
        'große Gegner oder eine Wand stoppt dich mit 10 Schaden und 1,25 s Betäubung. Der nächste Angriff setzt den Combo '
        'mit dem Aufwärtshaken fort. Ohne Schild: Schulterstoß für 8.',
        'Spring hoch und nach vorn und ramme den Schild in den Boden: Welle mit Radius 4, 10 Schaden, schleudert Gegner hoch. '
        'Ab 50 Ladung (verbraucht 50): Radius 6, 14 Schaden und ein weiß-blauer Sternblitz. Nur mit Schild in der Hand.',
        'Rolle 4,5 Blöcke in Laufrichtung (oder zurück), kurz unverwundbar. Direkt danach angreifen = Fegetritt. '
        'Eine Rolle zum fliegenden Schild fängt ihn früher.',
        'Ultimate. Heb den Schild und ruf das Team: Der Quinjet setzt Black Widow und Hawkeye ab, Iron Man landet aus dem Himmel, '
        'Thor erscheint im Blitz und Hulk springt herbei. Zu sechst bildet ihr einen Kreis, dann kämpfen die Avengers '
        '60 Sekunden an deiner Seite, mit Teamangriffen: Widow katapultiert Hawkeye in die Luft, Thor lädt Iron Mans '
        'Unibeam auf, und Thor schlägt auf deinen erhobenen Schild – eine Schockwelle. Abklingzeit 5 Minuten.'],
    shield='Captain Americas Schild', avenger='Avenger', projectile='Avenger-Geschoss', quinjet='Quinjet',
    kinds=['Iron Man', 'Thor', 'Hulk', 'Black Widow', 'Hawkeye'],
    cap=['Schild schwingt', 'Schild trifft', 'Faust zischt', 'Faust trifft', 'Tritt trifft', 'Schild kracht herab',
         'Captain kontert', 'Schild erhoben', 'Schild blockt', 'Schild pariert', 'Schild geworfen', 'Schild surrt',
         'Schild prallt ab', 'Schild gefangen', 'Schild kehrt zurück', 'Captain stürmt vor', 'Schildstoß', 'Captain springt',
         'Schild schlägt auf den Boden', 'Vibranium-Schockwelle', 'Captain rollt', 'Captain landet', 'Captain: nicht jetzt',
         'Vibranium geladen', 'Sammelfanfare'],
    av=['Quinjet dröhnt über dir', 'Quinjet-Rampe öffnet sich', 'Repulsor-Triebwerke', 'Iron Man landet', 'Repulsor-Stoß',
        'Rakete gestartet', 'Explosion', 'Unibeam feuert', 'Gesichtsplatte öffnet sich', 'Hulk brüllt', 'Hulk zertrümmert',
        'Hulk schlägt zu', 'Hulk trommelt auf die Brust', 'Donnerschlag', 'Mjölnir schwingt', 'Mjölnir trifft', 'Mjölnir rotiert',
        'Pistolenschuss', 'Widow\'s Bite knistert', 'Black Widow schlägt zu', 'Akrobatischer Salto', 'Bogensehne gespannt',
        'Pfeil abgeschossen', 'Pfeil trifft', 'Stromschlag', 'Schwere Landung', 'Avenger bricht auf'])

ES = dict(
    hero='Capitán América', become='Convertirse en el Capitán América', speaker='Capitán América',
    assemble_line='¡Vengadores, reuníos!', all_day='Puedo hacer esto todo el día.',
    names=['Rebote', 'Embestida con escudo', 'Golpe de vibranio', 'Voltereta táctica', '¡Vengadores, reuníos!'],
    descs=[
        'Lanza el escudo al objetivo en la mira: rebota entre hasta 5 enemigos (8 con 50+ de carga cinética, gasta 50), '
        '9 de daño, −15 % por rebote, y vuelve a ti. Mantén para apuntar un tiro con rebotes en paredes (hasta 4). '
        'Pulsa otra vez en vuelo para recuperarlo y golpear enemigos en el camino. Atrápalo en un combo para un Golpe de retorno.',
        'Carga hasta 9 bloques tras el escudo: los enemigos en el camino reciben 6 y salen despedidos; el primer enemigo grande '
        'o una pared te detiene con un golpe de 10 y aturdimiento de 1,25 s. El siguiente ataque sigue el combo desde el gancho. '
        'Sin escudo: carga con el hombro de 8.',
        'Salta hacia arriba y adelante y clava el escudo en el suelo: onda de radio 4, 10 de daño, lanza a los enemigos. '
        'Con 50+ de carga (gasta 50): radio 6, 14 de daño y un destello estelar blanco y azul. Requiere el escudo en la mano.',
        'Rueda 4,5 bloques hacia donde te mueves (o atrás), invulnerable un instante. Ataca justo después para una barrida. '
        'Rodar hacia el escudo en vuelo lo atrapa antes.',
        'Definitiva. Alza el escudo y llama al equipo: el Quinjet deja a Viuda Negra y Ojo de Halcón, Iron Man aterriza desde '
        'el cielo, Thor llega en un rayo y Hulk cae de un salto. Los seis forman un círculo y luego los Vengadores luchan a tu '
        'lado 60 segundos con ataques en equipo: la Viuda lanza a Ojo de Halcón por los aires, Thor sobrecarga el unirrayo '
        'de Iron Man y Thor golpea tu escudo alzado para una onda de choque. Recarga 5 minutos.'],
    shield='Escudo del Capitán América', avenger='Vengador', projectile='Proyectil de Vengador', quinjet='Quinjet',
    kinds=['Iron Man', 'Thor', 'Hulk', 'Viuda Negra', 'Ojo de Halcón'],
    cap=['El escudo silba', 'Golpe de escudo', 'Silba un puñetazo', 'Puñetazo', 'Patada', 'El escudo cae con fuerza',
         'El Capitán contraataca', 'Escudo en alto', 'El escudo bloquea', 'El escudo desvía', 'Escudo lanzado',
         'El escudo zumba', 'El escudo rebota', 'Escudo atrapado', 'El escudo regresa', 'El Capitán embiste',
         'Embestida con escudo', 'El Capitán salta', 'El escudo golpea el suelo', 'Onda de choque de vibranio',
         'El Capitán rueda', 'El Capitán aterriza', 'Capitán: ahora no', 'Vibranio cargado', 'Fanfarria de reunión'],
    av=['Rugido del Quinjet', 'Se abre la rampa del Quinjet', 'Propulsores repulsores', 'Iron Man aterriza',
        'Disparo repulsor', 'Misil lanzado', 'Explosión', 'Dispara el unirrayo', 'Se alza la máscara', 'Hulk ruge',
        'Hulk aplasta', 'Puñetazo de Hulk', 'Hulk se golpea el pecho', 'Cae un trueno', 'Mjölnir gira en el aire',
        'Golpe de Mjölnir', 'Mjölnir da vueltas', 'Disparo de pistola', 'Chisporrotea la Mordida de la Viuda',
        'Viuda Negra golpea', 'Voltereta acrobática', 'Se tensa la cuerda del arco', 'Flecha disparada', 'Impacto de flecha',
        'Descarga eléctrica', 'Aterrizaje pesado', 'Un Vengador se marcha'])
for loc in ['es_es', 'es_ar', 'es_cl', 'es_ec', 'es_mx', 'es_uy', 'es_ve']:
    L[loc] = dict(ES)
# Latin American dubs say "¡Vengadores, unidos!"
for loc in ['es_ar', 'es_cl', 'es_ec', 'es_mx', 'es_uy', 'es_ve']:
    L[loc] = dict(ES, assemble_line='¡Vengadores, unidos!',
                  names=ES['names'][:4] + ['¡Vengadores, unidos!'],
                  kinds=['Iron Man', 'Thor', 'Hulk', 'Viuda Negra', 'Ojo de Halcón'])

L['tr_tr'] = dict(
    hero='Kaptan Amerika', become='Kaptan Amerika ol', speaker='Kaptan Amerika',
    assemble_line='Yenilmezler, toplanın!', all_day='Bunu bütün gün yapabilirim.',
    names=['Seken Kalkan', 'Kalkan Hücumu', 'Vibranyum Darbesi', 'Taktik Yuvarlanma', 'Yenilmezler, Toplanın!'],
    descs=[
        'Kalkanı nişangâhtaki hedefe fırlat: 5 düşman arasında seker (50+ kinetik yükte 8, 50 harcar), 9 hasar, her sekişte '
        '−%15, sonra geri döner. Basılı tut: duvarlardan sekme yolu nişanla (en fazla 4). Uçarken tekrar bas: geri çağır, '
        'yoldaki düşmanlara çarpar. Kombo sırasında yakala: Dönüş Darbesi.',
        'Kalkanın arkasında 9 bloka kadar hücum et: yoldaki düşmanlar 6 hasar alıp savrulur; ilk büyük düşman ya da duvar seni '
        '10 hasarlık darbe ve 1,25 sn sersemletmeyle durdurur. Sonraki saldırı komboya aparkattan devam eder. Kalkansız: 8 hasarlık omuz hücumu.',
        'Yukarı-ileri sıçra ve kalkanı yere vur: 4 yarıçaplı dalga, 10 hasar, düşmanları havaya atar. 50+ yükte (50 harcar): '
        'yarıçap 6, 14 hasar ve beyaz-mavi yıldız parlaması. Kalkan elde olmalı.',
        'Hareket yönüne (ya da geriye) 4,5 blok yuvarlan, kısa süre dokunulmaz ol. Hemen ardından saldır: süpürme tekmesi. '
        'Uçan kalkana doğru yuvarlanmak onu daha erken yakalar.',
        'Nihai. Kalkanı kaldır ve takımı çağır: Quinjet Kara Dul ve Şahingöz\'ü bırakır, Demir Adam gökten iner, Thor yıldırımla '
        'gelir, Hulk sıçrayarak düşer. Altınız bir çember kurar, ardından Yenilmezler 60 saniye yanında savaşır ve ortak hamleler yapar: '
        'Dul Kadın Şahin Göz\'ü havaya fırlatır, Thor Iron Man\'in tek ışınını aşırı yükler, Thor kaldırdığın kalkana vurup '
        'şok dalgası yaratır. Bekleme 5 dakika.'],
    shield='Kaptan Amerika\'nın Kalkanı', avenger='Yenilmez', projectile='Yenilmez Mermisi', quinjet='Quinjet',
    kinds=['Demir Adam', 'Thor', 'Hulk', 'Kara Dul', 'Şahingöz'],
    cap=['Kalkan savrulur', 'Kalkan çarpar', 'Yumruk vınlar', 'Yumruk iner', 'Tekme iner', 'Kalkan yukarıdan iner',
         'Kaptan karşı saldırır', 'Kalkan kalkar', 'Kalkan bloklar', 'Kalkan savuşturur', 'Kalkan fırlatıldı', 'Kalkan vızıldar',
         'Kalkan seker', 'Kalkan yakalandı', 'Kalkan geri döner', 'Kaptan hücum eder', 'Kalkan darbesi', 'Kaptan sıçrar',
         'Kalkan yere çarpar', 'Vibranyum şok dalgası', 'Kaptan yuvarlanır', 'Kaptan iner', 'Kaptan: şimdi değil',
         'Vibranyum yüklendi', 'Toplanma fanfarı'],
    av=['Quinjet gürler', 'Quinjet rampası açılır', 'İtici motorlar', 'Demir Adam iner', 'İtici atış', 'Füze fırlatıldı',
        'Patlama', 'Tek ışın ateşlenir', 'Yüz plakası kalkar', 'Hulk kükrer', 'Hulk ezer', 'Hulk yumruk atar',
        'Hulk göğsüne vurur', 'Gök gürler', 'Mjölnir savrulur', 'Mjölnir çarpar', 'Mjölnir döner', 'Tabanca atışı',
        'Dul Isırığı çıtırdar', 'Kara Dul vurur', 'Akrobatik takla', 'Yay gerilir', 'Ok bırakıldı', 'Ok isabet eder',
        'Elektrik şoku', 'Ağır iniş', 'Yenilmez ayrılır'])

L['zh_cn'] = dict(
    hero='美国队长', become='成为美国队长', speaker='美国队长',
    assemble_line='复仇者，集结！', all_day='我能跟你耗一整天。',
    names=['盾牌弹射', '盾牌冲撞', '振金重击', '战术翻滚', '复仇者，集结！'],
    descs=[
        '把盾牌掷向准星中的目标：在最多 5 名敌人之间弹射（动能充能 50 以上时为 8 名，消耗 50），造成 9 点伤害，每次弹射 −15%，随后飞回。'
        '按住可瞄准借墙反弹的投掷（最多 4 次）。飞行中再按一次召回盾牌，沿途击中敌人。连招中接住盾牌可打出“回旋一击”。',
        '举盾冲锋最多 9 格：路上的敌人受到 6 点伤害并被撞开；第一个大型敌人或墙壁会让你停下，造成 10 点伤害并眩晕 1.25 秒。'
        '下一次攻击从上勾拳继续连招。无盾时：肩撞造成 8 点伤害。',
        '向前上方跃起，将盾牌砸向地面：半径 4 的冲击波，造成 10 点伤害并击飞敌人。动能 50 以上（消耗 50）：半径 6，14 点伤害，'
        '并爆出白蓝色星光。需要盾牌在手。',
        '朝移动方向（或向后）翻滚 4.5 格，短暂无敌。翻滚后立刻攻击可使出扫腿。朝飞行中的盾牌翻滚能更早接住它。',
        '终极技。举起盾牌召集队友：昆式战机放下黑寡妇和鹰眼，钢铁侠从天而降，雷神在闪电中现身，绿巨人一跃而至。'
        '六人站成一圈，随后复仇者在你身边战斗 60 秒，并施展合体技：黑寡妇把鹰眼抛向空中，雷神为钢铁侠的胸口光束充能，'
        '雷神用妙尔尼尔锤击你举起的盾牌，形成冲击波。冷却 5 分钟。'],
    shield='美国队长的盾牌', avenger='复仇者', projectile='复仇者投射物', quinjet='昆式战机',
    kinds=['钢铁侠', '雷神', '绿巨人', '黑寡妇', '鹰眼'],
    cap=['盾牌挥动', '盾牌击中', '拳风呼啸', '拳头命中', '踢击命中', '盾牌重重砸下', '队长反击', '举起盾牌', '盾牌格挡',
         '盾牌弹反', '盾牌掷出', '盾牌呼啸飞行', '盾牌弹射', '接住盾牌', '盾牌返回', '队长冲锋', '盾牌撞击', '队长跃起',
         '盾牌砸地', '振金冲击波', '队长翻滚', '队长落地', '队长：现在不行', '振金充能完毕', '集结号角'],
    av=['昆式战机轰鸣', '昆式战机舱门打开', '掌心推进器', '钢铁侠落地', '掌心炮射击', '导弹发射', '爆炸', '胸口光束发射',
        '面甲掀起', '绿巨人咆哮', '绿巨人猛砸', '绿巨人出拳', '绿巨人捶胸', '雷霆劈下', '雷神之锤挥动', '雷神之锤击中',
        '雷神之锤旋转', '手枪射击', '寡妇之刺噼啪作响', '黑寡妇出击', '杂技空翻', '拉开弓弦', '放箭', '箭矢命中', '电击',
        '重重落地', '复仇者离开'])


# rework §15: joint moves (lines in Thor's / Iron Man's voice, hint, playtest command)
TEAM = {
    'en_us': ["Hey, Cap, raise your shield! I'll charge the strike!", 'Charging at four hundred percent!',
              'Hold %s facing Thor to raise the shield', 'Unknown joint move: %s (vault, beam, shield)',
              '%s has no Avengers fighting right now', 'The Avengers will try "%s" at the next chance'],
    'ru_ru': ['Эй, кеп, подставь щит! Я заряжу атаку!', 'Заряд — четыреста процентов!',
              'Удерживай %s лицом к Тору, чтобы подставить щит', 'Неизвестный совместный приём: %s (vault, beam, shield)',
              'У %s сейчас нет сражающихся Мстителей', 'Мстители попробуют «%s» при первой возможности'],
    'de_de': ['Hey, Cap, heb deinen Schild! Ich lade den Schlag auf!', 'Ladung auf vierhundert Prozent!',
              'Halte %s in Richtung Thor, um den Schild zu heben', 'Unbekannter Teamangriff: %s (vault, beam, shield)',
              '%s hat gerade keine kämpfenden Avengers', 'Die Avengers versuchen „%s“ bei der nächsten Gelegenheit'],
    'es': ['¡Eh, Cap, alza el escudo! ¡Cargaré el golpe!', '¡Carga al cuatrocientos por ciento!',
           'Mantén %s mirando a Thor para alzar el escudo', 'Ataque en equipo desconocido: %s (vault, beam, shield)',
           '%s no tiene Vengadores luchando ahora', 'Los Vengadores intentarán «%s» en la próxima ocasión'],
    'tr_tr': ['Hey, Kaptan, kalkanını kaldır! Saldırıyı ben yükleyeceğim!', 'Yükleme yüzde dört yüz!',
              'Kalkanı kaldırmak için Thor\'a dönüp %s tuşunu basılı tut', 'Bilinmeyen ortak hamle: %s (vault, beam, shield)',
              '%s şu anda savaşan Yenilmezlere sahip değil', 'Yenilmezler ilk fırsatta "%s" hamlesini deneyecek'],
    'zh_cn': ['嘿，队长，举起盾牌！我来蓄力一击！', '充能百分之四百！',
              '面向雷神按住 %s 举起盾牌', '未知的合体技：%s（vault、beam、shield）',
              '%s 当前没有正在战斗的复仇者', '复仇者将在下一次机会尝试「%s」'],
}
ENTER = {'en_us': 'Captain Rogers, reporting for duty.', 'ru_ru': 'Капитан Роджерс прибыл для прохождения службы.',
         'de_de': 'Captain Rogers meldet sich zum Dienst.', 'es': 'Capitán Rogers, presentándose para el servicio.',
         'tr_tr': 'Kaptan Rogers göreve hazır.', 'zh_cn': '罗杰斯上尉，前来报到。'}
for loc in TEAM:
    TEAM[loc].append(ENTER[loc])
TEAM_KEYS = ['subtitle.viltrumitecore.captain.team_shield', 'subtitle.viltrumitecore.captain.team_beam',
             'hint.viltrumitecore.captain.team_shield', 'commands.viltrumitecore.hero.team.unknown',
             'commands.viltrumitecore.hero.team.no_fight', 'commands.viltrumitecore.hero.team.forced',
             'subtitle.viltrumitecore.captain.enter']
for loc in L:
    L[loc]['team'] = TEAM['es' if loc.startswith('es_') else loc]


def build(v):
    out = {
        'hero.viltrumitecore.captain': v['hero'],
        'gui.viltrumitecore.race_selection.become_captain': v['become'],
        'subtitle.viltrumitecore.speaker.captain': v['speaker'],
        'subtitle.viltrumitecore.captain.assemble': v['assemble_line'],
        'subtitle.viltrumitecore.captain.all_day': v['all_day'],
    }
    for a, n, d in zip(ABIL, v['names'], v['descs']):
        out[f'ability.viltrumitecore.captain_{a}.name'] = n
        out[f'ability.viltrumitecore.captain_{a}.desc'] = d
    out['entity.viltrumitecore.captain_shield'] = v['shield']
    out['entity.viltrumitecore.avenger'] = v['avenger']
    out['entity.viltrumitecore.avenger_projectile'] = v['projectile']
    out['entity.viltrumitecore.quinjet'] = v['quinjet']
    for k, n in zip(KINDS, v['kinds']):
        out[f'entity.viltrumitecore.avenger.{k}'] = n
    assert len(v['cap']) == len(CAP_SFX) and len(v['av']) == len(AV_SFX)
    for k, n in zip(CAP_SFX, v['cap']):
        out[f'subtitles.viltrumitecore.captain.{k}'] = n
    for k, n in zip(AV_SFX, v['av']):
        out[f'subtitles.viltrumitecore.avengers.{k}'] = n
    out.update(zip(TEAM_KEYS, v['team'], strict=True))
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    keys = None
    for loc, v in sorted(L.items()):
        data = build(v)
        keys = keys or set(data)
        assert set(data) == keys, loc
        with open(os.path.join(OUT, f'{loc}.json'), 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write('\n')
    print(len(L), 'locales,', len(keys), 'keys')


if __name__ == '__main__':
    main()
