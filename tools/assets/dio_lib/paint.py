"""Procedural face painter: beveled metal plates, enamel, undersuit, decals.

Deterministic (seeded per face). Each face is painted at its atlas size with a
1-texel bevel (light top/left, dark bottom/right), a vertical light gradient on
side faces, fine brushed noise and an optional decoration.
"""
import numpy as np

MATS = {
    # base, light, dark (RGB 0-255)
    "gold":      ((226, 170, 44), (255, 228, 128), (138, 88, 18)),
    "gold_hi":   ((244, 198, 70), (255, 240, 170), (160, 108, 24)),
    "gold_deep": ((178, 120, 30), (222, 170, 70), (102, 62, 12)),
    "gold_face": ((236, 190, 84), (255, 232, 160), (160, 112, 40)),
    "teal":      ((30, 160, 160), (120, 236, 222), (10, 80, 92)),
    "teal_dark": ((16, 104, 112), (60, 170, 170), (6, 50, 60)),
    "navy":      ((36, 46, 62), (70, 86, 108), (16, 20, 30)),
    "eye":       ((226, 255, 252), (255, 255, 255), (150, 230, 230)),
    "steel":     ((196, 204, 214), (246, 250, 255), (112, 120, 134)),
    "steel_dark": ((112, 120, 132), (160, 168, 180), (58, 62, 72)),
    "leather":   ((92, 52, 32), (140, 86, 52), (48, 26, 14)),
    "brass":     ((196, 150, 60), (240, 206, 120), (112, 78, 24)),
    "roller_y":  ((236, 176, 24), (255, 214, 92), (150, 100, 10)),
    "roller_o":  ((222, 120, 24), (250, 168, 80), (130, 64, 8)),
    "iron":      ((70, 72, 78), (120, 122, 130), (34, 34, 38)),
    "rubber":    ((30, 30, 32), (64, 64, 68), (14, 14, 16)),
    "glass":     ((120, 170, 190), (200, 236, 250), (60, 90, 110)),
    "red":       ((180, 36, 40), (236, 90, 90), (100, 14, 18)),
}

# Decoration colours
TEAL = np.array([34, 176, 172], np.float32)
TEAL_HI = np.array([150, 246, 232], np.float32)
DARK = np.array([40, 26, 8], np.float32)


def _rng(seed):
    return np.random.default_rng(seed * 7919 + 17)


def _heart_mask(w, h, scale=0.8):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    x = (xx + 0.5 - w / 2) / (w / 2 * scale)
    y = -((yy + 0.5 - h / 2) / (h / 2 * scale)) + 0.15
    # classic implicit heart: (x^2 + y^2 - 1)^3 - x^2 y^3 <= 0  (scaled)
    x2 = x * 1.15
    v = (x2 * x2 + y * y - 1.0) ** 3 - x2 * x2 * y ** 3
    return v <= 0


def paint_face(mat, w, h, face, deco, seed, cube=None):
    base, light, dark = (np.array(c, np.float32) for c in MATS[mat])
    rng = _rng(seed)
    img = np.zeros((h, w, 3), np.float32)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    metal = mat.startswith(("gold", "steel", "brass", "roller", "iron"))
    if face in ("north", "south", "east", "west"):
        t = (yy + 0.5) / max(1, h)
        shade = 1.10 - 0.26 * t
    elif face == "up":
        shade = np.full((h, w), 1.12, np.float32)
    else:
        shade = np.full((h, w), 0.82, np.float32)
    img[:] = base
    img *= shade[..., None]
    if metal:
        # brushed streaks + soft specular band
        streak = rng.normal(0, 1, (1, w)).repeat(h, 0) * 0.035 + rng.normal(0, 1, (h, w)) * 0.02
        img *= (1 + streak)[..., None]
        if face in ("north", "south", "east", "west") and h >= 4:
            band = np.exp(-((yy / max(1, h) - 0.28) ** 2) / 0.012)
            img += (light - base) * 0.35 * band[..., None]
    else:
        img *= (1 + rng.normal(0, 1, (h, w)) * 0.025)[..., None]
    if mat == "navy":
        # muscle fibre lines on the undersuit
        fib = (np.sin(xx * 1.7 + yy * 0.35 + seed) > 0.82)
        img[fib] = img[fib] * 0.8
    # bevel
    if w >= 3 and h >= 3:
        img[0, :] = light * 0.92
        img[:, 0] = img[:, 0] * 0.5 + light * 0.5
        img[-1, :] = dark
        img[:, -1] = img[:, -1] * 0.45 + dark * 0.55
        if w >= 6 and h >= 6:
            img[1, 1:-1] = img[1, 1:-1] * 0.6 + light * 0.4
            img[-2, 1:-1] = img[-2, 1:-1] * 0.7 + dark * 0.3
    elif w >= 2 and h >= 2:
        img[-1, :] = img[-1, :] * 0.6 + dark * 0.4
    glow = None
    if mat == "eye":
        glow = np.zeros((h, w, 4), np.float32)
        glow[..., :3] = np.array([170, 255, 250], np.float32)
        glow[..., 3] = 255
    for d in (deco.split("+") if deco else []):
        glow = _deco(img, glow, d, w, h, xx, yy, rng, light, dark)
    out = np.zeros((h, w, 4), np.float32)
    out[..., :3] = np.clip(img, 0, 255)
    out[..., 3] = 255
    return out, glow


def _ensure_glow(glow, h, w):
    if glow is None:
        glow = np.zeros((h, w, 4), np.float32)
    return glow


def _deco(img, glow, d, w, h, xx, yy, rng, light, dark):
    if d == "lines":
        for y in range(3, h - 2, 3):
            img[y, 1:-1] = img[y, 1:-1] * 0.62 + DARK * 0.38
            if y + 1 < h - 1:
                img[y + 1, 1:-1] = img[y + 1, 1:-1] * 0.8 + light * 0.2
    elif d == "vlines":
        for x in range(3, w - 2, 3):
            img[1:-1, x] = img[1:-1, x] * 0.62 + DARK * 0.38
    elif d == "rivets":
        for (x, y) in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            if 0 <= x < w and 0 <= y < h and w >= 5 and h >= 5:
                img[y, x] = light
                if y + 1 < h:
                    img[y + 1, x] = dark
    elif d == "heart":
        m = _heart_mask(w, h, 0.78)
        edge = m & ~np.roll(m, 1, 0) | m & ~np.roll(m, -1, 1)
        img[m] = TEAL
        img[m & (yy < h * 0.45) & (xx < w * 0.5)] = TEAL_HI
        img[edge] = TEAL * 0.55
        glow = _ensure_glow(glow, h, w)
        glow[m, :3] = TEAL_HI * 0.5
        glow[m, 3] = 110
    elif d == "heart_gold":
        m = _heart_mask(w, h, 0.8)
        img[m] = np.array([250, 214, 90], np.float32)
        img[m & (yy > h * 0.55)] = np.array([196, 140, 30], np.float32)
    elif d == "stripe_v":
        cx = w / 2
        m = np.abs(xx + 0.5 - cx) <= max(0.6, w * 0.14)
        m &= (yy >= 1) & (yy < h - 1)
        img[m] = TEAL
        img[m & (np.abs(xx + 0.5 - cx) < 0.6)] = TEAL_HI
    elif d == "stripe_h":
        cy = h / 2
        m = np.abs(yy + 0.5 - cy) <= max(0.6, h * 0.16)
        img[m] = TEAL
    elif d == "edge_teal":
        img[-2:, :] = TEAL * 0.8
        img[-1, :] = TEAL * 0.5
    elif d == "diamond":
        cx, cy = w / 2, h / 2
        r = min(w, h) * 0.36
        m = (np.abs(xx + 0.5 - cx) + np.abs(yy + 0.5 - cy)) <= r
        img[m] = TEAL
        img[m & (yy + 0.5 < cy) & (xx + 0.5 < cx)] = TEAL_HI
        glow = _ensure_glow(glow, h, w)
        glow[m, :3] = TEAL_HI * 0.6
        glow[m, 3] = 140
    elif d == "gem":
        glow = _ensure_glow(glow, h, w)
        img[1:-1, 1:-1] = TEAL_HI
        glow[..., :3] = TEAL_HI * 0.7
        glow[..., 3] = 170
    elif d == "abs":
        for y in range(0, h, max(2, h // 3)):
            img[y, :] = img[y, :] * 0.6 + DARK * 0.4
        img[:, w // 2] = img[:, w // 2] * 0.55 + DARK * 0.45
    elif d == "face":
        _face(img, w, h, light, dark)
        glow = _ensure_glow(glow, h, w)
        ey = int(round(h * 0.42))
        for ex in (int(w * 0.22), int(w * 0.62)):
            ww = max(2, int(round(w * 0.18)))
            img[ey, ex:ex + ww] = np.array([230, 255, 252])
            glow[ey, ex:ex + ww, :3] = np.array([180, 255, 250])
            glow[ey, ex:ex + ww, 3] = 255
    elif d == "mouth":
        y = int(h * 0.55)
        img[y, int(w * 0.25):int(w * 0.75)] = DARK
    elif d == "grip":
        for y in range(0, h, 2):
            img[y, :] = img[y, :] * 0.7
    elif d == "text_road_roller":
        _text(img, "ROAD ROLLER", w, h)
    elif d == "hazard":
        m = ((xx + yy) // 3 % 2) == 0
        img[m] = np.array([28, 26, 24])
    elif d == "tread":
        for x in range(0, w, 3):
            img[:, x] = img[:, x] * 0.55
    elif d == "grille":
        for y in range(1, h - 1, 2):
            img[y, 1:-1] = np.array([30, 30, 30])
    elif d == "headlight":
        glow = _ensure_glow(glow, h, w)
        img[1:-1, 1:-1] = np.array([255, 244, 200])
        glow[1:-1, 1:-1, :3] = np.array([255, 240, 190])
        glow[1:-1, 1:-1, 3] = 200
    return glow


def _face(img, w, h, light, dark):
    """The World's mask: brow ridge, nose guard, cheek grooves, closed jaw."""
    by = int(h * 0.30)
    img[by, 1:-1] = dark * 0.9 + img[by, 1:-1] * 0.1
    img[by - 1, 1:-1] = light
    nx = w // 2
    img[by:int(h * 0.7), nx - 1:nx + 1] = img[by:int(h * 0.7), nx - 1:nx + 1] * 0.7 + light * 0.3
    img[int(h * 0.7), nx - 1:nx + 1] = dark
    for x in (int(w * 0.18), int(w * 0.82)):
        img[int(h * 0.52):int(h * 0.88), x] = dark * 0.7 + img[int(h * 0.52):int(h * 0.88), x] * 0.3
    my = int(h * 0.80)
    img[my, int(w * 0.32):int(w * 0.68)] = DARK
    img[my + 1 if my + 1 < h else my, int(w * 0.36):int(w * 0.64)] = light * 0.6 + img[my, int(w * 0.36):int(w * 0.64)] * 0.4


_FONT = {
    "R": ["111", "101", "110", "101", "101"], "O": ["111", "101", "101", "101", "111"],
    "A": ["010", "101", "111", "101", "101"], "D": ["110", "101", "101", "101", "110"],
    "L": ["100", "100", "100", "100", "111"], "E": ["111", "100", "110", "100", "111"],
    " ": ["0", "0", "0", "0", "0"],
}


def _text(img, text, w, h):
    glyphs = [_FONT[c] for c in text]
    tw = sum(len(g[0]) + 1 for g in glyphs) - 1
    s = max(1, min((w - 4) // tw, (h - 2) // 5))
    x0 = (w - tw * s) // 2
    y0 = (h - 5 * s) // 2
    img[y0 - 1:y0 + 5 * s + 1, x0 - 2:x0 + tw * s + 2] = np.array([30, 28, 26])
    x = x0
    for g in glyphs:
        for r, row in enumerate(g):
            for cidx, bit in enumerate(row):
                if bit == "1":
                    img[y0 + r * s:y0 + (r + 1) * s, x + cidx * s:x + (cidx + 1) * s] = np.array([250, 236, 200])
        x += (len(g[0]) + 1) * s
