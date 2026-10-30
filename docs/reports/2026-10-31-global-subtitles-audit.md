# Global subtitles expansion — audit, fixes and real-game check (2026-10-31)

Branch `feat/global-subtitles-expansion` (draft PR #93 → `main`). The 14 original commits
(`… 7a9e8f9d`, 74 files) are kept unchanged; everything below sits on top of them. `main` was not
touched, nothing was rebased, merged or force-pushed.

## What the system is (and is not)

- **Text only.** Hero lines are subtitles: a translation key (or literal) per page, a speaker, a
  portrait, a priority and a duration. `HeroLine` has no sound field and nothing in the code, the
  lang files or the docs claims a line is voiced. Ability sounds play from their own code paths at
  the same server moment; a line is never "the audio" of a sound.
- Sounds that sit next to a line (checked by name in `sounds.json` and by playback in a real client):
  `dio.wryyy` (own synthesized SFX, CC0) under `WRYYYY!`, and Reinhard's
  `reinhard.world_cut_strike_voice` under `reinhard:world_cut_blast`. The latter is a **pre-existing
  TEMPORARY asset from `main`** (anime clip, licence NOT verified, see `tools/assets/reinhard_sources.md`);
  the subtitle `Reid — cleave the world.` is **not** a transcript of it. Not changed here.
- No new audio, no vanilla placeholder sounds, no fake sound links were added.
- The overlay folder `lang-overlays/voice_lines/` is only a name: it holds text strings (renaming
  it would change the overlay merge order, so it stays).

## Pipeline checked

event (server, accepted action) → `HeroSpeech` decision (`SPEECHQA` log) → `HeroLineS2CPacket`
to the audience (speaker + players in the same level within range) → client `SubtitleQueue` →
`HeroSubtitles` draw (page, portrait, page ticks; `SUBPT` log) → STOP on death, logout, dimension
change, hero change, cancelled channel (`activeWhile`), replacement.

## Fixes on top of the 14 commits

| Commit | Fix |
|---|---|
| `3ce109d8` | Low-health remark now reads the final damage after `HeroDamageLayers` clamps it (called from `HeroEvents.onLivingDamage`; a separate LOWEST subscriber raced the clamp). Ambient remarks are `BUSY` under any line (`HeroSpeechRulesTest` failed). |
| `19b1665e`, `6492e128` | `tools/lang/sinclair_lang.py` carries the 6 new Sinclair rows (`--check`: no stale keys); `SinclairLangTest` 137 → 143 keys; idiomatic Russian Regulus `ult_flick`. |
| `a846bf26` | **Server lifetime of auto-timed lines.** It was 160 ticks per fragment + 20: a 2 s quip (`thor:throw`, "Мьёльнир!") held the speaker for 9 s, so a kill right after it was skipped as `BUSY` with nothing on screen (real client: `thor:kill_a -> BUSY (active thor:throw)` 115 ticks after the throw). Now the estimate mirrors the client's page time for the text the server reads (English on a dedicated server, verified: `dio:time_stop` lifetime 85 ticks) × 1.5 for longer translations / wrapped pages; unknown keys keep 160. Expiry never sends STOP, so clients still time their own pages. |
| `6623d6b6` | **Road Roller letterbox hid the subtitle panel.** The panel moves into the lower bar for the scene, but `RollerCinema` filled the bars in `RenderGuiEvent.Post`, over every overlay, cutting the lower half of the panel and the portrait. Now `Pre`, like the Jinwoo and Regulus letterboxes (screenshot before/after in the PR). |
| `cb00684b`, `57441260`, `372e3c5a`, `a846bf26`, `6e6719d2` | Opt-in QA tooling (off unless the JVM flags are set): client `SubtitlePlaytest` (`-Dviltrumitecore.subtitles.playtest=true`; `subtitle_qa.txt` commands: `press`, `key …/swap`, `cmd`, `shot`, `cam`, `look`, `leave`, `world`, `button`, `respawn`, `state`; logs `SUBPT` recv/draw/sound) and server `/subtitleqa` (`-Dviltrumitecore.subtitles.qa=true`: `hero`, `prep`, `victim`, `kill`, `lowhp`, `active`; `SPEECHQA` decision log). |

## Locales

12 locales (`de_de, en_us, es_ar, es_cl, es_ec, es_es, es_mx, es_uy, es_ve, ru_ru, tr_tr, zh_cn`):
every file parses, no duplicate keys, no subtitle key missing in any locale (base + overlays).
Values identical to English are names / interjections only (`The World!`, `WRYYYY!`, speaker names).
Russian checked in game: every line drawn in Russian, no raw keys. Key labels on the HUD (R, Y, Z,
V, B, N, ЛКМ/ПКМ) are the player's bindings, ability names stay translated as before.

## Real game (Forge 1.20.1 dev client, Xvfb + Mesa software GL, `ru_ru`)

Singleplayer, real key presses through the ability key mappings; line vs. sound ticks from `SUBPT`:

- **Thor**: enter, Wrath (`thor:wrath` on the windup with `wrath_windup` in the same tick,
  `thor:wrath_strike` with `thunder_rumble` in the same tick), throw, kill (after the fix), low health.
- **DIO**: enter, summon, barrage → finisher (the barrage chant stops when the barrage ends), knives,
  time stop → resume, **Road Roller**: four pages each in the tick of its sound (`roller_appear`,
  `roller_slam`, `wryyy`).
- **Sung Jinwoo**: enter, mutilation, Arise (2 pages, page 2 on the `ult_roar` tick), letterbox ok.
- **Reinhard**: enter, Holy Sword mode, Reid draw, saw sphere, **World Cut** (2 pages, then the blast
  line 5 ticks before the strike sound).
- **Regulus**: enter, Lion's Heart, seizure, sever, trample, **Kingdom of One** (accuse 50 →
  sentence 56 → flick 44 ticks, each with its phase sound).
- **Sinclair**: enter, bolt, assistant, hex, misdirection, **Audience Participation** capture and
  "window lost".
- **Iron Man**: enter, nano suit, unibeam. **Homelander**: enter, kill.
- 3D portraits drawn for every speaker; panels never overlap (one panel, queue).
- Stress: rapid presses (an ability line under 20 ticks old is not replaced: `BUSY`, by design),
  hero switch mid-Road-Roller (STOP, no later roller pages, new greeting), `/kill` mid-ultimate
  (STOP, death screen without a panel, respawn clean), leaving the world mid-line and re-entering
  (nothing stale, server `active []`).

Multiplayer: dedicated server (`runServer`, offline mode, RCON) + two real clients (P1, P2, own game
dirs and displays):

- Every line reaches both clients exactly once, with the right `own` flag and name.
- Range: P2 300 blocks away gets nothing (`audience=1`); back in range gets everything.
- Simultaneous ultimates (P1 time stop + P2 Wrath in the same tick): both decided `PLAY` on the
  server; each client shows one panel, the second waits in the queue. Thor's strike line comes after
  the time stop ends, when the frozen windup really strikes.
- Disconnect mid-line: P2 gets `STOP` for P1's line in the next tick; rejoin is clean.
- Death mid-ultimate and quick hero switches: `STOP` + new line on both clients.

## Not covered / limitations

- No listening test: the sandbox has no speakers; sound timing was checked by playback events only.
- Not exercised in game: Homelander overheat, Iron Man missiles / Veronica / Hulkbuster
  (`hulk_sleep`, `hulk_ram`), Mark suit-up delivery, Sinclair acts (beam, nova, cut, barrage, aegis,
  finale) and bow, Reinhard diagonal cut / World Erasure / phoenix, Jinwoo extract fail / riposte,
  Regulus king / reply / host lost / sand. They are covered by unit tests (`AllHeroLinesTest`) and
  the call-site review only.
- The client queue shows one panel: when two speakers talk at once the second line waits (up to its
  priority's max wait) and can start after its moment — design of the queue, unchanged.
- A player who slows subtitles below 1/1.5 speed can see an ambient remark replace a still-visible
  line of the same speaker (the server estimate covers ×1.5).
- Software rendering at 15–30 FPS: draws lag the packet by 1–9 ticks in these runs.
- Unrelated observations, left alone: a Thor hammer loop sound kept playing after switching away
  from Thor; Regulus' ult HUD label is clipped at the right edge at 854×480.
