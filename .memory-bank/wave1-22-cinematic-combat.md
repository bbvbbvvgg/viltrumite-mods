# Cinematic combat (wave 1, session 22)

- Shared camera shake lived in `client/render/vfx/CameraShake` (one static float, strongest wins, decay 0.86 per tick, applied in `ViewportEvent.ComputeCameraAngles`). About 30 call sites use it.
- Shared client hit-stop is `client/render/vfx/HitStop`. The server triggers it with `HeroFxS2CPacket.HIT_STOP` through `hero/fx/HeroFx`.
- `JinwooUltScene` already drives a client-only Marker camera. A cinematic API must not take over a camera that a hero scene already owns.
- Network protocol was "31" before this session.
