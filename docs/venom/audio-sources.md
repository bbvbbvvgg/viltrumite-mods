# Venom — audio sources

Every Venom sound is cut, layered, pitched and levelled from **real recordings released under CC0 1.0** on Freesound.
No synthesis stands in for a recording, no vanilla Minecraft sound, no film or game audio is used.

**Licence proof.** Each row was checked on its Freesound landing page on 2026-11-02: the page names the author and the
title shown below and its licence link points to the CC0 1.0 deed `https://creativecommons.org/publicdomain/zero/1.0/`
(label "Creative Commons 0"). `python3 tools/sfx/venom.py --fetch --verify` downloads the pages again and fails if any
page shows another licence, another author or another title. The audio of each row is that page's hq MP3 preview.
CC0 needs no attribution; the authors are credited anyway. This file ships in the mod jar as
`META-INF/credits/venom-audio.md`.

Rendering: `python3 tools/sfx/venom.py --package` (mono 44.1 kHz Ogg Vorbis, base64 under
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/venom/`, events in
`viltrumitecore/src/main/sound-overlays/venom.json`). Levels are measured, not auditioned: loudest 100 ms RMS per class
(signature -12 dBFS, impact -11.5 dBFS, motion -15 dBFS, body -15.5 dBFS, host -18 dBFS, utility -17 dBFS), peak at most −1 dBFS.

## Sources

| Source | Freesound page | Author | Title (landing page) | Licence | Used in |
|---|---|---|---|---|---|
| `roar_scary` | [freesound.org/s/489901](https://freesound.org/s/489901/) | NicknameLarry | Scary Monster Roar #2 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | roar_1 |
| `roar_short` | [freesound.org/s/132874](https://freesound.org/s/132874/) | ecfike | Monster Short Roar.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | roar_1 |
| `roar_monster` | [freesound.org/s/537883](https://freesound.org/s/537883/) | colorsCrimsonTears | Monster roar | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | roar_2, transform |
| `growl_hybrid` | [freesound.org/s/146558](https://freesound.org/s/146558/) | john129pats | Hybrid Monster Growl | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | engulf, roar_2, transform |
| `growl_monster` | [freesound.org/s/262431](https://freesound.org/s/262431/) | jackieAZ | Monster Growl 1.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | refuse_2 |
| `grunt_monster` | [freesound.org/s/262430](https://freesound.org/s/262430/) | jackieAZ | Monster Grunt 1 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | refuse_1, throw_1 |
| `growl6` | [freesound.org/s/77637](https://freesound.org/s/77637/) | lendrick | growl6.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | refuse_1, revert, throw_2 |
| `screech` | [freesound.org/s/807383](https://freesound.org/s/807383/) | Elth2010 | Monster screech | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | shriek_1, shriek_2 |
| `slime_squish` | [freesound.org/s/442772](https://freesound.org/s/442772/) | qubodup | Slime Squish | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | claw_3, engulf, reform_1, squirm_3, tendril_shot_2, tendril_stick_2, transform |
| `slime27` | [freesound.org/s/433840](https://freesound.org/s/433840/) | Archos | Slime 27.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | grab_2, reform_1, reform_2, release, revert, transform |
| `gel_cube` | [freesound.org/s/837146](https://freesound.org/s/837146/) | e_r_r_1012 | Gelatinous Cube Approach | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | transform |
| `squelching` | [freesound.org/s/547446](https://freesound.org/s/547446/) | tiddles451 | squelching.mp3 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | squirm_1, squirm_2 |
| `gel_hands` | [freesound.org/s/575333](https://freesound.org/s/575333/) | breadparticles | squishing gel between hands | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | engulf, grab_2, reform_2, revert, shell, squirm_3, transform |
| `gore_squish` | [freesound.org/s/535354](https://freesound.org/s/535354/) | EminYILDIRIM | Blood Gore Slime Squish | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | claw_hit_3 |
| `staff_whoosh` | [freesound.org/s/422513](https://freesound.org/s/422513/) | Nightflame | Swinging staff whoosh (strong) 04.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | claw_1, claw_2, throw_1 |
| `swing_woosh` | [freesound.org/s/389590](https://freesound.org/s/389590/) | Jofae | Swing Woosh | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | claw_3, lash_2, throw_2 |
| `punch_impact` | [freesound.org/s/266823](https://freesound.org/s/266823/) | ms107 | Punch impact sound.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | clash_1, clash_2, claw_hit_1, claw_hit_3 |
| `major_punch` | [freesound.org/s/399183](https://freesound.org/s/399183/) | janbezouska | Major punch | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | clash_1, claw_hit_2, slam_1, slam_2 |
| `rope_whoosh` | [freesound.org/s/473583](https://freesound.org/s/473583/) | Nox_Sound | Whoosh_Rope.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | lash_1, tendril_shot_1, zip |
| `whip_woosh` | [freesound.org/s/735907](https://freesound.org/s/735907/) | ErikCruzDev | Whip woosh | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | tendril_shot_2 |
| `splat` | [freesound.org/s/147017](https://freesound.org/s/147017/) | JustInvoke | Splat | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | claw_hit_1, tendril_shot_1, tendril_stick_1 |
| `splat3` | [freesound.org/s/719131](https://freesound.org/s/719131/) | sebastientate | Splat 3 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | claw_hit_2, tendril_stick_2 |
| `slug_splat` | [freesound.org/s/447930](https://freesound.org/s/447930/) | Breviceps | Step on a slug (Splat!) 2 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | claw_hit_3, grab_1, slam_2 |
| `whoosh_triple` | [freesound.org/s/733891](https://freesound.org/s/733891/) | velcronator | Whoosh Triple | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | zip |
| `whip_crack` | [freesound.org/s/529925](https://freesound.org/s/529925/) | SciFiSounds | Whip Crack.m4a | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | lash_1 |
| `whip_crack2` | [freesound.org/s/615761](https://freesound.org/s/615761/) | JayRom01 | whip and crack sound | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | clash_1, lash_2 |
| `body_debris` | [freesound.org/s/461697](https://freesound.org/s/461697/) | leonelmail | Body falls into debris | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | clash_2, slam_1 |
| `body_carpet` | [freesound.org/s/119825](https://freesound.org/s/119825/) | kbnevel | BodyFall_Carpet.aif | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | slam_2 |
| `ice_crack` | [freesound.org/s/262635](https://freesound.org/s/262635/) | j_p_higgins | Ice Crack 1 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | shell |
| `leather` | [freesound.org/s/501015](https://freesound.org/s/501015/) | Crinkem | Leather Stress 1 (Short) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | grab_1, shell |
| `fabric_tear` | [freesound.org/s/565970](https://freesound.org/s/565970/) | melle_teich | Tearing fabric | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | web_tear_1 |
| `cloth_rip` | [freesound.org/s/591527](https://freesound.org/s/591527/) | Rudmer_Rotteveel | Cloth Rip Even Long | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | web_tear_2 |
| `slurp` | [freesound.org/s/568043](https://freesound.org/s/568043/) | benkenart | coffee slurp 5 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | web_eat |
| `gulp` | [freesound.org/s/531755](https://freesound.org/s/531755/) | magnuswaker | Gulp - Hard Swallow | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | engulf, web_eat |
| `heartbeat` | [freesound.org/s/43621](https://freesound.org/s/43621/) | kathol | real heartbeat.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | heartbeat_1, heartbeat_2 |
| `breath_heavy` | [freesound.org/s/422342](https://freesound.org/s/422342/) | BehanSean | heavy breathing.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | breath_1 |
| `breath_agitated` | [freesound.org/s/516696](https://freesound.org/s/516696/) | Amekiano | Respiración agitada (heavy breathing).wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | breath_2 |
| `whispers` | [freesound.org/s/193818](https://freesound.org/s/193818/) | geoneo0 | Four_Voices_Whispering_6_wEcho.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | whisper_1, whisper_2, whisper_3 |
| `suction_pop` | [freesound.org/s/523687](https://freesound.org/s/523687/) | JoBot__ | Suction Pop | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | release |
| `plunger` | [freesound.org/s/164684](https://freesound.org/s/164684/) | deleted_user_2104797 | Plunger_4.wav | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | release |

## Shipped files

| File (`sounds/venom/`) | Event | Purpose | Layers: source (start s, length s, pitch, gain dB) |
|---|---|---|---|
| `transform.ogg` | `venom.transform` | the symbiote covers the host, grows and roars (one file, 2.8 s) | `gel_hands` (0.4, 1.3, ×1, -1); `slime_squish` (0, 0.4, ×1, -3); `slime27` (0.07, 0.45, ×1, -2); `gel_cube` (0.03, 0.95, ×0.8, -4); `growl_hybrid` (0.04, 1.3, ×0.85, -3); `roar_monster` (0.06, 1, ×0.8, +0) |
| `revert.ogg` | `venom.revert` | the symbiote recedes into the host | `gel_hands` (3, 1.1, ×1, +0, reversed); `slime27` (0.07, 0.45, ×1, -3, reversed); `growl6` (0, 1.15, ×0.75, -9) |
| `roar_1.ogg` | `venom.roar` | the frenzy roar, the boss roar | `roar_scary` (0.04, 2.4, ×0.85, +0); `roar_short` (0.02, 1.27, ×0.7, -7) |
| `roar_2.ogg` | `venom.roar` | the frenzy roar, the boss roar | `roar_monster` (0.06, 1.6, ×0.8, +0); `growl_hybrid` (0.04, 1.5, ×0.75, -6) |
| `squirm_1.ogg` | `venom.squirm` | the living surface squirms (transformation stages, hunger, reactions) | `squelching` (0.09, 0.8, ×0.9, +0) |
| `squirm_2.ogg` | `venom.squirm` | the living surface squirms (transformation stages, hunger, reactions) | `squelching` (1.22, 0.75, ×0.85, +0) |
| `squirm_3.ogg` | `venom.squirm` | the living surface squirms (transformation stages, hunger, reactions) | `gel_hands` (2.42, 0.7, ×0.9, +0); `slime_squish` (0, 0.4, ×1, -6) |
| `reform_1.ogg` | `venom.reform` | the body pulls itself back together after a heavy hit | `slime27` (0.07, 0.6, ×1, +0, reversed); `slime_squish` (0, 0.42, ×1, -2) |
| `reform_2.ogg` | `venom.reform` | the body pulls itself back together after a heavy hit | `gel_hands` (5, 0.6, ×1, +0); `slime27` (0.07, 0.45, ×0.9, -3) |
| `shriek_1.ogg` | `venom.shriek` | the symbiote shrieks at fire and loud sound | `screech` (0.05, 1.55, ×0.9, +0) |
| `shriek_2.ogg` | `venom.shriek` | the symbiote shrieks at fire and loud sound | `screech` (0.05, 1.55, ×0.78, +0) |
| `claw_1.ogg` | `venom.claw` | claw swing | `staff_whoosh` (0.03, 0.27, ×0.85, +0) |
| `claw_2.ogg` | `venom.claw` | claw swing | `staff_whoosh` (0.03, 0.27, ×1, +0) |
| `claw_3.ogg` | `venom.claw` | claw swing | `swing_woosh` (0.03, 0.27, ×0.9, +0); `slime_squish` (0, 0.3, ×1, -16) |
| `claw_hit_1.ogg` | `venom.claw_hit` | claws tear into a target | `punch_impact` (0.32, 0.5, ×1, +0); `splat` (0, 0.38, ×1, -5) |
| `claw_hit_2.ogg` | `venom.claw_hit` | claws tear into a target | `major_punch` (0.08, 0.5, ×0.9, +0); `splat3` (0, 0.5, ×1, -6) |
| `claw_hit_3.ogg` | `venom.claw_hit` | claws tear into a target | `slug_splat` (0.03, 0.55, ×1, -2); `punch_impact` (0.32, 0.5, ×0.85, -3); `gore_squish` (0.95, 0.5, ×1, +4) |
| `tendril_shot_1.ogg` | `venom.tendril_shot` | a tendril shoots out | `rope_whoosh` (0.07, 0.3, ×1, +0); `splat` (0, 0.3, ×1, -12) |
| `tendril_shot_2.ogg` | `venom.tendril_shot` | a tendril shoots out | `whip_woosh` (0.02, 0.4, ×0.9, +0); `slime_squish` (0, 0.3, ×1, -12) |
| `tendril_stick_1.ogg` | `venom.tendril_stick` | the tendril sticks to a surface or a body | `splat` (0, 0.39, ×0.85, +0) |
| `tendril_stick_2.ogg` | `venom.tendril_stick` | the tendril sticks to a surface or a body | `splat3` (0, 0.55, ×1, +0); `slime_squish` (0, 0.3, ×0.8, -6) |
| `zip.ogg` | `venom.zip` | towed through the air by a tendril | `whoosh_triple` (0.01, 1.22, ×0.9, +0); `rope_whoosh` (0.07, 0.3, ×1, -4) |
| `lash_1.ogg` | `venom.lash` | a tentacle lash | `rope_whoosh` (0.07, 0.25, ×1, -4); `whip_crack` (0.06, 0.4, ×0.9, +0) |
| `lash_2.ogg` | `venom.lash` | a tentacle lash | `swing_woosh` (0.03, 0.2, ×0.8, -4); `whip_crack2` (0.04, 0.48, ×0.85, +0) |
| `slam_1.ogg` | `venom.slam` | a body slammed into the ground, the lash finisher, the boss leap | `body_debris` (0.15, 1.45, ×1, +0); `major_punch` (0.08, 0.6, ×0.6, -2) |
| `slam_2.ogg` | `venom.slam` | a body slammed into the ground, the lash finisher, the boss leap | `body_carpet` (0, 1.3, ×1, +0); `major_punch` (0.08, 0.6, ×0.55, -2); `slug_splat` (0.03, 0.5, ×1, -8) |
| `grab_1.ogg` | `venom.grab` | claws and goo seize a body | `slug_splat` (0.03, 0.55, ×1, +0); `leather` (0.42, 0.5, ×1, -6) |
| `grab_2.ogg` | `venom.grab` | claws and goo seize a body | `slime27` (0.07, 0.5, ×1, +0); `gel_hands` (6, 0.5, ×1, -4) |
| `throw_1.ogg` | `venom.throw` | a held body hurled away | `staff_whoosh` (0.03, 0.27, ×0.6, +0); `grunt_monster` (0.1, 0.4, ×1, -2) |
| `throw_2.ogg` | `venom.throw` | a held body hurled away | `swing_woosh` (0.03, 0.27, ×0.55, +0); `growl6` (0, 0.55, ×0.9, -6) |
| `shell.ogg` | `venom.shell` | the symbiote hardens into a shell | `ice_crack` (0.13, 0.6, ×0.7, +0); `leather` (0.42, 0.85, ×1, -3); `gel_hands` (8, 0.8, ×1, -6) |
| `web_tear_1.ogg` | `venom.web_tear` | the Spider's web torn apart | `fabric_tear` (0.06, 0.62, ×1, +0) |
| `web_tear_2.ogg` | `venom.web_tear` | the Spider's web torn apart | `cloth_rip` (0.02, 1, ×1.1, +0) |
| `web_eat.ogg` | `venom.web_eat` | the symbiote swallows a web strand | `slurp` (0.19, 1.05, ×0.8, +0); `gulp` (0, 0.52, ×0.8, -1) |
| `clash_1.ogg` | `venom.clash` | Venom and the Spider collide | `major_punch` (0.08, 0.6, ×1, +0); `punch_impact` (0.32, 0.5, ×0.8, -3); `whip_crack2` (0.04, 0.4, ×0.6, -10) |
| `clash_2.ogg` | `venom.clash` | Venom and the Spider collide | `punch_impact` (0.32, 0.5, ×0.9, +0); `body_debris` (0.5, 0.8, ×1, -4) |
| `engulf.ogg` | `venom.engulf` | the symbiote engulfs a host | `growl_hybrid` (0.04, 1.2, ×0.7, -6); `gel_hands` (9, 1.2, ×1, -1); `slime_squish` (0, 0.42, ×1, -2); `gulp` (0, 0.52, ×0.6, +0) |
| `heartbeat_1.ogg` | `venom.heartbeat` | the host's racing heartbeat (heard only by him) | `heartbeat` (0.8, 0.65, ×1, +0) |
| `heartbeat_2.ogg` | `venom.heartbeat` | the host's racing heartbeat (heard only by him) | `heartbeat` (2.15, 0.6, ×1, +0) |
| `breath_1.ogg` | `venom.breath` | the host's breathing, muffled (heard only by him) | `breath_heavy` (0.2, 1.8, ×0.88, +0) |
| `breath_2.ogg` | `venom.breath` | the host's breathing, muffled (heard only by him) | `breath_agitated` (0, 1.6, ×0.85, +0) |
| `whisper_1.ogg` | `venom.whisper` | the inner voices: reversed, pitched whispers, no words (heard only by the host) | `whispers` (1.6, 1.8, ×0.85, +0, reversed) |
| `whisper_2.ogg` | `venom.whisper` | the inner voices: reversed, pitched whispers, no words (heard only by the host) | `whispers` (4, 1.8, ×0.8, +0, reversed) |
| `whisper_3.ogg` | `venom.whisper` | the inner voices: reversed, pitched whispers, no words (heard only by the host) | `whispers` (6.4, 1.8, ×0.9, +0, reversed) |
| `release.ogg` | `venom.release` | the symbiote lets go of a host | `slime27` (0.07, 0.45, ×1, -2, reversed); `plunger` (0.14, 0.2, ×1, +0); `suction_pop` (0, 0.1, ×1, -4) |
| `refuse_1.ogg` | `venom.refuse` | an ability is refused (a short low grunt) | `grunt_monster` (0.08, 0.42, ×0.8, +0); `growl6` (0, 0.4, ×0.85, -10) |
| `refuse_2.ogg` | `venom.refuse` | an ability is refused (a short low grunt) | `growl_monster` (0.05, 0.6, ×0.8, +0) |
