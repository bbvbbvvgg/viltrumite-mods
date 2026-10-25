"""64px knight ability emblems. Original deterministic pixel art, no font/glyph assets.
White enamel, blue cloth, steel bevels, gold fittings; native-resolution details.
Imported by make_reinhard_icons.py, which owns output paths and base64 packaging.
"""
import math
from PIL import Image, ImageDraw

WHITE='#f6f4e8'; STEEL='#b2c9df'; SHADE='#637f9e'; GOLD='#e7bd64'; DARK='#162942'; BLUE='#376caa'; RED='#ba4a48'

def tile():
    im=Image.new('RGBA',(64,64)); p=im.load()
    for y in range(64):
        for x in range(64):
            glow=max(0,1-math.hypot(x-38,y-20)/57)
            p[x,y]=(int(18+22*glow),int(33+35*glow),int(59+51*glow),255)
    d=ImageDraw.Draw(im)
    d.rectangle((1,1,62,62),outline=GOLD,width=1)
    d.line((3,3,60,3),fill=WHITE);d.line((3,3,3,60),fill=SHADE)
    for x,y in [(5,5),(58,5),(5,58),(58,58)]:
        d.polygon([(x,y-2),(x+2,y),(x,y+2),(x-2,y)],fill=GOLD)
    return im,d

def ring(d,x,y,rx=15,ry=7,col=STEEL):
    d.ellipse((x-rx,y-ry,x+rx,y+ry),outline=DARK,width=4)
    d.ellipse((x-rx,y-ry,x+rx,y+ry),outline=col,width=2)

def spark(d,x,y,r=4):
    d.line((x-r,y,x+r,y),fill=WHITE,width=1);d.line((x,y-r,x,y+r),fill=GOLD,width=1)
    d.rectangle((x-1,y-1,x+1,y+1),fill=WHITE)

def arrow(d,a,b,col=WHITE,width=3):
    d.line((*a,*b),fill=DARK,width=width+2);d.line((*a,*b),fill=col,width=width)
    dx,dy=b[0]-a[0],b[1]-a[1];n=math.hypot(dx,dy);ux,uy=dx/n,dy/n
    d.polygon([b,(b[0]-ux*8-uy*4,b[1]-uy*8+ux*4),(b[0]-ux*8+uy*4,b[1]-uy*8-ux*4)],fill=col)

def fist(d,x=34,y=29,scale=1):
    def box(v,fill,outline=DARK,w=1):
        d.rectangle(tuple(round((z*scale)+(x if i%2==0 else y)) for i,z in enumerate(v)),fill=fill,outline=outline,width=w)
    box((-17,4,-2,15),BLUE);box((-15,4,-10,15),GOLD);box((-13,6,-11,13),WHITE)
    d.polygon([(x-5,y-8),(x+13,y-8),(x+18,y-3),(x+18,y+10),(x+12,y+15),(x-3,y+14),(x-8,y+7)],fill=SHADE,outline=DARK)
    box((-5,-6,12,9),STEEL);box((-5,-6,12,-3),WHITE)
    for k in range(4):
        box((-6+k*5,-10,-2+k*5,0),STEEL);box((-6+k*5,-10,-2+k*5,-8),WHITE)
    box((-8,2,-1,9),WHITE);d.line((x+2,y+3,x+13,y+3),fill=SHADE,width=1)

def palm(d,x=29,y=34):
    # Open gauntlet: catching must not share the punch silhouette.
    d.rectangle((x-8,y+3,x+7,y+14),fill=BLUE,outline=DARK)
    d.line((x-7,y+12,x+6,y+12),fill=GOLD,width=2)
    d.polygon([(x-8,y-9),(x+8,y-9),(x+9,y+4),(x+4,y+9),(x-6,y+8)],fill=STEEL,outline=DARK)
    for dx,top in [(-8,-22),(-3,-25),(2,-24),(7,-19)]:
        d.rectangle((x+dx,y+top,x+dx+3,y-5),fill=STEEL,outline=DARK)
        d.line((x+dx+1,y+top+2,x+dx+1,y-7),fill=WHITE)
    d.polygon([(x+7,y),(x+15,y-8),(x+17,y-5),(x+11,y+7),(x+5,y+9)],fill=STEEL,outline=DARK)
    d.line((x-5,y-6,x+4,y-6),fill=WHITE,width=2)

def blade(d,ax=17,ay=49,bx=46,by=13,sheathed=False):
    dx,dy=bx-ax,by-ay;n=math.hypot(dx,dy);ux,uy=dx/n,dy/n;vx,vy=-uy,ux
    def p(t,w):return (round(ax+ux*t+vx*w),round(ay+uy*t+vy*w))
    d.line((*p(-7,0),*p(n,0)),fill=DARK,width=9)
    d.polygon([p(0,-3),p(n-6,-3),p(n,0),p(n-6,3),p(0,3)],fill=BLUE if sheathed else STEEL,outline=DARK)
    d.line((*p(2,-1),*p(n-5,-1)),fill=GOLD if sheathed else WHITE,width=2)
    if sheathed:
        for t in [3,n-10]:d.line((*p(t,-4),*p(t,4)),fill=GOLD,width=2)
    d.line((*p(0,-9),*p(0,9)),fill=DARK,width=5);d.line((*p(0,-8),*p(0,8)),fill=GOLD,width=3)
    d.line((*p(-8,0),*p(-1,0)),fill=BLUE,width=3)
    d.ellipse((ax-2,ay-2,ax+2,ay+2),fill=RED,outline=GOLD)

def boot(d,x=32,y=30,up=False):
    pts=[(-6,-17),(5,-17),(5,4),(15,9),(15,14),(-7,14)]
    if up:pts=[(-a,-b) for a,b in pts]
    d.polygon([(x+a,y+b) for a,b in pts],fill=STEEL,outline=DARK)
    d.line((x-4,y-13 if not up else y+13,x+3,y-13 if not up else y+13),fill=GOLD,width=3)
    d.line((x-4,y-9,x-4,y+7),fill=WHITE,width=2)
    d.line((x-5,y+12,x+13,y+12),fill=SHADE,width=2)

def figure(d,x,y,col=WHITE,s=1):
    d.rectangle((x-3*s,y-11*s,x+3*s,y-5*s),fill=col,outline=DARK)
    d.polygon([(x-4*s,y-4*s),(x+4*s,y-4*s),(x+6*s,y+6*s),(x-5*s,y+6*s)],fill=col,outline=DARK)
    d.line((x-2*s,y+6*s,x-4*s,y+13*s),fill=col,width=max(1,round(3*s)))
    d.line((x+2*s,y+6*s,x+6*s,y+12*s),fill=col,width=max(1,round(3*s)))

def icon(name):
    im,d=tile()
    if name=='light_punch':
        for y in [21,30,42]:d.line((8,y,24,y-1),fill=STEEL,width=2)
        fist(d);spark(d,54,27,5)
    elif name=='fist_combo':
        for x,y in [(18,18),(30,24)]:
            ghost=Image.new('RGBA',(64,64));fist(ImageDraw.Draw(ghost),30,28)
            ghost=ghost.resize((29,29),Image.Resampling.NEAREST)
            im.alpha_composite(ghost,(x-15,y-10))
        ring(d,37,43,20,9,GOLD);fist(d,34,34);spark(d,52,27,4)
    elif name=='step_dash':
        for x,col in [(17,SHADE),(25,BLUE),(37,WHITE)]:figure(d,x,32,col,.9)
        for y in [20,34,46]:d.line((7,y,28,y),fill=GOLD,width=2)
        arrow(d,(38,45),(54,25),GOLD,2)
    elif name=='dash_behind':
        figure(d,42,31,STEEL,1.1);figure(d,17,34,BLUE,.8)
        d.arc((9,10,54,52),50,270,fill=GOLD,width=3);arrow(d,(27,11),(42,14),WHITE,2)
    elif name in ['catch','reflect']:
        if name=='catch':
            palm(d,28,34)
        else:
            fist(d,30,32)
        if name=='catch':
            arrow(d,(55,15),(36,27),GOLD,2);spark(d,35,28)
            d.line((45,41,53,48),fill=BLUE,width=2)
        else:
            d.line((46,11,40,50),fill=GOLD,width=3)
            arrow(d,(55,21),(45,29),STEEL,2);arrow(d,(45,34),(55,45),WHITE,3)
            spark(d,44,32)
    elif name=='sky_kick':
        figure(d,47,17,WHITE,.55);arrow(d,(52,43),(52,10),GOLD,2)
        ring(d,35,31,20,5)
        d.polygon([(12,47),(27,28),(29,17),(35,12),(41,16),(38,29),(26,39),(20,52)],fill=STEEL,outline=DARK)
        d.line((15,46,30,30,32,19),fill=WHITE,width=3)
        d.line((30,17,38,21),fill=GOLD,width=3)
        d.line((12,49,20,53),fill=BLUE,width=3);spark(d,40,24,4)
    elif name=='grab_throw':
        figure(d,43,25,STEEL,.8);fist(d,24,30)
        d.arc((24,10,56,42),210,335,fill=WHITE,width=2);arrow(d,(48,13),(55,11),GOLD,2)
    elif name=='stomp':
        ring(d,32,48,23,7,GOLD);ring(d,32,47,14,4,WHITE);boot(d,30,29)
        for a,b in [((13,48),(9,54)),((48,47),(55,53)),((30,49),(26,57))]:d.line((*a,*b),fill=STEEL,width=2)
        spark(d,33,45)
    elif name in ['reid','reid_drawn']:
        if name=='reid_drawn':
            for t in range(0,8):
                a=t*math.pi/4;d.line((32,32,32+23*math.cos(a),32+23*math.sin(a)),fill=SHADE,width=1)
        blade(d,sheathed=name=='reid');spark(d,43,14,5)
    elif name=='lethal_chop':
        d.arc((8,7,57,55),235,55,fill=GOLD,width=5)
        d.arc((9,8,56,54),235,55,fill=WHITE,width=2)
        d.polygon([(16,48),(27,25),(38,15),(44,18),(39,33),(28,53)],fill=STEEL,outline=DARK)
        d.line((28,27,38,18),fill=WHITE,width=3);d.line((19,44,28,49),fill=GOLD,width=4);spark(d,48,39)
    elif name=='sheath_strike':
        d.arc((8,12,58,56),190,345,fill=WHITE,width=3)
        blade(d,14,34,52,27,True);spark(d,51,31,5)
    elif name=='cut_rain':
        for i in range(5):
            x=9+i*8;d.line((x,43+i%2*5,x+14,13+i%2*4),fill=GOLD,width=4)
            d.line((x,43+i%2*5,x+14,13+i%2*4),fill=WHITE,width=2)
        spark(d,31,48,4)
    elif name=='world_cut':
        d.polygon([(28,12),(37,12),(55,55),(9,55)],fill=DARK,outline=GOLD)
        d.polygon([(31,13),(34,13),(43,55),(23,55)],fill=WHITE)
        for x,y in [(15,38),(44,34),(12,50),(49,48)]:
            d.polygon([(x,y),(x+5,y-4),(x+8,y+2),(x+2,y+5)],fill=SHADE,outline=STEEL)
        d.line((8,13,55,13),fill=WHITE,width=1);spark(d,32,13,5)
    elif name=='diagonal_world_cut':
        d.polygon([(7,47),(44,9),(57,17),(20,55)],fill=DARK,outline=GOLD)
        d.line((11,49,49,12),fill=WHITE,width=3)
        d.line((13,51,51,14),fill=GOLD,width=1)
        for x,y in [(16,29),(38,43),(30,13),(47,34)]:
            d.polygon([(x,y),(x+5,y-4),(x+8,y+2),(x+2,y+5)],fill=BLUE,outline=STEEL)
        spark(d,35,25,4)
    elif name=='world_erasure':
        d.ellipse((10,10,54,54),fill='#070e1e',outline=GOLD,width=2)
        figure(d,32,33,WHITE,.9)
        shards=[[(14,14),(27,11),(22,25)],[(38,10),(51,17),(40,25)],[(10,31),(20,28),(16,43)],[(45,35),(55,31),(51,48)],[(25,46),(36,44),(32,55)]]
        for pts in shards:d.polygon(pts,fill=BLUE,outline=STEEL)
        d.line((31,11,28,23,36,30,27,38,33,54),fill=WHITE,width=1)
        for x,y in [(26,29),(38,41),(30,43)]:spark(d,x,y,2)
    else:raise ValueError(name)
    return im

NAMES=['light_punch','step_dash','catch','fist_combo','sky_kick','grab_throw','dash_behind','stomp','reflect','reid','reid_drawn','lethal_chop','sheath_strike','cut_rain','world_cut','world_erasure','diagonal_world_cut']
