import sys, numpy as np
sys.path.insert(0,'/data/vm/tools/atheria')
import nbt
W='/data/atheria/work/Atheria_1.20.1/region'
_cache={}
def chunk(cx,cz):
    k=(cx,cz)
    if k in _cache: return _cache[k]
    rx,rz=cx>>5,cz>>5
    idx=(cz&31)*32+(cx&31)
    for i,ts,raw in nbt.read_region(f'{W}/r.{rx}.{rz}.mca'):
        if i==idx:
            _,c=nbt.loads(raw); _cache[k]=c; return c
def block(x,y,z):
    c=chunk(x>>4,z>>4)
    for s in c['sections']:
        if int(s['Y'])==y>>4:
            bs=s['block_states']; pal=bs['palette']
            if len(pal)==1: return str(pal[0]['Name'])
            bits=max(4,(len(pal)-1).bit_length()); per=64//bits
            i=((y&15)*16+(z&15))*16+(x&15)
            arr=np.frombuffer(bs['data'].raw,dtype='>u8')
            v=(int(arr[i//per])>>((i%per)*bits))&((1<<bits)-1)
            p=pal[v]; return str(p['Name'])+str(dict(p.get('Properties',{})) if p.get('Properties') else '')
    return 'air'
def column(x,z,y0=-56,y1=-44):
    return [(y,block(x,y,z).replace('minecraft:','')) for y in range(y0,y1)]
if __name__=='__main__':
    for a in sys.argv[1:]:
        x,z=map(int,a.split(','))
        print(x,z,[b for b in column(x,z) if b[1]!='air'])
