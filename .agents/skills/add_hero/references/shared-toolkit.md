# Shared hero toolkit

Hero-neutral code extracted from Regulus. Use it before writing new FX or physics. All paths are under `viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/`.

## Server

### `hero/fx/HeroFx` — impact FX to nearby clients
Sends `HeroFxS2CPacket` to players tracking the source; the client draws it in `HeroImpactFx`.

| Call | Use |
|---|---|
| `shards(source, origin, power, material, float[] ends, whizSound)` | burst of block-coloured shards (kick, punch impact) |
| `shockwave(source, origin, power, material, radius)` | ground ring + camera shake (landing, clap) |
| `slam(source, origin, material, radius)` | short heavy ground ring |
| `blade(source, origin, end, whizSound)` | air-blade trail origin → end |
| `launch(source, feet, power, material)` | take-off puff (jump, dash start) |
| `flash(source, at)` | white impact flash |
| `hitStop(source, at, ticks, entityIds...)` | freeze the listed entities on screen for `ticks` (client `HitStop`); ids travel as {hi16, lo16, 0} triples (`encodeIds` / `decodeIds`, pure) |
| `sonicRing(source, at)` | white expanding ring in the air (launch / throw) |
| `airTrail(source, entity)` | white streak following the entity for 40 t (launched body, returned projectile) |

`material` may be null (default stone colour). Sound args may be null.

### `hero/HeroDebris` — flying blocks
- `launchBlock(level, pos, state, velocity, hurtPerBlock, hurtMax)` — spawns a `FallingBlockEntity` (through `FallingBlockEntityInvoker`) with velocity, sets the source block to air, returns the entity. Velocity is rounded to 1/8000 so client and server match.
- `canLaunch(level, pos, state)` — destroyable (`HeroDestruction`), no fluid, no block entity, not a door/bed half.
- `record Eruption(radius, depth, ahead, maxFlying, speed, power)`, `scaled(s)` — shape of a ground eruption.
- `erupt(source, groundPos, forward, spec, entityDamage, spared)` — tears a cone of blocks in front of the source, hurts entities not matched by `spared`. Returns launched count.
- `inEruption(dx, dy, dz, fx, fz, radius, depth, ahead)` — pure shape test (unit-tested).

### `hero/HeroShockwave` — landing shockwave
- `record Landing(minFall, fullPowerFall, minRadius, maxRadius, minDamage, maxDamage)` with `power(fall)`, `radius(fall, mult)`, `damage(fall, mult)`.
- `land(player, fallDistance, spec, multiplier, spared, impactSound)` — damage + knockback in radius, FX, sound. Returns false under `minFall`.
- `groundUnder(level, player)` — block state for FX colour.
- Call from `HeroDefinition.onLanded`. Example: `RegulusRules.LANDING = new Landing(8, 32, 5, 12, 5, 14)`.

### `hero/HeroSuperJump` — jump key
- `onInput(player)` is called by `HeroInputC2SPacket` for `HeroAction.JUMP` (pressed). It checks `superJumpVelocity > 0`, not anchored, `canAct(JUMP)`, then plays shared FX and calls `hero.onSuperJump(player)`.
- Velocity is applied on the client by `client/hero/SuperJumpClient` (client-authoritative movement). `FORWARD = 0.35` horizontal push.
- `apex(v)` — pure jump height estimate.

### `hero/HeroAirJump` — air jumps (multi-jump)
- Hook pair `airJumps` / `airJumpVelocity` (both sides) + `onAirStep` (server extras).
- `client/hero/AirJumpClient` — vanilla jump key in the air: checks `AirJumpRules.allow(used, max, reset, anchored, canAct)`, sets `motion.y = airJumpVelocity`, sends `HeroAction.AIR_STEP`; `onSync(entityId, serverUsed, step)` takes server corrections and draws the observer ring.
- Server `HeroAirJump.onInput` counts presses with the same rules (pure core `press(Body, Sink)`, unit-tested with fakes); an accepted press sends no motion (no double jump), a refused one sends motion `(x, min(y,0), z)` + the server counter. `tick` resets on ground/water/lava/ladder/flight/mount, `forget` on logout.
- `AirJumpRules.resets(...)` / `allow(...)` — pure rules shared by both sides. Packet `network/AirJumpSyncS2CPacket` (owner counter or `step=true` ring to trackers).
- `client/render/vfx/AirStepVfx` — pixel ring under the feet (own view and observers).
- `client/hero/SuperJumpClient.anchored(player, snapshot)` — public anchor check reused by air jumps.

### `hero/HeroProjectiles` — projectiles that hit a hero
- `onImpact(ProjectileImpactEvent)` (server): an `EntityHitResult` on a `ServerPlayer` asks `projectileResponse`; non-PASS → `ImpactResult.SKIP_ENTITY` and the response is applied. Deflected/reflected projectiles are pushed out of the hero's box (≤ 0.6 blocks) so they cannot re-hit next tick; `tickPending(level)` re-applies the new velocity at the end of the level tick (projectiles whose own tick overwrote it, e.g. llama spit).
- `ProjectileMath` (pure, unit-tested): `sideAxis`, `rotateAndScale` (Rodrigues; the same call turns a velocity and an `AbstractHurtingProjectile` power vector), `deflectYaw`/`deflected` (side away from the body centre when it is more than `SIDE_PICK_OFFSET` off the line, else random), `reflected`, catch yield. `DEFLECT_SCALE = 0.6`, turn 60–90°.
- `ProjectileResponse { PASS, DEFLECT, CATCH, REFLECT }` — append-only.
- `mixin/AbstractArrowAccessor` (common mixin) — invoker `getPickupItem` for CATCH.

### `hero/HeroResourceRegen` — slowing another player's own regen
- `scale(ServerPlayer)` — regen factor of this tick (cached), the lowest `foreignResourceRegenFactor` of heroes within `MAX_RADIUS` (64), `combine(float[])` pure. Resource owners multiply their own regen by it (Iron Man energy, Homelander cooling).
- Sources record the drained pairs; `flush(level)` at the end of a level tick pushes them to the source's `OwnerSection.MANA_LINKS` (client motes), `forget(player)` on logout.

### `hero/PersistentCooldowns` — cooldowns that outlive hero state
- Stored in the player's persistent NBT under `ViltrumiteCooldowns` (survives death, hero change and relog); `get(player, key)`, `set(player, key, ticks)`, `tick(player)` once per online tick (offline time does not count), pure `tickTag` (key removed at 0). Used by Reinhard's phoenix (12000 t).

### `hero/SafeSpot` — where an entity may be teleported
- `find(wanted, probe, radius, step)` → `Optional<Vec3>`: the wanted feet first, else the nearest grid point within the radius (ties: smaller vertical offset). A spot needs `fits && insideBorder && groundBelow(feet, MAX_DROP = 6)` — never over the void.
- `Probe` is a pure interface (unit tests use fakes); `LevelProbe(level, entity)` is the Minecraft side (`noCollision` with the entity's box, world border, a collider block within 6 below and above the build floor) and works on client and server, so a predicting client and the server search the same way. First user: Reinhard's Dash Behind.

### Other shared server helpers
- `hero/HeroDestruction.canDestroy(level, pos)` / `destroyBlock(level, pos)` — honours the `mobGriefing` gamerule, unbreakable blocks and the drop-chance config.
- `hero/HeroDamage.route(...)`, `applyCleanDamage(...)`, `isAnchored(entity)` — damage routing and control checks.
- `hero/control/ControlManager` + `HeroRegistry.allowsExternalControl(target, kind)` — grabs, freezes, anchors.
- `util/ThunderClapManager` — Viltrumite clap; base for hand-clap style abilities.
- `hero/DivineDamage` (`DIVINE` damage type, `source(level, attacker, type)`, `isDivine(source)` via tag `viltrumitecore:divine`) + `hero/DivineTargets` (`isDivine(entity)`, `isAbsolutelyInvulnerable(entity)`, entity tag `viltrumitecore:divine`, part entities use the parent) — damage that ignores armor, shields, enchantments and effects but not `bypasses_invulnerability`. Dev command `/viltrumite divine <targets> <amount> [type]` (op 2).
- `hero/AdminKill` — scope flag set while `/kill` runs (`KillCommandMixin`); inside it `HeroDamageLayers` skips `clampUnblockableDamage`, so HP floors never block an admin kill.

## Client

### `client/render/vfx/HeroImpactFx`
Receives `HeroFxS2CPacket` and renders all kinds above (pixel shards, rings, trails). No hero code needed. Shard speed constant `SHARD_VISUAL_SPEED`.

### `client/render/vfx/CameraShake`
- `addAt(at, power, range)` — shake with distance falloff; `add(power)` — local shake.
- `falloff(distance, range)` — pure. Decay ×0.86 per tick. Scaled by `punchShakeMultiplier` config.

### `client/render/vfx/HitStop`
Client hit-stop for any hero: `HeroFx.hitStop` freezes the targets' interpolation for a few ticks (`RenderLivingEvent.Pre`), a hero renderer can hold its clip clock with `heldTicks`. Pure clock methods are unit tested (`HitStopTest`).

### `client/render/vfx/AirTrailVfx`
Draws `HeroFx.sonicRing` (pure `ringRadius(life)`) and `HeroFx.airTrail` (40 t streak behind an entity, pure `trailAlpha`). Client constants only; the packet carries just the point / ids.

### `client/render/vfx/PixelVfx`
Pixel-art primitives for `RenderLevelStageEvent` renderers: `billboardPixel`, `beamDots`, `boxOutline`, `domeShell`, `sphereShell`, `bodyShell`, `crossGlow`, `rotateCamera`. Use for beams (eye lasers), auras, shells.

### `client/hero/SuperJumpClient`
Generic super-jump key loop. Reads `HeroRegistry.get(player).superJumpVelocity(player)`. Key id `key.viltrumitecore.regulus_super_jump` (kept for bindings), label "Super Jump".

## Adding to the toolkit
Move code here only when a second hero needs it or it has no hero meaning. Keep it hero-neutral: numbers come from the caller (a `record` spec), no `HeroId` checks. Add a JUnit test for pure math (`test/.../hero/HeroToolkitTest.java`).
