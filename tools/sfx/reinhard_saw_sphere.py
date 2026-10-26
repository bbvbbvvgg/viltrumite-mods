#!/usr/bin/env python3
"""Saw Sphere (Reinhard item 19): the hum, grind, finale and shatter cues.

saw_hum, saw_grind and saw_finale are fully original, deterministic synthesis (seeded numpy noise and oscillators,
no recording and no download), CC0-1.0. saw_shatter (reworked after the owner's playtest: the synthetic grains
sounded like beeps, not glass) layers four CC0 1.0 Freesound recordings (FREESOUND below, fetched as the public
HQ Ogg previews into ~/.cache/viltrumite-sfx/freesound and checked by SHA-256; raw files are never committed)
over a synthesized sub boom and a faint crystal ring. Four mono 44.1 kHz cues; the sword swings reuse the existing
cinematic sweep file and are not made here.

  saw_hum      4.0 s seamless loop (every partial completes whole cycles in the loop, the noise is circular):
               a 55 Hz drone with detuned harmonics (slow beating), a band-passed whirr amplitude-modulated at
               the disc rate and a faint 1.76 kHz glass shimmer. The client rises it in pitch (0.6 -> 1.0) and
               volume while the shell grows, so the file itself is the steady top of that rise.
  saw_grind    0.55 s metal-on-stone bite of one disc pass: a toothed noise burst (2-7 kHz, 95 Hz tooth rate),
               four inharmonic metal partials and a short rock crunch below 600 Hz. Rate-limited by the client.
  saw_finale   3.0 s accent of the last great swing: a sub thump, a rising-then-falling air sweep and a long
               ringing metal chord (inharmonic, slow decay).
  saw_shatter  3.6 s break-up of the shell: the crack of a pane (unfa 221528, high-passed 700 Hz), a dense
               smash (chewiesmissus 244238), shards raining down (221Beimesche 336425, from 0.1 s), a slowed
               shatter shimmer (bevibeldesign 349905, above 1.8 kHz, -11 dB), a 90 -> 42 Hz sub boom and a faint
               E6 crystal ring; each recording is trimmed at its onset and normalized before the layer gain.

Each cue is DC-free, faded (except the loop), set to its 100 ms momentary max target (BS.1770 K-weighting, the meter
of tools/sfx/reinhard_strikes.py) under TRUE_PEAK and packaged through the sound-design helper (Vorbis q5, .ogg.b64).

    python3 tools/sfx/reinhard_saw_sphere.py            # synthesize and package all four
    python3 tools/sfx/reinhard_saw_sphere.py --check    # re-synthesize and compare with the committed assets
    python3 tools/sfx/reinhard_saw_sphere.py --no-package --out /tmp/x   # WAV only

Measured numbers only: the sandbox that wrote this has no audio playback, so the cues were not auditioned.
"""
import argparse
import hashlib
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
# Freesound id -> (creator, title, HQ preview, SHA-256 of the preview). All CC0 1.0, checked on the live pages
# (https://freesound.org/s/<id>/, 2026-10-27).
FREESOUND = {
    "221528": ("unfa", "Glass Break", "https://cdn.freesound.org/previews/221/221528_1038806-hq.ogg",
               "5335c238731731b8efcb5b2bd81071432786c93fded88db6f8bc4090feb74f76"),
    "244238": ("chewiesmissus", "Glass Smash.wav", "https://cdn.freesound.org/previews/244/244238_3156517-hq.ogg",
               "7b50e61a9b9575bc12444053eec8e1dacb8bfc7a75eaa662b0856d34a836fd3a"),
    "336425": ("221Beimesche", "Glass Shattering and Falling", "https://cdn.freesound.org/previews/336/336425_4663534-hq.ogg",
               "bd1ba2cd799d63e169c8f15a1b216a133e86f503981d613ddb8f7a82e66847d8"),
    "349905": ("bevibeldesign", "Slow Motion Glass Shatter", "https://cdn.freesound.org/previews/349/349905_4557960-hq.ogg",
               "e73b9631b028b58b875b62d7c93bd539c5c467c5487a85beb23b05832174cde0"),
}
FREESOUND_CACHE = os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "freesound")
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


def recording(sound_id):
    """Mono 44.1 kHz float samples of a FREESOUND preview (downloaded once, SHA-256 checked)."""
    _, _, url, sha = FREESOUND[sound_id]
    path = os.path.join(FREESOUND_CACHE, os.path.basename(url))
    if not os.path.isfile(path):
        os.makedirs(FREESOUND_CACHE, exist_ok=True)
        import urllib.request
        request = urllib.request.Request(url, headers={"User-Agent": "viltrumite-sfx/1.0"})
        with urllib.request.urlopen(request, timeout=60) as response, open(path + ".part", "wb") as out:
            out.write(response.read())
        os.replace(path + ".part", path)
    with open(path, "rb") as f:
        if hashlib.sha256(f.read()).hexdigest() != sha:
            raise SystemExit(f"{path}: SHA-256 mismatch (expected {sha})")
    pcm = subprocess.run(["ffmpeg", "-v", "error", "-nostdin", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(pcm, dtype="<f4").astype(np.float64)


def take(sound_id, length, lo=None, hi=None):
    """The recording from 4 ms before its onset (first sample above 10% of the peak), filtered, normalized."""
    x = recording(sound_id)
    start = max(0, int(np.argmax(np.abs(x) > 0.1 * np.abs(x).max())) - int(0.004 * SR))
    x = x[start:start + int(length * SR)]
    return norm(filt(x, lo, hi) if lo or hi else x)


def shatter(rng, length):
    n = int(round(length * SR))
    t = np.arange(n) / SR

    def at(x, seconds, gain):
        out = np.zeros(n)
        i = int(seconds * SR)
        k = min(len(x), n - i)
        out[i:i + k] = x[:k] * db(gain)
        return out

    crack = take("221528", length, 700)
    smash = take("244238", length, 120)
    fall = take("336425", length, 250)
    slow = take("349905", length, 1800)
    boom = np.tanh(1.6 * np.sin(2 * math.pi * np.cumsum(42 + 48 * np.exp(-t / 0.09)) / SR)) * env(t, 0.002, 0.45)
    ring = np.zeros(n)
    for f, gain in ((1318.5, 0), (1975.5, -3), (2637, -6), (3951, -9)):
        ring += np.sin(2 * math.pi * f * t + 0.3 * np.sin(2 * math.pi * 4.3 * t)) * db(gain) * np.exp(-t / 1.0)
    ring = norm(ring) * np.clip((t - 0.04) / 0.25, 0, 1)
    return (at(crack, 0, 0) + at(smash, 0.008, -2) + at(fall, 0.10, -3) + at(slow, 0.05, -11)
            + boom * db(-5) + ring * db(-24))


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


# Cues mixed from FREESOUND recordings: not "own", and the helper only takes Openverse ids (its API was down),
# so they are encoded like the helper does it (mono 44.1 kHz Vorbis q5, base64 under binassets).
RECORDED = {"saw_shatter"}


def package_recorded(wav, name):
    import base64
    import tempfile
    out = os.path.join(ROOT, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore", "sounds",
                       "reinhard", name + ".ogg.b64")
    with tempfile.TemporaryDirectory(prefix="viltrumite-sfx-") as tmp:
        ogg = os.path.join(tmp, name + ".ogg")
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-i", wav, "-map", "0:a:0", "-vn",
                        "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", "-f", "ogg", ogg], check=True)
        with open(ogg, "rb") as f, open(out, "wb") as dst:
            dst.write(base64.encodebytes(f.read()))


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
            if name in RECORDED:
                package_recorded(wav, name)
            else:
                subprocess.run([sys.executable, HELPER, "package", "--input", wav, "--module", "core", "--name", "reinhard/" + name,
                                "--event", "reinhard_" + name, "--own", "--replace"], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
