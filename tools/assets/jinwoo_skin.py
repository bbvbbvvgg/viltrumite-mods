"""Sung Jinwoo skin in the standard Steve layout (classic arms, inner + outer layer), HD 128x128
(2 texels per model pixel; geo UVs stay in 64-space). Own pixel art, CC0.
Look: long black coat to the knees with a high collar and zipper, black trousers and boots,
black messy hair, glowing violet-blue eyes (glow plate in the gear model). Writes
textures/entity/jinwoo/jinwoo.png."""
import numpy as np

from jinwoo_geo import Face, Mat, put, write_png

COAT, COAT_HI, COAT_LO, SHEEN = '#16171e', '#2c2e3b', '#0c0d11', '#3a2d63'
PANTS, BOOT, SOLE = '#13141a', '#0c0c0f', '#050506'
SKIN, SKIN_LO = '#e8c5a8', '#c99f84'
HAIR, HAIR_HI = '#0c0c11', '#262b42'
D = 2  # texels per model pixel


def sides(face):
    return face.name in ('north', 'south', 'east', 'west')


def rim(face, rgb, amount=0.45):
    if sides(face) and face.w > 3:
        put(rgb, np.s_[:, 0], COAT_HI, amount)
        put(rgb, np.s_[:, -1], COAT_HI, amount)


def folds(face, rgb, rs, n=5):
    if sides(face) and face.h > 6 and face.w > 3:
        for _ in range(max(1, face.w // n)):
            c = rs.randint(1, face.w - 1)
            put(rgb, np.s_[rs.randint(0, face.h // 2):, c], COAT_LO, 0.5)
            put(rgb, np.s_[:, min(face.w - 1, c + 1)], COAT_HI, 0.15)


# ---- head ----------------------------------------------------------------------------------------------
def face_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'up':
        put(rgb, np.s_[:, :], HAIR, 1.0)
        return
    if face.name == 'down':
        return
    if face.name == 'south':
        put(rgb, np.s_[:, :], HAIR, 1.0)
        return
    if face.name in ('east', 'west'):
        put(rgb, y > 28.0, HAIR, 1.0)
        put(rgb, (y > 25.5) & (np.abs(z) < 1.0), HAIR, 1.0)  # sideburn
        put(rgb, (z > 1.5) & (y > 24.5), HAIR, 1.0)
        put(rgb, y < 25.0, SKIN_LO, 0.35)
        return
    ax = np.abs(x)
    put(rgb, y > 29.6, HAIR, 1.0)
    brow = (ax > 0.7) & (ax < 3.3) & (np.abs(y - (28.75 + (ax - 0.7) * 0.15)) < 0.3)
    put(rgb, brow, '#141217', 0.95)
    band = (ax > 0.9) & (ax < 3.2)
    put(rgb, band & (np.abs(y - 28.1) < 0.25), '#15111a', 1.0)  # upper lash line
    iris = (ax > 1.25) & (ax < 2.4) & (y > 27.05) & (y < 27.95)
    put(rgb, band & (y > 27.05) & (y < 27.95) & ~iris, '#dcd8ea', 0.9)
    put(rgb, iris, '#6a56d8', 1.0)
    put(rgb, (ax > 1.6) & (ax < 2.05) & (y > 27.2) & (y < 27.8), '#2a1f66', 0.8)
    put(rgb, band & (np.abs(y - 26.95) < 0.12), SKIN_LO, 0.5)
    put(rgb, (ax < 0.4) & (y > 25.9) & (y < 27.0), SKIN_LO, 0.4)  # nose
    put(rgb, (ax < 0.45) & (np.abs(y - 25.95) < 0.15), '#a87c68', 0.6)
    put(rgb, (ax < 1.25) & (np.abs(y - 25.15) < 0.2), '#94685b', 0.8)  # mouth
    put(rgb, (ax > 3.0) & (y < 26.5), SKIN_LO, 0.3)
    put(rgb, y < 24.6, SKIN_LO, 0.4)


def hat_alpha(face, rs):
    """Messy hair volume on the outer layer: jagged fringe over the brows, full top and back."""
    x, y, z = face.world()
    a = np.zeros((face.h, face.w), np.uint8)
    if face.name == 'up':
        a[:] = 255
    elif face.name == 'south':
        edge = 24.6 + rs.uniform(0, 1.2, face.w)
        a[y > edge[None, :]] = 255
    elif face.name in ('east', 'west'):
        edge = 28.0 - np.clip(z, 0, 4) * 0.9 + rs.uniform(-0.3, 0.5, face.w)[None, :]
        a[y > edge] = 255
        a[(y > 25.6) & (np.abs(z + 0.3) < 0.9)] = 255
    elif face.name == 'north':
        strands = np.repeat(rs.uniform(28.6, 30.6, face.w // 3 + 1), 3)[:face.w]
        a[y > strands[None, :]] = 255
    return a


def hat_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], HAIR, 1.0)
    for _ in range(max(2, face.w // 3)):
        c = rs.randint(0, face.w)
        put(rgb, np.s_[:, c], HAIR_HI, 0.4 if face.name != 'up' else 0.6)
    if face.name == 'north':
        put(rgb, np.s_[-2:, :], '#050507', 0.5)


# ---- body ----------------------------------------------------------------------------------------------
def shirt_fn(face, rgb, glow, rs):
    put(rgb, np.s_[:, :], '#1b1c23', 0.9)
    if face.name == 'north':
        x, y, z = face.world()
        put(rgb, np.abs(x) < 1.6, '#23252e', 0.8)


def jacket_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    folds(face, rgb, rs)
    if face.name == 'north':
        put(rgb, np.abs(x) < 0.3, '#43475a', 0.9)                         # zipper
        put(rgb, (np.abs(x) < 0.6) & (np.abs(x) >= 0.3), COAT_LO, 0.6)
        lapel = np.abs(np.abs(x) - (0.6 + (24 - y) * 0.45)) < 0.3
        put(rgb, lapel & (y > 19.5), COAT_HI, 0.7)
        put(rgb, (x > 1.3) & (x < 3.3) & (np.abs(y - 20.3) < 0.25), COAT_HI, 0.5)  # breast pocket
        put(rgb, (np.abs(y - 12.6) < 0.4), '#0a0a0d', 0.8)                 # belt
        put(rgb, (np.abs(y - 12.6) < 0.4) & (np.abs(x) < 0.9), '#5a5f6e', 0.9)
    if face.name == 'south':
        put(rgb, (np.abs(x) < 0.25) & (y < 21), COAT_LO, 0.6)
        put(rgb, np.abs(y - 21.2) < 0.2, COAT_HI, 0.4)
        put(rgb, np.abs(y - 12.6) < 0.4, '#0a0a0d', 0.8)
    if face.name == 'up':
        put(rgb, np.s_[:, :], COAT_HI, 0.3)
    rim(face, rgb, 0.35)


def sleeve_in_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    put(rgb, y < 15.0, SKIN, 1.0)                                         # hands
    put(rgb, (y < 15.0) & (y > 14.6), SKIN_LO, 0.5)
    if face.name == 'down':
        put(rgb, np.s_[:, :], SKIN_LO, 1.0)


def sleeve_alpha(face, rs):
    x, y, z = face.world()
    a = np.where(y > 15.2, 255, 0).astype(np.uint8)
    if face.name == 'down':
        a[:] = 0
    return a


def sleeve_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    folds(face, rgb, rs, 4)
    put(rgb, (y > 15.2) & (y < 16.6), '#0f1015', 0.9)                     # cuff
    put(rgb, np.abs(y - 16.6) < 0.25, '#3a3d4c', 0.8)
    put(rgb, y > 23.2, COAT_HI, 0.3)
    rim(face, rgb, 0.3)


def legs_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    if face.name == 'north':
        put(rgb, np.abs(x - (face.ox + face.sx / 2)) < 0.2, '#0d0e12', 0.5)
    boot = y < 3.0
    put(rgb, boot, BOOT, 1.0)
    put(rgb, y < 0.5, SOLE, 1.0)
    put(rgb, np.abs(y - 3.0) < 0.25, '#26272f', 0.8)
    if face.name == 'north':
        for yy in (1.2, 2.0):
            put(rgb, (np.abs(y - yy) < 0.2) & (np.abs(x - (face.ox + face.sx / 2)) < 0.7), '#2e2f38', 0.8)
    if face.name == 'down':
        put(rgb, np.s_[:, :], SOLE, 1.0)


def coat_skirt_alpha(face, rs):
    """Long coat to the knees on the trouser layer (open at the inner front edge)."""
    x, y, z = face.world()
    a = np.where(y > 4.6, 255, 0).astype(np.uint8)
    if face.name in ('up', 'down'):
        a[:] = 0
    return a


def coat_skirt_fn(face, rgb, glow, rs):
    x, y, z = face.world()
    folds(face, rgb, rs, 3)
    f = np.clip((11.5 - y) / 7.0, 0, 1) * 0.2
    rgb[:] = rgb * (1 - f[..., None]) + np.array([52, 44, 92], float) * f[..., None]
    put(rgb, (y > 4.6) & (y < 5.4), COAT_HI, 0.8)                         # hem
    put(rgb, (y >= 5.4) & (y < 5.8), SHEEN, 0.6)
    if face.name == 'north':
        inner = x > -0.6 if face.ox > -1 else x < 0.6  # opening at the centre line
        put(rgb, inner & (np.abs(x) < 0.6), COAT_HI, 0.6)
        put(rgb, (np.abs(y - 9.2) < 0.3) & (np.abs(x) > 1.0) & (np.abs(x) < 3.2), '#07070a', 0.8)  # pocket slit
    rim(face, rgb, 0.4)


MATS = {
    ('head', 0): Mat(SKIN, noise=2, edge=0.0, top=1.0, bottom=0.85, fn=face_fn),
    ('head', 1): Mat(HAIR, noise=7, edge=0.0, top=1.2, fn=hat_fn, alpha_fn=hat_alpha),
    ('torso', 0): Mat(COAT, noise=3, edge=0.0, fn=shirt_fn),
    ('torso', 1): Mat(COAT, noise=4, edge=0.0, fn=jacket_fn),
    ('arm', 0): Mat(COAT, noise=3, edge=0.0, fn=sleeve_in_fn),
    ('arm', 1): Mat(COAT, noise=4, edge=0.0, fn=sleeve_fn, alpha_fn=sleeve_alpha),
    ('leg', 0): Mat(PANTS, noise=4, edge=0.0, fn=legs_fn),
    ('leg', 1): Mat(COAT, noise=5, edge=0.0, fn=coat_skirt_fn, alpha_fn=coat_skirt_alpha),
}


def group(bone):
    if bone == 'head' or bone == 'torso':
        return bone
    return 'arm' if any(p in bone for p in ('arm', 'hand', 'fingers')) else 'leg'


class _Cube:
    """Full skin box as a painting context (world coords over the whole limb)."""

    def __init__(self, origin, size):
        self.origin, self.size = origin, size


def paint_skin(model):
    """Paints every skin box once (full box, not per segment) into a 128x128 HD skin."""
    import random
    rng = random.Random(1028)
    img = np.zeros((64 * D, 64 * D, 4), np.uint8)
    for full in model.skin_boxes:
        origin, size, (u, v), layer, bone = full
        mat = MATS[(group(bone), layer)]
        w, h, d = size
        rects = {'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'east': (u + d + w, v + d, d, h),
                 'south': (u + d + w + d, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}
        cube = _Cube(origin, size)
        for name, (x, y, rw, rh) in rects.items():
            face = Face(cube, name, rw * D, rh * D)
            col, _ = mat.paint(face, rng)
            img[y * D:(y + rh) * D, x * D:(x + rw) * D] = col
    return img


def main():
    from jinwoo_model import body_model
    img = paint_skin(body_model(False))
    write_png('textures/entity/jinwoo/jinwoo.png', img)


if __name__ == '__main__':
    main()
