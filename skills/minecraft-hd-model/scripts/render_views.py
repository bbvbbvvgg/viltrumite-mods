"""Render a 4-view sheet of one or more geo + texture pairs (textures read from .png.b64 or .png).
Usage (repo root): python3 skills/minecraft-hd-model/scripts/render_views.py OUT.png --center 0,1.0,0 --size 2.2 \
    --views 0/0,90/0,180/0,35/-10 captain/captain:captain/captain captain/captain_gear:captain/captain_gear
Each pair is GEO_REL:TEX_REL relative to geo/ and textures/entity/ (no extension)."""
import argparse, base64, io, os, sys
import numpy as np
from PIL import Image, ImageDraw
ROOT = os.getcwd()
sys.path.insert(0, os.path.join(ROOT, 'tools/assets'))
import jinwoo_preview as jp  # noqa: E402
RES = os.path.join(ROOT, 'viltrumitecore/src/main/resources/assets/viltrumitecore')
BIN = os.path.join(ROOT, 'viltrumitecore/src/main/binassets/assets/viltrumitecore')
ap = argparse.ArgumentParser()
ap.add_argument('out'); ap.add_argument('pairs', nargs='+')
ap.add_argument('--center', default='0,1.0,0'); ap.add_argument('--size', type=float, default=2.2)
ap.add_argument('--views', default='0/0,90/0,180/0,35/-10'); ap.add_argument('--px', type=int, default=500)
a = ap.parse_args()


def tex(rel):
    p = os.path.join(BIN, 'textures/entity', rel + '.png.b64')
    if os.path.exists(p):
        return np.array(Image.open(io.BytesIO(base64.b64decode(open(p).read()))).convert('RGBA'))
    return np.array(Image.open(os.path.join(ROOT, 'build/jinwoo_preview/textures/entity', rel + '.png')).convert('RGBA'))


layers = [(jp.load(os.path.join(RES, 'geo', g + '.geo.json')), tex(t), None) for g, t in (p.split(':') for p in a.pairs)]
q = jp.layered(layers, {}, None)
center = tuple(float(v) for v in a.center.split(','))
views = [tuple(float(v) for v in s.split('/')) for s in a.views.split(',')]
img = Image.new('RGB', (a.px * len(views), a.px + 24), (40, 42, 48))
for i, (yaw, pitch) in enumerate(views):
    im = Image.fromarray(jp.render(q, None, None, yaw, pitch, a.px, scale=a.px / a.size, center=center))
    img.paste(im, (i * a.px, 24))
    ImageDraw.Draw(img).text((i * a.px + 6, 6), f'yaw {yaw:g} pitch {pitch:g}', fill=(255, 255, 255))
img.save(a.out)
print('saved', a.out)
