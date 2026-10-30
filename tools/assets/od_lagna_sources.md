# Od Lagna's Wish — asset sources

All files are own work released under CC0-1.0. Nothing was downloaded, recorded or traced.

| File (packaged) | Source | How |
|---|---|---|
| `binassets/assets/viltrumitecore/textures/item/od_lagna_wish.png.b64` | `tools/assets/make_od_lagna_assets.py` | 16x16 pixel art: a blue light orb held by a golden ring, two sparkles |
| `binassets/assets/viltrumitecore/textures/gui/od_lagna/halo.png.b64` | `tools/assets/make_od_lagna_assets.py` | 128x128 white radial alpha falloff, tinted gold in the GUI |
| `binassets/assets/od_lagna/sounds/call.ogg.b64` | `tools/sfx/od_lagna.py` | rising major-chord swell with slow vibrato and sparkles, peaking near the swap (2 s) |
| `binassets/assets/od_lagna/sounds/granted.ogg.b64` | `tools/sfx/od_lagna.py` | bright bell stroke with a low warm bloom, the swap |
| `binassets/assets/od_lagna/sounds/return.ogg.b64` | `tools/sfx/od_lagna.py` | warm chord settling down a step and fading like a breath, the return to a human |
| `binassets/assets/od_lagna/sounds/open.ogg.b64` | `tools/sfx/od_lagna.py` | airy upward arpeggio over soft breath noise, the screen opens |
| `binassets/assets/od_lagna/sounds/select.ogg.b64` | `tools/sfx/od_lagna.py` | small glass tick, a wish is selected |
| `binassets/assets/od_lagna/sounds/confirm.ogg.b64` | `tools/sfx/od_lagna.py` | three rising chimes, «исполнить желание» |

Rebuild: `python3 tools/sfx/od_lagna.py OUT_DIR` writes the WAV mixes; each is encoded mono 32 kHz Vorbis q2
(`ffmpeg -i X.wav -ac 1 -ar 32000 -c:a libvorbis -q:a 2 X.ogg`, mono so positional attenuation works) and stored
as base64 (`base64 -w 76`). Every packaged file stays below 60 KB so it fits one GitHub MCP request.

The sounds live in their own asset namespace `od_lagna` (`assets/od_lagna/sounds.json`), are played only on the
client (`WishSounds`) and are not registry entries, so the shared `viltrumitecore` sounds.json is untouched.
