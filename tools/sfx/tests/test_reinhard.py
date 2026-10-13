"""Run from repository root: python3 -m unittest discover -s tools/sfx/tests -v."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / 'tools/sfx/reinhard.py'

class ReinhardConverterTest(unittest.TestCase):
    def module(self):
        self.assertTrue(SCRIPT.is_file(), 'reproducible converter does not exist')
        spec = importlib.util.spec_from_file_location('reinhard_sfx', SCRIPT)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        return mod

    def test_fourteen_allowed_outputs(self):
        m = self.module()
        self.assertEqual(14, len(m.RECIPES))
        self.assertNotIn('world_cut_blast', m.RECIPES)
        self.assertNotIn('cut_rain', m.RECIPES)
        self.assertFalse({'93835', '378199', '825864'} & {r['source'] for r in m.RECIPES.values()})

    def test_licensed_input_digest_is_pinned(self):
        m = self.module()
        self.assertEqual(11, len(m.SOURCES))
        for name, item in m.SOURCES.items():
            self.assertEqual(64, len(item['sha256']), name)
            self.assertIn(item['license'], ('CC0-1.0', 'CC-BY-4.0'))

    def test_class_targets_and_fades(self):
        m = self.module()
        self.assertEqual({'impact': -14, 'bed': -20, 'ui': -18}, m.TARGETS)
        for r in m.RECIPES.values():
            self.assertGreater(r['duration'], 0)
            self.assertGreater(r['fade_out'], 0)
        for n in ('mode_restrained', 'mode_knight', 'mode_holy'):
            self.assertEqual(.03, m.RECIPES[n]['fade_in'])
            self.assertEqual(.4, m.RECIPES[n]['fade_out'])

    def test_heartbeat_fits_unchanged_24_tick_pulse(self):
        m = self.module()
        self.assertEqual(1.2, m.RECIPES['limbo_heartbeat']['duration'])
        self.assertTrue(m.RECIPES['limbo_heartbeat']['loop'])
        self.assertTrue(m.RECIPES['reid_rumble']['loop'])

    def test_missing_inputs_fail_without_writing(self):
        m = self.module()
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(ValueError, 'Missing source'):
                m.validate_sources(Path(td))
            self.assertEqual([], list(Path(td).iterdir()))

    def test_changed_input_rejected(self):
        m = self.module()
        with tempfile.TemporaryDirectory() as td:
            for s in m.SOURCES.values():
                (Path(td) / s['file']).write_bytes(b'not the approved source')
            with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
                m.validate_sources(Path(td))

    def test_help_is_independent_of_inputs(self):
        self.module()
        result = subprocess.run(['python3', str(SCRIPT), '--help'], capture_output=True, text=True)
        self.assertEqual(0, result.returncode)
        self.assertIn('--check', result.stdout)
        self.assertIn('--src', result.stdout)

    def test_committed_files_meet_format_loudness_and_peaks(self):
        m = self.module()
        import base64
        with tempfile.TemporaryDirectory() as td:
            for name, recipe in m.RECIPES.items():
                encoded = m.OUT / (name + '.ogg.b64')
                self.assertTrue(encoded.is_file(), name + ' missing after converter gate')
                path = Path(td) / (name + '.ogg')
                path.write_bytes(base64.b64decode(encoded.read_bytes()))
                measured = m.inspect(path, recipe)
                self.assertLessEqual(measured['true_peak_dbtp'], -1)
                self.assertLessEqual(measured['sample_peak_dbfs'], -1)
                self.assertAlmostEqual(recipe['duration'], measured['duration'], delta=.03)

    def test_check_detects_missing_committed_file(self):
        m = self.module()
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(ValueError, 'Missing committed'):
                m.compare_pcm(Path(td), Path(td), ['mode_restrained'])

if __name__ == '__main__':
    unittest.main()
