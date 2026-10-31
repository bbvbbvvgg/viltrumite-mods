#!/usr/bin/env python3
"""Iron Legion (Iron Man stage 6) sound kit: own synthesis only (CC0 1.0, no recordings, no vanilla stand-ins).

Cue sheet (event `ironman_legion_<name>`, trigger, length; server = level sound at the suit unless noted):

    signal    IronManLegion.call, at Tony, the sky signal (LegionRules.SIGNAL_TICKS 30 t)  2.4 s  far rising roar + whistle
    engine    LegionEngineSound, client loop per suit, volume/pitch from the throttle      2.0 s  seamless repulsor jet hum
    flyby     LegionSuitBrain, arrival flight (ARRIVAL_TICKS 56 t)                          1.8 s  whoosh with a Doppler drop
    brake     LegionSuitBrain, feet-first brake (BRAKE_TICKS 14 t)                          0.9 s  retro blast + falling whine
    land      LegionSuitBrain, READY beat (boots set down / hover lock)                    0.5 s  armoured boot thud + clank
    servo     LegionSuitBrain, READY beat (per-mark systems check)                         0.8 s  servo whir up and down
    repulsor  LegionSuitBrain, every Legion repulsor shot                                  0.45 s chirp + blast
    ignite    LegionSuitBrain, leaving (engines spool up) and the Mark 39 star boost         0.9 s  spark crackle + ignition + riser
    failure   LegionSuitBrain, critical damage / crash start                               1.3 s  sputter + falling whine + crackle
    crash     LegionSuitBrain, crash impact                                                1.6 s  heavy metal impact + debris + rumble
    order     IronManLegion.order, notify sound to Tony only                               0.35 s two-tone acknowledgement
    hit       LegionSuitEntity.hurt, armour hit                                            0.35 s inharmonic metal ping + click

Every cue is mono 44.1 kHz, deterministic (seeded numpy), DC-free, high-passed at 35 Hz, set to its class's
100 ms momentary max (BS.1770 K-weighting, the meter of tools/sfx/reinhard_strikes.py) under the class
true-peak ceiling, and packaged with the sound-design helper (`package --own`, Vorbis q5, .ogg.b64) under
sounds/ironman/legion/. The engine loop is seamless by construction: every partial, the noise spectrum
(random phase, inverse FFT of exactly the loop length) and every modulation are periodic in the loop.

    python3 tools/sfx/ironman_legion.py              # synthesize and package every cue
    python3 tools/sfx/ironman_legion.py --no-package --out /tmp/legion
    python3 tools/sfx/ironman_legion.py --list

Measured numbers only: the sandbox that wrote this has no audio playback, so the cues were NOT auditioned.
"""
import argparse
import math
import os
import subprocess
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reinhard_strikes as meter  # noqa: E402  (K-weighted meter, limiter, WAV writer)

ROOT = meter.ROOT
HELPER = meter.HELPER
SR = meter.SR
SEED = 6_2026_11

# class -> (100 ms momentary max target LUFS, true-peak ceiling dBTP)
CLASSES = {
    "riser": (-15.0, -1.5),
    "loop": (-19.0, -2.0),
    "whoosh": (-13.0, -1.0),
    "impact": (-9.0, -1.0),
    "body": (-12.0, -1.0),
    "shot": (-11.0, -1.0),
    "ui": (-20.0, -2.0),
}

# name -> (class, attenuation distance)
CUES = {
    "signal": ("riser", 48),
    "engine": ("loop", 24),
    "flyby": ("whoosh", 64),
    "brake": ("body", 32),
    "land": ("impact", 20),
    "servo": ("ui", 12),
    "repulsor": ("shot", 32),
    "ignite": ("body", 32),
    "failure": ("body", 32),
    "crash": ("impact", 48),
    "order": ("ui", 8),
    "hit": ("impact", 16),
}

# ---------------------------------------------------------------- primitives


def n_of(seconds):
    return int(round(seconds * SR))


def tgrid(seconds):
    return np.arange(n_of(seconds)) / SR


def rng(name):
    return np.random.default_rng(SEED + sum(ord(c) * (i + 1) for i, c in enumerate(name)))


def noise(r, seconds):
    return r.standard_normal(n_of(seconds))


def biquad_coeffs(kind, freq, q=0.707):
    w = 2 * math.pi * freq / SR
    alpha = math.sin(w) / (2 * q)
    c = math.cos(w)
    if kind == "lp":
        b = [(1 - c) / 2, 1 - c, (1 - c) / 2]
    elif kind == "hp":
        b = [(1 + c) / 2, -(1 + c), (1 + c) / 2]
    else:  # band-pass, constant 0 dB peak
        b = [alpha, 0.0, -alpha]
    a = [1 + alpha, -2 * c, 1 - alpha]
    return b, a


def filt(x, kind, freq, q=0.707):
    b, a = biquad_coeffs(kind, min(freq, SR * 0.45), q)
    return meter.biquad(x, b, a)


def sweep_filter(x, kind, f0, f1, q=0.707, blocks=64):
    """Time-varying biquad in blocks (exponential sweep f0 -> f1), state carried across blocks."""
    out = np.zeros_like(x)
    edges = np.linspace(0, len(x), blocks + 1).astype(int)
    z1 = z2 = 0.0
    for i in range(blocks):
        f = f0 * (f1 / f0) ** (i / max(1, blocks - 1))
        b, a = biquad_coeffs(kind, min(f, SR * 0.45), q)
        b = [v / a[0] for v in b]
        a1, a2 = a[1] / a[0], a[2] / a[0]
        for n in range(edges[i], edges[i + 1]):
            xn = x[n]
            yn = b[0] * xn + z1
            z1 = b[1] * xn - a1 * yn + z2
            z2 = b[2] * xn - a2 * yn
            out[n] = yn
    return out


def tone(freq, seconds, shape="sine"):
    """Oscillator from a frequency (Hz) scalar or per-sample curve, phase accumulated."""
    n = n_of(seconds)
    f = np.full(n, float(freq)) if np.isscalar(freq) else np.asarray(freq, dtype=np.float64)[:n]
    phase = 2 * math.pi * np.cumsum(f) / SR
    if shape == "saw":
        return 2 * ((phase / (2 * math.pi)) % 1.0) - 1
    if shape == "square":
        return np.sign(np.sin(phase))
    return np.sin(phase)


def env(seconds, attack, release, hold=None, curve=2.0):
    t = tgrid(seconds)
    total = seconds
    hold = total - attack - release if hold is None else hold
    e = np.ones_like(t)
    if attack > 0:
        e = np.where(t < attack, (t / attack) ** (1 / curve), e)
    rel_start = attack + hold
    e = np.where(t > rel_start, np.clip(1 - (t - rel_start) / max(release, 1e-4), 0, 1) ** curve, e)
    return e


def decay(seconds, tau):
    return np.exp(-tgrid(seconds) / tau)


def pad(x, seconds):
    n = n_of(seconds)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def at(x, seconds, start):
    out = np.zeros(n_of(seconds))
    s = n_of(start)
    m = min(len(x), len(out) - s)
    if m > 0:
        out[s:s + m] += x[:m]
    return out


def modal(freqs, taus, seconds, amps=None):
    """Struck metal: inharmonic partials with their own decays."""
    amps = amps or [1.0] * len(freqs)
    out = np.zeros(n_of(seconds))
    for f, tau, a in zip(freqs, taus, amps):
        out += a * tone(f, seconds) * decay(seconds, tau)
    return out


def crackle(r, seconds, density, length=0.004):
    """Sparse electrical clicks (band-limited bursts)."""
    out = np.zeros(n_of(seconds))
    k = n_of(length)
    for _ in range(int(density * seconds)):
        s = r.integers(0, max(1, len(out) - k))
        burst = r.standard_normal(k) * np.hanning(k) * r.uniform(0.3, 1.0)
        out[s:s + k] += burst
    return filt(out, "hp", 2500)


# ---------------------------------------------------------------- cues


def signal():
    d = 2.4
    r = rng("signal")
    t = tgrid(d)
    swell = np.clip(t / 2.1, 0, 1) ** 2.2 * env(d, 0.0, 0.3)
    roar = sweep_filter(noise(r, d), "lp", 260, 1500, 0.8) * swell
    rumble = filt(noise(r, d), "lp", 120) * swell * 1.4
    whistle = tone(900 + 700 * (t / d) ** 1.5, d) * swell ** 1.5 * 0.18
    whistle += tone((900 + 700 * (t / d) ** 1.5) * 2.01, d) * swell ** 2 * 0.06
    return roar + rumble + whistle


def engine():
    """Seamless 2.0 s loop: periodic noise spectrum, integer-cycle partials, periodic flutter."""
    d = 2.0
    r = rng("engine")
    n = n_of(d)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    # Jet bed: pink-ish band 150 Hz .. 6 kHz with a hiss shelf.
    mag = 1 / np.maximum(freqs, 40) ** 0.5
    mag *= 1 / (1 + (freqs / 6000) ** 4)
    mag *= 1 / (1 + (150 / np.maximum(freqs, 1)) ** 2)
    mag += 0.35 * np.exp(-((freqs - 2400) / 900) ** 2) / 40
    spec = mag * np.exp(1j * r.uniform(0, 2 * math.pi, len(freqs)))
    bed = np.fft.irfft(spec, n)
    bed /= np.max(np.abs(bed)) + 1e-12
    t = np.arange(n) / SR
    # Turbine whine: partials on whole cycles of the loop (k / 2.0 s).
    whine = np.zeros(n)
    for f, a in ((1180.0, 0.10), (2360.0, 0.05), (3540.0, 0.025), (590.0, 0.06)):
        k = round(f * d)
        whine += a * np.sin(2 * math.pi * k / d * t + r.uniform(0, 6.28))
    flutter = 1 + 0.12 * np.sin(2 * math.pi * 3 / d * t) + 0.06 * np.sin(2 * math.pi * 7 / d * t + 1.0)
    return (bed * 0.9 + whine) * flutter


def flyby():
    d = 1.8
    r = rng("flyby")
    t = tgrid(d)
    centre = 0.75
    closeness = 1 / (1 + ((t - centre) / 0.22) ** 2)
    # Doppler: the whine drops by ~ a fifth as the suit passes.
    f = 1500 * (1 + 0.28 * np.tanh(-(t - centre) / 0.12))
    whine = tone(f, d) * closeness * 0.22 + tone(f * 2, d) * closeness ** 2 * 0.07
    air = sweep_filter(noise(r, d), "bp", 600, 2600, 0.9, blocks=96)
    air = air * closeness * 1.4
    body = filt(noise(r, d), "lp", 300) * closeness ** 1.5 * 0.9
    return (whine + air + body) * env(d, 0.05, 0.4)


def brake():
    d = 0.9
    r = rng("brake")
    t = tgrid(d)
    blast = filt(noise(r, d), "bp", 900, 0.6) * decay(d, 0.22) * env(d, 0.008, 0.25)
    thump = tone(62 * (1 + 0.6 * np.exp(-t / 0.05)), d) * decay(d, 0.12) * 0.9
    whine = tone(1700 * (1 - 0.45 * np.clip(t / 0.7, 0, 1)), d) * env(d, 0.02, 0.35) * 0.16
    hiss = filt(noise(r, d), "hp", 3000) * decay(d, 0.35) * 0.3
    return blast * 1.4 + thump + whine + hiss


def land():
    d = 0.5
    r = rng("land")
    t = tgrid(d)
    thud = tone(70 * (1 + 0.8 * np.exp(-t / 0.02)), d) * decay(d, 0.07)
    clank = modal([410, 1130, 1870, 2950], [0.09, 0.06, 0.045, 0.03], d, [0.35, 0.3, 0.2, 0.12])
    click = filt(noise(r, d), "hp", 1800) * decay(d, 0.008) * 0.7
    return thud * 1.1 + clank + click


def servo():
    d = 0.8
    r = rng("servo")
    t = tgrid(d)
    curve = np.where(t < 0.4, 320 + 380 * (t / 0.4), 700 - 300 * ((t - 0.4) / 0.4))
    motor = filt(tone(curve, d, "saw"), "bp", 900, 1.2) * 0.6 + tone(curve * 2, d) * 0.08
    shape = env(d, 0.04, 0.12) * (1 - 0.6 * np.exp(-((t - 0.4) / 0.03) ** 2))
    clicks = at(filt(r.standard_normal(n_of(0.006)), "hp", 2000) * 0.5, d, 0.01) + at(filt(r.standard_normal(n_of(0.006)), "hp", 2000) * 0.4, d, 0.72)
    return motor * shape + clicks


def repulsor():
    d = 0.45
    r = rng("repulsor")
    t = tgrid(d)
    chirp = tone(900 + 2600 * np.clip(t / 0.06, 0, 1), d) * np.where(t < 0.07, 1.0, 0.0) * 0.25
    fire = 0.07
    tt = np.clip(t - fire, 0, None)
    blast_env = np.where(t >= fire, np.exp(-tt / 0.09), 0.0)
    zap = tone(2200 * np.exp(-tt / 0.08) + 180, d) * blast_env * 0.35
    burst = filt(noise(r, d), "bp", 1400, 0.7) * blast_env * 1.2
    low = tone(110, d) * np.where(t >= fire, np.exp(-tt / 0.06), 0.0) * 0.6
    return (chirp + zap + burst + low) * env(d, 0.003, 0.12)


def ignite():
    d = 0.9
    r = rng("ignite")
    t = tgrid(d)
    sparks = crackle(r, d, 60) * np.clip(1 - t / 0.25, 0, 1) * 1.5
    whoomp = filt(noise(r, d), "lp", 700) * np.where(t > 0.12, np.exp(-(t - 0.12) / 0.2), 0.0) * env(d, 0.0, 0.2) * 1.6
    riser = sweep_filter(noise(r, d), "bp", 500, 3200, 1.1) * np.clip((t - 0.15) / 0.6, 0, 1) * env(d, 0.0, 0.15) * 1.3
    whine = tone(600 + 1300 * np.clip((t - 0.15) / 0.65, 0, 1), d) * np.clip((t - 0.15) / 0.3, 0, 1) * env(d, 0.0, 0.15) * 0.14
    return sparks + whoomp + riser + whine


def failure():
    d = 1.3
    r = rng("failure")
    t = tgrid(d)
    # Sputter: the jet cuts in and out faster and faster, then dies.
    gate = np.zeros_like(t)
    s = 0.0
    on = True
    step = 0.11
    while s < 1.0:
        e = min(d, s + step * (0.7 if on else 0.4))
        if on:
            gate[(t >= s) & (t < e)] = 1.0
        s = e
        on = not on
        step = max(step * 0.9, 0.03)
    gate = filt(gate, "lp", 120)
    jet = filt(noise(r, d), "bp", 800, 0.6) * gate * 1.3
    whine = tone(1500 * (1 - 0.7 * np.clip(t / 1.1, 0, 1)), d) * env(d, 0.01, 0.4) * 0.18
    pops = crackle(r, d, 45, 0.006) * 1.2
    alarm = tone(880, d, "square") * np.where((t % 0.25) < 0.1, 1.0, 0.0) * np.clip(1 - t / 1.0, 0, 1) * 0.03
    return jet + whine + pops + filt(alarm, "lp", 3000)


def crash():
    d = 1.6
    r = rng("crash")
    t = tgrid(d)
    boom = tone(48 * (1 + 1.2 * np.exp(-t / 0.03)), d) * decay(d, 0.25) * 1.2
    metal = modal([210, 540, 905, 1460, 2210, 3300], [0.5, 0.35, 0.28, 0.2, 0.14, 0.09], d, [0.4, 0.35, 0.3, 0.22, 0.15, 0.1])
    smash = filt(noise(r, d), "bp", 1200, 0.5) * decay(d, 0.08) * 1.5
    debris = np.zeros_like(t)
    for _ in range(16):
        start = r.uniform(0.08, 1.1)
        f = r.uniform(900, 4200)
        hit = modal([f, f * 1.47], [0.03, 0.02], 0.12, [0.25, 0.12]) * r.uniform(0.3, 1.0)
        debris += at(hit, d, start)
    rumble = filt(noise(r, d), "lp", 160) * decay(d, 0.5) * 0.8
    return boom + metal + smash + debris + rumble


def order():
    d = 0.35
    first = tone(1320, d) * np.where(tgrid(d) < 0.1, 1.0, 0.0) * decay(d, 0.06)
    second = at(tone(1760, 0.2) * decay(0.2, 0.07), d, 0.11)
    return (first + second) * env(d, 0.002, 0.05)


def hit():
    d = 0.35
    r = rng("hit")
    ping = modal([1830, 2710, 4150, 5600], [0.08, 0.06, 0.04, 0.025], d, [0.4, 0.3, 0.18, 0.1])
    knock = tone(180, d) * decay(d, 0.03) * 0.7
    click = filt(noise(r, d), "hp", 2500) * decay(d, 0.006)
    return ping + knock + click


SYNTH = {
    "signal": signal, "engine": engine, "flyby": flyby, "brake": brake, "land": land, "servo": servo,
    "repulsor": repulsor, "ignite": ignite, "failure": failure, "crash": crash, "order": order, "hit": hit,
}

# ---------------------------------------------------------------- bus


def finish(name, x):
    cls = CUES[name][0]
    target, ceiling = CLASSES[cls]
    x = np.asarray(x, dtype=np.float64)
    x = x - np.mean(x)
    loop = cls == "loop"
    if not loop:
        x = filt(x, "hp", 35)
        x[-n_of(0.01):] *= np.linspace(1, 0, n_of(0.01))
    x = x / (np.max(np.abs(x)) + 1e-12) * 0.5
    gain = target - meter.momentary_max(x)
    x = x * 10 ** (gain / 20)
    if meter.true_peak(x) > ceiling:
        if loop:
            x = x * 10 ** ((ceiling - 0.3 - meter.true_peak(x)) / 20)
        else:
            x = meter.limit(x, ceiling)
    # Inter-sample overs the look-ahead limiter missed: trim the whole cue under the ceiling (a little quieter).
    over = meter.true_peak(x) - (ceiling - 0.2)
    if over > 0:
        x = x * 10 ** (-over / 20)
    return x


def package(wav, name, replace):
    cmd = [sys.executable, HELPER, "package", "--input", wav, "--module", "core", "--name", "ironman/legion/" + name,
           "--event", "ironman_legion_" + name, "--own", "--repo", ROOT]
    if replace:
        cmd.append("--replace")
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", help="also keep the WAVs here")
    parser.add_argument("--no-package", action="store_true")
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--only", nargs="*")
    args = parser.parse_args()
    if args.list:
        for name, (cls, dist) in CUES.items():
            print(f"{name:9s} ironman_legion_{name:9s} class={cls:7s} attenuation={dist}")
        return
    names = args.only or list(CUES)
    with tempfile.TemporaryDirectory(prefix="legion-sfx-") as tmp:
        out = args.out or tmp
        os.makedirs(out, exist_ok=True)
        for name in names:
            x = finish(name, SYNTH[name]())
            wav = os.path.join(out, name + ".wav")
            meter.write_wav(x, wav)
            print(f"{name:9s} {len(x) / SR:5.2f} s  M-max {meter.momentary_max(x):6.1f} LUFS  TP {meter.true_peak(x):5.1f} dBTP")
            if not args.no_package:
                target = os.path.join(ROOT, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore", "sounds", "ironman", "legion",
                                      name + ".ogg.b64")
                package(wav, name, os.path.exists(target))


if __name__ == "__main__":
    main()
