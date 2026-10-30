"""Stage 2 of the Atheria pedestrian analysis: street network, walk tiles and the city profile.

Input: the per-region column surfaces of stage 1 (columns.py: h2 = standing height in half
blocks, mat = floor material class). Output, inside the prepared world folder:
  viltrumite_city/walk/r.X.Z.bin  pedestrian cells in the WalkTile format (see WalkTile.java)
  viltrumite_city/profile.json    arena, districts, population defaults, statistics

Algorithm (per region, with a 64-block halo from the neighbour regions):
 1. local ground level = low percentile of the surface heights in a 96x96 window;
 2. seeds = outdoor surfaces within -1..+1.5 blocks of the local ground (streets, plazas,
    parks; roofs, wall tops and terraces are far above it);
 3. flood fill from the seeds across steps of at most one block, so ramps, stairs and
    hills stay connected while roofs, separated by walls, never join;
 4. small isolated pieces (yards, pockets between walls) are dropped;
 5. class: roads and road markings are WALK (crossable), everything else is PREFERRED
    (sidewalks, plazas, parks: spawn and stroll targets).
Water, lava, fire, magma and every column without two blocks of headroom were already
rejected by stage 1. Material is only a hint for the class, the geometry decides.

Usage: python3 walk.py <cols dir> <world dir> [--name Atheria --version v2.06]
"""
import argparse, json, os, re, struct, sys, zlib, glob, collections
import numpy as np
import cv2

NONE = -32768
M_NONE, M_ROAD, M_PAVE, M_NATURAL, M_WOOD, M_OTHER, M_MARK = range(7)
WALK, PREFERRED = 1, 2
HALO = 64
GROUND_WIN = 96          # blocks
GROUND_PCT = 20          # percentile of surface heights that is "the street"
SEED_LO, SEED_HI = -2, 3  # half blocks around the local ground (-1 .. +1.5 blocks)
STEP = 2                 # max step between neighbour cells, half blocks (one block)
MIN_PIECE = 48           # cells

def load(cols, rx, rz):
    f = os.path.join(cols, 'r.%d.%d.npz' % (rx, rz))
    if not os.path.exists(f):
        return None
    d = np.load(f)
    return d['h2'], d['mat']

def window(cols, rx, rz, cache):
    """h2/mat of the region plus a HALO-wide border from its neighbours."""
    S = 512 + 2 * HALO
    H = np.full((S, S), NONE, np.int16); M = np.zeros((S, S), np.uint8)
    for dz in (-1, 0, 1):
        for dx in (-1, 0, 1):
            k = (rx + dx, rz + dz)
            if k not in cache:
                cache[k] = load(cols, *k)
            d = cache[k]
            if d is None:
                continue
            # source rectangle of the neighbour that falls into the window
            x0 = 0 if dx == 0 else (512 - HALO if dx < 0 else 0)
            x1 = 512 if dx == 0 else (512 if dx < 0 else HALO)
            z0 = 0 if dz == 0 else (512 - HALO if dz < 0 else 0)
            z1 = 512 if dz == 0 else (512 if dz < 0 else HALO)
            wx = HALO + dx * 512 + x0; wz = HALO + dz * 512 + z0
            H[wz:wz + (z1 - z0), wx:wx + (x1 - x0)] = d[0][z0:z1, x0:x1]
            M[wz:wz + (z1 - z0), wx:wx + (x1 - x0)] = d[1][z0:z1, x0:x1]
    return H, M

def ground_level(H):
    """Local street height (half blocks) per cell from a coarse percentile grid."""
    S = H.shape[0]; T = 16
    n = S // T
    g = np.full((n, n), np.nan, np.float32)
    r = GROUND_WIN // T // 2
    valid = H != NONE
    for tz in range(n):
        for tx in range(n):
            z0 = max(0, (tz - r) * T); z1 = min(S, (tz + r + 1) * T)
            x0 = max(0, (tx - r) * T); x1 = min(S, (tx + r + 1) * T)
            blk = H[z0:z1, x0:x1][valid[z0:z1, x0:x1]]
            if blk.size >= 64:
                g[tz, tx] = np.percentile(blk, GROUND_PCT)
    g = np.repeat(np.repeat(g, T, 0), T, 1)
    return g

def network(H, M):
    valid = H != NONE
    g = ground_level(H)
    d = H.astype(np.float32) - g
    seeds = valid & ~np.isnan(g) & (d >= SEED_LO) & (d <= SEED_HI)
    reach = seeds.copy()
    Hi = H.astype(np.int32)
    for it in range(400):
        grow = np.zeros_like(reach)
        for sz, sx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            src = np.roll(reach, (sz, sx), (0, 1))
            hs = np.roll(Hi, (sz, sx), (0, 1))
            grow |= src & valid & (np.abs(Hi - hs) <= STEP)
        grow &= ~reach
        # never border-wrap
        grow[0, :] = grow[-1, :] = False; grow[:, 0] = grow[:, -1] = False
        if not grow.any():
            break
        reach |= grow
    # small pieces out (4-connectivity on cells that also connect by step height)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(reach.astype(np.uint8), connectivity=4)
    small = stats[:, cv2.CC_STAT_AREA] < MIN_PIECE
    small[0] = False
    reach &= ~small[lab]
    cls = np.where((M == M_ROAD) | (M == M_MARK), WALK, PREFERRED).astype(np.int32)
    cls[~reach] = 0
    return cls

def write_tile(path, H, C):
    """H, C: 512x512 [z, x] arrays (region local)."""
    bitmap = bytearray(128); body = bytearray()
    vals = np.where(C > 0, (H.astype(np.int32) + 512) | (C << 13), 0).astype(np.uint16)
    cells = 0
    for cz in range(32):
        for cx in range(32):
            blk = vals[cz * 16:cz * 16 + 16, cx * 16:cx * 16 + 16]
            if not blk.any():
                continue
            i = cz * 32 + cx
            bitmap[i >> 3] |= 1 << (i & 7)
            body += blk.astype('>u2').tobytes()  # row-major [z][x] = index z*16+x
            cells += int((blk != 0).sum())
    if not cells:
        return 0, 0
    with open(path, 'wb') as f:
        f.write(struct.pack('>ii', 0x56435731, 1))
        f.write(zlib.compress(bytes(bitmap) + bytes(body), 6))
    return cells, int((C == PREFERRED).sum())

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('cols'); ap.add_argument('world')
    ap.add_argument('--name', default='Atheria'); ap.add_argument('--id', default='atheria')
    ap.add_argument('--version', default='v2.06')
    ap.add_argument('--districts', default=None, help='JSON list of {name, x, z} district anchors')
    a = ap.parse_args()
    out = os.path.join(a.world, 'viltrumite_city'); wd = os.path.join(out, 'walk')
    os.makedirs(wd, exist_ok=True)
    for f in glob.glob(os.path.join(wd, '*.bin')):
        os.remove(f)
    regs = sorted(tuple(map(int, re.search(r'r\.(-?\d+)\.(-?\d+)\.npz', f).groups())) for f in glob.glob(os.path.join(a.cols, 'r.*.npz')))
    cache = {}; total = 0; pref = 0
    density = {}  # 128x128 cell -> walk cells
    bounds = [10 ** 9, 10 ** 9, -10 ** 9, -10 ** 9]
    for k, (rx, rz) in enumerate(regs):
        # keep the cache to the 3x3 neighbourhood of the current row
        for key in [key for key in cache if abs(key[1] - rz) > 1]:
            del cache[key]
        H, M = window(a.cols, rx, rz, cache)
        C = network(H, M)[HALO:HALO + 512, HALO:HALO + 512]
        Hr = H[HALO:HALO + 512, HALO:HALO + 512]
        n, p = write_tile(os.path.join(wd, 'r.%d.%d.bin' % (rx, rz)), Hr, C)
        total += n; pref += p
        if n:
            zz, xx = np.nonzero(C)
            bx, bz = rx * 512, rz * 512
            bounds = [min(bounds[0], bx + xx.min()), min(bounds[1], bz + zz.min()), max(bounds[2], bx + xx.max()), max(bounds[3], bz + zz.max())]
            for q, cnt in collections.Counter(zip((bx + xx) >> 7, (bz + zz) >> 7)).items():
                density[q] = cnt
        if k % 25 == 0:
            print('%d/%d regions, %d cells' % (k, len(regs), total), flush=True)
    # arena: every region that has chunks (the whole map), full build height
    present = [tuple(map(int, re.search(r'r\.(-?\d+)\.(-?\d+)\.mca', f).groups()))
               for f in glob.glob(os.path.join(a.world, 'region', 'r.*.mca')) if os.path.getsize(f) >= 8192]
    ax0 = min(r[0] for r in present) * 512; az0 = min(r[1] for r in present) * 512
    ax1 = max(r[0] for r in present) * 512 + 511; az1 = max(r[1] for r in present) * 512 + 511
    districts = make_districts(density, a.districts)
    prof = {
        'format': 1, 'id': a.id, 'name': a.name, 'mapVersion': a.version, 'dimension': 'minecraft:overworld',
        'arena': {'minX': ax0, 'minY': -64, 'minZ': az0, 'maxX': ax1, 'maxY': 1023, 'maxZ': az1},
        'districts': districts,
        'population': {'density': 1.0, 'perPlayer': 40, 'maxTotal': 160},
        'stats': {'walkCells': total, 'preferredCells': pref, 'walkBounds': bounds},
        'generator': 'tools/atheria/walk.py',
    }
    json.dump(prof, open(os.path.join(out, 'profile.json'), 'w'), indent=1)
    print('done: %d cells (%d preferred), %d districts' % (total, pref, len(districts)))

def make_districts(density, anchors_file):
    """Districts = connected groups of dense 128x128 blocks; named after the nearest anchor."""
    if not density:
        return []
    ks = np.array(list(density.keys())); x0, z0 = ks.min(0); x1, z1 = ks.max(0)
    grid = np.zeros((z1 - z0 + 1, x1 - x0 + 1), np.int32)
    for (qx, qz), c in density.items():
        grid[qz - z0, qx - x0] = c
    dense = (grid >= 1500).astype(np.uint8)
    dense = cv2.dilate(dense, np.ones((3, 3), np.uint8))
    n, lab, stats, _ = cv2.connectedComponentsWithStats(dense, connectivity=8)
    anchors = json.load(open(anchors_file)) if anchors_file else []
    out = []
    for i in range(1, n):
        zz, xx = np.nonzero((lab == i) & (grid > 0))
        if len(zz) < 4:
            continue
        cells = int(grid[zz, xx].sum())
        bx0 = int((xx.min() + x0) * 128); bz0 = int((zz.min() + z0) * 128)
        bx1 = int((xx.max() + x0) * 128 + 127); bz1 = int((zz.max() + z0) * 128 + 127)
        cx = (bx0 + bx1) / 2; cz = (bz0 + bz1) / 2
        name = None; best = 1e18
        for an in anchors:
            if bx0 - 64 <= an['x'] <= bx1 + 64 and bz0 - 64 <= an['z'] <= bz1 + 64:
                dd = (an['x'] - cx) ** 2 + (an['z'] - cz) ** 2
                if dd < best:
                    best = dd; name = an['name']
        out.append({'name': name, 'minX': bx0, 'minZ': bz0, 'maxX': bx1, 'maxZ': bz1, 'walkCells': cells})
    out.sort(key=lambda d: -d['walkCells'])
    seen = collections.Counter()
    for i, d in enumerate(out):
        base = d['name'] or 'District %d' % (i + 1)
        seen[base] += 1
        d['name'] = base if seen[base] == 1 else '%s %d' % (base, seen[base])
    return out

if __name__ == '__main__':
    main()
