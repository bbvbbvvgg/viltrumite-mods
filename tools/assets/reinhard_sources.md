# Reinhard asset sources

One row per committed asset. Raw archives are never committed (`reinhard_assets.zip`, given by the user in the Stage 1 session).

| Committed file | Source archive | Original path | Author | Conversion |
|---|---|---|---|---|
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/reinhard/emblem.png.b64` | `reinhard_assets.zip` | `icons/knight_emblem.png` (16×16) | grebeshok105 (user archive) | none, copied byte for byte; `tools/assets/make_reinhard_icons.py` |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/reinhard/hero.png.b64` | `reinhard_assets.zip` | `icons/reinhard.png` (32×32) | grebeshok105 (user archive) | none, copied byte for byte; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/reinhard/blessing_bearer.png.b64` | `reinhard_assets.zip` | `icons/blessing_bearer.png` (32×32) | grebeshok105 (user archive) | none, copied byte for byte; `tools/assets/make_reinhard_icons.py` (HUD Phoenix indicator, drawn at 12×12) |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/reinhard/{mode_restrained,mode_knight,mode_holy_sword}.png.b64` | own work | `tools/assets/make_reinhard_icons.py` | Notion AI for grebeshok105 | drawn by the script (12×12: fist on white, gauntlet on blue, sword hilt on gold) |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/gui/ability/reinhard/*.png.b64` (17 ability icons) | own work | `tools/assets/reinhard_icon_art.py` via `make_reinhard_icons.py` | Notion AI for grebeshok105 | drawn by the script on a 32×32 grid in the style of `blessing_bearer.png` (its frame pixels are reused and re-tinted per mode; palette = that icon + Endesga 64), upscaled 2× nearest to 64×64 |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/reinhard/reinhard.geo.json` | `reinhard_assets.zip` | `reinhard_model/reinhard.obj` | grebeshok105 (user archive) | `tools/assets/convert_reinhard.py` → `tools/obj2geo.py`: six bones, vanilla pivots, +24 px, rotated boxes as cube `rotation`/`pivot` |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/reinhard/reinhard_arms.geo.json` | `reinhard_assets.zip` | `reinhard_model/reinhard.obj` (arms) | grebeshok105 (user archive) | same script, arms only, bones `armorRightArm` / `armorLeftArm` |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/reinhard/sheath.geo.json` | `reinhard_assets.zip` | `sword/blade_sheath.obj` | grebeshok105 (user archive) | same script: ×16, origin at the hilt guard, one bone `sword`; `dragon_glow` overlay faces dropped, vt measured from the top |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/reinhard/blade.geo.json` | `reinhard_assets.zip` | `sword/training_blade_drawn.obj` (+ `training_blade.obj` for the sheathed offset) | grebeshok105 (user archive) | same as the sheath; empty bone `sheathed_offset` pivot (0, −14, 0) |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/reinhard/{reinhard,reinhard_glow}.png.b64` | `reinhard_assets.zip` | `reinhard_model/{reinhard,reinhard_glow}.png` (128×128) | grebeshok105 (user archive) | none, copied byte for byte; same script |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/reinhard/{dragon_sword,dragon_sword_glow}.png.b64` | `reinhard_assets.zip` | `sword/{dragon_sword,dragon_sword_glow}.png` (128×128) | grebeshok105 (user archive) | none, copied byte for byte; same script |


## Sounds

The core JAR bundles this table as `META-INF/credits/reinhard-sources.md` and maintained credits as `META-INF/credits/reinhard-credits.md`; root-only attribution is not a standalone distribution mechanism.

Checked against the live Freesound sound pages on **2026-10-13** (not inferred from archive names). Raw `sounds.zip` is external input, never committed. Sources below are used only by `tools/sfx/reinhard.py`. Changes: trimmed / layered / normalized; mono 44.1 kHz Vorbis q5. No synthesis. These transformations are not in-game listening or runtime acceptance.

| ID / sound name | Author | License (version) | Freesound page | Exact archive file | Output (.ogg.b64 basename) / status | Source SHA-256 |
|---|---|---|---|---|---|---|
| 398218 — St. John Cathedral female choir 001.wav | klankbeeld | [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/) | https://freesound.org/people/klankbeeld/sounds/398218/ | `398218__klankbeeld__st-john-cathedral-female-choir-001.wav` | mode_restrained, mode_knight, mode_holy, reid_choir | `303b75361ae6ae2f407f3661bf4bdb498afbe39ab4e3d02c3c97cdeb365807be` |
| 770035 — Sword - Unsheathe | Vrymaa | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/Vrymaa/sounds/770035 | `770035__vrymaa__sword-unsheathe.wav` | reid_unsheathe | `cadfdd8d7427fff68415b482d27fe714cfd86d60d2a83acf8d4200e5dd895e26` |
| 870684 — Very low rumble (Stereo) | CHallSmith | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/CHallSmith/sounds/870684/ | `870684__challsmith__very-low-rumble-stereo.wav` | reid_rumble | `fb3d74efcb82021a397dbf07649ce409e53dd1b53b8e16cd9532fa47c76c2926` |
| 503809 — reverse fx 6.wav | reathance | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/reathance/sounds/503809/ | `503809__reathance__reverse-fx-6.wav` | world_cut_silence | `bf2a3a7d7df281130e36776b445d2a0627cf41c9a91c0fd196e85fabe868d278` |
| 248150 — Deepest Boom Yet | hykenfreak | [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/) | https://freesound.org/people/hykenfreak/sounds/248150/ | `248150__hykenfreak__deepest-boom-yet.wav` | world_cut_tail, world_cut_hum, world_cut_pulse, world_cut_impact | `8a95a5fabb53a3140371b8b6edef304ed75598672f4d4f1e1b565c773c866e5c` |
| 825864 — Deep Cinematic Boom | Alliare | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/Alliare/sounds/825864 | `825864__alliare__deep-cinematic-boom.mp3` | NOT USED: World Cut mix blocked by 93835 | `83ac7b803c169fc675bd4a6f3429a307fbaf4aaa905054256abac1e0a2caf9fa` |
| 93835 — Huge Explosion1.flac | CGEffex | [CC-BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/) | https://freesound.org/people/CGEffex/sounds/93835 | `93835__cgeffex__huge-explosion1.flac` | EXCLUDED: world_cut_blast placeholder retained | `51a0a552b30c090ea7f7b89520e9df56cbad51045c168392e421b8cfab9ebaa5` |
| 554564 — Glass Shatter 2.wav | Greg_Surr | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/Greg_Surr/sounds/554564/ | `554564__greg_surr__glass-shatter-2.wav` | erase_crack | `79b099b8a8cdfa20e85d3097d2a0f7d2a0d3592f18cc59e7d968c84d810577cf` |
| 331620 — Spooky Sucking Air  | hykenfreak | [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/) | https://freesound.org/people/hykenfreak/sounds/331620/ | `331620__hykenfreak__spooky-sucking-air.wav` | erase_inhale | `7f40dff2eb2bab8cc0eda07b9d37b270fed09e29038cae39147e1b26874ffa7a` |
| 249716 — SLOW HEARTBEAT | daandraait | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/daandraait/sounds/249716 | `249716__daandraait__slow-heartbeat.wav` | limbo_heartbeat | `1ed32edb9121a482e25ad2a606047a2efee40e6682d13a28539a80585c56d134` |
| 182050 — Sonic Boom | qubodup | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/qubodup/sounds/182050/ | `182050__qubodup__sonic-boom.flac` | sky_kick | `68e00321c8f0219078b4b9737a561fcd1316c67800a031828df1bd926a74e06f` |
| 275809 — earthquake.aif | dingus23 | [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/) | https://freesound.org/people/dingus23/sounds/275809/ | `275809__dingus23__earthquake.aiff` | stomp | `60ab71881cdb214c09a624cdee22ada76624c3fce1ff3af061174596148888aa` |
| 378199 — 06992 sucking flyby whoosh.wav | Robinhood76 | [CC-BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/) | https://freesound.org/people/Robinhood76/sounds/378199/ | `378199__robinhood76__06992-sucking-flyby-whoosh.wav` | EXCLUDED: cut_rain placeholder retained | `6a7b98c9fa7f633a36e0c206e2450bf7426a3e8aa87847dba6568147394d3df1` |
| 519413 — Sword , blade ring and scrape | Iridiuss | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/s/519413 | `519413__iridiuss__sword-blade-ring-and-scrape.m4a` | sheath_ring, world_cut_blade | `2cbf59f58b0f0e516fd80f876df184e31c3d274ebd65a9577f760fbb03e972da` |

### Blocked licenses — owner decision pending

- **93835 (CGEffex), CC-BY-NC 4.0:** not allowed by this hero's CC0/CC-BY rule. No copy, decoded audio, or layer from this file is distributed. `reinhard.world_cut_blast` keeps its exact Stage 6 vanilla placeholder; **825864 is not silently used alone**. Deferred question: approve Alliare [825864](https://freesound.org/people/Alliare/sounds/825864/) (CC0 1.0) alone, or mix it with hykenfreak [248150](https://freesound.org/people/hykenfreak/sounds/248150/) (CC-BY 4.0)? Neither alternative has been substituted or auditioned.
- **378199 (Robinhood76), CC-BY-NC 4.0:** excluded. `reinhard.cut_rain` keeps its exact Stage 6 vanilla placeholder. Deferred question: approve a trim of reathance [503809](https://freesound.org/people/reathance/sounds/503809/) or Vrymaa [770035](https://freesound.org/people/Vrymaa/sounds/770035/) (both CC0 1.0)? These are candidates, not approved replacements.

### Events with no selected source in §10.1

The plan lists fewer recordings than Stage 6 event IDs. Preserve the original cues for `air_step`, `refuse`, `phoenix`, `parry`, `super_jump`, `step_dash`, `catch`, `grab`, `dash_behind`, `reflect`, `reid_far`, `reid_sheathe`: no owner-approved recording for these events. They remain explicit unresolved legacy cues, not falsely claimed as final sounds. §10.2 (`light_punch`, `combo_hit`, `combo_final`, `kick_toss`, `throw` and the kit Chop sounds) stays unchanged. `mode_holy_sword` remains the event ID; its file is `mode_holy`. `sheath_strike` points to `sheath_ring`.

klankbeeld additionally requests credit to Freesound and no raw-stock redistribution in the description. Only transformed excerpts in this mod are distributed; no raw recording or stock library. The page's CC-BY 4.0 license is recorded above.

### Processed outputs / reproducible edits

All files are `viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/reinhard/<name>.ogg.b64`. Individual operations, peak conditioning and pinned first-pass values are in the script table; no measurements are ear-selected. Run `python3 tools/sfx/reinhard.py --src /path/to/unpacked/sounds --check`. Decode-only hashes, not container bytes, define reproducibility.

| Output | Source ID | Start / length (s) | Class target | Changes |
|---|---|---|---|---|
| `mode_restrained` | 398218 | 10 / 1.8 | -18 LUFS | trim, fades, two-pass loudnorm |
| `mode_knight` | 398218 | 20 / 2 | -18 LUFS | trim, fades, two-pass loudnorm |
| `mode_holy` | 398218 | 30 / 2.2 | -18 LUFS | trim, fades, two-pass loudnorm |
| `reid_choir` | 398218 | 40 / 6 | -20 LUFS | trim, fades, two-pass loudnorm |
| `reid_unsheathe` | 770035 | 0 / 1.879297 | -14 LUFS | trim, fades, two-pass loudnorm |
| `reid_rumble` | 870684 | 10 / 6 | -20 LUFS | trim, fades, two-pass loudnorm, loop-edge fades |
| `world_cut_silence` | 503809 | 3 / 3 | -20 LUFS | trim, fades, two-pass loudnorm |
| `world_cut_tail` | 248150 | 0 / 8 | -14 LUFS target (-14.66 measured) | original -20 LUFS master, +12 dB pre-gain + lookahead limiter; decoded true peak -2.25 dBTP; DONE only |
| `erase_crack` | 554564 | 0 / 1.5 | -14 LUFS | trim, fades, two-pass loudnorm, peak limiting |
| `erase_inhale` | 331620 | 0 / 2.3 | -20 LUFS | trim, fades, two-pass loudnorm |
| `limbo_heartbeat` | 249716 | 0 / 1.2 | -20 LUFS | trim, fades, two-pass loudnorm, peak limiting, loop-edge fades |
| `sky_kick` | 182050 | 0 / 2.5 | -14 LUFS | trim, fades, two-pass loudnorm |
| `stomp` | 275809 | 0 / 3 | -14 LUFS | trim, fades, two-pass loudnorm, peak limiting |
| `sheath_ring` | 519413 | 2.31 / 1.1 | -14 LUFS | trim, fades, two-pass loudnorm, peak limiting |
| `world_cut_hum` | 248150 | 0.10 / 2.00 seed | -12.6 LUFS | forward + time reverse, exact seam, 8 ms circular smoothing, tanh drive 2.0/ceiling 0.8 for loudness; 1.992 s loop; `tools/sfx/reinhard_worldcut.py` |
| `world_cut_pulse` | 248150 | 0.10 / 1.35 | -18 LUFS | pitch 0.90, 30 ms attack, quadratic release; `tools/sfx/reinhard_worldcut.py` |
| `world_cut_impact` | 248150 | 0.10 / 0.60 | -14 LUFS | pitch 0.90, 5 ms attack, tight decay, tanh drive 4.0/ceiling 0.8; `tools/sfx/reinhard_worldcut.py` |
| `world_cut_blade` | 519413 | 2.31 / 1.1 | -14 LUFS | same `sheath_ring` file; separate event id so only the normal-cut cue is duck-exempt |

## Cinematic original synthesis (2026-10-20)

No new external recordings. `tools/sfx/reinhard_cinematic.py` deterministically creates four original CC0-1.0 layers:
- `reid_cinematic_layer`: 6.00 s tension, metal harmonics and light pulse, alongside existing approved draw recordings.
- `cut_rumble`: 1.25 s low air/stone layer, retriggered no faster than 1.4 s, no overlapping instance per cast.
- `cut_cinematic_impact`: 3.40 s displacement impact with decaying low tail; replaces the vanilla World Cut placeholder, not blocked NC recordings.
- `cut_cinematic_sweep`: 0.32 s pressure sweep, written for Rain of Cuts; since item 19 (2026-10-27) it voices the Saw Sphere swings (`reinhard.saw_swing`, Rain retired); replaces the vanilla placeholder, not blocked NC recordings.

## World Cut explosion — original synthesis (2026-10-26)

`world_cut_explosion.ogg.b64` (event `reinhard.world_cut_explosion`) is the heavy explosion under the strike voice on
the BLAST of the normal World Cut. `tools/sfx/reinhard_explosion.py` is its only writer: deterministic synthesis from
seeded numpy noise and oscillators, **no recording and no download**, CC0-1.0, packaged with the sound-design helper
(`package --own`, mono 44.1 kHz Vorbis q5).

| Layer | Recipe | Role |
|---|---|---|
| slam | band-passed noise 200 Hz-6 kHz, 1.5 ms attack, 60 ms decay, plus an 8 ms crack above 2 kHz | the attack, lands on the voice's first hit |
| boom | sine sweep 98 -> 30 Hz, 4 ms attack, 1.0 s decay, tanh drive 2.2, low-pass 420 Hz | the low boom; its 60-250 Hz harmonics carry it without a sub woofer |
| roar | pink noise through a falling low-pass (3.5 kHz -> 250 Hz), high-pass 110 Hz, 0.7 s decay, seeded turbulence | the fireball |
| echoes | four low-passed slap-backs of slam + early roar at 0.17 / 0.38 / 0.75 / 1.30 s, -9 to -21 dB | the size of an open valley |
| rumble | brown noise 22-140 Hz plus a 150-500 Hz grit layer, slow seeded swell, 2.4 s decay | the rolling decay under the voice |
| debris | sparse seeded grains 0.4-2.5 kHz thinning out over 3.5 s, -26 dB | falling rock |

6.00 s mono, 100 ms momentary max -8.2 LUFS (K-weighted meter of `tools/sfx/reinhard_strikes.py`, set to -7.5 LUFS
under a -1 dBTP ceiling with at most 3 dB of look-ahead limiting), decoded true peak -1.0 dBTP, integrated
-21.0 LUFS (long decay). About 77 % of the first second sits below 160 Hz, where the strike voice (most energy at
2.5-5 kHz) is thin. Played by `WorldCutAudio.strike` together with the voice, once per cast, at the nearest point
of the cut (positional, linear 320-block attenuation, gain 0.85). `python3 tools/sfx/reinhard_explosion.py --check`
re-synthesizes and compares with the committed file. Measured numbers only, **not a listening test**: the sandbox
has no audio playback.

## Saw Sphere — synthesis and CC0 glass recordings (2026-10-27)

`saw_hum`, `saw_grind`, `saw_finale` and `saw_shatter` (events `reinhard.saw_*`, item 19) are written only by
`tools/sfx/reinhard_saw_sphere.py`. The first three are deterministic synthesis from seeded numpy noise and
oscillators, **no recording and no download**, CC0-1.0, packaged with the sound-design helper (`package --own`, mono
44.1 kHz Vorbis q5). `saw_shatter` was redone after the owner's playtest (the synthetic glass grains sounded like
beeps): it layers four **CC0 1.0** Freesound recordings, fetched by the script as the public HQ Ogg previews (cached
outside the repo, SHA-256 pinned in the script, never committed) and checked on their live pages on 2026-10-27,
over a synthesized sub boom and crystal ring; the script encodes it like the helper (mono 44.1 kHz Vorbis q5, base64).
Changes: trimmed at the onset / filtered / layered / normalized. The swing (`reinhard.saw_swing`) reuses
`cut_cinematic_sweep` and adds no file.

| Freesound id — title | Author | License | Page | Layer in saw_shatter |
|---|---|---|---|---|
| [221528](https://freesound.org/people/unfa/sounds/221528/) — Glass Break | unfa | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/unfa/sounds/221528/ | the crack, high-passed 700 Hz, 0 dB |
| [244238](https://freesound.org/people/chewiesmissus/sounds/244238/) — Glass Smash.wav | chewiesmissus | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/chewiesmissus/sounds/244238/ | the dense smash, high-passed 120 Hz, -2 dB at 8 ms |
| [336425](https://freesound.org/people/221Beimesche/sounds/336425/) — Glass Shattering and Falling | 221Beimesche | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/221Beimesche/sounds/336425/ | shards raining down, high-passed 250 Hz, -3 dB from 0.1 s |
| [349905](https://freesound.org/people/bevibeldesign/sounds/349905/) — Slow Motion Glass Shatter | bevibeldesign | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | https://freesound.org/people/bevibeldesign/sounds/349905/ | shimmer above 1.8 kHz, -11 dB from 50 ms |

| Cue | Recipe | Role | Level (100 ms max / true peak before encoding) |
|---|---|---|---|
| saw_hum | 4.0 s seamless loop: 55 Hz drone with detuned harmonics, tanh drive, 300-1400 Hz whirr pulsed at ~7 Hz, faint 1.76 / 2.64 kHz shimmer; every partial completes whole cycles, the noise is filtered circularly | bed of the whole scene; the client rises it (pitch 0.6 -> 1.0, volume) while the shell grows | -16 LUFS / -9.3 dBTP, never limited (seam step 0.38 of the largest inner step) |
| saw_grind | 0.55 s toothed noise 2-7 kHz (95 Hz tooth rate), four inharmonic metal partials, a short rock crunch below 600 Hz | one disc biting stone, rate-limited to one per 3 ticks per scene | -14 LUFS / -8.3 dBTP |
| saw_finale | 3.0 s sub thump 94 -> 34 Hz, an air sweep peaking at 0.25 s, a long inharmonic metal chord from 392 Hz | the last great swing | -9 LUFS / -1.1 dBTP |
| saw_shatter | 3.6 s the four recordings above (crack, smash, falling shards, slowed shimmer) + a 90 -> 42 Hz tanh sub boom (-5 dB) + a faint E6 crystal ring (-24 dB) | the shell breaking up | -10 LUFS / -3.0 dBTP |

`python3 tools/sfx/reinhard_saw_sphere.py --check` re-synthesizes / re-mixes and compares with the committed files
(correlation >= 0.95; saw_shatter 0.98 against its own Vorbis encode). Measured numbers only, **not a listening test**: the sandbox has no audio playback.

## REINHARD OVERHAUL strike sounds (CC0 1.0, Freesound via Openverse)

Found with the sound-design skill (`.agents/skills/sound-design/scripts/openverse_sfx.py search`, `license=cc0&source=freesound`, one query per family: `fast punch whoosh`, `punch impact body hit`, `sword swing whoosh`, `sword slash cut`, `heavy sword impact`, `deep impact boom`, `sword ring resonance`) and fetched with its `download` command. License, creator and description were checked on each live Freesound page on **2026-10-25**; every page shows CC0 1.0. 420674 states it is a modified version of Abyssmal's [35213 "slashkut"](https://freesound.org/people/Abyssmal/sounds/35213/), whose page is also CC0 1.0. The downloaded files are Openverse's Freesound **preview MP3s** (lossy, not the original masters); raw downloads, previews and scratch mixes are not committed. The verbatim Openverse attribution lines are kept in the local `~/.cache/viltrumite-sfx/work/credits.txt`.

| Freesound | Original title / file name | Creator | License | Openverse ID | Used in |
|---|---|---|---|---|---|
| [614084](https://freesound.org/people/mateusboga/sounds/614084) | Woosh 1 | mateusboga | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | b8d4ab14-8b11-47a9-a386-10bd626ee880 | fist_swing_1, reid_swing_2 |
| [60004](https://freesound.org/people/qubodup/sounds/60004) | Fast Sword Swing Sound | qubodup | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | 518ead89-8e89-4614-b954-bb7a844f3c42 | fist_swing_2, reid_swing_2 |
| [60024](https://freesound.org/people/qubodup/sounds/60024) | swosh swinging sword 37.flac | qubodup | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | 084c97f3-439e-4f7f-af13-5473411c5c43 | reid_swing_1, reid_swing_3 |
| [507466](https://freesound.org/people/Danjocross/sounds/507466) | Clean fast Swoosh.aiff | Danjocross | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | c1b6b14f-c5d2-4511-9d8a-81fb973efe58 | fist_swing_3, reid_swing_3 |
| [475132](https://freesound.org/people/bolkmar/sounds/475132) | FX - Swoosh - Light | bolkmar | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | d343ce9e-01df-4852-b625-3ac3eb805cdd | reid_swing_1 |
| [393904](https://freesound.org/people/Rodzuz/sounds/393904) | Body punches | Rodzuz | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | 84ea30b3-f549-4663-919b-588d7ed3bac6 | fist_hit_1..3, reid_hit_2, reid_heavy_1 |
| [512389](https://freesound.org/people/Kievinay/sounds/512389) | Bass Impact - Ki_01.mp3 | Kievinay | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | d946174b-650a-4ef5-ba65-2b11686aa732 | fist_hit_1..3, reid_hit_1..3, reid_heavy_1..2 |
| [420674](https://freesound.org/people/SypherZent/sounds/420674) | Deep Cut / Slash / Gash | SypherZent | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | 83b6422f-50a2-45e5-8408-048db7b9e341 | reid_hit_3, reid_heavy_2 |
| [547038](https://freesound.org/people/CogFireStudios/sounds/547038) | Hit Swing Sword | CogFireStudios | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | cd90a58e-6aa9-4f1b-a421-96d8861147c1 | reid_hit_2 |
| [547035](https://freesound.org/people/Eponn/sounds/547035) | Hit Impact Sword | Eponn | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | ced84325-bddc-4add-be3c-b529c146219f | reid_hit_3, reid_heavy_2 |
| [547036](https://freesound.org/people/Eponn/sounds/547036) | Hit Impact Sword 2 | Eponn | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | b122081c-ef13-455b-8fd0-675fcbb8157e | reid_hit_1 |
| [547042](https://freesound.org/people/CogFireStudios/sounds/547042) | Hit Impact Sword 3 | CogFireStudios | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | 3c27fa96-eaf7-440f-9d1c-d14db5e381ae | reid_heavy_1 |
| [22426](https://freesound.org/people/kibibu/sounds/22426) | sword_2.wav | kibibu | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | 2ee6f76b-8bfb-4129-b200-7bdc4f398bb0 | reid_hit_1..3, reid_heavy_1..2, reid_drawn_hum |

`tools/sfx/reinhard_strikes.py` is the only writer of these assets and holds the exact recipe (start, length, varispeed pitch, gain, filters, fades per layer). Each event picks a random variant per play.

| Output | Layers (role) | Trigger alignment | Level (100 ms K-weighted max / true-peak ceiling) |
|---|---|---|---|
| fist_swing_1..3 | one whoosh each: 614084 / 60004 (pitch 1.12) / 507466; high-pass 150 Hz | whoosh peak about 65 ms in (played one tick before contact) | -15 LUFS / -3 dBTP |
| fist_hit_1..3 | three single chest punches cut from 393904 (onset minus 3 ms, high-pass 90 Hz, +4 dB at 1.7 kHz) over a short pitched-up 512389 thump at -19/-20 dB | transient at 0 | -15.5 LUFS (crest-limited, no limiter) / -1 dBTP |
| reid_swing_1..3 | deep whoosh pitched 0.78-0.88 (60024, 60004, 507466) plus an air layer (475132, 614084, 60024) | peak 105-130 ms in (played two ticks before contact) | -13.8 LUFS / -2 dBTP |
| reid_hit_1..3 | sword-in-flesh hit (547036 / 547038 / 420674 + 547035 body), 512389 thump, 22426 deep ring at -18 dB; 393904 body blow in variant 2 | transient within 35 ms | -9 to -9.5 LUFS / -1 dBTP, at most 1.5 dB look-ahead limiting |
| reid_heavy_1..2 | heavy sword hit pitched 0.92 / 0.88 (547042 / 547035), full 512389 boom, 22426 ring at -12 dB, plus a 393904 body blow or a 420674 slice | transient within 30 ms, 1.6 s tail | -8.3 to -8.9 LUFS / -1 dBTP, at most 1.5 dB limiting |
| reid_drawn_hum | spectral freeze of the 22426 ring tail (0.35-2.35 s, decay flattened), seeded random phase over exactly 4.000 s, 70 Hz-3 kHz, +12 dB tilt above 250 Hz, periodic 0.5/1.25 Hz breathing | seamless 176400-sample loop, no fades | -18 LUFS / -6 dBTP |

44.1 kHz mono Vorbis q5 through the skill helper's `package` command (`.ogg.b64`). `python3 tools/sfx/reinhard_strikes.py --check` re-mixes from the downloads and compares with the committed files (correlation >= 0.95). Levels are measured numbers, **not a listening test**: the sandbox that produced them has no audio playback, so the cues were not auditioned and still need an in-game listening pass.

## World Cut ambience from the owner re-export (2026-10-21)

The owner supplied `01-original-final-tail.ogg` (8.00 s mono, SHA-256
`8fc6abd5...12dce6`). It is the approved hykenfreak `248150` tail re-exported
about 6.7 dB quieter; the shipped file is not byte-identical to the archive
trim. `tools/sfx/reinhard_worldcut.py` is the only writer of the two derived
assets. The 1.992 s hum sums the first 2.00 s with its own time reverse and
then smooths the wrap; the 1.35 s pulse is a pitched-down swell of the same
seed. Both are mono 44.1 kHz Vorbis q5 and pass decoded seam, loudness and
peak gates. Run `python3 tools/sfx/reinhard_worldcut.py --src <file> --check`.
No raw input is committed and no listening proof is claimed.

24 kHz mono Vorbis quality 4, faded/soft-limited source PCM peak -6.02 dBFS. Decoded true peaks measured with ffmpeg ebur128: draw -5.9, impact -5.3, sweep -5.1 dBFS. This is file-level headroom, not a claim of clipping-free scene mixing. Subjective gain and Minecraft mix require listening in game. Existing CC-BY attribution stays unchanged.

## REINHARD OVERHAUL draw sounds (item 15; CC0 1.0 recordings and own synthesis)

The Reid draw has its own five cues, one per phase of the 6 s draw (draw ticks 0-120), started by the client on the scene clock (`ReidDrawAudio`): only CC0 recordings and own synthesis (the older choir / rumble / unsheathe events are no longer played by the draw). Recordings were found with the sound-design skill (`.agents/skills/sound-design/scripts/openverse_sfx.py search`, `license=cc0&source=freesound`; queries such as `sword unsheathe slow metal scrape`, `metal scrape long`, `blade scrape metal slide`, `sword sheath draw`, `katana`, `deep boom impact`, `wind gust whoosh`) and fetched with its `download` command. License, creator and description were checked on each live Freesound page on **2026-10-26**; every page shows CC0 1.0. 337835 says it was cut up and edited from EpicWizard's [264889 "SFX Metal Shed Door - Hits, Clangs, Rattle"](https://freesound.org/people/EpicWizard/sounds/264889/), whose page is also CC0 1.0. The downloads are Openverse's Freesound **preview MP3s** (lossy, not the original masters); raw downloads, previews and scratch mixes are not committed. The verbatim Openverse attribution lines are kept in the local `~/.cache/viltrumite-sfx/work/credits.txt`.

| Freesound | Original title / file name | Creator | License | Openverse ID | Used in |
|---|---|---|---|---|---|
| [592023](https://freesound.org/people/C-V/sounds/592023) | sword drawn.wav | C-V | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | c9cba616-8be5-47bc-9edb-ac99cb6843f3 | reid_draw_scrape (slow draw, rip and exit) |
| [337835](https://freesound.org/people/newagesoup/sounds/337835) | long-metal-scrape-05.wav | newagesoup | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | 0e790aec-2dbc-4a85-9771-e03c4ccb7065 | reid_draw_scrape (grit layer) |
| [593485](https://freesound.org/people/Scottrex05/sounds/593485) | Katana sheath.wav | Scottrex05 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | 787844c2-63f2-485a-8eaf-e23be80af3e0 | reid_draw_koiguchi (clack) |
| [649933](https://freesound.org/people/AudioPapkin/sounds/649933) | Riser Hit sfx 046.wav | AudioPapkin | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | 0b657f87-2549-45e8-a1ae-4a991818ced8 | reid_draw_release (hit and decay) |
| [512389](https://freesound.org/people/Kievinay/sounds/512389) | Bass Impact - Ki_01.mp3 | Kievinay | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | d946174b-650a-4ef5-ba65-2b11686aa732 | reid_draw_koiguchi, reid_draw_release (also used by the strikes) |
| [22426](https://freesound.org/people/kibibu/sounds/22426) | sword_2.wav | kibibu | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | 2ee6f76b-8bfb-4129-b200-7bdc4f398bb0 | reid_draw_koiguchi, reid_draw_scrape, reid_draw_release (also used by the strikes) |

`tools/sfx/reinhard_draw.py` is the only writer of these assets and holds the exact recipe (start, length, varispeed pitch, time stretch, filters, gains, fades, envelopes on the draw timeline of `ReidDrawChoreo`). `reid_draw_hum` and `reid_draw_air` are own synthesis (numpy, fixed seeds; CC0-1.0).

| Cue | Starts on (draw tick) | Layers | Phase alignment | Level (100 ms K-weighted max / true-peak ceiling) |
|---|---|---|---|---|
| reid_draw_hum | d6, 6.3 s | own: low drone rising 41 to 55 Hz, a detuned second voice, inharmonic blade partials from the koiguchi, band-limited rumble bed | rises with the pull, trembles in the strain (86-92), holds its breath (92-96), swells at the release, natural decay | -17 LUFS / -3 dBTP |
| reid_draw_koiguchi | d33, 0.6 s | 593485 katana clack (pitch 0.85), 22426 faint ring, 512389 felt thump | clack at 0.050 s = d34 (the mouth breaks) | -14 LUFS / -1 dBTP, at most 1.5 dB limiting |
| reid_draw_scrape | d36, 3.8 s | 592023 slow tonal draw (pitch 0.8, stretched to 2.7 s) shaped by the extraction speed, 337835 grit from d58 stuttering in the strain, 592023 rip, 22426 ring of the freed blade | exit peak at 3.000 s = d96 (the release) | -12 LUFS / -1 dBTP, at most 1.5 dB limiting |
| reid_draw_air | d56, 2.0 s | own: fluttering band of wind 250 to 1100 Hz, rate rising | sucked in over the last 0.2 s, cut at d96 | -19 LUFS / -3 dBTP |
| reid_draw_release | d96, 4.8 s | 649933 riser hit (pitch 0.92), 512389 sub boom, 22426 deep ring (pitch 0.72), own burst of displaced air | transient at 0 = d96, natural decay over 4.8 s | -8 LUFS / -1 dBTP, at most 3 dB limiting |

44.1 kHz mono Vorbis q5 through the skill helper's `package` command (`.ogg.b64`). `python3 tools/sfx/reinhard_draw.py --check` re-mixes from the downloads and compares with the committed files; `tools/sfx/tests/test_draw.py` and `ReidDrawAudioTest` keep the cue ticks, the Java schedule and `sounds.json` in sync. Levels are measured numbers, **not a listening test**: the sandbox has no audio playback, so the cues were not auditioned and still need an in-game listening pass.

## TEMPORARY test asset — World Cut strike voice (license NOT verified)

REINHARD OVERHAUL item 12. `sword_strike_voice.ogg.b64` is the owner-confirmed sound of Reinhard's strike on
Theresia from Re:Zero Season 3 Episode 14 ("Reinhard Finally Draws His Sword", reference
https://www.youtube.com/watch?v=GtFqJpCrwCQ). It is a **temporary test asset**:

- Source file: owner repository https://github.com/bbvbbvvgg/Codex-Superheroes,
  `src/main/resources/assets/superheroes/sounds/reinhard/sword_strike_voice.ogg` at commit
  `be526ea313d7f5f652346c70a88b995f28977cee` (git blob `2cdc23f09b22fea399c62dfc805195ce4f121fd0`). The owner
  listened to it and confirmed it is the strike on Theresia; it was not searched for or re-cut here.
- Shipped byte-identical: stereo 48 kHz Vorbis, 8.76 s, 159533 bytes, SHA-256
  `b7c97f3fb070687b8ae4252a627f4f9b932dd7975fb16cee1a3f4694b04ae1a6`. The onset is at 3.6 ms (peak at 19 ms),
  so the hit lands on the tick the voice starts; about 4.5 s of loud body, then an even decay. Integrated
  loudness -14.7 LUFS (ffmpeg ebur128). Measured numbers only, **not a listening test**.
- Event `reinhard.world_cut_strike_voice` (stream), normal World Cut only: `WorldCutAudio.strike` starts it once
  on the elapsed BLAST tick (`ReinhardRules.WORLD_CUT_BLAST` = 80, the server tick of the first damage and the
  carve start) instead of the old `world_cut_impact` hit. Stereo is never positioned by the sound engine, so the
  voice is head-relative and the distance to the cut sets its volume (1.0 within 32 blocks, 0.25 at 320). The
  hum and the pulse voice drop to 45 % under its loud body. The diagonal World Cut audio is unchanged.
- **License NOT verified.** The recording comes from a commercial anime episode; no license is claimed and it is
  not covered by the CC0 / CC-BY rules of this file. **Replace it with a licensed or original sound before any
  public release** and keep the timing: the replacement must put its transient within the first ~20 ms and be
  played from the same `WorldCutAudio.strike` call. `reinhard.world_cut_impact` (derived from the CC-BY 248150
  tail) stays registered as the licensed fallback.
