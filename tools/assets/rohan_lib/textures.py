"""Heaven's Door flat textures: life-book page, its glowing ink, the air sketch, the manuscript, speed streaks, the
reading/writing GUI book spread and the six ability icons (original procedural art)."""
import math

import numpy as np
from PIL import Image, ImageDraw

from dio_lib.skin import _icon_base

PAPER = (246, 238, 220)
PAPER_D = (222, 208, 182)
INK = (26, 24, 34)
GREEN = (60, 170, 100)
GOLD = (236, 186, 70)


def _rng(seed):
    return np.random.default_rng(seed)


def _scribble(d, x0, y0, x1, rng, fill, width=1, amp=1.2):
    """A handwritten line: a jittery polyline of letter-like bumps."""
    pts = []
    x = x0
    while x < x1:
        pts.append((x, y0 + rng.uniform(-amp, amp)))
        x += rng.uniform(1.5, 3.0)
        if rng.random() < 0.12:
            if len(pts) > 1:
                d.line(pts, fill=fill, width=width)
            pts = []
            x += rng.uniform(2, 4)
    if len(pts) > 1:
        d.line(pts, fill=fill, width=width)


def page():
    w, h = 48, 64
    rng = _rng(3)
    a = np.zeros((h, w, 3), np.float32)
    a[:] = PAPER
    a *= (1 + rng.normal(0, 0.02, (h, w)))[..., None]
    yy, xx = np.mgrid[0:h, 0:w]
    # darker toward the spine (x = 0) and the edges
    a *= (0.86 + 0.14 * np.clip(xx / 10.0, 0, 1))[..., None]
    a[[0, -1], :] = PAPER_D
    a[:, -1] = PAPER_D
    im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    d = ImageDraw.Draw(im)
    for y in range(7, h - 4, 5):
        _scribble(d, 6, y, w - 5 - rng.integers(0, 10), rng, (110, 100, 100, 255))
    d.rectangle([5, 3, 22, 4], fill=(70, 60, 70, 255))
    return im


def page_ink():
    """Emissive pass of a page being written: black = no light, the fresh lines glow."""
    w, h = 48, 64
    rng = _rng(9)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    d = ImageDraw.Draw(im)
    for y in range(7, h - 4, 5):
        _scribble(d, 6, y, w - 5 - rng.integers(0, 10), rng, (255, 240, 200, 255))
    d.rectangle([5, 3, 22, 4], fill=(255, 220, 160, 255))
    return im


def sketch():
    """Rohan's air drawing: a quick ink figure that opens whoever looks at it (transparent background)."""
    w, h = 96, 128
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rng = _rng(21)
    col = (20, 20, 30, 255)
    # head, eye, swept hair, a door opening in the face
    d.ellipse([30, 14, 66, 52], outline=col, width=2)
    for k in range(7):
        x = 30 + k * 6
        d.line([(x, 18 - k % 2 * 3), (x + 14, 6 + k)], fill=col, width=2)
    d.ellipse([40, 30, 50, 36], outline=col, width=2)
    d.point((45, 33), fill=col)
    d.line([(56, 26), (56, 46), (64, 44), (64, 28), (56, 26)], fill=col, width=2)
    # body with quick hatching
    d.line([(48, 52), (44, 90)], fill=col, width=3)
    d.line([(46, 60), (22, 78)], fill=col, width=2)
    d.line([(46, 60), (74, 70), (84, 58)], fill=col, width=2)
    d.line([(44, 90), (30, 122)], fill=col, width=2)
    d.line([(44, 90), (60, 120)], fill=col, width=2)
    for _ in range(40):
        x, y = rng.uniform(52, 78), rng.uniform(70, 104)
        d.line([(x, y), (x + 5, y - 5)], fill=(20, 20, 30, 200), width=1)
    # ink splatter (no-guide sketching)
    for _ in range(26):
        x, y, r = rng.uniform(6, 90), rng.uniform(6, 122), rng.uniform(0.6, 2.2)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(20, 20, 30, 230))
    # the frame of the page he holds up
    d.rectangle([2, 2, w - 3, h - 3], outline=(20, 20, 30, 120), width=1)
    return im


def manuscript():
    w, h = 48, 64
    rng = _rng(33)
    im = Image.new("RGBA", (w, h), PAPER + (255,))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w - 1, h - 1], outline=PAPER_D + (255,))
    panels = [(3, 3, 44, 22), (3, 25, 22, 44), (25, 25, 44, 44), (3, 47, 44, 60)]
    for i, (x0, y0, x1, y1) in enumerate(panels):
        d.rectangle([x0, y0, x1, y1], outline=INK + (255,), width=1)
        for _ in range(10 + i * 3):
            x, y = rng.uniform(x0 + 2, x1 - 2), rng.uniform(y0 + 2, y1 - 2)
            d.line([(x, y), (x + rng.uniform(-4, 4), y + rng.uniform(-4, 4))], fill=INK + (255,))
    # speed lines in the big panel and a speech balloon
    for k in range(9):
        y = 5 + k * 2
        d.line([(5, y), (5 + rng.uniform(10, 30), y)], fill=(60, 60, 70, 255))
    d.ellipse([28, 6, 42, 14], fill=(255, 255, 255, 255), outline=INK + (255,))
    return im


def streak():
    w, h = 64, 8
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    a = np.clip(1.0 - np.abs(yy + 0.5 - h / 2) / (h / 2), 0, 1) * np.clip(xx / w, 0, 1) ** 1.5
    im = np.zeros((h, w, 4), np.float32)
    im[..., :3] = 255
    im[..., 3] = a * 255
    return Image.fromarray(im.astype(np.uint8), "RGBA")


def book_gui():
    """256x160 open-book spread on a 256x256 sheet: leather cover, two cream pages, spine shadow, ruling."""
    W, H = 256, 160
    rng = _rng(77)
    sheet = np.zeros((256, 256, 4), np.float32)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cover = np.array([96, 42, 40], np.float32)
    img = np.zeros((H, W, 3), np.float32)
    img[:] = cover
    img *= (1 + rng.normal(0, 0.05, (H, W)))[..., None]
    alpha = np.full((H, W), 255.0)
    # rounded corners
    for cx, cy in ((4, 4), (W - 5, 4), (4, H - 5), (W - 5, H - 5)):
        m = ((xx - cx) ** 2 + (yy - cy) ** 2 > 16) & (np.abs(xx - cx) <= 4) & (np.abs(yy - cy) <= 4)
        m &= ((xx < 4) | (xx > W - 5)) & ((yy < 4) | (yy > H - 5))
        alpha[m] = 0
    # gold tooling line
    inset = 3
    edge = ((xx == inset) | (xx == W - 1 - inset) | (yy == inset) | (yy == H - 1 - inset)) & (xx >= inset) & (xx <= W - 1 - inset) & (yy >= inset) & (yy <= H - 1 - inset)
    img[edge] = GOLD
    # pages
    px0, py0, px1, py1 = 8, 7, W - 9, H - 8
    pm = (xx >= px0) & (xx <= px1) & (yy >= py0) & (yy <= py1)
    paper = np.array(PAPER, np.float32) * (1 + rng.normal(0, 0.015, (H, W)))[..., None]
    img[pm] = paper[pm]
    # page stack edges at the bottom
    for k in range(3):
        img[py1 - k, px0:px1 + 1] = np.array(PAPER_D, np.float32) * (0.95 - 0.05 * k)
    # spine shadow
    mid = W / 2
    sh = np.exp(-((xx + 0.5 - mid) ** 2) / 60.0)
    img[pm] *= (1 - 0.32 * sh[pm])[..., None]
    img[(np.abs(xx + 0.5 - mid) < 1) & pm] = np.array([150, 130, 110], np.float32)
    # faint ruling
    for y in range(py0 + 22, py1 - 6, 10):
        rule = pm[y] & (np.abs(xx[y] + 0.5 - mid) > 8)
        img[y, rule] = img[y, rule] * 0.93 + np.array([170, 190, 210], np.float32) * 0.07
    sheet[:H, :, :3] = np.clip(img, 0, 255)
    sheet[:H, :, 3] = alpha
    return Image.fromarray(sheet.astype(np.uint8), "RGBA")


def icons():
    out = {}
    # Heaven's Door: the boy's top hat over an open book
    im, d = _icon_base(GREEN)
    d.polygon([(6, 22), (16, 19), (26, 22), (26, 27), (16, 24), (6, 27)], fill=PAPER)
    d.line([(16, 19), (16, 24)], fill=(150, 130, 110))
    d.rectangle([11, 6, 21, 14], fill=(200, 236, 255, 200))
    d.rectangle([11, 13, 21, 15], fill=(240, 240, 240))
    for x in range(11, 22, 2):
        d.point((x, 14), fill=(30, 30, 40))
    d.rectangle([8, 16, 24, 17], fill=(200, 236, 255, 220))
    out["heavens_door"] = im
    # Air sketch: a pen stroke drawing a face in the air
    im, d = _icon_base(GREEN)
    d.ellipse([9, 7, 21, 19], outline=(240, 240, 240), width=1)
    d.point((13, 12), fill=(240, 240, 240))
    d.point((17, 12), fill=(240, 240, 240))
    d.arc([12, 12, 18, 17], 20, 160, fill=(240, 240, 240))
    d.line([(20, 26), (27, 19)], fill=(30, 30, 36), width=3)
    d.line([(19, 27), (21, 25)], fill=(200, 206, 220), width=2)
    out["air_sketch"] = im
    # Pen nibs: three G-pen nibs flying
    im, d = _icon_base(GREEN)
    for i, (x, y) in enumerate(((7, 9), (13, 15), (19, 21))):
        d.polygon([(x, y), (x + 6, y - 2), (x + 8, y), (x + 6, y + 2)], fill=(206, 212, 224))
        d.line([(x + 2, y), (x + 7, y)], fill=(40, 40, 50))
        d.line([(x - 5, y + 1), (x - 1, y)], fill=(255, 255, 255, 140))
    out["pen_nibs"] = im
    # Faster than a punch: a fist stopped by a raised page
    im, d = _icon_base(GOLD)
    d.rectangle([5, 12, 13, 20], fill=(220, 160, 120))
    d.line([(1, 14), (5, 14)], fill=(255, 255, 255, 150))
    d.line([(1, 18), (5, 18)], fill=(255, 255, 255, 150))
    d.polygon([(16, 6), (25, 8), (24, 26), (15, 24)], fill=PAPER)
    d.line([(18, 12), (23, 13)], fill=INK)
    d.line([(18, 16), (22, 17)], fill=INK)
    d.line([(14, 9), (12, 5)], fill=(255, 230, 120))
    d.line([(14, 23), (12, 27)], fill=(255, 230, 120))
    out["faster_than_a_punch"] = im
    # Self edit: an open page on a cheek with an arrow flying back
    im, d = _icon_base(GREEN)
    d.ellipse([6, 6, 20, 24], fill=(240, 216, 196))
    d.polygon([(13, 12), (19, 10), (19, 19), (13, 21)], fill=PAPER)
    d.line([(14, 14), (18, 13)], fill=INK)
    d.line([(14, 17), (18, 16)], fill=INK)
    d.line([(21, 16), (28, 16)], fill=(255, 255, 255), width=2)
    d.polygon([(28, 12), (28, 20), (31, 16)], fill=(255, 255, 255))
    out["self_edit"] = im
    # Manuscript: a manga page with panels and a burst
    im, d = _icon_base(GOLD)
    d.rectangle([9, 5, 23, 27], fill=PAPER, outline=INK)
    d.line([(9, 13), (23, 13)], fill=INK)
    d.line([(16, 13), (16, 20)], fill=INK)
    d.line([(9, 20), (23, 20)], fill=INK)
    for k in range(8):
        a = k * math.pi / 4
        d.line([(16 + 9 * math.cos(a), 16 + 9 * math.sin(a)), (16 + 13 * math.cos(a), 16 + 13 * math.sin(a))], fill=GOLD)
    out["manuscript"] = im
    return {k: v.resize((64, 64), Image.NEAREST) for k, v in out.items()}
