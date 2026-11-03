"""Offline preview of the Venom model and clips (software renderer of jinwoo_preview, same bone math as
AnimRenderer). The marks are composited over the black atlas as in the formed body. Usage:
  python3 tools/assets/venom_preview.py -o out.png [--pulse 0..1]           turnaround + face close-ups
  python3 tools/assets/venom_preview.py FILE CLIP t1,t2,... -o out.png [--yaw 35] [--pulse 0.5]
FILE is relative to animations/ (e.g. venom/venom_moves.animation.json)."""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import jinwoo_preview as jp  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'viltrumitecore/src/main/resources/assets/viltrumitecore')
TEX = os.path.join(ROOT, 'build/jinwoo_preview/textures/entity/venom')


def tex(name):
    return np.array(Image.open(os.path.join(TEX, name)).convert('RGBA'))


def textures(pulse=0.0, marks=1.0):
    base = tex('venom.png').copy()
    mk = tex('venom_marks.png')
    a = (mk[..., 3:4].astype(float) / 255.0) * marks
    base[..., :3] = (base[..., :3] * (1 - a) + mk[..., :3] * a).astype(np.uint8)
    glow = tex('venom_eyes.png').astype(float) * 0.25 * marks
    glow[..., :3] += tex('venom_pulse.png')[..., :3].astype(float) * pulse * np.array([0.55, 0.6, 1.0])
    return base, np.clip(glow, 0, 255).astype(np.uint8)


def quads(pose, pulse=0.0, arms=False, root=None):
    g = jp.load(os.path.join(RES, 'geo/venom', 'venom_arms.geo.json' if arms else 'venom.geo.json'))
    base, glow = textures(pulse)
    return jp.layered([(g, base, glow)], pose, root)


def sheet(frames, pulse=0.0, size=320, cols=4, center=(0, 1.1, 0), scale=None):
    tiles = []
    for title, pose, yaw, pitch, *extra in frames:
        c = extra[0] if extra else center
        sc = extra[1] if len(extra) > 1 else scale
        im = Image.fromarray(jp.render(quads(pose, pulse), None, None, yaw, pitch, size, scale=sc or size / 2.9, center=c))
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
    pulse = float(a[a.index('--pulse') + 1]) if '--pulse' in a else 0.0
    if len(a) > 2 and not a[0].startswith('-'):
        file, clip, ts = a[0], a[1], [float(x) for x in a[2].split(',')]
        yaw = float(a[a.index('--yaw') + 1]) if '--yaw' in a else 35
        frames = [(f'{clip} {t:.2f}', clip_pose(file, clip, t), yaw, -8) for t in ts]
        sheet(frames, pulse, size=locals().get('size', 320)).save(out)
    else:
        size = int(a[a.index('--size') + 1]) if '--size' in a else 320
        face = (0, 1.68, -0.62)
        frames = [('front', {}, 0, 0, (0, 1.1, 0), size / 2.6), ('3/4', {}, 35, -10, (0, 1.1, 0), size / 2.6), ('side', {}, 90, 0, (0, 1.1, 0), size / 2.6),
                  ('back', {}, 180, 0, (0, 1.1, 0), size / 2.6),
                  ('face', {}, 0, 0, face, size * 1.5), ('face 3/4', {}, 30, -5, face, size * 1.5), ('face side', {}, 80, 0, face, size * 1.5),
                  ('face low', {}, 15, 12, face, size * 1.5)]
        sheet(frames, pulse, size=locals().get('size', 320)).save(out)
