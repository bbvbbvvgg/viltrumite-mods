#!/usr/bin/env python3
"""Meteor Rain 2.0 sounds: CC0 Freesound recordings found via Openverse, layered with original synthesis.

Sources are fetched with the sound-design skill helper (Freesound preview MP3s, never committed):

    python3 .agents/skills/sound-design/scripts/openverse_sfx.py download <openverse-uuid>

into ~/.cache/viltrumite-sfx/work/raw/<uuid>.mp3 with the verbatim attribution. Every source is CC0 1.0
(see tools/assets/meteor_rain_sources.md). Run from the repository root:

    python3 tools/sfx/meteor_rain.py              # mix every cue and package it (refuses to overwrite)
    python3 tools/sfx/meteor_rain.py --replace    # rebuild the committed assets
    python3 tools/sfx/meteor_rain.py --only roar --wav /tmp/mr   # mix one cue to WAV for review

Everything is seeded, so a rerun gives the same mix. Loops (approach, roar, fire_roar) are built in the
frequency domain over exactly one loop length (magnitude envelope, seeded random phase, inverse FFT) with
every modulation periodic in the loop, so they are seamless by construction. Each cue is normalised on its
loudest 100 ms RMS to a per-class target and soft-limited under -1 dBFS. Packaging goes through the skill
helper (mono 44.1 kHz Vorbis q5, .ogg.b64). Cues are kept short because a push request is capped at 64 KB.

No listening test is implied by this script: levels and spectra are measured, not auditioned.
"""
import argparse
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HELPER = os.path.join(ROOT, ".agents", "skills", "sound-design", "scripts", "openverse_sfx.py")
RAW = os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "raw")
SR = 44100

# key -> (Openverse UUID, Freesound id, creator, original title). All CC0 1.0.
SOURCES = {
    "explosion_debris": ("8c54c9c2-618e-41fb-82b0-0d1a20bf0c23", 367622, "FxKid2", "Explosion with Debris.wav"),
    "far_explosion": ("023d25d5-a1e6-475d-a148-db56cd73636d", 264031, "Logicogonist", "low mid afar explosion 1.wav"),
    "fire_crackling": ("166449d3-2ad9-4f48-bbc1-e15887464a56", 181563, "kingsrow", "Fire Crackling 01.wav"),
    "fire_roaring": ("74ceb541-e106-4e94-81eb-7eef6adae23b", 636178, "jamesdrake89", "Fire - Crackling, Spitting, Roaring"),
}

# class -> loudest-100 ms RMS target dBFS
TARGET = {"loop_far": -20.0, "loop_near": -17.0, "whoosh": -12.0, "impact": -9.0, "far": -12.0, "shock": -11.0,
          "debris": -15.0, "rubble": -17.0, "crackle": -14.0, "fire_loop": -18.0, "flare": -13.0, "brand": -15.0}

_cache = {}


def source(key):
    if key not in _cache:
        uuid = SOURCES[key][0]
        path = os.path.join(RAW, uuid + ".mp3")
        if not os.path.isfile(path):
            sys.exit(f"Missing source {key}; run: python3 {os.path.relpath(HELPER, ROOT)} download {uuid}")
        out = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"],
                             capture_output=True, check=True).stdout
        _cache[key] = np.frombuffer(out, np.int16).astype(np.float64) / 32768.0
    return _cache[key]


def seg(key, start, length, pitch=1.0):
    """`length` seconds of a source from `start`, varispeed by `pitch` (0.8 = lower and longer)."""
    x = source(key)
    n = int(length * SR)
    pos = start * SR + np.arange(n) * pitch
    pos = pos[pos < len(x) - 1]
    i = pos.astype(int)
    f = pos - i
    y = x[i] * (1 - f) + x[i + 1] * f
    return np.pad(y, (0, n - len(y)))


def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def band(x, lo=None, hi=None, order=2.0):
    """Zero-phase FFT filter with smooth Butterworth-like skirts."""
    n = len(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    h = np.ones_like(f)
    if lo:
        h *= 1.0 / np.sqrt(1.0 + (lo / np.maximum(f, 1e-3)) ** (2 * order))
    if hi:
        h *= 1.0 / np.sqrt(1.0 + (f / hi) ** (2 * order))
    return np.fft.irfft(np.fft.rfft(x) * h, n)


def sweep(x, lo_curve, hi_curve, frame=2048):
    """Time-varying band-pass (overlap-add STFT); curves are callables of time 0..1 -> Hz."""
    hop = frame // 4
    win = np.hanning(frame)
    out = np.zeros(len(x) + frame)
    norm = np.zeros(len(x) + frame)
    xp = np.pad(x, (0, frame))
    f = np.fft.rfftfreq(frame, 1.0 / SR)
    for s in range(0, len(x), hop):
        t = s / max(1, len(x))
        lo, hi = lo_curve(t), hi_curve(t)
        h = 1.0 / np.sqrt(1.0 + (lo / np.maximum(f, 1e-3)) ** 4) / np.sqrt(1.0 + (f / hi) ** 4)
        spec = np.fft.rfft(xp[s:s + frame] * win) * h
        out[s:s + frame] += np.fft.irfft(spec, frame) * win
        norm[s:s + frame] += win * win
    return (out / np.maximum(norm, 1e-6))[:len(x)]


def noise(seconds, rng, color=0.0):
    """Gaussian noise with a 1/f^color power slope (0 white, 1 pink, 2 brown)."""
    n = int(seconds * SR)
    spec = rng.normal(size=n // 2 + 1) + 1j * rng.normal(size=n // 2 + 1)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    spec *= 1.0 / np.maximum(f, 20.0) ** (color / 2.0)
    y = np.fft.irfft(spec, n)
    return y / (np.std(y) + 1e-12)


def freeze(magnitude_of, seconds, rng):
    """Seamless loop: a magnitude envelope (callable of Hz) with seeded random phase over exactly the loop length."""
    n = int(seconds * SR)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    spec = magnitude_of(f) * np.exp(1j * rng.uniform(0, 2 * np.pi, len(f)))
    spec[0] = 0
    y = np.fft.irfft(spec, n)
    return y / (np.std(y) + 1e-12)


def spectrum_of(x, smooth=60.0):
    """Smoothed magnitude spectrum of a recording, as a callable of Hz (for freezing)."""
    s = np.abs(np.fft.rfft(x * np.hanning(len(x))))
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    k = max(1, int(smooth / (f[1] - f[0])))
    s = np.convolve(s, np.ones(k) / k, mode="same")
    return lambda q: np.interp(q, f, s)


def env(seconds, attack, decay, hold=0.0, curve=4.0):
    t = t_axis(seconds)
    e = np.clip(t / max(attack, 1e-4), 0, 1)
    tail = np.clip((t - attack - hold) / max(decay, 1e-4), 0, None)
    return e * np.exp(-curve * tail)


def place(dst, src, at, gain=1.0):
    i = int(at * SR)
    if i >= len(dst):
        return dst
    m = min(len(src), len(dst) - i)
    dst[i:i + m] += src[:m] * gain
    return dst


def db(x):
    return 10.0 ** (x / 20.0)


def grains(seconds, rng, count, lo, hi, decay=(0.01, 0.05), density=lambda t: 1.0, gain=(0.3, 1.0)):
    """Rock / debris ticks: short band-passed noise bursts placed by a density curve."""
    out = np.zeros(int(seconds * SR))
    placed = 0
    while placed < count:
        t = rng.uniform(0, seconds * 0.95)
        if rng.uniform() > density(t / seconds):
            continue
        d = rng.uniform(*decay)
        g = noise(d * 6, rng) * np.exp(-t_axis(d * 6) / d)
        g = band(g, rng.uniform(lo, (lo + hi) / 2), rng.uniform((lo + hi) / 2, hi))
        place(out, g, t, rng.uniform(*gain))
        placed += 1
    return out


def fade(x, fin=0.003, fout=0.25):
    n = len(x)
    a = int(fin * SR)
    b = int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[n - b:] *= np.linspace(1, 0, b) ** 2
    return x


def finish(x, cls, loop=False):
    """Normalise on the loudest 100 ms RMS, then soft-limit under -1 dBFS (tanh knee above -4 dBFS)."""
    w = int(0.1 * SR)
    rms = max(np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, max(1, len(x) - w), w // 2))
    x = x * db(TARGET[cls]) / max(rms, 1e-9)
    knee = db(-4.0)
    ceiling = db(-1.0)
    over = np.abs(x) > knee
    x[over] = np.sign(x[over]) * (knee + (ceiling - knee) * np.tanh((np.abs(x[over]) - knee) / (ceiling - knee)))
    if not loop:
        x = fade(x, 0.002, 0.08)
    return x


# ------------------------------------------------------------------ cues

def approach(rng):
    """Far meteor: a deep rolling rumble with a faint tearing hiss, slow periodic swell (loop 3 s)."""
    L = 3.0
    t = t_axis(L)
    low = freeze(lambda f: 1.0 / (1.0 + (f / 90.0) ** 3) / np.maximum(f, 25.0) ** 0.5, L, rng)
    mid = freeze(lambda f: np.exp(-((np.log(np.maximum(f, 1)) - np.log(260)) ** 2) / 0.5), L, rng)
    hiss = freeze(lambda f: np.exp(-((np.log(np.maximum(f, 1)) - np.log(1600)) ** 2) / 0.4), L, rng)
    swell = 0.75 + 0.25 * np.sin(2 * np.pi * 1 * t / L) + 0.08 * np.sin(2 * np.pi * 5 * t / L + 1.0)
    flutter = 1.0 + 0.15 * np.sin(2 * np.pi * 23 * t / L)
    return finish(low * swell + 0.35 * mid * swell * flutter + 0.06 * hiss * swell, "loop_far", loop=True)


def roar(rng):
    """Near meteor: a burning roar with sputter, built on the frozen spectrum of the roaring fire (loop 3 s)."""
    L = 3.0
    t = t_axis(L)
    fire = spectrum_of(seg("fire_roaring", 166.0, 4.0))
    burn = freeze(lambda f: fire(f) * (1.0 / (1.0 + (f / 5500.0) ** 4)), L, rng)
    body = freeze(lambda f: 1.0 / (1.0 + (f / 420.0) ** 2) / (1.0 + (60.0 / np.maximum(f, 1)) ** 4), L, rng)
    sub = freeze(lambda f: 1.0 / (1.0 + (f / 70.0) ** 4), L, rng)
    sputter = 0.8 + 0.2 * np.sin(2 * np.pi * 31 * t / L) * np.sin(2 * np.pi * 4 * t / L + 0.4)
    return finish(body * sputter + 0.6 * sub + 0.32 * burn * (0.7 + 0.3 * np.sin(2 * np.pi * 7 * t / L)), "loop_near", loop=True)


def whoosh(rng, peak, top):
    """Pass-by: noise through a band sweeping up to `top` Hz at `peak` s, then down; a low push at the peak."""
    L = 1.7
    t = t_axis(L)
    x = noise(L, rng, 1.0)
    p = peak / L
    rise = lambda q: np.exp(-((q - p) ** 2) / 0.03)
    y = sweep(x, lambda q: 120 + 500 * rise(q), lambda q: 500 + top * rise(q))
    e = np.where(t < peak, (t / peak) ** 2.5, np.exp(-(t - peak) / 0.18))
    push = np.sin(2 * np.pi * (55 - 15 * np.clip(t - peak, 0, 1)) * t) * np.exp(-np.abs(t - peak) / 0.12)
    return finish(y * e + 0.5 * push, "whoosh")


def impact_near(rng, pitch, start):
    """Near impact: a broadband crack, the recorded explosion body, a dropping sub boom (3 s)."""
    L = 3.0
    t = t_axis(L)
    crack = band(noise(0.06, rng), 900, 9000) * env(0.06, 0.0008, 0.015)
    body = seg("explosion_debris", start, L, pitch)
    boom = np.sin(2 * np.pi * np.cumsum(70 - 38 * np.clip(t / 0.9, 0, 1)) / SR) * env(L, 0.004, 0.6, 0.05, 3.0)
    rumble = band(noise(L, rng, 2.0), 30, 160) * env(L, 0.02, 1.2, 0.1, 3.0)
    x = body + 0.45 * boom + 0.3 * rumble
    place(x, crack, 0.0, 0.9)
    return finish(fade(x, 0.001, 0.6), "impact")


def impact_far(rng, pitch, delay):
    """Far impact: the distant recording darkened, a sub swell and two rolling echoes (3.5 s)."""
    L = 3.5
    x = band(seg("far_explosion", 0.0, L, pitch), 35, 1400)
    echo = band(x, 40, 700)
    y = x.copy()
    place(y, echo, delay, 0.45)
    place(y, echo, delay * 2.4, 0.25)
    y += 0.35 * band(noise(L, rng, 2.0), 25, 110) * env(L, 0.08, 1.4, 0.2, 3.0)
    return finish(fade(y, 0.004, 0.8), "far")


def shockwave(rng):
    """Shockwave: a dropping sub thump with a soft-saturated harmonic so small speakers carry it (1.6 s)."""
    L = 1.6
    t = t_axis(L)
    f = 48 - 20 * np.clip(t / 0.5, 0, 1)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(L, 0.008, 0.45, 0.04, 2.6)
    s = np.tanh(2.2 * s) / np.tanh(2.2)
    air = band(noise(L, rng, 1.0), 60, 400) * env(L, 0.003, 0.25, 0.0, 3.0)
    return finish(s + 0.25 * air, "shock")


def debris(rng, start):
    """Debris: the recorded debris tail with dense then thinning rock ticks (2.4 s)."""
    L = 2.4
    rec = band(seg("explosion_debris", start, L), 250, 9000) * env(L, 0.05, 1.5, 0.3, 2.0)
    ticks = grains(L, rng, 70, 600, 5000, (0.006, 0.03), lambda q: np.exp(-2.5 * q), (0.15, 0.8))
    thuds = grains(L, rng, 14, 120, 700, (0.02, 0.07), lambda q: np.exp(-1.5 * q), (0.3, 1.0))
    return finish(rec + 0.6 * ticks + 0.5 * thuds, "debris")


def rubble(rng):
    """Rubble: low crumbling clatter that slides to rest over a gritty bed (2.6 s)."""
    L = 2.6
    bed = band(noise(L, rng, 1.0), 150, 1800) * env(L, 0.15, 1.6, 0.4, 2.2)
    clatter = grains(L, rng, 60, 150, 1400, (0.015, 0.06), lambda q: 1.0 - 0.7 * q, (0.2, 1.0))
    return finish(0.35 * bed + clatter, "rubble")


def crackles(rng):
    """Single crackles: the strongest transients of the crackling recordings, one per variant (0.5 s)."""
    x = np.concatenate([seg("fire_crackling", 28.0, 5.0), seg("fire_roaring", 120.0, 30.0)])
    w = int(0.01 * SR)
    energy = np.convolve(x ** 2, np.ones(w) / w, mode="same")
    picks = []
    order = np.argsort(energy)[::-1]
    for i in order:
        if all(abs(i - p) > SR for p in picks) and i > int(0.05 * SR) and i < len(x) - SR:
            picks.append(i)
        if len(picks) == 3:
            break
    out = []
    for p in picks:
        c = x[p - int(0.03 * SR):p + int(0.47 * SR)].copy()
        c = band(c, 300, 12000) * env(0.5, 0.002, 0.25, 0.03, 3.5)[:len(c)]
        out.append(finish(c, "crackle"))
    return out


def fire_roar(rng):
    """Big fire: a deep turbulent roar, the frozen crackle spectrum on top, a few placed pops (loop 3 s)."""
    L = 3.0
    t = t_axis(L)
    fire = spectrum_of(seg("fire_roaring", 166.0, 4.0))
    hiss = freeze(lambda f: fire(f) * (1.0 / (1.0 + (f / 7000.0) ** 4)), L, rng)
    body = freeze(lambda f: 1.0 / (1.0 + (f / 300.0) ** 2) / (1.0 + (45.0 / np.maximum(f, 1)) ** 4), L, rng)
    gust = 0.75 + 0.18 * np.sin(2 * np.pi * 2 * t / L) + 0.07 * np.sin(2 * np.pi * 9 * t / L + 2.0)
    x = body * gust + 0.3 * hiss * (0.8 + 0.2 * np.sin(2 * np.pi * 13 * t / L))
    pops = crackles(np.random.default_rng(7))
    for i, at in enumerate((0.4, 1.35, 2.1)):
        place(x, pops[i % len(pops)] * 6.0, at, 1.0)
    return finish(x, "fire_loop", loop=True)


def fire_flare(rng, top):
    """Flare: a soft 'whoomp' as the fire catches, the band opening to `top` Hz, with a few pops (1.3 s)."""
    L = 1.3
    t = t_axis(L)
    x = noise(L, rng, 1.0)
    y = sweep(x, lambda q: 80 + 120 * q, lambda q: 300 + top * np.exp(-((q - 0.12) ** 2) / 0.02))
    e = env(L, 0.07, 0.8, 0.05, 3.0)
    breath = np.sin(2 * np.pi * 75 * t) * env(L, 0.05, 0.3, 0.0, 3.0)
    x = y * e + 0.3 * breath
    pops = crackles(np.random.default_rng(11))
    place(x, pops[0] * 2.0, 0.25, 1.0)
    return finish(x, "flare")


def brand(rng):
    """Brand: a small flaming swish rising and falling with crackle (1.0 s)."""
    L = 1.0
    x = noise(L, rng, 1.0)
    y = sweep(x, lambda q: 300 + 200 * np.sin(np.pi * q), lambda q: 900 + 1400 * np.sin(np.pi * q))
    e = np.sin(np.pi * np.clip(t_axis(L) / 0.85, 0, 1)) ** 1.5
    pops = crackles(np.random.default_rng(13))
    z = y * e
    place(z, pops[1] * 1.5, 0.35, 1.0)
    return finish(z, "brand")


# name -> (builder(rng) -> signal, sources used)
CUES = {
    "approach": (approach, []),
    "roar": (roar, ["fire_roaring"]),
    "whoosh_1": (lambda r: whoosh(r, 1.0, 2600), []),
    "whoosh_2": (lambda r: whoosh(r, 1.15, 1900), []),
    "impact_near_1": (lambda r: impact_near(r, 1.0, 0.0), ["explosion_debris"]),
    "impact_near_2": (lambda r: impact_near(r, 0.88, 0.0), ["explosion_debris"]),
    "impact_far_1": (lambda r: impact_far(r, 0.92, 0.32), ["far_explosion"]),
    "impact_far_2": (lambda r: impact_far(r, 0.8, 0.45), ["far_explosion"]),
    "shockwave": (shockwave, []),
    "debris_1": (lambda r: debris(r, 2.4), ["explosion_debris"]),
    "debris_2": (lambda r: debris(r, 2.7), ["explosion_debris"]),
    "rubble": (rubble, []),
    "fire_crackle_1": (lambda r: crackles(r)[0], ["fire_crackling", "fire_roaring"]),
    "fire_crackle_2": (lambda r: crackles(r)[1], ["fire_crackling", "fire_roaring"]),
    "fire_crackle_3": (lambda r: crackles(r)[2], ["fire_crackling", "fire_roaring"]),
    "fire_roar": (fire_roar, ["fire_roaring"]),
    "fire_flare_1": (lambda r: fire_flare(r, 2400), ["fire_crackling", "fire_roaring"]),
    "fire_flare_2": (lambda r: fire_flare(r, 1600), ["fire_crackling", "fire_roaring"]),
    "brand": (brand, ["fire_crackling", "fire_roaring"]),
}


def write_wav(path, x):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", action="append", default=[])
    ap.add_argument("--wav", help="write WAVs to this folder instead of packaging")
    ap.add_argument("--replace", action="store_true")
    args = ap.parse_args()
    names = args.only or list(CUES)
    for i, name in enumerate(CUES):
        if name not in names:
            continue
        builder, used = CUES[name]
        x = builder(np.random.default_rng(1000 + i))
        if args.wav:
            os.makedirs(args.wav, exist_ok=True)
            write_wav(os.path.join(args.wav, name + ".wav"), x)
            print(name, f"{len(x) / SR:.2f}s", f"peak {20 * np.log10(np.abs(x).max() + 1e-12):.1f} dBFS")
            continue
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, name + ".wav")
            write_wav(path, x)
            cmd = [sys.executable, HELPER, "package", "--input", path, "--module", "core", "--name", "meteor_rain/" + name,
                   "--event", "meteor_rain_" + name, "--repo", ROOT]
            cmd += [a for k in used for a in ("--source-id", SOURCES[k][0])] if used else ["--own"]
            if args.replace:
                cmd.append("--replace")
            subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)
            print("packaged", name)


if __name__ == "__main__":
    main()
