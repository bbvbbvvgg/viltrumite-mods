import json, os, collections
LANG = "/workspace/work/vm/viltrumitecore/src/main/resources/assets/viltrumitecore/lang"
P = "hud.viltrumitecore.ironman.hulkbuster."
T = {
 "busy": ("Systems busy", "Systeme ausgelastet", "Sistemas ocupados", "Sistemler meşgul", "系统繁忙"),
 "cooldown": ("Recharging", "Lädt nach", "Recargando", "Yeniden şarj", "冷却中"),
 "ground_only": ("Ground only", "Nur am Boden", "Solo en el suelo", "Sadece yerde", "仅限地面"),
 "hands_busy": ("Hands busy", "Hände belegt", "Manos ocupadas", "Eller dolu", "双手占用"),
 "no_energy": ("Not enough energy", "Zu wenig Energie", "Energía insuficiente", "Yetersiz enerji", "能量不足"),
 "not_damaged": ("Arm still intact", "Arm noch intakt", "El brazo sigue intacto", "Kol hâlâ sağlam", "手臂仍完好"),
 "spare_used": ("Spare arm already used", "Ersatzarm schon verbraucht", "Brazo de repuesto ya usado", "Yedek kol kullanıldı", "备用手臂已用"),
 "line_sleep": ("Go to sleep! Sleep! Sleep!", "Schlaf! Schlaf! Schlaf!", "¡Duérmete! ¡Duerme! ¡Duerme!", "Uyu! Uyu! Uyu!", "睡吧！睡吧！睡吧！"),
 "dash": ("Dash", "Sprint", "Impulso", "Atılma", "冲刺"),
 "jet": ("Jet charge", "Düsenladung", "Carga de propulsores", "Jet şarjı", "喷射蓄力"),
 "hover": ("Thruster fuel", "Schubtreibstoff", "Combustible", "İtici yakıtı", "推进燃料"),
 "grip": ("Grip", "Griff", "Agarre", "Kavrama", "握力"),
 "struggle": ("Mash W A S D / Space to break free!", "W A S D / Leertaste hämmern, um freizukommen!", "¡Pulsa W A S D / Espacio para liberarte!", "Kurtulmak için W A S D / Boşluk'a bas!", "连按 W A S D / 空格挣脱！"),
 "ready": ("ready", "bereit", "listo", "hazır", "就绪"),
 "move.combo": ("Combo", "Kombo", "Combo", "Kombo", "连击"),
 "move.charged": ("Charged punch", "Aufgeladener Schlag", "Golpe cargado", "Yüklü yumruk", "蓄力重拳"),
 "move.grab_reach": ("Grab", "Packen", "Agarre", "Yakalama", "擒抱"),
 "move.grab_miss": ("Grab miss", "Griff verfehlt", "Agarre fallido", "Iska", "擒抱落空"),
 "move.grab_hold": ("Hold", "Halten", "Sujetar", "Tutma", "擒住"),
 "move.grab_punch": ("Jackhammer", "Presslufthammer", "Martillo neumático", "Kırıcı yumruk", "冲击锤"),
 "move.grab_finish": ("Sleep!", "Schlaf!", "¡Duerme!", "Uyu!", "睡吧！"),
 "move.grab_throw": ("Throw", "Wurf", "Lanzamiento", "Fırlatma", "投掷"),
 "move.grab_slam": ("Slam", "Einschlag", "Estampar", "Çarpma", "猛砸"),
 "move.block": ("Block", "Block", "Bloqueo", "Blok", "格挡"),
 "move.counter": ("Counter", "Konter", "Contraataque", "Karşı saldırı", "反击"),
 "move.heavy_repulsors": ("Heavy repulsors", "Schwere Repulsoren", "Repulsores pesados", "Ağır itici ışınlar", "重型掌心炮"),
 "move.seismic_rift": ("Seismic rift", "Seismischer Riss", "Grieta sísmica", "Sismik yarık", "地震裂隙"),
 "move.veronica_cage": ("Veronica cage", "Veronica-Käfig", "Jaula de Veronica", "Veronica kafesi", "维罗妮卡牢笼"),
 "move.spare_arm": ("Spare arm", "Ersatzarm", "Brazo de repuesto", "Yedek kol", "备用手臂"),
 "move.jet_ram": ("Jet ram", "Düsenramme", "Embestida a reacción", "Jet koçbaşı", "喷射冲撞"),
 "move.dash_strike": ("Dash strike", "Sprintschlag", "Golpe de impulso", "Atılma vuruşu", "冲刺重击"),
 "move.hero_landing": ("Hero landing", "Heldenlandung", "Aterrizaje heroico", "Kahraman inişi", "英雄落地"),
}
COL = {"en_us": 0, "de_de": 1, "tr_tr": 3, "zh_cn": 4}
for f in sorted(os.listdir(LANG)):
    code = f[:-5]
    col = COL.get(code, 2)
    path = os.path.join(LANG, f)
    data = json.load(open(path, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    for k, v in T.items():
        data[P + k] = v[col]
    with open(path, "w", encoding="utf-8") as out:
        json.dump(data, out, ensure_ascii=False, indent=2)
        out.write("\n")
print("ok")
