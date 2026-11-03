"""Shared helpers of the #136 sound builders (city_life.py, scp096.py): CC0 Freesound previews (licence checked on
the sound page before download), decoding, picking steady windows and loud events by measurement (the build
sandbox has no audio device), seamless loops, levelling and packing into base64 binassets (split under the
push size limit)."""
import base64
import os
import re
import subprocess
import sys
import tempfile
import urllib.request
import wave

import numpy as np

RAW = os.path.expanduser("~/.cache/viltrumite-sfx/work/raw")
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SOUNDS = os.path.join(REPO, "viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds")
CC0 = "creativecommons.org/publicdomain/zero/1.0/"
PART = 48_000
UA = {"User-Agent": "Mozilla/5.0 (viltrumite-mods sound builder)"}


def raw_path(fid):
    return os.path.join(RAW, f"fs_{fid}.mp3")


def _fetch_one(key, fid, user, title):
    p = raw_path(fid)
    page = urllib.request.urlopen(urllib.request.Request(f"https://freesound.org/s/{fid}/", headers=UA), timeout=60).read().decode("utf-8", "replace")
    lic = set(re.findall(r"creativecommons.org/[a-z/0-9.]+", page))
    if lic != {CC0}:
        raise SystemExit(f"{key} ({fid}) is not CC0 only: {sorted(lic)}")
    m = re.search(r'og:audio" content="(?:https://freesound.org)?(https://cdn[^"]+)"', page)
    if not m:
        raise SystemExit(f"{key} ({fid}): no preview on the page")
    url = m.group(1).replace("-lq.mp3", "-hq.mp3")
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        size = int(r.headers.get("Content-Length") or 0)
        if os.path.isfile(p) and size and os.path.getsize(p) == size:
            return
        tmp = p + ".tmp"
        with open(tmp, "wb") as f:
            while True:
                b = r.read(1 << 16)
                if not b:
                    break
                f.write(b)
    if size and os.path.getsize(tmp) != size:
        raise SystemExit(f"{key} ({fid}): truncated download")
    os.replace(tmp, p)
    print("fetch", fid, user, title, flush=True)


def fetch(sources, workers=6):
    """sources: key -> (freesound id, creator, title). Downloads the HQ preview after checking the CC0 licence on
    the sound page; complete files (same size as the server's) are kept, partial ones are fetched again."""
    from concurrent.futures import ThreadPoolExecutor
    os.makedirs(RAW, exist_ok=True)
    todo = {}
    for key, (fid, user, title) in sources.items():
        todo.setdefault(fid, (key, fid, user, title))
    with ThreadPoolExecutor(workers) as ex:
        for f in [ex.submit(_fetch_one, *v) for v in todo.values()]:
            f.result()


_cache = {}


def load(fid, sr, stereo=False):
    k = (fid, sr, stereo)
    if k not in _cache:
        p = raw_path(fid)
        if not os.path.isfile(p):
            sys.exit(f"missing {p}: run the builder with --fetch")
        out = subprocess.run(["ffmpeg", "-v", "error", "-i", p, "-ac", "2" if stereo else "1", "-ar", str(sr), "-f", "s16le", "-"],
                             capture_output=True, check=True).stdout
        x = np.frombuffer(out, np.int16).astype(np.float64) / 32768.0
        _cache[k] = x.reshape(-1, 2) if stereo else x
    return _cache[k]


def rms_track(x, sr, win=0.1, hop=0.05):
    m = x.mean(axis=1) if x.ndim == 2 else x
    w, h = int(win * sr), int(hop * sr)
    if len(m) < w:
        return np.array([np.sqrt(np.mean(m * m) + 1e-12)])
    c = np.cumsum(np.concatenate([[0.0], m * m]))
    idx = np.arange(0, len(m) - w, h)
    return np.sqrt((c[idx + w] - c[idx]) / w + 1e-12)


def steadiest(x, sr, seconds, skip=0.5, avoid=()):
    """Start (s) of the window of `seconds` with the most even loudness (no close voice, horn or bang standing out)."""
    r = 20 * np.log10(rms_track(x, sr, 0.25, 0.25) + 1e-9)
    n = int(seconds / 0.25)
    best, start = None, skip
    for i in range(int(skip / 0.25), max(int(skip / 0.25) + 1, len(r) - n)):
        if any(a <= i * 0.25 <= b or a <= i * 0.25 + seconds <= b for a, b in avoid):
            continue
        seg = r[i:i + n]
        if len(seg) < n:
            break
        score = np.std(seg) + 0.6 * max(0.0, np.max(seg) - np.median(seg) - 6.0)
        if best is None or score < best:
            best, start = score, i * 0.25
    return start


def events(x, sr, min_gap=0.35, rel_db=-14.0, pre=0.04, max_len=3.0, tail_db=-30.0):
    """Loud events (start, end) seconds, loudest first: above rel_db of the loudest, cut until the tail decays."""
    r = rms_track(x, sr, 0.02, 0.01)
    db = 20 * np.log10(r + 1e-9)
    top = db.max()
    out = []
    used = np.zeros(len(db), bool)
    for i in np.argsort(-db):
        if db[i] < top + rel_db:
            break
        if used[max(0, i - int(min_gap / 0.01)):i + int(min_gap / 0.01)].any():
            continue
        a = i
        while a > 0 and db[a - 1] > db[i] - 24 and i - a < 80:
            a -= 1
        b = i
        while b < len(db) - 1 and db[b + 1] > db[i] + tail_db and (b - a) * 0.01 < max_len:
            b += 1
        used[a:b + 1] = True
        out.append((max(0.0, a * 0.01 - pre), b * 0.01 + 0.05))
    return out


def cut(x, sr, a, b, fade_in=0.005, fade_out=0.04):
    y = x[int(a * sr):int(b * sr)].copy()
    fi, fo = min(len(y) // 4, int(fade_in * sr)), min(len(y) // 3, int(fade_out * sr))
    ramp_i = np.linspace(0, 1, fi)
    ramp_o = np.linspace(1, 0, fo)
    if y.ndim == 2:
        ramp_i, ramp_o = ramp_i[:, None], ramp_o[:, None]
    if fi:
        y[:fi] *= ramp_i
    if fo:
        y[-fo:] *= ramp_o
    return y


def loop(x, sr, seconds, xfade=1.5):
    """Seamless loop of `seconds`: the material after the end is crossfaded (equal power) onto the head."""
    n = int(seconds * sr)
    k = int(xfade * sr)
    if len(x) < n + k:
        x = np.concatenate([x, x[: n + k - len(x)]])
    y = x[:n].copy()
    a = np.sin(0.5 * np.pi * np.linspace(0, 1, k))
    b = np.cos(0.5 * np.pi * np.linspace(0, 1, k))
    if y.ndim == 2:
        a, b = a[:, None], b[:, None]
    y[:k] = x[:k] * a + x[n:n + k] * b
    return y


def fft_filter(x, sr, lo=None, hi=None, order=2.0):
    """Zero-phase band shaping in the frequency domain (smooth Butterworth-like magnitude)."""
    mono = x.ndim == 1
    X = np.fft.rfft(x if not mono else x[:, None], axis=0)
    f = np.fft.rfftfreq(x.shape[0], 1.0 / sr)
    g = np.ones_like(f)
    if lo:
        g *= 1.0 / np.sqrt(1.0 + (lo / np.maximum(f, 1e-3)) ** (2 * order))
    if hi:
        g *= 1.0 / np.sqrt(1.0 + (f / hi) ** (2 * order))
    y = np.fft.irfft(X * g[:, None], n=x.shape[0], axis=0)
    return y[:, 0] if mono else y


def resample(x, factor):
    """Varispeed by factor (>1 higher and shorter), linear interpolation."""
    n = int(len(x) / factor)
    pos = np.arange(n) * factor
    i = np.minimum(pos.astype(int), len(x) - 2)
    f = pos - i
    if x.ndim == 2:
        f = f[:, None]
    return x[i] * (1 - f) + x[i + 1] * f


def level(x, sr, rms_db, peak_db=-1.0):
    loud = rms_track(x, sr, 0.1, 0.05).max()
    y = x * (10 ** (rms_db / 20.0) / (loud + 1e-12))
    lim = 10 ** (peak_db / 20.0)
    return np.tanh(y / lim) * lim


def place(dst, src, at_s, gain, sr):
    i = int(at_s * sr)
    if i >= len(dst):
        return
    n = min(len(src), len(dst) - i)
    dst[i:i + n] += src[:n] * gain


def write_wav(path, x, sr):
    x = np.clip(x, -1, 1)
    ch = 2 if x.ndim == 2 else 1
    with wave.open(path, "wb") as w:
        w.setnchannels(ch)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes((x * 32767).astype(np.int16).tobytes())


def encode(x, sr, quality, rate=None, bitrate=None):
    """Ogg Vorbis bytes; quality (VBR -q) or, for speech, an average bitrate such as "18k"."""
    with tempfile.TemporaryDirectory() as tmp:
        wav, ogg = os.path.join(tmp, "x.wav"), os.path.join(tmp, "x.ogg")
        write_wav(wav, x, sr)
        cmd = ["ffmpeg", "-v", "error", "-y", "-i", wav]
        if rate:
            cmd += ["-ar", str(rate)]
        cmd += ["-c:a", "libvorbis"] + (["-b:a", bitrate] if bitrate else ["-q:a", str(quality)]) + ["-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", ogg]
        subprocess.run(cmd, check=True)
        return open(ogg, "rb").read()


def write_b64(rel, data):
    """Write sounds/<rel>.ogg as base64 (split into .partN past PART characters). Returns the number of files."""
    base = os.path.join(SOUNDS, rel + ".ogg.b64")
    os.makedirs(os.path.dirname(base), exist_ok=True)
    d = os.path.dirname(base)
    for f in os.listdir(d):
        if f == os.path.basename(base) or f.startswith(os.path.basename(base) + ".part"):
            os.remove(os.path.join(d, f))
    text = base64.encodebytes(data).decode("ascii")
    if len(text) <= PART:
        open(base, "w").write(text)
        return 1
    parts, cur = [], ""
    for line in text.splitlines(keepends=True):
        if len(cur) + len(line) > PART:
            parts.append(cur)
            cur = ""
        cur += line
    parts.append(cur)
    for k, p in enumerate(parts):
        open(f"{base}.part{k}", "w").write(p)
    return len(parts)


def describe(name, x, sr, size=None):
    m = x.mean(axis=1) if x.ndim == 2 else x
    loud = 20 * np.log10(rms_track(m, sr).max() + 1e-12)
    peak = 20 * np.log10(np.abs(m).max() + 1e-12)
    edge = abs(m[0] - m[-1])
    kb = f" {size / 1024:6.1f} KB" if size else ""
    print(f"{name:34s} {len(m) / sr:6.2f}s rms100 {loud:6.1f} peak {peak:6.1f} edge {edge:.3f}{kb}")
