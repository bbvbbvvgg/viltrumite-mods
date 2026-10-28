"""Thor rework R2b: the authoring kit behind make_thor_clips.py.

Key poses are written as intents (fist targets, hammer direction, elbow / knee poles, hip drop,
torso turn) and solved here by IK on R1's skeleton (thor.geo.json, thor_arms.geo.json); the result is
baked into plain Blockbench keyframes (degrees / px) for the shared animation system. Nothing here
runs in the game: the clips in thor.animation.json are the only output.

Frames: TP = BakedGeoModel's internal model frame, px (x = Thor's right, y up, z back; front -Z).
FP = the hand pass view space, blocks (x right, y up, camera looks -Z), through vanilla's
renderPlayerArm chain and PlayerGeoLayer's arm frame, exactly as ThorRenderer.onRenderArm draws it.
"""
import math
import os

import numpy as np

import thor_rig as R

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore")
GEO = os.path.join(RES, "geo/thor")
BODY = R.Model(os.path.join(GEO, "thor.geo.json"))
ARMS = R.Model(os.path.join(GEO, "thor_arms.geo.json"))
HAMMER = R.Model(os.path.join(GEO, "mjolnir_hand.geo.json"))
HAMMER_BONES = HAMMER.subtree("mjolnir")
FP_ELBOW = 6.0  # ThorRenderer.FP_ELBOW, the camera forearm's rest bend
HANDLE_SPACING = 4.6  # px between the two fists on the handle (fist width 4.75)


def P(right, up, fwd):
    """(right, up, forward) -> internal model coords."""
    return np.array([right, up, -fwd], float)


def unit(v):
    v = np.asarray(v, float)
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def basis_map(a_from, b_from, a_to, b_to):
    """Rotation taking (a_from, b_from) onto (a_to, b_to) (b made orthogonal to a on both sides)."""
    def frame(a, b):
        a = unit(a)
        b = unit(b - a * (a @ b))
        return np.column_stack([a, b, np.cross(a, b)])
    return frame(a_to, b_to) @ frame(a_from, b_from).T


def rot_to(a, b):
    """Shortest rotation taking direction a to direction b."""
    a, b = unit(a), unit(b)
    v = np.cross(a, b)
    c = float(a @ b)
    if c < -0.999999:
        axis = unit(np.cross(a, [1, 0, 0] if abs(a[0]) < 0.9 else [0, 1, 0]))
        return axis_angle(axis, math.pi)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx / (1 + c)


def axis_angle(axis, angle):
    axis = unit(axis)
    x, y, z = axis
    c, s = math.cos(angle), math.sin(angle)
    C = 1 - c
    return np.array([[c + x * x * C, x * y * C - z * s, x * z * C + y * s],
                     [y * x * C + z * s, c + y * y * C, y * z * C - x * s],
                     [z * x * C - y * s, z * y * C + x * s, c + z * z * C]])


def two_bone(root, target, l1, l2, pole):
    """Elbow / knee position (root, target, lengths, pole direction); returns (joint, reached)."""
    d = target - root
    dist = np.linalg.norm(d)
    lo, hi = abs(l1 - l2) + 1e-3, l1 + l2 - 1e-3
    reached = lo <= dist <= hi
    dist_c = min(max(dist, lo), hi)
    dh = d / dist if dist > 1e-9 else np.array([0, -1.0, 0])
    p = np.asarray(pole, float) - dh * (dh @ pole)
    if np.linalg.norm(p) < 1e-6:
        p = np.cross(dh, [1, 0, 0])
    p = unit(p)
    cos1 = (l1 * l1 + dist_c * dist_c - l2 * l2) / (2 * l1 * dist_c)
    a1 = math.acos(max(-1.0, min(1.0, cos1)))
    return root + l1 * (math.cos(a1) * dh + math.sin(a1) * p), reached, p, dh


# ------------------------------------------------------------------------------------- third person
class Chain:
    """One limb of a model: root / mid / end bones and the end effector locator."""

    def __init__(self, model, upper, lower, end, tip, effector=None):
        self.upper, self.lower, self.end, self.tip = upper, lower, end, tip
        b = model.bones
        self.a0 = b[lower].pivot - b[upper].pivot            # upper bone vector (geometry)
        self.w0 = b[end].pivot - b[lower].pivot              # lower bone to the end joint
        tip_p = b[tip].pivot if effector is None else np.asarray(effector, float)
        self.g0 = tip_p - b[end].pivot                       # end joint to the effector
        self.l1 = float(np.linalg.norm(self.a0))
        self.l2 = float(np.linalg.norm(self.w0 if end != lower else self.g0))


RIGHT_ARM = Chain(BODY, "right_arm", "right_forearm", "right_hand", "mjolnir_grip")
LEFT_ARM = Chain(BODY, "left_arm", "left_forearm", "left_hand", "left_grip")
RIGHT_LEG = Chain(BODY, "right_leg", "right_shin", "right_shin", "right_shin", effector=[4.5, 1.5, 0.0])
LEFT_LEG = Chain(BODY, "left_leg", "left_shin", "left_shin", "left_shin", effector=[-4.5, 1.5, 0.0])
FOOT = {"r": np.array([4.5, 1.5, 0.0]), "l": np.array([-4.5, 1.5, 0.0])}
WRIST_TILT = math.radians(35)


def hammer_axis(pose):
    """Model-space handle-to-head direction of Mjölnir drawn on the grip (−Z of the grip frame)."""
    return unit(pose.world_rot("mjolnir_grip") @ np.array([0, 0, -1.0]))


def solve_arm(pose, chain, target, pole, ham=None, wrist=None, side=1.0, tilt_limit=WRIST_TILT):
    """IK of one arm onto a fist target (model px). ham = hammer / handle direction through the fist;
    wrist = extra local hand turn (bb degrees) for an empty fist. Returns the miss (px)."""
    hand_local = np.eye(3)
    up = chain.upper
    for _ in range(4):
        pose.touch()
        S = pose.point(up)
        parent = pose.parent_frame(up)[:3, :3]
        # Effector offset with the current hand turn, in model space, follows the forearm.
        fore_rot = pose.world_rot(chain.lower)
        g_world = fore_rot @ hand_local @ chain.g0
        W_target = target - g_world
        E, reached, p, dh = two_bone(S, W_target, chain.l1, chain.l2, pole)
        n = unit(np.cross(p, dh)) * side
        # Upper bone: a0 -> E - S, hinge local x -> n.
        Ru = basis_map(chain.a0, np.array([1.0, 0, 0]), E - S, n)
        pose.set_world_rot(up, Ru)
        W = pose.point(chain.lower) + 0  # elbow pivot after the upper turn
        Rf = basis_map(chain.w0, np.array([1.0, 0, 0]), W_target - W, n)
        pose.set_world_rot(chain.lower, Rf)
        fore_rot = pose.world_rot(chain.lower)
        if ham is not None:
            hf = fore_rot.T @ unit(ham)  # handle direction in the forearm frame
            alpha = math.asin(max(-1.0, min(1.0, hf[1])))
            alpha = max(-tilt_limit, min(tilt_limit, alpha))
            psi = math.atan2(-hf[0], -hf[2])
            hand_local = R.ry(psi) @ R.rx(alpha)
        elif wrist is not None:
            w = np.radians([-wrist[0], -wrist[1], wrist[2]])
            hand_local = R.rzyx(w)
        else:
            hand_local = np.eye(3)
        pose.set_world_rot(chain.end, fore_rot @ hand_local)
    pose.touch()
    return float(np.linalg.norm(pose.point(chain.tip) - target))


def aim_grip(pose, ham, grip="mjolnir_grip"):
    """Turns the grip locator so Mjölnir's axis is exactly ham (the fist keeps its wrist limit)."""
    pose.touch()
    hand_rot = pose.world_rot(pose.model.bones[grip].parent)
    cur = hand_rot @ np.array([0, 0, -1.0])
    world = rot_to(cur, ham) @ hand_rot
    pose.set_world_rot(grip, world)
    pose.touch()


def solve_leg(pose, chain, target, pole):
    pose.touch()
    H = pose.point(chain.upper)
    K, reached, p, dh = two_bone(H, target, chain.l1, chain.l2, pole)
    n = unit(np.cross(dh, p))  # knee bulges forward: the hinge stays +x
    pose.set_world_rot(chain.upper, basis_map(chain.a0, np.array([1.0, 0, 0]), K - H, n))
    pose.touch()
    Kp = pose.point(chain.lower)
    pose.set_world_rot(chain.lower, basis_map(chain.g0 + chain.w0, np.array([1.0, 0, 0]), target - Kp, n))
    pose.touch()
    return float(np.linalg.norm((pose.frame(chain.lower) @ np.append(FOOT["r" if chain is RIGHT_LEG else "l"], 1))[:3] - target))


def torso_frame(pose):
    return pose.frame("body")


def solve_tp(spec, ref=None):
    """One key pose from an intent dict (see make_thor_clips.py). Returns (pose, misses)."""
    pose = R.Pose(BODY)
    if ref is not None:
        pose.rot = {k: v.copy() for k, v in ref.rot.items()}
    misses = {}
    hip = np.asarray(spec.get("hip", (0, 0, 0)), float)  # internal px
    bbpos = [-hip[0], hip[1], hip[2]]
    pose.set_bb("body", rot=spec.get("torso", (0, 0, 0)), pos=bbpos)
    for leg in ("right_leg", "left_leg"):
        pose.pos[leg] = np.array(bbpos)
    pose.set_bb("head", rot=spec.get("head", (0, 0, 0)))
    pose.set_bb("belly", rot=spec.get("belly", (0, 0, 0)))
    T = pose.frame("body")

    def place(v, local):
        v = np.asarray(v, float)
        return (T @ np.append(v, 1))[:3] if local else v

    # Legs: planted feet by default (rest stance), knee poles forward and a little out.
    for side, chain in (("r", RIGHT_LEG), ("l", LEFT_LEG)):
        foot = spec.get(side + "foot")
        if foot is None:
            foot = FOOT[side] + np.array([1.0 if side == "r" else -1.0, 0, 0]) * spec.get("stance", 1.0)
        foot = np.asarray(foot, float)
        sx = 1.0 if side == "r" else -1.0
        pole = np.asarray(spec.get(side + "knee", (0.25 * sx, 0.0, -1.0)), float)
        if spec.get(side + "leg") is not None:
            pose.set_bb(chain.upper, rot=spec[side + "leg"])
            pose.set_bb(chain.lower, rot=spec.get(side + "shin", (0, 0, 0)))
        else:
            misses[side + "foot"] = solve_leg(pose, chain, foot, pole)
    # Right arm.
    held = spec.get("ham")
    if spec.get("rarm") is not None:
        pose.set_bb("right_arm", rot=spec["rarm"])
        pose.set_bb("right_forearm", rot=spec.get("rfore", (0, 0, 0)))
        pose.set_bb("right_hand", rot=spec.get("rwrist", (0, 0, 0)))
    elif spec.get("rf") is not None:
        target = place(spec["rf"], spec.get("rf_local", False))
        ham = None if held is None else unit(place(held, False) if not spec.get("ham_local") else T[:3, :3] @ np.asarray(held, float))
        misses["rf"] = solve_arm(pose, RIGHT_ARM, target, np.asarray(spec.get("rpole", (1.0, -0.3, 0.6)), float), ham=ham,
                                 wrist=spec.get("rwrist"), side=1.0)
    if held is not None:
        ham = unit(np.asarray(held, float)) if not spec.get("ham_local") else unit(T[:3, :3] @ np.asarray(held, float))
        aim_grip(pose, ham)
    if spec.get("grip_roll"):
        # Spin / nudge of the hammer about its own handle-normal (flight), local to the grip.
        pose.rot["mjolnir_grip"] = pose.rot["mjolnir_grip"] + np.radians([-spec["grip_roll"][0], -spec["grip_roll"][1], spec["grip_roll"][2]])
        pose.touch()
    # Left arm.
    lf = spec.get("lf")
    if spec.get("larm") is not None:
        pose.set_bb("left_arm", rot=spec["larm"])
        pose.set_bb("left_forearm", rot=spec.get("lfore", (0, 0, 0)))
        pose.set_bb("left_hand", rot=spec.get("lwrist", (0, 0, 0)))
    elif isinstance(lf, str) and lf.startswith("handle"):
        off = float(lf.split(":")[1]) if ":" in lf else HANDLE_SPACING
        axis = hammer_axis(pose)
        target = pose.point("mjolnir_grip") + axis * off
        misses["lf"] = solve_arm(pose, LEFT_ARM, target, np.asarray(spec.get("lpole", (-1.0, -0.4, 0.5)), float), ham=axis)
    elif lf is not None:
        misses["lf"] = solve_arm(pose, LEFT_ARM, place(lf, spec.get("lf_local", False)), np.asarray(spec.get("lpole", (-1.0, -0.3, 0.6)), float),
                                 wrist=spec.get("lwrist"))
    for bone, rot in (spec.get("add") or {}).items():
        pose.rot[bone] = pose.rot[bone] + np.radians([-rot[0], -rot[1], rot[2]])
        pose.touch()
    return pose, misses


# ------------------------------------------------------------------------------------- first person
def _vanilla(side, equip=0.0, swing=0.0):
    """ItemInHandRenderer.renderPlayerArm up to the arm draw (1.20.1), as one 4x4 (ReinhardFirstPersonRig.vanilla)."""
    m = np.eye(4)
    f1 = math.sqrt(swing)
    f2 = -0.3 * math.sin(f1 * math.pi)
    f3 = 0.4 * math.sin(f1 * math.pi * 2)
    f4 = -0.4 * math.sin(swing * math.pi)
    m = m @ R.trans([side * (f2 + 0.64000005), f3 - 0.6 + equip * -0.6, f4 - 0.71999997])
    m = m @ R.mat4(R.ry(math.radians(side * 45)))
    f5 = math.sin(swing * swing * math.pi)
    f6 = math.sin(f1 * math.pi)
    m = m @ R.mat4(R.ry(math.radians(side * f6 * 70)))
    m = m @ R.mat4(R.rz(math.radians(side * f5 * -20)))
    m = m @ R.trans([side * -1.0, 3.6, 3.5])
    m = m @ R.mat4(R.rz(math.radians(side * 120)))
    m = m @ R.mat4(R.rx(math.radians(200)))
    m = m @ R.mat4(R.ry(math.radians(side * -135)))
    m = m @ R.trans([side * 5.6, 0, 0])
    return m


def fp_base(left):
    """View space of thor_arms' geo frame for one camera arm (blocks), the arm part at ThorRenderer.armRest."""
    side = -1.0 if left else 1.0
    rest = R.trans([(5.0 if left else -5.0) / 16, 2.0 / 16, 0]) @ R.mat4(R.rz(-0.1 if left else 0.1))
    geo = R.trans([(-5.0 if left else 5.0) / 16, 22.0 / 16, 0]) @ R.mat4(np.diag([-1.0, -1.0, 1.0]))
    return _vanilla(side) @ rest @ geo


FP_BASE = {False: fp_base(False), True: fp_base(True)}
PX = 1.0 / 16


def fp_point(pose, bone, left):
    return (FP_BASE[left] @ np.append(pose.point(bone) * PX, 1))[:3]


def fp_rest():
    pose = R.Pose(ARMS)
    for b in ("right_forearm", "left_forearm"):
        pose.set_bb(b, rot=(-FP_ELBOW, 0, 0))  # ThorRenderer sets rotX = +6° internal
    return pose


FP_ARM = {False: ("armorRightArm", "right_forearm", "right_hand", "mjolnir_grip"),
          True: ("armorLeftArm", "left_forearm", "left_hand", "left_grip")}


def solve_fp_arm(pose, left, target, ham=None, roll=0.0, elbow=None, wrist=None):
    """Camera arm: aim the whole arm (shoulder turn) at a view-space fist target, then slide it there
    (the arm bone's position key), hand / grip turned for the hammer axis (view space)."""
    root, fore, hand, grip = FP_ARM[left]
    base = FP_BASE[left]
    M = base[:3, :3] / PX * PX  # linear part (no scale in the chain besides the flips)
    Minv = np.linalg.inv(base[:3, :3])
    pose.set_bb(fore, rot=(-(FP_ELBOW if elbow is None else elbow), 0, 0))
    pose.set_bb(root, rot=(0, 0, 0), pos=(0, 0, 0))
    S = fp_point(pose, root, left)
    G = fp_point(pose, grip, left)
    # Aim: rest shoulder->grip onto shoulder->target, roll about the aim to bring the arm's "up" (view +y side) along.
    aim_rest = unit(G - S)
    aim = unit(np.asarray(target, float) - S)
    Rv = rot_to(aim_rest, aim)
    if roll:
        Rv = axis_angle(aim, math.radians(roll)) @ Rv
    L = Minv @ Rv @ base[:3, :3]
    pose.rot[root] = R.euler_near(L, pose.rot[root])
    pose.touch()
    if wrist is not None:
        pose.set_bb(hand, rot=wrist)
    if ham is not None:
        # Hammer axis in geo model space of this arm.
        ham_m = unit(Minv @ unit(ham))
        fr = pose.world_rot(fore)
        hf = fr.T @ ham_m
        alpha = max(-WRIST_TILT, min(WRIST_TILT, math.asin(max(-1.0, min(1.0, hf[1])))))
        psi = math.atan2(-hf[0], -hf[2])
        pose.set_world_rot(hand, fr @ R.ry(psi) @ R.rx(alpha))
        aim_grip(pose, ham_m, grip)
    # Slide: residual to the target, back into the arm bone's parent (geo) frame, as a bb position.
    G = fp_point(pose, grip, left)
    d = Minv @ (np.asarray(target, float) - G) / PX
    pose.pos[root] = np.array([-d[0], d[1], d[2]])
    pose.touch()
    return float(np.linalg.norm(fp_point(pose, grip, left) - target)) / PX


def fp_boxes(pose, with_hammer, arms=("r", "l")):
    out = []
    for left in (False, True):
        if ("l" if left else "r") not in arms:
            continue
        root = FP_ARM[left][0]
        names = ARMS.subtree(root)
        base = FP_BASE[left] @ np.diag([PX, PX, PX, 1.0])
        out += R.boxes("arms", pose, base, names)
        if with_hammer and not left:
            out += hammer_boxes(pose, "mjolnir_grip", base)
    return out


def hammer_boxes(pose, grip="mjolnir_grip", base=None):
    """Mjölnir's subtree drawn with its pivot on the grip locator (ThorRenderer.drawMjolnir)."""
    hp = R.Pose(HAMMER)
    mj = HAMMER.bones["mjolnir"]
    frame = pose.pivot_frame(grip) @ R.trans(-mj.pivot)
    if base is not None:
        frame = base @ frame
    return R.boxes("mjolnir", hp, frame, HAMMER_BONES)


def tp_boxes(pose, with_hammer):
    out = R.boxes("body", pose)
    if with_hammer:
        out += hammer_boxes(pose)
    return out


def tp_rules():
    rules = R.Rules().allow("body:right_hand", "mjolnir:*")
    for h in ("handle", "handle_plains"):
        rules.allow("body:left_hand", "mjolnir:" + h)
    rest = R.Pose(BODY)
    rules.rest(tp_boxes(rest, False))
    return rules


# ------------------------------------------------------------------------------------- sampling
EASE = {
    "linear": lambda t: t,
    "easeInSine": lambda t: 1 - math.cos(t * math.pi / 2),
    "easeOutSine": lambda t: math.sin(t * math.pi / 2),
    "easeInOutSine": lambda t: -(math.cos(math.pi * t) - 1) / 2,
    "easeInQuad": lambda t: t * t,
    "easeOutQuad": lambda t: 1 - (1 - t) * (1 - t),
    "easeInOutQuad": lambda t: 2 * t * t if t < 0.5 else 1 - (-2 * t + 2) ** 2 / 2,
    "easeInCubic": lambda t: t ** 3,
    "easeOutCubic": lambda t: 1 - (1 - t) ** 3,
    "easeInOutCubic": lambda t: 4 * t ** 3 if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2,
}


def sample_channel(keys, t):
    """keys: [(seconds, value(3), easing of the segment that starts here)] -> value at t (KeyframeStack)."""
    if t <= keys[0][0]:
        return np.asarray(keys[0][1], float)
    if t >= keys[-1][0]:
        return np.asarray(keys[-1][1], float)
    for i in range(len(keys) - 1):
        t0, v0, e = keys[i]
        t1, v1, _ = keys[i + 1]
        if t0 <= t <= t1:
            f = 0.0 if t1 <= t0 else (t - t0) / (t1 - t0)
            f = EASE[e](f)
            return np.asarray(v0, float) + (np.asarray(v1, float) - np.asarray(v0, float)) * f
    return np.asarray(keys[-1][1], float)
