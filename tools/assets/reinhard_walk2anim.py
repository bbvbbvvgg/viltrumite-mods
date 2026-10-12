#!/usr/bin/env python3
"""reinhard_walk.json (user archive) -> clip `walk` of animations/reinhard/reinhard.animation.json.

Input: {length: s, referenceAmount: a, tracks: {bone: [{time, rotation: [x, y, z]}]}},
rotation in degrees in Java ModelPart convention. BakedGeoModel applies (-x, -y, z)
of a Bedrock bone rotation and the parser negates x and y, so the same numbers
give the same turn as a ModelPart: values are copied unchanged.
Output: format 1.8.0, loop true, animation_length = length, linear keyframes.
Other clips already in the file are kept (tools/assets/make_reinhard_clips.py
writes the stance clips into the same file). referenceAmount is printed for
ReinhardPoses.WALK_REFERENCE_AMOUNT.

Run: python3 tools/assets/reinhard_walk2anim.py --assets DIR
"""
import argparse
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(REPO, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore",
                   "animations", "reinhard", "reinhard.animation.json")


def num(v):
    v = round(float(v), 4)
    return 0 if v == 0 else (int(v) if v == int(v) else v)


def time_key(t):
    return ("%.4f" % float(t)).rstrip("0").rstrip(".") if float(t) else "0.0"


def convert(src):
    bones = {}
    for bone, keys in src["tracks"].items():
        rotation = {}
        for k in keys:
            rotation[time_key(k["time"])] = [num(c) for c in k["rotation"]]
        bones[bone] = {"rotation": rotation}
    return {"loop": True, "animation_length": num(src["length"]), "bones": bones}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    args = ap.parse_args()
    with open(os.path.join(args.assets, "reinhard_model", "reinhard_walk.json"), encoding="utf-8") as f:
        src = json.load(f)
    data = {"format_version": "1.8.0", "animations": {}}
    if os.path.exists(OUT):
        with open(OUT, encoding="utf-8") as f:
            data = json.load(f)
    data["animations"]["walk"] = convert(src)
    data["animations"] = dict(sorted(data["animations"].items()))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print("wrote", os.path.relpath(OUT, REPO))
    print("WALK_REFERENCE_AMOUNT =", src["referenceAmount"], "; length", src["length"], "s")


if __name__ == "__main__":
    main()
