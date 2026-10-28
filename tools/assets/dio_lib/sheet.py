"""Frame sheets for review: front / side / back of a posed model."""
import math

from PIL import Image

from . import preview

VIEWS = (("front", 180), ("side", 270), ("back", 0))


def views(model, clip=None, t=0.0, size=(300, 420), dist=6.0, target=(0, 1.15, 0), pitch=8, views=VIEWS, extra=None):
    model.pose(clip, t)
    tris = model.triangles(preview._ry(math.radians(180)))
    layers = [(tris, model.tex, 1)] + (extra or [])
    out = Image.new("RGB", (size[0] * len(views), size[1]))
    for i, (_, yaw) in enumerate(views):
        cam = preview.Camera(yaw, pitch, target, dist, size[0], size[1])
        out.paste(preview.render(layers, cam), (i * size[0], 0))
    return out
