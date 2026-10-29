"""Sung Jinwoo body rig on the player's own skin + his gear (own work, CC0).

The body is the vanilla player model (classic 4 px arms and slim 3 px arms) in the standard
64x64 skin layout, so the renderer binds the player's skin texture. Limbs are split at the
elbows/knees/wrists with per-face UVs cut from the same skin boxes (inner layer + outer layer),
which keeps any skin correct while the joints bend.
Gear (separate model, own atlas): daggers right_dagger "Kasaka's Fang" / left_dagger "Knight
Killer" as children of the hands, the Monarch's high collar, two rear coat tails (2 segments each,
secondary dynamics in the renderer) and an eye plate for the glow pass. Gear bones repeat the body
skeleton names and pivots so one clip poses both models identically.
Outputs: geo/jinwoo/jinwoo.geo.json, jinwoo_slim.geo.json, jinwoo_gear.geo.json,
jinwoo_arms.geo.json, jinwoo_arms_slim.geo.json, jinwoo_arms_gear.geo.json,
textures/entity/jinwoo/jinwoo_gear.png (+ _glow).
"""
import copy
import os

import numpy as np

from jinwoo_geo import Cube, Mat, Model, put, write_json, RES

COAT, COAT_HI, COAT_LO, SHEEN = '#16171e', '#2c2e3b', '#0c0d11', '#3a2d63'

# ---- skeleton ------------------------------------------------------------------------------------------
ELBOW_Y, WRIST_Y, KNUCKLE_Y, KNEE_Y = 18, 15, 13, 6


def skeleton(m, slim):
    aw = 3 if slim else 4
    rx0 = -4 - aw                        # right arm outer x
    cx = rx0 + aw / 2                    # right arm centre
    m.bone('root', None, [0, 0, 0])
    m.bone('waist', 'root', [0, 12, 0])
    m.bone('torso', 'waist', [0, 12, 0])
    m.bone('collar', 'torso', [0, 24, 0])
    m.bone('head', 'torso', [0, 24, 0])
    m.bone('right_arm', 'torso', [-5, 22, 0])
    m.bone('right_forearm', 'right_arm', [cx, ELBOW_Y, 0])
    m.bone('right_hand', 'right_forearm', [cx, WRIST_Y, 0])
    m.bone('right_fingers', 'right_hand', [-4, KNUCKLE_Y, 0])
    m.bone('right_thumb', 'right_hand', [-4, 14, -1])
    m.bone('right_dagger', 'right_hand', [-5.8, 12.6, 0])
    m.bone('left_arm', 'torso', [5, 22, 0])
    m.bone('left_forearm', 'left_arm', [-cx, ELBOW_Y, 0])
    m.bone('left_hand', 'left_forearm', [-cx, WRIST_Y, 0])
    m.bone('left_fingers', 'left_hand', [4, KNUCKLE_Y, 0])
    m.bone('left_thumb', 'left_hand', [4, 14, -1])
    m.bone('left_dagger', 'left_hand', [5.8, 12.6, 0])
    for side, sx in (('right', -1), ('left', 1)):
        m.bone(f'coat_tail_{side}', 'waist', [sx * 2.1, 12, 2.4])
        m.bone(f'coat_tail_{side}_lower', f'coat_tail_{side}', [sx * 2.1, 7.4, 2.5])
    m.bone('right_leg', 'root', [-1.9, 12, 0])
    m.bone('right_shin', 'right_leg', [-1.9, KNEE_Y, 0])
    m.bone('left_leg', 'root', [1.9, 12, 0])
    m.bone('left_shin', 'left_leg', [1.9, KNEE_Y, 0])
    return aw, rx0


# ---- skin boxes ----------------------------------------------------------------------------------------
def seg_uv(u, v, w, h, d, r0, r1, top, bottom):
    """Per-face UVs of rows [r0, r1) of a vanilla skin box at (u, v) sized w x h x d (texels)."""
    rows = r1 - r0
    uv = {'west': (u, v + d + r0, d, rows), 'north': (u + d, v + d + r0, w, rows),
          'east': (u + d + w, v + d + r0, d, rows), 'south': (u + d + w + d, v + d + r0, w, rows)}
    # caps: the top cap always comes from the box top, the bottom cap from the box bottom (joint fill)
    uv['up'] = (u + d, v, w, d) if top else (u + d + w, v, w, d)
    uv['down'] = (u + d + w, v, w, d) if bottom else (u + d, v, w, d)
    return uv


def skin_box(m, bone, origin, size, uv0, r0, r1, layer=0):
    """Rows r0..r1 of a skin box (origin/size of the full box); layer 1 = outer layer (inflated)."""
    w, h, d = size
    o = [origin[0], origin[1] + h - r1, origin[2]]
    c = Cube(bone, o, [w, r1 - r0, d], None, inflate=(0.5 if bone == 'head' else 0.25) * layer)
    c.uv = seg_uv(uv0[0], uv0[1], w, h, d, r0, r1, r0 == 0, r1 == h)
    m.by_name[bone].cubes.append(c)


def body_model(slim):
    m = Model('jinwoo_slim' if slim else 'jinwoo', density=1, width=64)
    m.height = 64
    m.skin_boxes = []
    aw, rx0 = skeleton(m, slim)
    for layer, uvs in ((0, dict(head=(0, 0), body=(16, 16), ra=(40, 16), la=(32, 48), rl=(0, 16), ll=(16, 48))),
                       (1, dict(head=(32, 0), body=(16, 32), ra=(40, 32), la=(48, 48), rl=(0, 32), ll=(0, 48)))):
        skin_box(m, 'head', [-4, 24, -4], [8, 8, 8], uvs['head'], 0, 8, layer)
        skin_box(m, 'torso', [-4, 12, -2], [8, 12, 4], uvs['body'], 0, 12, layer)
        m.skin_boxes += [([-4, 24, -4], [8, 8, 8], uvs['head'], layer, 'head'),
                         ([-4, 12, -2], [8, 12, 4], uvs['body'], layer, 'torso')]
        for side, x0, key in (('right', rx0, 'ra'), ('left', 4, 'la')):
            box = ([x0, 12, -2], [aw, 12, 4])
            m.skin_boxes.append((box[0], box[1], uvs[key], layer, f'{side}_arm'))
            skin_box(m, f'{side}_arm', *box, uvs[key], 0, 24 - ELBOW_Y, layer)
            skin_box(m, f'{side}_forearm', *box, uvs[key], 24 - ELBOW_Y, 24 - WRIST_Y, layer)
            skin_box(m, f'{side}_hand', *box, uvs[key], 24 - WRIST_Y, 24 - KNUCKLE_Y, layer)
            skin_box(m, f'{side}_fingers', *box, uvs[key], 24 - KNUCKLE_Y, 12, layer)
        for side, x0, key in (('right', -3.9, 'rl'), ('left', -0.1, 'll')):
            box = ([x0, 0, -2], [4, 12, 4])
            m.skin_boxes.append((box[0], box[1], uvs[key], layer, f'{side}_leg'))
            skin_box(m, f'{side}_leg', *box, uvs[key], 0, 12 - KNEE_Y, layer)
            skin_box(m, f'{side}_shin', *box, uvs[key], 12 - KNEE_Y, 12, layer)
    return m


# ---- gear materials ------------------------------------------------------------------------------------
def rim(face, rgb, amount=0.45):
    if face.name in ('north', 'south', 'east', 'west') and face.w > 3:
        put(rgb, np.s_[:, 0], COAT_HI, amount)
        put(rgb, np.s_[:, -1], COAT_HI, amount)


def coat_fn(face, rgb, glow, rs):
    if face.name in ('north', 'south', 'east', 'west') and face.h > 3:
        for _ in range(max(1, face.w // 5)):
            c = rs.randint(1, max(2, face.w - 1))
            put(rgb, np.s_[rs.randint(0, max(1, face.h // 2)):, c], COAT_LO, 0.55)
        x, y, z = face.world()
        f = np.clip((11.5 - y) / 8.0, 0, 1) * 0.2
        rgb[:] = rgb * (1 - f[..., None]) + np.array([52, 44, 92], float) * f[..., None]
    rim(face, rgb)


def hem_fn(face, rgb, glow, rs):
    coat_fn(face, rgb, glow, rs)
    if face.name in ('north', 'south', 'east', 'west') and face.h > 3:
        put(rgb, np.s_[-2:, :], COAT_HI, 0.8)
        put(rgb, np.s_[-3:-2, :], SHEEN, 0.7)
        glow[-1, :] = [110, 80, 230, 70]


def collar_fn(face, rgb, glow, rs):
    if face.name in ('north', 'south', 'east', 'west'):
        put(rgb, np.s_[0, :], COAT_HI, 0.9)
        put(rgb, np.s_[1, :], SHEEN, 0.5)
    rim(face, rgb, 0.3)


def eyes_fn(face, rgb, glow, rs):
    """Eye plate: transparent in the base pass, glowing violet-blue irises in the glow pass (skin eye rows)."""
    if face.name != 'north':
        return
    x, y, z = face.world()
    ax = np.abs(x)
    iris = (ax > 1.0) & (ax < 3.0) & (y > 27.0) & (y < 28.0)
    core = (ax > 1.25) & (ax < 2.25) & (y > 27.0) & (y < 28.0)
    glow[iris] = [120, 95, 255, 150]
    glow[core] = [175, 155, 255, 255]


def fang_blade(face, rgb, glow, rs):
    """Kasaka's Fang: violet at the guard to teal at the tip, glowing cutting edges."""
    x, y, z = face.world()
    t = np.clip((-z - 2.2) / 9.5, 0, 1)
    a, b = np.array([60, 38, 110], float), np.array([58, 178, 170], float)
    rgb[:] = rgb * 0.3 + (a[None, None] * (1 - t[..., None]) + b[None, None] * t[..., None]) * 0.78
    if face.name in ('east', 'west'):
        put(rgb, np.s_[0, :], '#a9f5ee', 0.8)
        put(rgb, np.s_[-1, :], '#c5a6ff', 0.7)
        put(rgb, np.s_[face.h // 2, :], '#2a1b4d', 0.5)
        glow[0, :] = [120, 240, 230, 220]
        glow[-1, :] = [170, 120, 255, 200]
    if face.name in ('up', 'down'):
        glow[:] = [120, 240, 230, 160] if face.name == 'up' else [170, 120, 255, 150]


def knight_blade(face, rgb, glow, rs):
    """Knight Killer: black blade with a violet edge."""
    rgb[:] = rgb * 0.6 + np.array([14, 13, 18], float) * 0.4
    if face.name in ('east', 'west'):
        put(rgb, np.s_[0, :], '#7a4fd6', 0.9)
        put(rgb, np.s_[-1, :], '#5b3aa8', 0.6)
        put(rgb, np.s_[face.h // 2, :], '#26242e', 0.6)
        glow[0, :] = [150, 90, 255, 230]
        glow[-1, :] = [120, 70, 220, 120]
    if face.name == 'up':
        glow[:] = [150, 90, 255, 170]


def grip_wrap(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, (np.floor(z * 2.5) % 2) == 0, '#4a3c60', 0.5)


def gem(color):
    def fn(face, rgb, glow, rs):
        glow[:] = color
    return fn


def hair_fn(face, rgb, glow, rs):
    if face.w > 1:
        for _ in range(max(1, face.w // 3)):
            put(rgb, np.s_[:, rs.randint(0, face.w)], '#262b42', 0.35 if face.name != 'up' else 0.6)


M = {
    'hair': Mat('#0c0c11', noise=7, edge=0.12, top=1.25, fn=hair_fn),
    'coat': Mat(COAT, noise=5, edge=0.0, fn=coat_fn),
    'hem': Mat(COAT, noise=5, edge=0.0, fn=hem_fn),
    'collar': Mat('#14151c', noise=4, edge=0.15, fn=collar_fn),
    'eyes': Mat('#000000', noise=0, edge=0.0, fn=eyes_fn, alpha=0),
    'fang_blade': Mat('#3c2a6c', noise=4, edge=0.0, fn=fang_blade, grain=1),
    'fang_grip': Mat('#271f31', noise=3, edge=0.1, fn=grip_wrap, grain=1),
    'fang_guard': Mat('#1c1b26', noise=3, edge=0.15),
    'fang_gem': Mat('#4fd6cc', noise=2, edge=0.0, fn=gem([110, 230, 220, 230])),
    'knight_blade': Mat('#121216', noise=3, edge=0.0, fn=knight_blade, grain=1),
    'knight_grip': Mat('#1a1a20', noise=3, edge=0.1, fn=grip_wrap, grain=1),
    'knight_guard': Mat('#0f0f13', noise=2, edge=0.15),
    'knight_gem': Mat('#8a5cff', noise=2, edge=0.0, fn=gem([150, 100, 255, 230])),
}


def dagger(m, bone, kind):
    """Dagger in hand space: handle through the fist along Z, blade towards -Z, edge up (+Y)."""
    gx, gy, gz = m.by_name[bone].pivot
    blade, handle, guard, g = [M[f'{kind}_{p}'] for p in ('blade', 'grip', 'guard', 'gem')]
    m.cube(bone, [gx - 0.4, gy - 0.45, gz - 1.7], [0.8, 0.9, 3.6], handle)
    m.cube(bone, [gx - 0.5, gy - 0.55, gz + 1.9], [1.0, 1.1, 0.8], guard)
    if kind == 'fang':
        m.cube(bone, [gx - 0.6, gy - 1.35, gz - 2.4], [1.2, 2.7, 0.7], guard)
        m.cube(bone, [gx - 0.25, gy - 0.25, gz - 2.55], [0.5, 0.5, 0.2], g)
        m.cube(bone, [gx - 0.22, gy - 0.6, gz - 6.6], [0.44, 1.5, 4.2], blade)
        m.cube(bone, [gx - 0.2, gy - 0.45, gz - 9.6], [0.4, 1.25, 3.0], blade, rotation=[-4, 0, 0], pivot=[gx, gy, gz - 6.6])
        m.cube(bone, [gx - 0.17, gy - 0.2, gz - 11.4], [0.34, 0.85, 1.9], blade, rotation=[-10, 0, 0], pivot=[gx, gy, gz - 9.6])
        m.cube(bone, [gx - 0.15, gy + 0.85, gz - 4.4], [0.3, 0.45, 0.9], blade)
    else:
        m.cube(bone, [gx - 0.55, gy - 1.2, gz - 2.3], [1.1, 2.4, 0.55], guard)
        m.cube(bone, [gx - 0.2, gy - 0.2, gz - 2.45], [0.4, 0.4, 0.2], g)
        m.cube(bone, [gx - 0.2, gy - 0.55, gz - 9.3], [0.4, 1.3, 7.0], blade)
        m.cube(bone, [gx - 0.18, gy - 0.4, gz - 11.6], [0.36, 0.9, 2.3], blade)
        m.cube(bone, [gx - 0.15, gy - 0.2, gz - 12.4], [0.3, 0.5, 0.8], blade)


def gear_model():
    m = Model('jinwoo_gear', density=2, width=128, seed=1028)
    skeleton(m, False)
    # eye plate just in front of the face (glow pass only)
    m.cube('head', [-4, 24, -4.08], [8, 8, 0.02], M['eyes'], faces=('north',))
    # messy hair volume over the skin's hair layer: fringe tufts, top spikes, nape tufts
    hair = M['hair']
    m.bone('hair', 'head', [0, 30, 0])
    m.bone('hair_fringe', 'hair', [0, 31.6, -4.6])
    for x0, w, y0, rz, rx in ((-4.5, 2.0, 28.6, -10, -6), (-2.7, 1.9, 28.1, 6, -9), (-0.95, 1.9, 28.7, -5, -7),
                              (0.85, 1.9, 28.2, 9, -10), (2.6, 1.9, 28.8, 14, -6)):
        m.cube('hair_fringe', [x0, y0, -5.15], [w, 32.4 - y0, 0.7], hair, rotation=[rx, 0, rz], pivot=[x0 + w / 2, 32.3, -4.7])
    for x0, z0, rx, ry, rz in ((-3.6, -3.7, 28, 0, -14), (-1.0, -4.1, 34, 0, 6), (1.6, -3.6, 26, 0, 16),
                               (-2.8, -0.9, 18, 10, -20), (0.6, -0.5, 22, -8, 12), (-1.2, 1.6, -30, 0, 4),
                               (1.8, 1.8, -26, 0, 18), (-3.4, 2.0, -24, 0, -18)):
        m.cube('hair', [x0, 32.2, z0], [1.9, 1.5, 2.6], hair, rotation=[rx, ry, rz], pivot=[x0 + 0.95, 32.4, z0 + 1.3])
    for x0, rz in ((-3.8, -12), (-1.1, 3), (1.6, 14)):
        m.cube('hair', [x0, 24.4, 4.3], [2.2, 3.2, 0.8], hair, rotation=[-18, 0, rz], pivot=[x0 + 1.1, 27, 4.5])
    # high collar wrapping the base of the head, open at the front
    c = M['collar']
    m.cube('collar', [-4.4, 23.5, 4.0], [8.8, 3.4, 0.7], c, rotation=[-8, 0, 0], pivot=[0, 24, 4.3])
    m.cube('collar', [-4.85, 23.5, -3.2], [0.7, 3.0, 7.6], c, rotation=[0, 0, 5], pivot=[-4.5, 24, 0])
    m.cube('collar', [4.15, 23.5, -3.2], [0.7, 3.0, 7.6], c, rotation=[0, 0, -5], pivot=[4.5, 24, 0])
    m.cube('collar', [-4.75, 23.4, -4.7], [2.7, 1.9, 0.7], c, rotation=[0, -14, 0], pivot=[-4.5, 24, -4.3])
    m.cube('collar', [2.05, 23.4, -4.7], [2.7, 1.9, 0.7], c, rotation=[0, 14, 0], pivot=[4.5, 24, -4.3])
    # coat tails (rear), split at the centre slit
    m.cube('coat_tail_right', [-4.35, 7.4, 2.15], [4.25, 4.9, 0.6], M['coat'])
    m.cube('coat_tail_right_lower', [-4.35, 2.6, 2.15], [4.25, 4.8, 0.6], M['hem'])
    m.cube('coat_tail_left', [0.1, 7.4, 2.15], [4.25, 4.9, 0.6], M['coat'])
    m.cube('coat_tail_left_lower', [0.1, 2.6, 2.15], [4.25, 4.8, 0.6], M['hem'])
    dagger(m, 'right_dagger', 'fang')
    dagger(m, 'left_dagger', 'knight')
    return m


ARM_BONES = ('arm', 'forearm', 'hand', 'fingers', 'thumb', 'dagger')
FP_TOP = {'right_arm': 'armorRightArm', 'left_arm': 'armorLeftArm'}


def arms_only(src, ident):
    """FP model: top bones armorRightArm / armorLeftArm (PlayerBoneMap names) carrying the arm chains."""
    fp = Model(ident, density=src.density, width=src.width)
    fp.height = src.height
    for b in src.bones:
        parts = b.name.split('_', 1)
        if parts[0] not in ('right', 'left') or len(parts) < 2 or parts[1] not in ARM_BONES:
            continue
        nb = fp.bone(FP_TOP.get(b.name, b.name), None if b.name in FP_TOP else FP_TOP.get(b.parent, b.parent), b.pivot, b.rotation)
        nb.cubes = [copy.copy(c) for c in b.cubes]
    return fp


def main():
    out = {}
    for slim in (False,):
        body = body_model(slim)
        name = 'jinwoo_slim' if slim else 'jinwoo'
        write_json(os.path.join(RES, f'geo/jinwoo/{name}.geo.json'), body.geo_json())
        write_json(os.path.join(RES, f'geo/jinwoo/{name.replace("jinwoo", "jinwoo_arms")}.geo.json'),
                   arms_only(body, name.replace('jinwoo', 'jinwoo_arms')).geo_json())
        out[name] = body
    gear = gear_model()
    base, glow = gear.write('geo/jinwoo/jinwoo_gear.geo.json', 'textures/entity/jinwoo/jinwoo_gear.png',
                            'textures/entity/jinwoo/jinwoo_gear_glow.png')
    write_json(os.path.join(RES, 'geo/jinwoo/jinwoo_arms_gear.geo.json'), arms_only(gear, 'jinwoo_arms_gear').geo_json())
    print('gear atlas', gear.width, gear.height)


if __name__ == '__main__':
    main()
