#!/usr/bin/env python3
"""REINHARD OVERHAUL item 6: Reid's blade 25% longer, hilt and guard untouched.

Only the blade cubes change: the five long cubes keep their base (just above the
guard) and grow from the blade root BASE; the tip cubes move with the point of the
blade where they sit, so the tip keeps its shape. The sheath grows by the same
amount: its body/edge rails get longer and the chape (every cube at the far end)
moves down by the extra blade length inside it. Per-face UVs stretch over the
longer faces (uniform steel), no texture edit. Refuses to run twice.
Run: python3 tools/assets/lengthen_reid_blade.py
"""
import json
from pathlib import Path

GEO = Path(__file__).resolve().parents[2] / 'viltrumitecore/src/main/resources/assets/viltrumitecore/geo/reinhard'
SCALE = 1.25
BASE = 5.1          # blade root (native px), just above the guard/ricasso cubes
LONG = 20.0         # cubes longer than this are blade body
TIP_FROM = 36.0     # cubes starting above this are the point
OLD_TIP = 40.78     # measured tip before the change
SHEATH_OFFSET = 14.0  # sheathed blade y = sheath y + 14 (sheathed_offset bone)


def r(v):
    return round(v, 4)


def lengthen_blade(geo):
    cubes = geo['minecraft:geometry'][0]['bones'][0]['cubes']
    long = [c for c in cubes if c['size'][1] > LONG]
    assert len(long) == 5 and abs(max(c['origin'][1] + c['size'][1] for c in long) - 39.04) < .01, 'blade already lengthened?'
    for c in long:
        top = c['origin'][1] + c['size'][1]
        c['size'][1] = r(BASE + (top - BASE) * SCALE - c['origin'][1])
        if 'pivot' in c:
            c['pivot'][1] = r(BASE + (c['pivot'][1] - BASE) * SCALE)
    for c in cubes:
        if c['origin'][1] >= TIP_FROM:
            anchor = c.get('pivot', c['origin'])[1]
            shift = (anchor - BASE) * (SCALE - 1)
            c['origin'][1] = r(c['origin'][1] + shift)
            if 'pivot' in c:
                c['pivot'][1] = r(c['pivot'][1] + shift)
    return BASE + (OLD_TIP - BASE) * SCALE - OLD_TIP


def lengthen_sheath(geo, extra):
    cubes = geo['minecraft:geometry'][0]['bones'][0]['cubes']
    rails = [c for c in cubes if c['size'][1] > LONG]
    assert len(rails) == 3 and all(abs(c['size'][1] - 34.0) < .01 for c in rails), 'sheath already lengthened?'
    chape_from = 17.0
    for c in cubes:
        if c in rails:
            c['size'][1] = r(c['size'][1] + extra)
            if 'pivot' in c:
                c['pivot'][1] = r(c['pivot'][1] + extra / 2)
        elif c['origin'][1] >= chape_from:
            c['origin'][1] = r(c['origin'][1] + extra)
            if 'pivot' in c:
                c['pivot'][1] = r(c['pivot'][1] + extra)


def main():
    blade_path, sheath_path = GEO / 'blade.geo.json', GEO / 'sheath.geo.json'
    blade, sheath = json.loads(blade_path.read_text()), json.loads(sheath_path.read_text())
    extra = lengthen_blade(blade)
    lengthen_sheath(sheath, extra)
    for path, geo in [(blade_path, blade), (sheath_path, sheath)]:
        path.write_text(json.dumps(geo, indent=1) + '\n')
    print(f'blade +{extra:.2f} px (tip {OLD_TIP + extra:.2f}), sheath +{extra:.2f} px')


if __name__ == '__main__':
    main()
