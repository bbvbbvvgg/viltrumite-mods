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
- `hero/Annihilation` — the Reid strike (any hero may use it): `strike(attacker, target)` kills every mortal target through the `viltrumitecore:annihilation` damage type (bypasses invulnerability, armor, shields, enchantments, effects, resistance, totems, hero layers; HP floors step aside inside `run`), never a divine target, never absolute invulnerability (spark instead); part entities strike their parent. Pure `decide(verdict, creativeOrSpectator, alive)`.
- `hero/CarveJobs` + `hero/CarveBudget` — queued block work spread over END level ticks: `CarveJobs.submit(level, job)`, `Job.step(level, budget)` / `priority()` / `remainingWork()`; per tick `CarveBudget.perTick(BASE_BUDGET 2000, mspt)` (full ≤ 40 ms, linear to base/8 at 60 ms, then `max(50, base/20)`), higher priority first, FIFO inside; unloaded levels drop their queue. Pair with `HeroDestruction.clearBlock(level, pos)` (no drops / particles / neighbour updates, `canDestroy` rules). `hero/LevelTickPhases.systemsFor(phase)` pins which system runs in which half of the level tick (control START; projectiles, regen, carve END).
- `hero/LiftLeases` — the only owner of `noGravity` changes for lifted entities: lease per owner UUID, the original flag restored when the last lease goes; an entity that unloads mid-lift is restored on load through the `ViltrumiteLifted` tag (`EntityJoinLevelEvent`). `hero/HeroImpulse.mayMoveNonLiving / mayLiftLiving` — which entity types a hero may push or lift (items, falling blocks, minecarts...; not frames, paintings, display entities).

### Budgeted carve example

Submit once on the server. Do not step a job from a player tick. The shared END queue owns the per-level budget. Every attempt spends budget, including unloaded, air and protected positions. Keep remainingWork exact, or return the default unknown bound.

```java
CarveJobs.submit(level, new CarveJobs.Job() {
   public boolean step(ServerLevel world, int budget) {
      for (int used = 0; used < budget && cursor.hasNext(); used++) {
         BlockPos pos = cursor.next();
         if (world.isLoaded(pos) && world.getWorldBorder().isWithinBounds(pos)
            && pos.getY() >= world.getMinBuildHeight() && pos.getY() < world.getMaxBuildHeight()) {
            HeroDestruction.clearBlock(world, pos);
         }
      }
      return !cursor.hasNext();
   }
   public int priority() { return 10; }
});
```

Here `cursor` is a finite Iterator<BlockPos> of clipped positions. Higher priorities run first; FIFO applies within a priority. One job cannot receive more than the remaining tick budget. A throwing job is dropped. No final neighbour-update sweep is needed. Flags alone do not stop old-block callbacks. HeroDestruction.setAir with CLEAR_FLAGS uses QuietBlockRemoval plus the common LevelChunk redirect to discard old block entities without onRemove drops/XP or redstone neighbour callbacks. Use the helper, not raw level.setBlock with the same flags. Ordinary flags 3 keep the old callbacks.

World Cut uses priority 10; Rain of Cuts uses 0. `HeroDebris.launchBlock(..., flags)` accepts explicit source-removal flags; use `HeroDestruction.CLEAR_FLAGS` for a clean carve. Old callers still use flags 3. A debris launch clears its source: do not clear the same position again.

Detached execute effects may call `Annihilation.strike(null, target)`: the victim supplies the damage level. `HeroFx.flash(level, at)` sends a detached flash without a tracking source. Client timed scenes may call `HeroImpactFx.shockwaveVisual(at, radius)` for rings without automatic shake or dust; the scene owns those effects.

## Client

### `client/render/vfx/HeroImpactFx`
Receives `HeroFxS2CPacket` and renders all kinds above (pixel shards, rings, trails). No hero code needed. Shard speed constant `SHARD_VISUAL_SPEED`.

### `client/render/vfx/CameraShake`
- `addAt(at, power, range)` — shake with distance falloff; `add(power)` — local shake.
- `falloff(distance, range)` — pure. Decay ×0.86 per tick. Scaled by `punchShakeMultiplier` config.

### `client/render/vfx/SkyTint` and `SoundDuck`
Screen-wide mood for world events: `SkyTint.INSTANCE.push(owner, priority, tintSupplier)` / `remove(owner)` (sky, fog, clouds, rain; highest priority wins, eased), `SoundDuck.INSTANCE.duck(owner, category, target, fadeTicks, exemptIds)` / `release(owner)` (the caller's own sounds exempt). Pure `SkyTint.mix/apply`, `SoundDuck.factor/exempt` are unit tested. First user: Reinhard's Reid draw scene.

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
