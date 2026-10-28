# Hero seam

How a hero plugs into `viltrumitecore`. Paths are under `viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/` unless noted.

## 1. `hero/HeroDefinition`

Required:

| Method | Meaning |
|---|---|
| `HeroId id()` | enum key |
| `allowsFlight(Player)` | mod flight allowed (also gated by `HeroRegistry.installFlightPolicy`) |
| `allowsLegacyAbilities(Player)` | Viltrumite ability pipeline (punch, clap, dash) |
| `ownsAbility(String id)` | ability id belongs to this hero |
| `allowsExternalControl(LivingEntity, ControlKind)` | can others grab/freeze/anchor this player |
| `canAct(ServerPlayer, HeroAction)` | gate for every input action |
| `tick(ServerPlayer)` | server tick |
| `handleInput(ServerPlayer, HeroAction, boolean pressed)` | key input (JUMP is handled by `HeroSuperJump`, not here) |
| `enter(ServerPlayer)` | hero chosen |
| `snapshot(Player)` | `HeroPublicSnapshot` synced to clients |
| `cleanup(ServerPlayer, CleanupReason)` | stop everything (death, logout, hero change …) |

Default hooks (override only when needed):

| Hook | Default |
|---|---|
| `allowsAbilityPages`, `hasAbilityPanel` | panel visibility |
| `meleeDamageFactor(Player)` | 1.0 |
| `cancelsFallDamage(Player)` | false |
| `onLanded(ServerPlayer, float fallDistance)` | nothing; called from `HeroEvents.onLivingFall` |
| `lines()` | `HeroLines.NONE`; spoken lines for cinematic subtitles (`hero/speech/`) |
| `superJumpVelocity(Player)` | 0 (no super jump); both sides |
| `onSuperJump(ServerPlayer)` | nothing (hero sounds) |
| `preventsExhaustion(Player)` | false |
| `allowsLegacyAbility(Player, String id)` | `allowsLegacyAbilities && ownsAbility(id)`; both sides, read through `LegacyKit.allows` — a hero keeps a subset of the Viltrumite kit by owning those ids |
| `chopStyle(Player)` | `ChopStyle.DEFAULT` (legacy chop look/sound); both sides, read cheaply per frame by the chop VFX/sounds. `ChopStyle(forceLeft, arcScale, trailInner, trailOuter, ripple, soundVolume, soundPitch)`: volume/pitch multiply the two legacy chop sounds; `forceLeft` is applied by the server in `ChopC2SPacket` (`ChopStyle.resolveLeft`). Reinhard: 2× white-crimson arc with a ripple, always left while Reid is out |
| `abilityIcon(String id, Player player)` | `abilityIcon(id)`; per-player icon (Reinhard swaps Reid → `reid_drawn` from the synced flags) |
| `heroInputSlots()` / `heroActionFor(String id)` | none; slot ids whose keys send `HeroInputC2SPacket` press/release (generic client loop) |
| `onHurt(ServerPlayer, DamageSource, float)` | nothing; from `HeroEvents` LivingHurtEvent |
| `onDimensionChange(ServerPlayer)` | nothing; stop channels |
| `onRespawn(ServerPlayer)` | nothing; fresh body after `PlayerRespawnEvent` (death and End return). `restoreHero()` on clone skips `enter`, so re-apply chassis modifiers here; must be idempotent |
| `abilityIcon(String id)` | null; hero-specific icon for any slot (kit slots drawn as this hero), read by panel and HUD via `ViltrumiteAbility.getIcon(player)` |
| `saveHeroState` / `loadHeroState` / `cloneHeroState` | drop state |
| `grantsFlightAbility(Player)` | false; vanilla `mayfly` for heroes without the legacy kit (`HeroFlightGrant`, own NBT marker) |
| `flightProfile(Player)` | null = legacy flight; else `FlightProfile` (speedMul, throttle up/down, lock cap, inertia, turn rate, hover damping, glide); both sides |
| `absorbIncoming(ServerPlayer, DamageSource, float)` | `DamageAbsorb.PASS`; LivingAttackEvent, before armor (shields, layers) |
| `clampFinalDamage(ServerPlayer, DamageSource, float)` | unchanged; LivingDamageEvent, after armor (HP floors) |
| `clampUnblockableDamage(ServerPlayer, DamageSource, float)` | unchanged; the only hook that sees `bypasses_invulnerability` hits (void, `generic_kill`), from `HeroDamageLayers` (LivingDamageEvent and the clean path). Never called inside an `AdminKill` scope (`/kill`, `KillCommandMixin`), so an HP floor here cannot block `/kill` |
| `isDivine(Player)` | false; a divine being: only divine damage (`viltrumitecore:divine`, `DivineDamage`) hurts it in full, execute-style powers skip it. Read through `DivineTargets.isDivine` (entity tag `viltrumitecore:divine` for mobs) |
| `isAbsolutelyInvulnerable(Player)` | false; immune even to divine damage (Regulus with an active Lion's Heart). Read through `DivineTargets.isAbsolutelyInvulnerable` (part entities use the parent) |
| `modifyOutgoingDamage(ServerPlayer, LivingEntity, DamageSource, float)` | unchanged; this hero as attacker |
| `mouseAction(MouseButton, Player)` | null = vanilla; claimed buttons go through `HeldInputs` (press gated once, release always routed); both sides |
| `onInputRefused(ServerPlayer, HeroAction)` | nothing; feedback when a claimed press fails `canAct` |
| `guardAction(Player)` | null = vanilla swap-hands key (F); non-null: client consumes the key, edges go through `HeldInputs` as `MouseButton.GUARD`; both sides |
| `blocksHandSwap(Player)` | false; server cancels `LivingSwapItemsEvent.Hands` (also forged packets) |
| `abilityIcon(String, Player)` | `abilityIcon(String)`; icon of a slot for this player now (Iron Man slot 3 = the worn mark's signature) |
| `bodyScale(Player)` | 1; hitbox and eye height factor, both sides (`HeroSizeEvents` on Forge `EntityEvent.Size`; call `refreshDimensions()` on change, the client refreshes in `BodyScaleClient`); check room with `BodyScale.findFree` before growing |
| `flightSpeedScale(Player)` | 1; factor on the legacy (no profile) CRUISE/SONIC speed, both sides (flight `FlightProfiles.setSpeedScaleResolver`) |
| `hiddenFromFocus(Player)` | false; true = Homelander focus skips this player (Mark 15) |
| `scanInfo(Player)` | `ScanInfo.EMPTY`; server-side extra lines for the Iron Man scan card (`protections` / `weakSpots` / `conditions` as translatable `ScanLine`s). Read by scanners on the server only; never send secrets the player could not see |
| `hiddenFromScan(Player)` | false; true = the scan skips this player (no progress, no card) |
| `airJumps(Player)` / `airJumpVelocity(Player)` | 0 / 0.0 = no air jumps; both sides. Count and vertical speed (b/t, horizontal kept) of jumps in the air until ground, water, lava, a ladder, flight or a mount refill them (`AirJumpRules.resets`). Client `AirJumpClient` moves the player (client-authoritative, like `SuperJumpClient`) and sends `HeroAction.AIR_STEP`; the server `HeroAirJump` counts presses, refuses extra ones with a motion + `AirJumpSyncS2CPacket` correction, and sends the step ring to trackers. `AIR_STEP` is routed by `HeroInputC2SPacket` like `JUMP` (not through `onHeroInput`) |
| `onAirStep(ServerPlayer)` | nothing; server, after an accepted air jump (hero sound/extras; the shared ring is drawn by clients) |
| `projectileResponse(ServerPlayer, Projectile)` | `ProjectileResponse.PASS`; server, `ProjectileImpactEvent` on this hero (`HeroProjectiles`). `DEFLECT` (turned 60–90° sideways ×0.6, power vector of fireballs too), `CATCH` (arrow pickup item / trident to the inventory, else discarded), `REFLECT` (back at the owner, `setOwner(self)`). Enum is append-only |
| `blocksEffect(Player, MobEffectInstance)` | false; both sides, `MobEffectEvent.Applicable` → DENY (effect immunity) |
| `foreignResourceRegenFactor(Player self, Player other)` | 1.0; server, how this hero slows another player's own resource regen (0..1, strongest source wins, `HeroResourceRegen.scale`). Resource owners (Iron Man energy, Homelander cooling) multiply their regen by `HeroResourceRegen.scale(player)` |

Snapshots: `availableActions` is a `long` bit mask (`1L << HeroAction.ordinal()`, build it with `HeroPublicSnapshot.actionMask(...)`; `-1` = all; at most 64 actions, guarded by `actionCountFitsMask`). `HeroPublicSnapshot.extraCooldowns` (max `EXTRA_COOLDOWN_MAX`, per-hero meaning; Iron Man index 0 = nano-lost lock ticks, 1 = countermeasures cooldown, 2 = Veronica, 3 = mark signature). `variant` and `resource2` are generic hero-owned ints written after the extras (older encodings decode 0); Iron Man: `IronManVariant` (mark + parts on the body) and mark durability ×10. Owner-only data: `HeroRegistry.pushOwnerSection(player, OwnerSection, Section)` replaces one typed section (`CARRIERS`, `THREATS`, `MARKS`, `SCAN`, `MANA_LINKS`; enum is append-only, wire = ordinal); client reads `ClientHeroData.section(...)`.

New behaviour that shared code must ask about → add a new default hook here. Do not branch on `HeroId` in shared code.

## 2. Touchpoints for a new hero

| What | File |
|---|---|
| Hero id | `hero/HeroId.java` (append) |
| Input actions | `hero/HeroAction.java` (append) |
| Registration | `hero/HeroRegistry.registerDefaults()` |
| Hero code | `hero/<id>/` (`<Id>Hero`, `<Id>Rules`, `<Id>State`, `<Id>Abilities`, one class per ability) |
| Panel look | `client/<id>/<Id>PanelStyle` → `PanelStyles.register`; `drawFrames` / `drawSlot` / `drawCooldown` / `accentColor`, and `drawAttachments(g, abilityBar, player, snapshot)` (default nothing) for hero HUD next to the panel, drawn after both bars and only outside the fixed layout (the ability bar is 5 px from the right screen edge) |
| Ability panel slots, icons, grey-out | `hero/<id>/<Id>Abilities.panelAbilities()` registered from `ability/ViltrumiteAbilities.java`; icons via `abilityIcon` |
| Input sending | `heroInputSlots()/heroActionFor()` hooks (generic loop in `client/ViltrumiteCoreClient`), `network/packet/HeroInputC2SPacket.java` |
| Public sync | `hero/HeroPublicSnapshot.java` (generic `resource`, `resourceLocked`, `heroFlags`; encoded only when non-default), owner-only ids via `HeroRegistry.pushOwnerSnapshot` |
| Choosing the hero | `client/gui/RaceSelectionScreen.java` (+ lang `gui.viltrumitecore.race_selection.become_<id>`), items like `item/EvangeliumItem.java` |
| Skin | `client/<id>/<Id>Client` → `HeroSkins.register`; texture `assets/viltrumitecore/textures/entity/hero/<id>.png` |
| Client init | `client/ViltrumiteCoreClient.java` |
| Poses / VFX | `client/<id>/`, `client/render/vfx/` (follow `animation-system`) |
| Key mappings | `client/ViltrumiteCoreClient.java` (lang `key.viltrumitecore.*`) |
| Sounds | `ViltrumiteCore.SOUND_EVENTS`, `assets/viltrumitecore/sounds.json`, `src/main/binassets/**.ogg.b64` |
| Spoken lines / subtitle portrait | `hero/<id>/<Id>Lines` → `HeroDefinition.lines()`; `client/hero/speech/HeroPortraits.register` for custom models |
| Lang | `assets/viltrumitecore/lang/*.json` (all locales) |
| Mixins | `viltrumitecore.mixins.json`, `viltrumitecore.client.mixins.json` (only if no hook exists) |
| Tests | `src/test/java/dev/baranhan/viltrumitecore/hero/<id>/` |
| Design | `docs/design/<date>-<id>-design.md` |

## 3. Known legacy debt (Regulus branches in shared code)

These files check `HeroId.REGULUS` directly. When a new hero needs the same behaviour, replace the branch with a `HeroDefinition` hook instead of adding a second branch:

- `ability/ViltrumiteAbilities` — Regulus slot registration and grey-out.
- `client/ViltrumiteCoreClient` — Regulus punch key block (hero input slots are generic now).
- `client/gui/RaceSelectionScreen` — hero buttons and head preview.
- `client/mixin/GameRendererDashMixin`, `RegulusFovMixin`, `RegulusHudMixin`, `SilhouetteRendererMixin`.
- `client/render/vfx/RegulusActionVFXManager`, `RegulusLionVFXManager`.
- `hero/HeroDamage`, `hero/HeroEvents`, `item/EvangeliumItem`.
- `hero/HeroPublicSnapshot` — fields are Regulus-shaped (lion, madness, hearts, `COOLDOWN_COUNT = 6`).

Run `grep -rln "HeroId.REGULUS" viltrumitecore/src/main/java` to see the current list. `NoHeroBranchTest` holds the allowlist; shrink it when a file moves behind a hook, never grow it.

Reference new-style hero: `hero/homelander/` + `client/homelander/` (no new shared branches).

## Body loss without an entity clone
- `CleanupReason.ERASED` = death on the same entity, without a new entity clone. Treat it like DEATH. Never rely on the clone to drop transient state. `losesBody()` means DEATH or ERASED.
- `HeroEvents.prepareErasure` calls hero cleanup first, then releases held inputs, controls/projectiles and both legacy grab directions, resets motion and refreshes the same session's life totem. Persistent cooldowns stay. World-owned death-surviving domes/jobs stay.
- Hero cleanup uses its death-copy policy after world-side channel release. No new chassis removal is allowed for ERASED.
- `HeroEvents.freshBody` is the shared respawn/return path: baseline, pending flags, flight sync, owner re-send, hero.onRespawn, then the existing legacy Evangelium rule. Limbo calls it after returning to spawn.

The same entity must not inherit a previous body's channel, grab, action pose or flight state. Invoke `prepareErasure` before scheduling isolation, not on return. Death-copy policies keep session UUID / Phoenix / persistent cooldowns, Regulus cooldowns, Iron Man roster/pod and Reinhard mode; reset their transient body fields. Restore vitals and gravity at the shared world spawn before `freshBody`; do not call vanilla death/clone/statistics paths or remove the hero's chassis/flight grant.
