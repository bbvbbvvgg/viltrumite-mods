#!/usr/bin/env python3
"""Reinhard HUD art (Stage 1): knight emblem and hero icon copied from the
user archive unchanged, three 12x12 mode icons drawn here in the house pixel
style (closed fist on white, single gauntlet on blue, sword hilt on gold).
Stage 3+ ability icons (16x16, panel slots) are drawn here too and written
under textures/gui/ability/reinhard/.

Run: python3 tools/assets/make_reinhard_icons.py [--assets DIR] [--out ROOT] [--only NAME]
DIR = the unpacked reinhard_assets archive (its icons/ folder or the folder
itself); needed only for the copied files (without it they are skipped). ROOT defaults to viltrumitecore/src/main/binassets; every file is
written as <name>.png.b64 there and as a plain PNG preview under
build/reinhard_icons/.
"""
import argparse
import base64
import io
import os
import sys

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BIN = os.path.join(REPO, "viltrumitecore", "src", "main", "binassets")
GUI = "assets/viltrumitecore/textures/gui/reinhard/"
ABILITY_GUI = "assets/viltrumitecore/textures/gui/ability/reinhard/"
PREVIEW = os.path.join(REPO, "build", "reinhard_icons")

WHITE = (244, 241, 234, 255)
WHITE_D = (196, 190, 178, 255)
GOLD = (232, 193, 90, 255)
GOLD_D = (156, 122, 46, 255)
BLUE = (47, 93, 168, 255)
BLUE_D = (26, 52, 104, 255)
STEEL = (200, 206, 216, 255)
STEEL_D = (120, 128, 142, 255)
SKIN = (238, 200, 168, 255)
SKIN_D = (190, 142, 112, 255)
RED = (196, 40, 48, 255)
CLEAR = (0, 0, 0, 0)


def plate(fill, edge):
    """12x12 rounded tile: 1 px edge, corners cut."""
    img = Image.new("RGBA", (12, 12), CLEAR)
    px = img.load()
    for y in range(12):
        for x in range(12):
            corner = (x in (0, 11)) and (y in (0, 11))
            if corner:
                continue
            border = x in (0, 11) or y in (0, 11)
            px[x, y] = edge if border else fill
    return img


def paint(img, rows, palette, ox=0, oy=0):
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[ox + x, oy + y] = palette[ch]


def mode_restrained():
    """Closed bare fist on a white plate."""
    img = plate(WHITE, WHITE_D)
    paint(img, [
        "..kkkk..",
        ".kssssk.",
        "kssssssk",
        "ksdsdsdk",
        "kssssssk",
        ".kssssk.",
        "..kssk..",
        "..kkkk..",
    ], {"k": SKIN_D, "s": SKIN, "d": SKIN_D}, 2, 2)
    return img


def mode_knight():
    """Single steel gauntlet with a gold cuff on a blue plate."""
    img = plate(BLUE, BLUE_D)
    paint(img, [
        "..mmmm..",
        ".mssssm.",
        "mssssssm",
        "msmsmsmm",
        "mssssssm",
        ".mssssm.",
        ".gggggg.",
        ".gGGGGg.",
    ], {"m": STEEL_D, "s": STEEL, "g": GOLD, "G": GOLD_D}, 2, 2)
    return img


def mode_holy_sword():
    """Sword hilt (red jewel, gold guard, blade stub) on a gold plate."""
    img = plate(GOLD, GOLD_D)
    paint(img, [
        "...ww...",
        "...ws...",
        "...ws...",
        "bbbrrbbb",
        ".bbbbbb.",
        "...hh...",
        "...hh...",
        "..bbbb..",
    ], {"w": WHITE, "s": STEEL, "b": BLUE_D, "r": RED, "h": BLUE}, 2, 2)
    return img


def ability_plate(fill, edge):
    """16x16 panel slot tile: 1 px edge, corners cut."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if (x in (0, 15)) and (y in (0, 15)):
                continue
            px[x, y] = edge if x in (0, 15) or y in (0, 15) else fill
    return img


def light_punch():
    """Restrained: a bare fist thrown to the right with three speed lines."""
    img = ability_plate(WHITE, WHITE_D)
    paint(img, [
        "........kkkk..",
        ".......kssssk.",
        "ll....ksssssk.",
        "......ksdsdsdk",
        ".lll..kssssssk",
        "......ksdsdsdk",
        "ll....kssssssk",
        ".......kssssk.",
        "....kkkkksskk.",
        "....kssssssk..",
        "....kkkkkkk...",
    ], {"k": SKIN_D, "s": SKIN, "d": SKIN_D, "l": STEEL_D}, 1, 2)
    return img


def step_dash():
    """Restrained: a boot stepping right, a fading afterimage and speed lines behind."""
    img = ability_plate(WHITE, WHITE_D)
    paint(img, [
        ".........bbb..",
        ".........bsb..",
        "...ff....bsb..",
        "...ff....bsb..",
        "ll.ff....bsbb.",
        "...ff....bsssb",
        "lll.f....bsssb",
        "....ff...bbbbb",
        "ll..fff.......",
        "..............",
        ".llllllllllll.",
    ], {"b": BLUE_D, "s": STEEL, "f": WHITE_D, "l": STEEL_D}, 1, 2)
    return img


def catch_projectile():
    """Restrained: an open palm closing on an arrow flying in from the left."""
    img = ability_plate(WHITE, WHITE_D)
    paint(img, [
        "........k.k.k.",
        "........sksksk",
        ".......kssssss",
        "a.....ksssssss",
        ".a....kssssssk",
        "aaaaaGGssssssk",
        ".a....kssssssk",
        "a.....kssssk..",
        "......kssssk..",
        ".......kkkk...",
    ], {"k": SKIN_D, "s": SKIN, "a": STEEL_D, "G": GOLD_D}, 1, 3)
    return img


def fist_combo():
    """Knight: a steel gauntlet with a gold cuff and three impact marks."""
    img = ability_plate(BLUE, BLUE_D)
    paint(img, [
        ".......w...w..",
        "..mmmm..w.w...",
        ".mssssm.......",
        "mssssssm.www..",
        "msmsmsmm......",
        "mssssssm.w.w..",
        ".mssssm.w...w.",
        ".gggggg.......",
        ".gGGGGg.......",
    ], {"m": STEEL_D, "s": STEEL, "g": GOLD, "G": GOLD_D, "w": WHITE}, 1, 3)
    return img


def sky_kick():
    """Knight: a steel boot kicking straight up, a white ring and a trail above it."""
    img = ability_plate(BLUE, BLUE_D)
    paint(img, [
        "....w..w..w...",
        ".....wwwww....",
        "...w.......w..",
        ".....wwwww....",
        "....mmmmm.....",
        "....msssm.....",
        "....msssm.....",
        "....mssm......",
        "....mssm......",
        "....gggg......",
        "....gGGg......",
    ], {"m": STEEL_D, "s": STEEL, "g": GOLD, "G": GOLD_D, "w": WHITE}, 1, 2)
    return img


def grab_throw():
    """Knight: a steel gauntlet closing on a figure, a white arc of the throw above it."""
    img = ability_plate(BLUE, BLUE_D)
    paint(img, [
        ".......wwww...",
        ".....ww....w..",
        "....w.......w.",
        "..............",
        "...mmmm.......",
        "..mssssm......",
        "..msssssm.....",
        "..mmsssm......",
        "...mssm.......",
        "...gggg.......",
        "...gGGg.......",
    ], {"m": STEEL_D, "s": STEEL, "g": GOLD, "G": GOLD_D, "w": WHITE}, 1, 2)
    return img


def dash_behind():
    """Knight: a white afterimage on the left, a curved arrow around a steel figure to its back."""
    img = ability_plate(BLUE, BLUE_D)
    paint(img, [
        "..w......mm...",
        ".www....mssm..",
        "..w.....mssm..",
        ".www..wwwmm...",
        ".w.w.w...ss...",
        ".w.ww...msssm.",
        "....w...msssm.",
        "....ww...ss...",
        ".....wwggs.s..",
        "......wGg..s..",
        ".......g......",
    ], {"m": STEEL_D, "s": STEEL, "g": GOLD, "G": GOLD_D, "w": WHITE}, 1, 2)
    return img


def stomp():
    """Knight: a steel boot slamming down, a gold crack and white rings on the ground."""
    img = ability_plate(BLUE, BLUE_D)
    paint(img, [
        ".....mmm......",
        ".....msm......",
        ".....msm......",
        ".....mssmm....",
        ".....msssssm..",
        ".....mmmmmmm..",
        "..w....g....w.",
        ".w....ggg....w",
        "ww...g.G.g...w",
        ".wwwwg...gwww.",
        "....g.....g...",
    ], {"m": STEEL_D, "s": STEEL, "g": GOLD, "G": GOLD_D, "w": WHITE}, 1, 2)
    return img


def reflect():
    """Knight: a steel backhand swatting a white arrow back along its own path."""
    img = ability_plate(BLUE, BLUE_D)
    paint(img, [
        "..........mm..",
        ".........mssm.",
        "..w.....msssm.",
        ".ww.....mssm..",
        "wwwwwwwmssm...",
        ".ww....mssm...",
        "..w.....mm....",
        "........gg....",
        "........gG....",
        "..www...gg....",
        ".w..www.......",
    ], {"m": STEEL_D, "s": STEEL, "g": GOLD, "G": GOLD_D, "w": WHITE}, 1, 2)
    return img



def reid():
    """Holy Sword: Reid in its sheath, a diagonal blue scabbard with gold fittings, the white hilt up."""
    img = ability_plate(GOLD, GOLD_D)
    paint(img, [
        "...........ww.",
        "..........wsw.",
        ".........GGw..",
        "........GbG...",
        ".......bbb....",
        "......bhb.....",
        ".....bhb......",
        "....GbG.......",
        "...bhb........",
        "..bhb.........",
        ".GbG..........",
        ".GG...........",
    ], {"w": WHITE, "s": STEEL, "b": BLUE_D, "h": BLUE, "G": GOLD_D}, 1, 2)
    return img


def reid_drawn():
    """Holy Sword: Reid drawn, a bright white blade with a red edge glint, the hilt down-left."""
    img = ability_plate(GOLD, GOLD_D)
    paint(img, [
        "............w.",
        "...........ww.",
        "..........wsw.",
        ".........wsw..",
        "........wsw...",
        ".......wsw....",
        "......wsr.....",
        ".....wsw......",
        "..G.wsw.......",
        "...GGw........",
        "..bGG.........",
        ".bb..G........",
    ], {"w": WHITE, "s": STEEL, "r": RED, "b": BLUE_D, "G": GOLD_D}, 1, 2)
    return img


def lethal_chop():
    """Lethal Chop: a flat white hand-blade arc with a crimson edge sweeping across the slot."""
    img = ability_plate(GOLD, GOLD_D)
    paint(img, [
        "..............",
        "....rrrrrr....",
        "..rrwwwwwwrr..",
        ".rww......wwr.",
        "rw..........wr",
        "w............w",
        "..............",
        "....sss.......",
        "...sssss......",
        "..kssssk......",
        "...kkkk.......",
        "..............",
    ], {"w": WHITE, "r": RED, "s": SKIN, "k": SKIN_D}, 1, 2)
    return img

ABILITIES = {
    "light_punch": light_punch,
    "step_dash": step_dash,
    "catch": catch_projectile,
    "fist_combo": fist_combo,
    "sky_kick": sky_kick,
    "grab_throw": grab_throw,
    "dash_behind": dash_behind,
    "stomp": stomp,
    "reflect": reflect,
    "reid": reid,
    "reid_drawn": reid_drawn,
    "lethal_chop": lethal_chop,
}

DRAWN = {
    "mode_restrained": mode_restrained,
    "mode_knight": mode_knight,
    "mode_holy_sword": mode_holy_sword,
}
COPIED = {"emblem": "knight_emblem.png", "hero": "reinhard.png", "blessing_bearer": "blessing_bearer.png"}


def find_source(assets, name):
    for candidate in (os.path.join(assets, "icons", name), os.path.join(assets, name)):
        if os.path.isfile(candidate):
            return candidate
    sys.exit("missing %s in %s" % (name, assets))


def write(out_root, name, data, gui=GUI):
    target = os.path.join(out_root, gui + name + ".png.b64")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w", encoding="ascii") as f:
        f.write(base64.b64encode(data).decode("ascii") + "\n")
    os.makedirs(PREVIEW, exist_ok=True)
    with open(os.path.join(PREVIEW, name + ".png"), "wb") as f:
        f.write(data)
    print("wrote", os.path.relpath(target, REPO))


def png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", help="unpacked reinhard_assets archive (copied files only)")
    parser.add_argument("--out", default=BIN)
    parser.add_argument("--only", help="rebuild one file (name without .png)")
    args = parser.parse_args()
    names = list(COPIED) + list(DRAWN) + list(ABILITIES)
    if args.only:
        if args.only not in names:
            sys.exit("unknown name %s; known: %s" % (args.only, ", ".join(names)))
        names = [args.only]
    for name in names:
        if name in COPIED:
            if not args.assets:
                print("skipped", name, "(no --assets)")
                continue
            with open(find_source(args.assets, COPIED[name]), "rb") as f:
                write(args.out, name, f.read())
        elif name in ABILITIES:
            write(args.out, name, png_bytes(ABILITIES[name]()), ABILITY_GUI)
        else:
            write(args.out, name, png_bytes(DRAWN[name]()))


if __name__ == "__main__":
    main()
