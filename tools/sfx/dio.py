#!/usr/bin/env python3
"""DIO / The World: fully original procedural SFX (own synthesis, CC0-1.0). No external samples.

Usage: python3 tools/sfx/dio.py [--check] [--wav DIR]
Deterministic numpy synthesis -> mono 44.1 kHz 16-bit -> ffmpeg libvorbis (bitexact) -> base64
text assets in core binassets (decoded by Gradle decodeBinaryAssets). --check re-synthesises in a
temporary directory and compares decoded PCM with the committed files. --wav DIR also writes the
raw mixes for listening. Each cue documents its trigger in tools/assets/dio_sources.md.
"""
import argparse
import base64
import hashlib
import os
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/dio"
SR = 44100


# --- primitives ------------------------------------------------------------------------------

def rng(seed):
    return np.random.default_rng(seed)


def tt(dur):
    return np.arange(int(dur * SR)) / SR


def noise(dur, seed):
    return rng(seed).standard_normal(int(dur * SR))


def env(t, attack, decay, hold=0.0):
    a = np.clip(t / max(attack, 1e-4), 0, 1) ** 2
    d = np.exp(-np.maximum(t - attack - hold, 0) / max(decay, 1e-4))
    return a * d


def fade(x, fin=0.004, fout=0.02):
    n = len(x)
    x = x.copy()
    i, o = int(fin * SR), int(fout * SR)
    if i:
        x[:i] *= np.linspace(0, 1, i)
    if o:
        x[n - o:] *= np.linspace(1, 0, o)
    return x


def sweep(t, f0, f1, curve=1.0, shape="sin"):
    """Sine (or saw) with frequency gliding f0->f1 over the duration (power curve)."""
    u = (t / t[-1]) ** curve
    f = f0 + (f1 - f0) * u
    ph = 2 * np.pi * np.cumsum(f) / SR
    if shape == "saw":
        return 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    return np.sin(ph)


def spectral(x, gain_fn, frame=2048):
    """Time-varying linear-phase filter: STFT with a gain(time_s, freqs) mask, overlap-add."""
    hop = frame // 4
    win = np.hanning(frame)
    pad = np.concatenate([np.zeros(frame), x, np.zeros(frame)])
    out = np.zeros_like(pad)
    norm = np.zeros_like(pad)
    freqs = np.fft.rfftfreq(frame, 1 / SR)
    for s in range(0, len(pad) - frame, hop):
        seg = pad[s:s + frame] * win
        g = gain_fn((s + frame / 2 - frame) / SR, freqs)
        out[s:s + frame] += np.fft.irfft(np.fft.rfft(seg) * g, frame) * win
        norm[s:s + frame] += win ** 2
    out /= np.maximum(norm, 1e-6)
    return out[frame:frame + len(x)]


def band(lo, hi, soft=1.5):
    """Smooth band mask factory in octaves."""
    def g(f):
        f = np.maximum(f, 1.0)
        a = 1 / (1 + (lo / f) ** (2 * soft)) if lo > 0 else 1.0
        b = 1 / (1 + (f / hi) ** (2 * soft)) if hi > 0 else 1.0
        return a * b
    return g


def filt(x, lo=0, hi=0, soft=1.5):
    m = band(lo, hi, soft)
    return spectral(x, lambda _t, f: m(f))


def bandsweep(x, lo0, hi0, lo1, hi1, curve=1.0, soft=1.5):
    dur = len(x) / SR

    def g(t, f):
        u = np.clip(t / dur, 0, 1) ** curve
        return band(lo0 + (lo1 - lo0) * u, hi0 + (hi1 - hi0) * u, soft)(f)
    return spectral(x, g)


def resonator(x, freq, q):
    """Two-pole resonant bandpass (one channel, python loop on a short buffer)."""
    w = 2 * np.pi * freq / SR
    r = np.exp(-w / (2 * q))
    a1, a2 = -2 * r * np.cos(w), r * r
    y = np.zeros_like(x)
    y1 = y2 = 0.0
    g = (1 - r)
    for i in range(len(x)):
        v = g * x[i] - a1 * y1 - a2 * y2
        y[i] = v
        y2, y1 = y1, v
    return y


def ring(t, partials, decay, seed=0, detune=0.002):
    """Inharmonic metal: sum of decaying partials (freq, amp, decay scale)."""
    r = rng(seed)
    out = np.zeros_like(t)
    for f, a, d in partials:
        f2 = f * (1 + r.uniform(-detune, detune))
        out += a * np.sin(2 * np.pi * f2 * t + r.uniform(0, 6.28)) * np.exp(-t / (decay * d))
    return out


def thump(t, f0, f1, decay, drop=0.04):
    f = f1 + (f0 - f1) * np.exp(-t / drop)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / decay)


def reverb(x, size, seed, mix=0.3, tone=4000, wrap=False):
    n = int(size * SR)
    t = np.arange(n) / SR
    ir = rng(seed).standard_normal(n) * np.exp(-t / (size / 5))
    ir = filt(ir, 150, tone)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    if wrap:
        L = len(x)
        y = np.real(np.fft.ifft(np.fft.fft(x) * np.fft.fft(ir[:L] if n >= L else np.pad(ir, (0, L - n)))))
    else:
        L = len(x) + n
        y = np.fft.irfft(np.fft.rfft(x, L) * np.fft.rfft(ir, L), L)[:len(x)]
    return x * (1 - mix) + y * mix * 2.0


def place(out, x, at):
    i = int(at * SR)
    j = min(len(out), i + len(x))
    out[i:j] += x[:j - i]
    return out


def tanh(x, drive):
    return np.tanh(x * drive) / np.tanh(drive)


def finish(x, peak_db=-2.0, fin=0.002, fout=0.03):
    x = fade(x - np.mean(x), fin, fout)
    p = np.max(np.abs(x)) + 1e-9
    return x * (10 ** (peak_db / 20) / p)


# --- cues --------------------------------------------------------------------------------------

def punch(dur, f0, f1, body_decay, crack, seed, sub=1.0):
    t = tt(dur)
    low = thump(t, f0, f1, body_decay) * sub
    mid = filt(noise(dur, seed), 200, 1800) * env(t, 0.001, body_decay * 0.6)
    snap = filt(noise(dur, seed + 1), 2500, 9000) * env(t, 0.0005, 0.012) * crack
    return tanh(low * 0.9 + mid * 0.5 + snap, 1.8)


def whoosh(dur, f_lo, f_hi, seed, peak_at=0.45, down=True):
    t = tt(dur)
    n = noise(dur, seed)
    a, b = (f_hi, f_lo) if down else (f_lo, f_hi)
    x = bandsweep(n, a * 0.6, a * 1.6, b * 0.6, b * 1.6, soft=1.2)
    u = t / dur
    e = np.where(u < peak_at, (u / peak_at) ** 2, np.exp(-(u - peak_at) * 7 / (1 - peak_at)))
    return x * e


def stand_summon():
    d = 1.0
    t = tt(d)
    swell = bandsweep(noise(d, 10), 80, 400, 600, 7000, curve=0.7) * (t / d) ** 2 * np.exp(-np.maximum(t - 0.62, 0) * 9)
    boom = thump(np.maximum(t - 0.6, 0), 120, 38, 0.32) * (t > 0.6)
    gold = ring(np.maximum(t - 0.6, 0), [(523, 1, 1), (1312, .6, .7), (2093, .45, .5), (3341, .3, .35), (4870, .2, .25)], 0.5, 11) * (t > 0.6)
    shimmer = filt(noise(d, 12), 5000, 12000) * env(np.maximum(t - 0.6, 0), 0.01, 0.15) * (t > 0.6)
    x = swell * 0.55 + boom * 0.9 + gold * 0.35 + shimmer * 0.25
    return finish(reverb(x, 1.2, 13, 0.3))


def stand_dismiss():
    d = 0.7
    t = tt(d)
    suck = bandsweep(noise(d, 20), 3000, 9000, 120, 600, curve=0.6) * env(t, 0.05, 0.25)
    gold = ring(t, [(2093, .5, 1), (1312, .6, .8), (784, .8, .7)], 0.25, 21)
    gold *= np.exp(-t / 0.2)
    x = suck * 0.8 + gold * 0.25
    return finish(reverb(x, 0.8, 22, 0.25))


def stand_whoosh():
    return finish(whoosh(0.28, 500, 3500, 30, 0.35) , -4)


def stand_hit():
    d = 0.32
    t = tt(d)
    x = punch(d, 160, 55, 0.07, 0.9, 40)
    x += whoosh(d, 800, 3000, 41, 0.05) * 0.15
    return finish(reverb(x, 0.35, 42, 0.15))


def stand_hook():
    d = 0.5
    t = tt(d)
    x = punch(d, 140, 42, 0.12, 1.0, 50, sub=1.2)
    crunch = filt(noise(d, 51), 400, 3500) * env(t, 0.001, 0.05) * (1 + 0.8 * np.sin(2 * np.pi * 37 * t))
    x = tanh(x + crunch * 0.4, 1.5)
    return finish(reverb(x, 0.6, 52, 0.2))


def barrage_hit():
    d = 0.14
    return finish(punch(d, 200, 70, 0.035, 0.7, 60), -3)


def barrage_rush():
    d = 0.7
    out = np.zeros(int(d * SR))
    r = rng(70)
    tpos = 0.0
    k = 0
    while tpos < d - 0.08:
        h = punch(0.09, 180 + r.uniform(-30, 40), 65, 0.025, 0.5, 71 + k) * r.uniform(0.5, 1.0)
        w = whoosh(0.07, 700, 2600, 200 + k, 0.6) * 0.5
        place(out, h * 0.7, tpos)
        place(out, w, max(tpos - 0.03, 0))
        tpos += 0.038 + r.uniform(-0.006, 0.006)
        k += 1
    t = tt(d)
    out *= 0.75 + 0.25 * np.sin(2 * np.pi * 6 * t)
    air = filt(noise(d, 79), 300, 2000) * 0.12
    return finish(reverb(out + air, 0.4, 78, 0.2), -2, 0.004, 0.06)


def finisher():
    d = 1.1
    t = tt(d)
    pre = whoosh(0.2, 400, 2500, 80, 0.85, down=False)
    x = np.zeros_like(t)
    place(x, pre * 0.5, 0.0)
    hit = punch(0.9, 120, 32, 0.25, 1.2, 81, sub=1.4)
    crack = filt(noise(0.9, 82), 1500, 12000) * env(tt(0.9), 0.0005, 0.05)
    boom = filt(noise(0.9, 83), 30, 300) * env(tt(0.9), 0.003, 0.3)
    place(x, tanh(hit + crack * 0.6 + boom * 0.8, 2.2), 0.18)
    return finish(reverb(x, 1.0, 84, 0.25))


def knife_draw():
    d = 0.4
    t = tt(d)
    scr = bandsweep(noise(d, 90), 2500, 5000, 5000, 11000) * env(t, 0.03, 0.12)
    scr *= 1 + 0.5 * np.sin(2 * np.pi * 55 * t)
    tink = ring(np.maximum(t - 0.12, 0), [(3520, 1, 1), (5280, .6, .6), (7900, .4, .4)], 0.18, 91) * (t > 0.12)
    return finish(scr * 0.6 + tink * 0.35, -4)


def knife_throw():
    d = 0.4
    out = np.zeros(int(d * SR))
    for i in range(5):
        w = whoosh(0.22, 1800, 6500, 100 + i, 0.25) * (0.7 + 0.06 * i)
        place(out, w, i * 0.022)
    return finish(out, -3)


def knife_hit():
    d = 0.25
    t = tt(d)
    th = thump(t, 300, 110, 0.04) * 0.7
    tick = ring(t, [(2900, 1, 1), (4630, .6, .6), (6900, .4, .4)], 0.06, 110) * 0.4
    sn = filt(noise(d, 111), 2000, 8000) * env(t, 0.0005, 0.008)
    return finish(th + tick + sn * 0.5, -3)


def knife_stop():
    d = 0.6
    t = tt(d)
    # a ring that freezes: the decay halts at 0.08 s and holds as a thin standing tone
    hold = np.where(t < 0.08, np.exp(-t / 0.05), np.exp(-0.08 / 0.05) * np.exp(-(t - 0.08) / 0.5))
    tone = sum(a * np.sin(2 * np.pi * f * t) for f, a in ((2637, 1), (3951, .5), (5274, .3))) * hold
    rev = bandsweep(noise(d, 120), 6000, 12000, 2000, 6000) * env(t, 0.002, 0.03)
    return finish(tone * 0.5 + rev * 0.3, -5)


def time_stop():
    d = 3.2
    t = tt(d)
    # 0-0.55 s reversed swell, 0.55 s the world "snaps", then the inversion wave and a hollow drone.
    rise = bandsweep(noise(d, 130), 200, 1500, 2000, 12000, curve=0.5) * np.where(t < 0.55, (t / 0.55) ** 3, 0)
    snap_t = np.maximum(t - 0.55, 0)
    on = (t >= 0.55).astype(float)
    sn = np.zeros_like(t)
    place(sn, punch(1.6, 90, 28, 0.45, 1.0, 131, sub=1.5), 0.55)
    wave_ = bandsweep(noise(d, 132), 4000, 12000, 60, 400, curve=0.4) * env(snap_t, 0.02, 0.6) * on
    drone = (sweep(t, 55, 41, shape="saw") * 0.5 + np.sin(2 * np.pi * 27.5 * t)) * env(snap_t, 0.3, 1.6) * on
    drone = filt(drone, 20, 500)
    bell = ring(snap_t, [(220, 1, 1), (440 * 1.003, .5, .8), (660 * 0.997, .4, .6), (1317, .3, .4), (2205, .2, .3)], 1.2, 133) * on
    x = rise * 0.5 + sn * 1.0 + wave_ * 0.5 + drone * 0.45 + bell * 0.3
    # the inverted tail: everything above 2 kHz is sucked out after 1.2 s
    x = spectral(x, lambda s, f: band(0, 12000 - 10500 * np.clip((s - 1.0) / 1.0, 0, 1))(f))
    return finish(reverb(x, 2.0, 134, 0.35), -1.5, 0.002, 0.3)


def time_tick():
    """Seamless 1 s clock loop: tick at 0.0 s, tock at 0.5 s, circular reverb (no seam)."""
    d = 1.0
    t = tt(d)
    out = np.zeros_like(t)
    for at, f, seed in ((0.0, 2400, 140), (0.5, 1800, 141)):
        c = tt(0.08)
        click = resonator(noise(0.08, seed) * env(c, 0.0003, 0.004), f, 12) * 6
        click += ring(c, [(f * 0.5, 1, 1), (f * 0.77, .5, .6)], 0.02, seed) * 0.4
        place(out, click, at + 0.001)
    out = reverb(out, 0.6, 142, 0.35, 5000, wrap=True)
    out -= np.mean(out)
    return out * (10 ** (-6 / 20) / (np.max(np.abs(out)) + 1e-9))


def time_resume():
    d = 1.4
    t = tt(d)
    wind = np.zeros_like(t)
    for i in range(7):  # ratchet clicks accelerating
        at = 0.6 * (1 - (1 - i / 7) ** 1.6)
        c = tt(0.05)
        place(wind, resonator(noise(0.05, 150 + i) * env(c, 0.0002, 0.003), 3000 - 120 * i, 10) * 4, at)
    rel = bandsweep(noise(d, 158), 60, 400, 1500, 12000, curve=0.8) * np.where(t < 0.7, (t / 0.7) ** 2, np.exp(-(t - 0.7) / 0.25))
    pop = np.zeros_like(t)
    place(pop, punch(0.6, 70, 140, 0.2, 0.6, 159), 0.68)
    x = wind * 0.4 + rel * 0.6 + pop * 0.7
    return finish(reverb(x, 1.0, 160, 0.3))


def ledger_burst():
    d = 0.6
    out = np.zeros(int(d * SR))
    r = rng(170)
    for i in range(6):
        place(out, punch(0.4, 150 + r.uniform(-20, 60), 45, 0.08, 1.0, 171 + i) * r.uniform(0.6, 1), i * 0.012 + r.uniform(0, 0.006))
    t = tt(d)
    out += filt(noise(d, 179), 200, 6000) * env(t, 0.001, 0.08) * 0.6
    return finish(reverb(tanh(out, 1.6), 0.6, 178, 0.25))


def drain_grab():
    d = 0.45
    t = tt(d)
    grip = thump(t, 120, 60, 0.06)
    wet = filt(noise(d, 180), 150, 1500) * env(t, 0.002, 0.12)
    wet = spectral(wet, lambda s, f: 1 + 3 * np.exp(-((f - (500 + 1500 * s)) / 150) ** 2))
    return finish(grip * 0.8 + wet * 0.6, -3)


def drain_pulse():
    d = 0.6
    t = tt(d)
    beat = np.zeros_like(t)
    place(beat, thump(tt(0.2), 75, 45, 0.05), 0.0)
    place(beat, thump(tt(0.2), 70, 42, 0.06) * 0.7, 0.18)
    suck = filt(noise(d, 190), 200, 2500) * env(t, 0.12, 0.15)
    suck = spectral(suck, lambda s, f: 1 + 4 * np.exp(-((f - (1200 - 900 * min(s / 0.5, 1))) / 120) ** 2))
    return finish(beat * 1.0 + suck * 0.35, -3)


def roller_appear():
    d = 1.8
    t = tt(d)
    # incoming whistle from above plus a diesel engine that chugs into existence
    whistle = sweep(t, 2400, 600, 1.4) * env(t, 0.3, 0.6) * 0.35
    rpm = 9 + 6 * np.clip(t / 1.2, 0, 1)
    ph = 2 * np.pi * np.cumsum(rpm) / SR
    chug = (0.5 + 0.5 * np.sin(ph)) ** 6
    engine = filt(noise(d, 200), 40, 400) * chug * 1.5 + filt(sweep(t, 48, 62, shape="saw"), 30, 300) * 0.5
    engine *= np.clip(t / 0.5, 0, 1)
    rattle = filt(noise(d, 201), 800, 3500) * chug * 0.25
    x = whistle + engine * 0.7 + rattle
    return finish(reverb(x, 1.0, 202, 0.2), -2, 0.01, 0.2)


def roller_slam():
    d = 1.5
    t = tt(d)
    boom = thump(t, 80, 25, 0.5) * 1.3
    low = filt(noise(d, 210), 25, 250) * env(t, 0.002, 0.45)
    clank = ring(t, [(180, 1, 1), (412, .8, .8), (733, .7, .6), (1190, .5, .5), (1874, .4, .35), (2650, .3, .25)], 0.5, 211, 0.01)
    debris = np.zeros_like(t)
    r = rng(212)
    for i in range(40):
        c = tt(0.04)
        place(debris, filt(noise(0.04, 300 + i), 1000, 7000) * env(c, 0.0005, 0.006) * r.uniform(0.2, 0.7), 0.05 + r.exponential(0.25))
    x = tanh(boom + low * 0.9 + clank * 0.5, 2.0) + debris * 0.5
    return finish(reverb(x, 1.4, 213, 0.3))


def roller_punch():
    d = 0.2
    t = tt(d)
    clang = ring(t, [(310, 1, 1), (705, .8, .7), (1220, .6, .5), (1980, .4, .35)], 0.07, 220, 0.02)
    return finish(tanh(punch(d, 170, 60, 0.04, 0.8, 221) * 0.8 + clang * 0.5, 1.6), -3)


def roller_explode():
    d = 2.6
    t = tt(d)
    blast = filt(noise(d, 230), 20, 6000) * env(t, 0.003, 0.18)
    body = filt(noise(d, 231), 20, 600) * env(t, 0.01, 0.7)
    rumble = filt(noise(d, 232), 15, 120) * env(t, 0.05, 1.1)
    sub = thump(t, 60, 22, 0.6)
    crackle = np.zeros_like(t)
    r = rng(233)
    for i in range(60):
        c = tt(0.02)
        place(crackle, filt(noise(0.02, 400 + i), 1500, 9000) * np.exp(-c / 0.004) * r.uniform(0.1, 0.5), 0.1 + r.exponential(0.5))
    metal = ring(t, [(150, 1, 1), (390, .7, .7), (870, .5, .5)], 0.6, 234, 0.02) * 0.25
    x = tanh(blast * 1.2 + body + rumble * 1.2 + sub * 0.9, 2.5) + crackle * 0.4 + metal
    return finish(reverb(x, 2.0, 235, 0.3), -1.0, 0.002, 0.4)


def wryyy():
    """A non-verbal, synthesised vampiric screech (no voice recording): saw glottis + moving formants."""
    d = 1.9
    t = tt(d)
    f0 = 210 + 70 * np.clip(t / 0.4, 0, 1) + 10 * np.sin(2 * np.pi * 6.5 * t) * np.clip((t - 0.3) / 0.5, 0, 1)
    ph = 2 * np.pi * np.cumsum(f0) / SR
    src = sum(np.sin(k * ph) / k for k in range(1, 40))
    src += filt(noise(d, 240), 1500, 8000) * 0.25  # rasp
    # formants glide "w" -> "rr" -> "ii"
    def g(s, f):
        u = np.clip(s / 0.5, 0, 1)
        f1 = 300 + 0 * u
        f2 = 700 + 1700 * u
        f3 = 2600 + 600 * u
        return (np.exp(-((f - f1) / 90) ** 2) + 0.8 * np.exp(-((f - f2) / 140) ** 2) + 0.5 * np.exp(-((f - f3) / 200) ** 2) + 0.03)
    x = spectral(src, g)
    x = tanh(x / (np.max(np.abs(x)) + 1e-9) * 1.0, 2.5)
    x *= env(t, 0.06, 0.9, hold=0.6)
    return finish(reverb(x, 1.2, 241, 0.3), -2, 0.01, 0.25)


def guard():
    d = 0.35
    t = tt(d)
    plate = ring(t, [(620, 1, 1), (1340, .6, .7), (2210, .4, .5)], 0.12, 250, 0.01)
    x = thump(t, 110, 60, 0.07) * 0.7 + plate * 0.35 + whoosh(d, 300, 1400, 251, 0.1) * 0.3
    return finish(x, -3)


def deflect():
    d = 0.35
    t = tt(d)
    ping = ring(t, [(1760, 1, 1), (2790, .7, .7), (4180, .5, .5), (6120, .3, .3)], 0.15, 260, 0.004)
    sn = filt(noise(d, 261), 3000, 11000) * env(t, 0.0005, 0.01)
    return finish(ping * 0.6 + sn * 0.6 + thump(t, 200, 90, 0.03) * 0.4, -3)


def refuse():
    d = 0.22
    t = tt(d)
    buzz = filt(sweep(t, 150, 110, shape="saw"), 80, 1200) * env(t, 0.005, 0.07)
    return finish(buzz, -8)


def sun_sizzle():
    d = 0.9
    t = tt(d)
    hiss = filt(noise(d, 270), 3000, 12000) * (0.6 + 0.4 * np.sin(2 * np.pi * 3.1 * t) ** 2)
    pops = np.zeros_like(t)
    r = rng(271)
    for i in range(55):
        c = tt(0.006)
        place(pops, filt(noise(0.006, 500 + i), 2000, 12000) * np.exp(-c / 0.0012) * r.uniform(0.3, 1.0), r.uniform(0, d - 0.01))
    return finish((hiss * 0.35 + pops) * env(t, 0.04, 0.5, hold=0.2), -6, 0.02, 0.15)


CUES = {
    "stand_summon": stand_summon, "stand_dismiss": stand_dismiss, "stand_whoosh": stand_whoosh,
    "stand_hit": stand_hit, "stand_hook": stand_hook, "barrage_hit": barrage_hit, "barrage_rush": barrage_rush,
    "finisher": finisher, "knife_draw": knife_draw, "knife_throw": knife_throw, "knife_hit": knife_hit,
    "knife_stop": knife_stop, "time_stop": time_stop, "time_tick": time_tick, "time_resume": time_resume,
    "ledger_burst": ledger_burst, "drain_grab": drain_grab, "drain_pulse": drain_pulse,
    "roller_appear": roller_appear, "roller_slam": roller_slam, "roller_punch": roller_punch,
    "roller_explode": roller_explode, "wryyy": wryyy, "guard": guard, "deflect": deflect, "refuse": refuse,
    "sun_sizzle": sun_sizzle,
}


# --- packaging ---------------------------------------------------------------------------------

def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def encode(wav, ogg):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-map_metadata", "-1", "-fflags", "+bitexact",
                    "-flags:a", "+bitexact", "-c:a", "libvorbis", "-q:a", "2", "-ac", "1", "-ar", str(SR), str(ogg)], check=True)


def decoded_pcm(ogg):
    return subprocess.run(["ffmpeg", "-v", "error", "-i", str(ogg), "-f", "s16le", "-ac", "1", "-"], check=True,
                          capture_output=True).stdout


def build(tmp, wav_dir=None):
    res = {}
    for name, fn in CUES.items():
        x = fn()
        assert np.isfinite(x).all() and np.max(np.abs(x)) > 0.05, name
        w = Path(tmp) / (name + ".wav")
        o = Path(tmp) / (name + ".ogg")
        write_wav(w, x)
        if wav_dir:
            os.makedirs(wav_dir, exist_ok=True)
            write_wav(Path(wav_dir) / (name + ".wav"), x)
        encode(w, o)
        res[name] = o
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--wav")
    a = ap.parse_args()
    with tempfile.TemporaryDirectory() as tmp:
        built = build(tmp, a.wav)
        bad = 0
        for name, ogg in built.items():
            dst = OUT / (name + ".ogg.b64")
            if a.check:
                if not dst.exists():
                    print("missing", dst); bad += 1; continue
                committed = Path(tmp) / (name + ".committed.ogg")
                committed.write_bytes(base64.b64decode(dst.read_text()))
                if hashlib.sha256(decoded_pcm(committed)).digest() != hashlib.sha256(decoded_pcm(ogg)).digest():
                    print("differs", name); bad += 1
            else:
                OUT.mkdir(parents=True, exist_ok=True)
                dst.write_bytes(base64.encodebytes(ogg.read_bytes()))
        print("%d cues %s" % (len(built), "checked" if a.check else "written"))
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
