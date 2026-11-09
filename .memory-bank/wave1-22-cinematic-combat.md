# Cinematic combat (wave 1, session 22)

- Shared camera shake lived in `client/render/vfx/CameraShake` (one static float, strongest wins, decay 0.86 per tick, applied in `ViewportEvent.ComputeCameraAngles`). About 30 call sites use it.
- Shared client hit-stop is `client/render/vfx/HitStop`. The server triggers it with `HeroFxS2CPacket.HIT_STOP` through `hero/fx/HeroFx`.
- `JinwooUltScene` already drives a client-only Marker camera. A cinematic API must not take over a camera that a hero scene already owns.
- Network protocol was "31" before this session.

## VM log
- The VM can be fully reset (3x so far, /data included). The GitHub branch is the source of truth: push right after writing.
- Recovery: Drive kit -> sha256sum -c -> 7za x -> overlay branch files via get_file_contents -> ~/kit/run.sh.
- Slice 1: compile green. Full build fixes: system-catalog section 9 uniforms, post pass sets FeelWaveA/B, the wave early-out term sits before the Venom terms, roster packet order (v32).
