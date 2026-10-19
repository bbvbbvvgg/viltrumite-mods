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
    # A glove is a wrist child, not a rigid extension of the sleeve. Keep its
    # original cube/pivot/UV intact so a grip can align without twisting an elbow.
    for side,x in [('right',-5.3281),('left',5.3281)]:
        name=side+'_hand'
        if any(b['name']==name for b in bones): continue
        parent=next(b for b in bones if b['name']==side+'_forearm')
        glove=[c for c in parent['cubes'] if c['origin'][1]<12]
        parent['cubes']=[c for c in parent['cubes'] if c not in glove]
        bones.append(dict(name=name,parent=parent['name'],pivot=[x,12.6993,0],cubes=glove))
    if path.name == 'reinhard.geo.json':
        body = next(b for b in bones if b['name']=='body')
        body['pivot']=[0,12,0]
        if not any(b['name']=='reid_grip' for b in bones):
            bones.append(dict(name='reid_grip',parent='body',pivot=[0,0,0]))
        # A visible belt carrier bridges the coat flare. Do not float the sheath
        # outside the coat or bury it in the long skirt to fake attachment.
        if not any(b['name']=='reid_belt' for b in bones):
            belt=next(c for c in body['cubes'] if abs(c['origin'][1]-13.6922)<.001)
            import copy
            strap=dict(origin=[3.45,13.65,-1.5],size=[3.35,1.05,1.5],uv=copy.deepcopy(belt['uv']))
            bones.append(dict(name='reid_belt',parent='body',pivot=[0,12,0],cubes=[strap]))
        next(b for b in bones if b['name']=='reid_belt')['cubes'][0]['size']=[3.35,1.05,1.5]
        for b in bones:
            if b['name'] in ('head','right_arm','left_arm'):
                b['parent']='body'
        for side,x in [('right',-1.9),('left',1.9)]:
            parent=next(b for b in bones if b['name']==side+'_leg')
            name=side+'_shin'
            if any(b['name']==name for b in bones): continue
            lower=[c for c in parent['cubes'] if c['origin'][1]+c['size'][1] <= 7.633]
            parent['cubes']=[c for c in parent['cubes'] if c not in lower]
            bones.append(dict(name=name,parent=parent['name'],pivot=[x,7.6325,0],cubes=lower))
    path.write_text(json.dumps(data, indent=1)+'\n')

if __name__ == '__main__':
    for name in ['reinhard', 'reinhard_arms']:
        rig(GEO / (name+'.geo.json'))
