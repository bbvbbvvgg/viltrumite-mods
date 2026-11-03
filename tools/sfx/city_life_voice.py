"""Voice recordings of the Atheria citizens (#136): Russian text-to-speech, own recordings of this project.

Piper (https://github.com/OHF-Voice/piper1-gpl) speaks the lines with the voices ``ru_RU-denis-medium`` and
``ru_RU-dmitri-medium`` (rhasspy/piper-voices; both trained on CC0 datasets of OHF-Voice/voice-datasets). The
two female voices are the same takes re-pitched with the WORLD vocoder (pyworld): F0 up about a sixth to a fifth
and the spectral envelope stretched so the formants move up as well. Calls are spoken faster, pitched up and
driven harder so they read as shouts. Every take is trimmed, high-passed and levelled here.

    pip install piper-tts pyworld        # once; voices are fetched into ~/.cache/viltrumite-sfx/piper
"""
import os
import re
import subprocess
import urllib.request
import wave

import numpy as np

import city_life_lines as L

PIPER = os.path.expanduser("~/.cache/viltrumite-sfx/piper")
VOICE_URL = "https://huggingface.co/rhasspy/piper-voices/resolve/main/ru/ru_RU/{v}/medium/ru_RU-{v}-medium.{ext}"
SR = 22050
_loaded = {}


def fetch_voices():
    os.makedirs(PIPER, exist_ok=True)
    for v in ("denis", "dmitri"):
        for ext in ("onnx", "onnx.json"):
            p = os.path.join(PIPER, f"ru_RU-{v}-medium.{ext}")
            if not os.path.exists(p):
                print("fetch", p)
                urllib.request.urlretrieve(VOICE_URL.format(v=v, ext=ext), p)


def _voice(name):
    if name not in _loaded:
        from piper import PiperVoice
        _loaded[name] = PiperVoice.load(os.path.join(PIPER, f"ru_RU-{name}-medium.onnx"))
    return _loaded[name]


def mood_of(line):
    if line in L.call_lines():
        return "call"
    for d in L.DIALOGUES:
        if any(l == line for _, l in d[4]):
            return d[1]
    return L.CALM


def synth(voice_id, line, seed):
    """Raw take (float mono, SR) of one line in one voice."""
    from piper.config import SynthesisConfig
    base, f0k, warp, speed = L.VOICES[voice_id]
    female = voice_id.startswith("f")
    text = L.text_for(line, female)
    mood = mood_of(line)
    rnd = np.random.default_rng(seed)
    length = {"call": 0.84, L.WARY: 1.05, L.AFTER: 1.08}.get(mood, 1.0) / speed
    length *= 1.0 + rnd.uniform(-0.03, 0.03)
    cfg = SynthesisConfig(length_scale=length, noise_scale=0.62 + rnd.uniform(0.0, 0.1), noise_w_scale=0.75 + rnd.uniform(0.0, 0.1))
    chunks = [c.audio_float_array for c in _voice(base).synthesize(text, syn_config=cfg)]
    x = np.concatenate(chunks).astype(np.float64)
    shout = mood == "call"
    if female or shout:
        x = world(x, f0k * (1.16 if shout else 1.0), warp * (1.03 if shout else 1.0), lift=0.35 if shout else 0.0)
    return x, text


def world(x, f0k, warp, lift=0.0):
    """WORLD re-synthesis: F0 times f0k, formants stretched by warp, optional upper-band lift (strain)."""
    import pyworld as pw
    f0, t = pw.harvest(x, SR, f0_floor=60, f0_ceil=500, frame_period=5.0)
    sp = pw.cheaptrick(x, f0, t, SR)
    ap = pw.d4c(x, f0, t, SR)
    n = sp.shape[1]
    idx = np.arange(n)
    src = idx / warp
    sp = np.stack([np.interp(src, idx, r) for r in sp])
    ap = np.stack([np.interp(src, idx, r) for r in ap])
    if lift:
        freq = idx * (SR / 2.0) / (n - 1)
        tilt = 1.0 + lift * np.clip((freq - 1200.0) / 2500.0, 0.0, 1.0)
        sp = sp * tilt[None, :] ** 2
    # a little vibrato-free jitter keeps the re-pitched voice from sounding buzzy
    return pw.synthesize(np.where(f0 > 0, f0 * f0k, 0.0), sp, ap, SR, 5.0)


def trim(x, floor_db=-42.0, pad=0.03):
    env = np.convolve(np.abs(x), np.ones(220) / 220.0, mode="same")
    thr = np.max(env) * 10 ** (floor_db / 20.0)
    on = np.nonzero(env > thr)[0]
    if on.size == 0:
        return x
    a = max(0, on[0] - int(pad * SR))
    b = min(len(x), on[-1] + int(pad * 2 * SR))
    y = x[a:b].copy()
    fi, fo = int(0.006 * SR), int(0.04 * SR)
    y[:fi] *= np.linspace(0, 1, fi)
    y[-fo:] *= np.linspace(1, 0, fo)
    return y


def write_wav(path, x, sr=SR):
    x = np.clip(x, -1.0, 1.0)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes((x * 32767).astype(np.int16).tobytes())


def level(x, rms_db, peak_db=-1.5):
    """Loudest 100 ms RMS to rms_db, soft-clipped under peak_db."""
    win = int(0.1 * SR)
    pw_ = np.convolve(x * x, np.ones(win) / win, mode="valid") if len(x) > win else np.array([np.mean(x * x)])
    loud = np.sqrt(np.max(pw_)) + 1e-9
    y = x * (10 ** (rms_db / 20.0) / loud)
    lim = 10 ** (peak_db / 20.0)
    return np.tanh(y / lim) * lim
