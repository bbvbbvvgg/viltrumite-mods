# Thor review round 2 — status matrix (core 1.49.0, protocol 9)

Branch `feat/thor-integration-abc` (PR #62), started from `cfa2c1e`. Statuses:
FIXED BEFORE THIS PASS (previous pass, commits up to `cfa2c1e`), FIXED IN THIS PASS,
PARTIALLY FIXED, NOT REPRODUCIBLE ON CURRENT HEAD, OPEN DESIGN, REQUIRES IN-GAME TEST,
STILL OPEN. Nothing here was run in a Minecraft client; every gameplay item also needs
the in-game check list at the end.

## Integration header (merge conflicts)

| Item | Status | Note |
|---|---|---|
| ThorFlags bit 5 A vs C | FIXED BEFORE THIS PASS | bit 5 HAMMER_DETACHED, bit 6 SCENE, 6-arg compose |
| ThorContext 9th component A vs B | FIXED BEFORE THIS PASS | 10-component record |
| ThorActions.context three-way | FIXED BEFORE THIS PASS | |
| changeHero boolean vs deferral | FIXED BEFORE THIS PASS | admitsEntry then deferral |
| Shared foundation files edited by all | FIXED BEFORE THIS PASS | merged in the integration branch |

## PR #55 foundation

| Item | Status | Note |
|---|---|---|
| B1 paired moves cannot start | FIXED BEFORE THIS PASS | openGrabWindow(target), start(partner) |
| B2 onStop lost / stop() clears foreign locks | FIXED BEFORE THIS PASS | one lifecycle path, ThorLifecycle |
| B3 MMB uniqueness test forbids sessions | FIXED BEFORE THIS PASS | test checks decision acts |
| B4 two sources of hammer truth | FIXED BEFORE THIS PASS | MjolnirWorld only writer |
| M1 static registries vs server lifecycle | FIXED BEFORE THIS PASS | per-server clear (sessions, buses) |
| M2 ThorMoves must match client/server | PARTIALLY FIXED | protocol bumped to 9; no registry hash handshake |
| M3 handler call order | FIXED BEFORE THIS PASS | documented order, tests |
| M4 forced release = player release | FIXED BEFORE THIS PASS | ReleaseCause.FORCED |
| M5 hyper armor | OPEN DESIGN | no approved hyper armor rules |
| M6 one timeline, no overlay | FIXED BEFORE THIS PASS | RECALL overlay |
| M7 conditions / cooldowns point | FIXED BEFORE THIS PASS | admits, PersistentCooldowns |
| M8 positional ThorContext / flying false | FIXED BEFORE THIS PASS | flying = fullFlight |
| M9 LETHAL_HIT not implementable | FIXED IN THIS PASS | LETHAL_HIT = candidate, scene in the real death (FatalityTrigger) |
| M10 ThorEvents.post rethrows | FIXED BEFORE THIS PASS | |
| M11 one-Thor / defer hero change hooks | FIXED BEFORE THIS PASS | |
| M12 enemy = any living | FIXED BEFORE THIS PASS / OPEN DESIGN | HeroTargets.canHit; villagers/passives OPEN |
| M13 resolver ignores belt | FIXED BEFORE THIS PASS | beltOnly, DRAW key OPEN |
| m1 public mutable ThorState fields | STILL OPEN | encapsulation refactor not done |
| m2 stop() clears flags before onStop | FIXED BEFORE THIS PASS | |
| m3 client re-reads claim every tick | FIXED BEFORE THIS PASS | latched on press |
| m4 design §1 "in development" | OPEN DESIGN | |
| m5 HeroInputC2SPacket.equipped | STILL OPEN | not re-audited this pass |
| m6 read model fields | PARTIALLY FIXED | |
| m7 event types | PARTIALLY FIXED | |
| m8 shared interaction layer unused | STILL OPEN | |
| m9 vacuous tests | PARTIALLY FIXED | |
| m10 docs / DoD | PARTIALLY FIXED | memory bank + SESSION updated; PR body not editable without repo access |
| m11 dev commands | STILL OPEN | |
| NIT (unused context fields, FQN, Reinhard test, protocol, Y feedback, refusal text, beard, geo width, constants) | PARTIALLY FIXED | protocol now 9; the rest STILL OPEN / REQUIRES IN-GAME TEST (beard, geo UV) |

## PR #57 session A (Mjölnir)

| Item | Status |
|---|---|
| M1 soft-lock in unloaded chunk | FIXED BEFORE THIS PASS |
| M2 settle() removal / IN_HAND meaning | FIXED BEFORE THIS PASS |
| M3 ThorFlags bit 5 | FIXED BEFORE THIS PASS |
| M4 ThorContext 9th | FIXED BEFORE THIS PASS |
| M5 changeHero boolean | FIXED BEFORE THIS PASS |
| M6 owner change mid-throw | FIXED BEFORE THIS PASS |
| M7 throw hits allies | FIXED BEFORE THIS PASS (villagers OPEN DESIGN) |
| M8 invariant untested | FIXED BEFORE THIS PASS |
| M9 physical catch into the palm | REQUIRES IN-GAME TEST |
| m1 body vanishes outside sim distance | FIXED BEFORE THIS PASS (store continues) |
| m2 damage source without attacker | FIXED BEFORE THIS PASS |
| m3 post rethrows | FIXED BEFORE THIS PASS |
| m4 attackerId = logicalId | FIXED BEFORE THIS PASS |
| m5 HOLSTERED overloaded | FIXED BEFORE THIS PASS |
| m6 ThorState write encapsulation | STILL OPEN |
| m7 craters outside CarveJobs | FIXED BEFORE THIS PASS |
| m8 first fall ignores protected land | OPEN DESIGN |
| m9 transform "underground" heuristic | FIXED BEFORE THIS PASS (natural ground only) |
| m10 drop point in a wall | FIXED BEFORE THIS PASS |
| m11 pierce through wall in last segment | FIXED BEFORE THIS PASS |
| m12 charged throw sticks early | FIXED BEFORE THIS PASS |
| m13 transient body fields lost | FIXED BEFORE THIS PASS |
| m14 auto-aim performance | STILL OPEN |
| m15 TP reach arm only without clip | REQUIRES IN-GAME TEST |
| m16 owner pick-up rips | FIXED BEFORE THIS PASS |
| m17 void recovery loop | FIXED BEFORE THIS PASS (№217 OPEN) |
| NIT 1-7 | PARTIALLY FIXED (decision refs, dev cmds, tuning constants STILL OPEN) |

## PR #56 session B (combat)

| Item | Status | Note |
|---|---|---|
| B1 RMB eats vanilla use | FIXED IN THIS PASS | `VanillaUse` arbitration; RMB priority on lying Mjölnir OPEN DESIGN (conservative: pick-up) |
| B2 sky lightning without target / column | FIXED IN THIS PASS | single target only, no fallback point, path blocked |
| M1 storm detonates on forced release | FIXED BEFORE THIS PASS | |
| M2 scheduled effects survive dimension | FIXED BEFORE THIS PASS + IN THIS PASS | dimension-bound tasks, lifecycle cancel; sky strike re-checks target, enemy and loaded chunk |
| M3 cooldowns reset by relog | FIXED BEFORE THIS PASS | death reset OPEN DESIGN |
| M4 sky target policy default | FIXED IN THIS PASS | SINGLE_TARGET_ONLY; old configs migrated |
| M5 discharge DPS / i-frames | PARTIALLY FIXED | area / ranged hits keep i-frames; range 16, cadence and numbers OPEN DESIGN (balance) |
| M6 hits pets / mounts | FIXED BEFORE THIS PASS | HeroTargets.canHit; villagers OPEN |
| M7 HAMMER_STRIKE without hammer | FIXED BEFORE THIS PASS | |
| M8 particle packet flood | FIXED IN THIS PASS | one packet per arc / ring / crackle |
| M9 LETHAL_HIT mid-strike | FIXED BEFORE THIS PASS + IN THIS PASS | landStrike stops; scene now starts in the death event |
| M10 first person | FIXED IN THIS PASS / REQUIRES IN-GAME TEST | ThorFirstPerson rows, no vanilla swing |
| m1 contact key later than damage | STILL OPEN | clip timing |
| m2 "Not now" spam / step overflow | STILL OPEN | |
| m3 WeakHashMap leak | FIXED BEFORE THIS PASS | forget on lifecycle stop |
| m4 COMBO_END.amount = ordinal | STILL OPEN | |
| m5 WRATH crater flag, no LoS | FIXED IN THIS PASS | |
| m6 vanilla bolts side effects | STILL OPEN | needs a client bolt FX |
| m7 protected blocks = only unbreakable | STILL OPEN | shared HeroDestruction (no BreakEvent / claims) |
| m8 damage type | STILL OPEN | |
| m9 hit-stop vs push | STILL OPEN | |
| m10 buffered step without hammer | FIXED BEFORE THIS PASS | hammer-in-hand rule |
| m11 state machine tests | PARTIALLY FIXED | |
| m12 wave latency | OPEN DESIGN | |
| m13 hiss hint | FIXED IN THIS PASS | |
| m14 direction from displacement | OPEN DESIGN | PLACEHOLDER |
| NIT burst unused / config / gate order / FQN / memory bank / version | PARTIALLY FIXED | config validated + versioned; memory bank and version done |

## PR #58 session C (scenes)

| Item | Status | Note |
|---|---|---|
| B1 fatality bypasses / burns totem | FIXED IN THIS PASS | scene starts in LivingDeathEvent after the totem check; held death completed once |
| B2 grab victim through walls | FIXED IN THIS PASS / REQUIRES IN-GAME TEST | SceneSpace (server + client), last safe point, settle |
| B3 ThorFlags bit 5 | FIXED BEFORE THIS PASS | |
| M1 doomed victim survives dimension change | FIXED IN THIS PASS | travel cancelled, victim found anywhere, persistent tag |
| M2 vehicles | FIXED IN THIS PASS | stopRiding / passengers refused |
| M3 teleport Nether roof / lava | FIXED IN THIS PASS | server judge + client aim |
| M4 teleport invulnerability, no cooldown | PARTIALLY FIXED | invulnerability removed; cooldown OPEN DESIGN |
| M5 NoGravity sticks / fly kick | FIXED IN THIS PASS | no NoGravity, sustainsAirborne |
| M6 client-driven Thor position | FIXED IN THIS PASS | path validated, server snaps a drifting Thor |
| M7 fatality in the air | FIXED IN THIS PASS | ground spot ≤ 0.6 |
| M8 player velocity | FIXED IN THIS PASS | real motion |
| M9 ducking scene sounds | FIXED IN THIS PASS | background categories only |
| M10 client scene state without timeout | FIXED IN THIS PASS | expiry, entity leave, start/stop tracking |
| M11 bosses / giants | FIXED IN THIS PASS | refused (giants OPEN DESIGN #15) |
| M12 victim poses ignore Body | FIXED IN THIS PASS / REQUIRES IN-GAME TEST | |
| M13 re-entrant start from B's damage | FIXED IN THIS PASS | candidate only; start in the death event, landStrike stops |
| M14 deferral vs changeHero | FIXED BEFORE THIS PASS | |
| M15 RMB during grab → block use | FIXED IN THIS PASS | claimsBlockUse in scenes |
| M16 doomed death lost on restart | FIXED IN THIS PASS | persistent tag + ServerStopping abort |
| m1 PvP / creative | FIXED IN THIS PASS | |
| m2 mob AI active | FIXED IN THIS PASS | NoAI restored on every end |
| m3 held Thor grabs | FIXED IN THIS PASS | |
| m4 GRAB timeline 1200 | FIXED IN THIS PASS | |
| m5 onStart on buffered | FIXED BEFORE THIS PASS | onQueued |
| m6 thrown body impact heuristic | STILL OPEN | |
| m7 craters outside budget | FIXED IN THIS PASS | |
| m8 slam drop 6 blocks | FIXED IN THIS PASS | 1.5 |
| m9 dash client prediction / config sync | FIXED BEFORE THIS PASS (refusal) / STILL OPEN (config sync) | |
| m10 teleport camera x-ray | STILL OPEN | |
| m11 cancel on focus loss | FIXED IN THIS PASS | |
| m12 push/pop imbalance | FIXED IN THIS PASS | receiveCanceled Post |
| m13 PoseRig begun and dropped | FIXED IN THIS PASS | |
| m14 plan step over limit | FIXED IN THIS PASS | no scene |
| m15 ThorHero.hammer direct read | FIXED BEFORE THIS PASS | |
| m16 protocol not bumped | FIXED IN THIS PASS | 9 |
| m17 session tests | PARTIALLY FIXED | pure + seam tests, no server harness |
| m18 PoseBlend outside client/anim | STILL OPEN | |
| m19 ScenePoseFade garbage | STILL OPEN | |
| m20 held victim head rotation | STILL OPEN | shared mixin |
| NIT 1 doc comment | STILL OPEN | |
| NIT 2 LAST not cleared | FIXED IN THIS PASS | |
| NIT 3 victim logout reason | FIXED IN THIS PASS | |
| NIT 4 double S2C to victim | STILL OPEN | |
| NIT 5 teleport sync to trackers | FIXED IN THIS PASS | |
| NIT 6 resist bucket | STILL OPEN | |
| NIT 7 teleport handshake order | STILL OPEN | |
| NIT 8 rehearsal victim in floor | STILL OPEN | |
| NIT 9 claimsMouse semantics | STILL OPEN | |
| Scope: mobility in C vs B spec | OPEN DESIGN | owner to confirm |
| Fatality Thor invulnerability default | OPEN DESIGN | bounded by the scene length |
| Grab text hint vs §2.1 | OPEN DESIGN | |

## Remaining BLOCKER / MAJOR

- BLOCKER: none confirmed open in code. B-B1, B-B2, C-B1, C-B2 are fixed but REQUIRE IN-GAME TEST.
- MAJOR still not fully closed: B-M5 (balance numbers OPEN), C-M4 (teleport cooldown OPEN), foundation M2 (no registry handshake), foundation M5 (OPEN), A-M9 (in-game).

## In-game check list

The C list from the review (1–10) plus: RMB with food / bow / shield / pearl / bucket, on doors, chests, villagers, horses, boats, item frames, a lying Mjölnir; THUNDER_RING with no hit (no bolt, no hole); storm with several viewers (packet rate); first-person wave / storm / wrath / strikes.
