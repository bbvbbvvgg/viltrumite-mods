# Wave 1 · session 01 — roster scale

- Branch `wave1/01-roster-scale`, PR #168 into `integration/viltrumite-2.0`.
- `hero/roster/ActionSet` replaces the 64-bit action mask in `HeroPublicSnapshot` (component `actions`).
- Snapshot token: `-1` = ALL (unchanged bytes); a set within ordinals 0..63 stays the old decimal long (old strings re-encode byte for byte, `HeroIdentityTest` relies on it); otherwise `~<base64url>`.
- `HeroInputC2SPacket` ordinal is a varint. `PROTOCOL_VERSION` = 32. Core version stays 1.71.1 (the orchestrator sets versions, D1).
- `actionMask(...)` is deprecated (0..63 only); use `HeroPublicSnapshot.actionSet(...)` + `withActions`.
- Contract: `docs/architecture/hero-roster-scale.md`.
- Gotcha: the sandbox may stop during a Gradle build (seen twice). Push before every build.
- Gotcha: never leave a symlink in `/data` that points outside `/data` (e.g. `/data/proj -> ~/kit/project`).
  The sandbox backup restore then fails with ENOENT and the computer stays unusable.
- Verified 2026-11-09: local `./run.sh build` green on the kit (3411 tests passed, 2m50s); `HeroIdentityTest` legacy-token round trip is the regression to watch.
