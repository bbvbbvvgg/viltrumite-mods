"""Heaven's Door: a small boy in a long cream coat with a bow tie, lines around the eyes and a see-through top hat
with a checkered band (original design after the canon description; no copied art).

Rig (bedrock, front = north, right = -X), feet at y = 0, hat top ~ y = 31:
root > pelvis > waist > chest > neck > head > hat_band, hat (translucent pass), hair
chest > coat_back, coat_R, coat_L, bow
chest > shoulder_R > forearm_R > hand_R > finger_R (index, VFX anchor: pivot - 1.6 y), thumb_R  (+ mirrored _L)
pelvis > thigh_R > shin_R > foot_R (+ mirrored _L)
"""
from dio_lib.geo import Geo


def build():
    g = Geo("heavens_door", density=4)
    g.bone("root", None, [0, 0, 0])
    g.bone("pelvis", "root", [0, 11, 0])
    g.cube("pelvis", [-2.6, 9.6, -1.5], [5.2, 2.4, 3.0], "pants")
    g.cube("pelvis", [-2.7, 11.4, -1.6], [5.4, 0.7, 3.2], "pen", deco={"north": "buttons"})
    g.bone("waist", "pelvis", [0, 12, 0])
    g.cube("waist", [-2.3, 12.0, -1.3], [4.6, 2.9, 2.6], "jacket")
    g.bone("chest", "waist", [0, 14.8, 0])
    g.cube("chest", [-2.8, 14.8, -1.6], [5.6, 3.8, 3.2], "jacket", deco={"north": "seam"})
    g.cube("chest", [-1.1, 17.2, -1.8], [2.2, 1.2, 0.4], "paper")
    g.bone("bow", "chest", [0, 18.0, -1.9])
    g.cube("bow", [-1.3, 17.4, -2.2], [2.6, 1.2, 0.5], "bow", deco={"north": "bow"})
    # coat: shoulders/yoke on the chest, long panels as separate swinging bones
    g.cube("chest", [-3.1, 15.6, -1.85], [6.2, 3.1, 3.7], "coat", inflate=0.05, deco={"north": "stitch"})
    g.cube("chest", [-2.0, 18.5, -1.9], [4.0, 0.9, 3.8], "coat")
    g.bone("coat_back", "chest", [0, 18.4, 1.7])
    g.cube("coat_back", [-3.2, 4.2, 1.5], [6.4, 14.2, 0.6], "coat", deco={"south": "seam+stitch", "north": "stitch"})
    g.cube("coat_back", [-3.1, 4.2, 1.35], [6.2, 13.8, 0.15], "coat_in")
    for side, sx in (("R", -1), ("L", 1)):
        def X(x0, w, sx=sx):
            return x0 if sx < 0 else -(x0 + w)
        c = "coat_" + side
        g.bone(c, "chest", [sx * 2.4, 17.5, -1.6])
        g.cube(c, [X(-3.3, 2.5), 5.0, -2.1], [2.5, 12.6, 0.5], "coat", deco={"north": "stitch+buttons" if sx < 0 else "stitch"})
        g.cube(c, [X(-3.35, 0.6), 5.0, -1.7], [0.6, 12.6, 3.0], "coat")
    # neck, head
    g.bone("neck", "chest", [0, 18.6, 0])
    g.cube("neck", [-0.8, 18.5, -0.8], [1.6, 0.9, 1.6], "hd_skin")
    g.bone("head", "neck", [0, 19.2, 0])
    g.cube("head", [-3.2, 19.2, -3.0], [6.4, 6.2, 6.0], "hd_skin", deco={"north": "hd_face"})
    g.bone("hair", "head", [0, 24, 0])
    g.cube("hair", [-3.45, 23.4, -3.25], [6.9, 2.0, 6.5], "hd_hair", deco={"north": "strands", "east": "strands", "west": "strands", "south": "strands"})
    g.cube("hair", [-3.45, 20.0, 1.6], [6.9, 3.6, 1.65], "hd_hair", deco={"south": "strands"})
    g.cube("hair", [-3.5, 20.6, -2.6], [0.6, 3.0, 4.2], "hd_hair", deco={"west": "strands"})
    g.cube("hair", [2.9, 20.6, -2.6], [0.6, 3.0, 4.2], "hd_hair", deco={"east": "strands"})
    g.cube("hair", [-3.0, 22.9, -3.45], [3.4, 1.2, 0.6], "hd_hair", rotation=[0, 0, -8], pivot=[-1.3, 23.5, -3.2])
    g.cube("hair", [0.6, 23.1, -3.45], [2.5, 0.9, 0.6], "hd_hair", rotation=[0, 0, 10], pivot=[1.8, 23.5, -3.2])
    g.bone("hat_band", "head", [0, 25.4, 0])
    g.cube("hat_band", [-2.75, 25.3, -2.75], [5.5, 1.1, 5.5], "hd_band", deco={f: "checker" for f in ("north", "south", "east", "west")})
    g.bone("hat", "head", [0, 25.4, 0])
    g.cube("hat", [-4.6, 25.2, -4.6], [9.2, 0.25, 9.2], "glass")
    g.cube("hat", [-2.7, 26.4, -2.7], [5.4, 4.6, 5.4], "glass")
    # arms
    for side, sx in (("R", -1), ("L", 1)):
        def X(x0, w, sx=sx):
            return x0 if sx < 0 else -(x0 + w)
        s = "shoulder_" + side
        g.bone(s, "chest", [sx * 3.3, 18.0, 0])
        g.cube(s, [X(-4.6, 1.9), 14.4, -0.95], [1.9, 4.0, 1.9], "coat", inflate=0.1)
        f = "forearm_" + side
        g.bone(f, s, [sx * 3.65, 14.6, 0])
        g.cube(f, [X(-4.55, 1.8), 11.6, -0.9], [1.8, 3.1, 1.8], "coat")
        g.cube(f, [X(-4.7, 2.1), 11.4, -1.05], [2.1, 0.8, 2.1], "coat_in")
        h = "hand_" + side
        g.bone(h, f, [sx * 3.65, 11.5, 0])
        g.cube(h, [X(-4.35, 1.4), 10.2, -0.75], [1.4, 1.4, 1.5], "hd_skin")
        n = "finger_" + side
        g.bone(n, h, [sx * 3.8, 10.3, -0.45])
        g.cube(n, [X(-4.12, 0.5), 8.7, -0.7], [0.5, 1.6, 0.5], "hd_skin")
        t = "thumb_" + side
        g.bone(t, h, [sx * 3.2, 10.9, -0.7])
        g.cube(t, [X(-3.45, 0.5), 9.9, -1.05], [0.5, 1.1, 0.5], "hd_skin")
    # legs
    for side, sx in (("R", -1), ("L", 1)):
        def X(x0, w, sx=sx):
            return x0 if sx < 0 else -(x0 + w)
        t = "thigh_" + side
        g.bone(t, "pelvis", [sx * 1.3, 10.4, 0])
        g.cube(t, [X(-2.35, 2.1), 5.6, -1.05], [2.1, 4.9, 2.1], "pants")
        s = "shin_" + side
        g.bone(s, t, [sx * 1.3, 5.8, 0])
        g.cube(s, [X(-2.25, 1.9), 1.2, -0.95], [1.9, 4.7, 1.9], "pants")
        ft = "foot_" + side
        g.bone(ft, s, [sx * 1.3, 1.3, 0])
        g.cube(ft, [X(-2.35, 2.1), 0.0, -2.2], [2.1, 1.3, 3.3], "shoe")
    # tiny glowing eye glints (emissive pass)
    g.cube("head", [-2.0, 22.1, -3.08], [0.5, 0.5, 0.1], "hd_eye", deco={"north": "glow"})
    g.cube("head", [1.4, 22.1, -3.08], [0.5, 0.5, 0.1], "hd_eye", deco={"north": "glow"})
    return g
