"""Downgrade a Minecraft 1.21.x Anvil world (Atheria v2.06) to 1.20.1 (DataVersion 3465).

Chunks/entities at DataVersion <= 3465 are copied byte for byte (vanilla 1.20.1 upgrades
them itself). Newer chunks are rewritten: blocks that do not exist in 1.20.1 are mapped to
the closest 1.20.1 block (table below), block states are fitted to the 1.20.1 property
schema, block entity / item / text component formats are converted back to their
1.20.1 shape and DataVersion is set to 3465 so DataFixerUpper never touches them.

Usage: python3 downgrade.py <src world> <dst world> --reports <1.20.1 generated/reports>
"""
import os, sys, json, re, shutil, argparse, collections, time
from multiprocessing import Pool
import nbt
from nbt import Compound, List, Byte, Short, Int, Long, Float, Double, IntArray, LongArray, STRING, COMPOUND

TARGET_DV = 3465
COMPONENT_NBT_DV = 4325  # 1.21.5: text components stored as NBT instead of JSON strings

# Blocks that do not exist in 1.20.1 -> (replacement, forced properties)
BLOCK_MAP = {
    'minecraft:short_grass': ('minecraft:grass', {}),
    'minecraft:iron_chain': ('minecraft:chain', {}),
    'minecraft:polished_tuff': ('minecraft:tuff', {}),
    'minecraft:chiseled_tuff': ('minecraft:tuff', {}),
    'minecraft:tuff_bricks': ('minecraft:tuff', {}),
    'minecraft:chiseled_tuff_bricks': ('minecraft:tuff', {}),
    'minecraft:polished_tuff_wall': ('minecraft:andesite_wall', {}),
    'minecraft:tuff_wall': ('minecraft:andesite_wall', {}),
    'minecraft:tuff_brick_wall': ('minecraft:andesite_wall', {}),
    'minecraft:polished_tuff_stairs': ('minecraft:polished_andesite_stairs', {}),
    'minecraft:tuff_stairs': ('minecraft:andesite_stairs', {}),
    'minecraft:tuff_brick_stairs': ('minecraft:polished_andesite_stairs', {}),
    'minecraft:polished_tuff_slab': ('minecraft:polished_andesite_slab', {}),
    'minecraft:tuff_slab': ('minecraft:andesite_slab', {}),
    'minecraft:tuff_brick_slab': ('minecraft:polished_andesite_slab', {}),
    'minecraft:pale_oak_planks': ('minecraft:birch_planks', {}),
    'minecraft:pale_oak_log': ('minecraft:birch_log', {}),
    'minecraft:pale_oak_wood': ('minecraft:birch_wood', {}),
    'minecraft:stripped_pale_oak_log': ('minecraft:stripped_birch_log', {}),
    'minecraft:stripped_pale_oak_wood': ('minecraft:stripped_birch_wood', {}),
    'minecraft:pale_oak_fence': ('minecraft:birch_fence', {}),
    'minecraft:pale_oak_fence_gate': ('minecraft:birch_fence_gate', {}),
    'minecraft:pale_oak_stairs': ('minecraft:birch_stairs', {}),
    'minecraft:pale_oak_slab': ('minecraft:birch_slab', {}),
    'minecraft:pale_oak_trapdoor': ('minecraft:birch_trapdoor', {}),
    'minecraft:pale_oak_door': ('minecraft:birch_door', {}),
    'minecraft:pale_oak_button': ('minecraft:birch_button', {}),
    'minecraft:pale_oak_pressure_plate': ('minecraft:birch_pressure_plate', {}),
    'minecraft:pale_oak_sign': ('minecraft:birch_sign', {}),
    'minecraft:pale_oak_wall_sign': ('minecraft:birch_wall_sign', {}),
    'minecraft:pale_oak_hanging_sign': ('minecraft:birch_hanging_sign', {}),
    'minecraft:pale_oak_wall_hanging_sign': ('minecraft:birch_wall_hanging_sign', {}),
    'minecraft:pale_oak_leaves': ('minecraft:birch_leaves', {}),
    'minecraft:pale_oak_sapling': ('minecraft:birch_sapling', {}),
    'minecraft:pale_moss_block': ('minecraft:moss_block', {}),
    'minecraft:pale_moss_carpet': ('minecraft:moss_carpet', {}),
    'minecraft:pale_hanging_moss': ('minecraft:air', {}),
    'minecraft:leaf_litter': ('minecraft:air', {}),
    'minecraft:resin_bricks': ('minecraft:bricks', {}),
    'minecraft:chiseled_resin_bricks': ('minecraft:bricks', {}),
    'minecraft:resin_brick_wall': ('minecraft:brick_wall', {}),
    'minecraft:resin_brick_slab': ('minecraft:brick_slab', {}),
    'minecraft:resin_brick_stairs': ('minecraft:brick_stairs', {}),
    'minecraft:resin_block': ('minecraft:orange_terracotta', {}),
    'minecraft:resin_clump': ('minecraft:air', {}),
    'minecraft:copper_lantern': ('minecraft:lantern', {}),
    'minecraft:exposed_copper_lantern': ('minecraft:lantern', {}),
    'minecraft:weathered_copper_lantern': ('minecraft:lantern', {}),
    'minecraft:oxidized_copper_lantern': ('minecraft:lantern', {}),
    'minecraft:copper_bars': ('minecraft:iron_bars', {}),
    'minecraft:copper_chain': ('minecraft:chain', {}),
    'minecraft:copper_torch': ('minecraft:torch', {}),
    'minecraft:copper_wall_torch': ('minecraft:wall_torch', {}),
    'minecraft:lightning_rod': ('minecraft:lightning_rod', {}),
    'minecraft:copper_trapdoor': ('minecraft:iron_trapdoor', {}),
    'minecraft:copper_door': ('minecraft:iron_door', {}),
    'minecraft:copper_grate': ('minecraft:iron_bars', {}),
    'minecraft:copper_bulb': ('minecraft:redstone_lamp', {}),
    'minecraft:chiseled_copper': ('minecraft:cut_copper', {}),
    'minecraft:exposed_chiseled_copper': ('minecraft:exposed_cut_copper', {}),
    'minecraft:weathered_chiseled_copper': ('minecraft:weathered_cut_copper', {}),
    'minecraft:oxidized_chiseled_copper': ('minecraft:oxidized_cut_copper', {}),
    'minecraft:copper_golem_statue': ('minecraft:air', {}),
    'minecraft:copper_chest': ('minecraft:chest', {}),
    'minecraft:crafter': ('minecraft:dropper', {}),
    'minecraft:heavy_core': ('minecraft:air', {}),
    'minecraft:vault': ('minecraft:spawner', {}),
    'minecraft:trial_spawner': ('minecraft:spawner', {}),
    'minecraft:creaking_heart': ('minecraft:birch_log', {}),
    'minecraft:open_eyeblossom': ('minecraft:oxeye_daisy', {}),
    'minecraft:closed_eyeblossom': ('minecraft:oxeye_daisy', {}),
    'minecraft:wildflowers': ('minecraft:pink_petals', {}),
    'minecraft:bush': ('minecraft:fern', {}),
    'minecraft:firefly_bush': ('minecraft:fern', {}),
    'minecraft:short_dry_grass': ('minecraft:dead_bush', {}),
    'minecraft:tall_dry_grass': ('minecraft:dead_bush', {}),
    'minecraft:cactus_flower': ('minecraft:air', {}),
    'minecraft:dried_ghast': ('minecraft:air', {}),
    'minecraft:test_block': ('minecraft:air', {}),
    'minecraft:test_instance_block': ('minecraft:air', {}),
}
SHELF_RE = re.compile(r'^minecraft:(\w+)_shelf$')
WOODS = {'oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'bamboo', 'crimson', 'warped'}
OXI = re.compile(r'^minecraft:(waxed_)?(exposed_|weathered_|oxidized_)?(.+)$')

def map_block_name(name):
    """Return (name, forced_props) for a 1.21 block name."""
    if name in BLOCK_MAP:
        return BLOCK_MAP[name]
    m = SHELF_RE.match(name)
    if m:
        wood = m.group(1)
        wood = 'birch' if wood == 'pale_oak' else wood
        if wood not in WOODS:
            wood = 'oak'
        # a shelf is a board on the wall: an open trapdoor against the back wall
        return ('minecraft:%s_trapdoor' % wood, {'open': 'true', 'half': 'top'})
    m = OXI.match(name)
    if m and (m.group(1) or m.group(2)):
        base = 'minecraft:' + m.group(3)
        if base in BLOCK_MAP:
            return BLOCK_MAP[base]
    return (name, {})

class Schema:
    def __init__(self, reports):
        blocks = json.load(open(os.path.join(reports, 'blocks.json')))
        self.blocks = {}
        for name, b in blocks.items():
            props = b.get('properties', {})
            default = {}
            for st in b['states']:
                if st.get('default'):
                    default = st.get('properties', {})
            self.blocks[name] = (props, default)
        reg = json.load(open(os.path.join(reports, 'registries.json')))
        self.items = set(reg['minecraft:item']['entries'].keys())
        self.effects = {k: v['protocol_id'] for k, v in reg['minecraft:mob_effect']['entries'].items()}
        self.biomes = None

S = None  # schema, set per worker
STATS = None

def fit_state(state):
    """Fit one palette entry to 1.20.1. Returns a new Compound."""
    name = str(state.get('Name'))
    props = state.get('Properties') or {}
    new, forced = map_block_name(name)
    if new not in S.blocks:
        STATS['unmapped:' + name] += 1
        new, forced = 'minecraft:air', {}
    if new != name:
        STATS['mapped:%s->%s' % (name, new)] += 1
    schema, default = S.blocks[new]
    out = Compound()
    out['Name'] = new
    if schema:
        p = Compound()
        for k, vals in schema.items():
            v = forced.get(k, props.get(k))
            if v is None or str(v) not in vals:
                v = default.get(k, vals[0])
            p[k] = str(v)
        out['Properties'] = p
    return out

# ---------------- paletted container helpers ----------------

def unpack(data, bits, n=4096):
    per = 64 // bits; mask = (1 << bits) - 1; out = []
    for lv in data.values():
        lv &= 0xFFFFFFFFFFFFFFFF
        for j in range(per):
            out.append((lv >> (j * bits)) & mask)
            if len(out) == n:
                return out
    return out

def pack(vals, bits):
    per = 64 // bits; longs = []
    for i in range(0, len(vals), per):
        v = 0
        for j, x in enumerate(vals[i:i + per]):
            v |= x << (j * bits)
        if v >= 1 << 63:
            v -= 1 << 64
        longs.append(v)
    return LongArray.of(longs)

def block_bits(n):
    return max(4, (n - 1).bit_length())

def fix_block_states(bs):
    pal = bs['palette']
    newpal = [fit_state(s) for s in pal]
    keys = [json.dumps(s, sort_keys=True) for s in newpal]
    if len(set(keys)) == len(keys):
        bs['palette'] = List(newpal, tag=COMPOUND)
        return
    # dedupe palette, remap data
    uniq = {}; remap = []; outpal = []
    for k, s in zip(keys, newpal):
        if k not in uniq:
            uniq[k] = len(outpal); outpal.append(s)
        remap.append(uniq[k])
    if len(outpal) == 1:
        bs['palette'] = List(outpal, tag=COMPOUND)
        bs.pop('data', None)
        return
    if 'data' in bs:
        vals = unpack(bs['data'], block_bits(len(pal)))
        vals = [remap[v] if v < len(remap) else 0 for v in vals]
        bs['data'] = pack(vals, block_bits(len(outpal)))
    bs['palette'] = List(outpal, tag=COMPOUND)

# ---------------- text components ----------------

BOOL_KEYS = {'bold', 'italic', 'underlined', 'strikethrough', 'obfuscated', 'interpret'}

def comp_to_json_obj(v):
    if isinstance(v, str):
        return v
    if isinstance(v, list):
        return [comp_to_json_obj(x) for x in v]
    if isinstance(v, dict):
        o = {}
        for k, x in v.items():
            if k in BOOL_KEYS:
                o[k] = bool(int(x))
            elif k == 'extra' or k == 'with':
                o[k] = [comp_to_json_obj(e) for e in x]
            elif k == 'click_event':
                act = str(x.get('action'))
                val = x.get('url', x.get('command', x.get('page', x.get('value', ''))))
                o['clickEvent'] = {'action': act, 'value': str(val)}
            elif k == 'hover_event':
                act = str(x.get('action'))
                if act == 'show_text':
                    o['hoverEvent'] = {'action': act, 'contents': comp_to_json_obj(x.get('value', ''))}
            elif k == 'shadow_color':
                continue
            elif k in ('text', 'translate', 'color', 'font', 'insertion', 'keybind', 'selector', 'nbt', 'fallback'):
                o[k] = str(x) if not isinstance(x, (dict, list)) else comp_to_json_obj(x)
            elif k == 'score':
                o[k] = {a: str(b) for a, b in x.items()}
            elif k == 'separator':
                o[k] = comp_to_json_obj(x)
        if not any(k in o for k in ('text', 'translate', 'keybind', 'score', 'selector', 'nbt')):
            o['text'] = ''
        return o
    return str(v)

def text_to_json(v, dv):
    """A text component stored at DataVersion dv -> 1.20.1 JSON string."""
    if dv < COMPONENT_NBT_DV and isinstance(v, str):
        return v  # already JSON
    return json.dumps(comp_to_json_obj(v), ensure_ascii=False, separators=(',', ':'))

# ---------------- items ----------------

ITEM_RENAME = {'minecraft:short_grass': 'minecraft:grass', 'minecraft:turtle_scute': 'minecraft:scute',
               'minecraft:iron_chain': 'minecraft:chain'}

def convert_item(it, dv):
    if not isinstance(it, dict) or 'id' not in it:
        return None
    iid = str(it['id'])
    iid = ITEM_RENAME.get(iid, iid)
    if iid not in S.items:
        mapped, _ = map_block_name(iid)
        if mapped in S.items and mapped != 'minecraft:air':
            iid = mapped
        else:
            STATS['item_dropped:' + iid] += 1
            return None
    out = Compound()
    if 'Slot' in it:
        out['Slot'] = Byte(int(it['Slot']))
    out['id'] = iid
    out['Count'] = Byte(max(1, min(127, int(it.get('count', it.get('Count', 1))))))
    comps = it.get('components') or {}
    tag = Compound()
    display = Compound()
    if 'minecraft:custom_name' in comps:
        display['Name'] = text_to_json(comps['minecraft:custom_name'], dv)
    if 'minecraft:item_name' in comps and 'Name' not in display:
        display['Name'] = text_to_json(comps['minecraft:item_name'], dv)
    if 'minecraft:lore' in comps:
        display['Lore'] = List([text_to_json(x, dv) for x in comps['minecraft:lore']], tag=STRING)
    if 'minecraft:dyed_color' in comps:
        c = comps['minecraft:dyed_color']
        display['color'] = Int(int(c['rgb']) if isinstance(c, dict) else int(c))
    if display:
        tag['display'] = display
    if 'minecraft:profile' in comps:
        so = convert_profile(comps['minecraft:profile'])
        if so is not None:
            tag['SkullOwner'] = so
    ench = comps.get('minecraft:enchantments')
    if isinstance(ench, dict):
        lv = ench.get('levels', ench)
        el = List(tag=COMPOUND)
        for k, v in lv.items():
            if k == 'show_in_tooltip': continue
            e = Compound(); e['id'] = str(k); e['lvl'] = Short(int(v)); el.append(e)
        if el: tag['Enchantments'] = el
    dmg = comps.get('minecraft:damage')
    if dmg is not None:
        tag['Damage'] = Int(int(dmg))
    if 'minecraft:writable_book_content' in comps:
        pages = comps['minecraft:writable_book_content'].get('pages', [])
        tag['pages'] = List([str(p.get('raw', '')) if isinstance(p, dict) else str(p) for p in pages], tag=STRING)
    if 'minecraft:written_book_content' in comps:
        wb = comps['minecraft:written_book_content']
        t = wb.get('title', '')
        tag['title'] = str(t.get('raw', '')) if isinstance(t, dict) else str(t)
        tag['author'] = str(wb.get('author', ''))
        pages = []
        for p in wb.get('pages', []):
            raw = p.get('raw', '') if isinstance(p, dict) and 'raw' in p else p
            pages.append(text_to_json(raw, dv))
        tag['pages'] = List(pages, tag=STRING)
        tag['resolved'] = Byte(1)
    if tag:
        out['tag'] = tag
    return out

def convert_items_list(lst, dv):
    out = List(tag=COMPOUND)
    for it in lst or []:
        c = convert_item(it, dv)
        if c is not None:
            out.append(c)
    return out

def convert_profile(p):
    so = Compound()
    if isinstance(p, str):
        so['Name'] = p
        return so
    if not isinstance(p, dict):
        return None
    if 'name' in p: so['Name'] = str(p['name'])
    if 'id' in p and isinstance(p['id'], IntArray): so['Id'] = p['id']
    props = p.get('properties')
    if props:
        tex = List(tag=COMPOUND)
        for pr in props:
            if str(pr.get('name')) == 'textures':
                t = Compound(); t['Value'] = str(pr.get('value', ''))
                if 'signature' in pr: t['Signature'] = str(pr['signature'])
                tex.append(t)
        if tex:
            pp = Compound(); pp['textures'] = tex; so['Properties'] = pp
    if 'Id' not in so and 'Name' not in so and 'Properties' not in so:
        return None
    if 'Id' not in so and 'Properties' in so:
        so['Id'] = IntArray.of([0x41746865, 0x72696120, 0x68656164, len(str(so['Properties']['textures'][0]['Value'])) & 0x7fffffff])
    return so

# ---------------- block entities ----------------

BANNER_CODES = {
    'base': 'b', 'square_bottom_left': 'bl', 'square_bottom_right': 'br', 'square_top_left': 'tl',
    'square_top_right': 'tr', 'stripe_bottom': 'bs', 'stripe_top': 'ts', 'stripe_left': 'ls',
    'stripe_right': 'rs', 'stripe_center': 'cs', 'stripe_middle': 'ms', 'stripe_downright': 'drs',
    'stripe_downleft': 'dls', 'small_stripes': 'ss', 'cross': 'cr', 'straight_cross': 'sc',
    'triangle_bottom': 'bt', 'triangle_top': 'tt', 'triangles_bottom': 'bts', 'triangles_top': 'tts',
    'diagonal_left': 'ld', 'diagonal_up_right': 'rd', 'diagonal_up_left': 'lud', 'diagonal_right': 'rud',
    'circle': 'mc', 'rhombus': 'mr', 'half_vertical': 'vh', 'half_horizontal': 'hh',
    'half_vertical_right': 'vhr', 'half_horizontal_bottom': 'hhb', 'border': 'bo', 'curly_border': 'cbo',
    'gradient': 'gra', 'gradient_up': 'gru', 'bricks': 'bri', 'globe': 'glb', 'creeper': 'cre',
    'skull': 'sku', 'flower': 'flo', 'mojang': 'moj', 'piglin': 'pig'}
COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray',
          'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']
SIGN_COLORS = set(COLORS)
CONTAINERS = {'minecraft:chest', 'minecraft:trapped_chest', 'minecraft:barrel', 'minecraft:shulker_box',
              'minecraft:dispenser', 'minecraft:dropper', 'minecraft:hopper', 'minecraft:furnace',
              'minecraft:blast_furnace', 'minecraft:smoker', 'minecraft:brewing_stand',
              'minecraft:campfire', 'minecraft:soul_campfire', 'minecraft:chiseled_bookshelf'}
DROP_BE = {'minecraft:shelf', 'minecraft:copper_golem_statue', 'minecraft:vault', 'minecraft:trial_spawner',
           'minecraft:crafter', 'minecraft:creaking_heart', 'minecraft:test_block', 'minecraft:test_instance_block'}

def convert_sign_text(t, dv):
    o = Compound()
    msgs = t.get('messages', ['', '', '', ''])
    o['messages'] = List([text_to_json(m, dv) for m in msgs], tag=STRING)
    if 'filtered_messages' in t:
        o['filtered_messages'] = List([text_to_json(m, dv) for m in t['filtered_messages']], tag=STRING)
    c = str(t.get('color', 'black'))
    o['color'] = c if c in SIGN_COLORS else 'black'
    o['has_glowing_text'] = Byte(int(t.get('has_glowing_text', 0)))
    return o

def convert_be(be, dv, block_at):
    bid = str(be.get('id'))
    if bid in DROP_BE:
        return None
    out = Compound()
    for k in ('id', 'x', 'y', 'z', 'keepPacked'):
        if k in be: out[k] = be[k]
    if 'keepPacked' not in out: out['keepPacked'] = Byte(0)
    if block_at is not None and block_at == 'minecraft:air':
        return None
    # common: names
    for nk in ('CustomName', 'custom_name'):
        if nk in be:
            out['CustomName'] = text_to_json(be[nk], dv)
    if 'Lock' in be and isinstance(be['Lock'], str): out['Lock'] = be['Lock']
    if 'LootTable' in be: out['LootTable'] = be['LootTable']
    if 'LootTableSeed' in be: out['LootTableSeed'] = be['LootTableSeed']
    if bid in ('minecraft:sign', 'minecraft:hanging_sign'):
        for side in ('front_text', 'back_text'):
            if side in be: out[side] = convert_sign_text(be[side], dv)
        out['is_waxed'] = Byte(int(be.get('is_waxed', 0)))
    elif bid == 'minecraft:banner':
        pl = List(tag=COMPOUND)
        for p in be.get('patterns', []):
            pat = p.get('pattern')
            pat = str(pat.get('asset_id', '')) if isinstance(pat, dict) else str(pat)
            code = BANNER_CODES.get(pat.replace('minecraft:', ''))
            col = str(p.get('color', 'white'))
            if code is None or col not in COLORS:
                STATS['banner_pattern_dropped:' + pat] += 1
                continue
            e = Compound(); e['Color'] = Int(COLORS.index(col)); e['Pattern'] = code; pl.append(e)
        if pl: out['Patterns'] = pl
    elif bid == 'minecraft:skull':
        if 'profile' in be:
            so = convert_profile(be['profile'])
            if so is not None: out['SkullOwner'] = so
        if 'note_block_sound' in be: out['note_block_sound'] = str(be['note_block_sound'])
    elif bid in CONTAINERS:
        out['Items'] = convert_items_list(be.get('Items', []), dv)
        for k in ('BurnTime', 'CookTime', 'CookTimeTotal', 'CookingTimes', 'CookingTotalTimes', 'TransferCooldown',
                  'BrewTime', 'Fuel', 'last_interacted_slot'):
            if k in be: out[k] = be[k]
        if 'cooking_time_spent' in be: out['CookTime'] = Short(int(be['cooking_time_spent']))
        if 'cooking_total_time' in be: out['CookTimeTotal'] = Short(int(be['cooking_total_time']))
        if 'lit_time_remaining' in be: out['BurnTime'] = Short(int(be['lit_time_remaining']))
    elif bid == 'minecraft:decorated_pot':
        if 'sherds' in be:
            out['sherds'] = List([str(s) if str(s) in S.items else 'minecraft:brick' for s in be['sherds']], tag=STRING)
    elif bid == 'minecraft:brushable_block':
        if 'item' in be:
            it = convert_item(be['item'], dv)
            if it is not None: out['item'] = it
    elif bid == 'minecraft:beacon':
        out['Levels'] = Int(int(be.get('Levels', 0)))
        for nk, ok in (('primary_effect', 'Primary'), ('secondary_effect', 'Secondary')):
            if nk in be and str(be[nk]) in S.effects:
                out[ok] = Int(S.effects[str(be[nk])] + 1)
    elif bid == 'minecraft:lectern':
        if 'Book' in be:
            it = convert_item(be['Book'], dv)
            if it is not None:
                out['Book'] = it; out['Page'] = Int(int(be.get('Page', 0)))
    elif bid == 'minecraft:jukebox':
        if 'RecordItem' in be:
            it = convert_item(be['RecordItem'], dv)
            if it is not None: out['RecordItem'] = it
    elif bid == 'minecraft:command_block':
        for k in ('Command', 'SuccessCount', 'TrackOutput', 'powered', 'auto', 'conditionMet', 'UpdateLastExecution', 'LastExecution'):
            if k in be: out[k] = be[k]
        if 'CustomName' not in out: out['CustomName'] = '{"text":"@"}'
        if 'LastOutput' in be: out['LastOutput'] = text_to_json(be['LastOutput'], dv)
    elif bid == 'minecraft:comparator':
        if 'OutputSignal' in be: out['OutputSignal'] = be['OutputSignal']
    elif bid == 'minecraft:bed' or bid == 'minecraft:daylight_detector' or bid == 'minecraft:bell' \
            or bid == 'minecraft:enchanting_table' or bid == 'minecraft:ender_chest' or bid == 'minecraft:conduit' \
            or bid == 'minecraft:end_gateway' or bid == 'minecraft:end_portal':
        pass
    elif bid == 'minecraft:beehive':
        out['bees'] = List(tag=COMPOUND)
    elif bid == 'minecraft:structure_block':
        for k, v in be.items():
            if k not in out and not isinstance(v, dict) and k != 'components': out[k] = v
    elif bid == 'minecraft:mob_spawner':
        for k in ('Delay', 'MinSpawnDelay', 'MaxSpawnDelay', 'SpawnCount', 'MaxNearbyEntities', 'RequiredPlayerRange', 'SpawnRange'):
            if k in be: out[k] = be[k]
    else:
        # unknown in our table: keep scalar fields only
        for k, v in be.items():
            if k not in out and k != 'components' and not isinstance(v, (dict, list)):
                out[k] = v
        STATS['be_generic:' + bid] += 1
    return out

# ---------------- entities ----------------

DROP_ENTITIES = {'minecraft:item', 'minecraft:experience_orb', 'minecraft:creaking', 'minecraft:breeze',
                 'minecraft:bogged', 'minecraft:armadillo', 'minecraft:happy_ghast', 'minecraft:copper_golem',
                 'minecraft:wind_charge', 'minecraft:breeze_wind_charge', 'minecraft:ominous_item_spawner'}
BOATS = {'minecraft:%s_boat' % w: w for w in ('oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry')}
BASE_KEYS = ('id', 'Pos', 'Motion', 'Rotation', 'UUID', 'OnGround', 'Air', 'Fire', 'Invulnerable', 'PortalCooldown',
             'CustomNameVisible', 'Silent', 'NoGravity', 'Glowing', 'Tags', 'TicksFrozen', 'HasVisualFire')
LIVING_KEYS = ('Health', 'HurtTime', 'HurtByTimestamp', 'DeathTime', 'AbsorptionAmount', 'FallFlying', 'Brain',
               'LeftHanded', 'CanPickUpLoot', 'PersistenceRequired', 'NoAI', 'Age', 'ForcedAge', 'InLove',
               'Color', 'Sheared', 'Variant', 'Tame', 'Temper', 'Bred', 'EatingHaystack', 'FromBucket',
               'Saddle', 'CollarColor', 'Sitting', 'Owner', 'HomePosX', 'HomePosY', 'HomePosZ', 'HasEgg',
               'Puff', 'PuffState', 'Strength', 'DespawnDelay', 'Invisible', 'ShowArms', 'Small', 'NoBasePlate',
               'Marker', 'DisabledSlots', 'Pose', 'EggLayTime', 'IsChickenJockey', 'BucketVariantTag',
               'AngerTime', 'HasNectar', 'HasStung', 'Offers', 'Xp')

def convert_entity(e, dv):
    eid = str(e.get('id'))
    if eid in DROP_ENTITIES:
        STATS['entity_dropped:' + eid] += 1
        return None
    out = Compound()
    for k in BASE_KEYS:
        if k in e: out[k] = e[k]
    if 'fall_distance' in e: out['FallDistance'] = Float(float(e['fall_distance']))
    if 'CustomName' in e: out['CustomName'] = text_to_json(e['CustomName'], dv)
    if 'block_pos' in e and isinstance(e['block_pos'], IntArray):
        x, y, z = e['block_pos'].values()
        out['TileX'] = Int(x); out['TileY'] = Int(y); out['TileZ'] = Int(z)
    if eid == 'minecraft:painting':
        out['facing'] = Byte(int(e.get('facing', e.get('Facing', 0))))
        v = e.get('variant', 'minecraft:kebab')
        out['variant'] = str(v) if isinstance(v, str) else 'minecraft:kebab'
    elif eid in ('minecraft:item_frame', 'minecraft:glow_item_frame'):
        for k in ('Facing', 'ItemRotation', 'ItemDropChance', 'Invisible', 'Fixed'):
            if k in e: out[k] = e[k]
        if 'Item' in e:
            it = convert_item(e['Item'], dv)
            if it is not None: out['Item'] = it
    elif eid in ('minecraft:block_display', 'minecraft:item_display', 'minecraft:text_display'):
        for k, v in e.items():
            if k in out or k in ('item', 'block_state', 'text', 'Passengers', 'fall_distance', 'teleport_duration'):
                continue
            out[k] = v
        if 'block_state' in e: out['block_state'] = fit_state(e['block_state'])
        if 'item' in e:
            it = convert_item(e['item'], dv)
            if it is not None:
                it.pop('Slot', None); out['item'] = it
        if 'text' in e: out['text'] = text_to_json(e['text'], dv)
    elif eid == 'minecraft:falling_block':
        if 'BlockState' in e: out['BlockState'] = fit_state(e['BlockState'])
        for k in ('Time', 'DropItem', 'HurtEntities', 'FallHurtMax', 'FallHurtAmount', 'CancelDrop'):
            if k in e: out[k] = e[k]
    elif eid in BOATS or eid == 'minecraft:minecart':
        if eid in BOATS:
            out['id'] = 'minecraft:boat'; out['Type'] = BOATS[eid]
    else:
        for k in LIVING_KEYS:
            if k in e: out[k] = e[k]
        eq = e.get('equipment')
        if isinstance(eq, dict):
            def it(slot):
                c = convert_item(eq[slot], dv) if slot in eq else None
                if c is not None: c.pop('Slot', None)
                return c if c is not None else Compound()
            out['ArmorItems'] = List([it('feet'), it('legs'), it('chest'), it('head')], tag=COMPOUND)
            out['HandItems'] = List([it('mainhand'), it('offhand')], tag=COMPOUND)
        if 'variant' in e and isinstance(e['variant'], str) and eid in ('minecraft:frog', 'minecraft:cat', 'minecraft:wolf'):
            out['variant'] = e['variant']
    if 'Passengers' in e:
        ps = List(tag=COMPOUND)
        for p in e['Passengers']:
            c = convert_entity(p, dv)
            if c is not None: ps.append(c)
        if ps: out['Passengers'] = ps
    return out

# ---------------- chunks ----------------

def block_name_at(chunk, x, y, z):
    sy = y >> 4
    for s in chunk.get('sections', []):
        if int(s['Y']) != sy: continue
        bs = s.get('block_states')
        if not bs: return 'minecraft:air'
        pal = bs['palette']
        if len(pal) == 1: return str(pal[0]['Name'])
        bits = block_bits(len(pal))
        idx = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        per = 64 // bits
        lv = bs['data'].values()[idx // per] & 0xFFFFFFFFFFFFFFFF
        v = (lv >> ((idx % per) * bits)) & ((1 << bits) - 1)
        return str(pal[v]['Name']) if v < len(pal) else 'minecraft:air'
    return 'minecraft:air'

def convert_chunk(c):
    dv = int(c.get('DataVersion', 0))
    for s in c.get('sections', []):
        bs = s.get('block_states')
        if bs and 'palette' in bs:
            fix_block_states(bs)
        bio = s.get('biomes')
        if bio and 'palette' in bio:
            bio['palette'] = List([b if b in S.biomes else 'minecraft:plains' for b in bio['palette']], tag=STRING)
            if len(set(bio['palette'])) != len(bio['palette']):
                # very rare: collapse to plains-only if duplicates appear
                bio['palette'] = List(['minecraft:plains'], tag=STRING); bio.pop('data', None)
    bes = List(tag=COMPOUND)
    for be in c.get('block_entities', []):
        try:
            at = block_name_at(c, int(be['x']), int(be['y']), int(be['z']))
        except Exception:
            at = None
        n = convert_be(be, dv, at)
        if n is not None: bes.append(n)
        else: STATS['be_dropped:' + str(be.get('id'))] += 1
    c['block_entities'] = bes
    for tk in ('block_ticks', 'fluid_ticks'):
        if tk in c:
            nl = List(tag=COMPOUND)
            for t in c[tk]:
                name = str(t.get('i'))
                if tk == 'block_ticks':
                    name, _ = map_block_name(name)
                    if name not in S.blocks or name == 'minecraft:air': continue
                t['i'] = name; nl.append(t)
            c[tk] = nl
    c['DataVersion'] = Int(TARGET_DV)
    return c

def convert_region(args):
    src, dst, kind, reports = args
    global S, STATS
    if S is None:
        S = Schema(reports)
        S.biomes = set('minecraft:' + b for b in open(os.path.join(reports, '..', '..', '..', 'biomes_1.20.1.txt')).read().split())
    STATS = collections.Counter()
    out = []
    for i, ts, raw in nbt.read_region(src):
        _, c = nbt.loads(raw)
        dv = int(c.get('DataVersion', 0))
        if dv <= TARGET_DV:
            out.append((i, ts, raw)); STATS[kind + '_kept'] += 1
            continue
        if kind == 'region':
            c = convert_chunk(c)
        else:
            ents = List(tag=COMPOUND)
            for e in c.get('Entities', []):
                n = convert_entity(e, dv)
                if n is not None: ents.append(n)
            c['Entities'] = ents
            c['DataVersion'] = Int(TARGET_DV)
        STATS[kind + '_converted'] += 1
        out.append((i, ts, nbt.dumps(c)))
    if out:
        nbt.write_region(dst, out)
    return src, STATS

def convert_level_dat(src, dst):
    lv = nbt.load_file(src)
    d = lv['Data']
    d['DataVersion'] = Int(TARGET_DV)
    ver = Compound(); ver['Id'] = Int(TARGET_DV); ver['Name'] = '1.20.1'; ver['Series'] = 'main'; ver['Snapshot'] = Byte(0)
    d['Version'] = ver
    d['version'] = Int(19133)
    dp = Compound()
    dp['Enabled'] = List(['vanilla', 'file/atheria_height'], tag=STRING)
    dp['Disabled'] = List(tag=STRING)
    d['DataPacks'] = dp
    d['WasModded'] = Byte(1)
    d['ServerBrands'] = List(['forge'], tag=STRING)
    d['enabled_features'] = List(['minecraft:vanilla'], tag=STRING)
    d.pop('Player', None)
    # superflat overworld generator in the 1.20.1 codec shape
    wg = d['WorldGenSettings']
    for dim, g in wg['dimensions'].items():
        gen = g['generator']
        if str(gen.get('type')) == 'minecraft:flat':
            st = gen['settings']
            st.pop('structure_overrides', None)
            st['structure_overrides'] = List(tag=STRING)
    # spawn in the airport (Centria): the world has no stored spawn, use 1.21.10 spawn compound if present
    sp = d.get('spawn')
    if isinstance(sp, dict) and 'pos' in sp:
        x, y, z = sp['pos'].values()
        d['SpawnX'] = Int(x); d['SpawnY'] = Int(y); d['SpawnZ'] = Int(z); d['SpawnAngle'] = Float(float(sp.get('yaw', 0.0)))
    d.pop('spawn', None)
    gr = d['GameRules']
    for k in list(gr.keys()):
        if k.startswith('axiom'):
            del gr[k]
    # the arena: no fire spread, no mob griefing of the prepared map, explosions keep no drops
    gr['doFireTick'] = 'false'
    gr['randomTickSpeed'] = '3'
    gr['doMobSpawning'] = 'false'
    gr['doTraderSpawning'] = 'false'
    gr['doPatrolSpawning'] = 'false'
    gr['doInsomnia'] = 'false'
    gr['doWeatherCycle'] = 'false'
    gr['doDaylightCycle'] = 'false'
    nbt.save_file(dst, lv)
    return d

DIM_TYPE = {
    "ultrawarm": False, "natural": True, "coordinate_scale": 1.0, "has_skylight": True, "has_ceiling": False,
    "ambient_light": 0.0, "monster_spawn_light_level": {"type": "minecraft:uniform", "value": {"min_inclusive": 0, "max_inclusive": 7}},
    "monster_spawn_block_light_limit": 0, "piglin_safe": False, "bed_works": True, "respawn_anchor_works": False,
    "has_raids": True, "min_y": -64, "height": 1088, "logical_height": 1088,
    "infiniburn": "#minecraft:infiniburn_overworld", "effects": "minecraft:overworld"}

def write_datapack(dst_world):
    root = os.path.join(dst_world, 'datapacks', 'atheria_height')
    os.makedirs(os.path.join(root, 'data', 'minecraft', 'dimension_type'), exist_ok=True)
    json.dump({"pack": {"pack_format": 15, "description": "Atheria: 1088 block build height (1.20.1)"}},
              open(os.path.join(root, 'pack.mcmeta'), 'w'), indent=2)
    for name in ('overworld', 'overworld_caves'):
        json.dump(DIM_TYPE, open(os.path.join(root, 'data', 'minecraft', 'dimension_type', name + '.json'), 'w'), indent=2)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('src'); ap.add_argument('dst'); ap.add_argument('--reports', required=True)
    ap.add_argument('--jobs', type=int, default=2); ap.add_argument('--only', default=None)
    a = ap.parse_args()
    if os.path.exists(a.dst) and a.only is None:
        sys.exit('destination exists: ' + a.dst)
    os.makedirs(a.dst, exist_ok=True)
    tasks = []
    for kind, folder in (('region', 'region'), ('entities', 'entities')):
        sd = os.path.join(a.src, folder); dd = os.path.join(a.dst, folder)
        os.makedirs(dd, exist_ok=True)
        for f in sorted(os.listdir(sd)):
            if not f.endswith('.mca') or os.path.getsize(os.path.join(sd, f)) < 8192: continue
            if a.only and not re.match(a.only, f): continue
            tasks.append((os.path.join(sd, f), os.path.join(dd, f), kind, a.reports))
    total = collections.Counter(); t0 = time.time()
    with Pool(a.jobs) as p:
        for n, (src, st) in enumerate(p.imap_unordered(convert_region, tasks, chunksize=1)):
            total.update(st)
            if n % 50 == 0:
                print('%d/%d %.0fs' % (n, len(tasks), time.time() - t0), flush=True)
    if a.only is None:
        d = convert_level_dat(os.path.join(a.src, 'level.dat'), os.path.join(a.dst, 'level.dat'))
        write_datapack(a.dst)
        for keep in ('icon.png',):
            shutil.copy2(os.path.join(a.src, keep), os.path.join(a.dst, keep))
        os.makedirs(os.path.join(a.dst, 'data'), exist_ok=True)
        for f in ('idcounts.dat',):
            if os.path.exists(os.path.join(a.src, 'data', f)):
                shutil.copy2(os.path.join(a.src, 'data', f), os.path.join(a.dst, 'data', f))
    json.dump(dict(total.most_common()), open(os.path.join(a.dst, '..', os.path.basename(a.dst.rstrip('/')) + '.convert-stats.json'), 'w'), indent=1)
    print('done in %.0fs' % (time.time() - t0))
    for k, v in total.most_common(60): print(' ', k, v)

if __name__ == '__main__':
    main()
