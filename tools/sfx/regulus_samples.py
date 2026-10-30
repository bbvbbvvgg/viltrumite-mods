#!/usr/bin/env python3
"""Regulus Reborn: the physical cues mixed from real CC0 / public-domain recordings.

The synthesis in tools/sfx/regulus_reborn.py stays the voice of *stopped time* (glass, swells cut to
silence, the heart); everything that is a body, a foot, a fist, cloth, rock or air moving is mixed here
from recordings, with a quiet layer of the old synthesised cue on top so the two families still match.

Sources (all CC0 1.0 or public domain, never committed, cached under ~/.cache/viltrumite-sfx/regulus):
  * Kenney audio packs (kenney.nl, CC0): "Impact Sounds", "RPG Audio", "Sci-fi Sounds";
  * Wikimedia Commons files marked CC0 / public domain (pdsounds.org transfers and own work);
  * Freesound CC0 recordings fetched through Openverse with the sound-design skill helper.
Every source, its creator, licence and landing page is listed in SOURCES and in
tools/assets/regulus_sources.md / CREDITS.md.

Usage (repository root):
    python3 tools/sfx/regulus_samples.py --fetch     # download the sources into the cache
    python3 tools/sfx/regulus_samples.py             # mix and write the .ogg.b64 assets
    python3 tools/sfx/regulus_samples.py --check     # remix and compare (skipped if not cached)
    python3 tools/sfx/regulus_samples.py --wav DIR   # also dump the mixes as WAV for listening

A layer takes `length` seconds of a source from `start` (None = 5 ms before its first transient),
plays it at `pitch` (varispeed), band-limits it to lo..hi Hz, applies `gain` dB and lands `delay`
seconds into the cue; "synth:<cue>" layers are the regulus_reborn.py cue itself. The sum is faded and
level-matched (above 120 Hz) to the loudest 100 ms of the synthesised cue it replaces (so the in-game balance of the
kit stays where it was) under a -1 dBFS peak ceiling. Mono 44.1 kHz Vorbis like the rest of the kit.

Nothing here implies a listening test: levels are measured, not auditioned.
"""
import argparse
import base64
import hashlib
import http.cookiejar
import json
import os
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import regulus_reborn as rr  # noqa: E402

ROOT = rr.ROOT
SR = rr.SR
OUT = rr.OUT
CACHE = Path(os.environ.get("OPENVERSE_SFX_HOME", str(Path.home() / ".cache" / "viltrumite-sfx"))).expanduser()
HERE = CACHE / "regulus"
OV_RAW = CACHE / "work" / "raw"
HELPER = ROOT / ".agents/skills/sound-design/scripts/openverse_sfx.py"
UA = "ViltrumiteSfxTool/1.0 (https://github.com/bbvbbvvgg/viltrumite-mods; dev tooling)"

KENNEY = {"impact": "impact-sounds", "rpg": "rpg-audio", "scifi": "sci-fi-sounds"}


def kenney(pack, file):
    return {"origin": "kenney", "pack": pack, "file": "Audio/" + file, "creator": "Kenney (kenney.nl)",
            "license": "CC0 1.0", "page": "https://kenney.nl/assets/" + KENNEY[pack]}


def commons(title, creator, license_name):
    return {"origin": "commons", "title": title, "creator": creator, "license": license_name,
            "page": "https://commons.wikimedia.org/wiki/" + urllib.parse.quote(title.replace(" ", "_"))}


def openverse(uuid, freesound, creator, title):
    return {"origin": "openverse", "uuid": uuid, "creator": creator, "title": title, "license": "CC0 1.0",
            "page": "https://freesound.org/s/%d/" % freesound}


SOURCES = {
    # Kenney "Impact Sounds"
    **{"punch_h%d" % i: kenney("impact", "impactPunch_heavy_00%d.ogg" % i) for i in range(5)},
    **{"punch_m%d" % i: kenney("impact", "impactPunch_medium_00%d.ogg" % i) for i in range(5)},
    **{"soft_h%d" % i: kenney("impact", "impactSoft_heavy_00%d.ogg" % i) for i in range(5)},
    **{"mining%d" % i: kenney("impact", "impactMining_00%d.ogg" % i) for i in range(5)},
    **{"glass_l%d" % i: kenney("impact", "impactGlass_light_00%d.ogg" % i) for i in range(3)},
    **{"generic_l%d" % i: kenney("impact", "impactGeneric_light_00%d.ogg" % i) for i in range(3)},
    # Kenney "RPG Audio"
    **{"cloth%d" % i: kenney("rpg", "cloth%d.ogg" % i) for i in range(1, 5)},
    **{"step%d" % i: kenney("rpg", "footstep0%d.ogg" % i) for i in range(10)},
    "knife1": kenney("rpg", "knifeSlice.ogg"),
    "knife2": kenney("rpg", "knifeSlice2.ogg"),
    "chop": kenney("rpg", "chop.ogg"),
    # Kenney "Sci-fi Sounds"
    "lowboom0": kenney("scifi", "lowFrequency_explosion_000.ogg"),
    "lowboom1": kenney("scifi", "lowFrequency_explosion_001.ogg"),
    **{"crunch%d" % i: kenney("scifi", "explosionCrunch_00%d.ogg" % i) for i in range(5)},
    # Wikimedia Commons
    "heart": commons("File:Human heart rate.flac", "Wilfredor", "CC0 1.0"),
    "gravel": commons("File:Assorted gravel rock and stones.ogg", "stephan (pdsounds.org)", "Public domain"),
    "thud": commons("File:Dull thud.ogg", "gregoryweir (pdsounds.org)", "Public domain"),
    "quake": commons("File:Earthquake.ogg", "igel457 (pdsounds.org)", "Public domain"),
    "firework": commons("File:Explosion-LS100155.ogg", "Fg2", "Public domain"),
    # Freesound CC0 via Openverse
    "fast_swing": openverse("518ead89-8e89-4614-b954-bb7a844f3c42", 60004, "qubodup", "Fast Sword Swing Sound"),
    "light_swoosh": openverse("d343ce9e-01df-4852-b625-3ac3eb805cdd", 475132, "bolkmar", "FX - Swoosh - Light"),
    "deep_whoosh": openverse("eab8a6e2-0ac8-4615-9e0d-ebde30523783", 351256, "Kinoton", "Deep Whoosh #1"),
}


def L(src, gain=0.0, pitch=1.0, start=None, length=None, lo=0, hi=0, delay=0.0):
    return {"src": src, "gain": gain, "pitch": pitch, "start": start, "length": length, "lo": lo, "hi": hi, "delay": delay}


def S(cue, gain):
    return L("synth:" + cue, gain)


# cue -> one layer list per variant (the variant count must match regulus_reborn.CUES)
RECIPES = {
    "step": [[L("step%d" % s, 0, p, length=0.3, hi=9000), S("step", -14)]
             for s, p in ((0, 1.0), (1, 0.96), (3, 1.04), (6, 0.98))],
    "touch_swish": [[L("fast_swing", 0, p), L("light_swoosh", -8, p * 0.9, lo=300), S("touch_swish", -12)]
                    for p in (1.0, 1.1, 0.92)],
    "touch_hit": [[L("punch_h%d" % i, 0, p), S("touch_hit", -7)] for i, p in ((0, 1.0), (1, 1.05), (2, 0.95))],
    "recoil": [[L("soft_h%d" % i, 0, p, length=0.45), S("recoil", -4)] for i, p in ((0, 1.0), (1, 1.06), (2, 0.94))],
    "reply_strike": [[L("punch_h4", 0, 0.92), L("lowboom1", -6, 1.2, length=0.6), S("reply_strike", -8)]],
    "rupture": [[L("punch_m2", 0, 0.8), L("chop", -4, 0.85, delay=0.02), L("crunch0", -12, 1.0, length=0.5, delay=0.03),
                 S("rupture", -8)]],
    "heartbeat": [[L("heart", 0, 1.0, start=0.30, length=0.8, hi=1500), S("heartbeat", -10)],
                  [L("heart", 0, 0.97, start=2.30, length=0.8, hi=1500), S("heartbeat", -10)]],
    "seizure": [[L("heart", 0, 0.85, start=4.25, length=2.2, hi=1200), S("seizure", -3)]],
    "sand_scoop": [[L("gravel", 0, 1.1, start=6.0, length=0.6, lo=200), L("cloth2", -6, 1.0), S("sand_scoop", -10)]],
    "sand_release": [[L("deep_whoosh", 0, 1.3, start=0.15, length=1.0), L("gravel", -6, 1.15, start=8.0, length=0.8, lo=300),
                      S("sand_release", -6)]],
    "grain_hit": [[L("generic_l%d" % i, 0, p), L("glass_l%d" % i, -7, 1.2), S("grain_hit", -6)]
                  for i, p in ((0, 1.0), (1, 1.08), (2, 0.94))],
    "sever_release": [[L("deep_whoosh", 0, 1.0, start=0.1, length=1.6), L("fast_swing", -4, 0.7), S("sever_release", -4)]],
    "sever_cut": [[L("knife1", 0, 1.0), S("sever_cut", -6)], [L("knife2", 0, 1.0), S("sever_cut", -6)]],
    "trample_lift": [[L("cloth1", 0, 0.9), L("light_swoosh", -4, 0.8, lo=200)]],
    "trample_stomp": [[L("thud", 0, 0.8), L("mining0", -3, 0.9), L("lowboom0", -2, 1.0, length=1.4),
                       L("gravel", -8, 1.0, start=0.7, length=1.2, lo=150, delay=0.08), S("trample_stomp", -8)]],
    "trample_wave": [[L("soft_h%d" % i, 0, p, length=0.45), L("deep_whoosh", -6, 1.5, start=0.2, length=0.5), S("trample_wave", -6)]
                     for i, p in ((3, 1.0), (4, 0.94))],
    "ult_wave": [[L("mining%d" % i, 0, 1.0), L("crunch%d" % c, -4, 1.0, length=0.9), S("ult_wave", -4)]
                 for i, c in ((1, 1), (2, 2))],
    "ult_flick": [[L("firework", 0, 1.0, start=0.55, length=1.0), L("lowboom1", -3, 0.9), S("ult_flick", -6)]],
    "ult_scoop": [[L("quake", -6, 1.0, start=10.0, length=1.6, hi=2000), L("deep_whoosh", 0, 0.6, start=0.0, length=1.4), S("ult_scoop", -2)]],
}
SAMPLED = frozenset(RECIPES)


# ---------------------------------------------------------------- fetching
def _opener():
    jar = http.cookiejar.CookieJar()
    op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    op.addheaders = [("User-Agent", UA)]
    return op


def fetch_kenney(pack):
    dest = HERE / "kenney" / KENNEY[pack]
    if (dest / "License.txt").exists():
        return
    op = _opener()
    page = "https://kenney.nl/assets/" + KENNEY[pack]
    html = op.open(page, timeout=60).read().decode("utf-8", "replace")
    key = html.split("data-download-id='", 1)[1].split("'", 1)[0]
    req = urllib.request.Request("https://kenney.nl/download-link/" + key, headers={"Referer": page})
    url = json.loads(op.open(req, timeout=60).read())["url"]
    with tempfile.TemporaryDirectory() as tmp:
        z = Path(tmp) / "pack.zip"
        z.write_bytes(op.open(urllib.request.Request(url, headers={"Referer": page}), timeout=300).read())
        with zipfile.ZipFile(z) as f:
            f.extractall(dest)
    print("kenney", KENNEY[pack], "->", dest)


def fetch_commons(title):
    dest = HERE / "commons" / title[5:].replace(" ", "_")
    if dest.exists():
        return
    api = ("https://commons.wikimedia.org/w/api.php?action=query&prop=imageinfo&iiprop=url|sha1&format=json&titles="
           + urllib.parse.quote(title))
    op = _opener()
    info = next(iter(json.loads(op.open(api, timeout=60).read())["query"]["pages"].values()))["imageinfo"][0]
    data = op.open(info["url"], timeout=300).read()
    assert hashlib.sha1(data).hexdigest() == info["sha1"], title
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    print("commons", title, "->", dest)


def fetch():
    for s in SOURCES.values():
        if s["origin"] == "kenney":
            fetch_kenney(s["pack"])
        elif s["origin"] == "commons":
            fetch_commons(s["title"])
        elif not (OV_RAW / (s["uuid"] + ".mp3")).exists():
            subprocess.run([sys.executable, str(HELPER), "download", s["uuid"]], check=True)


def path_of(s):
    if s["origin"] == "kenney":
        return HERE / "kenney" / KENNEY[s["pack"]] / s["file"]
    if s["origin"] == "commons":
        return HERE / "commons" / s["title"][5:].replace(" ", "_")
    return OV_RAW / (s["uuid"] + ".mp3")


def missing():
    return sorted(k for k, s in SOURCES.items() if not path_of(s).exists())


# ---------------------------------------------------------------- mixing
_decoded = {}


def decode(key):
    if key not in _decoded:
        raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path_of(SOURCES[key])), "-f", "f32le", "-ac", "1",
                              "-ar", str(SR), "-"], check=True, capture_output=True).stdout
        _decoded[key] = np.frombuffer(raw, dtype="<f4").astype(np.float64)
    return _decoded[key]


def first_transient(x):
    hop = 64
    peak = np.max(np.abs(x)) + 1e-12
    over = np.nonzero(np.abs(x) > peak * 10 ** (-30 / 20))[0]
    return max(0, (over[0] if len(over) else 0) - int(0.005 * SR)) // hop * hop


def render_layer(layer, variant):
    if layer["src"].startswith("synth:"):
        x = rr.CUES[layer["src"][6:]][0](variant)
    else:
        src = decode(layer["src"])
        i0 = first_transient(src) if layer["start"] is None else int(layer["start"] * SR)
        n = len(src) - i0 if layer["length"] is None else int(layer["length"] * SR * layer["pitch"])
        x = src[i0:i0 + n]
        if layer["pitch"] != 1.0:  # varispeed: read the slice faster (higher) or slower (lower)
            pos = np.arange(0, len(x) - 1, layer["pitch"])
            x = np.interp(pos, np.arange(len(x)), x)
        if layer["lo"] or layer["hi"]:
            x = rr.band(x, layer["lo"], layer["hi"])
        x = x / (np.max(np.abs(x)) + 1e-12)
        k = min(len(x), int(0.004 * SR))
        x[:k] *= np.linspace(0.0, 1.0, k) if layer["start"] is not None else 1.0
        o = min(len(x), int(0.03 * SR))
        x[len(x) - o:] *= np.linspace(1.0, 0.0, o)
    return x * 10 ** (layer["gain"] / 20)


def loudest_100ms(x):
    """RMS of the loudest 100 ms window, measured above 120 Hz (a crude stand-in for K-weighting, so
    sub-bass-heavy recordings are not matched quieter than they sound)."""
    x = rr.band(x, 120, 0)
    w = int(0.1 * SR)
    if len(x) <= w:
        return np.sqrt(np.mean(x ** 2) + 1e-12)
    c = np.concatenate([[0.0], np.cumsum(x ** 2)])
    return np.sqrt(np.max((c[w:] - c[:-w]) / w) + 1e-12)


def mix(cue, variant):
    layers = RECIPES[cue][variant]
    parts = [(render_layer(l, variant), l["delay"]) for l in layers]
    n = max(len(p) + int(d * SR) for p, d in parts)
    out = np.zeros(n)
    for p, d in parts:
        rr.place(out, p, d)
    reference = rr.CUES[cue][0](variant)
    out *= loudest_100ms(reference) / loudest_100ms(out)
    peak = np.max(np.abs(out))
    if peak > 10 ** (-1 / 20):
        out *= 10 ** (-1 / 20) / peak
    o = int(0.02 * SR)
    out[len(out) - o:] *= np.linspace(1.0, 0.0, o)
    return out


def files():
    for cue in RECIPES:
        variants = rr.CUES[cue][1]
        assert len(RECIPES[cue]) == variants, cue
        for v in range(variants):
            yield cue, v, (cue if variants == 1 else cue + str(v))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--wav")
    ap.add_argument("--only")
    a = ap.parse_args()
    if a.fetch:
        fetch()
        return
    gone = missing()
    if gone:
        print("sources not cached (%s); run --fetch first" % ", ".join(gone))
        sys.exit(0 if a.check else 1)
    bad = 0
    count = 0
    with tempfile.TemporaryDirectory() as tmp:
        for cue, v, file in files():
            if a.only and a.only != cue:
                continue
            x = mix(cue, v)
            assert np.isfinite(x).all() and np.max(np.abs(x)) > 0.05, file
            w, o = Path(tmp) / (file + ".wav"), Path(tmp) / (file + ".ogg")
            rr.write_wav(w, x)
            if a.wav:
                os.makedirs(a.wav, exist_ok=True)
                rr.write_wav(Path(a.wav) / (file + ".wav"), x)
            rr.encode(w, o)
            dst = OUT / (file + ".ogg.b64")
            count += 1
            if a.check:
                committed = Path(tmp) / (file + ".committed.ogg")
                committed.write_bytes(base64.b64decode(dst.read_text()))
                if hashlib.sha256(rr.decoded_pcm(committed)).digest() != hashlib.sha256(rr.decoded_pcm(o)).digest():
                    print("differs", file)
                    bad += 1
            else:
                dst.write_bytes(base64.encodebytes(o.read_bytes()))
    print("%d sampled files %s" % (count, "checked" if a.check else "written"))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
