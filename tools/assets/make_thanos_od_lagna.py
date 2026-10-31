#!/usr/bin/env python3
"""Od Lagna's look for the Thanos scene (own work, CC0): Reinhard's own skin (same rig, same UVs)
recoloured into a radiant white-gold being, so the model is reused untouched and the Reinhard
hero keeps his own textures.

Base: every texel is mapped by luminance onto a warm ramp (deep gold -> gold -> cream -> white);
saturated reds (hair) become pale gold strands, blues (eyes) become bright gold. Glow: a soft
self-light over the whole body (he shines in the void) plus bright eyes.

Outputs binassets textures/entity/thanos/od_lagna_scene(.png.b64) and od_lagna_scene_glow, plain
copies in build/jinwoo_preview/.

Clips (same file, `clips`): animations/thanos/thanos_od_lagna.animation.json for the Reinhard rig
(head, body, arms, forearms, hands, legs, shins), played by client/thanos/ThanosOdLagna on the scene
clock of ThanosSceneTimeline: od_lagna_form (40 t, VOID_END..ARRIVAL_END: curled light unfolds),
od_lagna_idle (loop 80 t: serene hover, breathing), od_lagna_line_1..6 (LINE_TICKS = 100 t each, a
gesture per line, starting and ending in the idle pose: the weight fades in 10 t and out 12 t),
od_lagna_restore (120 t from SPEECH_END - 6: the arms rise and open, the world comes back).
Bedrock rotations: negative X raises an arm or a leg forward; positive Z opens the right arm out,
negative Z the left one; positive X on a shin bends the knee.

Run: python3 tools/assets/make_thanos_od_lagna.py [skin|clips]   (no argument: both)
"""
import base64
import io
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from jinwoo_geo import BIN, write_anims, write_png  # noqa: E402
from thanos_clips import clip  # noqa: E402

RAMP = [(0.0, (92, 62, 18)), (0.35, (186, 136, 46)), (0.6, (236, 200, 112)), (0.8, (255, 240, 200)), (1.0, (255, 253, 246))]


def ramp(lum):
    out = np.zeros(lum.shape + (3,))
    for (t0, c0), (t1, c1) in zip(RAMP, RAMP[1:]):
        m = (lum >= t0) & (lum <= t1)
        f = ((lum - t0) / (t1 - t0))[m][:, None]
        out[m] = np.array(c0) * (1 - f) + np.array(c1) * f
    return out


def load(name):
    b = open(os.path.join(BIN, 'textures/entity/reinhard/%s.png.b64' % name)).read()
    return np.array(Image.open(io.BytesIO(base64.b64decode(b))).convert('RGBA')).astype(float)


def skin():
    src = load('reinhard')
    rgb = src[..., :3] / 255.0
    lum = 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]
    mx, mn = rgb.max(-1), rgb.min(-1)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    red = (rgb[..., 0] > rgb[..., 2] + 0.15) & (sat > 0.35)
    blue = (rgb[..., 2] > rgb[..., 0] + 0.12) & (sat > 0.3)
    # lift everything: he is light itself; darks stay readable as gold lines
    k = np.clip(0.06 + lum * 0.88, 0, 1)
    k = np.where(red, np.clip(0.55 + lum * 0.5, 0, 1), k)  # hair: pale gold strands
    k = np.where(blue, 0.42, k)  # eyes and blue trims: amber gold
    out = np.zeros_like(src)
    out[..., :3] = ramp(k)
    out[..., 3] = src[..., 3]
    out = np.clip(out, 0, 255).astype(np.uint8)
    write_png('textures/entity/thanos/od_lagna_scene.png', out)
    glow = np.zeros_like(src)
    opaque = src[..., 3] > 0
    glow[..., :3] = out[..., :3].astype(float) * 0.16
    glow[blue, :3] = (255, 226, 140)
    eyes = load('reinhard_glow')[..., 3] > 0
    glow[eyes, :3] = (255, 232, 150)
    glow[..., 3] = np.where(opaque | eyes, 255, 0)
    glow[~(opaque | eyes), :3] = 0
    write_png('textures/entity/thanos/od_lagna_scene_glow.png', np.clip(glow, 0, 255).astype(np.uint8))
    print('od lagna scene skin: %d texels' % int(opaque.sum()))


# --- clips -----------------------------------------------------------------------------------------

REST = {
    'head': (-4, 0, 0), 'body': {'rot': (0, 0, 0), 'pos': (0, 0, 0)},
    'right_arm': (-10, 0, 16), 'left_arm': (-10, 0, -16), 'right_forearm': (-14, 0, 0), 'left_forearm': (-14, 0, 0),
    'right_hand': (0, 0, 0), 'left_hand': (0, 0, 0),
    'right_leg': (4, 0, 2), 'left_leg': (10, 0, -2), 'right_shin': (8, 0, 0), 'left_shin': (16, 0, 0),
}
UPPER = ('head', 'body', 'right_arm', 'left_arm', 'right_forearm', 'left_forearm', 'right_hand', 'left_hand')


def P(**bones):
    out = dict(REST)
    out.update(bones)
    return out


def upper(pose):
    return {k: v for k, v in pose.items() if k in UPPER}


def B(rx=0, y=0.0):
    return {'rot': (rx, 0, 0), 'pos': (0, y, 0)}


def idle():
    breath = P(head=(-6, 0, 0), body=B(0, 0.4), right_arm=(-12, 0, 20), left_arm=(-12, 0, -20), right_forearm=(-18, 0, 0),
               left_forearm=(-18, 0, 0), right_leg=(6, 0, 2), left_leg=(12, 0, -2), right_shin=(10, 0, 0), left_shin=(18, 0, 0))
    return clip('od_lagna_idle', 80, [(0, REST), (40, breath), (80, REST)], loop=True)


def form():
    curled = P(head=(28, 0, 0), body=B(18, -2.0), right_arm=(-55, 25, -12), left_arm=(-55, -25, 12), right_forearm=(-70, 0, 0),
               left_forearm=(-70, 0, 0), right_leg=(-35, 0, 0), left_leg=(-28, 0, 0), right_shin=(55, 0, 0), left_shin=(50, 0, 0))
    opening = P(head=(5, 0, 0), body=B(6, -0.5), right_arm=(-35, 0, 40), left_arm=(-35, 0, -40), right_forearm=(-30, 0, 0),
                left_forearm=(-30, 0, 0), right_leg=(-10, 0, 2), left_leg=(-6, 0, -2), right_shin=(20, 0, 0), left_shin=(22, 0, 0))
    wide = P(head=(-12, 0, 0), body=B(-4, 0.5), right_arm=(-25, 0, 75), left_arm=(-25, 0, -75), right_forearm=(-8, 0, 0),
             left_forearm=(-8, 0, 0))
    return clip('od_lagna_form', 40, [(0, curled), (6, curled), (18, opening), (30, wide), (40, REST)])


def line(n, keys):
    """A line's gesture: idle pose at both ends (the scene fades its weight in and out)."""
    rows = [(0, upper(REST))] + [(t, upper(P(**pose))) for t, pose in keys] + [(100, upper(REST))]
    return clip('od_lagna_line_%d' % n, 100, rows)


def lines():
    # 1 "Enough. You clenched your fist...": the open right palm raised against him
    stop = dict(right_arm=(-95, -15, 8), right_forearm=(-25, 0, 0), right_hand=(-12, 0, 0), head=(6, 0, 0), left_arm=(-8, 0, -14))
    push = dict(stop, right_arm=(-100, -12, 8), right_forearm=(-14, 0, 0))
    # 2 "You called it balance...": both palms up, weighing the scales
    scales = dict(right_arm=(-38, 0, 28), left_arm=(-38, 0, -28), right_forearm=(-50, 0, 0), left_forearm=(-50, 0, 0), head=(2, 0, 0))
    tip_r = dict(scales, right_arm=(-46, 0, 28), left_arm=(-30, 0, -28))
    tip_l = dict(scales, right_arm=(-30, 0, 28), left_arm=(-46, 0, -28))
    # 3 "...I am the place where every voice returns": hand on the heart, then the arms open wide
    heart = dict(left_arm=(-48, 38, 0), left_forearm=(-75, 0, 0), right_arm=(-40, -20, 30), right_forearm=(-30, 0, 0), head=(4, 0, 0))
    open_ = dict(right_arm=(-22, 0, 72), left_arm=(-22, 0, -72), right_forearm=(-10, 0, 0), left_forearm=(-10, 0, 0), head=(-10, 0, 0),
                 body=B(-4, 0.3))
    # 4 "Your stones are shards of me...": the right arm points at the gauntlet, a lean toward him
    point = dict(right_arm=(-88, 4, 2), right_forearm=(-4, 0, 0), right_hand=(-6, 0, 0), body=B(5, 0.0), head=(-2, 0, 0), left_arm=(-6, 0, -12))
    # 5 "I will not undo what was fought for...": palms down, the head bowed to the heroes, then raised
    bow = dict(right_arm=(-24, 0, 12), left_arm=(-24, 0, -12), right_forearm=(-30, 0, 0), left_forearm=(-30, 0, 0), right_hand=(14, 0, 0),
               left_hand=(14, 0, 0), head=(14, 0, 0))
    lift = dict(bow, head=(4, 0, 0), right_arm=(-30, 0, 18), left_arm=(-30, 0, -18))
    # 6 "Now — return...": the arms rise over the head, the face up to the returning light
    rise = dict(right_arm=(-60, 0, 30), left_arm=(-60, 0, -30), right_forearm=(-20, 0, 0), left_forearm=(-20, 0, 0), head=(-6, 0, 0))
    up = dict(right_arm=(-155, 0, 18), left_arm=(-155, 0, -18), right_forearm=(-10, 0, 0), left_forearm=(-10, 0, 0), head=(-16, 0, 0),
              body=B(-6, 0.6))
    return [
        line(1, [(14, stop), (60, push), (82, stop)]),
        line(2, [(14, scales), (38, tip_r), (60, tip_l), (82, scales)]),
        line(3, [(14, heart), (50, heart), (62, open_), (86, open_)]),
        line(4, [(16, point), (70, dict(point, right_arm=(-86, 4, 2))), (86, point)]),
        line(5, [(14, bow), (56, bow), (70, lift), (86, lift)]),
        line(6, [(16, rise), (46, up), (100 - 1, up)]),
    ]


def restore():
    up = P(right_arm=(-150, 0, 20), left_arm=(-150, 0, -20), right_forearm=(-10, 0, 0), left_forearm=(-10, 0, 0), head=(-14, 0, 0), body=B(-6, 0.6))
    wide = P(right_arm=(-40, 0, 85), left_arm=(-40, 0, -85), right_forearm=(-6, 0, 0), left_forearm=(-6, 0, 0), head=(-10, 0, 0), body=B(-4, 1.4),
             right_leg=(2, 0, 4), left_leg=(6, 0, -4), right_shin=(6, 0, 0), left_shin=(10, 0, 0))
    settle = P(right_arm=(-30, 0, 70), left_arm=(-30, 0, -70), right_forearm=(-10, 0, 0), left_forearm=(-10, 0, 0), head=(-8, 0, 0), body=B(-3, 1.2))
    return clip('od_lagna_restore', 120, [(0, up), (40, wide), (100, settle), (120, settle)])


def clips():
    out = [form(), idle()] + lines() + [restore()]
    size = write_anims('animations/thanos/thanos_od_lagna.animation.json', out)
    assert size < 55000, size
    print('thanos_od_lagna: %d clips, %d bytes' % (len(out), size))


if __name__ == '__main__':
    what = sys.argv[1] if len(sys.argv) > 1 else 'all'
    if what in ('skin', 'all'):
        skin()
    if what in ('clips', 'all'):
        clips()
