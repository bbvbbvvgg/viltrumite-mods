"""Regulus ability icons, drawn in the house style of the Reinhard icons (icon_art.py).

Regulus keeps his own identity inside the shared grammar: an ivory-to-bronze frame (the
white king in gold), white-lavender hair, amber eyes, a white coat with gold trim, gold
for his authority (tether, dome, crown) and crimson only for hearts, stored damage and
madness. Counter exists only during madness, so its frame is blood red.
Original deterministic pixel art; imported by make_hero_icons.py.
"""
import math

from icon_art import *  # noqa: F401,F403 (house kit: palette, canvas, masks, strokes, sprites)
from icon_art import registry, to64

ICONS, art = registry()

# Regulus Reborn: the six slotted powers first (RegulusAbilities.slotIds), then the two unslotted
# ones (Greed's Touch on LMB, Little King on the heart key). The first kit's art stays below for
# history but is no longer exported.
NAMES = ["lions_heart", "stopped_sand", "air_severance", "trample", "right_of_reply", "kingdom_of_one",
         "greeds_touch", "little_king"]
# Asset folder under assets/viltrumitecore/ (the ids keep their existing paths).
FOLDER = "textures/gui/ability/regulus/"
PANEL = "default"
# HUD mock: panel pages (RegulusAbilities.slotIds) and the three flight-bar slots (none owned).
HUD = [NAMES[:6]]
FLIGHT = (None, None, None)


def icon32(name):
    return ICONS[name]()


def icon(name):
    return to64(icon32(name))


# ---------------------------------------------------------------- Regulus palette
HAIR_R = [hx(c) for c in ("8b8aa8", "b3b0cc", "d9d4e6", "f4f2fa")]      # white-lavender hair
HAIR_MAD = [hx(c) for c in ("5d5d6e", "7d7c90", "a19fb3", "c9c7d6")]    # madness: greyed
AMBER = hx("f79617")
SKIN_R = [hx(c) for c in ("bf6f4a", "e69c69", "f6ca9f", "f9e6cf")]
COAT = [hx(c) for c in ("92a1b9", "cad0d7", "f0eef2", "ffffff")]
GOLD_R = [hx(c) for c in ("8f6c34", "c9a35c", "e8cf98", "fff3c4")]   # the frame's bronze-gold
HEART = [hx(c) for c in ("571c27", "a00810", "e0202c", "f5555d", "fdd2ed")]
ICE = [hx(c) for c in ("657392", "92a1b9", "c7cfdd", "ffffff")]
DIRT = [hx(c) for c in ("5d2c28", "8a4836", "bf6f4a", "e69c69")]
GRASS = [hx(c) for c in ("1e6f50", "33984b", "5ac54f", "99e65f")]
STONE = [hx(c) for c in ("2a2f4e", "424c6e", "657392", "92a1b9", "c7cfdd")]

FIG = {"h": HAIR_R[2], "H": HAIR_R[3], "j": HAIR_R[1], "f": SKIN_R[3], "F": SKIN_R[2], "e": AMBER,
       "w": COAT[2], "W": COAT[3], "v": COAT[1], "g": GOLD_R[1], "G": GOLD_R[2], "p": COAT[1], "k": ROCK[1]}
FIG_MAD = dict(FIG, h=HAIR_MAD[2], H=HAIR_MAD[3], j=HAIR_MAD[1], e=BLOOD[3], w=COAT[1], v=COAT[0],
               W=COAT[2], p=COAT[0])

SKIN_PAL = {"h": SKIN_R[3], "l": SKIN_R[2], "m": SKIN_R[1], "s": SKIN_R[0]}

# Regulus kicking the ground: planted on the left leg, right leg driven forward and down.
KICK = [
    "..jhhhj.....",
    ".jhHHhhj....",
    "jhHhhhhhj...",
    "jhfffeFhj...",
    ".jfffFfj....",
    "...fF.......",
    "..wwWww.....",
    ".vwgWwgw....",
    "fv.wGwwvf...",
    "...wgwwv....",
    "...wwwwpp...",
    "...pp..ppp..",
    "...pp...ppp.",
    "...pp....pkk",
    "...pp.....kk",
    "..kkk.......",
]

# Regulus in madness, airborne, both fists hammered down (used upside up, falling on the foe).
SLAMMER = [
    "..jhhhj..",
    ".jhHhhhj.",
    "jhHhhhhhj",
    "jhffeffej",
    ".jfffffj.",
    "..wwWww..",
    "ffwwgwwff",
    "ffwWGwwff",
    "..wwgww..",
    "..wwwww..",
    "..pp.pp..",
    ".pp...pp.",
    ".k.....k.",
]

GRIP = [
    ".hhlhhlhhl.",
    "hllmllmllmh",
    "lllmllmllml",
    "mmmmmmmmmml",
    "hhhhhhhlllm",
    "lllllllmllm",
    "smmmmmmmllm",
    ".llllllllm.",
    "..mmmmmmm..",
]

SMALL_HEART = [
    ".x.x.",
    "xxxxx",
    "xxxxx",
    ".xxx.",
    "..x..",
]

BIG_HEART = [
    "..xxx...xxx..",
    ".xxxxx.xxxxx.",
    "xxxxxxxxxxxxx",
    "xxxxxxxxxxxxx",
    "xxxxxxxxxxxxx",
    ".xxxxxxxxxxx.",
    "..xxxxxxxxx..",
    "...xxxxxxx...",
    "....xxxxx....",
    ".....xxx.....",
    "......x......",
]

CROWN = [
    "x..x..x",
    "xx.x.xx",
    "xxxxxxx",
    "xxxxxxx",
]


def rows_mask(rows, x, y):
    return {(x + a, y + b) for b, r in enumerate(rows) for a, ch in enumerate(r) if ch not in ". "}


def oriented(cx, cy, ang, test):
    """Pixels whose (along, across) coordinates in a frame turned by ang degrees pass test(a, p)."""
    ux, uy = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    out = set()
    for x in range(32):
        for y in range(32):
            dx, dy = x - cx, y - cy
            if test(dx * ux + dy * uy, -dx * uy + dy * ux):
                out.add((x, y))
    return out


def heart(ic, rows, x, y, ramp, outline=OUT):
    """Shaded heart: dark lower-right, bright upper-left lobe and a white glint."""
    m = rows_mask(rows, x, y)
    ic.solid(m, (ramp[0], ramp[1], ramp[2]), outline=outline)
    w = max(len(r) for r in rows)
    if w >= 9:
        ic.fill({(x + 2, y + 2), (x + 3, y + 1), (x + 2, y + 3)}, ramp[3])
        ic.set(x + 2, y + 1, ramp[4] if len(ramp) > 4 else WHITE)
    else:
        ic.set(x + 1, y + 1, ramp[-1])
    return m


def gold_heart(ic, x, y):
    heart(ic, SMALL_HEART, x, y, (GOLD[0], GOLD[2], GOLD[3], WHITE))


def block(ic, x, y, s, top=GRASS, side=DIRT, cracked=False):
    """A small earth block seen slightly from above: grass cap, dirt body, outline."""
    m = rect(x, y, x + s - 1, y + s - 1)
    ic.solid(m, (side[1], side[2], side[3]))
    ic.fill(rect(x, y, x + s - 1, y + max(0, s // 3 - 1)), top[2])
    ic.fill(rect(x, y, x + s - 1, y), top[3])
    for k in range(x, x + s, 2):
        ic.set(k, y + s // 3, top[1])
    ic.set(x + s - 1, y + s - 1, side[0])
    if cracked:
        ic.set(x + s // 2, y + s // 2, side[0])
    return m


def coat_sleeve(ic, mask, cuff):
    ic.solid(mask, (COAT[0], COAT[2], COAT[3]))
    ic.fill(cuff, GOLD_R[1])
    for (x, y) in cuff:
        if (x - 1, y) not in cuff or (x, y - 1) not in cuff:
            ic.set(x, y, GOLD_R[2])
        elif (x + 1, y) not in cuff or (x, y + 1) not in cuff:
            ic.set(x, y, GOLD_R[0])


# ---------------------------------------------------------------- icons
@art
def lions_heart():
    """The Little King stops his own heart: a still heart above a flatlined pulse, inside the
    white-gold boundary of stopped time, with two arrows frozen grey on its shell."""
    ic = Icon("regulus", center=(16, 15))
    cx, cy, r = 16, 16, 11.5
    ball = disc(cx, cy, r)
    ic.mix(ball, GOLD_R[2], 0.14)
    ic.mix(disc(cx - 4, cy - 4, 5.5), WHITE, 0.10)
    ic.fill(arc(cx, cy, r, 1.1, 0, 360), GOLD_R[1])
    ic.fill(arc(cx, cy, r, 1.1, 190, 290), GOLD_R[3])
    ic.fill(arc(cx, cy, r, 1.1, 20, 80), GOLD_R[0])
    ic.fill(arc(cx, cy, r - 2.2, 1, 205, 250), WHITE)
    for a in (35, 110, 165, 300):
        rr = math.radians(a)
        ic.set(int(round(cx + math.cos(rr) * (r + 1.6))), int(round(cy + math.sin(rr) * (r + 1.6))), GOLD_R[2])
    # frozen arrows: stopped on the shell, drained to ice grey
    arrow(ic, 2, 29, 7, 24, col=ICE[1], head=ICE[3], fletch=ICE[2])
    arrow(ic, 30, 6, 25, 10, col=ICE[1], head=ICE[3], fletch=ICE[2])
    # the pulse: one last beat, then a flat line
    beat = polyline([(8, 23), (9, 23), (10, 19), (12, 27), (13, 23)])
    flat = line(14, 23, 26, 23)
    ic.fill(line(6, 23, 8, 23), GOLD[1])
    ic.fill(beat, WHITE)
    ic.fill(flat, GOLD[3])
    ic.fill(line(20, 23, 24, 23), GOLD[1])
    ic.fill(line(25, 23, 26, 23), GOLD_R[0])
    heart(ic, BIG_HEART, 10, 8, HEART)
    ic.sparkle(25, 26, big=False)
    ic.sparkle(6, 7, big=False)
    return ic.image()


@art
def debris_kick():
    """Regulus kicks the ground; the torn-out earth flies forward as blocks with a fan of shards."""
    ic = Icon("regulus", center=(17, 16))
    ground = 25
    ic.fill(rect(2, ground, 29, 29), BG[3])
    ic.fill(rect(2, ground, 29, ground), PALE[0])
    ic.fill(rect(2, ground + 1, 29, ground + 1), BG[4])
    pit = {(x, y) for x in range(32) for y in range(ground, ground + 4)
           if ((x - 17) / 5.5) ** 2 + ((y - ground) / 2.8) ** 2 <= 1}
    ic.fill(pit, INK)
    ic.fill({(x, ground) for x in range(12, 23)}, BG[1])
    for pts in (((12, 26), (9, 28), (7, 28)), ((22, 26), (25, 28), (27, 28))):
        ic.fill(polyline(pts), INK)
    # the swing of the kick, a white arc scooping up out of the pit
    sw = stroke([(9, 26), (15, 27), (19, 21)], 0.4, 2.2, 0.8)
    paint_stroke(ic, sw, (PALE[0], PALE[2], WHITE))
    # shard spray: streaks fanning up and right out of the pit
    for (ax, ay, bx, by) in ((20, 21, 28, 17), (21, 22, 29, 22), (19, 19, 22, 14)):
        ic.fill(line(ax, ay, bx - (bx - ax) * 0.3, by - (by - ay) * 0.3), BG[4])
        chip = {(bx, by), (bx + 1, by), (bx, by + 1), (bx + 1, by + 1)}
        ic.fill(grow(chip) - chip, OUT)
        ic.fill(chip, DIRT[2])
        ic.set(bx, by, DIRT[3])
        ic.set(bx + 1, by + 1, DIRT[1])
    # the hurled blocks
    block(ic, 18, 4, 5)
    block(ic, 25, 7, 4)
    block(ic, 14, 11, 4)
    sprite(ic, KICK, 3, 9, FIG)
    ic.sparkle(10, 4)
    return ic.image()


@art
def mania():
    """Regulus' fist grips a gold tether and reels the foe in; caught, it is held gold-outlined."""
    ic = Icon("regulus", center=(18, 15))
    st = stroke([(9, 17), (16, 15), (22, 15)], 1.6, 1.8, 1.6)
    m = set(st)
    ic.mix(grow(grow(m, True), True) - grow(m, True), GOLD_R[0], 0.5)
    ic.fill(grow(m, True) - m, GOLD[1])
    ic.fill(m, GOLD[3])
    for (x, y) in m:
        if x % 3 == 0:
            ic.set(x, y, GOLD[2])
    ic.fill({p for p, d in st.items() if d < 0.3 and p[0] % 3 != 0}, WHITE)
    # pull chevrons pointing back toward the fist
    for x0 in (14, 18):
        ic.fill({(x0 + 1, 9), (x0, 10), (x0 + 1, 11)}, GOLD[2])
        ic.fill({(x0 + 1, 19), (x0, 20), (x0 + 1, 21)}, GOLD[2])
    # caught foe, leaning in, gold outline of the hold
    fm = sprite(ic, FOE, 21, 9, {"o": ENEMY[2], "r": BLOOD[3]}, outline=GOLD[2])
    for (a, b) in fm:
        if (a - 1, b) not in fm or (a, b - 1) not in fm:
            ic.set(a, b, ENEMY[3])
    ic.fill(grow(fm, True) - grow(fm) - fm, GOLD_R[0])
    coat_sleeve(ic, rect(3, 21, 10, 29), rect(3, 21, 10, 22))
    sprite(ic, GRIP, 2, 12, SKIN_PAL)
    ic.sparkle(26, 25)
    ic.sparkle(7, 6, big=False)
    return ic.image()


@art
def greeds_embrace():
    """A dome of gold points shuts over the ground; the foe inside stands locked while every hit on
    it hangs in the air as a red shard, stored to be paid back when the dome lifts."""
    ic = Icon("regulus", center=(16, 18))
    ground = 25
    ic.fill(rect(2, ground + 1, 29, 29), BG[2])
    ic.fill(rect(2, ground + 1, 29, ground + 1), BG[4])
    cx, cy, rx, ry = 16, ground, 12.5, 19.5
    dome = {(x, y) for x in range(32) for y in range(32) if y <= ground and ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1}
    ic.mix(dome, GOLD_R[1], 0.18)

    def at(lat, lon, squash=1.6):
        c = math.cos(math.radians(lat))
        return (int(round(cx + rx * c * math.cos(math.radians(lon)))),
                int(round(cy - ry * math.sin(math.radians(lat)) + squash * c * math.sin(math.radians(lon)))))
    # inner lattice, faint: one parallel and two meridians
    inner = {at(34, 180 * i / 10) for i in range(1, 10)}
    for lon in (62, 118):
        inner |= {at(lat, lon) for lat in range(10, 80, 12)}
    ic.fill(inner, GOLD_R[1])
    # the shell: bright points every few degrees, lit up-left
    shell = {(int(round(cx + rx * math.cos(math.radians(a)))), int(round(cy + ry * math.sin(math.radians(a)))))
             for a in range(184, 357, 9)}
    shell.add((cx, int(round(cy - ry))))
    for (x, y) in shell:
        ic.set(x, y, WHITE if x < cx - 3 else GOLD[3] if x < cx + 5 else GOLD[2])
    for x in range(4, 29, 2):
        ic.set(x, ground, GOLD[2])
    # the locked victim and its stored hits
    foe(ic, 12, 11)
    for (x, y) in ((7, 15), (22, 11), (22, 19), (8, 22)):
        sh = {(x, y), (x + 1, y - 1), (x + 1, y), (x + 2, y - 2)}
        ic.fill(grow(sh) - sh, OUT)
        ic.fill(sh, BLOOD[3])
        ic.set(x + 2, y - 2, BLOOD[4])
    ic.sparkle(27, 4, big=False)
    return ic.image()


@art
def counter():
    """Madness only: the foe went up on the uppercut (white trail), Regulus appeared behind it in a
    white flash and his fist hammers it flat into a crater."""
    ic = Icon("regulus_madness", center=(16, 14))
    ground = 24
    ic.fill(rect(2, ground, 29, 29), BG[3])
    ic.fill(rect(2, ground, 29, ground), PALE[0])
    ic.fill(rect(2, ground + 1, 29, ground + 1), BG[4])
    crater = {(x, y) for x in range(32) for y in range(ground - 1, 30)
              if ((x - 17) / 9.5) ** 2 + ((y - ground + 1) / 4.5) ** 2 <= 1}
    ic.fill(crater, INK)
    ic.fill({p for p in crater if p[1] >= ground + 2 and ((p[0] - 17) / 7) ** 2 + ((p[1] - ground - 1) / 2.5) ** 2 > 1}, BG[1])
    for pts in (((8, 26), (5, 28), (3, 27)), ((26, 26), (29, 28))):
        ic.fill(polyline(pts), INK)
    # the foe, flattened on its back in the crater
    ic.fill(ring_pts(17, ground, 12, 2.4, 185, 355), BLOOD[3])
    foe(ic, 10, 18, rot=1)
    for (x, y) in ((6, 19), (4, 21), (27, 18), (29, 21)):
        ic.fill({(x, y), (x, y + 1)}, PALE[0])
    # the hammer fist from above, crimson streaks of the dive
    ang = 90
    cxf, cyf = 17, 10
    fist = oriented(cxf, cyf, ang, lambda a, p: (abs(a) / 3.8) ** 4 + (abs(p) / 4.4) ** 4 <= 1)
    sleeve = oriented(cxf, cyf, ang, lambda a, p: -12 <= a <= -3.4 and abs(p) <= 3.2)
    cuff = oriented(cxf, cyf, ang, lambda a, p: -5.2 <= a <= -3.4 and abs(p) <= 3.2)
    for x, y0 in ((11, 2), (23, 3), (13, 0), (21, 0)):
        ic.fill(line(x, y0, x, y0 + 5), BLOOD[2] if x in (13, 21) else BLOOD[3])
        ic.set(x, y0 + 5, BLOOD[4])
    ic.solid(sleeve, (COAT[0], COAT[1], COAT[2]))
    ic.fill(cuff, GOLD_R[1])
    ic.fill({(x, y) for (x, y) in cuff if (x, y - 1) not in cuff}, GOLD_R[2])
    ic.solid(fist, (SKIN_R[1], SKIN_R[2], SKIN_R[3]))
    for p0 in (-2.2, 0, 2.2):
        ic.fill(oriented(cxf, cyf, ang, lambda a, p, p0=p0: 1.2 <= a <= 3.8 and abs(p - p0) <= 0.45), SKIN_R[0])
    ic.fill({(15, 3), (16, 2), (19, 4)}, BLOOD[2])
    for (x, y) in ((13, 16), (17, 15), (21, 16)):
        ic.fill(line(x, y, x + (x - 17) * 0.25, y + 1), WHITE)
    # the white flash he came out of, and the rising trail of the uppercut
    burst(ic, 26, 8, 3.4, ramp=(PALE[1], PALE[3], WHITE), spikes=6, rot=15)
    up = stroke([(5, 22), (4, 14), (7, 5)], 0.6, 1.8, 1.0)
    paint_stroke(ic, up, (PALE[0], PALE[2], WHITE))
    ic.fill({(6, 4), (7, 3), (8, 4), (7, 4)}, WHITE)
    ic.fill({(5, 5), (9, 5)}, PALE[1])
    return ic.image()


@art
def punch():
    """The Viltrumite-grade fist, ringed by the hearts that empower it, cracks a stone block."""
    ic = Icon("regulus", center=(15, 17))
    # the block, hit on its lower-left edge
    bm = rect(18, 3, 28, 13)
    ic.solid(bm, (STONE[1], STONE[2], STONE[3]))
    for (x, y) in ((20, 5), (24, 4), (26, 8), (21, 10), (25, 11)):
        ic.fill({(x, y), (x + 1, y)}, STONE[1])
        ic.set(x, y - 1, STONE[4])
    for pts in (((19, 12), (22, 9), (22, 6), (25, 5)), ((19, 12), (24, 11), (27, 8)), ((22, 9), (26, 9))):
        ic.fill(polyline(pts), INK)
    for (x, y) in ((29, 15), (27, 16), (16, 3), (14, 6)):
        ic.fill({(x, y), (x, y + 1)}, STONE[3])
    burst(ic, 19, 13, 4.2, ramp=(GOLD[1], GOLD[3], WHITE), spikes=8, rot=22.5)
    # the fist on a 45-degree line, knuckles up-right
    ang = -45
    fist = oriented(13.5, 18.5, ang, lambda a, p: (abs(a) / 4.6) ** 4 + (abs(p) / 4.4) ** 4 <= 1)
    sleeve = oriented(13.5, 18.5, ang, lambda a, p: -12 <= a <= -4.0 and abs(p) <= 3.6)
    cuff = oriented(13.5, 18.5, ang, lambda a, p: -6.2 <= a <= -4.0 and abs(p) <= 3.6)
    coat_sleeve(ic, sleeve, cuff)
    ic.solid(fist, (SKIN_R[1], SKIN_R[2], SKIN_R[3]))
    for p0 in (-1.6, 0.8, 3.0):
        ic.fill(oriented(13.5, 18.5, ang, lambda a, p, p0=p0: 1.2 <= a <= 4.2 and abs(p - p0) <= 0.45), SKIN_R[0])
    thumb = oriented(13.5, 18.5, ang, lambda a, p: -2.5 <= a <= 1.5 and -4.3 <= p <= -2.6)
    ic.fill(thumb, SKIN_R[3])
    ic.fill(oriented(13.5, 18.5, ang, lambda a, p: -2.5 <= a <= 1.5 and -2.6 < p <= -1.9), SKIN_R[1])
    # the hearts that feed it
    gold_heart(ic, 5, 8)
    gold_heart(ic, 23, 22)
    heart(ic, SMALL_HEART, 4, 21, (HEART[1], HEART[2], HEART[3], WHITE))
    ic.sparkle(10, 4, big=False)
    return ic.image()


# ---------------------------------------------------------------- Regulus Reborn
def grain(ic, x, y, col=None, glint=True):
    """One stopped grain: a gold pixel with an outline nub and a white glint above-left."""
    ic.set(x, y, col or GOLD[2])
    ic.set(x + 1, y, GOLD_R[0])
    if glint:
        ic.set(x - 1, y - 1, WHITE)


def crack_star(ic, cx, cy, r, seed=3, col=WHITE, dim=PALE[1]):
    """Hairline crack star: jagged rays (the stopped-time impact language, no fire, no bolts)."""
    import random
    rnd = random.Random(seed)
    for i in range(6):
        a = math.radians(i * 60 + rnd.uniform(-18, 18))
        px, py = cx, cy
        n = int(r)
        for k in range(1, n + 1):
            a += rnd.uniform(-0.35, 0.35)
            nx, ny = cx + math.cos(a) * k, cy + math.sin(a) * k
            ic.fill(line(px, py, nx, ny), col if k < n * 0.6 else dim)
            px, py = nx, ny
    ic.set(cx, cy, WHITE)


def pane(ic, x0, y0, x1, y1):
    """A pane of stopped air: glassy fill, bright top-left rims, ice bottom-right."""
    m = rect(x0, y0, x1, y1)
    ic.mix(m, ICE[2], 0.35)
    ic.fill(line(x0, y0, x1, y0), ICE[3])
    ic.fill(line(x0, y0, x0, y1), ICE[3])
    ic.fill(line(x1, y0, x1, y1), ICE[1])
    ic.fill(line(x0, y1, x1, y1), ICE[1])
    ic.fill(line(x0 + 2, y0 + 3, x0 + 4, y0 + 1), WHITE)


def flick_hand(ic, x, y):
    """A white-gloved hand from the left, index finger flicking right."""
    cuff = rect(x, y + 1, x + 1, y + 5)
    sleeve = rect(x - 4, y, x - 1, y + 6)
    coat_sleeve(ic, sleeve, cuff)
    palm = rect(x + 2, y + 1, x + 5, y + 5)
    ic.solid(palm, (SKIN_R[1], SKIN_R[2], SKIN_R[3]))
    finger = rect(x + 6, y + 1, x + 9, y + 2)
    ic.solid(finger, (SKIN_R[1], SKIN_R[2], SKIN_R[3]))
    thumb = rect(x + 4, y - 1, x + 5, y)
    ic.solid(thumb, (SKIN_R[1], SKIN_R[2], SKIN_R[3]))
    return palm | finger


@art
def stopped_sand():
    """A sweep of the hand throws a handful of dust; every grain stops in the air in a fan, each
    glinting the instant it halted, the foe at the end of the fan pierced by the first ones."""
    ic = Icon("regulus", center=(15, 16))
    flick_hand(ic, 5, 20)
    import random
    rnd = random.Random(11)
    for i in range(16):
        a = math.radians(-58 + i * 4.2 + rnd.uniform(-2, 2))
        d = 6 + rnd.uniform(0, 13) + i * 0.25
        x, y = int(round(14 + math.cos(a) * d)), int(round(21 + math.sin(a) * d))
        # the short travel trail that ends where time stopped the grain
        tx, ty = int(round(14 + math.cos(a) * (d - 3))), int(round(21 + math.sin(a) * (d - 3)))
        ic.mix(line(tx, ty, x, y), GOLD_R[2], 0.35)
        grain(ic, x, y, GOLD[3] if i % 3 == 0 else GOLD[2])
    foe(ic, 21, 5)
    crack_star(ic, 24, 10, 3, seed=5)
    return ic.image()


@art
def air_severance():
    """A flat sheet of stopped air, thin as glass, widening as it flies, the foe cut clean across
    it; behind the sheet the hairline scar it left in the air."""
    ic = Icon("regulus", center=(16, 16))
    sheet = poly([(4, 15), (27, 9), (27, 21), (4, 17)])
    ic.mix(sheet, ICE[2], 0.4)
    ic.fill(line(4, 15, 27, 9), WHITE)
    ic.fill(line(4, 17, 27, 21), ICE[1])
    ic.fill(line(6, 16, 26, 15), ICE[3])
    for x in range(5, 18, 3):
        ic.set(x, 16 + (x % 2), PALE[1])
    top = foe(ic, 19, 3)
    # the cut: a gap through the foe at the sheet's height
    for x in range(17, 30):
        ic.set(x, 13, WHITE)
        ic.set(x, 14, BG[1])
    ic.sparkle(27, 13, big=False, col=ICE[3])
    return ic.image()


@art
def trample():
    """Trample: a white-trousered leg brings the heel down; the ground caves under it and a flat
    gold shock ring runs out across the floor, chips of earth jumping off the crater's rim."""
    ic = Icon("regulus", center=(16, 18))
    ground = 23
    ic.fill(rect(2, ground, 29, 29), BG[3])
    ic.fill(rect(2, ground, 29, ground), PALE[0])
    # the crater and its cracks
    pit = {(x, y) for x in range(32) for y in range(ground, ground + 4)
           if ((x - 16) / 5.0) ** 2 + ((y - ground) / 2.6) ** 2 <= 1}
    ic.fill(pit, INK)
    for pts in (((11, 24), (7, 26), (4, 26)), ((21, 24), (25, 26), (28, 27)), ((14, 26), (12, 29)), ((18, 26), (20, 29))):
        ic.fill(polyline(pts), INK)
    # the shock rings: flat ellipses on the floor, the outer one brighter at its front
    outer = ring_pts(16, ground + 1, 14, 3.4, 0, 360, 2)
    inner = ring_pts(16, ground + 1, 9, 2.2, 0, 360, 3)
    ic.fill({p for p in outer if p[1] >= ground}, GOLD_R[2])
    ic.fill({p for p in outer if p[1] > ground + 2}, GOLD_R[3])
    ic.fill({p for p in outer if p[1] < ground}, GOLD_R[1])
    ic.fill({p for p in inner if p[1] >= ground - 1}, GOLD_R[3])
    # the leg: white trouser down to a gold cuff, a dark boot heel-first into the pit
    leg = poly([(14, 2), (19, 2), (19, 15), (14, 15)])
    ic.solid(leg, [COAT[1], COAT[2], COAT[3], COAT[3]])
    ic.fill(rect(14, 15, 19, 16), GOLD_R[2])
    ic.set(14, 16, GOLD_R[1])
    boot = poly([(14, 17), (19, 17), (19, 23), (9, 23), (9, 21), (14, 20)])
    ic.solid(boot, [STONE[0], STONE[1], STONE[2], STONE[3]])
    # speed streaks above the knee-down
    for x, y0, y1 in ((11, 4, 11), (22, 3, 13), (24, 7, 12)):
        ic.fill(line(x, y0, x, y1), PALE[1])
    # chips of earth jumping off the rim
    for (x, y) in ((5, 17), (26, 15), (8, 13), (24, 19)):
        chip = {(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)}
        ic.fill(grow(chip) - chip, OUT)
        ic.fill(chip, DIRT[2])
        ic.set(x, y, DIRT[3])
    ic.sparkle(27, 22, big=False)
    ic.sparkle(4, 22, big=False)
    return ic.image()


@art
def right_of_reply():
    """Right of Reply: a pane of stopped air takes the foe's blow (the arrow snapped against it),
    and the reply goes back along the same line in gold."""
    ic = Icon("regulus", center=(16, 16))
    pane(ic, 13, 5, 17, 26)
    arrow(ic, 28, 12, 19, 14, col=ICE[1], head=ICE[3], fletch=ICE[2])
    crack_star(ic, 18, 14, 3, seed=2)
    ret = line(12, 20, 3, 23, w=1.2)
    glow_line(ic, ret, core=WHITE, halo=GOLD[2], outer=GOLD_R[1])
    ic.fill(line(4, 21, 3, 23) | line(5, 25, 3, 23), GOLD[3])
    ic.sparkle(24, 24, big=False)
    return ic.image()


@art
def kingdom_of_one():
    """Kingdom of One: a single foe held in a dome of stopped air, five rings of gold grains
    around it, the crown above: one subject, one judge."""
    ic = Icon("regulus", center=(16, 17))
    ground = 26
    ic.fill(rect(2, ground + 1, 29, 29), BG[2])
    ic.fill(rect(2, ground + 1, 29, ground + 1), BG[4])
    cx, cy, rx, ry = 16, ground, 12.0, 17.0
    dome = {(x, y) for x in range(32) for y in range(32) if y <= ground and ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1}
    ic.mix(dome, ICE[2], 0.16)
    for a in range(184, 357, 8):
        x = int(round(cx + rx * math.cos(math.radians(a))))
        y = int(round(cy + ry * math.sin(math.radians(a))))
        ic.set(x, y, ICE[3] if x < cx else ICE[2])
    foe(ic, 12, 12)
    for k, r in enumerate((6.5, 9.0)):
        for i in range(10):
            a = math.radians(i * 36 + k * 18)
            grain(ic, int(round(16 + math.cos(a) * r)), int(round(18 + math.sin(a) * r * 0.45)), glint=i % 2 == 0)
    crown = rows_mask(CROWN, 13, 3)
    ic.solid(crown, (GOLD[0], GOLD[2], GOLD[3]))
    ic.set(13, 3, WHITE)
    return ic.image()


@art
def greeds_touch():
    """Greed's Touch: a bored flick of one finger; where it touched, the air cracks like glass."""
    ic = Icon("regulus", center=(16, 16))
    flick_hand(ic, 6, 14)
    crack_star(ic, 20, 15, 7, seed=4)
    ic.sparkle(26, 7, big=False)
    return ic.image()


@art
def little_king():
    """The Little King: his heart leaves the crown and travels into a subject's chest."""
    ic = Icon("regulus", center=(16, 16))
    crown = rows_mask(CROWN, 4, 6)
    ic.solid(crown, (GOLD[0], GOLD[2], GOLD[3]))
    foe(ic, 19, 11)
    for i, (x, y) in enumerate(((9, 12), (12, 11), (15, 12))):
        ic.set(x, y, GOLD[2] if i % 2 else PALE[2])
    heart(ic, SMALL_HEART, 21, 15, HEART)
    heart(ic, SMALL_HEART, 5, 13, (HEART[0], HEART[2], HEART[3], WHITE))
    return ic.image()
