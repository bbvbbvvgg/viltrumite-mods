# Iron Man (stages 1b–5 and fixes)

Moved verbatim from `project.md` (core 1.59.0) to keep the main file short; stage 6 lives in `ironman-legion.md`.

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
- Size goes only through `HeroDefinition.bodyScale` (`HeroSizeEvents`, Forge `EntityEvent.Size`). The Hulkbuster scale is read from the public snapshot on both sides (`IronManHero.hulkBig` → `HulkbusterLayer.bigOnWire`), never the server live layer: both sides must size from the same synced data. Before growing, `IronManHulkbuster.freeSpot` checks the real final box (standing 0.6×1.8 × 1.7) with `level.noCollision`, searches 2 blocks, else refuses.
- Hulkbuster exit (`HulkbusterExit`): the yaw is frozen and quantised to the wire byte at the start; the big box lasts only until Tony steps out (tick 24, `bigOnWire` reads the exit tick from the scene word); the server and the owner client move the player on one pure path, so the exit ends on the step-out spot with no snap.
- Damage order: shield → Hulkbuster (whole hit, no spill) → mark → nano armor → Tony. The shield is ×1.5 inside.
- Grab and throw use `ControlManager` PULL (acquire / release with an effect id); the Viltrumite legacy grab is not reused (decompiled, unverified). Every end of a carry calls `IronManHulkbuster.releaseGrab`.
- Mark 48 art is Sind (native 68 px tall, drawn at 1.7 × 32/68). Clips are baked from the Sind `.fsk` scripts by `tools/fsk2anim.py`; the Fisk semantics of `animate2` and `curve` are assumed (unverified). The jackhammer is the Sind left-arm swap; `grab_hold` has no Sind source.
- Inside the Hulkbuster, slots 1–3 (UNIBEAM / MISSILES / NANO_ARSENAL actions) are grab / jump slam / hop; RMB tools JACKHAMMER ↔ HULK_REPULSOR; no flight (`wantsFlight` false while any part is on).

## Iron Man fixes, iteration 1 (2026-10-10)
- Sounds: only the thruster loops are synthesized; the rest is built from Kenney CC0 packs by `tools/sfx/ironman_cc0.py`. Beeping events (fizzle, JARVIS chime and voice, scan) have empty sound lists on purpose. Film audio is not used (copyright).
- First-person shield guard: `IronManFirstPerson` + `IronManFirstPersonShieldMixin` (renderArmWithItem after pushPose). The quaternion and translate were solved against the vanilla FP arm transform so the plate faces forward at view (-0.42, -0.30, -0.85); unverified in game.
- Empty suit exit: the shell opens on hinges (`tools/assets/opening_shell.py`), the player renders as Tony with no plates during EXITING, and Tony walks out (`IronManMarks.walkOut`, ticks 8-20). No launch, no `suit_expulsion`.
- Per-call `Mth.lerp(k, …)` smoothing in pose code runs twice per frame with the shader shadow pass and follows the frame rate: always ease by `System.nanoTime` dt.

## Iron Man polish (core 1.24.1)
- RMB: any item in either hand keeps vanilla use (`IronManHero.handsBusy`); NANO_BLADE has no RMB (dash removed from input).
- Pages: 0-3 combat (Unibeam, missiles, arsenal, countermeasures), 6-9 scan, Veronica, helmet, suit. `HeroDefinition.previousDefaultLoadouts` + `HeroRegistry.isPreviousDefault`: an untouched old default is reset to the new one on login.
- Icons: `tools/assets/make_hero_icons.py --hero ironman` (`ironman_icon_art.py`, house style, core 1.46.0). `make_ironman_icons_v3.py` / `icon_figure.py` and the stage2/4/5 icon art are superseded (v3 refuses to write without `--legacy-overwrite`).
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
- Hulkbuster first person: `HulkbusterFirstPerson` (camera placement before vanilla `renderPlayerArm` + arm rest pose) is shared by the renderer and `HulkbusterFirstPersonCoverageTest` (CPU replica of the hand pass; hand projection is a fixed 70 degrees, near 0.05, so FOV setting does not change it). Change the numbers only with that test.
- Glide (0-energy) cannot climb: the flight mixin clamps a glide tick to `startY - glideSink` (vanilla Space lift).
- Client float animation time: `client/anim/RealTime.seconds()` (wraps, keeps float precision).
