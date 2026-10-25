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

    @staticmethod
    def sample(root):
        sample = root / "sample.wav"
        with wave.open(str(sample), "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(44100)
            wav.writeframes(b"\0\0" * 4410)
        return sample

    @staticmethod
    def module(root, modid, decodes):
        (root / modid).mkdir(parents=True, exist_ok=True)
        text = ("def binAssetsSrc = file('src/main/binassets')\n"
                "def decodeBinaryAssets = tasks.register('decodeBinaryAssets') {}\n") if decodes else "jar {}\n"
        (root / modid / "build.gradle").write_text(text, encoding="utf-8")

    def require_ffmpeg(self):
        if not sfx.shutil.which("ffmpeg") or not sfx.shutil.which("ffprobe"):
            self.skipTest("ffmpeg/ffprobe not installed")

    def test_real_builds_match_the_storage_rule(self):
        repo = Path(__file__).resolve().parents[4]
        self.assertTrue(sfx.decodes_binassets(repo, "viltrumitecore"))
        self.assertFalse(sfx.decodes_binassets(repo, "viltrumiteflight"),
                         "flight gained decodeBinaryAssets: update the skill's flight storage rule")

    def test_flight_packages_a_raw_resource_its_jar_ships(self):
        self.require_ffmpeg()
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.module(root, "viltrumiteflight", decodes=False)
            result = sfx.package_audio(self.sample(root), "flight", "test_whoosh", "test_whoosh",
                                       [], True, False, False, root)
            output = Path(result["output"])
            self.assertEqual(root / "viltrumiteflight/src/main/resources/assets/viltrumiteflight/sounds/test_whoosh.ogg",
                             output)
            self.assertEqual(b"OggS", output.read_bytes()[:4], "raw Vorbis, not base64 text")
            self.assertFalse((root / "viltrumiteflight/src/main/binassets").exists())
            self.assertEqual("resources raw .ogg", result["storage"])
            self.assertIn("commit the raw .ogg", result["next_steps"][0])

    def test_replace_removes_the_superseded_raw_core_sound(self):
        self.require_ffmpeg()
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.module(root, "viltrumitecore", decodes=True)
            raw = root / "viltrumitecore/src/main/resources/assets/viltrumitecore/sounds/dash1.ogg"
            raw.parent.mkdir(parents=True)
            raw.write_bytes(b"old")
            with self.assertRaises(sfx.SfxError):
                sfx.package_audio(self.sample(root), "core", "dash1", "dash", [], True, False, False, root)
            self.assertTrue(raw.is_file(), "nothing removed without --replace")
            result = sfx.package_audio(self.sample(root), "core", "dash1", "dash", [], True, False, True, root)
            packed = root / "viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/dash1.ogg.b64"
            self.assertEqual(str(packed), result["output"])
            self.assertTrue(packed.is_file())
            self.assertFalse(raw.exists(), "processResources must not receive two dash1.ogg files")
            self.assertEqual([str(raw)], result["removed"])
            self.assertIn("deletion", result["next_steps"][0])

    def test_package_own_audio_and_refuse_overwrite(self):
        self.require_ffmpeg()
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.module(root, "viltrumitecore", decodes=True)
            sample = self.sample(root)
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
