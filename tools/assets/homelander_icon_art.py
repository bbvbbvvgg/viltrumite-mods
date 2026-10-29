"""Homelander ability icons, drawn in the house style of the Reinhard icons (icon_art.py).

Homelander's identity inside the shared grammar: a Vought royal-blue frame, the navy suit with
gold eagle shoulders, the red flag cape, maroon gloves with a gold band, combed blond hair and
blue eyes that burn red for Heat Vision. White shock rings stand for his sonic force (punch,
clap, roar), red only for the lasers and the cape. The two flight-bar icons (Fast Take-Off,
Cruise Flight) replace the generic Viltrumite figure on his flight bar.
Original deterministic pixel art; imported by make_hero_icons.py.
"""
import math

from icon_art import *  # noqa: F401,F403 (house kit: palette, canvas, masks, strokes, sprites)
from icon_art import registry, to64

ICONS, art = registry()

# Panel order (punch, dash, lasers, focus, roar, thunderclap), then the two flight-bar slots.
NAMES = ["punch", "dash", "lasers", "focus", "roar", "thunderclap", "fast_takeoff", "supersonic_flight"]
FOLDER = "textures/gui/ability/homelander/"
PANEL = "default"
# HUD mock: page 1 = HomelanderAbilities.LOADOUT; flight bar = speed lock (not owned), take-off, cruise.
HUD = [["punch", "dash", "lasers", "focus", "roar", "thunderclap"]]
FLIGHT = (None, "fast_takeoff", "supersonic_flight")


def icon32(name):
    return ICONS[name]()


def icon(name):
    return to64(icon32(name))


# ---------------------------------------------------------------- Homelander palette
NAVY = [hx(c) for c in ("010c2a", "02204e", "0a3a7a", "3e5fc4")]
EAGLE = [hx(c) for c in ("6b5a2e", "a08a4c", "d9b95c", "f6e39a")]
GLOVE = [hx(c) for c in ("3a0412", "5e0a1f", "8a1630", "b8324a")]
CAPE = [hx(c) for c in ("6b0f1a", "a01f2b", "d0343f", "f0f0f0")]
HAIR_H = [hx(c) for c in ("6e5634", "a08656", "cdb47c", "eedc9e")]
SKIN_H = [hx(c) for c in ("8a5444", "c48e78", "e3b39c", "f6d6c4")]
EYE = hx("4f8fba")
LASER = [hx(c) for c in ("891e2b", "e0202c", "ff5a4a", "ffd0c0")]
FIRE = [hx(c) for c in ("c42430", "f77622", "feae34", "fee761")]
BRICK = [hx(c) for c in ("391f21", "5d2c28", "8a4836", "b86f50")]
WALL = [hx(c) for c in ("272736", "3e3b55", "5a5779", "7d7a9c")]

SUIT = {"y": HAIR_H[2], "Y": HAIR_H[3], "z": HAIR_H[1], "s": SKIN_H[2], "S": SKIN_H[3], "t": SKIN_H[1],
        "e": EYE, "E": WHITE, "m": SKIN_H[0], "n": NAVY[1], "N": NAVY[2], "b": NAVY[0], "g": EAGLE[2],
        "G": EAGLE[3], "q": EAGLE[1], "r": CAPE[2], "R": CAPE[1], "w": CAPE[3], "c": GLOVE[2], "C": GLOVE[3],
        "k": GLOVE[0], "o": OUT}

# Bust in three-quarter profile, facing right: combed blond hair, eye, nose, jaw, gold eagle shoulders.
BUST = [
    "....zyyyyz.....",
    "...zyYYYyyyz...",
    "..zyYyyyyyyyz..",
    "..zyyyyyyyyyy..",
    "..zyzyysssyy...",
    "..zzysssSssss..",
    "..zyssssseEss..",
    "...ystsssssss..",
    "...yttssssssss.",
    "....tsssssss...",
    "....ttsssmss...",
    ".....ttssss....",
    "......tss......",
    ".RRgggqtsqggg..",
    "RRgGGggnngGggg.",
    "RqgGgnnNNnngggq",
    "RqgnnnnNNnnnngg",
]
BUST_ROAR = list(BUST)
BUST_ROAR[6] = "..zyssssseEss.."
BUST_ROAR[9] = "....tsssssmmm.."
BUST_ROAR[10] = "....ttsssmoo..."
BUST_ROAR[11] = ".....ttssmoo..."
BUST_ROAR[12] = "......tssmm...."

# Flying horizontally, fist first, cape streaming back (facing right): see flyer().
FLYER_OLD = [
    "rrw..............",
    ".rrrw............",
    "..rrrrw...yyyz...",
    "...wrrrr.yYyyyz..",
    "nnnnnnnnnyysssccC",
    "nNNnnnNggnnsSsccc",
    "bbbnnnnnngqnnncck",
    "...bbbb..........",
]

# Rising straight up, fist overhead, cape below (front view).
RISER = [
    ".cC......",
    "cccC.....",
    "ccck.....",
    ".gG......",
    ".nN.zyyz.",
    ".nNzyYyyz",
    ".nNysSssy",
    ".nnseEsEy",
    ".nnsssss.",
    ".nn.tss..",
    "gnnnnnngg",
    "gGnNNnnGg",
    "rnnNNnnnr",
    "rnnnnnncr",
    "rrnnnnnrr",
    ".rnn.nnr.",
    ".rnn.nnr.",
    ".rbb.bbr.",
    "..rw.wr..",
]

FIST = [
    "..CCCCCCC...",
    ".CcccccccCC.",
    "CcccccccccCk",
    "cccccccccckc",
    "ccckkkkkkkck",
    "cccCCCCCCkcc",
    "cccccccckckk",
    "ccckkkkkkckc",
    ".cccccccccck",
    "..kkkkkkkkk.",
]

GLOVE_L = [   # open gloved hand from the side, fingers up, palm facing right, thumb raised
    ".C.C.C...",
    "CcCcCcC..",
    "ckckckc..",
    "ckckckc..",
    "ccccccc.C",
    "cccccccCc",
    "ccccccccc",
    "cccccccck",
    "ccccccck.",
    ".ccccckk.",
    ".ggggggg.",
    ".GGGGGGG.",
    ".nnnnnnn.",
    ".nNnnnnn.",
    ".nNnnnnn.",
]


FIST_MASK = [
    "...xxxxxx...",
    "..xxxxxxxxx.",
    ".xxxxxxxxxxx",
    "xxxxxxxxxxxx",
    "xxxxxxxxxxxx",
    "xxxxxxxxxxxx",
    "xxxxxxxxxxxx",
    "xxxxxxxxxxxx",
    ".xxxxxxxxxx.",
    "..xxxxxxxx..",
]


def glove_fist(ic, x, y):
    """Maroon gloved fist in side view, thumb on top, knuckles facing right."""
    m = {(x + a, y + b) for b, r in enumerate(FIST_MASK) for a, ch in enumerate(r) if ch == "x"}
    ic.solid(m, (GLOVE[0], GLOVE[2], GLOVE[3]))
    # thumb across the top, finger joints at the front, lit knuckles
    ic.fill(line(x + 2, y + 4, x + 8, y + 4), GLOVE[0])
    ic.fill(line(x + 3, y + 3, x + 8, y + 3), GLOVE[3])
    ic.set(x + 9, y + 3, GLOVE[0])
    for yy in (y + 6, y + 8):
        ic.fill(line(x + 7, yy, x + 10, yy), GLOVE[0])
    for yy in (y + 5, y + 7):
        ic.set(x + 11, yy, GLOVE[3])
    ic.set(x + 10, y + 2, GLOVE[3])
    return m


def flyer(ic, x, y):
    """Homelander flying to the right, fist first: the take-off figure turned a quarter."""
    return sprite(ic, RISER, x, y, SUIT, rot=1, flipy=True)


def shade_glove(ic, m):
    """Lit rim on gloved shapes (maroon), so the hand reads against navy and charcoal."""
    for (x, y) in m:
        c = ic.get(x, y)
        if c in (GLOVE[2],) and ((x - 1, y) not in m or (x, y - 1) not in m):
            ic.set(x, y, GLOVE[3])


def sonic_rings(ic, cx, cy, radii, a0, a1, cols=(PALE[3], PALE[1], PALE[0]), w=1.2):
    for i, r in enumerate(radii):
        ic.fill(arc(cx, cy, r, w, a0, a1), cols[min(i, len(cols) - 1)])


def brick_wall(ic, x0, y0, x1, y1, broken=()):
    m = rect(x0, y0, x1, y1) - set(broken)
    ic.fill(m, BRICK[2])
    for (x, y) in m:
        row = (y - y0) // 3
        if (y - y0) % 3 == 2:
            ic.set(x, y, BRICK[0])
        elif (x - x0 + (2 if row % 2 else 0)) % 5 == 4:
            ic.set(x, y, BRICK[0])
        elif (y - y0) % 3 == 0:
            ic.set(x, y, BRICK[3])
    return m


def chunk(ic, x, y, s=2, ramp=BRICK):
    m = rect(x, y, x + s - 1, y + s - 1)
    ic.fill(grow(m) - m, OUT)
    ic.fill(m, ramp[2])
    ic.set(x, y, ramp[3])
    if s > 1:
        ic.set(x + s - 1, y + s - 1, ramp[1])


# ---------------------------------------------------------------- icons
@art
def punch():
    """Sonic Punch: the gloved fist sends a cone of shock rings ahead, tearing blocks apart."""
    ic = Icon("homelander", center=(17, 15))
    cx, cy = 12, 15
    for i, r in enumerate((7, 11, 15)):
        ic.mix(arc(cx, cy, r, 2.4, -32, 32), PALE[3], 0.18)
    sonic_rings(ic, cx, cy, (8, 12, 16), -34, 34)
    ic.fill(line(cx, cy, 29, cy - 11), PALE[0])
    ic.fill(line(cx, cy, 29, cy + 11), PALE[0])
    for (x, y, s) in ((24, 8, 3), (27, 13, 2), (25, 19, 3), (21, 23, 2), (28, 22, 2)):
        chunk(ic, x, y, s, ramp=WALL)
    # sleeve and fist from the left
    sl = rect(2, 11, 6, 19)
    ic.solid(sl, (NAVY[0], NAVY[1], NAVY[2]))
    ic.fill(rect(5, 11, 6, 19), EAGLE[2])
    ic.fill(rect(5, 11, 6, 11), EAGLE[3])
    ic.fill(rect(5, 19, 6, 19), EAGLE[0])
    glove_fist(ic, 7, 10)
    for y in (7, 23):
        speed_lines(ic, [(y, 3, 9)])
    ic.sparkle(25, 4)
    return ic.image()


@art
def dash():
    """Dash: Homelander flies fist-first through a brick wall, bricks bursting out of it."""
    ic = Icon("homelander", center=(15, 16))
    hole = disc(23, 16, 4.6)
    brick_wall(ic, 21, 3, 28, 28, broken=hole)
    ic.fill(hole & rect(21, 3, 28, 28), BG[1])
    ic.fill({p for p in grow(hole) - hole if 21 <= p[0] <= 28}, BRICK[0])
    for (x, y, s) in ((26, 7, 2), (28, 11, 2), (27, 22, 2), (25, 25, 2), (29, 17, 2)):
        chunk(ic, x, y, s)
    speed_lines(ic, [(10, 2, 9), (13, 3, 7), (19, 2, 8), (22, 4, 9)])
    flyer(ic, 2, 12)
    burst(ic, 23, 16, 3.2, ramp=(PALE[1], PALE[3], WHITE), spikes=7)
    ic.sparkle(8, 5)
    return ic.image()


@art
def lasers():
    """Heat Vision: two red beams out of his eyes scorch the ground and set it alight."""
    ic = Icon("homelander", center=(11, 14))
    ground = 25
    ic.fill(rect(2, ground, 29, 29), BG[2])
    ic.fill(rect(2, ground, 29, ground), BG[4])
    sprite(ic, BUST, 1, 5, dict(SUIT, e=LASER[1], E=LASER[3]))
    # the two beams: eye -> scorch, white core, red halo
    for (ay, by) in ((11, 24), (12, 26)):
        beam = line(12, ay, 26, by)
        ic.mix(grow(beam, True) - beam, LASER[1], 0.75)
    for (ay, by) in ((11, 24), (12, 26)):
        beam = line(12, ay, 26, by)
        ic.fill(beam, LASER[3])
    ic.fill(line(12, 11, 18, 16), WHITE)
    # scorch and flames where they land
    ic.fill({(x, ground) for x in range(21, 30)}, INK)
    for (x, h) in ((22, 3), (24, 5), (26, 4), (28, 2)):
        for k in range(h):
            ic.set(x, ground - k, FIRE[min(3, k)] if k < h - 1 else FIRE[3])
            ic.set(x + 1, ground - k + 1, FIRE[1])
    ic.fill(disc(12, 11, 1.2), LASER[3])
    ic.set(12, 11, WHITE)
    ic.sparkle(26, 6)
    return ic.image()


@art
def focus():
    """Focus: his eyes see through the wall; the creatures behind it glow in coloured outlines, a
    dotted path leads to the nearest one."""
    ic = Icon("homelander", center=(16, 18))
    # the wall, dimmed: x-ray view
    wall = rect(2, 12, 29, 29)
    brick_wall(ic, 2, 12, 29, 29)
    ic.mix(wall, BG[1], 0.55)
    # creatures behind it, outlined in tracking colours (never red)
    for (x, y, col) in ((5, 14, hx("5ac54f")), (18, 15, hx("0cf1ff"))):
        tmp = Icon("homelander", bg=False)
        m = sprite(tmp, FOE, x, y, {"o": ENEMY[2], "r": col}, outline=None)
        ic.fill(grow(m, True) - m, col)
        ic.mix(m & ic.inside, BG[0], 0.65)
        for (a, b) in m:
            if tmp.px.get((a, b)) == col:
                ic.set(a, b, col)
    for x in range(9, 22, 2):
        ic.set(x, 29 - (x - 9) // 4, PALE[3])
    # his eyes over the wall: hair, brows, glowing blue irises
    band = rect(2, 2, 29, 10)
    ic.fill(band, SKIN_H[2])
    ic.fill(rect(2, 2, 29, 3), HAIR_H[2])
    for x in range(2, 30):
        if x % 4 == 1:
            ic.set(x, 3, HAIR_H[3])
        if x % 5 == 2:
            ic.set(x, 4, HAIR_H[1])
    ic.fill(rect(2, 9, 29, 10), SKIN_H[1])
    for ex in (6, 19):
        ic.fill(rect(ex - 1, 5, ex + 6, 5), HAIR_H[0])
        eye = rect(ex, 6, ex + 5, 8)
        ic.fill(eye, WHITE)
        ic.fill(rect(ex + 1, 6, ex + 4, 8), hx("0cf1ff"))
        ic.fill(rect(ex + 2, 6, ex + 3, 6), hx("94fdff"))
        ic.fill(rect(ex + 2, 7, ex + 3, 8), OUT)
        ic.set(ex + 2, 7, WHITE)
        ic.fill(rect(ex, 9, ex + 5, 9), SKIN_H[0])
    ic.fill(rect(15, 6, 16, 10), SKIN_H[1])
    ic.set(15, 6, SKIN_H[3])
    ic.fill(rect(2, 11, 29, 11), OUT)
    for x in (8, 10, 21, 23):
        ic.fill(line(x, 12, x + (x - 15) * 0.15, 15), hx("94fdff"))
    return ic.image()


@art
def roar():
    """Roar: a cone of shock rings out of his open mouth hurls the foe back."""
    ic = Icon("homelander", center=(15, 15))
    cx, cy = 11, 17
    for r in (5, 8.5, 12):
        ic.mix(arc(cx, cy, r, 3.0, -42, 42), PALE[3], 0.14)
    sonic_rings(ic, cx, cy, (5, 8.5), -42, 42, cols=(WHITE, PALE[2]), w=1.6)
    foe(ic, 21, 9, rot=0)
    ic.mix(arc(cx, cy, 12.5, 1.6, -40, 40), WHITE, 0.55)
    for (x, y) in ((29, 10), (29, 15), (29, 20)):
        ic.fill(line(x - 1, y, x, y), PALE[1])
    sprite(ic, BUST_ROAR, 0, 7, SUIT)
    ic.sparkle(25, 26, big=False)
    ic.sparkle(5, 4, big=False)
    return ic.image()


@art
def thunderclap():
    """Thunderclap: the gloves clap together; the shockwave cone rips the ground and throws
    the debris up."""
    ic = Icon("homelander", center=(16, 13))
    ground = 24
    ic.fill(rect(2, ground, 29, 29), BG[3])
    ic.fill(rect(2, ground, 29, ground), PALE[0])
    ic.fill(rect(2, ground + 1, 29, ground + 1), BG[4])
    for pts in (((16, 24), (13, 27), (14, 29)), ((16, 24), (20, 26), (22, 29)), ((15, 24), (8, 26), (5, 29)),
                ((17, 24), (25, 25), (28, 28))):
        ic.fill(polyline(pts), INK)
    sonic_rings(ic, 16, 12, (6, 9.5, 13), 0, 360, cols=(WHITE, PALE[2], PALE[0]), w=1.1)
    for (x, y, s) in ((5, 18, 2), (25, 17, 2), (9, 21, 2), (22, 21, 2), (27, 21, 2)):
        chunk(ic, x, y, s, ramp=ROCK)
    sprite(ic, GLOVE_L, 6, 4, SUIT)
    sprite(ic, GLOVE_L, 17, 4, SUIT, flipx=True)
    burst(ic, 16, 9, 3.4, ramp=(PALE[1], PALE[3], WHITE), spikes=8)
    return ic.image()


@art
def fast_takeoff():
    """Fast Take-Off: launched straight up off the ground, a shock ring left behind."""
    ic = Icon("homelander", center=(16, 12))
    ground = 25
    ic.fill(rect(2, ground, 29, 29), BG[3])
    ic.fill(rect(2, ground, 29, ground), PALE[0])
    ic.fill(ring_pts(16, ground, 11, 2.6, 180, 360), PALE[3])
    ic.fill(ring_pts(16, ground, 7, 1.6, 180, 360), PALE[1])
    for x, (y0, y1) in ((7, (6, 16)), (11, (10, 21)), (21, (10, 21)), (25, (6, 16))):
        for y in range(y0, y1 + 1):
            t = (y - y0) / max(1, y1 - y0)
            ic.set(x, y, PALE[3] if t > 0.66 else PALE[1] if t > 0.33 else PALE[0])
    sprite(ic, RISER, 11, 3, SUIT)
    ic.sparkle(26, 5, big=False)
    return ic.image()


@art
def supersonic_flight():
    """Cruise Flight: level flight at speed above the clouds, a vapour cone at his fist."""
    ic = Icon("homelander", bg=False)
    sky = [hx("1a1932"), hx("2a2f4e"), hx("3c5e8b"), hx("4f8fba")]
    for (x, y) in ic.inside:
        ic.px[(x, y)] = sky[min(3, y // 8)]
    for (cx, cy, rx) in ((6, 27, 7), (18, 29, 8), (28, 26, 6)):
        cl = {(x, y) for x in range(32) for y in range(32) if ((x - cx) / rx) ** 2 + ((y - cy) / 3.2) ** 2 <= 1}
        ic.fill(cl, PALE[1])
        ic.fill({(x, y) for (x, y) in cl if (x, y - 1) not in cl}, WHITE)
    for (x, y) in ((5, 5), (13, 3), (27, 8), (22, 4)):
        ic.set(x, y, PALE[2])
    speed_lines(ic, [(11, 2, 9), (14, 2, 6), (19, 2, 8)], cols=(STEEL[2], STEEL[3], WHITE))
    for (ax, ay) in ((17, 7), (17, 24)):
        ic.mix(line(26, 15.5, ax, ay), WHITE, 0.45)
    for (r, c) in ((4, WHITE), (7, PALE[2])):
        ic.fill(arc(26, 15.5, r, 1.1, 120, 240, ry=r * 1.15), c)
    flyer(ic, 4, 11)
    return ic.image()
