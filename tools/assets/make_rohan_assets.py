#!/usr/bin/env python3
"""Generates every Rohan Kishibe / Heaven's Door model, clip, texture and icon (original art, CC0, author: the project).

Outputs (core module):
  resources/assets/viltrumitecore/geo/rohan/{heavens_door,rohan_outfit}.geo.json
  resources/assets/viltrumitecore/animations/rohan/heavens_door_<clip>.animation.json
  binassets/assets/viltrumitecore/textures/entity/rohan/*.png.b64 (atlases, glow, page, page_ink, sketch, manuscript, streak)
  binassets/assets/viltrumitecore/textures/entity/hero/rohan.png.b64
  binassets/assets/viltrumitecore/textures/gui/rohan/book.png.b64
  binassets/assets/viltrumitecore/textures/gui/ability/rohan/*.png.b64
Run: python3 tools/assets/make_rohan_assets.py [--preview DIR] [--check]
--check regenerates into memory and fails if a committed file differs.
"""
import argparse
import base64
import io
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rohan_lib import clips, heavens_door, outfit, paint, skin, textures  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore")
BIN = os.path.join(ROOT, "viltrumitecore/src/main/binassets/assets/viltrumitecore")


def png_bytes(arr_or_img, quantize=0):
    img = arr_or_img if isinstance(arr_or_img, Image.Image) else Image.fromarray(np.clip(arr_or_img, 0, 255).astype(np.uint8), "RGBA")
    if quantize:
        # Palette PNGs keep the pushed text assets small; alpha is binary or absent here.
        img = img.convert("RGBA").quantize(colors=quantize, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def outputs():
    files = {}

    def geo(name, g, width, colors=128):
        g.pack(width)
        alb, glow = g.paint(paint.paint_face)
        files["geo/rohan/%s.geo.json" % name] = (json.dumps(g.to_json(), separators=(",", ":")) + "\n").encode()
        # FASTOCTREE keeps the glass hat alpha levels in the palette
        files["textures/entity/rohan/%s.png" % name] = png_bytes(alb, colors)
        if glow[..., 3].max() > 0:
            files["textures/entity/rohan/%s_glow.png" % name] = png_bytes(glow)
        return g

    geo("heavens_door", heavens_door.build(), 256)
    geo("rohan_outfit", outfit.build(), 128)
    for c in clips.clips():
        # one file per clip: every pushed text file stays small
        files["animations/rohan/heavens_door_%s.animation.json" % c.name] = (json.dumps({"format_version": "1.8.0", "animations": {c.name: c.bake()}}, separators=(",", ":"), sort_keys=True) + "\n").encode()
    files["textures/entity/hero/rohan.png"] = png_bytes(skin.skin())
    for name, fn in (("page", textures.page), ("page_ink", textures.page_ink), ("sketch", textures.sketch),
                     ("manuscript", textures.manuscript), ("streak", textures.streak)):
        files["textures/entity/rohan/%s.png" % name] = png_bytes(fn())
    files["textures/gui/rohan/book.png"] = png_bytes(textures.book_gui(), 64)
    for name, im in textures.icons().items():
        files["textures/gui/ability/rohan/%s.png" % name] = png_bytes(im)
    return files


def target(rel):
    if rel.endswith(".png"):
        return os.path.join(BIN, rel + ".b64")
    return os.path.join(RES, rel)


def encode(rel, data):
    if rel.endswith(".png"):
        return base64.encodebytes(data)
    return data


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--preview", help="also write raw PNG/JSON outputs into this folder")
    a = ap.parse_args()
    files = outputs()
    bad = 0
    for rel, data in sorted(files.items()):
        path = target(rel)
        enc = encode(rel, data)
        if a.check:
            if not os.path.isfile(path) or open(path, "rb").read() != enc:
                print("DIFF", os.path.relpath(path, ROOT))
                bad += 1
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as fh:
            fh.write(enc)
        if a.preview:
            p = os.path.join(a.preview, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "wb").write(data)
    print("%d files %s" % (len(files), "checked" if a.check else "written"))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
