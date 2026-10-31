#!/usr/bin/env python3
"""Chitauri invasion: fully original procedural SFX (own synthesis, CC0-1.0). No external samples, no vanilla sounds.

Cue sheet and triggers: tools/assets/chitauri_sources.md. Events: viltrumitecore/src/main/sound-overlays/chitauri.json
(merged into sounds.json at build), registry: worldevent/chitauri/ChitauriSounds, subtitles: lang-overlays/chitauri.

Usage: python3 tools/sfx/chitauri.py [--check] [--wav DIR] [--only NAME ...]
Deterministic numpy synthesis -> mono 44.1 kHz 16-bit -> ffmpeg libvorbis (bitexact) -> base64 text assets in core
binassets (decoded by Gradle decodeBinaryAssets). --check re-synthesises in a temporary directory and compares the
decoded PCM with the committed files. --wav DIR also writes the raw mixes for listening. Loops (LOOPS) are built
periodic: noise is shaped in the frequency domain of exactly one loop length and every tone and modulation makes a
whole number of cycles per loop, so the end sample runs straight into the first one.
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
OUT = ROOT / "viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/chitauri"
SR = 44100


# --- primitives (same family as tools/sfx/dio.py) ----------------------------------------------

def rng(seed):
    return np.random.default_rng(seed)


def tt(dur):
    return np.arange(int(round(dur * SR))) / SR


def noise(dur, seed):
    return rng(seed).standard_normal(int(round(dur * SR)))


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


def osc(freq, shape="sin", phase0=0.0):
    """Oscillator from an instantaneous frequency array."""
    ph = 2 * np.pi * np.cumsum(freq) / SR + phase0
    if shape == "saw":
        return 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    if shape == "sqr":
        return np.sign(np.sin(ph))
    return np.sin(ph)


def glide(t, f0, f1, curve=1.0):
    u = (t / max(t[-1], 1e-9)) ** curve
    return f0 + (f1 - f0) * u


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


def formant(x, peaks, frame=2048):
    """Static vocal-tract colour: sum of resonance bumps (freq, bandwidth, gain) applied as a spectral mask."""
    def g(_t, f):
        m = np.full_like(f, 0.05)
        for fc, bw, gain in peaks:
            m += gain / (1 + ((f - fc) / (bw / 2)) ** 2)
        return m
    return spectral(x, g, frame)


def moving_formant(x, track, frame=2048):
    """track(u) -> list of (freq, bw, gain) for u in 0..1 over the clip."""
    dur = len(x) / SR

    def g(t, f):
        m = np.full_like(f, 0.04)
        for fc, bw, gain in track(np.clip(t / dur, 0, 1)):
            m += gain / (1 + ((f - fc) / (bw / 2)) ** 2)
        return m
    return spectral(x, g, frame)


def ring(t, partials, decay, seed=0, detune=0.002):
    r = rng(seed)
    out = np.zeros_like(t)
    for f, a, d in partials:
        f2 = f * (1 + r.uniform(-detune, detune))
        out += a * np.sin(2 * np.pi * f2 * t + r.uniform(0, 6.28)) * np.exp(-t / (decay * d))
    return out


def thump(t, f0, f1, decay, drop=0.04):
    f = f1 + (f0 - f1) * np.exp(-t / drop)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / decay)


def reverb(x, size, seed, mix=0.3, tone=4000):
    n = int(size * SR)
    t = np.arange(n) / SR
    ir = rng(seed).standard_normal(n) * np.exp(-t / (size / 5))
    ir = filt(ir, 150, tone)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    L = len(x) + n
    y = np.fft.irfft(np.fft.rfft(x, L) * np.fft.rfft(ir, L), L)[:len(x)]
    return x * (1 - mix) + y * mix * 2.0


def place(out, x, at):
    i = int(at * SR)
    j = min(len(out), i + len(x))
    if j > i:
        out[i:j] += x[:j - i]
    return out


def tanh(x, drive):
    return np.tanh(x * drive) / np.tanh(drive)


def finish(x, fin=0.002, fout=0.03):
    x = fade(x - np.mean(x), fin, fout)
    return x / (np.max(np.abs(x)) + 1e-9)


def crackle(dur, rate, seed, lo=2000, hi=11000, length=0.004):
    """Random electric pops (Poisson) of short band-passed noise."""
    out = np.zeros(int(round(dur * SR)))
    r = rng(seed)
    n = max(1, int(dur * rate))
    pop = tt(length)
    for i in range(n):
        at = r.uniform(0, dur)
        p = noise(length, seed * 1000 + i) * np.exp(-pop / (length / 4)) * r.uniform(0.2, 1.0)
        place(out, p, at)
    return filt(out, lo, hi)


# --- loop primitives (periodic over exactly one loop length) ----------------------------------

def pnoise(dur, seed, lo, hi, soft=1.5):
    """Periodic band-limited noise: random spectrum of one loop length, inverse FFT (wraps seamlessly)."""
    n = int(round(dur * SR))
    r = rng(seed)
    spec = np.fft.rfft(r.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= band(lo, hi, soft)(f)
    x = np.fft.irfft(spec, n)
    return x / (np.std(x) + 1e-12)


def ptone(t, f, dur, amp=1.0, vib=0.0, vib_rate=0.0, shape="sin", phase0=0.0):
    """Tone with a whole number of cycles per loop; vibrato rate is snapped to whole cycles too."""
    f = round(f * dur) / dur
    if vib_rate:
        vib_rate = max(1, round(vib_rate * dur)) / dur
        freq = f + vib * f * np.sin(2 * np.pi * vib_rate * t)
    else:
        freq = np.full_like(t, f)
    ph = 2 * np.pi * (np.cumsum(freq) - freq[0]) / SR + phase0
    if shape == "saw":
        return amp * (2 * ((ph / (2 * np.pi)) % 1.0) - 1)
    return amp * np.sin(ph)


def plfo(t, rate, dur, phase=0.0):
    rate = max(1, round(rate * dur)) / dur
    return np.sin(2 * np.pi * rate * t + phase)


# --- weapons -----------------------------------------------------------------------------------

def rifle_fire(seed=10, f0=2300, f1=280):
    """Soldier energy rifle, one shot of a burst: plasma zap (falling saw with FM), crack, low kick, short tail."""
    d = 0.5
    t = tt(d)
    fm = 1 + 0.18 * np.sin(2 * np.pi * 95 * t)
    zap = osc(glide(t, f0, f1, 0.35) * fm, "saw") * env(t, 0.002, 0.08)
    zap = bandsweep(zap, 600, 9000, 150, 2500, 0.5)
    crack = filt(noise(d, seed), 1800, 12000) * env(t, 0.0004, 0.01)
    kick = thump(t, 160, 55, 0.06, 0.015)
    sizzle = crackle(d, 90, seed + 1, 3000, 11000) * env(t, 0.01, 0.12)
    x = zap * 0.8 + crack * 0.9 + kick * 0.7 + sizzle * 0.5
    return finish(reverb(tanh(x, 1.6), 0.35, seed + 2, 0.15), 0.001, 0.05)


def rifle_fire_2():
    return rifle_fire(20, 2050, 240)


def rifle_charge():
    """The charge-up before a burst (~0.6 s): rising whine with a tremolo that speeds up, crackle building."""
    d = 0.65
    t = tt(d)
    u = t / d
    f = glide(t, 320, 2100, 1.6)
    trem_rate = 8 + 26 * u ** 1.5
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(trem_rate) / SR)
    whine = (osc(f) * 0.7 + osc(f * 2.01) * 0.25 + osc(f * 0.5, "saw") * 0.2) * trem
    hiss = bandsweep(noise(d, 31), 800, 3000, 3000, 10000) * 0.25
    pops = crackle(d, 70, 32) * u ** 2
    x = (whine + hiss + pops) * np.clip(u * 1.4, 0, 1) ** 2
    x *= np.where(u < 0.93, 1.0, np.clip((1 - u) / 0.07, 0, 1))
    return finish(x, 0.01, 0.01)


def bolt_impact(seed=40):
    """A bolt hits a surface: crack, electric sizzle and a small thump."""
    d = 0.55
    t = tt(d)
    crack = filt(noise(d, seed), 1200, 11000) * env(t, 0.0004, 0.015)
    body = filt(noise(d, seed + 1), 250, 2500) * env(t, 0.001, 0.05)
    sizzle = (filt(noise(d, seed + 2), 4000, 12000) * 0.4 + crackle(d, 140, seed + 3)) * env(t, 0.004, 0.16)
    kick = thump(t, 130, 50, 0.07, 0.02)
    zap = osc(glide(t, 1500, 400, 0.4), "saw") * env(t, 0.001, 0.035) * 0.3
    x = crack + body * 0.6 + sizzle * 0.7 + kick * 0.8 + zap
    return finish(reverb(tanh(x, 1.5), 0.4, seed + 4, 0.18), 0.001, 0.06)


def bolt_impact_2():
    return bolt_impact(50)


def cannon_fire():
    """Chariot cannon: heavy plasma discharge, deep kick, rolling electric tail."""
    d = 0.95
    t = tt(d)
    fm = 1 + 0.25 * np.sin(2 * np.pi * 47 * t)
    zap = osc(glide(t, 1100, 90, 0.4) * fm, "saw") * env(t, 0.002, 0.14)
    zap = bandsweep(zap, 300, 7000, 80, 1500, 0.6)
    kick = thump(t, 110, 34, 0.16, 0.03)
    blast = filt(noise(d, 61), 150, 4000) * env(t, 0.001, 0.09)
    crack = filt(noise(d, 62), 2000, 12000) * env(t, 0.0004, 0.012)
    tail = crackle(d, 60, 63, 1500, 9000) * env(t, 0.05, 0.3)
    x = zap * 0.8 + kick * 1.1 + blast * 0.6 + crack * 0.7 + tail * 0.5
    return finish(reverb(tanh(x, 1.8), 0.7, 64, 0.22), 0.001, 0.08)


def explosion(seed=70, low=70):
    """Energy explosion (cannon bolts, chariot hits): boom, falling blast noise, electric crackle, debris."""
    d = 1.7
    t = tt(d)
    boom = thump(t, low, 26, 0.45, 0.06)
    blast = spectral(noise(d, seed), lambda tm, f: band(30, 7000 * np.exp(-tm / 0.35) + 300)(f)) * env(t, 0.002, 0.35)
    crack = filt(noise(d, seed + 1), 1500, 12000) * env(t, 0.0005, 0.02)
    zap = osc(glide(t, 900, 120, 0.3), "saw") * env(t, 0.002, 0.12) * 0.25
    elec = crackle(d, 80, seed + 2, 2000, 10000) * env(t, 0.05, 0.45)
    bits = np.zeros_like(t)
    r = rng(seed + 3)
    for i in range(14):
        c = tt(0.03)
        place(bits, filt(noise(0.03, seed * 50 + i), 900, 6000) * env(c, 0.0005, 0.008) * r.uniform(0.1, 0.4), 0.25 + r.exponential(0.35))
    x = boom * 1.2 + blast * 0.9 + crack * 0.6 + zap + elec * 0.45 + bits
    return finish(reverb(tanh(x, 2.0), 1.0, seed + 4, 0.25, 3000), 0.001, 0.15)


def explosion_2():
    return explosion(80, 62)


# --- chariots ----------------------------------------------------------------------------------

def chariot_engine():
    """Chariot engine loop (2 s, seamless): turbine whine with harmonics, jet rush, a rough low drone."""
    d = 2.0
    t = tt(d)
    whine = ptone(t, 410, d, 0.5, 0.006, 3.0) + ptone(t, 820, d, 0.22, 0.006, 3.0) + ptone(t, 1235, d, 0.12, 0.004, 3.0)
    whine += ptone(t, 2470, d, 0.05, 0.003, 4.0)
    whine *= 0.85 + 0.15 * plfo(t, 6.0, d)
    rush = pnoise(d, 101, 300, 2600) * 0.5 * (0.8 + 0.2 * plfo(t, 2.0, d, 1.0))
    hiss = pnoise(d, 102, 3000, 9000) * 0.12
    drone = ptone(t, 62, d, 0.45, 0.0, 0.0, "saw")
    drone = np.real(np.fft.irfft(np.fft.rfft(drone) * band(30, 400)(np.fft.rfftfreq(len(drone), 1 / SR)), len(drone)))
    drone *= 0.75 + 0.25 * plfo(t, 11.0, d)
    x = whine * 0.6 + rush + hiss + drone * 0.6
    x = np.tanh(x * 1.2)
    return x / (np.max(np.abs(x)) + 1e-9)


def chariot_flyby():
    """A chariot screams past: engine tone with a Doppler drop, swelling rush and a whoosh."""
    d = 1.9
    t = tt(d)
    u = t / d
    peak = 0.42
    dop = 1.0 + 0.32 * np.tanh((peak - u) * 9)
    whine = osc(410 * dop) * 0.5 + osc(820 * dop) * 0.25 + osc(1235 * dop) * 0.12
    drone = filt(osc(62 * dop, "saw"), 30, 400) * 0.5
    rush = spectral(noise(d, 111), lambda tm, f: band(250, 1500 + 5000 * np.exp(-((tm / d - peak) / 0.12) ** 2))(f))
    e = np.where(u < peak, (u / peak) ** 2.2, np.exp(-(u - peak) * 5.5 / (1 - peak)))
    x = (whine * 0.6 + drone + rush * 0.9) * e
    return finish(np.tanh(x * 1.3), 0.05, 0.12)


def chariot_hit():
    """A chariot takes a hit: hollow alien-metal clank, sparks."""
    d = 0.6
    t = tt(d)
    clank = ring(t, [(520, 1.0, 1.0), (787, 0.7, 0.7), (1290, 0.5, 0.5), (2110, 0.35, 0.35), (3370, 0.2, 0.25)], 0.12, 121)
    hit = filt(noise(d, 122), 400, 6000) * env(t, 0.0005, 0.012)
    sparks = crackle(d, 120, 123, 3000, 12000) * env(t, 0.003, 0.12)
    kick = thump(t, 180, 70, 0.05, 0.01)
    x = clank * env(t, 0.0005, 0.2) * 0.8 + hit + sparks * 0.6 + kick * 0.6
    return finish(reverb(tanh(x, 1.5), 0.3, 124, 0.12), 0.001, 0.05)


def chariot_explode():
    """A chariot blows apart: big energy blast, shrieking metal, shrapnel and a falling engine whine."""
    d = 2.3
    t = tt(d)
    blast = explosion(130, 58)
    x = np.zeros_like(t)
    place(x, blast * 1.0, 0.0)
    shriek = ring(t, [(310, 1.0, 1.0), (468, 0.8, 0.9), (733, 0.6, 0.7), (1180, 0.4, 0.5), (1913, 0.25, 0.4)], 0.5, 131, 0.01)
    shriek *= env(t, 0.003, 0.6)
    whine = osc(glide(t, 900, 160, 0.7)) * env(t, 0.01, 0.9) * 0.25
    shrap = np.zeros_like(t)
    r = rng(132)
    for i in range(24):
        c = tt(0.06)
        part = ring(c, [(r.uniform(1800, 5200), 1.0, 1.0), (r.uniform(2500, 7000), 0.5, 0.6)], 0.02, 900 + i) + filt(noise(0.06, 950 + i), 2000, 9000) * np.exp(-c / 0.005)
        place(shrap, part * r.uniform(0.1, 0.35), 0.15 + r.exponential(0.45))
    x += shriek * 0.35 + whine + shrap
    return finish(reverb(tanh(x, 1.6), 1.2, 133, 0.25, 3500), 0.001, 0.2)


# --- soldiers ----------------------------------------------------------------------------------

def _clicks(dur, n, seed, f_lo=1400, f_hi=3400):
    out = np.zeros(int(round(dur * SR)))
    r = rng(seed)
    at = 0.02
    for i in range(n):
        c = tt(0.02)
        fc = r.uniform(f_lo, f_hi)
        click = ring(c, [(fc, 1.0, 1.0), (fc * 1.53, 0.4, 0.6)], 0.004, seed * 10 + i) + noise(0.02, seed * 20 + i) * np.exp(-c / 0.0012) * 0.5
        place(out, click * r.uniform(0.5, 1.0), at)
        at += r.uniform(0.018, 0.06)
        if at > dur - 0.03:
            break
    return out


def soldier_chatter(seed=200, base=105):
    """Hive chatter: mandible click trains over a throaty, formant-shaped alien growl."""
    d = 0.95
    t = tt(d)
    r = rng(seed)
    f = base * (1 + 0.15 * np.sin(2 * np.pi * r.uniform(3, 6) * t) + 0.25 * glide(t, 0, 1, 0.8) * r.choice([-1, 1]))
    jitter = 1 + 0.02 * filt(noise(d, seed + 1), 0, 40) * 5
    growl = osc(f * jitter, "saw") + 0.35 * osc(f * 2 * jitter, "saw")
    rough = filt(noise(d, seed + 2), 300, 3500) * 0.35
    voice = formant(growl + rough, [(520, 260, 1.0), (1150, 300, 0.7), (2550, 500, 0.45)])
    syll = np.zeros_like(t)
    a = 0.0
    while a < d:
        ln = r.uniform(0.09, 0.22)
        seg = (t >= a) & (t < a + ln)
        syll[seg] = np.sin(np.pi * (t[seg] - a) / ln) ** 0.7
        a += ln + r.uniform(0.02, 0.09)
    clicks = _clicks(d, 16, seed + 3)
    x = voice * syll * 0.8 + clicks * 0.7
    return finish(reverb(tanh(x, 1.8), 0.35, seed + 4, 0.15), 0.01, 0.06)


def soldier_chatter_2():
    return soldier_chatter(210, 92)


def soldier_chatter_3():
    return soldier_chatter(220, 124)


def soldier_hurt(seed=230, top=640):
    """Short pained screech: rising-falling rough vocal with clicks."""
    d = 0.45
    t = tt(d)
    u = t / d
    f = 260 + (top - 260) * np.sin(np.pi * np.clip(u * 1.3, 0, 1)) ** 1.5
    v = osc(f, "saw") + 0.5 * osc(f * 1.5, "saw") + filt(noise(d, seed), 500, 5000) * 0.6
    v = formant(v, [(800, 400, 1.0), (1700, 500, 0.8), (3100, 700, 0.5)])
    x = v * env(t, 0.008, 0.16) + _clicks(d, 5, seed + 1) * 0.4
    return finish(tanh(x, 2.2), 0.003, 0.05)


def soldier_hurt_2():
    return soldier_hurt(240, 560)


def soldier_death():
    """Death: a falling screech that chokes off, then armour clatter and the body hitting the ground."""
    d = 1.2
    t = tt(d)
    u = t / 0.7
    f = 540 * np.exp(-np.clip(u, 0, 1) * 1.5) + 90
    v = osc(f, "saw") + 0.4 * osc(f * 1.5, "saw") + filt(noise(d, 251), 400, 4000) * 0.5
    v = formant(v, [(700, 400, 1.0), (1500, 500, 0.7), (2800, 700, 0.4)]) * env(t, 0.01, 0.25, 0.15) * (t < 0.75)
    clatter = np.zeros_like(t)
    r = rng(252)
    for i in range(6):
        c = tt(0.12)
        part = ring(c, [(r.uniform(700, 1600), 1.0, 1.0), (r.uniform(1800, 3800), 0.5, 0.5)], 0.03, 260 + i)
        place(clatter, part * r.uniform(0.3, 0.7), 0.55 + i * r.uniform(0.04, 0.09))
    body = thump(tt(0.4), 120, 45, 0.08, 0.02) + filt(noise(0.4, 253), 100, 1500) * env(tt(0.4), 0.001, 0.05)
    place(clatter, body * 0.9, 0.6)
    x = v * 0.9 + clatter
    return finish(reverb(tanh(x, 1.7), 0.5, 254, 0.18), 0.005, 0.1)


def soldier_step(seed=270):
    """Armoured footstep: heel thud, a hard metal tick of the greave, gravel."""
    d = 0.2
    t = tt(d)
    r = rng(seed)
    thud = thump(t, 120, 60, 0.03, 0.01)
    tick = ring(t, [(r.uniform(2400, 3200), 1.0, 1.0), (r.uniform(3800, 5200), 0.6, 0.6)], 0.012, seed + 1) * 0.5
    grit = filt(noise(d, seed + 2), 900, 7000) * env(t, 0.001, 0.025) * 0.6
    x = np.zeros_like(t)
    place(x, tick, 0.004)
    x += thud + grit
    return finish(tanh(x, 1.4), 0.0005, 0.03)


def soldier_step_2():
    return soldier_step(280)


def soldier_step_3():
    return soldier_step(290)


def soldier_melee():
    """Bayonet slash: fast swish, then the blade biting with a metallic scrape."""
    d = 0.5
    t = tt(d)
    u = t / 0.22
    swish = bandsweep(noise(d, 301), 400, 1500, 2500, 8000, 0.7) * np.where(u < 1, np.sin(np.pi * np.clip(u, 0, 1)) ** 2, 0)
    bite = np.zeros_like(t)
    c = tt(0.28)
    blade = ring(c, [(1720, 1.0, 1.0), (2630, 0.7, 0.7), (4150, 0.4, 0.4)], 0.06, 302) * env(c, 0.0005, 0.1)
    scrape = filt(noise(0.28, 303), 2500, 9000) * env(c, 0.002, 0.05) * 0.6
    hit = thump(c, 140, 60, 0.05, 0.01)
    place(bite, blade * 0.5 + scrape + hit * 0.7, 0.17)
    x = swish * 0.8 + bite
    return finish(reverb(tanh(x, 1.4), 0.3, 304, 0.12), 0.002, 0.05)


# --- Leviathan ---------------------------------------------------------------------------------

def leviathan_roar():
    """The Leviathan's roar (~3.8 s): giant throat growl, metallic overtone groan and a whale-like screech, huge room."""
    d = 3.8
    t = tt(d)
    u = t / d
    shape = np.clip(u / 0.12, 0, 1) ** 1.5 * np.clip((1 - u) / 0.45, 0, 1) ** 1.2
    f = 52 + 16 * np.sin(np.pi * np.clip(u * 1.1, 0, 1)) + 3 * filt(noise(d, 401), 0, 8) * 20
    growl = osc(f, "saw") + 0.6 * osc(f * 2.02, "saw") + 0.3 * osc(f * 3.01, "saw")
    growl += filt(noise(d, 402), 60, 900) * 0.8
    growl = moving_formant(growl, lambda v: [(260 + 260 * np.sin(np.pi * v), 180, 1.0), (720 + 300 * v, 300, 0.6), (1900, 600, 0.3)])
    sf = 520 + 360 * np.sin(np.pi * np.clip((u - 0.15) / 0.7, 0, 1)) ** 1.3
    screech = osc(sf * (1 + 0.012 * np.sin(2 * np.pi * 5.5 * t)), "saw") + 0.4 * osc(sf * 1.498, "saw")
    screech = formant(screech, [(1200, 500, 1.0), (2600, 800, 0.6)]) * np.clip((u - 0.12) / 0.15, 0, 1) * np.clip((0.85 - u) / 0.3, 0, 1)
    groan = ring(t, [(97, 1.0, 1.0), (151, 0.8, 0.8), (233, 0.6, 0.7), (377, 0.4, 0.5)], 3.0, 403, 0.004)
    groan *= 0.5 + 0.5 * np.sin(2 * np.pi * 1.7 * t) ** 2
    breath = filt(noise(d, 404), 200, 3000) * 0.4
    x = (growl * 1.0 + screech * 0.45 + groan * 0.4 + breath) * shape
    x = tanh(x, 2.2)
    return finish(reverb(x, 2.6, 405, 0.35, 2500), 0.02, 0.4)


def leviathan_loop():
    """Leviathan presence loop (4 s, seamless): deep breathing rumble, plate creaks and a slow mechanical groan."""
    d = 4.0
    t = tt(d)
    breath = pnoise(d, 411, 25, 220) * (0.55 + 0.45 * plfo(t, 0.5, d)) ** 2
    air = pnoise(d, 412, 250, 1600) * 0.25 * (0.5 + 0.5 * plfo(t, 0.5, d, 0.4)) ** 3
    groan = ptone(t, 38.0, d, 0.6, 0.02, 0.25) + ptone(t, 57.0, d, 0.4, 0.02, 0.25) + ptone(t, 76.0, d, 0.2, 0.015, 0.5, "saw")
    groan *= 0.7 + 0.3 * plfo(t, 0.75, d, 2.0)
    creak = np.zeros_like(t)
    r = rng(413)
    for i in range(5):
        at = r.uniform(0.2, d - 0.4)
        c = tt(0.35)
        part = ring(c, [(r.uniform(180, 320), 1.0, 1.0), (r.uniform(400, 700), 0.6, 0.7)], 0.12, 420 + i) * np.sin(np.pi * c / 0.35)
        place(creak, part * r.uniform(0.15, 0.3), at)
    x = breath * 0.9 + air + groan * 0.6 + creak
    x = np.tanh(x * 1.1)
    return x / (np.max(np.abs(x)) + 1e-9)


def leviathan_hit():
    """A heavy blow lands on Leviathan armour: deep boom with a huge, low metal ring."""
    d = 1.2
    t = tt(d)
    boom = thump(t, 95, 34, 0.22, 0.03)
    plate = ring(t, [(173, 1.0, 1.0), (262, 0.8, 0.8), (409, 0.6, 0.6), (733, 0.4, 0.4), (1187, 0.25, 0.3)], 0.45, 431, 0.003)
    crack = filt(noise(d, 432), 600, 8000) * env(t, 0.0005, 0.02)
    grit = filt(noise(d, 433), 200, 2500) * env(t, 0.001, 0.12)
    x = boom * 1.1 + plate * env(t, 0.001, 0.5) * 0.6 + crack * 0.7 + grit * 0.5
    return finish(reverb(tanh(x, 1.7), 1.1, 434, 0.25, 2500), 0.001, 0.15)


def _rubble(dur, n, seed, start=0.0, spread=0.4, lo=300, hi=5000, gain=(0.15, 0.6)):
    out = np.zeros(int(round(dur * SR)))
    r = rng(seed)
    for i in range(n):
        ln = r.uniform(0.02, 0.08)
        c = tt(ln)
        rock = filt(noise(ln, seed * 100 + i), r.uniform(lo, lo * 2), r.uniform(hi * 0.5, hi)) * np.exp(-c / (ln / 4))
        rock += thump(c, r.uniform(90, 200), 50, ln / 3, 0.01) * 0.4
        place(out, rock * r.uniform(*gain), start + r.exponential(spread))
    return out


def leviathan_smash():
    """The Leviathan ploughs through a building: crunch bursts, low boom, rubble cascade and dust."""
    d = 2.0
    t = tt(d)
    boom = thump(t, 75, 28, 0.4, 0.05)
    crunch = np.zeros_like(t)
    for i, at in enumerate((0.0, 0.07, 0.16, 0.3)):
        c = tt(0.25)
        part = filt(noise(0.25, 441 + i), 150, 5000) * env(c, 0.001, 0.06)
        place(crunch, tanh(part * 2, 2.0) * (1.0 - i * 0.15), at)
    rubble = _rubble(d, 60, 442, 0.1, 0.45)
    dust = filt(noise(d, 443), 1500, 7000) * env(t, 0.1, 0.6) * 0.2
    x = boom * 1.1 + crunch * 0.9 + rubble + dust
    return finish(reverb(tanh(x, 1.8), 1.0, 444, 0.25, 3000), 0.001, 0.2)


def leviathan_crash():
    """A dead Leviathan hits the city (~3.5 s): enormous impact, ground rumble, collapsing debris, a last groan."""
    d = 3.6
    t = tt(d)
    boom = thump(t, 60, 20, 0.9, 0.08)
    impact = filt(noise(d, 451), 40, 4000) * env(t, 0.002, 0.25)
    rumble = filt(noise(d, 452), 20, 160) * env(t, 0.02, 1.2) * 1.2
    rubble = _rubble(d, 120, 453, 0.15, 0.8, 200, 4500, (0.1, 0.5))
    groan = ring(t, [(83, 1.0, 1.0), (131, 0.8, 0.8), (199, 0.6, 0.6)], 1.5, 454, 0.005) * env(t, 0.3, 1.2) * 0.4
    x = boom * 1.3 + impact + rumble + rubble + groan
    return finish(reverb(tanh(x, 2.0), 2.4, 455, 0.3, 2200), 0.001, 0.5)


def leviathan_death():
    """The Leviathan dies (~4 s): a long falling roar that breaks into a metallic death rattle."""
    d = 4.2
    t = tt(d)
    u = t / d
    f = 66 * np.exp(-u * 1.1) + 18
    growl = osc(f, "saw") + 0.6 * osc(f * 2.03, "saw") + filt(noise(d, 461), 60, 900) * 0.7
    growl = moving_formant(growl, lambda v: [(480 - 260 * v, 200, 1.0), (980 - 400 * v, 300, 0.6)])
    sf = 900 * np.exp(-u * 1.6) + 150
    screech = (osc(sf, "saw") + 0.4 * osc(sf * 1.51, "saw")) * np.clip((0.7 - u) / 0.4, 0, 1)
    screech = formant(screech, [(1300, 500, 1.0), (2500, 800, 0.5)])
    rattle = np.zeros_like(t)
    r = rng(462)
    a = 1.9
    while a < d - 0.3:
        c = tt(0.08)
        rattle = place(rattle, ring(c, [(r.uniform(200, 500), 1.0, 1.0), (r.uniform(600, 1400), 0.5, 0.5)], 0.03, int(a * 1000)) * r.uniform(0.2, 0.5), a)
        a += r.uniform(0.04, 0.12) * (1 + (a - 1.9))
    shape = np.clip(u / 0.06, 0, 1) * np.clip((1 - u) / 0.5, 0, 1)
    x = (growl + screech * 0.4) * shape + rattle * 0.6
    return finish(reverb(tanh(x, 2.0), 2.6, 463, 0.35, 2300), 0.02, 0.6)


# --- portal and phases --------------------------------------------------------------------------

def portal_open():
    """The beam tears the sky open (~4.5 s): rising tearing noise and crackle, ignition boom, a swelling energy chord."""
    d = 4.6
    t = tt(d)
    u = t / d
    tear = bandsweep(noise(d, 501), 150, 800, 2500, 11000, 0.8) * np.clip(u / 0.3, 0, 1) ** 2 * np.clip((1 - u) / 0.6, 0, 1)
    elec = crackle(d, 200, 502) * np.clip(u / 0.25, 0, 1) * np.clip((1 - u) / 0.5, 0, 1)
    ign = np.zeros_like(t)
    c = tt(2.6)
    place(ign, thump(c, 70, 24, 0.8, 0.07) * 1.3 + filt(noise(2.6, 503), 40, 3000) * env(c, 0.002, 0.3), 1.15)
    chord_env = np.clip((t - 1.15) / 0.8, 0, 1) ** 1.5 * np.clip((d - t) / 1.6, 0, 1)
    chord = sum(a * osc(np.full_like(t, f) * (1 + 0.004 * np.sin(2 * np.pi * (0.7 + k * 0.3) * t)), "saw") for k, (f, a) in
                enumerate([(55, 0.8), (82.5, 0.5), (110, 0.45), (165, 0.3), (220, 0.2)]))
    chord = filt(chord, 30, 1800) * chord_env
    shimmer = filt(noise(d, 504), 4000, 11000) * chord_env * 0.25 * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * t))
    x = tear * 0.7 + elec * 0.6 + ign + chord * 0.7 + shimmer
    return finish(reverb(tanh(x, 1.6), 2.8, 505, 0.35, 3500), 0.05, 0.6)


def portal_loop():
    """Portal hum loop (5 s, seamless): deep energy chord, slow swirl, a shimmering high band and faint crackle."""
    d = 5.0
    t = tt(d)
    chord = (ptone(t, 55.0, d, 0.8, 0.003, 0.6, "saw") + ptone(t, 82.4, d, 0.5, 0.003, 0.8, "saw")
             + ptone(t, 110.0, d, 0.4, 0.002, 1.0) + ptone(t, 164.8, d, 0.25, 0.002, 1.2) + ptone(t, 220.0, d, 0.12, 0.002, 1.4))
    spec = np.fft.rfft(chord)
    chord = np.fft.irfft(spec * band(30, 900)(np.fft.rfftfreq(len(chord), 1 / SR)), len(chord))
    swirl = 0.75 + 0.25 * plfo(t, 0.4, d)
    shimmer = pnoise(d, 511, 3500, 10000) * 0.18 * (0.6 + 0.4 * plfo(t, 3.0, d)) * (0.7 + 0.3 * plfo(t, 0.2, d, 1.3))
    wind = pnoise(d, 512, 200, 1500) * 0.3 * (0.6 + 0.4 * plfo(t, 0.6, d, 0.5))
    pops = np.zeros_like(t)
    r = rng(513)
    n = len(t)
    for i in range(40):
        at = int(r.uniform(0, n))
        ln = int(0.004 * SR)
        p = rng(514 + i).standard_normal(ln) * np.exp(-np.arange(ln) / (ln / 4)) * r.uniform(0.1, 0.35)
        idx = (at + np.arange(ln)) % n
        pops[idx] += p
    spec = np.fft.rfft(pops)
    pops = np.fft.irfft(spec * band(2500, 11000)(np.fft.rfftfreq(n, 1 / SR)), n)
    x = chord * swirl * 0.8 + shimmer + wind + pops * 0.8
    x = np.tanh(x * 1.2)
    return x / (np.max(np.abs(x)) + 1e-9)


def portal_thunder(seed=520):
    """Portal lightning: a sharp electric crack, crackle bursts and rolling thunder."""
    d = 2.7
    t = tt(d)
    crack = filt(noise(d, seed), 1200, 12000) * env(t, 0.0003, 0.03)
    snap = osc(glide(t, 3000, 300, 0.3), "saw") * env(t, 0.001, 0.04) * 0.4
    elec = crackle(d, 160, seed + 1, 2000, 11000) * env(t, 0.005, 0.25)
    roll = filt(noise(d, seed + 2), 25, 300) * env(t, 0.06, 0.8) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 1.7 * t + 1)))
    boom = thump(t, 70, 28, 0.5, 0.05)
    x = crack * 1.0 + snap + elec * 0.6 + roll * 1.3 + boom * 0.8
    return finish(reverb(tanh(x, 1.8), 2.2, seed + 3, 0.3, 3000), 0.0005, 0.4)


def portal_close():
    """The portal collapses (~4 s): air sucked in, the energy chord falling, a final implosion thump and a fading shimmer."""
    d = 4.2
    t = tt(d)
    u = t / d
    suck = bandsweep(noise(d, 531), 2000, 9000, 120, 900, 1.3) * np.clip(u / 0.65, 0, 1) ** 2 * (t < 2.75)
    fall = np.exp(-np.clip(t / 2.7, 0, 1) * 1.6)
    chord = sum(a * osc(f * fall, "saw") for f, a in [(110, 0.7), (165, 0.5), (220, 0.35), (330, 0.2)])
    chord = filt(chord, 30, 2000) * np.clip(t / 0.3, 0, 1) * (t < 2.75)
    imp = np.zeros_like(t)
    c = tt(1.4)
    place(imp, thump(c, 120, 30, 0.35, 0.03) * 1.3 + filt(noise(1.4, 532), 50, 4000) * env(c, 0.001, 0.08), 2.75)
    shimmer = filt(noise(d, 533), 5000, 12000) * np.clip((t - 2.8) / 0.05, 0, 1) * np.exp(-np.maximum(t - 2.8, 0) / 0.5) * 0.3
    x = suck * 0.8 + chord * 0.7 + imp + shimmer
    return finish(reverb(tanh(x, 1.6), 2.4, 534, 0.3, 3500), 0.05, 0.4)


def debris(seed=540):
    """Rubble falls: clatter of stone and concrete chunks, a few heavy thuds."""
    d = 1.7
    t = tt(d)
    rubble = _rubble(d, 70, seed, 0.0, 0.35)
    heavy = np.zeros_like(t)
    r = rng(seed + 1)
    for i in range(4):
        c = tt(0.3)
        place(heavy, thump(c, r.uniform(80, 120), 40, 0.08, 0.02) + filt(noise(0.3, seed * 7 + i), 100, 2000) * env(c, 0.001, 0.04), r.uniform(0, 0.8))
    dust = filt(noise(d, seed + 2), 1500, 7000) * env(t, 0.05, 0.5) * 0.15
    x = rubble + heavy * 0.7 + dust
    return finish(reverb(tanh(x, 1.5), 0.8, seed + 3, 0.2, 3000), 0.001, 0.15)


def debris_2():
    return debris(550)


def phase_horn():
    """Alien war horn for each phase (~3 s): low brass stack with a fifth, throat formant, a downward bend at the end."""
    d = 3.2
    t = tt(d)
    u = t / d
    bend = 1 - 0.06 * np.clip((u - 0.7) / 0.3, 0, 1) ** 2
    vib = 1 + 0.006 * np.sin(2 * np.pi * 4.5 * t) * np.clip((u - 0.2) / 0.3, 0, 1)
    f = 73.4 * bend * vib
    brass = osc(f, "saw") + 0.7 * osc(f * 1.5, "saw") + 0.4 * osc(f * 2, "saw") + 0.2 * osc(f * 0.5, "saw")
    brass = moving_formant(brass, lambda v: [(380 + 200 * np.sin(np.pi * v), 220, 1.0), (1150, 400, 0.5), (2400, 700, 0.25)])
    rasp = filt(noise(d, 561), 400, 3500) * 0.2
    shape = np.clip(u / 0.12, 0, 1) ** 2 * np.clip((1 - u) / 0.35, 0, 1)
    x = (brass + rasp) * shape
    return finish(reverb(tanh(x, 2.0), 2.6, 562, 0.35, 2500), 0.03, 0.5)


def sever():
    """The hive link is cut: the energy hum powers down (falling chord, slowing tremolo), crackle dies, a final thunk."""
    d = 2.7
    t = tt(d)
    u = t / d
    fall = np.exp(-u * 2.2)
    trem = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(14 * fall + 1) / SR)
    chord = sum(a * osc(f * (0.35 + 0.65 * fall), "saw") for f, a in [(220, 0.7), (330, 0.5), (440, 0.35), (660, 0.2)])
    chord = filt(chord, 40, 3000) * trem * np.clip((1 - u) / 0.6, 0, 1)
    elec = crackle(d, 150, 571) * np.exp(-t / 0.5)
    snap = filt(noise(d, 572), 1500, 11000) * env(t, 0.0003, 0.02)
    thunk = np.zeros_like(t)
    c = tt(0.6)
    place(thunk, thump(c, 90, 35, 0.15, 0.02), 1.9)
    x = chord * 0.8 + elec * 0.6 + snap * 0.8 + thunk * 0.7
    return finish(reverb(tanh(x, 1.6), 1.6, 573, 0.3, 3000), 0.001, 0.3)


CUES = {
    "rifle_fire": rifle_fire, "rifle_fire_2": rifle_fire_2, "rifle_charge": rifle_charge,
    "bolt_impact": bolt_impact, "bolt_impact_2": bolt_impact_2, "cannon_fire": cannon_fire,
    "explosion": explosion, "explosion_2": explosion_2,
    "chariot_engine": chariot_engine, "chariot_flyby": chariot_flyby, "chariot_hit": chariot_hit,
    "chariot_explode": chariot_explode,
    "soldier_chatter": soldier_chatter, "soldier_chatter_2": soldier_chatter_2, "soldier_chatter_3": soldier_chatter_3,
    "soldier_hurt": soldier_hurt, "soldier_hurt_2": soldier_hurt_2, "soldier_death": soldier_death,
    "soldier_step": soldier_step, "soldier_step_2": soldier_step_2, "soldier_step_3": soldier_step_3,
    "soldier_melee": soldier_melee,
    "leviathan_roar": leviathan_roar, "leviathan_loop": leviathan_loop, "leviathan_hit": leviathan_hit,
    "leviathan_smash": leviathan_smash, "leviathan_crash": leviathan_crash, "leviathan_death": leviathan_death,
    "portal_open": portal_open, "portal_loop": portal_loop, "portal_thunder": portal_thunder,
    "portal_close": portal_close, "debris": debris, "debris_2": debris_2, "phase_horn": phase_horn, "sever": sever,
}
LOOPS = {"chariot_engine", "leviathan_loop", "portal_loop"}

# Loudness by role (loudest 100 ms RMS, dBFS), soft-limited under CEILING_DB (tools/sfx/dio.py lesson):
# signature/heavy -8..-10, hits and weapons -10..-13, voices -13..-15, motion -14, steps -19, loops -16..-18
# (the loops are scaled again by ChitauriLoops at run time).
LOUDNESS = {
    "portal_open": -9.0, "portal_close": -9.5, "portal_thunder": -9.0, "sever": -10.0, "phase_horn": -10.5,
    "leviathan_roar": -8.5, "leviathan_death": -9.0, "leviathan_crash": -8.0, "leviathan_smash": -9.0, "leviathan_hit": -10.0,
    "chariot_explode": -8.5, "explosion": -9.5, "explosion_2": -9.5, "cannon_fire": -10.5, "debris": -12.5, "debris_2": -12.5,
    "rifle_fire": -12.0, "rifle_fire_2": -12.0, "bolt_impact": -13.0, "bolt_impact_2": -13.0, "rifle_charge": -15.0,
    "chariot_hit": -12.0, "chariot_flyby": -11.5, "soldier_melee": -13.0,
    "soldier_chatter": -15.0, "soldier_chatter_2": -15.0, "soldier_chatter_3": -15.0,
    "soldier_hurt": -13.5, "soldier_hurt_2": -13.5, "soldier_death": -13.0,
    "soldier_step": -19.0, "soldier_step_2": -19.0, "soldier_step_3": -19.0,
    "chariot_engine": -16.0, "leviathan_loop": -15.0, "portal_loop": -16.5,
}
CEILING_DB = -1.5
# Vorbis quality per cue: long cues use a lower quality so every .ogg.b64 stays under the 60 KB push limit.
QUALITY = {"portal_loop": 0, "leviathan_loop": 0, "leviathan_roar": 1, "leviathan_death": 1, "leviathan_crash": 1,
           "portal_open": 1, "portal_close": 1, "phase_horn": 1}


def momentary_db(x):
    w = int(0.1 * SR)
    c = np.convolve(x * x, np.ones(w) / w, "valid") if len(x) > w else np.array([np.mean(x * x)])
    return 10 * np.log10(np.max(c) + 1e-12)


def master(x, target, loop=False):
    ceil = 10 ** (CEILING_DB / 20)
    for _ in range(3):
        x = x * 10 ** ((target - momentary_db(x)) / 20)
        if np.max(np.abs(x)) > ceil:
            x = ceil * np.tanh(x / ceil)
    if not loop:
        x = fade(x, 0.0005, 0.01)
    return x


# --- packaging ---------------------------------------------------------------------------------

def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def encode(wav, ogg, q):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-map_metadata", "-1", "-fflags", "+bitexact",
                    "-flags:a", "+bitexact", "-c:a", "libvorbis", "-q:a", str(q), "-ac", "1", "-ar", str(SR), str(ogg)], check=True)


def decoded_pcm(ogg):
    return subprocess.run(["ffmpeg", "-v", "error", "-i", str(ogg), "-f", "s16le", "-ac", "1", "-"], check=True,
                          capture_output=True).stdout


def build(tmp, wav_dir=None, only=None):
    res = {}
    for name, fn in CUES.items():
        if only and name not in only:
            continue
        loop = name in LOOPS
        x = master(fn(), LOUDNESS[name], loop)
        assert np.isfinite(x).all() and np.max(np.abs(x)) > 0.05, name
        if loop:
            # seam check: the jump from the last sample to the first is no bigger than the largest step inside
            # the loop (a saw reset may fall on the seam; it happens every cycle anyway)
            assert abs(x[0] - x[-1]) <= np.max(np.abs(np.diff(x))) * 1.02 + 1e-4, name
        w = Path(tmp) / (name + ".wav")
        o = Path(tmp) / (name + ".ogg")
        write_wav(w, x)
        if wav_dir:
            os.makedirs(wav_dir, exist_ok=True)
            write_wav(Path(wav_dir) / (name + ".wav"), x)
        encode(w, o, QUALITY.get(name, 2))
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
            dst = OUT / (name + ".ogg.b64")
            if a.check:
                if not dst.exists():
                    print("missing", dst)
                    bad += 1
                    continue
                committed = Path(tmp) / (name + ".committed.ogg")
                committed.write_bytes(base64.b64decode(dst.read_text()))
                if hashlib.sha256(decoded_pcm(committed)).digest() != hashlib.sha256(decoded_pcm(ogg)).digest():
                    print("differs", name)
                    bad += 1
            else:
                OUT.mkdir(parents=True, exist_ok=True)
                dst.write_bytes(base64.encodebytes(ogg.read_bytes()))
        print("%d cues %s" % (len(built), "checked" if a.check else "written"))
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
