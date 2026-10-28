"""Run from repository root: python3 -m unittest discover -s tools/sfx/tests -v."""
import importlib.util
import json
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / 'tools/sfx/thor.py'
ASSETS = ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/thor'
SOUNDS = ROOT / 'viltrumitecore/src/main/resources/assets/viltrumitecore/sounds.json'


@unittest.skipUnless(importlib.util.find_spec('numpy'), 'numpy is needed by tools/sfx/thor.py')
class ThorKitTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('thor_sfx', SCRIPT)
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def test_file_table_matches_sounds_json_and_assets(self):
        sounds = json.loads(SOUNDS.read_text(encoding='utf-8'))
        used = {}
        for key, entry in sounds.items():
            if key.startswith('thor.'):
                for sound in entry['sounds']:
                    used[sound['name'].split('/', 1)[1]] = key[len('thor.'):]
        self.assertEqual(set(self.mod.FILES), set(used))
        for name, event in used.items():
            self.assertEqual(event, self.mod.event_of(name), name)
        shipped = {p.name[:-len('.ogg.b64')] for p in ASSETS.glob('*.ogg.b64')}
        self.assertEqual(shipped, set(used))

    def test_every_asset_fits_one_push(self):
        # The GitHub connector takes ~64 KB per call; one file must fit with its JSON envelope.
        for path in ASSETS.glob('*.ogg.b64'):
            self.assertLess(path.stat().st_size, 60000, path.name)

    def test_synthesis_is_deterministic(self):
        np = __import__('numpy')
        for name in ('hammer_metal_1', 'thunder_crack_2', 'electric_zap_3'):
            self.assertTrue(np.array_equal(self.mod.synth(name), self.mod.synth(name)), name)

    def test_one_shots_start_and_end_silent(self):
        np = __import__('numpy')
        for name in ('hammer_bass_1', 'thunder_impact_1', 'fist_hit_2', 'hammer_catch'):
            x = self.mod.synth(name)
            peak = float(np.max(np.abs(x)))
            self.assertLess(abs(x[0]), 0.01 * peak, name)
            self.assertLess(abs(x[-1]), 0.01 * peak, name)
            self.assertTrue(np.isfinite(x).all(), name)

    def test_loops_are_seamless(self):
        for name in self.mod.FILES:
            if self.mod.is_loop(name):
                self.assertLess(self.mod.seam(self.mod.synth(name)), 5.0, name)

    def test_layer_names_cover_thunder_and_hammer(self):
        names = set(self.mod.FILES)
        for layer in ('thunder_crack', 'thunder_impact', 'thunder_rumble', 'hammer_metal', 'hammer_earth', 'hammer_bass'):
            self.assertTrue(any(re.fullmatch(layer + r'(_\d+)?', n) for n in names), layer)


if __name__ == '__main__':
    unittest.main()
