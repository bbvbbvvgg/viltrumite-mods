#!/usr/bin/env python3
"""The Ghost Rider's sounds: original procedural synthesis (numpy only, no recordings, no film or game audio), CC0
(own work). Reuses the primitives of tools/sfx/jinwoo.py, tools/sfx/captain.py and tools/sfx/red_hulk.py.

Usage: python3 tools/sfx/ghost_rider.py [--wav-dir DIR] [--package] [--only name,name]
  --package  encodes each WAV (ffmpeg libvorbis, mono 44.1 kHz) into
             viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/ghost_rider/<name>.ogg.b64; a file that
             would not fit one GitHub push request is split into <name>.ogg.b64.part0..N (joined by decodeBinaryAssets).
Deterministic: every recipe seeds its own RNG.
The Hell Cycle is a demonic V-twin: uneven firing pulses (45 degree twin, 315/405 degree spacing) through an exhaust
resonance, a furnace roar under it and a low growl in the pipes. The engine, skid and flame loops are seamless (the
tail is cross-faded into the head; HellCycleEngineSound loops and re-pitches them). The chain is a jangle of short
inharmonic metal partials; the hellfire is the furnace, crackle and sizzle palette. No recipe imitates speech: his
lines are text subtitles only (HeroSpeech); groans and the stare's whispers are non-verbal formant noise, never words,
never TTS. No vanilla Minecraft sound is used.
"""
import argparse
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from jinwoo import (SR, ROOT, t_axis, rng, noise, env_adsr, exp_env, lowpass, highpass, sweep_band, sine, saw,  # noqa: E402
                    place, silence, master, whoosh, thud, click, formant_voice, write_wav, stft_filter, band)
from captain import reverb, boom  # noqa: E402
import red_hulk  # noqa: E402
from red_hulk import crackle, furnace, sizzle, hiss, sub  # noqa: E402

OUT = ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/ghost_rider'


# ------------------------------------------------------------------ building blocks

def loop(make, dur, xfade=0.25):
    """A seamless loop of dur seconds: render dur + xfade, then cross-fade the tail over the head (equal power)."""
    y = make(dur + xfade)
    n, x = int(dur * SR), int(xfade * SR)
    out = y[:n].copy()
    w = np.linspace(0, 1, x)
    out[:x] = out[:x] * np.sqrt(w) + y[n:n + x] * np.sqrt(1 - w)
    return out


def master_loop(x, peak=0.8, drive=1.3):
    """master() without the fades (they would click at the loop point)."""
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-9)
    return np.tanh(x * drive) / np.tanh(drive) * peak


def vtwin(dur, rpm, seed, rough=0.08, body=1.0, pipe=420.0, bark=1.0):
    """Exhaust pulses of a 45 degree V-twin at rpm (4-stroke: one cycle per two revolutions, two uneven bangs)."""
    cycle = 120.0 / rpm(0) if callable(rpm) else 120.0 / rpm
    y = silence(dur)
    r = rng(seed)
    t, k = 0.0, 0
    while t < dur:
        rp = rpm(t) if callable(rpm) else rpm
        cycle = 120.0 / rp
        for frac, amp in ((0.0, 1.0), (315.0 / 720.0, 0.82)):
            at = t + frac * cycle + r.normal(0, rough * cycle * 0.08)
            if 0 <= at < dur:
                d = min(0.12, cycle * 1.6)
                n = int(d * SR)
                tt = np.arange(n) / SR
                f0 = 55 + rp / 60.0 * 0.9
                pulse = np.sin(2 * np.pi * f0 * tt * (1 - 0.25 * tt / d)) * np.exp(-tt / (d * 0.28))
                pulse += r.standard_normal(n) * np.exp(-tt / (d * 0.12)) * 0.55 * bark
                place(y, pulse * amp * r.uniform(1 - rough, 1 + rough) * body, at)
        t += cycle
        k += 1
    y = lowpass(y, 1800) + 0.35 * stft_filter(y, lambda tt, f: band(pipe, 3.0)(f))
    return y


def hell_growl(dur, f0, seed, amount=0.6, vowels='uou'):
    """A low demonic growl in the pipes (non-verbal)."""
    y = formant_voice(dur, f0, vowels, seed, growl=amount)
    return lowpass(y, 900)


def metal(dur, base, seed, decay=0.25, n=6, bright=1.0):
    """A struck metal piece: inharmonic partials with fast decays."""
    r = rng(seed)
    t = t_axis(dur)
    y = np.zeros_like(t)
    ratios = [1.0, 2.76, 5.4, 8.93, 13.34, 18.64][:n]
    for i, k in enumerate(ratios):
        f = base * k * r.uniform(0.97, 1.03)
        y += np.sin(2 * np.pi * f * t + r.random() * 6) * np.exp(-t / (decay / (1 + i * 0.6))) * (0.8 ** i) * (bright if i > 1 else 1.0)
    return y * exp_env(len(t), 0.0008, decay * 2)


def jangle(dur, seed, links=14, spread=0.25, base=1900, gain=0.5):
    """Chain links knocking together: many tiny metal ticks spread in time."""
    y = silence(dur)
    r = rng(seed)
    for _ in range(links):
        at = r.uniform(0, spread)
        place(y, metal(0.12, base * r.uniform(0.7, 1.4), int(r.integers(1e6)), 0.03, 4) * r.uniform(0.3, 1.0) * gain, at)
    return y


def whip_crack(dur, seed, at=0.0):
    """A whip crack: a very short broadband snap with a little tail."""
    y = silence(dur)
    n = int(0.012 * SR)
    snap = rng(seed).standard_normal(n) * np.exp(-np.arange(n) / (0.002 * SR))
    place(y, highpass(np.concatenate([snap, np.zeros(int(0.05 * SR))]), 1200) * 2.2, at)
    return y


def fire_whoomp(dur, seed, f0=180, f1=1400, attack=0.04):
    """Fire catching: a dark rising breath of noise with a low body."""
    y = whoosh(dur, f0, f1, 1.4, seed=seed, attack=attack, decay=dur / 2.5, color='pink') * 1.2
    y += furnace(dur, seed + 1, 600) * exp_env(int(dur * SR), attack * 1.5, dur / 2) * 0.7
    y += crackle(dur, 35, seed + 2, gain=0.3)
    return y


def creak(dur, seed, f0=180):
    """Leather creak: a rough, slowly pitched friction buzz."""
    t = t_axis(dur)
    src = saw(lambda tt: f0 * (1 + 0.15 * np.sin(2 * np.pi * 2.2 * tt)), dur, seed) * (1 + 0.8 * np.sign(np.sin(2 * np.pi * 37 * t)))
    y = stft_filter(src, lambda tt, f: band(1300, 2.5)(f) + 0.4 * band(2600, 3.0)(f))
    return y * env_adsr(len(t), 0.04, 0.1, 0.6, dur * 0.4)


def whisper(dur, seed, vowels='eaoe'):
    """Non-verbal whispering: breath noise through moving vowel formants, no words."""
    src = noise(dur, seed, 'pink')
    F = {'a': (800, 1150, 2900), 'o': (450, 800, 2830), 'u': (325, 700, 2530), 'e': (400, 1600, 2700)}

    def g(tt, f):
        u = np.clip(tt / dur, 0, 0.999) * (len(vowels) - 1)
        i = int(u)
        w = u - i
        a = np.array(F[vowels[i]])
        b = np.array(F[vowels[min(i + 1, len(vowels) - 1)]])
        fm = a * (1 - w) + b * w
        out = np.zeros_like(f)
        for k, fc in enumerate(fm):
            out += (1.0, 0.7, 0.4)[k] / (1 + ((f - fc) / (fc / 10)) ** 2)
        return out
    t = t_axis(dur)
    am = 0.5 + 0.5 * np.abs(np.sin(2 * np.pi * 2.7 * t + rng(seed).random() * 6))
    return highpass(stft_filter(src, g), 300) * am


# ------------------------------------------------------------------ the Hell Cycle (loops)

def engine_idle():
    def make(d):
        y = vtwin(d, 900, 5000, rough=0.12, body=1.2, pipe=300)
        y += furnace(d, 5001, 450, 3.0) * 0.25
        y += hell_growl(d, lambda t: 46 + 3 * np.sin(2 * np.pi * 0.7 * t), 5002, 0.5) * 0.18
        return y
    return master_loop(loop(make, 2.0), 0.75, 1.4)


def engine_low():
    def make(d):
        y = vtwin(d, 2600, 5010, rough=0.08, body=1.0, pipe=380)
        y += furnace(d, 5011, 700, 5.0) * 0.3
        y += hell_growl(d, lambda t: 62 + 4 * np.sin(2 * np.pi * 0.9 * t), 5012, 0.7, 'uoau') * 0.22
        return y
    return master_loop(loop(make, 1.6), 0.8, 1.6)


def engine_high():
    def make(d):
        y = vtwin(d, 6400, 5020, rough=0.05, body=0.9, pipe=520, bark=1.3)
        y += sweep_band(noise(d, 5021, 'pink'), 900, 1300, 3.0) * 0.25
        y += furnace(d, 5022, 1100, 9.0) * 0.35
        y += hell_growl(d, lambda t: 96 + 5 * np.sin(2 * np.pi * 1.3 * t), 5023, 0.8, 'aoaa') * 0.22
        return y
    return master_loop(loop(make, 1.4), 0.82, 1.9)


def skid():
    def make(d):
        t = t_axis(d)
        f = 1350 + 140 * np.sin(2 * np.pi * 3.1 * t) + 90 * np.sin(2 * np.pi * 7.3 * t + 1.0)
        squeal = sine(lambda tt: 1350 + 140 * np.sin(2 * np.pi * 3.1 * tt) + 90 * np.sin(2 * np.pi * 7.3 * tt + 1.0), d)
        squeal = squeal * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 11 * t))) * 0.35
        grit = stft_filter(noise(d, 5031), lambda tt, ff: band(1800, 1.5)(ff)) * 0.8
        flame = highpass(furnace(d, 5032, 2000, 13.0), 600) * 0.4
        del f
        return squeal + grit + flame + crackle(d, 60, 5033, gain=0.25)
    return master_loop(loop(make, 2.0), 0.7, 1.3)


def flame_roar():
    def make(d):
        y = furnace(d, 5040, 900, 4.0) * 1.0 + highpass(noise(d, 5041, 'pink'), 1500) * 0.15
        y += crackle(d, 45, 5042, gain=0.35)
        return y
    return master_loop(loop(make, 3.0, 0.4), 0.7, 1.2)


# ------------------------------------------------------------------ the Hell Cycle (one-shots)

def bike_form():
    d = 2.4
    y = fire_whoomp(d, 5100, 120, 1800, 0.25) * 1.2
    y += boom(d, 90, 30, 0.4, 5101) * 0.7
    for i, at in enumerate((0.35, 0.55, 0.7, 0.9, 1.05)):
        place(y, metal(0.6, 210 + 60 * i, 5110 + i, 0.18) * 0.45, at)
    place(y, jangle(0.8, 5120, 18, 0.5, 1500, 0.4), 0.3)
    rev = vtwin(1.2, lambda t: 1200 + 3800 * np.sin(np.pi * min(t / 1.0, 1.0)) ** 2, 5130, 0.06, 1.0, 420)
    place(y, rev * env_adsr(len(rev), 0.05, 0.2, 0.8, 0.4) * 0.9, 1.15)
    return master(reverb(y, 0.8, 0.25), 0.92, 1.8)


def bike_burnout():
    d = 2.0
    y = furnace(d, 5140, 800, 5.0) * env_adsr(int(d * SR), 0.02, 0.4, 0.5, 1.4) * 0.9
    y += whoosh(d, 1400, 200, 1.5, seed=5141, attack=0.05, decay=0.8, color='pink') * 0.8
    y += crackle(d, 50, 5142, gain=0.4) * np.linspace(1, 0.1, int(d * SR))
    for i, at in enumerate((0.2, 0.5, 0.75)):
        place(y, metal(0.5, 260 - 40 * i, 5150 + i, 0.2) * 0.3, at)
    place(y, sizzle(1.2, 5160, 0.5) * 0.4, 0.4)
    return master(reverb(y, 0.6, 0.25), 0.85, 1.5)


def ignition():
    d = 1.6
    y = silence(d)
    for i in range(5):
        place(y, thud(0.09, 160, 90, 0.03, 5200 + i) * 0.5 + click(0.09, 5210 + i, 2500) * 0.3, 0.05 + i * 0.075)
    rev = vtwin(1.15, lambda t: 900 + 3200 * np.exp(-((t - 0.35) / 0.25) ** 2) + 600 * min(t, 1.0), 5220, 0.08, 1.1, 360)
    place(y, rev * env_adsr(len(rev), 0.01, 0.2, 0.8, 0.3), 0.42)
    place(y, fire_whoomp(0.7, 5230, 200, 1500, 0.05) * 0.6, 0.42)
    return master(reverb(y, 0.4, 0.18), 0.9, 1.7)


def backfire():
    d = 0.7
    y = boom(d, 140, 60, 0.12, 5240, debris=False) * 1.2 + click(d, 5241, 900) * 1.4
    place(y, fire_whoomp(0.5, 5242, 400, 2200, 0.01) * 0.6, 0.01)
    return master(reverb(y, 0.35, 0.2), 0.95, 2.2)


def shift():
    d = 0.4
    y = metal(d, 320, 5250, 0.06, 4) * 0.7 + thud(d, 120, 70, 0.04, 5251) * 0.8 + click(d, 5252, 1800) * 0.5
    return master(y, 0.75, 1.4)


def boost():
    d = 1.5
    y = fire_whoomp(d, 5260, 250, 3200, 0.06) * 1.3
    rev = vtwin(d, lambda t: 4200 + 3200 * min(t / 0.6, 1.0), 5261, 0.05, 0.9, 520, 1.3)
    y += rev * env_adsr(len(rev), 0.02, 0.3, 0.7, 0.6) * 0.8
    y += sub(d, 70, 35, 0.4, 5262) * 0.6
    return master(reverb(y, 0.5, 0.2), 0.92, 1.9)


def bike_jump():
    d = 0.7
    y = thud(d, 110, 60, 0.06, 5270) * 0.7 + metal(d, 180, 5271, 0.08, 3) * 0.4
    y += whoosh(d, 300, 1500, 2.0, seed=5272, attack=0.05, decay=0.25) * 0.6
    return master(y, 0.8, 1.4)


def bike_land():
    d = 1.0
    y = boom(d, 95, 32, 0.22, 5280) * 1.1 + metal(d, 150, 5281, 0.15, 4) * 0.5
    place(y, jangle(0.4, 5282, 8, 0.15, 1400, 0.35), 0.02)
    place(y, crackle(0.6, 50, 5283, gain=0.35), 0.03)
    return master(reverb(y, 0.4, 0.2), 0.92, 2.0)


def bike_crash():
    d = 1.4
    y = boom(d, 85, 28, 0.3, 5290) * 1.3
    for i, at in enumerate((0.0, 0.06, 0.15, 0.27, 0.4)):
        place(y, metal(0.6, 140 + 90 * i, 5291 + i, 0.2, 6, 1.2) * 0.55, at)
    place(y, jangle(0.7, 5300, 16, 0.4, 1600, 0.4), 0.05)
    place(y, fire_whoomp(0.8, 5301, 300, 1800, 0.02) * 0.6, 0.02)
    return master(reverb(y, 0.6, 0.25), 0.95, 2.4)


def hard_stop():
    d = 1.0
    t = t_axis(d)
    squeal = sine(lambda tt: 1700 - 400 * tt / d + 80 * np.sin(2 * np.pi * 9 * tt), d) * 0.3
    grit = stft_filter(noise(d, 5310), lambda tt, f: band(2100, 1.6)(f)) * 0.7
    y = (squeal + grit) * env_adsr(len(t), 0.02, 0.2, 0.7, 0.4)
    y += hiss(d, 5311, 3000, 1500, 0.1) * 0.3
    return master(y, 0.8, 1.4)


def mount():
    d = 0.9
    y = creak(0.5, 5320) * 0.6
    place(y, metal(0.4, 420, 5321, 0.08, 4) * 0.4, 0.15)
    place(y, jangle(0.3, 5322, 6, 0.12, 1700, 0.3), 0.18)
    blip = vtwin(0.5, lambda t: 900 + 2200 * np.exp(-((t - 0.12) / 0.08) ** 2), 5323, 0.1, 1.0, 360)
    place(y, blip * env_adsr(len(blip), 0.01, 0.1, 0.7, 0.2) * 0.7, 0.35)
    return master(y, 0.82, 1.5)


def ram():
    d = 0.9
    y = thud(d, 110, 45, 0.12, 5330) * 1.5 + lowpass(noise(d, 5331, 'brown'), 600) * exp_env(int(d * SR), 0.002, 0.1) * 0.8
    place(y, fire_whoomp(0.6, 5332, 300, 2000, 0.01) * 0.6, 0.01)
    place(y, sizzle(0.5, 5333, 0.2) * 0.3, 0.03)
    return master(reverb(y, 0.4, 0.2), 0.92, 2.2)


# ------------------------------------------------------------------ the Hell Chain

def chain_lash():
    d = 0.7
    y = whoosh(d, 400, 2600, 2.0, seed=5400, attack=0.12, decay=0.12) * 0.8
    y += jangle(d, 5401, 12, 0.18, 2000, 0.5)
    y += whip_crack(d, 5402, 0.17) * 0.4
    place(y, crackle(0.4, 60, 5403, gain=0.25), 0.17)
    return master(y, 0.85, 1.6)


def chain_hit():
    d = 0.7
    y = thud(d, 130, 60, 0.08, 5410) * 1.2 + jangle(d, 5411, 16, 0.12, 1700, 0.6)
    y += sizzle(d, 5412, 0.25) * 0.4
    return master(reverb(y, 0.3, 0.15), 0.88, 1.9)


def chain_throw():
    d = 0.9
    y = jangle(d, 5420, 30, 0.5, 2100, 0.4)
    y += whoosh(d, 600, 2400, 2.2, seed=5421, attack=0.05, decay=0.3) * 0.7
    y += fire_whoomp(d, 5422, 300, 1600, 0.05) * 0.35
    return master(y, 0.82, 1.5)


def chain_whirl():
    d = 1.6
    t = t_axis(d)
    rate = 5.0
    swish = sweep_band(noise(d, 5430, 'pink'), 500, 900, 2.0) * (0.25 + 0.75 * np.abs(np.sin(np.pi * rate * t)) ** 3)
    y = swish * 1.2 + furnace(d, 5431, 900, rate) * 0.4
    for k in range(int(d * rate)):
        place(y, jangle(0.2, 5440 + k, 5, 0.06, 1900, 0.35), k / rate + 0.05)
    y *= env_adsr(len(t), 0.15, 0.2, 0.9, 0.35)
    return master(y, 0.85, 1.5)


def chain_slam():
    d = 1.5
    y = boom(d, 100, 30, 0.35, 5450) * 1.3
    place(y, whip_crack(0.3, 5451), 0.0)
    place(y, jangle(0.8, 5452, 24, 0.35, 1500, 0.5), 0.0)
    place(y, fire_whoomp(1.0, 5453, 200, 1500, 0.03) * 0.8, 0.02)
    return master(reverb(y, 0.7, 0.28), 0.95, 2.3)


# ------------------------------------------------------------------ hellfire

def hellfire_ignite():
    d = 0.9
    y = fire_whoomp(d, 5500, 220, 2200, 0.03) * 1.2 + sizzle(d, 5501, 0.3) * 0.3
    return master(y, 0.85, 1.6)


def hellfire_wave():
    d = 2.2
    y = fire_whoomp(d, 5510, 120, 2600, 0.12) * 1.4
    y += sweep_band(noise(d, 5511, 'brown'), 90, 500, 1.2) * exp_env(int(d * SR), 0.15, 0.7) * 0.8
    y += sub(d, 60, 28, 0.6, 5512) * 0.7
    y += crackle(d, 70, 5513, gain=0.45) * np.linspace(1, 0.2, int(d * SR))
    return master(reverb(y, 0.9, 0.3), 0.93, 1.9)


def hellfire_burn():
    d = 1.2
    y = crackle(d, 55, 5520, gain=0.5) + furnace(d, 5521, 700, 6.0) * 0.4 * env_adsr(int(d * SR), 0.1, 0.2, 0.7, 0.4)
    y += sizzle(d, 5522, 0.4) * 0.3
    return master(y, 0.7, 1.3)


def dash():
    d = 1.0
    y = fire_whoomp(d, 5530, 300, 3000, 0.02) * 1.2 + sub(d, 90, 40, 0.2, 5531) * 0.6
    y += whoosh(d, 1500, 400, 2.0, seed=5532, attack=0.03, decay=0.3) * 0.5
    return master(reverb(y, 0.4, 0.2), 0.9, 1.8)


# ------------------------------------------------------------------ the Rider

def transform():
    d = 2.8
    y = silence(d)
    groan = formant_voice(1.3, lambda t: 110 - 25 * t, 'aou', 5600, growl=0.4) * env_adsr(int(1.3 * SR), 0.15, 0.3, 0.7, 0.5)
    place(y, lowpass(groan, 1600) * 0.45, 0.05)
    place(y, sizzle(1.4, 5601, 0.9) * 0.4, 0.2)
    r = rng(5602)
    for _ in range(9):
        place(y, click(0.06, int(r.integers(1e6)), 700) * r.uniform(0.4, 0.9) + thud(0.06, 260, 140, 0.02, int(r.integers(1e6))) * 0.25,
              r.uniform(0.4, 1.3))
    place(y, fire_whoomp(1.4, 5603, 150, 2600, 0.03) * 1.4, 1.38)
    place(y, boom(1.2, 80, 26, 0.35, 5604) * 0.9, 1.38)
    place(y, hell_growl(1.2, lambda t: 70 - 20 * t, 5605, 0.9, 'aou') * env_adsr(int(1.2 * SR), 0.05, 0.3, 0.6, 0.5) * 0.5, 1.42)
    return master(reverb(y, 0.9, 0.3), 0.94, 1.8)


def revert():
    d = 1.8
    y = furnace(d, 5610, 600, 3.0) * env_adsr(int(d * SR), 0.01, 0.3, 0.4, 1.2) * 0.8
    y += whoosh(d, 1600, 150, 1.4, seed=5611, attack=0.02, decay=0.6, color='pink') * 0.8
    y += hiss(d, 5612, 4000, 2000, 0.3) * 0.25
    place(y, lowpass(noise(0.7, 5613, 'pink'), 900) * env_adsr(int(0.7 * SR), 0.2, 0.1, 0.5, 0.4) * 0.3, 1.0)
    return master(reverb(y, 0.6, 0.25), 0.82, 1.4)


def skull_fire():
    d = 1.3
    y = fire_whoomp(d, 5620, 160, 2400, 0.02) * 1.3 + sub(d, 80, 35, 0.3, 5621) * 0.6
    y += crackle(d, 60, 5622, gain=0.4)
    return master(reverb(y, 0.5, 0.22), 0.92, 1.8)


def stare():
    d = 2.6
    t = t_axis(d)
    y = hell_growl(d, lambda tt: 41 + 2 * np.sin(2 * np.pi * 0.4 * tt), 5630, 0.4, 'uouu') * 0.5
    y += whisper(d, 5631, 'eaoea') * 0.35 + whisper(d, 5632, 'oaeo') * 0.25
    hb = silence(d)
    for i in range(6):
        at = 0.1 + i * 0.42
        place(hb, thud(0.18, 70, 40, 0.06, 5640 + i) * 0.8, at)
        place(hb, thud(0.16, 60, 35, 0.05, 5650 + i) * 0.5, at + 0.16)
    y += hb
    y *= np.clip(t / 1.2, 0.2, 1.0)
    place(y, furnace(0.6, 5660, 900, 6.0) * env_adsr(int(0.6 * SR), 0.1, 0.1, 0.8, 0.3) * 0.5, 1.95)
    return master(reverb(y, 1.0, 0.35), 0.85, 1.4)


def verdict():
    d = 2.0
    y = boom(d, 70, 22, 0.6, 5670, debris=False) * 1.2 + sub(d, 50, 24, 0.9, 5671)
    chord = sum(saw(f, d, 5672 + i) for i, f in enumerate((55.0, 82.4, 110.0, 116.5)))
    y += lowpass(chord, 700) * env_adsr(int(d * SR), 0.05, 0.4, 0.5, 1.0) * 0.35
    place(y, fire_whoomp(1.4, 5673, 150, 2000, 0.03) * 1.0, 0.03)
    return master(reverb(y, 1.2, 0.35), 0.95, 1.8)


def innocent():
    d = 1.4
    y = sum(np.sin(2 * np.pi * f * t_axis(d)) * (0.8 ** i) for i, f in enumerate((220.0, 277.2, 329.6))) * exp_env(int(d * SR), 0.2, 0.5) * 0.4
    y += whoosh(d, 1800, 300, 1.4, seed=5680, attack=0.05, decay=0.5, color='pink') * 0.6
    y += furnace(d, 5681, 500, 2.0) * env_adsr(int(d * SR), 0.01, 0.2, 0.3, 0.9) * 0.3
    return master(reverb(y, 0.9, 0.35), 0.75, 1.2)


def refuse():
    d = 0.45
    y = hiss(d, 5690, 2500, 1200, 0.01) * 0.6 + thud(d, 150, 80, 0.05, 5691) * 0.7 + metal(d, 600, 5692, 0.05, 3) * 0.2
    return master(y, 0.68, 1.3)


RECIPES = {
    'engine_idle': engine_idle, 'engine_low': engine_low, 'engine_high': engine_high, 'skid': skid, 'flame_roar': flame_roar,
    'bike_form': bike_form, 'bike_burnout': bike_burnout, 'ignition': ignition, 'backfire': backfire, 'shift': shift,
    'boost': boost, 'bike_jump': bike_jump, 'bike_land': bike_land, 'bike_crash': bike_crash, 'hard_stop': hard_stop,
    'mount': mount, 'ram': ram, 'chain_lash': chain_lash, 'chain_hit': chain_hit, 'chain_throw': chain_throw,
    'chain_whirl': chain_whirl, 'chain_slam': chain_slam, 'hellfire_ignite': hellfire_ignite, 'hellfire_wave': hellfire_wave,
    'hellfire_burn': hellfire_burn, 'dash': dash, 'transform': transform, 'revert': revert, 'skull_fire': skull_fire,
    'stare': stare, 'verdict': verdict, 'innocent': innocent, 'refuse': refuse,
}
LOOPS = ('engine_idle', 'engine_low', 'engine_high', 'skid', 'flame_roar')


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--wav-dir', type=Path)
    ap.add_argument('--package', action='store_true')
    ap.add_argument('--only')
    a = ap.parse_args(argv)
    names = a.only.split(',') if a.only else list(RECIPES)
    tmp = tempfile.TemporaryDirectory(prefix='ghost-rider-sfx-')
    out = a.wav_dir or Path(tmp.name)
    out.mkdir(parents=True, exist_ok=True)
    red_hulk.OUT = OUT
    for name in names:
        x = RECIPES[name]()
        p = out / f'{name}.wav'
        write_wav(p, x)
        info = f'{name}: {len(x) / SR:.2f}s peak {np.max(np.abs(x)):.2f} rms {np.sqrt(np.mean(x ** 2)):.3f}'
        if name in LOOPS:
            info += f' seam {abs(x[0] - x[-1]):.3f}'
        if a.package:
            info += f' parts {red_hulk.package(p, name)}'
        print(info, flush=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
