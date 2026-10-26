#!/usr/bin/env python3
"""CPU preview of the Saw Sphere geometry exported by SawSpherePreviewTest (not an in-game check).

Run from the repository root after
  SAW_PREVIEW=1 ./gradlew :viltrumitecore:test --tests '*SawSpherePreviewTest*'
Projects the camera-relative quads (vertical FOV 70), draws a grey ground grid and the test's
stone house for context, composites the fill pass with alpha blending (back to front) and the
glow pass additively, like SawSphereRenderer, and writes one PNG per frame plus a contact sheet.
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'viltrumitecore'
TAN = math.tan(math.radians(35))


def basis(eye, target):
    f = np.array(target, float) - np.array(eye, float)
    f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0]); r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return r, u, f


def project(p, cam, w, h):
    r, u, f = cam
    z = p @ f
    x = p @ r
    y = p @ u
    z = np.maximum(z, 1e-3)
    return np.stack([(x / (z * TAN * w / h) + 1) * .5 * w, (1 - y / (z * TAN)) * .5 * h], -1), z


def raster(img, poly, color, alpha, additive):
    h, w, _ = img.shape
    xs, ys = poly[:, 0], poly[:, 1]
    x0, x1 = max(0, int(xs.min())), min(w - 1, int(math.ceil(xs.max())))
    y0, y1 = max(0, int(ys.min())), min(h - 1, int(math.ceil(ys.max())))
    if x0 > x1 or y0 > y1:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
    # two triangles 0-1-2 and 0-2-3, barycentric colour / alpha interpolation
    for tri in ((0, 1, 2), (0, 2, 3)):
        a, b, c = poly[list(tri)]
        den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(den) < 1e-9:
            continue
        l1 = ((b[1] - c[1]) * (gx - c[0]) + (c[0] - b[0]) * (gy - c[1])) / den
        l2 = ((c[1] - a[1]) * (gx - c[0]) + (a[0] - c[0]) * (gy - c[1])) / den
        l3 = 1 - l1 - l2
        inside = (l1 >= 0) & (l2 >= 0) & (l3 >= 0)
        if not inside.any():
            continue
        col = (l1[..., None] * color[tri[0]] + l2[..., None] * color[tri[1]] + l3[..., None] * color[tri[2]])
        al = (l1 * alpha[tri[0]] + l2 * alpha[tri[1]] + l3 * alpha[tri[2]])[..., None]
        region = img[y0:y1 + 1, x0:x1 + 1]
        m = inside[..., None]
        if additive:
            region[:] = np.where(m, np.minimum(1, region + col * al), region)
        else:
            region[:] = np.where(m, region * (1 - al) + col * al, region)


def context(img, cam, eye, w, h, y0=64.0):
    draw = Image.fromarray((img * 255).astype(np.uint8))
    d = ImageDraw.Draw(draw)
    e = np.array(eye)
    for k in range(-30, 31, 2):
        for a, b in (([k, y0, -30], [k, y0, 30]), ([-30, y0, k], [30, y0, k])):
            pts = np.array([a, b], float) - e
            pr, z = project(pts, cam, w, h)
            if (z > .5).all():
                d.line([tuple(pr[0]), tuple(pr[1])], fill=(70, 78, 70), width=1)
    house = [[8.5, 14.5], [y0, y0 + 6], [-4, 3]]
    faces = []
    for axis in range(3):
        for side in range(2):
            quad = []
            for u in range(2):
                for v in ((0, 1) if u == 0 else (1, 0)):
                    p = [0, 0, 0]
                    p[axis] = house[axis][side]
                    o = [i for i in range(3) if i != axis]
                    p[o[0]] = house[o[0]][u]
                    p[o[1]] = house[o[1]][v]
                    quad.append(p)
            faces.append(quad)
    for quad in sorted(faces, key=lambda q: -np.linalg.norm(np.mean(q, 0) - e)):
        pr, z = project(np.array(quad, float) - e, cam, w, h)
        if (z > .5).all():
            d.polygon([tuple(p) for p in pr], fill=(105, 100, 95), outline=(60, 58, 55))
    return np.asarray(draw, dtype=np.float32) / 255


def render(path, w, h):
    data = json.loads(path.read_text())
    cam = basis(data['eye'], data['target'])
    img = np.zeros((h, w, 3), np.float32)
    img[:] = np.linspace([.42, .58, .85], [.70, .80, .92], h)[:, None, :]
    img = context(img, cam, data['eye'], w, h)
    for key, additive in (('fill', False), ('glow', True)):
        v = np.array(data[key], np.float32).reshape(-1, 4, 7)
        if not len(v):
            continue
        pos = v[..., :3]
        pr, z = project(pos.reshape(-1, 3), cam, w, h)
        pr = pr.reshape(-1, 4, 2)
        z = z.reshape(-1, 4)
        order = np.argsort(-z.mean(1)) if not additive else range(len(v))
        for q in order:
            if (z[q] < .3).any():
                continue
            raster(img, pr[q], v[q, :, 3:6] / 255, v[q, :, 6] / 255, additive)
    out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    ImageDraw.Draw(out).text((4, 2), path.stem, fill=(255, 255, 0))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--source-dir', type=Path, default=ROOT / 'build/diagnostics/saw-sphere')
    ap.add_argument('--output-dir', type=Path, default=ROOT / 'build/diagnostics/saw-sphere-png')
    ap.add_argument('--size', default='480x270')
    ap.add_argument('--columns', type=int, default=4)
    args = ap.parse_args()
    w, h = (int(v) for v in args.size.split('x'))
    args.output_dir.mkdir(parents=True, exist_ok=True)
    frames = []
    for path in sorted(args.source_dir.glob('*.json')):
        im = render(path, w, h)
        im.save(args.output_dir / (path.stem + '.png'))
        frames.append(im)
    if frames:
        rows = math.ceil(len(frames) / args.columns)
        sheet = Image.new('RGB', (w * args.columns, h * rows), (20, 20, 24))
        for i, im in enumerate(frames):
            sheet.paste(im, ((i % args.columns) * w, (i // args.columns) * h))
        sheet.save(args.output_dir / 'sheet.png')


if __name__ == '__main__':
    main()
