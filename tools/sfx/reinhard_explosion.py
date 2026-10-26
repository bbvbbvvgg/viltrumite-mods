#!/usr/bin/env python3
"""World Cut explosion: the heavy boom under the strike voice on the BLAST of the normal World Cut.

Fully original, deterministic synthesis (seeded numpy noise and oscillators, no recording, no download), CC0-1.0.
One mono 44.1 kHz cue for a positional one-shot, 6.0 s:

  slam     0-0.30 s   band-passed noise burst (200 Hz-6 kHz, 1.5 ms attack, 60 ms decay) and an 8 ms crack
                      above 2 kHz: the attack that reads on small speakers and lands on the voice's first hit.
  boom     sub sweep 98 -> 30 Hz, 4 ms attack, 1.0 s decay, tanh drive so its 60-250 Hz harmonics carry it
                      on speakers without a sub woofer.
  roar     pink noise under a falling low-pass (3.5 kHz -> 250 Hz), high-passed at 110 Hz, 8 ms attack,
                      0.7 s decay, roughened by fast seeded turbulence: the fireball.
  echoes   four low-passed slap-backs of the slam and the early roar (0.17 / 0.38 / 0.75 / 1.30 s, -9 to -21 dB):
                      the size of an open valley, not a room.
  rumble   brown noise below 140 Hz plus a 150-500 Hz grit layer, slow seeded swell, 2.4 s decay: the rolling
                      decay under the voice.
  debris   sparse seeded falling-rock grains (0.4-2.5 kHz) thinning out over 3.5 s, -26 dB.

The sum is DC-free, high-passed at 25 Hz, faded, set to TARGET_M100 (100 ms momentary max, BS.1770 K-weighting,
the meter of tools/sfx/reinhard_strikes.py) under TRUE_PEAK with at most MAX_LIMIT dB of look-ahead limiting, and
packaged through the sound-design skill helper (mono 44.1 kHz Vorbis q5, .ogg.b64).

    python3 tools/sfx/reinhard_explosion.py            # synthesize and package
    python3 tools/sfx/reinhard_explosion.py --check    # re-synthesize and compare with the committed asset
    python3 tools/sfx/reinhard_explosion.py --no-package --out /tmp/x   # WAV only

Measured numbers only: the sandbox that wrote this has no audio playback, so the cue was not auditioned.
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
ASSETS = meter.ASSETS
SR = meter.SR
NAME = "world_cut_explosion"
SEED = 20261026
LENGTH = 6.0
TARGET_M100 = -7.5
TRUE_PEAK = -1.0
MAX_LIMIT = 3.0
# Layer peaks in dB before mastering.
SLAM_DB = 1.0
BOOM_DB = -4.0
ROAR_DB = 1.0
RUMBLE_DB = -7.0
DEBRIS_DB = -26.0
ECHOES = ((0.17, 2500, -9), (0.38, 1500, -13), (0.75, 900, -17), (1.30, 600, -21))


def butter_mag(freq, lo=None, hi=None, order=2):
    """Butterworth magnitude response (zero phase when applied in the FFT domain)."""
    mag = np.ones_like(freq)
    f = np.maximum(freq, 1e-6)
    if lo:
        mag /= np.sqrt(1 + (lo / f) ** (2 * order))
    if hi:
        mag /= np.sqrt(1 + (f / hi) ** (2 * order))
    return mag


def filt(x, lo=None, hi=None, order=2):
    n = len(x)
    spec = np.fft.rfft(x, 2 * n)
    spec *= butter_mag(np.fft.rfftfreq(2 * n, 1 / SR), lo, hi, order)
    return np.fft.irfft(spec, 2 * n)[:n]


def brown(rng, n):
    w = rng.standard_normal(n)
    y = np.empty(n)
    acc = 0.0
    for i in range(n):
        acc = acc * 0.997 + w[i]
        y[i] = acc
    return y / (np.max(np.abs(y)) + 1e-12)


def sweep_lowpass(x, cutoff):
    """Two cascaded one-pole low-passes with a per-sample cutoff (Hz)."""
    a = 1.0 - np.exp(-2 * math.pi * np.asarray(cutoff) / SR)
    y1 = y2 = 0.0
    out = np.empty_like(x)
    for i in range(len(x)):
        y1 += a[i] * (x[i] - y1)
        y2 += a[i] * (y1 - y2)
        out[i] = y2
    return out


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def env(t, attack, decay):
    return (1 - np.exp(-t / attack)) * np.exp(-t / decay)


def pink(rng, n):
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= 1 / np.sqrt(np.maximum(f, 20.0))
    return norm(np.fft.irfft(spec, n))


def slam_layer(rng, n):
    t = np.arange(n) / SR
    burst = norm(filt(rng.standard_normal(n), 200, 6000)) * env(t, 0.0015, 0.06)
    crack = norm(filt(rng.standard_normal(n), 2000, None)) * np.exp(-t / 0.006) * 10 ** (-4 / 20)
    return burst + crack


def synth():
    rng = np.random.default_rng(SEED)
    n = int(round(LENGTH * SR))
    t = np.arange(n) / SR

    slam = np.zeros(n)
    m = int(0.30 * SR)
    slam[:m] = slam_layer(rng, m) * 10 ** (SLAM_DB / 20)

    freq = 30 + 68 * np.exp(-t / 0.32)
    phase = 2 * math.pi * np.cumsum(freq) / SR
    boom = np.tanh(2.2 * np.sin(phase)) / math.tanh(2.2)
    boom = norm(filt(boom * env(t, 0.004, 1.0), None, 420)) * 10 ** (BOOM_DB / 20)

    cutoff = 250 + 3300 * np.exp(-t / 0.45)
    turbulence = 1 + 0.5 * norm(filt(rng.standard_normal(n), None, 40))
    roar = sweep_lowpass(pink(rng, n), cutoff)
    roar = filt(roar, 110, None) * env(t, 0.008, 0.7) * turbulence
    roar = norm(roar) * 10 ** (ROAR_DB / 20)

    early = slam + roar * np.clip((0.5 - t) / 0.1, 0, 1)
    echoes = np.zeros(n)
    for delay, cut, gain in ECHOES:
        at = int(delay * SR)
        echoes[at:] += filt(early, None, cut)[:n - at] * 10 ** (gain / 20)

    swell = 1 + 0.4 * norm(filt(rng.standard_normal(n), None, 2.0))
    low = norm(filt(brown(rng, n), 22, 140, order=4))
    grit = norm(filt(pink(rng, n), 150, 500)) * 10 ** (-10 / 20)
    rumble = norm((low + grit) * swell * env(t, 0.12, 2.4)) * 10 ** (RUMBLE_DB / 20)

    debris = np.zeros(n)
    grain_t = np.arange(int(0.03 * SR)) / SR
    at = 0.15
    while at < 3.5:
        at += rng.exponential(1 / (40 * math.exp(-(at - 0.15) / 0.8) + 3))
        i = int(at * SR)
        if i >= n - len(grain_t):
            break
        grain = rng.standard_normal(len(grain_t)) * env(grain_t, 0.001, rng.uniform(0.005, 0.012))
        debris[i:i + len(grain)] += grain * rng.uniform(0.3, 1.0) * math.exp(-(at - 0.15) / 1.2)
    debris = norm(filt(debris, 400, 2500, order=4)) * 10 ** (DEBRIS_DB / 20)

    out = slam + boom + roar + echoes + rumble + debris
    out = filt(out - np.mean(out), 25, None)
    fade_in = int(0.001 * SR)
    out[:fade_in] *= np.linspace(0, 1, fade_in)
    fade_out = int(1.2 * SR)
    out[n - fade_out:] *= np.cos(np.linspace(0, math.pi / 2, fade_out)) ** 2
    return out


def master(x):
    gain = TARGET_M100 - meter.momentary_max(x)
    over = meter.true_peak(x) + gain - TRUE_PEAK
    if over > MAX_LIMIT:
        gain -= over - MAX_LIMIT
    return meter.limit(x * 10 ** (gain / 20), TRUE_PEAK)


def decode_asset():
    return meter.decode_asset(NAME)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "mix", "reinhard"))
    parser.add_argument("--check", action="store_true", help="re-synthesize and compare with the committed asset")
    parser.add_argument("--no-package", action="store_true", help="write the WAV only")
    args = parser.parse_args()
    x = master(synth())
    if args.check:
        y = decode_asset()
        k = min(len(x), len(y))
        corr = float(np.dot(x[:k], y[:k]) / (np.linalg.norm(x[:k]) * np.linalg.norm(y[:k]) + 1e-15))
        ok = abs(len(x) - len(y)) <= 0.03 * SR and corr >= 0.95
        print(f"{NAME}: {'ok' if ok else 'MISMATCH'} correlation {corr:.4f}, {len(y) / SR:.3f} s")
        sys.exit(0 if ok else 1)
    os.makedirs(args.out, exist_ok=True)
    wav = os.path.join(args.out, NAME + ".wav")
    meter.write_wav(x, wav)
    print(f"{NAME}: {len(x) / SR:.3f} s, M100 {meter.momentary_max(x):.1f} LUFS, TP {meter.true_peak(x):.1f} dBTP -> {wav}")
    if not args.no_package:
        subprocess.run([sys.executable, HELPER, "package", "--input", wav, "--module", "core", "--name", "reinhard/" + NAME,
                        "--event", "reinhard_" + NAME, "--own", "--replace"], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
