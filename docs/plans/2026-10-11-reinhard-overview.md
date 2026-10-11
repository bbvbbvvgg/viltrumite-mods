# Reinhard van Astrea — Implementation Plan (overview)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reinhard van Astrea as the fifth playable hero: own chassis (20 HP, 99.9% resist, 1 HP floor), the `divine` damage seam, 8 passive blessings, three modes on MMB (Restrained → Knight → Holy Sword), 6 Restrained/Knight abilities, the Dragon Sword Reid with its draw event, Lethal Chop variant, Sheath Strike, Rain of Cuts, World Cut and World Erasure.

**Architecture:** `HeroId.REINHARD("reinhard")`, server code in `hero/reinhard/`, client code in `client/reinhard/`. All numbers live in `ReinhardRules` (pure, tested). The body is a whole-body replacement through the Hulkbuster pattern (`RenderPlayerEvent.Pre` + `RenderArmEvent`, animation-system §7.4), posed only with the anim core (`AnimationController.blend/overlay`). Shared code learns about Reinhard only through new hero-neutral `HeroDefinition` hooks and shared helpers (section "New shared seams"); `NoHeroBranchTest` must stay green and its allowlist must not grow.

**Tech Stack:** Forge 47.3.0, Minecraft 1.20.1, Java 17, Mojang mappings, Mixin 0.8.5, JUnit 5.9.3; Python 3 + Pillow for asset converters; ffmpeg for `tools/sfx/`.

**Spec:** `docs/design/2026-10-11-reinhard-design.md` (branch `docs/reinhard-design`, PR #25). Section numbers `§x.y` below refer to it. The spec is approved: a plan never changes its decisions. Gaps are listed in «Вопросы пользователю» at the end.

Paths used in every stage file: `core/` = `viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/`, `res/` = `viltrumitecore/src/main/resources/assets/viltrumitecore/`, `data/` = `viltrumitecore/src/main/resources/data/`, `bin/` = `viltrumitecore/src/main/binassets/assets/viltrumitecore/`, `test/` = `viltrumitecore/src/test/java/dev/baranhan/viltrumitecore/`.

## Stages

Each stage = its own plan file, branch, PR. After each stage the game is playable and nothing that worked before is broken. A jar for the user is built only from Stage 1b on (`AGENTS.md` §3.4: no hero without animations).

| Stage | Plan file | Branch | Tasks | Playable result |
|---|---|---|---|---|
| 1 | `2026-10-11-reinhard-stage1-foundation.md` | `feat/reinhard-stage1` | 9 | pick Reinhard; chassis; divine seam + dev command; modes on MMB; HUD; knight panel |
| 1b | `2026-10-11-reinhard-stage1b-visuals.md` | `feat/reinhard-stage1b` | 8 | model replaces the player (3rd/1st person), sheath on the belt, walk, stances, mode auras |
| 2 | `2026-10-11-reinhard-stage2-blessings.md` | `feat/reinhard-stage2` | 8 | all 8 blessings §4 |
| 3 | `2026-10-11-reinhard-stage3-restrained-knight.md` | `feat/reinhard-stage3` | 11 | all §6 abilities with poses and VFX |
| 4 | `2026-10-11-reinhard-stage4-holy-sword.md` | `feat/reinhard-stage4` | 9 | Reid draw event, sheathe/auto-return, Lethal Chop variant, Sheath Strike, Rain of Cuts |
| 5 | `2026-10-11-reinhard-stage5-world-cut.md` | `feat/reinhard-stage5` | 6 | World Cut with the block budget |
| 6 | `2026-10-11-reinhard-stage6-world-erasure.md` | `feat/reinhard-stage6` | 7 | World Erasure, global pause, limbo |
| 7 | `2026-10-11-reinhard-stage7-sounds-polish.md` | `feat/reinhard-stage7` | 5 | final sounds, credits, acceptance pass, release |

Total: 63 tasks. Each stage branch starts from the previous stage branch (or `main` after the previous PR is merged). Before every push: fetch and compare with the remote branch (other sessions push too).

## Global Constraints

Every task implicitly includes these lines.

- `AGENTS.md` §3: every pose, renderer, particle, shader and VFX goes through the existing system (`client/anim/`, model mixins, `client/render/vfx/`, `PixelVfx`, `HeroImpactFx`, `CameraShake`, post shader `dash_impact`). No PlayerAnimator, GeckoLib animation, Veil, second keyframe engine or second post chain. Read `.agents/skills/animation-system/SKILL.md` before any visual task.
- `AGENTS.md` §7: no `HeroId.REINHARD` branch in shared code. Shared code asks `HeroDefinition` hooks. `NoHeroBranchTest` pattern gets `REINHARD` and the `reinhard` path prefix in Stage 1 Task 1; the allowlist never grows.
- `AGENTS.md` §8: gameplay is server-authoritative. Movement that the player feels (step dash, air step, super jump, dash behind) is applied on the client at once and validated by the server (spec §1.2.3). Client classes only in `client/` packages and `viltrumitecore.client.mixins.json`.
- Append only: `HeroId`, `HeroAction`, `MouseButton`, `OwnerSection`, `CleanupReason`, `ReinhardMode`. No renames of registry ids, NBT keys, synced data, key-mapping ids.
- Spec §1.2.6: no hook or exception for another hero. Reinhard never pierces Lion's Heart. The only cross-hero contracts are the hero-neutral hooks below.
- Spec §1.2.4: no shared resource; only cooldowns and conditions.
- Spec §1.2.5: own chassis. `allowsLegacyAbilities` is `false` (no Viltrumite strength modifier, damage reduction, forced `mayfly`, 40-tick heal). Only `viltrumite:chop` comes from the kit, through `allowsLegacyAbility`.
- Spec §1.2.7: no flight (`allowsFlight` false, `grantsFlightAbility` false).
- Spec §5: `mobGriefing` and unbreakable blocks are honoured everywhere: every block change goes through `HeroDestruction.canDestroy`.
- All numbers are starting values in `ReinhardRules` (ticks, 20 t = 1 s). Tuning later only changes constants.
- Lang: every new key in all 11 files of `res/lang/` in the same commit (en_us is the source).
- Binaries (png, ogg) are committed only as `bin/**.b64`, produced by a script in `tools/`. Raw archives (`reinhard_assets.zip`, `sounds.zip`) are never committed.
- Tests: JUnit 5 in `test/hero/reinhard/` (client pure logic in `test/client/reinhard/`), test first.
- Verification commands of every task, unless the task says more:
  - `./gradlew :viltrumitecore:test --tests '*Reinhard*' --no-daemon` → PASS
  - `./gradlew build --no-daemon` → BUILD SUCCESSFUL
  - `python3 .agents/skills/add_hero/scripts/check_hero_resources.py --hero reinhard` → 0 new problems
  - `git diff --check` → no output
- Every in-game check: first AND third person (spec §11).
- Commits: English, conventional (`feat(reinhard): …`, `feat(hero): …` for shared seams, `docs(reinhard): …`). PR: Russian title, body starts with «Для игрока».

## New shared seams

Each seam lives in shared code, is a hero-neutral default, and is useful to any future hero. Reinhard is only the first user.

| Seam | Stage | Where | Why it is shared |
|---|---|---|---|
| `HeroDefinition.isDivine(Player)` + entity tag `viltrumitecore:divine` + `hero/DivineTargets` | 1 | `hero/HeroDefinition`, `data/viltrumitecore/tags/entity_types/divine.json` | spec §3.3: "divine" is a class of beings; future god-tier heroes and bosses join through it |
| Damage type `viltrumitecore:divine` + damage-type tag `viltrumitecore:divine` + `hero/DivineDamage` + dev command | 1 | `data/viltrumitecore/damage_type/`, `command/CoreCommands` | spec §3.2: the only way to hurt divine beings; any future hero can deal it by adding its own damage type to the tag |
| `HeroDefinition.isAbsolutelyInvulnerable(Player)` | 1 | `hero/HeroDefinition`; Regulus answers with Lion's Heart | spec §3.3: "absolute invulnerability" is a property, not a Regulus check; Reid and Erasure ask the hook, never `HeroId.REGULUS` |
| `HeroDefinition.clampUnblockableDamage(...)` + `hero/AdminKill` scope | 1 | `hero/HeroDamageLayers`, `mixin/KillCommandMixin` | today `BYPASSES_INVULNERABILITY` skips every hero hook; a hero HP floor must also see void and `generic_kill`, while `/kill` must stay an admin tool for all heroes |
| `HeroDefinition.blocksEffect(Player, MobEffectInstance)` | 2 | `hero/HeroEvents` (`MobEffectEvent.Applicable`) | effect immunity is generic (any hero may be immune to poison) |
| `HeroDefinition.projectileResponse(...)` + `hero/HeroProjectiles` | 2 | `hero/HeroProjectiles` (`ProjectileImpactEvent`) | deflect / catch / reflect physics is hero-neutral; heroes only choose the response |
| `HeroDefinition.airJumps(Player)` / `airJumpVelocity(Player)` + `hero/HeroAirJump` + `client/hero/AirJumpClient` | 2 | next to `HeroSuperJump` / `SuperJumpClient` | multi-jump is a movement contract like super jump; client-predicted, server-validated |
| `HeroDefinition.foreignResourceRegenFactor(Player self, Player other)` + `hero/HeroResourceRegen` | 2 | Iron Man `Energy.tick`, Homelander `EyeHeat.tick` read `HeroResourceRegen.scale(player)` | spec §4.7: "через общий хук героя, не через ветку по герою" |
| `hero/PersistentCooldowns` | 2 | persisted player NBT, ticked for every online player | spec §4.2: a cooldown that survives relog, hero change and death and does not tick offline |
| `HeroDefinition.chopStyle(Player)` → `hero/ChopStyle` | 4 | `ChopSweepVFXManager`, chop key loop, `ChopC2SPacket`, `tickChop`, `ChopImpactManager` | spec §7.2: a hero variant of the kit chop without touching Homelander (`ChopStyle.DEFAULT` = today's values) |
| `hero/Annihilation` + damage type `viltrumitecore:annihilation` | 4 | `hero/` | spec §1.3 "Удар Рейда": kill anything non-divine, ignore armor/layers/totems, stop at absolute invulnerability; reusable for any execute-style power |
| `hero/CarveJobs` + `CarveBudget` | 4 | `hero/` (ticked from `HeroEvents.onLevelTick`) | budgeted, TPS-adaptive block removal for any large destruction |
| `hero/control/WorldPause` (global freeze) | 6 | `hero/control/` + `client/hero/WorldPauseClient` | spec §7.7: a server-wide cinematic pause; built on `ControlManager` FREEZE, so every existing `canAct`/anchor gate works |
| `CleanupReason.ERASED` | 6 | `hero/CleanupReason` | an erased player is not dead, but every hero must stop its channels like on death |
| Client: `SkyTint`, `SoundDuck`, `HitStop` (`client/render/vfx/`), `PanelStyle.drawAttachments`, `HeroHeadPreviews` (`client/hero/`) | 1–5 | shared client toolkit | sky darkening, sound ducking, hit-stop, HUD attachments and race-screen head previews are needed by any big hero |
| Post shader uniforms `EraseCrack/EraseShatter/EraseDark/EraseRebuild/EraseSeed` | 6 | `dash_impact` | spec §9.5: new uniforms in the existing chain, no second chain |

After each seam lands, the same task updates `.agents/skills/add_hero/references/hero-seam.md` (hooks table) or `shared-toolkit.md`.

## Key technical decisions (not product decisions)

1. **Pose source.** Cancelling the vanilla render (spec §8) also skips every `PlayerModel.setupAnim` TAIL mixin (`ChopModelMixin`, …). So Reinhard's third-person poses are geo clips in `ReinhardRenderer`, driven by synced fields; first-person poses are drawn in `RenderArmEvent`, and the existing first-person mixins that move the `PoseStack` before the arm (`FirstPersonChopMixin`) still apply. Clips are generated by `tools/assets/make_reinhard_clips.py` from keyframe tables (reproducible).
2. **Lethal Chop base damage** comes from `ReinhardRules.CHOP_BASE_DAMAGE = 20` through the existing `HeroDefinition.chassisStats` seam (`HeroChassis(20, 0, 0)`). `ChopImpactManager` already reads `getBaseDamage()` through it; the Viltrumite stat values are never read. Reinhard's chassis numbers other than base damage are ignored because `isViltrumite()` is false.
3. **Ability panel = mode.** 3 modes × 6 slots = the 18-slot loadout. The server sets `ACTIVE_PAGE` = mode ordinal; `allowsAbilityPages` is false, so the page key does nothing for Reinhard.
4. **Snapshot layout** (`ReinhardFlags`, Stage 1 Task 2): `extraCooldowns[0..13]` = ability cooldowns by `ReinhardAbility.ordinal()`, `[14]` = mode cooldown, `[15]` = Phoenix (seconds); `resource` = Reid ticks until auto-return; `actionId/actionElapsed/actionLength` = the current action timeline.
5. **Long-range scenes** (Reid draw r 96, World Cut 200 blocks, Erasure for all players) use their own S2C packets in `network/packet/`, because the public snapshot reaches only tracking players.
6. **Packet speed cap.** `ClientboundSetEntityMotionPacket` clamps velocity to ±3.9 b/t. Sky Kick uses exactly 3.9: apex ≈ 61 blocks (spec "60+"). The 120-block throw re-applies velocity for several ticks.

## Spec coverage

| Spec | Requirement | Stage · Task |
|---|---|---|
| §1.2.1 | PvP numbers, mobs only targets | all: numbers in `ReinhardRules`; 7·4 tuning pass |
| §1.2.2 | existing anim/VFX systems only | Global Constraints; 1b·all, 3·11, 4·4–5, 5·4, 6·4–6 |
| §1.2.3 | server decides; felt movement predicted on client | 2·4, 3·3, 3·8 |
| §1.2.4 | no shared resource | 1·2 (`ReinhardState` has only cooldowns) |
| §1.2.5 | own chassis, kit not inherited, Chop via `LegacyKit` | 1·3, 4·6 |
| §1.2.6 | no hooks for other heroes; no Lion's Heart piercing | 1·5, 4·1, 6·2 |
| §1.2.7 | no flight | 1·1 |
| §2 | 4th race button, head from model, `changeHero` | 1·1 (button), 1b·6 (3D head) |
| §3.1 | 20 HP, 99.9% resist incl. clean and % damage, floor 1 HP incl. void/`generic_kill`, `/kill` kills, void rescue, regen 1 HP/s, no fall damage, 100% knockback resistance | 1·3, 1·4 |
| §3.1 | protections in all modes | 2·8 (mode matrix test) |
| §3.2 | `divine` type, layer check, dev command, order Adaptation → Phoenix → death | 1·5 (type, command, death), 2·1 (Phoenix, Adaptation, order) |
| §3.3 | `isDivine`, entity tag, `isAbsolutelyInvulnerable` (Lion's Heart), Reid/Erasure skip divine | 1·5, 4·1, 6·2 |
| §4.1 | front melee parry with pose and spark | 2·2 |
| §4.2 | Phoenix, NBT cooldown 10 min online | 2·1 |
| §4.3 | projectiles deflect sideways; windows win | 2·3, 3·4, 3·10 |
| §4.4 | 5 air steps + super jump | 2·4 |
| §4.5 | outline back attackers and invisible r 32 | 2·5 |
| §4.6 | poison and negative effect immunity | 2·6 |
| §4.7 | enemy resource regen ×0.5 r 24 via hook | 2·7 |
| §4.8 | 3 divine hits of one type → immune until death | 2·1 |
| §5 | three modes, MMB cycle, pick-block cancelled, no pose, cd 3 s, own sound each, own page each | 1·6, 1·7 |
| §5 | destruction per mode, `mobGriefing`, bedrock | 3·1 (`DestructionLevel`), 3·5, 3·9, 4·8, 5·2 |
| §5 | leaving Holy Sword sheathes Reid | 4·2 |
| §6 | input kinds, slot order, air step / super jump not in slots | 1·7 (loadout), 3·1 |
| §6.1 | Light Punch, Step Dash, Catch Projectile | 3·2, 3·3, 3·4 |
| §6.2 | Fist Combo, Sky Kick, Grab & Throw, Dash Behind, Stomp, Reflect | 3·5, 3·6, 3·7, 3·8, 3·9, 3·10 |
| §7.1 | Holy Sword page, slot conditions, draw any time | 1·7 (layout), 4·2 (conditions) |
| §7.2 | Lethal Chop = kit chop, base 20 → 35, variant visual, Homelander unchanged | 4·6 |
| §7.3.1–7 | draw event 6 s: slow draw, shake, gravity lift r 96 max 2–3 blocks, silence/dark sky/weather, cracks/pillar/funnel, final flash/wave/chord, chat to all | 4·3, 4·4 |
| §7.3 | gold aura and sky tint while drawn; auto-return 60 s; sheathe 1 s | 4·2, 4·5 |
| §7.4 | Sheath Strike r 6, stun 60 t, 20 dmg, cd 160 | 4·7 |
| §7.5 | Rain of Cuts 30°, 40 blocks, 12 cuts, cd 300 | 4·8 |
| §7.6 | World Cut timeline, 200×17×45–50, Reid strike on the line, budget 2000/tick, adaptive wave, client by time, few debris, cd 45 s | 5·1–5·6 |
| §7.7 | World Erasure: target ≤ 64, 7–8 s, all players all dims, freeze, no overlap, mob vanishes, player → limbo 30 s → spawn, Lion's Heart/divine immune, cd 60 s | 6·1–6·7 |
| §8 | assets only from the archive, OBJ → geo, Hulkbuster pattern, sheath on belt, walk conversion, icons, no Tensura mechanics | 1b·1–1b·5, 1·8 |
| §9.1 | mode icons + Reid timer bar, cooldowns on panel, knight `PanelStyle` | 1·8, 4·5 |
| §9.2 | stances and auras per mode; Chop left hand with Reid; pose per ability in both views | 1b·4, 1b·7, 3·11, 4·5, 4·6 |
| §9.3 | kick/throw: sonic ring, white trail, hit-stop 2–3 t | 3·6, 3·7 |
| §9.4 | no camera takeover for Reinhard; FOV pull for others; strong shake | 5·4 |
| §9.5 | erasure screen phases, sounds, new uniforms | 6·4, 6·5 |
| §9.6 | limbo look, text lines, heartbeat, white flash | 6·6 |
| §10 | quality recorded sounds, licenses, `CREDITS.md`, vanilla placeholders until then | every stage (placeholder slots), 7·1–7·3 |
| §10.1 | 15 events from 14 Freesound files, mix and trims by `tools/sfx/` script | 7·1, 7·2 |
| §10.2 | mod and vanilla sounds | 3·2, 3·5, 3·6, 3·7, 4·6 |
| §11.1–12 | acceptance criteria | each stage lists the ones it closes; 7·4 runs all |
| §12 | open later (worthy list, balance, sounds) | not in scope; `Reid` draw gate is a single `ReinhardRules.canDraw(...)` that returns true |

## Risks shared by all stages (from the memory bank)

- **Static init order:** a `static final INSTANCE = new X()` must come after the static fields its constructor reads (crashed the client once). Applies to `ReinhardHero`, `ReinhardPanelStyle`, sound holders.
- **Snapshot race:** the abilities packet can land before the hero snapshot. Never wipe local state on the client from a missing snapshot bit; server-only resets (the Iron Man survival flight bug).
- **Pose smoothing:** never `Mth.lerp(k, …)` per call in pose code: it runs twice with the Oculus shadow pass and follows FPS. Ease by `System.nanoTime` dt (`1 - exp(-k·dt)`); float time from `RealTime.seconds()`.
- **Shadow pass:** skip glow (`RenderType.eyes`) and world VFX when `ShaderCompat.isShadowPass()`.
- **Owner sections** are deduplicated by content; push `Section.EMPTY` before re-sending the same content. Clear on respawn, dimension change, logout.
- **`SilhouetteManager.getState`** consumes a per-frame delta: call once per entity per frame.
- **Knockback** of any target only through `HeroRegistry.allowsImpulse(target)` and only after `hurt(...)` returned true.
- **Loadout migration:** new abilities of a later stage reach saves through `ViltrumiteOfferedAbilities`; a slot the player cleared stays empty.
- **Gradle cache** can be wiped: if `--offline` fails on foojay, run one online build.
- **GitHub MCP push is text only:** binaries via `bin/**.b64`; deletes need a separate call.

## Вопросы пользователю

Это реальные развилки продукта, которые спека не решает. До ответа план использует указанный вариант по умолчанию; ответ меняет только константы или одну задачу.

1. **Комбо кулаками (§6.2): одно нажатие или три?** По умолчанию: каждое нажатие — следующий удар серии (окно 20 t на следующий; 3-й удар — волна r 4). Альтернатива: одно нажатие проигрывает всю серию 10/10/16 сама. (Stage 3 · Task 5.)
2. **Стирание мира (§7.7): что с замороженными игроками 7–8 с?** Спека морозит всех игроков, но не мобов и не урон. По умолчанию: на время стирания все игроки, кроме Рейнхарда, неуязвимы, а враждебные мобы рядом с ними (r 32) тоже замирают. Альтернатива: мобы и урон работают как обычно. (Stage 6 · Task 1.)
3. **«Удары одного типа» в Обретении защиты (§4.8).** По умолчанию: тип = тип урона (`DamageType`) из тега `viltrumitecore:divine`; удар другого божественного типа сбрасывает серию, обычные удары серию не трогают; окна времени нет. Это контракт для будущих «божественных» героев: каждый вид их атаки = свой тип урона. (Stage 2 · Task 1.)
4. **Точка воскрешения Феникса (§4.2) — кто её «выбирает»?** По умолчанию: автоматически — безопасная точка в 6–10 блоках от места смерти, дальше от убийцы. Альтернатива: на месте смерти. (Stage 2 · Task 1.)
5. **Иконки `reinhard.png` и `blessing_bearer.png` (§8).** Спека не говорит, где они. По умолчанию: `reinhard.png` — значок героя в окне выбора расы рядом с 3D-головой; `blessing_bearer.png` — индикатор Феникса под значками режимов (серый во время перезарядки). (Stage 1 · Task 8, Stage 2 · Task 1.)

Заметка: архив `sounds.zip` из этой сессии содержит все 14 исходников из §10.1. Лицензии проверяет Stage 7 · Task 1; если какой-то файл окажется CC-BY-NC — это будет отдельный вопрос.
