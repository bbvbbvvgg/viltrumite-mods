#!/usr/bin/env python3
"""Assemble a ready-to-unpack client ZIP: mods/ (Viltrumite + verified optimization mods) + config + README.

    python3 tools/perf/build_client_zip.py --viltrumite-jars ./mod-jars --profile client --out viltrumite-client.zip

* `--viltrumite-jars` = folder with the CI artifact `mod-jars` (viltrumitecore-*.jar, viltrumiteflight-*.jar)
  plus GeckoLib 4.4.9 if you want it inside the ZIP.
* Third-party JARs come from tools/perf/fetch_mods.py (official CDN + SHA-512/SHA-256 check).
* Mods whose licence forbids redistribution (EntityCulling: tr7zw Protective License) are NOT put into the ZIP unless
  `--personal` is given (a ZIP only for yourself). Shared ZIPs get a DOWNLOAD-SEPARATELY.txt with the link instead.
"""
import argparse, glob, json, os, subprocess, sys, tempfile, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
MANIFEST = os.path.join(ROOT, 'docs', 'performance', 'modpack-manifest.json')
NO_REDIST = {'LicenseRef-tr7zw-Protective-License'}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--viltrumite-jars', required=True)
    ap.add_argument('--profile', default='client')
    ap.add_argument('--out', default='viltrumite-client.zip')
    ap.add_argument('--personal', action='store_true', help='also bundle mods that may not be redistributed')
    a = ap.parse_args()
    man = json.load(open(MANIFEST, encoding='utf-8'))
    mods = {m['id']: m for m in man['mods']}
    prof = man['profiles'][a.profile]['mods']
    with tempfile.TemporaryDirectory() as tmp:
        cache = os.path.join(tmp, 'mods')
        subprocess.check_call([sys.executable, os.path.join(HERE, 'fetch_mods.py'), '--profile', a.profile, '--out', cache])
        own = sorted(glob.glob(os.path.join(a.viltrumite_jars, '*.jar')))
        if not own:
            sys.exit('no Viltrumite JARs in ' + a.viltrumite_jars)
        skipped = []
        with zipfile.ZipFile(a.out, 'w', zipfile.ZIP_DEFLATED) as z:
            for j in own:
                z.write(j, 'mods/' + os.path.basename(j))
            for i in prof:
                m = mods[i]
                if m.get('manual'):
                    skipped.append(m); continue
                if m['license'] in NO_REDIST and not a.personal:
                    skipped.append(m); continue
                z.write(os.path.join(cache, m['file']), 'mods/' + m['file'])
            z.write(os.path.join(ROOT, 'docs', 'performance', 'RECOMMENDED_MODPACK.md'), 'README-PERFORMANCE.md')
            if skipped:
                z.writestr('DOWNLOAD-SEPARATELY.txt', 'These mods are recommended but may not be re-uploaded; download them yourself:\n' +
                           ''.join(f"- {m['name']} {m['version']}: {m['project_url']} (sha256 {m['sha256']})\n" for m in skipped))
            z.writestr('LICENSES-THIRD-PARTY.txt', ''.join(
                f"{mods[i]['name']} {mods[i]['version']} - {mods[i]['license']} - source: {mods[i]['source_code'] or mods[i]['project_url']}\n" for i in prof))
    print('wrote', a.out, '(skipped:', ', '.join(m['name'] for m in skipped) or 'none', ')')


if __name__ == '__main__':
    main()
