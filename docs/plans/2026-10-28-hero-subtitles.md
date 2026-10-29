# Cinematic hero subtitles (core 1.50.0)

Goal: one shared system for spoken hero lines shown as a two-panel cinematic subtitle above the hotbar
(left: speaker name + text, right: live 3D bust of the speaker). First user: Reinhard's ultimate, World Cut.
Branch `feat/hero-subtitles` from `main` (core 1.45.0).

## Checklist
- [x] Common model: `hero/speech/HeroLine` (id, fragments, speaker key, priority, duration, cooldown, range,
      `activeWhile`), `HeroLineTrigger` (ability id or named event), `HeroLines` (per-hero table),
      `HeroDefinition.lines()` (default `HeroLines.NONE`).
- [x] Pure rules `HeroSpeechRules` (play / replace / already active / cooldown / outranked, lifetime, variants).
- [x] Server `HeroSpeech`: one active line per speaker, audience = speaker + players in the same level within range,
      server-END validation sends STOP on logout, death, respawn, spectator, dimension change, hero change,
      `activeWhile` false; logout clears cooldowns; server stop clears all.
- [x] Packet `HeroLineS2CPacket` (PLAY / STOP / CLEAR, bounded fields). Protocol 7 → 11.
- [x] Client: `SubtitleLayout` (pure geometry), `SubtitleTiming` (pure timing), `SubtitleQueue` (pure concurrency),
      `SubtitleRenderer` (panel art), `HeroPortraits` (+ `PlayerBustPortrait`, `ReinhardPortrait`),
      `HeroSubtitles` (receive, clock, overlay below chat).
- [x] Reinhard: `ReinhardLines.WORLD_CUT` bound to the World Cut ability; spoken from `ReinhardActions.press`
      when the cast is accepted.
- [x] Client config: `heroSubtitles` (on/off), `heroSubtitleDurationScale` (0.5–2.5).
- [x] Op command `/herolines play <line> [speaker] | preview short|long | say <text> | stop`.
- [x] Lang: subtitle keys in all locales; new full `ru_ru.json` (all 509 keys, Russian is the reference language of the lines).
- [x] Unit tests: rules, line table, packet round trip/caps, layout, timing, queue.

## How to add a line to a hero
1. Add text keys to every lang file: `subtitle.viltrumitecore.<hero>.<line>` (one fragment) or
   `subtitle.viltrumitecore.<hero>.<scene>.1..N` (long scene; each fragment is a page).
   The name comes from `subtitle.viltrumitecore.speaker.<hero>` (add it once per hero).
2. Declare the line in `hero/<id>/<Id>Lines`:
   `HeroLine.builder("<id>:<line>").text(key)` or `.textSeries(prefix, N)`, optional `.priority(...)`,
   `.duration(ticks)` (0 = by text length), `.cooldown(ticks)`, `.range(blocks)`, `.activeWhile(predicate)`.
3. Bind it: `HeroLines.builder().onAbility(abilityId, line)` or `.onEvent("<id>:<event>", line)`; return the table
   from `<Id>Hero.lines()`.
4. Speak it on the server at the moment the action is accepted (never per tick):
   `HeroSpeech.ability(player, abilityId)` or `HeroSpeech.trigger(player, HeroLineTrigger.event(key))`.
   Stop it early with `HeroSpeech.interrupt(player, lineId)` when the action is cancelled.
5. Portrait: nothing to do for a player-model hero (skin + outer layer, slim/wide). A hero with a custom model
   registers `HeroPortraits.register(HeroId.<ID>, portrait)` in `<Id>Client.init()` (see `ReinhardPortrait`).

Reinhard's full monologue later = one `textSeries` line with N fragments bound to an event; no code changes in the
shared system.

## Rules
- One subtitle on screen. Panels never overlap: a new line waits (≤ 3 queued) or forces a short exit (6 ticks) of the
  current one when it comes from the same speaker or has a higher priority (AMBIENT < ABILITY < ULTIMATE).
  The local player's own lines are queued first. Stale queued lines are dropped (60/120/160 ticks by priority);
  identical lines are deduplicated.
- Server: same line again while active → ALREADY_ACTIVE (no resend); a cooldown (default 40 ticks) blocks spam;
  a lower priority never replaces a higher one.
- Duration: `clamp((18 + 1.15 · chars) · scale, 40, 240)` ticks per page; an explicit duration is spread over pages
  (≥ 30 ticks each). Enter 10, exit 10, page cross-fade 5 out + 7 in, slide 6 px, drift 2 px.
- The clock runs on client ticks while the game is not paused; the subtitle is cleared on world change and logout,
  the own line stops on death/respawn, a line from a dead speaker stops.
- Never blocks input or the camera: it is a HUD overlay registered below the chat panel.

## Geometry (GUI pixels, measured on the reference at GUI scale 2)
- Text panel: padding left 9, right 35 (min 10), name → body 14, line pitch 11, ≤ 3 lines per page, preferred wrap
  214, min width 150. Height `max(52, 31 + 11 · lines)`.
- Portrait panel: same height, width `height · 75 / 64`, gap 5 between panels, screen margin 6.
- The panel size is fixed per line (largest page), so pages never resize the box.
- Pages: whole sentences are packed into ≤ 3 lines; a longer sentence is cut between clauses (after , ; : — – or
  CJK punctuation) into the fewest, most even pages. Every page uses an even wrap (same line count as the greedy
  wrap, narrowest width), so a narrow screen never leaves one word on its own line. Authored `\n` breaks are kept
  while every authored line fits; once one has to be rewrapped they turn into spaces.
- Bottom edge sits above the hotbar, the status bars (Forge `leftHeight/rightHeight`), the action-bar message and the
  held-item name; the empty Forge armor row counts as the gap (like the reference), the move is smoothed.
- When the hero has the right-side ability bar, the layout room ends left of it (slot 24 + key label + 6), so the
  panel shrinks and shifts instead of covering the bar.
- Art: outer #0C0D10, border #8E9398 / shade #757A7F with cut corners, fill gradient #25272B → #18191C at 74–78 %
  alpha, inner shadow, 6×6 corner ornaments. Name #A6AAAF uppercase, text #F2F2F2 with shadow, vanilla font
  (Cyrillic included).
- Portrait: real model in a scissor clip, own key/fill lights, depth pre-pass for fades, yaw −32°, pitch 15°.

## Not covered
- Voice audio (text only).
