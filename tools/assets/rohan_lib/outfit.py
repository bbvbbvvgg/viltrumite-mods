"""Rohan's 3D outfit parts in player-model coordinates (feet y = 0, head pivot y = 24): side-swept hair over the
undercut, the zigzag headband, pen-nib earrings, collar, jacket hem and cuffs, the G-pen (child of rightarm) and the
manuscript page (child of leftarm). Pen and page are toggled by RohanClient."""
from dio_lib.geo import Geo


def build():
    g = Geo("rohan_outfit", density=4)
    g.bone("head", None, [0, 24, 0])
    # hair: swept volume on top, the long side sweep falls toward his left (+X)
    g.cube("head", [-4.3, 30.9, -4.4], [8.6, 1.6, 8.6], "hair", deco={f: "strands" for f in ("north", "south", "east", "west", "up")})
    g.cube("head", [-3.2, 31.8, -4.6], [7.6, 1.3, 6.6], "hair", rotation=[0, 0, -6], pivot=[0, 32, 0], deco={"up": "strands", "north": "strands"})
    g.cube("head", [-1.0, 28.4, -4.65], [5.4, 2.8, 1.1], "hair", rotation=[0, 0, -18], pivot=[2.0, 30.6, -4.2], deco={"north": "strands"})
    g.cube("head", [3.2, 26.6, -4.5], [1.4, 4.4, 1.2], "hair", rotation=[0, 0, -8], pivot=[3.9, 30.6, -4.0], deco={"north": "strands"})
    g.cube("head", [-4.3, 29.6, 2.2], [8.6, 2.0, 2.2], "hair", deco={"south": "strands"})
    # zigzag headband
    g.cube("head", [-4.25, 29.2, -4.25], [8.5, 1.1, 8.5], "band", deco={f: "zigzag" for f in ("north", "south", "east", "west")})
    g.cube("head", [-0.7, 28.9, 4.1], [1.4, 1.7, 0.4], "band")
    # pen-nib earrings
    for x in (-4.45, 4.05):
        g.cube("head", [x, 25.0, -0.3], [0.4, 1.6, 0.6], "gold", deco={"west": "nib", "east": "nib"})
        g.cube("head", [x + 0.05, 26.5, -0.15], [0.3, 0.4, 0.3], "gold")
    g.bone("body", None, [0, 24, 0])
    # high collar and jacket hem with gold trim
    g.cube("body", [-4.35, 22.6, -2.5], [8.7, 1.9, 5.0], "jacket", deco={"north": "stitch"})
    g.cube("body", [-4.45, 23.6, 1.2], [8.9, 2.2, 1.5], "jacket", rotation=[-12, 0, 0], pivot=[0, 23.6, 2.0])
    g.cube("body", [-4.3, 15.0, -2.35], [2.2, 0.9, 4.7], "gold")
    g.cube("body", [2.1, 15.0, -2.35], [2.2, 0.9, 4.7], "gold")
    g.cube("body", [-4.3, 15.9, 1.95], [8.6, 0.8, 0.4], "gold")
    g.cube("body", [-4.25, 11.9, -2.3], [8.5, 0.9, 4.6], "shoe")
    g.cube("body", [-0.7, 11.8, -2.5], [1.4, 1.1, 0.3], "gold")
    for nm, x in (("rightarm", -6.0), ("leftarm", 6.0)):
        g.bone(nm, None, [-5 if x < 0 else 5, 22, 0])
        g.cube(nm, [x - 2.3, 13.6, -2.3], [4.6, 0.9, 4.6], "gold")
        g.cube(nm, [x - 2.25, 21.6, -2.25], [4.5, 2.6, 4.5], "jacket", deco={"up": "stitch"})
    # the G-pen in the right hand (points along the arm, nib out)
    g.bone("pen", "rightarm", [-6.0, 12.0, -1.0])
    g.cube("pen", [-6.3, 9.2, -1.9], [0.6, 4.4, 0.6], "pen", rotation=[-28, 0, 0], pivot=[-6.0, 12.0, -1.6])
    g.cube("pen", [-6.2, 8.3, -2.3], [0.4, 1.0, 0.4], "nib", rotation=[-28, 0, 0], pivot=[-6.0, 12.0, -1.6], deco={"north": "nib"})
    # the manuscript page in the left hand, perpendicular to the arm so it faces forward when the arm is raised
    g.bone("page", "leftarm", [6.0, 12.0, 0])
    g.cube("page", [3.3, 10.6, -6.0], [5.4, 0.2, 7.0], "paper", deco={"down": "panels", "up": "panels"})
    for nm, x in (("rightleg", -1.9), ("leftleg", 1.9)):
        g.bone(nm, None, [x, 12, 0])
        g.cube(nm, [x - 2.2, 0.0, -2.4], [4.4, 1.4, 4.8], "shoe")
    return g
