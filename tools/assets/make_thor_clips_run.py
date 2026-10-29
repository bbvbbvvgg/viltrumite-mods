"""make_thor_clips.py's runner: bake, check, first person, write (split into pushable files)."""
import argparse
import json
import math
import os
import sys
import time

import numpy as np

import thor_clipcore as C
import thor_clipkit as K
import thor_mesh as M

OUT = os.path.join(K.RES, "animations/thor")
FILE_LIMIT = 10 ** 9  # one file: the runtime (ThorAssets.ANIMATIONS) and the contract tests read only thor.animation.json
BASE_FILE = "thor.animation.json"
# Every clip in thor.animation.json (the old push-size split into several files was never loaded by AnimCache).
GROUPS = (("thor.animation.json", ("idle", "walk", "run", "stance", "lift", "flight", "throw", "catch", "pull", "crouch", "sit",
                                   "draw", "holster", "electric", "wrath", "hammer_", "fist_")),)
OWNERS = {
    "full": (C.TP_BONES, C.POS_BONES),
    "upper": (C.UPPER_BONES, ()),
    "lift": (C.TP_BONES + ("head",), C.POS_BONES),
    "pull": (tuple(b for b in C.TP_BONES if b not in ("right_arm", "right_forearm", "right_hand", "mjolnir_grip")), C.POS_BONES),
}


def fp_loop_samples(clip, plan):
    bob, sway, cycles, breath = plan.loop
    out = []
    prev = None
    rest = C.FP_HAMMER_R if plan.hammer else C.FP_FIST_R
    for t in range(clip.length + 1):
        ph = 2 * math.pi * cycles * t / clip.length
        d = np.array([sway * math.sin(ph), -bob * abs(math.sin(ph)) + breath * math.sin(2 * math.pi * t / clip.length), 0.0])
        pose = K.fp_rest()
        ham = C.FP_HAMMER_DIR if plan.hammer else None
        K.solve_fp_arm(pose, False, rest + d, ham=ham)
        K.solve_fp_arm(pose, True, C.FP_HIDDEN_L, ham=None)
        if prev is not None:
            for bn in C.FP_BONES:
                pose.rot[bn] = C.R.euler_near(C.R.rzyx(pose.rot[bn]), prev.rot[bn])
            pose.touch()
        out.append((t, pose, {}))
        prev = pose
    return out


def runs(flags):
    best = cur = 0
    for f in flags:
        cur = cur + 1 if f else 0
        best = max(best, cur)
    return best


def clip_json(clip, samples, bones, pos_bones, table_row, extra=None, keep=()):
    ch = C.channels(samples, bones, pos_bones)
    out = {"loop": True if clip.loop else "hold_on_last_frame", "animation_length": round(clip.length * C.TICK, 4)}
    if table_row:
        out["timeline"] = {("%.4g" % (clip.contact * C.TICK)): "contact"}
    out["bones"] = C.write_channels(ch, clip.length, keep)
    if extra:
        out["bones"].update(extra)
    return out


def main(all_clips, table):
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--only")
    ap.add_argument("--quiet", action="store_true")
    ap.add_argument("--report")
    a = ap.parse_args()
    clips = all_clips()
    names = [c.name for c in clips]
    assert len(set(names)) == len(names), "duplicate clip names"
    missing = [n for n in table if n not in names]
    assert not missing, "table rows without a clip: %s" % missing
    if a.only:
        want = set(a.only.split(","))
        clips = [c for c in clips if c.name in want]
    global CLIPS, TABLE
    CLIPS, TABLE = clips, table
    t0 = time.time()
    if len(clips) > 1:
        import multiprocessing as mp
        with mp.get_context("fork").Pool(min(2, os.cpu_count() or 1)) as pool:
            results = pool.map(process, range(len(clips)), chunksize=1)
    else:
        results = [process(0)]
    anims, report, problems = {}, {}, []
    for name, out, rep, probs, line in results:
        anims.update(out)
        report[name] = rep
        problems += probs
        if not a.quiet:
            print(line)
    print("baked %d clips in %.1fs" % (len(clips), time.time() - t0))
    for p in problems:
        print("PROBLEM", p)
    if a.report:
        json.dump(report, open(a.report, "w"), indent=1, default=float)
    if a.check or a.only:
        return
    write(anims)



CLIPS, TABLE = [], {}


def process(index):
    clip = CLIPS[index]
    table = TABLE
    anims, problems = {}, []
    tp = C.bake_tp(clip)
    worst = []
    ground = []
    miss = {}
    for t, pose, m in tp:
        for k, v in m.items():
            miss[k] = max(miss.get(k, 0.0), v)
        hits = M.scan(pose, K.HAMMER if clip.hammer_at(t) else None, belt_model=C.BELT if clip.belt_at(t) else None,
                   belt_grip=C.BELT_GRIP)
        if hits:
            worst.append((t, hits[0]))
        if not getattr(clip, "airborne", False) and not any(k[1].get("legs") for k in clip.keys):
            r, l = M.feet(pose)
            if min(r, l) < -1.0 or min(r, l) > 1.0:
                ground.append((t, round(float(r), 2), round(float(l), 2)))
    need = {k: round(max(float(getattr(p, "need", {}).get(k, 0.0)) for _, p, _ in tp), 1) for k in ("kr", "kl", "hu", "lr", "bsr", "bsp")}
    rep = {"length": clip.length, "misses": {k: round(v, 2) for k, v in miss.items()}, "need": need,
           "hits": [(t, h[0], h[1], round(float(h[2]), 2)) for t, h in worst], "ground": ground}
    bones, pos = OWNERS[clip.owner]
    keep = sorted(set((clip.beats or {}).values())) if clip.beats else []
    extra = None
    if clip.owner == "lift":
        extra = {"head": {"rotation": {"0": [-28.0, 0.0, 0.0]}}, "belly": {"rotation": {"0": [-4.0, 0.0, 0.0]}}}
        bones = C.TP_BONES
    anims[clip.name] = clip_json(clip, tp, bones, pos, clip.name in table, extra, keep)
    if clip.fp is not None:
        plan = clip.fp
        if getattr(plan, "loop", None) is not None:
            fp = fp_loop_samples(clip, plan)
            clip.fp_left = False
        else:
            proxy = None
            if any(abs(float(k[1].get("spin", 0.0))) > 1 for k in clip.keys):
                saved = clip.keys
                clip.keys = [(t, dict(it, spin=float(it.get("spin", 0.0)) * clip.fp_spin), e) for t, it, e in saved]
                proxy = C.bake_tp(clip)
                clip.keys = saved
            fp = C.fp_bake(clip, tp, plan, proxy)
        nears, covers, cuts, fmiss = [], [], [], 0.0
        for t, pose, m in fp:
            n, cut, cov = C.check_fp(pose, clip.hammer_at(t), clip.fp_left)
            nears.append(n)
            covers.append(cov)
            if cut:
                cuts.append(t)
            fmiss = max([fmiss] + [v for k, v in m.items() if k.startswith("fp_")])
        rep["fp_near"] = round(float(max(nears)), 3)
        rep["fp_cut_ticks"] = cuts
        rep["fp_cover_run"] = runs([c for (t, _, _), c in zip(fp, covers) if clip.contact is None or abs(t - clip.contact) > 1])
        rep["fp_cover_ticks"] = [t for (t, _, _), c in zip(fp, covers) if c]
        rep["fp_miss_px"] = round(float(fmiss), 2)
        fp_bones = C.FP_BONES if clip.fp_left else tuple(b for b in C.FP_BONES if b not in C.FP_LEFT_BONES)
        anims[clip.name + "_fp"] = clip_json(clip, fp, fp_bones, tuple(b for b in C.FP_POS_BONES if b in fp_bones),
                                             clip.name in table, None, keep)
        if cuts:
            problems.append("%s_fp: the near plane cuts a cube on screen at ticks %s" % (clip.name, cuts))
        if rep["fp_cover_run"] > 2:
            problems.append("%s_fp: covers the crosshair %d ticks in a row" % (clip.name, rep["fp_cover_run"]))
    if worst:
        problems.append("%s: %d ticks with penetration, worst %s" % (clip.name, len(worst), max(rep["hits"], key=lambda h: h[3])))
    if ground:
        problems.append("%s: %d ticks with the feet off the ground (px), e.g. %s" % (clip.name, len(ground), ground[0]))
    for k, v in miss.items():
        if v > 0.6:
            problems.append("%s: %s misses its target by %.2f px" % (clip.name, k, v))
    line = "%-26s %3dt hits:%-3d miss:%s fp:%s need:%s" % (clip.name, clip.length, len(worst),
        {k: round(float(v), 1) for k, v in miss.items() if v > 0.3}, {k: rep.get(k) for k in ("fp_near", "fp_cut_ticks", "fp_cover_run")},
        {k: v for k, v in need.items() if v})
    return clip.name, anims, rep, problems, line

def write(anims):
    base = json.load(open(os.path.join(OUT, BASE_FILE)))["animations"]
    for keep in ("crouch", "sit", "lift_squat"):  # R1's clips, kept as R1 wrote them
        anims[keep] = base[keep]
    files = {}
    for name in sorted(anims, key=lambda n: (n.endswith("_fp"), n)):
        stem = name[:-3] if name.endswith("_fp") else name
        for fname, prefixes in GROUPS:
            if any(stem == p or stem.startswith(p) for p in prefixes):
                files.setdefault(fname, []).append(name)
                break
        else:
            raise SystemExit("no file group for " + name)
    for f in os.listdir(OUT):
        if f.endswith(".animation.json"):
            os.remove(os.path.join(OUT, f))
    written = []
    for fname, names in files.items():
        names = sorted(names, key=lambda n: (n[:-3] if n.endswith("_fp") else n, n.endswith("_fp")))
        parts = [[]]
        for n in names:
            trial = parts[-1] + [n]
            if parts[-1] and len(json.dumps(dump({k: anims[k] for k in trial}))) > FILE_LIMIT:
                parts.append([n])
            else:
                parts[-1] = trial
        for i, part in enumerate(parts):
            out = fname if i == 0 else fname.replace(".animation.json", "_%d.animation.json" % (i + 1))
            text = dump({k: anims[k] for k in part})
            open(os.path.join(OUT, out), "w").write(text)
            written.append((out, len(text), len(part)))
    for out, size, n in written:
        print("wrote %-40s %6d bytes %3d clips" % (out, size, n))


def dump(animations):
    return json.dumps({"format_version": "1.8.0", "animations": animations}, separators=(",", ":"), ensure_ascii=False) + "\n"
