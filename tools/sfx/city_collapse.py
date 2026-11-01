#!/usr/bin/env python3
"""Structural collapse sounds (world/collapse): CC0 Freesound recordings layered and shaped, no vanilla sounds.

Every source is CC0 1.0 and listed with its page, author and use in tools/assets/city_collapse_sources.md.
The HQ previews are fetched once (never committed) into ~/.cache/viltrumite-sfx/work/freesound/:

    python3 tools/sfx/city_collapse.py --fetch            # download the previews listed in SOURCES
    python3 tools/sfx/city_collapse.py                    # mix every cue and package it (refuses to overwrite)
    python3 tools/sfx/city_collapse.py --replace          # rebuild the committed assets
    python3 tools/sfx/city_collapse.py --only roar_1 --wav /tmp/cc   # one cue to WAV for review

Seeded, so a rerun gives the same mix. Each cue is normalised on its loudest 100 ms RMS to a per-class target
and soft-limited under -1 dBFS (DSP helpers shared with tools/sfx/meteor_rain.py). Packaging: mono Vorbis as
.ogg.b64 under viltrumitecore/src/main/binassets (decoded by Gradle), quality and rate lowered for the long
low cues so each file stays under the 48 KB push limit (a rumble loses nothing at 22 kHz).

No listening test is implied by this script: levels and spectra are measured, not auditioned.
"""
import argparse
import base64
import os
import subprocess
import sys
import tempfile
import urllib.request
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import meteor_rain as mr  # noqa: E402  (band, sweep, noise, env, place, db, grains, fade)

ROOT = os.path.dirname(os.path.dirname(HERE))
RAW = os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "freesound")
OUT = os.path.join(ROOT, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore", "sounds", "collapse")
SR = mr.SR
LIMIT = 47000  # bytes of .ogg.b64

# key -> (Freesound id, creator, title, HQ preview). All CC0 1.0 (checked on the sound page).
SOURCES = {
    "collapse": (712918, "greyfeather", "building collapse / demolition", "https://cdn.freesound.org/previews/712/712918_15139380-hq.mp3"),
    "concrete": (843339, "loganzsound", "Concrete Breaks Several Denoised", "https://cdn.freesound.org/previews/843/843339_16682330-hq.mp3"),
    "smash": (522099, "magnuswaker", "Concrete SMASH 2", "https://cdn.freesound.org/previews/522/522099_11537497-hq.mp3"),
    "heavy": (397596, "Nightflame", "Heavy Impacts", "https://cdn.freesound.org/previews/397/397596_3302499-hq.mp3"),
    "boom": (539968, "Za-Games", "Deep Boom", "https://cdn.freesound.org/previews/539/539968_12029332-hq.mp3"),
    "groan": (496836, "hinchinbrook", "Metallic Groan", "https://cdn.freesound.org/previews/496/496836_9847211-hq.mp3"),
    "bend": (556714, "NachtmahrTV", "Bending Metal", "https://cdn.freesound.org/previews/556/556714_5620304-hq.mp3"),
    "creak": (172411, "EagleStealthTeam", "Creaking Metal - Slow", "https://cdn.freesound.org/previews/172/172411_2915633-hq.mp3"),
    "metal_hit": (697748, "SoundDesignForYou", "Metal Impact Creak Resonant", "https://cdn.freesound.org/previews/697/697748_13085623-hq.mp3"),
    "rockfall": (389303, "AlanCat", "rockfall2a.wav", "https://cdn.freesound.org/previews/389/389303_5486695-hq.mp3"),
    "rockslide": (608957, "NinjaSharkStudios", "Rockslide_1.wav", "https://cdn.freesound.org/previews/608/608957_4067-hq.mp3"),
    "debris": (703247, "xkeril", "Big falling debris (crash)", "https://cdn.freesound.org/previews/703/703247_13504080-hq.mp3"),
    "stones": (554148, "Nox_Sound", "Foley_Impact_Stones_Strong_Debris", "https://cdn.freesound.org/previews/554/554148_9250976-hq.mp3"),
    "subsonic": (453405, "kyles", "bass rumble deep subterranean subsonic", "https://cdn.freesound.org/previews/453/453405_612689-hq.mp3"),
    "earth": (217657, "Reitanna", "earth rumble.wav", "https://cdn.freesound.org/previews/217/217657_950925-hq.mp3"),
    "quake": (199803, "TheMinkman", "Quake Short.wav", "https://cdn.freesound.org/previews/199/199803_3491501-hq.mp3"),
    "deep": (184313, "swiftoid", "Cinematic Deep Rumble", "https://cdn.freesound.org/previews/184/184313_854782-hq.mp3"),
    "rubble": (569510, "Sheyvan", "Stone Impact Rubble Debris 14", "https://cdn.freesound.org/previews/569/569510_3248005-hq.mp3"),
    "clatter": (461696, "leonelmail", "Aggressive Clatter 01", "https://cdn.freesound.org/previews/461/461696_4437257-hq.mp3"),
    "gravel": (34175, "Dalibor", "pour out gravel on the stairs.wav", "https://cdn.freesound.org/previews/34/34175_25449-hq.mp3"),
    "splinter": (869103, "ozymandias37", "Tall dead tree splintering then falling", "https://cdn.freesound.org/previews/869/869103_11162537-hq.mp3"),
}

TARGET = {"impact": -9.0, "crumble": -12.0, "groan": -15.0, "crack": -11.0, "shed": -14.0, "roar": -11.0, "ground": -8.0,
          "debris": -13.0, "rumble": -13.0, "tail": -17.0, "dust": -19.0, "distant": -12.0}

_cache = {}


def fetch():
    os.makedirs(RAW, exist_ok=True)
    for key, (fid, _user, _title, url) in SOURCES.items():
        path = os.path.join(RAW, f"fs_{fid}.mp3")
        if not os.path.isfile(path):
            data = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=60).read()
            with open(path, "wb") as f:
                f.write(data)
            print("fetched", key, fid)


def source(key):
    if key not in _cache:
        path = os.path.join(RAW, f"fs_{SOURCES[key][0]}.mp3")
        if not os.path.isfile(path):
            sys.exit(f"Missing source {key}; run: python3 tools/sfx/city_collapse.py --fetch")
        out = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"],
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


def rms(x):
    return np.sqrt(np.mean(x ** 2)) + 1e-12


def lvl(x, target_db):
    """Scale a layer to an RMS level (dBFS), so the mix ratios do not depend on how loud a recording was made."""
    return x * mr.db(target_db) / rms(x)


def tail_env(L, attack, hold, decay):
    t = mr.t_axis(L)
    e = np.clip(t / max(attack, 1e-4), 0, 1)
    return e * np.exp(-np.clip(t - attack - hold, 0, None) / max(decay, 1e-4))


def finish(x, cls):
    w = int(0.1 * SR)
    loud = max(np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, max(1, len(x) - w), w // 2))
    x = x * mr.db(TARGET[cls]) / max(loud, 1e-9)
    knee, ceiling = mr.db(-4.0), mr.db(-1.0)
    over = np.abs(x) > knee
    x[over] = np.sign(x[over]) * (knee + (ceiling - knee) * np.tanh((np.abs(x[over]) - knee) / (ceiling - knee)))
    return mr.fade(x, 0.002, 0.12)


# ------------------------------------------------------------------ cues (L = seconds)

def impact(rng, p, start):
    """Phase 1 hit: concrete smash on top of a heavy thud and a short deep boom (1.7 s)."""
    L = 1.7
    x = np.zeros(int(L * SR))
    mr.place(x, lvl(mr.band(seg("smash", 0.0, 1.25, p), 120, 9000), -16), 0.0)
    mr.place(x, lvl(mr.band(seg("heavy", start, 1.4, p * 0.9), 40, 3000) * tail_env(1.4, 0.004, 0.08, 0.35), -15), 0.0)
    mr.place(x, lvl(mr.band(seg("boom", 0.35, 1.6, p * 0.95), 25, 400) * tail_env(1.6, 0.01, 0.1, 0.5), -17), 0.01)
    return finish(x, "impact")


def crumble(rng, p, start):
    """Right after the hit: a burst of crumbling concrete and rubble (1.5 s)."""
    L = 1.5
    x = np.zeros(int(L * SR))
    mr.place(x, lvl(mr.band(seg("concrete", start, 1.5, p), 150, 8000) * tail_env(1.5, 0.005, 0.25, 0.4), -16), 0.0)
    mr.place(x, lvl(mr.band(seg("rubble", 0.0, 0.9, p * 0.9), 200, 7000), -19), 0.12)
    mr.place(x, lvl(mr.grains(1.3, rng, 26, 900, 5000, decay=(0.006, 0.03), density=lambda t: np.exp(-3 * t)), -24), 0.05)
    return finish(x, "crumble")


def groan(rng, p, which):
    """Phase 2: the steel frame groans and bends (2.8 s)."""
    L = 2.8
    x = np.zeros(int(L * SR))
    e = np.sin(np.pi * np.clip(mr.t_axis(L) / L, 0, 1)) ** 0.7
    if which == 1:
        a = mr.band(seg("groan", 3.6, L, p * 0.85), 60, 4000)
        b = mr.band(seg("creak", 6.0, L, p * 0.7), 80, 3000)
    else:
        a = mr.band(seg("bend", 0.25, L, p * 0.8), 60, 4000)
        b = mr.band(seg("groan", 5.8, L, p * 0.75), 80, 3000)
    mr.place(x, lvl(a * e, -17), 0.0)
    mr.place(x, lvl(b * e, -21), 0.0)
    mr.place(x, lvl(mr.band(seg("metal_hit", 0.0, 1.6, p * 0.8), 60, 5000) * tail_env(1.6, 0.003, 0.1, 0.5), -22), 0.15 + 0.3 * which)
    return finish(x, "groan")


def crack(rng, p, start, wood):
    """Phase 2: one sharp structural crack (0.9 s)."""
    L = 0.9
    x = np.zeros(int(L * SR))
    mr.place(x, lvl(mr.band(seg("concrete", start, 0.9, p), 250, 10000) * tail_env(0.9, 0.002, 0.05, 0.18), -14), 0.0)
    if wood:
        mr.place(x, lvl(mr.band(seg("splinter", wood, 0.8, p), 300, 9000) * tail_env(0.8, 0.002, 0.08, 0.2), -18), 0.01)
    mr.place(x, lvl(mr.band(seg("heavy", 1.62, 0.7, p * 1.1), 40, 900) * tail_env(0.7, 0.003, 0.03, 0.15), -20), 0.0)
    return finish(x, "crack")


def shed(rng, p, variant):
    """A facade piece breaks off and clatters (1.0 s)."""
    L = 1.0
    x = np.zeros(int(L * SR))
    if variant == 1:
        mr.place(x, lvl(mr.band(seg("clatter", 0.3, 0.85, p), 150, 9000), -16), 0.0)
    elif variant == 2:
        mr.place(x, lvl(mr.band(seg("stones", 1.95, 0.95, p), 150, 9000) * tail_env(0.95, 0.002, 0.1, 0.25), -16), 0.0)
    else:
        mr.place(x, lvl(mr.band(seg("rubble", 0.0, 0.95, p * 0.85), 150, 8000), -16), 0.0)
    mr.place(x, lvl(mr.grains(0.9, rng, 12, 1200, 6000, decay=(0.005, 0.02), density=lambda t: np.exp(-2.5 * t)), -26), 0.08)
    return finish(x, "shed")


def roar(rng, p, start):
    """Phase 3: the section coming down, a roar of masonry over a deep rumble (3.4 s)."""
    L = 3.4
    x = np.zeros(int(L * SR))
    t = mr.t_axis(L)
    swell = np.clip(t / 0.5, 0, 1) * np.exp(-np.clip(t - 2.2, 0, None) / 0.6)
    mr.place(x, lvl(mr.band(seg("collapse", 0.1, L, p), 50, 7000) * swell, -15), 0.0)
    mr.place(x, lvl(mr.band(seg("rockslide", start, L, p * 0.9), 120, 6000) * swell, -19), 0.0)
    mr.place(x, lvl(mr.band(seg("deep", 3.0, L, p), 25, 300) * swell, -18), 0.0)
    mr.place(x, lvl(mr.grains(L, rng, 60, 700, 5000, decay=(0.008, 0.04), density=lambda q: 0.3 + q), -27), 0.0)
    return finish(x, "roar")


def ground(rng, p, start):
    """The section hits the ground: a slam, big debris crash and a deep boom (2.4 s)."""
    L = 2.4
    x = np.zeros(int(L * SR))
    mr.place(x, lvl(mr.band(seg("debris", 0.12, 2.4, p), 60, 8000) * tail_env(2.4, 0.003, 0.25, 0.7), -14), 0.0)
    mr.place(x, lvl(mr.band(seg("heavy", start, 1.3, p * 0.85), 30, 2500) * tail_env(1.3, 0.003, 0.08, 0.35), -14), 0.0)
    mr.place(x, lvl(mr.band(seg("boom", 0.4, 2.4, p * 0.85), 20, 300) * tail_env(2.4, 0.01, 0.2, 0.8), -14), 0.0)
    return finish(x, "ground")


def scatter(rng, p, start):
    """Debris scattering over the street after the slam (2.0 s)."""
    L = 2.0
    x = np.zeros(int(L * SR))
    mr.place(x, lvl(mr.band(seg("rockfall", start, L, p), 150, 8000) * tail_env(L, 0.01, 0.5, 0.6), -16), 0.0)
    mr.place(x, lvl(mr.band(seg("stones", 4.0, 1.6, p * 0.95), 150, 9000) * tail_env(1.6, 0.003, 0.2, 0.4), -19), 0.1)
    mr.place(x, lvl(mr.grains(L, rng, 50, 800, 6000, decay=(0.006, 0.03), density=lambda q: np.exp(-2 * q)), -25), 0.0)
    return finish(x, "debris")


def rumble(rng, p, which):
    """The sub layer felt under the slam (3.6 s)."""
    L = 3.6
    x = np.zeros(int(L * SR))
    e = tail_env(L, 0.06, 0.6, 1.1)
    if which == 1:
        mr.place(x, lvl(mr.band(seg("subsonic", 29.6, L, p), 20, 180) * e, -15), 0.0)
        mr.place(x, lvl(mr.band(seg("earth", 10.0, L, p), 25, 400) * e, -19), 0.0)
    else:
        mr.place(x, lvl(mr.band(seg("quake", 4.9, L, p), 25, 500) * e, -16), 0.0)
        mr.place(x, lvl(mr.band(seg("deep", 21.8, L, p), 20, 250) * e, -18), 0.0)
    return finish(x, "rumble")


def tail(rng, p, start):
    """Phase 4: rubble settling, stones trickling down the pile (3.6 s)."""
    L = 3.6
    x = np.zeros(int(L * SR))
    t = mr.t_axis(L)
    e = np.clip(t / 0.2, 0, 1) * np.exp(-t / 1.6)
    mr.place(x, lvl(mr.band(seg("rockslide", start, L, p), 150, 6000) * e, -21), 0.0)
    mr.place(x, lvl(mr.band(seg("gravel", 2.6, 2.4, p * 0.85), 200, 7000) * tail_env(2.4, 0.05, 0.6, 0.6), -24), 0.4)
    mr.place(x, lvl(mr.grains(L, rng, 34, 700, 5000, decay=(0.006, 0.03), density=lambda q: np.exp(-1.6 * q)), -26), 0.0)
    return finish(x, "tail")


def dust(rng, p, start):
    """Phase 4: gravel and dust pouring off the pile, a soft hiss (3.2 s)."""
    L = 3.2
    x = np.zeros(int(L * SR))
    t = mr.t_axis(L)
    e = np.sin(np.pi * np.clip(t / L, 0, 1)) ** 0.8
    mr.place(x, lvl(mr.band(seg("gravel", start, L, p * 0.8), 300, 6000) * e, -21), 0.0)
    mr.place(x, lvl(mr.band(mr.noise(L, rng, 1.0), 400, 3500) * e * (0.7 + 0.3 * np.sin(2 * np.pi * 0.7 * t)), -30), 0.0)
    return finish(x, "dust")


def distant(rng, p, which):
    """The whole collapse heard far away: a dull rolling boom with a long tail (4.2 s)."""
    L = 4.2
    x = np.zeros(int(L * SR))
    t = mr.t_axis(L)
    e = np.clip(t / 0.08, 0, 1) * np.exp(-np.clip(t - 0.4, 0, None) / 1.3)
    mr.place(x, lvl(mr.band(seg("boom", 0.3, L, p * 0.8), 20, 260) * e, -14), 0.0)
    mr.place(x, lvl(mr.band(seg("collapse", 0.4, 2.6, p * 0.75), 30, 500) * tail_env(2.6, 0.05, 0.6, 0.8), -18), 0.15)
    mr.place(x, lvl(mr.band(seg("deep" if which == 1 else "earth", 8.0, L, p), 20, 220) * e, -19), 0.0)
    return finish(x, "distant")


# name -> (builder(rng) -> signal)
CUES = {
    "impact_1": lambda r: impact(r, 1.0, 0.28),
    "impact_2": lambda r: impact(r, 0.88, 3.03),
    "crumble_burst_1": lambda r: crumble(r, 1.0, 1.35),
    "crumble_burst_2": lambda r: crumble(r, 0.9, 3.15),
    "metal_groan_1": lambda r: groan(r, 1.0, 1),
    "metal_groan_2": lambda r: groan(r, 0.92, 2),
    "crack_1": lambda r: crack(r, 1.0, 1.38, 0.0),
    "crack_2": lambda r: crack(r, 0.9, 6.1, 3.4),
    "crack_3": lambda r: crack(r, 1.08, 3.18, 6.9),
    "facade_shed_1": lambda r: shed(r, 1.0, 1),
    "facade_shed_2": lambda r: shed(r, 0.95, 2),
    "facade_shed_3": lambda r: shed(r, 1.05, 3),
    "roar_1": lambda r: roar(r, 1.0, 5.0),
    "roar_2": lambda r: roar(r, 0.9, 7.4),
    "ground_impact_1": lambda r: ground(r, 1.0, 4.62),
    "ground_impact_2": lambda r: ground(r, 0.86, 0.28),
    "debris_scatter_1": lambda r: scatter(r, 1.0, 0.22),
    "debris_scatter_2": lambda r: scatter(r, 0.92, 1.9),
    "rumble_1": lambda r: rumble(r, 1.0, 1),
    "rumble_2": lambda r: rumble(r, 0.9, 2),
    "tail_1": lambda r: tail(r, 1.0, 9.0),
    "tail_2": lambda r: tail(r, 0.9, 1.0),
    "dust_1": lambda r: dust(r, 1.0, 0.3),
    "dust_2": lambda r: dust(r, 0.9, 3.0),
    "distant_1": lambda r: distant(r, 1.0, 1),
    "distant_2": lambda r: distant(r, 0.88, 2),
}

# sources each cue uses (for the sources file)
USES = {
    "impact": ["smash", "heavy", "boom"], "crumble_burst": ["concrete", "rubble"], "metal_groan": ["groan", "creak", "bend", "metal_hit"],
    "crack": ["concrete", "splinter", "heavy"], "facade_shed": ["clatter", "stones", "rubble"], "roar": ["collapse", "rockslide", "deep"],
    "ground_impact": ["debris", "heavy", "boom"], "debris_scatter": ["rockfall", "stones"], "rumble": ["subsonic", "earth", "quake", "deep"],
    "tail": ["rockslide", "gravel"], "dust": ["gravel"], "distant": ["boom", "collapse", "deep", "earth"],
}


def write_wav(path, x):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())


def encode(wav, low):
    """Vorbis, mono; the first (rate, quality) that fits the push limit. Low cues start at a lower rate."""
    tries = [(22050, 4), (22050, 3), (22050, 2), (16000, 2)] if low else [(44100, 4), (44100, 3), (32000, 3), (32000, 2), (22050, 2)]
    for rate, q in tries:
        with tempfile.TemporaryDirectory() as tmp:
            ogg = os.path.join(tmp, "x.ogg")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-ac", "1", "-ar", str(rate), "-c:a", "libvorbis", "-q:a", str(q), ogg], check=True)
            payload = base64.encodebytes(open(ogg, "rb").read())
        if len(payload) <= LIMIT:
            return payload, rate, q
    raise SystemExit(f"{wav}: does not fit {LIMIT} bytes")


LOW = ("rumble", "distant", "dust")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--only", action="append", default=[])
    ap.add_argument("--wav", help="write WAVs to this folder instead of packaging")
    ap.add_argument("--replace", action="store_true")
    args = ap.parse_args()
    if args.fetch:
        fetch()
        return
    names = args.only or list(CUES)
    os.makedirs(OUT, exist_ok=True)
    for i, name in enumerate(CUES):
        if name not in names:
            continue
        x = CUES[name](np.random.default_rng(2000 + i))
        if args.wav:
            os.makedirs(args.wav, exist_ok=True)
            write_wav(os.path.join(args.wav, name + ".wav"), x)
            print(name, f"{len(x) / SR:.2f}s", f"peak {20 * np.log10(np.abs(x).max() + 1e-12):.1f} dBFS")
            continue
        target = os.path.join(OUT, name + ".ogg.b64")
        if os.path.exists(target) and not args.replace:
            sys.exit(f"{target} exists; use --replace")
        with tempfile.TemporaryDirectory() as tmp:
            wav = os.path.join(tmp, name + ".wav")
            write_wav(wav, x)
            payload, rate, q = encode(wav, name.startswith(LOW))
        with open(target, "wb") as f:
            f.write(payload)
        print("packaged", name, f"{len(x) / SR:.2f}s", rate, "Hz q", q, len(payload), "bytes")


if __name__ == "__main__":
    main()
