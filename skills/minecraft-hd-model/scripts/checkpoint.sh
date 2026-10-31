#!/bin/bash
# checkpoint.sh "message" [branch]  — add all, commit if changed, push, verify local == remote
set -e
BR=${2:-$(git rev-parse --abbrev-ref HEAD)}
git add -A
git diff --cached --quiet || git commit -q -m "$1"
git push -q origin HEAD:"$BR"
git fetch -q origin "$BR"
L=$(git rev-parse HEAD); R=$(git rev-parse "origin/$BR")
echo "local=$L remote=$R $([ "$L" = "$R" ] && echo MATCH || echo MISMATCH)"
