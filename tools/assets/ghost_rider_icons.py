"""Ghost Rider ability icons (64x64), hero badge (32x32) and panel emblem (16x16) -- own art, CC0.
Charred black tiles with a hellfire rim; every motif burns from deep red through orange to yellow-white, bone is warm
ivory, iron is cold blue-grey (the chain, the bike). Drawn 4x supersampled with PIL and reduced, so the small motifs
stay smooth. Writes base64 PNGs under viltrumitecore/src/main/binassets (the build decodes them).
Run: python3 tools/assets/ghost_rider_icons.py [--preview out.png]"""
import base64
import io
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore', 'src', 'main', 'binassets',
                    'assets', 'viltrumitecore', 'textures', 'gui')
S = 64
K = 4
BG = (14, 9, 8)
BG_HI = (48, 20, 12)
EMBER = (150, 30, 10)
FIRE = (255, 110, 20)
HOT = (255, 190, 60)
WHITE_HOT = (255, 244, 200)
BONE = (226, 214, 186)
BONE_DARK = (140, 122, 96)
SOCKET = (20, 6, 4)
IRON = (120, 126, 136)
IRON_DARK = (58, 60, 68)
LEATHER = (30, 28, 32)
SKIN = (214, 160, 122)


def big():
    return Image.new('RGBA', (S * K, S * K), (0, 0, 0, 0))


def tile(img, rim=FIRE):
    d = ImageDraw.Draw(img)
    n = S * K
    d.rounded_rectangle((K, K, n - 2 * K, n - 2 * K), 9 * K, fill=(6, 3, 3, 255))
    d.rounded_rectangle((3 * K, 3 * K, n - 4 * K, n - 4 * K), 7 * K, fill=BG + (255,))
    glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    for r in range(28, 0, -1):
        t = 1 - r / 28
        c = tuple(int(BG[i] + (BG_HI[i] - BG[i]) * t) for i in range(3))
        g.ellipse(((32 - r) * K, (40 - r) * K, (32 + r) * K, (40 + r) * K), fill=c + (255,))
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((3 * K, 3 * K, n - 4 * K, n - 4 * K), 7 * K, fill=255)
    img.paste(glow, (0, 0), mask)
    d.rounded_rectangle((2 * K, 2 * K, n - 3 * K, n - 3 * K), 8 * K, outline=rim + (255,), width=K)
    return img


def flame(d, x, y, w, h, seed, lean=0.0, layers=((EMBER, 1.0), (FIRE, 0.78), (HOT, 0.52), (WHITE_HOT, 0.26))):
    """A tongue of fire, base centre (x, y) in icon px, w wide, h tall, leaning lean (px per px of height)."""
    rnd = random.Random(seed)
    wob = [rnd.uniform(-0.18, 0.18) for _ in range(4)]
    for col, f in layers:
        pts = []
        steps = 14
        for i in range(steps + 1):
            t = i / steps
            half = w * f * 0.5 * math.sin(math.pi * min(1.0, (1 - t) * 1.15)) ** 0.8
            cx = x + lean * h * t * f + w * wob[int(t * 3.99)] * t * f
            pts.append((cx - half, y - h * f * t))
        for i in range(steps, -1, -1):
            t = i / steps
            half = w * f * 0.5 * math.sin(math.pi * min(1.0, (1 - t) * 1.15)) ** 0.8
            cx = x + lean * h * t * f + w * wob[int(t * 3.99)] * t * f
            pts.append((cx + half, y - h * f * t))
        d.polygon([(px * K, py * K) for px, py in pts], fill=col + (255,))


def crown(d, cx, cy, width, height, seed, n=7, lean=0.0):
    rnd = random.Random(seed)
    for i in range(n):
        u = (i + 0.5) / n
        x = cx - width / 2 + width * u
        h = height * (0.55 + 0.45 * math.sin(math.pi * u)) * rnd.uniform(0.8, 1.1)
        flame(d, x, cy, width / n * 1.9, h, seed * 31 + i, lean + rnd.uniform(-0.12, 0.12))


def skull(d, cx, cy, s, jaw=0.0, eyes=True):
    """A front skull centred at (cx, cy), s = half width (icon px)."""
    k = K
    d.ellipse(((cx - s) * k, (cy - s * 1.05) * k, (cx + s) * k, (cy + s * 0.85) * k), fill=BONE + (255,))
    d.rounded_rectangle(((cx - s * 0.62) * k, (cy + s * 0.35) * k, (cx + s * 0.62) * k, (cy + s * 1.05 + jaw) * k), int(s * 0.25 * k),
                        fill=BONE + (255,))
    d.rectangle(((cx - s * 0.62) * k, (cy + s * 0.72) * k, (cx + s * 0.62) * k, (cy + s * 0.78 + jaw) * k), fill=SOCKET + (255,))
    for i in range(5):
        x = cx - s * 0.5 + i * s * 0.25
        d.line((x * k, (cy + s * 0.6) * k, x * k, (cy + s * 0.95 + jaw) * k), fill=BONE_DARK + (255,), width=max(1, k // 2))
    for sx in (-1, 1):
        ex = cx + sx * s * 0.42
        d.ellipse(((ex - s * 0.3) * k, (cy - s * 0.2) * k, (ex + s * 0.3) * k, (cy + s * 0.32) * k), fill=SOCKET + (255,))
        if eyes:
            d.ellipse(((ex - s * 0.16) * k, (cy - s * 0.06) * k, (ex + s * 0.16) * k, (cy + s * 0.22) * k), fill=FIRE + (255,))
            d.ellipse(((ex - s * 0.07) * k, (cy + 0.02 * s) * k, (ex + s * 0.07) * k, (cy + s * 0.14) * k), fill=WHITE_HOT + (255,))
    d.polygon([(cx * k, (cy + s * 0.28) * k), ((cx - s * 0.14) * k, (cy + s * 0.52) * k), ((cx + s * 0.14) * k, (cy + s * 0.52) * k)],
              fill=SOCKET + (255,))


def chain_link(d, cx, cy, ang, length=6.0, width=3.4, col=IRON, hot=False):
    ca, sa = math.cos(ang), math.sin(ang)
    pts = []
    for i in range(24):
        t = 2 * math.pi * i / 24
        x, y = math.cos(t) * length / 2, math.sin(t) * width / 2
        pts.append(((cx + x * ca - y * sa) * K, (cy + x * sa + y * ca) * K))
    d.polygon(pts, outline=(HOT if hot else col) + (255,), width=int(K * 1.1))


def finish(img):
    small = img.resize((S, S), Image.LANCZOS)
    return small


def hell_cycle():
    img = tile(big())
    d = ImageDraw.Draw(img)
    for wx in (17, 47):
        for r, col in ((11, EMBER), (10, FIRE), (8.5, HOT)):
            d.ellipse(((wx - r) * K, (44 - r) * K, (wx + r) * K, (44 + r) * K), outline=col + (255,), width=int(K * 1.6))
        d.ellipse(((wx - 7) * K, (37) * K, (wx + 7) * K, (51) * K), fill=(18, 14, 16, 255), outline=IRON_DARK + (255,), width=K)
        d.ellipse(((wx - 2) * K, (42) * K, (wx + 2) * K, (46) * K), fill=IRON + (255,))
        for a in range(0, 360, 45):
            t = math.radians(a)
            flame(d, wx + 10.5 * math.cos(t), 44 + 10.5 * math.sin(t), 3.0, 4.5, 900 + a + wx, lean=-0.9)
    d.line((17 * K, 44 * K, 30 * K, 32 * K, 44 * K, 30 * K, 47 * K, 44 * K), fill=IRON + (255,), width=int(2.2 * K), joint='curve')
    d.polygon([(26 * K, 31 * K), (40 * K, 27 * K), (44 * K, 31 * K), (30 * K, 35 * K)], fill=LEATHER + (255,))
    d.line((44 * K, 30 * K, 49 * K, 20 * K), fill=IRON + (255,), width=int(1.8 * K))
    d.line((47 * K, 21 * K, 52 * K, 19 * K), fill=IRON_DARK + (255,), width=int(1.6 * K))
    skull(d, 52, 30, 3.6)
    for i in range(5):
        flame(d, 10 + i * 2.5, 36 - i, 4.0, 8 + i, 950 + i, lean=-1.6)
    return finish(img)


def transformation():
    img = tile(big())
    d = ImageDraw.Draw(img)
    crown(d, 36, 22, 26, 16, 7, n=6, lean=0.05)
    # left: Johnny's face, right: the skull (split down the middle)
    face = Image.new('RGBA', img.size, (0, 0, 0, 0))
    f = ImageDraw.Draw(face)
    f.rounded_rectangle((18 * K, 18 * K, 46 * K, 54 * K), 8 * K, fill=SKIN + (255,))
    f.rectangle((18 * K, 16 * K, 46 * K, 24 * K), fill=(80, 50, 30, 255))
    f.ellipse((22 * K, 31 * K, 29 * K, 36 * K), fill=(250, 250, 250, 255))
    f.ellipse((24 * K, 32 * K, 27 * K, 35 * K), fill=(70, 50, 40, 255))
    f.line((23 * K, 46 * K, 31 * K, 46 * K), fill=(140, 80, 60, 255), width=K)
    left = Image.new('L', img.size, 0)
    ImageDraw.Draw(left).rectangle((0, 0, 32 * K, S * K), fill=255)
    img.paste(face, (0, 0), Image.composite(face.getchannel('A'), Image.new('L', img.size, 0), left))
    sk = Image.new('RGBA', img.size, (0, 0, 0, 0))
    skull(ImageDraw.Draw(sk), 32, 34, 14)
    right = Image.new('L', img.size, 0)
    ImageDraw.Draw(right).rectangle((32 * K, 0, S * K, S * K), fill=255)
    img.paste(sk, (0, 0), Image.composite(sk.getchannel('A'), Image.new('L', img.size, 0), right))
    # the burning seam
    rnd = random.Random(3)
    pts = [(32 + rnd.uniform(-1.5, 1.5), 18 + i * 3) for i in range(13)]
    d.line([(x * K, y * K) for x, y in pts], fill=HOT + (255,), width=int(K * 1.4))
    for x, y in pts[::3]:
        flame(d, x, y, 3, 5, int(y), lean=0.2)
    return finish(img)


def hell_dash():
    img = tile(big())
    d = ImageDraw.Draw(img)
    for i, y in enumerate((22, 30, 38, 46)):
        d.line((8 * K, y * K, (30 + i * 3) * K, y * K), fill=(EMBER if i % 2 else FIRE) + (255,), width=int(K * 1.6))
    for i in range(7):
        flame(d, 14 + i * 3.2, 40 - (i % 3), 6, 12 + i, 1300 + i, lean=-1.8)
    crown(d, 44, 26, 14, 11, 13, n=5, lean=-1.2)
    skull(d, 44, 34, 9)
    return finish(img)


def chain_whirl():
    img = tile(big())
    d = ImageDraw.Draw(img)
    for i in range(16):
        t = 2 * math.pi * i / 16
        r = 19
        chain_link(d, 32 + r * math.cos(t), 34 + r * math.sin(t), t + math.pi / 2 + (math.pi / 2 if i % 2 else 0), 6.5, 3.6, hot=i % 4 == 0)
    for i in range(10):
        t = 2 * math.pi * i / 10
        flame(d, 32 + 22 * math.cos(t), 34 + 22 * math.sin(t), 4, 7, 1400 + i, lean=-0.5)
    skull(d, 32, 33, 7.5)
    crown(d, 32, 27, 11, 8, 17, n=4)
    return finish(img)


def hellfire_wave():
    img = tile(big())
    d = ImageDraw.Draw(img)
    d.polygon([(8 * K, 52 * K), (56 * K, 52 * K), (56 * K, 55 * K), (8 * K, 55 * K)], fill=(40, 16, 10, 255))
    for i in range(9):
        x = 10 + i * 5.2
        h = 8 + 26 * (i / 8) ** 1.2
        flame(d, x, 53, 7 + i * 0.5, h, 1500 + i, lean=0.35)
    return finish(img)


def penance_stare():
    img = tile(big())
    d = ImageDraw.Draw(img)
    # two hellfire streams from the sockets towards the viewer
    for sx in (-1, 1):
        ex = 32 + sx * 9
        for w, col in ((9, EMBER), (6.5, FIRE), (4, HOT), (2, WHITE_HOT)):
            d.polygon([((ex - w * 0.25) * K, 30 * K), ((ex + w * 0.25) * K, 30 * K), ((ex + sx * 9 + w) * K, 58 * K),
                       ((ex + sx * 9 - w) * K, 58 * K)], fill=col + (255,))
    d.rounded_rectangle((12 * K, 14 * K, 52 * K, 38 * K), 10 * K, fill=BONE + (255,))
    d.rectangle((12 * K, 14 * K, 52 * K, 18 * K), fill=BONE_DARK + (255,))
    for sx in (-1, 1):
        ex = 32 + sx * 9
        d.ellipse(((ex - 7) * K, 21 * K, (ex + 7) * K, 35 * K), fill=SOCKET + (255,))
        d.ellipse(((ex - 4) * K, 24 * K, (ex + 4) * K, 32 * K), fill=FIRE + (255,))
        d.ellipse(((ex - 2) * K, 26 * K, (ex + 2) * K, 30 * K), fill=WHITE_HOT + (255,))
    d.polygon([(32 * K, 33 * K), (29.5 * K, 38 * K), (34.5 * K, 38 * K)], fill=SOCKET + (255,))
    crown(d, 32, 14, 40, 10, 23, n=8)
    return finish(img)


def badge():
    img = Image.new('RGBA', (32 * K * 2, 32 * K * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # drawn on the 64 grid then halved
    crown(d, 32, 26, 34, 20, 41, n=7)
    skull(d, 32, 36, 15)
    return img.resize((32, 32), Image.LANCZOS)


def emblem():
    img = Image.new('RGBA', (S * K, S * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((4 * K, 4 * K, 60 * K, 60 * K), outline=FIRE + (255,), width=4 * K)
    crown(d, 32, 30, 30, 18, 51, n=5)
    skull(d, 32, 38, 13)
    return img.resize((16, 16), Image.LANCZOS)


ICONS = {'hell_cycle': hell_cycle, 'transformation': transformation, 'hell_dash': hell_dash, 'chain_whirl': chain_whirl,
         'hellfire_wave': hellfire_wave, 'penance_stare': penance_stare}


def write(img, rel):
    path = os.path.join(ROOT, rel + '.b64')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    buf = io.BytesIO()
    img.save(buf, 'PNG', optimize=True)
    with open(path, 'w', encoding='ascii') as f:
        f.write(base64.encodebytes(buf.getvalue()).decode('ascii'))


def main():
    icons = {k: fn() for k, fn in ICONS.items()}
    for k, img in icons.items():
        write(img, 'ability/ghost_rider/%s.png' % k)
    write(badge(), 'ghost_rider/hero.png')
    write(emblem(), 'ghost_rider/emblem.png')
    if len(sys.argv) > 2 and sys.argv[1] == '--preview':
        sheet = Image.new('RGBA', (len(icons) * 136 + 220, 136), (40, 40, 40, 255))
        for i, img in enumerate(icons.values()):
            sheet.alpha_composite(img.resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
        sheet.alpha_composite(badge().resize((96, 96), Image.NEAREST), (len(icons) * 136 + 8, 4))
        sheet.alpha_composite(emblem().resize((64, 64), Image.NEAREST), (len(icons) * 136 + 120, 20))
        sheet.save(sys.argv[2])


if __name__ == '__main__':
    main()
