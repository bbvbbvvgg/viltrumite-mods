#!/usr/bin/env python3
"""Ability icons of Regulus, Homelander and Iron Man in the house style of the Reinhard icons.

The art lives in <hero>_icon_art.py on top of the shared kit icon_art.py (frame, backdrop,
outlines, light, sparkles); Reinhard keeps his own driver (make_reinhard_icons.py) and stays the
reference. Every icon is 32x32 art saved 2x nearest-neighbour as a 64x64 RGBA PNG, written as
<name>.png.b64 under src/main/binassets at the asset path the Java code already uses:

    regulus     textures/gui/ability/regulus/<name>.png      (6)
    homelander  textures/gui/ability/homelander/<name>.png   (6 panel + 2 flight bar)
    ironman     textures/gui/ability/ironman/<name>.png      (9 kit incl. legion + 7 signatures + 3 Hulkbuster)

Run:
    python3 tools/assets/make_hero_icons.py                    # all heroes
    python3 tools/assets/make_hero_icons.py --hero regulus --only mania
    python3 tools/assets/make_hero_icons.py --check            # committed files == generated?
Previews (never shipped) go to build/hero_icons/: plain PNGs, a contact sheet per hero (art view,
GUI scale 2 and 3) and a real-scale HUD mock with the hero's panel style.
"""
import argparse
import base64
import importlib
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import icon_preview as preview  # noqa: E402

REPO = os.path.dirname(os.path.dirname(HERE))
BIN = os.path.join(REPO, "viltrumitecore", "src", "main", "binassets")
PREVIEW = os.path.join(REPO, "build", "hero_icons")
HEROES = ["regulus", "homelander", "ironman"]


def module(hero):
    return importlib.import_module(hero + "_icon_art")


def png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def target(root, mod, name):
    return os.path.join(root, "assets", "viltrumitecore", mod.FOLDER + name + ".png.b64")


def encode(data):
    return base64.b64encode(data).decode("ascii") + "\n"


def write_previews(hero, mod, images):
    out = os.path.join(PREVIEW, hero)
    os.makedirs(out, exist_ok=True)
    for name, img in images.items():
        img.save(os.path.join(out, name + ".png"))
    preview.contact_sheet(list(images.items()), hero + " ability icons", cols=4).save(
        os.path.join(PREVIEW, hero + "_sheet.png"))
    flight = [images.get(n) for n in mod.FLIGHT]
    huds = []
    for page, slots in enumerate(mod.HUD):
        panel = [images.get(n) if n else None for n in slots]
        keys = tuple(k if n else "" for k, n in zip(("R", "Y", "Z", "X", "C", "V"), slots))
        huds.append(preview.hud(panel, flight, style=mod.PANEL, scale=2, keys=keys, page=page + 1))
        huds.append(preview.hud(panel, flight, style=mod.PANEL, scale=3, keys=keys, page=page + 1,
                                background=preview.night_background))
    preview.row(huds).save(os.path.join(PREVIEW, hero + "_hud.png"))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--hero", choices=HEROES, action="append", help="hero to draw (repeatable; default all)")
    ap.add_argument("--only", help="draw a single icon name")
    ap.add_argument("--out", default=BIN, help="binassets root (default: viltrumitecore/src/main/binassets)")
    ap.add_argument("--check", action="store_true", help="compare with the committed files, write nothing")
    ap.add_argument("--no-preview", action="store_true", help="skip build/hero_icons previews")
    args = ap.parse_args()
    stale = []
    for hero in args.hero or HEROES:
        mod = module(hero)
        names = [args.only] if args.only else mod.NAMES
        if args.only and args.only not in mod.NAMES:
            continue
        images = {}
        for name in names:
            img = mod.icon(name)
            assert img.size == (64, 64) and img.mode == "RGBA", (hero, name, img.size, img.mode)
            images[name] = img
            text = encode(png_bytes(img))
            path = target(args.out, mod, name)
            if args.check:
                try:
                    with open(path, encoding="ascii") as f:
                        same = f.read() == text
                except FileNotFoundError:
                    same = False
                if not same:
                    stale.append(os.path.relpath(path, REPO))
                continue
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="ascii") as f:
                f.write(text)
        if not args.check and not args.no_preview and not args.only:
            write_previews(hero, mod, images)
        print("%s: %d icon(s)%s" % (hero, len(images), " checked" if args.check else " written"))
    if stale:
        print("out of date:\n  " + "\n  ".join(stale))
        sys.exit(1)


if __name__ == "__main__":
    main()
