#!/usr/bin/env python3
"""Reinhard CC0/CC-BY recordings: deterministic trim/fade + two-pass loudnorm.

Usage: python3 tools/sfx/reinhard.py --src /path/to/unpacked/sounds [--check]
Requires ffmpeg/ffprobe with libvorbis. Raw input never enters the repository.
Only .ogg.b64 is installed via the existing Gradle decodeBinaryAssets pipeline.
--check rebuilds in a temporary directory and compares decoded PCM, not Ogg bytes.
--report PATH optionally records file measurements (NOT listening/runtime proof).
Offsets are objective initial edits, not ear-selected/accepted performances.
NC inputs 93835 and 378199 and their blocked mix are intentionally absent.
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
TARGETS = {'impact': -14, 'bed': -20, 'ui': -18}
# -2 dBTP at normalization leaves headroom for lossy Vorbis overshoot; decoded gate is -1 dBTP.
TRUE_PEAK = -2

SOURCES = {'398218': {'file': '398218__klankbeeld__st-john-cathedral-female-choir-001.wav',
            'sha256': '303b75361ae6ae2f407f3661bf4bdb498afbe39ab4e3d02c3c97cdeb365807be',
            'license': 'CC-BY-4.0'},
 '770035': {'file': '770035__vrymaa__sword-unsheathe.wav',
            'sha256': 'cadfdd8d7427fff68415b482d27fe714cfd86d60d2a83acf8d4200e5dd895e26',
            'license': 'CC0-1.0'},
 '870684': {'file': '870684__challsmith__very-low-rumble-stereo.wav',
            'sha256': 'fb3d74efcb82021a397dbf07649ce409e53dd1b53b8e16cd9532fa47c76c2926',
            'license': 'CC0-1.0'},
 '503809': {'file': '503809__reathance__reverse-fx-6.wav',
            'sha256': 'bf2a3a7d7df281130e36776b445d2a0627cf41c9a91c0fd196e85fabe868d278',
            'license': 'CC0-1.0'},
 '248150': {'file': '248150__hykenfreak__deepest-boom-yet.wav',
            'sha256': '8a95a5fabb53a3140371b8b6edef304ed75598672f4d4f1e1b565c773c866e5c',
            'license': 'CC-BY-4.0'},
 '554564': {'file': '554564__greg_surr__glass-shatter-2.wav',
            'sha256': '79b099b8a8cdfa20e85d3097d2a0f7d2a0d3592f18cc59e7d968c84d810577cf',
            'license': 'CC0-1.0'},
 '331620': {'file': '331620__hykenfreak__spooky-sucking-air.wav',
            'sha256': '7f40dff2eb2bab8cc0eda07b9d37b270fed09e29038cae39147e1b26874ffa7a',
            'license': 'CC-BY-4.0'},
 '249716': {'file': '249716__daandraait__slow-heartbeat.wav',
            'sha256': '1ed32edb9121a482e25ad2a606047a2efee40e6682d13a28539a80585c56d134',
            'license': 'CC0-1.0'},
 '182050': {'file': '182050__qubodup__sonic-boom.flac',
            'sha256': '68e00321c8f0219078b4b9737a561fcd1316c67800a031828df1bd926a74e06f',
            'license': 'CC0-1.0'},
 '275809': {'file': '275809__dingus23__earthquake.aiff',
            'sha256': '60ab71881cdb214c09a624cdee22ada76624c3fce1ff3af061174596148888aa',
            'license': 'CC-BY-4.0'},
 '519413': {'file': '519413__iridiuss__sword-blade-ring-and-scrape.m4a',
            'sha256': '2cbf59f58b0f0e516fd80f876df184e31c3d274ebd65a9577f760fbb03e972da',
            'license': 'CC0-1.0'}}

# Output → approved source + objective edits + pinned first-pass measurements.
RECIPES = {'mode_restrained': {'source': '398218',
                     'start': 10,
                     'duration': 1.8,
                     'kind': 'ui',
                     'fade_in': 0.03,
                     'fade_out': 0.4,
                     'loop': False,
                     'measured': {'input_i': '-25.68',
                                  'input_tp': '-12.28',
                                  'input_lra': '0.00',
                                  'input_thresh': '-35.68',
                                  'target_offset': '-0.01'}},
 'mode_knight': {'source': '398218',
                 'start': 20,
                 'duration': 2,
                 'kind': 'ui',
                 'fade_in': 0.03,
                 'fade_out': 0.4,
                 'loop': False,
                 'measured': {'input_i': '-33.37',
                              'input_tp': '-20.58',
                              'input_lra': '0.00',
                              'input_thresh': '-43.37',
                              'target_offset': '-0.01'}},
 'mode_holy': {'source': '398218',
               'start': 30,
               'duration': 2.2,
               'kind': 'ui',
               'fade_in': 0.03,
               'fade_out': 0.4,
               'loop': False,
               'measured': {'input_i': '-29.15',
                            'input_tp': '-14.53',
                            'input_lra': '0.00',
                            'input_thresh': '-39.15',
                            'target_offset': '-0.01'}},
 'reid_choir': {'source': '398218',
                'start': 40,
                'duration': 6,
                'kind': 'bed',
                'fade_in': 0.03,
                'fade_out': 1,
                'loop': False,
                'measured': {'input_i': '-27.16',
                             'input_tp': '-11.98',
                             'input_lra': '4.80',
                             'input_thresh': '-37.26',
                             'target_offset': '-2.87'}},
 'reid_unsheathe': {'source': '770035',
                    'start': 0,
                    'duration': 1.879297,
                    'kind': 'impact',
                    'fade_in': 0.01,
                    'fade_out': 0.15,
                    'loop': False,
                    'measured': {'input_i': '-22.24',
                                 'input_tp': '-10.71',
                                 'input_lra': '0.00',
                                 'input_thresh': '-34.56',
                                 'target_offset': '-0.01'}},
 'reid_rumble': {'source': '870684',
                 'start': 10,
                 'duration': 6,
                 'kind': 'bed',
                 'fade_in': 0.04,
                 'fade_out': 0.04,
                 'loop': True,
                 'measured': {'input_i': '-25.32',
                              'input_tp': '-4.65',
                              'input_lra': '0.30',
                              'input_thresh': '-35.32',
                              'target_offset': '0.23'}},
 'world_cut_silence': {'source': '503809',
                       'start': 3,
                       'duration': 3,
                       'kind': 'bed',
                       'fade_in': 0.1,
                       'fade_out': 0.3,
                       'loop': False,
                       'measured': {'input_i': '-45.54',
                                    'input_tp': '-31.55',
                                    'input_lra': '0.20',
                                    'input_thresh': '-56.88',
                                    'target_offset': '-1.66'}},
 'world_cut_tail': {'source': '248150',
                    'start': 0,
                    'duration': 8,
                    'kind': 'bed',
                    'fade_in': 0.01,
                    'fade_out': 1,
                    'loop': False,
                    'measured': {'input_i': '-19.23',
                                 'input_tp': '-2.39',
                                 'input_lra': '26.80',
                                 'input_thresh': '-33.67',
                                 'target_offset': '0.93'}},
 'erase_crack': {'source': '554564',
                 'start': 0,
                 'duration': 1.5,
                 'kind': 'impact',
                 'fade_in': 0.005,
                 'fade_out': 0.2,
                 'loop': False,
                 'measured': {'input_i': '-27.33',
                              'input_tp': '-17.15',
                              'input_lra': '0.00',
                              'input_thresh': '-38.08',
                              'target_offset': '0.01'},
                 'pre_limit': 0.125},
 'erase_inhale': {'source': '331620',
                  'start': 0,
                  'duration': 2.3,
                  'kind': 'bed',
                  'fade_in': 0.02,
                  'fade_out': 0.25,
                  'loop': False,
                  'measured': {'input_i': '-17.05',
                               'input_tp': '1.02',
                               'input_lra': '0.00',
                               'input_thresh': '-29.60',
                               'target_offset': '0.06'}},
 'limbo_heartbeat': {'source': '249716',
                     'start': 0,
                     'duration': 1.2,
                     'kind': 'bed',
                     'fade_in': 0.005,
                     'fade_out': 0.15,
                     'loop': True,
                     'measured': {'input_i': '-27.58',
                                  'input_tp': '-12.04',
                                  'input_lra': '0.00',
                                  'input_thresh': '-38.25',
                                  'target_offset': '0.01'},
                     'pre_limit': 0.25},
 'sky_kick': {'source': '182050',
              'start': 0,
              'duration': 2.5,
              'kind': 'impact',
              'fade_in': 0.005,
              'fade_out': 0.35,
              'loop': False,
              'measured': {'input_i': '-11.06',
                           'input_tp': '0.31',
                           'input_lra': '0.00',
                           'input_thresh': '-21.93',
                           'target_offset': '-0.01'}},
 'stomp': {'source': '275809',
           'start': 0,
           'duration': 3,
           'kind': 'impact',
           'fade_in': 0.005,
           'fade_out': 0.4,
           'loop': False,
           'measured': {'input_i': '-27.74',
                        'input_tp': '-18.06',
                        'input_lra': '0.00',
                        'input_thresh': '-37.74',
                        'target_offset': '-0.22'},
           'pre_limit': 0.125},
 'sheath_ring': {'source': '519413',
                 'start': 2.31,
                 'duration': 1.1,
                 'kind': 'impact',
                 'fade_in': 0.01,
                 'fade_out': 0.2,
                 'loop': False,
                 'measured': {'input_i': '-13.88',
                              'input_tp': '-1.96',
                              'input_lra': '0.00',
                              'input_thresh': '-24.43',
                              'target_offset': '-0.02'},
                 'pre_limit': 0.7}}


def run(*args):
    result = subprocess.run(args, capture_output=True)
    if result.returncode:
        raise ValueError(result.stderr.decode(errors='replace')[-3000:])
    return result


def validate_sources(folder):
    for item in SOURCES.values():
        path = folder / item['file']
        if not path.is_file():
            raise ValueError(f'Missing source: {path}')
        if hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']:
            raise ValueError(f'SHA-256 mismatch: {path}')


def edit_filter(recipe):
    d = recipe['duration']
    filters = (f"atrim=start={recipe['start']}:duration={d},asetpts=PTS-STARTPTS,"
            f"aformat=sample_rates=44100:channel_layouts=mono,"
            f"afade=t=in:d={recipe['fade_in']},"
            f"afade=t=out:st={d-recipe['fade_out']}:d={recipe['fade_out']}")
    # Short high-crest recordings otherwise cannot meet both I and TP targets.
    if 'pre_limit' in recipe:
        filters += f",alimiter=limit={recipe['pre_limit']}:level=false:latency=true"
    return filters


def measure(path, target):
    result = run('ffmpeg', '-hide_banner', '-nostdin', '-i', str(path), '-af',
                 f'loudnorm=I={target}:TP={TRUE_PEAK}:LRA=11:print_format=json', '-f', 'null', '-')
    text = result.stderr.decode()
    obj = json.loads(text[text.rfind('{'):text.rfind('}')+1])
    if not all(math.isfinite(float(obj[k])) for k in ('input_i', 'input_tp', 'input_lra', 'input_thresh', 'target_offset')):
        raise ValueError(f'Non-finite loudness (empty/silent cut): {path}')
    return obj


def pcm(path):
    return run('ffmpeg', '-v', 'error', '-nostdin', '-i', str(path), '-ac', '1', '-ar', '44100',
               '-f', 'f32le', '-acodec', 'pcm_f32le', '-').stdout


def pcm_hash(path):
    return hashlib.sha256(pcm(path)).hexdigest()


def inspect(path, recipe):
    info = json.loads(run('ffprobe', '-v', 'error', '-show_entries',
                         'stream=codec_name,sample_rate,channels:format=duration', '-of', 'json', str(path)).stdout)
    stream = info['streams'][0]
    if (stream['codec_name'], stream['sample_rate'], stream['channels']) != ('vorbis', '44100', 1):
        raise ValueError(f'Wrong output format: {path}')
    duration = float(info['format']['duration'])
    if abs(duration - recipe['duration']) > .03:
        raise ValueError(f'Wrong duration: {path}: {duration}')
    loud = measure(path, TARGETS[recipe['kind']])
    data = pcm(path)
    samples = array.array('f'); samples.frombytes(data)
    peak = max(abs(x) for x in samples)
    sample_db = 20 * math.log10(max(peak, 1e-12))
    if sample_db > -1 or float(loud['input_tp']) > -1:
        raise ValueError(f'Decoded peak exceeds -1 dB ceiling: {path}')
    if abs(float(loud['input_i']) - TARGETS[recipe['kind']]) > 1:
        raise ValueError(f'Decoded LUFS outside ±1 target: {path}: {loud["input_i"]}')
    return dict(duration=duration, codec='vorbis', rate=44100, channels=1,
                integrated_lufs=float(loud['input_i']), true_peak_dbtp=float(loud['input_tp']),
                sample_peak_dbfs=round(sample_db, 3), target_lufs=TARGETS[recipe['kind']],
                pcm_sha256=hashlib.sha256(data).hexdigest(),
                seam_delta=abs(samples[0]-samples[-1]) if recipe['loop'] else None,
                loop_endpoints=max(abs(samples[0]), abs(samples[-1])) if recipe['loop'] else None)


def build(folder, destination):
    validate_sources(folder)
    destination.mkdir(parents=True, exist_ok=True)
    report = {}
    for name, recipe in RECIPES.items():
        raw = destination / (name + '.wav')
        target = TARGETS[recipe['kind']]
        run('ffmpeg', '-v', 'error', '-nostdin', '-y', '-i', str(folder / SOURCES[recipe['source']]['file']),
            '-af', edit_filter(recipe), '-acodec', 'pcm_f32le', str(raw))
        measured = measure(raw, target)  # pass 1: never use single-pass loudnorm.
        stored = recipe['measured']
        if stored is None:
            raise ValueError(f'Missing pinned first-pass measurements: {name}')
        for key in ('input_i', 'input_tp', 'input_lra', 'input_thresh', 'target_offset'):
            if abs(float(measured[key])-float(stored[key])) > .1:
                raise ValueError(f'First-pass drift: {name} {key}; remeasure on approved source')
        norm = (f'loudnorm=I={target}:TP={TRUE_PEAK}:LRA=11:linear=true:'
                f'measured_I={stored["input_i"]}:measured_TP={stored["input_tp"]}:'
                f'measured_LRA={stored["input_lra"]}:measured_thresh={stored["input_thresh"]}:'
                f'offset={stored["target_offset"]},aresample=44100,'
                'alimiter=limit=0.891251:level=false:latency=true')
        ogg = destination / (name + '.ogg')
        run('ffmpeg', '-v', 'error', '-nostdin', '-y', '-i', str(raw), '-af', norm,
            '-ac', '1', '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '5', '-map_metadata', '-1', str(ogg))
        report[name] = inspect(ogg, recipe)
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
            if pcm_hash(old) != pcm_hash(rebuilt / (name + '.ogg')):
                raise ValueError(f'PCM mismatch: {name}')
        finally:
            old.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--src', type=Path, required=True, help='unpacked sounds.zip folder')
    parser.add_argument('--check', action='store_true', help='rebuild and compare committed decoded PCM; do not write assets')
    parser.add_argument('--out', type=Path, default=OUT, help='b64 destination (default: binassets)')
    parser.add_argument('--report', type=Path, help='write file-only measurements JSON outside binassets')
    args = parser.parse_args()
    try:
        with tempfile.TemporaryDirectory(prefix='reinhard-sfx-') as td:
            temporary = Path(td)
            report = build(args.src, temporary)
            if args.check:
                compare_pcm(args.out, temporary, RECIPES)
            else:
                args.out.mkdir(parents=True, exist_ok=True)
                for name in RECIPES:
                    (args.out / (name + '.ogg.b64')).write_bytes(base64.encodebytes((temporary / (name + '.ogg')).read_bytes()))
            if args.report:
                args.report.write_text(json.dumps(dict(file_only_not_runtime=True,
                    ffmpeg=run('ffmpeg', '-version').stdout.decode().splitlines()[0], sounds=report), indent=2)+'\n')
        print(f'{"CHECK PASS" if args.check else "BUILT"}: {len(RECIPES)} sounds; blocked blast/rain unchanged; no listening/runtime proof')
    except (ValueError, OSError) as error:
        parser.exit(1, str(error)+'\n')


if __name__ == '__main__':
    main()
