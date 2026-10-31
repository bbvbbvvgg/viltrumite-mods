#!/usr/bin/env python3
"""Offline previews of the Thanos model and clips (not committed; writes build/thanos-assets/).

Run: python3 tools/assets/thanos_preview.py rest            # four angles of the rest pose
     python3 tools/assets/thanos_preview.py clip NAME [N]   # N frames of one clip (3/4 view)
     python3 tools/assets/thanos_preview.py hand            # close-up of the gauntlet
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import jinwoo_preview as jp  # noqa: E402

def _draw_tri(img, zb, xs, ys, zs, uvs, tex, glow, shade, tw, th):
    """jinwoo_preview.draw_tri, but glow-only texels (base alpha 0) add light without hiding what is behind."""
    import math
    H, W = zb.shape
    x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
    y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
    if x1 < x0 or y1 < y0:
        return
    den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
    if abs(den) < 1e-9:
        return
    X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    a = ((ys[1] - ys[2]) * (X - xs[2]) + (xs[2] - xs[1]) * (Y - ys[2])) / den
    b = ((ys[2] - ys[0]) * (X - xs[2]) + (xs[0] - xs[2]) * (Y - ys[2])) / den
    c = 1 - a - b
    m = (a >= -1e-4) & (b >= -1e-4) & (c >= -1e-4)
    if not m.any():
        return
    z = a * zs[0] + b * zs[1] + c * zs[2]
    u = a * uvs[0][0] + b * uvs[1][0] + c * uvs[2][0]
    v = a * uvs[0][1] + b * uvs[1][1] + c * uvs[2][1]
    ui = np.clip(u.astype(int), 0, tw - 1)
    vi = np.clip(v.astype(int), 0, th - 1)
    texel = tex[vi, ui]
    sub = zb[y0:y1 + 1, x0:x1 + 1]
    region = img[y0:y1 + 1, x0:x1 + 1]
    gt = glow[vi, ui].astype(float) if glow is not None else np.zeros(texel.shape)
    solid = m & (texel[..., 3] > 8) & (z < sub)
    col = texel[..., :3].astype(float) * shade + gt[..., :3]
    region[solid] = col[solid]
    sub[solid] = z[solid]
    ghost = m & (texel[..., 3] <= 8) & (gt[..., :3].sum(-1) > 10) & (z < sub)
    region[ghost] = region[ghost] + gt[..., :3][ghost]


jp.draw_tri = _draw_tri
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'viltrumitecore/src/main/resources/assets/viltrumitecore')
PLAIN = os.path.join(ROOT, 'build/jinwoo_preview/textures/entity/thanos')
OUT = os.path.join(ROOT, 'build/thanos-assets')


def load():
    geo = jp.load(os.path.join(RES, 'geo/thanos/thanos.geo.json'))
    tex = np.array(Image.open(os.path.join(PLAIN, 'thanos.png')).convert('RGBA'))
    glow = np.array(Image.open(os.path.join(PLAIN, 'thanos_glow.png')).convert('RGBA'))
    return geo, tex, glow


def main(argv):
    os.makedirs(OUT, exist_ok=True)
    geo, tex, glow = load()
    mode = argv[0] if argv else 'rest'
    if mode == 'rest':
        frames = [('front', {}, 0, 0), ('3/4 left', {}, -35, -8), ('3/4 right', {}, 35, -8), ('back', {}, 180, 0)]
        jp.sheet(geo, tex, glow, frames, size=420, cols=4, center=(0, 1.9, 0), scale=420 / 4.4).save(os.path.join(OUT, 'rest.png'))
    elif mode == 'hand':
        frames = [('back of hand', {}, -90, 0), ('front', {}, -20, 0), ('3/4', {}, -55, -15), ('palm', {}, 90, 0)]
        jp.sheet(geo, tex, glow, frames, size=360, cols=4, center=(-1.03, 1.35, 0), scale=360 / 1.1).save(os.path.join(OUT, 'hand.png'))
    elif mode == 'head':
        frames = [('front', {}, 0, 0), ('3/4', {}, -35, -5), ('side', {}, -90, 0), ('below', {}, -20, 20)]
        jp.sheet(geo, tex, glow, frames, size=320, cols=4, center=(0, 3.4, 0), scale=320 / 1.1).save(os.path.join(OUT, 'head.png'))
    elif mode == 'clip':
        name = argv[1]
        n = int(argv[2]) if len(argv) > 2 else 8
        yaw = float(argv[3]) if len(argv) > 3 else -30
        import glob
        anims = {}
        for f in glob.glob(os.path.join(RES, 'animations/thanos/*.animation.json')):
            anims.update(json.load(open(f))['animations'])
        a = anims[name]
        length = a['animation_length']
        frames = []
        for i in range(n):
            t = length * i / max(1, n - 1)
            frames.append(('%s %.2fs' % (name, t), jp.pose_from_clip(a, t), yaw, -6))
        jp.sheet(geo, tex, glow, frames, size=300, cols=min(n, 6), center=(0, 1.9, 0), scale=300 / 4.8).save(
            os.path.join(OUT, 'clip_%s.png' % name))
    print('ok')


if __name__ == '__main__':
    main(sys.argv[1:])
