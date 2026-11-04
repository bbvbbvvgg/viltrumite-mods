#!/bin/bash
# Repeatable Atheria smoke/collapse benchmark through the dev QA harness (SubtitlePlaytest).
# Start the client with -Dviltrumitecore.subtitles.playtest=true -Dviltrumitecore.subtitles.playtest.world=true
# on a fresh copy of Atheria v2.06 (save name subtitle_playtest), wait until the player is in the world, then:
#   tools/perf/atheria_bench.sh <label> [render distance]
# Results: grep 'SUBPT.*perf' in the client log (avg FPS, 1 % low, p50/p99/max frame ms, GC, heap, particles).
# Screenshots (needs ImageMagick `import` and DISPLAY): $OUT/<label>_*.png
# Commands are spaced by >= 4 s because the harness polls the file every 5 client ticks and a slow frame may
# otherwise swallow a line.
set -e
LABEL=${1:?label}; RD=${2:-6}
RUN=${RUN_DIR:-$(cd "$(dirname "$0")/../../viltrumitecore/run" && pwd)}
OUT=${OUT:-$RUN/perf-shots}; mkdir -p "$OUT"
q(){ echo "$1" > "$RUN/subtitle_qa.txt"; sleep "${2:-4}"; }
shot(){ if [ -n "$DISPLAY" ] && command -v import >/dev/null; then import -window root "$OUT/${LABEL}_$1.png"; fi; }
win(){ q "perfreset" "$2"; q "perfstate ${LABEL}_$1" 5; }

q "rd $RD" 6
q "cmd gamemode creative"; q "cmd time set noon"; q "cmd gamerule doDaylightCycle false"; q "cmd weather clear"
q "clouds off"; q "cmd gamerule doMobSpawning false"; q "cmd city clear"; q "cmd aftermath clear"; q "cmd collapse clear"
q "tp 1384 -44 -250 0 -8" 12; q "cmd clear"
win A_idle 20; shot A_idle
# C: destroyed-building smoke (ruin zone 22 blocks ahead, impact stage)
q "cmd execute positioned ^ ^ ^22 run aftermath collapse 14 1.0" 30
win C_live 20; shot C_live
q "freeze on" 8
for i in 1 2; do
  q "smoketrim on" 6; shot C_on$i; win C_frozen_trimON_$i 20
  q "smoketrim off" 6; shot C_off$i; win C_frozen_trimOFF_$i 20
done
q "smoketrim on"; q "freeze off"; q "cmd aftermath clear" 6
# B: real collapse of the glass pyramid base, watched from 34 blocks
q "tp 1384 -40 -262 0 -5" 10
q "perfreset" 1; q "cmd collapse cut 1368 -44 -244 1400 -42 -210" 30
q "perfstate ${LABEL}_B_live_first30s" 5; q "collapsestate" 5; shot B_live
q "freeze on" 8
for i in 1 2; do
  q "smoketrim on" 6; shot B_on$i; win B_frozen_trimON_$i 20
  q "smoketrim off" 6; shot B_off$i; win B_frozen_trimOFF_$i 20
done
q "smoketrim on"; q "freeze off" 5
win B_after 20
echo "done: grep -a 'SUBPT.*perf ${LABEL}_' <client log>"
