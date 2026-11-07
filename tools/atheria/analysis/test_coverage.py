"""Self-test of coverage.py on a synthetic tile (T18): a tiny VCW1 world is written to a
temp dir, then read back and validated against district boxes. Run:

  python3 -m unittest tools.atheria.analysis.test_coverage -v
"""
import json
import os
import re
import shutil
import struct
import sys
import tempfile
import unittest
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import coverage  # noqa: E402


def vcw1_tile(present_chunks, cells):
    """Build a VCW1 byte stream: present_chunks = {(cz, cx)}, cells = {(cz, cx, idx): class}."""
    bitmap = bytearray(128)
    body = bytearray()
    for cz in range(32):
        for cx in range(32):
            if (cz, cx) not in present_chunks:
                continue
            i = cz * 32 + cx
            bitmap[i >> 3] |= 1 << (i & 7)
            vals = bytearray(512)
            for (ccz, ccx, idx), cls in cells.items():
                if (ccz, ccx) == (cz, cx):
                    struct.pack_into('>H', vals, 2 * idx, (512 + 4) | (cls << 13))
            body += vals
    return struct.pack('>ii', coverage.MAGIC, coverage.FORMAT) + zlib.compress(bytes(bitmap) + bytes(body), 6)


class CoverageTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix='coverage-test-')
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.walk = os.path.join(self.dir, 'viltrumite_city', 'walk')
        os.makedirs(self.walk)
        # one chunk of walk cells at region chunk (0,0) plus one all-zero present chunk (0,1);
        # 154 = z*16+x (z=9, x=10) inside the second chunk, index z*16+x per WalkTile.java
        self.tile = vcw1_tile({(0, 0), (0, 1)}, {(0, 0, 0): coverage.WALK, (0, 1, 154): coverage.PREFERRED})
        with open(os.path.join(self.walk, 'r.0.0.bin'), 'wb') as f:
            f.write(self.tile)

    def test_read_tile_counts_classes(self):
        chunks, cells = coverage.read_tile(os.path.join(self.walk, 'r.0.0.bin'))
        self.assertEqual({(0, 0): (1, 0), (0, 1): (0, 1)}, chunks)
        self.assertEqual(2, cells)

    def test_rejects_bad_magic_and_format(self):
        bad = self.tile[:4] + struct.pack('>i', 0x12345678) + self.tile[8:]
        p = os.path.join(self.dir, 'bad.bin')
        with open(p, 'wb') as f:
            f.write(bad)
        with self.assertRaisesRegex(ValueError, 'unsupported format'):
            coverage.read_tile(p)
        wrong_magic = struct.pack('>ii', 0x12345678, 1) + self.tile[8:]
        with open(p, 'wb') as f:
            f.write(wrong_magic)
        with self.assertRaisesRegex(ValueError, 'bad magic'):
            coverage.read_tile(p)

    def test_full_coverage_of_profile_districts(self):
        prof = os.path.join(self.dir, 'profile.json')
        # district box inside the covered chunk (blocks 0..15 / 0..15 = chunk 0 0)
        json.dump({'id': 'atheria', 'mapVersion': 'v2.06', 'districts': [
            {'name': 'Downtown Atheria', 'minX': 0, 'minZ': 0, 'maxX': 15, 'maxZ': 15}]}, open(prof, 'w'))
        out, code = self.run_tool(['--profile', prof])
        self.assertEqual(0, code, out)
        self.assertIn('coverage: 1/1 chunks (100.0%), 0 missing', out)

    def test_hole_is_reported_with_district(self):
        prof = os.path.join(self.dir, 'profile.json')
        json.dump({'districts': [
            {'name': 'Downtown Atheria', 'minX': 0, 'minZ': 0, 'maxX': 15, 'maxZ': 15},
            {'name': 'Centria', 'minX': 16, 'minZ': 0, 'maxX': 31, 'maxZ': 15},
            {'name': 'Capitol', 'minX': 32, 'minZ': 32, 'maxX': 47, 'maxZ': 47}]}, open(prof, 'w'))
        out, code = self.run_tool(['--profile', prof])
        self.assertEqual(1, code, out)
        # chunks 0 0 (walk) and 1 0 (one preferred cell) are covered; the Capitol chunk is not in the tile
        self.assertIn('no coverage: chunk 2 2 (Capitol)', out)
        self.assertIn('coverage: 2/3 chunks (66.7%), 1 missing', out)
        self.assertIn('walk tiles: 1 regions, 2 covered chunks, 2 walk cells', out)

    def test_anchor_circles_cover_by_range(self):
        dis = os.path.join(self.dir, 'districts.json')
        json.dump([{'name': 'Capitol', 'x': 8, 'z': 8}], open(dis, 'w'))
        out, code = self.run_tool(['--districts', dis, '--anchor-range', '10'])
        # a 10-block circle around (8, 8) spans chunks -1..1 in both axes: only chunk 0 0 is covered
        self.assertEqual(1, code, out)
        self.assertIn('coverage: 2/9 chunks (22.2%), 7 missing', out)
        dis2 = os.path.join(self.dir, 'one.json')
        # anchor exactly on the covered single chunk: box 8..8 is inside chunk 0 0
        json.dump([{'name': 'Capitol', 'x': 5, 'z': 5}], open(dis2, 'w'))
        out, code = self.run_tool(['--districts', dis2, '--anchor-range', '0'])
        self.assertEqual(0, code, out)
        self.assertIn('coverage: 1/1 chunks (100.0%), 0 missing', out)

    def run_tool(self, args):
        import io
        from contextlib import redirect_stdout
        argv = [self.dir] + args
        saved = sys.argv
        buf = io.StringIO()
        code = 0
        try:
            sys.argv = ['coverage.py'] + argv
            with redirect_stdout(buf):
                try:
                    coverage.main()
                except SystemExit as e:
                    code = e.code or 0
        finally:
            sys.argv = saved
        return buf.getvalue(), code


if __name__ == '__main__':
    unittest.main()
