#!/usr/bin/env python3
"""Godzilla - City Catastrophe: fully original procedural SFX for the kaiju (own synthesis, CC0-1.0).

No samples, no recordings, no vanilla sounds and nothing taken from any film: every cue is synthesised from the
primitives of tools/sfx/chitauri.py (oscillators, shaped noise, resonant rings, formant masks, convolution room) plus
the few helpers below. The roar is a slowed stick-slip "bowed string" voice (a sawtooth with period jitter, a friction
burst on every slip and bow chatter) over a throat growl with vocal fry, coloured by a moving formant mask and thrown
into a city-sized room with discrete tower echoes; the far versions are the same voices low-passed by the air and
drowned in a longer room. Cue sheet, triggers and licence: tools/assets/godzilla_sources.md. Events:
viltrumitecore/src/main/sound-overlays/godzilla.json; registry: worldevent/godzilla/GodzillaSounds; playback:
client/godzilla/GodzillaAudio (own distance curve, speed-of-sound delay).

Usage: python3 tools/sfx/godzilla.py [--check] [--wav DIR] [--only NAME ...]   (same pipeline as chitauri.py)
Loops (LOOPS) are periodic over exactly one loop length: noise is shaped in the spectrum of the whole loop, every tone
and modulation makes whole cycles, pops and booms wrap around and the room is a circular convolution.
"""
import argparse
import base64
import hashlib
import os
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chitauri as C  # noqa: E402
from chitauri import (SR, band, crackle, env, filt, finish, formant, moving_formant, noise, osc, place, plfo,  # noqa: E402
                      pnoise, ptone, reverb, ring, rng, spectral, tanh, thump, tt)

OUT = C.OUT.parent / "godzilla"


# --- helpers -----------------------------------------------------------------------------------

def smooth(u):
    u = np.clip(u, 0.0, 1.0)
    return u * u * (3 - 2 * u)


def gate(t, start, end, fin, fout):
    """1 between start and end with smooth ramps of fin / fout seconds."""
    return smooth((t - start) / max(fin, 1e-4)) * smooth((end - t) / max(fout, 1e-4))


def knots(t, pts):
    """Piecewise curve through (time, value) knots, eased between them."""
    ts = np.array([p[0] for p in pts], dtype=float)
    vs = np.array([p[1] for p in pts], dtype=float)
    i = np.clip(np.searchsorted(ts, t, side="right") - 1, 0, len(ts) - 2)
    u = np.clip((t - ts[i]) / np.maximum(ts[i + 1] - ts[i], 1e-9), 0, 1)
    w = u * u * (3 - 2 * u)
    return vs[i] * (1 - w) + vs[i + 1] * w


def wander(d, seed, rate):
    """Smooth random control signal (~N(0, 1)): cosine-interpolated knots, `rate` per second."""
    n = int(round(d * SR))
    v = rng(seed).standard_normal(int(np.ceil(d * rate)) + 2)
    x = np.arange(n) / SR * rate
    i = np.floor(x).astype(int)
    w = 0.5 - 0.5 * np.cos(np.pi * (x - i))
    return v[i] * (1 - w) + v[i + 1] * w


def echo(x, taps, lp=2500):
    """Discrete reflections off the towers: delayed, darker copies (delay s, gain)."""
    out = x.copy()
    dark = filt(x, 60, lp)
    for delay, gain in taps:
        i = int(delay * SR)
        if i < len(x):
            out[i:] += dark[:len(x) - i] * gain
    return out


def pad(x, extra):
    return np.concatenate([x, np.zeros(int(extra * SR))])


def city(x, seed, size=3.0, mix=0.32, taps=((0.19, 0.32), (0.43, 0.22), (0.83, 0.12)), tone=2600, tail=1.2):
    """The city as a room: tower echoes and a large diffuse tail (the clip grows by `tail` seconds)."""
    return reverb(echo(pad(C.fade(x, 0.0, 0.35), tail), taps), size, seed, mix, tone)


def far(x, seed, lp=850, size=4.5, mix=0.55, tail=2.0):
    """Heard across the city: the air eats the top, the low end rolls, echoes come late and long."""
    y = filt(C.fade(x, 0.0, 0.2), 25, lp, 1.2)
    y = echo(pad(y, tail), ((0.55, 0.42), (1.2, 0.28), (2.0, 0.16)), lp * 0.8)
    return reverb(y, size, seed, mix, lp * 1.6)


def _rubble(d, n, seed, start=0.0, spread=0.4, lo=300, hi=5000, gain=(0.15, 0.6), lens=(0.02, 0.08)):
    """Falling concrete: short band-passed bursts with a little body thump, exponential arrival times."""
    out = np.zeros(int(round(d * SR)))
    r = rng(seed)
    for i in range(n):
        ln = r.uniform(*lens)
        c = tt(ln)
        rock = filt(noise(ln, seed * 100 + i), r.uniform(lo, lo * 2), r.uniform(hi * 0.5, hi)) * np.exp(-c / (ln / 4))
        rock += thump(c, r.uniform(90, 200), 50, ln / 3, 0.01) * 0.4
        place(out, rock * r.uniform(*gain), min(start + r.exponential(spread), start + 3 * spread, d - ln - 0.05))
    return out


def _glass(d, n, seed, start=0.0, spread=0.25):
    """Shattering windows: bright, short inharmonic rings with a tick on top."""
    out = np.zeros(int(round(d * SR)))
    r = rng(seed)
    for i in range(n):
        ln = r.uniform(0.05, 0.22)
        c = tt(ln)
        f = r.uniform(2400, 7800)
        shard = ring(c, [(f, 1.0, 1.0), (f * 1.47, 0.6, 0.7), (f * 2.13, 0.35, 0.5)], ln / 3, seed * 10 + i, 0.01)
        shard += noise(ln, seed * 20 + i) * np.exp(-c / 0.002) * 0.6
        place(out, filt(shard, 1800, 0) * r.uniform(0.1, 0.45), min(start + r.exponential(spread), start + 3 * spread, d - ln - 0.05))
    return out


def _crackrun(d, seed, start, length, f0, f1, rate=260, gain=1.0):
    """A crack running out through the ground: a pop train whose band falls and thins with the distance."""
    out = np.zeros(int(round(d * SR)))
    r = rng(seed)
    n = int(length * rate)
    for i in range(n):
        u = (i / max(n - 1, 1)) ** 0.7
        at = start + u * length + r.uniform(0, 0.01)
        ln = 0.006 + 0.03 * u
        c = tt(ln)
        fc = f0 + (f1 - f0) * u
        pop = filt(noise(ln, seed * 50 + i), fc * 0.5, fc * 1.6) * np.exp(-c / (ln / 3))
        place(out, pop * (1 - 0.8 * u) * r.uniform(0.3, 1.0) * gain, at)
    return out


def _whoosh(d, seed, peak, lo0, hi0, lo1, hi1, width=0.35):
    """A huge limb through the air: noise whose band opens towards the pass and closes after it (bell envelope)."""
    t = tt(d)
    x = noise(d, seed)

    def g(tm, f):
        u = np.exp(-((tm - peak) / (width * 1.4)) ** 2)
        return band(lo0 + (lo1 - lo0) * u, hi0 + (hi1 - hi0) * u, 1.2)(f)
    y = spectral(x, g)
    shape = np.exp(-((t - peak) / width) ** 2) ** 0.8
    rumble = filt(noise(d, seed + 1), 25, 110) * np.exp(-((t - peak) / (width * 1.3)) ** 2)
    return y * shape + rumble * 0.9


# --- the voice ---------------------------------------------------------------------------------

def _bow(t, f, seed, rough=0.25, sharp=8.0, rub=0.75):
    """Slowed stick-slip string: Helmholtz saw with pressure jitter, a friction burst on every slip, bow chatter."""
    d = len(t) / SR
    ph = 2 * np.pi * np.cumsum(f * (1 + 0.012 * wander(d, seed, 55))) / SR
    saw = 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    slip = (0.5 + 0.5 * np.cos(ph)) ** sharp
    fric = filt(noise(d, seed + 1), 700, 8000) * slip * rub
    chatter = 1 + rough * np.clip(wander(d, seed + 2, 38), -2.5, 2.5) * 0.4
    return (saw + fric) * chatter


def _throat(t, f, seed, fry=22.0, depth=0.7):
    """Deep throat growl: low saw gated by vocal-fry pulses, breathy noise riding the pulses."""
    d = len(t) / SR
    ph = 2 * np.pi * np.cumsum(f) / SR
    saw = 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    fr = fry * (1 + 0.15 * wander(d, seed, 4))
    pulses = (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(fr) / SR)) ** 3
    x = saw * (1 - depth + depth * pulses)
    x += filt(noise(d, seed + 1), 60, 900) * (0.4 + 0.6 * pulses) * 0.6
    return filt(x, 30, 1400)


def _voice(d, seed, fk, ak, rough=0.3, sub=0.45, inharm=0.35, throat=0.6, bright=1.0, dark_at=None, break_at=None):
    """The kaiju voice: bowed screech + period-doubled sub + inharmonic ring + throat, through a moving formant mask.

    fk / ak: (time, value) knots of pitch (Hz) and loudness (sub and inharm may be knots too); dark_at: time after which the formants close into the
    low bellow; break_at: (start, end) of a voice break (rougher, a subharmonic jump, a drop-out)."""
    t = tt(d)
    f = knots(t, fk) * (1 + 0.012 * np.sin(2 * np.pi * 5.2 * t) + 0.01 * wander(d, seed + 3, 3))
    a = knots(t, ak)
    sub = knots(t, sub) if isinstance(sub, list) else sub
    inharm = knots(t, inharm) if isinstance(inharm, list) else inharm
    v = _bow(t, f, seed, rough)
    v += sub * _bow(t, f * 0.5, seed + 10, rough * 1.3, 5.0, 0.4)
    v *= 1 + inharm * np.sin(2 * np.pi * np.cumsum(f * 0.73) / SR)
    if break_at is not None:
        b = gate(t, break_at[0], break_at[1], 0.06, 0.12)
        v = v * (1 - 0.55 * b * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 9 * t)))) + b * 0.6 * _bow(t, f * 0.33, seed + 20, 0.8, 4.0, 0.6)
    d_u = (lambda u: smooth((u * d - dark_at) / 0.5)) if dark_at is not None else (lambda u: 0.0)

    def track(u):
        k = d_u(u)
        o = smooth(u * d / 0.6)
        return [(250, 220, 0.5), ((620 + 260 * o) * (1 - 0.35 * k), 480, 1.0), ((1450 + 250 * o) * (1 - 0.25 * k), 650, 0.8 * bright),
                (2700 * (1 - 0.15 * k), 950, 0.45 * bright * (1 - 0.5 * k)), (3900, 1300, 0.18 * bright * (1 - 0.7 * k))]
    v = moving_formant(v, track)
    th = _throat(t, f * 0.25 + 18, seed + 30, 20 + 6 * a) * throat
    breath = filt(noise(d, seed + 40), 300, 4500) * 0.18
    x = (v + th + breath) * a
    return tanh(x / (np.max(np.abs(x)) + 1e-9) * 1.4, 2.0)


ROAR_F = [(0.0, 115), (0.45, 165), (0.9, 290), (2.2, 322), (3.15, 300), (3.55, 175), (4.6, 92), (5.6, 80)]
ROAR_A = [(0.0, 0.0), (0.35, 0.22), (0.8, 1.0), (3.0, 0.95), (3.25, 0.62), (3.45, 0.92), (4.4, 0.72), (4.95, 0.0), (5.6, 0.0)]


# the screech stays tonal (little sub, little ring), the bellow opens up the period-doubled sub and the rasp
ROAR_SUB = [(0.0, 0.5), (0.9, 0.22), (3.1, 0.22), (3.5, 0.65), (5.6, 0.65)]
ROAR_RING = [(0.0, 0.3), (0.9, 0.18), (3.1, 0.18), (3.5, 0.45), (5.6, 0.45)]


def _roar_dry(seed=11):
    return _voice(5.6, seed, ROAR_F, ROAR_A, sub=ROAR_SUB, inharm=ROAR_RING, dark_at=3.25)


def roar():
    """Signature roar (~6.8 s): throat intake, the long rising bowed screech, then the falling bellow; city room."""
    return finish(city(_roar_dry(), 12, 3.2, 0.33), 0.01, 0.6)


def roar_far():
    """The same roar across the city (~7.6 s): no top, the bellow carries, long late echoes off the towers."""
    x = _roar_dry() + filt(noise(5.6, 13), 20, 90) * knots(tt(5.6), ROAR_A) * 0.5
    return finish(far(x, 14, 900, 4.5, 0.55, 2.0), 0.03, 0.8)


def growl():
    """Short threat growl (~2.4 s): throat and fry swelling, a faint screech overtone, a snort of breath."""
    d = 2.4
    t = tt(d)
    a = knots(t, [(0, 0), (0.35, 0.8), (1.2, 1.0), (1.9, 0.6), (2.4, 0)])
    f = knots(t, [(0, 44), (0.5, 56), (1.4, 52), (2.4, 42)])
    th = _throat(t, f, 31, 23.0, 0.75)
    over = formant(_bow(t, f * 3.02, 32, 0.4, 6.0, 0.5), [(650, 400, 1.0), (1500, 600, 0.5)]) * 0.22
    breath = filt(noise(d, 33), 250, 3000) * env(t, 0.02, 0.4) * 0.3
    x = (th + over) * a + breath
    return finish(city(tanh(x, 1.8), 34, 1.6, 0.22, ((0.15, 0.2),), 2400, 0.6), 0.01, 0.3)


def hurt():
    """A heavy hit lands (~2.6 s): a short, high, ragged bellow that cracks and falls."""
    fk = [(0.0, 210), (0.14, 345), (0.7, 305), (1.25, 185), (1.7, 130)]
    ak = [(0.0, 0.0), (0.07, 1.0), (0.8, 0.85), (1.45, 0.4), (1.75, 0.0)]
    x = _voice(1.8, 41, fk, ak, rough=0.55, sub=0.3, inharm=0.45, throat=0.45, bright=1.15, dark_at=1.0)
    return finish(city(x, 42, 2.0, 0.28, ((0.17, 0.25), (0.41, 0.14)), 2600, 0.8), 0.005, 0.4)


def death():
    """The last roar (~8 s): rises, cracks into a rattling subharmonic, falls away into a long, low exhale."""
    d = 6.4
    fk = [(0.0, 140), (0.5, 250), (1.6, 330), (2.8, 300), (3.4, 245), (4.6, 140), (5.6, 85), (6.4, 70)]
    ak = [(0.0, 0.0), (0.45, 0.8), (1.0, 1.0), (3.0, 0.9), (3.45, 0.45), (3.75, 0.8), (5.0, 0.42), (6.0, 0.0), (6.4, 0.0)]
    x = _voice(d, 51, fk, ak, rough=0.45, sub=0.55, dark_at=3.6, break_at=(3.2, 4.4))
    t = tt(d)
    exhale = filt(noise(d, 52), 90, 900) * gate(t, 4.6, 6.3, 0.5, 1.2) * 0.35
    rattle = _throat(t, 38 + 0 * t, 53, 14.0, 0.9) * gate(t, 4.4, 6.2, 0.3, 1.0) * 0.35
    return finish(city(x + exhale + rattle, 54, 4.0, 0.4, tail=1.8), 0.01, 1.0)


def retreat():
    """The final retreat (~7.4 s): a long, lower, mournful call as it turns away, half lost to the city."""
    d = 5.4
    fk = [(0.0, 130), (0.7, 225), (2.6, 262), (3.9, 205), (5.0, 118), (5.4, 105)]
    ak = [(0.0, 0.0), (0.6, 0.85), (2.8, 1.0), (4.2, 0.7), (5.1, 0.0), (5.4, 0.0)]
    x = _voice(d, 61, fk, ak, rough=0.25, sub=0.5, inharm=0.25, bright=0.8, dark_at=3.4)
    y = filt(x, 25, 2600, 1.2)
    return finish(city(y, 62, 4.2, 0.45, ((0.33, 0.35), (0.8, 0.24), (1.5, 0.14)), 2000, 2.0), 0.02, 1.2)


def rise():
    """Getting up (~3.6 s): a strained, swelling growl, debris sliding off the back, the planted foot at 1.9 s."""
    d = 3.6
    t = tt(d)
    a = knots(t, [(0, 0), (0.4, 0.7), (1.0, 0.45), (1.7, 1.0), (2.4, 0.7), (3.3, 0)])
    f = knots(t, [(0, 50), (0.8, 58), (1.7, 66), (3.0, 52)])
    th = _throat(t, f, 71, 26.0, 0.8) * a
    strain = formant(_bow(t, f * 3.1, 72, 0.6, 5.0, 0.6), [(560, 380, 1.0), (1300, 600, 0.5)]) * a * 0.28
    slide = _rubble(d, 140, 73, 0.25, 1.1, 350, 5500, (0.05, 0.3))
    creak = filt(noise(d, 74), 70, 320) * (0.5 + 0.5 * np.sin(2 * np.pi * 1.3 * t)) ** 2 * gate(t, 0.2, 2.6, 0.4, 0.6) * 0.5
    foot = np.zeros_like(t)
    place(foot, _impact(1.6, 75, 0.8), 1.9)
    x = tanh(th + strain, 1.6) + slide + creak + foot
    return finish(city(x, 76, 2.0, 0.25, ((0.18, 0.2),), 2400, 0.6), 0.01, 0.4)


# --- body --------------------------------------------------------------------------------------

def _impact(d, seed, scale=1.0, f0=70, f1=26, decay=0.35):
    """A footfall: the deep ground thump, the concrete crush under the sole, a short rumble."""
    t = tt(d)
    boom = thump(t, f0, f1, decay * scale, 0.05)
    crush = np.zeros_like(t)
    for i, at in enumerate((0.0, 0.025, 0.06)):
        c = tt(0.2)
        place(crush, tanh(filt(noise(0.2, seed * 10 + i), 160, 4800) * env(c, 0.0008, 0.05) * 2.2, 2.0) * (1 - 0.25 * i), at)
    thud = filt(noise(d, seed + 1), 20, 130) * env(t, 0.004, 0.35 * scale)
    return boom * 1.25 + crush * 0.55 * scale + thud * 1.1


def step():
    """A footfall up close (~2 s): the crushing slam, the ground thud and the debris pattering back."""
    d = 2.0
    x = _impact(d, 81) + _rubble(d, 30, 82, 0.08, 0.35, 400, 6000, (0.04, 0.22))
    return finish(reverb(tanh(x, 1.6), 1.3, 83, 0.2, 2200), 0.0005, 0.3)


def step_far():
    """A footfall far away (~2.6 s): a felt sub boom rolling in, the city rattling faintly."""
    d = 2.6
    t = tt(d)
    boom = thump(t, 46, 21, 0.65, 0.09) * smooth(t / 0.02)
    roll = filt(noise(d, 91), 14, 85) * env(t, 0.03, 0.75) * 1.3
    rattle = filt(noise(d, 92), 900, 4000) * env(t, 0.05, 0.25) * 0.05
    x = filt(boom * 1.2 + roll, 0, 320) + rattle
    return finish(reverb(x, 2.2, 93, 0.4, 900), 0.02, 0.5)


def stomp():
    """The deliberate stomp (~3.2 s): a bigger slam, the shockwave crack running out, air pushed away, debris."""
    d = 3.2
    t = tt(d)
    x = _impact(d, 101, 1.5, 62, 20, 0.5) * 1.2
    x += _crackrun(d, 102, 0.03, 1.4, 5200, 700, 300, 0.9)
    x += filt(noise(d, 103), 30, 200) * env(t, 0.02, 0.9) * 1.1
    x += _whoosh(d, 104, 0.25, 300, 1500, 120, 700, 0.3) * 0.35
    x += _rubble(d, 70, 105, 0.15, 0.6, 300, 5500, (0.05, 0.3))
    return finish(city(tanh(x, 1.8), 106, 2.2, 0.26, ((0.2, 0.22), (0.47, 0.12)), 2400, 0.4), 0.0005, 0.5)


def tail_swish():
    """The tail swinging through the air (~1.9 s): a slow, enormous whoosh with a low pressure wave."""
    return finish(reverb(_whoosh(1.9, 111, 0.95, 70, 500, 180, 1900, 0.42), 1.0, 112, 0.2, 2500), 0.05, 0.3)


def claw_swish():
    """A claw swipe (~1.2 s): a faster, brighter whoosh."""
    return finish(reverb(_whoosh(1.2, 121, 0.5, 200, 1100, 450, 3600, 0.2), 0.8, 122, 0.18, 3000), 0.03, 0.25)


def tail_hit():
    """The tail lands on a building (~2.9 s): boom, the concrete cracking, a crunch cascade, glass and rubble."""
    d = 2.4
    t = tt(d)
    boom = thump(t, 82, 30, 0.42, 0.04)
    crack = filt(noise(d, 131), 900, 9000) * env(t, 0.0005, 0.03)
    crunch = np.zeros_like(t)
    for i, at in enumerate((0.0, 0.05, 0.12, 0.22)):
        c = tt(0.3)
        place(crunch, tanh(filt(noise(0.3, 132 + i), 150, 5200) * env(c, 0.001, 0.07) * 2, 2.0) * (1 - i * 0.18), at)
    x = boom * 1.2 + crack * 0.8 + crunch * 0.9 + _rubble(d, 80, 133, 0.12, 0.55) + _glass(d, 26, 134, 0.05, 0.2)
    x += filt(noise(d, 135), 22, 160) * env(t, 0.01, 0.7)
    return finish(city(tanh(x, 1.8), 136, 1.8, 0.28, ((0.17, 0.2),), 2800, 0.5), 0.0005, 0.4)


def claw_hit():
    """Claws through a facade (~2.3 s): the hit, steel tearing with a falling screech, concrete crumbling."""
    d = 1.9
    t = tt(d)
    boom = thump(t, 110, 40, 0.3, 0.03)
    tear_f = knots(t, [(0, 980), (0.5, 620), (1.1, 430)])
    tear = _bow(t, tear_f, 141, 0.9, 3.0, 1.2) + _bow(t, tear_f * 1.37, 142, 0.9, 3.0, 1.0) * 0.6
    tear = formant(tear, [(1100, 500, 1.0), (2300, 800, 0.6), (3700, 900, 0.3)]) * gate(t, 0.02, 1.0, 0.03, 0.4) * 0.35
    crack = filt(noise(d, 143), 800, 9000) * env(t, 0.0005, 0.025)
    x = boom * 1.1 + tear + crack * 0.7 + _rubble(d, 60, 144, 0.08, 0.45) + _glass(d, 14, 145, 0.03, 0.15)
    return finish(city(tanh(x, 1.7), 146, 1.6, 0.25, ((0.16, 0.18),), 2800, 0.4), 0.0005, 0.35)


def smash():
    """The body ploughing through a building (~3.6 s): impact, fracture, glass, steel groan, a long rubble cascade."""
    d = 3.2
    t = tt(d)
    boom = thump(t, 74, 27, 0.55, 0.05)
    crack = filt(noise(d, 151), 500, 9000) * env(t, 0.0005, 0.04)
    crunch = np.zeros_like(t)
    for i, at in enumerate((0.0, 0.06, 0.15, 0.27, 0.42)):
        c = tt(0.35)
        place(crunch, tanh(filt(noise(0.35, 152 + i), 140, 5000) * env(c, 0.001, 0.08) * 2, 2.0) * (1 - i * 0.13), at)
    groan = ring(t, [(71, 1.0, 1.0), (109, 0.8, 0.8), (167, 0.6, 0.6), (251, 0.35, 0.5)], 1.1, 153, 0.006)
    groan *= gate(t, 0.15, 1.8, 0.2, 0.6) * (1 + 0.3 * np.sin(2 * np.pi * 2.3 * t)) * 0.4
    x = boom * 1.2 + crack * 0.7 + crunch + groan + _rubble(d, 150, 154, 0.12, 0.8, 200, 5200, (0.08, 0.5))
    x += _glass(d, 40, 155, 0.04, 0.3) + filt(noise(d, 156), 22, 220) * env(t, 0.04, 1.0) * 1.1
    return finish(city(tanh(x, 1.9), 157, 2.4, 0.3, ((0.2, 0.22), (0.5, 0.12)), 2600, 0.4), 0.0005, 0.6)


def fall():
    """The body hitting the ground (~4.4 s): two huge impacts, the ground roar, a crack run and debris."""
    d = 3.8
    t = tt(d)
    x = np.zeros_like(t)
    place(x, _impact(d, 161, 2.6, 58, 24, 0.45) * 1.3, 0.0)
    place(x, _impact(d, 162, 1.4, 66, 31, 0.4) * 0.8, 0.36)
    x += filt(noise(d, 163), 18, 190) * env(t, 0.02, 1.3) * 1.3
    x += _crackrun(d, 164, 0.05, 1.8, 4200, 500, 240, 0.8)
    x += _rubble(d, 180, 165, 0.1, 1.0, 200, 5000, (0.06, 0.45))
    return finish(city(tanh(x, 2.0), 166, 3.0, 0.32, ((0.24, 0.25), (0.6, 0.14)), 2200, 0.6), 0.0005, 0.8)


def arrive():
    """The arrival (~8 s): the ground trembling closer, the city rattling, then a far roar rolling over the towers."""
    d = 6.4
    t = tt(d)
    tremble = filt(noise(d, 171), 12, 70) * knots(t, [(0, 0), (2.5, 0.8), (4.5, 1.0), (6.4, 0.6)]) * 1.2
    steps = np.zeros_like(t)
    for i, at in enumerate((0.3, 1.5, 2.7)):
        c = tt(1.4)
        place(steps, filt(thump(c, 44, 20, 0.6, 0.08) + filt(noise(1.4, 172 + i), 14, 80) * env(c, 0.03, 0.5), 0, 260) * (0.45 + 0.25 * i), at)
    rattle = crackle(d, 40, 175, 1200, 6000, 0.006) * knots(t, [(0, 0), (1.0, 0.2), (3.0, 0.6), (6.4, 0.3)]) * 0.5
    call = np.zeros_like(t)
    place(call, filt(_voice(3.4, 176, [(0, 120), (0.5, 210), (2.0, 255), (2.7, 150), (3.4, 90)],
                             [(0, 0), (0.45, 0.9), (2.2, 1.0), (2.9, 0.4), (3.4, 0)], sub=0.6, bright=0.7, dark_at=2.2), 25, 900, 1.2), 2.9)
    x = tremble + steps + rattle + call * 0.75
    return finish(far(x, 177, 1400, 4.5, 0.45, 1.6), 0.4, 1.0)


# --- atomic breath -----------------------------------------------------------------------------

# The cue starts when the brace ends (GodzillaRules.CHARGE_BRACE = 16 ticks): ignition at the tail until tick 46
# (1.5 s), the glow runs up the spine until tick 78 (3.1 s), energy gathers in the mouth until tick 100 (4.2 s).
CHARGE_IGNITE_S = (46 - 16) / 20.0
CHARGE_SPINE_S = (78 - 16) / 20.0
CHARGE_S = (100 - 16) / 20.0


def _front(ts):
    """GodzillaRules.glowFront over cue time: 0 (tail tip) .. 1 (head)."""
    out = np.where(ts < CHARGE_IGNITE_S, 0.38 * smooth(ts / CHARGE_IGNITE_S),
                   0.38 + 0.62 * smooth((ts - CHARGE_IGNITE_S) / (CHARGE_SPINE_S - CHARGE_IGNITE_S)))
    return np.where(ts >= CHARGE_SPINE_S, 1.0, out)


def charge():
    """Breath charge (4.2 s): each plate igniting with a zap from tail to head, the hum rising, the throat crackling."""
    d = CHARGE_S
    t = tt(d)
    zaps = np.zeros_like(t)
    grid = np.linspace(0, d, 4000)
    fr = _front(grid)
    r = rng(181)
    plates = 34
    for i in range(plates):
        s = (i + 0.5) / plates
        at = grid[np.argmax(fr >= s)] if np.any(fr >= s) else d - 0.2
        ln = 0.09
        c = tt(ln)
        pitch = 900 + 2400 * s
        z = ring(c, [(pitch, 1.0, 1.0), (pitch * 1.51, 0.5, 0.6), (pitch * 2.37, 0.3, 0.4)], 0.025, 182 + i, 0.01)
        z += filt(noise(ln, 300 + i), 2500, 11000) * np.exp(-c / 0.012) * 1.2
        z += thump(c, 160 + 80 * s, 60, 0.03, 0.006) * 0.8
        place(zaps, z * (0.35 + 0.4 * s) * r.uniform(0.75, 1.0), at + r.uniform(-0.02, 0.02))
    lvl = smooth(t / d) * 0.8 + 0.2
    hf = 52 * (110 / 52) ** (t / d)
    hum = (osc(hf, "saw") * 0.5 + osc(hf * 2.003) * 0.4 + osc(hf * 3.01) * 0.2) * lvl
    hum = filt(hum, 30, 1400) * (1 + 0.15 * np.sin(2 * np.pi * np.cumsum(4 + 10 * t / d) / SR))
    mouth = gate(t, CHARGE_SPINE_S - 0.5, d + 1, 0.9, 0.1)
    wf = 600 * (2700 / 600) ** smooth((t - (CHARGE_SPINE_S - 0.6)) / (d - CHARGE_SPINE_S + 0.6))
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(6 + 20 * smooth(t / d)) / SR)
    whine = (osc(wf) + 0.3 * osc(wf * 1.5)) * trem * mouth * 0.32
    throat = crackle(d, 140, 183, 1500, 9500, 0.003) * knots(t, [(0, 0.1), (CHARGE_SPINE_S - 0.4, 0.25), (d, 1.0)]) * 1.4
    inhale = filt(noise(d, 184), 200, 2200) * mouth * smooth((t - CHARGE_SPINE_S + 0.5) / 1.5) * 0.35
    x = zaps + hum * 0.55 + whine + throat + inhale
    x = tanh(x, 1.4)
    return finish(reverb(x, 1.2, 185, 0.18, 4000), 0.01, 0.006)


def beam_start():
    """Breath release (~2.6 s): the blast leaving the mouth, an energy tear sweeping down, the beam roar blooming."""
    d = 2.2
    t = tt(d)
    whoomp = thump(t, 95, 30, 0.5, 0.035)
    tear = noise(d, 191)

    def g(tm, f):
        u = smooth(tm / 0.45)
        return band(3500 - 3200 * u, 13000 - 9500 * u, 1.3)(f)
    tear = spectral(tear, g) * env(t, 0.002, 0.35) * 1.6
    core = (osc(np.full_like(t, 110.0), "saw") * 0.5 + osc(np.full_like(t, 221.0)) * 0.35 + filt(noise(d, 192), 80, 4500) * 0.9)
    core *= env(t, 0.05, 0.9) * 0.9
    x = whoomp * 1.3 + tear + core + crackle(d, 160, 193, 2000, 11000) * env(t, 0.005, 0.5) * 1.4
    return finish(reverb(tanh(x, 1.9), 2.0, 194, 0.3, 3200), 0.0005, 0.4)


def beam_end():
    """The beam dying (~2.8 s): a falling whine, the energy sputtering out, the plates ticking as they cool."""
    d = 2.8
    t = tt(d)
    wf = 2400 * (230 / 2400) ** smooth(t / 1.4)
    whine = (osc(wf) + 0.35 * osc(wf * 1.5)) * gate(t, 0, 1.5, 0.01, 0.6) * 0.4
    rate = 22 * (1 - smooth(t / 1.6)) + 3
    stutter = (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * np.cumsum(rate) / SR))) * 0.7 + 0.3
    core = filt(noise(d, 201), 80, 4000) * stutter * gate(t, 0, 1.2, 0.005, 0.6)
    drone = osc(110 * (0.5 + 0.5 * np.exp(-t / 0.6)), "saw") * env(t, 0.005, 0.6) * 0.4
    ticks = np.zeros_like(t)
    r = rng(202)
    for i in range(16):
        c = tt(0.06)
        fc = r.uniform(1900, 4800)
        place(ticks, ring(c, [(fc, 1.0, 1.0), (fc * 1.6, 0.4, 0.6)], 0.012, 210 + i) * r.uniform(0.15, 0.35), r.uniform(0.7, 2.6))
    hiss = filt(noise(d, 203), 2500, 9000) * gate(t, 0.4, 2.8, 0.3, 1.4) * 0.12
    x = whine + core * 0.8 + drone + ticks + hiss
    return finish(reverb(tanh(x, 1.5), 1.5, 204, 0.25, 3500), 0.002, 0.4)


# --- loops (periodic over exactly one loop length) ---------------------------------------------

def pfilt(x, lo=0, hi=0, soft=1.5):
    """Circular (periodic) band filter in the spectrum of the whole loop."""
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(np.fft.rfft(x) * band(lo, hi, soft)(f), len(x))


def pplace(out, x, at):
    """Place with wrap-around past the loop end."""
    idx = (int(at * SR) + np.arange(len(x))) % len(out)
    np.add.at(out, idx, x)
    return out


def pcrackle(d, rate, seed, lo, hi, length=0.004):
    n = int(round(d * SR))
    out = np.zeros(n)
    r = rng(seed)
    pop = tt(length)
    for i in range(max(1, int(d * rate))):
        p = noise(length, seed * 1000 + i) * np.exp(-pop / (length / 4)) * r.uniform(0.2, 1.0)
        pplace(out, p, r.uniform(0, d))
    return pfilt(out, lo, hi)


def preverb(x, size, seed, mix=0.3, tone=4000):
    """Room as a circular convolution (the tail of the loop end rings into its start)."""
    n = len(x)
    m = int(size * SR)
    ti = np.arange(m) / SR
    ir = rng(seed).standard_normal(m) * np.exp(-ti / (size / 5))
    ir = filt(ir, 150, tone)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    irp = np.zeros(n)
    np.add.at(irp, np.arange(m) % n, ir)
    y = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(irp), n)
    return x * (1 - mix) + y * mix * 2.0


LOOP_S = 3.0


def beam_loop():
    """The beam (3 s, seamless): a dense roaring energy core, an electric buzz, a shimmering whine, sizzle."""
    d = LOOP_S
    t = tt(d)
    core = pnoise(d, 221, 70, 5200) * (1 + 0.15 * plfo(t, 7, d) + 0.08 * plfo(t, 13, d, 1.0))
    core = pfilt(core, 60, 0) * 0.8 + pnoise(d, 222, 300, 600) * 0.5 + pnoise(d, 223, 1500, 2300) * 0.3
    buzz = ptone(t, 110, d, 0.4, shape="saw") + ptone(t, 220, d, 0.25) + ptone(t, 330, d, 0.12)
    buzz = pfilt(buzz, 40, 2500) * (0.8 + 0.2 * plfo(t, 3, d, 0.4))
    whine = ptone(t, 1650, d, 0.12, 0.008, 6) + ptone(t, 2475, d, 0.05, 0.006, 5)
    sub = ptone(t, 41, d, 0.45)
    sizzle = pcrackle(d, 110, 224, 2500, 11000) * 1.3
    x = core + buzz + whine + sub + sizzle
    x = np.tanh(preverb(x, 0.9, 225, 0.2, 4500) * 1.1)
    return x / (np.max(np.abs(x)) + 1e-9)


def beam_impact():
    """Where the beam hits (3 s, seamless): roaring fire, melting stone hissing, pops, deep stone cracks."""
    d = LOOP_S
    t = tt(d)
    mod = 0.65 + 0.2 * plfo(t, 0.67, d) + 0.1 * plfo(t, 1.33, d, 1.1) + 0.05 * plfo(t, 2.67, d, 2.3)
    fire = pnoise(d, 231, 35, 1300) * mod
    hiss = pnoise(d, 232, 3000, 12000) * 0.22 * (0.7 + 0.3 * plfo(t, 2.0, d, 0.5))
    pops = pcrackle(d, 70, 233, 800, 6500, 0.006) * 1.6 + pcrackle(d, 9, 234, 150, 1200, 0.02) * 1.8
    booms = np.zeros_like(t)
    for i, at in enumerate((0.4, 1.9)):
        c = tt(0.8)
        pplace(booms, (thump(c, 72, 28, 0.25, 0.03) + filt(noise(0.8, 235 + i), 100, 3000) * env(c, 0.001, 0.05) * 0.5) * (0.8 - 0.2 * i), at)
    x = fire + hiss + pops + booms * 0.8
    x = np.tanh(preverb(x, 1.0, 237, 0.25, 3000) * 1.1)
    return x / (np.max(np.abs(x)) + 1e-9)


CUES = {
    "roar": roar, "roar_far": roar_far, "growl": growl, "step": step, "step_far": step_far, "stomp": stomp,
    "tail_swish": tail_swish, "tail_hit": tail_hit, "claw_swish": claw_swish, "claw_hit": claw_hit, "charge": charge,
    "beam_start": beam_start, "beam_loop": beam_loop, "beam_impact": beam_impact, "beam_end": beam_end, "smash": smash,
    "hurt": hurt, "fall": fall, "rise": rise, "death": death, "arrive": arrive, "retreat": retreat,
}
LOOPS = {"beam_loop", "beam_impact"}
# Loudest 100 ms RMS (dBFS), soft-limited under chitauri.CEILING_DB. The kaiju is the loudest thing in the city:
# signature cues -8..-9.5, impacts -9.5..-11, voices -10..-12, motion -13..-14, steps -13..-15, loops -14..-15
# (the loops and the far layers are scaled again by GodzillaAudio with distance).
LOUDNESS = {
    "roar": -8.0, "roar_far": -10.0, "growl": -12.0, "step": -12.5, "step_far": -14.0, "stomp": -9.5,
    "tail_swish": -13.0, "tail_hit": -10.0, "claw_swish": -13.5, "claw_hit": -10.5, "charge": -11.0,
    "beam_start": -8.5, "beam_loop": -13.5, "beam_impact": -14.5, "beam_end": -11.5, "smash": -9.5,
    "hurt": -10.0, "fall": -8.5, "rise": -11.5, "death": -8.5, "arrive": -9.5, "retreat": -10.0,
}
# Vorbis quality per cue (mono) and the encoding rate: the far and sub-heavy cues carry nothing above a few kHz, so
# they go out at 22.05 / 32 kHz. A cue still over the 60 KB push limit is written as <name>.ogg.b64.part0..N (joined by
# the Gradle decodeBinaryAssets task).
QUALITY = {"roar": 1, "roar_far": 0, "death": 1, "arrive": 0, "retreat": 0, "fall": 1, "smash": 1, "charge": 1,
           "rise": 1, "stomp": 1, "beam_loop": 1, "beam_impact": 1, "tail_hit": 1, "beam_end": 1, "hurt": 1}
RATE = {"roar_far": 22050, "step_far": 22050, "arrive": 22050, "retreat": 32000, "death": 32000, "roar": 32000,
        "fall": 32000, "rise": 32000, "growl": 32000, "hurt": 32000}


def encode(wav, ogg, q, rate):
    import subprocess
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-map_metadata", "-1", "-fflags", "+bitexact",
                    "-flags:a", "+bitexact", "-c:a", "libvorbis", "-q:a", str(q), "-ac", "1", "-ar", str(rate), str(ogg)],
                   check=True)


PART = 56000


def _committed(name):
    """The committed base64 text of a cue (single file or joined parts), or None."""
    one = OUT / (name + ".ogg.b64")
    if one.exists():
        return one.read_text()
    parts = sorted(OUT.glob(name + ".ogg.b64.part*"), key=lambda q: int(q.name.rsplit("part", 1)[1]))
    return "".join(q.read_text() for q in parts) if parts else None


def _write(name, data):
    for old in list(OUT.glob(name + ".ogg.b64")) + list(OUT.glob(name + ".ogg.b64.part*")):
        old.unlink()
    OUT.mkdir(parents=True, exist_ok=True)
    if len(data) < PART:
        (OUT / (name + ".ogg.b64")).write_bytes(data)
        return 1
    lines = data.splitlines(keepends=True)
    chunks, cur = [], b""
    for ln in lines:
        if len(cur) + len(ln) > PART:
            chunks.append(cur)
            cur = b""
        cur += ln
    chunks.append(cur)
    for i, c in enumerate(chunks):
        (OUT / ("%s.ogg.b64.part%d" % (name, i))).write_bytes(c)
    return len(chunks)


def build(tmp, wav_dir=None, only=None):
    res = {}
    for name, fn in CUES.items():
        if only and name not in only:
            continue
        loop = name in LOOPS
        x = C.master(fn(), LOUDNESS[name], loop)
        assert np.isfinite(x).all() and np.max(np.abs(x)) > 0.05, name
        if loop:
            assert abs(x[0] - x[-1]) <= np.max(np.abs(np.diff(x))) * 1.02 + 1e-4, name
        w = Path(tmp) / (name + ".wav")
        o = Path(tmp) / (name + ".ogg")
        C.write_wav(w, x)
        if wav_dir:
            os.makedirs(wav_dir, exist_ok=True)
            C.write_wav(Path(wav_dir) / (name + ".wav"), x)
        encode(w, o, QUALITY.get(name, 2), RATE.get(name, SR))
        res[name] = o
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--wav")
    ap.add_argument("--only", nargs="*")
    a = ap.parse_args()
    with tempfile.TemporaryDirectory() as tmp:
        built = build(tmp, a.wav, set(a.only) if a.only else None)
        bad = 0
        for name, ogg in built.items():
            if a.check:
                text = _committed(name)
                if text is None:
                    print("missing", name)
                    bad += 1
                    continue
                committed = Path(tmp) / (name + ".committed.ogg")
                committed.write_bytes(base64.b64decode(text))
                if hashlib.sha256(C.decoded_pcm(committed)).digest() != hashlib.sha256(C.decoded_pcm(ogg)).digest():
                    print("differs", name)
                    bad += 1
            else:
                data = base64.encodebytes(ogg.read_bytes())
                n = _write(name, data)
                print("%-12s %6d%s" % (name, len(data), " (%d parts)" % n if n > 1 else ""))
        print("%d cues %s" % (len(built), "checked" if a.check else "written"))
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()