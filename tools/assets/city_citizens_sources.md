# Citizen peril voice sources (issue #138)

All nine citizen voice events (`citizen.*`, 27 files under
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/citizen/`) are trimmed, shaped and mixed by
`tools/sfx/city_citizens.py` from the CC0 1.0 Freesound recordings below. No vanilla Minecraft sound is used.
Every license was checked on the Freesound page of the sound (the page links
https://creativecommons.org/publicdomain/zero/1.0/); two of them (`woman_scream`, `woman_scream_2`) were found
through Openverse (license `cc0`) and are the same Freesound uploads. The HQ preview
MP3s are fetched by `python3 tools/sfx/city_citizens.py --fetch` and never committed.

| Key | Freesound | Creator | Original title | Used in |
|---|---|---|---|---|
| `fear_m` | [391362](https://freesound.org/people/guamorims/sounds/391362/) | guamorims | Cry of fear - Man.wav | `citizen.scream.male` |
| `fear_f` | [391363](https://freesound.org/people/guamorims/sounds/391363/) | guamorims | Cry of fear - Female.wav | `citizen.scream.female` |
| `fear_crowd` | [391364](https://freesound.org/people/guamorims/sounds/391364/) | guamorims | Cry of fear - Collective.wav |  |
| `help_m` | [391365](https://freesound.org/people/guamorims/sounds/391365/) | guamorims | Cry for help - Man.wav | `citizen.help.male` |
| `help_f` | [391361](https://freesound.org/people/guamorims/sounds/391361/) | guamorims | Cry for help - Female.wav | `citizen.help.female` |
| `help_crowd` | [391366](https://freesound.org/people/guamorims/sounds/391366/) | guamorims | Cry for help- Collective.wav |  |
| `no_m` | [391357](https://freesound.org/people/guamorims/sounds/391357/) | guamorims | Scream NO - Man.wav | `citizen.witness.male` |
| `no_f` | [391358](https://freesound.org/people/guamorims/sounds/391358/) | guamorims | Scream NO - Female.wav | `citizen.witness.female` |
| `woman_scream` | [222545](https://freesound.org/people/queen_westeros/sounds/222545/) | queen_westeros | WOMAN SCREAM | `citizen.scream.female` |
| `woman_scream_2` | [673306](https://freesound.org/people/courtneyeck/sounds/673306/) | courtneyeck | Woman Scream | `citizen.scream.female` |
| `forest_m` | [873718](https://freesound.org/people/VanEngelen/sounds/873718/) | VanEngelen | VOXScrm_Loud Scream Male Medium Distance In Forest LCR | `citizen.scream.male`, `citizen.fall.male` |
| `fall_m` | [345455](https://freesound.org/people/Artmasterrich/sounds/345455/) | Artmasterrich | Male_Fall_Death_01 | `citizen.fall.male` |
| `fall_f` | [713549](https://freesound.org/people/IENBA/sounds/713549/) | IENBA | Female Falling Screams | `citizen.fall.female` |
| `helphelp` | [87531](https://freesound.org/people/Whiprealgood/sounds/87531/) | Whiprealgood | Help! Help!.wav | `citizen.help.male` |
| `asking` | [814235](https://freesound.org/people/TyrantTim/sounds/814235/) | TyrantTim | Asking For help | `citizen.help.male` |
| `get_help` | [315843](https://freesound.org/people/melnjay87/sounds/315843/) | melnjay87 | male saying someone get help.wav | `citizen.help.male` |
| `help_me_f` | [422689](https://freesound.org/people/niamhd00145229/sounds/422689/) | niamhd00145229 | help me!.wav | `citizen.help.female` |
| `help_me_2` | [529115](https://freesound.org/people/Feed_/sounds/529115/) | Feed_ | help me | `citizen.help.female` |
| `help_us` | [800903](https://freesound.org/people/wordwiz1/sounds/800903/) | wordwiz1 | Help us | `citizen.help.female` |
| `omg_m` | [692784](https://freesound.org/people/Elijahbaby/sounds/692784/) | Elijahbaby | masculine "oh, my god" | `citizen.witness.male` |
| `omg_m2` | [387941](https://freesound.org/people/Sami_Kullström/sounds/387941/) | Sami_Kullström | Oh my god.wav | `citizen.witness.male` |
| `oh_no_f` | [639860](https://freesound.org/people/owstu/sounds/639860/) | owstu | Female Voc txt _Oh No.wav | `citizen.witness.female` |
| `gasp_f` | [346213](https://freesound.org/people/FloydP/sounds/346213/) | FloydP | Women gasping.wav | `citizen.witness.female` |
| `crowd_panic` | [491405](https://freesound.org/people/IENBA/sounds/491405/) | IENBA | Crowd in panic |  |
| `crowd_scream` | [459101](https://freesound.org/people/DigestContent/sounds/459101/) | DigestContent | Crowd Screaming |  |

## Cue sheet

| Event | Files | Subtitle (en / ru) | Notes |
|---|---|---|---|
| `citizen.scream.male` / `.female` | `scream_male_1..3`, `scream_female_1..3` | Citizen screams / Житель кричит | The floor gives way under the feet (teeter), a short fall |
| `citizen.fall.male` / `.female` | `fall_male_1..2`, `fall_female_1..2` | Falling citizen screams / Падающий житель кричит | Pitch glides down 14-20 %, the top closes 11 kHz -> ~4 kHz, -5 dB over the cue; played on the entity, so it falls with the body |
| `citizen.help.male` / `.female` | `help_male_1..4`, `help_female_1..4` | Citizen calls for help / Житель зовёт на помощь | Hanging from a ledge, the floors of a failing building; English calls plus one Portuguese "socorro" take each (guamorims) |
| `citizen.witness.male` / `.female` | `witness_male_1..3`, `witness_female_1..3` | Witness cries out / Вскрик свидетеля | "No!", "oh my god", "oh no", a gasp |
| `citizen.crowd_panic` | `crowd_panic_1..3` | Crowd panics / Паника толпы | Crowd beds plus two or three single cries low-passed as farther down the street; played once at the crowd centre |

Levels: loudest 100 ms RMS at -10 dBFS (scream, fall), -11 (help), -12 (witness), -13 (crowd), soft-limited
under -1 dBFS; 110 Hz high-pass. Mono Vorbis, 44.1 kHz q4 (crowd 32 kHz q2 to stay under the push limit).

Not auditioned by ear (no audio device in the build sandbox): segments were picked by measured energy, pitch
(male/female by F0) and spectrograms only - the final mix needs a listening pass.
