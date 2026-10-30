#!/usr/bin/env python3
"""Original procedural World Erasure cues (Reinhard N). No samples, no external input: CC0-1.0.

Cue sheet (scene ticks, 20 t/s; see ReinhardRules / ErasureScene.Cue):
  erase_tension  20 -> 90   slow tension under the crack growth, creaks thicken, cut at the shatter
  erase_shatter  90         sub boom under the existing erase_crack glass shatter
  erase_dissolve 112 -> 150 rising white shimmer, last spark as the target is gone
  erase_snap     168..184   a shard ring snapping home (pitch varies per ring in code)
  erase_restore  192        final impact: reality is whole again
Deterministic seeds. Rebuild: python3 tools/sfx/reinhard_erasure.py [--replace] (needs numpy, ffmpeg).
Packages through the sound-design helper (mono 44.1 kHz Vorbis, .ogg.b64 in core binassets).
Not a substitute for listening and tuning in Minecraft.
"""
from pathlib import Path
import subprocess, sys, tempfile, wave
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
RATE = 44100
HELPER = ROOT / '.agents/skills/sound-design/scripts/openverse_sfx.py'


def time(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def band(x, lo, hi):
    """Band filter in the frequency domain (4th-order skirts)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    g = np.ones_like(f)
    if lo:
        g *= 1 / (1 + (lo / np.maximum(f, 1)) ** 4)
    if hi:
        g *= 1 / (1 + (f / hi) ** 4)
    return np.fft.irfft(spec * g, n=len(x))


def norm(x, peak=1.0):
    m = np.max(np.abs(x))
    return x / m * peak if m > 0 else x


def reverb(x, seconds, rng, damp=3000):
    """Cheap diffuse tail: convolution with decaying, darkened noise."""
    t = time(seconds)
    ir = rng.normal(0, 1, len(t)) * np.exp(-t * 6.9 / seconds)
    ir = band(ir, 80, damp)
    ir[0] = 0
    n = len(x) + len(ir)
    wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)
    dry = np.concatenate([x, np.zeros(len(ir))])
    return dry, norm(wet)


def sweep(f0, f1, t, curve=1.0):
    """Phase of a frequency glide f0 -> f1 over t (curve > 1: late glide)."""
    u = (t / t[-1]) ** curve
    f = f0 + (f1 - f0) * u
    return 2 * np.pi * np.cumsum(f) / RATE


def tension():
    rng = np.random.default_rng(2026103001)
    d = 3.5
    t = time(d)
    u = t / d
    drone = np.sin(sweep(38, 52, t, 1.6)) + .5 * np.sin(sweep(76, 106, t, 1.6)) + .22 * np.sin(sweep(57, 79, t, 1.6))
    drone *= .35 + .65 * u ** 1.4
    air = band(rng.normal(0, 1, len(t)), 300, 2400)
    air = norm(air) * (.08 + .5 * u ** 2.2)
    # glass under strain: resonant partials, slow beating, louder towards the rupture
    glass = sum(np.sin(2 * np.pi * f * t + p) * (1 + .6 * np.sin(2 * np.pi * b * t))
                for f, p, b in ((1870, .3, .7), (2790, 1.1, 1.3), (4130, 2.0, 2.1), (5510, .7, 3.4)))
    glass = norm(glass) * (u ** 2.6) * .22
    # creaks: short clicks whose density grows from a few to a crackle
    creak = np.zeros(len(t))
    pos = .25
    while True:
        gap = .32 * (1 - u[int(pos * RATE)]) ** 1.7 + .012
        pos += gap * (.5 + rng.random())
        if pos >= d - .03:
            break
        i = int(pos * RATE)
        n = int(RATE * (.006 + .02 * rng.random()))
        k = np.arange(n)
        f = 900 + 4200 * rng.random()
        seg = rng.normal(0, 1, n) * np.exp(-k / (n / 4)) + np.sin(2 * np.pi * f * k / RATE) * np.exp(-k / (n / 3))
        seg = seg[:len(creak) - i]
        creak[i:i + len(seg)] += seg * (.25 + .75 * u[i]) * (.4 + .6 * rng.random())
    creak = norm(band(creak, 700, 9000)) * .55
    # critical last half-second: everything swells
    crit = np.clip((t - 3.0) / .5, 0, 1) ** 2
    x = drone * .55 + air + glass * (1 + 1.5 * crit) + creak * (1 + crit)
    x *= np.minimum(1, t / .6) * np.minimum(1, (d - t) / .015)
    return norm(np.tanh(norm(x) * 1.6), .89)


def boom(d, f0, f1, seed, crack=.6, tail=2.2, bright=4000):
    rng = np.random.default_rng(seed)
    t = time(d)
    sub = np.sin(sweep(f0, f1, t, .35)) * np.exp(-t * 2.2)
    sub += .4 * np.sin(sweep(f0 * 2.01, f1 * 2, t, .35)) * np.exp(-t * 4)
    hit = band(rng.normal(0, 1, len(t)), 120, bright) * np.exp(-t * 18)
    snap = band(rng.normal(0, 1, len(t)), 2500, 12000) * np.exp(-t * 60)
    body = norm(sub) * .9 + norm(hit) * crack + norm(snap) * .35
    dry, wet = reverb(body, tail, rng, 2200)
    x = dry + wet * .45
    tt = np.arange(len(x)) / RATE
    x *= np.minimum(1, (tt[-1] - tt) / .3)
    return x


def shatter():
    return norm(np.tanh(norm(boom(2.4, 95, 27, 2026103002)) * 2.2), .9)


def dissolve():
    rng = np.random.default_rng(2026103003)
    d = 2.1
    t = time(d)
    u = t / d
    # a bright chord that slowly rises: light, not a voice
    chord = sum(np.sin(sweep(f, f * 1.12, t, 1.3) + rng.random() * 6) * a
                for f, a in ((523.25, 1), (659.25, .8), (783.99, .7), (1046.5, .5), (1567.98, .3), (2093, .22)))
    chord = norm(chord) * (u ** 1.5) * (.6 + .4 * np.sin(2 * np.pi * 5.5 * t) ** 2)
    # sparkles: tiny pings, denser towards the end
    sp = np.zeros(len(t))
    for _ in range(260):
        i = int(rng.random() ** .55 * (d - .1) * RATE)
        n = int(RATE * .05)
        k = np.arange(n)
        seg = np.sin(2 * np.pi * (2500 + 6000 * rng.random()) * k / RATE) * np.exp(-k / (RATE * .012)) * (.3 + .7 * rng.random())
        seg = seg[:len(sp) - i]
        sp[i:i + len(seg)] += seg
    sp = norm(sp) * (.2 + .8 * u)
    airy = norm(band(rng.normal(0, 1, len(t)), 3000, 14000)) * u ** 2 * .35
    # the last spark as the final cell goes (~1.85 s = tick 149)
    spark = np.exp(-np.maximum(0, t - 1.85) * 9) * (t >= 1.85) * np.sin(2 * np.pi * 3136 * t)
    x = chord * .6 + sp * .45 + airy + spark * .7
    x *= np.minimum(1, t / .4) * np.minimum(1, (d - t) / .12)
    dry, wet = reverb(x, 1.2, rng, 7000)
    x = dry + wet * .3
    return norm(np.tanh(norm(x) * 1.3), .85)


def snap():
    rng = np.random.default_rng(2026103004)
    d = .7
    t = time(d)
    tick = band(rng.normal(0, 1, len(t)), 1800, 11000) * np.exp(-t * 90)
    ring = sum(np.sin(2 * np.pi * f * t) * np.exp(-t * k) for f, k in ((2240, 22), (3410, 30), (5120, 40)))
    thump = np.sin(sweep(120, 48, t, .4)) * np.exp(-t * 14)
    x = norm(tick) * .9 + norm(ring) * .35 + norm(thump) * .75
    x *= np.minimum(1, (d - t) / .2)
    return norm(np.tanh(norm(x) * 1.8), .85)


def restore():
    x = boom(3.2, 80, 24, 2026103006, crack=.8, tail=2.8, bright=6000)
    t = np.arange(len(x)) / RATE
    # reality whole again: a low open fifth blooms under the tail
    chord = (np.sin(2 * np.pi * 65.4 * t) + .6 * np.sin(2 * np.pi * 98 * t) + .35 * np.sin(2 * np.pi * 130.8 * t))
    chord *= np.minimum(1, t / .25) * np.exp(-t * 1.1)
    x = norm(x) + chord * .25
    x *= np.minimum(1, (t[-1] - t) / .4)
    return norm(np.tanh(norm(x) * 2.4), .92)


CUES = {
    'erase_tension': tension,
    'erase_shatter': shatter,
    'erase_dissolve': dissolve,
    'erase_snap': snap,
    'erase_restore': restore,
}


def main():
    replace = '--replace' in sys.argv
    with tempfile.TemporaryDirectory() as tmp:
        for name, make in CUES.items():
            x = make()
            wav = Path(tmp) / f'{name}.wav'
            with wave.open(str(wav), 'wb') as w:
                w.setparams((1, 2, RATE, len(x), 'NONE', 'not compressed'))
                w.writeframes((np.clip(x, -1, 1) * 32767).astype('<i2').tobytes())
            cmd = [sys.executable, str(HELPER), 'package', '--input', str(wav), '--module', 'core', '--name', f'reinhard/{name}',
                   '--event', f'reinhard_{name}', '--own', '--repo', str(ROOT)]
            if replace:
                cmd.append('--replace')
            subprocess.run(cmd, check=True)
            print(name, f'{len(x) / RATE:.2f}s')


if __name__ == '__main__':
    main()
