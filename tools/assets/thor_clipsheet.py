#!/usr/bin/env python3
"""Quick contact sheets straight from make_thor_clips.py's solver (iteration aid; the committed
sheets come from ThorClipPreviewTest, which poses the written JSON with the game's transforms).

  python3 tools/assets/thor_clipsheet.py CLIP [CLIP ...] [--every N] [--out DIR]
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import make_thor_clips as M  # noqa: E402
import make_thor_clips_run as RUN  # noqa: E402
import preview_thor_clips as PV  # noqa: E402
import thor_clipcore as C  # noqa: E402
import thor_clipkit as K  # noqa: E402
import thor_mesh as MESH  # noqa: E402

PX = 1 / 16.0


def frame(tick, beat, boxes, hits, scale):
    return {"tick": tick, "beat": beat, "boxes": [{"m": b.model, "b": b.bone, "c": [list(map(float, c * scale)) for c in b.corners()]}
                                                  for b in boxes],
            "hits": [{"a": a, "b": b, "px": float(d)} for a, b, d in hits]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("clips", nargs="+")
    ap.add_argument("--every", type=int, default=0)
    ap.add_argument("--ticks", help="comma list of ticks (overrides beats / every)")
    ap.add_argument("--out", default="/data/dbg")
    ap.add_argument("--cell", default="150x180")
    a = ap.parse_args()
    cell = tuple(int(v) for v in a.cell.split("x"))
    allc = {c.name: c for c in M.all_clips()}
    for name in a.clips:
        clip = allc[name]
        RUN.CLIPS, RUN.TABLE = [clip], M.TABLE
        beats = dict(clip.beats or {})
        ticks = sorted(set(beats.values())) if beats and not a.every else list(range(0, clip.length + 1, a.every or 4))
        if a.ticks:
            ticks = [int(v) for v in a.ticks.split(",")]
        label = {v: k for k, v in beats.items()}
        tp = {t: p for t, p, _ in C.bake_tp(clip)}
        def boxes(t):
            return K.tp_boxes(tp[t], clip.hammer_at(t)) + (C.belt_boxes(tp[t]) if clip.belt_at(t) else [])

        def hits(t):
            return MESH.scan(tp[t], K.HAMMER if clip.hammer_at(t) else None, belt_model=C.BELT if clip.belt_at(t) else None,
                   belt_grip=C.BELT_GRIP)
        frames = [frame(t, label.get(t, ""), boxes(t), hits(t), PX) for t in ticks]
        data = {"clip": name, "length": clip.length, "contact": clip.contact, "body": "R1 (python solver)", "frames": frames}
        PV.sheet(data, cell).save(os.path.join(a.out, name + ".png"))
        if clip.fp is not None:
            _, anims, rep, probs, _ = RUN.process(0)
            plan = clip.fp
            if getattr(plan, "loop", None) is not None:
                fp = RUN.fp_loop_samples(clip, plan)
                clip.fp_left = False
            else:
                fp = C.fp_bake(clip, list((t, tp[t], {}) for t in sorted(tp)), plan)
            fpd = {t: p for t, p, _ in fp}
            arms = ("r", "l") if getattr(clip, "fp_left", True) else ("r",)
            frames = [frame(t, label.get(t, ""), K.fp_boxes(fpd[t], clip.hammer_at(t), arms), [], 1.0) for t in ticks]
            data = {"clip": name + "_fp", "length": clip.length, "contact": clip.contact, "body": "thor_arms", "fp": True,
                    "eye": [0, 0, 0], "frames": frames}
            PV.sheet(data, cell).save(os.path.join(a.out, name + "_fp.png"))
            print(name, [p for p in probs])
        print("wrote", os.path.join(a.out, name + ".png"))


if __name__ == "__main__":
    main()
