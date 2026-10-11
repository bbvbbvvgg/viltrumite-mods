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

### `client/render/vfx/PixelVfx`
Pixel-art primitives for `RenderLevelStageEvent` renderers: `billboardPixel`, `beamDots`, `boxOutline`, `domeShell`, `sphereShell`, `bodyShell`, `crossGlow`, `rotateCamera`. Use for beams (eye lasers), auras, shells.

### `client/hero/SuperJumpClient`
Generic super-jump key loop. Reads `HeroRegistry.get(player).superJumpVelocity(player)`. Key id `key.viltrumitecore.regulus_super_jump` (kept for bindings), label "Super Jump".

## Adding to the toolkit
Move code here only when a second hero needs it or it has no hero meaning. Keep it hero-neutral: numbers come from the caller (a `record` spec), no `HeroId` checks. Add a JUnit test for pure math (`test/.../hero/HeroToolkitTest.java`).
