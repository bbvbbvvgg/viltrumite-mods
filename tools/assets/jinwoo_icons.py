"""Jinwoo ability icons (64x64), hero badge and panel emblem — own pixel art, CC0.
Writes base64 PNGs under viltrumitecore/src/main/binassets (the build decodes them).
Run: python3 tools/assets/jinwoo_icons.py [--preview out.png]"""
import base64
import io
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore', 'src', 'main', 'binassets',
                    'assets', 'viltrumitecore', 'textures', 'gui')
S = 64
PURPLE = (150, 70, 255)
VIOLET = (196, 140, 255)
TEAL = (90, 230, 230)
BLUE_EYE = (120, 190, 255)
INK = (14, 12, 22)
STEEL = (60, 56, 78)
GOLD = (232, 193, 90)


def canvas():
    return Image.new('RGBA', (S, S), (0, 0, 0, 0))


def background(img, rim, inner=(40, 36, 58)):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((1, 1, S - 2, S - 2), 9, fill=(10, 9, 16, 255))
    d.rounded_rectangle((3, 3, S - 4, S - 4), 7, fill=inner + (255,))
    # radial vignette
    for r in range(27, 0, -1):
        t = 1 - r / 27
        d.ellipse((32 - r, 32 - r, 32 + r, 32 + r), fill=(int(40 + 70 * t), int(36 + 56 * t), int(58 + 92 * t), 255))
    d.rounded_rectangle((1, 1, S - 2, S - 2), 9, outline=rim + (255,), width=2)
    d.rounded_rectangle((4, 4, S - 5, S - 5), 6, outline=(rim[0] // 3, rim[1] // 3, rim[2] // 3, 255), width=1)
    for x, y in ((9, 9), (S - 10, S - 10)):
        d.line((x - 2, y, x + 2, y), fill=(220, 210, 255, 200))
        d.line((x, y - 2, x, y + 2), fill=(220, 210, 255, 200))


def glow_layer(draw_fn, color, radius=2.2, strength=1.6):
    layer = canvas()
    draw_fn(ImageDraw.Draw(layer), color)
    blur = layer.filter(ImageFilter.GaussianBlur(radius))
    px = blur.load()
    for y in range(S):
        for x in range(S):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, min(255, int(a * strength)))
    return blur, layer


def add_glow(img, draw_fn, color, radius=2.2, strength=1.6, core=True):
    blur, layer = glow_layer(draw_fn, color, radius, strength)
    img.alpha_composite(blur)
    if core:
        img.alpha_composite(layer)


def dagger(d, x, y, ang, length, edge, body=INK, width=3.2):
    """Blade from the hilt (x, y) along ang (deg, 0 = up)."""
    a = math.radians(ang)
    ux, uy = math.sin(a), -math.cos(a)
    px, py = -uy, ux
    tip = (x + ux * length, y + uy * length)
    base_l = (x + px * width / 2, y + py * width / 2)
    base_r = (x - px * width / 2, y - py * width / 2)
    d.polygon([base_l, tip, base_r], fill=body + (255,), outline=edge + (255,))
    d.line((x + px * 4, y + py * 4, x - px * 4, y - py * 4), fill=STEEL + (255,), width=2)
    d.line((x, y, x - ux * 6, y - uy * 6), fill=(40, 34, 52, 255), width=2)


def figure(d, cx, foot, h, color=INK, eyes=BLUE_EYE, coat=True):
    """Jinwoo silhouette: head, coat, legs; eyes as two pixels."""
    k = h / 30.0
    hx = cx
    d.ellipse((hx - 3.2 * k, foot - 30 * k, hx + 3.2 * k, foot - 23.5 * k), fill=color + (255,))
    d.polygon([(hx - 4.5 * k, foot - 23 * k), (hx + 4.5 * k, foot - 23 * k), (hx + 5.5 * k, foot - 9 * k), (hx - 5.5 * k, foot - 9 * k)],
              fill=color + (255,))
    if coat:
        d.polygon([(hx - 5.5 * k, foot - 12 * k), (hx - 7 * k, foot - 4 * k), (hx - 2 * k, foot - 6 * k)], fill=color + (255,))
        d.polygon([(hx + 5.5 * k, foot - 12 * k), (hx + 7 * k, foot - 4 * k), (hx + 2 * k, foot - 6 * k)], fill=color + (255,))
    d.rectangle((hx - 3.5 * k, foot - 10 * k, hx - 0.8 * k, foot), fill=color + (255,))
    d.rectangle((hx + 0.8 * k, foot - 10 * k, hx + 3.5 * k, foot), fill=color + (255,))
    if eyes:
        ey = foot - 27 * k
        d.point((hx - 1.2 * k, ey), fill=eyes + (255,))
        d.point((hx + 1.2 * k, ey), fill=eyes + (255,))


def helmet(d, cx, cy, s, color=INK, eye=BLUE_EYE):
    d.polygon([(cx - s, cy + s), (cx - s, cy - s * 0.4), (cx, cy - s * 1.3), (cx + s, cy - s * 0.4), (cx + s, cy + s)], fill=color + (255,))
    d.line((cx, cy - s * 1.3, cx, cy - s * 2.0), fill=color + (255,), width=max(1, int(s / 3)))
    d.line((cx - s * 0.6, cy, cx - s * 0.15, cy), fill=eye + (255,))
    d.line((cx + s * 0.15, cy, cx + s * 0.6, cy), fill=eye + (255,))


def arc(d, box, a0, a1, color, width):
    d.arc(box, a0, a1, fill=color + (255,), width=width)


def puddle(img, cx, cy, rx, ry):
    d = ImageDraw.Draw(img)
    add_glow(img, lambda g, c: g.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), outline=c + (255,), width=1), PURPLE, 1.6, 1.4)
    d.ellipse((cx - rx + 1, cy - ry + 1, cx + rx - 1, cy + ry - 1), fill=(6, 4, 12, 255))


def icon_shadow_step(img):
    d = ImageDraw.Draw(img)
    for i, (x, a) in enumerate(((16, 70), (24, 110), (32, 160))):
        figure(d, x, 50, 26, color=(40 + 10 * i, 26 + 6 * i, 70 + 12 * i), eyes=None)
    figure(d, 44, 50, 28)
    for y in (24, 32, 40):
        add_glow(img, lambda g, c, y=y: g.line((6, y, 22, y), fill=c + (255,), width=1), VIOLET, 1.2, 1.2)
    add_glow(img, lambda g, c: g.line((44 - 4, 50 - 22, 58, 50 - 30), fill=c + (255,), width=1), TEAL, 1.0, 1.3)


def icon_mutilation(img):
    d = ImageDraw.Draw(img)
    for k, ang in enumerate((-50, -20, 10, 40, 70)):
        r = 22
        def fn(g, c, ang=ang):
            g.arc((32 - r, 32 - r, 32 + r, 32 + r), ang - 90 - 24, ang - 90 + 24, fill=c + (255,), width=2)
        add_glow(img, fn, PURPLE if k % 2 else TEAL, 1.5, 1.5)
    add_glow(img, lambda g, c: (g.line((14, 14, 50, 50), fill=c + (255,), width=2), g.line((50, 14, 14, 50), fill=c + (255,), width=2)),
             VIOLET, 2.0, 1.6)
    dagger(d, 22, 44, 40, 24, TEAL, body=(70, 40, 120))
    dagger(d, 42, 44, -40, 24, PURPLE)


def icon_rulers_authority(img):
    d = ImageDraw.Draw(img)
    # open hand bottom-left reaching up-right
    d.polygon([(8, 58), (8, 44), (14, 36), (20, 36), (26, 42), (26, 58)], fill=(34, 28, 46, 255), outline=STEEL + (255,))
    for i, x in enumerate((12, 16, 20, 24)):
        d.rectangle((x - 1, 28 + abs(i - 1.5) * 2, x + 1, 38), fill=(34, 28, 46, 255), outline=STEEL + (255,))
    # lifted creatures
    for cx, cy in ((44, 18), (52, 30), (36, 28)):
        d.ellipse((cx - 4, cy - 4, cx + 4, cy + 4), fill=(70, 120, 60, 255), outline=(20, 30, 20, 255))
        d.point((cx - 1, cy - 1), fill=(10, 10, 10, 255))
        d.point((cx + 1, cy - 1), fill=(10, 10, 10, 255))
        add_glow(img, lambda g, c, cx=cx, cy=cy: g.line((24, 30, cx, cy), fill=c + (255,), width=1), VIOLET, 1.3, 1.6)
        add_glow(img, lambda g, c, cx=cx, cy=cy: g.ellipse((cx - 6, cy - 6, cx + 6, cy + 6), outline=c + (255,), width=1), PURPLE, 1.0, 1.1)


def icon_extraction(img):
    d = ImageDraw.Draw(img)
    puddle(img, 32, 50, 22, 6)
    # rising smoke column
    add_glow(img, lambda g, c: [g.ellipse((26 + 3 * math.sin(i), 46 - i * 5, 38 + 3 * math.sin(i), 52 - i * 5), fill=c + (120,)) for i in range(6)],
             (90, 50, 160), 2.5, 1.0)
    # hand reaching up out of the puddle
    d.polygon([(26, 50), (26, 30), (38, 30), (38, 50)], fill=INK + (255,))
    for i, x in enumerate((27, 30, 33, 36)):
        d.rectangle((x, 20 + abs(i - 1.5) * 2.5, x + 2, 31), fill=INK + (255,))
    d.rectangle((38, 30, 42, 34), fill=INK + (255,))
    add_glow(img, lambda g, c: g.polygon([(26, 50), (26, 30), (38, 30), (38, 50)], outline=c + (255,)), PURPLE, 1.0, 1.0, core=False)
    add_glow(img, lambda g, c: (g.point((30, 12), fill=c + (255,)), g.point((35, 12), fill=c + (255,))), BLUE_EYE, 1.8, 3.0)


def icon_shadow_exchange(img):
    d = ImageDraw.Draw(img)
    puddle(img, 18, 50, 11, 4)
    puddle(img, 46, 50, 11, 4)
    figure(d, 18, 49, 24)
    helmet(d, 46, 40, 6)
    d.rectangle((41, 42, 51, 49), fill=INK + (255,))
    def arrows(g, c):
        g.arc((14, 6, 50, 34), 200, 340, fill=c + (255,), width=2)
        g.polygon([(48, 18), (52, 12), (44, 12)], fill=c + (255,))
        g.arc((14, 26, 50, 54), 20, 160, fill=c + (255,), width=1)
    add_glow(img, arrows, TEAL, 1.5, 1.5)


def icon_arise(img):
    d = ImageDraw.Draw(img)
    puddle(img, 32, 54, 26, 5)
    for i, (x, y, s) in enumerate(((12, 42, 4), (22, 38, 5), (42, 38, 5), (52, 42, 4), (32, 34, 6))):
        helmet(d, x, y, s)
        d.rectangle((x - s, y + s, x + s, 54), fill=INK + (255,))
    add_glow(img, lambda g, c: [g.point((x + dx, y), fill=c + (255,)) for x, y, s in ((12, 42, 4), (22, 38, 5), (42, 38, 5), (52, 42, 4), (32, 34, 6))
                                for dx in (-s * 0.4, s * 0.4)], BLUE_EYE, 1.6, 3.0, core=False)
    # the Monarch's eyes above
    def eyes(g, c):
        g.polygon([(16, 16), (28, 13), (27, 17)], fill=c + (255,))
        g.polygon([(48, 16), (36, 13), (37, 17)], fill=c + (255,))
    add_glow(img, eyes, BLUE_EYE, 2.4, 2.2)


def icon_army_summon(img):
    d = ImageDraw.Draw(img)
    add_glow(img, lambda g, c: g.ellipse((8, 40, 56, 58), outline=c + (255,), width=2), PURPLE, 2.0, 1.4)
    d.ellipse((10, 42, 54, 56), fill=(6, 4, 12, 255))
    for x, h in ((18, 6), (32, 14), (46, 8)):
        helmet(d, x, 44 - h, 5)
        d.rectangle((x - 5, 49 - h, x + 5, 49), fill=INK + (255,))
    add_glow(img, lambda g, c: [g.line((x, 30 - h, x, 16 - h), fill=c + (200,)) for x, h in ((18, 6), (32, 14), (46, 8))], VIOLET, 1.2, 1.2)
    add_glow(img, lambda g, c: (g.polygon([(32, 4), (26, 11), (38, 11)], fill=c + (255,)),), TEAL, 1.2, 1.4)


def icon_army_attack(img):
    d = ImageDraw.Draw(img)
    def target(g, c):
        g.ellipse((34, 6, 58, 30), outline=c + (255,), width=2)
        g.line((46, 2, 46, 12), fill=c + (255,), width=1)
        g.line((46, 24, 46, 34), fill=c + (255,), width=1)
        g.line((30, 18, 40, 18), fill=c + (255,), width=1)
        g.line((52, 18, 62, 18), fill=c + (255,), width=1)
    add_glow(img, target, (255, 90, 120), 1.5, 1.5)
    # sword pointing to the target
    d.polygon([(12, 54), (40, 22), (44, 26), (16, 58)], fill=(30, 26, 44, 255), outline=VIOLET + (255,))
    d.line((8, 46, 22, 60), fill=STEEL + (255,), width=3)
    helmet(d, 16, 30, 6)
    add_glow(img, lambda g, c: (g.line((40, 22, 46, 18), fill=c + (255,), width=2),), TEAL, 1.5, 1.6)


def icon_army_guard(img):
    d = ImageDraw.Draw(img)
    figure(d, 32, 46, 24)
    for k in range(6):
        a = k * math.pi / 3
        x, y = 32 + math.cos(a) * 21, 34 + math.sin(a) * 13
        d.polygon([(x - 4, y - 5), (x + 4, y - 5), (x + 4, y + 1), (x, y + 6), (x - 4, y + 1)], fill=(32, 26, 50, 255), outline=VIOLET + (255,))
    add_glow(img, lambda g, c: g.ellipse((9, 19, 55, 49), outline=c + (255,), width=1), PURPLE, 1.6, 1.3, core=False)


def icon_army_sentinel(img):
    d = ImageDraw.Draw(img)
    figure(d, 32, 40, 30, color=(120, 108, 92), eyes=(20, 20, 20), coat=False)
    puddle(img, 32, 48, 18, 5)
    def eye(g, c):
        g.ellipse((24, 45, 40, 51), outline=c + (255,), width=1)
        g.ellipse((30, 46, 34, 50), fill=c + (255,))
    add_glow(img, eye, BLUE_EYE, 1.6, 2.0)
    add_glow(img, lambda g, c: g.arc((10, 4, 54, 60), 200, 340, fill=c + (255,), width=1), VIOLET, 1.2, 1.2, core=False)


def icon_monarch_charge(img):
    d = ImageDraw.Draw(img)
    for i, (x, y) in enumerate(((22, 20), (22, 44), (14, 32 - 14), (14, 46), (30, 32))):
        pass
    for row, xs in enumerate(((40,), (30, 30), (20, 20, 20))):
        for j in range(len(xs)):
            y = 32 + (j - (len(xs) - 1) / 2) * 13
            helmet(d, xs[j] - row * 2 + 2, y, 4)
    figure(d, 50, 44, 24)
    for y in (22, 32, 42):
        add_glow(img, lambda g, c, y=y: g.line((4, y, 14, y), fill=c + (255,), width=1), VIOLET, 1.2, 1.3)
    add_glow(img, lambda g, c: g.polygon([(58, 32), (52, 24), (52, 40)], outline=c + (255,)), TEAL, 1.4, 1.5)


ICONS = {
    'shadow_step': (icon_shadow_step, PURPLE),
    'mutilation': (icon_mutilation, PURPLE),
    'rulers_authority': (icon_rulers_authority, PURPLE),
    'extraction': (icon_extraction, PURPLE),
    'shadow_exchange': (icon_shadow_exchange, PURPLE),
    'arise': (icon_arise, GOLD),
    'army_summon': (icon_army_summon, (110, 150, 255)),
    'army_attack': (icon_army_attack, (110, 150, 255)),
    'army_guard': (icon_army_guard, (110, 150, 255)),
    'army_sentinel': (icon_army_sentinel, (110, 150, 255)),
    'monarch_charge': (icon_monarch_charge, GOLD),
}


def make_icon(key):
    fn, rim = ICONS[key]
    img = canvas()
    background(img, rim)
    fn(img)
    return img


def badge():
    """32x32 race-screen badge: two crossed daggers over the Monarch's eyes."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((1, 1, 30, 30), fill=(12, 10, 20, 255), outline=PURPLE + (255,), width=2)
    dagger(d, 10, 24, 40, 15, TEAL, body=(70, 40, 120), width=2.6)
    dagger(d, 22, 24, -40, 15, PURPLE, width=2.6)
    d.line((9, 12, 13, 11), fill=BLUE_EYE + (255,))
    d.line((19, 11, 23, 12), fill=BLUE_EYE + (255,))
    return img


def emblem():
    """16x16 panel emblem: a glowing helmet."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(2, 14), (2, 7), (8, 2), (14, 7), (14, 14)], fill=(18, 14, 28, 255), outline=PURPLE + (255,))
    d.line((4, 9, 7, 9), fill=BLUE_EYE + (255,))
    d.line((9, 9, 12, 9), fill=BLUE_EYE + (255,))
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
        write(img, f'ability/jinwoo/{k}.png')
    write(badge(), 'jinwoo/hero.png')
    write(emblem(), 'jinwoo/emblem.png')
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
