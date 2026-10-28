#!/usr/bin/env python3
"""Contact sheets for the Sinclair playtest report.

The repository tooling can only push text, so each sheet is an SVG wrapping an embedded WebP
(GitHub renders it like a picture). Usage, from the repo root after a playtest run:

    python3 docs/reports/sinclair-playtest/make_sheets.py viltrumitecore/run/screenshots
"""
import base64, glob, io, os, sys
from PIL import Image, ImageDraw

OUT = os.path.dirname(os.path.abspath(__file__))
TILE = (400, 225)
COLS, ROWS = 2, 2
LIMIT = 52000  # bytes per SVG (push limit with headroom)

GROUPS = [
    ("01_body", ["01", "02", "03", "04"]),
    ("02_motion", ["05", "06", "09", "12"]),
    ("03_first_person", ["07", "08", "10", "11"]),
    ("04_savannah_a", ["14", "15", "16", "17"]),
    ("05_savannah_b", ["18", "19", "21", "22"]),
    ("06_rabbit_hex", ["24", "25", "27", "28"]),
    ("07_misdirection", ["29", "30", "31", "32"]),
    ("08_capture", ["33", "34", "35", "13"]),
]
ACTS = ["beam", "nova", "cut", "barrage", "aegis", "finale"]


def find(src, tag):
    hits = sorted(glob.glob(os.path.join(src, f"sinclair_{tag}_*.png")))
    return hits[0] if hits else None


def sheet(paths, name):
    w, h = TILE
    canvas = Image.new("RGB", (w * COLS, h * ROWS), (21, 18, 26))
    draw = ImageDraw.Draw(canvas)
    for i, path in enumerate(paths[: COLS * ROWS]):
        im = Image.open(path).convert("RGB").resize(TILE, Image.LANCZOS)
        x, y = (i % COLS) * w, (i // COLS) * h
        canvas.paste(im, (x, y))
        label = os.path.basename(path)[len("sinclair_"):-4]
        draw.rectangle((x, y + h - 14, x + 6 * len(label) + 6, y + h), fill=(21, 18, 26))
        draw.text((x + 3, y + h - 13), label, fill=(242, 193, 78))
    for quality in (70, 60, 50, 42, 35):
        buf = io.BytesIO()
        canvas.save(buf, "WEBP", quality=quality, method=6)
        data = base64.b64encode(buf.getvalue()).decode()
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{canvas.width}" height="{canvas.height}" '
               f'viewBox="0 0 {canvas.width} {canvas.height}"><image width="{canvas.width}" height="{canvas.height}" '
               f'href="data:image/webp;base64,{data}"/></svg>\n')
        if len(svg) <= LIMIT:
            break
    with open(os.path.join(OUT, name + ".svg"), "w") as fh:
        fh.write(svg)
    print(name, len(svg), "q", quality)


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else "viltrumitecore/run/screenshots"
    for name, tags in GROUPS:
        paths = [p for p in (find(src, t) for t in tags) if p]
        if paths:
            sheet(paths, name)
    for i, act in enumerate(ACTS):
        paths = sorted(glob.glob(os.path.join(src, f"sinclair_*_act_{act}_*.png")))
        own = [p for p in paths if "own_view" in p]
        beats = [p for p in paths if "own_view" not in p]
        if beats:
            sheet(beats[:4], f"{9 + i:02d}_act_{act}")
        if own:
            pass  # the own-view shots go into the last sheet
    owns = sorted(glob.glob(os.path.join(src, "sinclair_*_own_view.png")))
    if owns:
        sheet(owns[:4], "15_acts_own_view_a")
        sheet(owns[4:8], "16_acts_own_view_b")


if __name__ == "__main__":
    main()
