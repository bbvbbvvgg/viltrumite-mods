"""Hawkeye (The Avengers, 2012) — body + gear (spiky hair, compound bow, quiver, nocked arrow). Own work, CC0.

Short dirty-blond hair with a spiky top and a faded back and sides, squinting blue-grey eyes under straight brows,
a square jaw with controlled stubble and a crooked half-smile. Sleeveless black tactical top with a high collar,
purple shoulder panels and piping, a zip and chest seams; bare arms with muscle shading; a long strapped armguard
on the bow arm, a shooting glove and wrist wrap on the draw hand; belt with pouches and a buckle; tactical trousers
with knee patches and a thigh pouch; laced combat boots. Gear: compound recurve bow (purple riser, black limbs,
cams, taut string), a nocked arrow, the quiver on the back over the right shoulder with a fan of fletchings and a
chest strap.
"""
import numpy as np

from avengers_common import D, Mat, build_body, hair_strands, near, rig, side_x, sname, stitches
from clean_geo import CleanModel

SKIN = '#e5bb98'
HAIR, HAIR_HI, HAIR_LO = '#8a6a42', '#a8875a', '#5f462b'
VEST, VEST_HI, VEST_LO = '#22212a', '#34313e', '#15141a'
PURPLE, PURPLE_HI = '#58357a', '#7550a0'
PANTS = '#26252c'
LEATHER = '#3b302a'
HAIR_SHADOW = (0.94, 0.84, 0.82)

HAWKEYE_FACE = [
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHhHHHhHHHHhHHHH',
    'HhHssshsssshssHH',
    'Hsllllllllllllsh',
    'ssllllllllllllss',
    'sdBBBBsllsBBBBds',
    'sddKKKdssdKKKdds',
    'ssdwiPdlndPiwdss',
    'ssdddsslnsdddsss',
    'sslllssllnsllls s'[:16],
    'sssssdnNNndsssss',
    'seesssssssssoees',
    'seeeeemmmmmoeees',
    'seeeeeeEeeeeeees',
    'sEeeeeeeeeeeeeEs',
]
PAL = {'H': (HAIR, 0.0), 'h': (HAIR, -0.12), 's': (SKIN, 0.0), 'l': (SKIN, 0.06), 'd': (SKIN, -0.09),
       'B': ('#6e5232', 0.0), 'K': ('#3b2c22', 0.0), 'w': ('#e6e2dc', 0.0), 'i': ('#6f8fae', 0.0),
       'P': ('#1f2a38', 0.0), 'n': (SKIN, -0.13), 'N': ('#a8705c', 0.0), 'e': ('#d4ab8a', 0.0),
       'E': ('#b89070', 0.0), 'm': ('#a26a5a', 0.0), 'o': ('#c08c76', 0.0)}


# ------------------------------------------------------------------------------------------------ painters
def head_fn(ctx):
    if ctx.name == 'north':
        ctx.pixels(HAWKEYE_FACE, PAL)
    elif ctx.name == 'up':
        hair_strands(ctx, ctx.all(), HAIR)
    elif ctx.name == 'south':
        hair_strands(ctx, ctx.y > 26.0, HAIR)
        fade = (ctx.y > 25.0) & (ctx.y <= 26.0)
        ctx.put(fade, HAIR_LO, 0.6)       # faded nape
        hair_strands(ctx, fade, None, 0.6)
    elif ctx.name in ('east', 'west'):
        hair = (ctx.y > 29.6) | ((ctx.y > 27.6) & (ctx.z > 1.0)) | ((ctx.y > 26.4) & (ctx.z > 2.6))
        hair_strands(ctx, hair, HAIR)
        fade = ~hair & (ctx.y > 28.6) & (ctx.z > -1.6)
        ctx.put(fade, HAIR_LO, 0.55)   # short faded sides
        hair_strands(ctx, fade, None, 0.5)
        ear = (np.abs(ctx.y - 27.2) < 1.0) & (np.abs(ctx.z - 0.4) < 0.75)
        ctx.light(ear, 0.05)
        ctx.light(ear & (ctx.z > 0.5), -0.1)
        stub = (ctx.y < 26.6) & (ctx.z < -0.6)
        ctx.put(stub, '#d4ab8a', 0.8)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.1)


def vest_fn(ctx):
    if ctx.name == 'north':
        # purple piping running down from the shoulder panels, a centre zip, chest seams
        for s in (-1, 1):
            pipe = np.abs(ctx.x - s * 2.85) < 0.26
            ctx.put(pipe & (ctx.y > 15.0), PURPLE)
            ctx.light(pipe & (ctx.y > 15.0), 0.04)
            ctx.light(np.abs(ctx.x - s * 1.4) < 0.26, -0.07)
        zip_ = (np.abs(ctx.x + 0.13) < 0.13) & (ctx.y > 13.5)
        ctx.put(zip_, '#6c7078')
        ctx.light(near(ctx.y, 19.2, 0.26), -0.08)
        ctx.light(near(ctx.y, 18.7, 0.24), 0.04)
    elif ctx.name in ('east', 'west'):
        ctx.put(ctx.y > 21.5, PURPLE, 0.85)
        ctx.light(near(ctx.z, 0.0, 0.26), -0.08)
    elif ctx.name == 'south':
        ctx.light(near(ctx.x, 0.0, 0.26), -0.08)
        for s in (-1, 1):
            ctx.put(np.abs(ctx.x - s * 2.6) < 0.26, PURPLE, 0.7)


def shoulder_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.2, 0.08)
        ctx.put(ctx.v > 0.78, VEST_LO, 0.8)
    if ctx.name == 'up':
        ctx.light(near(ctx.z, 0.0, 0.26), -0.08)


def skin_arm_fn(ctx):
    """Bare arm: deltoid / biceps highlight on the front, triceps shadow at the back, a vein hint."""
    if ctx.name == 'north':
        ctx.light(near(ctx.y, 20.2, 0.6) & (np.abs(ctx.u - 0.5) < 0.3), 0.06)
        ctx.light(near(ctx.y, 18.6, 0.3), -0.05)
    elif ctx.name == 'south':
        ctx.light(near(ctx.y, 19.5, 0.8), -0.05)
    elif ctx.side:
        ctx.light(near(ctx.y, 20.0, 0.5), 0.03)


def forearm_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.3, 0.04)


def guard_fn(ctx):
    """Long armguard: leather with two straps and a purple edge."""
    if ctx.side:
        ctx.put(ctx.v < 0.12, PURPLE, 0.8)
        for vv in (0.35, 0.72):
            st = near(ctx.v, vv, 0.07)
            ctx.put(st, '#1c1a1d')
            ctx.light(st, -0.05)
            if ctx.name in ('east', 'west'):
                ctx.put(st & near(ctx.u, 0.5, 0.12), '#9aa1ab')
        stitches(ctx, near(ctx.u, 0.12, 0.05), '#5a4c42', 2, 0.6)


def glove_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.25, 0.05)


def belt_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.25, 0.07)
        stitches(ctx, near(ctx.v, 0.82, 0.08), '#4a4552', 2, 0.6)


def pouch_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.35, 0.06)
        ctx.light(near(ctx.v, 0.38, 0.08), -0.1)
    if ctx.name == 'north':
        ctx.put(near(ctx.u, 0.5, 0.15) & near(ctx.v, 0.45, 0.12), '#8a8f98')


def pants_fn(ctx):
    if ctx.name in ('east', 'west'):
        outer = (ctx.name == 'east') == (np.mean(ctx.x) > 0)
        if outer:
            ctx.light(near(ctx.z, 0.0, 0.26), -0.1)
            stitches(ctx, near(ctx.z, 0.5, 0.25), '#3d3c45', 2, 0.6)
    if ctx.name == 'north':
        knee = (ctx.y > 5.3) & (ctx.y < 8.0) & (np.abs(ctx.u - 0.5) < 0.38)
        ctx.put(knee, '#1d1c22')
        ctx.light(knee & (ctx.y > 7.4), 0.06)
        stitches(ctx, knee & ((np.abs(ctx.u - 0.5) > 0.3) | near(ctx.y, 5.5, 0.2)), '#3d3c45', 2, 0.6)


def boot_fn(ctx):
    if ctx.name == 'north':
        lace = (np.abs(ctx.u - 0.5) < 0.25) & (ctx.y > 1.4)
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        ctx.light(lace & ((rr + cc) % 2 == 0), 0.1)
        ctx.light(lace & ((rr + cc) % 2 == 1), -0.06)
    if ctx.side:
        ctx.put(ctx.y < 0.6, '#0f0f12')
        ctx.light(near(ctx.y, 0.8, 0.2), 0.06)


def hair_cap_fn(ctx):
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.16)


def bow_fn(ctx):
    if ctx.side:
        ctx.light((slice(0, 1), slice(None)), 0.08)
        ctx.light(near(ctx.u, 0.5, 0.12), 0.04)


def riser_fn(ctx):
    if ctx.side:
        ctx.light((slice(0, 1), slice(None)), 0.1)
        ctx.put(near(ctx.v, 0.5, 0.12), '#2a2b31')            # the grip wrap across the riser


def cam_fn(ctx):
    if ctx.name in ('east', 'west'):
        r = np.hypot(ctx.u - 0.5, ctx.v - 0.5)
        ctx.light(r < 0.2, -0.15)
        ctx.light((r > 0.35), 0.06)


def fletch_fn(ctx):
    if ctx.side and ctx.w > 2:
        ctx.light((slice(None), slice(0, 1)), 0.1)
        ctx.light((slice(None), slice(-1, None)), -0.08)


def quiver_fn(ctx):
    if ctx.side:
        ctx.put(ctx.v < 0.06, PURPLE)
        ctx.light(near(ctx.v, 0.5, 0.05), -0.06)
        stitches(ctx, near(ctx.u, 0.1, 0.05) | near(ctx.u, 0.9, 0.05), '#3a3843', 2, 0.6)


M = {
    'head': Mat(SKIN, 'skin', head_fn, ao=0.45, micro=0.5),
    'neck': Mat('#d6aa86', 'skin'),
    'torso': Mat(VEST, 'leather', vest_fn, spec=0.3),
    'collar': Mat(VEST_HI, 'leather', lambda c: c.put(c.v > 0.7, PURPLE, 0.8) if c.side else None),
    'arm': Mat(SKIN, 'skin', skin_arm_fn),
    'forearm': Mat(SKIN, 'skin', forearm_fn),
    'hand': Mat(SKIN, 'skin'),
    'fingers': Mat('#ddb18e', 'skin'),
    'leg': Mat(PANTS, 'cloth', pants_fn),
    'shin': Mat(PANTS, 'cloth', pants_fn),
    'shoulder': Mat(PURPLE, 'leather', shoulder_fn, spec=0.3),
    'belt': Mat('#2d2a31', 'leather', belt_fn),
    'buckle': Mat('#9aa1ab', 'metal'),
    'boot': Mat(VEST_LO, 'leather', boot_fn),
    'toe': Mat('#1b1a20', 'leather'),
    'guard': Mat(LEATHER, 'leather', guard_fn),
    'wrap': Mat('#2b2522', 'leather', glove_fn),
    'glove': Mat('#2e2723', 'leather', glove_fn),
    'tab': Mat('#1f1b19', 'leather'),
    'pouch': Mat('#2a2730', 'leather', pouch_fn),
    'hair': Mat(HAIR, 'hair', hair_cap_fn, cool=HAIR_SHADOW, micro=0.6, quant=0.07),
    'hair_hi': Mat(HAIR_HI, 'hair', hair_cap_fn, cool=HAIR_SHADOW, micro=0.6, quant=0.07),
    'hair_lo': Mat(HAIR_LO, 'hair', hair_cap_fn, cool=HAIR_SHADOW, micro=0.6, quant=0.07),
    'bow': Mat('#26272d', 'metal', bow_fn, spec=0.35),
    'riser': Mat(PURPLE, 'metal', riser_fn, spec=0.35),
    'cam': Mat('#8d949e', 'metal', cam_fn),
    'string': Mat('#d8dbe0', 'flat'),
    'shaft': Mat('#2a2b31', 'metal'),
    'tip': Mat('#b8bec8', 'metal'),
    'fletch': Mat(PURPLE, 'cloth', fletch_fn),
    'fletch_dk': Mat('#2a2830', 'cloth', fletch_fn),
    'nock': Mat('#c9b46a', 'paint'),
    'quiver': Mat('#26252c', 'leather', quiver_fn),
    'quiver_rim': Mat('#3a3743', 'metal'),
    'qstrap': Mat('#1d1c22', 'leather', lambda c: stitches(c, near(c.v, 0.2, 0.1) | near(c.v, 0.8, 0.1), '#3d3b45', 2, 0.6) if c.side else None),
}


# ------------------------------------------------------------------------------------------------ geometry
def body():
    m = CleanModel('hawkeye', density=D, width=128, seed=31, light=(32.0, 0.0, 0.05))
    rig(m)
    build_body(m, M)
    c = m.cube
    c('torso', [-2.8, 23.15, -2.25], [5.6, 1.1, 4.5], M['collar'])
    c('torso', [-4.25, 11.9, -2.25], [8.5, 1.5, 4.5], M['belt'])
    c('torso', [-0.9, 12.05, -2.55], [1.8, 1.2, 0.3], M['buckle'], faces=('north', 'east', 'west', 'up', 'down'))
    for x0 in (-3.6, 1.7):
        c('torso', [x0, 11.4, -2.75], [1.9, 1.6, 0.55], M['pouch'])
    c('torso', [-4.6, 11.6, -0.9], [0.4, 1.5, 1.8], M['pouch'])
    for s in (-1, 1):
        side = sname(s)
        c(f'{side}_arm', [side_x(s, 3.95, 4.25), 22.2, -2.2], [4.25, 2.0, 4.4], M['shoulder'])   # vest shoulder
        # laced combat boots: shell, toe cap
        c(f'{side}_shin', [side_x(s, 0.08, 4.0), 0.0, -2.15], [4.0, 4.2, 4.3], M['boot'])
        c(f'{side}_shin', [side_x(s, 0.35, 3.4), 0.03, -2.65], [3.4, 1.17, 0.55], M['toe'])
    c('right_leg', [-4.45, 7.3, -1.2], [0.5, 2.7, 2.4], M['pouch'])                          # thigh pouch
    # the bow arm (left) wears a long strapped armguard, the draw hand (right) a shooting glove + wrist wrap
    c('left_forearm', [3.8, 14.9, -2.2], [4.4, 3.0, 4.4], M['guard'])
    c('right_forearm', [-8.15, 15.05, -2.15], [4.3, 1.05, 4.3], M['wrap'])
    c('right_hand', [-8.2, 12.95, -2.2], [4.4, 2.1, 4.4], M['glove'])
    for z0, dz in ((-1.95, 0.9), (-1.0, 0.9), (-0.05, 0.9)):
        c('right_fingers', [-7.92, 11.95, z0], [3.84, 1.1, dz], M['tab'])                    # finger tabs
    return m


def gear():
    m = CleanModel('hawkeye_gear', density=D, width=128, seed=33, light=(32.0, 0.0, 0.05))
    rig(m)
    m.bone('bow', 'left_hand', [6.0, 13.6, 0])
    m.bone('bow_top', 'bow', [6.0, 13.6, -1.0])
    m.bone('bow_bottom', 'bow', [6.0, 13.6, 1.0])
    m.bone('arrow', 'right_hand', [-6.0, 12.8, 0])
    m.bone('quiver', 'torso', [0, 18, 2.4])
    c = m.cube
    # short hair: cap, a lifted fringe and a spiky top (small tilted tufts)
    c('head', [-4.25, 31.0, -4.25], [8.5, 1.3, 8.5], M['hair'])
    c('head', [-3.9, 30.5, -4.6], [7.8, 1.1, 0.7], M['hair_hi'], rotation=[-12, 0, 0], pivot=[0, 31.6, -4.3])
    tufts = ((-3.5, -3.9, 2.3, 0.7, 2.6, -16, 5, 'hair_hi'), (-1.1, -4.0, 2.3, 0.85, 2.8, -20, -3, 'hair'),
             (1.3, -3.8, 2.2, 0.75, 2.5, -15, -6, 'hair_hi'), (-2.6, -1.0, 2.6, 0.5, 3.0, -8, 4, 'hair_lo'),
             (0.3, -0.9, 2.6, 0.55, 3.2, -6, -4, 'hair'))
    for x0, z0, w, hgt, d, rx, rz, mat in tufts:
        c('head', [x0, 32.15, z0], [w, hgt, d], M[mat], rotation=[rx, 0, rz], pivot=[x0 + w / 2, 32.2, z0 + d / 2],
          faces=('north', 'south', 'east', 'west', 'up'))
    # compound recurve bow: riser in the fist, limbs along hand Z, cams at the tips, a taut string
    c('bow', [5.5, 12.2, -1.2], [1.0, 2.75, 2.4], M['riser'])
    c('bow', [5.6, 11.6, -0.65], [0.8, 0.6, 1.3], M['bow'])                       # grip below the fist
    c('bow', [5.62, 13.9, -2.2], [0.76, 0.8, 1.05], M['riser'])                   # arrow shelf / sight block
    c('bow_top', [5.65, 13.1, -9.0], [0.7, 1.0, 8.0], M['bow'], rotation=[-12, 0, 0], pivot=[6.0, 13.6, -1.0])
    c('bow_bottom', [5.65, 13.1, 1.0], [0.7, 1.0, 8.0], M['bow'], rotation=[12, 0, 0], pivot=[6.0, 13.6, 1.0])
    c('bow_top', [5.55, 14.6, -9.5], [0.9, 1.4, 1.1], M['cam'])
    c('bow_bottom', [5.55, 14.6, 8.4], [0.9, 1.4, 1.1], M['cam'])
    c('bow', [5.95, 15.65, -8.8], [0.1, 0.1, 17.6], M['string'])
    # nocked arrow in the right hand (shaft along hand -Z)
    c('arrow', [-6.15, 12.65, -10.0], [0.3, 0.3, 11.0], M['shaft'])
    c('arrow', [-6.3, 12.5, -11.1], [0.6, 0.6, 1.2], M['tip'])
    c('arrow', [-6.05, 12.2, 0.2], [0.1, 1.2, 1.65], M['fletch'])
    c('arrow', [-6.6, 12.75, 0.3], [1.2, 0.1, 1.5], M['fletch'])
    # quiver on the back, tilted over the right shoulder: body, rim, a fan of arrows (shaft ends + fletchings)
    c('quiver', [-1.4, 13.4, 2.1], [2.8, 9.2, 1.9], M['quiver'], rotation=[0, 0, -20], pivot=[0, 18, 2.4])
    c('quiver', [-1.55, 22.3, 1.95], [3.1, 0.6, 2.2], M['quiver_rim'], rotation=[0, 0, -20], pivot=[0, 18, 2.4])
    fan = ((-1.15, -27, 'fletch', 0.0), (-0.45, -23, 'fletch_dk', 0.25), (0.25, -19, 'fletch', 0.1),
           (0.95, -15, 'fletch_dk', 0.3))
    for i, (dx, rz, mat, dzz) in enumerate(fan):
        c('quiver', [dx, 22.9, 2.55 + dzz], [0.5, 2.1, 0.9], M[mat], rotation=[0, 0, rz], pivot=[0, 18, 2.4])
        c('quiver', [dx + 0.15, 25.0, 2.85 + dzz], [0.2, 0.35, 0.3], M['nock'], rotation=[0, 0, rz], pivot=[0, 18, 2.4])
    # strap across the chest (right shoulder to left hip) with a buckle
    c('torso', [-4.15, 17.2, -2.35], [8.3, 0.7, 4.7], M['qstrap'], rotation=[0, 0, 32], pivot=[0, 18, 0])
    c('torso', [-0.5, 17.05, -2.5], [1.0, 1.0, 0.2], M['buckle'], rotation=[0, 0, 32], pivot=[0, 18, 0],
      faces=('north', 'east', 'west', 'up', 'down'))
    return m


def build():
    b, g = body(), gear()
    b.occluders, g.occluders = [g], [b]
    return b, g
