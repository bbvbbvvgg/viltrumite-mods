"""Run from repository root: python3 -m unittest discover -s tools/sfx/tests -v."""
import base64
import importlib.util
import json
import shutil
import subprocess
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / 'tools/sfx/reinhard_explosion.py'
ASSET = ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/reinhard/world_cut_explosion.ogg.b64'
SOUNDS = ROOT / 'viltrumitecore/src/main/resources/assets/viltrumitecore/sounds.json'


@unittest.skipUnless(importlib.util.find_spec('numpy'), 'numpy is needed by tools/sfx/reinhard_explosion.py')
class ExplosionCueTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('reinhard_explosion', SCRIPT)
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)
        cls.raw = cls.mod.synth()

    def test_synthesis_is_deterministic(self):
        np = __import__('numpy')
        self.assertTrue(np.array_equal(self.raw, self.mod.synth()))
        self.assertEqual(int(round(self.mod.LENGTH * self.mod.SR)), len(self.raw))

    def test_boom_lands_at_once_and_decays_to_silence(self):
        np = __import__('numpy')
        sr = self.mod.SR
        x = self.raw
        w = int(0.1 * sr)
        rms = [np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, len(x) - w, w)]
        self.assertLessEqual(int(np.argmax(rms)), 1, 'the loudest 100 ms is the first or second')
        self.assertLess(20 * np.log10(rms[-1] / max(rms)), -40, 'decays to silence, no cut-off tail')
        self.assertLess(abs(x[0]), 1e-3, 'no click at the start')
        self.assertLess(abs(x[-1]), 1e-4, 'no click at the end')

    def test_low_boom_with_an_audible_attack(self):
        np = __import__('numpy')
        sr = self.mod.SR
        first = self.raw[:sr]
        power = np.abs(np.fft.rfft(first)) ** 2
        freq = np.fft.rfftfreq(len(first), 1 / sr)
        self.assertGreater(power[freq < 160].sum() / power.sum(), 0.6, 'a low boom')
        attack = self.raw[:int(0.3 * sr)]
        power = np.abs(np.fft.rfft(attack)) ** 2
        freq = np.fft.rfftfreq(len(attack), 1 / sr)
        mids = power[(freq >= 160) & (freq < 2560)].sum() / power.sum()
        self.assertGreater(mids, 0.15, 'an attack that small speakers can play')

    def test_master_is_level_set_under_the_ceiling(self):
        x = self.mod.master(self.raw)
        self.assertLessEqual(self.mod.meter.true_peak(x), self.mod.TRUE_PEAK + 0.05)
        self.assertGreater(self.mod.meter.momentary_max(x), self.mod.TARGET_M100 - self.mod.MAX_LIMIT - 0.5)

    def test_registered_positional_mono_event(self):
        entry = json.loads(SOUNDS.read_text(encoding='utf-8'))['reinhard.world_cut_explosion']
        self.assertEqual([{'name': 'viltrumitecore:reinhard/world_cut_explosion', 'stream': False,
                           'attenuation_distance': 320}], entry['sounds'])
        self.assertEqual('subtitles.viltrumitecore.reinhard.world_cut_explosion', entry['subtitle'])

    @unittest.skipUnless(shutil.which('ffprobe'), 'ffprobe is needed to inspect the committed asset')
    def test_committed_asset_is_mono_vorbis_of_the_script(self):
        data = base64.decodebytes(ASSET.read_bytes())
        probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'stream=codec_name,channels,sample_rate',
                                '-of', 'json', '-'], input=data, capture_output=True, check=True)
        stream = json.loads(probe.stdout)['streams'][0]
        self.assertEqual(('vorbis', 1, '44100'), (stream['codec_name'], stream['channels'], stream['sample_rate']))
        np = __import__('numpy')
        x = self.mod.master(self.raw)
        y = self.mod.decode_asset()
        n = min(len(x), len(y))
        corr = float(np.dot(x[:n], y[:n]) / (np.linalg.norm(x[:n]) * np.linalg.norm(y[:n])))
        self.assertGreater(corr, 0.95)


if __name__ == '__main__':
    unittest.main()
