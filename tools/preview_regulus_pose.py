#!/usr/bin/env python3
"""Posed previews of the Regulus body: samples generated clips (linear keys) and draws the geo with
tools/preview_geo.py's rasteriser (same pivot/rotation convention as regulus_rig.py: the clip rotation
is added to the bone's rest rotation, the position channel translates the bone in geo px).
Review aid only, not a Minecraft acceptance run; previews are not committed.

Run: python3 tools/preview_regulus_pose.py OUT.png CLIP:SECONDS [CLIP:SECONDS ...] [--views front,side] [--scale 6]
"""
import argparse
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_geo as pg  # noqa: E402
from PIL import Image  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore")


def clips():
    out = {}
    for f in glob.glob(os.path.join(RES, "animations/regulus/*.animation.json")):
        out.update(json.load(open(f))["animations"])
    return out


def sample(ch, t):
    if isinstance(ch, list):
        return ch
    keys = sorted((float(k), v.get("post", v.get("pre")) if isinstance(v, dict) else v) for k, v in ch.items())
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            k = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return [a + (b - a) * k for a, b in zip(v0, v1)]
    return keys[-1][1]


def posed_quads(geo, clip, t):
    g = geo["minecraft:geometry"][0]
    bones = {b["name"]: b for b in g["bones"]}
    pose = {name: {ch: sample(v, t) for ch, v in b.items()} for name, b in (clip or {}).get("bones", {}).items()}
    cache = {}

    def matrix(name):
        if name in cache:
            return cache[name]
        b = bones[name]
        m = matrix(b["parent"]) if b.get("parent") in bones else pg.IDENTITY
        p = pose.get(name, {})
        rest = b.get("rotation") or [0, 0, 0]
        rot = [rest[i] + p.get("rotation", [0, 0, 0])[i] for i in range(3)]
        m = pg.mat_mul(m, pg.translate(*p.get("position", [0, 0, 0])))
        m = pg.mat_mul(m, pg.rotation(b.get("pivot", [0, 0, 0]), rot))
        cache[name] = m
        return m

    quads = []
    for name, b in bones.items():
        m = matrix(name)
        for cube in b.get("cubes", []):
            cm = pg.mat_mul(m, pg.rotation(cube.get("pivot", [0, 0, 0]), cube.get("rotation")))
            for corners, uv in pg.cube_quads(cube):
                quads.append(([pg.apply(cm, c) for c in corners], uv))
    return quads, g["description"]["texture_width"], g["description"]["texture_height"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("poses", nargs="+")
    ap.add_argument("--views", default="front,side")
    ap.add_argument("--scale", type=int, default=6)
    ap.add_argument("--geo", default=os.path.join(RES, "geo/regulus/regulus.geo.json"))
    ap.add_argument("--tex", default=os.path.join(ROOT, "build/regulus-assets/regulus.png"))
    a = ap.parse_args()
    geo = json.load(open(a.geo))
    tex = Image.open(a.tex).convert("RGBA")
    all_clips = clips()
    cells = []
    for spec in a.poses:
        name, _, sec = spec.partition(":")
        quads, tw, th = posed_quads(geo, all_clips.get(name), float(sec or 0))
        for v in a.views.split(","):
            view = pg.VIEWS[v]
            img = Image.new("RGBA", (24 * a.scale, 44 * a.scale), (40, 44, 52, 255))
            zbuf = [[-1e9] * img.size[0] for _ in range(img.size[1])]
            pg.raster(img, zbuf, quads, tex, tw, th, view, a.scale, 12 * a.scale, 38 * a.scale)
            cells.append(img)
    out = Image.new("RGBA", (sum(c.size[0] + 4 for c in cells), cells[0].size[1]), (20, 20, 24, 255))
    x = 0
    for c in cells:
        out.paste(c, (x, 0))
        x += c.size[0] + 4
    out.save(a.out)


if __name__ == "__main__":
    main()
