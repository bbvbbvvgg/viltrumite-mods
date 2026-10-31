# Iron Man stage 6 — Iron Legion (core 1.59.0, protocol 17)

Design: `docs/design/2026-10-31-ironman-legion-design.md` (Russian, accepted as the stage-6 spec). Plan: `docs/plans/2026-10-09-ironman-stage6-legion.md`.

## Ownership and lifecycle
- `MarkLocation.LEGION` (appended; NBT stores the name). A mark is in exactly one place; every change goes through `MarkRoster.move(id, from, to)`. Callable = `STORED` && no break cooldown && durability > 0 (`MarkRoster.legionCallable`; `VeronicaView.legionCallable` is the same rule for display, the server re-checks with `LegionCall.validate`). Max 3 (`LegionRules.MAX_SUITS`).
- Durability lives only in the roster. Damage to `LegionSuitEntity` goes through `MarkDamage.apply` straight into the roster; the entity only mirrors `fraction`. Recall never heals; a new call reads the roster. 0 durability → CRASH phase (44 t) → `breakMark` (STORED + break cooldown).
- Bodies are never saved (`shouldBeSaved` false) and remove themselves on the first tick without a valid owner/roster/squad. `recallWorld()` puts LEGION back to STORED with EMPTY/IN_DELIVERY, so load / relog / server stop are covered by the roster alone. Death, hero change, suit off → `IronManLegion.onCleanup`; dimension change → `onDimensionChange`; server stop → `LegionEvents.onServerStopping` → `IronManLegion.clear()`.
- Call cooldown = `extraCooldowns[4]` (starts when the last suit leaves). Refusals (`IronManLegion.Refusal` → `gui.viltrumitecore.legion.refuse_*`): none callable, cooldown, Veronica busy (pod / parts / suit entry / Hulkbuster), controlled.

## Ability and UI
- `ironman:legion`, page 2 slot index 11 (`IronManAbilities.LEGION_SLOT`), `HeroAction.LEGION`. Press with no Legion → `LegionMenuS2CPacket` → `LegionScreen` call mode (cards, ≤3, keys 1–7 / Enter); with a Legion → command mode (5 orders keys 1–5, P cycles target priority; `LegionOrderC2SPacket(order, priority)` with −1 = unchanged).
- HUD: `LegionHud` right column from `LegionStatusS2CPacket` (`LegionStatus`: order, priority, cooldown, event bits `LegionSquad.EVENT_*`, per suit mark/fraction/phase/stance/role/engaged). JARVIS lines are queued client-side from the event bits (`ClientLegion.status` → `JarvisVoice.legion(Line)`), subtitles when the voice is offline.
- Packets appended after the v16 table → protocol 17 (`HeroRosterIntegrationTest` checks the order).

## AI (server)
- `LegionSquad` (one per owner, coordinator round every 10 t shifted by owner) builds the threat board (`ThreatScore`: attackers of Tony/suits remembered 200 t, danger, distance, line of sight, health/armour, priority) and assigns roles (`LegionTactics.assign`: VANGUARD / FLANKER / OVERWATCH / GUARDIAN by `LegionProfile`, overkill spread, interception of whoever hits Tony). Retreat < 30 %, return to Veronica < 12 % (`RETURN_FRACTION`).
- `LegionSuitBrain` ticks every 2 t shifted by slot; flight is pure math in `LegionSteering` (accel limits, wedge/ring/trio formations, 3 block feelers ±35°, separation, strafing, projectile dodge, anti-stuck 60 t, teleport only > 64 blocks (`TELEPORT_DISTANCE` 72) and ≤ once per 10 s).
- Friendly fire: one rule `LegionAllies.spares/mayHit/splashAllowed` used by `RepulsorBlastEntity`, `MicroMissileEntity` splash and the `LegionEvents.onAttack` (`LivingAttackEvent`, HIGH) safety net. Villagers/neutral only on ATTACK; players only with server PvP. Another player's suit has its owner's relation (`LegionAllies.relation`); suit durability loss from a player or from another player's Legion passes `LegionAllies.playerDamageAllowed` (server PvP + `canHarmPlayer` both ways + not spectator; ownerless suits deal none).
- Rays: `SignatureTrace.rayCandidate` (also used by `IronManCombat.aimPoint/entityInReach`) skips Tony's own suits, so his rays reach the enemy behind them.
- Server gate: `IronManLegion.mayCommand` (Iron Man + alive + `canAct(LEGION)` + no `ActionLocks` + not held by an anchoring hero) guards both `LegionCallC2SPacket` and `LegionOrderC2SPacket`.
- ATTACK: the ordered target is added to the threat board even outside the 28-block scan box (`LegionSquad.withOrdered`); it stays valid up to `ORDER_REACH + 16` from Tony.
- HOLD: the far-distance teleport fallback measures from the held point (`LegionSuitBrain.fallbackAnchor`) and only targets a loaded chunk. If the held point unloads, the suits unload too (`shouldBeSaved` false) and go back to the roster as STORED with their durability.
- Signatures reuse the stage-4 cores through `SignatureActor` (player or suit; `LegionSuitEntity implements SignatureActor.Provider`), no copies: micro laser, rocket fist (`RocketFistEntity`), camouflage, starboost, pulse unibeam, shoulder gun, slam.

## Client
- `LegionSuitRenderer` (EntityRenderer) poses the empty-suit shell (`geo/ironman/marks/empty_suit.geo.json`, mark skin `MarkTextures`, extras `MarkExtras`, signature parts `SignatureVisuals.suitParts`, flames `ThrusterFlames.flicker` at feet/palms). Layer order: hover↔cruise blend (loop time wrapped by `wrap`, the core does not wrap LOOP clips) → phase overlay (brake, `ready_<mark>`, takeoff, crash loop) → move overlay → arm aim / head look by quaternion slerp → additive fire/hit. Body lean/bank/bob/crash tumble are pose-stack transforms about `CENTRE` 0.95. `Anchors` (palms, chest, shoulder) come from the same matrices for VFX.
- Clips: `animations/ironman/legion_suit.animation.json` generated by `tools/assets/make_legion_clips.py` (21 clips; ready clips 26 t and end on the hover start pose; additive clips start/end at 0). `LegionClipsTest` checks names, bones, lengths, seams.
- Sign convention re-checked here: the geo X flip puts the shell's right limbs at render +x, so clip Z > 0 moves the RIGHT limb outward; clip Y > 0 turns toward the model's right; clip X < 0 swings a limb forward / tilts the head up.
- `LegionVfx` (engine loops `LegionEngineSound`, beats, trails, damage sparks/smoke, War Machine `GunRounds.fire`, sky glint on APPROACH, Mark 7 laser, pulse beam). Seams made public for it: `GunRounds`, `ThrusterFlames.flicker`, `MarkVfx.spawn`.
- Sounds: own numpy synthesis `tools/sfx/ironman_legion.py` (12 cues `ironman_legion_*`, events in `sound-overlays/legion.json`, subtitles in `lang-overlays/legion/*`, 12 locales). Not auditioned in game yet.
- Icon: `legion` in `tools/assets/ironman_icon_art.py` (three suits on boot flames).

## Scripted playtest (review of PR #98)
- `client/ironman/legion/qa/LegionPlaytest` (`-Dviltrumitecore.legion.playtest=true`, plain `runClient`): loads save `run/saves/legion` (superflat, cheats; not in git), resets the roster by a hero change, drives the real key → screen → C2S path, logs `LEGION_PT CHECK PASS/FAIL` and `LEGION_PT DONE pass= fail= sounds={...}`, screenshots `run/screenshots/legion_*.png`. Last run: 35/35.
- Pitfalls: `cmd(...)` inside `server(...)` runs one task later (it killed the freshly spawned ATTACK target once); no-AI zombies still burn in daylight (give them a helmet); `ironman_legion_land` plays only if a suit ends BRAKE on the ground, so it is rarely heard on flat arrivals.
- `LegionScreen` call grid must fit 240 scaled px (1280×720 auto scale 3, default 854×480 window); `LegionClientRulesTest` checks both menu heights.

## Not verified in game (as of the review)
- Listening by ear (only an OpenAL wave capture: no clipping, peak −1.6 dBFS), two clients on a dedicated server, other players' Legions in multiplayer, HUD at GUI scale 3–4 (the test window maxes at 2).
