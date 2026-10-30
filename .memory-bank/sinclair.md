# Sinclair (topic file)

Durable notes for «Великолепный Синклер». Main memory: `.memory-bank/project.md`. Specs:
`docs/design/2026-10-28-sinclair-design.md` (hero), `docs/design/2026-11-02-sinclair-cinematic-polish.md`
(copy cinematic, four-part acts, sound rework). Core 1.55.0, protocol 14.

## Sound
- 45 events in `SinclairSounds`. The 31 original ones live in the shared `assets/viltrumitecore/sounds.json`
  (`viltrumitecore:sinclair.<name>`); the 14 added in 1.55.0 live in the own namespace
  `assets/viltrumitecore_sinclair/sounds.json` (`viltrumitecore_sinclair:sinclair.<name>`, registered with `stage(name)`).
  Reason: the shared sounds.json is above 64 KB and cannot be pushed through the GitHub connector.
- All files are `.ogg.b64` under `src/main/binassets/assets/viltrumitecore/sounds/sinclair/` (both namespaces point there).
- Generator: `tools/sfx/sinclair_cc0.py KENNEY_ROOT [--only NAMES] [--wav DIR] [--audit]` (Kenney CC0 layers + synthesis,
  mastering by loudness class). `tools/sfx/sinclair.py` is retired. Table of every cue: `tools/assets/sinclair_sources.md`.
- `SinclairSounds.location(name)` / `OWN_SOUNDS` feed the `SoundDuck` exemptions (StageLight hush, copy cinematic).

## Copy cinematic
- `CopyCinematic` (pure timeline, 60 t), `CopyScene` (server: ControlManager FREEZE on both, `mayStart` gate,
  idempotent `stop`), `SinclairMove.COPY`, client `client/sinclair/stage/*` (camera, card, overlay hide, duck).
- No new packets: the client reads move COPY + volunteer id from Sinclair's snapshot. `/sinclair cinematic on|off|status`
  (SavedData, permission 2) also gates the act camera (flag `SinclairFlags.STAGE_CAMERA`).
- Camera/mouse mixins `SinclairStageCameraMixin` / `SinclairStageMouseMixin` (priority 1900, no-op without a live scene).

## Acts
- `ActScript` has four parts (tension / main / climax / finish). Damage budgets are pinned by `ActScriptTest`; when a
  timeline changes, keep the per-act total (BEAM 9.6, CUT 8, BARRAGE 16.2, NOVA 7, AEGIS 4, FINALE 3×2.5).
- Clips come from `tools/assets/make_sinclair_clips.py` (`--check` in CI); lang from `tools/lang/sinclair_lang.py` (12 locales).
- FINALE is the spare act: the card shows the "spare act" ribbon, never another hero's ultimate.

## Playtest
- `client/sinclair/qa/SinclairPlaytest` (`-Dviltrumitecore.sinclair.playtest=true`, `.ultOnly=true` skips to the ultimate).
  Steps can wait on the synced move tick (`shotAt`, `shotAct`, `untilIdle`). See `docs/reports/sinclair-playtest/README.md`.
- First launch needs `:viltrumitecore:downloadAssets` (retry on read timeouts); `run/options.txt` with `onboardAccessibility:false`.

## Gotchas found in the client
- `GuiGraphics.fill`/`drawString` flush through `RenderType.gui()`, whose clear state disables blending: raw
  Tesselator quads drawn after them must call `RenderSystem.enableBlend()` again (the card halo drew opaque).
- A staged camera (`Camera#setup` mixin, `detached=true`) does not stop vanilla first-person hands: cancel
  `RenderHandEvent` while `StageCamera.staged()`.
- Damage on a FREEZE-held body is queued until release (shared control rule), so `/kill` cannot abort the copy
  scene; removal (logout, unload, dimension change) does.
