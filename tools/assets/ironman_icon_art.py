"""Iron Man ability icons, drawn in the house style of the Reinhard icons (icon_art.py).

Iron Man's identity inside the shared grammar: hot-rod red frame for the nanosuit kit, gold
frame for the mark signatures (slot 3 inside a mark), graphite frame for the Hulkbuster slots;
armour drawn in each suit's own colours (Mark 50 red-gold, Mark 7 red-gold, Mark 42 gold-burgundy,
Mark 15 black, Mark 39 silver, Mark 17 gold-red, Iron Heart white-black, War Machine gunmetal),
repulsor and reactor light in white-cyan, JARVIS HUD in cyan, threat locks in red.
Original deterministic pixel art; imported by make_hero_icons.py.
"""
import math

from icon_art import *  # noqa: F401,F403 (house kit: palette, canvas, masks, strokes, sprites)
from icon_art import registry, to64

ICONS, art = registry()

NAMES = ["unibeam", "missiles", "nano_arsenal", "countermeasures", "scan", "veronica", "helmet", "suit",
         "sig_micro_laser", "sig_rocket_fist", "sig_camouflage", "sig_starboost", "sig_pulse_unibeam",
         "sig_shoulder_gun", "sig_slam", "hulk_grab", "hulk_slam", "hulk_hop"]
FOLDER = "textures/gui/ability/ironman/"
PANEL = "ironman"
# HUD mock: IronManAbilities pages 1 and 2 (four slots each), then a mark and the Hulkbuster on page 1.
HUD = [["unibeam", "missiles", "nano_arsenal", "countermeasures", None, None],
       ["scan", "veronica", "helmet", "suit", None, None],
       ["unibeam", "missiles", "sig_rocket_fist", "countermeasures", None, None],
       ["hulk_grab", "hulk_slam", "hulk_hop", "countermeasures", None, None]]
FLIGHT = (None, None, None)


def icon32(name):
    return ICONS[name]()


def icon(name):
    return to64(icon32(name))


# ---------------------------------------------------------------- suits
def ramp(*cs):
    return [hx(c) for c in cs]


RED = ramp("6b0f18", "a9212f", "d6323e", "f2707a")
GOLDP = ramp("8f6a2e", "c9a35a", "e9cb88", "f8ecc4")
GLOW = [hx("0098dc"), hx("4ad9ff"), hx("d9f1ff"), WHITE]


def suit(primary, secondary, glow=GLOW, joint=None):
    return {"a": primary[1], "A": primary[2], "H": primary[3], "d": primary[0],
            "g": secondary[1], "G": secondary[2], "q": secondary[0],
            "c": glow[1], "C": glow[3], "k": joint or OUT}


MARK50 = suit(RED, GOLDP)
MARK7 = suit(ramp("6b0f18", "9f131f", "c9293a", "e8606a"), ramp("8f6a2e", "c39a4e", "e7c67c", "f6e2b0"))
MARK42 = suit(ramp("8f6a2e", "c89756", "ebb767", "ffe3a0"), ramp("36071b", "671327", "8a2038", "a8384a"))
MARK15 = suit(ramp("0c0c0f", "1d1d22", "34343c", "55555f"), ramp("4a4a4a", "7c7c80", "b2b2b2", "d6d6d6"))
MARK39 = suit(ramp("7c8290", "b2b6bf", "d9dce2", "ffffff"), ramp("1d1d1d", "2e2e30", "4a4a4e", "66666a"))
MARK17 = suit(ramp("8f6a2e", "caa14d", "e7c981", "f8ecc4"), ramp("5e0a14", "9a121d", "c42430", "e0505a"))
IRONHEART = suit(ramp("8c8c92", "cbcbd0", "e9e9ec", "ffffff"), ramp("0c0c0c", "1e1e1e", "343436", "4c4c50"))
WARMACHINE = suit(ramp("1c1c1e", "3a3a3e", "5d5d62", "8a8a90"), ramp("6f6f74", "a2a2a8", "cfcfd4", "f0f0f2"),
                  glow=[hx("891e2b"), hx("fc5b5b"), hx("ffb3b3"), WHITE])
HULK = suit(RED, GOLDP)

TONY = {"h": hx("2a1a14"), "s": hx("c8906c"), "S": hx("e2b08c"), "b": hx("3a261c"), "t": hx("1d1d22"),
        "T": hx("34343c"), "e": hx("2a1a14")}

# Standing, front view: helmet with gold faceplate, chest reactor, repulsor palms.
FRONT = [
    "...aaaaa...",
    "..aAAaaad..",
    "..aGgggqd..",
    "..gCggqCg..",
    "..aggggqd..",
    "...aqqqd...",
    ".aaaaaaaad.",
    "aAaaGcGaaad",
    "aAa.acCa.ad",
    "aAd.aaad.ad",
    "cc..gqgq..c",
    "...aa.ad...",
    "...aa.ad...",
    "...Ad.ad...",
    "..ggd.dgg..",
]

# Side view facing right: faceplate and eye at the front, reactor on the chest.
SIDE = [
    "...aaaa...",
    "..aAAaaa..",
    "..aAaaGg..",
    "..daagCg..",
    "..daaGgq..",
    "...ddqq...",
    "..aaaaaa..",
    ".aAaaaacC.",
    ".aAaaaaad.",
    ".ad.Gaad..",
    "..d.gad...",
    "...qaqa...",
    "...aa.aa..",
    "...ad..ad.",
    "..ggq..gg.",
]

# Flying up and to the right, arms back, boots firing (Mark 39 starboost).
FLY_UP = [
    "......aaa.",
    ".....aAaaa",
    ".....aGCgq",
    "....aaagqd",
    "...aAacaa.",
    "..aAaaCaa.",
    ".aaaaaad..",
    "ggaaaad...",
    "q.aaad....",
    "..aad.....",
    ".aad......",
    "add.......",
    "gg........",
]

HELMET = [
    "....aaaaaaaa....",
    "..aaAAAaaaaaad..",
    ".aAAaaaaaaaaaad.",
    ".aAagggggggggad.",
    "aAagGGgggggggqad",
    "aAagGgggggggggqd",
    "aaggCCCgggCCCgqd",
    "aaggcccgggcccgqd",
    "aaagggggggggggad",
    "aAagggggqggggqad",
    ".aagggggqgggggd.",
    ".aaaggqqqqqggad.",
    "..aaggggggggad..",
    "..daqgggggqqad..",
    "...daaqqqqqad...",
    "....ddaaaadd....",
]

HULK_FRONT = [
    ".....aaaaa......",
    "....aAGgCa......",
    "....aagggd......",
    "aaaaaaaaaaaaaaa.",
    "aAAaaaaGgaaaaaad",
    "aAaaaaGCCgaaaaad",
    "aAaadaaccaadaaad",
    "aAad.aaaaaa.daad",
    "gggg.aaaaaa.gggq",
    "gGgq.aGaaga.gggq",
    "gggq..aaaa..gggq",
    "qqqq.aaa.aa.qqqq",
    ".....aaa.aaa....",
    "....aaad.aaad...",
    "....gggq.gggq...",
]

# Superhero landing, facing right: knee down, fist planted in the ground.
LANDING = [
    "....aaaa....",
    "...aAAaaa...",
    "...aaaGgg...",
    "...daagCg...",
    "...ddaGgq...",
    "..aaaaqqa...",
    ".aAaaaaacC..",
    "aAaaaaaaad..",
    "aAd.aaaaaad.",
    "ad..gaaa.ad.",
    "gg..aaqaa.a.",
    "...aad.aaad.",
    "..aad...aqg.",
    ".ggq...ggqg.",
]

# Hulkbuster slamming: both arms down, the giant fists on the ground.
HULK_SLAM = [
    ".....aaaaa......",
    "....aAGgCa......",
    "....aagggd......",
    "..aaaaaaaaaaaa..",
    ".aAAaaaGgaaaaad.",
    ".aAaaaGCCgaaaad.",
    ".aAadaaccaadaad.",
    ".aAd.aaaaaa.dad.",
    ".aAd.aaaaaa.dad.",
    ".aad.aGaaga.dad.",
    "gggg..aaaa..gggq",
    "gGGgq.aa.aaqgggq",
    "gGggq.aa.aaqgggq",
    "ggggq.aa.aaqgggq",
    "qqqq.aad.aadqqqq",
]

FOE_CUT = [
    "...ooo...",
    "..ooooo..",
    "..oorro..",
    "..ooooo..",
    "...ooo...",
    ".ooooooo.",
    "ooooooooo",
]


def man(ic, rows, x, y, pal, **kw):
    return sprite(ic, rows, x, y, pal, **kw)


def beam(ic, pts, w, core=WHITE, mid=GLOW[2], edge=GLOW[1], halo=GLOW[0]):
    st = stroke(pts, w[0], w[1], w[2])
    m = set(st)
    ic.mix(grow(m, True) - m, halo, 0.7)
    for p, d in st.items():
        ic.set(p[0], p[1], core if d < 0.35 else mid if d < 0.7 else edge)
    return m


def lock(ic, x0, y0, x1, y1, col=BLOOD[3], arm=2):
    for (cx, cy, sx, sy) in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        ic.fill({(cx + sx * k, cy) for k in range(arm + 1)} | {(cx, cy + sy * k) for k in range(arm + 1)}, col)


def missile(ic, x, y, ang, col=PALE[2], tip=BLOOD[3]):
    """A small missile: 4-px body along ang (degrees), red tip, white exhaust dot."""
    ux, uy = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    body = {(int(round(x + ux * k)), int(round(y + uy * k))) for k in range(4)}
    ic.fill(grow(body) - body, OUT)
    ic.fill(body, col)
    ic.set(int(round(x + ux * 3)), int(round(y + uy * 3)), tip)
    ic.set(int(round(x - ux)), int(round(y - uy)), FIRE[2])


FIRE = [hx(c) for c in ("c42430", "f77622", "feae34", "fee761")]


def flame(ic, x, y, ang, length, width=2.4):
    """Thruster flame from (x, y) pointing along ang."""
    ux, uy = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    st = stroke([(x, y), (x + ux * length * 0.5, y + uy * length * 0.5), (x + ux * length, y + uy * length)],
                width, width * 0.8, 0.3)
    for p, d in st.items():
        ic.set(p[0], p[1], WHITE if d < 0.3 else FIRE[3] if d < 0.6 else FIRE[2] if d < 0.85 else FIRE[1])


def ground(ic, y, col=BG[3]):
    ic.fill(rect(2, y, 29, 29), col)
    ic.fill(rect(2, y, 29, y), PALE[0])
    ic.fill(rect(2, y + 1, 29, y + 1), BG[4])


def nanites(ic, pts, cols=(RED[2], GOLDP[2], RED[1])):
    for i, (x, y) in enumerate(pts):
        ic.set(x, y, cols[i % len(cols)])


# ---------------------------------------------------------------- page 1
@art
def unibeam():
    """Unibeam: the chest reactor fires a thick white-cyan beam that shoves the foe back."""
    ic = Icon("ironman", center=(14, 15))
    beam(ic, [(10, 14), (20, 14), (30, 13)], (3.0, 5.0, 6.4))
    foe(ic, 21, 8)
    beam(ic, [(10, 14), (19, 14), (21, 14)], (2.2, 3.4, 3.0))
    speed_lines(ic, [(6, 26, 29), (23, 25, 29)], cols=(PALE[0], PALE[1]))
    man(ic, SIDE, 1, 7, MARK50)
    ic.fill(disc(9, 14, 1.6), WHITE)
    ic.sparkle(6, 4)
    return ic.image()


@art
def missiles():
    """Missiles: shoulder pods open and a fan of homing missiles curves onto the locked targets."""
    ic = Icon("ironman", center=(14, 17))
    targets = ((25, 6), (25, 19))
    for (tx, ty) in targets:
        lock(ic, tx - 3, ty - 3, tx + 3, ty + 3)
        ic.set(tx, ty, BLOOD[4])
    for (ctrl, end, ang) in ((((11, 15), (13, 4), (20, 5)), (21, 6), 10),
                             (((12, 15), (17, 9), (21, 9)), (21, 9), -10),
                             (((13, 17), (18, 21), (21, 19)), (21, 19), -20)):
        trail = stroke(list(ctrl), 0.4, 1.6, 0.8)
        ic.fill(set(trail), PALE[0])
        ic.fill({p for p, d in trail.items() if d < 0.45}, PALE[2])
        missile(ic, end[0] - 3, end[1], ang)
    man(ic, FRONT, 3, 14, MARK50)
    for (x, y) in ((2, 19), (3, 18), (12, 19), (13, 18)):
        ic.set(x, y, GOLDP[3])
    ic.sparkle(6, 5)
    return ic.image()

@art
def nano_arsenal():
    """Nano Arsenal: nanites pour out of the gauntlet and set into a blade and a hammer."""
    ic = Icon("ironman", center=(16, 14))
    # the blade on the rising diagonal: wide red body, white-cyan cutting edge, gold guard
    def along(x, y):
        return ((x - 6) + (26 - y)) / 1.414
    def across(x, y):
        return ((x - 6) - (26 - y)) / 1.414
    blade = {(x, y) for x in range(32) for y in range(32) if 4.0 <= along(x, y) <= 26 and abs(across(x, y)) <= 1.6 - max(0, along(x, y) - 22) * 0.4}
    ic.solid(blade, (RED[0], RED[1], RED[2]))
    ic.fill({(x, y) for (x, y) in blade if across(x, y) < -0.6}, GLOW[2])
    ic.fill({(x, y) for (x, y) in blade if along(x, y) > 23}, WHITE)
    guard = {(x, y) for x in range(32) for y in range(32) if 3.0 <= along(x, y) <= 4.4 and abs(across(x, y)) <= 3.4}
    ic.solid(guard, (GOLDP[0], GOLDP[1], GOLDP[2]))
    grip = {(x, y) for x in range(32) for y in range(32) if 0 <= along(x, y) < 3.0 and abs(across(x, y)) <= 0.8}
    ic.solid(grip, (RED[0], RED[0], RED[1]))
    # the hammer crossing it
    handle = line(25, 26, 13, 11, 1.6)
    ic.solid(handle, (GOLDP[0], GOLDP[1], GOLDP[2]))
    head = {(x, y) for x in range(32) for y in range(32)
            if abs((x - 11.5) * 0.78 + (y - 8.5) * 0.62) <= 3.0 and abs(-(x - 11.5) * 0.62 + (y - 8.5) * 0.78) <= 5.2}
    ic.solid(head, (RED[0], RED[1], RED[2], RED[3]))
    ic.fill({(x, y) for (x, y) in head if abs(-(x - 11.5) * 0.62 + (y - 8.5) * 0.78) <= 0.9}, GOLDP[2])
    # nanites streaming in from the corner
    nanites(ic, [(27, 4), (25, 7), (28, 9), (22, 3), (24, 11), (29, 6), (20, 6), (26, 14), (23, 16)])
    ic.sparkle(5, 25, big=False)
    ic.sparkle(26, 24)
    return ic.image()

@art
def countermeasures():
    """Countermeasures: flares burst out in two curving wings; the homing missile breaks off after them."""
    ic = Icon("ironman", center=(16, 12))
    for side in (-1, 1):
        for k in range(3):
            ex = 16 + side * (7 + 4 * k)
            ey = 12 + 6 * k
            ctrl = [(16 + side * 2, 9), (16 + side * (5 + 4 * k), 5 + 2 * k), (ex, ey)]
            trail = stroke(ctrl, 0.3, 1.5, 0.9)
            ic.fill(set(trail), PALE[0])
            ic.fill({p for p, d in trail.items() if d < 0.45}, PALE[3])
            ic.fill(grow({(int(round(ex)), int(round(ey)))}), FIRE[2])
            ic.set(int(round(ex)), int(round(ey)), WHITE)
    tr = stroke([(28, 29), (25, 25), (21, 25)], 0.3, 1.0, 0.6)
    ic.fill(set(tr), BG[4])
    missile(ic, 20, 25, 180, col=ENEMY[3], tip=PALE[3])
    man(ic, FRONT, 11, 3, MARK50)
    return ic.image()

@art
def scan():
    """Scan: JARVIS locks the creature in cyan brackets, a scan line sweeps it and an info card reads it."""
    ic = Icon("ironman", center=(11, 16))
    foe(ic, 5, 9)
    lock(ic, 2, 6, 16, 25, col=GLOW[1], arm=3)
    ic.mix(rect(3, 15, 15, 16), GLOW[1], 0.55)
    ic.fill(rect(3, 15, 15, 15), GLOW[2])
    # info card
    card = rect(18, 5, 28, 21)
    ic.mix(card, GLOW[0], 0.45)
    ic.outline(card, GLOW[1])
    ic.fill(rect(20, 7, 26, 8), BLOOD[3])
    ic.fill(rect(24, 7, 26, 8), BLOOD[1])
    ic.fill(rect(20, 11, 26, 11), GLOW[2])
    ic.fill(rect(20, 13, 24, 13), GLOW[1])
    ic.fill(rect(20, 15, 25, 15), GLOW[1])
    ic.fill(rect(20, 18, 22, 19), GOLD[2])
    ic.fill(rect(24, 18, 26, 19), EARTHY[2])
    ic.fill(line(16, 12, 18, 10), GLOW[1])
    ic.sparkle(24, 26, big=False, col=GLOW[2])
    return ic.image()


@art
def veronica():
    """Veronica: the red pod drops from orbit on a fire trail onto the marked landing ring."""
    ic = Icon("ironman", center=(16, 13))
    ground(ic, 25)
    ic.fill(ring_pts(17, 25, 9, 1.8, 0, 360, step=4), BLOOD[3])
    ic.fill({(17, 25), (8, 25), (26, 25)}, BLOOD[4])
    trail = stroke([(1, 0), (6, 4), (11, 9)], 0.6, 3.2, 4.2)
    for p, d in trail.items():
        ic.set(p[0], p[1], FIRE[1] if d > 0.6 else FIRE[2] if d > 0.3 else FIRE[3])
    for (x, y) in ((3, 8), (8, 2), (2, 12)):
        ic.fill({(x, y), (x + 1, y)}, BG[4])
    # the pod: a hexagonal red capsule nose-down along the fall line, gold bands, glowing port
    c, s_ = 0.707, 0.707
    def uv(x, y):
        return ((x - 16.5) * c + (y - 15.5) * s_, -(x - 16.5) * s_ + (y - 15.5) * c)
    pod = {(x, y) for x in range(32) for y in range(32)
           if abs(uv(x, y)[0]) <= 7.0 and abs(uv(x, y)[1]) <= 5.0 - max(0, abs(uv(x, y)[0]) - 4.0) * 1.0}
    ic.solid(pod, (RED[0], RED[1], RED[2], RED[3]))
    for k in (-3.2, 2.0):
        ic.fill({(x, y) for (x, y) in pod if abs(uv(x, y)[0] - k) <= 0.55}, GOLDP[2])
    ic.fill({(x, y) for (x, y) in pod if uv(x, y)[0] > 5.6}, GOLDP[1])
    ic.fill(disc(15.5, 14.5, 1.3), GLOW[2])
    ic.set(15, 14, WHITE)
    ic.sparkle(27, 6)
    return ic.image()

@art
def helmet():
    """Helmet: the faceplate swings up off the helmet, the eyes lit underneath."""
    ic = Icon("ironman", center=(16, 16))
    hm = sprite(ic, HELMET, 8, 9, MARK50)
    # the open face: Tony underneath, plate lifted above
    face = rect(11, 14, 20, 22)
    ic.fill(face, TONY["s"])
    ic.fill(rect(11, 14, 20, 14), TONY["h"])
    ic.fill({(13, 17), (14, 17), (17, 17), (18, 17)}, TONY["e"])
    ic.fill(rect(13, 20, 18, 22), TONY["b"])
    ic.fill(rect(14, 21, 17, 21), TONY["s"])
    ic.set(16, 18, TONY["S"])
    plate = rect(11, 2, 20, 8)
    ic.solid(plate, (GOLDP[0], GOLDP[1], GOLDP[2], GOLDP[3]))
    ic.fill({(12, 5), (13, 5), (14, 5), (17, 5), (18, 5), (19, 5)}, GLOW[2])
    ic.fill(line(15.5, 7, 15.5, 8), GOLDP[0])
    for x in (9, 22):
        ic.fill(line(x, 6, x, 10), GLOW[1])
        ic.fill({(x - 1, 7), (x + 1, 7), (x, 6)}, GLOW[2])
    ic.sparkle(26, 4)
    return ic.image()


@art
def suit():
    """Suit: the Mark 50 nanosuit flows out of the reactor over Tony, half of him already armoured."""
    ic = Icon("ironman", center=(16, 15))
    body = rect(6, 18, 25, 29)
    head = rect(11, 5, 20, 16)
    ic.outline(body | head)
    # Tony's half: face, beard, black shirt
    ic.fill({p for p in head if p[0] <= 15}, TONY["s"])
    ic.fill({p for p in head if p[0] <= 15 and p[1] <= 7}, TONY["h"])
    ic.fill({(13, 10), (14, 10)}, TONY["e"])
    ic.fill({p for p in head if p[0] <= 15 and p[1] >= 13}, TONY["b"])
    ic.fill({p for p in body if p[0] <= 15}, TONY["t"])
    ic.fill({p for p in body if p[0] <= 15 and p[1] == 18}, TONY["T"])
    # armoured half
    ic.fill({p for p in head if p[0] >= 16}, RED[1])
    ic.fill({p for p in head if p[0] >= 16 and p[1] >= 8}, GOLDP[1])
    ic.fill({(17, 10), (18, 10), (19, 10)}, GLOW[2])
    ic.fill({p for p in head if p[0] >= 16 and p[1] == 5}, RED[2])
    ic.fill({p for p in body if p[0] >= 16}, RED[1])
    ic.fill({p for p in body if p[0] >= 16 and p[1] == 18}, RED[2])
    ic.fill({(x, y) for x in range(18, 26, 3) for y in (22, 25)}, GOLDP[1])
    ic.fill(rect(11, 17, 20, 17), OUT)
    # nanite front between the halves
    nanites(ic, [(16, 6), (15, 9), (16, 12), (15, 15), (16, 19), (15, 23), (16, 26), (14, 21), (13, 25), (14, 12)])
    # the reactor
    ic.fill(disc(16, 22, 2.2), GLOW[1])
    ic.fill(disc(16, 22, 1.1), WHITE)
    ic.mix(disc(16, 22, 4.2) - disc(16, 22, 2.4), GLOW[2], 0.35)
    ic.sparkle(26, 6)
    return ic.image()


# ---------------------------------------------------------------- mark signatures
@art
def sig_micro_laser():
    """Mark 7 micro laser: a hair-thin forearm laser slices a shield in two."""
    ic = Icon("ironman_mark", center=(19, 15))
    arm = rect(2, 16, 12, 21)
    ic.solid(arm, (MARK7["d"], MARK7["a"], MARK7["A"]))
    ic.fill(rect(4, 16, 11, 16), MARK7["g"])
    em = rect(9, 13, 12, 15)
    ic.solid(em, (MARK7["q"], MARK7["g"], MARK7["G"]))
    ic.set(12, 14, WHITE)
    las = line(12, 14, 29, 14)
    ic.mix(grow(las, True) - las, BLOOD[3], 0.8)
    ic.fill(las, WHITE)
    # the shield, split along the cut, halves drifting apart
    top = {(x, y) for x in range(32) for y in range(32) if 18 <= x <= 27 and 5 <= y <= 13 and not (y > 10 and abs(x - 22.5) > 13 - y + 2.5)}
    bot = {(x, y) for x in range(32) for y in range(32) if 18 <= x <= 27 and 16 <= y <= 25 and abs(x - 22.5) <= (27 - y) * 0.9}
    for m in (top, bot):
        ic.solid(m, (ROCK[1], ROCK[2], ROCK[3]))
        ic.fill({(x, y) for (x, y) in m if x in (18, 27)}, PALE[0])
        ic.fill({(22, y) for (x, y) in m} & m, ROCK[0])
    ic.fill(rect(18, 5, 27, 5) & top, PALE[1])
    for (x, y) in ((20, 15), (24, 15), (27, 14)):
        ic.set(x, y, FIRE[3])
    ic.sparkle(6, 6)
    return ic.image()


@art
def sig_rocket_fist():
    """Mark 42 rocket fist: the gauntlet flies off on its thruster, hits and loops back to the arm."""
    ic = Icon("ironman_mark", center=(17, 15))
    ret = stroke([(26, 20), (22, 29), (8, 26)], 0.6, 1.4, 0.6)
    ic.fill({p for p, d in ret.items() if (p[0] + p[1]) % 3 != 0}, GOLDP[3])
    ic.fill({(8, 25), (9, 24), (9, 27)}, GOLDP[3])
    foe(ic, 21, 3)
    burst(ic, 22, 12, 3.4, ramp=(FIRE[1], FIRE[3], WHITE), spikes=7)
    flame(ic, 11, 14, 180, 8, width=3.2)
    fist = {(x, y) for x in range(32) for y in range(32) if ((x - 15.5) / 4.2) ** 4 + ((y - 14) / 3.8) ** 4 <= 1}
    ic.solid(fist, (MARK42["q"], MARK42["a"], MARK42["A"], MARK42["H"]))
    for yy in (13, 15):
        ic.fill(line(16, yy, 19, yy), MARK42["d"])
    ic.fill(rect(12, 11, 12, 17), MARK42["g"])
    arm = rect(2, 23, 7, 28)
    ic.solid(arm, (MARK42["q"], MARK42["g"], MARK42["G"]))
    ic.fill(disc(7, 25.5, 1.4), GLOW[2])
    return ic.image()


@art
def sig_camouflage():
    """Mark 15 camouflage: the black suit fades into a shimmer; its first strike lands twice as hard."""
    ic = Icon("ironman_mark", center=(14, 15))
    tmp = Icon("ironman_mark", bg=False)
    m = sprite(tmp, FRONT, 6, 7, MARK15, outline=None)
    edge = grow(m, True) - m
    for (x, y) in m:
        if ic.ok(x, y):
            c = tmp.px[(x, y)]
            a = 0.85 if y < 12 else 0.55 if y < 17 else 0.25
            if (x + y) % 2 == 0 and y >= 15:
                continue
            ic.mix({(x, y)}, c, a)
    ic.fill({p for p in edge if (p[0] + 2 * p[1]) % 3 != 0 and p[1] < 17}, GLOW[1])
    ic.fill({p for p in edge if (p[0] + 2 * p[1]) % 4 == 0 and p[1] >= 17}, GLOW[0])
    ic.fill({(8, 10), (14, 10)}, GLOW[2])
    # the doubled first hit: two stacked strike marks
    for (x, y) in ((22, 6), (25, 9)):
        burst(ic, x, y, 2.8, ramp=(BLOOD[2], BLOOD[3], WHITE), spikes=6)
    ic.fill(line(20, 15, 27, 15), PALE[1])
    ic.fill(line(21, 18, 28, 18), PALE[0])
    ic.sparkle(24, 24)
    return ic.image()


@art
def sig_starboost():
    """Mark 39 starboost: the back booster ignites and fires the suit straight up into the stars."""
    ic = Icon("ironman_mark", bg=False)
    sky = [hx("0e071b"), hx("1a1932"), hx("2a2f4e"), hx("3b1443")]
    for (x, y) in ic.inside:
        ic.px[(x, y)] = sky[min(2, (31 - y) // 10)] if (x + y) % 7 else sky[1]
    for (x, y) in ((5, 5), (12, 3), (25, 9), (8, 14), (27, 18), (20, 4)):
        ic.set(x, y, PALE[2])
    ic.sparkle(25, 4, big=False, col=GOLD[3])
    flame(ic, 11, 20, 125, 10, width=4.2)
    flame(ic, 9, 18, 150, 7, width=2.2)
    man(ic, FLY_UP, 11, 5, MARK39)
    for (y, a, b) in ((24, 16, 19), (27, 19, 22)):
        ic.fill(line(a, y, b, y - 3), PALE[0])
    return ic.image()


@art
def sig_pulse_unibeam():
    """Mark 17 pulse unibeam: the reactor fires three short, separate pulses."""
    ic = Icon("ironman_mark", center=(13, 15))
    for k, (x0, L, w) in enumerate(((12, 3, 3.8), (19, 3, 3.2), (25, 3, 2.6))):
        beam(ic, [(x0, 14), (x0 + L / 2, 14), (x0 + L, 14)], (w * 0.8, w, w * 0.6))
    for x0 in (17, 23):
        ic.set(x0, 14, GLOW[0])
    man(ic, SIDE, 1, 7, MARK17)
    ic.fill(disc(9, 14, 1.5), WHITE)
    ic.sparkle(24, 6)
    ic.sparkle(26, 24, big=False, col=GOLD[3])
    return ic.image()

@art
def sig_shoulder_gun():
    """War Machine Mk2 shoulder gun: the rotary cannon on his shoulder hoses bullets at the target."""
    ic = Icon("ironman_mark", center=(9, 16))
    ic.mix(disc(8, 18, 8), BG[4], 0.35)
    man(ic, SIDE, 2, 11, WARMACHINE)
    gun = rect(5, 7, 15, 10)
    ic.solid(gun, (hx("3a3a3e"), hx("8a8a90"), hx("cfcfd4")))
    ic.fill(rect(6, 11, 9, 11), hx("5d5d62"))
    ic.fill({(x, 8) for x in range(8, 16, 2)}, hx("1c1c1e"))
    ic.fill({(x, 9) for x in range(9, 16, 2)}, hx("5d5d62"))
    burst(ic, 18, 8.5, 3.0, ramp=(FIRE[1], FIRE[3], WHITE), spikes=6)
    for (x, y) in ((22, 7), (26, 9), (23, 11), (28, 6), (27, 12)):
        ic.fill({(x, y), (x + 1, y)}, FIRE[3])
        ic.set(x - 1, y, FIRE[1])
    for (x, y) in ((12, 14), (14, 15), (13, 17)):
        ic.fill({(x, y)}, GOLD[2])
    ic.sparkle(25, 22)
    return ic.image()

@art
def sig_slam():
    """Iron Heart Mk3 slam: down from the jump, fist into the ground, a ring shockwave out of it."""
    ic = Icon("ironman_mark", center=(16, 13))
    ground(ic, 24)
    for (rx, ry, c) in ((13, 3.2, GLOW[1]), (9, 2.2, GLOW[2]), (5, 1.3, WHITE)):
        ic.fill(ring_pts(16, 24, rx, ry, 0, 360, step=3), c)
    for pts in (((16, 25), (13, 27), (14, 29)), ((17, 25), (21, 27), (22, 29))):
        ic.fill(polyline(pts), INK)
    for x, (y0, y1) in ((8, (2, 9)), (24, (2, 9)), (12, (0, 5)), (20, (0, 5))):
        for y in range(y0, y1 + 1):
            ic.set(x, y, PALE[1] if y > (y0 + y1) / 2 else PALE[0])
    man(ic, LANDING, 10, 10, IRONHEART)
    for (x, y) in ((4, 20), (27, 19), (6, 17), (25, 16)):
        ic.fill({(x, y), (x + 1, y)}, PALE[0])
    return ic.image()

@art
def hulk_grab():
    """Hulkbuster grab: the giant gauntlet closes round the foe; the throw arc shows where it goes."""
    ic = Icon("ironman_hulk", center=(16, 14))
    path = stroke([(17, 9), (25, 0), (30, 9)], 0.6, 1.6, 1.0)
    ic.fill({p for p, d in path.items() if (p[0] + p[1]) % 3}, PALE[2])
    ic.fill({(29, 9), (30, 10), (31, 9), (30, 11)}, PALE[3])
    # the foe, upper body sticking out of the fist
    sprite(ic, FOE_CUT, 12, 4, {"o": ENEMY[2], "r": BLOOD[3]})
    hand = {(x, y) for x in range(32) for y in range(32) if ((x - 16) / 9) ** 4 + ((y - 17) / 6) ** 4 <= 1}
    ic.solid(hand, (RED[0], RED[1], RED[2], RED[3]))
    for x0 in (8, 13, 18):
        f = rect(x0, 11, x0 + 3, 15)
        ic.solid(f, (GOLDP[0], GOLDP[1], GOLDP[2]))
        ic.fill(rect(x0, 13, x0 + 3, 13), GOLDP[0])
    thumb = rect(22, 13, 25, 18)
    ic.solid(thumb, (GOLDP[0], GOLDP[1], GOLDP[2]))
    ic.fill(rect(8, 20, 24, 21), GOLDP[1])
    ic.fill(rect(8, 20, 24, 20), GOLDP[2])
    wrist = rect(10, 24, 22, 29)
    ic.solid(wrist, (RED[0], RED[1], RED[2]))
    ic.fill(rect(13, 25, 19, 26), hx("3d3d3d"))
    ic.fill(rect(15, 25, 17, 25), GLOW[1])
    ic.sparkle(5, 5)
    return ic.image()

@art
def hulk_slam():
    """Hulkbuster jump slam: both fists come down and the ground caves in around them."""
    ic = Icon("ironman_hulk", center=(16, 11))
    ground(ic, 23)
    crater = {(x, y) for x in range(32) for y in range(23, 30) if ((x - 16) / 12) ** 2 + ((y - 23) / 4.5) ** 2 <= 1}
    ic.fill(crater, INK)
    ic.fill(ring_pts(16, 23, 13.5, 2.8, 180, 360), PALE[3])
    for pts in (((4, 24), (3, 27)), ((28, 24), (29, 28)), ((16, 27), (15, 29))):
        ic.fill(polyline(pts), INK)
    for (x, y, s_) in ((3, 16, 2), (27, 15, 2), (5, 12, 2), (25, 11, 2)):
        ic.fill(grow(rect(x, y, x + s_ - 1, y + s_ - 1)) - rect(x, y, x + s_ - 1, y + s_ - 1), OUT)
        ic.fill(rect(x, y, x + s_ - 1, y + s_ - 1), ROCK[2])
    man(ic, HULK_SLAM, 8, 8, HULK)
    for x in (6, 25):
        ic.fill(line(x, 2, x, 7), PALE[1])
    return ic.image()

@art
def hulk_hop():
    """Hulkbuster hop: a heavy leap on a long arc, a dust ring where it pushed off."""
    ic = Icon("ironman_hulk", center=(18, 11))
    ground(ic, 26)
    arcm = stroke([(5, 25), (8, 8), (16, 4)], 0.8, 2.2, 1.0)
    ic.fill({p for p, d in arcm.items() if (p[0] * 2 + p[1]) % 5 > 1}, PALE[2])
    ic.fill({p for p, d in arcm.items() if d < 0.35 and (p[0] * 2 + p[1]) % 5 > 1}, WHITE)
    ic.fill(ring_pts(6, 26, 5, 1.2, 180, 360), PALE[3])
    for (x, y) in ((2, 23), (9, 22), (11, 24), (3, 21)):
        ic.fill({(x, y), (x + 1, y)}, PALE[0])
    ic.mix({(x, 26) for x in range(16, 29)}, INK, 0.6)
    man(ic, HULK_FRONT, 14, 5, HULK)
    for x in (16, 20, 24):
        ic.fill(line(x, 22, x, 24), PALE[0])
    ic.sparkle(27, 4, big=False)
    return ic.image()
