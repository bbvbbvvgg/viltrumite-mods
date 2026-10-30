"""One-shot preparation of Atheria v2.06 (by Tazluh) for Viltrumite Mods on Minecraft 1.20.1.

The map and its resource pack belong to their author and are NOT stored in this
repository. This script turns the original download into the ready world:

  1. extracts the original archive (Distant Horizons caches and LOD data are skipped);
  2. generates the 1.20.1 block/registry reports from the vanilla 1.20.1 server jar;
  3. downgrades the 1.21.x world to 1.20.1 (downgrade.py);
  4. extracts the outdoor surfaces (columns.py) and builds the pedestrian network and
     the city profile (walk.py) into <world>/viltrumite_city/;
  5. fits the resource pack to 1.20.1 and installs it as the world pack (resources.zip,
     applied automatically in singleplayer);
  6. names the world and packs it as one folder in a zip that unpacks straight into
     .minecraft/saves.

Usage: python3 prepare.py <Atheria_v2.06.zip> <work dir> --server-jar <minecraft 1.20.1 server.jar>
Needs: Python 3.10+, numpy, opencv-python-headless, Java 17.
"""
import argparse, json, os, shutil, subprocess, sys, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import nbt  # noqa: E402

WORLD_NAME = 'Atheria v2.06 (Viltrumite)'
FOLDER = 'Atheria_v2.06_Viltrumite'

def run(*cmd, cwd=None):
    print('>', ' '.join(cmd), flush=True)
    subprocess.run(cmd, check=True, cwd=cwd)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('archive'); ap.add_argument('work')
    ap.add_argument('--server-jar', required=True); ap.add_argument('--java', default='java')
    ap.add_argument('--jobs', default='2')
    a = ap.parse_args()
    w = os.path.abspath(a.work); os.makedirs(w, exist_ok=True)
    src = os.path.join(w, 'src')
    if not os.path.isdir(src):
        with zipfile.ZipFile(a.archive) as z:
            names = [n for n in z.namelist() if 'DistantHorizons.sqlite' not in n and '/data/lod/' not in n]
            z.extractall(src, names)
    world_src = next(os.path.join(src, d) for d in os.listdir(src) if os.path.isfile(os.path.join(src, d, 'level.dat')))
    pack_src = next((os.path.join(src, f) for f in os.listdir(src) if f.lower().endswith('.zip') and 'resource' in f.lower()), None)
    rep = os.path.join(w, 'reports')
    reports = os.path.join(rep, 'generated', 'reports')
    if not os.path.isfile(os.path.join(reports, 'blocks.json')):
        os.makedirs(rep, exist_ok=True)
        run(a.java, '-DbundlerMainClass=net.minecraft.data.Main', '-jar', os.path.abspath(a.server_jar), '--reports', cwd=rep)
    if not os.path.isfile(os.path.join(reports, 'biomes.txt')):
        with zipfile.ZipFile(a.server_jar) as outer:
            inner = next(n for n in outer.namelist() if n.startswith('META-INF/versions/') and n.endswith('.jar'))
            with outer.open(inner) as f, zipfile.ZipFile(f) as zj:
                biomes = sorted(n.rsplit('/', 1)[1][:-5] for n in zj.namelist() if n.startswith('data/minecraft/worldgen/biome/') and n.endswith('.json'))
        open(os.path.join(reports, 'biomes.txt'), 'w').write('\n'.join(biomes) + '\n')
    world = os.path.join(w, FOLDER)
    if not os.path.isdir(os.path.join(world, 'region')):
        run(sys.executable, os.path.join(HERE, 'downgrade.py'), world_src, world, '--reports', reports, '--jobs', a.jobs)
    cols = os.path.join(w, 'cols')
    if not os.path.isdir(cols) or not os.listdir(cols):
        run(sys.executable, os.path.join(HERE, 'columns.py'), world, cols)
    anchors = os.path.join(HERE, 'districts.json')
    run(sys.executable, os.path.join(HERE, 'walk.py'), cols, world, *(['--districts', anchors] if os.path.isfile(anchors) else []))
    if pack_src:
        run(sys.executable, os.path.join(HERE, 'resourcepack.py'), pack_src, os.path.join(world, 'resources.zip'))
    lv = nbt.load_file(os.path.join(world, 'level.dat'))
    lv['Data']['LevelName'] = WORLD_NAME
    nbt.save_file(os.path.join(world, 'level.dat'), lv)
    for junk in ('session.lock',):
        p = os.path.join(world, junk)
        if os.path.exists(p):
            os.remove(p)
    out = os.path.join(w, FOLDER + '.zip')
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for root, _, files in os.walk(world):
            for f in files:
                full = os.path.join(root, f)
                arc = os.path.join(FOLDER, os.path.relpath(full, world))
                z.write(full, arc, compress_type=zipfile.ZIP_STORED if f.endswith(('.mca', '.zip')) else zipfile.ZIP_DEFLATED)
    print('ready:', out, os.path.getsize(out) // (1 << 20), 'MiB')

if __name__ == '__main__':
    main()
