"""DIO 64x64 player skin and the six ability icons (original pixel art)."""
import math

import numpy as np
from PIL import Image, ImageDraw

SKIN = (238, 214, 190)
SKIN_D = (206, 176, 150)
HAIR = (250, 222, 110)
HAIR_D = (214, 170, 52)
HAIR_L = (255, 244, 170)
YEL = (238, 196, 40)
YEL_D = (190, 140, 20)
BLACK = (30, 30, 36)
BLACK_L = (58, 58, 70)
GREEN = (60, 170, 110)
GREEN_D = (30, 110, 70)
LIP = (110, 60, 120)
EYE = (190, 40, 50)


def _box(img, u, v, w, h, d, fn):
    faces = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }
    for face, (x0, y0, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                c = fn(face, x, y, fw, fh)
                if c is not None:
                    img[y0 + y, x0 + x] = list(c) + [255] if len(c) == 3 else c


def _noise(c, x, y, k=6):
    n = ((x * 73856093) ^ (y * 19349663)) % 7 - 3
    return tuple(max(0, min(255, v + n * k // 3)) for v in c)


def skin():
    img = np.zeros((64, 64, 4), np.uint8)

    def head(face, x, y, w, h):
        if face == "top":
            return _noise(HAIR if (x + y) % 3 else HAIR_D, x, y)
        if face == "bottom":
            return SKIN_D
        if face == "front":
            if y <= 1:
                return _noise(HAIR, x, y)
            if y == 2 and x in (0, 1, 6, 7):
                return HAIR_D
            if y in (2, 3) and 0 < x < 7 and y == 3:
                return GREEN if x not in (3, 4) else (250, 222, 90)
            if y == 2:
                return SKIN
            if y == 4 and x in (1, 2, 5, 6):
                return (120, 70, 40) if x in (1, 6) else SKIN_D
            if y == 5 and x in (1, 2):
                return EYE if x == 2 else (255, 255, 255)
            if y == 5 and x in (5, 6):
                return EYE if x == 5 else (255, 255, 255)
            if y == 6 and x in (3, 4):
                return SKIN_D
            if y == 7 and 2 <= x <= 5:
                return LIP if x in (3, 4) else SKIN_D
            if x in (0, 7) and y <= 6:
                return HAIR_D
            return SKIN
        if face == "back":
            return _noise(HAIR if y < 7 else HAIR_D, x, y)
        # sides: hair over the ears down to the jaw
        if y == 3:
            return GREEN
        return _noise(HAIR if y < 6 or x < 4 else SKIN_D, x, y)

    _box(img, 0, 0, 8, 8, 8, head)

    def hat(face, x, y, w, h):
        if face == "top":
            return _noise(HAIR_L if (x * 3 + y) % 4 == 0 else HAIR, x, y)
        if face == "front":
            if y == 0 or (y == 1 and x not in (3, 4)):
                return HAIR_L if x % 3 == 0 else HAIR
            if y == 3 and 1 <= x <= 6:
                return GREEN if x not in (3, 4) else (255, 214, 70)
            return None
        if face in ("left", "right"):
            if y == 3:
                return GREEN_D
            if y < 7 and (x + y) % 2 == 0:
                return HAIR
            return None
        if face == "back":
            return HAIR if y < 8 and (x + y) % 3 else None
        return None

    _box(img, 32, 0, 8, 8, 8, hat)

    def body(face, x, y, w, h):
        if face == "top":
            return YEL
        if face == "front":
            if 2 <= x <= 5 and y < 9:
                return _noise(BLACK if y > 0 else SKIN, x, y, 3)
            if y >= 10:
                return YEL_D if y == 10 else YEL
            return _noise(YEL if x not in (1, 6) else YEL_D, x, y)
        if face == "back":
            return _noise(YEL if y < 10 else YEL_D, x, y)
        return _noise(YEL, x, y)

    _box(img, 16, 16, 8, 12, 4, body)

    def jacket(face, x, y, w, h):
        if face == "front":
            if x in (1, 6) and y < 10:
                return YEL_D
            if y == 9 and 2 <= x <= 5:
                return (40, 140, 90)
            return None
        if face == "back" and y < 2:
            return YEL_D
        return None

    _box(img, 16, 32, 8, 12, 4, jacket)

    def arm(face, x, y, w, h):
        if face == "top":
            return SKIN
        if face == "bottom":
            return SKIN_D
        if y >= 9 and y <= 10:
            return YEL
        if y == 11:
            return SKIN_D
        return _noise(SKIN, x, y, 2)

    _box(img, 40, 16, 4, 12, 4, arm)
    _box(img, 32, 48, 4, 12, 4, arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return YEL
        if face == "bottom":
            return BLACK
        if y >= 10:
            return GREEN_D if y == 10 else BLACK_L
        if face == "front" and 4 <= y <= 6 and 0 <= x <= 3:
            hx = [(1, 4), (2, 4), (0, 5), (1, 5), (2, 5), (3, 5), (1, 6), (2, 6)]
            hx = [(0, 4), (1, 4), (2, 4), (3, 4), (0, 5), (1, 5), (2, 5), (3, 5), (1, 6), (2, 6)]
            if (x, y) in hx:
                return GREEN if y < 6 else GREEN_D
        return _noise(YEL if face != "back" else YEL_D, x, y)

    _box(img, 0, 16, 4, 12, 4, leg)
    _box(img, 16, 48, 4, 12, 4, leg)
    return Image.fromarray(img, "RGBA")


# ------------------------------------------------------------------ icons
GOLD = (240, 190, 60)
GOLD_D = (150, 100, 20)
TEAL = (40, 190, 180)


def _icon_base(accent=(240, 190, 60)):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for r in range(15, 0, -1):
        k = r / 15
        col = (int(24 + 30 * (1 - k)), int(20 + 18 * (1 - k)), int(36 + 30 * (1 - k)), 255)
        d.ellipse([16 - r, 16 - r, 15 + r, 15 + r], fill=col)
    d.ellipse([1, 1, 30, 30], outline=accent + (255,), width=1)
    return im, d


def icons():
    out = {}
    # The World: masked head with teal ear plates
    im, d = _icon_base()
    d.rectangle([11, 9, 20, 22], fill=GOLD)
    d.rectangle([10, 7, 21, 9], fill=(255, 220, 110))
    d.rectangle([15, 3, 16, 8], fill=(255, 220, 110))
    d.rectangle([8, 12, 9, 19], fill=TEAL)
    d.rectangle([22, 12, 23, 19], fill=TEAL)
    d.rectangle([12, 14, 14, 14], fill=(220, 255, 250))
    d.rectangle([17, 14, 19, 14], fill=(220, 255, 250))
    d.rectangle([13, 19, 18, 19], fill=GOLD_D)
    d.rectangle([12, 23, 19, 26], fill=GOLD_D)
    out["the_world"] = im
    # MUDA: a storm of fists
    im, d = _icon_base()
    for i, (x, y) in enumerate(((6, 7), (17, 5), (10, 15), (20, 15), (6, 22), (16, 22))):
        a = 255 if i in (2, 3) else 150
        d.rectangle([x, y, x + 6, y + 5], fill=GOLD + (a,))
        d.rectangle([x, y, x + 6, y], fill=(255, 236, 150, a))
        d.rectangle([x + 1, y + 2, x + 5, y + 2], fill=GOLD_D + (a,))
    d.line([2, 16, 8, 16], fill=(255, 255, 255, 160))
    d.line([24, 9, 29, 9], fill=(255, 255, 255, 160))
    out["muda"] = im
    # knives: fan of three blades
    im, d = _icon_base()
    for ang in (-24, 0, 24):
        a = math.radians(ang - 90)
        cx, cy = 16, 26
        tip = (cx + math.cos(a) * 20, cy + math.sin(a) * 20)
        mid = (cx + math.cos(a) * 8, cy + math.sin(a) * 8)
        d.line([mid, tip], fill=(220, 228, 240), width=3)
        d.line([mid, tip], fill=(255, 255, 255), width=1)
        d.line([(cx + math.cos(a) * 2, cy + math.sin(a) * 2), mid], fill=(120, 70, 40), width=3)
        pa = a + math.pi / 2
        d.line([(mid[0] + math.cos(pa) * 3, mid[1] + math.sin(pa) * 3), (mid[0] - math.cos(pa) * 3, mid[1] - math.sin(pa) * 3)], fill=GOLD, width=1)
    out["knives"] = im
    # ZA WARUDO: stopped clock, inverted halves
    im, d = _icon_base(TEAL)
    d.ellipse([5, 5, 26, 26], fill=(230, 236, 240))
    d.pieslice([5, 5, 26, 26], 90, 270, fill=(30, 36, 60))
    for k in range(12):
        a = math.radians(k * 30)
        x, y = 15.5 + math.cos(a) * 9, 15.5 + math.sin(a) * 9
        col = (30, 36, 60) if math.cos(a) >= 0 else (230, 236, 240)
        d.point((x, y), fill=col)
    d.line([16, 16, 16, 8], fill=GOLD, width=2)
    d.line([16, 16, 21, 19], fill=GOLD, width=2)
    d.ellipse([14, 14, 17, 17], fill=TEAL)
    out["time_stop"] = im
    # drain: fanged hand and blood drop
    im, d = _icon_base((200, 40, 50))
    d.polygon([(16, 5), (22, 15), (22, 19), (19, 23), (13, 23), (10, 19), (10, 15)], fill=(180, 24, 36))
    d.polygon([(14, 9), (12, 15), (14, 15)], fill=(240, 120, 120))
    d.polygon([(11, 24), (13, 29), (15, 24)], fill=(250, 250, 250))
    d.polygon([(17, 24), (19, 29), (21, 24)], fill=(250, 250, 250))
    out["drain"] = im
    # Road Roller
    im, d = _icon_base((240, 150, 30))
    d.ellipse([3, 15, 15, 27], fill=(70, 72, 80))
    d.ellipse([6, 18, 12, 24], fill=(222, 120, 24))
    d.ellipse([19, 17, 29, 27], fill=(70, 72, 80))
    d.rectangle([9, 11, 26, 19], fill=(236, 176, 24))
    d.rectangle([11, 4, 25, 6], fill=(222, 120, 24))
    d.rectangle([12, 6, 13, 11], fill=(70, 72, 80))
    d.rectangle([23, 6, 24, 11], fill=(70, 72, 80))
    d.rectangle([10, 13, 25, 14], fill=(40, 30, 20))
    out["road_roller"] = im
    return {k: v.resize((64, 64), Image.NEAREST) for k, v in out.items()}
