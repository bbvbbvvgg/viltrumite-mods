# Captain America 2012 — sources of the assets

Everything below is **own work, CC0**: no film audio, no ripped models or textures. The only recorded samples are the
four Hulk sounds (CC0 1.0 Freesound, see "Hulk recordings").

## Sounds (`tools/sfx/captain.py`)
52 events (`captain.*` 25, `avengers.*` 27), procedural numpy synthesis on the primitives of `tools/sfx/jinwoo.py`
(noise bands, swept filters, inharmonic partials, formant buzz, convolution reverb from filtered noise).
Rebuild: `python3 tools/sfx/captain.py --package` (deterministic seeds; 44.1 kHz mono Vorbis q5).

- Vibranium shield: `ring()` — inharmonic partials 1 : 2.32 : 4.25 : 6.63 : 9.38 with a 0.4 % detuned twin (slow beat),
  so blocks, ricochets and parries *sing* instead of clanking; `clang()` = click + body thud + ring.
- `captain.assemble` — a synthetic horn call on a rising G-major triad with a drum hit. **No voice**: the line
  "Avengers, assemble!" is a text subtitle only.
- `avengers.hulk_roar`, `hulk_punch`, `hulk_smash`, `hulk_chest` — replaced by recordings, see below (the synthesis in
  `captain.py` stays as a fallback recipe, not packaged).

## Hulk recordings (`tools/sfx/hulk_cc0.py`)
CC0 1.0 Freesound previews found and downloaded via Openverse (`openverse_sfx.py download <uuid>`), licence checked on Openverse:

| Source | Openverse UUID | Used in |
|---|---|---|
| zerokingfull — Monster Roar 02 (freesound 347410) | f52ac5d2-3bb8-4b27-99e9-5493698f0c76 | hulk_roar (main voice, ×0.8) |
| NicknameLarry — Monster Roar (freesound 487513) | 6018bfd1-81b5-4872-ba3f-eb11eb09479c | hulk_roar (second voice, ×0.68) |
| NicknameLarry — Scary Monster Roar #2 (freesound 489901) | e6152a5c-3cec-45eb-9347-24ac7235df16 | hulk_roar (low bed, ×0.62) |
| insanity54 — body_hit.wav (freesound 276600) | 2651c4ac-55bc-480b-86a1-c31dd3004f92 | hulk_punch, hulk_smash, hulk_chest |
| 200221-WeanBekker — Heavy sand impacts.wav (freesound 543699) | e2b9cb0f-3e86-424d-a781-aead8f3e61ad | hulk_punch, hulk_smash (debris) |

Rebuild: download the five UUIDs, then `python3 tools/sfx/hulk_cc0.py`. Checked by numbers and spectrograms, not by ear.
- Quinjet / Iron Man thrusters — brown-noise jet body + swept band + turbine whine; Thor — noise crack + modulated rumble;
  Widow's Bites / shocks — random high-passed micro-bursts over a buzzing saw.

## Textures and icons
- `tools/assets/captain_icons.py` — 5 ability icons (64×64), race badge (32×32), panel emblem (16×16).
- Models/textures of Captain, the shield and the Avengers: `tools/assets/captain_model.py`, `tools/assets/avengers_model.py`.
- Animations: `tools/assets/captain_clips.py`, `captain_fp_clips.py`, `avengers_clips.py`.
