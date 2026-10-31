"""Captain America head pass (rework §3.1) on top of the committed model (captain_model.py no longer reproduces
the shipped body/gear byte for byte, so the head is refined here instead of regenerating everything).

Base: the body / gear geometry and textures of commit BASE (read with `git show`, so the pass is repeatable).
* The head is narrower (8 -> 7.6 wide and deep) and the whole helmet with its wings, ear discs, cheek guards and
  chin strap is scaled in with it (x/z * 0.93): no more oversized cube on the shoulders.
* The helmet shell becomes an octagon: two crossed boxes and four 45-degree corner prisms instead of one box, so
  every view reads round; the top cap is cut the same way.
* Steve's face gets real relief: brow ridge, a nose with nostril shade, cheekbones and a squared chin with a
  cleft, painted from the face's own palette, so the face reads at a distance and in profile.
New islands go below the used area of each atlas with 1-texel edge gutters (geo_lint rule).
Run from the repository root: python3 tools/assets/captain_head.py && python3 tools/assets/geo_lint.py captain"""
import base64
import io
import json
import math
import os
import subprocess

import numpy as np
from PIL import Image

BASE = '9f15f6c0'
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
GEO = 'viltrumitecore/src/main/resources/assets/viltrumitecore/geo/captain/'
TEX = 'viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/captain/'
HEAD_W = 7.6           # new head width / depth (was 8)
HELMET_K = 0.93        # helmet x/z scale
CHAMFER = 0.45         # octagon corner cut of the helmet shell (bigger cuts let the head's corners poke through)
CAP_CHAMFER = 0.95     # the top cap sits above the head, it can be cut deeper
SKIN, SKIN_HI, SKIN_LO, SKIN_DK = (237, 194, 159), (251, 204, 166), (201, 134, 118), (150, 96, 80)
BROW, BROW_HI = (127, 88, 55), (141, 102, 64)


def git(path):
    return subprocess.run(['git', '-C', ROOT, 'show', f'{BASE}:{path}'], capture_output=True, check=True).stdout


def load_geo(name):
    return json.loads(git(GEO + name))


def load_png(name):
    return np.array(Image.open(io.BytesIO(base64.b64decode(git(TEX + name + '.png.b64')))).convert('RGBA'))


def save_png(name, arr):
    buf = io.BytesIO()
    Image.fromarray(arr).save(buf, 'PNG', optimize=True)
    with open(os.path.join(ROOT, TEX, name + '.png.b64'), 'w') as f:
        f.write(base64.encodebytes(buf.getvalue()).decode('ascii'))


def save_geo(name, g):
    with open(os.path.join(ROOT, GEO, name), 'w') as f:
        json.dump(g, f, separators=(',', ':'))


class Atlas:
    """Packs new islands (rows, left to right) below `top` with a 1-texel gutter that repeats the island edge."""

    def __init__(self, img, top):
        self.img, self.x, self.y, self.row = img, 1, top + 2, 0

    def put(self, island):
        h, w = island.shape[:2]
        if self.x + w + 1 > self.img.shape[1]:
            self.x, self.y, self.row = 1, self.y + self.row + 2, 0
        x, y = self.x, self.y
        assert y + h + 1 <= self.img.shape[0], 'atlas full'
        pad = np.pad(island, ((1, 1), (1, 1), (0, 0)), mode='edge')
        self.img[y - 1:y + h + 1, x - 1:x + w + 1] = pad
        self.x += w + 2
        self.row = max(self.row, h)
        return [x, y], [w, h]


def tx(n):
    """Texels for n model units (2 per unit, as the shipped atlases)."""
    return max(1, int(math.ceil(n * 2 - 1e-6)))


def shade(w, h, base, top=0.0, bottom=0.0, left=0.0, right=0.0):
    """Flat colour with linear light ramps (fractions, + = lighter)."""
    yy, xx = np.mgrid[0:h, 0:w]
    v = (yy + 0.5) / h
    u = (xx + 0.5) / w
    k = 1.0 + top * (1 - v) + bottom * v + left * (1 - u) + right * u
    rgb = np.clip(np.array(base, float)[None, None, :] * k[..., None], 0, 255)
    return np.dstack([rgb, np.full((h, w), 255.0)]).astype(np.uint8)


def resample(img, uv, size, w, h, crop=(0.0, 1.0, 0.0, 1.0)):
    """A (w x h) island taken from an existing face island (nearest), optionally its sub-rectangle."""
    (u, v), (sw, sh) = uv, size
    x0, x1, y0, y1 = crop
    xs = (u + (x0 + (x1 - x0) * (np.arange(w) + 0.5) / w) * sw).astype(int)
    ys = (v + (y0 + (y1 - y0) * (np.arange(h) + 0.5) / h) * sh).astype(int)
    return img[np.clip(ys, 0, img.shape[0] - 1)][:, np.clip(xs, 0, img.shape[1] - 1)].copy()


def cube(atlas, origin, size, faces, rotation=None, pivot=None):
    """faces: name -> island array (sized to the face)."""
    c = {'origin': [round(x, 4) for x in origin], 'size': [round(x, 4) for x in size], 'uv': {}}
    if rotation:
        c['rotation'], c['pivot'] = rotation, pivot
    for name, isl in faces.items():
        uv, sz = atlas.put(isl)
        c['uv'][name] = {'uv': uv, 'uv_size': sz}
    return c


def used_v(g):
    return max(f['uv'][1] + abs(f['uv_size'][1]) for b in g['bones'] for c in b.get('cubes', []) for f in c['uv'].values())


def face_sizes(size):
    w, h, d = size
    return {'north': (tx(w), tx(h)), 'south': (tx(w), tx(h)), 'east': (tx(d), tx(h)), 'west': (tx(d), tx(h)),
            'up': (tx(w), tx(d)), 'down': (tx(w), tx(d))}


def body():
    g = load_geo('captain.geo.json')
    img = load_png('captain')
    geo = g['minecraft:geometry'][0]
    atlas = Atlas(img, used_v(geo))
    head = next(b for b in geo['bones'] if b['name'] == 'head')
    c = head['cubes'][0]
    c['origin'] = [-HEAD_W / 2, 24, -HEAD_W / 2]
    c['size'] = [HEAD_W, 8, HEAD_W]
    z = -HEAD_W / 2
    cubes = []
    # brow ridge over the painted brows (row 8 of the face)
    w, h, d = 5.6, 0.5, 0.2
    brow = shade(tx(w), tx(h), BROW, top=0.15, bottom=-0.1)
    brow[:, tx(w) // 2 - 1:tx(w) // 2 + 1] = shade(2, tx(h), SKIN, top=0.05)  # the gap between the brows
    cubes.append(cube(atlas, [-w / 2, 27.5, z - d], [w, h, d],
                      {'north': brow, 'up': shade(tx(w), tx(d), BROW_HI, top=0.1), 'down': shade(tx(w), tx(d), BROW, top=-0.3),
                       'east': shade(tx(d), tx(h), BROW, top=-0.1), 'west': shade(tx(d), tx(h), BROW, top=-0.1)}))
    # nose: bridge to tip, lit front, shaded sides, nostrils underneath
    w, h, d = 1.0, 1.75, 0.55
    front = shade(tx(w), tx(h), SKIN, top=0.02, bottom=0.05)
    front[0, :] = SKIN_HI + (255,)
    front[-1, :] = SKIN_LO + (255,)
    cubes.append(cube(atlas, [-w / 2, 25.75, z - d], [w, h, d],
                      {'north': front, 'east': shade(tx(d), tx(h), SKIN, bottom=-0.18, left=-0.05),
                       'west': shade(tx(d), tx(h), SKIN, bottom=-0.18, right=-0.05), 'up': shade(tx(w), tx(d), SKIN_HI),
                       'down': shade(tx(w), tx(d), SKIN_DK, top=0.1)}))
    # cheekbones: soft highlights under the eyes
    w, h, d = 1.0, 0.5, 0.12
    for x0 in (-2.85, 1.85):
        cubes.append(cube(atlas, [x0, 26.0, z - d], [w, h, d],
                          {'north': shade(tx(w), tx(h), SKIN_HI, bottom=-0.08), 'down': shade(tx(w), tx(d), SKIN_LO),
                           'up': shade(tx(w), tx(d), SKIN_HI), 'east': shade(tx(d), tx(h), SKIN), 'west': shade(tx(d), tx(h), SKIN)}))
    # squared chin with a cleft
    w, h, d = 2.8, 0.85, 0.28
    chin = shade(tx(w), tx(h), SKIN, top=0.04, bottom=-0.1)
    chin[:, tx(w) // 2 - 1:tx(w) // 2 + 1] = (np.array(SKIN_LO + (255,))).astype(np.uint8)
    cubes.append(cube(atlas, [-w / 2, 24.0, z - d], [w, h, d],
                      {'north': chin, 'up': shade(tx(w), tx(d), SKIN_HI), 'down': shade(tx(w), tx(d), SKIN_DK),
                       'east': shade(tx(d), tx(h), SKIN, bottom=-0.15), 'west': shade(tx(d), tx(h), SKIN, bottom=-0.15)}))
    head['cubes'] += cubes
    save_geo('captain.geo.json', g)
    save_png('captain', img)
    return len(cubes)


def gear():
    g = load_geo('captain_gear.geo.json')
    img = load_png('captain_gear')
    geo = g['minecraft:geometry'][0]
    atlas = Atlas(img, used_v(geo))
    head = next(b for b in geo['bones'] if b['name'] == 'head')
    k = HELMET_K
    for c in head['cubes']:
        o, s = c['origin'], c['size']
        c['origin'] = [round(o[0] * k, 4), o[1], round(o[2] * k, 4)]
        c['size'] = [round(s[0] * k, 4), s[1], round(s[2] * k, 4)]
        if 'pivot' in c:
            p = c['pivot']
            c['pivot'] = [round(p[0] * k, 4), p[1], round(p[2] * k, 4)]
    # the shell (first cube) and the top cap (second) become octagons
    out = []
    for idx, ch in ((0, CHAMFER), (1, CAP_CHAMFER)):
        src = head['cubes'][idx]
        (x0, y0, z0), (sx, sy, sz) = src['origin'], src['size']
        X, Z = sx / 2, sz / 2
        uv = src['uv']
        # box A: full width, depth cut; box B: width cut, full depth (its top 0.03 lower: no shared plane)
        a = {'east': resample(img, uv['east']['uv'], uv['east']['uv_size'], tx(sz - 2 * ch), tx(sy), (ch / sz, 1 - ch / sz, 0, 1)),
             'west': resample(img, uv['west']['uv'], uv['west']['uv_size'], tx(sz - 2 * ch), tx(sy), (ch / sz, 1 - ch / sz, 0, 1)),
             'up': resample(img, uv['up']['uv'], uv['up']['uv_size'], tx(sx), tx(sz - 2 * ch), (0, 1, ch / sz, 1 - ch / sz))}
        out.append(cube(atlas, [x0, y0, -Z + ch], [sx, sy, sz - 2 * ch], a))
        b = {'north': resample(img, uv['north']['uv'], uv['north']['uv_size'], tx(sx - 2 * ch), tx(sy), (ch / sx, 1 - ch / sx, 0, 1)),
             'south': resample(img, uv['south']['uv'], uv['south']['uv_size'], tx(sx - 2 * ch), tx(sy), (ch / sx, 1 - ch / sx, 0, 1)),
             'up': resample(img, uv['up']['uv'], uv['up']['uv_size'], tx(sx - 2 * ch), tx(sz), (ch / sx, 1 - ch / sx, 0, 1))}
        out.append(cube(atlas, [-X + ch, y0, z0], [sx - 2 * ch, sy - 0.03, sz], b))
        # corner prisms: a square of side ch*sqrt2 turned 45 degrees about Y on each corner (top 0.06 lower)
        side = ch * math.sqrt(2)
        for cx, cz in ((X - ch, Z - ch), (-(X - ch), Z - ch), (X - ch, -(Z - ch)), (-(X - ch), -(Z - ch))):
            f = {n: resample(img, uv['east']['uv'], uv['east']['uv_size'], tx(side), tx(sy), (0.4, 0.6, 0, 1))
                 for n in ('north', 'south', 'east', 'west')}
            f['up'] = resample(img, uv['up']['uv'], uv['up']['uv_size'], tx(side), tx(side), (0.4, 0.6, 0.4, 0.6))
            out.append(cube(atlas, [cx - side / 2, y0, cz - side / 2], [side, sy - 0.06, side], f,
                            rotation=[0, 45, 0], pivot=[round(cx, 4), y0, round(cz, 4)]))
    head['cubes'] = out + head['cubes'][2:]
    save_geo('captain_gear.geo.json', g)
    save_png('captain_gear', img)
    return len(out)


def main():
    nb = body()
    ng = gear()
    print('captain head pass: body +%d face cubes, helmet %d octagon cubes' % (nb, ng))


if __name__ == '__main__':
    main()
