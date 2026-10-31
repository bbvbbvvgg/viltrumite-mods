#!/usr/bin/env python3
"""Reinhard - THE FINAL PUNCH (secret Shift+N): original procedural cues. CC0-1.0.

The speech is subtitles only (no synthesised voice): FinalPunchRules.LINE_AT owns its timing.

Cue sheet (scene ticks, 20 t/s; FinalPunchRules / FinalPunchAudio own the exact ticks):
  music     8 -> 398    the swelling score, cut dead at the silence
  wind      loop        rises from Phase I to IV, cut at the silence
  hum       loop        the fist's low hum (Phase III-IV), cut at the silence
  ignite    ~140        the fist catches fire
  spark     arcs        short electric crackles (Phase III-IV)
  ring      pulses      giant energy-ring whomps (Phase IV)
  pressure  ~300        roaring pressure and creaking reality (Phase IV)
  crackle   398 -> 444  the only sound in the silence: faint energy crackle
  whoosh    412         the extremely slow strike
  impact    444         the punch
  jaw       444         bone crack (people)       clang 444  metal jaw (iron golem)
  smoke     452         hiss around the lowered fist
Deterministic seeds. Rebuild: python3 tools/sfx/reinhard_final_punch.py [--replace] [cue ...]
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
    t = time(seconds)
    ir = rng.normal(0, 1, len(t)) * np.exp(-t * 6.9 / seconds)
    ir = band(ir, 80, damp)
    ir[0] = 0
    n = len(x) + len(ir)
    wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)
    dry = np.concatenate([x, np.zeros(len(ir))])
    return dry, norm(wet)


def sweep(f0, f1, t, curve=1.0):
    u = (t / t[-1]) ** curve
    f = f0 + (f1 - f0) * u
    return 2 * np.pi * np.cumsum(f) / RATE


def fade(x, a=.01, b=.05):
    t = np.arange(len(x)) / RATE
    return x * np.minimum(1, t / max(a, 1e-4)) * np.minimum(1, (t[-1] - t) / max(b, 1e-4))


def loopable(x, cross=.5):
    """Seamless loop: the tail crossfades into the head."""
    n = int(cross * RATE)
    head, body, tail = x[:n], x[n:-n], x[-n:]
    w = np.linspace(0, 1, n)
    return np.concatenate([tail * (1 - w) + head * w, body])


def saw(phase, harmonics=12):
    return sum(np.sin(phase * k) / k for k in range(1, harmonics + 1))


# ------------------------------------------------------------------ score

def music():
    """19.5 s: a low string drone, a minor choir, war drums that accelerate, everything rising."""
    rng = np.random.default_rng(2026103101)
    d = 19.6
    t = time(d)
    u = t / d
    swell = u ** 1.6
    # D minor: drone on D1/D2, choir D3 F3 A3 D4, climbing a semitone over the last third
    climb = 1 + .059 * np.clip((u - .66) / .34, 0, 1) ** 2
    drone = saw(2 * np.pi * np.cumsum(36.71 * climb) / RATE, 10) + .7 * saw(2 * np.pi * np.cumsum(73.42 * climb) / RATE, 8)
    drone = band(drone, 30, 300) * (1 - swell) + band(drone, 30, 2700) * swell
    choir = np.zeros(len(t))
    for f in (146.83, 174.61, 220.0, 293.66, 349.23):
        for det in (-.004, 0, .005):
            vib = 1 + .006 * np.sin(2 * np.pi * (4.8 + rng.random()) * t + rng.random() * 6)
            ph = 2 * np.pi * np.cumsum(f * (1 + det) * vib * climb) / RATE
            choir += saw(ph, 6)
    # formant-ish "ah": two resonant bands
    choir = band(choir, 400, 1300) + .5 * band(choir, 900, 2600)
    choir = norm(choir) * np.clip((u - .08) / .4, 0, 1) ** 1.3
    # drums: hits whose gap shrinks from 2.2 s to 0.12 s
    drums = np.zeros(len(t))
    pos = 1.0
    while pos < d - .15:
        i = int(pos * RATE)
        n = int(RATE * .9)
        k = np.arange(n) / RATE
        hit = np.sin(2 * np.pi * np.cumsum(60 * np.exp(-k * 9) + 42) / RATE) * np.exp(-k * 5.5)
        hit += band(rng.normal(0, 1, n), 80, 1800) * np.exp(-k * 30) * .5
        hit = hit[:len(drums) - i]
        drums[i:i + len(hit)] += hit * (.45 + .55 * u[i])
        g = pos / d
        pos += 2.2 * (1 - g) ** 2.2 + .12
    brass = np.zeros(len(t))
    # a brass-like stab of the main motif (D F A D) at 9 s and 14 s
    for start, notes in ((8.8, (146.83, 174.61, 220.0, 293.66)), (13.9, (146.83, 174.61, 233.08, 293.66))):
        for j, f in enumerate(notes):
            s = start + j * .45
            i = int(s * RATE)
            n = int(RATE * .7)
            k = np.arange(n) / RATE
            note = saw(2 * np.pi * f * k, 14) * np.minimum(1, k / .05) * np.exp(-k * 2.2)
            note = band(note, 120, 3500)
            brass[i:i + n] += note[:max(0, len(brass) - i)]
    riser = band(rng.normal(0, 1, len(t)), 500, 9000) * np.clip((u - .7) / .3, 0, 1) ** 3
    shep = sum(np.sin(sweep(f, f * 2, t, 3.0)) for f in (220, 330, 440)) * np.clip((u - .62) / .38, 0, 1) ** 2.5
    x = norm(drone) * (.35 + .5 * swell) + choir * .55 * (.4 + .6 * swell) + norm(drums) * .8 + norm(brass) * .35 \
        + norm(riser) * .25 + norm(shep) * .18
    dry, wet = reverb(x, 2.4, rng, 3500)
    x = (dry + wet * .35)[:len(t)]
    x = fade(x, 1.5, .02)
    return norm(np.tanh(norm(x) * 1.4), .9)


def wind():
    rng = np.random.default_rng(2026103102)
    d = 6.5
    t = time(d)
    n = rng.normal(0, 1, len(t))
    gust = .55 + .45 * np.sin(2 * np.pi * t / d * 2 + 1.3) * np.sin(2 * np.pi * t / d * 3)
    low = band(n, 120, 900) * gust
    whistle = band(rng.normal(0, 1, len(t)), 900, 2600) * (.3 + .7 * np.sin(2 * np.pi * t / d * 4) ** 2)
    x = norm(low) + norm(whistle) * .35
    return norm(loopable(x, .6), .8)


def hum():
    rng = np.random.default_rng(2026103103)
    d = 4.0
    t = time(d)
    # whole-cycle frequencies keep the loop seamless (4 s window)
    x = np.sin(2 * np.pi * 41.0 * t) + .6 * np.sin(2 * np.pi * 82.0 * t) + .3 * np.sin(2 * np.pi * 123.25 * t)
    x *= 1 + .25 * np.sin(2 * np.pi * 3.0 * t)
    buzz = band(rng.normal(0, 1, len(t)), 60, 400) * .35
    x = np.tanh(norm(x + buzz) * 2.0)
    return norm(loopable(x, .3), .85)


def ignite():
    rng = np.random.default_rng(2026103104)
    d = 1.8
    t = time(d)
    whoosh = band(rng.normal(0, 1, len(t)), 200, 3000) * np.minimum(1, t / .08) * np.exp(-t * 2.2)
    roar = band(rng.normal(0, 1, len(t)), 60, 500) * np.exp(-t * 1.4)
    crack = np.zeros(len(t))
    for _ in range(70):
        i = int(rng.random() * (d - .05) * RATE)
        n = int(RATE * .004)
        crack[i:i + n] += rng.normal(0, 1, n) * (1 - i / len(t))
    x = norm(whoosh) + norm(roar) * .7 + norm(band(crack, 1500, 9000)) * .5
    return norm(np.tanh(norm(fade(x, .005, .3)) * 1.5), .85)


def spark():
    rng = np.random.default_rng(2026103105)
    d = .7
    t = time(d)
    x = np.zeros(len(t))
    for _ in range(28):
        i = int(rng.random() ** 1.5 * (d - .06) * RATE)
        n = int(RATE * (.003 + .012 * rng.random()))
        seg = rng.normal(0, 1, n) * np.exp(-np.arange(n) / (n / 3))
        x[i:i + n] += seg
    buzz = np.sign(np.sin(2 * np.pi * 120 * t)) * np.exp(-t * 7) * .25
    x = band(x, 1200, 12000) + buzz
    return norm(np.tanh(norm(fade(x, .001, .1)) * 1.7), .8)


def ring():
    rng = np.random.default_rng(2026103106)
    d = 1.4
    t = time(d)
    whomp = np.sin(sweep(180, 40, t, .5)) * np.exp(-t * 3.2)
    air = band(rng.normal(0, 1, len(t)), 300, 4000) * np.exp(-((t - .12) / .1) ** 2)
    x = norm(whomp) + norm(air) * .5
    dry, wet = reverb(x, 1.0, rng, 2500)
    return norm(np.tanh(norm(fade((dry + wet * .3)[:len(t)], .002, .2)) * 1.6), .85)


def pressure():
    rng = np.random.default_rng(2026103107)
    d = 7.0
    t = time(d)
    u = t / d
    noise = rng.normal(0, 1, len(t))
    roar = (band(noise, 30, 260) * (1 - u) + band(noise, 30, 1760) * u) * (.3 + .7 * u ** 1.3)
    rumble = np.sin(sweep(28, 46, t, 1.2)) * (.5 + .5 * u)
    creak = np.zeros(len(t))
    pos = .3
    while pos < d - .1:
        i = int(pos * RATE)
        n = int(RATE * (.05 + .1 * rng.random()))
        k = np.arange(n) / RATE
        f = 90 + 200 * rng.random()
        seg = np.sin(2 * np.pi * np.cumsum(f * (1 + .3 * np.sin(2 * np.pi * 30 * k))) / RATE) * np.exp(-k * 18)
        creak[i:i + n] += seg[:len(creak) - i] * (.3 + .7 * u[i])
        pos += .45 * (1 - u[i]) + .05 + .2 * rng.random()
    x = norm(roar) + norm(rumble) * .8 + norm(creak) * .4
    x = np.tanh(norm(x) * (1.5 + 1.5 * u))
    return norm(fade(x, .8, .05), .9)


def crackle():
    rng = np.random.default_rng(2026103108)
    d = 3.0
    t = time(d)
    x = np.zeros(len(t))
    for _ in range(90):
        i = int(rng.random() * (d - .01) * RATE)
        n = int(RATE * (.0015 + .004 * rng.random()))
        x[i:i + n] += rng.normal(0, 1, n) * (.2 + .8 * rng.random())
    x = band(x, 2000, 10000) + band(rng.normal(0, 1, len(t)), 3000, 9000) * .03
    return norm(loopable(x, .2), .5)


def whoosh():
    rng = np.random.default_rng(2026103109)
    d = 2.2
    t = time(d)
    u = t / d
    noise = rng.normal(0, 1, len(t))
    air = (band(noise, 80, 600) * (1 - u ** 2) + band(noise, 80, 3600) * u ** 2) * u ** 1.5
    low = np.sin(sweep(30, 70, t, 2.0)) * u ** 2
    x = norm(air) + norm(low) * .6
    return norm(fade(x, .3, .01), .85)


def impact():
    rng = np.random.default_rng(2026103110)
    d = 4.5
    t = time(d)
    click = band(rng.normal(0, 1, len(t)), 3000, 16000) * np.exp(-t * 120)
    thump = np.sin(sweep(160, 30, t, .3)) * np.exp(-t * 3.0)
    sub = np.sin(sweep(55, 22, t, .5)) * np.exp(-t * 1.2)
    crunch = np.tanh(band(rng.normal(0, 1, len(t)), 150, 5000) * 6) * np.exp(-t * 7)
    x = norm(click) * .8 + norm(thump) + norm(sub) * .9 + norm(crunch) * .7
    dry, wet = reverb(x, 3.0, rng, 2200)
    x = (dry + wet * .55)[:len(t)]
    x = fade(x, .0005, .6)
    # Peak .6: the server plays it at full gain together with jaw / clang; Vorbis overshoots by ~15%.
    return norm(np.tanh(norm(x) * 3.0), .6)


def jaw():
    rng = np.random.default_rng(2026103111)
    d = .6
    t = time(d)
    snap = band(rng.normal(0, 1, len(t)), 900, 9000) * np.exp(-t * 70)
    wood = sum(np.sin(2 * np.pi * f * t) * np.exp(-t * k) for f, k in ((420, 40), (760, 55), (1310, 70)))
    crack = np.zeros(len(t))
    for j in range(6):
        i = int((.004 + j * .012 + rng.random() * .006) * RATE)
        n = int(RATE * .003)
        crack[i:i + n] += rng.normal(0, 1, n)
    x = norm(snap) + norm(wood) * .5 + norm(band(crack, 1500, 12000)) * .7
    return norm(np.tanh(norm(fade(x, .0005, .2)) * 2.2), .7)


def clang():
    rng = np.random.default_rng(2026103112)
    d = 2.6
    t = time(d)
    parts = ((312, 2.2), (847, 3.0), (1290, 3.8), (1733, 4.6), (2411, 6.0), (3170, 7.5))
    x = sum(np.sin(2 * np.pi * f * t + rng.random() * 6) * np.exp(-t * k) * (1.2 - i * .15) for i, (f, k) in enumerate(parts))
    hit = band(rng.normal(0, 1, len(t)), 500, 8000) * np.exp(-t * 50)
    thud = np.sin(sweep(120, 50, t, .4)) * np.exp(-t * 9)
    x = norm(x) + norm(hit) * .7 + norm(thud) * .6
    dry, wet = reverb(x, 1.6, rng, 6000)
    return norm(np.tanh(norm(fade((dry + wet * .3)[:len(t)], .0005, .4)) * 1.8), .7)


def smoke():
    rng = np.random.default_rng(2026103113)
    d = 3.0
    t = time(d)
    hiss = band(rng.normal(0, 1, len(t)), 2500, 11000) * np.exp(-t * 1.1)
    sizzle = np.zeros(len(t))
    for _ in range(60):
        i = int(rng.random() ** 1.6 * (d - .02) * RATE)
        n = int(RATE * .002)
        sizzle[i:i + n] += rng.normal(0, 1, n)
    x = norm(hiss) * .6 + norm(band(sizzle, 2000, 10000)) * .4
    return norm(fade(x, .05, .8), .6)


CUES = {'music': music, 'wind': wind, 'hum': hum, 'ignite': ignite, 'spark': spark, 'ring': ring,
        'pressure': pressure, 'crackle': crackle, 'whoosh': whoosh, 'impact': impact, 'jaw': jaw,
        'clang': clang, 'smoke': smoke}


def write_wav(path, x, rate=RATE):
    data = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with wave.open(str(path), 'w') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(data.tobytes())


def package(wav, name, replace):
    cmd = [sys.executable, str(HELPER), 'package', '--input', str(wav), '--module', 'core', '--name', f'reinhard/final_punch/{name}',
           '--event', f'reinhard_final_punch_{name}', '--own', '--repo', str(ROOT)]
    if replace:
        cmd.append('--replace')
    subprocess.run(cmd, check=True, capture_output=True)


def main():
    replace = '--replace' in sys.argv
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        only = [a for a in sys.argv[1:] if not a.startswith('--')]
        for name, make in CUES.items():
            if only and name not in only:
                continue
            wav = tmp / f'{name}.wav'
            write_wav(wav, make())
            package(wav, name, replace)
            print(name, 'ok')


if __name__ == '__main__':
    main()
