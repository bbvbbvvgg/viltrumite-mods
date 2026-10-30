#!/usr/bin/env python3
"""Regulus Reborn: fully original procedural SFX (own synthesis, CC0-1.0). No external samples.

Usage: python3 tools/sfx/regulus_reborn.py [--check] [--wav DIR] [--json]
Deterministic numpy synthesis -> mono 44.1 kHz 16-bit -> ffmpeg libvorbis (bitexact) -> base64 text
assets under core binassets sounds/regulus/reborn/ (decoded by Gradle decodeBinaryAssets). --check
re-synthesises and compares decoded PCM with the committed files; --json (re)writes the 32
`regulus.*` events in the sound overlay sound-overlays/regulus.json (variants as several entries of one event, subtitles
subtitles.viltrumitecore.regulus_<cue>). The first kit's regulus_* sounds stay (Reinhard uses them).

Sound language (spec §9): stopped time is a sound that *stops* — swells that cut to dead silence,
glass and crystal resonances, the heartbeat; impacts are dry and close, never thunder or fire.
Cue sheet (trigger, scope) lives in tools/assets/regulus_sources.md.
"""
import argparse
import base64
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/regulus/reborn"
# sounds.json is past the one-request push size: the events live in a sound overlay merged by
# processResources (viltrumitecore/build.gradle).
SOUNDS_JSON = ROOT / "viltrumitecore/src/main/sound-overlays/regulus.json"
SR = 44100


# --- primitives ------------------------------------------------------------------------------

def tt(dur):
    return np.arange(int(dur * SR)) / SR


def noise(dur, seed):
    return np.random.default_rng(seed).standard_normal(int(dur * SR))


def env(t, attack, decay, hold=0.0):
    a = np.clip(t / max(attack, 1e-4), 0, 1) ** 2
    d = np.exp(-np.maximum(t - attack - hold, 0) / max(decay, 1e-4))
    return a * d


def band(x, lo, hi, soft=1.5):
    """Zero-phase FFT band-pass with soft (octave-ish) skirts."""
    n = len(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    g = np.ones_like(f)
    if lo > 0:
        g *= 1 / (1 + (lo / np.maximum(f, 1)) ** (2 * soft))
    if hi > 0:
        g *= 1 / (1 + (f / hi) ** (2 * soft))
    return np.fft.irfft(np.fft.rfft(x) * g, n)


def sweep(t, f0, f1, curve=1.0):
    u = (t / max(t[-1], 1e-6)) ** curve
    f = f0 + (f1 - f0) * u
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def glass(t, f0, decay, seed, partials=(1.0, 2.32, 4.25, 6.63, 9.38), detune=0.003):
    """Inharmonic struck-glass bank: bright partials decaying faster, slight beating."""
    r = np.random.default_rng(seed)
    out = np.zeros_like(t)
    for i, p in enumerate(partials):
        f = f0 * p * (1 + r.uniform(-detune, detune))
        ph = r.uniform(0, 2 * np.pi)
        out += np.sin(2 * np.pi * f * t + ph) * np.exp(-t / (decay / (1 + 0.8 * i))) / (1 + 0.6 * i)
        out += 0.4 * np.sin(2 * np.pi * f * (1.0015) * t) * np.exp(-t / (decay / (1 + 0.8 * i))) / (1 + 0.6 * i)
    return out


def thump(t, f0, f1, decay, drop=0.05):
    f = f1 + (f0 - f1) * np.exp(-t / drop)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / decay)


def heartbeat(t, at, strength=1.0):
    """Lub-dub: two low thumps 0.16 s apart, the second softer."""
    out = np.zeros_like(t)
    for k, (dt, s, f) in enumerate(((0.0, 1.0, 62), (0.16, 0.7, 54))):
        u = t - at - dt
        m = u >= 0
        out[m] += s * strength * thump(u[m], f * 1.8, f, 0.09, 0.02)
    return out


def tick(t, at, f, decay=0.012, seed=0):
    """A tiny crystalline tick (a grain halting)."""
    out = np.zeros_like(t)
    u = t - at
    m = u >= 0
    out[m] = glass(u[m], f, decay * 6, seed, partials=(1.0, 2.7, 5.1)) * np.exp(-u[m] / decay)
    return out


def crack(dur, seed, lo=1500, hi=9000, n=14, spread=0.05):
    """Dry hairline crack: a burst of sharp clicks (glass/stone fracture), no rumble."""
    t = tt(dur)
    r = np.random.default_rng(seed)
    out = np.zeros_like(t)
    for _ in range(n):
        at = abs(r.normal(0, spread))
        i = int(at * SR)
        if i >= len(t):
            continue
        k = min(len(t) - i, int(0.004 * SR))
        out[i:i + k] += r.uniform(0.4, 1.0) * r.choice([-1, 1]) * np.exp(-np.arange(k) / (0.0006 * SR))
    return band(out, lo, hi)


def swish(dur, seed, lo, hi, peak=0.45):
    t = tt(dur)
    shape = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 2
    shape *= np.exp(-((t / dur - peak) ** 2) / 0.05)
    return band(noise(dur, seed), lo, hi) * shape


def cut_swell(dur, seed, lo0=200, hi0=1800, rise=3.0):
    """Reverse swell that stops dead: the moment time stops."""
    t = tt(dur)
    x = band(noise(dur, seed), lo0, hi0) * (t / dur) ** rise
    x[-int(0.002 * SR):] *= np.linspace(1, 0, int(0.002 * SR))
    return x


def reverb(x, size, seed, mix=0.25, tone=5000):
    """Small diffuse room: exponentially decaying filtered noise convolution (FFT)."""
    n = int(size * SR)
    ir = noise(size, seed) * np.exp(-np.arange(n) / (0.25 * n))
    ir = band(ir, 120, tone)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    m = len(x) + n
    wet = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)[:len(x) + n]
    out = np.concatenate([x, np.zeros(n)])
    return out * (1 - mix) + wet * mix


def place(out, x, at):
    i = int(at * SR)
    k = min(len(x), len(out) - i)
    if k > 0:
        out[i:i + k] += x[:k]
    return out


def finish(x, peak_db=-2.0, fin=0.002, fout=0.03):
    x = np.tanh(x / (np.max(np.abs(x)) + 1e-9) * 1.3)
    x = x / (np.max(np.abs(x)) + 1e-9) * 10 ** (peak_db / 20)
    i, o = int(fin * SR), int(fout * SR)
    x[:i] *= np.linspace(0, 1, i)
    x[len(x) - o:] *= np.linspace(1, 0, o)
    return x


# --- cues ------------------------------------------------------------------------------------
# name -> (fn(variant) -> signal, variants, subtitle EN, attenuation blocks)

def lion_on(v):
    d = 1.5
    out = np.zeros(int(d * SR))
    place(out, cut_swell(0.42, 101, 150, 2400, 2.6) * 0.9, 0.0)
    t = tt(1.08)
    place(out, glass(t, 523.0, 0.55, 102) * 0.5 + glass(t, 784.0, 0.45, 103) * 0.3, 0.42)
    place(out, heartbeat(tt(0.6), 0.0, 1.0)[: int(0.6 * SR)], 0.42)
    return finish(reverb(out, 0.6, 104, 0.2)[: int(d * SR)])


def lion_off(v):
    d = 1.1
    t = tt(d)
    x = sweep(t, 784, 392, 0.6) * np.exp(-t / 0.35) * 0.5
    x += band(noise(d, 111), 300, 3000) * np.clip(t / 0.5, 0, 1) * np.exp(-np.maximum(t - 0.5, 0) / 0.15) * 0.5
    return finish(reverb(x, 0.5, 112, 0.25)[: len(t)], peak_db=-4)


def lion_deny(v):
    t = tt(0.32)
    x = thump(t, 220, 140, 0.03) * 0.8
    x += np.roll(thump(t, 200, 130, 0.03), int(0.11 * SR)) * 0.6
    return finish(band(x, 80, 2500), peak_db=-6)


def recoil(v):
    t = tt(0.55)
    f = (1180, 1320, 1050)[v]
    x = glass(t, f, 0.16, 120 + v) * 0.7
    x += np.concatenate([crack(0.05, 125 + v, 2500, 12000, 8, 0.004), np.zeros(len(t) - int(0.05 * SR))]) * 1.2
    x += thump(t, 180, 90, 0.04) * 0.4
    return finish(x, peak_db=-3)


def projectile_stop(v):
    d = 0.65
    out = np.zeros(int(d * SR))
    place(out, cut_swell(0.22, 130, 1200, 7000, 1.4) * 0.8, 0.0)
    place(out, glass(tt(0.43), 1568.0, 0.18, 131) * 0.35, 0.22)
    return finish(out, peak_db=-4)


def heartbeat_cue(v):
    t = tt(0.7)
    return finish(band(heartbeat(t, 0.02, 1.0), 30, 600), peak_db=-3 - v)


def strain(v):
    t = tt(1.3)
    hum = (np.sin(2 * np.pi * 55 * t) + 0.5 * np.sin(2 * np.pi * 58.3 * t)) * (0.6 + 0.4 * np.sin(2 * np.pi * 1.6 * t))
    x = hum * np.clip(t / 0.3, 0, 1) * np.clip((1.3 - t) / 0.3, 0, 1) * 0.5
    x += heartbeat(t, 0.1, 0.8) + heartbeat(t, 0.62, 0.9)
    x += band(noise(1.3, 140), 2000, 6000) * 0.02
    return finish(x, peak_db=-5)


def seizure(v):
    d = 1.6
    t = tt(d)
    x = heartbeat(t, 0.0, 1.0) + heartbeat(t, 0.3, 0.8) + heartbeat(t, 0.42, 1.0)
    cl = glass(t, 466.0, 0.5, 150) + glass(t, 493.9, 0.5, 151) + glass(t, 698.5, 0.4, 152)
    x += cl * 0.35 * np.clip((t - 0.42) / 0.02, 0, 1)
    x += band(noise(d, 153), 200, 1200) * env(t, 0.5, 0.25) * 0.3
    return finish(reverb(x, 0.5, 154, 0.2)[: len(t)])


def touch_swish(v):
    return finish(swish(0.26, 160 + v, 500 + 120 * v, 5000, 0.5), peak_db=-8)


def touch_hit(v):
    d = 0.48
    t = tt(d)
    x = thump(t, (150, 170, 135)[v], (70, 80, 64)[v], 0.07, 0.015) * 0.9
    x += np.concatenate([crack(0.08, 170 + v, 1800, 11000, 12, 0.008), np.zeros(len(t) - int(0.08 * SR))]) * 1.1
    x += glass(t, (880, 988, 831)[v], 0.12, 175 + v) * 0.25
    x += band(noise(d, 178 + v), 150, 1500) * env(t, 0.002, 0.03) * 0.5
    return finish(x)


def rupture(v):
    d = 0.85
    t = tt(d)
    x = thump(t, 120, 45, 0.25, 0.04) * 1.0
    x += band(noise(d, 180), 80, 900) * env(t, 0.004, 0.12) * 0.7
    x += np.concatenate([crack(0.2, 181, 700, 6000, 22, 0.04), np.zeros(len(t) - int(0.2 * SR))]) * 0.7
    return finish(reverb(x, 0.4, 182, 0.15)[: len(t)])


def sand_scoop(v):
    d = 0.55
    t = tt(d)
    grains = band(noise(d, 190) * (np.random.default_rng(191).random(len(t)) > 0.985), 2000, 12000) * 3.0
    x = band(noise(d, 192), 800, 6000) * np.clip(t / d, 0, 1) ** 1.5 * 0.5 + grains * np.clip(t / d, 0, 1)
    return finish(x, peak_db=-6)


def sand_release(v):
    d = 0.9
    out = np.zeros(int(d * SR))
    place(out, swish(0.3, 200, 900, 9000, 0.3) * 0.9, 0.0)
    r = np.random.default_rng(201)
    t = tt(d)
    for i in range(9):
        out += tick(t, 0.12 + i * 0.035 + r.uniform(0, 0.02), r.uniform(2400, 4200), 0.01, 202 + i) * 0.35
    return finish(out, peak_db=-4)


def grain_hit(v):
    t = tt(0.22)
    x = tick(t, 0.0, (3100, 3500, 2800)[v], 0.008, 210 + v) * 0.8
    x += thump(t, 260, 120, 0.03, 0.01) * 0.5
    return finish(x, peak_db=-6)


def grain_stop(v):
    t = tt(0.16)
    return finish(tick(t, 0.0, (3800, 4300, 3500)[v], 0.006, 220 + v), peak_db=-10)


def sever_charge(v):
    d = 0.6
    t = tt(d)
    x = sweep(t, 1400, 3200, 1.6) * (t / d) ** 2 * 0.25
    x += band(noise(d, 230), 3000, 10000) * (t / d) ** 2 * 0.3
    return finish(x, peak_db=-7)


def sever_release(v):
    d = 0.75
    t = tt(d)
    x = band(noise(d, 240), 1500, 14000) * env(t, 0.01, 0.12) * 0.9
    x += glass(t, 2093.0, 0.3, 241, partials=(1.0, 1.51, 2.76)) * 0.25 * np.clip(t / 0.01, 0, 1)
    x += sweep(t, 4000, 1500, 0.5) * env(t, 0.005, 0.08) * 0.2
    return finish(x, peak_db=-3)


def sever_cut(v):
    t = tt(0.42)
    x = band(noise(0.42, 250 + v), 2500, 15000) * env(t, 0.001, 0.035) * 1.0
    x += tick(t, 0.03, (2637, 2349)[v], 0.03, 252 + v) * 0.5
    x += thump(t, 200, 90, 0.04) * 0.4
    return finish(x, peak_db=-3)


def procession_step(v):
    d = 0.4
    t = tt(d)
    x = thump(t, (110, 100, 120)[v], 50, 0.09, 0.02) * 1.0
    x += np.concatenate([crack(0.12, 260 + v, 500, 5000, 10, 0.02), np.zeros(len(t) - int(0.12 * SR))]) * 0.45
    x += band(noise(d, 263 + v), 60, 400) * env(t, 0.003, 0.08) * 0.4
    return finish(x, peak_db=-4)


def procession_ram(v):
    d = 1.1
    t = tt(d)
    x = thump(t, 140, 42, 0.3, 0.05) * 1.1
    x += band(noise(d, 270), 100, 2500) * env(t, 0.002, 0.15) * 0.8
    x += np.concatenate([crack(0.3, 271, 600, 8000, 30, 0.06), np.zeros(len(t) - int(0.3 * SR))]) * 0.8
    x += glass(t, 392.0, 0.5, 272) * 0.2
    return finish(reverb(x, 0.6, 273, 0.2)[: len(t)])


def reply_stance(v):
    t = tt(0.65)
    x = (glass(t, 659.3, 0.35, 280) + glass(t, 987.8, 0.3, 281) * 0.6) * np.clip(t / 0.04, 0, 1)
    x += band(noise(0.65, 282), 2000, 8000) * env(t, 0.02, 0.06) * 0.2
    return finish(x, peak_db=-6)


def reply_strike(v):
    d = 0.65
    out = np.zeros(int(d * SR))
    place(out, swish(0.18, 290, 700, 9000, 0.7) * 0.8, 0.0)
    t = tt(0.47)
    hit = thump(t, 170, 70, 0.09, 0.015) + np.concatenate([crack(0.08, 291, 1500, 10000, 12, 0.008), np.zeros(len(t) - int(0.08 * SR))])
    place(out, hit, 0.18)
    return finish(out)


def king_bind(v):
    d = 0.95
    t = tt(d)
    x = heartbeat(t, 0.0, 0.9)
    x += glass(t, 1046.5, 0.5, 300, partials=(1.0, 2.0, 3.01, 4.2)) * 0.3 * np.clip((t - 0.3) / 0.01, 0, 1)
    return finish(reverb(x, 0.5, 301, 0.25)[: len(t)], peak_db=-4)


def king_hop(v):
    d = 0.65
    t = tt(d)
    x = heartbeat(t, 0.0, 0.6) * 0.8 + swish(d, 310, 400, 3000, 0.55) * 0.4
    x += glass(t, 1174.7, 0.2, 311, partials=(1.0, 2.0, 3.0)) * 0.2 * np.clip((t - 0.4) / 0.01, 0, 1)
    return finish(x, peak_db=-5)


def king_release(v):
    d = 0.85
    t = tt(d)
    x = heartbeat(t, 0.0, 0.7)
    x += np.roll(np.concatenate([crack(0.25, 320, 2000, 12000, 26, 0.03), np.zeros(len(t) - int(0.25 * SR))]), int(0.2 * SR)) * 0.8
    for i in range(5):
        x += tick(t, 0.24 + 0.05 * i, 2600 + 300 * i, 0.03, 321 + i) * 0.2
    return finish(x, peak_db=-4)


def refuse(v):
    t = tt(0.2)
    return finish(band(thump(t, 300, 200, 0.02, 0.005) + np.roll(thump(t, 280, 180, 0.02, 0.005), int(0.06 * SR)), 150, 3000), peak_db=-12)


def ult_accuse(v):
    d = 1.8
    t = tt(d)
    drone = sum(np.sin(2 * np.pi * f * t) / (k + 1) for k, f in enumerate((65.4, 98.0, 130.8, 196.0)))
    x = drone * np.clip(t / 0.8, 0, 1) * np.clip((d - t) / 0.4, 0, 1) * 0.35
    x += (glass(t, 523.3, 0.9, 330) + glass(t, 784.0, 0.8, 331) * 0.6 + glass(t, 659.3, 0.8, 332) * 0.5) * np.clip((t - 0.7) / 0.02, 0, 1) * 0.3
    return finish(reverb(x, 0.8, 333, 0.3)[: len(t)], peak_db=-3)


def ult_scoop(v):
    d = 2.1
    out = np.zeros(int(d * SR))
    t = tt(1.7)
    rush = band(noise(1.7, 340), 200, 9000) * (t / 1.7) ** 2.2
    swirl = np.sin(2 * np.pi * np.cumsum(2 + 9 * t / 1.7) / SR)
    rush *= 0.75 + 0.25 * swirl
    grains = band(noise(1.7, 341) * (np.random.default_rng(342).random(len(t)) > 0.97), 3000, 14000) * 2.0 * (t / 1.7)
    place(out, rush + grains, 0.0)
    out[int(1.7 * SR) - int(0.002 * SR):int(1.7 * SR)] *= np.linspace(1, 0, int(0.002 * SR))
    tail = tt(0.4)
    place(out, glass(tail, 349.2, 0.4, 343) * 0.4 + heartbeat(tail, 0.0, 0.6), 1.7)
    return finish(out, peak_db=-2)


def ult_wave(v):
    d = 0.75
    t = tt(d)
    r = np.random.default_rng(350 + v)
    x = np.zeros_like(t)
    for i in range(16):
        x += tick(t, abs(r.normal(0.02, 0.02)), r.uniform(2200, 4800), 0.01, 351 + i + 20 * v) * 0.3
    x += thump(t, 150, 55, 0.15, 0.03) * 0.9
    x += band(noise(d, 360 + v), 2000, 12000) * env(t, 0.002, 0.04) * 0.6
    return finish(x)


def ult_flick(v):
    d = 2.6
    out = np.zeros(int(d * SR))
    t = tt(d)
    place(out, tick(tt(0.2), 0.0, 3520.0, 0.01, 370) * 0.5, 0.0)
    boom = thump(tt(2.4), 90, 30, 0.6, 0.08) * 1.2
    boom += band(noise(2.4, 371), 40, 1200) * env(tt(2.4), 0.004, 0.35) * 0.9
    boom += np.concatenate([crack(0.5, 372, 500, 9000, 40, 0.1), np.zeros(int(2.4 * SR) - int(0.5 * SR))]) * 0.9
    boom += (glass(tt(2.4), 261.6, 1.4, 373) + glass(tt(2.4), 392.0, 1.2, 374) * 0.7) * 0.25
    place(out, boom, 0.2)
    return finish(reverb(out, 1.0, 375, 0.3)[: len(t)], peak_db=-1)


def ult_resolve(v):
    d = 2.2
    t = tt(d)
    x = (glass(t, 392.0, 1.5, 380) + glass(t, 587.3, 1.3, 381) * 0.7 + glass(t, 784.0, 1.0, 382) * 0.4) * np.clip(t / 0.25, 0, 1)
    x += band(noise(d, 383), 300, 2500) * env(t, 0.6, 0.6) * 0.08
    return finish(reverb(x, 1.0, 384, 0.35)[: len(t)], peak_db=-5, fout=0.4)


def step(v):
    d = 0.22
    t = tt(d)
    x = thump(t, (170, 180, 160, 190)[v], 90, 0.025, 0.008) * 0.8
    x += band(noise(d, 390 + v), 1500, 7000) * env(t, 0.001, 0.012) * 0.6
    x += band(noise(d, 394 + v), 200, 1200) * env(t, 0.002, 0.03) * 0.4
    return finish(x, peak_db=-8)


CUES = {
    "lion_on": (lion_on, 1, "Time stops around Regulus", 32),
    "lion_off": (lion_off, 1, "Regulus's time resumes", 24),
    "lion_deny": (lion_deny, 1, "The Lion's Heart refuses", 12),
    "recoil": (recoil, 3, "A blow stops against Regulus", 24),
    "projectile_stop": (projectile_stop, 1, "A projectile stops dead", 24),
    "heartbeat": (heartbeat_cue, 2, "A stopped heart beats", 16),
    "strain": (strain, 1, "Regulus's heart strains", 16),
    "seizure": (seizure, 1, "Regulus's heart seizes", 24),
    "touch_swish": (touch_swish, 3, "Regulus swipes", 16),
    "touch_hit": (touch_hit, 3, "Greed's Touch strikes", 24),
    "rupture": (rupture, 1, "Something ruptures inside", 24),
    "sand_scoop": (sand_scoop, 1, "Regulus scoops up dust", 16),
    "sand_release": (sand_release, 1, "Stopped sand flies", 32),
    "grain_hit": (grain_hit, 3, "A stopped grain pierces", 24),
    "grain_stop": (grain_stop, 3, "A grain stops in the air", 16),
    "sever_charge": (sever_charge, 1, "The air goes still", 16),
    "sever_release": (sever_release, 1, "Severed air flies", 32),
    "sever_cut": (sever_cut, 2, "Stopped air cuts", 24),
    "procession_step": (procession_step, 3, "Regulus walks through everything", 24),
    "procession_ram": (procession_ram, 1, "Regulus walks into something", 32),
    "reply_stance": (reply_stance, 1, "Regulus waits for an answer", 16),
    "reply_strike": (reply_strike, 1, "Regulus replies", 24),
    "king_bind": (king_bind, 1, "A heart is given", 16),
    "king_hop": (king_hop, 1, "The heart moves on", 16),
    "king_release": (king_release, 1, "A borrowed heart breaks free", 16),
    "refuse": (refuse, 1, "Regulus can't act", 8),
    "ult_accuse": (ult_accuse, 1, "Regulus passes judgement", 48),
    "ult_scoop": (ult_scoop, 1, "The air itself is gathered", 48),
    "ult_wave": (ult_wave, 2, "Stopped grains strike", 40),
    "ult_flick": (ult_flick, 1, "One flick of a finger", 64),
    "ult_resolve": (ult_resolve, 1, "The judgement is over", 32),
    "step": (step, 4, "Regulus's footsteps", 12),
}


def files():
    for name, (fn, variants, _, _) in CUES.items():
        for v in range(variants):
            yield name, v, (name if variants == 1 else name + str(v)), fn


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def encode(wav, ogg):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-map_metadata", "-1", "-fflags", "+bitexact",
                    "-flags:a", "+bitexact", "-c:a", "libvorbis", "-q:a", "1", "-ac", "1", "-ar", str(SR), str(ogg)], check=True)


def decoded_pcm(ogg):
    return subprocess.run(["ffmpeg", "-v", "error", "-i", str(ogg), "-f", "s16le", "-ac", "1", "-"], check=True,
                          capture_output=True).stdout


def sounds_json():
    data = {}
    for name, (fn, variants, _, dist) in CUES.items():
        entries = []
        for v in range(variants):
            file = name if variants == 1 else name + str(v)
            entry = {"name": "viltrumitecore:regulus/reborn/" + file, "attenuation_distance": dist}
            if variants > 1:
                entry["pitch"] = round(1.0 + 0.03 * (v - (variants - 1) / 2), 3)
            entries.append(entry)
        data["regulus." + name] = {"category": "player", "subtitle": "subtitles.viltrumitecore.regulus_" + name, "sounds": entries}
    SOUNDS_JSON.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--wav")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    if a.json:
        sounds_json()
        print("sounds.json: %d regulus.* events" % len(CUES))
        return
    bad = 0
    with tempfile.TemporaryDirectory() as tmp:
        count = 0
        for name, v, file, fn in files():
            x = fn(v)
            assert np.isfinite(x).all() and np.max(np.abs(x)) > 0.05, file
            w = Path(tmp) / (file + ".wav")
            o = Path(tmp) / (file + ".ogg")
            write_wav(w, x)
            if a.wav:
                os.makedirs(a.wav, exist_ok=True)
                write_wav(Path(a.wav) / (file + ".wav"), x)
            encode(w, o)
            dst = OUT / (file + ".ogg.b64")
            count += 1
            if a.check:
                if not dst.exists():
                    print("missing", dst)
                    bad += 1
                    continue
                committed = Path(tmp) / (file + ".committed.ogg")
                committed.write_bytes(base64.b64decode(dst.read_text()))
                if hashlib.sha256(decoded_pcm(committed)).digest() != hashlib.sha256(decoded_pcm(o)).digest():
                    print("differs", file)
                    bad += 1
            else:
                OUT.mkdir(parents=True, exist_ok=True)
                dst.write_bytes(base64.encodebytes(o.read_bytes()))
        print("%d files %s" % (count, "checked" if a.check else "written"))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
