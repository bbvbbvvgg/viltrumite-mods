"""Offline tests; no Openverse quota or network access needed."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import wave

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "openverse_sfx.py"
spec = importlib.util.spec_from_file_location("openverse_sfx", SCRIPT)
sfx = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sfx)


class SfxTests(unittest.TestCase):
    def test_url_security(self):
        self.assertEqual(sfx.safe_url("https://cdn.freesound.org/previews/a.mp3"),
                         "https://cdn.freesound.org/previews/a.mp3")
        for url in ["http://cdn.freesound.org/a.mp3", "https://127.0.0.1/a.mp3",
                    "https://localhost/a.mp3", "file:///tmp/a.mp3", "https://example.com:8080/a.mp3"]:
            with self.subTest(url=url), self.assertRaises(sfx.SfxError):
                sfx.safe_url(url)

    def test_name_validation(self):
        self.assertEqual(sfx.validate_name("regulus/air_blade0"), "regulus/air_blade0")
        for name in ["../bad", "Regulus/sound", "sound.ogg", "/root", "foo//bar"]:
            with self.subTest(name=name), self.assertRaises(sfx.SfxError):
                sfx.validate_name(name)

    def test_search_cache_and_null_category(self):
        with tempfile.TemporaryDirectory() as d, patch.object(sfx, "CACHE", Path(d)):
            payload = {"result_count": 1, "results": [{"id": "a" * 36, "title": "laser",
                       "source": "freesound", "license": "cc0", "category": None,
                       "url": "https://cdn.freesound.org/a.mp3", "attribution": "exact text"}]}
            with patch.object(sfx, "request_json", return_value=(payload, {"X-RateLimit-Limit": "20/min"})) as mocked:
                first = sfx.search("laser")
                second = sfx.search("laser")
            self.assertEqual(mocked.call_count, 1)
            self.assertIn("license=cc0", mocked.call_args.args[0])
            self.assertNotIn("category=", mocked.call_args.args[0])
            self.assertIsNone(first["results"][0]["category"])
            self.assertTrue(second["cache_hit"])
            self.assertEqual(sfx.find_cached("a" * 36)["attribution"], "exact text")

    def test_search_rejects_non_cc0(self):
        with tempfile.TemporaryDirectory() as d, patch.object(sfx, "CACHE", Path(d)):
            payload = {"result_count": 1, "results": [{"id": "x", "license": "by", "source": "freesound", "url": "https://example.com/a.mp3"}]}
            with patch.object(sfx, "request_json", return_value=(payload, {})):
                self.assertEqual(sfx.search("test")["results"], [])

    def test_package_own_audio_and_refuse_overwrite(self):
        if not sfx.shutil.which("ffmpeg") or not sfx.shutil.which("ffprobe"):
            self.skipTest("ffmpeg/ffprobe not installed")
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            sample = root / "sample.wav"
            with wave.open(str(sample), "wb") as wav:
                wav.setnchannels(1)
                wav.setsampwidth(2)
                wav.setframerate(44100)
                wav.writeframes(b"\0\0" * 4410)
            result = sfx.package_audio(sample, "core", "regulus/test_sound", "regulus_test_sound",
                                       [], True, False, False, root)
            output = Path(result["output"])
            self.assertTrue(output.is_file())
            self.assertEqual(result["output_audio"]["codec"], "vorbis")
            self.assertEqual(result["output_audio"]["channels"], 1)
            self.assertEqual(result["sounds_json_fragment"]["regulus_test_sound"]["sounds"],
                             ["viltrumitecore:regulus/test_sound"])
            with self.assertRaises(sfx.SfxError):
                sfx.package_audio(sample, "core", "regulus/test_sound", "regulus_test_sound",
                                  [], True, False, False, root)


if __name__ == "__main__":
    unittest.main()
