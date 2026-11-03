#!/usr/bin/env python3
"""Voices of citizens in peril (issue #138): CC0 Freesound recordings, trimmed and shaped, no vanilla sounds.

Every source is CC0 1.0 (checked on its Freesound page) and listed with page, author and use in
tools/assets/city_citizens_sources.md. The HQ previews are fetched once (never committed) into
~/.cache/viltrumite-sfx/work/freesound/:

    python3 tools/sfx/city_citizens.py --fetch            # download the previews listed in SOURCES
    python3 tools/sfx/city_citizens.py                    # mix every cue and package it (refuses to overwrite)
    python3 tools/sfx/city_citizens.py --replace          # rebuild the committed assets
    python3 tools/sfx/city_citizens.py --only fall_male_1 --wav /tmp/cc   # one cue to WAV for review

Cues: scream (the floor gives way), fall (a long scream gliding down in pitch and losing its top while the
body drops; the engine moves it with the citizen), help (from a ledge or a trapped floor), witness (a short
"no!" / "oh my god" / gasp of a bystander) - each in male and female takes - and crowd_panic (layered crowds
and single cries in the street). Each cue is high-passed, normalised on its loudest 100 ms RMS to a per-class
target and soft-limited under -1 dBFS (helpers shared with tools/sfx/meteor_rain.py and city_collapse.py).
Packaging: mono Vorbis as .ogg.b64 under viltrumitecore/src/main/binassets (decoded by Gradle), each file
under the 48 KB push limit.

No listening test is implied by this script: segments were picked by measured energy, pitch and spectrum,
not auditioned.
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
import meteor_rain as mr  # noqa: E402  (band, sweep, env, place, db, fade)

ROOT = os.path.dirname(os.path.dirname(HERE))
RAW = os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "freesound")
OUT = os.path.join(ROOT, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore", "sounds", "citizen")
SR = mr.SR
LIMIT = 47000  # bytes of .ogg.b64

# key -> (Freesound id, creator, title, HQ preview). All CC0 1.0 (checked on the sound page).
SOURCES = {
    "fear_m": (391362, "guamorims", "Cry of fear - Man.wav", "https://cdn.freesound.org/previews/391/391362_7364394-hq.mp3"),
    "fear_f": (391363, "guamorims", "Cry of fear - Female.wav", "https://cdn.freesound.org/previews/391/391363_7364394-hq.mp3"),
    "fear_crowd": (391364, "guamorims", "Cry of fear - Collective.wav", "https://cdn.freesound.org/previews/391/391364_7364394-hq.mp3"),
    "help_m": (391365, "guamorims", "Cry for help - Man.wav", "https://cdn.freesound.org/previews/391/391365_7364394-hq.mp3"),
    "help_f": (391361, "guamorims", "Cry for help - Female.wav", "https://cdn.freesound.org/previews/391/391361_7364394-hq.mp3"),
    "help_crowd": (391366, "guamorims", "Cry for help- Collective.wav", "https://cdn.freesound.org/previews/391/391366_7364394-hq.mp3"),
    "no_m": (391357, "guamorims", "Scream NO - Man.wav", "https://cdn.freesound.org/previews/391/391357_7364394-hq.mp3"),
    "no_f": (391358, "guamorims", "Scream NO - Female.wav", "https://cdn.freesound.org/previews/391/391358_7364394-hq.mp3"),
    "woman_scream": (222545, "queen_westeros", "WOMAN SCREAM", "https://cdn.freesound.org/previews/222/222545_4079747-hq.mp3"),
    "woman_scream_2": (673306, "courtneyeck", "Woman Scream", "https://cdn.freesound.org/previews/673/673306_13324371-hq.mp3"),
    "forest_m": (873718, "VanEngelen", "VOXScrm_Loud Scream Male Medium Distance In Forest LCR", "https://cdn.freesound.org/previews/873/873718_9285510-hq.mp3"),
    "fall_m": (345455, "Artmasterrich", "Male_Fall_Death_01", "https://cdn.freesound.org/previews/345/345455_4038846-hq.mp3"),
    "fall_f": (713549, "IENBA", "Female Falling Screams", "https://cdn.freesound.org/previews/713/713549_9616576-hq.mp3"),
    "helphelp": (87531, "Whiprealgood", "Help! Help!.wav", "https://cdn.freesound.org/previews/87/87531_1380975-hq.mp3"),
    "asking": (814235, "TyrantTim", "Asking For help", "https://cdn.freesound.org/previews/814/814235_16297603-hq.mp3"),
    "get_help": (315843, "melnjay87", "male saying someone get help.wav", "https://cdn.freesound.org/previews/315/315843_5372385-hq.mp3"),
    "help_me_f": (422689, "niamhd00145229", "help me!.wav", "https://cdn.freesound.org/previews/422/422689_8457618-hq.mp3"),
    "help_me_2": (529115, "Feed_", "help me", "https://cdn.freesound.org/previews/529/529115_10119156-hq.mp3"),
    "help_us": (800903, "wordwiz1", "Help us", "https://cdn.freesound.org/previews/800/800903_12069828-hq.mp3"),
    "omg_m": (692784, "Elijahbaby", "masculine \"oh, my god\"", "https://cdn.freesound.org/previews/692/692784_14393941-hq.mp3"),
    "omg_m2": (387941, "Sami_Kullstr\u00f6m", "Oh my god.wav", "https://cdn.freesound.org/previews/387/387941_7282645-hq.mp3"),
    "oh_no_f": (639860, "owstu", "Female Voc txt _Oh No.wav", "https://cdn.freesound.org/previews/639/639860_1690102-hq.mp3"),
    "gasp_f": (346213, "FloydP", "Women gasping.wav", "https://cdn.freesound.org/previews/346/346213_6228432-hq.mp3"),
    "crowd_panic": (491405, "IENBA", "Crowd in panic", "https://cdn.freesound.org/previews/491/491405_9616576-hq.mp3"),
    "crowd_scream": (459101, "DigestContent", "Crowd Screaming", "https://cdn.freesound.org/previews/459/459101_8541784-hq.mp3"),
}

# class -> loudest-100 ms RMS target dBFS
TARGET = {"scream": -10.0, "fall": -10.0, "help": -11.0, "witness": -12.0, "crowd": -13.0}

_cache = {}


def fetch():
    os.makedirs(RAW, exist_ok=True)
    for key, (fid, _user, _title, url) in SOURCES.items():
        path = os.path.join(RAW, f"{fid}.mp3")
        if os.path.isfile(path):
            continue
        if url is None:
            sys.exit(f"{key}: fetch the Openverse result with the sound-design helper and copy it to {path}")
        data = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=60).read()
        with open(path, "wb") as f:
            f.write(data)
        print("fetched", key, fid)


def source(key):
    if key not in _cache:
        path = os.path.join(RAW, f"{SOURCES[key][0]}.mp3")
        if not os.path.isfile(path):
            sys.exit(f"Missing source {key}; run: python3 tools/sfx/city_citizens.py --fetch")
        out = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"],
                             capture_output=True, check=True).stdout
        _cache[key] = np.frombuffer(out, np.int16).astype(np.float64) / 32768.0
    return _cache[key]


def seg(key, start, end, pitch=1.0):
    """The source from `start` to `end` seconds, varispeed by `pitch` (0.9 = lower and longer)."""
    x = source(key)
    n = int((end - start) * SR / pitch)
    pos = start * SR + np.arange(n) * pitch
    pos = pos[pos < len(x) - 1]
    i = pos.astype(int)
    f = pos - i
    return x[i] * (1 - f) + x[i + 1] * f


def glide(x, p0, p1, curve=1.6):
    """Varispeed whose rate moves from p0 to p1 over the cue: a scream that sinks while the body falls away."""
    out = []
    pos = 0.0
    n = len(x)
    while pos < n - 1:
        t = pos / n
        out.append(pos)
        pos += p0 + (p1 - p0) * t ** curve
    pos = np.array(out)
    i = pos.astype(int)
    f = pos - i
    return x[i] * (1 - f) + x[i + 1] * f


def lvl(x, target_db):
    return x * mr.db(target_db) / (np.sqrt(np.mean(x ** 2)) + 1e-12)


def finish(x, cls, fout=0.08):
    x = mr.band(x, lo=110.0, order=2.0)
    w = int(0.1 * SR)
    loud = max(np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, max(1, len(x) - w), w // 2))
    x = x * mr.db(TARGET[cls]) / max(loud, 1e-9)
    knee, ceiling = mr.db(-4.0), mr.db(-1.0)
    over = np.abs(x) > knee
    x[over] = np.sign(x[over]) * (knee + (ceiling - knee) * np.tanh((np.abs(x[over]) - knee) / (ceiling - knee)))
    return mr.fade(x, 0.006, fout)


def voice(key, start, end, cls, pitch=1.0, fout=0.08):
    return finish(seg(key, start, end, pitch), cls, fout)


def fall(key, start, end, p0, p1, top_end):
    """A falling scream: the pitch sinks (p0 -> p1), the top closes from 11 kHz to `top_end`, a slight fade-down."""
    x = glide(seg(key, start, end), p0, p1)
    x = mr.sweep(x, lambda t: 90.0, lambda t: 11000.0 * (top_end / 11000.0) ** (t ** 1.3))
    x = x * np.linspace(1.0, mr.db(-5.0), len(x)) ** 1.0
    return finish(x, "fall", 0.35)


def crowd(layers, length, rng):
    """Street panic: crowd beds plus single cries panned in time, each layer at its own level."""
    out = np.zeros(int(length * SR))
    for key, start, end, at, gain_db, pitch, hi in layers:
        x = seg(key, start, end, pitch)
        if hi:
            x = mr.band(x, hi=hi)  # a cry farther down the street
        x = lvl(x, gain_db)
        x = mr.fade(x, 0.05, min(0.4, len(x) / SR / 3))
        mr.place(out, x, at)
    out = out * mr.env(length, 0.12, 0.9, hold=length - 1.2, curve=3.0)
    return finish(out, "crowd", 0.6)


CUES = {
    "scream_male_1": lambda rng: voice("fear_m", 2.62, 3.86, "scream"),
    "scream_male_2": lambda rng: voice("forest_m", 6.04, 7.8, "scream", fout=0.25),
    "scream_male_3": lambda rng: voice("forest_m", 13.78, 16.2, "scream", fout=0.25),
    "scream_female_1": lambda rng: voice("fear_f", 3.0, 4.5, "scream"),
    "scream_female_2": lambda rng: voice("woman_scream", 0.0, 2.15, "scream", fout=0.2),
    "scream_female_3": lambda rng: voice("woman_scream_2", 0.36, 2.7, "scream", fout=0.2),
    "fall_male_1": lambda rng: fall("fall_m", 0.04, 1.88, 0.98, 0.8, 3800.0),
    "fall_male_2": lambda rng: fall("forest_m", 39.3, 42.1, 1.0, 0.84, 3600.0),
    "fall_female_1": lambda rng: fall("fall_f", 11.18, 13.3, 1.0, 0.86, 4200.0),
    "fall_female_2": lambda rng: fall("fall_f", 30.12, 31.95, 1.0, 0.84, 4000.0),
    "help_male_1": lambda rng: voice("helphelp", 0.02, 0.98, "help"),
    "help_male_2": lambda rng: voice("asking", 0.2, 1.34, "help"),
    "help_male_3": lambda rng: voice("get_help", 0.24, 3.55, "help"),
    "help_male_4": lambda rng: voice("help_m", 4.62, 5.8, "help"),
    "help_female_1": lambda rng: voice("help_me_f", 0.12, 1.05, "help"),
    "help_female_2": lambda rng: voice("help_me_2", 0.26, 1.24, "help"),
    "help_female_3": lambda rng: voice("help_us", 0.22, 1.06, "help"),
    "help_female_4": lambda rng: voice("help_f", 3.14, 4.55, "help"),
    "witness_male_1": lambda rng: voice("no_m", 0.16, 1.3, "witness"),
    "witness_male_2": lambda rng: voice("omg_m", 1.72, 2.6, "witness"),
    "witness_male_3": lambda rng: voice("omg_m2", 0.0, 1.24, "witness"),
    "witness_female_1": lambda rng: voice("no_f", 0.08, 1.06, "witness"),
    "witness_female_2": lambda rng: voice("oh_no_f", 0.64, 1.76, "witness"),
    "witness_female_3": lambda rng: voice("gasp_f", 0.38, 1.18, "witness"),
    "crowd_panic_1": lambda rng: crowd([
        ("crowd_panic", 2.0, 6.6, 0.0, -16.0, 1.0, None),
        ("help_crowd", 0.6, 4.4, 0.35, -18.0, 1.0, None),
        ("fear_f", 1.56, 2.6, 1.7, -24.0, 1.0, 3500.0),
        ("fear_m", 0.08, 0.9, 2.9, -25.0, 0.96, 3000.0)], 4.6, rng),
    "crowd_panic_2": lambda rng: crowd([
        ("crowd_scream", 3.0, 7.6, 0.0, -15.0, 1.0, None),
        ("fear_crowd", 0.14, 1.86, 0.4, -18.0, 1.0, None),
        ("fear_crowd", 2.52, 3.9, 2.4, -19.0, 0.97, None),
        ("no_f", 1.46, 2.16, 1.3, -25.0, 1.0, 3200.0)], 4.6, rng),
    "crowd_panic_3": lambda rng: crowd([
        ("crowd_panic", 14.0, 18.6, 0.0, -16.0, 1.0, None),
        ("help_crowd", 4.0, 7.6, 0.6, -18.0, 0.98, None),
        ("no_m", 2.0, 3.14, 2.2, -25.0, 1.0, 3000.0)], 4.6, rng),
}


def write_wav(path, x):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())


def encode(wav):
    """Vorbis, mono; the first (rate, quality) that fits the push limit."""
    for rate, q in [(44100, 4), (44100, 3), (32000, 3), (32000, 2), (22050, 2)]:
        with tempfile.TemporaryDirectory() as tmp:
            ogg = os.path.join(tmp, "x.ogg")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-ac", "1", "-ar", str(rate), "-c:a", "libvorbis", "-q:a", str(q), ogg], check=True)
            payload = base64.encodebytes(open(ogg, "rb").read())
        if len(payload) <= LIMIT:
            return payload, rate, q
    raise SystemExit(f"{wav}: does not fit {LIMIT} bytes")


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
        x = CUES[name](np.random.default_rng(3800 + i))
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
            payload, rate, q = encode(wav)
        with open(target, "wb") as f:
            f.write(payload)
        print("packaged", name, f"{len(x) / SR:.2f}s", rate, "Hz q", q, len(payload), "bytes")


if __name__ == "__main__":
    main()
