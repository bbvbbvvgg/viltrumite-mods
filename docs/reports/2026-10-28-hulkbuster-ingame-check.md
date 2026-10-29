# Hulkbuster rework — real game launch and jar (2026-10-28)

Environment: aarch64 sandbox, OpenJDK 17.0.21, Xvfb 1280x720, Mesa llvmpipe (software GL 4.5), LWJGL 3.3.1
arm64 natives via `-Dorg.lwjgl.librarypath`. No audio device (OpenAL off): **no sound was heard**.

| Check | Result |
|---|---|
| `./gradlew build` | jars built: `reforgedcodex-core-1.51.0.jar`, `reforgedcodex-flight-1.10.1.jar` |
| `./gradlew test` | 1926 tests, 1 failed: RegulusVisualContractTest (needs x64 liblwjgl in the unit-test JVM; environment) |
| `runClient` | game launched, singleplayer creative flat world, Iron Man |

## Seen in game (`screens/2026-10-28-hulkbuster/`)

- Veronica menu: pod call, Mark 7 deploy, then the Hulkbuster card (deploy is refused until a mark is worn — by design).
- Ground suit-up: the full-size Sind Mark 48 frame from the first frame, plates close, "Hulkbuster online".
  No small suit growing, no vanilla armour pieces.
- **No glowing squares on the chest** in third person (front view).
- Relog: the suit is still on after leaving and re-entering the world.
- LMB combo on an iron golem: swing, contact dust and debris.
- Grab (RMB) on a zombie: victim lifted at head height, Grip bar on the HUD, jackhammer arm raised for the punch.

## Fixed after the check

- Contact / repulsor / reactor flashes were one flat 1.4-block white square: now clusters of small additive motes (`glowBurst`).
- HUD: ability state column overlapped long names ("Heavy repulsorsready").

## Not checked

Air suit-up variant, exit and break-up, spare arm, dash / jet jump / hover / dive, repulsors, rift, cage,
Jet Ram, damage stages, multiplayer view, frame-rate sweep, sound. In third-person front view the camera sits
inside the suit during the scene (camera distance is not scaled for a 3.4-block body). The Veronica card text
still describes the stage-5 kit. Owner playtest required.
