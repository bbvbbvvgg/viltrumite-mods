"""Clean model builder for the Captain America / Avengers assets (own work, CC0).

Same geometry API and Bedrock output as jinwoo_geo.Model (per-face UV, same face / coordinate conventions), but the
texture side is built for crisp, bright pixel art instead of the noisy procedural look:
  * every face island gets a 1 px gutter filled with its own edge colours (dilation), so nearest-neighbour sampling
    at a face edge can never pull a neighbouring island in (the thin "stripes" along cube edges);
  * materials are flat colours with optional, model-wide (not per-cube) vertical light, so stacked cubes shade
    continuously instead of banding cube by cube; details are painted by small functions in model space;
  * no value noise, no per-face dark borders.
Run with PYTHONHASHSEED=0 (deterministic output). geo_lint.py checks the results (UV bounds, overlaps, gutters,
coplanar faces = z-fighting).
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_geo import Bone, Cube, Face, Model, hexc, put, write_json, write_png, RES  # noqa: E402,F401

PAD = 1  # gutter ring around every island (px)


def rgb(c):
    return np.array(hexc(c)[:3] if isinstance(c, str) else c[:3], float)


def mix(a, b, f):
    a, b = rgb(a), rgb(b)
    return tuple(int(round(v)) for v in a + (b - a) * f)


class CleanModel(Model):
    """jinwoo_geo.Model with gutter-padded atlas packing and edge dilation."""

    def __init__(self, identifier, density=2, width=128, seed=7, light=None):
        super().__init__(identifier, density=density, width=width, seed=seed)
        # model-wide light: (y_top, y_bottom, gain) -> brightness 1 + gain at y_top .. 1 - gain at y_bottom
        self.light = light

    def pack(self):
        rects = []
        for b in self.bones:
            for c in b.cubes:
                for f in c.faces:
                    w, h = self._face_px(c, f)
                    rects.append((h, w, c, f))
        rects.sort(key=lambda r: (-r[0], -r[1]))
        while rects and max(r[1] for r in rects) + 2 * PAD > self.width:
            self.width *= 2
        W = self.width
        x = y = shelf = 0
        for h, w, c, f in rects:
            sw, sh = w + 2 * PAD, h + 2 * PAD
            if x + sw > W:
                x, y, shelf = 0, y + shelf, 0
            c.uv[f] = (x + PAD, y + PAD, w, h)
            x += sw
            shelf = max(shelf, sh)
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
                    face.model = self
                    col, gl = c.mat.paint(face, self.rng)
                    base[y:y + h, x:x + w] = col
                    if gl is not None:
                        glow[y:y + h, x:x + w] = gl
                    dilate(base, x, y, w, h)
                    dilate(glow, x, y, w, h)
        return base, glow


def dilate(arr, x, y, w, h):
    """Copies the island's edge pixels into its 1 px gutter ring (corners included)."""
    H, W = arr.shape[:2]
    if y - 1 >= 0:
        arr[y - 1, x:x + w] = arr[y, x:x + w]
    if y + h < H:
        arr[y + h, x:x + w] = arr[y + h - 1, x:x + w]
    if x - 1 >= 0:
        arr[max(0, y - 1):min(H, y + h + 1), x - 1] = arr[max(0, y - 1):min(H, y + h + 1), x]
    if x + w < W:
        arr[max(0, y - 1):min(H, y + h + 1), x + w] = arr[max(0, y - 1):min(H, y + h + 1), x + w - 1]


class Flat:
    """Flat colour material. top/bottom: cap brightness; light: use the model-wide vertical light;
    fn(face, rgb, glow, rs) paints details (rgb float array h x w x 3, glow float h x w x 4 or None)."""

    def __init__(self, base, fn=None, glow=None, alpha=255, alpha_fn=None, top=1.06, bottom=0.84, light=True,
                 edge=None):
        self.base = rgb(base)
        self.fn, self.glow, self.alpha, self.alpha_fn = fn, glow, alpha, alpha_fn
        self.top, self.bottom, self.light, self.edge = top, bottom, light, edge

    def paint(self, face, rng):
        w, h = face.w, face.h
        rs = np.random.RandomState(rng.randrange(1 << 30))
        out_rgb = np.tile(self.base, (h, w, 1))
        if face.name == 'up':
            out_rgb *= self.top
        elif face.name == 'down':
            out_rgb *= self.bottom
        m = getattr(face, 'model', None)
        if self.light and m is not None and m.light:
            y_top, y_bot, gain = m.light
            x, y, z = face.world()
            f = np.clip((y - y_bot) / max(1e-6, y_top - y_bot), 0, 1)
            out_rgb *= (1 - gain + 2 * gain * f)[..., None]
        if self.edge is not None and face.name in ('north', 'south', 'east', 'west') and h > 2:
            # a soft 1 px darker lower edge only (reads as an overhang, not as a grid)
            col, amt = self.edge
            put(out_rgb, np.s_[-1, :], col, amt)
        glow = None
        if self.glow is not None or self.fn is not None:
            glow = np.zeros((h, w, 4), float)
        if self.fn is not None:
            self.fn(face, out_rgb, glow, rs)
        if self.glow is not None:
            self.glow(face, glow, rs)
        out = np.zeros((h, w, 4), np.uint8)
        out[..., :3] = np.clip(out_rgb, 0, 255).astype(np.uint8)
        out[..., 3] = self.alpha if self.alpha_fn is None else self.alpha_fn(face, rs)
        gl = None if glow is None else np.clip(glow, 0, 255).astype(np.uint8)
        return out, gl


# ---- painting helpers (model-space masks) ---------------------------------------------------------------
def sides(face):
    return face.name in ('north', 'south', 'east', 'west')


def face_uv(face):
    """Pixel-centre u, v (0..1) arrays of the face."""
    return face.grid()


def poly_mask(px, py, pts):
    from matplotlib.path import Path
    flat = np.c_[np.ravel(px), np.ravel(py)]
    return Path(pts).contains_points(flat).reshape(np.shape(px))


def star_pts(cx, cy, r, inner=0.4, rot=0.0):
    pts = []
    for i in range(10):
        a = rot + i * math.pi / 5
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + rr * math.sin(a), cy + rr * math.cos(a)))
    return pts


def star_mask(px, py, cx, cy, r, inner=0.4, rot=0.0):
    return poly_mask(px, py, star_pts(cx, cy, r, inner, rot))


def box_mask(a, b, a0, a1, b0, b1):
    return (a >= a0) & (a < a1) & (b >= b0) & (b < b1)


def px_rect(rgbarr, x0, y0, x1, y1, color, amount=1.0):
    """Paints face-pixel rectangle [x0, x1) x [y0, y1) (clipped)."""
    h, w = rgbarr.shape[:2]
    x0, x1 = max(0, x0), min(w, x1)
    y0, y1 = max(0, y0), min(h, y1)
    if x0 < x1 and y0 < y1:
        put(rgbarr, np.s_[y0:y1, x0:x1], color, amount)


def glow_put(glow, mask, color, alpha=255):
    if glow is None:
        return
    c = np.array(list(hexc(color)[:3]) + [alpha], float) if isinstance(color, str) else np.array(color, float)
    glow[mask] = c


def write_model(m, geo_rel, tex_rel, glow_rel=None):
    base, glow = m.paint()
    write_json(os.path.join(RES, geo_rel), m.geo_json())
    write_png(tex_rel, base)
    if glow_rel:
        g = glow.astype(float)
        g[..., :3] *= g[..., 3:4] / 255.0
        g[..., 3] = np.where(g[..., 3] > 0, 255, 0)
        write_png(glow_rel, g.astype(np.uint8))
    return base, glow
