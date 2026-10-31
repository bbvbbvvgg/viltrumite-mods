#!/bin/bash
# usage: ckpt.sh "commit message"  (stages all tracked/untracked changes in repo except run dirs)
set -e
cd /data/repo
git status --short | head -40
git add -A
if ! git diff --cached --quiet; then git commit -q -m "$1"; fi
git push -q origin HEAD:integ/captain-america-main
git fetch -q origin integ/captain-america-main
L=$(git rev-parse HEAD); R=$(git rev-parse origin/integ/captain-america-main)
echo "local=$L remote=$R $([ "$L" = "$R" ] && echo MATCH || echo MISMATCH)"
