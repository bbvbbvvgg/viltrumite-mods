#!/usr/bin/env python3
"""Thanos / the six Infinity Stones world event: original procedural SFX and music (own synthesis, CC0-1.0).

No external samples and no vanilla sound stands in for a stone. Every cue is loudness-matched by role
(LOUDNESS) and registered by the Thanos sound overlay (viltrumitecore/src/main/sound-overlays/thanos.json,
merged into sounds.json at build). The trigger of each cue is documented in tools/assets/thanos_sources.md.

Each stone has its own timbre, reused by its moves and by the combos that mix it:
  power   (purple) distorted saw growl, electric arcs, heavy booms
  space   (blue)   glass partials, flanged air, portal hum
  reality (red)    ring-modulated detuned saws, liquid pitch wobble
  time    (green)  clock ticks, bells, reversed tape
  mind    (yellow) beating high sines (tinnitus), psychic throb
  soul    (orange) formant choir, breath, ghostly glide

Usage: python3 tools/sfx/thanos.py [--check] [--wav DIR] [--only a,b]
Deterministic numpy synthesis -> mono 44.1 kHz 16-bit -> ffmpeg libvorbis (bitexact) -> base64 text
assets in core binassets (decoded by Gradle decodeBinaryAssets; a file whose text is past PART bytes is
committed as NAME.ogg.b64.part0..N, joined in order at build). Loops (void, music_battle, music_final) are
rendered circularly, so their wrap point is seamless. --check re-synthesises in a temporary directory and
compares decoded PCM with the committed files. --wav DIR also writes the raw mixes for listening.
"""
import argparse
import base64
import hashlib
import os
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dio as d  # noqa: E402  (shared primitives, meter, encoder)

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/thanos"
SR = d.SR
PART = 56000  # base64 bytes per committed chunk (one GitHub push request carries less than 60 KB)

tt, env, sweep, spectral, band, filt, bandsweep = d.tt, d.env, d.sweep, d.spectral, d.band, d.filt, d.bandsweep
ring, thump, reverb, tanh, finish, rng = d.ring, d.thump, d.reverb, d.tanh, d.finish, d.rng


# --- primitives --------------------------------------------------------------------------------

def hz(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


def nz(n, seed):
    return rng(seed).standard_normal(n)


def put(out, x, at, gain=1.0):
    """Adds x into out at `at` seconds (clipped at both ends)."""
    i = int(round(at * SR))
    s, e = max(i, 0), min(len(out), i + len(x))
    if e > s:
        out[s:e] += gain * x[s - i:e - i]
    return out


def wrap(out, x, at, gain=1.0):
    """Adds x into a loop buffer at `at` seconds, wrapping past the end to the start."""
    n = len(out)
    i = int(round(at * SR)) % n
    k = 0
    while k < len(x):
        m = min(len(x) - k, n - i)
        out[i:i + m] += gain * x[k:k + m]
        k += m
        i = 0
    return out


def osc(t, f, shape="sin", vib=0.0, rate=5.0, seed=0):
    """Oscillator; f may glide (array). vib = vibrato depth in semitones."""
    r = rng(seed)
    f = np.broadcast_to(np.asarray(f, dtype=float), t.shape).astype(float)
    if vib:
        f = f * 2.0 ** (vib / 12.0 * np.sin(2 * np.pi * rate * t + r.uniform(0, 6.283)))
    ph = np.cumsum(f) / SR + r.uniform(0, 1)
    if shape == "saw":
        return 2.0 * (ph % 1.0) - 1.0
    if shape == "tri":
        return 2.0 * np.abs(2.0 * (ph % 1.0) - 1.0) - 1.0
    return np.sin(2 * np.pi * ph)


def stack(t, f, voices=5, spread=0.01, seed=0, vib=0.0, rate=4.6, shape="saw"):
    """Detuned unison (strings, brass, choir source)."""
    out = np.zeros_like(t)
    for i in range(voices):
        k = (i - (voices - 1) / 2.0) / max(1.0, (voices - 1) / 2.0)
        out += osc(t, np.asarray(f) * (1 + spread * k), shape, vib, rate + 0.37 * i, seed + i)
    return out / voices


def swell(t, at, rise, fall):
    """Envelope that rises (squared) over `rise` seconds into `at`, then falls exponentially."""
    u = np.clip((t - (at - rise)) / max(rise, 1e-4), 0, 1) ** 2
    return np.where(t < at, u, np.exp(-np.maximum(t - at, 0) / max(fall, 1e-4)))


def gate(t, start, stop, fin=0.01, fout=0.05):
    """1 between start and stop with linear ramps."""
    return np.minimum(np.clip((t - start) / fin, 0, 1), np.clip((stop - t) / fout, 0, 1))


def late(t, at):
    """Time axis that starts at `at` (0 before)."""
    return np.maximum(t - at, 0)


def flange(x, base_ms, depth_ms, rate, mix=0.7):
    n = np.arange(len(x), dtype=float)
    dl = (base_ms + depth_ms * (0.5 + 0.5 * np.sin(2 * np.pi * rate * n / SR))) * SR / 1000.0
    return x + mix * np.interp(n - dl, n, x, left=0.0)


def speed(x, ratio):
    """Tape-speed resample: ratio may vary per sample (pitch and time move together)."""
    n = len(x)
    r = np.broadcast_to(np.asarray(ratio, dtype=float), (n,))
    pos = np.cumsum(r) - r[0]
    return np.interp(pos, np.arange(n), x, right=0.0)


def echoes(x, taps):
    """Multi-tap echo: taps = [(delay s, gain, tape speed)]."""
    out = x.copy()
    for dly, g, sp in taps:
        put(out, speed(x, sp) if sp != 1.0 else x, dly, g)
    return out


def bursts(n, count, seed, at0, at1, lo, hi, length=(0.002, 0.012), shape="flat", amp=(0.3, 1.0), win="exp"):
    """Sparse noise bursts (electric arcs, dust grains, debris) between at0 and at1, filtered once."""
    out = np.zeros(n)
    r = rng(seed)
    for _ in range(count):
        u = r.uniform(0, 1)
        if shape == "up":
            u = np.sqrt(u)
        elif shape == "down":
            u = 1 - np.sqrt(u)
        elif shape == "mid":
            u = np.clip(0.5 + 0.22 * r.standard_normal(), 0, 1)
        ln = max(8, int(r.uniform(*length) * SR))
        k = np.arange(ln)
        w = np.exp(-k / (ln / 3.0)) if win == "exp" else np.sin(np.pi * k / ln) ** 2
        put(out, r.standard_normal(ln) * w * r.uniform(*amp), at0 + (at1 - at0) * u)
    return filt(out, lo, hi)


VOWELS = {
    "ah": ((750, 1.0, 110), (1150, 0.55, 130), (2700, 0.22, 200), (3500, 0.1, 260)),
    "oh": ((450, 1.0, 90), (800, 0.6, 110), (2600, 0.12, 220)),
    "oo": ((320, 1.0, 70), (800, 0.35, 100), (2300, 0.08, 220)),
    "mm": ((260, 1.0, 60), (2100, 0.05, 300)),
    "uh": ((560, 1.0, 100), (1000, 0.55, 120), (2400, 0.15, 220)),
}


def vowel(x, v, floor=0.015):
    peaks = VOWELS[v]

    def g(_t, f):
        m = np.full(f.shape, floor)
        for fc, a, bw in peaks:
            m = m + a * np.exp(-0.5 * ((f - fc) / bw) ** 2)
        return m
    return spectral(x, g)


def choir(t, notes, v="ah", seed=0, vib=0.16, breath=0.05):
    x = np.zeros_like(t)
    for i, m in enumerate(notes):
        x += stack(t, hz(m), 4, 0.006, seed + 10 * i, vib, 5.1)
    return vowel(x, v) + filt(nz(len(t), seed + 99), 1500, 7000) * breath


def bell(t, f, decay, seed, bright=1.0):
    parts = [(f * 0.5, 0.35, 1.6), (f, 1.0, 1.0), (f * 1.19, 0.45, 0.8), (f * 1.5, 0.35, 0.7), (f * 2.0, 0.5 * bright, 0.6),
             (f * 2.52, 0.3 * bright, 0.45), (f * 3.01, 0.22 * bright, 0.35), (f * 4.17, 0.14 * bright, 0.25)]
    return ring(t, parts, decay, seed, 0.001)


def glass(t, f, decay, seed):
    parts = [(f, 1.0, 1.0), (f * 2.32, 0.5, 0.6), (f * 4.25, 0.3, 0.4), (f * 6.63, 0.18, 0.28), (f * 9.38, 0.1, 0.2)]
    return ring(t, parts, decay, seed, 0.002)


def metal(t, f, decay, seed):
    """Armour / gauntlet plate."""
    parts = [(f, 1.0, 1.0), (f * 1.73, 0.8, 0.8), (f * 2.41, 0.6, 0.6), (f * 3.37, 0.45, 0.5), (f * 4.62, 0.3, 0.35), (f * 5.9, 0.2, 0.3)]
    return ring(t, parts, decay, seed, 0.004)


def boom(dur, f0, f1, decay, seed, grit=0.6, crack=0.6, drop=0.05):
    t = tt(dur)
    low = thump(t, f0, f1, decay, drop)
    body = filt(nz(len(t), seed), 40, 520) * env(t, 0.002, decay * 0.5)
    snap = filt(nz(len(t), seed + 1), 1500, 8500) * env(t, 0.0005, 0.02) * crack
    return tanh(low + body * grit + snap, 1.6)


def whoosh(dur, f_lo, f_hi, seed, peak_at=0.45, down=True):
    t = tt(dur)
    a, b = (f_hi, f_lo) if down else (f_lo, f_hi)
    x = bandsweep(nz(len(t), seed), a * 0.6, a * 1.6, b * 0.6, b * 1.6, soft=1.2)
    u = t / dur
    return x * np.where(u < peak_at, (u / peak_at) ** 2, np.exp(-(u - peak_at) * 7 / (1 - peak_at)))


def growl(t, f, seed, drive=2.5, lo=40, hi=1400):
    """Power stone: distorted detuned saw."""
    return filt(tanh(stack(t, f, 3, 0.02, seed, 0.0), drive), lo, hi)


def tinnitus(t, f, seed, beat=6.0):
    """Mind stone: a pair of close high sines beating."""
    return osc(t, f, seed=seed) + osc(t, np.asarray(f) + beat, seed=seed + 1)


def warp(t, f, seed, lfo=5.5, depth=0.6):
    """Reality stone: ring-modulated detuned saws with a liquid pitch wobble."""
    x = stack(t, np.asarray(f) * 2 ** (depth / 12 * np.sin(2 * np.pi * lfo * t)), 3, 0.03, seed)
    return filt(x * osc(t, np.asarray(f) * 1.414, seed=seed + 5), 80, 5000)


def tick(dur, f, seed, wood=0.6):
    """Time stone: one clock tick (resonant click)."""
    t = tt(dur)
    click = filt(nz(len(t), seed), 1500, 9000) * env(t, 0.0002, 0.003)
    body = ring(t, [(f, 1, 1), (f * 2.7, .5, .5), (f * 5.1, .25, .3)], 0.03, seed + 1)
    return click * 0.8 + body * wood


def stone_chord(t, decay, seed, root=62):
    """The six stones together: one bell per stone over a D minor colour."""
    out = np.zeros_like(t)
    for i, m in enumerate((root, root + 3, root + 7, root + 10, root + 14, root + 19)):
        out += bell(late(t, 0.035 * i), hz(m), decay, seed + i, 0.7) * (t >= 0.035 * i) * (0.9 - 0.08 * i)
    return out


def cfilt(x, lo=0, hi=0, soft=1.5):
    """Static band filter over the whole buffer (circular: keeps a loop seamless)."""
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(np.fft.rfft(x) * band(lo, hi, soft)(f), len(x))


def loop_hz(f, length):
    """Nearest frequency that completes whole cycles over the loop (no click at the wrap)."""
    return max(1, round(f * length)) / length


def loop_finish(x, peak_db=-2.0):
    x = x - np.mean(x)
    return x * (10 ** (peak_db / 20) / (np.max(np.abs(x)) + 1e-9))


# --- arrival, body, melee ---------------------------------------------------------------------------

def arrival():
    """Thanos arrives: a portal tears open over the arena, a titan's weight hits the ground, a dark chord."""
    dur = 4.4
    t = tt(dur)
    tear = flange(bandsweep(nz(len(t), 100), 200, 900, 2500, 9500, curve=0.6), 2.0, 6.0, 0.9, 0.8) * swell(t, 1.5, 1.45, 0.05) * 0.6
    shine = np.zeros_like(t)
    for i, m in enumerate((74, 77, 81, 86, 89)):
        put(shine, glass(tt(1.6), hz(m), 0.8, 101 + i), 0.2 + 0.22 * i, 0.3)
    shine *= swell(t, 1.5, 1.4, 0.5)
    hum = filt(stack(t, 98 * 2 ** (np.clip(t / 1.5, 0, 1) * 5 / 12), 4, 0.008, 110), 40, 1500) * swell(t, 1.5, 1.4, 0.8) * 0.5
    x = tear + shine + hum
    put(x, boom(2.9, 78, 24, 0.95, 120, 1.0, 1.0, 0.07), 1.5)
    put(x, bursts(int(2.0 * SR), 90, 121, 0.0, 1.4, 500, 6000, (0.006, 0.04), "down", (0.1, 0.6)), 1.52, 0.5)
    chord = np.zeros_like(t)
    for i, m in enumerate((38, 45, 50, 53, 57)):
        chord += stack(t, hz(m), 5, 0.008, 130 + 10 * i, 0.05)
    chord = bandsweep(chord, 40, 400, 40, 1600, curve=0.5) * env(late(t, 1.5), 0.35, 1.4, 1.0) * (t >= 1.5) * 0.4
    put(x, stone_chord(tt(2.6), 1.4, 190, 62), 1.6, 0.15)
    return finish(reverb(x + chord, 2.6, 199, 0.34), -2, 0.01, 0.6)


def step(seed=200):
    """A 3.8-block titan's foot: deep thud, armour plates, a little grit."""
    dur = 0.55
    t = tt(dur)
    x = boom(dur, 105, 38, 0.12, seed, 0.8, 0.25)
    plate = metal(late(t, 0.012), 410 + seed % 7 * 9, 0.05, seed + 1) * (t > 0.012) * 0.10
    grit = bursts(len(t), 14, seed + 2, 0.005, 0.12, 900, 6500, (0.004, 0.02), "down", (0.1, 0.4))
    return finish(reverb(x + plate + grit * 0.35, 0.4, seed + 3, 0.16))


def step_2():
    return step(207)


def swing():
    dur = 0.6
    t = tt(dur)
    body = whoosh(dur, 160, 900, 300, 0.42)
    air = whoosh(dur, 900, 3200, 301, 0.38) * 0.35
    push = filt(nz(len(t), 302), 30, 180) * swell(t, 0.27, 0.25, 0.09) * 0.8
    return finish(body + air + push, -3)


def slam():
    """Ground slam (melee HAMMER, the stun shockwave): boom, crack and rubble."""
    dur = 1.6
    t = tt(dur)
    x = boom(dur, 85, 27, 0.42, 310, 0.9, 1.0)
    crack = filt(nz(len(t), 311), 700, 5000) * env(t, 0.001, 0.07) * (1 + 0.6 * np.sin(2 * np.pi * 41 * t))
    rubble = bursts(len(t), 60, 312, 0.03, 1.1, 500, 6000, (0.006, 0.04), "down", (0.1, 0.6))
    x = tanh(x + crack * 0.5, 1.4) + rubble * 0.5
    return finish(reverb(x, 1.4, 313, 0.28))


def hit():
    """Gauntlet fist lands on a hero."""
    dur = 0.6
    t = tt(dur)
    x = boom(dur, 150, 48, 0.1, 320, 0.7, 1.1)
    clank = metal(t, 730, 0.12, 321) * 0.16
    flesh = filt(nz(len(t), 322), 250, 2200) * env(t, 0.001, 0.035) * 0.6
    return finish(reverb(tanh(x + clank + flesh, 1.4), 0.45, 323, 0.18))


def hurt():
    """Thanos takes a hit: armour clang and a guttural growl (no words)."""
    dur = 0.7
    t = tt(dur)
    clang = metal(t, 520, 0.16, 330) * 0.35 + filt(nz(len(t), 331), 1200, 7000) * env(t, 0.0005, 0.01) * 0.4
    f = 78 * (1 + 0.12 * np.exp(-t / 0.08)) * (1 - 0.1 * t)
    voice = vowel(tanh(stack(t, f, 3, 0.012, 332, 0.25, 7.0), 2.0), "uh") * env(late(t, 0.03), 0.03, 0.22) * (t > 0.03)
    return finish(reverb(clang + voice * 1.1, 0.5, 333, 0.15))


def stun():
    """Staggered: heavy body knock, a ringing head and a groan sliding down."""
    dur = 1.5
    t = tt(dur)
    knock = boom(dur, 120, 40, 0.18, 340, 0.6, 0.6)
    ringing = (osc(t, 2240, seed=341) + 0.7 * osc(t, 2271, seed=342) + 0.4 * osc(t, 3310, seed=343)) * env(t, 0.02, 0.5) * 0.08
    f = 96 * 2 ** (-np.clip(t / 1.2, 0, 1) * 9 / 12)
    groan = vowel(tanh(stack(t, f, 3, 0.015, 344, 0.35, 5.5), 1.6), "oh") * env(late(t, 0.08), 0.12, 0.45) * (t > 0.08)
    return finish(reverb(knock + ringing + groan * 0.8, 1.0, 345, 0.25))


# --- Power stone ----------------------------------------------------------------------------------

def power_charge():
    dur = 1.4
    t = tt(dur)
    u = np.clip(t / 1.25, 0, 1)
    g = growl(t, 55 * 2 ** u, 400, 3.0) * u ** 1.6
    whine = osc(t, 380 * 2 ** (u * 2.2), seed=401) * u ** 2 * 0.18
    arcs = bursts(len(t), 160, 402, 0.0, 1.3, 1800, 11000, shape="up")
    cut = np.clip((dur - t) / 0.12, 0, 1)
    return finish((g + whine + arcs * 0.55) * cut, -2, 0.01, 0.05)


def power_impact():
    dur = 1.8
    t = tt(dur)
    x = boom(dur, 95, 24, 0.55, 410, 1.0, 1.2)
    blast = tanh(filt(nz(len(t), 411), 60, 3000) * env(t, 0.001, 0.12) * 2.0, 2.0)
    arcs = bursts(len(t), 90, 412, 0.02, 1.4, 2000, 11000, shape="down")
    purple = growl(t, 62, 413, 2.0, 40, 900) * env(t, 0.005, 0.35) * 0.5
    return finish(reverb(x + blast * 0.6 + arcs * 0.5 + purple, 1.6, 414, 0.3))


def power_wave():
    """A ring of force rolls outward: blast, then a long rumble whooshing past."""
    dur = 2.4
    t = tt(dur)
    x = boom(dur, 80, 26, 0.5, 420, 0.8, 0.9)
    roll = filt(nz(len(t), 421), 30, 220) * swell(t, 0.25, 0.2, 0.8) * 1.2
    rush = bandsweep(nz(len(t), 422), 2500, 7000, 120, 500, curve=0.5) * swell(t, 0.18, 0.15, 0.7) * 0.7
    arcs = bursts(len(t), 120, 423, 0.05, 2.0, 1800, 10000, shape="down")
    return finish(reverb(x + roll + rush + arcs * 0.4, 1.8, 424, 0.3))


# --- Space stone ----------------------------------------------------------------------------------

def space_open():
    """A portal tears open: glass partials climb, flanged air, blue hum."""
    dur = 1.4
    t = tt(dur)
    tear = flange(bandsweep(nz(len(t), 500), 300, 1200, 3000, 10000, curve=0.7), 1.5, 5.0, 1.3, 0.85) * swell(t, 0.7, 0.65, 0.4)
    shine = np.zeros_like(t)
    for i, m in enumerate((74, 78, 81, 86, 90)):
        put(shine, glass(tt(0.9), hz(m), 0.5, 501 + i), 0.08 * i, 0.5)
    hum = filt(stack(t, 147, 3, 0.004, 507) + 0.5 * osc(t, 294, seed=508), 60, 1500) * swell(t, 0.6, 0.55, 0.7) * 0.6
    return finish(reverb(tear * 0.7 + shine * 0.5 + hum, 1.2, 509, 0.32))


def space_step(seed=510):
    """Teleport: air sucked in, a pop, glass sparkle where he lands."""
    dur = 1.0
    t = tt(dur)
    x = bandsweep(nz(len(t), seed), 4000, 10000, 200, 900, curve=0.5) * swell(t, 0.24, 0.22, 0.012) * 0.8
    put(x, boom(0.6, 320, 70, 0.07, seed + 1, 0.5, 0.9), 0.24)
    for i in range(4):
        put(x, glass(tt(0.7), hz(84 + 4 * i), 0.35, seed + 2 + i), 0.25 + 0.03 * i, 0.18)
    return finish(reverb(x, 0.8, seed + 9, 0.28))


def space_pull():
    """Gravity: a deep tone falls, the air rushes toward the gauntlet."""
    dur = 1.9
    t = tt(dur)
    u = np.clip(t / 1.6, 0, 1)
    fall = filt(stack(t, 220 * 2 ** (-u * 2.0), 3, 0.01, 520), 40, 1800) * swell(t, 0.3, 0.28, 1.1) * 0.7
    rush = bandsweep(nz(len(t), 521), 5000, 12000, 150, 700, curve=0.7) * swell(t, 1.2, 1.1, 0.25)
    sub = osc(t, 41, seed=522) * swell(t, 0.8, 0.7, 0.6) * 0.6
    shine = glass(t, hz(79), 0.7, 523) * 0.2
    return finish(reverb(flange(fall + rush * 0.6, 1.0, 4.0, 0.7, 0.6) + sub + shine, 1.2, 524, 0.3))


def space_ward():
    """A shield of folded space: crystalline chord, fluttering hum."""
    dur = 1.4
    t = tt(dur)
    x = np.zeros_like(t)
    for i, m in enumerate((69, 76, 81, 88)):
        put(x, glass(tt(1.3), hz(m), 0.8, 530 + i), 0.02 * i, 0.45)
    x += filt(stack(t, 147, 4, 0.006, 535), 80, 2500) * (0.75 + 0.25 * np.sin(2 * np.pi * 11 * t)) * env(t, 0.05, 0.6, 0.3) * 0.5
    x += boom(dur, 140, 70, 0.08, 538, 0.3, 0.3) * 0.5
    return finish(reverb(x, 1.0, 539, 0.3))


# --- Reality stone --------------------------------------------------------------------------------

def reality_warp():
    """The world bends: ring-modulated saws, liquid wobble, granular stutter."""
    dur = 1.7
    t = tt(dur)
    w = warp(t, 98 * 2 ** (np.sin(2 * np.pi * 0.8 * t) * 4 / 12), 600, 6.5, 0.9) * swell(t, 0.5, 0.45, 0.8)
    goo = filt(nz(len(t), 601), 200, 1600) * (0.5 + 0.5 * np.sin(2 * np.pi * (3 + 9 * t) * t)) * env(t, 0.1, 0.7) * 0.5
    stutter = bursts(len(t), 70, 602, 0.1, 1.4, 600, 5000, (0.01, 0.04), "mid", (0.2, 0.8), "hann")
    low = osc(t, 49, seed=603) * swell(t, 0.5, 0.45, 0.6) * 0.5
    return finish(reverb(tanh(w + goo + stutter * 0.4 + low, 1.3), 1.2, 604, 0.3))


def reality_mirage():
    """Copies of him blink in: a soft swell echoed at shifted pitches, red pops."""
    dur = 1.5
    t = tt(dur)
    core = warp(t, 147, 610, 4.0, 0.4) * swell(t, 0.35, 0.3, 0.3) * 0.6 + filt(nz(len(t), 611), 1500, 6000) * swell(t, 0.3, 0.28, 0.15) * 0.3
    x = echoes(core, [(0.11, 0.6, 1.06), (0.23, 0.45, 0.94), (0.34, 0.32, 1.12), (0.46, 0.22, 0.89)])
    for i, a in enumerate((0.32, 0.43, 0.55, 0.66)):
        put(x, boom(0.3, 420 - 40 * i, 140, 0.04, 612 + i, 0.3, 0.6), a, 0.35)
    return finish(reverb(x, 1.0, 618, 0.3))


# --- Time stone -----------------------------------------------------------------------------------

def time_dome():
    """Time thickens: ticks slowing down, a green bell, a whoosh stretched low."""
    dur = 1.9
    t = tt(dur)
    x = np.zeros_like(t)
    at, gap, k = 0.0, 0.11, 0
    while at < 1.5:
        put(x, tick(0.15, 1700 - 90 * k, 700 + k, 0.5), at, 0.6 - 0.03 * k)
        at += gap
        gap *= 1.25
        k += 1
    x += bell(late(t, 0.05), hz(48), 1.4, 720) * (t >= 0.05) * 0.5
    x += speed(whoosh(dur, 400, 2500, 721, 0.25), 1.0 - 0.45 * np.clip(t / dur, 0, 1)) * 0.5
    x += osc(t, 65 * (1 - 0.3 * np.clip(t / dur, 0, 1)), seed=722) * env(t, 0.05, 0.9) * 0.4
    return finish(reverb(x, 1.5, 723, 0.32))


def time_rewind():
    """Back in time: a reversed bell swell, a tape chirp climbing, ticks running backwards."""
    dur = 1.7
    t = tt(dur)
    x = (bell(t, hz(55), 0.6, 730) + filt(nz(len(t), 731), 800, 6000) * env(t, 0.001, 0.25) * 0.3)[::-1] * 0.6
    x += filt(osc(t, 300 * 2 ** (np.clip(t / 1.4, 0, 1) * 3.5), "tri", seed=732), 200, 6000) * swell(t, 1.4, 1.3, 0.05) * 0.18
    at, gap, k = 1.45, 0.16, 0
    while at > 0.05:
        put(x, tick(0.12, 1400 + 60 * k, 740 + k, 0.45)[::-1], at, 0.4)
        at -= gap
        gap = max(gap * 0.82, 0.055)
        k += 1
    return finish(reverb(x, 1.0, 733, 0.28), -2, 0.002, 0.08)


# --- Mind stone -----------------------------------------------------------------------------------

def mind_gaze():
    """A golden beam: tinnitus building in the skull, then the strike."""
    dur = 1.6
    t = tt(dur)
    u = np.clip(t / 0.95, 0, 1)
    on = gate(t, 0, 1.0, 0.01, 0.06)
    x = tinnitus(t, 1500 * 2 ** (u * 0.6), 800, 5 + 9 * u) * u ** 2 * on * 0.25
    x += filt(nz(len(t), 801), 300, 2500) * (0.5 + 0.5 * np.sin(2 * np.pi * 8 * t)) * u ** 2 * on * 0.3
    s = late(t, 0.95)
    zap = osc(t, 900 * 2 ** (-np.clip(s / 0.35, 0, 1) * 2), seed=802) * 0.5 + filt(nz(len(t), 803), 1500, 9000) * 0.5
    x += zap * env(s, 0.003, 0.22) * (t > 0.95)
    x += growl(t, 165, 804, 1.6, 200, 3000) * env(s, 0.01, 0.4) * (t > 0.95) * 0.35
    return finish(reverb(x, 1.1, 805, 0.3))


def mind_pulse():
    """Psychic pulses: a throbbing yellow chord and three waves."""
    dur = 1.9
    t = tt(dur)
    chord = osc(t, 165, seed=810) + 0.7 * osc(t, 247, seed=811) + 0.5 * osc(t, 330, seed=812) + 0.3 * osc(t, 495, seed=813)
    x = chord * (0.55 + 0.45 * np.sin(2 * np.pi * 7.0 * t) ** 2) * env(t, 0.08, 0.9, 0.4) * 0.35
    x += tinnitus(t, 2600, 814, 7) * env(t, 0.2, 0.8) * 0.06
    for i, a in enumerate((0.15, 0.6, 1.05)):
        w = filt(nz(int(0.6 * SR), 815 + i), 80, 900) * env(tt(0.6), 0.02, 0.12) + thump(tt(0.6), 140, 55, 0.12) * 0.8
        put(x, w, a, 0.6)
    return finish(reverb(x, 1.3, 819, 0.3))


# --- Soul stone -----------------------------------------------------------------------------------

def soul_tether():
    """Orange chains of the soul: a ghostly choir glide over a breathing drone."""
    dur = 1.9
    t = tt(dur)
    glide = 2 ** (np.clip(t / 1.4, 0, 1) * 5 / 12)
    x = np.zeros_like(t)
    for i, m in enumerate((47, 54, 59)):
        x += stack(t, hz(m) * glide, 4, 0.008, 900 + 10 * i, 0.3, 4.2)
    x = vowel(x, "oo") * swell(t, 0.7, 0.6, 0.9)
    x += filt(nz(len(t), 940), 600, 5000) * (0.6 + 0.4 * np.sin(2 * np.pi * 2.3 * t)) * env(t, 0.2, 0.8) * 0.2
    x += osc(t, 61.7, seed=941) * env(t, 0.15, 1.0) * 0.4
    return finish(reverb(x, 1.6, 942, 0.38))


def soul_nova():
    """A choir swells and bursts into a blast of orange light."""
    dur = 3.2
    t = tt(dur)
    x = choir(t, (47, 54, 59, 63, 66), "ah", 950, 0.2, 0.06) * swell(t, 1.25, 1.2, 0.12) * 0.7
    b = tt(1.9)
    blast = boom(1.9, 100, 30, 0.45, 960, 0.8, 1.0) + filt(nz(len(b), 965), 2000, 9000) * env(b, 0.002, 0.25) * 0.4
    for i, m in enumerate((71, 75, 78)):
        blast += bell(b, hz(m), 0.9, 961 + i, 1.2) * 0.25
    put(x, tanh(blast, 1.3), 1.25)
    return finish(reverb(x, 1.6, 967, 0.32))


# --- gauntlet, phases, combos ---------------------------------------------------------------------

def stones_flare():
    """Final minute: all six stones flare at once."""
    dur = 2.8
    t = tt(dur)
    x = bandsweep(nz(len(t), 1000), 100, 500, 2000, 9000, curve=0.6) * swell(t, 0.55, 0.5, 0.02) * 0.6
    put(x, stone_chord(tt(2.2), 1.4, 1001), 0.55, 0.6)
    put(x, boom(2.2, 90, 28, 0.6, 1010, 0.8, 0.8), 0.55)
    return finish(reverb(x, 2.0, 1011, 0.35))


def phase():
    """A phase turns: the gauntlet hums up and hits, the stones answer."""
    dur = 2.4
    t = tt(dur)
    x = (filt(nz(len(t), 1020), 300, 4000) * 0.6 + growl(t, 73, 1021, 1.8, 40, 1200) * 0.5) * swell(t, 0.7, 0.68, 0.03)
    put(x, stone_chord(tt(1.7), 1.0, 1022, 57), 0.7, 0.45)
    put(x, boom(1.5, 85, 30, 0.45, 1030, 0.8, 0.7), 0.7)
    return finish(reverb(x, 1.6, 1031, 0.32))


def combo_portal_punch():
    """Space + Power: a portal tears beside him, the fist goes through and lands on the far side."""
    dur = 2.1
    t = tt(dur)
    x = flange(bandsweep(nz(len(t), 1100), 300, 1500, 3000, 10000, curve=0.6), 1.5, 5.0, 1.8, 0.8) * swell(t, 0.55, 0.5, 0.1) * 0.6
    x += growl(t, 55 * 2 ** np.clip(t / 0.6, 0, 1), 1101, 3.0) * swell(t, 0.6, 0.55, 0.03) * 0.6
    put(x, whoosh(0.25, 300, 2500, 1102, 0.8, False), 0.42, 0.6)
    put(x, boom(1.4, 110, 26, 0.5, 1103, 1.0, 1.2), 0.62)
    put(x, bursts(int(1.3 * SR), 80, 1104, 0.0, 1.0, 1800, 10000, shape="down"), 0.64, 0.5)
    for i, m in enumerate((81, 86, 90)):
        put(x, glass(tt(0.9), hz(m), 0.4, 1105 + i), 0.66, 0.25)
    return finish(reverb(x, 1.5, 1109, 0.3))


def combo_time_crush():
    """Time + Power: the clock slows to a stop, then the crushing blow."""
    dur = 2.5
    t = tt(dur)
    x = np.zeros_like(t)
    at, gap, k = 0.0, 0.09, 0
    while at < 1.15:
        put(x, tick(0.15, 1600 - 70 * k, 1110 + k), at, 0.55)
        at += gap
        gap *= 1.3
        k += 1
    x += speed(bell(t, hz(50), 1.2, 1130), 1.0 - 0.5 * np.clip(t / 1.3, 0, 1)) * 0.4
    x += growl(t, 49 * 2 ** np.clip(t / 1.3, 0, 1), 1131, 2.6) * swell(t, 1.3, 1.25, 0.03) * 0.55
    hitx = boom(1.1, 90, 22, 0.55, 1132, 1.0, 1.0)
    put(x, hitx, 1.3)
    pre = int(0.4 * SR)
    put(x, hitx[:pre][::-1] * np.linspace(0, 1, pre) ** 2, 0.9, 0.3)
    return finish(reverb(x, 1.6, 1133, 0.3))


def combo_soul_mirage():
    """Reality + Soul: ghost copies sing around the hero, wobbling."""
    dur = 2.3
    t = tt(dur)
    ch = choir(t, (52, 59, 64), "oo", 1140, 0.35, 0.08) * swell(t, 0.6, 0.55, 1.0) * 0.6
    w = warp(t, 123, 1150, 5.0, 0.7) * swell(t, 0.5, 0.45, 0.7) * 0.4
    x = echoes(ch + w, [(0.14, 0.55, 1.05), (0.28, 0.4, 0.95), (0.42, 0.28, 1.1)])
    for i, a in enumerate((0.5, 0.64, 0.78)):
        put(x, boom(0.3, 380 - 40 * i, 140, 0.04, 1160 + i, 0.3, 0.6), a, 0.3)
    return finish(reverb(x, 1.6, 1165, 0.36))


def combo_mind_step():
    """Mind + Space: a psychic whine locks on, he blinks behind the hero and strikes."""
    dur = 1.8
    t = tt(dur)
    u = np.clip(t / 0.6, 0, 1)
    x = tinnitus(t, 1700 * 2 ** (u * 0.5), 1170, 6 + 10 * u) * u ** 2 * gate(t, 0, 0.62, 0.01, 0.03) * 0.25
    x += bandsweep(nz(len(t), 1171), 4000, 10000, 200, 900, curve=0.5) * swell(t, 0.6, 0.55, 0.012) * 0.7
    put(x, boom(0.6, 320, 70, 0.07, 1172, 0.5, 0.9), 0.6)
    put(x, boom(1.0, 130, 40, 0.18, 1173, 0.8, 1.1), 0.78)
    z = tt(0.4)
    put(x, osc(z, 1000 * 2 ** (-z / 0.12), seed=1174) * env(z, 0.002, 0.12), 0.78, 0.4)
    return finish(reverb(x, 1.0, 1175, 0.28))


def combo_trinity():
    """Power + Space + Reality: three stones wind up together and detonate one after another."""
    dur = 3.0
    t = tt(dur)
    s = swell(t, 1.55, 1.5, 0.04)
    x = growl(t, 55 * 2 ** np.clip(t / 1.5, 0, 1), 1180, 2.8) * s * 0.45
    x += flange(bandsweep(nz(len(t), 1181), 300, 1200, 3000, 10000, curve=0.6), 1.5, 5.0, 1.1, 0.8) * s * 0.4
    x += warp(t, 98 * 2 ** (np.clip(t / 1.5, 0, 1) * 7 / 12), 1182, 6.0, 0.8) * s * 0.35
    for i, a in enumerate((1.55, 1.72, 1.89)):
        put(x, boom(1.0, 100 - 10 * i, 26, 0.45, 1183 + i, 1.0, 1.0), a, 0.9)
    put(x, stone_chord(tt(1.4), 0.9, 1190), 1.55, 0.25)
    return finish(reverb(x, 2.0, 1199, 0.33))


def combo_eternity():
    """Time + Mind + Soul: a bell tolls, the skull rings, a choir rises, then a white blast."""
    dur = 3.1
    t = tt(dur)
    s = swell(t, 1.8, 1.75, 0.05)
    x = choir(t, (50, 57, 62, 65), "ah", 1200, 0.22, 0.06) * s * 0.5
    x += tinnitus(t, 2100 * 2 ** (np.clip(t / 1.8, 0, 1) * 0.4), 1240, 8) * s * 0.08
    for i, a in enumerate((0.0, 0.6, 1.2)):
        put(x, bell(tt(1.6), hz(45), 1.0, 1241 + i), a, 0.35)
    put(x, boom(1.2, 95, 24, 0.55, 1250, 1.0, 1.1), 1.8)
    put(x, filt(nz(int(1.2 * SR), 1251), 1500, 10000) * env(tt(1.2), 0.002, 0.3), 1.8, 0.5)
    return finish(reverb(x, 2.0, 1252, 0.35))



def combo_time_volley():
    """Time + Space + Power: the clock races and stalls, six gates ring open, the stopped air hums with charge."""
    dur = 2.6
    t = tt(dur)
    x = np.zeros_like(t)
    at, gap = 0.0, 0.2
    for k in range(18):
        put(x, tick(0.12, 1450 + 45 * k, 1300 + k), at, 0.45 + 0.025 * k)
        at += gap
        gap = max(0.045, gap * 0.86)
    s = swell(t, 1.5, 1.45, 0.06)
    x += flange(bandsweep(nz(len(t), 1320), 400, 1800, 3000, 9000, curve=0.6), 1.5, 4.0, 1.4, 0.8) * s * 0.3
    x += growl(t, 52 * 2 ** np.clip(t / 1.5, 0, 1), 1321, 2.6) * s * 0.4
    put(x, bell(tt(1.1), hz(57), 0.9, 1322), 1.5, 0.4)
    for i, m in enumerate((79, 84, 88, 91, 95, 98)):
        put(x, glass(tt(0.9), hz(m), 0.5, 1323 + i), 1.52 + 0.05 * i, 0.16)
    z = tt(1.1)
    hold = osc(z, 110, "saw", seed=1330) * 0.5 + osc(z, 165.3, "saw", seed=1331) * 0.3
    put(x, filt(hold, 60, 900) * env(z, 0.02, 0.5), 1.5, 0.3)
    pre = int(0.3 * SR)
    rev = boom(0.6, 140, 40, 0.2, 1332, 0.6, 0.6)[:pre][::-1] * np.linspace(0, 1, pre) ** 2
    put(x, rev, 1.2, 0.5)
    return finish(reverb(x, 1.6, 1339, 0.32))


def combo_moon_fall():
    """Power + Space: a gate opens in the sky with a groan of stone, the moon's shards tear down through it."""
    dur = 3.2
    t = tt(dur)
    s = swell(t, 1.7, 1.65, 0.6)
    x = growl(t, 41 * 2 ** (np.clip(t / 1.7, 0, 1) * 7 / 12), 1340, 3.0, 30, 700) * s * 0.5
    x += flange(bandsweep(nz(len(t), 1341), 200, 900, 1600, 6000, curve=0.7), 2.0, 6.0, 0.7, 0.8) * s * 0.35
    for i, m in enumerate((74, 79, 86)):
        put(x, glass(tt(1.6), hz(m), 0.9, 1342 + i), 0.15 + 0.35 * i, 0.15)
    groan = speed(filt(nz(int(2.0 * SR), 1346), 50, 420), 0.6 + 0.2 * np.linspace(0, 1, int(2.0 * SR)))
    put(x, groan * env(tt(2.0), 0.4, 0.8), 0.6, 0.5)
    for i in range(4):
        put(x, whoosh(1.1, 120, 1600, 1350 + i, 0.75, True), 1.55 + 0.22 * i, 0.55 - 0.08 * i)
    put(x, boom(1.5, 70, 20, 0.7, 1360, 1.0, 0.7), 1.7, 0.8)
    put(x, bursts(int(1.4 * SR), 70, 1361, 0.0, 1.0, 300, 4000, shape="down", length=(0.004, 0.02)), 1.72, 0.4)
    return finish(reverb(x, 2.2, 1369, 0.35))


def combo_infinity():
    """All six stones: each timbre joins in turn (power, space, reality, soul, mind, time) into one white chord."""
    dur = 3.4
    t = tt(dur)
    x = np.zeros_like(t)
    cut = np.where(t < 2.0, 1.0, np.exp(-np.maximum(t - 2.0, 0) / 0.08))

    def lift(at):
        return np.clip((t - at) / 0.35, 0, 1) ** 2 * cut
    x += growl(t, 55 * 2 ** np.clip(t / 2.0, 0, 1), 1370, 2.8) * lift(0.0) * 0.3
    x += flange(bandsweep(nz(len(t), 1371), 500, 1800, 3000, 9000, curve=0.6), 1.5, 4.0, 1.2, 0.8) * lift(0.3) * 0.25
    x += warp(t, 98 * 2 ** (np.clip(t / 2.0, 0, 1) * 5 / 12), 1372, 6.0, 0.7) * lift(0.6) * 0.22
    x += choir(t, (50, 57, 62), "ah", 1373, 0.2, 0.05) * lift(0.9) * 0.35
    x += tinnitus(t, 1900 * 2 ** (np.clip(t / 2.0, 0, 1) * 0.4), 1374, 7) * lift(1.2) * 0.06
    at, k = 1.5, 0
    while at < 1.98:
        put(x, tick(0.1, 1600 + 80 * k, 1375 + k), at, 0.4)
        at += 0.08
        k += 1
    for i, a in enumerate((0.0, 0.3, 0.6, 0.9, 1.2, 1.5)):
        put(x, bell(tt(0.8), hz(62 + (0, 3, 7, 10, 14, 19)[i]), 0.5, 1390 + i, 0.6), a, 0.12)
    put(x, stone_chord(tt(1.4), 1.0, 1400), 2.0, 0.4)
    put(x, boom(1.4, 100, 22, 0.6, 1410, 1.0, 1.2), 2.0, 0.9)
    put(x, filt(nz(int(1.2 * SR), 1411), 1500, 10000) * env(tt(1.2), 0.002, 0.3), 2.0, 0.45)
    return finish(reverb(x, 2.2, 1419, 0.36))

# --- snap scene -----------------------------------------------------------------------------------

def world_stop():
    """Tick 0 of the snap: the world is sucked still, then a sub drop and a thin ringing silence."""
    dur = 1.9
    t = tt(dur)
    x = bandsweep(nz(len(t), 1300), 300, 2000, 3000, 12000, curve=0.8) * swell(t, 0.8, 0.78, 0.006) * 0.6
    r = tt(0.8)
    put(x, (filt(nz(len(r), 1301), 60, 900) * env(r, 0.001, 0.25))[::-1], 0.0, 0.7)
    x += thump(late(t, 0.8), 70, 22, 0.6, 0.15) * (t >= 0.8)
    x += (osc(t, 3520, seed=1302) + 0.5 * osc(t, 3530, seed=1303)) * env(late(t, 0.8), 0.1, 0.5) * (t >= 0.8) * 0.05
    return finish(reverb(x, 1.4, 1304, 0.25))


def gauntlet_raise():
    """The gauntlet comes up: plates grind, the six stones begin to hum."""
    dur = 1.5
    t = tt(dur)
    x = filt(nz(len(t), 1310), 400, 3500) * (0.5 + 0.5 * np.sin(2 * np.pi * (35 + 20 * t) * t)) ** 3 * env(t, 0.05, 0.6) * 0.5
    x += bursts(len(t), 18, 1311, 0.05, 0.8, 1500, 8000, (0.002, 0.006), "flat", (0.3, 0.9)) * 0.4
    x += filt(stack(t, 73.4 * 2 ** (np.clip(t / 1.3, 0, 1) * 7 / 12), 5, 0.01, 1312), 40, 2000) * swell(t, 1.3, 1.25, 0.3) * 0.5
    x += whoosh(dur, 200, 1500, 1313, 0.45, False) * 0.5
    return finish(reverb(x, 1.0, 1314, 0.25))


def stone_light():
    """One stone ignites (the scene plays it six times at the stone's pitch)."""
    dur = 1.2
    t = tt(dur)
    x = bell(t, hz(76), 0.7, 1320, 1.3) * 0.6 + glass(t, hz(88), 0.4, 1321) * 0.3
    x += filt(nz(len(t), 1322), 80, 900) * env(t, 0.01, 0.12) * 0.6 + thump(t, 180, 80, 0.12) * 0.5
    x += bursts(len(t), 40, 1323, 0.0, 0.6, 5000, 12000, (0.002, 0.006), "down", (0.2, 0.7)) * 0.4
    return finish(reverb(x, 1.0, 1324, 0.3))


def fingers():
    """Metal fingers fold for the snap: plates scrape, joints click, tension climbs."""
    dur = 1.0
    t = tt(dur)
    x = np.zeros_like(t)
    for i, a in enumerate((0.0, 0.18, 0.36)):
        c = tt(0.2)
        sc = filt(nz(len(c), 1330 + i), 1200 + 300 * i, 5000) * env(c, 0.02, 0.06) * (0.6 + 0.4 * np.sin(2 * np.pi * 90 * c))
        put(x, sc, a, 0.6)
        put(x, metal(tt(0.15), 900 + 140 * i, 0.04, 1340 + i), a + 0.15, 0.25)
    x += filt(stack(t, 110 * 2 ** (np.clip(t / 0.9, 0, 1) * 5 / 12), 4, 0.01, 1350), 60, 2500) * swell(t, 0.9, 0.85, 0.1) * 0.35
    return finish(reverb(x, 0.8, 1355, 0.22))


def snap_click():
    """THE SNAP: a dry, very sharp click, then a white blast and a ringing that will not stop."""
    dur = 3.6
    t = tt(dur)
    x = filt(nz(len(t), 1360), 1800, 12000) * env(t, 0.0001, 0.0025) * 1.8
    x += ring(t, [(2450, 1, 1), (3900, .6, .6), (6100, .3, .4)], 0.03, 1361) * 0.5
    b = tt(3.5)
    blast = boom(3.5, 70, 18, 1.2, 1362, 1.2, 1.0, 0.09) + filt(nz(len(b), 1363), 200, 12000) * env(b, 0.002, 0.45) * 0.6
    put(x, tanh(blast, 1.5), 0.06)
    x += (osc(t, 4186, seed=1364) + 0.6 * osc(t, 4193, seed=1365)) * env(late(t, 0.06), 0.4, 1.6) * (t >= 0.06) * 0.04
    return finish(reverb(x, 2.6, 1366, 0.38), -1.5, 0.0005, 0.2)


def dissolve():
    """Everything turns to dust: grains thicken then thin, wind, tones falling away."""
    dur = 6.5
    t = tt(dur)
    n = len(t)
    dust = bursts(n, 1400, 1400, 0.0, 6.0, 1500, 11000, (0.003, 0.02), "mid", (0.1, 0.8), "hann")
    dust += bursts(n, 500, 1401, 0.0, 6.0, 300, 2500, (0.01, 0.05), "mid", (0.1, 0.6), "hann") * 0.6
    wind = bandsweep(nz(n, 1402), 700, 4000, 150, 900, curve=0.8) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.35 * t)) * 0.4
    shape = np.clip(t / 1.2, 0, 1) * np.clip((dur - t) / 3.5, 0, 1) ** 1.5
    falling = np.zeros_like(t)
    for i, m in enumerate((62, 65, 69, 74)):
        falling += osc(t, hz(m) * 2 ** (-np.clip(t / 5.5, 0, 1) * (14 + 2 * i) / 12), seed=1403 + i) * (0.3 - 0.05 * i)
    falling *= env(t, 0.6, 1.8, 1.0) * 0.4
    return finish(reverb((dust + wind) * shape + falling, 2.4, 1410, 0.4), -2, 0.3, 0.6)


def void():
    """The empty universe (seamless 8 s loop): a sub you feel more than hear, slow air, far shimmer."""
    length = 8.0
    t = tt(length)
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * loop_hz(0.125, length) * t)
    sub = osc(t, loop_hz(32.0, length), seed=1500) * 0.6 + osc(t, loop_hz(48.0, length), seed=1501) * 0.25 * lfo
    air = cfilt(nz(len(t), 1502), 120, 900) * (0.6 + 0.4 * np.sin(2 * np.pi * loop_hz(0.25, length) * t + 1.0)) * 0.25
    far = np.zeros_like(t)
    for i, f in enumerate((1760.0, 2217.0, 2637.0)):
        far += osc(t, loop_hz(f, length), seed=1503 + i) * (0.5 + 0.5 * np.sin(2 * np.pi * loop_hz(0.125 * (i + 1), length) * t + i)) ** 3
    return loop_finish(reverb(sub + air + far * 0.012, 2.5, 1510, 0.35, 3000, wrap=True))


def od_lagna_arrive():
    """She forms out of the void: a warm major choir and bells, far from any stone's colour."""
    dur = 3.8
    t = tt(dur)
    x = choir(t, (45, 52, 57, 61, 64), "ah", 1600, 0.12, 0.04) * env(t, 1.2, 1.4, 0.8) * 0.6
    for i, m in enumerate((81, 85, 88, 93, 97)):
        put(x, bell(tt(2.0), hz(m), 0.9, 1610 + i, 0.8), 0.4 + 0.22 * i, 0.22)
    x += bandsweep(nz(len(t), 1620), 200, 1200, 1500, 8000, curve=0.7) * env(t, 1.0, 1.0, 0.5) * 0.25
    return finish(reverb(x, 2.6, 1621, 0.42), -2, 0.2, 0.6)


def od_lagna_line():
    """A line of hers begins: a soft breath of her choir under the subtitle."""
    dur = 2.4
    t = tt(dur)
    x = choir(t, (57, 64, 69), "mm", 1630, 0.1, 0.03) * env(t, 0.35, 0.8, 0.5) * 0.7
    x += bell(late(t, 0.1), hz(93), 0.8, 1640, 0.6) * (t >= 0.1) * 0.12
    return finish(reverb(x, 1.8, 1641, 0.4), -2, 0.1, 0.4)


def restore():
    """Golden restore: an arpeggio climbs, a warm chord blooms, the world comes back."""
    dur = 6.0
    t = tt(dur)
    x = np.zeros_like(t)
    for i, m in enumerate((57, 61, 64, 69, 73, 76, 81, 85, 88, 93)):
        put(x, bell(tt(2.5), hz(m), 1.0, 1700 + i, 0.9), 0.25 * i, 0.22)
    pad = np.zeros_like(t)
    for i, m in enumerate((45, 52, 57, 61)):
        pad += stack(t, hz(m), 5, 0.01, 1720 + 10 * i)
    x += filt(pad, 60, 2200) * env(late(t, 0.5), 1.6, 1.6, 1.6) * (t >= 0.5) * 0.35
    x += choir(t, (64, 69, 73), "ah", 1760, 0.12, 0.03) * env(late(t, 1.5), 1.2, 1.5, 1.0) * (t >= 1.5) * 0.35
    x += filt(nz(len(t), 1770), 4000, 12000) * env(t, 2.0, 1.5, 0.8) * 0.06
    put(x, boom(2.5, 80, 40, 0.6, 1780, 0.4, 0.2), 2.6, 0.5)
    return finish(reverb(x, 2.8, 1781, 0.4), -2, 0.05, 1.2)


def victory():
    """Thanos falls: the last stone hum dies, timpani roll, a brass chord turns major."""
    dur = 5.0
    t = tt(dur)
    x = growl(t, 73 * 2 ** (-np.clip(t / 1.2, 0, 1)), 1800, 2.0, 40, 1200) * env(t, 0.02, 0.5) * 0.4
    at, k = 0.6, 0
    while at < 1.9:
        r = tt(0.3)
        hitr = thump(r, 130, 70, 0.12) + filt(nz(len(r), 1810 + k), 100, 900) * env(r, 0.001, 0.05) * 0.4
        put(x, hitr, at, 0.15 + 0.35 * (at - 0.6) / 1.3)
        at += 0.07
        k += 1
    brass = np.zeros_like(t)
    for i, m in enumerate((38, 45, 50, 54, 57)):
        brass += stack(t, hz(m), 5, 0.008, 1860 + 10 * i, 0.06)
    x += bandsweep(brass, 40, 600, 40, 2600, curve=0.4) * env(late(t, 1.9), 0.25, 1.6, 1.2) * (t >= 1.9) * 0.45
    x += choir(t, (62, 66, 69), "ah", 1920, 0.14, 0.03) * env(late(t, 2.0), 0.6, 1.4, 1.0) * (t >= 2.0) * 0.3
    put(x, boom(3.0, 90, 34, 0.6, 1850, 0.8, 0.9), 1.9)
    return finish(reverb(x, 2.4, 1930, 0.35), -2, 0.005, 0.8)


def countdown():
    """The last ten seconds: a heavy clock tick over a low D."""
    dur = 0.6
    t = tt(dur)
    x = tick(dur, 1250, 1950, 0.8)
    x += osc(t, hz(38), seed=1951) * env(t, 0.004, 0.18) * 0.6 + osc(t, hz(50), seed=1952) * env(t, 0.004, 0.12) * 0.25
    return finish(reverb(x, 0.6, 1953, 0.2))


# --- music ------------------------------------------------------------------------------------------
# D minor. The battle loop (100 BPM, 8 bars, 19.2 s) and the final-minute loop (128 BPM, 8 bars, 15 s:
# sixteenth-note bass, a clock on every eighth, snare and a climbing string tremolo) share instruments:
# spiccato low strings, brass, choir, taiko, gauntlet anvil. Notes are rendered one by one into a
# circular buffer (anything past the end wraps to the start), the drone completes whole cycles and the
# reverb is circular, so the loop point is seamless.

PROG_BATTLE = ((38, 41, 45), (34, 38, 41), (31, 34, 38), (33, 37, 40), (38, 41, 45), (34, 38, 41), (39, 43, 46), (33, 37, 40))
PROG_FINAL = ((38, 41, 45), (39, 43, 46), (34, 38, 41), (33, 37, 40), (38, 41, 45), (39, 43, 46), (31, 34, 38), (33, 37, 40))


def m_string(dur, m, seed, bright=1400):
    t = tt(dur)
    x = stack(t, hz(m), 4, 0.006, seed) * 0.8 + osc(t, hz(m) / 2, seed=seed + 7) * 0.5
    return filt(x, 30, bright) * env(t, 0.006, dur * 0.45)


def m_brass(dur, chord, seed, up=12):
    t = tt(dur + 0.4)
    x = np.zeros_like(t)
    for i, m in enumerate(chord):
        x += stack(t, hz(m + up), 5, 0.007, seed + 10 * i, 0.05)
    return bandsweep(x, 40, 500, 40, 2200, curve=0.35) * gate(t, 0, dur + 0.35, 0.18, 0.4)


def m_choir(dur, chord, seed, up=24, v="ah"):
    t = tt(dur + 0.6)
    return choir(t, [m + up for m in chord], v, seed, 0.14, 0.03) * gate(t, 0, dur + 0.5, 0.6, 0.6)


def m_taiko(seed, big=1.0):
    t = tt(0.9)
    x = thump(t, 115, 46, 0.32 * big) * 1.1 + filt(nz(len(t), seed), 60, 700) * env(t, 0.001, 0.07) * 0.6
    return x + filt(nz(len(t), seed + 1), 1200, 5000) * env(t, 0.0005, 0.008) * 0.25


def m_tom(seed):
    t = tt(0.35)
    return thump(t, 240, 130, 0.07) * 0.6 + filt(nz(len(t), seed), 150, 1500) * env(t, 0.001, 0.03) * 0.3


def m_anvil(seed):
    t = tt(0.8)
    return metal(t, 980, 0.3, seed) * 0.35 + filt(nz(len(t), seed + 1), 2000, 9000) * env(t, 0.0005, 0.01) * 0.3


def m_snare(seed):
    t = tt(0.4)
    return filt(nz(len(t), seed), 900, 7000) * env(t, 0.001, 0.08) * 0.6 + thump(t, 220, 160, 0.05) * 0.3


def m_swell(dur, seed):
    """Reversed cymbal into the downbeat."""
    t = tt(dur)
    return (filt(nz(len(t), seed), 3000, 12000) * env(t, 0.001, dur * 0.5))[::-1]


def music(bpm, prog, final, seed):
    beat = 60.0 / bpm
    bar = beat * 4
    length = bar * len(prog)
    n = int(round(length * SR))
    strings, brass, voices, drums, high = (np.zeros(n) for _ in range(5))
    k = seed
    for b, chord in enumerate(prog):
        r, third, fifth = chord
        t0 = b * bar
        if final:
            pat = (r, r, r + 12, r, r + 1 if b % 2 else r + 7, r, r + 12, r + 10) * 2
            step_ = beat / 4
        else:
            pat = (r, r, r + 12, r, third, r, fifth, r + 12)
            step_ = beat / 2
        for i, m in enumerate(pat):
            wrap(strings, m_string(step_ * 1.6, m, k, 1700 if final else 1300), t0 + i * step_, 1.0 if i % 4 == 0 else 0.7)
            k += 1
        if b % 2 == 0 or final:
            wrap(brass, m_brass(bar * (1 if final else 2) - 0.25, chord, k), t0)
            k += 50
        if final or b >= 4:
            wrap(voices, m_choir(bar - 0.1, chord, k, 24, "ah" if final else "oo"), t0)
            k += 50
        for h in ((0, 2, 2.5, 3) if final else (0, 2)):
            wrap(drums, m_taiko(k, 1.0 if h in (0, 2) else 0.6), t0 + h * beat, 1.0 if h == 0 else 0.75)
            k += 2
        for e in range(8):
            if final or e % 2 == 1:
                wrap(drums, m_tom(k), t0 + e * beat / 2, 0.35 if final else 0.25)
                k += 1
        if final:
            for s in (1, 3):
                wrap(drums, m_snare(k), t0 + s * beat, 0.5)
                k += 1
            for e in range(8):
                wrap(high, tick(0.1, 2100, k, 0.3), t0 + e * beat / 2, 0.5 if e % 2 == 0 else 0.3)
                k += 1
            for e in range(16):
                m = 74 + b // 2 + (12 if e % 2 == 1 and b >= 6 else 0)
                wrap(high, m_string(beat / 4 * 1.2, m, k, 4000), t0 + e * beat / 4, 0.12)
                k += 1
        if b % 2 == 1:
            wrap(drums, m_anvil(k), t0 + beat, 0.6)
            k += 2
    wrap(drums, m_swell(bar / 2, k), length - bar / 2, 0.5)
    t = np.arange(n) / SR
    drone = osc(t, loop_hz(hz(26), length), seed=k + 1) * 0.35 + osc(t, loop_hz(hz(38), length), seed=k + 2) * 0.2
    mix = strings * 0.55 + brass * (0.4 if final else 0.33) + voices * (0.28 if final else 0.22) + drums * 0.9 + high * 0.25 + drone
    return loop_finish(reverb(mix, 2.2, k + 3, 0.26, 5000, wrap=True))


def music_battle():
    return music(100, PROG_BATTLE, False, 2000)


def music_final():
    return music(128, PROG_FINAL, True, 3000)


CUES = {
    "arrival": arrival, "step": step, "step_2": step_2, "swing": swing, "slam": slam, "hit": hit, "hurt": hurt, "stun": stun,
    "power_charge": power_charge, "power_impact": power_impact, "power_wave": power_wave,
    "space_open": space_open, "space_step": space_step, "space_pull": space_pull, "space_ward": space_ward,
    "reality_warp": reality_warp, "reality_mirage": reality_mirage, "time_dome": time_dome, "time_rewind": time_rewind,
    "mind_gaze": mind_gaze, "mind_pulse": mind_pulse, "soul_tether": soul_tether, "soul_nova": soul_nova,
    "stones_flare": stones_flare, "phase": phase,
    "combo_portal_punch": combo_portal_punch, "combo_time_crush": combo_time_crush, "combo_soul_mirage": combo_soul_mirage,
    "combo_mind_step": combo_mind_step, "combo_trinity": combo_trinity, "combo_eternity": combo_eternity,
    "combo_time_volley": combo_time_volley, "combo_moon_fall": combo_moon_fall, "combo_infinity": combo_infinity,
    "world_stop": world_stop, "gauntlet_raise": gauntlet_raise, "stone_light": stone_light, "fingers": fingers,
    "snap_click": snap_click, "dissolve": dissolve, "void": void, "od_lagna_arrive": od_lagna_arrive,
    "od_lagna_line": od_lagna_line, "restore": restore, "victory": victory, "countdown": countdown,
    "music_battle": music_battle, "music_final": music_final,
}
LOOPS = ("void", "music_battle", "music_final")

# Momentary loudness targets (loudest 100 ms RMS, dBFS) by role, then soft-limited under the ceiling.
# signature/scene -7.5..-10, impacts -8.5..-10, stone casts -10.5..-12, body/motion -12..-13, beds -14..-24.
LOUDNESS = {
    "arrival": -8.5, "snap_click": -7.5, "world_stop": -10.0, "stones_flare": -9.0, "phase": -10.0, "victory": -9.5,
    "restore": -11.0, "dissolve": -12.0, "od_lagna_arrive": -12.0, "od_lagna_line": -16.0, "gauntlet_raise": -12.0,
    "stone_light": -12.0, "fingers": -13.0, "void": -24.0,
    "slam": -9.0, "power_impact": -9.0, "power_wave": -9.5, "hit": -10.0, "soul_nova": -9.5,
    "combo_portal_punch": -9.0, "combo_time_crush": -9.0, "combo_trinity": -8.5, "combo_eternity": -8.5,
    "combo_soul_mirage": -10.5, "combo_mind_step": -10.0, "combo_time_volley": -10.0, "combo_moon_fall": -9.0,
    "combo_infinity": -8.5,
    "power_charge": -11.0, "space_open": -11.5, "space_step": -11.5, "space_pull": -11.0, "space_ward": -12.0,
    "reality_warp": -11.0, "reality_mirage": -12.0, "time_dome": -11.0, "time_rewind": -11.5, "mind_gaze": -11.0,
    "mind_pulse": -11.5, "soul_tether": -11.5,
    "step": -12.5, "step_2": -12.5, "swing": -13.0, "hurt": -12.0, "stun": -11.0, "countdown": -13.0,
    "music_battle": -14.0, "music_final": -13.0,
}


# --- packaging ------------------------------------------------------------------------------------

def committed(name):
    """Base64 text of a committed cue (single file or ordered parts), or None."""
    single = OUT / (name + ".ogg.b64")
    if single.exists():
        return single.read_text()
    parts = sorted(OUT.glob(name + ".ogg.b64.part*"), key=lambda p: int(p.name.rsplit("part", 1)[1]))
    return "".join(p.read_text() for p in parts) if parts else None


def write(name, ogg):
    text = base64.encodebytes(ogg.read_bytes()).decode("ascii")
    for old in [OUT / (name + ".ogg.b64")] + list(OUT.glob(name + ".ogg.b64.part*")):
        if old.exists():
            old.unlink()
    if len(text) <= PART:
        (OUT / (name + ".ogg.b64")).write_text(text)
        return 1
    chunks, cur = [], ""
    for ln in text.splitlines(keepends=True):
        if len(cur) + len(ln) > PART:
            chunks.append(cur)
            cur = ""
        cur += ln
    chunks.append(cur)
    for i, c in enumerate(chunks):
        (OUT / ("%s.ogg.b64.part%d" % (name, i))).write_text(c)
    return len(chunks)


def build(tmp, wav_dir=None, only=None):
    res = {}
    for name, fn in CUES.items():
        if only and name not in only:
            continue
        x = d.master(fn(), LOUDNESS[name])
        assert np.isfinite(x).all() and np.max(np.abs(x)) > 0.02, name
        w = Path(tmp) / (name + ".wav")
        o = Path(tmp) / (name + ".ogg")
        d.write_wav(w, x)
        if wav_dir:
            os.makedirs(wav_dir, exist_ok=True)
            d.write_wav(Path(wav_dir) / (name + ".wav"), x)
        d.encode(w, o)
        res[name] = o
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--wav")
    ap.add_argument("--only")
    a = ap.parse_args()
    only = set(a.only.split(",")) if a.only else None
    with tempfile.TemporaryDirectory() as tmp:
        built = build(tmp, a.wav, only)
        bad = 0
        for name, ogg in built.items():
            if a.check:
                text = committed(name)
                if text is None:
                    print("missing", name)
                    bad += 1
                    continue
                c = Path(tmp) / (name + ".committed.ogg")
                c.write_bytes(base64.b64decode(text))
                if hashlib.sha256(d.decoded_pcm(c)).digest() != hashlib.sha256(d.decoded_pcm(ogg)).digest():
                    print("differs", name)
                    bad += 1
            else:
                OUT.mkdir(parents=True, exist_ok=True)
                parts = write(name, ogg)
                print("%-20s %6.2f s %7d B%s" % (name, len(d.decoded_pcm(ogg)) / 2 / SR, ogg.stat().st_size,
                                                 " (%d parts)" % parts if parts > 1 else ""))
        print("%d cues %s" % (len(built), "checked" if a.check else "written"))
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
