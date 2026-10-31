#!/usr/bin/env python3
"""Generates the encyclopedia strings: viltrumitecore/src/main/lang-overlays/codex/<locale>.json.

Russian is the primary text, English is complete; the other shipped locales get the English text as the
project's fallback (the same as the wish phrases), so every locale has exactly the same keys.
Run: python3 tools/codex/codex_lang.py [--check]
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from codex_text_gui import GUI  # noqa: E402
from codex_text_heroes import HEROES  # noqa: E402
from codex_text_world import NPCS, EVENTS  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "viltrumitecore/src/main/lang-overlays/codex"
LOCALES = ["de_de", "en_us", "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve", "ru_ru", "tr_tr", "zh_cn"]
PREFIX = "codex.viltrumitecore."


def table(index):
    out = {}
    for key, pair in GUI.items():
        out[PREFIX + "gui." + key] = pair[index]
    for group in (HEROES, NPCS, EVENTS):
        for entry, fields in group.items():
            for field, pair in fields.items():
                out[PREFIX + entry + "." + field] = pair[index]
    return out


def main():
    check = "--check" in sys.argv
    ru, en = table(0), table(1)
    assert ru.keys() == en.keys()
    stale = []
    OUT.mkdir(parents=True, exist_ok=True)
    for loc in LOCALES:
        data = ru if loc == "ru_ru" else en
        text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
        path = OUT / (loc + ".json")
        if check:
            if not path.is_file() or path.read_text(encoding="utf-8") != text:
                stale.append(path.name)
        else:
            path.write_text(text, encoding="utf-8")
    if stale:
        print("stale codex lang files:", ", ".join(stale))
        sys.exit(1)
    print(("checked " if check else "wrote ") + str(len(LOCALES)) + " locales, " + str(len(ru)) + " keys")


if __name__ == "__main__":
    main()
