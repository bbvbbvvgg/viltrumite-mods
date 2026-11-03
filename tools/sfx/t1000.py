#!/usr/bin/env python3
"""T-1000 (liquid-metal infiltrator) sounds, layered from CC0 Freesound recordings found via Openverse.

Fetch the sources first (Freesound preview MP3s land in ~/.cache/viltrumite-sfx/work/raw, never committed):

    python3 .agents/skills/sound-design/scripts/openverse_sfx.py download <openverse-uuid>   # every uuid in SOURCES

then, from the repository root:

    python3 tools/sfx/t1000.py                # mix every variant and package it (mono 44.1 kHz Vorbis, .ogg.b64)
    python3 tools/sfx/t1000.py --no-package   # only write the WAV mixes to /tmp/t1000_sfx
    python3 tools/sfx/t1000.py --only clang

Every event `t1000.<cue>` gets the variants `t1000/<cue>_<n>` (one event, several entries). A layer takes `length` s
of a source from `start`, optionally reversed, plays it at varispeed `pitch` (0.6 = lower and slower), band-limits it,
applies `gain` dB and lands `delay` s into the variant. The sum is faded and level-matched: the loudest 100 ms window
(RMS) goes to the cue's `level` dBFS under a -1 dBFS sample-peak ceiling (a look-ahead limiter takes at most 9 dB off
spiky transients), so the variants of one event play equally loud. Layer lengths are source seconds: the output
lasts `length / pitch`. Every source is CC0 1.0; credits in tools/assets/t1000_sources.md and CREDITS.md. Levels are measured; the
recordings were chosen by their metadata and envelope analysis, not by a listening test.
"""
import argparse
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HELPER = os.path.join(ROOT, ".agents", "skills", "sound-design", "scripts", "openverse_sfx.py")
RAW = os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx", "work", "raw")
SR = 44100
CEILING = -1.0
MAX_LIMIT = 9.0

# name: (Openverse UUID, creator, Freesound title)
SOURCES = {
    'slime13': ('0ef058e3-eb33-4322-9264-268ccdf82bd5', 'Archos', 'Slime 13.wav'),
    'slime16': ('c5d7acf0-4afe-4855-bfa8-b9f12a4fc1db', 'Archos', 'Slime 16.wav'),
    'slime19': ('9baaff7a-0d90-4acb-8193-aaa14ad6a4cc', 'Archos', 'Slime 19.wav'),
    'slime27': ('78c1dc77-86aa-4427-b723-f9dd1be1ec1b', 'Archos', 'Slime 27.wav'),
    'slug': ('a11fb938-8457-4f17-aa86-90c85e454356', 'Breviceps', 'Step on a slug (Splat!) 2'),
    'oobleck': ('07847f2f-a7c3-4707-a210-b9757b37b5eb', 'AlaskaRobotics', 'squishy oobleck goo'),
    'muddy': ('c0e232b4-7bb2-4d3e-8735-05e53e593142', 'Breviceps', 'Muddy Boots'),
    'jello': ('bf88a6c7-6b42-469f-b510-1f75ebe50fbd', 'Fugeni', 'Jello Squish'),
    'clang': ('6afade61-ff10-493f-b420-48978c4b237c', 'kermite607', 'Metal Clang sound'),
    'clang_long': ('45c01a17-af00-4c1f-93d1-2ca594cceacb', 'tomwilkinson', 'Metallic clang'),
    'hollow': ('5f77a8d3-2522-4808-b70c-73f912bcd34d', 'craigsmith', 'R15-07-Metal Clangs and Hollow Hits.wav'),
    'scrape1': ('5b9afc06-6776-4bd0-a758-e5edf2556d32', 'BehanSean', 'Metal scrape 1.wav'),
    'scrape_s2': ('d19c4cf1-d402-4dc1-a6e3-5a5b473609d6', 'newagesoup', 'small-metal-scrape-02.wav'),
    'scrape_s4': ('77193be1-ff92-4cb9-b190-636099fad3f3', 'newagesoup', 'small-metal-scrape-04.wav'),
    'slide': ('2204ba42-c96d-4ff3-9b6b-c7fa0f45e096', 'tyballer92', 'Metallic Scrape Slide'),
    'metal': ('a1369abe-d98f-457c-a22f-1f8e5dd9db5c', 'sentryx86', 'Metal Scraping Metal.wav'),
    'ice1': ('ea33aaae-0977-44bd-b455-ab73205da0c2', 'j_p_higgins', 'Ice Crack 1'),
    'ice2': ('3727718c-564f-43b2-8995-d4695a69d762', 'j_p_higgins', 'Ice Crack 2'),
    'ice5': ('d0d5da51-a072-45ae-9e30-4b85a4404bcb', 'ecfike', 'Ice Crack 5.wav'),
    'ice9': ('f0e06f5e-62d8-4c80-91c1-6b92b60f4b73', 'ecfike', 'Ice Crack 9.wav'),
    'swoosh': ('f1dc6045-7965-41de-87f8-4731d2583fd2', 'qubodup', 'Swosh swoosh whoosh air sound, free, high quality'),
    'swoosh15': ('7843f159-6886-426c-9d36-85f0d8d775f9', 'qubodup', 'Swoosh 15 Windy'),
    'swoosh09': ('a8e87f60-537e-4982-ba57-5b755dfbc77e', 'qubodup', 'swoosh wind 09.flac'),
    'swoosh3': ('d81d654b-d66e-44a8-98a3-3b97cadd3f8e', 'D4XX', 'Swoosh 3'),
    'swoosh1': ('2068f6d9-dd45-47a7-9556-8a2b033fe683', 'D4XX', 'Swoosh 1'),
    'radiator': ('7b350c80-e10d-48e2-82cb-9364f0c969cf', 'flood-mix', 'RADIATOR - Metallic clanging, water spouting'),
}


def L(src, start, length, pitch=1.0, gain=0.0, delay=0.0, hp=None, lp=None, fade_in=0.0, fade_out=0.05, rev=False):
    return dict(src=src, start=start, length=length, pitch=pitch, gain=gain, delay=delay, hp=hp, lp=lp,
                fade_in=fade_in, fade_out=fade_out, rev=rev)


def V(*layers, fade=0.08, drive=0.0):
    return dict(layers=list(layers), fade=fade, drive=drive)


CUES = {
    # liquid metal gliding while it walks or crawls: thick wet squelches, low and soft
    'flow': dict(level=-20.0, variants=[
        V(L('jello', 0.85, 0.55, pitch=0.62, lp=2600, fade_in=0.08, fade_out=0.3),
          L('oobleck', 0.30, 0.5, pitch=0.75, gain=-6.0, lp=3000, delay=0.08, fade_in=0.05, fade_out=0.25), fade=0.25),
        V(L('jello', 3.35, 0.55, pitch=0.58, lp=2400, fade_in=0.08, fade_out=0.3),
          L('slime19', 0.50, 0.5, pitch=0.7, gain=-5.0, lp=3000, delay=0.05, fade_in=0.05, fade_out=0.25), fade=0.25),
        V(L('oobleck', 1.15, 0.55, pitch=0.66, lp=2600, fade_in=0.08, fade_out=0.3),
          L('jello', 2.18, 0.5, pitch=0.6, gain=-4.0, lp=2400, delay=0.1, fade_in=0.05, fade_out=0.25), fade=0.25),
    ]),
    # a footstep of the metal body: a squelchy step with a dull metallic weight under it
    'step': dict(level=-16.0, variants=[
        V(L('muddy', 0.07, 0.33, pitch=0.8, lp=5000), L('hollow', 0.10, 0.3, pitch=0.55, gain=-8.0, lp=1500)),
        V(L('muddy', 0.44, 0.25, pitch=0.78, lp=5000), L('hollow', 0.67, 0.3, pitch=0.6, gain=-8.0, lp=1500)),
        V(L('slug', 0.03, 0.3, pitch=0.7, lp=3500), L('hollow', 1.14, 0.3, pitch=0.6, gain=-9.0, lp=1500)),
    ]),
    # metal pulled long (the hook shooting out, the body stretching): a strained scrape over a wet pull
    'stretch': dict(level=-16.0, variants=[
        V(L('scrape_s2', 0.03, 0.65, pitch=0.8, hp=300, fade_out=0.2),
          L('slime16', 0.35, 0.6, pitch=0.7, gain=-4.0, lp=4000, fade_out=0.2), fade=0.15),
        V(L('scrape_s4', 0.12, 0.58, pitch=0.75, hp=300, fade_out=0.2),
          L('oobleck', 0.60, 0.6, pitch=0.8, gain=-5.0, lp=4000, fade_out=0.2), fade=0.15),
    ]),
    # a hand flowing into a blade: a squelch, then the metallic «shing» of the edge forming
    'morph': dict(level=-15.0, variants=[
        V(L('slug', 0.03, 0.55, pitch=0.65, lp=4000, fade_out=0.15),
          L('scrape1', 0.34, 0.6, pitch=1.0, gain=-3.0, delay=0.12, hp=800, fade_out=0.25),
          L('metal', 0.04, 0.45, pitch=1.2, gain=-14.0, delay=0.2, hp=2500, fade_out=0.2), fade=0.2),
        V(L('slime13', 0.08, 0.6, pitch=0.7, lp=4000, fade_out=0.15),
          L('slide', 0.22, 0.65, pitch=1.15, gain=-2.0, delay=0.1, hp=600, fade_out=0.25),
          L('metal', 1.26, 0.4, pitch=1.3, gain=-15.0, delay=0.22, hp=2500, fade_out=0.2), fade=0.2),
    ]),
    # a blade swing: air with a thin metallic sheen
    'swish': dict(level=-14.0, variants=[
        V(L('swoosh', 0.0, 0.47, pitch=1.1), L('metal', 0.04, 0.25, pitch=1.4, gain=-17.0, hp=3000), fade=0.1),
        V(L('swoosh15', 0.02, 0.38, pitch=1.05), L('metal', 0.21, 0.25, pitch=1.5, gain=-17.0, hp=3000), fade=0.1),
        V(L('swoosh3', 0.05, 0.48, pitch=1.15), L('metal', 2.33, 0.25, pitch=1.4, gain=-17.0, hp=3000), fade=0.1),
    ]),
    # the blade striking a body: a wet hit with a metallic tick and a puff of air
    'blade_hit': dict(level=-12.0, variants=[
        V(L('slug', 0.03, 0.32, pitch=0.9, lp=6000), L('clang', 0.03, 0.17, pitch=1.6, gain=-18.0, hp=2500),
          L('swoosh09', 0.0, 0.17, pitch=1.3, gain=-10.0)),
        V(L('slime27', 0.07, 0.38, pitch=0.75, lp=6000), L('hollow', 2.35, 0.25, pitch=1.3, gain=-12.0, hp=400),
          L('swoosh1', 0.03, 0.2, pitch=1.4, gain=-12.0)),
        V(L('muddy', 0.07, 0.33, pitch=1.1, lp=6000), L('clang', 0.03, 0.17, pitch=1.9, gain=-20.0, hp=2500),
          L('swoosh09', 0.0, 0.17, pitch=1.2, gain=-11.0)),
    ]),
    # blade against blade or armour: a ringing clang
    'clang': dict(level=-13.0, variants=[
        V(L('clang_long', 0.66, 0.95, pitch=1.0, fade_out=0.5), L('clang', 0.03, 0.5, pitch=0.9, gain=-8.0, lp=9000, fade_out=0.3),
          fade=0.4),
        V(L('clang_long', 0.66, 0.95, pitch=1.15, fade_out=0.5), L('hollow', 3.12, 0.45, pitch=1.2, gain=-6.0, fade_out=0.3),
          fade=0.4),
    ]),
    # a shot or a blow sinking into the liquid metal: a thick plunk with a dull metal knock
    'impact': dict(level=-12.0, variants=[
        V(L('slime27', 0.07, 0.38, pitch=0.55, lp=2500), L('hollow', 0.10, 0.35, pitch=0.7, gain=-6.0, lp=2500),
          L('slug', 0.03, 0.3, pitch=0.5, gain=-6.0, lp=2500)),
        V(L('slug', 0.36, 0.18, pitch=0.55, lp=2500), L('hollow', 1.37, 0.35, pitch=0.72, gain=-6.0, lp=2500),
          L('slime13', 0.08, 0.17, pitch=0.5, gain=-6.0, lp=2500)),
        V(L('muddy', 0.07, 0.35, pitch=0.6, lp=2500), L('hollow', 2.84, 0.35, pitch=0.68, gain=-6.0, lp=2500),
          L('slime27', 0.07, 0.3, pitch=0.5, gain=-7.0, lp=2500)),
    ]),
    # a heavy blow denting the whole body: a deep squelch with a metal groan
    'deform': dict(level=-12.0, variants=[
        V(L('slug', 0.03, 0.33, pitch=0.45, lp=1800, fade_out=0.2), L('hollow', 1.85, 0.45, pitch=0.5, gain=-3.0, lp=1200, fade_out=0.2),
          L('slide', 0.22, 0.6, pitch=0.55, gain=-10.0, delay=0.08, fade_out=0.3), fade=0.25),
        V(L('slime27', 0.07, 0.5, pitch=0.42, lp=1800, fade_out=0.2), L('hollow', 3.76, 0.42, pitch=0.5, gain=-3.0, lp=1200, fade_out=0.2),
          L('scrape_s2', 0.03, 0.6, pitch=0.5, gain=-11.0, delay=0.08, lp=3000, fade_out=0.3), fade=0.25),
    ]),
    # wounds closing: a quiet wet suck backwards into the body
    'regen': dict(level=-19.0, variants=[
        V(L('slime16', 0.35, 0.6, pitch=1.0, rev=True, fade_in=0.15, fade_out=0.04),
          L('slime13', 0.25, 0.35, pitch=1.2, gain=-5.0, delay=0.28, fade_out=0.15), fade=0.1),
        V(L('slime19', 0.50, 0.55, pitch=1.05, rev=True, fade_in=0.15, fade_out=0.04),
          L('slime27', 0.07, 0.3, pitch=1.25, gain=-6.0, delay=0.3, fade_out=0.15), fade=0.1),
    ]),
    # chrome spreading over the skin (also the concealment, played lower): a rising metallic shimmer over liquid
    'reveal': dict(level=-16.0, variants=[
        V(L('metal', 1.25, 1.2, pitch=0.8, hp=1500, lp=9000, fade_in=0.45, fade_out=0.35),
          L('oobleck', 0.60, 1.3, pitch=0.7, gain=-5.0, lp=3500, fade_in=0.2, fade_out=0.4),
          L('scrape_s2', 0.03, 0.9, pitch=0.9, gain=-9.0, rev=True, delay=0.35, hp=500, fade_in=0.3, fade_out=0.05), fade=0.3),
    ]),
    # collapsing into a puddle (and the death splash): a splattering slump with a little metal in the water
    'splash': dict(level=-13.0, variants=[
        V(L('radiator', 0.90, 0.7, pitch=0.8, lp=6000, fade_out=0.3), L('slug', 0.03, 0.6, pitch=0.6, gain=-2.0, lp=4000),
          L('slime19', 0.98, 0.5, pitch=0.7, gain=-6.0, delay=0.1, fade_out=0.2), fade=0.25),
        V(L('radiator', 3.00, 0.7, pitch=0.8, lp=6000, fade_out=0.3), L('slime27', 0.07, 0.5, pitch=0.6, gain=-2.0, lp=4000),
          L('oobleck', 0.98, 0.5, pitch=0.7, gain=-6.0, delay=0.1, fade_out=0.2), fade=0.25),
    ]),
    # rising out of the puddle: the splash backwards, gathering squelches and a final settling knock
    'reassemble': dict(level=-15.0, variants=[
        V(L('slug', 0.03, 0.8, pitch=0.6, rev=True, lp=4000, fade_in=0.3, fade_out=0.03),
          L('oobleck', 0.00, 1.4, pitch=0.65, gain=-5.0, lp=3500, fade_in=0.2, fade_out=0.3),
          L('slide', 0.22, 1.2, pitch=0.7, gain=-9.0, rev=True, delay=0.2, fade_in=0.3, fade_out=0.05),
          L('slime16', 0.35, 0.5, pitch=0.8, gain=-3.0, delay=1.1, fade_out=0.2),
          L('hollow', 0.10, 0.4, pitch=0.6, gain=-10.0, delay=1.3, lp=1500), fade=0.3),
    ]),
    # frozen metal cracking
    'crack': dict(level=-17.0, variants=[
        V(L('ice1', 0.12, 0.6, pitch=0.85, fade_out=0.25), L('ice2', 0.38, 0.5, pitch=0.9, gain=-4.0, delay=0.08, fade_out=0.2),
          fade=0.2),
        V(L('ice9', 0.30, 0.65, pitch=0.85, fade_out=0.25), L('clang', 0.03, 0.4, pitch=2.2, gain=-24.0, hp=3000, fade_out=0.2),
          fade=0.2),
    ]),
    # frozen metal shattering: a burst of ice, a bright metal ring and a shower of fragments
    'shatter': dict(level=-11.0, variants=[
        V(L('ice5', 0.18, 1.4, pitch=0.9, fade_out=0.5), L('ice9', 0.30, 1.1, pitch=1.0, gain=-2.0, fade_out=0.4),
          L('clang', 0.03, 0.8, pitch=1.3, gain=-9.0, lp=9000, fade_out=0.4),
          L('hollow', 0.10, 0.5, pitch=1.4, gain=-8.0, fade_out=0.2),
          L('metal', 0.04, 1.0, pitch=1.5, gain=-15.0, delay=0.15, hp=4000, fade_out=0.4), fade=0.5),
    ]),
    # the final destruction: bubbling, groaning throes that sink into a fading slosh
    'destroy': dict(level=-13.0, variants=[
        V(L('jello', 5.0, 2.1, pitch=0.5, lp=2000, fade_in=0.1, fade_out=1.4),
          L('slide', 0.20, 0.7, pitch=0.45, gain=-4.0, delay=0.2, fade_out=0.5),
          L('metal', 1.20, 1.1, pitch=0.5, gain=-9.0, delay=0.4, lp=3000, fade_in=0.3, fade_out=0.8),
          L('hollow', 4.20, 0.4, pitch=0.45, gain=-6.0, delay=0.8, lp=1500, fade_out=0.3),
          L('oobleck', 0.00, 1.2, pitch=0.5, gain=-6.0, delay=1.5, lp=2500, fade_out=1.0), fade=1.4),
    ]),
}


def raw_path(src):
    uuid = SOURCES[src][0]
    for ext in ('mp3', 'ogg', 'wav', 'flac'):
        p = os.path.join(RAW, f"{uuid}.{ext}")
        if os.path.exists(p):
            return p
    sys.exit(f"Missing source {src} ({uuid}); run: python3 {os.path.relpath(HELPER, ROOT)} download {uuid}")


def render(spec):
    chain = [f"atrim=start={spec['start']}:duration={spec['length']}", "asetpts=PTS-STARTPTS"]
    if spec['rev']:
        chain.append("areverse")
    chain += [f"asetrate={SR * spec['pitch']:.0f}", f"aresample={SR}"]
    if spec['hp']:
        chain.append(f"highpass=f={spec['hp']}")
    if spec['lp']:
        chain.append(f"lowpass=f={spec['lp']}")
    b = subprocess.run(['ffmpeg', '-v', 'error', '-i', raw_path(spec['src']), '-ac', '1', '-ar', str(SR), '-af', ','.join(chain),
                        '-f', 'f32le', '-'], capture_output=True, check=True).stdout
    x = np.frombuffer(b, np.float32).astype(np.float64)
    n = len(x)
    f = min(n, int(spec['fade_out'] * SR))
    if f > 0:
        x[n - f:] *= np.linspace(1.0, 0.0, f)
    fi = min(n, max(64, int(spec['fade_in'] * SR)))
    x[:fi] *= np.linspace(0.0, 1.0, fi)
    return x * 10 ** (spec['gain'] / 20.0)


def loudness(y):
    """Loudest 100 ms RMS window (dBFS)."""
    w = int(0.1 * SR)
    if len(y) <= w:
        return 20 * np.log10(np.sqrt((y ** 2).mean()) + 1e-12)
    c = np.concatenate([[0.0], np.cumsum(y ** 2)])
    rms = np.sqrt((c[w:] - c[:-w]) / w)
    return 20 * np.log10(rms.max() + 1e-12)


def mix(cue, variant):
    parts = [(int(l['delay'] * SR), render(l)) for l in variant['layers']]
    n = max(d + len(x) for d, x in parts)
    y = np.zeros(n)
    for d, x in parts:
        y[d:d + len(x)] += x
    # trim leading silence (keep 5 ms before the first sample above -50 dBFS)
    thr = np.abs(y).max() * 10 ** (-50 / 20.0)
    first = int(np.argmax(np.abs(y) > thr))
    y = y[max(0, first - int(0.005 * SR)):]
    n = len(y)
    f = min(n, int(variant['fade'] * SR))
    y[n - f:] *= np.linspace(1.0, 0.0, f) ** 1.5
    if variant['drive'] > 0.0:
        y /= max(1e-9, np.abs(y).max())
        y = np.tanh(variant['drive'] * y) / np.tanh(variant['drive'])
    # level-match: bring the loudest 100 ms to the cue level, let a look-ahead limiter take at most
    # MAX_LIMIT dB off the transients above the ceiling, and settle for less level when a take is too spiky
    for _ in range(3):
        y = y * 10 ** ((cue['level'] - loudness(y)) / 20.0)
        peak = 20 * np.log10(np.abs(y).max() + 1e-12)
        if peak > CEILING + MAX_LIMIT:
            y = y * 10 ** ((CEILING + MAX_LIMIT - peak) / 20.0)
        y = limit(y)
    return y


def limit(y):
    """Look-ahead peak limiter to the ceiling: 2 ms look-ahead, 60 ms release."""
    c = 10 ** (CEILING / 20.0) * 0.999
    g = np.minimum(1.0, c / np.maximum(np.abs(y), 1e-12))
    la = int(0.002 * SR)
    pad = np.concatenate([g, np.ones(la)])
    g = np.lib.stride_tricks.sliding_window_view(pad, la + 1).min(axis=1)[:len(y)]
    a = 1.0 - np.exp(-1.0 / (0.06 * SR))
    out = np.empty_like(g)
    cur = 1.0
    for i in range(len(g)):
        cur = min(g[i], cur + (1.0 - cur) * a)
        out[i] = cur
    # a short smoothing of the attack so the gain never steps
    k = np.hanning(la * 2 + 1)
    k /= k.sum()
    out = np.minimum(out, np.convolve(out, k, mode='same'))
    return np.clip(y * out, -c, c)


def write_wav(y, path):
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(y, -1, 1) * 32767).astype('<i2').tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only')
    ap.add_argument('--no-package', action='store_true')
    ap.add_argument('--out', default=os.path.join(tempfile.gettempdir(), 't1000_sfx'))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    for name in ([a.only] if a.only else CUES):
        cue = CUES[name]
        for k, variant in enumerate(cue['variants'], 1):
            y = mix(cue, variant)
            wav = os.path.join(a.out, f"{name}_{k}.wav")
            write_wav(y, wav)
            peak = 20 * np.log10(np.abs(y).max() + 1e-12)
            print(f"{name}_{k}: {len(y) / SR:.2f} s, loud {loudness(y):.1f} dBFS, peak {peak:.1f} dBFS -> {wav}")
            if not a.no_package:
                cmd = [sys.executable, HELPER, 'package', '--input', wav, '--module', 'core', '--name', f"t1000/{name}_{k}",
                       '--event', f"t1000_{name}", '--replace']
                for src in sorted({l['src'] for l in variant['layers']}):
                    cmd += ['--source-id', SOURCES[src][0]]
                subprocess.run(cmd, cwd=ROOT, check=True, stdout=subprocess.DEVNULL)


if __name__ == '__main__':
    main()
