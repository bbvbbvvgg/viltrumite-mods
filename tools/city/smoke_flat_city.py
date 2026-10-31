r"""Synthetic flat test city for the living city without the licensed Atheria map.

Usage: python3 tools/city/smoke_flat_city.py viltrumitecore/run/world
Run the dev server on a flat world (server.properties: level-type=minecraft\:flat, enable-rcon=true),
then over RCON: /forceload add -96 -96 96 96, /city enable, /city populate 120, summon threats, /city status.
Writes viltrumite_city/profile.json (arena +-112, one district) and VCW1 walk tiles: every column is
walkable at the flat surface (feet y = -60), streets every 16 blocks are WALK, the rest PREFERRED.
"""
import struct, zlib, os, json, sys
world = sys.argv[1]
folder = os.path.join(world, 'viltrumite_city'); os.makedirs(os.path.join(folder,'walk'), exist_ok=True)
FEET = -60; H2 = FEET*2
def encode(h2, cls): return ((h2 + 512) & 0x1FFF) | (cls << 13)
R = 112
for rx in (-1, 0):
  for rz in (-1, 0):
    chunks = [None]*1024
    for x in range(-R, R):
      for z in range(-R, R):
        if x // 512 != rx or z // 512 != rz: continue
        lx, lz = x - rx*512, z - rz*512
        ci = (lz >> 4)*32 + (lx >> 4)
        if chunks[ci] is None: chunks[ci] = [0]*256
        # streets every 16 blocks are "walk", the rest preferred (plazas)
        cls = 1 if (x % 16 == 0 or z % 16 == 0) else 2
        chunks[ci][(lz & 15)*16 + (lx & 15)] = encode(H2, cls)
    bitmap = bytearray(128); body = b''
    for i, ch in enumerate(chunks):
      if ch is not None:
        bitmap[i >> 3] |= 1 << (i & 7)
    for ch in chunks:
      if ch is not None: body += struct.pack('>256H', *ch)
    data = struct.pack('>ii', 0x56435731, 1) + zlib.compress(bytes(bitmap) + body)
    open(os.path.join(folder, 'walk', f'r.{rx}.{rz}.bin'), 'wb').write(data)
json.dump({"format":1,"id":"smoke","name":"Smoke City","arena":{"minX":-R,"minZ":-R,"maxX":R,"maxZ":R},
  "districts":[{"name":"Centria","minX":-R,"minZ":-R,"maxX":R,"maxZ":R}],
  "population":{"density":1.0,"perPlayer":40,"maxTotal":160}}, open(os.path.join(folder,'profile.json'),'w'))
print('ok')
