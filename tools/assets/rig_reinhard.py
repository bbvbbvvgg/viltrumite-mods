#!/usr/bin/env python3
"""Add elbows using the archive's existing sleeve/hand cubes. UVs and faces stay intact.
Run after convert_reinhard.py. Idempotent; no resampling or new third-party art.
"""
import json
from pathlib import Path

GEO = Path(__file__).resolve().parents[2] / 'viltrumitecore/src/main/resources/assets/viltrumitecore/geo/reinhard'

def rig(path):
    data = json.loads(path.read_text())
    bones = data['minecraft:geometry'][0]['bones']
    for side, x in [('right', -5), ('left', 5)]:
        parent = next(b for b in bones if b['name'] in (side+'_arm', 'armor'+side.title()+'Arm'))
        name = side+'_forearm'
        if any(b['name'] == name for b in bones):
            continue
        lower = [c for c in parent['cubes'] if c['origin'][1] < 17.49]
        parent['cubes'] = [c for c in parent['cubes'] if c not in lower]
        bones.append(dict(name=name, parent=parent['name'], pivot=[x,17.5,0], cubes=lower))
    path.write_text(json.dumps(data, indent=1)+'\n')

if __name__ == '__main__':
    for name in ['reinhard', 'reinhard_arms']:
        rig(GEO / (name+'.geo.json'))
