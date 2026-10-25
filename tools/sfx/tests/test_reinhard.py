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
        # Fresh source is essential for mutation checks: same-size edits within one
        # filesystem timestamp tick must not reuse a stale bytecode cache.
        exec(compile(SCRIPT.read_bytes(), str(SCRIPT), 'exec'), mod.__dict__)
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
                expected = {'mode_restrained':-18, 'mode_knight':-18, 'mode_holy':-18, 'reid_choir':-20,
                            'reid_unsheathe':-14, 'reid_rumble':-20, 'world_cut_silence':-20, 'world_cut_tail':-20,
                            'erase_crack':-14, 'erase_inhale':-20, 'limbo_heartbeat':-20, 'sky_kick':-14,
                            'stomp':-14, 'sheath_ring':-14}[name]
                # Independently inspect decoded integrated loudness, not inspect()'s own decision.
                result = subprocess.run(['ffmpeg', '-hide_banner', '-nostdin', '-i', str(path), '-af',
                                         'loudnorm=I=-14:TP=-2:LRA=11:print_format=json', '-f', 'null', '-'], capture_output=True, text=True, check=True)
                import json
                raw_measure = json.loads(result.stderr[result.stderr.rfind('{'):result.stderr.rfind('}')+1])
                self.assertAlmostEqual(expected, float(raw_measure['input_i']), delta=1)
                self.assertLessEqual(measured['true_peak_dbtp'], -1)
                self.assertLessEqual(measured['sample_peak_dbfs'], -1)
                self.assertAlmostEqual(recipe['duration'], measured['duration'], delta=.03)

    def test_check_detects_missing_committed_file(self):
        m = self.module()
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(ValueError, 'Missing committed'):
                m.compare_pcm(Path(td), Path(td), ['mode_restrained'])

    def asset(self, name):
        import base64
        return base64.b64decode((ROOT / 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/reinhard' / (name + '.ogg.b64')).read_bytes())

    def test_different_pcm_rejected(self):
        import base64
        m = self.module()
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td)
            (folder / 'probe.ogg.b64').write_bytes(base64.encodebytes(self.asset('mode_restrained')))
            (folder / 'probe.ogg').write_bytes(self.asset('mode_knight'))
            with self.assertRaisesRegex(ValueError, 'PCM mismatch'):
                m.compare_pcm(folder, folder, ['probe'])

    def test_identical_pcm_different_container_accepted(self):
        import base64
        m = self.module()
        original = self.asset('mode_restrained')
        changed = bytearray(original)
        position = 0
        while position < len(changed):
            self.assertEqual(b'OggS', changed[position:position+4])
            segments = changed[position+26]
            end = position+27+segments+sum(changed[position+27:position+27+segments])
            changed[position+14:position+18] = (123456).to_bytes(4, 'little')
            changed[position+22:position+26] = b'\x00'*4
            crc = 0
            for byte in changed[position:end]:
                crc ^= byte << 24
                for _ in range(8):
                    crc = ((crc << 1) ^ (0x04c11db7 if crc & 0x80000000 else 0)) & 0xffffffff
            changed[position+22:position+26] = crc.to_bytes(4, 'little')
            position = end
        self.assertNotEqual(original, changed)
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td)
            (folder / 'probe.ogg.b64').write_bytes(base64.encodebytes(original))
            (folder / 'probe.ogg').write_bytes(changed)
            m.compare_pcm(folder, folder, ['probe'])  # Same packets, valid different Ogg serial/checksum.

    def drift_build(self, delta):
        import hashlib
        m = self.module()
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td)
            source = folder / 'recorded.ogg'
            source.write_bytes(self.asset('mode_restrained'))
            recipe = dict(source='fixture', start=0, duration=1.8, kind='ui', fade_in=.03, fade_out=.4, loop=False)
            raw = folder / 'first-pass.wav'
            m.run('ffmpeg', '-v', 'error', '-nostdin', '-y', '-i', str(source), '-af', m.edit_filter(recipe), '-c:a', 'pcm_f32le', str(raw))
            truth = m.measure(raw, -18)
            recipe['measured'] = {k: truth[k] for k in ('input_i', 'input_tp', 'input_lra', 'input_thresh', 'target_offset')}
            recipe['measured']['input_i'] = str(float(truth['input_i'])+delta)
            # A real approved processed recording supplies the controlled one-recipe fixture;
            # only input tables vary. No command, codec, validation or measurement is mocked.
            m.SOURCES = {'fixture': dict(file=source.name, sha256=hashlib.sha256(source.read_bytes()).hexdigest(), license='CC-BY-4.0')}
            m.RECIPES = {'probe': recipe}
            return m.build(folder, folder / 'rebuilt')

    def test_build_rejects_drift_outside_tolerance(self):
        with self.assertRaisesRegex(ValueError, 'First-pass drift: probe input_i'):
            self.drift_build(.11)

    def test_build_accepts_measurement_inside_tolerance(self):
        report = self.drift_build(.09)
        self.assertEqual({'probe'}, set(report))
        self.assertAlmostEqual(-18, report['probe']['integrated_lufs'], delta=1)

    def test_wrong_format_rejected(self):
        m = self.module()
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td)
            source = folder / 'source.ogg'; source.write_bytes(self.asset('mode_restrained'))
            wrong = folder / 'wrong.wav'
            m.run('ffmpeg', '-v', 'error', '-nostdin', '-y', '-i', str(source), '-ac', '1', '-ar', '44100', '-c:a', 'pcm_f32le', str(wrong))
            with self.assertRaisesRegex(ValueError, 'Wrong output format'):
                m.inspect(wrong, m.RECIPES['mode_restrained'])

    def test_wrong_loudness_rejected(self):
        m = self.module()
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td)
            source = folder / 'source.ogg'; source.write_bytes(self.asset('mode_restrained'))
            wrong = folder / 'wrong.ogg'
            m.run('ffmpeg', '-v', 'error', '-nostdin', '-y', '-i', str(source), '-af', 'volume=0.01', '-c:a', 'libvorbis', '-q:a', '5', str(wrong))
            with self.assertRaisesRegex(ValueError, 'Decoded LUFS outside'):
                m.inspect(wrong, m.RECIPES['mode_restrained'])

    def test_wrong_peak_rejected(self):
        import json
        m = self.module()
        with tempfile.TemporaryDirectory() as td:
            wrong = Path(td) / 'legacy-punch.ogg'
            m.run('ffmpeg', '-v', 'error', '-nostdin', '-y', '-i', str(ROOT / 'viltrumitecore/src/main/resources/assets/viltrumitecore/sounds/punch4.ogg'),
                  '-ac', '1', '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '5', str(wrong))
            duration = float(json.loads(m.run('ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'json', str(wrong)).stdout)['format']['duration'])
            with self.assertRaisesRegex(ValueError, 'Decoded peak exceeds'):
                m.inspect(wrong, dict(duration=duration, kind='impact', loop=False))

    def test_provenance_matches_actual_recipe_and_source(self):
        import json
        m = self.module()
        approved = json.loads((ROOT / 'viltrumitecore/src/test/resources/reinhard/approved-provenance.json').read_text())
        bindings = {'mode_restrained':'398218', 'mode_knight':'398218', 'mode_holy':'398218', 'reid_choir':'398218',
                    'reid_unsheathe':'770035', 'reid_rumble':'870684', 'world_cut_silence':'503809', 'world_cut_tail':'248150',
                    'erase_crack':'554564', 'erase_inhale':'331620', 'limbo_heartbeat':'249716', 'sky_kick':'182050',
                    'stomp':'275809', 'sheath_ring':'519413'}
        self.assertEqual(bindings, {n: r['source'] for n,r in m.RECIPES.items()})
        self.assertEqual(set(bindings.values()), set(m.SOURCES))
        for source, record in m.SOURCES.items():
            self.assertEqual(approved[source]['file'], record['file'])
            self.assertEqual(approved[source]['sha256'], record['sha256'])
            self.assertEqual({'CC0 1.0':'CC0-1.0', 'CC-BY 4.0':'CC-BY-4.0'}[approved[source]['license']], record['license'])

if __name__ == '__main__':
    unittest.main()
