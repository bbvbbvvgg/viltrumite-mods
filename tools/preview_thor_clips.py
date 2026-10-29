#!/usr/bin/env python3
"""Contact sheets of Thor's clips (front / side / back, or first person) from posed-box exports.

Run from the repository root after
  THOR_PREVIEW=1 ./gradlew :viltrumitecore:test --tests '*ThorClipPreviewTest*'
The exporter poses the body (R1's model, or the vanilla-proportion stand-in) with exactly the game's
bone and cube transforms (AnimRenderer order) at each clip's key beats and writes every cube as an
oriented box plus the penetrations found (ThorIntersection). This script only draws them:
flat-shaded boxes, penetrating cubes in red, the contact (damage tick) column framed in yellow.
Third-person clips get three rows (front, side, back); `_fp` clips one first-person row
(camera at the eye, vertical FOV 70, arms and attachments only). Not a Minecraft acceptance run.

  python3 tools/preview_thor_clips.py [CLIP ...] [--source-dir DIR] [--output-dir DIR] [--cell 200x240]
"""
import argparse
import json
import math
import re
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'viltrumitecore'
FACES = ((0, 2, 6, 4), (1, 5, 7, 3), (0, 4, 5, 1), (2, 3, 7, 6), (0, 1, 3, 2), (4, 6, 7, 5))
LIGHT = (0.35, 0.8, -0.5)
FP_BONES = re.compile(r'arm|hand|finger|thumb|palm|wrist|elbow', re.I)
TAN = math.tan(math.radians(35))
NEAR = 0.05

# Java model space (x = -Bedrock x, y up, the model faces -z): (screen x, screen y up, depth toward the viewer).
VIEWS = {
    'front': lambda p: (-p[0], p[1], -p[2]),
    'side': lambda p: (p[2], p[1], -p[0]),
    'back': lambda p: (p[0], p[1], p[2]),
}


def color(box, hit):
    if hit:
        return (230, 60, 50)
    if box['m'] != 'body':
        return (200, 170, 90) if 'gold' in box['b'] or box['b'].startswith('cube') else (150, 160, 175)
    if FP_BONES.search(box['b']):
        return (120, 150, 200)
    if 'head' in box['b']:
        return (210, 180, 150)
    return (90, 105, 130)


def normal(pts):
    ax, ay, az = (pts[1][i] - pts[0][i] for i in range(3))
    bx, by, bz = (pts[2][i] - pts[0][i] for i in range(3))
    n = (ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
    length = math.sqrt(sum(c * c for c in n)) or 1.0
    return tuple(c / length for c in n)


def shade(rgb, n):
    k = 0.45 + 0.55 * abs(sum(n[i] * LIGHT[i] for i in range(3))) / math.sqrt(sum(c * c for c in LIGHT))
    return tuple(min(255, int(c * k)) for c in rgb)


def faces(frame, project, only=None):
    hit_ids = {h['a'] for h in frame['hits']} | {h['b'] for h in frame['hits']}
    out = []
    for box in frame['boxes']:
        if only is not None and not only(box):
            continue
        rgb = color(box, f"{box['m']}:{box['b']}" in hit_ids)
        for face in FACES:
            pts3 = [box['c'][i] for i in face]
            projected = [project(p) for p in pts3]
            if any(p is None for p in projected):
                continue
            out.append((sum(p[2] for p in projected) / 4, projected, shade(rgb, normal(pts3))))
    out.sort(key=lambda f: f[0])
    return out


def ortho_cell(frame, view, bounds, size):
    w, h = size
    (minx, maxx), (miny, maxy) = bounds
    scale = min((w - 16) / max(maxx - minx, 1e-6), (h - 30) / max(maxy - miny, 1e-6))
    ox = w / 2 - (minx + maxx) / 2 * scale
    oy = h - 8 + miny * scale
    img = Image.new('RGB', size, (36, 40, 48))
    draw = ImageDraw.Draw(img)
    draw.line((0, oy, w, oy), fill=(70, 74, 84))
    for _, pts, rgb in faces(frame, lambda p: view(p)):
        draw.polygon([(ox + p[0] * scale, oy - p[1] * scale) for p in pts], fill=rgb, outline=tuple(max(0, c - 40) for c in rgb))
    return img


def fp_cell(frame, eye, size):
    w, h = size
    aspect = w / h

    def project(p):
        x, y, z = p[0] - eye[0], p[1] - eye[1], -(p[2] - eye[2])
        if z < NEAR:
            return None
        return (w / 2 + x / (z * TAN * aspect) * w / 2, h / 2 - y / (z * TAN) * h / 2, -z)

    img = Image.new('RGB', size, (52, 60, 72))
    draw = ImageDraw.Draw(img)
    for _, pts, rgb in faces(frame, project, lambda b: b['m'] != 'body' or FP_BONES.search(b['b'])):
        draw.polygon([(p[0], p[1]) for p in pts], fill=rgb, outline=tuple(max(0, c - 40) for c in rgb))
    draw.line((w / 2 - 5, h / 2, w / 2 + 5, h / 2), fill=(230, 230, 230))
    draw.line((w / 2, h / 2 - 5, w / 2, h / 2 + 5), fill=(230, 230, 230))
    return img


def bounds(data, view):
    xs, ys = [], []
    for frame in data['frames']:
        for box in frame['boxes']:
            for c in box['c']:
                p = view(c)
                xs.append(p[0])
                ys.append(p[1])
    return (min(xs), max(xs)), (min(min(ys), 0.0), max(ys))


def sheet(data, cell):
    frames = data['frames']
    rows = ['fp'] if data.get('fp') else list(VIEWS)
    w, h = cell
    header = 34
    label = 16
    out = Image.new('RGB', (len(frames) * (w + 4) + 60, header + len(rows) * (h + label + 4)), (20, 22, 26))
    draw = ImageDraw.Draw(out)
    hits = sum(len(f['hits']) for f in frames)
    contact = data.get('contact')
    title = f"{data['clip']}  {data['length']} t = {data['length'] / 20:.2f} s"
    if contact is not None:
        title += f"  contact @{contact} ({contact / 20:.2f} s)"
    title += f"  penetrations: {hits}  body: {data['body']}"
    draw.text((6, 4), title, fill=(235, 235, 235))
    draw.text((6, 18), 'red = penetrating cube, yellow frame = damage tick', fill=(160, 160, 170))
    eye = data.get('eye', [0, 1.75, 0])
    for r, row in enumerate(rows):
        y = header + r * (h + label + 4)
        draw.text((4, y + h // 2), row, fill=(200, 200, 210))
        b = None if row == 'fp' else bounds(data, VIEWS[row])
        for i, frame in enumerate(frames):
            x = 60 + i * (w + 4)
            img = fp_cell(frame, eye, cell) if row == 'fp' else ortho_cell(frame, VIEWS[row], b, cell)
            out.paste(img, (x, y))
            if contact is not None and frame['tick'] == contact:
                draw.rectangle((x - 2, y - 2, x + w + 1, y + h + 1), outline=(250, 210, 40), width=2)
            text = f"{frame['beat']} @{frame['tick']}"
            if frame['hits']:
                text += f"  !{len(frame['hits'])} {max(hh['px'] for hh in frame['hits']):.1f}px"
            draw.text((x + 2, y + h + 2), text, fill=(250, 120, 110) if frame['hits'] else (200, 200, 200))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('clips', nargs='*')
    parser.add_argument('--source-dir', type=Path, default=ROOT / 'build/diagnostics/thor-clips')
    parser.add_argument('--output-dir', type=Path)
    parser.add_argument('--cell', default='200x240')
    args = parser.parse_args()
    cell = tuple(int(v) for v in args.cell.split('x'))
    out_dir = args.output_dir or args.source_dir / 'sheets'
    out_dir.mkdir(parents=True, exist_ok=True)
    files = sorted(args.source_dir.glob('*.json'))
    if args.clips:
        files = [f for f in files if f.stem in args.clips]
    if not files:
        raise SystemExit(f'no exports in {args.source_dir}; run the ThorClipPreviewTest export first')
    total = 0
    for f in files:
        data = json.loads(f.read_text())
        path = out_dir / f'{f.stem}.png'
        sheet(data, cell).save(path)
        hits = sum(len(fr['hits']) for fr in data['frames'])
        total += hits
        print(f'{path}  frames {len(data["frames"])}  penetrations {hits}')
    return 1 if total else 0


if __name__ == '__main__':
    raise SystemExit(main())
