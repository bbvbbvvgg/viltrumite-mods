#!/usr/bin/env python3
"""Od Lagna's look for the Thanos scene (own work, CC0): Reinhard's own skin (same rig, same UVs)
recoloured into a radiant white-gold being, so the model is reused untouched and the Reinhard
hero keeps his own textures.

Base: every texel is mapped by luminance onto a warm ramp (deep gold -> gold -> cream -> white);
saturated reds (hair) become pale gold strands, blues (eyes) become bright gold. Glow: a soft
self-light over the whole body (he shines in the void) plus bright eyes.

Outputs binassets textures/entity/thanos/od_lagna_scene(.png.b64) and od_lagna_scene_glow, plain
copies in build/jinwoo_preview/. Run: python3 tools/assets/thanos_od_lagna.py
"""
import base64
import io
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_geo import BIN, write_png  # noqa: E402

RAMP = [(0.0, (92, 62, 18)), (0.35, (186, 136, 46)), (0.6, (236, 200, 112)), (0.8, (255, 240, 200)), (1.0, (255, 253, 246))]


def ramp(lum):
    out = np.zeros(lum.shape + (3,))
    for (t0, c0), (t1, c1) in zip(RAMP, RAMP[1:]):
        m = (lum >= t0) & (lum <= t1)
        f = ((lum - t0) / (t1 - t0))[m][:, None]
        out[m] = np.array(c0) * (1 - f) + np.array(c1) * f
    return out


def load(name):
    b = open(os.path.join(BIN, 'textures/entity/reinhard/%s.png.b64' % name)).read()
    return np.array(Image.open(io.BytesIO(base64.b64decode(b))).convert('RGBA')).astype(float)


def main():
    src = load('reinhard')
    rgb = src[..., :3] / 255.0
    lum = 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]
    mx, mn = rgb.max(-1), rgb.min(-1)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    red = (rgb[..., 0] > rgb[..., 2] + 0.15) & (sat > 0.35)
    blue = (rgb[..., 2] > rgb[..., 0] + 0.12) & (sat > 0.3)
    # lift everything: he is light itself; darks stay readable as gold lines
    k = np.clip(0.06 + lum * 0.88, 0, 1)
    k = np.where(red, np.clip(0.55 + lum * 0.5, 0, 1), k)  # hair: pale gold strands
    k = np.where(blue, 0.42, k)  # eyes and blue trims: amber gold
    out = np.zeros_like(src)
    out[..., :3] = ramp(k)
    out[..., 3] = src[..., 3]
    out = np.clip(out, 0, 255).astype(np.uint8)
    write_png('textures/entity/thanos/od_lagna_scene.png', out)
    glow = np.zeros_like(src)
    opaque = src[..., 3] > 0
    glow[..., :3] = out[..., :3].astype(float) * 0.16
    glow[blue, :3] = (255, 226, 140)
    eyes = load('reinhard_glow')[..., 3] > 0
    glow[eyes, :3] = (255, 232, 150)
    glow[..., 3] = np.where(opaque | eyes, 255, 0)
    glow[~(opaque | eyes), :3] = 0
    write_png('textures/entity/thanos/od_lagna_scene_glow.png', np.clip(glow, 0, 255).astype(np.uint8))
    print('od lagna scene skin: %d texels' % int(opaque.sum()))


if __name__ == '__main__':
    main()
