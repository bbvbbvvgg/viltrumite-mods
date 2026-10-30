import sys, os, re, json
sys.path.insert(0,'/data/vm/tools/atheria')
import nbt
from multiprocessing import Pool
W='/data/atheria/work/Atheria_1.20.1/region'
def txt(j):
    try:
        o=json.loads(j)
    except Exception:
        return str(j)
    def f(o):
        if isinstance(o,str): return o
        if isinstance(o,list): return ''.join(f(x) for x in o)
        if isinstance(o,dict): return o.get('text','')+''.join(f(x) for x in o.get('extra',[]))
        return ''
    return f(o)
def reg(fn):
    out=[]
    for i,ts,raw in nbt.read_region(os.path.join(W,fn)):
        _,c=nbt.loads(raw)
        for be in c.get('block_entities',[]):
            id_=str(be.get('id',''))
            if 'sign' in id_:
                lines=[]
                for side in ('front_text','back_text'):
                    t=be.get(side)
                    if t and 'messages' in t:
                        lines+= [txt(str(m)) for m in t['messages']]
                s=' | '.join(l for l in lines if l.strip())
                if s.strip(): out.append((int(be['x']),int(be['y']),int(be['z']),s))
    return out
if __name__=='__main__':
    fs=[f for f in os.listdir(W) if f.endswith('.mca')]
    res=[]
    with Pool(2) as p:
        for o in p.imap_unordered(reg,fs): res+=o
    json.dump(res,open('/data/atheria/work/an/signs.json','w'),ensure_ascii=False)
    print(len(res))
