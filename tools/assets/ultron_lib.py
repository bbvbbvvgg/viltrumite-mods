"""Shared kit for the Ultron uprising assets (scout drone, combat robot, heavy assault robot, flying
interceptor, engineer, Ultron Prime, network nodes, missile). Own work, CC0.

Builds on jinwoo_geo (Bedrock geo + auto-packed procedural atlas + clips) and chitauri_lib (forward
kinematics, clip sampling, posed previews). Adds the Ultron palette and machined-metal materials:
polished chrome and brushed steel with a lit top band, a soft specular streak, anisotropic brushing,
bevelled plate edges, panel seams and bolts, dark gunmetal internals with ribbed cable, and the red
emissive set (eyes, mouth light, cores, vents, lenses) painted into a separate glow atlas.
Units are model pixels, front -Z, the character's right side -X (preview front view shows the right
side on screen-left, as a player facing the machine sees it).
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
from jinwoo_geo import Mat, Model, Clip, write_anims, put, hexc, RES, BIN, ROOT  # noqa: E402,F401
import chitauri_lib as CL  # noqa: E402,F401

# ---- palette --------------------------------------------------------------------------------------------
CHROME = '#a7afba'
CHROME_LIGHT = '#c9d0d9'
CHROME_DARK = '#737b87'
STEEL = '#7d8590'
STEEL_DARK = '#555c66'
TITAN = '#a39e98'          # warm titanium accents (Prime's face plates)
GUN = '#34383f'
GUN_DARK = '#202328'
GUN_LIGHT = '#4b5059'
BLACK = '#141518'
HAZARD = '#c8962e'         # engineer tool markings (paint, not light)
RED = (255, 44, 26)
RED_HOT = (255, 196, 160)
RED_DIM = (150, 22, 12)
AMBER = (255, 156, 48)
AMBER_HOT = (255, 226, 170)


def _streaks(rgb, rs, axis, amount):
    """Anisotropic brushing: every row (axis 0) or column (axis 1) slightly brighter or darker."""
    h, w = rgb.shape[:2]
    if axis == 0:
        m = rs.uniform(1 - amount, 1 + amount, (h, 1))
    else:
        m = rs.uniform(1 - amount, 1 + amount, (1, w))
    rgb *= m[..., None]


def _bevel(rgb, light=34.0, dark=0.66):
    """Machined edge: lit top row and left column, shadowed bottom row and right column."""
    h, w = rgb.shape[:2]
    if h < 3 or w < 3:
        return
    rgb[0, :] += light
    rgb[:, 0] += light * 0.45
    rgb[-1, :] *= dark
    rgb[:, -1] *= dark + (1 - dark) * 0.4


def metal_fn(spec=1.0, streak=0.035, bevel=True, seams=0, bolts=False, scuffs=2, grime=0.1, band=0.24, sheen=0.55, contrast=1.0):
    """Polished / brushed metal plate (see the module comment). Side faces get the stylised chrome profile: a
    bright sky reflection at the top, a sharp specular streak (band: 0 top .. 1 bottom), a dark horizon
    reflection below the middle and a faint ground bounce at the bottom; contrast scales the profile (1 chrome,
    ~0.5 brushed steel), spec the highlights, sheen a faint diagonal glint."""
    def fn(face, rgb, glow, rs):
        h, w = rgb.shape[:2]
        u, v = face.grid()
        cap = face.name in ('up', 'down')
        if not cap:
            prof = (1.0 + contrast * (0.26 * np.exp(-((v - 0.08) / 0.1) ** 2)
                                      - 0.42 * np.exp(-((v - 0.64) / 0.15) ** 2)
                                      + 0.1 * np.clip((v - 0.86) / 0.14, 0, 1)))
            rgb *= prof[..., None]
            rgb += (np.exp(-((v - band) / 0.07) ** 2) * 70.0 * spec)[..., None]
            diag = np.exp(-(((u + v) * 0.5 - 0.35 - rs.uniform(-0.1, 0.1)) / 0.06) ** 2)
            rgb += (diag * 28.0 * sheen * spec)[..., None]
            rgb *= (1.0 - grime * np.clip((v - 0.8) / 0.2, 0, 1))[..., None]
            _streaks(rgb, rs, 0, streak)
        else:
            prof = 1.0 + contrast * (0.22 * np.exp(-((u - 0.3) / 0.18) ** 2) - 0.25 * np.exp(-((u - 0.72) / 0.16) ** 2))
            rgb *= prof[..., None]
            _streaks(rgb, rs, 1, streak)
        if seams and w >= 6 and h >= 6:
            for _ in range(seams):
                if rs.rand() < 0.5 or cap:
                    x = rs.randint(2, w - 2)
                    rgb[:, x] *= 0.5
                    if x + 1 < w:
                        rgb[:, x + 1] += 18
                else:
                    y = rs.randint(2, h - 2)
                    rgb[y, :] *= 0.5
                    if y + 1 < h:
                        rgb[y + 1, :] += 16
        if bolts and w >= 6 and h >= 6:
            for (yy, xx) in ((1, 1), (1, w - 2), (h - 2, 1), (h - 2, w - 2)):
                rgb[yy, xx] = rgb[yy, xx] * 0.45
                if yy > 0:
                    rgb[yy - 1, xx] += 30
        for _ in range(scuffs):
            if w < 4 or h < 3:
                break
            x, y = rs.randint(0, w), rs.randint(0, h)
            n = rs.randint(2, max(3, min(w, 6)))
            for i in range(n):
                xx = x + i
                if xx < w:
                    rgb[y, xx] *= 1.16
        if bevel:
            _bevel(rgb)
    return fn


def gun_fn(ribs=False, lights=0, colour=RED):
    """Dark gunmetal internals: fine machining lines, optional cable ribbing and tiny status lights."""
    def fn(face, rgb, glow, rs):
        h, w = rgb.shape[:2]
        u, v = face.grid()
        if face.name not in ('up', 'down'):
            rgb *= (1.08 - 0.18 * v)[..., None]
            rgb += (np.exp(-((v - 0.3) / 0.15) ** 2) * 16.0)[..., None]
        if ribs and face.name not in ('up', 'down'):
            for y in range(h):
                if y % 2 == 0:
                    rgb[y, :] *= 1.32
                else:
                    rgb[y, :] *= 0.8
        elif w >= 5:
            rgb[:, w // 2] *= 0.8
        if lights and glow is not None and face.name not in ('up', 'down') and w >= 3 and h >= 3:
            for _ in range(lights):
                x, y = rs.randint(1, w - 1), rs.randint(1, h - 1)
                glow[y, x] = (*colour, 255)
                rgb[y, x] = colour
        _streaks(rgb, rs, 0, 0.03)
    return fn


def cable_fn(face, rgb, glow, rs):
    """Braided cable bundle: diagonal braid across the length."""
    h, w = rgb.shape[:2]
    if face.name in ('up', 'down'):
        return
    for y in range(h):
        for x in range(w):
            if (x + y) % 3 == 0:
                rgb[y, x] *= 1.35
            elif (x - y) % 4 == 0:
                rgb[y, x] *= 0.75


def vent_fn(glowing=False, colour=RED):
    """Horizontal vent slats (dark gaps), the gaps glow when glowing."""
    def fn(face, rgb, glow, rs):
        h, w = rgb.shape[:2]
        if face.name in ('up', 'down'):
            return
        for y in range(h):
            if y % 2 == 1:
                rgb[y, 1:w - 1] *= 0.35
                if glowing and glow is not None and 0 < y < h - 1:
                    glow[y, 1:w - 1] = (*colour, 210)
                    rgb[y, 1:w - 1] = np.array(colour, float) * 0.4
            else:
                rgb[y, :] *= 1.15
    return fn


def glow_fn(colour=RED, hot=RED_HOT, core=0.25, alpha=255, shape='box', faces=None, base=0.45):
    """Emissive cell: a hot core fading to saturated colour at the rim. shape: 'box' (square falloff), 'slit'
    (horizontal), 'round' (radial), 'vslit' (vertical). faces limits which faces glow (others stay dark)."""
    def fn(face, rgb, glow, rs):
        u, v = face.grid()
        if faces is not None and face.name not in faces:
            rgb *= 0.25
            return
        if shape == 'slit':
            d = np.abs(v - 0.5) * 2.0 * 0.85 + np.abs(u - 0.5) * 0.35
        elif shape == 'vslit':
            d = np.abs(u - 0.5) * 2.0 * 0.85 + np.abs(v - 0.5) * 0.35
        elif shape == 'round':
            d = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2) * 2.0
        else:
            d = np.maximum(np.abs(u - 0.5), np.abs(v - 0.5)) * 2.0
        t = np.clip((d - core) / max(1e-3, 1.0 - core), 0, 1)[..., None] ** 0.75
        col = np.array(hot, float) * (1 - t) + np.array(colour, float) * t
        rgb[...] = col * base
        if glow is not None:
            glow[..., :3] = col
            a = np.clip(1.15 - d, 0.35, 1.0) if shape == 'round' else np.ones_like(d)
            glow[..., 3] = alpha * a
    return fn


def lens_fn(colour=RED, hot=RED_HOT, rings=2):
    """Optic lens: dark glass with concentric iris rings and a hot pupil; a white catch light."""
    def fn(face, rgb, glow, rs):
        u, v = face.grid()
        if face.name not in ('north',):
            rgb *= 0.35
            return
        r = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2) * 2.0
        rgb[...] = (24, 18, 20)
        col = np.zeros(rgb.shape, float)
        a = np.zeros(r.shape, float)
        pupil = r < 0.32
        col[pupil] = hot
        a[pupil] = 255
        for k in range(rings):
            rr = 0.5 + 0.22 * k
            ring = np.abs(r - rr) < 0.09
            col[ring] = colour
            a[ring] = 200 - 60 * k
        mid = (r >= 0.32) & (r < 0.45)
        col[mid] = np.array(colour, float) * 0.8
        a[mid] = 230
        rgb[...] = np.where(a[..., None] > 0, col * 0.5, rgb)
        if glow is not None:
            glow[..., :3] = col
            glow[..., 3] = a
        h, w = rgb.shape[:2]
        if h >= 4 and w >= 4:
            rgb[h // 4, w // 4] = (235, 235, 240)
    return fn


def mats(density_spec=1.0):
    """The Ultron material set (names used by every model script)."""
    s = density_spec
    return {
        'chrome': Mat(CHROME, noise=5, edge=0.12, top=1.18, bottom=0.66, fn=metal_fn(spec=1.1 * s)),
        'chrome_seam': Mat(CHROME, noise=5, edge=0.12, top=1.18, bottom=0.66, fn=metal_fn(spec=1.1 * s, seams=2)),
        'chrome_bolt': Mat(CHROME, noise=5, edge=0.12, top=1.18, bottom=0.66, fn=metal_fn(spec=1.0 * s, seams=1, bolts=True)),
        'chrome_light': Mat(CHROME_LIGHT, noise=4, edge=0.1, top=1.15, bottom=0.7, fn=metal_fn(spec=1.25 * s, scuffs=1)),
        'chrome_dark': Mat(CHROME_DARK, noise=5, edge=0.14, top=1.15, bottom=0.66, fn=metal_fn(spec=0.75 * s, seams=1, contrast=0.85)),
        'steel': Mat(STEEL, noise=6, edge=0.14, top=1.15, bottom=0.66, fn=metal_fn(spec=0.6 * s, streak=0.06, seams=1, contrast=0.5)),
        'steel_bolt': Mat(STEEL, noise=6, edge=0.14, top=1.15, bottom=0.66, fn=metal_fn(spec=0.6 * s, streak=0.06, bolts=True, seams=1, contrast=0.5)),
        'steel_dark': Mat(STEEL_DARK, noise=6, edge=0.16, top=1.12, bottom=0.66, fn=metal_fn(spec=0.45 * s, streak=0.06, seams=1, scuffs=3, contrast=0.5)),
        'titan': Mat(TITAN, noise=4, edge=0.12, top=1.16, bottom=0.68, fn=metal_fn(spec=1.0 * s, scuffs=1)),
        'gun': Mat(GUN, noise=5, edge=0.16, fn=gun_fn()),
        'gun_dark': Mat(GUN_DARK, noise=4, edge=0.14, fn=gun_fn()),
        'gun_light': Mat(GUN_LIGHT, noise=5, edge=0.16, fn=gun_fn()),
        'gun_lit': Mat(GUN, noise=5, edge=0.16, fn=gun_fn(lights=1)),
        'gun_amber': Mat(GUN, noise=5, edge=0.16, fn=gun_fn(lights=1, colour=AMBER)),
        'ribs': Mat(GUN, noise=3, edge=0.08, fn=gun_fn(ribs=True)),
        'cable': Mat('#2a2d33', noise=3, edge=0.06, fn=cable_fn),
        'black': Mat(BLACK, noise=3, edge=0.1, fn=gun_fn()),
        'vent': Mat(GUN_LIGHT, noise=3, edge=0.1, fn=vent_fn()),
        'vent_glow': Mat(GUN, noise=3, edge=0.1, fn=vent_fn(glowing=True)),
        'hazard': Mat(HAZARD, noise=6, edge=0.16, top=1.1, bottom=0.7, fn=metal_fn(spec=0.6, streak=0.05, scuffs=4)),
        'eye': Mat('#ff3a22', noise=0, edge=0, fn=glow_fn(colour=(255, 34, 18), hot=(255, 150, 118), shape='slit', core=0.0)),
        'eye_hot': Mat('#ff3a22', noise=0, edge=0, fn=glow_fn(colour=(255, 34, 18), hot=(255, 205, 175), shape='slit', core=0.12)),
        'eye_round': Mat('#ff3a22', noise=0, edge=0, fn=glow_fn(shape='round', core=0.2)),
        'mouth': Mat('#d0281a', noise=0, edge=0, fn=glow_fn(colour=(196, 26, 16), hot=(255, 96, 64), shape='slit', core=0.0, alpha=220, base=0.3)),
        'core': Mat('#ff3a22', noise=0, edge=0, fn=glow_fn(shape='round', core=0.25)),
        'core_box': Mat('#ff3a22', noise=0, edge=0, fn=glow_fn(shape='box', core=0.3)),
        'glow_soft': Mat('#c82818', noise=0, edge=0, fn=glow_fn(shape='box', core=0.0, alpha=190, base=0.35)),
        'thruster': Mat('#ff5a2a', noise=0, edge=0, fn=glow_fn(colour=(255, 90, 40), hot=(255, 230, 200), shape='round', core=0.3,
                                                                faces=('down', 'south', 'north', 'up'))),
        'flame': Mat('#ff6a30', noise=0, edge=0, fn=glow_fn(colour=(255, 84, 36), hot=(255, 236, 210), shape='box', core=0.2, alpha=235, base=0.5)),
        'amber': Mat('#ff9c30', noise=0, edge=0, fn=glow_fn(colour=AMBER, hot=AMBER_HOT, shape='round', core=0.25)),
        'amber_slit': Mat('#ff9c30', noise=0, edge=0, fn=glow_fn(colour=AMBER, hot=AMBER_HOT, shape='slit', core=0.15)),
        'lens': Mat('#181214', noise=0, edge=0, fn=lens_fn()),
        'lens_amber': Mat('#181214', noise=0, edge=0, fn=lens_fn(colour=AMBER, hot=AMBER_HOT, rings=1)),
    }


class Builder:
    """Thin geometry helper over a jinwoo_geo Model: boxes from two corners, mirrored pairs, plates with a
    bevel lip, octagonal prisms (round parts out of three rotated boxes) and telescoping pistons."""

    def __init__(self, model, materials):
        self.m = model
        self.M = materials

    def bone(self, name, parent, pivot, rotation=None):
        return self.m.bone(name, parent, pivot, rotation)

    def box(self, bone, a, b, mat, **kw):
        x0, y0, z0 = a
        x1, y1, z1 = b
        return self.m.cube(bone, [min(x0, x1), min(y0, y1), min(z0, z1)], [abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)],
                           self.M[mat] if isinstance(mat, str) else mat, **kw)

    def rbox(self, bone, a, b, mat, rot, pivot=None):
        """Rotated box (rotation about pivot, default the box centre)."""
        if pivot is None:
            pivot = [(a[i] + b[i]) / 2 for i in range(3)]
        return self.box(bone, a, b, mat, rotation=list(rot), pivot=list(pivot))

    def oct(self, bone, centre, radius, length, axis, mat, cap_mat=None):
        """Octagonal prism around an axis ('x', 'y' or 'z'): one square box plus one turned 45 degrees."""
        cx, cy, cz = centre
        r = radius
        s = r * 0.92
        half = length / 2.0
        if axis == 'y':
            a, b = (cx - s, cy - half, cz - s), (cx + s, cy + half, cz + s)
            rot = [0, 45, 0]
        elif axis == 'x':
            a, b = (cx - half, cy - s, cz - s), (cx + half, cy + s, cz + s)
            rot = [45, 0, 0]
        else:
            a, b = (cx - s, cy - s, cz - half), (cx + s, cy + s, cz + half)
            rot = [0, 0, 45]
        self.box(bone, a, b, mat)
        self.rbox(bone, a, b, cap_mat or mat, rot, pivot=centre)

    def piston(self, bone_a, bone_b, top, bottom, r=0.5, mat_a='steel_dark', mat_b='chrome_light'):
        """Vertical piston: a thick cylinder on bone_a (upper), a thin polished rod on bone_b (lower) that slides
        inside it as the bones move apart."""
        x, y0, z = top
        _, y1, _ = bottom
        mid = (y0 + y1) / 2.0
        self.box(bone_a, (x - r, mid, z - r), (x + r, y0, z + r), mat_a)
        rr = r * 0.55
        self.box(bone_b, (x - rr, y1, z - rr), (x + rr, mid + (y0 - mid) * 0.7, z + rr), mat_b)


def finger_chain(b, side, hand_bone, base, names, lengths, width, mat='chrome', joint='gun', spread=0.0, down=(0, -1, 0)):
    """A jointed finger hanging from the hand: segments along -Y from base (x, y, z), each its own bone."""
    parent = hand_bone
    x, y, z = base
    out = []
    for i, (name, ln) in enumerate(zip(names, lengths)):
        bn = name
        b.bone(bn, parent, [x, y, z], [0, 0, spread] if (i == 0 and spread) else None)
        w = width * (0.92 ** i)
        b.box(bn, (x - w / 2, y - ln, z - w / 2), (x + w / 2, y, z + w / 2), mat)
        b.box(bn, (x - w / 2 - 0.05, y - 0.35, z - w / 2 - 0.05), (x + w / 2 + 0.05, y + 0.05, z + w / 2 + 0.05), joint)
        out.append(bn)
        parent = bn
        y -= ln
    return out


def smooth_t(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def ease_in(t):
    t = max(0.0, min(1.0, t))
    return t * t * t


def ease_out(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


def add(*poses):
    """Sums rotation/position poses bone by bone (tuples or {'rot','pos'} dicts)."""
    out = {}
    for p in poses:
        for k, v in p.items():
            spec = v if isinstance(v, dict) else {'rot': v}
            cur = out.setdefault(k, {})
            for ch, val in spec.items():
                if ch == 'scale':
                    cur[ch] = list(val)
                    continue
                base = cur.get(ch, [0.0, 0.0, 0.0])
                cur[ch] = [a + c for a, c in zip(base, val)]
    return {k: ({'rot': tuple(v['rot'])} if set(v) == {'rot'} else {kk: tuple(vv) for kk, vv in v.items()}) for k, v in out.items()}


def scale_pose(p, f):
    out = {}
    for k, v in p.items():
        spec = v if isinstance(v, dict) else {'rot': v}
        out[k] = {ch: tuple(x * f for x in val) if ch != 'scale' else tuple(val) for ch, val in spec.items()}
    return out


def blend(a, b, f):
    """Blends two poses (missing channels count as zero)."""
    return add(scale_pose(a, 1 - f), scale_pose(b, f))


def keys(clip, times_poses):
    for t, p in times_poses:
        clip.key(round(t, 4), p)
    return clip


# ---- push-size limits: one GitHub push request carries ~60 KB, so big assets are stored in parts --------------
PART_LIMIT = 54000      # base64 text (no escaping in the push request)
JSON_PART_LIMIT = 44000  # JSON grows ~20 % when escaped into the push request


def _parts(base):
    import glob
    return sorted(glob.glob(base + '.part*'), key=lambda f: int(f.rsplit('.part', 1)[1]))


def _write_parts(base, text, limit=PART_LIMIT):
    for f in _parts(base):
        os.remove(f)
    chunks, cur = [], 0
    while cur < len(text):
        end = min(len(text), cur + limit)
        if end < len(text):
            cut = max(text.rfind('\n', cur, end), text.rfind('},', cur, end))
            if cut > cur + limit // 2:
                end = cut + (1 if text[cut] == '\n' else 2)
        chunks.append(text[cur:end])
        cur = end
    os.makedirs(os.path.dirname(base), exist_ok=True)
    for i, ch in enumerate(chunks):
        with open('%s.part%d' % (base, i), 'w', encoding='utf-8') as fh:
            fh.write(ch)
    return len(chunks)


def finalize_assets(json_rels=(), tex_rels=()):
    """Moves resource JSON bigger than PART_LIMIT into binassets as plain .partN chunks (joined by Gradle) and
    splits big texture .b64 files into .b64.partN; small files stay whole and stale parts are removed."""
    for rel in json_rels:
        src = os.path.join(RES, rel)
        dst = os.path.join(BIN, rel)
        if not os.path.exists(src):
            continue
        text = open(src, encoding='utf-8').read()
        if len(text.encode('utf-8')) > JSON_PART_LIMIT:
            _write_parts(dst, text, JSON_PART_LIMIT)
            os.remove(src)
        else:
            for f in _parts(dst):
                os.remove(f)
    for rel in tex_rels:
        if not rel:
            continue
        b64 = os.path.join(BIN, rel + '.b64')
        if not os.path.exists(b64):
            continue
        text = open(b64, encoding='ascii').read()
        if len(text) > PART_LIMIT:
            _write_parts(b64, text)
            os.remove(b64)
        else:
            for f in _parts(b64):
                os.remove(f)


def load_json(rel):
    """Reads a resource JSON whole or joined from its binassets parts."""
    import json as _json
    src = os.path.join(RES, rel)
    if os.path.exists(src):
        return _json.load(open(src, encoding='utf-8'))
    return _json.loads(''.join(open(f, encoding='utf-8').read() for f in _parts(os.path.join(BIN, rel))))


def preview_all(name, geo_path, tex, glow, clips, out_dir=None, frac=0.35, extra=()):
    """Rest views + one panel per clip (sampled at frac or at its contact) into build/ultron_preview."""
    out = out_dir or os.path.join(ROOT, 'build', 'ultron_preview')
    geo = load_json(geo_path)
    texp = os.path.join(ROOT, 'build', 'jinwoo_preview', tex)
    glowp = os.path.join(ROOT, 'build', 'jinwoo_preview', glow) if glow else None
    CL.preview(os.path.join(out, '%s_rest.png' % name), [(geo, {}, texp, glowp)], views=('front', 'q_front', 'side', 'q_back', 'back'), scale=8)
    ims = []
    for c in clips:
        t = getattr(c, 'review', None)
        t = c.length * frac if t is None else t
        pose = CL.sample_clip(c, t)
        ims.append(CL.preview(os.path.join(out, '%s_%s.png' % (name, c.name)), [(geo, pose, texp, glowp)], views=('q_front', 'side'), scale=5))
    if ims:
        CL.sheet(os.path.join(out, '%s_clips.png' % name), ims, cols=5)


# ---- clips with per-key easing --------------------------------------------------------------------------
class UClip(Clip):
    """Clip whose keys may carry an easing for the segment that starts at them (Bedrock 'easing', read by
    AnimationParser): anticipation eases out into the wind-up, the strike eases in (accelerates) into the
    contact frame, the follow-through eases out, the recovery eases in-out. Keys without an easing use the
    clip mode (monotone cubic: smooth, no overshoot). review: the preview sample time."""

    def __init__(self, name, length, loop=False, mode='monotone', review=None):
        super().__init__(name, length, loop)
        self.mode = mode
        self.eases = {}
        self.review = review

    def key(self, t, pose, ease=None):
        t = round(t, 4)
        self.frames.append((t, pose))
        if ease:
            self.eases[t] = ease
        return self

    def to_json(self):
        from jinwoo_geo import r1
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
                vals = [[r1(round(v, 2)) for v in kf[tt]] for tt in times]
                if all(v == vals[0] for v in vals):
                    zero = key == 'rotation' and all(x == 0 for x in vals[0]) or key == 'position' and all(x == 0 for x in vals[0]) \
                        or key == 'scale' and all(x == 1 for x in vals[0])
                    if zero and not self.loop and bone not in self.keep:
                        continue
                    jb[key] = vals[0]
                    continue
                d = {}
                for i, tt in enumerate(times):
                    e = self.eases.get(tt)
                    if e:
                        d['%g' % tt] = {'post': vals[i], 'easing': e}
                    else:
                        d['%g' % tt] = {'post': vals[i], 'lerp_mode': self.mode}
                jb[key] = d
            if jb:
                out_b[bone] = jb
        j = {'animation_length': round(self.length, 4)}
        if self.loop:
            j['loop'] = True
        j['bones'] = out_b
        return j


def loop_keys(clip, fn, steps):
    """Samples fn(phase 0..1) -> pose at steps+1 evenly spaced keys over a looping clip (last = first)."""
    for i in range(steps + 1):
        ph = (i % steps) / steps
        clip.key(clip.length * i / steps, fn(ph))
    return clip


# ---- humanoid rig ----------------------------------------------------------------------------------------
def V(*a):
    return tuple(float(x) for x in a) if len(a) == 3 else tuple(float(x) for x in a[0])


ZERO = (0.0, 0.0, 0.0)


def humanoid(p, fingers_r=(), fingers_l=(), thumb_r=(), thumb_l=(), extra=None):
    """High-level humanoid parameters -> bone pose (Blockbench degrees, clip convention).

    p keys (all optional): pos (root offset px), hips, spine, chest, neck, head (rx, ry, rz); jaw (open deg);
    arm_r / arm_l (shoulder rx, ry, rz), elbow_r / elbow_l (flex deg, positive = bent), hand_r / hand_l (rx, ry, rz),
    grip_r / grip_l (finger curl 0..1); leg_r / leg_l (rx, ry, rz), knee_r / knee_l (flex deg, positive = bent),
    foot_r / foot_l (rx, ry, rz), toe_r / toe_l (deg). Signs: rx > 0 bends the torso forward and swings a leg back,
    rx < 0 raises an arm forward; ry > 0 turns toward the character's right; the right arm abducts with rz > 0,
    the left with rz < 0. Fingers hang from the hand with the palm facing the body; they curl with rz (right < 0)."""
    g = lambda k: V(p.get(k, ZERO))  # noqa: E731
    out = {
        'root': {'pos': g('pos'), 'rot': g('root')},
        'hips': g('hips'),
        'abdomen': g('spine'),
        'chest': g('chest'),
        'neck': g('neck'),
        'head': g('head'),
        'jaw': (p.get('jaw', 0.0), 0.0, 0.0),
    }
    for s in ('r', 'l'):
        sign = 1 if s == 'r' else -1
        arm = g('arm_' + s)
        out['arm_' + s] = arm
        out['forearm_' + s] = (-p.get('elbow_' + s, 0.0), 0.0, 0.0)
        out['hand_' + s] = g('hand_' + s)
        leg = g('leg_' + s)
        out['leg_' + s] = leg
        out['shin_' + s] = (p.get('knee_' + s, 0.0), 0.0, 0.0)
        out['foot_' + s] = g('foot_' + s)
        out['toe_' + s] = (p.get('toe_' + s, 0.0), 0.0, 0.0)
        grip = p.get('grip_' + s, 0.0)
        for chain in (fingers_r if s == 'r' else fingers_l):
            for i, bn in enumerate(chain):
                out[bn] = (0.0, 0.0, -sign * grip * (55.0 if i == 0 else 70.0))
        for i, bn in enumerate(thumb_r if s == 'r' else thumb_l):
            out[bn] = (grip * (25.0 if i == 0 else 40.0), 0.0, -sign * grip * 20.0)
    if extra:
        for k, v in extra.items():
            out[k] = v
    return out


def mix(a, b, f):
    """Blends two parameter dicts (numbers and 3-tuples)."""
    out = {}
    for k in set(a) | set(b):
        va, vb = a.get(k), b.get(k)
        if va is None:
            va = 0.0 if not isinstance(vb, (tuple, list)) else ZERO
        if vb is None:
            vb = 0.0 if not isinstance(va, (tuple, list)) else ZERO
        if isinstance(va, (tuple, list)):
            out[k] = tuple(x + (y - x) * f for x, y in zip(va, vb))
        else:
            out[k] = va + (vb - va) * f
    return out


def plus(a, *bs):
    """Adds parameter dicts."""
    out = dict(a)
    for b in bs:
        for k, v in b.items():
            cur = out.get(k)
            if cur is None:
                out[k] = v
            elif isinstance(v, (tuple, list)):
                out[k] = tuple(x + y for x, y in zip(cur, v))
            else:
                out[k] = cur + v
    return out


def mirror_r(model):
    """Copies every bone whose name ends in '_r' to '_l' mirrored across X = 0 (parents too)."""
    from jinwoo_geo import Cube
    for b in list(model.bones):
        if not b.name.endswith('_r'):
            continue
        nb_name = b.name[:-2] + '_l'
        parent = b.parent[:-2] + '_l' if b.parent and b.parent.endswith('_r') else b.parent
        rot = None if b.rotation is None else [b.rotation[0], -b.rotation[1], -b.rotation[2]]
        nb = model.bone(nb_name, parent, [-b.pivot[0], b.pivot[1], b.pivot[2]], rot)
        for c in b.cubes:
            o = [-(c.origin[0] + c.size[0]), c.origin[1], c.origin[2]]
            crot = None if c.rotation is None else [c.rotation[0], -c.rotation[1], -c.rotation[2]]
            piv = None if c.pivot is None else [-c.pivot[0], c.pivot[1], c.pivot[2]]
            nc = Cube(nb_name, o, c.size, c.mat, inflate=c.inflate, rotation=crot, pivot=piv,
                      faces=tuple({'east': 'west', 'west': 'east'}.get(f, f) for f in c.faces), tag=c.tag)
            nb.cubes.append(nc)
