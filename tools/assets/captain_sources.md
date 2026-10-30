# Captain America 2012 — sources of the assets

Everything below is **own work, CC0**: no film audio, no recorded samples, no ripped models or textures.

## Sounds (`tools/sfx/captain.py`)
52 events (`captain.*` 25, `avengers.*` 27), procedural numpy synthesis on the primitives of `tools/sfx/jinwoo.py`
(noise bands, swept filters, inharmonic partials, formant buzz, convolution reverb from filtered noise).
Rebuild: `python3 tools/sfx/captain.py --package` (deterministic seeds; 44.1 kHz mono Vorbis q5).

- Vibranium shield: `ring()` — inharmonic partials 1 : 2.32 : 4.25 : 6.63 : 9.38 with a 0.4 % detuned twin (slow beat),
  so blocks, ricochets and parries *sing* instead of clanking; `clang()` = click + body thud + ring.
- `captain.assemble` — a synthetic horn call on a rising G-major triad with a drum hit. **No voice**: the line
  "Avengers, assemble!" is a text subtitle only.
- `avengers.hulk_roar` — a non-verbal formant growl (no words).
- Quinjet / Iron Man thrusters — brown-noise jet body + swept band + turbine whine; Thor — noise crack + modulated rumble;
  Widow's Bites / shocks — random high-passed micro-bursts over a buzzing saw.

## Textures and icons
- `tools/assets/captain_icons.py` — 5 ability icons (64×64), race badge (32×32), panel emblem (16×16).
- Models/textures of Captain, the shield and the Avengers: `tools/assets/captain_model.py`, `tools/assets/avengers_model.py`.
- Animations: `tools/assets/captain_clips.py`, `captain_fp_clips.py`, `avengers_clips.py`.
