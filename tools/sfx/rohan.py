#!/usr/bin/env python3
"""Rohan Kishibe / Heaven's Door sound kit: 16 events (17 files) mixed from CC0 1.0 Freesound recordings.

Openverse (api.openverse.org) answered 504/403 while this kit was made, so the recordings were found with the
Freesound search (licence filter "Creative Commons 0"); the CC0 licence of every row was checked on its landing page
(2026-11-02) and the attribution below is composed from that page. The raw hq-MP3 previews are cached in
~/.cache/viltrumite-sfx/rohan (never committed). Changes: trimmed, pitched (resampled), reversed, filtered, layered,
faded and peak-normalised; mono OGG Vorbis q5, 44.1 kHz, packed as .ogg.b64 for core's decodeBinaryAssets.

    python3 tools/sfx/rohan.py --fetch    # download the missing previews into the cache
    python3 tools/sfx/rohan.py            # mix + write sounds/rohan/*.ogg.b64, the sound overlay and SOURCES.md
    python3 tools/sfx/rohan.py --check    # re-mix and compare the decoded audio with the committed files
"""
import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "viltrumitecore/src/main"
OUT = CORE / "binassets/assets/viltrumitecore/sounds/rohan"
OVERLAY = CORE / "sound-overlays/rohan.json"
NOTES = CORE / "resources/assets/viltrumitecore/sounds/rohan/SOURCES.md"  # ships in the JAR
CACHE = Path(os.environ.get("OPENVERSE_SFX_HOME", str(Path.home() / ".cache" / "viltrumite-sfx"))).expanduser() / "rohan"
SR = 44100

# key -> (Freesound id, creator, title on the landing page, preview path under cdn.freesound.org/previews/)
SOURCES = {
    "page2": (119127, "esperri", "Book Turn Page 2.wav", "119/119127_1990695-hq.mp3"),
    "page3": (119128, "esperri", "Book Turn Page 3.wav", "119/119128_1990695-hq.mp3"),
    "turn": (457767, "partheeban", "turnPage.mp3", "457/457767_9570326-hq.mp3"),
    "flutter": (468113, "Aquafeniz", "Flutter.wav", "468/468113_9930348-hq.mp3"),
    "close": (156927, "tia666", "bkClose.wav", "156/156927_2830492-hq.mp3"),
    "tear": (489968, "falcospizaetus", "TearingPaper11", "489/489968_10570192-hq.mp3"),
    "pen": (337087, "Anthousai", "writing - pen - wide 01.wav", "337/337087_5923045-hq.mp3"),
    "scribble": (257518, "waldram", "Scribble_short.wav", "257/257518_114036-hq.mp3"),
    "woosh": (683101, "florianreichelt", "quick woosh", "683/683101_6253486-hq.mp3"),
    "whoosh": (449996, "DJT4NN3R", "whoosh_short_mid.wav", "449/449996_3217484-hq.mp3"),
    "knife": (259563, "nicktermer", "knife throw.wav", "259/259563_2316086-hq.mp3"),
    "stab": (344404, "jawbutch", "Knife Stab Melon.wav", "344/344404_4986214-hq.mp3"),
    "shimmer": (632344, "adh.dreaming", "Shimmer", "632/632344_12672694-hq.mp3"),
    "bookhit": (424479, "Shroke_Makee", "Book Hit", "424/424479_8491197-hq.mp3"),
    "folder": (178056, "Eneasz", "folder snapped shut.wav", "178/178056_2103700-hq.mp3"),
    "sparkle": (844398, "SkySpeira", "Mysterious Sparkle Flourish", "844/844398_7155498-hq.mp3"),
    "rumble": (431390, "epicdude959", "Short Increasing Rumble", "431/431390_3570427-hq.mp3"),
    "shake": (119847, "kbnevel", "Paper_Shake.aif", "119/119847_1990690-hq.mp3"),
}


def L(src, start=0.0, dur=None, gain=0.0, pitch=1.0, rev=False, hp=None, lp=None, at=0.0, fin=0.004, fout=0.06, onset=True):
    """One layer: src from `start` s (after its detected onset) for `dur` s, placed at `at` s in the mix."""
    return dict(src=src, start=start, dur=dur, gain=gain, pitch=pitch, rev=rev, hp=hp, lp=lp, at=at, fin=fin, fout=fout, onset=onset)


# event (= file name under sounds/rohan/) -> (length s, peak dBFS, layers) ; a list of those makes variants
RECIPES = {
    "door_summon": (1.1, -1.5, [L("flutter", 0.0, 1.0, -2), L("shimmer", 0.15, 1.0, -9, hp=900, fin=0.08, fout=0.4, at=0.05),
                                 L("whoosh", 0.0, 0.6, -6, pitch=1.15, at=0.0)]),
    "door_touch": (0.7, -1.5, [L("turn", 0.0, 0.6, -1), L("bookhit", 0.0, 0.4, -9, pitch=1.3, lp=6000),
                                L("shimmer", 0.3, 0.6, -15, hp=1500, fin=0.03, fout=0.3, at=0.05)]),
    "dismiss": (0.9, -3.0, [L("flutter", 0.0, 0.8, -3, rev=True, fin=0.2, fout=0.05), L("shimmer", 0.2, 0.8, -12, rev=True, hp=1200, fin=0.3)]),
    "book_open": (0.9, -1.5, [L("page2", 0.0, 0.8, 0), L("flutter", 0.25, 0.6, -10, at=0.12, fin=0.05, fout=0.3)]),
    "page_flip": [(0.75, -2.0, [L("page2", 0.0, 0.7, 0)]), (0.75, -2.0, [L("page3", 0.0, 0.7, 0)])],
    "write": (0.9, -3.0, [L("pen", 2.0, 0.9, 0, fin=0.03, fout=0.15, onset=False)]),
    "book_close": (0.6, -1.0, [L("close", 0.0, 0.55, 0), L("bookhit", 0.0, 0.3, -10, pitch=1.1, lp=5000)]),
    "page_rip": (1.0, -1.0, [L("tear", 0.0, 0.95, 0)]),
    "sketch": (1.1, -2.5, [L("scribble", 0.0, 1.05, 0, hp=300), L("pen", 4.0, 1.0, -9, onset=False, fin=0.05, fout=0.2)]),
    "nib_throw": (0.4, -3.0, [L("woosh", 0.0, 0.4, 0, pitch=1.4, hp=600, fout=0.12), L("whoosh", 0.1, 0.3, -10, pitch=1.8, hp=900, fout=0.1)]),
    "nib_hit": (0.4, -1.5, [L("knife", 0.0, 0.26, 0), L("stab", 0.0, 0.3, -10, pitch=1.4)]),
    "counter": (0.7, -1.0, [L("whoosh", 0.0, 0.35, -7, pitch=1.35, fout=0.15), L("folder", 0.0, 0.6, 0, at=0.1)]),
    "whoosh": (0.8, -2.0, [L("whoosh", 0.0, 0.8, 0, fout=0.3), L("woosh", 0.0, 0.4, -5, at=0.12, fout=0.15)]),
    "manuscript": (2.0, -2.0, [L("sparkle", 0.0, 2.0, 0, fout=0.5), L("shake", 1.0, 1.2, -11, onset=False, fin=0.2, fout=0.5, at=0.2)]),
    "resist": (1.3, -5.0, [L("rumble", 0.0, 1.3, 0, pitch=0.8, lp=2500, fin=0.05, fout=0.4), L("shake", 3.0, 1.0, -7, onset=False, fin=0.1, fout=0.3)]),
    "refuse": (0.6, -1.5, [L("bookhit", 0.0, 0.45, 0, pitch=0.85, lp=4000), L("folder", 0.0, 0.5, -8, pitch=0.7, at=0.02)]),
}


def fetch():
    CACHE.mkdir(parents=True, exist_ok=True)
    for fid, user, title, prev in SOURCES.values():
        p = CACHE / ("%d.mp3" % fid)
        if p.exists():
            continue
        req = urllib.request.Request("https://cdn.freesound.org/previews/" + prev, headers={"User-Agent": "viltrumite-mods-sfx/1.0"})
        with urllib.request.urlopen(req, timeout=90) as r:
            p.write_bytes(r.read())
        print("fetched", fid, title)


def load(key):
    fid = SOURCES[key][0]
    p = CACHE / ("%d.mp3" % fid)
    if not p.exists():
        raise SystemExit("CC0 source %s not cached: run with --fetch first" % p.name)
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(p), "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"], check=True,
                         capture_output=True).stdout
    return np.frombuffer(raw, np.float32).astype(np.float64)


def _onset(x, frac=0.08):
    i = int(np.argmax(np.abs(x) > np.abs(x).max() * frac))
    return max(0, i - int(0.004 * SR))


def _resample(x, k):
    if abs(k - 1.0) < 1e-9:
        return x
    n = int(len(x) / k)
    return np.interp(np.arange(n) * k, np.arange(len(x)), x)


def _filter(x, hp=None, lp=None):
    if not hp and not lp:
        return x
    n = len(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    X = np.fft.rfft(x)
    g = np.ones_like(f)
    if hp:
        g *= 1 / np.sqrt(1 + (hp / np.maximum(f, 1e-3)) ** 4)
    if lp:
        g *= 1 / np.sqrt(1 + (f / lp) ** 4)
    return np.fft.irfft(X * g, n)


def _fade(x, fin, fout):
    n = len(x)
    a, b = min(n, int(fin * SR)), min(n, int(fout * SR))
    if a:
        x[:a] *= np.linspace(0, 1, a) ** 2
    if b:
        x[n - b:] *= np.linspace(1, 0, b) ** 2
    return x


def render(length, peak_db, layers, cache):
    out = np.zeros(int(length * SR))
    for ly in layers:
        src = cache.setdefault(ly["src"], load(ly["src"]))
        s0 = (_onset(src) if ly["onset"] else 0) + int(ly["start"] * SR)
        x = src[s0:]
        x = _resample(x, ly["pitch"])
        if ly["dur"]:
            x = x[:int(ly["dur"] * SR)]
        x = np.array(x, np.float64)
        if ly["rev"]:
            x = x[::-1].copy()
        x = _filter(x, ly["hp"], ly["lp"])
        x = _fade(x, ly["fin"], ly["fout"]) * 10 ** (ly["gain"] / 20)
        a = int(ly["at"] * SR)
        n = min(len(x), len(out) - a)
        out[a:a + n] += x[:n]
    out = _fade(out, 0.002, 0.03)
    pk = np.abs(out).max()
    if pk > 0:
        out *= 10 ** (peak_db / 20) / pk
    return out


def encode(x):
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "m.f32"
        wav.write_bytes(x.astype(np.float32).tobytes())
        ogg = Path(tmp) / "m.ogg"
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", str(wav), "-map_metadata", "-1",
                        "-c:a", "libvorbis", "-q:a", "5", "-fflags", "+bitexact", "-flags:a", "+bitexact", "-f", "ogg", str(ogg)], check=True)
        return ogg.read_bytes()


def files():
    """{relative name: (pcm, ogg bytes)} for every file of every event."""
    cache, out = {}, {}
    for ev, rec in RECIPES.items():
        variants = rec if isinstance(rec, list) else [rec]
        for i, (length, pk, layers) in enumerate(variants):
            name = ev if len(variants) == 1 else "%s%d" % (ev, i + 1)
            pcm = render(length, pk, layers, cache)
            out[name] = (pcm, encode(pcm))
    return out


def overlay():
    d = {}
    for ev, rec in RECIPES.items():
        variants = rec if isinstance(rec, list) else [rec]
        names = [ev] if len(variants) == 1 else ["%s%d" % (ev, i + 1) for i in range(len(variants))]
        d["rohan." + ev] = {"category": "player", "subtitle": "subtitles.viltrumitecore.rohan." + ev,
                            "sounds": [{"name": "viltrumitecore:rohan/" + n} for n in names]}
    return d


def sources_md():
    lines = ["# Heaven's Door sounds — sources", "",
             "Every file in this folder is mixed by `tools/sfx/rohan.py` from [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)",
             "Freesound recordings (attribution not required; credited with thanks). No anime/game audio, no voice.", "",
             "| Key | Freesound | Creator | Title | Used in |", "|---|---|---|---|---|"]
    use = {}
    for ev, rec in RECIPES.items():
        for _, _, layers in (rec if isinstance(rec, list) else [rec]):
            for ly in layers:
                use.setdefault(ly["src"], set()).add(ev)
    for k, (fid, user, title, _) in SOURCES.items():
        lines.append("| %s | [%d](https://freesound.org/people/%s/sounds/%d/) | %s | %s | %s |" % (
            k, fid, user, fid, user, title, ", ".join(sorted(use.get(k, ())))))
    lines += ["", "Changes: trimmed at the detected onset, resampled for pitch, reversed (dismiss), FFT high/low-pass,",
              "layered, faded and peak-normalised; mono OGG Vorbis q5 44.1 kHz. Licence of each row checked on its Freesound",
              "landing page on 2026-11-02; Openverse was unreachable (HTTP 504/403), so the attribution is composed from that page.", ""]
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--wav", help="also write reviewable WAVs into this folder")
    a = ap.parse_args()
    if a.fetch:
        fetch()
        return
    out = files()
    texts = {OVERLAY: json.dumps(overlay(), indent=2) + "\n", NOTES: sources_md()}
    bad = 0
    for name, (pcm, ogg) in sorted(out.items()):
        p = OUT / (name + ".ogg.b64")
        data = base64.encodebytes(ogg)
        if a.check:
            if not p.exists() or p.read_bytes() != data:
                print("DIFF", p.relative_to(ROOT))
                bad += 1
            continue
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
        if a.wav:
            Path(a.wav).mkdir(parents=True, exist_ok=True)
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-", str(Path(a.wav) / (name + ".wav"))],
                           input=pcm.astype(np.float32).tobytes(), check=True)
    for p, t in texts.items():
        if a.check:
            if not p.exists() or p.read_text(encoding="utf-8") != t:
                print("DIFF", p.relative_to(ROOT))
                bad += 1
        else:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(t, encoding="utf-8")
    print("%d sound files %s" % (len(out), "checked" if a.check else "written"))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
