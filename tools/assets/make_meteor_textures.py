"""Procedural textures of Meteor Rain 2.0 (own work, CC0).

Particle sprites (irregular volumetric smoke puffs, flame licks, sparks, ash flakes) and the additive
glow / plasma-tail textures of the meteor renderer. Writes base64 PNGs into the core binassets
(core's decodeBinaryAssets unpacks them). Deterministic: run again to reproduce byte-identical files.

    python tools/assets/make_meteor_textures.py [--preview out.png]
"""
import base64, io, os, sys
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'viltrumitecore', 'src', 'main', 'binassets', 'assets', 'viltrumitecore', 'textures')


def fbm(n, rng, octaves=4):
    out = np.zeros((n, n))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        g = 2 ** (o + 2)
        grid = rng.random((g + 1, g + 1))
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
        out += amp * (np.asarray(img) / 255.0)
        total += amp
        amp *= 0.5
    return out / total


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def save(rel, rgba):
    img = Image.fromarray(np.clip(rgba * 255 + 0.5, 0, 255).astype(np.uint8), 'RGBA')
    buf = io.BytesIO()
    img.save(buf, 'PNG', optimize=True)
    path = os.path.join(ROOT, rel + '.png.b64')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(base64.b64encode(buf.getvalue()).decode())
    return img


def smoke(i):
    # 64 px with smooth alpha: smoke puffs are drawn up to ~15 blocks wide, where stepped pixel-art alpha
    # turned a smoke column into a mosaic of hard-edged squares (seen in the runtime QA screenshots)
    n = 64
    rng = np.random.default_rng(1000 + i)
    y, x = np.mgrid[0:n, 0:n] + 0.5
    dens = np.zeros((n, n))
    lobes = 4 + rng.integers(0, 4)
    for _ in range(lobes):
        cx, cy = n / 2 + rng.normal(0, n * 0.11, 2)
        r = n * (0.16 + rng.random() * 0.13)
        d = np.hypot(x - cx, y - cy) / r
        dens = np.maximum(dens, np.clip(1 - d, 0, 1) ** 0.8)
    noise = fbm(n, rng, octaves=5)
    dens = dens + (noise - 0.5) * 0.45
    edge = np.hypot(x - n / 2, y - n / 2) / (n / 2)
    dens *= smoothstep(1.0, 0.6, edge)
    alpha = smoothstep(0.0, 0.6, dens)
    alpha[alpha < 0.02] = 0.0
    # light from the upper left: the lit side of a billow, a darker underside
    shade = 0.72 + 0.28 * np.clip(1.0 - ((x - n * 0.38) ** 2 + (y - n * 0.34) ** 2) / (n * 0.62) ** 2, 0, 1)
    shade = np.clip(shade * (0.9 + 0.2 * noise), 0, 1)
    rgba = np.dstack([shade, shade, shade, alpha * 0.9])
    return save('particle/meteor_smoke_%d' % i, rgba)


def flame(i):
    # 32 px, smooth alpha: the impact fireball draws these flames 3-8 blocks wide right in front of the camera
    n = 32
    rng = np.random.default_rng(2000 + i)
    y, x = np.mgrid[0:n, 0:n] + 0.5
    cx = n / 2 + rng.normal(0, 1.2)
    # teardrop: wider at the bottom, licking up
    dy = (y - n * 0.62) / (n * 0.42)
    dx = (x - cx) / (n * (0.30 + 0.12 * np.clip(dy + 0.6, 0, 1)))
    d = np.hypot(dx, dy)
    noise = fbm(n, rng, 4)
    dens = 1 - d + (noise - 0.5) * 0.5
    alpha = smoothstep(0.0, 0.5, dens)
    core = smoothstep(0.35, 0.85, dens)
    lum = 0.55 + 0.45 * core
    alpha[alpha < 0.03] = 0.0
    rgba = np.dstack([lum, lum, lum, alpha])
    return save('particle/meteor_flame_%d' % i, rgba)


def spark(i):
    n = 8
    y, x = np.mgrid[0:n, 0:n] + 0.5
    d = np.hypot(x - n / 2, y - n / 2)
    if i == 0:
        a = smoothstep(3.2, 0.6, d)
    else:
        cross = np.minimum(np.abs(x - n / 2), np.abs(y - n / 2))
        a = np.maximum(smoothstep(2.0, 0.4, d), smoothstep(1.1, 0.2, cross) * smoothstep(3.8, 1.0, d) * 0.7)
    lum = 0.7 + 0.3 * smoothstep(2.0, 0.0, d)
    return save('particle/meteor_spark_%d' % i, np.dstack([lum, lum, lum, np.round(a * 6) / 6]))


def ash(i):
    n = 8
    rng = np.random.default_rng(3000 + i)
    a = np.zeros((n, n))
    cx, cy = 3 + rng.integers(0, 2), 3 + rng.integers(0, 2)
    for _ in range(3 + i):
        a[np.clip(cy + rng.integers(-1, 2), 0, n - 1), np.clip(cx + rng.integers(-2, 3), 0, n - 1)] = 1
    a[cy, cx] = 1
    lum = np.full((n, n), 0.9)
    return save('particle/meteor_ash_%d' % i, np.dstack([lum, lum, lum, a]))


def glow():
    n = 128  # the halo is drawn up to 33 blocks wide; 64 px showed its texels up close
    y, x = np.mgrid[0:n, 0:n] + 0.5
    d = np.hypot(x - n / 2, y - n / 2) / (n / 2)
    v = np.clip(1 - d, 0, 1) ** 1.5 + 0.8 * np.clip(1 - d * 2.5, 0, 1) ** 2
    v = np.clip(v / v.max(), 0, 1)
    return save('entity/meteor_glow', np.dstack([v, v, v, v]))


def tail():
    # u (x) runs along the tail and tiles, v (y) across it
    w, h = 64, 32
    rng = np.random.default_rng(4000)
    y, x = np.mgrid[0:h, 0:w] + 0.5
    across = np.exp(-((y - h / 2) / (h * 0.26)) ** 2)
    streak = np.zeros((h, w))
    for k in range(10):
        f = 1 + rng.integers(1, 4)
        ph = rng.random() * 2 * np.pi
        row = h / 2 + rng.normal(0, h * 0.18)
        streak += np.exp(-((y - row) / (1.2 + rng.random() * 2.5)) ** 2) * (0.5 + 0.5 * np.sin(2 * np.pi * f * x / w + ph))
    v = across * (0.55 + 0.45 * np.clip(streak / streak.max(), 0, 1))
    v = np.clip(v / v.max(), 0, 1)
    return save('entity/meteor_tail', np.dstack([v, v, v, v]))


if __name__ == '__main__':
    imgs = [smoke(i) for i in range(8)] + [flame(i) for i in range(4)] + [spark(i) for i in range(2)] + [ash(i) for i in range(3)]
    g, t = glow(), tail()
    if '--preview' in sys.argv:
        out = sys.argv[sys.argv.index('--preview') + 1]
        sheet = Image.new('RGBA', (32 * 9 * 4, 32 * 4 + 64 * 4), (40, 40, 60, 255))
        for k, im in enumerate(imgs):
            big = im.resize((128, 128), Image.BILINEAR if im.size[0] > 32 else Image.NEAREST)
            sheet.alpha_composite(big, ((k % 9) * 128, 0 if k < 9 else 128))
        sheet.alpha_composite(g.resize((256, 256), Image.NEAREST), (0, 256))
        sheet.alpha_composite(t.resize((512, 256), Image.NEAREST), (300, 256))
        sheet.save(out)
    print('textures written')
