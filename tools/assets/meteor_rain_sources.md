# Meteor Rain 2.0 asset sources

Textures are **own work** (CC0-1.0). Sounds are CC0 1.0 Freesound recordings (found through Openverse) layered
with original synthesis (CC0-1.0). Raw downloads, previews and intermediate mixes are never committed.

## Textures

| Committed files | Generator | Notes |
|---|---|---|
| `textures/particle/meteor_smoke_0..7.png.b64` | `tools/assets/make_meteor_textures.py` | soft, irregular smoke puffs (seeded fBm), white with alpha; tinted in code |
| `textures/particle/meteor_flame_0..3.png.b64` | same | flame licks for additive rendering |
| `textures/particle/meteor_spark_0..1.png.b64`, `meteor_ash_0..2.png.b64` | same | sparks / embers and ash flakes |
| `textures/entity/meteor_glow.png.b64` | same | radial glow halo of the meteor |
| `textures/entity/meteor_tail.png.b64` | same | plasma tail strip (u along the tail, tiling; v across) |

Paths are relative to `viltrumitecore/src/main/binassets/assets/viltrumitecore/`. The meteor fire block reuses the
vanilla fire models and textures by parent reference (`models/block/meteor_fire_*.json`); nothing is copied.

## Sound sources (all CC0 1.0, checked on Openverse; preview MP3s fetched by the skill helper)

| Key | Openverse UUID | Freesound | Creator | Original title | Attribution (verbatim) |
|---|---|---|---|---|---|
| `explosion_debris` | `8c54c9c2-618e-41fb-82b0-0d1a20bf0c23` | [367622](https://freesound.org/people/FxKid2/sounds/367622) | FxKid2 | Explosion with Debris.wav | "Explosion with Debris.wav" by FxKid2 is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `far_explosion` | `023d25d5-a1e6-475d-a148-db56cd73636d` | [264031](https://freesound.org/people/Logicogonist/sounds/264031) | Logicogonist | low mid afar explosion 1.wav | "low mid afar explosion 1.wav" by Logicogonist is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `fire_crackling` | `166449d3-2ad9-4f48-bbc1-e15887464a56` | [181563](https://freesound.org/people/kingsrow/sounds/181563) | kingsrow | Fire Crackling 01.wav | "Fire Crackling 01.wav" by kingsrow is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |
| `fire_roaring` | `74ceb541-e106-4e94-81eb-7eef6adae23b` | [636178](https://freesound.org/people/jamesdrake89/sounds/636178) | jamesdrake89 | Fire - Crackling, Spitting, Roaring | "Fire - Crackling, Spitting, Roaring" by jamesdrake89 is marked with CC0 1.0. To view the terms, visit https://creativecommons.org/publicdomain/zero/1.0/. |

License URL for all four: https://creativecommons.org/publicdomain/zero/1.0/. The Openverse searches used
`license=cc0&source=freesound` ("explosion", "fire crackling"); several other searches (rumble, whoosh, debris,
rubble, glass) timed out, so those families are synthesised instead.

## Sounds

Recipe: `tools/sfx/meteor_rain.py` (seeded; loops built over exactly one loop length in the frequency domain, so
they are seamless). Packaged by the skill helper as mono 44.1 kHz Vorbis q5 `.ogg.b64` in
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/meteor_rain/`. Events:
`viltrumitecore/src/main/sound-overlays/meteor_rain_fx.json`; registry `worldevent/meteor/MeteorRainSounds`;
subtitles in all 12 locales (`src/main/lang-overlays/meteor_rain/`). Playback is client side only
(`client/meteor/MeteorRainAudio`, `CityFireClient`).

| Event `meteor_rain.*` | Files | Layers / processing | Trigger and playback |
|---|---|---|---|
| `approach` | `approach` (3.0 s loop) | own: frozen low rumble (<90 Hz), 260 Hz body with flutter, faint 1.6 kHz hiss, periodic swell | follows the nearest meteor up to 320 blocks, manual distance curve, attenuation off |
| `roar` | `roar` (3.0 s loop) | own body/sub noise + frozen spectrum of `fire_roaring` (166–170 s), periodic sputter | the 3 nearest meteors within 190 blocks; Doppler pitch from the radial speed |
| `whoosh` | `whoosh_1`, `whoosh_2` (1.7 s) | own: pink noise through a band sweeping to 2.6 / 1.9 kHz, peak at 1.0 / 1.15 s, low push | ~1 s before a meteor's predicted closest approach within 36 blocks, once per meteor |
| `impact_near` | `impact_near_1`, `_2` (3.0 s) | `explosion_debris` 0–3 s at pitch 1.0 / 0.88 + own 60 ms crack + dropping 70→32 Hz sub boom + low rumble | impact within ~120 blocks, fades out from 40 to 120 |
| `impact_far` | `impact_far_1`, `_2` (3.5 s) | `far_explosion` at pitch 0.92 / 0.8, band 35 Hz–1.4 kHz, two darker echoes, sub swell | fades in from 40 blocks, the only layer far away (FAR_IMPACT packet 150–480 blocks) |
| `shockwave` | `shockwave` (1.6 s) | own: 48→28 Hz thump, tanh saturation for small speakers, low air burst | every impact within 260 blocks |
| `debris` | `debris_1`, `_2` (2.4 s) | `explosion_debris` tail (2.4 / 2.7 s on) + own rock ticks and thuds thinning out | impacts within 60 blocks, 10–17 ticks after the boom |
| `rubble` | `rubble` (2.6 s) | own: low clatter grains over a gritty bed | impacts within 45 blocks, 26–35 ticks after the boom |
| `fire_crackle` | `fire_crackle_1..3` (0.5 s) | the three strongest transients of `fire_crackling` (28–33 s) and `fire_roaring` (120–150 s), 300 Hz–12 kHz | burning blocks' animate ticks, at most 8 per second overall, attenuation 16 |
| `fire_roar` | `fire_roar` (3.0 s loop) | own deep roar + frozen `fire_roaring` spectrum + three placed crackles | the 2 nearest fire zones (edge distance < 70), from the zone edge nearest to the listener |
| `fire_flare` | `fire_flare_1`, `_2` (1.3 s) | own "whoomp" sweep + a crackle | flare packets and landed brands, attenuation 32 |
| `brand` | `brand` (1.0 s) | own small flaming swish + a crackle | a brand thrown, attenuation 24 |

Impacts are delayed by the speed of sound (343 m/s, capped at 28 ticks) and thinned when several land within 1.5 s
(tails first, then everything ducks). No vanilla sound was replaced: the vanilla explosion of the impact still plays.

**Not auditioned by ear**: the mixes were checked by numbers (peak, loudest-100 ms RMS, loop seam step, encoded
size) and spectrograms only; the final balance needs an in-game listening pass.
