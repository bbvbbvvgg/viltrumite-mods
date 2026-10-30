#!/usr/bin/env python3
"""Original synthesis of the Od Lagna's Wish sounds (own work, CC0). Reproducible: fixed seeds.

python tools/sfx/od_lagna.py OUT_DIR   -> writes the reviewed WAV mixes; package each with
.agents/skills/sound-design/scripts/openverse_sfx.py package --own --module core --name od_lagna/<n> --event od_lagna_<n>
"""
import sys, wave
from pathlib import Path
import numpy as np

SR = 44100

def t_axis(seconds):
    return np.arange(int(SR * seconds)) / SR

def env(t, attack, release, total):
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    r = np.clip((total - t) / max(release, 1e-4), 0, 1)
    return np.minimum(a, r) ** 1.5

def partial(t, f, amp, decay=None, vib=0.0, vib_rate=5.0, phase=0.0):
    ph = 2 * np.pi * f * t + vib * np.sin(2 * np.pi * vib_rate * t) + phase
    s = amp * np.sin(ph)
    if decay is not None:
        s *= np.exp(-t / decay)
    return s

def lowpass(x, cutoff):
    # one-pole, enough for softening noise
    a = np.exp(-2 * np.pi * cutoff / SR)
    # exact one-pole IIR through its impulse response (fast, no Python loop)
    n = min(len(x), int(SR * 0.05) + 1)
    h = (1 - a) * a ** np.arange(n)
    size = len(x) + n
    nfft = 1 << (size - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(h, nfft), nfft)[:len(x)]

def reverb(x, seconds=1.2, mix=0.35, seed=1):
    rng = np.random.default_rng(seed)
    n = int(SR * seconds)
    ir = rng.standard_normal(n) * np.exp(-np.linspace(0, 7, n))
    ir[0] = 0
    size = len(x) + n
    nfft = 1 << (size - 1).bit_length()
    wet = np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(ir, nfft), nfft)[:size]
    wet /= max(1e-9, np.max(np.abs(wet)))
    dry = np.concatenate([x, np.zeros(n)])
    return (1 - mix) * dry + mix * wet * np.max(np.abs(x))

def sparkles(t, count, lo, hi, seed, amp=0.08, start=0.0, end=None):
    rng = np.random.default_rng(seed)
    out = np.zeros_like(t)
    end = t[-1] if end is None else end
    for _ in range(count):
        at = rng.uniform(start, end)
        f = rng.uniform(lo, hi)
        idx = t >= at
        tt = t[idx] - at
        out[idx] += amp * rng.uniform(0.4, 1) * np.sin(2 * np.pi * f * tt) * np.exp(-tt / rng.uniform(0.05, 0.18))
    return out

def finish(x, peak=0.85):
    x = x - np.mean(x)
    fade = int(SR * 0.02)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    return x / max(1e-9, np.max(np.abs(x))) * peak

def call():
    # The plea: a rising major chord swell with slow vibrato and sparkles, peaking near 2 s (swap tick 40).
    total = 3.2; t = t_axis(total)
    chord = [220.0, 277.18, 329.63, 440.0, 554.37, 659.25]
    swell = np.clip(t / 2.0, 0, 1) ** 2 * np.clip((total - t) / 1.2, 0, 1)
    x = sum(partial(t, f * (1 + 0.004 * np.sin(2 * np.pi * 0.3 * t + i)), 0.18 / (1 + 0.4 * i), vib=0.004 * f / 5, vib_rate=4.5 + 0.3 * i) for i, f in enumerate(chord))
    x = x * swell
    noise = lowpass(np.random.default_rng(3).standard_normal(len(t)), 2400) * 0.25 * swell
    x += noise + sparkles(t, 60, 1800, 5200, 4, 0.05, 0.3, 2.4)
    return finish(reverb(x, 1.4, 0.4, 5))

def granted():
    # The answer: a bright bell stroke with a low warm bloom under it.
    total = 2.6; t = t_axis(total)
    base = 523.25
    ratios = [1.0, 2.01, 2.99, 4.2, 5.43, 6.8]
    bell = sum(partial(t, base * r, 0.5 / (1 + i), decay=1.6 / (1 + 0.6 * i)) for i, r in enumerate(ratios))
    bloom = sum(partial(t, f, 0.12, decay=1.4) for f in (130.81, 196.0, 261.63)) * np.clip(t / 0.15, 0, 1)
    x = bell + bloom + sparkles(t, 30, 2500, 6000, 7, 0.06, 0.0, 0.9)
    return finish(reverb(x, 1.8, 0.45, 8))

def open_():
    # The screen opens: an airy upward arpeggio over soft breath noise.
    total = 1.4; t = t_axis(total)
    notes = [659.25, 830.61, 987.77, 1318.5]
    x = np.zeros_like(t)
    for i, f in enumerate(notes):
        at = 0.08 * i; idx = t >= at; tt = t[idx] - at
        x[idx] += 0.3 * np.sin(2 * np.pi * f * tt) * np.exp(-tt / 0.6)
    x += lowpass(np.random.default_rng(9).standard_normal(len(t)), 3000) * 0.12 * env(t, 0.3, 0.8, total)
    return finish(reverb(x, 1.0, 0.35, 10), 0.6)

def select():
    # A small glass tick for hover and pick.
    total = 0.3; t = t_axis(total)
    x = partial(t, 1760, 0.5, decay=0.06) + partial(t, 2637, 0.25, decay=0.04)
    return finish(reverb(x, 0.3, 0.25, 11), 0.5)

def confirm():
    # «исполнить желание»: three rising chimes.
    total = 1.2; t = t_axis(total)
    x = np.zeros_like(t)
    for i, f in enumerate([783.99, 1046.5, 1567.98]):
        at = 0.09 * i; idx = t >= at; tt = t[idx] - at
        x[idx] += 0.35 * (np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(2 * np.pi * 2.01 * f * tt)) * np.exp(-tt / 0.45)
    return finish(reverb(x, 1.0, 0.35, 12), 0.7)

def return_():
    # «вернуть прежнюю жизнь»: a warm chord that settles down a step and fades like a breath.
    total = 3.0; t = t_axis(total)
    glide = np.where(t < 1.2, 1.0, 1.0 - 0.0561 * np.clip((t - 1.2) / 0.8, 0, 1))
    x = sum(partial(t * glide, f, 0.2 / (1 + i)) for i, f in enumerate([261.63, 329.63, 392.0, 523.25]))
    x *= env(t, 0.5, 1.6, total)
    x += lowpass(np.random.default_rng(13).standard_normal(len(t)), 1200) * 0.1 * env(t, 0.8, 1.4, total)
    return finish(reverb(x, 1.6, 0.4, 14), 0.75)

SOUNDS = {'call': call, 'granted': granted, 'open': open_, 'select': select, 'confirm': confirm, 'return': return_}

def write(path, x):
    with wave.open(str(path), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype('<i2').tobytes())

if __name__ == '__main__':
    out = Path(sys.argv[1] if len(sys.argv) > 1 else 'od_lagna_wav'); out.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        write(out / f'{name}.wav', fn()); print(out / f'{name}.wav')
