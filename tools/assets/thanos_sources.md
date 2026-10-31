# Thanos event — asset sources

Every Thanos file is own work released under CC0-1.0 (drawn, modelled, animated or synthesised by the
scripts below; nothing was downloaded, traced or recorded). The one derivative is Od Lagna's scene skin,
a recolour of Reinhard's skin from the user archive (see the last table row).

| Committed file | Source | How |
|---|---|---|
| `viltrumitecore/src/main/resources/assets/viltrumitecore/geo/thanos/thanos.geo.json` | `tools/assets/thanos_model.py` | 38 bones / 101 cubes, 60 px tall (3.75 blocks): MCU-style Thanos, left Infinity Gauntlet with two-segment fingers and thumb, six stone bones (`stone_time` on the thumb, `stone_space` index, `stone_reality` middle, `stone_power` ring, `stone_soul` little finger, `stone_mind` back of the hand), each a socket + two crossed gem cubes + two glow-only halo shells |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/thanos/thanos.png.b64` | `tools/assets/thanos_model.py` | procedural pixel-art atlas 256×512 (2 texels per model px), stored as an indexed PNG (256 colours, alpha kept) |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/thanos/thanos_glow.png.b64` | `tools/assets/thanos_model.py` | additive glow layer: irises and the six gems/halos (premultiplied, black = no glow) |
| `viltrumitecore/src/main/resources/assets/viltrumitecore/animations/thanos/thanos_{base,melee,stones,combos,scene}.animation.json` | `tools/assets/thanos_clips.py` | 34 keyframed clips (idle, walk, turns, every move, hurt, stun, intro, victory, snap, snap_hold, defeat); move clips use the base telegraph/active/recovery ticks of `ThanosMove`, scene clips the beats of `ThanosSceneTimeline` |
| `viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/thanos/{od_lagna_scene,od_lagna_scene_glow}.png.b64` | `tools/assets/thanos_od_lagna.py` from `textures/entity/reinhard/{reinhard,reinhard_glow}.png.b64` (grebeshok105, user archive `reinhard_assets.zip`, see `reinhard_sources.md`) | luminance remap onto a white-gold ramp (hair pale gold, eyes amber), soft self-light glow; same 128×128 UV layout, so the Reinhard geo is reused unchanged; Reinhard's own textures are untouched |

Sounds: see the «Sounds» section below (added with `tools/sfx/thanos.py`).

Previews (not committed): `python3 tools/assets/thanos_preview.py rest|head|hand|clip NAME [frames] [yaw]` →
`build/thanos-assets/`.
