# Тор: скелет нового тела (R1) — контракт для R2

> Дата: 2026-10-28. Этап R1 переработки (`2026-10-28-thor-rework.md` §1–2). Этот документ — то, на что опираются клипы R2:
> имена костей, пивоты, якоря и правила записи клипа. Имена костей и якорей **стабильны**: переименование = правка этого документа + `ThorAssets` + тестов.

## 1. Файлы
| Что | Путь | Источник |
|---|---|---|
| Тело (TP), 17 костей | `assets/viltrumitecore/geo/thor/thor.geo.json` | `tools/assets/make_thor_body.py` |
| FP-руки, 8 костей | `assets/viltrumitecore/geo/thor/thor_arms.geo.json` | тот же генератор |
| Атлас тела 128×64 | `textures/entity/thor/thor_body.png` (в git как `.png.b64`) | тот же генератор (`--png` — превью атласа) |
| Клипы тела | `assets/viltrumitecore/animations/thor/thor.animation.json` | тот же генератор (`clips()`) |
| Мьёльнир | `geo/thor/mjolnir_hand.geo.json`, `mjolnir_belt.geo.json`, `mjolnir_world.geo.json` | без изменений |
| Имена в коде | `client/thor/ThorAssets` | — |

Перегенерация: `python3 tools/assets/make_thor_body.py --png /tmp/thor_body.png`. Ручные правки `thor.geo.json` / `thor.animation.json` затираются генератором — правьте генератор (или в R2 переведите клипы в отдельный файл Blockbench, см. §5).

## 2. Кости тела (`geometry.thor`, единицы — пиксели модели, 16 px = 1 блок до масштаба рендера)
Ось модели Bedrock: перед = **−Z**, правая сторона персонажа = **−X**, вверх = +Y. Ноль — земля под ногами, центр хитбокса.

| Кость | Родитель | Пивот (x, y, z) | Покой (rot) | Назначение |
|---|---|---|---|---|
| `body` | — | (0, 21, 0) | — | таз/корпус; всё верхнее тело — его дети |
| `belly` | body | (0, 29.5, −4.5) | (−6, 0, 0) | живот (вторичная, «дыхание») |
| `head` | body | (0, 40.5, −1) | — | голова, шея в пивоте |
| `beard` | head | (0, 42, −6) | — | борода (вторичная, подбородок) |
| `right_arm` | body | (−12.25, 37.5, 0) | (0, 0, 4) | плечо; rot z — разведение от торса |
| `right_forearm` | right_arm | (−12.75, 28.5, 0) | — | локоть |
| `right_hand` | right_forearm | (−12.75, 19.5, 0) | — | запястье |
| `mjolnir_grip` | right_hand | (−12.75, 17, 0) | — | **локатор**: центр кулака, сюда рисуется Мьёльнир/предмет |
| `left_arm` | body | (12.25, 37.5, 0) | (0, 0, −4) | зеркально |
| `left_forearm` | left_arm | (12.75, 28.5, 0) | — | |
| `left_hand` | left_forearm | (12.75, 19.5, 0) | — | |
| `left_grip` | left_hand | (12.75, 17, 0) | — | **локатор** левого кулака (предмет в левой руке, второй хват) |
| `mjolnir_belt` | body | (−10.75, 21.5, 0.5) | — | **локатор**: молот на поясе (правое бедро) |
| `right_leg` | — | (−4.5, 21, 0) | — | бедро (тазобедренный сустав) |
| `right_shin` | right_leg | (−4.5, 11, 0) | — | колено; стопа — часть голени |
| `left_leg` | — | (4.5, 21, 0) | — | |
| `left_shin` | left_leg | (4.5, 11, 0) | — | |

Длины: плечо 9 px, предплечье 9 px, кисть до центра кулака 2.5 px (плечо→кулак 20.5 px); бедро 10 px, голень 11 px. Макушка 51.1 px.
Ноги — верхнего уровня (как у ванили): наклон `body` не уводит ноги, приседание = `position` у `body` и обеих ног одновременно.

Масштаб рендера: `ThorHero.BODY_HEIGHT_SCALE` = 1.75 → 51.1 px × 1.75 / 16 ≈ **3.19 блока** визуально; ширина `BODY_WIDTH_SCALE` = 1.6.

## 3. FP-руки (`geometry.thor_arms`)
Рисуются ванильной цепочкой первого лица (`RenderArmEvent`/`RenderHandEvent`), пивоты в кадре ванильной руки.

| Кость | Родитель | Пивот |
|---|---|---|
| `armorRightArm` | — | (−5, 22, 0) |
| `right_forearm` | armorRightArm | (−6, 21, 0) |
| `right_hand` | right_forearm | (−6, 14, 0) |
| `mjolnir_grip` | right_hand | (−6, 12, 0) — Мьёльнир в FP |
| `armorLeftArm` | — | (5, 22, 0) |
| `left_forearm` | armorLeftArm | (6, 21, 0) |
| `left_hand` | left_forearm | (6, 14, 0) |
| `left_grip` | left_hand | (6, 12, 0) |

Корни `armorRightArm`/`armorLeftArm` — проектная конвенция FP-рук (как `ironman/hulkbuster/fp_arm.geo.json`): их кадр = кадр ванильной FP-руки (качание, взмах, отдача ванили остаются). Ниже корня имена совпадают с телом (`right_forearm`, `right_hand`, `mjolnir_grip`…), поэтому один клип R2 с этими костями играется и в FP, и в TP — одни часы действия.

## 4. Якоря
| Якорь | Где | API |
|---|---|---|
| Кулак с молотом | кость `mjolnir_grip` | `ThorRenderer.locator(body, ThorAssets.MJOLNIR_GRIP)` → `Matrix4f` модели |
| Левый кулак | `left_grip` | `ThorRenderer.locator(body, ThorAssets.LEFT_GRIP)` |
| Молот на поясе | `mjolnir_belt` | `ThorRenderer.locator(body, ThorAssets.MJOLNIR_BELT_BONE)` |
| Предмет ванили в руке | кулаки | `ThorRenderer.itemFrame(body, HumanoidArm)` |
| Рукоять стоящего молота | мир | `MjolnirAnchors.grip(hammer, pt)` → `Grip(handleTop, upperPalm, lowerPalm, up)`; `MjolnirAnchors.lifting(player, pt)` — молот, который игрок сейчас поднимает |
| Ладони Тора на рукояти | мир | `ThorLift.thorPalms(grip)`: верхний кулак 1.0 px под `handleTop`, нижний 5.6 px |
| IK обеих рук | модель | `ThorLift.solveThor(model, rightFist, leftFist, up, weight)` — плечо–локоть–запястье, полюс локтя `ELBOW_POLE` |
| Двухкостный IK | любая цепочка | `GeoPose.reach(upper, lower, end, tip, target, pole, endRotation)` |

## 5. Конвейер позы (`ThorBodyPose`, каждый кадр)
1. **Мост ванили** `bridge(model, turns, head)`: повороты частей `PlayerModel` после `setupAnim` и всех миксинов (ходьба, взмах, текущие клипы `ThorPoser`, полёт) → кости geo. Позиции частей не переносятся.
2. **Базовые клипы** `baseClips(model, crouch, sit)`: `crouch`, `sit` из `thor.animation.json`, аддитивно поверх моста (весами позы).
3. **Подъём** `ThorLift.poseThor(...)`: оверлей `lift_squat` по весу (0.3 с разгон, smoothstep) + IK обеих рук на рукоять; кулаки на половине веса идут дугой наружу-вперёд вокруг бёдер.
4. Рендер: `ThorRenderer` рисует тело, Мьёльнир на `mjolnir_grip`/`mjolnir_belt`, предмет в руке, ник, невидимость/свечение (`passes`), тень; ванильный рендер игрока отменяется.

R2 заменяет шаг 1 клипами (idle/walk/run/атаки) и оставляет шаги 2–4. IK — после клипа: клип ставит FK-позу, IK доводит кулаки до якоря.

## 6. Как написать клип
- Формат: Bedrock `animation.json` (`format_version 1.8.0`), кости по именам из §2/§3, ключи `rotation`/`position`, easing как в существующих клипах; проигрывание — `AnimationController.overlay(clip, model, seconds, weight)`.
- Знаки (проверено превью): `rotation.x` > 0 — наклон вперёд для `body`/`head` (голова < 0 — взгляд вверх); бедро `x` < 0 — колено вперёд; голень `x` > 0 — стопа назад (сгиб колена); `right_arm z` > 0 — от торса (левая зеркально); `position.y` < 0 — вниз, `position.z` > 0 — назад.
- Присед/наклон: двигайте `body` **и** обе ноги одной `position`, затем согните голени так, чтобы подошвы остались на земле (тест `feetOnTheGround`, допуск 1 px).
- Молот в руке — только через `mjolnir_grip` (ни одного числа позы молота в Java). Две руки на рукояти — через IK к якорю, а не подбором углов.
- Проверка: `ThorBodyGeometryTest` (скан мешей: предплечья/кисти против торса, живота, головы, бороды, ног — допуск 0.5 px, кадры смешивания 1 px; ноги на земле; макушка в присяде под 2.625; оба кулака на рукояти) и превью `python3 tools/preview_thor.py OUT --poses rest,crouch,lift_0` (вид спереди/сбоку/сзади + FP). Новую позу добавьте в `ThorBodyGeometryTest.poses()`.

## 7. Хитбокс (`hero/HeroBodySize`, `HeroBodyTracker`)
| Поза | Размер (ш × в), блоки | Глаза |
|---|---|---|
| STANDING | 0.96 × 3.15 | 2.835 |
| CROUCHING | 0.96 × 2.625 | 2.2225 |
| SWIMMING / FALL_FLYING / SPIN_ATTACK | 0.96 × 0.9 (высота ×1.5 максимум — пролезает в 1-блочный лаз) | 0.6 |
| SLEEPING | ваниль 0.2 × 0.2 | ваниль |

Ширина 0.96 (не 1.0–1.2 из спецификации): Тор проходит в 1-блочные двери и лазы. Reach +1.0 блока, шаг +0.4 (`STEP_HEIGHT_ADDITION`). Размеры возвращаются к ванили при выходе из героя, смерти, релоге, смене измерения (`HeroBodyTracker` пересчитывает при смене героя/позы и при первом появлении сущности; `EntityPoseBoxMixin` подгоняет коробку позы, чтобы проверка «влезает ли поза» шла по росту Тора).
