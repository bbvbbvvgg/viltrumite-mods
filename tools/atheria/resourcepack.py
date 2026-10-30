"""Fit the Atheria resource pack (made for 1.21.x, pack_format 42) to Minecraft 1.20.1.

- pack.mcmeta: pack_format 15 (1.20.1) plus supported_formats, so 1.20.1 loads it
  without the "made for a newer version" warning;
- assets/minecraft/shaders/core is dropped: core shader sources are version specific
  and a 1.21 shader breaks 1.20.1 entity rendering;
- Windows/OneNote junk (Thumbs.db, *.onetoc2) is dropped, and so are files under assets/
  whose names are not valid resource locations (e.g. "white_carpet - kopia.json", a stray
  Windows copy): Minecraft ignores them anyway and logs a warning for each.
Textures, block and item models and sounds are copied unchanged (same formats in 1.20.1).

Usage: python3 resourcepack.py <Atheria Resource Pack.zip> <out resources.zip>
"""
import json, re, sys, zipfile

DROP_PREFIX = ('assets/minecraft/shaders/core/',)
DROP_NAME = ('thumbs.db', '.onetoc2', '.ds_store', 'desktop.ini')
VALID = re.compile(r'^assets/[a-z0-9_.-]+/[a-z0-9_./-]+$')

def main(src, dst):
    kept = dropped = 0
    with zipfile.ZipFile(src) as zi, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zo:
        names = zi.namelist()
        root = ''
        meta = [n for n in names if n.endswith('pack.mcmeta')]
        if meta and meta[0] != 'pack.mcmeta':
            root = meta[0][:-len('pack.mcmeta')]  # pack inside a folder: flatten
        for n in names:
            if n.endswith('/') or not n.startswith(root):
                continue
            rel = n[len(root):]
            low = rel.lower()
            if rel.startswith(DROP_PREFIX) or low.endswith(DROP_NAME) or 'onenote table' in low or \
                    (rel.startswith('assets/') and not VALID.match(rel)):
                dropped += 1
                continue
            data = zi.read(n)
            if rel == 'pack.mcmeta':
                m = json.loads(data.decode('utf-8-sig'))
                p = m.setdefault('pack', {})
                p['pack_format'] = 15
                p['supported_formats'] = {'min_inclusive': 15, 'max_inclusive': 42}
                if not p.get('description'):
                    p['description'] = 'Atheria by Tazluh (fitted to 1.20.1 for Viltrumite Mods)'
                data = json.dumps(m, indent=2).encode()
            zo.writestr(rel, data)
            kept += 1
    print('resource pack: %d files kept, %d dropped -> %s' % (kept, dropped, dst))

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
