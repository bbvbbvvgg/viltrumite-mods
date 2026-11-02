#!/usr/bin/env python3
"""Venom's sounds: real CC0 1.0 recordings from Freesound, cut, layered, pitched and levelled here. No synthesis
stands in for a recording, no vanilla Minecraft sound, no film or game audio. Every source row below was checked on
its Freesound landing page (licence link creativecommons.org/publicdomain/zero/1.0, author, title); the table, the
proof and the use of every file are in docs/venom/audio-sources.md (shipped in the jar as
META-INF/credits/venom-audio.md).

Usage:
  python3 tools/sfx/venom.py --fetch      download the hq MP3 previews and landing pages into the cache (never committed)
  python3 tools/sfx/venom.py --verify     re-read every cached landing page: CC0 1.0 licence, author, title
  python3 tools/sfx/venom.py --package    render every event and write
        viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/venom/<file>.ogg.b64 and
        viltrumitecore/src/main/sound-overlays/venom.json
  python3 tools/sfx/venom.py --docs       rewrite docs/venom/audio-sources.md (sources, licence proof, files)
Cache: $VENOM_SFX_CACHE (default ~/.cache/viltrumite-sfx/venom): raw/fs_<id>.mp3, pages/<id>.html.
The symbiote palette: wet slime and gel squelches, a pitched-down beast growl and roar, whooshes and whip cracks for
the tendrils, real fabric tears for the Spider's webs, a real stethoscope heartbeat, breathing and reversed whispers
for the host's head. Not auditioned by a person: levels are measured (loudest 100 ms RMS per class, peak <= -1 dBFS).
"""
import argparse
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
SR = 44100
OUT = ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/venom'
OVERLAY = ROOT / 'viltrumitecore/src/main/sound-overlays/venom.json'
DOC = ROOT / 'docs/venom/audio-sources.md'
CACHE = Path(os.environ.get('VENOM_SFX_CACHE', Path.home() / '.cache/viltrumite-sfx/venom'))
CHECKED = '2026-11-02'
LICENCE = 'creativecommons.org/publicdomain/zero/1.0'

# key -> (Freesound id, author (Freesound user), title as on the landing page, preview path under cdn.freesound.org/previews/)
SOURCES = {
    'roar_scary': (489901, 'NicknameLarry', 'Scary Monster Roar #2', '489/489901_10418401-hq.mp3'),
    'roar_short': (132874, 'ecfike', 'Monster Short Roar.wav', '132/132874_2337290-hq.mp3'),
    'roar_monster': (537883, 'colorsCrimsonTears', 'Monster roar', '537/537883_11785387-hq.mp3'),
    'growl_hybrid': (146558, 'john129pats', 'Hybrid Monster Growl', '146/146558_831330-hq.mp3'),
    'growl_monster': (262431, 'jackieAZ', 'Monster Growl 1.wav', '262/262431_4385633-hq.mp3'),
    'grunt_monster': (262430, 'jackieAZ', 'Monster Grunt 1', '262/262430_4385633-hq.mp3'),
    'growl6': (77637, 'lendrick', 'growl6.wav', '77/77637_237568-hq.mp3'),
    'screech': (807383, 'Elth2010', 'Monster screech', '807/807383_16147181-hq.mp3'),
    'slime_squish': (442772, 'qubodup', 'Slime Squish', '442/442772_71257-hq.mp3'),
    'slime27': (433840, 'Archos', 'Slime 27.wav', '433/433840_652422-hq.mp3'),
    'gel_cube': (837146, 'e_r_r_1012', 'Gelatinous Cube Approach', '837/837146_17859805-hq.mp3'),
    'squelching': (547446, 'tiddles451', 'squelching.mp3', '547/547446_9912277-hq.mp3'),
    'gel_hands': (575333, 'breadparticles', 'squishing gel between hands', '575/575333_9246516-hq.mp3'),
    'gore_squish': (535354, 'EminYILDIRIM', 'Blood Gore Slime Squish', '535/535354_10912485-hq.mp3'),
    'staff_whoosh': (422513, 'Nightflame', 'Swinging staff whoosh (strong) 04.wav', '422/422513_3302499-hq.mp3'),
    'swing_woosh': (389590, 'Jofae', 'Swing Woosh', '389/389590_6512973-hq.mp3'),
    'punch_impact': (266823, 'ms107', 'Punch impact sound.wav', '266/266823_5035221-hq.mp3'),
    'major_punch': (399183, 'janbezouska', 'Major punch', '399/399183_3287894-hq.mp3'),
    'rope_whoosh': (473583, 'Nox_Sound', 'Whoosh_Rope.wav', '473/473583_9250976-hq.mp3'),
    'whip_woosh': (735907, 'ErikCruzDev', 'Whip woosh', '735/735907_15173542-hq.mp3'),
    'splat': (147017, 'JustInvoke', 'Splat', '147/147017_758593-hq.mp3'),
    'splat3': (719131, 'sebastientate', 'Splat 3', '719/719131_13711016-hq.mp3'),
    'slug_splat': (447930, 'Breviceps', 'Step on a slug (Splat!) 2', '447/447930_9159316-hq.mp3'),
    'whoosh_triple': (733891, 'velcronator', 'Whoosh Triple', '733/733891_6703998-hq.mp3'),
    'whip_crack': (529925, 'SciFiSounds', 'Whip Crack.m4a', '529/529925_11751070-hq.mp3'),
    'whip_crack2': (615761, 'JayRom01', 'whip and crack sound', '615/615761_13658484-hq.mp3'),
    'body_debris': (461697, 'leonelmail', 'Body falls into debris', '461/461697_4437257-hq.mp3'),
    'body_carpet': (119825, 'kbnevel', 'BodyFall_Carpet.aif', '119/119825_1990690-hq.mp3'),
    'ice_crack': (262635, 'j_p_higgins', 'Ice Crack 1', '262/262635_3175560-hq.mp3'),
    'leather': (501015, 'Crinkem', 'Leather Stress 1 (Short)', '501/501015_3544425-hq.mp3'),
    'fabric_tear': (565970, 'melle_teich', 'Tearing fabric', '565/565970_12013381-hq.mp3'),
    'cloth_rip': (591527, 'Rudmer_Rotteveel', 'Cloth Rip Even Long', '591/591527_4921277-hq.mp3'),
    'slurp': (568043, 'benkenart', 'coffee slurp 5', '568/568043_12771108-hq.mp3'),
    'gulp': (531755, 'magnuswaker', 'Gulp - Hard Swallow', '531/531755_11537497-hq.mp3'),
    'heartbeat': (43621, 'kathol', 'real heartbeat.wav', '43/43621_211893-hq.mp3'),
    'breath_heavy': (422342, 'BehanSean', 'heavy breathing.wav', '422/422342_6613494-hq.mp3'),
    'breath_agitated': (516696, 'Amekiano', 'Respiración agitada (heavy breathing).wav', '516/516696_4148049-hq.mp3'),
    'whispers': (193818, 'geoneo0', 'Four_Voices_Whispering_6_wEcho.wav', '193/193818_2000738-hq.mp3'),
    'suction_pop': (523687, 'JoBot__', 'Suction Pop', '523/523687_9795688-hq.mp3'),
    'plunger': (164684, 'deleted_user_2104797', 'Plunger_4.wav', '164/164684_2104797-hq.mp3'),
}

# loudness class -> loudest 100 ms RMS target (dBFS)
CLASSES = {'signature': -12.0, 'impact': -11.5, 'motion': -15.0, 'body': -15.5, 'host': -18.0, 'utility': -17.0}


def L(src, start, length, at=0.0, gain=0.0, pitch=1.0, rev=False, hp=None, lp=None, fin=0.004, fout=0.04):
    """A layer: `length` s of source `src` from `start`, placed at `at` s, gain in dB, pitch by resampling."""
    return dict(src=src, start=start, length=length, at=at, gain=gain, pitch=pitch, rev=rev, hp=hp, lp=lp, fin=fin, fout=fout)


# event -> (class, subtitle use, [variant = [layers]]); the use text goes to the source table
EVENTS = {
    'transform': ('signature', 'the symbiote covers the host, grows and roars (one file, 2.8 s)', [[
        L('gel_hands', 0.40, 1.30, 0.0, -1), L('slime_squish', 0.0, 0.40, 0.15, -3), L('slime27', 0.07, 0.45, 0.55, -2),
        L('gel_cube', 0.03, 0.95, 0.85, -4, 0.8), L('growl_hybrid', 0.04, 1.30, 1.05, -3, 0.85, fin=0.25),
        L('roar_monster', 0.06, 1.0, 1.75, 0, 0.8, fout=0.3)]]),
    'revert': ('body', 'the symbiote recedes into the host', [[
        L('gel_hands', 3.0, 1.1, 0.0, 0, rev=True, fin=0.15), L('slime27', 0.07, 0.45, 0.55, -3, rev=True),
        L('growl6', 0.0, 1.15, 0.1, -9, 0.75, fin=0.2, fout=0.3)]]),
    'roar': ('signature', 'the frenzy roar, the boss roar', [
        [L('roar_scary', 0.04, 2.4, 0.0, 0, 0.85, fout=0.4), L('roar_short', 0.02, 1.27, 0.0, -7, 0.7)],
        [L('roar_monster', 0.06, 1.6, 0.0, 0, 0.8, fout=0.3), L('growl_hybrid', 0.04, 1.5, 0.05, -6, 0.75, fout=0.3)]]),
    'squirm': ('body', 'the living surface squirms (transformation stages, hunger, reactions)', [
        [L('squelching', 0.09, 0.8, 0.0, 0, 0.9)], [L('squelching', 1.22, 0.75, 0.0, 0, 0.85)],
        [L('gel_hands', 2.42, 0.7, 0.0, 0, 0.9), L('slime_squish', 0.0, 0.4, 0.2, -6)]]),
    'reform': ('body', 'the body pulls itself back together after a heavy hit', [
        [L('slime27', 0.07, 0.6, 0.0, 0, rev=True), L('slime_squish', 0.0, 0.42, 0.35, -2)],
        [L('gel_hands', 5.0, 0.6, 0.0, 0), L('slime27', 0.07, 0.45, 0.3, -3, 0.9)]]),
    'shriek': ('signature', 'the symbiote shrieks at fire and loud sound', [
        [L('screech', 0.05, 1.55, 0.0, 0, 0.9, fout=0.3)], [L('screech', 0.05, 1.55, 0.0, 0, 0.78, hp=180, fout=0.3)]]),
    'claw': ('motion', 'claw swing', [
        [L('staff_whoosh', 0.03, 0.27, 0.0, 0, 0.85)], [L('staff_whoosh', 0.03, 0.27, 0.0, 0, 1.0)],
        [L('swing_woosh', 0.03, 0.27, 0.0, 0, 0.9), L('slime_squish', 0.0, 0.3, 0.0, -16)]]),
    'claw_hit': ('impact', 'claws tear into a target', [
        [L('punch_impact', 0.32, 0.5, 0.0, 0), L('splat', 0.0, 0.38, 0.01, -5)],
        [L('major_punch', 0.08, 0.5, 0.0, 0, 0.9), L('splat3', 0.0, 0.5, 0.01, -6)],
        [L('slug_splat', 0.03, 0.55, 0.0, -2), L('punch_impact', 0.32, 0.5, 0.0, -3, 0.85), L('gore_squish', 0.95, 0.5, 0.02, 4)]]),
    'tendril_shot': ('motion', 'a tendril shoots out', [
        [L('rope_whoosh', 0.07, 0.3, 0.0, 0), L('splat', 0.0, 0.3, 0.2, -12)],
        [L('whip_woosh', 0.02, 0.4, 0.0, 0, 0.9), L('slime_squish', 0.0, 0.3, 0.05, -12)]]),
    'tendril_stick': ('body', 'the tendril sticks to a surface or a body', [
        [L('splat', 0.0, 0.39, 0.0, 0, 0.85)], [L('splat3', 0.0, 0.55, 0.0, 0), L('slime_squish', 0.0, 0.3, 0.0, -6, 0.8)]]),
    'zip': ('motion', 'towed through the air by a tendril', [[
        L('whoosh_triple', 0.01, 1.22, 0.0, 0, 0.9, fout=0.25), L('rope_whoosh', 0.07, 0.3, 0.0, -4)]]),
    'lash': ('impact', 'a tentacle lash', [
        [L('rope_whoosh', 0.07, 0.25, 0.0, -4), L('whip_crack', 0.06, 0.4, 0.12, 0, 0.9)],
        [L('swing_woosh', 0.03, 0.2, 0.0, -4, 0.8), L('whip_crack2', 0.04, 0.48, 0.1, 0, 0.85)]]),
    'slam': ('impact', 'a body slammed into the ground, the lash finisher, the boss leap', [
        [L('body_debris', 0.15, 1.45, 0.0, 0, fout=0.3), L('major_punch', 0.08, 0.6, 0.0, -2, 0.6)],
        [L('body_carpet', 0.0, 1.3, 0.0, 0, fout=0.3), L('major_punch', 0.08, 0.6, 0.0, -2, 0.55), L('slug_splat', 0.03, 0.5, 0.02, -8)]]),
    'grab': ('body', 'claws and goo seize a body', [
        [L('slug_splat', 0.03, 0.55, 0.0, 0), L('leather', 0.42, 0.5, 0.08, -6)],
        [L('slime27', 0.07, 0.5, 0.0, 0), L('gel_hands', 6.0, 0.5, 0.05, -4)]]),
    'throw': ('motion', 'a held body hurled away', [
        [L('staff_whoosh', 0.03, 0.27, 0.0, 0, 0.6), L('grunt_monster', 0.1, 0.4, 0.0, -2)],
        [L('swing_woosh', 0.03, 0.27, 0.0, 0, 0.55), L('growl6', 0.0, 0.55, 0.0, -6, 0.9)]]),
    'shell': ('body', 'the symbiote hardens into a shell', [[
        L('ice_crack', 0.13, 0.6, 0.0, 0, 0.7), L('leather', 0.42, 0.85, 0.15, -3), L('gel_hands', 8.0, 0.8, 0.0, -6)]]),
    'web_tear': ('body', 'the Spider\'s web torn apart', [
        [L('fabric_tear', 0.06, 0.62, 0.0, 0)], [L('cloth_rip', 0.02, 1.0, 0.0, 0, 1.1, fout=0.15)]]),
    'web_eat': ('body', 'the symbiote swallows a web strand', [[
        L('slurp', 0.19, 1.05, 0.0, 0, 0.8), L('gulp', 0.0, 0.52, 0.9, -1, 0.8)]]),
    'clash': ('impact', 'Venom and the Spider collide', [
        [L('major_punch', 0.08, 0.6, 0.0, 0), L('punch_impact', 0.32, 0.5, 0.0, -3, 0.8), L('whip_crack2', 0.04, 0.4, 0.0, -10, 0.6)],
        [L('punch_impact', 0.32, 0.5, 0.0, 0, 0.9), L('body_debris', 0.5, 0.8, 0.0, -4)]]),
    'engulf': ('signature', 'the symbiote engulfs a host', [[
        L('growl_hybrid', 0.04, 1.2, 0.0, -6, 0.7), L('gel_hands', 9.0, 1.2, 0.1, -1), L('slime_squish', 0.0, 0.42, 0.25, -2),
        L('gulp', 0.0, 0.52, 0.75, 0, 0.6, fout=0.2)]]),
    'heartbeat': ('host', 'the host\'s racing heartbeat (heard only by him)', [
        [L('heartbeat', 0.80, 0.65, 0.0, 0, lp=500, fout=0.12)], [L('heartbeat', 2.15, 0.6, 0.0, 0, lp=500, fout=0.12)]]),
    'breath': ('host', 'the host\'s breathing, muffled (heard only by him)', [
        [L('breath_heavy', 0.2, 1.8, 0.0, 0, 0.88, fin=0.1, fout=0.3)], [L('breath_agitated', 0.0, 1.6, 0.0, 0, 0.85, fin=0.1, fout=0.3)]]),
    'whisper': ('host', 'the inner voices: reversed, pitched whispers, no words (heard only by the host)', [
        [L('whispers', 1.6, 1.8, 0.0, 0, 0.85, rev=True, fin=0.2, fout=0.3)], [L('whispers', 4.0, 1.8, 0.0, 0, 0.8, rev=True, fin=0.2, fout=0.3)],
        [L('whispers', 6.4, 1.8, 0.0, 0, 0.9, rev=True, fin=0.2, fout=0.3)]]),
    'release': ('body', 'the symbiote lets go of a host', [[
        L('slime27', 0.07, 0.45, 0.0, -2, rev=True), L('plunger', 0.14, 0.2, 0.32, 0), L('suction_pop', 0.0, 0.1, 0.4, -4)]]),
    'refuse': ('utility', 'an ability is refused (a short low grunt)', [
        [L('grunt_monster', 0.08, 0.42, 0.0, 0, 0.8), L('growl6', 0.0, 0.4, 0.0, -10, 0.85)],
        [L('growl_monster', 0.05, 0.6, 0.0, 0, 0.8, fout=0.15)]]),
}


def raw(src):
    return CACHE / 'raw' / f'fs_{SOURCES[src][0]}.mp3'


def page(src):
    return CACHE / 'pages' / f'{SOURCES[src][0]}.html'


def fetch():
    for d in ('raw', 'pages'):
        (CACHE / d).mkdir(parents=True, exist_ok=True)
    for src, (fid, user, _, preview) in SOURCES.items():
        if not raw(src).exists():
            subprocess.run(['curl', '-sfL', '-m', '90', '-A', 'Mozilla/5.0', '-o', str(raw(src)), 'https://cdn.freesound.org/previews/' + preview], check=True)
        if not page(src).exists():
            subprocess.run(['curl', '-sfL', '-m', '60', '-A', 'Mozilla/5.0', '-o', str(page(src)), f'https://freesound.org/people/{user}/sounds/{fid}/'],
                           check=True)
        print('ok', src)


def verify():
    bad = 0
    for src, (fid, user, title, _) in SOURCES.items():
        h = page(src).read_text(encoding='utf-8', errors='replace')
        lic = sorted(set(re.findall(r'creativecommons\.org/(?:publicdomain|licenses)/[a-z\-]+/[0-9.]+', h)))
        ok = lic == [LICENCE] and f'/people/{user}/sounds/{fid}' in h and title in h.replace('&#x27;', "'").replace('&quot;', '"').replace('&amp;', '&')
        bad += not ok
        print('OK ' if ok else 'BAD', fid, user, title, lic)
    if bad:
        sys.exit(f'{bad} source(s) failed the licence check')


_CACHE = {}


def decode(src):
    if src not in _CACHE:
        pcm = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', str(raw(src)), '-ac', '1', '-ar', str(SR), '-f', 'f32le', '-'],
                             capture_output=True, check=True).stdout
        _CACHE[src] = np.frombuffer(pcm, dtype=np.float32).astype(np.float64)
    return _CACHE[src]


def band(x, hp=None, lp=None):
    if hp is None and lp is None:
        return x
    n = len(x)
    f = np.fft.rfftfreq(n * 2, 1 / SR)
    X = np.fft.rfft(np.concatenate([x, np.zeros(n)]))
    g = np.ones_like(f)
    if hp:
        g *= 1 / np.sqrt(1 + (hp / np.maximum(f, 1e-3)) ** 4)
    if lp:
        g *= 1 / np.sqrt(1 + (f / lp) ** 4)
    return np.fft.irfft(X * g)[:n]


def layer(spec):
    x = decode(spec['src'])
    a = int(spec['start'] * SR)
    n = int(spec['length'] * spec['pitch'] * SR)
    seg = x[a:a + n].copy()
    if len(seg) < 8:
        raise ValueError(f'empty layer {spec}')
    if spec['rev']:
        seg = seg[::-1]
    if spec['pitch'] != 1.0:
        m = int(len(seg) / spec['pitch'])
        seg = np.interp(np.linspace(0, len(seg) - 1, m), np.arange(len(seg)), seg)
    seg = band(seg, spec['hp'], spec['lp'])
    fi, fo = int(spec['fin'] * SR), int(spec['fout'] * SR)
    if fi:
        seg[:fi] *= np.linspace(0, 1, min(fi, len(seg)))[:len(seg[:fi])]
    if fo:
        seg[-fo:] *= np.cos(np.linspace(0, np.pi / 2, len(seg[-fo:]))) ** 2
    return seg * 10 ** (spec['gain'] / 20)


def loudest_rms(x):
    w = int(0.1 * SR)
    if len(x) <= w:
        return 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)
    c = np.cumsum(np.concatenate([[0], x ** 2]))
    return 10 * np.log10((c[w:] - c[:-w]).max() / w + 1e-12)


def render(layers, cls):
    end = max(s['at'] + s['length'] for s in layers) + 0.05
    out = np.zeros(int(end * SR) + SR)
    for s in layers:
        seg = layer(s)
        a = int(s['at'] * SR)
        out[a:a + len(seg)] += seg
    nz = np.nonzero(np.abs(out) > 1e-5)[0]
    out = out[:nz[-1] + 1] if len(nz) else out
    out = band(out, hp=35)
    out *= 10 ** ((CLASSES[cls] - loudest_rms(out)) / 20)
    # soft knee above -6 dBFS, then a hard ceiling at -1 dBFS
    k = 10 ** (-6 / 20)
    over = np.abs(out) > k
    out[over] = np.sign(out[over]) * (k + (1 - k) * np.tanh((np.abs(out[over]) - k) / (1 - k)))
    ceil = 10 ** (-1 / 20)
    pk = np.abs(out).max()
    if pk > ceil:
        out *= ceil / pk
    tail = min(len(out), int(0.01 * SR))
    out[-tail:] *= np.linspace(1, 0, tail)
    return out


def files():
    for ev, (cls, use, variants) in EVENTS.items():
        for i, v in enumerate(variants):
            yield ev, (ev if len(variants) == 1 else f'{ev}_{i + 1}'), cls, v


def encode(x, name):
    with tempfile.TemporaryDirectory() as td:
        wav = os.path.join(td, 'a.wav')
        ogg = os.path.join(td, 'a.ogg')
        pcm = (np.clip(x, -1, 1) * 32767).astype('<i2').tobytes()
        import wave
        with wave.open(wav, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm)
        for q in ('4', '3', '2', '1', '0'):
            subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', q, '-ar', str(SR), '-ac', '1',
                            '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', ogg], check=True)
            data = Path(ogg).read_bytes()
            if len(base64.b64encode(data)) < 52000:
                return data, q
    raise ValueError(f'{name} does not fit one push request')


def package():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob('*.ogg.b64*'):
        old.unlink()
    rows = []
    for ev, name, cls, v in files():
        x = render(v, cls)
        data, q = encode(x, name)
        b64 = base64.encodebytes(data).decode()
        (OUT / f'{name}.ogg.b64').write_text(b64)
        pk = 20 * np.log10(np.abs(x).max() + 1e-12)
        rows.append((name, len(x) / SR, loudest_rms(x), pk, len(data), q))
        print(f'{name:16s} {len(x) / SR:5.2f}s rms100 {loudest_rms(x):6.1f} peak {pk:5.1f} ogg {len(data):6d} q{q}')
    overlay = {}
    for ev, (cls, use, variants) in EVENTS.items():
        names = [ev] if len(variants) == 1 else [f'{ev}_{i + 1}' for i in range(len(variants))]
        sounds = []
        for n in names:
            s = {'name': f'viltrumitecore:venom/{n}'}
            if ev in ('heartbeat', 'breath', 'whisper'):
                s['attenuation_distance'] = 4
            elif ev in ('roar', 'shriek', 'transform', 'slam'):
                s['attenuation_distance'] = 32
            sounds.append(s)
        overlay[f'venom.{ev}'] = {'category': 'player', 'subtitle': f'subtitles.viltrumitecore.venom.{ev}', 'sounds': sounds}
    OVERLAY.write_text(json.dumps(overlay, indent=2) + '\n', encoding='utf-8')
    return rows


def write_docs():
    """docs/venom/audio-sources.md: the licence proof per source and the recipe and purpose per shipped file."""
    used = {}
    for ev, name, cls, v in files():
        for x in v:
            used.setdefault(x['src'], []).append(name)
    out = ['# Venom — audio sources', '',
           'Every Venom sound is cut, layered, pitched and levelled from **real recordings released under CC0 1.0** on Freesound.',
           'No synthesis stands in for a recording, no vanilla Minecraft sound, no film or game audio is used.', '',
           f'**Licence proof.** Each row was checked on its Freesound landing page on {CHECKED}: the page names the author and the',
           'title shown below and its licence link points to the CC0 1.0 deed `https://creativecommons.org/publicdomain/zero/1.0/`',
           '(label "Creative Commons 0"). `python3 tools/sfx/venom.py --fetch --verify` downloads the pages again and fails if any',
           'page shows another licence, another author or another title. The audio of each row is that page\'s hq MP3 preview.',
           'CC0 needs no attribution; the authors are credited anyway. This file ships in the mod jar as',
           '`META-INF/credits/venom-audio.md`.', '',
           'Rendering: `python3 tools/sfx/venom.py --package` (mono 44.1 kHz Ogg Vorbis, base64 under',
           '`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/venom/`, events in',
           '`viltrumitecore/src/main/sound-overlays/venom.json`). Levels are measured, not auditioned: loudest 100 ms RMS per class',
           '(' + ', '.join(f'{k} {v:g} dBFS' for k, v in CLASSES.items()) + '), peak at most −1 dBFS.', '',
           '## Sources', '',
           '| Source | Freesound page | Author | Title (landing page) | Licence | Used in |', '|---|---|---|---|---|---|']
    for src, (fid, user, title, _) in SOURCES.items():
        u = ', '.join(sorted(set(used.get(src, []))))
        out.append(f'| `{src}` | [freesound.org/s/{fid}](https://freesound.org/s/{fid}/) | {user} | {title.replace("|", "/")} | '
                   f'[CC0 1.0](https://{LICENCE}/) | {u} |')
    out += ['', '## Shipped files', '', '| File (`sounds/venom/`) | Event | Purpose | Layers: source (start s, length s, pitch, gain dB) |',
            '|---|---|---|---|']
    for ev, name, cls, v in files():
        lay = '; '.join(f"`{x['src']}` ({x['start']:g}, {x['length']:g}, ×{x['pitch']:g}, {x['gain']:+g}{', reversed' if x['rev'] else ''})" for x in v)
        out.append(f'| `{name}.ogg` | `venom.{ev}` | {EVENTS[ev][1]} | {lay} |')
    out.append('')
    DOC.parent.mkdir(parents=True, exist_ok=True)
    DOC.write_text('\n'.join(out), encoding='utf-8')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--fetch', action='store_true')
    ap.add_argument('--verify', action='store_true')
    ap.add_argument('--package', action='store_true')
    ap.add_argument('--docs', action='store_true')
    a = ap.parse_args()
    if a.fetch:
        fetch()
    if a.verify:
        verify()
    if a.package:
        package()
    if a.docs:
        write_docs()
    unused = set(SOURCES) - {s['src'] for _, _, _, v in files() for s in v}
    assert not unused, f'unused sources: {unused}'


if __name__ == '__main__':
    main()
