"""Removes the separate finger and thumb cubes of Captain America and the Avengers (user request 2026-10-31: curled
fingers stuck out beside the shield and the weapons). The bones stay (empty), so every animation still loads;
the hand block is lengthened down to where the fingers ended, so the arm keeps its length and reads as a
Minecraft fist. The vanilla-layout rigs (avenger / avenger_slim) already have a plain 4x1x4 finger block: it is
moved onto the hand bone, so finger curls no longer bend it out of the arm.
Hulk's fist only grows by half the finger length (a full-length block reads as a club).
Idempotent: run from the repository root, `python3 tools/assets/remove_fingers.py`."""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
GEO = os.path.join(ROOT, 'viltrumitecore/src/main/resources/assets/viltrumitecore/geo')
FILES = ['captain/captain.geo.json', 'captain/captain_arms.geo.json', 'avengers/avenger.geo.json',
         'avengers/avenger_slim.geo.json', 'avengers/thor.geo.json', 'avengers/black_widow.geo.json',
         'avengers/hawkeye.geo.json', 'avengers/hulk.geo.json']
HALF = {'avengers/hulk.geo.json'}


def run(rel):
    path = os.path.join(GEO, rel)
    data = json.load(open(path))
    bones = {b['name']: b for b in data['minecraft:geometry'][0]['bones']}
    changed = 0
    for side in ('left', 'right'):
        hand, fingers, thumb = bones[f'{side}_hand'], bones[f'{side}_fingers'], bones[f'{side}_thumb']
        fc = fingers.get('cubes', []) + thumb.get('cubes', [])
        if not fc:
            continue
        hc = hand.get('cubes', [])
        plain = all(abs(c['size'][0] - hc[0]['size'][0]) < 1e-6 and abs(c['size'][2] - hc[0]['size'][2]) < 1e-6
                    for c in fingers.get('cubes', []))
        if plain:
            hand['cubes'] = hc + fingers.get('cubes', []) + thumb.get('cubes', [])
        else:
            top = hc[0]['origin'][1]
            low = min(c['origin'][1] for c in fingers.get('cubes', []))
            if rel in HALF:
                low = top - (top - low) / 2
            w0, d0 = hc[0]['size'][0], hc[0]['size'][2]
            for i, c in enumerate(hc):
                if i == 0 or (c['size'][0] >= 0.9 * w0 and c['size'][2] >= 0.9 * d0):
                    y1 = c['origin'][1] + c['size'][1]
                    y0 = low - (0.05 if i else 0.0)
                    c['origin'][1] = round(y0, 4)
                    c['size'][1] = round(y1 - y0, 4)
        fingers['cubes'] = []
        thumb['cubes'] = []
        changed += len(fc)
    with open(path, 'w') as f:
        json.dump(data, f, separators=(',', ':'))
    return changed


if __name__ == '__main__':
    for rel in FILES:
        print(rel, 'finger/thumb cubes removed:', run(rel))
