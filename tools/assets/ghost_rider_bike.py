#!/usr/bin/env python3
"""Ghost Rider's Hell Cycle: geo (~230 cubes), atlas and glow. Own work, CC0.

A long hellfire chopper: raked springer-style fork with a skull headlamp, low drag bars, a teardrop tank with
painted flames, a V-twin with glowing fin gaps, two drag pipes on the right, a fat rear tyre under a spiked
fender, a low leather seat, a bone-ringed double-cradle frame and a kickstand on the left.

Coordinates (JSON, px): origin = entity position on the ground; front = -Z; +X = the bike's LEFT side (the loader
mirrors x). Axles at y 6, z -14.4 (front) and +14.4 (rear) = HellCycleRules.WHEELBASE 1.8 blocks; wheel radius 6 px
= HellCycleRules.WHEEL_RADIUS. Seat top 13.2 px, rider's hips at z +6.4 (HellCycleRules.SEAT_BACK 0.40).

Bones animated by HellCycleRenderer: fork_steer (Y, about the raked steering axis), front_wheel / rear_wheel (X,
spin), kickstand (X/Z, down when parked). fork_rake carries the static 32 degree rake, bars cancels it so the bars
and the headlamp are authored in model coordinates.
Renderer anchors (px, JSON): exhaust tips (-4.0, 7.4, 18.0) and (-4.4, 10.4, 17.6), pointing +Z;
headlamp eyes (+-1.0, 17.9, -12.3); grips (+-8.4, 23.6, -3.9); pegs (+-4.6, 8.4, -3.2).

Run: PYTHONHASHSEED=0 python3 tools/assets/ghost_rider_bike.py [--preview]
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from clean_geo import CleanModel, write_model  # noqa: E402
from pixel_shade import Mat  # noqa: E402

GEO = 'geo/ghost_rider/hell_cycle.geo.json'
TEX = 'textures/entity/ghost_rider/hell_cycle.png'
GLOW = 'textures/entity/ghost_rider/hell_cycle_glow.png'

AXLE_Y = 6.0
AXLE_Z = 14.4
WHEEL_R = 6.0
RAKE = 32.0
HEAD = (0.0, 19.2, -6.2)        # steering-head pivot (model coordinates)
FORK_LEN = 15.5                 # pivot -> front axle along the raked axis

BLACK = '#1d1d22'
GUN = '#2c2d34'
CHROME = '#b9bec8'
DCHROME = '#7d828d'
BONE = '#d9d0b8'
LEATHER = '#2e1f17'
RUBBER = '#18171b'
TANK = '#121216'
EMBER = '#ff7a1a'
EMBER_HOT = '#ffc04a'
EMBER_RED = '#ff3a12'


# ------------------------------------------------------------------------------------------------ painters
def chrome_fn(ctx):
    """Reflective bands: a bright sky line near the top, a dark horizon band, a warm floor bounce."""
    if ctx.side and ctx.h >= 3:
        rows = np.arange(ctx.h)[:, None] / max(1, ctx.h - 1)
        ctx.light(np.broadcast_to(rows < 0.22, (ctx.h, ctx.w)), 0.20)
        ctx.light(np.broadcast_to((rows > 0.45) & (rows < 0.62), (ctx.h, ctx.w)), -0.22)
        ctx.light(np.broadcast_to(rows > 0.85, (ctx.h, ctx.w)), 0.06)
    elif ctx.name == 'up':
        ctx.light(ctx.all(), 0.12)


def black_fn(ctx):
    if ctx.side and ctx.h >= 3:
        ctx.light((slice(0, 1), slice(None)), 0.10)


def ember_metal_fn(ctx):
    """Black frame metal with thin hellfire veins glowing through cracks (model-space pattern)."""
    black_fn(ctx)
    x, y, z = ctx.x, ctx.y, ctx.z
    vein = np.abs(np.sin(z * 0.9 + y * 0.55 + np.sin(y * 1.7 + x) * 1.3)) < 0.07
    vein &= (np.sin(z * 0.37 + y * 0.21) > 0.35)
    ctx.put(vein, '#5a1806', 0.9)
    ctx.emit(vein, EMBER, 210)


def bone_fn(ctx):
    if ctx.side and ctx.h >= 3:
        ctx.light((slice(0, 1), slice(None)), 0.08)
        ctx.light((slice(-1, None), slice(None)), -0.10)


def leather_fn(ctx):
    rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
    if ctx.name == 'up':
        # diamond quilting with dark tucks and stitched edges
        d = ((rr + cc) % 6 == 0) | ((rr - cc) % 6 == 0)
        ctx.light(d, -0.18)
        edge = (rr == 1) | (rr == ctx.h - 2) | (cc == 1) | (cc == ctx.w - 2)
        ctx.put(edge & ((rr + cc) % 2 == 0), '#6b4a33', 0.55)
    elif ctx.side and ctx.h >= 3:
        ctx.put((rr == 1) & (cc % 2 == 0), '#6b4a33', 0.5)
        ctx.light((slice(-1, None), slice(None)), -0.12)


def tire_fn(ctx):
    rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
    if ctx.name == 'down':
        # chevron tread across the width (u), repeating along the segment (v)
        u = cc - ctx.w / 2.0
        chev = ((rr * 2 + np.abs(u)).astype(int) % 5) < 2
        ctx.light(chev, -0.25)
        ctx.light((slice(None), slice(0, 1)), 0.06)
        ctx.light((slice(None), slice(-1, None)), 0.06)
    elif ctx.name in ('east', 'west'):
        ctx.light((slice(None), slice(None)), 0.0)
        ctx.put(rr == ctx.h - 2, '#3a1a12', 0.6)       # a scorched ring near the tread


def tank_fn(ctx):
    """Gloss black tank with hot-rod flames licking back from the front on both sides."""
    x, y, z = ctx.x, ctx.y, ctx.z
    if ctx.name in ('east', 'west', 'up'):
        zz = z + 6.6                                    # 0 at the tank's nose, growing backwards
        yy = (y - 17.0) if ctx.name != 'up' else (np.abs(x) - 1.0) * 1.4
        tongue = 1.6 + 0.9 * np.sin(yy * 2.4 + 0.6) + 0.5 * np.sin(yy * 5.1 + 1.0)
        length = 3.2 + 2.6 * (0.5 + 0.5 * np.cos(yy * 1.9)) + tongue
        f = zz < length
        core = zz < length - 1.4
        hot = zz < length - 2.8
        ctx.put(f, '#c62a0c')
        ctx.put(core, '#ff7a14')
        ctx.put(hot, '#ffd25a')
        ctx.flat |= hot
        ctx.emit(f & ~core, '#8a1c06', 90)
        ctx.emit(core & ~hot, '#ff6a10', 120)
        ctx.emit(hot, '#ffb030', 150)
    if ctx.side and ctx.h >= 3:
        ctx.light((slice(0, 1), slice(None)), 0.16)


def engine_fn(ctx):
    """Cylinder fins: every other texel row is a gap glowing with hellfire."""
    rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
    if ctx.side:
        gap = (rr % 2 == 1)
        ctx.put(gap, '#3a1206', 0.9)
        ctx.emit(gap, EMBER, 170)
        ctx.light(rr % 2 == 0, 0.08)


def head_fn(ctx):
    if ctx.side and ctx.h >= 3:
        ctx.light((slice(0, 1), slice(None)), 0.16)
        ctx.light((slice(-1, None), slice(None)), -0.14)


def pipe_fn(ctx):
    """Heat-blued pipes: chrome bands darkening to bronze and violet, black at the rear."""
    chrome_fn(ctx)
    z = ctx.z
    t = np.clip((z + 4.0) / 22.0, 0, 1)
    ctx.put(t > 0.15, '#6a5a4a', 0.35)
    ctx.put(t > 0.45, '#4a3a5a', 0.35)
    ctx.put(t > 0.75, '#1e1a22', 0.5)


def tip_fn(ctx):
    pipe_fn(ctx)
    if ctx.name == 'south':
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        cy, cx = (ctx.h - 1) / 2.0, (ctx.w - 1) / 2.0
        hole = (rr - cy) ** 2 + (cc - cx) ** 2 <= max(1.0, (min(ctx.h, ctx.w) / 2.0 - 0.8) ** 2)
        ctx.put(hole, '#2a0802')
        ctx.emit(hole, EMBER_HOT, 255)
        ctx.flat |= hole


def skull_fn(ctx):
    """The headlamp skull: eye sockets (glowing), nose hole, cheek shadows (front = north face)."""
    rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
    w, h = ctx.w, ctx.h
    if ctx.name == 'north':
        u = (cc + 0.5) / w - 0.5
        v = (rr + 0.5) / h
        eye = ((np.abs(u) - 0.21) ** 2 / 0.018 + (v - 0.45) ** 2 / 0.022) < 1.0
        ring = ((np.abs(u) - 0.21) ** 2 / 0.032 + (v - 0.45) ** 2 / 0.036) < 1.0
        nose = (np.abs(u) < 0.06 + (v - 0.66) * 0.5) & (v > 0.66) & (v < 0.86)
        brow = (v < 0.27) & (np.abs(u) < 0.42)
        ctx.light(ring & ~eye, -0.22)
        ctx.light(brow, 0.08)
        ctx.put(eye, '#3a0c02')
        ctx.emit(eye, EMBER_HOT, 255)
        ctx.flat |= eye
        ctx.put(nose, '#24140c')
        ctx.light((v > 0.86), -0.12)
    elif ctx.side:
        ctx.light((slice(-1, None), slice(None)), -0.16)
        ctx.light(((rr > h * 0.55) & (rr < h * 0.8)), -0.1)
    if ctx.name == 'up':
        ctx.light(ctx.all(), 0.06)


def jaw_fn(ctx):
    rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
    if ctx.name == 'north':
        teeth = (rr < ctx.h * 0.55) & (cc % 2 == 0)
        ctx.light(teeth, 0.10)
        ctx.light((rr < ctx.h * 0.55) & (cc % 2 == 1), -0.28)
        ctx.light(rr >= ctx.h * 0.55, -0.06)


def rotor_fn(ctx):
    """Brake disc, red hot: drilled dark holes and a glowing outer ring."""
    rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
    if ctx.name in ('east', 'west'):
        u = (cc + 0.5) / ctx.w - 0.5
        v = (rr + 0.5) / ctx.h - 0.5
        r = np.sqrt(u * u + v * v) * 2
        hot = r > 0.8
        holes = (r > 0.45) & (r < 0.7) & (((rr + cc) % 3) == 0)
        ctx.put(hot, '#7a1a08', 0.8)
        ctx.emit(hot, EMBER_RED, 150)
        ctx.put(holes, '#0c0c0e')


def tail_fn(ctx):
    ctx.put(ctx.all(), '#5a0808')
    if ctx.name in ('south', 'up'):
        ctx.emit(ctx.all(), '#ff2a10', 255)
        ctx.flat |= ctx.all()


def grip_fn(ctx):
    rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
    if ctx.side:
        ctx.light(cc % 2 == 0, -0.12)


MATS = {
    'black': Mat(BLACK, 'metal', black_fn, ao=0.45, quant=0.04),
    'ember': Mat('#1f1b1d', 'metal', ember_metal_fn, ao=0.45, quant=0.04),
    'gun': Mat(GUN, 'metal', black_fn, ao=0.5, quant=0.04),
    'chrome': Mat(CHROME, 'metal', chrome_fn, ao=0.25, quant=0.04, micro=0.4),
    'dchrome': Mat(DCHROME, 'metal', chrome_fn, ao=0.3, quant=0.04, micro=0.4),
    'bone': Mat(BONE, 'paint', bone_fn, ao=0.4, quant=0.05, micro=0.3),
    'leather': Mat(LEATHER, 'leather', leather_fn, ao=0.45, quant=0.04),
    'rubber': Mat(RUBBER, 'rubber', tire_fn, ao=0.2, quant=0.04, edges=False),
    'tank': Mat(TANK, 'paint', tank_fn, ao=0.35, quant=0.04),
    'fins': Mat(GUN, 'metal', engine_fn, ao=0.2, quant=0.04, edges=False, micro=0.0),
    'head': Mat('#3a3b44', 'metal', head_fn, ao=0.3, quant=0.04),
    'pipe': Mat(DCHROME, 'metal', pipe_fn, ao=0.2, quant=0.04, micro=0.3),
    'tip': Mat(DCHROME, 'metal', tip_fn, ao=0.2, quant=0.04, micro=0.3),
    'skull': Mat(BONE, 'paint', skull_fn, ao=0.35, quant=0.05, micro=0.3),
    'jaw': Mat(BONE, 'paint', jaw_fn, ao=0.35, quant=0.05, micro=0.3),
    'rotor': Mat('#3c3d44', 'metal', rotor_fn, ao=0.1, quant=0.04, edges=False, micro=0.0),
    'tail': Mat('#5a0808', 'flat', tail_fn, ao=0.0, edges=False),
    'grip': Mat('#151418', 'rubber', grip_fn, ao=0.2, quant=0.04),
}


# ------------------------------------------------------------------------------------------------ geometry helpers
class Bike:
    def __init__(self):
        self.m = CleanModel('hell_cycle', density=2, width=256, seed=666, light=(30.0, 0.0, 0.10))

    def box(self, bone, a, b, mat, **kw):
        x0, y0, z0 = a
        x1, y1, z1 = b
        return self.m.cube(bone, [min(x0, x1), min(y0, y1), min(z0, z1)], [abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)],
                           MATS[mat], **kw)

    def cbox(self, bone, c, size, mat, rot=None, pivot=None):
        """Box centred on c, optionally rotated about c (or pivot)."""
        o = [c[0] - size[0] / 2.0, c[1] - size[1] / 2.0, c[2] - size[2] / 2.0]
        kw = {}
        if rot:
            kw['rotation'] = list(rot)
            kw['pivot'] = list(pivot or c)
        return self.m.cube(bone, o, list(size), MATS[mat], **kw)

    def tube(self, bone, p0, p1, t, mat, ty=None, ext=0.0):
        """Square tube (t x ty) from p0 to p1 (extended by ext at both ends); rotation (asin dy, atan2(dx, dz), 0)."""
        p0, p1 = np.array(p0, float), np.array(p1, float)
        if p1[2] < p0[2]:
            p0, p1 = p1, p0
        d = p1 - p0
        L = float(np.linalg.norm(d))
        n = d / L
        rx = math.degrees(math.asin(max(-1.0, min(1.0, n[1]))))
        ry = math.degrees(math.atan2(n[0], n[2]))
        ty = ty or t
        o = [p0[0] - t / 2.0, p0[1] - ty / 2.0, p0[2] - ext]
        rot = [round(rx, 3), round(ry, 3), 0]
        if abs(rot[0]) < 1e-6 and abs(rot[1]) < 1e-6:
            return self.m.cube(bone, o, [t, ty, L + 2 * ext], MATS[mat])
        return self.m.cube(bone, o, [t, ty, L + 2 * ext], MATS[mat], rotation=rot, pivot=list(p0))

    def chain_of_tubes(self, bone, pts, t, mat, ext=0.3):
        for a, b in zip(pts, pts[1:]):
            self.tube(bone, a, b, t, mat, ext=ext)

    def ring(self, bone, axle, r_in, r_out, width, n, mat, phase=0.0, inset=0.08):
        """n segments around the axle (YZ plane); odd segments are a hair narrower (no coplanar sidewalls)."""
        cy, cz = axle[1], axle[2]
        chord = 2.0 * r_out * math.sin(math.pi / n) + 0.25
        for k in range(n):
            w = width - (inset if k % 2 else 0.0)
            o = [axle[0] - w / 2.0, cy - r_out, cz - chord / 2.0]
            ang = phase + k * 360.0 / n
            self.m.cube(bone, o, [w, r_out - r_in, chord], MATS[mat], rotation=[round(ang, 3), 0, 0], pivot=[axle[0], cy, cz])

    def disc(self, bone, c, r, width, mat, steps=3, dx=0.04):
        """Round plate in the YZ plane (12-gon from rotated squares, each a hair thicker: no coplanar faces)."""
        s = 2.0 * r * math.cos(math.pi / (4 * steps))
        for k in range(steps):
            w = width + k * dx
            o = [c[0] - w / 2.0, c[1] - s / 2.0, c[2] - s / 2.0]
            ang = k * 90.0 / steps
            if ang:
                self.m.cube(bone, o, [w, s, s], MATS[mat], rotation=[ang, 0, 0], pivot=list(c))
            else:
                self.m.cube(bone, o, [w, s, s], MATS[mat])

    def spike(self, bone, base, direction, length, width, mat='bone'):
        """A tapering spike of three stacked boxes along direction."""
        base = np.array(base, float)
        d = np.array(direction, float)
        d /= np.linalg.norm(d)
        steps = 3
        for k in range(steps):
            f = k / steps
            w = width * (1.0 - 0.3 * k)
            p0 = base + d * (length * f)
            p1 = base + d * (length * (f + 1.0 / steps) + 0.05)
            self.tube(bone, p0, p1, w, mat)


def raked(p_unraked):
    """Model coordinates of a point given in the un-raked fork frame (steering axis vertical through HEAD)."""
    th = math.radians(RAKE)
    x, y, z = p_unraked
    dy, dz = y - HEAD[1], z - HEAD[2]
    return (x, HEAD[1] + dy * math.cos(th) - dz * math.sin(th), HEAD[2] + dy * math.sin(th) + dz * math.cos(th))


def build():
    b = Bike()
    m = b.m
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, 0, 0])

    # ---- rear wheel ------------------------------------------------------------------------------------------
    rear = (0.0, AXLE_Y, AXLE_Z)
    m.bone('rear_wheel', 'body', list(rear))
    b.ring('rear_wheel', rear, 4.45, WHEEL_R, 4.6, 16, 'rubber')
    b.ring('rear_wheel', rear, 3.55, 4.35, 3.2, 16, 'chrome', phase=11.25)
    for k in range(4):
        b.cbox('rear_wheel', rear, (0.6 + 0.05 * k, 0.6, 7.4), 'dchrome', rot=(k * 45.0 + 5.0, 0, 0))
    b.cbox('rear_wheel', rear, (3.8, 2.4, 2.4), 'chrome')
    b.cbox('rear_wheel', rear, (3.6, 2.4, 2.4), 'chrome', rot=(45, 0, 0))
    b.cbox('rear_wheel', (0.0, AXLE_Y, AXLE_Z), (1.2, 1.2, 1.2), 'gun', rot=None)

    # ---- frame -----------------------------------------------------------------------------------------------
    neck_bot = raked((0.0, 18.5, HEAD[2]))
    neck_top = raked((0.0, 22.1, HEAD[2]))
    # backbone: neck top, through the tank tunnel, down behind it to the seat junction
    b.tube('body', (0.0, neck_top[1] - 0.4, neck_top[2] + 0.2), (0.0, 16.2, 2.0), 1.6, 'ember', ext=0.4)
    b.tube('body', (0.0, 16.2, 2.0), (0.0, 11.2, 4.6), 1.6, 'ember', ext=0.4)
    # double down tubes around the front cylinder, bottom rails, rear rails and seat stays
    for s in (-1, 1):
        x_dn = s * 2.5
        b.chain_of_tubes('body', [(s * 0.9, neck_bot[1] + 0.2, neck_bot[2] - 0.3), (x_dn, 14.0, -7.2),
                                  (x_dn, 3.4, -7.4), (s * 1.9, 3.2, 0.8)], 1.3, 'ember', ext=0.35)
        b.tube('body', (s * 1.9, 3.2, 0.8), (s * 3.0, 6.0, AXLE_Z), 1.2, 'ember', ext=0.3)
        b.tube('body', (s * 1.6, 11.0, 4.6), (s * 3.0, 6.6, AXLE_Z), 1.1, 'ember', ext=0.3)
        b.tube('body', (s * 1.6, 11.0, 4.6), (s * 2.5, 10.6, 11.6), 1.0, 'ember', ext=0.3)   # seat rails
        b.box('body', (s * 2.5, 5.0, AXLE_Z - 1.1), (s * 3.6, 7.2, AXLE_Z + 1.1), 'black')     # axle plates
        b.cbox('body', (s * 3.85, AXLE_Y, AXLE_Z), (0.5, 1.3, 1.3), 'chrome')                 # axle nuts
        # bone vertebrae on the down tubes
        for t in (0.25, 0.45, 0.65, 0.85):
            p = np.array((x_dn, 14.0, -7.2)) * (1 - t) + np.array((x_dn, 3.4, -7.4)) * t
            b.cbox('body', tuple(p), (1.9, 0.7, 1.9), 'bone')
    for t in (0.3, 0.75):                                                     # vertebrae on the exposed backbone
        p0 = np.array((0.0, 16.2, 2.0))
        p1 = np.array((0.0, 11.2, 4.6))
        p = p0 * (1 - t) + p1 * t
        d = p1 - p0
        rx = math.degrees(math.asin(d[1] / np.linalg.norm(d)))
        b.cbox('body', tuple(p), (2.2, 2.2, 0.7), 'bone', rot=(rx + 90.0, 0, 0))
    b.box('body', (-1.3, 3.0, -7.9), (1.3, 3.9, 1.2), 'black')             # skid plate between the rails

    # ---- engine: crankcase, primary, cam cover, transmission, two cylinders ------------------------------------
    b.box('body', (-2.2, 3.5, -5.9), (2.2, 8.45, 0.8), 'gun')
    b.box('body', (-1.9, 2.9, -5.2), (1.9, 3.6, 0.2), 'gun')
    b.disc('body', (2.75, 6.0, -2.6), 2.5, 1.1, 'chrome')                    # primary cover (left)
    b.cbox('body', (-2.6, 7.4, -3.0), (0.9, 2.4, 3.2), 'chrome', rot=(-20, 0, 0))   # cam cover (right)
    b.box('body', (-1.9, 4.0, 0.8), (1.9, 9.0, 4.4), 'gun')                 # transmission
    b.cbox('body', (2.2, 7.6, 2.6), (0.6, 2.2, 2.6), 'chrome')               # transmission side cover (left)
    for name, base, lean, cw in (('front', (0.0, 8.4, -3.7), 22.5, 2.8), ('rear', (0.0, 8.4, -0.3), -22.5, 2.74)):
        th = math.radians(lean)
        axis = np.array((0.0, math.cos(th), -math.sin(th)))
        base = np.array(base)
        rx = -lean                     # JSON rx tilting +y towards -z for a forward lean
        b.cbox('body', tuple(base + axis * 2.4), (cw, 4.8, cw), 'fins', rot=(rx, 0, 0))
        for k in range(4):
            b.cbox('body', tuple(base + axis * (0.9 + k * 1.15)), (3.7 + 0.04 * k, 0.4, 3.7 + 0.04 * k), 'gun', rot=(rx, 0, 0))
        b.cbox('body', tuple(base + axis * 5.5), (3.3, 1.4, 3.3), 'head', rot=(rx, 0, 0))
        b.cbox('body', tuple(base + axis * 6.5), (2.5, 0.8, 3.0), 'chrome', rot=(rx, 0, 0))
    b.disc('body', (-2.75, 10.7, -1.9), 1.9, 0.8, 'chrome')                  # air cleaner (right)
    b.disc('body', (-3.2, 10.7, -1.9), 1.1, 0.3, 'skull')                    # its skull badge

    # ---- exhaust: two drag pipes on the right -------------------------------------------------------------------
    b.chain_of_tubes('body', [(-1.2, 13.4, -6.7), (-3.4, 11.2, -8.2), (-3.9, 7.4, -7.6), (-4.0, 6.9, -3.0),
                              (-4.0, 7.4, 16.0)], 1.2, 'pipe', ext=0.35)
    b.chain_of_tubes('body', [(-1.5, 13.6, 2.7), (-3.4, 11.6, 3.6), (-4.4, 10.4, 6.5), (-4.4, 10.4, 15.6)],
                     1.2, 'pipe', ext=0.35)
    b.box('body', (-4.85, 6.55, 16.0), (-3.15, 8.25, 18.0), 'tip')
    b.box('body', (-5.25, 9.55, 15.6), (-3.55, 11.25, 17.6), 'tip')
    b.box('body', (-4.75, 9.3, 7.0), (-3.6, 11.5, 12.0), 'chrome')            # heat shield on the upper pipe

    # ---- tank, seat, oil tank, pegs ------------------------------------------------------------------------------
    b.box('body', (-2.4, 15.4, -5.6), (2.4, 18.8, 0.6), 'tank')
    b.box('body', (-1.9, 15.8, -6.7), (1.9, 18.5, -5.6), 'tank')
    b.box('body', (-2.0, 15.1, 0.6), (2.0, 18.0, 1.9), 'tank')
    b.box('body', (-1.7, 18.8, -5.0), (1.7, 19.3, 0.2), 'tank')
    b.cbox('body', (0.0, 19.5, -2.6), (1.2, 0.5, 1.2), 'chrome')              # filler cap
    b.box('body', (-0.35, 19.3, -4.6), (0.35, 19.45, 0.0), 'chrome')           # tank strip
    b.box('body', (-2.6, 11.8, 3.0), (2.6, 13.2, 10.2), 'leather')            # seat pan
    b.box('body', (-1.6, 12.0, 2.1), (1.6, 13.1, 3.0), 'leather')             # seat nose
    b.box('body', (-2.4, 13.2, 10.0), (2.4, 14.7, 11.8), 'leather')           # hump
    b.box('body', (-2.2, 11.2, 10.0), (2.2, 13.1, 11.6), 'leather')
    b.box('body', (1.5, 7.4, 4.6), (3.0, 10.8, 8.4), 'black')                 # oil tank (left)
    b.box('body', (2.98, 8.2, 4.4), (3.18, 8.7, 8.6), 'chrome')
    b.box('body', (2.98, 9.6, 4.4), (3.18, 10.1, 8.6), 'chrome')
    for s in (-1, 1):
        b.box('body', (s * 3.1, 7.8, -3.7), (s * 5.9, 8.7, -2.7), 'chrome')    # forward pegs
        b.box('body', (s * 3.9, 7.7, -3.75), (s * 5.6, 8.8, -2.65), 'grip')
        b.tube('body', (s * 2.0, 5.0, -2.8), (s * 3.2, 8.2, -3.2), 0.7, 'black')

    # ---- rear fender with spikes and tail light ------------------------------------------------------------------
    fr_in, fr_out = 7.0, 7.7
    steps = [-18 + k * 14.0 for k in range(9)]                                 # -18 .. 94 degrees from the top
    for k, phi in enumerate(steps):
        ang = phi
        w = 5.6 - (0.08 if k % 2 else 0.0)
        chord = 2 * fr_out * math.sin(math.radians(7.0)) + 0.35
        o = [-w / 2.0, AXLE_Y + fr_in, AXLE_Z - chord / 2.0]
        m.cube('body', o, [w, fr_out - fr_in, chord], MATS['black'], rotation=[-ang, 0, 0], pivot=[0, AXLE_Y, AXLE_Z])
    for phi in (8.0, 32.0, 56.0):
        a = math.radians(phi)
        base = (0.0, AXLE_Y + fr_out * math.cos(a), AXLE_Z + fr_out * math.sin(a))
        b.spike('body', base, (0.0, math.cos(a) + 0.35, math.sin(a) + 0.5), 2.2, 0.9)
    a = math.radians(70.0)
    b.cbox('body', (0.0, AXLE_Y + (fr_out + 0.3) * math.cos(a), AXLE_Z + (fr_out + 0.3) * math.sin(a)),
           (2.4, 0.9, 1.0), 'tail', rot=(-70.0 + 90.0, 0, 0))
    for s in (-1, 1):                                                          # fender struts
        b.tube('body', (s * 3.0, 7.0, AXLE_Z + 0.6), (s * 2.95, 11.6, AXLE_Z + 5.2), 0.8, 'chrome')

    # ---- kickstand (left) ------------------------------------------------------------------------------------------
    # authored hanging straight down, folded back along the frame by the bone's rest rotation; the renderer swings it
    # down and out (-10, 0, -28 from straight) while the bike is parked, leaning 8 degrees onto it
    m.bone('kickstand', 'body', [2.6, 4.0, 1.4], rotation=[80, 0, 0])
    b.box('kickstand', (2.25, -0.2, 1.05), (2.95, 4.2, 1.75), 'chrome')
    b.box('kickstand', (2.0, -0.6, 0.6), (3.2, -0.15, 2.2), 'black')

    # ---- fork: rake, steering, legs, clamps, front wheel ---------------------------------------------------------------
    m.bone('fork_rake', 'body', list(HEAD), rotation=[-RAKE, 0, 0])
    hx, hy, hz = HEAD
    b.box('fork_rake', (-1.1, 18.3, hz - 1.1), (1.1, 22.3, hz + 1.1), 'black')            # neck (steering head)
    m.bone('fork_steer', 'fork_rake', list(HEAD))
    axle_y = hy - FORK_LEN
    for s in (-1, 1):
        b.box('fork_steer', (s * 1.75, axle_y - 0.6, hz - 0.75), (s * 3.25, 10.6, hz + 0.75), 'black')     # sliders
        b.box('fork_steer', (s * 1.95, 10.6, hz - 0.55), (s * 3.05, 23.6, hz + 0.55), 'chrome')            # stanchions
        b.cbox('fork_steer', (s * 2.5, axle_y, hz), (1.7 + 0.04, 1.4, 1.4), 'chrome', rot=(45, 0, 0))     # axle caps
    b.box('fork_steer', (-3.6, 17.4, hz - 1.2), (3.6, 18.4, hz + 1.3), 'black')            # lower clamp
    b.box('fork_steer', (-3.6, 22.3, hz - 1.35), (3.6, 23.3, hz + 1.45), 'black')          # upper clamp
    b.box('fork_steer', (-0.7, 23.3, hz - 0.7), (0.7, 23.8, hz + 0.7), 'chrome')           # steerer nut
    b.box('fork_steer', (1.95, 5.2, hz + 0.75), (2.75, 7.4, hz + 2.2), 'black')            # caliper (left)
    front = (0.0, axle_y, hz)
    m.bone('front_wheel', 'fork_steer', list(front))
    b.ring('front_wheel', front, 4.65, WHEEL_R, 3.0, 16, 'rubber')
    b.ring('front_wheel', front, 3.75, 4.55, 2.2, 16, 'chrome', phase=11.25)
    for k in range(4):
        b.cbox('front_wheel', front, (0.5 + 0.05 * k, 0.5, 7.8), 'dchrome', rot=(k * 45.0 + 5.0, 0, 0))
    b.cbox('front_wheel', front, (2.6, 2.0, 2.0), 'chrome')
    b.cbox('front_wheel', front, (2.4, 2.0, 2.0), 'chrome', rot=(45, 0, 0))
    b.disc('front_wheel', (1.55, axle_y, hz), 2.5, 0.25, 'rotor')

    # ---- bars and headlamp (model coordinates: bars cancels the rake) -----------------------------------------------
    m.bone('bars', 'fork_steer', list(HEAD), rotation=[RAKE, 0, 0])
    clamp = raked((0.0, 23.3, hz))
    cz = clamp[2]
    # low drag bars: the grips sit at shoulder height a straight arm's length ahead of the rider (QA: the ape hangers
    # filled the first-person view and no straight vanilla arm could reach them)
    for s in (-1, 1):
        b.tube('bars', (s * 1.8, clamp[1] - 0.2, cz), (s * 1.8, 24.6, cz - 0.1), 0.9, 'chrome', ext=0.2)
        b.chain_of_tubes('bars', [(s * 1.8, 24.6, cz - 0.1), (s * 5.4, 24.3, cz - 0.6), (s * 7.2, 23.6, cz + 0.1)], 0.9, 'chrome', ext=0.3)
        b.box('bars', (s * 7.0, 23.1, cz - 0.4), (s * 9.8, 24.1, cz + 0.6), 'grip')
        b.box('bars', (s * 9.8, 23.2, cz - 0.3), (s * 10.2, 24.0, cz + 0.5), 'chrome')
        b.box('bars', (s * 6.8, 24.1, cz - 0.2), (s * 7.4, 24.5, cz + 1.0), 'chrome')               # levers' perches
        b.tube('bars', (s * 7.1, 24.3, cz + 0.6), (s * 9.3, 23.9, cz + 2.2), 0.4, 'chrome')            # levers
    b.box('bars', (-1.8, 23.9, cz - 0.5), (1.8, 24.6, cz + 0.3), 'chrome')                     # riser bridge
    # skull headlamp on the lower clamp
    b.box('bars', (-1.1, 16.4, -8.9), (1.1, 17.8, -7.0), 'black')
    b.box('bars', (-2.2, 15.0, -12.2), (2.2, 19.2, -8.8), 'skull')
    b.box('bars', (-1.6, 13.8, -11.8), (1.6, 15.0, -9.4), 'jaw')
    b.box('bars', (-1.9, 19.2, -11.8), (1.9, 19.7, -9.1), 'skull')
    for s in (-1, 1):                                                         # cheek spikes
        b.spike('bars', (s * 2.1, 16.4, -10.4), (s * 1.0, 0.25, 0.35), 1.8, 0.7)
    return m


def split_b64(rel, limit=45000):
    """Splits a binasset too big for one GitHub push request into <rel>.b64.part0..N at line boundaries (joined back
    by decodeBinaryAssets in viltrumitecore/build.gradle; same as red_hulk_model.split_b64)."""
    import glob
    from jinwoo_geo import BIN
    path = os.path.join(BIN, rel + '.b64')
    for old in glob.glob(path + '.part*'):
        os.remove(old)
    text = open(path).read()
    if len(text) <= limit:
        return
    parts, cur = [], ''
    for line in text.splitlines(keepends=True):
        if len(cur) + len(line) > limit:
            parts.append(cur)
            cur = ''
        cur += line
    parts.append(cur)
    for i, part in enumerate(parts):
        with open('%s.part%d' % (path, i), 'w') as fh:
            fh.write(part)
    os.remove(path)


def move_geo_to_parts(rel, limit=40000):
    """The geometry JSON is bigger than one GitHub push request, so it lives in binassets as plain <rel>.part0..N
    (joined without decoding by decodeBinaryAssets, like the Ultron geometry)."""
    import glob
    from jinwoo_geo import BIN, RES
    src, dst = os.path.join(RES, rel), os.path.join(BIN, rel)
    for old in glob.glob(dst + '.part*'):
        os.remove(old)
    text = open(src, encoding='utf-8').read()
    if len(json.dumps(text)) <= limit:
        return
    chunks, cur = [], 0
    while cur < len(text):
        end = cur
        while end < len(text) and len(json.dumps(text[cur:end + 2000])) <= limit:
            end += 2000
        end = min(len(text), max(end, cur + 1000))
        if end < len(text):
            cut = text.rfind('\n', cur, end)
            if cut > cur + 1000:
                end = cut + 1
        chunks.append(text[cur:end])
        cur = end
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    for i, ch in enumerate(chunks):
        with open('%s.part%d' % (dst, i), 'w', encoding='utf-8') as fh:
            fh.write(ch)
    os.remove(src)


def load_geo(rel):
    """Reads the geometry whole or joined from its binassets parts."""
    import glob
    from jinwoo_geo import BIN, RES
    src = os.path.join(RES, rel)
    if os.path.exists(src):
        return json.load(open(src, encoding='utf-8'))['minecraft:geometry'][0]
    parts = sorted(glob.glob(os.path.join(BIN, rel) + '.part*'), key=lambda f: int(f.rsplit('.part', 1)[1]))
    return json.loads(''.join(open(f, encoding='utf-8').read() for f in parts))['minecraft:geometry'][0]


def preview(out):
    import jinwoo_preview as jp
    from PIL import Image, ImageDraw
    root = os.path.abspath(os.path.join(HERE, '..', '..'))
    g = load_geo(GEO)
    base = np.array(Image.open(os.path.join(root, 'build/jinwoo_preview', TEX)).convert('RGBA'))
    glow = np.array(Image.open(os.path.join(root, 'build/jinwoo_preview', GLOW)).convert('RGBA'))
    frames = [('left', {}, 90, 0), ('right', {}, -90, 0), ('front 3/4', {}, 35, 12), ('rear 3/4', {}, 150, 15),
              ('top', {}, 90, 89), ('front', {}, 0, 0), ('steer 25', {'fork_steer': {'rotation': [0, -25, 0]}}, 30, 30),
              ('parked', {'kickstand': {'rotation': [-90, 0, -28]}}, 0, 5, 8.0)]
    size = 360
    tiles = []
    for title, pose, yaw, pitch, *lean in frames:
        root_m = None
        if lean:
            a = math.radians(lean[0])      # lean onto the left side (JSON +x = internal -x)
            root_m = jp.rot_z(a)
        q = jp.layered([(g, base, glow)], pose, root_m)
        im = Image.fromarray(jp.render(q, None, None, yaw, pitch, size, scale=size / 3.2, center=(0, 0.9, 0)))
        ImageDraw.Draw(im).text((4, 4), title, fill=(255, 255, 255))
        tiles.append(im)
    cols = 4
    sheet = Image.new('RGB', (cols * size, ((len(tiles) + cols - 1) // cols) * size), (30, 30, 30))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * size, (i // cols) * size))
    sheet.save(out)


if __name__ == '__main__':
    model = build()
    write_model(model, GEO, TEX, GLOW)
    move_geo_to_parts(GEO)
    split_b64(TEX)
    split_b64(GLOW)
    n = sum(len(bb.cubes) for bb in model.bones)
    print('hell cycle: %d cubes, atlas %dx%d' % (n, model.width, model.height))
    if '--preview' in sys.argv:
        preview(sys.argv[sys.argv.index('--preview') + 1] if len(sys.argv) > sys.argv.index('--preview') + 1 else '/tmp/bike.png')
