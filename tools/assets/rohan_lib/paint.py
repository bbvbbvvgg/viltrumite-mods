"""Face painter for the Rohan geos: dio_lib.paint shading plus Rohan's own materials and decorations.

Decorations (``deco``, joined with "+"): zigzag (headband), checker (hat band), hd_face (Heaven's Door face with the
lines around the eyes), buttons, seam, panels (manga page), nib (pen-nib slit), strands (hair), bow, glow (whole face
emissive), stitch (coat edge line).
"""
import numpy as np

from dio_lib import paint as base

MATS = {
    "hair":      ((40, 48, 92), (78, 90, 150), (20, 24, 52)),
    "temple":    ((34, 96, 70), (70, 140, 104), (16, 56, 40)),
    "skin":      ((240, 216, 196), (255, 236, 222), (204, 172, 150)),
    "band":      ((60, 160, 90), (120, 210, 140), (28, 96, 50)),
    "jacket":    ((222, 232, 242), (250, 254, 255), (168, 184, 204)),
    "jacket_b":  ((176, 206, 232), (214, 234, 250), (120, 150, 186)),
    "tank":      ((56, 150, 86), (100, 196, 126), (28, 90, 50)),
    "pants":     ((206, 222, 238), (240, 248, 255), (150, 170, 196)),
    "nib":       ((196, 202, 214), (246, 250, 255), (110, 116, 130)),
    "paper":     ((246, 240, 226), (255, 252, 244), (214, 204, 184)),
    "pen":       ((28, 28, 34), (70, 70, 84), (10, 10, 14)),
    "shoe":      ((44, 52, 70), (84, 96, 120), (20, 24, 34)),
    "coat":      ((238, 226, 196), (255, 246, 222), (190, 172, 136)),
    "coat_in":   ((180, 120, 140), (214, 160, 176), (120, 70, 90)),
    "hd_hair":   ((222, 206, 160), (250, 240, 204), (170, 150, 100)),
    "hd_skin":   ((250, 226, 206), (255, 244, 232), (214, 184, 164)),
    "hd_band":   ((40, 40, 48), (90, 90, 104), (16, 16, 20)),
    "glass":     ((200, 236, 255), (255, 255, 255), (130, 180, 210)),
    "bow":       ((196, 40, 64), (240, 100, 120), (110, 16, 32)),
    "hd_eye":    ((30, 40, 70), (90, 120, 200), (10, 12, 26)),
}
base.MATS.update(MATS)

INK = np.array([24, 22, 30], np.float32)
WHITE = np.array([250, 252, 255], np.float32)


def paint_face(mat, w, h, face, deco, seed, cube=None):
    known = [d for d in (deco.split("+") if deco else []) if d in ("lines", "vlines", "rivets", "grip")]
    out, glow = base.paint_face(mat, w, h, face, "+".join(known) or None, seed, cube)
    img = out[..., :3]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    for d in (deco.split("+") if deco else []):
        if d == "zigzag":
            # white saw-tooth line along the band
            amp = max(1.0, h * 0.3)
            per = max(3.0, h * 1.2)
            ph = np.abs(((xx + 0.5) / per) % 1.0 - 0.5) * 2.0
            y = h / 2 - amp / 2 + ph * amp
            m = np.abs(yy + 0.5 - y) <= max(0.55, h * 0.14)
            img[m] = WHITE
        elif d == "checker":
            s = max(1, int(round(min(w, h) / 2)))
            m = ((xx // s + yy // s) % 2) == 0
            img[m] = np.array([236, 236, 240], np.float32)
        elif d == "hd_face":
            _hd_face(img, w, h)
            glow = _glow(glow, h, w)
            ey = int(h * 0.48)
            for ex in (int(w * 0.24), int(w * 0.62)):
                glow[ey, ex + 1:ex + 2, :3] = np.array([140, 200, 255], np.float32)
                glow[ey, ex + 1:ex + 2, 3] = 160
        elif d == "rohan_face":
            pass
        elif d == "buttons":
            cx = w // 2
            for y in range(2, h - 1, max(3, h // 4)):
                img[y, max(0, cx - 1):cx + 1] = np.array([226, 176, 54], np.float32)
        elif d == "seam":
            img[:, w // 2] = img[:, w // 2] * 0.7
        elif d == "stitch":
            img[1:-1:2, 1] = img[1:-1:2, 1] * 0.6 + INK * 0.4
            img[1:-1:2, -2] = img[1:-1:2, -2] * 0.6 + INK * 0.4
        elif d == "panels":
            _panels(img, w, h)
        elif d == "nib":
            img[:, w // 2] = INK
            if h >= 3:
                img[h // 3, max(0, w // 2 - 1):w // 2 + 1] = INK
        elif d == "strands":
            rng = np.random.default_rng(seed)
            for x in range(0, w, 2):
                off = int(rng.integers(0, 3))
                img[off::3, x] = img[off::3, x] * 0.8
        elif d == "bow":
            m = (np.abs(xx + 0.5 - w / 2) * h / max(1, w)) >= np.abs(yy + 0.5 - h / 2) * 0.7
            img[~m] = img[~m] * 0.55
            img[:, max(0, w // 2 - 1):w // 2 + 1] = np.array([140, 20, 40], np.float32)
        elif d == "glow":
            glow = _glow(glow, h, w)
            glow[..., :3] = img
            glow[..., 3] = 255
    out[..., :3] = np.clip(img, 0, 255)
    if mat == "glass":
        # see-through crown: only the bevel and a glint are dense
        a = np.full((h, w), 150, np.float32)
        a[0, :] = a[-1, :] = 230
        a[:, 0] = a[:, -1] = 230
        a[(xx >= w * 0.18) & (xx < w * 0.18 + 1)] = 245
        out[..., 3] = a
    return out, glow


def _glow(glow, h, w):
    return np.zeros((h, w, 4), np.float32) if glow is None else glow


def _hd_face(img, w, h):
    """Big round eyes with the radiating lines of Heaven's Door, small mouth."""
    ey = int(h * 0.48)
    ew = max(2, int(round(w * 0.2)))
    for ex in (int(w * 0.22), int(w * 0.58)):
        img[ey - 1:ey + 1, ex:ex + ew] = np.array([30, 40, 70], np.float32)
        img[ey - 1, ex + ew - 1] = WHITE
        img[ey - 2, ex:ex + ew] = INK
        # lines around the eyes
        if ey + 2 < h:
            for k in range(0, ew, 2):
                img[ey + 2, ex + k] = img[ey + 2, ex + k] * 0.4 + INK * 0.6
        if ex - 1 >= 0:
            img[ey - 1:ey + 2, ex - 1] = img[ey - 1:ey + 2, ex - 1] * 0.5 + INK * 0.5
        if ex + ew < w:
            img[ey - 1:ey + 2, ex + ew] = img[ey - 1:ey + 2, ex + ew] * 0.5 + INK * 0.5
    my = int(h * 0.78)
    if my < h:
        img[my, int(w * 0.44):int(w * 0.58)] = np.array([170, 90, 90], np.float32)
    img[int(h * 0.3), int(w * 0.2):int(w * 0.4)] = img[int(h * 0.3), int(w * 0.2):int(w * 0.4)] * 0.6
    img[int(h * 0.3), int(w * 0.6):int(w * 0.8)] = img[int(h * 0.3), int(w * 0.6):int(w * 0.8)] * 0.6


def _panels(img, w, h):
    img[:] = np.array([248, 246, 240], np.float32)
    border = INK
    img[0, :] = img[-1, :] = border
    img[:, 0] = img[:, -1] = border
    ys = [0, int(h * 0.38), int(h * 0.7), h - 1]
    for y in ys[1:-1]:
        img[y, :] = border
    img[:ys[1], int(w * 0.55)] = border
    img[ys[2]:, int(w * 0.4)] = border
    rng = np.random.default_rng(w * 31 + h)
    for _ in range((w * h) // 10):
        x, y = int(rng.integers(1, max(2, w - 1))), int(rng.integers(1, max(2, h - 1)))
        img[y, x] = img[y, x] * 0.5 + border * 0.5
