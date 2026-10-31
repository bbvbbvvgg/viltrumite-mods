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
BRONZE = '#5d5344'        # tarnished plate: the bulk of the armour
BRONZE_DARK = '#3d372f'
GOLD = '#a3834c'          # trims, rings, crest edges only
GOLD_DARK = '#6b5130'
ALLOY = '#2c2e34'
ALLOY_LIGHT = '#4a4d55'
HOSE = '#1f1e1d'
BONE = '#b9ad93'
ENERGY = (70, 140, 255)
ENERGY_HOT = (190, 225, 255)


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


def armour_fn(seams=True, rivets=True, scratches=3, trim=None):
    """Worn plate: lit top band, darker lower edge, seam lines, rivets, scratches; optional gold trim line
    along the top edge of the side faces (the plates stay dark bronze, gold only as an accent)."""
    trim_rgb = None if trim is None else np.array(hexc(trim)[:3], float)

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
                    rgb[yy, xx] = rgb[yy, xx] * 0.45 + np.array([196, 160, 98]) * 0.55
        if trim_rgb is not None and face.name not in ('up', 'down') and h >= 4:
            rgb[0, :] = rgb[0, :] * 0.35 + trim_rgb * 0.65
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


def energy_glow(colour=ENERGY, hot=ENERGY_HOT, core=0.2, alpha=255):
    """Glowing cell: small hot core fading to saturated energy blue at the rim (base and glow layers)."""
    def fn(face, rgb, glow, rs):
        u, v = face.grid()
        d = np.maximum(abs(u - 0.5), abs(v - 0.5)) * 2.0
        t = np.clip((d - core) / max(1e-3, 1.0 - core), 0, 1)[..., None] ** 0.7
        col = np.array(hot, float) * (1 - t) + np.array(colour, float) * t
        rgb[...] = col * 0.45
        if glow is not None:
            glow[..., :3] = col
            glow[..., 3] = alpha
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
        'bronze': Mat(BRONZE, noise=8, edge=0.26, top=1.16, bottom=0.7, fn=armour_fn(trim=GOLD)),
        'bronze_plain': Mat(BRONZE, noise=6, edge=0.22, top=1.14, bottom=0.72, fn=armour_fn(seams=False, rivets=False, scratches=1)),
        'bronze_dark': Mat(BRONZE_DARK, noise=6, edge=0.2, fn=armour_fn(seams=False, scratches=2)),
        'gold': Mat(GOLD, noise=9, edge=0.24, top=1.18, bottom=0.7, fn=armour_fn(seams=False)),
        'gold_dark': Mat(GOLD_DARK, noise=7, edge=0.2, fn=armour_fn(seams=False)),
        'alloy': Mat(ALLOY, noise=5, edge=0.22, fn=alloy_fn()),
        'alloy_lit': Mat(ALLOY, noise=5, edge=0.22, fn=alloy_fn(lights=2)),
        'alloy_light': Mat(ALLOY_LIGHT, noise=6, edge=0.2, fn=alloy_fn()),
        'hose': Mat(HOSE, noise=4, edge=0.1, fn=hose_fn),
        'bone': Mat(BONE, noise=10, edge=0.2),
        'eye': Mat('#121016', noise=0, edge=0, fn=eye_fn),
        'energy': Mat('#4f8cff', noise=0, edge=0, fn=energy_glow()),
        'energy_soft': Mat('#3f74e0', noise=0, edge=0, fn=energy_glow(core=0.0, alpha=200)),
    }


# ---- forward kinematics ------------------------------------------------------------------------------------
_BONES = {}


def bones_of(geo):
    key = id(geo)
    if key not in _BONES:
        _BONES[key] = {b['name']: b for b in geo['minecraft:geometry'][0]['bones']}
    return _BONES[key]


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


def _rot_np(pivot, rot):
    """numpy 4x4 of preview_geo.rotation (Bedrock pivot rotation, R = Rz(-rz) Ry(ry) Rx(-rx))."""
    if not rot or not any(rot):
        return np.eye(4)
    px, py, pz = pivot
    rx, ry, rz = (math.radians(v) for v in rot)
    cx, sx = math.cos(-rx), math.sin(-rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(-rz), math.sin(-rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    R = Rz @ Ry @ Rx
    m = np.eye(4)
    m[:3, :3] = R
    p = np.array([px, py, pz], float)
    m[:3, 3] = p - R @ p
    return m


def _bone_m(geo, pose, name, cache):
    if name in cache:
        return cache[name]
    bones = bones_of(geo)
    b = bones[name]
    parent = b.get('parent')
    m = _bone_m(geo, pose, parent, cache) if parent in bones else np.eye(4)
    p = pose.get(name, {})
    if 'pos' in p:
        t = np.eye(4)
        t[:3, 3] = p['pos']
        m = m @ t
    rest = b.get('rotation') or [0, 0, 0]
    rot = [r + a for r, a in zip(rest, p.get('rot', [0, 0, 0]))]
    m = m @ _rot_np(b.get('pivot', [0, 0, 0]), rot)
    if 'scale' in p:
        sxx, syy, szz = p['scale']
        pv = np.array(b.get('pivot', [0, 0, 0]), float)
        s4 = np.diag([sxx, syy, szz, 1.0])
        t1, t2 = np.eye(4), np.eye(4)
        t1[:3, 3] = pv
        t2[:3, 3] = -pv
        m = m @ t1 @ s4 @ t2
    cache[name] = m
    return m


def bone_matrix(geo, pose, name):
    """numpy 4x4 model matrix of one bone under a pose (only its ancestors are evaluated)."""
    return _bone_m(geo, pose or {}, name, {})


def matrices(geo, pose=None):
    """Bone name -> 4x4 model matrix (nested lists, Bedrock space) under a pose {bone: {'rot': [..], 'pos': [..]}}."""
    cache = {}
    out = {}
    for name in bones_of(geo):
        out[name] = _bone_m(geo, pose or {}, name, cache).tolist()
    return out


def point(geo, pose, bone, local):
    m = bone_matrix(geo, pose, bone)
    return tuple(m[:3, :3] @ np.array(local, float) + m[:3, 3])


def pivot_of(geo, bone):
    return list(bones_of(geo)[bone].get('pivot', [0, 0, 0]))


def unit(v):
    v = np.array(v, float)
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def euler_of(R):
    """Bedrock (rx, ry, rz) whose pivot rotation has the 3x3 rotation part R (R = Rz(-rz) Ry(ry) Rx(-rx))."""
    sb = max(-1.0, min(1.0, -R[2][0]))
    b = math.asin(sb)
    if abs(sb) < 0.99999:
        a = math.atan2(R[2][1], R[2][2])
        g = math.atan2(R[1][0], R[0][0])
    else:
        a = math.atan2(-R[1][2], R[1][1])
        g = 0.0
    return (round(-math.degrees(a), 2), round(math.degrees(b), 2), round(-math.degrees(g), 2))


def frame(d, u):
    """Rotation taking the rest axes -Y (barrel / along the limb) to d and -Z (top / thumb side) to u."""
    d = unit(d)
    u = np.array(u, float)
    u = unit(u - np.dot(u, d) * d)
    ey, ez = -d, -u
    return np.column_stack([np.cross(ey, ez), ey, ez])


def as_pose(p):
    """Clip-style pose (tuples or dicts) -> FK pose dict with lists."""
    out = {}
    for k, v in p.items():
        if isinstance(v, dict):
            out[k] = {kk: list(vv) for kk, vv in v.items()}
        else:
            out[k] = {'rot': list(v)}
    return out


def solve_chain(geo, pose, shoulder, elbow, target, pole, end_bone=None, end_local=None, w_pole=0.45, iters=60,
                elbow_range=(-155.0, -2.0)):
    """Two-bone IK by damped least squares over (shoulder rx, ry, rz, elbow rx). The end point defaults to the
    pivot of the elbow's child (the wrist); the elbow is pulled toward the analytic position on the side of
    the pole vector so the swivel is natural. Returns (pose, end error px)."""
    p = json.loads(json.dumps(pose))
    for b in (shoulder, elbow):
        p.setdefault(b, {}).setdefault('rot', [0.0, 0.0, 0.0])
    bones = bones_of(geo)
    if end_bone is None:
        child = [n for n, b in bones.items() if b.get('parent') == elbow and n.startswith('hand')]
        end_bone, end_local = elbow, pivot_of(geo, child[0])
    T = np.array(target, float)
    S = np.array(point(geo, p, shoulder, pivot_of(geo, shoulder)))
    a = np.linalg.norm(np.array(pivot_of(geo, elbow)) - np.array(pivot_of(geo, shoulder)))
    b = np.linalg.norm(np.array(end_local) - np.array(pivot_of(geo, elbow)))
    d_vec = T - S
    d = np.linalg.norm(d_vec)
    dc = min(max(d, abs(a - b) + 1e-3), a + b - 1e-3)
    u = d_vec / max(d, 1e-9)
    cos_a = (a * a + dc * dc - b * b) / (2 * a * dc)
    sin_a = math.sqrt(max(0.0, 1 - cos_a * cos_a))
    pv = np.array(pole, float)
    v = pv - np.dot(pv, u) * u
    v = unit(v) if np.linalg.norm(v) > 1e-6 else unit(np.cross(u, [0, 0, 1]))
    E = S + a * (cos_a * u + sin_a * v)
    piv_e = pivot_of(geo, elbow)

    def resid(q):
        p[shoulder]['rot'] = list(q[:3])
        p[elbow]['rot'][0] = q[3]
        end = np.array(point(geo, p, end_bone, end_local))
        el = np.array(point(geo, p, shoulder, piv_e))
        return np.concatenate([end - T, w_pole * (el - E)])

    q = np.array(p[shoulder]['rot'] + [p[elbow]['rot'][0]], float)
    q[3] = min(max(q[3], elbow_range[0]), elbow_range[1])
    r = resid(q)
    cost = float(r @ r)
    lam = 1e-2
    for _ in range(iters):
        J = np.zeros((len(r), 4))
        for i in range(4):
            dq = q.copy()
            dq[i] += 0.25
            J[:, i] = (resid(dq) - r) / 0.25
        A = J.T @ J
        g = J.T @ r
        improved = False
        for _ in range(8):
            step = -np.linalg.solve(A + lam * np.diag(np.diag(A) + 1e-6), g)
            nq = q + np.clip(step, -40, 40)
            nq[3] = min(max(nq[3], elbow_range[0]), elbow_range[1])
            nr = resid(nq)
            nc = float(nr @ nr)
            if nc < cost:
                q, r, cost = nq, nr, nc
                lam = max(lam / 3, 1e-6)
                improved = True
                break
            lam *= 4
        if not improved or cost < 1e-6:
            break
    resid(q)
    for b in (shoulder, elbow):
        p[b]['rot'] = [round(x, 2) for x in p[b]['rot']]
    end = np.array(point(geo, p, end_bone, end_local))
    return p, float(np.linalg.norm(end - T))


def place_hand(geo, pose, side, grip_local, G, R_hand, pole):
    """Puts hand_<side>'s grip_local point at G with world orientation R_hand: solves the arm for the
    wrist, then the wrist rotation. Returns (pose, error px)."""
    arm, fore, hand = 'arm_' + side, 'forearm_' + side, 'hand_' + side
    piv = np.array(pivot_of(geo, hand), float)
    W = np.array(G, float) - R_hand @ (np.array(grip_local, float) - piv)
    p, _ = solve_chain(geo, pose, arm, fore, W, pole)
    Rf = bone_matrix(geo, p, fore)[:3, :3]
    p[hand] = dict(p.get(hand, {}))
    p[hand]['rot'] = list(euler_of(Rf.T @ R_hand))
    err = float(np.linalg.norm(np.array(point(geo, p, hand, grip_local)) - np.array(G, float)))
    return p, err


def reach(geo, pose, chain, end_bone, end_local, target, iters=160, step0=24.0):
    """Back-compat wrapper: two-bone reach of end_local of end_bone onto target (elbow down/out)."""
    shoulder, elbow = chain
    side = -1 if shoulder.endswith('_r') else 1
    return solve_chain(geo, pose, shoulder, elbow, target, (side, -1, 0.3), end_bone=end_bone, end_local=end_local)


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


def orbit(yaw, pitch=12.0):
    """Camera orbiting the model: yaw 0 = front view, positive yaw swings toward the character's left side."""
    a, b = math.radians(yaw), math.radians(pitch)
    ca, sa, cb, sb = math.cos(a), math.sin(a), math.cos(b), math.sin(b)

    def view(p):
        x, y, z = p
        xr = x * ca + z * sa
        zr = -x * sa + z * ca
        return (xr, y * cb + zr * sb, -zr * cb + y * sb)
    return view


VIEW_EXTRA = {'q_front': orbit(35), 'q_left': orbit(70, 8), 'q_back': orbit(215), 'q_right': orbit(-40), 'high': orbit(20, 40)}


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
        view = VIEW_EXTRA[name] if name in VIEW_EXTRA else pg.VIEWS[name]
        pts = [view(c) for quads, *_ in loaded for corners, _ in quads for c in corners] or [(0, 0, 0)]
        minx, maxx = min(p[0] for p in pts), max(p[0] for p in pts)
        miny, maxy = min(p[1] for p in pts), max(p[1] for p in pts)
        w = int((maxx - minx) * scale + 2 * scale)
        h = int((maxy - miny) * scale + 2 * scale)
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
