# Holy Sword cinematic overhaul

Source: PR #36 HEAD `69d094d0be5bb1e11c192266159c6df84a86cdc3`.
Branch: `feat/reinhard-holy-sword-cinematic`; separate PR, not PR #35.

## Invariants
Approved narrow blade, final free-blade roll, hip frame and sheath geometry remain untouched.
World Erasure and its shader are not rewritten. Append-only wire enums/registrations.
All block visits share CarveJobs/CarveBudget, loaded chunks only, no neighbour flood.

## Diagnosis
Ground cut ends its client scene at tick 190 regardless of queue completion; admission
currently rejects a second cast in the entire dimension. Terrain is a rectangular
constant-width strip. Z is still Sheath Strike, Y still accepts DRAWN. Rain clocks
contacts every tick but weapon only makes four sweeps. Existing animation architecture
already uses one synced clock and per-view exponential blending (Regulus/Iron Man);
retain it, author choreography on the existing grip bone.

## Implementation sequence
1. Server gates/loadout, detached diagonal geometry and budgeted carving, canyon profile.
2. Bounded scene progress/completion, heavy ground and separate diagonal choreography.
3. Actual Reid energy silhouette, spatial rain waves, draw pillar/audio layering.
4. Regression tests, build, CPU diagnostics, native attempt if available, separate PR.

Native visual quality, PvP/TPS, shader compatibility and sound listening must not be
reported as verified by JUnit or compilation.
