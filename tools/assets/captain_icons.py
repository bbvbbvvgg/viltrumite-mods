"""Captain America ability icons (64x64), hero badge (32x32) and panel emblem (16x16) — own pixel art, CC0.
Writes base64 PNGs under viltrumitecore/src/main/binassets (the build decodes them).
Run: python3 tools/assets/captain_icons.py [--preview out.png]"""
import base64
import io
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore', 'src', 'main', 'binassets',
                    'assets', 'viltrumitecore', 'textures', 'gui')
S = 64
NAVY = (22, 34, 66)
NAVY_HI = (52, 78, 132)
RED = (200, 40, 58)
RED_DARK = (120, 20, 34)
SILVER = (214, 222, 236)
SILVER_DARK = (120, 132, 156)
WHITE = (250, 250, 255)
BLUE = (40, 84, 170)
KINETIC = (110, 190, 255)
GOLD = (236, 196, 92)
INK = (10, 14, 26)


def canvas(size=S):
    return Image.new('RGBA', (size, size), (0, 0, 0, 0))


def background(img, rim):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((1, 1, S - 2, S - 2), 9, fill=(8, 12, 24, 255))
    d.rounded_rectangle((3, 3, S - 4, S - 4), 7, fill=NAVY + (255,))
    for r in range(27, 0, -1):
        t = 1 - r / 27
        d.ellipse((32 - r, 32 - r, 32 + r, 32 + r),
                  fill=(int(NAVY[0] + 30 * t), int(NAVY[1] + 44 * t), int(NAVY[2] + 66 * t), 255))
    d.rounded_rectangle((1, 1, S - 2, S - 2), 9, outline=rim + (255,), width=2)
    d.rounded_rectangle((4, 4, S - 5, S - 5), 6, outline=(rim[0] // 3, rim[1] // 3, rim[2] // 3, 255), width=1)
    # red-white stripe at the bottom of the frame
    d.line((12, S - 6, S - 13, S - 6), fill=RED + (255,))
    d.line((12, S - 7, S - 13, S - 7), fill=WHITE + (160,))


def add_glow(img, draw_fn, color, radius=2.2, strength=1.6, core=True):
    layer = canvas(img.size[0])
    draw_fn(ImageDraw.Draw(layer), color)
    blur = layer.filter(ImageFilter.GaussianBlur(radius))
    px = blur.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, min(255, int(a * strength)))
    img.alpha_composite(blur)
    if core:
        img.alpha_composite(layer)


def star(d, cx, cy, r, color, rot=-90.0):
    pts = []
    for i in range(10):
        rr = r if i % 2 == 0 else r * 0.42
        a = math.radians(rot + i * 36)
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=color + (255,))


def shield(d, cx, cy, r, squash=1.0, outline=True):
    """Concentric red/white/red rings, blue disc, white star (squash < 1 = seen at an angle)."""
    rings = [(1.0, RED), (0.8, SILVER), (0.6, RED), (0.42, BLUE)]
    for k, c in rings:
        rx, ry = r * k, r * k * squash
        d.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=c + (255,))
    if squash > 0.6:
        star(d, cx, cy, r * 0.38, WHITE)
    else:
        d.line((cx - r * 0.3, cy, cx + r * 0.3, cy), fill=WHITE + (255,), width=2)
    if outline:
        d.ellipse((cx - r, cy - r * squash, cx + r, cy + r * squash), outline=INK + (255,), width=1)
        # rim highlight
        d.arc((cx - r + 1, cy - r * squash + 1, cx + r - 1, cy + r * squash - 1), 200, 290, fill=WHITE + (200,), width=1)


def icon_ricochet(img):
    d = ImageDraw.Draw(img)
    # bounce path: three dashed segments zig-zagging through targets
    path = [(10, 46), (26, 14), (40, 40), (54, 16)]

    def trail(dd, c):
        for (x0, y0), (x1, y1) in zip(path, path[1:]):
            dd.line((x0, y0, x1, y1), fill=c + (255,), width=2)
    add_glow(img, trail, KINETIC, radius=1.8, strength=1.4)
    for x, y in path[1:3]:
        d.ellipse((x - 3, y - 3, x + 3, y + 3), fill=GOLD + (255,))
        d.line((x - 5, y, x + 5, y), fill=WHITE + (255,))
        d.line((x, y - 5, x, y + 5), fill=WHITE + (255,))
    shield(d, 47, 23, 10, squash=0.55)
    d.ellipse((6, 42, 14, 50), outline=SILVER + (255,), width=1)


def icon_shield_rush(img):
    d = ImageDraw.Draw(img)
    for i, y in enumerate((18, 26, 34, 42, 50)):
        x0 = 8 + (i % 2) * 4
        d.line((x0, y, x0 + 16 + (i % 3) * 4, y), fill=(SILVER if i % 2 else KINETIC) + (230,), width=2)
    add_glow(img, lambda dd, c: dd.ellipse((26, 12, 58, 52), outline=c + (255,), width=3), KINETIC, radius=2.6, strength=1.2,
             core=False)
    shield(d, 42, 32, 17, squash=1.0)
    d.arc((22, 12, 62, 52), 120, 240, fill=WHITE + (220,), width=2)


def icon_vibranium_slam(img):
    d = ImageDraw.Draw(img)
    # ground cracks and a shock ring
    d.polygon([(4, 50), (60, 50), (60, 58), (4, 58)], fill=(54, 46, 40, 255))
    for x0, x1 in ((32, 12), (32, 52), (32, 22), (32, 44)):
        d.line((x0, 50, x1, 57), fill=INK + (255,), width=1)

    def ring(dd, c):
        dd.ellipse((6, 42, 58, 56), outline=c + (255,), width=2)
        dd.ellipse((16, 45, 48, 53), outline=c + (255,), width=1)
    add_glow(img, ring, KINETIC, radius=2.0, strength=1.5)
    # shield edge-on, driven into the ground
    shield(d, 32, 30, 15, squash=1.0)
    d.polygon([(26, 6), (38, 6), (32, 12)], fill=WHITE + (255,))
    d.line((32, 2, 32, 8), fill=WHITE + (255,), width=2)


def icon_tactical_roll(img):
    d = ImageDraw.Draw(img)

    def arrow(dd, c):
        dd.arc((10, 10, 54, 54), 200, 520, fill=c + (255,), width=4)
        a = math.radians(160)
        tx, ty = 32 + 22 * math.cos(a), 32 + 22 * math.sin(a)
        dd.polygon([(tx - 7, ty - 3), (tx + 5, ty - 6), (tx + 1, ty + 6)], fill=c + (255,))
    add_glow(img, arrow, SILVER, radius=1.6, strength=1.3)
    shield(d, 32, 32, 12)


def icon_assemble(img):
    d = ImageDraw.Draw(img)
    # six team stars in a circle around the A
    for i in range(6):
        a = math.radians(-90 + i * 60)
        x, y = 32 + 21 * math.cos(a), 32 + 21 * math.sin(a)
        col = (RED, GOLD, (90, 200, 90), (230, 230, 240), (140, 60, 200), KINETIC)[i]
        star(d, x, y, 5, col)

    def a_mark(dd, c):
        dd.ellipse((18, 18, 46, 46), outline=c + (255,), width=3)
        dd.polygon([(32, 21), (42, 44), (37, 44), (32, 32), (27, 44), (22, 44)], fill=c + (255,))
        dd.line((26, 38, 38, 38), fill=c + (255,), width=2)
    add_glow(img, a_mark, (255, 236, 200), radius=2.0, strength=1.3)
    # the A's arrow tip sweeps out of the circle
    d.line((44, 18, 50, 12), fill=WHITE + (255,), width=2)


ICONS = {
    'ricochet': (icon_ricochet, SILVER),
    'shield_rush': (icon_shield_rush, SILVER),
    'vibranium_slam': (icon_vibranium_slam, KINETIC),
    'tactical_roll': (icon_tactical_roll, SILVER),
    'assemble': (icon_assemble, RED),
}


def make_icon(key):
    fn, rim = ICONS[key]
    img = canvas()
    background(img, rim)
    fn(img)
    return img


def badge():
    """32x32 race-screen badge: the star shield."""
    img = canvas(32)
    d = ImageDraw.Draw(img)
    shield(d, 16, 16, 14.5)
    return img


def emblem():
    """16x16 panel emblem: a tiny star shield."""
    img = canvas(16)
    d = ImageDraw.Draw(img)
    for k, c in ((7.5, RED), (6.0, SILVER), (4.5, RED), (3.2, BLUE)):
        d.ellipse((8 - k, 8 - k, 8 + k - 1, 8 + k - 1), fill=c + (255,))
    star(d, 7.5, 7.7, 2.8, WHITE)
    return img


def write(img, rel):
    path = os.path.join(ROOT, rel + '.b64')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    buf = io.BytesIO()
    img.save(buf, 'PNG', optimize=True)
    with open(path, 'w') as f:
        f.write(base64.b64encode(buf.getvalue()).decode())


def main():
    icons = {k: make_icon(k) for k in ICONS}
    for k, img in icons.items():
        write(img, f'ability/captain/{k}.png')
    write(badge(), 'captain/hero.png')
    write(emblem(), 'captain/emblem.png')
    if '--preview' in sys.argv:
        out = sys.argv[sys.argv.index('--preview') + 1]
        sheet = Image.new('RGBA', (len(icons) * 136 + 80, 136), (40, 40, 40, 255))
        for i, img in enumerate(icons.values()):
            sheet.alpha_composite(img.resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
        sheet.alpha_composite(badge().resize((64, 64), Image.NEAREST), (len(icons) * 136 + 8, 4))
        sheet.alpha_composite(emblem().resize((48, 48), Image.NEAREST), (len(icons) * 136 + 16, 76))
        sheet.save(out)


if __name__ == '__main__':
    main()
