# Structural collapse sound sources

All twelve collapse sound events (`collapse.*`, 26 files under
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/collapse/`) are mixed by
`tools/sfx/city_collapse.py` from the CC0 1.0 Freesound recordings below plus seeded noise grains (own work,
CC0-1.0). No vanilla Minecraft sound is used. Every license was checked on the Freesound page of the sound
(the page links https://creativecommons.org/publicdomain/zero/1.0/); the HQ preview MP3s are fetched by
`python3 tools/sfx/city_collapse.py --fetch` and never committed.

| Key | Freesound | Creator | Original title | Used in |
|---|---|---|---|---|
| `collapse` | [712918](https://freesound.org/people/greyfeather/sounds/712918/) | greyfeather | building collapse / demolition | `collapse.roar`, `collapse.distant` |
| `concrete` | [843339](https://freesound.org/people/loganzsound/sounds/843339/) | loganzsound | Concrete Breaks Several Denoised | `collapse.crumble_burst`, `collapse.crack` |
| `smash` | [522099](https://freesound.org/people/magnuswaker/sounds/522099/) | magnuswaker | Concrete SMASH 2 | `collapse.impact` |
| `heavy` | [397596](https://freesound.org/people/Nightflame/sounds/397596/) | Nightflame | Heavy Impacts | `collapse.impact`, `collapse.crack`, `collapse.ground_impact` |
| `boom` | [539968](https://freesound.org/people/Za-Games/sounds/539968/) | Za-Games | Deep Boom | `collapse.impact`, `collapse.ground_impact`, `collapse.distant` |
| `groan` | [496836](https://freesound.org/people/hinchinbrook/sounds/496836/) | hinchinbrook | Metallic Groan | `collapse.metal_groan` |
| `bend` | [556714](https://freesound.org/people/NachtmahrTV/sounds/556714/) | NachtmahrTV | Bending Metal | `collapse.metal_groan` |
| `creak` | [172411](https://freesound.org/people/EagleStealthTeam/sounds/172411/) | EagleStealthTeam | Creaking Metal - Slow | `collapse.metal_groan` |
| `metal_hit` | [697748](https://freesound.org/people/SoundDesignForYou/sounds/697748/) | SoundDesignForYou | Metal Impact Creak Resonant | `collapse.metal_groan` |
| `rockfall` | [389303](https://freesound.org/people/AlanCat/sounds/389303/) | AlanCat | rockfall2a.wav | `collapse.debris_scatter` |
| `rockslide` | [608957](https://freesound.org/people/NinjaSharkStudios/sounds/608957/) | NinjaSharkStudios | Rockslide_1.wav | `collapse.roar`, `collapse.tail` |
| `debris` | [703247](https://freesound.org/people/xkeril/sounds/703247/) | xkeril | Big falling debris (crash) | `collapse.ground_impact` |
| `stones` | [554148](https://freesound.org/people/Nox_Sound/sounds/554148/) | Nox_Sound | Foley_Impact_Stones_Strong_Debris | `collapse.facade_shed`, `collapse.debris_scatter` |
| `subsonic` | [453405](https://freesound.org/people/kyles/sounds/453405/) | kyles | bass rumble deep subterranean subsonic | `collapse.rumble` |
| `earth` | [217657](https://freesound.org/people/Reitanna/sounds/217657/) | Reitanna | earth rumble.wav | `collapse.rumble`, `collapse.distant` |
| `quake` | [199803](https://freesound.org/people/TheMinkman/sounds/199803/) | TheMinkman | Quake Short.wav | `collapse.rumble` |
| `deep` | [184313](https://freesound.org/people/swiftoid/sounds/184313/) | swiftoid | Cinematic Deep Rumble | `collapse.roar`, `collapse.rumble`, `collapse.distant` |
| `rubble` | [569510](https://freesound.org/people/Sheyvan/sounds/569510/) | Sheyvan | Stone Impact Rubble Debris 14 | `collapse.crumble_burst`, `collapse.facade_shed` |
| `clatter` | [461696](https://freesound.org/people/leonelmail/sounds/461696/) | leonelmail | Aggressive Clatter 01 | `collapse.facade_shed` |
| `gravel` | [34175](https://freesound.org/people/Dalibor/sounds/34175/) | Dalibor | pour out gravel on the stairs.wav | `collapse.tail`, `collapse.dust` |
| `splinter` | [869103](https://freesound.org/people/ozymandias37/sounds/869103/) | ozymandias37 | Tall dead tree splintering then falling | `collapse.crack` |

License for all: CC0 1.0 Universal, https://creativecommons.org/publicdomain/zero/1.0/. Attribution is not
required; it is given here and in `CREDITS.md` anyway.

## Rejected candidates

| Freesound | Why |
|---|---|
| 845932 (craigsmith, "Building Collapse") | marked CC0, but the description says the effects come from Hanna-Barbera / MGM 35 mm loops: provenance unclear |
| 177556 | a derivative of sounds whose licenses could not be confirmed |
| 222521 | derived from 82722, license of the parent unknown |
| 147584 | "inspired by" an unlicensed source |
| 326304 | built from parent sounds with mixed licenses |
| 721600 | a composite whose layers are not documented |

## Cue sheet

| Event | Files | Phase | Layers |
|---|---|---|---|
| `collapse.impact` | `impact_1..2` | 1, the hit | concrete smash, heavy thud, short deep boom |
| `collapse.crumble_burst` | `crumble_burst_1..2` | 1 | concrete breaking, rubble, grains |
| `collapse.metal_groan` | `metal_groan_1..2` | 2, failure | metallic groan, bending / creaking metal, resonant metal hit |
| `collapse.crack` | `crack_1..3` | 2 | concrete break, timber splinter, low thud |
| `collapse.facade_shed` | `facade_shed_1..3` | 2 (and pieces landing) | clatter, stone debris, rubble, grains |
| `collapse.roar` | `roar_1..2` | 3, the fall | building collapse, rockslide, cinematic rumble, grains |
| `collapse.ground_impact` | `ground_impact_1..2` | slam | big falling debris crash, heavy impact, deep boom |
| `collapse.debris_scatter` | `debris_scatter_1..2` | slam | rockfall, stone debris, grains |
| `collapse.rumble` | `rumble_1..2` | 1 and slam | subsonic bass rumble, earth rumble, quake, cinematic rumble |
| `collapse.tail` | `tail_1..2` | 4, settling | rockslide tail, gravel pour, grains |
| `collapse.dust` | `dust_1..2` | 4 | gravel pour, filtered noise hiss |
| `collapse.distant` | `distant_1..2` | far listeners | deep boom, low-passed collapse, rumble |

Levels are measured (loudest 100 ms RMS per class, soft limit under -1 dBFS), not auditioned.
