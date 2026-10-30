#!/usr/bin/env python3
"""Captain America and the Avengers' sounds: original procedural synthesis (numpy only, no recordings,
no film audio), CC0 (own work). Reuses the primitives of tools/sfx/jinwoo.py.

Usage: python3 tools/sfx/captain.py [--wav-dir DIR] [--package] [--only name,name]
  --package  converts each WAV with .agents/skills/sound-design/scripts/openverse_sfx.py package --own into
             viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/<captain|avengers>/<name>.ogg.b64
Deterministic: every recipe seeds its own RNG. Recipes per file: tools/assets/captain_sources.md.
No recipe imitates speech: the lines of the ultimate are text subtitles only; the Hulk's roar is a
non-verbal formant growl.
"""
import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import jinwoo  # noqa: E402
from jinwoo import (SR, ROOT, PACKAGER, t_axis, rng, noise, env_adsr, exp_env, lowpass, highpass, sweep_band, sine, saw,  # noqa: E402
                    partials, place, silence, master, fade, whoosh, thud, click, drone, formant_voice, write_wav)


# ------------------------------------------------------------------ vibranium and other building blocks

def reverb(x, size=0.35, mix=0.25, seed=7):
    """jinwoo.reverb with a fade on the dry tail first: ringing partials cut at the buffer end would click."""
    return jinwoo.reverb(fade(x, 0.001, min(0.08, len(x) / SR * 0.25)), size, mix, seed)


def ring(dur, base, seed=0, decay=0.35, bright=1.0):
    """The shield's voice: inharmonic metal partials with a slow beat (vibranium sings, it does not clank)."""
    ratios = [1.0, 2.32, 4.25, 6.63, 9.38]
    decays = [decay, decay * 0.7, decay * 0.5, decay * 0.32, decay * 0.2]
    amps = [1.0, 0.7 * bright, 0.45 * bright, 0.28 * bright, 0.16 * bright]
    y = partials([base * r for r in ratios], decays, dur, amps, seed)
    y += 0.5 * partials([base * r * 1.004 for r in ratios[:3]], decays[:3], dur, amps[:3], seed + 1)
    return fade(y, 0.0005, min(0.06, dur * 0.3))


def clang(dur, base, seed=0, decay=0.35, body=150):
    y = click(dur, seed, 2200) * 1.4
    y += thud(dur, body, body * 0.45, 0.05, seed + 1) * 0.9
    y += ring(dur, base, seed + 2, decay) * 0.7
    return y


def boom(dur, f0, f1, decay, seed=0, debris=True):
    y = thud(dur, f0, f1, decay, seed) * 1.6
    y += lowpass(noise(dur, seed + 1, 'brown'), 400) * exp_env(int(dur * SR), 0.004, decay * 1.2)
    y += highpass(noise(dur, seed + 2), 1500) * exp_env(int(dur * SR), 0.001, 0.04) * 0.6
    if debris:
        r = rng(seed + 3)
        for _ in range(9):
            at = r.uniform(0.08, min(dur * 0.7, 0.7))
            place(y, click(0.06, int(r.integers(1e6)), 1800) * r.uniform(0.15, 0.4), at)
    return y


def spin_whoosh(dur, rate, f0, f1, seed=0):
    t = t_axis(dur)
    y = sweep_band(noise(dur, seed, 'pink'), f0, f1, 3.0)
    return y * (0.55 + 0.45 * np.sin(2 * np.pi * rate * t) ** 2)


def zap(dur, seed=0, f=2400):
    r = rng(seed)
    y = silence(dur)
    t = 0.0
    while t < dur - 0.02:
        burst = highpass(noise(0.02, int(r.integers(1e6))), f * r.uniform(0.6, 1.4)) * exp_env(int(0.02 * SR), 0.0005, 0.006)
        place(y, burst, t, r.uniform(0.4, 1.0))
        t += r.uniform(0.006, 0.03)
    hum = saw(lambda tt: 120 + 30 * np.sin(2 * np.pi * 7 * tt), dur, seed) * 0.25
    return (y + lowpass(hum, 1500)) * env_adsr(int(dur * SR), 0.005, 0.1, 0.7, dur * 0.4)


def brass(dur, f0, seed=0):
    """A synthetic horn (saws through an opening 'brassy' band), used for the rally fanfare."""
    src = saw(f0, dur, seed) + 0.6 * saw(f0 * 1.003, dur, seed + 1) + 0.4 * saw(f0 * 0.997, dur, seed + 2)
    n = len(src)
    def g(t, f):
        fc = 600 + 2400 * min(1.0, t / 0.12)
        return 1 / (1 + (f / fc) ** 3)
    from jinwoo import stft_filter
    y = stft_filter(src, g)
    return y * env_adsr(n, 0.04, 0.15, 0.8, min(0.25, dur * 0.4))


def jet(dur, seed=0, whine=2600):
    t = t_axis(dur)
    y = lowpass(noise(dur, seed, 'brown'), 700) * 1.2
    y += sweep_band(noise(dur, seed + 1, 'pink'), 500, 1800, 1.4) * 0.6
    y += sine(lambda tt: whine * (1 + 0.02 * np.sin(2 * np.pi * 0.7 * tt)), dur) * 0.06
    return y


def steps(dur, n, gap, seed=0, f=110):
    y = silence(dur)
    for i in range(n):
        place(y, thud(0.12, f, f * 0.5, 0.03, seed + i) + click(0.12, seed + 50 + i, 2500) * 0.3, 0.02 + i * gap, 0.8)
    return y


# ------------------------------------------------------------------ Captain America

def shield_swing():
    d = 0.38
    y = whoosh(d, 350, 2300, 2.0, seed=100, attack=0.06, decay=0.1, color='pink') * 1.2
    y += ring(d, 410, 101, 0.12, 0.4) * 0.05 * exp_env(int(d * SR), 0.06, 0.1)
    return master(reverb(y, 0.2, 0.12), 0.78)


def shield_hit():
    d = 0.7
    y = clang(d, 520, 110, 0.32, body=140)
    return master(reverb(y, 0.35, 0.22), 0.86, 1.5)


def fist_swing():
    d = 0.2
    return master(whoosh(d, 700, 3200, 2.4, seed=120, attack=0.025, decay=0.05), 0.7)


def fist_hit():
    d = 0.3
    y = thud(d, 160, 60, 0.05, 130) * 1.3 + click(d, 131, 1800) * 0.9
    y += lowpass(noise(d, 132), 2500) * exp_env(int(d * SR), 0.001, 0.02) * 0.6
    return master(reverb(y, 0.2, 0.12), 0.85, 1.8)


def kick():
    d = 0.42
    y = whoosh(d, 400, 2400, 2.2, seed=140, attack=0.05, decay=0.06) * 0.8
    place(y, thud(0.35, 130, 50, 0.07, 141) * 1.5 + click(0.35, 142, 1600), 0.08)
    return master(reverb(y, 0.25, 0.15), 0.86, 1.8)


def finisher():
    d = 1.3
    y = silence(d)
    place(y, whoosh(0.3, 300, 2000, 2.0, seed=150, attack=0.08, decay=0.08), 0.0, 0.8)
    place(y, clang(1.1, 470, 151, 0.55, body=110) * 1.2 + boom(1.1, 90, 35, 0.22, 152, False) * 0.7, 0.12)
    return master(reverb(y, 0.6, 0.3), 0.9, 1.6)


def counter():
    d = 1.0
    y = silence(d)
    place(y, ring(0.3, 1240, 160, 0.08, 1.2) * 0.6 + click(0.3, 161, 4000), 0.0)
    place(y, whoosh(0.2, 600, 3000, 2.4, seed=162, attack=0.02, decay=0.05), 0.06, 0.8)
    place(y, clang(0.8, 540, 163, 0.38, body=120) * 1.1, 0.14)
    return master(reverb(y, 0.4, 0.25), 0.9, 1.6)


def guard_up():
    d = 0.35
    y = lowpass(noise(d, 170, 'pink'), 1800) * exp_env(int(d * SR), 0.02, 0.06) * 0.6
    place(y, click(0.3, 171, 2500) * 0.6 + ring(0.3, 620, 172, 0.1, 0.5) * 0.3, 0.07)
    return master(y, 0.62)


def block():
    d = 0.8
    y = clang(d, 560, 180, 0.42, body=170) * 1.1
    y += ring(d, 280, 181, 0.3, 0.3) * 0.3
    return master(reverb(y, 0.35, 0.2), 0.88, 1.5)


def parry():
    d = 1.0
    y = click(d, 190, 4500) * 1.2 + ring(d, 1480, 191, 0.5, 1.3) * 0.8
    y += ring(d, 740, 192, 0.4, 0.6) * 0.4
    y += highpass(whoosh(d, 6000, 2500, 3, seed=193, attack=0.001, decay=0.05), 1500) * 0.3
    return master(reverb(y, 0.5, 0.3), 0.86, 1.3)


def throw():
    d = 0.55
    y = whoosh(d, 400, 2600, 2.0, seed=200, attack=0.04, decay=0.12) * 0.9
    y += spin_whoosh(d, 11, 1200, 3600, 201) * exp_env(int(d * SR), 0.08, 0.25) * 0.7
    return master(reverb(y, 0.2, 0.12), 0.82)


def shield_fly():
    d = 0.42
    y = spin_whoosh(d, 13, 1500, 1700, 210) * 0.9
    y += ring(d, 880, 211, 1.0, 0.3) * 0.05
    return master(y * env_adsr(len(y), 0.06, 0.1, 0.9, 0.12), 0.6, 1.0)


def ricochet():
    d = 0.9
    y = clang(d, 690, 220, 0.38, body=200) * 1.1
    y += sine(lambda t: 2800 - 1400 * np.exp(-t / 0.2), d) * exp_env(int(d * SR), 0.01, 0.18) * 0.18
    return master(reverb(y, 0.4, 0.25), 0.88, 1.5)


def catch():
    d = 0.5
    y = thud(d, 130, 60, 0.05, 230) * 1.2 + click(d, 231, 2000) * 0.7
    y += ring(d, 520, 232, 0.12, 0.5) * 0.35
    y += lowpass(noise(d, 233, 'pink'), 1500) * exp_env(int(d * SR), 0.005, 0.05) * 0.4
    return master(reverb(y, 0.25, 0.15), 0.84, 1.6)


def recall():
    d = 0.7
    y = spin_whoosh(d, 12, 900, 3200, 240) * env_adsr(int(d * SR), 0.45, 0.1, 0.8, 0.15)
    y += ring(d, 700, 241, 0.6, 0.4) * 0.06 * (t_axis(d) / d)
    return master(y, 0.74)


def rush():
    d = 0.8
    y = steps(d, 4, 0.12, 250) * 0.8
    y += whoosh(d, 250, 1800, 1.6, seed=251, attack=0.25, decay=0.3, color='pink') * 0.9
    return master(reverb(y, 0.25, 0.15), 0.82)


def rush_hit():
    d = 1.0
    y = clang(d, 430, 260, 0.45, body=90) * 1.2 + boom(d, 85, 34, 0.14, 261, False) * 0.9
    return master(reverb(y, 0.45, 0.25), 0.9, 1.8)


def slam_leap():
    d = 0.7
    y = thud(d, 110, 50, 0.05, 270) * 0.9 + click(d, 271, 2200) * 0.4
    y += whoosh(d, 300, 2400, 1.8, seed=272, attack=0.12, decay=0.25) * 1.0
    return master(reverb(y, 0.3, 0.15), 0.8)


def slam_impact():
    d = 1.6
    y = boom(d, 95, 30, 0.35, 280) * 1.2 + ring(d, 400, 281, 0.7, 0.6) * 0.4
    return master(reverb(y, 0.8, 0.35), 0.92, 2.0)


def slam_empowered():
    d = 2.2
    t = t_axis(d)
    y = boom(d, 85, 24, 0.5, 290) * 1.3
    y += ring(d, 330, 291, 1.1, 0.8) * 0.5
    # the vibranium 'wom': a falling resonant sub-sweep after the hit
    y += sine(lambda tt: 160 * np.exp(-tt / 0.6) + 40, d) * exp_env(len(t), 0.02, 0.7) * 0.6
    y += whoosh(d, 3000, 250, 1.4, seed=292, attack=0.03, decay=0.6, color='pink') * 0.6
    return master(reverb(y, 1.0, 0.4), 0.94, 2.0)


def roll():
    d = 0.55
    y = lowpass(noise(d, 300, 'pink'), 2500) * env_adsr(int(d * SR), 0.05, 0.1, 0.6, 0.2) * 0.8
    place(y, thud(0.3, 120, 55, 0.05, 301) * 0.8, 0.08)
    place(y, thud(0.3, 140, 60, 0.04, 302) * 0.6, 0.32)
    return master(y, 0.72)


def landing():
    d = 0.5
    y = thud(d, 120, 45, 0.08, 310) * 1.4 + lowpass(noise(d, 311, 'brown'), 600) * exp_env(int(d * SR), 0.003, 0.08)
    y += click(d, 312, 1500) * 0.5
    return master(reverb(y, 0.25, 0.15), 0.85, 1.8)


def refuse():
    d = 0.4
    y = silence(d)
    for i, at in enumerate((0.0, 0.13)):
        place(y, lowpass(click(0.2, 320 + i, 800) + ring(0.2, 300, 322 + i, 0.05, 0.2) * 0.4, 1800), at, 0.8 - i * 0.2)
    return master(y, 0.6)


def charged():
    d = 0.9
    t = t_axis(d)
    y = sine(lambda tt: 520 + 520 * (tt / d), d) * 0.3 + sine(lambda tt: 780 + 780 * (tt / d), d) * 0.18
    y *= env_adsr(len(t), 0.25, 0.2, 0.7, 0.3)
    y += ring(d, 1040, 330, 0.4, 0.8) * 0.4 * (t > 0.5) * np.exp(-np.maximum(0, t - 0.5) / 0.3)
    return master(reverb(y, 0.5, 0.3), 0.7, 1.1)


def assemble():
    """The rally fanfare under the line (no voice): a rising horn call on a major triad and a drum hit."""
    d = 2.8
    y = silence(d)
    notes = [(0.0, 0.32, 196.0), (0.34, 0.2, 261.6), (0.56, 0.5, 329.6), (1.08, 1.5, 392.0)]
    for at, dur, f in notes:
        place(y, brass(dur, f, int(f)) * 0.5 + brass(dur, f / 2, int(f) + 7) * 0.35, at)
    place(y, brass(1.5, 493.9, 341) * 0.25 + brass(1.5, 587.3, 342) * 0.2, 1.08)
    place(y, boom(1.4, 80, 32, 0.35, 343, False) * 0.9, 1.08)
    place(y, lowpass(noise(2.4, 344, 'pink'), 2000) * env_adsr(int(2.4 * SR), 0.6, 0.4, 0.5, 0.8) * 0.12, 0.2)
    return master(reverb(y, 1.2, 0.35), 0.9, 1.3)


# ------------------------------------------------------------------ the Avengers

def quinjet_flyby():
    d = 3.2
    t = t_axis(d)
    y = jet(d, 400, 2900)
    # doppler-ish: the band rises as it approaches and falls away
    y = sweep_band(y, 400, 1400, 0.8, 1.0) * 0.5 + y * 0.5
    y += sine(lambda tt: 2900 * (1.06 - 0.12 * tt / d), d) * 0.05
    y *= env_adsr(len(t), 1.0, 0.6, 0.85, 1.2)
    return master(reverb(y, 0.6, 0.3), 0.9, 1.3)


def quinjet_ramp():
    d = 2.0
    y = silence(d)
    place(y, highpass(noise(0.8, 410), 3000) * env_adsr(int(0.8 * SR), 0.01, 0.2, 0.5, 0.4) * 0.5, 0.0)
    place(y, saw(lambda tt: 140 + 40 * tt, 1.4, 411) * 0.12 * env_adsr(int(1.4 * SR), 0.1, 0.1, 0.9, 0.2), 0.2)
    place(y, clang(0.6, 260, 412, 0.15, body=90) * 0.9, 1.55)
    return master(reverb(lowpass(y, 6000), 0.6, 0.3), 0.85)


def ironman_thrust():
    d = 0.9
    y = jet(d, 420, 3400) * env_adsr(int(d * SR), 0.03, 0.2, 0.6, 0.4)
    y += highpass(noise(d, 421), 2500) * exp_env(int(d * SR), 0.01, 0.15) * 0.5
    return master(y, 0.82, 1.4)


def ironman_land():
    d = 1.0
    y = boom(d, 110, 40, 0.2, 430) * 1.1 + ring(d, 330, 431, 0.25, 0.4) * 0.25
    place(y, saw(lambda tt: 300 - 120 * tt, 0.4, 432) * 0.06 * exp_env(int(0.4 * SR), 0.05, 0.15), 0.25)
    return master(reverb(y, 0.5, 0.25), 0.9, 1.8)


def ironman_repulsor():
    d = 0.7
    y = silence(d)
    place(y, sine(lambda tt: 900 + 2600 * tt / 0.18, 0.18) * env_adsr(int(0.18 * SR), 0.02, 0.05, 0.8, 0.02) * 0.3, 0.0)
    blast = whoosh(0.5, 2600, 500, 1.6, seed=440, attack=0.002, decay=0.12) + thud(0.5, 200, 80, 0.06, 441) * 0.7
    blast += sine(lambda tt: 1200 * np.exp(-tt / 0.08) + 300, 0.5) * exp_env(int(0.5 * SR), 0.001, 0.1) * 0.3
    place(y, blast, 0.17)
    return master(reverb(y, 0.35, 0.22), 0.86, 1.5)


def ironman_missile():
    d = 1.0
    y = click(d, 450, 2000) * 0.8
    y += highpass(noise(d, 451), 1500) * env_adsr(int(d * SR), 0.02, 0.2, 0.5, 0.5) * 0.6
    y += whoosh(d, 600, 2800, 1.8, seed=452, attack=0.05, decay=0.4) * 0.7
    return master(y, 0.8)


def explosion():
    d = 1.8
    y = boom(d, 75, 26, 0.45, 460) * 1.4
    y += lowpass(noise(d, 461, 'pink'), 1800) * exp_env(int(d * SR), 0.002, 0.35) * 0.6
    return master(reverb(y, 0.9, 0.35), 0.94, 2.2)


def ironman_unibeam():
    d = 2.0
    t = t_axis(d)
    charge = sine(lambda tt: 300 + 1700 * np.clip(tt / 0.5, 0, 1), d) * (t < 0.5) * 0.3
    beam = (saw(220, d, 470) * 0.3 + saw(330.5, d, 471) * 0.2 + highpass(noise(d, 472), 2000) * 0.25)
    beam = lowpass(beam, 4500) * (t >= 0.45) * env_adsr(len(t), 0.5, 0.2, 0.85, 0.35)
    y = charge + beam
    place(y, thud(0.5, 160, 60, 0.08, 473), 0.45, 0.8)
    return master(reverb(y, 0.5, 0.25), 0.88, 1.5)


def ironman_faceplate():
    d = 0.45
    y = saw(lambda tt: 600 + 500 * tt / d, d, 480) * 0.08 * env_adsr(int(d * SR), 0.02, 0.05, 0.9, 0.05)
    y = lowpass(y, 3000)
    place(y, click(0.2, 481, 2500) * 0.9 + ring(0.2, 900, 482, 0.05, 0.5) * 0.3, 0.3)
    return master(y, 0.7)


def hulk_roar():
    d = 2.0
    y = formant_voice(d, lambda t: 62 + 30 * np.sin(np.pi * t / d), 'uaao', 490, growl=1.0)
    y += 0.6 * formant_voice(d, lambda t: 93 + 34 * np.sin(np.pi * t / d), 'oaa', 491, growl=0.9)
    y += lowpass(noise(d, 492, 'brown'), 500) * 0.4
    y *= env_adsr(len(y), 0.15, 0.3, 0.85, 0.5)
    return master(reverb(y, 0.8, 0.3), 0.9, 2.0)


def hulk_smash():
    d = 2.0
    y = boom(d, 70, 22, 0.6, 500) * 1.6
    y += lowpass(noise(d, 501, 'brown'), 250) * env_adsr(int(d * SR), 0.02, 0.4, 0.4, 1.0) * 0.8
    return master(reverb(y, 1.0, 0.35), 0.95, 2.4)


def hulk_punch():
    d = 0.7
    y = thud(d, 110, 35, 0.12, 510) * 1.8 + click(d, 511, 1200) * 0.6
    y += lowpass(noise(d, 512, 'brown'), 600) * exp_env(int(d * SR), 0.002, 0.12)
    return master(reverb(y, 0.4, 0.2), 0.92, 2.2)


def hulk_chest():
    d = 0.9
    y = silence(d)
    for i, at in enumerate((0.0, 0.32)):
        place(y, thud(0.5, 85, 40, 0.1, 520 + i) * 1.4 + lowpass(click(0.5, 530 + i, 400), 900) * 0.6, at, 1.0 - 0.1 * i)
    return master(reverb(y, 0.4, 0.2), 0.88, 2.0)


def thor_thunder():
    d = 3.0
    t = t_axis(d)
    crack = highpass(noise(0.4, 540), 800) * exp_env(int(0.4 * SR), 0.001, 0.06) * 1.4
    y = silence(d)
    place(y, crack, 0.0)
    rumble = lowpass(noise(d, 541, 'brown'), 300) * env_adsr(len(t), 0.05, 0.6, 0.5, 1.6)
    rumble *= 1 + 0.4 * np.sin(2 * np.pi * 3.1 * t) * np.sin(2 * np.pi * 0.7 * t)
    y += rumble * 1.3
    place(y, zap(0.5, 542, 3000) * 0.5, 0.0)
    return master(reverb(y, 1.4, 0.4), 0.95, 2.0)


def thor_hammer_swing():
    d = 0.55
    y = whoosh(d, 200, 1600, 1.6, seed=550, attack=0.08, decay=0.15, color='pink') * 1.2
    y += zap(d, 551, 3500) * 0.25 * exp_env(int(d * SR), 0.05, 0.2)
    return master(reverb(y, 0.3, 0.15), 0.84, 1.4)


def thor_hammer_hit():
    d = 1.2
    y = boom(d, 100, 30, 0.25, 560) * 1.3 + ring(d, 260, 561, 0.3, 0.4) * 0.3
    place(y, zap(0.6, 562, 2800) * 0.6, 0.0)
    return master(reverb(y, 0.7, 0.3), 0.92, 2.0)


def thor_hammer_spin():
    d = 1.0
    y = spin_whoosh(d, 6, 300, 900, 570) * 1.1
    y += zap(d, 571, 3200) * 0.12
    return master(y * env_adsr(int(d * SR), 0.1, 0.1, 0.9, 0.2), 0.8)


def widow_shot():
    d = 0.5
    y = highpass(noise(d, 580), 1200) * exp_env(int(d * SR), 0.0005, 0.02) * 1.6
    y += thud(d, 240, 90, 0.03, 581) * 0.8
    return master(reverb(y, 0.5, 0.35), 0.86, 1.8)


def widow_bite():
    d = 0.6
    return master(zap(d, 590, 2600) * 1.2, 0.8, 1.4)


def widow_strike():
    d = 0.35
    y = whoosh(d, 900, 3800, 2.6, seed=600, attack=0.03, decay=0.05) * 0.8
    place(y, thud(0.25, 180, 70, 0.04, 601) + click(0.25, 602, 2000) * 0.8, 0.07)
    return master(y, 0.82, 1.6)


def acrobat():
    d = 0.6
    y = whoosh(d, 500, 2200, 1.6, seed=610, attack=0.15, decay=0.2, color='pink')
    y += lowpass(noise(d, 611, 'pink'), 3000) * env_adsr(int(d * SR), 0.1, 0.2, 0.4, 0.2) * 0.3
    place(y, thud(0.2, 150, 70, 0.03, 612) * 0.5, 0.48)
    return master(y, 0.7)


def hawkeye_draw():
    d = 0.7
    t = t_axis(d)
    creak = saw(lambda tt: 95 + 40 * tt / d, d, 620) * (0.6 + 0.4 * (rng(621).random(len(t)) > 0.5))
    y = lowpass(creak, 1400) * env_adsr(len(t), 0.1, 0.1, 0.7, 0.1) * 0.25
    y += lowpass(noise(d, 622, 'pink'), 2200) * env_adsr(len(t), 0.05, 0.1, 0.3, 0.1) * 0.3
    return master(y, 0.6)


def hawkeye_release():
    d = 0.7
    t = t_axis(d)
    twang = sine(lambda tt: 110 + 40 * np.exp(-tt / 0.05), d) * np.exp(-t / 0.18) * (1 + 0.3 * np.sin(2 * np.pi * 9 * t))
    y = twang * 0.8 + click(d, 630, 2500) * 0.6
    y += whoosh(d, 2500, 900, 2.5, seed=631, attack=0.01, decay=0.15) * 0.6
    return master(y, 0.8)


def hawkeye_arrow_hit():
    d = 0.4
    y = thud(d, 300, 120, 0.03, 640) + click(d, 641, 2500) * 0.8
    y += partials([180, 410], [0.08, 0.05], d, [0.3, 0.2], 642)
    return master(y, 0.8, 1.6)


def shock():
    d = 0.7
    y = zap(d, 650, 2000) * 1.2 + thud(d, 200, 80, 0.04, 651) * 0.4
    return master(reverb(y, 0.3, 0.2), 0.82, 1.4)


def heavy_land():
    d = 1.1
    y = boom(d, 90, 30, 0.22, 660) * 1.3
    return master(reverb(y, 0.5, 0.25), 0.92, 2.0)


def depart():
    d = 1.4
    y = whoosh(d, 300, 3200, 1.6, seed=670, attack=0.3, decay=0.5, color='pink') * 1.0
    y += jet(d, 671, 3000) * env_adsr(int(d * SR), 0.2, 0.3, 0.5, 0.7) * 0.4
    return master(reverb(y, 0.5, 0.25), 0.84, 1.3)


CAPTAIN = {
    'shield_swing': shield_swing, 'shield_hit': shield_hit, 'fist_swing': fist_swing, 'fist_hit': fist_hit, 'kick': kick,
    'finisher': finisher, 'counter': counter, 'guard_up': guard_up, 'block': block, 'parry': parry, 'throw': throw,
    'shield_fly': shield_fly, 'ricochet': ricochet, 'catch': catch, 'recall': recall, 'rush': rush, 'rush_hit': rush_hit,
    'slam_leap': slam_leap, 'slam_impact': slam_impact, 'slam_empowered': slam_empowered, 'roll': roll, 'landing': landing,
    'refuse': refuse, 'charged': charged, 'assemble': assemble,
}
AVENGERS = {
    'quinjet_flyby': quinjet_flyby, 'quinjet_ramp': quinjet_ramp, 'ironman_thrust': ironman_thrust, 'ironman_land': ironman_land,
    'ironman_repulsor': ironman_repulsor, 'ironman_missile': ironman_missile, 'explosion': explosion, 'ironman_unibeam': ironman_unibeam,
    'ironman_faceplate': ironman_faceplate, 'hulk_roar': hulk_roar, 'hulk_smash': hulk_smash, 'hulk_punch': hulk_punch,
    'hulk_chest': hulk_chest, 'thor_thunder': thor_thunder, 'thor_hammer_swing': thor_hammer_swing, 'thor_hammer_hit': thor_hammer_hit,
    'thor_hammer_spin': thor_hammer_spin, 'widow_shot': widow_shot, 'widow_bite': widow_bite, 'widow_strike': widow_strike,
    'acrobat': acrobat, 'hawkeye_draw': hawkeye_draw, 'hawkeye_release': hawkeye_release, 'hawkeye_arrow_hit': hawkeye_arrow_hit,
    'shock': shock, 'heavy_land': heavy_land, 'depart': depart,
}
RECIPES = {**{f'captain/{k}': v for k, v in CAPTAIN.items()}, **{f'avengers/{k}': v for k, v in AVENGERS.items()}}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--wav-dir', type=Path)
    ap.add_argument('--package', action='store_true')
    ap.add_argument('--only')
    a = ap.parse_args(argv)
    names = a.only.split(',') if a.only else list(RECIPES)
    tmp = tempfile.TemporaryDirectory(prefix='captain-sfx-')
    out = a.wav_dir or Path(tmp.name)
    for name in names:
        x = RECIPES[name]()
        p = out / f'{name}.wav'
        p.parent.mkdir(parents=True, exist_ok=True)
        write_wav(p, x)
        print(f'{name}: {len(x) / SR:.2f}s peak {np.max(np.abs(x)):.2f} rms {np.sqrt(np.mean(x ** 2)):.3f}', flush=True)
        if a.package:
            subprocess.run([sys.executable, str(PACKAGER), 'package', '--input', str(p), '--module', 'core', '--name', name,
                            '--event', name.replace('/', '_'), '--own', '--replace', '--repo', str(ROOT)], check=True,
                           capture_output=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
