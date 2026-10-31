"""Lint for the clean Captain / Avengers models (own work, CC0).

For every model group (meshes drawn together with one pose, e.g. body + gear) checks:
  * uv:      every face island inside the texture, texture size == description size;
  * overlap: no two face islands share texels;
  * gutter:  the 1 px ring around each island repeats its edge colours (no bleeding at face edges);
  * zfight:  no two faces of the group lie in the same plane facing the same way with overlapping area
             (coplanar faces flicker as stripes in game).
Usage: python3 tools/assets/geo_lint.py [group ...]   (no argument = all groups; exit code 1 on any error)
"""
import base64
import io
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'viltrumitecore/src/main/resources/assets/viltrumitecore')
BIN = os.path.join(ROOT, 'viltrumitecore/src/main/binassets/assets/viltrumitecore')

GROUPS = {
    'captain': [('captain/captain', 'captain/captain'), ('captain/captain_gear', 'captain/captain_gear')],
    'captain_arms': [('captain/captain_arms', 'captain/captain'), ('captain/captain_arms_gear', 'captain/captain_gear')],
    'shield': [('captain/captain_shield', 'captain/captain_shield')],
    'thor': [('avengers/thor', 'avengers/thor'), ('avengers/thor_gear', 'avengers/thor_gear')],
    'black_widow': [('avengers/black_widow', 'avengers/black_widow'), ('avengers/black_widow_gear', 'avengers/black_widow_gear')],
    'hawkeye': [('avengers/hawkeye', 'avengers/hawkeye'), ('avengers/hawkeye_gear', 'avengers/hawkeye_gear')],
    'hulk': [('avengers/hulk', 'avengers/hulk')],
    'iron_man_gear': [('avengers/iron_man_gear', 'ironman/mark_42/pieces', {'gutter': False, 'reused': True})],
    'mjolnir': [('avengers/mjolnir', 'avengers/mjolnir')],
    'iron_man': [('avengers/avenger', 'avengers/iron_man', {'gutter': False}),
                 ('avengers/iron_man_gear', 'ironman/mark_42/pieces', {'gutter': False, 'reused': True})],
    'arrow': [('avengers/arrow', 'avengers/arrow')],
    'arrow_explosive': [('avengers/arrow_explosive', 'avengers/arrow_explosive')],
    'arrow_shock': [('avengers/arrow_shock', 'avengers/arrow_shock')],
    'mini_missile': [('avengers/mini_missile', 'avengers/mini_missile')],
    'quinjet': [('avengers/quinjet', 'avengers/quinjet')],
}


def load_tex(rel):
    p = os.path.join(BIN, 'textures/entity', rel + '.png.b64')
    if not os.path.exists(p):
        return None
    return np.array(Image.open(io.BytesIO(base64.b64decode(open(p).read()))).convert('RGBA'))


def rot_m(rx, ry, rz):
    # Bedrock order as in BakedGeoModel / preview: Z * Y(-) * X(-) on mirrored X; we only need planes, so use the
    # geometric rotation consistently for every cube (comparisons are relative).
    ax, ay, az = map(math.radians, (rx, ry, rz))
    X = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    Y = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    Z = np.array([[math.cos(az), -math.sin(az), 0], [math.sin(az), math.cos(az), 0], [0, 0, 1]])
    return Z @ Y @ X


def bone_frames(bones):
    """Rest-pose world transform (R, t) of every bone (rotation about its pivot, parents first)."""
    by = {b['name']: b for b in bones}
    cache = {}

    def frame(name):
        if name in cache:
            return cache[name]
        b = by[name]
        R, t = (np.eye(3), np.zeros(3)) if not b.get('parent') or b['parent'] not in by else frame(b['parent'])
        r = b.get('rotation')
        if r and any(r):
            p = np.array(b['pivot'], float)
            Rl = rot_m(*r)
            # x' = Rl (x - p) + p, then parent
            R2 = R @ Rl
            t2 = R @ (p - Rl @ p) + t
            R, t = R2, t2
        cache[name] = (R, t)
        return cache[name]

    for n in by:
        frame(n)
    return cache


FACE_DEF = {  # name: (axis, sign)
    'north': (2, -1), 'south': (2, 1), 'west': (0, -1), 'east': (0, 1), 'down': (1, -1), 'up': (1, 1)}


def cube_faces(c, R, t):
    o = np.array(c['origin'], float)
    s = np.array(c['size'], float)
    inf = c.get('inflate', 0.0)
    lo, hi = o - inf, o + s + inf
    Rc, pc = np.eye(3), np.zeros(3)
    if c.get('rotation') and any(c['rotation']):
        Rc = rot_m(*c['rotation'])
        pc = np.array(c.get('pivot', c['origin']), float)
    out = []
    for name in c['uv'] if isinstance(c.get('uv'), dict) else FACE_DEF:
        axis, sign = FACE_DEF[name]
        a1, a2 = [i for i in range(3) if i != axis]
        corners = []
        for u, v in ((0, 0), (1, 0), (1, 1), (0, 1)):
            p = np.zeros(3)
            p[axis] = hi[axis] if sign > 0 else lo[axis]
            p[a1] = lo[a1] + (hi[a1] - lo[a1]) * u
            p[a2] = lo[a2] + (hi[a2] - lo[a2]) * v
            p = Rc @ (p - pc) + pc
            corners.append(R @ p + t)
        n = np.zeros(3)
        n[axis] = sign
        n = R @ (Rc @ n)
        area = (hi[a1] - lo[a1]) * (hi[a2] - lo[a2])
        out.append((name, np.array(corners), n, area))
    return out


def inside(pt, quad, n):
    # point strictly inside convex quad (same plane)
    sgn = 0
    for i in range(4):
        a, b = quad[i], quad[(i + 1) % 4]
        cr = np.dot(np.cross(b - a, pt - a), n)
        if abs(cr) < 1e-4:
            return False
        s = 1 if cr > 0 else -1
        if sgn == 0:
            sgn = s
        elif s != sgn:
            return False
    return True


def _plane_2d(q, n):
    a = np.array([1.0, 0.0, 0.0]) if abs(n[0]) < 0.9 else np.array([0.0, 1.0, 0.0])
    e1 = np.cross(n, a)
    e1 /= np.linalg.norm(e1)
    e2 = np.cross(n, e1)
    return [(float(np.dot(p, e1)), float(np.dot(p, e2))) for p in q]


def _area(poly):
    return 0.5 * abs(sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
                         for i in range(len(poly))))


def _clip(subject, clipper):
    """Sutherland-Hodgman: intersection of two convex polygons (2D)."""
    def ccw(poly):
        s = sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
                for i in range(len(poly)))
        return poly if s > 0 else poly[::-1]
    out = ccw(subject)
    clipper = ccw(clipper)
    for i in range(len(clipper)):
        a, b = clipper[i], clipper[(i + 1) % len(clipper)]
        inp, out = out, []
        if not inp:
            break

        def side(p):
            return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
        for j in range(len(inp)):
            p, q = inp[j], inp[(j + 1) % len(inp)]
            sp, sq = side(p), side(q)
            if sp >= 0:
                out.append(p)
            if (sp >= 0) != (sq >= 0):
                t = sp / (sp - sq)
                out.append((p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t))
    return out


def overlap(q1, q2, n, min_area=0.004):
    """True when two coplanar quads share more than min_area (px^2) - thin slivers included."""
    inter = _clip(_plane_2d(q1, n), _plane_2d(q2, n))
    return len(inter) >= 3 and _area(inter) > min_area


WARNINGS = []


def lint_group(name, members):
    errors = []
    faces = []
    for member in members:
        geo_rel, tex_rel = member[:2]
        gutters = len(member) < 3 or member[2].get('gutter', True)  # vanilla-layout skins have no gutters
        reused = len(member) > 2 and member[2].get('reused', False)  # a hero asset taken as is (Sind Mark 42)
        path = os.path.join(RES, 'geo', geo_rel + '.geo.json')
        if not os.path.exists(path):
            errors.append(f'{name}: missing {geo_rel}')
            continue
        g = json.load(open(path))['minecraft:geometry'][0]
        d = g['description']
        tw, th = d['texture_width'], d['texture_height']
        tex = load_tex(tex_rel)
        if tex is None:
            errors.append(f'{name}: missing texture {tex_rel}')
        k = 1
        if tex is not None and not gutters and tex.shape[1] % tw == 0 and tex.shape[1] // tw == tex.shape[0] // th:
            k = tex.shape[1] // tw  # HD player-layout skin (e.g. 128 x 128 on a 64 x 64 geometry)
        if tex is not None and (tex.shape[1] != tw * k or tex.shape[0] != th * k):
            errors.append(f'{name}: {geo_rel} declares {tw}x{th} but {tex_rel} is {tex.shape[1]}x{tex.shape[0]}')
        used = np.zeros((int(th), int(tw)), np.int32)
        frames = bone_frames(g['bones'])
        for b in g['bones']:
            R, t = frames[b['name']]
            for ci, c in enumerate(b.get('cubes', [])):
                uvs = c.get('uv')
                if not isinstance(uvs, dict):
                    errors.append(f'{name}: {geo_rel} {b["name"]}#{ci} box UV (expected per-face)')
                    continue
                clear = set()
                masks = {}
                for f, spec in uvs.items():
                    (u, v), (w, h) = spec['uv'], spec['uv_size']
                    flips = (w < 0, h < 0)
                    # a negative size is the same island read backwards (GeckoLib flips the face)
                    u, w = (u + w, -w) if w < 0 else (u, w)
                    v, h = (v + h, -h) if h < 0 else (v, h)
                    if w <= 0 or h <= 0 or u < 0 or v < 0 or u + w > tw or v + h > th:
                        errors.append(f'{name}: {geo_rel} {b["name"]}#{ci}.{f} uv out of bounds {u},{v} {w}x{h}')
                        continue
                    if not gutters:
                        # player-layout skin: joint caps reuse the box caps by design, no gutters
                        u, v, w, h = int(u * k), int(v * k), int(math.ceil(w * k)), int(math.ceil(h * k))
                        if tex is not None and (tex[v:v + h, u:u + w][..., 3] == 0).all():
                            clear.add(f)
                        elif tex is not None:
                            masks[f] = (flips, tex[v:v + h, u:u + w][..., 3] > 0)
                        continue
                    u, v, w, h = int(u), int(v), int(math.ceil(w)), int(math.ceil(h))
                    if (used[v:v + h, u:u + w] > 0).any():
                        errors.append(f'{name}: {geo_rel} {b["name"]}#{ci}.{f} uv overlaps another face')
                    used[v:v + h, u:u + w] += 1
                    if tex is not None:
                        isl = tex[v:v + h, u:u + w]
                        if v > 0 and not np.array_equal(tex[v - 1, u:u + w], isl[0]):
                            errors.append(f'{name}: {geo_rel} {b["name"]}#{ci}.{f} top gutter differs (bleeding)')
                        if v + h < th and not np.array_equal(tex[v + h, u:u + w], isl[-1]):
                            errors.append(f'{name}: {geo_rel} {b["name"]}#{ci}.{f} bottom gutter differs (bleeding)')
                        if u > 0 and not np.array_equal(tex[v:v + h, u - 1], isl[:, 0]):
                            errors.append(f'{name}: {geo_rel} {b["name"]}#{ci}.{f} left gutter differs (bleeding)')
                        if u + w < tw and not np.array_equal(tex[v:v + h, u + w], isl[:, -1]):
                            errors.append(f'{name}: {geo_rel} {b["name"]}#{ci}.{f} right gutter differs (bleeding)')
                        if (isl[..., 3] == 0).all():
                            clear.add(f)  # fully transparent face: never drawn, cannot z-fight
                for fn, quad, n, area in cube_faces(c, R, t):
                    if area > 1e-6 and fn not in clear:
                        faces.append((f'{geo_rel}:{b["name"]}#{ci}.{fn}', quad, n, masks.get(fn), reused))
    # z-fighting: same plane, same facing, overlapping area
    for i in range(len(faces)):
        ni, qi, nn, mi, ri = faces[i]
        di = float(np.dot(nn, qi[0]))
        for j in range(i + 1, len(faces)):
            nj, qj, mn, mj, rj = faces[j]
            if np.dot(nn, mn) < 0.999:
                continue
            if abs(di - float(np.dot(mn, qj[0]))) > 0.015:
                continue
            if (mi is not None and mj is not None and mi[0] == mj[0] and mi[1].shape == mj[1].shape
                    and np.allclose(qi, qj, atol=1e-4) and not (mi[1] & mj[1]).any()):
                continue  # layered masks of a reused asset (Sind pieces): the same face, disjoint opaque pixels
            if overlap(qi, qj, nn):
                if ri and rj:
                    # both faces belong to the hero's own Sind pieces (identical in the hero's suit): reported, not fatal
                    WARNINGS.append(f'{name}: reused-asset overlap {ni} / {nj}')
                else:
                    errors.append(f'{name}: z-fight {ni} / {nj}')
    return errors


def main(argv):
    names = argv or list(GROUPS)
    errs = []
    for n in names:
        errs += lint_group(n, GROUPS[n])
    for e in errs:
        print(e)
    if WARNINGS:
        print(f'geo_lint: {len(WARNINGS)} overlap(s) inside reused hero assets (same as in the hero, not counted)')
    print(f'geo_lint: {len(errs)} problem(s) in {", ".join(names)}')
    return 1 if errs else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
