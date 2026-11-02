# City structural collapse: integration guide

Core 1.68.0, protocol 26. Package `dev.baranhan.viltrumitecore.world.collapse` (server) and
`dev.baranhan.viltrumitecore.client.collapse` (client). This file is the contract for other systems: city
infrastructure, aftermath (fires, smoke, ruins), restoration, NPC panic and a future Ultron attack. Nothing in the
collapse code depends on those systems. They only listen to events and, if they damage buildings, wrap their block
changes in a damage scope.

## 1. What the system does

1. **Damage ledger.** Every block that a known cause removes (a damage scope, see §4) is written to a per-level
   `DamageLedger` (memory only, 8x8x8 cells, 60 s window, 32768 entries per level). Each entry keeps its weight,
   tick and cause.
2. **Settle.** When a damaged cluster gets no new damage for 8 ticks (3 ticks after an explicit impact), and its
   weighted score reaches `SCORE_THRESHOLD` (60), the director analyses it. One analysis per level per tick.
   Several buildings damaged in the same tick are analysed on the next ticks, one by one.
3. **Analysis** (`CollapseAnalyzer`, pure, unit tested). It reads one bounded box around the damage (at most
   25 x 25 blocks, 4 blocks down, 30 blocks up from the damage) and decides:
   - not a building (terrain above the damage centre, too little structure, lower than 6 blocks) → no collapse;
   - **SEVERED**: blocks that the flood fill from the box floor and box sides cannot reach hang in the air → they fall;
   - **OVERHANG**: a hole at least 3 storeys deep and 4 wide drops a band of floors above it;
   - **LOCAL**: heavy damage without a clear load path → only loose blocks over the hole crumble (≤ 160).
   A section above `collapseMaxBlocks` (1600) is truncated to the part nearest the damage.
4. **Timeline** (server owns every block change):
   phase 1 impact (6 ticks) → phase 2 creak (cracks, facade shedding, 20–40 ticks) → phase 3 the section is removed
   (≤ `collapseBlocksPerTick` per tick) and falls on clients → impact on the ground (impact damage, rubble ≤ 8 %
   / 160 blocks, up to 32 weak blocks broken) → phase 4 dust settles (200 ticks) → finished.
5. **Client** draws the section as at most 140 baked fragments (VBOs, one draw per fragment, no entities, no
   per-block physics), plus dust, debris, cracks, camera shake and 12 sound layers. LOD by distance: full ≤ 90
   blocks, reduced ≤ 170, minimal beyond, far clients (no chunks) get only the distant boom.

Variants (`CollapseVariant`, append-only): `TOPPLE`, `PANCAKE`, `FACADE_SHEAR`, `CRUMBLE`. Severity
(`CollapseSeverity`): `MINOR` ≤ 250 blocks, `MAJOR` ≤ 800, `CATASTROPHIC` above.

## 2. Events (`MinecraftForge.EVENT_BUS`, server thread)

Order for one collapse, always with the same `info().eventId()`:

| Event | When | `affectedBlocks` | `originalStates` |
|---|---|---|---|
| `CityCollapseEvent.Started` (cancelable) | analysis accepted, nothing changed yet | planned section | empty |
| `CityCollapseEvent.Collapsed` | phase 3: the section was removed | blocks really removed (shed + main fall) | their states before removal |
| `CityCollapseEvent.Impact` | the section hit the ground | every block changed so far (removed, rubble, broken weak blocks) | states before the change |
| `CityCollapseEvent.Finished` | dust settled **or** aborted (`aborted()`) | every block the collapse changed | complete record |

- `Finished` is posted exactly once for every started collapse, also when it is aborted (level unload, server
  stop, `/collapse clear`). A cancelled `Started` posts no other event; its damage is consumed and the region
  cools down for 400 ticks.
- Subscribe from any mod or branch without new dependencies:

```java
@SubscribeEvent
public static void onImpact(CityCollapseEvent.Impact e) {
   CollapseInfo info = e.info();
   AABB zone = info.landing();          // dust, fires, blackout area
   float strength = info.severity().strength(); // 0..1
}
```

## 3. `CollapseInfo` (record, fields are append-only)

| Field | Meaning |
|---|---|
| `eventId` | unique per server run, positive, increasing |
| `dimension` | level key |
| `center` | centre of the collapsing section |
| `radius` | horizontal danger radius (footprint + fall spread) |
| `severity` | `MINOR` / `MAJOR` / `CATASTROPHIC`, `strength()` 0..1 |
| `cause` | `CollapseCause` (`id()` is stable: `leviathan`, `meteor`, `chitauri`, `hero`, `explosion`, `command`, custom ids) |
| `affectedBlocks` | see the table in §2, in change order, immutable |
| `originalStates` | position → state right before the collapse changed it, immutable |
| `timestamp` | `level.getGameTime()` of the event |
| `variant` | visual family |
| `landing` | AABB where the section lands |
| `truncated` | true when the section was cut to the block limit |

Helper: `info.inZone(x, y, z)` — inside the danger radius or the landing box (NPC panic, AI danger zones).

## 4. Feeding damage: `StructuralDamage`

Block changes count only inside a **damage scope**. Without a scope a block change is ignored (world edit,
farming, pistons, the collapse itself).

```java
StructuralDamage.run(CollapseCause.HERO, () -> level.destroyBlock(pos, false)); // Runnable
boolean ok = StructuralDamage.call(CollapseCause.HERO, () -> level.setBlock(pos, air, 3)); // Supplier
StructuralDamage.orDefault(CollapseCause.HERO, () -> ...); // keeps an outer scope's cause, else uses HERO
StructuralDamage.impact(level, hitPos, CollapseCause.METEOR); // heavy hit: the cluster settles in 3 ticks
StructuralDamage.suppressed(() -> ...); // changes inside never feed the ledger
```

- New causes: `CollapseCause.custom("ultron", 1.0F)` (id is lower-cased, use a stable id such as `ultron` or
  `ultron:core_blast`; weight is clamped to 0..1; weight 0 = never feeds).
  Weights: leviathan 1.0, meteor 1.0, command 1.0, hero 0.8, chitauri 0.6, explosion 0.35.
- Explosions are recorded automatically (`ExplosionEvent.Detonate`, lowest priority, only when no scope is open):
  a meteor explosion is `meteor`, Chitauri explosions are `chitauri`, others `explosion`.
- Wired now: hero destruction (`HeroDestruction`, punch impact, meteor punch, thunderclap), Chitauri glass/crack/
  smash, Leviathan plough, segment landing and crash (crash also calls `impact`), meteors.
- The collapse names itself after the cause with the most recorded weight around the analysed spot, so two
  buildings hit in the same tick by different causes keep their own cause.

**Ultron hook (not implemented here):** wrap Ultron block damage in `StructuralDamage.run(ULTRON, …)` with
`ULTRON = CollapseCause.custom("ultron", 1.0F)` and call `StructuralDamage.impact` at each heavy hit. Nothing
else is needed; listen to the events for his own reactions.

## 4a. Support loss (#135): every removal, the whole structure

The damage ledger alone missed real players: mining, `/fill`, fire and plain `setBlock` have no scope, the box
above is only 30 rows tall and its top and sides count as support, and a big section was cut to 1600 blocks. Since
#135 a second, weightless ledger runs next to it:

1. **Support ledger** (`SupportLedger`, memory only, same cells/caps, 10 min window): every removed block of the
   `STRUCTURE` class is recorded, with or without a scope (not inside `StructuralDamage.suppressed`, not world
   generation). Config `collapseSupportLoss` (true) switches it off.
2. **Probe** (`SupportProbe`, pure, incremental, ≤ 5 ms per tick, one per level): when a cluster settles (8 quiet
   ticks, removals within 64 blocks / ±12 rows), a flood fill from the neighbours of the removed blocks walks the
   real structure, lowest layer first. It is **grounded** when it reaches a block below the cut, an unbreakable
   block, an unloaded chunk or 192 blocks away; a structure it exhausts is **floating**. A wall that still stands
   answers after a few dozen blocks; a severed tower costs its size (≤ 600 000 blocks, more is "undecided" = stays).
3. **Base cut** (`BaseCut`): Atheria buildings touch their neighbours (sky bridges, shared wings, walls against
   the next house), so a building cut through every wall still reaches the ground next door. The footprint of the
   cut (every column with a removed block + the rooms that ring encloses) restricts a second probe; the building
   inside falls when it stands on nothing of its own and at most 60 % of its outer faces touch a neighbour. A wall
   strip, an L of two facades, a doorway, a room dug into a solid block or a house wedged on three sides stays.
4. **Before check**: the same search with the removed blocks put back must reach the ground, so a sky platform
   that already floated is left alone.
5. **Chain** (`SupportChainPlan`): the floating structure comes down top first in stages of
   `2 x collapseMaxBlocks` (≤ 4000, packet axis ≤ 255) through the normal director timeline (`Rule.SUPPORT`, cause
   `support` unless another cause is recorded nearby); a stage starts every 5–14 ticks (a skyscraper in about 18 s),
   each lands on the street under the building. When a normal collapse finishes, what hung on its section is probed
   again (follow-up).
6. **Off**: with `collapseEnabled=false` or `mobGriefing=false` nothing falls and players within 96 blocks see one
   action-bar line why (`message.viltrumitecore.collapse.support_blocked.*`, once per 30 s).

## 5. Restoration

- Every block change of a collapse goes through `level.setBlock`, so the living city journal (`CityJournalMixin`)
  records it like any other damage: `/city reset` restores collapsed buildings inside the city.
- Outside the city, a restoration system can store `Finished.info().affectedBlocks()` + `originalStates()` and
  put them back (also valid for `aborted()` collapses: only the really changed blocks are listed).
- Collapse block changes are `suppressed`: restoring or collapsing never feeds the ledger again.

## 6. Hooks for other systems

| System | Event | What to read |
|---|---|---|
| Infrastructure (power, water, traffic lights) | `Impact` | `landing`, `radius`, `severity` |
| Aftermath (fires, smoke, ruins) | `Impact`, `Finished` | `landing`, `affectedBlocks` |
| NPC panic / AI danger | `Started` → `Finished` | `inZone`, `radius` |
| Protected zones | `Started` | cancel it |
| Statistics, quests | `Finished` | `cause`, `severity`, `aborted()` |

## 7. Config, commands, network

- `config/viltrumitecore.json`: `collapseEnabled` (true), `collapseMaxActive` (3), `collapseMaxBlocks` (1600),
  `collapseBlocksPerTick` (900). Missing keys are added on load. The gamerule `mobGriefing=false` also stops
  collapses.
- `config/viltrumitecore.json`: `collapseSupportLoss` (true): support-loss probes of §4a.
- `/collapse` (op 2): `status` (active, analyses, analysis/tick ms, ledger size, support probes/chains, probe ms and
  nodes, last result), `clear` (abort all, forget damage and removals), `analyze <pos>` (dry run),
  `support <pos>` (does the structure at `pos` reach anything below `pos`?), `strike <pos> <radius 1-8> [cause]`,
  `cut <from> <to> [cause]` (≤ 4096 blocks).
- Debug log: `-Dviltrumitecore.collapse.log=true` (every analysis with cost, every start/finish).
- `CityCollapseS2CPacket` (last packet of `CoreMessages`, protocol 26): `START` carries the section as a bitset
  over its bounding box (≈ 3 KB at 1600 blocks) and the timeline; `COLLAPSE`, `IMPACT`, `END` carry only the id.
  Sent to players within 320 blocks; beyond 160 blocks the start is marked `far`.

## 8. Limits (known, by design)

- Memory only: a server restart forgets running collapses and the ledger (changed blocks stay, journaled).
- The damage analysis box is still 25 x 25 x (4 + 30) blocks; what it misses (a tall tower cut at the base, the
  rest of a truncated section) is caught by the support probe of §4a.
- No loads are computed: a single remaining pillar or wall still holds a whole building (cut it too); interior
  stairs or partition walls that reach the ground floor hold a base-cut building.
- A structure bigger than 600 000 blocks, or one reaching an unloaded chunk or 192 blocks away, counts as standing.
- The support ledger is memory only (restart forgets removals) and probes one cluster per level at a time.
- Fragment light is sampled once at the start; translucent blocks (glass, ice) are drawn with the cutout sheet.
- Clients that do not have the chunks see only dust from far and hear the distant boom.
- Viltrumite flight dash damage (`PlayerEntityCoreMixin`) is not wrapped in a scope yet, so it does not feed the
  ledger.

## 9. Wired after the integration of #124–#127 (core 1.69.0, protocol 27)

`city/links/CityDisasterLinks` connects the three city systems (server side):

- `CityCollapseEvent.Impact` → `CityAftermath.registerCollapse(... "collapse:<eventId>" ..., box = landing)` and
  `CityInfrastructure.damage(center, radius, severity.strength(), "collapse", "collapse:<eventId>:<cause>")`.
  Cause, centre, radius, severity and event id are kept. One impact per event id (guard set + aftermath dedupes ids).
- From `Started` to `Finished` the city burst stream skips `PowerGrid.onBurst` inside `info.inZone(...)`, so the
  falling blocks hit the grid once, at the impact.
- `CollapseCause.ULTRON` ("ultron", weight 1.0); explosions of `entity.ultron.*` record as `ultron`.
