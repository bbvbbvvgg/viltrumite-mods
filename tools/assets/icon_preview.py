"""Previews for ability icons: real-scale HUD mock, contact sheets, before/after sheets.

The HUD mock repeats the layout of ViltrumiteInGameHudMixin (six vertical ability
slots, three flight slots, page number, key labels) and the fills of the panel
styles (DefaultPanelStyle hotbar texture, IronManPanelStyle glass, ReinhardPanelStyle
plate). It draws in screen pixels for a GUI scale: a 16x16-unit icon blit samples the
64x64 texture nearest-neighbour, as Minecraft does for GUI textures, so GUI scale 2
shows the 32x32 art 1:1 and GUI scale 3 shows the uneven 1.5x pattern a player sees.
Used by make_hero_icons.py; the images go under build/, never into resources.
"""
import base64
import io
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
CORE = os.path.join(REPO, "viltrumitecore", "src", "main")
HOTBAR = os.path.join(CORE, "resources", "assets", "viltrumitecore", "textures", "gui", "ability_hotbar.png")
FONT_PATHS = ("/usr/share/fonts/liberation-sans/LiberationSans-Regular.ttf",
              "/usr/share/fonts/liberation/LiberationSans-Regular.ttf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")


def font(size):
    for p in FONT_PATHS:
        if os.path.isfile(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def load_texture(rel):
    """An asset path under assets/viltrumitecore/ from resources/ or binassets/ (*.b64)."""
    res = os.path.join(CORE, "resources", "assets", "viltrumitecore", rel)
    if os.path.isfile(res):
        return Image.open(res).convert("RGBA")
    b64 = os.path.join(CORE, "binassets", "assets", "viltrumitecore", rel + ".b64")
    with open(b64) as f:
        return Image.open(io.BytesIO(base64.b64decode(f.read()))).convert("RGBA")


def argb(v):
    v &= 0xFFFFFFFF
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, (v >> 24) & 255)


def blit_scaled(dst, tex, x, y, size):
    """Blit a whole texture into a size x size screen square with nearest sampling."""
    tw, th = tex.size
    src = tex.load()
    tile = Image.new("RGBA", (size, size))
    t = tile.load()
    for j in range(size):
        v = min(th - 1, int((j + 0.5) * th / size))
        for i in range(size):
            t[i, j] = src[min(tw - 1, int((i + 0.5) * tw / size)), v]
    dst.alpha_composite(tile, (x, y))


class Screen:
    """GUI-unit drawing on a screen-pixel canvas."""

    def __init__(self, gw, gh, scale, background):
        self.s = scale
        self.gw, self.gh = gw, gh
        self.im = background(gw * scale, gh * scale)

    def fill(self, x0, y0, x1, y1, color):
        s = self.s
        layer = Image.new("RGBA", self.im.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).rectangle([x0 * s, y0 * s, x1 * s - 1, y1 * s - 1], fill=argb(color))
        self.im.alpha_composite(layer)

    def blit(self, tex, x, y, u, v, w, h, tw, th):
        s = self.s
        crop = tex.crop((u, v, u + w, v + h)).resize((w * s, h * s), Image.NEAREST)
        self.im.alpha_composite(crop, (x * s, y * s))

    def icon(self, tex, x, y):
        blit_scaled(self.im, tex, x * self.s, y * self.s, 16 * self.s)

    def text(self, x, y, txt, color=(255, 255, 255, 255)):
        d = ImageDraw.Draw(self.im)
        f = font(7 * self.s)
        d.text((x * self.s + self.s, y * self.s + self.s), txt, fill=(60, 60, 60, 255), font=f)
        d.text((x * self.s, y * self.s), txt, fill=color, font=f)


# ---------------------------------------------------------------- panel styles (mirrors of the Java classes)
class DefaultStyle:
    def __init__(self):
        self.tex = Image.open(HOTBAR).convert("RGBA")

    def frames(self, sc, fb, ab):
        sc.blit(self.tex, fb[0], fb[1], 0, 22, fb[2], fb[3], 128, 186)
        sc.blit(self.tex, ab[0], ab[1], 0, 44, ab[2], ab[3], 128, 186)

    def slot(self, sc, x, y, empty):
        pass

    def cooldown(self, sc, x, y):
        sc.fill(x, y, x + 16, y + 16, -1875692749)

    accent = (255, 225, 0, 255)


class IronManStyle:
    GLASS, EDGE, BRACKET, CELL = 0x7010243A, 0x9060D8FF, 0xFFA8F0FF, 0x5018405A
    OFFLINE, OFFLINE_MARK, COOLDOWN = 0x400A1A26, 0x5060A0C0, 0x902070C0

    def frames(self, sc, fb, ab):
        for b in (fb, ab):
            x0, y0, x1, y1 = b[0], b[1], b[0] + b[2], b[1] + b[3]
            sc.fill(x0, y0, x1, y1, self.GLASS)
            for r in ((x0, y0, x1, y0 + 1), (x0, y1 - 1, x1, y1), (x0, y0, x0 + 1, y1), (x1 - 1, y0, x1, y1)):
                sc.fill(*r, self.EDGE)
            l = 4
            for r in ((x0 - 1, y0 - 1, x0 + l, y0), (x0 - 1, y0 - 1, x0, y0 + l), (x1 - l, y0 - 1, x1 + 1, y0),
                      (x1, y0 - 1, x1 + 1, y0 + l), (x0 - 1, y1, x0 + l, y1 + 1), (x0 - 1, y1 - l, x0, y1 + 1),
                      (x1 - l, y1, x1 + 1, y1 + 1), (x1, y1 - l, x1 + 1, y1 + 1)):
                sc.fill(*r, self.BRACKET)

    def slot(self, sc, x, y, empty):
        if empty:
            sc.fill(x, y, x + 16, y + 16, self.OFFLINE)
            sc.fill(x + 5, y + 7, x + 11, y + 8, self.OFFLINE_MARK)
        else:
            sc.fill(x, y, x + 16, y + 16, self.CELL)

    def cooldown(self, sc, x, y):
        sc.fill(x, y, x + 16, y + 16, self.COOLDOWN)
        sc.fill(x, y + 15, x + 16, y + 16, self.EDGE)

    accent = (127, 230, 255, 255)


class ReinhardStyle:
    WHITE, GOLD, GOLD_DARK, BLUE, BLUE_EMPTY, COOLDOWN = 0xD8F4F1EA, 0xFFE8C15A, 0xFF9C7A2E, 0xFF2F5DA8, 0x902F5DA8, 0xA0101830

    def frames(self, sc, fb, ab):
        for b in (fb, ab):
            x0, y0, x1, y1 = b[0], b[1], b[0] + b[2], b[1] + b[3]
            sc.fill(x0, y0, x1, y1, self.WHITE)
            for r in ((x0, y0, x1, y0 + 1), (x0, y1 - 1, x1, y1), (x0, y0, x0 + 1, y1), (x1 - 1, y0, x1, y1)):
                sc.fill(*r, self.GOLD)
            sc.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, self.GOLD_DARK)

    def slot(self, sc, x, y, empty):
        if empty:
            sc.fill(x, y, x + 16, y + 16, self.BLUE_EMPTY)
            sc.fill(x + 7, y + 7, x + 9, y + 9, self.GOLD_DARK)
        else:
            sc.fill(x, y, x + 16, y + 16, self.BLUE)

    def cooldown(self, sc, x, y):
        sc.fill(x, y, x + 16, y + 16, self.COOLDOWN)
        sc.fill(x, y + 15, x + 16, y + 16, self.GOLD)

    accent = (232, 193, 90, 255)


STYLES = {"default": DefaultStyle, "ironman": IronManStyle, "reinhard": ReinhardStyle}


def day_background(w, h):
    """Sky, distant hills and a grass/dirt band: a bright daytime world behind the HUD."""
    im = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=(int(120 + 60 * t), int(167 + 40 * t), 255, 255))
    d.polygon([(0, int(h * .62)), (w * .3, int(h * .5)), (w * .6, int(h * .6)), (w, int(h * .48)), (w, h), (0, h)],
              fill=(96, 140, 72, 255))
    d.rectangle([0, int(h * .72), w, h], fill=(110, 82, 58, 255))
    for i in range(0, w, max(8, w // 40)):
        d.rectangle([i, int(h * .72), i + w // 80, int(h * .74)], fill=(88, 150, 60, 255))
    return im


def night_background(w, h):
    """Dark cave stone: worst case for dark icon edges."""
    im = Image.new("RGBA", (w, h), (34, 34, 38, 255))
    d = ImageDraw.Draw(im)
    step = max(8, w // 48)
    for y in range(0, h, step):
        for x in range(0, w, step):
            v = 30 + ((x * 7 + y * 13) // step) % 5 * 6
            d.rectangle([x, y, x + step - 1, y + step - 1], fill=(v, v, v + 4, 255))
    return im


def hud(icons, flight, style="default", scale=2, background=day_background, grey=(), keys=("R", "Y", "Z", "X", "C", "V"),
        page=1):
    """Bottom-right HUD crop. icons: up to six textures (None = empty slot); flight: three (None = not owned)."""
    gw, gh = 132, 170
    sc = Screen(gw, gh, scale, background)
    st = STYLES[style]()
    right_w, bar_h, vert_w, vert_h, pad = 62, 22, 22, 120, 5
    rx = gw - right_w - pad
    by = gh - bar_h - pad
    vx = rx + right_w - vert_w
    vy = by - vert_h
    st.frames(sc, (rx, by, right_w, bar_h), (vx, vy, vert_w, vert_h))
    for i in range(6):
        x, y = vx + 3, vy + 3 + i * 20
        tex = icons[i] if i < len(icons) else None
        if keys[i]:
            sc.fill(x - 6 - 7, y + 2, x - 2, y + 14, 0x70000000)
            sc.text(x - 6 - 5, y + 3, keys[i])
        st.slot(sc, x, y, tex is None)
        if tex is not None:
            sc.icon(tex, x, y)
            if i in grey:
                st.cooldown(sc, x, y)
    sc.fill(vx + 5, vy - 14, vx + 18, vy - 2, 0x60000000)
    sc.text(vx + 9, vy - 13, str(page), st.accent)
    for i in range(3):
        x, y = rx + 3 + i * 20, by + 3
        tex = flight[i] if i < len(flight) else None
        st.slot(sc, x, y, tex is None)
        if tex is not None:
            sc.icon(tex, x, y)
    return sc.im


# ---------------------------------------------------------------- sheets
BG = (34, 35, 42, 255)
INK = (225, 226, 232, 255)
DIM = (150, 152, 165, 255)


def checker(w, h, a=(52, 53, 60, 255), b=(64, 65, 74, 255), cell=8):
    im = Image.new("RGBA", (w, h), a)
    d = ImageDraw.Draw(im)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=b)
    return im


def big(tex, size=128):
    """Art view: whole texture scaled to size with nearest (a 64x64 icon at 2x, a 16x16 at 8x)."""
    out = checker(size, size)
    out.alpha_composite(tex.resize((size, size), Image.NEAREST))
    return out


def real(tex, scale):
    """What the player sees: 16 GUI units at a GUI scale."""
    s = 16 * scale
    out = checker(s, s, cell=4)
    blit_scaled(out, tex, 0, 0, s)
    return out


def contact_sheet(entries, title, cols=4):
    """entries: [(label, texture)]. Each cell: art view, then GUI 2 and GUI 3 real size."""
    cw, ch = 128 + 12 + 48 + 8, 128 + 30
    rows = (len(entries) + cols - 1) // cols
    W, H = 20 + cols * (cw + 18), 46 + rows * (ch + 10)
    im = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(im)
    d.text((20, 12), title, fill=INK, font=font(18))
    for k, (label, tex) in enumerate(entries):
        r, c = divmod(k, cols)
        x, y = 20 + c * (cw + 18), 46 + r * (ch + 10)
        im.alpha_composite(big(tex), (x, y))
        im.alpha_composite(real(tex, 2), (x + 140, y))
        im.alpha_composite(real(tex, 3), (x + 140, y + 40))
        d.text((x + 140, y + 92), "GUI 2 / 3", fill=DIM, font=font(10))
        d.text((x, y + 132), label, fill=INK, font=font(13))
    return im


def before_after(pairs, title, cols=3):
    """pairs: [(label, before, after)]: art views side by side plus both at GUI scale 2."""
    cw, ch = 128 * 2 + 16 + 40, 128 + 30
    rows = (len(pairs) + cols - 1) // cols
    W, H = 20 + cols * (cw + 24), 70 + rows * (ch + 12)
    im = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(im)
    d.text((20, 12), title, fill=INK, font=font(18))
    d.text((20, 38), "before  ->  after   (art view; right: GUI scale 2, real size)", fill=DIM, font=font(12))
    for k, (label, before, after) in enumerate(pairs):
        r, c = divmod(k, cols)
        x, y = 20 + c * (cw + 24), 70 + r * (ch + 12)
        if before is not None:
            im.alpha_composite(big(before), (x, y))
            im.alpha_composite(real(before, 2), (x + 128 * 2 + 24, y + 8))
        else:
            d.rectangle([x, y, x + 127, y + 127], outline=DIM)
            d.text((x + 30, y + 56), "(new file)", fill=DIM, font=font(12))
        d.text((x + 131, y + 54), ">", fill=INK, font=font(14))
        im.alpha_composite(big(after), (x + 140, y))
        im.alpha_composite(real(after, 2), (x + 128 * 2 + 24, y + 48))
        d.text((x, y + 132), label, fill=INK, font=font(13))
    return im


def stack(images, gap=16, bg=BG):
    W = max(i.width for i in images)
    H = sum(i.height for i in images) + gap * (len(images) - 1)
    out = Image.new("RGBA", (W, H), bg)
    y = 0
    for i in images:
        out.alpha_composite(i, (0, y))
        y += i.height + gap
    return out


def row(images, gap=16, bg=BG):
    W = sum(i.width for i in images) + gap * (len(images) - 1)
    H = max(i.height for i in images)
    out = Image.new("RGBA", (W, H), bg)
    x = 0
    for i in images:
        out.alpha_composite(i, (x, 0))
        x += i.width + gap
    return out
