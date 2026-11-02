"""Review helper: the 64x64 skin on a classic player geo (+ overlay layer) with the outfit geo on top."""
import json
import math
import os
import tempfile

from dio_lib import preview

PARTS = (  # name, pivot, origin, size, base uv, overlay uv
    ("head", (0, 24, 0), (-4, 24, -4), (8, 8, 8), (0, 0), (32, 0)),
    ("body", (0, 24, 0), (-4, 12, -2), (8, 12, 4), (16, 16), (16, 32)),
    ("rightarm", (-5, 22, 0), (-8, 12, -2), (4, 12, 4), (40, 16), (40, 32)),
    ("leftarm", (5, 22, 0), (4, 12, -2), (4, 12, 4), (32, 48), (48, 48)),
    ("rightleg", (-1.9, 12, 0), (-3.9, 0, -2), (4, 12, 4), (0, 16), (0, 32)),
    ("leftleg", (1.9, 12, 0), (-0.1, 0, -2), (4, 12, 4), (16, 48), (0, 48)),
)


def _faces(u, v, w, h, d):
    r = lambda x, y, a, b: {"uv": [x, y], "uv_size": [a, b]}
    return {"up": r(u + d, v, w, d), "down": r(u + d + w, v, w, d), "east": r(u, v + d, d, h), "north": r(u + d, v + d, w, h),
            "west": r(u + d + w, v + d, d, h), "south": r(u + d + w + d, v + d, w, h)}


def player_geo():
    bones = []
    for name, piv, o, s, uv, ov in PARTS:
        cubes = [{"origin": list(o), "size": list(s), "uv": _faces(*uv, *s)},
                 {"origin": list(o), "size": list(s), "inflate": 0.5 if name != "head" else 0.5, "uv": _faces(*ov, *s)}]
        bones.append({"name": name, "pivot": list(piv), "cubes": cubes})
    return {"format_version": "1.12.0", "minecraft:geometry": [{"description": {"identifier": "geometry.p", "texture_width": 64,
                                                                                "texture_height": 64}, "bones": bones}]}


def models(skin_png, outfit_geo_json, outfit_png):
    d = tempfile.mkdtemp()
    p = os.path.join(d, "p.geo.json")
    json.dump(player_geo(), open(p, "w"))
    o = os.path.join(d, "o.geo.json")
    open(o, "wb").write(outfit_geo_json)
    return preview.PModel(p, skin_png), preview.PModel(o, outfit_png)


def pose_parts(model, rots):
    """rots: {bone: (x, y, z) degrees, vanilla ModelPart convention (x<0 raises an arm forward)}"""
    model.reset()
    for n, r in rots.items():
        b = model.bones.get(n)
        if b is not None:
            b.rot = [math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2])]


def sheet(skin_png, outfit_geo_json, outfit_png, rots=None, size=(320, 460), dist=4.6, views=((180, 6), (235, 6), (0, 6), (130, 6)),
          hidden=()):
    from PIL import Image
    pm, om = models(skin_png, outfit_geo_json, outfit_png)
    for m in (pm, om):
        pose_parts(m, rots or {})
    for n in hidden:
        if n in om.bones:
            om.bones[n].cubes = []
    root = preview._ry(math.radians(180))
    layers = [(pm.triangles(root), pm.tex, 1), (om.triangles(root), om.tex, 1)]
    out = Image.new("RGB", (size[0] * len(views), size[1]))
    for i, (yaw, pitch) in enumerate(views):
        cam = preview.Camera(yaw, pitch, (0, 1.05, 0), dist, size[0], size[1])
        out.paste(preview.render(layers, cam), (i * size[0], 0))
    return out
