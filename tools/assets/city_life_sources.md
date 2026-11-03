# Living Atheria — sound sources (issue #136)

Everything the streets of Atheria sound like: the crowd murmur and footsteps around the player, the far streets
by day and by night, wind between the buildings, panic and the stunned aftermath (beds); pigeons, bicycle bells,
laughter, church bells and dogs (details); screams, sobs and coughs of citizens; and the voices of the citizens
themselves (chats and calls). Built by `python3 tools/sfx/city_life.py` (`--fetch` downloads the recordings,
`--wav <dir>` writes WAV files to listen to, `--only beds|details|panic|voices|data` rebuilds one part). The
lines, dialogues, calls and their translations are in `tools/sfx/city_life_lines.py`; the generated
`city/life/CityLifeCatalog.java`, `src/main/sound-overlays/city_life*.json` and
`src/main/lang-overlays/city_life/*.json` must not be edited by hand.

## Recordings

Every recording below is published on Freesound under **Creative Commons Zero 1.0**
(<https://creativecommons.org/publicdomain/zero/1.0/>): no attribution is required, it is given anyway. Only the
public preview MP3 is used (cached in `~/.cache/viltrumite-sfx/work/raw/fs_<id>.mp3`, never committed); the licence
of each one is checked by `tools/sfx/fslib.py` when it is fetched (2026-11-02).

| key | title (landing page) | creator | Freesound id | licence | used in |
|---|---|---|---|---|---|
| `shop_street` | [Shopping Street Ambience](https://freesound.org/s/451734/) | florianreichelt | 451734 | CC0 1.0 | bed crowd |
| `walla` | [Small Crowd Walla](https://freesound.org/s/653920/) | IENBA | 653920 | CC0 1.0 | beds crowd, crowd_far |
| `old_town` | [People Ambience In The Old Town](https://freesound.org/s/725605/) | WhiteNoiseSleeper | 725605 | CC0 1.0 | beds crowd_far, hush |
| `coffeehouse` | [0105coffeehouse.wav](https://freesound.org/s/46310/) | moxobna | 46310 | CC0 1.0 | bed crowd |
| `steps_wet` | [Two people's footsteps walking on a wet cement surface](https://freesound.org/s/334457/) | evsecrets | 334457 | CC0 1.0 | bed steps |
| `steps_stone` | [2 persons walking on stonefloor.mp3](https://freesound.org/s/220335/) | Reikun | 220335 | CC0 1.0 | bed steps |
| `steps_concrete` | [Concrete Footsteps](https://freesound.org/s/465299/) | SoftDistortionFX | 465299 | CC0 1.0 | bed running |
| `stairs_run` | [Crowd on Stairs.wav](https://freesound.org/s/361172/) | quattrostagii | 361172 | CC0 1.0 | bed running |
| `berlin_morning` | [Early Summer Morning Ambience with Birds, Berlin](https://freesound.org/s/457616/) | Pfannkuchn | 457616 | CC0 1.0 | bed street_day |
| `park_morning` | [Ambience - European city park - morning - birds](https://freesound.org/s/563547/) | ValentinPetiteau | 563547 | CC0 1.0 | bed street_day |
| `night_corner` | [street corner side street quiet night distant traffic](https://freesound.org/s/413949/) | kyles | 413949 | CC0 1.0 | beds street_night, hush |
| `crickets` | [Cricket chirping](https://freesound.org/s/530477/) | GB01 | 530477 | CC0 1.0 | bed street_night |
| `gentle_wind` | [Looping Gentle Wind Ambience on an Open Desert Plain](https://freesound.org/s/697217/) | dhallcomposer | 697217 | CC0 1.0 | beds wind, hush |
| `window_wind` | [Winter wind whistling through window.mp3](https://freesound.org/s/69512/) | audible-edge | 69512 | CC0 1.0 | bed wind |
| `crowd_panic` | [CrowdPanic.mp3](https://freesound.org/s/435716/) | belthazarus | 435716 | CC0 1.0 | bed panic |
| `crowd_panic_2` | [Crowd in panic](https://freesound.org/s/491405/) | IENBA | 491405 | CC0 1.0 | bed panic |
| `dog_far` | [distant_dog.wav](https://freesound.org/s/54545/) | Heigh-hoo | 54545 | CC0 1.0 | dog |
| `dog_big_far` | [Angry big dog barking - Far [d15].wav](https://freesound.org/s/440865/) | v23 | 440865 | CC0 1.0 | dog |
| `bike_bell` | [Bicycle_Bell.wav](https://freesound.org/s/330956/) | nikiforov5000 | 330956 | CC0 1.0 | bike_bell |
| `bike_bell_2` | [Pashley 'Ding Dong' type Bike bell.WAV](https://freesound.org/s/400695/) | dm103 | 400695 | CC0 1.0 | bike_bell |
| `pigeons` | [Pigeons flying](https://freesound.org/s/532180/) | mcmikai | 532180 | CC0 1.0 | pigeons |
| `pigeons_2` | [pigeons_fly away_ wing flaps_CsG](https://freesound.org/s/528250/) | csaszi | 528250 | CC0 1.0 | pigeons |
| `pigeons_3` | [121003 Pigeon flock fly away, wing flaps, Toronto](https://freesound.org/s/616623/) | TRP | 616623 | CC0 1.0 | pigeons |
| `laugh_small` | [Small Crowd Laughing](https://freesound.org/s/138112/) | snakebarney | 138112 | CC0 1.0 | laugh |
| `laugh_crowd` | [crowd_laugh.wav](https://freesound.org/s/76693/) | moxobna | 76693 | CC0 1.0 | laugh |
| `bells_town` | [Bells_distant_in_town.wav](https://freesound.org/s/266525/) | ivolipa | 266525 | CC0 1.0 | bells |
| `f_fear` | [woman screaming of fear .wav](https://freesound.org/s/319258/) | The_Sound_Side | 319258 | CC0 1.0 | scream.female |
| `f_scream_1` | [Female scream 01](https://freesound.org/s/169810/) | missozzy | 169810 | CC0 1.0 | scream.female |
| `f_scream_2` | [Female scream 02](https://freesound.org/s/169811/) | missozzy | 169811 | CC0 1.0 | scream.female |
| `f_scream_x2` | [Scream, female x2](https://freesound.org/s/208893/) | peridactyloptrix | 208893 | CC0 1.0 | scream.female |
| `m_loud` | [Male voice screaming loudly](https://freesound.org/s/169628/) | Dinsfire | 169628 | CC0 1.0 | scream.male |
| `m_scream` | [Man screaming.wav](https://freesound.org/s/325548/) | Archeos | 325548 | CC0 1.0 | scream.male |
| `m_wounded` | [Wounded man scream](https://freesound.org/s/221544/) | joseppujol | 221544 | CC0 1.0 | scream.male |
| `cough_1` | [Cough.wav](https://freesound.org/s/208761/) | Harris85 | 208761 | CC0 1.0 | cough |
| `cough_2` | [coughing](https://freesound.org/s/184871/) | Eelke | 184871 | CC0 1.0 | cough |
| `cough_3` | [coughing](https://freesound.org/s/407839/) | BOBtheROSS | 407839 | CC0 1.0 | cough |
| `cough_4` | [cough1.mp3](https://freesound.org/s/142604/) | loisgwenllian | 142604 | CC0 1.0 | cough |
| `cough_5` | [Coughing Young Man (me).wav](https://freesound.org/s/51136/) | RutgerMuller | 51136 | CC0 1.0 | cough |
| `sob_f` | [Woman Crying.wav](https://freesound.org/s/449439/) | purplereptar | 449439 | CC0 1.0 | sob.female |
| `sob_f_2` | [Girl Crying](https://freesound.org/s/218184/) | mvVoiceActing | 218184 | CC0 1.0 | sob.female |
| `sob_m` | [Crying Man](https://freesound.org/s/263776/) | scottemoil | 263776 | CC0 1.0 | sob.male |

## Voices

The 73 lines (25 two- and three-turn dialogues for the calm, wary and aftermath city, and short calls for
startle, flight, help and survivors) are spoken in Russian by **Piper** text-to-speech
(<https://github.com/OHF-Voice/piper1-gpl>) with the voices `ru_RU-denis-medium` and `ru_RU-dmitri-medium`
(rhasspy/piper-voices, trained on the CC0 datasets of OHF-Voice/voice-datasets). The two female voices are the same
takes re-pitched with the WORLD vocoder (pyworld): F0 up and the spectral envelope stretched so the formants
follow. Calls are spoken faster, pitched up and driven harder. The takes are own work of this project
(`tools/sfx/city_life_voice.py`), shared under CC0 1.0 like the rest of the mixes; every take was checked by speech
recognition (vosk small-ru) against its text: mean word match 0.76, 257 of 292 takes at 0.5 or better; the misses
are mostly short shouted calls (one or two words, pitched up and driven). Subtitles carry the words in all 12 locales.

## Playback (summary)

- Server (`city/life/CityLife`, `CityMood`, `CityConversation`): a mood map of the streets in 32-block cells
  (alarm from danger, blasts and collapses with a 45 s half-life, trauma with 4 min) gives calm → wary → panic →
  aftermath and the slow return; chats pick a dialogue for mood, time of day and weather, never one heard nearby in
  the last 5 min, at most 2 voiced chats within 20 blocks and 16 in the city; calls and screams are limited per
  16-block street cell (12 ticks apart, 3 per 2 s) and per citizen. Lines are played at the speaker.
- Client (`client/city/life/CityLifeVoices`, `CityLifeAmbience`, `CityLifeMixer`): voices get distance, direction
  and occlusion (open / thin wall 45 % / building 25 %), at most 3 chats, 3 calls, 3 screams and 2 quiet sounds at
  once with the nearest first (the farthest fades out), no line twice within 24 blocks and 1.5 s; the speaker
  gestures while his line plays and the other one nods. Beds follow the citizens actually around (density, calm /
  walking / running / panicking), time of day, rain, indoors, height above the street and nearby ruins; a placed bed
  plays as two layers, a mono source toward the crowd it stands for and a centred wide copy, so it leans to a side
  (at most 60 %) but is never hard-panned to one ear.
- QA: `/city life` (mood here, talks, calls, limits), `/city life reset`; client playtest command `lifestate`.

Not auditioned by ear: checked by numbers (loudness, loop seams), spectrograms, speech recognition, in-game
logs and recordings of the game's own output (OpenAL wave writer) only — the final mix deserves a listening pass.
