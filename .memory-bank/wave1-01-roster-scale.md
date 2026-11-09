# Wave 1 · session 01 — roster scale

- Branch `wave1/01-roster-scale`, PR #168 into `integration/viltrumite-2.0`.
- `hero/roster/ActionSet` replaces the 64-bit action mask in `HeroPublicSnapshot` (component `actions`).
- Snapshot token: `-1` = ALL (unchanged bytes), `~<base64url>` = explicit set, decimal long still read.
- `HeroInputC2SPacket` ordinal is a varint. `PROTOCOL_VERSION` = 32. Core version stays 1.71.1 (the orchestrator sets versions, D1).
- `actionMask(...)` is deprecated (0..63 only); use `HeroPublicSnapshot.actionSet(...)` + `withActions`.
- Contract: `docs/architecture/hero-roster-scale.md`.
- Gotcha: the sandbox may stop during a Gradle build (seen twice). Push before every build.
- Gotcha: never leave a symlink in `/data` that points outside `/data` (e.g. `/data/proj -> ~/kit/project`).
  The sandbox backup restore then fails with ENOENT and the computer stays unusable.
