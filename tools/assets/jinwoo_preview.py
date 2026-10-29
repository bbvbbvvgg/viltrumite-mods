"""Offline preview renderer for the Jinwoo geo models and clips (checks poses without the game).

Mirrors BakedGeoModel / AnimRenderer math: Bedrock X is negated, rotations Z*Y*X about the pivot,
position offsets (-x, y, z). Draws a cutout pass with simple Lambert lighting plus the glow pass.
Usage: python3 tools/assets/jinwoo_preview.py <geo.json> <tex.png> [glow.png] [anim.json clip t] -o out.png
"""
import json
import math
import sys

import numpy as np
from PIL import Image


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])


def tr(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def sc(x, y, z):
    return np.diag([x, y, z, 1.0])


def sample(ch, t):
    """Linear sampling of a Bedrock channel (array or {time: vec|{post}})."""
    if isinstance(ch, list):
        return ch
    keys = sorted((float(k), (v['post'] if isinstance(v, dict) else v)) for k, v in ch.items())
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return keys[-1][1]


def load(geo_path):
    g = json.load(open(geo_path))['minecraft:geometry'][0]
    return g


def pose_from_clip(anim, t):
    pose = {}
    for bone, chans in anim.get('bones', {}).items():
        p = {}
        for k in ('rotation', 'position', 'scale'):
            if k in chans:
                p[k] = sample(chans[k], t)
        pose[bone] = p
    return pose


def build_quads(g, pose, extra=None, root=None):
    """Returns list of (verts 4x3 world, uv 4x2 in texels, normal) for every cube face."""
    bones = {b['name']: b for b in g['bones']}
    children = {}
    for b in g['bones']:
        children.setdefault(b.get('parent'), []).append(b['name'])
    out = []
    extra = extra or {}

    def walk(name, M):
        b = bones[name]
        p = b.get('pivot', [0, 0, 0])
        piv = np.array([-p[0], p[1], p[2]]) / 16
        r0 = b.get('rotation', [0, 0, 0])
        pp = dict(pose.get(name, {}))
        for k, v in extra.get(name, {}).items():
            pp[k] = v
        rr = pp.get('rotation', [0, 0, 0])
        rx = math.radians(-(r0[0] + rr[0]))
        ry = math.radians(-(r0[1] + rr[1]))
        rz = math.radians(r0[2] + rr[2])
        ps = pp.get('position', [0, 0, 0])
        s = pp.get('scale', [1, 1, 1])
        if isinstance(s, (int, float)):
            s = [s, s, s]
        M = M @ tr(-ps[0] / 16, ps[1] / 16, ps[2] / 16) @ tr(*piv) @ rot_z(rz) @ rot_y(ry) @ rot_x(rx) @ sc(*s) @ tr(*(-piv))
        if not pp.get('hidden'):
            for c in b.get('cubes', []):
                out.extend(cube_quads(c, M))
        for ch in children.get(name, []):
            walk(ch, M)

    for top in children.get(None, []):
        walk(top, np.eye(4) if root is None else root)
    return out


def cube_quads(c, M):
    o, s = c['origin'], c['size']
    inf = c.get('inflate', 0)
    x1, x2 = (-(o[0] + s[0]) - inf) / 16, (-o[0] + inf) / 16
    y1, y2 = (o[1] - inf) / 16, (o[1] + s[1] + inf) / 16
    z1, z2 = (o[2] - inf) / 16, (o[2] + s[2] + inf) / 16
    if 'rotation' in c:
        cp = c.get('pivot', [0, 0, 0])
        cpv = np.array([-cp[0], cp[1], cp[2]]) / 16
        cr = c['rotation']
        M = M @ tr(*cpv) @ rot_z(math.radians(cr[2])) @ rot_y(math.radians(-cr[1])) @ rot_x(math.radians(-cr[0])) @ tr(*(-cpv))
    uv = c['uv']

    def face(key):
        f = uv.get(key)
        if f is None:
            return None
        u, v = f['uv']
        w, h = f['uv_size']
        return [u + w, v, u, v + h]  # FLIP_U

    faces = [
        ('north', [(x1, y2, z1), (x2, y2, z1), (x2, y1, z1), (x1, y1, z1)], (0, 0, -1)),
        ('south', [(x2, y2, z2), (x1, y2, z2), (x1, y1, z2), (x2, y1, z2)], (0, 0, 1)),
        ('east', [(x1, y2, z2), (x1, y2, z1), (x1, y1, z1), (x1, y1, z2)], (-1, 0, 0)),
        ('west', [(x2, y2, z1), (x2, y2, z2), (x2, y1, z2), (x2, y1, z1)], (1, 0, 0)),
        ('up', [(x1, y2, z2), (x2, y2, z2), (x2, y2, z1), (x1, y2, z1)], (0, 1, 0)),
        ('down', [(x1, y1, z1), (x2, y1, z1), (x2, y1, z2), (x1, y1, z2)], (0, -1, 0)),
    ]
    res = []
    for key, verts, n in faces:
        r = face(key)
        if r is None:
            continue
        u0, v0, u1, v1 = r
        V = np.array([list(v) + [1] for v in verts]) @ M.T
        N = (M[:3, :3] @ np.array(n, float))
        N /= (np.linalg.norm(N) + 1e-9)
        res.append((V[:, :3], np.array([(u0, v0), (u1, v0), (u1, v1), (u0, v1)]), N))
    return res


def render(quads, tex, glow, yaw=0.0, pitch=0.0, size=320, scale=None, center=(0, 1.0, 0), persp=None):
    """Orthographic render (yaw 0 = looking at the front of the model), or a first-person perspective
    camera when persp=(fov_deg,): camera at the origin looking down -Z like the hand-render pass."""
    W = H = size
    if persp is not None:
        return render_persp(quads, tex, glow, size, persp[0])
    img = np.zeros((H, W, 3), float)
    img[:] = (58, 62, 72)
    zb = np.full((H, W), 1e9)
    R = rot_x(math.radians(pitch)) @ rot_y(math.radians(yaw))
    scale = scale or size / 2.6
    light = np.array([0.35, 0.8, -0.5])
    light /= np.linalg.norm(light)
    for q in quads:
        V, UV, N = q[:3]
        tx, gw = (q[3], q[4]) if len(q) > 3 else (tex, glow)
        th, tw = tx.shape[:2]
        P = (np.c_[V - np.array(center), np.ones(4)] @ R.T)[:, :3]
        Nr = R[:3, :3] @ N
        sx = W / 2 - P[:, 0] * scale
        sy = H / 2 - P[:, 1] * scale
        dz = P[:, 2]
        shade = 0.55 + 0.45 * max(0.0, float(np.dot(Nr, light)))
        for tri in ((0, 1, 2), (0, 2, 3)):
            draw_tri(img, zb, sx[list(tri)], sy[list(tri)], dz[list(tri)], UV[list(tri)], tx, gw, shade, tw, th)
    return np.clip(img, 0, 255).astype(np.uint8)


def render_persp(quads, tex, glow, size, fov):
    W = H = size
    img = np.zeros((H, W, 3), float)
    img[:] = (96, 120, 150)
    zb = np.full((H, W), 1e9)
    f = (H / 2) / math.tan(math.radians(fov) / 2)
    light = np.array([0.35, 0.8, 0.5])
    light /= np.linalg.norm(light)
    for q in quads:
        V, UV, N = q[:3]
        tx, gw = (q[3], q[4]) if len(q) > 3 else (tex, glow)
        th, tw = tx.shape[:2]
        if (V[:, 2] > -0.03).any():
            continue
        d = -V[:, 2]
        sx = W / 2 + V[:, 0] / d * f
        sy = H / 2 - V[:, 1] / d * f
        shade = 0.55 + 0.45 * max(0.0, float(np.dot(N, light)))
        for tri in ((0, 1, 2), (0, 2, 3)):
            draw_tri(img, zb, sx[list(tri)], sy[list(tri)], d[list(tri)], UV[list(tri)], tx, gw, shade, tw, th)
    return np.clip(img, 0, 255).astype(np.uint8)


def draw_tri(img, zb, xs, ys, zs, uvs, tex, glow, shade, tw, th):
    H, W = zb.shape
    x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
    y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
    if x1 < x0 or y1 < y0:
        return
    den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
    if abs(den) < 1e-9:
        return
    X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    a = ((ys[1] - ys[2]) * (X - xs[2]) + (xs[2] - xs[1]) * (Y - ys[2])) / den
    b = ((ys[2] - ys[0]) * (X - xs[2]) + (xs[0] - xs[2]) * (Y - ys[2])) / den
    c = 1 - a - b
    m = (a >= -1e-4) & (b >= -1e-4) & (c >= -1e-4)
    if not m.any():
        return
    z = a * zs[0] + b * zs[1] + c * zs[2]
    u = (a * uvs[0][0] + b * uvs[1][0] + c * uvs[2][0])
    v = (a * uvs[0][1] + b * uvs[1][1] + c * uvs[2][1])
    ui = np.clip(u.astype(int), 0, tw - 1)
    vi = np.clip(v.astype(int), 0, th - 1)
    texel = tex[vi, ui]
    sub = zb[y0:y1 + 1, x0:x1 + 1]
    gmask = (glow[vi, ui][..., :3].sum(-1) > 10) if glow is not None else False
    m &= ((texel[..., 3] > 8) | gmask) & (z < sub)
    if not m.any():
        return
    col = texel[..., :3].astype(float) * shade
    if glow is not None:
        gt = glow[vi, ui].astype(float)
        col = np.where((texel[..., 3:4] > 8), col, 0) + gt[..., :3]
    region = img[y0:y1 + 1, x0:x1 + 1]
    region[m] = col[m]
    sub[m] = z[m]


def sheet(geo, tex, glow, frames, size=300, cols=4, label=True, center=(0, 1.0, 0), scale=None, persp=None, root=None):
    """frames: list of (title, pose, yaw, pitch). Returns a PIL image grid. root: 4x4 model matrix."""
    from PIL import ImageDraw
    tiles = []
    for title, pose, yaw, pitch in frames:
        q = build_quads(geo, pose, root=root)
        im = Image.fromarray(render(q, tex, glow, yaw, pitch, size, scale=scale, center=center, persp=persp))
        if label:
            ImageDraw.Draw(im).text((4, 4), title, fill=(255, 255, 255))
        tiles.append(im)
    rows = (len(tiles) + cols - 1) // cols
    out = Image.new('RGB', (cols * size, rows * size), (30, 30, 30))
    for i, t in enumerate(tiles):
        out.paste(t, ((i % cols) * size, (i // cols) * size))
    return out


if __name__ == '__main__':
    args = sys.argv[1:]
    out = args[args.index('-o') + 1]
    geo = load(args[0])
    tex = np.array(Image.open(args[1]).convert('RGBA'))
    glow = np.array(Image.open(args[2]).convert('RGBA')) if len(args) > 2 and args[2].endswith('.png') else None
    frames = [('front', {}, 0, 0), ('3/4', {}, 35, -10), ('side', {}, 90, 0), ('back', {}, 180, 0)]
    sheet(geo, tex, glow, frames).save(out)


def layered(models, pose, root=None):
    """models: list of (geo, tex, glow); returns textured quads of all models posed alike."""
    out = []
    for g, t, gl in models:
        k = t.shape[1] / g['description']['texture_width']  # HD textures keep geo UVs in base space
        out.extend((V, UV * k, N, t, gl) for V, UV, N in build_quads(g, pose, root=root))
    return out


def sheet_layered(models, frames, size=300, cols=4, center=(0, 1.0, 0), scale=None, persp=None, root=None):
    from PIL import ImageDraw
    tiles = []
    for title, pose, yaw, pitch in frames:
        im = Image.fromarray(render(layered(models, pose, root), None, None, yaw, pitch, size, scale=scale, center=center, persp=persp))
        ImageDraw.Draw(im).text((4, 4), title, fill=(255, 255, 255))
        tiles.append(im)
    rows = (len(tiles) + cols - 1) // cols
    out = Image.new('RGB', (cols * size, rows * size), (30, 30, 30))
    for i, t in enumerate(tiles):
        out.paste(t, ((i % cols) * size, (i // cols) * size))
    return out
