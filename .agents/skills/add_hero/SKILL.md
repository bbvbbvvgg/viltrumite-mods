---
name: add_hero
description: Ports or adds a playable hero to viltrumite-mods (HeroId, HeroDefinition, abilities, skin, poses, VFX, sounds, lang, tests) or adds an ability to an existing hero. Use when adding a new hero, porting a hero from Codex-Superheroes, adding or reworking a hero ability, or reviewing such work. Do NOT use for animation-only work (use animation-system) or for flight-mod-only changes.
---

# add_hero

Procedure for a new playable hero in `viltrumitecore`. Input: a hero idea or a source hero (often from Codex-Superheroes, Fabric 1.21). Output: a playable, tested hero with design doc, lang in all locales, a green build and a PR.

`AGENTS.md` owns global rules. If this skill and `AGENTS.md` disagree, `AGENTS.md` wins. All animation, pose, renderer and VFX code also follows `animation-system` (mandatory).

`<id>` = lowercase id (`regulus`, `homelander`). `<Id>` = CamelCase (`Regulus`, `Homelander`). Package root: `viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/`.

Reference implementation: Regulus (`hero/regulus/`, `client/regulus/`, design `docs/design/2026-10-05-regulus-design.md`).

## Instructions

### 1. Read first
1. `AGENTS.md`, `.memory-bank/project.md`, last entries of `SESSION.md`.
2. `references/hero-seam.md` — the HeroDefinition contract and every file a hero touches.
3. `references/shared-toolkit.md` — reuse these before writing new FX/physics.
4. When porting: the source hero folder (Codex: `src/main/java/io/github/grebeshok105/codex/hero/<id>/` and `client/hero/<id>/`). List each ability, passive, item, effect, sound and texture. Mark each as: reuse existing kit / reuse toolkit / new code.

### 2. Design spec (Russian)
1. Copy `assets/design_spec_template.md` to `docs/design/<YYYY-MM-DD>-<id>-design.md`. Fill every section that applies; delete sections that do not.
2. Every ability needs: input, timings in ticks, numbers, cooldown, refusal behavior and player feedback, interactions.
3. Put all numbers in the "Сводка чисел" table. They become `<Id>Rules` constants.
4. Missing design decision → ask the user. Do not invent lore-critical behavior.

### 3. Server side — `hero/<id>/`
1. Add `HeroId.<ID>("<id>")` to `hero/HeroId.java`. Append at the end; never reorder (saved by key).
2. `<Id>Rules` — pure numbers and pure functions only (no Minecraft world access). Unit-testable.
3. `<Id>State` — per-player state. Persist with `saveHeroState`/`loadHeroState`, copy with `cloneHeroState` if it must survive death.
4. `<Id>Hero implements HeroDefinition` — implement required methods, override only the hooks the hero needs (`references/hero-seam.md` §1).
5. `<Id>Abilities` — ability ids `"<id>:<name>"`, slot order, `actionFor(id)`. New input kinds → add values to `HeroAction` (append only).
6. One class per ability (`LionsHeart`, `DebrisKick` pattern): `tryStart`, `tick`, `cancel`. Gate every start with `hero.canAct(player, action)` and `HeroDamage.isAnchored(player)`. Channels (beams, toggles) also stop in `tick` as soon as the player is anchored or dead, not only on press.
7. `enter(player)`: apply the hero chassis (max-health/armor modifiers) right away and set health, or a new hero starts with empty bonus hearts. Remove the modifiers in `cleanup(HERO_CHANGE)`.
8. Kit heroes (`allowsLegacyAbilities` true) inherit the Viltrumite chassis (strength, damage reduction, mayfly, heal every 40 t). Own regen → override `usesLegacyRegen` to false. No fall damage → `cancelsFallDamage` true (+ `onLanded` if the design has a landing hit).
9. Register in `HeroRegistry.registerDefaults()`.
10. How to become the hero: race selection button and/or an item/command calling `HeroRegistry.changeHero(player, HeroId.<ID>)`.

### 4. Abilities panel
1. Return the panel entries from `HeroDefinition.panelAbilities()` (see `HomelanderAbilities.panelAbilities`); `HeroRegistry.register` adds them to the registry. Do not edit `ViltrumiteAbilities` (Regulus registration there is legacy). Entry: id, icon `textures/gui/ability/<id>/<name>.png`, lang `ability.viltrumitecore.<id>_<name>.name/.desc`, grey-out predicate from the public snapshot.
2. Keep `ownsAbility(id)` in sync with the slot list, including flight utility indicators (`viltrumite:fast_takeoff`, `viltrumite:supersonic_flight`, `viltrumite:speed_lock`) — the HUD shows only owned ones.
3. A hero that replaces another must migrate saved slots (`HeroRegistry.resetLoadout` on the legacy save, `repairLoadout` otherwise); an id alias alone keeps the old slots.
4. A greyed slot press gives refusal feedback (sound), never silence — also on the client branch when the start is denied locally.

### 5. Client side — `client/<id>/`
1. `<Id>Client.init()` — `HeroSkins.register(HeroId.<ID>, provider)`; call it from `ViltrumiteCoreClient`.
2. Skin: `textures/entity/hero/<id>.png` (64x64). Add the race selection button and head preview in `client/gui/RaceSelectionScreen`.
3. Poses and VFX: follow `animation-system`. Use `PixelVfx`, `HeroImpactFx`, `CameraShake` before making new renderers. Pose offsets are additive; do not zero other parts' rotations (breaks flight poses). Pose weight caches are cleared when the entity is no longer the hero.
4. Effects that come from a body part (eye beams, glowing eyes) take positions from the rendered model (`HomelanderEyesLayer`), not from `getEyePosition`: otherwise they float in the air in flight poses. Glow belongs on the model, not as free billboards.
5. Movement that the player feels (dash, jump, knockback of self) is client-authoritative: apply velocity on the client too.

### 6. Reuse the shared toolkit
Use `references/shared-toolkit.md`. Typical mapping:
- block-shard bursts, shockwaves, slam rings, blade trails, take-off puffs, flashes → `HeroFx.*` (server) → `HeroImpactFx` (client, automatic).
- flying debris / ground eruption → `HeroDebris.erupt` / `launchBlock`.
- fall-damage landing shockwave → `HeroShockwave.land` from `onLanded`.
- super jump on the jump key → return a velocity from `superJumpVelocity`; sounds in `onSuperJump`.
- breaking blocks → `HeroDestruction.canDestroy/destroyBlock` (honours `mobGriefing` and unbreakable blocks).
- grabs / freezes / anchors → `ControlManager`, `HeroRegistry.allowsExternalControl`.
- damage → `HeroDamage.route` / `applyCleanDamage`.

### 7. Sounds and textures
Before sourcing or changing sounds, follow `../sound-design/SKILL.md` (CC0/own assets only).
1. Sound events in `assets/viltrumitecore/sounds.json`, Java holders in the core sound registry.
2. Binary files (`.ogg`, `.png`) that must be pushed through the GitHub MCP go to `viltrumitecore/src/main/binassets/<same path>.b64` (base64). Gradle task `decodeBinaryAssets` writes them into resources at build time.
3. Use only CC0 or own sounds. Record source and license in the design doc.

### 8. Lang
Add every key to all locales in `assets/viltrumitecore/lang/` in the same change (en_us, de_de, es_*, tr_tr, zh_cn …). en_us is the source of truth.

### 9. Tests and checks
1. JUnit tests for `<Id>Rules` and any pure toolkit math in `viltrumitecore/src/test/java/.../hero/<id>/`.
2. Run `python3 .agents/skills/add_hero/scripts/check_hero_resources.py --hero <id>` — must report 0 new problems.
3. Run the full build: `./gradlew build` (JDK 17). All tests green.
4. `git diff --check`.

### 10. Ship
1. Bump the core mod version (feature → minor) only when a jar is shipped.
2. Append a Russian entry to `SESSION.md`; add short facts to `.memory-bank/project.md`.
3. PR per `AGENTS.md` §14: Russian title, body starts with «Для игрока», then the technical part.

## Examples

### Example: port Homelander from Codex
Source: `codex/hero/homelander/` — EyeLasers, HandClap, IronFists, StunningRoar, XRay, madness + aftermath, milk bottle, uranium dagger/isotope weakness, flight, regen, laser scorch, no fall damage.

Mapping:
- Flight, punch → reuse the Viltrumite kit (`allowsFlight` true, `meleeDamageFactor`).
- HandClap → reuse Thunderclap code (`ThunderClapManager`, `HeroDebris.launchBlock`, `HeroFx.shockwave`).
- No fall damage + landing hit → `cancelsFallDamage` true, `onLanded` → `HeroShockwave.land` with a `HomelanderRules.LANDING` spec.
- EyeLasers, laser scorch → new: beam ray-cast + `HeroDestruction` + new beam renderer (animation-system).
- XRay → new client-only overlay; StunningRoar → new; madness → new state with timers.
- Uranium weakness, milk → new items + damage hook in `HeroDamage`/`HeroEvents` through a HeroDefinition hook, not a `HeroId.HOMELANDER` branch.

Result layout: `hero/homelander/{HomelanderHero,HomelanderRules,HomelanderState,HomelanderAbilities,EyeLasers,...}.java`, `client/homelander/HomelanderClient.java`, `textures/entity/hero/homelander.png`, design doc in Russian, tests for `HomelanderRules`.

### Example: add one ability to an existing hero
1. Add the row to the hero design doc and the numbers table.
2. Add the id to `<Id>Abilities`, a `HeroAction` value if it needs a key, the slot in `ViltrumiteAbilities`, the icon, lang in all locales.
3. Implement the ability class, wire it in `<Id>Hero.handleInput/tick/cleanup`.
4. Test the rules, run the checker and the build.

## Review checklist (Codex findings on PR #8)
Check before opening the PR:
- [ ] Every push/knockback of a target checks `HeroRegistry.allowsExternalControl(target, ControlKind.IMPULSE)` and `!HeroDamage.isAnchored(target)`.
- [ ] Fire and other side effects apply only when `target.hurt(...)` returned true.
- [ ] Ray hits on `PartEntity` (ender dragon) resolve to `getParent()`.
- [ ] Every new `MobEffect` with `showIcon` has `textures/mob_effect/<name>.png` (as `binassets/*.b64`).
- [ ] Effects a hero applies are cut back on stop/cleanup (no linger past the spec), and fear-like effects beat mob goals (`setTarget(null)` while active).
- [ ] VFX shape matches the gameplay area (a cone ability is not drawn as a radial ring; camera shake not behind the caster).
- [ ] Legacy entry points (`setViltrumite`, commands, race screen) go through `HeroRegistry.changeHero`.
- [ ] No concrete hero class imported from shared registries; `NoHeroBranchTest` green.
- [ ] Ask the user about visuals before "fixing" them for a review: the Codex cone-roar fix was reverted by the user.

## Edge cases
- **No hero branches in shared code.** Do not add `if (heroId == HeroId.X)` to shared classes. Add a default method to `HeroDefinition` and override it. Existing Regulus branches are legacy debt (`references/hero-seam.md` §3); when you touch one, move it behind a hook.
- **HeroPublicSnapshot is Regulus-shaped** (hearts, madness, lion, 6 cooldowns). If the new hero needs other public data, extend the snapshot generically (more cooldown slots, a generic int/flag array), do not add hero-named fields.
- **Stable ids.** Never rename registry ids, `HeroId` keys, NBT keys or key-mapping ids (example: the super-jump key is still `key.viltrumitecore.regulus_super_jump` to keep player bindings).
- **Fall distance**: read it in `LivingFallEvent` (already done by `HeroEvents` → `onLanded`). `player.fallDistance` is reset before other hooks.
- **FallingBlockEntity** constructor is private — use `HeroDebris.launchBlock`, it uses the invoker mixin.
- **Server vs client**: `superJumpVelocity` is called on both sides; keep it free of server-only state or sync it in the snapshot.
- **Cleanup**: every ability must stop in `cleanup(player, reason)` (death, logout, hero change, dimension change). Leaked state = stuck poses and frozen targets.
- **Concurrent agents**: before pushing, fetch and compare with the remote branch; other sessions may have pushed.
- **GitHub MCP push is text only**: binaries via `binassets/*.b64`; deleted or renamed files need a separate delete call.

## Troubleshooting
- Checker reports a missing lang key → add it to en_us and every other locale.
- Checker reports a missing `.ogg` → add `binassets/.../<name>.ogg.b64` or fix the `sounds.json` name.
- Sound plays for nobody → the server `playSound(player, ...)` with the player as first arg excludes that player; pass `null` to include everyone.
- Hero changes do not reach the client → check `snapshot(player)` and that the value is part of `HeroPublicSnapshot`.
- `./gradlew build --offline` fails on the foojay plugin → the Gradle cache was wiped; run one online build.
- Kit dash does nothing in flight → the shared `startDash` only starts from NONE/HOVER.
- Super jump does nothing → `superJumpVelocity` returns 0 on the client, or `canAct(JUMP)` is false, or the player is anchored.
