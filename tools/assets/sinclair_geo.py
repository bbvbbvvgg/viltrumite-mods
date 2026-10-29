#!/usr/bin/env python3
"""Tiny builder for Bedrock geo models with per-face material swatches.

Shared by make_sinclair_model.py. A model is a list of bones; every cube names a
material and every face is mapped onto that material's 16x16 swatch in the atlas
(texel density 1 px per model px, clamped to the swatch). The atlas is painted by
sinclair_paint.py. Coordinates are Bedrock px: y up, the model faces -z (north),
+x is the model's left.
"""
import json

FACES = ("north", "south", "east", "west", "up", "down")
SWATCH = 16


class Atlas:
    """Assigns 16x16 slots to material names in first-use order."""

    def __init__(self, size=256):
        self.size = size
        self.slots = {}

    def slot(self, mat):
        if mat not in self.slots:
            n = len(self.slots)
            per_row = self.size // SWATCH
            if n >= per_row * per_row:
                raise ValueError("atlas full")
            self.slots[mat] = ((n % per_row) * SWATCH, (n // per_row) * SWATCH)
        return self.slots[mat]


class Bone:
    def __init__(self, model, name, parent, pivot, rotation=None):
        self.model = model
        self.name = name
        self.parent = parent
        self.pivot = [round(v, 4) for v in pivot]
        self.rotation = rotation
        self.cubes = []

    def cube(self, origin, size, mat, faces=None, pivot=None, rotation=None, inflate=0.0):
        """faces: optional {face: material} overrides (e.g. a lining or a painted front)."""
        self.cubes.append(dict(origin=origin, size=size, mat=mat, faces=faces or {}, pivot=pivot,
                               rotation=rotation, inflate=inflate))
        return self

    def to_json(self, atlas):
        out = {"name": self.name, "pivot": self.pivot}
        if self.parent:
            out["parent"] = self.parent
        if self.rotation and any(abs(r) > 1e-6 for r in self.rotation):
            out["rotation"] = [round(r, 3) for r in self.rotation]
        cubes = []
        for c in self.cubes:
            w, h, d = c["size"]
            dims = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
            uv = {}
            for face in FACES:
                mat = c["faces"].get(face, c["mat"])
                sx, sy = atlas.slot(mat)
                fw, fh = dims[face]
                fw = max(0.25, min(SWATCH - 0.5, fw))
                fh = max(0.25, min(SWATCH - 0.5, fh))
                ox = (SWATCH - fw) / 2.0
                oy = (SWATCH - fh) / 2.0
                uv[face] = {"uv": [round(sx + ox, 4), round(sy + oy, 4)], "uv_size": [round(fw, 4), round(fh, 4)]}
            cube = {"origin": [round(v, 4) for v in c["origin"]], "size": [round(v, 4) for v in c["size"]], "uv": uv}
            if c["inflate"]:
                cube["inflate"] = c["inflate"]
            if c["rotation"] is not None:
                cube["pivot"] = [round(v, 4) for v in (c["pivot"] or c["origin"])]
                cube["rotation"] = [round(r, 3) for r in c["rotation"]]
            cubes.append(cube)
        if cubes:
            out["cubes"] = cubes
        return out


class Model:
    def __init__(self, identifier, atlas):
        self.identifier = identifier
        self.atlas = atlas
        self.bones = []
        self.by_name = {}

    def bone(self, name, parent, pivot, rotation=None):
        if name in self.by_name:
            raise ValueError("duplicate bone " + name)
        if parent and parent not in self.by_name:
            raise ValueError("unknown parent %s for %s" % (parent, name))
        b = Bone(self, name, parent, pivot, rotation)
        self.bones.append(b)
        self.by_name[name] = b
        return b

    def to_json(self, bounds=(4, 4)):
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry." + self.identifier,
                    "texture_width": self.atlas.size,
                    "texture_height": self.atlas.size,
                    "visible_bounds_width": bounds[0],
                    "visible_bounds_height": bounds[1],
                    "visible_bounds_offset": [0, 1, 0],
                },
                "bones": [b.to_json(self.atlas) for b in self.bones],
            }],
        }

    def write(self, path, bounds=(4, 4)):
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(self.to_json(bounds), fh, indent=1)
            fh.write("\n")


def mirror_x(origin, size):
    """Mirror a cube across the model's x = 0 plane."""
    return [-(origin[0] + size[0]), origin[1], origin[2]]


def mirror_rot(rot):
    if rot is None:
        return None
    return [rot[0], -rot[1], -rot[2]]
