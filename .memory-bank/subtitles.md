# Hero subtitles (speech) — working notes

- Text only: `HeroLine` has no sound; never describe a line as voiced. Report:
  `docs/reports/2026-10-31-global-subtitles-audit.md`, plan: `docs/plans/2026-10-28-hero-subtitles.md`.
- Server: `hero/speech/HeroSpeech` (one active line per speaker, audience = speaker + same-level
  players in range, STOP on death/logout/dimension/hero change/`activeWhile` false), pure rules in
  `HeroSpeechRules` (unit-tested), shared events in `HeroLineEvents` (KILL, HERO_ENTER, LOW_HEALTH).
- LOW_HEALTH reads the final damage: called from `HeroEvents.onLivingDamage` after
  `HeroDamageLayers.onDamage`; do not add a separate LivingDamageEvent subscriber (order race).
- Server lifetime of auto-timed lines = client page formula on the server's text (English on a
  dedicated server) × `ESTIMATE_TEXT_MARGIN` 1.5 (+20 fade); unknown key → 160/fragment. Expiry
  sends no STOP. Ambient lines are BUSY while another line lives; an ability line < 20 ticks old is
  not replaced.
- Client: `client/hero/speech/HeroSubtitles` + `SubtitleQueue` (one panel; same speaker or higher
  priority cuts). Letterboxes must draw in `RenderGuiEvent.Pre` so the panel stays on top.
- Real-game QA (opt-in, off by default): client `-Dviltrumitecore.subtitles.playtest=true`
  (+ `.playtest.world=true` for an auto SP world) reads `<gameDir>/subtitle_qa.txt`; server
  `-Dviltrumitecore.subtitles.qa=true` adds `/subtitleqa`. Logs: `SUBPT` (client), `SPEECHQA` (server).
- MP harness: start `runServer` (offline, RCON), launch extra clients with the runClient java
  command line + `--gameDir <dir> --username <name> --quickPlayMultiplayer localhost:25565`; set
  `MOD_CLASSES=viltrumitecore%%<classes>:viltrumitecore%%<resources>:viltrumiteflight%%…` or the
  flight mixin config is not found. `-Dfile.encoding=UTF-8` makes Russian readable in the logs.
