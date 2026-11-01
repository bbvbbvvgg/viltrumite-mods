# City aftermath — sound sources

Every recording below is published on Freesound under **Creative Commons Zero 1.0**
(<https://creativecommons.org/publicdomain/zero/1.0/>): no attribution is required, it is given anyway.
Only the public preview MP3 is used (cached in `~/.cache/viltrumite-sfx/work/raw/fs_<id>.mp3`, never committed);
`python3 tools/sfx/city_aftermath.py --fetch` downloads them again. The mix is fully seeded and rebuilt with
`python3 tools/sfx/city_aftermath.py --replace`. Licence checked on each landing page on 2026-11-01.

## Recordings

| key | title (landing page) | creator | Freesound id | licence |
|---|---|---|---|---|
| `far_explosions` | [distant explosions](https://freesound.org/people/Kostrava/sounds/320788/) | Kostrava | 320788 | CC0 1.0 |
| `metal_explosion` | [distant explosion](https://freesound.org/people/Kostrava/sounds/324277/) | Kostrava | 324277 | CC0 1.0 |
| `huge_explosion` | [Huge Distant Explosion](https://freesound.org/people/Fission9/sounds/476225/) | Fission9 | 476225 | CC0 1.0 |
| `nuclear_distant` | [Nuclear distant.wav](https://freesound.org/people/Svarvarn/sounds/322365/) | Svarvarn | 322365 | CC0 1.0 |
| `building_collapse` | [building collapse / demolition](https://freesound.org/people/greyfeather/sounds/712918/) | greyfeather | 712918 | CC0 1.0 |
| `house_collapse` | [Bakers house collapse](https://freesound.org/people/DasWaff/sounds/721600/) | DasWaff | 721600 | CC0 1.0 |
| `creak_slow` | [Creaking Metal - Slow](https://freesound.org/people/EagleStealthTeam/sounds/172411/) | EagleStealthTeam | 172411 | CC0 1.0 |
| `creak_metal` | [Creaking Metal.wav](https://freesound.org/people/Soapuel/sounds/489442/) | Soapuel | 489442 | CC0 1.0 |
| `creak_long` | [Long Metal Creak](https://freesound.org/people/LoafDV/sounds/238289/) | LoafDV | 238289 | CC0 1.0 |
| `rockfall` | [rockfall2a.wav](https://freesound.org/people/AlanCat/sounds/389303/) | AlanCat | 389303 | CC0 1.0 |
| `rocks` | [Rocks.wav](https://freesound.org/people/adamgryu/sounds/336023/) | adamgryu | 336023 | CC0 1.0 |
| `low_rumble` | [Low Rumble - loopable ambiance](https://freesound.org/people/lennartgreen/sounds/638914/) | lennartgreen | 638914 | CC0 1.0 |
| `ominous_rumble` | [Ominous Rumble - loopable (24bit flac)](https://freesound.org/people/steaq/sounds/593785/) | steaq | 593785 | CC0 1.0 |
| `howling_wind` | [howling_wind.wav](https://freesound.org/people/juryduty/sounds/185070/) | juryduty | 185070 | CC0 1.0 |
| `tower_wind` | [Howling wind through window in tower](https://freesound.org/people/tomhannen/sounds/486599/) | tomhannen | 486599 | CC0 1.0 |
| `sparks` | [Electric Sparks.wav](https://freesound.org/people/kev_durr/sounds/396470/) | kev_durr | 396470 | CC0 1.0 |
| `arcs` | [Electricity, Arcs, Sparks, long](https://freesound.org/people/julianmateo_/sounds/522690/) | julianmateo_ | 522690 | CC0 1.0 |
| `air_raid` | [multiple siren](https://freesound.org/people/Kostrava/sounds/324276/) | Kostrava | 324276 | CC0 1.0 |
| `stone_impacts` | [Foley_Impact_Stones_Strong_Debris_Stereo_DS.wav](https://freesound.org/people/Nox_Sound/sounds/554148/) | Nox_Sound | 554148 | CC0 1.0 |
| `stone_rubble` | [Stone Impact Rubble Debris 14](https://freesound.org/people/Sheyvan/sounds/569510/) | Sheyvan | 569510 | CC0 1.0 |
| `fire_crackles` | [fire crackles](https://freesound.org/people/florianreichelt/sounds/563766/) | florianreichelt | 563766 | CC0 1.0 |
| `wood_fire` | [Wood fire crackling, near flames](https://freesound.org/people/felix.blume/sounds/779877/) | felix.blume | 779877 | CC0 1.0 |

Rejected: city siren field recordings (TRP 568814, kyles 635741) — they may contain people talking in the
background and could not be auditioned, and the brief forbids voices; "Distant police siren" (705395) — AI
generated. The far sirens are the synthesized air-raid recording plus an original synthesized wail.

## Cue sheet

| packed file | built from |
|---|---|
| `aftermath/explosion_far_1` | `far_explosions` |
| `aftermath/explosion_far_2` | `huge_explosion` |
| `aftermath/explosion_far_3` | `metal_explosion` |
| `aftermath/explosion_far_4` | `nuclear_distant` |
| `aftermath/collapse_1` | `house_collapse`, `rockfall` |
| `aftermath/collapse_2` | `building_collapse`, `rockfall` |
| `aftermath/crumble_1` | `rocks` |
| `aftermath/crumble_2` | `rocks` |
| `aftermath/crumble_3` | `rocks` |
| `aftermath/rocks_1` | `rockfall` |
| `aftermath/rocks_2` | `rockfall` |
| `aftermath/rocks_3` | `rockfall` |
| `aftermath/debris_impact_1` | `stone_impacts` |
| `aftermath/debris_impact_2` | `stone_impacts` |
| `aftermath/debris_impact_3` | `stone_impacts` |
| `aftermath/debris_impact_4` | `stone_rubble` |
| `aftermath/metal_creak_1` | `creak_slow` |
| `aftermath/metal_creak_2` | `creak_long` |
| `aftermath/metal_creak_3` | `creak_metal` |
| `aftermath/sparks_1` | `sparks` |
| `aftermath/sparks_2` | `sparks` |
| `aftermath/sparks_3` | `arcs` |
| `aftermath/fire_crackle_1` | `wood_fire` |
| `aftermath/fire_crackle_2` | `fire_crackles` |
| `aftermath/fire_crackle_3` | `wood_fire` |
| `aftermath/fire_bed` | `wood_fire`, `fire_crackles` |
| `aftermath/rumble` | `ominous_rumble`, `low_rumble` |
| `aftermath/wind` | `tower_wind`, `howling_wind` |
| `aftermath/siren_far_1` | `air_raid` |
| `aftermath/siren_far_2` | `air_raid` |
| `aftermath/siren_far_3` | original synthesis |

Original synthesis (no recording) in every cue: sub thumps, sub swells, grit and chunk grains, mains buzz,
wire zaps, the low fire roar and the `siren_far_3` wail — all CC0 like the rest of `tools/sfx`.

## Limits

- Not auditioned by ear: checked by measured loudness (loudest 100 ms RMS per class), peak, spectral centroid,
  loop seam and spectrograms only. The mix needs a listening pass on real speakers.
- Loops are crossfaded cuts (equal-power, 0.8–1.2 s) — seamless by construction, but the texture repeats every
  6–8 s; the client layers two fire beds at different pitches and moves every bed with the player to hide it.
