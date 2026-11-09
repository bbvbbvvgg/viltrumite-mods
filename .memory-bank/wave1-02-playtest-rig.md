# Wave 1 - session 02 - playtest rig

Branch `wave1/02-playtest-rig`, PR #169 into `integration/viltrumite-2.0`.
User docs (Russian): `docs/playtest/rig.md`. Design: `docs/design/2026-11-wave1-02-playtest-rig.md`.

## What it is

`tools/playtest/rig.py` (Python 3, stdlib only) starts a dedicated server and N clients of
the mod on an offline headless machine (the offline kit: JDK, Gradle cache, Debian 11
Xvfb + Mesa llvmpipe + xdotool in `kit/client-runtime`), connects the clients, runs JSON
scenarios and writes a report (`kit/rig/reports/<time>-<name>/report.md|json`, shots, logs).

## Files

- `tools/playtest/rig.py` - the rig (up/run/down/status/guard/capture/rcon/qa/key/shot).
- `tools/playtest/scenarios/*.json` - quick, smoke-heroes (14 heroes), world-events
  (conquest, trio), city-atheria (Atheria map + `/city reset`).
- `tools/playtest/data/{heroes,events}.json` + `gen_data.py` - lists generated from sources
  (`HeroId`, `WorldEvents`).
- `viltrumitecore/.../command/PlaytestCommands.java` - `/playtest ping|heroes|who|hero`,
  registered only with `-Dviltrumitecore.playtest=true`, permission level 2. The only
  Java file of this session; no shared files touched.

## How it works (key decisions)

1. Capture once: run `:viltrumitecore:runServer` / `runClient` via the kit Gradle, find
   the NEW `java` process whose cmdline has `cpw.mods.bootstraplauncher.BootstrapLauncher`
   (it lives under the Gradle daemon, in another session id - do not match by sid), save
   argv/cwd/env to `rig/launch-*.json`, kill the whole Gradle process tree.
2. Then start the game JVMs directly from the saved argv (no Gradle daemon: saves ~2 GB RAM).
   Re-capture happens automatically when jars/classes are newer than the capture.
3. Server: own dir `rig/server`, generated `server.properties` (offline, RCON 25575,
   password `rig`, flat world). Ready = `Done (` in log + RCON answers.
4. Clients: own game dir `rig/clients/<nick>`, own Xvfb display (100-199, TCP + xauth),
   `--quickPlayMultiplayer 127.0.0.1:25565`, `options.txt` without accessibility screen.
   Joined = `<nick> joined the game` in the server log.
5. Client control: QA harness file `subtitle_qa.txt` (`SubtitlePlaytest`, flag
   `-Dviltrumitecore.subtitles.playtest=true`), xdotool keys, `import`/xwd screenshots.
6. Memory guard process: kills the newest client, then the server, when MemAvailable <
   350 MB. Client start is refused when MemAvailable < `--min-free-mb` (1500).

## Pitfalls found (all fixed in rig.py)

- `client.sh` LD_LIBRARY_PATH contains a literal `${LD_LIBRARY_PATH:+...}`; copying it
  glued garbage to the last path -> Xvfb could not load `libbz2.so.1.0`. rig strips it.
- The Debian Xvfb loads `swrast_dri.so` only from its compiled path
  `/usr/lib/x86_64-linux-gnu/dri` (LIBGL_DRIVERS_PATH is ignored by the server). On a
  non-Debian host there is no GLX -> client fails `GLX: Failed to find a suitable
  GLXFBConfig`. rig makes `client-runtime/usr/bin/Xvfb.rig`: a copy with that string
  replaced by the same-length relative `../lib/x86_64-linux-gnu/dri`, run with cwd
  `usr/bin`. `glxinfo -B` then shows llvmpipe, OpenGL 4.5.
- Xvfb does not accept `-verbose`.
- Never `pkill -f`/`pgrep -f` a pattern that is also in your own shell command: it kills
  the shell itself. Use `rig.py down --all` (matches by /proc exe + cmdline, skips self).

## Machine limits (agent sandbox, 4 GB RAM, 2 cores, no swap)

- Server ~1.16 GB RSS + one client ~1.3 GB RSS -> ~1.4 GB left. A second client does not
  fit (needs about 6 GB total). Two-client mode is implemented but NOT verified in game.
- The sandbox VM restarts about every 15 minutes and `/data` rolls back to an old
  snapshot. GitHub is the only durable storage: push every change at once. A restart
  needs: unpack kit (~1 min), restore files, build (~3.7 min), up (~1.5 min).
- Do not run Gradle while a client is running (OOM).

## Verified results

- `up --clients 1 --no-build`: server ready in 19.6 s, client Alpha joined in 27.7 s.
- `quick.json`: 8 PASS / 0 FAIL; idle 20.0 TPS, 11.6 mspt.
- `smoke-heroes.json`: first 9 heroes PASS (20.0 TPS, 2-4 mspt each) before a VM restart.
- Client FPS column in metrics was empty: `perfstate` output not parsed yet (open item).

## Known mod issues seen

- `Invalid path in pack: viltrumitecore:sounds/regulus/KENNEY_LICENSE.txt` (asset file
  with upper-case name inside `sounds/`; harmless, should be moved out of assets).
- `DedicatedServerProperties: No key layers in MapLike[{}]` - vanilla warning for empty
  flat generator settings; harmless.
