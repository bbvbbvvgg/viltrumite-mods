"""SCP-106 clips for viltrumitecore (own work, CC0): see Scp106Renderer for how they are layered.

The rig is the SCP-096 one (same bone names), so the pose algebra and the foot IK of scp096_anims are reused on
the SCP-106 geo. Conventions: spine/head rx > 0 bends forward, jaw rx > 0 opens, arm/thigh rx < 0 swings forward,
forearm rx < 0 flexes the elbow, shin rx > 0 flexes the knee, right arm rz > 0 lifts it outward; root 'pos' y is
in model pixels (the emerge/sink/capture clips push the whole body under the floor, which hides it by depth).

    python3 tools/assets/scp106_anims.py [--preview]
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
import scp096_anims as A  # noqa: E402
import scp106_model as sm  # noqa: E402
from jinwoo_geo import write_anims, RES, ROOT  # noqa: E402

ANIM_REL = 'animations/scp106/scp106.animation.json'
PREVIEW = os.path.join(ROOT, 'build', 'scp106_preview')

with open(os.path.join(RES, sm.GEO_REL)) as fh:
    A.GEO = json.load(fh)
A.BONES = sm.all_bones()
A.sm = sm
P, full, copy, add, spine, set_rot, grounded, curve = A.P, A.full, A.copy, A.add, A.spine, A.set_rot, A.grounded, A.curve
UNDER = -40.0


def hands(d, curl=18.0, tip=26.0, thumb=10.0):
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        d.setdefault('fingers_' + side, (0, 0, s * curl))
        d.setdefault('tips_' + side, (0, 0, s * tip))
        d.setdefault('thumb_' + side, (0, 0, s * thumb))
    return d


def stance(t=0.0, tall=0.0):
    """The hunched old man: bent back, head pushed forward, long arms hanging in front, knees bent."""
    w = math.sin(2 * math.pi * t)
    c = math.cos(2 * math.pi * t)
    d = hands({
        'pelvis': (6, 0, 0.6 * w), 'belly': (11 - 4 * tall + 1.2 * w, 0, 0), 'chest': (17 - 6 * tall + 1.8 * w, 1.0 * c, 0),
        'neck': (20 - 4 * tall, 0, 0), 'head': (-34 + 10 * tall - 1.5 * w, 4 * c, 5 * math.sin(2 * math.pi * t + 0.7)),
        'jaw': (5 + 2 * (0.5 + 0.5 * w), 0, 0),
        'arm_r': (-10 + 2 * w, 0, 7), 'forearm_r': (-16 - 3 * c, 0, 0), 'hand_r': (-8, 0, 0),
        'arm_l': (-12 - 2 * w, 0, -7), 'forearm_l': (-18 + 3 * c, 0, 0), 'hand_l': (-8, 0, 0),
    }, curl=18 + 4 * w, tip=26 + 6 * c)
    p = full(P(d))
    p['root']['pos'] = [0.0, -1.6, 0.0]
    for side, x, z in (('r', -1.9, -0.9), ('l', 1.9, 0.5)):
        A.plant_foot(p, side, (x, 1.2, z), yaw=8)
    return grounded(p)


# ---- walk: a slow, heavy limp; the right foot drags a little ------------------------------------------------
WALK_SHIN = [(0, 12), (0.15, 20), (0.4, 14), (0.55, 24), (0.7, 46), (0.85, 34), (1.0, 12)]
WALK_FOOT = [(0, -12), (0.12, 0), (0.42, 6), (0.58, 22), (0.75, 4), (0.9, -10), (1.0, -12)]


def walk_pose(t):
    c = math.cos(2 * math.pi * t)
    sn = math.sin(2 * math.pi * t)
    d = hands({
        'root': (0, 0, 3.0 * math.sin(2 * math.pi * (t - 0.25)) * -1),
        'pelvis': (7, 6 * c, 0), 'belly': (11, -2 * c, 0), 'chest': (17, -5 * c, 2 * sn),
        'neck': (20, 0, 0), 'head': (-33 + 3 * math.cos(4 * math.pi * t), -3 * c, 5 * math.sin(2 * math.pi * t + 0.8)),
        'jaw': (6, 0, 0),
        'arm_r': (-12 + 9 * c, 0, 7), 'forearm_r': (-16 - 6 * math.sin(2 * math.pi * t - 0.7), 0, 0), 'hand_r': (-8, 0, 0),
        'arm_l': (-12 - 9 * c, 0, -7), 'forearm_l': (-16 + 6 * math.sin(2 * math.pi * t - 0.7), 0, 0), 'hand_l': (-8, 0, 0),
    })
    for side, ph in (('r', 0.0), ('l', 0.5)):
        tt = (t + ph) % 1.0
        drag = 0.65 if side == 'r' else 1.0
        d['thigh_' + side] = (-14 - 15 * drag * math.cos(2 * math.pi * tt), 0, -1.5 if side == 'r' else 1.5)
        d['shin_' + side] = (curve(WALK_SHIN, tt) * (0.8 if side == 'r' else 1.0), 0, 0)
        d['foot_' + side] = (curve(WALK_FOOT, tt), 0, 0)
    p = full(P(d, root_pos=(0.45 * sn, 0, 0)))
    return grounded(p)


def stare_pose(t):
    """Stops and stares: a little taller, the head tilted and slowly turning, the grin opening, one hand lifting."""
    w = math.sin(2 * math.pi * t)
    p = stance(t * 0.5, tall=0.6)
    spine(p, head=(-28 + 2 * w, 9 * math.sin(2 * math.pi * t), 18 + 4 * w), jaw=(12 + 4 * w, 0, 0),
          arm_r=(-34 + 3 * w, 0, 14), forearm_r=(-48, 0, 0), hand_r=(-22, 0, 0),
          fingers_r=(0, 0, -6 - 10 * (0.5 + 0.5 * w)), tips_r=(0, 0, -12 - 18 * (0.5 + 0.5 * w)))
    return p


def phase_pose(t):
    """Pressing through a wall: leaning into it, arms ahead sunk into the surface, head down."""
    w = math.sin(2 * math.pi * t)
    d = hands({
        'pelvis': (10, 0, 0), 'belly': (14, 0, 0), 'chest': (16 + 2 * w, 3 * w, 0), 'neck': (16, 0, 0), 'head': (-18 + 3 * w, 0, 0),
        'jaw': (10, 0, 0),
        'arm_r': (-78 + 6 * w, 0, 8), 'forearm_r': (-12, 0, 0), 'hand_r': (-30, 0, 0),
        'arm_l': (-74 - 6 * w, 0, -8), 'forearm_l': (-14, 0, 0), 'hand_l': (-30, 0, 0),
    }, curl=10, tip=14)
    for side, ph in (('r', 0.0), ('l', 0.5)):
        tt = (t + ph) % 1.0
        d['thigh_' + side] = (-18 - 10 * math.cos(2 * math.pi * tt), 0, 0)
        d['shin_' + side] = (curve(WALK_SHIN, tt), 0, 0)
        d['foot_' + side] = (curve(WALK_FOOT, tt), 0, 0)
    return grounded(full(P(d)))


def under(p, y):
    """The same pose pushed down into the floor by y model pixels (root position)."""
    q = copy(full(p))
    q['root']['pos'][1] += y
    return q


def reach_up(depth, spread=0.0):
    """Arms up out of the floor, the body below: hands clawing at the surface."""
    p = stance(0.0)
    spine(p, chest=(4, 0, 0), neck=(6, 0, 0), head=(-12, 0, 0), jaw=(18, 0, 0),
          arm_r=(-160, 0, -22 - spread), forearm_r=(-20, 0, 0), hand_r=(10, 0, 0),
          arm_l=(-155, 0, 22 + spread), forearm_l=(-26, 0, 0), hand_l=(10, 0, 0),
          fingers_r=(0, 0, -40), tips_r=(0, 0, -55), fingers_l=(0, 0, 40), tips_l=(0, 0, 55))
    return under(p, depth)


def push_up(depth):
    """Hands flat on the floor beside it, heaving the torso out."""
    p = stance(0.0)
    spine(p, belly=(18, 0, 0), chest=(22, 0, 0), neck=(14, 0, 0), head=(-36, 0, 0), jaw=(14, 0, 0),
          arm_r=(-28, 0, 46), forearm_r=(-4, 0, 0), hand_r=(-60, 0, 0),
          arm_l=(-26, 0, -46), forearm_l=(-6, 0, 0), hand_l=(-60, 0, 0),
          fingers_r=(0, 0, -6), tips_r=(0, 0, -8), fingers_l=(0, 0, 6), tips_l=(0, 0, 8))
    return under(p, depth)


def emerge_keys():
    s = stance(0.0)
    return [(0.0, reach_up(UNDER - 6)), (0.5, reach_up(-31, 6)), (1.0, reach_up(-27, 12)), (1.5, push_up(-19)),
            (2.1, push_up(-11)), (2.7, under(spine(copy(s), chest=(26, 0, 0), head=(-40, 0, 0)), -4)), (3.2, under(s, -0.6)), (3.5, s)]


def sink_keys():
    s = stance(0.0)
    bow = spine(copy(s), belly=(16, 0, 0), chest=(22, 0, 0), head=(-26, 0, 0), jaw=(16, 0, 0))
    return [(0.0, s), (0.35, under(bow, -2)), (0.9, push_up(-12)), (1.5, reach_up(-24, 10)), (1.95, reach_up(-33, 4)),
            (2.25, reach_up(UNDER - 6))]


def grab(p, reach, pull, jaw):
    """Both arms reaching for the prey at chest height (reach 1) and hauling it in (pull 1)."""
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        set_rot(p, 'arm_' + side, (-82 * reach + 20 * pull, s * -8 * pull, -s * (6 + 10 * reach)))
        set_rot(p, 'forearm_' + side, (-10 - 60 * pull, 0, 0))
        set_rot(p, 'hand_' + side, (-10, 0, 0))
        set_rot(p, 'fingers_' + side, (0, 0, s * (12 + 40 * pull)))
        set_rot(p, 'tips_' + side, (0, 0, s * (16 + 55 * pull)))
    set_rot(p, 'jaw', (jaw, 0, 0))
    return p


def capture_keys():
    s = stance(0.0)
    lunge = grab(spine(copy(s), belly=(16, 0, 0), chest=(22, 0, 0), head=(-38, 0, 0)), 1.0, 0.0, 34)
    hold = grab(spine(copy(s), belly=(10, 0, 0), chest=(12, 0, 0), head=(-30, 0, 0)), 1.0, 0.7, 26)
    haul = grab(spine(copy(s), belly=(4, 0, 0), chest=(6, 0, 0), head=(-22, 8, 10)), 0.9, 1.0, 40)
    return [(0.0, s), (0.3, lunge), (0.7, hold), (1.3, haul), (1.8, under(haul, -6)), (2.3, under(haul, -20)),
            (2.7, reach_up(-32, 0)), (3.0, reach_up(UNDER - 6))]


def swipe_keys():
    s = stance(0.0)
    up = A.upper_only
    wind = spine(copy(s), chest=(10, -24, 0), head=(-30, 10, 0), arm_r=(-70, 0, 62), forearm_r=(-40, 0, 0), hand_r=(10, 0, 0),
                 fingers_r=(0, 0, -6), tips_r=(0, 0, -10), jaw=(22, 0, 0))
    hit = spine(copy(s), chest=(20, 26, 0), head=(-34, -12, 0), arm_r=(-62, 40, -26), forearm_r=(-12, 0, 0), hand_r=(-20, 0, 0),
                fingers_r=(0, 0, -30), tips_r=(0, 0, -45), jaw=(26, 0, 0))
    follow = spine(copy(hit), chest=(18, 18, 0), arm_r=(-40, 30, -14))
    return [(0.0, up(s)), (0.35, up(wind)), (0.55, up(hit)), (0.75, up(follow)), (1.2, up(s))]


def build_clips():
    clips = {
        'idle': A.cycle(stance, 8, 4.0, 'idle'),
        'walk': A.cycle(walk_pose, 8, 1.0, 'walk'),
        'stare': A.cycle(stare_pose, 8, 3.0, 'stare'),
        'phase': A.cycle(phase_pose, 8, 2.0, 'phase'),
        'emerge': A.clip_from('emerge', 3.5, emerge_keys()),
        'sink': A.clip_from('sink', 2.25, sink_keys()),
        'capture': A.clip_from('capture', 3.0, capture_keys()),
    }
    upper = set(A.upper_only({}).keys())
    clips['swipe'] = A.clip_from('swipe', 1.2, swipe_keys(), base=False, bones=upper)
    clips['swipe'].keep = upper
    for c in clips.values():
        A.tidy(c)
    return clips


def main():
    clips = build_clips()
    size = write_anims(ANIM_REL, list(clips.values()))
    print('clips', ', '.join(clips), 'bytes', size)
    if '--preview' in sys.argv:
        from PIL import Image
        import chitauri_lib as cl
        tex = os.path.join(PREVIEW, 'tex.png')
        glow = os.path.join(PREVIEW, 'glow.png')
        shots = [('idle', stance(0.25)), ('walk_0', walk_pose(0.0)), ('walk_25', walk_pose(0.25)), ('stare', stare_pose(0.25)),
                 ('phase', phase_pose(0.0)), ('emerge_reach', reach_up(-27, 12)), ('emerge_push', push_up(-11)),
                 ('capture_lunge', capture_keys()[1][1]), ('capture_haul', capture_keys()[3][1]), ('swipe', add(stance(0.0), swipe_keys()[2][1]))]
        paths = []
        for name, pose in shots:
            out = os.path.join(PREVIEW, 'pose_%s.png' % name)
            cl.preview(out, [(A.GEO, pose, tex, glow)], views=('front', 'side'), scale=5)
            paths.append(out)
        cl.sheet(os.path.join(PREVIEW, 'poses.png'), paths, cols=5)


if __name__ == '__main__':
    main()
