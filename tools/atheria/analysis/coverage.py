"""Coverage validator for the Atheria walk tiles (T18 of reports/city-map.md).

Reads the VCW1 tiles that tools/atheria/walk.py wrote into <world>/viltrumite_city/walk/
(magic 0x56435731, format 1, zlib of a 128-byte chunk bitmap + 256 big-endian u16 cells per
present chunk, index z*16+x — the WalkTile.java format) and checks the coverage against the
urban area. Holes in the walk network leave citizens spawning in the void or off the grid, so
every chunk that the city covers should carry walk cells.

Urban reference, in order of preference (the first available wins):
 1. --profile <profile.json>: the district boxes of the prepared city (walk.py output);
 2. --districts <districts.json>: anchor points, each covers a circle of --anchor-range blocks
    (default 900, the walk.py ANCHOR_RANGE);
 3. --cols <cols dir>: the stage-1 column surfaces; the urban_mask of walk.py is rebuilt per
    tile (URBAN_TILE/URBAN_MIN/URBAN_ROAD/URBAN_GROW as in walk.py, dilated without cv2).

Prints per-region stats, every chunk of the urban area without coverage (chunk coordinates
and the nearest district), and a summary. Exit code 1 if there are uncovered urban chunks.

Usage:
  python3 coverage.py <world dir> [--profile p.json | --districts d.json [--anchor-range N]
                       | --cols dir] [--quiet]
"""
import argparse, json, math, os, re, struct, sys, zlib

MAGIC = 0x56435731
FORMAT = 1
WALK, PREFERRED = 1, 2
CHUNKS = 32          # per region axis
CELLS = 16           # per chunk axis
REGION = CHUNKS * CELLS

# urban_mask constants of walk.py
URBAN_TILE = 16
URBAN_MIN = 0.25
URBAN_GROW = 2
URBAN_ROAD = 0.10
M_NONE, M_ROAD, M_PAVE, M_NATURAL, M_WOOD, M_OTHER, M_MARK = range(7)


def read_tile(path):
    """Parse one VCW1 file: (classes set per chunk {(cz,cx): (walk, preferred)}, cell count)."""
    with open(path, 'rb') as f:
        head = f.read(8)
        if len(head) < 8:
            raise ValueError('file too short')
        magic, fmt = struct.unpack('>ii', head)
        if magic != MAGIC:
            raise ValueError('bad magic 0x%08X' % (magic & 0xFFFFFFFF))
        if fmt != FORMAT:
            raise ValueError('unsupported format %d' % fmt)
        body = zlib.decompress(f.read())
    if len(body) < 128:
        raise ValueError('bitmap truncated')
    bitmap = body[:128]
    chunks = {}
    off = 128
    cells = 0
    for i in range(CHUNKS * CHUNKS):
        if (bitmap[i >> 3] >> (i & 7) & 1) == 0:
            continue
        if off + 2 * CELLS * CELLS > len(body):
            raise ValueError('body truncated at chunk %d' % i)
        walk = preferred = 0
        for j in range(CELLS * CELLS):
            v = struct.unpack_from('>H', body, off + 2 * j)[0]
            c = (v >> 13) & 3
            if v != 0 and c:
                if c == PREFERRED:
                    preferred += 1
                else:
                    walk += 1
        chunks[(i // CHUNKS, i % CHUNKS)] = (walk, preferred)
        off += 2 * CELLS * CELLS
        cells += walk + preferred
    return chunks, cells


def region_files(walk_dir):
    out = {}
    for name in os.listdir(walk_dir):
        m = re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.bin', name)
        if m:
            out[(int(m.group(1)), int(m.group(2)))] = os.path.join(walk_dir, name)
    return out


def profile_boxes(path):
    """District boxes of the prepared profile.json: [(name, minX, minZ, maxX, maxZ)]."""
    prof = json.load(open(path))
    out = []
    for d in prof.get('districts', []):
        out.append((d['name'], d['minX'], d['minZ'], d['maxX'], d['maxZ']))
    return out, prof.get('id', ''), prof.get('mapVersion', '')


def anchor_boxes(path, rng):
    """Anchor circles as boxes: every anchor covers [-rng; +rng]."""
    out = []
    for a in json.load(open(path)):
        out.append((a['name'], a['x'] - rng, a['z'] - rng, a['x'] + rng, a['z'] + rng))
    return out, '', ''


def nearest(name_boxes, x, z):
    """Name of the box whose centre is nearest to the point (for the report)."""
    best, name = None, '?'
    for n, x0, z0, x1, z1 in name_boxes:
        d = ((x0 + x1) / 2.0 - x) ** 2 + ((z0 + z1) / 2.0 - z) ** 2
        if best is None or d < best:
            best, name = d, n
    return name


def box_chunks(name_boxes):
    """Chunks (world chunk coords) covered by the boxes: {(cx, cz): district}."""
    out = {}
    for n, x0, z0, x1, z1 in name_boxes:
        for cx in range(x0 >> 4, (x1 >> 4) + 1):
            for cz in range(z0 >> 4, (z1 >> 4) + 1):
                out.setdefault((cx, cz), n)
    return out


def dilate(mask):
    """8-neighbour dilation of a set of (tx, tz) tiles (cv2-free stand-in for walk.py)."""
    grown = set(mask)
    for (tx, tz) in mask:
        for dz in (-1, 0, 1):
            for dx in (-1, 0, 1):
                grown.add((tx + dx, tz + dz))
    return grown


def urban_mask_from_cols(cols_dir, rx, rz):
    """Rebuild walk.py's urban mask for one region from the stage-1 column surfaces.

    Returns the set of 32x32-chunk keys of the region that hold urban blocks.
    """
    try:
        import numpy as np
    except ImportError:
        raise SystemExit('--cols needs numpy (walk.py stage 1 output)')
    f = os.path.join(cols_dir, 'r.%d.%d.npz' % (rx, rz))
    if not os.path.exists(f):
        return set()
    M = np.load(f)['mat']
    n = REGION // URBAN_TILE
    Mt = M[:n * URBAN_TILE, :n * URBAN_TILE].reshape(n, URBAN_TILE, n, URBAN_TILE)
    built = np.isin(Mt, (M_ROAD, M_PAVE, M_MARK)).mean((1, 3)) >= URBAN_MIN
    road = np.isin(Mt, (M_ROAD, M_MARK)).mean((1, 3)) >= URBAN_ROAD
    road_grown = dilate({(int(z), int(x)) for z, x in zip(*road.nonzero())})
    built &= np.array([[1 if (tz, tx) in road_grown else 0 for tx in range(n)] for tz in range(n)], bool)
    grown = dilate({(int(z), int(x)) for z, x in zip(*(built.nonzero()))})
    chunks = set()
    for (tz, tx) in grown:
        chunks.add((tz * URBAN_TILE // CELLS, tx * URBAN_TILE // CELLS))
    return chunks


def main():
    ap = argparse.ArgumentParser(description=(__doc__ or 'walk tile coverage validator').splitlines()[0])
    ap.add_argument('world', help='world folder (viltrumite_city/walk inside)')
    src = ap.add_mutually_exclusive_group()
    src.add_argument('--profile', help='profile.json (district boxes)')
    src.add_argument('--districts', help='districts.json (anchor circles)')
    src.add_argument('--cols', help='stage-1 cols dir (rebuild urban_mask)')
    ap.add_argument('--anchor-range', type=int, default=900, help='anchor reach in blocks (default 900)')
    ap.add_argument('--quiet', action='store_true', help='only the summary')
    a = ap.parse_args()

    walk_dir = os.path.join(a.world, 'viltrumite_city', 'walk')
    if not os.path.isdir(walk_dir):
        raise SystemExit('no walk folder: %s' % walk_dir)
    if not (a.profile or a.districts or a.cols):
        raise SystemExit('one of --profile / --districts / --cols is required')

    files = region_files(walk_dir)
    if not files:
        raise SystemExit('no walk tiles in %s' % walk_dir)

    if a.profile:
        boxes, city, ver = profile_boxes(a.profile)
    elif a.districts:
        boxes, city, ver = anchor_boxes(a.districts, a.anchor_range)
    else:
        boxes, city, ver = [], '', ''

    # chunks that must be covered (urban reference)
    if boxes:
        want_chunks = box_chunks(boxes)
        ref = 'districts: %d boxes' % len(boxes)
    else:
        want_chunks = {}
        ref = 'urban_mask from cols'
        for (rx, rz) in files:
            for (cz, cx) in urban_mask_from_cols(a.cols, rx, rz):
                want_chunks[(rx * CHUNKS + cx, rz * CHUNKS + cz)] = 'urban'

    # read every tile, collect coverage
    covered = {}      # world chunk -> (walk, preferred)
    total_cells = 0
    for (rx, rz), path in sorted(files.items()):
        try:
            chunks, cells = read_tile(path)
        except (OSError, ValueError, zlib.error) as e:
            print('ERROR r.%d.%d: %s' % (rx, rz, e), file=sys.stderr)
            continue
        total_cells += cells
        for (cz, cx), (w, p) in chunks.items():
            if w + p:
                covered[(rx * CHUNKS + cx, rz * CHUNKS + cz)] = (w, p)

    missing = sorted(c for c in want_chunks if c not in covered)
    if not a.quiet:
        print('walk tiles: %d regions, %d covered chunks, %d walk cells' % (len(files), len(covered), total_cells))
        print('urban reference: %s, %d chunks to cover' % (ref, len(want_chunks)))
        for (cx, cz) in missing:
            print('no coverage: chunk %d %d (%s)' % (cx, cz, nearest(boxes, (cx << 4) + 8, (cz << 4) + 8) if boxes else 'urban'))
    if boxes and city:
        print('city %s %s' % (city, ver))
    share = 1.0 - len(missing) / len(want_chunks) if want_chunks else 1.0
    print('coverage: %d/%d chunks (%.1f%%), %d missing' % (len(want_chunks) - len(missing), len(want_chunks), share * 100.0, len(missing)))
    if missing:
        sys.exit(1)


if __name__ == '__main__':
    main()
