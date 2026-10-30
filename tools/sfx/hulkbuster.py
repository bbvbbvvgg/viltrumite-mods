#!/usr/bin/env python3
"""Hulkbuster Mark 48 sound kit: CC0 recordings carry every cue, own synthesis supports them (fix spec item 3).

Each of the 56 files (31 events) is a layer stack of CC0 1.0 Freesound recordings (SOURCES: steel clangs, metal
impacts, heavy ground thuds, hydraulics and air releases, servo motors, afterburners / flame bursts, scrapes,
debris, sparks) placed attack / impact / tail on the file's contact time, over the deterministic synthesis of the
H6 kit (rework spec 2026-10-28 §10), which stays as a support layer (sub weight, servo whine, pod whistle,
reactor hum): seeded numpy noise, oscillators, modal steel partials, stick-slip friction; own work, CC0 1.0.
Contact times and lengths are the H6 ones, so no server pre-roll and no Java changed.

Loops (servo hum, thrusters, elbow charge, damage sparks) stay seamless by construction: a spectral freeze of a
recording over exactly the loop length (seeded random phase, no fades, modulation periodic in the loop) plus the
circular synthesis; the spark loop folds the recording's overlap onto its head so the crackle stays. The client
plays them (HulkbusterLoops) and stops them explicitly.

Every cue is mono 44.1 kHz, DC-free, high-passed at 38 Hz, soft-clipped on the bus (impact / heavy / step / body),
set to its class 100 ms momentary max (BS.1770 K-weighting, the meter of tools/sfx/reinhard_strikes.py) under the
class true-peak ceiling, variants of one class corrected after limiting so they stay matched, and encoded like the
sound-design helper's `package` (Vorbis q5, .ogg.b64) under sounds/ironman/hulkbuster/.

Provenance: Openverse (the helper's search / download backend) was unreachable, so every source's CC0 1.0 licence
was checked on its Freesound landing page and the credits (tools/assets/ironman_sources.md, CREDITS.md) were
composed from that page, not from an Openverse `attribution` field. The audio is each page's hq MP3 preview,
fetched into ~/.cache/viltrumite-sfx/work/raw/ (never committed). The helper's `package` needs an Openverse
--source-id, so this script encodes with the helper's exact ffmpeg settings itself (see package()).

    python3 tools/sfx/hulkbuster.py --fetch         # download the missing Freesound previews (raw cache)
    python3 tools/sfx/hulkbuster.py                 # mix and package every cue
    python3 tools/sfx/hulkbuster.py --only step_1   # one file (its class is mixed too, for level matching)
    python3 tools/sfx/hulkbuster.py --check         # re-mix and compare with the committed assets
    python3 tools/sfx/hulkbuster.py --no-package --out /tmp/hb
    python3 tools/sfx/hulkbuster.py --list          # file -> event -> class
    python3 tools/sfx/hulkbuster.py --sources       # file -> recordings (Freesound ids)
    python3 tools/sfx/hulkbuster.py --sounds-json   # rewrite the ironman_hulkbuster_* entries of sounds.json

A full run takes about 1.5 minutes; --only per class splits it. Measured numbers only: the sandbox that wrote
this has no audio playback, so the cues were NOT auditioned.
"""
import argparse
import json
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
ASSETS = os.path.join(ROOT, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore", "sounds", "ironman", "hulkbuster")
SOUNDS_JSON = os.path.join(ROOT, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore", "sounds.json")
SEED = 48_2026_10

# class -> (100 ms momentary max target LUFS, true-peak ceiling dBTP, max limiting dB)
CLASSES = {
    "impact": (-8.0, -1.0, 3.0),
    "heavy": (-6.5, -1.0, 3.0),
    "step": (-12.0, -1.0, 2.0),
    "swing": (-13.0, -1.0, 1.0),
    "screech": (-14.0, -1.0, 1.0),
    "riser": (-14.0, -1.0, 1.0),
    "body": (-11.0, -1.0, 2.0),
    "loop": (-18.0, -2.0, 0.0),
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




# ---------------------------------------------------------------- Mark 48 building blocks

STEEL = [1.0, 1.59, 2.14, 2.30, 2.65, 2.92, 3.50, 4.15, 4.62, 5.40, 6.21, 7.33]


def plate(rng, t, f0, decay=0.6, bright=1.0, n=12):
    """A thick riveted steel plate: dense inharmonic partials, high ones die first."""
    rows = []
    for i, r in enumerate(STEEL[:n]):
        amp = (0.8 ** i) * (bright ** min(i, 8))
        d = decay * (1.0 / (1 + 0.45 * i))
        rows.append((f0 * r * (1 + rng.normal(0, 0.004)), amp, d, rng.uniform(0, 2 * np.pi)))
    return modal(t, rows)


def servo(t, f_start, f_end, wobble=0.03, grit=0.3, rng=None):
    """Electric servo whine with gear grit: a gliding fundamental, odd harmonics, AM from the gear teeth."""
    glide = f_end + (f_start - f_end) * np.exp(-t / max(t[-1] * 0.35, 1e-3))
    glide = glide * (1 + wobble * np.sin(2 * np.pi * 7.3 * t))
    phase = 2 * np.pi * np.cumsum(glide) / SR
    y = np.sin(phase) + 0.45 * np.sin(2 * phase) + 0.3 * np.sin(3 * phase) + 0.12 * np.sin(5 * phase)
    teeth = 0.65 + 0.35 * np.sign(np.sin(phase * 0.125))
    y = y * teeth
    if rng is not None and grit:
        y += grit * filt(rng.standard_normal(len(t)), 1800, 6000) * (0.5 + 0.5 * np.abs(np.sin(phase * 0.25)))
    return y


def hydraulic(rng, t, at, length, lo=1600, hi=7500, burst=True):
    """Hydraulic hiss: band noise with a pressure burst at the start of the stroke."""
    shape = np.clip((t - at) / 0.01, 0, 1) * np.exp(-np.maximum(t - at, 0) / max(length, 1e-3))
    if burst:
        shape += 1.5 * np.clip((t - at) / 0.002, 0, 1) * np.exp(-np.maximum(t - at, 0) / 0.02)
    return filt(rng.standard_normal(len(t)), lo, hi) * shape


def thud(rng, t, at, f=48.0, decay=0.18, drive=2.2):
    """Heavy foot / body thump: a falling sub sine and a low noise knock."""
    tt = np.maximum(t - at, 0)
    gate = (t >= at)
    knock = filt(rng.standard_normal(len(t)), 30, 300, order=3) * env(tt, 0.002, decay * 0.5) * gate
    return (sub_drop(tt, f * 1.8, f, 0.03, decay, drive) * gate) + 0.6 * norm(knock)


def rattle(rng, t, at, count=14, spread=0.12, f=900.0):
    """Loose plates settling: a burst of short metal ticks after the contact."""
    y = np.zeros_like(t)
    for _ in range(count):
        s = at + abs(rng.normal(0, spread))
        tt = np.maximum(t - s, 0)
        ff = f * rng.uniform(0.7, 1.6)
        y += rng.uniform(0.2, 1.0) * np.sin(2 * np.pi * ff * tt) * np.exp(-tt / rng.uniform(0.008, 0.03)) * (t >= s)
    return y


def stickslip(rng, t, rate, f_res, q=6.0, jitter=0.15):
    """Bowed / dragged metal: stick-slip impulses at a gliding rate excite a resonant body."""
    n = len(t)
    y = np.zeros(n)
    pos = 0.0
    while True:
        i = int(pos * SR)
        if i >= n:
            break
        y[i] += rng.uniform(0.6, 1.0)
        r = max(20.0, float(rate(t[i])) * (1 + rng.normal(0, jitter)))
        pos += 1.0 / r
    out = np.zeros(n)
    for k, fr in enumerate(f_res):
        out += peak(fr, 22 - 4 * k, q, y) - y
    return out


def whoosh(rng, t, centre, width, f_lo=180, f_hi=1100, pitch=1.0):
    body = sweep_band(rng.standard_normal(len(t)), lambda s: pitch * (f_lo + (f_hi - f_lo) * bell(s, centre, width)), q=1.0)
    return body * bell(t, centre, width * 1.4)


def jet(rng, seconds, circular=False, lo=40, hi=1400):
    roar = filt(brown(rng, seconds, circular=circular), lo, 600, order=2, circular=circular)
    hiss = filt(white(rng, seconds), 900, hi * 4, circular=circular)
    return norm(roar) + 0.35 * norm(hiss)


def strike(rng, v, f0, L, wind=0.32, contact=0.42, bass=40.0, tail=0.9, crunch=0.0, piston=0, whoosh_gain=0.8):
    """Full strike chain: servo wind-up, whoosh, steel plate, sub bass, metal tail (+ optional crunch, piston)."""
    t = tgrid(L)
    pre = servo(t, 260 + 30 * v, 520 + 40 * v, rng=rng) * bell(t, wind * 0.6, wind * 0.35) * 0.35
    air = whoosh(rng, t, contact - 0.05, 0.05, pitch=1.0 + 0.06 * v) * whoosh_gain
    tc = np.maximum(t - contact, 0)
    gate = t >= contact
    metal = plate(rng, tc, f0 * (1 + 0.04 * v), decay=0.7, bright=0.95) * gate
    clank = filt(rng.standard_normal(len(t)), 400, 4200) * env(tc, 0.0008, 0.03) * gate
    click = filt(rng.standard_normal(len(t)), 3000, 10000) * env(tc, 0.0003, 0.004) * gate
    sub = sub_drop(tc, bass * 2.2, bass, 0.025, 0.28, 2.6) * gate
    ring = plate(rng, tc, f0 * 0.5, decay=tail, bright=0.6, n=8) * gate * 0.5
    debris = grains(rng, L, 70 + 20 * v, contact + 0.01, min(L, contact + 0.7), 300, 4000, falloff=2.5)
    y = pre + norm(air) + 0.9 * norm(metal) + 0.9 * norm(clank) + 0.45 * norm(click) + 1.0 * sub + 0.6 * norm(ring) + 0.35 * norm(debris)
    if crunch:
        crack = grains(rng, L, 60, contact, contact + 0.08, 600, 5000, length=(0.001, 0.006), falloff=1.0)
        y += crunch * norm(crack) + crunch * 0.6 * filt(rng.standard_normal(len(t)), 120, 900) * env(tc, 0.001, 0.05) * gate
    for k in range(piston):
        s = contact - 0.06 + 0.035 * k
        y += 0.5 * norm(hydraulic(rng, t, s, 0.03, 900, 5000)) * (0.6 ** k)
    return fade(room(rng, sat(y * 0.8, 1.7), 1.3, 0.22, lp=4500), 0.002, 0.3)


# ---------------------------------------------------------------- recipes (rng, variant) -> float array

def step(rng, v):
    L = 0.9
    t = tgrid(L)
    hyd = hydraulic(rng, t, 0.0, 0.09, 1500 + 200 * v, 6500) * 0.55
    hit = thud(rng, t, 0.07, f=44 + 4 * v, decay=0.22)
    grit = grains(rng, L, 90, 0.075, 0.5, 200, 2600, length=(0.002, 0.02), falloff=3.0)
    plates = rattle(rng, t, 0.09, count=10 + 2 * v, spread=0.08, f=820 + 60 * v)
    servo_tail = servo(t, 340, 180, rng=rng) * bell(t, 0.25, 0.08) * 0.15
    y = norm(hyd) * 0.5 + 1.0 * hit + 0.4 * norm(grit) + 0.35 * norm(plates) + servo_tail
    return fade(room(rng, sat(y, 1.5), 0.8, 0.15, lp=3000), 0.001, 0.2)


def screech(rng, v):
    L = [0.9, 1.1, 0.7, 1.3, 1.0][v]
    t = tgrid(L)
    base = [190.0, 150.0, 240.0, 120.0, 175.0][v]
    glide = [1.6, 0.6, 2.0, 0.8, 1.3][v]
    rate = lambda s: base * (1 + (glide - 1) * (s / L))
    res = [r * (1 + 0.05 * v) for r in (720.0, 1460.0, 2380.0, 3700.0)]
    body = stickslip(rng, t, rate, res, q=8.0 - v * 0.6)
    swell = np.sin(np.pi * np.clip(t / L, 0, 1)) ** 0.7 * (0.75 + 0.25 * np.sin(2 * np.pi * (3 + v) * t))
    grind = filt(rng.standard_normal(len(t)), 2000, 9000) * swell * 0.08
    y = norm(body) * swell + grind
    return fade(room(rng, y, 0.9, 0.2, lp=6000), 0.02, 0.18)


def swing(rng, v):
    L = 0.75
    t = tgrid(L)
    pitch = [0.9, 1.0, 1.12][v]
    whine = servo(t, 300 * pitch, 720 * pitch, rng=rng) * bell(t, 0.18, 0.1) * 0.45
    air = whoosh(rng, t, 0.36, 0.07, 150, 800, pitch)
    push = filt(brown(rng, L), 35, 140) * bell(t, 0.36, 0.07)
    hiss = hydraulic(rng, t, 0.05, 0.08, 1800, 6000) * 0.25
    y = whine + norm(air) + 0.8 * norm(push) + norm(hiss) * 0.3
    return fade(room(rng, y, 0.7, 0.16), 0.01, 0.2)


def punch(rng, v):
    return strike(rng, v, [150.0, 165.0, 178.0][v], 1.4, contact=0.30, bass=42.0)


def hammer(rng, v):
    # Right-arm jackhammer stroke on a target: piston burst, steel, bone-deep thump.
    return strike(rng, v, [210.0, 232.0, 199.0][v], 1.0, wind=0.12, contact=0.10, bass=46.0, tail=0.5, piston=3, whoosh_gain=0.3)


def jackhammer(rng, v):
    # Face punch in the grab chain (up to five): piston + crunch, quick tail so five can follow each other.
    return strike(rng, v, [190.0, 205.0, 220.0][v], 0.85, wind=0.1, contact=0.08, bass=44.0, tail=0.35, crunch=0.8, piston=4,
                  whoosh_gain=0.25)


def finish_punch(rng, v):
    L = 2.4
    t = tgrid(L)
    y = strike(rng, 0, 138.0, L, wind=0.5, contact=0.62, bass=34.0, tail=1.6, crunch=1.0, piston=5)
    boom = filt(brown(rng, L), 25, 220) * env(np.maximum(t - 0.62, 0), 0.004, 0.6) * (t >= 0.62)
    return fade(y + 0.7 * norm(boom), 0.002, 0.5)


def charged_punch(rng, v):
    L = 2.2
    t = tgrid(L)
    y = strike(rng, 0, 128.0, L, wind=0.2, contact=0.16, bass=32.0, tail=1.5, piston=2)
    thrust = jet(rng, L) * env(t, 0.02, 0.25) * 0.5
    ring = filt(brown(rng, L), 20, 180) * env(np.maximum(t - 0.16, 0), 0.01, 0.7) * (t >= 0.16)
    return fade(y + 0.5 * norm(thrust) + 0.6 * norm(ring), 0.002, 0.5)


def charge(rng, v):
    # Elbow thruster spool before the charged punch (up to ~1.5 s), ends on a lock clunk.
    L = 1.6
    t = tgrid(L)
    spool = servo(t, 120, 900, wobble=0.01, grit=0.2, rng=rng) * (t / L) ** 1.4 * 0.5
    roar = jet(rng, L) * (t / L) ** 2
    lock = thud(rng, t, 1.45, f=70, decay=0.08, drive=1.6) * 0.5
    y = spool + 0.8 * norm(roar) + lock
    return fade(room(rng, y, 0.8, 0.15), 0.05, 0.08)


def clamp(rng, v):
    # Armour plate closing (suit-up x5, block, cage lock, arm dock): hydraulic push, steel clang, latch.
    L = 0.9
    t = tgrid(L)
    at = 0.12
    hyd = hydraulic(rng, t, 0.0, 0.1, 1200 + 150 * v, 5500) * bell(t, 0.06, 0.05)
    clang = plate(rng, np.maximum(t - at, 0), [260.0, 290.0, 320.0, 240.0][v], decay=0.45, bright=1.0) * (t >= at)
    latch = filt(rng.standard_normal(len(t)), 1500, 8000) * env(np.maximum(t - at - 0.05, 0), 0.0005, 0.006) * (t >= at + 0.05)
    hit = thud(rng, t, at, f=60, decay=0.1, drive=1.6)
    y = 0.6 * norm(hyd) + norm(clang) + 0.6 * norm(latch) + 0.7 * hit
    return fade(room(rng, sat(y, 1.4), 0.7, 0.15, lp=5000), 0.002, 0.2)


def block_hit(rng, v):
    L = 1.0
    t = tgrid(L)
    clang = plate(rng, t, [300.0, 340.0, 275.0][v], decay=0.55, bright=1.05)
    spark = grains(rng, L, 120, 0.0, 0.25, 3000, 11000, length=(0.0005, 0.003), falloff=2.0)
    hit = thud(rng, t, 0.0, f=58, decay=0.12)
    scrape = stickslip(rng, t, lambda s: 300 - 150 * s, [1500.0, 2900.0], q=6) * bell(t, 0.12, 0.06)
    y = norm(clang) + 0.5 * norm(spark) + 0.7 * hit + 0.3 * norm(scrape)
    return fade(room(rng, sat(y, 1.5), 0.9, 0.18, lp=6000), 0.001, 0.25)


def repulsor_charge(rng, v):
    L = 1.3
    t = tgrid(L)
    f = 140 + 700 * (t / L) ** 1.6
    whine = buzz(t, f, 10, 1.1) * (t / L) ** 1.2
    hum = np.sin(2 * np.pi * 60 * t) * 0.3 * (t / L)
    air = sweep_band(white(rng, L), lambda s: 400 + 2500 * (s / L) ** 2, q=1.5) * (t / L) ** 2 * 0.5
    y = norm(whine) * 0.7 + hum + norm(air) * 0.4
    return fade(room(rng, y, 0.8, 0.18), 0.05, 0.05)


def repulsor_beam(rng, v):
    L = 1.8
    t = tgrid(L)
    blast = filt(white(rng, L), 200, 9000) * env(t, 0.002, 0.08)
    beam = buzz(t, 95 + 6 * np.sin(2 * np.pi * 5 * t), 16, 0.9) * env(t, 0.01, 0.7, 0.25)
    roar = jet(rng, L) * env(t, 0.005, 0.45)
    sub = sub_drop(t, 110, 38, 0.05, 0.5, 2.4)
    y = norm(blast) + 0.55 * norm(beam) + 0.7 * norm(roar) + 0.9 * sub
    return fade(room(rng, sat(y, 1.6), 1.3, 0.25, lp=5000), 0.001, 0.4)


def dash(rng, v):
    L = 0.9
    t = tgrid(L)
    burst = jet(rng, L) * env(t, 0.006, 0.18)
    kick = thud(rng, t, 0.0, f=50, decay=0.12)
    skid = stickslip(rng, t, lambda s: 140 + 40 * v, [520.0, 1150.0], q=5) * bell(t, 0.35, 0.15) * 0.4
    grit = grains(rng, L, 160, 0.05, 0.8, 300, 3000, falloff=1.2)
    y = norm(burst) + 0.7 * kick + norm(skid) * 0.4 + 0.3 * norm(grit)
    return fade(room(rng, y, 0.8, 0.16), 0.002, 0.2)


def cough(rng, v):
    # Overheated thrusters sputter (jet jump refused / hover out).
    L = 0.9
    t = tgrid(L)
    y = np.zeros_like(t)
    for k, at in enumerate([0.0, 0.17 + 0.03 * v, 0.38]):
        y += (0.8 ** k) * jet(rng, L) * env(np.maximum(t - at, 0), 0.004, 0.06) * (t >= at)
    clatter = rattle(rng, t, 0.05, 8, 0.2, 700)
    return fade(room(rng, norm(y) + 0.3 * norm(clatter), 0.6, 0.15), 0.002, 0.15)


def land(rng, v):
    L = 1.8
    t = tgrid(L)
    hit = thud(rng, t, 0.0, f=36 + 4 * v, decay=0.4, drive=2.8)
    slam_metal = plate(rng, t, 140 + 15 * v, decay=0.5, bright=0.8)
    debris = grains(rng, L, 260, 0.01, 1.3, 200, 3500, falloff=2.0)
    plates = rattle(rng, t, 0.03, 22, 0.15, 750)
    hyd = hydraulic(rng, t, 0.08, 0.2, 1200, 5000)
    y = 1.1 * hit + 0.5 * norm(slam_metal) + 0.45 * norm(debris) + 0.3 * norm(plates) + 0.35 * norm(hyd)
    return fade(room(rng, sat(y, 1.8), 1.4, 0.22, lp=2800), 0.001, 0.4)


def slam(rng, v):
    # Victim slammed into ground / wall, seismic rift contact, dive impact.
    L = 2.2
    t = tgrid(L)
    hit = thud(rng, t, 0.0, f=32 + 3 * v, decay=0.55, drive=3.0)
    crack = filt(rng.standard_normal(len(t)), 700, 5000) * env(t, 0.001, 0.03)
    debris = grains(rng, L, 380, 0.01, 1.8, 180, 4000, falloff=1.8)
    metal = plate(rng, t, 120 + 10 * v, decay=0.6, bright=0.7) * 0.6
    rumble = filt(brown(rng, L), 20, 160) * env(t, 0.05, 0.8)
    y = 1.1 * hit + 0.7 * norm(crack) + 0.55 * norm(debris) + 0.45 * norm(metal) + 0.6 * norm(rumble)
    return fade(room(rng, sat(y, 1.9), 1.6, 0.25, lp=2600), 0.001, 0.5)


def grab(rng, v):
    L = 0.9
    t = tgrid(L)
    whine = servo(t, 500, 260, rng=rng) * bell(t, 0.1, 0.06) * 0.4
    fingers = rattle(rng, t, 0.16, 12, 0.03, 1100)
    clench = thud(rng, t, 0.16, f=66, decay=0.1, drive=1.5)
    hyd = hydraulic(rng, t, 0.14, 0.1, 1500, 6000) * 0.5
    creak = stickslip(rng, t, lambda s: 90, [480.0, 960.0], q=7) * bell(t, 0.35, 0.12) * 0.3
    y = whine + 0.6 * norm(fingers) + 0.8 * clench + 0.5 * norm(hyd) + 0.3 * norm(creak)
    return fade(room(rng, y, 0.8, 0.15), 0.002, 0.2)


def throw(rng, v):
    L = 1.0
    t = tgrid(L)
    whine = servo(t, 280, 760, rng=rng) * bell(t, 0.22, 0.12) * 0.5
    air = whoosh(rng, t, 0.42, 0.1, 120, 700, 0.9)
    release = hydraulic(rng, t, 0.44, 0.08, 1500, 6000)
    y = whine + norm(air) + 0.5 * norm(release)
    return fade(room(rng, y, 0.9, 0.18), 0.01, 0.25)


def hop(rng, v):
    # Jet jump launch: thruster blast + ground kick.
    L = 1.6
    t = tgrid(L)
    blast = jet(rng, L) * env(t, 0.01, 0.45)
    kick = thud(rng, t, 0.0, f=40, decay=0.25, drive=2.4)
    sweep = sweep_band(white(rng, L), lambda s: 300 + 1500 * math.exp(-s / 0.3), q=1.2) * env(t, 0.01, 0.4)
    y = norm(blast) + kick + 0.4 * norm(sweep)
    return fade(room(rng, sat(y, 1.6), 1.2, 0.2, lp=4000), 0.002, 0.4)


def ult_ignite(rng, v):
    # Jet Ram ignition: reactor overdrive, all thrusters spool, rising scream up to the charge.
    L = 2.6
    t = tgrid(L)
    rise = (t / L) ** 1.5
    reactor = buzz(t, 60 + 180 * rise, 14, 1.0) * rise
    spool = servo(t, 150, 1200, wobble=0.015, grit=0.25, rng=rng) * rise * 0.4
    thrust = jet(rng, L) * rise ** 1.4
    clunks = sum(thud(rng, t, at, f=70, decay=0.06, drive=1.4) * 0.35 for at in (0.2, 0.55, 0.9))
    y = 0.5 * norm(reactor) + spool + norm(thrust) + clunks
    return fade(room(rng, sat(y, 1.4), 1.2, 0.2), 0.05, 0.1)


def ult_impact(rng, v):
    L = 4.5
    t = tgrid(L)
    hit = thud(rng, t, 0.0, f=28, decay=0.9, drive=3.2)
    crack = filt(rng.standard_normal(len(t)), 500, 7000) * env(t, 0.001, 0.05)
    metal = plate(rng, t, 110.0, decay=1.4, bright=0.8)
    blast = filt(brown(rng, L), 20, 400) * env(t, 0.01, 1.4)
    debris = grains(rng, L, 700, 0.02, 3.8, 150, 4500, falloff=1.4)
    tail = filt(brown(rng, L), 18, 120) * env(t, 0.3, 1.8)
    y = 1.2 * hit + 0.8 * norm(crack) + 0.5 * norm(metal) + 0.9 * norm(blast) + 0.5 * norm(debris) + 0.6 * norm(tail)
    return fade(room(rng, sat(y, 2.0), 2.4, 0.3, lp=2400), 0.001, 1.0)


def refuse(rng, v):
    L = 0.4
    t = tgrid(L)
    knock = filt(white(rng, L), 120, 900) * env(t, 0.001, 0.03)
    tone = sub_drop(t, 90, 60, 0.04, 0.1, 1.5)
    servo_no = servo(t, 300, 200, rng=None) * env(t, 0.005, 0.06) * 0.2
    return fade(0.7 * norm(knock) + tone + servo_no, 0.001, 0.1)


def drop(rng, v):
    # Veronica pod falling from the sky (also the cage fall): long descending whistle into a thud.
    L = 3.0
    t = tgrid(L)
    whistle = sweep_band(white(rng, L), lambda s: 2400 * math.exp(-s / 1.6) + 300, q=4.0) * (t / L) ** 0.5
    tone = np.sin(2 * np.pi * np.cumsum(1800 * np.exp(-t / 1.4) + 260) / SR) * 0.15 * (t / L)
    roar = filt(brown(rng, L), 40, 500) * (t / L) ** 2
    hit = thud(rng, t, 2.6, f=34, decay=0.3, drive=2.8)
    y = norm(whistle) * 0.6 + tone + 0.6 * norm(roar) + 1.1 * hit
    return fade(room(rng, sat(y, 1.6), 1.6, 0.2, lp=3500), 0.2, 0.3)


def exit_(rng, v):
    # Pod opening / suit exit: seals vent, hinges swing, plates part.
    L = 1.8
    t = tgrid(L)
    vent = hydraulic(rng, t, 0.0, 0.35, 900, 7000, burst=True)
    hinge = stickslip(rng, t, lambda s: 70 + 40 * s, [400.0, 830.0, 1500.0], q=7) * bell(t, 0.7, 0.3) * 0.5
    clanks = sum(plate(rng, np.maximum(t - at, 0), 230 + 30 * k, decay=0.3) * (t >= at) * 0.4
                 for k, at in enumerate((0.45, 0.8, 1.1)))
    y = norm(vent) * 0.8 + norm(hinge) * 0.5 + norm(clanks)
    return fade(room(rng, y, 1.0, 0.2, lp=5000), 0.002, 0.3)


def assemble(rng, v):
    # Reactor spin-up after the torso closes: rising electric hum, a bright lock, steady glow.
    L = 2.0
    t = tgrid(L)
    rise = np.clip(t / 1.2, 0, 1) ** 1.3
    hum = buzz(t, 45 + 75 * rise, 14, 1.0) * rise
    whine = np.sin(2 * np.pi * np.cumsum(400 + 2600 * rise) / SR) * 0.15 * rise
    lock = plate(rng, np.maximum(t - 1.2, 0), 520, decay=0.5, bright=1.1) * (t >= 1.2) * 0.5
    y = norm(hum) * 0.7 + whine + norm(lock) * 0.6
    return fade(room(rng, y, 1.0, 0.2), 0.05, 0.4)


def break_(rng, v):
    # Suit broken: torn plates, sparks, hydraulic rupture, a heavy collapse.
    L = 2.6
    t = tgrid(L)
    tear = stickslip(rng, t, lambda s: 260 - 120 * s, [700.0, 1600.0, 3000.0], q=5) * env(t, 0.01, 0.4)
    burst = hydraulic(rng, t, 0.05, 0.5, 800, 8000)
    sparks = grains(rng, L, 300, 0.0, 1.6, 3500, 12000, length=(0.0005, 0.003), falloff=1.5)
    fall = thud(rng, t, 0.9, f=34, decay=0.5, drive=2.8)
    plates = rattle(rng, t, 0.95, 30, 0.3, 650)
    y = 0.6 * norm(tear) + 0.6 * norm(burst) + 0.4 * norm(sparks) + 1.0 * fall + 0.4 * norm(plates)
    return fade(room(rng, sat(y, 1.7), 1.5, 0.22, lp=4000), 0.002, 0.5)


# ---------------------------------------------------------------- loops (circular)

def servo_loop(rng, v):
    # Ambient servo hum while moving (client scales volume by speed).
    L = 2.0
    t = tgrid(L)
    f = loop_f(110.0, L)
    hum = buzz(t, f, 10, 1.3) * (0.8 + 0.2 * np.sin(2 * np.pi * loop_f(1.0, L) * t))
    whine = np.sin(2 * np.pi * loop_f(620.0, L) * t) * 0.12 * (0.6 + 0.4 * np.sin(2 * np.pi * loop_f(2.0, L) * t))
    grit = filt(white(rng, L), 2500, 7000, circular=True) * 0.05
    return norm(hum) * 0.6 + whine + grit


def thruster_loop(rng, v):
    L = 3.0
    t = tgrid(L)
    roar = jet(rng, L, circular=True)
    mod = 0.85 + 0.1 * np.sin(2 * np.pi * loop_f(1.0, L) * t) + 0.05 * np.sin(2 * np.pi * loop_f(4.0, L) * t + 0.4)
    crackle = filt(impulses(rng, L, lambda s: 9.0, circular=True), 1500, 8000, circular=True)
    tone = np.sin(2 * np.pi * loop_f(72.0, L) * t) * 0.2
    return norm(roar) * mod + 0.12 * norm(crackle) + tone


def charge_loop(rng, v):
    # Elbow thruster / repulsor charge held: steady high spool with gear AM.
    L = 1.0
    t = tgrid(L)
    f = loop_f(880.0, L)
    whine = np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * 2 * f * t) + 0.2 * np.sin(2 * np.pi * 3 * f * t)
    am = 0.75 + 0.25 * np.sin(2 * np.pi * loop_f(11.0, L) * t)
    roar = filt(brown(rng, L, circular=True), 60, 900, circular=True)
    return norm(whine * am) * 0.5 + norm(roar) * 0.6


def spark_loop(rng, v):
    # Damaged suit: irregular electrical spitting and a sick reactor flutter.
    L = 2.0
    t = tgrid(L)
    zaps = filt(impulses(rng, L, lambda s: 7.0 + 5.0 * (0.5 + 0.5 * math.sin(2 * math.pi * s / L)), circular=True), 2500, 11000,
                circular=True)
    hum = buzz(t, loop_f(60.0, L), 8, 1.1) * (0.6 + 0.4 * (np.sin(2 * np.pi * loop_f(3.0, L) * t) > 0.7))
    return norm(zaps) * 0.8 + norm(hum) * 0.3


# ---------------------------------------------------------------- CC0 recordings (fix spec item 3)
#
# Recording layers carry the body of every cue family; the synthesis above stays as a support layer under them.
# Sources are Freesound previews (hq MP3) of CC0 1.0 recordings, fetched to RAW (never committed) with --fetch.
# Openverse (the sound-design helper's search/download backend) was unreachable from the sandbox that built
# this kit, so the licence of each row was checked on its Freesound landing page and the credits were composed
# from that page (see tools/assets/ironman_sources.md); there is no Openverse UUID or `attribution` field.

RAW = meter.RAW

# key -> (Freesound id, creator, title as on the landing page, preview path under cdn.freesound.org/previews/).
# Every row: CC0 1.0 (creativecommons.org/publicdomain/zero/1.0) on its landing page, checked 2026-10-30.
SOURCES = {
    "clang_aards": (842171, "AardsReal", "Basic Metal Clang Free", "842/842171_13307919-hq.mp3"),
    "metal_impact": (620077, "RyanKingArt", "Metal Impact", "620/620077_11069322-hq.mp3"),
    "clank_concrete": (345080, "metrostock99", "FX METAL CLANKING ON CONCRETE 501.wav", "345/345080_1256155-hq.mp3"),
    "pan_clang": (125404, "jasonLON", "Metallic Clang.aif", "125/125404_2279655-hq.mp3"),
    "steel_plate": (386129, "ldezem", "Anvil - Lokomo 125 kg - Hammer on 6mm steel 1 time", "386/386129_1655965-hq.mp3"),
    "anvil": (270589, "michorvath", "Anvil Hit 1", "270/270589_3094998-hq.mp3"),
    "sledge_anvil": (554012, "itinerantmonk108", "2lb sledge on anvil.wav", "554/554012_2397507-hq.mp3"),
    "door_slam": (48980, "Lunardrive", "Metal Door Slam_SoundSmith.wav", "48/48980_120830-hq.mp3"),
    "heavy_door": (406197, "kyles", "door metal big heavy close kinda slam thud echo offmic.wav", "406/406197_612689-hq.mp3"),
    "ground_thud": (640204, "7of9Designs", "Heavy Metal Thud on Ground", "640/640204_12131847-hq.mp3"),
    "robot_step": (815039, "AudioPapkin", "Big robot footstep 005", "815/815039_8698658-hq.mp3"),
    "body_fall": (504626, "leonelmail", "BODY FALL - V HVY - DIRT", "504/504626_4437257-hq.mp3"),
    "debris": (703247, "xkeril", "Big falling debris (crash)", "703/703247_13504080-hq.mp3"),
    "explosion": (560510, "Nox_Sound", "Explosion_Debris_Short_Stereo.wav", "560/560510_9250976-hq.mp3"),
    "screech": (530075, "magnuswaker", "Metal Scrape", "530/530075_11537497-hq.mp3"),
    "scrape": (337835, "newagesoup", "long-metal-scrape-05.wav", "337/337835_4067257-hq.mp3"),
    "latch": (154447, "lucaslara", "Metal Latching_1-2.aif", "154/154447_2592491-hq.mp3"),
    "air_release": (457294, "brunoboselli", "Air (or steam) pressure release", "457/457294_300738-hq.mp3"),
    "pffft": (131302, "reg7783", "release_air_pffft.wav", "131/131302_2394357-hq.mp3"),
    "hydraulic": (871228, "qubodup", "Short Machine Sound 4 (CC0 mix)", "871/871228_71257-hq.mp3"),
    "servo_whir": (824121, "derjuli", "ServoMotor.wav", "824/824121_2573247-hq.mp3"),
    "servo_sweep": (322021, "Burningmonkey", "Servo motor", "322/322021_813260-hq.mp3"),
    "afterburner": (413312, "TiesWijnen", "Afterburner", "413/413312_1511977-hq.mp3"),
    "eurofighter": (162242, "surrey_film", "Eurofighter afterburner woomph", "162/162242_1328296-hq.mp3"),
    "torch": (244230, "SyntaxSeed", "Gas Torch Burn", "244/244230_4465058-hq.mp3"),
    "flame_bursts": (395038, "AslakHostaker", "Flamethrower_Weapon_Short_Medium_Bursts.wav", "395/395038_7487276-hq.mp3"),
    "low_swoosh": (475135, "bolkmar", "FX - Swoosh - Low Pitch", "475/475135_2927958-hq.mp3"),
    "sparks": (396240, "weaveofkev", "Welding_Electric Sparks.wav", "396/396240_453602-hq.mp3"),
}
# Derived works and their parents (both CC0 1.0 on their landing pages).
PARENTS = {
    "hydraulic": [(637811, "kyles", "scissor lifts machines up down hydraulic movements2.flac"), (186731, "zaphod1984", "Freight Elevator")],
    "scrape": [(264889, "EpicWizard", "SFX Metal Shed Door - Hits, Clangs, Rattle")],
}

# Transient (or alignment) times inside each preview, measured on the decoded preview (1 ms envelope, 25 % of the
# local peak) so a layer's hit lands exactly on the cue's contact time.
ON = {
    "clang_aards": 0.066, "metal_impact": (0.076, 0.694), "clank_concrete": (0.613, 1.12), "pan_clang": 0.170,
    "steel_plate": 0.0, "anvil": 0.287, "sledge_anvil": (0.084, 1.024), "door_slam": (0.046, 0.564), "heavy_door": 0.337,
    "ground_thud": 0.189, "robot_step": 0.97, "body_fall": 0.425, "debris": 0.048, "explosion": (0.028, 3.028, 8.028),
    "latch": (0.597, 3.434), "pffft": 0.36, "hydraulic": 1.043, "servo_sweep": (0.199, 1.796, 3.342, 4.703, 5.999),
    "afterburner": 0.045, "flame_bursts": (2.245, 5.884, 9.954, 14.2), "low_swoosh": 0.207,
}


def on(src, k=0):
    v = ON[src]
    return v[k] if isinstance(v, tuple) else v


LAYER_CREST = 12.0  # dB: a layer's sample peak may sit at most this far above its loudest 100 ms RMS


def r(src, onset, at, length, pitch=1.0, gain=0.0, pre=0.004, hp=None, lp=None, fin=0.002, fout=None, ramp=0.0, eq=(), crest=LAYER_CREST):
    """One recording layer: the source moment `onset` (s) lands at cue time `at`, `length` s of output after it
    (plus `pre` s before it), varispeed `pitch`, filters, `gain` dB relative to the layer's own loudest 100 ms
    (RMS 0.1 = 0 dB), fades, and an optional rising power ramp (t/len)^ramp. Peaks more than `crest` dB over the
    layer's loudest 100 ms are look-ahead limited on that layer alone (a 1 ms spike of a spiky take, not the body),
    so the class loudness is not dragged down by one take's crest factor (skill gotcha: short hits are crest-limited)."""
    return dict(src=src, onset=onset, at=at, length=length, pitch=pitch, gain=gain, pre=pre, hp=hp, lp=lp, fin=fin,
                fout=fout if fout is not None else min(0.25, 0.4 * length), ramp=ramp, eq=eq, crest=crest)


def tame(x, peak):
    """ffmpeg look-ahead limiter (1 ms attack, 30 ms release, no make-up) holding x under `peak` (linear)."""
    if np.max(np.abs(x)) <= peak or len(x) < n_of(0.01):
        return x
    scale = 0.5 / peak
    cmd = ["ffmpeg", "-v", "error", "-nostdin", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
           "-af", "alimiter=limit=0.5:attack=1:release=30:level=0", "-f", "f32le", "-"]
    y = np.frombuffer(subprocess.run(cmd, input=(x * scale).astype(np.float32).tobytes(), capture_output=True, check=True).stdout,
                      dtype=np.float32).astype(np.float64) / scale
    out = np.zeros_like(x)
    out[:min(len(x), len(y))] = y[:len(x)]
    return out


def raw_file(src):
    return os.path.join(RAW, f"fs_{SOURCES[src][0]}.mp3")


def fetch():
    import urllib.request
    os.makedirs(RAW, exist_ok=True)
    for src, (fid, user, title, preview) in SOURCES.items():
        path = raw_file(src)
        if os.path.isfile(path) and os.path.getsize(path) > 0:
            continue
        url = "https://cdn.freesound.org/previews/" + preview
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (viltrumite-sfx)"})
        with urllib.request.urlopen(req, timeout=90) as resp, open(path + ".tmp", "wb") as out:
            out.write(resp.read())
        os.replace(path + ".tmp", path)
        print(f"fetched {fid} {user} - {title}")


def ref_level(x):
    """Loudest 100 ms RMS (unweighted), the reference a layer's gain is relative to."""
    w = int(0.1 * SR)
    if len(x) <= w:
        return float(np.sqrt(np.mean(x ** 2)) + 1e-12)
    c = np.concatenate([[0.0], np.cumsum(x ** 2)])
    return float(np.sqrt(np.max(c[w:] - c[:-w]) / w) + 1e-12)


_RAW_CACHE = {}


def render_rec(spec):
    src = raw_file(spec["src"])
    if not os.path.isfile(src):
        sys.exit(f"Missing source {spec['src']} ({src}); run: python3 tools/sfx/hulkbuster.py --fetch")
    p = spec["pitch"]
    start = spec["onset"] - spec["pre"] * p
    skip = 0.0
    if start < 0:
        skip = -start / p
        start = 0.0
    dur = (spec["length"] + spec["pre"]) * p
    key = (spec["src"], round(start, 5), round(dur, 5), p, spec["hp"], spec["lp"], spec["eq"])
    if key not in _RAW_CACHE:
        chain = [f"aresample={SR}", f"atrim=start={start:.5f}:duration={dur:.5f}", "asetpts=N/SR/TB"]
        if p != 1.0:
            chain += [f"asetrate={SR * p:.3f}", f"aresample={SR}"]
        if spec["hp"]:
            chain.append(f"highpass=f={spec['hp']}:poles=2")
        if spec["lp"]:
            chain.append(f"lowpass=f={spec['lp']}:poles=2")
        for freq, width, g in spec["eq"]:
            chain.append(f"equalizer=f={freq}:t=o:w={width}:g={g}")
        _RAW_CACHE[key] = meter.ffmpeg_pcm(src, ",".join(chain))
    x = _RAW_CACHE[key].copy()
    x = x - np.mean(x)
    x *= 0.1 / ref_level(x)
    if spec["crest"] is not None:
        x = tame(x, 0.1 * 10 ** (spec["crest"] / 20))
    x *= 10 ** (spec["gain"] / 20)
    n = len(x)
    if spec["ramp"]:
        x *= (np.arange(n) / max(n - 1, 1)) ** spec["ramp"]
    fi, fo = min(n, n_of(spec["fin"])), min(n, n_of(spec["fout"]))
    if fi:
        x[:fi] *= np.linspace(0.0, 1.0, fi)
    if fo:
        x[n - fo:] *= np.cos(np.linspace(0.0, math.pi / 2, fo)) ** 2
    return x[n_of(skip):], spec["at"] - spec["pre"] + skip


def place(out, x, at):
    a = n_of(at)
    if a < 0:
        x, a = x[-a:], 0
    m = min(len(x), len(out) - a)
    if m > 0:
        out[a:a + m] += x[:m]


def freeze(src, start, length, loop, seed, pitch=1.0, band=(40.0, 12000.0)):
    """Spectral freeze (skill gotcha): the magnitude spectrum of the recording segment with seeded random
    phases over exactly the loop length, so the loop is seamless by construction (no fades)."""
    spec = r(src, start, 0.0, length, pitch=pitch, pre=0.0, fin=0.0, fout=0.0, crest=None)
    seg, _ = render_rec(spec)
    w = int(0.05 * SR)
    env_ = np.sqrt(np.convolve(seg ** 2, np.ones(w) / w, mode="same")) + 1e-6
    flat = seg / env_ * np.mean(env_)
    mag = np.abs(np.fft.rfft(flat * np.hanning(len(flat))))
    n = n_of(loop)
    f_loop = np.fft.rfftfreq(n, 1 / SR)
    m = np.interp(f_loop, np.fft.rfftfreq(len(flat), 1 / SR), mag)
    m *= butter_mag(f_loop, band[0], band[1], 2)
    rng = np.random.default_rng(seed)
    phase = rng.uniform(0, 2 * math.pi, len(m))
    phase[0] = 0.0
    x = np.fft.irfft(m * np.exp(1j * phase), n)
    return x / (np.std(x) + 1e-12)


def wrap_loop(src, start, loop, overlap):
    """Seamless loop that keeps transients (crackle): loop + overlap seconds, the overlap folded onto the head
    with equal-power fades, so sample L-1 runs straight into sample 0 of the recording."""
    spec = r(src, start, 0.0, loop + overlap, pre=0.0, fin=0.0, fout=0.0, crest=None)
    seg, _ = render_rec(spec)
    n, k = n_of(loop), n_of(overlap)
    seg = seg[:n + k]
    out = seg[:n].copy()
    fade_in = np.sin(np.linspace(0, math.pi / 2, k)) ** 2
    out[:k] = seg[:k] * fade_in + seg[n:n + k] * (1 - fade_in)
    return out / (np.std(out) + 1e-12)


def tame_circular(x, crest):
    """Peak-limit a loop without breaking its seam: limit three copies end to end and keep the middle one."""
    n = len(x)
    y = tame(np.tile(x, 3), ref_level(x) * 10 ** (crest / 20))
    return y[n:2 * n]


def periodic(n, L, terms):
    t = np.arange(n) / n
    y = np.ones(n)
    for cycles, depth, ph in terms:
        y += depth * np.sin(2 * np.pi * cycles * t + ph)
    return y


# Recording recipes: base -> f(v) -> dict(layers=[...], synth=dB of the synthesis support under the recordings
# (by loudest 100 ms RMS; None = none), synth_lp=low-pass on the support (Hz) so only its sub / weight stays).
# Cue times follow the synthesis files (contact times unchanged, so no server pre-roll moves).

def rec_step(v):
    return dict(layers=[
        r("ground_thud", on("ground_thud"), 0.07, 0.55, pitch=[0.85, 0.8, 0.9, 0.75][v]),
        r("robot_step", on("robot_step"), 0.07, 0.6, pitch=[0.9, 0.85, 0.95, 0.8][v], gain=-4, pre=0.03),
        (r("pffft", on("pffft"), 0.0, 0.12, gain=-16, hp=1500, fout=0.06) if v % 2 == 0 else
         r("hydraulic", on("hydraulic"), 0.0, 0.14, pitch=0.9, gain=-14, hp=800)),
        r("clank_concrete", on("clank_concrete", v % 2), 0.085, 0.4, pitch=[0.8, 0.85, 0.7, 0.9][v], gain=-12),
    ], synth=-8, synth_lp=220)


def rec_screech(v):
    lay = [
        [r("screech", 0.05, 0.0, 0.9, pitch=0.8, pre=0.0, fin=0.03, fout=0.18)],
        [r("scrape", 1.1, 0.0, 1.1, pitch=0.85, pre=0.0, fin=0.03, fout=0.2)],
        [r("screech", 0.25, 0.0, 0.7, pitch=0.95, pre=0.0, fin=0.02, fout=0.15)],
        [r("scrape", 2.2, 0.0, 1.3, pitch=0.7, pre=0.0, fin=0.04, fout=0.25)],
        [r("screech", 0.0, 0.0, 1.0, pitch=0.7, pre=0.0, fin=0.03, fout=0.2),
         r("scrape", 1.5, 0.05, 0.9, pitch=0.8, gain=-6, pre=0.0, fin=0.05, fout=0.2)],
    ][v]
    return dict(layers=lay, synth=-8, synth_lp=None)


def rec_swing(v):
    return dict(layers=[
        r("low_swoosh", on("low_swoosh"), 0.36, 0.38, pitch=[0.85, 0.75, 0.95][v], pre=0.2, fin=0.05),
        r("servo_sweep", on("servo_sweep", v), 0.06, 0.35, pitch=[0.7, 0.6, 0.8][v], gain=-9, hp=300, fin=0.01),
    ], synth=-6, synth_lp=None)


def _wind(at, v, gain=-15, pitch=0.65, k=None):
    return r("servo_sweep", on("servo_sweep", (v if k is None else k) % 5), at, 0.25, pitch=pitch, gain=gain, hp=300, fin=0.03, fout=0.1)


def rec_punch(v):
    c = 0.30
    body = [
        [r("metal_impact", on("metal_impact", 0), c, 0.7, pitch=0.7), r("heavy_door", on("heavy_door"), c, 0.8, pitch=0.8, gain=-4)],
        [r("metal_impact", on("metal_impact", 1), c, 0.7, pitch=0.72), r("door_slam", on("door_slam", 1), c, 0.6, pitch=0.85, gain=-4)],
        [r("steel_plate", on("steel_plate"), c, 1.0, pitch=0.6, gain=-3, pre=0.0), r("heavy_door", on("heavy_door"), c, 0.8, pitch=0.75, gain=-2)],
    ][v]
    return dict(layers=body + [
        r("ground_thud", on("ground_thud"), c, 0.45, pitch=0.7, gain=-6),
        _wind(0.04, v),
        r("low_swoosh", on("low_swoosh"), c - 0.04, 0.12, pitch=0.8, gain=-9, pre=0.18, fin=0.05, fout=0.08),
        r("debris", on("debris"), c + 0.02, 0.9, gain=-15, lp=4000),
    ], synth=-9, synth_lp=250)


def rec_hammer(v):
    c = 0.10
    hit = [r("sledge_anvil", on("sledge_anvil", 0), c, 0.6, pitch=0.6, gain=-2),
           r("sledge_anvil", on("sledge_anvil", 1), c, 0.6, pitch=0.62, gain=-2),
           r("anvil", on("anvil"), c, 0.6, pitch=0.55, gain=-2)][v]
    return dict(layers=[
        hit,
        r("heavy_door", on("heavy_door"), c, 0.6, pitch=[0.85, 0.9, 0.8][v], gain=-2),
        r("hydraulic", on("hydraulic"), 0.04, 0.12, pitch=1.1, gain=-10, hp=500),
        r("clank_concrete", on("clank_concrete", v % 2), c + 0.02, 0.35, pitch=0.8, gain=-13),
    ], synth=-8, synth_lp=300)


def rec_jackhammer(v):
    c = 0.08
    return dict(layers=[
        r("metal_impact", on("metal_impact", v % 2), c, 0.55, pitch=[0.8, 0.82, 0.86][v]),
        r("body_fall", on("body_fall"), c, 0.3, pitch=0.9, gain=-4),
        r("sledge_anvil", on("sledge_anvil", 1), c, 0.4, pitch=0.7, gain=-7),
    ], synth=-10, synth_lp=None)


def rec_finish_punch(v):
    c = 0.62
    return dict(layers=[
        r("heavy_door", on("heavy_door"), c, 1.4, pitch=0.65),
        r("metal_impact", on("metal_impact", 1), c, 0.9, pitch=0.6, gain=-2),
        r("body_fall", on("body_fall"), c, 0.5, pitch=0.8, gain=-6),
        r("debris", on("debris"), c + 0.02, 1.7, gain=-8, lp=5000),
        _wind(0.05, 0, gain=-13, pitch=0.55), _wind(0.3, 0, gain=-13, pitch=0.5, k=2),
        r("low_swoosh", on("low_swoosh"), c - 0.04, 0.15, pitch=0.7, gain=-6, pre=0.25, fin=0.05, fout=0.1),
    ], synth=-7, synth_lp=250)


def rec_charged_punch(v):
    c = 0.16
    return dict(layers=[
        r("afterburner", on("afterburner"), 0.0, 0.9, pitch=0.9, gain=-4, fout=0.5),
        r("steel_plate", on("steel_plate"), c, 1.6, pitch=0.55, gain=-2, pre=0.0, fout=0.8),
        r("heavy_door", on("heavy_door"), c, 1.0, pitch=0.7),
        r("debris", on("debris"), c + 0.02, 1.4, gain=-10, lp=5000),
    ], synth=-7, synth_lp=250)


def rec_charge(v):
    return dict(layers=[
        r("eurofighter", 10.1, 0.0, 1.45, pre=0.0, fin=0.0, fout=0.04, ramp=1.6),
        r("servo_whir", 0.3, 0.0, 1.45, pitch=0.7, gain=-8, pre=0.0, fin=0.0, fout=0.04, ramp=1.5),
        r("latch", on("latch", 0), 1.45, 0.14, pitch=0.6, gain=-4, fout=0.06),
        r("heavy_door", on("heavy_door"), 1.45, 0.15, gain=-6, fout=0.06),
    ], synth=-6, synth_lp=None)


def rec_clamp(v):
    at = 0.12
    clang = [r("clang_aards", on("clang_aards"), at, 0.6, pitch=0.6),
             r("pan_clang", on("pan_clang"), at, 0.7, pitch=0.7),
             r("steel_plate", on("steel_plate"), at, 0.6, pitch=0.75, pre=0.0),
             r("anvil", on("anvil"), at, 0.6, pitch=0.55)][v]
    return dict(layers=[
        r("hydraulic", on("hydraulic"), 0.10, 0.12, gain=-8, hp=300, pre=0.1, fin=0.03),
        clang,
        r("heavy_door", on("heavy_door"), at, 0.5, pitch=[0.9, 1.0, 0.95, 0.85][v], gain=-5),
        r("latch", on("latch", v % 2), at + 0.05, 0.2, gain=-8),
    ], synth=-10, synth_lp=300)


def rec_block_hit(v):
    hit = [r("metal_impact", on("metal_impact", 0), 0.0, 0.8, pitch=0.8),
           r("pan_clang", on("pan_clang"), 0.0, 0.8, pitch=0.6),
           r("steel_plate", on("steel_plate"), 0.0, 0.9, pitch=0.7, pre=0.0)][v]
    return dict(layers=[
        hit,
        r("heavy_door", on("heavy_door"), 0.0, 0.6, gain=-5),
        r("sparks", 0.15 + 0.4 * v, 0.0, 0.3, gain=-14, hp=3000, pre=0.0),
        r("screech", 0.1, 0.05, 0.25, gain=-14, pre=0.0, fin=0.02),
    ], synth=-10, synth_lp=300)


def rec_repulsor_charge(v):
    # Energy weapon: synthesis-led (the repulsor whine), recorded jet spool as support.
    return dict(layers=[
        r("eurofighter", 10.2, 0.0, 1.3, pitch=1.25, gain=-6, pre=0.0, fin=0.0, fout=0.05, ramp=2.0),
        r("servo_whir", 0.5, 0.0, 1.3, pitch=1.3, gain=-10, pre=0.0, fin=0.0, fout=0.05, ramp=1.4),
    ], synth=4, synth_lp=None)


def rec_repulsor_beam(v):
    return dict(layers=[
        r("afterburner", on("afterburner"), 0.0, 1.6, gain=-2, lp=7000, fout=0.8),
        r("heavy_door", on("heavy_door"), 0.0, 0.5, pitch=0.6, gain=-6),
    ], synth=2, synth_lp=None)


def rec_dash(v):
    return dict(layers=[
        r("flame_bursts", on("flame_bursts", v), 0.0, 0.6, pitch=0.8, fout=0.3),
        r("afterburner", on("afterburner"), 0.0, 0.5, pitch=0.85, gain=-4, fout=0.3),
        r("ground_thud", on("ground_thud"), 0.0, 0.4, pitch=0.8, gain=-3),
        r("scrape", 1.2 + 0.6 * v, 0.2, 0.5, pitch=0.8, gain=-10, pre=0.0, fin=0.05),
    ], synth=-9, synth_lp=None)


def rec_cough(v):
    ats = [0.0, 0.17 + 0.03 * v, 0.38]
    return dict(layers=[
        r("flame_bursts", on("flame_bursts", (2 + k + v) % 4), at, 0.1, pitch=0.75, gain=-2 * k, fout=0.06)
        for k, at in enumerate(ats)] + [
        r("clank_concrete", on("clank_concrete", v), 0.05, 0.3, gain=-12),
    ], synth=-8, synth_lp=None)


def rec_land(v):
    return dict(layers=[
        r("ground_thud", on("ground_thud"), 0.0, 0.6, pitch=[0.7, 0.65][v]),
        r("robot_step", on("robot_step"), 0.0, 0.8, pitch=0.8, gain=-2, pre=0.03),
        r("heavy_door", on("heavy_door"), 0.0, 0.9, pitch=0.7, gain=-5),
        r("debris", on("debris"), 0.01, 1.3, gain=-9, lp=4000),
        (r("pffft", on("pffft"), 0.1, 0.25, gain=-12, hp=1200) if v == 0 else
         r("hydraulic", on("hydraulic"), 0.1, 0.25, gain=-12, hp=800)),
    ], synth=-8, synth_lp=200)


def rec_slam(v):
    return dict(layers=[
        r("body_fall", on("body_fall"), 0.0, 0.6, pitch=0.8, gain=-1),
        r("ground_thud", on("ground_thud"), 0.0, 0.6, pitch=[0.6, 0.55][v]),
        r("debris", on("debris"), 0.01, 1.9, gain=-5, lp=5000),
        r("explosion", on("explosion", v), 0.0, 1.6, gain=-6, lp=3000, fout=0.8),
        r("heavy_door", on("heavy_door"), 0.0, 0.9, pitch=0.65, gain=-7),
    ], synth=-7, synth_lp=200)


def rec_grab(v):
    return dict(layers=[
        r("servo_sweep", on("servo_sweep", 2 + v), 0.0, 0.2, pitch=0.7, gain=-6, hp=300, fin=0.01),
        r("heavy_door", on("heavy_door"), 0.16, 0.4, pitch=[1.1, 1.05][v], gain=-2),
        r("latch", on("latch", v), 0.16, 0.2, gain=-6),
        r("clank_concrete", on("clank_concrete", v), 0.17, 0.25, gain=-11),
        r("hydraulic", on("hydraulic"), 0.15, 0.2, gain=-9, hp=500, pre=0.05),
    ], synth=-9, synth_lp=None)


def rec_throw(v):
    return dict(layers=[
        r("low_swoosh", on("low_swoosh"), 0.42, 0.45, pitch=0.7, pre=0.25, fin=0.05),
        r("servo_sweep", on("servo_sweep", 1), 0.1, 0.3, pitch=0.6, gain=-6, hp=300, fin=0.01),
        r("pffft", on("pffft"), 0.44, 0.3, gain=-6, hp=900),
    ], synth=-7, synth_lp=None)


def rec_hop(v):
    return dict(layers=[
        r("afterburner", on("afterburner"), 0.0, 1.4, pitch=0.85, fout=0.8),
        r("flame_bursts", on("flame_bursts", 3), 0.0, 0.8, pitch=0.8, gain=-3, fout=0.4),
        r("ground_thud", on("ground_thud"), 0.0, 0.5, pitch=0.7, gain=-2),
    ], synth=-9, synth_lp=300)


def rec_ult_ignite(v):
    return dict(layers=[
        r("eurofighter", 9.0, 0.0, 2.55, pre=0.0, fin=0.0, fout=0.05, ramp=1.3),
        r("afterburner", 0.5, 0.0, 2.55, gain=-4, pre=0.0, fin=0.0, fout=0.05, ramp=2.0),
        r("servo_whir", 0.3, 0.0, 2.55, pitch=0.6, gain=-10, pre=0.0, fin=0.0, fout=0.05, ramp=1.2),
    ] + [r("heavy_door", on("heavy_door"), at, 0.2, pitch=1.2, gain=-6, fout=0.08) for at in (0.2, 0.55, 0.9)],
        synth=-6, synth_lp=None)


def rec_ult_impact(v):
    return dict(layers=[
        r("explosion", on("explosion", 2), 0.0, 4.4, gain=0, fout=1.5),
        r("debris", on("debris"), 0.02, 4.4, gain=-4, fout=1.5),
        r("ground_thud", on("ground_thud"), 0.0, 1.0, pitch=0.5),
        r("heavy_door", on("heavy_door"), 0.0, 1.5, pitch=0.55, gain=-4),
        r("steel_plate", on("steel_plate"), 0.0, 2.5, pitch=0.45, gain=-8, pre=0.0, fout=1.2),
    ], synth=-6, synth_lp=150)


def rec_refuse(v):
    return dict(layers=[
        r("latch", on("latch", 0), 0.0, 0.3, pitch=0.7),
        r("heavy_door", on("heavy_door"), 0.0, 0.12, pitch=1.3, gain=-6, fout=0.05),
    ], synth=-4, synth_lp=None)


def rec_drop(v):
    # Pod fall: the synthesized whistle stays a near-equal partner (no CC0 falling-whistle recording fitted).
    return dict(layers=[
        r("eurofighter", 4.0, 0.0, 2.6, gain=-4, pre=0.0, fin=0.0, fout=0.05, ramp=1.5),
        r("ground_thud", on("ground_thud"), 2.6, 0.4, pitch=0.55),
        r("heavy_door", on("heavy_door"), 2.6, 0.4, pitch=0.6, gain=-3),
        r("debris", on("debris"), 2.62, 0.38, gain=-8, fout=0.2),
    ], synth=-3, synth_lp=None)


def rec_exit(v):
    return dict(layers=[
        r("air_release", 0.05, 0.0, 0.45, hp=300, pre=0.0, fin=0.005, fout=0.3),
        r("hydraulic", 0.0, 0.35, 0.9, gain=-8, pre=0.0, fin=0.05, fout=0.2),
        r("clang_aards", on("clang_aards"), 0.45, 0.35, pitch=0.7, gain=-2),
        r("pan_clang", on("pan_clang"), 0.8, 0.3, pitch=0.75, gain=-2),
        r("latch", on("latch", 0), 1.1, 0.3, pitch=0.8, gain=-3),
        r("heavy_door", on("heavy_door"), 1.1, 0.5, pitch=1.1, gain=-4),
    ], synth=-8, synth_lp=None)


def rec_assemble(v):
    # Reactor spin-up: synthesis-led electric hum, recorded servo whir and steel lock.
    return dict(layers=[
        r("servo_whir", 0.3, 0.0, 1.2, pitch=0.8, gain=-6, pre=0.0, fin=0.0, fout=0.04, ramp=1.2),
        r("steel_plate", on("steel_plate"), 1.2, 0.7, pre=0.0, fout=0.4),
        r("latch", on("latch", 1), 1.2, 0.3, gain=-4),
    ], synth=0, synth_lp=None)


def rec_break(v):
    return dict(layers=[
        r("scrape", 0.75, 0.0, 1.0, pitch=0.8, pre=0.0, fin=0.01),
        r("air_release", 0.05, 0.05, 0.6, gain=-4, hp=300, pre=0.0, fin=0.005),
        r("sparks", 0.1, 0.0, 1.6, gain=-8, pre=0.0, fin=0.01, fout=0.6),
        r("ground_thud", on("ground_thud"), 0.9, 0.6, pitch=0.6),
        r("heavy_door", on("heavy_door"), 0.9, 0.9, pitch=0.7, gain=-3),
        r("debris", on("debris"), 0.92, 1.5, gain=-6, fout=0.6),
    ], synth=-8, synth_lp=None)


# Mix-bus tanh soft clip per class (the synthesis kit's `sat`), knee in dB above the cue's loudest 100 ms RMS:
# rounds the first milliseconds of big hits so the class reaches its loudness without harder look-ahead limiting.
BUS_KNEE = {"impact": 7.0, "heavy": 7.0, "step": 8.0, "body": 8.0}
BUS_HP = 38.0  # Hz, 2nd-order high-pass on every one-shot: below it a pitched-down thud is peak, not loudness

# Loops: recordings frozen (or wrapped) over exactly the loop length; synthesis support is circular too.
LOOPS = {
    # name: (builder(n) -> recorded body, synth support dB, synth low-pass)
    "servo_loop": (lambda L: freeze("servo_whir", 0.3, 3.4, L, 824121, pitch=0.6, band=(80.0, 7000.0))
                   * periodic(n_of(L), L, [(1, 0.12, 0.0), (2, 0.06, 1.3)]), -6, None),
    "thruster_loop": (lambda L: (freeze("afterburner", 1.5, 3.8, L, 413312, band=(30.0, 9000.0))
                                 + 0.6 * freeze("torch", 2.0, 10.0, L, 244230, pitch=0.8, band=(40.0, 8000.0)))
                      * periodic(n_of(L), L, [(1, 0.08, 0.0), (4, 0.04, 0.4)]), -10, None),
    "charge_loop": (lambda L: freeze("servo_whir", 0.3, 2.8, L, 8241211, pitch=1.2, band=(150.0, 10000.0))
                    * periodic(n_of(L), L, [(4, 0.15, 0.0)])
                    + 0.7 * freeze("eurofighter", 10.5, 1.3, L, 162242, band=(60.0, 8000.0)), -4, None),
    "spark_loop": (lambda L: tame_circular(wrap_loop("sparks", 0.1, L, 0.25), 9.0), -3, None),
}

RECS = {name[4:]: fn for name, fn in list(globals().items()) if name.startswith("rec_") and callable(fn)}


def rec_sources(name):
    """Source keys a file uses (empty = synthesis only)."""
    base = FILES[name][4]
    if is_loop(name):
        return {"servo_loop": ["servo_whir"], "thruster_loop": ["afterburner", "torch"], "charge_loop": ["servo_whir", "eurofighter"],
                "spark_loop": ["sparks"]}.get(name, [])
    fn = RECS.get(base)
    return list(dict.fromkeys(spec["src"] for spec in fn(FILES[name][1])["layers"])) if fn else []


def combine(name, synth_x):
    """Recordings (body) + synthesis (support) for one file; length and contact times are the synthesis file's."""
    base = FILES[name][4]
    if is_loop(name):
        if name not in LOOPS:
            return synth_x
        build, sdb, slp = LOOPS[name]
        L = len(synth_x) / SR
        body = build(L)
        body = body[:len(synth_x)]
        sup = filt(synth_x, hi=slp, circular=True) if slp else synth_x
        return body * (0.1 / ref_level(body)) + sup * (0.1 * 10 ** (sdb / 20) / ref_level(sup))
    fn = RECS.get(base)
    if not fn:
        return synth_x
    plan = fn(FILES[name][1])
    out = np.zeros(len(synth_x))
    for spec in plan["layers"]:
        x, at = render_rec(spec)
        place(out, x, at)
    if plan["synth"] is not None:
        sup = filt(synth_x, hi=plan["synth_lp"]) if plan["synth_lp"] else synth_x
        out += sup * (ref_level(out) * 10 ** (plan["synth"] / 20) / ref_level(sup))
    out = filt(out, lo=BUS_HP)
    knee = BUS_KNEE.get(FILES[name][2])
    if knee is not None:
        t = ref_level(out) * 10 ** (knee / 20)
        out = t * np.tanh(out / t)
    tail = min(len(out), n_of(0.012))
    out[len(out) - tail:] *= np.linspace(1.0, 0.0, tail)
    return out - np.mean(out)


# ---------------------------------------------------------------- table

PLAN = [
    # base, variants, class, event (ironman_hulkbuster_<event>)
    ("step", 4, "step", "step"),
    ("screech", 5, "screech", "screech"),
    ("swing", 3, "swing", "swing"),
    ("punch", 3, "impact", "punch"),
    ("hammer", 3, "impact", "hammer"),
    ("jackhammer", 3, "impact", "jackhammer"),
    ("finish_punch", 1, "heavy", "finish_punch"),
    ("charged_punch", 1, "heavy", "charged_punch"),
    ("charge", 1, "riser", "charge"),
    ("clamp", 4, "body", "clamp"),
    ("block_hit", 3, "impact", "block_hit"),
    ("repulsor_charge", 1, "riser", "repulsor_charge"),
    ("repulsor_beam", 1, "heavy", "repulsor_beam"),
    ("dash", 2, "swing", "dash"),
    ("cough", 2, "body", "cough"),
    ("land", 2, "impact", "land"),
    ("slam", 2, "heavy", "slam"),
    ("grab", 2, "body", "grab"),
    ("throw", 1, "swing", "throw"),
    ("hop", 1, "impact", "hop"),
    ("ult_ignite", 1, "riser", "ult_ignite"),
    ("ult_impact", 1, "heavy", "ult_impact"),
    ("refuse", 1, "ui", "refuse"),
    ("drop", 1, "riser", "drop"),
    ("exit", 1, "body", "exit"),
    ("assemble", 1, "riser", "assemble"),
    ("break", 1, "heavy", "break"),
    ("servo_loop", 1, "loop", "servo_loop"),
    ("thruster_loop", 1, "loop", "thruster_loop"),
    ("charge_loop", 1, "loop", "charge_loop"),
    ("spark_loop", 1, "loop", "spark_loop"),
]
RECIPES = {"exit": exit_, "break": break_}


def _files():
    table = {}
    for base, count, cls, event in PLAN:
        recipe = RECIPES.get(base) or globals()[base]
        for v in range(count):
            table[f"{base}_{v + 1}" if count > 1 else base] = (recipe, v, cls, event, base)
    return table


FILES = _files()


def is_loop(name):
    return name.endswith("_loop")


def synth(name):
    recipe, variant = FILES[name][:2]
    seed = SEED + sum(ord(c) * (i + 1) for i, c in enumerate(name))
    x = recipe(np.random.default_rng(seed), variant).astype(np.float64)
    if not is_loop(name):
        x = x - np.mean(x)
    return x


def render(names):
    """Synthesize the requested files plus their class siblings, then level every class together."""
    classes = {FILES[n][2] for n in names}
    members = [n for n in FILES if FILES[n][2] in classes]
    raw = {n: combine(n, synth(n)) for n in members}
    out = {}
    for cls in classes:
        target, ceiling, allowance = CLASSES[cls]
        group = [n for n in members if FILES[n][2] == cls]
        loud = {n: meter.momentary_max(raw[n]) for n in group}
        common = min([target] + [loud[n] + ceiling + allowance - 0.05 - meter.true_peak(raw[n]) for n in group])
        # The look-ahead limiter also lowers the body of a sub-heavy hit, so each variant's gain is corrected until its
        # limited 100 ms loudness sits on the class value (at most 1.5 dB of extra gain); a variant that still falls
        # short lowers the whole class, so variants stay matched.
        for _ in range(3):
            short = 0.0
            for n in group:
                g = common - loud[n]
                y = raw[n] * 10 ** (g / 20)
                if not is_loop(n):
                    for _ in range(4):
                        y = meter.limit(raw[n] * 10 ** (g / 20), ceiling)
                        err = common - meter.momentary_max(y)
                        if abs(err) < 0.1 or g - (common - loud[n]) + err > 1.5:
                            break
                        g += err
                    short = max(short, common - meter.momentary_max(y))
                out[n] = y
            if short < 0.15:
                break
            common -= short
    return {n: out[n] for n in names}


def decode_asset(name):
    import base64
    with open(os.path.join(ASSETS, name + ".ogg.b64"), "rb") as stream:
        data = base64.decodebytes(stream.read())
    proc = subprocess.run(["ffmpeg", "-v", "error", "-nostdin", "-i", "-", "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                          input=data, capture_output=True, check=True)
    return np.frombuffer(proc.stdout, dtype=np.float32).astype(np.float64)


def seam(x):
    d2 = np.diff(x, 2)
    edge = abs(x[1] - 2 * x[0] + x[-1]), abs(x[0] - 2 * x[-1] + x[-2])
    return max(edge) / (np.sqrt(np.mean(d2 ** 2)) + 1e-12)


def package(wav, name):
    """Same encode as the sound-design helper's `package` (ffmpeg, mono, 44.1 kHz, libvorbis -q:a 5, Ogg, base64
    text into binassets). Done here because the helper's package step demands an Openverse --source-id (or --own,
    which would misstate a recording's provenance) and Openverse was unreachable; see the module docstring."""
    import base64
    import tempfile
    out = os.path.join(ASSETS, name + ".ogg.b64")
    with tempfile.TemporaryDirectory(prefix="viltrumite-sfx-") as tmp:
        ogg = os.path.join(tmp, "processed.ogg")
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-i", wav, "-map", "0:a:0", "-vn", "-ac", "1",
                        "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", "-f", "ogg", ogg], check=True)
        probe = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=codec_name,channels,sample_rate",
                                "-of", "csv=p=0", ogg], capture_output=True, text=True, check=True).stdout.strip()
        if probe != "vorbis,44100,1":
            sys.exit(f"{name}: unexpected encode {probe}")
        with open(ogg, "rb") as stream:
            payload = base64.encodebytes(stream.read())
    os.makedirs(ASSETS, exist_ok=True)
    with open(out + ".tmp", "wb") as stream:
        stream.write(payload)
    os.replace(out + ".tmp", out)
    plain = os.path.join(ROOT, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore", "sounds", "ironman", "hulkbuster", name + ".ogg")
    if os.path.exists(plain):
        os.remove(plain)


def write_sounds_json():
    """Point every ironman_hulkbuster_<event> at its variants (other entries untouched, order kept)."""
    with open(SOUNDS_JSON, encoding="utf-8") as stream:
        data = json.load(stream)
    events = {}
    for name, (_, _, _, event, _) in FILES.items():
        events.setdefault(event, []).append(name)
    for event, names in events.items():
        key = "ironman_hulkbuster_" + event
        entry = data.get(key, {"category": "player"})
        entry["subtitle"] = "subtitles.viltrumitecore." + key
        sounds = []
        for n in names:
            s = {"name": "viltrumitecore:ironman/hulkbuster/" + n}
            if is_loop(n):
                s["stream"] = False
            sounds.append(s if len(s) > 1 else s["name"])
        entry["sounds"] = sounds
        data[key] = entry
    with open(SOUNDS_JSON, "w", encoding="utf-8") as stream:
        json.dump(data, stream, indent=2, ensure_ascii=False)
        stream.write("\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "mix", "hulkbuster"))
    parser.add_argument("--only", action="append", choices=sorted(FILES))
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--no-package", action="store_true")
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--sounds-json", action="store_true")
    parser.add_argument("--fetch", action="store_true", help="download the missing Freesound previews into the raw cache")
    parser.add_argument("--sources", action="store_true", help="file -> recording sources (Freesound ids)")
    args = parser.parse_args()
    if args.fetch:
        fetch()
        return
    if args.sources:
        for name in FILES:
            ids = ", ".join(f"{SOURCES[s][0]} {SOURCES[s][1]}" for s in rec_sources(name)) or "synthesis only"
            print(f"{name}\t{ids}")
        return
    if args.list:
        for name, (_, _, cls, event, _) in FILES.items():
            print(f"{name}\tironman_hulkbuster_{event}\t{cls}")
        return
    if args.sounds_json:
        write_sounds_json()
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
            extra = f", seam {seam(y):.4f}" if is_loop(name) else ""
            print(f"{name}: {'ok' if ok else 'MISMATCH'} correlation {corr:.4f}, {len(y) / SR:.3f} s, decoded M100 "
                  f"{meter.momentary_max(y):.1f} LUFS, TP {meter.true_peak(y):.1f} dBTP{extra}")
            continue
        wav = os.path.join(args.out, name + ".wav")
        meter.write_wav(x, wav)
        extra = f", seam {seam(x):.4f}" if is_loop(name) else ""
        print(f"{name}: {len(x) / SR:.3f} s, M100 {meter.momentary_max(x):.1f} LUFS, TP {meter.true_peak(x):.1f} dBTP{extra}")
        if not args.no_package:
            package(wav, name)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
