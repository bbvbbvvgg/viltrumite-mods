#!/usr/bin/env python3
"""SCP-106 and its pocket dimension: every sound mixed from CC0 Freesound recordings found via Openverse.

Fetch the sources first (Freesound preview MP3s land in ~/.cache/viltrumite-sfx/work/raw, never committed):

    python3 .agents/skills/sound-design/scripts/openverse_sfx.py download <openverse-uuid>

then, from the repository root:

    python3 tools/sfx/scp106.py                 # mix every cue and package it (.ogg.b64, 44.1 kHz Vorbis)
    python3 tools/sfx/scp106.py --no-package --only breath_1

A layer takes `length` s of a source from `start`, plays it at varispeed `pitch` (0.7 = lower and slower),
optionally reversed, band-limits it, applies `gain` dB and lands `delay` s into the cue. A cue sums its layers,
adds a synthetic room (`room` = (seconds, wet mix), a decaying noise impulse response), soft-saturates (`drive`),
fades and peak-normalises to `peak` dBFS. A `loop` cue (the ambience beds) is cut to `length` s with the tail
crossfaded into its head, so it repeats without a seam; beds are stereo, everything positional is mono.
Every source is CC0 1.0; credits in tools/assets/scp106_sources.md and CREDITS.md. Numbers are measured from
envelopes and onsets, not auditioned.
"""
import argparse
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HELPER = os.path.join(ROOT, ".agents", "skills", "sound-design", "scripts", "openverse_sfx.py")
RAW = os.path.join(os.environ.get("OPENVERSE_SFX_HOME", os.path.join(os.path.expanduser("~"), ".cache", "viltrumite-sfx")), "work", "raw")
SR = 44100

SOURCES = {
    'zombie_breath': '5c881de7-4a41-4d97-888e-9bdbe681cd19',   # "Zombie breathing" by Huminaatio
    'slow_breath': 'fd7703b4-31f3-4b0b-85f1-d327e137a1a1',     # "Male Breathing Slow" by Kodack
    'heavy_breath': '4dec51fa-e24f-4a6a-b0ea-3886e0ad3439',    # "Heavy Breathing" by Under7dude
    'steps_concrete': '51bcda21-8c54-4019-a500-b4032c7030ef',  # "footsteps concrete" by Yuval
    'squelch': 'b4a7f38d-7ef3-41ed-a0b6-b83dd682edc9',         # "Squelch.flac" by billox30
    'mud_bubbles': '646e4e55-ad71-4fbf-9311-ba623ad595e0',     # "Bubbling Mud with Steam" by ProductionNow
    'feet_squelch': '586461b4-265a-4e0d-a3d3-14f67c945a4b',    # "Feet Squelch" by eqavox
    'squelching': '6d53a2de-28e5-460b-b76c-e849d6b52b79',      # "squelching.mp3" by tiddles451
    'miser_laugh': '35a6c127-b5af-4b0a-a4ec-d62cb5e51f33',     # "OLD MISER LAUGH" by Elenalostale
    'old_laugh_4': '968781f7-f791-4951-84dc-14267e1ab744',     # "Laugh_old_man_4.wav"
    'dark_drone_1': 'e4eda782-f5ef-4195-95b9-427979252840',    # "Dark Drone 1" by nomiqbomi
    'dark_drone_amb': '660dd2b9-83ed-476d-8e52-aed6b5ca52e6',  # "Dark Drone Ambient" by LulSayer
    'dark_drone_4': '9dd0e804-079e-4619-8a01-f648a2a15cde',    # "Dark Drone 4" by nomiqbomi
    'cave_drips': '8e901828-b785-42bb-86cc-01e70ac776b6',      # "Dripping cave water drops - ambient atmosphere" by Duisterwho
    'cave_loop': 'efe3da3b-e6e3-4292-8fd3-e5b8e36dbf86',       # "Ambiance_Atmosphere_Cave_Loop_Stereo.ogg" by Nox_Sound
    'drips': '4499a09b-0787-4869-8b0a-c5cbed5a12e3',           # "Dripping water" by Electroviolence
    'drip_echo': '7039e283-5809-43a4-ae2f-d218a720d78b',       # "WATER DRIPPING ECHO.wav" by mlsprovideos
    'acid_burn': 'c0e5e30a-f113-40ab-8194-e3f70707f8d6',       # "Sizzle / Acid Burn" by bottles
    'sizzle_drops': 'a02c919b-ed2c-475c-951b-c13e53a8a23b',    # "Sizzle single drops to multiple drops" by Canakinsound
    'acid_bubbling': '61630532-3c0f-4a42-bc82-457b89732e81',   # "Acid Bubbling.wav" by spookymodem
    'factory_buzz': 'cd796a43-85d1-4eaa-9748-1e3776f64d46',    # "Factory Fluorescent light buzz" by janbezouska
    'backrooms': 'c00c3433-6530-4bb6-8b99-49d6de48c4d3',       # "Backrooms Ambience" by Resaural
    'hull_creak': '9a15a0b5-45a6-44d2-82fc-2587a54f21bf',      # "wood creaks deep door or ship hull close slow.flac" by kyles
    'door_creak': '355bd187-1c42-4f73-ac4d-01095d86e082',      # "door wood creak open slow.flac" by kyles
    'wood_creaks': '2eb447ed-b9cc-4438-98dc-1ea6411ac6ae',     # "wood creaks" by seth-m
    'pipe_basement': '9c238831-b3d0-423b-ae82-5ad21b9a38d9',   # "Metallic Pipe Hits on Concrete in Basement" by RutgerMuller
    'hollow_pipe': 'bf6e9f0b-8526-4e35-a6e0-db123f4b1a53',     # "Hollow Metal Pipe Hits" by Zott820
    'long_metal': '3ec98696-1bc9-449e-9ca2-5ab9cd577090',      # "long-metal-hit-01.wav" by newagesoup
    'creepy_whisper': 'bae2b772-a614-4a46-ad99-6367a8d4ca39',  # "Creepy Whispering" by RaspberryTickle
    'unfa_whispers': '71bda332-f6cf-4cc7-a09a-fa699a0667a2',   # "Whispers" by unfa
    'whisper_trail': 'c626cd46-e941-4611-9ba9-e197cbd48dd8',   # "whisper trail 2.ogg" by Black Boe
    'triple_knock': '6e71b761-f44d-4e5e-99eb-3a279975499c',    # "Triple Knock on Door" by Tombombadil1988
    'door_knocking': '959edf67-d4a5-4bc7-8089-e69964db8fe8',   # "Knocking on Door" by Fabrizio84
    'metal_door_knock': 'f864bf04-b7ab-409d-a3f6-bab5165d6ab8',  # "hard knocking metal door" by gamer500
    'wood_knock': '04feb626-352a-46c3-bbab-80bbbc9aa8c2',      # "Knock on Door/Wood | Knocking 3 Times on Wood.mp3" by Alex-Fullam
}


def L(src, start, length, pitch=1.0, gain=0.0, delay=0.0, hp=None, lp=None, fade_in=0.004, fade_out=0.05, reverse=False):
    return dict(src=src, start=start, length=length, pitch=pitch, gain=gain, delay=delay, hp=hp, lp=lp,
                fade_in=fade_in, fade_out=fade_out, reverse=reverse)


def C(event, layers, peak=-1.0, fade=0.1, room=None, drive=0.0, stereo=False, loop=None, lp=None, hp=None):
    return dict(event=event, layers=layers, peak=peak, fade=fade, room=room, drive=drive, stereo=stereo, loop=loop, lp=lp, hp=hp)


CUES = {
    # ---------------------------------------------------------------- the Old Man (mono, positional)
    # a wet, rattling breath, an octave of body below it
    'breath_1': C('scp106.breath', [
        L('zombie_breath', 0.15, 3.75, pitch=0.8, hp=60, lp=3500, fade_out=0.6),
        L('slow_breath', 1.0, 3.6, pitch=0.7, gain=-9.0, lp=1500, fade_out=0.8),
    ], peak=-2.0, fade=0.5, room=(0.6, 0.18)),
    'breath_2': C('scp106.breath', [
        L('zombie_breath', 8.3, 3.7, pitch=0.78, hp=60, lp=3500, fade_out=0.6),
        L('slow_breath', 5.8, 3.5, pitch=0.68, gain=-9.0, lp=1500, fade_out=0.8),
    ], peak=-2.0, fade=0.5, room=(0.6, 0.18)),
    'breath_3': C('scp106.breath', [
        L('zombie_breath', 25.15, 3.65, pitch=0.82, hp=60, lp=3500, fade_out=0.6),
        L('heavy_breath', 0.4, 3.2, pitch=0.72, gain=-10.0, lp=1800, fade_out=0.8),
    ], peak=-2.0, fade=0.5, room=(0.6, 0.18)),
    # a slow heavy step that lands in something wet
    'step_1': C('scp106.step', [
        L('steps_concrete', 6.31, 0.5, pitch=0.7, lp=2500, fade_out=0.15),
        L('squelch', 2.93, 0.35, pitch=0.8, gain=-4.0, delay=0.02, fade_out=0.12),
        L('feet_squelch', 5.0, 0.4, pitch=0.75, gain=-9.0, delay=0.05, fade_out=0.15),
    ], peak=-2.0, fade=0.12, room=(0.35, 0.12)),
    'step_2': C('scp106.step', [
        L('steps_concrete', 9.47, 0.5, pitch=0.68, lp=2500, fade_out=0.15),
        L('squelch', 7.94, 0.35, pitch=0.78, gain=-4.0, delay=0.02, fade_out=0.12),
        L('feet_squelch', 8.1, 0.4, pitch=0.72, gain=-9.0, delay=0.05, fade_out=0.15),
    ], peak=-2.0, fade=0.12, room=(0.35, 0.12)),
    'step_3': C('scp106.step', [
        L('steps_concrete', 16.10, 0.5, pitch=0.72, lp=2500, fade_out=0.15),
        L('squelch', 10.86, 0.35, pitch=0.82, gain=-4.0, delay=0.02, fade_out=0.12),
        L('feet_squelch', 11.5, 0.4, pitch=0.7, gain=-9.0, delay=0.05, fade_out=0.15),
    ], peak=-2.0, fade=0.12, room=(0.35, 0.12)),
    # the floor boils and gives him up: bubbling tar, two heavy squelches, a swell under it, a breath at the end
    'emerge': C('scp106.emerge', [
        L('mud_bubbles', 2.0, 2.2, pitch=0.6, lp=2500, fade_in=0.4, fade_out=0.8),
        L('squelch', 7.9, 0.6, pitch=0.55, delay=0.3, fade_out=0.2),
        L('squelching', 3.5, 0.5, pitch=0.6, gain=-2.0, delay=1.4, fade_out=0.2),
        L('dark_drone_amb', 5.0, 2.0, pitch=0.5, gain=-6.0, lp=600, fade_in=1.0, fade_out=1.0),
        L('slow_breath', 1.0, 2.0, pitch=0.65, gain=-8.0, delay=2.2, lp=2000, fade_out=0.6),
    ], peak=-1.0, fade=0.6, room=(0.9, 0.22)),
    # sinking back: the swell falls away, the tar closes over him
    'sink': C('scp106.sink', [
        L('mud_bubbles', 6.0, 1.8, pitch=0.55, lp=2200, fade_in=0.05, fade_out=0.9, reverse=True),
        L('squelch', 10.86, 0.5, pitch=0.5, delay=0.1, fade_out=0.2),
        L('feet_squelch', 3.0, 1.5, pitch=0.6, gain=-6.0, delay=0.2, fade_out=0.6),
        L('dark_drone_amb', 12.0, 1.6, pitch=0.45, gain=-6.0, lp=500, fade_out=1.2),
    ], peak=-1.0, fade=0.7, room=(0.9, 0.22)),
    # it closes on you: a deep wet crunch, the floor taking you, his laugh low and close
    'capture': C('scp106.capture', [
        L('squelch', 7.94, 0.6, pitch=0.5, fade_out=0.2),
        L('squelching', 0.6, 0.5, pitch=0.55, gain=-2.0, delay=0.15, fade_out=0.2),
        L('feet_squelch', 1.0, 1.4, pitch=0.6, gain=-4.0, delay=0.3, fade_out=0.5),
        L('mud_bubbles', 9.0, 1.4, pitch=0.5, gain=-5.0, delay=0.9, lp=1800, fade_out=0.7),
        L('miser_laugh', 0.4, 1.3, pitch=0.7, gain=-7.0, delay=1.0, hp=90, lp=3500, fade_out=0.5),
        L('dark_drone_amb', 15.0, 2.0, pitch=0.45, gain=-8.0, lp=500, fade_in=0.3, fade_out=1.0),
    ], peak=-1.0, fade=0.6, room=(1.1, 0.25)),
    # an old man's laugh from somewhere it should not echo
    'laugh_1': C('scp106.laugh', [
        L('miser_laugh', 0.40, 2.9, pitch=0.8, hp=80, lp=4500, fade_out=0.4),
    ], peak=-1.5, fade=0.6, room=(1.4, 0.35)),
    'laugh_2': C('scp106.laugh', [
        L('old_laugh_4', 0.2, 3.1, pitch=0.78, hp=80, lp=4500, fade_out=0.5),
    ], peak=-1.5, fade=0.6, room=(1.4, 0.35)),
    'laugh_3': C('scp106.laugh', [
        L('miser_laugh', 3.75, 3.35, pitch=0.74, hp=80, lp=4000, fade_out=0.5),
    ], peak=-1.5, fade=0.6, room=(1.6, 0.4)),
    # the surface eats itself
    'corrode_1': C('scp106.corrode', [
        L('acid_burn', 83.45, 1.5, pitch=0.85, fade_out=0.5),
        L('sizzle_drops', 91.76, 1.2, pitch=0.9, gain=-5.0, delay=0.1, fade_out=0.5),
    ], peak=-3.0, fade=0.4, room=(0.3, 0.1)),
    'corrode_2': C('scp106.corrode', [
        L('acid_burn', 28.28, 1.5, pitch=0.8, fade_out=0.5),
        L('acid_bubbling', 0.6, 1.2, pitch=0.7, gain=-4.0, delay=0.15, fade_out=0.4),
    ], peak=-3.0, fade=0.4, room=(0.3, 0.1)),
    # ---------------------------------------------------------------- the pocket dimension, one-shots (mono)
    'drip_1': C('scp106.pocket.drip', [L('drips', 4.87, 1.0, pitch=0.9, fade_out=0.4)], peak=-4.0, fade=0.3, room=(1.4, 0.4)),
    'drip_2': C('scp106.pocket.drip', [L('drips', 25.41, 1.0, pitch=0.8, fade_out=0.4)], peak=-4.0, fade=0.3, room=(1.4, 0.4)),
    'drip_3': C('scp106.pocket.drip', [L('cave_drips', 8.55, 1.0, pitch=0.85, fade_out=0.4)], peak=-4.0, fade=0.3, room=(1.2, 0.3)),
    'far_steps_1': C('scp106.pocket.far_steps', [
        L('steps_concrete', 20.0, 4.3, pitch=0.8, hp=120, lp=1200, fade_in=0.3, fade_out=1.2),
    ], peak=-4.0, fade=0.8, room=(1.6, 0.5)),
    'far_steps_2': C('scp106.pocket.far_steps', [
        L('steps_concrete', 0.9, 3.8, pitch=0.76, hp=120, lp=1000, fade_in=0.2, fade_out=1.4),
    ], peak=-4.0, fade=0.8, room=(1.6, 0.5)),
    # ---------------------------------------------------------------- the Old Man, continued
    # through the wall: the surface softens, bubbles, lets him through, a breath sucked inwards
    'phase': C('scp106.phase', [
        L('acid_bubbling', 0.5, 1.4, pitch=0.6, lp=2200, fade_in=0.2, fade_out=0.5),
        L('squelching', 2.0, 0.6, pitch=0.55, gain=-3.0, delay=0.25, fade_out=0.25),
        L('dark_drone_amb', 20.0, 1.6, pitch=0.5, gain=-5.0, lp=500, fade_in=0.6, fade_out=0.2, reverse=True),
        L('slow_breath', 1.0, 1.6, pitch=0.6, gain=-12.0, delay=0.5, lp=1500, fade_out=0.4, reverse=True),
    ], peak=-2.0, fade=0.4, room=(0.5, 0.15)),
    # ---------------------------------------------------------------- the pocket dimension, more one-shots (mono)
    'creak_1': C('scp106.pocket.creak', [L('hull_creak', 17.0, 3.6, pitch=0.75, lp=3000, fade_in=0.2, fade_out=0.8)],
                 peak=-4.0, fade=0.6, room=(1.5, 0.4)),
    'creak_2': C('scp106.pocket.creak', [L('door_creak', 0.25, 5.0, pitch=0.7, lp=3500, fade_in=0.1, fade_out=1.0)],
                 peak=-4.0, fade=0.8, room=(1.5, 0.4)),
    'creak_3': C('scp106.pocket.creak', [L('wood_creaks', 0.9, 3.0, pitch=0.65, lp=3000, fade_out=0.6)],
                 peak=-4.0, fade=0.5, room=(1.8, 0.45)),
    # a pipe struck somewhere far off, the ring carried down the corridors
    'metal_1': C('scp106.pocket.metal', [L('pipe_basement', 0.2, 4.0, pitch=0.7, lp=3500, fade_out=1.2)],
                 peak=-4.0, fade=0.8, room=(2.2, 0.55)),
    'metal_2': C('scp106.pocket.metal', [
        L('hollow_pipe', 0.6, 1.6, pitch=0.6, lp=3000, fade_out=0.8),
        L('long_metal', 0.35, 3.5, pitch=0.5, gain=-9.0, delay=0.05, lp=1500, fade_out=1.5),
    ], peak=-4.0, fade=0.8, room=(2.2, 0.55)),
    'metal_3': C('scp106.pocket.metal', [L('long_metal', 0.35, 4.5, pitch=0.62, lp=2500, fade_out=2.0)],
                 peak=-5.0, fade=1.0, room=(2.4, 0.6)),
    # voices that are not quite words, from the end of the corridor
    'whisper_1': C('scp106.pocket.whisper', [L('creepy_whisper', 0.55, 3.4, pitch=0.85, hp=200, lp=6000, fade_in=0.2, fade_out=0.6)],
                   peak=-4.0, fade=0.5, room=(1.6, 0.45)),
    'whisper_2': C('scp106.pocket.whisper', [L('unfa_whispers', 5.5, 4.0, pitch=0.8, hp=200, lp=5000, fade_in=0.4, fade_out=0.8)],
                   peak=-4.0, fade=0.6, room=(1.6, 0.45)),
    'whisper_3': C('scp106.pocket.whisper', [L('whisper_trail', 0.8, 2.6, pitch=0.75, hp=200, lp=5000, fade_in=0.3, fade_out=0.5, reverse=True)],
                   peak=-4.0, fade=0.5, room=(1.8, 0.5)),
    # someone on the other side of a door that leads nowhere
    'knock_1': C('scp106.pocket.knock', [L('triple_knock', 0.25, 1.1, pitch=0.8, lp=4000, fade_out=0.3)],
                 peak=-3.0, fade=0.5, room=(1.2, 0.35)),
    'knock_2': C('scp106.pocket.knock', [L('door_knocking', 0.1, 2.3, pitch=0.72, lp=3500, fade_out=0.5)],
                 peak=-3.0, fade=0.5, room=(1.2, 0.35)),
    'knock_3': C('scp106.pocket.knock', [L('metal_door_knock', 0.05, 1.6, pitch=0.7, lp=4000, fade_out=0.5)],
                 peak=-3.0, fade=0.5, room=(1.4, 0.4)),
    # cold air through a seam that should not be there: a long exhale turned into wind
    'draft_1': C('scp106.pocket.draft', [
        L('slow_breath', 1.0, 3.6, pitch=0.42, hp=150, lp=1400, fade_in=0.6, fade_out=1.2),
        L('cave_loop', 4.0, 4.0, pitch=0.8, gain=-8.0, hp=300, lp=4000, fade_in=0.8, fade_out=1.2),
    ], peak=-5.0, fade=1.0, room=(1.2, 0.4)),
    'draft_2': C('scp106.pocket.draft', [
        L('heavy_breath', 0.4, 3.2, pitch=0.4, hp=150, lp=1300, fade_in=0.6, fade_out=1.2),
        L('cave_loop', 12.0, 4.0, pitch=0.8, gain=-8.0, hp=300, lp=4000, fade_in=0.8, fade_out=1.2),
    ], peak=-5.0, fade=1.0, room=(1.2, 0.4)),
    # a sigil taken: a struck bowl of metal, its ring swelled backwards into itself
    'sigil': C('scp106.pocket.sigil', [
        L('long_metal', 0.35, 4.5, pitch=0.8, lp=6000, fade_out=2.0),
        L('long_metal', 0.6, 2.5, pitch=0.8, gain=-8.0, lp=4000, fade_in=1.0, fade_out=0.05, reverse=True),
        L('dark_drone_1', 40.0, 3.0, pitch=1.2, gain=-14.0, lp=3000, fade_in=0.5, fade_out=1.5),
    ], peak=-2.0, fade=1.2, room=(2.0, 0.45)),
    # the breach opens: the walls grind, a deep rumble, metal groaning
    'breach': C('scp106.pocket.breach', [
        L('dark_drone_amb', 20.0, 2.5, pitch=0.4, lp=400, fade_in=0.3, fade_out=1.5),
        L('hull_creak', 30.0, 4.0, pitch=0.5, gain=-4.0, lp=1500, fade_in=0.2, fade_out=1.5),
        L('mud_bubbles', 12.0, 2.0, pitch=0.4, gain=-6.0, lp=1200, delay=0.5, fade_out=1.0),
        L('long_metal', 0.4, 3.5, pitch=0.4, gain=-6.0, lp=1200, delay=0.4, fade_out=2.0),
    ], peak=-1.0, fade=1.2, room=(1.8, 0.4), drive=0.3),
    # something right behind you
    'scare': C('scp106.pocket.scare', [
        L('miser_laugh', 0.4, 1.8, pitch=0.6, hp=80, lp=4000, fade_in=0.4, fade_out=0.05, reverse=True),
        L('hollow_pipe', 0.6, 1.2, pitch=0.45, gain=-2.0, delay=1.15, lp=2500, fade_out=0.8),
        L('squelch', 7.94, 0.5, pitch=0.5, gain=-3.0, delay=1.15, fade_out=0.2),
        L('zombie_breath', 0.15, 1.8, pitch=0.7, gain=-6.0, delay=1.3, lp=3000, fade_out=0.6),
    ], peak=-1.0, fade=0.6, room=(1.0, 0.3), drive=0.25),
    # out: the drone swells backwards and lets go, the tar giving you up
    'escape': C('scp106.pocket.escape', [
        L('dark_drone_4', 30.0, 2.5, pitch=0.8, lp=2500, fade_in=1.2, fade_out=0.1, reverse=True),
        L('mud_bubbles', 2.0, 2.0, pitch=0.6, gain=-5.0, lp=2500, fade_in=1.0, fade_out=0.1, reverse=True),
        L('long_metal', 0.35, 3.5, pitch=1.0, gain=-10.0, delay=2.6, lp=7000, fade_out=2.0),
        L('slow_breath', 5.8, 2.5, pitch=0.9, gain=-6.0, delay=2.8, lp=4000, fade_out=1.0),
    ], peak=-1.5, fade=1.5, room=(1.4, 0.35)),
    # a door that will not open: the handle tried, wood rattling in its frame
    'locked': C('scp106.pocket.locked', [
        L('wood_knock', 0.25, 0.7, pitch=1.1, lp=5000, fade_out=0.2),
        L('door_creak', 6.3, 0.6, pitch=1.0, gain=-6.0, delay=0.15, lp=4000, fade_out=0.3),
        L('metal_door_knock', 0.6, 0.5, pitch=1.3, gain=-10.0, delay=0.05, lp=5000, fade_out=0.3),
    ], peak=-3.0, fade=0.4, room=(0.6, 0.2)),
    # a key: small metal, picked up off stone
    'key': C('scp106.pocket.key', [
        L('hollow_pipe', 0.6, 0.6, pitch=2.2, hp=600, lp=9000, fade_out=0.3),
        L('long_metal', 0.4, 1.6, pitch=2.0, gain=-6.0, delay=0.05, hp=800, fade_out=0.8),
    ], peak=-3.0, fade=0.6, room=(0.8, 0.25)),
    # the architecture rearranges itself behind your back
    'shift': C('scp106.pocket.shift', [
        L('hull_creak', 33.0, 4.0, pitch=0.55, lp=2000, fade_in=0.3, fade_out=1.5),
        L('pipe_basement', 0.2, 3.0, pitch=0.5, gain=-5.0, delay=0.6, lp=1500, fade_out=1.5),
        L('dark_drone_amb', 22.0, 3.5, pitch=0.45, gain=-6.0, lp=500, fade_in=1.0, fade_out=1.5),
    ], peak=-2.0, fade=1.2, room=(2.0, 0.5)),
    # ---------------------------------------------------------------- ambience beds (stereo, seamless loops)
    'hum': C('scp106.pocket.hum', [
        L('dark_drone_1', 10.0, 20.5, pitch=0.85, lp=2000, fade_in=0.01, fade_out=0.01),
        L('factory_buzz', 20.0, 12.0, pitch=0.5, gain=-14.0, hp=60, lp=900, fade_in=0.01, fade_out=0.01),
        L('backrooms', 0.0, 19.9, pitch=0.9, gain=-15.0, lp=2500, fade_in=0.01, fade_out=0.01),
    ], peak=-6.0, stereo=True, loop=(20.0, 2.0)),
    'void': C('scp106.pocket.void', [
        L('dark_drone_4', 20.0, 17.5, pitch=0.7, lp=900, fade_in=0.01, fade_out=0.01),
        L('dark_drone_amb', 0.0, 15.0, pitch=0.6, gain=-6.0, lp=700, fade_in=0.01, fade_out=0.01),
    ], peak=-6.0, stereo=True, loop=(22.0, 3.0)),
    'water': C('scp106.pocket.water', [
        L('cave_loop', 0.0, 23.0, lp=6000, fade_in=0.01, fade_out=0.01),
        L('cave_drips', 20.0, 23.0, gain=-4.0, fade_in=0.01, fade_out=0.01),
        L('drip_echo', 10.0, 23.0, gain=-10.0, lp=5000, fade_in=0.01, fade_out=0.01),
    ], peak=-6.0, stereo=True, loop=(20.0, 2.0)),
}


def raw_path(src):
    uuid = SOURCES[src]
    for ext in ('mp3', 'ogg', 'wav', 'flac'):
        p = os.path.join(RAW, f"{uuid}.{ext}")
        if os.path.exists(p):
            return p
    sys.exit(f"Missing source {src} ({uuid}); run: python3 {os.path.relpath(HELPER, ROOT)} download {uuid}")


def render(spec, channels):
    # varispeed first (the cut is measured in source seconds), then the trim of the stretched audio
    chain = [f"atrim=start={spec['start']}:duration={spec['length']}", "asetpts=PTS-STARTPTS",
             f"asetrate={SR * spec['pitch']:.0f}", f"aresample={SR}"]
    if spec['reverse']:
        chain.append("areverse")
    if spec['hp']:
        chain.append(f"highpass=f={spec['hp']}")
    if spec['lp']:
        chain.append(f"lowpass=f={spec['lp']}")
    b = subprocess.run(['ffmpeg', '-v', 'error', '-i', raw_path(spec['src']), '-ac', str(channels), '-ar', str(SR), '-af', ','.join(chain),
                        '-f', 'f32le', '-'], capture_output=True, check=True).stdout
    x = np.frombuffer(b, np.float32).astype(np.float64).reshape(-1, channels)
    n = len(x)
    fo = min(n, int(spec['fade_out'] * SR))
    if fo > 0:
        x[n - fo:] *= np.linspace(1.0, 0.0, fo)[:, None]
    fi = min(n, max(64, int(spec['fade_in'] * SR)))
    x[:fi] *= np.linspace(0.0, 1.0, fi)[:, None]
    return x * 10 ** (spec['gain'] / 20.0)


def room(y, seconds, wet, seed=106):
    """A dark synthetic room: exponentially decaying, low-passed noise impulse response (one per channel)."""
    n = int(seconds * SR)
    r = np.random.default_rng(seed)
    t = np.arange(n) / SR
    out = np.zeros((len(y) + n, y.shape[1]))
    for c in range(y.shape[1]):
        ir = r.standard_normal(n) * np.exp(-t * 6.9 / seconds)
        ir = np.convolve(ir, np.ones(6) / 6.0, mode='same')  # soften the top end
        ir[0] = 0.0
        ir /= np.sqrt((ir ** 2).sum()) + 1e-12
        m = 1 << int(np.ceil(np.log2(len(y) + n)))
        wetc = np.fft.irfft(np.fft.rfft(y[:, c], m) * np.fft.rfft(ir, m), m)[:len(y) + n]
        out[:, c] = wetc * wet
        out[:len(y), c] += y[:, c] * (1.0 - wet * 0.5)
    return out


def mix(name):
    cue = CUES[name]
    ch = 2 if cue['stereo'] else 1
    parts = [(int(l['delay'] * SR), render(l, ch)) for l in cue['layers']]
    n = max(d + len(x) for d, x in parts)
    y = np.zeros((n, ch))
    for d, x in parts:
        y[d:d + len(x)] += x
    if cue['room']:
        y = room(y, *cue['room'])
    if cue['hp']:
        y = y - _smooth(y, cue['hp'])
    if cue['lp']:
        y = _smooth(y, cue['lp'])
    if cue['loop']:
        length, xf = cue['loop']
        N, X = int(length * SR), int(xf * SR)
        if len(y) < N + X:
            sys.exit(f"{name}: loop needs {length + xf:.1f} s of material, has {len(y) / SR:.1f} s")
        k = np.linspace(0.0, 1.0, X)[:, None]
        out = y[:N].copy()
        out[:X] = y[:X] * np.sqrt(k) + y[N:N + X] * np.sqrt(1.0 - k)
        y = out
    else:
        f = min(len(y), int(cue['fade'] * SR))
        y[len(y) - f:] *= (np.linspace(1.0, 0.0, f) ** 1.5)[:, None]
        # trim trailing near-silence of the room tail
        env = np.abs(y).max(axis=1)
        last = np.nonzero(env > 10 ** (-60 / 20.0) * env.max())[0]
        if len(last):
            y = y[:last[-1] + 1]
    if cue['drive'] > 0.0:
        y /= max(1e-9, np.abs(y).max())
        y = np.tanh(cue['drive'] * y) / np.tanh(cue['drive'])
    return y * (10 ** (cue['peak'] / 20.0) / max(1e-9, np.abs(y).max()))


def _smooth(y, fc):
    """One-pole low-pass via FFT (zero phase): gentle master tone control."""
    m = 1 << int(np.ceil(np.log2(len(y) + 1)))
    f = np.fft.rfftfreq(m, 1.0 / SR)
    h = 1.0 / np.sqrt(1.0 + (f / fc) ** 4)
    out = np.zeros_like(y)
    for c in range(y.shape[1]):
        out[:, c] = np.fft.irfft(np.fft.rfft(y[:, c], m) * h, m)[:len(y)]
    return out


def write_wav(y, path):
    with wave.open(path, 'wb') as w:
        w.setnchannels(y.shape[1])
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(y, -1, 1) * 32767).astype('<i2').tobytes())


SOUNDS = os.path.join(ROOT, 'viltrumitecore', 'src', 'main', 'binassets', 'assets', 'viltrumitecore', 'sounds', 'scp106')
OVERLAY = os.path.join(ROOT, 'viltrumitecore', 'src', 'main', 'sound-overlays', 'scp106.json')
SOURCES_MD = os.path.join(ROOT, 'tools', 'assets', 'scp106_sources.md')
PART_BYTES = 48000  # one GitHub push request carries ~60 KB; build.gradle joins <name>.ogg.b64.part0..N


def split_parts(name):
    """Split a packaged .ogg.b64 larger than PART_BYTES at line boundaries into .ogg.b64.part0..N."""
    path = os.path.join(SOUNDS, name + '.ogg.b64')
    for old in os.listdir(SOUNDS):
        if old.startswith(name + '.ogg.b64.part'):
            os.remove(os.path.join(SOUNDS, old))
    if os.path.getsize(path) <= PART_BYTES:
        return
    lines = open(path).read().splitlines(keepends=True)
    parts, cur = [], ''
    for line in lines:
        if cur and len(cur) + len(line) > PART_BYTES:
            parts.append(cur)
            cur = ''
        cur += line
    parts.append(cur)
    for i, text in enumerate(parts):
        with open(f"{path}.part{i}", 'w') as f:
            f.write(text)
    os.remove(path)


def write_overlay():
    """sound-overlays/scp106.json: one event per cue group; the three beds stream."""
    import json
    events = {}
    for name, cue in CUES.items():
        ev = cue['event']
        entry = events.setdefault(ev, {
            'category': 'ambient' if ev.startswith('scp106.pocket.') else 'hostile',
            'subtitle': 'subtitles.viltrumitecore.' + ev, 'sounds': []})
        sound = {'name': 'viltrumitecore:scp106/' + name}
        if cue['loop']:
            sound['stream'] = True
        entry['sounds'].append(sound)
    with open(OVERLAY, 'w') as f:
        json.dump(events, f, indent=2)
        f.write('\n')


def write_sources():
    """tools/assets/scp106_sources.md from the cached Openverse metadata of every source."""
    import json
    meta_dir = os.path.join(os.path.dirname(RAW), 'metadata')
    rows = []
    for key, uuid in SOURCES.items():
        item = json.load(open(os.path.join(meta_dir, uuid + '.json')))['openverse']
        assert (item.get('license') or '').lower() == 'cc0', key
        used = sorted({n for n, c in CUES.items() if any(l['src'] == key for l in c['layers'])})
        rows.append(f"| `{key}` | [{item['foreign_landing_url'].rstrip('/').split('/')[-1]}]({item['foreign_landing_url']}) | "
                    f"{item.get('creator') or '?'} | {item.get('title') or '?'} | CC0 1.0 | {', '.join('`' + u + '`' for u in used)} |")
    head = (
        "# SCP-106 sound sources\n\n"
        "All 25 SCP-106 sound events (`scp106.*`, `scp106.pocket.*`; 44 files under\n"
        "`viltrumitecore/src/main/binassets/assets/viltrumitecore/sounds/scp106/`) are mixed by `tools/sfx/scp106.py`\n"
        "from the CC0 1.0 Freesound recordings below, found through Openverse. No vanilla Minecraft sound and no\n"
        "SCP – Containment Breach audio is used. Every license is CC0 1.0\n"
        "(https://creativecommons.org/publicdomain/zero/1.0/), checked by the download helper's license gate against the\n"
        "Openverse record that points at the Freesound page. The HQ preview MP3s are downloaded with\n"
        "`python3 .agents/skills/sound-design/scripts/openverse_sfx.py download <uuid>` (uuids in `SOURCES` of the\n"
        "script) and never committed.\n\n"
        "Processing (all in `tools/sfx/scp106.py`, ffmpeg + numpy): segment cut, varispeed (mostly 0.4-0.9x, lower and\n"
        "slower), occasional reversal, high/low-pass, layering, a synthetic decaying-noise room, soft saturation, fades,\n"
        "peak normalisation; the three beds (`hum`, `void`, `water`) are stereo loops with the tail crossfaded into the\n"
        "head. Encoded as 44.1 kHz Ogg Vorbis q5 by the sound-design helper.\n\n"
        "| Key | Freesound | Creator | Original title | License | Used in |\n|---|---|---|---|---|---|\n")
    with open(SOURCES_MD, 'w') as f:
        f.write(head + '\n'.join(rows) + '\n')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', help='comma-separated cue names')
    ap.add_argument('--no-package', action='store_true')
    ap.add_argument('--out', default=os.path.join(tempfile.gettempdir(), 'scp106_sfx'))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    for name in (a.only.split(',') if a.only else CUES):
        cue = CUES[name]
        y = mix(name)
        wav = os.path.join(a.out, name + '.wav')
        write_wav(y, wav)
        rms = 20 * np.log10(np.sqrt((y ** 2).mean()) + 1e-12)
        print(f"{name}: {len(y) / SR:.2f} s, {y.shape[1]} ch, RMS {rms:.1f} dBFS -> {wav}")
        if not a.no_package:
            cmd = [sys.executable, HELPER, 'package', '--input', wav, '--module', 'core', '--name', 'scp106/' + name,
                   '--event', cue['event'].replace('.', '_'), '--replace']
            if cue['stereo']:
                cmd.append('--stereo')
            for src in sorted({l['src'] for l in cue['layers']}):
                cmd += ['--source-id', SOURCES[src]]
            subprocess.run(cmd, cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
            split_parts(name)
    if not a.only:
        write_overlay()
        write_sources()


if __name__ == '__main__':
    main()
