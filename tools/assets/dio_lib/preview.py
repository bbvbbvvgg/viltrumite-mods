"""Software preview of geo models + clips, mirroring the Java runtime math.

Mirrors client/anim/geo/BakedGeoModel (x negation, FLIP_U, SWAP_EAST_WEST),
AnimationParser (rotation sign), AnimationController.seek and AnimRenderer
(translate pos, pivot, Z*Y*X, scale). Used for front/side/back frame sheets.
"""
import json
import math

import numpy as np
from PIL import Image


def _rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], np.float64)


def _ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], np.float64)


def _rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], np.float64)


def _t(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def _s(x, y, z):
    return np.diag([x, y, z, 1.0])


class PBone:
    def __init__(self, j, parent):
        p = j.get("pivot", [0, 0, 0])
        r = j.get("rotation", [0, 0, 0])
        self.name = j["name"]
        self.parent = parent
        self.children = []
        self.pivot = (-p[0], p[1], p[2])
        self.init = (math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2]))
        self.cubes = []
        self.reset()

    def reset(self):
        self.rot = list(self.init)
        self.pos = [0.0, 0.0, 0.0]
        self.scale = [1.0, 1.0, 1.0]
        self.hidden = False


class PModel:
    def __init__(self, geo_path, tex_path):
        root = json.load(open(geo_path))["minecraft:geometry"][0]
        d = root["description"]
        self.tw, self.th = d["texture_width"], d["texture_height"]
        self.tex = np.asarray(Image.open(tex_path).convert("RGBA"), np.float32) / 255.0
        self.bones = {}
        self.top = []
        for jb in root["bones"]:
            parent = self.bones.get(jb.get("parent"))
            b = PBone(jb, parent)
            self.bones[b.name] = b
            (parent.children if parent else self.top).append(b)
            for jc in jb.get("cubes", []):
                b.cubes.append(self._cube(jc))

    def _cube(self, j):
        o, s = j["origin"], j["size"]
        inf = j.get("inflate", 0)
        x1 = (-(o[0] + s[0]) - inf) / 16
        x2 = (-o[0] + inf) / 16
        y1 = (o[1] - inf) / 16
        y2 = (o[1] + s[1] + inf) / 16
        z1 = (o[2] - inf) / 16
        z2 = (o[2] + s[2] + inf) / 16
        uv = j["uv"]

        def f(name):
            e = uv.get(name)
            return None if e is None else [e["uv"][0], e["uv"][1], e["uv"][0] + e["uv_size"][0], e["uv"][1] + e["uv_size"][1]]

        north, south, up, down = f("north"), f("south"), f("up"), f("down")
        east, west = f("west"), f("east")  # SWAP_EAST_WEST
        quads = []

        def add(r, n, a, b, c, dd):
            if r is None:
                return
            u0, v0, u1, v1 = r[0] / self.tw, r[1] / self.th, r[2] / self.tw, r[3] / self.th
            u0, u1 = u1, u0  # FLIP_U
            quads.append((np.array([a, b, c, dd], np.float64), np.array([[u0, v0], [u1, v0], [u1, v1], [u0, v1]]), np.array(n, np.float64)))

        add(north, (0, 0, -1), (x1, y2, z1), (x2, y2, z1), (x2, y1, z1), (x1, y1, z1))
        add(south, (0, 0, 1), (x2, y2, z2), (x1, y2, z2), (x1, y1, z2), (x2, y1, z2))
        add(west, (-1, 0, 0), (x1, y2, z2), (x1, y2, z1), (x1, y1, z1), (x1, y1, z2))
        add(east, (1, 0, 0), (x2, y2, z1), (x2, y2, z2), (x2, y1, z2), (x2, y1, z1))
        add(up, (0, 1, 0), (x1, y2, z2), (x2, y2, z2), (x2, y2, z1), (x1, y2, z1))
        add(down, (0, -1, 0), (x1, y1, z1), (x2, y1, z1), (x2, y1, z2), (x1, y1, z2))
        piv = j.get("pivot", [0, 0, 0])
        rot = j.get("rotation", [0, 0, 0])
        return quads, (-piv[0] / 16, piv[1] / 16, piv[2] / 16), (math.radians(-rot[0]), math.radians(-rot[1]), math.radians(rot[2]))

    def reset(self):
        for b in self.bones.values():
            b.reset()

    def pose(self, clip, t):
        """AnimationController.seek: reset, then clip channels at t seconds."""
        self.reset()
        if clip is None:
            return
        for name, ch in clip.get("bones", {}).items():
            b = self.bones.get(name)
            if b is None:
                continue
            if "rotation" in ch:
                r = sample(ch["rotation"], t, 0)
                b.rot = [b.init[0] + math.radians(-r[0]), b.init[1] + math.radians(-r[1]), b.init[2] + math.radians(r[2])]
            if "position" in ch:
                b.pos = list(sample(ch["position"], t, 0))
            if "scale" in ch:
                b.scale = list(sample(ch["scale"], t, 1))

    def triangles(self, root_matrix, only=None):
        out = []

        def walk(b, m, visible):
            if b.pos != [0, 0, 0]:
                m = m @ _t(-b.pos[0] / 16, b.pos[1] / 16, b.pos[2] / 16)
            px, py, pz = (v / 16 for v in b.pivot)
            m = m @ _t(px, py, pz) @ _rz(b.rot[2]) @ _ry(b.rot[1]) @ _rx(b.rot[0]) @ _s(*b.scale) @ _t(-px, -py, -pz)
            vis = visible or (only is not None and b.name in only)
            if not b.hidden and (only is None or vis):
                for quads, cp, cr in b.cubes:
                    cm = m
                    if any(cr):
                        cm = m @ _t(*cp) @ _rz(cr[2]) @ _ry(cr[1]) @ _rx(cr[0]) @ _t(-cp[0], -cp[1], -cp[2])
                    for verts, uvs, n in quads:
                        vw = (cm @ np.c_[verts, np.ones(4)].T).T[:, :3]
                        nw = cm[:3, :3] @ n
                        nw /= np.linalg.norm(nw) + 1e-9
                        out.append((vw, uvs, nw))
            for c in b.children:
                walk(c, m, vis)

        for b in self.top:
            walk(b, root_matrix, False)
        return out


def sample(channel, t, default):
    if isinstance(channel, list):
        return channel
    keys = sorted((float(k), v) for k, v in channel.items())

    def val(v):
        if isinstance(v, dict):
            v = v.get("post", v.get("vector"))
        return v

    if t <= keys[0][0]:
        return val(keys[0][1])
    if t >= keys[-1][0]:
        return val(keys[-1][1])
    for i in range(len(keys) - 1):
        t0, a = keys[i]
        t1, b = keys[i + 1]
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            a, b = val(a), val(b)
            return [a[k] + (b[k] - a[k]) * f for k in range(3)]
    return [default] * 3


class Camera:
    def __init__(self, yaw_deg, pitch_deg, target, dist, w=360, h=480, fov=32):
        self.w, self.h = w, h
        yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
        fwd = np.array([-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
        self.eye = np.array(target) - fwd * dist
        self.fwd = fwd
        up = np.array([0, 1.0, 0])
        self.right = np.cross(fwd, up)
        self.right /= np.linalg.norm(self.right)
        self.up = np.cross(self.right, fwd)
        self.f = (h / 2) / math.tan(math.radians(fov) / 2)

    def project(self, p):
        d = p - self.eye
        x, y, z = d @ self.right, d @ self.up, d @ self.fwd
        return np.stack([self.w / 2 + x * self.f / z, self.h / 2 - y * self.f / z, z], -1)


LIGHT0 = np.array([0.2, 1.0, -0.7])
LIGHT1 = np.array([-0.2, 1.0, 0.7])
LIGHT0 /= np.linalg.norm(LIGHT0)
LIGHT1 /= np.linalg.norm(LIGHT1)


def render(layers, cam, bg=(46, 50, 60)):
    """layers: list of (triangles, texture, alpha). Returns PIL image."""
    W, H = cam.w, cam.h
    color = np.zeros((H, W, 3), np.float32)
    color[:] = np.array(bg, np.float32) / 255
    # simple floor grid shading
    zbuf = np.full((H, W), np.inf)
    for tris, tex, alpha in layers:
        th, tw = tex.shape[:2]
        for verts, uvs, n in tris:
            pv = cam.project(verts)
            if np.any(pv[:, 2] <= 0.05):
                continue
            light = 0.4 + 0.6 * min(1.0, max(0.0, n @ LIGHT0) * 0.6 + max(0.0, n @ LIGHT1) * 0.6)
            for tri in ((0, 1, 2), (0, 2, 3)):
                P = pv[list(tri)]
                U = uvs[list(tri)]
                x0, x1 = int(max(0, np.floor(P[:, 0].min()))), int(min(W - 1, np.ceil(P[:, 0].max())))
                y0, y1 = int(max(0, np.floor(P[:, 1].min()))), int(min(H - 1, np.ceil(P[:, 1].max())))
                if x1 < x0 or y1 < y0:
                    continue
                (ax, ay, _), (bx, by, _), (cx, cy, _) = P
                den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
                if abs(den) < 1e-9:
                    continue
                ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
                px, py = xs + 0.5, ys + 0.5
                l0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den
                l1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den
                l2 = 1 - l0 - l1
                inside = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
                if not inside.any():
                    continue
                iz = l0 / P[0, 2] + l1 / P[1, 2] + l2 / P[2, 2]
                z = 1 / iz
                u = (l0 * U[0, 0] / P[0, 2] + l1 * U[1, 0] / P[1, 2] + l2 * U[2, 0] / P[2, 2]) * z
                v = (l0 * U[0, 1] / P[0, 2] + l1 * U[1, 1] / P[1, 2] + l2 * U[2, 1] / P[2, 2]) * z
                ti = np.clip((v * th).astype(int), 0, th - 1)
                tj = np.clip((u * tw).astype(int), 0, tw - 1)
                texel = tex[ti, tj]
                zb = zbuf[y0:y1 + 1, x0:x1 + 1]
                m = inside & (texel[..., 3] > 0.1) & (z < zb)
                if not m.any():
                    continue
                cb = color[y0:y1 + 1, x0:x1 + 1]
                rgb = texel[..., :3] * light
                if alpha >= 1:
                    cb[m] = rgb[m]
                    zb[m] = z[m]
                else:
                    cb[m] = cb[m] * (1 - alpha) + rgb[m] * alpha
    return Image.fromarray((np.clip(color, 0, 1) * 255).astype(np.uint8))
