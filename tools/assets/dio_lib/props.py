"""DIO props: throwing knife, Road Roller, outfit parts on the player model."""
from .geo import Geo


def knife():
    g = Geo("dio_knife", density=4)
    g.bone("knife", None, [0, 0, 0])
    g.cube("knife", [-0.22, -0.85, -10.5], [0.44, 1.7, 8.5], "steel")
    g.cube("knife", [-0.12, -1.05, -10.5], [0.24, 0.3, 8.5], "steel_dark")
    g.cube("knife", [-0.2, -0.62, -12.4], [0.4, 1.24, 2.2], "steel", rotation=[-22, 0, 0], pivot=[0, -0.2, -10.4])
    g.cube("knife", [-0.1, -0.2, -10.6], [0.2, 0.5, 8.0], "steel_dark")
    g.cube("knife", [-1.9, -0.55, -2.4], [3.8, 1.1, 0.7], "brass")
    g.cube("knife", [-2.2, -0.4, -2.3], [0.5, 0.8, 0.5], "brass")
    g.cube("knife", [1.7, -0.4, -2.3], [0.5, 0.8, 0.5], "brass")
    g.cube("knife", [-0.6, -0.6, -1.7], [1.2, 1.2, 4.8], "leather", deco={"north": "grip", "south": "grip", "up": "grip", "down": "grip", "east": "grip", "west": "grip"})
    g.cube("knife", [-0.8, -0.8, 3.1], [1.6, 1.6, 1.1], "brass")
    return g


def _cyl_x(g, bone, cx, cy, cz, r, width, mat, n=4, deco=None):
    """Cylinder along X out of n squares rotated around X (n=4 -> 16-gon)."""
    s = 2 * r * 0.98
    for i in range(n):
        g.cube(bone, [cx - width / 2, cy - s / 2, cz - s / 2], [width, s, s], mat,
               rotation=[i * 90.0 / n, 0, 0] if i else None, pivot=[cx, cy, cz], deco=deco)


def road_roller():
    g = Geo("road_roller", density=1.5)
    g.bone("roller", None, [0, 0, 0])
    # front drum (big), rear drum
    g.bone("front_drum", "roller", [0, 11, -26])
    _cyl_x(g, "front_drum", 0, 11, -26, 11, 38, "iron", deco={"north": "tread", "south": "tread", "up": "tread", "down": "tread"})
    _cyl_x(g, "front_drum", 0, 11, -26, 5, 42, "roller_o")
    g.bone("rear_drum", "roller", [0, 9.5, 25])
    _cyl_x(g, "rear_drum", 0, 9.5, 25, 9.5, 40, "iron", deco={"north": "tread", "south": "tread", "up": "tread", "down": "tread"})
    _cyl_x(g, "rear_drum", 0, 9.5, 25, 4, 44, "roller_o")
    # yoke over the front drum
    g.bone("yoke", "roller", [0, 18, -26])
    g.cube("yoke", [-22.5, 6, -32], [3, 16, 12], "roller_o", deco={"west": "rivets", "east": "rivets"})
    g.cube("yoke", [19.5, 6, -32], [3, 16, 12], "roller_o", deco={"west": "rivets", "east": "rivets"})
    g.cube("yoke", [-22.5, 22, -34], [45, 4, 14], "roller_y", deco={"north": "text_road_roller", "up": "hazard"})
    g.cube("yoke", [-6, 20, -22], [12, 6, 10], "roller_o")
    # body
    g.bone("body", "roller", [0, 18, 0])
    g.cube("body", [-18, 14, -14], [36, 10, 34], "roller_y", deco={"west": "text_road_roller", "east": "text_road_roller"})
    g.cube("body", [-18.5, 13, -15], [37, 2, 36], "roller_o")
    g.cube("body", [-14, 24, -12], [28, 2, 16], "iron", deco={"up": "tread"})
    g.cube("body", [-6.5, 26, -4], [13, 4, 9], "leather")
    g.cube("body", [-6.5, 30, 4], [13, 9, 2], "leather")
    g.cube("body", [-1, 26, -12], [2, 11, 2], "iron", rotation=[24, 0, 0], pivot=[0, 26, -11])
    g.cube("body", [-5, 36, -18], [10, 1, 10], "rubber", rotation=[24, 0, 0], pivot=[0, 37, -13])
    g.cube("body", [-14.5, 19, -15.5], [4, 3, 1], "glass", deco={"north": "headlight"})
    g.cube("body", [10.5, 19, -15.5], [4, 3, 1], "glass", deco={"north": "headlight"})
    # engine hood (rear)
    g.bone("hood", "body", [0, 24, 8])
    g.cube("hood", [-16, 24, 6], [32, 12, 16], "roller_y", deco={"south": "grille", "up": "lines"})
    g.cube("hood", [-16.5, 35, 5.5], [33, 1.5, 17], "roller_o", deco={"up": "hazard"})
    g.cube("hood", [11, 36, 16], [2.5, 14, 2.5], "iron")
    g.cube("hood", [10.5, 49, 15.5], [3.5, 2, 3.5], "iron")
    # canopy
    g.bone("roof", "body", [0, 24, 0])
    for x in (-17, 15):
        for z in (-12, 4):
            g.cube("roof", [x, 24, z], [2, 22, 2], "iron")
    g.cube("roof", [-19, 46, -15], [38, 2.5, 24], "roller_o", deco={"north": "hazard", "south": "hazard", "up": "lines"})
    g.cube("roof", [-18, 45, -14], [36, 1, 22], "roller_y")
    return g


def outfit():
    """DIO outfit parts in player-model coordinates (feet y = 0, head pivot y = 24)."""
    g = Geo("dio_outfit", density=2)
    g.bone("head", None, [0, 24, 0])
    g.cube("head", [-4.2, 28.6, -4.2], [8.4, 1.4, 8.4], "teal_dark")
    g.cube("head", [-1.3, 28.2, -4.6], [2.6, 2.2, 0.5], "teal", deco={"north": "heart_gold"})
    g.bone("body", None, [0, 24, 0])
    g.cube("body", [-4.6, 22.2, -2.6], [9.2, 2.4, 5.2], "gold", deco={"north": "lines"})
    g.cube("body", [-4.6, 23.5, 1.4], [9.2, 3.2, 1.4], "gold")
    g.cube("body", [-3.9, 12.4, -2.35], [1.4, 9.6, 0.4], "gold")
    g.cube("body", [2.5, 12.4, -2.35], [1.4, 9.6, 0.4], "gold")
    g.bone("rightarm", None, [-5, 22, 0])
    g.cube("rightarm", [-8.4, 21.4, -2.4], [4.8, 2.4, 4.8], "gold", rotation=[0, 0, 10], pivot=[-5, 22, 0], deco={"up": "rivets"})
    g.bone("leftarm", None, [5, 22, 0])
    g.cube("leftarm", [3.6, 21.4, -2.4], [4.8, 2.4, 4.8], "gold", rotation=[0, 0, -10], pivot=[5, 22, 0], deco={"up": "rivets"})
    for nm, x in (("rightleg", -1.9), ("leftleg", 1.9)):
        g.bone(nm, None, [x, 12, 0])
        g.cube(nm, [x - 1.6, 5.2, -2.7], [3.2, 3.0, 0.8], "teal", deco={"north": "heart_gold"})
        g.cube(nm, [x - 1.3, 1.0, -2.5], [2.6, 1.8, 0.6], "teal", deco={"north": "heart_gold"})
    return g
