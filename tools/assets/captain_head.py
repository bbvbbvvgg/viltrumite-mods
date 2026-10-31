"""Captain America head, face and helmet (rework §3.1, second pass) on top of the committed model.

captain_model.py no longer reproduces the shipped body / gear byte for byte, so the head is rebuilt here from the
blobs of commit BASE (read with `git show`, so the pass is repeatable and idempotent).

Why the first pass failed (checked in game, 2026-10-31): the helmet crown still started at eye level
(y 28.5, a 4.4-unit blue block plus a 0.6 brow band in front of the eyes), the cheek guards were 1.3 units wide on
each side and a thick brown chin strap crossed the chin, so only about 5.6 x 4 units of the 8 x 8 head front were
face, i.e. ~11 x 8 texels at 2 texels per unit: eyes, nose and mouth were crammed into that, the mouth read as a
moustache and the relief cubes (nose / chin / cheeks) added dark under-faces as stray dark pixels. Scaling the
helmet by 0.93 in x/z only shrank the face further.

This pass:
* Head 7.5 x 8 x 7.5; helmet only 0.25 thick (8.0 wide, the old one was 8.8): the crown starts at the brow
  (y 29), with a thin rim over the eyes, cheek guards in three steps (0.5 onto the face at the eyes, 0.75 at the
  mouth, 1.25 at the chin, so the opening narrows like a jaw instead of a square) and a thin strap under the chin.
* The face follows Reinhard's face (the project's best-reading face): big clean pixels (2 per unit, 15 x 16),
  even pale skin, big two-row eyes (white + blue iris) under a dark lash line, a brow line, a one-pixel nose
  shadow and a short soft mouth; no noise and no relief cubes (they made stray dark pixels).
* Crown, cowl and top cap are octagons (45-degree corner prisms) within the clearance of the head's corners;
  the A, the wings and the ear discs of BASE keep their islands and move onto the new surface.
New islands go below the used area of each atlas with 1-texel edge gutters (geo_lint rule).
Run from the repository root: python3 tools/assets/captain_head.py && python3 tools/assets/geo_lint.py captain"""
import base64
import io
import json
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
BASE = '9f15f6c0'
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
GEO = 'viltrumitecore/src/main/resources/assets/viltrumitecore/geo/captain/'
TEX = 'viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/captain/'

HX = 3.75            # head half width / depth
HY0, HY1 = 24.0, 32.0
T = 0.25             # helmet thickness
O = HX + T           # helmet outer half width (4.0)
BROW_Y = 29.0        # crown bottom = top of the face opening
GUARD_IN = 3.25      # inner edge of the cheek guards (|x|) at the eyes
MID_IN, MID_Y = 3.0, 25.9   # ... at the mouth
JAW_IN, JAW_Y = 2.5, 25.0   # ... at the chin: the opening narrows like a jaw, the face is no longer a square
GUARD_Z = -2.6       # back edge of the guards / front of the cowl
CH = 0.42            # crown / cowl corner cut (the head corner needs (2*O-CH)/sqrt2 > HX*sqrt2)
FACE_PX = 2          # face texels per unit (big readable pixels, as Reinhard's face)

# skin and face palette
SKIN, SKIN_HI, SKIN_SH2 = (242, 204, 174), (250, 218, 192), (230, 188, 158)
SKIN_SH, SKIN_DK = (216, 170, 140), (190, 140, 112)
SKIN_CHEEK = (243, 199, 164)
BROW, BROW_LT, LASH = (140, 100, 60), (168, 126, 80), (70, 46, 34)
WHITE, WHITE_SH = (238, 238, 234), (208, 208, 206)
IRIS, IRIS_DK, PUPIL = (96, 158, 230), (42, 98, 192), (26, 32, 56)
LIP_LINE, LIP = (196, 130, 116), (214, 156, 138)
# helmet palette
HB, HB_HI, HB_LO, HB_DK = (52, 92, 186), (74, 118, 210), (40, 72, 154), (26, 44, 104)
STRAP, STRAP_DK = (104, 66, 38), (78, 48, 28)

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


def rgba(c):
    return np.array(tuple(c) + (255,), np.uint8)


def flat(w, h, c):
    return np.tile(rgba(c), (h, w, 1))


def face_island():
    """The 15 x 16 head front (row 0 = top of the head, col 0 = the viewer's left), drawn in the style of
    Reinhard's face: even, clean skin, big two-row eyes under a dark lash line, a brow line, a single-pixel nose
    shadow and a short soft mouth; no noise, no relief. Rows 0-5 are under the crown; the opening (rows 6-15)
    narrows towards the chin (guards at |x| 3.25 / 3.0 / 2.5)."""
    w, h = round(2 * HX * FACE_PX), round((HY1 - HY0) * FACE_PX)
    a = flat(w, h, SKIN)
    a[:6] = rgba(SKIN_SH)
    L = {}

    def p(r, c, col):
        L[(r, c)] = col
    for c in range(1, 8):
        p(6, c, SKIN_SH2)          # soft shadow under the rim
    for c in range(2, 6):
        p(6, c, BROW)              # brows
    p(6, 2, BROW_LT)
    for c in range(3, 6):
        p(7, c, LASH)              # upper lash line
    p(8, 3, WHITE); p(8, 4, IRIS_DK); p(8, 5, IRIS_DK)   # eyes: white + iris (dark top, light bottom)
    p(9, 3, WHITE_SH); p(9, 4, IRIS); p(9, 5, IRIS)
    p(10, 3, SKIN_SH2); p(10, 4, SKIN_SH2)             # lower lid
    p(11, 7, SKIN_SH)              # nose shadow
    p(10, 7, SKIN_HI)              # nose bridge light
    for c in (6, 7):
        p(12, c, LIP_LINE)         # mouth (3 px with the mirror)
    p(13, 7, SKIN_SH2)             # under the lip
    for c in range(1, 4):
        p(13, c, SKIN_SH2)         # jaw shade
    for c in range(1, 8):
        p(14, c, SKIN_SH2 if c > 3 else SKIN_SH)
        p(15, c, SKIN_SH)          # under the chin
    for (r, c), col in L.items():
        a[r, c] = rgba(col)
        a[r, w - 1 - c] = rgba(col)
    return a


def shade(w, h, base, top=0.0, bottom=0.0, edge=None, edge_rows=()):
    """Flat colour with a vertical light ramp; optional rows replaced by an edge colour."""
    yy = (np.arange(h) + 0.5) / h
    k = 1.0 + top * (1 - yy) + bottom * yy
    rgb = np.clip(np.array(base, float)[None, :] * k[:, None], 0, 255).astype(np.uint8)
    a = np.zeros((h, w, 4), np.uint8)
    a[..., :3] = rgb[:, None, :]
    a[..., 3] = 255
    for r in edge_rows:
        a[r] = rgba(edge)
    return a


def faces_for(size, spec, px=2):
    """spec: face name -> callable(w, h) -> island, sized px texels per unit."""
    w, h, d = size
    dims = {'north': (w, h), 'south': (w, h), 'east': (d, h), 'west': (d, h), 'up': (w, d), 'down': (w, d)}
    out = {}
    for n, f in spec.items():
        fw, fh = dims[n]
        out[n] = f(tx2(fw, px), tx2(fh, px))
    return out


def tx2(n, px):
    return max(1, int(math.ceil(n * px - 1e-6)))


def helmet(kind):
    """Island painters for the helmet parts."""
    if kind == 'side':
        return lambda w, h: shade(w, h, HB, top=0.08, bottom=-0.12)
    if kind == 'front':
        return lambda w, h: shade(w, h, HB, top=0.1, bottom=-0.08, edge=HB_DK, edge_rows=(h - 1,))
    if kind == 'top':
        return lambda w, h: shade(w, h, HB_HI, top=0.0, bottom=-0.05)
    if kind == 'under':
        return lambda w, h: flat(w, h, HB_DK)
    if kind == 'inner':
        return lambda w, h: shade(w, h, HB_LO, top=-0.1, bottom=-0.25)
    if kind == 'rim':
        return lambda w, h: shade(w, h, HB_LO, edge=HB_HI, edge_rows=(0,))
    if kind == 'strap':
        return lambda w, h: shade(w, h, STRAP, bottom=-0.2)
    if kind == 'strap_dk':
        return lambda w, h: flat(w, h, STRAP_DK)
    raise ValueError(kind)


def octagon(atlas, x, z, y0, y1, ch, sides, up=None, down=None, front=None, drop=(0.0, 0.03, 0.06)):
    """Two crossed boxes and four 45-degree corner prisms (tops staggered by `drop`: no shared planes)."""
    out = []
    yA, yB, yP = y1 - drop[0], y1 - drop[1], y1 - drop[2]
    fa = {'east': sides, 'west': sides}
    if up:
        fa['up'] = up
    sa = [2 * x, yA - y0, 2 * (z - ch)]
    out.append(cube(atlas, [-x, y0, -(z - ch)], sa, faces_for(sa, fa)))
    fb = {'north': front or sides, 'south': sides}
    if up:
        fb['up'] = up
    if down:
        fb['down'] = down
    sb = [2 * (x - ch), yB - y0, 2 * z]
    out.append(cube(atlas, [-(x - ch), y0, -z], sb, faces_for(sb, fb)))
    side = ch * math.sqrt(2)
    for cx, cz in ((x - ch, z - ch), (-(x - ch), z - ch), (x - ch, -(z - ch)), (-(x - ch), -(z - ch))):
        fp = {n: sides for n in ('north', 'south', 'east', 'west')}
        if up:
            fp['up'] = up
        sp = [side, yP - y0, side]
        out.append(cube(atlas, [cx - side / 2, y0, cz - side / 2], sp, faces_for(sp, fp),
                        rotation=[0, 45, 0], pivot=[round(cx, 4), y0, round(cz, 4)]))
    return out


def body():
    g = load_geo('captain.geo.json')
    img = load_png('captain')
    geo = g['minecraft:geometry'][0]
    atlas = Atlas(img, used_v(geo))
    head = next(b for b in geo['bones'] if b['name'] == 'head')
    face = face_island()
    size = [2 * HX, HY1 - HY0, 2 * HX]
    skin = lambda w, h: shade(w, h, SKIN_DK, bottom=-0.1)  # noqa: E731  (only the neck line shows)
    f = faces_for(size, {'south': skin, 'east': skin, 'west': skin, 'up': lambda w, h: flat(w, h, SKIN_SH),
                         'down': lambda w, h: flat(w, h, SKIN_DK)})
    f['north'] = face
    cubes = [cube(atlas, [-HX, HY0, -HX], size, f)]
    head['cubes'] = cubes
    save_geo('captain.geo.json', g)
    save_png('captain', img)
    return len(cubes)


def moved(c, dx=0.0, dy=0.0, dz=0.0):
    c = json.loads(json.dumps(c))
    c['origin'] = [round(c['origin'][0] + dx, 4), round(c['origin'][1] + dy, 4), round(c['origin'][2] + dz, 4)]
    if 'pivot' in c:
        p = c['pivot']
        c['pivot'] = [round(p[0] + dx, 4), round(p[1] + dy, 4), round(p[2] + dz, 4)]
    return c


def gear():
    g = load_geo('captain_gear.geo.json')
    img = load_png('captain_gear')
    geo = g['minecraft:geometry'][0]
    atlas = Atlas(img, used_v(geo))
    head = next(b for b in geo['bones'] if b['name'] == 'head')
    old = head['cubes']
    out = []
    # crown: from the brow to the top of the head + T, octagonal
    out += octagon(atlas, O, O, BROW_Y, HY1 + T, CH, helmet('side'), up=helmet('top'), down=helmet('under'),
                   front=helmet('front'))
    # top cap and its ridge
    out += octagon(atlas, O - 0.55, O - 0.55, HY1 + T, HY1 + T + 0.3, 0.9, helmet('side'), up=helmet('top'))
    out.append(moved(old[2], dy=(HY1 + T + 0.3) - 32.9))
    # rim over the eyes
    rs = [2 * (GUARD_IN + 0.05), 0.35, 0.2]
    out.append(cube(atlas, [-(GUARD_IN + 0.05), BROW_Y - 0.2, -O - 0.1], rs,
                    faces_for(rs, {'north': helmet('rim'), 'up': helmet('top'), 'down': helmet('under'),
                                   'east': helmet('inner'), 'west': helmet('inner')})))
    # cowl over the back and the sides below the crown (octagonal back corners)
    y0 = HY0 + 0.02  # down to the jaw line (just above the torso top: no shared plane)
    s1 = [2 * O, BROW_Y - y0, (O - CH) - GUARD_Z]
    out.append(cube(atlas, [-O, y0, GUARD_Z], s1, faces_for(s1, {'east': helmet('side'), 'west': helmet('side'),
                                                                 'down': helmet('under')})))
    s2 = [2 * (O - CH), BROW_Y - y0, CH]
    out.append(cube(atlas, [-(O - CH), y0, O - CH], s2, faces_for(s2, {'south': helmet('side'), 'down': helmet('under')})))
    side = CH * math.sqrt(2)
    for sx in (1, -1):
        cx, cz = sx * (O - CH), O - CH
        sp = [side, BROW_Y - y0, side]
        out.append(cube(atlas, [cx - side / 2, y0, cz - side / 2], sp,
                        faces_for(sp, {n: helmet('side') for n in ('north', 'south', 'east', 'west')}),
                        rotation=[0, 45, 0], pivot=[round(cx, 4), y0, round(cz, 4)]))
    # cheek guards in three steps (eyes / mouth / chin): the opening narrows like a jaw
    for sx in (1, -1):
        inner = 'west' if sx > 0 else 'east'
        outer = 'east' if sx > 0 else 'west'
        for x_in, ya, yb, zf in ((GUARD_IN, MID_Y, BROW_Y, -O), (MID_IN, JAW_Y, MID_Y, -O + 0.03), (JAW_IN, y0, JAW_Y, -O + 0.06)):
            sz = [O - x_in, yb - ya, GUARD_Z - zf]
            x0 = x_in if sx > 0 else -O
            out.append(cube(atlas, [x0, ya, zf], sz, faces_for(sz, {'north': helmet('side'), inner: helmet('inner'),
                                                                     outer: helmet('side'), 'down': helmet('under')})))
    # thin strap under the chin
    ss = [2 * JAW_IN, 0.35, (-HX + 1.0) - (-HX - 0.1)]
    out.append(cube(atlas, [-JAW_IN, HY0 - 0.2, -HX - 0.1], ss,
                    faces_for(ss, {'north': helmet('strap'), 'up': helmet('strap'), 'down': helmet('strap_dk'),
                                   'east': helmet('strap'), 'west': helmet('strap')})))
    # BASE details onto the new surface: ear discs and wings (|x| in by 0.4), the A (z back by 0.4)
    shift = 4.4 - O
    for i in (8, 9, 10, 11, 12, 13):
        out.append(moved(old[i], dx=shift))
    for i in (17, 18, 19, 20, 21, 22):
        out.append(moved(old[i], dx=-shift))
    for i in (25, 26, 27):
        out.append(moved(old[i], dy=-0.05, dz=shift))
    head['cubes'] = out
    save_geo('captain_gear.geo.json', g)
    save_png('captain_gear', img)
    return len(out)


def main():
    import remove_fingers  # the body comes from BASE: drop its finger / thumb cubes again (see remove_fingers.py)
    nb = body()
    remove_fingers.run('captain/captain.geo.json')
    ng = gear()
    print('captain head pass: head %d cubes (face %dx%d), helmet %d cubes' % (nb, round(2 * HX * FACE_PX),
                                                                            round((HY1 - HY0) * FACE_PX), ng))


if __name__ == '__main__':
    main()
