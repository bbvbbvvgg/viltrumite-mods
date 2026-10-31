import sys, os
sys.path.insert(0, 'tools/assets')
import captain_preview as cp, jinwoo_preview as jp
from PIL import Image
kind, out = sys.argv[1], sys.argv[2]
pose = {}
if len(sys.argv) > 3:
    import json
    pose = json.loads(sys.argv[3])
if kind == 'captain':
    q = cp.captain_quads(pose)
elif kind == 'captain_noshield':
    q = cp.captain_quads(pose, shield=False)
elif kind == 'shield':
    q = jp.layered([(cp.geo('captain/captain_shield.geo.json'), cp.tex('captain/captain_shield.png'), None)], pose, None)
else:
    q = cp.avenger_quads(kind)(pose) if kind not in ('quinjet', 'mjolnir', 'arrow', 'mini_missile') else cp.prop_quads(kind)(pose)
H = 2.9 if kind == 'hulk' else 2.0
head = 2.55 if kind == 'hulk' else 1.75
if kind == 'quinjet':
    views = ((30, -20, (0, 0.45, 0), 700 / 3.2), (150, -25, (0, 0.45, 0), 700 / 3.2), (180, -8, (0, 0.45, 0.6), 700 / 1.4), (90, 0, (0, 0.45, 0), 700 / 3.2))
elif kind == 'shield':
    views = ((0, 0, (0, 0, 0), 700 / 1.0), (35, -15, (0, 0, 0), 700 / 1.0), (180, 0, (0, 0, 0), 700 / 1.0), (90, 0, (0, 0, 0), 700 / 1.0))
elif kind in ('mjolnir', 'arrow', 'mini_missile'):
    views = ((30, -20, (0, 0.1, 0), 700 / 1.0), (120, -10, (0, 0.1, 0), 700 / 1.0), (0, 0, (0, 0.1, 0), 700 / 1.0), (90, -80, (0, 0.1, 0), 700 / 1.0))
else:
    views = ((0, 0, (0, H / 2, 0), 700 / (H * 1.1)), (35, -8, (0, H / 2, 0), 700 / (H * 1.1)),
             (20, -5, (0, head, 0), 700 / 0.9), (160, -10, (0, H / 2, 0), 700 / (H * 1.1)))
tiles = [Image.fromarray(jp.render(q, None, None, yaw, pitch, 700, scale=scale, center=center)) for yaw, pitch, center, scale in views]
img = Image.new('RGB', (1400, 1400))
for i, im in enumerate(tiles):
    img.paste(im, ((i % 2) * 700, (i // 2) * 700))
img = img.resize((1000, 1000))
img.save(out)
