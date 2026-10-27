#!/usr/bin/env python3
"""Session A: the free-standing world Mjolnir model (MjolnirEntity).

Takes the hand prop (geo/thor/mjolnir_hand.geo.json, from convert_thor.py),
drops the arm / hand-grip bones and recentres the hammer on its own middle:
the handle runs along +Y below the origin, the head sits above it, so the
renderer can spin, lay and turn it around the origin. Same cubes and UVs:
the caught hammer and the hand prop are the same size (no model jump).
Run from the repository root.
"""
import copy
import json
from pathlib import Path

GEO = Path("viltrumitecore/src/main/resources/assets/viltrumitecore/geo/thor")
SRC = GEO / "mjolnir_hand.geo.json"
OUT = GEO / "mjolnir_world.geo.json"
# Handle axis at x=-6, z=0; the hammer spans y 9..24 (px): its middle goes to the origin.
SHIFT = (6.0, -16.5, 0.0)


def shift(vec):
    return [round(v + s, 4) for v, s in zip(vec, SHIFT)]


def main():
    data = json.loads(SRC.read_text())
    geo = data["minecraft:geometry"][0]
    bones = []
    for bone in geo["bones"]:
        if bone["name"] in ("armorRightArm", "mjolnir"):
            continue
        bone = copy.deepcopy(bone)
        if bone.get("parent") == "mjolnir":
            bone["parent"] = "mjolnir_world"
        if "pivot" in bone:
            bone["pivot"] = shift(bone["pivot"])
        for cube in bone.get("cubes", []):
            cube["origin"] = shift(cube["origin"])
            if "pivot" in cube:
                cube["pivot"] = shift(cube["pivot"])
        bones.append(bone)
    root = {"name": "mjolnir_world", "pivot": [0, 0, 0]}
    out = copy.deepcopy(data)
    out_geo = out["minecraft:geometry"][0]
    out_geo["description"]["identifier"] = "geometry.thor_mjolnir_world"
    out_geo["bones"] = [root] + bones
    OUT.write_text(json.dumps(out, separators=(",", ":")) + "\n")
    print(f"wrote {OUT} ({len(bones)} bones)")


if __name__ == "__main__":
    main()
