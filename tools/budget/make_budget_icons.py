#!/usr/bin/env python3
"""Writes the 150 Ultimate Hero Expansion ability icons (64x64, Reinhard style).

Output: viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/ability/budget/<hero>/<ability>.png.b64
(decoded by the decodeBinaryAssets Gradle task) and PNG previews plus a contact
sheet under build/budget_icons/.
Run from the repository root: python3 tools/budget/make_budget_icons.py [--only HERO]
"""
import argparse
import base64
import io
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import budget_icon_art as art  # noqa: E402
import make_budget_roster as roster  # noqa: E402

REPO = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(REPO, "viltrumitecore", "src", "main", "binassets", "assets", "viltrumitecore", "textures", "gui", "ability", "budget")
PREVIEW = os.path.join(REPO, "build", "budget_icons")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only")
    a = ap.parse_args()
    os.makedirs(PREVIEW, exist_ok=True)
    sheet_rows = []
    count = 0
    for hero in roster.H:
        if a.only and hero["key"] != a.only:
            continue
        row = []
        for move in hero["moves"]:
            img = art.draw(hero, move).resize((64, 64), Image.NEAREST)
            buf = io.BytesIO()
            img.save(buf, "PNG", optimize=True)
            d = os.path.join(OUT, hero["key"])
            os.makedirs(d, exist_ok=True)
            with open(os.path.join(d, move[1] + ".png.b64"), "w") as f:
                f.write(base64.b64encode(buf.getvalue()).decode("ascii") + "\n")
            img.save(os.path.join(PREVIEW, f"{hero['key']}_{move[1]}.png"))
            row.append(img)
            count += 1
        sheet_rows.append(row)
    cols = 4
    sheet = Image.new("RGBA", (cols * 68 + 4, len(sheet_rows) * 68 + 4), (40, 40, 40, 255))
    for j, row in enumerate(sheet_rows):
        for i, img in enumerate(row):
            sheet.paste(img, (4 + i * 68, 4 + j * 68), img)
    sheet.save(os.path.join(PREVIEW, "sheet.png"))
    print(f"{count} icons")


if __name__ == "__main__":
    main()
