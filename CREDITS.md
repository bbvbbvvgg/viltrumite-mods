# Credits

Third-party and reused assets in this repository. Every committed file is listed with its source in a per-hero sources file (`tools/assets/<hero>_sources.md`).

## Iron Man

Sources list: `tools/assets/ironman_sources.md`. Permission texts: `docs/licenses/ironman/` (to be added — the owner holds the authors' messages).

### Own work
- Tony Stark skin `textures/entity/hero/ironman.png` (from Stage 1b; the 1a file from Codex-Superheroes was a suit skin and is replaced), the suit ability icon and the two thruster loops (`tools/sfx/ironman_stage1.sh`, the only synthesized sounds left).

- Stage 3: scan / countermeasures / helmet icons and the helmet HUD frame (`tools/assets/make_ironman_stage3_assets.py`).

- Stage 4: suit interior skin, Veronica pod model and texture, Veronica and signature icons, Mark 15 camouflage shimmer (`tools/assets/make_ironman_stage4_assets.py`, `make_ironman_stage4_visuals.py`, `make_ironman_stage4_signatures.py`); the opening-shell interior atlas; the Veronica model after the Age of Ultron stills.

### Kenney — Sci-Fi Sounds 1.0 and Impact Sounds 1.0
- Source: kenney.nl. License: CC0 1.0 (public domain; credit not required, given here).
- Used: every Iron Man sound except the thruster loops — built by `tools/sfx/ironman_cc0.py` (layered, pitched, loudness-normalized).

The Codex-Superheroes JARVIS voice lines are no longer used (muted with the other beeping sounds, user decision 2026-10-10).

### Satsu — Iron Man Addon 3.6.1
- Author: Satsu.
- Used (Stage 1b): Mark 50 suit skin and glow map, Mark 50 head mask geometry, feet / palm / stabilizer flame geometry, `new_thruster_white` flame textures.
- Used (Stage 2 models): nano blade (`nanokatar`), nano hammer (`nano_mallet`), nano shield faces (on a smaller forearm plate), forearm rocket launcher (+ first person), nano stabilizer, the force-field hex texture — `tools/assets/convert_ironman_stage2_parts.py`.
- Used (Stage 4 models): Mark 7, 15, 17, 39, War Machine Mk2, Iron Heart Mk3 skins (suit + `all_helmet` mask baked by `tools/bake_suit_skin.py`), `each_part` suit pieces, Mark 7 flaps, Mark 17 heartbreaker chest, War Machine shoulder pads and `torret_mark_2` with its clip, Iron Heart Mk3 plates, Mark 39 jetpack and flames, empty suit shell (`full_body` + helmet, cut into an opening shell) — `tools/assets/convert_ironman_stage4_marks.py`.
- Permission: received by the repository owner, 2026-10-09 (spec §3.1). Text: pending in `docs/licenses/ironman/`.

### Sind — Iron Man pack 2.2.0
- Author: Sind.
- Used: shoulder rocket pods (`cannons`, `rockets`, `cannons.fsk`), the arm rocket and missile body (`armrocket`); Mark 42 suit skin, the 14 `mk42` pieces with their thrusters, glow map baked from the pieces' lights; Mark 7 micro-laser (`mk6/laser`, `mark7_laser`); Hulkbuster Mark 48 body, left arm, jackhammer arm (texture from the archive's prepared `jackhammer_arm_mark48`), fire models, first-person arms, `repulsor_layer` flame frames, and animations baked from the `hulkbuster/*.fsk` scripts — `tools/tabula2geo.py`, `tools/fsk2anim.py`, `tools/assets/convert_ironman_sind.py`. HUD reference only (redrawn).
- Permission: received by the repository owner, 2026-10-09 (spec §3.1). Text: pending in `docs/licenses/ironman/`.

The raw archive `IronMan_Hulkbuster_models.zip` is not committed. Only converted files that the mod uses are committed.


## Roundabout — The Hand erasure shader

- Authors: Hydraheads and the Roundabout contributors (Roundabout, the JoJo mod, https://github.com/Hydraheads/RoundaboutMod). Source: archive `TheHand-source-da2326bd5.zip` from the repository owner (SHA-256 `a600d0e2c429a625cfb4082f8df05b928a94e37a977421d9f6fb9f94e8ad72c3`).
- License: Roundabout License v1.2 — modification and redistribution allowed with credit; ownership is not claimed. Text: `docs/licenses/roundabout/LICENSE.txt`.
- Used: `thehand_erasure.vsh/.fsh/.json` (GLSL as authored; only addition is the `Scale` uniform) and ports of `TheHandErasureRenderer` (draw passes, ring mesh, timeline) and `TheHandErasureStyle` (colours) in `client/render/erasure/`. Not used: stand, palm source, stopped time, sandbox clock, timing readout, mixin callbacks.

## Reinhard van Astrea

Standalone core JARs carry this maintained document at `META-INF/credits/reinhard-credits.md` and the provenance table at `META-INF/credits/reinhard-sources.md`. The mod metadata credits points to these audio-license exceptions. The code license does not override the individual CC-BY/CC0 audio licenses.

Sound sources and exact input/output provenance: `tools/assets/reinhard_sources.md`. Checked 2026-10-13. Only processed excerpts are distributed, never the raw archive. Changes: **trimmed / layered / normalized**, mono 44.1 kHz Vorbis. No synthesis.

### CC-BY 4.0 — Freesound
- **klankbeeld** — [St. John Cathedral female choir 001.wav (398218)](https://freesound.org/people/klankbeeld/sounds/398218/), [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/). Mode chords and Reid choir: trimmed / layered / normalized. Credit: klankbeeld and **Freesound.org**; only creative transformed excerpts, no raw-stock redistribution.
- **hykenfreak** — [Deepest Boom Yet (248150)](https://freesound.org/people/hykenfreak/sounds/248150/) and [Spooky Sucking Air (331620)](https://freesound.org/people/hykenfreak/sounds/331620/), [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/). World Cut tail, its derived hum/pulse loop / erasure inhale: trimmed / layered / normalized.
- **dingus23** — [earthquake.aif (275809)](https://freesound.org/people/dingus23/sounds/275809/), [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/). Stomp: trimmed / layered / normalized.

### CC0 1.0 — Freesound
Vrymaa (770035), CHallSmith (870684), reathance (503809), Greg_Surr (554564), daandraait (249716), qubodup (182050), Iridiuss (519413). Individual source URLs and [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) license links are in the source table. Changes: trimmed / layered / normalized. Alliare (825864) is approved-license input but **not distributed** while the blast mix is blocked.

### Excluded (not distributed)
CGEffex 93835 and Robinhood76 378199 are **CC-BY-NC 4.0** on the live pages. No excerpts/layers included. Their events retain vanilla placeholders pending owner approval of a CC0/CC-BY alternative.

### Reinhard cinematic synthesis

`reid_cinematic_layer`, `cut_rumble`, `cut_cinematic_impact` and `cut_cinematic_sweep` are original deterministic procedural audio generated by `tools/sfx/reinhard_cinematic.py` (CC0-1.0). No external samples. These layers do not replace World Erasure recordings. Source PCM peak: -6.02 dBFS; final mixed perceived loudness still needs an in-game listening pass.

### REINHARD OVERHAUL strikes — CC0 1.0, Freesound (found via Openverse)

`fist_swing_1..3`, `fist_hit_1..3`, `reid_swing_1..3`, `reid_hit_1..3`, `reid_heavy_1..2` and `reid_drawn_hum` are mixed by `tools/sfx/reinhard_strikes.py` from these [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) recordings (attribution is not required; credited with thanks): mateusboga — [Woosh 1 (614084)](https://freesound.org/people/mateusboga/sounds/614084/); qubodup — [Fast Sword Swing Sound (60004)](https://freesound.org/people/qubodup/sounds/60004/), [swosh swinging sword 37 (60024)](https://freesound.org/people/qubodup/sounds/60024/); Danjocross — [Clean fast Swoosh (507466)](https://freesound.org/people/Danjocross/sounds/507466/); bolkmar — [FX - Swoosh - Light (475132)](https://freesound.org/people/bolkmar/sounds/475132/); Rodzuz — [Body punches (393904)](https://freesound.org/people/Rodzuz/sounds/393904/); Kievinay — [Bass Impact - Ki_01 (512389)](https://freesound.org/people/Kievinay/sounds/512389/); SypherZent — [Deep Cut / Slash / Gash (420674)](https://freesound.org/people/SypherZent/sounds/420674/), itself a modified version of Abyssmal's CC0 [slashkut (35213)](https://freesound.org/people/Abyssmal/sounds/35213/); CogFireStudios — [Hit Swing Sword (547038)](https://freesound.org/people/CogFireStudios/sounds/547038/), [Hit Impact Sword 3 (547042)](https://freesound.org/people/CogFireStudios/sounds/547042/); Eponn — [Hit Impact Sword (547035)](https://freesound.org/people/Eponn/sounds/547035/), [Hit Impact Sword 2 (547036)](https://freesound.org/people/Eponn/sounds/547036/); kibibu — [sword_2.wav (22426)](https://freesound.org/people/kibibu/sounds/22426/). Changes: trimmed / layered / pitched / filtered / level-matched; the drawn hum is a spectral freeze of the kibibu ring. Per-source table and processing: `tools/assets/reinhard_sources.md`. Not auditioned; final mix loudness still needs an in-game listening pass.

### TEMPORARY test asset — license NOT verified

`sword_strike_voice` (the BLAST of the normal World Cut, REINHARD OVERHAUL item 12) is the owner-confirmed sound of Reinhard's strike on Theresia from Re:Zero Season 3 Episode 14 (reference: https://www.youtube.com/watch?v=GtFqJpCrwCQ), copied unmodified from the owner's Codex-Superheroes repository for testing. Its rights are **not verified** and no license is claimed; it must be replaced with a licensed or original sound before a public release. Details and digest: `tools/assets/reinhard_sources.md`.
