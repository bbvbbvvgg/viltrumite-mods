#!/usr/bin/env python3
"""OBJ (Blockbench-style cube export) -> Bedrock geo.json for BakedGeoModel.

Every `o` object becomes one top-level bone. Quads are grouped into boxes:
consecutive quads whose corners are the corners of one box (axis-aligned or
rotated) form one cube; a lone quad becomes a zero-thickness cube with only
that face. A rotated box gets a cube `rotation` about its centre (`pivot`).
Missing faces are omitted (BakedGeoModel draws nothing for them).

Coordinates: OBJ x = Bedrock x (right = -x), y up; `--offset` is added after
`--scale` (the body OBJ has the neck at y 0: --offset 0 24 0).
UVs: per-face `uv` / `uv_size` solved so that the quad BakedGeoModel builds
(FLIP_U, SWAP_EAST_WEST, x negated) carries exactly the OBJ vertex UVs
(v flipped: v_px = (1 - vt) * H; with --uv-top-origin v_px = vt * H).
`--skip-material-suffix _glow` drops emissive overlay copies of faces (the
renderer draws the glow texture over the same geometry). A face whose UV is turned 90 degrees
against its edges cannot be written as a Bedrock face: error with its index.
Every cube is checked by rebuilding the parser's quads and comparing.

Run: python3 tools/obj2geo.py IN.obj OUT.geo.json --texture W H
     [--bones head,body,...] [--pivots vanilla|none] [--armor-bones]
     [--scale S] [--offset X Y Z] [--identifier NAME] [--pivot X Y Z]
     [--single-bone NAME] [--meta-bone NAME X Y Z]
     [--skip-material-suffix SUFFIX] [--uv-top-origin]
"""
import argparse
import json
import math
import sys

EPS = 1e-4
VANILLA_PIVOTS = {
    "head": (0, 24, 0), "body": (0, 24, 0),
    "right_arm": (-5, 22, 0), "left_arm": (5, 22, 0),
    "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0),
}
ARMOR_NAMES = {
    "head": "armorHead", "body": "armorBody", "right_arm": "armorRightArm", "left_arm": "armorLeftArm",
    "right_leg": "armorRightLeg", "left_leg": "armorLeftLeg",
}


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def mul(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def norm(a):
    length = math.sqrt(dot(a, a))
    return mul(a, 1.0 / length) if length > 1e-12 else None


def close(a, b, eps=EPS * 10):
    return all(abs(x - y) <= eps for x, y in zip(a, b))


def load_obj(path, scale, offset, skip_suffix=None, uv_top=False):
    objects = []
    verts, uvs = [], []
    current = None
    skipping = False
    with open(path, encoding="utf-8") as f:
        for line in f:
            p = line.split()
            if not p:
                continue
            if p[0] == "o":
                current = {"name": p[1], "faces": []}
                objects.append(current)
            elif p[0] == "v":
                x, y, z = (float(v) * scale for v in p[1:4])
                verts.append((x + offset[0], y + offset[1], z + offset[2]))
            elif p[0] == "vt":
                v = float(p[2])
                uvs.append((float(p[1]), 1.0 - v if uv_top else v))
            elif p[0] == "usemtl":
                skipping = bool(skip_suffix) and p[1].endswith(skip_suffix)
            elif p[0] == "f" and skipping:
                continue
            elif p[0] == "f":
                if current is None:
                    current = {"name": "bone", "faces": []}
                    objects.append(current)
                corners = []
                for q in p[1:]:
                    idx = q.split("/")
                    corners.append((verts[int(idx[0]) - 1], uvs[int(idx[1]) - 1] if len(idx) > 1 and idx[1] else (0.0, 0.0)))
                if len(corners) != 4:
                    sys.exit("face %d of %s has %d corners (quads only)" % (len(current["faces"]), current["name"], len(corners)))
                current["faces"].append(corners)
    return objects


def quad_axes(quad):
    p = [c[0] for c in quad]
    e1 = norm(sub(p[1], p[0]))
    e2 = norm(sub(p[3], p[0]))
    if e1 is None or e2 is None or abs(dot(e1, e2)) > 1e-3:
        return None
    return e1, e2


def same_dir(a, b):
    return abs(abs(dot(a, b)) - 1.0) < 1e-4


def box_of(quads):
    """Box (centre, axes, half extents) whose faces are exactly these quads, or None."""
    axes = []
    for quad in quads:
        qa = quad_axes(quad)
        if qa is None:
            return None
        for e in qa:
            if not any(same_dir(e, a) for a in axes):
                axes.append(e)
    if len(quads) == 1:
        axes.append(norm(cross(axes[0], axes[1])))
    if len(axes) != 3:
        return None
    for i in range(3):
        for j in range(i + 1, 3):
            if abs(dot(axes[i], axes[j])) > 1e-3:
                return None
    points = [c[0] for q in quads for c in q]
    lo = [min(dot(pt, a) for pt in points) for a in axes]
    hi = [max(dot(pt, a) for pt in points) for a in axes]
    if len(quads) > 1 and any(h - l < EPS for l, h in zip(lo, hi)):
        return None
    for quad in quads:
        # Every quad is a full face: on one side plane, spanning the other two extents.
        on_face = False
        for k in range(3):
            vals = [dot(c[0], axes[k]) for c in quad]
            for side in (lo[k], hi[k]):
                if all(abs(v - side) < 1e-3 for v in vals):
                    others = [m for m in range(3) if m != k]
                    if all(abs(min(dot(c[0], axes[m]) for c in quad) - lo[m]) < 1e-3
                           and abs(max(dot(c[0], axes[m]) for c in quad) - hi[m]) < 1e-3 for m in others):
                        on_face = True
        if not on_face:
            return None
    return axes, lo, hi


def rotations_for(axes):
    """Every renderer-space rotation (columns = local x, y, z along the box axes, det +1), closest to identity first."""
    import itertools
    # Axes are in Bedrock space; renderer space has x negated.
    rax = [(-a[0], a[1], a[2]) for a in axes]
    out = []
    for perm in itertools.permutations(range(3)):
        for signs in itertools.product((1, -1), repeat=3):
            cols = [mul(rax[perm[k]], signs[k]) for k in range(3)]
            if dot(cross(cols[0], cols[1]), cols[2]) > 0:
                out.append(cols)
    out.sort(key=lambda c: -(c[0][0] + c[1][1] + c[2][2]))
    return out


def euler_zyx(cols):
    """cols -> (rx, ry, rz) radians with M = Rz(rz) Ry(ry) Rx(rx) (AnimRenderer order)."""
    m = [[cols[c][r] for c in range(3)] for r in range(3)]
    ry = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(math.cos(ry)) > 1e-6:
        rz = math.atan2(m[1][0], m[0][0])
        rx = math.atan2(m[2][1], m[2][2])
    else:
        rz = 0.0
        rx = math.atan2(-m[1][2], m[1][1])
    return rx, ry, rz


def rot_matrix(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    rzm = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    rym = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rxm = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]

    def mm(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mm(mm(rzm, rym), rxm)


def apply3(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def parser_faces(origin, size):
    """Corner positions (Bedrock px) a, b, c, d of BakedGeoModel's quads per JSON face key (unrotated)."""
    ox, oy, oz = origin
    sx, sy, sz = size
    xmin, xmax, ymin, ymax, zmin, zmax = ox, ox + sx, oy, oy + sy, oz, oz + sz
    return {
        "north": [(xmax, ymax, zmin), (xmin, ymax, zmin), (xmin, ymin, zmin), (xmax, ymin, zmin)],
        "south": [(xmin, ymax, zmax), (xmax, ymax, zmax), (xmax, ymin, zmax), (xmin, ymin, zmax)],
        # BakedGeoModel's "west" quad (normal -x in renderer space) sits at Bedrock xmax and reads the JSON "east" key.
        "east": [(xmax, ymax, zmax), (xmax, ymax, zmin), (xmax, ymin, zmin), (xmax, ymin, zmax)],
        "west": [(xmin, ymax, zmin), (xmin, ymax, zmax), (xmin, ymin, zmax), (xmin, ymin, zmin)],
        "up": [(xmax, ymax, zmax), (xmin, ymax, zmax), (xmin, ymax, zmin), (xmax, ymax, zmin)],
        "down": [(xmax, ymin, zmin), (xmin, ymin, zmin), (xmin, ymin, zmax), (xmax, ymin, zmax)],
    }


def to_world(p, pivot, m):
    """Bedrock px local point -> Bedrock px world through the cube rotation (applied in renderer space)."""
    r = (-p[0], p[1], p[2])
    rp = (-pivot[0], pivot[1], pivot[2])
    w = add(rp, apply3(m, sub(r, rp)))
    return (-w[0], w[1], w[2])


def convert_cube(quads, tex_w, tex_h, where):
    box = box_of(quads)
    if box is None:
        return None
    axes, lo, hi = box
    for cols in rotations_for(axes):
        cube = cube_for(quads, axes, lo, hi, cols, tex_w, tex_h)
        if cube is not None:
            return cube
    sys.exit("%s: no cube orientation carries the OBJ UVs (a face UV is turned against its edges)" % where)


def cube_for(quads, axes, lo, hi, cols, tex_w, tex_h):
    rx, ry, rz = euler_zyx(cols)
    rotated = any(abs(a) > 1e-5 for a in (rx, ry, rz))
    if not rotated:
        rx = ry = rz = 0.0
    m = rot_matrix(rx, ry, rz)
    centre_axes = [(l + h) / 2 for l, h in zip(lo, hi)]
    centre = (0.0, 0.0, 0.0)
    for k in range(3):
        centre = add(centre, mul(axes[k], centre_axes[k]))
    # Local (unrotated) extents along renderer x, y, z = the box extents of the axis mapped to each column.
    half = []
    for k in range(3):
        col = cols[k]
        bed = (-col[0], col[1], col[2])
        i = max(range(3), key=lambda j: abs(dot(bed, axes[j])))
        half.append((hi[i] - lo[i]) / 2)
    origin = (centre[0] - half[0], centre[1] - half[1], centre[2] - half[2])
    size = (2 * half[0], 2 * half[1], 2 * half[2])
    pivot = centre
    faces = parser_faces(origin, size)
    uv = {}
    used = set()
    for name, corners in faces.items():
        plane = [k for k in range(3) if "xyz"[k] != axis_of(name)]
        if any(size[k] < EPS for k in plane):
            # A face of a zero-thickness cube that collapses to a line: never an OBJ quad.
            continue
        world = [to_world(c, pivot, m) for c in corners]
        match = None
        for qi, quad in enumerate(quads):
            if qi in used:
                continue
            found = []
            for w in world:
                hit = [c for c in quad if close(c[0], w, 2e-3)]
                if not hit:
                    break
                found.append(hit[0][1])
            if len(found) == 4:
                if size[["x", "y", "z"].index(axis_of(name))] < EPS:
                    # Zero thickness: both faces share corners; keep the one facing like the OBJ winding.
                    if not faces_same_way(quad, name, m):
                        continue
                match = (qi, found)
                break
        if match is None:
            continue
        qi, found = match
        used.add(qi)
        a, b, c, d = [(t[0] * tex_w, (1.0 - t[1]) * tex_h) for t in found]
        u0, u1, v0, v1 = b[0], a[0], a[1], c[1]
        if not (abs(c[0] - u0) < 1e-3 and abs(d[0] - u1) < 1e-3 and abs(b[1] - v0) < 1e-3 and abs(d[1] - v1) < 1e-3):
            return None
        uv[name] = {"uv": [rnd(u0), rnd(v0)], "uv_size": [rnd(u1 - u0), rnd(v1 - v0)]}
    if len(used) != len(quads):
        return None
    cube = {"origin": [rnd(v) for v in origin], "size": [rnd(v) for v in size], "uv": uv}
    if rotated:
        cube["pivot"] = [rnd(v) for v in pivot]
        cube["rotation"] = [rnd(-math.degrees(rx)), rnd(-math.degrees(ry)), rnd(math.degrees(rz))]
    return cube


def axis_of(face):
    return {"north": "z", "south": "z", "east": "x", "west": "x", "up": "y", "down": "y"}[face]


FACE_NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def faces_same_way(quad, name, m):
    p = [c[0] for c in quad]
    n = cross(sub(p[1], p[0]), sub(p[2], p[1]))
    fn = FACE_NORMALS[name]
    r = apply3(m, (-fn[0], fn[1], fn[2]))
    return dot(n, (-r[0], r[1], r[2])) > 0


def rnd(v):
    v = round(v, 4)
    return 0.0 if v == 0 else v


def group(faces, tex_w, tex_h, name):
    cubes = []
    i = 0
    while i < len(faces):
        for k in range(min(6, len(faces) - i), 0, -1):
            cube = convert_cube(faces[i:i + k], tex_w, tex_h, "%s faces %d-%d" % (name, i, i + k - 1))
            if cube is not None:
                cubes.append(cube)
                i += k
                break
        else:
            sys.exit("%s: face %d is not part of a box and not a rectangle (non-convertible quad)" % (name, i))
    return cubes


def verify(cubes, faces, tex_w, tex_h, name):
    """Rebuild BakedGeoModel's vertices (renderer space, UV after FLIP_U) and compare with every OBJ corner."""
    built = []
    for cube in cubes:
        rot = cube.get("rotation", [0, 0, 0])
        m = rot_matrix(math.radians(-rot[0]), math.radians(-rot[1]), math.radians(rot[2]))
        pivot = tuple(cube.get("pivot", [0, 0, 0]))
        for face, corners in parser_faces(cube["origin"], cube["size"]).items():
            if face not in cube["uv"]:
                continue
            f = cube["uv"][face]
            U0, V0 = f["uv"]
            U1, V1 = U0 + f["uv_size"][0], V0 + f["uv_size"][1]
            uvs = [(U1, V0), (U0, V0), (U0, V1), (U1, V1)]
            built.append(sorted((tuple(round(x, 2) for x in to_world(c, pivot, m)), (round(u, 2), round(v, 2))) for c, (u, v) in zip(corners, uvs)))
    want = [sorted((tuple(round(x, 2) for x in c[0]), (round(c[1][0] * tex_w, 2), round((1 - c[1][1]) * tex_h, 2))) for c in q) for q in faces]

    def key(q):
        return [(p, t) for p, t in q]
    left = [key(q) for q in want]
    for q in built:
        k = key(q)
        hit = next((i for i, w in enumerate(left) if all(close(a[0], b[0], 0.02) and close(a[1], b[1], 0.02) for a, b in zip(w, k))), None)
        if hit is None:
            sys.exit("%s: rebuilt face %s matches no OBJ face" % (name, k))
        left.pop(hit)
    if left:
        sys.exit("%s: %d OBJ faces not rebuilt" % (name, len(left)))


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("obj")
    ap.add_argument("out")
    ap.add_argument("--texture", nargs=2, type=int, required=True)
    ap.add_argument("--bones", help="comma list: output order and filter of OBJ objects")
    ap.add_argument("--pivots", choices=["vanilla", "none"], default="none")
    ap.add_argument("--armor-bones", action="store_true")
    ap.add_argument("--scale", type=float, default=1.0)
    ap.add_argument("--offset", nargs=3, type=float, default=[0.0, 0.0, 0.0])
    ap.add_argument("--identifier", default="geometry.converted")
    ap.add_argument("--single-bone", help="put every object's cubes under one bone of this name")
    ap.add_argument("--pivot", nargs=3, type=float, help="pivot of the single bone")
    ap.add_argument("--skip-material-suffix", help="drop faces of materials ending with this (emissive overlay copies)")
    ap.add_argument("--uv-top-origin", action="store_true", help="OBJ vt v is measured from the top of the image")
    ap.add_argument("--meta-bone", nargs=4, metavar=("NAME", "X", "Y", "Z"), help="empty child bone carrying a vector as its pivot")
    args = ap.parse_args(argv)
    tex_w, tex_h = args.texture
    objects = load_obj(args.obj, args.scale, args.offset, args.skip_material_suffix, args.uv_top_origin)
    if args.bones:
        order = args.bones.split(",")
        by = {o["name"]: o for o in objects}
        missing = [n for n in order if n not in by]
        if missing:
            sys.exit("objects not in %s: %s" % (args.obj, missing))
        objects = [by[n] for n in order]
    bones = []
    total = 0
    summary = []
    for obj in objects:
        cubes = group(obj["faces"], tex_w, tex_h, obj["name"])
        verify(cubes, obj["faces"], tex_w, tex_h, obj["name"])
        n = sum(len(c["uv"]) for c in cubes)
        total += n
        summary.append("%s: %d cubes, %d faces" % (obj["name"], len(cubes), n))
        if args.single_bone:
            if not bones:
                bones.append({"name": args.single_bone, "pivot": list(args.pivot or (0, 0, 0)), "cubes": []})
            bones[0]["cubes"].extend(cubes)
            continue
        name = ARMOR_NAMES.get(obj["name"], obj["name"]) if args.armor_bones else obj["name"]
        pivot = VANILLA_PIVOTS.get(obj["name"], (0, 0, 0)) if args.pivots == "vanilla" else (0, 0, 0)
        bones.append({"name": name, "pivot": list(pivot), "cubes": cubes})
    if args.meta_bone:
        name, x, y, z = args.meta_bone
        bones.append({"name": name, "parent": bones[0]["name"], "pivot": [rnd(float(x)), rnd(float(y)), rnd(float(z))]})
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": args.identifier, "texture_width": tex_w, "texture_height": tex_h,
                        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=1)
        f.write("\n")
    print("bones: %d" % len(bones))
    for line in summary:
        print("  " + line)
    print("faces: %d" % total)


if __name__ == "__main__":
    main(sys.argv[1:])
