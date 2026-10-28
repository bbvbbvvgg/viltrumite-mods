# Sung Jinwoo — playtest report (2026-10-28)

Client: `./gradlew :viltrumitecore:runClient`, Forge 47.3.0 / 1.20.1, Xvfb 1280×720, Mesa llvmpipe
(software GL — client FPS 6–7 is the renderer, not the mod). Creative superflat world, cheats on.
QA helpers: `-Dviltrumitecore.jinwoo.qa=true` enables `/jinwooqa fill` (roster to cap, full mana,
cooldowns reset) and `/jinwooqa mana`. Without the property the command is not registered.

## Checked in game

| Area | Result |
| --- | --- |
| Hero select | "Become Jinwoo" button with the 3D head preview; text fits the button in all locales |
| Look (TP) | Steve-shaped body with the 128×128 Jinwoo skin, hair, collar, coat tails, eye glow; calm and combat stance with both daggers |
| Look (FP) | Calm/empty hand → vanilla hand with the Jinwoo skin (offhand arm via the shared AlwaysVisibleOffhand mixin); daggers/moves → own arms model, guard pose with hands apart, both daggers visible |
| Daggers | MMB draw/sheath, 8 s idle dissolve (by design) |
| Combo | LMB 5-hit chain kills husks |
| Shadow Step (R) | Dash, afterimages hidden within 2.6 blocks of the camera, mana −10 |
| Mutilation (Y) | Works, mana cost applied |
| Ruler's Authority (Z, hold) | Lifts cows with purple tethers, throws them |
| Extraction (V) | "Arise." subtitle + portrait, soldier rises from the remnant; "No soul remnant nearby" hint |
| Exchange (B) | Swaps with the aimed shadow; with no target the hint now says "Aim at a shadow or post a sentinel" (was "No active shadows" while an army was out) |
| Arise ultimate (N) | Letterbox, 3 camera shots, black-mirror domain with purple veins, ghost army, subtitles with portrait, sky tint, domain timer, regen; nametag hidden during the camera; small eye flash |
| Page 2 | Summon/recall (R), Attack (Y), Guard (Z), Sentinel on a villager (V, puddle ring), Monarch's Charge (B) |
| HUD | Army counter `12/24` above the mana bar, no overlap with key labels |
| Load | Roster 24, 12 summoned + ultimate: integrated server 13–18 ms/tick (F3) |

## Fixed during the playtest

- FP arms too large/close → `FP_OFFSET (0, −1.72, −0.38)`; calm hand returns to vanilla.
- FP guard fists crossed in the middle of the screen → guard yaw ±4° (was ±14°).
- Left dagger invisible in FP → forward grip in `GUARD`.
- Shadow soldiers walking through the camera filled the screen with black → not rendered while the camera is inside their (inflated) box.
- Afterimages covering the FP view on Shadow Step.
- Ultimate: big blue eye squares → small violet flash; local nametag hidden during the camera.
- HUD counter overlapping the "R" key label; "Become Sung Jinwoo" overflowing the button.

## Known / not fixed

- Third-person camera can end up inside the army when it crowds behind the player (vanilla camera, no collision with entities).
- Mob death poof particles are vanilla.
- Hero page 3 is the generic empty page of the shared hero HUD.
- Sounds are synthesized (tools/sfx/jinwoo.py) and were checked for levels/length, not auditioned in a studio.
