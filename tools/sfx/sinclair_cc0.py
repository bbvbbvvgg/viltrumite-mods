#!/usr/bin/env python3
"""The Magnificent Sinclair, sound pass 2 (design 2026-11-02 §4): Kenney CC0 recordings + own synthesis.

Sources: Kenney "Casino Audio", "Impact Sounds", "Interface Sounds" and "RPG Audio" (kenney.nl, CC0 1.0;
see tools/assets/sinclair_sources.md) for the organic layers - cards, cloth, wood, metal, glass - and
numpy synthesis (tools/sfx/sinclair.py primitives, own work, CC0) for the magic: shimmer, pads, risers,
the beam. No vanilla sounds. Every event is mono 44.1 kHz Vorbis q5, installed as <name>.ogg.b64 for the
Gradle decodeBinaryAssets pipeline. The 31 pass-1 events keep their entries in the shared sounds.json; the
new events are defined in assets/viltrumitecore_sinclair/sounds.json (see SinclairSounds.stage).

Rules applied to every event (the pass-1 audit found 14 hard cut-offs, 15 bare sine tones and an 11 dB
loudness spread): content decays before the end and a raised-cosine release closes it (tail check
< -40 dB); loudness is matched per class (active-part RMS, see CLASS) with a soft limiter at -1 dBFS;
timed events are cut to the server beats in ActScript / CopyCinematic (comments give the tick).

Run: python3 tools/sfx/sinclair_cc0.py KENNEY_ROOT [--only NAME ...] [--wav DIR] [--audit]
KENNEY_ROOT holds the unpacked packs: casino-audio/, impact-sounds/, interface-sounds/, rpg-audio/
(each with Audio/*.ogg). --audit prints duration, loudness and tail level of the committed files.
"""
import argparse
import base64
import glob
import json
import os
import subprocess
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sinclair as S  # noqa: E402  (primitives only)

SR = S.SR
OUT = S.OUT
SOUNDS_JSON = S.SOUNDS_JSON
PACKS = ("casino-audio", "impact-sounds", "interface-sounds", "rpg-audio")
KROOT = None
_CACHE = {}


# ------------------------------------------------------------------ sample access
def K(stem):
    """A Kenney CC0 recording by file stem, mono float at 44.1 kHz, leading silence trimmed."""
    if stem in _CACHE:
        return _CACHE[stem]
    for pack in PACKS:
        path = os.path.join(KROOT, pack, "Audio", stem + ".ogg")
        if os.path.isfile(path):
            raw = subprocess.run(["ffmpeg", "-v", "error", "-nostdin", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                                 check=True, capture_output=True).stdout
            y = np.frombuffer(raw, dtype="<f4").astype(np.float64)
            on = np.argmax(np.abs(y) > 0.01 * np.max(np.abs(y)))
            y = y[max(0, on - 32):]
            y = y / (np.max(np.abs(y)) or 1.0)
            _CACHE[stem] = y
            return y
    raise SystemExit("missing Kenney sample: " + stem)


def pitch(x, factor):
    """Varispeed: factor > 1 is higher and shorter (tape-style, keeps transients natural)."""
    if factor == 1.0:
        return x
    idx = np.arange(0, len(x) - 1, factor)
    return np.interp(idx, np.arange(len(x)), x)


def cut(x, dur, release=0.04):
    n = min(len(x), int(dur * SR))
    return release_tail(x[:n], release)


def release_tail(x, r):
    x = x.copy()
    m = min(len(x), int(r * SR))
    if m > 1:
        x[-m:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, m))
    return x


def db(v):
    return 10 ** (v / 20.0)


def room(x, t60=0.9, wet=0.25, seed=7, bright=7000, tail=True):
    """Small hall: FFT convolution with exponentially decaying filtered noise (fast, deterministic)."""
    n_ir = int(t60 * SR)
    r = S.rng(seed)
    ir = r.normal(0, 1, n_ir) * np.exp(-6.9 * np.arange(n_ir) / n_ir)
    ir = S.lowpass(ir, bright)
    ir[: int(0.008 * SR)] *= np.linspace(0, 1, int(0.008 * SR))
    ir /= np.sqrt(np.sum(ir ** 2)) or 1.0
    n = len(x) + (n_ir if tail else 0)
    m = 1 << int(np.ceil(np.log2(len(x) + n_ir)))
    y = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)[:n]
    dry = np.pad(x, (0, n - len(x)))
    return dry * (1 - wet) + y * wet * 0.6


def sparkle(d, grains, lo, hi, seed, decay=0.07, shape=None):
    """Glitter: many tiny inharmonic bell grains (two detuned partials), never a bare tone."""
    r = S.rng(seed)
    out = np.zeros(int(d * SR))
    for _ in range(grains):
        u = r.uniform(0, 1)
        if shape is not None and r.uniform() > shape(u):
            continue
        f = np.exp(r.uniform(np.log(lo), np.log(hi)))
        g = S.bell(min(0.4, decay * 5), f, partials=((1, 1), (2.31 + r.uniform(-0.05, 0.05), 0.35), (3.7, 0.12)), tau=decay)
        i = int(u * (len(out) - 1))
        j = min(len(out), i + len(g))
        out[i:j] += g[: j - i] * r.uniform(0.3, 1.0)
    return out


def air(d, lo, hi, seed, a=0.05, r=0.2):
    n = int(d * SR)
    return S.band(S.rng(seed).normal(0, 1, n), lo, hi) * S.env(n, a, d - a - r, 1.0, r)


def thump(d, f0, f1, tau, seed=1, click_gain=0.5):
    n = int(d * SR)
    body = np.sin(2 * np.pi * np.cumsum(np.geomspace(f0, f1, n)) / SR) * S.expdecay(n, tau)
    hit = S.lowpass(S.rng(seed).normal(0, 1, n), 2500) * S.expdecay(n, 0.006)
    return body + hit * click_gain


def riser(d, f0, f1, seed):
    n = int(d * SR)
    t = S.t_axis(d)
    noise = S.sweep_filter(S.rng(seed).normal(0, 1, n), f0, f1)
    f = f0 / 4 * (f1 / f0) ** (t / d)
    tone = S.lowpass(sum(S.saw(d, 1.0, 0) * 0 + 2 * ((np.cumsum(f * (1 + det)) / SR) % 1.0) - 1 for det in (-0.006, 0.0, 0.007)) / 3, 3000)
    return (noise * 0.7 + tone * 0.35) * (t / d) ** 1.6


def ray(d, base, seed):
    """Beam body: detuned saw stack through a slowly moving band, with a hiss layer (not a sine)."""
    n = int(d * SR)
    t = S.t_axis(d)
    stack = np.zeros(n)
    for k, det in enumerate((-0.008, -0.003, 0.0, 0.004, 0.009)):
        ph = S.rng(seed + k).uniform(0, 1)
        stack += 2 * ((base * (1 + det) * t + ph) % 1.0) - 1
        stack += 0.5 * (2 * ((base * 1.5 * (1 + det) * t + ph) % 1.0) - 1)
    stack = S.sweep_filter(stack / 7.5, 900, 1500, q=1.5)
    hiss = S.band(S.rng(seed + 9).normal(0, 1, n), 3500, 11000) * (0.6 + 0.4 * np.sin(2 * np.pi * 7.3 * t))
    return stack + hiss * 0.18


def clap(r):
    """One human clap: two or three slaps a few ms apart, palm resonance, short room."""
    n = int(0.06 * SR)
    y = np.zeros(n)
    for k in range(r.integers(2, 4)):
        o = int(r.uniform(0, 0.006) * SR) + k * int(0.002 * SR)
        m = int(0.02 * SR)
        s = r.normal(0, 1, m) * np.exp(-np.arange(m) / (r.uniform(0.003, 0.006) * SR))
        j = min(n, o + m)
        y[o:j] += s[: j - o] * r.uniform(0.5, 1.0)
    lo, hi = r.uniform(700, 1100), r.uniform(2400, 4200)
    return S.band(np.pad(y, (0, 512)), lo, hi)


def crowd(d, people, seed, shape):
    """Applause from individual clappers: own tempo (3.2-5.2 Hz), drift, timbre and loudness each."""
    r = S.rng(seed)
    out = np.zeros(int(d * SR))
    for p in range(people):
        rate = r.uniform(3.2, 5.2)
        t = r.uniform(0, 0.25)
        loud = r.uniform(0.3, 1.0)
        start, stop = r.uniform(0.0, 0.18), r.uniform(0.75, 1.0)
        while t < d:
            u = t / d
            if start <= u <= stop:
                c = clap(r) * loud * shape(u)
                i = int(t * SR)
                j = min(len(out), i + len(c))
                out[i:j] += c[: j - i]
            t += 1.0 / rate * r.uniform(0.9, 1.1)
            rate *= r.uniform(0.995, 1.004)
    return out


def snare_roll(d, seed, rate0=17.0, rate1=24.0):
    """Humanised buzz roll: alternating hands, timing jitter, crescendo, wires rattling between strokes."""
    r = S.rng(seed)
    out = np.zeros(int(d * SR))
    t, k = 0.0, 0
    while t < d - 0.05:
        u = t / d
        vel = (0.25 + 0.75 * u ** 1.4) * (1.0 if k % 2 == 0 else 0.82) * r.uniform(0.85, 1.0)
        m = int(0.07 * SR)
        head = np.sin(2 * np.pi * r.uniform(185, 205) * np.arange(m) / SR) * S.expdecay(m, 0.012)
        wires = S.band(r.normal(0, 1, m), 1800, 8500) * S.expdecay(m, r.uniform(0.02, 0.03))
        hit = head * 0.5 + wires
        i = int(t * SR)
        j = min(len(out), i + m)
        out[i:j] += hit[: j - i] * vel
        t += 1.0 / (rate0 + (rate1 - rate0) * u) + r.normal(0, 0.003)
        k += 1
    return out


def brass_stab(d, freqs, seed, bright=4500):
    return S.chord(d, freqs, seed, bright)


def celesta(d, freqs, seed, tau=0.5):
    y = np.zeros(int(d * SR))
    for i, f in enumerate(freqs):
        b = S.bell(d, f, partials=((1, 1), (2.0, 0.25), (3.01, 0.12), (4.2, 0.05)), tau=tau)
        y[: len(b)] += b * (0.8 + 0.2 * S.rng(seed + i).uniform())
    return y / max(1, len(freqs))


def P(total, parts):
    """S.place that grows to fit every layer: no layer is ever hard-cut by the buffer end."""
    need = max([total] + [off + len(sig) / SR for off, sig, _ in parts])
    return S.place(need + 0.005, parts)


# ------------------------------------------------------------------ events
def wand_shot():
    return P(0.32, [(0.0, pitch(K("glass_002"), 1.35), 0.55), (0.0, S.whoosh(0.12, 2500, 7000, 401), 0.5),
                    (0.0, S.click(int(0.01 * SR), 402), 0.5), (0.02, sparkle(0.28, 10, 3000, 9000, 403, 0.05), 0.5)])


def spark_hit():
    return P(0.4, [(0.0, pitch(K("impactGlass_light_001"), 1.25), 0.7), (0.0, thump(0.12, 900, 260, 0.03, 411, 0.3), 0.4),
                   (0.02, sparkle(0.35, 8, 2500, 8000, 412, 0.05, lambda u: 1 - u), 0.45)])


def wand_reload():
    return P(1.0, [(0.05, S.whoosh(0.28, 600, 3000, 32), 0.6), (0.3, S.whoosh(0.28, 700, 3400, 33), 0.6),
                   (0.6, pitch(K("metalClick"), 1.1), 0.8), (0.64, sparkle(0.34, 9, 2200, 7000, 421, 0.06), 0.45)])


def bolt_cast():
    d = 0.7
    n = int(0.6 * SR)
    t = S.t_axis(0.6)
    f = 180 * (2.2 ** (t / 0.6))
    hum = np.sin(2 * np.pi * np.cumsum(f) / SR + 2.0 * np.sin(2 * np.pi * f * 1.5 * t)) * S.env(n, 0.12, 0.3, 0.25, 0.15)
    hum = hum * 0.6 + S.band(S.rng(431).normal(0, 1, n), 400, 2400) * S.env(n, 0.1, 0.3, 0.2, 0.15) * 0.4
    return P(d, [(0.0, pitch(K("card-slide-3"), 1.15), 0.8), (0.02, hum, 0.45), (0.25, sparkle(0.4, 10, 1500, 6000, 432, 0.07), 0.4)])


def bolt_snap():
    return P(0.4, [(0.0, S.finger_snap(51, 1.1), 0.9), (0.0, pitch(K("switch_002"), 0.9), 0.35),
                   (0.03, S.whoosh(0.18, 1800, 5000, 441), 0.35), (0.04, sparkle(0.3, 7, 2500, 7000, 442, 0.05), 0.35)])


def bolt_hit():
    d = 0.9
    n = int(0.7 * SR)
    crackle = S.band(S.rng(61).normal(0, 1, n), 800, 6000) * S.expdecay(n, 0.07) * (S.rng(62).random(n) > 0.6)
    return room(P(d, [(0.0, pitch(K("impactGlass_medium_002"), 0.9), 0.6), (0.0, thump(0.5, 150, 45, 0.09, 451), 0.9),
                      (0.0, crackle, 0.4), (0.03, sparkle(0.7, 16, 1500, 7000, 452, 0.08, lambda u: 1 - u), 0.4)]), 0.6, 0.2, 453)


def assistant_appear():
    d = 1.2
    n = int(d * SR)
    choir = S.pad(d, (523.25, 659.25, 783.99), 71) * S.env(n, 0.45, 0.4, 0.6, 0.35)
    gl = S.harp_gliss([392, 440, 523.25, 587.33, 659.25, 783.99, 880, 1046.5], 0.045, 72)
    mix = P(1.3, [(0.0, choir, 0.6), (0.0, gl, 0.55), (0.0, pitch(K("cloth2"), 1.1), 0.35),
                  (0.25, sparkle(1.0, 24, 2000, 8000, 461, 0.08, lambda u: np.sin(np.pi * u)), 0.35)])
    return room(mix, 1.0, 0.3, 462)


def swap():
    return P(0.55, [(0.0, S.swap(), 1.0), (0.05, pitch(K("cloth1"), 1.3), 0.4)])


def assistant_leave():
    d = 1.2
    n = int(d * SR)
    gl = S.harp_gliss([1046.5, 880, 783.99, 659.25, 587.33, 523.25, 440, 392], 0.05, 91)
    breath = S.pad(d, (659.25, 783.99), 92) * S.env(n, 0.05, 0.8, 0.0, 0.3, curve=2.0)
    return room(P(1.25, [(0.0, gl, 0.55), (0.0, breath, 0.45), (0.0, pitch(K("cloth3"), 1.2), 0.3)]), 1.0, 0.3, 471)


def hex_cast():
    d = 0.75
    t = S.t_axis(0.45)
    boing = np.sin(2 * np.pi * np.cumsum(300 + 120 * np.sin(2 * np.pi * 9 * t) * np.exp(-t * 5)) / SR) * S.expdecay(len(t), 0.18)
    boing = boing * 0.5 + S.band(S.rng(481).normal(0, 1, len(t)), 250, 1200) * S.expdecay(len(t), 0.12) * 0.5
    return P(d, [(0.0, pitch(K("cloth4"), 1.2), 0.6), (0.0, S.whoosh(0.3, 500, 2500, 101), 0.45), (0.18, boing, 0.45),
                 (0.2, sparkle(0.5, 10, 1500, 5000, 482, 0.08), 0.35)])


def hex_poof():
    return room(P(0.9, [(0.0, pitch(K("impactSoft_medium_002"), 1.1), 0.7), (0.0, S.smoke_poof(111, 0.6), 0.6),
                        (0.06, sparkle(0.6, 12, 1800, 6000, 491, 0.07, lambda u: 1 - u), 0.4)]), 0.7, 0.25, 492)


def hex_end():
    rev = S.smoke_poof(121, 0.4)[::-1]
    return P(0.95, [(0.0, rev, 0.6), (0.38, pitch(K("impactSoft_medium_000"), 1.4), 0.6),
                    (0.4, sparkle(0.35, 8, 1200, 4000, 501, 0.06, lambda u: 1 - u), 0.4)])


def snap():
    return room(S.snap(), 0.5, 0.18, 511)


def decoy_reveal():
    confetti = S.band(S.rng(141).normal(0, 1, int(0.5 * SR)), 2500, 9000) * (S.rng(142).random(int(0.5 * SR)) > 0.85) * S.expdecay(int(0.5 * SR), 0.18)
    return room(P(0.85, [(0.0, S.smoke_poof(143, 0.5), 0.8), (0.0, pitch(K("cloth1"), 1.4), 0.4), (0.02, confetti, 0.5),
                         (0.05, sparkle(0.6, 12, 1300, 4500, 521, 0.09, lambda u: 1 - u), 0.4)]), 0.6, 0.22, 522)


def tada():
    ta = brass_stab(0.14, (392, 493.88, 587.33), 151, 3000)
    da = brass_stab(0.8, (523.25, 659.25, 783.99, 1046.5), 152, 5000)
    return room(P(1.1, [(0.0, ta, 0.9), (0.17, da, 1.0), (0.17, S.cymbal(0.8, 531, 0.5), 0.12),
                        (0.17, sparkle(0.8, 14, 2000, 8000, 532, 0.1, lambda u: 1 - u), 0.25)]), 1.0, 0.28, 533)


def refuse():
    """Muted 'wah-wah': two falling brass notes through a closing filter, then a soft knock."""
    def wah(d, f, seed):
        n = int(d * SR)
        y = S.saw(d, f) + S.saw(d, f, 0.01)
        return S.sweep_filter(y, 1600, 450, q=2.0) * S.env(n, 0.02, d - 0.08, 0.7, 0.06)
    return room(P(0.6, [(0.0, wah(0.16, 233.08, 541), 0.7), (0.18, wah(0.26, 174.61, 542), 0.7),
                        (0.42, pitch(K("impactWood_light_002"), 1.3), 0.25)]), 0.5, 0.2, 543)


def ovation():
    d = 2.6
    shape = lambda u: min(1.0, u / 0.12) * (1.0 if u < 0.55 else max(0.0, 1 - (u - 0.55) / 0.45)) ** 1.3
    return room(crowd(d, 26, 551, shape), 1.2, 0.3, 552)


def ult_capture():
    """Follow-spot switched on: heavy latch, warm lamp hum (filtered, not a sine), shimmer rising into the light."""
    d = 1.4
    n = int(d * SR)
    t = S.t_axis(d)
    lamp = S.lowpass(S.saw(d, 60) + 0.6 * S.saw(d, 120, 0.003), 700) * S.env(n, 0.06, 0.5, 0.45, 0.5)
    lamp += S.band(S.rng(561).normal(0, 1, n), 2000, 6000) * 0.05 * (1 + np.sin(2 * np.pi * 120 * t))
    return room(P(d, [(0.0, pitch(K("metalLatch"), 0.75), 0.9), (0.0, pitch(K("impactMetal_heavy_001"), 0.6), 0.35),
                      (0.02, lamp, 0.35), (0.2, S.whoosh(0.8, 700, 3500, 562), 0.25),
                      (0.3, sparkle(1.0, 22, 1500, 7000, 563, 0.09, lambda u: np.sin(np.pi * u)), 0.4)]), 0.9, 0.22, 564)


def ult_card():
    return P(0.8, [(0.0, S.whoosh(0.3, 800, 4000, 181), 0.5), (0.22, pitch(K("card-slide-5"), 1.1), 0.8),
                   (0.28, sparkle(0.5, 10, 2500, 8000, 571, 0.08, lambda u: 1 - u), 0.4)])


def act_drumroll():
    # ActScene.begin (t = 0): a 1.4 s roll that swells into each act's main beat and dies away under it
    d = 1.5
    roll = snare_roll(1.35, 581)
    return room(P(d, [(0.0, roll, 1.0)]), 0.7, 0.2, 582)


def act_footlights():
    # BEAM t = 2: five footlights clunk on, converging (t 2..18), lamp hum swells until the ray (t 28)
    d = 1.5
    n = int(d * SR)
    lamp = S.lowpass(S.saw(d, 100) + 0.5 * S.saw(d, 150, 0.004), 900) * S.env(n, 0.5, 0.6, 0.8, 0.25)
    parts = [(0.0, lamp, 0.25)]
    for k, at in enumerate((0.0, 0.22, 0.42, 0.58, 0.72)):
        parts.append((at, pitch(K("switch_00%d" % (3 + k % 3)), 0.7), 0.6))
        parts.append((at, pitch(K("impactMetal_light_00%d" % k), 0.55), 0.25))
    parts.append((0.5, sparkle(0.9, 14, 1500, 6000, 591, 0.08, lambda u: u), 0.3))
    return room(P(d, parts), 0.8, 0.2, 592)


def act_beam():
    # BEAM t = 28..70 (2.1 s): the ray with an accent on each damage pulse (every 5 ticks = 0.25 s, 9 pulses)
    d = 2.3
    n = int(d * SR)
    body = ray(d, 110, 601) * S.env(n, 0.06, 0.2, 0.85, 0.22)
    pulse = np.zeros(n)
    for k in range(9):
        i = int(k * 0.25 * SR)
        m = int(0.12 * SR)
        j = min(n, i + m)
        pulse[i:j] += np.exp(-np.arange(j - i) / (0.03 * SR))
    body = body * (0.75 + 0.35 * pulse)
    ticks = [(k * 0.25, pitch(K("impactGlass_light_00%d" % (k % 5)), 1.6), 0.18) for k in range(9)]
    return room(P(d, [(0.0, S.whoosh(0.25, 600, 3000, 602), 0.4), (0.0, body, 1.0)] + ticks), 0.7, 0.18, 603)


def act_beam_charge():
    # BEAM t = 70..78 (0.4 s): the card is drawn back and the light gathers into it
    d = 0.55
    return P(d, [(0.0, release_tail(riser(0.44, 500, 5000, 611), 0.06), 1.0), (0.0, sparkle(0.42, 12, 2500, 9000, 612, 0.04, lambda u: u), 0.4),
                 (0.38, S.cymbal(0.17, 613, 0.05), 0.15)])


def act_beam_burst():
    # BEAM t = 78: overdrive flash down the beam (shove), 1.8 s of falling glitter
    d = 1.9
    return room(P(d, [(0.0, thump(0.9, 120, 32, 0.18, 621, 0.8), 1.0), (0.0, pitch(K("impactGlass_heavy_002"), 0.85), 0.55),
                      (0.0, S.cymbal(1.4, 622, 0.5), 0.25), (0.0, ray(0.4, 165, 623) * S.expdecay(int(0.4 * SR), 0.1), 0.6),
                      (0.05, sparkle(1.6, 34, 1500, 9000, 624, 0.1, lambda u: (1 - u) ** 1.5), 0.45)]), 1.1, 0.25, 625)


def act_hat_spin():
    # NOVA t = 3..20 (0.85 s): the top hat spun on a fingertip, faster and faster, flicked off at t = 20
    d = 0.95
    n = int(0.85 * SR)
    t = np.arange(n) / SR
    rate = 5 + 14 * (t / 0.85) ** 1.5
    trem = 0.45 + 0.55 * np.sin(2 * np.pi * np.cumsum(rate) / SR) ** 2
    felt = S.band(S.rng(631).normal(0, 1, n), 300, 2400) * trem * S.env(n, 0.1, 0.5, 0.9, 0.05)
    cloth = np.tile(pitch(K("cloth1"), 1.5), 3)[:n] * 0.4
    spin = release_tail(felt + cloth * trem, 0.12)
    return P(d, [(0.0, spin, 0.8), (0.76, S.whoosh(0.2, 900, 3800, 632), 0.6), (0.8, air(0.3, 500, 2500, 633, 0.02, 0.25), 0.15)])


def act_nova_whirl():
    # NOVA t = 20 (throw) and t = 38 (second whirl over the target)
    d = 1.1
    n = int(0.95 * SR)
    t = np.arange(n) / SR
    rate = 6 + 18 * t / 0.95
    trem = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(rate) / SR)
    w = S.sweep_filter(S.rng(211).normal(0, 1, n), 400, 2600) * trem
    w = w * S.env(n, 0.15, 0.4, 0.8, 0.3)
    return P(d, [(0.0, w, 1.0), (0.0, pitch(K("cloth4"), 0.8), 0.3), (0.2, sparkle(0.8, 10, 1200, 4000, 641, 0.07), 0.3)])


def act_nova_burst():
    # NOVA t = 52: the hat bursts into cards (damage + knock-back)
    flutter = np.concatenate([S.paper_flick(220 + i) * S.rng(240 + i).uniform(0.2, 0.7) for i in range(14)])
    return room(P(2.0, [(0.0, thump(0.8, 120, 32, 0.16, 651, 0.8), 1.0), (0.0, S.smoke_poof(221, 0.6), 0.5),
                        (0.0, S.cymbal(1.2, 222, 0.45), 0.25), (0.02, K("card-fan-2"), 0.7), (0.1, cut(K("card-shuffle"), 0.9, 0.4), 0.45),
                        (0.05, flutter, 0.4), (0.1, sparkle(1.4, 24, 1500, 8000, 652, 0.09, lambda u: 1 - u), 0.35)]), 1.0, 0.25, 653)


def act_cut_saw():
    # CUT t = 14: the line of standing cards is laid out (t 14..24), then the blade sings once
    parts = [(at, pitch(K("card-place-%d" % (1 + k % 4)), 1.0 + 0.05 * k), 0.7) for k, at in enumerate((0.0, 0.1, 0.18, 0.25, 0.31, 0.36))]
    parts.append((0.45, pitch(K("drawKnife3"), 1.1), 0.5))
    parts.append((0.47, S.bell(0.45, 1244.5, partials=((1, 1), (2.41, 0.6), (3.87, 0.4), (5.9, 0.3)), tau=0.3), 0.15))
    return room(P(1.0, parts), 0.6, 0.2, 661)


def act_saw_rasp():
    # CUT t = 30 and 38: one saw stroke along the line (1 damage)
    d = 0.5
    n = int(0.36 * SR)
    t = np.arange(n) / SR
    teeth = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 38 * t))
    rasp = S.band(S.rng(671).normal(0, 1, n), 1000, 5500) * (0.4 + 0.6 * teeth) * np.sin(np.pi * t / 0.36) ** 0.8
    return P(d, [(0.0, rasp, 0.8), (0.0, pitch(K("knifeSlice"), 0.75), 0.6), (0.02, pitch(K("impactWood_light_001"), 0.9), 0.3)])


def act_cut_slash():
    # CUT t = 50: the grand cut along the whole line (6 damage)
    shing = S.bell(0.9, 1661.2, partials=((1, 1), (2.13, 0.7), (3.41, 0.5), (4.9, 0.35), (6.77, 0.2)), tau=0.35)
    return room(P(1.4, [(0.0, S.whoosh(0.25, 5000, 400, 681), 0.7), (0.05, pitch(K("drawKnife1"), 0.8), 0.7),
                        (0.12, pitch(K("impactWood_heavy_001"), 0.85), 0.8), (0.12, thump(0.5, 90, 35, 0.12, 682, 0.3), 0.6),
                        (0.12, shing, 0.2), (0.15, sparkle(1.0, 14, 1500, 6000, 683, 0.08, lambda u: 1 - u), 0.25)]), 1.0, 0.25, 684)


def card_hit():
    return P(0.28, [(0.0, pitch(K("card-place-2"), 1.15), 0.8), (0.0, S.card_hit(), 0.35)])


def act_card_fan():
    # BARRAGE t = 1 (fan of twelve opens) and t = 42 (pitch 1.25, the last three gather overhead)
    return P(0.8, [(0.0, K("card-fan-1"), 0.9), (0.15, sparkle(0.6, 12, 2500, 8000, 691, 0.07), 0.35)])


def act_card_volley():
    # BARRAGE t = 20 / 28 / 36: three cards thrown
    parts = []
    for k in range(3):
        parts.append((k * 0.07, cut(pitch(K("card-shove-%d" % (1 + k)), 1.1 + 0.05 * k), 0.4, 0.15), 0.6))
        parts.append((k * 0.07, S.whoosh(0.14, 1200 + 100 * k, 5000, 251 + k), 0.45))
    return P(0.55, parts)


def act_card_finale():
    # BARRAGE t = 50: the finale volley thrown down from above the head
    return room(P(1.2, [(0.0, S.whoosh(0.3, 5000, 700, 701), 0.6), (0.0, K("card-fan-2"), 0.6), (0.05, cut(K("card-shuffle"), 0.7, 0.35), 0.4),
                        (0.22, pitch(K("impactPunch_medium_002"), 1.0), 0.5), (0.22, pitch(K("card-place-4"), 0.9), 0.6),
                        (0.25, sparkle(0.9, 14, 1500, 7000, 702, 0.08, lambda u: 1 - u), 0.3)]), 0.8, 0.22, 703)


def act_curtain():
    # AEGIS t = 0: the velvet curtain falls around him
    rings = [(0.02 + k * 0.035, pitch(K("chips-collide-%d" % (1 + k % 4)), 1.4), 0.12) for k in range(6)]
    return room(P(1.3, [(0.0, S.whoosh(0.9, 180, 900, 261), 0.7), (0.0, pitch(K("cloth3"), 0.65), 0.8),
                        (0.6, pitch(K("impactSoft_heavy_001"), 0.8), 0.5)] + rings), 0.8, 0.22, 711)


def act_curtain_rustle():
    # AEGIS t = 14 and 30: the curtain stirs while he turns inside
    return P(0.8, [(0.0, pitch(K("cloth2"), 0.8), 0.8), (0.1, pitch(K("cloth4"), 0.7), 0.5), (0.0, air(0.75, 200, 1200, 721, 0.1, 0.35), 0.25)])


def act_curtain_open():
    # AEGIS t = 50 (blast) and FINALE t = 58 (pitch 1.2, the chest flies open)
    n = int(0.55 * SR)
    swell = S.cymbal(0.55, 271, 2.0)[::-1] * np.linspace(0, 1, n) ** 2
    return room(P(1.4, [(0.0, swell, 0.3), (0.5, pitch(K("cloth4"), 1.1), 0.7), (0.5, S.whoosh(0.4, 900, 200, 272), 0.6),
                        (0.52, brass_stab(0.5, (523.25, 659.25, 783.99), 273), 0.55),
                        (0.52, sparkle(0.8, 16, 2000, 8000, 731, 0.09, lambda u: 1 - u), 0.3)]), 1.0, 0.25, 732)


def act_chest():
    # FINALE t = 24: the magic chest drops and lands
    return room(P(1.3, [(0.0, S.whoosh(0.22, 300, 1200, 283), 0.4), (0.2, pitch(K("impactWood_heavy_003"), 0.8), 1.0),
                        (0.2, pitch(K("impactPlank_medium_002"), 0.8), 0.5), (0.2, thump(0.5, 80, 35, 0.12, 741, 0.2), 0.6),
                        (0.4, pitch(K("metalLatch"), 1.0), 0.4), (0.55, pitch(K("creak1"), 1.1), 0.35)]), 0.8, 0.2, 742)


def act_sword():
    # FINALE t = 30 / 38 / 46 (pitch steps up): a sword is pushed through the chest
    return P(0.8, [(0.0, pitch(K("drawKnife2"), 1.0), 0.6), (0.1, pitch(K("impactWood_medium_002"), 1.0), 0.7),
                   (0.1, S.bell(0.5, 1661.2, partials=((1, 1), (2.13, 0.7), (3.41, 0.5), (4.9, 0.35)), tau=0.25), 0.15)])


def act_chest_rattle():
    # FINALE t = 52..58 (0.3 s): the chest trembles just before the reveal
    parts = [(k * 0.04, cut(pitch(K("impactWood_light_00%d" % (k % 5)), 1.0 + 0.04 * k), 0.12, 0.05), 0.4 + 0.08 * k) for k in range(7)]
    parts.append((0.0, cut(pitch(K("metalPot2"), 1.6), 0.3, 0.1), 0.2))
    return P(0.45, parts)


def act_bow():
    shape = lambda u: min(1.0, u / 0.08) * max(0.0, 1.0 - u) ** 1.2
    fan = brass_stab(0.9, (523.25, 659.25, 783.99, 1046.5), 302, 4500)
    return room(P(2.3, [(0.0, S.cymbal(1.0, 303, 0.7), 0.25), (0.0, fan, 0.7), (0.1, crowd(2.0, 18, 751, shape), 0.9)]), 1.0, 0.28, 752)


def copy_sting():
    # COPY t = 0: the world stops - a bell-like card sting over a low 'time halt'
    n = int(1.2 * SR)
    halt = S.lowpass(S.rng(761).normal(0, 1, n), 300) * S.env(n, 0.005, 0.5, 0.0, 0.4) * 1.4
    pad = S.pad(1.2, (392.0, 587.33, 783.99), 762) * S.env(n, 0.05, 0.6, 0.4, 0.5)
    return room(P(1.5, [(0.0, thump(0.7, 160, 40, 0.14, 763, 0.4), 0.8), (0.0, halt, 0.4), (0.0, pitch(K("impactBell_heavy_001"), 1.5), 0.45),
                        (0.02, celesta(1.2, (783.99, 1174.66, 1567.98), 764, 0.45), 0.6), (0.05, pad, 0.35),
                        (0.08, sparkle(1.2, 22, 2000, 9000, 765, 0.09, lambda u: 1 - u), 0.35)]), 1.2, 0.3, 766)


def copy_unfold():
    # COPY t = 8..18: the card unfolds towards the camera
    return P(0.8, [(0.0, pitch(K("cards-pack-open-1"), 1.1), 0.7), (0.1, pitch(K("bookFlip2"), 1.3), 0.4),
                   (0.0, sparkle(0.6, 16, 2000, 9000, 771, 0.06, lambda u: u ** 0.7), 0.4), (0.0, S.whoosh(0.5, 600, 4000, 772), 0.3)])


def copy_fold():
    # COPY t = 44..52: the card folds and flies to its owner
    return P(0.7, [(0.0, pitch(K("card-slide-7"), 1.0), 0.7), (0.12, pitch(K("bookClose"), 1.6), 0.35),
                   (0.0, S.whoosh(0.45, 4000, 700, 781), 0.35), (0.05, sparkle(0.55, 12, 1800, 7000, 782, 0.06, lambda u: 1 - u), 0.35)])


def copy_return():
    # COPY t = 52..60: time flows again (reverse air rising, a soft release)
    n = int(0.45 * SR)
    rev = S.whoosh(0.45, 4000, 500, 791)[::-1] * np.linspace(0.2, 1, n)
    return room(P(0.8, [(0.0, rev, 0.6), (0.38, thump(0.3, 70, 140, 0.08, 792, 0.1), 0.35),
                        (0.4, sparkle(0.4, 8, 1500, 5000, 793, 0.06, lambda u: 1 - u), 0.3)]), 0.6, 0.2, 794)


# name -> (recipe, loudness class)
CLASS = {"frequent": -21.0, "detail": -20.0, "ability": -18.0, "act": -16.5, "signature": -15.0}
RECIPES = {
    "wand_shot": (wand_shot, "frequent"), "spark_hit": (spark_hit, "frequent"), "wand_reload": (wand_reload, "detail"),
    "bolt_cast": (bolt_cast, "ability"), "bolt_snap": (bolt_snap, "ability"), "bolt_hit": (bolt_hit, "ability"),
    "assistant_appear": (assistant_appear, "ability"), "swap": (swap, "ability"), "assistant_leave": (assistant_leave, "detail"),
    "hex_cast": (hex_cast, "ability"), "hex_poof": (hex_poof, "ability"), "hex_end": (hex_end, "detail"),
    "snap": (snap, "ability"), "decoy_reveal": (decoy_reveal, "ability"), "tada": (tada, "act"), "refuse": (refuse, "detail"),
    "ovation": (ovation, "act"), "ult_capture": (ult_capture, "signature"), "ult_card": (ult_card, "act"),
    "act_drumroll": (act_drumroll, "act"), "act_footlights": (act_footlights, "detail"), "act_beam": (act_beam, "act"),
    "act_beam_charge": (act_beam_charge, "act"), "act_beam_burst": (act_beam_burst, "signature"),
    "act_hat_spin": (act_hat_spin, "detail"), "act_nova_whirl": (act_nova_whirl, "act"), "act_nova_burst": (act_nova_burst, "signature"),
    "act_cut_saw": (act_cut_saw, "act"), "act_saw_rasp": (act_saw_rasp, "act"), "act_cut_slash": (act_cut_slash, "signature"),
    "card_hit": (card_hit, "frequent"), "act_card_fan": (act_card_fan, "detail"), "act_card_volley": (act_card_volley, "act"),
    "act_card_finale": (act_card_finale, "signature"), "act_curtain": (act_curtain, "act"), "act_curtain_rustle": (act_curtain_rustle, "detail"),
    "act_curtain_open": (act_curtain_open, "signature"), "act_chest": (act_chest, "act"), "act_sword": (act_sword, "act"),
    "act_chest_rattle": (act_chest_rattle, "detail"), "act_bow": (act_bow, "act"), "copy_sting": (copy_sting, "signature"),
    "copy_unfold": (copy_unfold, "detail"), "copy_fold": (copy_fold, "detail"), "copy_return": (copy_return, "detail"),
}


# ------------------------------------------------------------------ mastering
def active_rms(y):
    w = int(0.02 * SR)
    fr = np.sqrt(np.convolve(y * y, np.ones(w) / w, "valid")[::w // 2] + 1e-12)
    keep = fr > fr.max() * db(-30)
    return float(np.sqrt(np.mean(fr[keep] ** 2)))


def tail_db(y):
    w = int(0.01 * SR)
    fr = np.sqrt(np.convolve(y * y, np.ones(w) / w, "valid")[::w // 2] + 1e-12)
    return 20 * np.log10(fr[-2] / fr.max() + 1e-12)


def master(y, cls):
    y = np.asarray(y, dtype=np.float64)
    y = S.highpass(y, 35)
    y -= np.mean(y)
    # trim trailing near-silence, then a raised-cosine release over the last 18 % (80..350 ms)
    env = np.abs(y)
    last = len(y) - np.argmax(env[::-1] > env.max() * db(-54))
    y = y[: max(int(0.05 * SR), min(len(y), last + int(0.02 * SR)))]
    y = release_tail(y, min(0.35, max(0.08, 0.18 * len(y) / SR)))
    y[: int(0.002 * SR)] *= np.linspace(0, 1, int(0.002 * SR))
    y = y * (db(CLASS[cls]) / (active_rms(y) or 1.0))
    ceiling = db(-1.0)
    over = np.abs(y) > ceiling * 0.7
    if over.any():  # soft knee above -4 dBFS, hard ceiling at -1 dBFS
        k = ceiling * 0.7
        s = np.sign(y[over])
        y[over] = s * (k + (ceiling - k) * np.tanh((np.abs(y[over]) - k) / (ceiling - k)))
    return y.astype(np.float32)


def render(name):
    fn, cls = RECIPES[name]
    return master(fn(), cls)


OWN_SOUNDS_JSON = os.path.join(S.ROOT, "viltrumitecore/src/main/resources/assets/viltrumitecore_sinclair/sounds.json")


def update_sounds_json():
    """Pass-1 events stay in the shared sounds.json (unchanged: same names, files replaced in place); pass-2 events
    are written to Sinclair's own namespace file (SinclairSounds.OWN_SOUNDS) so the 72 KB shared file is not edited."""
    with open(SOUNDS_JSON, encoding="utf-8") as fh:
        shared = json.load(fh)
    own = {}
    for name in RECIPES:
        if "sinclair." + name in shared:
            continue
        own["sinclair." + name] = {
            "category": "player",
            "subtitle": "subtitles.viltrumitecore.sinclair_" + name,
            "sounds": [{"name": "viltrumitecore:sinclair/" + name, "attenuation_distance": 32}],
        }
    with open(OWN_SOUNDS_JSON, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(own, indent=2, ensure_ascii=False) + "\n")
    missing = [k for k in shared if k.startswith("sinclair.") and k[len("sinclair."):] not in RECIPES]
    if missing:
        raise SystemExit("shared sounds.json has events without a recipe: %s" % missing)


def audit():
    print("%-20s %5s %6s %7s %7s" % ("name", "dur", "peak", "rms", "tail"))
    for path in sorted(glob.glob(os.path.join(OUT, "*.ogg.b64"))):
        with tempfile.NamedTemporaryFile(suffix=".ogg") as fh:
            fh.write(base64.b64decode("".join(open(path).read().split())))
            fh.flush()
            y = S.decode(fh.name).astype(np.float64)
        print("%-20s %5.2f %6.1f %7.1f %7.1f" % (os.path.basename(path)[:-8], len(y) / SR, 20 * np.log10(np.abs(y).max()),
                                                 20 * np.log10(active_rms(y)), tail_db(y)))


def main():
    global KROOT
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("kenney", nargs="?")
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--wav", help="also write plain .ogg files here for listening")
    ap.add_argument("--audit", action="store_true")
    args = ap.parse_args()
    if args.audit:
        audit()
        return 0
    if not args.kenney:
        ap.error("KENNEY_ROOT is required")
    KROOT = args.kenney
    names = args.only or list(RECIPES)
    with tempfile.TemporaryDirectory() as tmp:
        for name in names:
            pcm = render(name)
            ogg = os.path.join(tmp, name + ".ogg")
            S.encode(pcm, ogg)
            with open(ogg, "rb") as fh:
                data = fh.read()
            os.makedirs(OUT, exist_ok=True)
            with open(os.path.join(OUT, name + ".ogg.b64"), "wb") as fh:
                fh.write(base64.encodebytes(data))
            if args.wav:
                os.makedirs(args.wav, exist_ok=True)
                with open(os.path.join(args.wav, name + ".ogg"), "wb") as fh:
                    fh.write(data)
            print("%-20s %5.2fs %6d bytes  rms %5.1f  tail %6.1f" % (name, len(pcm) / SR, len(data),
                                                                     20 * np.log10(active_rms(pcm.astype(np.float64))), tail_db(pcm)))
    if not args.only:
        update_sounds_json()
    return 0


if __name__ == "__main__":
    sys.exit(main())
