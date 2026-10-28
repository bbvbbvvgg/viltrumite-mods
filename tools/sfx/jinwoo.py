#!/usr/bin/env python3
"""Sung Jinwoo sounds: original procedural synthesis (numpy only, no recordings), CC0 (own work).

Usage: python3 tools/sfx/jinwoo.py [--wav-dir DIR] [--package] [--only name,name]
  --wav-dir  where the rendered 44.1 kHz mono WAVs go (default: a temp dir)
  --package  convert each WAV with .agents/skills/sound-design/scripts/openverse_sfx.py package --own
             into viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/jinwoo/<name>.ogg.b64
Deterministic: every recipe seeds its own RNG. Parameters per file: tools/assets/jinwoo_sources.md.
"""
import argparse
import math
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

SR = 44100
ROOT = Path(__file__).resolve().parents[2]
PACKAGER = ROOT / '.agents/skills/sound-design/scripts/openverse_sfx.py'


# ------------------------------------------------------------------ primitives

def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def rng(seed):
    return np.random.default_rng(seed)


def noise(dur, seed, color='white'):
    r = rng(seed)
    x = r.standard_normal(int(dur * SR))
    if color == 'white':
        return x
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X = np.fft.rfft(x)
    f[0] = f[1]
    X *= (1 / np.sqrt(f)) if color == 'pink' else (1 / f)
    y = np.fft.irfft(X, len(x))
    return y / (np.std(y) + 1e-9)


def env_adsr(n, a, d, s, r, sustain=0.0):
    """Piecewise envelope over n samples: attack a, decay d to level s, hold, release r (seconds)."""
    t = np.arange(n) / SR
    dur = n / SR
    e = np.ones(n) * s
    e[t < a] = t[t < a] / max(a, 1e-4)
    m = (t >= a) & (t < a + d)
    e[m] = 1 - (1 - s) * (t[m] - a) / max(d, 1e-4)
    rel = t > dur - r
    e[rel] *= np.clip((dur - t[rel]) / max(r, 1e-4), 0, 1)
    return e


def exp_env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(0, t - attack) / max(decay, 1e-4))


def stft_filter(x, gain_fn, frame=1024, hop=256):
    """Time-varying spectral filter: gain_fn(time_s, freqs) -> gains (overlap-add, Hann)."""
    w = np.hanning(frame)
    pad = np.concatenate([np.zeros(frame), x, np.zeros(frame)])
    out = np.zeros_like(pad)
    norm = np.zeros_like(pad)
    freqs = np.fft.rfftfreq(frame, 1 / SR)
    for start in range(0, len(pad) - frame, hop):
        seg = pad[start:start + frame] * w
        tc = (start + frame / 2 - frame) / SR
        S = np.fft.rfft(seg) * gain_fn(tc, freqs)
        out[start:start + frame] += np.fft.irfft(S, frame) * w
        norm[start:start + frame] += w * w
    out = out / np.maximum(norm, 1e-6)
    return out[frame:frame + len(x)]


def band(fc, q):
    """Resonant band gain curve around fc with quality q."""
    def g(freqs):
        return 1.0 / (1.0 + ((freqs - fc) / (fc / q)) ** 2)
    return g


def lowpass(x, fc):
    return stft_filter(x, lambda t, f: 1 / (1 + (f / fc) ** 4))


def highpass(x, fc):
    return stft_filter(x, lambda t, f: 1 / (1 + (fc / np.maximum(f, 1)) ** 4))


def sweep_band(x, f0, f1, q, curve=1.0):
    dur = len(x) / SR

    def g(t, f):
        u = np.clip(t / dur, 0, 1) ** curve
        fc = f0 * (f1 / f0) ** u
        return 1.0 / (1.0 + ((f - fc) / (fc / q)) ** 2)
    return stft_filter(x, g)


def sine(freq, dur, phase=0.0):
    t = t_axis(dur)
    if callable(freq):
        ph = 2 * np.pi * np.cumsum(freq(t)) / SR
        return np.sin(ph + phase)
    return np.sin(2 * np.pi * freq * t + phase)


def saw(freq, dur, seed=0):
    t = t_axis(dur)
    f = freq(t) if callable(freq) else np.full_like(t, freq)
    ph = np.cumsum(f) / SR + rng(seed).random()
    return 2 * (ph - np.floor(ph + 0.5))


def partials(freqs, decays, dur, amps=None, seed=0):
    r = rng(seed)
    t = t_axis(dur)
    y = np.zeros_like(t)
    amps = amps or [1.0] * len(freqs)
    for f, d, a in zip(freqs, decays, amps):
        y += a * np.sin(2 * np.pi * f * t + r.random() * 6.28) * np.exp(-t / d)
    return y


def place(dst, src, at, gain=1.0):
    i = int(at * SR)
    if i >= len(dst):
        return dst
    n = min(len(src), len(dst) - i)
    dst[i:i + n] += src[:n] * gain
    return dst


def silence(dur):
    return np.zeros(int(dur * SR))


def fade(x, fin=0.003, fout=0.02):
    n = len(x)
    a = min(n, int(fin * SR))
    b = min(n, int(fout * SR))
    x = x.copy()
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def master(x, peak=0.85, drive=1.2):
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-9)
    x = np.tanh(x * drive) / np.tanh(drive)
    return fade(x * peak)


def reverb(x, size=0.35, mix=0.25, seed=7):
    """Cheap diffuse tail: convolution with exponentially decaying filtered noise."""
    ir = noise(size, seed)
    n = len(ir)
    ir = ir * np.exp(-np.arange(n) / (size * SR / 5))
    ir = lowpass(ir, 5000)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    wet = np.convolve(x, ir)
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    return dry * (1 - mix) + wet * mix


# ------------------------------------------------------------------ building blocks

def whoosh(dur, f0, f1, q=2.2, seed=1, attack=0.03, decay=None, color='white'):
    n = noise(dur, seed, color)
    y = sweep_band(n, f0, f1, q)
    return y * exp_env(len(y), attack, decay or dur / 3)


def blade_shimmer(dur, base, seed=0):
    return partials([base, base * 1.51, base * 2.27, base * 3.1], [0.05, 0.04, 0.03, 0.02], dur, [1, 0.6, 0.4, 0.25], seed)


def thud(dur, f0, f1, decay, seed=0):
    tone = sine(lambda t: f1 + (f0 - f1) * np.exp(-t / (decay * 0.5)), dur)
    body = lowpass(noise(dur, seed), f0 * 3)
    return (tone + 0.5 * body) * exp_env(int(dur * SR), 0.002, decay)


def click(dur, seed=0, hp=3000):
    return highpass(noise(dur, seed), hp) * exp_env(int(dur * SR), 0.0005, 0.008)


def drone(freqs, dur, seed=0, cutoff=1200, detune=0.006):
    y = np.zeros(int(dur * SR))
    for i, f in enumerate(freqs):
        for d in (-detune, 0, detune):
            y += saw(f * (1 + d), dur, seed + i * 3 + int(d * 1e4))
    return lowpass(y, cutoff)


def formant_voice(dur, f0, vowels, seed=0, growl=0.0):
    """Buzz through moving formant bands (an 'ah' choir or a growl)."""
    src = saw(f0, dur, seed) + 0.3 * noise(dur, seed + 1)
    if growl:
        t = t_axis(dur)
        src *= 1 + growl * np.sin(2 * np.pi * 31 * t) * rng(seed).uniform(0.6, 1.0)
    F = {'a': (800, 1150, 2900), 'o': (450, 800, 2830), 'u': (325, 700, 2530), 'e': (400, 1600, 2700)}

    def g(t, f):
        u = np.clip(t / dur, 0, 0.999) * (len(vowels) - 1)
        i = int(u)
        w = u - i
        a = np.array(F[vowels[i]])
        b = np.array(F[vowels[min(i + 1, len(vowels) - 1)]])
        fm = a * (1 - w) + b * w
        out = np.zeros_like(f)
        for k, fc in enumerate(fm):
            out += (1.0, 0.6, 0.3)[k] / (1 + ((f - fc) / (fc / 8)) ** 2)
        return out
    return stft_filter(src, g)


def heartbeat(dur, bpm, seed=0):
    y = silence(dur)
    beat = 60 / bpm
    t = 0.05
    while t < dur:
        place(y, thud(0.25, 90, 45, 0.07, seed), t, 1.0)
        place(y, thud(0.25, 80, 40, 0.06, seed + 1), t + 0.18, 0.6)
        t += beat
    return y


# ------------------------------------------------------------------ recipes (name -> signal)

def dagger_swing(v):
    f1 = (5200, 6400, 4600)[v]
    d = (0.26, 0.24, 0.3)[v]
    y = whoosh(d, 900, f1, 2.6, seed=10 + v, attack=0.035, decay=0.07)
    y += 0.25 * blade_shimmer(d, (3150, 3480, 2890)[v], seed=v) * exp_env(int(d * SR), 0.04, 0.05)
    return master(reverb(y, 0.2, 0.15), 0.8)


def dagger_hit(v):
    d = 0.32
    y = click(d, 20 + v, 2500) * 1.2
    y += thud(d, (190, 220, 170)[v], 70, 0.06, 30 + v) * 0.9
    y += 0.5 * partials([2100 + 90 * v, 3420, 5630, 7200], [0.09, 0.07, 0.05, 0.03], d, [1, 0.7, 0.5, 0.3], 40 + v)
    y += 0.35 * whoosh(d, 3000, 1500, 2, seed=50 + v, attack=0.001, decay=0.03)
    return master(reverb(y, 0.25, 0.2), 0.85, 1.6)


def spin():
    d = 0.55
    y = whoosh(d, 600, 3800, 2.0, seed=60, attack=0.12, decay=0.25)
    y += 0.8 * whoosh(d, 3800, 900, 2.0, seed=61, attack=0.2, decay=0.2)
    y += 0.2 * blade_shimmer(d, 3000, 62) * env_adsr(int(d * SR), 0.15, 0.2, 0.3, 0.1)
    return master(reverb(y, 0.3, 0.2))


def finisher():
    d = 0.9
    y = whoosh(d, 500, 5000, 2.0, seed=70, attack=0.18, decay=0.08)
    hit = thud(0.7, 140, 38, 0.22, 71) * 1.4 + click(0.7, 72, 1800) + 0.4 * partials([1800, 2900, 4300], [0.2, 0.15, 0.1], 0.7, seed=73)
    place(y, hit, 0.22)
    place(y, 0.3 * lowpass(noise(0.6, 74, 'brown'), 300) * exp_env(int(0.6 * SR), 0.01, 0.2), 0.24)
    return master(reverb(y, 0.5, 0.3), 0.88, 1.8)


def block():
    d = 0.35
    y = partials([880, 1490, 2310, 3150], [0.12, 0.09, 0.07, 0.05], d, [1, 0.8, 0.5, 0.3], 80) + click(d, 81, 1500) * 1.3
    y += 0.6 * thud(d, 160, 70, 0.05, 82)
    return master(reverb(y, 0.3, 0.2), 0.82, 1.5)


def parry():
    d = 0.8
    y = silence(d)
    pre = whoosh(0.12, 2000, 7000, 3, seed=90, attack=0.1, decay=0.02)
    place(y, pre, 0.0, 0.4)
    ring = partials([2480, 3990, 6320, 8100], [0.35, 0.28, 0.2, 0.12], 0.7, [1, 0.8, 0.6, 0.4], 91) + click(0.7, 92, 4000) * 1.2
    place(y, ring, 0.1)
    sparkle = highpass(noise(0.5, 93), 6000) * exp_env(int(0.5 * SR), 0.005, 0.12) * 0.3
    place(y, sparkle, 0.11)
    return master(reverb(y, 0.6, 0.35), 0.85)


def dagger_draw():
    d = 0.65
    smoke = lowpass(noise(d, 100, 'pink'), 900) * env_adsr(int(d * SR), 0.35, 0.1, 0.6, 0.15)
    form = sine(lambda t: 600 + 2600 * (t / d) ** 2, d) * env_adsr(int(d * SR), 0.3, 0.1, 0.5, 0.12) * 0.25
    y = smoke + form
    shing = partials([3300, 4970, 7100], [0.18, 0.12, 0.08], 0.35, seed=101) * 0.6 + whoosh(0.35, 2500, 7000, 3, seed=102, attack=0.01, decay=0.06)
    place(y, shing, 0.42)
    return master(reverb(y, 0.4, 0.25), 0.8)


def dagger_sheath():
    d = 0.45
    y = partials([3300, 4970], [0.06, 0.05], d, seed=110) * 0.4
    y += sine(lambda t: 2400 * np.exp(-t / 0.12) + 300, d) * exp_env(int(d * SR), 0.005, 0.12) * 0.25
    y += lowpass(noise(d, 111, 'pink'), 700) * exp_env(int(d * SR), 0.05, 0.15) * 0.8
    return master(reverb(y, 0.3, 0.2), 0.7)


def refuse():
    d = 0.3
    y = thud(d, 140, 90, 0.06, 120) + 0.4 * sine(lambda t: 420 - 600 * t, d) * exp_env(int(d * SR), 0.003, 0.08)
    return master(y, 0.7)


def step():
    d = 0.45
    y = whoosh(d, 300, 2400, 1.6, seed=130, attack=0.02, decay=0.1, color='pink') * 1.2
    y += thud(d, 110, 45, 0.08, 131) * 0.7
    y += 0.4 * whoosh(d, 4000, 1200, 2, seed=132, attack=0.05, decay=0.15)
    return master(reverb(y, 0.35, 0.25), 0.82)


def air_step():
    d = 0.32
    y = whoosh(d, 500, 2000, 1.5, seed=140, attack=0.015, decay=0.07, color='pink') + 0.5 * thud(d, 160, 80, 0.04, 141)
    return master(reverb(y, 0.25, 0.2), 0.7)


def landing():
    d = 0.45
    y = thud(d, 120, 40, 0.1, 150) * 1.3 + lowpass(noise(d, 151, 'brown'), 600) * exp_env(int(d * SR), 0.003, 0.12) * 0.6
    y += 0.3 * highpass(noise(d, 152), 2000) * exp_env(int(d * SR), 0.01, 0.06)
    return master(y, 0.85, 1.6)


def dive():
    d = 0.7
    y = whoosh(d, 400, 3200, 3.0, seed=160, attack=0.5, decay=0.4) + 0.3 * sine(lambda t: 500 + 1400 * t / d, d) * env_adsr(int(d * SR), 0.5, 0.1, 0.4, 0.1)
    return master(y, 0.8)


def mutilation():
    d = 1.5
    y = silence(d)
    for i in range(8):
        place(y, dagger_swing(i % 3) * 0.6, i * 0.1 + 0.02 * (i % 2))
        place(y, dagger_hit(i % 3) * 0.35, i * 0.1 + 0.05)
    place(y, finisher() * 0.9, 0.78)
    return master(y, 0.88, 1.4)


def authority_grip():
    d = 1.3
    t = t_axis(d)
    hum = drone([55, 82.5], d, 170, 500) * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * t))
    rise = sine(lambda tt: 180 + 160 * tt / d, d) * 0.3 + sine(lambda tt: 271 + 240 * tt / d, d) * 0.2
    y = (hum + rise) * env_adsr(len(t), 0.25, 0.2, 0.8, 0.25)
    y += 0.3 * sweep_band(noise(d, 171, 'pink'), 300, 1800, 4) * env_adsr(len(t), 0.4, 0.2, 0.7, 0.3)
    return master(reverb(y, 0.6, 0.3), 0.78)


def authority_slam():
    d = 0.9
    y = thud(d, 100, 30, 0.3, 180) * 1.5 + lowpass(noise(d, 181, 'brown'), 400) * exp_env(int(d * SR), 0.005, 0.3)
    y += 0.4 * whoosh(d, 2000, 300, 1.5, seed=182, attack=0.002, decay=0.1)
    return master(reverb(y, 0.6, 0.3), 0.9, 2.0)


def extract_channel():
    d = 1.7
    t = t_axis(d)
    breath = sweep_band(noise(d, 190, 'pink'), 400, 1400, 3) * env_adsr(len(t), 0.6, 0.3, 0.6, 0.4)
    choir = formant_voice(d, 73.4, 'uoa', 191) * 0.5 + formant_voice(d, 110, 'uoa', 192) * 0.35
    choir *= env_adsr(len(t), 0.8, 0.3, 0.8, 0.4)
    return master(reverb(breath * 0.6 + choir, 0.9, 0.4), 0.75)


def extract_rise():
    d = 1.3
    y = thud(d, 80, 28, 0.4, 200) * 1.4
    y += whoosh(d, 200, 2600, 1.8, seed=201, attack=0.25, decay=0.35, color='pink')
    y += 0.3 * partials([660, 990, 1485], [0.6, 0.5, 0.4], d, seed=202) * env_adsr(int(d * SR), 0.2, 0.3, 0.5, 0.4)
    return master(reverb(y, 0.8, 0.35), 0.88, 1.6)


def extract_fail():
    d = 0.7
    y = highpass(noise(d, 210), 1500) * exp_env(int(d * SR), 0.02, 0.25) * 0.5
    y += sine(lambda t: 330 * np.exp(-t / 0.4) + 80, d) * exp_env(int(d * SR), 0.01, 0.3) * 0.5
    return master(reverb(y, 0.5, 0.3), 0.7)


def exchange():
    d = 0.9
    n = noise(d, 220, 'pink')

    def g(t, f):
        fc = 200 + 900 * abs(math.sin(t * 9))
        return 1 / (1 + ((f - fc) / (fc / 6)) ** 2)
    y = stft_filter(n, g) * env_adsr(int(d * SR), 0.05, 0.2, 0.6, 0.3) * 1.5
    y += thud(d, 90, 40, 0.15, 221) * 0.6
    return master(reverb(y, 0.5, 0.3), 0.8)


def summon():
    d = 1.3
    y = silence(d)
    for i in range(4):
        place(y, whoosh(0.6, 150, 1500, 1.6, seed=230 + i, attack=0.25, decay=0.2, color='pink'), i * 0.15, 0.7)
    place(y, thud(0.8, 70, 30, 0.3, 235) * 1.3, 0.55)
    return master(reverb(y, 0.8, 0.35), 0.85, 1.5)


def recall():
    d = 0.85
    y = whoosh(d, 1800, 200, 1.6, seed=240, attack=0.5, decay=0.1, color='pink')
    place(y, thud(0.3, 110, 50, 0.06, 241) * 0.7, 0.6)
    return master(reverb(y, 0.4, 0.3), 0.78)


def horn(d, f0, seed):
    t = t_axis(d)
    vib = 1 + 0.01 * np.sin(2 * np.pi * 5.5 * t)
    y = saw(lambda tt: f0 * (1 + 0.01 * np.sin(2 * np.pi * 5.5 * tt)), d, seed) + 0.5 * saw(lambda tt: f0 * 1.5 * (1 + 0.01 * np.sin(2 * np.pi * 5.5 * tt)), d, seed + 1)
    del vib
    return lowpass(y, 900) * env_adsr(len(t), 0.06, 0.15, 0.7, 0.2)


def command_attack():
    d = 0.65
    y = horn(d, 98, 250) + 0.6 * click(d, 251, 2000) + 0.3 * partials([1960, 2940], [0.15, 0.1], d, seed=252)
    return master(reverb(y, 0.4, 0.3), 0.8, 1.6)


def command_guard():
    d = 0.65
    y = horn(d, 73.4, 260) + 0.5 * partials([880, 1490, 2310], [0.2, 0.15, 0.1], d, seed=261)
    return master(reverb(y, 0.5, 0.3), 0.78)


def command_sentinel():
    d = 0.7
    t = t_axis(d)
    y = (sine(392, d) * 0.4 + sine(587.3, d) * 0.25) * env_adsr(len(t), 0.15, 0.2, 0.5, 0.25)
    y += 0.4 * highpass(noise(d, 270, 'pink'), 2500) * env_adsr(len(t), 0.2, 0.2, 0.4, 0.2)
    return master(reverb(y, 0.7, 0.4), 0.7)


def monarch_charge():
    d = 1.3
    y = lowpass(noise(d, 280, 'brown'), 250) * env_adsr(int(d * SR), 0.4, 0.2, 0.8, 0.3) * 1.2
    for i in range(10):
        place(y, thud(0.2, 120, 60, 0.05, 281 + i) * 0.5, 0.2 + i * 0.08)
    y += whoosh(d, 300, 3000, 1.6, seed=295, attack=0.6, decay=0.3)
    return master(reverb(y, 0.6, 0.3), 0.88, 1.5)


def shadow_rise():
    d = 1.1
    y = lowpass(noise(d, 300, 'brown'), 300) * env_adsr(int(d * SR), 0.3, 0.3, 0.6, 0.4) * 1.3
    y += whoosh(d, 150, 1400, 1.5, seed=301, attack=0.5, decay=0.3, color='pink') * 0.8
    y += 0.25 * formant_voice(d, 65.4, 'uo', 302) * env_adsr(int(d * SR), 0.4, 0.3, 0.5, 0.3)
    return master(reverb(y, 0.7, 0.35), 0.8)


def shadow_dissolve():
    d = 0.75
    y = highpass(noise(d, 310, 'pink'), 1200) * exp_env(int(d * SR), 0.02, 0.3) * 0.6
    y += sine(lambda t: 260 * np.exp(-t / 0.5) + 60, d) * exp_env(int(d * SR), 0.01, 0.3) * 0.4
    return master(reverb(y, 0.5, 0.35), 0.65)


def shadow_slash():
    d = 0.32
    y = whoosh(d, 500, 3500, 2.2, seed=320, attack=0.04, decay=0.07) + 0.5 * thud(d, 150, 60, 0.05, 321)
    return master(reverb(y, 0.25, 0.2), 0.8)


def shadow_claw():
    d = 0.35
    y = silence(d)
    for i in range(2):
        s = highpass(sweep_band(noise(0.12, 330 + i), 5000, 2200, 4), 1500) * exp_env(int(0.12 * SR), 0.004, 0.03)
        place(y, s, i * 0.1)
    return master(y, 0.78)


def shadow_slam():
    d = 0.7
    y = thud(d, 90, 32, 0.25, 340) * 1.5 + lowpass(noise(d, 341, 'brown'), 500) * exp_env(int(d * SR), 0.004, 0.25)
    return master(reverb(y, 0.5, 0.3), 0.9, 2.0)


def shadow_roar():
    d = 1.3
    y = formant_voice(d, lambda t: 70 + 25 * np.sin(np.pi * t / d), 'oaau', 350, growl=0.8)
    y += 0.5 * formant_voice(d, lambda t: 104 + 30 * np.sin(np.pi * t / d), 'oaa', 351, growl=0.9)
    y *= env_adsr(len(y), 0.12, 0.3, 0.8, 0.35)
    return master(reverb(y, 0.6, 0.3), 0.85, 1.8)


def ult_charge():
    d = 2.2
    t = t_axis(d)
    swell = drone([41.2, 61.7, 82.4], d, 400, 300 + 0) * (t / d) ** 1.5
    swell += sweep_band(noise(d, 401, 'pink'), 200, 2200, 2) * (t / d) ** 2 * 0.6
    y = swell + heartbeat(d, 84, 402) * 1.1
    return master(reverb(y, 0.8, 0.3), 0.86, 1.4)


def ult_rise():
    d = 2.6
    y = silence(d)
    place(y, thud(1.5, 70, 24, 0.5, 410) * 1.6 + click(1.5, 411, 900) * 0.8, 0.0)
    choir = (formant_voice(d, 110, 'oaa', 412) + formant_voice(d, 130.8, 'oaa', 413) * 0.8 + formant_voice(d, 164.8, 'oae', 414) * 0.6
             + formant_voice(d, 55, 'ooa', 415) * 0.7)
    choir *= env_adsr(len(choir), 0.5, 0.5, 0.75, 0.8)
    place(y, choir * 0.6, 0.05)
    return master(reverb(y, 1.2, 0.45), 0.9, 1.5)


def ult_roar():
    d = 2.2
    y = silence(d)
    r = rng(420)
    for i in range(14):
        f0 = r.uniform(60, 140)
        dur = r.uniform(0.9, 1.6)
        g = formant_voice(dur, f0, ''.join(r.choice(list('oau'), 3)), 421 + i, growl=r.uniform(0.5, 1.0))
        g *= env_adsr(len(g), 0.1, 0.3, 0.7, 0.3)
        place(y, g, r.uniform(0, 0.5), r.uniform(0.4, 0.8))
    y += lowpass(noise(d, 440, 'brown'), 300) * env_adsr(int(d * SR), 0.3, 0.4, 0.6, 0.5) * 0.8
    return master(reverb(y, 1.0, 0.4), 0.9, 1.6)


def ult_wave():
    d = 1.8
    y = lowpass(noise(d, 450, 'brown'), 220) * env_adsr(int(d * SR), 0.15, 0.4, 0.7, 0.6) * 1.4
    y += whoosh(d, 200, 2500, 1.4, seed=451, attack=0.3, decay=0.6, color='pink')
    y += thud(d, 75, 26, 0.5, 452)
    return master(reverb(y, 1.0, 0.4), 0.9, 1.6)


RECIPES = {
    'dagger_swing_1': lambda: dagger_swing(0), 'dagger_swing_2': lambda: dagger_swing(1), 'dagger_swing_3': lambda: dagger_swing(2),
    'dagger_hit_1': lambda: dagger_hit(0), 'dagger_hit_2': lambda: dagger_hit(1), 'dagger_hit_3': lambda: dagger_hit(2),
    'spin': spin, 'finisher': finisher, 'block': block, 'parry': parry, 'dagger_draw': dagger_draw, 'dagger_sheath': dagger_sheath,
    'refuse': refuse, 'step': step, 'air_step': air_step, 'landing': landing, 'dive': dive, 'mutilation': mutilation,
    'authority_grip': authority_grip, 'authority_slam': authority_slam, 'extract_channel': extract_channel, 'extract_rise': extract_rise,
    'extract_fail': extract_fail, 'exchange': exchange, 'summon': summon, 'recall': recall, 'command_attack': command_attack,
    'command_guard': command_guard, 'command_sentinel': command_sentinel, 'monarch_charge': monarch_charge, 'shadow_rise': shadow_rise,
    'shadow_dissolve': shadow_dissolve, 'shadow_slash': shadow_slash, 'shadow_claw': shadow_claw, 'shadow_slam': shadow_slam,
    'shadow_roar': shadow_roar, 'ult_charge': ult_charge, 'ult_rise': ult_rise, 'ult_roar': ult_roar, 'ult_wave': ult_wave,
}


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with wave.open(str(path), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--wav-dir', type=Path)
    ap.add_argument('--package', action='store_true')
    ap.add_argument('--only')
    a = ap.parse_args(argv)
    names = a.only.split(',') if a.only else list(RECIPES)
    tmp = tempfile.TemporaryDirectory(prefix='jinwoo-sfx-')
    out = a.wav_dir or Path(tmp.name)
    out.mkdir(parents=True, exist_ok=True)
    for name in names:
        x = RECIPES[name]()
        p = out / f'{name}.wav'
        write_wav(p, x)
        print(f'{name}: {len(x) / SR:.2f}s peak {np.max(np.abs(x)):.2f} rms {np.sqrt(np.mean(x ** 2)):.3f}')
        if a.package:
            subprocess.run([sys.executable, str(PACKAGER), 'package', '--input', str(p), '--module', 'core', '--name', f'jinwoo/{name}',
                            '--event', f'jinwoo_{name}', '--own', '--replace', '--repo', str(ROOT)], check=True, capture_output=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
