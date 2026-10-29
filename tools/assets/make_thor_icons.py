#!/usr/bin/env python3
"""Thor ability icons (own pixel art, CC0 by the project): thunder, onslaught, wrath, teleport.

Drawn on a 32x32 grid and scaled x2 (nearest) to the 64x64 panel size, written as
base64 into binassets (decoded by Gradle at build time). Deterministic.

    python3 tools/assets/make_thor_icons.py [--out DIR] [--preview PNG]
"""
import argparse
import base64
import io
import math
import os

from PIL import Image, ImageDraw

GRID = 32
SCALE = 2
DEFAULT_OUT = "viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/ability/thor"

FRAME = (201, 162, 88, 255)
FRAME_DARK = (92, 66, 34, 255)
BOLT_EDGE = (86, 196, 255, 255)
BOLT_CORE = (236, 250, 255, 255)


def background(glow):
    """Storm-blue vertical gradient with a soft radial glow in the middle."""
    img = Image.new("RGBA", (GRID, GRID))
    px = img.load()
    for y in range(GRID):
        for x in range(GRID):
            t = y / (GRID - 1)
            base = (int(16 + 18 * t), int(20 + 22 * t), int(38 + 36 * t))
            d = math.hypot(x - 15.5, y - 15.5) / 16.0
            g = max(0.0, 1.0 - d) ** 2
            col = tuple(min(255, int(base[i] + glow[i] * g)) for i in range(3))
            # 4-level posterisation keeps the pixel-art banding of the other heroes' icons.
            col = tuple((c // 6) * 6 for c in col)
            px[x, y] = col + (255,)
    return img


def frame(img):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, GRID - 1, GRID - 1], outline=FRAME)
    d.rectangle([1, 1, GRID - 2, GRID - 2], outline=FRAME_DARK)
    for x, y in ((0, 0), (GRID - 1, 0), (0, GRID - 1), (GRID - 1, GRID - 1)):
        img.putpixel((x, y), (0, 0, 0, 0))


def bolt(d, points, core_points):
    d.polygon(points, fill=BOLT_EDGE)
    d.polygon(core_points, fill=BOLT_CORE)


def sparks(img, coords, colour=BOLT_CORE):
    for x, y in coords:
        img.putpixel((x, y), colour)


def thunder():
    img = background((40, 110, 170))
    d = ImageDraw.Draw(img)
    # Storm cloud along the top.
    for cx, cy, r in ((9, 7, 4), (15, 6, 5), (22, 7, 4), (12, 9, 3), (19, 9, 3)):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(70, 82, 112, 255))
    for cx, cy, r in ((14, 5, 3), (21, 6, 2)):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(104, 118, 150, 255))
    bolt(d, [(18, 8), (10, 19), (15, 19), (12, 29), (23, 15), (18, 15), (22, 8)],
         [(18, 10), (12, 18), (16, 18), (14, 26), (21, 16), (17, 16), (20, 10)])
    sparks(img, [(7, 22), (25, 20), (24, 26), (8, 27), (26, 13)])
    frame(img)
    return img


def onslaught():
    img = background((30, 90, 160))
    d = ImageDraw.Draw(img)
    # Speed lines trailing to the lower left.
    for i, (y, length) in enumerate(((20, 8), (24, 10), (28, 6))):
        shade = (120 - i * 20, 170 - i * 20, 230 - i * 20, 255)
        d.line([(3, y), (3 + length, y - length // 2)], fill=shade)
    # A zig-zag bolt shooting up-right, ending in an arrow head.
    path = [(5, 27), (13, 19), (11, 17), (21, 10)]
    d.line(path, fill=BOLT_EDGE, width=3, joint="curve")
    d.line(path, fill=BOLT_CORE, width=1)
    d.polygon([(27, 4), (17, 7), (24, 14)], fill=BOLT_EDGE)
    d.polygon([(25, 6), (20, 8), (23, 11)], fill=BOLT_CORE)
    sparks(img, [(26, 18), (15, 25), (9, 12), (27, 23)])
    frame(img)
    return img


def wrath():
    img = background((150, 60, 30))
    d = ImageDraw.Draw(img)
    # Lightning rays behind the hammer.
    for (x0, y0, x1, y1) in ((16, 15, 4, 4), (16, 15, 28, 5), (16, 15, 3, 21), (16, 15, 29, 22)):
        mx, my = (x0 + x1) // 2 + 2, (y0 + y1) // 2 - 1
        d.line([(x0, y0), (mx, my), (x1, y1)], fill=BOLT_EDGE, width=2)
        d.line([(x0, y0), (mx, my), (x1, y1)], fill=BOLT_CORE, width=1)
    # Mjölnir: head, runes, handle with leather wrap, pommel.
    d.rectangle([8, 6, 23, 14], fill=(122, 128, 140, 255))
    d.rectangle([8, 6, 23, 7], fill=(176, 182, 194, 255))
    d.rectangle([8, 13, 23, 14], fill=(78, 82, 94, 255))
    d.rectangle([8, 6, 9, 14], fill=(96, 100, 112, 255))
    d.rectangle([22, 6, 23, 14], fill=(96, 100, 112, 255))
    for x in (12, 15, 18):
        d.line([(x, 9), (x + 1, 11)], fill=(120, 210, 255, 255))
    d.rectangle([15, 15, 17, 26], fill=(104, 66, 38, 255))
    for y in range(16, 26, 2):
        d.line([(15, y), (17, y)], fill=(140, 94, 56, 255))
    d.rectangle([14, 26, 18, 28], fill=(150, 154, 166, 255))
    d.point((16, 29), fill=(104, 66, 38, 255))
    sparks(img, [(5, 12), (27, 13), (6, 28), (26, 28)], (255, 214, 150, 255))
    frame(img)
    return img


def teleport():
    img = background((60, 70, 150))
    d = ImageDraw.Draw(img)
    # Top-down ground marker: two rings and a cross, a bolt striking its centre.
    d.ellipse([6, 14, 26, 28], outline=(120, 170, 240, 255))
    d.ellipse([10, 17, 22, 25], outline=(170, 210, 255, 255))
    d.line([(16, 15), (16, 27)], fill=(120, 170, 240, 255))
    d.line([(7, 21), (25, 21)], fill=(120, 170, 240, 255))
    bolt(d, [(19, 3), (12, 12), (16, 12), (14, 21), (21, 10), (17, 10), (21, 3)],
         [(19, 5), (14, 11), (17, 11), (15, 18), (19, 11), (16, 11), (19, 5)])
    sparks(img, [(5, 8), (27, 9), (26, 27), (6, 27)])
    frame(img)
    return img


ICONS = {"thunder": thunder, "onslaught": onslaught, "wrath": wrath, "teleport": teleport}


def render(name):
    return ICONS[name]().resize((GRID * SCALE, GRID * SCALE), Image.NEAREST)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", default=DEFAULT_OUT)
    parser.add_argument("--preview")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    images = []
    for name in ICONS:
        img = render(name)
        images.append(img)
        buf = io.BytesIO()
        img.save(buf, format="PNG", optimize=True)
        with open(os.path.join(args.out, name + ".png.b64"), "w", encoding="ascii") as fh:
            fh.write(base64.b64encode(buf.getvalue()).decode("ascii") + "\n")
        print("wrote", name)
    if args.preview:
        sheet = Image.new("RGBA", (len(images) * 276, 256), (60, 60, 60, 255))
        for i, img in enumerate(images):
            sheet.alpha_composite(img.resize((256, 256), Image.NEAREST), (i * 276, 0))
        sheet.save(args.preview)


if __name__ == "__main__":
    main()
