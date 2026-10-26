"""Reinhard ability icons in the style of the Earth status icon (blessing_bearer).

Original deterministic pixel art. Every icon is authored on the same 32x32 grid as
textures/gui/reinhard/blessing_bearer.png (that file is copied from the user
archive and is only read here): its two-pixel bevel frame is reused pixel for
pixel and re-tinted per Reinhard mode (Restrained silver, Knight blue, Holy Sword
gold), the charcoal radial backdrop, navy outlines, top-left ramp shading and
plus-shaped sparkles follow it too. The finished 32x32 image is upscaled 2x
with nearest-neighbour to the 64x64 ability texture size, so one art pixel is
one screen pixel at GUI scale 2 (icons are blitted at 16x16 GUI units).
Colours come from the Earth icon plus the Endesga 64 palette it is built on.
Imported by make_reinhard_icons.py, which owns output paths and base64 packaging.
"""
import base64
import io
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
EARTH = os.path.join(REPO, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore",
                     "textures", "gui", "reinhard", "blessing_bearer.png.b64")


def hx(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


# ---------------------------------------------------------------- palette
OUT = hx("172038")          # navy outline, as on the Earth icon pedestal
INK = hx("0e071b")          # deepest shadow / void
BG = [hx(c) for c in ("131313", "1b1b1b", "272727", "3d3d3d", "5d5d5d")]
SILVER = [hx(c) for c in ("424c6e", "657392", "92a1b9", "c7cfdd", "ffffff")]
PALE = [hx(c) for c in ("858585", "cad0d7", "dbe2e5", "ffffff")]
CYAN = [hx(c) for c in ("00396d", "0069aa", "0098dc", "00cdf9", "0cf1ff", "94fdff")]
TEAL = [hx(c) for c in ("134c4c", "11727a", "1aaaa7", "4aedd9", "a1fbe8")]
STEEL = [hx(c) for c in ("253a5e", "3c5e8b", "4f8fba", "73bed3", "a4dddb")]
GOLD = [hx(c) for c in ("a17e4b", "ceaf67", "f1db4d", "fae875")]
SKIN = [hx(c) for c in ("8a4836", "bf6f4a", "e69c69", "f6ca9f", "f9e6cf")]
HAIR = [hx(c) for c in ("571c27", "891e2b", "c42430", "ea323c", "f5555d")]
BLOOD = [hx(c) for c in ("571c27", "891e2b", "c42430", "ea323c", "f68187")]
VOID = [hx(c) for c in ("0e071b", "1a1932", "3b1443", "622461", "93388f", "ca52c9", "fdd2ed")]
NIGHT = [hx(c) for c in ("03193f", "0c2e44", "00396d", "1a1932", "2a2f4e", "424c6e")]
EARTHY = [hx(c) for c in ("1e6f50", "33984b", "5ac54f", "99e65f")]
ROCK = [hx(c) for c in ("391f21", "5d2c28", "8a4836", "bf6f4a")]
ENEMY = [hx(c) for c in ("1a1932", "2a2f4e", "424c6e", "657392")]
WHITE = hx("ffffff")
YELLOW = hx("fae875")
CLEAR = (0, 0, 0, 0)

# Earth frame shades (outer bevel to inner rim) and their per-mode replacements.
FRAME_SRC = [hx(c) for c in ("ffffff", "a1fbe8", "4aedd9", "1aaaa7", "11727a")]
FRAME_MODE = {
    "restrained": [hx(c) for c in ("ffffff", "dbe2e5", "cad0d7", "92a1b9", "657392")],
    "knight": [hx(c) for c in ("ffffff", "a4dddb", "73bed3", "4f8fba", "3c5e8b")],
    "holy": [hx(c) for c in ("ffffff", "fae875", "f1db4d", "ceaf67", "a17e4b")],
}

_EARTH = None


def _earth():
    global _EARTH
    if _EARTH is None:
        with open(EARTH) as f:
            _EARTH = Image.open(io.BytesIO(base64.b64decode(f.read()))).convert("RGBA")
    return _EARTH


def frame_and_inside():
    """Frame pixels {(x, y): shade index} and the set of interior pixels of the Earth icon."""
    im = _earth()
    p = im.load()
    frame, inside = {}, set()
    for y in range(32):
        for x in range(32):
            c = p[x, y]
            if c[3] == 0:
                continue
            band = x <= 2 or x >= 29 or y <= 2 or y >= 29
            if band and c in FRAME_SRC:
                frame[(x, y)] = FRAME_SRC.index(c)
            else:
                inside.add((x, y))
    return frame, inside


# ---------------------------------------------------------------- masks (sets of pixels)
def disc(cx, cy, r):
    return {(x, y) for x in range(32) for y in range(32) if (x - cx) ** 2 + (y - cy) ** 2 <= r * r}


def rect(x0, y0, x1, y1):
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}


def poly(pts):
    out = set()
    n = len(pts)
    for y in range(32):
        for x in range(32):
            inside, j = False, n - 1
            for i in range(n):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                    inside = not inside
                j = i
            if inside:
                out.add((x, y))
    return out


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L = dx * dx + dy * dy
    t = 0 if L == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / L))
    return math.hypot(px - ax - t * dx, py - ay - t * dy)


def line(ax, ay, bx, by, w=1.0):
    """Thick segment; w=1 gives a clean single-pixel Bresenham-like line."""
    if w <= 1.0:
        out = set()
        n = max(abs(bx - ax), abs(by - ay), 1)
        for i in range(int(round(n)) + 1):
            t = i / max(1, round(n))
            out.add((int(round(ax + (bx - ax) * t)), int(round(ay + (by - ay) * t))))
        return out
    return {(x, y) for x in range(32) for y in range(32) if seg_dist(x, y, ax, ay, bx, by) <= w / 2}


def polyline(pts, w=1.0):
    out = set()
    for a, b in zip(pts, pts[1:]):
        out |= line(a[0], a[1], b[0], b[1], w)
    return out


def arc(cx, cy, r, w, a0, a1, ry=None):
    """Band of width w around an (elliptic) circle between angles a0..a1 (degrees, y down)."""
    ry = r if ry is None else ry
    out = set()
    for x in range(32):
        for y in range(32):
            dx, dy = (x - cx) / r, (y - cy) / ry
            d = math.hypot(dx, dy) * r
            a = math.degrees(math.atan2(dy, dx)) % 360
            lo, hi = a0 % 360, a1 % 360
            ok = lo <= a <= hi if lo <= hi else (a >= lo or a <= hi)
            if ok and abs(d - r) <= w / 2:
                out.add((x, y))
    return out


def grow(m, diag=False):
    nb = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (1, -1), (-1, 1), (-1, -1)] if diag else [])
    return m | {(x + dx, y + dy) for x, y in m for dx, dy in nb}


def shrink(m):
    return {(x, y) for x, y in m if all((x + dx, y + dy) in m for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


# ---------------------------------------------------------------- canvas
class Icon:
    def __init__(self, mode, center=(16, 15), bg=True):
        self.frame, self.inside = frame_and_inside()
        self.mode = mode
        self.px = {}
        if bg:
            self.backdrop(center)

    def backdrop(self, center, rings=(17.5, 14.0, 10.5, 7.0)):
        cx, cy = center
        for x, y in self.inside:
            d = math.hypot(x - cx, (y - cy) * 1.05)
            lvl = 4
            for i, r in enumerate(rings):
                if d > r:
                    lvl = i
                    break
            self.px[(x, y)] = BG[lvl]

    def ok(self, x, y):
        return (x, y) in self.inside

    def set(self, x, y, c):
        if self.ok(x, y):
            self.px[(x, y)] = c

    def get(self, x, y):
        return self.px.get((x, y), BG[0])

    def fill(self, mask, c):
        for x, y in mask:
            self.set(x, y, c)

    def mix(self, mask, c, a):
        for x, y in mask:
            if self.ok(x, y):
                b = self.get(x, y)
                self.px[(x, y)] = tuple(int(round(b[i] * (1 - a) + c[i] * a)) for i in range(3)) + (255,)

    def put(self, sprite):
        for (x, y), c in sprite.items():
            self.set(x, y, c)

    def outline(self, mask, c=OUT, diag=False):
        self.fill(grow(mask, diag) - mask, c)

    def solid(self, mask, ramp, outline=OUT, diag=False, light=(-1, -1), rim=True):
        """Outline + flat shade with a lit top-left rim and a dark bottom-right rim.
        ramp: (dark, mid, light[, highlight])."""
        if outline is not None:
            self.outline(mask, outline, diag)
        lx, ly = light
        dark, mid, lit = ramp[0], ramp[1], ramp[2]
        for x, y in mask:
            c = mid
            if rim:
                lit_edge = (x + lx, y) not in mask or (x, y + ly) not in mask
                dark_edge = (x - lx, y) not in mask or (x, y - ly) not in mask
                if lit_edge and not dark_edge:
                    c = lit
                elif dark_edge and not lit_edge:
                    c = dark
            self.set(x, y, c)
        if len(ramp) > 3:
            for x, y in mask:
                if ((x + lx, y) not in mask and (x, y + ly) not in mask):
                    self.set(x, y, ramp[3])

    def sphere(self, mask, cx, cy, r, ramp, outline=OUT, lx=-0.55, ly=-0.65):
        if outline is not None:
            self.outline(mask, outline)
        n = len(ramp)
        for x, y in mask:
            nx, ny = (x - cx) / r, (y - cy) / r
            nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
            lum = max(0.0, nx * lx + ny * ly + nz * 0.55)
            self.set(x, y, ramp[min(n - 1, int(lum * n))])

    def sparkle(self, x, y, big=True, col=None):
        if big:
            for d in (1, 2):
                for dx, dy in ((d, 0), (-d, 0), (0, d), (0, -d)):
                    self.set(x + dx, y + dy, PALE[1] if d == 2 else PALE[2])
            self.set(x, y, WHITE)
        else:
            c = col or YELLOW
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                self.set(x + dx, y + dy, c)
            self.set(x, y, WHITE if col is None else col)

    def image(self):
        im = Image.new("RGBA", (32, 32), CLEAR)
        p = im.load()
        ramp = FRAME_MODE[self.mode]
        for (x, y), i in self.frame.items():
            p[x, y] = ramp[i]
        for (x, y), c in self.px.items():
            p[x, y] = c
        return im


# ---------------------------------------------------------------- reusable pieces


def speed_lines(ic, rows, cols=(PALE[0], PALE[1], PALE[3])):
    for (y, a, b) in rows:
        n = max(1, b - a)
        for i, x in enumerate(range(a, b + 1)):
            t = i / n
            ic.set(x, y, cols[min(len(cols) - 1, int(t * len(cols)))])


def burst(ic, cx, cy, r, ramp=(CYAN[2], CYAN[4], WHITE), spikes=8, rot=0.0):
    """Impact star: spiky polygon with bright core."""
    pts = []
    for i in range(spikes * 2):
        a = math.radians(rot + i * 180 / spikes)
        rr = r if i % 2 == 0 else r * 0.45
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    m = poly(pts) | {(int(round(cx)), int(round(cy)))}
    ic.fill(grow(m) - m, OUT)
    ic.fill(m, ramp[0])
    ic.fill(shrink(m) | (m & disc(cx, cy, r * 0.5)), ramp[1])
    ic.fill(disc(cx, cy, max(0.8, r * 0.28)), ramp[2])
    return m


# ---------------------------------------------------------------- icons
def icon32(name):
    return ICONS[name]()


def icon(name):
    return icon32(name).resize((64, 64), Image.NEAREST)


ICONS = {}


def art(fn):
    ICONS[fn.__name__] = fn
    return fn


# ---------------------------------------------------------------- strokes
def bezier(pts, t):
    while len(pts) > 1:
        pts = [(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t) for a, b in zip(pts, pts[1:])]
    return pts[0]


def stroke(ctrl, w0, wm, w1, steps=160):
    """Tapered Bezier stroke -> {(x, y): d} with d = distance / half-width (0 centre .. 1 edge)."""
    samples = []
    for i in range(steps + 1):
        t = i / steps
        w = (w0 + (wm - w0) * (t / 0.5)) if t < 0.5 else (wm + (w1 - wm) * ((t - 0.5) / 0.5))
        samples.append((bezier(ctrl, t), max(0.05, w / 2)))
    out = {}
    xs = [p[0][0] for p in samples]
    ys = [p[0][1] for p in samples]
    for x in range(int(min(xs)) - 4, int(max(xs)) + 5):
        for y in range(int(min(ys)) - 4, int(max(ys)) + 5):
            best = min(math.hypot(x - px, y - py) / hw for (px, py), hw in samples)
            if best <= 1.0:
                out[(x, y)] = best
    return out


def paint_stroke(ic, st, ramp, outline=None, cuts=(0.4, 0.75)):
    """ramp = (edge, mid, core)."""
    m = set(st)
    if outline is not None:
        ic.fill(grow(m) - m, outline)
    for p, d in st.items():
        ic.set(p[0], p[1], ramp[2] if d < cuts[0] else ramp[1] if d < cuts[1] else ramp[0])
    return m


def ring_pts(cx, cy, rx, ry, a0=0, a1=360, step=2):
    out = set()
    a = a0
    while a <= a1:
        r = math.radians(a)
        out.add((int(round(cx + rx * math.cos(r))), int(round(cy + ry * math.sin(r)))))
        a += step
    return out


# ---------------------------------------------------------------- hand-drawn sprites
SKIN_PAL = {"h": SKIN[4], "l": SKIN[3], "m": SKIN[2], "s": SKIN[1], "d": SKIN[0]}
BOOT_PAL = {"h": ROCK[3], "l": ROCK[2], "m": ROCK[1], "s": ROCK[0], "k": INK, "n": BG[4],
            "g": GOLD[2], "G": GOLD[3], "q": GOLD[0]}

FIST_R = [
    "...lllll.....",
    "..lhhhhhlll..",
    ".lhllllllhhl.",
    "lhlllllllmlll",
    "lllllllllmsd.",
    "llllllllmllll",
    "lmdddddddmsd.",
    "mlhhhhhhhdlll",
    "mmllllllldsd.",
    ".smmmmmmmmml.",
    "..sssssssss..",
]

FIST_FRONT = [
    ".hl..hl..hl..h.",
    "hhllhhllhhllhll",
    "lllmlllmlllmllm",
    "lllslllslllslls",
    "mmmsmmmsmmmsmms",
    "lllslllslllslls",
    "hhhhhhhhlllslls",
    "lllllllldllslls",
    "mmmmmmmsmmmsmms",
    ".mllllllllllllm",
    "..mmmmmmmmmmmm.",
    "...sssssssss...",
]

THROWER = [
    ".......ff.",
    "......ww..",
    "...hhhw...",
    "..hHhhw...",
    ".hhHhhhh..",
    ".hhhffFh..",
    "..hfkff...",
    "...fff....",
    "..wwWww...",
    "..wwgww...",
    "..wWwww...",
    "..wwWww...",
    "..pp.pp...",
    ".pp...pp..",
    "pp.....pp.",
    "k.......kk",
]

KICKER = [
    "..........kk",
    ".........ppk",
    "...hhh..pp..",
    "..hHhhh.pp..",
    "..hhffF.pp..",
    "...fkf.pp...",
    "...fff.pp...",
    "f.wwWwpp....",
    "fvwwgwp.....",
    "...wWww.....",
    "...wwww.....",
    "...wWww.....",
    "....pp......",
    "....pp......",
    "....pp......",
    "....pp......",
    "...kkk......",
]

PALM = [
    "......ll......",
    "......hl.ll...",
    "...ll.hlshl...",
    "...hlshlshl...",
    "...hlshlshl...",
    "...hlshlshlll.",
    "...hlshlshlhl.",
    "...hlshlshlhm.",
    "..lhllhllhlhm.",
    "ll.lllllllllm.",
    "hll.llllllllm.",
    ".hll.lllllllm.",
    "..hll.lllllmm.",
    "...hlllllllmm.",
    "....lllllllm..",
    "....mmmmmmm...",
]

BOOT_R = [
    "..GGGGGG......",
    "..qgggggq.....",
    "..hlllms......",
    "..hlllms......",
    "..hlllms......",
    "..hlllms......",
    "..hllllms.....",
    "..hlllllmss...",
    ".hhllllllllms.",
    ".hlllllllllllm",
    ".hlmmmmmmmmmmm",
    "kkkkkkkkkkkkkk",
    ".nnn.....nnnn.",
]

KNIFE_HAND_R = [
    "...ll..........",
    "..lhhl.........",
    ".lhlllhhhhhhhh.",
    "lhllllsssssssll",
    "lllllllllllllll",
    "mmmmmmsssssssm.",
    "smmmmmmmmmmmm..",
    ".sssssssssss...",
]

RUNNER = [
    "...hhh...",
    "..hHhhh..",
    ".hhHhhhh.",
    ".hhhffFh.",
    "..hfkff..",
    "...fff...",
    "..wwWww..",
    ".vwwgwwff",
    "vv.wWw...",
    "f..wWw...",
    "..wwWww..",
    "..pp.pp..",
    ".pp...pp.",
    "pp.....pp",
    "k.......kk",
]
RUNNER_PAL = {"h": HAIR[2], "H": HAIR[3], "f": SKIN[3], "F": SKIN[4], "k": OUT, "w": SILVER[3], "W": WHITE,
              "v": SILVER[2], "g": GOLD[2], "p": STEEL[2]}

FOE = [
    "...ooo...",
    "..ooooo..",
    "..oorro..",
    "..ooooo..",
    "...ooo...",
    ".ooooooo.",
    "ooooooooo",
    "oo.ooo.oo",
    "o..ooo..o",
    "...ooo...",
    "..oo.oo..",
    "..oo.oo..",
    ".oo...oo.",
    ".oo...oo.",
]


def sprite(ic, rows, x, y, pal, outline=OUT, flipx=False, flipy=False, rot=0, diag=False):
    """Stamp an ASCII sprite with an automatic outline. rot = quarter turns clockwise."""
    pts = {}
    for b, row in enumerate(rows):
        for a, ch in enumerate(row):
            if ch not in ". ":
                pts[(a, b)] = pal[ch]
    w = max(len(r) for r in rows)
    h = len(rows)
    for _ in range(rot % 4):
        pts = {(h - 1 - b, a): c for (a, b), c in pts.items()}
        w, h = h, w
    if flipx:
        pts = {(w - 1 - a, b): c for (a, b), c in pts.items()}
    if flipy:
        pts = {(a, h - 1 - b): c for (a, b), c in pts.items()}
    pts = {(x + a, y + b): c for (a, b), c in pts.items()}
    m = set(pts)
    if outline is not None:
        ic.fill(grow(m, diag) - m, outline)
    ic.put(pts)
    return m


def foe(ic, x, y, flipx=False, flipy=False, rot=0):
    """Shadowy opponent: slate body with a lit left rim and red eyes."""
    m = sprite(ic, FOE, x, y, {"o": ENEMY[2], "r": BLOOD[3]}, flipx=flipx, flipy=flipy, rot=rot)
    for (a, b) in m:
        if ic.get(a, b) != ENEMY[2]:
            continue
        if (a - 1, b) not in m or (a, b - 1) not in m:
            ic.set(a, b, ENEMY[3])
        elif (a + 1, b) not in m or (a, b + 1) not in m:
            ic.set(a, b, ENEMY[1])
    return m


def ghost(ic, rows, x, y, pal, col, a, flipx=False):
    tmp = Icon(ic.mode, bg=False)
    m = sprite(tmp, rows, x, y, pal, outline=None, flipx=flipx)
    ic.mix(m & ic.inside, col, a)
    return m


def cuff_sleeve(ic, x0, y0, x1, y1, side="left"):
    """White coat sleeve with a gold cuff band on the hand side."""
    m = rect(x0, y0, x1, y1)
    ic.solid(m, (SILVER[2], SILVER[3], WHITE))
    if side == "left":
        band = rect(x1 - 1, y0, x1, y1)
    elif side == "top":
        band = rect(x0, y0, x1, y0 + 1)
    else:
        band = rect(x0, y1 - 1, x1, y1)
    ic.fill(band, GOLD[2])
    for x, y in band:
        if (x, y - 1) not in band:
            ic.set(x, y, GOLD[3])
        elif (x, y + 1) not in band:
            ic.set(x, y, GOLD[0])


def arrow(ic, ax, ay, bx, by, col=GOLD[1], head=PALE[2], fletch=BLOOD[3]):
    """Arrow from tail (ax, ay) to tip (bx, by): outlined shaft, steel head, red fletching."""
    shaft = line(ax, ay, bx, by)
    n = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / n, (by - ay) / n
    vx, vy = -uy, ux
    tip = {(int(round(bx - ux * k + vx * s)), int(round(by - uy * k + vy * s)))
           for k in (0, 1, 2) for s in (-k * 0.6, 0, k * 0.6)}
    fl = set()
    for k in (0, 1, 2):
        for s in (1.2, -1.2):
            fl.add((int(round(ax + ux * k + vx * s)), int(round(ay + uy * k + vy * s))))
    allm = shaft | tip | fl
    ic.fill(grow(allm) - allm, OUT)
    ic.fill(shaft, col)
    ic.fill(fl, fletch)
    ic.fill(tip, head)
    ic.set(int(round(bx)), int(round(by)), WHITE)
    return allm


def diag(x0, y0, t0, t1, w):
    """45-degree bar rising to the right: pixels (x0 + t + k, y0 - t), k centred on the axis."""
    return {(x0 + t + k, y0 - t) for t in range(t0, t1) for k in range(-(w // 2), w - w // 2)}


def across(x0, y0, x, y):
    """Signed offset of (x, y) from the 45-degree axis through (x0, y0)."""
    return x - (x0 + (y0 - y))


def sword(ic, x0, y0, length, sheathed=False, glow=False):
    """Reid on a 45-degree diagonal: pommel at (x0, y0), tip up and to the right."""
    w = 4 if sheathed else 3
    grip = diag(x0, y0, 1, 5, 2)
    pommel = rect(x0 - 1, y0, x0, y0 + 1)
    gx, gy = x0 + 5, y0 - 5
    guard = {(gx - 4 + i, gy - 4 + i) for i in range(9)} | {(gx - 3 + i, gy - 4 + i) for i in range(8)}
    end = 6 + length
    body = diag(x0, y0, 6, end, w)
    body |= {(x0 + end + k, y0 - end) for k in range(-(w // 2) + 1, w - w // 2 - 1)}
    if not sheathed:
        body |= {(x0 + end + 1, y0 - end - 1)}
    allm = grip | pommel | guard | body
    if glow:
        ic.mix(grow(body, True) - allm, CYAN[3], 0.55)
        ic.mix(grow(grow(body, True), True) - grow(body, True) - allm, CYAN[2], 0.25)
    ic.fill(grow(allm) - allm, OUT)
    for (x, y) in body:
        k = across(x0, y0, x, y)
        if sheathed:
            ic.set(x, y, WHITE if k <= -2 else SILVER[3] if k == -1 else SILVER[2] if k == 0 else SILVER[1])
        else:
            ic.set(x, y, PALE[2] if k < 0 else WHITE if k == 0 else SILVER[2])
    if sheathed:
        bands = (6, 7, 6 + length // 2, end - 2, end - 1, end)
        for t in bands:
            for (x, y) in diag(x0, y0, t, t + 1, w + 1) & grow(body):
                k = across(x0, y0, x, y)
                ic.set(x, y, GOLD[3] if k <= -1 else GOLD[2] if k == 0 else GOLD[0])
        mid = 6 + length // 2 - 4
        ic.set(x0 + mid - 1, y0 - mid, CYAN[3])
        ic.set(x0 + mid + 4, y0 - mid - 5, CYAN[3])
    else:
        for t in range(8, end - 1, 6):
            ic.set(x0 + t - 1, y0 - t, WHITE)
        ic.set(x0 + end + 1, y0 - end - 1, WHITE)
    for (x, y) in grip:
        ic.set(x, y, CYAN[1] if across(x0, y0, x, y) < 0 else CYAN[0])
    for t in (2, 4):
        ic.set(x0 + t - 1, y0 - t, CYAN[3])
    for (x, y) in guard:
        ic.set(x, y, GOLD[2] if (x - gx) - (y - gy) <= 0 else GOLD[0])
    ic.set(gx - 4, gy - 4, GOLD[3]); ic.set(gx + 4, gy + 4, GOLD[1])
    ic.set(gx, gy, BLOOD[3]); ic.set(gx - 1, gy, BLOOD[4])
    ic.fill(pommel, GOLD[1])
    ic.set(x0 - 1, y0, GOLD[3])
    return allm


def landscape(ic, horizon=19):
    """Night sky, stars, snowy mountains and a grass strip (World Cut scenes)."""
    for x, y in ic.inside:
        t = (y - 2) / max(1, horizon - 2)
        c = NIGHT[0] if t < 0.35 else (NIGHT[2] if t < 0.7 else CYAN[1])
        if y >= horizon:
            c = EARTHY[0] if y < horizon + 4 else ROCK[1] if y < horizon + 7 else ROCK[0]
        ic.px[(x, y)] = c
    for x, y in ((5, 5), (11, 3), (24, 4), (27, 9), (8, 9), (20, 7)):
        ic.set(x, y, PALE[2])
    ridge = [(2, 16), (6, 10), (9, 13), (14, 7), (19, 13), (23, 9), (30, 16), (30, horizon), (2, horizon)]
    mount = poly(ridge) | rect(2, 15, 30, horizon - 1)
    ic.fill(mount, STEEL[1])
    for x, y in mount:
        if (x, y - 1) not in mount or (x, y - 2) not in mount:
            ic.set(x, y, PALE[2] if y < 12 else STEEL[3])
        elif (x - 1, y) not in mount:
            ic.set(x, y, STEEL[2])
    back = poly([(2, 18), (5, 15), (11, 17), (17, 14), (24, 17), (30, 14), (30, horizon), (2, horizon)])
    ic.fill(back, STEEL[0])
    for x in range(2, 31):
        ic.set(x, horizon, EARTHY[2])
        ic.set(x, horizon + 1, EARTHY[1])
    for x in range(3, 30, 4):
        ic.set(x, horizon + 4, ROCK[2])
        ic.set(x + 2, horizon + 7, ROCK[1])


def glow_line(ic, mask, core=WHITE, halo=CYAN[3], outer=CYAN[1]):
    ic.mix(grow(grow(mask, True), True) - grow(mask, True), outer, 0.45)
    ic.mix(grow(mask, True) - mask, halo, 0.85)
    ic.fill(mask, core)


# ---------------------------------------------------------------- icons
@art
def light_punch():
    ic = Icon("restrained", center=(17, 15))
    speed_lines(ic, [(8, 3, 10), (10, 2, 6), (21, 3, 9), (23, 5, 10)])
    burst(ic, 25, 15, 5.6, spikes=8, rot=22.5)
    for a in (-40, 0, 40):
        r = math.radians(a)
        ic.fill(line(25 + math.cos(r) * 4, 15 + math.sin(r) * 4, 25 + math.cos(r) * 6, 15 + math.sin(r) * 6), WHITE)
    cuff_sleeve(ic, 2, 11, 9, 19)
    sprite(ic, FIST_R, 10, 10, SKIN_PAL)
    ic.sparkle(24, 5)
    ic.sparkle(7, 26, big=False)
    return ic.image()


@art
def step_dash():
    ic = Icon("restrained", center=(18, 16))
    ic.fill(line(3, 25, 29, 25), BG[4])
    ic.fill(line(8, 26, 25, 26), BG[3])
    ghost(ic, RUNNER, 6, 9, RUNNER_PAL, CYAN[1], 0.55)
    ghost(ic, RUNNER, 11, 9, RUNNER_PAL, CYAN[3], 0.7)
    sprite(ic, RUNNER, 17, 9, RUNNER_PAL)
    speed_lines(ic, [(6, 5, 13), (17, 2, 8), (21, 4, 11)], cols=(CYAN[1], CYAN[3], CYAN[5]))
    for x, y in ((15, 24), (13, 23), (14, 22), (11, 24)):
        ic.set(x, y, PALE[1])
    ic.sparkle(26, 5)
    return ic.image()


@art
def catch():
    ic = Icon("restrained", center=(18, 16))
    speed_lines(ic, [(13, 2, 6), (17, 3, 7)])
    arrow(ic, 3, 4, 12, 13)
    ic.sparkle(11, 15, big=False)
    ic.set(13, 11, WHITE); ic.set(9, 14, YELLOW)
    cuff_sleeve(ic, 17, 24, 24, 29, side="top")
    sprite(ic, PALM, 13, 8, SKIN_PAL)
    ic.sparkle(26, 5)
    ic.sparkle(5, 25, big=False)
    return ic.image()


@art
def fist_combo():
    ic = Icon("knight", center=(16, 15))
    for (x, y, r, rot) in ((5, 6, 4.2, 0), (26, 6, 4.2, 30), (16, 5, 5.2, 22.5)):
        burst(ic, x, y, r, ramp=(CYAN[1], CYAN[3], WHITE), spikes=7, rot=rot)
    for pts in (((4, 26), (3, 20), (5, 15)), ((28, 26), (29, 20), (27, 15))):
        ic.fill(set(stroke(list(pts), 0.4, 1.4, 0.4)), PALE[1])
    cuff_sleeve(ic, 11, 23, 21, 29, side="top")
    sprite(ic, FIST_FRONT, 9, 11, SKIN_PAL)
    return ic.image()


@art
def sky_kick():
    ic = Icon("knight", center=(16, 14))
    st = stroke([(14, 28), (24, 21), (17, 12)], 0.4, 3.0, 2.2)
    ic.mix(grow(set(st)) - set(st), CYAN[1], 0.6)
    paint_stroke(ic, st, (CYAN[2], CYAN[4], WHITE))
    foe(ic, 18, 2)
    for x, y0 in ((19, 18), (23, 19), (27, 17)):
        ic.fill(line(x, y0, x, y0 + 3), PALE[1])
    sprite(ic, KICKER, 4, 12, RUNNER_PAL)
    ic.sparkle(17, 11, big=False)
    ic.sparkle(6, 6)
    return ic.image()


@art
def grab_throw():
    ic = Icon("knight", center=(17, 14))
    path = stroke([(13, 12), (22, -2), (30, 8), (27, 21)], 1.0, 2.2, 1.8)
    paint_stroke(ic, path, (STEEL[2], STEEL[3], STEEL[4]), outline=OUT)
    head = {(x, y) for y in range(20, 25) for x in range(24 + (y - 20), 31 - (y - 20))}
    ic.fill(grow(head) - head, OUT)
    ic.fill(head, STEEL[4])
    foe(ic, 11, 8, rot=1)
    for x, y in ((14, 18), (17, 19), (20, 18)):
        ic.fill({(x, y), (x + 1, y + 1)}, PALE[1])
    ic.fill(line(19, 28, 30, 28), BG[4])
    for x, y in ((22, 26), (28, 25), (25, 26)):
        ic.set(x, y, PALE[1])
    sprite(ic, THROWER, 2, 13, RUNNER_PAL)
    ic.sparkle(6, 6)
    return ic.image()


@art
def dash_behind():
    ic = Icon("knight", center=(15, 16))
    path = stroke([(6, 11), (14, -2), (26, 10)], 1.6, 2.2, 1.0)
    m = paint_stroke(ic, path, (TEAL[2], TEAL[3], TEAL[4]), outline=OUT)
    ic.fill({(24, 12), (25, 12), (26, 12), (27, 12), (28, 12), (25, 13), (26, 13), (27, 13), (26, 14)}, TEAL[4])
    ic.fill({(23, 11), (29, 11)}, OUT)
    ghost(ic, RUNNER, 2, 12, RUNNER_PAL, CYAN[2], 0.5)
    foe(ic, 10, 13)
    sprite(ic, RUNNER, 21, 14, RUNNER_PAL, flipx=True)
    ic.fill({(20, 28), (21, 27), (30, 28)}, PALE[1])
    return ic.image()


@art
def stomp():
    ic = Icon("knight", center=(16, 12))
    ground = rect(2, 21, 30, 29)
    ic.fill(ground, BG[3])
    ic.fill(rect(2, 21, 30, 21), PALE[0])
    ic.fill(rect(2, 22, 30, 22), BG[4])
    ic.fill(rect(2, 27, 30, 29), BG[2])
    for x in range(4, 30, 6):
        ic.fill({(x, 24), (x + 1, 24), (x + 3, 26)}, BG[4])
    for pts in (((16, 22), (13, 25), (14, 27), (11, 29)), ((17, 22), (20, 24), (19, 26), (23, 29)),
                ((14, 22), (8, 24), (3, 23)), ((19, 22), (25, 23), (29, 25))):
        ic.fill(polyline(pts), INK)
    ic.fill(ring_pts(16, 20, 14, 3, 160, 380), CYAN[2])
    ic.fill(ring_pts(16, 20, 10, 2, 165, 375), CYAN[4])
    for x, y, c in ((4, 14, PALE[0]), (5, 12, PALE[1]), (27, 13, PALE[0]), (26, 16, PALE[1]), (7, 9, PALE[0])):
        ic.fill(rect(x, y, x + 1, y), c)
        ic.set(x, y + 1, BG[4])
    for x in (8, 24):
        ic.fill(line(x, 3, x, 8), PALE[1])
    sprite(ic, BOOT_R, 9, 7, BOOT_PAL)
    ic.fill(line(16, 2, 16, 4), PALE[2])
    return ic.image()


@art
def reflect():
    ic = Icon("knight", center=(12, 16))
    shield = stroke([(10, 3), (19, 16), (10, 29)], 0.8, 3.2, 0.8)
    ic.mix(grow(set(shield), True) - set(shield), TEAL[1], 0.8)
    paint_stroke(ic, shield, (TEAL[2], TEAL[3], TEAL[4]))
    arrow(ic, 29, 26, 19, 19, col=GOLD[1])
    arrow(ic, 19, 14, 28, 5, col=CYAN[3], head=WHITE, fletch=CYAN[4])
    ic.sparkle(18, 16)
    sprite(ic, KNIFE_HAND_R, 3, 7, SKIN_PAL, rot=3)
    cuff_sleeve(ic, 3, 23, 10, 29, side="top")
    return ic.image()


@art
def reid():
    ic = Icon("holy", center=(16, 15))
    sword(ic, 5, 26, 15, sheathed=True)
    ic.sparkle(23, 23)
    ic.sparkle(8, 7, big=False)
    ic.sparkle(27, 13, big=False, col=CYAN[4])
    return ic.image()


@art
def reid_drawn():
    ic = Icon("holy", center=(19, 12))
    cx, cy = 21, 10
    for a in range(0, 360, 30):
        m = line(cx + math.cos(math.radians(a)) * 5, cy + math.sin(math.radians(a)) * 5,
                 cx + math.cos(math.radians(a)) * 16, cy + math.sin(math.radians(a)) * 16)
        ic.mix(m, GOLD[3] if a % 60 == 0 else GOLD[1], 0.5)
    ic.mix(disc(cx, cy, 5), GOLD[3], 0.3)
    sword(ic, 5, 26, 16, glow=True)
    ic.sparkle(26, 5)
    ic.sparkle(7, 8, big=False)
    return ic.image()


@art
def lethal_chop():
    ic = Icon("holy", center=(16, 16))
    st = stroke([(3, 5), (6, 21), (21, 25)], 0.6, 4.6, 1.2)
    ic.mix(grow(set(st), True) - set(st), BLOOD[0], 0.8)
    paint_stroke(ic, st, (BLOOD[2], BLOOD[3], BLOOD[4]))
    core = stroke([(4, 8), (7, 21), (19, 24)], 0.3, 1.2, 0.3)
    ic.fill(set(core), WHITE)
    for x, y in ((12, 28), (25, 28), (8, 27)):
        ic.fill({(x, y), (x, y - 1)}, BLOOD[2])
        ic.set(x, y - 2, BLOOD[3])
    sprite(ic, KNIFE_HAND_R, 14, 18, SKIN_PAL)
    for y in (12, 14):
        speed_lines(ic, [(y, 15, 22)])
    ic.sparkle(25, 8)
    return ic.image()


@art
def sheath_strike():
    ic = Icon("holy", center=(16, 16))
    for (ctrl, w, c) in (([(2, 10), (8, 2), (19, 3)], 1.6, PALE[1]), ([(3, 16), (10, 6), (20, 5)], 1.2, PALE[0])):
        ic.fill(set(stroke(ctrl, 0.4, w, 0.4)), c)
    sword(ic, 4, 27, 13, sheathed=True)
    burst(ic, 25, 6, 4.2, ramp=(GOLD[1], GOLD[3], WHITE), spikes=7)
    for x, y in ((15, 3), (28, 9), (27, 17)):
        ic.sparkle(x, y, big=False)
    return ic.image()


def flat_saw(ic, sx, sy, rx, ry):
    """A gold blade-saw seen almost edge-on: lit top face, dark rim, alternating teeth, white hub."""
    body = {(x, y) for x in range(32) for y in range(32) if ((x - sx) / rx) ** 2 + ((y - sy) / ry) ** 2 <= 1}
    teeth = set()
    for i, a in enumerate(range(0, 360, 20)):
        if i % 2 == 0:
            t = (int(round(sx + (rx + 1) * math.cos(math.radians(a)))), int(round(sy + (ry + 0.7) * math.sin(math.radians(a)))))
            if t not in body:
                teeth.add(t)
    ic.outline(body | teeth, OUT)
    ic.fill(body, GOLD[1])
    ic.fill({(x, y) for (x, y) in body if y < sy}, GOLD[2])
    ic.fill({(x, y) for (x, y) in body if y < sy and abs(x - sx) < rx * 0.45}, GOLD[3])
    ic.fill({(x, y) for (x, y) in body if y > sy}, GOLD[0])
    ic.fill(teeth, GOLD[3])
    ic.set(int(round(sx)), int(round(sy)), WHITE)


@art
def saw_sphere():
    """A translucent cyan barrier sphere cut off by the ground, gold blade-saws lying flat along its shell and a
    tiny Reinhard (red hair, white coat, Reid out) spinning at its heart."""
    ic = Icon("holy", center=(16, 17))
    cx, cy, r, ground = 16, 15.5, 11.8, 25
    ic.fill(rect(2, ground + 1, 29, 29), BG[2])
    ic.fill(rect(2, ground + 1, 29, ground + 1), BG[4])
    ball = {(x, y) for (x, y) in disc(cx, cy, r) if y <= ground}
    ic.mix(ball, CYAN[1], 0.35)
    ic.mix(ball & disc(cx - 4, cy - 5, 6), CYAN[4], 0.18)
    ic.fill(grow(ball) - ball - rect(0, ground + 1, 31, 31), OUT)
    shell = ball - shrink(ball) - rect(0, ground, 31, ground)
    ic.fill(shell, CYAN[3])
    ic.fill({(x, y) for (x, y) in shell if x - cx + y - cy < -9}, CYAN[5])
    ic.fill(arc(cx, cy, r - 2.2, 1, 200, 245), CYAN[4])
    ic.mix(rect(cx - 11, ground, cx + 11, ground), CYAN[4], 0.8)
    for (sx, sy, rx) in ((9, 10, 3.3), (23.5, 12, 3.3), (8.5, 20.5, 3.5), (23.5, 21.5, 3.5)):
        flat_saw(ic, sx, sy, rx, 1.3)
    # the swing: a gold arc wrapped round him, front half bright
    ic.fill(arc(16, 17, 7, 1, 10, 170, ry=2.6), GOLD[3])
    ic.fill(arc(16, 17, 7, 1, 190, 350, ry=2.6), GOLD[0])
    # tiny Reinhard
    hair = {(15, 11), (16, 11), (17, 11), (14, 12), (15, 12), (16, 12), (17, 12), (18, 12), (14, 13), (18, 13)}
    face = {(15, 13), (16, 13), (17, 13), (15, 14), (16, 14), (17, 14)}
    coat = rect(14, 15, 18, 19) | {(13, 16)}
    legs = {(15, 20), (15, 21), (17, 20), (17, 21)}
    ic.outline(hair | face | coat | legs, OUT)
    ic.fill(hair, HAIR[3])
    ic.set(16, 11, HAIR[4])
    ic.fill(face, SKIN[3])
    ic.set(17, 14, SKIN[2])
    ic.fill(coat, PALE[2])
    ic.fill({(18, 15), (18, 16), (18, 17), (18, 18), (18, 19)}, PALE[1])
    ic.fill({(16, 15), (16, 16)}, GOLD[2])
    ic.fill(legs, STEEL[0])
    ic.fill(line(19, 16, 26, 16), WHITE)
    ic.set(19, 16, GOLD[2])
    ic.set(27, 16, PALE[2])
    ic.sparkle(27, 5)
    ic.sparkle(5, 4, big=False, col=CYAN[5])
    return ic.image()


@art
def world_cut():
    ic = Icon("holy", bg=False)
    landscape(ic)
    snap = dict(ic.px)
    for (x, y) in ic.inside:
        if x > 16 and (x, y - 2) in snap:
            ic.px[(x, y)] = snap[(x, y - 2)]
    glow_line(ic, line(16, 2, 16, 29))
    ic.sparkle(16, 11)
    return ic.image()


@art
def diagonal_world_cut():
    ic = Icon("holy", bg=False)
    landscape(ic)
    snap = dict(ic.px)
    for (x, y) in ic.inside:
        if x + y < 31 and (x + 1, y + 1) in snap:
            ic.px[(x, y)] = snap[(x + 1, y + 1)]
    glow_line(ic, {(x, 31 - x) for x in range(2, 30)})
    ic.sparkle(17, 14)
    return ic.image()


@art
def world_erasure():
    ic = Icon("holy", bg=False)
    landscape(ic)
    cx, cy, r = 16, 15, 8.5
    ic.mix(disc(cx, cy, r + 2.5) - disc(cx, cy, r), VOID[4], 0.45)
    core = disc(cx, cy, r)
    ic.sphere(core, cx - 2, cy - 2, r + 2, [VOID[0], VOID[0], VOID[1], VOID[2], VOID[3]], outline=None,
              lx=0.6, ly=0.6)
    ic.fill(core - shrink(core), VOID[6])
    ic.fill(arc(cx, cy, r - 1, 1, 200, 260), VOID[5])
    for x, y in ((14, 12), (19, 17), (12, 18), (18, 11)):
        ic.set(x, y, VOID[5])
    for x, y, c in ((5, 8, STEEL[3]), (26, 7, PALE[2]), (27, 22, EARTHY[2]), (6, 23, ROCK[2]), (24, 26, EARTHY[1]),
                    (4, 15, STEEL[2]), (28, 14, STEEL[2])):
        ic.fill(rect(x, y, x + 1, y + 1), c)
        ic.set(x + (1 if x < cx else -1), y + (1 if y < cy else -1), VOID[5])
    return ic.image()


NAMES = ['light_punch', 'step_dash', 'catch', 'fist_combo', 'sky_kick', 'grab_throw', 'dash_behind', 'stomp',
         'reflect', 'reid', 'reid_drawn', 'lethal_chop', 'sheath_strike', 'saw_sphere', 'world_cut', 'world_erasure',
         'diagonal_world_cut']
