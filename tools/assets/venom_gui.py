"""Venom GUI art -- own art, CC0: six ability icons (64x64), hero badge (32x32), panel emblem (16x16) and the three
host-takeover overlays (veins 256x256, haze 64x64, flash 256x128, lids 64x64).
The palette is the symbiote's: blue-black flesh with a cold sheen, chalk-white eyes and emblem, ivory teeth, a wet red
tongue, and dark violet for the veins that creep over a possessed host's view. Everything is drawn supersampled with
PIL and reduced, so the small motifs stay smooth; the overlays are deliberately never fully opaque (the takeover view
must never turn plain black). Writes base64 PNGs under viltrumitecore/src/main/binassets (the build decodes them).
Run: python3 tools/assets/venom_gui.py [--preview out.png]"""
import base64
import io
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore', 'src', 'main', 'binassets',
                    'assets', 'viltrumitecore', 'textures', 'gui')
S = 64
K = 4
INK = (8, 8, 14)
FLESH = (20, 22, 36)
SHEEN = (70, 84, 130)
SHEEN_HI = (150, 170, 220)
WHITE = (242, 244, 250)
WHITE_DIM = (176, 182, 200)
TOOTH = (236, 230, 210)
TOOTH_DARK = (150, 140, 118)
GUM = (70, 6, 18)
TONGUE = (196, 40, 66)
TONGUE_HI = (240, 110, 130)
VEIN = (54, 16, 74)
VEIN_HI = (126, 56, 160)
SKIN = (196, 150, 126)
SKIN_DARK = (128, 92, 80)
RAGE = (200, 30, 40)
BG = (36, 42, 70)
BG_HI = (108, 122, 170)


def smooth(pts, rounds=3, closed=True):
    """Chaikin corner cutting: turns a rough polygon into a soft organic outline."""
    for _ in range(rounds):
        out = []
        n = len(pts)
        rng = range(n) if closed else range(n - 1)
        if not closed:
            out.append(pts[0])
        for i in rng:
            (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % n]
            out.append((0.75 * x0 + 0.25 * x1, 0.75 * y0 + 0.25 * y1))
            out.append((0.25 * x0 + 0.75 * x1, 0.25 * y0 + 0.75 * y1))
        if not closed:
            out.append(pts[-1])
        pts = out
    return pts


def scaled(pts, cx, cy, s, k=K):
    return [((cx + x * s) * k, (cy + y * s) * k) for x, y in pts]


def big(w=S, h=S, k=K):
    return Image.new('RGBA', (w * k, h * k), (0, 0, 0, 0))


def tile(img, rim=SHEEN):
    d = ImageDraw.Draw(img)
    n = S * K
    d.rounded_rectangle((K, K, n - 2 * K, n - 2 * K), 9 * K, fill=(3, 3, 6, 255))
    d.rounded_rectangle((3 * K, 3 * K, n - 4 * K, n - 4 * K), 7 * K, fill=BG + (255,))
    glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    for r in range(30, 0, -1):
        t = 1 - r / 30
        c = tuple(int(BG[i] + (BG_HI[i] - BG[i]) * t) for i in range(3))
        g.ellipse(((32 - r) * K, (34 - r) * K, (32 + r) * K, (34 + r) * K), fill=c + (255,))
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((3 * K, 3 * K, n - 4 * K, n - 4 * K), 7 * K, fill=255)
    img.paste(glow, (0, 0), mask)
    # a goo rim: the outline is wobbly and drips at the bottom
    d.rounded_rectangle((2 * K, 2 * K, n - 3 * K, n - 3 * K), 8 * K, outline=rim + (255,), width=K)
    rnd = random.Random(hash(rim) & 0xffff)
    for i in range(4):
        x = 10 + i * 13 + rnd.uniform(-3, 3)
        ln = rnd.uniform(2.5, 6)
        d.rounded_rectangle(((x - 1.1) * K, (n / K - 4) * K, (x + 1.1) * K, (n / K - 4 + ln) * K), int(1.1 * K), fill=rim + (255,))
    return img


def finish(img, size=S):
    return img.resize((size, size), Image.LANCZOS)


# ---------------------------------------------------------------------------------------------------------------- face
EYE_R = smooth([(0.07, -0.08), (0.26, -0.40), (0.58, -0.62), (0.97, -0.74), (1.00, -0.50), (0.82, -0.24), (0.48, -0.06),
                (0.20, 0.02)], 3)
HEAD = smooth([(0, -1.18), (0.62, -1.08), (1.02, -0.62), (1.06, 0.05), (0.86, 0.66), (0.42, 1.05), (0, 1.16),
               (-0.42, 1.05), (-0.86, 0.66), (-1.06, 0.05), (-1.02, -0.62), (-0.62, -1.08)], 3)


def mouth_curves(open_):
    """Upper and lower lip curves (unit face coords), left to right; they meet at the corners of the grin."""
    up, lo = [], []
    for i in range(25):
        u = i / 24 * 2 - 1
        y = 0.12 + 0.26 * u * u - 0.04 * (1 - u * u)
        up.append((u * 0.94, y))
        lo.append((u * 0.94, y + (0.10 + open_ * 0.66) * (1 - u * u) ** 0.6))
    return up, lo


def venom_face(d, cx, cy, s, open_=0.4, tongue=True, eyes=WHITE, flesh=FLESH, k=K, sheen=True, alpha=255):
    a = alpha
    d.polygon(scaled(HEAD, cx, cy, s, k), fill=flesh + (a,))
    if sheen:
        hl = smooth([(-0.62, -0.92), (-0.20, -1.06), (0.10, -1.02), (-0.30, -0.84), (-0.70, -0.52)], 2)
        d.polygon(scaled(hl, cx, cy, s, k), fill=SHEEN + (a,))
    for sx in (1, -1):
        eye = [(x * sx, y) for x, y in EYE_R]
        rim = [(x * sx * 1.08, y * 1.08 - 0.02) for x, y in EYE_R]
        d.polygon(scaled(rim, cx, cy, s, k), fill=INK + (a,))
        d.polygon(scaled(eye, cx, cy, s, k), fill=eyes + (a,))
    up, lo = mouth_curves(open_)
    mouth = up + list(reversed(lo))
    d.polygon(scaled(mouth, cx, cy, s, k), fill=GUM + (a,))
    # teeth: long needles along both lips, biggest at the corners of the grin
    n = 13
    for i in range(n):
        u = 0.06 + 0.88 * (i + 0.5) / n
        j = int(round(u * 24))
        x0, y0 = up[j]
        x1, y1 = lo[j]
        gap = y1 - y0
        ln = min(0.14 + 0.16 * (1 - abs(u - 0.5) * 2), gap * 0.58)
        w = 0.045 + 0.02 * (1 - abs(u - 0.5) * 2)
        d.polygon(scaled([(x0 - w, y0 - 0.01), (x0 + w, y0 - 0.01), (x0 + w * 0.15, y0 + ln)], cx, cy, s, k), fill=TOOTH + (a,))
        d.polygon(scaled([(x1 - w, y1 + 0.01), (x1 + w, y1 + 0.01), (x1 + w * 0.15, y1 - ln * 0.85)], cx, cy, s, k),
                  fill=TOOTH + (a,))
    if tongue and open_ > 0.2:
        t = smooth([(-0.10, 0.36), (0.14, 0.36), (0.22, 0.70), (0.34, 1.02), (0.30, 1.34), (0.12, 1.56), (0.20, 1.30),
                    (0.12, 1.04), (0.00, 0.74)], 3)
        d.polygon(scaled(t, cx, cy, s, k), fill=TONGUE + (a,))
        d.line(scaled(smooth([(0.04, 0.42), (0.12, 0.76), (0.22, 1.06), (0.22, 1.28)], 2, closed=False), cx, cy, s, k),
               fill=TONGUE_HI + (a,), width=max(1, int(s * 0.05 * k)))


def spider(d, cx, cy, s, col=WHITE, k=K, a=255):
    """The white symbiote emblem: a fat body with eight legs that sweep out and wrap around (s = half size)."""
    d.polygon(scaled(smooth([(0, -0.62), (0.16, -0.40), (0.20, -0.06), (0.12, 0.38), (0, 0.72), (-0.12, 0.38),
                             (-0.20, -0.06), (-0.16, -0.40)], 3), cx, cy, s, k), fill=col + (a,))
    d.ellipse(((cx - 0.13 * s) * k, (cy - 0.86 * s) * k, (cx + 0.13 * s) * k, (cy - 0.58 * s) * k), fill=col + (a,))
    legs = [
        [(0.10, -0.34), (0.48, -0.66), (0.74, -0.98), (0.94, -0.84)],
        [(0.14, -0.18), (0.62, -0.30), (1.00, -0.40), (1.10, -0.10)],
        [(0.14, 0.02), (0.60, 0.10), (0.98, 0.34), (1.04, 0.66)],
        [(0.10, 0.20), (0.40, 0.50), (0.58, 0.92), (0.40, 1.10)],
    ]
    w = max(1, int(s * 0.12 * k))
    for leg in legs:
        for sx in (1, -1):
            p = smooth([(x * sx, y) for x, y in leg], 3, closed=False)
            pts = scaled(p, cx, cy, s, k)
            for i in range(len(pts) - 1):
                f = 1 - i / len(pts)
                d.line((pts[i], pts[i + 1]), fill=col + (a,), width=max(1, int(w * (0.45 + 0.55 * f))))


def goo(d, pts, width, col=FLESH, k=K, hi=SHEEN, seed=0):
    """A tapering symbiote tentacle along pts (icon px), width at the root, with a sheen line on top."""
    p = smooth(pts, 3, closed=False)
    n = len(p)
    for i in range(n - 1):
        f = 1 - i / (n - 1)
        w = width * (0.18 + 0.82 * f)
        (x0, y0), (x1, y1) = p[i], p[i + 1]
        d.line((x0 * k, y0 * k, x1 * k, y1 * k), fill=col + (255,), width=max(1, int(w * k)))
        r = w * 0.5
        d.ellipse(((x1 - r) * k, (y1 - r) * k, (x1 + r) * k, (y1 + r) * k), fill=col + (255,))
    if hi:
        for i in range(1, n - 2):
            f = 1 - i / (n - 1)
            w = width * (0.18 + 0.82 * f)
            (x0, y0), (x1, y1) = p[i], p[i + 1]
            dx, dy = x1 - x0, y1 - y0
            ln = math.hypot(dx, dy) or 1
            ox, oy = -dy / ln * w * 0.22, dx / ln * w * 0.22
            d.line(((x0 - ox) * k, (y0 - oy) * k, (x1 - ox) * k, (y1 - oy) * k), fill=hi + (255,), width=max(1, int(w * 0.22 * k)))


# ---------------------------------------------------------------------------------------------------------------- icons
def symbiosis():
    img = tile(big())
    d = ImageDraw.Draw(img)
    # a human head swallowed from the right by the suit: skin on the left, the symbiote mask on the right
    head = Image.new('RGBA', img.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(head)
    hd.polygon(scaled(HEAD, 32, 32, 20), fill=SKIN + (255,))
    hd.ellipse((17 * K, 26 * K, 25 * K, 31 * K), fill=(250, 246, 240, 255))
    hd.ellipse((19.6 * K, 27 * K, 23 * K, 30.4 * K), fill=(60, 44, 36, 255))
    hd.line((16 * K, 23.5 * K, 25 * K, 22.6 * K), fill=SKIN_DARK + (255,), width=int(1.4 * K))
    hd.line((22 * K, 44 * K, 30 * K, 45 * K), fill=SKIN_DARK + (255,), width=int(1.2 * K))
    mask = Image.new('L', img.size, 0)
    md = ImageDraw.Draw(mask)
    edge = [(31, 6), (27, 16), (33, 25), (28, 36), (34, 46), (29, 58), (64, 64), (64, 0)]
    md.polygon([(x * K, y * K) for x, y in smooth(edge, 3)], fill=255)
    suit = Image.new('RGBA', img.size, (0, 0, 0, 0))
    venom_face(ImageDraw.Draw(suit), 32, 32, 20, open_=0.3, tongue=False)
    head.paste(suit, (0, 0), ImageChops.multiply(mask, suit.getchannel('A')))
    img.alpha_composite(head)
    d = ImageDraw.Draw(img)
    # goo threads reaching across the seam
    for y, ln, seed in ((14, 7, 1), (27, 9, 2), (40, 6, 3), (50, 8, 4)):
        goo(d, [(31, y), (31 - ln * 0.5, y + 1.5), (31 - ln, y + 0.5)], 2.6, seed=seed)
    return finish(img)


def tendril():
    img = tile(big())
    d = ImageDraw.Draw(img)
    # a symbiote fist bottom-left, a tendril whipping to the top-right and splatting there
    d.ellipse((6 * K, 40 * K, 22 * K, 58 * K), fill=FLESH + (255,))
    d.arc((7 * K, 41 * K, 21 * K, 57 * K), 200, 300, fill=SHEEN + (255,), width=int(1.4 * K))
    goo(d, [(16, 46), (24, 36), (26, 28), (36, 22), (44, 16), (50, 12)], 6.0)
    rnd = random.Random(7)
    pts = []
    for i in range(14):
        a = i / 14 * 2 * math.pi
        r = rnd.uniform(4.5, 8.5) if i % 2 else rnd.uniform(2.6, 4.0)
        pts.append((51 + math.cos(a) * r, 11 + math.sin(a) * r))
    d.polygon([(x * K, y * K) for x, y in smooth(pts, 2)], fill=WHITE_DIM + (255,))
    d.polygon([(x * K, y * K) for x, y in smooth([(51 + (x - 51) * 0.6, 11 + (y - 11) * 0.6) for x, y in pts], 2)],
              fill=FLESH + (255,))
    for (x0, y0, x1, y1) in ((10, 26, 18, 22), (6, 34, 14, 30), (22, 52, 30, 48)):
        d.line((x0 * K, y0 * K, x1 * K, y1 * K), fill=SHEEN + (160,), width=K)
    return finish(img)


def tentacle_lash():
    img = tile(big())
    d = ImageDraw.Draw(img)
    # three tentacles fanning out of one shoulder in a sweeping arc, with a white speed trail
    for r in (40, 34, 28):
        d.arc(((8 - r * 0.3) * K, (8) * K, (8 + r * 1.4) * K, (8 + r * 1.4) * K), 280, 350, fill=WHITE_DIM + (110,), width=int(1.2 * K))
    goo(d, [(14, 56), (20, 40), (30, 26), (44, 18), (56, 18)], 7.0)
    goo(d, [(12, 56), (16, 38), (20, 22), (28, 10), (36, 6)], 5.6)
    goo(d, [(16, 58), (28, 48), (42, 42), (54, 40), (60, 34)], 5.0)
    for x, y in ((56, 18), (36, 6), (60, 34)):
        d.polygon([((x - 1.5) * K, (y - 1) * K), ((x + 3) * K, (y - 3) * K), ((x + 0.5) * K, (y + 1.6) * K)], fill=WHITE + (255,))
    return finish(img)


def symbiote_shell():
    img = tile(big())
    d = ImageDraw.Draw(img)
    # a hardened dome of goo with spines; something (a white impact star) breaking on it
    dome = smooth([(8, 52), (10, 34), (20, 18), (32, 13), (44, 18), (54, 34), (56, 52)], 3)
    d.polygon([(x * K, y * K) for x, y in dome], fill=FLESH + (255,))
    for i in range(7):
        a = math.pi * (1.05 + i / 6 * 0.9)
        x0, y0 = 32 + math.cos(a) * 22, 50 + math.sin(a) * 34
        x1, y1 = 32 + math.cos(a) * 29, 50 + math.sin(a) * 42
        if y0 > 50:
            continue
        dx, dy = -(y1 - y0) * 0.25, (x1 - x0) * 0.25
        d.polygon([((x0 + dx) * K, (y0 + dy) * K), ((x0 - dx) * K, (y0 - dy) * K), (x1 * K, y1 * K)], fill=FLESH + (255,))
    d.arc((14 * K, 18 * K, 50 * K, 70 * K), 200, 290, fill=SHEEN_HI + (255,), width=int(1.6 * K))
    d.arc((18 * K, 24 * K, 46 * K, 70 * K), 210, 260, fill=SHEEN + (255,), width=int(1.0 * K))
    spider(d, 32, 37, 9)
    # the impact
    for i in range(8):
        a = i / 8 * 2 * math.pi + 0.3
        d.line((52 * K, 14 * K, (52 + math.cos(a) * 7) * K, (14 + math.sin(a) * 7) * K), fill=WHITE + (220,), width=K)
    return finish(img)


def feeding_frenzy():
    img = tile(big(), rim=RAGE)
    d = ImageDraw.Draw(img)
    venom_face(d, 32, 26, 22, open_=1.0, eyes=(255, 236, 236))
    # red rage veins on the dome
    for pts in ([(20, 8), (24, 12), (22, 16)], [(44, 8), (40, 12), (43, 16)]):
        d.line([(x * K, y * K) for x, y in pts], fill=RAGE + (255,), width=K)
    return finish(img)


def host_takeover():
    img = tile(big(), rim=VEIN_HI)
    # a human face in profile-free front view, veins crawling over it and one eye already filled white
    face = big()
    fd = ImageDraw.Draw(face)
    fd.polygon(scaled(HEAD, 32, 33, 20), fill=SKIN + (255,))
    for sx in (-1, 1):
        ex = 32 + sx * 8
        fd.ellipse(((ex - 4.5) * K, 27 * K, (ex + 4.5) * K, 33 * K), fill=(250, 246, 240, 255))
        if sx < 0:
            fd.ellipse(((ex - 2) * K, 28 * K, (ex + 2) * K, 32 * K), fill=(60, 44, 36, 255))
    fd.line((26 * K, 45 * K, 38 * K, 45 * K), fill=SKIN_DARK + (255,), width=int(1.3 * K))
    img.alpha_composite(face)
    d = ImageDraw.Draw(img)
    # the right side of the face goes black, the eye there turns into the symbiote's
    cover = big()
    cd = ImageDraw.Draw(cover)
    venom_face(cd, 32, 33, 20, open_=0.2, tongue=False)
    m = Image.new('L', img.size, 0)
    ImageDraw.Draw(m).polygon([(x * K, y * K) for x, y in smooth([(36, 6), (34, 20), (38, 30), (35, 42), (40, 60), (64, 64),
                                                                       (64, 0)], 3)], fill=255)
    img.paste(cover, (0, 0), ImageChops.multiply(m, cover.getchannel('A')))
    d = ImageDraw.Draw(img)
    rnd = random.Random(11)
    for i in range(9):
        x, y = 36 + rnd.uniform(-1, 1), 12 + i * 5 + rnd.uniform(-1, 1)
        pts = [(x, y)]
        for _ in range(5):
            x -= rnd.uniform(1.5, 3.2)
            y += rnd.uniform(-2.2, 2.2)
            pts.append((x, y))
        d.line([(px * K, py * K) for px, py in pts], fill=VEIN + (255,), width=int(1.1 * K))
        d.line([(px * K, py * K) for px, py in pts[:3]], fill=VEIN_HI + (255,), width=max(1, K // 2))
    return finish(img)


ICONS = {'symbiosis': symbiosis, 'tendril': tendril, 'tentacle_lash': tentacle_lash, 'symbiote_shell': symbiote_shell,
         'feeding_frenzy': feeding_frenzy, 'host_takeover': host_takeover}


def badge():
    img = big()
    d = ImageDraw.Draw(img)
    venom_face(d, 32, 26, 22, open_=0.5)
    return img.resize((32, 32), Image.LANCZOS)


def emblem():
    img = big()
    d = ImageDraw.Draw(img)
    spider(d, 32, 30, 26)
    return img.resize((16, 16), Image.LANCZOS)


# ---------------------------------------------------------------------------------------------------------------- overlays
def veins():
    """256x256: dark violet veins creeping in from every edge, branching and thinning towards a clear centre; a faint
    violet haze along the border. Stretched over the whole screen by VenomHostHud."""
    n, k = 256, 3
    img = Image.new('RGBA', (n * k, n * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = random.Random(1717)
    c = n / 2

    def branch(x, y, ang, w, depth, stop):
        steps = 0
        while w > 0.6:
            nx = x + math.cos(ang) * 3.0
            ny = y + math.sin(ang) * 3.0
            dist = math.hypot(nx - c, ny - c)
            if dist < stop:
                # the tip: a short thinning hook
                w *= 0.8
            col = VEIN if depth < 2 else tuple(int(v * 0.85) for v in VEIN)
            d.line((x * k, y * k, nx * k, ny * k), fill=col + (255,), width=max(1, int(w * k)))
            d.ellipse(((nx - w / 2) * k, (ny - w / 2) * k, (nx + w / 2) * k, (ny + w / 2) * k), fill=col + (255,))
            if w > 2.0:
                d.line((x * k, y * k, nx * k, ny * k), fill=VEIN_HI + (170,), width=max(1, int(w * 0.28 * k)))
            x, y = nx, ny
            steps += 1
            # steer towards the centre, with a living wobble
            to_c = math.atan2(c - y, c - x)
            diff = (to_c - ang + math.pi) % (2 * math.pi) - math.pi
            ang += diff * 0.05 + rnd.uniform(-0.22, 0.22) + 0.10 * math.sin(steps * 0.45 + depth)
            w *= rnd.uniform(0.972, 0.99)
            if depth < 3 and steps > 4 and rnd.random() < 0.07:
                branch(x, y, ang + rnd.choice((-1, 1)) * rnd.uniform(0.6, 1.1), w * 0.62, depth + 1, stop + rnd.uniform(8, 30))

    for i in range(18):
        side = i % 4
        u = (i // 4 + rnd.uniform(0.1, 0.9)) / 5
        if side == 0:
            x, y = u * n, -2
        elif side == 1:
            x, y = n + 2, u * n
        elif side == 2:
            x, y = (1 - u) * n, n + 2
        else:
            x, y = -2, (1 - u) * n
        branch(x, y, math.atan2(c - y, c - x) + rnd.uniform(-0.6, 0.6), rnd.uniform(5.0, 8.0), 0, rnd.uniform(40, 80))
    img = img.resize((n, n), Image.LANCZOS)
    return img.filter(ImageFilter.GaussianBlur(0.6))


def haze():
    """64x64: the violet bruise that gathers along the border of a possessed host's view (transparent in the middle,
    about 45 % at the very edge). Drawn under the veins, stretched over the screen."""
    n = 64
    c = n / 2
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    hp = img.load()
    for y in range(n):
        for x in range(n):
            r = max(abs(x - c + 0.5), abs(y - c + 0.5)) / c
            r2 = math.hypot(x - c + 0.5, y - c + 0.5) / (c * 1.414)
            t = max(0.0, (max(r, r2 * 1.2) - 0.55) / 0.45)
            hp[x, y] = (20, 6, 28, int(min(1.0, t) ** 1.6 * 115))
    return img


def flash():
    """256x128: the symbiote's face lunging at the host -- white eyes, the grin and the tongue, translucent, fading out
    at the edges so it reads as a flash in the mind rather than a sticker."""
    w, h, k = 256, 128, 3
    img = Image.new('RGBA', (w * k, h * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    venom_face(d, w / 2, h * 0.44, 54, open_=0.95, k=k, flesh=(10, 10, 18), sheen=False)
    img = img.resize((w, h), Image.LANCZOS)
    # fade: elliptical, so the face dissolves into the view
    fade = Image.new('L', (w, h), 0)
    fp = fade.load()
    for y in range(h):
        for x in range(w):
            r = math.hypot((x - w / 2) / (w / 2), (y - h / 2) / (h / 2))
            fp[x, y] = int(max(0.0, min(1.0, (1.0 - r) / 0.35)) * 215)
    a = ImageChops.multiply(img.getchannel('A'), fade)
    img.putalpha(a)
    return img


def lid():
    """64x64: rows 0-31 are the top lid (opaque-ish at the top, a wavy translucent edge at the bottom), rows 32-63 the
    mirrored bottom lid. Peak alpha is 0.92: even closed, the world glimmers through."""
    w, h = 64, 64
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    p = img.load()
    for x in range(w):
        edge = 25.0 + 2.2 * math.sin(x / w * 2 * math.pi * 2.0) + 1.1 * math.sin(x / w * 2 * math.pi * 5.0 + 1.3)
        for y in range(32):
            t = (edge - y) / 14.0
            a = max(0.0, min(1.0, t))
            a = a * a * (3 - 2 * a)
            vein = 0.12 * max(0.0, math.sin(x * 0.9 + y * 0.35)) * a
            col = (int(16 + 40 * vein), int(6 + 8 * vein), int(24 + 50 * vein))
            p[x, y] = col + (int(a * 0.92 * 255),)
            p[x, h - 1 - y] = col + (int(a * 0.92 * 255),)
    return img


# ---------------------------------------------------------------------------------------------------------------- io
# the veins are thin anti-aliased lines in three colours: a 256-colour palette keeps them intact at a fifth of the size
PALETTE = {'venom/veins.png'}


def write(img, rel):
    path = os.path.join(ROOT, rel + '.b64')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if rel in PALETTE:
        img = img.quantize(colors=256, method=Image.FASTOCTREE)
    buf = io.BytesIO()
    img.save(buf, 'PNG', optimize=True)
    with open(path, 'w', encoding='ascii') as f:
        f.write(base64.encodebytes(buf.getvalue()).decode('ascii'))


def build():
    out = {('ability/venom/%s.png' % k): fn() for k, fn in ICONS.items()}
    out['venom/hero.png'] = badge()
    out['venom/emblem.png'] = emblem()
    out['venom/veins.png'] = veins()
    out['venom/haze.png'] = haze()
    out['venom/flash.png'] = flash()
    out['venom/lid.png'] = lid()
    return out


def main():
    out = build()
    for rel, img in out.items():
        write(img, rel)
    if len(sys.argv) > 2 and sys.argv[1] == '--preview':
        sheet = Image.new('RGBA', (6 * 136 + 240, 136 + 280), (70, 90, 70, 255))
        for i, k in enumerate(ICONS):
            sheet.alpha_composite(out['ability/venom/%s.png' % k].resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
        sheet.alpha_composite(out['venom/hero.png'].resize((96, 96), Image.NEAREST), (6 * 136 + 8, 4))
        sheet.alpha_composite(out['venom/emblem.png'].resize((64, 64), Image.NEAREST), (6 * 136 + 120, 20))
        sheet.alpha_composite(out['venom/haze.png'].resize((256, 256), Image.BILINEAR), (4, 144))
        sheet.alpha_composite(out['venom/veins.png'].quantize(colors=256, method=Image.FASTOCTREE).convert('RGBA'), (4, 144))
        sheet.alpha_composite(out['venom/flash.png'].resize((512, 256)), (268, 144))
        sheet.alpha_composite(out['venom/lid.png'].resize((192, 192), Image.NEAREST), (790, 144))
        sheet.save(sys.argv[2])


if __name__ == '__main__':
    main()
