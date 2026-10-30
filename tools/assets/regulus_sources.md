# Regulus Reborn — sound sources and cue sheet

All 32 Regulus Reborn events (50 files) are **original procedural audio** made by
`tools/sfx/regulus_reborn.py`: seeded numpy synthesis (sines, partials, filtered noise, envelopes), mono 44.1 kHz,
encoded with ffmpeg libvorbis (bitexact), stored as base64 under
`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/regulus/reborn/`. No recordings, no downloads, no
third-party samples. License: **CC0 1.0** (dedicated to the public domain by the project).

- Regenerate: `python3 tools/sfx/regulus_reborn.py` (`--wav DIR` writes WAV previews, `--json` rewrites the events in
  `viltrumitecore/src/main/sound-overlays/regulus.json`, `--check` fails if a committed file differs from the synthesis).
- Sound language: stopped time *stops* — swells that cut to silence, glass and crystal resonances, the heartbeat;
  impacts dry and close. Never thunder, never fire.
- Not auditioned in game (no audio device on the build machine); loudness and balance against other heroes still need
  a listening pass.
- The first kit's `regulus_*` events and files stay in `sounds.json` because Reinhard reuses some of them.

| Event | Trigger | Files (variants) | Range (blocks) |
|---|---|---|---|
| `regulus.lion_on` | Lion's Heart on (protection frame of `lion_on`) | lion_on | 32 |
| `regulus.lion_off` | Lion's Heart released | lion_off | 24 |
| `regulus.lion_deny` | Lion pressed while locked / on cooldown | lion_deny | 12 |
| `regulus.recoil` | a melee hit stopped by the Lion, recoil returned; Right of Reply refusal | recoil0, recoil1, recoil2 | 24 |
| `regulus.projectile_stop` | a projectile stopped dead in front of the Lion | projectile_stop | 24 |
| `regulus.heartbeat` | Little King: his heart beating inside the host | heartbeat0, heartbeat1 | 16 |
| `regulus.strain` | hostless Lion strain tick (every second, louder with strain); host lost | strain | 16 |
| `regulus.seizure` | heart seizure at full strain | seizure | 24 |
| `regulus.touch_swish` | Greed's Touch swing (3 steps) | touch_swish0, touch_swish1, touch_swish2 | 16 |
| `regulus.touch_hit` | Greed's Touch contact | touch_hit0, touch_hit1, touch_hit2 | 24 |
| `regulus.rupture` | delayed rupture of a touched target | rupture | 24 |
| `regulus.sand_scoop` | Stopped Sand scoop | sand_scoop | 16 |
| `regulus.sand_release` | Stopped Sand release (fan of grains) | sand_release | 32 |
| `regulus.grain_hit` | a stopped grain hits an entity | grain_hit0, grain_hit1, grain_hit2 | 24 |
| `regulus.grain_stop` | a grain stops against a block or at the end of its flight | grain_stop0, grain_stop1, grain_stop2 | 16 |
| `regulus.sever_charge` | Air Severance wind-up | sever_charge | 16 |
| `regulus.sever_release` | Air Severance release | sever_release | 32 |
| `regulus.sever_cut` | the blade cuts an entity / block line | sever_cut0, sever_cut1 | 24 |
| `regulus.procession_step` | Procession footfall | procession_step0, procession_step1, procession_step2 | 24 |
| `regulus.procession_ram` | Procession ram contact | procession_ram | 32 |
| `regulus.reply_stance` | Right of Reply stance | reply_stance | 16 |
| `regulus.reply_strike` | Right of Reply strike | reply_strike | 24 |
| `regulus.king_bind` | Little King binds a subject | king_bind | 16 |
| `regulus.king_hop` | the heart hops to the next subject | king_hop | 16 |
| `regulus.king_release` | a subject is released | king_release | 16 |
| `regulus.refuse` | a refused input (the short "no" gesture) | refuse | 8 |
| `regulus.ult_accuse` | Kingdom of One: accusation (ACCUSE phase) | ult_accuse | 48 |
| `regulus.ult_scoop` | Kingdom of One: dome scooped (SCOOP) | ult_scoop | 48 |
| `regulus.ult_wave` | Kingdom of One: each of the five sentence waves | ult_wave0, ult_wave1 | 40 |
| `regulus.ult_flick` | Kingdom of One: the flick contact | ult_flick | 64 |
| `regulus.ult_resolve` | Kingdom of One: resolve / crater | ult_resolve | 32 |
| `regulus.step` | footfall of a stopped body while the Lion holds | step0, step1, step2, step3 | 12 |
