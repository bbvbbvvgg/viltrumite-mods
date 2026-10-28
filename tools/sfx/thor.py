#!/usr/bin/env python3
"""Thor (God of War Ragnarok) sound kit, stage R5: every Thor cue as original, deterministic synthesis.

No recording and no download: seeded numpy noise, oscillators, modal (inharmonic) metal partials, a
synthetic room and saturation. Own work of the repository owner (Notion AI), CC0 1.0. The Openverse API
timed out on 2026-10-28, so no CC0 recording was layered; a later pass may replace single layers with
recordings found through the sound-design skill without changing the event ids.

Design rule (user playtest: "everything must feel heavy and powerful"): long low anticipation, a short hard
contact, a long tail. Big events are split into layers that the game plays together (ThorSounds / ThorCue):

  thunder        = thunder_crack + thunder_impact + thunder_rumble (long roll, wide range)
  hammer impact  = hammer_metal  + hammer_earth   + hammer_bass    (uru ring, ground, sub drop)

Every cue is mono 44.1 kHz, DC-free, faded (loops are circular and seamless by construction: every partial
completes whole cycles in the loop, all filters are circular FFT filters, no fades), set to its class
100 ms momentary max target (BS.1770 K-weighting, the meter of tools/sfx/reinhard_strikes.py) under the
class true-peak ceiling, and packaged through the sound-design helper (Vorbis q5, .ogg.b64).

    python3 tools/sfx/thor.py                    # synthesize and package every cue
    python3 tools/sfx/thor.py --only hammer_metal_1
    python3 tools/sfx/thor.py --check            # re-synthesize and compare with the committed assets
    python3 tools/sfx/thor.py --no-package --out /tmp/thor   # WAV only
    python3 tools/sfx/thor.py --list             # the file -> event table (sounds.json is written by hand)

Measured numbers only: the sandbox that wrote this has no audio playback, so the cues were NOT auditioned.
"""
import argparse
import math
import os
import subprocess
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reinhard_strikes as meter  # noqa: E402  (shared K-weighted meter, limiter and WAV writer)

ROOT = meter.ROOT
HELPER = meter.HELPER
SR = meter.SR
ASSETS = os.path.join(ROOT, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore", "sounds", "thor")
SEED = 20261028

# class -> (100 ms momentary max target LUFS, true-peak ceiling dBTP, max limiting dB)
CLASSES = {
    "impact": (-8.0, -1.0, 3.0),
    "thunder": (-7.0, -1.0, 3.0),
    "rumble": (-14.0, -1.0, 1.0),
    "swing": (-13.0, -1.0, 1.0),
    "windup": (-17.0, -1.5, 1.0),
    "tail": (-19.0, -1.5, 1.0),
    "loop": (-18.0, -2.0, 0.0),
    "electric": (-12.0, -1.0, 2.0),
    "riser": (-14.0, -1.0, 1.0),
    "body": (-10.0, -1.0, 2.0),
    "ui": (-18.0, -2.0, 0.0),
}


# ---------------------------------------------------------------- primitives

def n_of(seconds):
    return int(round(seconds * SR))


def tgrid(seconds):
    return np.arange(n_of(seconds)) / SR


def butter_mag(freq, lo=None, hi=None, order=2):
    mag = np.ones_like(freq)
    f = np.maximum(freq, 1e-6)
    if lo:
        mag /= np.sqrt(1 + (lo / f) ** (2 * order))
    if hi:
        mag /= np.sqrt(1 + (f / hi) ** (2 * order))
    return mag


def filt(x, lo=None, hi=None, order=2, circular=False):
    """Zero-phase Butterworth magnitude in the FFT domain (circular for loops)."""
    n = len(x)
    m = n if circular else 2 * n
    spec = np.fft.rfft(x, m)
    spec *= butter_mag(np.fft.rfftfreq(m, 1 / SR), lo, hi, order)
    return np.fft.irfft(spec, m)[:n]


def peak(f, gain_db, q, x, circular=False):
    """Resonant boost (Gaussian on a log-frequency axis) in the FFT domain."""
    n = len(x)
    m = n if circular else 2 * n
    spec = np.fft.rfft(x, m)
    freq = np.maximum(np.fft.rfftfreq(m, 1 / SR), 1e-6)
    width = 1.0 / max(q, 0.1)
    spec *= 1 + (10 ** (gain_db / 20) - 1) * np.exp(-0.5 * (np.log2(freq / f) / width) ** 2)
    return np.fft.irfft(spec, m)[:n]


def white(rng, seconds):
    return rng.standard_normal(n_of(seconds))


def brown(rng, seconds, circular=False):
    """Red noise via the FFT (1/f^2 power), high-passed at 15 Hz, unit RMS."""
    n = n_of(seconds)
    m = n if circular else 2 * n
    spec = np.fft.rfft(rng.standard_normal(m))
    freq = np.maximum(np.fft.rfftfreq(m, 1 / SR), 1.0)
    spec /= freq
    spec *= butter_mag(freq, lo=15)
    y = np.fft.irfft(spec, m)[:n]
    return y / (np.std(y) + 1e-12)


def pink(rng, seconds, circular=False):
    n = n_of(seconds)
    m = n if circular else 2 * n
    spec = np.fft.rfft(rng.standard_normal(m))
    freq = np.maximum(np.fft.rfftfreq(m, 1 / SR), 1.0)
    spec /= np.sqrt(freq)
    y = np.fft.irfft(spec, m)[:n]
    return y / (np.std(y) + 1e-12)


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def env(t, attack, decay, hold=0.0):
    """Linear attack, optional hold, exponential decay (time constant decay)."""
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    d = np.exp(-np.maximum(t - attack - hold, 0) / max(decay, 1e-4))
    return a * d


def bell(t, centre, width):
    return np.exp(-0.5 * ((t - centre) / width) ** 2)


def fade(x, fin=0.003, fout=0.05):
    y = x.copy()
    a, b = min(len(y), n_of(fin)), min(len(y), n_of(fout))
    if a:
        y[:a] *= np.linspace(0, 1, a)
    if b:
        y[-b:] *= np.linspace(1, 0, b) ** 2
    return y


def sweep_band(x, centres, q=1.5, frame=1024, hop=256, circular=False):
    """Time-varying band-pass: each STFT frame gets a log-Gaussian band around centres(t) (Hz)."""
    n = len(x)
    win = np.hanning(frame)
    pad = frame
    if circular:
        src = np.concatenate([x[-pad:], x, x[:pad]])
    else:
        src = np.concatenate([np.zeros(pad), x, np.zeros(pad)])
    out = np.zeros(len(src))
    norm_w = np.zeros(len(src))
    freq = np.maximum(np.fft.rfftfreq(frame, 1 / SR), 1e-6)
    width = 1.0 / q
    for start in range(0, len(src) - frame, hop):
        t = (start + frame / 2 - pad) / SR
        if circular:
            t %= n / SR
        fc = max(20.0, float(centres(max(0.0, t))))
        spec = np.fft.rfft(src[start:start + frame] * win)
        spec *= np.exp(-0.5 * (np.log2(freq / fc) / width) ** 2)
        out[start:start + frame] += np.fft.irfft(spec, frame) * win
        norm_w[start:start + frame] += win ** 2
    y = out / np.maximum(norm_w, 1e-6)
    return y[pad:pad + n]


def modal(t, partials):
    """Damped inharmonic partials: (freq, amp, decay, phase) rows."""
    y = np.zeros_like(t)
    for f, a, d, ph in partials:
        y += a * np.sin(2 * np.pi * f * t + ph) * np.exp(-t / d)
    return y


def uru(rng, t, f0, decay=1.0, bright=1.0):
    """Mjolnir's uru head: a thick, inharmonic bar/plate with detuned pairs (slow beating)."""
    ratios = [1.0, 1.506, 2.32, 2.98, 3.86, 4.71, 5.41, 6.93, 8.9, 11.3]
    rows = []
    for i, r in enumerate(ratios):
        amp = (0.85 ** i) * (bright ** min(i, 6))
        d = decay * (0.9 / (1 + 0.55 * i))
        for detune in (-0.0025, 0.0025):
            rows.append((f0 * r * (1 + detune + rng.normal(0, 0.0007)), amp * 0.5, d, rng.uniform(0, 2 * np.pi)))
    return modal(t, rows)


def sub_drop(t, f_start, f_end, tau, decay, drive=2.0):
    """Sine with an exponential pitch drop, tanh-saturated for harmonics a small speaker can show."""
    f = f_end + (f_start - f_end) * np.exp(-t / tau)
    phase = 2 * np.pi * np.cumsum(f) / SR
    y = np.sin(phase) * env(t, 0.004, decay)
    return np.tanh(drive * y) / np.tanh(drive)


def grains(rng, seconds, count, t0, t1, lo, hi, length=(0.004, 0.03), falloff=1.5, circular=False):
    """Short filtered noise bursts at random times (debris, crackle); amplitude falls with time."""
    n = n_of(seconds)
    y = np.zeros(n)
    for _ in range(count):
        at = rng.uniform(t0, t1)
        ln = rng.uniform(*length)
        k = max(8, n_of(ln))
        g = rng.standard_normal(k) * np.exp(-np.linspace(0, 5, k))
        amp = rng.uniform(0.3, 1.0) * (1.0 if circular else math.exp(-falloff * (at - t0) / max(t1 - t0, 1e-3)))
        s = n_of(at)
        if circular:
            idx = (s + np.arange(k)) % n
            y[idx] += amp * g
        elif s < n:
            e = min(n, s + k)
            y[s:e] += amp * g[:e - s]
    return filt(y, lo, hi, order=2, circular=circular)


def impulses(rng, seconds, rate_fn, t0=0.0, t1=None, circular=False):
    """Sparse random impulses with a time-varying rate (crackle, tearing)."""
    n = n_of(seconds)
    t1 = seconds if t1 is None else t1
    y = np.zeros(n)
    t = t0
    while t < t1:
        rate = max(1e-3, rate_fn(t))
        t += rng.exponential(1.0 / rate)
        if t >= t1:
            break
        s = n_of(t) % n if circular else n_of(t)
        if s < n:
            y[s] += rng.choice([-1, 1]) * rng.uniform(0.2, 1.0)
    return y


def buzz(t, f, harmonics=12, tilt=1.0, circular_f=None):
    """Electric hum: odd-heavy harmonic series on a (possibly varying) fundamental."""
    if np.isscalar(f):
        phase = 2 * np.pi * f * t
    else:
        phase = 2 * np.pi * np.cumsum(f) / SR
    y = np.zeros_like(t)
    for h in range(1, harmonics + 1):
        a = (1.0 / h ** tilt) * (1.0 if h % 2 else 0.55)
        y += a * np.sin(h * phase)
    return y


def room(rng, x, length=1.6, wet=0.25, lp=3500, predelay=0.012):
    """A dark synthetic room: exponentially decaying, low-passed noise IR."""
    ir_t = tgrid(length)
    ir = rng.standard_normal(len(ir_t)) * np.exp(-ir_t / (length / 6.9))
    ir = filt(ir, 60, lp)
    ir = np.concatenate([np.zeros(n_of(predelay)), ir])
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-12
    m = len(x) + len(ir)
    y = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)[:len(x)]
    return x + wet * y * (np.std(x) / (np.std(y) + 1e-12))


def loop_f(f, seconds):
    """Nearest frequency with a whole number of cycles in the loop."""
    return max(1, round(f * seconds)) / seconds


def sat(x, drive):
    return np.tanh(drive * x) / np.tanh(drive)


# ---------------------------------------------------------------- recipes
# Each recipe(rng, variant) returns a float array; the length is part of the recipe.

def hammer_windup(rng, v):
    L = 0.85
    t = tgrid(L)
    air = sweep_band(white(rng, L), lambda s: 110 + 280 * (s / L) ** 1.5, q=1.2) * bell(t, 0.55, 0.22)
    creak_src = filt(white(rng, L), 500, 1400)
    stick = impulses(rng, L, lambda s: 35 + 40 * s, 0.1, 0.6)
    creak = peak(820 * (1 + 0.06 * v), 14, 3.0, np.convolve(stick, np.exp(-np.arange(400) / 60.0), "same")) * 0.4 + creak_src * 0.05
    creak *= bell(t, 0.33, 0.13)
    tension = np.sin(2 * np.pi * np.cumsum(38 + 10 * t / L) / SR) * (t / L) ** 2 * np.exp(-np.maximum(t - 0.7, 0) / 0.05)
    clink = uru(rng, np.maximum(t - 0.18, 0), 520 + 40 * v, decay=0.25, bright=0.7) * (t > 0.18) * 0.12
    y = 0.9 * norm(air) + 0.45 * norm(creak) + 0.55 * tension + clink
    return fade(room(rng, y, 0.9, 0.18), 0.02, 0.12)


def hammer_swing(rng, v):
    L = 0.65
    t = tgrid(L)
    pitch = [0.92, 1.0, 1.1][v % 3]
    centre = lambda s: pitch * (170 + 700 * bell(s, 0.17, 0.07) + 120 * (s < 0.17))
    body = sweep_band(white(rng, L), centre, q=1.0) * (env(t, 0.13, 0.12) + 0.0)
    push = filt(brown(rng, L), 40, 160) * bell(t, 0.16, 0.06)
    rip = filt(white(rng, L), 2500, 7000) * bell(t, 0.165, 0.025) * 0.25
    y = norm(body) + 0.7 * norm(push) + rip
    return fade(room(rng, y, 0.7, 0.15), 0.01, 0.2)


def hammer_metal(rng, v):
    L = 1.5
    t = tgrid(L)
    f0 = [178.0, 196.0, 211.0][v % 3]
    ring = uru(rng, t, f0, decay=1.1, bright=0.95)
    clank = filt(white(rng, L), 500, 3500) * env(t, 0.0008, 0.035)
    click = filt(white(rng, L), 2500, 9000) * env(t, 0.0003, 0.004)
    thick = np.sin(2 * np.pi * f0 * 0.5 * t) * env(t, 0.002, 0.18)
    y = 0.85 * norm(ring) + 0.9 * norm(clank) + 0.5 * norm(click) + 0.5 * thick
    return fade(room(rng, sat(y * 0.9, 1.6), 1.4, 0.22, lp=5000), 0.001, 0.3)


def hammer_earth(rng, v):
    L = 1.7
    t = tgrid(L)
    thud = filt(white(rng, L), 30, 380, order=3) * env(t, 0.002, 0.11)
    knock = np.sin(2 * np.pi * (62 + 8 * v) * t) * env(t, 0.002, 0.13)
    crack = filt(white(rng, L), 900, 4500) * env(t, 0.001, 0.02) * 0.6
    debris = grains(rng, L, 160 + 40 * v, 0.03, 1.3, 250, 3200, length=(0.003, 0.025), falloff=2.2)
    dirt = filt(white(rng, L), 300, 2200) * env(t, 0.03, 0.35) * 0.18
    y = norm(thud) + 0.8 * knock + crack + 0.55 * norm(debris) + dirt
    return fade(room(rng, sat(y, 1.4), 1.2, 0.2, lp=2500), 0.001, 0.35)


def hammer_bass(rng, v):
    L = 2.0
    t = tgrid(L)
    drop = sub_drop(t, 82 - 6 * v, 31, 0.12, 0.75, drive=2.4)
    tail = filt(brown(rng, L), 20, 90, order=3) * env(t, 0.03, 0.7) * 0.5
    y = norm(drop) + tail
    return fade(y, 0.002, 0.4)


def hammer_body(rng, v):
    L = 0.5
    t = tgrid(L)
    thud = filt(white(rng, L), 100, 900) * env(t, 0.001, 0.05)
    meat = np.sin(2 * np.pi * np.cumsum(60 + 45 * np.exp(-t / 0.04)) / SR) * env(t, 0.002, 0.09)
    slap = filt(white(rng, L), 1500, 5500) * env(t, 0.0005, 0.007) * 0.5
    y = norm(thud) + 0.9 * meat + slap
    return fade(room(rng, sat(y, 1.8), 0.5, 0.12), 0.001, 0.15)


def hammer_tail(rng, v):
    L = 2.6
    t = tgrid(L)
    ring = uru(rng, t, [178.0, 205.0][v % 2], decay=2.2, bright=0.55) * env(t, 0.01, 1.3)
    rumble = filt(brown(rng, L), 25, 140, order=3) * bell(t, 0.5, 0.45)
    settle = grains(rng, L, 70, 0.2, 2.2, 400, 3000, length=(0.003, 0.02), falloff=2.5) * 0.6
    y = 0.6 * norm(ring) + 0.8 * norm(rumble) + 0.4 * norm(settle)
    return fade(room(rng, y, 1.8, 0.3), 0.03, 0.8)


def fist_windup(rng, v):
    L = 0.45
    t = tgrid(L)
    air = sweep_band(white(rng, L), lambda s: 150 + 300 * s / L, q=1.2) * bell(t, 0.3, 0.1)
    cloth = filt(white(rng, L), 1800, 6000) * (0.5 + 0.5 * np.sin(2 * np.pi * 23 * t)) * bell(t, 0.18, 0.08) * 0.3
    tense = np.sin(2 * np.pi * 45 * t) * (t / L) ** 2 * 0.4
    y = norm(air) + norm(cloth) * 0.4 + tense
    return fade(y, 0.01, 0.08)


def fist_swing(rng, v):
    L = 0.4
    t = tgrid(L)
    pitch = [0.95, 1.05, 1.15][v % 3]
    body = sweep_band(white(rng, L), lambda s: pitch * (300 + 1100 * bell(s, 0.1, 0.04)), q=1.1) * env(t, 0.08, 0.07)
    push = filt(brown(rng, L), 50, 200) * bell(t, 0.09, 0.035)
    y = norm(body) + 0.5 * norm(push)
    return fade(y, 0.005, 0.12)


def fist_hit(rng, v):
    L = 0.6
    t = tgrid(L)
    thud = filt(white(rng, L), 80, 1200) * env(t, 0.0008, 0.045)
    bass = sub_drop(t, 95 - 8 * v, 45, 0.04, 0.16, drive=2.2)
    slap = filt(white(rng, L), 1200, 6000) * env(t, 0.0004, 0.006)
    crunch = grains(rng, L, 10, 0.0, 0.04, 800, 4000, length=(0.002, 0.006), falloff=0.5) * 0.5
    y = norm(thud) + 0.9 * bass + 0.6 * norm(slap) + 0.3 * norm(crunch)
    return fade(room(rng, sat(y, 2.0), 0.5, 0.12), 0.0005, 0.18)


def thunder_crack(rng, v):
    L = 1.4
    t = tgrid(L)
    n0 = np.zeros(len(t))
    n0[n_of(0.002)] = 1.0
    nwave = filt(n0, 30, 12000) * 40
    tear = impulses(rng, L, lambda s: 900 * math.exp(-s / 0.09) + 40, 0.0, 0.45)
    tear = np.convolve(tear, rng.standard_normal(90) * np.exp(-np.arange(90) / 18.0), "same")
    tear = filt(tear, 350, 11000) * env(t, 0.001, 0.12 + 0.03 * v)
    sizzle = filt(white(rng, L), 2500, 9000) * env(t, 0.005, 0.25) * 0.25
    boom = filt(brown(rng, L), 25, 220) * env(t, 0.004, 0.22)
    y = 0.6 * nwave + norm(tear) + sizzle + 0.8 * norm(boom)
    return fade(room(rng, sat(y, 1.5), 1.6, 0.3, lp=6000), 0.0005, 0.4)


def thunder_impact(rng, v):
    L = 2.2
    t = tgrid(L)
    boom = filt(brown(rng, L), 20, 320, order=3) * env(t, 0.003, 0.45)
    drop = sub_drop(t, 70 - 6 * v, 28, 0.15, 0.9, drive=2.5)
    body = filt(white(rng, L), 180, 1500) * env(t, 0.001, 0.16)
    y = norm(boom) + 0.9 * drop + 0.55 * norm(body)
    return fade(room(rng, sat(y, 1.8), 2.0, 0.3, lp=2200), 0.001, 0.6)


def thunder_rumble(rng, v):
    L = 7.0
    t = tgrid(L)
    rolls = np.zeros(len(t))
    for i in range(9 + 2 * v):
        at = rng.uniform(0.15, 5.2)
        rolls += rng.uniform(0.35, 1.0) * math.exp(-at / 3.0) * bell(t, at, rng.uniform(0.25, 0.8))
    rolls += 0.6 * bell(t, 0.25, 0.25)
    cutoff = lambda s: 70 + 260 * math.exp(-s / 1.6)
    low = sweep_band(brown(rng, L), cutoff, q=0.55, frame=2048, hop=512)
    sub = filt(brown(rng, L), 18, 60, order=3)
    y = (norm(low) + 0.6 * norm(sub)) * rolls
    y *= np.minimum(1, t / 0.12) * np.exp(-np.maximum(t - 4.5, 0) / 1.0)
    return fade(sat(norm(y), 1.3), 0.05, 1.2)


def electric_on(rng, v):
    L = 1.3
    t = tgrid(L)
    f = 40 + 75 * (t / L) ** 1.4
    hum = buzz(t, f, 14, 0.9) * (t / L) ** 1.2
    crackle = impulses(rng, L, lambda s: 20 + 400 * (s / L) ** 2, 0.0, 1.05)
    crackle = filt(np.convolve(crackle, rng.standard_normal(40) * np.exp(-np.arange(40) / 8.0), "same"), 1500, 10000)
    snap = filt(white(rng, L), 600, 9000) * env(np.maximum(t - 1.02, 0), 0.0005, 0.05) * (t >= 1.02)
    thump = sub_drop(np.maximum(t - 1.02, 0), 90, 40, 0.05, 0.2) * (t >= 1.02)
    y = 0.55 * norm(hum) + 0.5 * norm(crackle) + 0.9 * norm(snap) + 0.7 * thump
    return fade(room(rng, y, 1.0, 0.2), 0.02, 0.2)


def electric_off(rng, v):
    L = 0.95
    t = tgrid(L)
    f = 115 - 80 * (t / L) ** 0.8
    hum = buzz(t, f, 12, 1.0) * np.exp(-t / 0.35)
    crackle = filt(impulses(rng, L, lambda s: 300 * math.exp(-s / 0.2) + 5), 1500, 9000)
    y = 0.7 * norm(hum) + 0.4 * norm(crackle)
    return fade(y, 0.003, 0.3)


def electric_loop(rng, v):
    L = 3.0
    t = tgrid(L)
    f = loop_f(60.0, L)
    mod = 0.75 + 0.25 * np.sin(2 * np.pi * t / L) * np.sin(2 * np.pi * 3 * t / L + 1.0)
    hum = buzz(t, f, 16, 0.85) * mod
    hiss = filt(white(rng, L), 3000, 9000, circular=True) * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * t / L) ** 2)
    crackle = impulses(rng, L, lambda s: 35 + 25 * math.sin(2 * math.pi * 2 * s / L) ** 2, circular=True)
    crackle = filt(np.real(np.fft.ifft(np.fft.fft(crackle) * np.fft.fft(np.pad(rng.standard_normal(48) * np.exp(-np.arange(48) / 9.0), (0, len(t) - 48))))), 1200, 10000, circular=True)
    pops = grains(rng, L, 9, 0.0, L, 400, 5000, length=(0.01, 0.04), circular=True)
    y = 0.6 * norm(hum) + 0.12 * norm(hiss) + 0.45 * norm(crackle) + 0.3 * norm(pops)
    return y


def electric_zap(rng, v):
    L = 0.6
    t = tgrid(L)
    burst = impulses(rng, L, lambda s: 2500 * math.exp(-s / 0.03) + 20, 0.0, 0.25)
    burst = np.convolve(burst, rng.standard_normal(60) * np.exp(-np.arange(60) / 10.0), "same")
    burst = filt(burst, 500, 11000) * env(t, 0.0005, 0.07)
    f = 120 * (1 + 0.08 * np.sin(2 * np.pi * 37 * t)) * (1 + 0.1 * v)
    arc = buzz(t, f, 18, 0.7) * env(t, 0.002, 0.09)
    snap = filt(white(rng, L), 1200, 10000) * env(t, 0.0003, 0.005)
    y = norm(burst) + 0.5 * norm(arc) + 0.8 * norm(snap)
    return fade(room(rng, y, 0.6, 0.15, lp=7000), 0.0005, 0.2)


def electric_tail(rng, v):
    L = 1.5
    t = tgrid(L)
    crackle = filt(impulses(rng, L, lambda s: 250 * math.exp(-s / 0.4) + 3), 1500, 9000)
    hum = buzz(t, 60.0, 10, 1.0) * np.exp(-t / 0.5) * (0.7 + 0.3 * np.sin(2 * np.pi * 5 * t))
    y = 0.6 * norm(crackle) + 0.5 * norm(hum)
    return fade(y, 0.01, 0.5)


def electric_wave(rng, v):
    L = 1.9
    t = tgrid(L)
    out = sweep_band(white(rng, L), lambda s: 2200 * math.exp(-s / 0.35) + 220, q=1.0) * env(t, 0.01, 0.4)
    crackle = filt(impulses(rng, L, lambda s: 1500 * math.exp(-s / 0.25) + 10), 900, 10000)
    boom = sub_drop(t, 75, 32, 0.08, 0.5, 2.2)
    y = norm(out) + 0.6 * norm(crackle) + 0.8 * boom
    return fade(room(rng, sat(y, 1.4), 1.6, 0.25), 0.001, 0.5)


def storm_charge(rng, v):
    L = 2.2
    t = tgrid(L)
    rise = (t / L) ** 1.6
    wind = sweep_band(white(rng, L), lambda s: 220 + 2200 * (s / L) ** 2, q=0.9) * rise
    hum = buzz(t, 45 + 40 * t / L, 12, 0.9) * rise
    crackle = filt(impulses(rng, L, lambda s: 20 + 700 * (s / L) ** 2.5), 1200, 10000)
    y = norm(wind) + 0.5 * norm(hum) + 0.5 * norm(crackle)
    y *= np.exp(-np.maximum(t - 1.9, 0) / 0.08)
    return fade(y, 0.05, 0.1)


def wrath_windup(rng, v):
    # 5 s: covers a WRATH intro of up to 100 ticks (R2a: contact >= 60 ticks); ThorSounds stops it at contact.
    L = 5.0
    t = tgrid(L)
    grow = np.minimum(1.0, (t / 3.4)) ** 1.5
    drone = buzz(t, 33 + 22 * np.minimum(1, t / 3.6), 8, 1.1) * grow
    wind = sweep_band(pink(rng, L), lambda s: 150 + 650 * min(1.0, s / 3.6) ** 2, q=0.7, frame=2048, hop=512)
    wind = filt(wind, None, 2500) * grow * (0.75 + 0.25 * np.sin(2 * np.pi * 0.37 * t) * np.sin(2 * np.pi * 0.9 * t + 1))
    distant = np.zeros(len(t))
    for at in (0.5, 1.5, 2.5, 3.4, 4.2):
        distant += bell(t, at + rng.uniform(-0.15, 0.15), 0.3) * (0.4 + 0.6 * at / 4.2)
    rumble = filt(brown(rng, L), 20, 110, order=3) * distant
    crackle = filt(impulses(rng, L, lambda s: 6 + 110 * min(1.0, s / 3.6) ** 2), 900, 3000)
    y = 0.65 * norm(drone) + 0.6 * norm(wind) + 0.75 * norm(rumble) + 0.25 * norm(crackle)
    # Dark on purpose (a far storm gathering); also keeps the packed asset under the 64 KB GitHub MCP call cap.
    return fade(filt(room(rng, y, 1.8, 0.3, lp=2500), None, 3200, order=4), 0.3, 0.8)


def hammer_spin_loop(rng, v):
    L = 1.0
    t = tgrid(L)
    rev = loop_f(6.0, L)                   # 6 rev/s (spec 5-8); two head passes per revolution
    passes = 0.5 + 0.5 * np.cos(2 * np.pi * 2 * rev * t)
    passes = passes ** 4
    air = sweep_band(white(rng, L), lambda s: 380 + 420 * (0.5 + 0.5 * math.cos(2 * math.pi * 2 * rev * s)), q=1.1,
                     circular=True) * (0.25 + passes)
    drone = np.sin(2 * np.pi * loop_f(55.0, L) * t) * (0.6 + 0.4 * passes) + 0.3 * np.sin(2 * np.pi * loop_f(110.0, L) * t)
    y = norm(air) + 0.35 * drone
    return y


def flight_loop(rng, v):
    L = 4.0
    t = tgrid(L)
    roar = filt(brown(rng, L, circular=True), 30, 700, order=2, circular=True)
    band = filt(white(rng, L), 150, 500, circular=True)
    mod = 0.8 + 0.2 * np.sin(2 * np.pi * t / L) + 0.1 * np.sin(2 * np.pi * 3 * t / L + 0.7)
    crackle = filt(impulses(rng, L, lambda s: 6.0, circular=True), 1500, 8000, circular=True)
    y = (norm(roar) + 0.35 * norm(band)) * mod + 0.12 * norm(crackle)
    return y


def hammer_fly_loop(rng, v):
    L = 1.0
    t = tgrid(L)
    rev = loop_f(8.0, L)
    passes = (0.5 + 0.5 * np.cos(2 * np.pi * rev * t)) ** 3
    air = filt(white(rng, L), 250, 1600, circular=True) * (0.3 + passes)
    hum = np.sin(2 * np.pi * loop_f(70.0, L) * t) * 0.4 + buzz(t, loop_f(60.0, L), 8, 1.2) * 0.12
    y = norm(air) + hum
    return y


def hammer_throw(rng, v):
    L = 0.95
    t = tgrid(L)
    whoosh = sweep_band(white(rng, L), lambda s: 160 + 900 * bell(s, 0.14, 0.08) + 200 * (s > 0.14), q=1.0) * env(t, 0.1, 0.22)
    push = sub_drop(t, 70, 40, 0.05, 0.18, 1.8)
    crackle = filt(impulses(rng, L, lambda s: 600 * math.exp(-s / 0.15)), 1500, 9000) * 0.5
    y = norm(whoosh) + 0.6 * push + 0.4 * norm(crackle)
    return fade(room(rng, y, 0.9, 0.15), 0.005, 0.3)


def hammer_catch(rng, v):
    L = 1.1
    t = tgrid(L)
    ring = uru(rng, t, 160.0, decay=0.45, bright=0.7)
    slap = filt(white(rng, L), 200, 2500) * env(t, 0.0005, 0.03)
    bass = sub_drop(t, 75, 38, 0.06, 0.35, 2.0)
    spark = filt(impulses(rng, L, lambda s: 900 * math.exp(-s / 0.05)), 2000, 10000) * 0.4
    y = 0.6 * norm(ring) + norm(slap) + 0.8 * bass + 0.3 * norm(spark)
    return fade(room(rng, sat(y, 1.6), 0.9, 0.18), 0.0005, 0.3)


def hammer_recall(rng, v):
    L = 1.4
    t = tgrid(L)
    rise = (t / L) ** 1.8
    whoosh = sweep_band(white(rng, L), lambda s: 250 + 1400 * (s / L) ** 1.5, q=1.0) * rise
    hum = buzz(t, 50 + 50 * t / L, 12, 0.9) * rise
    crackle = filt(impulses(rng, L, lambda s: 30 + 500 * (s / L) ** 2), 1500, 10000)
    y = norm(whoosh) + 0.45 * norm(hum) + 0.35 * norm(crackle)
    y *= np.exp(-np.maximum(t - 1.25, 0) / 0.05)
    return fade(y, 0.05, 0.08)


def hammer_draw(rng, v):
    L = 1.1
    t = tgrid(L)
    leather = filt(white(rng, L), 900, 4500) * (0.6 + 0.4 * np.sin(2 * np.pi * 31 * t)) * bell(t, 0.25, 0.12)
    heft = uru(rng, np.maximum(t - 0.55, 0), 240.0, decay=0.35, bright=0.6) * (t >= 0.55)
    thump = sub_drop(np.maximum(t - 0.55, 0), 80, 42, 0.04, 0.22, 2.0) * (t >= 0.55)
    swing = sweep_band(white(rng, L), lambda s: 160 + 400 * bell(s, 0.45, 0.08), q=1.2) * bell(t, 0.45, 0.08)
    y = 0.5 * norm(leather) + 0.5 * norm(heft) + 0.8 * thump + 0.5 * norm(swing)
    return fade(room(rng, y, 0.8, 0.15), 0.01, 0.25)


def hammer_holster(rng, v):
    L = 0.75
    t = tgrid(L)
    clank = uru(rng, np.maximum(t - 0.12, 0), 300.0, decay=0.2, bright=0.6) * (t >= 0.12)
    leather = filt(white(rng, L), 700, 3500) * env(t, 0.01, 0.06)
    thump = sub_drop(np.maximum(t - 0.12, 0), 70, 45, 0.03, 0.14, 1.8) * (t >= 0.12)
    y = 0.6 * norm(clank) + 0.5 * norm(leather) + 0.7 * thump
    return fade(y, 0.005, 0.2)


def lift_strain(rng, v):
    L = 1.7
    t = tgrid(L)
    groan = np.sin(2 * np.pi * np.cumsum(44 + 3 * np.sin(2 * np.pi * 4.5 * t)) / SR) * bell(t, 0.8, 0.45)
    stick = impulses(rng, L, lambda s: 28 + 12 * math.sin(7 * s), 0.1, 1.5)
    creak = peak(640, 16, 3.5, np.convolve(stick, np.exp(-np.arange(500) / 80.0), "same")) * bell(t, 0.8, 0.45)
    crumble = grains(rng, L, 40, 0.1, 1.5, 300, 2500, falloff=0.3) * 0.4
    y = 0.8 * norm(groan) + 0.5 * norm(creak) + 0.3 * norm(crumble)
    return fade(room(rng, y, 1.0, 0.2), 0.05, 0.3)


def refuse(rng, v):
    L = 0.35
    t = tgrid(L)
    knock = filt(white(rng, L), 120, 900) * env(t, 0.001, 0.03)
    tone = sub_drop(t, 95, 62, 0.04, 0.09, 1.5)
    y = 0.7 * norm(knock) + tone
    return fade(y, 0.001, 0.1)


def dash(rng, v):
    L = 0.6
    t = tgrid(L)
    whoosh = sweep_band(white(rng, L), lambda s: 250 + 1400 * bell(s, 0.12, 0.05) + 150 * (s > 0.12), q=1.0) * env(t, 0.06, 0.14)
    crackle = filt(impulses(rng, L, lambda s: 900 * math.exp(-s / 0.1)), 1500, 10000) * 0.5
    push = sub_drop(t, 80, 45, 0.04, 0.15, 1.8)
    y = norm(whoosh) + 0.4 * norm(crackle) + 0.5 * push
    return fade(y, 0.003, 0.2)


def pull(rng, v):
    L = 1.3
    t = tgrid(L)
    whoosh = sweep_band(white(rng, L), lambda s: 200 + 1300 * bell(s, 0.35, 0.2), q=0.9) * env(t, 0.2, 0.4)
    wind = filt(brown(rng, L), 60, 500) * bell(t, 0.5, 0.3)
    crackle = filt(impulses(rng, L, lambda s: 400 * math.exp(-s / 0.4)), 1500, 9000)
    y = norm(whoosh) + 0.6 * norm(wind) + 0.35 * norm(crackle)
    return fade(room(rng, y, 1.0, 0.2), 0.01, 0.35)


def grab(rng, v):
    L = 0.5
    t = tgrid(L)
    cloth = filt(white(rng, L), 1200, 6000) * env(t, 0.005, 0.05)
    thud = filt(white(rng, L), 80, 600) * env(t, 0.002, 0.06)
    bass = sub_drop(t, 80, 50, 0.03, 0.1, 1.8)
    y = 0.5 * norm(cloth) + norm(thud) + 0.6 * bass
    return fade(y, 0.002, 0.15)


def bone_crunch(rng, v):
    L = 0.75
    t = tgrid(L)
    clicks = grains(rng, L, 70, 0.0, 0.22, 900, 6500, length=(0.001, 0.006), falloff=1.2)
    thud = filt(white(rng, L), 80, 700) * env(t, 0.001, 0.06)
    snap = filt(white(rng, L), 1500, 8000) * env(t, 0.0003, 0.004)
    y = norm(clicks) + 0.8 * norm(thud) + 0.7 * norm(snap)
    return fade(room(rng, sat(y, 1.5), 0.5, 0.1), 0.0005, 0.2)


def gore_squelch(rng, v):
    L = 0.85
    t = tgrid(L)
    src = filt(white(rng, L), 200, 3000) * (0.4 + 0.6 * np.abs(np.sin(2 * np.pi * (9 + 6 * t) * t)))
    wet = sweep_band(src, lambda s: 1300 - 800 * s / L, q=2.5) * env(t, 0.01, 0.25)
    pops = grains(rng, L, 18, 0.02, 0.6, 400, 2500, length=(0.008, 0.03), falloff=1.0)
    thump = sub_drop(t, 70, 45, 0.04, 0.12, 1.5)
    y = norm(wet) + 0.5 * norm(pops) + 0.5 * thump
    return fade(y, 0.003, 0.25)


def heartbeat(rng, v):
    L = 0.95
    t = tgrid(L)
    beat = lambda at, a: a * sub_drop(np.maximum(t - at, 0), 58, 38, 0.03, 0.09, 2.0) * (t >= at)
    y = beat(0.0, 1.0) + beat(0.28, 0.7)
    y += filt(white(rng, L), 40, 200) * (env(t, 0.002, 0.04) + 0.7 * env(np.maximum(t - 0.28, 0), 0.002, 0.04) * (t >= 0.28)) * 0.3
    return fade(y, 0.001, 0.2)


# file name -> (recipe, variant, class); the event is the name without its _<n> suffix.
def _files():
    table = {}
    plan = [
        ("hammer_windup", 2, "windup"), ("hammer_swing", 3, "swing"), ("hammer_metal", 3, "impact"),
        ("hammer_earth", 2, "impact"), ("hammer_bass", 2, "impact"), ("hammer_body", 2, "body"),
        ("hammer_tail", 2, "tail"), ("fist_windup", 1, "windup"), ("fist_swing", 3, "swing"), ("fist_hit", 3, "body"),
        ("thunder_crack", 3, "thunder"), ("thunder_impact", 2, "impact"), ("thunder_rumble", 2, "rumble"),
        ("electric_on", 1, "electric"), ("electric_off", 1, "electric"), ("electric_loop", 1, "loop"),
        ("electric_zap", 3, "electric"), ("electric_tail", 1, "tail"), ("electric_wave", 1, "impact"),
        ("storm_charge", 1, "riser"), ("wrath_windup", 1, "riser"), ("hammer_spin_loop", 1, "loop"),
        ("flight_loop", 1, "loop"), ("hammer_fly_loop", 1, "loop"), ("hammer_throw", 1, "swing"),
        ("hammer_catch", 1, "body"), ("hammer_recall", 1, "riser"), ("hammer_draw", 1, "windup"),
        ("hammer_holster", 1, "windup"), ("lift_strain", 1, "windup"), ("refuse", 1, "ui"), ("dash", 1, "swing"),
        ("pull", 1, "swing"), ("grab", 1, "body"), ("bone_crunch", 1, "body"), ("gore_squelch", 1, "body"),
        ("heartbeat", 1, "body"),
    ]
    for base, count, cls in plan:
        recipe = globals()[base]
        for v in range(count):
            table[f"{base}_{v + 1}" if count > 1 else base] = (recipe, v, cls)
    return table


FILES = _files()


def event_of(name):
    base = name.rsplit("_", 1)
    return base[0] if len(base) == 2 and base[1].isdigit() and name in FILES and FILES[name][0].__name__ != name else name


def is_loop(name):
    return name.endswith("_loop")


def synth(name):
    recipe, variant, _ = FILES[name]
    seed = SEED + sum(ord(c) * (i + 1) for i, c in enumerate(name))
    x = recipe(np.random.default_rng(seed), variant).astype(np.float64)
    if not is_loop(name):
        x = x - np.mean(x)
    return x


def render(names):
    """Synthesize the requested files plus their class siblings, then level every class together."""
    classes = {FILES[n][2] for n in names}
    members = [n for n in FILES if FILES[n][2] in classes]
    raw = {n: synth(n) for n in members}
    out = {}
    for cls in classes:
        target, ceiling, allowance = CLASSES[cls]
        group = [n for n in members if FILES[n][2] == cls]
        loud = {n: meter.momentary_max(raw[n]) for n in group}
        common = min([target] + [loud[n] + ceiling + allowance - 0.05 - meter.true_peak(raw[n]) for n in group])
        for n in group:
            y = raw[n] * 10 ** ((common - loud[n]) / 20)
            if not is_loop(n):
                y = meter.limit(y, ceiling)
            out[n] = y
    return {n: out[n] for n in names}


def decode_asset(name):
    import base64
    with open(os.path.join(ASSETS, name + ".ogg.b64"), "rb") as stream:
        data = base64.decodebytes(stream.read())
    proc = subprocess.run(["ffmpeg", "-v", "error", "-nostdin", "-i", "-", "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                          input=data, capture_output=True, check=True)
    return np.frombuffer(proc.stdout, dtype=np.float32).astype(np.float64)


def seam(x):
    """Second difference across the loop boundary over the RMS second difference of the body.

    A continuous (circular) loop scores like any other sample, about 0..3; a click scores far higher."""
    d2 = np.diff(x, 2)
    edge = abs(x[1] - 2 * x[0] + x[-1]), abs(x[0] - 2 * x[-1] + x[-2])
    return max(edge) / (np.sqrt(np.mean(d2 ** 2)) + 1e-12)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "mix", "thor"))
    parser.add_argument("--only", action="append", choices=sorted(FILES))
    parser.add_argument("--check", action="store_true", help="re-synthesize and compare with the committed assets")
    parser.add_argument("--no-package", action="store_true", help="write the WAV files only")
    parser.add_argument("--list", action="store_true", help="print file -> event -> class")
    args = parser.parse_args()
    if args.list:
        for name in FILES:
            print(f"{name}\tthor.{event_of(name)}\t{FILES[name][2]}")
        return
    names = args.only or list(FILES)
    os.makedirs(args.out, exist_ok=True)
    failed = False
    mixes = render(names)
    for name in names:
        x = mixes[name]
        if args.check:
            y = decode_asset(name)
            n = min(len(x), len(y))
            corr = float(np.dot(x[:n], y[:n]) / (np.linalg.norm(x[:n]) * np.linalg.norm(y[:n]) + 1e-15))
            ok = abs(len(x) - len(y)) <= 0.03 * SR and corr >= 0.95
            failed |= not ok
            print(f"{name}: {'ok' if ok else 'MISMATCH'} correlation {corr:.4f}, {len(y) / SR:.3f} s")
            continue
        wav = os.path.join(args.out, name + ".wav")
        meter.write_wav(x, wav)
        extra = f", seam {seam(x):.4f}" if is_loop(name) else ""
        print(f"{name}: {len(x) / SR:.3f} s, M100 {meter.momentary_max(x):.1f} LUFS, TP {meter.true_peak(x):.1f} dBTP{extra}")
        if not args.no_package:
            cmd = [sys.executable, HELPER, "package", "--input", wav, "--module", "core", "--name", "thor/" + name,
                   "--event", "thor_" + event_of(name), "--own", "--replace"]
            subprocess.run(cmd, cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
