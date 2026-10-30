#!/usr/bin/env python3
"""Thor rework R2b: every Thor clip, third and first person, as Blockbench animation JSON.

  python3 tools/assets/make_thor_clips.py            # bake, check, write animations/thor/*.animation.json
  python3 tools/assets/make_thor_clips.py --check    # bake and check only (no files)
  python3 tools/assets/make_thor_clips.py --only hammer_spin,fist_jab

Clips are written as intents on beats (thor_clipcore: arm directions from the shoulders, hammer
direction and head face, pelvis, torso, feet) and solved by IK on R1's skeleton every tick; the
attack rows' lengths and contact ticks come from ThorAttacks.java (the R2a table), never typed here.
First-person versions (<clip>_fp, thor_arms.geo.json) follow the third-person grip and hammer on the
same ticks, mapped into the camera view, with the head / fist on the crosshair at the contact tick.
Checks per tick: no posed cube of the body inside another (Python mirror of ThorClipBodyTest), feet
on their targets, hands on their targets, first person in front of the near plane and off the
crosshair outside the contact. Output is split into files of at most ~56 KB (the repository's push
channel takes one file per request); every file is a complete Blockbench animation file.
"""
import argparse
import json
import math
import os
import re
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import thor_clipcore as C  # noqa: E402
import thor_clipkit as K  # noqa: E402

key = C.key
ROOT = K.ROOT
JAVA = os.path.join(ROOT, "viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/hero/thor")
OUT = os.path.join(K.RES, "animations/thor")


# ------------------------------------------------------------------------------------------- the table
def table():
    """clip -> (windup, contact, recovery) from ThorAttacks.java (constants resolved from ThorRules / ThorCombatTimings)."""
    consts = {}
    for f in ("ThorRules.java", "combat/ThorCombatTimings.java"):
        for m in re.finditer(r"static final int (\w+) = (\d+);", open(os.path.join(JAVA, f)).read()):
            consts[m.group(1)] = int(m.group(2))
            consts[os.path.basename(f)[:-5] + "." + m.group(1)] = int(m.group(2))
    src = open(os.path.join(JAVA, "combat/ThorAttacks.java")).read()
    rows = {}

    def num(s):
        s = s.strip()
        return int(s) if s.isdigit() else consts[s]

    for m in re.finditer(r'strike\(\w+, "(\w+)", (\d+), (\d+), (\d+),', src):
        rows[m.group(1)] = (int(m.group(2)), int(m.group(3)), int(m.group(4)))
    for m in re.finditer(r'move\(ThorMove\.\w+, \d+, "(\w+)", ([\w.]+), ([\w.]+), ([\w.]+),', src):
        rows[m.group(1)] = (num(m.group(2)), num(m.group(3)), num(m.group(4)))
    return rows


TABLE = table()


# ------------------------------------------------------------------------------------------- stances
# Relaxed (no hammer): heavy, a little forward, fists hanging just off the thighs.
IDLE = key(C.BASE, hip=(0, -0.6, 0), tor=(4, 0, 0), ra=(60, -80, 19.4), la=(-60, -80, 19.4))
# Relaxed with Mjölnir: the hammer hangs head down by the right thigh, the face along the walk.
IDLE_H = key(IDLE, ra=(55, -76, 18.4), hm=(14, -82), hf=(0, 0))
# Combat stance with Mjölnir: knees in, left foot forward, hammer up in front, left hand out.
STANCE_H = key(C.BASE, hip=(0, -2.2, 0), tor=(7, 12, 0), rft=(0.5, 0, -2.0), lft=(-0.5, 0, 3.0),
               ra=(32, -42, 16.5), rp=(1, -0.8, 0.2), hm=(-12, 42), la=(-4, -34, 16.5), lp=(-1, -0.7, 0.2))
# Fist guard: orthodox, right fist by the chin, left fist forward.
GUARD = key(C.BASE, hip=(0, -2.2, 0), tor=(6, 16, 0), rft=(0.5, 0, -2.5), lft=(-0.5, 0, 3.0),
            ra=(-28, -2, 13.5), rp=(0.5, -1, 0.3), la=(24, 4, 15.0), lp=(-0.5, -1, 0.3), rw=(-10, 0, 0), lw=(-10, 0, 0))

# Audit v3 guards (ThorGuard): Mjölnir across the body / a high guard with both fists.
GUARD_HAMMER = key(STANCE_H, hip=(0, -2.8, 0), tor=(9, 6, 0), ra=(4, -14, 14.5), rp=(1, -0.6, 0.3), hm=(-62, 38),
                   la=(-6, -2, 13.5), lp=(-1, -0.6, 0.3))
GUARD_FIST = key(GUARD, tor=(8, 4, 0), ra=(-14, 16, 12.0), la=(14, 16, 12.0), rw=(-20, 0, 0), lw=(-20, 0, 0))


def fp_track(keys, hammer=True):
    """A first-person path of its own (draw / holster: the arm drops out of the view and comes back):
    keys [(tick, fist target in the view, hammer direction or None)], smoothstep between keys."""
    def custom(t, rt, ham, lt):
        for (t0, r0, h0), (t1, r1, h1) in zip(keys, keys[1:]):
            if t0 <= t <= t1:
                f = C.smooth((t - t0) / max(1, t1 - t0))
                r = np.asarray(r0, float) * (1 - f) + np.asarray(r1, float) * f
                h = None if h0 is None and h1 is None else C.slerp(C.unit(np.asarray(h0 if h0 is not None else h1, float)),
                                                                     C.unit(np.asarray(h1 if h1 is not None else h0, float)), f)
                return r, h, C.FP_HIDDEN_L.copy()
        r, h = keys[-1][1], keys[-1][2]
        return np.asarray(r, float), None if h is None else C.unit(np.asarray(h, float)), C.FP_HIDDEN_L.copy()
    return C.FpPlan(hammer=hammer, contact=None, left="never", custom=custom)


FP_LOW = (0.50, -1.05, -0.58)          # the fist below the view (at the hip)
FP_HANG = (0.15, -0.95, 0.25)          # the hammer hanging from the low fist
FP_OUT = ((0.62, -0.62, -0.70), (0.75, 0.35, -0.55))  # rising on the far right, the head out to the side


def strike(name, base, G, L, Cn, O, T, hammer=True, fp=None, owner="full", extra=(), ease=None, noise=None, fp_spin=0.35,
           end=None):
    w, c, r = TABLE[name]
    bt = C.beats(w, c, r)
    E = dict(C.HEAVY_EASE)
    if ease:
        E.update(ease)
    seq = [("start", base), ("gather", key(base, **G)), ("loaded", key(base, **L)), ("contact", key(base, **Cn)),
           ("overswing", key(base, **O)), ("settle", key(base, **T)), ("end", end if end is not None else base)]
    keys = [(bt[n], it, E.get(n, "easeInOutSine")) for n, it in seq]
    for tick, it, e in extra:
        keys.append((tick, it, e))
    keys.sort(key=lambda k: k[0])
    return C.Clip(name, w + c + r, keys, hammer=hammer, owner=owner, contact=w, beats=bt,
                  fp=fp or C.FpPlan(hammer=hammer, guard=not hammer), noise=noise, fp_spin=fp_spin)


def shake(amp, freq, phase=0.0):
    return lambda t: amp * math.sin(2 * math.pi * freq * t * C.TICK + phase)


def tremble(t, it, amp=1.0, start=0, stop=10 ** 6):
    if t < start or t > stop:
        return it
    it = dict(it)
    s = C.TICK * t
    a = amp
    it["tor"] = tuple(np.asarray(it["tor"], float) + np.array([0.5 * a * math.sin(44 * s), 0.4 * a * math.sin(39 * s + 1.3),
                                                                 0.3 * a * math.sin(51 * s + 0.4)]))
    if isinstance(it["ra"], tuple) and it["ra"] and not isinstance(it["ra"][0], str):
        it["ra"] = tuple(np.asarray(it["ra"], float) + np.array([0.8 * a * math.sin(47 * s + 2), 0.8 * a * math.sin(42 * s), 0.0]))
    return it


# ------------------------------------------------------------------------------------------- hammer strikes
def hammer_clips():
    S = STANCE_H
    out = []
    # 1st of the chain: flat right-to-left (trail yaw 60 -> -60).
    out.append(strike("hammer_swing_right", S,
        G=dict(hip=(0.6, -2.8, 0.4), tor=(6, 34, -3), rft=(1.0, 0, -2.5), ra=(96, -34, 17.5), rp=(0.6, -1, 0.5), hm=(132, -12),
               la=(-30, -30, 16)),
        L=dict(hip=(0.9, -3.2, 0.6), tor=(7, 44, -4), rft=(1.0, 0, -2.5), ra=(112, -20, 18.5), rp=(0.5, -1, 0.6), hm=(154, -2),
               la=(-14, -16, 16.5)),
        Cn=dict(hip=(-0.6, -3.4, -1.4), tor=(9, -14, 2), lft=(-0.5, 0, 4.5), ra=(-2, -6, 19.8), rp=(1, -0.4, 0.3), hm=(-14, 0),
                hf=(-90, 0), la=(-80, -50, 16)),
        O=dict(hip=(-0.8, -3.2, -1.4), tor=(9, -42, 3), lft=(-0.5, 0, 4.5), ra=(-66, -14, 18.5), rp=(0.4, 0.2, -1), hm=(-108, -8), hf=(-90, 0),
               la=(-118, -40, 17), lp=(-0.2, -1, 0.6)),
        T=dict(hip=(-0.3, -2.8, -0.4), tor=(7, -20, 1), ra=(-20, -26, 18.8), rp=(0.6, -1, 0.2), hm=(-50, -20), la=(-70, -55, 17))))
    # 2nd: diagonal down from over the left shoulder (yaw -55 -> 55, pitch 45 -> -30).
    out.append(strike("hammer_swing_backhand", S,
        G=dict(hip=(-0.4, -2.4, 0), tor=(4, -26, 1), ra=(-32, 22, 17.5), rp=(0.6, 0.9, 0.2), hm=(-104, 34), la=(-100, -50, 17),
               lp=(-0.2, -1, 0.6)),
        L=dict(hip=(-0.6, -2.6, 0.2), tor=(3, -36, 2), ra=(-36, 28, 18.2), rp=(0.5, 1, 0.1), hm=(-112, 40), la=(-110, -48, 17),
               lp=(-0.2, -1, 0.6)),
        Cn=dict(hip=(0.6, -3.2, -1.0), tor=(10, 24, -2), ra=(40, -22, 19.6), rp=(1, 0.2, 0.2), hm=(58, -34), hf=(40, -60),
                la=(-50, -40, 16)),
        O=dict(hip=(0.8, -3.8, -0.8), tor=(14, 34, -3), ra=(58, -58, 18.6), rp=(1, -0.2, 0.3), hm=(56, -74), la=(-40, -50, 16)),
        T=dict(hip=(0.3, -3.0, -0.3), tor=(8, 18, -1), ra=(36, -54, 17.5), hm=(10, -30), la=(-20, -40, 16))))
    # Finisher LLR: overhead down (pitch 80 -> -45), one-handed, the left hand counter-swings.
    over = dict(
        G=dict(hip=(0, -1.6, 0.6), tor=(-7, 6, 0), ra=(8, 62, 18.5), rp=(1, 0.3, 0.3), hm=(4, 128), hf=(0, 40), la=(-20, 18, 17)),
        L=dict(hip=(0, -1.2, 0.8), tor=(-11, 4, 0), ra=(6, 76, 19.0), rp=(1, 0.4, 0.4), hm=(2, 158), hf=(0, 60), la=(-14, 28, 17.5)),
        Cn=dict(hip=(0, -5.5, -1.2), tor=(26, 0, 0), lft=(-0.5, 0, 4.5), ra=(4, -8, 19.8), rp=(1, -0.2, 0.4), hm=(2, -22), hf=(0, -90),
                la=(-50, -60, 17)),
        O=dict(hip=(0, -6.2, -1.2), tor=(30, -2, 0), lft=(-0.5, 0, 4.5), ra=(6, -40, 19.4), rp=(1, -0.3, 0.3), hm=(4, -64), hf=(0, -90),
               la=(-60, -70, 17)),
        T=dict(hip=(0, -4.0, -0.6), tor=(16, 2, 0), ra=(18, -46, 18), hm=(4, -40), la=(-40, -60, 17)))
    out.append(strike("hammer_overhead", S, **over))
    # Lunge finish: from the lunge the weight climbs onto the front (left) leg, the rear knee drives up (Thor on one
    # leg, the hammer high over the right shoulder, the left arm out for balance), then the right foot stomps down
    # in front and the whole body drops behind the hammer into the ground.
    out.append(strike("hammer_lunge_finish", S,
        G=dict(hip=(-0.6, -1.8, -0.4), tor=(-6, 10, 2), lft=(-0.5, 0, 4.0), rft=(0.5, 3.0, 0.5), rk=(0.3, 0.2, 1),
               ra=(10, 62, 18.5), rp=(1, 0.3, 0.3), hm=(4, 128), hf=(0, 40), la=(-60, 0, 17.5)),
        L=dict(hip=(-0.8, -1.4, -0.6), tor=(-10, 8, 3), lft=(-0.5, 0, 4.0), rft=(0.5, 5.5, 2.5), rk=(0.3, 0.4, 1),
               ra=(8, 76, 19.0), rp=(1, 0.4, 0.4), hm=(2, 158), hf=(0, 60), la=(-66, 6, 18.0)),
        Cn=dict(hip=(0, -6.4, -3.4), tor=(28, -2, 0), lft=(-0.5, 0, 2.5), rft=(1.0, 0, 6.5), ra=(2, -10, 19.8), rp=(1, -0.2, 0.4),
                hm=(0, -26), hf=(0, -90), la=(-50, -60, 17)),
        O=dict(hip=(0, -7.0, -3.4), tor=(32, -2, 0), lft=(-0.5, 0, 2.5), rft=(1.0, 0, 6.5), ra=(4, -42, 19.4), rp=(1, -0.3, 0.3),
               hm=(2, -66), hf=(0, -90), la=(-60, -70, 17)),
        T=dict(hip=(0, -4.2, -1.4), tor=(16, 2, 0), lft=(-0.5, 0, 2.5), rft=(1.0, 0, 3.0), ra=(18, -46, 18), hm=(4, -40),
               la=(-40, -60, 17))))
    # Spins (ring yaw 90 -> -270): the whole body turns once round, the hammer on a long arm. hammer_spin is tall and
    # open (the hammer at chest height, the free arm flung out as the counterweight); hammer_heavy_spin is low and
    # closed (deep knees, the torso over the hammer sweeping at hip height, the free fist tucked to the chest).
    def spin(name, heavy):
        low = 2.2 if heavy else 0.0
        lean = 8 if heavy else 0
        dip = -16 if heavy else 0  # hammer pitch: hip height for the heavy spin
        free = (-60, -40, 11.5) if heavy else (-85, -14, 17.0)
        lp = (-1, -0.5, 0.2) if heavy else (-1, -0.5, 0.6)
        return strike(name, S,
            G=dict(hip=(0.5, -2.8 - low, 0.4), tor=(5 + lean, 40, -3), ra=(92, -12 + dip, 19.4), rp=(0.2, -1, 0.6), hm=(100, -6 + dip),
                   hf=(180, 0), la=(-40, -20, 16)),
            L=dict(hip=(0.6, -3.2 - low, 0.6), tor=(6 + lean, 54, -4), ra=(98, -8 + dip, 19.6), rp=(0.2, -1, 0.6), hm=(108, -4 + dip),
                   hf=(180, 0), la=(-30, -14, 16)),
            Cn=dict(hip=(0, -3.4 - low, 0), tor=(8 + lean, 0, 0), spin=-90, ra=(90, -10 + dip, 19.8), rp=(0.2, -1, 0.5),
                    hm=(96, -6 + dip), hf=(180, 0), la=free, lp=lp),
            O=dict(hip=(0, -3.4 - low, 0), tor=(8 + lean, -6, 0), spin=-200, ra=(88, -12 + dip, 19.6), rp=(0.2, -1, 0.5),
                   hm=(92, -8 + dip), hf=(180, 0), la=free, lp=lp),
            T=dict(hip=(0, -3.0 - low, 0), tor=(6 + lean // 2, -4, 0), spin=-310, ra=(55, -55, 18.0), hm=(40, -20), la=(-70, -50, 17)),
            end=key(S, spin=-360), fp_spin=0.3)
    out.append(spin("hammer_spin", False))
    out.append(spin("hammer_heavy_spin", True))
    # 3rd of the chain: uppercut from a crouch (pitch -50 -> 70).
    up = dict(
        G=dict(hip=(0.3, -5.5, 0.8), tor=(18, 22, -2), ra=(36, -86, 18.5), rp=(1, 0, 0.4), hm=(160, -58), hf=(90, 0), la=(-20, -30, 16)),
        L=dict(hip=(0.4, -6.5, 1.0), tor=(24, 28, -3), ra=(30, -92, 18.8), rp=(1, 0, 0.5), hm=(178, -40), hf=(90, 0), la=(-14, -24, 16)),
        Cn=dict(hip=(-0.2, -1.6, -1.2), tor=(-4, -8, 1), ra=(-6, 26, 18.2), rp=(1, -0.6, 0.2), hm=(-4, 70), hf=(90, 0),
                la=(-60, -60, 17)),
        O=dict(hip=(-0.2, -1.2, -1.0), tor=(-8, -10, 1), ra=(0, 62, 18.0), rp=(1, -0.2, 0.3), hm=(6, 118), hf=(90, 0), la=(-60, -60, 17)),
        T=dict(hip=(0, -2.0, -0.4), tor=(2, 8, 0), ra=(28, 18, 12.5), rp=(1, -0.6, 0.4), hm=(56, 136), la=(-30, -40, 16)))
    out.append(strike("hammer_uppercut", S, **up))
    # Sweep launch: a golf swing. Low and wide, the hammer drawn back flat behind the right foot; it sweeps through at
    # shin height and carries on up in front, the body rising onto the left leg and the right heel coming up in the
    # follow-through, the hammer high over the left shoulder.
    out.append(strike("hammer_sweep_launch", S,
        # (the right foot stays planted under the widening squat, only the left steps out: widen() adds 5.5 px)
        G=dict(hip=(0.4, -6.4, 0.8), tor=(22, 34, -4), rft=(-4.0, 0, -2.0), lft=(-1.5, 0, 3.5), ra=(100, -44, 19.4),
               rp=(0.4, -0.2, 1), hm=(124, -36), hf=(180, 0), la=(-40, -40, 16)),
        L=dict(hip=(0.6, -7.4, 1.0), tor=(24, 44, -5), rft=(-4.0, 0, -2.0), lft=(-1.5, 0, 3.5), ra=(104, -40, 19.6),
               rp=(0.4, -0.2, 1), hm=(136, -30), hf=(180, 0), la=(-34, -36, 16)),
        Cn=dict(hip=(-0.4, -2.6, -1.2), tor=(2, -12, 2), rft=(1.0, 0, -2.0), lft=(-1.0, 0, 3.5), rfr=-18.0, ra=(-10, 30, 19.2),
                rp=(1, -0.6, 0.2), hm=(-14, 56), hf=(90, 0), la=(-70, -50, 17)),
        O=dict(hip=(-0.6, -1.6, -1.2), tor=(-6, -24, 3), rft=(1.0, 0, -2.0), lft=(-1.0, 0, 3.5), rfr=-26.0, ra=(-24, 62, 18.6),
               rp=(1, 0.2, 0.2), hm=(-30, 104), hf=(90, 0), la=(-90, -50, 17)),
        T=dict(hip=(0, -2.4, -0.4), tor=(4, 0, 0), ra=(10, 20, 15.5), rp=(1, -0.6, 0.4), hm=(30, 120), la=(-40, -40, 16))))
    # Rising diagonal from the right shin up past the left shoulder (pitch -40 -> 50).
    out.append(strike("hammer_rising", S,
        G=dict(hip=(0.5, -4.5, 0.4), tor=(14, 30, -5), ra=(54, -70, 18.8), hm=(70, -64), la=(-30, -30, 16)),
        L=dict(hip=(0.6, -5.2, 0.5), tor=(18, 38, -6), ra=(62, -66, 19.0), hm=(88, -52), la=(-24, -24, 16)),
        Cn=dict(hip=(-0.3, -2.0, -0.8), tor=(0, -12, 3), ra=(-22, 16, 19.2), rp=(1, -0.5, 0.2), hm=(-30, 44), hf=(-30, -40),
                la=(-80, -60, 17)),
        O=dict(hip=(-0.4, -1.6, -0.8), tor=(-2, -20, 3), ra=(-34, 46, 18.6), rp=(1, 0.2, 0.2), hm=(-52, 70), la=(-100, -50, 17),
               lp=(-0.2, -1, 0.6)),
        T=dict(hip=(0, -2.2, -0.3), tor=(4, -6, 1), ra=(-4, 4, 16.5), hm=(-20, 30), la=(-40, -40, 16))))
    # 4th of the chain: two-beat ground slam (pitch 85 -> -70): up on the toes, hang, down into the ground.
    slam = dict(
        G=dict(hip=(0, -1.4, 0.4), tor=(-9, 8, 0), ra=(8, 70, 18.8), rp=(1, 0.4, 0.3), hm=(2, 134), hf=(0, 40), la=(-26, 30, 17)),
        L=dict(hip=(0, -1.3, 0.6), tor=(-13, 6, 0), ra=(4, 82, 19.4), rp=(1, 0.4, 0.4), hm=(0, 166), hf=(0, 70), la=(-20, 40, 17.5)),
        Cn=dict(hip=(0, -9.0, -1.2), tor=(36, 0, 0), lft=(-0.5, 0, 5.0), rft=(0.5, 0, -3.0), ra=(4, -44, 19.6), rp=(1, -0.2, 0.3),
                hm=(2, -82), hf=(0, -90), la=(-60, -70, 17)),
        O=dict(hip=(0, -9.4, -1.2), tor=(38, 0, 0), lft=(-0.5, 0, 5.0), rft=(0.5, 0, -3.0), ra=(4, -46, 19.4), rp=(1, -0.2, 0.3),
               hm=(2, -86), hf=(0, -90), la=(-64, -72, 17)),
        T=dict(hip=(0, -4.5, -0.6), tor=(16, 4, 0), ra=(16, -52, 18.5), hm=(6, -46), la=(-40, -60, 17)))

    def slam_noise(t, it, c):
        # The recoil shakes the arms for the contact phase.
        if c <= t <= c + 4:
            it = dict(it)
            k = (t - c) / 4.0
            it["tor"] = tuple(np.asarray(it["tor"], float) + np.array([1.2 * (1 - k) * math.sin(t * 2.6), 0, 0]))
        return it

    recoil = lambda name: (lambda c: lambda t, it: slam_noise(t, it, c))(TABLE[name][0])
    out.append(strike("hammer_ground_slam", S, **slam, noise=recoil("hammer_ground_slam")))
    # Strike finisher: a windmill. The hammer drops behind the right hip as the weight sinks back, swings up the
    # back and over the top on a straight arm while the left foot takes a long step, and comes down in front.
    out.append(strike("hammer_strike_finisher", S,
        G=dict(hip=(0.4, -3.4, 1.4), tor=(8, 30, -4), rft=(1.0, 0, -3.5), ra=(104, -40, 19.0), rp=(0.4, -1, 0.6), hm=(150, -50),
               hf=(90, 0), la=(-30, -10, 17)),
        L=dict(hip=(0.2, -1.6, 0.8), tor=(-12, 14, -2), rft=(1.0, 0, -3.5), ra=(40, 80, 19.6), rp=(1, 0.2, 0.6), hm=(150, 120),
               hf=(0, 60), la=(-50, 10, 17.5)),
        Cn=dict(hip=(0, -8.0, -3.0), tor=(34, -4, 0), lft=(-0.5, 0, 7.0), rft=(0.5, 0, -3.5), ra=(4, -40, 19.6), rp=(1, -0.2, 0.3),
                hm=(2, -80), hf=(0, -90), la=(-60, -70, 17)),
        O=dict(hip=(0, -8.4, -3.0), tor=(36, -4, 0), lft=(-0.5, 0, 7.0), rft=(0.5, 0, -3.5), ra=(4, -44, 19.4), rp=(1, -0.2, 0.3),
               hm=(2, -84), hf=(0, -90), la=(-64, -72, 17)),
        T=dict(hip=(0, -4.6, -1.4), tor=(16, 4, 0), lft=(-0.5, 0, 4.5), ra=(16, -52, 18.5), hm=(6, -46), la=(-40, -60, 17)),
        noise=recoil("hammer_strike_finisher")))
    # Falling slam: in the air the knees tuck and the hammer is cocked behind the right shoulder; on the contact
    # tick Thor lands in a three-point crouch: right knee nearly down, left foot forward, the left hand on the
    # ground, the hammer driven down beside the right foot.
    out.append(strike("hammer_falling_slam", S,
        G=dict(hip=(0, -4.0, 0.6), tor=(4, 18, -2), rft=(1.0, 0, -1.0), lft=(-0.5, 0, 2.0), ra=(50, 40, 15.5), rp=(1, 0, 0.6),
               hm=(70, 150), hf=(0, 0), la=(-50, -30, 16)),
        L=dict(hip=(0, -3.6, 0.8), tor=(-4, 22, -3), rft=(1.0, 0, -1.0), lft=(-0.5, 0, 2.0), ra=(46, 54, 16.5), rp=(1, 0.1, 0.6),
               hm=(60, 166), hf=(0, 20), la=(-56, -20, 16.5)),
        Cn=dict(hip=(0, -9.6, -1.0), tor=(30, 10, 0), lft=(-1.0, 0, 5.0), rft=(1.5, 0, -5.5), rfr=-25.0, ra=(24, -56, 19.6),
                rp=(1, -0.2, 0.3), hm=(14, -84), hf=(0, -90), la=(-26, -72, 18.5), lp=(-1, -0.2, -0.2)),
        O=dict(hip=(0, -10.0, -1.0), tor=(32, 10, 0), lft=(-1.0, 0, 5.0), rft=(1.5, 0, -5.5), rfr=-25.0, ra=(24, -58, 19.4),
               rp=(1, -0.2, 0.3), hm=(14, -86), hf=(0, -90), la=(-26, -74, 18.5), lp=(-1, -0.2, -0.2)),
        T=dict(hip=(0, -5.0, -0.6), tor=(14, 6, 0), lft=(-0.5, 0, 3.5), rft=(0.8, 0, -3.0), ra=(16, -52, 18.5), hm=(6, -46),
               la=(-40, -60, 17)),
        noise=recoil("hammer_falling_slam")))
    # Heavy diagonals from the shoulder (yaw ±45, pitch 60 -> -40).
    heavy_down = dict(
        G=dict(hip=(0.3, -2.0, 0.6), tor=(-4, 22, -3), ra=(42, 34, 12.0), rp=(1, -0.2, 0.6), hm=(70, 138), hf=(0, 0), la=(-20, -10, 16)),
        L=dict(hip=(0.4, -1.8, 0.9), tor=(-9, 28, -5), ra=(44, 46, 12.5), rp=(1, 0, 0.6), hm=(64, 156), hf=(0, 0), la=(-10, 0, 16.5)),
        Cn=dict(hip=(-0.4, -4.2, -1.6), tor=(16, -20, 3), lft=(-0.5, 0, 5.0), ra=(-18, -22, 19.6), rp=(1, 0, 0.2), hm=(-34, -34),
                hf=(-40, -60), la=(-90, -60, 17)),
        O=dict(hip=(-0.5, -4.8, -1.6), tor=(24, -32, 4), lft=(-0.5, 0, 5.0), ra=(-36, -56, 18.8), rp=(1, 0.4, 0), hm=(-44, -72),
               la=(-110, -50, 17), lp=(-0.2, -1, 0.6)),
        T=dict(hip=(0, -3.2, -0.6), tor=(10, -10, 1), lft=(-0.5, 0, 4.0), ra=(4, -50, 17.5), hm=(-10, -44), la=(-60, -60, 17)))
    out.append(strike("hammer_heavy_down", S, **heavy_down))
    # Strike blow: the wide arc. The arm swings out straight to the right and up (the hammer at arm's length high
    # off the right side, not shouldered like heavy_down), the hips coil back over the right foot; the left foot
    # steps long and the hammer sweeps the big diagonal down across the front to the left knee.
    out.append(strike("hammer_strike_blow", S,
        G=dict(hip=(0.6, -2.6, 1.0), tor=(-2, 30, -6), rft=(1.0, 0, -3.0), ra=(96, 26, 19.4), rp=(0.3, -1, 0.5), hm=(108, 40),
               hf=(0, 0), la=(-30, -10, 16.5)),
        L=dict(hip=(0.8, -2.4, 1.4), tor=(-6, 38, -8), rft=(1.0, 0, -3.0), ra=(98, 40, 19.6), rp=(0.3, -1, 0.5), hm=(112, 62),
               hf=(0, 20), la=(-20, -4, 17)),
        Cn=dict(hip=(-0.4, -4.6, -3.0), tor=(18, -20, 4), lft=(-0.5, 0, 7.5), ra=(-18, -24, 19.6), rp=(1, 0, 0.2), hm=(-36, -36),
                hf=(-40, -60), la=(-90, -60, 17)),
        O=dict(hip=(-0.5, -5.2, -3.0), tor=(24, -32, 5), lft=(-0.5, 0, 7.5), ra=(-36, -56, 18.8), rp=(1, 0.4, 0), hm=(-44, -72),
               la=(-110, -50, 17), lp=(-0.2, -1, 0.6)),
        T=dict(hip=(0, -3.4, -1.2), tor=(10, -10, 1), lft=(-0.5, 0, 4.5), ra=(4, -50, 17.5), hm=(-10, -44), la=(-60, -60, 17))))
    out.append(strike("hammer_heavy_reverse", S,
        G=dict(hip=(-0.3, -2.0, 0.4), tor=(-3, -24, 2), ra=(-30, 34, 17.5), rp=(0.6, 1, 0.2), hm=(-66, 112), hf=(0, 0), la=(-110, -40, 17),
               lp=(-0.2, -1, 0.6)),
        L=dict(hip=(-0.4, -1.8, 0.6), tor=(-7, -30, 3), ra=(-32, 44, 18.0), rp=(0.6, 1, 0.2), hm=(-78, 148), hf=(0, 0),
               la=(-116, -40, 17), lp=(-0.2, -1, 0.6)),
        Cn=dict(hip=(0.5, -4.4, -1.8), tor=(16, 26, -3), rft=(-0.5, 0, 4.0), ra=(36, -22, 19.6), rp=(1, 0.2, 0.2), hm=(44, -34),
                hf=(40, -60), la=(-50, -40, 16)),
        O=dict(hip=(0.6, -5.0, -1.8), tor=(22, 34, -4), rft=(-0.5, 0, 4.0), ra=(54, -58, 18.8), hm=(60, -74), la=(-40, -50, 16)),
        T=dict(hip=(0.2, -3.4, -0.6), tor=(10, 18, -1), rft=(0.0, 0, 1.0), ra=(34, -54, 17.5), hm=(20, -44), la=(-20, -40, 16))))
    # Pommel jab: Mjölnir stood upright in the fist (head up, the butt under the fist), pulled back by the right
    # ribs as the hips coil; the left foot steps in, the hips fire and the fist drives forward and down so the butt
    # leads, the head stays upright behind it (a hammer that pointed back along the forearm would go through it).
    out.append(strike("hammer_pommel", S,
        G=dict(hip=(0.3, -2.6, 0.8), tor=(4, 26, 0), ra=(64, -14, 12.5), rp=(1, -0.6, 0.6), hm=(10, 96), hf=(90, 0), la=(-10, -20, 16)),
        L=dict(hip=(0.4, -2.8, 1.2), tor=(3, 32, 0), ra=(70, -10, 11.5), rp=(1, -0.6, 0.7), hm=(14, 98), hf=(90, 0), la=(-6, -14, 16)),
        Cn=dict(hip=(-0.2, -3.0, -1.4), tor=(12, -8, 0), lft=(-0.5, 0, 5.0), ra=(-4, -16, 19.4), rp=(1, -0.8, 0.2), hm=(-6, 72),
                hf=(90, 0), la=(-70, -60, 17)),
        O=dict(hip=(-0.2, -3.0, -1.4), tor=(13, -10, 0), lft=(-0.5, 0, 5.0), ra=(-4, -18, 19.8), rp=(1, -0.8, 0.2), hm=(-8, 68),
               hf=(90, 0), la=(-70, -60, 17)),
        T=dict(hip=(0, -2.4, -0.5), tor=(7, 4, 0), ra=(20, -30, 16.5), hm=(10, 40), la=(-30, -40, 16))))
    # Double swing: right to left, the wrist turns, left to right (yaw 70 -> -70 and back).
    out.append(strike("hammer_double_swing", S,
        G=dict(hip=(0.5, -2.8, 0.4), tor=(6, 32, -3), ra=(92, -24, 18.8), rp=(0.5, -1, 0.5), hm=(116, -8), hf=(180, 0), la=(-30, -30, 16)),
        L=dict(hip=(0.7, -3.0, 0.5), tor=(6, 42, -4), ra=(100, -18, 19.2), rp=(0.5, -1, 0.5), hm=(130, -4), hf=(180, 0),
               la=(-20, -20, 16)),
        Cn=dict(hip=(-0.5, -3.2, -0.8), tor=(8, -16, 2), ra=(-4, -10, 19.8), rp=(1, -0.4, 0.3), hm=(-20, -2), hf=(-90, 0),
                la=(-80, -50, 16)),
        O=dict(hip=(-0.6, -3.2, -0.8), tor=(8, -34, 3), ra=(-60, -16, 18.8), rp=(0.4, 0.2, -1), hm=(-96, -6), hf=(-90, 0),
               la=(-118, -40, 17), lp=(-0.2, -1, 0.6)),
        T=dict(hip=(0.4, -3.0, -0.2), tor=(7, 24, -2), ra=(56, -22, 18.8), rp=(0.8, -1, 0.3), hm=(84, -10), hf=(90, 0),
               la=(-40, -30, 16))))
    # Thrust: a spear drive. The hammer is drawn back to the right hip, head forward, the hips and shoulders coil to
    # the right and the open left hand reaches out to aim; the left foot steps long, the hips fire, the right arm drives
    # the head straight out at chest height while the left hand pulls back to the left hip (one hand: the far fist
    # cannot reach the handle in front of Thor's chest without going through it).
    out.append(strike("hammer_thrust", S,
        G=dict(hip=(0.4, -2.6, 1.2), tor=(2, 30, 0), rft=(1.0, 0, -3.0), ra=(52, -52, 13.0), rp=(1, 0.2, 0.8), hm=(-4, 4),
               hf=(0, 90), la=(14, -6, 17.5), lp=(-1, -0.6, 0.3), lw=(-50, 0, 0)),
        L=dict(hip=(0.6, -2.8, 1.6), tor=(0, 36, 0), rft=(1.0, 0, -3.0), ra=(56, -50, 12.0), rp=(1, 0.2, 0.9), hm=(-6, 6),
               hf=(0, 90), la=(18, -4, 18.5), lp=(-1, -0.6, 0.3), lw=(-50, 0, 0)),
        Cn=dict(hip=(-0.3, -3.8, -2.4), tor=(14, -18, 0), lft=(-0.5, 0, 8.0), ra=(-14, -6, 19.8), rp=(1, -0.6, 0.2), hm=(-6, -2),
                hf=(0, 90), la=(-70, -66, 15.5), lp=(-1, -0.2, -0.4)),
        O=dict(hip=(-0.3, -4.0, -2.6), tor=(16, -22, 0), lft=(-0.5, 0, 8.0), ra=(-14, -8, 19.9), rp=(1, -0.6, 0.2), hm=(-6, -4),
               hf=(0, 90), la=(-70, -68, 15.5), lp=(-1, -0.2, -0.4)),
        T=dict(hip=(0, -3.0, -1.0), tor=(8, 2, 0), ra=(10, -36, 16.5), hm=(-6, 20), la=(-40, -50, 16))))
    # Lunge: short swing, long step (diagonal 40,30 -> -40,-20).
    out.append(strike("hammer_lunge", S,
        G=dict(hip=(0.3, -2.4, 0.6), tor=(2, 26, -2), ra=(62, 30, 16.5), rp=(1, -0.3, 0.5), hm=(66, 84), la=(-20, -10, 16)),
        L=dict(hip=(0.4, -2.4, 0.8), tor=(0, 32, -3), ra=(70, 40, 17.0), rp=(1, -0.2, 0.5), hm=(74, 108), la=(-14, 0, 16)),
        Cn=dict(hip=(-0.3, -5.0, -4.5), tor=(24, -16, 2), lft=(-0.5, 0, 8.6), rft=(0.5, 0, -3.5), ra=(-24, -12, 19.7),
                rp=(1, -0.2, 0.2), hm=(-42, -22), hf=(-40, -60), la=(-90, -60, 17)),
        O=dict(hip=(-0.4, -5.4, -4.5), tor=(28, -26, 3), lft=(-0.5, 0, 8.6), rft=(0.5, 0, -3.5), ra=(-46, -40, 18.8),
               hm=(-60, -52), la=(-110, -50, 17), lp=(-0.2, -1, 0.6)),
        T=dict(hip=(0, -3.4, -1.5), tor=(12, -8, 0), lft=(-0.5, 0, 5.0), ra=(-10, -48, 17.5), hm=(-20, -40), la=(-60, -60, 17))))
    # Low sweep at shin height (yaw 75 -> -75): crouched, the head passes clear in front of the shins.
    out.append(strike("hammer_low_sweep", S,
        # Audit v3 (5-tick windup): the feet stay planted through the gather, the crouch comes on the load.
        G=dict(hip=(0.5, -5.0, 0.8), tor=(24, 38, -5), ra=(96, -46, 19.4), rp=(0.4, -0.2, 1),
               hm=(112, -38), hf=(180, 0), la=(-40, -40, 16)),
        L=dict(hip=(0.6, -8.0, 1.0), tor=(26, 46, -6), rft=(2.0, 0, -2.0), lft=(-2.0, 0, 3.5), ra=(102, -42, 19.6), rp=(0.4, -0.2, 1),
               hm=(124, -34), hf=(180, 0), la=(-34, -36, 16)),
        Cn=dict(hip=(-0.3, -8.4, -0.6), tor=(30, -8, 1), rft=(2.0, 0, -2.0), lft=(-2.0, 0, 3.5), ra=(10, -50, 19.8), rp=(1, 0, 0.3),
                hm=(-10, -40), hf=(-90, 0), la=(-90, -50, 17)),
        O=dict(hip=(-0.5, -8.0, -0.6), tor=(28, -38, 4), rft=(2.0, 0, -2.0), lft=(-2.0, 0, 3.5), ra=(-64, -44, 19.4),
               rp=(0.6, 0.4, -0.6), hm=(-100, -38), hf=(-90, 0), la=(-120, -30, 17), lp=(-0.2, -1, 0.6)),
        T=dict(hip=(0, -5.0, -0.2), tor=(14, -12, 1), ra=(-20, -56, 18.0), hm=(-50, -40), la=(-60, -50, 17))))
    return out


# ------------------------------------------------------------------------------------------- fists
def fist_clips():
    S = GUARD
    out = []
    fp = lambda contact="fist": C.FpPlan(hammer=False, guard=True, left="always", contact=contact)
    out.append(strike("fist_jab", S,
        G=dict(tor=(6, 22, 0), la=(18, 2, 13.0), lp=(-0.6, -1, 0.3)),
        L=dict(tor=(6, 24, 0), hip=(0, -2.4, 0.4), la=(16, 4, 12.0), lp=(-0.6, -1, 0.3)),
        Cn=dict(tor=(7, 34, 0), hip=(0, -2.4, -1.2), lft=(-0.5, 0, 4.5), la=(12, 4, 20.0), lp=(-1, -0.6, 0.2), lw=(0, 0, 0)),
        O=dict(tor=(7, 36, 0), hip=(0, -2.4, -1.4), lft=(-0.5, 0, 4.5), la=(11, 3, 20.3), lp=(-1, -0.6, 0.2)),
        T=dict(tor=(6, 22, 0), hip=(0, -2.3, -0.4), la=(20, 4, 15.5)), hammer=False, fp=fp("left")))
    out.append(strike("fist_cross", S,
        G=dict(tor=(6, 26, 0), hip=(0, -2.4, 1.0), ra=(-24, 0, 12.0)),
        L=dict(tor=(6, 32, 0), hip=(0, -2.6, 1.2), ra=(-22, -2, 11.0)),
        Cn=dict(tor=(9, -22, 0), hip=(0, -2.8, -1.6), ra=(-10, 2, 20.0), rp=(1, -0.6, 0.2), rw=(0, 0, 0), la=(30, 4, 12.5)),
        O=dict(tor=(9, -26, 0), hip=(0, -2.8, -1.8), ra=(-12, 1, 20.3), rp=(1, -0.6, 0.2), la=(30, 4, 12.5)),
        T=dict(tor=(7, 4, 0), hip=(0, -2.4, -0.4), ra=(-26, 0, 14.5)), hammer=False, fp=fp()))
    out.append(strike("fist_hook", S,
        G=dict(tor=(6, 30, 0), la=(-24, 6, 15.0), lp=(-0.4, 1, 0.2)),
        L=dict(tor=(6, 40, 0), hip=(0, -2.6, 0.4), la=(-36, 8, 15.0), lp=(-0.4, 1, 0.1)),
        Cn=dict(tor=(7, -18, 0), hip=(0, -2.8, -0.8), la=(40, 6, 15.5), lp=(-0.6, 1, 0.4), lw=(0, 0, 0)),
        O=dict(tor=(7, -28, 0), hip=(0, -2.8, -0.8), la=(56, 4, 14.0), lp=(-0.6, 1, 0.5)),
        T=dict(tor=(6, 4, 0), la=(30, 4, 15.0)), hammer=False, fp=fp("left")))
    out.append(strike("fist_uppercut", S,
        G=dict(tor=(14, 26, 0), hip=(0, -4.6, 0.6), ra=(16, -52, 12.5), rp=(0.8, -1, 0.4)),
        L=dict(tor=(18, 30, 0), hip=(0, -5.6, 0.8), ra=(20, -60, 12.5), rp=(0.8, -1, 0.5)),
        Cn=dict(tor=(-4, -10, 0), hip=(0, -1.4, -1.0), ra=(-18, 36, 15.5), rp=(1, -1, 0.6), rw=(-30, 0, 0)),
        O=dict(tor=(-7, -14, 0), hip=(0, -1.2, -1.0), ra=(-14, 52, 15.5), rp=(1, -0.6, 0.6)),
        T=dict(tor=(4, 6, 0), hip=(0, -2.2, -0.3), ra=(-24, 6, 14.0)), hammer=False, fp=fp()))
    out.append(strike("fist_haymaker", S,
        G=dict(tor=(5, 40, 0), hip=(0.4, -2.6, 0.8), ra=(84, 10, 15.5), rp=(0.4, 0.6, 0.6)),
        L=dict(tor=(4, 52, 0), hip=(0.6, -2.8, 1.0), ra=(102, 14, 16.0), rp=(0.3, 0.6, 0.7)),
        Cn=dict(tor=(11, -30, 0), hip=(-0.5, -3.0, -1.6), lft=(-0.5, 0, 5.0), ra=(-18, 2, 19.5), rp=(1, 0.2, 0.2), rw=(0, 0, 0),
                la=(30, -20, 12.0)),
        O=dict(tor=(13, -44, 0), hip=(-0.6, -3.0, -1.6), lft=(-0.5, 0, 5.0), ra=(-48, -8, 17.5), rp=(0.6, 0.6, -0.4),
               la=(30, -20, 12.0)),
        T=dict(tor=(7, -4, 0), hip=(0, -2.4, -0.4), ra=(-28, -2, 14.0)), hammer=False, fp=fp()))
    hf = dict(
        G=dict(tor=(0, 14, 0), ra=(10, 58, 16.0), rp=(1, 0.2, 0.4)),
        L=dict(tor=(-6, 12, 0), hip=(0, -1.8, 0.4), ra=(12, 74, 18.0), rp=(1, 0.3, 0.4)),
        Cn=dict(tor=(16, -4, 0), hip=(0, -3.6, -1.0), ra=(-6, -16, 19.4), rp=(1, -0.4, 0.3), rw=(0, 0, 0)),
        O=dict(tor=(18, -6, 0), hip=(0, -3.8, -1.0), ra=(-6, -38, 18.8), rp=(1, -0.4, 0.3)),
        T=dict(tor=(8, 8, 0), hip=(0, -2.6, -0.3), ra=(-24, -8, 14.5)))
    out.append(strike("fist_hammer_fist", S, **hf, hammer=False, fp=fp()))
    hfl = {k: {kk: vv for kk, vv in v.items() if kk not in ("ra", "rp", "rw")} for k, v in hf.items()}
    for k, (la, lp) in {"G": ((-10, 58, 16.0), (-1, 0.2, 0.4)), "L": ((-12, 74, 18.0), (-1, 0.3, 0.4)),
                        "Cn": ((6, -16, 19.4), (-1, -0.4, 0.3)), "O": ((6, -38, 18.8), (-1, -0.4, 0.3)),
                        "T": ((22, 4, 15.0), (-0.5, -1, 0.3))}.items():
        hfl[k]["la"], hfl[k]["lp"] = la, lp
        hfl[k]["tor"] = (hf[k]["tor"][0], hf[k]["tor"][1] + 18, hf[k]["tor"][2])
    out.append(strike("fist_hammer_fist_left", S, **hfl, hammer=False, fp=fp("left")))
    out.append(strike("fist_ground_pound", S,
        G=dict(tor=(-6, 4, 0), hip=(0, -1.4, 0.4), ra=(4, 70, 17.5), rp=(1, 0.3, 0.3), la=(-4, 70, 17.5), lp=(-1, 0.3, 0.3)),
        L=dict(tor=(-10, 4, 0), hip=(0, -1.3, 0.6), ra=(2, 80, 18.5), rp=(1, 0.3, 0.3), la=(-2, 80, 18.5), lp=(-1, 0.3, 0.3)),
        Cn=dict(tor=(38, 0, 0), hip=(0, -8.2, -1.0), lft=(-0.5, 0, 4.0), rft=(2.0, 0, -2.0), ra=(-12, -58, 19.4), rp=(1, 0, 0.4), la=(12, -58, 19.4),
                lp=(-1, 0, 0.4), rw=(0, 0, 0), lw=(0, 0, 0)),
        O=dict(tor=(40, 0, 0), hip=(0, -8.6, -1.0), lft=(-0.5, 0, 4.0), rft=(2.0, 0, -2.0), ra=(-12, -60, 19.4), rp=(1, 0, 0.4), la=(12, -60, 19.4),
               lp=(-1, 0, 0.4)),
        T=dict(tor=(16, 6, 0), hip=(0, -4.6, -0.4), ra=(-20, -30, 15.0), la=(16, -30, 15.0)), hammer=False, fp=fp()))
    out.append(strike("fist_body_blow", S,
        G=dict(tor=(12, 24, 0), hip=(0, -4.4, 0.6), ra=(-6, -40, 12.0), rp=(1, -1, 0.5)),
        L=dict(tor=(14, 30, 0), hip=(0, -5.0, 0.8), ra=(-4, -44, 11.0), rp=(1, -1, 0.6)),
        Cn=dict(tor=(20, -18, 0), hip=(0, -5.4, -1.4), lft=(-0.5, 0, 5.0), ra=(-12, -30, 19.4), rp=(1, -0.6, 0.2), rw=(0, 0, 0)),
        O=dict(tor=(21, -22, 0), hip=(0, -5.4, -1.6), lft=(-0.5, 0, 5.0), ra=(-14, -30, 19.8), rp=(1, -0.6, 0.2)),
        T=dict(tor=(10, 4, 0), hip=(0, -3.4, -0.4), ra=(-24, -8, 14.0)), hammer=False, fp=fp()))
    out.append(strike("fist_elbow", S,
        G=dict(tor=(6, 32, 0), la=(-30, 8, 8.0), lp=(-1, 0.3, -0.3)),
        L=dict(tor=(6, 40, 0), hip=(0, -2.6, 0.4), la=(-36, 10, 7.5), lp=(-1, 0.3, -0.2)),
        Cn=dict(tor=(8, -24, 0), hip=(0, -2.8, -1.0), lft=(-0.5, 0, 4.5), la=(30, 4, 11.5), lp=(-0.3, 0.3, -1)),
        O=dict(tor=(8, -32, 0), hip=(0, -2.8, -1.0), lft=(-0.5, 0, 4.5), la=(36, 4, 11.5), lp=(-0.3, 0.3, -1)),
        T=dict(tor=(6, 4, 0), la=(24, 4, 14.0)), hammer=False, fp=fp("left")))
    out.append(strike("fist_push_kick", S,
        G=dict(tor=(-6, 8, 0), hip=(0, -1.4, 0.6), rft=(0.8, 9.0, 7.5), rk=(0.4, 0.4, 1)),
        L=dict(tor=(-11, 6, 0), hip=(0, -1.2, 0.8), rft=(0.8, 11.0, 8.8), rk=(0.4, 0.6, 1)),
        Cn=dict(tor=(-14, 2, 0), hip=(0, -1.0, 0.8), rft=(0.5, 11.1, 17.0), rk=(0.2, 1, 0.3), ra=(-20, -10, 14.0), la=(20, -10, 15.0)),
        O=dict(tor=(-15, 2, 0), hip=(0, -1.0, 0.8), rft=(0.5, 10.7, 17.5), rk=(0.2, 1, 0.3), ra=(-20, -10, 14.0), la=(20, -10, 15.0)),
        T=dict(tor=(-4, 10, 0), hip=(0, -1.8, 0.3), rft=(0.5, 5.0, 6.0), rk=(0.2, 0.4, 1)),
        hammer=False, fp=fp(None)))
    out.append(strike("fist_tackle", S,
        G=dict(tor=(26, -14, 0), hip=(0, -5.0, 0.6), ra=(-30, -34, 11.0), la=(20, -30, 12.0)),
        L=dict(tor=(30, -20, 0), hip=(0, -5.6, 0.8), ra=(-30, -36, 10.5), la=(20, -32, 12.0)),
        Cn=dict(tor=(34, -34, 0), hip=(0, -5.6, -4.0), lft=(-0.5, 0, 9.5), rft=(0.5, 0, -2.0), ra=(-34, -40, 10.5), la=(16, -36, 12.0)),
        O=dict(tor=(34, -36, 0), hip=(0, -5.6, -4.4), lft=(-0.5, 0, 9.5), rft=(0.5, 0, -2.0), ra=(-34, -40, 10.5), la=(16, -36, 12.0)),
        T=dict(tor=(16, -6, 0), hip=(0, -3.6, -1.4), lft=(-0.5, 0, 5.0), ra=(-28, -10, 13.0)), hammer=False, fp=fp(None)))
    out.append(strike("fist_tackle_hook", S,
        G=dict(tor=(24, 30, 0), hip=(0.4, -4.4, 0.4), ra=(70, 0, 15.0), rp=(0.4, 0.8, 0.5)),
        L=dict(tor=(22, 40, 0), hip=(0.5, -4.6, 0.6), ra=(80, 4, 15.5), rp=(0.3, 0.8, 0.5)),
        Cn=dict(tor=(10, -26, 0), hip=(-0.4, -3.0, -1.2), ra=(-30, 6, 16.0), rp=(0.6, 1, 0.3), rw=(0, 0, 0), la=(30, -10, 12.0)),
        O=dict(tor=(10, -36, 0), hip=(-0.5, -3.0, -1.2), ra=(-52, 6, 14.5), rp=(0.4, 1, -0.2), la=(30, -10, 12.0)),
        T=dict(tor=(7, 0, 0), hip=(0, -2.4, -0.4), ra=(-28, 0, 14.0)), hammer=False, fp=fp()))
    return out


# ------------------------------------------------------------------------------------------- draw, electric, wrath
def power_clips():
    out = []
    # Draw: the hand comes out round the hammer, snatches the grip (one tick: no fist through the handle),
    # pulls the hammer off the hip, rips it up on the contact tick, its weight drops the arm, into the stance.
    grip = dict(ra="belt", rp=(1, 0, 0.4), hm="belt", hf="belt_face")
    near = dict(grip, ra="belt+5.0,0.5,0.0")
    out.append(strike("draw", IDLE,
        G=dict(tor=(5, 10, -4), hip=(0, -1.8, 0), ra=(78, -66, 17.0), rp=(1, 0.2, 0.2), hm="belt", hf="belt_face"),
        L=dict(tor=(6, 14, -7), hip=(0.4, -2.8, 0), bs=10.0, bp=(3.0, 0.6, 0.4), **grip),
        Cn=dict(tor=(6, 14, -7), hip=(0.4, -2.8, 0), bs=12.0, bp=(3.4, 1.0, 0.6), **grip),
        O=dict(tor=(4, 8, -2), hip=(0.2, -3.0, 0), ra=(44, -40, 17.0), rp=(1, -0.6, 0.3), hm=(20, 30), la=(-40, -60, 18)),
        T=dict(tor=(8, 10, 0), hip=(0, -3.4, 0), ra=(36, -56, 17.5), rp=(1, -0.8, 0.2), hm=(-4, 10), la=(-10, -40, 17)),
        extra=((9, key(IDLE, tor=(6, 13, -6), hip=(0.3, -2.4, 0), **near), "linear"),
               (10, key(IDLE, tor=(6, 14, -7), hip=(0.4, -2.6, 0), bs=4.0, bp=(2.7, 0.4, 0.2), **grip), "easeInOutSine")),
        end=STANCE_H, fp=fp_track([(0, C.FP_FIST_R, None), (6, (0.52, -0.78, -0.62), None), (10, FP_LOW, None),
                                   (TABLE["draw"][0], FP_LOW, FP_HANG), (20, FP_OUT[0], FP_OUT[1]),
                                   (26, (0.46, -0.48, -0.80), (0.05, 0.95, -0.30)), (34, C.FP_HAMMER_R, C.FP_HAMMER_DIR)])))
    out[-1].hand_span = (TABLE["draw"][0], 10 ** 6)  # ThorActions: the hammer leaves the belt on the contact tick
    # Holster: the hammer comes down to the hip, the fist sets it in the loop, lets go on the contact tick,
    # opens off the handle (one tick) and the arm comes back along the body while the hammer settles.
    out.append(strike("holster", STANCE_H,
        G=dict(tor=(6, 12, -4), hip=(0.2, -2.8, 0), ra=(56, -70, 18.5), rp=(1, -0.2, 0.4), hm=(30, -70), hf="belt_face",
               la=(-30, -60, 18)),
        L=dict(tor=(6, 14, -7), hip=(0.4, -2.8, 0), la=(-40, -70, 18.5), bs=12.0, bp=(3.4, 1.0, 0.6), **grip),
        Cn=dict(tor=(6, 14, -7), hip=(0.4, -3.0, 0), la=(-40, -70, 18.5), bs=10.0, bp=(3.0, 0.6, 0.4), **grip),
        O=dict(tor=(6, 10, -6), hip=(0.3, -2.6, 0), ra=(78, -66, 17.0), rp=(1, 0.2, 0.2), la=(-50, -76, 19)),
        T=dict(tor=(5, 4, -1), hip=(0, -1.2, 0), ra=(62, -80, 19.0), la=(-58, -80, 19.4)),
        extra=((15, key(STANCE_H, tor=(6, 14, -7), hip=(0.4, -3.0, 0), la=(-40, -70, 18.5), bs=6.0, bp=(2.7, 0.4, 0.2), **grip),
                "linear"),
               (16, key(STANCE_H, tor=(6, 13, -7), hip=(0.4, -2.9, 0), la=(-44, -72, 18.7), bp=(1.2, 0.2, 0.1), **near),
                "easeInOutSine")),
        end=IDLE, fp=fp_track([(0, C.FP_HAMMER_R, C.FP_HAMMER_DIR), (6, FP_OUT[0], FP_OUT[1]), (11, FP_LOW, FP_HANG),
                               (TABLE["holster"][0], FP_LOW, FP_HANG), (18, FP_LOW, None), (26, (0.52, -0.62, -0.66), None),
                               (32, C.FP_FIST_R, None)])))
    out[-1].hand_span = (0, TABLE["holster"][0] - 1)  # back on the belt from the contact tick
    raised = key(STANCE_H, hip=(0, -1.6, 0), tor=(-6, 6, 0), ra=(16, 66, 18.8), rp=(1, 0.2, 0.3), hm=(8, 100), hf=(0, 0),
                 la=(-50, 10, 17.5))
    w, c, r = TABLE["electric_mode"]
    out.append(C.Clip("electric_mode", w + c + r, [(0, raised, "easeOutSine"), (2, key(raised, ra=(16, 72, 19.2)), "easeInOutSine"),
                                                  (4, STANCE_H, "easeInOutSine")], contact=0, beats=C.beats(w, c, r),
                      fp=C.FpPlan(hammer=True, contact=None), owner="upper"))
    out.append(strike("electric_discharge_right", STANCE_H,
        G=dict(tor=(4, 18, 0), ra=(34, -18, 13.0), hm=(10, 50)),
        L=dict(tor=(3, 22, 0), ra=(36, -14, 12.0), hm=(12, 56)),
        Cn=dict(tor=(8, -10, 0), hip=(0, -2.6, -0.8), ra=(-6, -2, 20.0), rp=(1, -0.5, 0.2), hm=(-4, 4), hf=(0, 90)),
        O=dict(tor=(2, -4, 0), hip=(0, -2.4, 0.2), ra=(0, 6, 17.5), rp=(1, -0.5, 0.2), hm=(0, 22)),
        T=dict(tor=(6, 6, 0), ra=(20, -26, 16.5), hm=(-6, 34)), owner="upper"))
    out.append(strike("electric_discharge_left", STANCE_H,
        G=dict(tor=(4, -14, 0), la=(-30, -10, 13.0), ra=(50, -50, 15.5)),
        L=dict(tor=(3, -18, 0), la=(-32, -6, 12.0), ra=(54, -52, 15.5)),
        Cn=dict(tor=(8, 22, 0), hip=(0, -2.6, -0.8), la=(8, -2, 20.0), lp=(-1, -0.5, 0.2), lw=(-60, 0, 0), ra=(60, -56, 16.0)),
        O=dict(tor=(2, 16, 0), hip=(0, -2.4, 0.2), la=(4, 6, 17.5), lp=(-1, -0.5, 0.2), lw=(-40, 0, 0), ra=(58, -54, 16.0)),
        T=dict(tor=(6, 12, 0), la=(-4, -30, 16.5)), owner="upper", fp=C.FpPlan(hammer=True, left="always", contact="left")))
    # Wave: the weight rocks back onto the right foot while Mjölnir rises high over the right shoulder (the charge)
    # and the open left hand draws back by the ribs; the left foot steps in and both arms drive forward together,
    # the hammer head and the left palm side by side, the lightning pushed out in front.
    out.append(strike("electric_wave", STANCE_H,
        G=dict(tor=(-5, 16, 0), hip=(0.5, -2.4, 1.2), rft=(1.0, 0, -3.0), ra=(30, 58, 17.0), rp=(1, -0.2, 0.4), hm=(10, 100),
               hf=(0, 0), la=(-56, -24, 13.5), lp=(-1, -0.4, 0.3), lw=(-30, 0, 0)),
        L=dict(tor=(-9, 20, 0), hip=(0.6, -2.6, 1.6), rft=(1.0, 0, -3.0), ra=(26, 68, 18.0), rp=(1, -0.2, 0.4), hm=(6, 118),
               hf=(0, 0), la=(-60, -16, 12.5), lp=(-1, -0.4, 0.3), lw=(-40, 0, 0)),
        Cn=dict(tor=(14, -6, 0), hip=(0, -3.6, -1.8), lft=(-0.5, 0, 6.0), ra=(-12, -2, 19.6), rp=(1, -0.5, 0.2), hm=(0, 8),
                hf=(90, 0), la=(14, -2, 19.4), lp=(-1, -0.5, 0.2), lw=(-60, 0, 0)),
        O=dict(tor=(16, -8, 0), hip=(0, -3.8, -1.9), lft=(-0.5, 0, 6.0), ra=(-12, -4, 19.8), rp=(1, -0.5, 0.2), hm=(0, 4),
               hf=(90, 0), la=(14, -4, 19.6), lp=(-1, -0.5, 0.2), lw=(-60, 0, 0)),
        T=dict(tor=(8, 6, 0), hip=(0, -3.0, -0.6), ra=(10, -30, 16.5), hm=(-10, 40), la=(-10, -40, 16.5)),
        fp=C.FpPlan(hammer=True, left="always")))
    # Storm: Mjölnir held high the whole growth, the arm trembling, then 12 ticks down.
    w, c, r = TABLE["electric_storm"]
    high = key(STANCE_H, hip=(0, -2.6, 0), tor=(-8, 4, 0), ra=(10, 82, 19.9), rp=(1, 0.3, 0.2), hm=(4, 94), hf=(0, 0),
               la=(-70, 20, 18.5), lp=(-0.4, -1, 0.3))
    bt = C.beats(w, c, r)
    out.append(C.Clip("electric_storm", w + c + r, [(0, high, "easeInOutSine"), (bt["overswing"], key(high, tor=(-9, 4, 0)), "easeInOutSine"),
                                                   (bt["settle"], key(STANCE_H, ra=(30, 10, 17.5), hm=(-8, 70)), "easeInOutSine"),
                                                   (bt["end"], STANCE_H, "easeInOutSine")],
                      contact=0, beats=bt, noise=lambda t, it: tremble(t, it, 1.0, 0, bt["overswing"]), owner="upper",
                      fp=C.FpPlan(hammer=True, contact=None)))
    # Wrath (6.4 s): the hammer slowly up to the sky, the storm builds (strain, tremble), the strike at
    # 3.6 s down into the ground, the recoil, the long rise.
    w, c, r = TABLE["wrath"]
    bt = C.beats(w, c, r)
    sky = key(STANCE_H, hip=(0, -1.8, 0.4), tor=(-10, 2, 0), ra=(6, 84, 20.0), rp=(1, 0.3, 0.2), hm=(2, 96), hf=(0, 0),
              la=(-80, 10, 18.5), lp=(-0.3, -1, 0.3))
    strain = key(sky, hip=(0, -2.8, 0.6), tor=(-12, 2, -1), la=(-86, 20, 18.5))
    top = key(sky, hip=(0, -1.2, 0.8), tor=(-14, 2, 0), ra=(4, 86, 20.1), hm=(0, 150), hf=(0, 60))
    hit = key(STANCE_H, hip=(0, -9.6, -1.4), tor=(38, 0, 0), lft=(-0.5, 0, 5.0), rft=(0.5, 0, -3.0), ra=(4, -46, 19.6),
              rp=(1, -0.2, 0.3), hm=(2, -84), hf=(0, -90), la=(-64, -72, 17))
    keys = [(0, STANCE_H, "easeInOutSine"), (bt["gather"], sky, "easeInOutSine"), (bt["gather"] + 25, strain, "easeInOutSine"),
            (bt["loaded"], top, "easeInCubic"), (bt["contact"], hit, "easeOutCubic"),
            (bt["overswing"], key(hit, hip=(0, -10.0, -1.4), tor=(40, 0, 0)), "easeInOutSine"),
            (bt["settle"], key(STANCE_H, hip=(0, -5.0, -0.6), tor=(16, 4, 0), ra=(16, -52, 18.5), hm=(6, -46), la=(-40, -60, 17)),
             "easeInOutSine"), (bt["end"], STANCE_H, "easeInOutSine")]

    def wrath_noise(t, it):
        if bt["gather"] <= t < bt["loaded"]:
            amp = 0.4 + 1.2 * (t - bt["gather"]) / (bt["loaded"] - bt["gather"])
            it = tremble(t, it, amp)
        if bt["contact"] <= t <= bt["overswing"]:
            it = dict(it)
            k = (t - bt["contact"]) / (bt["overswing"] - bt["contact"])
            it["tor"] = tuple(np.asarray(it["tor"], float) + np.array([1.4 * (1 - k) * math.sin(t * 2.6), 0, 0]))
        return it

    out.append(C.Clip("wrath", w + c + r, keys, contact=w, beats=bt, noise=wrath_noise, fp=C.FpPlan(hammer=True)))
    return out


# ------------------------------------------------------------------------------------------- locomotion, lift, throw, flight
def breathe(amp, period):
    def f(t, it):
        it = dict(it)
        s = math.sin(2 * math.pi * t / period)
        it["tor"] = tuple(np.asarray(it["tor"], float) + np.array([0.8 * amp * s, 0, 0]))
        it["hip"] = tuple(np.asarray(it["hip"], float) + np.array([0, 0.25 * amp * s, 0]))
        return it
    return f


def loop(name, length, keys, hammer, fp, noise=None, owner="full"):
    return C.Clip(name, length, keys, loop=True, hammer=hammer, owner=owner, fp=fp, noise=noise)


def fp_hold(hammer, right, ham, left):
    """A held first-person pose (the guard): both arms on fixed view targets, the hammer on a fixed axis."""
    def custom(t, rt, h, lt, length=None):
        return np.asarray(right, float), None if ham is None else C.unit(np.asarray(ham, float)), np.asarray(left, float)
    return C.FpPlan(hammer=hammer, contact=None, left="always", custom=custom)


def fp_loop(hammer, bob=0.0, sway=0.0, cycles=1, breath=0.0):
    rest = C.FP_HAMMER_R if hammer else C.FP_FIST_R

    def custom(t, rt, ham, lt, length=None):
        return rt, ham, lt
    plan = C.FpPlan(hammer=hammer, contact=None, left="never")
    plan.loop = (bob, sway, cycles, breath)
    return plan


# Gait (owner playtest 2026-10-28: walk / run redone). A heavy 3-metre body: the stance foot moves back in a
# straight line at constant speed (ThorClipPlayer drives the cycle by distance walked, GAIT_BLOCKS, so it never
# slides), the sole rolls heel -> flat -> toe, the pelvis drops after each contact and rises over the passing
# foot, it sways over the stance leg, the shoulders turn against the stepping leg, the feet track under the hips.
GAIT = {
    # stride: stance-foot travel each side of the hips (px); stance: share of the cycle a foot is down;
    # lift: swing foot height (px); drop / rise: pelvis at the down / passing beat (px); sway: pelvis over the
    # stance leg (px); twist: shoulders against the legs (deg); roll: torso roll over the stance leg (deg);
    # heel / toe: sole roll at contact / toe-off (deg, + toes up); inward: feet under the hips (px).
    "walk": dict(stride=8.5, stance=0.48, lift=3.2, drop=-2.4, rise=-0.5, sway=0.7, twist=7.0, roll=1.6, heel=14.0,
                 toe=-22.0, inward=1.4, length=24),
    "run": dict(stride=8.0, stance=0.34, lift=4.5, drop=-3.0, rise=-0.8, sway=0.5, twist=10.0, roll=1.0, heel=8.0,
                toe=-28.0, inward=1.6, length=24),
}


THROW_COIL = 10  # ticks of throw_windup: the INTRO's 4 and 6 more of the hold, then throw_hold loops


def gait_blocks(g):
    """World distance (blocks) of one full gait cycle: the stance foot covers 2 * stride px in `stance` of it."""
    return 2.0 * g["stride"] / 16.0 / g["stance"]


def _foot(ph, g, side):
    """(foot offset (x, y, z) px, sole roll deg) of one foot at cycle phase ph (0 = this foot's heel strike)."""
    S, st = g["stride"], g["stance"]
    ph %= 1.0
    x = -g["inward"] if side == "r" else g["inward"]
    if ph < st:
        f = ph / st
        z = S - 2.0 * S * f  # straight, constant speed: no slide
        roll = g["heel"] * max(0.0, 1.0 - f / 0.18) + g["toe"] * max(0.0, (f - 0.72) / 0.28) ** 1.5
        return (x, 0.0, z), roll
    f = (ph - st) / (1.0 - st)
    # Swing: the foot leaves behind (toe first), passes under the hips, reaches forward and settles on the heel.
    z = -S + 2.0 * S * (0.5 - 0.5 * math.cos(math.pi * f))
    y = g["lift"] * math.sin(math.pi * min(1.0, f * 1.08)) ** 1.3
    roll = g["toe"] * max(0.0, 1.0 - f / 0.35) * 0.8 + g["heel"] * max(0.0, (f - 0.7) / 0.3)
    return (x, y, z), roll


def gait_keys(base, g, swing, arm_out, hammer, keys=12):
    """One loop of the gait (right heel strike at 0), `keys` evenly spaced linear keys over g["length"] ticks."""
    ks = []
    n = g["length"]
    for i in range(keys + 1):
        ph = i / float(keys)
        rf, rr = _foot(ph, g, "r")
        lf, lr = _foot(ph + 0.5, g, "l")
        # Pelvis: two bobs a cycle, lowest just after each contact, highest over the passing foot.
        b = 0.5 - 0.5 * math.cos(2 * math.pi * ((2 * ph - 0.12) % 1.0))
        hy = base["hip"][1] + g["drop"] + (g["rise"] - g["drop"]) * b
        side = math.cos(2 * math.pi * (ph - 0.18))  # +1 over the right (stance) leg
        hip = (g["sway"] * side, hy, base["hip"][2])
        # Shoulders turn against the forward leg; the arms swing with the shoulders, a beat late.
        lead = math.cos(2 * math.pi * ph)  # +1 when the right foot is forward
        arm = math.cos(2 * math.pi * (ph - 0.06))
        tor = (base["tor"][0], g["twist"] * lead, -g["roll"] * side)
        ra = (arm_out, -90 - swing * arm, base["ra"][2])
        la = (-arm_out, -90 + swing * arm, base["la"][2])
        it = key(base, rft=rf, lft=lf, rfr=rr, lfr=lr, hip=hip, tor=tor, ra=ra, la=la, arc=False)
        if hammer:
            it["hm"] = (16, -84 + 4 * arm)  # the hammer barely swings: the arm carries its weight
        ks.append((round(ph * n, 3), it, "linear"))
    ks[-1] = (n, ks[0][1], "linear")
    return ks


def base_clips():
    out = []
    out.append(loop("idle", 60, [(0, IDLE, "linear"), (60, IDLE, "linear")], False, fp_loop(False, breath=0.006), noise=breathe(1.0, 60)))
    out.append(loop("idle_hammer", 60, [(0, IDLE_H, "easeInOutSine"), (30, key(IDLE_H, hm=(10, -79)), "easeInOutSine"),
                                         (60, IDLE_H, "easeInOutSine")], True, fp_loop(True, breath=0.006), noise=breathe(1.0, 60)))
    out.append(loop("stance", 50, [(0, GUARD, "linear"), (50, GUARD, "linear")], False, fp_loop(False, breath=0.008), noise=breathe(1.2, 50)))
    out.append(loop("stance_hammer", 50, [(0, STANCE_H, "linear"), (50, STANCE_H, "linear")], True, fp_loop(True, breath=0.008),
                    noise=breathe(1.2, 50)))
    # Audit v3 guard (F held, ThorGuard): Mjölnir raised across the body, head up and to the left, the left
    # forearm braced behind it; bare-handed, a high guard with both fists by the face. Looped holds that breathe.
    out.append(loop("guard_hammer", 40, [(0, GUARD_HAMMER, "linear"), (40, GUARD_HAMMER, "linear")], True,
                    fp_hold(True, (0.30, -0.46, -0.72), (-0.55, 0.80, -0.25), (-0.34, -0.40, -0.72)), noise=breathe(0.8, 40)))
    out.append(loop("guard_fist", 40, [(0, GUARD_FIST, "linear"), (40, GUARD_FIST, "linear")], False,
                    fp_hold(False, (0.26, -0.12, -0.72), None, (-0.26, -0.12, -0.72)), noise=breathe(0.8, 40)))
    W, Rn = GAIT["walk"], GAIT["run"]
    walk = key(IDLE, hip=(0, 0.0, 0), tor=(6, 0, 0))
    out.append(loop("walk", W["length"], gait_keys(walk, W, 15.0, 14.0, False), False,
                    fp_loop(False, bob=0.014, sway=0.008, cycles=1)))
    walk_h = key(IDLE_H, hip=(0, 0.0, 0), tor=(6, 0, 0))
    wh = gait_keys(walk_h, W, 9.0, 18.0, True)
    for _, it, _ in wh:  # the hammer arm hangs still under the weight; only the free arm swings fully
        it["ra"] = (IDLE_H["ra"][0], IDLE_H["ra"][1] + 0.25 * (it["ra"][1] + 90), IDLE_H["ra"][2])
    out.append(loop("walk_hammer", W["length"], wh, True, fp_loop(True, bob=0.014, sway=0.008, cycles=1)))
    run = key(IDLE, hip=(0, 0.0, -0.6), tor=(13, 0, 0), ra=(20, -60, 13.0), la=(-20, -60, 13.0))
    out.append(loop("run", Rn["length"], gait_keys(run, Rn, 34.0, 12.0, False), False,
                    fp_loop(False, bob=0.03, sway=0.014, cycles=1)))
    run_h = key(IDLE_H, hip=(0, 0.0, -0.6), tor=(13, 0, 0), la=(-20, -60, 13.0))
    rkh = gait_keys(run_h, Rn, 30.0, 12.0, True)
    for _, it, _ in rkh:
        it["ra"] = (40, -66 + 0.2 * (it["ra"][1] + 90), 18.5)  # the hammer arm carried, almost still
        it["hm"] = (150, -56)  # the hammer trails back-down, clear of the leg
    out.append(loop("run_hammer", Rn["length"], rkh, True, fp_loop(True, bob=0.03, sway=0.014, cycles=1)))

    # Lift (anyone, Thor's body): squat over the planted handle; ThorLift solves both arms onto it.
    squat = key(C.BASE, hip=(0, -9.5, 3.0), tor=(34, 0, 0), rft=(2.5, 0, 0), lft=(-2.5, 0, 0), rk=(0.9, 0, 1), lk=(-0.9, 0, 1),
                ra=(10, -56, 15.0), la=(-10, -56, 15.0), rp=(1, -0.4, 0.3), lp=(-1, -0.4, 0.3))

    # Thor pulls his own planted hammer: from the squat, yank, the hammer comes up in an arc, into the stance.
    # The left hand braces on the left knee (Thor's body is too broad round the belly for both fists on a handle
    # planted between the feet: the far arm would go through the chest), the right fist alone rips the hammer out.
    held = key(squat, hm=(0, -88), hf=(0, 0), la=(-10, -58, 16.5), lp=(-1, -0.2, -0.3), ra=(4, -30, 18.5), rp=(0.4, 0.1, -1))
    out.append(C.Clip("lift_thor", 24, [
        (0, held, "easeInOutSine"),
        (6, key(held, hip=(0, -5.0, 2.6), tor=(14, 0, 0), ra=(6, -26, 19.6), hm=(0, -62)), "easeOutCubic"),
        (11, key(STANCE_H, hip=(0, -1.6, 0.4), tor=(-6, 10, 0), ra=(30, 16, 17.5), rp=(1, -0.4, 0.3), hm=(16, 64), hf=(90, 0),
                 la=(-40, -20, 17)), "easeOutSine"),
        (17, key(STANCE_H, hip=(0, -3.0, 0), tor=(9, 12, 0), ra=(34, -50, 16.5), hm=(-12, 30)), "easeInOutSine"),
        (24, STANCE_H, "easeInOutSine")], beats={"gather": 4, "settle": 17}, fp=C.FpPlan(hammer=True, contact=None),
        noise=lambda t, it: tremble(t, it, 1.2, 0, 5)))  # the strain before the hammer gives (owner: a heavy lift)
    # Throw (owner playtest: it snapped and cut off). The server's wind-up is 4 ticks (MjolnirTuning.THROW_WINDUP),
    # then a hold of up to THROW_HOLD_MAX while MMB charges, then THROW_FOLLOW ticks after the release. ThorClipPlayer
    # plays throw_windup from the press, across the INTRO into the hold (it keeps coiling for THROW_COIL ticks), then
    # throw_hold looped from the windup's last pose, then throw_release from whatever pose the hold reached.
    # Wind-up: the weight goes back onto the right foot, the hips and shoulders turn away, the hammer rises behind
    # the head on a bent arm, the free arm points at the target.
    coil = key(STANCE_H, hip=(0.6, -2.4, 1.2), tor=(-2, 26, 0), rft=(1.0, 0, -3.5), lft=(-0.5, 0, 3.5), rfr=0.0,
               ra=(70, 24, 16.5), rp=(0.6, 0.2, 0.6), hm=(120, 40), hf=(0, 90), la=(-10, -6, 18.5), lp=(-1, -0.5, 0.2))
    wind = key(STANCE_H, hip=(0.8, -3.0, 1.8), tor=(-7, 46, 0), rft=(1.0, 0, -4.0), lft=(-0.5, 0, 4.0), lfr=6.0,
               ra=(112, 38, 16.5), rp=(0.6, 0.6, 0.6), hm=(160, 56), hf=(0, 90), la=(-22, 16, 19.0), lp=(-1, -0.5, 0.2))
    out.append(C.Clip("throw_windup", THROW_COIL, [(0, STANCE_H, "easeInOutSine"), (4, coil, "easeOutSine"),
                                                  (THROW_COIL, wind, "easeOutCubic")],
                      beats={"gather": 4, "settle": THROW_COIL}, fp=C.FpPlan(hammer=True, contact=None)))
    # Hold: the coiled body strains (a slow sway and a fine tremble), the hammer quivering behind the head.
    strain = key(wind, hip=(0.8, -3.3, 1.9), tor=(-8, 48, 0), ra=(113, 38.5, 16.0))
    out.append(loop("throw_hold", 20, [(0, wind, "easeInOutSine"), (10, strain, "easeInOutSine"), (20, wind, "easeInOutSine")],
                    True, C.FpPlan(hammer=True, contact=None), noise=lambda t, it: tremble(t, it, 0.8)))
    out[-1].fp_from_clip = "throw_windup"
    # Release (THROW_FOLLOW = 8 ticks): the left leg drives, the hips open, the arm whips over the shoulder and lets go
    # at tick 2 (the hammer leaves at the release), follows through across the body, then the weight settles.
    whip = key(STANCE_H, hip=(-0.2, -3.4, -0.8), tor=(4, 6, 0), rft=(1.0, 0, -3.0), lft=(-0.5, 0, 5.0), ra=(38, 20, 19.5),
               rp=(1, -0.2, 0.3), la=(-50, -30, 17))
    rel = key(STANCE_H, hip=(-0.5, -4.0, -2.4), tor=(18, -32, 0), rft=(1.0, 0, -2.0), rfr=-18.0, lft=(-0.5, 0, 6.5),
              ra=(-16, 2, 20.0), rp=(1, -0.2, 0.2), la=(-82, -52, 17))
    follow = key(STANCE_H, hip=(-0.4, -3.8, -2.0), tor=(20, -40, 0), rft=(0.5, 0, -1.0), rfr=-10.0, lft=(-0.5, 0, 6.5),
                 ra=(-40, -36, 18.0), rp=(1, -0.4, 0.2), la=(-76, -60, 17))
    settle = key(GUARD, hip=(-0.2, -2.6, -0.6), tor=(8, -6, 0), lft=(-0.5, 0, 4.0))
    out.append(C.Clip("throw_release", 8, [(0, wind, "easeInCubic"), (1, whip, "linear"), (2, rel, "easeOutCubic"),
                                           (4, follow, "easeInOutSine"), (8, settle, "easeInOutSine")],
                      hammer=False, beats={"gather": 0, "settle": 8}, fp=C.FpPlan(hammer=False, contact=None, rest=C.FP_HAMMER_R)))
    out[-1].away = True  # the hammer has left the hand (ThorRenderer draws the flying hammer)
    # Catch: the hammer slams into the open hand; the arm is thrown back, the knees take it.
    reach = key(STANCE_H, hip=(0, -2.0, 0), tor=(2, 6, 0), ra=(8, 14, 19.8), rp=(1, -0.4, 0.2), hm=(4, 30), hf=(0, 90),
                la=(-40, -30, 17))
    out.append(C.Clip("catch", 10, [(0, reach, "easeOutCubic"),
                                    (2, key(reach, hip=(0, -4.2, 1.4), tor=(-9, 26, 0), ra=(52, 26, 13.0), rp=(1, -0.2, 0.6),
                                            hm=(70, 96)), "easeInOutSine"),
                                    (6, key(STANCE_H, hip=(0, -3.0, 0.4), tor=(4, 16, 0)), "easeInOutSine"),
                                    (10, STANCE_H, "easeInOutSine")], beats={"gather": 0, "settle": 6},
                      fp=C.FpPlan(hammer=True, contact=None)))
    # Pull / recall: braced, the free hand out for the hammer (the right arm aims at it: ThorPoser's reach IK).
    brace = key(IDLE, hip=(0, -3.2, 0.6), tor=(-6, 10, 0), rft=(1.0, 0, -2.0), lft=(-1.0, 0, 3.5), ra=(4, 10, 20.0),
                rp=(1, -0.6, 0.2), rw=(-20, 0, 0), la=(-70, -60, 18.5))
    out.append(loop("pull", 20, [(0, brace, "easeInOutSine"), (10, key(brace, tor=(-7, 11, 0), ra=(4, 12, 20.1)), "easeInOutSine"),
                                 (20, brace, "easeInOutSine")], False, C.FpPlan(hammer=False, contact=None),
                    noise=lambda t, it: tremble(t, it, 0.5), owner="pull"))
    out[-1].away = True
    # Flight: the hammer arm out ahead, nearly still; the wrist nudges the spin (the spin itself is the hammer's).
    legs_h = {"rleg": (-12, 0, 4), "rshin": (18, 0, 0), "lleg": (-4, 0, -4), "lshin": (10, 0, 0)}
    hover = key(C.BASE, tor=(2, 0, 0), ra=(26, 56, 19.8), rp=(1, -0.4, 0.4), hm=(26, 64), hf=(0, 0), la=(-60, -62, 19.0), legs=legs_h)
    out.append(loop("flight_hover", 40, [(0, hover, "easeInOutSine"),
                                         (20, key(hover, legs={"rleg": (-4, 0, 4), "rshin": (10, 0, 0), "lleg": (-12, 0, -4),
                                                               "lshin": (18, 0, 0)}), "easeInOutSine"),
                                         (40, hover, "easeInOutSine")], True, C.FpPlan(hammer=True, contact=None),
                    noise=wrist_nudge(10)))
    legs_c = {"rleg": (6, 0, 2), "rshin": (8, 0, 0), "lleg": (2, 0, -2), "lshin": (12, 0, 0)}
    cruise = key(C.BASE, tor=(0, 0, 0), ra=(6, 70, 20.2), rp=(1, 0, 0.3), hm=(4, 74), hf=(90, 0), la=(-20, -96, 19.6), legs=legs_c)
    out.append(loop("flight_cruise", 20, [(0, cruise, "easeInOutSine"), (10, key(cruise, legs={"rleg": (3, 0, 2), "rshin": (11, 0, 0),
                                                                                               "lleg": (5, 0, -2), "lshin": (9, 0, 0)}),
                                           "easeInOutSine"), (20, cruise, "easeInOutSine")], True, C.FpPlan(hammer=True, contact=None),
                    noise=wrist_nudge(7)))
    legs_s = {"rleg": (4, 0, 1), "rshin": (4, 0, 0), "lleg": (4, 0, -1), "lshin": (4, 0, 0)}
    sonic = key(C.BASE, tor=(0, 0, 0), ra=(2, 84, 20.3), rp=(1, 0, 0.2), hm=(0, 86), hf=(90, 0), la=(-10, -100, 19.8), legs=legs_s)
    out.append(loop("flight_sonic", 20, [(0, sonic, "linear"), (20, sonic, "linear")], True, C.FpPlan(hammer=True, contact=None),
                    noise=wrist_nudge(5)))
    return out


def wrist_nudge(every):
    """A short flick of the hammer wrist every `every` ticks (the hand pushing the spin on)."""
    def f(t, it):
        ph = (t % every) / float(every)
        k = math.sin(math.pi * ph / 0.3) if ph < 0.3 else 0.0
        it = dict(it)
        add = dict(it.get("add") or {})
        add["right_hand"] = (6.0 * k, 0, 3.0 * k)
        it["add"] = add
        return it
    return f


WIDEN_FROM = 2.5  # px of squat: past it the feet step wider (the belly goes down between the knees)
WIDEN_RATE = 1.4  # px of width per px of squat, each foot
WIDEN_MAX = 5.5


def widen(clip):
    """A heavy, big-bellied body squats wide: every key deeper than WIDEN_FROM puts the feet further apart
    (the steps between keys come from the foot arcs); flight keys (free legs) are left alone."""
    keys = []
    for t, it, e in clip.keys:
        if not it.get("legs"):
            w = min(WIDEN_MAX, max(0.0, WIDEN_RATE * (-float(it["hip"][1]) - WIDEN_FROM)))
            if w > 0.0:
                it = dict(it)
                it["rft"] = tuple(np.asarray(it["rft"], float) + np.array([w, 0.0, 0.0]))
                it["lft"] = tuple(np.asarray(it["lft"], float) - np.array([w, 0.0, 0.0]))
        keys.append((t, it, e))
    clip.keys = keys
    return clip


def all_clips():
    return [widen(c) for c in base_clips() + power_clips() + hammer_clips() + fist_clips()]


if __name__ == "__main__":
    import make_thor_clips_run
    make_thor_clips_run.main(all_clips, TABLE)
