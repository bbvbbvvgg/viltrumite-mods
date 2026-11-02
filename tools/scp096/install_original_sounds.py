#!/usr/bin/env python3
r"""Copies the five SCP-096 sounds of YOUR OWN copy of SCP - Containment Breach into a Minecraft game folder.

The mod never ships these files (they are not ours to redistribute, see tools/assets/scp096_sources.md): at start-up
the client looks for them in scp096OriginalSoundsPath (config), in <gameDir>/viltrumitecore/scp096 and in the usual
install folders (Steam, Program Files, Desktop, Downloads, Games). This helper is for the last-resort case, or for
a dev run directory:

    python3 tools/scp096/install_original_sounds.py "<SCP-CB folder>" <gameDir>
    python3 tools/scp096/install_original_sounds.py "C:/Games/SCP - Containment Breach v1.3.11" "%APPDATA%/.minecraft"
    python3 tools/scp096/install_original_sounds.py ~/scpcb viltrumitecore/run

Accepted source layouts (the same as the mod's lookup): the game tree (SFX/Music/096.ogg), flat file names
(096.ogg) or flattened paths (SFX_Music_096.ogg). The files land as <gameDir>/viltrumitecore/scp096/<game tree>.
Nothing is converted or modified; restart Minecraft (or press F3+T) afterwards.
"""
import os
import shutil
import sys

# served name -> path inside an SCP: CB install (Scp096OriginalSounds.FILES)
FILES = {
    'cry': 'SFX/Music/096.ogg',
    'angered': 'SFX/Music/096Angered.ogg',
    'scream': 'SFX/SCP/096/Scream.ogg',
    'triggered': 'SFX/SCP/096/Triggered.ogg',
    'chase': 'SFX/Music/096Chase.ogg',
}
MAX_BYTES = 32 << 20


def candidates(game_path):
    return [game_path, game_path.rsplit('/', 1)[-1], game_path.replace('/', '_')]


def valid_ogg(path):
    try:
        size = os.path.getsize(path)
        if size < 1024 or size > MAX_BYTES:
            return False
        with open(path, 'rb') as f:
            return f.read(4) == b'OggS'
    except OSError:
        return False


def find(source, game_path):
    for rel in candidates(game_path):
        p = os.path.join(source, *rel.split('/'))
        if valid_ogg(p):
            return p
    return None


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    source, game_dir = argv[1], argv[2]
    target = os.path.join(game_dir, 'viltrumitecore', 'scp096')
    copied = 0
    for name, game_path in FILES.items():
        src = find(source, game_path)
        if src is None:
            print('missing  %-9s %s (the vanilla fallback stays in use)' % (name, game_path))
            continue
        dst = os.path.join(target, *game_path.split('/'))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        copied += 1
        print('copied   %-9s %s -> %s' % (name, src, dst))
    print('%d of %d sounds in %s' % (copied, len(FILES), target))
    return 0 if copied else 1


if __name__ == '__main__':
    sys.exit(main(sys.argv))
