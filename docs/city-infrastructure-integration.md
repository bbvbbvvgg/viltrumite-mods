# City infrastructure (Living City 3.0): integration guide

Power grid, blackouts and the city emergency of the living city. Code: `viltrumitecore/.../city/power/`
(server), `city/power/api/` (public API), `client/city/power/` (client only), packet
`network/packet/CityPowerS2CPacket`. The system only exists in a world with a prepared city (Atheria): every API
call is a no-op elsewhere, outside the city dimension and while `/city reset` runs.

## Model

| Piece | What it is |
|---|---|
| Cell | 48 x 48 blocks (3 x 3 chunks) of the city footprint. States: lit, surge (voltage dip), dark, damaged. |
| Substation | 3 x 3 cells (144 x 144 blocks). Has health 0..1; trips when worn out (≤ 0.42) or when 2/3 of its cells are dark. A trip darkens all its cells and may cascade to neighbours (load × 0.66 per step, depth ≤ 3, ≤ 6 trips per incident, 45–95 ticks between trips). A neighbour carrying load < 0.30 never gives way; above that the chance is `min(0.85, (load − 0.30) × 1.2)`, so one heavy hit usually trips 1–3 substations, not the whole ring. |
| District | Name from `CityProfile.districtAt` of the cell centre; used for subtitles and `OutageInfo`. |
| Fixture | A real light block in a loaded chunk (see "Lights"). Found by a lazy palette-filtered chunk scan (2 chunks/tick, ≤ 160 per chunk). |
| Incident | One hit on the grid (`InfrastructureIncident`): position, cause, source, radius, severity. Newest 16 are kept. |
| Alarm | City-wide emergency stage resolved from leases (`CityAlarm`), with up to 12 siren masts per district and up to 4 foci (the threats). |

A hit of severity `s` and radius `r` reaches `min(2.2 r + 24 s, 5.5 cells)` blocks. Each cell gets
`s × falloff(distance)`; ≥ 0.62 damaged (long repair, sparking transformer), ≥ 0.30 dark, ≥ 0.07 surge. The failure
travels outward at 3.5 blocks/tick (a rolling blackout), each cell flickers for 34 ticks before going dark. Damaged
cells come back after 2–3.6 min, dark ones after 35–92 s, tripped substations after 75–140 s (sooner after the
all-clear), staggered 16 ticks per
cell, each cell flickering back on. Rules: `PowerRules` (pure, unit tested in `PowerRulesTest`).

### Lights

Only real, reversible block states, written with flags `2 | 16` (client update, no neighbour updates/drops):

| Lit block | Without power |
|---|---|
| redstone lamp | `lit=false` |
| invisible `light` block | `level=0` |
| glowstone, sea lantern, shroomlight, 3 froglights | `viltrumitecore:dark_fixture` (`kind`, `axis` kept) |
| lantern / soul lantern | `viltrumitecore:dark_lantern` (`hanging`, `waterlogged`, `soul` kept) |
| end rod | `viltrumitecore:dark_end_rod` (`facing` kept) |
| jack o'lantern | carved pumpkin (`facing` kept) |

Torches, candles, campfires and fire are flames and stay lit. One fixture in nine (hash of its position) is on the
emergency circuit and keeps burning; the client draws a rotating red beacon on emergency fixtures of dark cells (at most one per chunk
and 6 per cell, looked up again every 5 s while a cell has fewer).

Invariants:
- The first switch of a fixture records its original state in the city `ChangeJournal`, so `/city reset` and the
  restore system bring it back exactly; switches themselves run with `ChangeJournal.suppressed` and never count as
  destruction (no bursts, no feedback loop).
- Switched fixtures are kept in a ledger (`<world>/data/viltrumite_city/power_ledger.dat`); after a restart dark
  fixtures are relit or kept dark to match the grid state. A fixture broken or replaced by a player is dropped from
  the ledger (it is only relit if it is still our dark block).
- Dark blocks have no loot table and are never placed outside the city.
- `/city reset` clears the grid first (`onCityReset`), then the journal restores the originals.
- Redstone lamps that are really powered by redstone relight on their next neighbour update (vanilla behaviour).

## Built-in triggers (`PowerTriggers`)

| Source | Hook | Effect |
|---|---|---|
| Meteor Rain 2.0 | `ExplosionEvent.Detonate` (LOW) with a `MeteorEntity` source | `damage(radius 12, severity 0.9, cause "meteor")` |
| Meteor Rain session | polled every 20 ticks | alarm lease `meteor:<owner>` WARNING in the first 160 ticks, then ACTIVE |
| Chitauri 2.0 explosions | `ExplosionEvent.Detonate`, source in the chitauri package | cause `chitauri`, severity from the destroyed block count |
| Chitauri invasion phase | polled | lease `chitauri`: OPENING → WARNING, GROUND/AIR → ACTIVE, LEVIATHAN/CLIMAX → CRITICAL |
| Any other explosion in the city | `ExplosionEvent.Detonate` | cause `explosion` |
| Lightning | `EntityJoinLevelEvent` of a `LightningBolt` | radius 4, severity 0.34 |
| Destruction bursts | `CityManager` burst stream (block-change journal) | `PowerGrid.onBurst`: ≥ 6 blocks in a chunk and tick, severity ≤ 0.75 |

Nothing in Meteor Rain, Chitauri, citizens or the restore system was rewritten: the grid listens to their events.
Citizens already react to the danger map of these events; the grid adds no AI of its own.

## API (`city.power.api`)

```java
// report damage (building collapse, Ultron beams, scripted events)
InfrastructureIncident inc = CityInfrastructure.damage(level, pos, 8.0F, 0.7F, "collapse", "mymod:tower_3");

// hold the emergency while a threat lasts; refresh before leaseTicks run out, clear when it is over
CityInfrastructure.raiseAlarm(level, "mymod:ultron", AlarmStage.CRITICAL, bossPos, 64.0F, 200);
CityInfrastructure.clearAlarm(level, "mymod:ultron");

// read the state
boolean lit = CityInfrastructure.powered(level, blockPos);
float dark = CityInfrastructure.darkFraction(level);
List<OutageInfo> districts = CityInfrastructure.outages(level);
AlarmStage stage = CityInfrastructure.alarmStage(level);
```

- `severity` 0..1: 0.3 darkens a block, 0.6 blows a transformer, 0.9 is a meteor strike. `radius` is the physical
  radius of the hit; the grid extends it along the cables.
- `cause` is a short id (`meteor`, `chitauri`, `explosion`, `lightning`, `collapse`, ...). Unknown causes are fine;
  the subtitles use a generic line for them. `source` is free text for logs and events.
- Leases: one per `sourceId`; the strongest active lease sets the stage. Stages: `NONE`, `WARNING`, `ACTIVE`,
  `CRITICAL`, `ALL_CLEAR`. When the last ACTIVE/CRITICAL lease ends the city sounds the all-clear for 30 s and the
  crews begin restoring power. A WARNING that ends without escalation goes quietly back to `NONE`.
- `damage` raises its own lease `incident:<cause>`: ACTIVE for 60 s at severity ≥ 0.45, WARNING for 30 s at
  ≥ 0.30. Plain `destruction` (a hero smashing walls) never warns and needs severity ≥ 0.60 for ACTIVE.
  Four or more meteor strikes within 60 s raise `meteor_storm` CRITICAL for 45 s.
- A substation cascade raises lease `cascade`: ACTIVE, CRITICAL once one incident has tripped
  `PowerRules.CRITICAL_TRIPS` (3) substations; the stage never drops while the lease lasts.
- Server thread only. Calls on the client side or without a city return null / defaults.

Events on `MinecraftForge.EVENT_BUS` (all `InfrastructureEvent`, server side):

| Event | When |
|---|---|
| `Damaged(incident, cells, transformers)` | an incident hit at least one cell; `transformers` are the blown transformer positions |
| `Outage(outage, cascade, incidentId)` | a district got darker; `cascade` when a substation trip caused it |
| `Restored(outage)` | a district got power back (partially or fully) |
| `AlarmChanged(previous, stage, reason)` | the city stage changed; `reason` is the lease source |

Use them for aftermath, quests, Ultron scripts, citizen panic lines, etc. Do not change fixture blocks yourself
while the grid owns them; call `damage` instead.

## Sound and subtitles

- 22 events `viltrumitecore:city_power.*` (category `ambient`), sources and cue sheet in
  `tools/assets/city_power_sources.md`. No vanilla sounds.
- Sirens are spatial: up to 3 nearest masts play at once, each a near/far crossfade (`PowerRules.sirenLevel`,
  `sirenNear`; audible up to 900 blocks, far variant past ~70 blocks). One siren type per stage (warning wail,
  attack rising/falling, critical three-tone), never stacked; the all-clear is a single steady tone. Klaxon only
  near CRITICAL foci (≤ 110 blocks), indoor blackout beeps, a low disaster bed during ACTIVE/CRITICAL, spark loops
  near the two closest damaged transformers. One-shots are delayed by the speed of sound (≤ 3 s).
- Spoken lines are subtitles only, through `HeroSpeech.announce` with the `city` voice (speaker
  `subtitle.viltrumitecore.speaker.city_defense`, portrait `CityDefensePortrait`), rate limited per district. No
  voice audio.

## Network and performance

- `CityPowerS2CPacket` (protocol 24): `STATE` (grid origin, non-lit cells packed 24+24+4 bits, transformers,
  emergency fixtures near the player, alarm stage, sites and foci), sent when dirty at most every 10 ticks, every
  600 ticks anyway, and to joining players; `FX` (impact, blow, cascade, arc) to players within 420 blocks.
- Server budgets per tick: 48 fixture writes, 768 state reads, 2 chunk scans, 64 timeline actions (queue ≤ 4096).
  Light updates are the real cost of a blackout; the write budget spreads them over ticks.
- Client: ≤ 96 beacons/glows drawn (224 blocks), ≤ 3 siren masts, ≤ 2 spark loops.

## Commands (`/city power`, op level of `/city`)

`status` · `blackout [radius]` · `damage <radius> <severity>` · `cascade` · `restore` ·
`siren warning|active|critical|clear` (lease `command`).
