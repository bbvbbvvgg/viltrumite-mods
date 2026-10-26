#!/usr/bin/env python3
"""REINHARD OVERHAUL item 15: the sound of the Reid draw, one cue per phase of the draw timeline.

The draw is six seconds (draw ticks d = 0..120, 20 per second, see ReidDrawChoreo). Each cue below starts on
its draw tick (ReidDrawAudio starts it on the scene clock, so the caster, the onlookers and a late joiner hear
the same moment) and carries its own phase:

    reid_draw_hum       d6    own synthesis: a low hum that rises with the pull, trembles in the strain,
                              holds its breath (92-96), swells at the release and decays naturally
    reid_draw_koiguchi  d33   the mouth breaks at d34: a heavy katana clack, a faint ring, a felt thump
    reid_draw_scrape    d36   the long metallic scrape: the slow tonal draw of a real blade stretched over
                              the pull and shaped by the extraction speed, a gritty layer under the main
                              extraction, the catch of the strain (86-92), the rip and the exit "shing"
                              exactly at the release (d96, 3.000 s in) and the ring of the freed blade
    reid_draw_air       d56   own synthesis: the air starts to vibrate around him, a fluttering band of
                              wind rising in pitch and rate, sucked in before the release and cut at d96
    reid_draw_release   d96   the final accent: a cinematic hit with its long natural decay, a sub boom,
                              the deep ring of the sword and a burst of displaced air

Only CC0 recordings and own synthesis: the older CC-BY choir and the server-played rumble / unsheathe are no
longer part of the draw.

Recordings are CC0 1.0 Freesound sources fetched with the sound-design skill helper (Freesound preview MP3s,
never committed):

    python3 .agents/skills/sound-design/scripts/openverse_sfx.py download <openverse-uuid>

Run from the repository root:

    python3 tools/sfx/reinhard_draw.py              # mix every cue and package it (.ogg.b64, mono 44.1 kHz)
    python3 tools/sfx/reinhard_draw.py --only reid_draw_scrape
    python3 tools/sfx/reinhard_draw.py --check      # remix and compare with the committed assets

Levels are 100 ms momentary loudness maxima (BS.1770 K-weighting) under a true-peak ceiling, using the helpers
of reinhard_strikes.py. No listening test is implied by this script: numbers are measured, not auditioned.
"""
import argparse
import math
import os
import subprocess
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reinhard_strikes as strikes  # noqa: E402  (shared loudness, true-peak, limiter and asset helpers)

ROOT, HELPER, RAW, ASSETS, SR = strikes.ROOT, strikes.HELPER, strikes.RAW, strikes.ASSETS, strikes.SR
TICKS = 20.0

# The draw timeline (ReinhardRules / ReidDrawChoreo); a unit test keeps these in sync with the Java source.
KOIGUCHI, STRAIN, SNAP, RELEASE, LENGTH = 34, 86, 92, 96, 120
# ReidDrawChoreo extraction keys: draw tick -> blade out of the scabbard (native px).
S_T = (0, 32, 35, 37, 60, 86, 92, 96, 100)
S_V = (0, 0, 0.8, 0.9, 12, 38, 41, 49.58 - 3.28, 49.58 - 3.28 + 7)

# key -> (Openverse UUID, Freesound id, creator, original title). All CC0 1.0 (checked on the Freesound pages).
SOURCES = {
    "draw": ("c9cba616-8be5-47bc-9edb-ac99cb6843f3", 592023, "C-V", "sword drawn.wav"),
    "grit": ("0e790aec-2dbc-4a85-9771-e03c4ccb7065", 337835, "newagesoup", "long-metal-scrape-05.wav"),
    "clack": ("787844c2-63f2-485a-8eaf-e23be80af3e0", 593485, "Scottrex05", "Katana sheath.wav"),
    "riser_hit": ("0b657f87-2549-45e8-a1ae-4a991818ced8", 649933, "AudioPapkin", "Riser Hit sfx 046.wav"),
    "bass_impact": strikes.SOURCES["bass_impact"],
    "sword_ring": strikes.SOURCES["sword_ring"],
}

# cue -> draw tick it starts on, attenuation distance (blocks), 100 ms loudness target LUFS, true-peak ceiling
# dBTP, most look-ahead limiting allowed (dB). The Java side (ReidDrawAudio.CUES, sounds.json) is tested to match.
CUES = {
    "reid_draw_hum": dict(start=6, distance=96, target=-17.0, ceiling=-3.0, limit=0.0),
    "reid_draw_koiguchi": dict(start=33, distance=32, target=-14.0, ceiling=-1.0, limit=1.5),
    "reid_draw_scrape": dict(start=36, distance=48, target=-12.0, ceiling=-1.0, limit=1.5),
    "reid_draw_air": dict(start=56, distance=64, target=-19.0, ceiling=-3.0, limit=0.0),
    "reid_draw_release": dict(start=96, distance=128, target=-8.0, ceiling=-1.0, limit=3.0),
}
OWN = {"reid_draw_hum", "reid_draw_air"}
OUTPUTS = list(CUES)


def at(cue, d):
    """Seconds into a cue at draw tick d."""
    return (d - CUES[cue]["start"]) / TICKS


def smooth(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


def raw_path(src, raw_dir):
    uuid = SOURCES[src][0]
    for ext in ("mp3", "wav", "flac", "ogg"):
        path = os.path.join(raw_dir, f"{uuid}.{ext}")
        if os.path.isfile(path):
            return path
    sys.exit(f"Missing source {src} ({uuid}); run: python3 {os.path.relpath(HELPER, ROOT)} download {uuid}")


def take(src, raw_dir, start, length, pitch=1.0, tempo=1.0, hp=None, lp=None, gain=0.0, fade_in=0.003, fade_out=None):
    """`length` seconds of a source from `start`, at varispeed `pitch`, time-stretched by `tempo` (atempo, pitch kept)."""
    chain = [f"aresample={SR}", f"atrim=start={start}:duration={length}", "asetpts=N/SR/TB"]
    if pitch != 1.0:
        chain += [f"asetrate={SR * pitch:.3f}", f"aresample={SR}"]
    if tempo != 1.0:
        chain.append(f"atempo={tempo:.5f}")
    if hp:
        chain.append(f"highpass=f={hp}:poles=2")
    if lp:
        chain.append(f"lowpass=f={lp}:poles=2")
    x = strikes.ffmpeg_pcm(raw_path(src, raw_dir), ",".join(chain)) * 10 ** (gain / 20)
    n = len(x)
    fi = min(n, int(fade_in * SR))
    if fi:
        x[:fi] *= np.linspace(0.0, 1.0, fi)
    if fade_out:
        fo = min(n, int(fade_out * SR))
        x[n - fo:] *= np.cos(np.linspace(0.0, math.pi / 2, fo)) ** 2
    return x


def place(out, x, delay):
    i = int(round(delay * SR))
    n = min(len(x), len(out) - i)
    out[i:i + n] += x[:n]


def timeline(n):
    return np.arange(n) / SR


def extraction_rate(d):
    """Blade speed (native px per draw tick), piecewise from the choreography keys, 0 outside the pull."""
    d = np.asarray(d, dtype=np.float64)
    rate = np.zeros_like(d)
    for (t0, v0), (t1, v1) in zip(zip(S_T, S_V), zip(S_T[1:], S_V[1:])):
        inside = (d >= t0) & (d < t1) & (d < RELEASE)
        rate[inside] = (v1 - v0) / (t1 - t0)
    return rate


def blur(x, seconds):
    w = max(1, int(seconds * SR))
    kernel = np.hanning(w + 2)[1:-1]
    return np.convolve(x, kernel / kernel.sum(), mode="same")


def stutter(n, seed, lo, hi, rate):
    """Seeded irregular catches: 1 - depth at random moments `rate` times per second, smoothed."""
    rng = np.random.default_rng(seed)
    t = timeline(n)
    out = np.ones(n)
    mask = (t >= lo) & (t < hi)
    k = int((hi - lo) * rate)
    for c in np.sort(rng.uniform(lo, hi, k)):
        depth = rng.uniform(0.35, 0.7)
        width = rng.uniform(0.012, 0.03)
        out -= depth * np.exp(-0.5 * ((t - c) / width) ** 2) * mask
    return np.clip(out, 0.15, 1.0)


def hum():
    """Own synthesis, 6.3 s from d6: a low drone (rising 41 -> 55 Hz), harmonic stack with a slow metallic shimmer."""
    cue = "reid_draw_hum"
    n = int(6.3 * SR)
    t = timeline(n)
    d = CUES[cue]["start"] + t * TICKS
    rng = np.random.default_rng(15)
    rise = smooth((d - 6) / (STRAIN - 6))
    f0 = 41.0 + 14.0 * rise ** 1.3
    vib = 1 + 0.012 * smooth((d - STRAIN) / 2) * (1 - smooth((d - SNAP) / 3)) * np.sin(2 * math.pi * 9.0 * t + 0.6)
    phase = 2 * math.pi * np.cumsum(f0 * vib) / SR
    bright = 0.25 + 0.75 * smooth((d - 20) / (RELEASE - 20))
    x = np.zeros(n)
    for k in range(1, 11):
        amp = (1.0 / k ** 1.15) * (bright ** max(0, k - 2) if k > 2 else 1.0)
        x += amp * np.sin(k * phase + rng.uniform(0, 2 * math.pi))
        # A second voice a few cents up: the slow beating of a big resonating body.
        x += 0.35 * amp * np.sin(k * phase * 1.0035 + rng.uniform(0, 2 * math.pi))
    # Inharmonic blade partials (free-free bar ratios) over the drone, entering with the light.
    shimmer = smooth((d - KOIGUCHI) / 30) * 0.6
    for ratio, a in ((5.404, 0.10), (8.933, 0.06), (13.34, 0.035)):
        x += shimmer * a * np.sin(ratio * 2 * phase + rng.uniform(0, 2 * math.pi)) * (1 + 0.3 * np.sin(2 * math.pi * 0.37 * ratio * t))
    # A low rumble bed: band-limited noise around the drone.
    noise = np.fft.irfft(np.fft.rfft(rng.standard_normal(n)) * np.exp(-0.5 * ((np.fft.rfftfreq(n, 1 / SR) - 70) / 45) ** 2), n)
    x += 0.8 * noise / (np.max(np.abs(noise)) + 1e-12) * (0.3 + 0.7 * rise)
    # Envelope: in from the breath, rising with the pull, tremble, inhale, swell at the release, natural decay.
    env = 0.04 + 0.21 * smooth((d - 6) / 26) + 0.45 * smooth((d - 30) / (STRAIN - 30)) ** 1.4
    trem = 1 - 0.28 * smooth((d - STRAIN) / 2) * (1 - smooth((d - SNAP) / 1.5)) * (0.5 + 0.5 * np.sin(2 * math.pi * 10.5 * t) * np.sin(2 * math.pi * 3.1 * t + 1.0))
    inhale = 1 - 0.5 * smooth((d - SNAP) / 3.5)
    pre = env * trem * inhale
    s = np.maximum(0.0, d - RELEASE)
    post = (0.35 + 0.65 * smooth(s / 0.6)) * (0.55 * np.exp(-s / 9.0) + 0.45 * np.exp(-s / 22.0)) * 1.05
    k = smooth((d - (RELEASE - 0.6)) / 0.6)
    gain = pre * (1 - k) + post * k
    gain *= smooth(t / 0.4)
    gain[-int(0.3 * SR):] *= np.cos(np.linspace(0.0, math.pi / 2, int(0.3 * SR))) ** 2
    y = x * gain
    return y - np.mean(y)


def koiguchi(raw_dir):
    """d33, 0.6 s: the clack lands at 0.050 s (d34)."""
    n = int(0.6 * SR)
    out = np.zeros(n)
    clack = take("clack", raw_dir, 5.90, 0.42, pitch=0.85, hp=110, fade_out=0.2)
    place(out, clack, 0.050 - 0.02 / 0.85)
    place(out, take("sword_ring", raw_dir, 0.0, 0.55, pitch=1.3, hp=500, gain=-20, fade_out=0.35), 0.050)
    place(out, take("bass_impact", raw_dir, 0.045, 0.45, pitch=1.4, hp=35, gain=-15, fade_out=0.3), 0.050)
    out[-int(0.05 * SR):] *= np.linspace(1.0, 0.0, int(0.05 * SR))
    return out - np.mean(out)


def scrape(raw_dir):
    """d36, 3.8 s: the slow draw (0.05-2.75 s) shaped by the blade speed, the catch of the strain, the rip, the exit
    peak at 3.000 s (d96) and the ring of the freed blade."""
    cue = "reid_draw_scrape"
    n = int(3.8 * SR)
    t = timeline(n)
    d = CUES[cue]["start"] + t * TICKS
    exit_at = at(cue, RELEASE)
    speed = blur(extraction_rate(d), 0.25)
    shape = 0.4 + 0.6 * np.clip(speed / 1.0, 0.0, 1.4)
    strain = smooth((d - STRAIN) / 1.5) * (1 - smooth((d - SNAP) / 1.5))
    catch = 1 - (1 - stutter(n, 86, at(cue, STRAIN), at(cue, SNAP), 16)) * 0.8
    slow = np.zeros(n)
    # Source 0.80-2.68 s (the slow tonal draw of a knife) at varispeed 0.8, stretched to 2.70 s: the singing steel.
    place(slow, take("draw", raw_dir, 0.80, 1.88, pitch=0.8, tempo=(1.88 / 0.8) / 2.70, hp=150, gain=9, fade_in=0.2, fade_out=0.03), 0.05)
    grit = np.zeros(n)
    # The grinding under the main extraction (from d58) and, stuttering, in the strain.
    place(grit, take("grit", raw_dir, 1.10, 2.0 * 0.9, pitch=0.9, hp=200, lp=7000, gain=-8, fade_in=0.5, fade_out=0.02), 1.0)
    grit[t >= exit_at] = 0.0
    grit *= smooth((d - 58) / 10) * (1 + 1.2 * strain) * catch
    body = (slow * (1 - 0.35 * strain) + grit) * shape
    rip = np.zeros(n)
    # Source 2.68-3.40 s: the rip and the exit peak (2.90 s in the source, 0.275 s in at varispeed 0.8).
    place(rip, take("draw", raw_dir, 2.68, 0.72, pitch=0.8, hp=150, fade_in=0.02, fade_out=0.25), exit_at - (2.90 - 2.68) / 0.8)
    ring = np.zeros(n)
    place(ring, take("sword_ring", raw_dir, 0.0, 0.8, pitch=0.9, hp=200, gain=-11, fade_out=0.5), exit_at)
    out = body + rip + ring
    out[-int(0.05 * SR):] *= np.linspace(1.0, 0.0, int(0.05 * SR))
    return out - np.mean(out)


def air():
    """Own synthesis, d56, 2.0 s: band of fluttering wind 250 -> 1100 Hz, sucked in over the last 0.2 s, cut at d96."""
    cue = "reid_draw_air"
    n = int(at(cue, RELEASE) * SR)
    rng = np.random.default_rng(56)
    frame, hop = 2048, 512
    window = np.hanning(frame)
    freqs = np.fft.rfftfreq(frame, 1 / SR)
    out = np.zeros(n + frame)
    norm = np.zeros(n + frame)
    length = n / SR
    for start in range(0, n, hop):
        tc = (start + frame / 2) / SR
        u = min(1.0, tc / length)
        suck = float(smooth((tc - (length - 0.25)) / 0.25))
        fc = 250.0 * (1100.0 / 250.0) ** u * (1 + 1.2 * suck)
        width = 0.55 - 0.15 * u
        mag = np.exp(-0.5 * (np.log(np.maximum(freqs, 1.0) / fc) / width) ** 2)
        spec = mag * np.exp(1j * rng.uniform(0, 2 * math.pi, len(freqs)))
        seg = np.fft.irfft(spec, frame) * window
        out[start:start + frame] += seg
        norm[start:start + frame] += window ** 2
    x = out[:n] / np.maximum(norm[:n], 1e-3)
    t = timeline(n)
    rate = 5.0 + 8.0 * (t / length)
    flutter = 1 - (0.35 + 0.25 * t / length) * (0.5 + 0.5 * np.sin(2 * math.pi * np.cumsum(rate) / SR))
    env = smooth(t / 0.5) * (0.35 + 0.65 * smooth(t / (length - 0.3))) * (1 + 0.45 * smooth((t - (length - 0.25)) / 0.25))
    y = x * flutter * env
    y[-int(0.008 * SR):] *= np.linspace(1.0, 0.0, int(0.008 * SR))
    return y - np.mean(y)


def burst(n):
    """Own: displaced air at the release, a 5 ms attack and a low-pass sweep from 6 kHz down to 400 Hz."""
    rng = np.random.default_rng(96)
    frame, hop = 2048, 512
    window = np.hanning(frame)
    freqs = np.fft.rfftfreq(frame, 1 / SR)
    out = np.zeros(n + frame)
    norm = np.zeros(n + frame)
    for start in range(0, n, hop):
        tc = (start + frame / 2) / SR
        cutoff = 400.0 + 5600.0 * math.exp(-tc / 0.25)
        mag = 1 / np.sqrt(1 + (freqs / cutoff) ** 4) / np.sqrt(1 + (60.0 / np.maximum(freqs, 1.0)) ** 4)
        seg = np.fft.irfft(mag * np.exp(1j * rng.uniform(0, 2 * math.pi, len(freqs))), frame) * window
        out[start:start + frame] += seg
        norm[start:start + frame] += window ** 2
    x = out[:n] / np.maximum(norm[:n], 1e-3)
    t = timeline(n)
    return x * np.minimum(1.0, t / 0.005) * np.exp(-t / 0.35)


def release(raw_dir):
    """d96, 4.8 s: transient at 0."""
    n = int(4.8 * SR)
    out = np.zeros(n)
    place(out, take("riser_hit", raw_dir, 0.535, 4.6, pitch=0.92, hp=30, fade_out=1.6), 0.0)
    place(out, take("bass_impact", raw_dir, 0.045, 3.2, pitch=0.85, hp=28, gain=-6, fade_out=1.2), 0.0)
    place(out, take("sword_ring", raw_dir, 0.0, 4.0, pitch=0.72, hp=60, gain=-7, fade_out=1.6), 0.0)
    b = burst(int(1.6 * SR))
    place(out, b / (np.max(np.abs(b)) + 1e-12) * 0.25, 0.0)
    out[-int(0.05 * SR):] *= np.linspace(1.0, 0.0, int(0.05 * SR))
    return out - np.mean(out)


def raw_mix(name, raw_dir):
    return {"reid_draw_hum": lambda: hum(), "reid_draw_koiguchi": lambda: koiguchi(raw_dir),
            "reid_draw_scrape": lambda: scrape(raw_dir), "reid_draw_air": lambda: air(),
            "reid_draw_release": lambda: release(raw_dir)}[name]()


def finish(name, x):
    """Gain to the cue's loudness target (lowered if the transient would need more than its allowed limiting), then limit."""
    spec = CUES[name]
    loud = strikes.momentary_max(x)
    gain = min(spec["target"] - loud, spec["ceiling"] + spec["limit"] - 0.05 - strikes.true_peak(x))
    return strikes.limit(x * 10 ** (gain / 20), spec["ceiling"])


def render(names, raw_dir):
    return {name: finish(name, raw_mix(name, raw_dir)) for name in names}


def sources_of(name):
    used = {
        "reid_draw_koiguchi": ["clack", "sword_ring", "bass_impact"],
        "reid_draw_scrape": ["draw", "grit", "sword_ring"],
        "reid_draw_release": ["riser_hit", "bass_impact", "sword_ring"],
    }
    return [SOURCES[s][0] for s in used.get(name, [])]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--src", default=RAW, help="folder with <uuid>.mp3 downloads (default: helper cache)")
    parser.add_argument("--out", default=os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "mix", "reinhard"))
    parser.add_argument("--only", action="append", choices=OUTPUTS)
    parser.add_argument("--check", action="store_true", help="remix and compare with the committed assets")
    parser.add_argument("--no-package", action="store_true", help="write the reviewed WAV mixes only")
    args = parser.parse_args()
    names = args.only or OUTPUTS
    os.makedirs(args.out, exist_ok=True)
    failed = False
    mixes = render(names, args.src)
    for name in names:
        x = mixes[name]
        if args.check:
            y = strikes.decode_asset(name)
            m = min(len(x), len(y))
            corr = float(np.dot(x[:m], y[:m]) / (np.linalg.norm(x[:m]) * np.linalg.norm(y[:m]) + 1e-15))
            ok = abs(len(x) - len(y)) <= 0.03 * SR and corr >= 0.95
            failed |= not ok
            print(f"{name}: {'ok' if ok else 'MISMATCH'} correlation {corr:.4f}, {len(y) / SR:.3f} s")
            continue
        wav = os.path.join(args.out, name + ".wav")
        strikes.write_wav(x, wav)
        print(f"{name}: {len(x) / SR:.3f} s, M100 {strikes.momentary_max(x):.1f} LUFS, TP {strikes.true_peak(x):.1f} dBTP -> {wav}")
        if not args.no_package:
            cmd = [sys.executable, HELPER, "package", "--input", wav, "--module", "core", "--name", "reinhard/" + name,
                   "--event", "reinhard_" + name, "--replace"]
            if name in OWN:
                cmd.append("--own")
            for uuid in sources_of(name):
                cmd += ["--source-id", uuid]
            subprocess.run(cmd, cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
