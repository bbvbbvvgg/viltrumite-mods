"""Heaven's Door clips (ids match client/rohan/HeavensDoorClips; lengths in ticks).

Signs (bedrock, as dio_lib): arm x<0 raises forward, right arm z>0 abducts (left: z<0), forearm x<0 bends the elbow,
shin x>0 bends the knee, head x>0 looks down; "@pos" moves bones in model units. The renderer adds breathing, head
tracking and coat sway on top, so the clips leave coat_* alone.
"""
from dio_lib.clips import Clip, eback, ein, eio, eout, merge, wave


def M(**kw):
    """Pose helper: R/L limb values mirrored automatically (y, z negated for _L)."""
    out = {}
    for k, v in kw.items():
        if k.endswith("_B"):
            base = k[:-2]
            out[base + "_R"] = list(v)
            out[base + "_L"] = [v[0], -v[1], -v[2]]
        elif k == "pos":
            out["@pos"] = v
        else:
            out[k] = list(v)
    return out


FLOAT = M(thigh_B=(-14, 0, 2), shin_B=(22, 0, 0), foot_B=(26, 0, 0), shoulder_B=(-6, 0, 9), forearm_B=(-14, 0, 0),
          hand_B=(0, 0, -4), finger_B=(-10, 0, 0), head=(4, -6, 0))


def idle():
    c = Clip("idle", 40, loop=True)
    c.key(0, FLOAT).key(40, FLOAT)
    c.layer(wave(40, {"@pos": {"root": [0, 0.35, 0]}, "thigh_R": [4, 0, 0], "thigh_L": [-3, 0, 0], "shoulder_R": [3, 0, 2]}))
    c.layer(wave(20, {"head": [0, 0, 2], "finger_R": [-8, 0, 0]}, 0.25))
    return c


def appear():
    c = Clip("appear", 8, hold=True)
    small = merge(M(thigh_B=(-60, 0, 4), shin_B=(90, 0, 0), foot_B=(30, 0, 0), shoulder_B=(-30, 0, -6), forearm_B=(-100, 0, 0),
                    head=(30, 0, 0), chest=(20, 0, 0), pos={"root": [0, -3, 2]}))
    burst = merge(M(thigh_B=(-6, 0, 6), shin_B=(10, 0, 0), foot_B=(20, 0, 0), shoulder_B=(-20, 0, 55), forearm_B=(-6, 0, 0),
                    finger_B=(0, 0, 0), head=(-14, 0, 0), chest=(-8, 0, 0), pos={"root": [0, 0.6, 0]}))
    c.key(0, small).key(4, burst, eout).key(8, FLOAT, eio)
    return c


def touch():
    """Lunge and touch: contact on tick 4 (RohanRules.DOOR_CONTACT - DOOR_SUMMON)."""
    c = Clip("touch", 10, hold=True)
    wind = merge(FLOAT, M(shoulder_R=(-20, 20, 30), forearm_R=(-70, 0, 0), chest=(0, 20, 0), waist=(0, 10, 0), head=(0, -10, 0)))
    hit = merge(M(thigh_B=(-30, 0, 2), shin_B=(30, 0, 0), foot_B=(30, 0, 0), shoulder_L=(10, 0, -30), forearm_L=(-30, 0, 0),
                  shoulder_R=(-92, -6, 4), forearm_R=(0, 0, 0), hand_R=(-6, 0, 0), finger_R=(-4, 0, 0), thumb_R=(0, 0, 20),
                  chest=(14, -16, 0), waist=(8, -6, 0), head=(-8, 12, 0), pos={"root": [0, -0.4, -3.2]}))
    c.key(0, FLOAT).key(2, wind, eout).key(4, hit, ein).key(6, merge(hit, M(shoulder_R=(4, 0, 0))), eout).key(10, merge(FLOAT, M(pos={"root": [0, 0, -1]})), eio)
    return c


def write():
    """Writing into an open book with the fingertip (loop)."""
    c = Clip("write", 12, loop=True)
    base = merge(FLOAT, M(shoulder_R=(-62, -10, 8), forearm_R=(-34, 0, 0), hand_R=(14, 0, 0), finger_R=(-6, 0, 0),
                          shoulder_L=(-30, 18, -10), forearm_L=(-60, 0, 0), hand_L=(0, 0, 10), head=(22, -10, 0), chest=(10, -6, 0)))
    c.key(0, base).key(12, base)
    c.layer(wave(6, {"shoulder_R": [0, 7, 0], "hand_R": [0, 0, 10]}))
    c.layer(wave(12, {"forearm_R": [-6, 0, 0], "head": [0, 4, 0]}, 0.25))
    return c


def guard():
    """'Faster than a punch': both arms crossed up in front, then the snap."""
    c = Clip("guard", 12, hold=True)
    cross = merge(M(thigh_B=(-20, 0, 6), shin_B=(30, 0, 0), foot_B=(26, 0, 0), shoulder_B=(-96, 30, 0), forearm_B=(-48, 0, 0),
                    hand_B=(0, 0, 0), chest=(-6, 0, 0), head=(6, 0, 0), pos={"root": [0, 0.3, -1.2]}))
    snap = merge(cross, M(shoulder_R=(-20, -40, 40), forearm_R=(-10, 0, 0), chest=(0, 22, 0), pos={"root": [0, 0.3, -1.6]}))
    c.key(0, FLOAT).key(3, cross, eback).key(8, cross).key(10, snap, ein).key(12, merge(snap, M(chest=(0, -6, 0))), eout)
    return c


def self_edit():
    """Writes on Rohan's cheek (10 ticks), then the 'fly backwards' push (10 ticks)."""
    c = Clip("self", 20, hold=True)
    reach = merge(FLOAT, M(shoulder_R=(-70, 46, 10), forearm_R=(-40, 0, 0), hand_R=(10, 0, 0), chest=(0, 30, 0), head=(10, 34, 0),
                           pos={"root": [0.8, 0, 0]}))
    c.key(0, FLOAT).key(3, reach, eout).key(10, reach)
    c.layer(lambda t: {"hand_R": [0, 0, 10 * __import__("math").sin(t * 1.6)]} if 3 <= t <= 10 else {})
    push = merge(M(shoulder_B=(-80, 0, 30), forearm_B=(0, 0, 0), hand_B=(-60, 0, 0), chest=(-10, 0, 0), head=(-10, 0, 0),
                   thigh_B=(-6, 0, 6), shin_B=(12, 0, 0), pos={"root": [0, 0.6, -1.4]}))
    c.key(13, push, eback).key(20, FLOAT, eio)
    return c


def present():
    """The manuscript: a showman's flourish while the page is held up to the onlookers."""
    c = Clip("present", 30, hold=True)
    open_ = merge(M(thigh_B=(-8, 0, 8), shin_B=(14, 0, 0), foot_B=(24, 0, 0), shoulder_R=(-40, 0, 78), forearm_R=(-10, 0, 0),
                    shoulder_L=(-40, 0, -78), forearm_L=(-10, 0, 0), hand_B=(0, 0, 20), head=(-16, 0, 0), chest=(-12, 0, 0),
                    pos={"root": [0, 1.2, 0]}))
    point = merge(open_, M(shoulder_R=(-110, -10, 10), forearm_R=(0, 0, 0), finger_R=(0, 0, 0), head=(-6, 0, 0)))
    c.key(0, FLOAT).key(6, open_, eback).key(18, open_).key(22, point, eout).key(30, point)
    c.layer(wave(15, {"@pos": {"root": [0, 0.25, 0]}}))
    return c


def dismiss():
    c = Clip("dismiss", 10, hold=True)
    fold = merge(M(thigh_B=(-50, 0, 0), shin_B=(80, 0, 0), foot_B=(30, 0, 0), shoulder_B=(-10, 0, 4), forearm_B=(-90, 0, 0),
                   head=(24, 0, 0), chest=(16, 0, 0), pos={"root": [0, 2.5, 3]}))
    c.key(0, FLOAT).key(10, fold, ein)
    return c


def clips():
    return [idle(), appear(), touch(), write(), guard(), self_edit(), present(), dismiss()]
