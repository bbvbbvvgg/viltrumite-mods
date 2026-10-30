"""Compare the block states of two copies of a world chunk by chunk (restore verification).

Usage: python3 compare.py <world A> <world B> <chunk x0> <chunk z0> <chunk x1> <chunk z1>
Prints every differing block (up to 40) and a total. Block entities and entities are ignored.
"""
import os, sys
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import nbt

def state(p):
    props = p.get('Properties') or {}
    return str(p['Name']) + ('[' + ','.join('%s=%s' % (k, props[k]) for k in sorted(props)) + ']' if props else '')

def sections(c):
    out = {}
    for s in c.get('sections', []):
        bs = s.get('block_states')
        if not bs or 'palette' not in bs:
            continue
        pal = [state(p) for p in bs['palette']]
        if len(pal) == 1:
            idx = np.zeros(4096, np.int64)
        else:
            bits = max(4, (len(pal) - 1).bit_length()); per = 64 // bits
            arr = np.frombuffer(bs['data'].raw, dtype='>u8').astype(np.uint64)
            sh = np.arange(per, dtype=np.uint64) * np.uint64(bits)
            idx = ((arr[:, None] >> sh[None, :]) & np.uint64((1 << bits) - 1)).reshape(-1)[:4096].astype(np.int64)
        out[int(s['Y'])] = np.array(pal, dtype=object)[idx]
    return out

def chunks(world, rx, rz):
    f = os.path.join(world, 'region', 'r.%d.%d.mca' % (rx, rz))
    res = {}
    if os.path.exists(f):
        for i, ts, raw in nbt.read_region(f):
            _, c = nbt.loads(raw)
            res[(int(c['xPos']), int(c['zPos']))] = c
    return res

def main():
    a, b = sys.argv[1], sys.argv[2]
    x0, z0, x1, z1 = map(int, sys.argv[3:7])
    diff = 0; shown = 0; cache = {}
    for cz in range(z0, z1 + 1):
        for cx in range(x0, x1 + 1):
            r = (cx >> 5, cz >> 5)
            if r not in cache:
                cache[r] = (chunks(a, *r), chunks(b, *r))
            ca, cb = cache[r][0].get((cx, cz)), cache[r][1].get((cx, cz))
            if ca is None or cb is None:
                print('chunk %d %d missing in %s' % (cx, cz, 'A' if ca is None else 'B')); continue
            sa, sb = sections(ca), sections(cb)
            for y in sorted(set(sa) | set(sb)):
                va = sa.get(y, np.array(['minecraft:air'] * 4096, dtype=object))
                vb = sb.get(y, np.array(['minecraft:air'] * 4096, dtype=object))
                d = np.nonzero(va != vb)[0]
                diff += len(d)
                for i in d[:max(0, 40 - shown)]:
                    print('%d %d %d: %s -> %s' % (cx * 16 + (i & 15), y * 16 + (i >> 8), cz * 16 + ((i >> 4) & 15), va[i], vb[i])); shown += 1
    print('differing blocks:', diff)

if __name__ == '__main__':
    main()
