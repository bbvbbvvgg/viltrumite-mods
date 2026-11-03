#!/usr/bin/env python3
"""Own sounds of SCP-096 (issue #136), shipped in the jar so a clean install never falls back to vanilla events:
the sobbing loop, the hysteric wailing loop, the chase screaming loop, the sting when the face is seen, the chase
music, the strike, the smash through walls and the leap. Mixed from CC0 1.0 Freesound recordings
(tools/sfx/city_life_sources.py: SCP, credits in tools/assets/scp096_sources.md) and synthesised here (the chase
music and the sting are written note by note below, nothing is sampled from SCP: Containment Breach or from any
song). The originals of a player's own SCP: CB copy still replace the five recognisable ones at start-up
(client/scp096/Scp096OriginalSounds).

    python3 tools/sfx/scp096.py            # writes sounds/scp096/*.ogg(.b64) and sound-overlays/scp096.json
    python3 tools/sfx/scp096.py --wav /tmp/scp
"""
import argparse
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import city_life_sources as S  # noqa: E402
import fslib as F  # noqa: E402

SR = 32000
NS = "viltrumitecore"
OVERLAY = os.path.join(F.REPO, "viltrumitecore/src/main/sound-overlays/scp096.json")
WAV_DIR = None
REPORT = []
rng = np.random.default_rng(96)


def src(key, sr=SR):
    return F.load(S.SCP[key][0], sr)


def out(rel, x, quality):
    if WAV_DIR:
        os.makedirs(WAV_DIR, exist_ok=True)
        F.write_wav(os.path.join(WAV_DIR, rel.replace("/", "__") + ".wav"), x, SR)
        F.describe(rel, x, SR)
        return
    data = F.encode(x, SR, quality)
    n = F.write_b64("scp096/" + rel, data)
    REPORT.append((rel, len(x) / SR, len(data), n))
    F.describe(rel, x, SR, len(data))


def norm(x):
    return x / (F.rms_track(x, SR).mean() + 1e-9)


def window(key, seconds, pitch=1.0, skip=0.3, lo=None, hi=None):
    x = src(key)
    need = seconds * pitch + 0.6
    if len(x) / SR < need + skip:
        x = np.concatenate([x] * int(np.ceil((need + skip) / (len(x) / SR) + 1)))
    a = F.steadiest(x, SR, need, skip=skip)
    y = x[int(a * SR):int((a + need) * SR)]
    if pitch != 1.0:
        y = F.resample(y, pitch)
    if lo or hi:
        y = F.fft_filter(y, SR, lo, hi)
    return y


def mix(n, parts):
    acc = np.zeros(n)
    for y, g, at in parts:
        F.place(acc, norm(y), at, g, SR)
    return acc


def reverb(x, seconds=1.4, wet=0.25, seed=1):
    """Small diffuse tail (exponential noise impulse response, FFT convolution)."""
    r = np.random.default_rng(seed)
    n = int(seconds * SR)
    ir = r.standard_normal(n) * np.exp(-np.arange(n) / (n / 6.0))
    ir = F.fft_filter(ir, SR, 200, 6000)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    m = len(x) + n
    y = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)[:len(x)]
    return x * (1 - wet) + y * wet * 3.0


# ---------------------------------------------------------------- creature loops (mono, positional)

def build_cry():
    # a grown man's broken, high, wheezing sobbing: two cries raised a little, a whimper on top and a ghostly
    # breathy layer far behind
    n = int(22 * SR)
    y = mix(n + int(2 * SR), [(window("cry_man", 24, 1.14, lo=170, hi=7200), 1.0, 0.0),
                             (window("sobs_man", 15, 1.18, lo=200, hi=7000), 0.55, 3.0),
                             (window("whimper", 6, 1.1, lo=220, hi=7500), 0.45, 9.5),
                             (window("whimper", 6, 1.16, lo=220, hi=7500), 0.35, 16.0),
                             (window("ghost_cry", 4.4, 1.0, lo=300, hi=5000), 0.22, 6.0)])
    y = reverb(y, 1.2, 0.18, 2)
    out("cry", F.level(F.loop(y, SR, 22, 1.8), SR, -20.0), 2)


def build_angered():
    # hysteria: the wailing climbs, breaks and comes back, never pausing
    n = int(12 * SR)
    h = src("hysteric")
    parts = []
    for k, (p, at) in enumerate(((0.94, 0.0), (1.02, 3.9), (0.9, 7.6), (0.98, 10.4))):
        parts.append((F.fft_filter(F.resample(h, p), SR, 180, 8500), 1.0, at))
    parts.append((window("girl_crying", 14, 0.86, lo=160, hi=6500), 0.6, 0.0))
    parts.append((window("cry_man_2", 14, 1.22, lo=200, hi=6500), 0.45, 0.0))
    y = mix(n + int(2 * SR), parts)
    y = np.tanh(norm(y) * 0.5) / 0.5
    y = reverb(y, 1.0, 0.2, 3)
    out("angered", F.level(F.loop(y, SR, 12, 1.2), SR, -16.0), 3)


def build_scream():
    # the chase: a relentless shriek, layered human and inhuman screams, short gasps between
    n = int(10 * SR)
    parts = [(window("shrieker", 11, 1.0, lo=150, hi=9000), 1.0, 0.0),
             (window("man_screaming", 11, 1.28, lo=200, hi=8000), 0.7, 0.0),
             (window("hq_screech", 11, 0.92, lo=180, hi=8000), 0.4, 0.0),
             (F.fft_filter(src("demonic_scream"), SR, 200, 8000), 0.6, 1.2),
             (F.fft_filter(src("horror_shrieks"), SR, 200, 8000), 0.5, 5.4)]
    y = mix(n + int(2 * SR), parts)
    y = np.tanh(norm(y) * 0.6) / 0.6
    y = reverb(y, 0.9, 0.15, 4)
    out("scream", F.level(F.loop(y, SR, 10, 1.0), SR, -13.0), 3)


# ---------------------------------------------------------------- the sting and the chase music (synthesised)

def env(n, a, d, sustain=0.0, curve=4.0):
    t = np.arange(n) / SR
    att = np.clip(t / max(a, 1e-4), 0, 1)
    dec = sustain + (1 - sustain) * np.exp(-np.maximum(t - a, 0) / max(d, 1e-4) * curve / 4.0)
    return att * dec


def saw(freq, n, detune=0.0, phase=0.0):
    t = np.arange(n) / SR
    f = freq * (1 + detune)
    return 2.0 * ((t * f + phase) % 1.0) - 1.0


def hz(note):
    return 440.0 * 2 ** ((note - 69) / 12.0)


def strings(notes, n, vib=5.2, trem=0.0, bright=3000):
    t = np.arange(n) / SR
    acc = np.zeros(n)
    for k, m in enumerate(notes):
        f = hz(m) * (1 + 0.004 * np.sin(2 * np.pi * (vib + 0.3 * k) * t + k))
        ph = np.cumsum(f) / SR
        for d in (-0.003, 0.0, 0.0035):
            acc += 2.0 * ((ph * (1 + d) + rng.random()) % 1.0) - 1.0
    acc = F.fft_filter(acc, SR, 90, bright)
    if trem:
        acc *= 0.65 + 0.35 * np.sign(np.sin(2 * np.pi * trem * t))
    return acc


def build_triggered():
    n = int(5.0 * SR)
    t = np.arange(n) / SR
    pre = int(0.45 * SR)
    total = np.zeros(n + pre)
    # reversed screech sucked in before the hit
    s = F.fft_filter(src("monster_screech"), SR, 300, 9000)
    a, b = F.events(s, SR, rel_db=-8, max_len=1.2)[0]
    hit = s[int(a * SR):int(b * SR)]
    rev = hit[::-1][-pre:] * np.linspace(0, 1, min(pre, len(hit))) ** 2
    total[pre - len(rev):pre] += norm(rev) * 0.5
    # the hit: sub boom, a dissonant string cluster stab with a long trembling decay, the screech forward
    boom = np.sin(2 * np.pi * np.cumsum(38 + 50 * np.exp(-t / 0.18)) / SR) * env(n, 0.004, 1.6)
    clus = strings([57, 58, 63, 64, 70], n, vib=6.0, trem=13.0, bright=5200) * env(n, 0.01, 2.6)
    shriek = np.zeros(n)
    F.place(shriek, norm(hit), 0.0, 1.0, SR)
    noise = F.fft_filter(rng.standard_normal(n), SR, 2500, 7000) * env(n, 0.002, 0.25)
    body = norm(boom) * 1.3 + norm(clus) * 0.8 + shriek * 0.55 + norm(noise) * 0.3
    total[pre:] += body
    y = reverb(total, 2.2, 0.3, 5)
    y *= np.concatenate([np.ones(len(y) - int(0.8 * SR)), np.linspace(1, 0, int(0.8 * SR))])
    out("triggered", F.level(y, SR, -12.0), 3)


def build_chase_music():
    # 16 bars of 4/4 at 145 bpm in D minor: taiko-like low drums, a chromatic eighth-note bass ostinato, a
    # trembling low string cluster that swells every four bars and a thin high dissonance; every note that rings
    # past the end is folded back onto the start, so the loop has no seam.
    bpm = 145.0
    beat = 60.0 / bpm
    bars = 16
    length = bars * 4 * beat
    n = int(round(length * SR))
    acc = np.zeros(n)

    def put(x, at, gain):
        i = int(round(at * SR)) % n
        k = min(len(x), n - i)
        acc[i:i + k] += x[:k] * gain
        if k < len(x):
            acc[:len(x) - k] += x[k:] * gain

    def drum(f0, decay, noise=0.2):
        m = int(decay * 4 * SR)
        tt = np.arange(m) / SR
        body = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 1.4 * np.exp(-tt / 0.03))) / SR) * np.exp(-tt / decay)
        hitn = F.fft_filter(rng.standard_normal(m), SR, 300, 4000) * np.exp(-tt / 0.02) * noise
        return body + hitn

    def metal():
        m = int(1.2 * SR)
        tt = np.arange(m) / SR
        x = sum(np.sin(2 * np.pi * f * tt + rng.random() * 6) for f in (523, 797, 1187, 1663, 2311)) * np.exp(-tt / 0.35)
        return x * 0.25

    low, mid, met = drum(52, 0.32), drum(88, 0.16, 0.35), metal()
    for bar in range(bars):
        t0 = bar * 4 * beat
        for b in (0, 2.5):
            put(low, t0 + b * beat, 1.0)
        for b in (1.0, 3.0):
            put(mid, t0 + b * beat, 0.55)
        for s in range(16):
            if s % 4 == 3 and rng.random() < 0.5:
                put(mid, t0 + s * beat / 4, 0.18)
        if bar % 2 == 1:
            put(met, t0 + 3.5 * beat, 0.35)
        if bar % 4 == 3:
            for k in range(4):
                put(low, t0 + (3 + k / 4) * beat, 0.6 + 0.1 * k)
    drums = acc.copy()
    acc[:] = 0
    # bass: D D F D Eb D C D (eighths), an octave lower on the last bar of each phrase
    seq = [38, 38, 41, 38, 39, 38, 36, 38]
    m = int(beat / 2 * SR)
    for bar in range(bars):
        for s, note in enumerate(seq):
            nn = note - (12 if bar % 4 == 3 and s >= 4 else 0)
            x = saw(hz(nn), int(m * 1.1)) + 0.5 * saw(hz(nn) * 0.5, int(m * 1.1), 0.003)
            x = F.fft_filter(x, SR, 30, 600 + 500 * (s % 2)) * env(len(x), 0.004, beat / 2 * 0.8, 0.2)
            put(x, bar * 4 * beat + s * beat / 2, 1.0)
    bass = np.tanh(norm(acc) * 0.8)
    acc[:] = 0
    # strings: four-bar swells of a tense cluster, tremolo 16ths, rising a semitone every phrase
    phrase = int(4 * 4 * beat * SR)
    for p in range(bars // 4):
        root = 50 + (0, 1, 0, 3)[p]
        x = strings([root, root + 1, root + 7, root + 13], phrase + int(0.6 * SR), vib=5.0, trem=bpm / 60 * 4, bright=3500)
        tt = np.arange(len(x)) / SR
        x *= (0.35 + 0.65 * np.clip(tt / (phrase / SR), 0, 1) ** 2) * np.minimum(1, (len(x) / SR - tt) / 0.6)
        put(x, p * 4 * 4 * beat, 1.0)
    strs = norm(acc)
    acc[:] = 0
    # high thin dissonance (two close sines), slowly beating
    tt = np.arange(n) / SR
    high = (np.sin(2 * np.pi * hz(86) * tt) + np.sin(2 * np.pi * (hz(86) * 1.03) * tt)) * (0.5 + 0.5 * np.sin(2 * np.pi * tt / length * 4))
    music = norm(drums) * 1.0 + bass * 0.75 + strs * 0.55 + norm(high) * 0.08
    music = reverb_loop(music, 1.6, 0.22)
    st = np.stack([music, music], axis=1)
    d = int(0.013 * SR)
    st[:, 1] = np.roll(strs * 0.55, d) + norm(drums) + bass * 0.75 + np.roll(norm(high) * 0.08, -d)
    st[:, 1] = reverb_loop(st[:, 1], 1.6, 0.22, seed=7)
    out("chase_music", F.level(st, SR, -15.0), 3)


def reverb_loop(x, seconds, wet, seed=6):
    n = len(x)
    y = reverb(np.concatenate([x, x[: int(seconds * SR)]]), seconds, wet, seed)
    z = y[:n].copy()
    tail = y[n:] - x[: len(y) - n] * (1 - wet)
    z[: len(tail)] += tail
    return z


# ---------------------------------------------------------------- impacts (mono one-shots)

def hits(key, k, max_len, lo=60, hi=9000, rel=-10):
    x = F.fft_filter(src(key), SR, lo, hi)
    res = []
    for a, b in F.events(x, SR, rel_db=rel, max_len=max_len, min_gap=0.3):
        if b - a < 0.08:
            continue
        res.append(F.cut(x, SR, a, b, 0.002, 0.06))
        if len(res) == k:
            break
    return res


def layer(*parts, length=None):
    n = int((length or max(len(p) / SR + at for p, g, at in parts)) * SR)
    acc = np.zeros(n)
    for y, g, at in parts:
        F.place(acc, norm(y), at, g, SR)
    return acc


def build_impacts():
    kicks = hits("power_kick", 3, 0.6)
    crunch = hits("bonebreaker", 3, 0.7, 200, 8000)
    smack = hits("smack_body", 3, 0.5)
    punch = hits("major_punch", 1, 0.9)[0]
    for i in range(3):
        y = layer((kicks[i], 1.0, 0.0), (smack[i % len(smack)], 0.6, 0.005), (crunch[i % len(crunch)], 0.45, 0.02), (punch, 0.35, 0.0))
        out(f"strike_{i + 1}", F.level(reverb(y, 0.5, 0.12, 10 + i), SR, -10.0), 3)
    wood = src("wood_smash")
    glass = hits("break_window", 2, 1.6, 300, 10000)
    for i in range(3):
        sub = F.fft_filter(punch, SR, 30, 400)
        y = layer((F.resample(wood, (0.92, 1.0, 1.08)[i]), 1.0, 0.0), (sub, 0.9, 0.0), (crunch[(i + 1) % len(crunch)], 0.5, 0.03),
                  (glass[i % len(glass)], 0.35 if i != 1 else 0.0, 0.05), length=1.9)
        out(f"smash_{i + 1}", F.level(reverb(y, 0.8, 0.15, 20 + i), SR, -10.0), 3)
    whoosh = F.fft_filter(src("big_whoosh"), SR, 80, 8000)
    steps = hits("barefoot_heavy", 2, 0.5, 40, 6000)
    for i in range(2):
        land = F.fft_filter(punch, SR, 30, 900)
        y = layer((F.resample(whoosh, (1.0, 1.1)[i]), 0.8, 0.0), (steps[i], 1.0, 0.55), (land, 0.7, 0.55), length=1.6)
        out(f"leap_{i + 1}", F.level(y, SR, -12.0), 3)


# ---------------------------------------------------------------- sound events

def write_events():
    def one(name, sub, files, att=None, stream=False, pitch=None, volume=None, category="hostile"):
        snd = []
        for f in files:
            s = {"name": f"{NS}:scp096/{f}"}
            if stream:
                s["stream"] = True
            if att:
                s["attenuation_distance"] = att
            if pitch:
                s["pitch"] = pitch
            if volume:
                s["volume"] = volume
            snd.append(s)
        ev = {"category": category, "sounds": snd}
        if sub:
            ev["subtitle"] = f"subtitles.{NS}.scp096.{sub}"
        return ev

    ev = {
        "scp096.cry": one(name="cry", sub="cry", files=["cry"], att=20, stream=True),
        "scp096.angered": one("angered", "angered", ["angered"], att=40, stream=True),
        "scp096.scream": one("scream", "scream", ["scream"], att=56, stream=True),
        "scp096.triggered": one("triggered", "triggered", ["triggered"]),
        "scp096.chase_music": one("chase_music", None, ["chase_music"], stream=True, category="music"),
        # the server plays these lower (Scp096Chase / Scp096Destruction: 0.55-0.95); the sound pitch brings our
        # recordings back near their own pitch
        "scp096.strike": one("strike", "strike", [f"strike_{i}" for i in (1, 2, 3)], pitch=1.2),
        "scp096.smash": one("smash", "smash", [f"smash_{i}" for i in (1, 2, 3)], pitch=1.5),
        "scp096.leap": one("leap", "leap", ["leap_1", "leap_2"], pitch=1.6),
    }
    json.dump(ev, open(OVERLAY, "w"), ensure_ascii=False, indent=2)
    open(OVERLAY, "a").write("\n")


def main():
    global WAV_DIR
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--wav")
    ap.add_argument("--only", action="append", default=[])
    a = ap.parse_args()
    if a.fetch:
        F.fetch(S.SCP)
        return
    WAV_DIR = a.wav
    parts = a.only or ["cry", "angered", "scream", "triggered", "music", "impacts", "data"]
    for p, fn in (("cry", build_cry), ("angered", build_angered), ("scream", build_scream), ("triggered", build_triggered),
                  ("music", build_chase_music), ("impacts", build_impacts)):
        if p in parts:
            fn()
    if "data" in parts and not WAV_DIR:
        write_events()
    if REPORT:
        print(f"{len(REPORT)} files, {sum(r[2] for r in REPORT) / 1024:.0f} KB of Vorbis")


if __name__ == "__main__":
    main()
