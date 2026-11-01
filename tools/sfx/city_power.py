#!/usr/bin/env python3
"""City infrastructure sounds (Living City 3.0): sirens, transformer blows, arcs, hum, power down/up, cascades.

Every recording is CC0 1.0 from Freesound (landing pages checked by hand: licence link, author, description; no
film, game or anime rips). The hq preview MP3s are fetched into ~/.cache/viltrumite-sfx/work/raw/fs_<id>.mp3 and
never committed. Credits and the cue sheet: tools/assets/city_power_sources.md. Run from the repository root:

    python3 tools/sfx/city_power.py --fetch           # download the missing previews (Freesound CDN)
    python3 tools/sfx/city_power.py                    # mix every cue, write .ogg.b64 (parts) into binassets
    python3 tools/sfx/city_power.py --only siren_attack --wav /tmp/cp   # one cue to WAV for review
    python3 tools/sfx/city_power.py --sources          # file -> Freesound ids

Seeded: a rerun gives the same mix. Loops are cut from steady parts of the recordings and closed with an
equal-power crossfade over the loop seam, so they repeat without a click. Each cue is normalised on its loudest
100 ms RMS to a per-class target and soft-limited under -1 dBFS. Far siren variants are low-passed with a
synthetic late reverb (own processing). Mono Vorbis; long files are split into .b64.partN (< 45 KB each) because
a GitHub MCP push request is capped at 64 KB.

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

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAW = os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "raw")
OUT = os.path.join(ROOT, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore", "sounds", "city_power")
SR = 44100
PART = 45_000

# key -> (Freesound id, creator, original title, hq preview URL). All CC0 1.0 (checked on the landing page).
SOURCES = {
    "prague_siren": (382611, "nooly", "Air raid siren (Prague)", "https://cdn.freesound.org/previews/382/382611_5121236-hq.mp3"),
    "sentry_siren": (714426, "vyclops", "air raid / tornado siren Sentry 20v2T alert warning signal", None),
    "mega_siren": (58927, "HootOwl", "3 Toned Mega Siren of Doom.wav", None),
    "wind_down": (118754, "maurcs", "Tornado Siren winding down.aiff", None),
    "klaxon": (555465, "alphatone", "Papatone Pictures Klaxon Rapid.wav", None),
    "glorb_arcs": (867585, "bassimat", "Electric Arcs, Relays and Transformer Hum", None),
    "arc_buzz": (776446, "pryanic", "ELECArc_Electric arc hum and buzz", None),
    "sparks_35kv": (396470, "kev_durr", "Electric Sparks.wav", None),
    "kv_spark": (549878, "therover", "etincelle-spark.wav", None),
    "steady_sparks": (438500, "craigsmith", "G32-16-Steady Sparks.wav", None),
    "power_lines": (347046, "johnaudiotech", "765 kilo-volt transmission lines buzzing and crackling", None),
    "transformer_hum": (641208, "ideaph", "transformer at night.wav", None),
    "electro_explosion": (730489, "STRANGEFOLD6541100", "Electro_explosion 0", None),
    "massive_discharge": (438494, "craigsmith", "G32-10-Massive Electrical Discharges.wav", None),
    "deep_boom": (539968, "Za-Games", "Deep Boom", None),
    "distant_metal": (324277, "Kostrava", "distant explosion", None),
    "distant_series": (320788, "Kostrava", "distant explosions", None),
    "kill_switch": (131599, "EchoCinematics", "Kill Switch (Large Breaker Switch) .WAV", None),
    "breaker": (130153, "sribubba", "Circuit Breaker 2 DP.wav", None),
    "collapse": (712918, "greyfeather", "building collapse / demolition", None),
    "creak_slow": (172411, "EagleStealthTeam", "Creaking Metal - Slow", None),
    "metal_groan": (496836, "hinchinbrook", "Metallic Groan", None),
    "city_air": (427841, "leonelmail", "Ambience City Quiet Night Air Tone", None),
    "alarm_panel": (874698, "loganzsound", "Fire Alarm Panel Repeated Beeps", None),
}

# class -> loudest-100 ms RMS target dBFS
TARGET = {"siren": -11.0, "siren_far": -14.0, "all_clear": -12.0, "klaxon": -15.0, "beeps": -19.0, "blow": -8.0,
          "blow_far": -12.0, "arc": -12.0, "spark_loop": -19.0, "hum": -21.0, "power": -14.0, "surge": -13.0,
          "cascade": -8.5, "groan": -14.0, "impact": -9.0, "distant": -14.0, "bed": -22.0}

_cache = {}


def raw_path(key):
    return os.path.join(RAW, f"fs_{SOURCES[key][0]}.mp3")


def fetch():
    import re
    os.makedirs(RAW, exist_ok=True)
    ua = {"User-Agent": "Mozilla/5.0 viltrumite-sfx"}
    for key, (sid, user, _title, url) in SOURCES.items():
        path = raw_path(key)
        if os.path.isfile(path):
            continue
        page = urllib.request.urlopen(urllib.request.Request(f"https://freesound.org/people/{user}/sounds/{sid}/", headers=ua),
                                      timeout=60).read().decode("utf-8", "replace")
        lic = re.search(r'title="Go to the full license text" href="([^"]+)"', page)
        if not lic or "publicdomain/zero" not in lic.group(1):
            sys.exit(f"{key}: licence on the landing page is not CC0 ({lic.group(1) if lic else '?'})")
        url = url or re.search(r'data-static-file-url="([^"]+-hq\.mp3)"', page).group(1)
        open(path, "wb").write(urllib.request.urlopen(urllib.request.Request(url, headers=ua), timeout=120).read())
        print("fetched", key, sid)


def source(key):
    if key not in _cache:
        path = raw_path(key)
        if not os.path.isfile(path):
            sys.exit(f"Missing source {key}; run: python3 tools/sfx/city_power.py --fetch")
        out = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"],
                             capture_output=True, check=True).stdout
        x = np.frombuffer(out, np.int16).astype(np.float64) / 32768.0
        _cache[key] = x - np.mean(x)
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


def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def band(x, lo=None, hi=None, order=2.0):
    """Zero-phase FFT filter with smooth Butterworth-like skirts."""
    n = len(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    h = np.ones_like(f)
    if lo:
        h *= 1.0 / np.sqrt(1.0 + (lo / np.maximum(f, 1e-3)) ** (2 * order))
    if hi:
        h *= 1.0 / np.sqrt(1.0 + (f / hi) ** (2 * order))
    return np.fft.irfft(np.fft.rfft(x) * h, n)


def noise(seconds, rng, color=0.0):
    n = int(seconds * SR)
    spec = rng.normal(size=n // 2 + 1) + 1j * rng.normal(size=n // 2 + 1)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    spec *= 1.0 / np.maximum(f, 20.0) ** (color / 2.0)
    y = np.fft.irfft(spec, n)
    return y / (np.std(y) + 1e-12)


def env(seconds, attack, decay, hold=0.0, curve=4.0):
    t = t_axis(seconds)
    e = np.clip(t / max(attack, 1e-4), 0, 1)
    tail = np.clip((t - attack - hold) / max(decay, 1e-4), 0, None)
    return e * np.exp(-curve * tail)


def place(dst, src, at, gain=1.0):
    i = int(at * SR)
    if i >= len(dst):
        return dst
    m = min(len(src), len(dst) - i)
    dst[i:i + m] += src[:m] * gain
    return dst


def db(x):
    return 10.0 ** (x / 20.0)


def norm(x):
    """Scale to unit loudest-100 ms RMS (layers are mixed by relative gains in dB)."""
    w = int(0.1 * SR)
    rms = max(np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, max(1, len(x) - w), w // 2))
    return x / max(rms, 1e-9)


def fade(x, fin=0.003, fout=0.25):
    n = len(x)
    a = int(fin * SR)
    b = int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[n - b:] *= np.linspace(1, 0, b) ** 2
    return x


def loop(x, seconds, cross):
    """A seamless loop of `seconds` from `x` (needs seconds + cross of material): equal-power seam crossfade."""
    n = int(seconds * SR)
    c = int(cross * SR)
    y = x[:n].copy()
    t = np.linspace(0, np.pi / 2, c)
    y[:c] = x[:c] * np.sin(t) + x[n:n + c] * np.cos(t)
    return y


def reverb(x, seconds, rng, decay=2.2, wet=0.5, hi=3000.0):
    """Synthetic late reverb: decaying filtered noise impulse response (own processing)."""
    ir = noise(seconds, rng, 1.0) * np.exp(-t_axis(seconds) * decay * 3.0 / seconds)
    ir = band(ir, 120.0, hi)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-12
    n = len(x) + len(ir)
    size = 1 << (n - 1).bit_length()
    w = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:len(x)]
    return x * (1.0 - wet) + w * wet * 3.0


def circular_reverb(x, seconds, rng, decay=2.2, wet=0.5, hi=3000.0):
    """Reverb that wraps around a loop, so the tail of the loop end rings into its start."""
    ir = noise(seconds, rng, 1.0) * np.exp(-t_axis(seconds) * decay * 3.0 / seconds)
    ir = band(ir, 120.0, hi)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-12
    ir = np.pad(ir, (0, max(0, len(x) - len(ir))))[:len(x)]
    w = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(ir), len(x))
    return x * (1.0 - wet) + w * wet * 3.0


def finish(x, cls, is_loop=False):
    """Normalise on the loudest 100 ms RMS, then soft-limit under -1 dBFS (tanh knee above -4 dBFS)."""
    x = norm(x) * db(TARGET[cls])
    knee = db(-4.0)
    ceiling = db(-1.0)
    over = np.abs(x) > knee
    x[over] = np.sign(x[over]) * (knee + (ceiling - knee) * np.tanh((np.abs(x[over]) - knee) / (ceiling - knee)))
    if not is_loop:
        x = fade(x, 0.002, 0.08)
    return x


# ------------------------------------------------------------------ sirens
def siren_loop(key, start, seconds, rng, far=False, lo=90.0):
    x = seg(key, start, seconds + 1.5)
    x = band(x, lo, None)
    y = loop(x, seconds, 1.5)
    if far:
        # 600 m away: the highs go first, the city throws back a long tail
        y = band(y, 140.0, 1400.0, 1.5)
        y = circular_reverb(y, 3.5, rng, decay=1.6, wet=0.65, hi=1600.0)
    return finish(y, "siren_far" if far else "siren", True)


def siren_warning(rng):
    # Prague: the slow rise and fall of a rotating air-raid siren (steady from 2 s to 38 s)
    return siren_loop("prague_siren", 6.0, 14.0, rng)


def siren_warning_far(rng):
    return siren_loop("prague_siren", 6.0, 14.0, rng, far=True)


def siren_attack(rng):
    # Sentry 20v2T in its 3-minute alert: a rotating wail that sweeps past the listener every few seconds
    return siren_loop("sentry_siren", 40.0, 14.0, rng)


def siren_attack_far(rng):
    return siren_loop("sentry_siren", 40.0, 14.0, rng, far=True)


def siren_critical(rng):
    # the three-toned mega siren (steady from 2 s to 31 s), with the rapid klaxon under it
    base = siren_loop("mega_siren", 8.0, 12.0, rng)
    return base


def siren_critical_far(rng):
    return siren_loop("mega_siren", 8.0, 12.0, rng, far=True)


def siren_all_clear(rng):
    # a last steady blast that winds down: Sentry tone into the Atlanta wind-down
    steady = seg("sentry_siren", 120.0, 6.0)
    down = seg("wind_down", 0.0, 22.0)
    out = np.zeros(int(26 * SR))
    place(out, norm(band(steady, 90.0, None)) * np.linspace(1, 0, len(steady)) ** 0.6, 0.0, 1.0)
    d = norm(band(down, 90.0, None)) * db(-1.5)
    d[:int(2 * SR)] *= np.linspace(0, 1, int(2 * SR))
    place(out, d, 3.0)
    return fade(finish(out, "all_clear"), 0.3, 3.0)


def klaxon(rng):
    x = band(seg("klaxon", 0.5, 9.5), 150.0, 9000.0)
    return finish(loop(x, 8.0, 0.4), "klaxon", True)


def alarm_beeps(rng):
    x = band(seg("alarm_panel", 0.2, 8.8), 300.0, None)
    return finish(loop(x, 8.0, 0.25), "beeps", True)


# ------------------------------------------------------------------ electricity
def arc(rng, variant):
    picks = [("kv_spark", 6.62, 0.9, 1.0), ("sparks_35kv", 2.0, 0.8, 1.0), ("glorb_arcs", 31.0, 1.0, 1.0),
             ("massive_discharge", 3.1, 1.2, 1.15)]
    key, start, length, pitch = picks[variant]
    x = seg(key, start, length, pitch)
    x = band(x, 250.0, None)
    x *= env(length, 0.003, length * 0.8, 0.05, 3.0)
    return finish(x, "arc")


def transformer_blow(rng, variant):
    """Click of the breaker, the arc crack, the low punch of the blow, then hissing sparks fading out."""
    pitch = [1.0, 0.92, 1.07][variant]
    out = np.zeros(int(4.2 * SR))
    click = band(seg("breaker", 0.0, 0.5, pitch), 400.0, None)
    place(out, norm(click) * env(0.5, 0.001, 0.2), 0.0, db(-8))
    crack = band(seg("kv_spark", 6.64 + 0.02 * variant, 0.8, pitch), 180.0, None)
    place(out, norm(crack) * env(0.8, 0.001, 0.5, 0.02, 3.5), 0.06, db(-2))
    blow = seg("electro_explosion", 0.0, 2.4, pitch)
    place(out, norm(blow) * env(2.4, 0.002, 1.8, 0.05, 2.5), 0.08, db(-1))
    sub = band(seg("deep_boom", 0.0, 2.6, 0.85 * pitch), None, 180.0, 3.0)
    place(out, norm(sub) * env(2.6, 0.004, 1.6, 0.05, 3.0), 0.09, db(-3))
    t = t_axis(0.5)
    thump = np.sin(2 * np.pi * (52.0 * pitch) * t - 6.0 * t) * np.exp(-t * 9.0)
    place(out, norm(thump), 0.09, db(-6))
    hiss = band(seg("steady_sparks", 6.0 + 7.0 * variant, 3.6, pitch), 600.0, None)
    hiss *= np.linspace(1, 0, len(hiss)) ** 1.6
    place(out, norm(hiss) * np.clip(t_axis(3.6) / 0.3, 0, 1), 0.45, db(-11))
    return finish(out, "blow")


def transformer_blow_far(rng, variant):
    near = transformer_blow(rng, variant + 1)
    x = band(near, 60.0, 1100.0 if variant == 0 else 800.0, 1.5)
    x = reverb(x, 2.5, rng, decay=1.8, wet=0.45, hi=1400.0)
    return finish(x, "blow_far")


def spark_loop(rng):
    x = band(seg("steady_sparks", 20.0, 5.2), 300.0, None)
    return finish(loop(x, 4.0, 1.0), "spark_loop", True)


def line_hum(rng):
    hum = band(seg("transformer_hum", 10.0, 7.0), 40.0, 4000.0)
    crackle = band(seg("power_lines", 2.0, 7.0), 1500.0, None)
    x = norm(hum) + norm(crackle) * db(-12)
    return finish(loop(x, 6.0, 1.0), "hum", True)


def power_down(rng):
    """The breaker drops, the hum sinks and dies (varispeed slowdown of the transformer hum, own processing)."""
    out = np.zeros(int(2.4 * SR))
    clunk = band(seg("kill_switch", 0.0, 1.2), 80.0, None)
    place(out, norm(clunk) * env(1.2, 0.001, 0.6), 0.0, db(-2))
    hum = seg("transformer_hum", 4.0, 3.0)
    n = int(2.2 * SR)
    rate = np.linspace(1.0, 0.25, n) ** 1.4
    pos = np.cumsum(rate)
    pos = pos[pos < len(hum) - 1]
    i = pos.astype(int)
    sink = hum[i] * (1 - (pos - i)) + hum[i + 1] * (pos - i)
    sink *= np.linspace(1, 0, len(sink)) ** 1.8
    place(out, norm(band(sink, 40.0, 3000.0)), 0.02, db(-7))
    relay = band(seg("breaker", 0.0, 0.4, 0.8), 300.0, None)
    place(out, norm(relay) * env(0.4, 0.001, 0.15), 0.55, db(-12))
    return finish(out, "power")


def power_up(rng):
    """Relays clack in a row, the hum swells back to its pitch."""
    out = np.zeros(int(2.6 * SR))
    for k, at in enumerate((0.0, 0.18, 0.31)):
        relay = band(seg("breaker", 0.0, 0.45, 0.9 + 0.08 * k), 300.0, None)
        place(out, norm(relay) * env(0.45, 0.001, 0.15), at, db(-6 - 2 * k))
    hum = seg("transformer_hum", 20.0, 3.0)
    n = int(2.0 * SR)
    rate = np.linspace(0.35, 1.0, n) ** 0.7
    pos = np.cumsum(rate)
    pos = pos[pos < len(hum) - 1]
    i = pos.astype(int)
    rise = hum[i] * (1 - (pos - i)) + hum[i + 1] * (pos - i)
    rise *= np.clip(np.linspace(0, 1.4, len(rise)), 0, 1) * np.linspace(1, 0.6, len(rise))
    place(out, norm(band(rise, 40.0, 4000.0)), 0.35, db(-6))
    zap = band(seg("glorb_arcs", 12.0, 0.5), 400.0, None) * env(0.5, 0.002, 0.3)
    place(out, norm(zap), 0.34, db(-10))
    return finish(out, "power")


def surge(rng, variant):
    """A voltage dip: the buzz stutters (gated arc buzz, own processing) with a small snap."""
    length = 1.1
    x = band(seg("arc_buzz", 30.0 + 21.0 * variant, length), 120.0, None)
    t = t_axis(length)
    gate = (np.sin(2 * np.pi * (11.0 + 5.0 * variant) * t + np.sin(2 * np.pi * 3.0 * t) * 2.0) > -0.2).astype(float)
    gate = np.convolve(gate, np.ones(200) / 200, mode="same")
    x = x * gate * env(length, 0.01, 0.7, 0.2, 2.5)
    snap = band(seg("sparks_35kv", 5.0 + variant, 0.35), 800.0, None) * env(0.35, 0.001, 0.2)
    place(x, norm(snap) * np.std(x) * 4.0, 0.05)
    return finish(x, "surge")


def cascade(rng, variant):
    """A substation going down: a massive discharge, a low punch, metal groaning under the strain."""
    out = np.zeros(int(7.0 * SR))
    dis = band(seg("massive_discharge", [2.2, 25.0][variant], 5.5), 60.0, None)
    place(out, fade(norm(dis) * env(5.5, 0.01, 4.0, 0.4, 2.5), 0.01, 1.2), 0.0, db(0))
    sub = band(seg("deep_boom", 0.0, 3.0, 0.75), None, 160.0, 3.0)
    place(out, norm(sub) * env(3.0, 0.004, 2.0, 0.05, 2.5), 0.05, db(-2))
    t = t_axis(0.9)
    place(out, norm(np.sin(2 * np.pi * 38.0 * t - 5.0 * t) * np.exp(-t * 5.0)), 0.05, db(-5))
    groan = band(seg("creak_slow", 2.0 + 9.0 * variant, 4.5, 0.8), 70.0, 5000.0)
    place(out, norm(groan) * env(4.5, 0.5, 3.0, 0.8, 2.0), 1.6, db(-9))
    return finish(out, "cascade")


def metal_groan(rng, variant):
    if variant == 0:
        x = band(seg("metal_groan", 3.4, 4.6, 0.7), 60.0, 6000.0)
    else:
        x = band(seg("creak_slow", 28.5, 4.8, 0.85), 60.0, 6000.0)
    x = reverb(x, 1.8, rng, decay=1.6, wet=0.3)
    return finish(x * env(len(x) / SR, 0.08, 3.0, 1.0, 2.0), "groan")


def low_impact(rng, variant):
    out = np.zeros(int(3.0 * SR))
    boom = band(seg("deep_boom", 0.0, 3.0, [0.8, 0.68][variant]), None, 220.0, 2.5)
    place(out, norm(boom) * env(3.0, 0.003, 2.4, 0.05, 2.6), 0.0)
    t = t_axis(1.2)
    f0 = [34.0, 29.0][variant]
    place(out, norm(np.sin(2 * np.pi * f0 * t - 4.0 * t) * np.exp(-t * 3.5)), 0.0, db(-3))
    return finish(out, "impact")


def distant_destruction(rng, variant):
    if variant == 0:
        x = seg("distant_series", 29.6, 6.0)
    elif variant == 1:
        x = seg("distant_series", 41.6, 6.0)
    else:
        x = np.zeros(int(6.0 * SR))
        place(x, norm(seg("distant_metal", 0.0, 6.0)), 0.0)
        place(x, norm(band(seg("collapse", 0.0, 3.0, 0.8), None, 900.0)), 0.6, db(-4))
    x = band(x, 35.0, 2500.0)
    return finish(fade(x, 0.01, 1.5), "distant")


def disaster_bed(rng):
    """Non-positional bed while the city is in a disaster: the night air tone of a city and far rumbling."""
    air = band(seg("city_air", 20.0, 21.5), 60.0, 6000.0)
    rumble = band(seg("distant_series", 4.0, 21.5), 30.0, 400.0)
    x = norm(air) + norm(rumble) * db(-6)
    return finish(loop(x, 12.0, 1.5), "bed", True)


def variants(fn, n):
    return [lambda rng, k=k: fn(rng, k) for k in range(n)]


# name -> (builder or list of variant builders, source keys, rate)
CUES = {
    "siren_warning": ([siren_warning], ["prague_siren"], 32000),
    "siren_warning_far": ([siren_warning_far], ["prague_siren"], 22050),
    "siren_attack": ([siren_attack], ["sentry_siren"], 32000),
    "siren_attack_far": ([siren_attack_far], ["sentry_siren"], 22050),
    "siren_critical": ([siren_critical], ["mega_siren"], 32000),
    "siren_critical_far": ([siren_critical_far], ["mega_siren"], 22050),
    "siren_all_clear": ([siren_all_clear], ["sentry_siren", "wind_down"], 22050),
    "klaxon": ([klaxon], ["klaxon"], 32000),
    "alarm_beeps": ([alarm_beeps], ["alarm_panel"], 32000),
    "arc": (variants(arc, 4), ["kv_spark", "sparks_35kv", "glorb_arcs", "massive_discharge"], SR),
    "transformer_blow": (variants(transformer_blow, 3), ["breaker", "kv_spark", "electro_explosion", "deep_boom", "steady_sparks"], SR),
    "transformer_blow_far": (variants(transformer_blow_far, 2), ["breaker", "kv_spark", "electro_explosion", "deep_boom", "steady_sparks"], 22050),
    "spark_loop": ([spark_loop], ["steady_sparks"], SR),
    "line_hum": ([line_hum], ["transformer_hum", "power_lines"], 32000),
    "power_down": ([power_down], ["kill_switch", "transformer_hum", "breaker"], SR),
    "power_up": ([power_up], ["breaker", "transformer_hum", "glorb_arcs"], SR),
    "surge": (variants(surge, 2), ["arc_buzz", "sparks_35kv"], SR),
    "cascade": (variants(cascade, 2), ["massive_discharge", "deep_boom", "creak_slow"], SR),
    "metal_groan": (variants(metal_groan, 2), ["metal_groan", "creak_slow"], 32000),
    "low_impact": (variants(low_impact, 2), ["deep_boom"], 22050),
    "distant_destruction": (variants(distant_destruction, 3), ["distant_series", "distant_metal", "collapse"], 22050),
    "disaster_bed": ([disaster_bed], ["city_air", "distant_series"], 22050),
}
LOOPS = {"siren_warning", "siren_warning_far", "siren_attack", "siren_attack_far", "siren_critical", "siren_critical_far",
         "klaxon", "alarm_beeps", "spark_loop", "line_hum", "disaster_bed"}


def file_names(name):
    builders = CUES[name][0]
    return [name] if len(builders) == 1 else [f"{name}_{k + 1}" for k in range(len(builders))]


def write_wav(path, x, rate=SR):
    if rate != SR:
        x = band(x, None, rate * 0.45, 4.0)
        x = np.interp(np.arange(int(len(x) * rate / SR)) * SR / rate, np.arange(len(x)), x)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())


def encode(x, rate, quality):
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "a.wav")
        ogg = os.path.join(tmp, "a.ogg")
        write_wav(wav, x, rate)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", str(quality), "-ac", "1", ogg], check=True)
        return open(ogg, "rb").read()


def write_b64(name, data):
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f == name + ".ogg.b64" or f.startswith(name + ".ogg.b64.part"):
            os.remove(os.path.join(OUT, f))
    text = base64.encodebytes(data).decode("ascii")
    if len(text) <= PART:
        open(os.path.join(OUT, name + ".ogg.b64"), "w").write(text)
        return 1
    lines = text.splitlines(keepends=True)
    parts, cur = [], ""
    for line in lines:
        if len(cur) + len(line) > PART:
            parts.append(cur)
            cur = ""
        cur += line
    parts.append(cur)
    for k, p in enumerate(parts):
        open(os.path.join(OUT, f"{name}.ogg.b64.part{k}"), "w").write(p)
    return len(parts)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", action="append", default=[])
    ap.add_argument("--wav", help="write WAVs to this folder instead of packaging")
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--sources", action="store_true")
    ap.add_argument("--quality", type=float, default=3.0)
    args = ap.parse_args()
    if args.fetch:
        fetch()
        return
    if args.sources:
        for name, (_b, used, _r) in CUES.items():
            print(name, ", ".join(f"{SOURCES[k][0]} ({k})" for k in used))
        return
    names = args.only or list(CUES)
    total = 0
    for i, name in enumerate(CUES):
        if name not in names:
            continue
        builders, _used, rate = CUES[name]
        for k, (builder, fname) in enumerate(zip(builders, file_names(name))):
            x = builder(np.random.default_rng(7000 + 37 * i + k))
            peak = 20 * np.log10(np.abs(x).max() + 1e-12)
            if args.wav:
                os.makedirs(args.wav, exist_ok=True)
                write_wav(os.path.join(args.wav, fname + ".wav"), x)
                print(fname, f"{len(x) / SR:.2f}s peak {peak:.1f} dBFS")
                continue
            q = 1.0 if name in ("siren_warning_far", "siren_attack_far", "siren_critical_far", "disaster_bed") else \
                1.5 if name in ("siren_warning", "siren_attack", "siren_critical", "siren_all_clear", "klaxon", "alarm_beeps") else args.quality
            data = encode(x, rate, q)
            parts = write_b64(fname, data)
            total += len(data)
            print(f"{fname}: {len(x) / SR:.2f}s {rate} Hz q{q} {len(data) // 1024} KB, {parts} part(s), peak {peak:.1f} dBFS")
    if not args.wav:
        print(f"total {total // 1024} KB ogg")


if __name__ == "__main__":
    main()
