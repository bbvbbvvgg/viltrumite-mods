#!/usr/bin/env python3
"""Saw Sphere (Reinhard item 19): the hum, grind, finale and shatter cues.

Fully original, deterministic synthesis (seeded numpy noise and oscillators, no recording, no download), CC0-1.0.
Four mono 44.1 kHz cues; the sword swings reuse the existing cinematic sweep file and are not made here.

  saw_hum      4.0 s seamless loop (every partial completes whole cycles in the loop, the noise is circular):
               a 55 Hz drone with detuned harmonics (slow beating), a band-passed whirr amplitude-modulated at
               the disc rate and a faint 1.76 kHz glass shimmer. The client rises it in pitch (0.6 -> 1.0) and
               volume while the shell grows, so the file itself is the steady top of that rise.
  saw_grind    0.55 s metal-on-stone bite of one disc pass: a toothed noise burst (2-7 kHz, 95 Hz tooth rate),
               four inharmonic metal partials and a short rock crunch below 600 Hz. Rate-limited by the client.
  saw_finale   3.0 s accent of the last great swing: a sub thump, a rising-then-falling air sweep and a long
               ringing metal chord (inharmonic, slow decay).
  saw_shatter  3.6 s crystalline break-up of the shell: a sharp crack, a dense cloud of short glass grains
               (2-9 kHz) thinning out, a low thump and a shimmering tail.

Each cue is DC-free, faded (except the loop), set to its 100 ms momentary max target (BS.1770 K-weighting, the meter
of tools/sfx/reinhard_strikes.py) under TRUE_PEAK and packaged through the sound-design helper (Vorbis q5, .ogg.b64).

    python3 tools/sfx/reinhard_saw_sphere.py            # synthesize and package all four
    python3 tools/sfx/reinhard_saw_sphere.py --check    # re-synthesize and compare with the committed assets
    python3 tools/sfx/reinhard_saw_sphere.py --no-package --out /tmp/x   # WAV only

Measured numbers only: the sandbox that wrote this has no audio playback, so the cues were not auditioned.
"""
import argparse
import math
import os
import subprocess
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reinhard_strikes as meter  # noqa: E402  (shared K-weighted meter, limiter and WAV writer)

ROOT = meter.ROOT
HELPER = meter.HELPER
SR = meter.SR
SEED = 20261027
TRUE_PEAK = -1.0
MAX_LIMIT = 3.0
# name -> (length s, 100 ms momentary max target LUFS, loop)
CUES = {
    "saw_hum": (4.0, -16.0, True),
    "saw_grind": (0.55, -14.0, False),
    "saw_finale": (3.0, -9.0, False),
    "saw_shatter": (3.6, -10.0, False),
}


def butter_mag(freq, lo=None, hi=None, order=2):
    mag = np.ones_like(freq)
    f = np.maximum(freq, 1e-6)
    if lo:
        mag /= np.sqrt(1 + (lo / f) ** (2 * order))
    if hi:
        mag /= np.sqrt(1 + (f / hi) ** (2 * order))
    return mag


def filt(x, lo=None, hi=None, order=2, circular=False):
    """Zero-phase Butterworth magnitude in the FFT domain; circular keeps a loop seamless."""
    n = len(x)
    size = n if circular else 2 * n
    spec = np.fft.rfft(x, size)
    spec *= butter_mag(np.fft.rfftfreq(size, 1 / SR), lo, hi, order)
    return np.fft.irfft(spec, size)[:n]


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def env(t, attack, decay):
    return (1 - np.exp(-t / attack)) * np.exp(-t / decay)


def db(v):
    return 10 ** (v / 20)


def loop_freq(f, length):
    """Nearest frequency that completes a whole number of cycles in the loop."""
    return max(1, round(f * length)) / length


def hum(rng, length):
    n = int(round(length * SR))
    t = np.arange(n) / SR
    out = np.zeros(n)
    for f, gain in ((55, 0), (110, -4), (110.5, -7), (165, -10), (220, -13), (275.25, -18), (330, -20)):
        fl = loop_freq(f, length)
        out += np.sin(2 * math.pi * fl * t + rng.uniform(0, 2 * math.pi)) * db(gain)
    out = np.tanh(1.6 * norm(out)) / math.tanh(1.6)
    # whirr: circular noise in 300-1400 Hz, pulsed at a whole-cycle disc rate (about 7 Hz)
    whirr = norm(filt(rng.standard_normal(n), 300, 1400, circular=True))
    rate = loop_freq(7.0, length)
    whirr *= 0.55 + 0.45 * np.sin(2 * math.pi * rate * t) ** 2
    shimmer = np.zeros(n)
    for f in (1760, 2640.5):
        fl = loop_freq(f, length)
        vib = loop_freq(0.5, length)
        shimmer += np.sin(2 * math.pi * fl * t + 0.6 * np.sin(2 * math.pi * vib * t))
    breath = 1 + 0.15 * np.sin(2 * math.pi * loop_freq(0.75, length) * t)
    x = (out * db(0) + whirr * db(-14) + norm(shimmer) * db(-30)) * breath
    return x - np.mean(x)


def metal(t, partials, decay):
    x = np.zeros_like(t)
    for f, gain, d in partials:
        x += np.sin(2 * math.pi * f * t) * db(gain) * np.exp(-t / (decay * d))
    return x


def grind(rng, length):
    n = int(round(length * SR))
    t = np.arange(n) / SR
    teeth = 0.35 + 0.65 * np.abs(np.sin(math.pi * 95 * t)) ** 3
    burst = norm(filt(rng.standard_normal(n), 2000, 7000)) * teeth * env(t, 0.004, 0.16)
    ring = norm(metal(t, ((1873, 0, 1.0), (2957, -3, 0.8), (4211, -6, 0.6), (5633, -9, 0.45)), 0.12))
    ring *= 1 - np.exp(-t / 0.002)
    crunch = norm(filt(rng.standard_normal(n), 150, 600)) * env(t, 0.002, 0.05)
    return burst + ring * db(-8) + crunch * db(-6)


def finale(rng, length):
    n = int(round(length * SR))
    t = np.arange(n) / SR
    freq = 34 + 60 * np.exp(-t / 0.18)
    thump = np.tanh(2.0 * np.sin(2 * math.pi * np.cumsum(freq) / SR)) * env(t, 0.003, 0.55)
    # air sweep: noise whose band centre rises to the swing (0.25 s) and falls away
    centre = 600 + 2600 * np.exp(-((t - 0.25) / 0.22) ** 2)
    noise = rng.standard_normal(n)
    sweep = np.zeros(n)
    for lo_hz in (500, 1100, 2200, 3600):
        band = filt(noise, lo_hz, lo_hz * 1.8)
        sweep += band * np.exp(-((np.log(lo_hz * 1.35) - np.log(centre)) / 0.5) ** 2)
    sweep = norm(sweep) * np.clip(t / 0.15, 0, 1) * np.exp(-np.maximum(t - 0.3, 0) / 0.35)
    ring = norm(metal(t, ((392, 0, 1.0), (587.9, -2, 0.9), (982, -5, 0.7), (1471, -8, 0.55), (2339, -12, 0.4)), 1.1))
    ring *= np.clip((t - 0.22) / 0.01, 0, 1)
    return thump * db(-2) + sweep * db(-3) + ring * db(-7)


def shatter(rng, length):
    n = int(round(length * SR))
    t = np.arange(n) / SR
    crack = norm(filt(rng.standard_normal(n), 1500, None)) * np.exp(-t / 0.012)
    thump = np.sin(2 * math.pi * np.cumsum(50 + 40 * np.exp(-t / 0.1)) / SR) * env(t, 0.003, 0.3)
    grains = np.zeros(n)
    at = 0.0
    while at < 2.6:
        at += rng.exponential(1 / (260 * math.exp(-at / 0.45) + 6))
        i = int(at * SR)
        size = int(rng.uniform(0.02, 0.09) * SR)
        if i + size >= n:
            break
        gt = np.arange(size) / SR
        f = rng.uniform(2000, 9000)
        g = np.sin(2 * math.pi * f * gt) + 0.5 * np.sin(2 * math.pi * f * rng.uniform(1.4, 2.3) * gt)
        grains[i:i + size] += g * env(gt, 0.0005, rng.uniform(0.008, 0.03)) * rng.uniform(0.3, 1.0) * math.exp(-at / 0.9)
    grains = norm(grains)
    tail = np.zeros(n)
    for f in (2093, 3136, 4186.5):
        tail += np.sin(2 * math.pi * f * t + 0.4 * np.sin(2 * math.pi * 5.1 * t)) * np.exp(-t / 1.1)
    tail = norm(tail) * np.clip((t - 0.05) / 0.3, 0, 1)
    return crack * db(-2) + thump * db(-6) + grains * db(0) + tail * db(-18)


SYNTH = {"saw_hum": hum, "saw_grind": grind, "saw_finale": finale, "saw_shatter": shatter}


def synth(name):
    length, _, loop = CUES[name]
    rng = np.random.default_rng(SEED + sorted(CUES).index(name))
    x = SYNTH[name](rng, length)
    if loop:
        return x
    x = filt(x - np.mean(x), 25, None)
    n = len(x)
    fade_in = int(0.001 * SR)
    x[:fade_in] *= np.linspace(0, 1, fade_in)
    fade_out = int(min(0.6, length * 0.3) * SR)
    x[n - fade_out:] *= np.cos(np.linspace(0, math.pi / 2, fade_out)) ** 2
    return x


def master(name, x):
    _, target, loop = CUES[name]
    gain = target - meter.momentary_max(x)
    over = meter.true_peak(x) + gain - TRUE_PEAK
    if loop and over > 0:
        gain -= over  # never limit the loop: the limiter's release would mark the seam
    elif over > MAX_LIMIT:
        gain -= over - MAX_LIMIT
    y = x * 10 ** (gain / 20)
    return y if loop else meter.limit(y, TRUE_PEAK)


def seam(x):
    """Largest step across the loop seam relative to the largest step inside the loop."""
    inner = np.max(np.abs(np.diff(x)))
    return abs(x[0] - x[-1]) / (inner + 1e-12)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "mix", "reinhard"))
    parser.add_argument("--check", action="store_true", help="re-synthesize and compare with the committed assets")
    parser.add_argument("--no-package", action="store_true", help="write the WAVs only")
    parser.add_argument("names", nargs="*", default=sorted(CUES))
    args = parser.parse_args()
    failed = False
    for name in args.names:
        x = master(name, synth(name))
        if args.check:
            y = meter.decode_asset(name)
            k = min(len(x), len(y))
            corr = float(np.dot(x[:k], y[:k]) / (np.linalg.norm(x[:k]) * np.linalg.norm(y[:k]) + 1e-15))
            ok = abs(len(x) - len(y)) <= 0.03 * SR and corr >= 0.95
            failed |= not ok
            print(f"{name}: {'ok' if ok else 'MISMATCH'} correlation {corr:.4f}, {len(y) / SR:.3f} s")
            continue
        os.makedirs(args.out, exist_ok=True)
        wav = os.path.join(args.out, name + ".wav")
        meter.write_wav(x, wav)
        extra = f", seam {seam(x):.2f}" if CUES[name][2] else ""
        print(f"{name}: {len(x) / SR:.3f} s, M100 {meter.momentary_max(x):.1f} LUFS, TP {meter.true_peak(x):.1f} dBTP{extra} -> {wav}")
        if not args.no_package:
            subprocess.run([sys.executable, HELPER, "package", "--input", wav, "--module", "core", "--name", "reinhard/" + name,
                            "--event", "reinhard_" + name, "--own", "--replace"], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
