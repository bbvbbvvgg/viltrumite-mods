import sys, json
sys.path.insert(0, 'tools/assets')
import captain_preview as cp, jinwoo_preview as jp
from PIL import Image
out = sys.argv[1]
pose = json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}
q = cp.captain_quads(pose, shield=False)
views = ((0, 0), (30, -5), (90, 0), (150, -10))
tiles = [Image.fromarray(jp.render(q, None, None, yaw, pitch, 600, scale=600 / 0.75, center=(0, 1.76, 0))) for yaw, pitch in views]
img = Image.new('RGB', (1200, 1200))
for i, im in enumerate(tiles):
    img.paste(im, ((i % 2) * 600, (i // 2) * 600))
img.save(out)
