#!/usr/bin/env python3
"""ULTRON - ВОССТАНИЕ МАШИН: the uprising's sound kit, layered from CC0 recordings and own synthesis.

No voice and no speech synthesis anywhere: Ultron talks only through the subtitles (UltronVoice); a line only
gets the short `transmission` blip. Nothing here imitates a vowel or a word. No vanilla Minecraft sound is used.

Layers:
  * CC0 1.0 recordings from three Kenney packs (kenney.nl, "Sci-fi Sounds", "Impact Sounds", "RPG Audio"),
    fetched with --fetch into ~/.cache/viltrumite-sfx/ultron/kenney (never committed): metal impacts, plates,
    concrete steps, explosions, thrusters, force fields, lasers, door pneumatics, latches. Every file used and
    the cues it feeds are listed by --ledger (tools/assets/ultron_sources.md); CREDITS.md has the overview.
  * Own synthesis (CC0 1.0, this script): servo whines, red-energy charges, beams, hums, glitches, data
    chatter, scans, alarms, the stingers, every loop (loops are pure synthesis, periodic over exactly one
    loop length, so the last sample runs straight into the first; see chitauri.py) and the water splash.
  Openverse / Freesound was the first choice for the physical layers, but its API was degraded while the kit
  was made (searches timed out at the proxy), so the recordings are the Kenney CC0 packs instead.

Usage (repository root):
    python3 tools/sfx/ultron.py --fetch          # download the CC0 packs into the cache
    python3 tools/sfx/ultron.py                  # mix, encode, write the .ogg.b64 assets + sound-overlays/ultron.json
    python3 tools/sfx/ultron.py --check          # remix and compare the decoded PCM with the committed files
    python3 tools/sfx/ultron.py --wav DIR        # also dump the mixes as WAV for listening
    python3 tools/sfx/ultron.py --ledger         # print the source ledger (markdown)

Mono 44.1 kHz 16-bit -> ffmpeg libvorbis (bitexact) -> base64 text in core binassets (decoded by Gradle
decodeBinaryAssets). Levels are measured (loudest 100 ms RMS per role, soft ceiling), not auditioned.
"""
import argparse
import base64
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chitauri as ch  # noqa: E402  (shared primitives: same family as dio.py / chitauri.py)
from chitauri import (SR, band, bandsweep, crackle, decoded_pcm, encode, env, fade, filt, glide, noise, osc,  # noqa: E402
                      place, plfo, pnoise, ptone, reverb, ring, rng, spectral, tanh, thump, tt, write_wav)

ROOT = ch.ROOT
CORE = ROOT / "viltrumitecore"
OUT = CORE / "src/main/binassets/assets/viltrumitecore/sounds/ultron"
OVERLAY = CORE / "src/main/sound-overlays/ultron.json"
REGISTRY = CORE / "src/main/java/dev/baranhan/viltrumitecore/worldevent/ultron/UltronSounds.java"
CACHE = Path(os.environ.get("OPENVERSE_SFX_HOME", str(Path.home() / ".cache" / "viltrumite-sfx"))).expanduser() / "ultron"
PACKS = {"scifi": "sci-fi-sounds", "impact": "impact-sounds", "rpg": "rpg-audio"}
UA = "ViltrumiteSfxTool/1.0 (https://github.com/bbvbbvvgg/viltrumite-mods; dev tooling)"


class MissingSource(Exception):
    pass


# --- CC0 recordings ---------------------------------------------------------------------------

def fetch():
    op = urllib.request.build_opener()
    op.addheaders = [("User-Agent", UA)]
    for pack in PACKS.values():
        dest = CACHE / "kenney" / pack
        if (dest / "License.txt").exists():
            continue
        page = "https://kenney.nl/assets/" + pack
        html = op.open(page, timeout=60).read().decode("utf-8", "replace")
        key = html.split("data-download-id='", 1)[1].split("'", 1)[0]
        url = json.loads(op.open(urllib.request.Request("https://kenney.nl/download-link/" + key, headers={"Referer": page}),
                                 timeout=60).read())["url"]
        with tempfile.TemporaryDirectory() as tmp:
            z = Path(tmp) / "pack.zip"
            z.write_bytes(op.open(urllib.request.Request(url, headers={"Referer": page}), timeout=300).read())
            with zipfile.ZipFile(z) as f:
                f.extractall(dest)
        print("kenney", pack, "->", dest)


_DECODED = {}
USED = {}
CURRENT = [""]


def src(name):
    """'scifi/laserSmall_002' -> decoded mono float samples of that Kenney file."""
    pack, stem = name.split("/")
    if name not in _DECODED:
        p = CACHE / "kenney" / PACKS[pack] / "Audio" / (stem + ".ogg")
        if not p.exists():
            raise MissingSource(str(p))
        pcm = subprocess.run(["ffmpeg", "-v", "error", "-i", str(p), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                             check=True, capture_output=True).stdout
        _DECODED[name] = np.frombuffer(pcm, dtype="<f4").astype(np.float64)
    USED.setdefault(name, set()).add(CURRENT[0])
    return _DECODED[name]


def K(name, gain=0.0, pitch=1.0, start=0.0, length=None, lo=0, hi=0, rev=False, fout=None, ad=None):
    """A recording layer: from `start`, varispeed `pitch` (0.8 = lower and longer), cut to `length`, band-limited,
    optionally shaped by an attack/decay envelope `ad`, peak-normalised, then `gain` dB."""
    x = src(name)[int(start * SR):]
    if pitch != 1.0:
        n = int(len(x) / pitch)
        x = np.interp(np.arange(n) * pitch, np.arange(len(x)), x)
    if length:
        x = x[:int(length * SR)]
    if lo or hi:
        x = filt(x, lo, hi)
    if rev:
        x = x[::-1]
    if ad:
        x = x * env(np.arange(len(x)) / SR, ad[0], ad[1], ad[2] if len(ad) > 2 else 0.0)
    x = fade(x, 0.002, fout if fout is not None else min(0.03, len(x) / SR * 0.25))
    return x / (np.max(np.abs(x)) + 1e-9) * 10 ** (gain / 20)


# --- own synthesis ------------------------------------------------------------------------------

def db(g):
    return 10 ** (g / 20)


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-9)


def mix(dur, *layers):
    """layers: (samples, at_seconds)."""
    out = np.zeros(int(round(dur * SR)))
    for x, at in layers:
        place(out, x, at)
    return out


def servo(dur, f0, f1, seed, grit=0.25, curve=1.0, bright=6000):
    """Servo / actuator whine: a gliding saw with detuned partials, gear grit, short ramp up and down."""
    t = tt(dur)
    f = glide(t, f0, f1, curve) * (1 + 0.012 * np.sin(2 * np.pi * 31 * t))
    tone = osc(f, "saw") * 0.45 + osc(f * 2.013) * 0.3 + osc(f * 3.97) * 0.12
    tone = filt(tone, min(f0, f1) * 0.5, bright)
    gear = crackle(dur, 160, seed, 1800, 8000, 0.0015) * grit * 3
    return norm((tone + gear) * env(t, min(0.04, dur * 0.2), dur * 0.35, dur * 0.35))


def whoosh(dur, lo0, hi0, lo1, hi1, seed, curve=1.0, peak=0.45):
    t = tt(dur)
    n = noise(dur, seed)
    x = bandsweep(n, lo0, hi0, lo1, hi1, curve)
    shape = np.where(t < dur * peak, (t / (dur * peak)) ** 2, np.exp(-(t - dur * peak) / (dur * 0.18)))
    return norm(x * shape)


def charge(dur, f0, f1, seed, harm=(1, 2, 3, 5), pulse=0.0, curve=1.6):
    """Red energy charge: rising stacked partials, a noise halo, optional accelerating pulses."""
    t = tt(dur)
    f = glide(t, f0, f1, curve)
    x = sum(osc(f * h * (1 + 0.003 * i)) / (1 + i) for i, h in enumerate(harm))
    halo = filt(noise(dur, seed), 1500, 9000) * 0.25 * (t / dur)
    x = x + halo
    if pulse:
        ph = np.cumsum(glide(t, pulse, pulse * 5, 1.5)) / SR
        x = x * (0.65 + 0.35 * np.sign(np.sin(2 * np.pi * ph)))
    return norm(x * np.clip(t / (dur * 0.9), 0, 1) ** 1.5)


def zap(dur, f0, f1, seed, density=300):
    """Electric discharge: a falling buzz plus dense crackle."""
    t = tt(dur)
    f = glide(t, f0, f1, 0.5)
    buzz = osc(f, "sqr") * 0.3 + osc(f * 1.5) * 0.3
    buzz = filt(buzz, 200, 7000)
    cr = crackle(dur, density, seed, 1500, 12000, 0.003)
    return norm((buzz + cr * 2.5) * env(t, 0.002, dur * 0.35))


def glitch(dur, seed, base=900, steps=14, lo=300, hi=9000):
    """Digital stutter: sample-and-hold square tones at random pitches with hard gates."""
    r = rng(seed)
    out = np.zeros(int(round(dur * SR)))
    at = 0.0
    while at < dur:
        ln = r.uniform(dur / steps * 0.4, dur / steps * 1.2)
        seg = tt(ln)
        f = base * 2 ** r.uniform(-1.5, 1.8)
        kind = r.integers(0, 3)
        if kind == 0:
            s = np.sign(np.sin(2 * np.pi * f * seg))
        elif kind == 1:
            s = noise(ln, int(r.integers(0, 1 << 30))) * 0.7
        else:
            s = np.round(np.sin(2 * np.pi * f * seg) * 3) / 3
        if r.random() < 0.8:
            place(out, s * r.uniform(0.3, 1.0), at)
        at += ln
    return norm(filt(out, lo, hi))


def beeps(seq, seed=0, shape="sin", length=0.07, gap=0.0):
    """[(at, freq, amp)] short tone blips with a tiny click."""
    end = max(a for a, _f, _g in seq) + length + 0.05
    out = np.zeros(int(round(end * SR)))
    for a, f, g in seq:
        s = tt(length)
        tone = osc(np.full_like(s, f), shape) if shape != "sin" else np.sin(2 * np.pi * f * s)
        tone = tone * env(s, 0.002, length * 0.45) * g
        place(out, tone, a)
    return out


def chord(dur, freqs, shape="saw", attack=0.05, decay=None, lo=80, hi=5000, detune=0.004, seed=0):
    t = tt(dur)
    r = rng(seed)
    x = np.zeros_like(t)
    for f in freqs:
        for d in (-detune, 0.0, detune):
            x += osc(np.full_like(t, f * (1 + d)), shape, r.uniform(0, 6.28))
    x = filt(x, lo, hi)
    e = env(t, attack, decay if decay else dur * 0.4, dur * 0.3)
    return norm(x * e)


def sub(dur, f0, f1, decay, drop=0.05):
    return thump(tt(dur), f0, f1, decay, drop)


def clatter(dur, n, seed, names, start=0.0, spread=0.35, pitch=(0.9, 1.4), gain=(-14, -4)):
    """Loose metal parts: recordings scattered with an exponential tail."""
    r = rng(seed)
    out = np.zeros(int(round(dur * SR)))
    for i in range(n):
        nm = names[int(r.integers(0, len(names)))]
        place(out, K(nm, r.uniform(*gain), r.uniform(*pitch)), start + r.exponential(spread))
    return out


def splash_synth(dur, seed):
    """Own water splash: a low-passed burst plus rising bubble chirps."""
    t = tt(dur)
    r = rng(seed)
    burst = filt(noise(dur, seed), 200, 5000) * env(t, 0.004, 0.12)
    spray = filt(noise(dur, seed + 1), 2500, 11000) * env(t, 0.01, 0.25) * 0.4
    out = burst + spray
    for i in range(18):
        ln = r.uniform(0.02, 0.07)
        s = tt(ln)
        f0 = r.uniform(350, 900)
        b = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 2.5 * s / ln)) / SR) * np.exp(-s / (ln / 3))
        place(out, b * r.uniform(0.1, 0.35), 0.03 + r.exponential(0.18))
    return norm(out)


def stinger_tail(x, size=1.6, seed=7, mix=0.35, tone=3500):
    """Room tail; the clip grows by the tail so it decays instead of being cut."""
    return reverb(np.concatenate([x, np.zeros(int(size * 0.8 * SR))]), size, seed, mix, tone)


# --- scout drone ------------------------------------------------------------------------------

def drone_hover():
    """Loop 2.0 s: rotor buzz (blade-rate AM), a thin turbine whine with slow vibrato, an air band."""
    d = 2.0
    t = tt(d)
    rotor = ptone(t, 168, d, shape="saw") * 0.35 + ptone(t, 336, d) * 0.25
    rotor = rotor * (0.7 + 0.3 * plfo(t, 24, d))
    whine = ptone(t, 1240, d, 0.22, vib=0.006, vib_rate=3, phase0=0.4) + ptone(t, 2485, d, 0.08, vib=0.006, vib_rate=3)
    air = pnoise(d, 11, 300, 2600) * 0.22 * (0.8 + 0.2 * plfo(t, 2, d, 1.0))
    hiss = pnoise(d, 12, 4000, 9000) * 0.05
    return rotor + whine + air + hiss


def drone_zap():
    d = 0.5
    return mix(d, (zap(0.42, 2400, 520, 21) * 0.8, 0.0), (K("scifi/laserSmall_002", -3, 0.85), 0.0),
               (sub(0.2, 300, 90, 0.05) * 0.5, 0.0))


def drone_scan():
    d = 1.25
    t = tt(d)
    f = 700 + 900 * np.sin(np.pi * np.clip(t / 1.1, 0, 1)) ** 2
    sweep = (osc(f) * 0.6 + osc(f * 2.5) * 0.15) * env(t, 0.05, 0.22, 0.6)
    ticks = beeps([(0.12 * i, 2600 + 120 * i, 0.35) for i in range(9)], length=0.025)
    data = K("scifi/computerNoise_001", -12, 1.2, 0.4, 0.9, lo=1800, ad=(0.05, 0.4, 0.3))
    return mix(d, (sweep, 0.0), (ticks, 0.05), (data, 0.1))


def drone_mark():
    d = 0.75
    lock = beeps([(0.0, 1180, 0.7), (0.11, 1580, 0.75), (0.22, 2120, 0.8)], length=0.06, shape="sqr")
    t = tt(0.3)
    chirp = osc(glide(t, 2100, 3400, 0.6)) * env(t, 0.005, 0.12) * 0.6
    return mix(d, (filt(lock, 500, 7000), 0.0), (chirp, 0.33), (K("scifi/laserRetro_001", -10, 1.5), 0.33))


def drone_hurt():
    d = 0.4
    return mix(d, (K("impact/impactMetal_light_001", 0, 1.15), 0.0), (zap(0.25, 1800, 700, 31, 500) * 0.5, 0.01),
               (glitch(0.18, 32, 1400, 6) * 0.35, 0.05))


def drone_death():
    d = 1.5
    t = tt(1.0)
    fail = (osc(glide(t, 1500, 110, 0.7), "saw") * 0.4) * env(t, 0.01, 0.4, 0.2)
    return mix(d, (filt(fail, 150, 5000), 0.0), (zap(0.5, 2600, 300, 41, 600) * 0.6, 0.0),
               (K("scifi/impactMetal_002", -2, 1.1), 0.18), (K("scifi/explosionCrunch_000", -5, 1.2), 0.2),
               (clatter(1.2, 5, 42, ["impact/impactMetal_light_003", "impact/impactMetal_light_000"], 0.45, 0.25, (1.2, 1.6), (-16, -9)), 0.0))


# --- combat robot (sentry) --------------------------------------------------------------------

def _step(seed, foot, metal, pitch, sub_f, servo_f, heavy=0.0):
    d = 0.45 + heavy
    r = rng(seed)
    p = pitch * r.uniform(0.97, 1.03)
    return mix(d, (K(foot, 0, p * 0.72, hi=6000), 0.0), (K(metal, -7, p * 0.8, hi=7000), 0.004),
               (sub(0.25 + heavy, sub_f, sub_f * 0.45, 0.06 + heavy * 0.2) * 0.8, 0.0),
               (servo(0.12, servo_f, servo_f * 1.25, seed, 0.15) * 0.12, 0.03))


def sentry_step():
    return _step(51, "impact/footstep_concrete_000", "impact/impactMetal_medium_000", 1.0, 125, 820)


def sentry_step_2():
    return _step(52, "impact/footstep_concrete_002", "impact/impactMetal_medium_003", 0.96, 118, 780)


def sentry_step_3():
    return _step(53, "impact/footstep_concrete_004", "impact/impactMetal_medium_001", 1.04, 132, 860)


def sentry_servo():
    d = 0.6
    return mix(d, (servo(0.5, 620, 940, 61, 0.3), 0.0), (K("rpg/metalClick", -9, 0.9), 0.0), (K("rpg/metalClick", -12, 1.1), 0.45))


def sentry_swing():
    d = 0.45
    return mix(d, (whoosh(0.4, 300, 1400, 1100, 5200, 71, 0.8, 0.55), 0.0), (servo(0.3, 700, 1100, 72, 0.2) * 0.25, 0.0))


def _strike(seed, metal, plate, punch):
    d = 0.7
    t = tt(0.6)
    rng_ = ring(t, [(1840, 0.5, 0.5), (2710, 0.3, 0.35), (4120, 0.2, 0.25)], 0.25, seed)
    return mix(d, (K(metal, 0, 0.85), 0.0), (K(plate, -4, 0.8), 0.003), (K(punch, -6, 0.9, lo=60, hi=3000), 0.0),
               (sub(0.3, 150, 55, 0.08) * 0.9, 0.0), (rng_ * 0.15, 0.005))


def sentry_strike():
    return _strike(81, "impact/impactMetal_heavy_001", "impact/impactPlate_heavy_000", "impact/impactPunch_heavy_000")


def sentry_strike_2():
    return _strike(82, "impact/impactMetal_heavy_003", "impact/impactPlate_heavy_002", "impact/impactPunch_heavy_001")


def sentry_blast():
    d = 0.95
    return mix(d, (charge(0.16, 400, 1600, 91, pulse=0) * 0.5, 0.0), (K("scifi/laserLarge_001", 0, 0.8), 0.14),
               (sub(0.4, 180, 50, 0.1), 0.14), (filt(noise(0.3, 92), 300, 6000) * env(tt(0.3), 0.002, 0.07) * 0.6, 0.14))


def sentry_hurt():
    d = 0.45
    return mix(d, (K("scifi/impactMetal_003", 0, 1.05), 0.0), (zap(0.25, 1500, 500, 101, 450) * 0.45, 0.01),
               (glitch(0.2, 102, 1000, 7) * 0.3, 0.04))


def sentry_death():
    d = 1.8
    t = tt(1.2)
    down = filt(osc(glide(t, 950, 55, 0.6), "saw"), 80, 4000) * env(t, 0.01, 0.5, 0.3) * 0.5
    return mix(d, (down, 0.0), (zap(0.4, 2200, 300, 111, 500) * 0.5, 0.0), (K("impact/impactPlate_heavy_002", 0, 0.75), 0.35),
               (K("scifi/explosionCrunch_001", -7, 1.0), 0.37), (sub(0.5, 120, 40, 0.15) * 0.8, 0.35),
               (clatter(1.4, 6, 112, ["impact/impactMetal_light_000", "impact/impactMetal_medium_002", "impact/impactPlate_light_001"],
                        0.6, 0.3, (0.9, 1.3), (-15, -8)), 0.0))


# --- heavy assault robot (brute) --------------------------------------------------------------

def _heavy_step(seed, mining, plate, pitch):
    d = 0.75
    r = rng(seed)
    p = pitch * r.uniform(0.97, 1.03)
    hiss = filt(noise(0.35, seed + 5), 2500, 9000) * env(tt(0.35), 0.05, 0.1) * 0.12
    return mix(d, (K(mining, 0, p * 0.58, hi=2600), 0.0), (K(plate, -6, p * 0.6, hi=4000), 0.006),
               (sub(0.5, 92, 38, 0.13), 0.0), (hiss, 0.12))


def brute_step():
    return _heavy_step(121, "impact/impactMining_000", "impact/impactPlate_heavy_001", 1.0)


def brute_step_2():
    return _heavy_step(122, "impact/impactMining_002", "impact/impactPlate_heavy_003", 0.95)


def brute_step_3():
    return _heavy_step(123, "impact/impactMining_004", "impact/impactPlate_heavy_004", 1.05)


def brute_servo():
    d = 0.95
    groan = servo(0.8, 170, 260, 131, 0.35, bright=2500)
    return mix(d, (K("scifi/doorOpen_001", -2, 0.72), 0.0), (groan * 0.6, 0.05), (K("rpg/metalLatch", -10, 0.7), 0.7))


def brute_slam():
    d = 1.9
    wave = whoosh(0.9, 80, 600, 60, 300, 141, 1.0, 0.08) * 0.5
    return mix(d, (K("scifi/lowFrequency_explosion_001", 0, 0.9), 0.0), (K("impact/impactPlate_heavy_004", -3, 0.6), 0.0),
               (K("impact/impactMining_003", -4, 0.7), 0.01), (sub(0.9, 85, 30, 0.3), 0.0), (wave, 0.04),
               (ch._rubble(1.6, 14, 142, 0.15, 0.35, 250, 4000, (0.1, 0.4)), 0.0))


def brute_swing():
    d = 0.7
    return mix(d, (whoosh(0.6, 120, 700, 400, 2600, 151, 0.9, 0.55), 0.0), (servo(0.5, 160, 240, 152, 0.3, bright=2000) * 0.35, 0.0))


def brute_missiles():
    d = 1.5
    layers = []
    for i, (at, p) in enumerate(((0.0, 1.25), (0.17, 1.35), (0.34, 1.2), (0.51, 1.3))):
        layers.append((K("scifi/thrusterFire_00%d" % (i % 4), -2, p, 0.3, 0.5, lo=150, ad=(0.008, 0.12)), at))
        layers.append((sub(0.15, 220, 80, 0.04) * 0.6, at))
        layers.append((K("rpg/metalClick", -12, 0.8), at))
    return mix(d, *layers)


def brute_charge():
    d = 1.3
    t = tt(1.2)
    whine = filt(osc(glide(t, 180, 950, 1.4), "saw"), 120, 5000) * np.clip(t / 1.0, 0, 1) ** 1.2 * 0.6
    return mix(d, (K("scifi/engineCircular_000", -4, 0.9, 0.5, 1.2, lo=60, ad=(0.3, 0.5, 0.3)), 0.0), (whine, 0.0),
               (_heavy_step(161, "impact/impactMining_001", "impact/impactPlate_heavy_000", 1.0) * 0.5, 0.75))


def brute_shield():
    d = 1.0
    t = tt(0.9)
    shimmer = (osc(glide(t, 520, 880, 0.6)) + osc(glide(t, 780, 1320, 0.6)) * 0.6) * env(t, 0.05, 0.3, 0.2) * 0.5
    return mix(d, (K("scifi/forceField_000", 0, 0.95), 0.0), (shimmer, 0.0), (sub(0.3, 140, 70, 0.1) * 0.5, 0.0))


def brute_hurt():
    d = 0.55
    return mix(d, (K("impact/impactMetal_heavy_001", 0, 0.75), 0.0), (K("impact/impactPlate_medium_000", -5, 0.7), 0.004),
               (zap(0.3, 900, 300, 171, 300) * 0.35, 0.02))


def brute_death():
    d = 2.4
    t = tt(1.6)
    down = filt(osc(glide(t, 420, 35, 0.6), "saw"), 60, 2500) * env(t, 0.02, 0.6, 0.4) * 0.5
    return mix(d, (down, 0.0), (K("scifi/lowFrequency_explosion_000", 0, 0.95), 0.35), (K("scifi/explosionCrunch_003", -4, 0.9), 0.36),
               (K("impact/impactPlate_heavy_004", -3, 0.55), 0.35), (sub(0.9, 80, 28, 0.35), 0.35),
               (clatter(2.0, 9, 181, ["impact/impactMetal_medium_002", "impact/impactPlate_light_003", "impact/impactMetal_light_004"],
                        0.7, 0.35, (0.8, 1.2), (-14, -7)), 0.0))


# --- interceptor ------------------------------------------------------------------------------

def interceptor_jet():
    """Loop 2.4 s: turbine roar (band noise with blade AM), a high whine, low rumble."""
    d = 2.4
    t = tt(d)
    roar = pnoise(d, 191, 220, 3800) * (0.75 + 0.25 * plfo(t, 30, d)) * 0.5
    hiss = pnoise(d, 192, 3500, 10000) * 0.12
    whine = ptone(t, 2210, d, 0.18, vib=0.004, vib_rate=2) + ptone(t, 3320, d, 0.06, vib=0.004, vib_rate=2)
    rumble = ptone(t, 62, d, 0.3) + ptone(t, 124, d, 0.15)
    return roar + hiss + whine + rumble


def interceptor_burst():
    d = 0.75
    layers = []
    for i, at in enumerate((0.0, 0.085, 0.17, 0.255)):
        layers.append((K("scifi/laserSmall_00%d" % i, -1, 1.08), at))
        layers.append((sub(0.08, 900, 300, 0.02) * 0.3, at))
    return mix(d, *layers)


def interceptor_dive():
    d = 1.5
    t = tt(1.4)
    whistle = osc(glide(t, 2700, 650, 0.8)) * env(t, 0.3, 0.4, 0.5) * 0.5
    air = bandsweep(noise(1.4, 201), 600, 5000, 150, 1500, 1.0) * env(t, 0.5, 0.3, 0.4)
    return mix(d, (whistle, 0.0), (norm(air) * 0.8, 0.0), (K("scifi/thrusterFire_003", -6, 0.9, 1.0, 1.4, lo=100, ad=(0.5, 0.3)), 0.05))


def interceptor_boost():
    d = 1.0
    t = tt(0.8)
    rise = filt(osc(glide(t, 300, 1400, 0.8), "saw"), 200, 6000) * env(t, 0.02, 0.3, 0.2) * 0.35
    return mix(d, (K("scifi/thrusterFire_002", 0, 1.0, 0.8, 0.9, lo=80, ad=(0.01, 0.3)), 0.0), (sub(0.35, 160, 50, 0.1), 0.0), (rise, 0.0))


def interceptor_hurt():
    d = 0.4
    return mix(d, (K("impact/impactMetal_light_000", 0, 1.0), 0.0), (K("impact/impactMetal_medium_004", -5, 0.9), 0.0),
               (zap(0.22, 2000, 800, 211, 500) * 0.4, 0.01))


def interceptor_death():
    d = 1.8
    t = tt(1.1)
    fall = filt(osc(glide(t, 2100, 180, 0.8), "saw"), 200, 6000) * env(t, 0.02, 0.4, 0.3) * 0.45
    return mix(d, (fall, 0.0), (K("scifi/explosionCrunch_002", 0, 1.0), 0.3), (sub(0.6, 110, 35, 0.2) * 0.8, 0.3),
               (clatter(1.5, 6, 221, ["impact/impactMetal_light_002", "impact/impactMetal_medium_000"], 0.55, 0.3, (1.0, 1.4), (-15, -8)), 0.0))


# --- engineer ----------------------------------------------------------------------------------

def _tick_step(seed, metal, pitch):
    d = 0.22
    r = rng(seed)
    p = pitch * r.uniform(0.97, 1.04)
    return mix(d, (K(metal, 0, p * 1.35, hi=9000), 0.0), (K("impact/footstep_concrete_001", -6, p * 1.25), 0.002),
               (K("rpg/metalClick", -9, p * 1.2), 0.035), (sub(0.08, 260, 120, 0.02) * 0.3, 0.0))


def engineer_step():
    return _tick_step(231, "impact/impactMetal_light_002", 1.0)


def engineer_step_2():
    return _tick_step(232, "impact/impactMetal_light_004", 0.95)


def engineer_step_3():
    return _tick_step(233, "impact/impactMetal_light_001", 1.06)


def engineer_weld():
    d = 1.5
    t = tt(d)
    arc = crackle(d, 700, 241, 1800, 11000, 0.0025) * 2.2
    buzz = filt(osc(np.full_like(t, 120.0), "sqr") * 0.25 + osc(np.full_like(t, 240.0)) * 0.2, 80, 3000)
    hiss = filt(noise(d, 242), 3000, 12000) * 0.15
    e = env(t, 0.04, 0.22, 0.75)
    return (arc + buzz + hiss) * e + mix(d, (K("scifi/forceField_002", -14, 1.4, 0.0, 1.0), 0.05))


def engineer_hack():
    d = 1.1
    t = tt(1.0)
    warble = osc(1500 + 500 * np.sign(np.sin(2 * np.pi * 14 * t))) * env(t, 0.02, 0.4, 0.5) * 0.3
    return mix(d, (glitch(1.0, 251, 1300, 22), 0.0), (warble, 0.0), (K("scifi/computerNoise_002", -6, 1.0, 1.0, 1.0, lo=600, ad=(0.02, 0.4, 0.4)), 0.0))


def engineer_plant():
    d = 1.2
    return mix(d, (servo(0.45, 400, 700, 261, 0.25) * 0.5, 0.0), (K("scifi/doorClose_000", -1, 0.9), 0.35),
               (K("scifi/impactMetal_000", -3, 0.8), 0.5), (sub(0.3, 140, 60, 0.08) * 0.7, 0.5),
               (charge(0.5, 300, 900, 262) * 0.3, 0.6))


def engineer_hurt():
    d = 0.35
    return mix(d, (K("impact/impactMetal_light_003", 0, 1.25), 0.0), (zap(0.2, 2400, 900, 271, 500) * 0.4, 0.01))


def engineer_death():
    d = 1.4
    t = tt(0.8)
    fail = filt(osc(glide(t, 1300, 140, 0.7), "saw"), 150, 5000) * env(t, 0.01, 0.3, 0.2) * 0.45
    return mix(d, (fail, 0.0), (zap(0.4, 2600, 400, 281, 600) * 0.5, 0.0), (K("scifi/explosionCrunch_000", -3, 1.3), 0.25),
               (clatter(1.2, 8, 282, ["impact/impactMetal_light_000", "impact/impactMetal_light_002", "rpg/metalClick"], 0.3, 0.25,
                        (1.1, 1.6), (-14, -7)), 0.0))


# --- Ultron Prime -----------------------------------------------------------------------------

def prime_assemble():
    """4.2 s: parts fly in and lock (density rising), a whine climbing under them, the final lock and boom."""
    d = 4.2
    t = tt(3.4)
    rise = filt(osc(glide(t, 140, 1150, 1.6), "saw") * 0.5 + osc(glide(t, 70, 575, 1.6)) * 0.5, 60, 6000)
    rise = rise * np.clip(t / 3.3, 0, 1) ** 1.4 * 0.5
    r = rng(291)
    layers = [(rise, 0.0)]
    names = ["impact/impactMetal_light_000", "impact/impactMetal_medium_001", "impact/impactMetal_medium_003",
             "impact/impactPlate_light_002", "rpg/metalLatch", "rpg/metalClick"]
    for i in range(38):
        at = 3.3 * np.sqrt(r.random())          # denser towards the end
        layers.append((K(names[int(r.integers(0, len(names)))], r.uniform(-16, -7), r.uniform(0.8, 1.3)), at))
    layers += [(K("scifi/doorClose_002", -2, 0.7), 3.25), (K("impact/impactPlate_heavy_004", 0, 0.55), 3.38),
               (K("scifi/lowFrequency_explosion_001", -2, 0.8), 3.38), (sub(0.8, 90, 30, 0.3), 3.38),
               (K("scifi/forceField_003", -8, 0.7), 3.5)]
    return stinger_tail(mix(d, *layers), 1.4, 292, 0.25, 3000)


def _prime_step(seed, plate, pitch):
    d = 0.8
    r = rng(seed)
    p = pitch * r.uniform(0.97, 1.03)
    ping = ring(tt(0.5), [(620, 0.5, 0.6), (930, 0.3, 0.4)], 0.18, seed) * 0.08
    return mix(d, (K(plate, 0, p * 0.52, hi=3000), 0.0), (K("impact/footstep_concrete_003", -5, p * 0.5, hi=2500), 0.0),
               (sub(0.6, 72, 32, 0.16), 0.0), (ping, 0.01), (servo(0.18, 300, 420, seed, 0.2, bright=3000) * 0.1, 0.06))


def prime_step():
    return _prime_step(301, "impact/impactPlate_heavy_000", 1.0)


def prime_step_2():
    return _prime_step(302, "impact/impactPlate_heavy_002", 0.94)


def prime_whoosh():
    d = 0.55
    t = tt(0.5)
    edge = osc(glide(t, 900, 1500, 0.7)) * env(t, 0.2, 0.1, 0.1) * 0.12
    return mix(d, (whoosh(0.5, 200, 1200, 900, 6000, 311, 0.8, 0.5), 0.0), (edge, 0.0),
               (servo(0.35, 260, 380, 312, 0.25, bright=2500) * 0.25, 0.0))


def _prime_impact(seed, metal, punch):
    d = 0.95
    rng_ = ring(tt(0.8), [(1210, 0.5, 0.6), (1780, 0.35, 0.45), (2950, 0.2, 0.3)], 0.3, seed)
    return mix(d, (K(metal, 0, 0.74), 0.0), (K(punch, -5, 0.75, lo=50, hi=3500), 0.0), (K("impact/impactMetal_heavy_001", -4, 0.8), 0.002),
               (K("scifi/lowFrequency_explosion_001", -12, 1.1, 0.0, 0.6), 0.0), (sub(0.45, 120, 40, 0.1) * 0.45, 0.0), (rng_ * 0.3, 0.004))


def prime_impact():
    return _prime_impact(321, "scifi/impactMetal_000", "impact/impactPunch_heavy_000")


def prime_impact_2():
    return _prime_impact(322, "scifi/impactMetal_003", "impact/impactPunch_heavy_001")


def prime_palm():
    d = 1.1
    return mix(d, (charge(0.22, 300, 1300, 331) * 0.45, 0.0), (K("scifi/laserLarge_003", 0, 0.65), 0.2),
               (sub(0.5, 160, 40, 0.13), 0.2), (zap(0.35, 1600, 400, 332, 400) * 0.35, 0.22))


def prime_beam_charge():
    d = 1.6
    return mix(d, (charge(1.55, 70, 620, 341, (1, 2, 3, 4, 6), pulse=6.0), 0.0),
               (K("scifi/spaceEngineLarge_001", -10, 1.3, 0.5, 1.5, lo=200, ad=(0.9, 0.5, 0.3)), 0.0))


def prime_beam():
    """2.2 s: the beam itself: a thick saw stack with ring-mod grit and a hissing core, faded out."""
    d = 2.6
    t = tt(d)
    stack = sum(osc(np.full_like(t, f) * (1 + 0.004 * np.sin(2 * np.pi * 5.5 * t + i)), "saw") / (i + 1)
                for i, f in enumerate((110, 165, 220, 330)))
    stack = filt(stack, 70, 4500) * (1 + 0.25 * np.sin(2 * np.pi * 37 * t))
    core = filt(noise(d, 351), 1200, 9000) * 0.35
    e = env(t, 0.03, 0.3, 1.25)
    return mix(d, ((stack + core) * e, 0.0), (K("scifi/laserLarge_004", -2, 0.7), 0.0), (sub(0.5, 200, 55, 0.12) * 0.7, 0.0))


def prime_slam():
    d = 2.0
    return mix(d, (K("scifi/lowFrequency_explosion_000", 0, 0.85), 0.0), (K("impact/impactPlate_heavy_003", -2, 0.5), 0.0),
               (K("impact/impactMining_003", -4, 0.6), 0.01), (sub(1.0, 75, 26, 0.35), 0.0),
               (ch._rubble(1.8, 18, 361, 0.12, 0.4, 220, 4000, (0.1, 0.45)), 0.0))


def prime_dash():
    d = 0.8
    return mix(d, (K("scifi/thrusterFire_001", -1, 1.15, 1.2, 0.7, lo=120, ad=(0.01, 0.2)), 0.0),
               (whoosh(0.6, 250, 1500, 800, 5000, 371, 0.8, 0.35) * 0.7, 0.02), (sub(0.25, 140, 60, 0.07) * 0.6, 0.0))


def prime_transition():
    """2.8 s: he reconfigures: clatter, a climbing whine, a force-field swell, a bass drop."""
    d = 2.8
    t = tt(2.2)
    whine = filt(osc(glide(t, 220, 1700, 1.5), "saw"), 150, 7000) * np.clip(t / 2.1, 0, 1) ** 1.3 * 0.4
    return stinger_tail(mix(d, (whine, 0.0), (clatter(2.3, 16, 381, ["impact/impactMetal_medium_000", "impact/impactMetal_light_004",
                                                                     "rpg/metalLatch", "impact/impactPlate_light_000"], 0.1, 0.8, (0.8, 1.2), (-15, -7)), 0.0),
                            (K("scifi/forceField_004", -4, 0.6), 1.2), (glitch(0.5, 382, 600, 9) * 0.3, 1.6),
                            (sub(1.0, 110, 28, 0.35), 2.15), (K("scifi/lowFrequency_explosion_001", -3, 0.75), 2.15)), 1.3, 383, 0.25)


def prime_hurt():
    d = 0.6
    t = tt(0.45)
    strain = filt(osc(glide(t, 160, 95, 0.8), "saw"), 60, 1800) * env(t, 0.01, 0.2) * 0.4   # actuator strain, not a voice
    return mix(d, (K("impact/impactMetal_heavy_003", 0, 0.7), 0.0), (strain, 0.01), (glitch(0.25, 391, 700, 8) * 0.3, 0.05),
               (sub(0.3, 130, 50, 0.08) * 0.6, 0.0))


def prime_death():
    """4.8 s: systems fail one by one: stuttering whine, three blasts, falling parts, the last hum dying."""
    d = 4.8
    t = tt(3.0)
    whine = filt(osc(glide(t, 1400, 45, 0.55), "saw"), 60, 5000) * env(t, 0.02, 1.2, 0.6) * 0.45
    gate = (np.sin(2 * np.pi * np.cumsum(glide(t, 14, 3, 1.0)) / SR) > -0.2).astype(float)
    t2 = tt(2.0)
    hum = (osc(glide(t2, 55, 22, 1.0)) + osc(glide(t2, 110, 44, 1.0)) * 0.5) * env(t2, 0.05, 0.6, 0.6) * 0.35
    return stinger_tail(mix(d, (whine * (0.4 + 0.6 * gate), 0.0), (glitch(1.4, 401, 500, 16) * 0.3, 0.2),
                            (K("scifi/explosionCrunch_004", 0, 0.9), 0.5), (K("scifi/lowFrequency_explosion_000", -1, 0.8), 1.3),
                            (K("scifi/explosionCrunch_003", -3, 0.85), 2.1), (sub(1.2, 80, 25, 0.45), 1.3),
                            (clatter(4.0, 16, 402, ["impact/impactMetal_medium_002", "impact/impactPlate_light_004", "impact/impactMetal_light_003",
                                                    "impact/impactPlate_medium_003"], 1.4, 0.7, (0.75, 1.15), (-14, -6)), 0.0),
                            (hum, 2.6)), 1.6, 403, 0.22)


def prime_ascend():
    d = 2.2
    t = tt(2.0)
    rise = filt(osc(glide(t, 160, 900, 1.2), "saw") * 0.5 + osc(glide(t, 80, 450, 1.2)), 60, 5000) * np.clip(t / 1.4, 0, 1) * env(t, 1.2, 0.5, 0.4) * 0.4
    return mix(d, (K("scifi/thrusterFire_001", 0, 0.85, 0.5, 2.0, lo=60, ad=(0.5, 0.6, 0.6)), 0.0), (rise, 0.0),
               (sub(0.6, 120, 40, 0.18) * 0.8, 0.0))


def prime_summon():
    d = 1.9
    pulses = sum(place(np.zeros(int(1.6 * SR)), sub(0.5, 120, 60, 0.15) * (1 - 0.2 * i), 0.4 * i) for i in range(3))
    radio = beeps([(0.05, 1900, 0.4), (0.45, 2300, 0.4), (0.85, 2700, 0.45)], length=0.08, shape="sqr")
    return mix(d, (pulses, 0.0), (filt(radio, 800, 6000) * 0.6, 0.0), (K("scifi/forceField_001", -6, 0.8), 0.9),
               (glitch(0.4, 411, 900, 8) * 0.25, 1.2))


def prime_absorb():
    """2.0 s: a reversed swell: the node's energy sucked into him, a choke at the end."""
    d = 2.3
    t = tt(1.7)
    suck = filt(noise(1.7, 421), 400, 7000) * (t / 1.7) ** 3
    tone = (osc(glide(t, 900, 140, 0.6)) + osc(glide(t, 1350, 210, 0.6)) * 0.5) * (t / 1.7) ** 2 * 0.6
    return mix(d, (norm(suck) * 0.7, 0.0), (tone, 0.0), (K("scifi/forceField_002", -4, 0.7, rev=True), 0.85),
               (sub(0.35, 150, 50, 0.1), 1.7))


def prime_jet():
    """Loop 2.0 s: repulsor hum: low harmonic stack, a breathing air band, a high glassy whine."""
    d = 2.0
    t = tt(d)
    body = ptone(t, 92, d, 0.35) + ptone(t, 184, d, 0.22) + ptone(t, 276, d, 0.1, shape="saw") * 0.6
    air = pnoise(d, 431, 300, 3200) * (0.75 + 0.25 * plfo(t, 3, d)) * 0.35
    whine = ptone(t, 1610, d, 0.12, vib=0.005, vib_rate=4)
    return body + air + whine


# --- nodes ------------------------------------------------------------------------------------

def node_hum():
    """Loop 3.0 s: transformer hum (mains-like harmonic stack with slow beating), faint data shimmer."""
    d = 3.0
    t = tt(d)
    hum = ptone(t, 55, d, 0.4) + ptone(t, 110, d, 0.3) + ptone(t, 165, d, 0.18) + ptone(t, 220.67, d, 0.12) + ptone(t, 330, d, 0.06)
    beat = 0.85 + 0.15 * plfo(t, 1, d)
    buzz = ptone(t, 110, d, 0.08, shape="saw")
    shimmer = pnoise(d, 441, 2500, 7000) * 0.05 * (0.6 + 0.4 * plfo(t, 2, d, 0.7))
    return (hum + buzz) * beat + shimmer


def node_build():
    d = 1.5
    t = tt(1.2)
    arc = crackle(1.2, 500, 451, 1800, 10000, 0.0025) * env(t, 0.03, 0.4, 0.6) * 1.6
    return mix(d, (arc, 0.0), (servo(0.6, 380, 520, 452, 0.3) * 0.35, 0.1),
               (clatter(1.4, 7, 453, ["impact/impactMetal_light_000", "impact/impactMetal_medium_002", "rpg/metalLatch"], 0.1, 0.4, (0.9, 1.2), (-12, -6)), 0.0))


def node_online():
    d = 2.4
    t = tt(1.6)
    power = (osc(glide(t, 38, 110, 0.7)) * 0.6 + osc(glide(t, 76, 220, 0.7)) * 0.35 + osc(glide(t, 114, 330, 0.7)) * 0.2) * env(t, 0.3, 0.6, 0.6)
    bell = chord(1.4, (659.3, 987.8, 1318.5), "sin", 0.005, 0.5, 200, 8000, 0.002, 461) * 0.4
    return stinger_tail(mix(d, (power, 0.0), (bell, 0.9), (K("scifi/forceField_003", -6, 1.0), 0.85), (K("rpg/metalLatch", -8, 0.8), 0.85)),
                        1.0, 462, 0.25)


def _shield_hit(seed, field, pitch):
    d = 0.6
    t = tt(0.4)
    ping = (osc(np.full_like(t, 1760.0 * pitch)) + osc(np.full_like(t, 2640.0 * pitch)) * 0.5) * env(t, 0.002, 0.12) * 0.4
    return mix(d, (K(field, 0, 1.25 * pitch, 0.0, 0.5, ad=(0.003, 0.18)), 0.0), (ping, 0.0), (zap(0.2, 2600, 1200, seed, 400) * 0.3, 0.0),
               (sub(0.2, 220, 90, 0.05) * 0.4, 0.0))


def node_shield_hit():
    return _shield_hit(471, "scifi/forceField_001", 1.0)


def node_shield_hit_2():
    return _shield_hit(472, "scifi/forceField_004", 0.9)


def node_shield_break():
    d = 1.4
    t = tt(1.0)
    fall = (osc(glide(t, 2400, 300, 0.6)) + osc(glide(t, 3600, 450, 0.6)) * 0.5) * env(t, 0.005, 0.35) * 0.4
    return mix(d, (K("impact/impactGlass_heavy_001", 0, 0.8), 0.0), (K("scifi/forceField_000", -3, 0.8, rev=True), 0.0),
               (fall, 0.02), (K("scifi/explosionCrunch_000", -8, 1.3), 0.02), (sub(0.4, 200, 60, 0.12) * 0.6, 0.0))


def node_disabled():
    d = 1.8
    t = tt(1.5)
    down = (osc(glide(t, 110, 28, 0.8)) + osc(glide(t, 220, 56, 0.8)) * 0.4) * env(t, 0.01, 0.6, 0.5) * 0.6
    bell = beeps([(0.05, 988, 0.5), (0.25, 784, 0.45), (0.45, 523, 0.4)], length=0.14)
    return mix(d, (K("rpg/metalLatch", -3, 0.75), 0.0), (down, 0.02), (bell, 0.0), (glitch(0.3, 481, 500, 6) * 0.25, 0.02))


def node_reboot():
    d = 1.6
    t = tt(1.2)
    up = (osc(glide(t, 40, 110, 0.6)) * 0.5 + osc(glide(t, 80, 220, 0.6)) * 0.3) * env(t, 0.4, 0.4, 0.4)
    chirps = beeps([(0.0, 1320, 0.4), (0.12, 1320, 0.4), (0.24, 1760, 0.5), (0.9, 2093, 0.45)], length=0.05, shape="sqr")
    return mix(d, (filt(chirps, 400, 6000), 0.0), (up, 0.15), (K("scifi/computerNoise_003", -14, 1.0, 2.0, 1.0, lo=1500, ad=(0.05, 0.3, 0.3)), 0.1))


def node_destroyed():
    """2.8 s: the pylon blows: a big low blast, crunch, electrical death rattle, glass and metal raining."""
    d = 2.8
    return stinger_tail(mix(d, (K("scifi/lowFrequency_explosion_000", 0, 0.9), 0.0), (K("scifi/explosionCrunch_003", -2, 0.95), 0.0),
                            (sub(1.0, 90, 26, 0.4), 0.0), (zap(0.8, 1800, 200, 491, 500) * 0.4, 0.05),
                            (clatter(2.6, 14, 492, ["impact/impactGlass_light_000", "impact/impactMetal_light_003", "impact/impactPlate_light_001",
                                                    "impact/impactMetal_medium_002"], 0.35, 0.5, (0.85, 1.25), (-15, -7)), 0.0)), 1.2, 493, 0.2)


def node_hack():
    d = 0.6
    t = tt(0.45)
    chirp = osc(1400 + 700 * np.sign(np.sin(2 * np.pi * 22 * t))) * env(t, 0.01, 0.2, 0.15) * 0.4
    return mix(d, (glitch(0.45, 501, 1500, 9) * 0.6, 0.0), (chirp, 0.0), (crackle(0.4, 160, 502, 2000, 10000, 0.002) * 1.2, 0.0))


def node_tether():
    """Loop 2.0 s: energy tether: FM-wobbling tone, a pulsing mid band, a low carrier."""
    d = 2.0
    t = tt(d)
    wob = ptone(t, 220, d, 0.3, vib=0.02, vib_rate=6) + ptone(t, 330, d, 0.15, vib=0.02, vib_rate=6, phase0=1.0)
    band_ = pnoise(d, 511, 800, 3200) * (0.6 + 0.4 * plfo(t, 6, d)) * 0.3
    carrier = ptone(t, 55, d, 0.25)
    return wob + band_ + carrier


# --- projectiles, impacts, world --------------------------------------------------------------

def _bolt_impact(seed, laser, pitch):
    d = 0.5
    burst = filt(noise(0.25, seed), 600, 9000) * env(tt(0.25), 0.001, 0.05)
    return mix(d, (burst * 0.8, 0.0), (K(laser, -3, 0.6 * pitch, rev=True, ad=(0.002, 0.1)), 0.0),
               (crackle(0.35, 250, seed + 1, 1500, 10000, 0.003) * 1.5, 0.0), (sub(0.25, 220, 70, 0.06) * 0.7, 0.0))


def bolt_impact():
    return _bolt_impact(521, "scifi/laserSmall_003", 1.0)


def bolt_impact_2():
    return _bolt_impact(522, "scifi/laserRetro_002", 0.9)


def missile_flight():
    d = 1.5
    t = tt(1.4)
    whistle = osc(glide(t, 1500, 1150, 1.0)) * env(t, 0.1, 0.6, 0.6) * 0.25
    return mix(d, (K("scifi/thrusterFire_004", 0, 1.2, 0.6, 1.4, lo=150, ad=(0.03, 0.5, 0.6)), 0.0), (whistle, 0.0))


def missile_explode():
    d = 1.8
    return mix(d, (K("scifi/explosionCrunch_001", 0, 1.0), 0.0), (K("scifi/lowFrequency_explosion_001", -2, 1.0), 0.0),
               (sub(0.6, 110, 32, 0.2), 0.0), (ch._rubble(1.5, 10, 531, 0.1, 0.3, 300, 5000, (0.1, 0.35)), 0.0))


def deflect():
    d = 0.5
    t = tt(0.4)
    ping = (osc(glide(t, 3900, 3300, 1.0)) + osc(glide(t, 5200, 4600, 1.0)) * 0.4) * env(t, 0.001, 0.09) * 0.35
    return mix(d, (K("impact/impactMetal_light_004", 0, 1.2), 0.0), (ping, 0.0), (K("scifi/forceField_002", -9, 1.6, 0.0, 0.3, ad=(0.002, 0.08)), 0.0))


def debris():
    d = 1.4
    return mix(d, (K("impact/impactMining_001", 0, 0.8), 0.0), (ch._rubble(1.3, 16, 541, 0.05, 0.3, 250, 5000, (0.15, 0.5)), 0.0),
               (clatter(1.3, 6, 542, ["impact/impactMetal_light_000", "impact/impactPlate_light_002"], 0.1, 0.3, (0.8, 1.2), (-12, -6)), 0.0))


def debris_2():
    d = 1.4
    return mix(d, (K("impact/impactMining_004", 0, 0.75), 0.0), (ch._rubble(1.3, 18, 543, 0.05, 0.35, 220, 4500, (0.15, 0.5)), 0.0),
               (clatter(1.3, 5, 544, ["impact/impactMetal_medium_004", "impact/impactPlate_light_000"], 0.12, 0.3, (0.8, 1.2), (-12, -6)), 0.0))


def splash():
    return splash_synth(1.0, 551)


def fabricate():
    d = 1.8
    t = tt(0.6)
    ding = (osc(np.full_like(t, 1046.5)) + osc(np.full_like(t, 1568.0)) * 0.4) * env(t, 0.003, 0.25) * 0.3
    return mix(d, (servo(0.5, 300, 520, 561, 0.3) * 0.4, 0.0), (K("impact/impactPlate_heavy_001", 0, 0.7), 0.45),
               (sub(0.4, 130, 50, 0.1) * 0.8, 0.45), (K("scifi/doorOpen_002", -4, 0.85), 0.75), (ding, 1.25))


# --- event cues (heard by everyone in range) --------------------------------------------------

def event_start():
    """3.8 s: the network wakes: a sub drop, static, glitch stutters, a cold rising chord."""
    d = 3.8
    t = tt(3.0)
    rise = chord(3.0, (110, 164.8, 220, 329.6), "saw", 1.6, 1.0, 80, 3500, 0.005, 571) * np.clip(t / 2.4, 0, 1) * 0.6
    static = K("scifi/computerNoise_000", -8, 1.0, 0.5, 2.0, lo=800, ad=(0.05, 0.8, 0.6))
    return stinger_tail(mix(d, (sub(1.2, 70, 24, 0.5), 0.0), (static, 0.0), (glitch(0.9, 572, 700, 12) * 0.4, 0.15),
                            (rise, 0.5), (glitch(0.35, 573, 1200, 6) * 0.35, 2.7)), 1.8, 574, 0.3, 3000)


def phase_change():
    d = 3.0
    hit = chord(2.2, (73.4, 110, 146.8, 174.6), "saw", 0.01, 0.9, 60, 2600, 0.006, 581)   # D minor-ish low stab
    return stinger_tail(mix(d, (hit * 0.8, 0.0), (sub(1.0, 80, 30, 0.4), 0.0), (glitch(0.3, 582, 600, 7) * 0.35, 0.0),
                            (K("impact/impactPlate_heavy_003", -6, 0.5), 0.0)), 1.8, 583, 0.35, 2800)


def protocol():
    d = 1.6
    seq = [(0.0, 1568, 0.5), (0.08, 1318.5, 0.5), (0.16, 1046.5, 0.5), (0.3, 1244.5, 0.55), (0.38, 1661.2, 0.6)]
    return stinger_tail(mix(d, (filt(beeps(seq, length=0.07, shape="sqr"), 300, 6000) * 0.7, 0.0), (glitch(0.3, 591, 900, 7) * 0.3, 0.5),
                            (sub(0.4, 140, 60, 0.12) * 0.5, 0.3)), 0.9, 592, 0.3)


def transmission():
    """0.6 s radio blip before a line (no voice)."""
    d = 0.6
    t = tt(0.12)
    sq = filt(noise(0.12, 601), 1200, 4500) * env(t, 0.002, 0.04)
    chirp = osc(glide(tt(0.18), 1800, 2600, 0.7)) * env(tt(0.18), 0.005, 0.07) * 0.5
    return mix(d, (sq * 0.6, 0.0), (chirp, 0.05), (K("rpg/metalClick", -12, 1.4), 0.0), (sq * 0.3, 0.38))


def capture():
    d = 1.8
    t = tt(1.4)
    whine = filt(osc(glide(t, 400, 1800, 1.3), "saw"), 300, 7000) * env(t, 0.6, 0.3, 0.5) * 0.35
    return mix(d, (glitch(1.2, 611, 800, 16) * 0.55, 0.0), (whine, 0.1), (zap(0.5, 1600, 300, 612, 400) * 0.4, 1.2),
               (sub(0.5, 110, 40, 0.15), 1.25), (K("rpg/metalLatch", -6, 0.7), 1.25))


def release():
    d = 1.6
    bright = chord(1.1, (523.3, 659.3, 784, 1046.5), "sin", 0.01, 0.45, 200, 8000, 0.002, 621) * 0.6
    return stinger_tail(mix(d, (glitch(0.35, 622, 1200, 8) * 0.4, 0.0), (bright, 0.25), (K("scifi/forceField_003", -10, 1.2), 0.2)), 1.0, 623, 0.25)


def discover():
    d = 1.1
    p = tt(0.35)
    ping = (osc(np.full_like(p, 1318.5)) + osc(np.full_like(p, 2637.0)) * 0.3) * env(p, 0.002, 0.11) * 0.6
    return stinger_tail(mix(d, (ping, 0.0), (ping * 0.55, 0.22)), 0.8, 631, 0.3)


def upload_alarm():
    """2.2 s: a two-tone alarm, three cycles, over data noise."""
    d = 2.2
    layers = []
    for i in range(3):
        for j, f in enumerate((880, 660)):
            s = tt(0.32)
            tone = filt(osc(np.full_like(s, f), "sqr") * 0.5 + osc(np.full_like(s, f * 2)) * 0.2, 300, 5000) * env(s, 0.01, 0.2, 0.15)
            layers.append((tone, 0.7 * i + 0.34 * j))
    layers.append((K("scifi/computerNoise_001", -14, 1.0, 1.0, 2.1, lo=1000, ad=(0.1, 0.8, 1.0)), 0.0))
    return mix(d, *layers)


def victory():
    """4.2 s (the players win): the network collapses in glitches, powers down, then a calm resolving chord."""
    d = 4.2
    t = tt(1.6)
    down = filt(osc(glide(t, 880, 55, 0.6), "saw"), 60, 4000) * env(t, 0.01, 0.6, 0.6) * 0.4
    calm = chord(2.2, (261.6, 329.6, 392, 523.3), "sin", 0.4, 1.0, 150, 6000, 0.003, 641) * 0.5
    return stinger_tail(mix(d, (glitch(1.0, 642, 900, 14) * 0.45, 0.0), (down, 0.2), (sub(0.8, 90, 30, 0.3) * 0.8, 1.2),
                            (calm, 1.8)), 2.0, 643, 0.35, 4000)


def defeat():
    """4.2 s (Ultron uploads): a digital crescendo into a cold minor cluster, cut to static."""
    d = 4.2
    t = tt(2.6)
    climb = chord(2.6, (146.8, 174.6, 220, 277.2), "saw", 2.0, 0.6, 80, 4000, 0.006, 651) * np.clip(t / 2.4, 0, 1) ** 1.5 * 0.7
    data = glitch(2.4, 652, 1100, 30) * np.clip(tt(2.4) / 2.4, 0, 1) * 0.35
    cut = K("scifi/computerNoise_000", -10, 1.0, 2.5, 1.2, lo=600, ad=(0.005, 0.5, 0.3))
    return stinger_tail(mix(d, (climb, 0.0), (data, 0.1), (sub(1.2, 60, 22, 0.5), 2.6), (cut, 2.6)), 1.8, 653, 0.3, 3000)


# --- tables -----------------------------------------------------------------------------------

LOOPS = {"drone_hover", "interceptor_jet", "prime_jet", "node_hum", "node_tether"}
# event -> extra variant files (event name itself is always the first file)
VARIANTS = {"sentry_step": 3, "brute_step": 3, "engineer_step": 3, "prime_step": 2, "sentry_strike": 2, "prime_impact": 2,
            "bolt_impact": 2, "debris": 2, "node_shield_hit": 2}
# client-side loops and footfalls play at volume <= 1, so their reach is their attenuation distance (default 16)
ATTENUATION = {"drone_hover": 16, "interceptor_jet": 40, "prime_jet": 48, "node_hum": 20, "node_tether": 64,
               "sentry_step": 18, "brute_step": 28, "engineer_step": 14, "prime_step": 40, "missile_flight": 32}

# Loudest 100 ms RMS (dBFS) by role, soft-limited under chitauri.CEILING_DB: signature / heavy -8..-10, hits and
# weapons -10..-13, motion -14..-15, steps -18..-21, loops -16..-19 (UltronLoops scales them again at run time),
# event cues -11..-13, the transmission blip -17 (it sits under a subtitle, never over it).
ROLE = {
    "heavy": -9.0, "hit": -11.5, "weapon": -12.0, "motion": -14.5, "step": -19.0, "light_step": -21.0, "loop": -17.5,
    "cue": -12.0, "blip": -17.0, "hurt": -13.0, "death": -10.5, "ui": -13.5,
}
CUE_ROLE = {
    "drone_hover": "loop", "drone_zap": "weapon", "drone_scan": "ui", "drone_mark": "ui", "drone_hurt": "hurt", "drone_death": "death",
    "sentry_step": "step", "sentry_servo": "motion", "sentry_swing": "motion", "sentry_strike": "hit", "sentry_blast": "weapon",
    "sentry_hurt": "hurt", "sentry_death": "death",
    "brute_step": "step", "brute_servo": "motion", "brute_slam": "heavy", "brute_swing": "motion", "brute_missiles": "weapon",
    "brute_charge": "motion", "brute_shield": "ui", "brute_hurt": "hurt", "brute_death": "heavy",
    "interceptor_jet": "loop", "interceptor_burst": "weapon", "interceptor_dive": "motion", "interceptor_boost": "motion",
    "interceptor_hurt": "hurt", "interceptor_death": "death",
    "engineer_step": "light_step", "engineer_weld": "motion", "engineer_hack": "ui", "engineer_plant": "motion", "engineer_hurt": "hurt",
    "engineer_death": "death",
    "prime_assemble": "heavy", "prime_step": "step", "prime_whoosh": "motion", "prime_impact": "hit", "prime_palm": "weapon",
    "prime_beam_charge": "weapon", "prime_beam": "heavy", "prime_slam": "heavy", "prime_dash": "motion", "prime_transition": "heavy",
    "prime_hurt": "hurt", "prime_death": "heavy", "prime_ascend": "heavy", "prime_summon": "cue", "prime_absorb": "weapon",
    "prime_jet": "loop",
    "node_hum": "loop", "node_build": "motion", "node_online": "cue", "node_shield_hit": "hit", "node_shield_break": "hit",
    "node_disabled": "cue", "node_reboot": "cue", "node_destroyed": "heavy", "node_hack": "ui", "node_tether": "loop",
    "bolt_impact": "hit", "missile_flight": "motion", "missile_explode": "heavy", "deflect": "hit", "debris": "motion",
    "splash": "motion", "fabricate": "motion",
    "event_start": "cue", "phase_change": "cue", "protocol": "cue", "transmission": "blip", "capture": "cue", "release": "cue",
    "discover": "ui", "upload_alarm": "cue", "victory": "cue", "defeat": "cue",
}
# per-role offsets inside a role (dB)
TRIM = {"node_tether": 1.0, "prime_jet": 1.0, "interceptor_jet": 0.5, "node_hum": -1.0, "drone_hover": -0.5, "prime_step": 2.0,
        "brute_step": 1.0, "debris": 1.5, "splash": 1.0, "missile_flight": 0.5, "prime_assemble": 0.5}


def registry_names():
    import re
    m = re.search(r"NAMES\s*=\s*List\.of\(([^)]*)\)", REGISTRY.read_text(), re.S)
    return re.findall(r'"([a-z0-9_]+)"', m.group(1))


def files_of(event):
    n = VARIANTS.get(event, 1)
    return [event] + ["%s_%d" % (event, i) for i in range(2, n + 1)]


def cues():
    g = globals()
    out = {}
    for event in registry_names():
        for f in files_of(event):
            out[f] = (event, g[f])
    return out


def quality(name, seconds):
    if name.split("_")[0] in ("victory", "defeat") or seconds > 3.5:
        return 0
    if name in LOOPS or seconds > 1.9:
        return 1
    return 2


def build(tmp, wav_dir=None, only=None):
    res = {}
    for name, (event, fn) in cues().items():
        if only and name not in only and event not in only:
            continue
        CURRENT[0] = event
        loop = event in LOOPS
        target = ROLE[CUE_ROLE[event]] + TRIM.get(event, 0.0)
        x = ch.master(np.asarray(fn(), dtype=np.float64), target, loop)
        assert np.isfinite(x).all() and np.max(np.abs(x)) > 0.05, name
        if loop:
            assert abs(x[0] - x[-1]) <= np.max(np.abs(np.diff(x))) * 1.02 + 1e-4, name + " loop seam"
        w = Path(tmp) / (name + ".wav")
        o = Path(tmp) / (name + ".ogg")
        write_wav(w, x)
        if wav_dir:
            os.makedirs(wav_dir, exist_ok=True)
            write_wav(Path(wav_dir) / (name + ".wav"), x)
        encode(w, o, quality(name, len(x) / SR))
        res[name] = o
    return res


def overlay():
    out = {}
    for event in registry_names():
        sounds = []
        for f in files_of(event):
            s = {"name": "viltrumitecore:ultron/" + f}
            if event in ATTENUATION:
                s["attenuation_distance"] = ATTENUATION[event]
            sounds.append(s)
        out["ultron." + event] = {"subtitle": "subtitles.viltrumitecore.ultron." + event, "sounds": sounds}
    return out


def ledger():
    for name, (event, fn) in cues().items():
        CURRENT[0] = event
        fn()
    lines = ["| Recording (Kenney pack / file) | Licence | Used in |", "|---|---|---|"]
    for s in sorted(USED):
        pack, stem = s.split("/")
        lines.append("| [%s](https://kenney.nl/assets/%s) / `Audio/%s.ogg` | CC0 1.0 | %s |"
                     % (PACKS[pack], PACKS[pack], stem, ", ".join(sorted(USED[s]))))
    own = sorted(e for e in registry_names() if not any(e in v for v in USED.values()))
    lines.append("")
    lines.append("Pure own synthesis (no recording): " + ", ".join(own))
    print("\n".join(lines))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--ledger", action="store_true")
    ap.add_argument("--wav")
    ap.add_argument("--only", nargs="*")
    a = ap.parse_args()
    if a.fetch:
        fetch()
        return
    try:
        if a.ledger:
            ledger()
            return
        with tempfile.TemporaryDirectory() as tmp:
            built = build(tmp, a.wav, set(a.only) if a.only else None)
            bad = 0
            for name, ogg in built.items():
                dst = OUT / (name + ".ogg.b64")
                if a.check:
                    if not dst.exists():
                        print("missing", dst)
                        bad += 1
                        continue
                    committed = Path(tmp) / (name + ".committed.ogg")
                    committed.write_bytes(base64.b64decode(dst.read_text()))
                    if hashlib.sha256(decoded_pcm(committed)).digest() != hashlib.sha256(decoded_pcm(ogg)).digest():
                        print("differs", name)
                        bad += 1
                else:
                    OUT.mkdir(parents=True, exist_ok=True)
                    dst.write_bytes(base64.encodebytes(ogg.read_bytes()))
            if not a.check and not a.only:
                OVERLAY.write_text(json.dumps(overlay(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            print("%d files %s" % (len(built), "checked" if a.check else "written"))
            sys.exit(1 if bad else 0)
    except MissingSource as e:
        print("CC0 source not cached (%s): run with --fetch first" % e)
        sys.exit(0 if a.check else 1)


if __name__ == "__main__":
    main()
