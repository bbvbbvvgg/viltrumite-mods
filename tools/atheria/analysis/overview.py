import numpy as np, os, re, glob
from PIL import Image
cols='/data/atheria/work/cols'
files=glob.glob(cols+'/r.*.npz')
rs=[tuple(map(int,re.search(r'r\.(-?\d+)\.(-?\d+)\.npz',f).groups())) for f in files]
rx0=min(r[0] for r in rs); rz0=min(r[1] for r in rs); rx1=max(r[0] for r in rs); rz1=max(r[1] for r in rs)
S=8
W=(rx1-rx0+1)*512//S; H=(rz1-rz0+1)*512//S
img=np.zeros((H,W,3),np.uint8)
pal=np.array([[0,0,0],[60,60,70],[170,170,160],[70,140,60],[150,100,50],[200,120,200],[250,250,90]],np.uint8)
for f,(rx,rz) in zip(files,rs):
    d=np.load(f); m=d['mat'][::S,::S]; h=d['h2'][::S,::S]
    c=pal[m].astype(np.int32)
    valid=h>-32768
    shade=np.clip((h.astype(np.int32)+104)//2,0,200)  # h2 -104 = y -52
    c=np.where(valid[...,None], np.clip(c*(0.6+np.minimum(shade,120)[...,None]/300.0),0,255), 0)
    y0=(rz-rz0)*512//S; x0=(rx-rx0)*512//S
    img[y0:y0+512//S,x0:x0+512//S]=c.astype(np.uint8)
Image.fromarray(img).save('/data/atheria/work/an/overview.png')
print(rx0,rz0,rx1,rz1,W,H)
