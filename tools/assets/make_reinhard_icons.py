#!/usr/bin/env python3
"""Reinhard HUD art: three mode icons (12x12) and sixteen ability icons (16x16).

Art rules (2026-10-19 rework):
- one dark plate per icon (navy gradient for abilities, mode colour for modes),
  a light bevel on the top-left edge, transparent cut corners;
- the figure is drawn as a light shape with a 1 px dark outline, so it reads at
  native 16x16 without a downscale;
- every icon is drawn 4x supersampled and box-filtered down, so edges stay smooth;
- white/gold knight palette with blue accents; each ability has its own silhouette;
- the plate is never painted over: the border is restored after compositing.

The script writes the same files as before: `<name>.png.b64` under the mod's
binassets (decoded by Gradle `decodeBinaryAssets`) plus a plain PNG preview in
`build/reinhard_icons/` for review. Copied archive art (emblem, hero portrait,
Phoenix) is unchanged and needs `--assets DIR`.

Run: python3 tools/assets/make_reinhard_icons.py [--assets DIR] [--out ROOT] [--only NAME]
"""
import argparse
import base64
import io
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BIN = os.path.join(REPO, "viltrumitecore", "src", "main", "binassets")
GUI = "assets/viltrumitecore/textures/gui/reinhard/"
ABILITY_GUI = "assets/viltrumitecore/textures/gui/ability/reinhard/"
PREVIEW = os.path.join(REPO, "build", "reinhard_icons")

SS = 4  # supersampling factor

INK = (14, 11, 20, 255)
STEEL = (206, 214, 230, 255)
STEEL_D = (118, 130, 156, 255)
WHITE = (252, 250, 244, 255)
WHITE_D = (186, 192, 208, 255)
GOLD = (255, 205, 92, 255)
GOLD_D = (164, 118, 36, 255)
BLUE = (60, 116, 206, 255)
BLUE_D = (24, 46, 100, 255)
RED = (228, 54, 62, 255)
RED_D = (140, 24, 32, 255)
CLEAR = (0, 0, 0, 0)

PLATE_TOP = (34, 46, 90, 255)
PLATE_BOT = (11, 16, 38, 255)
PLATE_EDGE = (150, 170, 214, 255)
PLATE_EDGE_D = (58, 72, 118, 255)


class Tile:
    """One icon: a plate plus a figure layer that gets an outline before compositing."""

    def __init__(self, size, top=PLATE_TOP, bottom=PLATE_BOT):
        self.size = size
        self.n = size * SS
        self.top = top
        self.bottom = bottom
        self.img = Image.new("RGBA", (self.n, self.n), CLEAR)
        self.fig = Image.new("RGBA", (self.n, self.n), CLEAR)
        self.draw_plate()
        self.f = ImageDraw.Draw(self.fig)

    # --- geometry -------------------------------------------------------
    def q(self, v):
        return int(round(v * SS))

    def box(self, x0, y0, x1, y1):
        return [self.q(x0), self.q(y0), self.q(x1), self.q(y1)]

    def pts(self, points):
        return [(self.q(x), self.q(y)) for x, y in points]

    # --- plate ----------------------------------------------------------
    def draw_plate(self):
        d = ImageDraw.Draw(self.img)
        n = self.n
        for y in range(n):
            k = y / max(1, n - 1)
            colour = tuple(int(round(self.top[i] + (self.bottom[i] - self.top[i]) * k)) for i in range(3)) + (255,)
            d.line([(0, y), (n, y)], fill=colour)
        # bevel: light top-left, dark bottom-right
        cut = SS
        d.line([(cut, cut), (n - 1 - cut, cut)], fill=(200, 216, 246, 255))
        d.line([(cut, cut), (cut, n - 1 - cut)], fill=(200, 216, 246, 255))
        d.line([(n - 1, cut), (n - 1, n - 1 - cut)], fill=PLATE_EDGE_D)
        d.line([(cut, n - 1), (n - 1, n - 1)], fill=PLATE_EDGE_D)
        # cut the corners last, or the frame puts them back: exactly the corner pixel's block
        for cx, cy in ((0, 0), (n - 1, 0), (0, n - 1), (n - 1, n - 1)):
            x0 = 0 if cx == 0 else n - SS
            y0 = 0 if cy == 0 else n - SS
            d.rectangle([x0, y0, x0 + SS - 1, y0 + SS - 1], fill=CLEAR)
        self.plate = self.img.copy()

    def reset(self):
        self.img = self.plate.copy()
        self.fig = Image.new("RGBA", (self.n, self.n), CLEAR)
        self.f = ImageDraw.Draw(self.fig)

    # --- shapes (coordinates in 16-space) --------------------------------
    def poly(self, points, fill):
        self.f.polygon(self.pts(points), fill=fill)

    def rect(self, x0, y0, x1, y1, fill, radius=0.0):
        if radius <= 0:
            self.f.rectangle(self.box(x0, y0, x1, y1), fill=fill)
        else:
            self.f.rounded_rectangle(self.box(x0, y0, x1, y1), radius=self.q(radius), fill=fill)

    def ellipse(self, x0, y0, x1, y1, fill):
        self.f.ellipse(self.box(x0, y0, x1, y1), fill=fill)

    def line(self, points, fill, width=1.0):
        self.f.line(self.pts(points), fill=fill, width=max(1, self.q(width)), joint="curve")

    def arc(self, x0, y0, x1, y1, start, end, fill, width=1.0):
        self.f.arc(self.box(x0, y0, x1, y1), start, end, fill=fill, width=max(1, self.q(width)))

    # --- composition -----------------------------------------------------
    def finish(self):
        """Outline the figure with INK, composite it and restore the plate frame."""
        alpha = self.fig.getchannel("A")
        grown = alpha.filter(ImageFilter.MaxFilter(2 * SS + 1))
        outline = Image.new("RGBA", (self.n, self.n), INK)
        outline.putalpha(grown)
        out = self.plate.copy()
        out.alpha_composite(outline)
        out.alpha_composite(self.fig)
        self.img = out
        self.img = self.img.resize((self.size, self.size), Image.BOX)
        # The figure may reach the corner block: the cut corner is part of the icon shape.
        for x, y in ((0, 0), (self.size - 1, 0), (0, self.size - 1), (self.size - 1, self.size - 1)):
            self.img.putpixel((x, y), CLEAR)
        return self.img


def ability(fn):
    def build():
        tile = Tile(16)
        fn(tile)
        return tile.finish()

    return build


# --- ability silhouettes -------------------------------------------------

@ability
def light_punch(t):
    # speed lines behind the fist
    for y in (5.0, 8.0, 11.0):
        t.line([(1.4, y), (4.6, y)], STEEL_D, 0.7)
    # the jab: forearm cuff, fist, knuckles
    t.rect(3.0, 5.6, 5.4, 10.4, GOLD, 0.6)
    t.rect(5.0, 4.6, 10.6, 11.4, WHITE, 1.6)
    t.rect(9.6, 4.9, 11.4, 11.1, STEEL, 1.2)
    for y0 in (5.4, 7.2, 9.0):
        t.rect(10.2, y0, 11.6, y0 + 1.2, WHITE_D, 0.5)
    t.rect(5.6, 5.2, 9.4, 6.6, STEEL, 0.6)
    # impact star at the knuckles
    t.poly([(12.2, 7.6), (13.4, 8.0), (12.6, 8.6), (13.0, 9.8), (12.0, 9.0), (11.2, 9.6), (11.5, 8.4), (10.6, 7.9), (11.8, 7.7), (12.0, 6.6)],
           GOLD)


@ability
def step_dash(t):
    # afterimages behind the boot
    t.rect(2.2, 6.4, 4.6, 9.2, (90, 130, 210, 150), 0.6)
    t.rect(3.6, 6.8, 5.6, 8.8, (150, 180, 235, 190), 0.5)
    for y in (5.4, 10.6):
        t.line([(1.4, y), (6.0, y)], STEEL_D, 0.6)
    # the boot
    t.poly([(6.0, 4.2), (9.0, 4.2), (9.6, 9.6), (12.6, 9.6), (12.6, 11.8), (6.4, 11.8), (6.0, 8.0)], WHITE)
    t.rect(6.2, 4.4, 8.8, 6.2, STEEL, 0.5)
    t.rect(11.4, 9.8, 12.6, 11.6, GOLD, 0.4)
    # motion chevrons
    for x in (10.4, 12.0):
        t.line([(x, 2.6), (x + 1.6, 4.2), (x, 5.8)], GOLD, 0.9)


@ability
def catch_projectile(t):
    # the palm, fingers up
    t.rect(5.0, 7.0, 10.8, 12.2, WHITE, 1.4)
    t.rect(5.2, 6.2, 6.6, 9.4, STEEL, 0.5)
    for x0 in (6.6, 8.0, 9.4):
        t.rect(x0, 3.4, x0 + 1.2, 8.0, STEEL, 0.5)
    t.rect(10.6, 4.6, 11.8, 8.4, STEEL, 0.5)
    t.rect(4.6, 10.6, 5.4, 12.4, GOLD, 0.4)
    # the caught arrow
    t.line([(1.2, 5.2), (7.6, 5.6)], GOLD, 1.0)
    t.poly([(7.8, 4.4), (9.8, 5.6), (7.8, 6.8)], RED)
    t.line([(1.4, 3.8), (3.0, 5.0)], GOLD_D, 0.6)
    t.line([(1.4, 6.8), (3.0, 5.8)], GOLD_D, 0.6)


@ability
def fist_combo(t):
    # three punches in a chain: small, medium, heavy; each a fist with its cuff and spark
    for x, y, size, body, cuff in ((3.2, 3.0, 1.6, STEEL, GOLD_D), (7.4, 6.6, 1.9, WHITE, GOLD), (11.8, 10.8, 2.2, WHITE, GOLD)):
        t.rect(x - size, y - size * 0.75, x + size, y + size * 0.75, body, 0.6)
        t.rect(x - size - 0.8, y - size * 0.75, x - size + 0.3, y + size * 0.75, cuff, 0.2)
        t.line([(x + size + 0.5, y - size * 0.5), (x + size + 1.2, y - size * 0.9)], GOLD, 0.6)
        t.line([(x + size + 0.5, y + size * 0.5), (x + size + 1.2, y + size * 0.9)], GOLD, 0.6)
    # the chain
    t.line([(4.6, 4.2), (5.8, 5.4)], GOLD, 0.6)
    t.line([(9.0, 8.2), (10.0, 9.4)], GOLD, 0.6)


@ability
def sky_kick(t):
    # upward chevrons
    for y in (2.0, 4.4):
        t.line([(4.0, y + 1.4), (8.0, y), (12.0, y + 1.4)], GOLD, 1.0)
    # the kicking boot, sole up
    t.poly([(8.6, 5.4), (12.2, 5.4), (12.6, 11.0), (9.0, 11.0)], WHITE)
    t.rect(8.8, 5.6, 12.0, 7.4, STEEL, 0.5)
    t.rect(9.2, 9.6, 12.6, 11.4, GOLD_D, 0.4)
    for y in (12.4, 13.6):
        t.arc(5.0, y - 2.4, 13.4, y + 1.2, 200, 340, STEEL, 0.7)


@ability
def grab_throw(t):
    # the gripping fist
    t.rect(2.0, 6.4, 5.8, 11.0, WHITE, 0.8)
    t.rect(1.6, 9.6, 2.6, 11.8, GOLD, 0.3)
    for y0 in (6.8, 8.0):
        t.line([(5.8, y0), (6.8, y0 - 0.1)], STEEL_D, 0.6)
    # the thrown body, tumbling along the arc
    t.arc(4.4, 1.0, 15.4, 10.0, 200, 330, GOLD, 0.9)
    t.ellipse(10.2, 1.4, 11.6, 2.8, WHITE)
    t.line([(10.9, 2.8), (12.0, 5.2)], WHITE, 1.1)
    t.line([(10.9, 3.4), (9.4, 4.6)], STEEL, 0.7)
    t.line([(10.9, 3.4), (12.6, 4.0)], STEEL, 0.7)
    t.line([(12.0, 5.2), (11.2, 7.0)], STEEL, 0.7)
    t.line([(12.0, 5.2), (13.4, 6.2)], STEEL, 0.7)
    t.line([(12.4, 6.6), (14.6, 6.2)], STEEL_D, 0.6)


@ability
def dash_behind(t):
    # the target in front: dim
    t.ellipse(4.6, 2.2, 6.6, 4.2, STEEL_D)
    t.rect(4.6, 4.4, 6.6, 9.4, STEEL_D, 0.6)
    t.rect(4.9, 9.4, 5.6, 12.2, (76, 86, 108, 255), 0.4)
    t.rect(5.7, 9.4, 6.4, 12.2, (76, 86, 108, 255), 0.4)
    # Reinhard's ghost behind, offset and bright
    t.ellipse(10.4, 1.4, 12.6, 3.6, WHITE)
    t.rect(10.4, 3.8, 12.6, 9.4, WHITE, 0.6)
    t.rect(10.6, 9.6, 11.4, 12.8, WHITE_D, 0.4)
    t.rect(11.8, 9.6, 12.6, 12.8, WHITE_D, 0.4)
    # the teleport arc between them
    t.arc(3.0, 5.0, 14.0, 13.6, 210, 330, GOLD, 0.9)
    t.poly([(13.8, 11.6), (15.0, 12.6), (13.4, 13.2)], GOLD)


@ability
def stomp(t):
    # the raised leg: thigh up, knee, boot driving down
    t.poly([(5.8, 3.2), (8.6, 2.6), (9.6, 6.6), (7.2, 7.4)], WHITE)
    t.rect(5.9, 3.2, 8.5, 4.8, STEEL, 0.5)
    t.ellipse(7.0, 6.2, 9.4, 8.4, STEEL)
    t.poly([(8.2, 7.4), (11.0, 8.0), (12.6, 10.4), (9.0, 11.2)], WHITE)
    t.rect(9.6, 9.4, 12.8, 11.4, GOLD, 0.5)
    # ground and cracks
    t.line([(1.0, 12.8), (15.0, 12.8)], STEEL, 1.1)
    for x, dx in ((2.2, -1.6), (4.2, -1.0), (7.4, -0.2), (10.6, 1.0), (13.0, 1.8)):
        t.line([(x, 13.0), (x + dx, 15.2)], RED, 0.9)
    # shock arc
    t.arc(0.8, 8.8, 15.2, 14.8, 10, 170, GOLD, 0.9)


@ability
def reflect(t):
    # the raised forearm
    t.rect(3.0, 4.0, 5.4, 12.0, WHITE, 1.0)
    t.rect(3.2, 4.2, 5.2, 6.0, STEEL, 0.5)
    t.rect(2.6, 10.8, 5.8, 12.8, GOLD, 0.4)
    # the deflection arc
    t.arc(3.0, 1.0, 13.0, 11.0, 250, 70, GOLD, 1.0)
    # incoming and returned arrows
    t.line([(1.0, 2.4), (6.4, 5.2)], RED, 0.9)
    t.poly([(6.6, 4.0), (8.2, 5.6), (6.0, 6.0)], RED_D)
    t.line([(7.2, 5.6), (13.6, 2.2)], WHITE, 0.9)
    t.poly([(13.8, 1.0), (15.2, 2.4), (13.0, 2.8)], WHITE)


@ability
def reid(t):
    # scabbard, diagonal, gold fittings, hilt up
    t.poly([(4.2, 13.6), (6.0, 15.0), (12.6, 3.4), (10.8, 2.2)], BLUE)
    t.poly([(4.8, 12.8), (5.8, 13.4), (11.8, 3.2), (11.0, 2.8)], BLUE_D)
    for x, y in ((5.4, 12.0), (9.4, 4.6)):
        t.line([(x - 0.8, y + 0.9), (x + 0.9, y - 0.9)], GOLD, 1.0)
    # the hilt above the mouth
    t.line([(12.0, 2.8), (13.6, 0.6)], GOLD, 1.4)
    t.line([(10.4, 2.2), (13.6, 0.4)], GOLD, 1.2)
    t.ellipse(13.2, 0.2, 14.4, 1.4, RED)
    # sheathed glint: a white edge along the scabbard so it reads on the navy plate
    t.poly([(5.0, 13.2), (5.6, 13.6), (12.2, 3.0), (11.7, 2.6)], (208, 220, 244, 255))
    t.line([(5.2, 13.0), (10.6, 3.6)], STEEL_D, 0.5)


@ability
def reid_drawn(t):
    # drawn blade with a red glint, gold guard, blue grip
    t.poly([(9.2, 2.4), (11.6, 0.8), (13.8, 13.2), (11.4, 14.2)], WHITE)
    t.line([(10.2, 2.4), (12.6, 12.6)], STEEL_D, 0.6)
    t.line([(11.0, 2.6), (12.0, 8.0)], RED, 0.6)
    t.line([(8.4, 2.2), (12.4, 0.2)], GOLD, 1.3)
    t.line([(11.0, 0.6), (9.4, 3.4)], BLUE, 1.3)
    t.ellipse(8.9, 3.1, 10.3, 4.5, GOLD)


@ability
def lethal_chop(t):
    # broad white-red arc across the tile
    t.arc(0.4, 0.4, 15.6, 13.6, 200, 340, WHITE, 1.2)
    t.arc(0.4, 2.4, 15.6, 15.6, 200, 340, RED, 0.8)
    # the striking hand low-left
    t.rect(1.0, 10.0, 4.6, 14.2, STEEL, 0.8)
    t.rect(0.8, 12.6, 2.4, 14.6, GOLD, 0.4)
    # blood drops
    for x, y in ((6.0, 5.4), (8.4, 4.2), (10.8, 3.4)):
        t.ellipse(x, y, x + 0.9, y + 0.9, RED)


@ability
def sheath_strike(t):
    # horizontal sheathed sword swung across
    t.rect(2.0, 7.0, 12.2, 8.8, BLUE, 0.5)
    t.rect(11.0, 6.6, 12.8, 9.2, GOLD, 0.5)
    t.rect(2.0, 7.2, 3.6, 8.6, GOLD_D, 0.4)
    t.line([(0.8, 7.8), (2.2, 7.8)], GOLD, 1.2)
    # the arc and the stun stars
    t.arc(1.0, 1.6, 15.0, 14.4, 200, 340, WHITE, 1.0)
    for x, y in ((6.4, 3.2), (9.6, 2.2), (12.4, 3.4)):
        t.line([(x - 0.9, y), (x + 0.9, y)], WHITE, 0.7)
        t.line([(x, y - 0.9), (x, y + 0.9)], WHITE, 0.7)


@ability
def cut_rain(t):
    # a fan of cuts from the lower left
    origin = (2.4, 13.6)
    for i, angle in enumerate((0, 14, 28, 42, 56, 70)):
        rad = angle * 3.14159 / 180.0
        x = origin[0] + 12.4 * (0.55 + 0.45 * __import__("math").cos(rad))
        y = origin[1] - 12.4 * (0.4 + 0.6 * __import__("math").sin(rad))
        t.line([origin, (x, y)], WHITE if i % 2 == 0 else GOLD, 0.8)
    for x, y in ((7.0, 8.0), (9.6, 6.2), (11.6, 7.4), (8.6, 10.4)):
        t.line([(x - 0.8, y + 0.8), (x + 0.8, y - 0.8)], RED, 0.6)
    t.rect(1.2, 12.4, 3.8, 14.6, WHITE, 0.6)


@ability
def world_cut(t):
    # the white line and the trench under it
    t.line([(0.8, 7.0), (15.2, 7.0)], WHITE, 1.4)
    t.line([(0.8, 8.4), (15.2, 8.4)], (255, 255, 255, 120), 0.6)
    t.poly([(3.0, 9.0), (13.0, 9.0), (10.0, 14.6), (6.0, 14.6)], (26, 20, 30, 255))
    t.poly([(3.0, 9.0), (5.0, 9.0), (4.4, 11.6), (2.8, 11.2)], STEEL_D)
    t.poly([(13.0, 9.0), (11.0, 9.0), (11.6, 11.6), (13.2, 11.2)], STEEL_D)
    for x in (5.4, 8.0, 10.6):
        t.line([(x, 14.4), (x + 0.6, 15.4)], RED, 0.6)
    # dust above the line
    for x, y in ((3.4, 4.6), (7.2, 3.4), (11.4, 4.2), (13.6, 2.6)):
        t.ellipse(x, y, x + 0.9, y + 0.9, GOLD)


@ability
def world_erasure(t):
    # the figure dissolving into vertical shards
    t.rect(6.4, 4.0, 9.6, 11.0, WHITE, 0.8)
    t.ellipse(6.6, 1.6, 9.4, 4.4, WHITE)
    t.rect(6.0, 11.0, 7.4, 14.4, WHITE_D, 0.4)
    t.rect(8.6, 11.0, 10.0, 14.4, WHITE_D, 0.4)
    # shards drifting off
    for x, y, h in ((4.2, 5.0, 2.0), (3.0, 8.0, 1.4), (11.8, 4.6, 2.6), (13.0, 8.2, 1.8), (11.4, 12.0, 1.2)):
        t.rect(x, y, x + 0.9, y + h, STEEL_D, 0.2)
    # the cracks across the world
    t.line([(0.8, 2.0), (5.0, 6.0), (3.8, 9.0)], GOLD, 0.6)
    t.line([(15.2, 1.4), (12.0, 5.2), (14.0, 9.6)], GOLD, 0.6)
    t.line([(1.0, 14.6), (4.6, 12.4)], GOLD_D, 0.5)


# --- mode icons ----------------------------------------------------------

def mode_plate(top, bottom):
    return Tile(12, top, bottom)


def mode_restrained():
    t = mode_plate((40, 56, 104, 255), (16, 22, 46, 255))
    # a relaxed open hand: four fingers with visible gaps over the palm
    t.rect(3.6, 6.6, 8.4, 10.6, WHITE, 0.7)
    for x0 in (4.0, 5.4, 6.8):
        t.rect(x0, 3.0, x0 + 1.0, 7.0, WHITE, 0.3)
    t.rect(8.0, 4.0, 9.0, 7.2, WHITE, 0.3)
    t.rect(2.8, 8.4, 3.8, 10.8, GOLD, 0.3)
    t.line([(4.2, 7.6), (8.2, 7.6)], STEEL_D, 0.4)
    return t.finish()


def mode_knight():
    t = mode_plate((34, 48, 92, 255), (12, 18, 40, 255))
    # the fighting fist, knuckle plate and cuff
    t.rect(3.4, 4.2, 8.2, 10.0, WHITE, 0.8)
    t.rect(3.6, 4.4, 8.0, 6.0, STEEL, 0.4)
    t.rect(7.8, 5.0, 9.0, 9.6, STEEL, 0.4)
    t.rect(2.6, 9.0, 3.8, 11.4, GOLD, 0.3)
    for y0 in (6.6, 8.0):
        t.line([(4.0, y0), (7.6, y0)], STEEL_D, 0.4)
    # the off hand held behind the back: dim, further away
    t.rect(9.4, 6.4, 10.6, 9.0, (120, 140, 184, 170), 0.4)
    return t.finish()


def mode_holy_sword():
    t = mode_plate((70, 54, 26, 255), (26, 18, 10, 255))
    # upright sword hilt: blade, gold guard, blue grip, red pommel
    t.rect(5.4, 1.0, 6.9, 7.0, WHITE, 0.3)
    t.line([(6.15, 1.4), (6.15, 6.6)], STEEL_D, 0.3)
    t.line([(3.6, 7.2), (8.8, 7.2)], GOLD, 1.2)
    t.rect(5.3, 7.6, 7.0, 10.4, BLUE, 0.4)
    t.ellipse(5.2, 10.0, 7.1, 11.6, GOLD)
    return t.finish()


ABILITIES = {
    "light_punch": light_punch,
    "step_dash": step_dash,
    "catch": catch_projectile,
    "fist_combo": fist_combo,
    "sky_kick": sky_kick,
    "grab_throw": grab_throw,
    "dash_behind": dash_behind,
    "stomp": stomp,
    "reflect": reflect,
    "reid": reid,
    "reid_drawn": reid_drawn,
    "lethal_chop": lethal_chop,
    "sheath_strike": sheath_strike,
    "cut_rain": cut_rain,
    "world_cut": world_cut,
    "world_erasure": world_erasure,
}

DRAWN = {
    "mode_restrained": mode_restrained,
    "mode_knight": mode_knight,
    "mode_holy_sword": mode_holy_sword,
}
COPIED = {"emblem": "knight_emblem.png", "hero": "reinhard.png", "blessing_bearer": "blessing_bearer.png"}


def find_source(assets, name):
    for candidate in (os.path.join(assets, "icons", name), os.path.join(assets, name)):
        if os.path.isfile(candidate):
            return candidate
    sys.exit("missing %s in %s" % (name, assets))


def write(out_root, name, data, gui=GUI):
    target = os.path.join(out_root, gui + name + ".png.b64")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w", encoding="ascii", newline="\n") as f:
        f.write(base64.b64encode(data).decode("ascii") + "\n")
    os.makedirs(PREVIEW, exist_ok=True)
    with open(os.path.join(PREVIEW, name + ".png"), "wb") as f:
        f.write(data)
    print("wrote", os.path.relpath(target, REPO))


def png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", help="unpacked reinhard_assets archive (copied files only)")
    parser.add_argument("--out", default=BIN)
    parser.add_argument("--only", help="rebuild one file (name without .png)")
    args = parser.parse_args()
    names = list(COPIED) + list(DRAWN) + list(ABILITIES)
    if args.only:
        if args.only not in names:
            sys.exit("unknown name %s; known: %s" % (args.only, ", ".join(names)))
        names = [args.only]
    for name in names:
        if name in COPIED:
            if not args.assets:
                print("skipped", name, "(no --assets)")
                continue
            with open(find_source(args.assets, COPIED[name]), "rb") as f:
                write(args.out, name, f.read())
        elif name in ABILITIES:
            write(args.out, name, png_bytes(ABILITIES[name]()), ABILITY_GUI)
        else:
            write(args.out, name, png_bytes(DRAWN[name]()))


if __name__ == "__main__":
    main()
