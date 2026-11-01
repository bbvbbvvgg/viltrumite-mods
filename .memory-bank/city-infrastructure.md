# Living City 3.0: power grid, blackouts, city emergency (core 1.66.0, protocol 24)

API and rules: `docs/city-infrastructure-integration.md`. Sounds and sources: `tools/assets/city_power_sources.md`.

## Code map
- Server `city/power/`: `PowerGrid` (cells 48x48, substations 3x3 cells, incidents, timeline), pure `PowerRules`
  (unit tested, `PowerRulesTest`), `FixtureSwitch` (real block swaps, ledger `viltrumite_city/power_ledger.dat`,
  once into the city journal so `/city reset` relights), `CityAlarm` (leases → stage), `PowerTriggers`
  (meteor / Chitauri / destruction bursts), `PowerCommands` (`/city power ...`), API `CityInfrastructure`.
- Client `client/city/power/`: `CityPowerClient` (state packet), `CityPowerAudio` (sirens, klaxon, sparks, one-shots),
  emergency beacons, flicker/flash FX. Packet `CityPowerS2CPacket` is appended last (protocol 24).
- Subtitles only through `HeroSpeech` (speaker CIVIL DEFENCE, siren portrait); sound subtitles via sounds.json.

## Tuning decisions (from Atheria playtests)
- `darkFraction` uses urban cells (cells with a district), not all grid cells.
- Cascade: neighbour load < 0.30 never trips; chance `min(0.85, (load-0.30)*1.2)`. Before this every 0.9 hit
  tripped ~6 substations and went CRITICAL. Cascade lease CRITICAL only at `CRITICAL_TRIPS` = 3 per incident.
- Plain `destruction` (hero smashing walls at spawn) needs sev ≥ 0.6 for ACTIVE and never WARNs, otherwise the
  login plaza of Atheria raises an emergency on its own.
- ≥ 4 meteor strikes in 60 s → `meteor_storm` CRITICAL 45 s; all-clear ~45-60 s after the rain stops.
- Emergency beacons: one per chunk, 6 per cell, re-lookup every 100 ticks (chunks load late).
- Epicentre one-shots were > 0 dBFS when stacked; transformer blow 0.7, arc 0.7, low impact 0.45.

## Atheria facts
- Downtown glass pyramid (~1376-1391, -40..-48, -209..-224) is full of end rods: best blackout test spot
  (`tp 1384 -44 -250 0 -8`, night). The map hides ~7000 invisible `minecraft:light` blocks; they are switched too.
- Grid: 13406 urban cells. Never commit the map.

## QA harness (sandbox, not in repo)
- `/data/qa/launch.sh snap|server|client <n> <user>`: dedicated server on a world copy (RCON pw `qa`), Xvfb
  client. Memory fits only server + ONE client (each ~1.8-2 GB RSS): the 2-client sync test was not possible.
- OpenAL wave capture (`alsoft.conf` `drivers=wave`) gives a float32 WAV to measure levels; set music to 0 in
  options.txt or vanilla music looks like a "stuck" sound. Software rendering: 1-6 FPS, not a perf signal.
- `pkill -f` with a pattern that occurs in the own command line kills the shell; kill by PID.
- The dedicated server may hang after "stop" (all chunks saved) - kill the PID.
