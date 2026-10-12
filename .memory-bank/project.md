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
- `ReinhardRenderer` (RenderPlayerEvent.Pre, always cancels for Reinhard, invisible included) + `ReinhardPoses` (pure) + `StanceBlend` + `ReinhardSword` (left hip, hilt up, scale 0.35, tilt Z −20 / X 50) + `ReinhardAuraVfx` (Knight wind motes, Holy Sword gold sparks). Clips: `tools/assets/reinhard_walk2anim.py` (walk = vanilla swing, 0.6662/(2π) s per walk unit), `tools/assets/make_reinhard_clips.py` (stances, knight hold, crouch/sit additive, swim).
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
