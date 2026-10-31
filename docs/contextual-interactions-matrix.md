# Contextual interaction matrix (stage 2)

Research map of the whole roster: who can meet whom in a fight, what the code exposes for that meeting and which pairs
carry a contextual interaction. The machine-readable catalog of the implemented interactions is
[`contextual-interactions.json`](contextual-interactions.json) (synced with `ContextualInteractions.builtins()` by
`ContextualInteractionCatalogTest`); this file is the design view.

Legend: **✔ id** — implemented interaction · `·` — no special meeting (ordinary combat rules) · `≈` — the meeting is
already handled by an existing system (named), so no duplicate was added · `✗` — considered and rejected (reason below).

## Roster and their hooks

| Participant | Kind | Code | Seam the interactions use |
|---|---|---|---|
| Captain America (player) | hero | `hero/captain` | presence `weapon.shield.vibranium` (GUARD), `guardFaces`, `react` (GUARD_SHATTERED / GUARD_BRACED / METEOR_STOPPED / GUARD_DEFLECTED) |
| Thor (player) | hero | `hero/thor` | presence `weapon.hammer`, `element.lightning`, `trait.divine` (ACTIVE in strike windows); lightning damage type |
| Mjolnir, thrown | hero item entity | `hero/thor/hammer/MjolnirEntity` | `thrown(...)` damage source (direct = hammer), `MjolnirWorld.recall` |
| Reinhard (player) | hero | `hero/reinhard` | presences REID_DRAWN / REID_STROKE (`weapon.sword`, `trait.divine`, `trait.annihilating`) |
| Iron Man (player) | hero | `hero/ironman` | presence `armor.powered` (flight suit), Hulkbuster presence `body.mech.heavy`; `RepulsorBlastEntity` |
| Iron Legion suits | summon | `entity/LegionSuitEntity` | entity class, owner, `LegionAllies` |
| Regulus (player) | hero | `hero/regulus` | Lion's Heart (absolute invulnerability), heart strain → seizure; `react(LION_STRAINED)` |
| DIO (player) | hero | `hero/dio` | `DioTimeStop` fields, deferred ledger payout (`DioTimeStop.paying()`) |
| Sung Jinwoo (player) | hero | `hero/jinwoo` | `ShadowSoldierEntity` (owner id, dissolve) |
| Viltrumites (Conquest, Thula, Lucan, Vidor, spawner clones) | NPC players | `entity/ViltrumiteFakePlayer`, `hero/ViltrumiteHero` | presence `body.viltrumite`, `react(METEOR_CAUGHT / STRIKE_RECOILED)` |
| Homelander, Sinclair, Wish | heroes | `hero/homelander`, `hero/sinclair`, `hero/wish` | ordinary combat (see "rejected") |
| NPC Avengers (Iron Man, Thor, Hulk, Widow, Hawkeye) | summons of Captain | `entity/avengers` | `AvengerEntity.kind()`, `action()`; hits are `mobAttack(avenger)` via `TeamDamage` |
| Shadow soldiers | summons of Jinwoo | `entity/jinwoo` | owner id, `dissolve()` |
| Thanos | boss (world event) | `entity/thanos`, `worldevent/thanos` | entity class (all six stones), `stoneHeal` |
| Chitauri soldiers / chariots | event mobs | `entity/chitauri` | `ChitauriMob` |
| Chitauri Leviathan | event boss | `entity/chitauri/ChitauriLeviathan` | multipart body (`parts()`), `staggerBy(ticks)` |
| Meteor | world event projectile | `entity/MeteorEntity`, `worldevent/meteor` | PROJECTILE impact before the crater |

## Matrix — hero × hero / summon / boss

Rows attack, columns are hit (direction matters).

| ↓ attacker / target → | Captain (raised shield) | Thor | Reinhard | Iron Man suit | Regulus (Lion's Heart) | Thanos | Legion suit | Leviathan |
|---|---|---|---|---|---|---|---|---|
| Reinhard — Reid stroke | **✔ reid_shatters_vibranium** | **✔ divine_weapon_clash** (both striking) | · | · | **✔ reid_strains_lions_heart** | · | · | · |
| Thor — Mjolnir in hand | **✔ mjolnir_meets_vibranium** (also friendly contact) | · | **✔ divine_weapon_clash** | **✔ thunder_overcharges_armor** (lightning) | ≈ Lion's Heart recoil | · | **✔ thunder_charges_legion** | **✔ lightning_runs_the_leviathan** |
| Thor — thrown Mjolnir | **✔ mjolnir_rebounds_off_shield** | · | · | · | ≈ Lion's Heart recoil | · | · | ≈ ordinary hit |
| NPC Thor (storm / throw) | ≈ TeamMoves shield strike | · | · | ≈ TeamMoves charge | · | · | · | **✔ lightning_runs_the_leviathan** |
| Iron Man — repulsor bolt | **✔ repulsor_splits_on_vibranium** | · | ≈ ArrowWard | · | ≈ Lion's Heart catch | · | · | · |
| Iron Man — Hulkbuster fist | · | · | · | · | · | · | · | **✔ heavy_hitter_halts_leviathan** |
| NPC Hulk | · | · | · | · | · | · | · | **✔ heavy_hitter_halts_leviathan** |
| Viltrumite fist | **✔ viltrumite_fist_meets_vibranium** | · | · | · | · | · | · | **✔ heavy_hitter_halts_leviathan** |
| Iron Legion suit | · | · | · | · | · | · | · | **✔ legion_vs_chitauri** (invasion) |
| DIO — time-stop ledger | · | · | · | · | ≈ Lion's Heart cannot be frozen | **✔ time_stone_rewinds_ledger** | · | · |
| Thanos melee | **✔ titan_breaks_guard** | · | · | · | · | · | **✔ titan_scatters_summons** | · |
| Thanos — any hit on a summon | · | · | · | · | · | · | **✔ titan_scatters_summons** (also shadows, NPC Avengers) | · |

## Matrix — world events

| Event ↓ / meets → | Captain (raised shield) | Thor (hammer in hand) | Reinhard (Reid drawn) | Viltrumite | Jinwoo with shadows out | Regulus (Lion's Heart) | Leviathan |
|---|---|---|---|---|---|---|---|
| Meteor (fall / rain) | **✔ vibranium_stops_meteor** | **✔ mjolnir_shatters_meteor** | **✔ reid_cleaves_meteor** | **✔ viltrumite_hurls_meteor** | **✔ shadow_exchange_meteor** | ≈ captureProjectile stops it dead | **✔ meteor_downs_leviathan** |
| Chitauri invasion | ≈ shield reflects bolts | · | · | · | · | · | see heroes above |

Combo chains that come out of the matrix (no extra code, they follow from the rules):

* a Viltrumite hurls a meteor (`viltrumite_hurls_meteor`) at the Leviathan → `meteor_downs_leviathan` credits him;
* NPC Thor's storm runs through a Leviathan (`lightning_runs_the_leviathan`) while the Legion finishes the dropped
  troopers (`legion_vs_chitauri`);
* Thor's lightning on a Legion suit (`thunder_charges_legion`) discharges into the Chitauri around it.

## Priority and conflict rules

* One trigger point, first matching interaction in priority order wins (`ContextualInteractions.run`); a fired pair
  gets the interaction's cooldown and the stroke's aftermath (`InteractionGuard`).
* Interactions on the same trigger and target never overlap by design: the target kind (raised shield, Lion's Heart,
  Leviathan, Legion suit, meteor) and the attacker kind (Reid stroke vs Mjolnir in hand vs thrown Mjolnir vs NPC) are
  disjoint; the tests pin the boundary cases (thrown Mjolnir is not a melee Mjolnir strike, a broken shield is no
  guard, Thanos without a guard is not a guard break).
* Side effects (split repulsors, discharges, Leviathan chain lightning, meteor bursts) only reach what
  `InteractionTargets.mayHurt` allows: never the actor, his summons, pets or team, players only as PvP rivals.

## Rejected / not duplicated

* Chitauri bolt / DIO knife on the shield — the raised shield already reflects every frontal projectile
  (`CaptainHero.projectileResponse` → REFLECT).
* Meteor on Regulus — Lion's Heart already stops any projectile dead (`RegulusHero.captureProjectile`).
* Road Roller vs Hulkbuster — the roller's blows are deferred into the time-stop ledger; the ledger meeting is covered
  by `time_stone_rewinds_ledger` only where lore needs it (Time Stone).
* Homelander laser on the shield — the laser is a ray resolved by `HeroRayDefense`, not a hit or projectile event;
  needs its own trigger point (future).
* Plain damage multipliers / knockbacks were not added as interactions by design.
