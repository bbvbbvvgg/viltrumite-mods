"""python3 compare.py ROOT OUTPREFIX  -> OUTPREFIX_head.png, OUTPREFIX_shield.png, OUTPREFIX_body.png"""
import sys, os, json
root, out = sys.argv[1], sys.argv[2]
sys.path.insert(0, os.path.join(root, 'tools/assets'))
import captain_preview as cp, jinwoo_preview as jp
from PIL import Image, ImageDraw

def sheet(q, views, center, scale, size=500, labels=None):
    img = Image.new('RGB', (size * len(views), size + 28), (40, 42, 48))
    for i, (yaw, pitch) in enumerate(views):
        im = Image.fromarray(jp.render(q, None, None, yaw, pitch, size, scale=scale, center=center))
        img.paste(im, (i * size, 28))
        ImageDraw.Draw(img).text((i * size + 8, 8), labels[i] if labels else f'{yaw}/{pitch}', fill=(255, 255, 255))
    return img

qn = cp.captain_quads({}, shield=False)
sheet(qn, ((0, 0), (90, 0), (180, 0), (35, -10)), (0, 1.72, 0), 500 / 0.85, labels=['front', 'side', 'back', '3/4']).save(out + '_head.png')
sheet(qn, ((0, 0), (35, -8), (90, 0), (180, 0)), (0, 1.0, 0), 500 / 2.2, labels=['front', '3/4', 'side', 'back']).save(out + '_body.png')
sg = jp.layered([(cp.geo('captain/captain_shield.geo.json'), cp.tex('captain/captain_shield.png'), None)], {}, None)
sheet(sg, ((0, 0), (45, 0), (90, 0), (180, 0)), (0, 0, 0), 500 / 1.0, labels=['front', '45 deg', 'side', 'back']).save(out + '_shield.png')
