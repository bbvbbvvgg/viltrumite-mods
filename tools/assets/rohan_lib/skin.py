"""Rohan Kishibe 64x64 player skin (original pixel art, Part 4 palette: navy hair, green undercut and headband,
white-blue suit with gold trim, green top)."""
import numpy as np
from PIL import Image

from dio_lib.skin import _box, _noise

SKIN = (240, 216, 196)
SKIN_D = (210, 182, 160)
HAIR = (40, 48, 92)
HAIR_L = (70, 84, 140)
HAIR_D = (24, 28, 58)
TEMPLE = (34, 96, 70)
TEMPLE_D = (22, 70, 50)
BAND = (60, 160, 90)
WHITE = (246, 250, 255)
EYE = (40, 120, 96)
JACKET = (222, 232, 242)
JACKET_D = (176, 196, 220)
TANK = (56, 150, 86)
TANK_D = (36, 110, 62)
GOLD = (226, 176, 54)
PANTS = (190, 210, 234)
PANTS_D = (160, 180, 206)
SHOE = (44, 52, 70)
LIP = (196, 130, 120)


def skin():
    img = np.zeros((64, 64, 4), np.uint8)

    def head(face, x, y, w, h):
        if face == "top":
            return _noise(HAIR if (x + y) % 4 else HAIR_L, x, y)
        if face == "bottom":
            return SKIN_D
        if face == "front":
            # fringe swept to his left (viewer's right), headband on the forehead
            if y == 0 or (y == 1 and x >= 2):
                return _noise(HAIR_L if x % 3 == 0 else HAIR, x, y)
            if y == 1:
                return SKIN
            if y == 2:
                return WHITE if (x % 2 == 0) else BAND
            if y == 3:
                if x >= 6:
                    return HAIR
                return HAIR_D if x in (1, 2, 5) else SKIN
            if y == 4:
                if x in (1, 6):
                    return WHITE
                if x in (2, 5):
                    return EYE
                return SKIN
            if y == 5 and x in (3, 4):
                return SKIN_D
            if y == 6 and x in (3, 4):
                return LIP
            if x in (0, 7) and y >= 5:
                return SKIN_D
            return SKIN
        if face == "back":
            if y == 2:
                return BAND
            if y >= 5:
                return _noise(TEMPLE_D if y >= 6 else TEMPLE, x, y, 3)
            return _noise(HAIR if (x + y) % 3 else HAIR_D, x, y)
        # sides: band, navy top, green undercut, ear
        if y == 2:
            return BAND if x % 2 else WHITE
        if y < 2:
            return _noise(HAIR, x, y)
        if y in (4, 5) and x in (3, 4):
            return SKIN_D if face == "right" else SKIN
        if y >= 3 and (x < 2 if face == "right" else x > 5) and y < 7:
            return SKIN
        return _noise(TEMPLE if y < 6 else TEMPLE_D, x, y, 3)

    _box(img, 0, 0, 8, 8, 8, head)

    def hat(face, x, y, w, h):
        if face == "top":
            return _noise(HAIR_L if (x * 3 + y) % 5 == 0 else HAIR, x, y)
        if face == "front":
            if y == 0 and x >= 1:
                return HAIR
            if y == 1 and x >= 4:
                return HAIR_L if x % 2 else HAIR
            return None
        if face in ("left", "right"):
            return HAIR if y == 0 else None
        if face == "back":
            return HAIR_D if y <= 1 else None
        return None

    _box(img, 32, 0, 8, 8, 8, hat)

    def body(face, x, y, w, h):
        if face == "top":
            return JACKET
        if face == "bottom":
            return PANTS_D
        if face == "front":
            if y >= 10:
                if y == 10:
                    return GOLD if x in (3, 4) else SHOE
                return PANTS if x not in (3, 4) else PANTS_D
            if y in (8, 9) and 2 <= x <= 5:
                return SKIN if y == 8 else SKIN_D
            if 2 <= x <= 5:
                if y == 0:
                    return SKIN
                return _noise(TANK if x in (3, 4) or y > 1 else TANK_D, x, y, 3)
            if x in (1, 6) and y < 9:
                return GOLD if y % 3 == 1 else JACKET_D
            return _noise(JACKET, x, y, 2)
        if face == "back":
            if y >= 10:
                return SHOE if y == 10 else PANTS
            return _noise(JACKET if y < 9 else JACKET_D, x, y, 2)
        if y >= 10:
            return SHOE if y == 10 else PANTS
        return _noise(JACKET, x, y, 2)

    _box(img, 16, 16, 8, 12, 4, body)

    def jacket(face, x, y, w, h):
        if face == "front":
            if x in (1, 6) and y < 9:
                return JACKET_D if y % 3 else GOLD
            if y == 9 and x in (0, 1, 6, 7):
                return GOLD
            return None
        if face == "back" and y == 0:
            return JACKET_D
        if face in ("left", "right") and y == 9:
            return GOLD
        return None

    _box(img, 16, 32, 8, 12, 4, jacket)

    def arm(face, x, y, w, h):
        if face == "top":
            return JACKET
        if face == "bottom":
            return SKIN_D
        if y >= 10:
            return SKIN if y == 10 else SKIN_D
        if y == 9:
            return GOLD
        if y == 8:
            return JACKET_D
        return _noise(JACKET, x, y, 2)

    _box(img, 40, 16, 4, 12, 4, arm)
    _box(img, 32, 48, 4, 12, 4, arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return PANTS
        if face == "bottom":
            return SHOE
        if y >= 10:
            return SHOE if y == 11 else (GOLD if face == "front" and x in (1, 2) else SHOE)
        if face in ("left", "right") and x == 1:
            return GOLD if y % 2 == 0 else PANTS_D
        return _noise(PANTS if face != "back" else PANTS_D, x, y, 2)

    _box(img, 0, 16, 4, 12, 4, leg)
    _box(img, 16, 48, 4, 12, 4, leg)
    return Image.fromarray(img, "RGBA")
