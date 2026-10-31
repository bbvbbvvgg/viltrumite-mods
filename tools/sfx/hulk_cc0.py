#!/usr/bin/env python3
"""Avenger Hulk sounds (Captain America's «Avengers, assemble!»), mixed from CC0 Freesound recordings found via Openverse.

Fetch the sources first (Freesound preview MP3s land in ~/.cache/viltrumite-sfx/work/raw, never committed):

    python3 .agents/skills/sound-design/scripts/openverse_sfx.py download <openverse-uuid>

then, from the repository root:

    python3 tools/sfx/hulk_cc0.py            # mix every cue and package it (mono 44.1 kHz Vorbis, .ogg.b64)
    python3 tools/sfx/hulk_cc0.py --no-package

A layer takes `length` s of a source from `start`, plays it at varispeed `pitch` (0.6 = lower and slower),
band-limits it, applies `gain` dB and lands `delay` s into the cue. The sum is faded, optionally soft-saturated (`drive`, tanh) and peak-normalised
to `peak` dBFS. Every source is CC0 1.0; credits in tools/assets/captain_sources.md and CREDITS.md. Numbers are
measured, not auditioned.
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

SOURCES = {
    'roar_02': 'f52ac5d2-3bb8-4b27-99e9-5493698f0c76',      # "Monster Roar 02"
    'roar': '6018bfd1-81b5-4872-ba3f-eb11eb09479c',         # "Monster Roar"
    'roar_scary': 'e6152a5c-3cec-45eb-9347-24ac7235df16',   # "Scary Monster Roar #2" by NicknameLarry
    'body_hit': '2651c4ac-55bc-480b-86a1-c31dd3004f92',     # "body_hit.wav"
    'sand': 'e2b9cb0f-3e86-424d-a781-aead8f3e61ad',         # "Heavy sand impacts.wav"
}


def L(src, start, length, pitch=1.0, gain=0.0, delay=0.0, hp=None, lp=None, fade_out=0.05):
    return dict(src=src, start=start, length=length, pitch=pitch, gain=gain, delay=delay, hp=hp, lp=lp, fade_out=fade_out)


CUES = {
    # a big, low, two-voiced bellow
    'hulk_roar': dict(peak=-1.0, fade=0.5, layers=[
        L('roar_02', 0.36, 2.3, pitch=0.8, hp=45, lp=6000),
        L('roar', 0.0, 2.2, pitch=0.68, gain=-5.0, delay=0.06, hp=40, lp=3200, fade_out=0.4),
        L('roar_scary', 0.25, 1.8, pitch=0.62, gain=-9.0, delay=0.15, hp=40, lp=2000, fade_out=0.4),
    ]),
    # a heavy fist: deep body thump, a slap on top, a little grit
    'hulk_punch': dict(peak=-1.0, fade=0.12, drive=2.0, layers=[
        L('body_hit', 0.19, 0.3, pitch=0.55, hp=30, lp=2500),
        L('body_hit', 0.19, 0.2, pitch=0.95, gain=-6.0, hp=200),
        L('sand', 6.04, 0.35, pitch=0.7, gain=-8.0, delay=0.01, lp=4000),
    ]),
    # both fists into the ground: a low crunch, a second hit of debris and a rolling tail
    'hulk_smash': dict(peak=-0.8, fade=0.45, drive=3.5, layers=[
        L('sand', 6.03, 0.7, pitch=0.5, hp=25, lp=1600, fade_out=0.3),
        L('body_hit', 0.19, 0.35, pitch=0.42, gain=-2.0, hp=25, lp=900),
        L('sand', 9.64, 0.6, pitch=0.62, gain=-5.0, delay=0.12, lp=3000, fade_out=0.3),
        L('sand', 19.16, 0.8, pitch=0.55, gain=-8.0, delay=0.3, lp=1800, fade_out=0.4),
    ]),
    # two chest beats
    'hulk_chest': dict(peak=-1.5, fade=0.15, drive=3.0, layers=[
        L('body_hit', 0.19, 0.3, pitch=0.5, hp=30, lp=1100),
        L('body_hit', 0.19, 0.3, pitch=0.46, gain=-1.0, delay=0.34, hp=30, lp=1000),
    ]),
}


def raw_path(src):
    uuid = SOURCES[src]
    for ext in ('mp3', 'ogg', 'wav', 'flac'):
        p = os.path.join(RAW, f"{uuid}.{ext}")
        if os.path.exists(p):
            return p
    sys.exit(f"Missing source {src} ({uuid}); run: python3 {os.path.relpath(HELPER, ROOT)} download {uuid}")


def render(spec):
    chain = [f"atrim=start={spec['start']}:duration={spec['length']}", "asetpts=PTS-STARTPTS",
             f"asetrate={SR * spec['pitch']:.0f}", f"aresample={SR}"]
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
    x[:min(n, 64)] *= np.linspace(0.0, 1.0, min(n, 64))
    return x * 10 ** (spec['gain'] / 20.0)


def mix(name):
    cue = CUES[name]
    parts = [(int(l['delay'] * SR), render(l)) for l in cue['layers']]
    n = max(d + len(x) for d, x in parts)
    y = np.zeros(n)
    for d, x in parts:
        y[d:d + len(x)] += x
    f = int(cue['fade'] * SR)
    y[n - f:] *= np.linspace(1.0, 0.0, f) ** 1.5
    drive = cue.get('drive', 0.0)
    if drive > 0.0:
        # soft saturation: weight for the short hits without a limiter
        y /= max(1e-9, np.abs(y).max())
        y = np.tanh(drive * y) / np.tanh(drive)
    return y * (10 ** (cue['peak'] / 20.0) / max(1e-9, np.abs(y).max()))


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
    ap.add_argument('--out', default=os.path.join(tempfile.gettempdir(), 'hulk_cc0'))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    for name in ([a.only] if a.only else CUES):
        y = mix(name)
        wav = os.path.join(a.out, name + '.wav')
        write_wav(y, wav)
        rms = 20 * np.log10(np.sqrt((y ** 2).mean()) + 1e-12)
        print(f"{name}: {len(y) / SR:.2f} s, RMS {rms:.1f} dBFS -> {wav}")
        if not a.no_package:
            cmd = [sys.executable, HELPER, 'package', '--input', wav, '--module', 'core', '--name', 'avengers/' + name,
                   '--event', 'avengers_' + name, '--replace']
            for src in sorted({l['src'] for l in CUES[name]['layers']}):
                cmd += ['--source-id', SOURCES[src]]
            subprocess.run(cmd, cwd=ROOT, check=True, stdout=subprocess.DEVNULL)


if __name__ == '__main__':
    main()
