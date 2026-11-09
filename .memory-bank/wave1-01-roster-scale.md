# Wave 1 · session 01 — roster scale

- Branch `wave1/01-roster-scale`, PR #168 into `integration/viltrumite-2.0`.
- `hero/roster/ActionSet` replaces the 64-bit action mask in `HeroPublicSnapshot` (component `actions`).
- Snapshot token: `-1` = ALL (unchanged bytes), `~<base64url>` = explicit set, decimal long still read.
- `HeroInputC2SPacket` ordinal is a varint. `PROTOCOL_VERSION` = 32. Core 1.72.0.
- `actionMask(...)` is deprecated (0..63 only); use `HeroPublicSnapshot.actionSet(...)` + `withActions`.
- Contract: `docs/architecture/hero-roster-scale.md`.
- Gotcha: the sandbox may stop during a Gradle build (seen twice). Push before every build.
