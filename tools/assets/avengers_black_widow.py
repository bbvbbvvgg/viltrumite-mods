"""Black Widow (The Avengers, 2012) — body + gear (hair locks, Widow's Bites, pistols). Own work, CC0.

Slim player rig. Black catsuit with a glossy sheen: princess seams, a silver zip to the high collar, quilted
shoulder caps and knee panels, side panels, gloves with knuckle highlights, knee-high boots with heels; belt with
silver trim, two ammo pouches and the round buckle with the red hourglass; thigh holsters with two straps each.
Shoulder-length red waves: crown, a side part on her left with two top locks, a side-swept fringe, three locks per
side with wave bulges, curled ends in front of and behind the shoulders, a layered back and nape. Green eyes with
lashes, arched brows, red lips. Widow's Bite bracelets with glowing cells; two compact pistols held correctly
(barrel along the arm, grip under the fist), so they point forward when she aims.
"""
import numpy as np

from avengers_common import D, Mat, build_body, near, rig, side_x, sname, stitches
from clean_geo import CleanModel

SKIN = '#f0cdb2'
HAIR, HAIR_HI, HAIR_LO = '#9a3024', '#b8492d', '#6e2219'
SUIT, SUIT_HI, SUIT_LO = '#1c1d24', '#2d2f39', '#121318'
SILVER, SILVER_LO = '#b9c0ca', '#878f9b'
RED = '#c81f2b'
GUN, GUN_HI = '#2a2c32', '#454952'
BLUE = '#5ab9ff'
HAIR_SHADOW = (0.92, 0.80, 0.94)       # red hair shades towards wine / plum

WIDOW_FACE = [
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHhHHHH',
    'HHllllllllllllHH',
    'HHsllllllllllsHH',
    'HsbBBbsllsbBBbsH',
    'HsssslsllslssssH',
    'HsdKKKkssdkKKKdH',
    'HswgGwdlndwGgwsH',
    'HsdddsslnssdddsH',
    'HscllssnnssllcsH',
    'HsssssdNNdsssssH',
    'HdsssdrrrrdsssdH',
    'HdssssmLLmssssdH',
    'HHdssssddssssdHH',
]
PAL = {'H': (HAIR, 0.0), 'h': (HAIR, -0.12), 's': (SKIN, 0.0), 'l': (SKIN, 0.06), 'd': (SKIN, -0.08),
       'n': (SKIN, -0.13), 'b': ('#8a3a24', 0.0), 'B': ('#5e2418', 0.0), 'K': ('#1e1416', 0.0),
       'k': ('#3a2a28', 0.0), 'w': ('#ece8e3', 0.0), 'g': ('#5c9a5e', 0.0), 'G': ('#2d5a36', 0.0),
       'N': ('#c08a76', 0.0), 'r': ('#b8343e', 0.0), 'R': ('#8a1f2c', 0.0), 'L': ('#d4545a', 0.0),
       'c': ('#eab3a0', 0.0), 'm': ('#d98f88', 0.0)}


# ------------------------------------------------------------------------------------------------ painters
def head_fn(ctx):
    if ctx.name == 'north':
        ctx.pixels(WIDOW_FACE, PAL)
    elif ctx.name in ('up', 'south', 'east', 'west'):
        ctx.put(ctx.all(), HAIR)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.1)


def quilt(ctx, mask, period=1.0, phase=0.0):
    """Ribbed panel: horizontal ribs (lit row, dark row) every `period` units inside the mask."""
    r = np.mod(ctx.y + phase, period)
    ctx.light(mask & (r < period * 0.35), 0.07)
    ctx.light(mask & (r > period * 0.7), -0.07)


def torso_fn(ctx):
    if ctx.name == 'north':
        # princess seams curving in to the waist, the zip, a seam under the bust
        for s in (-1, 1):
            xs = s * (1.55 + 0.05 * np.clip(ctx.y - 16.0, 0, 8))
            seam = np.abs(ctx.x - xs) < 0.26
            ctx.light(seam, -0.1)
            ctx.light(np.abs(ctx.x - xs - s * 0.5) < 0.24, 0.05)
        zip_ = (np.abs(ctx.x + 0.13) < 0.13) & (ctx.y > 13.3)
        ctx.put(zip_, SILVER_LO)
        ctx.light(zip_ & (np.mod(ctx.y, 1.0) < 0.5), 0.1)
        quilt(ctx, (np.abs(ctx.x) > 3.0) & (ctx.y > 14.0) & (ctx.y < 19.0), 1.0)
    elif ctx.name in ('east', 'west'):
        quilt(ctx, (ctx.y > 13.5) & (ctx.y < 20.5), 1.0)
        ctx.light(near(ctx.z, 0.0, 0.26) & (ctx.y > 20.5), -0.08)
    elif ctx.name == 'south':
        ctx.light(near(ctx.x, 0.0, 0.26), -0.08)
        for s in (-1, 1):
            ctx.light(near(ctx.x, s * 2.2, 0.26) & (ctx.y > 17.0), -0.06)


def bust_fn(ctx):
    if ctx.name == 'north':
        ctx.light(near(ctx.x, 0.0, 0.3), -0.14)       # cleavage shadow line at the zip
        zip_ = np.abs(ctx.x + 0.13) < 0.13
        ctx.put(zip_, SILVER_LO)
        ctx.light(ctx.v < 0.35, 0.06)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.12)


def arm_fn(ctx):
    if ctx.name in ('east', 'west'):
        outer = (ctx.name == 'east') == (np.mean(ctx.x) > 0)
        if outer:
            ctx.light(near(ctx.z, 0.0, 0.26), -0.09)
            ctx.light(near(ctx.z, 0.5, 0.24), 0.05)


def cap_fn(ctx):
    if ctx.side:
        quilt(ctx, ctx.all(), 0.75)
    if ctx.name == 'up':
        ctx.light(ctx.all(), 0.06)


def glove_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.v > 0.6, 0.06)
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.08)


def knuckle_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.45, 0.08)


def leg_fn(ctx):
    if ctx.name in ('east', 'west'):
        outer = (ctx.name == 'east') == (np.mean(ctx.x) > 0)
        if outer:
            ctx.light(near(ctx.z, 0.0, 0.26), -0.09)
    if ctx.name == 'north':
        quilt(ctx, (ctx.y > 5.0) & (ctx.y < 8.2) & (np.abs(ctx.u - 0.5) < 0.42), 0.8)   # knee panel


def boot_fn(ctx):
    if ctx.side:
        ctx.light(near(ctx.y, 2.2, 0.3), -0.06)
        ctx.light(ctx.v < 0.12, 0.06)
    if ctx.name == 'north':
        ctx.light(np.abs(ctx.u - 0.5) < 0.2, 0.07)


def belt_fn(ctx):
    if ctx.side:
        ctx.put(ctx.v < 0.22, SILVER_LO, 0.8)
        ctx.light(ctx.v < 0.22, 0.06)
        ctx.put(ctx.v > 0.8, SILVER_LO, 0.5)


def hourglass_fn(ctx):
    if ctx.name != 'north':
        return
    r = np.hypot(ctx.u - 0.5, ctx.v - 0.5)
    ctx.put(r > 0.36, SILVER_LO)
    ctx.light(r > 0.36, 0.06)
    tri = (np.abs(ctx.u - 0.5) <= np.abs(ctx.v - 0.5) * 0.9 + 0.02) & (np.abs(ctx.v - 0.5) < 0.3)
    ctx.put(tri, RED)
    ctx.light(tri & (ctx.v < 0.5), 0.08)


def round_alpha(face, rs):
    a = np.full((face.h, face.w), 255, np.uint8)
    if face.name == 'north' and face.w >= 3 and face.h >= 3:
        a[0, 0] = a[0, -1] = a[-1, 0] = a[-1, -1] = 0
    return a


def pouch_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.3, 0.07)
        ctx.light(near(ctx.v, 0.33, 0.08), -0.1)          # flap edge
    if ctx.name == 'north':
        ctx.put(near(ctx.u, 0.5, 0.15) & near(ctx.v, 0.4, 0.12), SILVER_LO)


def holster_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.18, 0.07)
        stitches(ctx, near(ctx.u, 0.15, 0.06) | near(ctx.u, 0.85, 0.06), '#3a3c45', 2, 0.7)


def strap_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.5, 0.05)
        b = near(ctx.z, -1.2, 0.3) & (ctx.name == 'north')
        ctx.put(b, SILVER_LO)


def bite_fn(ctx):
    """Widow's Bite bracelet: silver band, three glowing blue charge cells on the outside and front."""
    if not ctx.side:
        return
    ctx.light((slice(0, 1), slice(None)), 0.1)
    ctx.light((slice(-1, None), slice(None)), -0.08)
    if ctx.h >= 3:
        for col in range(1, ctx.w - 1, 3):
            cell = (slice(1, ctx.h - 1), slice(col, col + 1))
            ctx.put(cell, '#bfe6ff')
            ctx.emit(cell, BLUE)


def hair_cap_fn(ctx):
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.16)


def gun_fn(ctx):
    if ctx.side:
        ctx.light((slice(0, 1), slice(None)), 0.08)


def slide_fn(ctx):
    if ctx.side and ctx.h >= 2:
        ctx.light((slice(0, 1), slice(None)), 0.1)
        serr = (np.arange(ctx.w)[None, :] % 2 == 0) & (ctx.y > ctx.y.max() - 0.6)
        ctx.light(np.broadcast_to(serr, (ctx.h, ctx.w)), -0.08)
    if ctx.name == 'down':
        ctx.put((slice(None), slice(None)), '#0c0c0e')     # muzzle end


M = {
    'head': Mat(SKIN, 'skin', head_fn, ao=0.45, micro=0.5),
    'neck': Mat('#e2bb9f', 'skin'),
    'torso': Mat(SUIT, 'leather', torso_fn, spec=0.35),
    'bust': Mat(SUIT, 'leather', bust_fn, spec=0.35),
    'collar': Mat(SUIT_HI, 'leather', spec=0.35),
    'zip_pull': Mat(SILVER, 'metal'),
    'arm': Mat(SUIT, 'leather', arm_fn, spec=0.35),
    'forearm': Mat(SUIT, 'leather', arm_fn, spec=0.35),
    'hand': Mat(SUIT_LO, 'leather', glove_fn, spec=0.3),
    'fingers': Mat(SUIT_LO, 'leather', knuckle_fn, spec=0.3),
    'leg': Mat(SUIT, 'leather', leg_fn, spec=0.35),
    'shin': Mat(SUIT, 'leather', leg_fn, spec=0.35),
    'cap': Mat(SUIT_HI, 'leather', cap_fn, spec=0.35),
    'belt': Mat('#383b44', 'leather', belt_fn),
    'buckle': Mat('#15161b', 'metal', hourglass_fn, alpha_fn=round_alpha),
    'pouch': Mat('#25272e', 'leather', pouch_fn),
    'boot': Mat('#17181d', 'leather', boot_fn, spec=0.4),
    'heel': Mat('#0f1013', 'rubber'),
    'holster': Mat('#22242a', 'leather', holster_fn),
    'strap': Mat('#2a2c33', 'leather', strap_fn),
    'bite': Mat(SILVER, 'metal', bite_fn),
    'hair': Mat(HAIR, 'hair', hair_cap_fn, sheen=30.2, cool=HAIR_SHADOW),
    'hair_hi': Mat(HAIR_HI, 'hair', hair_cap_fn, sheen=30.2, cool=HAIR_SHADOW),
    'hair_lo': Mat(HAIR_LO, 'hair', hair_cap_fn, sheen=30.2, cool=HAIR_SHADOW),
    'fringe': Mat(HAIR_HI, 'hair', hair_cap_fn, cool=HAIR_SHADOW, locks='h'),
    'fringe_lo': Mat(HAIR, 'hair', hair_cap_fn, cool=HAIR_SHADOW, locks='h'),
    'gun': Mat(GUN, 'metal', gun_fn, spec=0.4),
    'slide': Mat(GUN_HI, 'metal', slide_fn),
    'sight': Mat('#d8dde4', 'metal'),
}


# ------------------------------------------------------------------------------------------------ geometry
def pistol(m, bone, gx, gy, gz):
    """Grip in the fist. Barrel along hand -Y (points forward when the arm is levelled), slide top on hand -Z,
    grip down the hand's +Z side (points down when aiming)."""
    c = m.cube
    c(bone, [gx - 0.4, gy - 2.95, gz - 1.3], [0.8, 3.7, 0.85], M['slide'])           # slide
    c(bone, [gx - 0.33, gy - 2.55, gz - 0.45], [0.66, 3.05, 0.6], M['gun'])          # frame / dust cover
    c(bone, [gx - 0.36, gy - 0.05, gz + 0.1], [0.72, 0.95, 2.25], M['gun'],          # grip, raked back
      rotation=[-14, 0, 0], pivot=[gx, gy + 0.4, gz + 0.1])
    c(bone, [gx - 0.11, gy - 1.05, gz + 0.15], [0.22, 0.22, 0.75], M['gun'])         # trigger guard (front)
    c(bone, [gx - 0.11, gy - 1.05, gz + 0.9], [0.22, 0.95, 0.2], M['gun'])           # trigger guard (bottom)
    c(bone, [gx - 0.1, gy - 2.85, gz - 1.45], [0.2, 0.3, 0.15], M['sight'])          # front sight
    c(bone, [gx - 0.22, gy + 0.45, gz - 1.45], [0.44, 0.25, 0.15], M['sight'])       # rear sight


def body():
    m = CleanModel('black_widow', density=D, width=128, seed=21, light=(32.0, 0.0, 0.05))
    rig(m, slim=True)
    build_body(m, M, slim=True)
    c = m.cube
    c('torso', [-2.45, 23.15, -2.25], [4.9, 1.1, 4.5], M['collar'])                  # high suit collar
    c('torso', [-0.3, 22.4, -2.4], [0.4, 0.75, 0.2], M['zip_pull'])
    c('torso', [-3.35, 19.3, -2.55], [6.7, 2.05, 0.65], M['bust'])
    c('torso', [-3.05, 21.3, -2.32], [6.1, 0.8, 0.47], M['bust'])
    c('torso', [-4.25, 12.2, -2.25], [8.5, 1.3, 4.5], M['belt'])
    c('torso', [-1.15, 11.95, -2.6], [2.3, 1.8, 0.35], M['buckle'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        c('torso', [side_x(s, 2.9, 1.3), 11.6, 2.2], [1.3, 1.5, 0.75], M['pouch'])
        c('torso', [side_x(s, 4.2, 0.6), 11.9, -0.9], [0.6, 1.4, 1.8], M['pouch'])
    for s in (-1, 1):
        side = sname(s)
        c(f'{side}_arm', [side_x(s, 3.95, 3.2), 21.7, -2.18], [3.2, 2.45, 4.36], M['cap'])   # quilted shoulder cap
        # knee-high boot with a heel and a toe
        c(f'{side}_shin', [side_x(s, 0.05, 4.05), 0.35, -2.15], [4.05, 4.4, 4.3], M['boot'])
        c(f'{side}_shin', [side_x(s, 0.4, 3.3), 0.0, -2.0], [3.3, 0.35, 3.0], M['heel'])
        c(f'{side}_shin', [side_x(s, 0.35, 3.4), 0.0, -2.65], [3.4, 1.25, 0.6], M['boot'])
        c(f'{side}_shin', [side_x(s, 0.75, 2.6), 0.0, 1.55], [2.6, 1.0, 0.95], M['heel'])
        # thigh holster with two straps
        c(f'{side}_leg', [side_x(s, 3.97, 0.65), 6.5, -1.3], [0.65, 3.7, 2.6], M['holster'])
        c(f'{side}_leg', [side_x(s, 0.15, 4.08), 9.4, -2.2], [4.08, 0.55, 4.4], M['strap'])
        c(f'{side}_leg', [side_x(s, 0.18, 4.03), 7.0, -2.17], [4.03, 0.5, 4.34], M['strap'])
    return m


def gear():
    m = CleanModel('black_widow_gear', density=D, width=128, seed=23, light=(32.0, 0.0, 0.05))
    rig(m, slim=True)
    m.bone('hair', 'head', [0, 30, 0])
    m.bone('right_gun', 'right_hand', [-5.5, 12.6, 0])
    m.bone('left_gun', 'left_hand', [5.5, 12.6, 0])
    c = m.cube
    # crown, the two top locks either side of the part (her left), side-swept fringe
    c('hair', [-4.45, 31.0, -4.45], [8.9, 1.5, 8.9], M['hair'])
    c('hair', [-4.35, 32.5, -4.3], [5.75, 0.45, 8.4], M['hair_hi'], faces=('north', 'south', 'east', 'west', 'up'))
    c('hair', [1.75, 32.5, -4.2], [2.55, 0.3, 7.6], M['hair'], faces=('north', 'south', 'east', 'west', 'up'))
    c('hair', [-4.5, 29.95, -4.85], [6.0, 1.6, 0.8], M['fringe'], rotation=[0, 0, 12], pivot=[1.4, 31.0, -4.45])
    c('hair', [1.7, 30.3, -4.72], [2.75, 1.1, 0.62], M['fringe_lo'], rotation=[0, 0, -10], pivot=[3.0, 31.0, -4.4])
    # side volume: three locks per side with wave bulges, then curls in front of and behind the shoulder
    for s in (-1, 1):
        locks = ((-4.4, 2.2, 25.2, 1.0, 4.02, 'hair_hi'), (-2.25, 2.7, 24.5, 1.15, 4.07, 'hair'),
                 (0.4, 2.85, 24.4, 1.05, 4.12, 'hair_lo'))
        for i, (z0, dz, y0, t, xi, mat) in enumerate(locks):
            c('hair', [side_x(s, xi, t), y0, z0], [t, 31.2 + 0.04 * i - y0, dz], M[mat])
            c('hair', [side_x(s, xi + t - 0.05, 0.3), y0 + 1.2 + 0.5 * i, z0 + 0.3], [0.3, 2.2, dz - 0.6], M[mat])
        c('hair', [side_x(s, 3.9, 1.3), 22.7, -4.25], [1.3, 2.55, 1.9], M['hair'])
        c('hair', [side_x(s, 4.05, 1.25), 22.5, 2.3], [1.25, 1.95, 1.4], M['hair_lo'])
    # back: four locks over the back of the head, nape hair with flipped ends
    for i, (x0, w, dz, mat) in enumerate(((-4.55, 2.35, 0.0, 'hair'), (-2.25, 2.25, 0.12, 'hair_lo'),
                                          (-0.05, 2.25, 0.05, 'hair'), (2.15, 2.4, 0.16, 'hair_hi'))):
        yb = 24.3 + 0.04 * i
        c('hair', [x0, yb, 3.95 + dz], [w, 31.0 + 0.03 * i - yb, 0.9], M[mat])
        c('hair', [x0 + 0.1, 22.0 + 0.3 * ((i * 3) % 4) * 0.5, 2.35 + dz], [w - 0.2, yb + 0.2 - 22.0 - 0.15 * ((i * 3) % 4), 1.0], M[mat])
    for s in (-1, 1):
        side = sname(s)
        c(f'{side}_forearm', [side_x(s, 3.8, 3.4), 15.1, -2.1], [3.4, 1.4, 4.2], M['bite'])
    pistol(m, 'right_gun', -5.5, 12.6, 0)
    pistol(m, 'left_gun', 5.5, 12.6, 0)
    return m


def build():
    b, g = body(), gear()
    b.occluders, g.occluders = [g], [b]
    return b, g
