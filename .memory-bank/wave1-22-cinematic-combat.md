# Cinematic combat (wave 1, session 22)

- Shared camera shake lived in `client/render/vfx/CameraShake` (one static float, strongest wins, decay 0.86 per tick, applied in `ViewportEvent.ComputeCameraAngles`). About 30 call sites use it.
- Shared client hit-stop is `client/render/vfx/HitStop`. The server triggers it with `HeroFxS2CPacket.HIT_STOP` through `hero/fx/HeroFx`.
- `JinwooUltScene` already drives a client-only Marker camera. A cinematic API must not take over a camera that a hero scene already owns.
- Network protocol was "31" before this session.

## VM log
- The VM can be fully reset (3x so far, /data included). The GitHub branch is the source of truth: push right after writing.
- Recovery: Drive kit -> sha256sum -c -> 7za x -> overlay branch files via get_file_contents -> ~/kit/run.sh.
- Slice 1: compile green. Full build fixes: system-catalog section 9 uniforms, post pass sets FeelWaveA/B, the wave early-out term sits before the Venom terms, roster packet order (v32).

## Аудит среза 1 (2026-11-25)

Audited revision: branch `wave1/22-cinematic-combat` at `704db00a` (base `main` `bc758de0`, draft PR #171, base `main`). Read-only code audit; no code changes. Derived per-hero data comes from reading the sources at that revision.

### A. Slice 1 element by element — live / dead / stub

- `combat/feel/CombatFeel.java` — **complete but dead.** Zero call sites in `viltrumitecore` / `viltrumiteflight` main code; the only references are its own javadoc and `combat/feel/*` tests. Consequence: nothing in the game ever fires the weighted hit-stop, the manga impact frame or the weight ladder ring.
- `combat/feel/HitWeight.java` — enum LIGHT/MEDIUM/HEAVY/CRUSHING/FINISHER, hit-stop 1/2/4/6/8, impact frame only CRUSHING+FINISHER, ring from HEAVY. Sole consumer is `CombatFeel.hit`. Dead at runtime.
- `combat/feel/ShakeProfile.java` — 7 profiles, ordinal = wire id, `byId` falls back to LIGHT_HIT. Consumers: `CombatFeel`, `FeelS2CPacket` decode. Dead until a hero calls.
- `combat/feel/ShakeMixer.java` — pure mixer (merge one layer per profile, 8 layers soft-limited by tanh, golden-angle phase). Sole consumer `CameraFeel`. Nothing adds a layer at runtime, so it always reports 0 layers. Dead.
- `combat/feel/FeelMath.java` — pure math, unit tested (`FeelMathTest`): falloff, ring radius/strength, impact phases, cooldown. Live (as a library).
- `client/cinematic/feel/CameraFeel.java` — **live for the legacy path, dead for the new one.** It is the single `ComputeCameraAngles` owner now (the old `CameraShake.onCameraAngles` was removed). The legacy offsets of every old caller still apply, scaled by `reduceCameraMotion`. The profiled `ShakeMixer` branch runs only when a layer exists — never at runtime.
- `client/cinematic/feel/ClientFeel.java` — implemented; only driven by `FeelS2CPacket`. Dead.
- `client/cinematic/feel/ImpactFrame.java` — implemented (invert 50 ms + lines 115 ms, 400 ms cooldown, `FlashOverlay` fallback when `impactFrames` off). Only triggered from `ClientFeel`. Dead.
- `client/cinematic/feel/ShockwaveDistortion.java` — implemented; rings ride the shared `dash_impact` pass through `FeelWaveA`/`FeelWaveB` (no second post chain). Wiring verified in the mixin, the program JSON and the fragment shader. No producer → `post()` returns zeros every frame. Dead.
- `client/render/vfx/CameraShake.java` — **live.** Refactored to `legacyOffsets(partialTick, out)`; the old formula and the 0.86/tick decay are byte-for-byte preserved; ~30 call sites unchanged.
- `network/packet/FeelS2CPacket.java` — implemented, clamped on both ends (NaN/inf/negative → 0, caps 4/256/200), round-trip tested. Registered last; no sender.
- `network/CoreMessages.java` — `PROTOCOL_VERSION` 31 → 32; `FeelS2CPacket` appended after `Scp106S2CPacket`. Live.
- Shaders — `assets/minecraft/shaders/program/dash_impact.fsh` + `.json` and `assets/viltrumitecore/shaders/post/dash_impact.json` carry `FeelWaveA/B`; `feelWave()` refraction; the new early-out term sits before the Venom terms. Live wiring, no producer.
- `client/cinematic/CinematicSettings.java` — file `config/viltrumitecore_cinematic.json` with 5 fields. Only `reduceCameraMotion`, `impactFrames`, `flashIntensity` have readers. `disableCinematicCamera` and `watchOthersCinematics` have **zero readers**. `load()` is not called from `ViltrumiteCoreClient.onClientSetup` (the other three configs are); the file appears lazily once any shake fires. No settings screen exists.
- Tests — `FeelMathTest`, `ShakeMixerTest`, `FeelWiringTest` (source-string guards: packet appended last, legacy formula kept, rings in the shared pass). No test asserts that a hero calls `CombatFeel`; no `CinematicSettings` test. `HeroRosterIntegrationTest` updated (v32 + `FeelS2CPacket` append-order assert).
- Camera API of the design (`Cinematics`, `CameraPath`, `CinematicCamera`, `assets/<ns>/cinematics/<id>.json`) — **absent**: no such classes, no `cinematics/` asset dir, no JSON loader. Slice 2+.

### B. Hero x feel API

No hero, mob or world event calls `CombatFeel` (verified by grep). The table shows, per hero, where damage lands and which legacy feel channel it feeds today.

| Hero | Damage seam (file:line) | HeroFx hit-stop | Own client shake | CombatFeel |
|---|---|---|---|---|
| HUMAN / VILTRUMITE | none (no kit) | — | — | no |
| HOMELANDER | `homelander/EyeLasers.java:81`; `homelander/Roar.java:86` | none | none of its own; Roar shakes via `HeroFx.shockwave` (`Roar.java:74` → `HeroImpactFx`) | no |
| REGULUS | `regulus/RegulusCombat.java:91` (`hit()`), used by GreedsTouch/Trample/AirSeverance/KingdomOfOne/LionsHeart… | none | `client/regulus/RegulusFx.java` (`addAt`/`cinematicAt`, lines 52,59,77,100,112,118,127,149,155,159,165) | no |
| IRON_MAN | `ironman/IronManCombat.java` (repulsor, unibeam, blade, hammer, slam); `FlyBy.java:42`; `SonicRam.java:45`; `entity/RepulsorBlastEntity.java:107` | only Hulkbuster (`ironman/hulkbuster/HulkbusterCombat.java:344,486,645,717`, `HulkbusterUltimate.java:60,107`) | `client/ironman/IronManCombatVfx.java:92`, `client/ironman/hulkbuster/HulkbusterVfx.java` (5), `HulkbusterShells.java` (2) | no |
| REINHARD | `reinhard/ability/Strikes.java:21` (`hit()`), FinalPunch, WorldCut/DiagonalWorldCut, SawSphere | yes — `melee/MeleeImpact.java:42` (LightPunch/ReidMelee), `FistCombo.java:160`, `GrabThrow.java:305`, `SkyKick.java:104`, `FinalPunch.java:277` (6) | `client/reinhard/cut/CutAtmosphere.java:54`, `cut/WorldCutScene.java:141,144`, `finalpunch/FinalPunchVfx.java:227`, `reid/ReidDrawSceneVfx.java:172`, `saw/SawSphereClient.java:270` | no |
| THOR | `thor/combat/CombatHits.java:261` (`hit()`), `WrathMove.java:235` (ult), `ElectricCombos/ElectricMoves`, `hammer/MjolnirImpact.java:131`, `MjolnirEntity.java:708` | yes — `combat/MeleeMoves.java:342` (4/2), `WrathMove.java:243` (5), `ThorGuard.java:186,192`, `scene/SceneFx.java:104,117,169,175,187` | only `client/thor/ThorStormVfx.java:107,143,179` (storm/build-up); plain hits do not shake | no |
| DIO | `dio/DioCombat.java:229` (`strike()`), `DioAbilitiesRuntime.java` (drain/roller), `DioTimeStop.java:350`, `DioKnifeEntity.java:175` | **none** | `client/dio/DioFxClient.java:193,210,222,243,263,271,290,305,311,316`, `client/dio/RoadRollerScene.java:112` | no |
| SINCLAIR | `sinclair/SinclairCombat.java:72,82` (projectile/trick), `act/ActScene.java:182,243,292,391,453`, `entity/*` projectiles | **none** | `client/sinclair/StageLight.java:90` (act beats), `HeroFx.flash` (`SinclairCombat.java:121`) | no |
| JINWOO | `jinwoo/JinwooCombat.java:48` (`strike()`), `:191` dive, `JinwooSkills.java` (step/rush/authority/charge), `MonarchArise.java:129` (ult wave) | yes — `JinwooCombat.java:167` (2), `JinwooSkills.java:141` (3), `:429` (2) | `client/jinwoo/JinwooVfx.java:223,261,296`, `client/jinwoo/JinwooUltScene.java:120` (ult camera) | no |
| CAPTAIN | `captain/CaptainCombat.java:144,177`, `CaptainSkills.java:233,297`, `CaptainTargets.java:131` | yes — `CaptainCombat.java:188`, `CaptainSkills.java:252` | `client/captain/CaptainVfx.java:121,134,307,313,320,325` | no |
| RED_HULK | `redhulk/RedHulkTargets.java:124` (`strike()`), `RedHulkCombat.java:87,93,112,117,212,255,290`, `RedHulkSkills.java:220,250,280,325,355` | yes — `RedHulkCombat.java:129`, `RedHulkSkills.java:238,250,280,335` | via recipes `client/redhulk/RedHulkFxRecipes.java` → `HeroImpactFx` | no |
| GHOST_RIDER | `ghostrider/GhostRiderCombat.java` (punch/lash/hook), `GhostRiderSkills.java` (dash/whirl/slam/wave/verdict), `Hellfire.java:126` | **none** | via `GhostRiderFxRecipes` (shake recipes) | no |
| VENOM | `venom/VenomCombat.java:87,~270,~285,~310,~325`, `VenomThrows.java:106`, `VenomSkills.java:342,354,367`, `VenomSpider.java:104`, boss `boss/VenomBossEntity.java` (claw/impale/slam/roar/spike/bite) | **none** | via `client/venom/VenomFxRecipes.java` (23–31) | no |
| THANOS / Black Order | `ThanosTargets.hurt` (`ThanosTargets.java:75`); `stone/*.java`; `war/ThanosWarMoves.java:356,444,483,539,614,708`; `entity/thanos/*` | none | `client/thanos/ThanosFx.java` (~10 `addAt`), `ThanosWarFx.java` (~20) | no |
| GODZILLA | `GodzillaActions.java:190,265,148,168,231,245`, `GodzillaBreath.java:133,160` (`hurtSphere`) | none | `client/godzilla/GodzillaClient.java:115,144,160,169,180,198,212,232,265` | no |
| SCP-096 | `scp/scp096/Scp096Chase.java:1133` (`strike()`) — only entity attack; carve/plow hit blocks | none | none (`client/scp096` has no `CameraShake`) | no |
| CHITAURI | soldiers/commander `doHurtTarget`; `ChitauriHeavy` slam; `ChitauriLeviathan` bite/crash; `ChitauriBolt.onHitEntity`; `ChitauriDestruction.blastEntities` | none (attackers are mobs, not `ServerPlayer`) | `client/chitauri/ChitauriVfx.java:287` (leviathan roar), 336 | no |

Natural insertion seams for `CombatFeel.hit`: `thor/combat/CombatHits.java:261`, `reinhard/ability/Strikes.java:21`, `redhulk/RedHulkTargets.java:124`, `captain/CaptainTargets.java:131`, `jinwoo/JinwooCombat.java:48`, `venom/VenomTargets.java`, `ghostrider/GhostRiderTargets.java`, `regulus/RegulusCombat.java:91`, `ThanosTargets.java:75`, `GodzillaActions.hurtSphere`, `Scp096Chase.strike`.

### C. Design features present / absent

| Feature | State |
|---|---|
| Hit-stop by weight (`HitWeight`) | code present, unused (CombatFeel) |
| Shake by weight (`ShakeMixer`) | code present, unused |
| Impact frame | code present, unused |
| Air distortion rings | code present, unused (shared `dash_impact` pass) |
| `FeelS2CPacket`, protocol 32 | present, unused sender |
| Cinematic camera (`Cinematics.play`, `CinematicCamera`) | absent |
| Letterbox | only inside `JinwooUltScene` (hero-owned), not from the API |
| Visual slow-mo | absent (server never slows; no client clock/dunk wired) |
| Scene skip (Backspace) | absent |
| JSON scenes `assets/<ns>/cinematics/<id>.json` | absent (no dir, no loader) |
| Settings: reduce motion | present + enforced |
| Settings: disable cinematic camera | field present, **no reader** |
| Settings: watch others' scenes | field present, **no reader** |
| Settings screen | absent |
| Finishers (G, HUD hint, C2S) | absent |
| ru/en localization of new keys | absent (no cinematic lang keys) |
| Tests | 3 unit/wiring test classes for slice 1 only |

### D. Code vs design discrepancies (code wins)

1. Design §3 names a dedicated post shader `cinematic_shockwave` for the rings. The code uses the shared `dash_impact` uniforms `FeelWaveA/B` (the "Решения по слою 1" section already says so, and `FeelWiringTest` asserts no second chain). §3 is stale.
2. The slice-1 claim "каждый удар будет чувствоваться" is not true in the code: no hero calls `CombatFeel`, so no new feel fires. Slice 1 shipped the API and its plumbing, not the gameplay.
3. Design §4 (camera, letterbox, slow-mo, skip, JSON scenes) and §6 (finishers) and §7 (settings screen) are unimplemented — expected, they are slices 2-4.
4. Design §9 lists slice 5 as "wave distortion", but the rings already shipped in slice 1. The slice list needs a refresh.
5. Design §6 sets the finisher default key to `G`. `G` is already bound in `AbilityInputManager.registerKeys` to `key.viltrumitecore.regulus_super_jump` (default `GLFW_KEY_G`). Direct conflict; the finisher needs another default or a rebind decision.
6. `CinematicSettings.load()` is missing from `ViltrumiteCoreClient.onClientSetup` (unlike the other three configs). Harmless today (lazy `get()`), but a settings screen will need it deterministic.

### E. Extension points for the rest of the wave

- Client config load: `client/ViltrumiteCoreClient.onClientSetup` (~line 100) — add `CinematicSettings.load()` beside `ViltrumiteCameraConfig.load()` / `ViltrumitePostProcessingConfig.load()` / `ViltrumiteClientConfig.load()`.
- Settings screen: buttons are built in `client/gui/ViltrumiteConfigScreen` (camera / post_process / client / flight_*); add a "cinematic" button + a new `client/gui/config/CinematicConfigScreen` (copy `PostProcessConfigScreen`). Reached from the dashboard (`P`) → `ViltrumiteDashboardScreen` → `ViltrumiteConfigScreen`. There is no `OptionsScreen` mixin in the repo, so "Special abilities" means this screen.
- New keys: `client/AbilityInputManager.registerKeys` (`RegisterKeyMappingsEvent`), category `category.viltrumitecore.keys`. Skip (Backspace) and Finisher keys go here; mind the `G` conflict.
- Networking: append new packets after `FeelS2CPacket` with `id()` in `network/CoreMessages.register`; bump `PROTOCOL_VERSION` (32 → 33) at the top of the class. `HeroRosterIntegrationTest.wireOrdinalsAreFrozenAndNewHeroesAppendInMergeOrder` must be updated: the `PROTOCOL_VERSION` string and the trailing `packets.subList(punch + 16, packets.size())` assertion.
- Shared cinematic helpers that already exist: `client/render/vfx/SoundDuck`, `client/render/vfx/SkyTint`, `client/hero/CinematicHud`; `JinwooUltScene` and `client/dio/RollerCamera`, `client/thor/scene/ThorSceneCamera`, `client/reinhard/finalpunch/FinalPunchCamera`, `client/ghostrider/GhostRiderCamera` are the existing own-camera owners the API must not fight.
- Lang: 12 locales in `viltrumitecore/src/main/resources/assets/viltrumitecore/lang/` (`de_de, en_us, es_ar, es_cl, es_ec, es_es, es_mx, es_uy, es_ve, ru_ru, tr_tr, zh_cn`); overlay pattern `src/main/lang-overlays/<part>/<locale>.json` merged by `build.gradle` `processResources` (~line 128). Update all locales together (AGENTS §8).
- Shader uniforms: any new uniform must also update `.agents/skills/animation-system/references/system-catalog.md` §9 (`ScreenEffectCatalogTest` guards it); `FeelWaveA/B` are already listed.
- Cinematic assets: create `viltrumitecore/src/main/resources/assets/viltrumitecore/cinematics/<id>.json` (dir absent); load with a client `ReloadListener` registered in `ViltrumiteCoreClient.onRegisterReloadListeners` (pattern `IronManSuitTextures.INSTANCE`).

### F. Remaining work as ~300-line tasks (one measurable result each)

Order: T1 and T2 are independent; T3 needs T2; T4 needs T1+T3; T5 needs T3+T4; T6 needs T1+T3.

- **T1 — adopt `CombatFeel` in the damage seams (server, no camera).** Result: each of Thor, Reinhard, Red Hulk, Captain, Jinwoo, Venom, Ghost Rider, Regulus calls `CombatFeel.hit(attacker, target, weight)` at its single damage seam with a mapped `HitWeight`; a new `FeelCallersTest` asserts one call per hero at `704db00a`+ files. Files: `hero/thor/combat/CombatHits.java`, `hero/reinhard/ability/Strikes.java`, `hero/redhulk/RedHulkTargets.java`, `hero/captain/CaptainTargets.java`, `hero/jinwoo/JinwooCombat.java`, `hero/venom/VenomTargets.java`, `hero/ghostrider/GhostRiderTargets.java`, `hero/regulus/RegulusCombat.java`; test `combat/feel/FeelCallersTest.java`. Parallel-safe: hero files are disjoint from client files.
- **T2 — client comfort settings done end to end.** Result: `CinematicSettings.load()` runs in `onClientSetup`; a `CinematicConfigScreen` edits all 5 fields and saves; `disableCinematicCamera`/`watchOthersCinematics` get real readers (`enabledCamera()`, `watchOthers()`); round-trip test green. Files: `client/cinematic/CinematicSettings.java`, `client/ViltrumiteCoreClient.java`, `client/gui/ViltrumiteConfigScreen.java`, `client/gui/config/CinematicConfigScreen.java` (new), lang ×12, test.
- **T3 — cinematic camera core.** Result: `Cinematics.play(caster, scene, focus)` sends a new packet; client `CinematicCamera` takes the camera via a `Marker`, plays a one-shot path, returns control on finish / skip (Backspace keybind) / death / dimension change / menu; respects T2 settings. Files: `combat/feel/Cinematics.java` (new), `network/packet/CinematicS2CPacket.java` (new), `network/CoreMessages.java`, `client/cinematic/CinematicCamera.java` (new), `client/AbilityInputManager.java`, `HeroRosterIntegrationTest.java`, lang ×12.
- **T4 — JSON scenes + letterbox + slow-mo + skip hint.** Result: `CameraPath`/`CinematicScene` model + a client `ReloadListener` loads `assets/<ns>/cinematics/<id>.json`; letterbox bars, `SoundDuck` slow-mo and a skip hint draw; one demo scene ships and loads. Files: `client/cinematic/CameraPath.java`, `CinematicScene.java`, `CinematicScenes.java` (new), `client/cinematic/CinematicHud.java` (new), `resources/assets/viltrumitecore/cinematics/*.json`, `ViltrumiteCoreClient.java`.
- **T5 — showcase: 4 ultimates.** Result: Homelander Roar, Thor `WRATH` (`WrathMove`), Reinhard `WORLD_CUT`, Jinwoo `MonarchArise` each call `Cinematics.play` once at start; Jinwoo keeps its own camera (API adds only the feel layer); a test asserts exactly one call per ultimate. Files: `hero/homelander/Roar.java`, `hero/thor/combat/WrathMove.java`, `hero/reinhard/ability/WorldCut.java`, `hero/jinwoo/MonarchArise.java`, 4 scene JSONs, test.
- **T6 — finishers.** Result: `G` (rebound, not the conflicting default) + `[G] Добить` HUD hint; C2S validated on the server (range ≤ 4.5, health ≤ max(4, 20 %), bosses ≤ 8 %, PvP rule); 2 shared + 2 hero scenes. Files: `combat/finisher/*` (new), a C2S packet in `network/CoreMessages.java`, `client/cinematic/finisher/*` (new), hud, lang ×12, test.
- **T7 — wave-distortion polish + legacy clean-up.** Result: rings reachable from at least one shipped hero (via T1/T5), on-screen check by screenshot; `CameraShake` legacy path documented as the compatibility shim. Files: `client/cinematic/feel/ShockwaveDistortion.java`, `GameRendererDashMixin.java` (only if tuning), `system-catalog.md` §9 if uniforms change.

### G. Source-of-truth notes

- The mines above are grep-verified at `704db00a`: `CombatFeel` has no call site; `CinematicSettings.disableCinematicCamera`/`watchOthersCinematics` have no reader; `assets/viltrumitecore/cinematics/` does not exist; no `Cinematics`/`CameraPath`/`CinematicCamera` class exists.
- Slice 1 is a plumbing slice. It is green and shippable, but it changes no gameplay until T1/T5 wire `CombatFeel`/`Cinematics`.

## T2 — настройки комфорта (2026-11-25)

- `CinematicSettings.load()` теперь вызывается в `ViltrumiteCoreClient.onClientSetup` рядом с остальными тремя конфигами: файл `config/viltrumitecore_cinematic.json` создаётся детерминированно при старте, а не лениво при первом толчке камеры.
- Публичные читатели: `cinematicCameraEnabled()` (= !disableCinematicCamera), `watchOthers()` (= watchOthersCinematics), `reducedMotion()`, `impactFramesEnabled()`, плюс существующие `motionScale()`, `flash()`. Код кинокамеры слоя 2 зовёт их вместо прямого чтения полей.
- Читатель камеры называется `cinematicCameraEnabled()` (в плане аудита — `enabledCamera()`; прав код).
- Сериализация вынесена в чистые `CinematicSettings.read(String)` / `write()` (Gson), чтобы юнит-тесту не требовались Minecraft-классы и CONFIGDIR. `load()`/`save()` работают через них же.
- Экран `client/gui/config/CinematicConfigScreen` (образец — `PostProcessConfigScreen` + toggle-стиль `ClientConfigScreen`) правит все 5 полей: 4 переключателя ON/OFF и слайдер яркости 0..1; кнопка «Сохранить и назад» пишет файл. Вход — кнопка `gui.viltrumitecore.config.cinematic` в `ViltrumiteConfigScreen` (рядом с flight_camera).
- Локализация: 7 ключей `gui.viltrumitecore.config.cinematic*` во всех 12 локалях `assets/viltrumitecore/lang/`; ru_ru/en_us — вручную, остальные переведены. Overlay не понадобился (ru_ru 62 KB; lang-overlays для этого ключа не нужен).
- Тест `client/cinematic/CinematicSettingsTest`: round-trip всех полей, мусор/`null`/`{}` → дефолты без исключения, отсутствующие поля → дефолты, неизвестные поля игнорируются, зажим `flash()` в 0..1, геттеры совпадают с полями.
- Слой 2 (`Cinematics`/`CinematicCamera`) отсутствует: `cinematicCameraEnabled()`/`watchOthers()` пока без читателей — держим API готовым, как требует план.

## T1a — подключение CombatFeel (2026-11-25)

- `CombatFeel.hit(...)` перестал быть мёртвым: у каждого из 8 героев вызов стоит в единственной точке урона (единый helper, через который проходят все их удары).
- Вес: чистый `HitWeight.forDamage(damage)` (пороги MEDIUM 5 / HEAVY 10 / CRUSHING 20) даёт обычным ударам LIGHT/MEDIUM/HEAVY; ульты переопределены явным `HitWeight.CRUSHING`.
- Точки врезки: Thor — `hero/thor/combat/CombatHits.hit` (WRATH→CRUSHING, иначе forDamage); Reinhard — `hero/reinhard/ability/Strikes.hit` + первый корпус LMB `LightPunch` (идёт через `player.attack()`, шов не задевает); Red Hulk — `hero/redhulk/RedHulkTargets.strike`; Captain — `hero/captain/CaptainTargets.strike`; Jinwoo — `hero/jinwoo/JinwooCombat.strike` (MonarchArise и rush/charge-финишёры → CRUSHING); Venom — `hero/venom/VenomTargets.strike`; Ghost Rider — `hero/ghostrider/GhostRiderTargets.strike`; Regulus — `hero/regulus/RegulusCombat.hit` (KingdomOfOne ULT_FLICK → CRUSHING).
- Хит-стоп — ровно один источник на удар:
  - **CombatFeel владеет** у Red Hulk, Captain, Jinwoo, Regulus. У Red Hulk, Captain, Jinwoo срезанные дублирующие `HeroFx.hitStop` (combo step≥3 / shield-4&counter&rush / combo-5&rush-finish&charge-finish) удалены — серверный тайминг от них не зависел. У Red Hulk дополнительно пресетные стопы CLAP/SEISMIC/LEAP_LAND/ULT_RELEASE (`RedHulkSkills`, `player.getId()`) сведены к 0: они шли ПОСЛЕ стопа CombatFeel и `HitStop.trigger` заменял срок, из-за чего атакующий размораживался раньше жертвы (патч-ревью Codex P2). Цена — у полностью промахнувшегося слэма/ульта атакующий больше не держится ни одного тика (рецепт по-прежнему рисуется).
  - **Авторский стоп владеет**, а шов зовёт `CombatFeel.hit(..., withHitStop=false)`: Thor (от пресетного стопа зависит серверный запуск цели — `MeleeMoves.schedule(hitStopTicks(strike))`; тики веса не совпадают с `strike.heavy()` на SLAM с малым уроном), Reinhard (первый корпус LMB мимо `Strikes.hit`; авторские стопы `MeleeImpact`/GrabThrow/SkyKick/FinalPunch), Ghost Rider (пресетные PUNCH/LASH/HOOK несут авторский стоп на том же ударе) и Venom (пресетные CLAW/SLAM и grab STICK/WEB). Прежняя формулировка «у GR/Venom дубли удалены» была неверной: дубли — это их же пресетные стопы, источник оставлен один.
- Моб-атакующие (Venom boss, Ghost Rider boss) шейк/кадр/кольцо получают, но хит-стоп не шлют: `CombatFeel.hit` включает стоп только для `ServerPlayer` (как в слое 1).
- Веер пакетов на AOE: `CombatFeel.hit` шлёт профилированный шейк / кадр удара / кольцо не чаще одного раза на атакующего за серверный тик, и только для **самого тяжёлого** удара тика (чистый `FeelFanout.admit(attackerId, gameTime, severity)`, `severity = weight.ordinal()`); равный или более лёгкий удар после тяжёлого глотается, а более тяжёлый после лёгкого проходит — иначе лёгкая волна ульта Регулуса (`ULT_WAVE_DAMAGE`, LIGHT) съедала бы кадр удара и кольцо от `ULT_FLICK` (CRUSHING) в том же тике. Хит-стоп — по-прежнему на каждую цель.
- Известное ограничение для плейтеста (не блокер): профилированная тряска `ShakeMixer` может складываться с легаси-тряской рецептов (`HeroImpactFx`) у Red Hulk, Ghost Rider, Jinwoo, Regulus и Captain — это два разных канала, дедуп по рецепту не сделан; чинить после плейтеста.
- Тест `combat/feel/FeelCallersTest` — поведенческий (правило проекта: без проверок по тексту исходника): `forDamage`, монотонность лестницы веса, дедуп веера и ограниченность его таблицы.
- Сеть не менялась: PROTOCOL_VERSION 32, новых пакетов нет (`FeelS2CPacket` уже зарегистрирован). Осиротевшие `RedHulkRules.HIT_STOP_HEAVY` и `JinwooRules.FINISHER_HIT_STOP` удалены.
- Осталось T1b (не в этом PR): Homelander, Iron Man, Dio, Sinclair, Thanos, Godzilla, SCP-096 и Chitauri — точки врезки перечислены в таблице аудита.

## T3 — ядро кинокамеры (2026-11-25)

- Серверный фасад: `combat/feel/Cinematics.play(ServerPlayer caster, ResourceLocation sceneId, @Nullable Entity focus)`. Шлёт один `CinematicS2CPacket` кастующему и каждому игроку в 48 блоках (`Cinematics.RANGE`). Сервер ничего не замедляет и не ждёт сцену — только отправка. `focus == null` (или чужой уровень) → крутим вокруг самого кастующего. Пока никто из героев не зовёт (подключение ультимейтов — T5).
- Пакет `network/packet/CinematicS2CPacket`: wire = varint casterId, utf(128) sceneId, varint focusId. Зажим и на кодировании, и на декодировании: отрицательные id → 0, id > `MAX_ENTITY_ID` (0xFFFFF) → потолок, `sceneId == null` / невалидный `ResourceLocation` → `DEFAULT_SCENE_ID` = `viltrumitecore:orbit`. Регистрируется **последним** в `CoreMessages.register()` — после `FeelS2CPacket` (номер пакета 106-й по счёту конец таблицы). `HeroRosterIntegrationTest` обновлён: `packets.subList(punch + 16, …)` теперь ждёт `List.of("FeelS2CPacket", "CinematicS2CPacket")`.
- `PROTOCOL_VERSION` ОСТАЛСЯ `"32"` — решение владельца (в плане аудита §E/T3 стоит 33). Обоснование: 32 — протокол всего PR волны 22; сдвинув его на 33, сломали бы соединение с already-32 пирами без нужды. Пакет добавлен в конец, старые id не тронуты.
- Правило видимости — `client/cinematic/CinematicVisibility.shouldPlay(boolean isCaster, boolean watchOthers, boolean cameraEnabled, boolean otherCameraOwnerActive)`. Чистая функция без Minecraft-классов. Формула: `(isCaster || watchOthers) && cameraEnabled && !otherCameraOwnerActive`. Не кастующий без `watchOthers` сцену не видит вообще (чужую камеру противнику не навязываем). Таблица истинности — 16 комбинаций в `CinematicVisibilityTest`.
- `otherCameraOwnerActive` считает `client/cinematic/CameraOwners.anyActive(mc)` — единственное место, где ядро кинокамеры знает о чужих камерах (остальной код героев не называет). Два вида владельцев: **entity-владельцы** подменяют camera entity (Джину `JinwooUltScene`, Капитан `CaptainUltScene`, Регулус `RegulusUltScene`, Танос `ThanosSceneClient`, `WishSceneClient`, Веном `VenomPossessionClient` — камера = чужой игрок) — ловятся общим `mc.getCameraEntity() != mc.player`; **angle-владельцы** подменяют `Camera.setup` миксином и camera entity не трогают — спрашиваются напрямую: Дио `RollerCinema.active()`, Тор `ThorSceneCamera.active()`, Райнхард `FinalPunchClient.cinematic()`, Синклер `StageCamera.staged()`, байк Гост Райдера `cameraEntity.getVehicle() instanceof HellCycleEntity`. Своя собственная камера «другим владельцем» не считается.
- Полный перебор владельцев камеры (grep по client/**): 6 `@Mixin(Camera.class)` — `DioSceneCameraMixin`, `ThorSceneCameraMixin`, `ReinhardSceneCameraMixin`, `SinclairStageCameraMixin`, `HellCycleCameraMixin` (все владельцы) и `CameraMixin` (смещения Target Lock, не владелец); `new Marker(EntityType.MARKER)` только в Captain/Jinwoo/Regulus/Thanos/Wish; `setCameraEntity` не на игрока только в Venom (host). Подписчики `ComputeCameraAngles`, которые камеру НЕ двигают (только тряска/крен), владельцами не являются: `ErasureScene`, `WorldCutJolt`, `PocketClient`, `ThanosFx`, `CameraShake`/`CameraFeel`, `ShockwaveDistortion`, `PixelVfx`, `ScreenProjector`.
- Рассинхрон при замене активной сцены: второй `CinematicS2CPacket` во время сцены раньше перезаписывал `savedType` на `FIRST_PERSON` (который ставим мы сами), и после конца игрок застревал в первом лице. Правило вынесено в чистую `CinematicVisibility.capturesCameraType(boolean alreadyHoldingView, boolean hasSavedType)` = `!alreadyHoldingView && !hasSavedType` (тип запоминаем только при первом захвате), кейс есть в `CinematicVisibilityTest`.
- Клавиша пропуска: клик Backspace вне сцены раньше оставался в очереди `KeyMapping.consumeClick()` и мгновенно убивал следующую сцену. Теперь `CinematicCamera.onClientTick` при `path == null` сливает очередь (`drainSkipKey()`), а при пропуске съедает всю очередь целиком.
- Проигрывание — `client/cinematic/CinematicPath`: рекорд `(Vec3 centre, double radius, double height, int ticks, float sweepTurns)`, чистый облёт вокруг фокуса, `BASE_TICKS = 60` (~3 с), `BASE_RADIUS = 5.0`, `BASE_HEIGHT = 2.6`, `SWEEP = 0.75` оборота. `around(centre, reduced)`: `reducedMotion()` → медленнее (`ticks × 1.5 = 90`) и с меньшей амплитудой (`radius/height × 0.65`). JSON-сцены — T4, пока любой `sceneId` играет этот облёт.
- `client/cinematic/CinematicCamera`: клиентский `Marker` + `setCameraEntity` (как `JinwooUltScene`), `CameraType.FIRST_PERSON` на время сцены, сохранённый тип возвращается. `init()` вешает слушатели из `ViltrumiteCoreClient.onClientSetup` (рядом с `CinematicSettings.load()`).
- Возврат камеры безусловный (`release()` = try/finally роли, чистит `path`/`camera`/сохранённый тип на всех путях): конец пути (`t >= ticks`), клавиша пропуска, смерть (`!player.isAlive()`), смена измерения (`LevelEvent.Unload` + `camera.level() != mc.level`), открытие меню (`mc.screen != null`), дисконнект (`ClientPlayerNetworkEvent.LoggingOut`), а также если камеру entity подменил кто-то ещё (`mc.getCameraEntity() != camera`). Застрять в чужой камере нельзя.
- Клавиша пропуска: `client/AbilityInputManager.skipCinematicKey` — `KeyMapping("key.viltrumitecore.skip_cinematic", IN_GAME, KEYSYM, GLFW_KEY_BACKSPACE, "category.viltrumitecore.keys")`. Потребляется в `CinematicCamera.onClientTick` через `consumeClick()`.
- Локализация: ключ `key.viltrumitecore.skip_cinematic` вставлен во все 12 локалей сразу после `key.viltrumitecore.dashboard`, формат файлов не тронут (11 файлов с отступом 2, ru_ru без отступа). ru — «Пропустить сцену», en — «Skip cinematic»; de «Filmsequenz überspringen», es (все 7) «Saltar cinemática», tr «Sinematiği atla», zh «跳过过场动画».
- Тесты без входа в игру: `CinematicVisibilityTest` (16 комбинаций + отдельные углы), `CinematicPathTest` (длительность 60 тиков, точки на кольце, `progress` зажат 0..1, look выше ног, reduced медленнее/уже), `CinematicS2CPacketTest` (round-trip + зажим id и сцены на encode и decode). Строковых проверок исходников нет.
- Ещё не подключено (нормально, T4/T5): `CombatFeel` из героев, кинополосы/замедление/подсказка пропуска, JSON-сцены, вызов `Cinematics.play` из ультимейтов.
