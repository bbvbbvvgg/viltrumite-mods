#!/usr/bin/env python3
"""Hulkbuster Mark 48 previews: rigged geo + one clip sampled like the Java core.

Contact sheet: rows = sample times, columns = front / side / back (and the glow pass).
Clip keys are read with the same rules as AnimationParser/KeyframeStack for the
subset make_hulkbuster_clips.py writes (linear, easeIn/Out/InOut Sine/Cubic/Quad, step),
rotation deltas added to the rest rotation, Bedrock (-x, -y, z) sign handling through
preview_geo.rotation. Previews are review material only; they are not committed and
they are not proof of the in-game look.

Run: python3 tools/preview_hulkbuster.py OUT.png [--clip NAME] [--times 0,0.5,1] [--scale 3]
     [--views front,side,back] [--glow] [--flames]
"""
import argparse
import base64
import io
import json
import math
import os
import sys

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(REPO, "tools"))
import preview_geo as pg  # noqa: E402

RES = os.path.join(REPO, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore")
BIN = os.path.join(REPO, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore")
GEO = os.path.join(RES, "geo", "ironman", "hulkbuster", "mark48.geo.json")
ANIM = os.path.join(RES, "animations", "ironman", "hulkbuster", "mark48.animation.json")


def png(rel):
    with open(os.path.join(BIN, rel + ".b64"), encoding="ascii") as f:
        return Image.open(io.BytesIO(base64.b64decode(f.read()))).convert("RGBA")


def ease(name, t):
    t = max(0.0, min(1.0, t))
    return {
        "linear": t,
        "easeInSine": 1 - math.cos(t * math.pi / 2),
        "easeOutSine": math.sin(t * math.pi / 2),
        "easeInOutSine": -(math.cos(math.pi * t) - 1) / 2,
        "easeInCubic": t ** 3,
        "easeOutCubic": 1 - (1 - t) ** 3,
        "easeInOutCubic": 4 * t ** 3 if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2,
        "easeInQuad": t * t,
        "easeOutQuad": 1 - (1 - t) ** 2,
        "easeInOutQuad": 2 * t * t if t < 0.5 else 1 - (-2 * t + 2) ** 2 / 2,
        "step": 0.0,
    }.get(name, t)


def sample(channel, t):
    """Keyframes {time: {vector, easing}}; the easing of the key a segment LEAVES shapes it (make_reinhard_clips rule)."""
    if not channel:
        return [0.0, 0.0, 0.0]
    if isinstance(channel, list):
        return [float(v) for v in channel]
    keys = sorted(((float(k), v) for k, v in channel.items()), key=lambda kv: kv[0])

    def vec(v):
        return [float(x) for x in (v["vector"] if isinstance(v, dict) else v)]
    if t <= keys[0][0]:
        return vec(keys[0][1])
    for (t0, a), (t1, b) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            e = a.get("easing", "linear") if isinstance(a, dict) else "linear"
            k = ease(e, (t - t0) / (t1 - t0) if t1 > t0 else 1)
            va, vb = vec(a), vec(b)
            return [va[i] + (vb[i] - va[i]) * k for i in range(3)]
    return vec(keys[-1][1])


def posed(geo, clip, t):
    g = json.loads(json.dumps(geo))
    bones = g["minecraft:geometry"][0]["bones"]
    if clip:
        for b in bones:
            ch = clip.get("bones", {}).get(b["name"])
            if not ch:
                continue
            r = sample(ch.get("rotation"), t)
            base = b.get("rotation", [0, 0, 0])
            b["rotation"] = [base[i] + r[i] for i in range(3)]
            p = sample(ch.get("position"), t)
            if any(p):
                b["_pos"] = p
    return g


def collect(geo, hide):
    g = geo["minecraft:geometry"][0]
    desc = g["description"]
    bones = {b["name"]: b for b in g["bones"]}
    cache = {}

    def m_of(name):
        if name in cache:
            return cache[name]
        b = bones[name]
        parent = b.get("parent")
        m = m_of(parent) if parent in bones else pg.IDENTITY
        pos = b.get("_pos")
        if pos:
            m = pg.mat_mul(m, pg.translate(*pos))
        m = pg.mat_mul(m, pg.rotation(b.get("pivot", [0, 0, 0]), b.get("rotation")))
        cache[name] = m
        return m

    quads = []
    for name, b in bones.items():
        if hide(name):
            continue
        m = m_of(name)
        for cube in b.get("cubes", []):
            cm = pg.mat_mul(m, pg.rotation(cube.get("pivot", [0, 0, 0]), cube.get("rotation")))
            for corners, uv in pg.cube_quads(cube):
                quads.append(([pg.apply(cm, c) for c in corners], uv))
    return quads, desc["texture_width"], desc["texture_height"]


def render_views(geo, tex, glow, views, scale, flames):
    w, h = 64 * scale, 76 * scale
    images = []
    body = collect(geo, lambda n: n.startswith("flame_"))
    lit = collect(geo, lambda n: not n.startswith("flame_")) if flames else None
    for view in views:
        img = Image.new("RGBA", (w, h), (32, 36, 44, 255))
        zbuf = [[-1e9] * w for _ in range(h)]
        ox, oy = w // 2, h - 4 * scale
        pg.raster(img, zbuf, body[0], tex, body[1], body[2], pg.VIEWS[view], scale, ox, oy)
        if glow is not None:
            pg.raster(img, zbuf, body[0], glow, body[1], body[2], pg.VIEWS[view], scale, ox, oy, additive=True)
        if lit:
            fire = png("textures/entity/ironman/repulsor_fire_0.png").resize((body[1], body[2]))
            pg.raster(img, zbuf, lit[0], fire, lit[1], lit[2], pg.VIEWS[view], scale, ox, oy, additive=True)
        images.append(img)
    return images


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--clip")
    ap.add_argument("--times", default="0")
    ap.add_argument("--scale", type=int, default=3)
    ap.add_argument("--views", default="front,side,back")
    ap.add_argument("--glow", action="store_true")
    ap.add_argument("--flames", action="store_true")
    ap.add_argument("--geo", default=GEO)
    args = ap.parse_args()
    with open(args.geo, encoding="utf-8") as f:
        geo = json.load(f)
    clip = None
    if args.clip:
        with open(ANIM, encoding="utf-8") as f:
            clip = json.load(f)["animations"][args.clip]
    tex = png("textures/entity/ironman/hulkbuster.png")
    glow = png("textures/entity/ironman/hulkbuster_glow.png") if args.glow else None
    views = args.views.split(",")
    times = [float(t) for t in args.times.split(",")]
    rows = []
    for t in times:
        rows.append(render_views(posed(geo, clip, t), tex, glow, views, args.scale, args.flames))
    cw, ch = rows[0][0].size
    sheet = Image.new("RGBA", (cw * len(views), ch * len(rows)), (0, 0, 0, 255))
    d = ImageDraw.Draw(sheet)
    for r, row in enumerate(rows):
        for c, img in enumerate(row):
            sheet.paste(img, (c * cw, r * ch))
        d.text((4, r * ch + 2), f"{args.clip or 'rest'} t={times[r]:.2f}s", fill=(255, 255, 255, 255))
    sheet.save(args.out)


if __name__ == "__main__":
    main()
