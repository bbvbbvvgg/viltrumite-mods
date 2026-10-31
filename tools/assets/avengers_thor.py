"""Thor (The Avengers, 2012) — body + gear (hair locks, cape, chest discs, Mjolnir). Own work, CC0.

Long golden hair in separate locks (crown, swept-back hairline, three locks per side, framing locks, five
layered locks down the back), a 3D trimmed beard with moustache, blue eyes under heavy brows. Dark armoured
tunic with a raised chest plate and six silver discs (ring + boss), chain-mail sleeves, layered pauldrons,
studded leather bracers, leather trousers with seams, knee-high armoured boots (cuff, toe cap, sole), a red
cape in four folds hanging from a shoulder drape and two clasp discs. MCU Mjolnir: chamfered uru head with
knot-work, raised rims, recessed faces, silver collar, wrapped grip, pommel and wrist strap.
"""
import numpy as np

from avengers_common import D, Mat, build_body, face_fn, near, rig, rim, seam_h, seam_x, side_x, sname, stitches, studs
from clean_geo import CleanModel

SKIN = '#e8bf9c'
HAIR, HAIR_LO = '#dfb257', '#b88a38'
BEARD = '#c4964a'
ARMOR, PLATE = '#2b2e37', '#383d48'
SILVER = '#c8ced7'
MAIL = '#8e949f'
PANTS = '#2d3038'
BOOT = '#24262c'
BELT = '#2c2927'
CAPE = '#b5161f'
URU = '#8f959f'
LEATHER = '#6a4529'

THOR_FACE = [
    'HHHHHHHHHHHHHHHH',
    'HHHHHHHHHHHHHHHH',
    'HHHhHHHHHHHHhHHH',
    'HHHHHHHHHHHHHHHH',
    'HhssssssssssshHH',
    'HssssslllsssssdH',
    'sbbbbbsllsbbbbbs',
    'sKKKKKsllsKKKKKs',
    'sdWIPWslnsWPIWds',
    'ssddddsllnddddss',
    'slllssslnnssslls',
    'sllsssnNnNnsssls',
    'dBBBBBBBBBBBBBBd',
    'dBBBBBBBBBBBBBBd',
    'BBBBBBBBBBBBBBBB',
    'BBBBBBBBBBBBBBBB',
]
PAL = {'H': HAIR, 'h': (HAIR, -0.12), 's': SKIN, 'd': (SKIN, -0.06), 'l': (SKIN, 0.05), 'b': '#9d7434',
       'K': ('#5a3e28', 0.0), 'W': '#f3f1ec', 'I': '#3d7fd3', 'P': '#1c2b4d', 'n': (SKIN, -0.08),
       'N': ('#9a6550', 0.0), 'B': BEARD}

BEARD_FRONT = [
    'BBBBBMMMMMBBBBB',
    'BBBMMMLLLMMMBBB',
    'BBBBBLLLLLBBBBB',
    'BBBBBBhhhBBBBBB',
    'BBBBBBBBBBBBBBB',
    'hBBBBBBBBBBBBBh',
]
BEARD_PAL = {'B': BEARD, 'M': (BEARD, -0.12), 'L': '#b56b5c', 'h': (BEARD, -0.1)}


# ------------------------------------------------------------------------------------------------ painters
def torso_fn(ctx):
    if ctx.name == 'north':
        mail = ctx.y < 16.7
        ctx.put(mail, MAIL)
        ctx.light(mail & ((np.add.outer(np.arange(ctx.h), np.arange(ctx.w)) % 2) == 0), 0.03)
    elif ctx.name in ('east', 'west'):
        ctx.put(ctx.y < 16.7, MAIL)
        seam_h(ctx, 20.5)
    elif ctx.name == 'south':
        seam_x(ctx, 0.0)
        seam_h(ctx, 16.0)


def plate_fn(ctx):
    if ctx.name == 'north':
        seam_x(ctx, 0.0, dark=-0.16)
        ctx.light(ctx.y > 23.0, 0.05)
        for x in (-3.4, 3.4):
            for y in (23.1, 17.1):
                r = near(ctx.x, x, 0.26) & near(ctx.y, y, 0.26)
                ctx.put(r, SILVER)
                ctx.light(r, 0.1)


def mail_fn(ctx):
    if ctx.side:
        rim(ctx, '#6d737d', 0.5)


def bracer_fn(ctx):
    if ctx.side:
        rim(ctx, SILVER, 0.55)
        studs(ctx, SILVER, every=3)
        stitches(ctx, near(ctx.v, 0.22, 0.08), '#4a4038', 2, 0.6)


def pants_fn(ctx):
    if ctx.name in ('east', 'west'):
        outer = (ctx.name == 'east') == (np.mean(ctx.x) > 0)
        if outer:
            seam = near(ctx.z, 0.0, 0.26)
            ctx.light(seam, -0.1)
            stitches(ctx, near(ctx.z, 0.5, 0.25), '#41444d', 2, 0.6)
    if ctx.side:
        ctx.light(near(ctx.y, 7.0, 0.3), -0.05)         # knee crease (thigh bottom)


def boot_fn(ctx):
    if ctx.side:
        ctx.put(ctx.y < 0.6, '#16171b')
        st = near(ctx.y, 5.0, 0.26)
        ctx.put(st & ((np.arange(ctx.w)[None, :] % 3) == 1), SILVER)
        ctx.light(st & ((np.arange(ctx.w)[None, :] % 3) == 1), 0.1)
    if ctx.name == 'down':
        ctx.put(ctx.all(), '#16171b')


def cape_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.all(), -0.18)                      # inner side
    elif ctx.name == 'south':
        if ctx.h > 6:
            ctx.light(near(ctx.y, 2.3, 0.26), -0.1)      # hem stitch line
            stitches(ctx, near(ctx.y, 1.8, 0.25), '#d63a3f', 2, 0.6)
    elif ctx.name == 'down':
        ctx.light(ctx.all(), -0.1)


def disc_fn(ctx):
    if ctx.name in ('north',):
        r = np.hypot(ctx.u - 0.5, ctx.v - 0.5)
        ctx.light(r > 0.38, 0.08)
        ctx.light((r < 0.32) & (r > 0.24), -0.1)


def boss_fn(ctx):
    if ctx.name == 'north':
        ctx.light(ctx.v < 0.4, 0.1)


def knot_fn(ctx):
    """Uru head: a closed knot ring with a cross on the long faces, polished rims."""
    if ctx.name in ('east', 'west', 'up', 'down') and ctx.w > 8 and ctx.h > 6:
        aspect = ctx.w / max(ctx.w, ctx.h), ctx.h / max(ctx.w, ctx.h)
        r = np.hypot((ctx.u - 0.5) * aspect[0], (ctx.v - 0.5) * aspect[1]) * 2
        ring = np.abs(r - 0.62) < 0.08
        inner = np.abs(r - 0.45) < 0.05
        cross = ((np.abs(ctx.u - 0.5) < 0.035) | (np.abs(ctx.v - 0.5) < 0.05)) & (r < 0.45)
        ctx.light(ring | cross, 0.14)
        ctx.light(inner, -0.12)
        ctx.light(np.abs(r - 0.62) < 0.16, -0.04)


def face_inset_fn(ctx):
    if ctx.name in ('north', 'south') and ctx.h > 4:
        ctx.light((slice(1, -1), slice(1, -1)), -0.1)


def handle_fn(ctx):
    if ctx.side:
        s = np.mod(ctx.y * 1.4 + ctx.u * 1.0, 1.0)
        ctx.light(s < 0.18, -0.12)
        ctx.light((s > 0.2) & (s < 0.35), 0.05)


def hair_cap_fn(ctx):
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.15)


def beard_fn(ctx):
    if ctx.name == 'north':
        ctx.pixels(BEARD_FRONT, BEARD_PAL)
    elif ctx.name == 'up':
        ctx.light(ctx.all(), -0.1)


M = {
    'head': Mat(SKIN, 'skin', face_fn(THOR_FACE, PAL, hair=HAIR, top=HAIR, back=HAIR, ear=SKIN), ao=0.45),
    'neck': Mat('#d9ad8a', 'skin'),
    'torso': Mat(ARMOR, 'leather', torso_fn),
    'plate': Mat(PLATE, 'metal', plate_fn),
    'collar': Mat(PLATE, 'metal'),
    'arm': Mat(MAIL, 'suit', mail_fn, micro=1.8),
    'forearm': Mat(MAIL, 'suit', micro=1.8),
    'hand': Mat(SKIN, 'skin'),
    'fingers': Mat('#e0b390', 'skin'),
    'leg': Mat(PANTS, 'leather', pants_fn),
    'shin': Mat(PANTS, 'leather', pants_fn),
    'pauldron': Mat(PLATE, 'metal', lambda c: rim(c, SILVER, 0.35) if c.side else None),
    'bracer': Mat(ARMOR, 'leather', bracer_fn),
    'bracer_rim': Mat(SILVER, 'metal'),
    'belt': Mat(BELT, 'leather', lambda c: stitches(c, near(c.v, 0.2, 0.1) | near(c.v, 0.8, 0.1), '#57504a', 2, 0.6) if c.side else None),
    'buckle': Mat(SILVER, 'metal'),
    'boot': Mat(BOOT, 'leather', boot_fn),
    'cuff': Mat(PLATE, 'metal', lambda c: rim(c, SILVER, 0.4) if c.side else None),
    'sole': Mat('#141519', 'rubber'),
    'beard': Mat(BEARD, 'hair', beard_fn, ao=0.35),
    'disc': Mat(SILVER, 'metal', disc_fn),
    'boss': Mat('#dfe3ea', 'metal', boss_fn),
    'cape': Mat(CAPE, 'cloth', cape_fn, ao=0.6),
    'cape_drape': Mat('#a3131b', 'cloth', cape_fn, ao=0.6),
    'hair': Mat(HAIR, 'hair', hair_cap_fn, sheen=30.6),
    'hair_lo': Mat(HAIR_LO, 'hair', hair_cap_fn, sheen=30.6),
    'hammer': Mat(URU, 'metal', knot_fn),
    'hammer_face': Mat(URU, 'metal', face_inset_fn),
    'hammer_rim': Mat('#aeb4bd', 'metal'),
    'hammer_silver': Mat(SILVER, 'metal'),
    'handle': Mat(LEATHER, 'leather', handle_fn),
    'strap': Mat('#4d311c', 'leather'),
}


# ------------------------------------------------------------------------------------------------ geometry
def mjolnir(m, bone, gx, gy, gz, mats=M):
    """Grip at (gx, gy, gz): handle along Y through the fist, head below, long axis front-back."""
    hy0, hh, hl, hw = gy - 7.9, 3.4, 5.8, 3.2
    m.cube(bone, [gx - hw / 2, hy0 + 0.3, gz - hl / 2 + 0.15], [hw, hh - 0.6, hl - 0.3], mats['hammer'])
    m.cube(bone, [gx - hw / 2 + 0.3, hy0, gz - hl / 2 + 0.05], [hw - 0.6, hh, hl - 0.1], mats['hammer'])
    for z0 in (gz - hl / 2 - 0.05, gz + hl / 2 - 0.55):
        m.cube(bone, [gx - hw / 2 - 0.12, hy0 + 0.18, z0], [hw + 0.24, hh - 0.36, 0.6], mats['hammer_rim'])
    for z0 in (gz - hl / 2 - 0.3, gz + hl / 2 + 0.05):
        m.cube(bone, [gx - hw / 2 + 0.45, hy0 + 0.45, z0], [hw - 0.9, hh - 0.9, 0.25], mats['hammer_face'])
    m.cube(bone, [gx - 0.8, hy0 + hh, gz - 0.8], [1.6, 0.45, 1.6], mats['hammer_silver'])
    m.cube(bone, [gx - 0.48, hy0 + hh + 0.45, gz - 0.48], [0.96, gy + 1.5 - (hy0 + hh + 0.45), 0.96], mats['handle'])
    m.cube(bone, [gx - 0.62, gy + 1.5, gz - 0.62], [1.24, 0.35, 1.24], mats['hammer_silver'])
    m.cube(bone, [gx - 0.52, gy + 1.85, gz - 0.52], [1.04, 0.55, 1.04], mats['hammer_silver'])
    m.cube(bone, [gx - 0.3, gy + 2.4, gz - 0.3], [0.6, 0.3, 0.6], mats['hammer_silver'])
    m.cube(bone, [gx - 0.12, gy - 0.9, gz + 0.55], [0.24, 2.95, 0.22], mats['strap'])
    m.cube(bone, [gx - 0.12, gy - 0.9, gz + 1.55], [0.24, 2.6, 0.22], mats['strap'])
    m.cube(bone, [gx - 0.12, gy - 1.12, gz + 0.77], [0.24, 0.22, 0.78], mats['strap'])
    m.cube(bone, [gx - 0.1, gy + 1.78, gz + 0.5], [0.2, 0.22, 1.1], mats['strap'], rotation=[-25, 0, 0],
           pivot=[gx, gy + 1.9, gz + 0.5])


def body():
    m = CleanModel('thor', density=D, width=128, seed=11, light=(32.0, 0.0, 0.04))
    rig(m)
    build_body(m, M)
    c = m.cube
    c('head', [-3.6, 23.6, -4.3], [7.2, 2.6, 1.0], M['beard'])                     # beard + moustache
    c('head', [-3.9, 24.2, -3.6], [7.8, 2.2, 0.5], M['beard'])                     # jaw line under the cheeks
    c('torso', [-3.9, 16.7, -2.5], [7.8, 6.9, 0.5], M['plate'])
    c('torso', [-3.2, 23.3, -2.35], [6.4, 0.9, 4.7], M['collar'])
    c('torso', [-4.3, 11.8, -2.3], [8.6, 1.5, 4.6], M['belt'])
    c('torso', [-1.05, 11.85, -2.6], [2.1, 1.4, 0.3], M['buckle'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        side = sname(s)
        # layered pauldron: top plate, lower plate
        c(f'{side}_arm', [side_x(s, 3.9, 4.45), 22.3, -2.4], [4.45, 2.1, 4.8], M['pauldron'])
        c(f'{side}_arm', [side_x(s, 4.15, 4.2), 20.6, -2.3], [4.2, 1.8, 4.6], M['pauldron'])
        # bracer with a silver wrist rim
        c(f'{side}_forearm', [side_x(s, 3.75, 4.5), 15.2, -2.25], [4.5, 2.6, 4.5], M['bracer'])
        c(f'{side}_forearm', [side_x(s, 3.7, 4.6), 14.75, -2.3], [4.6, 0.5, 4.6], M['bracer_rim'])
        # knee-high boot: sole, shell, cuff, toe cap
        c(f'{side}_shin', [side_x(s, 0.02, 4.25), 0.0, -2.35], [4.25, 0.5, 4.75], M['sole'])
        c(f'{side}_shin', [side_x(s, 0.08, 4.12), 0.5, -2.2], [4.12, 5.0, 4.4], M['boot'])
        c(f'{side}_shin', [side_x(s, 0.05, 4.2), 5.0, -2.28], [4.2, 1.15, 4.56], M['cuff'])
        c(f'{side}_shin', [side_x(s, 0.3, 3.6), 0.5, -2.8], [3.6, 1.4, 0.6], M['boot'])
    return m


def gear():
    m = CleanModel('thor_gear', density=D, width=128, seed=13, light=(32.0, 0.0, 0.04))
    rig(m)
    m.bone('cape', 'torso', [0, 23.6, 2.4])
    m.bone('cape_lower', 'cape', [0, 15, 2.7])
    m.bone('hair', 'head', [0, 30, 0])
    m.bone('hammer', 'right_hand', [-6, 13.2, 0])
    c = m.cube
    # chest discs (ring + boss) in two columns, the two cape clasps at the collar
    for x in (-1.95, 1.95):
        for y in (21.3, 19.0, 16.95 + 0.05):
            c('torso', [x - 0.9, y - 0.9, -3.05], [1.8, 1.8, 0.3], M['disc'], faces=('north', 'east', 'west', 'up', 'down'))
            c('torso', [x - 0.5, y - 0.5, -3.25], [1.0, 1.0, 0.2], M['boss'], faces=('north', 'east', 'west', 'up', 'down'))
    for s in (-1, 1):
        cx = s * 3.4
        c('torso', [cx - 0.85, 21.6, -2.85], [1.7, 1.7, 0.3], M['disc'], faces=('north', 'east', 'west', 'up', 'down'))
        c('torso', [cx - 0.45, 22.0, -3.05], [0.9, 0.9, 0.2], M['boss'], faces=('north', 'east', 'west', 'up', 'down'))
    # cape: drape across the shoulders, four hanging folds at staggered depths
    c('cape', [-4.6, 20.4, 2.3], [9.2, 3.4, 0.55], M['cape_drape'])
    for x0, w, dz, y0 in ((-4.75, 2.45, 0.05, 1.4), (-2.35, 2.35, 0.25, 1.0), (0.0, 2.35, 0.12, 1.2), (2.35, 2.4, 0.3, 1.5)):
        c('cape_lower', [x0, y0, 2.55 + dz], [w, 15.0 - y0 + 5.4 - 0.1, 0.42], M['cape'])
    # hair: crown, swept-back hairline, side locks, framing locks, back locks
    c('hair', [-4.35, 31.05, -4.35], [8.7, 1.45, 8.7], M['hair'])
    c('hair', [-4.3, 30.35, -4.62], [8.6, 1.2, 0.85], M['hair'])
    for s in (-1, 1):
        for i, (z0, dz, y0, xo) in enumerate(((-3.7, 2.35, 23.4, 0.0), (-1.35, 2.45, 22.3, 0.12), (1.1, 2.6, 21.8, 0.05))):
            c('hair', [side_x(s, 3.95 + xo, 1.0), y0, z0], [1.0, 31.2 - y0, dz], M['hair' if i != 1 else 'hair_lo'])
        c('hair', [side_x(s, 3.0, 1.45), 23.0, -4.58], [1.45, 7.4, 0.95], M['hair_lo'])
    for x0, w, dz, y0 in ((-4.3, 2.15, 0.0, 21.0), (-2.15, 2.15, 0.12, 20.4), (0.0, 2.15, 0.06, 20.8), (2.15, 2.15, 0.16, 21.3)):
        c('hair', [x0, y0, 4.0 + dz], [w, 31.2 - y0, 0.85], M['hair' if dz in (0.0, 0.06) else 'hair_lo'])
    mjolnir(m, 'hammer', -6.0, 13.2, 0.0)
    return m


def build():
    b, g = body(), gear()
    b.occluders, g.occluders = [g], [b]
    return b, g


def thrown_mjolnir():
    m = CleanModel('mjolnir_mcu', density=6, width=128, seed=43)
    m.bone('hammer', None, [0, 0, 0])
    mjolnir(m, 'hammer', 0.0, 3.2, 0.0)
    return m
