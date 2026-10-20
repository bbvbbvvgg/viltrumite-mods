#!/usr/bin/env python3
"""Original procedural Reinhard layers; no samples or external licensed input.
Deterministic seed; faded, soft-limited PCM with -6 dBFS peak before Vorbis.
Install via the existing .ogg.b64 Gradle pipeline. Generated audio: CC0-1.0.
Rebuild: python3 tools/sfx/reinhard_cinematic.py
Not a substitute for listening and tuning in Minecraft with other scene layers.
"""
from pathlib import Path
import base64, subprocess, tempfile, wave
import numpy as np
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/reinhard'
RATE=24000

def make(name,duration,kind):
    rng=np.random.default_rng(20261020+kind); t=np.arange(int(duration*RATE))/RATE
    noise=rng.normal(0,1,len(t)); freq=np.fft.rfftfreq(len(t),1/RATE)
    # Brownish air/body, no DC/subsonic output. High layers stay well below the body.
    spectrum=np.fft.rfft(noise)*np.where(freq>25,1/np.maximum(freq,25)**.65,0)
    air=np.fft.irfft(spectrum,n=len(t));air/=np.max(np.abs(air))
    body=np.sin(2*np.pi*(47*t+9*np.sin(t*1.9)))+.35*np.sin(2*np.pi*93*t)
    if kind==0: # six second draw build: tension, metal harmonics, luminous final beat
        env=(.2+.8*(t/duration)**1.7)*np.minimum(1,t/.12)
        metal=sum(np.sin(2*np.pi*f*t)*.07 for f in (227,341,479,683))
        x=env*(body*.26+air*.42+metal)+np.exp(-((t-5.15)/.075)**2)*air*.6
    elif kind==1: # non-looping 1.25s collapse layer; scheduled every 1.4s
        x=(body*.3+air*.72)*np.sin(np.pi*t/duration)**.65
    elif kind==2: # impact, displaced air, decaying stone/low tail
        chirp=np.sin(2*np.pi*(35*t+90*(1-np.exp(-t*7))/7))
        x=(chirp*.65+air*.75)*np.exp(-t*1.5)+air*.2*np.exp(-((t-.2)/.17)**2)
    else: # short blade pressure crack, not a vanilla placeholder
        env=np.sin(np.pi*t/duration)**1.3
        x=(air*.8+np.sin(2*np.pi*(520*t-200*t*t))*.15)*env
    fade=np.minimum(1,t/.025)*np.minimum(1,(duration-t)/min(.25,duration/4))
    x=np.tanh(x*1.4)*fade;x=x/max(1e-8,np.max(np.abs(x)))*.5
    with tempfile.TemporaryDirectory() as temp:
        wav=Path(temp)/'source.wav';ogg=Path(temp)/'output.ogg'
        with wave.open(str(wav),'wb') as w:
            w.setparams((1,2,RATE,len(t),'NONE','not compressed'));w.writeframes((x*32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','4',str(ogg)],check=True)
        data=ogg.read_bytes();(OUT/f'{name}.ogg.b64').write_text(base64.b64encode(data).decode()+'\n')
        print(name,len(data),'bytes; duration',duration,'s; PCM peak -6.02 dBFS')

if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    for item in [('reid_cinematic_layer',6,0),('cut_rumble',1.25,1),('cut_cinematic_impact',3.4,2),('cut_cinematic_sweep',.32,3)]:make(*item)
