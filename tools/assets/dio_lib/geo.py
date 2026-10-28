"""Bedrock 1.12 geo builder with a per-face texture atlas.

Every cube face gets its own atlas rectangle (DENSITY texels per model unit),
painted procedurally by dio_lib.paint. Coordinates are Blockbench/bedrock:
y up, the model faces north (-Z), the right side of the body is -X.
"""
import json
import math

import numpy as np

FACES = ("north", "south", "east", "west", "up", "down")


class Cube:
    def __init__(self, origin, size, mat, inflate=0.0, pivot=None, rotation=None, deco=None, deco_faces=None, glow=False):
        self.origin = [float(v) for v in origin]
        self.size = [float(v) for v in size]
        self.mat = mat
        self.inflate = float(inflate)
        self.pivot = pivot
        self.rotation = rotation
        self.deco = deco or {}
        if deco_faces is not None and isinstance(deco, str):
            self.deco = {f: deco for f in deco_faces}
        self.glow = glow
        self.uv = {}

    def face_dims(self, face):
        sx, sy, sz = self.size
        return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[face]


class Bone:
    def __init__(self, name, parent, pivot, rotation=None):
        self.name = name
        self.parent = parent
        self.pivot = [float(v) for v in pivot]
        self.rotation = rotation
        self.cubes = []


class Geo:
    def __init__(self, identifier, density=2):
        self.identifier = identifier
        self.density = density
        self.bones = []
        self.by_name = {}

    def bone(self, name, parent, pivot, rotation=None):
        assert name not in self.by_name, name
        b = Bone(name, parent, pivot, rotation)
        self.bones.append(b)
        self.by_name[name] = b
        return name

    def cube(self, bone, origin, size, mat, **kw):
        c = Cube(origin, size, mat, **kw)
        self.by_name[bone].cubes.append(c)
        return c

    def mirror_x(self, src_bone, dst_bone, cube_filter=None):
        """Copies the cubes of src_bone mirrored over x = 0 into dst_bone."""
        for c in self.by_name[src_bone].cubes:
            if cube_filter and not cube_filter(c):
                continue
            o, s = c.origin, c.size
            piv = None if c.pivot is None else [-c.pivot[0], c.pivot[1], c.pivot[2]]
            rot = None if c.rotation is None else [c.rotation[0], -c.rotation[1], -c.rotation[2]]
            deco = dict(c.deco)
            if "east" in deco or "west" in deco:
                e, w = deco.pop("east", None), deco.pop("west", None)
                if w: deco["east"] = w
                if e: deco["west"] = e
            nc = Cube([-(o[0] + s[0]), o[1], o[2]], s, c.mat, inflate=c.inflate, pivot=piv, rotation=rot, deco=deco, glow=c.glow)
            self.by_name[dst_bone].cubes.append(nc)

    # --- atlas -----------------------------------------------------------------
    def pack(self, width=512):
        rects = []
        for b in self.bones:
            for c in b.cubes:
                for f in FACES:
                    w, h = c.face_dims(f)
                    pw = max(1, int(math.ceil(w * self.density - 1e-6)))
                    ph = max(1, int(math.ceil(h * self.density - 1e-6)))
                    rects.append((ph, pw, c, f))
        rects.sort(key=lambda r: (-r[0], -r[1]))
        x = y = shelf = 0
        pad = 1
        for ph, pw, c, f in rects:
            if x + pw + pad > width:
                x = 0
                y += shelf + pad
                shelf = 0
            c.uv[f] = (x, y, pw, ph)
            x += pw + pad
            shelf = max(shelf, ph)
        height = y + shelf + pad
        h2 = 16
        while h2 < height:
            h2 *= 2
        self.tex_w, self.tex_h = width, h2
        return width, h2

    def paint(self, painter):
        albedo = np.zeros((self.tex_h, self.tex_w, 4), dtype=np.float32)
        glow = np.zeros((self.tex_h, self.tex_w, 4), dtype=np.float32)
        seed = 0
        for b in self.bones:
            for c in b.cubes:
                for f in FACES:
                    x, y, w, h = c.uv[f]
                    seed += 1
                    a, g = painter(c.mat, w, h, f, c.deco.get(f), seed, c)
                    albedo[y:y + h, x:x + w] = a
                    if g is not None:
                        glow[y:y + h, x:x + w] = g
        return albedo, glow

    def to_json(self):
        bones = []
        for b in self.bones:
            jb = {"name": b.name, "pivot": [round(v, 4) for v in b.pivot]}
            if b.parent:
                jb["parent"] = b.parent
            if b.rotation:
                jb["rotation"] = b.rotation
            cubes = []
            for c in b.cubes:
                jc = {"origin": [round(v, 4) for v in c.origin], "size": [round(v, 4) for v in c.size]}
                if c.inflate:
                    jc["inflate"] = c.inflate
                if c.rotation:
                    jc["pivot"] = [round(v, 4) for v in (c.pivot or [c.origin[i] + c.size[i] / 2 for i in range(3)])]
                    jc["rotation"] = c.rotation
                # uv_size is exact model size in texels so the face samples its own painted rect.
                jc["uv"] = {f: {"uv": [c.uv[f][0], c.uv[f][1]], "uv_size": [c.uv[f][2], c.uv[f][3]]} for f in FACES}
                cubes.append(jc)
            if cubes:
                jb["cubes"] = cubes
            bones.append(jb)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {"identifier": "geometry." + self.identifier, "texture_width": self.tex_w, "texture_height": self.tex_h,
                                "visible_bounds_width": 6, "visible_bounds_height": 6, "visible_bounds_offset": [0, 1.5, 0]},
                "bones": bones,
            }],
        }

    def dump(self, path):
        with open(path, "w") as fh:
            json.dump(self.to_json(), fh, separators=(",", ":"))
            fh.write("\n")

    def cube_count(self):
        return sum(len(b.cubes) for b in self.bones)
