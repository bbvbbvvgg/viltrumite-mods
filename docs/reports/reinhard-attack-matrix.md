# Reinhard automatic-defense attack matrix (Core 1.36.1)

All three modes use the same authoritative protection hooks. The animation channel never owns input or the main action clock. Holy/drawn Reid and active actions select the free left hand; the weapon hand/weapon frame remain owned by the existing sword solver. Large rear threats use an evade rather than a false front-palm contact. A rear arrow retains passive deflection instead of being teleported through the torso into a front hand.

| Attacks / implementation | Server interception | Reaction / actual result |
|---|---|---|
| Vanilla and legacy melee; Homelander punch/chop/dash/combo; Iron Man blade/hammer, SonicRam/FlyBy, Hulkbuster punches/jackhammer/throw; Regulus Counter; Reinhard ordinary strikes | HeroDamage → ReinhardHero.absorbIncoming → existing frontal Parry | PALM, WEAPON or EVADE for raw ≥20; existing front cone, absorption/resistance, damage and cadence preserved |
| Homelander EyeLasers | Explicit continuous hitscan hook before damage cadence | BEAM hold; real damage absorbed, client endpoint refined to final palm; no claimed reflection |
| Iron Man MicroLaser | SignatureTrace ray + explicit hook | BEAM; actual signature endpoint stops at palm, no fake reflected laser |
| Iron Man Unibeam / MARK_17 PulseUnibeam | HeroRayDefense clips piercing ray before glass/leaf destruction and before hitAlong; respects IronManTargets/PvP/teams | BEAM; defender and targets behind the stop do not receive the blocked ray; entities before the stop retain the ordinary beam rules. Pulse gaps and recovery remain bounded; damage cadence unchanged |
| Vanilla arrows / spectral arrows | Forge ProjectileImpactEvent → HeroProjectiles | The same tracked arrow is held 10 ticks, pickup disabled until release; free hand can catch during a sword action. One held arrow maximum; next arrow deflects. Original pickup policy restored |
| Trident, fireball and other vanilla Projectile | Same shared projectile seam | PROJECTILE; existing real reflection/deflection including acceleration and owner; a trident is not converted into an arrow |
| Iron Man RepulsorBlastEntity / MicroMissileEntity | Explicit HeroProjectiles.intercept before custom onHit | PROJECTILE; reflected velocity, owner and Homing target are real server state. No missile splash is applied for a successfully intercepted direct collision |
| Missile block/nearby-entity splash and ordinary explosions | Existing explosion DamageSource tag in absorbIncoming | BRACE; no fictional reflection and no new immunity. Ordinary resistance and impulse policy retained |
| MARK_42 RocketFistEntity | A steered Entity, **not** Projectile. SignatureTrace.shot annotates its existing hurt call; natural server return-to-owner remains | PROJECTILE contact response, then the actual glove returns. Not sent to the generic projectile reflection seam |
| ShoulderGun bullets | Hitscan SignatureTrace.shot; GunRounds is presentation only | PROJECTILE response, existing hurt/PvP/cadence; no fake reflected bullet entity |
| Homelander Roar; legacy ThunderClapManager; Iron Man repulsor shockwave, shared landing/Slam; Regulus DebrisKick; HeroDebris eruption; legacy flight collision area | Narrow HeroAttackContext.WAVE around the original hurt call (same source/amount/return) | BRACE rather than treating a wave as a held arrow. Existing frontal parry/resistance retained |
| Regulus AirBlade | HeroAttackContext.SHOT around the original analytic hit | PROJECTILE response, not a fictional projectile entity |
| Reinhard divine cuts / World Erasure / divine damage | Existing DivineDamage/adaptation route, unchanged | Not falsely absorbed or reflected by the ordinary-protection animation channel |
| Grab/control, passive abilities, fall/fire/environment, Regulus authority effects | Existing ControlManager / hero hooks / resistance / divine rules | Not invented as projectiles or melee. No new control lock or damage rebalance |

## Opt-in reproduction

Use a disposable offline test world with the Java property `-Dviltrumitecore.reinhard.qa=true` on both server and client. The command does not exist in a normal launch and requires operator level 2.

`reinhardqa <defender> mode_restrained|mode_knight|mode_holy`

`reinhardqa <defender> arrow|melee|weapon|heavy|repulsor|missile|splash|homelander|unibeam|pulse|microlaser|gun|rocketfist|airblade|wave <real-connected-attacker>`

Both players must be on a PvP-enabled server and not allied. Forge FakePlayer intentionally cannot PvP with normal players: it is **not** valid acceptance evidence for Unibeam/PulseUnibeam. The harness invokes production handlers; `splash` launches a real missile into a nearby stone block (it changes the test world). `ground|diagonal|both` starts actual production scene/CarveJobs work. Diagnostic force-setup is not normal ability activation. Do not use on a valuable world.

Final-bone/projection/wire tests and native observations are recorded separately in the main report. This table documents execution-path coverage, not a claim of subjective visual approval for every attack animation.
