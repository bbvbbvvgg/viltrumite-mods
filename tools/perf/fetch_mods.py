#!/usr/bin/env python3
"""Download the optimization mods listed in docs/performance/modpack-manifest.json and verify SHA-512/SHA-256.

No third-party JAR is stored in this repository; every file is fetched from its official CDN (Modrinth or CurseForge)
and rejected if the hash does not match the manifest.

    python3 tools/perf/fetch_mods.py --profile client --out ./mods          # recommended client set
    python3 tools/perf/fetch_mods.py --profile server --out ./server/mods   # dedicated server set
    python3 tools/perf/fetch_mods.py --list                                 # show profiles and entries
    python3 tools/perf/fetch_mods.py --profile client --out ./mods --dry-run

Rollback: every run writes <out>/.viltrumite-perf-installed.txt; `--uninstall --out <dir>` removes exactly those files.
"""
import argparse, hashlib, json, os, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
MANIFEST = os.path.join(HERE, '..', '..', 'docs', 'performance', 'modpack-manifest.json')
LEDGER = '.viltrumite-perf-installed.txt'


def load(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def hashes(path):
    s512, s256 = hashlib.sha512(), hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            s512.update(chunk); s256.update(chunk)
    return s512.hexdigest(), s256.hexdigest()


def verify(path, mod):
    h512, h256 = hashes(path)
    return h512 == mod['sha512'] and h256 == mod['sha256']


def fetch(mod, out, dry):
    dest = os.path.join(out, mod['file'])
    if os.path.exists(dest) and verify(dest, mod):
        print(f"  ok      {mod['file']} (already present, hash verified)")
        return dest
    if dry:
        print(f"  would download {mod['url']}")
        return dest
    tmp = dest + '.part'
    req = urllib.request.Request(mod['url'], headers={'User-Agent': 'viltrumite-mods/perf-fetch (github.com/bbvbbvvgg/viltrumite-mods)'})
    with urllib.request.urlopen(req, timeout=120) as r, open(tmp, 'wb') as f:
        while True:
            b = r.read(1 << 20)
            if not b:
                break
            f.write(b)
    if not verify(tmp, mod):
        os.remove(tmp)
        sys.exit(f"  HASH MISMATCH for {mod['file']} - refusing to install (manifest or CDN file changed)")
    os.replace(tmp, dest)
    print(f"  fetched {mod['file']} ({mod['size']} bytes, sha256 {mod['sha256'][:12]}...)")
    return dest


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--manifest', default=MANIFEST)
    ap.add_argument('--profile', help='profile name from the manifest (client, client-shaders, server, ...)')
    ap.add_argument('--out', help='target mods folder')
    ap.add_argument('--dry-run', action='store_true')
    ap.add_argument('--list', action='store_true')
    ap.add_argument('--uninstall', action='store_true', help='remove the files recorded by a previous run in --out')
    a = ap.parse_args()
    m = load(a.manifest)
    mods = {x['id']: x for x in m['mods']}
    if a.list:
        for name, p in m['profiles'].items():
            print(f"{name}: {p['description']}")
            for i in p['mods']:
                x = mods[i]
                print(f"   - {x['name']} {x['version']} [{x['side']}] {x['license']}")
        return
    if not a.out:
        ap.error('--out is required')
    ledger = os.path.join(a.out, LEDGER)
    if a.uninstall:
        if not os.path.exists(ledger):
            sys.exit('nothing recorded in ' + ledger)
        for line in open(ledger, encoding='utf-8').read().split():
            p = os.path.join(a.out, line)
            if os.path.exists(p):
                os.remove(p); print('  removed', line)
        os.remove(ledger)
        return
    if a.profile not in m['profiles']:
        ap.error('unknown profile; use --list')
    os.makedirs(a.out, exist_ok=True)
    prof = m['profiles'][a.profile]
    print(f"profile {a.profile}: {prof['description']}")
    present = set(os.listdir(a.out))
    for i in prof['mods']:
        for bad in mods[i].get('incompatible_files', []):
            if any(f.lower().startswith(bad.lower()) for f in present):
                sys.exit(f"  {mods[i]['name']} is incompatible with '{bad}*' found in {a.out}; remove it first")
    got = []
    for i in prof['mods']:
        x = mods[i]
        if x.get('manual'):
            print(f"  MANUAL  {x['name']}: {x['manual']}")
            continue
        got.append(os.path.basename(fetch(x, a.out, a.dry_run)))
    if not a.dry_run:
        old = open(ledger, encoding='utf-8').read().split() if os.path.exists(ledger) else []
        with open(ledger, 'w', encoding='utf-8') as f:
            f.write('\n'.join(sorted(set(old + got))) + '\n')
        print(f"done; installed files recorded in {ledger} (rollback: --uninstall --out {a.out})")


if __name__ == '__main__':
    main()
