# Contextual interactions — sources of the assets

Everything here is **derived from the mod's own CC0 sound bank**: no new recordings, no film audio. Each cue is a
deterministic ffmpeg mix (delay, gain, pitch, trim, limiter; 44.1 kHz mono Vorbis q5) of existing one-shots of the
participants, so a combo sounds like both heroes at once. Rebuild: `python3 tools/sfx/interactions.py [names...]`.

| Event | File | Layers (source → ledger) | Licence |
|---|---|---|---|
| `interaction.shield_shatter` | `sounds/interaction/shield_shatter` | `reinhard/reid_heavy_1`, `reinhard/erase_shatter` (reinhard_sources.md), `captain/block`, `captain/parry` (captain_sources.md) | CC0-1.0 (own synthesis + CC0 1.0 Freesound layers listed in reinhard_sources.md) |
| `interaction.storm_shield` | `sounds/interaction/storm_shield` | `thor/hammer_metal_1`, `thor/thunder_impact_1`, `thor/electric_wave`, `thor/hammer_bass_1` (thor sound tables), `captain/parry` | CC0-1.0 (own synthesis) |
| `interaction.meteor_burst` | `sounds/interaction/meteor_burst` | `captain/slam_impact`, `captain/block`, `thor/hammer_earth_1`, `thor/thunder_rumble_1` | CC0-1.0 (own synthesis) |
| `interaction.meteor_split` | `sounds/interaction/meteor_split` | `thor/thunder_crack_1`, `thor/hammer_earth_2`, `thor/electric_zap_1`, `thor/thunder_rumble_2` | CC0-1.0 (own synthesis) |
| `interaction.overcharge` | `sounds/interaction/overcharge` | `thor/electric_zap_2`, `thor/electric_tail`, `ironman/repulsor_charge` (ironman_sources.md: CC0 1.0 Freesound 162242, 824121) | CC0-1.0 |
| `interaction.divine_clash` | `sounds/interaction/divine_clash` | `reinhard/reid_hit_1` (reinhard_sources.md), `thor/hammer_metal_2`, `thor/electric_zap_3`, `thor/thunder_crack_2` | CC0-1.0 |
| `interaction.titan_fling` | `sounds/interaction/titan_fling` | `thor/hammer_body_1`, `thor/hammer_earth_1`, `thor/dash` | CC0-1.0 (own synthesis) |
| `interaction.storm_rebound` | `sounds/interaction/storm_rebound` | `thor/hammer_metal_3`, `captain/parry`, `thor/thunder_crack_3`, `thor/dash` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.vibranium_ring` | `sounds/interaction/vibranium_ring` | `thor/hammer_body_2`, `captain/block`, `captain/slam_impact`, `captain/parry` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.repulsor_split` | `sounds/interaction/repulsor_split` | `ironman/repulsor_shot`, `captain/parry`, `ironman/repulsor_volley`, `thor/electric_zap_1` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.time_rewind` | `sounds/interaction/time_rewind` | `thanos/time_rewind`, `dio/time_tick`, `dio/ledger_burst`, `thanos/stone_light` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.lion_strain` | `sounds/interaction/lion_strain` | `reinhard/reid_hit_2`, `reinhard/erase_shatter`, `regulus/impact_heavy1`, `dio/time_tick` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 / public domain (Regulus layer: regulus_sources.md) |
| `interaction.shield_crush` | `sounds/interaction/shield_crush` | `thanos/hit`, `thanos/slam`, `captain/block`, `captain/slam_impact` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.meteor_cleave` | `sounds/interaction/meteor_cleave` | `reinhard/reid_heavy_2`, `reinhard/saw_shatter`, `thor/hammer_earth_2`, `thor/hammer_earth_1` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.meteor_hurl` | `sounds/interaction/meteor_hurl` | `captain/slam_impact`, `thor/hammer_body_1`, `thor/dash`, `thor/thunder_rumble_1` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.shadow_exchange` | `sounds/interaction/shadow_exchange` | `jinwoo/exchange`, `captain/slam_impact`, `jinwoo/shadow_dissolve`, `chitauri/explosion` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.leviathan_impact` | `sounds/interaction/leviathan_impact` | `chitauri/explosion_2`, `chitauri/leviathan_hit`, `chitauri/leviathan_roar`, `chitauri/debris` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.legion_discharge` | `sounds/interaction/legion_discharge` | `thor/electric_zap_3`, `ironman/repulsor_charge`, `thor/electric_wave`, `ironman/repulsor_shot` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.spine_lightning` | `sounds/interaction/spine_lightning` | `thor/thunder_crack_2`, `thor/electric_wave`, `chitauri/leviathan_hit`, `thor/electric_tail` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |
| `interaction.leviathan_halt` | `sounds/interaction/leviathan_halt` | `ironman/hulkbuster/finish_punch`, `chitauri/leviathan_smash`, `chitauri/leviathan_hit`, `chitauri/debris_2` (hero ledgers in `tools/assets/*_sources.md`) | CC0-1.0 (derived from the mod's CC0 bank) |

Animation: `shield_shattered` (third person `captain_skills`, first person `captain_fp`) — own work, CC0, keyed in
`tools/assets/captain_clips.py` / `tools/assets/captain_fp_clips.py`.

FX: `viltrumitecore:interaction_*` presets (net ids 600-621), layers in
`client/interaction/InteractionFxRecipes` — procedural, no textures.
