#!/usr/bin/env python3
"""Chitauri chariot (sky sled): geo (~100 cubes: hull, insectoid prow with mandibles, belly cannon, two engine
pods with glowing thrusters, swept side blades, pilot console with control posts and the troopers' grab bar),
atlas + glow and clips (idle hover flutter, cannon recoil). The breakup VFX renders the top-level bones (hull,
nose, cannon, engine_l, engine_r, fin_l, fin_r, rail) as separate pieces. Own work, CC0.

Model origin = entity position + 0.55 blocks (ChitauriChariot.seatPosition centre); deck top at y 6.7 px, so a
soldier's feet stand on it. Front is -Z (the prow), +X the chariot's left side. The control posts sit at
(+-3.0, 24.3, -19.6) and the grab bar at y 22.7, z 4.0: the soldier clips mounted_pilot / mounted put the hands
there (seats in ChitauriChariot.SEATS).

Run: python3 tools/assets/chitauri_chariot.py [--preview]
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import chitauri_lib as L  # noqa: E402
from chitauri_lib import Model, Clip, write_anims  # noqa: E402

GEO = 'geo/chitauri/chariot.geo.json'
ANIM = 'animations/chitauri/chariot.animation.json'
TEX = 'textures/entity/chitauri/chariot.png'
GLOW = 'textures/entity/chitauri/chariot_glow.png'
DECK = 6.7

M = L.mats()


def build():
    m = Model('chitauri_chariot', density=1.5, width=256, seed=1975)

    def box(bone, a, b, mat, **kw):
        x0, y0, z0 = a
        x1, y1, z1 = b
        return m.cube(bone, [min(x0, x1), min(y0, y1), min(z0, z1)], [abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)], M[mat], **kw)

    m.bone('root', None, [0, 0, 0])
    # ---- hull: standing deck over a tapered body, belly keel and hover pads ------------------------
    m.bone('hull', 'root', [0, 2, 0])
    box('hull', (-9.5, 5.2, -14), (9.5, DECK, 16), 'alloy')                      # deck plate
    for z in (-11, -6, -1, 4, 9, 13):                                            # deck grating ridges
        box('hull', (-8.6, DECK, z), (8.6, DECK + 0.15, z + 0.6), 'bronze_dark')
    box('hull', (-0.6, DECK, -13.5), (0.6, DECK + 0.2, 15.5), 'gold')             # deck spine
    box('hull', (-10.8, 4.2, -14), (-9.5, 7.4, 16), 'bronze')                    # deck rims
    box('hull', (9.5, 4.2, -14), (10.8, 7.4, 16), 'bronze')
    box('hull', (-10.9, 7.0, -13), (-9.4, 7.6, 15), 'gold')                       # rim trims
    box('hull', (9.4, 7.0, -13), (10.9, 7.6, 15), 'gold')
    box('hull', (-8, 0, -16), (8, 5.2, 14), 'alloy')                             # main body
    box('hull', (-9.4, -2.6, -12), (-5.4, 1.6, 12), 'bronze', rotation=[0, 0, 28], pivot=[-6, 1.6, 0])    # tapered lower flanks
    box('hull', (5.4, -2.6, -12), (9.4, 1.6, 12), 'bronze', rotation=[0, 0, -28], pivot=[6, 1.6, 0])
    box('hull', (-6, -3, -12), (6, 0, 10), 'alloy')                              # belly
    box('hull', (-2, -5, -8), (2, -3, 6), 'bronze')                              # keel
    box('hull', (-1.2, -5.6, -6), (1.2, -5, 4), 'bronze_dark')
    box('hull', (-11, 0.5, -12), (-8, 4.6, 12), 'bronze_dark', rotation=[0, 0, -12], pivot=[-8, 4.6, 0])   # slanted side armour
    box('hull', (8, 0.5, -12), (11, 4.6, 12), 'bronze_dark', rotation=[0, 0, 12], pivot=[8, 4.6, 0])
    for z in (-11.0, -5.0, 1.0, 7.0):                                            # overlapping side scales
        box('hull', (-11.6, 1.0, z), (-10.6, 4.2, z + 4.2), 'bronze', rotation=[0, -8, -12], pivot=[-8, 4.6, z])
        box('hull', (10.6, 1.0, z), (11.6, 4.2, z + 4.2), 'bronze', rotation=[0, 8, 12], pivot=[8, 4.6, z])
    box('hull', (-11.9, 2.3, -10), (-11.5, 2.9, 10), 'energy', rotation=[0, 0, -12], pivot=[-8, 4.6, 0])  # side light strips
    box('hull', (11.5, 2.3, -10), (11.9, 2.9, 10), 'energy', rotation=[0, 0, 12], pivot=[8, 4.6, 0])
    box('hull', (-9, 4, 16), (9, DECK, 19), 'alloy')                             # rear deck
    box('hull', (-6, 4.6, 19), (6, 5.6, 19.3), 'energy_soft')                    # tail light
    box('hull', (-7, -1, 14), (7, 4, 18), 'bronze_dark')                         # rear bulkhead
    for z0 in (-6.5, 3.5):                                                       # hover pads
        box('hull', (-5, -3.25, z0), (-2, -3.0, z0 + 3.5), 'energy_soft')
        box('hull', (2, -3.25, z0), (5, -3.0, z0 + 3.5), 'energy_soft')
    for z in (-10, -4, 2, 8):                                                    # rib bands across the body
        box('hull', (-8.3, 0.6, z), (8.3, 4.8, z + 0.8), 'bronze_dark')
    # ---- prow: raked insectoid beak with a spine and forward mandibles -----------------------------------
    m.bone('nose', 'root', [0, 3, -16])
    box('nose', (-6.5, -1, -22), (6.5, 5.5, -16), 'alloy')
    box('nose', (-4.6, -0.2, -28), (4.6, 5.6, -22), 'alloy', rotation=[10, 0, 0], pivot=[0, 3, -22])
    box('nose', (-2.6, 1.2, -32.5), (2.6, 5.8, -27.6), 'bronze', rotation=[18, 0, 0], pivot=[0, 3, -28])
    box('nose', (-6.0, 5.0, -27), (6.0, 7.0, -16), 'bronze', rotation=[12, 0, 0], pivot=[0, 6, -16])       # beak cowl
    box('nose', (-3.6, 6.6, -26), (3.6, 7.6, -17), 'bronze_dark', rotation=[12, 0, 0], pivot=[0, 6, -16])
    box('nose', (-0.8, 7.2, -27), (0.8, 9.2, -13), 'gold', rotation=[10, 0, 0], pivot=[0, 7, -13])         # spine ridge
    box('nose', (-1.6, 2.6, -33.0), (1.6, 3.8, -32.6), 'energy', rotation=[18, 0, 0], pivot=[0, 3, -28])    # prow eye
    box('nose', (-7.2, 0.6, -29), (-5.4, 2.6, -20), 'bronze_dark', rotation=[0, -10, 0], pivot=[-6, 2, -20])    # mandibles
    box('nose', (5.4, 0.6, -29), (7.2, 2.6, -20), 'bronze_dark', rotation=[0, 10, 0], pivot=[6, 2, -20])
    box('nose', (-7.0, 0.9, -32), (-6.0, 2.3, -29), 'gold', rotation=[0, -10, 0], pivot=[-6, 2, -20])       # mandible tips
    box('nose', (6.0, 0.9, -32), (7.0, 2.3, -29), 'gold', rotation=[0, 10, 0], pivot=[6, 2, -20])
    for z in (-21.0, -19.0):                                                                             # cheek vent slits
        box('nose', (-6.6, 1.6, z), (-6.35, 3.6, z + 0.9), 'energy_soft')
        box('nose', (6.35, 1.6, z), (6.6, 3.6, z + 0.9), 'energy_soft')
    # ---- belly cannon -----------------------------------------------------------------------------------
    m.bone('cannon', 'root', [0, -2, -19])
    box('cannon', (-2.5, -3.5, -24), (2.5, -0.5, -14), 'alloy_light')
    box('cannon', (-1, -2.8, -31), (1, -1.2, -24), 'alloy')
    for z in (-27.0, -29.2):
        box('cannon', (-1.6, -3.4, z), (1.6, -0.6, z + 1.0), 'gold')
    box('cannon', (-1.2, -3.0, -31.6), (1.2, -1.0, -30.8), 'energy')
    box('cannon', (-1.8, -3.2, -23.6), (1.8, -0.8, -22.2), 'energy_soft')                                 # charge coil
    # ---- engine pods (left, mirrored to the right) ----------------------------------------------------
    m.bone('engine_l', 'root', [12.5, 2, 10])
    box('engine_l', (9.5, -1.5, 2), (15.5, 5.5, 20), 'alloy')
    box('engine_l', (10.0, -1.0, 0), (15.0, 5.0, 2), 'bronze')
    box('engine_l', (10.6, -0.4, -0.25), (14.4, 4.4, 0), 'energy_soft')                                  # intake glow
    box('engine_l', (9.3, 5.5, 4), (15.7, 6.3, 18), 'bronze')                                            # top plate
    box('engine_l', (10.1, -0.9, 20), (14.9, 4.9, 22.6), 'alloy_light')                                  # nozzle
    box('engine_l', (10.7, -0.3, 22.6), (14.3, 4.3, 22.9), 'energy')                                     # thruster
    box('engine_l', (15.1, 3.6, 12), (16.1, 8.8, 19), 'bronze_dark', rotation=[0, 0, -14], pivot=[15.5, 4, 15])   # tail fin
    box('engine_l', (15.3, 8.4, 15), (15.9, 9.2, 19), 'gold', rotation=[0, 0, -14], pivot=[15.5, 4, 15])
    box('engine_l', (11.5, -3.2, 8), (13.5, -1.5, 18), 'bronze_dark')                                    # ventral fin
    box('engine_l', (8, 1, 6), (9.5, 4, 14), 'alloy')                                                    # strut
    for z in (6.0, 11.0, 16.0):
        box('engine_l', (9.2, -1.1, z), (15.8, 5.9, z + 0.7), 'gold')                                    # pod rings
    box('engine_l', (15.5, 1.4, 4), (15.75, 2.2, 18), 'energy')                                         # pod strip
    # ---- swept side blades (two tapering segments) -----------------------------------------------------
    m.bone('fin_l', 'root', [11, 3, -2])
    box('fin_l', (11, 2.7, -8), (17, 3.4, 4), 'bronze', rotation=[0, -22, -12], pivot=[11, 3, -2])
    box('fin_l', (17, 2.8, -7), (22, 3.3, -1), 'bronze', rotation=[0, -22, -12], pivot=[11, 3, -2])
    box('fin_l', (21.6, 2.6, -6.6), (23, 3.5, -2.6), 'gold', rotation=[0, -22, -12], pivot=[11, 3, -2])
    box('fin_l', (12, 3.4, -5), (21, 3.6, -4.2), 'energy_soft', rotation=[0, -22, -12], pivot=[11, 3, -2])
    m.mirror_x('_l', '_r')
    # ---- pilot console with control posts, troopers' grab bar -----------------------------------------
    m.bone('rail', 'root', [0, DECK, 0])
    box('rail', (-2.5, DECK, -22), (2.5, 19.5, -19), 'alloy', rotation=[-6, 0, 0], pivot=[0, DECK, -20])
    box('rail', (-3.2, DECK, -22.6), (3.2, 9.5, -18.4), 'bronze_dark')                                    # console foot
    box('rail', (-4.5, 18.8, -23.2), (4.5, 21.4, -18.4), 'bronze')                                        # console head
    box('rail', (-2.4, 19.3, -18.45), (2.4, 20.9, -18.2), 'energy_soft')                                  # console screen
    box('rail', (-4.8, 21.2, -23.0), (4.8, 21.8, -18.8), 'gold')
    for sx in (-1, 1):
        box('rail', (sx * 2.5, 20.6, -20.1), (sx * 3.5, 25.6, -19.1), 'alloy_light')                       # control posts
        box('rail', (sx * 2.4, 25.4, -20.2), (sx * 3.6, 26.0, -19.0), 'gold')
        box('rail', (sx * 10.2, DECK, 3.5), (sx * 11.2, 22.6, 4.5), 'alloy')                              # bar posts
        box('rail', (sx * 10.0, DECK, 3.2), (sx * 11.4, 8.4, 4.8), 'bronze_dark')
    box('rail', (-11.2, 22.2, 3.5), (11.2, 23.2, 4.5), 'alloy_light')                                     # grab bar
    box('rail', (-11.4, 22.0, 3.3), (-10.0, 23.4, 4.7), 'gold')
    box('rail', (10.0, 22.0, 3.3), (11.4, 23.4, 4.7), 'gold')
    return m


def build_clips():
    idle = Clip('idle', 2.4, loop=True)
    for i in range(7):
        t = i * 0.4
        w = math.sin(2 * math.pi * t / 2.4)
        c = math.cos(2 * math.pi * t / 2.4)
        idle.key(t, {'fin_l': (1.5 * w, 0, -2 * w), 'fin_r': (1.5 * w, 0, 2 * w), 'engine_l': (1.2 * c, 0, 0),
                     'engine_r': (-1.2 * c, 0, 0), 'cannon': (0.6 * w, 0, 0)})
    cannon = Clip('cannon', 0.32)
    for t, r in ((0.0, 0.0), (0.03, 1.0), (0.12, 0.45), (0.32, 0.0)):
        cannon.key(t, {'cannon': {'rot': [-3 * r, 0, 0], 'pos': [0, 0.3 * r, 2.4 * r]}, 'nose': {'rot': [-1.5 * r, 0, 0]}})
    return [idle, cannon]


def main():
    m = build()
    m.write(GEO, TEX, GLOW)
    n = sum(len(b.cubes) for b in m.bones)
    print('chariot: %d bones, %d cubes, atlas %dx%d' % (len(m.bones), n, m.width, m.height))
    size = write_anims(ANIM, build_clips())
    print('chariot clips: %d bytes' % size)
    if '--preview' in sys.argv:
        import json
        import chitauri_soldier as S
        out = os.path.join(L.ROOT, 'build', 'chitauri_preview')
        tex = os.path.join(L.ROOT, 'build', 'jinwoo_preview', TEX)
        glow = os.path.join(L.ROOT, 'build', 'jinwoo_preview', GLOW)
        with open(os.path.join(L.RES, GEO)) as fh:
            g = json.load(fh)
        L.preview(os.path.join(out, 'chariot_rest.png'), [(g, {}, tex, glow)], views=('front', 'q_front', 'side', 'q_back', 'top'), scale=6)
        # crew check: pilot and two troopers standing at their seats with their clips
        clips = {c.name: c for c in S.build_clips()}
        sg = S.geo()
        stex = os.path.join(L.ROOT, 'build', 'jinwoo_preview', S.TEX)
        sglow = os.path.join(L.ROOT, 'build', 'jinwoo_preview', S.GLOW)
        items = [(g, {}, tex, glow)]
        seats = [((0.0, 0.42, 0.75), 'mounted_pilot'), ((-0.5, 0.42, -0.55), 'mounted'), ((0.5, 0.42, -0.55), 'mounted')]
        for (right, up, fwd), clip in seats:
            pose = L.sample_clip(clips[clip], 0.0)
            pose['root'] = {'pos': [-right * 16, up * 16, -fwd * 16]}
            items.append((sg, pose, stex, sglow))
        L.preview(os.path.join(out, 'chariot_crew.png'), items, views=('front', 'q_front', 'side', 'q_back'), scale=5)


if __name__ == '__main__':
    main()
