#!/usr/bin/env python3
"""City disaster aftermath soundscape: CC0 Freesound recordings, edited and layered with original synthesis.

Every source is CC0 1.0 (Creative Commons Zero) on Freesound; the table SOURCES lists the id, creator and
original title, and tools/assets/city_aftermath_sources.md links each landing page. Only the public preview
MP3 is used, cached outside the repository (never committed):

    ~/.cache/viltrumite-sfx/work/raw/fs_<freesound id>.mp3   (https://cdn.freesound.org/previews/...-hq.mp3)

Run from the repository root:

    python3 tools/sfx/city_aftermath.py --fetch            # download the previews into the cache
    python3 tools/sfx/city_aftermath.py                    # mix every cue and pack it (refuses to overwrite)
    python3 tools/sfx/city_aftermath.py --replace          # rebuild the committed assets
    python3 tools/sfx/city_aftermath.py --only wind --wav /tmp/am   # mix one cue to WAV for review
    python3 tools/sfx/city_aftermath.py --stats            # print measured length, level, centroid, size

Everything is seeded, so a rerun gives the same mix. Loops (fire_bed, rumble, wind) are cut from a recording
with an equal-power crossfade of the tail onto the head, so the last sample runs into the first. Each cue is
normalised on its loudest 100 ms RMS to a per-class target and soft-limited under -1 dBFS, then packed as
mono Vorbis (q3, loops q2; 44.1 kHz, dark cues 22.05 kHz) base64 text under viltrumitecore/src/main/binassets (decoded by Gradle). Cues stay
short because a push request is capped at 64 KB. No voices: the city siren recordings were rejected because
they could hold people talking in the background; sirens are the synthesized CC0 air-raid recording plus an
original synthesized wail.

No listening test is implied by this script: levels and spectra are measured, not auditioned.
"""
import argparse
import base64
import os
import subprocess
import sys
import tempfile
import urllib.request

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from meteor_rain import SR, band, db, env, fade, freeze, grains, noise, place, sweep, t_axis, write_wav  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAW = os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "raw")
OUT = os.path.join(ROOT, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore", "sounds", "aftermath")

# key -> (Freesound id, creator, original title, preview URL). All CC0 1.0.
SOURCES = {
    "far_explosions": (320788, "Kostrava", "distant explosions", "https://cdn.freesound.org/previews/320/320788_1134415-hq.mp3"),
    "metal_explosion": (324277, "Kostrava", "distant explosion", "https://cdn.freesound.org/previews/324/324277_1134415-hq.mp3"),
    "huge_explosion": (476225, "Fission9", "Huge Distant Explosion", "https://cdn.freesound.org/previews/476/476225_9395330-hq.mp3"),
    "nuclear_distant": (322365, "Svarvarn", "Nuclear distant.wav", "https://cdn.freesound.org/previews/322/322365_2513568-hq.mp3"),
    "building_collapse": (712918, "greyfeather", "building collapse / demolition", "https://cdn.freesound.org/previews/712/712918_15139380-hq.mp3"),
    "house_collapse": (721600, "DasWaff", "Bakers house collapse", "https://cdn.freesound.org/previews/721/721600_2617407-hq.mp3"),
    "creak_slow": (172411, "EagleStealthTeam", "Creaking Metal - Slow", "https://cdn.freesound.org/previews/172/172411_2915633-hq.mp3"),
    "creak_metal": (489442, "Soapuel", "Creaking Metal.wav", "https://cdn.freesound.org/previews/489/489442_10448599-hq.mp3"),
    "creak_long": (238289, "LoafDV", "Long Metal Creak", "https://cdn.freesound.org/previews/238/238289_1554038-hq.mp3"),
    "rockfall": (389303, "AlanCat", "rockfall2a.wav", "https://cdn.freesound.org/previews/389/389303_5486695-hq.mp3"),
    "rocks": (336023, "adamgryu", "Rocks.wav", "https://cdn.freesound.org/previews/336/336023_4054883-hq.mp3"),
    "low_rumble": (638914, "lennartgreen", "Low Rumble - loopable ambiance", "https://cdn.freesound.org/previews/638/638914_3661618-hq.mp3"),
    "ominous_rumble": (593785, "steaq", "Ominous Rumble - loopable (24bit flac)", "https://cdn.freesound.org/previews/593/593785_1474204-hq.mp3"),
    "howling_wind": (185070, "juryduty", "howling_wind.wav", "https://cdn.freesound.org/previews/185/185070_3279921-hq.mp3"),
    "tower_wind": (486599, "tomhannen", "Howling wind through window in tower", "https://cdn.freesound.org/previews/486/486599_1121623-hq.mp3"),
    "sparks": (396470, "kev_durr", "Electric Sparks.wav", "https://cdn.freesound.org/previews/396/396470_5415774-hq.mp3"),
    "arcs": (522690, "julianmateo_", "Electricity, Arcs, Sparks, long", "https://cdn.freesound.org/previews/522/522690_11584853-hq.mp3"),
    "air_raid": (324276, "Kostrava", "multiple siren", "https://cdn.freesound.org/previews/324/324276_1134415-hq.mp3"),
    "stone_impacts": (554148, "Nox_Sound", "Foley_Impact_Stones_Strong_Debris_Stereo_DS.wav", "https://cdn.freesound.org/previews/554/554148_9250976-hq.mp3"),
    "stone_rubble": (569510, "Sheyvan", "Stone Impact Rubble Debris 14", "https://cdn.freesound.org/previews/569/569510_3248005-hq.mp3"),
    "fire_crackles": (563766, "florianreichelt", "fire crackles", "https://cdn.freesound.org/previews/563/563766_6253486-hq.mp3"),
    "wood_fire": (779877, "felix.blume", "Wood fire crackling, near flames", "https://cdn.freesound.org/previews/779/779877_1661766-hq.mp3"),
}

# class -> loudest-100 ms RMS target dBFS
TARGET = {"far": -12.0, "collapse": -10.0, "crumble": -17.0, "rocks": -14.0, "impact": -11.0, "creak": -16.0,
          "sparks": -15.0, "crackle": -15.0, "fire_bed": -20.0, "rumble": -17.0, "wind": -19.0, "siren": -19.0}

_cache = {}


def path_of(key):
    return os.path.join(RAW, f"fs_{SOURCES[key][0]}.mp3")


def fetch():
    os.makedirs(RAW, exist_ok=True)
    for key, (fid, user, title, url) in SOURCES.items():
        p = path_of(key)
        if not os.path.isfile(p):
            print("fetch", fid, user, title)
            urllib.request.urlretrieve(url, p)


def source(key):
    if key not in _cache:
        p = path_of(key)
        if not os.path.isfile(p):
            sys.exit(f"Missing source {key} ({p}); run: python3 tools/sfx/city_aftermath.py --fetch")
        out = subprocess.run(["ffmpeg", "-v", "error", "-i", p, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"],
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


def unit(x):
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-12)


def loop_cut(x, seconds, xfade):
    """Seamless loop of `seconds` from x (needs seconds + xfade of material): the tail crossfaded onto the head."""
    n = int(round(seconds * SR))
    k = min(int(xfade * SR), len(x) - n)
    y = x[:n].copy()
    a = np.sin(0.5 * np.pi * np.linspace(0, 1, k))
    y[:k] = x[:k] * a + x[n:n + k] * np.cos(0.5 * np.pi * np.linspace(0, 1, k))
    return y


def echoes(x, taps, lo=80, hi=2500):
    """Street reflections: darker delayed copies (seconds, gain)."""
    y = x.copy()
    for at, g in taps:
        place(y, band(x, lo, hi), at, g)
    return y


def thump(seconds, f0, f1, decay, drop=0.4):
    t = t_axis(seconds)
    f = f0 + (f1 - f0) * np.clip(t / drop, 0, 1)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(seconds, 0.004, decay, 0.02, 3.0)
    return np.tanh(1.8 * s) / np.tanh(1.8)


def transients(x, count, gap, lead=0.02, length=0.6):
    """Start times (s) of the `count` loudest 10 ms windows at least `gap` s apart, fully inside x."""
    w = int(0.01 * SR)
    e = np.convolve(x ** 2, np.ones(w) / w, mode="same")
    picks = []
    for i in np.argsort(e)[::-1]:
        if i < lead * SR or i > len(x) - length * SR:
            continue
        if all(abs(i - p) > gap * SR for p in picks):
            picks.append(i)
        if len(picks) == count:
            break
    return sorted(p / SR - lead for p in picks)


def finish(x, cls, loop=False):
    """Normalise on the loudest 100 ms RMS, then soft-limit under -1 dBFS (tanh knee above -4 dBFS)."""
    w = int(0.1 * SR)
    rms = max(np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, max(1, len(x) - w), w // 2))
    x = x * db(TARGET[cls]) / max(rms, 1e-9)
    knee, ceiling = db(-4.0), db(-1.0)
    over = np.abs(x) > knee
    x[over] = np.sign(x[over]) * (knee + (ceiling - knee) * np.tanh((np.abs(x[over]) - knee) / (ceiling - knee)))
    if not loop:
        x = fade(x, 0.002, 0.12)
    return x


# ------------------------------------------------------------------ cues

def explosion_far(rng, key, start, length, pitch, lp, delay):
    """A distant blast over the rooftops: the recording darkened, a sub swell, two street echoes."""
    x = band(seg(key, start, length, pitch), 28, lp)
    x = fade(x, 0.01, 0.6)
    swell = band(noise(length, rng, 2.0), 22, 90) * env(length, 0.12, 1.6, 0.2, 2.6)
    y = echoes(x + 0.35 * swell, [(delay, 0.35), (delay * 2.3, 0.18)], 40, lp * 0.6)
    return finish(fade(y, 0.01, 0.9), "far")


def collapse(rng, key, start, length, pitch, rock_at):
    """A wall section giving way: crunching recording, a dropping sub boom, rocks raining down after."""
    L = 5.0
    body = band(seg(key, start, length, pitch), 35, 9000)
    body = np.pad(body, (0, int(L * SR) - len(body))) if len(body) < L * SR else body[:int(L * SR)]
    boom = thump(L, 62, 30, 1.1, 0.8)
    rumble = band(noise(L, rng, 2.0), 25, 180) * env(L, 0.05, 2.2, 0.3, 2.4)
    tail = band(seg("rockfall", rock_at, 3.2), 150, 7000) * env(3.2, 0.3, 1.8, 0.4, 2.0)
    x = unit(body) * 1.0 + 0.55 * boom + 0.35 * rumble
    place(x, unit(tail), 1.6, 0.45)
    return finish(fade(x, 0.002, 0.9), "collapse")


def crumble(rng, start, length, pitch):
    """Concrete crumbling off a broken edge: a short trickle of grit, pebbles and a couple of chunks."""
    rec = band(seg("rocks", start, length, pitch), 180, 8000) * env(length, 0.03, length * 0.55, 0.1, 2.2)
    grit = grains(length, rng, 45, 900, 6000, (0.004, 0.02), lambda q: np.exp(-2.0 * q), (0.1, 0.6))
    chunks = grains(length, rng, 4, 160, 900, (0.02, 0.06), lambda q: 1.0 - 0.6 * q, (0.5, 1.0))
    return finish(unit(rec) + 0.5 * unit(grit) + 0.45 * unit(chunks), "crumble")


def rocks(rng, start, length, pitch):
    """Rubble sliding off a pile: the rockfall recording with heavier thuds under it."""
    rec = band(seg("rockfall", start, length, pitch), 90, 9000)
    thuds = grains(length, rng, 10, 90, 500, (0.03, 0.08), lambda q: 1.0 - 0.5 * q, (0.4, 1.0))
    return finish(fade(unit(rec) + 0.35 * unit(thuds), 0.01, 0.5), "rocks")


def debris_impact(rng, key, start, length, pitch, f0):
    """A heavy chunk hitting the street: the stone impact recording, a body thump, scattering bits."""
    rec = band(seg(key, start, length, pitch), 60, 12000)
    body = thump(length, f0, f0 * 0.55, 0.22, 0.2)
    bits = grains(length, rng, 25, 1200, 7000, (0.004, 0.02), lambda q: np.exp(-4.0 * q), (0.1, 0.5))
    return finish(unit(rec) + 0.6 * body + 0.25 * unit(bits), "impact")


def metal_creak(rng, key, start, length, pitch, hp):
    """A bent girder groaning under load, slow swell in and out."""
    rec = band(seg(key, start, length, pitch), hp, 7000)
    shape = np.sin(np.pi * np.clip(t_axis(length) / length, 0, 1)) ** 0.7
    groan = band(noise(length, rng, 2.0), 40, 140) * shape
    return finish(rec * shape + 0.08 * unit(groan) * np.sqrt(np.mean(rec ** 2)), "creak")


def sparks(rng, key, start, length, hum):
    """A torn wire arcing: a burst of crackle with mains buzz under it, fading out."""
    rec = band(seg(key, start, length), 900, 16000) * env(length, 0.004, length * 0.4, 0.05, 2.8)
    t = t_axis(length)
    buzz = np.sign(np.sin(2 * np.pi * hum * t)) * 0.5 + np.sin(4 * np.pi * hum * t) * 0.5
    buzz = band(buzz, 90, 3000) * env(length, 0.002, length * 0.5, 0.0, 3.2) * (0.6 + 0.4 * rng.uniform(size=len(t)) ** 8)
    zap = band(noise(0.08, rng), 2500, 14000) * env(0.08, 0.0005, 0.03)
    x = unit(rec) + 0.22 * unit(buzz)
    place(x, unit(zap), 0.0, 1.4)
    return finish(x, "sparks")


def fire_crackle(rng, picks):
    """Embers popping in the rubble: two or three of the strongest crackles of the fire recordings."""
    L = 1.2
    x = np.zeros(int(L * SR))
    for key, at, off, g in picks:
        c = band(seg(key, at, 0.5), 400, 13000) * env(0.5, 0.002, 0.18, 0.02, 3.5)
        place(x, unit(c), off, g)
    hiss = band(noise(L, rng, 1.0), 1500, 9000) * env(L, 0.05, 0.7, 0.1, 2.0)
    return finish(x + 0.05 * hiss, "crackle")


def fire_bed(rng):
    """Smouldering rubble (loop 6 s): the near-flame recording, low roar under it, crossfaded into a loop."""
    L, X = 6.0, 0.8
    near = band(seg("wood_fire", 52.0, L + X), 120, 9000)
    soft = band(seg("fire_crackles", 18.0, L + X), 200, 8000)
    roar = band(noise(L + X, rng, 2.0), 40, 260)
    x = unit(near) + 0.45 * unit(soft) + 0.35 * roar
    return finish(loop_cut(x, L, X), "fire_bed", loop=True)


def rumble(rng):
    """Low rumble of a ruined block (loop 8 s): the two loopable drones, darkened, crossfaded into a loop."""
    L, X = 8.0, 1.0
    a = band(seg("ominous_rumble", 28.0, L + X), 25, 320)
    b = band(seg("low_rumble", 11.0, L + X), 25, 260)
    t = t_axis(L + X)
    sway = 0.85 + 0.15 * np.sin(2 * np.pi * t / 4.0)
    x = unit(a) * sway + 0.7 * unit(b)
    # soft-saturated overtones (120-600 Hz) so laptop speakers and earbuds still carry the rumble
    x = x + 0.6 * unit(band(np.tanh(1.5 * x), 120, 600)) * 0.35
    return finish(loop_cut(x, L, X), "rumble", loop=True)


def wind(rng):
    """Wind moaning through broken walls (loop 8 s): tower-window howl over a wider gust, crossfaded."""
    L, X = 8.0, 1.2
    tower = band(seg("tower_wind", 3.0, L + X), 140, 5000)
    gust = band(seg("howling_wind", 23.2, L + X), 120, 4000)
    x = unit(tower) + 0.6 * unit(gust)
    return finish(loop_cut(x, L, X), "wind", loop=True)


def siren_rec(rng, start, length):
    """A far air-raid siren over the city: synthesized recording, band-limited, with street echoes."""
    x = band(seg("air_raid", start, length), 250, 2600) * env(length, 0.6, length * 0.45, length * 0.35, 2.0)
    y = echoes(x, [(0.21, 0.4), (0.53, 0.25), (0.97, 0.14)], 300, 1600)
    return finish(fade(y, 0.2, 1.2), "siren")


def siren_wail(rng):
    """Original distant emergency wail: a rising and falling tone with harmonics, air-absorbed, echoed."""
    L = 6.5
    t = t_axis(L)
    f = 640 + 520 * (0.5 - 0.5 * np.cos(2 * np.pi * t / 3.1))
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) + 0.45 * np.sin(2 * ph) + 0.2 * np.sin(3 * ph) + 0.08 * np.sin(5 * ph)
    tone = band(tone, 300, 2200) * env(L, 1.0, 2.0, 3.2, 2.5) * (1 + 0.05 * np.sin(2 * np.pi * 6.3 * t))
    y = echoes(tone, [(0.18, 0.45), (0.47, 0.3), (0.88, 0.18), (1.4, 0.1)], 350, 1500)
    air = band(noise(L, rng, 1.0), 200, 1200) * 0.01
    return finish(fade(y + air, 0.3, 1.0), "siren")


# name -> (builder(rng) -> signal, sources used)
CUES = {
    "explosion_far_1": (lambda r: explosion_far(r, "far_explosions", 30.4, 4.6, 1.0, 1500, 0.38), ["far_explosions"]),
    "explosion_far_2": (lambda r: explosion_far(r, "huge_explosion", 0.0, 4.6, 0.94, 1800, 0.45), ["huge_explosion"]),
    "explosion_far_3": (lambda r: explosion_far(r, "metal_explosion", 0.0, 4.6, 0.9, 2200, 0.3), ["metal_explosion"]),
    "explosion_far_4": (lambda r: explosion_far(r, "nuclear_distant", 0.9, 4.6, 1.0, 1200, 0.5), ["nuclear_distant"]),
    "collapse_1": (lambda r: collapse(r, "house_collapse", 0.0, 4.6, 0.86, 0.4), ["house_collapse", "rockfall"]),
    "collapse_2": (lambda r: collapse(r, "building_collapse", 0.0, 3.0, 0.92, 6.8), ["building_collapse", "rockfall"]),
    "crumble_1": (lambda r: crumble(r, 1.0, 1.6, 1.0), ["rocks"]),
    "crumble_2": (lambda r: crumble(r, 2.6, 1.8, 0.9), ["rocks"]),
    "crumble_3": (lambda r: crumble(r, 3.9, 1.6, 1.08), ["rocks"]),
    "rocks_1": (lambda r: rocks(r, 0.5, 2.6, 1.0), ["rockfall"]),
    "rocks_2": (lambda r: rocks(r, 4.9, 2.6, 0.92), ["rockfall"]),
    "rocks_3": (lambda r: rocks(r, 9.8, 2.6, 1.05), ["rockfall"]),
    "debris_impact_1": (lambda r: debris_impact(r, "stone_impacts", 0.0, 2.2, 0.9, 74), ["stone_impacts"]),
    "debris_impact_2": (lambda r: debris_impact(r, "stone_impacts", 3.95, 2.2, 0.85, 62), ["stone_impacts"]),
    "debris_impact_3": (lambda r: debris_impact(r, "stone_impacts", 7.95, 2.2, 0.95, 82), ["stone_impacts"]),
    "debris_impact_4": (lambda r: debris_impact(r, "stone_rubble", 0.0, 1.0, 0.8, 90), ["stone_rubble"]),
    "metal_creak_1": (lambda r: metal_creak(r, "creak_slow", 1.4, 4.2, 1.0, 120), ["creak_slow"]),
    "metal_creak_2": (lambda r: metal_creak(r, "creak_long", 0.0, 3.1, 0.85, 90), ["creak_long"]),
    "metal_creak_3": (lambda r: metal_creak(r, "creak_metal", 16.6, 3.2, 0.8, 150), ["creak_metal"]),
    "sparks_1": (lambda r: sparks(r, "sparks", 1.2, 1.3, 60), ["sparks"]),
    "sparks_2": (lambda r: sparks(r, "sparks", 5.9, 1.1, 50), ["sparks"]),
    "sparks_3": (lambda r: sparks(r, "arcs", 6.0, 1.5, 60), ["arcs"]),
    "fire_crackle_1": (lambda r: fire_crackle(r, [("wood_fire", 19.85, 0.0, 1.0), ("wood_fire", 28.75, 0.42, 0.6)]), ["wood_fire"]),
    "fire_crackle_2": (lambda r: fire_crackle(r, [("fire_crackles", 7.65, 0.0, 1.0), ("fire_crackles", 25.45, 0.3, 0.7), ("fire_crackles", 43.25, 0.7, 0.5)]), ["fire_crackles"]),
    "fire_crackle_3": (lambda r: fire_crackle(r, [("wood_fire", 180.0, 0.0, 0.8), ("wood_fire", 165.25, 0.55, 1.0)]), ["wood_fire"]),
    "fire_bed": (fire_bed, ["wood_fire", "fire_crackles"]),
    "rumble": (rumble, ["ominous_rumble", "low_rumble"]),
    "wind": (wind, ["tower_wind", "howling_wind"]),
    "siren_far_1": (lambda r: siren_rec(r, 2.4, 5.6), ["air_raid"]),
    "siren_far_2": (lambda r: siren_rec(r, 40.9, 5.6), ["air_raid"]),
    "siren_far_3": (siren_wail, []),
}


LOOPS = ("fire_bed", "rumble", "wind")
# cues with little energy above 10 kHz are packed at 22.05 kHz (half the bytes, nothing audible lost)
DARK = ("fire_bed", "rumble", "wind", "siren_far", "explosion_far", "metal_creak", "collapse")


def stats(name, x, size=None):
    w = int(0.1 * SR)
    rms = max(np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, max(1, len(x) - w), w // 2))
    s = np.abs(np.fft.rfft(x))
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    cen = (s * f).sum() / s.sum()
    jump = abs(x[0] - x[-1])
    extra = f" ogg {size / 1024:.1f} KB" if size else ""
    print(f"{name:16s} {len(x) / SR:5.2f}s peak {20 * np.log10(np.abs(x).max() + 1e-12):6.1f} dBFS "
          f"rms100 {20 * np.log10(rms):6.1f} centroid {cen:6.0f} Hz edge {jump:.3f}{extra}")


def encode(x, quality=3, rate=SR):
    with tempfile.TemporaryDirectory() as tmp:
        wav, ogg = os.path.join(tmp, "x.wav"), os.path.join(tmp, "x.ogg")
        write_wav(wav, x)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-ac", "1", "-ar", str(rate), "-c:a", "libvorbis",
                        "-q:a", str(quality), "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", ogg], check=True)
        return open(ogg, "rb").read()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", action="append", default=[])
    ap.add_argument("--wav", help="write WAVs to this folder instead of packing")
    ap.add_argument("--replace", action="store_true")
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--stats", action="store_true")
    args = ap.parse_args()
    if args.fetch:
        fetch()
        return
    names = args.only or list(CUES)
    os.makedirs(OUT, exist_ok=True)
    for i, name in enumerate(CUES):
        if name not in names:
            continue
        builder, _ = CUES[name]
        x = builder(np.random.default_rng(2000 + i))
        if args.wav:
            os.makedirs(args.wav, exist_ok=True)
            write_wav(os.path.join(args.wav, name + ".wav"), x)
            stats(name, x)
            continue
        ogg = encode(x, 2 if name in LOOPS else 3, 22050 if name.startswith(DARK) else SR)
        if args.stats:
            stats(name, x, len(ogg))
            continue
        target = os.path.join(OUT, name + ".ogg.b64")
        if os.path.exists(target) and not args.replace:
            sys.exit(f"{target} exists; pass --replace to rebuild")
        with open(target, "wb") as fh:
            fh.write(base64.encodebytes(ogg))
        stats(name, x, len(ogg))


if __name__ == "__main__":
    main()
