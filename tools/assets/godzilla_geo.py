"""Godzilla geometry: one builder, three levels of detail sharing bones (clips play on every LOD) and the
swatch atlas. detail 2 = near (full plates, teeth, gills, fingers, toes), 1 = mid, 0 = far silhouette.
Own work, CC0. Bone pivots follow GodzillaRules (checked by GodzillaAssetsTest)."""
import math

import numpy as np

import godzilla_lib as G
from godzilla_lib import B, F, box, segment, loft, KaijuModel

# ---- joints (geo px: x left, y up, z back) ---------------------------------------------------------------
NECK = B(*G.NECK_PIVOT)                       # head pivot
SHOULDER = B(*G.SHOULDER_L)
TAIL = G.tail_joints()
HIP = np.array([23.0, 60.0, 3.0])             # thigh pivot (left)
KNEE = np.array([25.0, 32.0, -7.0])
ANKLE = np.array([24.0, 10.0, 3.0])
ELBOW = np.array([36.5, 89.0, -33.0])
WRIST = np.array([37.5, 71.0, -45.0])
JAW = np.array([0.0, 150.0, -37.0])

PIVOTS = {
    'root': (0, 0, 0), 'pelvis': (0, 66, 8), 'hips': (0, 66, 8), 'waist': (0, 86, 2), 'chest': (0, 108, -9),
    'neck': (0, 130, -17), 'head': tuple(NECK), 'jaw': tuple(JAW), 'mouth_glow': (0, 150, -46), 'eyes': tuple(NECK),
    'arm_l': tuple(SHOULDER), 'forearm_l': tuple(ELBOW), 'hand_l': tuple(WRIST),
    'leg_l': tuple(HIP), 'shin_l': tuple(KNEE), 'foot_l': tuple(ANKLE), 'toes_l': (24.0, 5.0, -13.0),
}
PARENT = {
    'pelvis': 'root', 'hips': 'pelvis', 'waist': 'hips', 'chest': 'waist', 'neck': 'chest', 'head': 'neck',
    'jaw': 'head', 'mouth_glow': 'head', 'eyes': 'head', 'arm_l': 'chest', 'forearm_l': 'arm_l', 'hand_l': 'forearm_l',
    'leg_l': 'pelvis', 'shin_l': 'leg_l', 'foot_l': 'shin_l', 'toes_l': 'foot_l', 'tail_0': 'hips',
}
for k in range(7):
    PIVOTS['tail_%d' % k] = tuple(TAIL[k])
    if k:
        PARENT['tail_%d' % k] = 'tail_%d' % (k - 1)


def mirror(p):
    return np.array([-p[0], p[1], p[2]], float)


# ---- spine coordinate for the plate glow (0 tail tip .. 1 head) -------------------------------------------
RIDGE = [  # (point on the back surface, bone) from the head down to the tail root
    (np.array([0.0, 163.0, -36.0]), 'neck'), (np.array([0.0, 158.0, -24.0]), 'neck'), (np.array([0.0, 155.0, -12.0]), 'chest'),
    (np.array([0.0, 152.0, -1.0]), 'chest'), (np.array([0.0, 145.0, 9.0]), 'chest'), (np.array([0.0, 133.0, 18.0]), 'chest'),
    (np.array([0.0, 118.0, 26.0]), 'waist'), (np.array([0.0, 102.0, 32.0]), 'waist'), (np.array([0.0, 88.0, 37.0]), 'hips'),
    (np.array([0.0, 79.0, 40.0]), 'hips'),
]


def tail_top(k, f):
    """Point on the top of tail segment k at fraction f along it (rest pose) and its pitch."""
    a, b = TAIL[k], TAIL[k + 1]
    p = a + (b - a) * f
    pitch = math.radians(G.TAIL_PITCH[k])
    h = G.TAIL_SIZE[k][1] * F * (1.0 - 0.18 * f) + (G.TAIL_SIZE[min(6, k + 1)][1] * F * 0.18 * f)
    up = np.array([0.0, math.cos(pitch), math.sin(pitch)])
    return p + up * h * 0.46, G.TAIL_PITCH[k]


def spine_polyline():
    pts = [p for p, _ in RIDGE]
    for k in range(7):
        for f in (0.0, 0.5):
            pts.append(tail_top(k, f)[0])
    pts.append(tail_top(6, 1.0)[0])
    return pts


SPINE = spine_polyline()
_SEG = [float(np.linalg.norm(SPINE[i + 1] - SPINE[i])) for i in range(len(SPINE) - 1)]
SPINE_LEN = sum(_SEG)


def spine_s(p):
    """Spine coordinate of the closest polyline point: 0 at the tail tip, 1 at the head end."""
    best, acc, s_best = 1e9, 0.0, 0.0
    for i in range(len(SPINE) - 1):
        a, b = SPINE[i], SPINE[i + 1]
        d = b - a
        t = max(0.0, min(1.0, float(np.dot(p - a, d) / (np.dot(d, d) + 1e-9))))
        q = a + d * t
        dist = float(np.linalg.norm(p - q))
        if dist < best:
            best, s_best = dist, acc + _SEG[i] * t
        acc += _SEG[i]
    return 1.0 - s_best / SPINE_LEN


# ---- builder ---------------------------------------------------------------------------------------------
def build(detail):
    ident = 'godzilla' if detail == 2 else 'godzilla_lod%d' % (2 - detail)
    m = KaijuModel(ident, seed=17 + detail)
    order = ['root', 'pelvis', 'hips', 'waist', 'chest', 'neck', 'head', 'jaw', 'mouth_glow', 'eyes',
             'arm_l', 'forearm_l', 'hand_l', 'leg_l', 'shin_l', 'foot_l', 'toes_l'] + ['tail_%d' % k for k in range(7)]
    for name in order:
        m.bone(name, PARENT.get(name), list(PIVOTS[name]))
    rnd = detail >= 1
    torso(m, detail, rnd)
    neck_head(m, detail, rnd)
    arm(m, detail, rnd)
    leg(m, detail, rnd)
    tail(m, detail, rnd)
    m.mirror_x('_l', '_r')
    plates(m, detail)
    return m


def torso(m, detail, rnd):
    S = [((0, 56, 14), 50, 40), ((0, 70, 11), 57, 48), ((0, 86, 3), 61, 54), ((0, 102, -5), 61, 54),
         ((0, 117, -12), 58, 52), ((0, 131, -17), 52, 46), ((0, 141, -21), 42, 40)]
    S = [(np.array(p, float), w, d) for p, w, d in S]
    bones = ['hips', 'hips', 'waist', 'chest', 'chest', 'chest']
    if detail == 0:
        for i in (0, 2, 4):
            a, b = S[i][0], S[i + 2][0] if i + 2 < len(S) else S[-1][0]
            segment(m, bones[i], a, b, (S[i][1] + S[i + 1][1]) / 2, (S[i][2] + S[i + 1][2]) / 2, 'skin', 'up', False, 0.1)
        box(m, 'chest', (0, 141, -2), (40, 22, 26), 'hide', [-30, 0, 0])
        return
    for i in range(len(S) - 1):
        (a, wa, da), (b, wb, db) = S[i], S[i + 1]
        segment(m, bones[i], a, b, (wa + wb) / 2, (da + db) / 2, 'skin', 'up', rnd, 0.16)
    # belly scutes down the front
    front = [((0, 62, -6), 30), ((0, 72, -14), 36), ((0, 87, -24), 40), ((0, 102, -31), 40), ((0, 117, -37), 38), ((0, 130, -39), 32)]
    fb = ['hips', 'hips', 'waist', 'chest', 'chest']
    for i in range(len(front) - 1):
        (a, wa), (b, wb) = front[i], front[i + 1]
        segment(m, fb[i], np.array(a, float), np.array(b, float), (wa + wb) / 2, 7, 'belly', 'up', False, 0.1)
    # back hump, lats and the trapezius that carries the plates
    box(m, 'chest', (0, 142, -3), (44, 24, 28), 'hide', [-32, 0, 0])
    box(m, 'chest', (0, 128, 10), (48, 26, 26), 'hide', [-38, 0, 0])
    box(m, 'waist', (0, 108, 20), (52, 26, 24), 'hide', [-24, 0, 0])
    box(m, 'hips', (0, 86, 28), (48, 22, 22), 'hide', [-18, 0, 0])
    for sx in (1, -1):
        box(m, 'chest', (sx * 21, 126, -20), (18, 26, 30), 'skin', [-20, 0, sx * -14])     # trapezius to shoulder
        box(m, 'chest', (sx * 14, 114, -34), (24, 20, 10), 'skin', [-14, 0, 0])            # pectoral
        box(m, 'hips', (sx * 25, 68, 10), (16, 26, 36), 'skin', [0, 0, sx * 6])             # haunch
        if detail >= 2:
            box(m, 'waist', (sx * 27, 94, 2), (8, 22, 40), 'skin', [-8, 0, sx * -4])         # flank folds
            for j in range(3):
                box(m, 'waist', (sx * 30.5, 86 + j * 7, 2 + j * 2), (2, 2, 30 - j * 4), 'hide', [-8, 0, 0])


def neck_head(m, detail, rnd):
    if detail == 0:
        segment(m, 'neck', np.array([0, 130, -18.0]), np.array([0, 150, -36.0]), 32, 30, 'skin', 'up', False, 0.15)
        box(m, 'head', (0, 158, -48), (24, 18, 34), 'skin')
        box(m, 'jaw', (0, 146, -53), (20, 7, 30), 'skin')
        return
    loft(m, 'neck', [(np.array([0, 128, -17.0]), 42, 40), (np.array([0, 140, -25.0]), 35, 33), (np.array([0, 150, -33.0]), 30, 28)],
         'skin', 'up', rnd, 0.15)
    segment(m, 'neck', np.array([0, 125, -35.0]), np.array([0, 143, -45.0]), 22, 6, 'belly', 'up', False, 0.1)   # throat scutes
    box(m, 'neck', (0, 152, -20), (26, 14, 22), 'hide', [-40, 0, 0])                                        # nape
    if detail >= 2:
        for sx in (1, -1):
            for j in range(3):
                box(m, 'neck', (sx * 15.6, 133 + j * 4.5, -27 - j * 3.2), (1.2, 2.2, 9), 'gill', [-38, 0, 0])
    # skull
    box(m, 'head', (0, 160, -45), (24, 16, 26), 'skin')
    box(m, 'head', (0, 158, -33), (22, 18, 12), 'hide')
    box(m, 'head', (0, 155.6, -61), (19, 11, 13), 'skin', [7, 0, 0])
    box(m, 'head', (0, 153.8, -68.3), (15, 7.5, 4.5), 'skin', [7, 0, 0])
    box(m, 'head', (0, 152.2, -56), (20.5, 4, 27), 'lip')
    box(m, 'head', (0, 166.5, -40), (14, 3, 16), 'hide')                                                     # crown
    for sx in (1, -1):
        box(m, 'head', (sx * 8.2, 165.0, -54.5), (9.5, 5, 13), 'hide', [8, 0, sx * 12])                       # brow ridge
        box(m, 'head', (sx * 11.6, 153.5, -40), (5, 13, 17), 'skin')                                         # cheek muscle
        box(m, 'eyes', (sx * 12.05, 161.0, -56.2), (0.5, 2.4, 4.4), 'eye')
        if detail >= 2:
            box(m, 'head', (sx * 4.2, 156.6, -70.9), (2.4, 1.6, 0.8), 'gill')                                # nostril
            box(m, 'head', (sx * 11.6, 158, -48), (2, 3, 6), 'hide')                                         # cheek spur
    # mouth interior (glows during the charge)
    box(m, 'mouth_glow', (0, 151.2, -50), (15, 3, 24), 'throat')
    box(m, 'mouth_glow', (0, 148.5, -38.5), (13, 9, 3), 'throat')
    # jaw
    box(m, 'jaw', (0, 145.6, -53), (20, 7.4, 30), 'skin')
    box(m, 'jaw', (0, 145.6, -66), (16, 6, 7), 'skin')
    box(m, 'jaw', (0, 142.4, -48), (18, 5, 22), 'belly')
    box(m, 'jaw', (0, 149.4, -53), (12, 1.6, 22), 'mouth')                                                   # tongue
    teeth = 7 if detail >= 2 else 2
    for sx in (1, -1):
        for j in range(teeth):
            z = -47.5 - j * (19.0 / max(1, teeth - 1))
            big = 1.3 if j in (1, 2) else 1.0
            box(m, 'head', (sx * (8.4 - j * 0.35), 149.2, z), (1.6 * big, 3.2 * big, 1.6 * big), 'teeth')
            box(m, 'jaw', (sx * (8.0 - j * 0.35), 150.2, z + 1.2), (1.5, 2.6 * big, 1.5), 'teeth')


def arm(m, detail, rnd):
    if detail == 0:
        segment(m, 'arm_l', SHOULDER, ELBOW, 17, 17, 'skin', 'down', False, 0.15)
        segment(m, 'forearm_l', ELBOW, WRIST, 14, 13, 'skin', 'down', False, 0.15)
        box(m, 'hand_l', WRIST + np.array([0, -6, -3.0]), (9, 12, 11), 'skin')
        return
    box(m, 'arm_l', SHOULDER + np.array([1.5, 1.0, 0.0]), (23, 22, 24), 'skin', [0, 0, -10])
    segment(m, 'arm_l', SHOULDER, ELBOW, 21, 21, 'skin', 'down', rnd, 0.16)
    box(m, 'arm_l', (SHOULDER + ELBOW) / 2 + np.array([3.0, 0, 2.0]), (6, 18, 12), 'hide', [8, 0, -14])        # triceps ridge
    segment(m, 'forearm_l', ELBOW, WRIST, 17, 16, 'skin', 'down', rnd, 0.16)
    box(m, 'forearm_l', (ELBOW + WRIST) / 2 + np.array([2.5, 0.0, 1.5]), (5, 14, 9), 'hide', [22, 0, -6])
    palm = WRIST + np.array([0.0, -5.0, -3.0])
    box(m, 'hand_l', palm, (10, 11, 12), 'skin', [24, 0, 0])
    fingers = [(-3.6, -7.0, 0.0), (-1.2, -9.0, 3.0), (1.2, -9.0, 3.0), (3.4, -6.5, 0.0)] if detail >= 2 else [(-2.0, -8.0, 0), (2.0, -8.0, 0)]
    for k, (dz, dy, spread) in enumerate(fingers):
        root = palm + np.array([-1.0 + k * 0.6 - 1.2, -3.0, dz - 2.0])
        mid = root + np.array([spread * 0.2, dy * 0.55, -4.0])
        tip = mid + np.array([spread * 0.15, dy * 0.45, -2.5])
        segment(m, 'hand_l', root, mid, 3.2, 3.2, 'skin', 'down', False, 0.2)
        segment(m, 'hand_l', mid, tip, 2.8, 2.8, 'skin', 'down', False, 0.2)
        claw = tip + np.array([0.0, -2.5, -1.5])
        segment(m, 'hand_l', tip, claw, 2.0, 2.2, 'claw', 'down', False, 0.1)
    if detail >= 2:
        box(m, 'hand_l', palm + np.array([-4.5, -3.0, 2.5]), (3, 6, 3), 'skin', [10, 0, 20])                  # thumb
        box(m, 'hand_l', palm + np.array([-5.5, -7.0, 1.5]), (2, 3.5, 2), 'claw', [10, 0, 20])


def leg(m, detail, rnd):
    if detail == 0:
        segment(m, 'leg_l', HIP, KNEE, 30, 32, 'skin', 'down', False, 0.12)
        segment(m, 'shin_l', KNEE, ANKLE, 21, 21, 'skin', 'down', False, 0.12)
        box(m, 'foot_l', (24, 5, -4), (24, 10, 28), 'skin')
        return
    segment(m, 'leg_l', HIP + np.array([0.0, 4.0, 1.0]), KNEE, 36, 38, 'skin', 'down', rnd, 0.14)
    box(m, 'leg_l', (HIP + KNEE) / 2 + np.array([8.0, 2.0, 3.0]), (14, 30, 28), 'hide', [-18, 0, -4])      # outer thigh
    box(m, 'leg_l', KNEE + np.array([0.5, 2.0, -3.0]), (22, 14, 16), 'skin', [-10, 0, 0])                  # knee
    segment(m, 'shin_l', KNEE, ANKLE, 22, 22, 'skin', 'down', rnd, 0.14)
    box(m, 'shin_l', (KNEE + ANKLE) / 2 + np.array([0.0, 0.0, 6.0]), (16, 18, 8), 'hide', [26, 0, 0])      # calf
    box(m, 'foot_l', (24, 5.5, 0), (24, 9, 20), 'skin')
    box(m, 'foot_l', (24, 0.9, -2), (25, 1.8, 26), 'sole')
    box(m, 'foot_l', (24, 7, 8), (18, 10, 8), 'hide')                                                         # heel
    box(m, 'toes_l', (24, 4.6, -15), (22, 8, 10), 'skin')
    toes = [(-8.0, 0.0), (0.0, -1.5), (8.0, 0.0)] if detail >= 2 else [(-6.0, 0.0), (6.0, 0.0)]
    for dx, dz in toes:
        root = np.array([24 + dx, 4.0, -17.0 + dz])
        tip = root + np.array([dx * 0.12, -0.6, -8.0])
        segment(m, 'toes_l', root, tip, 6.0, 6.0, 'skin', 'fwd', False, 0.1)
        if detail >= 2:
            box(m, 'toes_l', tip + np.array([0.0, 0.8, 0.0]), (6.4, 1.5, 4.0), 'hide')
        segment(m, 'toes_l', tip + np.array([0, 0.6, 0.5]), tip + np.array([dx * 0.05, -2.2, -4.5]), 3.6, 3.2, 'claw', 'fwd', False, 0.05)
    if detail >= 2:
        segment(m, 'foot_l', np.array([16.0, 4.0, 6.0]), np.array([13.5, 1.2, 12.0]), 3.0, 3.0, 'claw', 'back', False, 0.05)


def tail(m, detail, rnd):
    for k in range(7):
        a, b = TAIL[k], TAIL[k + 1]
        w0, h0 = G.TAIL_SIZE[k][0] * F, G.TAIL_SIZE[k][1] * F
        w1, h1 = (G.TAIL_SIZE[k + 1][0] * F, G.TAIL_SIZE[k + 1][1] * F) if k < 6 else (w0 * 0.55, h0 * 0.5)
        bone = 'tail_%d' % k
        if k == 0:
            w0, h0 = w0 * 1.25, h0 * 1.25
        if detail == 0:
            segment(m, bone, a, b, (w0 + w1) / 2, (h0 + h1) / 2, 'skin', 'back', False, 0.08)
            continue
        mid = (a + b) / 2
        wm, hm = (w0 + w1) / 2, (h0 + h1) / 2
        segment(m, bone, a, mid, (w0 + wm) / 2, (h0 + hm) / 2, 'skin', 'back', rnd, 0.12)
        segment(m, bone, mid, b, (wm + w1) / 2, (hm + h1) / 2, 'skin', 'back', rnd, 0.12)
        # underside scutes and a darker ridge on top
        pitch = math.radians(G.TAIL_PITCH[k])
        down = np.array([0.0, -math.cos(pitch), -math.sin(pitch)])
        up = -down
        segment(m, bone, a + down * h0 * 0.42, b + down * h1 * 0.42, wm * 0.55, 3.0, 'belly', 'back', False, 0.05)
        if detail >= 2:
            segment(m, bone, a + up * h0 * 0.40, b + up * h1 * 0.40, wm * 0.42, 4.0, 'hide', 'back', False, 0.05)
    if detail >= 1:
        tip = TAIL[7]
        d = TAIL[7] - TAIL[6]
        d = d / np.linalg.norm(d)
        segment(m, 'tail_6', tip - d * 2.0, tip + d * 9.0, 6.0, 5.0, 'skin', 'back', False, 0.1)


# ---- dorsal plates ---------------------------------------------------------------------------------------
LAYERS = [  # (y0, y1, length, back offset, thickness) as fractions of the plate height / base length / thickness
    (0.00, 0.36, 1.00, 0.00, 1.00), (0.30, 0.60, 0.76, 0.07, 0.85), (0.55, 0.82, 0.50, 0.13, 0.70), (0.78, 1.00, 0.26, 0.19, 0.55)]


def plate(m, bone, base, height, length, thick, tilt, roll=0.0, detail=2, jag=True):
    """One arrowhead plate standing on base (geo px), swept back by tilt degrees (about the base), rolled
    outwards by roll (side rows). Layers taper to the tip; jags on the leading edge at mid height."""
    layers = LAYERS if detail >= 2 else [LAYERS[0], LAYERS[2]] if detail == 1 else [(0.0, 1.0, 0.7, 0.08, 1.0)]
    rot = [-tilt, 0.0, roll]
    for y0, y1, ln, back, th in layers:
        if detail == 1 and y0 > 0.1:
            y0, y1, ln, back = 0.30, 1.0, 0.52, 0.12
        size = np.array([thick * th, (y1 - y0) * height + 0.6, ln * length])
        centre = base + np.array([0.0, (y0 + y1) / 2 * height, back * length])
        o = centre - size / 2
        u0 = 0.5 - ln / 2 + back * 0.5
        m.cube(bone, G.r3(o), G.r3(size), None, rotation=[round(a, 2) for a in rot], pivot=G.r3(base),
               tag={'mat': 'plate', 'u': (max(0.0, u0), min(1.0, u0 + ln)), 'v': (y0, y1)})
    if jag and detail >= 2:
        for yj, lj in ((0.42, 0.62), (0.66, 0.40)):
            size = np.array([thick * 0.6, 0.11 * height, 0.16 * length])
            centre = base + np.array([0.0, yj * height, -lj * length * 0.5 + 0.05 * length])
            m.cube(bone, G.r3(centre - size / 2), G.r3(size), None, rotation=[round(-tilt - 18.0, 2), 0.0, round(roll, 2)],
                   pivot=G.r3(base), tag={'mat': 'plate', 'u': (0.0, 0.25), 'v': (yj - 0.06, yj + 0.06)})


# Centre row: (where along the ridge / tail, height, base length, thickness); tallest over the shoulders.
BACK_PLATES = [  # (ridge param 0 head .. 9 tail root, height, length, thickness)
    (0.35, 13.0, 9.0, 2.6), (1.0, 19.0, 12.0, 3.0), (1.8, 30.0, 16.0, 3.4), (2.6, 40.0, 19.0, 3.8), (3.4, 38.0, 19.0, 3.8),
    (4.3, 33.0, 17.0, 3.6), (5.3, 28.0, 15.0, 3.4), (6.3, 24.0, 14.0, 3.2), (7.3, 20.0, 12.5, 3.0), (8.3, 17.0, 11.0, 2.8)]
SIDE_PLATES = [(1.4, 14.0, 9.0), (2.2, 22.0, 12.0), (3.0, 27.0, 14.0), (3.85, 25.0, 13.0), (4.8, 21.0, 12.0), (5.8, 18.0, 11.0),
               (6.8, 15.0, 10.0), (7.8, 12.0, 9.0)]
TAIL_PLATES = [(k, f, h) for k, f, h in [(0, 0.25, 15.0), (0, 0.75, 14.0), (1, 0.25, 13.0), (1, 0.75, 12.0), (2, 0.3, 11.0), (2, 0.8, 10.0),
                                         (3, 0.3, 9.0), (3, 0.8, 8.0), (4, 0.3, 7.0), (4, 0.8, 6.0), (5, 0.35, 5.0), (5, 0.85, 4.5),
                                         (6, 0.4, 4.0)]]


def ridge_at(t):
    i = min(len(RIDGE) - 2, int(math.floor(t)))
    f = t - i
    (a, ba), (b, bb) = RIDGE[i], RIDGE[i + 1]
    p = a + (b - a) * f
    d = b - a
    slope = math.degrees(math.atan2(-d[1], d[2]))  # how far the back leans: plates stand perpendicular to it
    return p, (ba if f < 0.5 else bb), slope


def plate_bone(m, s, row, idx, parent, pivot):
    name = 'plate_%04d_%s%d' % (int(round(s * 1000)), row, idx)
    m.bone(name, parent, G.r3(pivot))
    return name


def plates(m, detail):
    idx = 0
    for t, h, ln, th in BACK_PLATES:
        p, bone, slope = ridge_at(t)
        tilt = 14.0 + max(0.0, slope - 30.0) * 0.4
        name = plate_bone(m, spine_s(p), 'c', idx, bone, p)
        plate(m, name, p - np.array([0, 1.5, 0]), h, ln, th, tilt, 0.0, detail)
        idx += 1
    if detail >= 1:
        for t, h, ln in SIDE_PLATES:
            p, bone, slope = ridge_at(t)
            tilt = 16.0 + max(0.0, slope - 30.0) * 0.4
            for sx, row in ((1, 'l'), (-1, 'r')):
                q = p + np.array([sx * 6.5, -3.5, 1.0])
                name = plate_bone(m, spine_s(p), row, idx, bone, q)
                plate(m, name, q, h, ln, 2.4, tilt, sx * 16.0, detail, jag=False)
                idx += 1
    for k, f, h in TAIL_PLATES:
        p, pitch = tail_top(k, f)
        name = plate_bone(m, spine_s(p), 'c', idx, 'tail_%d' % k, p)
        plate(m, name, p - np.array([0, 1.0, 0]), h * 1.15, h * 0.9 + 3.0, max(1.8, 2.8 - k * 0.12), pitch + 18.0, 0.0, detail)
        idx += 1
