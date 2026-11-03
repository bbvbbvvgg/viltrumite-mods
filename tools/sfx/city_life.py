#!/usr/bin/env python3
"""Sounds of the living Atheria (issue #136): street beds, distant street details, panic, coughs and sobs mixed
from CC0 1.0 Freesound recordings (tools/sfx/city_life_sources.py, credits in tools/assets/city_life_sources.md),
and the Russian conversations and calls of the citizens (own text-to-speech takes, tools/sfx/city_life_voice.py).

Writes the packed sounds (viltrumitecore/src/main/binassets/.../sounds/city_life/), the sound events
(src/main/sound-overlays/city_life*.json), the subtitles in all twelve locales
(src/main/lang-overlays/city_life/) and the generated line table of the server
(city/life/CityLifeCatalog.java).

    python3 tools/sfx/city_life.py --fetch          # CC0 previews into ~/.cache/viltrumite-sfx/work/raw
    python3 tools/sfx/city_life.py                  # everything
    python3 tools/sfx/city_life.py --only beds      # beds | details | panic | voices | data
    python3 tools/sfx/city_life.py --wav /tmp/cl    # listenable WAVs instead of packing
"""
import argparse
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import city_life_lines as L  # noqa: E402
import city_life_sources as S  # noqa: E402
import fslib as F  # noqa: E402

SR = 32000
BED_RATE = 22050  # beds are dull backgrounds; 22 kHz keeps the shipped jar small
NS = "viltrumitecore"
MAIN = os.path.join(F.REPO, "viltrumitecore/src/main")
LANG_DIR = os.path.join(MAIN, "lang-overlays/city_life")
OVERLAY = os.path.join(MAIN, "sound-overlays")
CATALOG = os.path.join(MAIN, "java/dev/baranhan/viltrumitecore/city/life/CityLifeCatalog.java")
TTS_CACHE = os.path.expanduser("~/.cache/viltrumite-sfx/work/city_life_tts")
LOCALES = {"en_us": 0, "ru_ru": 1, "de_de": 2, "es_ar": 3, "es_cl": 3, "es_ec": 3, "es_es": 3, "es_mx": 3, "es_uy": 3,
           "es_ve": 3, "tr_tr": 4, "zh_cn": 5}
QUOTES = {0: ("“", "”"), 1: ("«", "»"), 2: ("„", "“"), 3: ("«", "»"), 4: ("“", "”"), 5: ("“", "”")}
WAV_DIR = None
REPORT = []


def src(key, stereo=False, sr=SR):
    fid = (S.CITY.get(key) or S.SCP[key])[0]
    return F.load(fid, sr, stereo)


def out(rel, x, quality, sr=SR, rate=None):
    if WAV_DIR:
        p = os.path.join(WAV_DIR, rel.replace("/", "__") + ".wav")
        os.makedirs(WAV_DIR, exist_ok=True)
        F.write_wav(p, x, sr)
        F.describe(rel, x, sr)
        return
    data = F.encode(x, sr, quality, rate=rate)
    n = F.write_b64("city_life/" + rel, data)
    REPORT.append((rel, len(x) / sr, len(data), n))
    F.describe(rel, x, sr, len(data))


# ---------------------------------------------------------------- beds (stereo loops, non-positional)

def stereo(x):
    return x if x.ndim == 2 else np.stack([x, x], axis=1)


def widen(m, sr, delay=0.011):
    """Pseudo-stereo from mono: a short Haas offset plus opposite comb colouring."""
    d = int(delay * sr)
    l = m.copy()
    r = np.concatenate([np.zeros(d), m[:-d]])
    return np.stack([0.6 * l + 0.4 * r, 0.4 * l + 0.6 * r], axis=1)


def bed_from(key, seconds, lo=None, hi=None, start=None, pitch=1.0):
    x = src(key, stereo=True)
    need = seconds * pitch + 2.0
    a = F.steadiest(x, SR, need) if start is None else start
    y = x[int(a * SR):int((a + need + 0.5) * SR)]
    if pitch != 1.0:
        y = F.resample(y, pitch)
    if lo or hi:
        y = F.fft_filter(y, SR, lo, hi)
    return F.loop(y, SR, seconds, 1.5)


def mono(x):
    """Positional beds are mono so the game pans them toward the crowd they stand for."""
    return x.mean(axis=1) if x.ndim == 2 else x


def mixdown(parts, seconds):
    n = int(seconds * SR)
    acc = np.zeros((n, 2))
    for y, g in parts:
        y = stereo(y)[:n]
        y = y / (F.rms_track(y, SR).mean() + 1e-9)
        acc[:len(y)] += y * g
    return acc


def build_beds():
    # calm crowd: a shopping street, a small walla and a café murmur stacked, the top rolled off a little
    # (people a few metres away, words not readable); one layer reversed so no language stands out
    crowd = mixdown([(bed_from("shop_street", 20, 90, 6500), 1.0), (bed_from("walla", 20, 120, 4200), 0.55),
                     (bed_from("coffeehouse", 20, 140, 3600)[::-1].copy(), 0.4)], 20)
    out("bed/crowd", F.level(mono(crowd), SR, -22.0), 0, rate=BED_RATE)
    # a few people far down the street: old-town murmur, darker and thinner
    sparse = mixdown([(bed_from("old_town", 18, 110, 3800), 1.0), (bed_from("walla", 18, 160, 2600, pitch=0.96)[::-1].copy(), 0.3)], 18)
    out("bed/crowd_far", F.level(mono(sparse), SR, -25.0), 0, rate=BED_RATE)
    # footsteps of a crowd: two-person walks layered with offsets, high-passed (no thumping)
    steps = mixdown([(bed_from("steps_wet", 14, 140, 9000), 1.0), (bed_from("steps_stone", 14, 140, 9000), 0.9),
                     (np.roll(bed_from("steps_wet", 14, 140, 9000, pitch=1.04), int(3.7 * SR), axis=0), 0.6)], 14)
    out("bed/steps", F.level(mono(steps), SR, -24.0), 0, rate=BED_RATE)
    # people running (panic): running parts of the concrete steps and a crowd on stairs
    run = mixdown([(bed_from("steps_concrete", 12, 110, 9000), 1.0), (bed_from("stairs_run", 12, 110, 9000), 0.9)], 12)
    out("bed/running", F.level(mono(run), SR, -22.0), 0, rate=BED_RATE)
    # the city by day: a Berlin morning (birds, far traffic hum), a park; by night a quiet corner and crickets
    day = mixdown([(bed_from("berlin_morning", 24, 60, 9000), 1.0), (bed_from("park_morning", 24, 60, 9000), 0.6)], 24)
    out("bed/street_day", F.level(day, SR, -24.0), 0, rate=BED_RATE)
    night = mixdown([(bed_from("night_corner", 20, 50, 7000), 1.0), (bed_from("crickets", 20, 1500, 9000), 0.35)], 20)
    out("bed/street_night", F.level(night, SR, -26.0), 0, rate=BED_RATE)
    # wind between the buildings: a gentle plain wind and the whistle of a window, slowly breathing
    wind = mixdown([(bed_from("gentle_wind", 20, 40, 6000), 1.0), (bed_from("window_wind", 20, 120, 5000), 0.45)], 20)
    t = np.arange(len(wind)) / SR
    wind *= (0.8 + 0.2 * np.sin(2 * np.pi * t / 20 * 2))[:, None]
    out("bed/wind", F.level(wind, SR, -24.0), 0, rate=BED_RATE)
    # panic in the streets: two crowd-panic recordings
    panic = mixdown([(bed_from("crowd_panic", 14, 110, 8000), 1.0), (bed_from("crowd_panic_2", 14, 110, 8000), 0.7)], 14)
    out("bed/panic", F.level(mono(panic), SR, -20.0), 1, rate=BED_RATE)
    # stunned aftermath: a thin far murmur, wind and a long quiet night corner (no birds), dark
    hush = mixdown([(bed_from("old_town", 18, 90, 1800, pitch=0.94), 0.5), (bed_from("night_corner", 18, 50, 3000), 1.0),
                    (bed_from("gentle_wind", 18, 40, 2500), 0.7)], 18)
    out("bed/hush", F.level(hush, SR, -28.0), 0, rate=BED_RATE)


# ---------------------------------------------------------------- details (mono one-shots, positional)

def pick_events(key, n, max_len, rel_db=-16.0, min_len=0.25, lo=None, hi=None, gain_db=-16.0, far=False, pre=0.04):
    x = src(key)
    if lo or hi:
        x = F.fft_filter(x, SR, lo, hi)
    outv = []
    for a, b in F.events(x, SR, rel_db=rel_db, max_len=max_len, pre=pre):
        if b - a < min_len:
            continue
        y = F.cut(x, SR, a, min(b, a + max_len), 0.004, 0.08)
        if far:
            y = F.fft_filter(y, SR, 120, 3500)
        outv.append(F.level(y, SR, gain_db))
        if len(outv) == n:
            break
    return outv


def build_details():
    for i, y in enumerate(pick_events("dog_far", 2, 2.4, far=True) + pick_events("dog_big_far", 2, 2.6, far=True), 1):
        out(f"detail/dog_{i}", y, 2)
    for i, y in enumerate(pick_events("bike_bell", 2, 1.6, rel_db=-10) + pick_events("bike_bell_2", 1, 1.8, rel_db=-10), 1):
        out(f"detail/bike_bell_{i}", y, 2)
    for i, key in enumerate(("pigeons", "pigeons_2", "pigeons_3"), 1):
        x = src(key)
        a = F.events(x, SR, rel_db=-18, max_len=4.0)[0][0]
        out(f"detail/pigeons_{i}", F.level(F.cut(x, SR, max(0, a - 0.1), min(len(x) / SR, a + 3.6), 0.02, 0.6), SR, -18.0), 2)
    for i, y in enumerate(pick_events("laugh_small", 1, 4.4, rel_db=-20, gain_db=-19, min_len=0.8) + pick_events("laugh_crowd", 2, 3.6, rel_db=-14, gain_db=-19, min_len=0.8), 1):
        out(f"detail/laugh_{i}", y, 2)
    x = src("bells_town")
    a = F.steadiest(x, SR, 9.0)
    out("detail/bells_1", F.level(F.cut(x, SR, a, a + 9.0, 0.4, 2.0), SR, -20.0), 2)


def gendered_screams(keys, n, max_len, f0_range):
    import pyworld as pw
    res = []
    for key in keys:
        x = src(key)
        for a, b in F.events(x, SR, rel_db=-12, max_len=max_len, min_gap=0.5):
            if b - a < 0.45:
                continue
            y = x[int(a * SR):int(b * SR)]
            f0, _ = pw.harvest(F.resample(y, SR / 16000.0).astype(np.float64), 16000, f0_floor=80, f0_ceil=1100, frame_period=10)
            v = f0[f0 > 0]
            if len(v) < 5 or not (f0_range[0] <= np.median(v) <= f0_range[1]):
                continue
            res.append(F.level(F.cut(x, SR, a, b, 0.004, 0.12), SR, -11.0))
            if len([r for r in res]) >= n:
                return res
    return res


def build_panic():
    for i, y in enumerate(gendered_screams(["f_fear", "f_scream_1", "f_scream_2", "f_scream_x2"], 4, 2.2, (300, 1100)), 1):
        out(f"panic/scream_female_{i}", y, 3)
    for i, y in enumerate(gendered_screams(["m_loud", "m_scream", "m_wounded"], 4, 2.2, (110, 520)), 1):
        out(f"panic/scream_male_{i}", y, 3)
    coughs = []
    for key in ("cough_1", "cough_2", "cough_3", "cough_4", "cough_5"):
        coughs += pick_events(key, 2, 1.6, rel_db=-12, gain_db=-17)[:2]
    for i, y in enumerate(coughs[:8], 1):
        out(f"panic/cough_{i}", y, 2)
    for i, (key, a_len) in enumerate((("sob_f", 4.5), ("sob_f_2", 4.5)), 1):
        x = src(key)
        a = F.steadiest(x, SR, a_len, skip=1.0)
        out(f"panic/sob_female_{i}", F.level(F.cut(x, SR, a, a + a_len, 0.15, 0.8), SR, -19.0), 2)
    x = src("sob_m")
    for i, a in enumerate((F.steadiest(x, SR, 4.5, skip=2.0), F.steadiest(x, SR, 4.5, skip=30.0)), 1):
        out(f"panic/sob_male_{i}", F.level(F.cut(x, SR, a, a + 4.5, 0.15, 0.8), SR, -19.0), 2)


# ---------------------------------------------------------------- voices

def voice_take(v, line):
    import city_life_voice as V
    os.makedirs(TTS_CACHE, exist_ok=True)
    p = os.path.join(TTS_CACHE, f"{v}_{line}.npy")
    if os.path.exists(p):
        return np.load(p)
    seed = abs(hash((v, line))) % (1 << 30) if False else sum(map(ord, v + line)) * 7919
    x, _ = V.synth(v, line, seed)
    x = V.trim(x)
    np.save(p, x)
    return x


def build_voices():
    import city_life_voice as V
    V.fetch_voices()
    durations = {}
    calls = L.call_lines()
    for v in L.VOICES:
        for line in L.LINES:
            x = voice_take(v, line)
            x = F.fft_filter(x, V.SR, 95, 7600)
            x = V.level(x, -15.5 if line in calls else -19.0)
            durations[(v, line)] = len(x) / V.SR
            if WAV_DIR:
                out(f"voice/{v}/{line}", x, 0, V.SR)
                continue
            # speech at 16 kHz: talk at an average 18 kbit/s, calls (shouted) at quality 0
            data = F.encode(x, V.SR, 0, rate=16000, bitrate=None if line in calls else "18k")
            F.write_b64(f"city_life/voice/{v}/{line}", data)
            REPORT.append((f"voice/{v}/{line}", len(x) / V.SR, len(data), 1))
    json.dump({f"{v}/{l}": d for (v, l), d in durations.items()}, open(os.path.join(TTS_CACHE, "durations.json"), "w"), indent=0)
    return durations


# ---------------------------------------------------------------- data: events, subtitles, server table

BEDS = {"crowd": "crowd", "crowd_far": "crowd", "steps": "steps", "running": "running", "street_day": "street",
        "street_night": "night", "wind": "wind", "panic": "panic", "hush": "hush"}


def files(prefix):
    d = os.path.join(F.SOUNDS, "city_life", os.path.dirname(prefix))
    stem = os.path.basename(prefix)
    names = sorted({f.split(".ogg")[0] for f in os.listdir(d) if f.startswith(stem + "_") and ".ogg" in f},
                   key=lambda s: int(s.rsplit("_", 1)[1]))
    return [f"{NS}:city_life/{os.path.dirname(prefix)}/{n}" for n in names]


def write_data(durations):
    ev = {}
    for name, sub in BEDS.items():
        ev[f"city_life.bed.{name}"] = {"category": "ambient", "subtitle": f"subtitles.{NS}.city_life.{sub}",
                                       "sounds": [{"name": f"{NS}:city_life/bed/{name}"}]}
    far = {"attenuation_distance": 64}
    for name, sub in (("dog", "dog"), ("bike_bell", "bike_bell"), ("pigeons", "pigeons"), ("laugh", "laugh"), ("bells", "bells")):
        ev[f"city_life.{name}"] = {"category": "ambient", "subtitle": f"subtitles.{NS}.city_life.{sub}",
                                   "sounds": [dict(name=n, **far) for n in files(f"detail/{name}")]}
    for g in ("male", "female"):
        ev[f"city_life.scream.{g}"] = {"category": "neutral", "subtitle": f"subtitles.{NS}.city_life.scream",
                                       "sounds": files(f"panic/scream_{g}")}
        ev[f"city_life.sob.{g}"] = {"category": "neutral", "subtitle": f"subtitles.{NS}.city_life.sob",
                                    "sounds": [{"name": n, "attenuation_distance": 12} for n in files(f"panic/sob_{g}")]}
    ev["city_life.cough"] = {"category": "neutral", "subtitle": f"subtitles.{NS}.city_life.cough",
                             "sounds": [{"name": n, "attenuation_distance": 14} for n in files("panic/cough")]}
    json.dump(ev, open(os.path.join(OVERLAY, "city_life.json"), "w"), ensure_ascii=False, indent=2)
    calls = L.call_lines()
    for v in L.VOICES:
        fem = v.startswith("f")
        vev = {}
        for line in L.LINES:
            key = f"subtitles.{NS}.city_life.line.{line}" + (".f" if fem and L.gendered(line) else "")
            snd = {"name": f"{NS}:city_life/voice/{v}/{line}"}
            if line not in calls:
                snd["attenuation_distance"] = 14
            vev[f"city_life.voice.{v}.{line}"] = {"category": "neutral", "subtitle": key, "sounds": [snd]}
        json.dump(vev, open(os.path.join(OVERLAY, f"city_life_voice_{v}.json"), "w"), ensure_ascii=False, indent=1)
    # subtitles
    os.makedirs(LANG_DIR, exist_ok=True)
    for loc, col in LOCALES.items():
        q0, q1 = QUOTES[col]
        lang = {}
        for k, t in L.SOUND_SUBTITLES.items():
            lang[f"subtitles.{NS}.city_life.{k}"] = t[col]
        for k, t in L.COMMAND_TEXT.items():
            lang[k] = t[col]
        for line, (ru, en) in L.LINES.items():
            if col == 1:
                lang[f"subtitles.{NS}.city_life.line.{line}"] = q0 + L.text_for(line, False) + q1
                if L.gendered(line):
                    lang[f"subtitles.{NS}.city_life.line.{line}.f"] = q0 + L.text_for(line, True) + q1
                continue
            text = en if col == 0 else L.I18N[line][col - 2]
            lang[f"subtitles.{NS}.city_life.line.{line}"] = q0 + text + q1
            if L.gendered(line):
                lang[f"subtitles.{NS}.city_life.line.{line}.f"] = q0 + text + q1
        json.dump(lang, open(os.path.join(LANG_DIR, f"{loc}.json"), "w"), ensure_ascii=False, indent=2)
    write_catalog(durations)


def write_catalog(durations):
    lines = list(L.LINES)
    voices = list(L.VOICES)
    ticks = [[int(math.ceil(durations[(v, l)] * 20.0)) for l in lines] for v in voices]
    mood = {L.CALM: "CALM", L.WARY: "WARY", L.AFTER: "AFTER"}
    when = {L.ANY: "ANY", L.MORNING: "MORNING", L.DAY: "DAY", L.EVENING: "EVENING", L.RAIN: "RAIN", L.CLEAR: "CLEAR"}
    j = []
    j.append("package dev.baranhan.viltrumitecore.city.life;\n")
    j.append("/**")
    j.append(" * GENERATED by tools/sfx/city_life.py from tools/sfx/city_life_lines.py - do not edit by hand. The lines the")
    j.append(" * citizens of the living city speak (#136): ids, the length of every take per voice (ticks), the dialogues")
    j.append(" * (who says which line, for which mood and time of day) and the single calls by occasion. Voice {@code v}")
    j.append(" * speaking line {@code l} is the sound event {@code viltrumitecore:city_life.voice.<VOICES[v]>.<LINES[l]>}.")
    j.append(" */")
    j.append("public final class CityLifeCatalog {")
    j.append("   public static final String[] VOICES = {" + ", ".join(f'"{v}"' for v in voices) + "};")
    j.append("   public static final String[] LINES = {")
    for k in range(0, len(lines), 6):
        j.append("      " + ", ".join(f'"{l}"' for l in lines[k:k + 6]) + ",")
    j.append("   };")
    j.append("   /** Ticks of each take: TICKS[voice][line]. */")
    j.append("   static final short[][] TICKS = {")
    for row in ticks:
        j.append("      {" + ", ".join(map(str, row)) + "},")
    j.append("   };")
    j.append("   public static final int CALM = 0;")
    j.append("   public static final int WARY = 1;")
    j.append("   public static final int AFTER = 2;")
    j.append("   public static final int ANY = 0;")
    j.append("   public static final int MORNING = 1;")
    j.append("   public static final int DAY = 2;")
    j.append("   public static final int EVENING = 3;")
    j.append("   public static final int RAIN = 4;")
    j.append("   /** Daytime without rain. */")
    j.append("   public static final int CLEAR = 5;")
    j.append("   public static final String[] DIALOGUES = {" + ", ".join(f'"{d[0]}"' for d in L.DIALOGUES) + "};")
    j.append("   public static final int[] MOOD = {" + ", ".join(mood[d[1]] for d in L.DIALOGUES) + "};")
    j.append("   public static final int[] WHEN = {" + ", ".join(when[d[2]] for d in L.DIALOGUES) + "};")
    j.append("   public static final int[] WEIGHT = {" + ", ".join(str(d[3]) for d in L.DIALOGUES) + "};")
    j.append("   /** Turns of each dialogue: role (0 starts, 1 answers) and line index, packed role << 16 | line. */")
    j.append("   public static final int[][] TURNS = {")
    for d in L.DIALOGUES:
        j.append("      {" + ", ".join(f"{r} << 16 | {lines.index(l)}" for r, l in d[4]) + "},")
    j.append("   };")
    for kind, ls in L.CALLS.items():
        j.append(f"   public static final int[] CALL_{kind.upper()} = {{" + ", ".join(str(lines.index(l)) for l in ls) + "};")
    j.append("")
    j.append("   private CityLifeCatalog() {")
    j.append("   }")
    j.append("")
    j.append("   /** Ticks voice v needs for line l. */")
    j.append("   public static int ticks(int voice, int line) {")
    j.append("      return TICKS[voice][line];")
    j.append("   }")
    j.append("")
    j.append("   /** Index of a line id, -1 when unknown. */")
    j.append("   public static int line(String id) {")
    j.append("      for (int i = 0; i < LINES.length; i++) {")
    j.append("         if (LINES[i].equals(id)) {")
    j.append("            return i;")
    j.append("         }")
    j.append("      }")
    j.append("      return -1;")
    j.append("   }")
    j.append("")
    j.append("   /** Index of a voice id, -1 when unknown. */")
    j.append("   public static int voice(String id) {")
    j.append("      for (int i = 0; i < VOICES.length; i++) {")
    j.append("         if (VOICES[i].equals(id)) {")
    j.append("            return i;")
    j.append("         }")
    j.append("      }")
    j.append("      return -1;")
    j.append("   }")
    j.append("}")
    os.makedirs(os.path.dirname(CATALOG), exist_ok=True)
    open(CATALOG, "w").write("\n".join(j) + "\n")


def main():
    global WAV_DIR
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--only", action="append", default=[])
    ap.add_argument("--wav")
    a = ap.parse_args()
    if a.fetch:
        F.fetch(S.CITY)
        return
    WAV_DIR = a.wav
    parts = a.only or ["beds", "details", "panic", "voices", "data"]
    durations = None
    if "beds" in parts:
        build_beds()
    if "details" in parts:
        build_details()
    if "panic" in parts:
        build_panic()
    if "voices" in parts:
        durations = build_voices()
    if "data" in parts and not WAV_DIR:
        if durations is None:
            d = json.load(open(os.path.join(TTS_CACHE, "durations.json")))
            durations = {tuple(k.split("/")): v for k, v in d.items()}
        write_data(durations)
    if REPORT:
        total = sum(r[2] for r in REPORT)
        print(f"{len(REPORT)} files, {total / 1024:.0f} KB of Vorbis")


if __name__ == "__main__":
    main()
