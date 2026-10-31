"""Shared kit for the Chitauri invasion assets (soldier, chariot, Leviathan). Own work, CC0.

Builds on jinwoo_geo (Bedrock geo + auto-packed procedural atlas + clips). Adds the Chitauri
palette and materials (leathery grey skin, worn gold-bronze armour, dark alloy, ribbed hoses,
blue-white energy), forward kinematics over a geo pose (same maths as tools/preview_geo.py and
BakedGeoModel: Bedrock degrees, pose = rest + clip), a small two-bone reach solver used to put
the off hand on the rifle in every clip, and posed previews (front / side / back) for review.
Units are model pixels, front -Z, the character's right side -X.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
from jinwoo_geo import Mat, Model, Clip, write_anims, put, hexc, RES, BIN, ROOT  # noqa: E402,F401
import preview_geo as pg  # noqa: E402

# ---- palette --------------------------------------------------------------------------------------------
SKIN = '#4b4642'
SKIN_DARK = '#34302d'
GOLD = '#9c7b45'
GOLD_DARK = '#6b5130'
ALLOY = '#2c2e34'
ALLOY_LIGHT = '#4a4d55'
HOSE = '#1f1e1d'
BONE = '#b9ad93'
ENERGY = (150, 205, 255)
ENERGY_HOT = (225, 242, 255)


def _scratches(face, rgb, rs, count, light=1.22):
    h, w = rgb.shape[:2]
    for _ in range(count):
        if w < 3 or h < 3:
            return
        x, y = rs.randint(0, w), rs.randint(0, h)
        n = rs.randint(2, max(3, min(w, h)))
        dx, dy = (1, 0) if rs.rand() < 0.5 else (1, 1)
        for i in range(n):
            xx, yy = x + i * dx, y + i * dy
            if 0 <= xx < w and 0 <= yy < h:
                rgb[yy, xx] *= light


def armour_fn(seams=True, rivets=True, scratches=3):
    """Worn gold-bronze plate: lit top band, darker lower edge, seam lines, rivets, scratches."""
    def fn(face, rgb, glow, rs):
        h, w = rgb.shape[:2]
        u, v = face.grid()
        if face.name not in ('up', 'down'):
            rgb *= (1.10 - 0.22 * v)[..., None]
        if seams and w >= 6 and h >= 4:
            for _ in range(rs.randint(0, 2)):
                x = rs.randint(2, w - 2)
                rgb[:, x] *= 0.62
        if rivets and w >= 5 and h >= 5:
            for (yy, xx) in ((1, 1), (1, w - 2), (h - 2, 1), (h - 2, w - 2)):
                if rs.rand() < 0.7:
                    rgb[yy, xx] = rgb[yy, xx] * 0.5 + np.array([210, 180, 120]) * 0.5
        _scratches(face, rgb, rs, scratches)
    return fn


def skin_fn(face, rgb, glow, rs):
    """Leathery hide: horizontal wrinkles, mottling, darker crevices."""
    h, w = rgb.shape[:2]
    for _ in range(max(1, h // 3)):
        y = rs.randint(0, h)
        x0 = rs.randint(0, max(1, w))
        ln = rs.randint(1, max(2, w // 2 + 1))
        rgb[y, x0:x0 + ln] *= 0.78
    blot = rs.uniform(0.9, 1.08, (max(1, h // 3 + 1), max(1, w // 3 + 1)))
    rgb *= np.kron(blot, np.ones((3, 3)))[:h, :w, None]


def hose_fn(face, rgb, glow, rs):
    """Ribbed hose: stripes across its length."""
    h, w = rgb.shape[:2]
    if face.name in ('up', 'down'):
        return
    for y in range(h):
        if y % 2 == 0:
            rgb[y, :] *= 1.25


def alloy_fn(lights=0, colour=ENERGY):
    """Dark alloy with fine panel lines; optional small energy lights on the side faces."""
    def fn(face, rgb, glow, rs):
        h, w = rgb.shape[:2]
        if w >= 6:
            rgb[:, w // 2] *= 0.75
        _scratches(face, rgb, rs, 1, 1.35)
        if lights and glow is not None and face.name not in ('up', 'down') and w >= 3 and h >= 3:
            for _ in range(lights):
                x, y = rs.randint(1, w - 1), rs.randint(1, h - 1)
                glow[y, x] = (*colour, 255)
                rgb[y, x] = colour
    return fn


def energy_glow(colour=ENERGY, hot=ENERGY_HOT, core=0.45):
    """Glowing cell: hot core fading to the energy colour at the rim (base and glow layers)."""
    def fn(face, rgb, glow, rs):
        u, v = face.grid()
        d = np.maximum(abs(u - 0.5), abs(v - 0.5)) * 2.0
        t = np.clip((d - core) / max(1e-3, 1.0 - core), 0, 1)[..., None]
        col = np.array(hot, float) * (1 - t) + np.array(colour, float) * t
        rgb[...] = col
        if glow is not None:
            glow[..., :3] = col
            glow[..., 3] = 255
    return fn


def eye_fn(face, rgb, glow, rs):
    rgb[...] = (18, 16, 22)
    if glow is not None and face.name == 'north':
        h, w = rgb.shape[:2]
        glow[..., :3] = (90, 120, 255)
        glow[..., 3] = 120
        glow[h // 2:, :, 3] = 60


def mats():
    return {
        'skin': Mat(SKIN, noise=7, edge=0.14, fn=skin_fn),
        'skin_dark': Mat(SKIN_DARK, noise=6, edge=0.12, fn=skin_fn),
        'gold': Mat(GOLD, noise=9, edge=0.24, top=1.18, bottom=0.7, fn=armour_fn()),
        'gold_plain': Mat(GOLD, noise=7, edge=0.2, top=1.15, bottom=0.72, fn=armour_fn(seams=False, rivets=False, scratches=1)),
        'gold_dark': Mat(GOLD_DARK, noise=7, edge=0.2, fn=armour_fn(seams=False)),
        'alloy': Mat(ALLOY, noise=5, edge=0.22, fn=alloy_fn()),
        'alloy_lit': Mat(ALLOY, noise=5, edge=0.22, fn=alloy_fn(lights=2)),
        'alloy_light': Mat(ALLOY_LIGHT, noise=6, edge=0.2, fn=alloy_fn()),
        'hose': Mat(HOSE, noise=4, edge=0.1, fn=hose_fn),
        'bone': Mat(BONE, noise=10, edge=0.2),
        'eye': Mat('#121016', noise=0, edge=0, fn=eye_fn),
        'energy': Mat('#9ccdff', noise=0, edge=0, fn=energy_glow()),
        'energy_soft': Mat('#7fb6ff', noise=0, edge=0, fn=energy_glow(core=0.15)),
    }


# ---- forward kinematics ------------------------------------------------------------------------------------
def bones_of(geo):
    return {b['name']: b for b in geo['minecraft:geometry'][0]['bones']}


def sample_clip(clip, t):
    """Pose of a jinwoo_geo Clip at t seconds (linear between keys; enough for reach solving and previews)."""
    pose = {}
    chans = {}
    for tt, p in clip.frames:
        for bone, v in p.items():
            spec = v if isinstance(v, dict) else {'rot': v}
            for ch in ('rot', 'pos', 'scale'):
                if ch in spec:
                    chans.setdefault((bone, ch), []).append((tt, spec[ch]))
    for (bone, ch), keys in chans.items():
        keys.sort(key=lambda k: k[0])
        if t <= keys[0][0]:
            val = keys[0][1]
        elif t >= keys[-1][0]:
            val = keys[-1][1]
        else:
            for (t0, a), (t1, b) in zip(keys, keys[1:]):
                if t0 <= t <= t1:
                    f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                    val = [x + (y - x) * f for x, y in zip(a, b)]
                    break
        pose.setdefault(bone, {})[ch] = list(val)
    return pose


def matrices(geo, pose=None):
    """Bone name -> 4x4 model matrix (Bedrock space) under a pose {bone: {'rot': [..], 'pos': [..]}}."""
    pose = pose or {}
    bones = bones_of(geo)
    cache = {}

    def m_of(name):
        if name in cache:
            return cache[name]
        b = bones[name]
        parent = b.get('parent')
        m = m_of(parent) if parent in bones else pg.IDENTITY
        p = pose.get(name, {})
        if 'pos' in p:
            px, py, pz = p['pos']
            m = pg.mat_mul(m, pg.translate(px, py, pz))
        rest = b.get('rotation') or [0, 0, 0]
        rot = [r + a for r, a in zip(rest, p.get('rot', [0, 0, 0]))]
        m = pg.mat_mul(m, pg.rotation(b.get('pivot', [0, 0, 0]), rot))
        if 'scale' in p:
            sx, sy, sz = p['scale']
            px, py, pz = b.get('pivot', [0, 0, 0])
            m = pg.mat_mul(m, pg.translate(px, py, pz))
            m = pg.mat_mul(m, [[sx, 0, 0, 0], [0, sy, 0, 0], [0, 0, sz, 0], [0, 0, 0, 1]])
            m = pg.mat_mul(m, pg.translate(-px, -py, -pz))
        cache[name] = m
        return m

    return {name: m_of(name) for name in bones}


def point(geo, pose, bone, local):
    return pg.apply(matrices(geo, pose)[bone], local)


def reach(geo, pose, chain, end_bone, end_local, target, iters=160, step0=24.0):
    """Coordinate search over (shoulder rx, ry, rz, elbow rx) so end_local of end_bone meets target.
    Returns the pose dict entries for the chain (merged into a copy of pose)."""
    shoulder, elbow = chain
    p = json.loads(json.dumps(pose))
    p.setdefault(shoulder, {}).setdefault('rot', [0, 0, 0])
    p.setdefault(elbow, {}).setdefault('rot', [0, 0, 0])
    params = [(shoulder, 0), (shoulder, 1), (shoulder, 2), (elbow, 0)]

    def err(q):
        e = point(geo, q, end_bone, end_local)
        return sum((a - b) ** 2 for a, b in zip(e, target))

    best = err(p)
    step = step0
    for _ in range(iters):
        improved = False
        for bone, axis in params:
            for d in (step, -step):
                q = json.loads(json.dumps(p))
                q[bone]['rot'][axis] += d
                if bone == elbow and axis == 0 and not (-150 <= q[bone]['rot'][0] <= 5):
                    continue
                e = err(q)
                if e < best:
                    best, p, improved = e, q, True
        if not improved:
            step *= 0.6
            if step < 0.05:
                break
    return p, math.sqrt(best)


# ---- previews ----------------------------------------------------------------------------------------------
def posed_quads(geo, pose):
    g = geo['minecraft:geometry'][0]
    desc = g.get('description', {})
    tw, th = desc.get('texture_width', 16), desc.get('texture_height', 16)
    ms = matrices(geo, pose)
    quads = []
    for b in g['bones']:
        m = ms[b['name']]
        for cube in b.get('cubes', []):
            cm = pg.mat_mul(m, pg.rotation(cube.get('pivot', [0, 0, 0]), cube.get('rotation')))
            for corners, uv in pg.cube_quads(cube):
                quads.append(([pg.apply(cm, c) for c in corners], uv))
    return quads, tw, th


def preview(out, items, views=('front', 'side', 'back'), scale=6, bg=(40, 44, 52, 255)):
    """items: [(geo_dict, pose_dict, tex_path, glow_path)] drawn together; one panel per view."""
    loaded = []
    for geo, pose, tex, glow in items:
        quads, tw, th = posed_quads(geo, pose)
        t = Image.open(tex).convert('RGBA') if tex else Image.new('RGBA', (int(tw), int(th)), (180, 180, 190, 255))
        gl = Image.open(glow).convert('RGBA') if glow else None
        loaded.append((quads, tw, th, t, gl))
    panels = []
    for name in views:
        view = pg.VIEWS[name]
        pts = [view(c) for quads, *_ in loaded for corners, _ in quads for c in corners] or [(0, 0, 0)]
        minx, maxx = min(p[0] for p in pts), max(p[0] for p in pts)
        miny, maxy = min(p[1] for p in pts), max(p[1] for p in pts)
        w = int((maxx - minx) * scale) + 2 * scale
        h = int((maxy - miny) * scale) + 2 * scale
        img = Image.new('RGBA', (max(w, 8), max(h, 8)), bg)
        zbuf = [[-1e9] * img.size[0] for _ in range(img.size[1])]
        ox, oy = scale - minx * scale, scale + maxy * scale
        for quads, tw, th, tex, glow in loaded:
            pg.raster(img, zbuf, quads, tex, tw, th, view, scale, ox, oy)
        for quads, tw, th, tex, glow in loaded:
            if glow is not None:
                pg.raster(img, zbuf, quads, glow, tw, th, view, scale, ox, oy, additive=True)
        panels.append(img)
    out_img = Image.new('RGBA', (sum(p.size[0] for p in panels) + 4 * (len(panels) - 1), max(p.size[1] for p in panels)), (20, 20, 24, 255))
    x = 0
    for p in panels:
        out_img.paste(p, (x, 0))
        x += p.size[0] + 4
    os.makedirs(os.path.dirname(out) or '.', exist_ok=True)
    out_img.save(out)
    return out_img


def sheet(out, images, cols=4, bg=(20, 20, 24, 255)):
    """Pastes preview images into a grid."""
    ims = [Image.open(i) if isinstance(i, str) else i for i in images]
    cw = max(i.size[0] for i in ims)
    ch = max(i.size[1] for i in ims)
    rows = (len(ims) + cols - 1) // cols
    s = Image.new('RGBA', (cw * min(cols, len(ims)), ch * rows), bg)
    for k, im in enumerate(ims):
        s.paste(im, ((k % cols) * cw, (k // cols) * ch))
    s.save(out)
    return s
