#!/usr/bin/env python3
"""Static resource checks for hero work in viltrumite-mods.

Read-only. Run from the repository root:

    python3 .agents/skills/add_hero/scripts/check_hero_resources.py
    python3 .agents/skills/add_hero/scripts/check_hero_resources.py --hero regulus

Checks (per mod: viltrumitecore, viltrumiteflight):
  1. lang     - every locale has exactly the key set of en_us.json.
  2. keys     - every literal "<ns>.viltrumitecore.<...>" translation key in Java
                exists in en_us.json (string literals only; built keys are skipped).
  3. sounds   - every sounds.json file reference resolves to an .ogg under
                src/main/resources or an .ogg.b64 under src/main/binassets.
  4. textures - every literal "textures/....png" in Java resolves the same way.
  5. mixins   - every class in the mixin configs exists, and every class in a
                mixin package is listed in a config.
--hero <id> limits checks 2 and 4 to Java files whose path or text mentions
the id (case-insensitive). Problems listed in ../assets/known_problems.txt
(legacy issues from the original JARs) are counted but do not fail the run;
remove a line there when you fix it. Exit code 1 when a new problem is found.
"""
import argparse
import json
import os
import re
import sys

MODS = ["viltrumitecore", "viltrumiteflight"]


def load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def asset_exists(mod_dir, ns, rel):
    res = os.path.join(mod_dir, "src/main/resources/assets", ns, rel)
    b64 = os.path.join(mod_dir, "src/main/binassets/assets", ns, rel + ".b64")
    return os.path.isfile(res) or os.path.isfile(b64)


def java_files(mod_dir, hero):
    root = os.path.join(mod_dir, "src/main/java")
    for base, _, names in os.walk(root):
        for name in names:
            if not name.endswith(".java"):
                continue
            path = os.path.join(base, name)
            text = open(path, encoding="utf-8").read()
            if hero and hero not in path.lower() and hero not in text.lower():
                continue
            yield path, text


def check_mod(mod, hero, problems):
    mod_dir = mod
    lang_dir = os.path.join(mod_dir, "src/main/resources/assets", mod, "lang")
    en = {}
    # Lang overlays (src/main/lang-overlays/<part>/<locale>.json) are merged into
    # the locale file by processResources: locales too big for one push grow there.
    overlay_dir = os.path.join(mod_dir, "src/main/lang-overlays")

    def load_locale(name):
        data = dict(load_json(os.path.join(lang_dir, name)))
        if os.path.isdir(overlay_dir):
            for part in sorted(os.listdir(overlay_dir)):
                extra = os.path.join(overlay_dir, part, name)
                if os.path.isfile(extra):
                    data.update(load_json(extra))
        return data

    if os.path.isdir(lang_dir):
        en = load_locale("en_us.json")
        for name in sorted(os.listdir(lang_dir)):
            if not name.endswith(".json") or name == "en_us.json":
                continue
            other = load_locale(name)
            missing = sorted(set(en) - set(other))
            extra = sorted(set(other) - set(en))
            for key in missing:
                problems.append(f"[lang] {mod}/{name}: missing {key}")
            for key in extra:
                problems.append(f"[lang] {mod}/{name}: not in en_us {key}")

    key_re = re.compile(r'"((?:ability|message|key|gui|item|entity|subtitles|effect|death|category|hero)\.' + re.escape(mod) + r'\.[a-z0-9_.]+)"')
    tex_re = re.compile(r'new ResourceLocation\(\s*"([a-z0-9_]+)"\s*,\s*"(textures/[^"]+\.png)"\s*\)')
    for path, text in java_files(mod_dir, hero):
        rel = os.path.relpath(path)
        for key in key_re.findall(text):
            if key.endswith(".") or key.startswith("category."):
                continue
            if en and key not in en:
                problems.append(f"[keys] {rel}: {key} not in en_us.json")
        for ns, tex in tex_re.findall(text):
            if ns in MODS and not asset_exists(ns, ns, tex):
                problems.append(f"[textures] {rel}: {ns}:{tex} not found")

    sounds = os.path.join(mod_dir, "src/main/resources/assets", mod, "sounds.json")
    if os.path.isfile(sounds):
        for event, spec in load_json(sounds).items():
            for entry in spec.get("sounds", []):
                name = entry if isinstance(entry, str) else entry.get("name", "")
                if isinstance(entry, dict) and entry.get("type") == "event":
                    continue
                ns, _, path = name.rpartition(":") if ":" in name else (mod, "", name)
                if ns == "minecraft":
                    continue
                if not asset_exists(ns, ns, f"sounds/{path}.ogg"):
                    problems.append(f"[sounds] {mod}/sounds.json {event}: {name}.ogg not found")

    resources = os.path.join(mod_dir, "src/main/resources")
    for config in sorted(os.listdir(resources)):
        if not (config.endswith(".mixins.json") and config.startswith(mod)):
            continue
        data = load_json(os.path.join(resources, config))
        package = data.get("package", "")
        listed = set(data.get("mixins", []) + data.get("client", []) + data.get("server", []))
        pkg_dir = os.path.join(mod_dir, "src/main/java", package.replace(".", "/"))
        for name in sorted(listed):
            if not os.path.isfile(os.path.join(pkg_dir, name.replace(".", "/") + ".java")):
                problems.append(f"[mixins] {config}: {name} has no class")
        if os.path.isdir(pkg_dir):
            for base, _, names in os.walk(pkg_dir):
                for file_name in names:
                    if not file_name.endswith(".java"):
                        continue
                    cls = os.path.relpath(os.path.join(base, file_name), pkg_dir)[:-5].replace(os.sep, ".")
                    text = open(os.path.join(base, file_name), encoding="utf-8").read()
                    if "@Mixin" in text and cls not in listed and not any(cls in load_json(os.path.join(resources, c)).get(k, []) for c in os.listdir(resources) if c.endswith(".mixins.json") for k in ("mixins", "client", "server")):
                        problems.append(f"[mixins] {package}.{cls} is not in any mixin config")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--hero", help="limit Java checks to files mentioning this hero id")
    args = parser.parse_args()
    if not os.path.isfile("settings.gradle"):
        print("Run from the repository root.", file=sys.stderr)
        return 2

    problems = []
    for mod in MODS:
        if os.path.isdir(mod):
            check_mod(mod, args.hero.lower() if args.hero else None, problems)

    baseline_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "known_problems.txt")
    known = set()
    if os.path.isfile(baseline_path):
        with open(baseline_path, encoding="utf-8") as handle:
            known = {line.rstrip("\n") for line in handle if line.strip() and not line.startswith("#")}

    new = [p for p in problems if p not in known]
    for problem in new:
        print(problem)
    # A --hero run skips unrelated files, so missing baseline lines prove nothing.
    fixed = [] if args.hero else sorted(known - set(problems))
    for line in fixed:
        print(f"[baseline] fixed, remove from known_problems.txt: {line}")
    print(f"{len(new)} new problem(s), {len(problems) - len(new)} known legacy problem(s)")
    return 1 if new else 0


if __name__ == "__main__":
    sys.exit(main())
