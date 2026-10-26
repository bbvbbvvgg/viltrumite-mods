#!/usr/bin/env python3
"""REINHARD OVERHAUL strike and stance sounds, mixed from CC0 Freesound recordings found via Openverse.

The recordings are fetched with the sound-design skill helper (Freesound preview MP3s, never committed):

    python3 .agents/skills/sound-design/scripts/openverse_sfx.py download <openverse-uuid>

which stores them as ~/.cache/viltrumite-sfx/work/raw/<uuid>.mp3 together with the Openverse metadata and the
verbatim attribution line. Every source below is CC0 1.0 (checked on its Freesound page, see
tools/assets/reinhard_sources.md). Run from the repository root:

    python3 tools/sfx/reinhard_strikes.py              # mix every cue into the scratch folder and package it
    python3 tools/sfx/reinhard_strikes.py --only reid_hit_2
    python3 tools/sfx/reinhard_strikes.py --check      # remix and compare with the committed assets

A cue is a list of layers. A layer takes `length` seconds of a source from `start`, plays it at `pitch`
(varispeed: pitch 0.8 is lower and 1.25x longer), filters it, applies `gain` dB and lands `delay` seconds
into the cue. The sum is faded, loudness-matched per class (100 ms momentary loudness, BS.1770 K-weighting)
under the class true-peak ceiling: at most 1.5 dB of look-ahead peak limiting on sword hits, none on fists,
swings or the loop; a variant that would need more lowers the whole class instead. `reid_drawn_hum` is a spectral freeze of the recorded sword ring:
the magnitude spectrum of the ring tail with seeded random phases over exactly one loop length, so the loop
is seamless by construction. Packaging goes through the skill helper (mono 44.1 kHz Vorbis q5, .ogg.b64).

No listening test is implied by this script: numbers are measured, not auditioned.
"""
import argparse
import math
import os
import subprocess
import sys
import tempfile

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HELPER = os.path.join(ROOT, ".agents", "skills", "sound-design", "scripts", "openverse_sfx.py")
RAW = os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "raw")
ASSETS = os.path.join(ROOT, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore", "sounds", "reinhard")
SR = 44100

# key -> (Openverse UUID, Freesound id, creator, original title). All CC0 1.0.
SOURCES = {
    "woosh": ("b8d4ab14-8b11-47a9-a386-10bd626ee880", 614084, "mateusboga", "Woosh 1"),
    "fast_swing": ("518ead89-8e89-4614-b954-bb7a844f3c42", 60004, "qubodup", "Fast Sword Swing Sound"),
    "swosh": ("084c97f3-439e-4f7f-af13-5473411c5c43", 60024, "qubodup", "swosh swinging sword 37.flac"),
    "cable_swoosh": ("c1b6b14f-c5d2-4511-9d8a-81fb973efe58", 507466, "Danjocross", "Clean fast Swoosh.aiff"),
    "light_swoosh": ("d343ce9e-01df-4852-b625-3ac3eb805cdd", 475132, "bolkmar", "FX - Swoosh - Light"),
    "body_punches": ("84ea30b3-f549-4663-919b-588d7ed3bac6", 393904, "Rodzuz", "Body punches"),
    "bass_impact": ("d946174b-650a-4ef5-ba65-2b11686aa732", 512389, "Kievinay", "Bass Impact - Ki_01.mp3"),
    "deep_cut": ("83b6422f-50a2-45e5-8408-048db7b9e341", 420674, "SypherZent", "Deep Cut / Slash / Gash"),
    "hit_swing": ("cd90a58e-6aa9-4f1b-a421-96d8861147c1", 547038, "CogFireStudios", "Hit Swing Sword"),
    "impact_1": ("ced84325-bddc-4add-be3c-b529c146219f", 547035, "Eponn", "Hit Impact Sword"),
    "impact_2": ("b122081c-ef13-455b-8fd0-675fcbb8157e", 547036, "Eponn", "Hit Impact Sword 2"),
    "impact_3": ("3c27fa96-eaf7-440f-9d1c-d14db5e381ae", 547042, "CogFireStudios", "Hit Impact Sword 3"),
    "sword_ring": ("2ee6f76b-8bfb-4129-b200-7bdc4f398bb0", 22426, "kibibu", "sword_2.wav"),
}

# class -> (100 ms momentary loudness target LUFS, true-peak ceiling dBTP, most peak limiting allowed dB)
CLASSES = {"fist_swing": (-15.0, -3.0, 0.0), "fist_hit": (-15.0, -1.0, 0.0), "reid_swing": (-13.0, -2.0, 0.0),
           "reid_hit": (-9.0, -1.0, 1.5), "reid_heavy": (-8.0, -1.0, 1.5), "reid_drawn_hum": (-18.0, -6.0, 0.0)}


def layer(src, start, length, pitch=1.0, gain=0.0, delay=0.0, hp=None, lp=None, fade_in=0.003, fade_out=None, eq=()):
    return dict(src=src, start=start, length=length, pitch=pitch, gain=gain, delay=delay, hp=hp, lp=lp,
                fade_in=fade_in, fade_out=fade_out, eq=eq)


SLAP = ((1700, 1.0, 4.0),)  # +4 dB one octave around 1.7 kHz: the knuckle slap reads on small speakers

# Trigger ticks (20 t/s): fist swing at contactStart-1 (whoosh peak ~60 ms in), Reid swing at contactStart-2 (peak
# ~100-150 ms in), hits at the authored contact (transient at 0). Rodzuz hits start 3 ms before the measured onset.
CUES = {
    "fist_swing_1": ([layer("woosh", 0.0, 0.165, hp=150, fade_out=0.05)], 0.17),
    "fist_swing_2": ([layer("fast_swing", 0.01, 0.30, pitch=1.12, hp=150, fade_out=0.14)], 0.26),
    "fist_swing_3": ([layer("cable_swoosh", 0.225, 0.32, hp=150, fade_out=0.16)], 0.32),
    "fist_hit_1": ([layer("body_punches", 2.085, 0.30, hp=90, fade_out=0.16, eq=SLAP),
                    layer("bass_impact", 0.056, 0.22, pitch=1.25, gain=-20, hp=50, fade_out=0.14)], 0.30),
    "fist_hit_2": ([layer("body_punches", 14.787, 0.28, hp=90, fade_out=0.15, eq=SLAP),
                    layer("bass_impact", 0.056, 0.22, pitch=1.35, gain=-20, hp=50, fade_out=0.14)], 0.28),
    "fist_hit_3": ([layer("body_punches", 16.998, 0.30, hp=90, fade_out=0.16, eq=SLAP),
                    layer("bass_impact", 0.056, 0.24, pitch=1.15, gain=-19, hp=50, fade_out=0.15)], 0.30),
    "reid_swing_1": ([layer("swosh", 0.06, 0.40, pitch=0.88, hp=90, fade_out=0.18),
                      layer("light_swoosh", 0.05, 0.45, gain=4, hp=400, fade_out=0.25)], 0.46),
    "reid_swing_2": ([layer("fast_swing", 0.0, 0.38, pitch=0.78, hp=80, fade_out=0.22),
                      layer("woosh", 0.0, 0.165, pitch=0.8, gain=-2, delay=0.02, hp=200, fade_out=0.08)], 0.49),
    "reid_swing_3": ([layer("cable_swoosh", 0.20, 0.45, pitch=0.8, hp=80, fade_out=0.28),
                      layer("swosh", 0.06, 0.34, pitch=0.8, gain=-6, hp=90, fade_out=0.18)], 0.56),
    "reid_hit_1": ([layer("impact_2", 0.0, 0.80, fade_out=0.35),
                    layer("bass_impact", 0.056, 0.40, pitch=1.1, gain=-10, hp=45, fade_out=0.25),
                    layer("sword_ring", 0.0, 0.90, gain=-18, hp=60, fade_out=0.5)], 0.80),
    "reid_hit_2": ([layer("hit_swing", 0.05, 0.70, pitch=0.94, fade_out=0.35),
                    layer("body_punches", 17.529, 0.30, gain=-6, hp=70, fade_out=0.16),
                    layer("bass_impact", 0.056, 0.35, pitch=1.1, gain=-9, hp=45, fade_out=0.22),
                    layer("sword_ring", 0.0, 0.90, pitch=1.06, gain=-18, hp=60, fade_out=0.5)], 0.75),
    "reid_hit_3": ([layer("deep_cut", 0.07, 0.26, pitch=0.92, fade_out=0.12),
                    layer("impact_1", 0.0, 0.70, pitch=1.05, gain=-4, lp=2500, fade_out=0.35),
                    layer("bass_impact", 0.056, 0.35, pitch=1.2, gain=-8, hp=45, fade_out=0.22),
                    layer("sword_ring", 0.0, 0.90, pitch=0.95, gain=-18, hp=60, fade_out=0.5)], 0.70),
    "reid_heavy_1": ([layer("impact_3", 0.0, 1.30, pitch=0.92, fade_out=0.6),
                      layer("bass_impact", 0.04, 1.20, gain=-5, hp=38, fade_out=0.7),
                      layer("sword_ring", 0.0, 2.20, pitch=0.9, gain=-12, hp=60, fade_out=1.2),
                      layer("body_punches", 16.998, 0.30, pitch=0.85, gain=-8, hp=70, fade_out=0.16)], 1.60),
    "reid_heavy_2": ([layer("impact_1", 0.0, 1.30, pitch=0.88, fade_out=0.6),
                      layer("bass_impact", 0.04, 1.20, pitch=0.92, gain=-5, hp=38, fade_out=0.7),
                      layer("sword_ring", 0.0, 2.20, pitch=0.85, gain=-12, hp=60, fade_out=1.2),
                      layer("deep_cut", 0.07, 0.26, pitch=0.8, gain=-9, fade_out=0.12)], 1.60),
}
HUM = dict(src="sword_ring", start=0.35, length=2.0, loop=4.0, seed=22426, band=(70.0, 3000.0), presence=12.0)
OUTPUTS = list(CUES) + ["reid_drawn_hum"]


def cue_class(name):
    return name.rstrip("0123456789").rstrip("_") if name[-1].isdigit() else name


def raw_path(src, raw_dir):
    uuid = SOURCES[src][0]
    for ext in ("mp3", "wav", "flac", "ogg"):
        path = os.path.join(raw_dir, f"{uuid}.{ext}")
        if os.path.isfile(path):
            return path
    sys.exit(f"Missing source {src} ({uuid}); run: python3 {os.path.relpath(HELPER, ROOT)} download {uuid}")


def ffmpeg_pcm(path, chain):
    cmd = ["ffmpeg", "-v", "error", "-nostdin", "-i", path, "-af", chain, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"]
    return np.frombuffer(subprocess.run(cmd, capture_output=True, check=True).stdout, dtype=np.float32).astype(np.float64)


def render_layer(spec, raw_dir):
    chain = [f"aresample={SR}", f"atrim=start={spec['start']}:duration={spec['length']}", "asetpts=N/SR/TB"]
    if spec["pitch"] != 1.0:
        chain += [f"asetrate={SR * spec['pitch']:.3f}", f"aresample={SR}"]
    if spec["hp"]:
        chain.append(f"highpass=f={spec['hp']}:poles=2")
    if spec["lp"]:
        chain.append(f"lowpass=f={spec['lp']}:poles=2")
    for freq, width, g in spec["eq"]:
        chain.append(f"equalizer=f={freq}:t=o:w={width}:g={g}")
    x = ffmpeg_pcm(raw_path(spec["src"], raw_dir), ",".join(chain)) * 10 ** (spec["gain"] / 20)
    n = len(x)
    fi = min(n, int(spec["fade_in"] * SR))
    if fi:
        x[:fi] *= np.linspace(0.0, 1.0, fi)
    if spec["fade_out"]:
        fo = min(n, int(spec["fade_out"] * SR))
        x[n - fo:] *= np.cos(np.linspace(0.0, math.pi / 2, fo)) ** 2
    return x


def biquad(x, b, a):
    # Direct form II transposed; small clips only.
    y = np.empty_like(x)
    z1 = z2 = 0.0
    b0, b1, b2 = (v / a[0] for v in b)
    a1, a2 = a[1] / a[0], a[2] / a[0]
    for i, xi in enumerate(x):
        yi = b0 * xi + z1
        z1 = b1 * xi - a1 * yi + z2
        z2 = b2 * xi - a2 * yi
        y[i] = yi
    return y


def k_weight(x):
    # ITU-R BS.1770 pre-filter and RLB high-pass, recomputed for 44.1 kHz.
    f0, gain, q = 1681.974450955533, 3.999843853973347, 0.7071752369554196
    k = math.tan(math.pi * f0 / SR)
    vh, vb = 10 ** (gain / 20), 10 ** (gain / 20) ** 0.4996667741545416
    a0 = 1 + k / q + k * k
    y = biquad(x, [(vh + vb * k / q + k * k), 2 * (k * k - vh), (vh - vb * k / q + k * k)], [a0, 2 * (k * k - 1), 1 - k / q + k * k])
    f0, q = 38.13547087602444, 0.5003270373238773
    k = math.tan(math.pi * f0 / SR)
    return biquad(y, [1.0, -2.0, 1.0], [1 + k / q + k * k, 2 * (k * k - 1), 1 - k / q + k * k])


def momentary_max(x):
    k = k_weight(x)
    w, hop = int(0.1 * SR), int(0.01 * SR)
    energies = [np.mean(k[i:i + w] ** 2) for i in range(0, max(1, len(k) - w + 1), hop)]
    return -0.691 + 10 * math.log10(max(energies) + 1e-15)


def true_peak(x):
    up = np.fft.irfft(np.fft.rfft(x), 4 * len(x)) * 4
    return 20 * math.log10(np.max(np.abs(up)) + 1e-15)


def level(cues):
    """Gain per cue: every variant of a class at the same 100 ms loudness.

    The class loudness is the target, or lower if a variant's transient would need more than the class's
    allowed peak limiting to stay under the ceiling, so variants stay level-matched."""
    gains = {}
    for cls in dict.fromkeys(cue_class(name) for name in cues):
        target, ceiling, allowance = CLASSES[cls]
        members = [name for name in cues if cue_class(name) == cls]
        loud = {name: momentary_max(cues[name]) for name in members}
        common = min([target] + [loud[name] + ceiling + allowance - 0.05 - true_peak(cues[name]) for name in members])
        for name in members:
            gains[name] = common - loud[name]
    return gains


def limit(x, ceiling_db):
    """ffmpeg's look-ahead limiter (1 ms attack, 40 ms release, no make-up gain), 0.5 dB under the ceiling."""
    if true_peak(x) <= ceiling_db:
        return x
    lim = 10 ** ((ceiling_db - 0.5) / 20)
    cmd = ["ffmpeg", "-v", "error", "-nostdin", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
           "-af", f"alimiter=limit={lim:.5f}:attack=1:release=40:level=0", "-f", "f32le", "-"]
    y = np.frombuffer(subprocess.run(cmd, input=x.astype(np.float32).tobytes(), capture_output=True, check=True).stdout,
                      dtype=np.float32).astype(np.float64)
    return y[:len(x)]


def mix(name, raw_dir):
    layers, length = CUES[name]
    out = np.zeros(int(round(length * SR)))
    for spec in layers:
        x = render_layer(spec, raw_dir)
        at = int(round(spec["delay"] * SR))
        n = min(len(x), len(out) - at)
        out[at:at + n] += x[:n]
    tail = min(len(out), int(0.012 * SR))
    out[len(out) - tail:] *= np.linspace(1.0, 0.0, tail)
    return out - np.mean(out)


def hum(raw_dir):
    spec = layer(HUM["src"], HUM["start"], HUM["length"], fade_in=0.0)
    seg = render_layer(spec, raw_dir)
    # Flatten the natural decay so the frozen spectrum is the ring's colour, not its first 50 ms.
    w = int(0.05 * SR)
    env = np.sqrt(np.convolve(seg ** 2, np.ones(w) / w, mode="same")) + 1e-6
    flat = seg / env
    mag = np.abs(np.fft.rfft(flat * np.hanning(len(flat))))
    n = int(round(HUM["loop"] * SR))
    f_loop = np.fft.rfftfreq(n, 1 / SR)
    m = np.interp(f_loop, np.fft.rfftfreq(len(flat), 1 / SR), mag)
    lo, hi = HUM["band"]
    m *= 1 / np.sqrt(1 + (lo / np.maximum(f_loop, 1.0)) ** 4) / np.sqrt(1 + (f_loop / hi) ** 4)
    m *= 10 ** (HUM["presence"] / 20 * np.clip((f_loop - 250.0) / 350.0, 0.0, 1.0))
    rng = np.random.default_rng(HUM["seed"])
    phase = rng.uniform(0, 2 * math.pi, len(m))
    phase[0] = 0.0
    x = np.fft.irfft(m * np.exp(1j * phase), n)
    t = np.arange(n) / n
    x *= 1 + 0.18 * np.sin(2 * math.pi * 2 * t) + 0.07 * np.sin(2 * math.pi * 5 * t + 1.1)
    return x - np.mean(x)


def render(names, raw_dir):
    """Mix the requested cues plus their class siblings (needed for level matching), then apply the gains."""
    classes = {cue_class(name) for name in names}
    cues = {name: hum(raw_dir) if name == "reid_drawn_hum" else mix(name, raw_dir)
            for name in OUTPUTS if cue_class(name) in classes}
    gains = level(cues)
    return {name: limit(cues[name] * 10 ** (gains[name] / 20), CLASSES[cue_class(name)][1]) for name in names}


def sources_of(name):
    srcs = [HUM["src"]] if name == "reid_drawn_hum" else [spec["src"] for spec in CUES[name][0]]
    return list(dict.fromkeys(SOURCES[s][0] for s in srcs))


def write_wav(x, path):
    subprocess.run(["ffmpeg", "-v", "error", "-nostdin", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    "-c:a", "pcm_f32le", path], input=x.astype(np.float32).tobytes(), check=True)


def decode_asset(name):
    import base64
    with open(os.path.join(ASSETS, name + ".ogg.b64"), "rb") as stream:
        data = base64.decodebytes(stream.read())
    proc = subprocess.run(["ffmpeg", "-v", "error", "-nostdin", "-i", "-", "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                          input=data, capture_output=True, check=True)
    return np.frombuffer(proc.stdout, dtype=np.float32).astype(np.float64)


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
            y = decode_asset(name)
            n = min(len(x), len(y))
            corr = float(np.dot(x[:n], y[:n]) / (np.linalg.norm(x[:n]) * np.linalg.norm(y[:n]) + 1e-15))
            ok = abs(len(x) - len(y)) <= 0.03 * SR and corr >= 0.95
            failed |= not ok
            print(f"{name}: {'ok' if ok else 'MISMATCH'} correlation {corr:.4f}, {len(y) / SR:.3f} s")
            continue
        wav = os.path.join(args.out, name + ".wav")
        write_wav(x, wav)
        print(f"{name}: {len(x) / SR:.3f} s, M100 {momentary_max(x):.1f} LUFS, TP {true_peak(x):.1f} dBTP -> {wav}")
        if not args.no_package:
            cmd = [sys.executable, HELPER, "package", "--input", wav, "--module", "core", "--name", "reinhard/" + name,
                   "--event", "reinhard_" + cue_class(name), "--replace"]
            for uuid in sources_of(name):
                cmd += ["--source-id", uuid]
            subprocess.run(cmd, cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
