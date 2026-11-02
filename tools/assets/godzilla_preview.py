#!/usr/bin/env python3
"""Review renders of the Godzilla model (not committed): rest views per LOD and clip panels.
Run: python3 tools/assets/godzilla_preview.py [rest|clips|NAME ...]"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
import chitauri_lib as CL  # noqa: E402
import godzilla_geo as GG  # noqa: E402
import godzilla_lib as G  # noqa: E402
from PIL import Image  # noqa: E402

OUT = os.path.join(G.ROOT, 'build', 'godzilla_preview')


def textures():
    os.makedirs(OUT, exist_ok=True)
    base, glow = G.paint_atlas()
    bp, gp = os.path.join(OUT, 'atlas.png'), os.path.join(OUT, 'glow.png')
    Image.fromarray(base, 'RGBA').save(bp)
    Image.fromarray(glow, 'RGBA').save(gp)
    return bp, gp


def geo_of(detail):
    m = GG.build(detail)
    m.pack()
    return m.geo_json()


def rest(views=('front', 'side', 'q_front', 'back'), scale=2, glow=False, details=(2,)):
    bp, gp = textures()
    for d in details:
        geo = geo_of(d)
        out = os.path.join(OUT, 'rest_lod%d.png' % (2 - d))
        CL.preview(out, [(geo, {}, bp, gp if glow else None)], views=views, scale=scale)
        print(out)


def pose(name, poses, views=('side', 'q_front'), scale=2, glow=False):
    bp, gp = textures()
    geo = geo_of(2)
    ims = []
    for i, p in enumerate(poses):
        out = os.path.join(OUT, '%s_%d.png' % (name, i))
        ims.append(CL.preview(out, [(geo, CL.as_pose(p), bp, gp if glow else None)], views=views, scale=scale))
    CL.sheet(os.path.join(OUT, name + '.png'), ims, cols=min(4, len(ims)))
    print(os.path.join(OUT, name + '.png'))


if __name__ == '__main__':
    what = sys.argv[1:] or ['rest']
    if 'rest' in what:
        rest()
    if 'lods' in what:
        rest(views=('side', 'q_front'), details=(1, 0))
    if 'glow' in what:
        rest(views=('side', 'q_back'), glow=True)
