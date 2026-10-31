"""The summoned Iron Man wears the hero's own Mark 42 (rework: the old blocky Avenger armour is gone).

The Sind Mark 42 pieces the hero uses (geo/ironman/mark_42/*.geo.json, from convert_ironman_sind.py, 160 px atlas
textures/entity/ironman/mark_42/pieces.png) are re-parented onto the Avenger rig bones, so the Avenger clips drive
them: helmet -> head (faceplate on its own hinge), chest / abdomen / back -> torso, shoulder -> a pod bone on the
upper arm, upper arm -> right/left_arm, gauntlet -> forearm, thigh -> leg, boot -> shin. The Sind pieces are full
limb boxes masked by the atlas (the gauntlet only paints the lower half of the arm box, the boot the lower half of
the leg box), and their elbow / knee lines sit exactly on the rig joints (y 18 / y 6), so the plates bend with the
arm and the knee. No Sind bone is rotated at rest, so the cubes keep their absolute coordinates. The body under
the plates wears the hero's Mark 42 skin (textures/entity/hero/ironman_mark_42.png, player layout = rig layout).
Under the faceplate (it lifts in the circle) is Tony's face: the body skin is the Mark 42 skin with the head (and
hat layer) of the hero's Tony skin (textures/entity/hero/ironman.png).
Box UV is converted to GeckoLib's per-face equivalent (geo_lint wants per-face, mirror baked in).
Outputs: geo/avengers/iron_man_gear.geo.json, binassets textures/entity/avengers/iron_man.png.b64. Deterministic."""
import base64
import io
import json
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
GEO = os.path.join(ROOT, 'viltrumitecore/src/main/resources/assets/viltrumitecore/geo')
BIN = os.path.join(ROOT, 'viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity')


def png(rel):
    return Image.open(io.BytesIO(base64.b64decode(open(os.path.join(BIN, rel + '.b64')).read()))).convert('RGBA')


def skin():
    """Mark 42 skin, Tony's head and hat layer (64x64 player layout = the rig's UV)."""
    out = png('hero/ironman_mark_42.png').copy()
    tony = png('hero/ironman.png')
    out.paste(tony.crop((0, 0, 64, 16)), (0, 0))
    # the plates sit over the body: an opaque outer (jacket / sleeve / pants) layer would only z-fight at the joints
    for box in ((16, 32, 40, 48), (40, 32, 56, 48), (48, 48, 64, 64), (0, 32, 16, 48), (0, 48, 16, 64)):
        out.paste((0, 0, 0, 0), box)
    buf = io.BytesIO()
    out.save(buf, 'PNG', optimize=True)
    with open(os.path.join(BIN, 'avengers/iron_man.png.b64'), 'w') as f:
        f.write(base64.encodebytes(buf.getvalue()).decode('ascii'))

# piece -> (target bone, bones of the piece that go to a separate bone instead)
PIECES = {
    'helmet': ('helmet', {'faceplate_42Faceplate': 'faceplate'}),
    'chest': ('torso', {}),
    'abdomen': ('torso', {}),
    'back': ('torso', {}),
    'right_shoulder': ('shoulder_right', {}),
    'left_shoulder': ('shoulder_left', {}),
    'right_upper_arm': ('right_arm', {}),
    'left_upper_arm': ('left_arm', {}),
    'right_gauntlet': ('right_forearm', {}),
    'left_gauntlet': ('left_forearm', {}),
    'right_thigh': ('right_leg', {}),
    'left_thigh': ('left_leg', {}),
    'right_boot': ('right_shin', {}),
    'left_boot': ('left_shin', {}),
}
# extra animated bones of the Iron Man clips (faceplate lift, micro-missile pods, back flaps)
EXTRA = [
    ('helmet', 'head', [0, 24, 0]),
    ('faceplate', 'helmet', [0, 32.3, -4.4]),
    ('shoulder_right', 'right_arm', [-6, 24, 0]),
    ('shoulder_left', 'left_arm', [6, 24, 0]),
    ('back_flaps', 'torso', [0, 22, 2.2]),
]


def flip(face):
    """The same island, read right-to-left (GeckoLib flips u for a negative uv_size)."""
    (u, v), (w, h) = face['uv'], face['uv_size']
    return {'uv': [u + w, v], 'uv_size': [-w, h]}


def per_face(c):
    """Box UV -> the per-face UV GeckoLib 4.4 bakes for it (BakedModelFactory.buildQuad box branch, floor(size)),
    with `mirror` baked in (GeckoLib swaps the west/east quads and flips u for a mirrored box) so the flag can go:
    a per-face mirrored cube would also swap up/down in GeckoLib, which the box path does not."""
    if not isinstance(c.get('uv'), list):
        return c
    c = dict(c)
    u, v = c['uv']
    w, h, d = (int(x // 1) for x in c['size'])
    f = {'west': {'uv': [u + d + w, v + d], 'uv_size': [d, h]}, 'east': {'uv': [u, v + d], 'uv_size': [d, h]},
         'north': {'uv': [u + d, v + d], 'uv_size': [w, h]}, 'south': {'uv': [u + 2 * d + w, v + d], 'uv_size': [w, h]},
         'up': {'uv': [u + d, v], 'uv_size': [w, d]}, 'down': {'uv': [u + d + w, v + d], 'uv_size': [w, -d]}}
    if c.pop('mirror', False):
        f = {'west': flip(f['east']), 'east': flip(f['west']), 'north': flip(f['north']), 'south': flip(f['south']),
             'up': flip(f['up']), 'down': flip(f['down'])}
    c['uv'] = {k: f[k] for k in ('north', 'east', 'south', 'west', 'up', 'down') if f[k]['uv_size'][0] and f[k]['uv_size'][1]}
    return c


def load(rel):
    return json.load(open(os.path.join(GEO, rel)))['minecraft:geometry'][0]


def subtree_owner(bones, name, redirect):
    """The redirect target of a Sind bone (its own or an ancestor's), or None."""
    by = {b['name']: b for b in bones}
    while name is not None:
        if name in redirect:
            return redirect[name]
        name = by[name].get('parent')
    return None


def main():
    rig = load('avengers/avenger.geo.json')
    bones = [{'name': b['name'], 'pivot': b.get('pivot', [0, 0, 0]), **({'parent': b['parent']} if b.get('parent') else {}), 'cubes': []}
             for b in rig['bones'] if not b['name'].startswith('coat_tail') and not b['name'].endswith('_dagger')]
    for name, parent, pivot in EXTRA:
        bones.append({'name': name, 'parent': parent, 'pivot': pivot, 'cubes': []})
    by = {b['name']: b for b in bones}
    for piece, (target, redirect) in PIECES.items():
        g = load(f'ironman/mark_42/{piece}.geo.json')
        for b in g['bones']:
            assert not b.get('rotation') or not any(b['rotation']), (piece, b['name'], 'rotated Sind bone')
            dest = subtree_owner(g['bones'], b['name'], redirect) or target
            for c in b.get('cubes', []):
                by[dest]['cubes'].append(per_face(c))
    for b in bones:
        if not b['cubes']:
            del b['cubes']
    out = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.avenger_iron_man_mk42', 'texture_width': 160, 'texture_height': 160,
                        'visible_bounds_width': 4, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1, 0]},
        'bones': bones}]}
    path = os.path.join(GEO, 'avengers/iron_man_gear.geo.json')
    with open(path, 'w') as f:
        # one line per cube: the per-face UV of 111 cubes would be ~120 KB indented
        geo = out['minecraft:geometry'][0]
        f.write('{"format_version": "1.12.0", "minecraft:geometry": [{\n "description": ' + json.dumps(geo['description']) + ',\n "bones": [\n')
        for i, b in enumerate(geo['bones']):
            head = {k: v for k, v in b.items() if k != 'cubes'}
            f.write('  ' + json.dumps(head)[:-1])
            if b.get('cubes'):
                f.write(', "cubes": [\n' + ',\n'.join('   ' + json.dumps(c, separators=(',', ':')) for c in b['cubes']) + '\n  ]')
            f.write('}' + (',' if i < len(geo['bones']) - 1 else '') + '\n')
        f.write(' ]\n}]}\n')
    print(path, sum(len(b.get('cubes', [])) for b in bones), 'cubes')
    skin()


if __name__ == '__main__':
    main()
