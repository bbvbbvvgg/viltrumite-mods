#!/usr/bin/env python3
"""CPU first-person preview of FirstPersonScene exports (not a Minecraft acceptance run).

Run from the repository root after
  REINHARD_FP_PREVIEW=1 ./gradlew :viltrumitecore:test --tests '*ReinhardFirstPersonPreviewTest*'
Projects the exported view-space quads like the game camera (vertical FOV 70, 16:9),
draws the crosshair and writes one PNG per frame plus contact sheets per prefix.
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'viltrumitecore'
TEXTURES = ROOT / 'build/resources/main/assets/viltrumitecore/textures/entity/reinhard'
TAN = math.tan(math.radians(35))
NEAR = 0.05


def load_texture(name, cache={}):
    if name not in cache:
        cache[name] = np.asarray(Image.open(TEXTURES / name).convert('RGBA'), dtype=np.float32)
    return cache[name]


def project(p, w, h):
    x, y, z = p
    depth = max(-z, 1e-6)
    nx = x / (depth * TAN * (w / h))
    ny = y / (depth * TAN)
    return ((nx + 1) * .5 * w, (1 - ny) * .5 * h, 1.0 / depth)


def raster(img, zbuf, tri, uvs, tex, shade):
    h, w = zbuf.shape
    (ax, ay, aw), (bx, by, bw), (cx, cy, cw) = tri
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(den) < 1e-9:
        return
    x0, x1 = max(0, int(min(ax, bx, cx))), min(w - 1, int(max(ax, bx, cx)) + 1)
    y0, y1 = max(0, int(min(ay, by, cy))), min(h - 1, int(max(ay, by, cy)) + 1)
    if x0 > x1 or y0 > y1:
        return
    xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
    l1 = ((by - cy) * (xs - cx) + (cx - bx) * (ys - cy)) / den
    l2 = ((cy - ay) * (xs - cx) + (ax - cx) * (ys - cy)) / den
    l3 = 1 - l1 - l2
    inside = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
    if not inside.any():
        return
    iw = l1 * aw + l2 * bw + l3 * cw
    u = (l1 * uvs[0][0] * aw + l2 * uvs[1][0] * bw + l3 * uvs[2][0] * cw) / iw
    v = (l1 * uvs[0][1] * aw + l2 * uvs[1][1] * bw + l3 * uvs[2][1] * cw) / iw
    th, tw = tex.shape[:2]
    iu = np.clip((u * tw).astype(int), 0, tw - 1)
    iv = np.clip((v * th).astype(int), 0, th - 1)
    color = tex[iv, iu]
    region = zbuf[y0:y1 + 1, x0:x1 + 1]
    ok = inside & (color[..., 3] >= 16) & (iw > region)
    region[ok] = iw[ok]
    target = img[y0:y1 + 1, x0:x1 + 1]
    target[ok] = color[ok][:, :3] * shade


def render(scene, w, h):
    img = np.zeros((h, w, 3), dtype=np.float32)
    sky = np.linspace(150, 90, h)[:, None]
    img[..., 0] = sky * .55
    img[..., 1] = sky * .7
    img[..., 2] = sky
    zbuf = np.full((h, w), -1.0, dtype=np.float32)
    light = np.array([.35, .8, .45])
    light /= np.linalg.norm(light)
    markers = []
    for mesh in scene:
        if 'marker' in mesh:
            markers.append(mesh)
            continue
        tex = load_texture(mesh['texture'])
        for quad in mesh['quads']:
            pts = np.array([q[:3] for q in quad])
            if (-pts[:, 2] < NEAR).any():
                continue
            n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
            norm = np.linalg.norm(n)
            shade = .55 + .45 * abs(float(np.dot(n / norm, light))) if norm > 1e-12 else 1
            proj = [project(p, w, h) for p in pts]
            uv = [(q[3], q[4]) for q in quad]
            for a, b, c in ((0, 1, 2), (0, 2, 3)):
                raster(img, zbuf, (proj[a], proj[b], proj[c]), (uv[a], uv[b], uv[c]), tex, shade)
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    draw = ImageDraw.Draw(out)
    cx, cy = w // 2, h // 2
    draw.line([(cx - 7, cy), (cx + 7, cy)], fill=(255, 255, 255))
    draw.line([(cx, cy - 7), (cx, cy + 7)], fill=(255, 255, 255))
    for m in markers:
        x, y, _ = project(m['marker'], w, h)
        draw.ellipse([x - 3, y - 3, x + 3, y + 3], outline=m.get('color', 'red'))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('samples', nargs='*')
    parser.add_argument('--source-dir', type=Path, default=ROOT / 'build/diagnostics/reinhard-fp')
    parser.add_argument('--output-dir', type=Path)
    parser.add_argument('--size', default='480x270')
    parser.add_argument('--columns', type=int, default=4)
    args = parser.parse_args()
    w, h = (int(v) for v in args.size.split('x'))
    out_dir = args.output_dir or args.source_dir / 'previews'
    out_dir.mkdir(parents=True, exist_ok=True)
    sheets = {}
    for source in sorted(args.source_dir.glob('*.json')):
        if args.samples and not any(source.stem.startswith(s) for s in args.samples):
            continue
        image = render(json.loads(source.read_text()), w, h)
        ImageDraw.Draw(image).text((6, 4), source.stem, fill=(255, 255, 0))
        image.save(out_dir / (source.stem + '.png'))
        sheets.setdefault(source.stem.split('__')[0], []).append(image)
    for prefix, images in sheets.items():
        cols = min(args.columns, len(images))
        rows = (len(images) + cols - 1) // cols
        sheet = Image.new('RGB', (cols * w + (cols - 1) * 4, rows * h + (rows - 1) * 4), (20, 20, 24))
        for i, image in enumerate(images):
            sheet.paste(image, ((i % cols) * (w + 4), (i // cols) * (h + 4)))
        sheet.save(out_dir / ('sheet_' + prefix + '.png'))


if __name__ == '__main__':
    main()
