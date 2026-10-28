#!/usr/bin/env python3
"""Procedural swatch painter for the Sinclair atlases (numpy + Pillow, deterministic).

Each material is (base hex, pattern, glow). Patterns are drawn into the 16x16 slot that
sinclair_geo.Atlas assigned. Glow materials are copied into the *_glow.png emissive pass.
Palette from the design spec: ghost violet #9B6BFF, lavender #C9B6FF, stage gold #F2C14E,
velvet crimson #8E1B2C, ivory #F3EBDD, ink #15121A, Savannah #B07CFF.
"""
import math

import numpy as np
from PIL import Image

S = 16


def hex_rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def _grid():
    y, x = np.mgrid[0:S, 0:S].astype(np.float32)
    return x, y


def _noise(rng, amp):
    return rng.normal(0.0, amp, (S, S)).astype(np.float32)


def paint_swatch(base, pattern, rng):
    """Returns (16,16,4) uint8."""
    x, y = _grid()
    c = np.broadcast_to(hex_rgb(base), (S, S, 3)).copy()
    alpha = np.full((S, S), 255.0, dtype=np.float32)
    shade = np.zeros((S, S), dtype=np.float32)
    if pattern == "flat":
        shade += _noise(rng, 2.0)
    elif pattern == "grain":
        phase = rng.uniform(0, 6.28)
        wob = np.sin(x * 0.45 + phase) * 1.3
        streak = np.sin((y + wob) * 1.7 + phase)
        shade += streak * 5.0 + _noise(rng, 2.0)
        shade -= (np.abs(np.sin((y + wob * 2) * 0.33)) < 0.07) * 9.0
    elif pattern == "joint":
        r = np.hypot(x - 7.5, y - 7.5)
        shade += -r * 1.2 + 6 + _noise(rng, 1.5)
    elif pattern == "weave":
        shade += ((x.astype(int) + y.astype(int)) % 2) * 3.0 - 1.5 + _noise(rng, 1.5)
        shade += ((y.astype(int) % 4) == 0) * -2.0
    elif pattern == "satin":
        shade += np.sin(x * 0.4) * 6.0 + (8 - np.abs(y - 6)) * 0.9 + _noise(rng, 1.0)
    elif pattern == "felt":
        shade += _noise(rng, 3.5)
    elif pattern == "gloss":
        band = np.exp(-((x - y * 0.6 - 3.0) ** 2) / 5.0)
        shade += band * 38.0 + _noise(rng, 1.2)
    elif pattern == "velvet":
        shade += np.sin(x * 1.25) * 12.0 + _noise(rng, 2.5) - (y > 13) * 8.0
    elif pattern == "gold":
        shade += (7 - np.hypot(x - 5, y - 5)) * 2.2 + _noise(rng, 3.0)
        sparkle = rng.random((S, S)) > 0.94
        shade += sparkle * 45.0
    elif pattern == "pleat":
        shade += (np.abs((x % 3) - 1.0) < 0.5) * -14.0 + _noise(rng, 1.2)
    elif pattern == "ghost":
        shade += (8 - y) * 2.2 + np.sin(x * 0.9 + y * 0.3) * 6.0 + _noise(rng, 2.0)
    elif pattern == "wisp":
        shade += np.sin(x * 0.8 + y * 0.5) * 10.0 + _noise(rng, 3.0)
        alpha = 150.0 + 70.0 * np.sin(y * 0.6 + x * 0.2)
    elif pattern == "hair":
        shade += np.sin(x * 2.1) * 9.0 + _noise(rng, 2.0)
    elif pattern == "fur":
        shade += _noise(rng, 4.0) + np.sin(y * 1.9 + x * 0.3) * 3.0
    elif pattern == "steel":
        shade += (x - 7.5) * -3.0 + _noise(rng, 1.0)
        shade += (np.abs(x - 7.5) < 0.6) * 30.0
    elif pattern == "trim":
        shade += (np.abs(y - 7.5) < 3) * 10.0 + _noise(rng, 2.5)
        shade += ((x.astype(int) % 4) == 0) * -18.0
    elif pattern == "medallion":
        r = np.hypot(x - 7.5, y - 7.5)
        shade += (6.5 - r) * 3.0 + _noise(rng, 2.0)
        ang = np.arctan2(y - 7.5, x - 7.5)
        star = r < (3.2 + 1.6 * np.cos(5 * ang))
        c[star] = hex_rgb("#8E1B2C")
        shade[star] = _noise(rng, 2.0)[star]
        shade -= (np.abs(r - 7.0) < 0.7) * 30.0
    elif pattern == "card_face":
        border = (x < 1) | (x > 14) | (y < 1) | (y > 14)
        shade += _noise(rng, 1.5)
        r = np.abs(x - 7.5) + np.abs(y - 7.5)
        pip = r < 4.2
        c[pip] = hex_rgb("#8E1B2C")
        c[(r < 1.6)] = hex_rgb("#F2C14E")
        c[border] = hex_rgb("#F2C14E")
    elif pattern == "card_back":
        lattice = ((x.astype(int) + y.astype(int)) % 4 == 0) | ((x.astype(int) - y.astype(int)) % 4 == 0)
        shade += lattice * 30.0 + _noise(rng, 2.0)
        border = (x < 1) | (x > 14) | (y < 1) | (y > 14)
        c[border] = hex_rgb("#F2C14E")
    elif pattern == "stud":
        r = np.hypot(x - 7.5, y - 7.5)
        shade += (5 - r) * 6.0
    elif pattern == "eye":
        r = np.hypot(x - 7.5, y - 7.5)
        shade += (4 - r) * 12.0
    elif pattern == "star":
        r = np.hypot(x - 7.5, y - 7.5)
        ang = np.arctan2(y - 7.5, x - 7.5)
        star = r < (3.5 + 2.5 * np.cos(5 * ang))
        shade += star * 40.0 - 10.0
    else:
        raise ValueError("unknown pattern " + pattern)
    rgb = np.clip(c + shade[..., None], 0, 255)
    out = np.zeros((S, S, 4), dtype=np.uint8)
    out[..., :3] = rgb.astype(np.uint8)
    out[..., 3] = np.clip(alpha, 0, 255).astype(np.uint8)
    return out


def paint(atlas, materials, seed=7):
    """materials: {name: (hex, pattern, glow)}; returns (texture, glow) PIL images."""
    size = atlas.size
    tex = np.zeros((size, size, 4), dtype=np.uint8)
    glow = np.zeros((size, size, 4), dtype=np.uint8)
    for name, (sx, sy) in atlas.slots.items():
        if name not in materials:
            raise KeyError("material not defined: " + name)
        base, pattern, is_glow = materials[name]
        rng = np.random.default_rng(seed + sum(ord(ch) * (i + 1) for i, ch in enumerate(name)))
        sw = paint_swatch(base, pattern, rng)
        tex[sy:sy + S, sx:sx + S] = sw
        if is_glow:
            glow[sy:sy + S, sx:sx + S] = sw
    return Image.fromarray(tex, "RGBA"), Image.fromarray(glow, "RGBA")
