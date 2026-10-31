#!/usr/bin/env python3
"""Iron Legion suit clips (stage 6) on the empty-suit shell (geo/ironman/marks/empty_suit.geo.json).

The shell has six top-level bones and no elbows or knees, so every pose is a
whole-limb pose; the renderer adds the body lean, bank, bob, crash tumble, the
arm aim (quaternion slerp after the clips) and the head look on top.

Layering in LegionSuitRenderer (animation-system §5):
  base   hover <-> cruise (LOOP, blended by throttle, time wrapped by the renderer)
  phase  brake, ready_<mark>, takeoff (one-shots, hold_on_last_frame), crash (LOOP)
  move   missiles, signature, dodge_left/right, land (overlay) and
         fire_right/left/both, hit (ADDITIVE: deltas that start and end at 0)

Rotation keys are Bedrock degrees (the parser negates X and Y):
  arms/legs X < 0 swing forward/up, X > 0 back; head X < 0 looks up;
  right limb Z > 0 / left limb Z < 0 move outward; Y > 0 turns to the model's left.
Easing on a key shapes the segment LEAVING that key (KeyframeStack).
Every one-shot overlay starts on the hover pose and the ready clips end on the
hover start pose, so the 2-tick fade-in / 5-tick fade-out never pops. Lengths
match the server ticks (LegionRules / LegionMove#length): ready 26 t = 1.3 s.

Run from the repo root:  python3 tools/assets/make_legion_clips.py
"""
import json
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(REPO, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore", "animations", "ironman",
                   "legion_suit.animation.json")

BONES = ["armorhead", "armorbody", "armorrightarm", "armorleftarm", "armorrightleg", "armorleftleg"]
MARKS = ["mark_7", "mark_42", "mark_15", "mark_39", "mark_17", "war_machine_mk2", "iron_heart_mk3"]

IO = "easeInOutSine"
OUTC = "easeOutCubic"
OUTQ = "easeOutQuad"
INQ = "easeInQuad"
BACK = "easeOutBack"
LIN = "linear"


def pose(head=(0, 0, 0), body=(0, 0, 0), arms=(0, 0, 0), legs=(0, 0, 0), ra=None, la=None, rl=None, ll=None):
    """Symmetric pose: arms/legs given for the right side, mirrored (Y, Z negated) for the left unless overridden."""
    def mirror(v):
        return (v[0], -v[1], -v[2])
    return {
        "armorhead": tuple(head),
        "armorbody": tuple(body),
        "armorrightarm": tuple(ra if ra is not None else arms),
        "armorleftarm": tuple(la if la is not None else mirror(arms)),
        "armorrightleg": tuple(rl if rl is not None else legs),
        "armorleftleg": tuple(ll if ll is not None else mirror(legs)),
    }


def tweak(base, **bones):
    """Copy of a pose with some bones replaced: tweak(p, armorhead=(x, y, z))."""
    out = dict(base)
    for k, v in bones.items():
        out[k] = tuple(v)
    return out


# Hover: palms down beside the hips (repulsors stabilise), legs straight and slightly apart.
HOVER = pose(head=(4, 0, 0), arms=(8, 0, 13), legs=(4, 0, 3))
# Cruise: arms swept back along the body, legs together trailing; the renderer leans the whole body.
CRUISE = pose(head=(-24, 0, 0), arms=(30, 0, 7), legs=(9, 0, 1))


def clip(keys, length, loop):
    """keys: [(time, pose, easing)]. Every bone gets a key at every time."""
    bones = {}
    for bone in BONES:
        rot = {}
        for t, p, e in keys:
            if bone not in p:
                continue
            rot[fmt(t)] = {"vector": [round(float(c), 2) for c in p[bone]], "easing": e}
        if rot:
            bones[bone] = {"rotation": rot}
    return {"loop": loop, "animation_length": length, "bones": bones}


def fmt(t):
    s = ("%.3f" % t).rstrip("0").rstrip(".")
    return s if s else "0"


def add(p, dx=0.0, **bones):
    """Pose plus per-bone deltas: add(HOVER, armorrightarm=(dx, dy, dz))."""
    out = dict(p)
    for k, v in bones.items():
        out[k] = tuple(out[k][i] + v[i] for i in range(3))
    return out


def lerp(a, b, f):
    return {k: tuple(a[k][i] + (b[k][i] - a[k][i]) * f for i in range(3)) for k in BONES}


def hover():
    # 2 s servo-stabilised idle: arms and legs drift out of phase, the head settles.
    k = [
        (0.0, HOVER, IO),
        (0.5, add(HOVER, armorrightarm=(2, 0, 3), armorleftarm=(-1, 0, -1), armorrightleg=(1, 0, 1), armorleftleg=(-1, 0, 0), armorhead=(1, 2, 0)), IO),
        (1.0, add(HOVER, armorrightarm=(-1, 0, 1), armorleftarm=(2, 0, -3), armorrightleg=(-1, 0, 0), armorleftleg=(1, 0, -1), armorhead=(-1, 0, 0)), IO),
        (1.5, add(HOVER, armorrightarm=(1, 0, -1), armorleftarm=(-1, 0, 2), armorrightleg=(0, 0, 1), armorleftleg=(0, 0, -1), armorhead=(1, -2, 0)), IO),
        (2.0, HOVER, IO),
    ]
    return clip(k, 2.0, True)


def cruise():
    # 1.2 s: arms flutter in the slipstream, legs counter-trim.
    k = [
        (0.0, CRUISE, IO),
        (0.3, add(CRUISE, armorrightarm=(2, 0, -1), armorleftarm=(-1, 0, 1), armorrightleg=(-2, 0, 0), armorleftleg=(2, 0, 0)), IO),
        (0.6, add(CRUISE, armorrightarm=(-1, 0, 1), armorleftarm=(2, 0, -1), armorrightleg=(1, 0, 0), armorleftleg=(-1, 0, 0), armorhead=(1, 0, 0)), IO),
        (0.9, add(CRUISE, armorrightarm=(1, 0, 0), armorleftarm=(-2, 0, 0), armorrightleg=(2, 0, 0), armorleftleg=(-2, 0, 0)), IO),
        (1.2, CRUISE, IO),
    ]
    return clip(k, 1.2, True)


def brake():
    # Flare: palms and legs thrown forward against the speed, overshoot, settle near the hover.
    flare = pose(head=(10, 0, 0), arms=(-58, 0, 26), legs=(-30, 0, 6))
    back = pose(head=(2, 0, 0), arms=(-30, 0, 20), legs=(-12, 0, 5))
    k = [
        (0.0, CRUISE, OUTC),
        (0.16, flare, IO),
        (0.42, back, IO),
        (0.7, add(HOVER, armorrightarm=(-6, 0, 4), armorleftarm=(-6, 0, -4)), LIN),
    ]
    return clip(k, 0.7, "hold_on_last_frame")


def takeoff():
    # Coil (legs tuck, arms push down-out), then the launch line: arms back, legs together, head up.
    coil = pose(head=(10, 0, 0), arms=(14, 0, 30), legs=(-22, 0, 4))
    launch = pose(head=(-22, 0, 0), arms=(24, 0, 9), legs=(6, 0, 0))
    k = [
        (0.0, HOVER, IO),
        (0.22, coil, OUTC),
        (0.45, add(launch, armorrightarm=(4, 0, 0), armorleftarm=(4, 0, 0)), IO),
        (0.6, launch, LIN),
    ]
    return clip(k, 0.6, "hold_on_last_frame")


def land():
    # Legs reach down, arms out for balance, impact dip, upright.
    reach = pose(head=(6, 0, 0), arms=(-8, 0, 34), legs=(-6, 0, 2))
    impact = pose(head=(14, 0, 0), arms=(-14, 0, 26), legs=(-12, 0, 6))
    stand = pose(head=(0, 0, 0), arms=(2, 0, 7), legs=(0, 0, 1))
    k = [
        (0.0, HOVER, IO),
        (0.24, reach, INQ),
        (0.36, impact, OUTC),
        (0.6, stand, LIN),
    ]
    return clip(k, 0.6, "hold_on_last_frame")


def crash():
    # 0.6 s loop of limbs flailing against a dead thruster (the renderer spins the body).
    a = pose(head=(18, 10, 0), ra=(-70, 0, 40), la=(30, 0, -80), rl=(-25, 0, 10), ll=(20, 0, -14))
    b = pose(head=(-12, -14, 0), ra=(20, 0, 90), la=(-60, 0, -35), rl=(18, 0, 16), ll=(-22, 0, -6))
    c = pose(head=(10, 6, 0), ra=(-35, 0, 60), la=(-10, 0, -60), rl=(-8, 0, 4), ll=(6, 0, -20))
    k = [(0.0, a, IO), (0.2, b, IO), (0.4, c, IO), (0.6, a, IO)]
    return clip(k, 0.6, True)


def missiles():
    # Arms up and out (forearm / shoulder launchers), two salvo kicks, back to the hover.
    up = pose(head=(-6, 0, 0), arms=(-78, 18, 40), legs=(6, 0, 4))
    kick = add(up, armorrightarm=(-10, 0, 4), armorleftarm=(-10, 0, -4), armorhead=(4, 0, 0))
    k = [
        (0.0, HOVER, OUTC),
        (0.16, up, OUTQ),
        (0.26, kick, IO),
        (0.36, up, OUTQ),
        (0.46, kick, IO),
        (0.58, up, IO),
        (0.8, HOVER, LIN),
    ]
    return clip(k, 0.8, "hold_on_last_frame")


def signature():
    # Generic signature stance: chest presented (unibeam line), arms spread back, legs braced.
    spread = pose(head=(-10, 0, 0), body=(-4, 0, 0), arms=(26, 0, 52), legs=(8, 0, 7))
    k = [
        (0.0, HOVER, OUTC),
        (0.24, spread, IO),
        (0.5, add(spread, armorrightarm=(3, 0, 3), armorleftarm=(3, 0, -3)), IO),
        (0.76, spread, IO),
        (1.0, HOVER, LIN),
    ]
    return clip(k, 1.0, "hold_on_last_frame")


def dodge(side):
    # Side-slip: the limbs on the dodge side tuck, the others fling out against the roll. side +1 = left.
    s = side
    tuck_arm = (10, 0, 4)
    fling_arm = (-20, 0, 58)
    if s > 0:  # dodge left: right arm flings, left arm tucks, legs swing to the right
        p = pose(head=(0, 12, 0), ra=fling_arm, la=(tuck_arm[0], 0, -tuck_arm[2]), rl=(4, 0, 18), ll=(4, 0, 6))
    else:
        p = pose(head=(0, -12, 0), ra=tuck_arm, la=(fling_arm[0], 0, -fling_arm[2]), rl=(4, 0, -6), ll=(4, 0, -18))
    k = [
        (0.0, HOVER, OUTC),
        (0.12, p, IO),
        (0.3, lerp(p, HOVER, 0.35), IO),
        (0.5, HOVER, LIN),
    ]
    return clip(k, 0.5, "hold_on_last_frame")


ZERO = (0, 0, 0)


def fire(right, left, length):
    # Additive recoil: the firing palm kicks up (X -16), the shoulder rocks back; deltas start and end at 0.
    k = [(0.0, {}, OUTC)]
    kick = {}
    settle = {}
    if right:
        kick["armorrightarm"] = (-16, 0, 2)
        settle["armorrightarm"] = (-4, 0, 0)
    if left:
        kick["armorleftarm"] = (-16, 0, -2)
        settle["armorleftarm"] = (-4, 0, 0)
    twist = 0 if (right and left) else (3 if right else -3)
    kick["armorbody"] = (2 if (right and left) else 0, twist, 0)
    kick["armorhead"] = (2, 0, 0)
    settle["armorbody"] = (0, twist * 0.3, 0)
    settle["armorhead"] = (0.5, 0, 0)
    zero = {b: ZERO for b in kick}
    keys = [(0.0, zero, OUTC), (0.05, kick, OUTQ), (length * 0.5, settle, IO), (length, zero, LIN)]
    return clip(keys, length, "hold_on_last_frame")


def hit():
    # Additive flinch: head snaps back, arms jerk out, legs kick; back to 0.
    kick = {"armorhead": (12, 6, 0), "armorbody": (5, 0, 0), "armorrightarm": (-8, 0, 12), "armorleftarm": (-8, 0, -12),
            "armorrightleg": (-6, 0, 3), "armorleftleg": (-6, 0, -3)}
    zero = {b: ZERO for b in kick}
    half = {b: tuple(c * 0.25 for c in v) for b, v in kick.items()}
    keys = [(0.0, zero, OUTC), (0.06, kick, OUTQ), (0.2, half, IO), (0.4, zero, LIN)]
    return clip(keys, 0.4, "hold_on_last_frame")


# --- per-mark ready beats (1.3 s, start on the hover, end on the hover start pose) ---

def ready(mark):
    H = HOVER
    if mark == "mark_7":
        # Combat drop: both palms snap out to the sides, repulsors armed, head sweeps.
        out = pose(head=(-4, 0, 0), arms=(-12, 0, 72), legs=(-4, 0, 9))
        k = [(0.0, H, OUTC), (0.22, out, BACK), (0.55, tweak(out, armorhead=(-4, 18, 0)), IO),
             (0.85, tweak(out, armorhead=(-4, -18, 0)), IO), (1.3, H, LIN)]
        k.insert(4, (1.05, lerp(out, H, 0.5), IO))
    elif mark == "mark_42":
        # Prehensile: the plates "settle" — staggered limb clicks, then a stretch.
        k = [(0.0, H, OUTQ),
             (0.12, add(H, armorrightarm=(-14, 0, 10)), OUTQ),
             (0.24, add(H, armorrightarm=(-14, 0, 10), armorleftarm=(-14, 0, -10)), OUTQ),
             (0.36, add(H, armorrightarm=(-14, 0, 10), armorleftarm=(-14, 0, -10), armorrightleg=(-10, 0, 4)), OUTQ),
             (0.48, add(H, armorrightarm=(-14, 0, 10), armorleftarm=(-14, 0, -10), armorrightleg=(-10, 0, 4), armorleftleg=(-10, 0, -4)), IO),
             (0.75, add(H, armorhead=(-12, 0, 0), armorrightarm=(-6, 0, 30), armorleftarm=(-6, 0, -30)), IO),
             (1.05, add(H, armorhead=(-4, 0, 0)), IO),
             (1.3, H, LIN)]
    elif mark == "mark_15":
        # Stealth: low, arms in, a slow head scan.
        low = pose(head=(10, 0, 0), arms=(-24, 0, 6), legs=(-18, 0, 2))
        k = [(0.0, H, IO), (0.3, tweak(low, armorhead=(10, 26, 0)), IO), (0.7, tweak(low, armorhead=(10, -26, 0)), IO),
             (1.0, low, IO), (1.3, H, LIN)]
    elif mark == "mark_39":
        # Starboost: a full overhead stretch, the high-altitude suit unfolding.
        up = pose(head=(-20, 0, 0), arms=(-14, 0, 150), legs=(4, 0, 8))
        k = [(0.0, H, OUTC), (0.35, up, IO), (0.7, add(up, armorrightarm=(0, 0, 8), armorleftarm=(0, 0, -8)), IO),
             (1.0, lerp(up, H, 0.6), IO), (1.3, H, LIN)]
    elif mark == "mark_17":
        # Heartbreaker: arms back, chest pushed out (unibeam), head up.
        chest = pose(head=(-14, 0, 0), body=(-6, 0, 0), arms=(38, 0, 26), legs=(6, 0, 6))
        k = [(0.0, H, OUTC), (0.3, chest, IO), (0.8, add(chest, armorrightarm=(4, 0, 2), armorleftarm=(4, 0, -2)), IO),
             (1.3, H, LIN)]
    elif mark == "war_machine_mk2":
        # Heavy: both guns up to the line, a nod, guns down.
        guns = pose(head=(4, 0, 0), arms=(-82, 0, 8), legs=(-6, 0, 8))
        k = [(0.0, H, OUTC), (0.28, guns, IO), (0.6, add(guns, armorhead=(10, 0, 0)), IO), (0.8, guns, IO),
             (1.3, H, LIN)]
    elif mark == "iron_heart_mk3":
        # Arms cross over the chest, then open wide.
        cross = pose(head=(6, 0, 0), arms=(-70, -40, 0), legs=(-4, 0, 3))
        wide = pose(head=(-8, 0, 0), arms=(-30, 0, 62), legs=(0, 0, 7))
        k = [(0.0, H, OUTC), (0.3, cross, IO), (0.55, cross, BACK), (0.85, wide, IO), (1.3, H, LIN)]
    else:
        raise SystemExit("unknown mark " + mark)
    return clip(k, 1.3, "hold_on_last_frame")


def build():
    anims = {
        "hover": hover(),
        "cruise": cruise(),
        "brake": brake(),
        "takeoff": takeoff(),
        "land": land(),
        "crash": crash(),
        "missiles": missiles(),
        "signature": signature(),
        "dodge_left": dodge(+1),
        "dodge_right": dodge(-1),
        "fire_right": fire(True, False, 0.4),
        "fire_left": fire(False, True, 0.4),
        "fire_both": fire(True, True, 0.5),
        "hit": hit(),
    }
    for m in MARKS:
        anims["ready_" + m] = ready(m)
    return {"format_version": "1.8.0", "animations": anims}


if __name__ == "__main__":
    data = build()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        text = json.dumps(data, indent=1)
        # One keyframe per line: collapse the vectors and the {vector, easing} objects.
        text = re.sub(r"\[\s+([^\[\]{}]*?)\s+\]", lambda m: "[" + " ".join(m.group(1).split()) + "]", text)
        text = re.sub(r"\{\s+(\"vector\": \[[^\]]*\]),\s+(\"easing\": \"\w+\")\s+\}", r"{\1, \2}", text)
        f.write(text + "\n")
    print(OUT, len(data["animations"]), "clips", os.path.getsize(OUT), "bytes")
