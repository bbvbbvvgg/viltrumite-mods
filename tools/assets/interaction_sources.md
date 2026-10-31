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

Animation: `shield_shattered` (third person `captain_skills`, first person `captain_fp`) — own work, CC0, keyed in
`tools/assets/captain_clips.py` / `tools/assets/captain_fp_clips.py`.

FX: `viltrumitecore:interaction_*` presets (net ids 600-607), layers in
`client/interaction/InteractionFxRecipes` — procedural, no textures.
