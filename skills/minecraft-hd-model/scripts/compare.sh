#!/bin/bash
# Before/after sheets: compare.sh OLD_SHA OUT_DIR [render_views args after the output name...]
# Example: compare.sh 5071d858 /tmp/cmp --center 0,1.72,0 --size 0.85 captain/captain:captain/captain captain/captain_gear:captain/captain_gear
set -e
OLD=$1; OUT=$2; shift 2
REPO=$(git rev-parse --show-toplevel); mkdir -p "$OUT"
WT=$(mktemp -d); git -C "$REPO" worktree add -f "$WT" "$OLD" >/dev/null
cp "$REPO/skills/minecraft-hd-model/scripts/render_views.py" "$WT/rv.py"
(cd "$WT" && python3 rv.py "$OUT/before.png" "$@")
(cd "$REPO" && python3 skills/minecraft-hd-model/scripts/render_views.py "$OUT/after.png" "$@")
git -C "$REPO" worktree remove --force "$WT"
python3 - "$OUT" <<'PY'
import sys; from PIL import Image
o = sys.argv[1]; a, b = Image.open(o + '/before.png'), Image.open(o + '/after.png')
im = Image.new('RGB', (max(a.width, b.width), a.height + b.height)); im.paste(a, (0, 0)); im.paste(b, (0, a.height)); im.save(o + '/before_after.png')
print('saved', o + '/before_after.png')
PY
