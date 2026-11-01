# ULTRON – ВОССТАНИЕ МАШИН (core 1.65.0, protocol 23, branch feat/ultron-uprising)

Plan: `docs/plans/2026-11-01-ultron-uprising.md`. Sources / cue sheet: `tools/assets/ultron_sources.md`.

## Shape
- Server `worldevent/ultron/`: `UltronUprising` (static facade, one `UltronSession` at a time, Forge events), `UltronDirector` (phase clock, spawns, nodes, upload, endings), `UltronNetwork` (node graph: links ≤ 3 within `LINK_RANGE`, power by BFS from the core), `UltronAdaptation` + `UltronLearning` (protocols from observed tactics), `UltronOrders` (doctrine), `UltronRoles`, `UltronProduction`, `UltronCapture` (golems, Legion suits), `UltronVoice` (subtitle lines), `UltronHud` (boss bars, titles), `UltronSavedData`.
- Entities `entity/ultron/`: `UltronUnit` base (drone, sentry, brute, interceptor, engineer), `UltronPrime` (boss, stages from `UltronBossRules`), `UltronNodeEntity`, `UltronBolt`, `UltronMissile`. Units and nodes are `shouldBeSaved() == false`: the session data rebuilds them after a restart.
- Client `client/ultron/`: one renderer per model on `UltronUnitRenderer` (layered pose: base loops → move clip → additive hurt/turns), `UltronLoops` (every loop sound), `UltronVfx`/`UltronWorldFx`, `UltronHudOverlay`.
- Commands (op 2): `/ultron start [seed <n>] [force] | end victory|defeat | stop | status | nodes | reveal | phase <key> | adapt <protocol> | spawn <kind>`.
- Packets: `UltronStateS2CPacket` (lists capped: 16 nodes, 8 links, 16 captured; forged sizes throw) and `UltronFxS2CPacket` (kinds 0..`LAST`=31, contiguous).

## Invariants
- Moves (`UltronMove`) carry their clip name; the server applies damage / sound / FX on the contact tick. Prime plays `stagger` for `HEAVY_HIT` (`UltronPrimeRenderer.clipFor`).
- Large geo / clip JSON and atlases ship split under `binassets` (`<file>.partN`, `<file>.b64[.partN]`), joined by Gradle; tests must read them through the joiner in `UltronAssetsTest`, not `resources/`.
- Every renderer string literal must be a bone or a clip of its model (`UltronAssetsTest.renderersFindTheirClipsAndBones`).
- Loop files are periodic with no attack or tail: only `UltronLoops` may play the five loops; one-shot accents use bursts (`prime_dash`, `interceptor_boost`).
- No voice: a line is `HeroSpeech.worldLine` (subtitle, world voice "ultron") plus the `transmission` blip; every `UltronVoice` line must have a trigger (literal id or a composed prefix such as `"protocol_" + key`).
- Line flow: only ULTIMATE phase / boss beats are forced (`say(..., true)`, they cut the line on screen; `UltronVoiceTest.onlyUltimateBeatsAreForced`); every unforced ABILITY line (captures, losses, protocols, doctrine, upload) goes through `sayNext` (now when quiet, else queued: ≤ `QUEUE_MAX` 3, stale after 20 s; a news line cut within 2 s by a beat is re-queued at the head); AMBIENT remarks are skipped while he reads; taunts rotate through `UltronVoice.TAUNTS` (6, longest-unsaid first, 300 s cooldown each, tried every `TAUNT_EVERY_S` 40 s while machines fight).
- Machines never chase or pick targets beyond the leash ring (`UltronUprising.beyondLeash`: area radius × `UltronDirector.LEASH` around the anchor).
- `UltronSession.upload` counts ticks: show `uploadShare()` (0..1), never `upload * 100`. Node states in `/ultron` output go through `viltrumitecore.ultron.node_state.<state>`.
- Turned shots: Reflect makes the hero the owner of an `UltronBolt` / `UltronMissile`; `UltronBolt.turned(owner)` / `hits(owner, e)` then target Ultron's own (never the captured golems / suits). `UltronMissile` is `Homing` (flares, Reflect) and homes on any entity, not just living ones.
- Reserve: a removed body is never `isAlive()`; prune / save use `UltronRules.backToReserve(destroyed, outcome, health)`. Non-saved entities are not removed by a chunk unload (1.20.1 `PersistentEntitySectionManager` only unloads `shouldBeSaved` ones): machines in an unloaded area stay hidden, counted, and resume on reload (checked in game). `save()` puts builds in the foundries and queued spawns into the saved reserve (`UltronRules.savedReserve`), the live arrays stay as they are.
- Node health is reset every tick (the hull lives in the network): hero clean damage reaches it through `hero/CleanDamageSink` (`HeroDamage.applyCleanDamage` hands the hit over).
- Captures are never saved: `onServerStopping` releases golems / suits before the final save, a suit's hits go into the `LegionOverride` shell so the roster durability is untouched.
- Translation keys: `viltrumitecore.ultron.*`, `commands.viltrumitecore.ultron.*`, `subtitles.viltrumitecore.ultron.<event>` (sounds), `subtitle.viltrumitecore.ultron.<line>[.<page>]` (lines). Only `%s`, `%n$s`, `%%` format; the status lines use positional args.

## Sound kit
- `tools/sfx/ultron.py` imports the primitives of `tools/sfx/chitauri.py`; recordings are three Kenney CC0 packs cached by `--fetch` in `~/.cache/viltrumite-sfx/ultron/kenney` (Openverse API timed out at the proxy in this sandbox, 2026-11). `--ledger` prints the recordings table for the sources file.
- Levels are measured per role, not auditioned (unverified by ear).
