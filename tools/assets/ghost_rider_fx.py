#!/usr/bin/env python3
"""Ghost Rider hellfire sprite sheet (own work, CC0): 8 looping frames x 2 rows of 16 x 32 pixel-art flames.

Row 0: a licking tongue (wheels, exhausts, trails, burning bodies); row 1: a wide blaze (the skull, bursts).
Palette quantised from white-hot core to deep red edges; alpha falls off on the outer two bands, so the sheet works
with RenderType.entityTranslucentEmissive. Time is periodic over the 8 frames (integer temporal frequencies), so the
animation loops without a seam. Drawn by client.ghostrider.HellfireDraw.

Run: python3 tools/assets/ghost_rider_fx.py
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from jinwoo_geo import write_png  # noqa: E402

SHEET = 'textures/entity/ghost_rider/hellfire.png'
W, H, FRAMES = 16, 32, 8
PAL = [  # (threshold, rgba)
    (0.86, (255, 250, 214, 255)),
    (0.72, (255, 214, 92, 255)),
    (0.56, (255, 150, 34, 255)),
    (0.40, (240, 84, 18, 240)),
    (0.26, (178, 34, 10, 200)),
    (0.16, (96, 14, 6, 130)),
]


def field(frame, wide):
    t = frame / FRAMES * 2 * math.pi
    ys, xs = np.mgrid[0:H, 0:W].astype(float)
    v = 1.0 - (ys + 0.5) / H                    # 0 at the base (bottom row), 1 at the top
    u = (xs + 0.5) / W - 0.5                    # -0.5 .. 0.5
    # flicker: rising waves, periodic in t (integer multiples)
    sway = 0.06 * np.sin(v * 5.0 - t) + 0.035 * np.sin(v * 11.0 - 2 * t + 1.3)
    if wide:
        sway *= 0.6
    uu = u - sway * v * 1.6
    half = (0.44 if wide else 0.40) * (1.0 - v) ** (0.55 if wide else 0.75) + 0.02
    shape = 1.0 - np.abs(uu) / np.maximum(half, 1e-3)
    shape = np.clip(shape, 0, 1)
    turb = (0.5 + 0.25 * np.sin(xs * 1.7 + v * 9.0 - 3 * t) + 0.25 * np.sin(xs * 0.9 - v * 14.0 - 2 * t + 0.7)
            + 0.18 * np.sin(v * 23.0 - 4 * t + xs * 0.4))
    # separate licks near the tip
    licks = 0.5 + 0.5 * np.sin(u * (9.0 if wide else 6.0) + np.sin(v * 3.0 - t) * 1.5)
    base_heat = (1.0 - v) ** 0.6
    f = shape * (0.55 * base_heat + 0.45 * turb) * (0.7 + 0.3 * licks)
    f *= np.clip(1.25 - v * (1.05 if wide else 1.15), 0, 1)
    f += 0.35 * shape * np.clip(0.35 - v, 0, 1) / 0.35   # hot root
    return f


def quantise(f):
    out = np.zeros(f.shape + (4,), np.uint8)
    for thr, c in reversed(PAL):
        out[f >= thr] = c
    return out


def main():
    sheet = np.zeros((H * 2, W * FRAMES, 4), np.uint8)
    for row, wide in ((0, False), (1, True)):
        for fr in range(FRAMES):
            sheet[row * H:(row + 1) * H, fr * W:(fr + 1) * W] = quantise(field(fr, wide))
    write_png(SHEET, sheet)
    print('hellfire sheet %dx%d' % (sheet.shape[1], sheet.shape[0]))


if __name__ == '__main__':
    main()
