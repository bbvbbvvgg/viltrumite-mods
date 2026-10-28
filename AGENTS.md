# AGENTS.md — Viltrumite Mods

Forge multi-project for Minecraft 1.20.1 (Forge 47.3.0, Java 17, Mojang official mappings, Mixin 0.8.5). This repository builds two mods:

- `viltrumitecore/`: mod id `viltrumitecore`, display name ViltrumiteForge, package `dev.baranhan.viltrumitecore`. It holds abilities, combat, grab, race, world events, ability animations and VFX. It depends on `viltrumiteflight`.
- `viltrumiteflight/`: mod id `viltrumiteflight`, package `dev.baranhan.viltrumiteflight`. It holds flight physics, flight animations and flight VFX.

The exact versions are in `gradle.properties` and the `build.gradle` files. If a document disagrees with the current code, the code wins.

## 1. What we are building

This repository revives the Viltrumite mod on a new architecture. The target is a **multi-hero** superhero mod. Viltrumites come first. The owner then adds more heroes and characters, one by one. Do not design anything for one character only.

A hero must feel like its source material: mechanics, timings, interactions, animations and VFX. A hero must also be readable and playable in Minecraft. Recognizability is more important than literal copying. A hero that is faithful but not playable is a failed hero.

The source code came from decompilation of the original JARs. It is working history. It shows the current behavior. It does not define the target architecture. Keep the player-facing behavior and the current visual identity. Replace old structure when it blocks new heroes, duplicates logic, or puts one character into shared code.

`grebeshok105/Codex-Superheroes` (Fabric 1.21.1) is dropped. Use it only as a source of ideas and process. Do not copy its code or Fabric APIs into this repository.

Each change must make the whole project stronger. Reuse a healthy shared mechanism, extend it, or replace it on purpose. Do not add a disconnected island to avoid legacy code.

## 2. Repository map

| Path | Content |
|---|---|
| `viltrumitecore/.../viltrumitecore/` | `ability/`, `entity/action/`, `mixin/` (common), `network/` (`CoreMessages`, `packet/`), `worldevent/`, `config/`, `util/` (`ViltrumiteCorePlayer`) |
| `viltrumitecore/.../viltrumitecore/client/` | `anim/` (animation core), `mixin/` (model mixins), `render/` (`vfx/`, `animation/`, `blood/`), `gui/`, `particle/`, `event/` |
| `viltrumiteflight/.../viltrumiteflight/` | `mixin/`, `network/`, `util/` (`ViltrumiteFlightPlayer`, FlightState), `client/` (`mixin/`, `render/`, `sound/`, `gui/`) |
| `*/src/main/resources/` | mixin configs (`<modid>.mixins.json`, `<modid>.client.mixins.json`), assets, geo and animation JSON, shaders, sounds, lang |
| `original-jars/` | original release JARs. Read-only. |
| `tools/decompile.py`, `DECOMPILE_REPORT.md` | decompile pipeline and its report |
| `.github/workflows/build.yml` | CI build. Publishes the `mod-jars` artifact. |
| `.github/workflows/decompile.yml` | one-shot decompile. Do not run it again: the sources now contain manual fixes. |
| `.agents/skills/` | project skills |
| `.memory-bank/` | persistent agent memory (see §6) |

## 3. Animations and visual effects: hard rules

The current visuals are the identity of the mod. You must not break these rules without explicit approval from the user.

1. Before any work on animations, poses, model mixins, renderers, particles, shaders or VFX, read `.agents/skills/animation-system/SKILL.md` and follow it.
2. Make all animations with the existing animation and visual system only:
   - the animation core in `viltrumitecore/client/anim/` (`AnimCache`, `AnimationController`, `Animation`, `AnimationParser`, `KeyframeStack`, `Easing`, `BakedGeoModel`, `GeoBone`, `AnimRenderer`);
   - the player model mixins (`PunchModelMixin`, `ChopModelMixin`, `DashModelMixin`, `GrabModelMixin`, `BarrageModelMixin`, `ThunderclapModelMixin`, `SuperSpeedModelMixin`, and in flight `PlayerModelMixin`, `SlowFlyingModelMixin`);
   - the effects in `client/render/vfx/`, `client/render/animation/`, the particles and the post shaders.
3. Do not add another animation or VFX system. This includes PlayerAnimator, Emotecraft, GeckoLib animation, Veil, and any second keyframe engine or pose framework. GeckoLib is only a `compileOnly` compat dependency for one render mixin. Do not use it for new animations.
4. A hero without animations is not acceptable. A hero or ability is not done until each ability has its own animation and its own visual feedback in the current style.
5. Keep the current visual effects. Do not remove, replace, simplify or restyle an existing effect unless the user asks. A refactor must give the same visual result.
6. If the existing system cannot show a needed animation, extend the existing system in its current packages. Update the skill in the same task. Do not build a side system.
7. If the skill is missing or disagrees with the code, study `client/anim/` and the model mixins first. Then fix the skill.

## 4. How user and agent work together

The user writes the **design spec**: what and why — behavior, feel, constraints, edge cases, interactions, acceptance criteria.

The agent owns how. Before a non-trivial implementation, an implementation plan must exist. If it is missing, the agent studies the repo, the skills, the memory bank and the code, and writes the plan. Small, obvious fixes need no plan. A plan never changes the user's design decisions silently.

Do not reopen settled design decisions without a reason.

When the user asks to invent something large with no spec (a hero, a system, a mechanic, a VFX direction, a UI), use the brainstorming gate: research, 2–3 approaches, trade-offs, alignment with the user, then a final design spec. The implementation plan comes after that.

## 5. Autonomy

Work end to end: understand the goal → read the memory bank and skills → study the code and git history → write the plan if needed → implement → build → verify in game when runtime changes → fix findings → self-review → ship.

Resolve every question that code, git, skills, the memory bank, the original JARs or docs can answer. Do not ask the user for routine confirmations or status checks. Escalate only real design or product forks.

## 6. Memory bank

`.memory-bank/` is the persistent project memory for AI agents. The main file is `.memory-bank/project.md`. Topic files are `.memory-bank/<topic>.md`.

- Before work: read `project.md`. Then read only the topic files for your task.
- Store only knowledge that is expensive, ambiguous or impossible to recover from the code:
  - discovered system behavior;
  - architecture relations;
  - decompile artifacts and their known fixes;
  - important invariants;
  - project-specific implementation details;
  - decisions and their reasons;
  - behavior confirmed against the original JARs.
- Do not store facts that the code shows directly.
- Do not use the memory bank as a task log, changelog, scratchpad or dump of session notes.
- After work: update the memory bank only when the task produced durable knowledge for future agents.
- Write short, factual entries that are easy to scan. Write one fact per bullet. Mark facts that are not verified as `unverified`.
- Sources of truth: the code for implementation; the original JARs for original runtime behavior; `AGENTS.md` for workflow and repository rules.
- If an entry conflicts with the current code or verified JAR behavior, update or remove the entry in the same task.

## 7. Architecture quality

The implementation must work fully, fit the project logically, create no duplicate systems, live in the right place, and stay extensible. Do not take the shortest path when it adds duplication, one-off workarounds or future cost. Keep refactors in scope: a feature task is not a full rewrite.

Today the code assumes one race (Viltrumite) and puts most player logic in a few large mixins. New heroes need a hero seam. The first task that adds a second hero must introduce a shared hero contract in a plan. Do not copy Viltrumite code paths and add `if (hero == ...)` branches. Shared code asks the hero abstraction. It never asks which concrete character the player is.

## 8. Hard rules

- Gameplay is server-authoritative. Rendering, HUD, particles, camera, keybinds, screens and model mixins are client-only. Put them in `client` packages and in `*.client.mixins.json`. A dedicated server must never load a client class.
- `viltrumitecore` depends on `viltrumiteflight`. `viltrumiteflight` must not depend on `viltrumitecore`.
- Mixins stay narrow. Mark added members `@Unique`. Write self-casts as `(Target)(Object)this`. Register each mixin in the correct config. Respect `ViltrumiteMixinPlugin`.
- Networking goes through `CoreMessages` and `network/packet/` (flight: its own `network/`). A C2S handler validates the sender and the player state before it acts.
- Use Mojang official names and public Forge/Minecraft APIs.
- Do not rename mod ids, packages, registry ids, synced data, NBT keys or config keys without a migration. Renames break worlds and configs.
- `original-jars/` is read-only.
- Update all lang files of a mod together.

## 9. Decompiled code

The sources are Vineflower output. Loops, casts, lambdas, switches and generics can be wrong. Before you trust strange code, compare it with the original bytecode (`javap -c -p` on the class from `original-jars/`). Record each confirmed decompile fix in `.memory-bank/decompile.md`.

## 10. Skills

Project skills live in `.agents/skills/<name>/SKILL.md` with `name` and `description` frontmatter. A matching skill is a procedure: read it and follow it.
Before adding, replacing, processing or wiring any SFX, read `.agents/skills/sound-design/SKILL.md` and follow it.

| Skill | Use for |
|---|---|
| `animation-system` | all animation, pose, model-mixin, renderer and VFX work (mandatory, see §3) |
| `add_hero` | adding or porting a hero, adding a hero ability; shared hero toolkit and resource checker |
| `sound-design` | new, missing or changed sound effects, Openverse sourcing, audio loops and sound credits |

`AGENTS.md` owns global rules. Skills own repeatable workflows. Do not make a skill for a micro-action. If a skill disagrees with the current code or this file, fix the skill.

## 11. Tools

Use the lightest tool that answers.

| Tool | For |
|---|---|
| gradle (`build`, `runClient`) | compilation, dev client with both mods |
| `javap` on `original-jars/` | original behavior and decompile checks |
| git / gh | branches, history, PRs, CI runs |
| web docs | Forge 1.20.1 and library APIs. Do not guess unstable APIs from memory. |

## 12. Verification

Compilation proves nothing about behavior.

- `./gradlew build` must pass locally or in the CI `build.yml` run.
- Runtime changes (gameplay, input, rendering, entities, networking, VFX, HUD) need a check in game with `./gradlew runClient`. Check animations and VFX in first and third person. If you cannot launch the game, write this in the PR. Do not claim a check you did not do.
- If you change the client/common split or mixin configs, prove that a dedicated server starts.
- JUnit 5 tests live in each module's `src/test/java` (run `./gradlew test`). Add tests for new non-trivial pure logic.

## 13. Definition of Done

Done means all of these are true: the design spec is fully implemented; each new ability has its animation and VFX (§3); existing visuals are unchanged unless the user asked; `./gradlew build` is green; the in-game check is done or its absence is stated; acceptance criteria are checked; the memory bank and skills are updated if the task produced durable knowledge; self-review is done; all work is committed and pushed.

## 14. Git workflow and GitHub

**MANDATORY — COMMIT AND PUSH IMMEDIATELY:** Whenever any task-related work is completed or changed (code, tests, configuration, documentation, assets, or anything else), commit it and push it to GitHub immediately. Do not wait until the end of the task or session. If the task continues, push the completed increment first, then continue working. Nothing completed may remain only on the local PC. After pushing, verify that the commit is visible on GitHub. If a push fails, resolve the problem and retry; never claim success until the remote commit is verified.

GitHub is the only home of the work. Work on one branch per task. Make small logical commits in English, conventional style: `feat(scope): ...`, `fix(scope): ...`, `docs(scope): ...`. Ship each finished task as its own PR. Nothing task-related stays only on a local machine.

PR format:
- **Title in Russian**, explicit, names the task («фикс захвата у вилтрумитов», not «fix» or «wip»).
- **Body starts with «Для игрока»**: what changed and how it works, for players, in Russian, no technical part. Emojis are welcome.
- **Below: the technical part for other agents**: what, why, key decisions, what you verified and how, known limits.

## 15. Versioning

Each mod has its own version in the gradle files (inherited: core 1.10.3, flight 1.6.7). Before you build a jar for the user, bump the version of each changed mod: bugfix only → patch +1; features or content → minor +1, patch resets. Never ship two different jars with the same version.

## 16. Documentation

- `README.md`: public overview, in Russian.
- `AGENTS.md`: agent contract and global rules, in English.
- `.agents/skills/`: current procedures.
- `.memory-bank/`: durable agent memory.

Write `AGENTS.md`, skills and the memory bank in simplified technical English (ASD-STE100 principles): short sentences, active voice, one term for one concept, explicit rules, no vague words. Strict compliance is not required. Update a document in the same task that made it stale. Do not create a new markdown file if an existing one is the right place.

Authority when sources disagree: current code (implementation) and original JARs (original runtime behavior) → `AGENTS.md` → skills → memory bank → other docs.