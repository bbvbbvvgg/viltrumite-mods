# Citizens in peril: falls, ledges, screams (issue #138)

People upstairs in tall buildings, who lose their balance when the floor gives way, grab a ledge, scream
while they fall, are caught by a flying hero or pulled up from the edge; witnesses cry out, the floors around
call for help and the street below panics. Server code in `city/citizen/` (`CitizenPeril`, `PerilRules`,
`CitizenVoices`, `CitizenPerilEvents`), occupants in `city/CityPopulation`, client poses in
`client/city/CitizenPoser` / `CitizenRenderer`, wall muffling in `client/city/CitizenVoiceOcclusion`.

## 1. States

Three synced `CitizenState`s were appended (the byte order of the old ones is unchanged):

| State | Who | Body | Ends |
|---|---|---|---|
| `TEETER` | stands on blocks a starting collapse will take | no walking, arms windmill, eyes down; screams / calls now and then | the floor goes (-> fall or ledge), or holds for `teeterTicks` (<= 160) -> `FLEEING` |
| `HANGING` | the ground went from under the feet and a ledge top was within reach (0.6-2.3 blocks above the feet) | fixed under the ledge, both hands up, legs kick; calls for help | a player within 2.5 blocks pulls it up; the time runs out -> climbs up (25-60 % by bravery) or lets go; the ledge breaks or it is hurt -> lets go |
| `FALLING` | a drop of 4+ blocks (scream from 6+) | flails, the renderer rocks the body +-55 degrees | lands alive -> `FALLEN` (vanilla fall damage applies); a flying player within 1.9 blocks catches it (slow falling 8 s, the fall scream is stopped) |

`peril()` states are `panicked()` and `contagious()` (people around get nervous). While one of them is active
`CitizenPeril.tick` owns the body and the brain sleeps (`CitizenEntity.tick`); `stateTicks` is counted there.
Grab chance: 55 % x (0.6 + 0.8 x bravery) when a collapse caused it, 30 % x ... otherwise, at most 85 %, at
most 4 people hanging per city. Hurting a hanging person makes it let go; hurting a teetering or falling one
keeps the peril state (no switch to plain fleeing mid-air).

## 2. Collapse hook

`CitizenPerilEvents` listens to `CityCollapseEvent.Started` at the lowest priority and skips cancelled events:

- citizens standing on a planned block of the section -> `TEETER` + a scream;
- other floors of the same building (section box inflated 12 / 6, above ground + 5) -> `ALERT`, up to 3 of them
  call for help after a short delay;
- 3+ citizens in the street around -> one `citizen.crowd_panic` at the crowd centre (cooldown 240 ticks).

Impact shelter (`CollapseRules.sheltered`, `CollapseDirector.impact`): the impact of a falling section only hurts
bodies with open air up to the section (or inside it) and the landing zone. Whoever has an intact floor above
is hit when that floor goes - in a support chain (#135) the top stage no longer kills every floor below at once,
the people downstairs teeter, fall or get crushed stage by stage.

Witnesses: when somebody starts to fall, up to 2 citizens within 28 blocks with a line of sight are `STARTLED`
and cry out a little later; a danger-map alert zone makes the rest of the street look and run.

## 3. Occupants

Atheria's towers have a floor every 4 blocks, but citizens only spawned on the street grid, so nobody could
fall from a skyscraper. Every 5 s each player gets up to `cityOccupants` (config, default 6, 0 = off, at most
16) people upstairs, two per pass, 10-48 blocks away: a column whose top is 20+ above the nearest street cell,
a structure floor (collapse volume `STRUCTURE`) at least 9 above it, under a roof, room for the body, no danger,
no fresh ruin. They count against the city caps and despawn like everybody else. `Occupy` lets them take a few
steps on the same floor now and then (flat path, under a roof; `CitizenNodeEvaluator` gives them the vanilla
walking rules, the street grid knows no interiors); they never walk back to the street
(`ReturnToGrid` skips them) and flee along the floor, away from the danger (`CitizenGoals.indoorPath`).
QA: `/city occupants <n>` (op) adds manual ones around the command position; `/city status` prints a peril line
(upstairs, teetered, fell, hung on, climbed, pulled up, caught, died, crowd panics). An occupant who ends up on
the street (fell, was caught) joins the crowd again. `-Dviltrumitecore.peril.log=true` logs every peril step
(teeter, fall with the drop, hang, climb / pull-up, let go, land, catch, deaths with their cause, voices, crowd).

## 4. Voices

Nine sound events (`citizen.scream|fall|help|witness.male|female`, `citizen.crowd_panic`), 27 files mixed from
CC0 Freesound recordings by `tools/sfx/city_citizens.py` (sources: `tools/assets/city_citizens_sources.md`), no
vanilla stand-ins, subtitles in all 12 locales (ru: "Житель кричит", "Падающий житель кричит", "Житель зовёт на
помощь", "Вскрик свидетеля", "Паника толпы"). The server plays a voice with `Level#playSound(null, entity, ...)`
(category neutral), so the client gets an entity-bound sound that follows the body - in single player and on a
dedicated server alike, no new packet. Volumes: fall 3.2 (heard ~51 blocks), scream 2.4, help 1.7, witness 1.3.
A per-level token bucket (6 voices burst, 0.2 per tick) caps the din; calls for help keep a reserve of 1.5
tokens, so a falling scream is never crowded out.

Client: `CitizenVoiceOcclusion` wraps every `citizen.*` sound in a tickable instance whose volume follows a
two-way ray test camera <-> mouth every 4 ticks: open 100 %, one thin wall or window 45 %, thick walls 25 %,
eased (35 % per step).

## 5. Limits

- No audio device in the build sandbox: the voices were picked and mixed by measurement (energy, F0,
  spectrograms), not by ear. Two "help" takes are Portuguese ("socorro"), the rest English or wordless.
- Occlusion is a ray test, not acoustics: no reverb, no diffraction around corners.
- Occupants stand on floors; there is no furniture-aware indoor life (offices, elevators, stairs) and no
  evacuation down the stairs: they run along their floor, away from the danger.
- A falling scream of a person who screamed while teetering may overlap that scream for a moment.
