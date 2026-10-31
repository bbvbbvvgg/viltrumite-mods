import sys, base64, io
from PIL import Image
p, out = sys.argv[1], sys.argv[2]
k = int(sys.argv[3]) if len(sys.argv) > 3 else 4
data = open(p, 'rb').read()
if p.endswith('.b64'):
    data = base64.b64decode(data)
im = Image.open(io.BytesIO(data)).convert('RGBA')
bg = Image.new('RGBA', im.size, (60, 60, 70, 255))
bg.alpha_composite(im)
bg = bg.resize((im.width * k, im.height * k), Image.NEAREST)
bg.convert('RGB').save(out)
print(im.size)
