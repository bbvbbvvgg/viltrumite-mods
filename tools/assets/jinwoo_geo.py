"""Shared builder for the Jinwoo hero assets (Sung Jinwoo + shadow army). Own work, CC0.

Builds Bedrock geo models (format 1.12.0, per-face UV) with an auto-packed texture atlas
painted procedurally (base + glow layers), and Bedrock animation files from pose keyframes.
Coordinate convention (matches BakedGeoModel / GeckoLib): units are model pixels (1/16 block),
front of the body is -Z, the right side of the character is -X.
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'viltrumitecore', 'src', 'main', 'resources', 'assets', 'viltrumitecore')
BIN = os.path.join(ROOT, 'viltrumitecore', 'src', 'main', 'binassets', 'assets', 'viltrumitecore')
SIDE_FACES = ('north', 'south', 'east', 'west')


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def r1(v):
    v = round(float(v), 3)
    return int(v) if v == int(v) else v


class Face:
    """Painting context of one cube face: pixel size and pixel -> model coordinate mapping."""

    def __init__(self, cube, name, w, h):
        self.cube, self.name, self.w, self.h = cube, name, w, h
        ox, oy, oz = cube.origin
        sx, sy, sz = cube.size
        self.ox, self.oy, self.oz, self.sx, self.sy, self.sz = ox, oy, oz, sx, sy, sz

    def grid(self):
        """Arrays (u, v) in 0..1 for each pixel centre: u left->right as seen from outside, v top->bottom."""
        u = (np.arange(self.w) + 0.5) / self.w
        v = (np.arange(self.h) + 0.5) / self.h
        return np.meshgrid(u, v)

    def world(self):
        """Per-pixel model coordinates (x, y, z) of the face (side faces exact, caps approximate)."""
        u, v = self.grid()
        ox, oy, oz, sx, sy, sz = self.ox, self.oy, self.oz, self.sx, self.sy, self.sz
        n = self.name
        if n == 'north':
            return ox + u * sx, oy + sy - v * sy, np.full_like(u, oz)
        if n == 'south':
            return ox + sx - u * sx, oy + sy - v * sy, np.full_like(u, oz + sz)
        if n == 'east':
            return np.full_like(u, ox + sx), oy + sy - v * sy, oz + u * sz
        if n == 'west':
            return np.full_like(u, ox), oy + sy - v * sy, oz + sz - u * sz
        if n == 'up':
            return ox + u * sx, np.full_like(u, oy + sy), oz + sz - v * sz
        return ox + u * sx, np.full_like(u, oy), oz + v * sz


class Cube:
    def __init__(self, bone, origin, size, mat, inflate=0.0, rotation=None, pivot=None, faces=None, tag=None):
        self.bone, self.origin, self.size, self.mat = bone, list(origin), list(size), mat
        self.inflate, self.rotation, self.pivot = inflate, rotation, pivot
        self.faces = faces or ('north', 'south', 'east', 'west', 'up', 'down')
        self.tag = tag
        self.uv = {}


class Bone:
    def __init__(self, name, parent, pivot, rotation=None):
        self.name, self.parent, self.pivot, self.rotation = name, parent, list(pivot), rotation
        self.cubes = []


class Model:
    def __init__(self, identifier, density=2, width=256, seed=7):
        self.identifier, self.density, self.width = identifier, density, width
        self.bones, self.by_name = [], {}
        self.rng = random.Random(seed)

    def bone(self, name, parent, pivot, rotation=None):
        b = Bone(name, parent, pivot, rotation)
        self.bones.append(b)
        self.by_name[name] = b
        return b

    def cube(self, bone, origin, size, mat, **kw):
        c = Cube(bone, origin, size, mat, **kw)
        self.by_name[bone].cubes.append(c)
        return c

    def mirror_x(self, src_prefix, dst_prefix):
        """Copies every bone whose name contains src_prefix to dst_prefix mirrored across X=0."""
        for b in list(self.bones):
            if src_prefix not in b.name:
                continue
            nb_name = b.name.replace(src_prefix, dst_prefix)
            parent = b.parent.replace(src_prefix, dst_prefix) if b.parent and src_prefix in b.parent else b.parent
            rot = None if b.rotation is None else [b.rotation[0], -b.rotation[1], -b.rotation[2]]
            nb = self.bone(nb_name, parent, [-b.pivot[0], b.pivot[1], b.pivot[2]], rot)
            for c in b.cubes:
                o = [-(c.origin[0] + c.size[0]), c.origin[1], c.origin[2]]
                rot = None if c.rotation is None else [c.rotation[0], -c.rotation[1], -c.rotation[2]]
                piv = None if c.pivot is None else [-c.pivot[0], c.pivot[1], c.pivot[2]]
                nc = Cube(nb_name, o, c.size, c.mat, inflate=c.inflate, rotation=rot, pivot=piv,
                          faces=tuple({'east': 'west', 'west': 'east'}.get(f, f) for f in c.faces), tag=c.tag)
                nb.cubes.append(nc)

    # ---- atlas -------------------------------------------------------------------------------------
    def _face_px(self, c, f):
        d = self.density
        sx, sy, sz = c.size
        w, h = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy),
                'up': (sx, sz), 'down': (sx, sz)}[f]
        return max(1, int(math.ceil(w * d - 1e-6))), max(1, int(math.ceil(h * d - 1e-6)))

    def pack(self):
        rects = []
        for b in self.bones:
            for c in b.cubes:
                for f in c.faces:
                    w, h = self._face_px(c, f)
                    rects.append((h, w, c, f))
        rects.sort(key=lambda r: (-r[0], -r[1]))
        x = y = shelf = 0
        W = self.width
        for h, w, c, f in rects:
            if x + w > W:
                x, y, shelf = 0, y + shelf, 0
            c.uv[f] = (x, y, w, h)
            x += w
            shelf = max(shelf, h)
        need = y + shelf
        H = 16
        while H < need:
            H *= 2
        self.height = H
        return H

    def paint(self):
        H = self.pack()
        base = np.zeros((H, self.width, 4), np.uint8)
        glow = np.zeros((H, self.width, 4), np.uint8)
        for b in self.bones:
            for c in b.cubes:
                for f, (x, y, w, h) in c.uv.items():
                    face = Face(c, f, w, h)
                    col, gl = c.mat.paint(face, self.rng)
                    base[y:y + h, x:x + w] = col
                    if gl is not None:
                        glow[y:y + h, x:x + w] = gl
        return base, glow

    # ---- output ------------------------------------------------------------------------------------
    def geo_json(self):
        bones = []
        for b in self.bones:
            jb = {'name': b.name, 'pivot': [r1(v) for v in b.pivot]}
            if b.parent:
                jb['parent'] = b.parent
            if b.rotation:
                jb['rotation'] = [r1(v) for v in b.rotation]
            cubes = []
            for c in b.cubes:
                jc = {'origin': [r1(v) for v in c.origin], 'size': [r1(v) for v in c.size]}
                if c.inflate:
                    jc['inflate'] = r1(c.inflate)
                if c.rotation:
                    jc['pivot'] = [r1(v) for v in (c.pivot or c.origin)]
                    jc['rotation'] = [r1(v) for v in c.rotation]
                jc['uv'] = {f: {'uv': [x, y], 'uv_size': [w, h]} for f, (x, y, w, h) in c.uv.items()}
                cubes.append(jc)
            if cubes:
                jb['cubes'] = cubes
            bones.append(jb)
        return {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': 'geometry.' + self.identifier, 'texture_width': self.width,
                            'texture_height': self.height, 'visible_bounds_width': 4,
                            'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1, 0]},
            'bones': bones}]}

    def write(self, geo_rel, tex_rel, glow_rel=None, base=None, glow=None):
        if base is None:
            base, glow = self.paint()
        write_json(os.path.join(RES, geo_rel), self.geo_json())
        write_png(tex_rel, base)
        if glow_rel:
            g = glow.astype(float)
            g[..., :3] *= g[..., 3:4] / 255.0  # additive eyes pass: premultiplied, black = no glow
            g[..., 3] = np.where(g[..., 3] > 0, 255, 0)
            write_png(glow_rel, g.astype(np.uint8))
        return base, glow


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as fh:
        json.dump(data, fh, separators=(',', ':'))
        fh.write('\n')


def write_png(rel, arr, preview_dir=None):
    """Saves a texture as base64 under binassets (decoded by Gradle) and a plain copy in build/jinwoo_preview."""
    import base64
    import io
    img = Image.fromarray(arr, 'RGBA')
    buf = io.BytesIO()
    img.save(buf, 'PNG', optimize=True)
    out = os.path.join(BIN, rel + '.b64')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w') as fh:
        fh.write(base64.encodebytes(buf.getvalue()).decode('ascii'))
    plain = os.path.join(ROOT, 'build', 'jinwoo_preview', rel)
    os.makedirs(os.path.dirname(plain), exist_ok=True)
    img.save(plain)


# ---- materials -----------------------------------------------------------------------------------------
class Mat:
    """Procedural pixel-art material: base colour with value noise, cap/side shading, edge darkening,
    optional per-pixel overlays (fn(face, rgb, glow, rng)) and an emissive layer."""

    def __init__(self, base, noise=6, edge=0.18, top=1.12, bottom=0.78, fn=None, glow=None, alpha=255, grain=2, alpha_fn=None):
        self.base = np.array(hexc(base)[:3], float)
        self.noise, self.edge, self.top, self.bottom = noise, edge, top, bottom
        self.fn, self.glow, self.alpha, self.grain, self.alpha_fn = fn, glow, alpha, grain, alpha_fn

    def paint(self, face, rng):
        w, h = face.w, face.h
        rs = np.random.RandomState(rng.randrange(1 << 30))
        rgb = np.tile(self.base, (h, w, 1))
        # blocky value noise (2x2 cells keeps the pixel-art feel)
        g = self.grain
        cells = rs.uniform(-1, 1, ((h + g - 1) // g, (w + g - 1) // g))
        n = np.kron(cells, np.ones((g, g)))[:h, :w]
        rgb += (n * self.noise)[..., None]
        if face.name == 'up':
            rgb *= self.top
        elif face.name == 'down':
            rgb *= self.bottom
        if self.edge and w > 2 and h > 2:
            e = np.ones((h, w))
            e[0, :] = e[-1, :] = e[:, 0] = e[:, -1] = 1 - self.edge
            rgb *= e[..., None]
        glow = None
        if self.glow is not None or self.fn is not None:
            glow = np.zeros((h, w, 4), float)
        if self.fn is not None:
            self.fn(face, rgb, glow, rs)
        out = np.zeros((h, w, 4), np.uint8)
        out[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
        out[..., 3] = self.alpha if self.alpha_fn is None else self.alpha_fn(face, rs)
        gl = None
        if glow is not None:
            if self.glow is not None:
                self.glow(face, glow, rs)
            gl = np.clip(glow, 0, 255).astype(np.uint8)
        return out, gl


def put(arr, mask, color, alpha=1.0):
    """Blends a colour (hex or rgb) into rgb or rgba arrays where mask is true."""
    c = np.array(hexc(color) if isinstance(color, str) else color, float)
    if arr.shape[-1] == 4:
        if len(c) == 3:
            c = np.append(c, 255)
        arr[mask] = arr[mask] * (1 - alpha) + c * alpha
    else:
        arr[mask] = arr[mask] * (1 - alpha) + c[:3] * alpha


# ---- animations ----------------------------------------------------------------------------------------
class Clip:
    """Keyframed clip. key(t_seconds, {bone: (rx, ry, rz)} or {bone: {'rot':..,'pos':..,'scale':..}})."""

    def __init__(self, name, length, loop=False, smooth=True):
        self.name, self.length, self.loop, self.smooth = name, length, loop, smooth
        self.frames = []
        self.sounds = {}
        self.keep = set()

    def key(self, t, pose):
        self.frames.append((t, pose))
        return self

    def to_json(self):
        chans = {}
        bones = []
        for t, pose in self.frames:
            for bone, v in pose.items():
                if bone not in bones:
                    bones.append(bone)
                spec = v if isinstance(v, dict) else {'rot': v}
                for ch, key in (('rot', 'rotation'), ('pos', 'position'), ('scale', 'scale')):
                    if ch in spec:
                        chans.setdefault(bone, {}).setdefault(key, {})[t] = spec[ch]
        out_b = {}
        for bone in bones:
            jb = {}
            for key, kf in chans.get(bone, {}).items():
                times = sorted(kf)
                vals = [[r1(round(v, 1)) for v in kf[tt]] for tt in times]
                if all(v == vals[0] for v in vals):
                    if key == 'rotation' and all(x == 0 for x in vals[0]) or key == 'scale' and all(x == 1 for x in vals[0]):
                        if not self.loop and bone not in self.keep:
                            continue
                    jb[key] = vals[0]
                    continue
                keep = [i for i in range(len(times))
                        if i == 0 or i == len(times) - 1 or not (vals[i] == vals[i - 1] == vals[i + 1])]
                d = {}
                mode = getattr(self, 'mode', 'catmullrom')
                for i in keep:
                    d['%g' % round(times[i], 4)] = {'post': vals[i], 'lerp_mode': mode} if self.smooth else vals[i]
                jb[key] = d
            if not jb:
                continue
            out_b[bone] = jb
        j = {'animation_length': round(self.length, 4)}
        if self.loop:
            j['loop'] = True
        j['bones'] = out_b
        return j


def write_anims(rel, clips):
    data = {'format_version': '1.8.0', 'animations': {c.name: c.to_json() for c in clips}}
    write_json(os.path.join(RES, rel), data)
    return os.path.getsize(os.path.join(RES, rel))


def lerp_pose(a, b, f):
    """Blends two rotation-only poses (bones missing on one side count as zero)."""
    out = {}
    for k in set(a) | set(b):
        va, vb = a.get(k, (0, 0, 0)), b.get(k, (0, 0, 0))
        out[k] = tuple(x + (y - x) * f for x, y in zip(va, vb))
    return out


def merge(*poses):
    out = {}
    for p in poses:
        out.update(p)
    return out
