# Project memory

Read this file before every task. Keep it short. See `AGENTS.md` §6 for the rules.

## Origin
- The original author (package prefix `dev.baranhan`) gave the mods to the repository owner. Only the release JARs existed. The sources in this repo were decompiled from them.
- Original JARs: `original-jars/viltrumitecore-forge-1.10.3.jar`, `original-jars/viltrumiteflight-forge-1.6.7.jar`.
- Built JARs (since core 1.24.1): `reforgedcodex-core-<ver>.jar`, `reforgedcodex-flight-<ver>.jar` (only `archivesName` renamed; mod ids/packages stay `viltrumitecore`/`viltrumiteflight`).

## Direction
- Goal: revival on a new architecture, then many heroes and characters.
- Heroes: Human, Homelander (replaced Viltrumite in 1.14.0), Regulus. `HeroId.VILTRUMITE` stays in the enum for save compatibility only: `fromKey("viltrumite")` and the legacy `IS_VILTRUMITE` boolean map to `HOMELANDER`.
- `grebeshok105/Codex-Superheroes` (Fabric) is dropped. It is an idea source only.

## Architecture relations
- `viltrumitecore` depends on `viltrumiteflight`. Core visual effects read flight state.
- `./gradlew runClient` loads both mods together.
- Per-player state lives on `Player` through mixin interfaces: `ViltrumiteCorePlayer` (core) and `ViltrumiteFlightPlayer` (flight, FlightState).
- `PlayerEntityCoreMixin` (~1500 lines) holds most core player logic: synced data (`IS_VILTRUMITE`, `HAS_CHOSEN_RACE`, `IS_DASHING`, `DASH_TICKS`, `PUNCH_TICKS`, `IS_LEFT_ARM_PUNCH`, ...), dash, punch, chop, grab. Client model mixins read this synced data to pose the model. It is a refactor target for the hero seam.
- Impact effects are driven by `PunchImpactManager`, `ChopImpactManager`, `ThunderClapManager`.
- Core has its own geo/animation loader in `client/anim/` for JSON in `assets/viltrumitecore/geo/` and `assets/viltrumitecore/animations/`. Details: `.agents/skills/animation-system/SKILL.md`.

## Hero seam
- Heroes implement `hero/HeroDefinition`; shared code asks hooks (`cancelsFallDamage`, `onLanded`, `superJumpVelocity`, `onSuperJump`, `onHurt`, `onDimensionChange`, `abilityIcon`, ...), not `HeroId`. Regulus still has legacy `HeroId.REGULUS` branches in shared code; `test/architecture/NoHeroBranchTest` fails on new ones outside hero-named places.
- Legacy Viltrumite kit (punch, dash, thunderclap, grab, ...) is gated per ability: `hero/LegacyKit.allows(player, id)` → `HeroDefinition.allowsLegacyAbility`. `isViltrumite()` now means "may use the kit".
- Generic hero input: `HeroDefinition.heroInputSlots()/heroActionFor()` drive `HeroInputC2SPacket` from slot keys (no per-hero client branch). Generic snapshot fields: `resource`, `resourceLocked`, `heroFlags` (bits). `HeroOwnerSnapshot` ids = owner-only entity ids, read per hero (Regulus carriers, Homelander focus targets).
- Shared hero toolkit: `hero/fx/HeroFx` → `HeroFxS2CPacket` → `client/render/vfx/HeroImpactFx`; `CameraShake`, `PixelVfx`, `hero/HeroDebris`, `hero/HeroShockwave`, `hero/HeroSuperJump`, `client/hero/SuperJumpClient`. Procedure: `.agents/skills/add_hero/`.
- Flight grant: `HeroDefinition.grantsFlightAbility` → `hero/HeroFlightGrant.sync` (every server tick + login/respawn/hero change) sets vanilla `mayfly` for non-legacy heroes; NBT `HeroGrantedMayfly` marks our grant, never revokes a foreign `mayfly`; creative/spectator untouched.
- Legacy per-tick mayfly wipe (`PlayerEntityCoreMixin` onTick tail, non-kit survival players) is SERVER-ONLY, fires only when mayfly/flying is set, and skips heroes whose `allowsFlight(player)` is true. A client-side wipe raced the hero snapshot (abilities packet lands before SUIT_WORN) and killed survival flight. `HeroFlightGrant` resends our grant every 40 ticks (KEEP case) as self-heal.
- Damage layers: `hero/HeroDamageLayers`; hooks `absorbIncoming` (LivingAttackEvent HIGH), `clampFinalDamage` (LivingDamageEvent LOWEST), `modifyOutgoingDamage` (LivingHurtEvent HIGH); `HeroDamage.applyCleanDamage` runs them too. Order: control → shield → Hulkbuster → mark → nano armor → HP.
- Mouse / held input: `HeroDefinition.mouseAction(MouseButton, Player)` (both sides) → client `client/hero/HeroMouseInput` cancels vanilla attack/use/pick-block and sends `HeroMouseC2SPacket` edges → server `hero/HeldInputs` (claim + canAct only on press, release always to the started action, forced release on control/death/logout/hero change). MIDDLE = heart key mapping (default MMB). Replaced `RegulusInputPriority`.
- Flight profile: `viltrumiteflight` `FlightProfile`/`FlightProfiles`/`FlightMotion`; `HeroDefinition.flightProfile` (null = legacy flight unchanged). Movement is client-side: inertia runs on the local client, server owns throttle/state. Iron Man normal flight = null profile (same as Homelander, user decision after play test); only the 0-energy glide uses `IronManRules.glideProfile()`.
- Super-jump key id stays `key.viltrumitecore.regulus_super_jump` (player bindings).
- Knockback of any target by a hero goes through `HeroRegistry.allowsImpulse(target)` (IMPULSE policy + not anchored) and only after `hurt(...)` returned true.
- Owner sections are deduplicated by content (`pushOwnerSection`). To restart a client timer with the same content (re-scan of the same target), push `Section.EMPTY` first.
- Hero migration by id alias does not move ability slots: a legacy Viltrumite save loads as Homelander with the old slots. `PlayerHeroMixin` marks a legacy save; on login `HeroRegistry.resetLoadout` writes the default loadout, otherwise `repairLoadout` resets slots that hold abilities the hero does not own.
- `isViltrumite()` == `allowsLegacyAbilities()`, so every kit hero also gets the old Viltrumite chassis from `PlayerStatsMixin`/`PlayerEntityCoreMixin`: base strength, damage reduction/ignore threshold, forced `mayfly`, heal every 40 t. A hero with its own regen opts out through `HeroDefinition.usesLegacyRegen` (Homelander does).
- `setViltrumite` on a `ServerPlayer` runs the full `HeroRegistry.changeHero` lifecycle (commands grant/revoke, scourge virus). NBT load uses the identity-only path (`viltrumitecore$setLegacyIdentity`). The race screen's «Человек» sends `HeroChoiceC2SPacket("human")`.
- Hero panel abilities are registered from `HeroDefinition.panelAbilities()` inside `HeroRegistry.register`, not from `ViltrumiteAbilities`.
- `HeroInputC2SPacket` accepts a press only if the action's slot ability is equipped on the active page (releases always pass).
- The shared dash (`PlayerEntityCoreMixin.startDash`) starts only from `FlightState.NONE` or `HOVER`; in CRUISE/SONIC it is a silent no-op. Homelander allows it only in HOVER.
- Thunderclap (`ThunderClapManager`) follows the full look vector (3D), tears exposed blocks on any side; the client rings tilt by pitch.
- Binary assets pushed through GitHub MCP live as base64 in `viltrumitecore/src/main/binassets/**.b64`; Gradle `decodeBinaryAssets` writes them to resources.

- Loadout: NBT `ViltrumiteOfferedAbilities` (server only) = default abilities the current hero already gave. `repairLoadout` fills only defaults outside it, so new abilities of a later version reach old saves once and a slot the player cleared stays empty. A save without the key counts its current slots as given. `changeHero`/`resetLoadout` set it to the defaults; the respawn clone copies it.

## Iron Man visuals (Stage 1b)
- 3D parts on player parts: `client/anim/render/PlayerGeoLayer` (+ `PlayerBoneMap`, first person via `PlayerGeoHandMixin`); providers register once (`IronManPartsProvider`: helmet + flames). Skill §7.1.
- Suit reveal: no custom shader (Oculus). `RevealMask` distance field → 16 frames baked at runtime into `DynamicTexture`s (`IronManSuitTextures`, reload listener in `ViltrumiteCoreClient`); `skin` frame is the player skin via `HeroSkins.skinVariant`. Decision: `docs/spikes/2026-10-ironman-reveal.md`. Skill §7.2.
- Ability panel looks: `client/hero/PanelStyle`/`PanelStyles` (default = original drawing, `IronManPanelStyle` holographic).
- Iron Man poses `IronManModelMixin` priority 1190 → `IronManPoser`; suit sounds own synthesis `tools/sfx/ironman_stage1.sh`; asset conversion `tools/assets/convert_ironman_stage1b.py`.
- Static init order: a `static final INSTANCE = new X()` must come after the static fields its constructor reads (crashed the client once).

## Iron Man combat (Stage 2)
- Server driver `hero/ironman/IronManCombat` (repulsors, unibeam + overdraft, missiles with lock, nano arsenal, shield); input routing in `IronManHero`. Rules are pure classes with tests (`CoreOverheat`, tool cycle, unibeam timeline, missile lock, nano arsenal, shield).
- Guard key seam: `HeroDefinition.guardAction` (F → `MouseButton.GUARD`, client `HeroGuardInput`) + `blocksHandSwap` (server cancels `LivingSwapItemsEvent`).
- Owner snapshot sections: `HeroRegistry.pushOwnerSection` / `ClientHeroData.section`; Iron Man uses `MARKS` (missile locks). `extraCooldowns[0]` = nano-lost lock.
- `IronManFlags` bits 27-29 = stage 2 pose bits, 30-31 reserved.
- `client/render/vfx/ScorchRenderer` + `ScorchBuffer` are shared (beam scorch marks).
- Client: `IronManCrosshair` (per tool), `IronManCombatParts` (Satsu nano parts in `geo/ironman/nano/`, `tools/assets/convert_ironman_stage2_parts.py`; shield = one 19x14 forearm plate in both views, mark = the same plate with the hex field; missiles = Sind shoulder pods for every suit, `geo/ironman/missiles/`), `NanoDamageVisuals` (baked damage masks, reload listener). Missiles are pixel VFX only. Arm swing poses are third person only.
- Sounds: `tools/sfx/ironman_stage2.sh`; icons/crosshairs: `tools/assets/make_ironman_stage2_icons.py`.

## Iron Man helmet and JARVIS (Stage 3)
- Helmet: `hero/ironman/Helmet` (toggle 12 t, auto-close on a combat hit), saved in NBT (`HelmetOpen`); flag `HELMET_CLOSED` only with the suit on. Client `HelmetAnim` (helmet open = only the faceplate: nano dissolve steps / mark `MarkFaceplate` slide; hand-to-helmet gesture only with the suit staying on), `HelmetHud` frame. Helmet open = HUD shows only energy, vanilla crosshair, no JARVIS, no scan.
- Server driver `hero/ironman/IronManJarvis` (helmet, scan, countermeasures); pure rules `jarvis/ThreatScan`, `scan/*` (`ScanProgress`, `ScanTraits`, `ScanAnalyzer`, `WeakSpots`, `ScanCard`), `Countermeasures`. Threat ids go in owner section `THREATS`, scan highlights in `SCAN`; scan card = `ScanCardS2CPacket`.
- Scan seam: `HeroDefinition.scanInfo(Player)` (extra `ScanLine`s) and `hiddenFromScan(Player)`. Regulus/Homelander/Iron Man add lines via static `scanInfoFor(...)`.
- Scan progress on the client: snapshot `controlTargetId` + lowest-priority channel `SCAN`.
- Countermeasures: `FlareEntity` (no damage, no model, `FlareRenderer` pixels), retarget homing (`Countermeasures.retargets`) and shulker bullets (`ShulkerBulletAccessor`), mobs forget the player 40 t, Wither/Ender Dragon ignored. `extraCooldowns[1]` = cooldown.
- Client JARVIS: `client/ironman/jarvis/JarvisVoice` + `VoiceGate` (one line at a time, global gap, per-line cooldown), `JarvisHints` (threat brackets, edge arrows, projectile marks, subtitle); scan UI `client/ironman/scan/ScanReticle`, `ScanCardRenderer`. World-to-screen: `client/render/vfx/ScreenProjector`.
- Voice: 4 CC0 recordings from Codex-Superheroes (suit up, threat, critical threat, scan done); other lines = subtitle + `jarvis_warning` chime. Sounds `tools/sfx/ironman_stage3.sh` (`--jarvis DIR` re-encodes the recordings); art `tools/assets/make_ironman_stage3_assets.py`.

## Iron Man Veronica and marks (Stage 4)
- One mark = one instance: `mark/MarkRoster` location `STORED/WORN/EMPTY/IN_DELIVERY`, changed only by the guarded `move(id, from, to)`; durability lives only in the roster (NBT `MarkRoster`). `IronManState.reconcileMark` makes only the mark on the body WORN; load and death recall world instances to STORED.
- `Suit` holds the mark states (`MARK`, `EQUIPPING`, `EXITING`, `MARK_PARTIAL`, appended to `SuitState`); `Suit.tick()` returns `Event.MARK_ON/EXITED`, `interrupt()` keeps locked parts as `MARK_PARTIAL`. Saved EQUIPPING/EXITING become NONE.
- Every system reads `IronManState.spec()` (`SuitSpec`: nano defaults or `MarkSpec` + Mark 42 lost parts), never nano constants for armor, missiles, Unibeam charge, shield cost, recoil, drain, weapon and flight speed.
- Damage order: shield (`IronManCombat.absorb`) → mark durability (`IronManMarks.absorb`, whole hit, no spill) → nano armor → Tony. A worn mark breaks on the next tick: debris + `Suit.autoNano()` (nano on at once, wave is visual only).
- Veronica settles: in LANDED it drops into the crater of its landing blast (or any hole dug under it) instead of hanging in the air. Model: one joined module, glossy bright red (`make_ironman_stage4_visuals.py`, 256 px).
- Veronica pod is permanent (user decision 2026-10-10): saved with the chunk (owner UUID, phase), linked by UUID (`IronManState.podUuid`, NBT `VeronicaPod`). It leaves only when the online owner is not Iron Man or called a newer pod (`IronManVeronica.retired`). A new call is allowed when the pod is beyond the menu range; the cooldown starts at the call.
- The empty suit (`EmptySuitEntity`) is not saved and leaves when the owner is offline, dead, in another level, far (> 96) or `emptySuitId` no longer names it. It is placed exactly where Tony stood, on his body yaw, and never moves (gravity only).
- Entering (PR #19 iteration 2): only from the front (`EmptySuitEntity.inFront`: ≤ 3 blocks, cos > 0.34, else actionbar `enter_front`); no teleport and no flying parts. `IronManState.enteringSuitId` roots Tony (`canAct` false); the client `SuitEntryDriver` walks him up, turns him round and steps him back (0–32 t), the server snaps him onto the suit at 32 and calls `IronManMarks.finishEntering` at 46 (`Suit.equipNow`, roster IN_DELIVERY → WORN). Control, death or a lost owner abort (`abortEntering`: the suit stays EMPTY where it stood).
- Part flight is client-only: server syncs `EQUIP_PHASE`, the SUIT timeline (`EquipTimeline`, 50 t delivery / 20 t enter), `variant` (mark + parts) and the parts source as `actionTarget`.
- Client arm poses (PR #19): `IronManAnimation` clock + `IronManPoser`/`IronManFirstPerson` (signatures are a layer there; `SignatureModelMixin`/`SignaturePoser` removed). Missiles launch from forearm pods tinted by `client/ironman/mark/SuitPalette`; shoulder Sind pods stay in the repo unused.
- Signatures: `mark/MarkSignature` per mark (`MarkSignatures`), state in `IronManState.signature`; slot 3 in a mark and the RMB `SIGNATURE` tool route to it; cooldown in `extraCooldowns[3]`.

- Archive models (`IronMan_Hulkbuster_models.zip`, user-supplied, not committed): Satsu GeckoLib geo is read as is by `BakedGeoModel`; Sind Tabula `.tbl` goes through `tools/tabula2geo.py` (Bedrock y = 24 - Java y, x kept, turn angles kept: `BakedGeoModel`'s (-x, -y, z) equals a Java `ModelPart` turn).
- Satsu mark skins: `full_body` UVs are the vanilla skin layout; `all_helmet` = three head boxes, baked into the head rows (`tools/bake_suit_skin.py`). Extras read the raw suit texture (`<mark>_suit.png`): their UVs also use the head rows.
- `each_part` has no back piece: the chest is split into its front faces (`chest`) and rear faces (`back`). 7-part arms = arm + shoulder.
- Sind Mark 42 pieces are full limb shells with partial textures in a 160 px atlas; the Sind generic `lights` skin texture is not in the archive, so the Mark 42 glow is baked from the pieces' `mark42_lights`.
- Box UV vs per-face UV in `BakedGeoModel`: dict `east` = Bedrock +x side, the box's second strip; scale a cube only after making its UV explicit (`explicit_uv` in `convert_ironman_sind.py`).

- Countermeasures forget a player only through `Brain.hasMemoryValue` first: goal-based mobs have no ATTACK_TARGET memory (crash 1.22.0).

## Iron Man Hulkbuster (Stage 5)
- `hulkbuster/HulkbusterLayer` is a layer over the suit (NBT `Hulkbuster`): the suit under it is untouched. Saved DROPPING/ASSEMBLING become ACTIVE with `needsFitCheck`; the first server tick checks the room or refuses (no cooldown).
- Size goes only through `HeroDefinition.bodyScale` (`HeroSizeEvents`, Forge `EntityEvent.Size`). Before growing, `IronManHulkbuster.freeSpot` checks the real final box (standing 0.6×1.8 × 1.7) with `level.noCollision`, searches 2 blocks, else refuses.
- Damage order: shield → Hulkbuster (whole hit, no spill) → mark → nano armor → Tony. The shield is ×1.5 inside.
- Grab and throw use `ControlManager` PULL (acquire / release with an effect id); the Viltrumite legacy grab is not reused (decompiled, unverified). Every end of a carry calls `IronManHulkbuster.releaseGrab`.
- Mark 48 art is Sind (native 68 px tall, drawn at 1.7 × 32/68). Clips are baked from the Sind `.fsk` scripts by `tools/fsk2anim.py`; the Fisk semantics of `animate2` and `curve` are assumed (unverified). The jackhammer is the Sind left-arm swap; `grab_hold` has no Sind source.
- Inside the Hulkbuster, slots 1–3 (UNIBEAM / MISSILES / NANO_ARSENAL actions) are grab / jump slam / hop; RMB tools JACKHAMMER ↔ HULK_REPULSOR; no flight (`wantsFlight` false while any part is on).

## Iron Man fixes, iteration 1 (2026-10-10)
- Sounds: only the thruster loops are synthesized; the rest is built from Kenney CC0 packs by `tools/sfx/ironman_cc0.py`. Beeping events (fizzle, JARVIS chime and voice, scan) have empty sound lists on purpose. Film audio is not used (copyright).
- First-person shield guard: `IronManFirstPerson` + `IronManFirstPersonShieldMixin` (renderArmWithItem after pushPose). The quaternion and translate were solved against the vanilla FP arm transform so the plate faces forward at view (-0.42, -0.30, -0.85); unverified in game.
- Empty suit exit: the shell opens on hinges (`tools/assets/opening_shell.py`), the player renders as Tony with no plates during EXITING, and Tony walks out (`IronManMarks.walkOut`, ticks 8-20). No launch, no `suit_expulsion`.
- Per-call `Mth.lerp(k, …)` smoothing in pose code runs twice per frame with the shader shadow pass and follows the frame rate: always ease by `System.nanoTime` dt.

## Rendering gotchas
- Through-wall entity highlights: use the vanilla glowing path. Per-entity colour, client-only: mixin `Minecraft.shouldEntityAppearGlowing` → true and `Entity.getTeamColor` → colour (guard `level().isClientSide`), see `OutlineGlowMixin` / `OutlineTeamColorMixin`. Sources register in `client/render/vfx/OutlineTargets` (first registered source that returns a colour wins; Homelander focus first, Iron Man scan highlight next). Drawing into `outlineBufferSource()` by hand is unreliable (the outline post pass runs only when some entity glows). `RenderStateShard` constants (`NO_DEPTH_TEST` etc.) are protected and unavailable to mod code.
- World position of a model part (eyes for beams): a `RenderLayer` on `PlayerRenderer` (`EntityRenderersEvent.AddLayers`), `model.head.translateAndRotate(poseStack)`, transform the point by the pose, undo the view with `RenderSystem.getInverseViewRotationMatrix()`, add the camera position. Skip `ShaderCompat.isShadowPass()`. Valid for the current frame only (see `HomelanderEyesLayer`). `getEyePosition` is wrong in flight poses.
- Emissive bits on the model (glowing eyes): draw quads in that layer with `RenderType.lightning()` (position+color, additive), just in front of the hat layer (z -4.6 px on the face).
- Soft trails that must not look neon: `SRC_ALPHA, ONE_MINUS_SRC_ALPHA`, ribbons with alpha 0 at the edges, slow motion; route geometry simplified (Douglas-Peucker) and Chaikin-smoothed, recomputed rarely with a cross-fade (see `FocusRouteRenderer`, `RouteCurve`).
- Client decals on blocks (scorch) must be dropped when the block is gone: check the collision shape behind the face periodically.
- `SilhouetteManager.getState(entity, shouldDraw)` consumes a per-frame delta (max 0.1 s). Call it at most once per entity per frame; a second caller zeros the first caller's alpha lerp.

## Environment and shipping
- The sandbox Gradle cache can be wiped between turns. If `./gradlew build --offline` fails on the foojay plugin, run one build online (no `--offline`), then offline works again.
- No git push credentials: push with GitHub MCP `push_files` (text only; binaries as `binassets/**.b64`).
- Releases: a temporary step in `.github/workflows/build.yml` (`permissions: contents: write`, `gh release create` with the CI-built jars, guarded by a `[release-vX]` marker in the commit message) pushed to `main`, then a follow-up commit restores the workflow. Used for v1.13.0-regulus and v1.14.6-homelander.
- Codex PR review has a usage limit; when it is reached it only posts a limit notice.

## Topic files
- `decompile.md`: decompile artifacts and their fixes.
## Iron Man polish (core 1.24.1)
- RMB: any item in either hand keeps vanilla use (`IronManHero.handsBusy`); NANO_BLADE has no RMB (dash removed from input).
- Pages: 0-3 combat (Unibeam, missiles, arsenal, countermeasures), 6-9 scan, Veronica, helmet, suit. `HeroDefinition.previousDefaultLoadouts` + `HeroRegistry.isPreviousDefault`: an untouched old default is reset to the new one on login.
- Icons: `tools/assets/make_ironman_icons_v3.py` (supersedes make_ironman_icons/stage2/4/5 icon art), renderer `tools/assets/icon_figure.py`.
- Scan locks once (`ScanProgress` lock + `IronManJarvis.lockCandidate`, cone `SCAN_LOCK_CONE_DEG`), no aim needed after.
- Suit stats: `SUIT_HEALTH_BONUS` max-health modifier while armored; nano/mark armor and toughness doubled (armor capped 30), Hulkbuster 800/30.
- Mark faceplate: rigid 90° turn about head centre (geo pivot 0,28,0); glow layer uses `HelmetAnim.progress` like the skin; `HelmetAnim` snaps (no close replay / gesture) for `SETTLE_TICKS` after a mark is fully on.
- Empty-suit entry: `EmptySuitEntity` stays `sealed` for `SEAL_LINGER` ticks after `finishEntering` (snapshot synced at once), so its removal never beats the owner's mark snapshot (no one-frame Tony skin).
## Iron Man review fixes (core 1.25.0, flight 1.10.1)
- Hero chassis: `HeroDefinition.chassisStats(Player)` → `HeroChassis(baseDamage, damageReduction, damageIgnoreThreshold)` overrides the Viltrumite `PlayerStats` getters (`PlayerStatsMixin`). Homelander = 6 / 40% / 0.5 (was Viltrumite 19 / 97%); null = Viltrumite stats.
- Mark armor: `MarkSpec.plating` = share of absorbed damage taken as durability (`MarkDamage.apply(durability, amount, plating)`, min 0.2). Lower = tougher. Suit armor values are never lowered (user rule).
- Iron Man always has a hidden ambient Regeneration II (`HeroPassiveEffect`, refreshed in `IronManHero.tick`, removed on hero change).
- Owner sections (ESP/scan boxes, threats, marks): `OwnerSections` is cleared/re-sent on respawn, dimension change, logout (`ClientHeroData` clears on `LoggingOut`/`Clone`). Never keep client highlight state past those events.
- AoE targets go through `IronManTargets.canHit` (allies and PvP-off players are skipped).
- Shield perfect block: window counts from the first raise; re-raise needs `PERFECT_REARM_TICKS`.
- Hulkbuster grab lasts at most `GRAB_MAX_TICKS`.
- Glide (0-energy) cannot climb: the flight mixin clamps a glide tick to `startY - glideSink` (vanilla Space lift).
- Client float animation time: `client/anim/RealTime.seconds()` (wraps, keeps float precision).
## Reinhard stage 1 (foundation, no jar until 1b)
- Unblockable seam: `HeroDefinition.clampUnblockableDamage(player, source, amount)` is the only hook for `bypasses_invulnerability` hits (void, `generic_kill`); default unchanged. `/kill` runs inside `AdminKill` (ThreadLocal depth, `KillCommandMixin` redirect of `Entity.kill` in `KillCommand.kill`) and `HeroDamageLayers` skips the hook there.
- Divine: damage type `viltrumitecore:divine` in damage-type tag `viltrumitecore:divine` + vanilla `bypasses_armor/shield/enchantments/effects` (never `bypasses_invulnerability`); entity tag `viltrumitecore:divine`; `DivineTargets.verdict`; Regulus Lion's Heart = `isAbsolutelyInvulnerable`. Iron Man marks / Hulkbuster still absorb divine hits (they skip only `bypasses_invulnerability`).
- Unit tests cannot load classes with `Registries` / `TagKey` / `ResourceKey` statics (no Bootstrap): keep pure rules in nested classes (`DivineTargets.Verdict.of`).
- Reinhard: page = mode (`ReinhardMode.page()`), MMB = `MODE_CYCLE` (3 s cooldown, `extraCooldowns[14]`), mode in `heroFlags` bits 0-1. `ReinhardAbility` ordinal = `extraCooldowns` index; `ReinhardAbilities.SHIPPED_STAGE` gates loadout/owned ids (bump every stage). `HeroPublicSnapshot.availableActions` is a `long` mask now (actions ≥ 32 exist).
- Knight HUD: `PanelStyle.drawAttachments`; mode icons sit LEFT of the ability bar (the bar is 5 px from the screen edge). Icons: `tools/assets/make_reinhard_icons.py --assets <unpacked reinhard_assets>`, sources in `tools/assets/reinhard_sources.md`.
## Reinhard stage 1b (visuals, first jar)
- Model: `geo/reinhard/{reinhard,reinhard_arms,sheath,blade}.geo.json` from the archive via `tools/assets/convert_reinhard.py` → `tools/obj2geo.py` (rotated boxes become cube rotation/pivot; sword OBJs: drop `dragon_glow` overlay faces, vt from the top). Sword origin = hilt guard; sheathed blade offset (0, −14, 0) (`ReinhardAssets.SHEATHED_OFFSET` + bone `sheathed_offset`).
- `ReinhardRenderer` (RenderPlayerEvent.Pre, always cancels for Reinhard, invisible included) + `ReinhardPoses` (pure) + `StanceBlend` + `ReinhardSword` (left hip, hilt up; quality-rework mount is a candidate, not native-accepted) + `ReinhardAuraVfx` (Knight wind motes, Holy Sword gold sparks). Clips: `tools/assets/reinhard_walk2anim.py` (walk = vanilla swing, 0.6662/(2π) s per walk unit), `tools/assets/make_reinhard_clips.py` (stances, knight hold, crouch/sit additive, swim).
- Shared additions: `AnimationController.overlay(…, weight)` / `additive(…)`, `HeroHeadPreviews` (race screen heads; fallback = flat skin face), `EntityRendererNameTagInvoker`, `AlwaysVisibleOffhandMixin` term `ReinhardFirstPerson.wantsOffhand`.
## Reinhard stage 2 (blessings, core 1.27.0)
- Blessings are passive, every mode (`BlessingModeMatrixTest`: no blessing rule takes `ReinhardMode`). Pure rules in `hero/reinhard/blessing/` (`Phoenix`, `Adaptation`, `Parry`, `ArrowWard`, `CloudStep`, `WindReading`, `Detox`, `ManaAbsorption`), numbers in `ReinhardRules`, visual constants in client classes. `SHIPPED_STAGE = 2` (no abilities yet, all are stage ≥ 3).
- New seam hooks (defaults = vanilla): `airJumps` / `airJumpVelocity` (both sides) + `onAirStep`, `projectileResponse` (`HeroProjectiles`, `ProjectileImpactEvent` → `SKIP_ENTITY`), `blocksEffect` (`MobEffectEvent.Applicable` DENY, both sides), `foreignResourceRegenFactor` (`HeroResourceRegen.scale`, Iron Man energy refill and Homelander cooling multiply by it). New common mixin `AbstractArrowAccessor` (CATCH, used from stage 3).
- Air jumps are client-authoritative (`AirJumpClient`); the server only counts: accepted = no motion packet (avoids a double boost), refused = motion `(x, min(y,0), z)` + `AirJumpSyncS2CPacket`. `HeroAction.AIR_STEP` routed like `JUMP`.
- `PersistentCooldowns` (player persistent NBT `ViltrumiteCooldowns`, online ticks only) holds the phoenix cooldown across death/hero change; Adaptation is dropped on respawn.
- Owner sections: Reinhard uses `THREATS` (wind reading outlines via `WindReadingClient`, colour -1 = not ours) and new `MANA_LINKS` (blue motes between drained pairs).
- Parry = `ReinhardFlags.PARRY_POSE`; client `ParryPulse` weight drives the `parry` overlay clip and a first-person main-arm `ModelPart` flick (no mixin).
## Reinhard stage 3 (Restrained + Knight abilities, core 1.27.0)
- `SHIPPED_STAGE = 3`: nine abilities (Restrained: Light Punch, Step Dash, Catch; Knight: Fist Combo, Sky Kick, Grab & Throw, Dash Behind, Stomp, Reflect). Router `hero/reinhard/ReinhardActions` (pure `gate`: control → mode → cooldown → busy/timeline) → `ability/*` `AbilityHandler`s; one `ActionTimeline` (ability, length, target, phase) drives the snapshot (`actionId`, `variant` = phase, `controlTargetId`), clips (`client/reinhard/ReinhardClips`) and the first-person table (`ReinhardFirstPersonPose`). Mode change / control / cleanup cancel everything.
- Hits go through `ability/Strikes` (i-frames skipped, vanilla knockback when `allowsImpulse`) and `ReinhardTargets.canHit`; destruction only via `DestructionLevel.of(mode).scaled(spec)` (Restrained none, Knight moderate ≤ r 4 / depth 1 / 40 blocks), blocks only, `HeroDestruction` rules honoured.
- Predicted on the client: Step Dash (`StepDashClient`, press through the generic `HeroInputC2SPacket`) and Dash Behind (`DashBehindClient` → own `ReinhardDashBehindC2SPacket`, the slot is left out of `heroInputSlots`; server re-checks page, finite coords, target ≤ 26 and on its own look ray (`DashBehind.onCrosshair`, ±1 block), shared `hero/SafeSpot`; refusal = snap back, no cooldown; every press is answered by `ReinhardDashBehindAnswerS2CPacket` with the server cooldown, which replaces the client's predictor cooldown).
- Windows: Catch (10 t, CATCH) and Reflect (20 t, REFLECT beats catch) answer `projectileResponse` through `ArrowWard.response`; flags `CATCH_WINDOW` / `REFLECT_WINDOW`. Grab & Throw holds with a PULL control of the level it started in (`GrabThrow.Control` seam), throw ~120 blocks by re-applying 3.9 for 21 t; flag `GRAB_HOLDING`.
- Shared FX: `HeroFxS2CPacket` kinds `HIT_STOP` / `SONIC_RING` / `AIR_TRAIL` (`HeroFx.hitStop/sonicRing/airTrail`), client `HitStop` + `AirTrailVfx`. Icons `make_reinhard_icons.py`, clips `make_reinhard_clips.py`; `ReinhardClipsTest` checks every shipped ability's clips.

## Reinhard stage 4 (Holy Sword: Reid, Lethal Chop, Sheath Strike, Rain of Cuts, core 1.28.0)
- `SHIPPED_STAGE = 4`. Reid (`hero/reinhard/reid/Reid`): sheathed → drawing (6 s, `ReidDrawEvent` world scene) → drawn (auto-return after 60 s without combat, `combat()` resets) → sheathing (1 s); snapshot flags `REID_DRAWING/DRAWN/SHEATHING`; pure slot rule `Reid.allows` (Sheath Strike only sheathed; Rain of Cuts / World Cut / World Erasure only drawn). Leaving Holy Sword sheathes.
- Draw scene: server `ReidDrawEvent` (gravity lift r 96 up to 2.5–3 blocks through `LiftLeases` + `GravityLift`, wave r 24 at the end, chat + far sound to the server), client `ReidDrawScene` + `ReidDrawSceneVfx` (shake, `SkyTint` dark sky, `SoundDuck` 25 %, cracks, pillar, funnel) via `ReidSceneS2CPacket`. Drawn: `ReidAura` (gold shell, motes, warm sky r 64), blade in the right hand.
- Reid strike = `hero/Annihilation` (kills mortals; divine / absolutely invulnerable untouched). Lethal Chop = the kit chop through `HeroDefinition.chopStyle` (`hero/ChopStyle`) + `allowsKitAbility`; Reinhard chop 35 + bleed, forced left with Reid.
- Sheath Strike (r 6, 20, FREEZE 3 s when `allowsExternalControl`, cd 8 s; stars `SheathStunStars`). Rain of Cuts (12 cuts ±15°, 40 blocks, `Annihilation.strike`, 1×3 grooves through `CarveJobs` / `HeroDestruction.clearBlock`, cd 15 s).
- Not checked: Oculus shaders with `SkyTint`, dedicated server start, in-game look (list in the PR).

## Reinhard stage 5 (World Cut, core 1.29.0)
- WorldCutManager owns detached casts by ServerLevel; END ticks advance the server game-time clock before CarveJobs runs. Death, logout or a changed caster dimension does not cancel a started cut. Unloaded levels and server stop discard unpersisted casts/jobs. Offline/dead/other-dimension attribution is null; Annihilation reads the victim's level.
- Terrain heights freeze at press from loaded centre columns (WORLD_SURFACE); pitch does not rotate the vertical trench. Unloaded columns use cast height and remain skip-only unless loaded naturally. Bounds/build height clip the carve; no chunk force-load.
- WorldCutGeometry.frontAlong is a count of consumed slices, not the zero-based last index. WorldCut uses that count directly; hits follow the greater of carve and timed visual fronts, once per resolved UUID, without ally/PvP filters. Divine and absolute verdicts remain in Annihilation.
- Flags alone do not suppress old-block onRemove: chest/furnace callbacks drop inventory/XP; redstone callbacks notify neighbours and schedule adjacent fluid ticks (mapped 1.20.1 bytecode checked). QuietBlockRemoval scope + common QuietBlockRemovalMixin skip those callbacks only for clean air replacement and remove block entities without drops. Ordinary destruction and debris flags 3 keep their callbacks. Runtime Mixin application still needs the manual server smoke.
- Clean carve flags = HeroDestruction.CLEAR_FLAGS (UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS). The debris overload uses the same source flags. There is no final neighbour sweep: water/lava are intended to stay still at the cut until external neighbour updates occur. This is not a guarantee against other world activity. Actual fluids and lighting performance are unverified in game.
- WorldCutScene keeps the fixed packet frame independently of the caster snapshot; game-time pixels never wait for blocks. Duck/FOV end on blast/timeout/unload. Ordinary CameraShake contributions cap at 2.5. Explicit cinematicAt contributions use the same accumulator with their own cap and local setting. Scene lifetime follows actual queue DONE; pure budget checks do not measure TPS.

## Reinhard stage 6 (World Erasure, core 1.30.0)
- `SHIPPED_STAGE=6`: all fourteen abilities; Holy Sword slot 6 requires drawn Reid. One server-wide `WorldPause`, target ray 64, 150-tick scene, once-only removal at 90, cooldown 1200. Outcome is frozen at cast (divine / absolute targets stay but spend cooldown). Absent target at removal escapes; later rejoin never triggers a second attempt. Every dimension gets packet server age, not a subtraction of world game times.
- WorldPause is level-owned FREEZE refreshed to now+5 at server END; foreign controls aren't replaced/released. Every non-caster online player is input-blocked/protected; nearby Enemy mobs within 32 of those players are included. Owner stays at the cast position, not immune by this seam. End/death/logout/dimension/hero change/reload/stop/watchdog release tracked controls; client has its own watchdog. Stage 4 CarveJobs/CarveBudget/LevelTickPhases stay unchanged.
- Mob discard is not a death. QuietDragonCompletion uses narrow common EndDragonFight accessors to mark defeat without terrain/rewards: **NO first-kill portal/gateway/egg**. Vanilla setDragonKilled changes blocks, so it cannot satisfy erasure's unchanged-world rule. An erased first dragon can leave no normal End exit; verify an existing exit or another permitted return method before using it.
- Players keep the same ServerPlayer: ERASED explicitly resets transient state using each hero's death-copy policy; persistent cooldowns/session/Phoenix, Regulus cooldowns, Iron Man roster/pod and Reinhard mode stay. Inventory/armour/offhand/XP are permanently erased (not stored); ender chest untouched. No real death/DEATHS increment/message/loot. Return invokes shared `freshBody`, including existing Evangelium refresh.
- `viltrumitecore:the_void` is a data dimension, all air/black fog/no sky/clouds. Persistent `viltrumitecore:limbo` (`ticksLeft`, `pendingStrip`) exists before queued END teleport and survives logout/restart; 600 online ticks only in limbo. BodyIsolation denies all damage before queues and flight/actions after the 150-tick pause too. Shared-spawn safe-column return never bed/anchor/platform; an invalid world spawn remains manual. Missing dimension refuses new casts and rescues already-tagged players. Existing worlds need restart to load the dimension.
- Client uses only existing dash_impact/anim/PixelVfx/SoundDuck/FlashOverlay: five zero-default erasure uniforms, sonic-only priority block, model mesh or metadata fallback silhouette/dust, 50 bpm logical heartbeat, five timed HUD lines, return flash. F1 hides the HUD, not the timer. All packet shape changes require matching core protocol-2 clients; flight stays 1.10.1.
- Automated source/geometry/codec/lifecycle tests are NOT proof of actual Forge events, server startup, renderer/GLSL/mixins, old-world/restart persistence or FPS/TPS. Runtime and shader-pack checks were forbidden; retain the full manual checklist and Rulings/costs in the Stage 6 journal/PR. Stage 7 replaces the allowed selected audio; its native acceptance remains open when game/server runs are forbidden.

Stage 6 final review fixes: server UUID/dimension position overlay freezes non-caster players even when control acquisition is refused; suspend foreign carry/PULL/legacy grind without stealing ownership. Pause originals persist in `PauseFlagRecovery` and participants release before vanilla logout save; isolation/foreign owners retain their flags. Pending strip clears capacity/cursors, closes and clears returned personal inputs (one transaction); external storage untouched. Client watchdog stops with actual game pause. Deferred legacy metadata/dev-command duration and all native proof boundaries are documented in the Stage 6 ledger/PR; build tests do not certify runtime.


## Reinhard stage 7 (sounds, core 1.31.0; flight unchanged 1.10.1)
- Owner exception allows CC0/CC-BY for Reinhard, not NC. Live-page check 2026-10-13 found CGEffex 93835 and Robinhood76 378199 **CC-BY-NC 4.0**. Do not accidentally include them from the complete user archive. The cinematic overhaul replaces blast/rain placeholders with original procedural CC0 audio; no excluded recording or silent Alliare-only substitution is used. Sources/license links and input digests: `tools/assets/reinhard_sources.md`.
- The existing heartbeat clock is 24 client ticks (50 bpm), independent of source length; the source recording has a slower native repetition. The processed 1.2 s pulse fits that clock without changing gameplay constants. Fade/endpoint metrics do not prove perceptually seamless loops or correct runtime stopping.
- Short high-crest clips can be peak-limited below the LUFS goal even with measured two-pass loudnorm. Condition selected transients before measurement; measure **decoded Vorbis** too. Preserve both LUFS and true/sample peak gates, not merely encoder settings. Pinned first-pass values depend on the approved source digests and ffmpeg build.
- §10.1 covers only 16 output recipes, not every one of the 33 Stage 6 events. Two outputs are license-blocked, 12 extra cues have no selected source, and five events reuse §10.2. Do not equate 14 final recordings with a fully accepted hero. No native listening/server/GLSL/network/save/TPS proof in Stage 7; retain SESSION/PR manual gates and Stage 6 known limitations.

- Attribution-required audio must carry author/source/license/change notices in the standalone core JAR. `processResources` copies maintained credits/source tables into `META-INF/credits/`; `mods.toml` exposes those audio exceptions without replacing the code license. A root-only CREDITS file is not a distribution guarantee.


## Reinhard rendering invariants discovered during the quality rework
- Stateless AnimationController seek/overlay/blend sample raw seconds without loop wrapping; a LOOP asset alone does not keep walking/breathing alive. The bug was verified with actual renderer composition beyond the first cycle. Keep Reinhard-only sampling in ReinhardPlayback until a deliberate shared-core change.
- Sword archive coordinates: the sheath assembly is rotated Z 180 to put the hilt up. Therefore extracting the blade toward the hilt subtracts from its local Y offset; adding to Y pushes it farther into the sheath. Native hand-transfer alignment is still unverified.
- A PULL lease owns control precedence, not hand attachment. Explicit held-target ownership now goes through HeroDefinition. Server movement suppression must run on the server thread; per-tick connection teleports restart player prediction. Use a shared neck anchor and one release correction. Native network smoothness remains unverified.

- FP geo/item conversion: PlayerGeoLayer uses vanilla arm frame → negative vanilla pivot → Y +1.5 → scale(-1,-1,1). Follow the geo shoulder/elbow from that frame, then HeldItemAnchor.local. Attaching directly to ModelPart ignores the elbow. Neutral-grip matrix equality is verified; native visual acceptance is not.

## Hero keyboard edge ownership
- heroInputSlots lists all abilities across pages, not only six equipped keys. Do not cap its iteration to a fixed array size. Reinhard's late Holy Sword inputs were silently dropped by the old eight-slot cache.
- Track edges by ability ID and context (hero, player session, active page and bindings). Seed held keys after a context/focus switch; do not cast the new ability on the old held physical key. Release already-sent channels on same-hero page/focus changes. Never replay old-player or old-hero edges into a new owner.


## Reinhard second-pass invariants (core 1.34.0)
- Retired R actions remain enum IDs for wire stability, but are not owned, shown
  or input-routed. Restrained/Holy ordinary LMB has a server contact timeline;
  Knight LMB is one step per press plus one next-hit buffer. Stray vanilla entity
  hit packets are rejected; only the ordinary contact callback admits Player.attack.
- HeroDefinition movement/mouse policy hooks default to legacy behaviour. Cloud
  Step opts into bounded forward steering through BOTH predicted and server seams.
- Pose overlays must preserve absent channels; crouch/shoulders/head share waist
  ancestry. Do not subtract the transient shared HitStop counter from Reinhard's
  authored action clock. A raw item-frame equality can still put a grip above the
  shoulder: constrain to measured native glove geometry, with an elbow pole.
- Active heavy-scene locks are distinct from short cooldowns. World Cut guard
  follows actual queue ownership (including failed-job removal), not a stale work
  counter. HUD uses the same level/server scene admission flags.
- Build/CPU geometry checks are not native proof. Client GLFW init and the EULA
  barrier still prevent gameplay/server acceptance in this environment.

- Reinhard grip-point equality hid two visual defects: wrong wrist roll and a
  blade path through hair. Resting palm contact must not inherit sheath roll.
  TP equipment now has an authored grip bone in the existing animation core;
  wrists follow its reachable path, then rendering reuses those exact frames.
  Full-mesh CPU checks include intermediate crouch/draw/action frames. They do
  not certify native visuals. A belt carrier bridges the coat flare.

- Reinhard's TP reid_grip frame and FP camera held-item frame use different roll bases. A universal local-Y quarter-turn exposes the wrong TP edge. Test the blade face normal and inspect all three views. Two-hand targets must be distinct and reachable; pronate the forearm about elbow-to-palm rather than dislocate the wrist. Do not alter the sheath to correct a drawn-blade orientation.

- Reinhard drawn blade orientation: native longitudinal axis is Y, width X, broad normal Z. In the raised TP World Erasure pose the face is already 10 degrees from front. A +90-degree Y roll exposes the edge; it does not create the anime diamond silhouette. The native asset is a narrow straight blade. Fixed-pose quad cross-sections distinguish a shape limit from an orientation bug. Do not resize or repose to conceal it; get owner approval before changing the blade shape.

- Owner superseded the wide-blade direction: cancelled the +35% silhouette and explicitly selected the bottom +90-degree axial diagnostic variant (front edge, broad face at the side). Keep the original blade asset, original body/hand poses and sheath. TP rolls only its final free-blade frame after the existing grip solve; FP keeps its existing roll.


## Detached cut and wire invariants
- A completed damaging wave and completed terrain work are different events. Keep
  scene admission and atmosphere until the queued carve leaves CarveJobs; do not
  extend the damaging wave while a slow queue drains.
- Reinhard wire cooldown slots 14 and 15 remain mode/Phoenix. Appended abilities
  use explicit snapshot indices after them; an enum ordinal is not a wire slot.
- Full-mesh intermediate checks caught an unreachable diagonal grip and forearm
  through the head even though endpoint frames looked valid. Keep reachability and
  SAT checks at intermediate times, both crouched and standing; do not relax them
  to approve a more dramatic roll. Native first-person/VFX acceptance remains open.

- A cooldown enum-to-wire mapping test does not prove snapshot production. Test
  the production composer through encode/decode with every ability and reserved
  slot populated. The shared wire cap can lag an appended ability even when
  its explicit index is correct. A capped decoder must locate trailing fields
  using the declared wire count, not the retained local count. Older peers can
  misread those fields; reject them when increasing the supported capacity.

- Dense gait keys plus per-segment ease-in/out produce near-zero velocity at
  intermediate swing points, even if all key poses and loop endpoints are right.
  Reinhard's velocity regression samples the production parser, not just JSON.
- A snap -> ready switch needs the guard underneath the outgoing snap. Fading
  only the snap to zero reveals idle and creates a jump when ready replaces it.
- Reid body fades must exclude reid_grip, which owns the exact weapon path.
  FP camera offsets and elbows need their own sheathed-boundary envelopes.

- Core 1.35.3 combat follow-up: Reinhard FP reuses FirstPersonArm's outer
  shoulder transform instead of local Euler addition; authored right-arm action
  suppresses vanilla swing. Forge-cancelled and normal item paths apply once.
  Shared ActionClock has an opt-in bounded monotonic snapshot sampler; legacy
  tick/time users unchanged. Shared parser accepts opt-in monotone cubic keys;
  only Reinhard strikes use them. Contact ticks, draw path and blade roll stay
  unchanged. Runtime acceptance must be reported separately from CPU gates.
- Sword grip continuity gate: paired forearms preserve the directed handle axis
  (old nearest-sign choice flipped 173 degrees during a jab). Wrist limit fades
  its antipodal correction instead of jumping +35 to -35 (old sheath strike
  jumped 69 degrees). These are arm-orientation fixes only: grip points, weapon
  matrices, sheath geometry and owner-selected +90-degree blade roll unchanged.

- Grip ownership blends reachable palm targets before the unchanged DLS solve;
  geometric seed preserves authored twist and blends the elbow pole. Separate
  paired pronation from leading-hand position ownership; no full twist at the
  first positive two-hand weight. Support elbow outward/front, leading elbow
  additionally down: a symmetric pole passed continuity but clipped the head.
  Keep BOTH final-bone continuity and full-mesh collision gates.
