#!/usr/bin/env python3
"""Contact sheets for the 2026-11-02 Sinclair playtest (copy cinematic and reworked acts).

Same format as ../make_sheets.py (SVG wrapping a WebP, the branch tooling pushes text only). From the
repo root, after a run with -Dviltrumitecore.sinclair.playtest=true -Dviltrumitecore.sinclair.playtest.ultOnly=true:

    python3 docs/reports/sinclair-playtest/2026-11-02/make_sheets.py viltrumitecore/run/screenshots
"""
import glob, os, sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import make_sheets  # noqa: E402

make_sheets.OUT = os.path.dirname(os.path.abspath(__file__))
ACTS = ["beam", "nova", "cut", "barrage", "aegis", "finale"]


def pick(src, *names):
    out = []
    for n in names:
        p = os.path.join(src, f"sinclair_{n}.png")
        if os.path.exists(p):
            out.append(p)
        else:
            print("missing", n)
    return out


def main():
    src = sys.argv[1]
    make_sheets.sheet(pick(src, "copy_01_camera_in", "copy_02_unfold", "copy_03_card_hold", "copy_05_fold"), "01_copy_cinematic")
    make_sheets.sheet(pick(src, "copy_04_bystander", "copy_06_return", "copy_07_off_window", "copy_08_abort"), "02_copy_side_off_abort")
    make_sheets.sheet(pick(src, "copy_card_nova", "copy_card_cut", "copy_card_barrage", "copy_card_aegis"), "03_copy_cards")
    for i, act in enumerate(ACTS):
        make_sheets.sheet(pick(src, f"act_{act}_1_tension", f"act_{act}_2_main", f"act_{act}_4_reveal", f"act_{act}_6_bow"), f"{4 + i:02d}_act_{act}")
    make_sheets.sheet(pick(src, *[f"act_{a}_4_reveal_own_view" for a in ACTS[:4]]), "10_acts_own_view_a")
    make_sheets.sheet(pick(src, *[f"act_{a}_4_reveal_own_view" for a in ACTS[4:]], "act_beam_3_hush", "act_beam_5_pause"), "11_acts_own_view_b_beam_hush")


if __name__ == "__main__":
    main()
