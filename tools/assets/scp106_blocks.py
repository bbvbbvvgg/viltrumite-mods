"""Procedural 16x16 block textures of the SCP-106 pocket dimension and the corrosion it leaves (own art, CC0).

Writes base64 PNGs (+ .mcmeta for animated ones) under viltrumitecore/src/main/binassets/assets/viltrumitecore/
textures/block/, the build decodes them. Run: python3 tools/assets/scp106_blocks.py
"""
import base64
import io
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore', 'src', 'main', 'binassets', 'assets',
                    'viltrumitecore', 'textures', 'block')


def clamp(v):
    return max(0, min(255, int(v)))


def noise(seed, w=16, h=16, scale=4):
    """Tileable value noise in [0, 1)."""
    r = random.Random(seed)
    gw, gh = max(1, w // scale), max(1, h // scale)
    grid = [[r.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / scale, y / scale
            x0, y0 = int(fx) % gw, int(fy) % gh
            x1, y1 = (x0 + 1) % gw, (y0 + 1) % gh
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def fbm(seed, w=16, h=16):
    n1, n2, n3 = noise(seed, w, h, 8), noise(seed + 1, w, h, 4), noise(seed + 2, w, h, 2)
    return [[(n1[y][x] * 0.5 + n2[y][x] * 0.3 + n3[y][x] * 0.2) for x in range(w)] for y in range(h)]


def img(fn, w=16, h=16, mode='RGBA'):
    im = Image.new(mode, (w, h))
    px = im.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = fn(x, y)
    return im


def shade(base, k):
    return tuple(clamp(c * k) for c in base[:3]) + ((base[3],) if len(base) > 3 else (255,))


def save(name, im, frametime=None, interpolate=False):
    os.makedirs(ROOT, exist_ok=True)
    buf = io.BytesIO()
    im.save(buf, 'PNG', optimize=True)
    with open(os.path.join(ROOT, name + '.png.b64'), 'w') as f:
        f.write(base64.b64encode(buf.getvalue()).decode('ascii'))
    if frametime:
        meta = {'animation': {'frametime': frametime, 'interpolate': interpolate}}
        with open(os.path.join(ROOT, name + '.png.mcmeta.b64'), 'w') as f:
            f.write(base64.b64encode(json.dumps(meta).encode()).decode('ascii'))


def concrete(seed, base, rust=0.0, cracks=0):
    n = fbm(seed)
    r = random.Random(seed)
    crack = set()
    for _ in range(cracks):
        x, y = r.randrange(16), r.randrange(16)
        for _ in range(r.randrange(4, 9)):
            crack.add((x % 16, y % 16))
            x += r.choice((-1, 0, 1))
            y += 1
    rn = noise(seed + 9, scale=4)

    def f(x, y):
        k = 0.78 + n[y][x] * 0.4 + (r.random() - 0.5) * 0.06
        c = shade(base, k)
        if rust and rn[y][x] > 1 - rust:
            t = (rn[y][x] - (1 - rust)) / rust
            c = tuple(clamp(c[i] * (1 - t) + (92, 52, 28)[i] * t * k) for i in range(3)) + (255,)
        if (x, y) in crack:
            c = shade(c, 0.55)
        return c

    return img(f)


def main():
    save('pocket_concrete', concrete(11, (112, 110, 102), rust=0.18, cracks=2))
    save('pocket_thin_wall', concrete(11, (112, 110, 102), rust=0.18, cracks=5))
    save('pocket_mass', concrete(12, (36, 33, 31), rust=0.1, cracks=1))
    n = fbm(13)
    save('pocket_tile', img(lambda x, y: shade((168, 170, 156) if (x % 8 and y % 8) else (78, 76, 68),
                                                0.75 + n[y][x] * 0.35 - (0.25 if n[y][x] > 0.72 else 0))))
    n = fbm(14)
    save('pocket_mosaic', img(lambda x, y: shade(
        [(96, 44, 36), (118, 108, 84), (40, 46, 52), (124, 116, 96)][((x // 4) + (y // 4) * 3) % 4] if (x % 4 and y % 4) else (34, 30, 26),
        0.7 + n[y][x] * 0.4)))
    n = fbm(15)
    rr = random.Random(15)
    save('pocket_rot', img(lambda x, y: (
        shade((26, 16, 10), 0.8 + rr.random() * 0.3) if n[y][x] > 0.62 else shade((92, 50, 26), 0.6 + n[y][x] * 0.6))))
    n = fbm(16)
    save('pocket_metal', img(lambda x, y: shade(
        (60, 58, 56) if (y in (0, 15) or x in (0, 15)) else (98, 74, 58) if n[y][x] > 0.6 else (88, 90, 92),
        0.75 + n[y][x] * 0.35 + (0.25 if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)) else 0))))
    n = fbm(17)
    save('pocket_grate', img(lambda x, y: (shade((70, 64, 58), 0.8 + n[y][x] * 0.4) if (x % 4 == 0 or y % 4 == 0) else (0, 0, 0, 0))))
    n = fbm(18)
    save('pocket_pillar', img(lambda x, y: shade((126, 122, 112), (0.6 if x in (0, 5, 10, 15) else 0.85) + n[y][x] * 0.3)))
    save('pocket_pillar_top', concrete(19, (120, 116, 106), cracks=1))
    n = fbm(20)
    save('pocket_plaster', img(lambda x, y: shade((132, 136, 112), 0.72 + n[y][x] * 0.4 - (0.2 if noise(21)[y][x] > 0.8 else 0))))
    n = fbm(22)
    save('pocket_rock', img(lambda x, y: shade((70, 66, 64), 0.7 + n[y][x] * 0.5)))
    n = fbm(23)
    save('pocket_rock_dark', img(lambda x, y: shade((34, 30, 32), 0.7 + n[y][x] * 0.5)))
    n = fbm(24)
    save('pocket_asphalt', img(lambda x, y: shade((44, 44, 46), 0.75 + n[y][x] * 0.4 + random.Random(x * 31 + y).random() * 0.1)))
    # lamps: a caged dim bulb, and the dead one
    def lamp(on):
        def f(x, y):
            d = math.hypot(x - 7.5, y - 7.5)
            if x in (0, 15) or y in (0, 15):
                return (40, 38, 36, 255)
            if d < 5.5:
                if (x + y) % 5 == 0:
                    return (50, 46, 42, 255)
                return (clamp(236 - d * 14), clamp(214 - d * 16), clamp(150 - d * 18), 255) if on else (clamp(70 - d * 4), clamp(66 - d * 4), 58, 255)
            return (58, 54, 50, 255)
        return img(f)
    save('pocket_lamp', lamp(True))
    save('pocket_lamp_dead', lamp(False))
    n = fbm(25)
    save('pocket_void_glass', img(lambda x, y: (8, 6, 10, 255) if not (x == y or x == y + 1) else (34, 30, 40, 255)))
    save('pocket_daylight', img(lambda x, y: (clamp(236 + n[y][x] * 19), clamp(240 + n[y][x] * 15), 255, 255)))
    # paper note with scribbles
    rr = random.Random(26)
    save('pocket_note', img(lambda x, y: (0, 0, 0, 0) if (x < 3 or x > 12 or y < 2 or y > 13) else
                            ((40, 30, 26, 255) if (y % 2 == 0 and 4 <= x <= 11 and rr.random() < 0.6) else (196, 186, 156, 255))))
    n = fbm(27)
    save('pocket_seam', img(lambda x, y: shade((24, 22, 20), 0.8 + n[y][x] * 0.3) if abs(x - 8 + (y % 3) - 1) > 0 else (150, 150, 140, 255)))
    # stain: a thin smear of ooze
    n = fbm(28)
    save('pocket_stain', img(lambda x, y: (12, 8, 6, clamp((n[y][x] - 0.35) * 600)) if n[y][x] > 0.35 else (0, 0, 0, 0)))
    n = fbm(29)
    save('scp106_corrosion', img(lambda x, y: (clamp(18 + n[y][x] * 30), clamp(10 + n[y][x] * 14), 6, clamp((n[y][x] - 0.3) * 700))
                                 if n[y][x] > 0.3 else (0, 0, 0, 0)))
    # animated: ooze (slow glossy ripples), breach (a turning hole), sigil (a pulse)
    frames = 16
    sheet = Image.new('RGBA', (16, 16 * frames))
    base = fbm(30, 16, 16)
    for fr in range(frames):
        ph = fr / frames * math.tau
        def f(x, y):
            w = math.sin(x * 0.8 + ph) * math.cos(y * 0.6 - ph) * 0.5 + 0.5
            k = 0.35 + base[y][x] * 0.5 + w * 0.25
            spec = 1.0 if (base[y][x] > 0.7 and w > 0.8) else 0
            return (clamp(14 * k + spec * 60), clamp(10 * k + spec * 50), clamp(8 * k + spec * 40), 255)
        sheet.paste(img(f), (0, 16 * fr))
    save('pocket_ooze', sheet, frametime=3, interpolate=True)
    sheet = Image.new('RGBA', (16, 16 * frames))
    for fr in range(frames):
        ph = fr / frames * math.tau
        def f(x, y):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            s = math.sin(a * 3 + d * 0.9 - ph) * 0.5 + 0.5
            k = max(0.0, 1 - d / 10.5)
            return (clamp(20 + 150 * s * k), clamp(4 + 18 * s * k), clamp(8 + 24 * s * k), 255)
        sheet.paste(img(f), (0, 16 * fr))
    save('pocket_breach', sheet, frametime=2, interpolate=True)
    frames = 8
    sheet = Image.new('RGBA', (16, 16 * frames))
    rock = fbm(31)
    for fr in range(frames):
        p = 0.5 + 0.5 * math.sin(fr / frames * math.tau)
        def f(x, y):
            glyph = (x in (3, 12) and 2 <= y <= 13) or (y in (2, 13) and 3 <= x <= 12) or (x == y and 3 <= x <= 12)
            if glyph:
                return (clamp(140 + 110 * p), clamp(20 + 30 * p), clamp(14 + 20 * p), 255)
            return shade((40, 30, 28), 0.7 + rock[y][x] * 0.4)
        sheet.paste(img(f), (0, 16 * fr))
    save('pocket_sigil', sheet, frametime=4, interpolate=True)


if __name__ == '__main__':
    main()
