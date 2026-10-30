import numpy as np, re, glob, json, sys
from PIL import Image, ImageDraw
cols='/data/atheria/work/cols'
X0,Z0,X1,Z1,S=[int(v) for v in sys.argv[1:6]]
out=sys.argv[6]
W=(X1-X0)//S; H=(Z1-Z0)//S
img=np.zeros((H,W,3),np.uint8)
pal=np.array([[0,0,0],[60,60,70],[170,170,160],[70,140,60],[150,100,50],[200,120,200],[250,250,90]],np.uint8)
for rx in range(X0>>9,(X1-1>>9)+1):
  for rz in range(Z0>>9,(Z1-1>>9)+1):
    try: d=np.load(f'{cols}/r.{rx}.{rz}.npz')
    except FileNotFoundError: continue
    m=d['mat']; h=d['h2']
    bx=rx*512; bz=rz*512
    xs=max(X0,bx); xe=min(X1,bx+512); zs=max(Z0,bz); ze=min(Z1,bz+512)
    mm=m[zs-bz:ze-bz:S, xs-bx:xe-bx:S]; hh=h[zs-bz:ze-bz:S, xs-bx:xe-bx:S]
    c=pal[mm].astype(np.float32)
    hy=np.clip((hh.astype(np.float32)/2+52),0,100)
    f=np.where(hy<3,1.0,0.45)
    c=c*f[...,None]
    c[hh==-32768]=0
    img[(zs-Z0)//S:(zs-Z0)//S+mm.shape[0],(xs-X0)//S:(xs-X0)//S+mm.shape[1]]=c.astype(np.uint8)
im=Image.fromarray(img); dr=ImageDraw.Draw(im)
for g in range(X0- X0%256,X1,256):
  dr.line([((g-X0)//S,0),((g-X0)//S,H)],fill=(40,40,120)); dr.text(((g-X0)//S+2,2),str(g),fill=(120,120,255))
for g in range(Z0- Z0%256,Z1,256):
  dr.line([(0,(g-Z0)//S),(W,(g-Z0)//S)],fill=(40,40,120)); dr.text((2,(g-Z0)//S+2),str(g),fill=(120,120,255))
if len(sys.argv)>7:
  for x,y,z,t in json.load(open(sys.argv[7])):
    if X0<=x<X1 and Z0<=z<Z1: dr.text(((x-X0)//S,(z-Z0)//S),t[:20],fill=(255,80,80))
im.save(out)
