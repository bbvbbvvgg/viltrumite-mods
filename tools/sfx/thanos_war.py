#!/usr/bin/env python3
"""THANOS 2.0, the war for Atheria: original procedural SFX (own synthesis, CC0-1.0).

The Sanctuary II, the new stone moves, Ebony Maw, Cull Obsidian and the city sirens. No samples and no
vanilla sound stands in; every cue is loudness-matched by role (LOUDNESS) and registered by the overlay
viltrumitecore/src/main/sound-overlays/thanos_war.json (written by this script, merged into sounds.json
at build). Triggers are documented in tools/assets/thanos_war_sources.md. The stone timbres are the ones
of tools/sfx/thanos.py, so a war move still sounds like its stone:

  ship   sub drones, hull resonances, engine roar, a low brass cluster for the horn, orbital lances
  maw    dry breath formants, cold shimmer of hovering grit, stressed stone groans, airless grips
  cull   ragged formant roars, chain rattle, concrete and brick crunches, thuds you feel
  city   two air-raid sirens carried by the street echo

Usage: python3 tools/sfx/thanos_war.py [--check] [--wav DIR] [--only a,b]
Same pipeline as thanos.py: mono 44.1 kHz 16-bit -> ffmpeg libvorbis (bitexact) -> base64 text assets in
core binassets (NAME.ogg.b64, or NAME.ogg.b64.part0..N past PART bytes). The loops (ship_hum, siren) are
rendered circularly, so their wrap point is seamless. --check re-synthesises in a temporary directory and
compares decoded PCM with the committed files (and the overlay with what the script would write).
"""
import argparse
import base64
import hashlib
import json
import os
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dio as d  # noqa: E402  (shared primitives, meter, encoder)
import thanos as th  # noqa: E402  (the stone timbres)

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/thanos_war"
OVERLAY = ROOT / "viltrumitecore/src/main/sound-overlays/thanos_war.json"
SR = d.SR
PART = th.PART

tt, env, filt, bandsweep, reverb, tanh, finish, ring, thump = d.tt, d.env, d.filt, d.bandsweep, d.reverb, d.tanh, d.finish, d.ring, d.thump
hz, nz, put, osc, stack, swell, gate, flange, speed = th.hz, th.nz, th.put, th.osc, th.stack, th.swell, th.gate, th.flange, th.speed
bursts, vowel, choir, bell, glass, metal, boom, whoosh = th.bursts, th.vowel, th.choir, th.bell, th.glass, th.metal, th.boom, th.whoosh
growl, tinnitus, warp, tick, stone_chord, cfilt, loop_hz, loop_finish = th.growl, th.tinnitus, th.warp, th.tick, th.stone_chord, th.cfilt, th.loop_hz, th.loop_finish


# --- primitives --------------------------------------------------------------------------------

def thunder(dur, seed):
    """A far roll (thunder, a ship's wake, a collapsing street): low noise with a jagged dying amplitude."""
    t = tt(dur)
    r = d.rng(seed)
    a = np.zeros_like(t)
    for _ in range(9):
        at = r.uniform(0, dur * 0.6)
        a += r.uniform(0.3, 1.0) * np.exp(-np.maximum(t - at, 0) / r.uniform(0.15, 0.6)) * (t >= at)
    return filt(nz(len(t), seed + 1), 30, 400) * a * env(t, 0.05, dur * 0.4)


def crunch(dur, seed, count=60, lo=300, hi=5000):
    """Stone, brick and concrete breaking: many short grains, dense at the start."""
    return bursts(int(dur * SR), count, seed, 0.0, 0.7, lo, hi, length=(0.003, 0.03), shape="down", amp=(0.2, 1.0))


def roar(t, f, seed, v="ah", drive=2.2, rasp=0.5):
    """A beast's throat: a distorted detuned pulse through vowel formants, plus breath rasp."""
    src = tanh(stack(t, f, 3, 0.015, seed, 0.25, 6.0), drive)
    return vowel(src, v, 0.02) + vowel(filt(nz(len(t), seed + 7), 200, 4000), v, 0.02) * rasp


def chain(dur, seed, count=26, f=2600):
    """Chain links rattling: small metal clinks at random."""
    out = np.zeros(int(dur * SR))
    r = d.rng(seed)
    for i in range(count):
        at = r.uniform(0, dur * 0.85)
        put(out, metal(tt(0.12), min(3000.0, f * r.uniform(0.7, 1.4)), 0.03, seed + 1 + i) * r.uniform(0.3, 1.0), at)
    return out


def footfall(seed, f0=70):
    """One heavy footfall: a low thud and a crunch of grit."""
    z = tt(0.5)
    x = thump(z, f0 * 1.6, f0 * 0.5, 0.12, 0.02) + filt(nz(len(z), seed), 50, 400) * env(z, 0.002, 0.06) * 0.6
    x += bursts(len(z), 18, seed + 1, 0.0, 0.12, 1500, 7000, length=(0.001, 0.005), amp=(0.1, 0.5)) * 0.5
    return tanh(x, 1.4)


# --- the Sanctuary II -----------------------------------------------------------------------------

def ship_arrival():
    """The ship comes out of the clouds: far thunder, the air tearing, a sub that grows with it, the hull
    groaning, the engines passing over, then a low brass chord as it settles into the hover."""
    dur = 9.0
    t = tt(dur)
    rise = np.clip(t / 6.5, 0, 1)
    x = osc(t, 22 + 14 * rise, seed=5000) * (0.2 + 0.8 * rise ** 1.5) * 0.7
    x += osc(t, 33 + 21 * rise, seed=5001) * rise ** 2 * 0.3
    x += filt(nz(len(t), 5002), 25, 220) * (0.15 + 0.85 * swell(t, 6.0, 5.5, 2.0)) * 0.9
    x += bandsweep(nz(len(t), 5003), 150, 600, 500, 3500, curve=0.8) * swell(t, 5.5, 4.5, 1.5) * 0.35
    for i, (a, g) in enumerate(((0.3, 0.5), (1.8, 0.45), (3.2, 0.4))):
        put(x, thunder(3.0, 5010 + i), a, g)
    for i, (a, f) in enumerate(((2.8, 47), (4.6, 41), (6.2, 52))):
        put(x, metal(tt(2.2), f, 0.6, 5020 + i), a, 0.18)
    x += growl(t, 46 + 6 * np.sin(2 * np.pi * 0.3 * t), 5030, 2.0, 30, 600) * swell(t, 6.2, 3.0, 2.2) * 0.35
    z = tt(2.6)
    brass = filt(stack(z, hz(29), 5, 0.012, 5040) + stack(z, hz(36), 5, 0.012, 5046) * 0.7, 30, 900)
    put(x, brass * env(z, 0.25, 1.0), 6.4, 0.45)
    return finish(reverb(x, 3.0, 5049, 0.38, 2500))


def ship_hum():
    """Hover drone (seamless 8 s loop): three sub partials, two of them beating, engine rumble and a far
    hull resonance breathing in and out."""
    length = 8.0
    t = tt(length)
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * loop_hz(0.25, length) * t)
    x = osc(t, loop_hz(31.0, length), seed=5100) * 0.6
    x += osc(t, loop_hz(46.5, length), seed=5101) * 0.3 * (0.6 + 0.4 * lfo)
    x += osc(t, loop_hz(62.0, length), seed=5102) * 0.15 + osc(t, loop_hz(62.625, length), seed=5103) * 0.15
    x += cfilt(nz(len(t), 5104), 30, 180) * 0.35 * (0.8 + 0.2 * lfo)
    breath = (0.5 + 0.5 * np.sin(2 * np.pi * loop_hz(0.125, length) * t + 1.3)) ** 2
    x += cfilt(nz(len(t), 5105), 700, 1100, 3.0) * breath * 0.12
    x += cfilt(osc(t, loop_hz(93.0, length), "saw", seed=5106), 60, 500) * (0.6 + 0.4 * lfo) * 0.08
    return loop_finish(reverb(x, 2.0, 5109, 0.3, 2000, wrap=True))


def ship_horn():
    """The Sanctuary's horn over the city: a low dissonant brass cluster swelling, a growl inside it."""
    dur = 4.5
    t = tt(dur)
    e = env(t, 0.6, 1.6, 1.2)
    x = np.zeros_like(t)
    for i, (m, g) in enumerate(((29, 1.0), (30, 0.6), (36, 0.7), (41, 0.45))):
        x += stack(t, hz(m), 6, 0.01, 5200 + 10 * i, 0.1, 3.0) * g
    x = vowel(tanh(x * 0.8, 1.8), "oh", 0.05) * e
    x += filt(nz(len(t), 5250), 40, 300) * e * 0.3
    return finish(reverb(x, 3.0, 5259, 0.4, 2500))


def ship_charge():
    """A lance charging in orbit over the marked ground (the 2 s warning): a whine climbing, arcs crackling."""
    dur = 2.1
    t = tt(dur)
    u = np.clip(t / 1.95, 0, 1)
    g = gate(t, 0, 1.97, 0.05, 0.04)
    x = (osc(t, 180 * 2 ** (u * 2.6), "saw", seed=5300) * 0.25 + osc(t, 181.5 * 2 ** (u * 2.6), "saw", seed=5301) * 0.2) * u ** 1.5 * g
    x = filt(x, 100, 6000)
    x += bursts(len(t), 90, 5302, 0.2, 1.95, 2000, 10000, shape="up", amp=(0.2, 0.9)) * g * 0.4
    x += filt(nz(len(t), 5303), 40, 200) * u ** 2 * g * 0.4
    return finish(reverb(x, 1.2, 5309, 0.25))


def ship_lance():
    """The orbital lance lands: the sky cracks, a white column, the ground blows out and rumbles."""
    dur = 3.0
    x = np.zeros(int(dur * SR))
    z = tt(0.5)
    put(x, osc(z, 3000 * 2 ** (-z / 0.08), seed=5400) * env(z, 0.001, 0.12), 0.0, 0.35)
    put(x, filt(nz(int(0.3 * SR), 5401), 2500, 12000) * env(tt(0.3), 0.001, 0.05), 0.0, 0.6)
    put(x, boom(2.4, 90, 20, 0.8, 5402, 1.3, 1.0), 0.04, 1.0)
    put(x, thunder(2.8, 5403), 0.1, 0.8)
    put(x, bursts(int(2.0 * SR), 120, 5404, 0.0, 1.0, 400, 6000, length=(0.003, 0.03), shape="down"), 0.1, 0.35)
    put(x, filt(nz(int(0.9 * SR), 5405), 600, 4000) * env(tt(0.9), 0.01, 0.35), 0.0, 0.3)
    return finish(reverb(x, 2.5, 5409, 0.35, 3000))


def ship_beam():
    """A tractor beam from the hull to the street: a column of hum and fizz sliding down, then letting go."""
    dur = 3.6
    t = tt(dur)
    e = env(t, 0.25, 0.9, 2.0)
    f = 90 * 2 ** (-0.5 * np.clip(t / 0.6, 0, 1))
    x = filt(stack(t, f, 4, 0.02, 5500) * 0.4 + osc(t, f * 2.01, seed=5505) * 0.2, 40, 2500) * e
    x += flange(filt(nz(len(t), 5506), 1500, 8000), 2.0, 3.0, 0.8, 0.8) * e * 0.2
    x += bursts(len(t), 60, 5507, 0.1, 3.0, 3000, 11000, amp=(0.1, 0.6)) * 0.25
    put(x, whoosh(0.7, 300, 3000, 5508, 0.3, True), 0.0, 0.4)
    return finish(reverb(x, 1.8, 5509, 0.32))


def ship_depart():
    """The ship leaves after the victory: the engines spool up, it climbs, the roar sinks away."""
    dur = 8.0
    t = tt(dur)
    spool = np.clip(t / 2.5, 0, 1)
    away = np.clip((t - 2.5) / 5.5, 0, 1)
    f = 40 * (1 + 0.6 * spool) * 2 ** (-0.8 * away)
    e = (0.3 + 0.7 * spool ** 1.5) * (1 - away) ** 1.5
    x = growl(t, f, 5600, 2.2, 30, 700) * e * 0.5 + osc(t, f * 0.5, seed=5601) * e * 0.6
    x += bandsweep(nz(len(t), 5602), 80, 900, 40, 300, curve=0.7) * e * 0.5
    x += bandsweep(nz(len(t), 5603), 600, 4000, 200, 1200, curve=0.6) * swell(t, 3.0, 2.5, 1.5) * 0.25
    put(x, thunder(4.0, 5604), 2.4, 0.5)
    return finish(reverb(x, 3.0, 5609, 0.38, 2500))


def portal_open():
    """A landing portal tears open for the army: swirling air, crackle, a deep whomp, then its hum."""
    dur = 3.0
    t = tt(dur)
    x = flange(bandsweep(nz(len(t), 5700), 200, 900, 800, 5000, curve=0.5), 1.0, 6.0, 2.5, 0.8) * swell(t, 0.8, 0.8, 1.2) * 0.5
    x += bursts(len(t), 70, 5701, 0.2, 2.0, 2500, 10000, amp=(0.2, 0.8)) * 0.35
    put(x, boom(1.4, 140, 35, 0.4, 5702, 0.8, 0.5), 0.8, 0.8)
    hum = osc(t, 72, "saw", seed=5703) * 0.3 + osc(t, 108.4, "saw", seed=5704) * 0.2
    x += filt(hum, 40, 900) * np.clip((t - 0.8) / 0.2, 0, 1) * np.exp(-np.maximum(t - 0.8, 0) / 1.2)
    return finish(reverb(x, 2.0, 5709, 0.33))


def siren():
    """City air-raid siren (seamless 6 s loop): a near siren rising and falling once per loop and a far
    one out of step, both carried by the street echo. Each mean pitch makes whole cycles per loop."""
    length = 6.0
    t = tt(length)

    def one(mean, depth, phase):
        ph = 2 * np.pi * np.cumsum(mean - depth * np.cos(2 * np.pi * t / length + phase)) / SR
        return np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph) + 0.12 * np.sin(4 * ph)
    near = one(560.0, 230.0, 0.0) * (0.75 + 0.25 * (0.5 - 0.5 * np.cos(2 * np.pi * t / length)))
    x = cfilt(near, 200, 4500) + cfilt(one(605.0, 200.0, 2.2), 300, 2200) * 0.45
    x += cfilt(nz(len(t), 5802), 60, 400) * 0.03
    return loop_finish(reverb(x, 2.5, 5809, 0.45, 3000, wrap=True))


# --- Thanos: the war moves ------------------------------------------------------------------------

def command():
    """He raises the gauntlet to the ship: the stones flare, a brass hit, a signal climbing to the sky."""
    dur = 2.6
    x = np.zeros(int(dur * SR))
    put(x, stone_chord(tt(1.8), 0.8, 5900, 57), 0.0, 0.35)
    z = tt(1.8)
    brass = vowel(tanh(stack(z, hz(38), 6, 0.01, 5910) + stack(z, hz(45), 6, 0.01, 5916) * 0.7, 1.6), "ah", 0.04) * env(z, 0.03, 0.7)
    put(x, brass, 0.05, 0.6)
    put(x, boom(1.2, 110, 30, 0.35, 5920, 0.8, 0.6), 0.05, 0.6)
    put(x, whoosh(1.6, 200, 5000, 5921, 0.6, False), 0.2, 0.4)
    z2 = tt(1.4)
    put(x, osc(z2, 400 * 2 ** (z2 * 2.2), seed=5922) * env(z2, 0.05, 0.5), 0.3, 0.12)
    return finish(reverb(x, 2.4, 5929, 0.38))


def absorb():
    """The gauntlet drinks an attack: the energy is sucked in (reversed), the power stone growls it away."""
    dur = 2.2
    x = np.zeros(int(dur * SR))
    n = int(0.9 * SR)
    src = filt(nz(n, 6000), 800, 9000) * env(tt(0.9), 0.002, 0.3) + bursts(n, 50, 6001, 0, 0.6, 2000, 10000)
    put(x, src[::-1] * np.linspace(0, 1, n) ** 1.5, 0.0, 0.6)
    put(x, glass(tt(1.3), hz(76), 0.6, 6002), 0.9, 0.25)
    z = tt(1.3)
    put(x, growl(z, 55 * 2 ** (-z * 0.5), 6003, 2.4) * env(z, 0.01, 0.5), 0.9, 0.5)
    put(x, boom(0.9, 160, 45, 0.15, 6004, 0.5, 0.4), 0.9, 0.5)
    return finish(reverb(x, 1.6, 6009, 0.33))


def rupture():
    """Power rupture: the street splits in a line of purple eruptions running outwards, rock roaring up."""
    dur = 2.8
    t = tt(dur)
    x = filt(nz(len(t), 6100), 30, 200) * env(t, 0.05, 1.2) * 0.6
    for i in range(5):
        a = 0.16 * i
        put(x, boom(1.2, 120 - 12 * i, 28, 0.35, 6101 + i, 1.0, 0.9), a, 0.75 + 0.05 * i)
        put(x, bursts(int(0.6 * SR), 40, 6110 + i, 0, 0.5, 1500, 9000, shape="down"), a, 0.3)
    x += growl(t, 50 * 2 ** (-t * 0.4), 6120, 3.0) * env(t, 0.02, 0.9) * 0.4
    put(x, crunch(1.8, 6121), 0.2, 0.5)
    return finish(reverb(x, 2.0, 6129, 0.34))


def barrage():
    """Space barrage: gates chime open around the hero and fire one after another."""
    dur = 2.4
    t = tt(dur)
    x = np.zeros_like(t)
    for i in range(6):
        a = 0.12 * i
        put(x, glass(tt(0.8), hz(83 + (0, 3, 5, 7, 10, 12)[i]), 0.35, 6200 + i), a, 0.25)
        put(x, flange(whoosh(0.5, 400, 4000, 6210 + i, 0.3, True), 1.0, 3.0, 3.0, 0.7), a + 0.05, 0.4)
        put(x, boom(0.6, 200, 60, 0.1, 6220 + i, 0.5, 0.8), a + 0.08, 0.5)
    x += flange(filt(nz(len(t), 6230), 1500, 8000), 1.5, 4.0, 1.6, 0.8) * env(t, 0.05, 0.6) * 0.15
    return finish(reverb(x, 1.6, 6239, 0.32))


def rewrite():
    """Reality rewrite: the ground under the hero turns red and liquid, the air wobbles and folds back."""
    dur = 2.8
    t = tt(dur)
    s = swell(t, 1.4, 1.3, 0.9)
    x = warp(t, 87 * 2 ** (np.sin(2 * np.pi * 0.7 * t) * 0.4), 6300, 4.0, 1.2) * s * 0.45
    n = int(1.2 * SR)
    put(x, (filt(nz(n, 6301), 300, 5000) * env(tt(1.2), 0.002, 0.4))[::-1], 0.2, 0.4)
    x += speed(filt(nz(len(t), 6302), 200, 2000), 1.0 + 0.3 * np.sin(2 * np.pi * 1.3 * t)) * s * 0.25
    put(x, boom(1.0, 150, 40, 0.3, 6303, 0.6, 0.4), 1.4, 0.55)
    return finish(reverb(x, 1.8, 6309, 0.36))


def anomaly():
    """Time anomaly: a bubble where the clock stutters, slows and races; a bell smears, then runs backwards."""
    dur = 2.6
    t = tt(dur)
    x = np.zeros_like(t)
    z = tt(2.4)
    ticks = np.zeros_like(z)
    for k in range(16):
        put(ticks, tick(0.12, 1300 + 40 * k, 6400 + k), 0.15 * k, 0.5)
    put(x, speed(ticks, 1.0 + 0.55 * np.sin(2 * np.pi * 0.6 * z)), 0.0, 0.8)
    b = bell(t, hz(52), 1.1, 6420)
    x += speed(b, 1.0 - 0.4 * np.clip(t / 1.2, 0, 1)) * 0.35
    n = int(0.8 * SR)
    put(x, b[:n][::-1] * np.linspace(0, 1, n) ** 2, 1.4, 0.3)
    x += filt(nz(len(t), 6421), 100, 800) * env(t, 0.3, 1.0) * 0.2
    return finish(reverb(x, 1.8, 6429, 0.36))


def compulsion():
    """Mind compulsion: a yellow whine bores in, a throb takes over the pulse, whispers circle."""
    dur = 2.6
    t = tt(dur)
    s = 0.15 + 0.85 * swell(t, 1.0, 1.0, 1.2)
    x = tinnitus(t, 1500 * 2 ** (np.clip(t / 1.0, 0, 1) * 0.3), 6500, 5.0) * s * 0.18
    x += osc(t, 70, seed=6501) * (0.5 + 0.5 * np.cos(2 * np.pi * 2.5 * t)) ** 6 * s * 0.6
    wh = vowel(filt(nz(len(t), 6502), 800, 6000), "ah", 0.02) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.9 * t)) * s
    x += flange(wh, 2.0, 6.0, 0.5, 0.8) * 0.35
    put(x, boom(0.5, 400, 120, 0.06, 6503, 0.3, 0.6), 1.0, 0.3)
    return finish(reverb(x, 1.6, 6509, 0.36))


def harvest():
    """Soul harvest: ghosts are drawn out and up into the gauntlet; a choir glides down, breath pulls in."""
    dur = 3.0
    t = tt(dur)
    s = swell(t, 1.6, 1.5, 1.0)
    x = speed(choir(t, (57, 64, 69), "oo", 6600, 0.3, 0.1), 1.0 - 0.25 * np.clip(t / 2.5, 0, 1)) * s * 0.55
    n = int(1.6 * SR)
    put(x, vowel(filt(nz(n, 6610), 400, 5000) * np.linspace(0, 1, n) ** 2, "oh", 0.05), 0.0, 0.5)
    x += osc(t, 55 * 2 ** (-np.clip(t / 2, 0, 1) * 0.5), seed=6611) * s * 0.4
    put(x, bell(tt(1.4), hz(69), 0.7, 6612, 0.6), 1.6, 0.25)
    return finish(reverb(x, 2.2, 6619, 0.4))


def time_stop():
    """Time stops: everything slides down to a halt (tape stop), a crystal note hangs in the silence."""
    dur = 2.0
    x = np.zeros(int(dur * SR))
    n = int(0.7 * SR)
    src = filt(nz(n, 6700), 100, 3000) * 0.4 + stack(tt(0.7), hz(57), 4, 0.01, 6701) * 0.4
    put(x, speed(src, np.linspace(1.0, 0.05, n) ** 1.3) * np.linspace(1, 0.2, n), 0.0, 0.8)
    for i, m in enumerate((88, 93, 95)):
        put(x, glass(tt(1.4), hz(m), 1.0, 6710 + i), 0.55, 0.2)
    put(x, tick(0.2, 1800, 6720, 0.9), 0.55, 0.6)
    return finish(reverb(x, 2.0, 6729, 0.4))


def volley():
    """Time resumes into the volley: a reversed rush, then six charged bolts fire almost at once."""
    dur = 2.0
    x = np.zeros(int(dur * SR))
    n = int(0.35 * SR)
    put(x, filt(nz(n, 6800), 500, 8000)[::-1] * np.linspace(0, 1, n) ** 2, 0.0, 0.5)
    for i in range(6):
        a = 0.35 + 0.045 * i
        put(x, whoosh(0.6, 300, 5000, 6810 + i, 0.15, True), a, 0.4)
        put(x, boom(0.9, 180 - 12 * i, 40, 0.18, 6820 + i, 0.8, 1.0), a, 0.45)
    put(x, bursts(int(1.2 * SR), 80, 6830, 0, 1.0, 2000, 10000, shape="down"), 0.35, 0.35)
    return finish(reverb(x, 1.6, 6839, 0.33))


def rock_impact():
    """A moon shard lands: the last of its fall, a crater blast, rock shattering, gravel raining."""
    dur = 2.6
    x = np.zeros(int(dur * SR))
    put(x, whoosh(0.35, 150, 1800, 6900, 0.9, True), 0.0, 0.5)
    put(x, boom(2.2, 75, 18, 0.7, 6901, 1.4, 1.2), 0.3, 1.0)
    put(x, crunch(1.6, 6902), 0.3, 0.7)
    put(x, thunder(2.2, 6903), 0.33, 0.5)
    put(x, bursts(int(2.0 * SR), 140, 6904, 0.1, 1.0, 1500, 8000, length=(0.001, 0.006), amp=(0.1, 0.6)), 0.5, 0.3)
    return finish(reverb(x, 2.0, 6909, 0.33))


def infinity_stage():
    """One stage of the Infinity combo (pitched up per stage): a stone flares in a chord stab and goes off."""
    dur = 1.8
    x = np.zeros(int(dur * SR))
    put(x, stone_chord(tt(1.5), 0.6, 7000, 62), 0.0, 0.35)
    put(x, boom(1.4, 120, 30, 0.4, 7010, 1.0, 1.1), 0.02, 0.8)
    put(x, filt(nz(int(0.6 * SR), 7011), 2000, 11000) * env(tt(0.6), 0.001, 0.12), 0.02, 0.4)
    z = tt(1.0)
    put(x, growl(z, 60 * 2 ** (-z), 7012, 2.5) * env(z, 0.005, 0.3), 0.02, 0.35)
    return finish(reverb(x, 1.8, 7019, 0.34))


# --- Ebony Maw -------------------------------------------------------------------------------------

def maw_voice():
    """Ebony Maw's presence: a dry breath through a thin vowel, a cold shimmer of hovering grit."""
    dur = 2.6
    t = tt(dur)
    e = env(t, 0.35, 0.9, 0.9)
    x = flange(vowel(filt(nz(len(t), 7100), 600, 8000), "uh", 0.02), 3.0, 5.0, 0.4, 0.7) * e * 0.5
    for i, m in enumerate((91, 96, 98, 103)):
        x += osc(t, hz(m) * (1 + 0.004 * np.sin(2 * np.pi * (0.3 + 0.1 * i) * t)), seed=7101 + i) * e * 0.03
    x += bursts(len(t), 90, 7110, 0.0, 2.4, 3000, 11000, length=(0.001, 0.004), amp=(0.05, 0.4)) * 0.3
    x += osc(t, 49, seed=7111) * e * 0.3
    return finish(reverb(x, 2.0, 7119, 0.4))


def maw_lift():
    """Telekinetic lift: stone and steel groan as they tear loose, crack and rise humming into the air."""
    dur = 1.9
    t = tt(dur)
    x = np.zeros_like(t)
    n = int(0.9 * SR)
    put(x, speed(filt(nz(n, 7200), 60, 700), np.linspace(0.6, 1.1, n)) * env(tt(0.9), 0.2, 0.4), 0.0, 0.5)
    for i, a in enumerate((0.25, 0.42, 0.55)):
        put(x, filt(nz(int(0.08 * SR), 7210 + i), 800, 7000) * env(tt(0.08), 0.0005, 0.015), a, 0.6)
    put(x, crunch(0.8, 7220, 40), 0.3, 0.35)
    s = swell(t, 1.0, 0.7, 0.8)
    x += flange(filt(nz(len(t), 7230), 1200, 6000), 2.0, 4.0, 0.9, 0.8) * s * 0.25
    x += osc(t, 140 * 2 ** (np.clip((t - 0.4) / 1.0, 0, 1) * 0.6), seed=7231) * s * 0.2
    return finish(reverb(x, 1.5, 7239, 0.33))


def maw_throw():
    """He flicks the debris at a hero: a pressure thump and a heavy whoosh."""
    dur = 1.1
    x = np.zeros(int(dur * SR))
    put(x, boom(0.5, 160, 50, 0.08, 7300, 0.4, 0.3), 0.0, 0.5)
    put(x, whoosh(0.9, 150, 2200, 7301, 0.3, True), 0.02, 0.8)
    put(x, flange(filt(nz(int(0.6 * SR), 7302), 2000, 9000) * env(tt(0.6), 0.01, 0.15), 1.0, 3.0, 4.0), 0.0, 0.2)
    return finish(reverb(x, 1.2, 7309, 0.28))


def maw_grab():
    """A telekinetic grip closes on a hero: an airless squeeze, a beating tone tightening, joints creaking."""
    dur = 1.6
    t = tt(dur)
    u = np.clip(t / 0.8, 0, 1)
    e = env(t, 0.05, 0.6, 0.6)
    x = tinnitus(t, 600 * 2 ** (u * 0.5), 7400, 4 + 10 * u) * e * 0.18 + filt(nz(len(t), 7401), 100, 600) * e * 0.3
    for i in range(5):
        put(x, filt(nz(int(0.06 * SR), 7410 + i), 300, 3000) * env(tt(0.06), 0.002, 0.02), 0.2 + 0.18 * i, 0.3)
    put(x, boom(0.4, 220, 80, 0.06, 7420, 0.3, 0.4), 0.0, 0.4)
    return finish(reverb(x, 1.2, 7429, 0.3))


def maw_barrier():
    """A shield of hovering debris snaps into place: stones grind together, a cold field hums."""
    dur = 1.8
    t = tt(dur)
    x = np.zeros_like(t)
    put(x, whoosh(0.6, 200, 3000, 7500, 0.6, False), 0.0, 0.4)
    put(x, crunch(0.9, 7501, 50, 200, 3000), 0.35, 0.5)
    put(x, boom(0.7, 180, 60, 0.12, 7502, 0.5, 0.5), 0.45, 0.5)
    field = osc(t, 220, "tri", seed=7503) * 0.4 + osc(t, 331, seed=7504) * 0.2
    x += field * gate(t, 0.45, 1.6, 0.05, 0.3) * 0.3 * (0.8 + 0.2 * np.sin(2 * np.pi * 7 * t))
    for i, m in enumerate((86, 91)):
        put(x, glass(tt(1.0), hz(m), 0.5, 7510 + i), 0.46, 0.15)
    return finish(reverb(x, 1.4, 7519, 0.3))


def maw_catch():
    """He stops a projectile in mid-air: a soft dead thud, a shiver, the object hangs."""
    dur = 1.0
    t = tt(dur)
    x = np.zeros_like(t)
    put(x, boom(0.4, 260, 90, 0.05, 7600, 0.3, 0.2), 0.0, 0.7)
    put(x, glass(tt(0.8), hz(84), 0.3, 7602), 0.0, 0.2)
    x += tinnitus(t, 1200, 7603, 9) * env(t, 0.01, 0.25) * 0.1
    return finish(reverb(x, 1.0, 7609, 0.3))


def maw_hurt():
    """Maw is hit: a thin pained hiss through clenched teeth and a flicker of his field."""
    dur = 0.7
    t = tt(dur)
    e = env(t, 0.02, 0.18, 0.08)
    x = filt(vowel(filt(nz(len(t), 7700), 1500, 9000), "ah", 0.05) * e * 0.6 + osc(t, 420 * 2 ** (-t * 0.8), "saw", seed=7701) * e * 0.15, 200, 9000)
    x += bursts(len(t), 20, 7702, 0.0, 0.3, 3000, 11000, amp=(0.1, 0.5)) * 0.3
    return finish(reverb(x, 0.8, 7709, 0.25))


def maw_death():
    """Ebony Maw falls: a last breath, his field collapses (the hovering grit drops), he hits the ground."""
    dur = 2.8
    x = np.zeros(int(dur * SR))
    n = int(1.2 * SR)
    br = vowel(filt(nz(n, 7800), 500, 6000), "oh", 0.03) * env(tt(1.2), 0.05, 0.4)
    put(x, speed(br, np.linspace(1.0, 0.6, n)), 0.0, 0.5)
    z = tt(1.4)
    for i, m in enumerate((91, 96, 98)):
        put(x, osc(z, hz(m) * 2 ** (-z * 1.2), seed=7810 + i) * env(z, 0.01, 0.5), 0.1, 0.06)
    put(x, bursts(int(1.2 * SR), 120, 7820, 0.0, 1.0, 600, 6000, length=(0.002, 0.02), shape="down"), 0.5, 0.45)
    put(x, boom(1.0, 120, 35, 0.25, 7830, 0.8, 0.4), 1.4, 0.6)
    return finish(reverb(x, 2.0, 7839, 0.36))


# --- Cull Obsidian -----------------------------------------------------------------------------------

def cull_roar():
    """Cull Obsidian roars: a huge ragged throat, swelling and breaking off."""
    dur = 2.4
    t = tt(dur)
    f = 62 * (1 + 0.25 * np.clip(t / 0.4, 0, 1)) * 2 ** (-np.clip((t - 1.2) / 1.0, 0, 1) * 0.5)
    e = env(t, 0.12, 0.6, 1.0)
    x = roar(t, f, 7900, "ah", 2.6, 0.6) * e + roar(t, f * 1.5, 7910, "oh", 2.2, 0.3) * e * 0.3 + osc(t, f * 0.5, seed=7920) * e * 0.4
    return finish(reverb(x, 1.8, 7929, 0.3))


def cull_swing():
    """A swing of his hammer-blade: a deep heavy whoosh, the chain rattling on the haft."""
    x = np.zeros(int(1.0 * SR))
    put(x, whoosh(0.8, 90, 1200, 8000, 0.55, True), 0.0, 1.0)
    put(x, chain(0.6, 8001, 10, 2200), 0.1, 0.25)
    return finish(reverb(x, 1.0, 8009, 0.25))


def cull_slam():
    """He slams the ground: a colossal boom, the slab splitting, concrete raining, a shock rolling out."""
    x = np.zeros(int(2.4 * SR))
    put(x, boom(2.2, 85, 18, 0.65, 8100, 1.5, 1.3), 0.0, 1.0)
    put(x, crunch(1.6, 8101, 90, 200, 5000), 0.0, 0.7)
    put(x, filt(nz(int(0.15 * SR), 8102), 600, 6000) * env(tt(0.15), 0.0005, 0.04), 0.0, 0.6)
    put(x, thunder(2.0, 8103), 0.05, 0.6)
    put(x, metal(tt(0.8), 180, 0.25, 8104), 0.0, 0.15)
    return finish(reverb(x, 1.8, 8109, 0.3))


def cull_charge():
    """His charge: a growl, then pounding footfalls gathering speed, the air shoved ahead of him."""
    dur = 2.2
    t = tt(dur)
    x = np.zeros_like(t)
    at, gap = 0.15, 0.34
    for k in range(9):
        put(x, footfall(8200 + 2 * k), at, 0.6 + 0.04 * k)
        at += gap
        gap = max(0.16, gap * 0.85)
    z = tt(1.0)
    put(x, roar(z, 70, 8220, "uh", 2.4, 0.5) * env(z, 0.05, 0.3), 0.0, 0.5)
    x += bandsweep(nz(len(t), 8230), 100, 600, 200, 1800, curve=0.7) * np.clip(t / 2.0, 0, 1) ** 2 * 0.25
    return finish(reverb(x, 1.4, 8239, 0.28))


def cull_wall():
    """He bursts through a wall: brick and concrete shatter outward, chunks bounce, dust hisses."""
    dur = 2.0
    t = tt(dur)
    x = np.zeros_like(t)
    put(x, boom(1.2, 140, 35, 0.3, 8400, 1.4, 1.4), 0.0, 0.9)
    put(x, crunch(1.5, 8401, 120, 300, 7000), 0.0, 0.8)
    r = d.rng(8402)
    z = tt(0.25)
    for i in range(7):
        chunk = thump(z, r.uniform(120, 220), 60, 0.05, 0.01) * 0.6 + filt(nz(len(z), 8410 + i), 400, 3000) * env(z, 0.001, 0.03) * 0.4
        put(x, chunk, 0.35 + r.uniform(0, 1.1), r.uniform(0.2, 0.45))
    x += filt(nz(len(t), 8420), 2000, 9000) * env(t, 0.1, 0.7) * 0.08
    return finish(reverb(x, 1.6, 8429, 0.3))


def cull_chain():
    """The blade flies out on its chain: links rattle off the haft, a whirl, a clank as it bites."""
    x = np.zeros(int(1.8 * SR))
    put(x, chain(1.0, 8500, 30, 2400), 0.0, 0.5)
    put(x, flange(whoosh(0.9, 300, 2500, 8510, 0.5, False), 1.0, 2.0, 6.0, 0.6), 0.1, 0.5)
    put(x, metal(tt(0.9), 340, 0.3, 8520), 0.9, 0.3)
    put(x, boom(0.5, 200, 60, 0.08, 8521, 0.6, 1.0), 0.9, 0.5)
    put(x, chain(0.6, 8530, 12, 1800), 0.95, 0.3)
    return finish(reverb(x, 1.3, 8539, 0.27))


def cull_grab():
    """His fist closes on a hero: a heavy grab, a grunt, armour creaking in his grip."""
    x = np.zeros(int(1.1 * SR))
    put(x, boom(0.5, 140, 50, 0.1, 8600, 0.8, 0.5), 0.0, 0.7)
    z = tt(0.5)
    put(x, roar(z, 85, 8601, "uh", 2.0, 0.4) * env(z, 0.02, 0.15), 0.05, 0.5)
    for i in range(4):
        put(x, metal(tt(0.15), 600 + 90 * i, 0.04, 8610 + i), 0.25 + 0.12 * i, 0.12)
    return finish(reverb(x, 1.0, 8619, 0.25))


def cull_hurt():
    """Cull is hurt: a short furious grunt."""
    t = tt(0.8)
    x = roar(t, 80 * (1 + 0.3 * np.exp(-t / 0.08)), 8700, "uh", 2.4, 0.5) * env(t, 0.015, 0.16, 0.05)
    return finish(reverb(x, 0.8, 8709, 0.22))


def cull_death():
    """Cull Obsidian falls: a dying roar that sags and breaks, then the weight of him hits the street."""
    x = np.zeros(int(3.0 * SR))
    z = tt(1.6)
    put(x, roar(z, 70 * 2 ** (-np.clip(z / 1.4, 0, 1) * 0.9), 8800, "oh", 2.4, 0.6) * env(z, 0.1, 0.5, 0.3), 0.0, 0.7)
    put(x, boom(1.4, 90, 20, 0.5, 8810, 1.4, 1.0), 1.55, 1.0)
    put(x, crunch(1.2, 8811, 70, 200, 4000), 1.55, 0.5)
    put(x, chain(0.5, 8812, 10, 2000), 1.6, 0.25)
    return finish(reverb(x, 1.8, 8819, 0.3))


def cull_step():
    """A Cull Obsidian footstep: a thud you feel and a crunch of gravel."""
    x = np.zeros(int(0.7 * SR))
    put(x, footfall(8300, 62), 0.0, 1.0)
    return finish(reverb(x, 0.8, 8309, 0.2))


def debris_impact():
    """A thrown slab or car lands: a hard crunch, a thud, fragments skittering."""
    x = np.zeros(int(1.4 * SR))
    put(x, boom(1.0, 130, 40, 0.25, 8900, 1.2, 1.3), 0.0, 0.8)
    put(x, crunch(1.0, 8901, 70, 300, 6000), 0.0, 0.7)
    put(x, metal(tt(0.6), 260, 0.15, 8902), 0.0, 0.12)
    put(x, bursts(int(1.0 * SR), 40, 8903, 0.1, 0.9, 1500, 8000, length=(0.001, 0.006), amp=(0.1, 0.5)), 0.1, 0.3)
    return finish(reverb(x, 1.2, 8909, 0.28))


# name: (synth, momentary loudness dBFS, attenuation distance in blocks; the server volume scales it)
CUES = {
    "ship_arrival": (ship_arrival, -9.0, 64), "ship_hum": (ship_hum, -18.0, 64), "ship_horn": (ship_horn, -10.0, 64),
    "ship_charge": (ship_charge, -12.0, 32), "ship_lance": (ship_lance, -8.5, 48), "ship_beam": (ship_beam, -12.0, 40),
    "ship_depart": (ship_depart, -10.0, 64), "portal_open": (portal_open, -11.0, 40), "siren": (siren, -16.0, 48),
    "command": (command, -11.0, 48), "absorb": (absorb, -11.0, 40), "rupture": (rupture, -9.0, 48),
    "barrage": (barrage, -10.0, 40), "rewrite": (rewrite, -11.0, 40), "anomaly": (anomaly, -11.0, 40),
    "compulsion": (compulsion, -11.5, 40), "harvest": (harvest, -11.0, 40), "time_stop": (time_stop, -10.0, 48),
    "volley": (volley, -12.0, 48), "rock_impact": (rock_impact, -10.0, 48), "infinity_stage": (infinity_stage, -9.0, 48),
    "maw_voice": (maw_voice, -14.0, 32), "maw_lift": (maw_lift, -12.0, 32), "maw_throw": (maw_throw, -11.0, 32),
    "maw_grab": (maw_grab, -12.0, 32), "maw_barrier": (maw_barrier, -11.5, 32), "maw_catch": (maw_catch, -12.0, 24),
    "maw_hurt": (maw_hurt, -13.0, 20), "maw_death": (maw_death, -11.0, 32),
    "cull_roar": (cull_roar, -9.5, 48), "cull_swing": (cull_swing, -12.0, 24), "cull_slam": (cull_slam, -8.5, 40),
    "cull_charge": (cull_charge, -10.0, 40), "cull_wall": (cull_wall, -9.0, 40), "cull_chain": (cull_chain, -11.0, 32),
    "cull_grab": (cull_grab, -12.0, 24), "cull_hurt": (cull_hurt, -12.0, 20), "cull_death": (cull_death, -10.0, 40),
    "cull_step": (cull_step, -13.0, 24), "debris_impact": (debris_impact, -11.0, 32),
}
LOOPS = ("ship_hum", "siren")


# --- packaging ------------------------------------------------------------------------------------

def overlay():
    """The sound overlay text: one event per cue, the loops streamed."""
    out = {}
    for name, (_, _, dist) in CUES.items():
        snd = {"name": "viltrumitecore:thanos_war/" + name, "attenuation_distance": dist}
        if name in LOOPS:
            snd["stream"] = True
        out["thanos_war." + name] = {"category": "ambient" if name == "siren" else "hostile",
                                     "subtitle": "subtitles.viltrumitecore.thanos_war." + name, "sounds": [snd]}
    return json.dumps(out, indent=2) + "\n"


def committed(name):
    single = OUT / (name + ".ogg.b64")
    if single.exists():
        return single.read_text()
    parts = sorted(OUT.glob(name + ".ogg.b64.part*"), key=lambda p: int(p.name.rsplit("part", 1)[1]))
    return "".join(p.read_text() for p in parts) if parts else None


def write(name, ogg):
    text = base64.encodebytes(ogg.read_bytes()).decode("ascii")
    for old in [OUT / (name + ".ogg.b64")] + list(OUT.glob(name + ".ogg.b64.part*")):
        old.unlink(missing_ok=True)
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
    for name, (fn, loud, _) in CUES.items():
        if only and name not in only:
            continue
        x = d.master(fn(), loud)
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
        if a.check and (not OVERLAY.exists() or OVERLAY.read_text() != overlay()):
            print("differs", OVERLAY.name)
            bad += 1
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
                print("%-16s %6.2f s %7d B%s" % (name, len(d.decoded_pcm(ogg)) / 2 / SR, ogg.stat().st_size,
                                                 " (%d parts)" % parts if parts > 1 else ""))
        if not a.check:
            OVERLAY.write_text(overlay())
        print("%d cues %s" % (len(built), "checked" if a.check else "written"))
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
