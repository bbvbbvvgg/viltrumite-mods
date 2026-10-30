#!/usr/bin/env python3
"""Od Lagna's Wish art (own work, CC0): the 16x16 item, its 64x64 preview and the GUI halo.

python tools/assets/make_od_lagna_assets.py   -> writes the base64 assets under viltrumitecore/src/main/binassets
"""
import base64, io, math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BIN = ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/textures'

OUTLINE = (74, 46, 14, 255)
GOLD_D = (176, 118, 34, 255)
GOLD = (226, 170, 58, 255)
GOLD_L = (250, 222, 128, 255)
WHITE = (255, 255, 255, 255)
CREAM = (255, 246, 220, 255)
BLUE_L = (196, 228, 255, 255)
BLUE = (128, 182, 240, 255)
BLUE_D = (70, 118, 196, 255)
BLUE_X = (44, 74, 140, 255)

def item():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    cx, cy, r = 7.5, 7.0, 4.3
    # orb: light from the upper left, soft blue body, deep rim
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                h = math.hypot(x - (cx - 1.6), y - (cy - 1.7))
                if h < 1.0:
                    c = WHITE
                elif h < 2.0:
                    c = CREAM
                elif h < 3.1:
                    c = BLUE_L
                elif d < r - 1.0:
                    c = BLUE
                elif d < r - 0.35:
                    c = BLUE_D
                else:
                    c = BLUE_X
                px[x, y] = c
    # the golden halo: a tilted ring, back half behind the orb, front half over it
    rx, ry, ringy = 6.6, 2.0, 8.6
    for y in range(16):
        for x in range(16):
            e = ((x - cx) / rx) ** 2 + ((y - ringy) / ry) ** 2
            if 0.62 <= e <= 1.18:
                front = y >= ringy
                inside_orb = math.hypot(x - cx, y - cy) <= r
                if front or not inside_orb:
                    shade = GOLD_L if y < ringy - 0.5 else (GOLD if y < ringy + 1.2 else GOLD_D)
                    px[x, y] = shade
    # the claw that holds it
    for (x, y, c) in [(6, 12, GOLD_D), (9, 12, GOLD_D), (7, 13, GOLD), (8, 13, GOLD), (7, 14, GOLD_D), (8, 14, GOLD_D), (5, 12, GOLD), (10, 12, GOLD)]:
        px[x, y] = c
    # dark outline around every solid pixel
    solid = {(x, y) for y in range(16) for x in range(16) if px[x, y][3] > 0}
    for (x, y) in list(solid):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in solid and px[nx, ny][3] == 0:
                px[nx, ny] = OUTLINE
    # sparkles (no outline): one four-point star, one twinkle
    for (x, y, c) in [(13, 1, CREAM), (13, 3, CREAM), (12, 2, CREAM), (14, 2, CREAM), (13, 2, WHITE), (2, 2, GOLD_L), (1, 13, BLUE_L), (14, 12, GOLD_L)]:
        if px[x, y][3] == 0:
            px[x, y] = c
    return im

def halo(size=128):
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = im.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            if d < 1:
                a = (1 - d) ** 2.2
                px[x, y] = (255, 255, 255, int(255 * a))
    return im

def b64(path, im):
    path.parent.mkdir(parents=True, exist_ok=True)
    buf = io.BytesIO(); im.save(buf, 'PNG', optimize=True)
    path.with_name(path.name + '.b64').write_text(base64.encodebytes(buf.getvalue()).decode('ascii'), encoding='ascii')

if __name__ == '__main__':
    icon = item()
    b64(BIN / 'item/od_lagna_wish.png', icon)
    b64(BIN / 'gui/od_lagna/halo.png', halo())
    icon.resize((256, 256), Image.NEAREST).save('/tmp/od_lagna_wish_preview.png')
    print('ok')
