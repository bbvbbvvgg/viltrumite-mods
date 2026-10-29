#!/usr/bin/env python3
"""Thor Stage 1 art from the user archive (GoW_Thor_Kratos_assets, unpacked).

Source: God of War HeroPack 3.0.0 for Fisk's Superheroes (1.7.10) by SymbioteSpidey,
already converted to Bedrock geo by the archive (forge_1.20.1/assets/gowhp).
Credits and the per-file table: tools/assets/thor_sources.md.

- Skin: thor_layer1.png (head, body, arms and their overlays) + thor_layer2.png
  (legs and their overlays) are disjoint 64x64 player-skin layouts; layer2 is
  composited over layer1 into textures/entity/hero/thor.png (wide arms). The base
  regions must stay fully opaque (PlayerModel draws them translucent).
- Mjolnir (mjolnir.geo.json, texture 128 in the model, 512 px PNG -> 256 px, a
  premultiplied 2x2 box filter; UV islands sit on the 128 grid, so no block mixes two
  islands. Keeps every committed file under the 64 KB GitHub MCP request): two geo files,
  one per attach point, so the hammer is a real model on the player parts
  (PlayerGeoLayer), not an item:
    geo/thor/mjolnir_hand.geo.json  armorRightArm > mjolnir > MjolnirBody ...
    geo/thor/mjolnir_belt.geo.json  armorBody     > mjolnir > MjolnirBody ...
  The FiskHeroes scale 0.45 is baked into the cubes (Bedrock bones have no
  scale); box UV becomes explicit per-face UV first, with the archive's
  `mirror` flags baked in, exactly as BakedGeoModel reads a mirrored box
  (checked cube by cube below). The hammer grip point lands on the bone
  `mjolnir` pivot; that bone carries the hold rotation, so Java animations
  turn the hammer around the hand (or the belt loop) by posing one bone.
- Glow: mjolnir_lights.png (= mjolnir_glowmask.png, the blue runes; FiskHeroes
  draws it always on) -> textures/entity/thor/mjolnir_glow.png, an emissive pass.
- Beard and belly (FatherKhimsky): thorbeard root `beard` has no cubes and no
  player-part name, so it goes under armorHead; thorbelly `belly1` goes under
  armorBody and the empty `body` bones are dropped.
- Previews (tools/preview_geo.py, a box-UV player figure built from the merged
  skin) into /tmp/thor_preview_*.png; not committed.

Run: python3 tools/assets/convert_thor.py --assets DIR [--no-preview]
"""
import argparse
import base64
import copy
import json
import math
import os
import subprocess
import sys

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(REPO, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore")
BIN = os.path.join(REPO, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore")
GEO = os.path.join(RES, "geo", "thor")
PREVIEW = os.path.join(REPO, "tools", "preview_geo.py")
SRC_GEO = ("forge_1.20.1", "assets", "gowhp", "geo")
SRC_TEX = ("forge_1.20.1", "assets", "gowhp", "textures", "heroes", "thor")

# FiskHeroes renderers/heroes/thor.js: mjolnir.setScale(0.45) for both the hand and the holster model.
SCALE = 0.45
# Hand: grip on the handle axis (model px, the hammer stands on y with the head at 20..32), placed at the
# centre of the right fist (player geo px, standing pose: arm x -8..-4, y 12..24, z -2..2).
HAND_GRIP = (0.0, 5.0, 0.0)
HAND_POINT = (-6.0, 13.0, 0.0)
# Handle (+y of the hammer) forward (-z), head's long side (x) up: a tool held in the fist.
HAND_AXES = {"x": (0, 1, 0), "y": (0, 0, -1)}
# Belt (FiskHeroes holster, right hip): outside the leg (leg x -4..0), head down at the shin, handle up to
# the belt; the pommel ends inside the hanging hand, so at rest the hand seems to rest on it.
BELT_GRIP = (0.0, 6.0, 0.0)
BELT_POINT = (-6.5, 12.8, 0.4)
BELT_TILT = 8.0  # degrees from hanging straight down, the head swings back (+z)
TEXTURES = {
    "mjolnir.png": "textures/entity/thor/mjolnir.png",
    "mjolnir_lights.png": "textures/entity/thor/mjolnir_glow.png",
    "thorbeard.png": "textures/entity/thor/beard.png",
    "thorbelly.png": "textures/entity/thor/belly.png",
}
SKIN = "textures/entity/hero/thor.png"
# Sources halved on the way in (see the Mjolnir note above).
HALVED = {"mjolnir.png", "mjolnir_lights.png"}


def write_b64(data, rel):
    target = os.path.join(BIN, rel + ".b64")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w", encoding="ascii") as f:
        f.write(base64.b64encode(data).decode("ascii") + "\n")
    print("wrote", os.path.relpath(target, REPO))


def compact(value):
    """Floats rounded to 1e-4 px (well below a texel) for compact geo files."""
    if isinstance(value, float):
        value = round(value, 4)
        return int(value) if value == int(value) else value
    if isinstance(value, list):
        return [compact(v) for v in value]
    if isinstance(value, dict):
        return {k: compact(v) for k, v in value.items()}
    return value


def write_geo(geo, name):
    os.makedirs(GEO, exist_ok=True)
    path = os.path.join(GEO, name)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(compact(geo), f, separators=(",", ":"))
        f.write("\n")
    print("wrote", os.path.relpath(path, REPO))
    return path


def halve(path):
    """2x2 box filter on premultiplied RGBA, PNG bytes (transparent texels carry no colour)."""
    import io
    import numpy as np
    pixels = np.asarray(Image.open(path).convert("RGBA")).astype(np.float64)
    h, w = pixels.shape[:2]
    pixels[..., :3] *= pixels[..., 3:4] / 255.0
    pixels = pixels.reshape(h // 2, 2, w // 2, 2, 4).mean(axis=(1, 3))
    alpha = pixels[..., 3:4] / 255.0
    with np.errstate(divide="ignore", invalid="ignore"):
        pixels[..., :3] = np.where(alpha > 0, pixels[..., :3] / alpha, 0.0)
    out = io.BytesIO()
    Image.fromarray(np.clip(np.rint(pixels), 0, 255).astype(np.uint8), "RGBA").save(out, "PNG", optimize=True)
    return out.getvalue()


def merge_skin(tex_dir, out_png):
    layer1 = Image.open(os.path.join(tex_dir, "thor_layer1.png")).convert("RGBA")
    layer2 = Image.open(os.path.join(tex_dir, "thor_layer2.png")).convert("RGBA")
    if layer1.size != (64, 64) or layer2.size != (64, 64):
        sys.exit("thor layers must be 64x64")
    a1, a2 = layer1.getchannel("A").tobytes(), layer2.getchannel("A").tobytes()
    overlap = sum(1 for p, q in zip(a1, a2) if p and q)
    if overlap:
        sys.exit("layer1 and layer2 overlap in %d pixels" % overlap)
    skin = Image.alpha_composite(layer1, layer2)
    holes = base_holes(skin)
    if holes:
        sys.exit("skin base faces have %d transparent pixels" % holes)
    skin.save(out_png, optimize=True)
    with open(out_png, "rb") as f:
        return f.read()


def base_holes(skin):
    """Transparent pixels on the faces of the six base boxes (vanilla box UV layout)."""
    alpha = skin.getchannel("A").load()
    boxes = [((0, 0), (8, 8, 8)), ((16, 16), (8, 12, 4)), ((40, 16), (4, 12, 4)), ((0, 16), (4, 12, 4)),
             ((16, 48), (4, 12, 4)), ((32, 48), (4, 12, 4))]
    holes = 0
    for (u, v), (w, h, d) in boxes:
        rects = [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h),
                 (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]
        for x0, y0, rw, rh in rects:
            holes += sum(1 for y in range(y0, y0 + rh) for x in range(x0, x0 + rw) if alpha[x, y] == 0)
    return holes


# --- BakedGeoModel box UV, replicated (SWAP_EAST_WEST = true; FLIP_U is applied later to both paths alike).

def baked_box_faces(cube):
    u, v = cube.get("uv", [0, 0])
    sx, sy, sz = cube["size"]
    faces = {
        "down": [u + sz, v, u + sz + sx, v + sz],
        "up": [u + sz + sx, v + sz, u + sz + sx + sx, v],
        "north": [u + sz, v + sz, u + sz + sx, v + sz + sy],
        "south": [u + sz + sx + sz, v + sz, u + sz + sx + sz + sx, v + sz + sy],
        "east": [u, v + sz, u + sz, v + sz + sy],
        "west": [u + sz + sx, v + sz, u + sz + sx + sz, v + sz + sy],
    }
    if cube.get("mirror"):
        faces = {k: [r[2], r[1], r[0], r[3]] for k, r in faces.items()}
        faces["east"], faces["west"] = faces["west"], faces["east"]
    return faces


def baked_face_dict(uv):
    out = {k: [f["uv"][0], f["uv"][1], f["uv"][0] + f["uv_size"][0], f["uv"][1] + f["uv_size"][1]] for k, f in uv.items()}
    out["east"], out["west"] = out.get("west"), out.get("east")
    return out


def explicit_uv(cube):
    """Per-face UV that BakedGeoModel reads exactly like the (mirrored) box, mirror flag dropped."""
    faces = baked_box_faces(cube)
    uv = {}
    for name in ("north", "south", "up", "down"):
        r = faces[name]
        uv[name] = {"uv": [r[0], r[1]], "uv_size": [r[2] - r[0], r[3] - r[1]]}
    # BakedGeoModel's per-face path reads Bedrock "west" for its east quad and "east" for its west quad.
    for bedrock, baked in (("west", "east"), ("east", "west")):
        r = faces[baked]
        uv[bedrock] = {"uv": [r[0], r[1]], "uv_size": [r[2] - r[0], r[3] - r[1]]}
    out = {k: v for k, v in cube.items() if k not in ("uv", "mirror")}
    out["uv"] = uv
    if baked_face_dict(uv) != faces:
        sys.exit("explicit UV differs from the box for %s" % json.dumps(cube))
    return out


# --- rotations: Blockbench/Bedrock [a, b, c] degrees means R = Rz(-c) Ry(b) Rx(-a) in geo space (BakedGeoModel).

def mat(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def rx(t):
    c, s = math.cos(t), math.sin(t)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(t):
    c, s = math.cos(t), math.sin(t)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(t):
    c, s = math.cos(t), math.sin(t)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def bedrock_matrix(rot):
    a, b, c = (math.radians(v) for v in rot)
    return mat(mat(rz(-c), ry(b)), rx(-a))


def bedrock_euler(m):
    """[a, b, c] for a rotation matrix (inverse of bedrock_matrix)."""
    b = -math.asin(max(-1.0, min(1.0, m[2][0])))
    big_a = math.atan2(m[2][1], m[2][2])
    big_c = math.atan2(m[1][0], m[0][0])
    rot = [round(-math.degrees(big_a), 4) + 0.0, round(math.degrees(b), 4) + 0.0, round(-math.degrees(big_c), 4) + 0.0]
    check = bedrock_matrix(rot)
    if max(abs(check[i][j] - m[i][j]) for i in range(3) for j in range(3)) > 1e-6:
        sys.exit("euler round trip failed")
    return rot


def axes_matrix(axes):
    """Rotation that sends the hammer's x and y axes to the given geo directions."""
    x, y = axes["x"], axes["y"]
    z = (x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0])
    return [[x[i], y[i], z[i]] for i in range(3)]


# --- geo building

def load_geo(assets, name):
    with open(os.path.join(assets, *SRC_GEO, name), encoding="utf-8") as f:
        return json.load(f)


def geometry(identifier, tw, th, bones):
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": identifier, "texture_width": tw, "texture_height": th},
        "bones": bones}]}


def placed(bones, grip, point, k):
    """Uniform scale k about the grip, grip moved to point; UV made explicit first."""
    def at(p):
        return [round((p[i] - grip[i]) * k + point[i], 4) + 0.0 for i in range(3)]

    out = []
    for b in copy.deepcopy(bones):
        b["pivot"] = at(b.get("pivot", [0, 0, 0]))
        cubes = []
        for c in b.get("cubes", []):
            c = explicit_uv(c)
            c["origin"] = at(c["origin"])
            c["size"] = [round(x * k, 4) + 0.0 for x in c["size"]]
            if c.get("inflate"):
                c["inflate"] = round(c["inflate"] * k, 4)
            if c.get("pivot"):
                c["pivot"] = at(c["pivot"])
            cubes.append(c)
        if cubes:
            b["cubes"] = cubes
        out.append(b)
    return out


def mjolnir(assets, root, root_pivot, grip, point, rotation, identifier):
    src = load_geo(assets, "mjolnir.geo.json")["minecraft:geometry"][0]
    bones = placed(src["bones"], grip, point, SCALE)
    for b in bones:
        if b.get("parent") is None:
            b["parent"] = "mjolnir"
    head = [{"name": root, "pivot": list(root_pivot)},
            {"name": "mjolnir", "parent": root, "pivot": [round(v, 4) + 0.0 for v in point], "rotation": rotation}]
    d = src["description"]
    return geometry(identifier, d["texture_width"], d["texture_height"], head + bones)


def beard(assets):
    src = load_geo(assets, "thorbeard.geo.json")["minecraft:geometry"][0]
    bones = placed(src["bones"], (0, 0, 0), (0, 0, 0), 1.0)
    for b in bones:
        if b["name"] == "beard":
            b["parent"] = "armorHead"
    d = src["description"]
    return geometry("geometry.thor_beard", d["texture_width"], d["texture_height"],
                    [{"name": "armorHead", "pivot": [0, 24, 0]}] + bones)


def belly(assets):
    src = load_geo(assets, "thorbelly.geo.json")["minecraft:geometry"][0]
    keep = [b for b in src["bones"] if b["name"] not in ("body", "body_2")]
    if any(b.get("cubes") for b in src["bones"] if b["name"] in ("body", "body_2")):
        sys.exit("thorbelly body bones are no longer empty")
    bones = placed(keep, (0, 0, 0), (0, 0, 0), 1.0)
    for b in bones:
        if b["name"] == "belly1":
            b["parent"] = "armorBody"
    d = src["description"]
    return geometry("geometry.thor_belly", d["texture_width"], d["texture_height"],
                    [{"name": "armorBody", "pivot": [0, 24, 0]}] + bones)


def belt_matrix():
    # Hanging straight: head down (+y of the hammer -> -y), head's long side front-back (x -> -z),
    # then tilted round x so the head swings back.
    hang = axes_matrix({"x": (0, 0, -1), "y": (0, -1, 0)})
    t = math.radians(BELT_TILT)
    return mat(rx(-t), hang)


# --- preview figure (standard Bedrock box UV, preview only)

FIGURE = {
    "armorHead": ((0, 0), (8, 8, 8), (-4, 24, -4), (32, 0), 0.5),
    "armorBody": ((16, 16), (8, 12, 4), (-4, 12, -2), (16, 32), 0.25),
    "armorRightArm": ((40, 16), (4, 12, 4), (-8, 12, -2), (40, 32), 0.25),
    "armorLeftArm": ((32, 48), (4, 12, 4), (4, 12, -2), (48, 48), 0.25),
    "armorRightLeg": ((0, 16), (4, 12, 4), (-4, 0, -2), (0, 32), 0.25),
    "armorLeftLeg": ((16, 48), (4, 12, 4), (0, 0, -2), (0, 48), 0.25),
}


def figure_geo():
    bones = []
    for name, (uv, size, origin, ov, inflate) in FIGURE.items():
        bones.append({"name": name, "pivot": [0, 0, 0], "cubes": [
            {"origin": list(origin), "size": list(size), "uv": list(uv)},
            {"origin": list(origin), "size": list(size), "uv": list(ov), "inflate": inflate}]})
    return geometry("geometry.thor_preview_figure", 64, 64, bones)


def run(*args):
    out = subprocess.run([sys.executable, *args], capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(out.stderr or out.stdout)
    if out.stdout.strip():
        print(out.stdout.rstrip())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args()
    a = args.assets
    tex_dir = os.path.join(a, *SRC_TEX)

    skin_tmp = "/tmp/thor_skin.png"
    write_b64(merge_skin(tex_dir, skin_tmp), SKIN)
    for src, dst in TEXTURES.items():
        if src in HALVED:
            write_b64(halve(os.path.join(tex_dir, src)), dst)
            continue
        with open(os.path.join(tex_dir, src), "rb") as f:
            write_b64(f.read(), dst)

    hand_rot = bedrock_euler(axes_matrix(HAND_AXES))
    belt_rot = bedrock_euler(belt_matrix())
    hand = write_geo(mjolnir(a, "armorRightArm", (-5, 22, 0), HAND_GRIP, HAND_POINT, hand_rot, "geometry.thor_mjolnir_hand"),
                     "mjolnir_hand.geo.json")
    belt = write_geo(mjolnir(a, "armorBody", (0, 24, 0), BELT_GRIP, BELT_POINT, belt_rot, "geometry.thor_mjolnir_belt"),
                     "mjolnir_belt.geo.json")
    beard_geo = write_geo(beard(a), "beard.geo.json")
    belly_geo = write_geo(belly(a), "belly.geo.json")
    print("hand rotation", hand_rot, "belt rotation", belt_rot)

    if args.no_preview:
        return
    fig = "/tmp/thor_preview_figure.geo.json"
    with open(fig, "w", encoding="utf-8") as f:
        json.dump(figure_geo(), f)
    mj = os.path.join(tex_dir, "mjolnir.png") + ":" + os.path.join(tex_dir, "mjolnir_lights.png")
    body = [fig + ":" + skin_tmp, beard_geo + ":" + os.path.join(tex_dir, "thorbeard.png"),
            belly_geo + ":" + os.path.join(tex_dir, "thorbelly.png")]
    run(PREVIEW, "/tmp/thor_preview_belt.png", *body, belt + ":" + mj, "--scale", "10", "--views", "front,side,back")
    run(PREVIEW, "/tmp/thor_preview_hand.png", *body, hand + ":" + mj, "--scale", "10", "--views", "front,side,top")
    run(PREVIEW, "/tmp/thor_preview_mjolnir.png", hand + ":" + mj, "--scale", "16", "--views", "front,side")
    print("previews: /tmp/thor_preview_{belt,hand,mjolnir}.png")


if __name__ == "__main__":
    main()
