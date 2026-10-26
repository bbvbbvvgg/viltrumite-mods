"""Run from repository root: python3 -m unittest discover -s tools/sfx/tests -v."""
import base64
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / 'tools/sfx/reinhard_draw.py'
ASSETS = ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/reinhard'
SOUNDS = ROOT / 'viltrumitecore/src/main/resources/assets/viltrumitecore/sounds.json'
LANG = ROOT / 'viltrumitecore/src/main/resources/assets/viltrumitecore/lang/en_us.json'
JAVA = ROOT / 'viltrumitecore/src/main/java/dev/baranhan/viltrumitecore'
AUDIO = JAVA / 'client/reinhard/reid/ReidDrawAudio.java'
CHOREO = JAVA / 'client/reinhard/ReidDrawChoreo.java'
RULES = JAVA / 'hero/reinhard/ReinhardRules.java'
# Committed cue lengths (s): the hum outlasts the release, the air is cut exactly on it.
LENGTHS = {'reid_draw_hum': 6.3, 'reid_draw_koiguchi': 0.6, 'reid_draw_scrape': 3.8, 'reid_draw_air': 2.0, 'reid_draw_release': 4.8}


def rule(name):
    return int(re.search(rf'int {name} = (\d+);', RULES.read_text(encoding='utf-8')).group(1))


def java_float(expr, names):
    expr = re.sub(r'(?<=[0-9.])[Ff]\b', '', expr.strip())
    for key, value in names.items():
        expr = re.sub(rf'\b{key}\b', repr(value), expr)
    return float(eval(expr, {'__builtins__': {}}))  # numbers and + - only, from our own source


@unittest.skipUnless(importlib.util.find_spec('numpy'), 'numpy is needed by tools/sfx/reinhard_draw.py')
class DrawCuesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('reinhard_draw', SCRIPT)
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def test_every_recording_is_a_declared_used_cc0_source(self):
        used = {uuid for name in self.mod.OUTPUTS for uuid in self.mod.sources_of(name)}
        self.assertEqual({uuid for uuid, *_ in self.mod.SOURCES.values()}, used, 'no undeclared and no unused source')
        for key, (uuid, freesound, creator, title) in self.mod.SOURCES.items():
            self.assertRegex(uuid, r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', key)
            self.assertTrue(creator and title and freesound, key)
        self.assertEqual([], self.mod.sources_of('reid_draw_hum'), 'the hum is own synthesis')
        self.assertEqual([], self.mod.sources_of('reid_draw_air'), 'the air is own synthesis')

    def test_sources_are_documented_with_page_license_and_openverse_id(self):
        sources = (ROOT / 'tools/assets/reinhard_sources.md').read_text(encoding='utf-8')
        section = sources[sources.index('## REINHARD OVERHAUL draw sounds'):sources.index('## TEMPORARY test asset')]
        credits = (ROOT / 'CREDITS.md').read_text(encoding='utf-8')
        for key, (uuid, freesound, creator, title) in self.mod.SOURCES.items():
            page = f'https://freesound.org/people/{creator}/sounds/{freesound}'
            row = next((line for line in section.splitlines() if line.startswith(f'| [{freesound}]({page})')), '')
            self.assertIn(uuid, row, key)
            self.assertIn(f'| {title} | {creator} |', row, key)
            self.assertIn('https://creativecommons.org/publicdomain/zero/1.0/', row, key)
            self.assertIn(page + '/', credits, key)
        for name in self.mod.OUTPUTS:
            self.assertIn(name, section, name)
        self.assertIn('not a listening test', section)

    def test_the_mix_is_on_the_java_draw_timeline(self):
        choreo = CHOREO.read_text(encoding='utf-8')
        consts = {k: int(v) for k, v in re.findall(r'\b(KOIGUCHI|STRAIN|SNAP) = (\d+)', choreo)}
        self.assertEqual((consts['KOIGUCHI'], consts['STRAIN'], consts['SNAP'], rule('REID_RELEASE_TICK'), rule('REID_DRAW_TICKS')),
                         (self.mod.KOIGUCHI, self.mod.STRAIN, self.mod.SNAP, self.mod.RELEASE, self.mod.LENGTH))
        mouth = java_float(re.search(r'float MOUTH = ([^;]+);', choreo).group(1), {})
        names = {'RELEASE': self.mod.RELEASE, 'MOUTH': mouth}
        names['FULL'] = java_float(re.search(r'float FULL = ([^;]+);', choreo).group(1), names)
        s_t = [java_float(v, names) for v in re.search(r'double\[\] S_T = \{([^}]+)\}', choreo).group(1).split(',')]
        s_v = [java_float(v, names) for v in re.search(r'double\[\] S_V = \{([^}]+)\}', choreo).group(1).split(',')]
        self.assertEqual(s_t, [float(v) for v in self.mod.S_T], 'extraction keys (ticks)')
        for a, b in zip(s_v, self.mod.S_V):
            self.assertAlmostEqual(a, b, places=4, msg='extraction keys (px)')

    def test_cues_start_where_the_client_starts_them(self):
        audio = AUDIO.read_text(encoding='utf-8')
        java = {}
        for name, start in re.findall(r'new Cue\("(reid_draw_\w+)", ([\w.]+),', audio):
            java[name] = rule(start.split('.')[-1]) if not start.isdigit() else int(start)
        self.assertEqual(list(self.mod.CUES), list(java), 'same cues, same order')
        for name, spec in self.mod.CUES.items():
            self.assertEqual(spec['start'], java[name], name)
        self.assertEqual(self.mod.RELEASE, self.mod.CUES['reid_draw_release']['start'])

    def test_cues_are_registered_with_their_distance_and_subtitle(self):
        sounds = json.loads(SOUNDS.read_text(encoding='utf-8'))
        lang = json.loads(LANG.read_text(encoding='utf-8'))
        for name, spec in self.mod.CUES.items():
            event = sounds['reinhard.' + name]
            self.assertEqual('player', event['category'], name)
            self.assertIn(event['subtitle'], lang, name)
            self.assertEqual([{'name': 'viltrumitecore:reinhard/' + name, 'attenuation_distance': spec['distance']}], event['sounds'], name)
            self.assertTrue((ASSETS / (name + '.ogg.b64')).is_file(), name)

    def test_own_synthesis_is_deterministic_and_on_its_phases(self):
        np = __import__('numpy')
        sr, ticks = self.mod.SR, self.mod.TICKS
        hum, air = self.mod.hum(), self.mod.air()
        self.assertTrue(np.array_equal(hum, self.mod.hum()), 'fixed seed')
        self.assertEqual(int(LENGTHS['reid_draw_hum'] * sr), len(hum))
        air_start = self.mod.CUES['reid_draw_air']['start']
        self.assertEqual(int((self.mod.RELEASE - air_start) / ticks * sr), len(air), 'the air ends on the release')

        def rms(x, cue, d0, d1):
            start = self.mod.CUES[cue]['start']
            a, b = int((d0 - start) / ticks * sr), int((d1 - start) / ticks * sr)
            return float(np.sqrt(np.mean(x[a:b] ** 2)))

        early, pull, held = rms(hum, 'reid_draw_hum', 10, 20), rms(hum, 'reid_draw_hum', 70, 80), rms(hum, 'reid_draw_hum', 93, 95.5)
        swell, tail = rms(hum, 'reid_draw_hum', 96.5, 100), rms(hum, 'reid_draw_hum', 125, 130)
        self.assertGreater(pull, 2 * early, 'the hum rises with the pull')
        self.assertLess(held, pull, 'it holds its breath before the release')
        self.assertGreater(swell, held, 'and swells at the release')
        self.assertLess(tail, swell / 3, 'then decays')
        self.assertGreater(rms(air, 'reid_draw_air', 90, 95), 2 * rms(air, 'reid_draw_air', 57, 62), 'the air vibrates harder towards the release')

    @unittest.skipUnless(shutil.which('ffmpeg'), 'ffmpeg decodes the committed cues')
    def test_committed_cues_have_their_length_and_their_moment(self):
        np = __import__('numpy')
        sr = self.mod.SR
        decoded = {}
        with tempfile.TemporaryDirectory() as tmp:
            for name in self.mod.OUTPUTS:
                ogg = Path(tmp) / (name + '.ogg')
                ogg.write_bytes(base64.b64decode((ASSETS / (name + '.ogg.b64')).read_text(encoding='ascii')))
                pcm = subprocess.run(['ffmpeg', '-v', 'error', '-nostdin', '-i', str(ogg), '-ac', '1', '-ar', str(sr), '-f', 'f32le', '-'],
                                     capture_output=True, check=True).stdout
                decoded[name] = np.frombuffer(pcm, dtype=np.float32).astype(np.float64)
        for name, x in decoded.items():
            self.assertAlmostEqual(LENGTHS[name], len(x) / sr, delta=0.03, msg=name)

        def envelope(x):
            w = int(0.005 * sr)
            return np.sqrt(np.convolve(x ** 2, np.ones(w) / w, mode='same'))

        def onset(x):
            env = envelope(x)
            return float(np.argmax(env > 0.1 * env.max())) / sr

        def loudest(x):
            return float(np.argmax(envelope(x))) / sr

        self.assertAlmostEqual(0.05, onset(decoded['reid_draw_koiguchi']), delta=0.015, msg='the mouth breaks on d34')
        self.assertLess(loudest(decoded['reid_draw_koiguchi']), 0.15, 'a clack, not a swell')
        self.assertAlmostEqual(3.0, loudest(decoded['reid_draw_scrape']), delta=0.05, msg='the exit lands on the release')
        self.assertLess(onset(decoded['reid_draw_release']), 0.01, 'the final accent hits on the release tick')
        self.assertLess(loudest(decoded['reid_draw_release']), 0.25, 'its peak right after, then the decay')


if __name__ == '__main__':
    unittest.main()
