"""Offline preview of the Red Hulk model and clips (software renderer of jinwoo_preview, same bone math as
AnimRenderer). Usage:
  python3 tools/assets/red_hulk_preview.py -o out.png [--heat 0..1]                 turnaround + face close-ups
  python3 tools/assets/red_hulk_preview.py FILE CLIP t1,t2,... -o out.png [--yaw 35] [--heat 0.6]
FILE is relative to animations/ (e.g. red_hulk/red_hulk_combat.animation.json)."""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import jinwoo_preview as jp  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'viltrumitecore/src/main/resources/assets/viltrumitecore')
TEX = os.path.join(ROOT, 'build/jinwoo_preview/textures/entity/red_hulk')


def tex(name):
    return np.array(Image.open(os.path.join(TEX, name)).convert('RGBA'))


def quads(pose, heat=0.0, arms=False, root=None):
    g = jp.load(os.path.join(RES, 'geo/red_hulk', 'red_hulk_arms.geo.json' if arms else 'red_hulk.geo.json'))
    base = tex('red_hulk.png')
    glow = tex('red_hulk_eyes.png').astype(float)
    glow[..., :3] += tex('red_hulk_heat.png')[..., :3].astype(float) * heat
    glow = np.clip(glow, 0, 255).astype(np.uint8)
    return jp.layered([(g, base, glow)], pose, root)


def sheet(frames, heat=0.0, size=320, cols=4, center=(0, 1.05, 0), scale=None):
    tiles = []
    for title, pose, yaw, pitch, *extra in frames:
        c = extra[0] if extra else center
        sc = extra[1] if len(extra) > 1 else scale
        im = Image.fromarray(jp.render(quads(pose, heat), None, None, yaw, pitch, size, scale=sc or size / 2.9, center=c))
        ImageDraw.Draw(im).text((4, 4), title, fill=(255, 255, 255))
        tiles.append(im)
    rows = (len(tiles) + cols - 1) // cols
    out = Image.new('RGB', (cols * size, rows * size), (30, 30, 30))
    for i, t in enumerate(tiles):
        out.paste(t, ((i % cols) * size, (i // cols) * size))
    return out


def clip_pose(file, clip, t):
    anim = json.load(open(os.path.join(RES, 'animations', file)))['animations'][clip]
    return jp.pose_from_clip(anim, t)


if __name__ == '__main__':
    a = sys.argv[1:]
    out = a[a.index('-o') + 1]
    heat = float(a[a.index('--heat') + 1]) if '--heat' in a else 0.0
    if len(a) > 2 and not a[0].startswith('-'):
        file, clip, ts = a[0], a[1], [float(x) for x in a[2].split(',')]
        yaw = float(a[a.index('--yaw') + 1]) if '--yaw' in a else 35
        frames = [(f'{clip} {t:.2f}', clip_pose(file, clip, t), yaw, -8) for t in ts]
        sheet(frames, heat).save(out)
    else:
        face = (0, 1.78, -0.3)
        frames = [('front', {}, 0, 0), ('3/4', {}, 35, -10), ('side', {}, 90, 0), ('back', {}, 180, 0),
                  ('face', {}, 0, 0, face, 640), ('face 3/4', {}, 30, -5, face, 640), ('face side', {}, 80, 0, face, 640),
                  ('face low', {}, 15, 12, face, 640)]
        sheet(frames, heat).save(out)
