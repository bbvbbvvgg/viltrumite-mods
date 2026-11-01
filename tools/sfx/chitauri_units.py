#!/usr/bin/env python3
"""Chitauri city war: original procedural SFX for the new units, pylons and territory cues (own synthesis, CC0-1.0).

No samples, no speech or text-to-speech, no vanilla sounds: every cue is built from the primitives of
tools/sfx/chitauri.py (oscillators, shaped noise, resonant rings, formant masks, convolution room). Each cue is layered
(body + transient + texture + room) and documented below; the cue sheet with triggers lives in
tools/assets/chitauri_sources.md. Events: src/main/sound-overlays/chitauri.json; registry: ChitauriSounds.

Usage: python3 tools/sfx/chitauri_units.py [--check] [--wav DIR] [--only NAME ...]   (same pipeline as chitauri.py)
"""
import argparse
import base64
import hashlib
import os
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chitauri as C  # noqa: E402
from chitauri import (SR, band, bandsweep, crackle, env, filt, finish, formant, glide, moving_formant, noise, osc,  # noqa: E402
                      place, plfo, pnoise, ptone, reverb, ring, rng, spectral, tanh, thump, tt)

OUT = C.OUT


def _clicks(d, n, seed, lo=1200, hi=3000):
    return C._clicks(d, n, seed, lo, hi)


def _growl(d, seed, base, peaks, rate=4.0, rough=0.4, contour=None):
    """Alien throat: two detuned saws with jitter through a formant mask (never speech: no syllables, fixed vowels)."""
    t = tt(d)
    r = rng(seed)
    u = t / d
    c = contour(u) if contour is not None else 1 + 0.12 * np.sin(2 * np.pi * r.uniform(rate * 0.7, rate * 1.3) * t)
    jit = 1 + 0.03 * filt(noise(d, seed + 1), 0, 35) * 6
    f = base * c * jit
    v = osc(f, "saw") + 0.45 * osc(f * 1.007 * 2, "saw") + filt(noise(d, seed + 2), 200, 3000) * rough
    return formant(v, peaks)


def _rubble(d, n, seed, start=0.0, spread=0.4, lo=300, hi=5000, gain=(0.15, 0.6)):
    return C._rubble(d, n, seed, start, spread, lo, hi, gain)


def _periodic_lowpass(x, lo, hi):
    return np.real(np.fft.irfft(np.fft.rfft(x) * band(lo, hi)(np.fft.rfftfreq(len(x), 1 / SR)), len(x)))


# --- captor ------------------------------------------------------------------------------------

def captor_grab():
    """Captor clamps a person: servo snap of the harness arms, a hard alien-metal clank, hydraulic hiss, short grunt."""
    d = 0.9
    t = tt(d)
    servo = osc(glide(t, 900, 2400, 0.5), "saw") * env(t, 0.004, 0.06) * 0.25
    servo = filt(servo, 600, 6000)
    clank = ring(t, [(620, 1.0, 1.0), (1480, 0.6, 0.6), (2930, 0.35, 0.4), (4410, 0.2, 0.25)], 0.09, 301)
    clank *= (t > 0.06)
    hiss = spectral(noise(d, 302), lambda tm, f: band(2500, 9000 - 4000 * np.clip(tm / 0.6, 0, 1))(f)) * env(t - 0.1, 0.03, 0.25) * (t > 0.1)
    grunt = _growl(0.35, 303, 120, [(450, 220, 1.0), (980, 260, 0.7), (2300, 500, 0.3)], rough=0.5) * env(tt(0.35), 0.02, 0.1)
    x = servo + clank * 0.8 + hiss * 0.35
    place(x, grunt * 0.7, 0.14)
    return finish(reverb(tanh(x, 1.6), 0.35, 304, 0.18), 0.001, 0.06)


def captor_tether():
    """Energy tether shot: a rising elastic zap, a whip crack of plasma, the tether's sizzle while it holds."""
    d = 0.8
    t = tt(d)
    zap = osc(glide(t, 300, 2600, 0.35) * (1 + 0.3 * np.sin(2 * np.pi * 63 * t)), "saw") * env(t, 0.004, 0.09)
    zap = bandsweep(zap, 200, 2000, 1500, 9000, 0.7)
    whip = bandsweep(noise(d, 311), 600, 1800, 3000, 12000, 0.4) * env(t - 0.07, 0.003, 0.03) * (t > 0.07)
    sizzle = crackle(d, 140, 312, 2500, 11000) * env(t - 0.1, 0.05, 0.35) * (t > 0.1)
    hum = osc(np.full_like(t, 180) * (1 + 0.04 * np.sin(2 * np.pi * 9 * t)), "saw") * env(t - 0.1, 0.04, 0.3) * (t > 0.1)
    hum = filt(hum, 100, 1800) * 0.3
    x = zap * 0.7 + whip * 0.6 + sizzle * 0.5 + hum
    return finish(reverb(tanh(x, 1.7), 0.4, 313, 0.2), 0.001, 0.08)


def captor_growl():
    """Captor idle: a wet, rattling hunting growl with mandible clicks (lower, slower than the soldier chatter)."""
    d = 1.2
    t = tt(d)
    g = _growl(d, 321, 82, [(380, 200, 1.0), (900, 260, 0.6), (2100, 450, 0.35)], rate=2.5, rough=0.6,
               contour=lambda u: 1 + 0.18 * np.sin(np.pi * u) - 0.1 * u)
    trem = 0.7 + 0.3 * np.sin(2 * np.pi * 23 * t) ** 2
    shape = np.sin(np.pi * np.clip(t / d, 0, 1)) ** 0.6
    x = g * trem * shape + _clicks(d, 10, 322) * 0.5
    return finish(reverb(tanh(x, 1.9), 0.4, 323, 0.16), 0.01, 0.08)


# --- heavy -------------------------------------------------------------------------------------

def heavy_cannon():
    """Heavy's arm cannon: a deep charged discharge (lower and longer than the chariot cannon), body kick, rolling tail."""
    d = 1.2
    t = tt(d)
    fm = 1 + 0.3 * np.sin(2 * np.pi * 38 * t)
    zap = osc(glide(t, 700, 60, 0.35) * fm, "saw") * env(t, 0.002, 0.18)
    zap = bandsweep(zap, 200, 5000, 60, 1000, 0.6)
    sub = thump(t, 90, 28, 0.22, 0.04)
    crack = filt(noise(d, 331), 1800, 12000) * env(t, 0.0004, 0.015)
    blast = filt(noise(d, 332), 100, 3000) * env(t, 0.001, 0.12)
    tail = crackle(d, 50, 333, 1200, 8000) * env(t, 0.06, 0.4)
    x = zap * 0.9 + sub * 1.2 + crack * 0.6 + blast * 0.6 + tail * 0.45
    return finish(reverb(tanh(x, 1.9), 0.8, 334, 0.24), 0.001, 0.1)


def heavy_vent():
    """Cooling vent after a volley: a pressurised steam burst that tails off, metal ticking as it cools."""
    d = 1.3
    t = tt(d)
    steam = spectral(noise(d, 341), lambda tm, f: band(900 + 600 * tm, 9000 - 3000 * tm)(f)) * env(t, 0.01, 0.4)
    pop = thump(t, 160, 70, 0.04, 0.01)
    ticks = np.zeros_like(t)
    r = rng(342)
    for i in range(7):
        c = tt(0.05)
        place(ticks, ring(c, [(r.uniform(2500, 4800), 1.0, 1.0)], 0.012, 343 + i) * r.uniform(0.2, 0.5), 0.35 + i * r.uniform(0.08, 0.14))
    x = steam * 0.8 + pop * 0.5 + ticks
    return finish(reverb(x, 0.4, 344, 0.15), 0.002, 0.15)


def armor_break():
    """Heavy armour cracks open: plates shatter (inharmonic rings), crunch, sparks and a pained bellow underneath."""
    d = 1.4
    t = tt(d)
    plates = np.zeros_like(t)
    r = rng(351)
    for i in range(5):
        c = tt(0.5)
        part = ring(c, [(r.uniform(400, 900), 1.0, 1.0), (r.uniform(1300, 2200), 0.6, 0.6), (r.uniform(2800, 5200), 0.35, 0.35)], 0.12, 352 + i)
        place(plates, part * r.uniform(0.4, 0.9), i * r.uniform(0.02, 0.06))
    crunch = filt(noise(d, 357), 300, 6000) * env(t, 0.001, 0.07)
    sparks = crackle(d, 120, 358, 3000, 12000) * env(t, 0.01, 0.35)
    bellow = _growl(0.8, 359, 70, [(350, 200, 1.0), (800, 260, 0.6), (1900, 450, 0.3)], rough=0.6,
                    contour=lambda u: 1 + 0.4 * np.sin(np.pi * np.clip(u * 1.4, 0, 1))) * env(tt(0.8), 0.04, 0.3)
    x = plates + crunch * 0.6 + sparks * 0.4
    place(x, bellow * 0.6, 0.08)
    return finish(reverb(tanh(x, 1.6), 0.7, 360, 0.22), 0.001, 0.12)


def heavy_slam():
    """Ground slam: a huge sub impact, the pavement cracking, a shock whoosh outwards and rubble settling."""
    d = 1.6
    t = tt(d)
    sub = thump(t, 70, 22, 0.35, 0.05)
    crack = filt(noise(d, 361), 400, 8000) * env(t, 0.0005, 0.04)
    shock = bandsweep(noise(d, 362), 100, 800, 2000, 7000, 0.5) * env(t, 0.005, 0.15)
    rub = _rubble(d, 22, 363, 0.08, 0.6, 300, 5000, (0.1, 0.45))
    x = sub * 1.4 + crack * 0.7 + shock * 0.5 + rub
    return finish(reverb(tanh(x, 2.0), 0.9, 364, 0.25, 3000), 0.001, 0.15)


def heavy_windup():
    """Slam wind-up: servos straining upward, a deep inhaled growl, plates grinding (0.7 s before the hit)."""
    d = 0.75
    t = tt(d)
    servo = osc(glide(t, 180, 900, 1.6), "saw") * env(t, 0.1, 1.0) * np.clip(t / d, 0, 1)
    servo = filt(servo, 150, 5000) * 0.35
    inhale = _growl(d, 371, 64, [(300, 180, 1.0), (700, 240, 0.6), (1700, 400, 0.3)], rough=0.8,
                    contour=lambda u: 1 + 0.5 * u) * np.clip(t / d, 0, 1) ** 1.5
    grind = filt(noise(d, 372), 800, 3500) * (0.5 + 0.5 * np.sin(2 * np.pi * 31 * t)) * 0.2
    x = servo + inhale * 0.8 + grind
    return finish(tanh(x, 1.5), 0.02, 0.02)


def heavy_hurt():
    """Heavy takes a hit: a short, deep pained grunt with a metallic body knock."""
    d = 0.55
    t = tt(d)
    g = _growl(d, 381, 95, [(420, 220, 1.0), (950, 300, 0.7), (2200, 500, 0.35)], rough=0.6,
               contour=lambda u: 1 + 0.35 * np.sin(np.pi * np.clip(u * 1.6, 0, 1)))
    knock = ring(t, [(310, 1.0, 1.0), (760, 0.5, 0.6)], 0.05, 382)
    x = g * env(t, 0.006, 0.17) + knock * 0.5
    return finish(tanh(x, 2.0), 0.002, 0.06)


def heavy_death():
    """Heavy dies: a long falling bellow that breaks into a rattle, then a heavy armoured body crashing down."""
    d = 2.2
    t = tt(d)
    bel = _growl(1.3, 391, 110, [(400, 220, 1.0), (900, 300, 0.6), (2100, 500, 0.3)], rough=0.7,
                 contour=lambda u: np.exp(-u * 1.3) + 0.25) * env(tt(1.3), 0.02, 0.5, 0.3)
    rattle = filt(noise(0.6, 392), 300, 2500) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 26 * tt(0.6)))) * env(tt(0.6), 0.01, 0.2)
    crash = thump(tt(0.8), 80, 25, 0.25, 0.03) + _rubble(0.8, 10, 393, 0.0, 0.3, 300, 4000, (0.15, 0.5))
    clank = ring(tt(0.6), [(520, 1, 1), (1250, 0.6, 0.6), (2600, 0.3, 0.3)], 0.08, 394)
    x = np.zeros_like(t)
    place(x, bel, 0.0)
    place(x, rattle * 0.5, 1.0)
    place(x, crash * 1.1, 1.35)
    place(x, clank * 0.5, 1.37)
    return finish(reverb(tanh(x, 1.7), 0.9, 395, 0.24), 0.005, 0.15)


def heavy_growl():
    """Heavy idle: slow, very deep breathing growl through a rebreather (filtered hiss pulses)."""
    d = 1.6
    t = tt(d)
    g = _growl(d, 401, 56, [(280, 160, 1.0), (650, 220, 0.6), (1500, 380, 0.3)], rate=1.5, rough=0.7,
               contour=lambda u: 1 + 0.15 * np.sin(np.pi * u))
    breath = filt(noise(d, 402), 600, 4000) * (0.5 + 0.5 * np.sin(2 * np.pi * 1.25 * t)) ** 2 * 0.3
    x = (g * 0.9 + breath) * np.sin(np.pi * np.clip(t / d, 0, 1)) ** 0.5
    return finish(reverb(tanh(x, 1.8), 0.5, 403, 0.18), 0.02, 0.1)


def heavy_step():
    """Heavy footstep: a low stomp, the clank of a thick greave plate, a grit crunch."""
    d = 0.32
    t = tt(d)
    stomp = thump(t, 90, 40, 0.06, 0.012)
    plate = ring(t, [(380, 1.0, 1.0), (940, 0.5, 0.6), (2100, 0.25, 0.4)], 0.04, 411)
    grit = filt(noise(d, 412), 1500, 7000) * env(t, 0.001, 0.025)
    x = stomp * 1.2 + plate * 0.35 + grit * 0.3
    return finish(tanh(x, 1.5), 0.0005, 0.04)


def heavy_step_2():
    d = 0.32
    t = tt(d)
    x = thump(t, 82, 36, 0.065, 0.012) * 1.2 + ring(t, [(350, 1.0, 1.0), (880, 0.5, 0.6), (2300, 0.2, 0.4)], 0.04, 413) * 0.35
    x += filt(noise(d, 414), 1500, 7000) * env(t, 0.001, 0.025) * 0.3
    return finish(tanh(x, 1.5), 0.0005, 0.04)


# --- commander ---------------------------------------------------------------------------------

def commander_staff():
    """Commander's staff bolt: a bright descending zap with a harmonic shimmer ring (one of a three-bolt fan)."""
    d = 0.6
    t = tt(d)
    zap = osc(glide(t, 3200, 420, 0.5) * (1 + 0.15 * np.sin(2 * np.pi * 90 * t)), "saw") * env(t, 0.002, 0.08)
    zap = bandsweep(zap, 800, 11000, 300, 4000, 0.6)
    shimmer = ring(t, [(1870, 1.0, 1.0), (2810, 0.7, 0.8), (3740, 0.5, 0.6)], 0.12, 421) * env(t, 0.003, 0.2)
    kick = thump(t, 140, 60, 0.05, 0.015)
    x = zap * 0.8 + shimmer * 0.3 + kick * 0.6
    return finish(reverb(tanh(x, 1.6), 0.5, 422, 0.2), 0.001, 0.06)


def commander_sweep():
    """Staff arc sweep: a heavy whoosh with a sizzling energy arc riding on it and a crack at the end."""
    d = 0.75
    t = tt(d)
    whoosh = bandsweep(noise(d, 431), 200, 800, 1500, 5000, 0.8) * np.sin(np.pi * np.clip(t / 0.5, 0, 1)) ** 1.5
    arc = osc(glide(t, 260, 820, 0.8) * (1 + 0.2 * np.sin(2 * np.pi * 55 * t)), "saw") * np.sin(np.pi * np.clip(t / 0.5, 0, 1))
    arc = filt(arc, 300, 6000) * 0.35
    fizz = crackle(d, 160, 432, 2500, 11000) * np.sin(np.pi * np.clip(t / 0.55, 0, 1))
    crack = filt(noise(d, 433), 2000, 12000) * env(t - 0.45, 0.001, 0.02) * (t > 0.45)
    x = whoosh * 0.7 + arc + fizz * 0.5 + crack * 0.6
    return finish(reverb(tanh(x, 1.5), 0.45, 434, 0.18), 0.005, 0.06)


def commander_order():
    """Commander barks an order: a sharp, rising guttural call with a click roll (an alien signal, not words)."""
    d = 0.7
    t = tt(d)
    g = _growl(d, 441, 150, [(600, 260, 1.0), (1300, 320, 0.8), (2700, 500, 0.4)], rough=0.45,
               contour=lambda u: 1 + 0.6 * np.clip(u * 2.5, 0, 1) - 0.4 * np.clip(u * 2 - 1, 0, 1))
    shape = env(t, 0.015, 0.28, 0.12)
    x = g * shape + _clicks(d, 12, 442, 1800, 4200) * 0.45
    return finish(reverb(tanh(x, 2.0), 0.5, 443, 0.2), 0.003, 0.06)


def commander_signal():
    """Reinforcement signal: a long alien war horn (detuned saw stack, moving formants) over a pulsing energy beacon."""
    d = 2.4
    t = tt(d)
    u = t / d
    f = 98 * (1 + 0.06 * np.clip(u * 4, 0, 1)) * (1 + 0.004 * np.sin(2 * np.pi * 5 * t))
    horn = osc(f, "saw") + osc(f * 1.006, "saw") + 0.6 * osc(f * 1.5, "saw") + 0.4 * osc(f * 2.003, "saw")
    horn = moving_formant(horn, lambda v: [(420 + 300 * v, 200, 1.0), (1100 + 200 * v, 300, 0.6), (2400, 500, 0.3)])
    horn *= env(t, 0.25, 0.6, 1.2)
    pulse = osc(np.full_like(t, 880), "sin") * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 6 * t))) * env(t, 0.05, 1.0, 0.8) * 0.15
    rise = filt(noise(d, 451), 2000, 9000) * np.clip(u * 2, 0, 1) * env(t, 0.4, 0.5, 0.8) * 0.15
    x = horn * 0.8 + pulse + rise
    return finish(reverb(tanh(x, 1.4), 1.6, 452, 0.35, 3500), 0.02, 0.3)


def commander_growl():
    """Commander idle: a measured, low authoritative rumble with a rattling throat and sparse clicks."""
    d = 1.3
    t = tt(d)
    g = _growl(d, 461, 74, [(360, 200, 1.0), (850, 260, 0.6), (2000, 450, 0.35)], rate=2.0, rough=0.45,
               contour=lambda u: 1 + 0.1 * np.sin(2 * np.pi * u))
    rattle = 0.75 + 0.25 * np.sign(np.sin(2 * np.pi * 19 * t))
    x = g * rattle * np.sin(np.pi * np.clip(t / d, 0, 1)) ** 0.6 + _clicks(d, 6, 462) * 0.35
    return finish(reverb(tanh(x, 1.8), 0.5, 463, 0.18), 0.01, 0.08)


# --- interceptor -------------------------------------------------------------------------------

def interceptor_engine():
    """Interceptor jet harness loop (2 s, seamless): a thin high turbine whine, a narrow jet rush, a fast flutter."""
    d = 2.0
    t = tt(d)
    whine = ptone(t, 780, d, 0.5, 0.008, 4.0) + ptone(t, 1560, d, 0.2, 0.008, 4.0) + ptone(t, 3120, d, 0.06, 0.004, 5.0)
    whine *= 0.85 + 0.15 * plfo(t, 9.0, d)
    rush = pnoise(d, 471, 900, 5000) * 0.45 * (0.85 + 0.15 * plfo(t, 3.0, d, 0.5))
    flutter = pnoise(d, 472, 150, 900) * 0.3 * (0.6 + 0.4 * plfo(t, 21.0, d))
    x = np.tanh((whine * 0.6 + rush + flutter) * 1.2)
    return x / (np.max(np.abs(x)) + 1e-9)


def interceptor_boost():
    """Afterburner kick: a whine rocketing up, a blast of jet noise, a low thump of the harness."""
    d = 1.0
    t = tt(d)
    whine = osc(glide(t, 500, 2600, 0.4), "saw") * env(t, 0.02, 0.4)
    whine = filt(whine, 300, 8000) * 0.35
    blast = spectral(noise(d, 481), lambda tm, f: band(300, 3000 + 6000 * np.exp(-tm / 0.3))(f)) * env(t, 0.005, 0.35)
    kick = thump(t, 110, 50, 0.08, 0.02)
    x = whine + blast * 0.8 + kick * 0.6
    return finish(reverb(tanh(x, 1.6), 0.5, 482, 0.18), 0.002, 0.15)


def interceptor_fire():
    """Twin wing blasters: two short, high plasma snaps 55 ms apart with a light crack."""
    d = 0.4
    t = tt(d)
    out = np.zeros_like(t)
    for i, (f0, at) in enumerate([(3600, 0.0), (3300, 0.055)]):
        s = tt(0.2)
        zap = osc(glide(s, f0, 700, 0.4) * (1 + 0.2 * np.sin(2 * np.pi * 120 * s)), "saw") * env(s, 0.001, 0.04)
        zap = bandsweep(zap, 1000, 12000, 500, 5000, 0.5)
        crack = filt(noise(0.2, 491 + i), 3000, 12000) * env(s, 0.0003, 0.008)
        place(out, zap * 0.8 + crack * 0.5, at)
    return finish(reverb(tanh(out, 1.6), 0.3, 493, 0.15), 0.0005, 0.05)


def interceptor_down():
    """Shot down: the engine whine falls and sputters, sparks pop, the harness shrieks as it spins."""
    d = 1.6
    t = tt(d)
    whine = osc(glide(t, 1500, 160, 0.7) * (1 + 0.05 * np.sin(2 * np.pi * 11 * t)), "saw")
    sput = (0.4 + 0.6 * (np.sin(2 * np.pi * (8 + 10 * t / d) * t) > -0.2))
    whine = filt(whine, 200, 6000) * sput * env(t, 0.01, 0.8, 0.3) * 0.5
    pops = crackle(d, 50, 501, 1500, 9000) + filt(noise(d, 502), 100, 2000) * (rng(503).random(len(t)) > 0.9996) * 3
    shriek = filt(osc(glide(t, 2400, 1800, 1.0), "saw"), 1500, 6000) * np.sin(np.pi * np.clip(t / d, 0, 1)) * 0.1
    x = whine + pops * 0.5 + shriek
    return finish(reverb(tanh(x, 1.6), 0.6, 504, 0.2), 0.002, 0.12)


# --- siege -------------------------------------------------------------------------------------

def siege_hum():
    """Siege lancer presence loop (2 s, seamless): a deep generator hum, slow power pulses, a plasma bubbling."""
    d = 2.0
    t = tt(d)
    hum = ptone(t, 55, d, 0.6, 0.0, 0.0, "saw") + ptone(t, 110.5, d, 0.3) + ptone(t, 165, d, 0.15)
    hum = _periodic_lowpass(hum, 30, 700)
    hum *= 0.75 + 0.25 * plfo(t, 2.0, d)
    bubble = pnoise(d, 511, 300, 1500) * (0.5 + 0.5 * plfo(t, 7.0, d)) * 0.25
    sheen = pnoise(d, 512, 3000, 8000) * 0.06
    x = np.tanh((hum + bubble + sheen) * 1.3)
    return x / (np.max(np.abs(x)) + 1e-9)


def siege_charge():
    """Siege charge (2.2 s, the dodge warning): a tone climbing an octave and a half, pulses accelerating, crackle building,
    then a locked, piercing whine for the last half second."""
    d = 2.2
    t = tt(d)
    u = np.clip(t / 1.7, 0, 1)
    f = 140 * 2 ** (1.5 * u ** 1.3)
    rate = 3 + 22 * u ** 2
    pulse = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * np.cumsum(rate) / SR))
    tone = (osc(f, "saw") + 0.5 * osc(f * 2.01, "saw")) * pulse
    tone = filt(tone, 80, 7000) * (0.4 + 0.6 * u)
    lock = (osc(np.full_like(t, 2350), "sin") + 0.3 * osc(np.full_like(t, 4700), "sin")) * np.clip((t - 1.7) / 0.05, 0, 1) * 0.35
    crk = crackle(d, 160, 521, 2000, 11000) * u ** 2
    x = tone * 0.7 + lock + crk * 0.4
    return finish(reverb(tanh(x, 1.5), 0.6, 522, 0.2), 0.01, 0.02)


def siege_fire():
    """Siege shell launch: a massive discharge (sub boom, descending heavy zap, air tear) and a long rolling rumble."""
    d = 2.2
    t = tt(d)
    zap = osc(glide(t, 900, 45, 0.3) * (1 + 0.35 * np.sin(2 * np.pi * 31 * t)), "saw") * env(t, 0.002, 0.25)
    zap = bandsweep(zap, 150, 6000, 40, 800, 0.5)
    sub = thump(t, 65, 20, 0.45, 0.05)
    tear = bandsweep(noise(d, 531), 3000, 12000, 600, 3000, 0.5) * env(t, 0.001, 0.12)
    rumble = filt(noise(d, 532), 30, 400) * env(t, 0.05, 0.9)
    x = zap * 0.9 + sub * 1.4 + tear * 0.6 + rumble * 0.8
    return finish(reverb(tanh(x, 2.0), 1.2, 533, 0.3, 2500), 0.001, 0.25)


def siege_impact():
    """Siege shell lands: a bigger, lower energy explosion with masonry crunching and a long debris rain."""
    d = 2.3
    t = tt(d)
    base = C.explosion(541, 50)
    x = np.zeros_like(t)
    place(x, base, 0.0)
    crunch = filt(noise(d, 542), 200, 5000) * env(t, 0.002, 0.12)
    rub = _rubble(d, 30, 543, 0.15, 1.0, 250, 4500, (0.08, 0.4))
    x = x + crunch * 0.6 + rub
    return finish(reverb(tanh(x, 1.6), 1.0, 544, 0.22, 3000), 0.001, 0.25)


# --- pylons and territory ----------------------------------------------------------------------

def beacon_deploy():
    """Pylon planted: a spike punching into the ground, plates unfolding (ratchets), then an energy power-up chime."""
    d = 1.8
    t = tt(d)
    spike = thump(t, 120, 45, 0.12, 0.02) + filt(noise(d, 551), 400, 6000) * env(t, 0.0005, 0.03)
    ratchet = np.zeros_like(t)
    for i in range(8):
        place(ratchet, ring(tt(0.05), [(1800 + 140 * i, 1.0, 1.0), (3700, 0.4, 0.5)], 0.01, 552 + i) * 0.5, 0.2 + i * 0.07)
    up = osc(glide(t, 200, 1400, 0.8), "saw") * np.clip((t - 0.75) / 0.1, 0, 1) * env(t - 0.75, 0.05, 0.5) * (t > 0.75)
    up = filt(up, 200, 6000) * 0.3
    chime = ring(t, [(988, 1.0, 1.0), (1481, 0.6, 0.9), (1976, 0.4, 0.7)], 0.5, 560) * (t > 1.15) * np.clip((t - 1.15) / 0.01, 0, 1)
    x = spike * 0.9 + ratchet + up + chime * 0.35
    return finish(reverb(tanh(x, 1.5), 0.8, 561, 0.25), 0.001, 0.2)


def beacon_hum():
    """Pylon field loop (2 s, seamless): a soft electric hum with a slow, eerie two-tone pulse."""
    d = 2.0
    t = tt(d)
    hum = ptone(t, 120, d, 0.5) + ptone(t, 240, d, 0.2) + ptone(t, 360.5, d, 0.1)
    two = ptone(t, 660, d, 0.18) * (0.5 + 0.5 * plfo(t, 1.0, d)) + ptone(t, 990, d, 0.12) * (0.5 - 0.5 * plfo(t, 1.0, d))
    fizz = pnoise(d, 571, 3000, 9000) * 0.05 * (0.6 + 0.4 * plfo(t, 5.0, d))
    x = np.tanh((hum + two + fizz) * 1.2)
    return x / (np.max(np.abs(x)) + 1e-9)


def beacon_down():
    """Pylon destroyed: power collapsing (falling whine), a glassy shatter of the field, sparks and a dull thud."""
    d = 1.8
    t = tt(d)
    fall = osc(glide(t, 1300, 70, 0.6), "saw") * env(t, 0.005, 0.6)
    fall = filt(fall, 80, 6000) * 0.45
    shatter = np.zeros_like(t)
    r = rng(581)
    for i in range(16):
        place(shatter, ring(tt(0.3), [(r.uniform(2500, 7000), 1.0, 1.0)], 0.06, 582 + i) * r.uniform(0.1, 0.4), r.uniform(0.0, 0.25))
    sparks = crackle(d, 90, 599, 2000, 11000) * env(t, 0.01, 0.5)
    thud = thump(t, 100, 40, 0.15, 0.02)
    x = fall + shatter + sparks * 0.4 + thud * 0.7
    return finish(reverb(tanh(x, 1.5), 0.8, 600, 0.25), 0.001, 0.2)


def captive_freed():
    """A captive is freed: the holding field pops (reverse whoosh into a soft burst) and a warm rising chime."""
    d = 1.3
    t = tt(d)
    swell = bandsweep(noise(d, 611), 3000, 9000, 300, 2000, 1.0) * np.clip(t / 0.3, 0, 1) ** 2 * (t < 0.3)
    pop = thump(t - 0.3, 220, 110, 0.06, 0.01) * (t > 0.3)
    chime = np.zeros_like(t)
    for i, f in enumerate([523.25, 659.25, 783.99]):
        place(chime, ring(tt(0.9), [(f, 1.0, 1.0), (f * 2, 0.3, 0.6)], 0.4, 612 + i) * env(tt(0.9), 0.005, 0.5), 0.32 + i * 0.07)
    x = swell * 0.5 + pop * 0.6 + chime * 0.5
    return finish(reverb(x, 0.9, 615, 0.3), 0.005, 0.15)


def sector_seized():
    """A sector falls to the hive: an ominous low horn chord (minor second clash) over a war-drum hit, long room."""
    d = 2.6
    t = tt(d)
    chord = np.zeros_like(t)
    for f, a in [(73.4, 1.0), (77.8, 0.7), (110.0, 0.6), (146.8, 0.35)]:
        chord += a * (osc(np.full_like(t, f) * (1 + 0.003 * np.sin(2 * np.pi * 4.5 * t)), "saw"))
    chord = formant(chord, [(380, 220, 1.0), (900, 300, 0.5), (2200, 500, 0.2)]) * env(t, 0.3, 0.8, 1.0)
    drum = thump(t, 85, 32, 0.3, 0.03) + filt(noise(d, 621), 80, 1200) * env(t, 0.001, 0.08)
    x = chord * 0.7 + drum * 1.0
    return finish(reverb(tanh(x, 1.3), 1.8, 622, 0.35, 3000), 0.01, 0.35)


def sector_liberated():
    """A sector is liberated: a bright rising major swell (shimmering partials) resolving into an open fifth."""
    d = 2.4
    t = tt(d)
    u = np.clip(t / 0.9, 0, 1)
    x = np.zeros_like(t)
    for f, a in [(196.0, 1.0), (246.9, 0.7), (293.7, 0.8), (392.0, 0.5), (587.3, 0.3)]:
        g = f * (0.94 + 0.06 * u)
        x += a * (osc(g, "sin") + 0.25 * osc(g * 2.002, "sin"))
    x *= env(t, 0.5, 0.7, 0.9)
    sheen = filt(noise(d, 631), 4000, 10000) * env(t, 0.6, 0.4, 0.3) * 0.08
    x = x * 0.6 + sheen
    return finish(reverb(x, 1.6, 632, 0.35, 5000), 0.02, 0.35)


CUES = {
    "captor_grab": captor_grab, "captor_tether": captor_tether, "captor_growl": captor_growl,
    "heavy_cannon": heavy_cannon, "heavy_vent": heavy_vent, "armor_break": armor_break, "heavy_slam": heavy_slam,
    "heavy_windup": heavy_windup, "heavy_hurt": heavy_hurt, "heavy_death": heavy_death, "heavy_growl": heavy_growl,
    "heavy_step": heavy_step, "heavy_step_2": heavy_step_2,
    "commander_staff": commander_staff, "commander_sweep": commander_sweep, "commander_order": commander_order,
    "commander_signal": commander_signal, "commander_growl": commander_growl,
    "interceptor_engine": interceptor_engine, "interceptor_boost": interceptor_boost, "interceptor_fire": interceptor_fire,
    "interceptor_down": interceptor_down,
    "siege_hum": siege_hum, "siege_charge": siege_charge, "siege_fire": siege_fire, "siege_impact": siege_impact,
    "beacon_deploy": beacon_deploy, "beacon_hum": beacon_hum, "beacon_down": beacon_down,
    "captive_freed": captive_freed, "sector_seized": sector_seized, "sector_liberated": sector_liberated,
}
LOOPS = {"interceptor_engine", "siege_hum", "beacon_hum"}
LOUDNESS = {name: -11.0 for name in CUES}
LOUDNESS.update({
    "heavy_step": -18.0, "heavy_step_2": -18.0, "captor_growl": -14.0, "heavy_growl": -14.0, "commander_growl": -14.0,
    "interceptor_engine": -16.0, "siege_hum": -16.0, "beacon_hum": -18.0, "heavy_hurt": -13.0, "heavy_vent": -14.0,
    "captive_freed": -13.0, "sector_liberated": -12.0, "sector_seized": -11.5, "siege_charge": -12.0,
})
QUALITY = {"siege_fire": 1, "siege_impact": 1, "commander_signal": 1, "sector_seized": 1, "sector_liberated": 1, "heavy_death": 1,
           "siege_charge": 1, "interceptor_engine": 1, "siege_hum": 1, "beacon_hum": 1, "beacon_deploy": 1, "beacon_down": 1}


def build(tmp, wav_dir=None, only=None):
    res = {}
    for name, fn in CUES.items():
        if only and name not in only:
            continue
        loop = name in LOOPS
        x = C.master(fn(), LOUDNESS[name], loop)
        assert np.isfinite(x).all() and np.max(np.abs(x)) > 0.05, name
        if loop:
            assert abs(x[0] - x[-1]) <= np.max(np.abs(np.diff(x))) * 1.02 + 1e-4, name
        w = Path(tmp) / (name + ".wav")
        o = Path(tmp) / (name + ".ogg")
        C.write_wav(w, x)
        if wav_dir:
            os.makedirs(wav_dir, exist_ok=True)
            C.write_wav(Path(wav_dir) / (name + ".wav"), x)
        C.encode(w, o, QUALITY.get(name, 2))
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
                if hashlib.sha256(C.decoded_pcm(committed)).digest() != hashlib.sha256(C.decoded_pcm(ogg)).digest():
                    print("differs", name)
                    bad += 1
            else:
                OUT.mkdir(parents=True, exist_ok=True)
                data = base64.encodebytes(ogg.read_bytes())
                assert len(data) < 60000, (name, len(data))
                dst.write_bytes(data)
        print("%d cues %s" % (len(built), "checked" if a.check else "written"))
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
