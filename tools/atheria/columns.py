"""Stage 1 of the Atheria pedestrian analysis: per-column outdoor surface extraction.

For every column of every full chunk we find the topmost standable surface:
 - scanning down from the top, "soft" blocks (leaves, panes, bars, fences, lamps, vines...)
   and passable blocks (air, plants, torches, signs, carpets...) are skipped;
 - the first hard block decides: a floor block (full top or bottom slab) whose two blocks
   above are passable is a surface; liquids, hazards and anything else reject the column.
Output per region: <out>/r.X.Z.npz with arrays h2 (int16, surface height in half blocks,
-32768 = none) and mat (uint8 material class of the floor block), 512x512 indexed [z, x].
"""
import os, sys, re, json, numpy as np
from multiprocessing import Pool
import nbt

PASS, FLOOR, HALF, SOFT, LIQUID, HAZARD, OTHER = range(7)
# floor materials
M_NONE, M_ROAD, M_PAVE, M_NATURAL, M_WOOD, M_OTHER, M_MARK = range(7)

PASSABLE = re.compile(r'(^air$|cave_air|void_air|^grass$|fern$|tall_grass|large_fern|dead_bush|seagrass|flower|tulip|'
    r'poppy|dandelion|orchid|allium|azure_bluet|oxeye|cornflower|lily_of|wither_rose|sunflower|lilac|peony|rose_bush|'
    r'pink_petals|torch|sign|banner|button|lever|pressure_plate|rail|carpet|snow$|redstone_wire|tripwire|sapling|'
    r'mushroom$|sugar_cane|vine|glow_lichen|sculk_vein|nether_sprouts|roots$|^light$|structure_void|skull|head$|'
    r'flower_pot|candle|frogspawn|moss_carpet|spore_blossom|hanging_roots|pointed_dripstone|small_dripleaf|'
    r'big_dripleaf_stem|cobweb|string|comparator|repeater|daylight_detector|wheat|carrots|potatoes|beetroots|'
    r'item_frame|ladder|scaffolding|lily_pad|end_rod|lightning_rod|amethyst_cluster|bud$|coral(?!_block)|'
    r'kelp|pickle|frog|turtle_egg|sniffer_egg|bamboo_sapling|torchflower|pitcher|cave_vines|weeping|twisting|'
    r'nether_wart|chain$|tripwire_hook|brushable)')
SOFTRE = re.compile(r'(leaves|glass_pane|iron_bars|fence|_wall$|^wall|lantern|bell|cauldron|anvil|brewing_stand|'
    r'grindstone|lectern|enchanting|hopper|chorus|bamboo$|cactus|azalea|dripleaf|_door$|trapdoor|bed$|chest|'
    r'conduit|beacon|end_portal_frame|stonecutter|composter|decorated_pot|bamboo|mangrove_roots|campfire|'
    r'_sign$|barrier|banner)')
LIQ = re.compile(r'(^water$|^lava$|bubble_column|kelp_plant)')
HAZ = re.compile(r'(fire$|magma|cactus|sweet_berry|powder_snow|lava_cauldron|wither_rose|campfire|sculk_sensor|'
    r'sculk_shrieker|pointed_dripstone|end_portal$|nether_portal|end_gateway)')
# gray_* entries must not swallow the light_gray_* pavement blocks (PAVE lists light_gray_concrete)
ROAD = re.compile(r'(black_concrete$|(?<!light_)gray_concrete$|black_terracotta|(?<!light_)gray_terracotta|coal_block|black_wool|(?<!light_)gray_wool|'
    r'polished_blackstone$|blackstone$|basalt|deepslate_tiles|polished_deepslate$|cobbled_deepslate$|'
    r'black_concrete_powder|(?<!light_)gray_concrete_powder|tuff$|(?<!light_)gray_glazed|black_glazed|^deepslate$|smooth_basalt|'
    r'polished_blackstone_slab|blackstone_slab|deepslate_tile_slab|polished_deepslate_slab|cobbled_deepslate_slab)')
MARK = re.compile(r'(white_concrete$|yellow_concrete$|yellow_terracotta|white_terracotta|yellow_wool|white_wool|quartz_slab|smooth_quartz_slab)')
PAVE = re.compile(r'(smooth_stone|stone_brick|light_gray_concrete|andesite|diorite|granite|^stone$|stone_slab|'
    r'cobblestone|bricks?$|brick_slab|sandstone|quartz|calcite|terracotta|concrete|prismarine|mud_brick|'
    r'tuff|packed_mud|nether_brick|end_stone_brick|purpur|copper|blackstone_brick|deepslate_brick|stone_stairs|'
    r'_stairs$|polished)')
NATURAL = re.compile(r'(grass_block|dirt|podzol|mycelium|sand$|red_sand$|gravel|snow_block|^snow$|moss_block|clay|'
    r'farmland|dirt_path|mud$|rooted_dirt|coarse_dirt|stone$|ice$|packed_ice|blue_ice|suspicious)')
WOOD = re.compile(r'(planks|_log$|_wood$|stripped|bamboo_mosaic|bamboo_block|_slab$)')

_cls_cache = {}

def classify(state):
    name = str(state['Name']).replace('minecraft:', '')
    props = state.get('Properties') or {}
    key = (name, props.get('type'), props.get('half'))
    r = _cls_cache.get(key)
    if r is not None:
        return r
    if LIQ.search(name): c = LIQUID
    elif HAZ.search(name): c = HAZARD
    elif PASSABLE.search(name) and not name.endswith('_block') and not name.endswith('_slab'): c = PASS
    elif SOFTRE.search(name): c = SOFT
    elif name.endswith('_slab'):
        c = HALF if props.get('type') == 'bottom' else FLOOR
    elif name.endswith('_stairs'):
        c = FLOOR
    elif name in ('farmland', 'dirt_path', 'soul_sand', 'mud', 'honey_block', 'chorus_flower'):
        c = FLOOR
    elif re.search(r'(slime_block|honey_block|scaffolding|piston_head|moving_piston|spawner|daylight)', name):
        c = OTHER
    else:
        c = FLOOR
    if MARK.search(name): m = M_MARK
    elif ROAD.search(name): m = M_ROAD
    elif NATURAL.search(name): m = M_NATURAL
    elif WOOD.search(name): m = M_WOOD
    elif PAVE.search(name): m = M_PAVE
    else: m = M_OTHER
    r = (c, m)
    _cls_cache[key] = r
    return r

MINY = -64

def chunk_columns(c):
    """Return (h2[16,16] int16, mat[16,16] uint8) for one chunk (indexed [z, x])."""
    secs = []
    for s in c.get('sections', []):
        bs = s.get('block_states')
        if not bs or 'palette' not in bs:
            continue
        y = int(s['Y'])
        pal = bs['palette']
        if len(pal) == 1 and str(pal[0]['Name']) in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'):
            continue
        cl = np.array([classify(p) for p in pal], dtype=np.uint8)
        if len(pal) == 1:
            idx = np.zeros(4096, dtype=np.int64)
        else:
            bits = max(4, (len(pal) - 1).bit_length())
            per = 64 // bits
            arr = np.frombuffer(bs['data'].raw, dtype='>u8').astype(np.uint64)
            sh = (np.arange(per, dtype=np.uint64) * np.uint64(bits))
            vals = ((arr[:, None] >> sh[None, :]) & np.uint64((1 << bits) - 1)).reshape(-1)[:4096].astype(np.int64)
            idx = np.minimum(vals, len(pal) - 1)
        secs.append((y, cl[idx][:, 0].reshape(16, 16, 16), cl[idx][:, 1].reshape(16, 16, 16)))
    h2 = np.full((16, 16), -32768, dtype=np.int16)
    mat = np.zeros((16, 16), dtype=np.uint8)
    if not secs:
        return h2, mat
    secs.sort(key=lambda t: t[0])
    y0 = secs[0][0]; y1 = secs[-1][0]
    H = (y1 - y0 + 1) * 16 + 2
    C = np.zeros((H, 16, 16), dtype=np.uint8)  # PASS by default (missing sections are air)
    M = np.zeros((H, 16, 16), dtype=np.uint8)
    for y, cs, ms in secs:
        o = (y - y0) * 16
        C[o:o + 16] = cs; M[o:o + 16] = ms
    hard = (C == FLOOR) | (C == HALF) | (C == LIQUID) | (C == HAZARD) | (C == OTHER)
    # topmost hard block per column
    rev = hard[::-1]
    anyh = rev.any(axis=0)
    top = H - 1 - rev.argmax(axis=0)
    zz, xx = np.nonzero(anyh)
    for z, x in zip(zz, xx):
        t = top[z, x]
        k = C[t, z, x]
        if k != FLOOR and k != HALF:
            continue
        if C[t + 1, z, x] != PASS or C[t + 2, z, x] != PASS:
            continue
        wy = y0 * 16 + t
        h2[z, x] = 2 * wy + (1 if k == HALF else 2)
        mat[z, x] = M[t, z, x]
    return h2, mat

def region(args):
    path, out = args
    m = re.search(r'r\.(-?\d+)\.(-?\d+)\.mca$', path)
    rx, rz = int(m.group(1)), int(m.group(2))
    H2 = np.full((512, 512), -32768, dtype=np.int16)
    MAT = np.zeros((512, 512), dtype=np.uint8)
    present = np.zeros((32, 32), dtype=bool)
    n = 0
    for i, ts, raw in nbt.read_region(path):
        _, c = nbt.loads(raw)
        st = str(c.get('Status', ''))
        if st not in ('full', 'minecraft:full'):
            continue
        cx, cz = i & 31, i >> 5
        h2, mat = chunk_columns(c)
        H2[cz * 16:cz * 16 + 16, cx * 16:cx * 16 + 16] = h2
        MAT[cz * 16:cz * 16 + 16, cx * 16:cx * 16 + 16] = mat
        present[cz, cx] = True
        n += 1
    if n:
        np.savez_compressed(os.path.join(out, 'r.%d.%d.npz' % (rx, rz)), h2=H2, mat=MAT, present=present)
    return path, n

if __name__ == '__main__':
    world, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    rd = os.path.join(world, 'region')
    tasks = [(os.path.join(rd, f), out) for f in sorted(os.listdir(rd)) if f.endswith('.mca') and os.path.getsize(os.path.join(rd, f)) >= 8192]
    if len(sys.argv) > 3:
        tasks = [t for t in tasks if re.search(sys.argv[3], t[0])]
    tot = 0
    with Pool(2) as p:
        for k, (path, n) in enumerate(p.imap_unordered(region, tasks)):
            tot += n
            if k % 50 == 0: print(k, len(tasks), tot, flush=True)
    print('done', tot, 'chunks')
