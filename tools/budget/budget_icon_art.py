"""Ultimate Hero Expansion ability icons, drawn in the Reinhard icon style.

Every icon reuses the Reinhard art kit (tools/assets/reinhard_icon_art.py): the
same 32x32 bevel frame taken from the Earth status icon (silver for strikes and
dashes, blue for projectiles and power-ups, gold for ultimates), the charcoal
radial backdrop, navy outlines, top-left ramp shading and plus sparkles. The
32x32 art is upscaled 2x (nearest) to the 64x64 panel texture.

Each ability is described by a motif (fist, beam, orb, slash, dash, aura, ...)
plus the ability colour from the roster and the hero's own look (hair, skin,
outfit), so the little figures in dash / aura / ring icons are the hero.
Imported by make_budget_icons.py.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "assets"))

import reinhard_icon_art as R  # noqa: E402
from reinhard_icon_art import (BG, CLEAR, FIST_FRONT, FIST_R, FOE, Icon, INK, OUT, PALE, PALM, WHITE,  # noqa: E402,F401
                               YELLOW, arc, burst, disc, grow, line, paint_stroke, poly, polyline, rect,
                               shrink, speed_lines, sprite, stroke)


def hx(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def mixc(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3)) + (255,)


def ramp(base):
    """Six-step ramp from one colour: deep, dark, mid, light, pale, white-hot."""
    c = hx(base) if isinstance(base, str) else base
    black = (8, 6, 20, 255)
    return [mixc(c, black, 0.62), mixc(c, black, 0.32), c, mixc(c, WHITE, 0.32), mixc(c, WHITE, 0.62),
            mixc(c, WHITE, 0.88)]


FRAME = {"A": "restrained", "B": "knight", "C": "restrained", "D": "knight", "E": "holy"}


# ------------------------------------------------------------------ the hero as a sprite
RUNNER = R.RUNNER
STAND = [
    "...hhh...",
    "..hHhhh..",
    "..hfFfh..",
    "..fkfkf..",
    "...fff...",
    ".wwwWwww.",
    "fwwwgwwwf",
    "f.wwWww.f",
    "f.wwWww.f",
    "..wwwww..",
    "..pp.pp..",
    "..pp.pp..",
    "..pp.pp..",
    "..ss.ss..",
]
STAND_SMALL = [
    ".hhh.",
    "hHhhh",
    ".fFf.",
    "wwWww",
    "fwWwf",
    ".wWw.",
    ".p.p.",
    ".s.s.",
]


def hero_pal(look):
    skin = hx(look["skin"])
    bald = "bald" in look["face"]
    hair = skin if bald else hx(look["hair"])
    shirt = hx(look["shirt"])
    pants = hx(look["pants"])
    shoes = hx(look["shoes"])
    if sum(shirt[:3]) < 90:  # near-black outfits still need to read on the charcoal backdrop
        shirt = mixc(shirt, PALE[0], 0.35)
    if sum(pants[:3]) < 90:
        pants = mixc(pants, PALE[0], 0.3)
    if sum(hair[:3]) < 90:
        hair = mixc(hair, PALE[0], 0.25)
    return {"h": hair, "H": mixc(hair, WHITE, 0.35), "f": skin, "F": mixc(skin, WHITE, 0.3), "k": OUT,
            "w": shirt, "W": mixc(shirt, WHITE, 0.25), "v": mixc(shirt, INK, 0.3), "g": mixc(shirt, YELLOW, 0.55),
            "p": pants, "s": shoes if sum(shoes[:3]) > 60 else mixc(shoes, PALE[0], 0.3)}


def ghost(ic, rows, x, y, pal, col, a):
    tmp = Icon(ic.mode, bg=False)
    m = sprite(tmp, rows, x, y, pal, outline=None)
    ic.mix(m & ic.inside, col, a)
    return m


def solid_ramp(ic, mask, c, outline=OUT):
    r = ramp(c) if not isinstance(c, list) else c
    ic.solid(mask, (r[1], r[2], r[3], r[4]), outline=outline)


def glow(ic, mask, r, core=None):
    ic.mix(grow(grow(mask, True), True) - grow(mask, True), r[1], 0.45)
    ic.mix(grow(mask, True) - mask, r[3], 0.8)
    ic.fill(mask, core or r[5])


def sleeve(ic, look, x0, y0, x1, y1):
    m = rect(x0, y0, x1, y1)
    solid_ramp(ic, m, hero_pal(look)["w"])
    return m


def skin_pal(look):
    s = hx(look["skin"])
    return {"h": mixc(s, WHITE, 0.45), "l": mixc(s, WHITE, 0.18), "m": s, "s": mixc(s, INK, 0.25),
            "d": mixc(s, INK, 0.5)}


def glove_pal(col):
    r = ramp(col)
    return {"h": r[4], "l": r[3], "m": r[2], "s": r[1], "d": r[0]}


def hand_pal(look, glove):
    return glove_pal(glove) if glove else skin_pal(look)


# ------------------------------------------------------------------ motifs
def m_punch(ic, r, look, glove=None, bolts=False, long_arm=False, web=False):
    speed_lines(ic, [(8, 3, 10), (10, 2, 6), (21, 3, 9), (23, 5, 10)], cols=(r[1], r[3], r[5]))
    burst(ic, 25, 15, 5.6, ramp=(r[2], r[4], WHITE), spikes=8, rot=22.5)
    for a in (-40, 0, 40):
        t = math.radians(a)
        ic.fill(line(25 + math.cos(t) * 4, 15 + math.sin(t) * 4, 25 + math.cos(t) * 6, 15 + math.sin(t) * 6), WHITE)
    if long_arm:
        arm = rect(2, 13, 10, 16)
        solid_ramp(ic, arm, hx(look["skin"]))
        ic.mix({(x, 13) for x in range(2, 11)}, WHITE, 0.4)
    else:
        sleeve(ic, look, 2, 11, 9, 19)
    sprite(ic, FIST_R, 10, 10, hand_pal(look, glove))
    if bolts:
        for pts in (((14, 6), (17, 9), (15, 10), (19, 13)), ((13, 24), (16, 22), (15, 21), (19, 19))):
            m = polyline(list(pts))
            glow(ic, m, r)
    if web:
        for a in range(0, 360, 45):
            t = math.radians(a)
            ic.fill(line(25, 15, 25 + math.cos(t) * 7, 15 + math.sin(t) * 7), PALE[3])
        ic.fill(arc(25, 15, 4, 1, 0, 360), PALE[2])
    ic.sparkle(24, 5)
    ic.sparkle(7, 26, big=False, col=r[4])


def m_combo(ic, r, look, glove=None, many=False):
    spots = ((5, 6, 4.2, 0), (26, 6, 4.2, 30), (16, 5, 5.2, 22.5))
    if many:
        spots += ((5, 17, 3.2, 10), (27, 17, 3.2, 40))
    for (x, y, rr, rot) in spots:
        burst(ic, x, y, rr, ramp=(r[1], r[3], WHITE), spikes=7, rot=rot)
    if many:
        for (gx, gy) in ((3, 9), (19, 1)):
            ghost(ic, FIST_FRONT[:9], gx, gy, hand_pal(look, glove), r[3], 0.5)
    for pts in (((4, 26), (3, 20), (5, 15)), ((28, 26), (29, 20), (27, 15))):
        ic.fill(set(stroke(list(pts), 0.4, 1.4, 0.4)), r[3])
    sleeve(ic, look, 11, 23, 21, 29)
    sprite(ic, FIST_FRONT, 9, 11, hand_pal(look, glove))


def m_mega_fist(ic, r, look, glove=None, bolts=False):
    ic.mix(disc(16, 14, 13), r[1], 0.45)
    ic.mix(disc(16, 14, 9), r[2], 0.5)
    for a in range(0, 360, 20):
        t = math.radians(a + 7)
        ic.fill(line(16 + math.cos(t) * 10, 14 + math.sin(t) * 10, 16 + math.cos(t) * 16, 14 + math.sin(t) * 16),
                r[5] if a % 40 else r[3])
    if bolts:
        for pts in (((3, 4), (8, 9), (6, 10), (11, 14)), ((28, 4), (24, 8), (26, 9), (21, 13)), ((4, 22), (8, 20), (7, 24))):
            m = polyline(list(pts), 1.4)
            ic.fill(grow(m) - m, r[3])
            ic.fill(m, INK)
    burst(ic, 16, 6, 4.0, ramp=(r[2], r[4], WHITE), spikes=8, rot=22.5)
    sleeve(ic, look, 11, 24, 21, 29)
    sprite(ic, FIST_FRONT, 9, 12, hand_pal(look, glove))
    ic.sparkle(27, 26, big=False, col=r[5])


def m_palm(ic, r, look, glove=None, rings=3):
    for i in range(rings):
        ic.fill(arc(17, 14, 6 + i * 3.2, 1, 0, 360, ry=(6 + i * 3.2) * 0.95), r[4 - min(i, 2)])
    ic.mix(disc(17, 14, 6), r[3], 0.4)
    sleeve(ic, look, 17, 24, 24, 29)
    sprite(ic, PALM, 11, 8, hand_pal(look, glove))
    ic.sparkle(26, 5)
    ic.sparkle(5, 25, big=False, col=r[5])


def m_beam(ic, r, look, wide=3.6, orb=False, eyes=False, glove=None):
    if eyes:
        # the hero's face bottom-left, two beams leaving the eyes
        face = rect(3, 18, 12, 28)
        pal = hero_pal(look)
        solid_ramp(ic, face, pal["f"])
        ic.fill(rect(3, 18, 12, 20), pal["h"])
        ic.fill(grow(rect(3, 18, 12, 20)) - grow(face), OUT)
        for ex in (5, 9):
            ic.fill(rect(ex, 22, ex + 1, 22), r[5])
            b = line(ex + 1, 22, 28, 5 + (ex - 5), 1)
            ic.mix(grow(b, True) - b, r[2], 0.7)
            ic.fill(b, r[5])
        ic.sparkle(25, 22, big=False, col=r[4])
        return
    beam = line(7, 24, 28, 4, wide)
    ic.mix(line(7, 24, 28, 4, wide + 4), r[1], 0.55)
    ic.mix(line(7, 24, 28, 4, wide + 2), r[3], 0.7)
    ic.fill(beam, r[4])
    ic.fill(line(7, 24, 28, 4, max(1.0, wide - 2)), r[5])
    if orb:
        ic.sphere(disc(23, 9, 5), 23, 9, 5, r[1:], outline=OUT)
        ic.fill(arc(23, 9, 3, 1, 190, 260), WHITE)
    burst(ic, 8, 23, 4.8, ramp=(r[2], r[4], WHITE), spikes=8)
    for (x, y) in ((4, 8), (12, 4), (26, 26), (20, 28)):
        ic.set(x, y, r[4])
    ic.sparkle(5, 5)
    ic.sparkle(26, 20, big=False, col=r[5])


def m_orb(ic, r, look, swirl=True, hand=True, big=False, glove=None):
    cx, cy, rr = (16, 13, 9.0) if big else (16, 12, 6.8)
    ic.mix(disc(cx, cy, rr + 3), r[1], 0.5)
    ic.mix(disc(cx, cy, rr + 1.6), r[2], 0.55)
    ic.sphere(disc(cx, cy, rr), cx, cy, rr, r[1:], outline=OUT)
    if swirl:
        ic.fill(arc(cx, cy, rr - 2.2, 1, 200, 320), WHITE)
        ic.fill(arc(cx, cy, rr - 4.0, 1, 20, 140), r[5])
        ic.fill(arc(cx + 1, cy + 1, rr - 1, 1, 40, 100), r[4])
    for a in range(0, 360, 60):
        t = math.radians(a + 15)
        ic.set(int(cx + math.cos(t) * (rr + 3)), int(cy + math.sin(t) * (rr + 3)), r[5])
    if hand:
        sleeve(ic, look, 11, 26, 21, 29)
        sprite(ic, FIST_FRONT[:9], 9, 19, hand_pal(look, glove))
    ic.sparkle(5, 5)
    ic.sparkle(27, 24, big=False, col=r[5])


def m_vortex(ic, r):
    cx, cy = 16, 15
    for i in range(160):
        t = i / 160 * math.pi * 5
        rad = 1 + i / 160 * 12
        x, y = cx + math.cos(t) * rad, cy + math.sin(t) * rad * 0.9
        ic.set(int(round(x)), int(round(y)), r[5] if i < 40 else r[4] if i < 90 else r[3] if i < 130 else r[2])
        ic.set(int(round(x)) + 1, int(round(y)), r[2] if i > 60 else r[4])
    ic.sphere(disc(cx, cy, 3), cx, cy, 3, r[2:], outline=OUT)
    ic.sparkle(6, 6)
    ic.sparkle(26, 25, big=False, col=r[5])


def m_slash(ic, r, n=1, katana=False):
    curves = [[(5, 25), (12, 8), (27, 5)], [(4, 18), (16, 10), (28, 14)], [(8, 28), (22, 22), (27, 9)]][:n]
    for i, c in enumerate(curves):
        st = stroke(c, 0.3, 3.4 if n == 1 else 2.6, 0.3)
        ic.mix(grow(set(st)) - set(st), r[1], 0.6)
        paint_stroke(ic, st, (r[2], r[4], WHITE))
    if katana:
        blade(ic, r, 6, 27, 14, col=PALE)
    ic.sparkle(25, 24, big=False, col=r[5])
    ic.sparkle(6, 6)


def blade(ic, r, x0, y0, length, col=None, glowc=True):
    """Thin katana: hilt at (x0, y0), blade rising to the upper right."""
    col = col or PALE
    body = {(x0 + 3 + t, y0 - 3 - t) for t in range(length)} | {(x0 + 4 + t, y0 - 3 - t) for t in range(length - 1)}
    hilt = {(x0 + t, y0 - t) for t in range(3)} | {(x0 + 1 + t, y0 - t) for t in range(2)}
    guard = {(x0 + 1, y0 - 4), (x0 + 2, y0 - 3), (x0 + 4, y0 - 1), (x0 + 5, y0 - 2), (x0 + 3, y0 - 2)}
    allm = body | hilt | guard
    if glowc:
        ic.mix(grow(body, True) - allm, r[3], 0.6)
    ic.fill(grow(allm) - allm, OUT)
    for (x, y) in body:
        ic.set(x, y, WHITE if (x - x0) + (y - y0) <= 0 else col[2])
    ic.fill(hilt, r[0])
    ic.fill(guard, R.GOLD[2])
    return allm


def m_blades(ic, r, n=1, crossed=False, chains=False):
    if crossed:
        blade(ic, r, 5, 27, 18)
        # mirrored second blade
        tmp = Icon(ic.mode, bg=False)
        m = blade(tmp, r, 5, 27, 18)
        for (x, y), c in tmp.px.items():
            ic.set(31 - x, y, c)
        burst(ic, 16, 13, 3.5, ramp=(r[2], r[4], WHITE), spikes=6)
    elif n == 3:
        for (x0, y0, ln) in ((4, 27, 17), (9, 28, 16), (3, 21, 15)):
            blade(ic, r, x0, y0, ln)
        ic.sparkle(26, 24, big=False, col=r[5])
    else:
        st = stroke([(4, 20), (14, 4), (29, 7)], 0.3, 2.6, 0.3)
        ic.mix(set(st), r[2], 0.55)
        blade(ic, r, 6, 27, 18)
    if chains:
        for i in range(0, 22, 2):
            t = i / 22
            x = 5 + t * 22
            y = 28 - 6 * math.sin(t * math.pi)
            ic.fill(rect(int(x), int(y), int(x), int(y)), R.STEEL[3] if i % 4 else R.STEEL[1])
        for (fx, fy) in ((15, 6), (19, 4), (11, 9)):
            m_flame_small(ic, r, fx, fy)
    ic.sparkle(26, 6, big=False, col=r[5]) if crossed else ic.sparkle(5, 5)


def m_flame_small(ic, r, x, y):
    m = poly([(x - 2, y + 3), (x, y - 3), (x + 2, y + 3)])
    ic.fill(m, r[3])
    ic.set(x, y + 2, WHITE)


def m_claws(ic, r):
    for k in (-4, 0, 4):
        st = stroke([(6 + k, 27), (14 + k, 15), (26 + k, 5)], 0.3, 2.6, 0.3)
        ic.mix(grow(set(st)) - set(st), r[1], 0.6)
        paint_stroke(ic, st, (r[2], r[4], WHITE), outline=OUT)
    ic.sparkle(6, 6)
    ic.sparkle(26, 25, big=False, col=r[5])


def m_dash(ic, r, look, mode="ghost"):
    pal = hero_pal(look)
    ic.fill(line(3, 25, 29, 25), BG[4])
    ic.fill(line(8, 26, 25, 26), BG[3])
    if mode == "clones":
        for gx in (4, 10):
            sprite(ic, RUNNER, gx, 9, pal)
            ic.mix({(x, y) for x in range(gx, gx + 9) for y in range(9, 24)} & ic.inside, r[3], 0.15)
    elif mode == "teleport":
        ghost(ic, RUNNER, 4, 9, pal, r[2], 0.5)
        for (x, y) in ((8, 6), (5, 12), (11, 20), (7, 24), (12, 8)):
            ic.set(x, y, r[5])
    elif mode == "up":
        for i, gy in enumerate((16, 12)):
            ghost(ic, RUNNER, 8 + i * 4, gy, pal, r[2 + i], 0.5 + i * 0.15)
        for x in range(6, 26, 5):
            ic.fill(rect(x, 22 - (x // 5), x + 3, 22 - (x // 5)), r[4])
    elif mode == "rope":
        ic.fill(line(26, 3, 21, 10), PALE[3])
        ghost(ic, RUNNER, 6, 9, pal, r[1], 0.55)
    elif mode == "bolts":
        ghost(ic, RUNNER, 5, 9, pal, r[2], 0.55)
        for pts in (((3, 8), (7, 11), (5, 12), (10, 15)), ((6, 20), (9, 18), (8, 22), (13, 21))):
            glow(ic, polyline(list(pts)), r)
    elif mode == "flames":
        for (fx, fy) in ((7, 20), (10, 22), (5, 17)):
            m_flame_small(ic, r, fx, fy)
        ghost(ic, RUNNER, 8, 9, pal, r[2], 0.45)
    elif mode == "shadow":
        ic.fill({(x, y) for x in range(4, 16) for y in (24, 25, 26) if (x - 10) ** 2 / 36 + (y - 25) ** 2 / 2 <= 1}, r[0])
        ghost(ic, RUNNER, 6, 11, pal, r[1], 0.7)
    else:
        ghost(ic, RUNNER, 6, 9, pal, r[1], 0.55)
        ghost(ic, RUNNER, 11, 9, pal, r[3], 0.7)
    sprite(ic, RUNNER, 17 if mode != "up" else 16, 9 if mode != "up" else 5, pal)
    speed_lines(ic, [(6, 5, 13), (17, 2, 8), (21, 4, 11)], cols=(r[1], r[3], r[5]))
    ic.sparkle(26, 5)


def aura_mask(cx, top, bottom, w):
    pts = []
    for i in range(9):
        x = cx - w + i * (2 * w / 8)
        y = top + (0 if i % 2 == 0 else 4)
        pts.append((x, y))
    pts += [(cx + w + 1, bottom), (cx - w - 1, bottom)]
    return poly(pts)


def m_aura(ic, r, look, extra=None):
    pal = hero_pal(look)
    a = aura_mask(16, 3, 28, 9.5)
    ic.mix(a, r[1], 0.6)
    ic.mix(aura_mask(16, 6, 28, 7), r[2], 0.65)
    ic.mix(aura_mask(16, 9, 28, 5), r[3], 0.5)
    ic.fill(line(3, 27, 29, 27), BG[4])
    sprite(ic, STAND, 12, 10, pal)
    for (x, y) in ((5, 18), (27, 16)):
        ic.fill({(x, y - 2), (x - 1, y - 1), (x + 1, y - 1), (x, y - 1), (x, y), (x, y + 1)}, r[5])
    if extra == "bubbles":
        for (x, y, rr) in ((6, 9, 1.6), (25, 8, 2.0), (23, 22, 1.3), (7, 22, 1.2)):
            ic.fill(arc(x, y, rr, 1, 0, 360), r[4])
            ic.set(int(x - rr / 2), int(y - rr / 2), WHITE)
    elif extra == "drops":
        for (x, y) in ((6, 8), (25, 7), (24, 21), (6, 21)):
            m = poly([(x, y - 3), (x + 2, y + 1), (x - 2, y + 1)]) | disc(x, y + 1, 1.6)
            ic.fill(grow(m) - m, OUT)
            ic.fill(m, r[2])
            ic.set(x - 1, y, r[4])
    elif extra == "bolts":
        for pts in (((5, 4), (8, 8), (6, 9), (9, 13)), ((26, 18), (23, 21), (25, 22), (22, 26))):
            glow(ic, polyline(list(pts)), r)
    elif extra == "gear":
        g = disc(26, 7, 3.2) - disc(26, 7, 1.2)
        for a2 in range(0, 360, 60):
            t = math.radians(a2)
            g |= rect(int(26 + math.cos(t) * 4), int(7 + math.sin(t) * 4), int(26 + math.cos(t) * 4), int(7 + math.sin(t) * 4))
        ic.fill(grow(g) - g, OUT)
        ic.fill(g, PALE[2])
    elif extra == "giant":
        ribs = set()
        for y in range(7, 20, 3):
            ribs |= arc(16, y + 6, 8, 1, 200, 340, ry=4)
        glow(ic, ribs, r, core=r[4])
        sprite(ic, STAND, 12, 10, pal)
    ic.sparkle(5, 5)


def m_eye(ic, r, look, pattern=None):
    lid = poly([(3, 15), (9, 9), (16, 7.5), (23, 9), (29, 15), (23, 21), (16, 22.5), (9, 21)])
    ic.fill(grow(lid) - lid, OUT)
    ic.fill(lid, PALE[3])
    ic.fill({(x, y) for (x, y) in lid if y > 18}, PALE[1])
    iris = disc(16, 15, 6)
    ic.sphere(iris, 16, 15, 6, r[1:], outline=None)
    ic.fill(disc(16, 15, 2.2), INK if pattern != "rinne" else r[0])
    if pattern == "tomoe":
        for a in (90, 210, 330):
            t = math.radians(a)
            ic.set(int(round(16 + math.cos(t) * 4)), int(round(15 + math.sin(t) * 4)), INK)
    ic.fill(rect(13, 12, 14, 13), WHITE)
    for a in range(-150, -20, 26):
        t = math.radians(a)
        ic.fill(line(16 + math.cos(t) * 10, 15 + math.sin(t) * 10, 16 + math.cos(t) * 13, 15 + math.sin(t) * 13), r[4])
    ic.sparkle(5, 26, big=False, col=r[5])
    ic.sparkle(26, 26, big=False, col=r[5])


def bolt_mask(pts, w=2.0):
    return polyline(pts, w)


def m_bolt(ic, r, look=None, hand=False, n=2):
    sets = [[(20, 3), (13, 13), (19, 14), (10, 28)], [(28, 6), (24, 13), (27, 14), (22, 22)],
            [(7, 4), (5, 10), (8, 11), (4, 17)]][:n]
    for i, pts in enumerate(sets):
        m = bolt_mask(pts, 2.6 if i == 0 else 1.4)
        ic.mix(grow(grow(m, True), True), r[1], 0.5)
        ic.fill(grow(m) - m, r[3])
        ic.fill(m, r[5] if i == 0 else r[4])
    if hand:
        sleeve(ic, look, 2, 24, 9, 29)
        sprite(ic, R.KNIFE_HAND_R, 8, 21, skin_pal(look))
        for (x, y) in ((22, 22), (25, 25), (19, 26), (26, 19)):
            ic.set(x, y, r[5])
    ic.sparkle(26, 26, big=False, col=r[5])


def flame_mask(cx, base, h, w):
    pts = [(cx - w, base), (cx - w * 0.9, base - h * 0.45), (cx - w * 0.35, base - h * 0.65), (cx - w * 0.5, base - h * 0.95),
           (cx, base - h * 0.75), (cx + w * 0.25, base - h), (cx + w * 0.55, base - h * 0.55), (cx + w * 0.9, base - h * 0.7),
           (cx + w, base - h * 0.3), (cx + w * 0.8, base)]
    return poly(pts) | disc(cx, base - w * 0.6, w * 0.8)


def m_flame(ic, r, look=None, boot=False):
    outer = flame_mask(16, 27, 23, 10)
    mid = flame_mask(16, 27, 16, 7)
    core = flame_mask(16, 27, 9, 4)
    ic.fill(grow(outer) - outer, OUT)
    ic.fill(outer, r[1])
    ic.fill(mid, r[2])
    ic.fill(core, r[4])
    ic.fill(shrink(shrink(core)), r[5])
    if boot:
        sprite(ic, R.BOOT_R, 9, 15, {"h": hx("4a4a5a"), "l": hx("2c2c38"), "m": hx("1d1d26"), "s": hx("111118"),
                                      "k": INK, "n": r[3], "g": PALE[2], "G": PALE[3], "q": PALE[1]})
    ic.sparkle(5, 5)
    ic.sparkle(27, 8, big=False, col=r[5])


def stand_small(ic, look, x, y):
    return sprite(ic, STAND_SMALL, x, y, hero_pal(look))


def m_ring(ic, r, look, rays=True, emblem=None):
    gy = 22
    ic.fill(rect(2, gy + 3, 29, 29), BG[2])
    for i, (rx, ry) in enumerate(((13, 5), (10, 3.8), (7, 2.6))):
        ic.fill(arc(16, gy, rx, 1, 0, 360, ry=ry), r[3 + i if i < 3 else 5])
    ic.mix({(x, y) for x in range(32) for y in range(32) if ((x - 16) / 13) ** 2 + ((y - gy) / 5) ** 2 <= 1}, r[2], 0.3)
    if rays:
        for x in (5, 9, 23, 27):
            h = 9 if x in (9, 23) else 6
            glow(ic, line(x, gy - 1, x, gy - h), r, core=r[4])
    if emblem == "lantern":
        e = disc(16, 8, 5) - disc(16, 8, 3)
        e |= rect(11, 5, 12, 11) | rect(20, 5, 21, 11)
        glow(ic, e, r)
    else:
        burst(ic, 16, 8, 4.5, ramp=(r[2], r[4], WHITE), spikes=8, rot=22.5)
    stand_small(ic, look, 14, gy - 7)
    ic.sparkle(5, 5)
    ic.sparkle(27, 5, big=False, col=r[5])


def m_meteor(ic, r):
    ic.fill(rect(2, 25, 29, 29), R.ROCK[1])
    ic.fill(rect(2, 25, 29, 25), R.EARTHY[2])
    trail = stroke([(5, 24), (12, 16), (19, 10)], 0.4, 4.5, 6.5)
    paint_stroke(ic, trail, (r[1], r[3], r[5]))
    rock = disc(21, 8, 5.2)
    ic.sphere(rock, 21, 8, 5.2, [R.ROCK[0], R.ROCK[1], R.ROCK[2], R.ROCK[3]], outline=OUT)
    for (x, y) in ((20, 6), (23, 9), (19, 10)):
        ic.set(x, y, R.ROCK[0])
    burst(ic, 6, 25, 3.6, ramp=(r[2], r[4], WHITE), spikes=7)
    ic.sparkle(27, 18, big=False, col=r[5])


def heart_mask(cx, cy, s):
    return disc(cx - s * 0.5, cy - s * 0.2, s * 0.55) | disc(cx + s * 0.5, cy - s * 0.2, s * 0.55) | \
        poly([(cx - s * 1.02, cy), (cx + s * 1.02, cy), (cx, cy + s * 1.15)])


def m_heart(ic, r):
    h = heart_mask(15, 15, 9)
    ic.mix(grow(grow(h, True), True) - h, r[1], 0.5)
    solid_ramp(ic, h, r)
    ic.fill(rect(10, 9, 11, 10), WHITE)
    plus = rect(23, 19, 24, 27) | rect(20, 22, 27, 24)
    ic.fill(grow(plus) - plus, OUT)
    ic.fill(plus, R.EARTHY[3])
    ic.fill(rect(23, 19, 23, 27) | rect(20, 22, 27, 22), WHITE)
    ic.sparkle(26, 6)
    ic.sparkle(5, 26, big=False, col=r[5])


def m_web(ic, r):
    cx, cy = 16, 15
    for a in range(0, 360, 45):
        t = math.radians(a + 10)
        ic.fill(line(cx, cy, cx + math.cos(t) * 14, cy + math.sin(t) * 14), PALE[3])
    for rr in (4, 8, 12):
        pts = [(cx + math.cos(math.radians(a + 10)) * rr, cy + math.sin(math.radians(a + 10)) * rr) for a in range(0, 405, 45)]
        ic.fill(polyline(pts), PALE[2] if rr < 12 else PALE[1])
    ic.fill(disc(cx, cy, 1.6), WHITE)
    ic.sparkle(26, 26, big=False, col=r[5])


def m_batarang(ic, r):
    pts = [(3, 12), (9, 13), (12, 10), (14, 13), (16, 11), (18, 13), (20, 10), (23, 13), (29, 12),
           (25, 16), (24, 21), (20, 18), (16, 21), (12, 18), (8, 21), (7, 16)]
    m = poly(pts)
    speed_lines(ic, [(24, 3, 9), (26, 5, 12)], cols=(r[1], r[3], r[5]))
    solid_ramp(ic, m, hx("5b6478"))
    ic.fill({(x, y) for (x, y) in m if y == min(yy for (xx, yy) in m if xx == x)}, PALE[2])
    ic.sparkle(26, 5)
    ic.sparkle(5, 26, big=False, col=r[5])


def m_trident(ic, r):
    shaft = line(6, 27, 22, 9, 1.6)
    head = line(22, 9, 27, 4, 1.6) | line(19, 8, 22, 3, 1.4) | line(23, 12, 28, 9, 1.4) | line(19, 8, 23, 12, 1.4)
    allm = shaft | head
    ic.mix(grow(grow(head, True), True), r[2], 0.5)
    ic.fill(grow(allm) - allm, OUT)
    ic.fill(shaft, R.GOLD[1])
    ic.fill(head, R.GOLD[3])
    for (x, y) in ((27, 4), (22, 3), (28, 9)):
        ic.set(x, y, WHITE)
    m_wave_small(ic, r)
    ic.sparkle(6, 6)


def m_wave_small(ic, r):
    for x in range(3, 29):
        y = int(round(26 + math.sin(x / 2.0) * 1.5))
        ic.fill(rect(x, y, x, 28), r[2])
        ic.set(x, y, r[5])


def m_wave(ic, r):
    body = poly([(3, 28), (4, 18), (8, 10), (15, 5), (23, 5), (28, 9), (26, 13), (22, 10), (18, 11), (16, 16), (18, 22), (29, 24), (29, 28)])
    ic.fill(grow(body) - body, OUT)
    ic.fill(body, r[1])
    ic.fill({(x, y) for (x, y) in body if x + y < 28}, r[2])
    curl = arc(20, 13, 5, 2, 200, 360) | arc(20, 13, 5, 2, 0, 20)
    ic.fill(curl & body, r[4])
    ic.fill(arc(15, 15, 9, 1, 190, 300), WHITE)
    for (x, y) in ((25, 4), (28, 6), (27, 3)):
        ic.set(x, y, r[5])
    ic.sparkle(6, 6)


GUN = [
    ".sssssssssss",
    "slllllllllll",
    "smmmmmmmmmmm",
    "sddmdddd....",
    ".ddd.d......",
    ".ddd........",
    "ddd.........",
    "ddd.........",
]


def m_pistols(ic, r):
    pal = {"s": PALE[3], "l": PALE[2], "m": hx("657392"), "d": hx("2a2f4e")}
    sprite(ic, GUN, 4, 6, pal)
    sprite(ic, GUN, 16, 16, pal, flipx=True)
    burst(ic, 19, 7.5, 3.4, ramp=(r[2], r[4], WHITE), spikes=7)
    burst(ic, 12, 17.5, 3.4, ramp=(r[2], r[4], WHITE), spikes=7, rot=20)
    speed_lines(ic, [(26, 4, 9), (5, 22, 28)], cols=(r[1], r[3], r[5]))
    for (x, y) in ((8, 25), (11, 27), (25, 9)):
        ic.fill(rect(x, y, x, y + 1), R.GOLD[2])
        ic.set(x, y, R.GOLD[3])
    ic.sparkle(26, 26, big=False, col=r[5])


def m_axe(ic, r):
    shaft = line(6, 27, 20, 10, 1.6)
    head = poly([(18, 6), (27, 3), (29, 11), (24, 14), (20, 12)])
    ic.mix(grow(grow(head, True), True) - head, r[3], 0.5)
    allm = shaft | head
    ic.fill(grow(allm) - allm, OUT)
    ic.fill(shaft, R.ROCK[2])
    solid_ramp(ic, head, hx("9fb6c8"))
    ic.fill(line(27, 3, 29, 11), WHITE)
    for k in range(3):
        ic.fill(arc(16, 16, 9 + k * 3, 1, 140, 200), r[3 + (k % 2)])
    for (x, y) in ((8, 6), (10, 9), (25, 24)):
        ic.sparkle(x, y, big=False, col=r[5])


def m_spirit(ic, r, portal=True, horns=True):
    if portal:
        ic.fill({(x, y) for x in range(32) for y in range(32) if ((x - 16) / 12) ** 2 + ((y - 25) / 3.2) ** 2 <= 1}, r[0])
        ic.fill(arc(16, 25, 12, 1, 0, 360, ry=3.2), r[4])
    tmp = Icon(ic.mode, bg=False)
    m = sprite(tmp, FOE, 0, 0, {"o": (1, 1, 1, 255), "r": (2, 2, 2, 255)}, outline=None)
    big = set()
    for (x, y) in m:
        for dx in (0, 1):
            for dy in (0, 1):
                big.add((7 + x * 2 + dx, 3 + y * 2 + dy))
    if horns:
        big |= line(11, 5, 9, 1, 1.4) | line(20, 5, 22, 1, 1.4)
    big = {p for p in big if p[1] <= 26}
    ic.mix(grow(grow(big, True), True) - big, r[3], 0.45)
    ic.fill(grow(big) - big, OUT)
    ic.fill(big, r[2])
    ic.fill({(x, y) for (x, y) in big if (x + 1, y) not in big or (x, y + 1) not in big}, r[1])
    ic.fill({(x, y) for (x, y) in big if (x - 1, y) not in big or (x, y - 1) not in big}, r[4])
    for (ex, ey) in ((13, 8), (18, 8)):
        ic.fill(rect(ex, ey, ex + 1, ey), WHITE)
        ic.set(ex, ey + 1, YELLOW)
    ic.fill(rect(13, 11, 18, 11), INK)
    for x in (13, 15, 17):
        ic.set(x, 11, WHITE)
    ic.sparkle(26, 5)
    ic.sparkle(5, 8, big=False, col=r[5])


def m_lantern(ic, r):
    body = rect(11, 9, 20, 22)
    top = rect(13, 5, 18, 8)
    base = rect(9, 23, 22, 25)
    allm = body | top | base
    ic.mix(grow(grow(allm, True), True), r[2], 0.5)
    ic.fill(grow(allm) - allm, OUT)
    ic.fill(top | base, PALE[1])
    ic.fill(body, r[2])
    e = disc(15.5, 15.5, 3.5) - disc(15.5, 15.5, 2)
    ic.fill(e | rect(11, 13, 20, 13) | rect(11, 18, 20, 18), r[5])
    ic.fill(rect(11, 9, 11, 22), r[3])
    ic.sparkle(26, 5)
    ic.sparkle(5, 25, big=False, col=r[5])


def m_clock(ic, r):
    face = disc(16, 15, 10)
    ic.fill(grow(face) - face, OUT)
    ic.fill(face, PALE[3])
    ic.fill(face - shrink(face), r[2])
    for a in range(0, 360, 30):
        t = math.radians(a)
        ic.set(int(round(16 + math.cos(t) * 8)), int(round(15 + math.sin(t) * 8)), OUT)
    ic.fill(line(16, 15, 16, 8), OUT)
    ic.fill(line(16, 15, 21, 15), r[1])
    ic.fill(rect(16, 15, 16, 15), r[3])
    # the 7:3 tie mark Nanami fights with
    ic.fill(rect(9, 26, 22, 27), R.GOLD[2])
    ic.fill(rect(18, 26, 18, 27), OUT)
    ic.sparkle(26, 5, big=False, col=r[5])


def m_ratio(ic, r):
    blade(ic, r, 5, 27, 19, glowc=True)
    # 7:3 weak point marker across the blade
    for (x, y) in line(14, 10, 22, 18):
        ic.set(x, y, r[4])
    burst(ic, 18, 14, 3.2, ramp=(r[2], r[4], WHITE), spikes=6)
    ic.fill(rect(4, 3, 9, 3), r[4]); ic.fill(rect(4, 5, 7, 5), r[3])
    ic.sparkle(26, 25, big=False, col=r[5])


def m_sun(ic, r, look):
    for a in range(0, 360, 30):
        t = math.radians(a)
        ic.fill(line(16 + math.cos(t) * 8, 12 + math.sin(t) * 8, 16 + math.cos(t) * 12, 12 + math.sin(t) * 12, 1.4), R.GOLD[3])
    sunm = disc(16, 12, 6.5)
    ic.sphere(sunm, 16, 12, 6.5, [R.GOLD[0], R.GOLD[1], R.GOLD[2], R.GOLD[3], WHITE], outline=OUT)
    stand_small(ic, look, 14, 21)
    ic.mix(rect(12, 20, 20, 29), R.GOLD[3], 0.2)
    ic.sparkle(5, 25, big=False, col=r[5])


def m_fan(ic, r):
    for k in range(3):
        ic.fill(arc(10, 24, 15 + k * 2, 1, 250, 345), r[3 + (k % 2)])
    handle = line(6, 27, 15, 18, 1.8)
    fan = disc(19, 13, 7.5)
    ic.fill(grow(handle | fan) - (handle | fan), OUT)
    ic.fill(handle, R.ROCK[2])
    ic.fill(line(6, 27, 15, 18, 1), R.ROCK[3])
    solid_ramp(ic, fan, hx("c42430"))
    ring = fan - disc(19, 13, 6)
    ic.fill(ring, R.GOLD[1])
    ic.fill(disc(19, 13, 2.6), PALE[3])
    ic.fill(line(19, 6, 19, 20) & fan - disc(19, 13, 2.6), hx("891e2b"))
    ic.fill(line(12, 13, 26, 13) & fan - disc(19, 13, 2.6), hx("891e2b"))
    burst(ic, 27, 24, 3.4, ramp=(r[2], r[4], WHITE), spikes=7)
    ic.sparkle(5, 5)


def m_shrine(ic, r, look):
    m_ring(ic, r, look, rays=False)
    roof = poly([(7, 9), (25, 9), (21, 5), (11, 5)])
    posts = rect(10, 10, 11, 18) | rect(21, 10, 22, 18)
    ic.fill(grow(roof | posts) - (roof | posts), OUT)
    ic.fill(posts, R.BLOOD[1])
    solid_ramp(ic, roof, hx("3a1a20"))
    ic.fill(rect(8, 9, 24, 9), R.BLOOD[3])


# ------------------------------------------------------------------ ability -> motif
# glove colour (hex) for heroes whose fists are gloved
GLOVES = {"saitama": "d42020", "superman": None, "batman": "2a2a33", "flash": "c01818", "spider_man": "c4141e",
          "deadpool": "8a1010", "green_lantern": "1f9a3a", "genos": "8c939c"}

MOTIFS = {
    "gojo": {"infinity": ("palm",), "blue": ("orb",), "six_eyes": ("eye",), "hollow_purple": ("beam", {"orb": True, "wide": 5.0})},
    "sukuna": {"cursed_strike": ("punch",), "dismantle": ("slash", {"n": 3}), "malevolent_shrine": ("shrine",)},
    "itadori": {"divergent_fist": ("punch", {"bolts": True}), "sprint": ("dash",), "black_flash": ("mega_fist", {"bolts": True})},
    "megumi": {"divine_dogs": ("spirit",), "shadow_step": ("dash", {"mode": "shadow"}), "ten_shadows": ("aura", {"extra": "giant"})},
    "toji": {"heavenly_strike": ("blades",), "restriction_dash": ("dash",), "physical_gift": ("aura",)},
    "geto": {"cursed_spirit": ("spirit",), "manipulation": ("palm", {"rings": 2}), "uzumaki": ("vortex",)},
    "nanami": {"ratio": ("ratio",), "overtime": ("clock",)},
    "mahito": {"idle_transfiguration": ("palm",), "soul_shift": ("dash", {"mode": "teleport"}), "self_embodiment": ("ring",)},
    "yuta": {"katana_slash": ("slash", {"katana": True}), "rika": ("spirit", {"portal": False}), "copy": ("eye", {"pattern": "rinne"}),
             "pure_love": ("beam", {"wide": 5.0})},
    "choso": {"piercing_blood": ("beam", {"wide": 2.0}), "blood_manipulation": ("aura", {"extra": "drops"})},
    "goku": {"ki_punch": ("punch",), "ki_blast": ("orb", {"swirl": False}), "instant_transmission": ("dash", {"mode": "teleport"}),
             "kamehameha": ("beam", {"wide": 5.6, "orb": True})},
    "vegeta": {"galick_gun": ("beam",), "saiyan_pride": ("aura", {"extra": "bolts"}), "final_flash": ("beam", {"wide": 6.0})},
    "naruto": {"chakra_punch": ("punch",), "shadow_clone_dash": ("dash", {"mode": "clones"}), "rasengan": ("orb", {"big": True})},
    "sasuke": {"chidori": ("bolt", {"hand": True, "n": 3}), "lightning_step": ("dash", {"mode": "bolts"}), "amaterasu": ("flame",)},
    "madara": {"gunbai_strike": ("fan",), "susanoo": ("aura", {"extra": "giant"}), "meteor": ("meteor",)},
    "luffy": {"gum_gum_pistol": ("punch", {"long_arm": True}), "gear_second": ("aura",), "gum_gum_gatling": ("combo", {"many": True})},
    "zoro": {"three_sword_style": ("blades", {"n": 3}), "flying_slash": ("slash", {"n": 2})},
    "sanji": {"diable_jambe": ("flame", {"boot": True}), "sky_walk": ("dash", {"mode": "up"})},
    "saitama": {"normal_punch": ("punch",), "serious_sidestep": ("dash",), "serious_punch": ("mega_fist",)},
    "genos": {"incineration": ("beam", {"wide": 5.0}), "boost": ("dash", {"mode": "flames"}), "upgrade": ("aura", {"extra": "gear"})},
    "superman": {"super_punch": ("punch",), "heat_vision": ("beam", {"eyes": True}), "solar_power": ("sun",), "divine_might": ("mega_fist",)},
    "batman": {"martial_arts": ("combo",), "batarang": ("batarang",), "grapple": ("dash", {"mode": "rope"})},
    "flash": {"speed_punch": ("punch", {"bolts": True}), "speed_force": ("dash", {"mode": "bolts"}), "lightning_speed": ("bolt", {"n": 3})},
    "green_lantern": {"lantern_beam": ("beam",), "willpower": ("lantern",), "lanterns_oath": ("ring", {"emblem": "lantern"})},
    "aquaman": {"trident_strike": ("trident",), "tidal_wave": ("wave",)},
    "spider_man": {"web_punch": ("punch", {"web": True}), "web_shot": ("web",), "web_swing": ("dash", {"mode": "rope"})},
    "deadpool": {"katana_combo": ("blades", {"crossed": True}), "pistols": ("pistols",), "regeneration": ("heart",)},
    "wolverine": {"claw_slash": ("claws",), "berserk_leap": ("dash", {"mode": "up"}), "healing_factor": ("heart",)},
    "kratos": {"blades_of_chaos": ("blades", {"chains": True}), "leviathan_throw": ("axe",), "spartan_rage": ("aura", {"extra": "drops"}),
               "god_slayer": ("ring",)},
    "shrek": {"ogre_slap": ("palm", {"rings": 2}), "swamp_power": ("aura", {"extra": "bubbles"}), "ogre_roar": ("ring",)},
    # MULTIVERSE EXPANSION PHASE II: the same motif kit, new colours.
    "aizen": {"reiatsu_strike": ("palm", {"rings": 3}), "black_coffin": ("vortex",), "kyoka_suigetsu": ("eye", {"pattern": "rinne"}),
              "hogyoku_ascension": ("ring",)},
    "ichigo": {"getsuga_tensho": ("slash", {"n": 2}), "shunpo": ("dash",), "bankai": ("aura", {"extra": "bolts"}),
               "mugetsu": ("beam", {"wide": 6.0})},
    "dante": {"rebellion": ("blades",), "ebony_ivory": ("pistols",), "devil_trigger": ("aura", {"extra": "bolts"})},
    "vergil": {"yamato_slash": ("slash", {"katana": True}), "judgement_cut": ("slash", {"n": 3}), "air_trick": ("dash", {"mode": "teleport"}),
               "judgement_cut_end": ("ring",)},
    "guts": {"dragon_slayer": ("blades",), "cannon_arm": ("beam", {"wide": 2.0}), "berserker_armor": ("aura", {"extra": "drops"})},
    "sans": {"bone_attack": ("blades", {"n": 3}), "shortcut": ("dash", {"mode": "teleport"}), "gaster_blaster": ("beam", {"wide": 5.0, "orb": True})},
    "eren": {"titan_punch": ("mega_fist",), "odm_gear": ("dash", {"mode": "rope"}), "titan_hardening": ("aura", {"extra": "giant"}),
             "the_rumbling": ("ring",)},
    "levi": {"spinning_slash": ("blades", {"n": 2, "crossed": True}), "odm_maneuver": ("dash", {"mode": "rope"}), "ackerman_power": ("aura",)},
    "denji": {"chainsaw_rip": ("blades", {"chains": True}), "pull_cord": ("dash", {"mode": "flames"}), "blood_frenzy": ("aura", {"extra": "drops"})},
    "makima": {"bang": ("beam", {"wide": 2.0}), "control_devil": ("eye", {"pattern": "rinne"}), "domination": ("ring",)},
    "power": {"blood_hammer": ("mega_fist",), "blood_spear": ("beam", {"wide": 2.0}), "greatest": ("aura", {"extra": "drops"})},
    "alucard": {"jackal": ("pistols",), "shadow_form": ("dash", {"mode": "shadow"}), "release_restraint": ("eye",), "familiars": ("spirit",)},
    "rimuru": {"water_blade": ("wave",), "predator": ("spirit", {"horns": False}), "beelzebuth": ("vortex",)},
    "ainz": {"grasp_heart": ("heart",), "dark_wisdom": ("clock",), "fallen_down": ("sun",)},
    "shadow": {"slime_sword": ("slash", {"katana": True}), "eminence_step": ("dash", {"mode": "shadow"}), "i_am_atomic": ("ring",)},
    "mob": {"psychic_blast": ("orb", {"big": True}), "hundred_percent": ("aura", {"extra": "bolts"}), "question_percent": ("ring",)},
    "escanor": {"rhitta_strike": ("axe",), "sunshine": ("sun",), "cruel_sun": ("meteor",)},
    "meliodas": {"full_counter": ("palm", {"rings": 2}), "hellblaze": ("flame",), "demon_mode": ("aura", {"extra": "drops"}),
                 "revenge_counter": ("beam", {"wide": 5.0})},
}

# icon-only colour overrides for abilities whose gameplay colour is too dark to read
ICON_COLOR = {"black_flash": "e01a3a", "shadow_step": "5a5a8a", "ten_shadows": "6a6aa0", "uzumaki": "9a4ad0",
              "geto.cursed_spirit": "8a4ab0", "piercing_blood": "d01a1a", "batarang": "f1db4d", "pistols": "ffb030",
              "cursed_strike": "e02040", "malevolent_shrine": "e01818", "manipulation": "9a5ac0",
              "divine_dogs": "8a96c8", "black_coffin": "7a3aa0", "mugetsu": "6a2a6a", "bankai": "5a5a7a", "shadow_form": "6a2a3a",
              "familiars": "8a2a3a", "hellblaze": "5a5a6a", "demon_mode": "6a6a7a", "eminence_step": "7a5ab0"}
CUSTOM_RAMP = {"amaterasu": [hx("0e071b"), hx("1a1220"), hx("2c1d3a"), hx("622461"), hx("ca52c9"), hx("fdd2ed")]}

LOOK_ARGS = {"punch", "combo", "mega_fist", "palm", "beam", "orb", "dash", "aura", "eye", "ring", "sun", "shrine"}
GLOVE_ARGS = {"punch", "combo", "mega_fist", "palm", "orb"}


def draw(hero, move):
    """hero: roster dict (key, look); move: (type, slug, en, ru, dmg, cd, colour, extra) -> 32x32 image."""
    mtype, slug, colour = move[0], move[1], move[6]
    spec = MOTIFS[hero["key"]][slug]
    name = spec[0]
    kw = dict(spec[1]) if len(spec) > 1 else {}
    ic = Icon(FRAME[mtype], center=(16, 15))
    colour = ICON_COLOR.get(hero["key"] + "." + slug, ICON_COLOR.get(slug, colour))
    r = CUSTOM_RAMP.get(slug) or ramp(colour)
    fn = globals()["m_" + name]
    args = [ic, r]
    if name in LOOK_ARGS:
        args.append(hero["look"])
    if name in GLOVE_ARGS and GLOVES.get(hero["key"]):
        kw["glove"] = GLOVES[hero["key"]]
    if name == "bolt" and kw.get("hand"):
        kw["look"] = hero["look"]
    fn(*args, **kw)
    return ic.image()
