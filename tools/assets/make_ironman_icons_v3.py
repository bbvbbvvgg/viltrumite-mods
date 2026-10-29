#!/usr/bin/env python3
"""Iron Man ability icons, panel style of the Viltrumite / Homelander art:
the hero himself (posed Minecraft model rendered from the suit skins) doing the
ability on a bright gradient tile with a bevelled border, dark outline and
pixel effects. 64x64, own work. Replaces the stage 2-5 icon art.

SUPERSEDED (core 1.46.0): the shipped icons come from
`make_hero_icons.py --hero ironman` (`ironman_icon_art.py`, Reinhard house style).
Kept for history; writing the old art needs `--legacy-overwrite`.

Run: python3 tools/assets/make_ironman_icons_v3.py [--preview OUT.png]
Writes base64 files under viltrumitecore/src/main/binassets/.../gui/ability/ironman.
"""
import base64
import io
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icon_figure import Figure, project, render  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BIN = os.path.join(ROOT, "viltrumitecore/src/main/binassets/assets/viltrumitecore/textures")
OUT = os.path.join(BIN, "gui/ability/ironman")
S = 64


def b64image(rel):
    with open(os.path.join(BIN, rel + ".b64"), "rb") as f:
        return Image.open(io.BytesIO(base64.b64decode(f.read()))).convert("RGBA")


def skin(name):
    return b64image("entity/hero/ironman_" + name + ".png"), b64image("entity/hero/ironman_" + name + "_glow.png")


TONY = b64image("entity/hero/ironman.png")


# ---------------------------------------------------------------- tile

def tile(top, bottom, glow=(0.62, 0.38), glow_col=None):
    """Gradient tile with a soft glow and the bevelled border of the Viltrumite icons."""
    top, bottom = np.array(top, float), np.array(bottom, float)
    glow_col = np.array(glow_col if glow_col else [min(255, c * 1.25 + 30) for c in top], float)
    y, x = np.mgrid[0:S, 0:S].astype(float)
    t = (y / (S - 1))[..., None]
    rgb = top * (1 - t) + bottom * t
    d = np.hypot(x - glow[0] * S, y - glow[1] * S) / (S * 0.62)
    g = (np.clip(1 - d, 0, 1) ** 2)[..., None]
    rgb = rgb * (1 - g * 0.7) + glow_col * g * 0.7
    img = np.dstack([rgb, np.full((S, S), 255.0)])
    # bevel: bright top row / left column, dark right and bottom pair
    img[0, :, :3] = np.minimum(255, img[0, :, :3] * 1.25 + 25)
    img[:, 0, :3] = np.minimum(255, img[:, 0, :3] * 1.25 + 25)
    img[:, S - 2, :3] *= 0.8
    img[S - 2, :, :3] *= 0.8
    img[:, S - 1, :3] *= 0.55
    img[S - 1, :, :3] *= 0.55
    return Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGBA")


def outline(fig_img, color=(22, 14, 30, 255)):
    """1 px dark outline around the figure (Homelander art)."""
    a = np.asarray(fig_img)[..., 3] > 0
    grown = a.copy()
    grown[1:, :] |= a[:-1, :]
    grown[:-1, :] |= a[1:, :]
    grown[:, 1:] |= a[:, :-1]
    grown[:, :-1] |= a[:, 1:]
    ring = grown & ~a
    out = np.zeros((S, S, 4), np.uint8)
    out[ring] = color
    img = Image.fromarray(out, "RGBA")
    img.alpha_composite(fig_img)
    return img


def clip_border(img):
    """Keep the bevel visible: nothing drawn over the outer border pixels."""
    return img


def finish(base, layers):
    for layer in layers:
        base.alpha_composite(layer)
    # redraw the bevel border on top (art never covers it)
    arr = np.asarray(base).copy()
    ref = np.asarray(BORDER_REF[0])
    mask = BORDER_MASK
    arr[mask] = ref[mask]
    return Image.fromarray(arr, "RGBA")


BORDER_MASK = np.zeros((S, S), bool)
BORDER_MASK[0, :] = BORDER_MASK[:, 0] = True
BORDER_MASK[S - 2:, :] = BORDER_MASK[:, S - 2:] = True
BORDER_REF = [None]


def layer():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


# ---------------------------------------------------------------- effects

CYAN = (120, 235, 255, 255)
CYAN_D = (40, 160, 255, 255)
WHITE = (250, 255, 255, 255)
ORANGE = (255, 150, 40, 255)
YELLOW = (255, 235, 120, 255)
RED = (230, 40, 40, 255)


def beam(d, a, b, width, core=WHITE, edge=CYAN, halo=(80, 200, 255, 110)):
    d.line([a, b], fill=halo, width=width + 4)
    d.line([a, b], fill=edge, width=width)
    d.line([a, b], fill=core, width=max(1, width - 3))


def plus(d, x, y, col=YELLOW, r=1):
    d.point([(x, y)], fill=WHITE)
    for i in range(1, r + 1):
        d.point([(x - i, y), (x + i, y), (x, y - i), (x, y + i)], fill=col)


def burst(d, cx, cy, r0, r1, n, col, start=0.0, step=None, width=1):
    step = step or 2 * math.pi / n
    for i in range(n):
        ang = start + i * step
        d.line([(cx + math.cos(ang) * r0, cy + math.sin(ang) * r0), (cx + math.cos(ang) * r1, cy + math.sin(ang) * r1)], fill=col, width=width)


def ring(d, cx, cy, r, col, width=1):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=col, width=width)


def flame(d, x, y, dx, dy, length, width=3):
    """Thruster flame from (x, y) along (dx, dy)."""
    n = math.hypot(dx, dy) or 1.0
    dx, dy = dx / n, dy / n
    for i in range(length):
        t = i / max(1, length - 1)
        col = lerp(WHITE, ORANGE, min(1, t * 1.6)) if t < 0.7 else lerp(ORANGE, (200, 40, 20, 255), (t - 0.7) / 0.3)
        w = max(1, round(width * (1 - t * 0.7)))
        cx, cy = x + dx * i, y + dy * i
        d.ellipse([cx - w / 2, cy - w / 2, cx + w / 2, cy + w / 2], fill=col[:3] + (int(255 * (1 - t * 0.5)),))


def missile(d, x, y, dx, dy, trail=10):
    n = math.hypot(dx, dy) or 1.0
    dx, dy = dx / n, dy / n
    for i in range(trail, 2, -1):
        c = 120 + i * 7
        r = 1.2 + i * 0.16
        cx, cy = x - dx * i * 1.4, y - dy * i * 1.4
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(c, c - 10, c - 20, 200))
    flame(d, x - dx * 3, y - dy * 3, -dx, -dy, 4, 3)
    d.line([(x - dx * 3, y - dy * 3), (x + dx * 3, y + dy * 3)], fill=(22, 14, 30, 255), width=4)
    d.line([(x - dx * 3, y - dy * 3), (x + dx * 2, y + dy * 2)], fill=(215, 220, 230, 255), width=2)
    d.point([(round(x + dx * 3), round(y + dy * 3))], fill=RED)


def hexes(d, pts, col=CYAN):
    for x, y in pts:
        d.rectangle([x, y, x + 1, y + 1], fill=col)
        d.point([(x + 2, y)], fill=WHITE)


# ---------------------------------------------------------------- scenes

def C(x, top, scale, yaw=-40.0, pitch=6.0):
    """Camera with the head top (y 32.5) at screen (x, top)."""
    return dict(yaw=yaw, pitch=pitch, scale=scale, center=(0, 32.5), shift=(x, top))


def fig_layer(fig, alpha=1.0, outline_col=(22, 14, 30, 255), **cam):
    img = render(fig, **cam)
    if alpha < 1.0:
        arr = np.asarray(img).copy()
        arr[..., 3] = (arr[..., 3] * alpha).astype(np.uint8)
        img = Image.fromarray(arr, "RGBA")
        return img
    return outline(img, outline_col)


def unibeam():
    base = tile((40, 80, 170), (12, 24, 70), glow=(0.75, 0.5), glow_col=(120, 220, 255))
    s50, g50 = skin("mark_50")
    f = Figure(s50, g50).set("right_arm", -30, 0, -25).set("left_arm", -30, 0, 25).set("head", 0, 10, 0)
    cam = C(20, 6, 2.5)
    fx = layer()
    d = ImageDraw.Draw(fx)
    cx, cy = project(f, "body", (0, 20, -2.4), **cam)
    beam(d, (cx, cy), (66, cy + 2), 10)
    d.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=WHITE)
    for p in [(44, 14), (54, 50), (60, 18), (40, 52)]:
        plus(d, *p, col=CYAN)
    return finish(base, [fig_layer(f, **cam), fx])


def missiles():
    base = tile((255, 175, 70), (205, 70, 30), glow=(0.72, 0.3), glow_col=(255, 235, 160))
    s50, g50 = skin("mark_50")
    f = Figure(s50, g50).set("left_arm", 82, 0, 0).set("right_arm", -20, 0, -10).set("head", 0, 10, 0)
    cam = C(18, 7, 2.4)
    fx = layer()
    d = ImageDraw.Draw(fx)
    for x, y, dx, dy in [(50, 12, 1, -0.7), (58, 26, 1, -0.25), (44, 22, 1, -0.5), (54, 42, 1, 0.05)]:
        missile(d, x, y, dx, dy, trail=12)
    return finish(base, [fx, fig_layer(f, **cam)])


def nano_arsenal():
    base = tile((150, 70, 220), (50, 20, 110), glow=(0.68, 0.35), glow_col=(220, 160, 255))
    s50, g50 = skin("mark_50")
    f = Figure(s50, g50).set("left_arm", 120, 0, 0).set("right_arm", -20, 0, -10).set("head", 0, 10, 0)
    cam = C(20, 9, 2.3)
    fx = layer()
    d = ImageDraw.Draw(fx)
    hx, hy = project(f, "left_arm", (6, 11, 0), **cam)
    tip = (hx + 24, hy - 4)
    d.polygon([(hx, hy - 2), (hx, hy + 2), (tip[0] - 4, tip[1] + 2), tip, (tip[0] - 4, tip[1] - 2)], fill=CYAN)
    d.line([(hx + 1, hy), (tip[0] - 2, tip[1])], fill=WHITE)
    d.line([(hx - 1, hy - 4), (hx - 1, hy + 4)], fill=(60, 70, 90, 255), width=2)
    hexes(d, [(tip[0] - 8, tip[1] + 8), (hx + 12, hy - 8), (52, 44), (40, 52), (56, 10)])
    return finish(base, [fig_layer(f, **cam), fx])


def countermeasures():
    base = tile((30, 120, 150), (8, 36, 60), glow=(0.5, 0.45), glow_col=(255, 200, 120))
    s50, g50 = skin("mark_50")
    f = Figure(s50, g50).set("right_arm", 0, 0, -30).set("left_arm", 0, 0, 30).set("right_leg", 0, 0, -8).set("left_leg", 0, 0, 8)
    cam = C(32, 8, 1.55, yaw=-12)
    fx = layer()
    d = ImageDraw.Draw(fx)
    for side in (-1, 1):
        for i in range(8):
            t = i / 7.0
            x = 32 + side * (9 + t * 21)
            y = 22 + t * 30 - math.sin(t * math.pi) * 14
            r = 2.4 - t * 1.0
            d.line([(x, y), (x - side * 3, y - 4)], fill=(255, 210, 140, 140), width=2)
            d.ellipse([x - r - 1, y - r - 1, x + r + 1, y + r + 1], fill=(255, 130, 40, 150))
            d.ellipse([x - r, y - r, x + r, y + r], fill=WHITE if i % 2 == 0 else YELLOW)
    return finish(base, [fx, fig_layer(f, **cam)])


def scan():
    base = tile((20, 90, 130), (6, 20, 45), glow=(0.5, 0.45), glow_col=(90, 220, 255))
    s50, g50 = skin("mark_50")
    f = Figure(s50, g50).set("head", 0, 30, 0)
    cam = C(24, 10, 4.4, yaw=-35)
    fx = layer()
    d = ImageDraw.Draw(fx)
    c = (46, 30)
    ring(d, c[0], c[1], 9, CYAN)
    ring(d, c[0], c[1], 4, CYAN_D)
    for ax, ay in [(-1, -1), (1, -1), (-1, 1), (1, 1)]:
        x, y = c[0] + ax * 13, c[1] + ay * 13
        d.line([(x, y), (x - ax * 4, y)], fill=CYAN)
        d.line([(x, y), (x, y - ay * 4)], fill=CYAN)
    d.line([(c[0] - 15, c[1]), (c[0] + 15, c[1])], fill=(120, 235, 255, 140))
    d.point([c], fill=WHITE)
    d.line([(34, 50), (58, 50)], fill=CYAN)
    d.line([(34, 53), (50, 53)], fill=CYAN_D)
    return finish(base, [fig_layer(f, **cam), fx])


def veronica():
    base = tile((110, 190, 255), (40, 100, 200), glow=(0.3, 0.2), glow_col=(255, 250, 220))
    fx = layer()
    d = ImageDraw.Draw(fx)
    for i in range(20):
        t = i / 19
        x, y = 4 + t * 22, 2 + t * 20
        r = 2 + t * 5
        d.ellipse([x - r, y - r, x + r, y + r], fill=lerp((255, 220, 120, 120), (255, 100, 30, 230), t))
    pod = Image.new("RGBA", (30, 40), (0, 0, 0, 0))
    pd = ImageDraw.Draw(pod)
    pd.rounded_rectangle([2, 2, 27, 37], 8, fill=(210, 30, 35, 255), outline=(22, 14, 30, 255))
    pd.rounded_rectangle([5, 5, 13, 34], 4, fill=(245, 80, 80, 255))
    pd.rectangle([4, 10, 25, 13], fill=(150, 150, 160, 255))
    pd.rectangle([4, 27, 25, 29], fill=(150, 150, 160, 255))
    pd.rectangle([14, 3, 15, 36], fill=(60, 20, 25, 255))
    pd.ellipse([18, 17, 23, 22], fill=CYAN)
    pd.point([(20, 19)], fill=WHITE)
    pod = pod.rotate(38, Image.NEAREST, expand=True)
    fx.alpha_composite(pod, (20, 12))
    d.arc([8, 50, 60, 66], 180, 360, fill=(255, 255, 255, 150))
    for p in [(54, 12), (12, 44), (58, 30)]:
        plus(d, *p, col=WHITE)
    return finish(base, [fx])


def helmet():
    base = tile((255, 225, 110), (220, 150, 40), glow=(0.5, 0.4), glow_col=(255, 250, 200))
    s50, g50 = skin("mark_50")
    open_skin = s50.copy()
    open_glow = g50.copy()
    for box in ((8, 8, 16, 16), (40, 8, 48, 16)):
        open_skin.paste(TONY.crop(box), box[:2])
        open_glow.paste(Image.new("RGBA", (8, 8), (0, 0, 0, 0)), box[:2])
    f = Figure(open_skin, open_glow).set("head", 0, 30, 0)
    cam = C(28, 16, 4.0, yaw=-35)
    fx = layer()
    d = ImageDraw.Draw(fx)
    # the faceplate raised above the forehead, tilted back
    plate = Image.new("RGBA", (8, 8))
    plate.paste(s50.crop((8, 8, 16, 16)), (0, 0))
    pg = g50.crop((8, 8, 16, 16))
    plate.alpha_composite(pg)
    plate = plate.resize((26, 14), Image.NEAREST)
    pl = layer()
    pl.alpha_composite(plate, (14, 2))
    for p in [(56, 12), (8, 50), (56, 48)]:
        plus(d, *p, col=WHITE)
    burst(d, 27, 10, 15, 18, 5, WHITE, start=-2.6, step=0.5)
    return finish(base, [fig_layer(f, **cam), outline(pl), fx])


def suit():
    base = tile((80, 200, 255), (20, 70, 160), glow=(0.5, 0.4), glow_col=(200, 250, 255))
    s50, g50 = skin("mark_50")
    tony = Figure(TONY).set("right_arm", 0, 0, -12).set("left_arm", 0, 0, 12)
    armor = Figure(s50, g50).set("right_arm", 0, 0, -12).set("left_arm", 0, 0, 12)
    cam = C(32, 8, 1.55, yaw=-20)
    a = np.asarray(fig_layer(armor, **cam)).copy()
    t = np.asarray(fig_layer(tony, **cam)).copy()
    y, x = np.mgrid[0:S, 0:S]
    edge = 36 - (x - 32) * 0.6
    mix = np.where((y > edge)[..., None], a, t)
    img = Image.fromarray(mix.astype(np.uint8), "RGBA")
    fx = layer()
    d = ImageDraw.Draw(fx)
    m = np.asarray(img)[..., 3] > 0
    for yy in range(S):
        for xx in range(S):
            if m[yy, xx] and abs(yy - edge[yy, xx]) < 1.0:
                d.point([(xx, yy)], fill=WHITE)
            elif m[yy, xx] and 1.0 <= yy - edge[yy, xx] < 2.0:
                d.point([(xx, yy)], fill=CYAN)
    hexes(d, [(16, 30), (46, 20), (44, 34), (18, 42), (50, 28), (12, 20)])
    return finish(base, [img, fx])


def bulky(name="mark_42"):
    s, g = skin(name)
    return Figure(s, g, bulk=1.35)


def hulk_grab():
    base = tile((110, 200, 90), (30, 90, 40), glow=(0.72, 0.4), glow_col=(220, 255, 180))
    f = bulky().set("left_arm", 80, 0, 0).set("right_arm", -20, 0, -15).set("head", 0, 10, 0)
    cam = C(18, 8, 1.9)
    fx = layer()
    d = ImageDraw.Draw(fx)
    hx, hy = project(f, "left_arm", (6, 10, 0), **cam)
    hx, hy = min(hx, 46), hy
    d.rectangle([hx + 1, hy - 9, hx + 14, hy + 4], fill=(80, 150, 75, 255), outline=(22, 14, 30, 255))
    d.rectangle([hx + 4, hy - 5, hx + 6, hy - 3], fill=(20, 30, 20, 255))
    d.rectangle([hx + 9, hy - 5, hx + 11, hy - 3], fill=(20, 30, 20, 255))
    d.rectangle([hx + 5, hy, hx + 10, hy + 1], fill=(40, 70, 40, 255))
    burst(d, hx + 8, hy - 3, 10, 13, 5, WHITE, start=-1.6, step=0.5)
    return finish(base, [fx, fig_layer(f, **cam)])


def hulk_slam():
    base = tile((255, 140, 60), (150, 40, 20), glow=(0.5, 0.8), glow_col=(255, 230, 150))
    f = bulky().set("right_arm", 60, 0, -10).set("left_arm", 60, 0, 10).set("head", 20, 0, 0)
    cam = C(32, 6, 1.55, yaw=-25, pitch=14)
    fx = layer()
    d = ImageDraw.Draw(fx)
    d.rectangle([1, 52, 61, 61], fill=(95, 62, 40, 255))
    d.line([(1, 52), (61, 52)], fill=(140, 100, 62, 255))
    for x1 in (10, 20, 44, 54):
        d.line([(32, 53), ((32 + x1) / 2, 57), (x1, 61)], fill=(30, 20, 15, 255))
    burst(d, 32, 52, 12, 18, 7, WHITE, start=math.pi, step=math.pi / 6)
    for p in [(8, 42), (54, 38), (14, 34), (50, 46)]:
        d.rectangle([p[0], p[1], p[0] + 2, p[1] + 2], fill=(120, 85, 55, 255))
    return finish(base, [fx, fig_layer(f, **cam)])


def hulk_hop():
    base = tile((140, 210, 255), (60, 120, 210), glow=(0.5, 0.3), glow_col=(255, 255, 230))
    f = bulky().set("right_arm", -30, 0, -35).set("left_arm", -30, 0, 35).set("right_leg", 50, 0, 0).set("left_leg", -15, 0, 0)
    cam = C(32, 4, 1.45, yaw=-30)
    fx = layer()
    d = ImageDraw.Draw(fx)
    for x, y, r in [(14, 57, 7), (26, 59, 6), (40, 59, 6), (52, 57, 7), (33, 56, 5)]:
        d.ellipse([x - r, y - r / 2, x + r, y + r / 2], fill=(240, 232, 215, 230))
    for x in (18, 46):
        d.line([(x, 42), (x, 50)], fill=(255, 255, 255, 200))
    return finish(base, [fx, fig_layer(f, **cam)])


def sig_micro_laser():
    base = tile((175, 30, 40), (60, 8, 20), glow=(0.72, 0.4), glow_col=(255, 130, 130))
    s, g = skin("mark_7")
    f = Figure(s, g).set("left_arm", 80, 0, 0).set("right_arm", -20, 0, -10).set("head", 0, 10, 0)
    cam = C(18, 7, 2.4)
    fx = layer()
    d = ImageDraw.Draw(fx)
    hx, hy = project(f, "left_arm", (6, 11, 0), **cam)
    for ex, ey in [(62, 8), (62, 24), (62, 40), (62, 56)]:
        d.line([(hx, hy), (ex, ey)], fill=(255, 70, 70, 255))
    d.line([(hx, hy), (62, 32)], fill=WHITE)
    plus(d, round(hx), round(hy), col=(255, 140, 140, 255), r=2)
    return finish(base, [fig_layer(f, **cam), fx])


def sig_rocket_fist():
    base = tile((255, 215, 90), (220, 130, 30), glow=(0.72, 0.38), glow_col=(255, 255, 210))
    s, g = skin("mark_42")
    f = Figure(s, g).set("right_arm", -20, 0, -10).set("head", 0, 10, 0)
    f.hidden = {"left_arm"}
    cam = C(18, 7, 2.4)
    fist = Figure(s, g).set("left_arm", 90, 0, 0)
    fist.hidden = {"head", "body", "right_arm", "right_leg", "left_leg"}
    fist.offset["left_arm"] = (0, 0, -14)
    fx = layer()
    d = ImageDraw.Draw(fx)
    fx2 = fig_layer(fist, **cam)
    m = np.asarray(fx2)[..., 3] > 0
    xs = np.where(m.any(axis=0))[0]
    ys = np.where(m.any(axis=1))[0]
    if len(xs):
        flame(d, xs.min() + 2, (ys.min() + ys.max()) / 2, -1, 0.1, 10, 6)
        burst(d, xs.max() + 2, (ys.min() + ys.max()) / 2, 3, 7, 5, WHITE, start=-1.2, step=0.6)
    return finish(base, [fig_layer(f, **cam), fx, fx2])


def sig_camouflage():
    base = tile((40, 115, 105), (10, 35, 40), glow=(0.5, 0.4), glow_col=(150, 255, 230))
    s, g = skin("mark_15")
    f = Figure(s, g).set("right_arm", 0, 0, -10).set("left_arm", 0, 0, 10)
    cam = C(32, 8, 1.55, yaw=-25)
    solid = outline(render(f, **cam))
    arr = np.asarray(solid).copy()
    y, x = np.mgrid[0:S, 0:S]
    fade = np.clip((x - 20) / 24.0, 0.0, 1.0)
    arr[..., 3] = (arr[..., 3] * (1 - fade * 0.88)).astype(np.uint8)
    ghost = Image.fromarray(arr, "RGBA")
    fx = layer()
    d = ImageDraw.Draw(fx)
    m = np.asarray(solid)[..., 3] > 0
    for yy in range(1, S - 1):
        for xx in range(1, S - 1):
            if m[yy, xx] and not (m[yy - 1, xx] and m[yy + 1, xx] and m[yy, xx - 1] and m[yy, xx + 1]) and xx > 30 and (xx + yy) % 3:
                d.point([(xx, yy)], fill=(160, 255, 235, 210))
    for r in (7, 12):
        d.arc([48 - r, 30 - r, 48 + r, 30 + r], -60, 60, fill=(160, 255, 235, 170))
    return finish(base, [ghost, fx])


def sig_starboost():
    base = tile((40, 40, 115), (10, 8, 40), glow=(0.72, 0.28), glow_col=(150, 160, 255))
    s, g = skin("mark_39")
    f = Figure(s, g).set("right_arm", 0, 0, -6).set("left_arm", 0, 0, 6).set("head", -50, 0, 0)
    f.root = (0, 0, -50)
    cam = C(34, 10, 1.5, yaw=-20, pitch=10)
    fl = fig_layer(f, **cam)
    fx = layer()
    d = ImageDraw.Draw(fx)
    rnd = random.Random(5)
    for _ in range(16):
        x, y = rnd.randint(3, 60), rnd.randint(3, 60)
        d.point([(x, y)], fill=(255, 255, 255, rnd.randint(120, 255)))
    top = layer()
    td = ImageDraw.Draw(top)
    for leg, lx in (("right_leg", -2), ("left_leg", 2)):
        x, y = project(f, leg, (lx, -0.5, 0), **cam)
        flame(td, x, y, -0.72, 0.7, 22, 7)
    x, y = project(f, "right_leg", (0, -0.5, 0), **cam)
    burst(d, x - 8, y + 8, 6, 12, 7, WHITE, start=1.2, step=0.5)
    return finish(base, [fx, fl, top])


def sig_pulse_unibeam():
    base = tile((125, 60, 205), (40, 15, 90), glow=(0.5, 0.45), glow_col=(200, 230, 255))
    s, g = skin("mark_17")
    f = Figure(s, g).set("right_arm", -25, 0, -35).set("left_arm", -25, 0, 35)
    cam = C(32, 6, 2.3, yaw=-12)
    fx = layer()
    d = ImageDraw.Draw(fx)
    cx, cy = project(f, "body", (0, 20, -2.4), **cam)
    for r, col in ((9, (200, 240, 255, 255)), (15, CYAN), (21, (90, 200, 255, 210)), (28, (90, 200, 255, 130))):
        ring(d, cx, cy, r, col, width=2 if r < 16 else 1)
    fl = fig_layer(f, **cam)
    top = layer()
    td = ImageDraw.Draw(top)
    td.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=WHITE)
    return finish(base, [fx, fl, top])


def sig_shoulder_gun():
    base = tile((125, 140, 160), (40, 45, 60), glow=(0.72, 0.3), glow_col=(255, 230, 160))
    s, g = skin("war_machine_mk2")
    f = Figure(s, g).set("right_arm", -10, 0, -10).set("left_arm", 10, 0, 10).set("head", 0, 10, 0)
    cam = C(20, 10, 2.3)
    fx = layer()
    d = ImageDraw.Draw(fx)
    sx, sy = project(f, "body", (2, 24, 0), **cam)
    d.rectangle([sx - 4, sy - 6, sx + 14, sy - 2], fill=(62, 66, 74, 255), outline=(22, 14, 30, 255))
    d.rectangle([sx + 14, sy - 5, sx + 18, sy - 3], fill=(40, 40, 46, 255), outline=(22, 14, 30, 255))
    d.ellipse([sx + 18, sy - 8, sx + 25, sy - 1], fill=(255, 250, 200, 255))
    burst(d, sx + 21, sy - 4, 4, 8, 8, YELLOW)
    for i, y in enumerate((sy - 12, sy - 4, sy + 4)):
        d.line([(sx + 30 + i * 3, y), (sx + 36 + i * 3, y)], fill=YELLOW)
    return finish(base, [fig_layer(f, **cam), fx])


def sig_slam():
    base = tile((255, 170, 70), (170, 60, 20), glow=(0.5, 0.8), glow_col=(255, 240, 170))
    s, g = skin("iron_heart_mk3")
    f = Figure(s, g).set("right_arm", 45, 0, -10).set("left_arm", 45, 0, 10).set("right_leg", 30, 0, 0).set("left_leg", -20, 0, 0).set("head", 15, 0, 0)
    cam = C(32, 6, 1.5, yaw=-25, pitch=14)
    fx = layer()
    d = ImageDraw.Draw(fx)
    d.rectangle([1, 54, 61, 61], fill=(115, 72, 40, 255))
    d.line([(1, 54), (61, 54)], fill=(155, 108, 66, 255))
    d.ellipse([8, 50, 56, 58], outline=WHITE)
    d.ellipse([1, 47, 62, 61], outline=(255, 250, 210, 170))
    for x1 in (12, 24, 40, 52):
        d.line([(32, 55), (x1, 61)], fill=(40, 25, 15, 255))
    return finish(base, [fx, fig_layer(f, **cam)])


ICONS = {
    "unibeam": unibeam,
    "missiles": missiles,
    "nano_arsenal": nano_arsenal,
    "countermeasures": countermeasures,
    "scan": scan,
    "veronica": veronica,
    "helmet": helmet,
    "suit": suit,
    "hulk_grab": hulk_grab,
    "hulk_slam": hulk_slam,
    "hulk_hop": hulk_hop,
    "sig_micro_laser": sig_micro_laser,
    "sig_rocket_fist": sig_rocket_fist,
    "sig_camouflage": sig_camouflage,
    "sig_starboost": sig_starboost,
    "sig_pulse_unibeam": sig_pulse_unibeam,
    "sig_shoulder_gun": sig_shoulder_gun,
    "sig_slam": sig_slam,
}


def build(name):
    fn = ICONS[name]
    # the tile of this icon is the border reference
    img = None
    orig_tile = globals()["tile"]

    def capture(*a, **k):
        t = orig_tile(*a, **k)
        BORDER_REF[0] = t.copy()
        return t

    globals()["tile"] = capture
    try:
        img = fn()
    finally:
        globals()["tile"] = orig_tile
    return img


def main(argv):
    preview = None
    if len(argv) > 2 and argv[1] == "--preview":
        preview = argv[2]
    images = {name: build(name) for name in ICONS}
    if preview:
        cols = 6
        rows = (len(images) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * 136, rows * 136), (50, 50, 50, 255))
        for i, (name, img) in enumerate(images.items()):
            sheet.alpha_composite(img.resize((128, 128), Image.NEAREST), ((i % cols) * 136 + 4, (i // cols) * 136 + 4))
        sheet.save(preview)
        return
    if "--legacy-overwrite" not in argv:
        sys.exit("superseded: run make_hero_icons.py --hero ironman (pass --legacy-overwrite to write the old art)")
    os.makedirs(OUT, exist_ok=True)
    for name, img in images.items():
        buf = io.BytesIO()
        img.save(buf, "PNG", optimize=True)
        with open(os.path.join(OUT, name + ".png.b64"), "w") as f:
            f.write(base64.b64encode(buf.getvalue()).decode() + "\n")
    print("wrote", len(images), "icons to", OUT)


if __name__ == "__main__":
    main(sys.argv)
