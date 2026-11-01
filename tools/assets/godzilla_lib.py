"""Shared kit of the Godzilla city-catastrophe kaiju (tools/assets/godzilla.py). Own work, CC0.

Layout: the body frame of GodzillaRules (fractions of HEIGHT: x = the kaiju's left, y = up, z = forward) maps to
geo pixels with F = 192 px per HEIGHT (MODEL_HEIGHT 12 blocks; the renderer scales by HEIGHT / MODEL_HEIGHT).
Geo convention (BakedGeoModel / jinwoo_geo): front -Z, the character's left +X, Bedrock degrees.

Texturing: a small shared swatch atlas (skin, hide, belly, plate, mouth, teeth, claw, eye, gill) instead of one
unique atlas cell per cube face: every face samples a sub-rectangle of its material swatch at a seeded offset.
All three LODs use the same 512x512 atlas and its glow twin, which keeps the PNGs small and the look identical.
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from jinwoo_geo import Model, Mat, Clip, write_anims, write_json, write_png, hexc, RES, BIN, ROOT  # noqa: E402,F401

F = 192.0  # geo px per HEIGHT

# ---- GodzillaRules mirror (GodzillaAssetsTest compares the geo against the Java constants) --------------------
NECK_PIVOT = (0.0, 0.74, 0.14)
HEAD_OFFSET = (0.0, 0.08, 0.13)
MOUTH_OFFSET = (0.0, 0.045, 0.21)
SHOULDER_L = (0.15, 0.58, 0.13)
TAIL_ROOT = (0.0, 0.33, -0.17)
TAIL_LENGTH = (0.21, 0.20, 0.20, 0.19, 0.19, 0.18, 0.18)
TAIL_PITCH = (8.0, 12.0, 15.0, 15.0, 12.0, 8.0, 3.0)
TAIL_SIZE = ((0.19, 0.16), (0.16, 0.14), (0.14, 0.12), (0.12, 0.10), (0.10, 0.085), (0.08, 0.07), (0.065, 0.055))
LEG_REST = (0.12, 0.16, -0.01)
FOOT_SWING = 0.085  # GodzillaRules.footForward amplitude
FALL_PIVOT = 0.11


def B(l, u, f):
    """Body-frame fractions -> geo px (x left, y up, z back)."""
    return np.array([l * F, u * F, -f * F], float)


def tail_joints():
    """Rest joints of the tail chain (geo px): joint k starts segment k; 8 points (root .. tip)."""
    p = B(*TAIL_ROOT)
    out = [p.copy()]
    for k in range(7):
        a = math.radians(TAIL_PITCH[k])
        L = TAIL_LENGTH[k] * F
        p = p + np.array([0.0, -math.sin(a) * L, math.cos(a) * L])
        out.append(p.copy())
    return out


# ---- swatch atlas ------------------------------------------------------------------------------------------
ATLAS = 512
DENSITY = 2.0  # texels per geo px on the swatches
SWATCH = {     # name: (x, y, w, h)
    'skin': (0, 0, 256, 256),
    'hide': (256, 0, 256, 256),
    'belly': (0, 256, 128, 128),
    'plate': (128, 256, 128, 256),
    'mouth': (256, 256, 64, 64),
    'throat': (320, 256, 64, 64),
    'teeth': (384, 256, 32, 32),
    'claw': (416, 256, 32, 32),
    'eye': (448, 256, 16, 16),
    'gill': (256, 320, 64, 64),
    'scar': (320, 320, 64, 64),
    'lip': (384, 288, 64, 32),
    'sole': (448, 288, 64, 64),
}


def _hash3(ix, iy, iz, seed):
    h = np.sin(ix * 127.1 + iy * 311.7 + iz * 74.7 + seed * 19.19) * 43758.5453
    return h - np.floor(h)


def cellular(x, y, seed, scale):
    """2-D Worley noise on a texel grid: (F1, F2, cell id hash) for points (x, y) / scale."""
    px, py = x / scale, y / scale
    cx, cy = np.floor(px), np.floor(py)
    f1 = np.full(px.shape, 9.0)
    f2 = np.full(px.shape, 9.0)
    idh = np.zeros(px.shape)
    for ox in (-1, 0, 1):
        for oy in (-1, 0, 1):
            gx, gy = cx + ox, cy + oy
            fx = gx + _hash3(gx, gy, 0, seed)
            fy = gy + _hash3(gx, gy, 1, seed)
            d = np.sqrt((px - fx) ** 2 + (py - fy) ** 2)
            closer = d < f1
            f2 = np.where(closer, f1, np.minimum(f2, d))
            idh = np.where(closer, _hash3(gx, gy, 2, seed), idh)
            f1 = np.where(closer, d, f1)
    return f1, f2, idh


def value_noise(w, h, cell, seed):
    rs = np.random.RandomState(seed)
    gw, gh = w // cell + 2, h // cell + 2
    g = rs.uniform(-1, 1, (gh, gw))
    y, x = np.mgrid[0:h, 0:w] / float(cell)
    x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx
    b = g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


def fbm(w, h, seed, cells=(48, 20, 8), amps=(1.0, 0.5, 0.25)):
    return sum(a * value_noise(w, h, c, seed + i * 7) for i, (c, a) in enumerate(zip(cells, amps))) / sum(amps)


def _rgb(hexs):
    return np.array(hexc(hexs)[:3], float)


def paint_scales(w, h, seed, base, crack, light, scale=7.0, ridge=0.0, blotch=0.12, speck=0.03):
    """Craggy reptile hide: Worley scales with dark creases, a lit upper-left bevel per scale, blotches, specks."""
    y, x = np.mgrid[0:h, 0:w].astype(float)
    f1, f2, idh = cellular(x, y * 1.15, seed, scale)
    edge = np.clip((f2 - f1) / 0.22, 0, 1)            # 0 at the crease
    # bevel: brighter towards the upper left of each scale (fake light from the top)
    gy = np.gradient(f1, axis=0)
    gx = np.gradient(f1, axis=1)
    bevel = np.clip(-(gx * 0.6 + gy * 0.9) * 6.0, -1, 1)
    big = fbm(w, h, seed + 3, cells=(64, 24, 6), amps=(1.0, 0.45, 0.2))
    rgb = np.tile(_rgb(base), (h, w, 1))
    rgb *= (1.0 + blotch * big)[..., None]
    rgb *= (0.92 + 0.16 * idh)[..., None]               # per-scale value
    rgb += (bevel * 14.0)[..., None] * _rgb(light) / 255.0
    crease = (1.0 - edge) ** 1.6
    rgb = rgb * (1 - crease[..., None] * 0.85) + _rgb(crack) * crease[..., None] * 0.85
    if ridge:
        bumps = np.clip(1.0 - f1 / 0.55, 0, 1) ** 2 * (idh > 0.62)
        rgb += (bumps * ridge)[..., None] * _rgb(light) / 255.0 * 28.0
    rs = np.random.RandomState(seed + 9)
    sp = rs.uniform(0, 1, (h, w)) < speck
    rgb[sp] = rgb[sp] * 0.6 + _rgb(light) * 0.4
    return rgb


def paint_belly(w, h, seed):
    """Belly scutes: horizontal bands of lighter, smoother plates with dark seams and fine wrinkles."""
    y, x = np.mgrid[0:h, 0:w].astype(float)
    band = 14.0
    v = (y / band) % 1.0
    row = np.floor(y / band)
    jitter = np.sin(row * 12.9898 + seed) * 0.5 + 0.5
    seam = np.clip(np.minimum(v, 1 - v) / 0.12, 0, 1)
    vseam = np.clip(np.abs(((x + jitter * 23.0) / 31.0) % 1.0 - 0.5) / 0.04, 0, 1)
    base = paint_scales(w, h, seed, '#4a4744', '#1f1d1c', '#8d877d', scale=5.5, blotch=0.1, speck=0.02)
    rgb = base * (0.92 + 0.12 * (1 - v))[..., None]
    shade = np.minimum(seam, 0.6 + 0.4 * vseam)
    rgb = rgb * (0.45 + 0.55 * shade[..., None])
    return rgb


def paint_plate(w, h, seed):
    """Dorsal plate: pale bone-grey with dark roots, vertical ridges and grooves, scorched tips; plus its glow:
    blue in the grooves, cyan on the ridges, a white-hot spine line and dimmer roots (the glow climbs the plate)."""
    y, x = np.mgrid[0:h, 0:w].astype(float)
    u, v = x / w, y / h                               # v: 0 tip .. 1 root
    ridges = 0.5 + 0.5 * np.sin(u * math.pi * 9.0 + 2.2 * np.sin(v * 5.0 + seed))
    big = fbm(w, h, seed + 1, cells=(40, 14, 5), amps=(1.0, 0.5, 0.3))
    root = np.clip((v - 0.62) / 0.3, 0, 1) ** 1.3
    tip = np.clip((0.12 - v) / 0.12, 0, 1)
    pale = _rgb('#b9b2a2') * (0.9 + 0.12 * big)[..., None]
    rgb = pale * (0.72 + 0.32 * ridges)[..., None]
    rgb = rgb * (1 - root[..., None]) + _rgb('#2c2b2d') * root[..., None]
    rgb = rgb * (1 - 0.35 * tip[..., None]) + _rgb('#6d675e') * 0.35 * tip[..., None]
    f1, f2, idh = cellular(x, y, seed + 5, 6.0)
    pits = np.clip(1 - (f2 - f1) / 0.12, 0, 1) ** 2
    rgb *= (1 - 0.35 * pits)[..., None]
    glow = np.zeros((h, w, 3))
    core = np.exp(-((u - 0.5) / 0.08) ** 2)
    glow += np.array([28, 110, 255.0]) * (0.55 + 0.45 * (1 - ridges))[..., None]
    glow += np.array([90, 200, 255.0]) * (ridges ** 3)[..., None] * 0.7
    glow += np.array([210, 245, 255.0]) * core[..., None] * 0.65
    glow *= (1.0 - 0.75 * root)[..., None]
    glow *= (0.8 + 0.2 * big)[..., None]
    return rgb, glow


def paint_atlas(seed=11):
    base = np.zeros((ATLAS, ATLAS, 4), float)
    glow = np.zeros((ATLAS, ATLAS, 4), float)

    def put(name, rgb, g=None):
        x, y, w, h = SWATCH[name]
        base[y:y + h, x:x + w, :3] = rgb
        base[y:y + h, x:x + w, 3] = 255
        if g is not None:
            glow[y:y + h, x:x + w, :3] = g
            glow[y:y + h, x:x + w, 3] = 255

    put('skin', paint_scales(256, 256, seed, '#2e2e31', '#121214', '#7c7a78', scale=7.5, ridge=0.6))
    put('hide', paint_scales(256, 256, seed + 20, '#262628', '#0d0d0f', '#6a6866', scale=9.5, ridge=1.0, blotch=0.16))
    put('belly', paint_belly(128, 128, seed + 40))
    prgb, pglow = paint_plate(128, 256, seed + 60)
    put('plate', prgb, pglow)
    # mouth: dark red-brown gums with wet highlights; throat: deep red that glows blue-white in the charge
    y, x = np.mgrid[0:64, 0:64].astype(float)
    n = fbm(64, 64, seed + 80, cells=(16, 6, 3))
    put('mouth', np.stack([70 + 30 * n, 22 + 10 * n, 24 + 8 * n], -1))
    r = np.sqrt((x - 32) ** 2 + (y - 32) ** 2) / 32.0
    put('throat', np.stack([60 + 20 * n, 16 + 6 * n, 20 + 6 * n], -1),
        np.stack([60 + 160 * np.clip(1 - r, 0, 1), 170 + 80 * np.clip(1 - r, 0, 1), np.full_like(r, 255)], -1) * np.clip(1.2 - r, 0, 1)[..., None])
    y, x = np.mgrid[0:32, 0:32].astype(float)
    put('teeth', np.stack([214 - 30 * y / 32, 205 - 34 * y / 32, 184 - 40 * y / 32], -1))
    put('claw', np.stack([38 + 50 * (1 - y / 32), 34 + 44 * (1 - y / 32), 31 + 36 * (1 - y / 32)], -1))
    y, x = np.mgrid[0:16, 0:16].astype(float)
    eye = np.stack([np.full_like(x, 214), np.full_like(x, 150), np.full_like(x, 48)], -1)
    eye *= (0.75 + 0.25 * np.clip(1 - np.abs(y - 6) / 8, 0, 1))[..., None]
    slit = np.abs(x - 8) < 1.2
    eye[slit] = (18, 12, 8)
    eye[(y < 3)] *= 0.55
    put('eye', eye, np.stack([np.full_like(x, 90), np.full_like(x, 52), np.full_like(x, 10)], -1) * (~slit)[..., None])
    y, x = np.mgrid[0:64, 0:64].astype(float)
    gill = paint_scales(64, 64, seed + 90, '#2a2a2c', '#0b0b0c', '#6a6866', scale=5.0)
    slits = (np.abs(((y + 4) / 12.0) % 1.0 - 0.5) < 0.12)
    gill[slits] = gill[slits] * 0.25 + np.array([60, 20, 22]) * 0.75
    put('gill', gill)
    put('scar', paint_scales(64, 64, seed + 100, '#3a3534', '#151313', '#8a7f78', scale=4.0, blotch=0.2))
    y, x = np.mgrid[0:32, 0:64].astype(float)
    put('lip', paint_scales(64, 32, seed + 110, '#232325', '#0c0c0d', '#5d5b5a', scale=4.5))
    put('sole', paint_scales(64, 64, seed + 120, '#3b3936', '#1a1918', '#77726a', scale=4.0, blotch=0.2))
    base = np.clip(base, 0, 255)
    # quantise a little: keeps the PNG small without visible banding
    base[..., :3] = np.round(base[..., :3] / 3.0) * 3.0
    glow = np.clip(glow, 0, 255)
    glow[..., :3] = np.round(glow[..., :3] / 4.0) * 4.0
    return base.astype(np.uint8), glow.astype(np.uint8)


# ---- swatch-mapped model -----------------------------------------------------------------------------------
class KaijuModel(Model):
    """Model whose faces sample the shared swatch atlas. Cube tag: {'mat': swatch, 'u': (u0, u1), 'v': (v0, v1)}
    (u / v ranges as fractions of the swatch, used by the plates so the root band sits at the plate base)."""

    def __init__(self, identifier, seed=7):
        super().__init__(identifier, density=DENSITY, width=ATLAS, seed=seed)
        self.height = ATLAS

    def pack(self):
        rng = np.random.RandomState(1234)
        for b in self.bones:
            for c in b.cubes:
                tag = c.tag or {}
                sw = tag.get('mat', 'skin')
                sx, sy, sw_w, sw_h = SWATCH[sw]
                for f in c.faces:
                    w, h = self._face_px(c, f)
                    if 'u' in tag or 'v' in tag:
                        u0, u1 = tag.get('u', (0.0, 1.0))
                        v0, v1 = tag.get('v', (0.0, 1.0))
                        if f in ('up', 'down'):
                            v0, v1 = (v0, v0 + 0.04) if f == 'up' else (v1 - 0.04, v1)
                        x = sx + int(round(u0 * sw_w))
                        y = sy + int(round((1.0 - v1) * sw_h))
                        ww = max(1, int(round((u1 - u0) * sw_w)))
                        hh = max(1, int(round((v1 - v0) * sw_h)))
                        c.uv[f] = (x, y, ww, hh)
                        continue
                    k = min(1.0, sw_w / max(w, 1), sw_h / max(h, 1))
                    ww, hh = max(1, int(w * k)), max(1, int(h * k))
                    x = sx + (rng.randint(0, sw_w - ww + 1) if ww < sw_w else 0)
                    y = sy + (rng.randint(0, sw_h - hh + 1) if hh < sw_h else 0)
                    c.uv[f] = (x, y, ww, hh)
        return self.height


def r3(v):
    return [round(float(a), 3) for a in v]


def orient(local, d):
    """Bedrock cube rotation (deg) turning a box's local long axis ('down', 'up', 'back', 'fwd') onto the
    direction d, in the BakedGeoModel convention (effective rotation Rz(-rz) Ry(ry) Rx(-rx), applied X, Y, Z)."""
    d = np.asarray(d, float)
    d = d / (np.linalg.norm(d) + 1e-12)
    dx, dy, dz = d
    if local == 'down':
        tx = math.asin(max(-1.0, min(1.0, -dz)))
        tz = math.atan2(dx, -dy)
        return [-math.degrees(tx), 0.0, -math.degrees(tz)]
    if local == 'up':
        tx = math.asin(max(-1.0, min(1.0, dz)))
        tz = math.atan2(-dx, dy)
        return [-math.degrees(tx), 0.0, -math.degrees(tz)]
    if local == 'back':
        tx = math.asin(max(-1.0, min(1.0, -dy)))
        ty = math.atan2(dx, dz)
        return [-math.degrees(tx), math.degrees(ty), 0.0]
    tx = math.asin(max(-1.0, min(1.0, dy)))
    ty = math.atan2(-dx, -dz)
    return [-math.degrees(tx), math.degrees(ty), 0.0]


def box(m, bone, centre, size, mat='skin', rot=None, inflate=0.0, tag=None, faces=None):
    """Axis-aligned box (geo px) about its centre, optionally rotated about the centre."""
    c = np.asarray(centre, float)
    s = np.asarray(size, float)
    t = dict(tag or {})
    t.setdefault('mat', mat)
    kw = {'tag': t}
    if inflate:
        kw['inflate'] = inflate
    if faces:
        kw['faces'] = faces
    if rot is not None and any(abs(a) > 1e-3 for a in rot):
        kw['rotation'] = [round(a, 2) for a in rot]
        kw['pivot'] = r3(c)
    return m.cube(bone, r3(c - s / 2.0), r3(s), None, **kw)


def segment(m, bone, a, b, w, h, mat='skin', axis='down', rounded=True, ext=0.18, taper=None, tag=None):
    """Box (or an octagonal pair of boxes) from point a to point b: cross-section w (x) by h, long axis turned
    onto b - a. ext: overlap added at both ends (share of the smaller cross-section)."""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    d = b - a
    L = float(np.linalg.norm(d)) + 2.0 * ext * min(w, h)
    c = (a + b) / 2.0
    rot = orient(axis, d)
    if axis in ('down', 'up'):
        sizes = [(w, L, h * 0.72), (w * 0.72, L, h)] if rounded else [(w, L, h)]
    else:
        sizes = [(w, h * 0.72, L), (w * 0.72, h, L)] if rounded else [(w, h, L)]
    return [box(m, bone, c, s, mat, rot, tag=tag) for s in sizes]


def loft(m, bone, pts, mat='skin', axis='down', rounded=True, ext=0.18, mats=None):
    """Chain of segments through cross-sections [(point, w, h)]; each piece uses the mean section."""
    out = []
    for i in range(len(pts) - 1):
        (a, wa, ha), (b, wb, hb) = pts[i], pts[i + 1]
        mm = mats[i] if mats else mat
        out += segment(m, bone, a, b, (wa + wb) / 2.0, (ha + hb) / 2.0, mm, axis, rounded, ext)
    return out
