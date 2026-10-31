#!/usr/bin/env python3
"""Small, dependency-free Openverse CC0 finder and Minecraft sound packager.

Core ships packed `.ogg.b64` text that its Gradle `decodeBinaryAssets` task
decodes; a module without that task (today: flight) gets a raw `.ogg` in its
resources, the only place its JAR picks sounds up.

Run from the viltrumite-mods repository root. Python 3.10+; ffmpeg/ffprobe
are needed only for the package subcommand. No API key is required.
"""
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
import uuid

API = "https://api.openverse.org/v1/audio/"
CACHE = Path(os.environ.get("OPENVERSE_SFX_HOME", str(Path.home() / ".cache" / "viltrumite-sfx"))).expanduser()
CACHE_AGE = 7 * 24 * 60 * 60
MAX_BYTES = 30 * 1024 * 1024
EXTENSIONS = {"mp3", "ogg", "wav", "flac", "aiff", "aif", "m4a", "opus"}
MODS = {"core": "viltrumitecore", "flight": "viltrumiteflight"}


class SfxError(Exception):
    pass


def safe_url(url: str) -> str:
    """Reject local, non-HTTPS and unusual URLs before following remote links."""
    try:
        parsed = urlsplit(url)
        host = (parsed.hostname or "").lower().rstrip(".")
        if parsed.scheme != "https" or not host or parsed.username or parsed.password:
            raise SfxError("Refusing non-HTTPS or malformed media URL")
        if parsed.port not in (None, 443):
            raise SfxError("Refusing nonstandard media URL port")
        if host == "localhost" or host.endswith((".localhost", ".local", ".internal")):
            raise SfxError("Refusing local media URL")
        try:
            ipaddress.ip_address(host.strip("[]"))
        except ValueError:
            pass
        else:
            raise SfxError("Refusing direct IP media URL")
    except ValueError as exc:
        raise SfxError(f"Invalid media URL: {exc}") from exc
    return url


class SafeRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        safe_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


OPENER = build_opener(SafeRedirects())


# Openverse sits behind a proxy that cuts slow backend queries at ~30 s with an HTML 500 page: that is a
# degraded service, not a bad key (a bad key is an instant JSON 400/401). So: long timeouts, retry on 5xx/HTML,
# and one cached token (12 h) instead of a token request per call.
TIMEOUT = 90
TRIES = int(os.environ.get("OPENVERSE_TRIES", "10"))
TOKEN_CACHE = CACHE / "token.json"


def _retrying(open_request, what: str):
    last = None
    for i in range(TRIES):
        try:
            with open_request() as response:
                return response.read(4 * 1024 * 1024 + 1), response.headers
        except HTTPError as exc:
            body = exc.read(512).decode("utf-8", "replace").lstrip()
            if exc.code >= 500 or body.startswith("<"):
                last = f"HTTP {exc.code} (proxy timeout)"
                time.sleep(min(2 ** i, 20))
                continue
            raise
        except (URLError, TimeoutError) as exc:
            last = str(exc)
            time.sleep(3)
            continue
    raise SfxError(f"Openverse {what}: {TRIES} attempts failed (service degraded: {last})")


def access_token() -> str:
    """Accept a token, reuse the cached one, or request a 12 h token from free API credentials."""
    direct = os.environ.get("OPENVERSE_API_TOKEN", "").strip()
    if direct:
        return direct
    try:
        cached = json.loads(TOKEN_CACHE.read_text("utf-8"))
        if cached.get("until", 0) > time.time() + 60 and cached.get("access_token"):
            return cached["access_token"]
    except (OSError, ValueError):
        pass
    client_id = os.environ.get("OPENVERSE_CLIENT_ID", "").strip()
    client_secret = os.environ.get("OPENVERSE_CLIENT_SECRET", "").strip()
    if not (client_id or client_secret):
        return ""
    if not (client_id and client_secret):
        raise SfxError("Set both OPENVERSE_CLIENT_ID and OPENVERSE_CLIENT_SECRET")
    data = urlencode({"grant_type": "client_credentials", "client_id": client_id,
                      "client_secret": client_secret}).encode("utf-8")

    def req():
        return OPENER.open(Request("https://api.openverse.org/v1/auth_tokens/token/", data=data,
                                   headers={"Content-Type": "application/x-www-form-urlencoded",
                                            "User-Agent": "viltrumite-sfx-skill/1.0"}), timeout=TIMEOUT)
    try:
        body, _ = _retrying(req, "authentication")
        d = json.loads(body[:8192])
    except HTTPError as exc:
        raise SfxError(f"Openverse authentication failed: HTTP {exc.code} (check the client id/secret)") from exc
    except json.JSONDecodeError as exc:
        raise SfxError(f"Openverse authentication failed: {exc}") from exc
    token = d.get("access_token", "")
    if not token:
        raise SfxError("Openverse token response had no access_token")
    CACHE.mkdir(parents=True, exist_ok=True)
    TOKEN_CACHE.write_text(json.dumps({"access_token": token, "until": time.time() + float(d.get("expires_in", 3600))}), "utf-8")
    os.chmod(TOKEN_CACHE, 0o600)
    return token


def request_json(url: str) -> tuple[dict, dict]:
    req_headers = {"Accept": "application/json", "User-Agent": "viltrumite-sfx-skill/1.0"}
    token = access_token()
    if token:
        req_headers["Authorization"] = f"Bearer {token}"
    try:
        data, headers = _retrying(lambda: OPENER.open(Request(url, headers=req_headers), timeout=TIMEOUT), "request")
        if len(data) > 4 * 1024 * 1024:
            raise SfxError("API response too large")
        limits = {key: value for key, value in headers.items() if key.lower().startswith("x-ratelimit-")}
        return json.loads(data), limits
    except HTTPError as exc:
        if exc.code == 429:
            raise SfxError(f"Openverse rate limit reached (429). Retry-After: {exc.headers.get('Retry-After', 'see response headers')}. Stop; do not bypass limits.") from exc
        if exc.code == 401:
            raise SfxError("Openverse rejected the token (401). Check OPENVERSE_API_TOKEN.") from exc
        raise SfxError(f"Openverse HTTP {exc.code}: {exc.reason}") from exc
    except json.JSONDecodeError as exc:
        raise SfxError(f"Openverse request failed: {exc}") from exc


def cache_path(query: str, source: str, limit: int) -> Path:
    key = json.dumps({"q": query, "source": source, "license": "cc0", "limit": limit}, sort_keys=True)
    return CACHE / "search" / (hashlib.sha256(key.encode()).hexdigest() + ".json")


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def search(query: str, source: str = "freesound", limit: int = 10, refresh: bool = False) -> dict:
    if not query.strip() or len(query) > 200:
        raise SfxError("Search query must be 1–200 characters")
    if not 1 <= limit <= 20:
        raise SfxError("--limit must be 1–20")
    if not re.fullmatch(r"[a-z0-9_]+", source):
        raise SfxError("Invalid source")
    path = cache_path(query, source, limit)
    if not refresh and path.is_file() and time.time() - path.stat().st_mtime < CACHE_AGE:
        result = read_json(path)
        result["cache_hit"] = True
        return result
    params = {"q": query, "license": "cc0", "source": source, "page_size": limit}
    data, limits = request_json(API + "?" + urlencode(params))
    results = []
    for item in data.get("results", []):
        if item.get("license", "").lower() != "cc0":
            continue
        if item.get("source") != source:
            continue
        if not item.get("id") or not item.get("url"):
            continue
        results.append({key: item.get(key) for key in (
            "id", "title", "creator", "source", "license", "license_url", "url",
            "foreign_landing_url", "attribution", "filetype", "duration", "filesize", "category", "tags")})
    result = {"query": query, "source": source, "license": "cc0", "total": data.get("result_count"),
              "results": results, "rate_limits": limits, "cache_hit": False}
    write_json(path, result)
    return result


def find_cached(identifier: str) -> dict | None:
    folder = CACHE / "search"
    if not folder.exists():
        return None
    for path in folder.glob("*.json"):
        try:
            for item in read_json(path).get("results", []):
                if item.get("id") == identifier:
                    return item
        except (ValueError, OSError):
            continue
    return None


def download(identifier: str) -> dict:
    try:
        identifier = str(uuid.UUID(identifier))
    except ValueError as exc:
        raise SfxError("Expected an Openverse result UUID") from exc
    item = find_cached(identifier)
    if item is None:
        data, _ = request_json(API + identifier + "/")
        item = data
    if item.get("license", "").lower() != "cc0":
        raise SfxError("License gate: only CC0 assets may be downloaded by this helper")
    url = safe_url(item.get("url") or "")
    ext = (item.get("filetype") or "").lower().lstrip(".")
    if ext not in EXTENSIONS:
        ext = Path(urlsplit(url).path).suffix.lower().lstrip(".")
    if ext not in EXTENSIONS:
        raise SfxError("Unsupported or unknown media format; inspect source manually")
    work = CACHE / "work"
    target = work / "raw" / f"{identifier}.{ext}"
    metadata = work / "metadata" / f"{identifier}.json"
    if target.is_file() and metadata.is_file():
        return {"file": str(target), "metadata": str(metadata), "cached": True}
    target.parent.mkdir(parents=True, exist_ok=True)
    metadata.parent.mkdir(parents=True, exist_ok=True)
    req = Request(url, headers={"User-Agent": "viltrumite-sfx-skill/1.0"})
    tmp_path = None
    try:
        with OPENER.open(req, timeout=35) as response:
            if int(response.headers.get("Content-Length") or 0) > MAX_BYTES:
                raise SfxError("Media file exceeds 30 MiB limit")
            with tempfile.NamedTemporaryFile(dir=target.parent, prefix=".sfx-", delete=False) as tmp:
                tmp_path = Path(tmp.name)
                total = 0
                head = b""
                while True:
                    chunk = response.read(64 * 1024)
                    if not chunk:
                        break
                    if not head:
                        head = chunk[:100].lstrip().lower()
                        if head.startswith((b"<!doctype", b"<html", b"<?xml", b"{\"error\"")):
                            raise SfxError("Media URL returned markup or an error instead of audio")
                    total += len(chunk)
                    if total > MAX_BYTES:
                        raise SfxError("Media file exceeds 30 MiB limit")
                    tmp.write(chunk)
        if not total:
            raise SfxError("Empty media download")
        tmp_path.replace(target)
        tmp_path = None
    except (HTTPError, URLError, TimeoutError) as exc:
        raise SfxError(f"Audio download failed: {exc}") from exc
    finally:
        if tmp_path is not None:
            tmp_path.unlink(missing_ok=True)
    write_json(metadata, {"openverse": item, "downloaded_at": datetime.now(timezone.utc).isoformat(),
                          "raw_file": str(target)})
    attribution = item.get("attribution")
    if attribution:
        credits = work / "credits.txt"
        previous = credits.read_text(encoding="utf-8") if credits.is_file() else ""
        if attribution not in previous:
            with credits.open("a", encoding="utf-8") as stream:
                stream.write(attribution)
                stream.write("\n")
    return {"file": str(target), "metadata": str(metadata), "credits": str(work / "credits.txt"), "cached": False}


def validate_name(name: str) -> str:
    if not re.fullmatch(r"[a-z0-9_]+(?:/[a-z0-9_]+)*", name):
        raise SfxError("--name must be a lowercase resource path such as regulus/blade_whoosh")
    return name


def probe_audio(path: Path) -> dict:
    ffprobe = shutil.which("ffprobe")
    if not ffprobe:
        raise SfxError("ffprobe is required for packaging; install FFmpeg")
    cmd = [ffprobe, "-v", "error", "-show_entries", "stream=codec_name,channels:format=duration",
           "-select_streams", "a:0", "-of", "json", str(path)]
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        raise SfxError(f"ffprobe failed: {proc.stderr.strip()}")
    info = json.loads(proc.stdout)
    if not info.get("streams"):
        raise SfxError("No audio stream found")
    return {"codec": info["streams"][0].get("codec_name"),
            "channels": info["streams"][0].get("channels"),
            "duration_seconds": float(info.get("format", {}).get("duration", 0))}


def decodes_binassets(repo: Path, modid: str) -> bool:
    """True when the module's Gradle build decodes src/main/binassets/**/*.b64 into resources."""
    build = repo / modid / "build.gradle"
    text = build.read_text(encoding="utf-8") if build.is_file() else ""
    return "decodeBinaryAssets" in text and "src/main/binassets" in text


def package_audio(input_file: Path, module: str, name: str, event: str, source_ids: list[str],
                  own: bool, stereo: bool, replace: bool, repo: Path) -> dict:
    validate_name(name)
    if not re.fullmatch(r"[a-z0-9_]+", event):
        raise SfxError("--event must be a lowercase Minecraft sound event ID")
    if not input_file.is_file():
        raise SfxError(f"Input audio not found: {input_file}")
    if not (own or source_ids) or (own and source_ids):
        raise SfxError("Provide one or more --source-id values OR --own")
    if source_ids:
        for identifier in source_ids:
            try:
                identifier = str(uuid.UUID(identifier))
            except ValueError as exc:
                raise SfxError("Invalid --source-id UUID") from exc
            metadata = CACHE / "work" / "metadata" / f"{identifier}.json"
            if not metadata.is_file():
                raise SfxError(f"Source metadata missing for {identifier}. Download it first.")
            if read_json(metadata)["openverse"].get("license", "").lower() != "cc0":
                raise SfxError("Source license is not CC0")
    modid = MODS[module]
    packed_path = repo / modid / "src" / "main" / "binassets" / "assets" / modid / "sounds" / f"{name}.ogg.b64"
    plain = repo / modid / "src" / "main" / "resources" / "assets" / modid / "sounds" / f"{name}.ogg"
    packed = decodes_binassets(repo, modid)
    out = packed_path if packed else plain
    # The other form of the same resource path would ship a second copy (or, for
    # an undecoded .b64, a dead file); --replace removes it after a good write.
    superseded = plain if packed else packed_path
    existing = [path for path in (packed_path, plain) if path.exists()]
    if existing and not replace:
        raise SfxError(f"Asset already exists ({existing[0]}). Refusing overwrite without --replace.")
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise SfxError("ffmpeg is required for packaging; install FFmpeg")
    input_info = probe_audio(input_file)
    with tempfile.TemporaryDirectory(prefix="viltrumite-sfx-") as tmpdir:
        tmp_ogg = Path(tmpdir) / "processed.ogg"
        cmd = [ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-i", str(input_file),
               "-map", "0:a:0", "-vn", "-ac", "2" if stereo else "1", "-ar", "44100",
               "-c:a", "libvorbis", "-q:a", "5", "-f", "ogg", str(tmp_ogg)]
        proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
        if proc.returncode != 0:
            raise SfxError(f"ffmpeg conversion failed: {proc.stderr.strip()}")
        info = probe_audio(tmp_ogg)
        if info["codec"] != "vorbis" or info["channels"] != (2 if stereo else 1):
            raise SfxError("Output is not the expected Vorbis/channel format")
        out.parent.mkdir(parents=True, exist_ok=True)
        payload = base64.encodebytes(tmp_ogg.read_bytes()) if packed else tmp_ogg.read_bytes()
        tmp_out = out.with_suffix(out.suffix + ".tmp")
        try:
            tmp_out.write_bytes(payload)
            tmp_out.replace(out)
        finally:
            tmp_out.unlink(missing_ok=True)
    removed = []
    if superseded.exists():
        superseded.unlink()
        removed.append(str(superseded))
    sound_entry = {event: {"category": "player", "subtitle": f"subtitles.{modid}.{event}",
                           "sounds": [f"{modid}:{name}"]}}
    next_steps = ["Merge fragment into the existing sounds.json", "Register/reuse SoundEvent in Java",
                  "Add subtitle key to all locales", "Update CREDITS.md and per-hero sources list",
                  "Build and test playback in game"]
    if not packed:
        next_steps.insert(0, f"{modid} has no decodeBinaryAssets: commit the raw .ogg with git "
                             "(a text-only push cannot carry it)")
    if removed:
        next_steps.insert(0, "Commit the deletion of the superseded file: " + ", ".join(removed))
    return {"output": str(out), "storage": "binassets .ogg.b64" if packed else "resources raw .ogg",
            "removed": removed, "input": input_info, "output_audio": info, "source_ids": source_ids,
            "sounds_json_fragment": sound_entry, "next_steps": next_steps}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("search", help="Search Openverse CC0 sounds; cache results for 7 days")
    p.add_argument("query", help="Concrete English sound description")
    p.add_argument("--source", default="freesound", help="Openverse source name (default: freesound)")
    p.add_argument("--limit", type=int, default=10, help="Results per query, 1–20 (default: 10)")
    p.add_argument("--refresh", action="store_true", help="Ignore the cache; consumes API quota")
    p = sub.add_parser("download", help="Download a CC0 result and preserve exact attribution")
    p.add_argument("id", help="UUID returned by search")
    p = sub.add_parser("package", help="Convert a reviewed audio mix into the module's Minecraft OGG Vorbis asset")
    p.add_argument("--input", type=Path, required=True, help="Local audio file to package")
    p.add_argument("--module", choices=tuple(MODS), required=True)
    p.add_argument("--name", required=True, help="Resource path, e.g. regulus/blade_whoosh")
    p.add_argument("--event", required=True, help="Sound event ID, e.g. regulus_blade_whoosh")
    p.add_argument("--source-id", action="append", default=[], help="Downloaded Openverse UUID; repeat for layers")
    p.add_argument("--own", action="store_true", help="The sound is fully original; no Openverse sources")
    p.add_argument("--stereo", action="store_true", help="Preserve two channels for non-positional audio")
    p.add_argument("--replace", action="store_true",
                   help="Explicitly overwrite an existing asset and remove its superseded raw/packed twin")
    p.add_argument("--repo", type=Path, default=Path.cwd(), help="Repository root (default: cwd)")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "search":
            result = search(args.query, args.source, args.limit, args.refresh)
        elif args.command == "download":
            result = download(args.id)
        else:
            result = package_audio(args.input, args.module, args.name, args.event, args.source_id,
                                   args.own, args.stereo, args.replace, args.repo.resolve())
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (SfxError, OSError, subprocess.SubprocessError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
