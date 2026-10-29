# Thor integration — first real game launch (2026-10-28)

Environment: Hark sandbox, aarch64, Debian 13, OpenJDK 17.0.21, Xvfb :99 1280x720, Mesa 25.0.7 llvmpipe
(software GL 4.5), LWJGL 3.3.1 arm64 natives from Maven Central via `-Dorg.lwjgl.librarypath` (the dev run
ships x64 natives only). No audio device: OpenAL failed to open, so **nothing was heard**.

| Check | Result |
|---|---|
| `compileJava compileTestJava` | green |
| `./gradlew test` | 1985 tests, 3 failed: RegulusVisualContractTest (needs liblwjgl, env), ThorClipContractTest and ThorClipBodyTest (fixed / reduced by the R2b bake, see below) |
| after R2b bake: ThorClipContractTest | passes |
| after R2b bake: ThorClipBodyTest | fails (belly ↔ thigh 2–5 px in bent poses, also R1 crouch; hand / hammer cases in `2026-10-28-thor-r2b-bake.md`) |
| `runServer` | `Done (5.644s)` |
| `runClient` | **game really launched**, singleplayer creative flat world, "Become Thor" |

## What was seen in game (screens in `screens/2026-10-28-thor/`)

- R1 body in third person: 3.2-block Thor, beard, belly, armour, Mjölnir in the right hand with rune glow.
- First person: own FP arms, Mjölnir in the right fist.
- LMB: the baked R2b hammer chain plays (wide wind-up, swing across the body, second strike). Arms read
  clearly; the legs barely move in these frames, and the right forearm passes close in front of the chest
  at the end of the backhand — to review in the owner's playtest.
- `V` opens a teleport-target camera ("LMB: teleport here · Esc: cancel"); `R` showed no visible change
  in the captured frames.

## Not checked in this session

Flight and hammer spin, throw / catch / recall, the two-hand lift from the ground, electric mode, WRATH,
VFX of strikes and bolts up close, sound (no device), multiplayer, 30/60/144/240 FPS (llvmpipe runs at a
few FPS). Owner playtest is still required; nothing here is "approved".
