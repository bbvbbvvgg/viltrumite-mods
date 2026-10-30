"""Survey an Anvil world: chunk DataVersions, block names, block entity ids, entity ids."""
import sys, os, json, collections
from multiprocessing import Pool
import nbt

def scan(path):
    dv = collections.Counter(); blocks = collections.Counter(); bes = collections.Counter()
    newblocks = collections.Counter(); status = collections.Counter(); ylo = 99; yhi = -99
    bounds = None
    try:
        for i, ts, raw in nbt.read_region(path):
            _, c = nbt.loads(raw)
            if 'Level' in c:
                dv['legacy-Level:%s' % c.get('DataVersion')] += 1
                continue
            v = int(c.get('DataVersion', 0)); dv[v] += 1
            status[str(c.get('Status'))] += 1
            for s in c.get('sections', []):
                bs = s.get('block_states')
                if not bs: continue
                y = int(s['Y'])
                pal = bs['palette']
                if len(pal) == 1 and pal[0]['Name'] == 'minecraft:air':
                    continue
                ylo = min(ylo, y); yhi = max(yhi, y)
                for p in pal:
                    blocks[p['Name']] += 1
                    if v > 3465: newblocks[p['Name']] += 1
            for be in c.get('block_entities', []):
                bes[str(be.get('id'))] += 1
    except Exception as e:
        return path, None, repr(e)
    return path, dict(dv=dv, blocks=blocks, bes=bes, newblocks=newblocks, status=status, ylo=ylo, yhi=yhi), None

def scan_entities(path):
    ids = collections.Counter(); dv = collections.Counter()
    try:
        for i, ts, raw in nbt.read_region(path):
            _, c = nbt.loads(raw)
            dv[int(c.get('DataVersion', 0))] += 1
            for e in c.get('Entities', []):
                ids[str(e.get('id'))] += 1
    except Exception as e:
        return path, None, repr(e)
    return path, dict(ids=ids, dv=dv), None

if __name__ == '__main__':
    world = sys.argv[1]; out = sys.argv[2]
    regs = [os.path.join(world, 'region', f) for f in os.listdir(os.path.join(world, 'region')) if os.path.getsize(os.path.join(world, 'region', f)) > 0]
    ents = [os.path.join(world, 'entities', f) for f in os.listdir(os.path.join(world, 'entities')) if os.path.getsize(os.path.join(world, 'entities', f)) > 0]
    tot = collections.defaultdict(collections.Counter); errs = []; ylo = 99; yhi = -99; per_region = {}
    with Pool(2) as p:
        for path, r, err in p.imap_unordered(scan, regs, chunksize=4):
            if err: errs.append((path, err)); continue
            per_region[os.path.basename(path)] = {str(k): v for k, v in r['dv'].items()}
            for k in ('dv', 'blocks', 'bes', 'newblocks', 'status'):
                tot[k].update(r[k])
            ylo = min(ylo, r['ylo']); yhi = max(yhi, r['yhi'])
        etot = collections.defaultdict(collections.Counter)
        for path, r, err in p.imap_unordered(scan_entities, ents, chunksize=8):
            if err: errs.append((path, err)); continue
            etot['entity_ids'].update(r['ids']); etot['entity_dv'].update(r['dv'])
    res = {k: {str(a): b for a, b in v.most_common()} for k, v in tot.items()}
    res.update({k: {str(a): b for a, b in v.most_common()} for k, v in etot.items()})
    res['section_y_range'] = [ylo, yhi]; res['errors'] = errs; res['per_region_dv'] = per_region
    json.dump(res, open(out, 'w'), indent=1)
    print('done', len(regs), 'regions', len(errs), 'errors')
