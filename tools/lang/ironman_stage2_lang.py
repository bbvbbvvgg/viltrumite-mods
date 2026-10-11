#!/usr/bin/env python3
"""Iron Man Stage 2 strings for all 11 locales (idempotent: re-run overwrites these keys only)."""
import json, os

LANG = os.path.join(os.path.dirname(__file__), '../../viltrumitecore/src/main/resources/assets/viltrumitecore/lang')

SUBS = ['repulsor_shot', 'repulsor_charge', 'repulsor_volley', 'repulsor_fizzle', 'unibeam_charge', 'unibeam_loop',
        'unibeam_overheat', 'overdraft_sputter', 'core_explosion', 'nanite_form', 'nanite_dissolve', 'nanite_repair',
        'blade_slash', 'hammer_hit', 'hammer_slam', 'shield_open', 'shield_hit', 'shield_perfect', 'missile_flaps',
        'missile_launch', 'missile_explode', 'jarvis_warning']

T = {
 'en': {
  'unibeam.name': '<gold>Unibeam',
  'unibeam.desc': '<grey>Type: <aqua>Hold\n\n<grey>The chest reactor charges (~1s),\n<grey>then a thick beam pushes targets (up to 3s).\n<grey>Burns marks, breaks only glass and leaves.\n<grey>Blinds whoever is hit or looks into it.\n<grey>Afterwards the core overheats (2s).\n<red>The third overheat triggers the overdraft.\n\n<blue>Iron Man',
  'missiles.name': '<gold>Micro-missiles',
  'missiles.desc': '<grey>Type: <aqua>Hold\n\n<grey>Shoulder flaps open (~0.5s).\n<grey>Sweep the crosshair over enemies to mark up to 4.\n<grey>Release: a fan of homing missiles.\n<grey>No marks: they fly straight ahead.\n\n<blue>Iron Man',
  'nano_arsenal.name': '<gold>Nano arsenal',
  'nano_arsenal.desc': '<grey>Type: <aqua>Press\n\n<grey>Forms a weapon on the right hand.\n<grey>Press again: blade ↔ hammer.\n<grey>Blade: LMB slash weakens armor.\n<grey>Hammer: LMB heavy blow / ground slam,\n<grey>RMB charged launch through soft blocks.\n<grey>MMB: back to the repulsors.\n\n<blue>Iron Man',
  'sub': ['Repulsor fires', 'Repulsor charges', 'Repulsor volley', 'Repulsor fizzles', 'Unibeam charges', 'Unibeam hums',
          'Core overheats', 'Arc reactor sputters', 'Arc reactor explodes', 'Nanites form', 'Nanites dissolve', 'Nanites repair the suit',
          'Nano blade slashes', 'Nano hammer hits', 'Nano hammer slams the ground', 'Nano shield opens', 'Shield absorbs a hit', 'Perfect block',
          'Missile flaps open', 'Micro-missiles launch', 'Micro-missile explodes', 'JARVIS warns'],
  'hud': {'overheat_warning': 'JARVIS: Core overheated twice. One more and it goes critical.',
          'overdraft': 'JARVIS: Overdraft! The core cannot hold this.',
          'overdraft_critical': 'JARVIS: Core critical — brace!',
          'nano_lost': 'Nanites lost: %s s until the suit is ready',
          'weapons_offline': 'WEAPONS OFFLINE',
          'core': 'CORE'},
  'sunder': 'Sundered',
  'repulsor_blast': 'Repulsor blast', 'micro_missile': 'Micro-missile',
 },
 'de': {
  'unibeam.name': '<gold>Unibeam',
  'unibeam.desc': '<grey>Typ: <aqua>Halten\n\n<grey>Der Brustreaktor lädt (~1s),\n<grey>dann drückt ein dicker Strahl Ziele weg (bis 3s).\n<grey>Hinterlässt Brandspuren, zerbricht nur Glas und Laub.\n<grey>Blendet Getroffene und wer hineinsieht.\n<grey>Danach überhitzt der Kern (2s).\n<red>Die dritte Überhitzung löst den Overdraft aus.\n\n<blue>Iron Man',
  'missiles.name': '<gold>Mikroraketen',
  'missiles.desc': '<grey>Typ: <aqua>Halten\n\n<grey>Die Schulterklappen öffnen sich (~0,5s).\n<grey>Fahre mit dem Fadenkreuz über Gegner, um bis zu 4 zu markieren.\n<grey>Loslassen: ein Fächer zielsuchender Raketen.\n<grey>Ohne Markierung fliegen sie geradeaus.\n\n<blue>Iron Man',
  'nano_arsenal.name': '<gold>Nano-Arsenal',
  'nano_arsenal.desc': '<grey>Typ: <aqua>Drücken\n\n<grey>Formt eine Waffe an der rechten Hand.\n<grey>Erneut drücken: Klinge ↔ Hammer.\n<grey>Klinge: LMT-Schnitt schwächt Rüstung, RMT-Sprint.\n<grey>Hammer: LMT schwerer Schlag / Bodenstoß,\n<grey>RMT geladener Wurf durch weiche Blöcke.\n<grey>MMT: zurück zu den Repulsoren.\n\n<blue>Iron Man',
  'sub': ['Repulsor feuert', 'Repulsor lädt', 'Repulsor-Salve', 'Repulsor versagt', 'Unibeam lädt', 'Unibeam summt',
          'Kern überhitzt', 'Arc-Reaktor stottert', 'Arc-Reaktor explodiert', 'Naniten formen sich', 'Naniten lösen sich auf', 'Naniten reparieren den Anzug',
          'Nanoklinge schneidet', 'Nanohammer trifft', 'Nanohammer schlägt auf den Boden', 'Nanoschild öffnet sich', 'Schild fängt einen Treffer ab', 'Perfekter Block',
          'Raketenklappen öffnen sich', 'Mikroraketen starten', 'Mikrorakete explodiert', 'JARVIS warnt'],
  'hud': {'overheat_warning': 'JARVIS: Der Kern ist zweimal überhitzt. Noch einmal, und es wird kritisch.',
          'overdraft': 'JARVIS: Overdraft! Der Kern hält das nicht aus.',
          'overdraft_critical': 'JARVIS: Kern kritisch — festhalten!',
          'nano_lost': 'Naniten verloren: Anzug in %s s bereit',
          'weapons_offline': 'WAFFEN OFFLINE',
          'core': 'KERN'},
  'sunder': 'Zerschlagen',
  'repulsor_blast': 'Repulsorstoß', 'micro_missile': 'Mikrorakete',
 },
 'es': {
  'unibeam.name': '<gold>Unirrayo',
  'unibeam.desc': '<grey>Tipo: <aqua>Mantener\n\n<grey>El reactor del pecho se carga (~1s),\n<grey>luego un rayo grueso empuja a los objetivos (hasta 3s).\n<grey>Deja quemaduras, solo rompe vidrio y hojas.\n<grey>Ciega a quien alcanza o a quien lo mira.\n<grey>Después el núcleo se sobrecalienta (2s).\n<red>El tercer sobrecalentamiento activa la sobrecarga.\n\n<blue>Iron Man',
  'missiles.name': '<gold>Microcohetes',
  'missiles.desc': '<grey>Tipo: <aqua>Mantener\n\n<grey>Se abren las compuertas de los hombros (~0,5s).\n<grey>Pasa la mira sobre los enemigos para marcar hasta 4.\n<grey>Al soltar: un abanico de cohetes guiados.\n<grey>Sin marcas vuelan en línea recta.\n\n<blue>Iron Man',
  'nano_arsenal.name': '<gold>Nanoarsenal',
  'nano_arsenal.desc': '<grey>Tipo: <aqua>Pulsar\n\n<grey>Forma un arma en la mano derecha.\n<grey>Pulsa otra vez: hoja ↔ martillo.\n<grey>Hoja: corte con clic izq. debilita armaduras, clic der. embestida.\n<grey>Martillo: clic izq. golpe pesado / golpe al suelo,\n<grey>clic der. cargado lanza a través de bloques blandos.\n<grey>Clic central: vuelve a los repulsores.\n\n<blue>Iron Man',
  'sub': ['Repulsor dispara', 'Repulsor se carga', 'Ráfaga de repulsores', 'Repulsor falla', 'Unirrayo se carga', 'Unirrayo zumba',
          'Núcleo se sobrecalienta', 'Reactor Arc chisporrotea', 'Reactor Arc explota', 'Nanobots se forman', 'Nanobots se disuelven', 'Nanobots reparan el traje',
          'Nanohoja corta', 'Nanomartillo golpea', 'Nanomartillo golpea el suelo', 'Nanoescudo se abre', 'Escudo absorbe un golpe', 'Bloqueo perfecto',
          'Compuertas de cohetes se abren', 'Microcohetes despegan', 'Microcohete explota', 'JARVIS advierte'],
  'hud': {'overheat_warning': 'JARVIS: El núcleo se sobrecalentó dos veces. Una más y será crítico.',
          'overdraft': 'JARVIS: ¡Sobrecarga! El núcleo no aguantará esto.',
          'overdraft_critical': 'JARVIS: Núcleo crítico, ¡prepárate!',
          'nano_lost': 'Nanobots perdidos: traje listo en %s s',
          'weapons_offline': 'ARMAS DESACTIVADAS',
          'core': 'NÚCLEO'},
  'sunder': 'Quebrantado',
  'repulsor_blast': 'Disparo de repulsor', 'micro_missile': 'Microcohete',
 },
 'tr': {
  'unibeam.name': '<gold>Unibeam',
  'unibeam.desc': '<grey>Tür: <aqua>Basılı tut\n\n<grey>Göğüs reaktörü dolar (~1sn),\n<grey>sonra kalın bir ışın hedefleri iter (3sn\'ye kadar).\n<grey>Yanık izi bırakır, yalnızca cam ve yaprak kırar.\n<grey>Vurulanları ve ışına bakanları kör eder.\n<grey>Ardından çekirdek aşırı ısınır (2sn).\n<red>Üçüncü aşırı ısınma aşırı yüklemeyi başlatır.\n\n<blue>Iron Man',
  'missiles.name': '<gold>Mikro füzeler',
  'missiles.desc': '<grey>Tür: <aqua>Basılı tut\n\n<grey>Omuz kapakları açılır (~0,5sn).\n<grey>Nişangâhı düşmanların üzerinden geçirerek 4 tanesini işaretle.\n<grey>Bırak: güdümlü füze yelpazesi.\n<grey>İşaret yoksa düz giderler.\n\n<blue>Iron Man',
  'nano_arsenal.name': '<gold>Nano cephanelik',
  'nano_arsenal.desc': '<grey>Tür: <aqua>Bas\n\n<grey>Sağ elde bir silah oluşturur.\n<grey>Tekrar bas: kılıç ↔ çekiç.\n<grey>Kılıç: sol tık kesik zırhı zayıflatır, sağ tık atılma.\n<grey>Çekiç: sol tık ağır darbe / yere vuruş,\n<grey>sağ tık şarjlı fırlatma, yumuşak blokları deler.\n<grey>Orta tık: repulsörlere dön.\n\n<blue>Iron Man',
  'sub': ['Repulsör ateşler', 'Repulsör dolar', 'Repulsör yaylımı', 'Repulsör tekler', 'Unibeam dolar', 'Unibeam uğuldar',
          'Çekirdek aşırı ısınır', 'Ark reaktörü tekler', 'Ark reaktörü patlar', 'Nanitler şekillenir', 'Nanitler dağılır', 'Nanitler zırhı onarır',
          'Nano kılıç keser', 'Nano çekiç vurur', 'Nano çekiç yere çakılır', 'Nano kalkan açılır', 'Kalkan darbeyi emer', 'Kusursuz blok',
          'Füze kapakları açılır', 'Mikro füzeler fırlar', 'Mikro füze patlar', 'JARVIS uyarır'],
  'hud': {'overheat_warning': 'JARVIS: Çekirdek iki kez aşırı ısındı. Bir kez daha olursa kritik olur.',
          'overdraft': 'JARVIS: Aşırı yükleme! Çekirdek buna dayanamaz.',
          'overdraft_critical': 'JARVIS: Çekirdek kritik — sıkı tutun!',
          'nano_lost': 'Nanitler kayboldu: zırh %s sn sonra hazır',
          'weapons_offline': 'SİLAHLAR DEVRE DIŞI',
          'core': 'ÇEKİRDEK'},
  'sunder': 'Parçalanmış',
  'repulsor_blast': 'Repulsör atışı', 'micro_missile': 'Mikro füze',
 },
 'zh': {
  'unibeam.name': '<gold>胸口光束炮',
  'unibeam.desc': '<grey>类型：<aqua>按住\n\n<grey>胸口反应堆充能（约1秒），\n<grey>随后粗大的光束推开目标（最长3秒）。\n<grey>留下灼痕，只会击碎玻璃和树叶。\n<grey>被击中或直视光束的人会被闪盲。\n<grey>之后核心过热（2秒）。\n<red>第三次过热会触发超载。\n\n<blue>钢铁侠',
  'missiles.name': '<gold>微型导弹',
  'missiles.desc': '<grey>类型：<aqua>按住\n\n<grey>肩部舱盖打开（约0.5秒）。\n<grey>用准星扫过敌人，最多标记4个。\n<grey>松开：扇形发射追踪导弹。\n<grey>没有标记时直线飞行。\n\n<blue>钢铁侠',
  'nano_arsenal.name': '<gold>纳米武器库',
  'nano_arsenal.desc': '<grey>类型：<aqua>按下\n\n<grey>在右手形成武器。\n<grey>再次按下：刀刃 ↔ 战锤。\n<grey>刀刃：左键斩击削弱护甲，右键冲刺。\n<grey>战锤：左键重击 / 砸地，\n<grey>右键蓄力击飞，穿透软方块。\n<grey>中键：切回掌心炮。\n\n<blue>钢铁侠',
  'sub': ['掌心炮开火', '掌心炮充能', '掌心炮齐射', '掌心炮哑火', '胸口光束充能', '胸口光束嗡鸣',
          '核心过热', '方舟反应堆不稳', '方舟反应堆爆炸', '纳米机器人成形', '纳米机器人消散', '纳米机器人修复战衣',
          '纳米刀刃斩击', '纳米战锤命中', '纳米战锤砸地', '纳米护盾展开', '护盾挡下攻击', '完美格挡',
          '导弹舱盖打开', '微型导弹发射', '微型导弹爆炸', '贾维斯警告'],
  'hud': {'overheat_warning': '贾维斯：核心已过热两次。再来一次就会进入临界。',
          'overdraft': '贾维斯：超载！核心撑不住了。',
          'overdraft_critical': '贾维斯：核心临界——准备冲击！',
          'nano_lost': '纳米机器人丢失：战衣将在 %s 秒后就绪',
          'weapons_offline': '武器离线',
          'core': '核心'},
  'sunder': '破甲',
  'repulsor_blast': '掌心炮光弹', 'micro_missile': '微型导弹',
 },
}

FILES = {'en_us': 'en', 'de_de': 'de', 'tr_tr': 'tr', 'zh_cn': 'zh'}
for es in ['es_ar', 'es_cl', 'es_ec', 'es_es', 'es_mx', 'es_uy', 'es_ve']:
    FILES[es] = 'es'

for name, code in FILES.items():
    path = os.path.join(LANG, name + '.json')
    data = json.load(open(path, encoding='utf-8'))
    t = T[code]
    for ability in ['unibeam', 'missiles', 'nano_arsenal']:
        data[f'ability.viltrumitecore.ironman_{ability}.name'] = t[ability + '.name']
        data[f'ability.viltrumitecore.ironman_{ability}.desc'] = t[ability + '.desc']
    for sub, text in zip(SUBS, t['sub']):
        data[f'subtitles.viltrumitecore.ironman_{sub}'] = text
    for key, text in t['hud'].items():
        data[f'hud.viltrumitecore.ironman.{key}'] = text
    data['effect.viltrumitecore.sunder'] = t['sunder']
    data['entity.viltrumitecore.repulsor_blast'] = t['repulsor_blast']
    data['entity.viltrumitecore.micro_missile'] = t['micro_missile']
    with open(path, 'w', encoding='utf-8') as out:
        out.write(json.dumps(data, indent=2, ensure_ascii=False) + '\n')
    print(name, len(data))
