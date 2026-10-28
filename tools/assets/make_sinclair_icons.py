#!/usr/bin/env python3
"""Sinclair HUD art (own work): five 64x64 ability icons, the 32x32 hero badge, the 16x16
panel emblem and a 16x16 act card. Drawn with Pillow at 4x and reduced, then written as
<name>.png.b64 under binassets (plain PNG previews in build/sinclair-assets/icons/).
Run: python3 tools/assets/make_sinclair_icons.py [--check]
"""
import argparse
import base64
import io
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
BIN = os.path.join(ROOT, "viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui")
PREVIEW = os.path.join(ROOT, "build/sinclair-assets/icons")

INK = (21, 18, 26, 255)
VIOLET = (155, 107, 255, 255)
LAVENDER = (201, 182, 255, 255)
GOLD = (242, 193, 78, 255)
CRIMSON = (142, 27, 44, 255)
IVORY = (243, 235, 221, 255)
SAV = (176, 124, 255, 255)
K = 4


def canvas(size):
    return Image.new("RGBA", (size * K, size * K), (0, 0, 0, 0))


def finish(img, size, outline=True):
    small = img.resize((size, size), Image.LANCZOS)
    if outline:
        a = small.split()[3].point(lambda v: 255 if v > 40 else 0)
        grown = a.filter(ImageFilter.MaxFilter(3))
        ring = Image.new("RGBA", small.size, (10, 8, 14, 255))
        base = Image.new("RGBA", small.size, (0, 0, 0, 0))
        base.paste(ring, mask=grown)
        base.alpha_composite(small)
        small = base
    return small


def backdrop(d, s, top, bottom):
    for y in range(s):
        t = y / s
        col = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3)) + (255,)
        d.line([(0, y), (s, y)], fill=col)


def rounded_bg(size, top, bottom):
    s = size * K
    bg = canvas(size)
    backdrop(ImageDraw.Draw(bg), s, top, bottom)
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([2 * K, 2 * K, s - 2 * K, s - 2 * K], radius=10 * K, fill=255)
    out = canvas(size)
    out.paste(bg, mask=mask)
    ImageDraw.Draw(out).rounded_rectangle([2 * K, 2 * K, s - 2 * K, s - 2 * K], radius=10 * K, outline=GOLD, width=2 * K)
    return out


def top_hat(d, cx, base, w, h, band=VIOLET, tilt=0.0):
    brim = [cx - w * 0.75, base - h * 0.1, cx + w * 0.75, base + h * 0.06]
    d.rounded_rectangle(brim, radius=int(h * 0.05) + 1, fill=INK)
    d.rectangle([cx - w / 2, base - h, cx + w / 2, base - h * 0.08], fill=INK)
    d.rectangle([cx - w / 2 - w * 0.04, base - h - h * 0.08, cx + w / 2 + w * 0.04, base - h + h * 0.04], fill=(40, 34, 48, 255))
    d.rectangle([cx - w / 2, base - h * 0.34, cx + w / 2, base - h * 0.12], fill=band)
    d.rectangle([cx - w * 0.08, base - h * 0.34, cx + w * 0.08, base - h * 0.12], fill=GOLD)
    d.line([cx + w * 0.3, base - h * 0.95, cx + w * 0.3, base - h * 0.4], fill=(70, 62, 80, 255), width=max(1, int(w * 0.05)))


def card(d, cx, cy, w, h, angle=0.0, face=True):
    pts = [(-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)]
    ca, sa = math.cos(angle), math.sin(angle)
    poly = [(cx + x * ca - y * sa, cy + x * sa + y * ca) for x, y in pts]
    d.polygon(poly, fill=IVORY if face else CRIMSON, outline=GOLD)
    d.line(poly + [poly[0]], fill=GOLD, width=max(1, int(w * 0.07)))
    r = w * 0.22
    diamond = [(cx - r * sa * 0 + 0, cy - r * 1.3), (cx + r, cy), (cx, cy + r * 1.3), (cx - r, cy)]
    diamond = [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in diamond]
    d.polygon(diamond, fill=CRIMSON if face else GOLD)


def star(d, cx, cy, r, fill, points=5, inner=0.45, rot=-math.pi / 2):
    pts = []
    for i in range(points * 2):
        rr = r if i % 2 == 0 else r * inner
        a = rot + i * math.pi / points
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=fill)


def glow(img, color, radius):
    a = img.split()[3].filter(ImageFilter.GaussianBlur(radius))
    layer = Image.new("RGBA", img.size, color)
    layer.putalpha(a.point(lambda v: int(v * 0.8)))
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.alpha_composite(layer)
    out.alpha_composite(img)
    return out


def icon_vexing_bolt():
    img = rounded_bg(64, (44, 20, 70), (14, 10, 22))
    s = 64 * K
    fx = canvas(64)
    d = ImageDraw.Draw(fx)
    for i in range(70):
        t = i / 70
        a = t * math.pi * 4.2
        r = (1 - t) * 22 * K
        x = s * 0.56 + math.cos(a) * r
        y = s * 0.46 + math.sin(a) * r * 0.8
        w = (2 + 4 * t) * K
        d.ellipse([x - w, y - w, x + w, y + w], fill=(int(155 + 90 * t), int(107 + 120 * t), 255, 255))
    d.ellipse([s * 0.56 - 7 * K, s * 0.46 - 7 * K, s * 0.56 + 7 * K, s * 0.46 + 7 * K], fill=(250, 244, 255, 255))
    d.line([10 * K, 54 * K, 26 * K, 38 * K], fill=INK, width=3 * K)
    d.line([24 * K, 40 * K, 30 * K, 34 * K], fill=IVORY, width=3 * K)
    for x, y in ((16, 18), (46, 52), (52, 20)):
        star(d, x * K, y * K, 3.5 * K, GOLD)
    img.alpha_composite(glow(fx, VIOLET, 3 * K))
    return finish(img, 64)


def icon_assistant():
    img = rounded_bg(64, (40, 24, 74), (12, 10, 22))
    fx = canvas(64)
    d = ImageDraw.Draw(fx)
    d.ellipse([26 * K, 9 * K, 38 * K, 22 * K], fill=LAVENDER)
    d.polygon([(25 * K, 10 * K), (39 * K, 10 * K), (42 * K, 34 * K), (22 * K, 34 * K)], fill=(91, 58, 168, 255))
    d.ellipse([27 * K, 11 * K, 37 * K, 22 * K], fill=LAVENDER)
    d.polygon([(26 * K, 23 * K), (38 * K, 23 * K), (40 * K, 34 * K), (24 * K, 34 * K)], fill=(122, 79, 217, 255))
    for i, (w, y0, col) in enumerate(((16, 33, (148, 102, 240, 255)), (22, 40, SAV), (28, 47, (194, 155, 255, 255)))):
        d.polygon([(32 * K - w / 2 * K, (y0 + 7) * K), (32 * K + w / 2 * K, (y0 + 7) * K), (32 * K + (w / 2 - 4) * K, y0 * K),
                   (32 * K - (w / 2 - 4) * K, y0 * K)], fill=col)
        d.line([(32 * K - w / 2 * K, (y0 + 7) * K), (32 * K + w / 2 * K, (y0 + 7) * K)], fill=LAVENDER, width=K)
    d.polygon([(26 * K, 54 * K), (38 * K, 54 * K), (33 * K, 62 * K)], fill=(184, 146, 255, 160))
    d.line([(24 * K, 26 * K), (12 * K, 18 * K)], fill=(228, 218, 255, 255), width=3 * K)
    d.line([(40 * K, 26 * K), (52 * K, 18 * K)], fill=(228, 218, 255, 255), width=3 * K)
    d.ellipse([29 * K, 15 * K, 31 * K, 16.5 * K], fill=(255, 255, 255, 255))
    d.ellipse([33 * K, 15 * K, 35 * K, 16.5 * K], fill=(255, 255, 255, 255))
    img.alpha_composite(glow(fx, SAV, 4 * K))
    d2 = ImageDraw.Draw(img)
    card(d2, 52 * K, 14 * K, 9 * K, 13 * K, 0.3)
    return finish(img, 64)


def icon_rabbit_hex():
    img = rounded_bg(64, (30, 22, 46), (10, 8, 16))
    d = ImageDraw.Draw(img)
    for x0, tilt in ((24, -0.25), (36, 0.25)):
        cx = x0 * K
        pts = [(cx - 4 * K, 34 * K), (cx + 4 * K, 34 * K), (cx + (4 + tilt * 20) * K, 10 * K), (cx + (-2 + tilt * 20) * K, 8 * K)]
        d.polygon(pts, fill=(247, 244, 238, 255))
        inner = [(cx - 2 * K, 32 * K), (cx + 2 * K, 32 * K), (cx + (2.5 + tilt * 18) * K, 13 * K), (cx + (-0.5 + tilt * 18) * K, 12 * K)]
        d.polygon(inner, fill=(242, 184, 198, 255))
    top_hat(d, 32 * K, 54 * K, 30 * K, 24 * K)
    fx = canvas(64)
    fd = ImageDraw.Draw(fx)
    for x, y, r in ((12, 20, 4), (52, 26, 3.5), (48, 10, 3)):
        star(fd, x * K, y * K, r * K, GOLD)
    img.alpha_composite(glow(fx, GOLD, 2 * K))
    return finish(img, 64)


def magician(d, cx, base, h, col, hat_col):
    w = h * 0.36
    d.rectangle([cx - w * 0.35, base - h * 0.48, cx - w * 0.08, base], fill=col)
    d.rectangle([cx + w * 0.08, base - h * 0.48, cx + w * 0.35, base], fill=col)
    d.polygon([(cx - w / 2, base - h * 0.45), (cx + w / 2, base - h * 0.45), (cx + w * 0.55, base - h * 0.78),
               (cx - w * 0.55, base - h * 0.78)], fill=col)
    d.ellipse([cx - w * 0.28, base - h * 0.98, cx + w * 0.28, base - h * 0.76], fill=IVORY if col[3] == 255 else (243, 235, 221, col[3]))
    top_hat(d, cx, base - h * 0.95, w * 0.5, h * 0.24, band=hat_col)


def icon_misdirection():
    img = rounded_bg(64, (24, 18, 40), (10, 8, 16))
    ghost = canvas(64)
    gd = ImageDraw.Draw(ghost)
    magician(gd, 22 * K, 58 * K, 44 * K, (155, 107, 255, 150), (201, 182, 255, 150))
    img.alpha_composite(ghost)
    d = ImageDraw.Draw(img)
    magician(d, 42 * K, 58 * K, 44 * K, (30, 26, 36, 255), VIOLET)
    d.line([(14 * K, 40 * K), (30 * K, 40 * K)], fill=(201, 182, 255, 200), width=2 * K)
    d.line([(10 * K, 46 * K), (28 * K, 46 * K)], fill=(201, 182, 255, 140), width=2 * K)
    fx = canvas(64)
    star(ImageDraw.Draw(fx), 52 * K, 16 * K, 8 * K, (255, 246, 200, 255), points=4, inner=0.25)
    img.alpha_composite(glow(fx, GOLD, 3 * K))
    return finish(img, 64)


def icon_audience():
    img = rounded_bg(64, (36, 14, 24), (10, 8, 16))
    s = 64 * K
    beam = canvas(64)
    bd = ImageDraw.Draw(beam)
    bd.polygon([(28 * K, 4 * K), (36 * K, 4 * K), (54 * K, 58 * K), (10 * K, 58 * K)], fill=(255, 238, 190, 90))
    bd.ellipse([10 * K, 52 * K, 54 * K, 62 * K], fill=(255, 238, 190, 120))
    img.alpha_composite(beam.filter(ImageFilter.GaussianBlur(2 * K)))
    d = ImageDraw.Draw(img)
    card(d, 32 * K, 34 * K, 18 * K, 26 * K, -0.18)
    fx = canvas(64)
    fd = ImageDraw.Draw(fx)
    for x, y in ((14, 18), (50, 22), (44, 50), (18, 46)):
        star(fd, x * K, y * K, 3 * K, GOLD)
    img.alpha_composite(glow(fx, GOLD, 2 * K))
    del s
    return finish(img, 64)


def hero_badge():
    img = canvas(32)
    d = ImageDraw.Draw(img)
    d.ellipse([3 * K, 4 * K, 29 * K, 31 * K], fill=(36, 22, 52, 255), outline=GOLD, width=K)
    d.ellipse([10 * K, 12 * K, 22 * K, 27 * K], fill=IVORY)
    d.line([(11 * K, 21.5 * K), (16 * K, 20.5 * K), (21 * K, 21.5 * K)], fill=INK, width=int(1.4 * K))
    d.line([(10.5 * K, 20.5 * K), (11 * K, 21.5 * K)], fill=INK, width=K)
    d.line([(21.5 * K, 20.5 * K), (21 * K, 21.5 * K)], fill=INK, width=K)
    top_hat(d, 16 * K, 13 * K, 12 * K, 11 * K)
    return finish(img, 32)


def emblem():
    img = canvas(16)
    d = ImageDraw.Draw(img)
    top_hat(d, 8 * K, 13 * K, 8 * K, 9 * K)
    return finish(img, 16)


def act_card():
    img = canvas(16)
    d = ImageDraw.Draw(img)
    card(d, 8 * K, 8 * K, 9 * K, 13 * K, 0.12)
    return finish(img, 16)


ICONS = {
    "ability/sinclair/vexing_bolt": icon_vexing_bolt,
    "ability/sinclair/spectral_assistant": icon_assistant,
    "ability/sinclair/rabbit_hex": icon_rabbit_hex,
    "ability/sinclair/misdirection": icon_misdirection,
    "ability/sinclair/audience_participation": icon_audience,
    "sinclair/hero": hero_badge,
    "sinclair/emblem": emblem,
    "sinclair/act_card": act_card,
}


def b64(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return base64.b64encode(buf.getvalue()).decode("ascii")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    stale = []
    os.makedirs(PREVIEW, exist_ok=True)
    for name, fn in ICONS.items():
        img = fn()
        path = os.path.join(BIN, name + ".png.b64")
        text = b64(img)
        if args.check:
            if not os.path.isfile(path) or open(path).read() != text:
                stale.append(name)
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as fh:
            fh.write(text)
        img.save(os.path.join(PREVIEW, name.replace("/", "_") + ".png"))
    for n in stale:
        print("stale:", n)
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
