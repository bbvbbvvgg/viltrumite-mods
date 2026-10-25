#!/usr/bin/env python3
"""Reinhard quality-rework clips, evaluated only by the existing anim core.

Run after rig_reinhard.py. Every timestamp below is authored in server ticks.
Rotation uses the project's ModelPart degree convention. Position is Bedrock px.
Knight combat leaves the articulated left arm behind his back. Stances and
locomotion loop; transient clips use explicit anticipation/contact/recovery.
Run: python3 tools/assets/make_reinhard_clips.py
"""
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(REPO, "viltrumitecore", "src", "main", "resources", "assets", "viltrumitecore",
                   "animations", "reinhard", "reinhard.animation.json")
BREATH = 3.0
ZERO = [0, 0, 0]


def breathe(rest, peak):
    """Loop rest -> peak -> rest over BREATH seconds."""
    return {"0.0": rest, str(BREATH / 2): peak, str(BREATH): rest}


def still(v):
    return {"0.0": v, str(BREATH): v}


def loop(bones, length=BREATH):
    return {"loop": True, "animation_length": length, "bones": bones}


KNIGHT_LEFT_ARM = {
    "rotation": breathe([25, 0, -10], [26, 0, -10]),
    "position": still([0, 0, 2]),
}

CLIPS = {
    "idle_restrained": loop({
        "head": {"rotation": breathe([0, 0, 0], [-1.5, 0, 1])},
        "body": {"rotation": still(ZERO)},
        "right_arm": {"rotation": breathe([0, 0, 3], [-1, 0, 5])},
        "left_arm": {"rotation": breathe([0, 0, -3], [-1, 0, -5])},
        "right_leg": {"rotation": still(ZERO)},
        "left_leg": {"rotation": still(ZERO)},
    }),
    "idle_knight": loop({
        "head": {"rotation": breathe([0, 0, 0], [-1, 0, 0])},
        "body": {"rotation": still(ZERO)},
        "right_arm": {"rotation": breathe([-2, 0, 3], [-3, 0, 4.5])},
        "left_arm": KNIGHT_LEFT_ARM,
        "right_leg": {"rotation": still(ZERO)},
        "left_leg": {"rotation": still(ZERO)},
    }),
    "idle_holy": loop({
        "head": {"rotation": breathe([0, 0, 0], [-1, 0, 0])},
        "body": {"rotation": still(ZERO)},
        "right_arm": {"rotation": breathe([0, 0, 3], [-1, 0, 5])},
        "left_arm": {"rotation": breathe([-15, 0, 5], [-16, 0, 5])},
        "right_leg": {"rotation": still(ZERO)},
        "left_leg": {"rotation": still(ZERO)},
    }),
    "knight_left_arm_hold": loop({"left_arm": KNIGHT_LEFT_ARM}),
    "crouch": loop({
        "body": {"rotation": [28.6479, 0, 0], "position": [0, -3.2, 0]},
        "head": {"position": [0, -4.2, 0]},
        "right_arm": {"rotation": [22.9183, 0, 0], "position": [0, -3.2, 0]},
        "left_arm": {"rotation": [22.9183, 0, 0], "position": [0, -3.2, 0]},
        "right_leg": {"position": [0, -0.2, 4]},
        "left_leg": {"position": [0, -0.2, 4]},
    }, 1.0),
    "sit": loop({
        "right_arm": {"rotation": [-36, 0, 0]},
        "left_arm": {"rotation": [-36, 0, 0]},
        "right_leg": {"rotation": [-81, 18, 4.5]},
        "left_leg": {"rotation": [-81, -18, -4.5]},
    }, 1.0),
}


def swim_clip():
    # Breaststroke: extension -> lateral catch -> bent-elbow pull -> recovery.
    # Signed rolls never make the old 180/360-degree wrap around the shoulder.
    stroke = [(0,[-165,0,-28]),(5,[-155,0,-55]),(10,[-100,0,-80]),
              (14,[-25,0,35]),(18,[-65,0,12]),(20,[-165,0,-28])]
    elbow = [(0,[-8,0,0]),(5,[-15,0,0]),(10,[-65,0,0]),
             (14,[-95,0,0]),(18,[-60,0,0]),(20,[-8,0,0])]
    bones = {}
    for side, sign in [('right',1),('left',-1)]:
        bones[side+'_arm'] = channel([(t,[v[0],v[1]*sign,v[2]*sign]) for t,v in stroke])
        bones[side+'_forearm'] = channel(elbow)
        bones[side+'_leg'] = channel([(0,[-12,0,4*sign]),(5,[14,0,8*sign]),
                                      (10,[-12,0,4*sign]),(15,[14,0,8*sign]),(20,[-12,0,4*sign])])
    return loop(bones,1.0)


# Quality rework: authored anticipation/contact/recovery, ticks as the source unit.
# Only the existing AnimationParser/AnimationController evaluates these keys.
import math

def keys(rows):
    return {str(round(t / 20, 6)): {'vector': list(v), 'easing': ease}
            for t, v, *style in rows for ease in [style[0] if style else 'easeInOutSine']}

def channel(rows):
    return {'rotation': keys(rows)}

def gait_channel(sample):
    """Bake the original trajectory, not a stop/start ease between its samples.

    Eighty linear intervals keep the 48-degree run arc within 0.04 degrees of
    the continuous cosine. Endpoints match exactly; no spline knee overshoot.
    Combat still uses its authored easing and contact holds.
    """
    return {'rotation': {str(i / 80): sample(i / 80) for i in range(81)}}

def action(length, **bones):
    return {'loop': 'hold_on_last_frame', 'animation_length': length/20,
            'bones': {bone: channel(rows) for bone, rows in bones.items()}}

def pos(clip, bone, rows):
    clip['bones'].setdefault(bone, {})['position'] = keys(rows)

CLIPS['crouch'] = loop({
    'body': {'rotation':[22,0,0], 'position':[0,-3,1]},
    'head': {'rotation':[-8,0,0], 'position':[0,-3,-1]},
    'right_arm': {'rotation':[12,0,6], 'position':[0,-3,0]},
    'left_arm': {'rotation':[12,0,-6], 'position':[0,-3,0]},
    'right_forearm': {'rotation':[-14,0,0]}, 'left_forearm': {'rotation':[-14,0,0]},
    'right_leg': {'rotation':[-12,0,3], 'position':[0,-.4,2]},
    'left_leg': {'rotation':[-12,0,-3], 'position':[0,-.4,2]},
},1.0)
CLIPS['sit'] = loop({
    'body': {'rotation':[3,0,0]},
    'right_arm': {'rotation':[-24,0,8]},'left_arm': {'rotation':[-24,0,-8]},
    'right_forearm': {'rotation':[-42,0,0]},'left_forearm': {'rotation':[-42,0,0]},
    'right_leg': {'rotation':[-82,16,5]},'left_leg': {'rotation':[-82,-16,-5]},
},1.0)
CLIPS['swim'] = swim_clip()
# Rest poses now include real elbows. The Knight's palm rests behind the lower back,
# not next to the thigh. Shoulder sweeps backward; elbow folds inward across the back.
for name in ['idle_restrained','idle_knight','idle_holy']:
    CLIPS[name]['bones']['right_forearm'] = {'rotation': breathe([-7,0,0],[-9,0,0])}
    CLIPS[name]['bones']['left_forearm'] = {'rotation': breathe([-7,0,0],[-9,0,0])}
KNIGHT_LEFT_ARM = {'rotation': breathe([40,20,10],[41,20,10]), 'position': still([0,0,.5])}
CLIPS['idle_knight']['bones']['left_arm'] = KNIGHT_LEFT_ARM
CLIPS['idle_knight']['bones']['left_forearm'] = {'rotation': breathe([-65,60,0],[-66,60,0])}
CLIPS['knight_left_arm_hold'] = loop({
    'left_arm': KNIGHT_LEFT_ARM,
    'left_forearm': CLIPS['idle_knight']['bones']['left_forearm']})
CLIPS['idle_knight']['bones']['body'] = {'rotation': breathe([0,-7,0],[.6,-7,0])}
CLIPS['idle_knight']['bones']['right_arm'] = {'rotation': breathe([-16,-12,6],[-17,-12,6])}
CLIPS['idle_knight']['bones']['right_forearm'] = {'rotation': breathe([-24,0,0],[-26,0,0])}
CLIPS['idle_holy']['bones']['right_arm'] = {'rotation': breathe([-30,24,6],[-31,24,6])}
CLIPS['idle_holy']['bones']['right_forearm'] = {'rotation': still([-38,-12,0])}
CLIPS['idle_holy']['bones']['left_arm'] = {'rotation': breathe([-18,0,-8],[-19,0,-8])}
# Continuous gait, feet oppose the hands. Small elbow articulation, not rigid poles.
for name, amplitude, arm_amplitude in [('walk',32,31.512679),('run',48,50)]:
    bones = {}
    for side, phase in [('right',0),('left',math.pi)]:
        sign = -1 if side == 'right' else 1
        bones[side+'_arm'] = gait_channel(lambda t: [-arm_amplitude*math.cos(2*math.pi*t+phase),0,3*sign])
        bones[side+'_leg'] = gait_channel(lambda t: [amplitude*math.cos(2*math.pi*t+phase),0,0])
        bones[side+'_forearm'] = gait_channel(lambda t: [-12-10*max(0,math.cos(2*math.pi*t+phase)),0,0])
    CLIPS[name] = loop(bones,1)

CLIPS['light_punch'] = action(10,
    right_arm=[(0,[-6,0,3]),(2,[-47,-16,7],'easeInCubic'),(4,[-91,8,0],'easeOutCubic'),(5,[-92,8,0]),(10,[-6,0,3])],
    right_forearm=[(0,[-8,0,0]),(2,[-68,0,0]),(4,[-4,0,0]),(5,[-6,0,0]),(10,[-8,0,0])],
    body=[(0,[0,0,0]),(2,[0,-9,0]),(4,[4,17,0]),(5,[3,13,0]),(10,[0,0,0])],
    head=[(0,[0,0,0]),(4,[-3,-10,0]),(10,[0,0,0])],
    left_arm=[(0,[0,0,-3]),(2,[-20,0,-8]),(5,[-16,0,-6]),(10,[0,0,-3])],
    left_forearm=[(0,[-8,0,0]),(4,[-30,0,0]),(10,[-8,0,0])],
    right_leg=[(0,[0,0,0]),(4,[-9,0,2]),(10,[0,0,0])],
    left_leg=[(0,[0,0,0]),(4,[7,0,-2]),(10,[0,0,0])])
pos(CLIPS['light_punch'],'right_arm',[(0,[0,0,0]),(4,[0,0,-1]),(10,[0,0,0])])
CLIPS['step_dash'] = action(6,
    body=[(0,[0,0,0]),(1,[19,0,0]),(3,[22,0,0]),(6,[0,0,0])],
    head=[(0,[0,0,0]),(2,[-16,0,0]),(6,[0,0,0])],
    right_arm=[(0,[0,0,3]),(2,[35,0,12]),(6,[0,0,3])],
    left_arm=[(0,[0,0,-3]),(2,[35,0,-12]),(6,[0,0,-3])],
    right_leg=[(0,[0,0,0]),(2,[-38,0,2]),(6,[0,0,0])],
    left_leg=[(0,[0,0,0]),(2,[32,0,-2]),(6,[0,0,0])])
for n,length in [('catch_ready',10),('reflect_ready',20)]:
    CLIPS[n] = action(length,
        right_arm=[(0,[-16,-12,6]),(2,[-72,-18,10]),(length,[-72,-18,10])],
        right_forearm=[(0,[-24,0,0]),(2,[-40,12,0]),(length,[-40,12,0])],
        body=[(0,[0,-7,0]),(2,[0,-12,0]),(length,[0,-12,0])],
        head=[(0,[0,0,0]),(length,[-3,7,0])])
for n in ['catch','reflect','parry']:
    CLIPS[n] = action(8 if n=='parry' else 6,
        right_arm=[(0,[-72,-18,10]),(1,[-90,30,12],'easeOutCubic'),(3,[-79,20,8]),(6,[-65,0,6])],
        right_forearm=[(0,[-40,12,0]),(1,[-12,-12,0]),(3,[-25,0,0]),(6,[-35,0,0])],
        body=[(0,[0,-12,0]),(1,[0,8,0]),(6,[0,-7,0])])
# All three Knight attacks retain the left-arm/back pose, including when walking.
for n, length, contact, wind, strike, twist in [
    ('combo_1',10,4,[-55,-18,8],[-92,5,0],18),
    ('combo_2',10,4,[-67,58,24],[-75,-42,12],-22),
    ('combo_3',14,6,[-105,14,-35],[-48,-8,6],-15)]:
    CLIPS[n] = action(length,
        right_arm=[(0,[-16,-12,6]),(contact-(2.5 if n=='combo_3' else 2),wind,'easeInCubic'),(contact,strike,'easeOutCubic'),(contact+1,[strike[i]+(strike[i]-wind[i])*.08 for i in range(3)]),(length,[-16,-12,6])],
        right_forearm=[(0,[-24,0,0]),(contact-(2.5 if n=='combo_3' else 2),[-42,0,0]),(contact,[-5,0,0]),(contact+1,[-9,0,0]),(length,[-24,0,0])],
        body=[(0,[0,-7,0]),(contact-(2.5 if n=='combo_3' else 2),[-4,-twist*.6,0]),(contact,[10 if n=='combo_3' else 3,twist,0]),(contact+1,[7,twist*.8,0]),(length,[0,-7,0])],
        head=[(0,[0,0,0]),(contact,[-5,-twist*.5,0]),(length,[0,0,0])],
        right_leg=[(0,[0,0,0]),(contact,[-16,0,3]),(length,[0,0,0])],
        left_leg=[(0,[0,0,0]),(contact,[10,0,-3]),(length,[0,0,0])])
# Continuous toss -> extension. The preceding clip ends at the exact kick pose:
# the final 1.3 ticks deliver the leg, rather than a discontinuity on contact.
CLIPS['kick_toss'] = action(6,
    right_arm=[(0,[-16,-12,6]),(2,[-72,-4,8]),(4,[-40,0,16]),(6,[-20,0,22])],
    right_forearm=[(0,[-24,0,0]),(2,[-42,0,0]),(6,[-14,0,0])],
    right_leg=[(0,[0,0,0]),(3.8,[-12,0,0]),(4.7,[-36,0,2],'easeInCubic'),(6,[-116,0,4],'easeOutCubic')],
    left_leg=[(0,[0,0,0]),(6,[8,0,-2])],
    body=[(0,[0,-7,0]),(4,[-3,-4,0]),(6,[-15,0,0])],
    head=[(0,[0,0,0]),(6,[-20,0,0])])
CLIPS['kick'] = action(10,
    right_leg=[(0,[-116,0,4]),(3,[-116,0,4]),(5,[-58,0,2]),(8,[-8,0,0]),(10,[0,0,0])],
    left_leg=[(0,[8,0,-2]),(3,[8,0,-2]),(10,[0,0,0])],
    right_arm=[(0,[-20,0,22]),(3,[-20,0,22]),(10,[-16,-12,6])],
    right_forearm=[(0,[-14,0,0]),(10,[-24,0,0])],
    body=[(0,[-15,0,0]),(3,[-15,0,0]),(10,[0,-7,0])],
    head=[(0,[-20,0,0]),(4,[-28,0,0]),(10,[0,0,0])])
CLIPS['grab_hold'] = action(6,
    right_arm=[(0,[-16,-12,6]),(2,[-88,-6,2]),(6,[-86,-6,2])],
    right_forearm=[(0,[-24,0,0]),(2,[-5,0,0]),(6,[-8,0,0])],
    body=[(0,[0,-7,0]),(6,[2,-7,0])],
    head=[(0,[0,0,0]),(6,[3,4,0])])
CLIPS['throw'] = action(12,
    right_arm=[(0,[-86,-6,2]),(2,[-100,16,30],'easeInCubic'),(3,[-45,-18,2],'easeOutCubic'),(4,[-28,-22,0]),(12,[-16,-12,6])],
    right_forearm=[(0,[-8,0,0]),(2,[-25,0,0]),(3,[-4,0,0]),(12,[-24,0,0])],
    body=[(0,[2,-7,0]),(2,[-8,18,0]),(3,[16,-22,0]),(5,[12,-16,0]),(12,[0,-7,0])],
    head=[(0,[3,4,0]),(3,[-6,10,0]),(12,[0,0,0])],
    right_leg=[(0,[0,0,0]),(3,[-22,0,3]),(12,[0,0,0])],
    left_leg=[(0,[0,0,0]),(3,[14,0,-3]),(12,[0,0,0])])
CLIPS['dash_behind'] = action(8,
    right_arm=[(0,[-16,-12,6]),(2,[38,0,12]),(4,[-38,-12,6]),(8,[-16,-12,6])],
    body=[(0,[0,-7,0]),(2,[24,-14,0]),(4,[6,10,0]),(8,[0,-7,0])],
    head=[(0,[0,0,0]),(2,[-18,8,0]),(8,[0,0,0])],
    right_leg=[(0,[0,0,0]),(2,[-38,0,0]),(4,[-12,0,0]),(8,[0,0,0])],
    left_leg=[(0,[0,0,0]),(2,[32,0,0]),(4,[10,0,0]),(8,[0,0,0])])
CLIPS['stomp'] = action(14,
    right_leg=[(0,[0,0,0]),(5,[-68,0,4]),(6.5,[-72,0,4],'easeInCubic'),(8,[5,0,0],'easeOutCubic'),(9,[4,0,0]),(14,[0,0,0])],
    left_leg=[(0,[0,0,0]),(6,[8,0,-3]),(8,[-9,0,-3]),(14,[0,0,0])],
    body=[(0,[0,-7,0]),(6,[-6,-7,0]),(8,[18,0,0]),(9,[15,0,0]),(14,[0,-7,0])],
    head=[(0,[0,0,0]),(6,[6,0,0]),(8,[-12,0,0]),(14,[0,0,0])],
    right_arm=[(0,[-16,-12,6]),(6,[-24,0,28]),(8,[8,0,18]),(14,[-16,-12,6])])
pos(CLIPS['stomp'],'body',[(0,[0,0,0]),(6,[0,.6,0]),(8,[0,-.8,0]),(14,[0,0,0])])
CLIPS['idle_holy_drawn'] = loop({
    'right_arm': {'rotation': breathe([-26,-10,6],[-27,-10,6])},
    'right_forearm': {'rotation': breathe([-18,0,0],[-20,0,0])},
    'left_arm': {'rotation': breathe([0,0,-4],[-1,0,-4])},
    'left_forearm': {'rotation': still([-8,0,0])}})
# Draw and sheathe remain 120/20 ticks; right hand crosses to the left hip.
CLIPS['reid_draw'] = action(120,
    right_arm=[(0,[-30,24,6]),(8,[-46,38,12]),(80,[-82,22,10]),(112,[-112,-8,8]),(120,[-26,-10,6])],
    right_forearm=[(0,[-38,-12,0]),(8,[-58,-12,0]),(80,[-42,0,0]),(112,[-16,0,0]),(120,[-18,0,0])],
    left_arm=[(0,[-18,0,-8]),(8,[-22,-10,-8]),(112,[-22,-10,-8]),(120,[0,0,-4])],
    left_forearm=[(0,[-8,0,0]),(8,[-32,0,0]),(112,[-32,0,0]),(120,[-8,0,0])],
    body=[(0,[0,0,0]),(8,[0,-8,0]),(100,[0,-6,0]),(120,[0,0,0])],
    head=[(0,[0,0,0]),(20,[6,0,0]),(100,[-4,0,0]),(120,[0,0,0])])
CLIPS['reid_sheathe'] = action(20,
    right_arm=[(0,[-26,-10,6]),(4,[-112,-8,8]),(16,[-46,38,12]),(20,[-30,24,6])],
    right_forearm=[(0,[-18,0,0]),(4,[-16,0,0]),(16,[-58,-12,0]),(20,[-38,-12,0])],
    left_arm=[(0,[0,0,-4]),(4,[-22,-10,-8]),(16,[-22,-10,-8]),(20,[-18,0,-8])],
    body=[(0,[0,0,0]),(4,[0,-6,0]),(16,[0,-8,0]),(20,[0,0,0])])
for side in ['right','left']:
    m = 1 if side=='right' else -1
    for prefix,length,contact in [('chop',20,7),('melee',6,2.5)]:
        CLIPS[prefix+'_'+side] = action(length, **{
            side+'_arm': [(0,[-16,0,3*m]),(contact-1,[-110,15*m,-35*m],'easeInCubic'),(contact,[-50,-30*m,-8*m],'easeOutCubic'),(contact+1,[-38,-42*m,-10*m]),(length,[-16,0,3*m])],
            side+'_forearm': [(0,[-12,0,0]),(contact-1,[-22,0,0]),(contact,[-5,0,0]),(length,[-12,0,0])],
            'body': [(0,[0,0,0]),(contact-1,[-4,16*m,0]),(contact,[10,-18*m,0]),(length,[0,0,0])],
            'head': [(0,[0,0,0]),(contact,[-6,8*m,0]),(length,[0,0,0])]})
CLIPS['sheath_strike'] = action(12,
    right_arm=[(0,[-30,24,6]),(4,[-60,55,18],'easeInCubic'),(6,[-82,-44,-8],'easeOutCubic'),(8,[-55,-50,-8]),(12,[-30,24,6])],
    right_forearm=[(0,[-38,0,0]),(4,[-65,0,0]),(6,[-4,0,0]),(12,[-38,0,0])],
    body=[(0,[0,0,0]),(4,[0,20,0]),(6,[8,-22,0]),(12,[0,0,0])],
    left_arm=[(0,[-18,0,-8]),(4,[-10,0,-14]),(12,[-18,0,-8])])
# Cinematic cuts: a shared server clock, readable preparation and contact at tick 80.
# REINHARD OVERHAUL item 13: the normal World Cut comes down from either shoulder (the action
# phase is hero/reinhard/cut/WorldCutSweep). 'world_cut' is raised over the RIGHT shoulder and
# cuts right to left; 'world_cut_from_left' is its exact mirror (left to right). The chest turns
# toward the raised shoulder and unwinds through the stroke; both reach the contact of tick 80
# square on the trench (no lean, no chest twist), so the carve and the cut line stay the same.
# The blade is raised upright first (tick 20, exactly as before) and only then settles over the
# shoulder (tick 30): the raise keeps the owner-selected edge/face read and a clear arm path.
# Most of the side read is the chest (twist + bend), which carries arms and grip together, so
# the right-hand-on-top grip stays reachable over the left shoulder as well.
SWEEP_TWIST = 12
# Side bend of the chest toward the raised shoulder (degrees); the head stays level and keeps
# most of the twist (it turns back only SWEEP_HEAD), clear of the raised blade.
SWEEP_BEND = 6
SWEEP_HEAD = 4
CLIPS['world_cut'] = action(100,
    body=[(0,[0,0,0]),(20,[-8,0,0]),(30,[-8,SWEEP_TWIST,-SWEEP_BEND]),(66,[-8,SWEEP_TWIST,-SWEEP_BEND]),(80,[16,0,0]),(87,[10,-4,2]),(100,[0,0,0])],
    head=[(0,[0,0,0]),(20,[-8,0,0]),(30,[-8,-SWEEP_HEAD,SWEEP_BEND]),(66,[-8,-SWEEP_HEAD,SWEEP_BEND]),(80,[-8,0,0]),(87,[-6,3,-2]),(100,[0,0,0])],
    right_leg=[(0,[0,0,0]),(20,[-12,0,4]),(80,[-18,0,4]),(100,[0,0,0])],
    left_leg=[(0,[0,0,0]),(20,[-8,0,-4]),(66,[-8,0,-4]),(80,[16,0,-4]),(100,[0,0,0])])
CLIPS['diagonal_world_cut'] = action(100,
    body=[(0,[0,0,0]),(20,[-5,4,0]),(66,[-5,4,0]),(80,[10,-4,0]),(87,[6,-2,0]),(100,[0,0,0])],
    head=[(0,[0,0,0]),(20,[-6,-8,0]),(80,[-6,8,0]),(100,[0,0,0])],
    right_leg=[(0,[0,0,0]),(20,[-10,0,5]),(80,[-15,0,5]),(100,[0,0,0])],
    left_leg=[(0,[0,0,0]),(20,[-5,4,-5]),(66,[-5,4,-5]),(80,[10,-4,-5]),(100,[0,0,0])])
CLIPS['cut_rain'] = action(46,
    body=[(0,[0,0,0]),(4,[-4,0,0])]+[(7+i*3,[4,(-1)**i*7,0]) for i in range(12)]+[(46,[0,0,0])],
    head=[(0,[0,0,0]),(7,[-3,0,0]),(40,[-3,0,0]),(46,[0,0,0])])
CLIPS['world_erasure'] = action(150,
    right_arm=[(0,[-26,-10,6]),(15,[-115,-12,8]),(30,[-115,-12,8],'easeInCubic'),(40,[-45,-52,-8],'easeOutCubic'),(150,[-45,-52,-8])],
    right_forearm=[(0,[-18,0,0]),(15,[-38,0,0]),(30,[-38,0,0]),(40,[-8,0,0]),(150,[-8,0,0])],
    left_arm=[(0,[0,0,-4]),(15,[-58,12,-8]),(30,[-58,12,-8]),(40,[-20,24,-12]),(150,[-20,24,-12])],
    left_forearm=[(0,[-8,0,0]),(15,[-30,0,0]),(40,[-18,0,0]),(150,[-18,0,0])],
    body=[(0,[0,0,0]),(15,[0,10,0]),(30,[0,10,0]),(40,[5,-16,0]),(150,[5,-16,0])],
    head=[(0,[0,0,0]),(30,[-5,-5,0]),(40,[-5,8,0]),(150,[-5,8,0])])

# Second pass: anatomical knees; the hip changes little during the actual thrust.
CLIPS['kick_toss'] = action(6,
    right_leg=[(0,[0,0,0]),(2,[-82,0,3]),(4.8,[-110,0,4]),(6,[-125,0,4],'easeOutCubic')],
    right_shin=[(0,[0,0,0]),(2,[110,0,0]),(4.8,[100,0,0]),(6,[0,0,0],'easeOutCubic')],
    left_leg=[(0,[0,0,0]),(6,[8,0,-2])],
    body=[(0,[0,-7,0]),(4,[-8,0,0]),(6,[-15,0,0])],
    head=[(0,[0,0,0]),(6,[8,0,0])],
    right_arm=[(0,[-16,-12,6]),(4,[-35,0,18]),(6,[-20,0,22])],
    right_forearm=[(0,[-24,0,0]),(6,[-14,0,0])])
CLIPS['kick']['bones']['right_leg']['rotation'] = channel([(0,[-125,0,4]),(1,[-125,0,4]),(4,[-100,0,2]),(8,[-8,0,0]),(10,[0,0,0])])['rotation']
CLIPS['kick']['bones']['right_shin'] = channel([(0,[0,0,0]),(1,[0,0,0]),(4,[100,0,0]),(8,[20,0,0]),(10,[0,0,0])])
CLIPS['crouch'] = action(1, body=[(0,[22,0,0])],head=[(0,[-12,0,0])],
    right_arm=[(0,[-8,0,0])],left_arm=[(0,[-8,0,0])],
    right_leg=[(0,[-18,0,0])],left_leg=[(0,[-18,0,0])],
    right_shin=[(0,[26,0,0])],left_shin=[(0,[26,0,0])],
    right_forearm=[(0,[-14,0,0])],left_forearm=[(0,[-14,0,0])])
CLIPS['crouch']['loop']=True
for name in ['walk','run']:
    for side,phase in [('right',0),('left',math.pi)]:
        CLIPS[name]['bones'][side+'_shin']=gait_channel(lambda t: [max(0,math.sin(2*math.pi*t+phase))*32,0,0])
CLIPS['air_rise']=action(1, right_leg=[(0,[-25,0,4])],left_leg=[(0,[12,0,-4])],
    right_shin=[(0,[38,0,0])],left_shin=[(0,[20,0,0])],body=[(0,[-6,0,0])])
CLIPS['air_fall']=action(1, right_leg=[(0,[-9,0,4])],left_leg=[(0,[-9,0,-4])],
    right_shin=[(0,[16,0,0])],left_shin=[(0,[16,0,0])],body=[(0,[4,0,0])])

CLIPS['idle_holy']['bones']['right_arm']={'rotation':breathe([0,0,3],[-1,0,4])}
CLIPS['idle_holy']['bones']['right_forearm']={'rotation':still([-8,0,0])}

# Draw uses the left hilt hand until the front-of-chest hand-off. The free right
# hand waits below the face, not at the old overhead extraction pose.
CLIPS['reid_draw']['bones']['right_arm']=channel([(0,[-16,-10,8]),(80,[-25,-10,8]),(120,[-26,-10,6])])
CLIPS['reid_draw']['bones']['right_forearm']=channel([(0,[-20,0,0]),(80,[-25,0,0]),(120,[-18,0,0])])
CLIPS['reid_sheathe']['bones']['right_arm']=channel([(0,[-26,-10,6]),(8,[-25,-10,8]),(20,[-16,-10,8])])
CLIPS['reid_sheathe']['bones']['right_forearm']=channel([(0,[-18,0,0]),(8,[-25,0,0]),(20,[-20,0,0])])

# Weapon choreography is sampled by the SAME animation core. The empty grip bone
# owns the complete weapon frame; wrists solve to it, never the reverse. Coordinates
# here are mirrored model px and model Euler degrees (converted to parser convention).
# Sheathed hilt = ReinhardSword.hip(): mouth (6.2,15.8,-1.0) tilted TILT_X back, handle
# centre 3.5 sword px (x .574 x .78) up the sheath axis. Keep in sync with TILT_X (50).
SHEATH_TILT = 50
_HANDLE_PX = 3.5*.574*.78
HIP = ([-6.2,15.8+_HANDLE_PX*math.cos(math.radians(SHEATH_TILT)),-1.0-_HANDLE_PX*math.sin(math.radians(SHEATH_TILT))],
       [90,90-SHEATH_TILT,-90])
READY = ([.5,18.5,-6],[350,0,-50])
def weapon(clip, rows):
    pos(clip,'reid_grip',[(t,[-p[0],p[1],p[2]]) for t,p,r in rows])
    clip['bones'].setdefault('reid_grip',{})['rotation']=keys([(t,[-r[0],-r[1],r[2]]) for t,p,r in rows])
weapon(CLIPS['idle_holy'],[(0,*HIP),(60,*HIP)])
weapon(CLIPS['idle_holy_drawn'],[(0,*READY),(60,*READY)])
# The long blade stays outside the hair envelope. Rotate below the head at X=-8.5,
# with enough ground clearance, then pass the hilt in front of the chest to the right.
DRAW=[(0,*HIP),(24,*HIP),(40,[-9.2,25,-4],HIP[1]),
      (52,[-9.2,25,-4],[90,0,0]),(64,[-9.2,25,-4],[180,0,0]),
      (76,[-6.8,23,-7.5],[270,0,0]),(88,[0,20,-6],[270,0,0]),(94,[0,20,-6],[270,0,0]),
      (100,[5.3,17,-4],[270,0,0]),(120,*READY)]
weapon(CLIPS['reid_draw'],DRAW)
weapon(CLIPS['reid_sheathe'],[(20-t/6,p,r) for t,p,r in reversed(DRAW)])
# Blade lean over the raised shoulder (degrees), at the end of the raise and through the hold,
# and the grip shift toward that shoulder (px).
SWEEP_LEAN = 12
SWEEP_SHIFT = 0.5
WORLD_CUT_WEAPON=[(0,*READY),(20,[0,20,-6.5],[360,0,0]),
    (30,[SWEEP_SHIFT,20,-6.5],[360,0,-SWEEP_LEAN]),
    (66,[SWEEP_SHIFT,20,-6.5],[360,0,-SWEEP_LEAN]),(80,[1,20,-6.5],[260,0,0]),
    (87,[1,20,-6.5],[270,0,0]),(100,*READY)]
weapon(CLIPS['world_cut'],WORLD_CUT_WEAPON)
weapon(CLIPS['diagonal_world_cut'],[(0,*READY),(20,[0,20,-6.5],[360,0,-12]),
    (66,[0,20,-6.5],[360,0,-12]),(80,[1,20,-6.5],[260,0,12]),
    (87,[1,20,-6.5],[270,0,8]),(100,*READY)])
weapon(CLIPS['world_erasure'],[(0,*READY),(15,[0,20,-6.5],[360,0,0]),
    (30,[0,20,-6.5],[360,0,0]),(40,[1,20,-6],[260,0,0]),
    (150,[1,20,-6],[260,0,0])])
weapon(CLIPS['light_punch'],[(0,*READY),(2,[1,20,-6],[275,0,0]),
    (4,[1,20,-6],[270,0,0]),(10,*READY)])
# Preserve the ability's existing timelines, hit events and world effects.
weapon(CLIPS['sheath_strike'],[(0,*HIP),(3,[0,18,-6],[270,0,0]),(4,[4,18,-7],[270,0,0]),
    (7,[5,18,-8],[270,0,0]),(12,*HIP)])
RAIN_WEAPON=[(0,*READY),(4,[0,20,-6.5],[320,0,0])]
for i in range(12):
    t=7+i*3
    sign=(-1)**i
    RAIN_WEAPON.extend([(t-1.5,[0,20,-6.5],[320,0,sign*10]),
                        (t,[1,20,-6.5],[260,0,-sign*10])])
RAIN_WEAPON.append((46,*READY))
weapon(CLIPS['cut_rain'],RAIN_WEAPON)

for side,sign in [('right',1),('left',-1)]:
    weapon(CLIPS['chop_'+side],[(0,*READY),(4,[0,20,-6],[350,0,-sign*30]),
        (7,[1,20,-6],[260,0,sign*12]),(9,[1,20,-6],[270,0,0]),(20,*READY)])

# REINHARD OVERHAUL (combat): ordinary strikes as data. Contact ticks match
# hero/reinhard/melee/MeleeMoves; the blade tip sweeps the same view-relative arc the
# server hit test and the client slash trail use (designed with a forward model of
# the grip chain, verified by the mesh collision tests). Grip rows: render px + model
# Euler degrees, one continuous representation per clip (no 360-degree wrap).
# Right jab: the right shoulder drives forward at contact (+ky turns the chest right).
CLIPS['light_punch']['bones']['body']=channel([(0,[0,0,0]),(2,[0,8,0]),(4,[4,-14,0]),(5,[3,-11,0]),(10,[0,0,0])])
CLIPS['light_punch']['bones']['head']=channel([(0,[0,0,0]),(4,[-3,8,0]),(10,[0,0,0])])

def mirror_rows(rows):
    return [(t,[v[0],-v[1],-v[2]],*style) for t,v,*style in rows]

def mirrored(clip):
    """Left/right mirror of an authored clip (rotation y/z and position x flip, sides swap)."""
    out={'loop':clip['loop'],'animation_length':clip['animation_length'],'bones':{}}
    for bone,data in clip['bones'].items():
        if bone=='reid_grip': continue
        name=bone.replace('right_','@').replace('left_','right_').replace('@','left_')
        target={}
        for ch,keysd in data.items():
            flipped={}
            for time,entry in keysd.items():
                vec=entry['vector'] if isinstance(entry,dict) else entry
                vec=[-vec[0],vec[1],vec[2]] if ch=='position' else [vec[0],-vec[1],-vec[2]]
                flipped[time]=dict(entry,vector=vec) if isinstance(entry,dict) else vec
            target[ch]=flipped
        out['bones'][name]=target
    return out

CLIPS['light_punch_left']=mirrored(CLIPS['light_punch'])
# The left-shoulder World Cut: body, head and legs mirrored; the grip mirrored through the raise
# and the hold. READY (the drawn guard both clips start from and return to) and the contact /
# follow-through rows are shared: both strokes meet the trench at the same square grip, and the
# right hand stays on top without reaching across for the contact.
CLIPS['world_cut_from_left']=mirrored(CLIPS['world_cut'])
weapon(CLIPS['world_cut_from_left'],[(t,p,r) if (p,r)==READY or t>=80 else (t,[-p[0],p[1],p[2]],[r[0],-r[1],-r[2]])
    for t,p,r in WORLD_CUT_WEAPON])
# Right hook: a wide swing from the right (Knight's combo_2 family, the fist passes at
# shoulder height in front of the face, never folded back into it), chest turning left.
CLIPS['light_hook']=action(12,
    right_arm=[(0,[-6,0,3]),(2.5,[-62,52,30],'easeInCubic'),(4,[-78,14,22]),(5,[-80,-26,14]),(6,[-76,-36,12]),(7,[-70,-40,10]),(12,[-6,0,3])],
    right_forearm=[(0,[-8,0,0]),(2.5,[-46,0,0]),(4,[-26,0,0]),(5,[-18,0,0]),(7,[-22,0,0]),(12,[-8,0,0])],
    body=[(0,[0,0,0]),(2.5,[-2,14,0]),(4,[2,2,0]),(5,[4,-16,0]),(6,[4,-24,0]),(7,[3,-24,0]),(12,[0,0,0])],
    head=[(0,[0,0,0]),(2.5,[2,-8,0]),(5,[-3,10,0]),(12,[0,0,0])],
    left_arm=[(0,[0,0,-3]),(2.5,[-42,-18,-8]),(7,[-38,-14,-8]),(12,[0,0,-3])],
    left_forearm=[(0,[-8,0,0]),(2.5,[-70,0,0]),(7,[-64,0,0]),(12,[-8,0,0])],
    right_leg=[(0,[0,0,0]),(5,[-10,0,2]),(12,[0,0,0])],
    left_leg=[(0,[0,0,0]),(5,[8,0,-2]),(12,[0,0,0])])

# Drawn Reid ordinary strokes are ONE-HANDED (ReinhardSword.strikeRelease): the left palm
# leaves the pommel for the scabbard mouth over the first ticks and returns at the end.
# Rows are authored by the RIGHT PALM (guard end of the hilt) so the arm reach is visible
# in the data: shoulder (5,22,0) -> elbow -> wrist is 4.5 + 4.81 = 9.31 px, keep every
# palm 6..8.9 px from the shoulder (no locked elbow, no residual in crouch either).
# The hand stays right of / below the head (hair envelope); the blade points away from
# the torso in body space, the chest twist (body ky) carries the stroke across.
PALM_PX = 3.5*.574*.78   # handle centre -> guard-side palm along the blade, scaled like the mesh
def _rot(axis, deg, v):
    a=math.radians(deg); c,s=math.cos(a),math.sin(a); x,y,z=v
    if axis=='x': return [x, c*y-s*z, s*y+c*z]
    if axis=='y': return [c*x+s*z, y, -s*x+c*z]
    return [c*x-s*y, s*x+c*y, z]
def blade_axis(rot):
    """Render-space blade direction of grip Euler degrees (Rz*Ry*Rx, as ReinhardSword)."""
    return _rot('z',rot[2],_rot('y',rot[1],_rot('x',rot[0],[0,1,0])))
def palm_of(grip):
    pos,rot=grip
    return [round(p+PALM_PX*a,3) for p,a in zip(pos,blade_axis(rot))]
def palm_rows(rows):
    """(t, palm, rot) -> (t, grip, rot); READY rows pass through unchanged."""
    out=[]
    for t,*row in rows:
        if len(row)==1: out.append((t,*row[0])); continue
        palm,rot=row
        out.append((t,[round(p-PALM_PX*a,3) for p,a in zip(palm,blade_axis(rot))],rot))
    return out
REID_STRIKES={
 # Forehand diagonal: blade loaded over the right shoulder, cut to the lower left.
 'reid_slash_down':dict(length=13,
   grip=palm_rows([(0,READY),(1,[6,21.4,-7],[376,0,-42]),(2,[8.6,26,-3.4],[400,0,-36]),(3.4,[7,24.5,-6.5],[362,0,-40]),
         (4.4,[4.5,21.5,-8.3],[292,0,-42]),(5.4,[3,18.8,-7.4],[238,0,-42]),(7,[2.8,17.6,-6.4],[226,0,-44]),(13,READY)]),
   body=[(0,[0,0,0]),(2,[-4,20,0]),(3.4,[-1,12,0]),(4.4,[3,-4,0]),(5.4,[5,-18,0]),(7,[5,-22,0]),(13,[0,0,0])],
   head=[(0,[0,0,0]),(2,[-3,-14,0]),(5.4,[0,12,0]),(7,[0,14,0]),(13,[0,0,0])],
   forearm=[(0,[-18,0,0]),(2,[-44,0,0]),(4.4,[-12,0,0]),(7,[-10,0,0]),(13,[-18,0,0])],
   right_leg=[(0,[0,0,0]),(4.4,[-14,0,3]),(8,[-14,0,3]),(13,[0,0,0])],
   right_shin=[(0,[0,0,0]),(4.4,[16,0,0]),(8,[16,0,0]),(13,[0,0,0])],
   left_leg=[(0,[0,0,0]),(4.4,[10,0,-3]),(8,[10,0,-3]),(13,[0,0,0])]),
 # Backhand rising diagonal: from the low left (chest turned left) up past the right shoulder.
 'reid_slash_rise':dict(length=13,
   grip=palm_rows([(0,READY),(2,[1.6,16.8,-5.6],[206,0,-52]),(3.4,[2.4,17.8,-6.8],[230,0,-46]),
         (4.4,[4.2,20.6,-8.3],[286,0,-42]),(5.4,[6.2,23.6,-6.8],[350,0,-42]),(7,[8,25.6,-4],[384,0,-40]),(9.5,[6.4,21,-7],[366,0,-46]),(13,READY)]),
   body=[(0,[0,0,0]),(2,[4,-22,0]),(3.4,[3,-14,0]),(4.4,[1,0,0]),(5.4,[-2,14,0]),(7,[-4,18,0]),(13,[0,0,0])],
   head=[(0,[0,0,0]),(2,[4,14,0]),(5.4,[-3,-10,0]),(13,[0,0,0])],
   forearm=[(0,[-18,0,0]),(2,[-30,0,0]),(4.4,[-14,0,0]),(7,[-34,0,0]),(13,[-18,0,0])],
   right_leg=[(0,[0,0,0]),(2,[-6,0,3]),(5.4,[4,0,3]),(13,[0,0,0])],
   left_leg=[(0,[0,0,0]),(2,[6,0,-3]),(5.4,[-12,0,-3]),(13,[0,0,0])],
   left_shin=[(0,[0,0,0]),(5.4,[14,0,0]),(13,[0,0,0])]),
 # Forehand horizontal at chest height (below the chin), right to left.
 'reid_slash_sweep':dict(length=15,
   grip=palm_rows([(0,READY),(3,[9.2,21.2,-4.6],[404,0,-90]),(4.2,[8.4,21,-6.6],[372,0,-90]),(5.2,[7,20.8,-8],[330,0,-90]),
         (6.2,[4.8,20.8,-8.4],[276,0,-90]),(7.2,[2.8,20.8,-7.9],[226,0,-90]),(8.2,[1.8,20.8,-6.8],[194,0,-90]),
         (10,[2,20.6,-6.2],[186,0,-84]),(15,READY)]),
   body=[(0,[0,0,0]),(3,[0,28,0]),(4.2,[0,20,0]),(5.2,[2,8,0]),(6.2,[3,-6,0]),(7.2,[3,-20,0]),(8.2,[3,-28,0]),
         (10,[2,-28,0]),(15,[0,0,0])],
   head=[(0,[0,0,0]),(3,[0,-20,0]),(7.2,[0,14,0]),(10,[0,20,0]),(15,[0,0,0])],
   forearm=[(0,[-18,0,0]),(3,[-50,0,0]),(6.2,[-8,0,0]),(10,[-24,0,0]),(15,[-18,0,0])],
   right_leg=[(0,[0,0,0]),(3,[-6,0,8]),(10,[-6,0,8]),(15,[0,0,0])],
   left_leg=[(0,[0,0,0]),(3,[6,0,-8]),(10,[6,0,-8]),(15,[0,0,0])],
   right_shin=[(0,[0,0,0]),(3,[12,0,0]),(10,[12,0,0]),(15,[0,0,0])],
   left_shin=[(0,[0,0,0]),(3,[12,0,0]),(10,[12,0,0]),(15,[0,0,0])]),
 # Heavy finisher: one-handed high guard right of the head, the chest folds into the cut.
 'reid_slash_finish':dict(length=22,
   grip=palm_rows([(0,READY),(2,[7.4,23,-6.6],[376,0,-30]),(4,[8.2,29.6,-1.6],[398,0,-16]),(6.5,[8.4,30,-1.2],[404,0,-14]),(7.3,[7.6,28.6,-4.6],[370,0,-10]),
         (8.2,[6,25.4,-7.6],[326,0,-6]),(9.2,[4.6,21.6,-8.6],[274,0,-2]),(10.2,[3.8,18.6,-7.6],[252,0,0]),
         (14,[3.8,18.4,-7.4],[248,0,0]),(22,READY)]),
   body=[(0,[0,0,0]),(4,[-10,8,0]),(6.5,[-12,8,0]),(7.3,[-6,4,0]),(8.2,[4,0,0]),(9.2,[11,-2,0]),(10.2,[14,-2,0]),
         (14,[12,-2,0]),(22,[0,0,0])],
   head=[(0,[0,0,0]),(4,[-8,0,0]),(6.5,[-10,0,0]),(9.2,[-14,0,0]),(14,[-12,0,0]),(22,[0,0,0])],
   forearm=[(0,[-18,0,0]),(4,[-70,0,0]),(6.5,[-74,0,0]),(9.2,[-10,0,0]),(14,[-12,0,0]),(22,[-18,0,0])],
   right_leg=[(0,[0,0,0]),(4,[4,0,3]),(6.5,[4,0,3]),(9.2,[-30,0,4]),(14,[-28,0,4]),(22,[0,0,0])],
   right_shin=[(0,[0,0,0]),(6.5,[6,0,0]),(9.2,[34,0,0]),(14,[32,0,0]),(22,[0,0,0])],
   left_leg=[(0,[0,0,0]),(6.5,[-4,0,-3]),(9.2,[16,0,-4]),(14,[15,0,-4]),(22,[0,0,0])],
   left_shin=[(0,[0,0,0]),(9.2,[22,0,0]),(14,[20,0,0]),(22,[0,0,0])],
   body_pos=[(0,[0,0,0]),(6.5,[0,.4,0]),(9.2,[0,-1,-.5]),(14,[0,-.9,-.4]),(22,[0,0,0])]),
 # Crouch: low backhand sweep at hip height, left to right; the blade stays in front of the hips.
 'reid_slash_low':dict(length=16,
   grip=palm_rows([(0,READY),(3,[1.8,18,-6.6],[185,0,-96]),(4.2,[2.6,18.2,-7.1],[206,0,-96]),(5.2,[3.8,18.4,-7.4],[240,0,-96]),
         (6.2,[5.4,18.4,-7.5],[276,0,-96]),(7.2,[7.2,18.6,-6.8],[318,0,-96]),(8.2,[8.4,19,-5.6],[350,0,-96]),
         (10,[8.8,19.4,-4.6],[364,0,-92]),(16,READY)]),
   body=[(0,[0,0,0]),(3,[6,-26,0]),(4.2,[6,-18,0]),(5.2,[6,-6,0]),(6.2,[6,8,0]),(7.2,[6,18,0]),(8.2,[6,26,0]),
         (10,[5,26,0]),(16,[0,0,0])],
   head=[(0,[0,0,0]),(3,[6,22,0]),(8.2,[6,-20,0]),(16,[0,0,0])],
   forearm=[(0,[-18,0,0]),(3,[-36,0,0]),(6.2,[-8,0,0]),(10,[-30,0,0]),(16,[-18,0,0])],
   right_leg=[(0,[0,0,0]),(3,[-16,0,8]),(10,[-16,0,8]),(16,[0,0,0])],
   right_shin=[(0,[0,0,0]),(3,[26,0,0]),(10,[26,0,0]),(16,[0,0,0])],
   left_leg=[(0,[0,0,0]),(3,[14,0,-8]),(10,[14,0,-8]),(16,[0,0,0])],
   left_shin=[(0,[0,0,0]),(3,[30,0,0]),(10,[30,0,0]),(16,[0,0,0])],
   body_pos=[(0,[0,0,0]),(3,[0,-.8,0]),(10,[0,-.8,0]),(16,[0,0,0])]),
}
for name,m in REID_STRIKES.items():
    bones=dict(body=m['body'],head=m['head'],right_forearm=m['forearm'],
        left_forearm=[(0,[-8,0,0]),(m['length'],[-8,0,0])])
    for leg in ['right_leg','left_leg','right_shin','left_shin']:
        if leg in m: bones[leg]=m[leg]
    CLIPS[name]=action(m['length'],**bones)
    if 'body_pos' in m: pos(CLIPS[name],'body',m['body_pos'])
    weapon(CLIPS[name],m['grip'])

# Sword Drawn state: the stance itself (body/legs, only while standing), its own gait
# (combat walk: shorter steps, bent knees, steady two-hand guard; run: shoulder carry,
# the blade trailing back above the legs), and the ground<->air transitions.
CLIPS['drawn_stance']=loop({
    'body': {'rotation': breathe([2,6,0],[2.6,6,0]), 'position': breathe([0,-.3,0],[0,-.45,0])},
    'right_leg': {'rotation': still([7,0,4])}, 'left_leg': {'rotation': still([-9,0,-3])},
    'right_shin': {'rotation': still([9,0,0])}, 'left_shin': {'rotation': still([7,0,0])}})
def drawn_gait(amplitude, knee, lean, bob):
    bones={}
    for side,phase in [('right',0),('left',math.pi)]:
        bones[side+'_leg']=gait_channel(lambda t,ph=phase: [amplitude*math.cos(2*math.pi*t+ph),0,0])
        bones[side+'_shin']=gait_channel(lambda t,ph=phase: [knee+max(0,math.sin(2*math.pi*t+ph))*(knee+24),0,0])
    bones['body']=gait_channel(lambda t: [lean,4*math.cos(2*math.pi*t),0])
    bones['body']['position']={str(i/40):[0,-bob*abs(math.sin(2*math.pi*i/40)),0] for i in range(41)}
    return loop(bones,1)
CLIPS['drawn_walk']=drawn_gait(26,8,5,.45)
weapon(CLIPS['drawn_walk'],[(i*2.5,[READY[0][0],READY[0][1]-.35*abs(math.sin(math.pi*i/4)),READY[0][2]],
    [READY[1][0]+1.5*math.sin(math.pi*i/4),0,READY[1][2]]) for i in range(9)])
CLIPS['drawn_run']=drawn_gait(44,10,14,.8)
# Sprint: ONE-handed shoulder carry (the renderer releases the left hand by the run
# weight): the right fist in front of the right shoulder, the blade laid back over it,
# right of the head. Blending from the guard only tilts the blade back (shortest slerp).
RUN_PALM,RUN_ROT=[7.6,24,-4.5],[400,0,-28]
weapon(CLIPS['drawn_run'],palm_rows([(i*2.5,[RUN_PALM[0],RUN_PALM[1]-.5*abs(math.sin(math.pi*i/4)),RUN_PALM[2]],
    [RUN_ROT[0]+3*math.sin(math.pi*i/4),0,RUN_ROT[2]]) for i in range(9)]))
# Air step: a quick tuck on every Cloud Step; landing: knees absorb, chest dips.
CLIPS['air_step']=action(6,
    right_leg=[(0,[-25,0,4]),(2,[-46,0,5]),(6,[-20,0,4])], right_shin=[(0,[38,0,0]),(2,[70,0,0]),(6,[34,0,0])],
    left_leg=[(0,[12,0,-4]),(2,[-18,0,-5]),(6,[6,0,-4])], left_shin=[(0,[20,0,0]),(2,[54,0,0]),(6,[24,0,0])],
    body=[(0,[-6,0,0]),(2,[10,0,0]),(6,[0,0,0])], head=[(0,[0,0,0]),(2,[-8,0,0]),(6,[0,0,0])])
CLIPS['land']=action(8,
    right_leg=[(0,[-9,0,4]),(2,[-24,0,4]),(8,[0,0,0])], right_shin=[(0,[16,0,0]),(2,[44,0,0]),(8,[0,0,0])],
    left_leg=[(0,[-9,0,-4]),(2,[-22,0,-4]),(8,[0,0,0])], left_shin=[(0,[16,0,0]),(2,[42,0,0]),(8,[0,0,0])],
    body=[(0,[4,0,0]),(2,[16,0,0]),(8,[0,0,0])], head=[(0,[0,0,0]),(2,[-12,0,0]),(8,[0,0,0])])
pos(CLIPS['land'],'body',[(0,[0,0,0]),(2,[0,-1,-.4]),(8,[0,0,0])])

# Bounded C1 curves only for strikes: no per-segment ease restart at contacts.
# Draw/sheath cinematics retain their protected paths and free-blade roll.
for name in ['light_punch','light_punch_left','light_hook','combo_1','combo_2','combo_3','sheath_strike','cut_rain','chop_left','chop_right',*REID_STRIKES]:
    for bone in CLIPS[name]['bones'].values():
        for channel_data in bone.values():
            if isinstance(channel_data, dict):
                for time, entry in list(channel_data.items()):
                    if isinstance(entry, list):
                        entry = channel_data[time] = {'vector': entry}
                    if isinstance(entry, dict):
                        entry.pop('easing', None)
                        entry['lerp_mode'] = 'monotone'

if __name__ == '__main__':
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT,'w') as f:
        json.dump({'format_version':'1.8.0','animations':CLIPS}, f, indent=2)
        f.write('\n')
    print('Wrote', len(CLIPS), 'clips')
