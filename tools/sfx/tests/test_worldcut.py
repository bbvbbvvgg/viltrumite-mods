"""Run from repository root: python3 -m unittest discover -s tools/sfx/tests -v."""
import array
import base64
import importlib.util
import math
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / 'tools/sfx/reinhard_worldcut.py'


class WorldCutAudioTest(unittest.TestCase):
    def module(self):
        self.assertTrue(SCRIPT.is_file(), 'reproducible world-cut converter does not exist')
        spec = importlib.util.spec_from_file_location('reinhard_worldcut', SCRIPT)
        mod = importlib.util.module_from_spec(spec)
        exec(compile(SCRIPT.read_bytes(), str(SCRIPT), 'exec'), mod.__dict__)
        return mod

    @staticmethod
    def seed(rate, seconds=3.0):
        return [0.6 * math.exp(-t / 1.1) * math.sin(2 * math.pi * 52 * t)
                + 0.15 * math.sin(2 * math.pi * 31 * t)
                for t in (i / rate for i in range(int(seconds * rate)))]

    @staticmethod
    def spread(samples, rate, window=0.1):
        step = int(window * rate)
        levels = []
        for start in range(0, len(samples) - step + 1, step):
            block = samples[start:start + step]
            levels.append(20 * math.log10(max(math.sqrt(sum(v * v for v in block) / step), 1e-12)))
        return max(levels) - min(levels)

    def test_pinned_source_digest(self):
        mod = self.module()
        self.assertEqual(64, len(mod.SOURCE_SHA256))
        self.assertEqual('8fc6abd552883d1e19afca25e7c589b7e7e6402c64bada75e0cc66857e12dce6',
                         mod.SOURCE_SHA256)

    def test_loop_is_seamless_and_loses_the_decay_jump(self):
        mod = self.module()
        seed = self.seed(mod.RATE)
        loop = mod.build_loop(seed)
        self.assertAlmostEqual(mod.SEED_SECONDS * mod.RATE - mod.SEAM_MS * mod.RATE / 1000,
                               len(loop), delta=2)
        rms = math.sqrt(sum(v * v for v in loop) / len(loop))
        self.assertLess(abs(loop[0] - loop[-1]), rms * .02)
        self.assertLess(self.spread(loop, mod.RATE), self.spread(seed, mod.RATE) * .7)

    def test_pulse_has_a_clean_attack_and_release(self):
        mod = self.module()
        pulse = mod.build_pulse(self.seed(mod.RATE))
        self.assertEqual(int(mod.PULSE_LENGTH * mod.RATE), len(pulse))
        self.assertLess(abs(pulse[0]), 1e-6)
        self.assertLess(abs(pulse[-1]), .01)
        self.assertGreater(max(abs(v) for v in pulse), .05)

    def test_pulse_lowers_a_known_source_frequency(self):
        mod = self.module()
        rate = mod.RATE
        tone = [math.sin(2 * math.pi * 100 * i / rate) for i in range(int(3 * rate))]
        pulse = mod.build_pulse(tone)
        start, stop = int(.05 * rate), int(.45 * rate)
        crossings = sum(1 for i in range(start + 1, stop) if (pulse[i - 1] < 0) != (pulse[i] < 0))
        frequency = crossings / 2 / ((stop - start) / rate)
        self.assertAlmostEqual(100 * mod.PULSE_PITCH, frequency, delta=2)

    def test_impact_is_short_and_clean(self):
        mod = self.module()
        impact = mod.build_impact(self.seed(mod.RATE))
        self.assertEqual(int(mod.IMPACT_LENGTH * mod.RATE), len(impact))
        self.assertLess(abs(impact[0]), 1e-6)
        self.assertLess(abs(impact[-1]), .01)
        self.assertGreater(max(abs(v) for v in impact), .05)

    def test_short_input_is_rejected(self):
        mod = self.module()
        with self.assertRaisesRegex(ValueError, 'does not contain the two-second seed'):
            mod.build_loop([0.0] * 1000)

    @staticmethod
    def wrap_loop(n, step=0.0):
        """Whole-period loop of n samples; `step` lifts the last tenth into a wrap click."""
        return [0.3 * math.sin(2 * math.pi * 100 * i / n) + 0.2 * math.sin(2 * math.pi * 37 * i / n)
                + (step if i > n * .9 else 0.0) for i in range(n)]

    def test_seam_gate_rejects_a_wrap_click(self):
        mod = self.module()
        smooth = self.wrap_loop(65536)
        delta, ratio = mod.check_seam('smooth', smooth)
        self.assertLess(delta, mod.SEAM_MAX_DELTA)
        self.assertLess(ratio, mod.SEAM_MAX_RATIO)
        with self.assertRaisesRegex(ValueError, 'Decoded loop seam fails'):
            mod.check_seam('click', self.wrap_loop(65536, step=.05))

    def encode(self, mod, samples, path):
        raw = path.with_suffix('.f32')
        raw.write_bytes(array.array('f', samples).tobytes())
        mod.run('ffmpeg', '-v', 'error', '-nostdin', '-y', '-f', 'f32le', '-ar', str(mod.RATE), '-ac', '1',
                '-i', str(raw), '-c:a', 'libvorbis', '-q:a', '5', '-map_metadata', '-1', str(path))

    @unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'needs ffmpeg/ffprobe')
    def test_inspect_enforces_the_seam_on_the_decoded_vorbis(self):
        mod = self.module()
        with tempfile.TemporaryDirectory() as td:
            for name, step in (('smooth', 0.0), ('click', .25)):
                path = Path(td) / (name + '.ogg')
                self.encode(mod, self.wrap_loop(65536, step), path)
                recipe = dict(duration=65536 / mod.RATE, loop=True,
                              target=float(mod.measure(path, -12.6)['input_i']))
                if step:
                    with self.assertRaisesRegex(ValueError, 'Decoded loop seam fails'):
                        mod.inspect(path, recipe)
                else:
                    out = mod.inspect(path, recipe)
                    self.assertLess(out['seam_delta'], mod.SEAM_MAX_DELTA)
                    self.assertLess(out['seam_ratio'], mod.SEAM_MAX_RATIO)

    @unittest.skipUnless(shutil.which('ffmpeg'), 'needs ffmpeg')
    def test_committed_hum_passes_the_decoded_seam_gate(self):
        mod = self.module()
        encoded = mod.OUT / 'world_cut_hum.ogg.b64'
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / 'world_cut_hum.ogg'
            path.write_bytes(base64.b64decode(''.join(encoded.read_text().split()), validate=True))
            delta, ratio = mod.check_seam(path, mod.read_pcm(path, 0))
        self.assertLess(delta, mod.SEAM_MAX_DELTA)
        self.assertLess(ratio, mod.SEAM_MAX_RATIO)

    def test_smoothing_keeps_the_circular_length(self):
        mod = self.module()
        samples = self.seed(mod.RATE)
        cut = mod.smooth_seam(samples, int(.008 * mod.RATE))
        self.assertEqual(len(samples) - int(.008 * mod.RATE), len(cut))


if __name__ == '__main__':
    unittest.main()
