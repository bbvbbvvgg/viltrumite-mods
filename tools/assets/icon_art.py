"""House style of the ability icons, shared by every hero.

The style is the one of the Earth status icon (textures/gui/reinhard/blessing_bearer.png,
copied from the user archive and only read here), first used by the Reinhard icons
(reinhard_icon_art.py), which are the reference set:

- one icon = a 32x32 art grid, saved 2x nearest-neighbour as the 64x64 ability texture.
  Icons are blitted at 16x16 GUI units, so one art pixel is one screen pixel at GUI scale 2;
- the two-pixel bevel frame of the Earth icon, reused pixel for pixel and re-tinted per
  hero (and per mode, where a hero has modes): see FRAMES;
- the charcoal radial backdrop, a spotlight behind the subject;
- navy #172038 outlines, top-left light (lit rim up-left, dark rim down-right);
- plus-shaped sparkles as accents; colours from the Earth icon and Endesga 64.

This module holds only hero-independent pieces: palette, frame, canvas, masks, strokes,
sprite stamping and the shared opponent. Hero art lives in <hero>_icon_art.py.
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
# Earth frame shades (outer bevel to inner rim) and their replacements: Reinhard modes, then one
# tint per hero (Iron Man has one per suit state). Index 0 stays white on every frame.
FRAME_SRC = [hx(c) for c in ("ffffff", "a1fbe8", "4aedd9", "1aaaa7", "11727a")]
FRAMES = {
    "restrained": [hx(c) for c in ("ffffff", "dbe2e5", "cad0d7", "92a1b9", "657392")],
    "knight": [hx(c) for c in ("ffffff", "a4dddb", "73bed3", "4f8fba", "3c5e8b")],
    "holy": [hx(c) for c in ("ffffff", "fae875", "f1db4d", "ceaf67", "a17e4b")],
    # Regulus: warm ivory to bronze, the white king in gold; madness-only slots are blood red.
    "regulus": [hx(c) for c in ("ffffff", "f7ecd2", "e8cf98", "c9a35c", "8f6c34")],
    "regulus_madness": [hx(c) for c in ("ffffff", "f68187", "ea323c", "c42430", "891e2b")],
    # Homelander: Vought royal blue, the suit colour.
    "homelander": [hx(c) for c in ("ffffff", "a9c4ff", "6f94ec", "3e5fc4", "26378c")],
    # Iron Man: hot-rod red (nanosuit), mark gold (mark signatures), graphite (Hulkbuster).
    "ironman": [hx(c) for c in ("ffffff", "f68187", "ea323c", "c42430", "891e2b")],
    "ironman_mark": [hx(c) for c in ("ffffff", "ffeb57", "ffc825", "edab50", "a5641c")],
    "ironman_hulk": [hx(c) for c in ("ffffff", "b4b4b4", "858585", "5d5d5d", "3d3d3d")],
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
        ramp = FRAMES[self.mode]
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


# ---------------------------------------------------------------- registry
def registry():
    """(icons, art): a per-hero name -> function map and the decorator that fills it."""
    icons = {}

    def art(fn):
        icons[fn.__name__] = fn
        return fn

    return icons, art


def to64(im32):
    """32x32 art -> 64x64 ability texture (2x nearest)."""
    return im32.resize((64, 64), Image.NEAREST)


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


def glow_line(ic, mask, core=WHITE, halo=CYAN[3], outer=CYAN[1]):
    ic.mix(grow(grow(mask, True), True) - grow(mask, True), outer, 0.45)
    ic.mix(grow(mask, True) - mask, halo, 0.85)
    ic.fill(mask, core)
