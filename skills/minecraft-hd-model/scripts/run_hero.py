"""Quick builder: python3 /data/run_hero.py thor  -> writes the hero's body+gear (or model) assets."""
import sys, os, time
sys.path.insert(0, 'tools/assets')
from clean_geo import write_model
from avengers_common import W, T
kind = sys.argv[1]
t = time.time()
mod = __import__('avengers_' + kind)
models = mod.build()
names = mod.OUTPUTS if hasattr(mod, 'OUTPUTS') else None
if kind in ('thor', 'black_widow', 'hawkeye', 'widow'):
    key = {'widow': 'black_widow'}.get(kind, kind)
    b, g = models
    write_model(b, W(f'{key}.geo.json'), T(f'{key}.png'))
    write_model(g, W(f'{key}_gear.geo.json'), T(f'{key}_gear.png'), T(f'{key}_gear_glow.png'))
else:
    for key, m in models:
        write_model(m, W(f'{key}.geo.json'), T(f'{key}.png'), T(f'{key}_glow.png'))
print('built', kind, round(time.time() - t, 1), 's')
