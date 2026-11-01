#!/usr/bin/env python3
"""The Red Hulk's sounds: original procedural synthesis (numpy only, no recordings, no film or game audio), CC0
(own work). Reuses the primitives of tools/sfx/jinwoo.py and tools/sfx/captain.py.

Usage: python3 tools/sfx/red_hulk.py [--wav-dir DIR] [--package] [--only name,name]
  --package  encodes each WAV (ffmpeg libvorbis, mono 44.1 kHz) into
             viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/red_hulk/<name>.ogg.b64; a file that would
             not fit one GitHub push request is split into <name>.ogg.b64.part0..N (joined by decodeBinaryAssets).
Deterministic: every recipe seeds its own RNG. Recipes per file: tools/assets/red_hulk_sources.md.
No recipe imitates speech: his lines are text subtitles only (HeroSpeech); the roars are non-verbal beast growls built
from a sawtooth buzz through moving formant bands plus noise, never words, never TTS. No vanilla Minecraft sound is used.
The heat palette: sub-bass impacts, fire crackle, furnace roar, steam hiss, a molten sizzle on every hot hit.
"""
import argparse
import base64
import glob
import os
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from jinwoo import (SR, ROOT, t_axis, rng, noise, env_adsr, exp_env, lowpass, highpass, sweep_band, sine, saw,  # noqa: E402
                    place, silence, master, fade, whoosh, thud, click, drone, formant_voice, write_wav)
from captain import reverb, boom  # noqa: E402

OUT = ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/red_hulk'
SPLIT = 45000


# ------------------------------------------------------------------ heat building blocks

def crackle(dur, density, seed=0, hp=1400, gain=0.5):
    """Fire crackle: sparse random clicks of random size."""
    y = silence(dur)
    r = rng(seed)
    n = int(dur * density)
    for _ in range(n):
        at = r.uniform(0, dur)
        place(y, click(0.03, int(r.integers(1e6)), hp * r.uniform(0.7, 1.6)) * r.uniform(0.2, 1.0) * gain, at)
    return y


def furnace(dur, seed=0, cutoff=700, flicker=7.0):
    """A furnace roar: brown noise through a low band with a slow flame flicker."""
    t = t_axis(dur)
    y = lowpass(noise(dur, seed, 'brown'), cutoff) + 0.4 * lowpass(noise(dur, seed + 1, 'pink'), cutoff * 2.5)
    wob = 1 + 0.35 * np.sin(2 * np.pi * flicker * t + rng(seed).random() * 6) * np.sin(2 * np.pi * flicker * 0.37 * t)
    return y * wob


def sizzle(dur, seed=0, decay=None):
    """Molten sizzle on a hot hit: bright noise with a fast tremolo."""
    t = t_axis(dur)
    y = highpass(noise(dur, seed), 3500) * (0.6 + 0.4 * np.sin(2 * np.pi * 43 * t))
    return y * exp_env(len(y), 0.004, decay or dur / 2.5)


def hiss(dur, seed=0, f0=2500, f1=6000, attack=0.05):
    """Steam: a band of white noise opening upward."""
    return sweep_band(noise(dur, seed), f0, f1, 1.2) * env_adsr(int(dur * SR), attack, 0.2, 0.7, dur * 0.4)


def sub(dur, f0, f1, decay, seed=0):
    """A sub-bass drop under every big impact."""
    return sine(lambda t: f1 + (f0 - f1) * np.exp(-t / (decay * 0.6)), dur) * exp_env(int(dur * SR), 0.003, decay)


def growl(dur, f0, vowels, seed, amount=1.0):
    """Non-verbal beast growl (no words): buzz through formants, rough amplitude, heavy noise breath."""
    y = formant_voice(dur, f0, vowels, seed, growl=amount)
    y += 0.5 * formant_voice(dur, lambda t: (f0(t) if callable(f0) else f0) * 1.49, vowels, seed + 1, growl=amount * 0.8)
    y += lowpass(noise(dur, seed + 2, 'brown'), 650) * 0.55
    return y


def punch_core(d, seed, f0=120, weight=1.0):
    y = thud(d, f0, f0 * 0.32, 0.11, seed) * 1.7 * weight
    y += click(d, seed + 1, 1300) * 0.7
    y += lowpass(noise(d, seed + 2, 'brown'), 700) * exp_env(int(d * SR), 0.002, 0.1) * 0.8
    y += sizzle(d, seed + 3, 0.18) * 0.18
    return y


# ------------------------------------------------------------------ recipes (28, one per RedHulkSounds event)

def swing():
    d = 0.45
    y = whoosh(d, 220, 1400, 1.8, seed=1000, attack=0.08, decay=0.16, color='pink') * 1.0
    y += lowpass(noise(d, 1001, 'brown'), 300) * exp_env(int(d * SR), 0.06, 0.12) * 0.5
    return master(y, 0.8, 1.3)


def swing_heavy():
    d = 0.7
    y = whoosh(d, 140, 900, 1.6, seed=1010, attack=0.18, decay=0.22, color='brown') * 1.2
    y += whoosh(d, 400, 2200, 2.0, seed=1011, attack=0.2, decay=0.14) * 0.4
    y += crackle(d, 30, 1012, gain=0.25)
    return master(reverb(y, 0.3, 0.15), 0.85, 1.4)


def punch():
    d = 0.6
    return master(reverb(punch_core(d, 1020), 0.35, 0.18), 0.9, 2.2)


def punch_heavy():
    d = 0.9
    y = punch_core(d, 1030, 95, 1.3) + sub(d, 70, 30, 0.3, 1031) * 0.9
    place(y, crackle(0.5, 50, 1032, gain=0.35), 0.03)
    return master(reverb(y, 0.5, 0.22), 0.93, 2.4)


def hammer():
    d = 1.6
    y = boom(d, 80, 24, 0.45, 1040) * 1.4 + sub(d, 55, 22, 0.6, 1041)
    place(y, crackle(1.0, 40, 1042, gain=0.35), 0.05)
    place(y, sizzle(0.8, 1043) * 0.25, 0.02)
    return master(reverb(y, 0.8, 0.3), 0.95, 2.5)


def grab():
    d = 0.5
    y = thud(d, 160, 70, 0.06, 1050) * 1.0 + lowpass(noise(d, 1051, 'pink'), 1500) * exp_env(int(d * SR), 0.005, 0.08) * 0.8
    return master(reverb(y, 0.25, 0.12), 0.82, 1.6)


def squeeze():
    d = 0.9
    t = t_axis(d)
    y = lowpass(noise(d, 1060, 'brown'), 400) * (0.6 + 0.4 * np.sin(2 * np.pi * 5 * t)) * env_adsr(int(d * SR), 0.1, 0.2, 0.7, 0.3)
    y += crackle(d, 25, 1061, 900, 0.4)
    y += sizzle(d, 1062, 0.6) * 0.15
    return master(y, 0.78, 1.5)


def throw():
    d = 0.8
    y = whoosh(d, 160, 1800, 1.5, seed=1070, attack=0.12, decay=0.3, color='pink') * 1.2
    y += growl(d, lambda t: 70 + 25 * np.sin(np.pi * t / d), 'ao', 1071, 0.8) * env_adsr(int(d * SR), 0.06, 0.2, 0.5, 0.3) * 0.4
    return master(reverb(y, 0.4, 0.2), 0.86, 1.6)


def throw_impact():
    d = 1.1
    y = boom(d, 100, 30, 0.3, 1080) * 1.2 + sub(d, 65, 28, 0.35, 1081) * 0.6
    return master(reverb(y, 0.6, 0.25), 0.92, 2.2)


def clap():
    d = 1.6
    y = click(d, 1090, 600) * 3.0 + thud(d, 220, 60, 0.05, 1091) * 1.6
    y += boom(d, 90, 30, 0.35, 1092, debris=False) * 1.0
    y += furnace(d, 1093, 1200) * exp_env(int(d * SR), 0.01, 0.4) * 0.9
    place(y, crackle(1.2, 60, 1094, gain=0.4), 0.02)
    return master(reverb(y, 1.0, 0.35), 0.95, 2.6)


def seismic():
    d = 2.2
    y = boom(d, 70, 20, 0.7, 1100) * 1.5 + sub(d, 48, 18, 0.9, 1101) * 1.2
    y += lowpass(noise(d, 1102, 'brown'), 250) * env_adsr(int(d * SR), 0.03, 0.5, 0.4, 1.2) * 0.9
    place(y, crackle(1.6, 35, 1103, 900, 0.5), 0.1)
    return master(reverb(y, 1.2, 0.35), 0.96, 2.6)


def leap_launch():
    d = 1.0
    y = boom(d, 90, 28, 0.25, 1110) * 1.1
    y += whoosh(d, 120, 1600, 1.4, seed=1111, attack=0.15, decay=0.45, color='brown') * 1.0
    return master(reverb(y, 0.5, 0.2), 0.9, 2.0)


def leap_land():
    d = 2.2
    y = boom(d, 75, 18, 0.65, 1120) * 1.6 + sub(d, 50, 16, 0.9, 1121) * 1.3
    place(y, crackle(1.5, 45, 1122, gain=0.45), 0.05)
    place(y, furnace(1.2, 1123, 500) * exp_env(int(1.2 * SR), 0.02, 0.4) * 0.5, 0.02)
    return master(reverb(y, 1.2, 0.35), 0.97, 2.7)


def blast_charge():
    d = 0.75
    t = t_axis(d)
    y = furnace(d, 1130, 900, 11.0) * env_adsr(int(d * SR), 0.6, 0.05, 1.0, 0.05) * 0.9
    y += drone([55, 82.5], d, 1131, 600) * (t / d) ** 2 * 0.4
    y += sweep_band(noise(d, 1132), 300, 2500, 2.0, 2.0) * (t / d) ** 2 * 0.5
    return master(y, 0.84, 1.5)


def blast_fire():
    d = 1.1
    t = t_axis(d)
    y = furnace(d, 1140, 1800, 13.0) * env_adsr(int(d * SR), 0.01, 0.1, 0.9, 0.35) * 1.2
    y += drone([65, 97.5, 130], d, 1141, 1400) * env_adsr(int(d * SR), 0.01, 0.1, 0.8, 0.35) * 0.35
    y += crackle(d, 70, 1142, gain=0.4)
    y *= 1 + 0.15 * np.sin(2 * np.pi * 10 * t)
    return master(reverb(y, 0.4, 0.2), 0.9, 1.8)


def fury_roar():
    d = 2.2
    y = growl(d, lambda t: 58 + 34 * np.sin(np.pi * t / d), 'uaaao', 1150, 1.0) * 1.0
    y += furnace(d, 1151, 600) * env_adsr(int(d * SR), 0.2, 0.3, 0.7, 0.8) * 0.5
    y += sub(d, 60, 35, 1.2, 1152) * 0.4
    y *= env_adsr(len(y), 0.12, 0.3, 0.85, 0.7)
    return master(reverb(y, 0.9, 0.3), 0.93, 2.1)


def overheat():
    d = 1.8
    y = hiss(d, 1160, 1800, 7000, 0.02) * 1.1
    y += boom(d, 110, 40, 0.18, 1161, debris=False) * 0.8
    y += crackle(d, 50, 1162, gain=0.35)
    t = t_axis(d)
    y += sine(lambda t: 900 - 500 * t / d, d) * exp_env(int(d * SR), 0.01, 0.6) * 0.08  # metal groan of the strained body
    return master(reverb(y, 0.6, 0.25), 0.9, 1.6)


def vent():
    d = 1.8
    y = hiss(d, 1170, 1200, 5000, 0.01) * 1.3
    y += lowpass(noise(d, 1171, 'brown'), 300) * exp_env(int(d * SR), 0.01, 0.4) * 0.6
    return master(reverb(y, 0.7, 0.3), 0.88, 1.4)


def ult_charge():
    d = 3.0
    t = t_axis(d)
    rise = (t / d) ** 1.6
    y = furnace(d, 1180, 500 + 1500 * 0.5, 6.0) * (0.3 + 0.9 * rise)
    y += drone([41, 61.5, 82], d, 1181, 300 + 900 * 0.5) * rise * 0.5
    y += sweep_band(noise(d, 1182), 120, 3000, 2.2, 1.8) * rise * 0.6
    y += crackle(d, 40, 1183, gain=0.35) * rise
    hb = silence(d)
    at, beat = 0.1, 0.62
    while at < d:
        place(hb, thud(0.3, 75, 38, 0.08, int(at * 100) + 1184), at, 0.9)
        at += beat
        beat = max(0.22, beat * 0.86)
    y += hb * 0.9
    return master(y, 0.9, 1.6)


def ult_release():
    d = 3.2
    y = boom(d, 60, 14, 1.0, 1190) * 1.8 + sub(d, 45, 14, 1.4, 1191) * 1.6
    y += furnace(d, 1192, 1400) * env_adsr(int(d * SR), 0.005, 0.4, 0.4, 2.0) * 1.0
    place(y, crackle(2.6, 60, 1193, 900, 0.55), 0.05)
    place(y, hiss(2.0, 1194, 2000, 7000, 0.3) * 0.35, 0.8)
    return master(reverb(y, 1.6, 0.4), 0.98, 2.8)


def ult_recover():
    d = 2.5
    t = t_axis(d)
    y = hiss(d, 1200, 3000, 1500, 0.2) * 0.8
    breath = silence(d)
    for i, at in enumerate((0.15, 1.1, 1.9)):
        b = lowpass(noise(0.6, 1201 + i, 'pink'), 900) * env_adsr(int(0.6 * SR), 0.15, 0.1, 0.6, 0.3)
        place(breath, b, at, 0.7 - 0.15 * i)
    y += breath
    y += crackle(d, 12, 1205, gain=0.3) * np.exp(-t / 1.2)
    return master(y, 0.78, 1.3)


def landing():
    d = 0.9
    y = thud(d, 90, 30, 0.16, 1210) * 1.6 + lowpass(noise(d, 1211, 'brown'), 400) * exp_env(int(d * SR), 0.003, 0.2) * 0.8
    return master(reverb(y, 0.4, 0.2), 0.9, 2.0)


def footstep():
    d = 0.4
    y = thud(d, 80, 34, 0.07, 1220) * 1.5 + lowpass(click(d, 1221, 300), 1200) * 0.5
    return master(y, 0.75, 1.8)


def heat_up():
    d = 0.9
    t = t_axis(d)
    y = furnace(d, 1230, 1100, 9.0) * env_adsr(int(d * SR), 0.05, 0.2, 0.6, 0.4) * 0.8
    y += sine(lambda t: 90 + 60 * t / d, d) * env_adsr(int(d * SR), 0.05, 0.2, 0.5, 0.4) * 0.3
    y += crackle(d, 40, 1231, gain=0.35)
    return master(y, 0.8, 1.4)


def heat_hum():
    """Seamless 2 s loop: the cycle-exact drone and a periodic noise so the loop point never clicks."""
    d = 2.0
    n = int(d * SR)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for f, a in ((45, 1.0), (90, 0.5), (135, 0.25), (180, 0.12)):
        y += a * np.sin(2 * np.pi * f * t)
    y *= 0.8 + 0.2 * np.sin(2 * np.pi * 1.5 * t)
    spec = np.fft.rfft(rng(1240).standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= 1.0 / (1.0 + (f / 500.0) ** 2)
    y += np.fft.irfft(spec, n) / 20.0 * 0.6
    y = y / (np.max(np.abs(y)) + 1e-9) * 0.7
    return y


def refuse():
    d = 0.5
    y = hiss(d, 1250, 3000, 2000, 0.01) * 0.6 + thud(d, 140, 70, 0.05, 1251) * 0.8
    return master(y, 0.7, 1.3)


def enter():
    d = 2.4
    y = growl(d, lambda t: 52 + 30 * np.sin(np.pi * t / d), 'uaao', 1260, 0.9) * env_adsr(int(d * SR), 0.5, 0.3, 0.8, 0.7) * 0.8
    place(y, boom(1.6, 85, 22, 0.5, 1261) * 1.4, 0.55)
    place(y, crackle(1.6, 40, 1262, gain=0.4), 0.6)
    return master(reverb(y, 1.0, 0.32), 0.94, 2.2)


def hurt():
    d = 0.6
    y = growl(d, lambda t: 85 - 25 * t / d, 'ao', 1270, 0.9) * env_adsr(int(d * SR), 0.02, 0.1, 0.6, 0.3) * 0.9
    y += thud(d, 120, 60, 0.05, 1271) * 0.6
    return master(y, 0.82, 1.7)


RECIPES = {
    'swing': swing, 'swing_heavy': swing_heavy, 'punch': punch, 'punch_heavy': punch_heavy, 'hammer': hammer, 'grab': grab,
    'squeeze': squeeze, 'throw': throw, 'throw_impact': throw_impact, 'clap': clap, 'seismic': seismic, 'leap_launch': leap_launch,
    'leap_land': leap_land, 'blast_charge': blast_charge, 'blast_fire': blast_fire, 'fury_roar': fury_roar, 'overheat': overheat,
    'vent': vent, 'ult_charge': ult_charge, 'ult_release': ult_release, 'ult_recover': ult_recover, 'landing': landing,
    'footstep': footstep, 'heat_up': heat_up, 'heat_hum': heat_hum, 'refuse': refuse, 'enter': enter, 'hurt': hurt,
}


def package(wav, name):
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='red-hulk-ogg-') as tmp:
        ogg = Path(tmp) / 'out.ogg'
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-nostdin', '-y', '-i', str(wav), '-ac', '1', '-ar', '44100',
                        '-c:a', 'libvorbis', '-q:a', '4', '-f', 'ogg', str(ogg)], check=True)
        text = base64.encodebytes(ogg.read_bytes()).decode('ascii')
    path = OUT / f'{name}.ogg.b64'
    for old in glob.glob(str(path) + '.part*'):
        os.remove(old)
    if path.exists():
        path.unlink()
    if len(text) <= SPLIT:
        path.write_text(text, encoding='ascii')
        return 1
    parts, cur = [], ''
    for line in text.splitlines(keepends=True):
        if len(cur) + len(line) > SPLIT:
            parts.append(cur)
            cur = ''
        cur += line
    parts.append(cur)
    for i, p in enumerate(parts):
        Path(f'{path}.part{i}').write_text(p, encoding='ascii')
    return len(parts)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--wav-dir', type=Path)
    ap.add_argument('--package', action='store_true')
    ap.add_argument('--only')
    a = ap.parse_args(argv)
    names = a.only.split(',') if a.only else list(RECIPES)
    tmp = tempfile.TemporaryDirectory(prefix='red-hulk-sfx-')
    out = a.wav_dir or Path(tmp.name)
    out.mkdir(parents=True, exist_ok=True)
    for name in names:
        x = RECIPES[name]()
        p = out / f'{name}.wav'
        write_wav(p, x)
        info = f'{name}: {len(x) / SR:.2f}s peak {np.max(np.abs(x)):.2f} rms {np.sqrt(np.mean(x ** 2)):.3f}'
        if a.package:
            info += f' parts {package(p, name)}'
        print(info, flush=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
