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
- `/collapse` (op 2): `status` (active, analyses, analysis/tick ms, ledger size), `clear` (abort all, forget damage),
  `analyze <pos>` (dry run), `strike <pos> <radius 1-8> [cause]`, `cut <from> <to> [cause]` (≤ 4096 blocks).
- Debug log: `-Dviltrumitecore.collapse.log=true` (every analysis with cost, every start/finish).
- `CityCollapseS2CPacket` (last packet of `CoreMessages`, protocol 26): `START` carries the section as a bitset
  over its bounding box (≈ 3 KB at 1600 blocks) and the timeline; `COLLAPSE`, `IMPACT`, `END` carry only the id.
  Sent to players within 320 blocks; beyond 160 blocks the start is marked `far`.

## 8. Limits (known, by design)

- Memory only: a server restart forgets running collapses and the ledger (changed blocks stay, journaled).
- The analysis box is 25 x 25 x (4 + 30) blocks. A tower that is taller than the box above the damage touches
  the box top and counts as supported, so a cut at the base of a very tall tower drops nothing; the hit must be
  within 30 blocks of what should fall.
- A truncated section leaves the rest of the structure hanging; the next damage there starts a new analysis.
- A thin column or a single wall still connects a section to the ground: the heuristic does not compute loads.
- Fragment light is sampled once at the start; translucent blocks (glass, ice) are drawn with the cutout sheet.
- Clients that do not have the chunks see only dust from far and hear the distant boom.
- Viltrumite flight dash damage (`PlayerEntityCoreMixin`) is not wrapped in a scope yet, so it does not feed the
  ledger.
