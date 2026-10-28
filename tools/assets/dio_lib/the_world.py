"""The World: layered gold armour over a dark undersuit, teal enamel accents.

Rig (bedrock, front = north, right = -X):
root > pelvis > waist > chest > neck > head (+hoses)
chest > shoulder_R > arm_R > forearm_R > hand_R > finger_{index,middle,ring,pinky}_R(_2), thumb_R(_2)
pelvis > thigh_R > shin_R > foot_R  (+ mirrored _L)
Feet stand at y = 0, crest top ~ y = 38.
"""
from .geo import Geo

FINGERS = (("index", -0.95), ("middle", -0.32), ("ring", 0.31), ("pinky", 0.92))


def build():
    g = Geo("the_world", density=2)
    g.bone("root", None, [0, 0, 0])
    # ---------------- pelvis ----------------
    g.bone("pelvis", "root", [0, 16, 0])
    g.cube("pelvis", [-4, 14.4, -2.3], [8, 3.6, 4.6], "navy")
    g.cube("pelvis", [-4.35, 16.2, -2.65], [8.7, 1.5, 5.3], "gold", deco={"north": "lines", "south": "lines"})
    g.cube("pelvis", [-0.9, 16.3, -2.95], [1.8, 1.3, 0.5], "teal", deco={"north": "gem"})
    g.cube("pelvis", [-1.7, 13.4, -2.85], [3.4, 3.0, 1.0], "gold_hi", rotation=[8, 0, 0], pivot=[0, 16.2, -2.4], deco={"north": "diamond"})
    g.cube("pelvis", [-4.9, 13.0, -2.3], [1.1, 3.8, 4.6], "gold", rotation=[0, 0, -7], pivot=[-4.4, 16.6, 0], deco={"west": "lines", "east": "lines"})
    g.cube("pelvis", [3.8, 13.0, -2.3], [1.1, 3.8, 4.6], "gold", rotation=[0, 0, 7], pivot=[4.4, 16.6, 0], deco={"west": "lines", "east": "lines"})
    g.cube("pelvis", [-3.6, 13.8, 1.9], [7.2, 3.0, 0.9], "gold_deep", deco={"south": "vlines"})
    # ---------------- waist ----------------
    g.bone("waist", "pelvis", [0, 17.6, 0])
    g.cube("waist", [-3.3, 17.5, -1.95], [6.6, 5.6, 3.9], "navy")
    for row in range(3):
        y = 17.9 + row * 1.65
        g.cube("waist", [-2.45, y, -2.45], [2.2 - row * 0.1, 1.45, 0.7], "gold_hi")
        g.cube("waist", [0.25 + row * 0.1, y, -2.45], [2.2 - row * 0.1, 1.45, 0.7], "gold_hi")
    g.cube("waist", [-0.22, 17.8, -2.55], [0.44, 5.0, 0.5], "teal")
    g.cube("waist", [-3.75, 17.7, -1.7], [1.1, 5.0, 3.4], "gold_deep", rotation=[0, 0, -4], pivot=[-3.2, 20, 0], deco={"west": "lines"})
    g.cube("waist", [2.65, 17.7, -1.7], [1.1, 5.0, 3.4], "gold_deep", rotation=[0, 0, 4], pivot=[3.2, 20, 0], deco={"east": "lines"})
    g.cube("waist", [-3.1, 17.8, 1.55], [6.2, 5.0, 0.8], "gold", deco={"south": "lines"})
    # ---------------- chest ----------------
    g.bone("chest", "waist", [0, 22.6, 0])
    g.cube("chest", [-4.5, 22.6, -2.6], [9, 7.0, 5.2], "navy")
    g.cube("chest", [-4.5, 25.0, -3.45], [4.3, 4.3, 1.3], "gold_hi", rotation=[-6, 10, 0], pivot=[-2.2, 27, -2.8], deco={"north": "rivets"})
    g.cube("chest", [0.2, 25.0, -3.45], [4.3, 4.3, 1.3], "gold_hi", rotation=[-6, -10, 0], pivot=[2.2, 27, -2.8], deco={"north": "rivets"})
    g.cube("chest", [-0.55, 22.9, -3.4], [1.1, 6.4, 0.9], "teal", deco={"north": "stripe_v"})
    g.cube("chest", [-0.9, 26.4, -3.75], [1.8, 1.8, 0.6], "teal", rotation=[0, 0, 45], pivot=[0, 27.3, -3.4], deco={"north": "gem"})
    g.cube("chest", [-4.85, 22.7, -2.8], [1.5, 3.4, 4.6], "gold_deep", deco={"west": "lines"})
    g.cube("chest", [3.35, 22.7, -2.8], [1.5, 3.4, 4.6], "gold_deep", deco={"east": "lines"})
    g.cube("chest", [-4.0, 22.8, -3.15], [3.4, 2.1, 0.8], "gold", deco={"north": "lines"})
    g.cube("chest", [0.6, 22.8, -3.15], [3.4, 2.1, 0.8], "gold", deco={"north": "lines"})
    g.cube("chest", [-3.3, 29.0, -2.5], [6.6, 1.4, 5.0], "gold", deco={"north": "edge_teal"})
    g.cube("chest", [-4.6, 28.4, -1.9], [3.2, 1.6, 3.9], "gold_deep", rotation=[0, 0, -16], pivot=[-1.6, 29.2, 0])
    g.cube("chest", [1.4, 28.4, -1.9], [3.2, 1.6, 3.9], "gold_deep", rotation=[0, 0, 16], pivot=[1.6, 29.2, 0])
    g.cube("chest", [-4.7, 23.0, 1.85], [9.4, 6.6, 1.2], "gold", deco={"south": "lines"})
    g.cube("chest", [-0.65, 22.8, 2.85], [1.3, 7.0, 0.8], "teal", deco={"south": "stripe_v"})
    g.cube("chest", [-2.6, 27.1, 2.85], [1.4, 1.4, 1.0], "teal_dark", deco={"south": "gem"})
    g.cube("chest", [1.2, 27.1, 2.85], [1.4, 1.4, 1.0], "teal_dark", deco={"south": "gem"})
    # ---------------- neck / head ----------------
    g.bone("neck", "chest", [0, 29.6, 0])
    g.cube("neck", [-1.35, 29.5, -1.3], [2.7, 2.7, 2.6], "navy")
    g.cube("neck", [-1.65, 30.0, -1.55], [3.3, 0.6, 3.1], "gold")
    g.cube("neck", [-1.55, 31.0, -1.45], [3.1, 0.5, 2.9], "gold_deep")
    g.bone("head", "neck", [0, 31.7, -0.2])
    g.cube("head", [-2.3, 31.7, -2.6], [4.6, 5.0, 4.7], "gold_face", deco={"north": "face"})
    g.cube("head", [-1.85, 31.25, -2.85], [3.7, 1.5, 1.5], "gold", rotation=[10, 0, 0], pivot=[0, 32, -2.2], deco={"north": "mouth"})
    g.cube("head", [-2.55, 35.5, -2.8], [5.1, 1.4, 5.2], "gold_hi", deco={"north": "edge_teal"})
    g.cube("head", [-2.5, 34.15, -2.95], [5.0, 0.9, 0.7], "gold", deco={"north": "lines"})
    g.cube("head", [-0.5, 35.6, -3.15], [1.0, 2.5, 5.6], "gold_hi", rotation=[-10, 0, 0], pivot=[0, 36.5, 0])
    g.cube("head", [-0.28, 36.9, -3.35], [0.56, 0.9, 1.6], "teal", rotation=[-25, 0, 0], pivot=[0, 37.2, -2.7])
    g.cube("head", [-2.95, 32.2, -2.1], [0.8, 3.0, 3.7], "teal", deco={"west": "diamond", "east": "diamond"})
    g.cube("head", [2.15, 32.2, -2.1], [0.8, 3.0, 3.7], "teal", deco={"west": "diamond", "east": "diamond"})
    g.cube("head", [-2.45, 32.0, 1.75], [4.9, 3.9, 1.0], "gold", deco={"south": "lines"})
    g.cube("head", [-1.8, 33.45, -2.72], [1.2, 0.45, 0.2], "eye")
    g.cube("head", [0.6, 33.45, -2.72], [1.2, 0.45, 0.2], "eye")
    for side, x in (("R", -1.9), ("L", 1.0)):
        g.bone("hose_" + side, "head", [x + 0.45, 33.0, 2.4])
        g.cube("hose_" + side, [x, 32.4, 2.2], [0.9, 0.9, 1.6], "teal_dark")
        g.cube("hose_" + side, [x - 0.05, 30.6, 3.2], [1.0, 2.0, 0.9], "teal_dark", deco={"south": "grip"})
        g.cube("hose_" + side, [x - 0.15, 32.25, 1.95], [1.2, 1.2, 0.5], "gold")
    # ---------------- arms ----------------
    for side, sx in (("R", -1), ("L", 1)):
        def X(x0, w, sx=sx):
            return x0 if sx < 0 else -(x0 + w)

        g.bone("shoulder_" + side, "chest", [sx * 4.4, 28.4, 0])
        g.bone("arm_" + side, "shoulder_" + side, [sx * 5.6, 27.6, 0])
        a = "arm_" + side
        g.cube(a, [X(-7.25, 3.3), 24.9, -1.65], [3.3, 3.4, 3.3], "navy")
        g.cube(a, [X(-8.1, 3.9), 26.3, -2.25], [3.9, 2.7, 4.5], "gold_hi", rotation=[0, 0, sx * -12], pivot=[sx * 5.6, 27.6, 0], deco={"up": "rivets", "north": "edge_teal", "south": "edge_teal"})
        g.cube(a, [X(-8.45, 1.0), 25.5, -2.35], [1.0, 1.4, 4.7], "teal", rotation=[0, 0, sx * -12], pivot=[sx * 5.6, 27.6, 0], deco={"west": "stripe_h", "east": "stripe_h"})
        g.cube(a, [X(-7.0, 2.8), 22.8, -1.4], [2.8, 4.8, 2.8], "navy")
        g.cube(a, [X(-7.15, 3.1), 23.3, -1.85], [3.1, 3.4, 0.9], "gold")
        g.cube(a, [X(-7.15, 3.1), 23.5, 0.95], [3.1, 3.0, 0.9], "gold_deep", deco={"south": "lines"})
        g.cube(a, [X(-7.3, 3.4), 24.15, -1.7], [3.4, 0.55, 3.4], "teal")
        f = "forearm_" + side
        g.bone(f, a, [sx * 5.6, 22.9, 0])
        g.cube(f, [X(-7.1, 2.9), 21.9, -0.35], [2.9, 1.5, 1.9], "gold_hi", rotation=[-20, 0, 0], pivot=[sx * 5.6, 22.6, 0.6])
        g.cube(f, [X(-6.9, 2.6), 18.6, -1.3], [2.6, 4.4, 2.6], "navy")
        g.cube(f, [X(-7.25, 3.3), 18.3, -1.65], [3.3, 3.2, 3.3], "gold", deco={"north": "lines", "south": "lines"})
        g.cube(f, [X(-7.4, 3.6), 18.1, -1.8], [3.6, 0.7, 3.6], "teal", deco={"north": "stripe_h"})
        g.cube(f, [X(-7.5, 0.7), 19.0, -0.9], [0.7, 2.2, 1.8], "gold_deep", deco={"west": "diamond", "east": "diamond"})
        h = "hand_" + side
        g.bone(h, f, [sx * 5.6, 18.2, 0])
        g.cube(h, [X(-6.45, 1.7), 16.3, -1.25], [1.7, 2.0, 2.5], "gold_face")
        g.cube(h, [X(-6.75, 0.5), 16.45, -1.15], [0.5, 1.7, 2.3], "gold", deco={"west": "diamond", "east": "diamond"})
        for fname, z in FINGERS:
            b1 = "finger_%s_%s" % (fname, side)
            b2 = b1 + "_2"
            ln = 1.05 if fname != "pinky" else 0.85
            g.bone(b1, h, [sx * 5.65, 16.35, z])
            g.cube(b1, [X(-6.15, 1.0), 16.35 - ln, z - 0.28], [1.0, ln, 0.56], "gold")
            g.bone(b2, b1, [sx * 5.65, 16.35 - ln, z])
            g.cube(b2, [X(-6.1, 0.9), 16.35 - ln - 0.9, z - 0.26], [0.9, 0.9, 0.52], "gold_face")
        g.bone("thumb_" + side, h, [sx * 5.05, 17.6, -1.05])
        g.cube("thumb_" + side, [X(-5.45, 0.8), 16.5, -1.55], [0.8, 1.1, 0.75], "gold")
        g.bone("thumb_%s_2" % side, "thumb_" + side, [sx * 5.05, 16.5, -1.2])
        g.cube("thumb_%s_2" % side, [X(-5.4, 0.7), 15.65, -1.5], [0.7, 0.9, 0.65], "gold_face")
    # ---------------- legs ----------------
    for side, sx in (("R", -1), ("L", 1)):
        def X(x0, w, sx=sx):
            return x0 if sx < 0 else -(x0 + w)

        t = "thigh_" + side
        g.bone(t, "pelvis", [sx * 2.2, 15.4, 0])
        g.cube(t, [X(-4.1, 3.8), 8.8, -1.85], [3.8, 6.8, 3.7], "navy")
        g.cube(t, [X(-4.2, 4.0), 9.7, -2.35], [4.0, 5.2, 1.0], "gold", deco={"north": "lines"})
        g.cube(t, [X(-4.55, 0.8), 10.0, -1.6], [0.8, 4.8, 3.2], "gold_deep", deco={"west": "lines", "east": "lines"})
        g.cube(t, [X(-4.3, 4.2), 13.6, -2.05], [4.2, 0.6, 4.1], "teal")
        g.cube(t, [X(-4.0, 3.6), 9.8, 1.5], [3.6, 4.6, 0.7], "gold_deep")
        s = "shin_" + side
        g.bone(s, t, [sx * 2.2, 9.0, 0])
        g.cube(s, [X(-4.0, 3.6), 7.5, -2.85], [3.6, 3.1, 1.4], "gold_hi", deco={"north": "heart"})
        g.cube(s, [X(-3.8, 3.2), 1.6, -1.5], [3.2, 7.4, 3.0], "navy")
        g.cube(s, [X(-4.0, 3.6), 2.0, -2.15], [3.6, 5.5, 1.0], "gold", deco={"north": "stripe_v"})
        g.cube(s, [X(-3.95, 3.5), 3.4, 0.9], [3.5, 4.6, 1.1], "gold_deep", deco={"south": "lines"})
        g.cube(s, [X(-4.25, 0.6), 2.4, -1.4], [0.6, 4.6, 2.8], "gold", deco={"west": "lines", "east": "lines"})
        ft = "foot_" + side
        g.bone(ft, s, [sx * 2.2, 1.8, 0])
        g.cube(ft, [X(-4.0, 3.6), 1.0, -1.75], [3.6, 1.15, 3.5], "teal")
        g.cube(ft, [X(-3.9, 3.4), 0.0, -4.2], [3.4, 1.4, 5.6], "gold")
        g.cube(ft, [X(-3.8, 3.2), 0.0, -4.65], [3.2, 1.1, 0.9], "gold_deep")
        g.cube(ft, [X(-3.7, 3.0), 0.0, 1.1], [3.0, 1.6, 0.8], "gold")
    _heroic(g)
    return g


LEG_BONES = ("thigh_", "shin_", "foot_")
LEG_TOP = 15.4
LEG_GAIN = 2.0


def _heroic(g):
    """Longer legs (+2 units) and broader, thicker limbs for a heroic silhouette."""
    k = (LEG_TOP + LEG_GAIN) / LEG_TOP

    def fy(y, leg):
        return y * k if leg else y + LEG_GAIN

    for b in g.bones:
        if b.name == "root":
            continue
        leg = b.name.startswith(LEG_BONES)
        b.pivot[1] = fy(b.pivot[1], leg)
        for c in b.cubes:
            y0, y1 = fy(c.origin[1], leg), fy(c.origin[1] + c.size[1], leg)
            c.origin[1], c.size[1] = y0, y1 - y0
            if c.pivot is not None:
                c.pivot = [c.pivot[0], fy(c.pivot[1], leg), c.pivot[2]]
            if b.name.startswith(("arm_", "thigh_", "forearm_")) and c.mat == "navy":
                c.inflate = 0.25
