#!/usr/bin/env python3
"""Contact sheet of a Sinclair clip: bakes the clip (linear sampling, rotations only) into
temporary geo files at the given ticks and renders them with tools/preview_geo.py.
Previews are for review only and are not committed.
Run: python3 tools/assets/sinclair_preview.py OUT.png CLIP TICK [TICK ...]
     [--model sinclair|savannah|rabbit|sinclair_arms|chest|curtain] [--views front,side] [--scale 6]
     [--show held_hat,card]  (bones hidden by default in game: held_hat, card)
"""
import argparse
import glob
import json
import os
import subprocess
import sys
import tempfile

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore")
ATLAS = {"sinclair": "sinclair", "sinclair_arms": "sinclair", "savannah": "savannah", "rabbit": "rabbit"}


def clips():
    out = {}
    for path in glob.glob(os.path.join(RES, "animations/sinclair/*.animation.json")):
        out.update(json.load(open(path))["animations"])
    return out


def sample(channel, sec):
    keys = sorted((float(k), v["post"] if isinstance(v, dict) else v) for k, v in channel.items())
    if sec <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= sec <= t1:
            u = (sec - t0) / (t1 - t0) if t1 > t0 else 0
            return [a + (b - a) * u for a, b in zip(v0, v1)]
    return keys[-1][1]


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("out")
    ap.add_argument("clip")
    ap.add_argument("ticks", nargs="+", type=float)
    ap.add_argument("--model", default="sinclair")
    ap.add_argument("--views", default="front,side")
    ap.add_argument("--scale", default="6")
    ap.add_argument("--show", default="")
    args = ap.parse_args()
    clip = clips()[args.clip]
    geo_path = os.path.join(RES, "geo/sinclair", args.model + ".geo.json")
    tex = os.path.join(ROOT, "build/sinclair-assets", ATLAS.get(args.model, "props") + ".png")
    if not os.path.isfile(tex):
        subprocess.run([sys.executable, os.path.join(ROOT, "tools/assets/make_sinclair_model.py")], check=True)
    hidden = {"held_hat", "card"} - set(filter(None, args.show.split(",")))
    frames = []
    with tempfile.TemporaryDirectory() as tmp:
        for i, tick in enumerate(args.ticks):
            geo = json.load(open(geo_path))
            for bone in geo["minecraft:geometry"][0]["bones"]:
                if bone["name"] in hidden:
                    bone.pop("cubes", None)
                track = clip["bones"].get(bone["name"])
                if track and "rotation" in track:
                    r = sample(track["rotation"], tick / 20.0)
                    base = bone.get("rotation", [0, 0, 0])
                    bone["rotation"] = [a + b for a, b in zip(base, r)]
            gp = os.path.join(tmp, "f%d.geo.json" % i)
            json.dump(geo, open(gp, "w"))
            png = os.path.join(tmp, "f%d.png" % i)
            subprocess.run([sys.executable, os.path.join(ROOT, "tools/preview_geo.py"), png, gp + ":" + tex,
                            "--scale", args.scale, "--views", args.views], check=True)
            frames.append(Image.open(png).convert("RGBA"))
    w = sum(f.width for f in frames) + 4 * len(frames)
    sheet = Image.new("RGBA", (w, max(f.height for f in frames)), (20, 20, 26, 255))
    x = 0
    for f in frames:
        sheet.paste(f, (x, 0))
        x += f.width + 4
    sheet.save(args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
