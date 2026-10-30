#!/usr/bin/env python3
"""The Magnificent Sinclair: every sound synthesised here from scratch (numpy), CC0 own work.

No recordings, no vanilla sounds. Each recipe is a deterministic function (fixed seeds) that
returns mono 44.1 kHz float PCM; the script peak-normalises it, encodes Ogg Vorbis with ffmpeg
(libvorbis, q5) and installs <name>.ogg.b64 into binassets/sounds/sinclair/ for the Gradle
decodeBinaryAssets pipeline. It also keeps the "sinclair.*" entries of sounds.json in step.
Usage: python3 tools/sfx/sinclair.py [--check] [--only NAME ...] [--wav DIR]
--check re-synthesises and compares against the committed files (decoded PCM correlation).

Superseded 2026-11-02 by tools/sfx/sinclair_cc0.py (sound pass 2: Kenney CC0 layers + these primitives,
45 events, loudness classes, release tails). This file now only provides the synthesis primitives and
the pass-1 recipes for reference; it no longer installs files or edits sounds.json (use --wav to listen).
"""
import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/sinclair")
SOUNDS_JSON = os.path.join(ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore/sounds.json")
SR = 44100


# ------------------------------------------------------------------ primitives
def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def rng(seed):
    return np.random.default_rng(seed)


def env(n, a, d, s=0.0, r=0.0, curve=3.0):
    """Attack/decay(to sustain)/release envelope in seconds over n samples."""
    t = np.arange(n) / SR
    e = np.ones(n) * s
    e[t < a] = (t[t < a] / max(a, 1e-6))
    m = (t >= a) & (t < a + d)
    e[m] = s + (1 - s) * np.exp(-curve * (t[m] - a) / max(d, 1e-6))
    if r > 0:
        tail = t > (t[-1] - r)
        e[tail] *= np.linspace(1, 0, tail.sum()) ** 2
    return e


def expdecay(n, tau):
    return np.exp(-np.arange(n) / SR / tau)


def fade(x, fin=0.003, fout=0.02):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def sine_sweep(dur, f0, f1, curve="exp"):
    t = t_axis(dur)
    if curve == "exp":
        f = f0 * (f1 / f0) ** (t / dur)
    else:
        f = f0 + (f1 - f0) * t / dur
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def band(x, lo, hi):
    """Brick-ish FFT band-pass with soft edges."""
    n = len(x)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    m = 1 / (1 + (lo / np.maximum(f, 1)) ** 4) / (1 + (f / hi) ** 4)
    return np.fft.irfft(X * m, n)


def lowpass(x, cutoff):
    return band(x, 1, cutoff)


def highpass(x, cutoff):
    return band(x, cutoff, SR / 2)


def sweep_filter(x, f0, f1, q=6.0, blocks=48):
    """Time-varying band-pass by overlapping FFT blocks (for whooshes)."""
    n = len(x)
    out = np.zeros(n)
    size = max(256, n // blocks * 2)
    hop = size // 2
    win = np.hanning(size)
    for start in range(0, n, hop):
        seg = x[start:start + size]
        if len(seg) < size:
            seg = np.pad(seg, (0, size - len(seg)))
        frac = min(1.0, start / max(1, n - size))
        fc = f0 * (f1 / f0) ** frac
        y = band(seg * win, fc / (1 + 1 / q) / 1.2, fc * (1 + 1 / q) * 1.2)
        end = min(n, start + size)
        out[start:end] += y[:end - start]
    return out


def bell(dur, f, partials=((1, 1), (2.76, 0.5), (5.4, 0.25), (8.93, 0.12)), tau=0.4):
    t = t_axis(dur)
    y = np.zeros_like(t)
    for ratio, amp in partials:
        if f * ratio > 0.45 * SR:
            continue  # no aliased partials
        y += amp * np.sin(2 * np.pi * f * ratio * t) * np.exp(-t / (tau / ratio ** 0.5))
    return y


def pluck(dur, f, seed=1, damp=0.996):
    n = int(dur * SR)
    p = max(2, int(SR / f))
    buf = rng(seed).uniform(-1, 1, p)
    y = np.zeros(n)
    for i in range(n):
        y[i] = buf[i % p]
        buf[i % p] = damp * 0.5 * (buf[i % p] + buf[(i + 1) % p])
    return y


def saw(dur, f, detune=0.0):
    t = t_axis(dur)
    y = 2 * ((f * (1 + detune) * t) % 1.0) - 1
    return y


def click(n, seed, width=0.002):
    y = np.zeros(n)
    w = int(width * SR)
    y[:w] = rng(seed).uniform(-1, 1, w) * np.linspace(1, 0, w)
    return y


def place(total, parts):
    """Mix (offset_seconds, signal, gain) parts into a buffer of total seconds."""
    out = np.zeros(int(total * SR))
    for off, sig, gain in parts:
        i = int(off * SR)
        j = min(len(out), i + len(sig))
        if j > i:
            out[i:j] += sig[:j - i] * gain
    return out


def reverb(x, wet=0.25, size=1.0, seed=3):
    """Small Schroeder room: 4 combs + 2 all-passes, deterministic."""
    combs = [int(SR * d * size) for d in (0.0297, 0.0371, 0.0411, 0.0437)]
    y = np.zeros(len(x) + int(SR * 0.6 * size))
    xp = np.pad(x, (0, len(y) - len(x)))
    for c in combs:
        buf = np.zeros(len(y))
        g = 0.78
        for i in range(c, len(y)):
            buf[i] = xp[i - c] + g * buf[i - c]
        y += buf
    y /= len(combs)
    for d, g in ((int(SR * 0.005), 0.7), (int(SR * 0.0017), 0.7)):
        out = np.zeros(len(y))
        for i in range(len(y)):
            prev = y[i - d] if i >= d else 0.0
            prevo = out[i - d] if i >= d else 0.0
            out[i] = -g * y[i] + prev + g * prevo
        y = out
    del seed
    return xp * (1 - wet) + y * wet


def applause(dur, density, seed, shape=None):
    r = rng(seed)
    n = int(dur * SR)
    out = np.zeros(n)
    count = int(dur * density)
    for k in range(count):
        t0 = r.uniform(0, dur)
        if shape is not None and r.uniform() > shape(t0 / dur):
            continue
        clen = int(r.uniform(0.006, 0.014) * SR)
        c = r.normal(0, 1, clen) * np.exp(-np.arange(clen) / (clen / 4))
        c = band(np.pad(c, (0, 256)), r.uniform(700, 1200), r.uniform(2500, 5000))
        i = int(t0 * SR)
        j = min(n, i + len(c))
        out[i:j] += c[:j - i] * r.uniform(0.4, 1.0)
    return out


# ------------------------------------------------------------------ recipes (name -> signal)
def wand_shot():
    d = 0.22
    n = int(d * SR)
    zap = sine_sweep(d, 2600, 700) * expdecay(n, 0.05)
    zap += 0.4 * sine_sweep(d, 5200, 1400) * expdecay(n, 0.03)
    sparkle = bell(d, 3520, tau=0.12) * 0.35
    noise = band(rng(11).normal(0, 1, n), 3000, 9000) * expdecay(n, 0.015) * 0.5
    return zap + sparkle + noise + click(n, 12) * 0.6


def spark_hit():
    d = 0.32
    n = int(d * SR)
    pop = band(rng(21).normal(0, 1, n), 600, 5000) * expdecay(n, 0.03)
    tinkle = place(d, [(0.0, bell(0.3, 2793, tau=0.1), 0.4), (0.03, bell(0.25, 3729, tau=0.08), 0.3),
                       (0.06, bell(0.22, 4699, tau=0.06), 0.2)])
    return pop + tinkle


def whoosh(d, f0, f1, seed, tau=None):
    n = int(d * SR)
    w = sweep_filter(rng(seed).normal(0, 1, n), f0, f1)
    e = np.sin(np.pi * np.arange(n) / n) ** 2 if tau is None else expdecay(n, tau)
    return w * e


def wand_reload():
    d = 0.95
    knock = band(rng(31).normal(0, 1, int(0.05 * SR)), 300, 2500) * expdecay(int(0.05 * SR), 0.008)
    tone = np.sin(2 * np.pi * 420 * t_axis(0.08)) * expdecay(int(0.08 * SR), 0.02)
    return place(d, [(0.05, whoosh(0.28, 600, 3000, 32), 0.7), (0.3, whoosh(0.28, 700, 3400, 33), 0.7),
                     (0.62, knock, 0.9), (0.62, tone, 0.6), (0.66, bell(0.3, 2349, tau=0.15), 0.4),
                     (0.72, bell(0.25, 3136, tau=0.12), 0.3)])


def finger_snap(seed, gain=1.0):
    n = int(0.12 * SR)
    s = band(rng(seed).normal(0, 1, n), 1500, 7000) * expdecay(n, 0.012)
    s += np.sin(2 * np.pi * 1850 * np.arange(n) / SR) * expdecay(n, 0.008) * 0.5
    return s * gain


def paper_flick(seed):
    n = int(0.06 * SR)
    return band(rng(seed).normal(0, 1, n), 1800, 9000) * expdecay(n, 0.007)


def bolt_cast():
    d = 0.6
    n = int(d * SR)
    t = t_axis(d)
    f = 180 * (2.2 ** (t / d))
    hum = np.sin(2 * np.pi * np.cumsum(f) / SR + 2.0 * np.sin(2 * np.pi * f * 1.5 * t)) * env(n, 0.15, 0.4, 0.3, 0.1)
    return place(d, [(0.0, paper_flick(41), 0.9), (0.02, hum, 0.5), (0.35, bell(0.25, 1760, tau=0.1), 0.25)])


def bolt_snap():
    return place(0.35, [(0.0, finger_snap(51), 1.0), (0.04, bell(0.3, 2637, tau=0.1), 0.35)])


def bolt_hit():
    d = 0.7
    n = int(d * SR)
    thump = np.sin(2 * np.pi * np.cumsum(np.linspace(140, 45, n)) / SR) * expdecay(n, 0.09)
    crackle = band(rng(61).normal(0, 1, n), 800, 6000) * expdecay(n, 0.07) * (rng(62).random(n) > 0.6)
    shimmer = sine_sweep(d, 3000, 900) * expdecay(n, 0.25) * 0.25
    return thump + crackle * 0.6 + shimmer


def pad(d, freqs, seed, vib=5.0, formant=(800, 1150)):
    n = int(d * SR)
    t = t_axis(d)
    r = rng(seed)
    y = np.zeros(n)
    for f in freqs:
        for det in (-0.004, 0.0, 0.005):
            ph = r.uniform(0, 6.28)
            y += saw(d, f * (1 + det)) * 0.0 + np.sin(2 * np.pi * f * (1 + det) * t + 0.004 * f * np.sin(2 * np.pi * vib * t) / vib + ph)
            y += 0.3 * np.sin(2 * np.pi * 2 * f * (1 + det) * t + ph)
    breath = band(r.normal(0, 1, n), formant[0], formant[1]) * 0.6
    return y / (len(freqs) * 3) + breath * 0.15


def harp_gliss(notes, step, seed, dur=1.2):
    parts = []
    for i, f in enumerate(notes):
        parts.append((i * step, pluck(min(dur, 0.9), f, seed + i, 0.997), 0.5))
    return place(dur, parts)


def assistant_appear():
    d = 1.2
    n = int(d * SR)
    choir = pad(d, (523.25, 659.25, 783.99), 71) * env(n, 0.45, 0.6, 0.6, 0.35)
    gl = harp_gliss([392, 440, 523.25, 587.33, 659.25, 783.99, 880, 1046.5], 0.045, 72)
    return reverb(choir * 0.8 + gl * 0.6, 0.35)[:int(1.5 * SR)]


def swap():
    d = 0.45
    shuffle = np.concatenate([paper_flick(80 + i) * 0.5 for i in range(6)])
    shuffle = np.pad(shuffle, (0, max(0, int(0.3 * SR) - len(shuffle))))
    rev = whoosh(0.25, 3000, 500, 81)[::-1]
    return place(d, [(0.0, shuffle, 0.6), (0.05, rev, 0.6), (0.28, band(rng(82).normal(0, 1, int(0.08 * SR)), 300, 3000) * expdecay(int(0.08 * SR), 0.015), 0.9)])


def assistant_leave():
    d = 1.2
    n = int(d * SR)
    gl = harp_gliss([1046.5, 880, 783.99, 659.25, 587.33, 523.25, 440, 392], 0.05, 91)
    air = pad(d, (659.25, 783.99), 92) * env(n, 0.05, 0.9, 0.0, 0.2, curve=2.0)
    return reverb(gl * 0.6 + air * 0.5, 0.35)[:int(1.4 * SR)]


def hex_cast():
    d = 0.65
    n = int(d * SR)
    t = t_axis(d)
    boing = np.sin(2 * np.pi * np.cumsum(300 + 120 * np.sin(2 * np.pi * 9 * t) * np.exp(-t * 5)) / SR) * expdecay(n, 0.25)
    return place(d, [(0.0, whoosh(0.3, 500, 2500, 101), 0.6), (0.18, boing, 0.5), (0.2, bell(0.4, 1568, tau=0.2), 0.3)])


def smoke_poof(seed, d=0.6):
    n = int(d * SR)
    p = lowpass(rng(seed).normal(0, 1, n), 1400) * env(n, 0.01, 0.35, 0.0, 0.1, curve=4)
    return p * 2.5


def hex_poof():
    d = 0.75
    bwoop = sine_sweep(0.3, 220, 880) * env(int(0.3 * SR), 0.01, 0.25, 0.0, 0.05) * 0.6
    return place(d, [(0.0, smoke_poof(111), 1.0), (0.05, bwoop, 0.7), (0.1, bell(0.4, 2093, tau=0.12), 0.25)])


def hex_end():
    d = 0.55
    rev = smoke_poof(121, 0.4)[::-1]
    pop = band(rng(122).normal(0, 1, int(0.05 * SR)), 400, 4000) * expdecay(int(0.05 * SR), 0.01)
    return place(d, [(0.0, rev, 0.8), (0.4, pop, 1.0), (0.4, sine_sweep(0.12, 900, 300) * expdecay(int(0.12 * SR), 0.04), 0.5)])


def snap():
    return place(0.45, [(0.0, finger_snap(131, 1.2), 1.0), (0.05, whoosh(0.35, 3500, 400, 132), 0.6)])


def decoy_reveal():
    d = 0.7
    confetti = band(rng(141).normal(0, 1, int(0.5 * SR)), 2500, 9000) * (rng(142).random(int(0.5 * SR)) > 0.85) * expdecay(int(0.5 * SR), 0.18)
    return place(d, [(0.0, smoke_poof(143, 0.5), 0.9), (0.02, confetti, 0.6), (0.05, bell(0.5, 1318.5, tau=0.18), 0.3),
                     (0.08, bell(0.5, 1975.5, tau=0.15), 0.25)])


def brass(d, f, seed, bright=4000):
    n = int(d * SR)
    t = t_axis(d)
    y = np.zeros(n)
    for det in (-0.003, 0.0, 0.004):
        y += saw(d, f, det)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * t) * np.clip(t * 4, 0, 1)
    y = y * vib
    e = env(n, 0.03, 0.2, 0.7, 0.12)
    cutoff = bright
    return lowpass(y / 3, cutoff) * e


def chord(d, freqs, seed, bright=4000):
    return sum(brass(d, f, seed + i, bright) for i, f in enumerate(freqs)) / len(freqs)


def tada():
    d = 1.0
    ta = chord(0.14, (392, 493.88, 587.33), 151, 3000)
    da = chord(0.75, (523.25, 659.25, 783.99, 1046.5), 152, 5000)
    return reverb(place(d, [(0.0, ta, 0.9), (0.17, da, 1.0), (0.17, bell(0.6, 2093, tau=0.3), 0.15)]), 0.25)[:int(1.2 * SR)]


def refuse():
    d = 0.4
    a = lowpass(saw(0.14, 233.08) + saw(0.14, 233.08, 0.01), 1500) * env(int(0.14 * SR), 0.01, 0.1, 0.6, 0.03)
    b = lowpass(saw(0.18, 174.61) + saw(0.18, 174.61, 0.01), 1200) * env(int(0.18 * SR), 0.01, 0.12, 0.6, 0.05)
    return place(d, [(0.0, a, 0.5), (0.17, b, 0.5)])


def ovation():
    d = 1.8
    shape = lambda u: np.sin(np.pi * min(1.0, u * 1.3)) ** 0.6
    a = applause(d, 1400, 161, shape)
    cheer = pad(d, (440, 554.37), 162, vib=7, formant=(500, 1800)) * env(int(d * SR), 0.3, 1.0, 0.2, 0.4) * 0.15
    return reverb(a + cheer, 0.3)[:int(2.0 * SR)]


def ult_capture():
    d = 1.2
    n = int(d * SR)
    clunk = lowpass(rng(171).normal(0, 1, int(0.12 * SR)), 900) * expdecay(int(0.12 * SR), 0.02) * 2
    hum = (np.sin(2 * np.pi * 120 * t_axis(d)) * 0.5 + 0.25 * np.sin(2 * np.pi * 240 * t_axis(d))) * env(n, 0.08, 0.5, 0.5, 0.4)
    shine = sine_sweep(0.8, 800, 2400) * env(int(0.8 * SR), 0.3, 0.4, 0.2, 0.2) * 0.25
    return place(d, [(0.0, clunk, 1.0), (0.02, hum, 0.35), (0.25, shine, 1.0)])


def ult_card():
    return place(0.7, [(0.0, whoosh(0.3, 800, 4000, 181), 0.6), (0.28, paper_flick(182), 0.8),
                       (0.3, bell(0.4, 2637, tau=0.18), 0.4), (0.33, bell(0.4, 3951, tau=0.12), 0.3)])


def drumroll():
    d = 1.3
    parts = []
    r = rng(191)
    hits = int(d * 28)
    for k in range(hits):
        tt = k / 28 + r.uniform(-0.004, 0.004)
        hl = int(0.05 * SR)
        h = band(r.normal(0, 1, hl), 900, 7000) * expdecay(hl, 0.012) + np.sin(2 * np.pi * 200 * np.arange(hl) / SR) * expdecay(hl, 0.01) * 0.4
        parts.append((max(0, tt), h, 0.3 + 0.7 * (k / hits) ** 1.5))
    return place(d, parts)


def act_beam():
    d = 1.7
    n = int(d * SR)
    t = t_axis(d)
    buzz = sum(np.sin(2 * np.pi * f * t + 0.6 * np.sin(2 * np.pi * 3 * f * t)) for f in (330, 495, 660)) / 3
    shimmer = band(rng(201).normal(0, 1, n), 4000, 10000) * (0.5 + 0.5 * np.sin(2 * np.pi * 13 * t)) * 0.3
    swell = sine_sweep(0.3, 400, 1800) * env(int(0.3 * SR), 0.02, 0.25, 0.0, 0.05)
    body = (buzz * 0.5 + shimmer) * env(n, 0.25, 0.5, 0.7, 0.4)
    return place(d + 0.1, [(0.0, swell, 0.6), (0.1, body, 1.0)])


def act_nova_whirl():
    d = 0.9
    n = int(d * SR)
    t = t_axis(d)
    rate = 6 + 18 * t / d
    trem = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(rate) / SR)
    w = sweep_filter(rng(211).normal(0, 1, n), 400, 2600) * trem
    tone = sine_sweep(d, 220, 660) * 0.2
    return (w + tone) * env(n, 0.2, 0.5, 0.8, 0.05)


def cymbal(d, seed, tau=0.6):
    n = int(d * SR)
    return highpass(rng(seed).normal(0, 1, n), 5000) * expdecay(n, tau)


def act_nova_burst():
    d = 1.1
    n = int(0.6 * SR)
    boom = np.sin(2 * np.pi * np.cumsum(np.linspace(120, 35, n)) / SR) * expdecay(n, 0.15)
    flutter = np.concatenate([paper_flick(220 + i) * rng(240 + i).uniform(0.2, 0.7) for i in range(14)])
    return place(d, [(0.0, boom, 1.2), (0.0, smoke_poof(221, 0.5), 0.6), (0.02, cymbal(1.0, 222), 0.4), (0.05, flutter, 0.6)])


def act_cut_saw():
    d = 1.1
    n = int(0.75 * SR)
    t = np.arange(n) / SR
    strokes = np.abs(np.sin(2 * np.pi * 3.2 * t)) ** 2
    rasp = band(rng(231).normal(0, 1, n), 1200, 5000) * strokes * (1 + 0.5 * np.sin(2 * np.pi * 90 * t))
    shing = bell(0.5, 1244.5, partials=((1, 1), (2.41, 0.6), (3.87, 0.4), (5.9, 0.3)), tau=0.35)
    return place(d, [(0.0, rasp, 0.6), (0.72, shing, 0.6), (0.72, whoosh(0.25, 4000, 900, 232), 0.5)])


def card_hit():
    n = int(0.2 * SR)
    thwack = band(rng(241).normal(0, 1, n), 700, 6000) * expdecay(n, 0.018)
    return thwack + np.sin(2 * np.pi * 330 * np.arange(n) / SR) * expdecay(n, 0.02) * 0.3


def act_card_volley():
    return place(0.45, [(0.0, whoosh(0.14, 1200, 5000, 251), 0.7), (0.1, whoosh(0.14, 1300, 5200, 252), 0.7),
                        (0.2, whoosh(0.14, 1400, 5400, 253), 0.7)])


def act_curtain():
    d = 1.1
    rattle = np.concatenate([np.sin(2 * np.pi * 2600 * np.arange(400) / SR) * expdecay(400, 0.003) * rng(260 + i).uniform(0.3, 1) for i in range(10)])
    rattle = np.pad(rattle, (0, 2000))
    return place(d, [(0.0, whoosh(0.9, 180, 900, 261), 1.0), (0.05, lowpass(rng(262).normal(0, 1, int(0.8 * SR)), 500) * env(int(0.8 * SR), 0.2, 0.5, 0.0, 0.1), 0.6),
                     (0.0, rattle, 0.25)])


def act_curtain_open():
    d = 1.0
    swell = cymbal(0.6, 271, 2.0)[::-1] * np.linspace(0, 1, int(0.6 * SR))
    return place(d, [(0.0, whoosh(0.5, 900, 200, 272), 0.9), (0.0, swell, 0.4), (0.58, chord(0.35, (523.25, 659.25, 783.99), 273), 0.7)])


def act_chest():
    d = 1.1
    n = int(0.5 * SR)
    thud = lowpass(rng(281).normal(0, 1, n), 400) * expdecay(n, 0.06) * 2.5 + np.sin(2 * np.pi * np.cumsum(np.linspace(90, 40, n)) / SR) * expdecay(n, 0.12)
    creak_n = int(0.4 * SR)
    ct = np.arange(creak_n) / SR
    creak = np.sign(np.sin(2 * np.pi * np.cumsum(180 + 60 * np.sin(2 * np.pi * 3 * ct)) / SR)) * (rng(282).random(creak_n) > 0.3)
    creak = band(creak, 400, 2500) * env(creak_n, 0.05, 0.3, 0.2, 0.05)
    lock = np.concatenate([np.sin(2 * np.pi * 1900 * np.arange(300) / SR) * expdecay(300, 0.002) for _ in range(3)] + [np.zeros(600)] * 3)
    return place(d, [(0.0, whoosh(0.2, 300, 1200, 283), 0.4), (0.18, thud, 1.0), (0.45, lock, 0.4), (0.6, creak, 0.35)])


def act_sword():
    d = 0.55
    n = int(0.12 * SR)
    shing = bell(0.5, 1661.2, partials=((1, 1), (2.13, 0.7), (3.41, 0.5), (4.9, 0.35), (6.77, 0.2)), tau=0.3)
    stab = lowpass(rng(291).normal(0, 1, n), 1500) * expdecay(n, 0.02)
    return place(d, [(0.0, whoosh(0.15, 1500, 6000, 292), 0.5), (0.1, shing, 0.5), (0.12, stab, 0.8)])


def act_bow():
    d = 2.0
    shape = lambda u: np.clip(1.4 - u * 1.1, 0, 1)
    a = applause(1.9, 1100, 301, shape) * 0.7
    fan = chord(0.9, (523.25, 659.25, 783.99, 1046.5), 302, 4500)
    return reverb(place(d, [(0.0, cymbal(1.2, 303, 0.9), 0.35), (0.0, fan, 0.8), (0.1, a, 1.0)]), 0.3)[:int(2.2 * SR)]


RECIPES = {
    "wand_shot": (wand_shot, "Wand fires", -3.0),
    "spark_hit": (spark_hit, "Spark hits", -5.0),
    "wand_reload": (wand_reload, "Wand twirls", -5.0),
    "bolt_cast": (bolt_cast, "Vexing Bolt flicked", -3.0),
    "bolt_snap": (bolt_snap, "Bolt redirected", -3.0),
    "bolt_hit": (bolt_hit, "Vexing Bolt bursts", -2.0),
    "assistant_appear": (assistant_appear, "Savannah appears", -3.0),
    "swap": (swap, "Places swapped", -3.0),
    "assistant_leave": (assistant_leave, "Savannah takes her leave", -4.0),
    "hex_cast": (hex_cast, "Hat flourish", -3.0),
    "hex_poof": (hex_poof, "Turned into a rabbit", -2.0),
    "hex_end": (hex_end, "Rabbit hex wears off", -3.0),
    "snap": (snap, "Sinclair snaps and vanishes", -3.0),
    "decoy_reveal": (decoy_reveal, "Decoy vanishes", -3.0),
    "tada": (tada, "Ta-da!", -2.0),
    "refuse": (refuse, "No volunteer", -5.0),
    "ovation": (ovation, "Ovation", -4.0),
    "ult_capture": (ult_capture, "Spotlight on a volunteer", -2.0),
    "ult_card": (ult_card, "A card flies out", -3.0),
    "act_drumroll": (drumroll, "Drumroll", -3.0),
    "act_beam": (act_beam, "Footlight beam", -2.0),
    "act_nova_whirl": (act_nova_whirl, "Hat whirls", -3.0),
    "act_nova_burst": (act_nova_burst, "Hat bursts into cards", -1.5),
    "act_cut_saw": (act_cut_saw, "Sawing trick", -2.0),
    "card_hit": (card_hit, "Card strikes", -4.0),
    "act_card_volley": (act_card_volley, "Card volley", -3.0),
    "act_curtain": (act_curtain, "Curtain falls", -2.0),
    "act_curtain_open": (act_curtain_open, "Curtain flies open", -2.0),
    "act_chest": (act_chest, "Magic chest lands", -1.5),
    "act_sword": (act_sword, "Sword through the chest", -3.0),
    "act_bow": (act_bow, "Sinclair takes a bow", -2.0),
}


def render(name):
    fn, _, peak_db = RECIPES[name]
    y = np.asarray(fn(), dtype=np.float64)
    y = fade(y)
    y -= np.mean(y)
    peak = np.max(np.abs(y)) or 1.0
    return (y / peak * 10 ** (peak_db / 20)).astype(np.float32)


def encode(pcm, path):
    raw = path + ".f32"
    pcm.astype("<f4").tofile(raw)
    subprocess.run(["ffmpeg", "-v", "error", "-nostdin", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", raw,
                    "-c:a", "libvorbis", "-q:a", "5", "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", path],
                   check=True)
    os.remove(raw)


def decode(path):
    out = subprocess.run(["ffmpeg", "-v", "error", "-nostdin", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(out, dtype="<f4")


def update_sounds_json():
    with open(SOUNDS_JSON, encoding="utf-8") as fh:
        data = json.load(fh)
    for name in RECIPES:
        data["sinclair." + name] = {
            "category": "player",
            "subtitle": "subtitles.viltrumitecore.sinclair_" + name,
            "sounds": [{"name": "viltrumitecore:sinclair/" + name, "attenuation_distance": 32}],
        }
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    with open(SOUNDS_JSON, "w", encoding="utf-8") as fh:
        fh.write(text)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--wav", help="also write plain .ogg files here for listening")
    args = ap.parse_args()
    if args.check:
        print("sinclair.py is superseded by sinclair_cc0.py; nothing to check")
        return 0
    if not args.wav:
        ap.error("pass 1 is retired: use --wav DIR to listen, tools/sfx/sinclair_cc0.py to install")
    names = args.only or list(RECIPES)
    bad = []
    with tempfile.TemporaryDirectory() as tmp:
        for name in names:
            pcm = render(name)
            ogg = os.path.join(tmp, name + ".ogg")
            encode(pcm, ogg)
            target = os.path.join(OUT, name + ".ogg.b64")
            if args.check:
                if not os.path.isfile(target):
                    bad.append(name + " (missing)")
                    continue
                committed = os.path.join(tmp, name + "_c.ogg")
                with open(committed, "wb") as fh:
                    fh.write(base64.b64decode("".join(open(target).read().split())))
                a, b = decode(committed), decode(ogg)
                m = min(len(a), len(b))
                corr = float(np.dot(a[:m], b[:m]) / (np.linalg.norm(a[:m]) * np.linalg.norm(b[:m]) + 1e-9))
                if abs(len(a) - len(b)) > SR * 0.01 or corr < 0.98:
                    bad.append("%s (corr %.3f)" % (name, corr))
                continue
            with open(ogg, "rb") as fh:
                data = fh.read()
            if args.wav:
                os.makedirs(args.wav, exist_ok=True)
                with open(os.path.join(args.wav, name + ".ogg"), "wb") as fh:
                    fh.write(data)
            print("%-18s %5.2fs %6d bytes" % (name, len(pcm) / SR, len(data)))
    if args.check:
        for b in bad:
            print("stale:", b)
        return 1 if bad else 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
