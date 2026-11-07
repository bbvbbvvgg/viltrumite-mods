# T19 — `/city districts` debug command + `districtAt` boundary test

Branch: `swarm/city-districts-cmd` (worktree /data/home/work/swarm/t19-city-districts)
Status: implementing

## Problem (from the brief)
`CityProfile.districtAt` feeds subtitles, `/city here`, `OutageInfo` (PowerGrid district naming), Chitauri
war sectors and `/city status` active-district lines — but there is no debug view of the districts
themselves and no test of the boundary rules (containment box, +1 edge, 64-block nearest fallback, null
beyond).

## Verified facts about the lookup
- `District.contains(x, z)`: box [minX..maxX] x [minZ..maxZ] **plus one** (`x <= maxX + 1`): the box is
  half-open on the block grid; a block at maxX+1 (the wall of the next 128-tile) still belongs.
- `District.distanceSq`: distance to the box (0 inside; clamp on each axis separately; y ignored).
- `CityProfile.districtAt(x, z)`: first box that contains wins immediately (boxes are sorted smallest
  first by walk.py, so the most specific wins); otherwise the nearest box **centre-line distance within
  64** (`bestD = 64*64`, strict `<`), else null.
- Ties: the first district with distance < bestD wins (earlier in the list wins at exactly equal
  distance — the sort is smallest-area-first, stable).
- Names come from the map's signs (analysis/signs.py → districts.json anchors); blocks further than
  ANCHOR_RANGE=900 from every anchor get the city name (walk.py make_districts).

## Fix (the two places the brief names)
- `CityCommands`: new literal `districts` (permission 2, right after `here`), prints:
  1. a header line (count, city name);
  2. one line per district: name, box (minX..maxX, minZ..maxZ), walkCells, and the share of walk cells;
  3. the caller's own position resolved through `districtAt` (or "none") — the same line shows the
     distance to that district's box when it is the nearest-outside case;
  4. a hint that `here` shows the street-level probe.
  All strings translatable in the city lang namespace (`viltrumitecore.city.districts.*`, 12 locales).
  Lang rule: update all 12 files together (AGENTS.md §8).
- Test `CityProfileDistrictsTest` (pure, no Bootstrap needed — CityProfile only uses Gson):
  containment including the +1 edges; distanceSq symmetric edge cases (outside on one axis, inside);
  districtAt: inside a small box inside a big box (specificity), nearest within 64 on the X axis,
  nearest within 64 on the corner, exactly-64 boundary (in), 64.001 (null-equivalent → nearest wins
  only if another box covers), far away → null, first-wins tie, empty districts list → null.
- `tools/atheria/districts.json`: no change needed — anchors stay as they are (the brief lists it as a
  file of interest; the anchors are correct and the debug command reads the built profile, not the
  anchors).

## Command shape (following `here`/`status` conventions)
- `/city districts` — permission 2 like `here` (it is a debug probe).
- Output stays one component per district (like PowerCommands status lines), GRAY, header GOLD.
