#!/usr/bin/env python3
"""World Cut ambience derived from one owner-provided OGG.

Source: `01-original-final-tail.ogg` (8.00 s mono Vorbis, SHA-256 pinned).
It is a quieter re-export of the approved CC-BY 4.0 `248150` tail that this
repository already ships as `world_cut_tail`.  Raw input never enters the
repository; only derived `.ogg.b64` files are installed through the Gradle
binary-asset decoder.

Two outputs:
  world_cut_hum    1.99 s seamless loop.  The first 2.00 s of the source are
                   summed with their own time reverse: y[t] = s[t] + s[T-t].
                   The decay and the swell cancel, so the loop keeps a steady
                   level, and y[0] == y[T] exactly (no volume jump at the
                   seam).  An 8 ms raised-cosine rotation then removes the
                   slope kink while keeping the loop circular.
  world_cut_pulse  1.35 s low swell for the periodic rupture pulse: the same
                   seed, pitched down 10 %, with a 30 ms attack and a smooth
                   quadratic release.

Usage:
  python3 tools/sfx/reinhard_worldcut.py --src path/to/01-original-final-tail.ogg
  python3 tools/sfx/reinhard_worldcut.py --src ... --check
  python3 tools/sfx/reinhard_worldcut.py --src ... --report out.json

--check rebuilds in a temporary directory and compares decoded PCM with the
committed files, like tools/sfx/reinhard.py.  No listening or runtime proof.
"""
import argparse
import array
import base64
import hashlib
import json
import math
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/reinhard'

RATE = 44100
SOURCE_SHA256 = '8fc6abd552883d1e19afca25e7c589b7e7e6402c64bada75e0cc66857e12dce6'
SEED_START = 0.10
SEED_SECONDS = 2.00
SEAM_MS = 8
PULSE_START = 0.10
PULSE_SECONDS = 1.60
PULSE_PITCH = 0.90
PULSE_LENGTH = 1.35
PULSE_ATTACK = 0.03
PULSE_RELEASE = 2.2
IMPACT_START = 0.10
IMPACT_SECONDS = 0.65
IMPACT_LENGTH = 0.60
IMPACT_PITCH = 0.90
TRUE_PEAK = -2
# Decoded loop gates: the wrap jump in full scale (-40 dBFS) and relative to
# the mean per-sample change at the loop start.  A step click is far above
# both; the committed hum decodes to 0.0021 and 1.6 (its steepest natural
# step in the window is 3.9x the mean, so 6 leaves room for a smooth wrap).
SEAM_MAX_DELTA = 0.01
SEAM_MAX_RATIO = 6.0
SEAM_WINDOW = 2000
TARGETS = {'world_cut_hum': -12.6, 'world_cut_pulse': -18, 'world_cut_impact': -14}


def run(*args, stdin=None):
    result = subprocess.run(args, input=stdin, capture_output=True)
    if result.returncode:
        raise ValueError(result.stderr.decode(errors='replace')[-3000:])
    return result


def read_pcm(path, min_seconds=8.0):
    data = run('ffmpeg', '-v', 'error', '-nostdin', '-i', str(path), '-ac', '1', '-ar', str(RATE),
               '-f', 'f32le', '-acodec', 'pcm_f32le', '-').stdout
    samples = array.array('f')
    samples.frombytes(data)
    if len(samples) < int(min_seconds * RATE):
        raise ValueError(f'Source shorter than {min_seconds:g} s: {path}')
    return samples


def smooth_seam(seg, n):
    """Circular rotation with a raised-cosine overlap at the wrap point.

    Keeps the original y[0] == y[-1] relation: the output ends on seg[n-1]
    and starts on seg[n]; the last n samples blend the old tail with the head.
    """
    length = len(seg)
    if n <= 0 or n * 2 >= length:
        raise ValueError('Invalid seam length')
    body = length - 2 * n
    out = array.array('f')
    out.extend(seg[n:length - n])
    for i in range(n):
        t = i / (n - 1)
        a = 0.5 - 0.5 * math.cos(math.pi * t)
        out.append(seg[length - n + i] * (1.0 - a) + seg[i] * a)
    return out


def saturate(samples, drive=2.0, ceiling=0.8):
    """Memoryless tanh drive: more RMS without digital clipping.

    A static waveshaper maps equal input samples to equal output samples, so
    a seamless loop stays seamless.  The peak is then pinned to the ceiling;
    linear scaling keeps the seam relation.
    """
    shaped = array.array('f', (ceiling * math.tanh(drive * v / ceiling) for v in samples))
    peak = max(abs(v) for v in shaped)
    if peak <= 0:
        raise ValueError('Silent loop')
    scale = ceiling / peak
    return array.array('f', (v * scale for v in shaped))


def build_loop(pcm):
    """Forward plus time-reverse of the first two seconds, exact seam."""
    start = int(SEED_START * RATE)
    length = int(SEED_SECONDS * RATE)
    seed = pcm[start:start + length]
    if len(seed) != length:
        raise ValueError('Source does not contain the two-second seed')
    folded = array.array('f', (seed[i] + seed[length - 1 - i] for i in range(length)))
    return saturate(smooth_seam(folded, int(SEAM_MS * RATE / 1000)))


def build_impact(pcm):
    """Short pitched-down hit for BLAST; the attack stays, the tail is tight."""
    start = int(IMPACT_START * RATE)
    length = int(IMPACT_SECONDS * RATE)
    seed = pcm[start:start + length]
    if len(seed) != length:
        raise ValueError('Source does not contain the impact seed')
    total = int(IMPACT_LENGTH * RATE)
    attack = int(.005 * RATE)
    tail = int(.03 * RATE)
    out = array.array('f')
    for i in range(total):
        pos = i * IMPACT_PITCH
        j = min(int(pos), len(seed) - 2)
        f = pos - j
        value = seed[j] * (1.0 - f) + seed[j + 1] * f
        if i < attack:
            value *= i / attack
        u = i / (total - 1)
        value *= (1.0 - u) ** 2.5
        if i > total - tail:
            value *= (total - i) / tail
        out.append(value)
    return out


def build_pulse(pcm):
    """Pitched-down swell with a short attack and a quadratic release."""
    start = int(PULSE_START * RATE)
    length = int(PULSE_SECONDS * RATE)
    seed = pcm[start:start + length]
    if len(seed) != length:
        raise ValueError('Source does not contain the pulse seed')
    step = PULSE_PITCH  # read slower to lower the pitch; >1 would raise it
    out = array.array('f')
    total = int(PULSE_LENGTH * RATE)
    attack = int(PULSE_ATTACK * RATE)
    tail = int(0.05 * RATE)
    for i in range(total):
        pos = i * step
        j = min(int(pos), len(seed) - 2)
        f = pos - j
        value = seed[j] * (1.0 - f) + seed[j + 1] * f
        if i < attack:
            value *= i / attack
        u = i / (total - 1)
        value *= (1.0 - u) ** PULSE_RELEASE
        if i > total - tail:
            value *= (total - i) / tail
        out.append(value)
    return out


def write_raw(path, samples):
    path.write_bytes(samples.tobytes())


def measure(path, target, raw=False):
    inputs = ['-f', 'f32le', '-ar', str(RATE), '-ac', '1'] if raw else []
    result = run('ffmpeg', '-hide_banner', '-nostdin', *inputs, '-i', str(path), '-af',
                 f'loudnorm=I={target}:TP={TRUE_PEAK}:LRA=11:print_format=json', '-f', 'null', '-')
    text = result.stderr.decode()
    obj = json.loads(text[text.rfind('{'):text.rfind('}') + 1])
    for key in ('input_i', 'input_tp', 'input_lra', 'input_thresh', 'target_offset'):
        if not math.isfinite(float(obj[key])):
            raise ValueError(f'Non-finite loudness: {path} {key}')
    return obj


def encode(raw, ogg, target):
    """Linear loudness gain, peak-limited only when the target would clip."""
    measured = measure(raw, target, raw=True)
    gain = target - float(measured['input_i'])
    if float(measured['input_tp']) + gain > TRUE_PEAK:
        gain = TRUE_PEAK - float(measured['input_tp'])
    filters = f'volume={gain:.3f}dB'
    if float(measured['input_tp']) + gain > TRUE_PEAK:
        filters += ',alimiter=limit=0.891251:level=false:latency=true'
    run('ffmpeg', '-v', 'error', '-nostdin', '-y', '-f', 'f32le', '-ar', str(RATE), '-ac', '1',
        '-i', str(raw), '-af', filters, '-c:a', 'libvorbis', '-q:a', '5', '-map_metadata', '-1', str(ogg))
    return measured, gain


def pcm_hash(samples):
    return hashlib.sha256(samples.tobytes()).hexdigest()


def seam_metrics(samples, window=SEAM_WINDOW):
    """Wrap jump of a loop and its ratio to the mean |derivative| at the loop start."""
    if len(samples) < 2:
        raise ValueError('Loop too short for a seam check')
    delta = abs(samples[0] - samples[-1])
    n = min(len(samples) - 1, window)
    slope = sum(abs(samples[i + 1] - samples[i]) for i in range(n)) / n
    return delta, delta / max(1e-12, slope)


def check_seam(path, samples):
    """Reject a decoded loop whose wrap is a click before any success is reported."""
    delta, ratio = seam_metrics(samples)
    if delta > SEAM_MAX_DELTA or ratio > SEAM_MAX_RATIO:
        raise ValueError(f'Decoded loop seam fails: {path}: delta {delta:.5f} (max {SEAM_MAX_DELTA}), '
                         f'ratio {ratio:.2f} (max {SEAM_MAX_RATIO})')
    return delta, ratio


def inspect(path, recipe):
    info = json.loads(run('ffprobe', '-v', 'error', '-show_entries',
                          'stream=codec_name,sample_rate,channels:format=duration', '-of', 'json',
                          str(path)).stdout)
    stream = info['streams'][0]
    if (stream['codec_name'], stream['sample_rate'], stream['channels']) != ('vorbis', '44100', 1):
        raise ValueError(f'Wrong output format: {path}')
    duration = float(info['format']['duration'])
    if abs(duration - recipe['duration']) > .03:
        raise ValueError(f'Wrong duration: {path}: {duration}')
    loud = measure(path, recipe['target'])
    data = run('ffmpeg', '-v', 'error', '-nostdin', '-i', str(path), '-ac', '1', '-ar', str(RATE),
               '-f', 'f32le', '-').stdout
    samples = array.array('f')
    samples.frombytes(data)
    peak = max(abs(x) for x in samples)
    sample_db = 20 * math.log10(max(peak, 1e-12))
    if sample_db > -1 or float(loud['input_tp']) > -1:
        raise ValueError(f'Decoded peak exceeds -1 dB ceiling: {path}')
    if abs(float(loud['input_i']) - recipe['target']) > 1:
        raise ValueError(f'Decoded LUFS outside +/-1 target: {path}: {loud["input_i"]}')
    seam_delta, seam_ratio = check_seam(path, samples) if recipe['loop'] else (None, None)
    return dict(duration=duration, codec='vorbis', rate=44100, channels=1,
                integrated_lufs=float(loud['input_i']), true_peak_dbtp=float(loud['input_tp']),
                sample_peak_dbfs=round(sample_db, 3), target_lufs=recipe['target'],
                pcm_sha256=hashlib.sha256(data).hexdigest(),
                seam_delta=seam_delta, seam_ratio=seam_ratio)


def build(src, destination):
    raw_bytes = src.read_bytes()
    if hashlib.sha256(raw_bytes).hexdigest() != SOURCE_SHA256:
        raise ValueError(f'Source digest mismatch: {src}')
    pcm = read_pcm(src)
    hum = build_loop(pcm)
    pulse = build_pulse(pcm)
    impact = saturate(build_impact(pcm), drive=4.0, ceiling=0.8)
    recipes = {
        'world_cut_hum': dict(samples=hum, duration=len(hum) / RATE,
                              target=TARGETS['world_cut_hum'], loop=True),
        'world_cut_pulse': dict(samples=pulse, duration=len(pulse) / RATE,
                                target=TARGETS['world_cut_pulse'], loop=False),
        'world_cut_impact': dict(samples=impact, duration=len(impact) / RATE,
                                 target=TARGETS['world_cut_impact'], loop=False),
    }
    destination.mkdir(parents=True, exist_ok=True)
    report = {}
    for name, recipe in recipes.items():
        raw = destination / (name + '.f32')
        write_raw(raw, recipe['samples'])
        measured, gain = encode(raw, destination / (name + '.ogg'), recipe['target'])
        report[name] = dict(recipe=dict(duration=recipe['duration'], target=recipe['target'],
                                        loop=recipe['loop']),
                            first_pass=measured, applied_gain_db=round(gain, 3),
                            output=inspect(destination / (name + '.ogg'), recipe))
        raw.unlink()
    return report


def compare_pcm(committed, rebuilt, names):
    for name in names:
        encoded = committed / (name + '.ogg.b64')
        if not encoded.is_file():
            raise ValueError(f'Missing committed sound: {encoded}')
        old = rebuilt / (name + '.committed.ogg')
        try:
            old.write_bytes(base64.b64decode(''.join(encoded.read_text().split()), validate=True))
            if pcm_hash(read_pcm(old, 0)) != pcm_hash(read_pcm(rebuilt / (name + '.ogg'), 0)):
                raise ValueError(f'PCM mismatch: {name}')
        finally:
            old.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--src', type=Path, required=True, help='owner-provided 01-original-final-tail.ogg')
    parser.add_argument('--check', action='store_true', help='rebuild and compare decoded PCM; do not write assets')
    parser.add_argument('--out', type=Path, default=OUT, help='b64 destination (default: binassets)')
    parser.add_argument('--report', type=Path, help='write file-only measurements JSON')
    args = parser.parse_args()
    try:
        with tempfile.TemporaryDirectory(prefix='reinhard-worldcut-') as td:
            temporary = Path(td)
            report = build(args.src, temporary)
            if args.check:
                compare_pcm(args.out, temporary, report)
            else:
                args.out.mkdir(parents=True, exist_ok=True)
                for name in report:
                    (args.out / (name + '.ogg.b64')).write_bytes(
                        base64.encodebytes((temporary / (name + '.ogg')).read_bytes()))
            if args.report:
                args.report.write_text(json.dumps(dict(
                    file_only_not_runtime=True,
                    source=dict(name=args.src.name, sha256=SOURCE_SHA256),
                    method=dict(seed_start_s=SEED_START, seed_seconds=SEED_SECONDS,
                                loop='forward + time reverse, exact seam, 8 ms circular smoothing',
                                pulse=f'pitch {PULSE_PITCH} (slower read), {PULSE_ATTACK}s attack, '
                                      f'(1-u)^{PULSE_RELEASE} release over {PULSE_LENGTH}s',
                                impact=f'pitch {IMPACT_PITCH}, {IMPACT_LENGTH}s decay',
                                hum='loop saturated: tanh drive 2.0, ceiling 0.8'),
                    ffmpeg=run('ffmpeg', '-version').stdout.decode().splitlines()[0],
                    sounds=report), indent=2) + '\n')
        print(f'{"CHECK PASS" if args.check else "BUILT"}: {len(report)} sounds; '
              'seam and loudness gates passed; no listening/runtime proof')
    except (ValueError, OSError) as error:
        parser.exit(1, str(error) + '\n')


if __name__ == '__main__':
    main()
