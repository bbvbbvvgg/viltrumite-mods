# Hero roster scale: the action set

Wave 1 · session 01. Core 1.72.0, network protocol 32.

## Problem

The merged roster has more than 64 `HeroAction` values. Before 1.72.0:

- `HeroPublicSnapshot.availableActions` was one `long`. Bit `n` was action `n`.
  Actions 64+ had no bit. Java `1L << 64` is `1L << 0`, so a naive check of action 64 read action 0.
- `HeroInputC2SPacket` sent the action ordinal as one signed byte. Ordinal 128+ became a negative id,
  `HeroAction.byId` returned `null` and the key press was lost without an error.

## Contract (for hero authors)

1. Check availability only with `snapshot.actionAvailable(HeroAction)` (or `actionAvailable(int ordinal)`).
   This API did not change. It is exact for every ordinal.
2. Most heroes offer every action. Keep passing `-1L` to the `long` constructors, or `ActionSet.ALL`.
   `ALL` also covers actions that are appended later.
3. To offer only some actions, build a set and use the canonical constructor or `withActions`:

   ```java
   ActionSet set = HeroPublicSnapshot.actionSet(HeroAction.LIONS_HEART, HeroAction.VENOM_TAKEOVER);
   HeroPublicSnapshot narrowed = snapshot.withActions(set);
   ```

4. Do not use `HeroPublicSnapshot.actionMask(...)` in new code. It is deprecated. It still works for
   ordinals 0..63 and throws `IllegalArgumentException` for 64+ with a message that points to `actionSet`.
5. `availableActions()` is a legacy `long` view: `-1L` for `ALL`, else bits 0..63. Ordinals 64+ are not
   in it. Never copy a snapshot through this view; use `actions()`.
6. `HeroAction` and `HeroId` stay append-only (rule A1). The set stores ordinals.

## `ActionSet` (`hero/roster/ActionSet.java`)

Immutable. Two kinds:

- `ALL`: every action, now and later.
- explicit: any ordinals in `[0, ORDINAL_LIMIT)`, `ORDINAL_LIMIT = 32768`. Stored as `long[]` words,
  trimmed (no trailing zero word), so `equals` and `hashCode` are exact.

| Method | Notes |
| --- | --- |
| `of(HeroAction...)`, `ofOrdinals(int...)`, `range(from, to)` | factories; bad ordinal → `IllegalArgumentException` |
| `fromLegacyMask(long)` | `-1L` → `ALL`, `0` → `NONE` |
| `contains(HeroAction)`, `contains(int)` | negative → `false` |
| `with`, `without`, `union` | return a new set (or the same set if nothing changes) |
| `size()`, `span()`, `ordinals()`, `forEachOrdinal` | `ALL` uses the current roster size |
| `toBytes()` / `fromBytes(byte[])` | binary form |
| `writeTo(FriendlyByteBuf)` / `readFrom(FriendlyByteBuf)` | for future packets |
| `toWire()` / `fromWire(String)` | snapshot token |

### Binary form

- tag `0`: `ALL` (1 byte).
- tag `1`, dense: varint byte count, then the bit set as little-endian bytes.
- tag `2`, sparse: varint count, then varint gaps `ordinal - previous - 1` (previous starts at -1).

The encoder picks the shorter form (dense on a tie). The decoder rejects an unknown tag, truncated
input, trailing bytes, a varint longer than 5 bytes, and any size past `ORDINAL_LIMIT`. Errors are
`NumberFormatException`, so `HeroPublicSnapshot.decode` falls back to `EMPTY` as for any bad field.

### Snapshot token

Field 20 of the packed snapshot string:

- `-1`: `ALL`. This is byte-for-byte the old value, so today's traffic does not change.
- `~` + unpadded base64url of the binary form: an explicit set. It never contains `;`.
- a decimal `long`: the old mask. Still accepted on read.

The snapshot is a synced string (`HERO_SNAPSHOT`), not NBT. No save migration is needed.

## Key packet

`HeroInputC2SPacket` writes the ordinal with `writeVarInt`. Ordinals 0..127 still take 1 byte, so the
packet is still 2 bytes. Ordinals 128..16383 take 2 bytes.

## Protocol

`CoreMessages.PROTOCOL_VERSION` is `"32"`. The packet table did not change, but the key packet and the
snapshot field did, so a `"31"` peer is refused at login.

## Limits that remain (per hero, not roster-wide)

- `AbilityPages`: 3 pages × 6 slots per hero. `HeroInputC2SPacket.equipped` assumes this (`* 6`, `< 18`).
- `HeroPublicSnapshot.COOLDOWN_COUNT = 6`, `EXTRA_COOLDOWN_MAX = 32`, `heroFlags` is 32 bits.
- `1 << ordinal()` on an `int` in `VenomBossBrain`, `AftermathCause`, `ThanosStone`: internal enums, not hero actions.
- `ThorSceneC2SPacket` and `SceneSync` write internal enum ordinals as a byte: small fixed enums.

## Tests

- `hero/roster/ActionSetTest`: a synthetic roster of 256 actions, boundary ordinals
  (63/64/127/128/255/256/32767), 500 random sets through bytes, token and `FriendlyByteBuf`,
  malformed and oversized input.
- `hero/HeroPublicSnapshotTest`: every real action masked alone survives encode/decode with no
  aliasing; `ALL` keeps the `-1` token; bad tokens decode to `EMPTY`.
- `network/packet/HeroInputVarintTest`: every action and synthetic ordinals 128..16384 survive the key
  packet; small ordinals keep the 2-byte size.
