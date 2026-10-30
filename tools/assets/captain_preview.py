"""Offline preview of Captain America / Avengers models and clips (mirrors AnimRenderer math, see jinwoo_preview).
Usage: python3 tools/assets/captain_preview.py [clipfile clip t1,t2,...] -o out.png"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import jinwoo_preview as jp  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'viltrumitecore/src/main/resources/assets/viltrumitecore')
TEX = os.path.join(ROOT, 'build/jinwoo_preview/textures/entity')


def tex(rel):
    p = os.path.join(TEX, rel)
    return np.array(Image.open(p).convert('RGBA')) if os.path.exists(p) else None


def geo(rel):
    return jp.load(os.path.join(RES, 'geo', rel))


def bone_matrix(g, pose, target, root=None):
    bones = {b['name']: b for b in g['bones']}
    chain = []
    n = target
    while n:
        chain.append(n)
        n = bones[n].get('parent')
    M = np.eye(4) if root is None else root
    for name in reversed(chain):
        b = bones[name]
        p = b.get('pivot', [0, 0, 0])
        piv = np.array([-p[0], p[1], p[2]]) / 16
        r0 = b.get('rotation', [0, 0, 0])
        pp = pose.get(name, {})
        rr = pp.get('rotation', [0, 0, 0])
        ps = pp.get('position', [0, 0, 0])
        s = pp.get('scale', [1, 1, 1])
        if isinstance(s, (int, float)):
            s = [s] * 3
        M = M @ jp.tr(-ps[0] / 16, ps[1] / 16, ps[2] / 16) @ jp.tr(*piv) @ jp.rot_z(math.radians(r0[2] + rr[2])) \
            @ jp.rot_y(math.radians(-(r0[1] + rr[1]))) @ jp.rot_x(math.radians(-(r0[0] + rr[0]))) @ jp.sc(*s) @ jp.tr(*(-piv))
    p = bones[target].get('pivot', [0, 0, 0])
    return M @ jp.tr(-p[0] / 16, p[1] / 16, p[2] / 16)


SHIELD_ROT_Y = 90.0


def captain_quads(pose, shield=True, root=None):
    body, gear = geo('captain/captain.geo.json'), geo('captain/captain_gear.geo.json')
    q = jp.layered([(body, tex('captain/captain.png'), None), (gear, tex('captain/captain_gear.png'), None)], pose, root)
    if shield:
        sg = geo('captain/captain_shield.geo.json')
        M = bone_matrix(gear, pose, 'left_shield', root) @ jp.rot_y(math.radians(SHIELD_ROT_Y))
        q += jp.layered([(sg, tex('captain/captain_shield.png'), None)], {}, M)
    return q


def sheet(frames, size=300, cols=4, center=(0, 1.0, 0), scale=None, quads=captain_quads):
    tiles = []
    for title, pose, yaw, pitch in frames:
        im = Image.fromarray(jp.render(quads(pose), None, None, yaw, pitch, size, scale=scale, center=center))
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
    if len(a) > 2 and not a[0].startswith('-'):
        file, clip, ts = a[0], a[1], [float(x) for x in a[2].split(',')]
        yaw = float(a[a.index('--yaw') + 1]) if '--yaw' in a else 35
        frames = [(f'{clip} {t:.2f}', clip_pose(file, clip, t), yaw, -8) for t in ts]
    else:
        frames = [('front', {}, 0, 0), ('3/4', {}, 35, -10), ('side', {}, 90, 0), ('back', {}, 180, 0)]
    sheet(frames).save(out)


AVENGER_RIG = {'iron_man': 'avenger', 'thor': 'avenger', 'hawkeye': 'avenger', 'black_widow': 'avenger_slim'}


def avenger_quads(kind):
    """Quads builder for an Avenger: shared body rig + kind skin + gear (Hulk uses his own model)."""
    def build(pose, root=None):
        if kind == 'hulk':
            return jp.layered([(geo('avengers/hulk.geo.json'), tex('avengers/hulk.png'), None)], pose, root)
        body, gear = geo(f'avengers/{AVENGER_RIG[kind]}.geo.json'), geo(f'avengers/{kind}_gear.geo.json')
        return jp.layered([(body, tex(f'avengers/{kind}.png'), None),
                           (gear, tex(f'avengers/{kind}_gear.png'), None)], pose, root)
    return build


def prop_quads(key):
    def build(pose, root=None):
        return jp.layered([(geo(f'avengers/{key}.geo.json'), tex(f'avengers/{key}.png'), None)], pose, root)
    return build
