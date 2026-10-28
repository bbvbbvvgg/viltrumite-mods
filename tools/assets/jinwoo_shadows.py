"""Shadow army models, textures and clips (own work, CC0): infantry, knight (+ Igris variant
texture with the crimson plume), insect (Beru) and beast (Tank). Each kind has
geo/jinwoo/shadow_<kind>.geo.json, textures/entity/jinwoo/shadow_<kind>.png (+ _glow) and
animations/jinwoo/shadow_<kind>.animation.json with idle, walk, attack, special, rise.
Clip lengths follow ShadowKind.attackLength (attack contact in the middle) and
JinwooRules.SHADOW_RISE (24 t)."""
import numpy as np

from jinwoo_clips import clip
from jinwoo_geo import Mat, Model, put, write_anims, write_json, write_png, RES
import os

EYE = [120, 150, 255, 255]
EYE_ELITE = [190, 150, 255, 255]


def seams(face, rgb, glow, rs, glow_amt=55):
    """Armour plates: dark-blue rim, faint violet seam glow along the top edge."""
    if face.w > 2 and face.h > 2:
        put(rgb, np.s_[0, :], '#2a3058', 0.6)
        put(rgb, np.s_[:, 0], '#1d2140', 0.5)
        put(rgb, np.s_[:, -1], '#1d2140', 0.5)
        if face.name in ('north', 'south', 'east', 'west'):
            glow[0, :] = [70, 80, 220, glow_amt]


def smoky(face, rgb, glow, rs):
    """Lower body dissolving into smoke: darker and noisier towards the bottom."""
    x, y, z = face.world()
    f = np.clip((6 - y) / 6, 0, 1)
    rgb[:] = rgb * (1 - 0.5 * f[..., None])
    seams(face, rgb, glow, rs, 30)


def visor(face, rgb, glow, rs):
    seams(face, rgb, glow, rs)
    if face.name == 'north':
        x, y, z = face.world()
        cy = face.cube.origin[1] + face.cube.size[1] * 0.52
        slit = (np.abs(y - cy) < 0.45) & (np.abs(x) > 0.5) & (np.abs(x) < 2.6)
        put(rgb, np.abs(y - cy) < 0.6, '#050508', 0.9)
        glow[slit] = EYE


def bug_eyes(face, rgb, glow, rs):
    seams(face, rgb, glow, rs, 40)
    if face.name == 'north':
        x, y, z = face.world()
        cy = face.cube.origin[1] + face.cube.size[1] * 0.6
        eye = (np.abs(y - cy) < 0.7) & (np.abs(np.abs(x) - 1.4) < 0.7)
        glow[eye] = EYE


def beast_eyes(face, rgb, glow, rs):
    fur(face, rgb, glow, rs)
    if face.name == 'north':
        x, y, z = face.world()
        cy = face.cube.origin[1] + face.cube.size[1] * 0.68
        eye = (np.abs(y - cy) < 0.5) & (np.abs(np.abs(x) - 2.2) < 0.7)
        glow[eye] = EYE


def fur(face, rgb, glow, rs):
    for _ in range(max(1, face.w * face.h // 6)):
        r, c = rs.randint(0, face.h), rs.randint(0, face.w)
        put(rgb, np.s_[r:r + 2, c], '#1e2036', 0.5)
    if face.name == 'up':
        put(rgb, np.s_[:, :], '#1a1c30', 0.3)


def blade(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], '#1a1d33', 0.6)
    if face.name in ('east', 'west'):
        glow[0, :] = [110, 130, 255, 200]
        glow[-1, :] = [90, 100, 240, 120]
    if face.name == 'up':
        glow[:] = [110, 130, 255, 140]


def plume(color):
    def fn(face, rgb, glow, rs):
        for _ in range(max(1, face.w // 2)):
            c = rs.randint(0, face.w)
            put(rgb, np.s_[:, c], '#000000', 0.25)
        glow[:] = [color[0] // 3, color[1] // 3, color[2] // 3, 90]
    return fn


def mats(plume_hex='#232a52', plume_rgb=(60, 70, 200)):
    return {
        'armor': Mat('#0e0f18', noise=5, edge=0.0, fn=seams),
        'dark': Mat('#08080d', noise=4, edge=0.0, fn=smoky),
        'visor': Mat('#0e0f18', noise=4, edge=0.0, fn=visor),
        'cloth': Mat('#0b0b12', noise=6, edge=0.0, fn=smoky),
        'blade': Mat('#121426', noise=3, edge=0.0, fn=blade, grain=1),
        'grip': Mat('#15131d', noise=2, edge=0.1),
        'plume': Mat(plume_hex, noise=10, edge=0.0, fn=plume(plume_rgb)),
        'chitin': Mat('#120f1c', noise=6, edge=0.0, fn=lambda f, r, g, rs: seams(f, r, g, rs, 45)),
        'bug_head': Mat('#120f1c', noise=5, edge=0.0, fn=bug_eyes),
        'wing': Mat('#1a1830', noise=8, edge=0.0, fn=lambda f, r, g, rs: seams(f, r, g, rs, 70)),
        'claw': Mat('#151527', noise=3, edge=0.0, fn=blade, grain=1),
        'fur': Mat('#0c0c13', noise=8, edge=0.0, fn=fur),
        'bear_head': Mat('#0c0c13', noise=7, edge=0.0, fn=beast_eyes),
        'horn': Mat('#16152a', noise=4, edge=0.0, fn=blade, grain=1),
    }


# ---- humanoids -----------------------------------------------------------------------------------------
def humanoid(m, M, tall=1.0, sword_len=10):
    leg = 12 * tall
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, leg, 0])
    m.cube('body', [-4, leg, -2.2], [8, 11, 4.4], M['armor'])
    m.cube('body', [-4.3, leg - 1.2, -2.5], [8.6, 2.2, 5], M['armor'])
    m.cube('body', [-4.2, leg - 5, -2.7], [3.9, 4, 0.6], M['cloth'], rotation=[-6, 0, 0], pivot=[0, leg - 1, -2.6])
    m.cube('body', [0.3, leg - 5, -2.7], [3.9, 4, 0.6], M['cloth'], rotation=[-6, 0, 0], pivot=[0, leg - 1, -2.6])
    m.cube('body', [-4.2, leg - 5, 2.1], [8.4, 4, 0.6], M['cloth'], rotation=[6, 0, 0], pivot=[0, leg - 1, 2.4])
    top = leg + 11
    m.bone('head', 'body', [0, top, 0])
    m.cube('head', [-3.6, top, -3.6], [7.2, 7.4, 7.2], M['visor'])
    for side, sx in (('right', -1), ('left', 1)):
        p = [sx * 5.4, top - 1.6, 0]
        m.bone(f'{side}_arm', 'body', p)
        x0 = -7.6 if sx < 0 else 4.4
        m.cube(f'{side}_arm', [x0, top - 7, -1.6], [3.2, 6.6, 3.2], M['armor'])
        m.cube(f'{side}_arm', [x0 - 0.6 if sx < 0 else x0 - 0.4, top - 2.8, -2.6], [4.2, 3.4, 5.2], M['armor'],
               rotation=[0, 0, sx * -12], pivot=[sx * 5.4, top - 1, 0])
        m.bone(f'{side}_forearm', f'{side}_arm', [sx * 6.0, top - 7, 0])
        m.cube(f'{side}_forearm', [x0 - 0.1, top - 12, -1.7], [3.4, 5.2, 3.4], M['armor'])
        m.bone(f'{side}_hand', f'{side}_forearm', [sx * 6.0, top - 12, 0])
        m.cube(f'{side}_hand', [x0 + 0.1, top - 14.2, -1.5], [3.0, 2.4, 3.0], M['dark'])
        m.bone(f'{side}_leg', 'root', [sx * 2, leg, 0])
        lx = -3.8 if sx < 0 else 0.2
        m.cube(f'{side}_leg', [lx, leg / 2, -1.8], [3.6, leg / 2, 3.6], M['cloth'])
        m.bone(f'{side}_shin', f'{side}_leg', [sx * 2, leg / 2, 0])
        m.cube(f'{side}_shin', [lx - 0.1, 0, -2], [3.8, leg / 2, 4], M['armor'])
        m.cube(f'{side}_shin', [lx, 0, -2.7], [3.6, 1.6, 0.8], M['armor'])
    gy = top - 13.2
    m.bone('weapon', 'right_hand', [-6.0, gy, 0])
    m.cube('weapon', [-6.4, gy - 0.5, -1.6], [0.8, 1.0, 3.4], M['grip'])
    m.cube('weapon', [-6.6, gy - 1.6, -2.3], [1.2, 3.2, 0.7], M['armor'])
    m.cube('weapon', [-6.25, gy - 0.9, -2.3 - sword_len], [0.5, 1.8, sword_len], M['blade'])
    m.cube('weapon', [-6.2, gy - 0.5, -3.3 - sword_len], [0.4, 1.0, 1.0], M['blade'])
    return top


def infantry():
    M = mats()
    m = Model('shadow_infantry', density=2, width=128, seed=11)
    top = humanoid(m, M, 1.0, 8)
    m.cube('head', [-0.5, top + 7.0, -4.2], [1.0, 2.6, 8.2], M['armor'])        # crest
    return m


def knight(elite=False):
    M = mats('#9c1026' if elite else '#232a52', (200, 20, 40) if elite else (60, 70, 200))
    m = Model('shadow_knight', density=2, width=128, seed=12)
    top = humanoid(m, M, 1.12, 15)
    m.cube('head', [-3.8, top + 0.2, -4.2], [7.6, 2.2, 0.8], M['armor'])        # face guard
    m.cube('head', [-0.6, top + 7.2, -2.2], [1.2, 1.6, 4.4], M['armor'])        # plume socket
    m.bone('plume', 'head', [0, top + 8.4, 1.5])
    m.cube('plume', [-0.9, top + 7.6, 1.2], [1.8, 1.6, 4.0], M['plume'], rotation=[-20, 0, 0], pivot=[0, top + 8.4, 1.5])
    m.bone('plume_tip', 'plume', [0, top + 7.0, 5.0])
    m.cube('plume_tip', [-0.8, top + 2.4, 4.4], [1.6, 4.8, 1.4], M['plume'], rotation=[12, 0, 0], pivot=[0, top + 7.0, 5.0])
    leg = 12 * 1.12
    m.bone('cape', 'body', [0, top - 0.4, 2.4])
    m.cube('cape', [-4.6, leg - 2, 2.3], [9.2, top - leg + 1.6, 0.6], M['cloth'])
    m.bone('cape_lower', 'cape', [0, leg - 2, 2.6])
    m.cube('cape_lower', [-4.8, 1.0, 2.3], [9.6, leg - 3, 0.6], M['cloth'])
    return m


def insect():
    M = mats()
    m = Model('shadow_insect', density=2, width=128, seed=13)
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, 14, 0])
    m.cube('body', [-3.2, 14, -2], [6.4, 9, 4], M['chitin'])
    m.cube('body', [-3.8, 19, -2.6], [7.6, 4.2, 5.2], M['chitin'])                # thorax plate
    m.cube('body', [-2.6, 10.5, -1.6], [5.2, 4, 3.4], M['chitin'], rotation=[12, 0, 0], pivot=[0, 14, 0])
    m.bone('head', 'body', [0, 23, 0])
    m.cube('head', [-2.6, 23, -3.4], [5.2, 4.6, 5.6], M['bug_head'])
    m.cube('head', [-2.0, 22.4, -4.6], [1.2, 1.6, 1.6], M['claw'], rotation=[0, 20, 0], pivot=[-1.4, 23, -3.6])
    m.cube('head', [0.8, 22.4, -4.6], [1.2, 1.6, 1.6], M['claw'], rotation=[0, -20, 0], pivot=[1.4, 23, -3.6])
    for side, sx in (('right', -1), ('left', 1)):
        m.bone(f'antenna_{side}', 'head', [sx * 1.2, 27.6, -2.2])
        m.cube(f'antenna_{side}', [sx * 1.2 - 0.25, 27.6, -2.4], [0.5, 6, 0.5], M['claw'], rotation=[-30, 0, sx * -18],
               pivot=[sx * 1.2, 27.6, -2.2])
        m.bone(f'wing_{side}', 'body', [sx * 1.2, 22, 2.0])
        wx = -8.2 if sx < 0 else 1.2
        m.cube(f'wing_{side}', [wx, 9, 2.0], [7.0, 13, 0.3], M['wing'], rotation=[14, sx * -20, sx * -14], pivot=[sx * 1.2, 22, 2.0])
        p = [sx * 4.6, 22, 0]
        x0 = -6.2 if sx < 0 else 3.4
        m.bone(f'{side}_arm', 'body', p)
        m.cube(f'{side}_arm', [x0, 15.5, -1.2], [2.8, 7, 2.6], M['chitin'])
        m.cube(f'{side}_arm', [x0 - 0.3, 21, -1.8], [3.4, 2.4, 3.6], M['chitin'])
        m.bone(f'{side}_forearm', f'{side}_arm', [sx * 4.8, 15.5, 0])
        m.cube(f'{side}_forearm', [x0 + 0.1, 9.5, -1.1], [2.6, 6, 2.4], M['chitin'])
        m.bone(f'{side}_hand', f'{side}_forearm', [sx * 4.8, 9.5, 0])
        for i, dz in enumerate((-0.9, 0.2)):
            m.cube(f'{side}_hand', [x0 + 0.6 + i * 1.0, 2.0, dz], [0.6, 7.5, 0.7], M['claw'], rotation=[-12, 0, 0],
                   pivot=[sx * 4.8, 9.5, 0])
        lx = -3.4 if sx < 0 else 0.4
        m.bone(f'{side}_leg', 'root', [sx * 1.9, 12, 0])
        m.cube(f'{side}_leg', [lx, 6.5, -1.5], [3.0, 5.8, 3.0], M['chitin'], rotation=[-28, 0, 0], pivot=[sx * 1.9, 12, 0])
        m.bone(f'{side}_shin', f'{side}_leg', [sx * 1.9, 7.2, -2.8])
        m.cube(f'{side}_shin', [lx + 0.2, 1.2, -3.8], [2.6, 6.2, 2.6], M['chitin'], rotation=[34, 0, 0], pivot=[sx * 1.9, 7.2, -2.8])
        m.cube(f'{side}_shin', [lx - 0.2, 0, -4.6], [3.4, 1.4, 4.4], M['claw'])
    return m


def beast():
    M = mats()
    m = Model('shadow_beast', density=2, width=256, seed=14)
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, 14, 0])
    m.cube('body', [-6, 8, -9], [12, 12, 18], M['fur'])
    m.cube('body', [-6.5, 12, -10], [13, 10, 9], M['fur'])                        # shoulders hump
    for i, (x, z, h, r) in enumerate(((-3, -8, 5, -20), (3, -8, 5, -20), (-2, -3, 4, -30), (2, -3, 4, -30),
                                      (0, 1, 4, -35), (-3.5, -6, 3, 30), (3.5, -6, 3, 30))):
        rz = -25 if x < -1 else 25 if x > 1 else 0
        m.cube('body', [x - 0.8, 21, z], [1.6, h, 1.8], M['horn'], rotation=[r, 0, rz], pivot=[x, 21.5, z + 0.9])
    m.bone('head', 'body', [0, 16, -10])
    m.cube('head', [-4.5, 11, -17], [9, 8, 7.5], M['bear_head'])
    m.cube('head', [-2.6, 11, -20], [5.2, 4.2, 3.2], M['fur'])                    # snout
    m.bone('jaw', 'head', [0, 11.5, -14])
    m.cube('jaw', [-2.4, 9.6, -19.6], [4.8, 1.6, 5.6], M['fur'])
    for sx in (-1, 1):
        m.cube('head', [sx * 3.3 - 1, 18.6, -13], [2, 2, 1.2], M['fur'])          # ears
        m.cube('head', [sx * 2.4 - 0.6, 18.4, -16.5], [1.2, 4.5, 1.2], M['horn'], rotation=[-28, 0, sx * -22],
               pivot=[sx * 2.4, 18.6, -16])
    for side, sx in (('right', -1), ('left', 1)):
        for part, z, ln in (('front', -6, 0), ('hind', 6, 1)):
            b = f'{part}_{side}_leg'
            m.bone(b, 'body', [sx * 3.8, 12, z])
            m.cube(b, [sx * 3.8 - 2.6, 4, z - 2.6], [5.2, 9, 5.2], M['fur'])
            m.bone(b + '_paw', b, [sx * 3.8, 4.5, z])
            m.cube(b + '_paw', [sx * 3.8 - 2.4, 0, z - 2.5], [4.8, 5, 4.8], M['fur'])
            m.cube(b + '_paw', [sx * 3.8 - 2.2, 0, z - 3.8], [4.4, 1.4, 1.4], M['horn'])
    m.cube('body', [-1.2, 15, 8.5], [2.4, 2.4, 2.6], M['fur'], rotation=[-30, 0, 0], pivot=[0, 16, 9])
    return m


# ---- clips ---------------------------------------------------------------------------------------------
def hum_clips(kind, attack_len, contact, special):
    idle = {'body': (2, 0, 0), 'head': (-2, 0, 0), 'right_arm': (-14, 0, 6), 'right_forearm': (-20, 0, 0), 'weapon': (60, 0, 0),
            'left_arm': (-6, 0, -6), 'left_forearm': (-14, 0, 0), 'right_leg': (-4, 0, 0), 'left_leg': (4, 0, 0),
            'right_shin': (4, 0, 0), 'left_shin': (4, 0, 0), 'root': {'rot': (0, 0, 0), 'pos': (0, 0, 0)}}
    if kind == 'knight':
        idle.update({'right_arm': (-8, 0, 8), 'right_forearm': (-6, 0, 0), 'weapon': (100, 0, 0), 'cape': (6, 0, 0), 'plume': (0, 0, 0)})
    breath = dict(idle, body=(4, 0, 0), head=(-4, 0, 0))
    walk_a = dict(idle, body=(10, 0, 0), right_leg=(-34, 0, 0), right_shin=(10, 0, 0), left_leg=(30, 0, 0), left_shin=(30, 0, 0),
                  left_arm=(-26, 0, -6), right_arm=(10, 0, 8))
    walk_b = dict(walk_a, right_leg=(30, 0, 0), right_shin=(30, 0, 0), left_leg=(-34, 0, 0), left_shin=(10, 0, 0),
                  left_arm=(14, 0, -6), right_arm=(-26, 0, 8))
    if kind == 'knight':
        walk_a.update(cape=(20, 0, 0), right_arm=(-8, 0, 8))
        walk_b.update(cape=(26, 0, 0), right_arm=(-8, 0, 8))
    up = dict(idle, body=(-6, 20, 0), right_arm=(-160, 20, 20), right_forearm=(-30, 0, 0), weapon=(70, 0, 0))
    down = dict(idle, root={'rot': (0, 0, 0), 'pos': (0, -1.5, 0)}, body=(20, -20, 0), right_arm=(-50, -30, 0),
                right_forearm=(-10, 0, 0), weapon=(80, 0, 0), right_leg=(-40, 0, 0), right_shin=(40, 0, 0), left_leg=(20, 0, 0))
    clips = [clip('idle', 40, [(0, idle), (20, breath), (40, idle)], loop=True),
             clip('walk', 16, [(0, walk_a), (8, walk_b), (16, walk_a)], loop=True),
             clip('attack', attack_len, [(0, idle), (contact - 2, up), (contact, down), (attack_len - 2, down), (attack_len, idle)])]
    if special:
        lunge = dict(down, root={'rot': (0, 0, 0), 'pos': (0, -3, 0)}, body=(30, 0, 0), right_arm=(-96, 0, 0), right_forearm=(0, 0, 0),
                     weapon=(90, 0, 0), right_leg=(-60, 0, 0), right_shin=(30, 0, 0), left_leg=(40, 0, 0), left_shin=(10, 0, 0))
        clips.append(clip('special', 16, [(0, idle), (5, dict(idle, root={'rot': (0, 0, 0), 'pos': (0, -2, 0)}, body=(20, 0, 0),
                                                                   right_arm=(30, 0, 20))), (8, lunge), (13, lunge), (16, idle)]))
    kneel = dict(idle, root={'rot': (0, 0, 0), 'pos': (0, -5, 0)}, body=(30, 0, 0), head=(20, 0, 0), right_leg=(-80, 0, 0),
                 right_shin=(80, 0, 0), left_leg=(10, 0, 0), left_shin=(100, 0, 0), right_arm=(-30, 0, 10), left_arm=(-20, 0, -20))
    clips.append(clip('rise', 24, [(0, kneel), (6, kneel), (18, dict(idle, body=(-8, 0, 0), head=(-16, 0, 0))), (20, idle), (24, idle)]))
    return clips


def insect_clips():
    idle = {'root': {'rot': (0, 0, 0), 'pos': (0, -0.5, 0)}, 'body': (14, 0, 0), 'head': (-10, 0, 0), 'right_arm': (-30, 0, 14),
            'right_forearm': (-50, 0, 0), 'left_arm': (-30, 0, -14), 'left_forearm': (-50, 0, 0), 'wing_right': (0, 0, 0),
            'wing_left': (0, 0, 0), 'antenna_right': (0, 0, 0), 'antenna_left': (0, 0, 0), 'right_leg': (0, 0, 0),
            'left_leg': (0, 0, 0), 'right_shin': (0, 0, 0), 'left_shin': (0, 0, 0)}
    flick = dict(idle, antenna_right=(-14, 0, 0), antenna_left=(10, 0, 0), wing_right=(0, 10, 0), wing_left=(0, -10, 0))
    wa = dict(idle, body=(24, 0, 0), right_leg=(-30, 0, 0), left_leg=(26, 0, 0), right_shin=(-10, 0, 0), left_shin=(20, 0, 0))
    wb = dict(wa, right_leg=(26, 0, 0), left_leg=(-30, 0, 0), right_shin=(20, 0, 0), left_shin=(-10, 0, 0))
    s1 = dict(idle, body=(20, -20, 0), right_arm=(-150, 30, 30), right_forearm=(-20, 0, 0))
    s2 = dict(idle, body=(26, 20, 0), right_arm=(-50, -30, 0), right_forearm=(-10, 0, 0), left_arm=(-150, -30, -30))
    s3 = dict(idle, body=(26, -10, 0), left_arm=(-50, 30, 0), left_forearm=(-10, 0, 0), right_arm=(-50, -30, 0))
    crouch = dict(idle, root={'rot': (0, 0, 0), 'pos': (0, -3, 0)}, body=(30, 0, 0), right_leg=(-30, 0, 0), left_leg=(-30, 0, 0),
                  right_shin=(30, 0, 0), left_shin=(30, 0, 0), wing_right=(0, 30, 20), wing_left=(0, -30, -20))
    leap = dict(idle, root={'rot': (0, 0, 0), 'pos': (0, 4, 0)}, body=(50, 0, 0), right_arm=(-160, 0, 20), left_arm=(-160, 0, -20),
                right_leg=(40, 0, 0), left_leg=(40, 0, 0), wing_right=(0, 50, 40), wing_left=(0, -50, -40))
    strike = dict(idle, body=(40, 0, 0), right_arm=(-40, 0, 0), left_arm=(-40, 0, 0))
    rise = dict(crouch, root={'rot': (0, 0, 0), 'pos': (0, -6, 0)}, head=(20, 0, 0))
    return [clip('idle', 40, [(0, idle), (20, flick), (40, idle)], loop=True),
            clip('walk', 10, [(0, wa), (5, wb), (10, wa)], loop=True),
            clip('attack', 10, [(0, idle), (2, s1), (3, s2), (6, s3), (10, idle)]),
            clip('special', 16, [(0, idle), (4, crouch), (7, leap), (11, strike), (16, idle)]),
            clip('rise', 24, [(0, rise), (6, rise), (18, dict(idle, wing_right=(0, 40, 30), wing_left=(0, -40, -30))), (24, idle)])]


def beast_clips():
    legs = ('front_right_leg', 'front_left_leg', 'hind_right_leg', 'hind_left_leg')
    idle = {'root': {'rot': (0, 0, 0), 'pos': (0, 0, 0)}, 'body': (0, 0, 0), 'head': (6, 0, 0), 'jaw': (0, 0, 0)}
    idle.update({l: (0, 0, 0) for l in legs})
    idle.update({l + '_paw': (0, 0, 0) for l in legs})
    breath = dict(idle, body=(-2, 0, 0), head=(2, 0, 0), jaw=(6, 0, 0))
    wa = dict(idle, front_right_leg=(-30, 0, 0), hind_left_leg=(-26, 0, 0), front_left_leg=(26, 0, 0), hind_right_leg=(24, 0, 0),
              front_left_leg_paw=(-30, 0, 0), hind_right_leg_paw=(-20, 0, 0), body=(2, 0, 3))
    wb = dict(idle, front_left_leg=(-30, 0, 0), hind_right_leg=(-26, 0, 0), front_right_leg=(26, 0, 0), hind_left_leg=(24, 0, 0),
              front_right_leg_paw=(-30, 0, 0), hind_left_leg_paw=(-20, 0, 0), body=(2, 0, -3))
    rear = dict(idle, root={'rot': (-34, 0, 0), 'pos': (0, 2, 4)}, front_right_leg=(-90, 0, 10), front_left_leg=(-90, 0, -10),
                front_right_leg_paw=(-40, 0, 0), front_left_leg_paw=(-40, 0, 0), head=(26, 0, 0), jaw=(30, 0, 0),
                hind_right_leg=(34, 0, 0), hind_left_leg=(34, 0, 0))
    slam = dict(idle, root={'rot': (14, 0, 0), 'pos': (0, -2, 0)}, front_right_leg=(-40, 0, 0), front_left_leg=(-40, 0, 0),
                head=(-10, 0, 0), jaw=(10, 0, 0))
    roar = dict(idle, root={'rot': (-10, 0, 0), 'pos': (0, 0.5, 0)}, head=(-30, 0, 0), jaw=(42, 0, 0), front_right_leg=(-10, 0, 0),
                front_left_leg=(-10, 0, 0))
    shake = dict(roar, head=(-26, 10, 0))
    shake2 = dict(roar, head=(-26, -10, 0))
    low = dict(idle, root={'rot': (0, 0, 0), 'pos': (0, -8, 0)}, head=(20, 0, 0))
    return [clip('idle', 40, [(0, idle), (20, breath), (40, idle)], loop=True),
            clip('walk', 20, [(0, wa), (10, wb), (20, wa)], loop=True),
            clip('attack', 20, [(0, idle), (8, rear), (12, slam), (16, slam), (20, idle)]),
            clip('special', 20, [(0, idle), (4, roar), (8, shake), (12, shake2), (16, roar), (20, idle)]),
            clip('rise', 24, [(0, low), (6, low), (18, roar), (24, idle)])]


def write_kind(name, model, clips, extra_textures=()):
    base, glow = model.paint()
    model.write(f'geo/jinwoo/shadow_{name}.geo.json', f'textures/entity/jinwoo/shadow_{name}.png',
                f'textures/entity/jinwoo/shadow_{name}_glow.png', base, glow)
    size = write_anims(f'animations/jinwoo/shadow_{name}.animation.json', clips)
    for tex_name, (b2, g2) in extra_textures:
        write_png(f'textures/entity/jinwoo/{tex_name}.png', b2)
    print(name, model.width, model.height, 'anim', size)


def main():
    write_kind('infantry', infantry(), hum_clips('infantry', 12, 6, False))
    k = knight()
    write_kind('knight', k, hum_clips('knight', 16, 9, True))
    igris = knight(elite=True)
    igris.pack()
    b2, g2 = igris.paint()
    write_png('textures/entity/jinwoo/shadow_igris.png', b2)
    g = g2.astype(float)
    g[..., :3] *= g[..., 3:4] / 255.0
    g[..., 3] = np.where(g[..., 3] > 0, 255, 0)
    write_png('textures/entity/jinwoo/shadow_igris_glow.png', g.astype(np.uint8))
    write_kind('insect', insect(), insect_clips())
    write_kind('beast', beast(), beast_clips())


if __name__ == '__main__':
    main()
