#!/usr/bin/env python3
"""Ultron scout drone: a chrome sphere the size of a head with one red optic under an angry brow, four ducted
rotors on struts (vectoring for flight), a whip antenna, side vents and a folding twin-prong zapper under the
optic. Geo (139 cubes), metal atlas + red glow atlas, and every clip: hover, fly, accel/decel/turn_l/turn_r
(additive), scan, zap, mark, hurt (additive), heavy_hit, death, fabricate. The rotors spin procedurally in the
renderer (speed follows thrust); the clips move the chassis, struts, optic iris, antenna and zapper. Contact
frames match UltronMove (ZAP 16 ticks, hit 9; MARK 30, paint 12; SCAN 40; death 24). Own work, CC0.

Run: python3 tools/assets/ultron_drone.py [--preview]
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ultron_lib as U  # noqa: E402
from ultron_lib import UClip  # noqa: E402

GEO = 'geo/ultron/drone.geo.json'
ANIM = 'animations/ultron/drone.animation.json'
TEX = 'textures/entity/ultron/drone.png'
GLOW = 'textures/entity/ultron/drone_glow.png'
CY = 6.4            # chassis centre height (px above the feet)
ARMS = ('f', 'b')   # front / back strut pairs (mirrored _r -> _l)


def build():
    m = U.Model('ultron_drone', density=4, width=256, seed=1601)
    b = U.Builder(m, U.mats())

    def pair(bone, a, c, mat, rot=None, pivot=None):
        if rot:
            b.rbox(bone, a, c, mat, rot, pivot)
            pv = None if pivot is None else (-pivot[0], pivot[1], pivot[2])
            b.rbox(bone, (-c[0], a[1], a[2]), (-a[0], c[1], c[2]), mat, (rot[0], -rot[1], -rot[2]), pv)
        else:
            b.box(bone, a, c, mat)
            b.box(bone, (-c[0], a[1], a[2]), (-a[0], c[1], c[2]), mat)

    b.bone('root', None, [0, 0, 0])
    b.bone('body', 'root', [0, CY, 0])
    # ---- chassis: a faceted ball (plates tangent to a sphere: 5 latitudes x 8 longitudes about the Z axis) ----
    R, ZC = 3.9, 0.3
    mats = ('chrome', 'chrome_seam', 'chrome_dark', 'chrome', 'chrome_light', 'chrome', 'chrome_seam', 'chrome_dark')
    for lat in (-58, -29, 0, 29, 58):
        r = R * math.cos(math.radians(lat))
        w = 2 * r * math.tan(math.radians(22.5)) * 1.14
        h = 2 * R * math.tan(math.radians(14.5)) * 1.18
        for i in range(8):
            th = i * 45
            mat = mats[i]
            if lat == 0 and th in (90, 270):
                mat = 'vent_glow'
            elif lat == 0:
                mat = 'steel'
            elif lat == -58 and th in (0, 180):
                mat = 'gun_light'
            b.rbox('body', (-w / 2, CY + R - 0.75, ZC - h / 2), (w / 2, CY + R, ZC + h / 2), mat, (lat, 0, th), (0, CY, ZC))
    b.box('body', (-2.0, CY - 2.0, ZC - 2.4), (2.0, CY + 2.0, ZC + 2.4), 'gun_dark')          # inner frame (no gaps)
    b.box('body', (-1.5, CY - 1.5, ZC + R - 0.7), (1.5, CY + 1.5, ZC + R - 0.1), 'gun')     # rear pole cap
    # optic: dark socket, lens, angry V brow, cheek plates
    b.oct('body', (0, CY, -3.25), 1.95, 1.0, 'z', 'gun_dark')
    b.box('body', (-1.6, CY - 1.6, -4.05), (1.6, CY + 1.6, -3.75), 'lens')
    pair('body', (-3.0, CY + 1.85, -4.45), (-0.05, CY + 2.6, -2.1), 'chrome_light', (-14, 0, -17), (-1.5, CY + 2.2, -3.3))
    pair('body', (-2.9, CY - 2.7, -4.1), (-1.0, CY - 0.9, -2.4), 'chrome', (10, -24, 14), (-1.9, CY - 1.8, -3.2))
    b.box('body', (-0.32, CY + 3.3, -2.6), (0.32, CY + 4.25, 2.8), 'steel')                 # crest
    b.box('body', (-1.3, CY - 1.0, 3.75), (1.3, CY + 1.2, 4.75), 'gun_light')               # rear thruster
    b.box('body', (-0.9, CY - 0.7, 4.7), (0.9, CY + 0.9, 5.0), 'thruster')
    b.box('body', (-1.5, CY - 4.25, -1.4), (1.5, CY - 3.55, 1.8), 'gun')                    # belly plate
    # iris: four aperture blades around the lens (rotate = focus sweep, scale down = aperture closes)
    b.bone('iris', 'body', [0, CY, -4.2])
    for (x0, y0, x1, y1) in ((-1.85, CY + 1.35, 1.25, CY + 1.95), (-1.25, CY - 1.95, 1.85, CY - 1.35),
                             (-1.95, CY - 1.25, -1.35, CY + 1.85), (1.35, CY - 1.85, 1.95, CY + 1.25)):
        b.box('iris', (x0, y0, -4.35), (x1, y1, -4.05), 'gun')
    # antenna (sways, extends while marking)
    b.bone('antenna', 'body', [0.9, CY + 3.0, 1.2])
    b.box('antenna', (0.65, CY + 2.8, 0.95), (1.15, CY + 3.5, 1.45), 'gun')
    b.box('antenna', (0.78, CY + 3.4, 1.08), (1.02, CY + 7.4, 1.32), 'chrome_light')
    b.box('antenna', (0.7, CY + 7.3, 1.0), (1.1, CY + 7.9, 1.4), 'core_box')
    # zapper: folds under the chassis, swings forward to fire (tip = the server's zap origin, ~local(0, .25, .4))
    b.bone('zapper', 'body', [0, CY - 2.9, -1.6])
    b.box('zapper', (-1.0, CY - 3.6, -2.2), (1.0, CY - 2.6, -0.8), 'gun')
    pair('zapper', (-0.85, CY - 3.45, -5.6), (-0.4, CY - 2.95, -2.0), 'chrome_light')
    pair('zapper', (-0.95, CY - 3.55, -5.9), (-0.3, CY - 2.85, -5.4), 'steel_dark')
    b.box('zapper', (-0.28, CY - 3.45, -5.2), (0.28, CY - 2.95, -2.2), 'cable')
    b.bone('zap_tip', 'zapper', [0, CY - 3.2, -5.6])
    b.box('zap_tip', (-0.3, CY - 3.5, -5.75), (0.3, CY - 2.9, -5.15), 'core')
    # ---- rotor struts (mirrored): strut, octagonal duct ring, rotor hub with three blades -------------
    for k, (z, tilt) in enumerate(((-3.3, -14), (3.3, 14))):
        arm = 'arm_%s_r' % ARMS[k]
        rot = 'rotor_%s_r' % ARMS[k]
        cx, cy, cz = -6.1, CY + 0.9, z * 1.08
        b.bone(arm, 'body', [-2.6, CY + 0.4, z * 0.55])
        b.rbox(arm, (-5.0, CY + 0.05, z * 0.55 - 0.45), (-2.2, CY + 0.85, z * 0.55 + 0.45), 'steel_dark', (0, tilt, 8), (-2.6, CY + 0.4, z * 0.55))
        b.rbox(arm, (-5.2, CY + 0.3, z * 0.55 - 0.3), (-3.4, CY + 0.6, z * 0.55 + 0.3), 'cable', (0, tilt, 8), (-2.6, CY + 0.4, z * 0.55))
        b.oct(arm, (-2.9, CY + 0.4, z * 0.55), 0.75, 1.4, 'x', 'gun')
        r_out, r_in, h = 2.35, 1.95, 1.0
        y0, y1 = cy - h / 2, cy + h / 2
        for rot_y in (0, 45):
            for (a, c) in (((cx - r_out, y0, cz - r_out), (cx + r_out, y1, cz - r_in)),
                           ((cx - r_out, y0, cz + r_in), (cx + r_out, y1, cz + r_out)),
                           ((cx - r_out, y0, cz - r_in), (cx - r_in, y1, cz + r_in)),
                           ((cx + r_in, y0, cz - r_in), (cx + r_out, y1, cz + r_in))):
                mat = 'chrome' if rot_y == 0 else 'chrome_dark'
                if rot_y:
                    b.rbox(arm, a, c, mat, (0, 45, 0), (cx, cy, cz))
                else:
                    b.box(arm, a, c, mat)
        b.box(arm, (cx - 0.5, y0 - 0.25, cz - 0.5), (cx + 0.5, y0 + 0.1, cz + 0.5), 'gun')          # motor
        b.box(arm, (cx - 0.25, y0 - 0.6, cz - r_out - 0.3), (cx + 0.25, y0 + 0.4, cz - r_out + 0.2), 'gun_lit')
        b.bone(rot, arm, [cx, cy, cz])
        b.box(rot, (cx - 0.45, cy - 0.35, cz - 0.45), (cx + 0.45, cy + 0.25, cz + 0.45), 'chrome_light')
        for a in (0, 120, 240):
            b.rbox(rot, (cx - 0.32, cy - 0.06, cz - 1.85), (cx + 0.32, cy + 0.1, cz - 0.3), 'steel', (0, a, 0), (cx, cy, cz))
    U.mirror_r(m)
    return m


# ---- poses: bone -> (rx, ry, rz) or {'rot', 'pos'} (Blockbench degrees; rx > 0 noses down) -------------
def pose(body=(0, 0, 0), pos=(0, 0, 0), arms_f=(0, 0, 0), arms_b=(0, 0, 0), iris=(0, 0, 0), iris_s=1.0,
         antenna=(0, 0, 0), ant_pos=0.0, zapper=58.0, tip_s=1.0, root=(0, 0, 0), root_pos=(0, 0, 0)):
    """arms_*: (pitch, yaw, roll) of the right strut; the left mirrors (pitch, -yaw, -roll)."""
    p = {'root': {'rot': tuple(root), 'pos': tuple(root_pos)},
         'body': {'rot': tuple(body), 'pos': tuple(pos)},
         'iris': {'rot': tuple(iris), 'scale': (iris_s, iris_s, 1.0)},
         'antenna': {'rot': tuple(antenna), 'pos': (0.0, ant_pos, 0.0)},
         'zapper': (zapper, 0.0, 0.0),
         'zap_tip': {'rot': (0.0, 0.0, 0.0), 'scale': (tip_s, tip_s, tip_s)}}
    for k, a in (('f', arms_f), ('b', arms_b)):
        p['arm_%s_r' % k] = tuple(a)
        p['arm_%s_l' % k] = (a[0], -a[1], -a[2])
    return p


def add(*ps):
    return U.add(*ps)


def iris(p, rot, s):
    """Copy of pose p with the iris turned by rot (deg about Z, added) and scaled to s (absolute)."""
    q = dict(p)
    base = q.get('iris', {'rot': (0, 0, 0)})
    q['iris'] = {'rot': (0.0, 0.0, base['rot'][2] + rot), 'scale': (s, s, 1.0)}
    return q


def clips():
    out = []
    # hover: bob, slow wobble, struts trim against it, the iris drifts, antenna lags
    c = UClip('hover', 2.4, loop=True)
    for i in range(9):
        t = 2.4 * i / 8
        ph = 2 * math.pi * i / 8
        c.key(t, pose(body=(3 * math.sin(ph + 0.6), 0, 4 * math.sin(ph)), pos=(0, 0.55 * math.sin(2 * ph), 0),
                      arms_f=(-3 * math.sin(ph + 0.6), 0, -5 * math.sin(ph)), arms_b=(-3 * math.sin(ph + 0.6), 0, 5 * math.sin(ph)),
                      iris=(0, 0, 25 * math.sin(ph)), antenna=(-6 * math.sin(ph + 1.2), 0, -8 * math.sin(ph + 0.8))))
    out.append(c)
    # fly: nose down, struts vectored back, antenna swept, a little porpoise
    c = UClip('fly', 1.2, loop=True)
    for i in range(7):
        t = 1.2 * i / 6
        ph = 2 * math.pi * i / 6
        c.key(t, pose(body=(18 + 2 * math.sin(ph), 0, 3 * math.sin(ph)), pos=(0, 0.35 * math.sin(2 * ph), 0),
                      arms_f=(-22, 0, -4 * math.sin(ph)), arms_b=(-12, 0, 4 * math.sin(ph)), antenna=(30 + 4 * math.sin(ph * 2), 0, 0),
                      iris=(0, 0, 0), zapper=64))
    out.append(c)
    for name, p in (('accel', {'body': (12, 0, 0), 'arm_f_r': (-16, 0, 0), 'arm_f_l': (-16, 0, 0), 'arm_b_r': (-16, 0, 0),
                               'arm_b_l': (-16, 0, 0), 'antenna': (16, 0, 0)}),
                    ('decel', {'body': (-16, 0, 0), 'arm_f_r': (20, 0, 0), 'arm_f_l': (20, 0, 0), 'arm_b_r': (20, 0, 0),
                               'arm_b_l': (20, 0, 0), 'antenna': (-14, 0, 0)}),
                    ('turn_l', {'body': (0, -10, -16), 'arm_f_r': (0, 0, 8), 'arm_b_r': (0, 0, 8), 'arm_f_l': (0, 0, 8),
                                'arm_b_l': (0, 0, 8), 'antenna': (0, 0, 14)}),
                    ('turn_r', {'body': (0, 10, 16), 'arm_f_r': (0, 0, -8), 'arm_b_r': (0, 0, -8), 'arm_f_l': (0, 0, -8),
                                'arm_b_l': (0, 0, -8), 'antenna': (0, 0, -14)})):
        c = UClip(name, 1.0)
        c.key(0, p).key(1.0, p)
        out.append(c)
    # scan (40 ticks): settle, sweep left - right - centre with the iris focusing, a final double-take
    base = pose(body=(10, 0, 0), arms_f=(-4, 0, 0), arms_b=(-4, 0, 0), zapper=58)
    c = UClip('scan', 2.0, review=0.7)
    c.key(0, pose(), 'easeOutCubic').key(0.25, base, 'easeInOutSine') \
        .key(0.7, iris(add(base, {'body': (0, -38, -8)}), -90, 0.75), 'easeInOutSine') \
        .key(1.2, iris(add(base, {'body': (2, 38, 8)}), 90, 0.7), 'easeInOutSine') \
        .key(1.45, iris(add(base, {'body': (6, 14, 2)}), 40, 0.55), 'easeOutBack') \
        .key(1.6, iris(add(base, {'body': (4, 18, 2)}), 60, 0.5)) \
        .key(2.0, pose())
    out.append(c)
    # zap (16 ticks, hit at 9 = 0.45 s): rear back, prongs swing forward and charge, lunge-snap, recoil, recover
    ready = pose(body=(-10, 0, 0), pos=(0, 0.6, 1.4), arms_f=(12, 0, 0), arms_b=(12, 0, 0), zapper=-4, tip_s=1.6, iris_s=0.7,
                 antenna=(-12, 0, 0))
    snap = pose(body=(14, 0, 0), pos=(0, -0.4, -1.8), arms_f=(-18, 0, 0), arms_b=(-18, 0, 0), zapper=8, tip_s=2.1, iris_s=0.55,
                antenna=(22, 0, 0))
    c = UClip('zap', 0.8, review=0.45)
    c.key(0, pose(), 'easeOutQuad').key(0.3, ready, 'easeInCubic')
    c.key(0.36, add(ready, {'body': (0.8, 0, 0.8)}))
    c.key(0.45, snap, 'easeOutCubic').key(0.56, add(snap, {'body': {'rot': (-12, 0, 0), 'pos': (0, 0.3, 1.2)}}), 'easeInOutSine') \
        .key(0.8, pose(zapper=30))
    out.append(c)
    # mark (30 ticks, paint at 12 = 0.6 s): rise, lock the iris, extend the antenna, pulse, hold the lock, recover
    lock = pose(body=(6, 0, 0), pos=(0, 1.6, 0.4), arms_f=(4, 0, -6), arms_b=(4, 0, 6), iris=(0, 0, 45), iris_s=0.45,
                antenna=(-8, 0, 0), ant_pos=1.4)
    pulse = iris(add(lock, {'body': {'rot': (-6, 0, 0), 'pos': (0, 0.2, 0.7)}, 'antenna': {'rot': (-10, 0, 0), 'pos': (0, 0.6, 0)}}), 45, 0.8)
    c = UClip('mark', 1.5, review=0.6)
    c.key(0, pose(), 'easeOutCubic').key(0.45, lock, 'easeInCubic').key(0.6, pulse, 'easeOutCubic') \
        .key(0.75, iris(lock, -15, 0.45)).key(1.1, iris(lock, 15, 0.45), 'easeInOutSine') \
        .key(1.5, pose())
    out.append(c)
    # hurt: additive jolt
    c = UClip('hurt', 0.35)
    c.key(0, {'body': (0, 0, 0)}).key(0.06, {'body': (-14, 8, 18), 'antenna': (-20, 0, 18)}, 'easeOutCubic') \
        .key(0.35, {'body': (0, 0, 0), 'antenna': (0, 0, 0)})
    out.append(c)
    # heavy_hit: knocked into a tumble, rights itself
    c = UClip('heavy_hit', 1.0, review=0.2)
    c.key(0, pose(), 'easeOutCubic').key(0.2, pose(body=(-50, 30, 70), pos=(0, 1.0, 3.5), arms_f=(20, 0, 20), arms_b=(-20, 0, -20),
                                                   antenna=(-30, 0, 30), iris_s=1.3), 'easeInOutSine') \
        .key(0.5, pose(body=(20, -10, -25), pos=(0, -0.8, 2.4), antenna=(25, 0, -20)), 'easeInOutSine') \
        .key(0.75, pose(body=(-6, 0, 8), pos=(0, 0.2, 0.6)), 'easeInOutSine').key(1.0, pose())
    out.append(c)
    # death (24 ticks): sparks, lurch, struts sag, tumbling drop with the lights out (renderer fades glow)
    c = UClip('death', 1.2, review=0.6)
    c.key(0, pose(), 'easeOutCubic').key(0.12, pose(body=(-20, 15, 30), pos=(0, 0.8, 0), antenna=(-30, 0, 20), iris_s=1.4)) \
        .key(0.3, pose(body=(25, -10, -20), pos=(0, 0.2, 0), arms_f=(30, 0, 25), arms_b=(30, 0, -25), antenna=(30, 0, -30), zapper=80),
             'easeInQuad') \
        .key(1.0, pose(root=(70, 260, 40), root_pos=(0, -3.6, 0), body=(25, -10, -20), arms_f=(50, 0, 40), arms_b=(45, 0, -40),
                       antenna=(60, 0, -40), zapper=90, iris_s=1.6), 'easeOutQuad') \
        .key(1.2, pose(root=(80, 300, 50), root_pos=(0, -3.4, 0), body=(25, -10, -20), arms_f=(55, 0, 45), arms_b=(50, 0, -45),
                       antenna=(70, 0, -40), zapper=90, iris_s=1.6))
    c.keep.update({'root'})
    out.append(c)
    # fabricate (60 ticks): folded ball on the pad, struts unfold one by one, lifts off, optic opens
    folded = pose(pos=(0, -3.0, 0), arms_f=(-70, 0, -80), arms_b=(70, 0, -80), antenna=(80, 0, 0), ant_pos=-1.0, zapper=80, iris_s=0.1)
    c = UClip('fabricate', 3.0, review=1.6)
    c.key(0, folded).key(0.9, folded, 'easeInOutSine') \
        .key(1.3, add(folded, {'arm_f_r': (70, 0, 80), 'arm_f_l': (70, 0, -80)}), 'easeOutBack') \
        .key(1.7, pose(pos=(0, -3.0, 0), antenna=(80, 0, 0), ant_pos=-1.0, zapper=80, iris_s=0.1), 'easeOutBack') \
        .key(2.2, pose(pos=(0, -1.0, 0), body=(-8, 0, 0), antenna=(-10, 0, 0), iris_s=0.2), 'easeInCubic') \
        .key(2.45, pose(pos=(0, 0.4, 0), body=(4, 0, 0), iris_s=1.25), 'easeOutCubic').key(3.0, pose())
    out.append(c)
    return out


def main():
    m = build()
    m.write(GEO, TEX, GLOW)
    n = sum(len(bn.cubes) for bn in m.bones)
    cl = clips()
    size = U.write_anims(ANIM, cl)
    print('drone: %d bones, %d cubes, atlas %dx%d; %d clips (%d bytes)' % (len(m.bones), n, m.width, m.height, len(cl), size))
    U.finalize_assets([GEO, ANIM], [TEX, GLOW])
    if '--preview' in sys.argv:
        U.preview_all('drone', GEO, TEX, GLOW, cl)


if __name__ == '__main__':
    main()
