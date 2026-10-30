"""Regulus rig math (own work): forward kinematics matching the mod's anim engine and a two-bone
leg IK used by make_regulus_clips.py so planted feet stay planted.

Engine conventions (client/anim): clip rotations are Bedrock degrees; the effective geo-space
rotation of a bone is Rz(-Z) * Ry(Y) * Rx(-X) about its pivot, applied over its rest rotation;
the position channel translates in geo space (px; front of the model is -z). A bone's transform
is parent * T(pos) * T(pivot) * R * T(-pivot).
"""
import math

PX_PER_BLOCK = 16.0 / 0.9375  # model px per world block (PLAYER_SCALE)
THIGH = 5.0
SHIN = 5.45
ANKLE_H = 1.15
HIPS_PIVOT = (0.0, 12.0, 0.0)
HIP_JOINT = {"right": (-1.85, 11.6, 0.0), "left": (1.85, 11.6, 0.0)}
HEEL = (0.0, -1.15, 1.2)    # heel contact relative to the ankle (geo, +z = back)
TOE_JOINT = (0.0, -1.15, -1.5)  # toe joint (ball of the foot) relative to the ankle
TOE_TIP = (0.0, -1.15, -2.95)
REACH = 0.985 * (THIGH + SHIN)


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def bone_rot(deg):
    x, y, z = (math.radians(v) for v in deg)
    return mat_mul(mat_mul(rz(-z), ry(y)), rx(-x))


def local(pivot, rot=(0, 0, 0), pos=(0, 0, 0)):
    m = translate(*pos)
    m = mat_mul(m, translate(*pivot))
    m = mat_mul(m, bone_rot(rot))
    return mat_mul(m, translate(-pivot[0], -pivot[1], -pivot[2]))


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def inverse_rigid(m):
    r = [[m[i][j] for j in range(3)] for i in range(3)]
    t = [m[i][3] for i in range(3)]
    rt = [[r[j][i] for j in range(3)] for i in range(3)]
    ti = [-sum(rt[i][k] * t[k] for k in range(3)) for i in range(3)]
    return [rt[0] + [ti[0]], rt[1] + [ti[1]], rt[2] + [ti[2]], [0, 0, 0, 1]]


def hips_matrix(pos, rot):
    return local(HIPS_PIVOT, rot, pos)


def solve_leg(side, hips_pos, hips_rot, ankle, pitch):
    """Thigh/shin/foot/toe rotations putting the ankle at a geo-space point with the foot pitched
    `pitch` degrees (toe down +) and flat sideways. Returns (rotations dict, stretched flag)."""
    hm = hips_matrix(hips_pos, hips_rot)
    a = apply(inverse_rigid(hm), ankle)
    h = HIP_JOINT[side]
    vx, vy, vz = a[0] - h[0], a[1] - h[1], a[2] - h[2]
    down = -vy
    tilt = math.atan2(-vx, down)  # Z+ moves a hanging limb towards -x
    vert = math.hypot(down, vx)
    fwd = -vz
    d = math.hypot(fwd, vert)
    stretched = d > REACH + 1e-6
    d = min(d, REACH)
    alpha = math.atan2(fwd, vert)
    cosb = (THIGH * THIGH + d * d - SHIN * SHIN) / (2 * THIGH * d)
    beta = math.acos(max(-1.0, min(1.0, cosb)))
    t1 = alpha + beta
    kf, kv = THIGH * math.sin(t1), THIGH * math.cos(t1)
    t2 = math.atan2(fwd - kf, vert - kv) if d > 1e-6 else t1
    hips_z = hips_rot[2]
    thigh_z = math.degrees(tilt)
    rots = {
        side + "_thigh": [-math.degrees(t1), 0.0, thigh_z],
        side + "_shin": [math.degrees(t1 - t2), 0.0, 0.0],
        side + "_foot": [pitch + math.degrees(t2) - hips_rot[0], 0.0, -(hips_z + thigh_z)],
    }
    return rots, stretched


def max_hip_height(ankle_y, ankle_f, hip_f):
    """Highest hip joint y that still reaches an ankle `ankle_f` px ahead (forward = -z)."""
    df = ankle_f - hip_f
    if abs(df) >= REACH:
        return ankle_y
    return ankle_y + math.sqrt(REACH * REACH - df * df)


# ------------------------------------------------------------------ forward kinematics check
CHAIN = {
    "root": (None, (0, 0, 0)), "hips": ("root", HIPS_PIVOT),
    "right_thigh": ("hips", HIP_JOINT["right"]), "left_thigh": ("hips", HIP_JOINT["left"]),
    "right_shin": ("right_thigh", (-1.85, 6.6, 0)), "left_shin": ("left_thigh", (1.85, 6.6, 0)),
    "right_foot": ("right_shin", (-1.85, 1.15, 0)), "left_foot": ("left_shin", (1.85, 1.15, 0)),
    "right_toe": ("right_foot", (-1.85, 0.0, -1.5)), "left_toe": ("left_foot", (1.85, 0.0, -1.5)),
}


def fk(pose):
    """pose: bone -> {"rotation": [..], "position": [..]}; returns world matrices of the leg chain."""
    out = {}
    for bone in CHAIN:
        parent, pivot = CHAIN[bone]
        p = pose.get(bone, {})
        m = local(pivot, p.get("rotation", (0, 0, 0)), p.get("position", (0, 0, 0)))
        out[bone] = mat_mul(out[parent], m) if parent else m
    return out


def foot_points(mats, side):
    """World heel, ball and toe tip of one foot (geo px)."""
    ft, toe = mats[side + "_foot"], mats[side + "_toe"]
    ax = HIP_JOINT[side][0]
    heel = apply(ft, (ax, ANKLE_H + HEEL[1], HEEL[2]))
    ball = apply(ft, (ax, 0.0, -1.5))
    tip = apply(toe, (ax, 0.0, -2.95))
    return heel, ball, tip
