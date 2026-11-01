r"""Synthetic city block for Meteor Rain 2.0 QA (city fire on concrete, stone, glass, brick and wood).

Usage (dev server on a flat world, RCON on, see smoke_flat_city.py):
    python3 tools/city/meteor_fire_block.py | xargs -d '\n' python3 tools/city/rcon.py
Builds around (0, -60, 0): asphalt streets, a 24-high concrete office with glass bands and plank floors, a
stone-brick block, a glass office, a brick block, a row of wooden houses, street trees. Every write goes
through normal commands, so the living city journals it like any build (run before /city enable or reset
the reference accordingly).
"""
G = -61  # ground block layer (flat world grass), feet y = -60


def box(x1, y1, z1, x2, y2, z2, block, mode=""):
    return f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block} {mode}".strip()


def building(x1, z1, x2, z2, h, wall, floor, window, band=3, furniture=None):
    y1, y2 = G + 1, G + h
    out = [box(x1, y1, z1, x2, y2, z2, wall, "hollow")]
    for y in range(y1 + band, y2, band + 1):
        out.append(box(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, floor))
        # a window band on every side of each storey
        out.append(box(x1, y - 2, z1 + 1, x1, y - 1, z2 - 1, window))
        out.append(box(x2, y - 2, z1 + 1, x2, y - 1, z2 - 1, window))
        out.append(box(x1 + 1, y - 2, z1, x2 - 1, y - 1, z1, window))
        out.append(box(x1 + 1, y - 2, z2, x2 - 1, y - 1, z2, window))
        if furniture:
            out.append(box(x1 + 2, y + 1, z1 + 2, x1 + 3, y + 1, z2 - 2, furniture))
    out.append(box(x1, y2, z1, x2, y2, z2, wall))
    return out


cmds = ["gamerule doFireTick false", "gamerule mobGriefing true", "time set noon", "weather clear"]
cmds += [box(-60, G, -60, 60, G, 60, "minecraft:gray_concrete_powder"),
         box(-60, G, -3, 60, G, 3, "minecraft:black_concrete"), box(-3, G, -60, 3, G, 60, "minecraft:black_concrete"),
         box(-60, G, 4, 60, G, 4, "minecraft:smooth_stone"), box(-60, G, -4, 60, G, -4, "minecraft:smooth_stone")]
cmds += building(6, 6, 18, 18, 24, "minecraft:gray_concrete", "minecraft:oak_planks", "minecraft:glass_pane", 3, "minecraft:white_wool")
cmds += building(-20, 6, -8, 16, 14, "minecraft:stone_bricks", "minecraft:spruce_planks", "minecraft:glass_pane", 3, "minecraft:bookshelf")
cmds += building(6, -20, 16, -8, 18, "minecraft:light_gray_concrete", "minecraft:smooth_stone_slab", "minecraft:glass", 3, "minecraft:red_wool")
cmds += building(22, -6, 32, 6, 10, "minecraft:bricks", "minecraft:oak_planks", "minecraft:glass_pane", 3, "minecraft:hay_block")
for i, x in enumerate(range(-30, -8, 7)):
    cmds += building(x, -18, x + 5, -10, 6, "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:glass_pane", 2)
    cmds.append(box(x, G + 7, -18, x + 5, G + 7, -10, "minecraft:dark_oak_slab"))
for x in range(-50, 51, 12):
    cmds.append(f"setblock {x} {G + 1} 6 minecraft:oak_log")
    cmds.append(box(x - 1, G + 3, 5, x + 1, G + 4, 7, "minecraft:oak_leaves[persistent=true]"))
print("\n".join(cmds))
