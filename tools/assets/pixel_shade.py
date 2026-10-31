"""Baked pixel-art shading for the procedural Captain / Avengers models (own work, CC0).

A material (`Mat`) paints base colours + per-texel light offsets through a small painter context; `finish()`
then bakes, per texel:
  * ambient occlusion from the model's own geometry (rest pose, rotations included): creases under belts,
    collars, plates, between locks of hair and at joints darken by themselves;
  * form light: a gentle top-down gradient per part, cylindrical shading on limbs, lit top edges and darker lower
    edges (bevel), a model-wide vertical light;
  * material micro texture (cloth weave, scale mail, leather mottling, brushed metal, hair locks, skin);
  * hue shifting: shadows lean cool (blue / purple, or warm red for skin), lights lean warm;
  * quantisation into clean tone steps with a 2 x 2 ordered dither at the transitions (pixel-art banding, no
    airbrush, no random noise).
Glowing texels are never shaded.
"""
import math

import numpy as np

# ------------------------------------------------------------------------------------------------ transforms


def _rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def _ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def rot_model(r):
    """Rotation of a Bedrock cube / bone in model coordinates (x right, y up, z back), as the game draws it."""
    if not r or not any(r):
        return np.eye(3)
    rx, ry, rz = (math.radians(v) for v in r)
    return _rz(-rz) @ _ry(ry) @ _rx(-rx)


NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0),
           'up': (0, 1, 0), 'down': (0, -1, 0)}


def bone_frames(model):
    by = {b.name: b for b in model.bones}
    cache = {}

    def frame(name):
        if name in cache:
            return cache[name]
        b = by[name]
        R, t = (np.eye(3), np.zeros(3)) if not b.parent or b.parent not in by else frame(b.parent)
        if b.rotation and any(b.rotation):
            p = np.array(b.pivot, float)
            Rl = rot_model(b.rotation)
            R, t = R @ Rl, R @ (p - Rl @ p) + t
        cache[name] = (R, t)
        return cache[name]

    for n in by:
        frame(n)
    return cache


def group_of(bone):
    b = bone.lower()
    for side in ('left', 'right'):
        if side in b:
            if any(k in b for k in ('arm', 'hand', 'finger', 'thumb')):
                return side + '_arm'
            if any(k in b for k in ('leg', 'shin', 'foot')):
                return side + '_leg'
    if any(k in b for k in ('head', 'hair', 'jaw', 'helmet', 'faceplate', 'brow')):
        return 'head'
    if any(k in b for k in ('gun', 'bow', 'arrow', 'hammer', 'shield', 'quiver')):
        return 'prop:' + b
    return 'body'


class Box:
    def __init__(self, cube, R, t):
        inf = cube.inflate or 0.0
        self.cube = cube
        self.lo = np.array(cube.origin, float) - inf
        self.hi = np.array(cube.origin, float) + np.array(cube.size, float) + inf
        Rc = rot_model(cube.rotation)
        pc = np.array(cube.pivot if cube.pivot is not None else cube.origin, float)
        # local (unrotated model coords) -> world:  w = R (Rc (p - pc) + pc) + t
        self.M = R @ Rc
        self.b = R @ (pc - Rc @ pc) + t
        self.Minv = self.M.T
        corners = np.array([[x, y, z] for x in (self.lo[0], self.hi[0]) for y in (self.lo[1], self.hi[1])
                            for z in (self.lo[2], self.hi[2])])
        wc = corners @ self.M.T + self.b
        self.center = wc.mean(axis=0)
        self.radius = float(np.max(np.linalg.norm(wc - self.center, axis=1)))
        self.group = group_of(cube.bone)

    def to_world(self, p):
        return p @ self.M.T + self.b

    def inside(self, q, shrink=0.02):
        local = (q - self.b) @ self.Minv.T
        return np.all((local > self.lo + shrink) & (local < self.hi - shrink), axis=-1)


class Scene:
    def __init__(self, model):
        frames = bone_frames(model)
        self.boxes = []
        self.by_cube = {}
        for b in model.bones:
            R, t = frames[b.name]
            for c in b.cubes:
                if getattr(c, 'no_occlude', False):
                    continue
                bx = Box(c, R, t)
                self.boxes.append(bx)
                self.by_cube[id(c)] = bx
        for b in model.bones:
            R, t = frames[b.name]
            for c in b.cubes:
                if id(c) not in self.by_cube:
                    self.by_cube[id(c)] = Box(c, R, t)
        # meshes drawn together with this one (body + gear): they shade each other too
        for other in getattr(model, 'occluders', ()):
            of = bone_frames(other)
            for b in other.bones:
                R, t = of[b.name]
                for c in b.cubes:
                    if not getattr(c, 'no_occlude', False):
                        self.boxes.append(Box(c, R, t))


def _hemisphere(k=14):
    """Cosine-weighted golden-spiral directions around +Z."""
    out = []
    ga = math.pi * (3 - math.sqrt(5))
    for i in range(k):
        r = math.sqrt((i + 0.5) / k)
        a = i * ga
        x, y = r * math.cos(a), r * math.sin(a)
        out.append((x, y, math.sqrt(max(0.0, 1 - r * r))))
    return np.array(out)


HEMI = _hemisphere(14)
STEPS = np.array([0.3, 0.75, 1.4, 2.3])
STEP_W = np.array([1.0, 0.8, 0.55, 0.35])


def _basis(n):
    a = np.array([0.0, 1.0, 0.0]) if abs(n[1]) < 0.9 else np.array([1.0, 0.0, 0.0])
    t1 = np.cross(n, a)
    t1 /= np.linalg.norm(t1)
    t2 = np.cross(n, t1)
    return np.stack([t1, t2, n])


def face_points(box, name, w, h):
    """World positions (h, w, 3) of the texel centres of a face, its world normal, and the local coords."""
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    u, v = np.meshgrid(u, v)
    (ox, oy, oz), (hx, hy, hz) = box.lo, box.hi
    sx, sy, sz = hx - ox, hy - oy, hz - oz
    if name == 'north':
        p = (ox + u * sx, hy - v * sy, np.full_like(u, oz))
    elif name == 'south':
        p = (hx - u * sx, hy - v * sy, np.full_like(u, hz))
    elif name == 'east':
        p = (np.full_like(u, hx), hy - v * sy, oz + u * sz)
    elif name == 'west':
        p = (np.full_like(u, ox), hy - v * sy, hz - u * sz)
    elif name == 'up':
        p = (ox + u * sx, np.full_like(u, hy), hz - v * sz)
    else:
        p = (ox + u * sx, np.full_like(u, oy), oz + v * sz)
    local = np.stack(p, axis=-1)
    world = box.to_world(local)
    n = box.M @ np.array(NORMALS[name], float)
    return world, n, local


def ambient_occlusion(scene, box, name, w, h, cross=0.35):
    world, n, _ = face_points(box, name, w, h)
    P = world.reshape(-1, 3) + n * 0.03
    dirs = HEMI @ _basis(n)
    reach = STEPS[-1]
    fc = P.mean(axis=0)
    fr = float(np.max(np.linalg.norm(P - fc, axis=1))) + reach
    occ = [b for b in scene.boxes if b is not box and np.linalg.norm(b.center - fc) < b.radius + fr]
    if not occ:
        return np.zeros((h, w))
    Q = P[:, None, None, :] + dirs[None, :, None, :] * STEPS[None, None, :, None]
    hit_same = np.zeros(Q.shape[:3], bool)
    hit_other = np.zeros(Q.shape[:3], bool)
    for b in occ:
        ins = b.inside(Q)
        if b.group == box.group:
            hit_same |= ins
        else:
            hit_other |= ins
    val = np.where(hit_same, 1.0, np.where(hit_other, cross, 0.0))
    ao = (val * STEP_W[None, None, :]).sum(axis=(1, 2)) / (STEP_W.sum() * len(HEMI))
    return np.clip(ao * 1.6, 0, 1).reshape(h, w)


# ------------------------------------------------------------------------------------------------ colour

def hexc(c):
    if isinstance(c, str):
        c = c.lstrip('#')
        return np.array([int(c[i:i + 2], 16) for i in (0, 2, 4)], float)
    return np.array(c[:3], float)


COOL = np.array([0.80, 0.86, 1.12])
WARM = np.array([1.07, 1.03, 0.90])
SKIN_SHADOW = np.array([1.02, 0.84, 0.86])


SPEC = np.array([255.0, 246.0, 226.0])


def shade(rgb, L, cool=COOL, warm=WARM, hue=1.0, spec=0.0):
    k = (L - 1.0)[..., None]
    out = rgb * L[..., None]
    if spec:
        out = out + np.clip(k, 0, None) * spec * SPEC     # highlights lift towards a warm white (dark metals shine)
    td = np.clip(-k * 2.2 * hue, 0, 1)
    tl = np.clip(k * 2.6 * hue, 0, 1)
    out = out * (1 + (cool - 1) * td) * (1 + (warm - 1) * tl)
    return out


BAYER2 = np.array([[-0.375, 0.125], [0.375, -0.125]])


def quantise(L, step):
    if not step:
        return L
    h, w = L.shape
    d = np.tile(BAYER2, (h // 2 + 1, w // 2 + 1))[:h, :w]
    return 1.0 + step * np.round((L - 1.0) / step + d * 0.9)


def smooth_noise(rs, h, w, cell=3.0, amp=1.0):
    gh, gw = int(h / cell) + 2, int(w / cell) + 2
    g = rs.uniform(-1, 1, (gh, gw))
    y = np.linspace(0, gh - 1.001, h)
    x = np.linspace(0, gw - 1.001, w)
    y0, x0 = np.floor(y).astype(int), np.floor(x).astype(int)
    fy, fx = (y - y0)[:, None], (x - x0)[None, :]
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    return amp * ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy)


# ------------------------------------------------------------------------------------------------ material

class Ctx:
    """Painter context of one face: base colours, light offsets, glow, alpha and coordinates."""

    def __init__(self, face, mat, rs):
        self.face, self.mat, self.rs = face, mat, rs
        self.name, self.w, self.h = face.name, face.w, face.h
        self.u, self.v = face.grid()
        self.x, self.y, self.z = face.world()
        self.rgb = np.tile(hexc(mat.base), (self.h, self.w, 1))
        self.L = np.zeros((self.h, self.w))
        self.glow = np.zeros((self.h, self.w, 4))
        self.alpha = np.full((self.h, self.w), 255, np.uint8)
        self.flat = np.zeros((self.h, self.w), bool)   # texels kept exactly as painted (no shading)

    @property
    def side(self):
        return self.name in ('north', 'south', 'east', 'west')

    def put(self, mask, colour, amount=1.0):
        c = hexc(colour)
        if isinstance(mask, tuple) or isinstance(mask, slice):
            self.rgb[mask] = self.rgb[mask] * (1 - amount) + c * amount
        else:
            m = np.asarray(mask, bool)
            self.rgb[m] = self.rgb[m] * (1 - amount) + c * amount

    def light(self, mask, delta):
        if isinstance(mask, tuple) or isinstance(mask, slice):
            self.L[mask] += delta
        else:
            self.L[np.asarray(mask, bool)] += delta

    def emit(self, mask, colour, a=255):
        c = hexc(colour)
        m = np.asarray(mask, bool) if not isinstance(mask, (tuple, slice)) else mask
        self.glow[m] = np.array([c[0], c[1], c[2], a], float)

    def all(self):
        return np.ones((self.h, self.w), bool)

    def pixels(self, rows, pal, x0=0, y0=0):
        """Pixel map (list of strings) at texel offset (x0, y0); '.' / unknown characters are skipped."""
        for r, line in enumerate(rows):
            for c, ch in enumerate(line):
                col = pal.get(ch)
                if col is None:
                    continue
                yy, xx = y0 + r, x0 + c
                if 0 <= yy < self.h and 0 <= xx < self.w:
                    if isinstance(col, tuple):          # (colour, light offset)
                        self.rgb[yy, xx] = hexc(col[0])
                        self.L[yy, xx] += col[1]
                    else:
                        self.rgb[yy, xx] = hexc(col)


KIND = {
    #            grad   cyl   edge_hi edge_lo edge_side  micro     spec
    'cloth':   (0.16, 0.16, 0.07, 0.10, 0.05, 'weave',   0.10),
    'suit':    (0.16, 0.18, 0.09, 0.10, 0.05, 'scale',   0.22),
    'leather': (0.16, 0.16, 0.10, 0.12, 0.06, 'mottle',  0.28),
    'metal':   (0.22, 0.20, 0.18, 0.14, 0.08, 'brushed', 0.60),
    'skin':    (0.12, 0.16, 0.04, 0.07, 0.04, 'skin',    0.08),
    'hair':    (0.20, 0.10, 0.06, 0.10, 0.03, 'hair',    0.30),
    'paint':   (0.14, 0.10, 0.10, 0.10, 0.05, 'paint',   0.40),
    'rubber':  (0.08, 0.08, 0.05, 0.08, 0.03, None,      0.10),
    'glass':   (0.14, 0.00, 0.14, 0.00, 0.00, None,      0.70),
    'flat':    (0.00, 0.00, 0.00, 0.00, 0.00, None,      0.00),
}


class Mat:
    """Shaded material. base: colour; kind: KIND key; fn(ctx): painter (regions, details, light offsets);
    ao: occlusion strength; hue: hue-shift strength; quant: tone step (0 = smooth); cool/warm: shadow/light tints;
    sheen: world y of a hair highlight band; alpha_fn(face, rs) as in clean_geo.Flat; glow_fn(ctx)."""
    shaded = True

    def __init__(self, base, kind='cloth', fn=None, ao=0.5, hue=1.0, quant=0.05, cool=None, warm=None,
                 alpha_fn=None, sheen=None, edges=True, micro=1.0, light=True, grad=None, cyl=None, spec=None):
        self.base, self.kind, self.fn = base, kind, fn
        self.ao, self.hue, self.quant = ao, hue, quant
        self.cool = COOL if cool is None else np.array(cool)
        self.warm = WARM if warm is None else np.array(warm)
        if kind == 'skin' and cool is None:
            self.cool = SKIN_SHADOW
        self.alpha_fn, self.sheen, self.edges, self.micro, self.light = alpha_fn, sheen, edges, micro, light
        g, c, eh, el, es, mk, sp = KIND[kind]
        self.grad = g if grad is None else grad
        self.cyl = c if cyl is None else cyl
        self.spec = sp if spec is None else spec
        self.edge_hi, self.edge_lo, self.edge_side, self.micro_kind = eh, el, es, mk

    def paint(self, face, rng):
        rs = np.random.RandomState(rng.randrange(1 << 30))
        ctx = Ctx(face, self, rs)
        if self.fn is not None:
            self.fn(ctx)
        if self.alpha_fn is not None:
            ctx.alpha = self.alpha_fn(face, rs)
        return ctx


def micro(ctx, kind, amt):
    h, w = ctx.h, ctx.w
    if kind == 'weave':
        chk = (np.add.outer(np.arange(h), np.arange(w)) % 2) * 2 - 1
        ctx.L += amt * (0.012 * chk + smooth_noise(ctx.rs, h, w, 3.5, 0.018))
    elif kind == 'scale':
        rr, cc = np.mgrid[0:h, 0:w]
        off = (rr // 2) % 2
        cell_r, cell_c = rr % 2, (cc + off) % 2
        pat = np.where((cell_r == 1) & (cell_c == 1), -0.05, np.where((cell_r == 0) & (cell_c == 0), 0.025, 0.0))
        ctx.L += amt * (pat + smooth_noise(ctx.rs, h, w, 4.0, 0.012))
    elif kind == 'mottle':
        ctx.L += amt * smooth_noise(ctx.rs, h, w, 2.5, 0.04)
        spots = ctx.rs.uniform(0, 1, (h, w)) > 0.985
        ctx.L[spots] += amt * 0.05
    elif kind == 'brushed':
        if w >= h:
            ctx.L += amt * ctx.rs.uniform(-0.02, 0.02, (h, 1))
        else:
            ctx.L += amt * ctx.rs.uniform(-0.02, 0.02, (1, w))
        if ctx.side and h >= 4:
            band = (ctx.v > 0.14) & (ctx.v < 0.26)
            ctx.L[band] += amt * 0.07
    elif kind == 'skin':
        ctx.L += amt * smooth_noise(ctx.rs, h, w, 3.0, 0.01)
    elif kind == 'paint':
        ctx.L += amt * smooth_noise(ctx.rs, h, w, 5.0, 0.014)
    elif kind == 'hair':
        hair_locks(ctx, amt)


def hair_locks(ctx, amt):
    """Locks of hair: columns grouped 2-4 texels wide with their own tone, dark partings between them,
    darker roots under the crown, lighter tips, a wavy sheen band; on caps the locks run front to back."""
    h, w = ctx.h, ctx.w
    along_u = ctx.name in ('up', 'down')
    n = h if along_u else w
    tone = np.zeros(n)
    edge = np.zeros(n, bool)
    i = 0
    while i < n:
        lw = int(ctx.rs.choice([2, 2, 3, 3, 4]))
        tone[i:i + lw] = ctx.rs.uniform(-0.07, 0.08)
        if lw >= 3:
            tone[i] += 0.04                     # each lock is lit on one side ...
            tone[min(n - 1, i + lw - 1)] -= 0.05  # ... and shaded on the other
        edge[min(n - 1, i + lw - 1)] = lw >= 2 and i + lw < n
        i += lw
    t = tone[:, None] if along_u else tone[None, :]
    e = edge[:, None] if along_u else edge[None, :]
    jitter = smooth_noise(ctx.rs, h, w, 2.0, 0.03)
    ctx.L += amt * (t + jitter)
    ctx.L += amt * np.where(np.broadcast_to(e, (h, w)), -0.12, 0.0)
    if ctx.side and h >= 4:
        ctx.L[-1:, :] += amt * 0.06          # tips catch light
    sheen = ctx.mat.sheen
    if sheen is not None and ctx.side:
        coord = ctx.u * ctx.w
        wave = sheen + 0.45 * np.sin(coord * 1.1 + ctx.rs.uniform(0, 6.28))
        band = np.abs(ctx.y - wave) < 0.4
        ctx.L[band] += amt * 0.13
        ctx.L[np.abs(ctx.y - wave + 0.6) < 0.25] += amt * 0.05


def finish(model, scene, cube, ctx):
    mat = ctx.mat
    face = ctx.face
    h, w = ctx.h, ctx.w
    box = scene.by_cube[id(cube)]
    ctx.L += 1.0                       # painters wrote offsets; shading works on a brightness multiplier
    L = ctx.L
    # form light
    if mat.light:
        _, n, local = face_points(box, face.name, w, h)
        y = local[..., 1]
        y0, y1 = box.lo[1], box.hi[1]
        if ctx.side:
            rel = (y - y0) / max(1e-6, (y1 - y0))
            L += mat.grad * (rel - 0.5)
            if mat.cyl:
                L += mat.cyl * (0.5 - np.abs(ctx.u - 0.5)) * 2 - mat.cyl * 0.5
        elif face.name == 'up':
            L += mat.grad * 0.5
        else:
            L -= mat.grad * 0.5 + 0.04
        if model.light:
            yt, yb, gain = model.light
            wy = box.to_world(local)[..., 1]
            f = np.clip((wy - yb) / max(1e-6, yt - yb), 0, 1)
            L += gain * (2 * f - 1)
    # bevel edges
    if mat.edges and h >= 3 and w >= 3:
        if ctx.side:
            L[0, :] += mat.edge_hi
            L[-1, :] -= mat.edge_lo
            L[:, 0] -= mat.edge_side
            L[:, -1] -= mat.edge_side
        elif face.name == 'up':
            L[0, :] += mat.edge_hi * 0.6
            L[-1, :] += mat.edge_hi * 0.6
            L[:, 0] += mat.edge_hi * 0.4
            L[:, -1] += mat.edge_hi * 0.4
    if mat.micro_kind and mat.micro:
        micro(ctx, mat.micro_kind, mat.micro)
    # occlusion
    if mat.ao:
        ao = ambient_occlusion(scene, box, face.name, w, h)
        L -= mat.ao * ao
    L = quantise(L, mat.quant)
    L = np.clip(L, 0.35, 1.45)
    out = shade(ctx.rgb, L, mat.cool, mat.warm, mat.hue, mat.spec)
    keep = ctx.flat | (ctx.glow[..., 3] > 0)
    out[keep] = ctx.rgb[keep]
    col = np.zeros((h, w, 4), np.uint8)
    col[..., :3] = np.clip(out, 0, 255).astype(np.uint8)
    col[..., 3] = ctx.alpha
    gl = np.clip(ctx.glow, 0, 255).astype(np.uint8)
    return col, gl
