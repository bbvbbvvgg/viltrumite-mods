"""Static mixin-target scan: which classes/methods each mod's mixins touch (class files, incl. jar-in-jar)."""
import zipfile,io,struct,json,sys,re
MIX='Lorg/spongepowered/asm/mixin/Mixin;'
INJ={'Lorg/spongepowered/asm/mixin/injection/Inject;':'Inject','Lorg/spongepowered/asm/mixin/injection/Redirect;':'Redirect',
     'Lorg/spongepowered/asm/mixin/injection/ModifyArg;':'ModifyArg','Lorg/spongepowered/asm/mixin/injection/ModifyArgs;':'ModifyArgs',
     'Lorg/spongepowered/asm/mixin/injection/ModifyVariable;':'ModifyVariable','Lorg/spongepowered/asm/mixin/injection/ModifyConstant;':'ModifyConstant',
     'Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;':'WrapOperation','Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;':'ModifyExpressionValue',
     'Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;':'ModifyReturnValue','Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;':'WrapWithCondition',
     'Lcom/llamalad7/mixinextras/injector/WrapWithCondition;':'WrapWithCondition','Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;':'WrapMethod'}
OVR='Lorg/spongepowered/asm/mixin/Overwrite;'
class R:
    def __init__(s,b): s.b=b; s.p=0
    def u1(s): v=s.b[s.p]; s.p+=1; return v
    def u2(s): v=struct.unpack_from('>H',s.b,s.p)[0]; s.p+=2; return v
    def u4(s): v=struct.unpack_from('>I',s.b,s.p)[0]; s.p+=4; return v
def parse(b):
    r=R(b); r.u4(); r.u2(); r.u2(); n=r.u2(); cp=[None]*n; i=1
    while i<n:
        t=r.u1()
        if t==1: l=r.u2(); cp[i]=('u',b[r.p:r.p+l].decode('utf8','replace')); r.p+=l
        elif t in (3,4): r.p+=4
        elif t in (5,6): r.p+=8; i+=1
        elif t==7: cp[i]=('c',r.u2())
        elif t==8: r.p+=2
        elif t in (9,10,11,12): r.p+=4
        elif t==15: r.p+=3
        elif t==16: r.p+=2
        elif t in (17,18): r.p+=4
        elif t in (19,20): r.p+=2
        i+=1
    U=lambda k: cp[k][1] if cp[k] and cp[k][0]=='u' else None
    r.u2(); this=U(cp[r.u2()][1]); r.u2()
    k=r.u2(); r.p+=2*k
    def ev():
        tag=chr(r.u1())
        if tag in 'BCDFIJSZs': return U(r.u2()) if tag=='s' else ('#',r.u2())
        if tag=='e': r.u2(); return U(r.u2())
        if tag=='c': return U(r.u2())
        if tag=='@': return ann()
        if tag=='[': return [ev() for _ in range(r.u2())]
    def ann():
        t=U(r.u2()); vals={}
        for _ in range(r.u2()): k=U(r.u2()); vals[k]=ev()
        return (t,vals)
    def attrs():
        out=[]
        for _ in range(r.u2()):
            name=U(r.u2()); l=r.u4(); end=r.p+l
            if name in ('RuntimeInvisibleAnnotations','RuntimeVisibleAnnotations'):
                out+= [ann() for _ in range(r.u2())]
            r.p=end
        return out
    fields=r.u2()
    for _ in range(fields): r.p+=6; attrs()
    meths=[]
    for _ in range(r.u2()):
        r.u2(); mn=U(r.u2()); md=U(r.u2()); meths.append((mn,md,attrs()))
    cls=attrs()
    return this,cls,meths
def flat(v):
    if isinstance(v,list):
        o=[]; [o.extend(flat(x)) for x in v]; return o
    return [v] if isinstance(v,str) else []
def scan_zip(z,prefix=''):
    res={}
    for n in z.namelist():
        if n.endswith('.jar'):
            try: res.update(scan_zip(zipfile.ZipFile(io.BytesIO(z.read(n))),prefix+n+'!'))
            except Exception: pass
        elif n.endswith('.class'):
            b=z.read(n)
            if MIX.encode()[1:-1] not in b and b'spongepowered/asm/mixin/Mixin' not in b: continue
            try: this,cls,meths=parse(b)
            except Exception as e: continue
            for t,vals in cls:
                if t==MIX:
                    targets=[x.strip('L;').replace('/','.') for x in flat(vals.get('value',[]))]+[x.replace('/','.') for x in flat(vals.get('targets',[]))]
                    hooks=[]
                    for mn,md,ann in meths:
                        for at,av in ann:
                            if at in INJ: hooks.append((INJ[at],flat(av.get('method',[]))))
                            if at==OVR: hooks.append(('Overwrite',[mn+md]))
                    res[prefix+this]={'targets':targets,'hooks':hooks}
    return res
if __name__=='__main__':
    out={}
    for p in sys.argv[1:]:
        out[p.split('/')[-1]]=scan_zip(zipfile.ZipFile(p))
    json.dump(out,sys.stdout,indent=0)
