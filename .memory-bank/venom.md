# Venom — the living symbiote (core 1.71.0, protocol 30, branch feat/venom-living-symbiote)

Spec: `docs/design/2026-11-02-venom-design.md`. Falbiks report: `docs/venom/spiderman-compatibility.md`.
Audio licences: `docs/venom/audio-sources.md`.

- Code: `hero/venom/*` (server: `VenomHero`, `VenomSkills`, `VenomCombat`, `VenomPossession`, `VenomWeakness`,
  `VenomRules` = every number), `hero/venom/boss/*` (`VenomBossEntity` adapter + pure `VenomBossBrain`),
  `client/venom/*` (renderer, coat layer, poser, tendrils, crawl, host FX/HUD), `compat/spider/*` (SpiderCompat).
- Keys R/Y/Z/V/B/N = symbiosis, tendril, tentacle_lash, symbiote_shell, feeding_frenzy, host_takeover.
  `HeroId.VENOM` and `HeroAction.VENOM_*` are appended (ordinals of older heroes unchanged).
- Assets: 47 own `.ogg` in `binassets/assets/viltrumitecore/sounds/venom/*.ogg.b64`, 25 events in
  `src/main/sound-overlays/venom.json`, generator `tools/sfx/venom.py`; textures in binassets (entity/venom with
  coat_0..16, gui/venom, gui/ability/venom); geo + animations in resources (`tools/assets/venom_*.py`);
  lang in `src/main/lang-overlays/venom/*.json` (12 locales, English key names). Never replace with vanilla sounds.
- Invariants: a takeover lives in memory only (never NBT); every exit goes through `VenomPossession.end`
  (13 `End` reasons) which restores control, removes `PossessionInputGate`, sends the client reset (uniforms 0,
  keys released) and gives the host 3 s of safety + 60 s takeover immunity. Player takeover needs server PvP AND
  `venomPossessionEnabled`; boss takeover needs `venomBossPossession`.
- Boss: 300 HP, phases HUNT → FRENZY (≤ 2/3) → STARVING (≤ 1/3), monotonic, one roar per border, restored on
  reload; threat scores with half-life 200 t, adapt at 12 (melee, ranged, fire, sound = hunts the noisemaker,
  height = pounce from the wall, kite). Tests: `VenomBossBrainTest`.
- Weaknesses: fire ×1.75 + 5 s no regen + recoil; loud sound (volume ≥ 2 within 10) recoil; hellfire scalds
  (`InteractionReaction.SYMBIOTE_SCALDED`). Fire and sound always work, also on the adapted boss. The recoil line
  names the cause: `venom:recoil` for fire, `venom:recoil_sound` for a bell/blast.
- Falbiks Heroes: reflection only through `SpiderCompat`, each `SpiderFeature` binds or disables itself with a
  logged reason (`/venom compat`); no hard dependency, none of their code or assets. Signature test:
  `./gradlew :viltrumitecore:test --tests '*FalbiksJarSignatureTest*' -PfalbiksJar=<path>`.
- Gotchas: tests rewrite the tracked `viltrumitecore/logs/*.log.gz` — never commit them.
- Release window (`HOST_SAFE_TICKS` 60): environment AND the releasing symbiote cannot hurt the host
  (`VenomPossession.sparedFrom`); the boss turns to another player or backs off (`VenomBossBrain.spit/sparing`).
  The host's resist HUD lifts over the voice subtitle panel (`VenomHostFx.resistY` + `HeroSubtitles.panelTop`);
  the husk's name tag is hidden for host and possessor only.
- Hero choice in game: Od Lagna's Wish (`viltrumitecore:od_lagna_wish`, RMB) → «я желаю больше никогда не быть
  одному…»; `/viltrumite choose` no longer opens a screen (by design). `/venom boss` refuses on Peaceful.
- Real QA (2026-11-02, Atheria): `docs/venom/playtest-2026-11-02.md` — singleplayer + dedicated server with
  2 clients (one with real X input). QA helpers: harness `venomstate`; `/subtitleqa lowhp|prep|hero`.
  Without the harness, keys need the window focused ~1 s before the press (`isWindowActive`).
