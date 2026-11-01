"""Red Hulk ability icons (64x64), hero badge (32x32) and panel emblem (16x16) -- own pixel art, CC0.
Charred iron tiles with a molten rim; every motif glows from dark red through orange to yellow-white, the colours of
his heat gauge. Writes base64 PNGs under viltrumitecore/src/main/binassets (the build decodes them).
Run: python3 tools/assets/red_hulk_icons.py [--preview out.png]"""
import base64
import io
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore', 'src', 'main', 'binassets',
                    'assets', 'viltrumitecore', 'textures', 'gui')
S = 64
CHAR = (26, 14, 12)
CHAR_HI = (70, 30, 22)
SKIN = (196, 40, 30)
SKIN_DARK = (112, 18, 14)
SKIN_HI = (236, 92, 60)
MOLTEN = (255, 120, 30)
HOT = (255, 196, 70)
WHITE_HOT = (255, 246, 214)
EYE = (255, 236, 90)
INK = (12, 6, 6)
STONE = (92, 80, 74)
STONE_DARK = (52, 44, 40)


def canvas(size=S):
    return Image.new('RGBA', (size, size), (0, 0, 0, 0))


def background(img, rim=MOLTEN):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((1, 1, S - 2, S - 2), 9, fill=(10, 5, 4, 255))
    d.rounded_rectangle((3, 3, S - 4, S - 4), 7, fill=CHAR + (255,))
    for r in range(28, 0, -1):
        t = 1 - r / 28
        d.ellipse((32 - r, 34 - r, 32 + r, 34 + r), fill=(int(CHAR[0] + 50 * t), int(CHAR[1] + 18 * t), int(CHAR[2] + 8 * t), 255))
    d.rounded_rectangle((1, 1, S - 2, S - 2), 9, outline=rim + (255,), width=2)
    d.rounded_rectangle((3, 3, S - 4, S - 4), 7, outline=(90, 30, 16, 255), width=1)


def add_glow(img, draw_fn, color, radius=2.4, strength=1.7, core=True):
    layer = canvas(img.size[0])
    draw_fn(ImageDraw.Draw(layer), color)
    glow = layer.filter(ImageFilter.GaussianBlur(radius))
    for _ in range(int(strength)):
        img.alpha_composite(glow)
    if strength % 1:
        g = glow.copy()
        g.putalpha(g.getchannel('A').point(lambda a: int(a * (strength % 1))))
        img.alpha_composite(g)
    if core:
        img.alpha_composite(layer)


def flame(d, cx, base, h, w, color):
    """A flame tongue: a pointed teardrop from (cx, base) up h pixels."""
    pts = []
    for i in range(21):
        t = i / 20
        y = base - h * t
        x = w * math.sin(math.pi * t) * (1 - t * 0.4)
        pts.append((cx - x, y))
    for i in range(20, -1, -1):
        t = i / 20
        y = base - h * t
        x = w * math.sin(math.pi * t) * (1 - t * 0.4)
        pts.append((cx + x * 0.9, y))
    d.polygon(pts, fill=color)


def fist(d, cx, cy, s, side=1, color=SKIN):
    """A clenched fist seen from the knuckles (side = 1 faces right)."""
    w, h = 14 * s, 12 * s
    d.rounded_rectangle((cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), int(4 * s), fill=color + (255,), outline=INK + (255,))
    for k in range(4):
        x = cx - w / 2 + (k + 0.5) * w / 4
        d.ellipse((x - 2 * s, cy - h / 2 - 1.5 * s, x + 2 * s, cy - h / 2 + 2.5 * s), fill=SKIN_HI + (255,), outline=INK + (255,))
    d.line((cx - w / 2 + 2 * s, cy + 1 * s, cx + w / 2 - 2 * s, cy + 1 * s), fill=SKIN_DARK + (255,), width=max(1, int(s)))
    tx = cx + side * (w / 2 - 3 * s)
    d.rounded_rectangle((tx - 3 * s, cy + 1 * s, tx + 3 * s, cy + h / 2 + 2 * s), int(2 * s), fill=SKIN + (255,), outline=INK + (255,))


def palm(d, cx, cy, side):
    """An open palm in profile, fingers up, facing the centre (side = -1 on the left)."""
    w = 9
    d.rounded_rectangle((cx - w / 2, cy - 4, cx + w / 2, cy + 14), 3, fill=SKIN + (255,), outline=INK + (255,))
    for k in range(4):
        fx = cx - w / 2 + 1 + k * 2.2
        d.rounded_rectangle((fx, cy - 14 + abs(k - 1.5) * 2, fx + 2.4, cy - 2), 1, fill=SKIN_HI + (255,), outline=INK + (255,))
    tx0, tx1 = sorted((cx - side * 7, cx - side * 2))
    d.rounded_rectangle((tx0, cy + 1, tx1, cy + 6), 2, fill=SKIN + (255,), outline=INK + (255,))
    d.rectangle((cx - w / 2 - 1, cy + 14, cx + w / 2 + 1, cy + 22), fill=SKIN_DARK + (255,), outline=INK + (255,))


def rays(d, cx, cy, r0, r1, n, color, width=2, rot=0.0):
    for i in range(n):
        a = rot + 2 * math.pi * i / n
        d.line((cx + math.cos(a) * r0, cy + math.sin(a) * r0, cx + math.cos(a) * r1, cy + math.sin(a) * r1), fill=color, width=width)


def icon_thermal_clap(img):
    background(img)
    add_glow(img, lambda d, c: rays(d, 32, 30, 6, 26, 12, c, 2, 0.13), MOLTEN + (230,), 2.0, 1.6)
    add_glow(img, lambda d, c: d.ellipse((22, 20, 42, 40), fill=c), HOT + (255,), 4.0, 2.4)
    add_glow(img, lambda d, c: d.ellipse((27, 25, 37, 35), fill=c), WHITE_HOT + (255,), 2.0, 1.5)
    d = ImageDraw.Draw(img)
    palm(d, 21, 32, -1)
    palm(d, 43, 32, 1)
    for k, r in enumerate((14, 19)):
        d.arc((32 - r - 6, 30 - r, 32 + r + 6, 30 + r), 200, 340, fill=HOT + (200 - 60 * k,), width=2)


def icon_seismic_slam(img):
    background(img)
    d = ImageDraw.Draw(img)
    d.rectangle((4, 44, 59, 59), fill=STONE_DARK + (255,))
    d.line((4, 44, 59, 44), fill=STONE + (255,), width=2)
    add_glow(img, lambda d, c: [d.line(p, fill=c, width=2) for p in (
        (32, 46, 22, 52, 14, 50, 6, 56), (32, 46, 40, 53, 50, 50, 58, 55), (32, 46, 31, 54, 34, 59), (24, 51, 20, 58), (44, 52, 47, 58))],
        MOLTEN + (255,), 1.6, 2.0)
    add_glow(img, lambda d, c: d.ellipse((10, 40, 54, 50), outline=c, width=2), HOT + (220,), 2.0, 1.4)
    d = ImageDraw.Draw(img)
    for x in (16, 48):
        d.polygon([(x - 3, 42), (x, 34), (x + 3, 42)], fill=STONE + (255,), outline=INK + (255,))
    # the fist coming down, arm above
    d.rectangle((26, 6, 38, 26), fill=SKIN_DARK + (255,), outline=INK + (255,))
    fist(d, 32, 34, 1.2, 1)
    add_glow(img, lambda d, c: [d.line((x, 8, x, 22), fill=c, width=1) for x in (22, 42)], HOT + (160,), 1.0, 1.0)


def icon_destruction_leap(img):
    background(img)
    d = ImageDraw.Draw(img)
    pts = [(8 + i * 2.4, 52 - 40 * math.sin(math.pi * i / 20)) for i in range(21)]
    for i in range(0, 20, 2):
        d.line((pts[i], pts[i + 1]), fill=HOT + (200,), width=2)
    d.rectangle((36, 52, 60, 59), fill=STONE_DARK + (255,))
    add_glow(img, lambda d, c: d.ellipse((38, 49, 60, 57), outline=c, width=2), MOLTEN + (255,), 2.0, 1.8)
    add_glow(img, lambda d, c: rays(d, 49, 52, 6, 13, 7, c, 2, math.pi), HOT + (220,), 1.4, 1.4)
    d = ImageDraw.Draw(img)
    # the leaping silhouette at the arc's apex: hunched body, fists forward
    cx, cy = 30, 15
    d.ellipse((cx - 7, cy - 5, cx + 7, cy + 7), fill=SKIN + (255,), outline=INK + (255,))
    d.ellipse((cx + 3, cy - 8, cx + 9, cy - 2), fill=SKIN_HI + (255,), outline=INK + (255,))
    d.line((cx + 4, cy + 2, cx + 12, cy + 8), fill=SKIN_DARK + (255,), width=4)
    d.line((cx - 4, cy + 5, cx - 10, cy + 12), fill=SKIN_DARK + (255,), width=4)
    d.point((cx + 7, cy - 6), fill=EYE + (255,))
    d.polygon([(44, 30), (52, 30), (48, 40)], fill=WHITE_HOT + (255,))


def icon_heat_blast(img):
    background(img)
    add_glow(img, lambda d, c: d.polygon([(20, 28), (60, 22), (60, 42), (20, 36)], fill=c), MOLTEN + (230,), 3.0, 2.0)
    add_glow(img, lambda d, c: d.polygon([(20, 30), (60, 28), (60, 36), (20, 34)], fill=c), WHITE_HOT + (255,), 1.5, 1.5)
    d = ImageDraw.Draw(img)
    # the chest in profile with the core
    d.rounded_rectangle((4, 12, 22, 54), 7, fill=SKIN + (255,), outline=INK + (255,))
    d.line((8, 22, 18, 22), fill=SKIN_DARK + (255,), width=2)
    d.line((8, 42, 18, 42), fill=SKIN_DARK + (255,), width=1)
    add_glow(img, lambda d, c: d.ellipse((13, 26, 25, 38), fill=c), HOT + (255,), 3.0, 2.2)
    add_glow(img, lambda d, c: d.ellipse((16, 29, 22, 35), fill=c), WHITE_HOT + (255,), 1.0, 1.0)
    d = ImageDraw.Draw(img)
    for x, y in ((40, 18), (50, 46), (56, 16), (34, 46)):
        d.ellipse((x - 1, y - 1, x + 1, y + 1), fill=HOT + (255,))


def icon_thermal_fury(img):
    background(img, (255, 70, 30))
    for cx, h in ((14, 22), (24, 30), (32, 36), (40, 30), (50, 22)):
        add_glow(img, lambda d, c, cx=cx, h=h: flame(d, cx, 30, h, 6, c), MOLTEN + (220,), 2.0, 1.2)
    for cx, h in ((24, 20), (32, 26), (40, 20)):
        add_glow(img, lambda d, c, cx=cx, h=h: flame(d, cx, 30, h, 3.5, c), HOT + (240,), 1.2, 1.0)
    d = ImageDraw.Draw(img)
    # the furious brow and two blazing eyes
    d.rounded_rectangle((8, 28, 56, 58), 10, fill=SKIN + (255,), outline=INK + (255,))
    d.polygon([(10, 34), (30, 42), (30, 46), (10, 40)], fill=SKIN_DARK + (255,))
    d.polygon([(54, 34), (34, 42), (34, 46), (54, 40)], fill=SKIN_DARK + (255,))
    add_glow(img, lambda d, c: [d.polygon([(14, 42), (28, 46), (26, 50), (15, 47)], fill=c),
                                d.polygon([(50, 42), (36, 46), (38, 50), (49, 47)], fill=c)], EYE + (255,), 2.2, 2.2)
    d = ImageDraw.Draw(img)
    d.line((22, 55, 42, 55), fill=INK + (255,), width=2)
    for x in range(24, 42, 4):
        d.polygon([(x, 55), (x + 2, 55), (x + 1, 57)], fill=(240, 230, 210, 255))


def icon_critical_overheat(img):
    background(img, (255, 236, 160))
    add_glow(img, lambda d, c: rays(d, 32, 32, 10, 29, 16, c, 2, 0.1), MOLTEN + (240,), 2.0, 1.6)
    add_glow(img, lambda d, c: rays(d, 32, 32, 8, 22, 8, c, 3, 0.3), HOT + (255,), 1.6, 1.4)
    for r, col in ((20, MOLTEN + (200,)), (14, HOT + (230,))):
        add_glow(img, lambda d, c, r=r: d.ellipse((32 - r, 32 - r, 32 + r, 32 + r), outline=c, width=2), col, 1.6, 1.2)
    add_glow(img, lambda d, c: d.ellipse((22, 22, 42, 42), fill=c), HOT + (255,), 5.0, 2.6)
    add_glow(img, lambda d, c: d.ellipse((26, 26, 38, 38), fill=c), WHITE_HOT + (255,), 2.0, 2.0)
    d = ImageDraw.Draw(img)
    # a cracked red body at the centre of the nova
    d.ellipse((28, 28, 36, 36), fill=SKIN + (255,), outline=INK + (255,))
    d.line((30, 29, 32, 32, 31, 35), fill=WHITE_HOT + (255,), width=1)


ICONS = {
    'thermal_clap': icon_thermal_clap, 'seismic_slam': icon_seismic_slam, 'destruction_leap': icon_destruction_leap,
    'heat_blast': icon_heat_blast, 'thermal_fury': icon_thermal_fury, 'critical_overheat': icon_critical_overheat,
}


def make_icon(key):
    img = canvas()
    ICONS[key](img)
    return img


def badge():
    """32x32 race-screen badge: the Red Hulk's scowl with the yellow eyes."""
    s = 4
    big = Image.new('RGBA', (32 * s, 32 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    d.ellipse((2 * s, 2 * s, 30 * s, 30 * s), fill=(20, 8, 6, 255), outline=MOLTEN + (255,), width=2 * s)
    d.rounded_rectangle((8 * s, 6 * s, 24 * s, 27 * s), 6 * s, fill=SKIN + (255,), outline=INK + (255,), width=s)
    d.rectangle((8 * s, 6 * s, 24 * s, 10 * s), fill=(30, 18, 16, 255))
    d.polygon([(9 * s, 13 * s), (16 * s, 16 * s), (16 * s, 17 * s), (9 * s, 15 * s)], fill=SKIN_DARK + (255,))
    d.polygon([(23 * s, 13 * s), (16 * s, 16 * s), (16 * s, 17 * s), (23 * s, 15 * s)], fill=SKIN_DARK + (255,))
    d.polygon([(10 * s, 16 * s), (15 * s, 17.5 * s), (14 * s, 19 * s), (10.5 * s, 18 * s)], fill=EYE + (255,))
    d.polygon([(22 * s, 16 * s), (17 * s, 17.5 * s), (18 * s, 19 * s), (21.5 * s, 18 * s)], fill=EYE + (255,))
    d.rectangle((12 * s, 22 * s, 20 * s, 24 * s), fill=INK + (255,))
    d.rectangle((12.5 * s, 22 * s, 19.5 * s, 23 * s), fill=(236, 226, 206, 255))
    return big.resize((32, 32), Image.LANCZOS)


def emblem():
    """16x16 panel emblem: a red fist in a molten ring."""
    s = 4
    big = Image.new('RGBA', (16 * s, 16 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    d.ellipse((0, 0, 16 * s - 1, 16 * s - 1), fill=(24, 10, 8, 255), outline=MOLTEN + (255,), width=s)
    fist(d, 8 * s, 8.6 * s, 2.6, 1)
    return big.resize((16, 16), Image.LANCZOS)


def write(img, rel):
    path = os.path.join(ROOT, rel + '.b64')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    buf = io.BytesIO()
    img.save(buf, 'PNG', optimize=True)
    with open(path, 'w') as f:
        f.write(base64.b64encode(buf.getvalue()).decode())


def main():
    for key in ICONS:
        write(make_icon(key), f'ability/red_hulk/{key}.png')
    write(badge(), 'red_hulk/hero.png')
    write(emblem(), 'red_hulk/emblem.png')
    if '--preview' in sys.argv:
        out = sys.argv[sys.argv.index('--preview') + 1]
        icons = [make_icon(k) for k in ICONS]
        sheet = Image.new('RGBA', (len(icons) * 136 + 200, 200), (40, 40, 44, 255))
        for i, ic in enumerate(icons):
            sheet.alpha_composite(ic.resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
            sheet.alpha_composite(ic, (i * 136 + 36, 136))
        sheet.alpha_composite(badge().resize((96, 96), Image.NEAREST), (len(icons) * 136 + 8, 4))
        sheet.alpha_composite(emblem().resize((64, 64), Image.NEAREST), (len(icons) * 136 + 120, 20))
        sheet.save(out)


if __name__ == '__main__':
    main()
