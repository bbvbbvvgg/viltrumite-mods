# Regulus Reborn — sound sources and cue sheet

33 events, 50 files, mono 44.1 kHz Vorbis stored as base64 under
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/regulus/reborn/`. Two families:

- **Stopped time** (14 events: Lion on/off/deny, projectile stop, strain, grain stop, severance charge, Right of Reply
  stance, Little King, refuse, ult accuse/resolve) — **original procedural audio** by `tools/sfx/regulus_reborn.py`
  (seeded numpy synthesis: partials, filtered noise, envelopes), CC0 1.0 by the project. Swells that cut to silence,
  glass and crystal resonances.
- **Bodies and matter** (19 events: steps, Greed's Touch swings and hits, recoil, rupture, the heartbeat, sand,
  severance release and cut, Trample, the ult waves, scoop and flick) — **mixed from real recordings** by
  `tools/sfx/regulus_samples.py`: CC0 / public-domain sources (table below) trimmed, varispeed-pitched, band-limited and
  layered, with the synthesised cue of the same name as a quiet top layer so both families match. Each mix is
  level-matched (loudest 100 ms above 120 Hz) to the synthesised cue it replaced, so the kit's balance stays where it was.

Regenerate: `python3 tools/sfx/regulus_samples.py --fetch` (sources into `~/.cache/viltrumite-sfx/regulus`, Openverse
files through the sound-design skill helper), then `python3 tools/sfx/regulus_samples.py` and
`python3 tools/sfx/regulus_reborn.py` (`--wav DIR` previews, `--check` compares with the committed files, `--json`
rewrites `viltrumitecore/src/main/sound-overlays/regulus.json`). Raw downloads are never committed.

Not auditioned in game (no audio device on the build machine): levels are measured, not listened to; balance against
other heroes still needs a listening pass. The first kit's `regulus_*` events stay in `sounds.json` (Reinhard reuses some).

## Recordings

| Key | Origin | Title / file | Creator | Licence | Page |
|---|---|---|---|---|---|
| `punch_h0` | Kenney pack `impact-sounds` | impactPunch_heavy_000.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `punch_h1` | Kenney pack `impact-sounds` | impactPunch_heavy_001.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `punch_h2` | Kenney pack `impact-sounds` | impactPunch_heavy_002.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `punch_h4` | Kenney pack `impact-sounds` | impactPunch_heavy_004.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `punch_m2` | Kenney pack `impact-sounds` | impactPunch_medium_002.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `soft_h0` | Kenney pack `impact-sounds` | impactSoft_heavy_000.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `soft_h1` | Kenney pack `impact-sounds` | impactSoft_heavy_001.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `soft_h2` | Kenney pack `impact-sounds` | impactSoft_heavy_002.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `soft_h3` | Kenney pack `impact-sounds` | impactSoft_heavy_003.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `soft_h4` | Kenney pack `impact-sounds` | impactSoft_heavy_004.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `mining0` | Kenney pack `impact-sounds` | impactMining_000.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `mining1` | Kenney pack `impact-sounds` | impactMining_001.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `mining2` | Kenney pack `impact-sounds` | impactMining_002.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `glass_l0` | Kenney pack `impact-sounds` | impactGlass_light_000.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `glass_l1` | Kenney pack `impact-sounds` | impactGlass_light_001.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `glass_l2` | Kenney pack `impact-sounds` | impactGlass_light_002.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `generic_l0` | Kenney pack `impact-sounds` | impactGeneric_light_000.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `generic_l1` | Kenney pack `impact-sounds` | impactGeneric_light_001.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `generic_l2` | Kenney pack `impact-sounds` | impactGeneric_light_002.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/impact-sounds |
| `cloth1` | Kenney pack `rpg-audio` | cloth1.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/rpg-audio |
| `cloth2` | Kenney pack `rpg-audio` | cloth2.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/rpg-audio |
| `step0` | Kenney pack `rpg-audio` | footstep00.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/rpg-audio |
| `step1` | Kenney pack `rpg-audio` | footstep01.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/rpg-audio |
| `step3` | Kenney pack `rpg-audio` | footstep03.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/rpg-audio |
| `step6` | Kenney pack `rpg-audio` | footstep06.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/rpg-audio |
| `knife1` | Kenney pack `rpg-audio` | knifeSlice.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/rpg-audio |
| `knife2` | Kenney pack `rpg-audio` | knifeSlice2.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/rpg-audio |
| `chop` | Kenney pack `rpg-audio` | chop.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/rpg-audio |
| `lowboom0` | Kenney pack `sci-fi-sounds` | lowFrequency_explosion_000.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/sci-fi-sounds |
| `lowboom1` | Kenney pack `sci-fi-sounds` | lowFrequency_explosion_001.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/sci-fi-sounds |
| `crunch0` | Kenney pack `sci-fi-sounds` | explosionCrunch_000.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/sci-fi-sounds |
| `crunch1` | Kenney pack `sci-fi-sounds` | explosionCrunch_001.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/sci-fi-sounds |
| `crunch2` | Kenney pack `sci-fi-sounds` | explosionCrunch_002.ogg | Kenney (kenney.nl) | CC0 1.0 | https://kenney.nl/assets/sci-fi-sounds |
| `heart` | Wikimedia Commons | Human heart rate.flac | Wilfredor | CC0 1.0 | https://commons.wikimedia.org/wiki/File%3AHuman_heart_rate.flac |
| `gravel` | Wikimedia Commons | Assorted gravel rock and stones.ogg | stephan (pdsounds.org) | Public domain | https://commons.wikimedia.org/wiki/File%3AAssorted_gravel_rock_and_stones.ogg |
| `thud` | Wikimedia Commons | Dull thud.ogg | gregoryweir (pdsounds.org) | Public domain | https://commons.wikimedia.org/wiki/File%3ADull_thud.ogg |
| `quake` | Wikimedia Commons | Earthquake.ogg | igel457 (pdsounds.org) | Public domain | https://commons.wikimedia.org/wiki/File%3AEarthquake.ogg |
| `firework` | Wikimedia Commons | Explosion-LS100155.ogg | Fg2 | Public domain | https://commons.wikimedia.org/wiki/File%3AExplosion-LS100155.ogg |
| `fast_swing` | Freesound via Openverse (`518ead89-8e89-4614-b954-bb7a844f3c42`) | Fast Sword Swing Sound | qubodup | CC0 1.0 | https://freesound.org/s/60004/ |
| `light_swoosh` | Freesound via Openverse (`d343ce9e-01df-4852-b625-3ac3eb805cdd`) | FX - Swoosh - Light | bolkmar | CC0 1.0 | https://freesound.org/s/475132/ |
| `deep_whoosh` | Freesound via Openverse (`eab8a6e2-0ac8-4615-9e0d-ebde30523783`) | Deep Whoosh #1 | Kinoton | CC0 1.0 | https://freesound.org/s/351256/ |

Kenney packs: "Impact Sounds", "RPG Audio", "Sci-fi Sounds" (kenney.nl, CC0 1.0, `License.txt` in each pack). The
Commons files marked "public domain" are pdsounds.org transfers or own work released into the public domain by the
recordist (`Earthquake.ogg` is itself synthesised by its author); `Human heart rate.flac` is CC0 own work. The three
Freesound files were checked CC0 1.0 on their Freesound pages; Openverse attribution lines, verbatim:
"Fast Sword Swing Sound" by qubodup is marked with CC0 1.0. / "FX - Swoosh - Light" by bolkmar is marked with CC0 1.0. /
"Deep Whoosh #1" by Kinoton is marked with CC0 1.0.

## Cue sheet

| Event | Trigger | Files (variants) | Range (blocks) | Made from |
|---|---|---|---|---|
| `regulus.lion_on` | Lion's Heart on (protection frame of `lion_on`) | lion_on | 32 | synthesis |
| `regulus.lion_off` | Lion's Heart released | lion_off | 24 | synthesis |
| `regulus.lion_deny` | Lion pressed while locked / on cooldown | lion_deny | 12 | synthesis |
| `regulus.recoil` | a melee hit stopped by the Lion, recoil returned; Right of Reply refusal | recoil0, recoil1, recoil2 | 24 | recordings: `soft_h0`, `soft_h1`, `soft_h2` + synth layer |
| `regulus.projectile_stop` | a projectile stopped dead in front of the Lion | projectile_stop | 24 | synthesis |
| `regulus.heartbeat` | Little King: his heart beating inside the host | heartbeat0, heartbeat1 | 16 | recordings: `heart` + synth layer |
| `regulus.strain` | hostless Lion strain tick (every second, louder with strain); host lost | strain | 16 | synthesis |
| `regulus.seizure` | heart seizure at full strain | seizure | 24 | recordings: `heart` + synth layer |
| `regulus.touch_swish` | Greed's Touch swing (3 steps) | touch_swish0, touch_swish1, touch_swish2 | 16 | recordings: `fast_swing`, `light_swoosh` + synth layer |
| `regulus.touch_hit` | Greed's Touch contact | touch_hit0, touch_hit1, touch_hit2 | 24 | recordings: `punch_h0`, `punch_h1`, `punch_h2` + synth layer |
| `regulus.rupture` | delayed rupture of a touched target | rupture | 24 | recordings: `chop`, `crunch0`, `punch_m2` + synth layer |
| `regulus.sand_scoop` | Stopped Sand scoop | sand_scoop | 16 | recordings: `cloth2`, `gravel` + synth layer |
| `regulus.sand_release` | Stopped Sand release (fan of grains) | sand_release | 32 | recordings: `deep_whoosh`, `gravel` + synth layer |
| `regulus.grain_hit` | a stopped grain hits an entity | grain_hit0, grain_hit1, grain_hit2 | 24 | recordings: `generic_l0`, `generic_l1`, `generic_l2`, `glass_l0`, `glass_l1`, `glass_l2` + synth layer |
| `regulus.grain_stop` | a grain stops against a block or at the end of its flight | grain_stop0, grain_stop1, grain_stop2 | 16 | synthesis |
| `regulus.sever_charge` | Air Severance wind-up | sever_charge | 16 | synthesis |
| `regulus.sever_release` | Air Severance release | sever_release | 32 | recordings: `deep_whoosh`, `fast_swing` + synth layer |
| `regulus.sever_cut` | the blade cuts an entity / block line | sever_cut0, sever_cut1 | 24 | recordings: `knife1`, `knife2` + synth layer |
| `regulus.trample_lift` | Trample: the knee comes up (press) | trample_lift | 16 | recordings: `cloth1`, `light_swoosh` |
| `regulus.trample_stomp` | Trample: the heel lands (impact tick 8), crater + ring start | trample_stomp | 40 | recordings: `gravel`, `lowboom0`, `mining0`, `thud` + synth layer |
| `regulus.trample_wave` | Trample: the ring reaches an entity | trample_wave0, trample_wave1 | 24 | recordings: `deep_whoosh`, `soft_h3`, `soft_h4` + synth layer |
| `regulus.reply_stance` | Right of Reply stance | reply_stance | 16 | synthesis |
| `regulus.reply_strike` | Right of Reply strike | reply_strike | 24 | recordings: `lowboom1`, `punch_h4` + synth layer |
| `regulus.king_bind` | Little King binds a subject | king_bind | 16 | synthesis |
| `regulus.king_hop` | the heart hops to the next subject | king_hop | 16 | synthesis |
| `regulus.king_release` | a subject is released | king_release | 16 | synthesis |
| `regulus.refuse` | a refused input (the short "no" gesture) | refuse | 8 | synthesis |
| `regulus.ult_accuse` | Kingdom of One: accusation (ACCUSE phase) | ult_accuse | 48 | synthesis |
| `regulus.ult_scoop` | Kingdom of One: dome scooped (SCOOP) | ult_scoop | 48 | recordings: `deep_whoosh`, `quake` + synth layer |
| `regulus.ult_wave` | Kingdom of One: each of the five sentence waves | ult_wave0, ult_wave1 | 40 | recordings: `crunch1`, `crunch2`, `mining1`, `mining2` + synth layer |
| `regulus.ult_flick` | Kingdom of One: the flick contact | ult_flick | 64 | recordings: `firework`, `lowboom1` + synth layer |
| `regulus.ult_resolve` | Kingdom of One: resolve / crater | ult_resolve | 32 | synthesis |
| `regulus.step` | footfall of a stopped body while the Lion holds | step0, step1, step2, step3 | 12 | recordings: `step0`, `step1`, `step3`, `step6` + synth layer |
