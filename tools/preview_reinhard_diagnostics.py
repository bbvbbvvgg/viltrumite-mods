"""CPU preview of ReinhardDiagnosticTest exports (not a Minecraft acceptance run).
Run from the repository root after REINHARD_DIAGNOSTICS=1 ./gradlew test.
"""
import sys,json,argparse
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from preview_geo import *
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('samples',nargs='*')
parser.add_argument('--source-dir',type=Path)
parser.add_argument('--output-dir',type=Path)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]/'viltrumitecore';texroot=root/'build/resources/main/assets/viltrumitecore/textures/entity/reinhard'
for source in (args.source_dir or root/'build/diagnostics/reinhard').glob('*.json'):
 if args.samples and source.stem not in args.samples:continue
 meshes=[]
 for mesh in json.loads(source.read_text()):
  texture=Image.open(texroot/mesh['texture']).convert('RGBA');quads=[]
  for q in mesh['quads']:
   us=[v[3] for v in q];vs=[v[4] for v in q];u0,u1=min(us),max(us);v0,v1=min(vs),max(vs)
   corners=[min(q,key=lambda p:(p[3]-u)**2+(p[4]-v)**2)[:3] for u,v in [(u0,v0),(u1,v0),(u1,v1),(u0,v1)]]
   quads.append((corners,(u0,v0,u1,v1)))
  meshes.append((quads,texture))
 panels=[]
 for view in ['front','side','back']:
  im=Image.new('RGBA',(340,380),(40,44,52,255));z=[[-1e9]*340 for _ in range(380)]
  for quads,texture in meshes:raster(im,z,quads,texture,1,1,VIEWS[view],9,170,330)
  panels.append(im)
 out=Image.new('RGBA',(1020,380),(40,44,52,255))
 for i,p in enumerate(panels):out.paste(p,(i*340,0))
 destination=args.output_dir or root/'build/diagnostics/reinhard/previews';destination.mkdir(parents=True,exist_ok=True)
 out.save(destination/(source.stem+'.png'))
