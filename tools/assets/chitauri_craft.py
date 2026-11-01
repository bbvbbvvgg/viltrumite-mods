#!/usr/bin/env python3
"""Chitauri craft and field structures: the Interceptor (one-seat attack skiff with a hunched pilot, swept blade
wings with air brakes, tilting engine nacelles, folding landing legs, twin belly cannons), the Siege platform
(armoured hover sled, anchor pads, yawing turret with a pitching rail barrel, capacitor banks that open while it
charges and the exposed core weak point), and the field Beacon (tripod pylon with counter-rotating rings; the
containment variant unfolds four restraint arms over its holding field). Geo, atlas + glow and clips each.
Own work, CC0.

Run: python3 tools/assets/chitauri_craft.py [--preview]
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import chitauri_lib as L  # noqa: E402
import chitauri_units as U  # noqa: E402
from chitauri_lib import Model, Clip, write_anims  # noqa: E402

M = U.M
LIMIT = U.LIMIT


def make(identifier, seed, density=1.5):
    m = Model(identifier, density=density, width=256, seed=seed)

    def box(bone, a, b, mat, **kw):
        x0, y0, z0 = a
        x1, y1, z1 = b
        return m.cube(bone, [min(x0, x1), min(y0, y1), min(z0, z1)], [abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)], M[mat], **kw)
    return m, box


# ---- Interceptor -------------------------------------------------------------------------------------------
def build_interceptor():
    m, box = make('chitauri_interceptor', 6604, density=1.5)
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, 8, 0])
    # fuselage: segmented insect body, armoured spine, cockpit tub
    box('body', (-3.2, 5.0, -8.0), (3.2, 9.6, 8.0), 'alloy')
    box('body', (-2.6, 4.0, -6.0), (2.6, 5.0, 6.5), 'bronze_dark')
    box('body', (-1.4, 3.2, -4.0), (1.4, 4.0, 4.0), 'alloy_light')
    for k, z in enumerate((-7.6, -3.4, 0.8, 5.0)):
        box('body', (-3.6, 6.0, z), (3.6, 9.2, z + 2.6), 'bronze', rotation=[0, 0, 0])
    box('body', (-0.8, 9.6, -2.0), (0.8, 10.6, 8.4), 'gold_dark')                         # dorsal spine
    box('body', (-3.0, 9.4, -7.6), (3.0, 10.0, -2.2), 'alloy_light')                       # cockpit rim
    box('body', (-2.4, 7.8, -7.2), (2.4, 9.6, -2.6), 'skin_dark')                          # cockpit tub
    box('body', (-2.2, 9.6, -8.4), (2.2, 10.4, -7.4), 'energy_soft')                       # canopy light
    box('body', (-3.4, 5.4, 6.0), (3.4, 9.0, 9.4), 'alloy')                                # tail segment
    box('body', (-2.6, 6.0, 9.4), (2.6, 8.6, 11.4), 'bronze_dark')
    box('body', (-1.6, 6.6, 11.4), (1.6, 8.0, 12.4), 'energy')                             # main thruster
    # pilot: hunched in the tub, hands on the yoke
    m.bone('pilot', 'body', [0, 9.0, -4.8])
    box('pilot', (-1.6, 9.0, -6.2), (1.6, 12.6, -3.4), 'skin')
    box('pilot', (-1.8, 10.2, -6.6), (1.8, 12.4, -5.9), 'bronze')
    box('pilot', (-1.9, 12.0, -6.0), (1.9, 12.9, -3.2), 'bronze_plain')
    m.bone('pilot_head', 'pilot', [0, 12.8, -5.0])
    box('pilot_head', (-1.1, 12.8, -6.6), (1.1, 15.0, -4.4), 'skin')
    box('pilot_head', (-1.25, 14.0, -6.85), (1.25, 14.6, -6.3), 'gold')
    box('pilot_head', (-0.9, 13.5, -6.9), (-0.2, 13.9, -6.75), 'eye')
    box('pilot_head', (0.2, 13.5, -6.9), (0.9, 13.9, -6.75), 'eye')
    box('pilot_head', (-0.35, 14.8, -6.2), (0.35, 15.8, -3.8), 'bronze_plain', rotation=[-14, 0, 0], pivot=[0, 15.0, -5.0])
    for s, b in ((-1, 'pilot_arm_r'),):                                                      # pilot_arm_l by mirror
        m.bone(b, 'pilot', [s * 2.0, 12.0, -5.0])
        box(b, (s * 2.0 - 0.55, 9.0, -6.0), (s * 2.0 + 0.55, 12.2, -4.8), 'skin')
        box(b, (s * 1.6 - 0.5, 8.8, -8.0), (s * 1.6 + 0.5, 9.8, -5.6), 'skin_dark')
    box('pilot', (-1.6, 8.6, -8.6), (1.6, 9.2, -7.8), 'alloy_light')                       # yoke
    # nose: mandible prow with the sensor eye
    m.bone('nose', 'body', [0, 7.4, -8.0])
    box('nose', (-2.4, 5.6, -11.6), (2.4, 8.8, -8.0), 'bronze')
    box('nose', (-1.6, 6.0, -13.4), (1.6, 8.0, -11.6), 'bronze_dark')
    box('nose', (-0.7, 6.6, -13.9), (0.7, 7.4, -13.3), 'energy')
    for s in (-1, 1):
        box('nose', (s * 2.0 - 0.4, 4.6, -15.2), (s * 2.0 + 0.4, 6.2, -10.6), 'gold_dark', rotation=[0, -s * 12, 0], pivot=[s * 2.0, 5.4, -10.6])
    # twin belly cannons
    m.bone('cannon', 'body', [0, 4.2, -3.0])
    for s in (-1, 1):
        box('cannon', (s * 1.6 - 0.6, 2.8, -10.4), (s * 1.6 + 0.6, 4.0, -1.0), 'alloy')
        box('cannon', (s * 1.6 - 0.75, 2.65, -6.0), (s * 1.6 + 0.75, 4.15, -4.6), 'gold')
        box('cannon', (s * 1.6 - 0.35, 3.0, -10.8), (s * 1.6 + 0.35, 3.8, -10.3), 'energy')
    m.bone('muzzle', 'cannon', [0, 3.4, -10.8])
    # wings (right; mirrored): root spar, swept blade, air brake flap, tip claw
    m.bone('wing_r', 'body', [-3.2, 8.0, 0.0])
    box('wing_r', (-10.0, 7.4, -3.0), (-3.2, 8.6, 3.2), 'alloy', rotation=[0, -14, 0], pivot=[-3.2, 8.0, 0])
    box('wing_r', (-17.0, 7.6, -2.6), (-9.6, 8.4, 3.4), 'bronze', rotation=[0, -24, -4], pivot=[-9.6, 8.0, 0])
    box('wing_r', (-15.0, 8.4, -1.6), (-10.4, 8.8, 2.4), 'alloy_lit', rotation=[0, -24, -4], pivot=[-9.6, 8.0, 0])
    box('wing_r', (-17.4, 7.5, -3.0), (-9.4, 8.5, -2.4), 'gold', rotation=[0, -24, -4], pivot=[-9.6, 8.0, 0])
    box('wing_r', (-20.0, 7.2, 2.6), (-16.0, 8.8, 4.0), 'gold_dark', rotation=[0, -24, -4], pivot=[-9.6, 8.0, 0])
    box('wing_r', (-8.6, 8.6, -1.2), (-5.0, 9.0, 1.8), 'alloy_lit', rotation=[0, -14, 0], pivot=[-3.2, 8.0, 0])
    m.bone('flap_r', 'wing_r', [-10.0, 8.3, 3.4])
    box('flap_r', (-15.6, 7.8, 3.2), (-8.4, 8.4, 5.8), 'bronze_dark', rotation=[0, -24, 0], pivot=[-10.0, 8.3, 3.4])
    # engine nacelle under the wing root, tilts for hover
    m.bone('engine_r', 'wing_r', [-7.4, 6.4, 0.0])
    box('engine_r', (-9.0, 4.6, -4.6), (-5.8, 7.8, 4.4), 'alloy')
    box('engine_r', (-9.3, 4.4, -3.0), (-5.5, 8.0, -1.8), 'bronze')
    box('engine_r', (-9.3, 4.4, 1.4), (-5.5, 8.0, 2.6), 'bronze')
    box('engine_r', (-8.6, 5.0, -5.4), (-6.2, 7.4, -4.6), 'alloy_light')                  # intake
    box('engine_r', (-8.4, 5.2, 4.4), (-6.4, 7.2, 5.4), 'energy')                         # exhaust
    m.bone('thrust_r', 'engine_r', [-7.4, 6.2, 5.4])
    # landing leg
    m.bone('leg_r', 'body', [-2.0, 4.6, -1.0])
    box('leg_r', (-2.5, 0.0, -1.5), (-1.5, 4.6, -0.5), 'alloy_light')
    box('leg_r', (-3.0, -0.6, -2.6), (-1.0, 0.2, 0.4), 'bronze_dark')
    m.mirror_x('_r', '_l')
    m.bone('leg_b', 'body', [0, 5.0, 6.0])
    box('leg_b', (-0.5, 0.0, 5.5), (0.5, 5.0, 6.5), 'alloy_light')
    box('leg_b', (-1.2, -0.6, 5.0), (1.2, 0.2, 7.4), 'bronze_dark')
    m.bone('tail', 'body', [0, 9.0, 8.0])
    box('tail', (-0.4, 9.0, 6.0), (0.4, 14.0, 10.4), 'bronze', rotation=[-24, 0, 0], pivot=[0, 9.0, 8.0])
    box('tail', (-0.5, 13.0, 9.4), (0.5, 14.4, 12.0), 'gold_dark', rotation=[-24, 0, 0], pivot=[0, 9.0, 8.0])
    m.bone('rudder', 'tail', [0, 11.0, 10.6])
    box('rudder', (-0.3, 9.4, 10.0), (0.3, 12.6, 12.4), 'bronze_dark', rotation=[-24, 0, 0], pivot=[0, 9.0, 8.0])
    return m


def interceptor_clips():
    out = []
    legs_up = {'leg_r': (-90, 0, 0), 'leg_l': (-90, 0, 0), 'leg_b': (80, 0, 0)}
    legs_down = {'leg_r': (0, 0, 0), 'leg_l': (0, 0, 0), 'leg_b': (0, 0, 0)}

    def wings(fold=0.0, sweep=0.0, dihedral=0.0, flap=0.0, tilt=0.0):
        """fold: wings raised (perched), sweep: swept back (boost), flap: air brakes up, tilt: engines down (hover)."""
        return {'wing_r': (0, -18 * sweep, -dihedral - 40 * fold), 'wing_l': (0, 18 * sweep, dihedral + 40 * fold),
                'flap_r': (-70 * flap, 0, 0), 'flap_l': (-70 * flap, 0, 0),
                'engine_r': (-80 * tilt, 18 * sweep, dihedral + 40 * fold), 'engine_l': (-80 * tilt, -18 * sweep, -dihedral - 40 * fold)}

    def pilot(look=0.0, lean=0.0):
        return {'pilot': (8 + 10 * lean, 0, 0), 'pilot_head': (-10 - 10 * lean, look, 0), 'pilot_arm_r': (-30 - 10 * lean, 0, 0),
                'pilot_arm_l': (-30 - 10 * lean, 0, 0)}

    cruise = Clip('cruise', 1.6, loop=True)
    for i in range(5):
        t = i * 0.4
        w = math.sin(2 * math.pi * t / 1.6)
        cruise.key(t, U.merge(wings(dihedral=4 + 3 * w), legs_up, pilot(6 * w), {'body': (1.5 * w, 0, 0), 'rudder': (0, 4 * w, 0)}))
    out.append(cruise)
    hover = Clip('hover', 2.0, loop=True)
    for i in range(6):
        t = i * 0.4
        w = math.sin(2 * math.pi * t / 2.0)
        hover.key(t, U.merge(wings(fold=0.15, dihedral=8 + 4 * w, tilt=1.0), legs_up, pilot(20 * math.sin(2 * math.pi * t / 2.0 + 1), 0.0),
                             {'body': {'rot': [-4 + 2 * w, 0, 0], 'pos': [0, 0.6 * w, 0]}}))
    out.append(hover)
    boost = Clip('boost', 0.4, loop=True)
    for i in range(5):
        t = i * 0.1
        w = math.sin(2 * math.pi * t / 0.4)
        boost.key(t, U.merge(wings(sweep=1.0, dihedral=-2 + 1.5 * w), legs_up, pilot(0, 1.0), {'body': (2 + 0.8 * w, 0, 0)}))
    out.append(boost)
    brake = Clip('brake', 0.6)
    brake.key(0.0, U.merge(wings(dihedral=4), legs_up, pilot(), {'body': (0, 0, 0)}))
    brake.key(0.15, U.merge(wings(dihedral=10, flap=1.0, tilt=0.6), legs_up, pilot(0, -1.0), {'body': (-16, 0, 0)}))
    brake.key(0.45, U.merge(wings(dihedral=8, flap=0.8, tilt=0.8), legs_up, pilot(0, -0.6), {'body': (-10, 0, 0)}))
    brake.key(0.6, U.merge(wings(dihedral=8, flap=0.3, tilt=1.0), legs_up, pilot(), {'body': (-4, 0, 0)}))
    out.append(brake)
    takeoff = Clip('takeoff', 1.2)
    takeoff.key(0.0, U.merge(wings(fold=1.0, tilt=1.0), legs_down, pilot(), {'body': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}))
    takeoff.key(0.35, U.merge(wings(fold=0.4, tilt=1.0, dihedral=6), legs_down, pilot(0, 0.5), {'body': {'rot': [-6, 0, 0], 'pos': [0, -0.6, 0]}}))
    takeoff.key(0.7, U.merge(wings(fold=0.1, tilt=0.8, dihedral=8), legs_up, pilot(0, 0.6), {'body': {'rot': [-10, 0, 0], 'pos': [0, 0.4, 0]}}))
    takeoff.key(1.2, U.merge(wings(dihedral=4, tilt=0.0), legs_up, pilot(0, 0.8), {'body': {'rot': [4, 0, 0], 'pos': [0, 0, 0]}}))
    out.append(takeoff)
    perch = Clip('perch', 3.0, loop=True)
    for i in range(4):
        t = i * 1.0
        w = math.sin(2 * math.pi * t / 3.0)
        perch.key(t, U.merge(wings(fold=1.0 - 0.05 * w, tilt=1.0), legs_down, pilot(30 * w), {'body': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}))
    out.append(perch)
    # banking / turning layers (the renderer rolls the whole craft; these flex the surfaces into the turn)
    for name, s in (('turn_l', 1), ('turn_r', -1)):
        c = Clip(name, 1.0, loop=True)
        for t in (0.0, 0.5, 1.0):
            c.key(t, U.merge(wings(dihedral=4), {'wing_r': (0, 0, -4 - s * 8), 'wing_l': (0, 0, 4 - s * 8), 'flap_r': (-30 if s < 0 else 0, 0, 0),
                                                 'flap_l': (-30 if s > 0 else 0, 0, 0), 'rudder': (0, s * 24, 0)}, legs_up,
                             pilot(s * 24, 0.3)))
        out.append(c)
    accel = Clip('accel', 0.5)
    accel.key(0.0, {'body': (0, 0, 0), 'engine_r': (0, 0, 0), 'engine_l': (0, 0, 0)})
    accel.key(0.12, {'body': (6, 0, 0), 'engine_r': (8, 0, 0), 'engine_l': (8, 0, 0), 'pilot': (10, 0, 0)})
    accel.key(0.5, {'body': (0, 0, 0), 'engine_r': (0, 0, 0), 'engine_l': (0, 0, 0), 'pilot': (0, 0, 0)})
    out.append(accel)
    maneuver = Clip('maneuver', 0.9)           # evasive barrel roll flourish: wings flex through the roll
    for t, f in ((0.0, 0.0), (0.2, 1.0), (0.45, -1.0), (0.7, 0.6), (0.9, 0.0)):
        maneuver.key(t, U.merge(wings(sweep=0.5, dihedral=6 * f), legs_up, pilot(-20 * f, 0.8), {'rudder': (0, 20 * f, 0)}))
    out.append(maneuver)
    fire = Clip('fire', 0.3)
    fire.key(0.0, {'cannon': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}, 'nose': (0, 0, 0)})
    fire.key(0.04, {'cannon': {'rot': [-2, 0, 0], 'pos': [0, 0.2, 1.6]}, 'nose': (-2, 0, 0)})
    fire.key(0.3, {'cannon': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}, 'nose': (0, 0, 0)})
    out.append(fire)
    hit = Clip('hit', 0.35)
    hit.key(0.0, {'body': (0, 0, 0), 'pilot': (0, 0, 0), 'wing_r': (0, 0, 0), 'wing_l': (0, 0, 0)})
    hit.key(0.05, {'body': (-6, 4, 8), 'pilot': (-14, 0, 6), 'wing_r': (0, 0, 8), 'wing_l': (0, 0, -4)})
    hit.key(0.35, {'body': (0, 0, 0), 'pilot': (0, 0, 0), 'wing_r': (0, 0, 0), 'wing_l': (0, 0, 0)})
    out.append(hit)
    # death fall: the left wing is torn and flutters, the pilot slumps, engines sputter (the renderer spins it)
    df = Clip('death_fall', 0.8, loop=True)
    for i in range(5):
        t = i * 0.2
        w = math.sin(2 * math.pi * t / 0.8)
        df.key(t, U.merge(wings(dihedral=0), legs_up, {'wing_l': (20 * w, 10, 50 + 25 * w), 'flap_l': (-80, 0, 30 * w), 'wing_r': (0, 0, -10 * w),
                                                       'flap_r': (-40 * abs(w), 0, 0), 'pilot': (30, 0, 20), 'pilot_head': (30, 20, 0),
                                                       'pilot_arm_r': (10, 0, -30), 'pilot_arm_l': (-60, 0, 40), 'engine_l': (40 * w, 20, 0),
                                                       'tail': (0, 0, 10 * w)}))
    out.append(df)
    return out


# ---- Siege platform ----------------------------------------------------------------------------------------
def build_siege():
    m, box = make('chitauri_siege', 7705, density=1.25)
    m.bone('root', None, [0, 0, 0])
    m.bone('hull', 'root', [0, 6, 0])
    box('hull', (-14, 4.0, -18), (14, 11.0, 18), 'alloy')
    box('hull', (-12, 11.0, -14), (12, 13.0, 14), 'bronze_dark')
    box('hull', (-16, 3.0, -16), (-14, 10.0, 16), 'bronze', rotation=[0, 0, -10], pivot=[-14, 10, 0])
    box('hull', (14, 3.0, -16), (16, 10.0, 16), 'bronze', rotation=[0, 0, 10], pivot=[14, 10, 0])
    for z in (-15, -9, -3, 3, 9):
        box('hull', (-16.8, 4.0, z), (-15.6, 9.6, z + 5), 'bronze_dark', rotation=[0, -6, -10], pivot=[-14, 10, z])
        box('hull', (15.6, 4.0, z), (16.8, 9.6, z + 5), 'bronze_dark', rotation=[0, 6, 10], pivot=[14, 10, z])
    box('hull', (-10, 1.0, -16), (10, 4.0, 14), 'alloy_light')                                    # belly
    box('hull', (-6, -0.4, -12), (6, 1.0, 10), 'bronze_dark')
    for x in (-7.0, 4.0):                                                                          # belly glow strips
        box('hull', (x, -0.6, -10), (x + 3, -0.3, 8), 'energy_soft')
    # prow ram with mandible blades
    box('hull', (-10, 4.0, -24), (10, 10.0, -18), 'bronze')
    box('hull', (-6, 5.0, -28), (6, 9.0, -24), 'bronze_dark')
    for s in (-1, 1):
        box('hull', (s * 7 - 1, 3.0, -32), (s * 7 + 1, 6.0, -22), 'gold_dark', rotation=[0, -s * 14, 0], pivot=[s * 7, 4.5, -22])
    box('hull', (-2, 6.0, -28.6), (2, 8.0, -28.0), 'energy')
    # rear: engine block with the core weak point under an armoured hatch
    box('hull', (-12, 5.0, 18), (12, 12.0, 24), 'alloy')
    for s in (-1, 1):
        box('hull', (s * 8 - 3, 6.0, 24), (s * 8 + 3, 10.0, 26), 'energy')
    m.bone('core', 'hull', [0, 13.0, 15.0])
    box('core', (-3.0, 12.4, 12.0), (3.0, 14.6, 18.0), 'plasma')
    m.bone('hatch', 'hull', [0, 13.6, 18.0])
    box('hatch', (-4.0, 13.0, 11.4), (4.0, 15.0, 18.4), 'bronze', inflate=0.05)
    box('hatch', (-1.0, 15.0, 12.0), (1.0, 15.6, 18.0), 'gold')
    # anchor pads (corners): drop and spike the street while deployed
    for name, x, z in (('pad_fr', -12, -14), ('pad_fl', 12, -14), ('pad_br', -12, 14), ('pad_bl', 12, 14)):
        m.bone(name, 'hull', [x, 4.0, z])
        box(name, (x - 3.0, 1.0, z - 3.0), (x + 3.0, 4.0, z + 3.0), 'alloy_light')
        box(name, (x - 2.2, 0.4, z - 2.2), (x + 2.2, 1.0, z + 2.2), 'energy_soft')
        box(name, (x - 0.8, -4.0, z - 0.8), (x + 0.8, 1.0, z + 0.8), 'gold_dark')                  # spike
    # capacitor banks on the flanks: hinge open during the charge
    for name, s in (('cap_r', -1), ('cap_l', 1)):
        m.bone(name, 'hull', [s * 12, 13.0, 2.0])
        box(name, (s * 12 - 2.0, 13.0, -6.0), (s * 12 + 2.0, 17.0, 10.0), 'alloy')
        for k in range(4):
            z = -5.0 + k * 4.0
            box(name, (s * 12 - 2.4, 13.4, z), (s * 12 + 2.4, 16.6, z + 1.2), 'gold')
            box(name, (s * 12 - 1.4, 17.0, z + 0.1), (s * 12 + 1.4, 17.4, z + 1.1), 'energy')
    # turret (yaw) with the rail barrel (pitch), its coils and muzzle
    m.bone('turret', 'hull', [0, 13.0, -2.0])
    box('turret', (-7.0, 13.0, -9.0), (7.0, 19.0, 5.0), 'bronze')
    box('turret', (-5.0, 19.0, -7.0), (5.0, 21.0, 3.0), 'bronze_dark')
    box('turret', (-7.6, 14.0, -6.0), (-7.0, 18.0, 2.0), 'gold')
    box('turret', (7.0, 14.0, -6.0), (7.6, 18.0, 2.0), 'gold')
    box('turret', (-2.0, 21.0, -3.0), (2.0, 22.4, 1.0), 'alloy_lit')                               # sensor dome
    box('turret', (-1.0, 21.4, -3.3), (1.0, 22.2, -3.0), 'eye')
    m.bone('barrel', 'turret', [0, 16.5, -8.0])
    box('barrel', (-2.6, 14.4, -14.0), (2.6, 18.6, -6.0), 'alloy')
    for k, b in enumerate(('coil1', 'coil2', 'coil3')):
        z = -16.0 - k * 6.0
        m.bone(b, 'barrel', [0, 16.5, z])
        box(b, (-2.8, 13.7, z - 1.6), (2.8, 19.3, z + 1.0), 'gold_dark')
        box(b, (-2.0, 14.5, z - 1.7), (2.0, 18.5, z + 1.1), 'energy_soft')
    for s in (-1, 1):                                                                              # twin rails
        box('barrel', (s * 1.6 - 0.7, 15.0, -34.0), (s * 1.6 + 0.7, 18.0, -14.0), 'bronze_dark')
    box('barrel', (-0.6, 16.0, -33.0), (0.6, 17.0, -14.0), 'energy')
    box('barrel', (-3.0, 14.0, -36.0), (3.0, 19.0, -34.0), 'gold')
    m.bone('muzzle', 'barrel', [0, 16.5, -36.5])
    return m


def siege_clips():
    out = []
    idle = Clip('idle', 2.4, loop=True)
    for i in range(7):
        t = i * 0.4
        w = math.sin(2 * math.pi * t / 2.4)
        idle.key(t, {'hull': {'rot': [0.8 * w, 0, 0.6 * math.cos(2 * math.pi * t / 2.4)], 'pos': [0, 0.6 * w, 0]}})
    out.append(idle)
    move = Clip('move', 1.2, loop=True)
    for i in range(4):
        t = i * 0.4
        w = math.sin(2 * math.pi * t / 1.2)
        move.key(t, {'hull': {'rot': [3 + 1.0 * w, 0, 1.2 * w], 'pos': [0, 0.4 * w, 0]}, 'pad_fr': (10, 0, 0), 'pad_fl': (10, 0, 0),
                     'pad_br': (-10, 0, 0), 'pad_bl': (-10, 0, 0)})
    out.append(move)
    pads = lambda d: {n: {'rot': [0, 0, 0], 'pos': [0, -5.0 * d, 0]} for n in ('pad_fr', 'pad_fl', 'pad_br', 'pad_bl')}  # noqa: E731
    caps = lambda o: {'cap_r': (0, 0, 40 * o), 'cap_l': (0, 0, -40 * o)}  # noqa: E731
    deploy = Clip('deploy', 1.0)
    deploy.key(0.0, U.merge(pads(0), caps(0), {'hull': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}))
    deploy.key(0.4, U.merge(pads(1.0), caps(0.2), {'hull': {'rot': [2, 0, 0], 'pos': [0, -1.0, 0]}}))
    deploy.key(0.55, U.merge(pads(1.1), caps(0.3), {'hull': {'rot': [-1, 0, 0], 'pos': [0, -2.2, 0]}}))
    deploy.key(1.0, U.merge(pads(1.0), caps(0.4), {'hull': {'rot': [0, 0, 0], 'pos': [0, -2.0, 0]}}))
    out.append(deploy)
    deployed = U.merge(pads(1.0), {'hull': {'rot': [0, 0, 0], 'pos': [0, -2.0, 0]}})
    charge = Clip('charge', 2.2)
    for i in range(12):
        t = i * 0.2
        f = min(1.0, t / 2.0)
        j = (0.4 + 1.2 * f) * (1 if i % 2 else -1)
        charge.key(min(t, 2.2), U.merge(deployed, caps(0.4 + 0.6 * f), {
            'hatch': (-70 * min(1.0, t / 0.5), 0, 0), 'coil1': {'rot': [0, 0, 0], 'pos': [0, 0, 0.4 * f], 'scale': [1 + 0.06 * f, 1 + 0.06 * f, 1]},
            'coil2': {'pos': [0, 0, 0.6 * f], 'scale': [1 + 0.08 * f, 1 + 0.08 * f, 1]},
            'coil3': {'pos': [0, 0, 0.8 * f], 'scale': [1 + 0.1 * f, 1 + 0.1 * f, 1]},
            'barrel': {'rot': [0, 0, j * 0.6], 'pos': [0, 0, 0]}, 'turret': (0, 0, j * 0.3)}))
    out.append(charge)
    fire = Clip('fire', 1.1)
    fire.key(0.0, U.merge(deployed, caps(1.0), {'hatch': (-70, 0, 0), 'barrel': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}))
    fire.key(0.05, U.merge(deployed, caps(1.0), {'hatch': (-70, 0, 0), 'barrel': {'rot': [-8, 0, 0], 'pos': [0, 0.5, 6.0]},
                                                 'hull': {'rot': [-3, 0, 0], 'pos': [0, -1.6, 1.4]}}))
    fire.key(0.4, U.merge(deployed, caps(0.7), {'hatch': (-50, 0, 0), 'barrel': {'rot': [-2, 0, 0], 'pos': [0, 0, 1.5]}}))
    fire.key(1.1, U.merge(deployed, caps(0.4), {'hatch': (0, 0, 0), 'barrel': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}}))
    out.append(fire)
    stagger = Clip('stagger', 0.6)
    stagger.key(0.0, {'hull': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}, 'turret': (0, 0, 0)})
    stagger.key(0.06, {'hull': {'rot': [-5, 3, 6], 'pos': [0, 0.8, 1.0]}, 'turret': (0, 6, 0)})
    stagger.key(0.25, {'hull': {'rot': [2, -1, -2], 'pos': [0, -0.4, 0.2]}, 'turret': (0, -2, 0)})
    stagger.key(0.6, {'hull': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}, 'turret': (0, 0, 0)})
    out.append(stagger)
    dying = Clip('dying', 2.5)
    dying.key(0.0, {'hull': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}, 'barrel': (0, 0, 0), 'cap_r': (0, 0, 0), 'cap_l': (0, 0, 0), 'hatch': (0, 0, 0)})
    dying.key(0.3, {'hull': {'rot': [4, 0, -8], 'pos': [0, 0.6, 0]}, 'barrel': (10, 0, 0), 'cap_r': (0, 0, 30), 'cap_l': (0, 0, -10),
                    'hatch': (-110, 0, 0)})
    dying.key(0.9, {'hull': {'rot': [10, 0, 14], 'pos': [0, -3.0, 0]}, 'barrel': (24, 0, 0), 'cap_r': (0, 0, 70), 'cap_l': (0, 0, -60),
                    'hatch': (-140, 0, 0), 'turret': (0, 18, 6)})
    dying.key(1.3, {'hull': {'rot': [14, 0, 20], 'pos': [0, -7.0, 0]}, 'barrel': (30, 0, 0), 'cap_r': (0, 0, 80), 'cap_l': (0, 0, -70),
                    'hatch': (-150, 0, 0), 'turret': (0, 22, 8)})
    dying.key(1.45, {'hull': {'rot': [12, 0, 18], 'pos': [0, -6.2, 0]}, 'barrel': (28, 0, 0), 'turret': (0, 22, 8)})
    dying.key(2.5, {'hull': {'rot': [12, 0, 18], 'pos': [0, -6.4, 0]}, 'barrel': (30, 0, 0), 'cap_r': (0, 0, 80), 'cap_l': (0, 0, -70),
                    'hatch': (-150, 0, 0), 'turret': (0, 22, 8)})
    out.append(dying)
    return out


# ---- Beacon ------------------------------------------------------------------------------------------------
def build_beacon():
    m, box = make('chitauri_beacon', 8806, density=1.5)
    m.bone('root', None, [0, 0, 0])
    m.bone('base', 'root', [0, 4, 0])
    box('base', (-4.0, 2.0, -4.0), (4.0, 6.0, 4.0), 'alloy')
    box('base', (-3.0, 6.0, -3.0), (3.0, 7.4, 3.0), 'bronze_dark')
    box('base', (-4.4, 3.0, -4.4), (4.4, 4.0, 4.4), 'gold_dark')
    box('base', (-1.6, 0.0, -1.6), (1.6, 2.0, 1.6), 'energy_soft')
    for k in range(3):
        a = k * 120.0
        b = 'leg%d' % (k + 1)
        rx, rz = math.sin(math.radians(a)) * 3.6, -math.cos(math.radians(a)) * 3.6
        m.bone(b, 'base', [rx, 4.0, rz], rotation=[0, -a, 0])
        box(b, (rx - 0.8, 0.0, rz - 9.0), (rx + 0.8, 1.6, rz), 'bronze', rotation=[-12, 0, 0], pivot=[rx, 4.0, rz])
        box(b, (rx - 1.2, -0.8, rz - 10.4), (rx + 1.2, 0.4, rz - 8.0), 'alloy_light', rotation=[-12, 0, 0], pivot=[rx, 4.0, rz])
        box(b, (rx - 0.4, 1.6, rz - 7.0), (rx + 0.4, 2.0, rz - 1.0), 'energy_soft', rotation=[-12, 0, 0], pivot=[rx, 4.0, rz])
    m.bone('mast', 'base', [0, 7.4, 0])
    box('mast', (-1.6, 7.4, -1.6), (1.6, 34.0, 1.6), 'alloy')
    for y in (12.0, 20.0, 28.0):
        box('mast', (-2.2, y, -2.2), (2.2, y + 1.2, 2.2), 'gold')
    for s in (-1, 1):
        box('mast', (s * 1.6 - 0.3 if s > 0 else -1.9, 9.0, -0.6), (s * 1.6 + 0.3 if s > 0 else -1.3, 32.0, 0.6), 'energy_soft')
    box('mast', (-0.6, 9.0, -1.9), (0.6, 32.0, -1.3), 'energy_soft')
    for name, y, r in (('ring_a', 16.0, 6.0), ('ring_b', 25.0, 5.0)):
        m.bone(name, 'mast', [0, y, 0])
        for k in range(6):
            a = k * 60.0
            box(name, (-1.6, y - 0.6, -r - 0.6), (1.6, y + 0.6, -r + 0.6), 'bronze', rotation=[0, a, 0], pivot=[0, y, 0])
            box(name, (-0.5, y - 0.8, -r - 0.9), (0.5, y + 0.8, -r - 0.5), 'energy', rotation=[0, a + 30, 0], pivot=[0, y, 0])
    m.bone('crown', 'mast', [0, 34.0, 0])
    box('crown', (-3.0, 34.0, -3.0), (3.0, 36.0, 3.0), 'bronze')
    for k in range(4):
        a = k * 90.0 + 45.0
        box('crown', (-0.5, 35.0, -3.6), (0.5, 40.0, -2.6), 'gold_dark', rotation=[-16, a, 0], pivot=[0, 35.0, 0])
    box('crown', (-1.4, 36.0, -1.4), (1.4, 38.8, 1.4), 'energy')
    m.bone('emitter', 'crown', [0, 39.0, 0])
    # containment restraint arms (hidden on a stronghold by the renderer)
    m.bone('cage', 'mast', [0, 30.0, 0])
    for k in range(4):
        a = k * 90.0
        b = 'arm%d' % (k + 1)
        m.bone(b, 'cage', [0, 30.0, 0], rotation=[0, -a, 0])
        box(b, (-0.7, 29.4, -18.0), (0.7, 30.6, -2.0), 'alloy_light', rotation=[-8, 0, 0], pivot=[0, 30.0, 0])
        box(b, (-1.0, 26.0, -19.4), (1.0, 31.0, -17.4), 'bronze', rotation=[-8, 0, 0], pivot=[0, 30.0, 0])
        box(b, (-0.5, 24.0, -18.9), (0.5, 26.0, -17.9), 'tether', rotation=[-8, 0, 0], pivot=[0, 30.0, 0])
        box(b, (-0.3, 30.6, -14.0), (0.3, 31.0, -4.0), 'tether_soft', rotation=[-8, 0, 0], pivot=[0, 30.0, 0])
    return m


def beacon_clips():
    out = []
    legs = lambda f: {'leg1': (70 * f, 0, 0), 'leg2': (70 * f, 0, 0), 'leg3': (70 * f, 0, 0)}  # noqa: E731
    arms = lambda f: {'arm%d' % (k + 1): (-80 * f, 0, 0) for k in range(4)}  # noqa: E731
    deploy = Clip('deploy', 2.0)
    deploy.key(0.0, U.merge(legs(1.0), arms(1.0), {'mast': {'rot': [0, 0, 0], 'pos': [0, -24, 0]}, 'ring_a': {'rot': [0, 0, 0], 'scale': [0.3, 1, 0.3]},
                                                    'ring_b': {'rot': [0, 0, 0], 'scale': [0.3, 1, 0.3]}, 'base': {'pos': [0, 3, 0]}}))
    deploy.key(0.35, U.merge(legs(1.0), arms(1.0), {'base': {'pos': [0, 0, 0]}, 'mast': {'pos': [0, -24, 0]}}))
    deploy.key(0.6, U.merge(legs(-0.1), arms(1.0), {'base': {'pos': [0, -0.4, 0]}, 'mast': {'pos': [0, -20, 0]}}))
    deploy.key(1.2, U.merge(legs(0.0), arms(0.8), {'mast': {'pos': [0, 0, 0]}, 'ring_a': {'rot': [0, 120, 0], 'scale': [0.6, 1, 0.6]},
                                                   'ring_b': {'rot': [0, -120, 0], 'scale': [0.6, 1, 0.6]}}))
    deploy.key(1.6, U.merge(legs(0.0), arms(-0.1), {'mast': {'pos': [0, 0.6, 0]}, 'ring_a': {'rot': [0, 200, 0], 'scale': [1.08, 1, 1.08]},
                                                    'ring_b': {'rot': [0, -200, 0], 'scale': [1.08, 1, 1.08]}}))
    deploy.key(2.0, U.merge(legs(0.0), arms(0.0), {'mast': {'pos': [0, 0, 0]}, 'ring_a': {'rot': [0, 240, 0], 'scale': [1, 1, 1]},
                                                   'ring_b': {'rot': [0, -240, 0], 'scale': [1, 1, 1]}, 'base': {'pos': [0, 0, 0]}}))
    deploy.keep.update({'mast', 'base'})
    out.append(deploy)
    idle = Clip('idle', 4.0, loop=True)
    for i in range(9):
        t = i * 0.5
        w = math.sin(2 * math.pi * t / 4.0)
        idle.key(t, {'ring_a': {'rot': [0, 90 * t, 0], 'pos': [0, 0.6 * w, 0]}, 'ring_b': {'rot': [0, -120 * t, 0], 'pos': [0, -0.6 * w, 0]},
                     'crown': {'rot': [0, 22.5 * t, 0], 'scale': [1 + 0.04 * w, 1 + 0.04 * w, 1 + 0.04 * w]},
                     'arm1': (3 * w, 0, 0), 'arm2': (-3 * w, 0, 0), 'arm3': (3 * w, 0, 0), 'arm4': (-3 * w, 0, 0)})
    out.append(idle)
    hurt = Clip('hurt', 0.4)
    hurt.key(0.0, {'mast': (0, 0, 0), 'crown': (0, 0, 0)})
    hurt.key(0.05, {'mast': (4, 0, -3), 'crown': (-6, 0, 6)})
    hurt.key(0.18, {'mast': (-2, 0, 1.5), 'crown': (3, 0, -3)})
    hurt.key(0.4, {'mast': (0, 0, 0), 'crown': (0, 0, 0)})
    out.append(hurt)
    down = Clip('down', 1.6)
    down.key(0.0, U.merge(legs(0), arms(0), {'mast': {'rot': [0, 0, 0], 'pos': [0, 0, 0]}, 'ring_a': {'pos': [0, 0, 0]}, 'ring_b': {'pos': [0, 0, 0]},
                                             'crown': (0, 0, 0)}))
    down.key(0.2, U.merge(legs(0), arms(-0.4), {'mast': {'rot': [4, 0, -6], 'pos': [0, 0, 0]}, 'crown': (20, 0, -20)}))
    down.key(0.6, U.merge(legs(-0.2), arms(-1.0), {'mast': {'rot': [16, 0, -24], 'pos': [0, -2, 0]}, 'ring_a': {'pos': [0, -8, 0]},
                                                    'ring_b': {'pos': [0, -14, 0]}, 'crown': (30, 0, -30)}))
    down.key(1.0, U.merge(legs(-0.3), arms(-1.2), {'mast': {'rot': [50, 0, -60], 'pos': [0, -3, 0]}, 'ring_a': {'pos': [0, -10, 0]},
                                                    'ring_b': {'pos': [0, -18, 0]}, 'crown': (30, 0, -30)}))
    down.key(1.15, U.merge(legs(-0.3), arms(-1.2), {'mast': {'rot': [74, 0, -80], 'pos': [0, -3.4, 0]}}))
    down.key(1.6, U.merge(legs(-0.3), arms(-1.2), {'mast': {'rot': [78, 0, -82], 'pos': [0, -3.4, 0]}, 'ring_a': {'pos': [0, -10, 0]},
                                                    'ring_b': {'pos': [0, -18, 0]}, 'crown': (30, 0, -30)}))
    down.keep.update({'mast'})
    out.append(down)
    return out


UNITS = {'interceptor': (build_interceptor, interceptor_clips), 'siege': (build_siege, siege_clips), 'beacon': (build_beacon, beacon_clips)}


def main():
    out_dir = os.path.join(L.ROOT, 'build', 'chitauri_preview')
    for name, (build, clips_fn) in UNITS.items():
        P = U.paths(name)
        m = build()
        m.write(P['geo'], P['tex'], P['glow'])
        n = sum(len(b.cubes) for b in m.bones)
        clips = clips_fn()
        size = write_anims(P['anims'][0], clips)
        geo_size = os.path.getsize(os.path.join(L.RES, P['geo']))
        print('%s: %d bones, %d cubes, atlas %dx%d, geo %d B, %d clips %d B' % (name, len(m.bones), n, m.width, m.height, geo_size,
                                                                              len(clips), size))
        assert geo_size < LIMIT and size < LIMIT
        if '--preview' in sys.argv:
            with open(os.path.join(L.RES, P['geo'])) as fh:
                g = json.load(fh)
            tex = os.path.join(L.ROOT, 'build', 'jinwoo_preview', P['tex'])
            glow = os.path.join(L.ROOT, 'build', 'jinwoo_preview', P['glow'])
            L.preview(os.path.join(out_dir, '%s_rest.png' % name), [(g, {}, tex, glow)], views=('front', 'q_front', 'side', 'top'), scale=5)
            ims = []
            for c in clips:
                pose = L.sample_clip(c, c.length * 0.4)
                ims.append(L.preview(os.path.join(out_dir, '%s_%s.png' % (name, c.name)), [(g, pose, tex, glow)], views=('q_front', 'side'), scale=3))
            L.sheet(os.path.join(out_dir, '%s_clips.png' % name), ims, cols=4)


if __name__ == '__main__':
    main()
