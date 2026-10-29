"""The World clips. Ticks are 20/s; contact ticks match DioRules exactly:
jab 9/4, cross 9/4, hook 14/7, finisher 12/5, barrage hits every 2 t.
Sign rules (verified numerically against the runtime math, facing +Z):
  arm x<0 raises forward; right arm z>0 abducts; y<0 on chest turns the right
  shoulder forward; forearm x<0 flexes the elbow; shin x>0 bends the knee;
  head x>0 looks down; right finger z<0 curls into the palm. Left = mirror
  (negate y and z).
"""
from .clips import Clip, merge, wave, lin, ein, ein2, eout, eout2, eio, eback

FINGERS = ("index", "middle", "ring", "pinky")


def mirror(p):
    out = {}
    for k, v in p.items():
        if k == "@pos":
            out["@pos"] = {_swap(n): [-w[0], w[1], w[2]] for n, w in v.items()}
        else:
            out[_swap(k)] = [v[0], -v[1], -v[2]]
    return out


def _swap(n):
    if n.endswith("_R") or "_R_" in n:
        return n.replace("_R", "_L")
    if n.endswith("_L") or "_L_" in n:
        return n.replace("_L", "_R")
    return n


def side(p, s):
    return p if s == "R" else mirror(p)


def fist(s, k=1.0):
    p = {}
    for i, f in enumerate(FINGERS):
        p["finger_%s_R" % f] = [0, -4 + i * 3, -88 * k]
        p["finger_%s_R_2" % f] = [0, 0, -100 * k]
    p["thumb_R"] = [-25 * k, 0, -35 * k]
    p["thumb_R_2"] = [0, 0, -40 * k]
    return side(p, s)


def open_hand(s, spread=1.0):
    p = {}
    for i, f in enumerate(FINGERS):
        p["finger_%s_R" % f] = [(i - 1.5) * 7 * spread, 0, -12]
        p["finger_%s_R_2" % f] = [0, 0, -14]
    p["thumb_R"] = [-30 * spread, 0, 18 * spread]
    return side(p, s)


def fists(k=1.0):
    return merge(fist("R", k), fist("L", k))


# ---------------------------------------------------------------- base poses
IDLE = merge({
    "pelvis": [4, 0, 0], "waist": [-3, 0, 0], "chest": [-6, 0, 0], "neck": [-2, 0, 0], "head": [8, 0, 0],
    "arm_R": [6, -10, 14], "forearm_R": [-28, 8, 0], "hand_R": [0, 0, -8],
    "arm_L": [-2, 8, -11], "forearm_L": [-36, -6, 0], "hand_L": [0, 0, 6],
    "thigh_R": [-14, 0, 3], "shin_R": [24, 0, 0], "foot_R": [26, 0, 0],
    "thigh_L": [-3, 0, -3], "shin_L": [12, 0, 0], "foot_L": [32, 0, 0],
    "hose_R": [6, 0, 0], "hose_L": [6, 0, 0],
}, fists(0.9))

READY = merge({
    "pelvis": [8, 4, 0], "waist": [2, 4, 0], "chest": [8, 8, 0], "neck": [-2, -4, 0], "head": [6, -6, 0],
    "arm_L": [-72, 6, -6], "forearm_L": [-84, 0, 0], "hand_L": [0, 0, 0],
    "arm_R": [-52, -8, 14], "forearm_R": [-104, 0, 0], "hand_R": [0, 0, 0],
    "shoulder_L": [0, 0, 0], "shoulder_R": [0, 0, 0],
    "thigh_R": [-28, 0, 4], "shin_R": [44, 0, 0], "foot_R": [22, 0, 0],
    "thigh_L": [8, 0, -3], "shin_L": [32, 0, 0], "foot_L": [28, 0, 0],
    "hose_R": [12, 0, 0], "hose_L": [12, 0, 0],
}, fists(1.0))


def p(base, **over):
    """Copy of base with bone overrides (bone=[x,y,z]); '@pos' via pos=dict."""
    out = {k: (dict(v) if k == "@pos" else list(v)) for k, v in base.items()}
    pos = over.pop("pos", None)
    out.update(over)
    if pos:
        out["@pos"] = dict(pos)
    return out


def breathing(period=60, k=1.0):
    return wave(period, {"chest": [-1.6 * k, 0, 0], "neck": [0.8 * k, 0, 0], "arm_R": [0, 0, 1.4 * k], "arm_L": [0, 0, -1.4 * k],
                         "@pos": {"shoulder_R": [0, 0.22 * k, 0], "shoulder_L": [0, 0.22 * k, 0], "root": [0, 0.6 * k, 0]}})


def sway(period=60, k=1.0):
    return wave(period, {"hose_R": [5 * k, 0, 2 * k], "hose_L": [5 * k, 0, -2 * k], "thigh_R": [2 * k, 0, 0], "thigh_L": [-1.5 * k, 0, 0],
                         "head": [0, 2.5 * k, 0]}, phase=0.25)


def clips():
    out = []
    # ---------------- idle (60 t loop) ----------------
    c = Clip("idle", 60, loop=True).key(0, IDLE).key(60, IDLE)
    c.layer(breathing(60)).layer(sway(60))
    out.append(c)
    # ---------------- ready (combat idle, 40 t loop) ----------------
    c = Clip("ready", 40, loop=True).key(0, READY).key(40, READY)
    c.layer(breathing(40, 0.7)).layer(sway(40, 0.6))
    out.append(c)
    # ---------------- summon (14 t) ----------------
    tucked = merge({
        "pelvis": [20, 0, 0], "waist": [18, 0, 0], "chest": [30, 0, 0], "neck": [10, 0, 0], "head": [26, 0, 0],
        "arm_R": [-58, 30, -38], "forearm_R": [-110, 0, 0], "arm_L": [-58, -30, 38], "forearm_L": [-110, 0, 0],
        "thigh_R": [-70, 0, 6], "shin_R": [110, 0, 0], "foot_R": [30, 0, 0],
        "thigh_L": [-62, 0, -6], "shin_L": [104, 0, 0], "foot_L": [30, 0, 0],
        "hose_R": [30, 0, 0], "hose_L": [30, 0, 0],
        "@pos": {"root": [0, -5, 4]},
    }, fists(1.0))
    unfurl = merge({
        "pelvis": [0, 0, 0], "waist": [-8, 0, 0], "chest": [-14, 0, 0], "neck": [-4, 0, 0], "head": [-10, 0, 0],
        "arm_R": [-18, 0, 72], "forearm_R": [-14, 0, 0], "arm_L": [-18, 0, -72], "forearm_L": [-14, 0, 0],
        "hand_R": [0, 0, 10], "hand_L": [0, 0, -10],
        "thigh_R": [-8, 0, 8], "shin_R": [10, 0, 0], "foot_R": [30, 0, 0],
        "thigh_L": [-2, 0, -8], "shin_L": [6, 0, 0], "foot_L": [32, 0, 0],
        "hose_R": [-14, 0, 0], "hose_L": [-14, 0, 0],
        "@pos": {"root": [0, 1.0, 0]},
    }, open_hand("R", 1.3), open_hand("L", 1.3))
    clench = merge(p(unfurl, arm_R=[-8, -6, 40], arm_L=[-8, 6, -40], forearm_R=[-40, 0, 0], forearm_L=[-40, 0, 0], pos={"root": [0, 0.4, 0]}), fists(1.0))
    c = Clip("summon", 14).key(0, tucked).key(8, unfurl, eout).key(11, clench, eio).key(14, p(IDLE, pos={"root": [0, 0, 0]}), eio)
    out.append(c)
    # ---------------- dismiss (10 t) ----------------
    arch = p(IDLE, chest=[-18, 0, 0], waist=[-8, 0, 0], head=[-12, 0, 0], arm_R=[34, 0, 22], arm_L=[34, 0, -22],
             forearm_R=[-20, 0, 0], forearm_L=[-20, 0, 0], pos={"shoulder_R": [0, 0, 0.8], "shoulder_L": [0, 0, 0.8], "root": [0, 0.5, 0.5]})
    fold = p(tucked, pos={"root": [0, -3, 5], "shoulder_R": [0, 0, 0], "shoulder_L": [0, 0, 0]})
    c = Clip("dismiss", 10).key(0, p(IDLE, pos={"root": [0, 0, 0], "shoulder_R": [0, 0, 0], "shoulder_L": [0, 0, 0]})).key(4, arch, eout).key(10, fold, ein2)
    out.append(c)
    # ---------------- jab: left, 9 t, contact 4 ----------------
    R0 = p(READY, pos={"root": [0, 0, 0], "shoulder_L": [0, 0, 0], "shoulder_R": [0, 0, 0]})
    load = p(R0, chest=[8, 0, 0], waist=[2, 0, 0], arm_L=[-62, 4, -10], forearm_L=[-100, 0, 0], pos={"root": [0, 0, 0.6], "shoulder_L": [0, 0, 0.3], "shoulder_R": [0, 0, 0]})
    hit = p(R0, pelvis=[8, 10, 0], waist=[4, 12, 0], chest=[10, 26, 0], head=[10, -24, 0], neck=[-2, -8, 0],
            arm_L=[-92, -14, 8], forearm_L=[-3, 0, 0], hand_L=[0, 0, 0],
            arm_R=[-50, -10, 14], forearm_R=[-108, 0, 0], thigh_R=[-34, 0, 4], thigh_L=[14, 0, -3],
            pos={"root": [0, 0, -1.6], "shoulder_L": [0.3, 0, -1.0], "shoulder_R": [0, 0, 0.4]})
    recoil = p(hit, chest=[9, 20, 0], arm_L=[-88, -10, 4], forearm_L=[-16, 0, 0], pos={"root": [0, 0, -1.2], "shoulder_L": [0.2, 0, -0.6], "shoulder_R": [0, 0, 0.3]})
    c = Clip("jab", 9).key(0, R0).key(2, load, eout2).key(4, hit, ein).key(5.5, recoil, eout).key(9, R0, eio)
    out.append(c)
    # ---------------- cross: right, 9 t, contact 4 ----------------
    load = p(R0, chest=[8, 16, 0], waist=[2, 8, 0], arm_R=[-46, -6, 18], forearm_R=[-112, 0, 0], pos={"root": [0, 0, 0.7], "shoulder_R": [0, 0, 0.5], "shoulder_L": [0, 0, 0]})
    hit = p(R0, pelvis=[8, -14, 0], waist=[4, -14, 0], chest=[12, -30, 0], head=[10, 26, 0], neck=[-2, 8, 0],
            arm_R=[-93, 16, -8], forearm_R=[-3, 0, 0],
            arm_L=[-66, 10, -10], forearm_L=[-96, 0, 0], thigh_R=[-10, 0, 4], thigh_L=[-20, 0, -3],
            pos={"root": [0, 0, -1.8], "shoulder_R": [-0.3, 0, -1.1], "shoulder_L": [0, 0, 0.4]})
    recoil = p(hit, chest=[10, -24, 0], arm_R=[-89, 12, -4], forearm_R=[-16, 0, 0], pos={"root": [0, 0, -1.3], "shoulder_R": [-0.2, 0, -0.6], "shoulder_L": [0, 0, 0.3]})
    c = Clip("cross", 9).key(0, R0).key(2, load, eout2).key(4, hit, ein).key(5.5, recoil, eout).key(9, R0, eio)
    out.append(c)
    # ---------------- hook: spinning right hook, 14 t, contact 7 ----------------
    wind = p(R0, pelvis=[6, 18, 0], waist=[4, 22, 0], chest=[8, 40, 0], head=[8, -42, 0], neck=[0, -12, 0],
             arm_R=[-40, 10, 78], forearm_R=[-88, 0, 0], arm_L=[-70, 0, -18], forearm_L=[-90, 0, 0],
             thigh_R=[-10, 0, 10], thigh_L=[-30, 0, -6],
             pos={"root": [0, 0.6, 1.0], "shoulder_R": [0, 0.3, 0.8], "shoulder_L": [0, 0, -0.4]})
    hit = p(R0, pelvis=[8, -20, 0], waist=[6, -26, 0], chest=[12, -48, 0], head=[8, 40, 0], neck=[0, 12, 0],
            arm_R=[-86, -6, 76], forearm_R=[-92, 0, 0], hand_R=[0, 0, 0], arm_L=[-60, 12, -24], forearm_L=[-100, 0, 0],
            thigh_R=[-36, 0, 6], thigh_L=[10, 0, -10],
            pos={"root": [0, 0.2, -2.0], "shoulder_R": [0, 0.2, -1.0], "shoulder_L": [0, 0, 0.6]})
    follow = p(hit, chest=[12, -66, 0], waist=[6, -34, 0], arm_R=[-80, -30, 64], forearm_R=[-100, 0, 0],
               pos={"root": [0, 0, -2.4], "shoulder_R": [0, 0.1, -0.6], "shoulder_L": [0, 0, 0.8]})
    c = Clip("hook", 14).key(0, R0).key(4, wind, eout2).key(7, hit, ein).key(9.5, follow, eout).key(14, R0, eio)
    out.append(c)
    # ---------------- barrage: 4 t loop, a fist lands every 2 t ----------------
    B = p(R0, pelvis=[10, 0, 0], waist=[6, 0, 0], chest=[12, 0, 0], head=[12, 0, 0], thigh_R=[-30, 0, 6], thigh_L=[-6, 0, -6], shin_R=[50, 0, 0], shin_L=[40, 0, 0])

    def bar(lead):
        ext = dict(arm=[-94, -8, 6], fore=[-4, 0, 0])
        ret = dict(arm=[-58, 10, 16], fore=[-118, 0, 0])
        a, b = ("L", "R") if lead == "L" else ("R", "L")
        q = p(B, chest=[12, 14 if lead == "L" else -14, 0], waist=[6, 6 if lead == "L" else -6, 0])
        q.update(side({"arm_R": ext["arm"], "forearm_R": ext["fore"]}, a))
        q.update(side({"arm_R": ret["arm"], "forearm_R": ret["fore"]}, b))
        q["@pos"] = {"root": [0, 0, -0.6], "shoulder_%s" % a: [0, 0, -0.9], "shoulder_%s" % b: [0, 0, 0.4]}
        return q

    c = Clip("barrage", 4, loop=True).key(0, bar("L")).key(2, bar("R"), eio).key(4, bar("L"), eio)
    out.append(c)
    # ---------------- finisher: right straight lunge, 12 t, contact 5 ----------------
    wind = p(R0, pelvis=[2, 14, 0], waist=[-2, 14, 0], chest=[-4, 36, 0], head=[6, -34, 0], arm_R=[-36, -10, 24], forearm_R=[-122, 0, 0],
             arm_L=[-80, -10, -10], forearm_L=[-40, 0, 0], thigh_R=[-6, 0, 6], thigh_L=[-24, 0, -4],
             pos={"root": [0, 0.8, 1.6], "shoulder_R": [0, 0, 0.9], "shoulder_L": [0, 0, -0.5]})
    hit = p(R0, pelvis=[14, -16, 0], waist=[10, -16, 0], chest=[16, -34, 0], head=[12, 30, 0], neck=[-2, 8, 0],
            arm_R=[-96, 18, -10], forearm_R=[0, 0, 0], arm_L=[-30, 10, -30], forearm_L=[-70, 0, 0],
            thigh_R=[-48, 0, 4], shin_R=[30, 0, 0], thigh_L=[26, 0, -4], shin_L=[46, 0, 0],
            pos={"root": [0, -0.4, -3.6], "shoulder_R": [-0.4, 0, -1.4], "shoulder_L": [0, 0, 0.8]})
    hold = p(hit, chest=[15, -30, 0], arm_R=[-94, 14, -6], forearm_R=[-6, 0, 0], pos={"root": [0, -0.3, -3.3], "shoulder_R": [-0.3, 0, -1.1], "shoulder_L": [0, 0, 0.7]})
    c = Clip("finisher", 12).key(0, bar("L")).key(3, wind, eout2).key(5, hit, ein).key(8, hold, eout).key(12, R0, eio)
    out.append(c)
    # ---------------- guard: crossed forearms, loop ----------------
    G = p(R0, chest=[14, 0, 0], waist=[4, 0, 0], head=[18, 0, 0], neck=[4, 0, 0],
          arm_R=[-78, 24, -10], forearm_R=[-74, 0, -14], hand_R=[0, 0, -10],
          arm_L=[-84, -24, 10], forearm_L=[-70, 0, 14], hand_L=[0, 0, 10],
          thigh_R=[-36, 0, 6], shin_R=[60, 0, 0], thigh_L=[-20, 0, -6], shin_L=[52, 0, 0],
          pos={"root": [0, -0.4, 0], "shoulder_R": [0.2, 0, -0.4], "shoulder_L": [-0.2, 0, -0.4]})
    c = Clip("guard", 20, loop=True).key(0, G).key(20, G)
    c.layer(wave(20, {"chest": [0.8, 0, 0], "forearm_R": [0, 0, 1.2], "forearm_L": [0, 0, -1.2]}))
    out.append(c)
    # ---------------- time stop: 20 t cast, holds ----------------
    gather = merge({
        "pelvis": [10, 0, 0], "waist": [8, 0, 0], "chest": [16, 0, 0], "neck": [6, 0, 0], "head": [22, 0, 0],
        "arm_R": [-62, 34, -30], "forearm_R": [-96, 0, 0], "arm_L": [-62, -34, 30], "forearm_L": [-96, 0, 0],
        "thigh_R": [-30, 0, 4], "shin_R": [52, 0, 0], "foot_R": [24, 0, 0],
        "thigh_L": [-24, 0, -4], "shin_L": [48, 0, 0], "foot_L": [24, 0, 0],
        "hose_R": [20, 0, 0], "hose_L": [20, 0, 0],
        "@pos": {"root": [0, -1.2, 0.6], "shoulder_R": [0, 0, 0], "shoulder_L": [0, 0, 0]},
    }, fists(1.0))
    spread = merge({
        "pelvis": [-4, 0, 0], "waist": [-10, 0, 0], "chest": [-20, 0, 0], "neck": [-6, 0, 0], "head": [-16, 0, 0],
        "arm_R": [-26, -8, 96], "forearm_R": [-12, 0, 0], "hand_R": [0, 0, 18], "arm_L": [-26, 8, -96], "forearm_L": [-12, 0, 0], "hand_L": [0, 0, -18],
        "thigh_R": [-6, 0, 12], "shin_R": [8, 0, 0], "foot_R": [34, 0, 0],
        "thigh_L": [-6, 0, -12], "shin_L": [8, 0, 0], "foot_L": [34, 0, 0],
        "hose_R": [-20, 0, 6], "hose_L": [-20, 0, -6],
        "@pos": {"root": [0, 1.4, 0], "shoulder_R": [0, 0.4, 0.5], "shoulder_L": [0, 0.4, 0.5]},
    }, open_hand("R", 1.6), open_hand("L", 1.6))
    held = p(spread, chest=[-22, 0, 0], arm_R=[-28, -8, 100], arm_L=[-28, 8, -100], pos={"root": [0, 1.6, 0], "shoulder_R": [0, 0.4, 0.5], "shoulder_L": [0, 0.4, 0.5]})
    c = Clip("time_stop", 20, hold=True).key(0, p(IDLE, pos={"root": [0, 0, 0], "shoulder_R": [0, 0, 0], "shoulder_L": [0, 0, 0]})).key(6, gather, eout2).key(11, spread, eback).key(20, held, eio)
    out.append(c)
    # ---------------- roller barrage: punching down onto the roller, 4 t loop ----------------
    D = p(R0, pelvis=[30, 0, 0], waist=[16, 0, 0], chest=[26, 0, 0], head=[30, 0, 0], neck=[6, 0, 0],
          thigh_R=[-50, 0, 8], shin_R=[80, 0, 0], thigh_L=[-30, 0, -8], shin_L=[70, 0, 0])

    def down(lead):
        a, b = ("L", "R") if lead == "L" else ("R", "L")
        q = p(D, chest=[26, 12 if lead == "L" else -12, 0])
        q.update(side({"arm_R": [-48, -6, 4], "forearm_R": [-6, 0, 0]}, a))
        q.update(side({"arm_R": [-128, 8, 22], "forearm_R": [-112, 0, 0]}, b))
        q["@pos"] = {"root": [0, 0, 0], "shoulder_%s" % a: [0, -0.4, -0.8], "shoulder_%s" % b: [0, 0.3, 0.3]}
        return q

    c = Clip("roller_barrage", 4, loop=True).key(0, down("L")).key(2, down("R"), eio).key(4, down("L"), eio)
    out.append(c)
    return out


CONTACTS = {"jab": 4, "cross": 4, "hook": 7, "finisher": 5, "barrage": 0, "roller_barrage": 0}
LENGTHS = {"jab": 9, "cross": 9, "hook": 14, "finisher": 12, "summon": 14, "dismiss": 10, "time_stop": 20}
