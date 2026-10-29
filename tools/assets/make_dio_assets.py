#!/usr/bin/env python3
"""Generates every DIO model, clip, texture and icon (original art, CC0, author: the project).

Outputs (core module):
  resources/assets/viltrumitecore/geo/dio/{the_world,dio_knife,road_roller,dio_outfit}.geo.json
  resources/assets/viltrumitecore/animations/dio/the_world.animation.json
  binassets/assets/viltrumitecore/textures/entity/dio/*.png.b64
  binassets/assets/viltrumitecore/textures/entity/hero/dio.png.b64
  binassets/assets/viltrumitecore/textures/gui/ability/dio/*.png.b64
Run: python3 tools/assets/make_dio_assets.py [--preview DIR] [--check]
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
from dio_lib import paint, props, skin, the_world, the_world_clips  # noqa: E402

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
        files["geo/dio/%s.geo.json" % name] = (json.dumps(g.to_json(), separators=(",", ":")) + "\n").encode()
        files["textures/entity/dio/%s.png" % name] = png_bytes(alb, colors)
        if glow[..., 3].max() > 0:
            files["textures/entity/dio/%s_glow.png" % name] = png_bytes(glow)
        return g

    geo("the_world", the_world.build(), 512)
    geo("dio_knife", props.knife(), 128)
    geo("road_roller", props.road_roller(), 1024, 40)
    geo("dio_outfit", props.outfit(), 64)
    for c in the_world_clips.clips():
        # one file per clip: every pushed text file stays small
        files["animations/dio/the_world_%s.animation.json" % c.name] = (json.dumps({"format_version": "1.8.0", "animations": {c.name: c.bake()}}, separators=(",", ":"), sort_keys=True) + "\n").encode()
    files["textures/entity/hero/dio.png"] = png_bytes(skin.skin())
    for name, im in skin.icons().items():
        files["textures/gui/ability/dio/%s.png" % name] = png_bytes(im)
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
