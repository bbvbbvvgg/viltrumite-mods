#!/usr/bin/env python3
"""Opt-in installer for the five *original* SCP-096 sounds from the official SCP: CB repository.

Nothing is copied into Git or the distributed mod JAR. This command downloads the files
straight from Regalis11/scpcb to a *local* Minecraft game directory, where the existing
Scp096OriginalSounds resource pack loads them automatically on F3+T or restart.

Usage:
    python3 tools/scp096/download_original_sounds.py viltrumitecore/run
    python3 tools/scp096/download_original_sounds.py "%APPDATA%\\.minecraft"

The upstream game says CC BY-SA 3.0; individual third-party samples may have separate
rights. This tool deliberately keeps the downloaded recordings out of the public mod.
"""
import argparse
from contextlib import closing
import hashlib
import os
from pathlib import Path
import tempfile
from urllib.error import HTTPError, URLError
from urllib.request import urlopen

# Fixed revision of the official game, never an unverified mirror or changing 'master'.
UPSTREAM = "https://raw.githubusercontent.com/Regalis11/scpcb/edb8fe0840b78f14d1aef3a0bf6174630e7be296/"
# Original game path -> (Git blob SHA1, exact size). Matches Scp096OriginalSounds.FILES.
FILES = {
    "SFX/Music/096.ogg": ("172653f589e575d26252107db63310c08a3610ff", 555453),
    "SFX/Music/096Angered.ogg": ("8034a22623e5f748921f0d961a561b471193c2c5", 450331),
    "SFX/SCP/096/Scream.ogg": ("4eb1be6596db2773dfc9562bf12fa974d903ccfc", 157222),
    "SFX/SCP/096/Triggered.ogg": ("d3be8209fed197a1dc73127ab1bd905b3a0a06ed", 157569),
    "SFX/Music/096Chase.ogg": ("4a95f42b29c87351b7af3379a2f50f80efbfad8e", 221126),
}


def git_blob_sha(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()


def verify(data, sha, size):
    return len(data) == size and data.startswith(b"OggS") and git_blob_sha(data) == sha


def install(game_dir, files=FILES, opener=urlopen):
    """Return (installed, already_present, errors). Existing custom files are never overwritten."""
    root = Path(game_dir).expanduser() / "viltrumitecore" / "scp096"
    installed = already = 0
    errors = []
    for relative, (sha, size) in files.items():
        dest = root.joinpath(*relative.split("/"))
        if dest.exists():
            if dest.is_file() and verify(dest.read_bytes(), sha, size):
                print("already: ", dest)
                already += 1
            else:
                errors.append(f"{relative}: target exists with different contents: {dest}")
            continue
        try:
            with closing(opener(UPSTREAM + relative, timeout=30)) as response:
                # Never accept huge or truncated/modified downloads.
                data = response.read(size + 1)
            if not verify(data, sha, size):
                raise ValueError("unexpected size, Ogg header or Git blob SHA1")
            dest.parent.mkdir(parents=True, exist_ok=True)
            # Atomic write, so a failed download cannot leave a corrupt .ogg.
            fd, tmp = tempfile.mkstemp(prefix=".scp096-", dir=dest.parent)
            try:
                with os.fdopen(fd, "wb") as out:
                    out.write(data)
                os.replace(tmp, dest)
            finally:
                if os.path.exists(tmp):
                    os.unlink(tmp)
            print("installed:", dest)
            installed += 1
        except (OSError, HTTPError, URLError, ValueError) as e:
            errors.append(f"{relative}: {e}")
    return installed, already, errors


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("game_dir", help="Minecraft game directory; for development: viltrumitecore/run")
    args = p.parse_args()
    installed, already, errors = install(args.game_dir)
    print(f"Original SCP-096 audio: {installed} installed, {already} already present, {len(errors)} errors.")
    for error in errors:
        print("ERROR:", error)
    print("Restart Minecraft or press F3+T to activate the original SCP: CB audio.")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
