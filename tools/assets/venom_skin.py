"""Venom's vanilla-layout textures: the 64x64 symbiote skin (HeroSkins fallback), its pulse strands, the coat frames
of the staged transformation (coat_0..16: the symbiote spreads from the hands and feet up the limbs, then over the
torso from the shoulders and hips, the head last; a wet highlight runs along the spreading edge) and the tendril strip
(16 x 64, u round the tube, v along a segment, tileable). Own work, CC0. Run through tools/assets/venom_model.py."""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from clean_geo import write_png  # noqa: E402
from pixel_shade import smooth_noise  # noqa: E402


def rgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


SYM, SYM_HI, SYM_LO = rgb('#15161f'), rgb('#2e3754'), rgb('#0a0a10')
SHEEN, VEIN, EDGE = rgb('#56689a'), rgb('#232a42'), rgb('#4a5a8c')
WHITE, WHITE_LO = rgb('#f2f4fa'), rgb('#c3c8d6')
TOOTH, MOUTH, TONGUE = rgb('#ece6d6'), rgb('#3a0a14'), rgb('#b8324c')
PULSE = rgb('#c9b8ff')

# box origin (u, v), size (w, h, d), part; inner layer then outer layer
BOXES = (
    ((0, 0), (8, 8, 8), 'head', (32, 0)),
    ((16, 16), (8, 12, 4), 'torso', (16, 32)),
    ((40, 16), (4, 12, 4), 'arm_r', (40, 32)),
    ((32, 48), (4, 12, 4), 'arm_l', (48, 48)),
    ((0, 16), (4, 12, 4), 'leg_r', (0, 32)),
    ((16, 48), (4, 12, 4), 'leg_l', (0, 48)),
)

# 8 x 8 face: K black, h highlight, W eye white, w eye low, T tooth, M mouth, R tongue
FACE = ('KhKKKKhK',
        'WwKKKKwW',
        'WWWKKWWW',
        'KWWWKWWK',
        'KKWKKWKK',
        'TMTMMTMT',
        'MRRRRRRM',
        'TMTRRTMT')
FPAL = {'K': SYM, 'h': SYM_HI, 'W': WHITE, 'w': WHITE_LO, 'T': TOOTH, 'M': MOUTH, 'R': TONGUE}
# 8 x 12 chest: the spider (S white) with its legs running off to the sides
CHEST = ('S.SKKS.S',
         'KS.SS.SK',
         'KKSSSSKK',
         'SSSSSSSS',
         'KKSSSSKK',
         'KS.SS.SK',
         'S.KSSK.S',
         'KKKSSKKK',
         'KKKKKKKK',
         'KKKKKKKK',
         'KKKKKKKK',
         'KKKKKKKK')
BACK = ('KS.KK.SK',
        'KKSSSSKK',
        'SSSSSSSS',
        'KKSSSSKK',
        'KS.SS.SK',
        'KKKSSKKK',
        'KKKKKKKK',
        'KKKKKKKK',
        'KKKKKKKK',
        'KKKKKKKK',
        'KKKKKKKK',
        'KKKKKKKK')


def rects(u, v, w, h, d):
    return {'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'east': (u + d + w, v + d, d, h),
            'south': (u + d + w + d, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}


def spread_time(part, name, r, c, rh, rw):
    """When the coat reaches this texel (0..1 of the coat's progress): limbs from the ends, then the torso, the head."""
    rv = (r + 0.5) / rh
    cu = (c + 0.5) / rw
    if part.startswith(('arm', 'leg')):
        if name == 'down':
            return 0.0
        if name == 'up':
            return 0.52
        return 0.52 * (1.0 - rv)
    if part == 'torso':
        if name == 'up':
            return 0.55
        if name == 'down':
            return 0.5
        f = 0.6 * (1 - abs(rv - 0.5) * 2) + 0.4 * (1 - abs(cu - 0.5) * 2 if name in ('north', 'south') else 0.3)
        return 0.52 + 0.3 * f
    # head: the neck first, the crown and the face last
    if name == 'down':
        return 0.8
    if name == 'up':
        return 1.0
    return 0.8 + 0.2 * (1.0 - rv)


def paint_master():
    """(skin rgba, spread time per texel, pulse mask)."""
    rs = np.random.RandomState(1988)
    img = np.zeros((64, 64, 4), float)
    when = np.full((64, 64), 9.0)
    pulse = np.zeros((64, 64), bool)
    for (u, v), (w, h, d), part, outer in BOXES:
        for layer, (lu, lv) in enumerate(((u, v), outer)):
            for name, (x, y, rw, rh) in rects(lu, lv, w, h, d).items():
                for r in range(rh):
                    for c in range(rw):
                        col = SYM.copy()
                        side = name not in ('up', 'down')
                        shade = 1.0 + (0.1 if name == 'up' else -0.18 if name == 'down' else 0.12 - 0.3 * r / max(1, rh - 1))
                        if side and (c == 1 or (rw > 4 and c == rw // 2 - 1)) and part != 'head':
                            col = SHEEN * 0.55 + SYM * 0.45             # the cold specular streak down each part
                        if part == 'head' and name == 'north':
                            ch = FACE[r][c]
                            col = FPAL[ch].copy()
                            shade = 1.0 if ch in 'WwTMR' else shade
                        elif part == 'torso' and name in ('north', 'south'):
                            ch = (CHEST if name == 'north' else BACK)[r][c]
                            if ch == 'S':
                                col, shade = WHITE.copy(), 1.0 - 0.08 * r / rh
                        elif part == 'torso' and name in ('east', 'west') and r in (1, 5):
                            col, shade = WHITE.copy(), 0.95                 # the spider's legs wrap round the ribs
                        if layer == 1 and not (part == 'head' and name == 'north' and FACE[r][c] in 'WTR'):
                            col = col * 0.92
                        img[y + r, x + c, :3] = np.clip(col * shade, 0, 255)
                        img[y + r, x + c, 3] = 255
                        when[y + r, x + c] = spread_time(part, name, r, c, rh, rw)
                # veins (pulse strands): a wandering line down the longer side faces
                if name in ('north', 'south', 'east', 'west') and rh >= 8 and not (part == 'head' and name == 'north'):
                    cx = rs.randint(0, rw)
                    for r in range(rh):
                        if 0 <= cx < rw and rs.uniform() < 0.85:
                            px, py = x + cx, y + r
                            if img[py, px, 0] < 200:            # never over the white marks
                                pulse[py, px] = True
                                img[py, px, :3] = VEIN
                        cx += rs.choice((-1, 0, 0, 1))
    return img, when, pulse


def coat_frames(img, when, frames=16):
    noise = smooth_noise(np.random.RandomState(7), 64, 64, cell=2.0, amp=0.06)
    t = np.clip(when + noise, 0.0, 1.0)
    t[when > 2] = 9
    out = []
    for k in range(frames + 1):
        lim = k / frames
        vis = t <= lim + 1e-6
        if k == frames:
            vis = when <= 1.0
        fr = np.zeros((64, 64, 4), np.uint8)
        fr[vis] = img[vis].astype(np.uint8)
        if 0 < k < frames:
            # the wet spreading edge: visible texels whose neighbour is still bare
            bare = ~vis & (when <= 1.0)
            edge = vis & (np.roll(bare, 1, 0) | np.roll(bare, -1, 0) | np.roll(bare, 1, 1) | np.roll(bare, -1, 1))
            fr[edge, :3] = EDGE.astype(np.uint8)
        out.append(fr)
    return out


def pulse_skin(pulse):
    out = np.zeros((64, 64, 4), np.uint8)
    out[pulse, :3] = PULSE.astype(np.uint8)
    out[pulse, 3] = 255
    return out


def tendril_strip():
    """16 x 64: u round the hexagonal tube, v along one segment (tileable in v)."""
    w, h = 16, 64
    img = np.zeros((h, w, 4), float)
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    light = 0.85 + 0.35 * np.cos((U - 0.3) * 2 * np.pi)                   # the lit side of the tube
    ripple = 0.06 * np.sin(V * 2 * np.pi * 3 + U * 2 * np.pi)            # tileable: whole periods along v
    L = np.clip(light + ripple, 0.4, 1.4)
    col = SYM[None, None, :] * L[..., None]
    hi = np.abs(U - 0.3) < 0.07
    col[hi] = SHEEN * 0.8
    vein = np.abs(U - (0.68 + 0.08 * np.sin(V * 2 * np.pi * 2))) < 0.045
    col[vein] = VEIN
    img[..., :3] = np.clip(col, 0, 255)
    img[..., 3] = 255
    return img.astype(np.uint8)


def main():
    img, when, pulse = paint_master()
    write_png('textures/entity/venom/venom_skin.png', img.astype(np.uint8))
    write_png('textures/entity/venom/venom_skin_pulse.png', pulse_skin(pulse))
    for k, fr in enumerate(coat_frames(img, when)):
        write_png('textures/entity/venom/coat/coat_%d.png' % k, fr)
    write_png('textures/entity/venom/venom_tendril.png', tendril_strip())


if __name__ == '__main__':
    main()
