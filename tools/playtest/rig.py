#!/usr/bin/env python3
"""viltrumite playtest rig: dedicated server + N offline clients on private Xvfb displays,
scripted JSON scenarios, metrics (TPS/MSPT, FPS/frametime, memory) and a report.

Quick start (details: docs/playtest/rig.md):
  tools/playtest/rig.py up --clients 2          # build, server, two clients, auto-join; returns when ready
  tools/playtest/rig.py run tools/playtest/scenarios/quick.json
  tools/playtest/rig.py down

Other commands: capture, status, shot, qa, key, rcon, report.
Only Python 3 stdlib.  Long commands (up/run) should be started in the background by agents:
  nohup tools/playtest/rig.py run SCEN.json > ~/rig/run.log 2>&1 &
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import signal
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent.parent


def _kit() -> Path:
    env = os.environ.get("RIG_KIT")
    if env:
        return Path(env).expanduser().resolve()
    if (PROJECT.parent / "run.sh").is_file() and (PROJECT.parent / "client-runtime").is_dir():
        return PROJECT.parent
    return Path.home() / "kit"


KIT = _kit()
RT = KIT / "client-runtime"
RIG_HOME = Path(os.environ.get("RIG_HOME", str(KIT / "rig"))).expanduser()
STATE = RIG_HOME / "state.json"
MAIN = "cpw.mods.bootstraplauncher.BootstrapLauncher"
TARGETS = {"server": "forgeserveruserdev", "client": "forgeclientuserdev"}
TASKS = {"server": ":viltrumitecore:runServer", "client": ":viltrumitecore:runClient"}
FINGERPRINT_FILES = ["build.gradle", "settings.gradle", "gradle.properties",
                     "viltrumitecore/build.gradle", "viltrumiteflight/build.gradle"]
ENV_SKIP = re.compile(
    r"^(PATH|PWD|OLDPWD|SHLVL|_|HOME|USER|LOGNAME|SHELL|TERM|HOSTNAME|DISPLAY|XAUTHORITY|LD_LIBRARY_PATH|"
    r"LIBGL_\w*|ALSOFT_DRIVERS|JAVA_HOME|GRADLE_USER_HOME|JAVA_TOOL_OPTIONS|MAIL|HIST\w*|LESS\w*|which_declare|"
    r"BASH_FUNC\w*.*)$|TOKEN|SECRET|PASS|KEY|NOTION|AWS|GITHUB|CERT|CA_BUNDLE|CAINFO|CAFILE|SSL", re.I)
DEFAULT_NAMES = ["Alpha", "Bravo", "Charlie", "Delta"]


class RigError(Exception):
    pass


def log(msg: str) -> None:
    print(f"[rig {dt.datetime.now():%H:%M:%S}] {msg}", flush=True)


def now_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")


# ---------------------------------------------------------------- processes

def read_cmdline(pid: int) -> list[str]:
    try:
        raw = Path(f"/proc/{pid}/cmdline").read_bytes()
    except OSError:
        return []
    return [p.decode("utf-8", "replace") for p in raw.split(b"\0")[:-1]]


def read_environ(pid: int) -> dict[str, str]:
    out: dict[str, str] = {}
    try:
        raw = Path(f"/proc/{pid}/environ").read_bytes()
    except OSError:
        return out
    for item in raw.split(b"\0"):
        if b"=" in item:
            k, v = item.split(b"=", 1)
            out[k.decode("utf-8", "replace")] = v.decode("utf-8", "replace")
    return out


def java_pids() -> list[int]:
    pids = []
    for d in Path("/proc").iterdir():
        if not d.name.isdigit():
            continue
        try:
            if os.path.basename(os.readlink(d / "exe")) == "java":
                pids.append(int(d.name))
        except OSError:
            pass
    return pids


def ppid_of(pid: int) -> int:
    try:
        stat = Path(f"/proc/{pid}/stat").read_text()
        return int(stat.rsplit(")", 1)[1].split()[1])
    except (OSError, ValueError, IndexError):
        return 0


def descends_from(pid: int, root: int) -> bool:
    for _ in range(64):
        if pid == root:
            return True
        if pid <= 1:
            return False
        pid = ppid_of(pid)
    return False


def descendants(root: int) -> list[int]:
    out = []
    for d in Path("/proc").iterdir():
        if d.name.isdigit() and int(d.name) != root and descends_from(int(d.name), root):
            out.append(int(d.name))
    return out


def kill_tree(root: int | None, grace: float = 10.0) -> None:
    """Kill a process group and every descendant. Gradle daemons call setsid(), so killing
    only the group of the Gradle wrapper leaves the daemon and the game JVM running."""
    if not root:
        return
    kids = descendants(root)
    groups = [root]
    for k in kids:
        try:
            g = os.getpgid(k)
        except OSError:
            continue
        if g not in groups:
            groups.append(g)
    for g in groups:
        kill_group(g, grace=grace)
    for k in kids:
        try:
            os.kill(k, signal.SIGKILL)
        except OSError:
            pass


def alive(pid: int | None) -> bool:
    if not pid:
        return False
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    try:
        for line in Path(f"/proc/{pid}/status").read_text().splitlines():
            if line.startswith("State:"):
                return "Z" not in line.split()[1]
    except OSError:
        return False
    return True


def rss_mb(pid: int | None) -> float | None:
    if not pid:
        return None
    try:
        for line in Path(f"/proc/{pid}/status").read_text().splitlines():
            if line.startswith("VmRSS:"):
                return round(int(line.split()[1]) / 1024, 1)
    except OSError:
        return None
    return None


def meminfo_mb() -> dict[str, float]:
    out = {}
    try:
        for line in Path("/proc/meminfo").read_text().splitlines():
            k, v = line.split(":", 1)
            if k in ("MemTotal", "MemAvailable", "SwapTotal", "SwapFree"):
                out[k] = round(int(v.split()[0]) / 1024, 1)
    except OSError:
        pass
    return out


def kill_group(pgid: int | None, grace: float = 15.0) -> None:
    if not pgid:
        return
    try:
        os.killpg(pgid, signal.SIGTERM)
    except OSError:
        return
    end = time.time() + grace
    while time.time() < end:
        try:
            os.killpg(pgid, 0)
        except OSError:
            return
        time.sleep(0.3)
    try:
        os.killpg(pgid, signal.SIGKILL)
    except OSError:
        pass


def spawn(argv: list[str], cwd: Path, env: dict[str, str], logfile: Path) -> subprocess.Popen:
    logfile.parent.mkdir(parents=True, exist_ok=True)
    fh = open(logfile, "ab")
    try:
        return subprocess.Popen(argv, cwd=str(cwd), env=env, stdout=fh, stderr=subprocess.STDOUT,
                                stdin=subprocess.DEVNULL, start_new_session=True)
    finally:
        fh.close()


def port_busy(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.3)
        return s.connect_ex(("127.0.0.1", port)) == 0


# ---------------------------------------------------------------- logs

class LogFile:
    """Append-only log with byte offsets, so checks can look only at output after an action."""

    def __init__(self, path: Path):
        self.path = Path(path)

    def size(self) -> int:
        try:
            return self.path.stat().st_size
        except OSError:
            return 0

    def read_from(self, offset: int) -> str:
        try:
            with open(self.path, "rb") as fh:
                fh.seek(offset)
                return fh.read().decode("utf-8", "replace")
        except OSError:
            return ""

    def find(self, regex: str, since: int = 0) -> re.Match | None:
        return re.search(regex, self.read_from(since), re.M)

    def wait(self, regex: str, timeout: float, since: int = 0, guard=None) -> re.Match | None:
        end = time.time() + timeout
        pos = since
        buf = ""
        rx = re.compile(regex, re.M)
        while True:
            chunk = self.read_from(pos)
            if chunk:
                pos += len(chunk.encode("utf-8", "replace"))
                buf = (buf + chunk)[-2_000_000:]
                m = rx.search(buf)
                if m:
                    return m
            if time.time() >= end:
                return None
            if guard is not None and not guard():
                return None
            time.sleep(0.4)


# ---------------------------------------------------------------- rcon

class Rcon:
    def __init__(self, port: int, password: str, timeout: float = 20.0):
        self.port, self.password, self.timeout = port, password, timeout
        self.sock: socket.socket | None = None
        self.req = 0

    def _send(self, kind: int, body: str) -> int:
        self.req += 1
        data = struct.pack("<ii", self.req, kind) + body.encode("utf-8") + b"\0\0"
        assert self.sock is not None
        self.sock.sendall(struct.pack("<i", len(data)) + data)
        return self.req

    def _recv_exact(self, n: int) -> bytes:
        assert self.sock is not None
        buf = b""
        while len(buf) < n:
            part = self.sock.recv(n - len(buf))
            if not part:
                raise ConnectionError("rcon closed")
            buf += part
        return buf

    def _recv(self) -> tuple[int, int, str]:
        (length,) = struct.unpack("<i", self._recv_exact(4))
        payload = self._recv_exact(length)
        rid, kind = struct.unpack("<ii", payload[:8])
        return rid, kind, payload[8:-2].decode("utf-8", "replace")

    def connect(self) -> None:
        self.close()
        self.sock = socket.create_connection(("127.0.0.1", self.port), timeout=self.timeout)
        rid = self._send(3, self.password)
        got, _, _ = self._recv()
        if got == -1 or got != rid:
            raise RigError("rcon auth failed")

    def close(self) -> None:
        if self.sock:
            try:
                self.sock.close()
            except OSError:
                pass
        self.sock = None

    def cmd(self, command: str) -> str:
        for attempt in range(2):
            try:
                if self.sock is None:
                    self.connect()
                rid = self._send(2, command)
                _, _, body = self._recv()
                return body
            except (OSError, ConnectionError, struct.error):
                self.close()
                if attempt == 1:
                    raise
                time.sleep(1)
        return ""


# ---------------------------------------------------------------- launch snapshots

def fingerprint() -> str:
    h = hashlib.sha256()
    for rel in FINGERPRINT_FILES:
        p = PROJECT / rel
        if p.is_file():
            h.update(rel.encode())
            h.update(p.read_bytes())
    h.update(str(KIT).encode())
    return h.hexdigest()[:16]


def capture(kind: str, timeout: float = 900) -> dict:
    """Start the ForgeGradle run task once, copy the real game JVM command line, kill it.
    Later launches skip Gradle (saves ~1 GB RAM per process and ~1 minute per start)."""
    RIG_HOME.mkdir(parents=True, exist_ok=True)
    # Small Gradle heap: the game JVM is killed right after it appears, so Gradle is the only big process.
    cmd = [str(KIT / "run.sh"), TASKS[kind], "-Dorg.gradle.jvmargs=-Xmx1536m", "--max-workers=2"]
    before = set(java_pids())
    logpath = RIG_HOME / f"capture-{kind}.log"
    log(f"capture {kind}: {' '.join(cmd)} (log {logpath})")
    p = spawn(cmd, KIT, dict(os.environ), logpath)
    found = None
    end = time.time() + timeout
    try:
        while found is None:
            if time.time() > end:
                raise RigError(f"capture {kind}: no {TARGETS[kind]} JVM within {timeout:.0f}s, see {logpath}")
            if p.poll() is not None:
                raise RigError(f"capture {kind}: gradle exited with {p.returncode}, see {logpath}")
            for pid in java_pids():
                # Gradle starts the game from its daemon, which has its own session (setsid),
                # so match any new game JVM instead of the session id.
                if pid in before:
                    continue
                argv = read_cmdline(pid)
                if TARGETS[kind] in argv and MAIN in argv:
                    found = pid
                    break
            time.sleep(0.3)
        argv = read_cmdline(found)
        env = read_environ(found)
        cwd = os.readlink(f"/proc/{found}/cwd")
    finally:
        if found:
            try:
                os.kill(found, signal.SIGKILL)
            except OSError:
                pass
        kill_tree(p.pid, grace=5)
    keep = {k: v for k, v in env.items() if not ENV_SKIP.search(k) and os.environ.get(k) != v}
    data = {"kind": kind, "captured": now_iso(), "fingerprint": fingerprint(), "kit": str(KIT),
            "cwd": cwd, "argv": argv, "env": keep}
    out = RIG_HOME / f"launch-{kind}.json"
    out.write_text(json.dumps(data, indent=1))
    log(f"capture {kind}: saved {out} ({len(argv)} args, env {sorted(keep)})")
    return data


def load_launch(kind: str, auto: bool = True) -> dict:
    path = RIG_HOME / f"launch-{kind}.json"
    data = None
    if path.is_file():
        data = json.loads(path.read_text())
        if data.get("fingerprint") != fingerprint():
            log(f"launch-{kind}.json is stale (build files changed)")
            data = None
        elif not Path(data["argv"][0]).exists():
            data = None
    if data is None:
        if not auto:
            raise RigError(f"no launch snapshot for {kind}; run: rig.py capture")
        data = capture(kind)
    return data


def set_arg(args: list[str], key: str, value: str | None) -> list[str]:
    out = list(args)
    if key in out:
        i = out.index(key)
        if value is None:
            del out[i:i + 2]
        else:
            out[i + 1] = value
    elif value is not None:
        out += [key, value]
    return out


def build_argv(snap: dict, heap_mb: int, props: list[str], game_dir: Path,
               overrides: dict[str, str | None], extra: list[str]) -> list[str]:
    argv = list(snap["argv"])
    if MAIN not in argv:
        raise RigError("launch snapshot has no BootstrapLauncher main class")
    i = argv.index(MAIN)
    java, jvm, prog = argv[0], argv[1:i], argv[i + 1:]
    drop = ("-Xmx", "-Xms", "-Dforge.logging.console.level=", "-Dfile.encoding=", "-Dsun.stdout.encoding=")
    jvm = [a for a in jvm if not a.startswith(drop)]
    head = [f"-Xmx{heap_mb}m", "-Xms256m", "-Dforge.logging.console.level=info",
            "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8"]
    head += [p for p in props if p]
    prog = set_arg(prog, "--gameDir", str(game_dir))
    for k, v in overrides.items():
        prog = set_arg(prog, k, v)
    return [java] + head + jvm + [MAIN] + prog + extra


# ---------------------------------------------------------------- server / clients

SERVER_PROPS = {
    "online-mode": "false", "enable-rcon": "true", "rcon.password": "rig", "rcon.port": "25575",
    "server-port": "25565", "level-name": "world", "level-type": "minecraft\\:flat",
    "generate-structures": "false", "spawn-protection": "0", "allow-flight": "true",
    "view-distance": "6", "simulation-distance": "4", "max-players": "8", "enable-command-block": "true",
    "difficulty": "easy", "motd": "viltrumite playtest rig", "sync-chunk-writes": "false",
    "max-tick-time": "180000", "broadcast-rcon-to-ops": "false", "enforce-secure-profile": "false",
    "spawn-npcs": "false",
}

CLIENT_OPTIONS = {
    "onboardAccessibility": "false", "renderDistance": "5", "simulationDistance": "5", "maxFps": "60",
    "enableVsync": "false", "graphicsMode": "0", "renderClouds": '"false"', "pauseOnLostFocus": "false",
    "narrator": "0", "tutorialStep": "none", "skipMultiplayerWarning": "true", "joinedFirstServer": "true",
    "soundCategory_music": "0.0", "fullscreen": "false", "autoJump": "false", "guiScale": "2",
    "realmsNotifications": "false", "hideMatchedNames": "false", "lang": "en_us",
}


def write_kv(path: Path, values: dict[str, str], sep: str, keep_existing: bool = True) -> None:
    lines: dict[str, str] = {}
    if keep_existing and path.is_file():
        for line in path.read_text(errors="replace").splitlines():
            if sep in line and not line.startswith("#"):
                k, v = line.split(sep, 1)
                lines[k] = v
    lines.update(values)
    path.write_text("".join(f"{k}{sep}{v}\n" for k, v in lines.items()))


def rt_env() -> dict[str, str]:
    env = {
        "LD_LIBRARY_PATH": f"{RT}/usr/lib/x86_64-linux-gnu:{RT}/lib/x86_64-linux-gnu",
        "LIBGL_DRIVERS_PATH": f"{RT}/usr/lib/x86_64-linux-gnu/dri",
        "LIBGL_ALWAYS_SOFTWARE": "1",
        "ALSOFT_DRIVERS": "null",
    }
    # Prefer the kit's own client.sh values when they differ (read once, cheap).
    try:
        text = (KIT / "client.sh").read_text()
        for key in ("LD_LIBRARY_PATH", "LIBGL_DRIVERS_PATH"):
            m = re.search(rf'export {key}="?([^"\n]+)"?', text)
            if m and "$" in m.group(1):
                val = m.group(1).replace("${RT}", str(RT)).replace("$RT", str(RT)).replace(
                    "${KIT}", str(KIT)).replace("$KIT", str(KIT))
                # drop shell expansions such as ${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}; left in place
                # they glue onto the last directory and hide it from the loader (libbz2 not found)
                val = re.sub(r"\$\{[^}]*\}", "", val)
                parts = [x for x in val.split(":") if x and "$" not in x]
                if parts:
                    env[key] = ":".join(parts)
    except OSError:
        pass
    return env


XVFB_DRI_COMPILED = b"/usr/lib/x86_64-linux-gnu/dri"
XVFB_DRI_RELATIVE = b"../lib/x86_64-linux-gnu/dri"  # resolved from the cwd RT/usr/bin


def xvfb_binary() -> Path:
    """The kit's Debian Xvfb loads swrast_dri.so for GLX only from its compiled path
    /usr/lib/x86_64-linux-gnu/dri (LIBGL_DRIVERS_PATH is ignored by the server). On hosts
    without that directory GLX has no visuals and Forge fails with 'GLX: Failed to find a
    suitable GLXFBConfig'. Make a copy with a same-length relative path instead."""
    orig = RT / "usr/bin/Xvfb"
    if (Path(XVFB_DRI_COMPILED.decode()) / "swrast_dri.so").exists():
        return orig
    patched = orig.with_name("Xvfb.rig")
    if patched.exists() and patched.stat().st_mtime >= orig.stat().st_mtime:
        return patched
    data = orig.read_bytes()
    old = XVFB_DRI_COMPILED + b"\0"
    if data.count(old) != 1:
        log("warning: Xvfb DRI path not found, GLX may have no visuals")
        return orig
    new = data.replace(old, XVFB_DRI_RELATIVE + b"\0" * (len(old) - len(XVFB_DRI_RELATIVE)))
    tmp = patched.with_name("Xvfb.rig.tmp")
    tmp.write_bytes(new)
    tmp.chmod(0o755)
    tmp.replace(patched)
    log(f"made {patched.name}: GLX drivers from {RT / 'usr/lib/x86_64-linux-gnu/dri'}")
    return patched


def start_xvfb(session: Path, size: str = "1280x720x24") -> tuple[int, str, dict[str, str]]:
    if session.exists():
        shutil.rmtree(session, ignore_errors=True)
    session.mkdir(parents=True, mode=0o700)
    xauth = session / "Xauthority"
    xauth.touch(mode=0o600)
    cookie = os.urandom(16).hex()
    env = dict(os.environ)
    env.update(rt_env())
    env["XAUTHORITY"] = str(xauth)
    xbin = RT / "usr/bin"
    for n in range(100, 200):
        if Path(f"/tmp/.X{n}-lock").exists() or port_busy(6000 + n):
            continue
        disp = f"127.0.0.1:{n}"
        subprocess.run([str(xbin / "xauth"), "-f", str(xauth), "add", disp, "MIT-MAGIC-COOKIE-1", cookie],
                       env=env, check=True, capture_output=True)
        args = [str(xvfb_binary()), f":{n}", "-screen", "0", size, "-listen", "tcp", "-nolisten", "unix",
                "-auth", str(xauth), "-nolisten", "local"]
        xkb = RT / "usr/share/X11/xkb"
        if xkb.is_dir():
            args += ["-xkbdir", str(xkb)]
        fonts = RT / "usr/share/fonts/X11/misc"
        if fonts.is_dir():
            args += ["-fp", str(fonts)]
        p = spawn(args, xbin, env, session / "xvfb.log")
        denv = dict(env)
        denv["DISPLAY"] = disp
        for _ in range(20):
            if p.poll() is not None:
                break
            r = subprocess.run([str(xbin / "xdotool"), "getdisplaygeometry"], env=denv, capture_output=True)
            if r.returncode == 0:
                (session / "env").write_text("".join(
                    f"export {k}={v}\n" for k, v in denv.items()
                    if k in ("DISPLAY", "XAUTHORITY", "LD_LIBRARY_PATH", "LIBGL_DRIVERS_PATH", "LIBGL_ALWAYS_SOFTWARE")))
                return p.pid, disp, denv
            time.sleep(0.5)
        kill_group(p.pid, grace=2)
    raise RigError(f"Xvfb did not start (see {session / 'xvfb.log'})")


def guard_loop(min_mb: float, interval: float = 1.0) -> int:
    """Separate process started by `up`. The machine has no swap, and running out of memory
    can kill everything (on the sandbox VM it restarted the whole machine). When MemAvailable
    drops below min_mb, kill the newest client; if no client is left, stop the server."""
    idle = 0
    log(f"memory guard on: min {min_mb:.0f} MB")
    while True:
        st = load_state()
        srv = st.get("server") or {}
        live = sorted((c.get("started", ""), n, c) for n, c in (st.get("clients") or {}).items()
                      if alive(c.get("pid")))
        if not live and not alive(srv.get("pid")):
            idle += 1
            if idle > 60:
                log("memory guard off: nothing running")
                return 0
        else:
            idle = 0
        avail = meminfo_mb().get("MemAvailable", 1e9)
        if avail < min_mb and (live or alive(srv.get("pid"))):
            if live:
                _, name, c = live[-1]
                what, rss = f"client {name}", rss_mb(c.get("pid"))
                kill_group(c.get("pid"), grace=1)
                kill_group(c.get("xvfb_pid"), grace=1)
            else:
                what, rss = "server", rss_mb(srv.get("pid"))
                kill_group(srv.get("pid"), grace=2)
            ev = {"t": now_iso(), "killed": what, "rss_mb": rss, "mem_available_mb": round(avail)}
            with open(RIG_HOME / "guard-events.jsonl", "a") as fh:
                fh.write(json.dumps(ev) + "\n")
            log(f"memory guard: MemAvailable {avail:.0f} MB < {min_mb:.0f} MB, killed {what} (rss {rss} MB)")
            time.sleep(3)
        time.sleep(interval)


def start_guard(min_mb: int) -> int | None:
    if min_mb <= 0:
        return None
    p = spawn([sys.executable, str(Path(__file__).resolve()), "guard", "--min-mb", str(min_mb)],
              RIG_HOME, dict(os.environ), RIG_HOME / "guard.log")
    return p.pid


def load_state() -> dict:
    if STATE.is_file():
        try:
            return json.loads(STATE.read_text())
        except ValueError:
            pass
    return {"server": None, "clients": {}}


def save_state(state: dict) -> None:
    RIG_HOME.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps(state, indent=1))


def prepare_world(server_dir: Path, world: str) -> None:
    target = server_dir / "world"
    if world == "keep":
        return
    if target.exists():
        shutil.rmtree(target)
    if world in ("fresh", "flat", ""):
        return
    src = Path(world).expanduser()
    if src.is_file() and src.suffix == ".zip":
        import zipfile
        tmp = server_dir / "world-unzip"
        shutil.rmtree(tmp, ignore_errors=True)
        with zipfile.ZipFile(src) as z:
            z.extractall(tmp)
        levels = sorted(tmp.rglob("level.dat"), key=lambda p: len(p.parts))
        if not levels:
            raise RigError(f"no level.dat inside {src}")
        shutil.move(str(levels[0].parent), str(target))
        shutil.rmtree(tmp, ignore_errors=True)
    elif src.is_dir() and (src / "level.dat").is_file():
        shutil.copytree(src, target)
    else:
        raise RigError(f"--world: not a world dir/zip: {src}")
    log(f"world copied from {src} (disposable copy in {target})")


class Rig:
    def __init__(self, opts: argparse.Namespace | None = None, state: dict | None = None):
        self.opts = opts
        self.state = state or load_state()
        srv = self.state.get("server") or {}
        self.rcon = Rcon(int(srv.get("rcon_port", 25575)), srv.get("rcon_password", "rig"))

    # -- server
    @property
    def server_log(self) -> LogFile:
        return LogFile(RIG_HOME / "server" / "console.log")

    def server_alive(self) -> bool:
        return alive((self.state.get("server") or {}).get("pid"))

    def start_server(self) -> None:
        o = self.opts
        d = RIG_HOME / "server"
        d.mkdir(parents=True, exist_ok=True)
        (d / "eula.txt").write_text("eula=true\n")
        props = dict(SERVER_PROPS)
        props["server-port"] = str(o.port)
        props["rcon.port"] = str(o.rcon_port)
        if o.world not in ("fresh", "flat"):
            props.pop("level-type")
            props.pop("generate-structures")
        for kv in o.server_prop or []:
            k, _, v = kv.partition("=")
            props[k] = v
        write_kv(d / "server.properties", props, "=", keep_existing=False)
        prepare_world(d, o.world)
        for port in (o.port, o.rcon_port):
            if port_busy(port):
                raise RigError(f"port {port} is busy: another server is running (rig.py down --all)")
        snap = load_launch("server")
        argv = build_argv(snap, o.server_heap, ["-Dviltrumitecore.playtest=true"] + (o.jvm or []), d,
                          {}, [])
        env = dict(os.environ)
        env.update(snap["env"])
        logp = d / "console.log"
        if logp.exists():
            logp.rename(d / "console.prev.log")
        mark = 0
        p = spawn(argv, d, env, logp)
        self.state["server"] = {"pid": p.pid, "port": o.port, "rcon_port": o.rcon_port, "rcon_password": "rig",
                                "dir": str(d), "log": str(logp), "started": now_iso(), "world": o.world,
                                "heap_mb": o.server_heap}
        save_state(self.state)
        log(f"server pid {p.pid} starting (heap {o.server_heap} MB, log {logp})")
        t0 = time.time()
        m = self.server_log.wait(r"Done \(([\d.,]+)s\)!", o.server_timeout, mark, guard=lambda: alive(p.pid))
        if not m:
            raise RigError(f"server did not reach 'Done' in {o.server_timeout}s "
                           f"(alive={alive(p.pid)}); tail: {tail(logp)}")
        self.state["server"]["ready_s"] = round(time.time() - t0, 1)
        self.rcon = Rcon(o.rcon_port, "rig")
        for _ in range(20):
            try:
                self.rcon.connect()
                break
            except (OSError, RigError):
                time.sleep(1)
        save_state(self.state)
        log(f"server ready in {self.state['server']['ready_s']} s")

    # -- clients
    def start_client(self, name: str) -> None:
        o = self.opts
        avail = meminfo_mb().get("MemAvailable", 0)
        need = o.client_heap + 700
        if self.state["clients"] and avail < max(o.min_free_mb, need):
            raise RigError(f"not enough memory for client {name}: MemAvailable {avail:.0f} MB < "
                           f"{max(o.min_free_mb, need)} MB")
        d = RIG_HOME / "clients" / name
        d.mkdir(parents=True, exist_ok=True)
        opts = dict(CLIENT_OPTIONS)
        opts["lang"] = o.lang
        opts["renderDistance"] = str(o.render_distance)
        write_kv(d / "options.txt", opts, ":")
        (d / "subtitle_qa.txt").write_text("")
        xpid, disp, denv = start_xvfb(d / ".session")
        snap = load_launch("client")
        env = denv
        env.update(snap["env"])
        props = ["-Dviltrumitecore.subtitles.playtest=true", "-Dviltrumitecore.playtest=true",
                 f"-Drig.client={name}"] + (o.jvm or [])
        argv = build_argv(snap, o.client_heap, props, d,
                          {"--username": name, "--width": "1280", "--height": "720", "--uuid": None},
                          ["--quickPlayMultiplayer", f"127.0.0.1:{o.port}"])
        logp = d / "console.log"
        if logp.exists():
            logp.rename(d / "console.prev.log")
        mark = self.server_log.size()
        p = spawn(argv, d, env, logp)
        self.state["clients"][name] = {"pid": p.pid, "xvfb_pid": xpid, "display": disp, "dir": str(d),
                                       "log": str(logp), "session": str(d / ".session"),
                                       "started": now_iso(), "heap_mb": o.client_heap}
        save_state(self.state)
        log(f"client {name} pid {p.pid} on {disp} (heap {o.client_heap} MB)")
        t0 = time.time()
        m = self.server_log.wait(rf"{re.escape(name)} joined the game", o.client_timeout, mark,
                                 guard=lambda: alive(p.pid) and self.server_alive())
        if not m:
            raise RigError(f"client {name} did not join in {o.client_timeout}s (alive={alive(p.pid)}); "
                           f"tail: {tail(logp)}")
        self.state["clients"][name]["join_s"] = round(time.time() - t0, 1)
        save_state(self.state)
        if not o.no_op:
            self.rcon.cmd(f"op {name}")
        log(f"client {name} joined in {self.state['clients'][name]['join_s']} s")

    def client(self, name: str) -> "ClientHandle":
        c = self.state["clients"].get(name)
        if not c:
            raise RigError(f"unknown client {name!r}; running: {sorted(self.state['clients'])}")
        return ClientHandle(name, c)

    def clients(self, spec=None) -> list["ClientHandle"]:
        names = list(self.state["clients"])
        if spec in (None, "first"):
            return [self.client(names[0])] if names else []
        if spec in ("*", "all"):
            return [self.client(n) for n in names]
        if isinstance(spec, list):
            return [self.client(n) for n in spec]
        return [self.client(spec)]

    # -- teardown
    def down(self, everything: bool = False) -> None:
        srv = self.state.get("server") or {}
        for name, c in list(self.state.get("clients", {}).items()):
            kill_group(c.get("pid"), grace=8)
            kill_group(c.get("xvfb_pid"), grace=3)
            shutil.rmtree(c.get("session", "/nonexistent"), ignore_errors=True)
            log(f"client {name} stopped")
        if srv.get("pid") and alive(srv["pid"]):
            try:
                self.rcon.cmd("stop")
                end = time.time() + 40
                while alive(srv["pid"]) and time.time() < end:
                    time.sleep(0.5)
            except (OSError, RigError, ConnectionError):
                pass
            kill_group(srv["pid"], grace=10)
            log("server stopped")
        if everything:
            for pid in java_pids():
                cl = " ".join(read_cmdline(pid))
                if str(RIG_HOME) in cl or "GradleWrapperMain" in cl or MAIN in cl:
                    try:
                        os.killpg(os.getpgid(pid), signal.SIGKILL)
                    except OSError:
                        pass
            subprocess.run(["pkill", "-f", str(RT / "usr/bin/Xvfb")], capture_output=True)
            shutil.rmtree(KIT / ".client-session", ignore_errors=True)
        kill_group(self.state.get("guard_pid"), grace=2)
        self.state = {"server": None, "clients": {}}
        save_state(self.state)


def tail(path: Path, n: int = 6) -> str:
    try:
        lines = Path(path).read_text(errors="replace").splitlines()[-n:]
    except OSError:
        return "(no log)"
    return " | ".join(l[-200:] for l in lines)


class ClientHandle:
    def __init__(self, name: str, info: dict):
        self.name, self.info = name, info
        self.dir = Path(info["dir"])
        self.log = LogFile(Path(info["log"]))
        self.pid = info["pid"]

    def alive(self) -> bool:
        return alive(self.pid)

    def env(self) -> dict[str, str]:
        env = dict(os.environ)
        env.update(rt_env())
        env["DISPLAY"] = self.info["display"]
        env["XAUTHORITY"] = str(Path(self.info["session"]) / "Xauthority")
        return env

    def qa(self, line: str, confirm: bool = True, timeout: float = 12) -> bool:
        """Send one line to SubtitlePlaytest (client/hero/speech/qa) through <gameDir>/subtitle_qa.txt."""
        rx = r"SUBPT t=\d+ exec '" + re.escape(line.strip()) + "'"
        for attempt in range(2):
            mark = self.log.size()
            with open(self.dir / "subtitle_qa.txt", "a", encoding="utf-8") as fh:
                fh.write(line.strip() + "\n")
            if not confirm:
                time.sleep(0.5)
                return True
            if self.log.wait(rx, timeout, mark, guard=self.alive):
                time.sleep(0.3)
                return True
        return False

    def xdo(self, *args: str) -> subprocess.CompletedProcess:
        return subprocess.run([str(RT / "usr/bin/xdotool"), *args], env=self.env(), capture_output=True, text=True)

    def key(self, keys: str, hold_ms: int = 150) -> None:
        hold = max(120, int(hold_ms)) / 1000
        for k in keys.split():
            self.xdo("keydown", k)
            time.sleep(hold)
            self.xdo("keyup", k)
            time.sleep(0.08)

    def shot(self, out: Path) -> bool:
        out.parent.mkdir(parents=True, exist_ok=True)
        r = subprocess.run([str(KIT / "jdk/bin/java"), "-cp", str(RT), "ScreenCapture", str(out)],
                           env=self.env(), capture_output=True, text=True, timeout=60)
        return r.returncode == 0 and out.is_file()


# ---------------------------------------------------------------- metrics

TPS_RX = re.compile(r"Overall\s*:\s*Mean tick time:\s*([\d.]+)\s*ms\.\s*Mean TPS:\s*([\d.]+)")
NUM_RX = re.compile(r"([A-Za-z][\w.%/]*)\s*[=:]\s*(-?\d+(?:\.\d+)?)")


def server_tps(rig: Rig) -> dict:
    try:
        text = rig.rcon.cmd("forge tps")
    except (OSError, ConnectionError, RigError) as e:
        return {"error": str(e)}
    m = TPS_RX.search(text)
    if not m:
        return {"raw": text[:300]}
    return {"mspt": float(m.group(1)), "tps": float(m.group(2))}


def parse_perf(line: str) -> dict:
    out: dict = {"raw": line.strip()[:400]}
    for k, v in NUM_RX.findall(line):
        out.setdefault(k, float(v))
    return out


def client_perf(c: ClientHandle, label: str) -> dict:
    mark = c.log.size()
    if not c.qa(f"perfstate {label}"):
        return {"error": "perfstate not acknowledged"}
    # The QA ack line "SUBPT t=N exec 'perfstate <label>'" also contains the label, so match
    # only the FrameProbe line itself: "SUBPT t=N perf <label> frames=... avgFps=...".
    m = c.log.wait(r"^.*SUBPT t=\d+ perf " + re.escape(label) + r"\s.*avgFps=.*$", 8, mark)
    if not m:
        m = c.log.wait(r"^.*SUBPT t=\d+ perf \S*\s.*avgFps=.*$", 4, mark)
    return parse_perf(m.group(0)) if m else {"error": "no perf line"}


# ---------------------------------------------------------------- scenarios

VAR_RX = re.compile(r"\$\{([\w.]+)\}")


def subst(value, vars_: dict):
    if isinstance(value, str):
        def rep(m):
            cur = vars_
            for part in m.group(1).split("."):
                if isinstance(cur, dict) and part in cur:
                    cur = cur[part]
                else:
                    return m.group(0)
            return str(cur)
        return VAR_RX.sub(rep, value)
    if isinstance(value, list):
        return [subst(v, vars_) for v in value]
    if isinstance(value, dict):
        return {k: (v if k in ("steps", "do") else subst(v, vars_)) for k, v in value.items()}
    return value


def load_json(path: Path) -> object:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def resolve_items(spec, base: Path):
    """foreach source: a list, or "@file.json" / "@file.json:key.sub" relative to the scenario."""
    if isinstance(spec, list):
        return spec
    if isinstance(spec, str) and spec.startswith("@"):
        fname, _, key = spec[1:].partition(":")
        data = load_json((base / fname) if not Path(fname).is_absolute() else Path(fname))
        for part in filter(None, key.split(".")):
            data = data[part]
        return data
    raise RigError(f"foreach: unsupported source {spec!r}")


STEP_KINDS = ("include", "foreach", "server", "qa", "chat", "wait", "key", "mouse", "shot", "expect",
              "metrics", "note", "mark", "rejoin", "section")


class Runner:
    def __init__(self, rig: Rig, scenario_path: Path, out_dir: Path, vars_: dict):
        self.rig = rig
        self.path = scenario_path
        self.out = out_dir
        self.vars = vars_
        self.checks: list[dict] = []
        self.metrics: list[dict] = []
        self.shots: list[dict] = []
        self.notes: list[str] = []
        self.sections: list[dict] = []
        self.marks: dict[str, dict[str, int]] = {}
        self.action_mark: dict[str, int] = {}
        self.aborted: str | None = None
        self.t0 = time.time()
        self.step_no = 0
        self.section = "main"
        self.dead_clients: set[str] = set()

    # -- helpers
    def logs(self) -> dict[str, LogFile]:
        out = {"server": self.rig.server_log}
        for c in self.rig.clients("*"):
            out[c.name] = c.log
        return out

    def snapshot_offsets(self) -> dict[str, int]:
        return {k: v.size() for k, v in self.logs().items()}

    def check(self, name: str, ok: bool, detail: str = "", soft: bool = False) -> None:
        rec = {"n": len(self.checks) + 1, "section": self.section, "name": name, "ok": bool(ok),
               "soft": soft, "detail": detail[:500], "t": round(time.time() - self.t0, 1)}
        self.checks.append(rec)
        mark = "PASS" if ok else ("WARN" if soft else "FAIL")
        log(f"{mark} [{self.section}] {name}" + (f" -- {detail[:160]}" if detail and not ok else ""))

    def health(self) -> None:
        if not self.rig.server_alive():
            self.aborted = "server process died"
            self.check("server alive", False, tail(Path(self.rig.state["server"]["log"])))
            return
        for c in self.rig.clients("*"):
            if c.name not in self.dead_clients and not c.alive():
                self.dead_clients.add(c.name)
                self.check(f"client {c.name} alive", False, tail(c.log.path))

    def targets(self, step: dict, default="first") -> list[ClientHandle]:
        spec = step.get("client", default)
        return [c for c in self.rig.clients(spec) if c.name not in self.dead_clients]

    # -- execution
    def run_file(self, path: Path, vars_: dict) -> None:
        data = load_json(path)
        steps = data["steps"] if isinstance(data, dict) else data
        local = dict(vars_)
        if isinstance(data, dict):
            local.update(data.get("vars", {}))
        self.run_steps(steps, local, path.parent)

    def run_steps(self, steps: list, vars_: dict, base: Path) -> None:
        for raw in steps:
            if self.aborted:
                return
            self.health()
            if self.aborted:
                return
            step = subst(raw, vars_)
            kind = next((k for k in STEP_KINDS if k in step), None)
            if kind is None:
                raise RigError(f"unknown step {raw!r}")
            self.step_no += 1
            try:
                getattr(self, "do_" + kind)(step, vars_, base)
            except RigError as e:
                self.check(f"step {self.step_no} {kind}", False, str(e), soft=bool(step.get("soft")))
                if step.get("fatal"):
                    self.aborted = f"fatal step failed: {e}"

    def do_section(self, step, vars_, base):
        self.section = str(step["section"])
        self.sections.append({"name": self.section, "t": round(time.time() - self.t0, 1)})
        log(f"== {self.section}")
        if step.get("steps"):
            self.run_steps(step["steps"], vars_, base)

    def do_include(self, step, vars_, base):
        p = Path(step["include"])
        p = p if p.is_absolute() else base / p
        local = dict(vars_)
        local.update(step.get("vars", {}))
        self.run_file(p, local)

    def do_foreach(self, step, vars_, base):
        items = resolve_items(step["foreach"], base)
        var = step.get("as", "item")
        # "only": list of ids in the step, or --var only=a,b,c to run a part of a long scenario.
        only = step.get("only") or vars_.get("only")
        if isinstance(only, str):
            only = [s.strip() for s in only.split(",") if s.strip()]
        for item in items:
            if only and isinstance(item, dict) and item.get("id") not in only:
                continue
            local = dict(vars_)
            local[var] = item
            self.run_steps(step["do"], local, base)
            if self.aborted:
                return

    def do_mark(self, step, vars_, base):
        self.marks[str(step["mark"])] = self.snapshot_offsets()

    def do_note(self, step, vars_, base):
        self.notes.append(f"[{self.section}] {step['note']}")
        log(f"note: {step['note']}")

    def do_wait(self, step, vars_, base):
        time.sleep(float(step["wait"]))

    def _action(self):
        self.action_mark = self.snapshot_offsets()

    def do_server(self, step, vars_, base):
        self._action()
        cmds = step["server"] if isinstance(step["server"], list) else [step["server"]]
        for cmd in cmds:
            try:
                resp = self.rig.rcon.cmd(cmd)
            except (OSError, ConnectionError) as e:
                raise RigError(f"rcon '{cmd}': {e}")
            log(f"rcon> {cmd} -> {resp.strip()[:160]}")
            if "expect" in step:
                ok = re.search(step["expect"], resp, re.I | re.S) is not None
                self.check(step.get("name", f"/{cmd}"), ok, f"response: {resp.strip()[:300]}",
                           soft=bool(step.get("soft")))
            if "reject" in step and re.search(step["reject"], resp, re.I | re.S):
                self.check(step.get("name", f"/{cmd}"), False, f"response: {resp.strip()[:300]}",
                           soft=bool(step.get("soft")))
        if step.get("pause"):
            time.sleep(float(step["pause"]))

    def do_qa(self, step, vars_, base):
        self._action()
        lines = step["qa"] if isinstance(step["qa"], list) else [step["qa"]]
        for c in self.targets(step):
            for line in lines:
                if not c.qa(line):
                    raise RigError(f"{c.name}: QA command not acknowledged: {line}")
        if step.get("pause"):
            time.sleep(float(step["pause"]))

    def do_chat(self, step, vars_, base):
        cmd = step["chat"].lstrip("/")
        self.do_qa({**step, "qa": f"cmd {cmd}"}, vars_, base)

    def do_key(self, step, vars_, base):
        self._action()
        for c in self.targets(step):
            c.key(step["key"], int(step.get("hold_ms", 150)))

    def do_mouse(self, step, vars_, base):
        self._action()
        m = step["mouse"]
        for c in self.targets(step):
            if "move" in m:
                c.xdo("mousemove", str(m["move"][0]), str(m["move"][1]))
            if "button" in m:
                b = str(m["button"])
                c.xdo("mousedown", b)
                time.sleep(max(120, int(m.get("hold_ms", 150))) / 1000)
                c.xdo("mouseup", b)

    def do_shot(self, step, vars_, base):
        name = re.sub(r"[^\w.-]+", "_", str(step["shot"]))
        for c in self.targets(step, default="*"):
            out = self.out / "shots" / f"{len(self.shots) + 1:03d}-{name}-{c.name}.png"
            ok = c.shot(out)
            self.shots.append({"file": str(out.relative_to(self.out)), "client": c.name, "name": step["shot"],
                               "section": self.section, "ok": ok, "t": round(time.time() - self.t0, 1)})
            if not ok:
                self.check(f"screenshot {name} ({c.name})", False, soft=True)

    def do_expect(self, step, vars_, base):
        e = step["expect"]
        where = e.get("log", "server")
        logs = self.logs()
        names = [n for n in logs if n != "server"] if where in ("*", "clients") else [where]
        since = e.get("since", "action")
        timeout = float(e.get("timeout_s", 15))
        for n in names:
            if n not in logs:
                raise RigError(f"expect: unknown log {n}")
            if since == "action":
                off = self.action_mark.get(n, 0)
            elif since == "start":
                off = 0
            elif since.startswith("mark:"):
                off = self.marks.get(since[5:], {}).get(n, 0)
            else:
                off = logs[n].size()
            title = e.get("name") or f"{n}: /{e['regex']}/"
            if e.get("absent"):
                time.sleep(timeout if e.get("wait", True) else 0)
                m = logs[n].find(e["regex"], off)
                self.check(title, m is None, m.group(0) if m else "", soft=bool(e.get("soft")))
            else:
                m = logs[n].wait(e["regex"], timeout, off)
                self.check(title, m is not None, "" if m else f"not seen within {timeout:.0f}s",
                           soft=bool(e.get("soft")))

    def do_metrics(self, step, vars_, base):
        label = re.sub(r"\W+", "_", str(step["metrics"]))
        window = float(step.get("window_s", 10))
        clients = [c for c in self.rig.clients("*") if c.name not in self.dead_clients]
        for c in clients:
            c.qa("perfreset")
        time.sleep(window)
        rec = {"label": label, "section": self.section, "t": round(time.time() - self.t0, 1),
               "window_s": window, "server": server_tps(self.rig), "clients": {},
               "mem": meminfo_mb(), "rss": {"server": rss_mb(self.rig.state["server"]["pid"])}}
        for c in clients:
            rec["clients"][c.name] = client_perf(c, label)
            rec["rss"][c.name] = rss_mb(c.pid)
        self.metrics.append(rec)
        srv = rec["server"]
        log(f"metrics {label}: server {srv.get('tps')} TPS {srv.get('mspt')} mspt; "
            f"rss {rec['rss']}; avail {rec['mem'].get('MemAvailable')} MB")
        if "min_tps" in step and srv.get("tps") is not None:
            self.check(f"TPS >= {step['min_tps']} ({label})", srv["tps"] >= float(step["min_tps"]),
                       f"tps {srv['tps']}", soft=True)

    def do_rejoin(self, step, vars_, base):
        """Kill one client and start it again (join/leave mid-ability, reconnect state)."""
        name = step["rejoin"]
        c = self.rig.state["clients"].get(name)
        if not c:
            raise RigError(f"rejoin: unknown client {name}")
        kill_group(c["pid"], grace=5)
        kill_group(c["xvfb_pid"], grace=2)
        del self.rig.state["clients"][name]
        self.dead_clients.discard(name)
        time.sleep(float(step.get("pause", 3)))
        self.rig.start_client(name)


# ---------------------------------------------------------------- error scan + report

ERR_RX = re.compile(r"/(ERROR|FATAL)\]|^Exception in thread|^\s*Caused by: |^java\.\S+(Exception|Error)\b", re.M)


def scan_errors(path: Path, since: int = 0, limit: int = 40) -> dict:
    lf = LogFile(path)
    text = lf.read_from(since)
    sigs: dict[str, dict] = {}
    total = 0
    for line in text.splitlines():
        if not ERR_RX.search(line):
            continue
        total += 1
        body = re.sub(r"^\[[^\]]*\]\s*(\[[^\]]*\]\s*)*:?\s*", "", line)
        sig = re.sub(r"\d+(\.\d+)?", "#", body)[:200]
        rec = sigs.setdefault(sig, {"count": 0, "example": line.strip()[:400]})
        rec["count"] += 1
    top = sorted(sigs.items(), key=lambda kv: -kv[1]["count"])[:limit]
    return {"lines": total, "unique": len(sigs), "top": [{"sig": k, **v} for k, v in top]}


def crash_reports(dirpath: Path, since_ts: float) -> list[str]:
    out = []
    for p in sorted((dirpath / "crash-reports").glob("*.txt")) if (dirpath / "crash-reports").is_dir() else []:
        if p.stat().st_mtime >= since_ts:
            out.append(str(p))
    return out


def fmt(v, digits=1):
    if v is None:
        return "—"
    if isinstance(v, float):
        return f"{v:.{digits}f}"
    return str(v)


def write_report(runner: Runner, scenario: dict, started: float, offsets: dict[str, int]) -> Path:
    out = runner.out
    rig = runner.rig
    dur = time.time() - started
    hard_fail = [c for c in runner.checks if not c["ok"] and not c["soft"]]
    soft_fail = [c for c in runner.checks if not c["ok"] and c["soft"]]
    passed = [c for c in runner.checks if c["ok"]]
    errors = {"server": scan_errors(rig.server_log.path, offsets.get("server", 0))}
    crashes = crash_reports(RIG_HOME / "server", started)
    for c in rig.clients("*"):
        errors[c.name] = scan_errors(c.log.path, offsets.get(c.name, 0))
        crashes += crash_reports(c.dir, started)
    status = "ABORTED" if runner.aborted else ("FAIL" if hard_fail else ("WARN" if soft_fail else "PASS"))
    data = {"scenario": scenario.get("name", runner.path.stem), "file": str(runner.path), "status": status,
            "aborted": runner.aborted, "started": dt.datetime.fromtimestamp(started).isoformat(timespec="seconds"),
            "duration_s": round(dur, 1), "server": rig.state.get("server"), "clients": rig.state.get("clients"),
            "checks": runner.checks, "metrics": runner.metrics, "shots": runner.shots, "notes": runner.notes,
            "sections": runner.sections, "errors": errors, "crash_reports": crashes,
            "commit": git_head()}
    (out / "report.json").write_text(json.dumps(data, indent=1, ensure_ascii=False))

    L = []
    L.append(f"# Плейтест: {data['scenario']} — {status}\n")
    L.append(f"- Запуск: {data['started']}, длительность {dur / 60:.1f} мин, коммит `{data['commit']}`")
    srv = rig.state.get("server") or {}
    L.append(f"- Сервер: выделенный, heap {srv.get('heap_mb')} МБ, мир `{srv.get('world')}`, "
             f"старт {fmt(srv.get('ready_s'))} с")
    for name, c in (rig.state.get("clients") or {}).items():
        L.append(f"- Клиент **{name}**: heap {c.get('heap_mb')} МБ, вход на сервер {fmt(c.get('join_s'))} с")
    L.append(f"- Проверки: {len(passed)} ✅, {len(hard_fail)} ❌, {len(soft_fail)} ⚠️")
    if runner.aborted:
        L.append(f"- **Прервано:** {runner.aborted}")
    L.append("")
    if hard_fail or soft_fail:
        L.append("## Что не прошло\n")
        L.append("| # | Раздел | Проверка | Подробности |\n|---|---|---|---|")
        for c in hard_fail + soft_fail:
            icon = "❌" if not c["soft"] else "⚠️"
            L.append(f"| {icon} {c['n']} | {c['section']} | {md(c['name'])} | {md(c['detail'][:200])} |")
        L.append("")
    if runner.metrics:
        L.append("## Метрики\n")
        names = list((rig.state.get("clients") or {}).keys())
        head = "| Точка | TPS | MSPT | " + " | ".join(f"{n} FPS / 1% low / p99 мс" for n in names) + \
               " | RSS сервер / клиенты, МБ | Свободно, МБ |"
        L.append(head)
        L.append("|" + "---|" * (head.count("|") - 1))
        for m in runner.metrics:
            s = m["server"]
            cells = []
            for n in names:
                p = m["clients"].get(n, {})
                cells.append(f"{fmt(first(p, 'avg', 'fps', 'avgFps'))} / {fmt(first(p, '1%', 'low1', 'p1'))} / "
                             f"{fmt(first(p, 'p99', 'p99ms'))}")
            rss = " / ".join(fmt(m["rss"].get(k), 0) for k in ["server"] + names)
            L.append(f"| {m['label']} | {fmt(s.get('tps'))} | {fmt(s.get('mspt'), 2)} | " + " | ".join(cells) +
                     f" | {rss} | {fmt(m['mem'].get('MemAvailable'), 0)} |")
        L.append("")
    L.append("## Ошибки в логах\n")
    for name, e in errors.items():
        L.append(f"- **{name}**: {e['lines']} строк ERROR/исключений, уникальных {e['unique']}")
        for t in e["top"][:8]:
            L.append(f"  - ×{t['count']} `{t['example'][:180].replace('`', '')}`")
    if crashes:
        L.append("- **Crash reports:** " + ", ".join(Path(c).name for c in crashes))
    L.append("")
    if runner.shots:
        L.append("## Скриншоты\n")
        for s in runner.shots:
            if s["ok"]:
                L.append(f"- {s['section']} · {s['name']} · {s['client']}: [{Path(s['file']).name}]({s['file']})")
        L.append("")
    if runner.notes:
        L.append("## Заметки\n")
        L += [f"- {n}" for n in runner.notes]
        L.append("")
    L.append("## Все проверки\n")
    L.append("| # | Раздел | Проверка | Итог | t, с |\n|---|---|---|---|---|")
    for c in runner.checks:
        icon = "✅" if c["ok"] else ("⚠️" if c["soft"] else "❌")
        L.append(f"| {c['n']} | {c['section']} | {md(c['name'])} | {icon} | {c['t']} |")
    (out / "report.md").write_text("\n".join(L) + "\n", encoding="utf-8")
    return out / "report.md"


def first(d: dict, *keys):
    for k in keys:
        if k in d:
            return d[k]
    return None


def md(s: str) -> str:
    return str(s).replace("|", "\\|").replace("\n", " ")


def git_head() -> str:
    for git in ("git", "/opt/git/bin/git"):
        try:
            r = subprocess.run([git, "-C", str(PROJECT), "rev-parse", "--short", "HEAD"], capture_output=True,
                               text=True, timeout=5)
            if r.returncode == 0:
                return r.stdout.strip()
        except (OSError, subprocess.TimeoutExpired):
            continue
    return os.environ.get("RIG_COMMIT", "unknown")


# ---------------------------------------------------------------- commands

def do_build(opts) -> None:
    cmd = [str(KIT / "run.sh"), "build"] + (["-x", "test"] if opts.skip_tests else [])
    log(f"build: {' '.join(cmd)}")
    t0 = time.time()
    with open(RIG_HOME / "build.log", "wb") as fh:
        r = subprocess.run(cmd, cwd=str(KIT), stdout=fh, stderr=subprocess.STDOUT)
    if r.returncode != 0:
        raise RigError(f"build failed ({r.returncode}), see {RIG_HOME / 'build.log'}: {tail(RIG_HOME / 'build.log')}")
    log(f"build ok in {time.time() - t0:.0f} s")


def cmd_up(opts) -> Rig:
    RIG_HOME.mkdir(parents=True, exist_ok=True)
    rig = Rig(opts)
    rig.down()
    if not opts.no_build:
        do_build(opts)
    rig = Rig(opts, {"server": None, "clients": {}})
    rig.state["guard_pid"] = start_guard(opts.guard_mb)
    rig.start_server()
    names = (opts.names.split(",") if opts.names else DEFAULT_NAMES)[:opts.clients]
    for name in names:
        try:
            rig.start_client(name)
        except RigError as e:
            if name == names[0] or opts.strict:
                raise
            log(f"WARNING: client {name} not started: {e}")
            rig.state.setdefault("skipped", {})[name] = str(e)
            save_state(rig.state)
            c = rig.state["clients"].pop(name, None)
            if c:
                kill_group(c.get("pid"), 5)
                kill_group(c.get("xvfb_pid"), 2)
                save_state(rig.state)
    log("rig is up: " + json.dumps({"server": rig.state["server"]["pid"],
                                    "clients": {n: c["display"] for n, c in rig.state["clients"].items()}}))
    return rig


def cmd_run(opts) -> int:
    scen_path = Path(opts.scenario).resolve()
    scenario = load_json(scen_path)
    if isinstance(scenario, list):
        scenario = {"steps": scenario}
    rig_cfg = scenario.get("rig", {})
    for k, v in rig_cfg.items():
        attr = k.replace("-", "_")
        if hasattr(opts, attr) and getattr(opts, attr) == getattr(DEFAULTS, attr, None):
            setattr(opts, attr, v)
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    out = Path(opts.out or (RIG_HOME / "reports" / f"{stamp}-{scen_path.stem}"))
    out.mkdir(parents=True, exist_ok=True)
    started = time.time()
    if opts.reuse and load_state().get("server") and Rig(opts).server_alive():
        rig = Rig(opts)
        log("reusing running rig")
    else:
        rig = cmd_up(opts)
    offsets = {"server": 0}
    for c in rig.clients("*"):
        offsets[c.name] = 0
    vars_ = dict(scenario.get("vars", {}))
    for kv in opts.var or []:
        k, _, v = kv.partition("=")
        vars_[k] = v
    vars_["clients"] = list(rig.state["clients"])
    for i, n in enumerate(vars_["clients"]):
        vars_[f"c{i + 1}"] = n
    runner = Runner(rig, scen_path, out, vars_)
    for name, why in (rig.state.get("skipped") or {}).items():
        runner.notes.append(f"НЕ ПРОВЕРЕНО В ИГРЕ: клиент {name} не запущен — {why}")
    stop = {"flag": False}

    def on_signal(signum, frame):
        stop["flag"] = True
        runner.aborted = f"signal {signum}"
    signal.signal(signal.SIGTERM, on_signal)
    signal.signal(signal.SIGINT, on_signal)
    try:
        runner.run_steps(scenario["steps"], vars_, scen_path.parent)
    except Exception as e:  # report what we have, then re-raise
        runner.aborted = f"{type(e).__name__}: {e}"
        log(f"scenario crashed: {runner.aborted}")
    finally:
        report = write_report(runner, scenario, started, offsets)
        copy_logs(rig, out)
        if not opts.keep:
            rig.down()
    log(f"report: {report}")
    hard = [c for c in runner.checks if not c["ok"] and not c["soft"]]
    return 2 if runner.aborted else (1 if hard else 0)


def copy_logs(rig: Rig, out: Path) -> None:
    d = out / "logs"
    d.mkdir(exist_ok=True)
    try:
        shutil.copy(rig.server_log.path, d / "server.log")
    except OSError:
        pass
    for c in rig.clients("*"):
        try:
            shutil.copy(c.log.path, d / f"client-{c.name}.log")
        except OSError:
            pass


def cmd_status(opts) -> int:
    st = load_state()
    srv = st.get("server") or {}
    print(json.dumps({
        "rig_home": str(RIG_HOME), "kit": str(KIT),
        "server": {"pid": srv.get("pid"), "alive": alive(srv.get("pid")), "rss_mb": rss_mb(srv.get("pid"))},
        "clients": {n: {"pid": c["pid"], "alive": alive(c["pid"]), "display": c["display"], "rss_mb": rss_mb(c["pid"])}
                    for n, c in (st.get("clients") or {}).items()},
        "mem": meminfo_mb()}, indent=1))
    return 0


DEFAULTS = argparse.Namespace()


def add_up_args(p: argparse.ArgumentParser) -> None:
    p.add_argument("--clients", type=int, default=2, help="number of clients (default 2)")
    p.add_argument("--names", default="", help="comma-separated nicknames (default Alpha,Bravo,...)")
    p.add_argument("--no-build", action="store_true", help="skip gradle build")
    p.add_argument("--skip-tests", action="store_true", help="build with -x test")
    p.add_argument("--world", default="fresh", help="fresh (flat), keep, or a world dir/zip to copy")
    p.add_argument("--server-heap", type=int, default=1024)
    p.add_argument("--client-heap", type=int, default=1200)
    p.add_argument("--min-free-mb", type=int, default=1500, help="memory gate for every extra client")
    p.add_argument("--port", type=int, default=25565)
    p.add_argument("--rcon-port", type=int, default=25575)
    p.add_argument("--server-timeout", type=float, default=420)
    p.add_argument("--client-timeout", type=float, default=600)
    p.add_argument("--render-distance", type=int, default=5)
    p.add_argument("--lang", default="en_us")
    p.add_argument("--server-prop", action="append", help="extra server.properties key=value")
    p.add_argument("--jvm", action="append", help="extra JVM arg for all game processes")
    p.add_argument("--no-op", action="store_true", help="do not /op the clients")
    p.add_argument("--strict", action="store_true", help="fail if any client cannot start")
    p.add_argument("--guard-mb", type=int, default=350,
                   help="memory guard: kill the newest client when MemAvailable < this (0 = off)")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="rig.py", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("capture", help="snapshot Forge JVM command lines (auto when build files change)")
    p.add_argument("what", nargs="?", default="all", choices=["all", "server", "client"])
    p = sub.add_parser("up", help="build, start server and clients, return when joined")
    add_up_args(p)
    p = sub.add_parser("run", help="up + scenario + report + down")
    p.add_argument("scenario")
    add_up_args(p)
    p.add_argument("--keep", action="store_true", help="leave the rig running after the scenario")
    p.add_argument("--reuse", action="store_true", help="use an already running rig")
    p.add_argument("--out", default="", help="report dir (default ~/rig/reports/<time>-<scenario>)")
    p.add_argument("--var", action="append", help="scenario variable key=value")
    p = sub.add_parser("down", help="stop server and clients")
    p.add_argument("--all", action="store_true", help="also kill stray game JVMs, Gradle and Xvfb")
    sub.add_parser("status", help="process and memory status")
    p = sub.add_parser("guard", help="(internal) memory guard loop, started by up")
    p.add_argument("--min-mb", type=float, default=350)
    p = sub.add_parser("shot", help="screenshot of a client display")
    p.add_argument("client")
    p.add_argument("file")
    p = sub.add_parser("qa", help="send a SubtitlePlaytest command to a client")
    p.add_argument("client")
    p.add_argument("line", nargs="+")
    p = sub.add_parser("key", help="press X keys on a client (xdotool names)")
    p.add_argument("client")
    p.add_argument("keys")
    p.add_argument("--hold-ms", type=int, default=150)
    p = sub.add_parser("rcon", help="server console command")
    p.add_argument("command", nargs="+")
    opts = ap.parse_args(argv)
    for action in sub.choices["run"]._actions:
        if action.dest not in ("help",):
            setattr(DEFAULTS, action.dest, action.default)
    try:
        if opts.cmd == "capture":
            for kind in (["server", "client"] if opts.what == "all" else [opts.what]):
                capture(kind)
            return 0
        if opts.cmd == "up":
            cmd_up(opts)
            return 0
        if opts.cmd == "run":
            return cmd_run(opts)
        if opts.cmd == "down":
            Rig().down(everything=opts.all)
            return 0
        if opts.cmd == "status":
            return cmd_status(opts)
        if opts.cmd == "guard":
            return guard_loop(opts.min_mb)
        rig = Rig()
        if opts.cmd == "shot":
            ok = rig.client(opts.client).shot(Path(opts.file).resolve())
            print(opts.file if ok else "screenshot failed")
            return 0 if ok else 1
        if opts.cmd == "qa":
            ok = rig.client(opts.client).qa(" ".join(opts.line))
            print("ok" if ok else "not acknowledged")
            return 0 if ok else 1
        if opts.cmd == "key":
            rig.client(opts.client).key(opts.keys, opts.hold_ms)
            return 0
        if opts.cmd == "rcon":
            print(rig.rcon.cmd(" ".join(opts.command)))
            return 0
    except RigError as e:
        log(f"ERROR: {e}")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
