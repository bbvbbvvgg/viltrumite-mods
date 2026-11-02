#!/usr/bin/env python3
"""Writes the Godzilla assets (own work, CC0; ledger tools/assets/godzilla_sources.md):

  geo/godzilla/godzilla.geo.json        LOD0, close up (head, teeth, claws, layered dorsal plates)
  geo/godzilla/godzilla_lod1.geo.json   LOD1, across the district (same rig, simpler plates and limbs)
  geo/godzilla/godzilla_lod2.geo.json   LOD2, across the city (silhouette: body, limbs, tail, plate rows)
  textures/entity/godzilla/godzilla.png       shared 512x512 swatch atlas (scales, hide, belly, plates, mouth)
  textures/entity/godzilla/godzilla_glow.png  emissive atlas (plates, throat, eyes), drawn additively and tinted
                                              per plate by GodzillaRenderer while the breath charges
  animations/godzilla/godzilla.animation.json every clip (shared by the three LODs: same bone names)

Run: python3 tools/assets/godzilla.py   (GodzillaAssetsTest checks pivots against GodzillaRules and the clip set)
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
import godzilla_clips as GC  # noqa: E402
import godzilla_geo as GG  # noqa: E402
import godzilla_lib as G  # noqa: E402
import ultron_lib as U  # noqa: E402

GEO = ['geo/godzilla/godzilla.geo.json', 'geo/godzilla/godzilla_lod1.geo.json', 'geo/godzilla/godzilla_lod2.geo.json']
TEX = 'textures/entity/godzilla/godzilla.png'
GLOW = 'textures/entity/godzilla/godzilla_glow.png'
ANIM = 'animations/godzilla/godzilla.animation.json'


def main():
    base, glow = G.paint_atlas()
    stats = []
    for lod, rel in enumerate(GEO):
        m = GG.build(2 - lod)
        m.pack()
        if lod == 0:
            m.write(rel, TEX, GLOW, base=base, glow=glow)
        else:
            G.write_json(os.path.join(G.RES, rel), m.geo_json())
        stats.append('LOD%d %d bones %d cubes' % (lod, len(m.bones), sum(len(b.cubes) for b in m.bones)))
    clips = GC.all_clips()
    size = U.write_anims(ANIM, clips)
    print('godzilla: %s; %d clips (%d bytes)' % (', '.join(stats), len(clips), size))
    U.finalize_assets(GEO + [ANIM], [TEX, GLOW])


if __name__ == '__main__':
    main()
