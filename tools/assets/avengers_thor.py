"""Thor (The Avengers, 2012) — body + gear (hair locks, beard, cape, chest discs, Mjolnir). Own work, CC0.

Long golden hair in separate locks (crown with raised strands, swept-back fringe, three locks per side, framing
locks, two-tier back locks that follow the neck), a 3D trimmed beard (front, chin, jaw sides) with moustache, blue
eyes under heavy brows. Dark armoured tunic: raised chest plate with a centre ridge and six silver discs (ring +
boss), two segmented abdomen bands, a belt with a silver buckle, chain-mail sleeves, layered pauldrons with silver
rims, studded leather bracers, leather trousers with seams, knee-high armoured boots (sole, shell, two straps,
cuff, toe cap). Red cape: thick shoulder drape, four hanging folds at staggered depths, two clasp discs. MCU
Mjolnir: chamfered uru head with knot-work, raised rims, recessed faces, silver collar, wrapped grip, pommel and a
leather wrist strap looping out of the fist.
"""
import numpy as np

from avengers_common import D, Mat, build_body, near, rig, side_x, sname, stitches
from clean_geo import CleanModel

SKIN = '#e9c09e'
HAIR, HAIR_LO, HAIR_HI = '#d3a24c', '#a97a34', '#e6bd63'
BEARD = '#c49447'
ARMOR, PLATE = '#2c3039', '#3b414e'
SILVER = '#c9cfd8'
MAIL = '#8d939e'
PANTS = '#2f323a'
BOOT = '#25272e'
BELT = '#2e2a28'
CAPE = '#b3161f'
URU = '#8a919c'
LEATHER = '#6b462a'
HAIR_SHADOW = (0.96, 0.80, 0.74)   # blond hair shades towards warm brown, not towards grey-blue

# 16 x 16 face. Columns 0-1 / 14-15 sit behind the framing locks, rows 0-3 behind the fringe, rows 12-15 behind the
# beard cube: the visible face is the forehead, heavy brows, blue eyes, the nose and the cheekbones.
THOR_FACE = [
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHhHHHHhHHHHhHHH',
    'HHHhHHHHHHhHHHHH',
    'HHdllllllllllldH',
    'Hdslllllllllllsd',
    'HdbBBBsllsBBBbdH',
    'HdDkIIkddkIIkDdH',
    'Hsdwipwldwpiwdsh',
    'HsdddddlnddddsdH',
    'Hsslllsllnslllss',
    'mdsssdnNNndsssdm',
    'mmmmmmmmmmmmmmmm',
    'mmmmmmmmmmmmmmmm',
    'mmmmmmmmmmmmmmmm',
    'mmmmmmmmmmmmmmmm',
]
PAL = {'H': (HAIR, 0.0), 'h': (HAIR, -0.14), 's': (SKIN, 0.0), 'd': (SKIN, -0.10), 'D': (SKIN, -0.2),
       'l': (SKIN, 0.07), 'b': ('#a27a3a', 0.0), 'B': ('#7c5826', 0.0), 'k': ('#4b3524', 0.0),
       'I': ('#2f5f9e', 0.0), 'w': ('#e8e4de', 0.0), 'i': ('#4a91e0', 0.0), 'p': ('#1d2c50', 0.0),
       'n': (SKIN, -0.14), 'N': ('#9a5e4b', 0.0), 'm': (BEARD, 0.0)}

BEARD_FRONT = [
    'bbmmmmMMmmmmbb',
    'bmmMMrrrrMMmmb',
    'bkmbbRRRRbbmkb',
    'kbbbbccccbbbbk',
    'kkbbbbbbbbbbkk',
]
BEARD_PAL = {'b': (BEARD, 0.0), 'm': ('#d3a556', 0.0), 'M': ('#9c7234', 0.0), 'r': ('#c27b66', 0.0),
             'R': ('#7d3f35', 0.0), 'c': (BEARD, 0.08), 'k': (BEARD, -0.14)}


# ------------------------------------------------------------------------------------------------ painters
def head_fn(ctx):
    if ctx.name == 'north':
        ctx.pixels(THOR_FACE, PAL)
    elif ctx.name in ('up', 'south'):
        ctx.put(ctx.all(), HAIR)
    elif ctx.name in ('east', 'west'):
        ctx.put(ctx.all(), HAIR)
        front = ctx.z < -2.9
        ctx.put(front & (ctx.y < 29.0), SKIN)               # temple / cheek in front of the side locks
        ctx.put(front & (ctx.y < 27.2), BEARD)              # sideburn
    elif ctx.name == 'down':
        ctx.put(ctx.all(), BEARD)
        ctx.light(ctx.all(), -0.12)


def beard_fn(ctx):
    if ctx.name == 'north':
        ctx.pixels(BEARD_FRONT, BEARD_PAL)
    elif ctx.name == 'up':
        ctx.light(ctx.all(), -0.08)


def tunic_fn(ctx):
    """Dark quilted leather under the plates: horizontal quilting rows, side seams, a darker back panel."""
    if ctx.side:
        for y in (13.6, 15.2, 16.8, 18.4, 20.0, 21.6):
            ctx.light(near(ctx.y, y, 0.26), -0.08)
            ctx.light(near(ctx.y, y - 0.5, 0.24), 0.04)
    if ctx.name in ('east', 'west'):
        ctx.light(near(ctx.z, 0.0, 0.26), -0.1)
    if ctx.name == 'south':
        ctx.light(near(ctx.x, 0.0, 0.26), -0.1)


def plate_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.y > 23.0, 0.06)
        for x in (-3.45, 3.45):
            for y in (23.1, 17.1):
                r = near(ctx.x, x, 0.26) & near(ctx.y, y, 0.26)
                ctx.put(r, SILVER)
                ctx.light(r, 0.12)
        # bevelled border of the plate
        ctx.light(near(ctx.x, -3.7, 0.26) | near(ctx.x, 3.7, 0.26), 0.07)


def band_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.v < 0.3, 0.08)
        for x in (-2.0, 2.0):
            ctx.light(near(ctx.x, x, 0.26), -0.12)


def ridge_fn(ctx):
    if ctx.name == 'north':
        ctx.light(near(ctx.x, -0.1, 0.25), 0.1)


def mail_fn(ctx):
    """Chain mail: offset rows of rings (lit top, dark bottom) — finer than the suit scale pattern."""
    if ctx.side or ctx.name == 'up':
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        off = (rr // 2) % 2
        top = (rr % 2 == 0) & ((cc + off) % 2 == 0)
        low = (rr % 2 == 1) & ((cc + off) % 2 == 1)
        ctx.light(top, 0.08)
        ctx.light(low, -0.1)


def pauldron_fn(ctx):
    if ctx.side:
        ctx.put(ctx.v > 0.72, SILVER, 0.75)          # silver rim along the lower edge
        ctx.light(ctx.v > 0.72, 0.04)
        rivet = near(ctx.v, 0.38, 0.12) & (np.mod(ctx.u * ctx.w, 4.0) < 1.0)
        ctx.put(rivet, SILVER, 0.8)
    if ctx.name == 'up':
        ctx.light(ctx.all(), 0.04)


def bracer_fn(ctx):
    if ctx.side:
        ctx.put(ctx.v < 0.16, SILVER, 0.6)
        rr, cc = np.mgrid[0:ctx.h, 0:ctx.w]
        for row in (ctx.h // 2,):
            st = (rr == row) & (cc % 3 == 1)
            ctx.put(st, SILVER)
            ctx.light(st, 0.14)
            ctx.light((rr == row + 1) & (cc % 3 == 1), -0.1)
        stitches(ctx, (rr == ctx.h - 2), '#555048', 2, 0.6)


def pants_fn(ctx):
    if ctx.name in ('east', 'west'):
        outer = (ctx.name == 'east') == (np.mean(ctx.x) > 0)
        if outer:
            ctx.light(near(ctx.z, 0.0, 0.26), -0.12)
            stitches(ctx, near(ctx.z, 0.5, 0.25), '#474a54', 2, 0.6)
    if ctx.name == 'north':
        ctx.light(near(ctx.y, 7.5, 0.3) & (np.abs(ctx.u - 0.5) < 0.35), -0.06)    # knee wrinkle
        ctx.light(near(ctx.y, 9.5, 0.3) & (np.abs(ctx.u - 0.5) < 0.25), -0.05)
        ctx.light(near(ctx.y, 8.5, 0.25) & (np.abs(ctx.u - 0.5) < 0.3), 0.05)


def boot_fn(ctx):
    if ctx.side:
        ctx.light(near(ctx.y, 3.5, 0.3), -0.06)       # ankle crease
        ctx.light(near(ctx.y, 1.2, 0.3), 0.05)
    if ctx.name == 'north':
        ctx.light(np.abs(ctx.u - 0.5) < 0.18, 0.06)   # shin ridge


def strap_fn(ctx):
    if ctx.side:
        ctx.light(ctx.v < 0.5, 0.06)
        if ctx.name in ('east', 'west') and ((ctx.name == 'east') == (np.mean(ctx.x) > 0)):
            b = np.abs(ctx.z - 0.4) < 0.5
            ctx.put(b, SILVER)
            ctx.light(b, 0.1)


def cuff_fn(ctx):
    if ctx.side:
        ctx.put(ctx.v < 0.3, SILVER, 0.55)
        ctx.light(ctx.v > 0.7, -0.06)


def cape_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.all(), -0.2)                       # inner side
    elif ctx.name == 'south':
        if ctx.h > 6:
            ctx.light(near(ctx.y, 2.2, 0.26), -0.1)      # hem stitch line
            stitches(ctx, near(ctx.y, 1.7, 0.25), '#d8454a', 2, 0.6)
            ctx.light(near(ctx.y, 1.2, 0.3), 0.05)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.1)


def drape_fn(ctx):
    if ctx.name == 'south':
        ctx.light(near(ctx.y, 22.6, 0.3), 0.07)          # a soft roll across the shoulders
        ctx.light(near(ctx.y, 21.3, 0.3), -0.08)
    elif ctx.name == 'up':
        ctx.light(ctx.all(), 0.05)


def disc_fn(ctx):
    if ctx.name == 'north':
        r = np.hypot(ctx.u - 0.5, ctx.v - 0.5)
        ctx.light((r > 0.3) & ((ctx.u + ctx.v) < 0.9), 0.12)   # lit upper-left rim
        ctx.light((r > 0.3) & ((ctx.u + ctx.v) > 1.1), -0.12)  # shaded lower-right rim


def disc_alpha(face, rs):
    a = np.full((face.h, face.w), 255, np.uint8)
    if face.name == 'north' and face.w >= 3 and face.h >= 3:
        a[0, 0] = a[0, -1] = a[-1, 0] = a[-1, -1] = 0
    return a


def boss_fn(ctx):
    if ctx.name == 'north':
        ctx.light((ctx.u < 0.5) & (ctx.v < 0.5), 0.16)


def knot_fn(ctx):
    """Uru head: a closed knot ring with a cross on the long faces, polished rims."""
    if ctx.name in ('east', 'west', 'up', 'down') and ctx.w > 8 and ctx.h > 6:
        aspect = ctx.w / max(ctx.w, ctx.h), ctx.h / max(ctx.w, ctx.h)
        r = np.hypot((ctx.u - 0.5) * aspect[0], (ctx.v - 0.5) * aspect[1]) * 2
        ring = np.abs(r - 0.62) < 0.08
        inner = np.abs(r - 0.45) < 0.05
        cross = ((np.abs(ctx.u - 0.5) < 0.035) | (np.abs(ctx.v - 0.5) < 0.05)) & (r < 0.45)
        ctx.light(ring | cross, 0.16)
        ctx.light(inner, -0.14)
        ctx.light(np.abs(r - 0.62) < 0.16, -0.05)


def face_inset_fn(ctx):
    if ctx.name in ('north', 'south') and ctx.h > 4:
        ctx.light((slice(1, -1), slice(1, -1)), -0.12)
        ctx.light((slice(1, 2), slice(1, -1)), 0.08)


def handle_fn(ctx):
    if ctx.side:
        s = np.mod(ctx.y * 1.4 + ctx.u * 1.0, 1.0)
        ctx.light(s < 0.2, -0.14)
        ctx.light((s > 0.22) & (s < 0.4), 0.06)


def hair_cap_fn(ctx):
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.18)


M = {
    'head': Mat(SKIN, 'skin', head_fn, ao=0.5, micro=0.6),
    'neck': Mat('#d8ab88', 'skin'),
    'torso': Mat(ARMOR, 'leather', tunic_fn),
    'plate': Mat(PLATE, 'metal', plate_fn),
    'band': Mat(PLATE, 'metal', band_fn),
    'ridge': Mat('#4a5160', 'metal', ridge_fn),
    'collar': Mat(PLATE, 'metal'),
    'arm': Mat(MAIL, 'metal', mail_fn, micro=0.4, spec=0.35),
    'forearm': Mat(MAIL, 'metal', mail_fn, micro=0.4, spec=0.35),
    'hand': Mat(SKIN, 'skin'),
    'fingers': Mat('#e1b592', 'skin'),
    'leg': Mat(PANTS, 'leather', pants_fn),
    'shin': Mat(PANTS, 'leather', pants_fn),
    'pauldron': Mat(PLATE, 'metal', pauldron_fn),
    'bracer': Mat(ARMOR, 'leather', bracer_fn),
    'bracer_rim': Mat(SILVER, 'metal'),
    'belt': Mat(BELT, 'leather', lambda c: stitches(c, near(c.v, 0.2, 0.1) | near(c.v, 0.8, 0.1), '#5d5650', 2, 0.6) if c.side else None),
    'buckle': Mat(SILVER, 'metal', lambda c: c.light((slice(1, -1), slice(1, -1)), -0.15) if c.name == 'north' and c.w > 2 and c.h > 2 else None),
    'boot': Mat(BOOT, 'leather', boot_fn),
    'boot_strap': Mat('#1d1f24', 'leather', strap_fn),
    'cuff': Mat(PLATE, 'metal', cuff_fn),
    'toe': Mat('#30333b', 'metal'),
    'sole': Mat('#141519', 'rubber'),
    'beard': Mat(BEARD, 'hair', beard_fn, ao=0.4, cool=HAIR_SHADOW),
    'beard_side': Mat(BEARD, 'hair', ao=0.4, cool=HAIR_SHADOW),
    'disc': Mat('#aab1bc', 'metal', disc_fn, alpha_fn=disc_alpha),
    'boss': Mat('#e3e7ee', 'metal', boss_fn, alpha_fn=disc_alpha),
    'cape': Mat(CAPE, 'cloth', cape_fn, ao=0.7, cyl=0.3),
    'cape_drape': Mat('#a5131c', 'cloth', drape_fn, ao=0.6),
    'hair': Mat(HAIR, 'hair', hair_cap_fn, sheen=30.4, cool=HAIR_SHADOW),
    'hair_lo': Mat(HAIR_LO, 'hair', hair_cap_fn, sheen=30.4, cool=HAIR_SHADOW),
    'hair_hi': Mat(HAIR_HI, 'hair', hair_cap_fn, sheen=30.4, cool=HAIR_SHADOW),
    'hammer': Mat(URU, 'metal', knot_fn),
    'hammer_face': Mat(URU, 'metal', face_inset_fn),
    'hammer_rim': Mat('#aab1bb', 'metal'),
    'hammer_silver': Mat(SILVER, 'metal'),
    'handle': Mat(LEATHER, 'leather', handle_fn),
    'strap': Mat('#4d311c', 'leather'),
}


# ------------------------------------------------------------------------------------------------ geometry
def mjolnir(m, bone, gx, gy, gz, mats=M, strap=True):
    """Grip at (gx, gy, gz): handle along Y through the fist, head below, long axis front-back."""
    hy0, hh, hl, hw = gy - 8.5, 3.4, 5.8, 3.2
    m.cube(bone, [gx - hw / 2, hy0 + 0.3, gz - hl / 2 + 0.15], [hw, hh - 0.6, hl - 0.3], mats['hammer'])
    m.cube(bone, [gx - hw / 2 + 0.3, hy0, gz - hl / 2 + 0.05], [hw - 0.6, hh, hl - 0.1], mats['hammer'])
    for z0 in (gz - hl / 2 - 0.05, gz + hl / 2 - 0.55):
        m.cube(bone, [gx - hw / 2 - 0.12, hy0 + 0.18, z0], [hw + 0.24, hh - 0.36, 0.6], mats['hammer_rim'])
    for z0 in (gz - hl / 2 - 0.3, gz + hl / 2 + 0.05):
        m.cube(bone, [gx - hw / 2 + 0.45, hy0 + 0.45, z0], [hw - 0.9, hh - 0.9, 0.25], mats['hammer_face'])
    m.cube(bone, [gx - 0.8, hy0 + hh, gz - 0.8], [1.6, 0.45, 1.6], mats['hammer_silver'])
    top = gy + 1.0
    m.cube(bone, [gx - 0.48, hy0 + hh + 0.45, gz - 0.48], [0.96, top - (hy0 + hh + 0.45), 0.96], mats['handle'])
    m.cube(bone, [gx - 0.62, top, gz - 0.62], [1.24, 0.3, 1.24], mats['hammer_silver'])
    m.cube(bone, [gx - 0.52, top + 0.3, gz - 0.52], [1.04, 0.4, 1.04], mats['hammer_silver'])
    if strap:   # leather wrist strap looping out of the bottom of the fist, outside of the handle
        sx = gx - 0.75 if gx < 0 else gx + 0.53
        m.cube(bone, [sx, gy - 2.3, gz - 0.4], [0.22, 1.5, 0.2], mats['strap'])
        m.cube(bone, [sx, gy - 2.3, gz + 0.2], [0.22, 1.5, 0.2], mats['strap'])
        m.cube(bone, [sx, gy - 2.5, gz - 0.4], [0.22, 0.2, 0.8], mats['strap'])


def body():
    m = CleanModel('thor', density=D, width=128, seed=11, light=(32.0, 0.0, 0.05))
    rig(m)
    build_body(m, M)
    c = m.cube
    # beard: front (moustache, mouth, chin), chin tuft, jaw sides
    c('head', [-3.7, 23.5, -4.35], [7.4, 2.7, 0.9], M['beard'])
    c('head', [-1.6, 23.1, -4.42], [3.2, 0.6, 0.8], M['beard_side'])
    for s in (-1, 1):
        c('head', [side_x(s, 3.95, 0.35), 23.9, -4.15], [0.35, 3.0, 3.0], M['beard_side'])
    # armour: chest plate with a centre ridge, two abdomen bands, collar, belt + buckle
    c('torso', [-3.95, 16.6, -2.5], [7.9, 7.0, 0.5], M['plate'])
    c('torso', [-0.35, 16.8, -2.68], [0.7, 6.5, 0.2], M['ridge'])
    c('torso', [-3.85, 15.15, -2.38], [7.7, 1.3, 0.38], M['band'])
    c('torso', [-3.6, 13.7, -2.3], [7.2, 1.3, 0.3], M['band'])
    c('torso', [-3.3, 23.25, -2.4], [6.6, 0.85, 4.8], M['collar'])
    c('torso', [-4.3, 11.8, -2.3], [8.6, 1.5, 4.6], M['belt'])
    c('torso', [-1.05, 11.85, -2.6], [2.1, 1.4, 0.3], M['buckle'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        side = sname(s)
        # layered pauldron: top plate, lower plate (narrower, so the outer faces never share a plane)
        c(f'{side}_arm', [side_x(s, 3.9, 4.45), 22.3, -2.4], [4.45, 2.1, 4.8], M['pauldron'])
        c(f'{side}_arm', [side_x(s, 4.15, 4.1), 20.6, -2.3], [4.1, 1.8, 4.6], M['pauldron'])
        # bracer with a silver wrist rim
        c(f'{side}_forearm', [side_x(s, 3.75, 4.5), 15.2, -2.25], [4.5, 2.6, 4.5], M['bracer'])
        c(f'{side}_forearm', [side_x(s, 3.7, 4.6), 14.75, -2.3], [4.6, 0.5, 4.6], M['bracer_rim'])
        # knee-high boot: sole, shell, two straps, cuff, toe cap
        c(f'{side}_shin', [side_x(s, 0.02, 4.25), 0.0, -2.35], [4.25, 0.5, 4.75], M['sole'])
        c(f'{side}_shin', [side_x(s, 0.08, 4.12), 0.5, -2.2], [4.12, 4.5, 4.4], M['boot'])
        c(f'{side}_shin', [side_x(s, 0.04, 4.2), 1.6, -2.27], [4.2, 0.55, 4.54], M['boot_strap'])
        c(f'{side}_shin', [side_x(s, 0.04, 4.2), 3.3, -2.27], [4.2, 0.55, 4.54], M['boot_strap'])
        c(f'{side}_shin', [side_x(s, 0.08, 4.19), 5.0, -2.3], [4.19, 1.15, 4.6], M['cuff'])
        c(f'{side}_shin', [side_x(s, 0.3, 3.6), 0.5, -2.8], [3.6, 1.4, 0.6], M['toe'])
    return m


def gear():
    m = CleanModel('thor_gear', density=D, width=128, seed=13, light=(32.0, 0.0, 0.05))
    rig(m)
    m.bone('cape', 'torso', [0, 23.6, 2.4])
    m.bone('cape_lower', 'cape', [0, 15, 2.7])
    m.bone('hair', 'head', [0, 30, 0])
    m.bone('hammer', 'right_hand', [-6, 13.2, 0])
    c = m.cube
    # chest discs (ring + boss) in two columns, the two cape clasps at the collar
    for x in (-1.95, 1.95):
        for y in (21.3, 19.1, 17.0):
            c('torso', [x - 0.9, y - 0.9, -3.05], [1.8, 1.8, 0.3], M['disc'], faces=('north', 'east', 'west', 'up', 'down'))
            c('torso', [x - 0.5, y - 0.5, -3.25], [1.0, 1.0, 0.2], M['boss'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        cx = s * 3.35
        c('torso', [cx - 0.85, 21.65, -2.85], [1.7, 1.7, 0.3], M['disc'], faces=('north', 'east', 'west', 'up', 'down'))
        c('torso', [cx - 0.45, 22.05, -3.05], [0.9, 0.9, 0.2], M['boss'], faces=('north', 'east', 'west', 'up', 'down'))
    # cape: thick drape across the shoulders, four hanging folds at staggered depths starting inside the drape
    c('cape', [-4.6, 20.4, 2.3], [9.2, 3.4, 1.05], M['cape_drape'])
    for i, (x0, w, dz, y0) in enumerate(((-4.75, 2.5, 0.05, 1.4), (-2.35, 2.45, 0.28, 1.0), (0.0, 2.45, 0.12, 1.2),
                                         (2.3, 2.45, 0.34, 1.5))):
        c('cape_lower', [x0, y0, 2.55 + dz], [w, 21.0 + 0.07 * i - y0, 0.42], M['cape'])
    # hair: crown with raised strands, swept-back fringe, side locks, framing locks, two-tier back locks
    c('hair', [-4.4, 31.0, -4.4], [8.8, 1.6, 8.8], M['hair'])
    for x0, w, z1, mat in ((-3.4, 1.7, 1.2, 'hair_hi'), (-1.2, 1.9, 2.6, 'hair'), (1.1, 1.6, 0.4, 'hair_hi')):
        c('hair', [x0, 32.6, -4.2], [w, 0.25, z1 + 4.2], M[mat], faces=('north', 'south', 'east', 'west', 'up'))
    c('hair', [-4.3, 30.2, -4.65], [8.6, 1.0, 0.75], M['hair'])
    for s in (-1, 1):
        locks = ((-3.1, 2.25, 26.7, 0.85, 31.1, 'hair', 4.02), (-0.9, 2.45, 24.6, 1.0, 31.2, 'hair_lo', 4.05),
                 (1.5, 2.8, 24.5, 0.9, 31.15, 'hair', 4.03))
        for z0, dz, y0, t, y1, mat, xi in locks:
            c('hair', [side_x(s, xi, t), y0, z0 - 0.05], [t, y1 - y0, dz + 0.05], M[mat])
        c('hair', [side_x(s, 3.5, 0.95), 27.0, -4.6], [0.95, 3.6, 0.9], M['hair_hi'])
    for i, (x0, w, dz, y0, mat) in enumerate(((-4.35, 2.2, 0.0, 22.6, 'hair'), (-2.2, 2.2, 0.14, 22.0, 'hair_lo'),
                                              (0.0, 2.2, 0.06, 22.4, 'hair'), (2.15, 2.2, 0.18, 22.9, 'hair_lo'))):
        yb = 24.1 + 0.05 * ((i * 3) % 4)
        c('hair', [x0, yb, 3.95 + dz], [w, 31.0 + 0.02 * i - yb, 0.85], M[mat])
        c('hair', [x0 + 0.1, y0, 3.05 + dz], [w - 0.2, yb + 0.15 - y0, 0.85], M[mat])
    mjolnir(m, 'hammer', -6.0, 13.2, 0.0)
    return m


def build():
    b, g = body(), gear()
    b.occluders, g.occluders = [g], [b]
    return b, g


def thrown_mjolnir():
    m = CleanModel('mjolnir_mcu', density=6, width=128, seed=43)
    m.bone('hammer', None, [0, 0, 0])
    mjolnir(m, 'hammer', 0.0, 3.8, 0.0, strap=False)
    return m
