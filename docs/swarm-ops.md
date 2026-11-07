# swarm ops — guide for delegated agents

Applies to every agent in the "swarm" operation on `bbvbbvvgg/viltrumite-mods`.
Global rules stay in `AGENTS.md` (read it fully). This file only adds the swarm
protocol. If they conflict, `AGENTS.md` wins for code rules, this file wins for
process.

Machine: **cool-shoal** (8 vCPU / 32 GB). You are probably running there.
Repository: `/home/boxd/work/viltrumite-mods` (full clone, origin = GitHub).
Local Gradle cache is warm and `./gradlew build -x test` passes on `main`.

## Hard rules

1. **Never touch `main`.** No commit, no push, no merge, no force-push.
   `git checkout -b <your-branch> origin/main` and work there.
2. **One task = one branch = one PR.** Branch name: `swarm/<task-slug>`.
   Never push into another agent's branch. Never delete or rewrite a remote
   branch you do not own.
3. **A PR's base is always `main`.** Independent work only. If your task needs
   another open swarm PR, do not stack: write in your PR body
   `Blocked by: #NN` and pick a different, independent slice instead.
4. **No model/config changes.** Never touch `~/.hermes`, provider configs or
   anything under `~/.config/hermes`. Not yours.
5. **Do not invent game results.** A green `./gradlew build` is a build, not a
   playtest. Say exactly what you ran.
6. **No junk for line counts.** No dead code, no unused classes, no duplicate
   implementations, no fake tests, no stub presented as a finished feature.
   A real 30-line fix beats 2000 lines of scaffolding.
7. **Deep-read before writing.** Read every file you change, its interfaces,
   its call sites, an existing analogous implementation, and the existing tests.
   Follow `.agents/skills/add_hero/SKILL.md` (hero work) and
   `.agents/skills/animation-system/SKILL.md` (any animation/pose/render/VFX
   work) — both are mandatory for their areas.
8. **Verify before PR:** `./gradlew build` green (all unit tests), plus
   `python3 .agents/skills/add_hero/scripts/check_hero_resources.py --hero <id>`
   for hero work, plus `git diff --check`.
9. **One build at a time in your workspace.** Use `--no-daemon` and
   `GRADLE_USER_HOME=/home/boxd/.gradle`. Do not run more than one Gradle build
   per machine concurrently; if you need a second, wait or use `-x test`.

## Git and GitHub

- Git author is already configured. Push with `git push -u origin HEAD`.
- GitHub CLI is authenticated (`gh`). Open the PR with `gh pr create --base main`
  or `gh pr create` after pushing; use a Russian title, body starts with
  «Для игрока» (what the player gets), then what changed technically, then
  verification (commands actually run and their result), then what is not
  verified (in-game checks). Project convention, see `AGENTS.md` §14.
- Before you start: `git fetch origin && git log --oneline -3 origin/main`.
  Before you push for the last time, `git fetch origin` and rebase onto
  `origin/main` if it moved; resolve conflicts yourself in your own files.
- Binary assets (`.png`, `.ogg`) are **not** committed raw. Follow the existing
  convention: `viltrumitecore/src/main/binassets/<same path>.b64` (base64), which
  Gradle `decodeBinaryAssets` writes into resources at build. See
  `.agents/skills/add_hero/SKILL.md` §7.

## Reports

Work in `/home/boxd/work/reports/<task>.md` on the swarm machine:
status (`in_progress` / `pr_open` / `blocked`), branch, PR number, what you
actually ran, what remains. Keep it under 40 lines. Append, never rewrite
another agent's report.

## Cost and time

The night window is limited. Prefer one cohesive, finished, verified change over
three half-done ones. If a task turns out much larger than its brief, ship the
solid core in this PR and list the rest as "next slice" in the PR body.
