"""Reinhard ability icons in the style of the Earth status icon (blessing_bearer).

Original deterministic pixel art. Every icon is authored on the same 32x32 grid as
textures/gui/reinhard/blessing_bearer.png (that file is copied from the user
archive and is only read here): its two-pixel bevel frame is reused pixel for
pixel and re-tinted per Reinhard mode (Restrained silver, Knight blue, Holy Sword
gold), the charcoal radial backdrop, navy outlines, top-left ramp shading and
plus-shaped sparkles follow it too. The finished 32x32 image is upscaled 2x
with nearest-neighbour to the 64x64 ability texture size, so one art pixel is
one screen pixel at GUI scale 2 (icons are blitted at 16x16 GUI units).
Colours come from the Earth icon plus the Endesga 64 palette it is built on.
Imported by make_reinhard_icons.py, which owns output paths and base64 packaging.
"""
import math

from icon_art import *  # noqa: F401,F403 (house kit: palette, canvas, masks, strokes, sprites)
from icon_art import registry, to64


# ---------------------------------------------------------------- icons
ICONS, art = registry()


def icon32(name):
    return ICONS[name]()


def icon(name):
    return to64(icon32(name))


# ---------------------------------------------------------------- hand-drawn sprites
SKIN_PAL = {"h": SKIN[4], "l": SKIN[3], "m": SKIN[2], "s": SKIN[1], "d": SKIN[0]}
BOOT_PAL = {"h": ROCK[3], "l": ROCK[2], "m": ROCK[1], "s": ROCK[0], "k": INK, "n": BG[4],
            "g": GOLD[2], "G": GOLD[3], "q": GOLD[0]}

FIST_R = [
    "...lllll.....",
    "..lhhhhhlll..",
    ".lhllllllhhl.",
    "lhlllllllmlll",
    "lllllllllmsd.",
    "llllllllmllll",
    "lmdddddddmsd.",
    "mlhhhhhhhdlll",
    "mmllllllldsd.",
    ".smmmmmmmmml.",
    "..sssssssss..",
]

FIST_FRONT = [
    ".hl..hl..hl..h.",
    "hhllhhllhhllhll",
    "lllmlllmlllmllm",
    "lllslllslllslls",
    "mmmsmmmsmmmsmms",
    "lllslllslllslls",
    "hhhhhhhhlllslls",
    "lllllllldllslls",
    "mmmmmmmsmmmsmms",
    ".mllllllllllllm",
    "..mmmmmmmmmmmm.",
    "...sssssssss...",
]

THROWER = [
    ".......ff.",
    "......ww..",
    "...hhhw...",
    "..hHhhw...",
    ".hhHhhhh..",
    ".hhhffFh..",
    "..hfkff...",
    "...fff....",
    "..wwWww...",
    "..wwgww...",
    "..wWwww...",
    "..wwWww...",
    "..pp.pp...",
    ".pp...pp..",
    "pp.....pp.",
    "k.......kk",
]

KICKER = [
    "..........kk",
    ".........ppk",
    "...hhh..pp..",
    "..hHhhh.pp..",
    "..hhffF.pp..",
    "...fkf.pp...",
    "...fff.pp...",
    "f.wwWwpp....",
    "fvwwgwp.....",
    "...wWww.....",
    "...wwww.....",
    "...wWww.....",
    "....pp......",
    "....pp......",
    "....pp......",
    "....pp......",
    "...kkk......",
]

PALM = [
    "......ll......",
    "......hl.ll...",
    "...ll.hlshl...",
    "...hlshlshl...",
    "...hlshlshl...",
    "...hlshlshlll.",
    "...hlshlshlhl.",
    "...hlshlshlhm.",
    "..lhllhllhlhm.",
    "ll.lllllllllm.",
    "hll.llllllllm.",
    ".hll.lllllllm.",
    "..hll.lllllmm.",
    "...hlllllllmm.",
    "....lllllllm..",
    "....mmmmmmm...",
]

BOOT_R = [
    "..GGGGGG......",
    "..qgggggq.....",
    "..hlllms......",
    "..hlllms......",
    "..hlllms......",
    "..hlllms......",
    "..hllllms.....",
    "..hlllllmss...",
    ".hhllllllllms.",
    ".hlllllllllllm",
    ".hlmmmmmmmmmmm",
    "kkkkkkkkkkkkkk",
    ".nnn.....nnnn.",
]

KNIFE_HAND_R = [
    "...ll..........",
    "..lhhl.........",
    ".lhlllhhhhhhhh.",
    "lhllllsssssssll",
    "lllllllllllllll",
    "mmmmmmsssssssm.",
    "smmmmmmmmmmmm..",
    ".sssssssssss...",
]

RUNNER = [
    "...hhh...",
    "..hHhhh..",
    ".hhHhhhh.",
    ".hhhffFh.",
    "..hfkff..",
    "...fff...",
    "..wwWww..",
    ".vwwgwwff",
    "vv.wWw...",
    "f..wWw...",
    "..wwWww..",
    "..pp.pp..",
    ".pp...pp.",
    "pp.....pp",
    "k.......kk",
]
RUNNER_PAL = {"h": HAIR[2], "H": HAIR[3], "f": SKIN[3], "F": SKIN[4], "k": OUT, "w": SILVER[3], "W": WHITE,
              "v": SILVER[2], "g": GOLD[2], "p": STEEL[2]}

def cuff_sleeve(ic, x0, y0, x1, y1, side="left"):
    """White coat sleeve with a gold cuff band on the hand side."""
    m = rect(x0, y0, x1, y1)
    ic.solid(m, (SILVER[2], SILVER[3], WHITE))
    if side == "left":
        band = rect(x1 - 1, y0, x1, y1)
    elif side == "top":
        band = rect(x0, y0, x1, y0 + 1)
    else:
        band = rect(x0, y1 - 1, x1, y1)
    ic.fill(band, GOLD[2])
    for x, y in band:
        if (x, y - 1) not in band:
            ic.set(x, y, GOLD[3])
        elif (x, y + 1) not in band:
            ic.set(x, y, GOLD[0])


def diag(x0, y0, t0, t1, w):
    """45-degree bar rising to the right: pixels (x0 + t + k, y0 - t), k centred on the axis."""
    return {(x0 + t + k, y0 - t) for t in range(t0, t1) for k in range(-(w // 2), w - w // 2)}


def across(x0, y0, x, y):
    """Signed offset of (x, y) from the 45-degree axis through (x0, y0)."""
    return x - (x0 + (y0 - y))


def sword(ic, x0, y0, length, sheathed=False, glow=False):
    """Reid on a 45-degree diagonal: pommel at (x0, y0), tip up and to the right."""
    w = 4 if sheathed else 3
    grip = diag(x0, y0, 1, 5, 2)
    pommel = rect(x0 - 1, y0, x0, y0 + 1)
    gx, gy = x0 + 5, y0 - 5
    guard = {(gx - 4 + i, gy - 4 + i) for i in range(9)} | {(gx - 3 + i, gy - 4 + i) for i in range(8)}
    end = 6 + length
    body = diag(x0, y0, 6, end, w)
    body |= {(x0 + end + k, y0 - end) for k in range(-(w // 2) + 1, w - w // 2 - 1)}
    if not sheathed:
        body |= {(x0 + end + 1, y0 - end - 1)}
    allm = grip | pommel | guard | body
    if glow:
        ic.mix(grow(body, True) - allm, CYAN[3], 0.55)
        ic.mix(grow(grow(body, True), True) - grow(body, True) - allm, CYAN[2], 0.25)
    ic.fill(grow(allm) - allm, OUT)
    for (x, y) in body:
        k = across(x0, y0, x, y)
        if sheathed:
            ic.set(x, y, WHITE if k <= -2 else SILVER[3] if k == -1 else SILVER[2] if k == 0 else SILVER[1])
        else:
            ic.set(x, y, PALE[2] if k < 0 else WHITE if k == 0 else SILVER[2])
    if sheathed:
        bands = (6, 7, 6 + length // 2, end - 2, end - 1, end)
        for t in bands:
            for (x, y) in diag(x0, y0, t, t + 1, w + 1) & grow(body):
                k = across(x0, y0, x, y)
                ic.set(x, y, GOLD[3] if k <= -1 else GOLD[2] if k == 0 else GOLD[0])
        mid = 6 + length // 2 - 4
        ic.set(x0 + mid - 1, y0 - mid, CYAN[3])
        ic.set(x0 + mid + 4, y0 - mid - 5, CYAN[3])
    else:
        for t in range(8, end - 1, 6):
            ic.set(x0 + t - 1, y0 - t, WHITE)
        ic.set(x0 + end + 1, y0 - end - 1, WHITE)
    for (x, y) in grip:
        ic.set(x, y, CYAN[1] if across(x0, y0, x, y) < 0 else CYAN[0])
    for t in (2, 4):
        ic.set(x0 + t - 1, y0 - t, CYAN[3])
    for (x, y) in guard:
        ic.set(x, y, GOLD[2] if (x - gx) - (y - gy) <= 0 else GOLD[0])
    ic.set(gx - 4, gy - 4, GOLD[3]); ic.set(gx + 4, gy + 4, GOLD[1])
    ic.set(gx, gy, BLOOD[3]); ic.set(gx - 1, gy, BLOOD[4])
    ic.fill(pommel, GOLD[1])
    ic.set(x0 - 1, y0, GOLD[3])
    return allm


def landscape(ic, horizon=19):
    """Night sky, stars, snowy mountains and a grass strip (World Cut scenes)."""
    for x, y in ic.inside:
        t = (y - 2) / max(1, horizon - 2)
        c = NIGHT[0] if t < 0.35 else (NIGHT[2] if t < 0.7 else CYAN[1])
        if y >= horizon:
            c = EARTHY[0] if y < horizon + 4 else ROCK[1] if y < horizon + 7 else ROCK[0]
        ic.px[(x, y)] = c
    for x, y in ((5, 5), (11, 3), (24, 4), (27, 9), (8, 9), (20, 7)):
        ic.set(x, y, PALE[2])
    ridge = [(2, 16), (6, 10), (9, 13), (14, 7), (19, 13), (23, 9), (30, 16), (30, horizon), (2, horizon)]
    mount = poly(ridge) | rect(2, 15, 30, horizon - 1)
    ic.fill(mount, STEEL[1])
    for x, y in mount:
        if (x, y - 1) not in mount or (x, y - 2) not in mount:
            ic.set(x, y, PALE[2] if y < 12 else STEEL[3])
        elif (x - 1, y) not in mount:
            ic.set(x, y, STEEL[2])
    back = poly([(2, 18), (5, 15), (11, 17), (17, 14), (24, 17), (30, 14), (30, horizon), (2, horizon)])
    ic.fill(back, STEEL[0])
    for x in range(2, 31):
        ic.set(x, horizon, EARTHY[2])
        ic.set(x, horizon + 1, EARTHY[1])
    for x in range(3, 30, 4):
        ic.set(x, horizon + 4, ROCK[2])
        ic.set(x + 2, horizon + 7, ROCK[1])


# ---------------------------------------------------------------- icons
@art
def light_punch():
    ic = Icon("restrained", center=(17, 15))
    speed_lines(ic, [(8, 3, 10), (10, 2, 6), (21, 3, 9), (23, 5, 10)])
    burst(ic, 25, 15, 5.6, spikes=8, rot=22.5)
    for a in (-40, 0, 40):
        r = math.radians(a)
        ic.fill(line(25 + math.cos(r) * 4, 15 + math.sin(r) * 4, 25 + math.cos(r) * 6, 15 + math.sin(r) * 6), WHITE)
    cuff_sleeve(ic, 2, 11, 9, 19)
    sprite(ic, FIST_R, 10, 10, SKIN_PAL)
    ic.sparkle(24, 5)
    ic.sparkle(7, 26, big=False)
    return ic.image()


@art
def step_dash():
    ic = Icon("restrained", center=(18, 16))
    ic.fill(line(3, 25, 29, 25), BG[4])
    ic.fill(line(8, 26, 25, 26), BG[3])
    ghost(ic, RUNNER, 6, 9, RUNNER_PAL, CYAN[1], 0.55)
    ghost(ic, RUNNER, 11, 9, RUNNER_PAL, CYAN[3], 0.7)
    sprite(ic, RUNNER, 17, 9, RUNNER_PAL)
    speed_lines(ic, [(6, 5, 13), (17, 2, 8), (21, 4, 11)], cols=(CYAN[1], CYAN[3], CYAN[5]))
    for x, y in ((15, 24), (13, 23), (14, 22), (11, 24)):
        ic.set(x, y, PALE[1])
    ic.sparkle(26, 5)
    return ic.image()


@art
def catch():
    ic = Icon("restrained", center=(18, 16))
    speed_lines(ic, [(13, 2, 6), (17, 3, 7)])
    arrow(ic, 3, 4, 12, 13)
    ic.sparkle(11, 15, big=False)
    ic.set(13, 11, WHITE); ic.set(9, 14, YELLOW)
    cuff_sleeve(ic, 17, 24, 24, 29, side="top")
    sprite(ic, PALM, 13, 8, SKIN_PAL)
    ic.sparkle(26, 5)
    ic.sparkle(5, 25, big=False)
    return ic.image()


@art
def fist_combo():
    ic = Icon("knight", center=(16, 15))
    for (x, y, r, rot) in ((5, 6, 4.2, 0), (26, 6, 4.2, 30), (16, 5, 5.2, 22.5)):
        burst(ic, x, y, r, ramp=(CYAN[1], CYAN[3], WHITE), spikes=7, rot=rot)
    for pts in (((4, 26), (3, 20), (5, 15)), ((28, 26), (29, 20), (27, 15))):
        ic.fill(set(stroke(list(pts), 0.4, 1.4, 0.4)), PALE[1])
    cuff_sleeve(ic, 11, 23, 21, 29, side="top")
    sprite(ic, FIST_FRONT, 9, 11, SKIN_PAL)
    return ic.image()


@art
def sky_kick():
    ic = Icon("knight", center=(16, 14))
    st = stroke([(14, 28), (24, 21), (17, 12)], 0.4, 3.0, 2.2)
    ic.mix(grow(set(st)) - set(st), CYAN[1], 0.6)
    paint_stroke(ic, st, (CYAN[2], CYAN[4], WHITE))
    foe(ic, 18, 2)
    for x, y0 in ((19, 18), (23, 19), (27, 17)):
        ic.fill(line(x, y0, x, y0 + 3), PALE[1])
    sprite(ic, KICKER, 4, 12, RUNNER_PAL)
    ic.sparkle(17, 11, big=False)
    ic.sparkle(6, 6)
    return ic.image()


@art
def grab_throw():
    ic = Icon("knight", center=(17, 14))
    path = stroke([(13, 12), (22, -2), (30, 8), (27, 21)], 1.0, 2.2, 1.8)
    paint_stroke(ic, path, (STEEL[2], STEEL[3], STEEL[4]), outline=OUT)
    head = {(x, y) for y in range(20, 25) for x in range(24 + (y - 20), 31 - (y - 20))}
    ic.fill(grow(head) - head, OUT)
    ic.fill(head, STEEL[4])
    foe(ic, 11, 8, rot=1)
    for x, y in ((14, 18), (17, 19), (20, 18)):
        ic.fill({(x, y), (x + 1, y + 1)}, PALE[1])
    ic.fill(line(19, 28, 30, 28), BG[4])
    for x, y in ((22, 26), (28, 25), (25, 26)):
        ic.set(x, y, PALE[1])
    sprite(ic, THROWER, 2, 13, RUNNER_PAL)
    ic.sparkle(6, 6)
    return ic.image()


@art
def dash_behind():
    ic = Icon("knight", center=(15, 16))
    path = stroke([(6, 11), (14, -2), (26, 10)], 1.6, 2.2, 1.0)
    m = paint_stroke(ic, path, (TEAL[2], TEAL[3], TEAL[4]), outline=OUT)
    ic.fill({(24, 12), (25, 12), (26, 12), (27, 12), (28, 12), (25, 13), (26, 13), (27, 13), (26, 14)}, TEAL[4])
    ic.fill({(23, 11), (29, 11)}, OUT)
    ghost(ic, RUNNER, 2, 12, RUNNER_PAL, CYAN[2], 0.5)
    foe(ic, 10, 13)
    sprite(ic, RUNNER, 21, 14, RUNNER_PAL, flipx=True)
    ic.fill({(20, 28), (21, 27), (30, 28)}, PALE[1])
    return ic.image()


@art
def stomp():
    ic = Icon("knight", center=(16, 12))
    ground = rect(2, 21, 30, 29)
    ic.fill(ground, BG[3])
    ic.fill(rect(2, 21, 30, 21), PALE[0])
    ic.fill(rect(2, 22, 30, 22), BG[4])
    ic.fill(rect(2, 27, 30, 29), BG[2])
    for x in range(4, 30, 6):
        ic.fill({(x, 24), (x + 1, 24), (x + 3, 26)}, BG[4])
    for pts in (((16, 22), (13, 25), (14, 27), (11, 29)), ((17, 22), (20, 24), (19, 26), (23, 29)),
                ((14, 22), (8, 24), (3, 23)), ((19, 22), (25, 23), (29, 25))):
        ic.fill(polyline(pts), INK)
    ic.fill(ring_pts(16, 20, 14, 3, 160, 380), CYAN[2])
    ic.fill(ring_pts(16, 20, 10, 2, 165, 375), CYAN[4])
    for x, y, c in ((4, 14, PALE[0]), (5, 12, PALE[1]), (27, 13, PALE[0]), (26, 16, PALE[1]), (7, 9, PALE[0])):
        ic.fill(rect(x, y, x + 1, y), c)
        ic.set(x, y + 1, BG[4])
    for x in (8, 24):
        ic.fill(line(x, 3, x, 8), PALE[1])
    sprite(ic, BOOT_R, 9, 7, BOOT_PAL)
    ic.fill(line(16, 2, 16, 4), PALE[2])
    return ic.image()


@art
def reflect():
    ic = Icon("knight", center=(12, 16))
    shield = stroke([(10, 3), (19, 16), (10, 29)], 0.8, 3.2, 0.8)
    ic.mix(grow(set(shield), True) - set(shield), TEAL[1], 0.8)
    paint_stroke(ic, shield, (TEAL[2], TEAL[3], TEAL[4]))
    arrow(ic, 29, 26, 19, 19, col=GOLD[1])
    arrow(ic, 19, 14, 28, 5, col=CYAN[3], head=WHITE, fletch=CYAN[4])
    ic.sparkle(18, 16)
    sprite(ic, KNIFE_HAND_R, 3, 7, SKIN_PAL, rot=3)
    cuff_sleeve(ic, 3, 23, 10, 29, side="top")
    return ic.image()


@art
def reid():
    ic = Icon("holy", center=(16, 15))
    sword(ic, 5, 26, 15, sheathed=True)
    ic.sparkle(23, 23)
    ic.sparkle(8, 7, big=False)
    ic.sparkle(27, 13, big=False, col=CYAN[4])
    return ic.image()


@art
def reid_drawn():
    ic = Icon("holy", center=(19, 12))
    cx, cy = 21, 10
    for a in range(0, 360, 30):
        m = line(cx + math.cos(math.radians(a)) * 5, cy + math.sin(math.radians(a)) * 5,
                 cx + math.cos(math.radians(a)) * 16, cy + math.sin(math.radians(a)) * 16)
        ic.mix(m, GOLD[3] if a % 60 == 0 else GOLD[1], 0.5)
    ic.mix(disc(cx, cy, 5), GOLD[3], 0.3)
    sword(ic, 5, 26, 16, glow=True)
    ic.sparkle(26, 5)
    ic.sparkle(7, 8, big=False)
    return ic.image()


@art
def lethal_chop():
    ic = Icon("holy", center=(16, 16))
    st = stroke([(3, 5), (6, 21), (21, 25)], 0.6, 4.6, 1.2)
    ic.mix(grow(set(st), True) - set(st), BLOOD[0], 0.8)
    paint_stroke(ic, st, (BLOOD[2], BLOOD[3], BLOOD[4]))
    core = stroke([(4, 8), (7, 21), (19, 24)], 0.3, 1.2, 0.3)
    ic.fill(set(core), WHITE)
    for x, y in ((12, 28), (25, 28), (8, 27)):
        ic.fill({(x, y), (x, y - 1)}, BLOOD[2])
        ic.set(x, y - 2, BLOOD[3])
    sprite(ic, KNIFE_HAND_R, 14, 18, SKIN_PAL)
    for y in (12, 14):
        speed_lines(ic, [(y, 15, 22)])
    ic.sparkle(25, 8)
    return ic.image()


@art
def sheath_strike():
    ic = Icon("holy", center=(16, 16))
    for (ctrl, w, c) in (([(2, 10), (8, 2), (19, 3)], 1.6, PALE[1]), ([(3, 16), (10, 6), (20, 5)], 1.2, PALE[0])):
        ic.fill(set(stroke(ctrl, 0.4, w, 0.4)), c)
    sword(ic, 4, 27, 13, sheathed=True)
    burst(ic, 25, 6, 4.2, ramp=(GOLD[1], GOLD[3], WHITE), spikes=7)
    for x, y in ((15, 3), (28, 9), (27, 17)):
        ic.sparkle(x, y, big=False)
    return ic.image()


def flat_saw(ic, sx, sy, rx, ry):
    """A gold blade-saw seen almost edge-on: lit top face, dark rim, alternating teeth, white hub."""
    body = {(x, y) for x in range(32) for y in range(32) if ((x - sx) / rx) ** 2 + ((y - sy) / ry) ** 2 <= 1}
    teeth = set()
    for i, a in enumerate(range(0, 360, 20)):
        if i % 2 == 0:
            t = (int(round(sx + (rx + 1) * math.cos(math.radians(a)))), int(round(sy + (ry + 0.7) * math.sin(math.radians(a)))))
            if t not in body:
                teeth.add(t)
    ic.outline(body | teeth, OUT)
    ic.fill(body, GOLD[1])
    ic.fill({(x, y) for (x, y) in body if y < sy}, GOLD[2])
    ic.fill({(x, y) for (x, y) in body if y < sy and abs(x - sx) < rx * 0.45}, GOLD[3])
    ic.fill({(x, y) for (x, y) in body if y > sy}, GOLD[0])
    ic.fill(teeth, GOLD[3])
    ic.set(int(round(sx)), int(round(sy)), WHITE)


@art
def saw_sphere():
    """A translucent cyan barrier sphere cut off by the ground, gold blade-saws lying flat along its shell and a
    tiny Reinhard (red hair, white coat, Reid out) spinning at its heart."""
    ic = Icon("holy", center=(16, 17))
    cx, cy, r, ground = 16, 15.5, 11.8, 25
    ic.fill(rect(2, ground + 1, 29, 29), BG[2])
    ic.fill(rect(2, ground + 1, 29, ground + 1), BG[4])
    ball = {(x, y) for (x, y) in disc(cx, cy, r) if y <= ground}
    ic.mix(ball, CYAN[1], 0.35)
    ic.mix(ball & disc(cx - 4, cy - 5, 6), CYAN[4], 0.18)
    ic.fill(grow(ball) - ball - rect(0, ground + 1, 31, 31), OUT)
    shell = ball - shrink(ball) - rect(0, ground, 31, ground)
    ic.fill(shell, CYAN[3])
    ic.fill({(x, y) for (x, y) in shell if x - cx + y - cy < -9}, CYAN[5])
    ic.fill(arc(cx, cy, r - 2.2, 1, 200, 245), CYAN[4])
    ic.mix(rect(cx - 11, ground, cx + 11, ground), CYAN[4], 0.8)
    for (sx, sy, rx) in ((9, 10, 3.3), (23.5, 12, 3.3), (8.5, 20.5, 3.5), (23.5, 21.5, 3.5)):
        flat_saw(ic, sx, sy, rx, 1.3)
    # the swing: a gold arc wrapped round him, front half bright
    ic.fill(arc(16, 17, 7, 1, 10, 170, ry=2.6), GOLD[3])
    ic.fill(arc(16, 17, 7, 1, 190, 350, ry=2.6), GOLD[0])
    # tiny Reinhard
    hair = {(15, 11), (16, 11), (17, 11), (14, 12), (15, 12), (16, 12), (17, 12), (18, 12), (14, 13), (18, 13)}
    face = {(15, 13), (16, 13), (17, 13), (15, 14), (16, 14), (17, 14)}
    coat = rect(14, 15, 18, 19) | {(13, 16)}
    legs = {(15, 20), (15, 21), (17, 20), (17, 21)}
    ic.outline(hair | face | coat | legs, OUT)
    ic.fill(hair, HAIR[3])
    ic.set(16, 11, HAIR[4])
    ic.fill(face, SKIN[3])
    ic.set(17, 14, SKIN[2])
    ic.fill(coat, PALE[2])
    ic.fill({(18, 15), (18, 16), (18, 17), (18, 18), (18, 19)}, PALE[1])
    ic.fill({(16, 15), (16, 16)}, GOLD[2])
    ic.fill(legs, STEEL[0])
    ic.fill(line(19, 16, 26, 16), WHITE)
    ic.set(19, 16, GOLD[2])
    ic.set(27, 16, PALE[2])
    ic.sparkle(27, 5)
    ic.sparkle(5, 4, big=False, col=CYAN[5])
    return ic.image()


@art
def world_cut():
    ic = Icon("holy", bg=False)
    landscape(ic)
    snap = dict(ic.px)
    for (x, y) in ic.inside:
        if x > 16 and (x, y - 2) in snap:
            ic.px[(x, y)] = snap[(x, y - 2)]
    glow_line(ic, line(16, 2, 16, 29))
    ic.sparkle(16, 11)
    return ic.image()


@art
def diagonal_world_cut():
    ic = Icon("holy", bg=False)
    landscape(ic)
    snap = dict(ic.px)
    for (x, y) in ic.inside:
        if x + y < 31 and (x + 1, y + 1) in snap:
            ic.px[(x, y)] = snap[(x + 1, y + 1)]
    glow_line(ic, {(x, 31 - x) for x in range(2, 30)})
    ic.sparkle(17, 14)
    return ic.image()


@art
def world_erasure():
    ic = Icon("holy", bg=False)
    landscape(ic)
    cx, cy, r = 16, 15, 8.5
    ic.mix(disc(cx, cy, r + 2.5) - disc(cx, cy, r), VOID[4], 0.45)
    core = disc(cx, cy, r)
    ic.sphere(core, cx - 2, cy - 2, r + 2, [VOID[0], VOID[0], VOID[1], VOID[2], VOID[3]], outline=None,
              lx=0.6, ly=0.6)
    ic.fill(core - shrink(core), VOID[6])
    ic.fill(arc(cx, cy, r - 1, 1, 200, 260), VOID[5])
    for x, y in ((14, 12), (19, 17), (12, 18), (18, 11)):
        ic.set(x, y, VOID[5])
    for x, y, c in ((5, 8, STEEL[3]), (26, 7, PALE[2]), (27, 22, EARTHY[2]), (6, 23, ROCK[2]), (24, 26, EARTHY[1]),
                    (4, 15, STEEL[2]), (28, 14, STEEL[2])):
        ic.fill(rect(x, y, x + 1, y + 1), c)
        ic.set(x + (1 if x < cx else -1), y + (1 if y < cy else -1), VOID[5])
    return ic.image()


NAMES = ['light_punch', 'step_dash', 'catch', 'fist_combo', 'sky_kick', 'grab_throw', 'dash_behind', 'stomp',
         'reflect', 'reid', 'reid_drawn', 'lethal_chop', 'sheath_strike', 'saw_sphere', 'world_cut', 'world_erasure',
         'diagonal_world_cut']
