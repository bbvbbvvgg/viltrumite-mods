#!/usr/bin/env python3
"""Original procedural REINHARD OVERHAUL strike layers; no samples or external licensed input.

Ordinary fist and Reid attacks get several close variants per event so repeated
strikes do not sound copy-pasted, plus one quiet seamless loop for the drawn
stance. Deterministic seeds; faded (except the loop), soft-limited PCM with
bounded headroom before Vorbis. Install via the existing .ogg.b64 Gradle
pipeline. Generated audio: CC0-1.0.
Rebuild: python3 tools/sfx/reinhard_overhaul.py   (--check validates without writing)
Not a substitute for listening and tuning in Minecraft next to other layers.
"""
from pathlib import Path
import argparse, base64, subprocess, sys, tempfile, wave, zlib
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/reinhard'
RATE = 44100
SEED = 20261101
LOOP_SECONDS = 4.0  # every hum partial is a multiple of 1/LOOP_SECONDS Hz


def rng(name):
    return np.random.default_rng(SEED + zlib.crc32(name.encode()))


def timeline(duration):
    return np.arange(int(round(duration * RATE))) / RATE


def band(x, lo, hi, order=4):
    """Zero-phase FFT band-pass with smooth Butterworth-like skirts (padded, no wrap)."""
    n = len(x); size = 1 << int(np.ceil(np.log2(2 * n)))
    freq = np.fft.rfftfreq(size, 1 / RATE)
    gain = np.ones_like(freq)
    if lo: gain *= 1 / np.sqrt(1 + (lo / np.maximum(freq, 1e-3)) ** (2 * order))
    if hi: gain *= 1 / np.sqrt(1 + (freq / hi) ** (2 * order))
    return np.fft.irfft(np.fft.rfft(x, size) * gain, size)[:n]


def sweep(x, centre, damping, stages=2):
    """Cascaded Chamberlin state-variable band-pass with a per-sample centre frequency."""
    f = 2 * np.sin(np.pi * np.minimum(centre, RATE / 6.5) / RATE)
    for _ in range(stages):
        low = mid = 0.0; out = np.empty_like(x)
        for i in range(len(x)):
            high = x[i] - low - damping * mid
            mid += f[i] * high
            low += f[i] * mid
            out[i] = mid
        x = out
    return x


def glide(t, start, end, rate):
    """Phase of a sine whose frequency glides exponentially from start to end."""
    freq = end + (start - end) * np.exp(-t * rate)
    return 2 * np.pi * np.cumsum(freq) / RATE


def norm(x):
    return x / max(1e-9, np.max(np.abs(x)))


def finish(x, t, peak, drive, fade_in=.002, fade_out=.03):
    duration = len(t) / RATE
    fade = np.minimum(1, t / fade_in) * np.clip((duration - t) / fade_out, 0, 1)
    x = np.tanh(norm(x) * drive) * fade
    return norm(x) * peak


def fist_swing(variant):
    name = f'fist_swing_{variant}'; r = rng(name)
    duration = .21 + .015 * variant; t = timeline(duration)
    top = (2200, 1950, 2450)[variant - 1]; apex = (.40, .36, .44)[variant - 1] * duration
    centre = np.interp(t, [0, apex, duration], [500 + 40 * variant, top, 900])
    air = sweep(band(r.normal(0, 1, len(t)), 120, 3200), centre, .75)
    body = band(r.normal(0, 1, len(t)), 140, 420)
    env = np.where(t < apex, (t / apex) ** 1.6, np.exp(-(t - apex) / (.045 + .005 * variant)))
    return name, finish((norm(air) + .35 * norm(body)) * env, t, .5, 1.3)


def fist_hit(variant):
    name = f'fist_hit_{variant}'; r = rng(name)
    duration = .32; t = timeline(duration)
    floor = (55, 60, 50)[variant - 1]
    thump = np.sin(glide(t, 120 + 8 * variant, floor, 30)) * np.exp(-t * 22)
    slap_lo, slap_hi = ((800, 2500), (950, 2800), (700, 2200))[variant - 1]
    slap = norm(band(r.normal(0, 1, len(t)), slap_lo, slap_hi)) * np.exp(-t * 55)
    click = norm(band(r.normal(0, 1, len(t)), 3000, None)) * np.exp(-t * 900)
    tail = norm(band(r.normal(0, 1, len(t)), 60, 400)) * np.exp(-t * 12)
    return name, finish(thump + .7 * slap + .45 * click + .25 * tail, t, .72, 1.8, fade_out=.06)


def reid_swing(variant):
    name = f'reid_swing_{variant}'; r = rng(name)
    duration = .34 + .02 * variant; t = timeline(duration)
    apex = (.45, .40, .50)[variant - 1] * duration
    centre = np.interp(t, [0, apex, duration], [300, (1500, 1350, 1650)[variant - 1], 600])
    air = norm(sweep(band(r.normal(0, 1, len(t)), 90, 2600), centre, .65))
    low = norm(band(r.normal(0, 1, len(t)), 60, 250))
    env = np.where(t < apex, (t / apex) ** 1.4, np.exp(-(t - apex) / .07))
    detune = 1 + .012 * (variant - 2)
    shimmer = sum(a * np.sin(2 * np.pi * f * detune * t + r.uniform(0, 2 * np.pi))
                  for f, a in ((2350, .06), (3710, .045), (5230, .03)))
    shimmer *= 1 + .3 * np.sin(2 * np.pi * 23 * t)
    return name, finish((air + .35 * low + shimmer) * env, t, .55, 1.3, fade_out=.05)


def ring(t, partials, r, attack=.002):
    return sum(a * np.exp(-t * d) * np.sin(2 * np.pi * f * t + r.uniform(0, 2 * np.pi))
               for f, d, a in partials) * np.minimum(1, t / attack)


def reid_hit(variant):
    name = f'reid_hit_{variant}'; r = rng(name)
    duration = .5; t = timeline(duration)
    scale = 1 + .02 * (variant - 2)
    slice_ = norm(band(r.normal(0, 1, len(t)), 2500, 7000)) * np.minimum(1, t / .0015) * np.exp(-t * 45)
    metal = ring(t, [(1870 * scale, 7, .22), (2960 * scale, 9, .16), (4410 * scale, 12, .11), (6100 * scale, 15, .07)], r)
    thud = np.sin(glide(t, 95, 50 + 3 * variant, 25)) * np.exp(-t * 18)
    body = norm(band(r.normal(0, 1, len(t)), 50, 500)) * np.exp(-t * 14)
    return name, finish(.6 * slice_ + metal * 2.2 + .7 * thud + .3 * body, t, .7, 1.5, fade_out=.12)


def reid_heavy(variant):
    name = f'reid_heavy_{variant}'; r = rng(name)
    duration = 1.2; t = timeline(duration)
    scale = (1.0, .94)[variant - 1]
    chirp = np.sin(glide(t, 70, 35, 4)) * np.exp(-t * 3.2) * np.minimum(1, t / .005)
    crack = norm(band(r.normal(0, 1, len(t)), 1500, None)) * np.exp(-t * 260)
    metal = ring(t, [(1210 * scale, 3.0, .18), (1890 * scale, 3.8, .14), (2730 * scale, 4.6, .10), (3560 * scale, 5.5, .07)], r, .003)
    rumble = norm(band(np.cumsum(r.normal(0, 1, len(t))), 25, 180)) * np.exp(-t * 2.6) * np.minimum(1, t / .03)
    return name, finish(chirp + .9 * crack + 1.8 * metal + .45 * rumble, t, .8, 2.0, fade_out=.25)


def reid_drawn_hum():
    """Seamless 4 s loop: tonal partials and noise are exactly periodic, no fades."""
    name = 'reid_drawn_hum'; r = rng(name)
    n = int(LOOP_SECONDS * RATE); t = np.arange(n) / RATE
    step = 1 / LOOP_SECONDS
    tone = sum(a * np.sin(2 * np.pi * f * t + r.uniform(0, 2 * np.pi))
               for f, a in ((82.5, 1.0), (82.75, .55), (165.0, .42), (247.5, .2), (330.25, .12),
                            (1650.0, .018), (2475.5, .012)))
    breath = 1 + .25 * np.sin(2 * np.pi * .5 * t) + .1 * np.sin(2 * np.pi * .75 * t + 1.3)
    freq = np.fft.rfftfreq(n, 1 / RATE)
    spectrum = r.normal(0, 1, len(freq)) * np.exp(1j * r.uniform(0, 2 * np.pi, len(freq)))
    spectrum *= np.where((freq > 150) & (freq < 900), 1, 0)
    air = norm(np.fft.irfft(spectrum, n)) * (1 + .3 * np.sin(2 * np.pi * .25 * t))
    x = np.tanh(norm(norm(tone) * breath + .12 * air) * 1.2)
    assert all(abs(f / step - round(f / step)) < 1e-9 for f in (82.5, 82.75, 165.0, 247.5, 330.25, 1650.0, 2475.5, .5, .75, .25))
    return name, norm(x) * .3


RECIPES = ([lambda v=v: fist_swing(v) for v in (1, 2, 3)] + [lambda v=v: fist_hit(v) for v in (1, 2, 3)]
           + [lambda v=v: reid_swing(v) for v in (1, 2, 3)] + [lambda v=v: reid_hit(v) for v in (1, 2, 3)]
           + [lambda v=v: reid_heavy(v) for v in (1, 2)] + [reid_drawn_hum])


def encode(name, x, write):
    with tempfile.TemporaryDirectory() as temp:
        wav = Path(temp) / 'source.wav'; ogg = Path(temp) / 'output.ogg'
        with wave.open(str(wav), 'wb') as w:
            w.setparams((1, 2, RATE, len(x), 'NONE', 'not compressed'))
            w.writeframes(np.round(x * 32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '4',
                        '-fflags', '+bitexact', '-flags:a', '+bitexact', str(ogg)], check=True)
        data = ogg.read_bytes()
        if write: (OUT / f'{name}.ogg.b64').write_text(base64.b64encode(data).decode() + '\n')
        return data


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--check', action='store_true', help='synthesize and validate without writing assets')
    args = parser.parse_args(argv)
    OUT.mkdir(parents=True, exist_ok=True)
    for recipe in RECIPES:
        name, x = recipe()
        peak = float(np.max(np.abs(x)))
        assert peak <= .81, (name, peak)
        if name != 'reid_drawn_hum': assert abs(x[-1]) < 1e-3 and abs(x[0]) < .05, name
        data = encode(name, x, not args.check)
        print(f'{name}: {len(x) / RATE:.3f} s, PCM peak {20 * np.log10(peak):.2f} dBFS, {len(data)} bytes')
    return 0


if __name__ == '__main__':
    sys.exit(main())
