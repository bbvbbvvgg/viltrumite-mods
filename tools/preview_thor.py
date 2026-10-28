#!/usr/bin/env python3
"""Thor R1 body previews from the posed-quad dump of ThorBodyGeometryTest (Pillow + numpy).

The test poses the real thor.geo.json through the renderer's pipeline (vanilla part turns
bridged onto the geo, base clips, the lift rig) and writes build/thor-preview/poses.json:
posed quads in Bedrock px with their UVs. This tool only rasterises them, so what it shows is
what ThorRenderer computes.

  third person: front / side / back of each pose (Mjölnir in hand, on the belt, or planted)
  first person: the camera arms with Mjölnir (vanilla 1.20.1 FP arm chain, FOV 70), and the
                lift seen from the crouched eye (arms of the body on the planted handle)

Run (after `./gradlew :viltrumitecore:test --tests '*ThorBodyGeometryTest'`):
  python3 tools/preview_thor.py OUT_DIR [--poses rest,crouch,lift_6] [--scale 6]
"""
import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_geo import VIEWS, raster  # noqa: E402
from preview_fp_arm import FOV, H, W, arm_matrix  # noqa: E402

TEX = "viltrumitecore/build/generated/binassets/assets/viltrumitecore/textures/entity/thor/"
DUMP = "viltrumitecore/build/thor-preview/poses.json"


def textures():
    body = Image.open(TEX + "thor_body.png").convert("RGBA")
    hammer = Image.open(TEX + "mjolnir.png").convert("RGBA")
    glow = Image.open(TEX + "mjolnir_glow.png").convert("RGBA")
    return {"body": body, "mjolnir": hammer}, {"mjolnir": glow}


def split(quads):
    out = {}
    for q in quads:
        out.setdefault(q["texture"], []).append(([tuple(c) for c in q["corners"]], tuple(q["uv"])))
    return out


def panel(groups, tex, glow, view, scale, bounds):
    minx, maxx, miny, maxy = bounds
    w = int((maxx - minx) * scale) + 2 * scale
    h = int((maxy - miny) * scale) + 2 * scale
    img = Image.new("RGBA", (w, h), (40, 44, 52, 255))
    zbuf = [[-1e9] * w for _ in range(h)]
    ox, oy = scale - minx * scale, scale + maxy * scale
    # ground line at y = 0 (the feet)
    d = ImageDraw.Draw(img)
    d.line([(0, oy), (w, oy)], fill=(90, 90, 100, 255))
    for name, quads in groups.items():
        raster(img, zbuf, quads, tex[name], 1, 1, VIEWS[view], scale, ox, oy)
    for name, quads in groups.items():
        if name in glow:
            raster(img, zbuf, quads, glow[name], 1, 1, VIEWS[view], scale, ox, oy, additive=True)
    return img


def third_person(dump, names, scale, out_dir, tex, glow):
    rows = []
    for name in names:
        groups = split(dump["third_person"][name])
        panels = []
        for view in ("front", "side", "back"):
            pts = [VIEWS[view](c) for qs in groups.values() for corners, _ in qs for c in corners]
            bounds = (min(p[0] for p in pts) - 2, max(p[0] for p in pts) + 2, min(0, min(p[1] for p in pts)) - 1, max(p[1] for p in pts) + 2)
            panels.append(panel(groups, tex, glow, view, scale, bounds))
        row = Image.new("RGBA", (sum(p.size[0] for p in panels) + 8 * len(panels), max(p.size[1] for p in panels) + 18), (20, 20, 24, 255))
        x = 0
        for p in panels:
            row.paste(p, (x, 18))
            x += p.size[0] + 8
        ImageDraw.Draw(row).text((4, 3), name + "  (front | side | back)", fill=(255, 255, 255, 255))
        rows.append(row)
        row.save(os.path.join(out_dir, "thor_" + name + ".png"))
    return rows


def perspective(groups, tex, glow, to_view, label):
    """to_view: Bedrock px point -> view space (x right, y up, -z forward, blocks)."""
    f = 1.0 / math.tan(math.radians(FOV) / 2.0)
    aspect = W / H

    def project(p):
        v = to_view(p)
        z = min(v[2], -0.02)
        return ((v[0] / -z) * f / aspect, (v[1] / -z) * f, 1.0 / -z * (1 if v[2] < -0.02 else -1))

    img = Image.new("RGBA", (W, H), (40, 44, 52, 255))
    zbuf = [[-1e9] * W for _ in range(H)]
    # screen = (ndc + 1) * W/2: ox = W/2, scale = W/2 on x; y uses H/2. raster uses one scale, so prescale y.
    sx, sy = W / 2.0, H / 2.0

    def view(p):
        x, y, d = project(p)
        return (x, y * sy / sx, d)

    for name, quads in groups.items():
        raster(img, zbuf, quads, tex[name], 1, 1, view, sx, W / 2.0, H / 2.0)
    for name, quads in groups.items():
        if name in glow:
            raster(img, zbuf, quads, glow[name], 1, 1, view, sx, W / 2.0, H / 2.0, additive=True)
    d = ImageDraw.Draw(img)
    d.line([(W / 2 - 8, H / 2), (W / 2 + 8, H / 2)], fill=(255, 255, 255, 255))
    d.line([(W / 2, H / 2 - 8), (W / 2, H / 2 + 8)], fill=(255, 255, 255, 255))
    d.text((6, 4), label, fill=(255, 255, 255, 255))
    return img


def fp_arms(dump, tex, glow):
    tiles = []
    # Bedrock px of thor_arms.geo.json -> vanilla arm part space (PlayerGeoLayer.partFrame) -> camera (vanilla FP chain).
    for side, key in ((1.0, "right"), (-1.0, "left")):
        pass
    groups_all = {}
    mats = {}
    for side, key in ((1.0, "right"), (-1.0, "left")):
        mats[key] = arm_matrix(side, (0, 0, 0, 0, 0, 0))
    def to_view_for(key, side):
        m = mats[key]
        pivot = np.array([-5.0 * side, 2.0, 0.0])

        def to_view(p):
            vanilla = np.array([p[0], 24.0 - p[1], p[2]])  # Bedrock -> vanilla model px
            local = (vanilla - pivot) / 16.0
            v = m @ np.array([local[0], local[1], local[2], 1.0])
            return v[:3]
        return to_view
    # draw each arm with its own chain into one image (depth by 1/z keeps the order right)
    f = 1.0 / math.tan(math.radians(FOV) / 2.0)
    img = Image.new("RGBA", (W, H), (40, 44, 52, 255))
    zbuf = [[-1e9] * W for _ in range(H)]
    sx = W / 2.0
    for side, key in ((1.0, "right"), (-1.0, "left")):
        to_view = to_view_for(key, side)

        def view(p, to_view=to_view):
            v = to_view(p)
            z = min(v[2], -0.02)
            return ((v[0] / -z) * f / (W / H), (v[1] / -z) * f * (H / 2.0) / sx, 1.0 / -z)

        groups = split(dump["first_person_arms"][key])
        for name, quads in groups.items():
            raster(img, zbuf, quads, tex[name], 1, 1, view, sx, W / 2.0, H / 2.0)
        for name, quads in groups.items():
            if name in glow:
                raster(img, zbuf, quads, glow[name], 1, 1, view, sx, W / 2.0, H / 2.0, additive=True)
    d = ImageDraw.Draw(img)
    d.line([(W / 2 - 8, H / 2), (W / 2 + 8, H / 2)], fill=(255, 255, 255, 255))
    d.line([(W / 2, H / 2 - 8), (W / 2, H / 2 + 8)], fill=(255, 255, 255, 255))
    d.text((6, 4), "first person: Thor arms, Mjolnir in hand (vanilla FP chain, FOV 70)", fill=(255, 255, 255, 255))
    return img


def fp_lift(dump, tex, glow, pitch):
    lift = dump["first_person_lift"]
    eye = lift["eye_px"]
    a = math.radians(pitch)

    def to_view(p):
        # model faces -Z (Bedrock); camera at the eye looking forward, pitched down by `pitch`
        x, y, z = p[0] / 16.0, (p[1] - eye) / 16.0, p[2] / 16.0
        # view: x right = Bedrock +x seen from behind is the model's left... camera behind the eyes looks along -Z, right = -x
        vx, vy, vz = -x, y, z
        # pitch down: rotate about x so forward (-z) tips toward -y
        cy, sy = math.cos(a), math.sin(a)
        return (vx, vy * cy - vz * sy, vy * sy + vz * cy)

    return perspective(split(lift["quads"]), tex, glow, to_view, "first person: lift from the crouched eye (pitch %d)" % pitch)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("out_dir")
    ap.add_argument("--poses", default="")
    ap.add_argument("--scale", type=int, default=6)
    ap.add_argument("--pitch", type=float, default=55.0)
    args = ap.parse_args(argv)
    os.makedirs(args.out_dir, exist_ok=True)
    with open(DUMP, encoding="utf-8") as f:
        dump = json.load(f)
    tex, glow = textures()
    names = [n for n in args.poses.split(",") if n] or list(dump["third_person"].keys())
    third_person(dump, names, args.scale, args.out_dir, tex, glow)
    fp_arms(dump, tex, glow).save(os.path.join(args.out_dir, "thor_fp_arms.png"))
    fp_lift(dump, tex, glow, args.pitch).save(os.path.join(args.out_dir, "thor_fp_lift.png"))
    print("written to", args.out_dir)


if __name__ == "__main__":
    main(sys.argv[1:])
