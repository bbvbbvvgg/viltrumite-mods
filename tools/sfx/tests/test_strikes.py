"""Run from repository root: python3 -m unittest discover -s tools/sfx/tests -v."""
import importlib.util
import json
import math
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / 'tools/sfx/reinhard_strikes.py'
ASSETS = ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/reinhard'
SOUNDS = ROOT / 'viltrumitecore/src/main/resources/assets/viltrumitecore/sounds.json'


@unittest.skipUnless(importlib.util.find_spec('numpy'), 'numpy is needed by tools/sfx/reinhard_strikes.py')
class StrikeCuesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('reinhard_strikes', SCRIPT)
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def test_every_layer_uses_a_declared_cc0_source(self):
        used = {layer['src'] for layers, _ in self.mod.CUES.values() for layer in layers} | {self.mod.HUM['src']}
        self.assertEqual(set(self.mod.SOURCES), used, 'no undeclared and no unused source')
        for key, (uuid, freesound, creator, title) in self.mod.SOURCES.items():
            self.assertRegex(uuid, r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', key)
            self.assertTrue(creator and title, key)

    def test_outputs_are_the_registered_variants(self):
        sounds = json.loads(SOUNDS.read_text(encoding='utf-8'))
        registered = set()
        for cls in {self.mod.cue_class(name) for name in self.mod.OUTPUTS}:
            entries = sounds['reinhard.' + cls]['sounds']
            registered |= {entry['name'].split('/', 1)[1] for entry in entries}
        self.assertEqual(set(self.mod.OUTPUTS), registered)
        for name in self.mod.OUTPUTS:
            self.assertTrue((ASSETS / (name + '.ogg.b64')).is_file(), name)
            self.assertIn(self.mod.cue_class(name), self.mod.CLASSES, name)

    def test_sources_are_documented_with_page_license_and_openverse_id(self):
        sources = (ROOT / 'tools/assets/reinhard_sources.md').read_text(encoding='utf-8')
        credits = (ROOT / 'CREDITS.md').read_text(encoding='utf-8')
        for key, (uuid, freesound, creator, title) in self.mod.SOURCES.items():
            page = f'https://freesound.org/people/{creator}/sounds/{freesound}'
            row = next((line for line in sources.splitlines() if line.startswith(f'| [{freesound}]({page})')), '')
            self.assertIn(uuid, row, key)
            self.assertIn(f'| {title} | {creator} |', row, key)
            self.assertIn('https://creativecommons.org/publicdomain/zero/1.0/', row, key)
            self.assertIn(page + '/', credits, key)

    def test_trigger_alignment_is_short(self):
        # Hits are played on the contact tick: no layer may start late, and each cue fits its class.
        for name, (layers, length) in self.mod.CUES.items():
            self.assertTrue(all(layer['delay'] <= 0.03 for layer in layers), name)
            self.assertLessEqual(length, 1.6, name)

    def test_variants_of_a_class_share_one_loudness_under_the_ceiling(self):
        np = __import__('numpy')
        sr = self.mod.SR
        t = np.arange(int(0.3 * sr)) / sr
        burst = np.exp(-t / 0.04) * np.sin(2 * math.pi * 180 * t)
        cues = {'fist_hit_1': burst * 0.5, 'fist_hit_2': burst * 0.1, 'fist_hit_3': np.roll(burst, 400) * 0.3}
        gains = self.mod.level(cues)
        _, ceiling, allowance = self.mod.CLASSES['fist_hit']
        loud = [self.mod.momentary_max(x * 10 ** (gains[n] / 20)) for n, x in cues.items()]
        self.assertLess(max(loud) - min(loud), 0.05)
        for n, x in cues.items():
            self.assertLessEqual(self.mod.true_peak(x * 10 ** (gains[n] / 20)), ceiling + allowance + 1e-6, n)

    def test_hum_is_one_seamless_loop(self):
        np = __import__('numpy')
        sr = self.mod.SR
        t = np.arange(int(2.2 * sr)) / sr
        ring = np.exp(-t / 0.8) * (np.sin(2 * math.pi * 148 * t) + 0.2 * np.sin(2 * math.pi * 400 * t))
        original = self.mod.render_layer
        self.mod.render_layer = lambda spec, raw: ring[:int(spec['length'] * sr)].copy()
        try:
            x = self.mod.hum('unused')
        finally:
            self.mod.render_layer = original
        self.assertEqual(int(round(self.mod.HUM['loop'] * sr)), len(x))
        steps = np.abs(np.diff(x))
        self.assertLess(abs(x[0] - x[-1]), np.percentile(steps, 99), 'the wrap is no larger than an ordinary step')
        spectrum = np.abs(np.fft.rfft(x))
        self.assertAlmostEqual(148, np.argmax(spectrum) * sr / len(x), delta=2, msg='the ring colour survives the freeze')


if __name__ == '__main__':
    unittest.main()
