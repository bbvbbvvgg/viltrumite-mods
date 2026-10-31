"""Sounds of the contextual interactions (docs/contextual-interactions.json) — derived from the mod's own CC0 bank.

Each cue layers existing one-shots of the participants (Thor's own synthesis, Captain's own synthesis, Reinhard's
Reid strikes, Iron Man's repulsor riser) so the combo sounds like *both* heroes at once. No new recordings, no film
audio. Ledger: tools/assets/interaction_sources.md.

Rebuild: python3 tools/sfx/interactions.py  (needs ffmpeg with libvorbis; deterministic: no random layers)
Writes viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/interaction/<name>.ogg.b64.
"""
import base64
import os
import subprocess
import sys
import tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
BANK = os.path.join(ROOT, 'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds')
OUT = os.path.join(BANK, 'interaction')

# name -> (length s, [(source, delay ms, gain dB, pitch factor, trim s or None)])
CUES = {
    # Reid's stroke into vibranium: the blade's heavy hit, the shield's ring, a falling sub crack
    'shield_shatter': (2.2, [('reinhard/reid_heavy_1', 0, 0.0, 1.0, None), ('captain/block', 0, -2.0, 0.82, None),
                             ('reinhard/erase_shatter', 30, -5.0, 1.15, 2.0), ('captain/parry', 60, -9.0, 0.7, None)]),
    # Mjolnir on the shield: hammer metal + the shield's sung parry + a thunder impact + the electric shock wave
    'storm_shield': (3.0, [('thor/hammer_metal_1', 0, -3.0, 1.0, None), ('captain/parry', 0, -4.0, 0.9, None),
                           ('thor/thunder_impact_1', 20, -5.0, 1.0, None), ('thor/electric_wave', 70, -6.0, 1.0, None),
                           ('thor/hammer_bass_1', 0, -7.0, 1.0, None)]),
    # a meteor dies on a raised guard: slam, earth, the shield's clang, a long rumble
    'meteor_burst': (3.0, [('captain/slam_impact', 0, 0.0, 0.85, None), ('thor/hammer_earth_1', 0, -2.0, 0.9, None),
                           ('captain/block', 0, -3.0, 0.75, None), ('thor/thunder_rumble_1', 80, -8.0, 0.9, 2.6)]),
    # lightning splits a meteor: crack, earth burst, zap
    'meteor_split': (3.0, [('thor/thunder_crack_1', 0, 0.0, 1.0, None), ('thor/hammer_earth_2', 90, -2.0, 0.85, None),
                           ('thor/electric_zap_1', 0, -4.0, 1.0, None), ('thor/thunder_rumble_2', 150, -8.0, 1.0, 2.6)]),
    # lightning into the arc reactor: zap + the repulsor riser + an electric tail
    'overcharge': (2.2, [('thor/electric_zap_2', 0, 0.0, 1.0, None), ('ironman/repulsor_charge', 30, -3.0, 1.2, 1.6),
                         ('thor/electric_tail', 120, -6.0, 1.0, 1.8)]),
    # Reid meets Mjolnir: blade hit + hammer metal + zap + a far crack
    'divine_clash': (2.4, [('reinhard/reid_hit_1', 0, 0.0, 1.0, None), ('thor/hammer_metal_2', 0, -1.0, 1.0, None),
                           ('thor/electric_zap_3', 20, -4.0, 1.0, None), ('thor/thunder_crack_2', 60, -9.0, 1.0, 2.0)]),
    # Thanos swats a summon away: body thud, earth, whoosh
    'titan_fling': (1.6, [('thor/hammer_body_1', 0, 0.0, 0.8, None), ('thor/hammer_earth_1', 0, -3.0, 1.0, 1.4),
                          ('thor/dash', 40, -6.0, 0.9, None)]),
}


def decode(src, tmp):
    path = os.path.join(BANK, src + '.ogg.b64')
    out = os.path.join(tmp, src.replace('/', '_') + '.ogg')
    with open(path, 'rb') as f:
        data = base64.b64decode(f.read())
    with open(out, 'wb') as f:
        f.write(data)
    return out


def render(name, length, layers, tmp):
    args = ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y']
    chains = []
    for i, (src, delay, gain, pitch, trim) in enumerate(layers):
        args += ['-i', decode(src, tmp)]
        f = f'[{i}:a]aformat=sample_rates=44100:channel_layouts=mono'
        if pitch != 1.0:
            f += f',asetrate={int(44100 * pitch)},aresample=44100'
        if trim:
            f += f',atrim=0:{trim},afade=t=out:st={max(0.0, trim - 0.4)}:d=0.4'
        f += f',volume={gain}dB,adelay={delay}:all=1[l{i}]'
        chains.append(f)
    mix = ''.join(f'[l{i}]' for i in range(len(layers)))
    chains.append(f'{mix}amix=inputs={len(layers)}:normalize=0:duration=longest,'
                  f'atrim=0:{length},afade=t=out:st={length - 0.35}:d=0.35,alimiter=limit=0.89:level=false[out]')
    ogg = os.path.join(tmp, name + '.ogg')
    args += ['-filter_complex', ';'.join(chains), '-map', '[out]', '-ac', '1', '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '5', ogg]
    subprocess.run(args, check=True)
    os.makedirs(OUT, exist_ok=True)
    with open(ogg, 'rb') as f:
        text = base64.encodebytes(f.read()).decode('ascii')
    with open(os.path.join(OUT, name + '.ogg.b64'), 'w', newline='\n') as f:
        f.write(text)
    return os.path.getsize(ogg)


def main():
    names = sys.argv[1:] or list(CUES)
    with tempfile.TemporaryDirectory() as tmp:
        for name in names:
            length, layers = CUES[name]
            print(name, render(name, length, layers, tmp), 'bytes')


if __name__ == '__main__':
    main()
