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
    stroke = [(0,[-165,0,8]),(5,[-155,0,55]),(10,[-100,0,80]),
              (14,[-25,0,35]),(18,[-65,0,12]),(20,[-165,0,8])]
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
        rows = [(t,[-arm_amplitude*math.cos(2*math.pi*t/20+phase),0,3*sign]) for t in range(0,21,2)]
        bones[side+'_arm'] = channel(rows)
        bones[side+'_leg'] = channel([(t,[amplitude*math.cos(2*math.pi*t/20+phase),0,0]) for t in range(0,21,2)])
        bones[side+'_forearm'] = channel([(t,[-12-10*max(0,math.cos(2*math.pi*t/20+phase)),0,0]) for t in range(0,21,2)])
    CLIPS[name] = loop(bones,1)

CLIPS['light_punch'] = action(8,
    right_arm=[(0,[-6,0,3]),(1.6,[-47,-16,7],'easeInCubic'),(3,[-91,8,0],'easeOutCubic'),(4,[-92,8,0]),(8,[-6,0,3])],
    right_forearm=[(0,[-8,0,0]),(1.6,[-68,0,0]),(3,[-4,0,0]),(4,[-6,0,0]),(8,[-8,0,0])],
    body=[(0,[0,0,0]),(1.6,[0,-9,0]),(3,[4,17,0]),(4,[3,13,0]),(8,[0,0,0])],
    head=[(0,[0,0,0]),(3,[-3,-10,0]),(8,[0,0,0])],
    left_arm=[(0,[0,0,-3]),(2,[-20,0,-8]),(4,[-16,0,-6]),(8,[0,0,-3])],
    left_forearm=[(0,[-8,0,0]),(3,[-30,0,0]),(8,[-8,0,0])],
    right_leg=[(0,[0,0,0]),(3,[-9,0,2]),(8,[0,0,0])],
    left_leg=[(0,[0,0,0]),(3,[7,0,-2]),(8,[0,0,0])])
pos(CLIPS['light_punch'],'right_arm',[(0,[0,0,0]),(3,[0,0,-1]),(8,[0,0,0])])
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
    ('combo_1',8,3,[-55,-18,8],[-92,5,0],18),
    ('combo_2',8,3,[-67,58,24],[-86,-42,-8],-22),
    ('combo_3',12,5,[-155,14,12],[-48,-8,6],-15)]:
    CLIPS[n] = action(length,
        right_arm=[(0,[-16,-12,6]),(contact-1.2,wind,'easeInCubic'),(contact,strike,'easeOutCubic'),(contact+1,strike),(length,[-16,-12,6])],
        right_forearm=[(0,[-24,0,0]),(contact-1.2,[-72,0,0]),(contact,[-5,0,0]),(contact+1,[-9,0,0]),(length,[-24,0,0])],
        body=[(0,[0,-7,0]),(contact-1.2,[-4,-twist*.6,0]),(contact,[10 if n=='combo_3' else 3,twist,0]),(contact+1,[7,twist*.8,0]),(length,[0,-7,0])],
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
    right_arm=[(0,[-86,-6,2]),(2,[-135,16,10],'easeInCubic'),(3,[-45,-18,2],'easeOutCubic'),(4,[-28,-22,0]),(12,[-16,-12,6])],
    right_forearm=[(0,[-8,0,0]),(2,[-50,0,0]),(3,[-4,0,0]),(12,[-24,0,0])],
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
            side+'_arm': [(0,[-16,0,3*m]),(contact-1,[-135,35*m,16*m],'easeInCubic'),(contact,[-50,-30*m,-8*m],'easeOutCubic'),(contact+1,[-38,-42*m,-10*m]),(length,[-16,0,3*m])],
            side+'_forearm': [(0,[-12,0,0]),(contact-1,[-50,0,0]),(contact,[-5,0,0]),(length,[-12,0,0])],
            'body': [(0,[0,0,0]),(contact-1,[-4,16*m,0]),(contact,[10,-18*m,0]),(length,[0,0,0])],
            'head': [(0,[0,0,0]),(contact,[-6,8*m,0]),(length,[0,0,0])]})
CLIPS['sheath_strike'] = action(12,
    right_arm=[(0,[-30,24,6]),(4,[-60,55,18],'easeInCubic'),(6,[-82,-44,-8],'easeOutCubic'),(8,[-55,-50,-8]),(12,[-30,24,6])],
    right_forearm=[(0,[-38,0,0]),(4,[-65,0,0]),(6,[-4,0,0]),(12,[-38,0,0])],
    body=[(0,[0,0,0]),(4,[0,20,0]),(6,[8,-22,0]),(12,[0,0,0])],
    left_arm=[(0,[-18,0,-8]),(4,[-10,0,-14]),(12,[-18,0,-8])])
# Twelve sharp cuts during the 20-tick server fan, with alternating torso drive.
CLIPS['cut_rain'] = action(20,
    right_arm=[(t,[-85,(-1)**i*38,(-1)**i*10], 'easeOutCubic') for i,t in enumerate([0]+[round((i+1)*20/12,6) for i in range(12)])],
    right_forearm=[(0,[-18,0,0]),(2,[-5,0,0]),(18,[-5,0,0]),(20,[-18,0,0])],
    body=[(t,[4,(-1)**i*12,0]) for i,t in enumerate(range(0,21,2))],
    left_arm=[(0,[0,0,-4]),(3,[12,0,-16]),(17,[12,0,-16]),(20,[0,0,-4])])
CLIPS['world_cut'] = action(80,
    right_arm=[(0,[-26,-10,6]),(10,[-166,8,25]),(13,[-170,8,25],'easeInCubic'),(20,[-42,-8,6],'easeOutCubic'),(80,[-42,-8,6])],
    left_arm=[(0,[0,0,-4]),(10,[-156,-12,-25]),(13,[-160,-12,-25]),(20,[-34,12,-8]),(80,[-34,12,-8])],
    right_forearm=[(0,[-18,0,0]),(10,[-35,0,0]),(20,[-4,0,0]),(80,[-4,0,0])],
    left_forearm=[(0,[-8,0,0]),(10,[-45,0,0]),(20,[-12,0,0]),(80,[-12,0,0])],
    body=[(0,[0,0,0]),(12,[-8,0,0]),(20,[18,0,0]),(26,[10,0,0]),(80,[10,0,0])],
    head=[(0,[0,0,0]),(12,[-8,0,0]),(20,[-10,0,0]),(80,[-10,0,0])],
    right_leg=[(0,[0,0,0]),(20,[-18,0,4]),(80,[-18,0,4])],
    left_leg=[(0,[0,0,0]),(20,[12,0,-4]),(80,[12,0,-4])])
CLIPS['world_erasure'] = action(150,
    right_arm=[(0,[-26,-10,6]),(15,[-115,-12,8]),(30,[-115,-12,8],'easeInCubic'),(40,[-45,-52,-8],'easeOutCubic'),(150,[-45,-52,-8])],
    right_forearm=[(0,[-18,0,0]),(15,[-38,0,0]),(30,[-38,0,0]),(40,[-8,0,0]),(150,[-8,0,0])],
    left_arm=[(0,[0,0,-4]),(15,[-58,12,-8]),(30,[-58,12,-8]),(40,[-20,24,-12]),(150,[-20,24,-12])],
    left_forearm=[(0,[-8,0,0]),(15,[-30,0,0]),(40,[-18,0,0]),(150,[-18,0,0])],
    body=[(0,[0,0,0]),(15,[0,10,0]),(30,[0,10,0]),(40,[5,-16,0]),(150,[5,-16,0])],
    head=[(0,[0,0,0]),(30,[-5,-5,0]),(40,[-5,8,0]),(150,[-5,8,0])])

if __name__ == '__main__':
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT,'w') as f:
        json.dump({'format_version':'1.8.0','animations':CLIPS}, f, indent=2)
        f.write('\n')
    print('Wrote', len(CLIPS), 'clips')
