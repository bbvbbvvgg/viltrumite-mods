#!/usr/bin/env python3
"""Reinhard Stage 1b art from the user archive (reinhard_assets.zip, unpacked).

- reinhard_model/reinhard.obj -> geo/reinhard/reinhard.geo.json (six body
  bones, vanilla pivots, neck at y 24) and reinhard_arms.geo.json (arms only,
  armor bone names for PlayerGeoLayer.renderArmPart).
- sword/blade_sheath.obj -> geo/reinhard/sheath.geo.json, sword/training_blade_drawn.obj
  -> geo/reinhard/blade.geo.json: block units x16, recentred so the hilt guard
  is the origin, one bone `sword`. The sheathed position of the blade is the
  translation that maps the drawn blade onto the faces of training_blade.obj
  that are not in blade_sheath.obj (least squares); written as the pivot of
  the empty bone `sheathed_offset` in blade.geo.json and printed as the Java
  line for ReinhardAssets.SHEATHED_OFFSET.
- the sword OBJs repeat every face under material `dragon_glow` pushed out
  by 0.0001 block (emissive overlay) and measure vt v from the top of the
  image: overlay faces are dropped (--skip-material-suffix _glow, the glow
  texture is drawn over the same geometry) and --uv-top-origin is passed.
- textures copied unchanged as binassets .png.b64.
- previews (tools/preview_geo.py) into /tmp/reinhard_preview_*.png for the
  check against the archive's previews/*.png.

Run: python3 tools/assets/convert_reinhard.py --assets DIR
"""
import argparse
import base64
import os
import statistics
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(REPO, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore")
BIN = os.path.join(REPO, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore")
GEO = os.path.join(RES, "geo", "reinhard")
OBJ2GEO = os.path.join(REPO, "tools", "obj2geo.py")
PREVIEW = os.path.join(REPO, "tools", "preview_geo.py")
SWORD_SCALE = 16.0
# Hilt guard of the drawn sword in OBJ block units x16 (widest band, y 12 px; x, z centre 8 px).
GUARD = (8.0, 12.0, 8.0)
TEXTURES = {
    "reinhard_model/reinhard.png": "textures/entity/reinhard/reinhard.png",
    "reinhard_model/reinhard_glow.png": "textures/entity/reinhard/reinhard_glow.png",
    "sword/dragon_sword.png": "textures/entity/reinhard/dragon_sword.png",
    "sword/dragon_sword_glow.png": "textures/entity/reinhard/dragon_sword_glow.png",
}


def run(*args):
    out = subprocess.run([sys.executable, *args], capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(out.stderr or out.stdout)
    print(out.stdout.rstrip())


def obj_faces(path, scale):
    verts, faces = [], []
    glow = False
    with open(path, encoding="utf-8") as f:
        for line in f:
            p = line.split()
            if not p:
                continue
            if p[0] == "usemtl":
                glow = p[1].endswith("_glow")
            elif p[0] == "v":
                verts.append(tuple(float(v) * scale for v in p[1:4]))
            elif p[0] == "f" and not glow:
                faces.append(tuple(verts[int(q.split("/")[0]) - 1] for q in p[1:]))
    return faces


def sheathed_offset(assets):
    """Translation (px) from the drawn blade to its place inside the sheath."""
    sheath = obj_faces(os.path.join(assets, "sword", "blade_sheath.obj"), SWORD_SCALE)
    whole = obj_faces(os.path.join(assets, "sword", "training_blade.obj"), SWORD_SCALE)
    drawn = obj_faces(os.path.join(assets, "sword", "training_blade_drawn.obj"), SWORD_SCALE)

    def key(face):
        return tuple(sorted(tuple(round(c, 3) for c in v) for v in face))
    in_sheath = {key(f) for f in sheath}
    blade = [f for f in whole if key(f) not in in_sheath]
    if len(blade) != len(drawn):
        sys.exit("sheathed blade has %d faces, drawn blade %d" % (len(blade), len(drawn)))
    deltas = [[b[i][k] - d[i][k] for b, d in zip(blade, drawn) for i in range(4)] for k in range(3)]
    t = [statistics.fmean(axis) for axis in deltas]
    residual = max(abs(v - t[k]) for k in range(3) for v in deltas[k])
    if residual > 1e-3:
        sys.exit("sheathed blade is not a pure translation of the drawn blade (residual %.4f px)" % residual)
    return [round(v, 4) + 0.0 for v in t]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    args = ap.parse_args()
    a = args.assets
    os.makedirs(GEO, exist_ok=True)
    body_obj = os.path.join(a, "reinhard_model", "reinhard.obj")
    run(OBJ2GEO, body_obj, os.path.join(GEO, "reinhard.geo.json"), "--texture", "128", "128",
        "--bones", "head,body,right_arm,left_arm,right_leg,left_leg", "--pivots", "vanilla", "--offset", "0", "24", "0",
        "--identifier", "geometry.reinhard")
    run(OBJ2GEO, body_obj, os.path.join(GEO, "reinhard_arms.geo.json"), "--texture", "128", "128",
        "--bones", "right_arm,left_arm", "--pivots", "vanilla", "--armor-bones", "--offset", "0", "24", "0",
        "--identifier", "geometry.reinhard_arms")
    run(os.path.join(REPO, "tools", "assets", "rig_reinhard.py"))
    offset = [-GUARD[0], -GUARD[1], -GUARD[2]]
    common = ["--texture", "128", "128", "--scale", str(SWORD_SCALE), "--offset", *map(str, offset), "--single-bone", "sword", "--pivot", "0", "0", "0",
              "--skip-material-suffix", "_glow", "--uv-top-origin"]
    run(OBJ2GEO, os.path.join(a, "sword", "blade_sheath.obj"), os.path.join(GEO, "sheath.geo.json"), *common, "--identifier", "geometry.reinhard_sheath")
    t = sheathed_offset(a)
    run(OBJ2GEO, os.path.join(a, "sword", "training_blade_drawn.obj"), os.path.join(GEO, "blade.geo.json"), *common,
        "--identifier", "geometry.reinhard_blade", "--meta-bone", "sheathed_offset", *map(str, t))
    print("Java: public static final Vector3f SHEATHED_OFFSET = new Vector3f(%sF, %sF, %sF);" % tuple(t))
    for src, dst in TEXTURES.items():
        with open(os.path.join(a, src), "rb") as f:
            data = f.read()
        target = os.path.join(BIN, dst + ".b64")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, "w", encoding="ascii") as f:
            f.write(base64.b64encode(data).decode("ascii") + "\n")
        print("wrote", os.path.relpath(target, REPO))
    tex = os.path.join(a, "reinhard_model", "reinhard.png") + ":" + os.path.join(a, "reinhard_model", "reinhard_glow.png")
    sword_tex = os.path.join(a, "sword", "dragon_sword.png") + ":" + os.path.join(a, "sword", "dragon_sword_glow.png")
    run(PREVIEW, "/tmp/reinhard_preview_body.png", os.path.join(GEO, "reinhard.geo.json") + ":" + tex, "--scale", "8", "--views", "front,back,side")
    run(PREVIEW, "/tmp/reinhard_preview_sword.png", os.path.join(GEO, "sheath.geo.json") + ":" + sword_tex, "--scale", "6", "--views", "front,side")
    run(PREVIEW, "/tmp/reinhard_preview_drawn.png", os.path.join(GEO, "blade.geo.json") + ":" + sword_tex, "--scale", "6", "--views", "front,side")


if __name__ == "__main__":
    main()
