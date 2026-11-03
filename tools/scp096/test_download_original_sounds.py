#!/usr/bin/env python3
"""Offline tests for the opt-in SCP: Containment Breach audio installer."""
import io
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import download_original_sounds as scp


class OriginalSoundInstallerTest(unittest.TestCase):
    def setUp(self):
        self.sample = b"OggS" + bytes(range(256)) * 8
        self.rel = "SFX/SCP/096/Scream.ogg"
        self.manifest = {self.rel: (scp.git_blob_sha(self.sample), len(self.sample))}

    def test_sha_and_ogg_validation(self):
        sha, size = self.manifest[self.rel]
        self.assertTrue(scp.verify(self.sample, sha, size))
        self.assertFalse(scp.verify(b"RIFF" + self.sample[4:], sha, size))
        self.assertFalse(scp.verify(self.sample + b"x", sha, size))
        self.assertFalse(scp.verify(self.sample, "0" * 40, size))

    def test_download_installs_original_and_reuses_verified_local_file(self):
        calls = []

        def opener(url, timeout):
            calls.append(url)
            self.assertEqual(timeout, 30)
            return io.BytesIO(self.sample)

        with tempfile.TemporaryDirectory() as d:
            result = scp.install(d, self.manifest, opener)
            self.assertEqual(result, (1, 0, []))
            dest = Path(d) / "viltrumitecore" / "scp096" / self.rel
            self.assertEqual(dest.read_bytes(), self.sample)
            self.assertEqual(scp.install(d, self.manifest, opener), (0, 1, []))
        self.assertEqual(calls, [scp.UPSTREAM + self.rel])

    def test_existing_different_file_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as d:
            target = Path(d) / "viltrumitecore" / "scp096" / self.rel
            target.parent.mkdir(parents=True)
            target.write_bytes(b"custom")

            def fail_if_called(url, timeout):
                self.fail("network should not be used")

            installed, already, errors = scp.install(d, self.manifest, fail_if_called)
            self.assertEqual((installed, already), (0, 0))
            self.assertEqual(len(errors), 1)
            self.assertEqual(target.read_bytes(), b"custom")

    def test_invalid_download_does_not_leave_corrupt_file(self):
        with tempfile.TemporaryDirectory() as d:
            installed, already, errors = scp.install(d, self.manifest, lambda url, timeout: io.BytesIO(b"bad"))
            self.assertEqual((installed, already), (0, 0))
            self.assertEqual(len(errors), 1)
            self.assertFalse((Path(d) / "viltrumitecore" / "scp096" / self.rel).exists())


if __name__ == "__main__":
    unittest.main()
