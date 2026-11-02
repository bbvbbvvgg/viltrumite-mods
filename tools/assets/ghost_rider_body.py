#!/usr/bin/env python3
"""Ghost Rider's body (own pixel art, CC0): Johnny Blaze's skin, the five burn-away frames and the Rider's skin, an
emissive overlay for the burning cracks, and the 3D gear (the skull with a hinged jaw and glowing sockets, jacket
collar, spiked shoulder pads, the chest chain, belt buckle, boot straps and heel spurs).

Skins: standard 64x64 Steve layout painted HD 128x128 (2 texels per model pixel; vanilla UVs stay in 64-space).
  skin_0 = Johnny (black leather biker jacket over a charcoal tee, belt, black leather trousers, black boots, brown hair, stubble)
  skin_1..5 = the transformation (GhostRiderRules.burnFrame): flush, glowing cracks, charred flesh, bone breaking
              through, the last strips of flesh; skin_6 = the Rider (head cube empty: the 3D skull replaces it,
              skeletal hands, a charred ribcage glowing through the open jacket).
  skin_glow_1..6 = emissive overlay on geo/ghost_rider/skin_glow.geo.json (vanilla boxes inflated a hair, base and
              outer layer), drawn with RenderType.eyes: cracks, glowing eyes, embers between the ribs and bones.
Gear: geo/ghost_rider/rider_gear.geo.json (+ texture, glow). Bones under the six armour bones; GhostRiderGear hides
  skull/jaw, r_pad/l_pad, chest_chain, r_spur/l_spur for Johnny, the chest chain while the chain is in his hand,
  and opens the jaw. Geo coordinates (px): y up from the feet, -x = the player's right side, front = -z.
Chain: textures/entity/ghost_rider/chain.png, one 8 x 16 link (GhostRiderChains draws two crossed quads per link).

Run: PYTHONHASHSEED=0 python3 tools/assets/ghost_rider_body.py [--preview]
"""
import math
import os
import random
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from jinwoo_geo import BIN, Face, Mat as FlatMat, Model, Cube, put, write_png, write_json, RES  # noqa: E402
from clean_geo import CleanModel, write_model  # noqa: E402
from pixel_shade import Mat  # noqa: E402

D = 2
FRAMES = 6
OUT = 'textures/entity/ghost_rider/'
GLOW_GEO = 'geo/ghost_rider/skin_glow.geo.json'
GEAR_GEO = 'geo/ghost_rider/rider_gear.geo.json'
GEAR_TEX = OUT + 'rider_gear.png'
GEAR_GLOW = OUT + 'rider_gear_glow.png'
CHAIN = OUT + 'chain.png'

SKIN, SKIN_LO, SKIN_HI = '#c8936b', '#a3714f', '#dcaa84'
HAIR, HAIR_HI = '#3a2617', '#5a3d26'
LEATHER, LEATHER_HI, LEATHER_LO = '#17171a', '#34343b', '#0b0b0d'
TEE = '#3a3a40'
DENIM, DENIM_HI, DENIM_LO = '#1c1c21', '#3b3b44', '#121216'   # black leather trousers (names kept)
BOOT, SOLE = '#121214', '#060607'
SILVER = '#a9adb6'
BONE, BONE_LO = '#ddd2b6', '#a99c7e'
CHAR = '#1b0f0a'
EMBER, EMBER_HOT, EMBER_RED = (255, 140, 30), (255, 226, 120), (220, 50, 12)

# burn-away schedule by frame 0..6
FLUSH = [0.0, 0.45, 0.30, 0.10, 0.0, 0.0, 0.0]
CHARRED = [0.0, 0.0, 0.35, 0.82, 0.9, 1.0, 1.0]
BONED = [0.0, 0.0, 0.0, 0.08, 0.45, 0.8, 1.0]
CRACK = [0.0, 0.05, 0.08, 0.11, 0.10, 0.08, 0.05]
HAIR_LEFT = [1.0, 0.85, 0.45, 0.0, 0.0, 0.0, 0.0]
EYE = [0.0, 0.55, 0.85, 1.0, 1.0, 1.0, 1.0]
SCORCH = [0.0, 0.0, 0.08, 0.18, 0.28, 0.36, 0.42]


def hexf(c):
    c = c.lstrip('#')
    return np.array([int(c[i:i + 2], 16) for i in (0, 2, 4)], float)


def mix(rgb, mask, colour, amount):
    """rgb[mask] -> colour by amount (array or scalar)."""
    c = hexf(colour) if isinstance(colour, str) else np.array(colour, float)
    a = np.broadcast_to(np.asarray(amount, float), mask.shape) * mask
    rgb[:] = rgb * (1 - a[..., None]) + c * a[..., None]


# ------------------------------------------------------------------------------------------------ fields
_RS = np.random.RandomState(4242)
_DIRS = _RS.normal(size=(5, 3))
_DIRS /= np.linalg.norm(_DIRS, axis=1, keepdims=True)
_PH = _RS.uniform(0, 2 * math.pi, 5)
_FREQ = np.array([0.85, 1.1, 1.45, 0.7, 1.9])


def field(x, y, z, k=0):
    """Smooth pseudo-noise over model space (continuous across faces): sum of oblique waves."""
    n = np.zeros_like(x, dtype=float)
    for i in range(5):
        d = _DIRS[(i + k) % 5]
        n += np.sin((x * d[0] + y * d[1] + z * d[2]) * _FREQ[i] * (1.0 + 0.3 * k) + _PH[i] + k)
    return n / 2.2


def cracks(face, width):
    if width <= 0:
        return np.zeros((face.h, face.w), bool)
    x, y, z = face.world()
    return np.abs(field(x, y, z, 0) + 0.5 * field(x * 1.9, y * 1.9, z * 1.9, 2)) < width


def patches(face, amount, k=1):
    """Patches growing with amount 0..1 (bone breaking through)."""
    if amount <= 0:
        return np.zeros((face.h, face.w), bool)
    x, y, z = face.world()
    n = field(x * 0.6, y * 0.6, z * 0.6, k)
    return n > (1.2 - 2.4 * amount)


# ------------------------------------------------------------------------------------------------ skin painters
class Painter:
    def __init__(self, frame):
        self.f = frame

    # ---- flesh that burns away (face, neck, hands)
    def flesh(self, face, rgb, region, bone_mask=None):
        f = self.f
        mix(rgb, region, '#9e3a20', FLUSH[f])
        noise = (_RS.uniform(0, 1, rgb.shape[:2]) > 0.5)
        mix(rgb, region, CHAR, CHARRED[f] * np.where(noise, 1.0, 0.85))
        if bone_mask is not None:
            mix(rgb, region & bone_mask, BONE, 1.0)
            mix(rgb, region & bone_mask & (_RS.uniform(0, 1, rgb.shape[:2]) > 0.75), BONE_LO, 0.5)
        cr = cracks(face, CRACK[f]) & region
        if bone_mask is not None:
            cr &= ~bone_mask
        mix(rgb, cr, EMBER, 0.9)

    # ---- head (base)
    def head(self, face, rgb, glow, rs):
        f = self.f
        x, y, z = face.world()
        all_ = np.ones((face.h, face.w), bool)
        hair = HAIR_LEFT[f]
        hair_col = hexf(HAIR) * hair + hexf('#151110') * (1 - hair)
        if face.name == 'down':
            self.flesh(face, rgb, all_)
            return
        if face.name == 'up':
            rgb[:] = hair_col if hair > 0 else rgb
            if hair <= 0:
                self.flesh(face, rgb, all_, patches(face, BONED[f]))
            return
        if face.name == 'south':
            hairline = y > 24.8
            if hair > 0:
                rgb[hairline] = hair_col
                mix(rgb, hairline & (rs.uniform(0, 1, rgb.shape[:2]) > 0.6), HAIR_HI, 0.35 * hair)
            self.flesh(face, rgb, ~hairline if hair > 0 else all_, patches(face, BONED[f]))
            return
        if face.name in ('east', 'west'):
            hairline = (y > 28.6) | ((z > 1.2) & (y > 25.2)) | ((np.abs(z + 0.2) < 0.8) & (y > 26.0))
            mix(rgb, y < 26.0, '#6b4c3a', 0.25)                         # stubble along the jaw
            if hair > 0:
                rgb[hairline] = hair_col
            region = ~hairline if hair > 0 else all_
            self.flesh(face, rgb, region, patches(face, BONED[f]))
            ear = (np.abs(z - 0.6) < 0.7) & (np.abs(y - 27.2) < 0.9)
            mix(rgb, ear & region, SKIN_LO, 0.5 * (1 - CHARRED[f]))
            return
        # north: the face
        ax = np.abs(x)
        top = y > 29.8
        if hair > 0:
            rgb[top] = hair_col
            fringe = (y > 29.2) & (np.sin(x * 2.3) > 0.2)
            rgb[fringe] = hair_col
        brow = (ax > 0.6) & (ax < 3.3) & (np.abs(y - (28.85 + (ax - 0.6) * 0.1)) < 0.32)
        mix(rgb, brow, '#2a1a10', 0.95)
        band = (ax > 0.9) & (ax < 3.2)
        mix(rgb, band & (np.abs(y - 28.15) < 0.22), '#1c120c', 1.0)          # lash line
        eye = band & (y > 27.1) & (y < 27.95)
        iris = (ax > 1.35) & (ax < 2.35) & (y > 27.1) & (y < 27.95)
        mix(rgb, eye & ~iris, '#e6ded2', 0.9)
        mix(rgb, iris, '#5a3a22', 1.0)
        mix(rgb, (ax > 1.65) & (ax < 2.05) & (y > 27.3) & (y < 27.75), '#1c120c', 0.9)
        mix(rgb, band & (np.abs(y - 26.95) < 0.12), SKIN_LO, 0.5)
        mix(rgb, (ax < 0.45) & (y > 25.9) & (y < 27.1), SKIN_LO, 0.45)            # nose
        mix(rgb, (ax < 0.6) & (np.abs(y - 25.95) < 0.15), '#8a5c44', 0.6)
        mix(rgb, (ax < 1.3) & (np.abs(y - 25.15) < 0.18), '#7a4a3a', 0.85)        # mouth
        mix(rgb, (ax < 1.5) & (np.abs(y - 24.85) < 0.12), SKIN_HI, 0.25)
        stubble = (y < 26.3) & ~((ax < 1.3) & (np.abs(y - 25.15) < 0.2))
        mix(rgb, stubble & (rs.uniform(0, 1, rgb.shape[:2]) > 0.35), '#6b4c3a', 0.35)
        mix(rgb, (ax > 3.0) & (y < 26.6), SKIN_LO, 0.3)
        region = ~top if hair > 0 else np.ones_like(top)
        bones = patches(face, BONED[f])
        if f >= 4:
            # cheekbones, brow and teeth break through first
            bones |= (ax < 3.4) & (np.abs(y - 26.4) < 0.5) & (ax > 1.2)
            bones |= (np.abs(y - 29.0) < 0.5) & (ax < 3.4)
            bones |= (ax < 1.6) & (np.abs(y - 25.0) < 0.45)
        self.flesh(face, rgb, region, bones)
        if f >= 1:
            sock = eye if f < 4 else ((ax > 0.8) & (ax < 3.2) & (y > 26.8) & (y < 28.6))
            mix(rgb, sock, '#ffcf6a' if f < 3 else '#ff9a2a', 1.0)
        if f >= 4:
            teeth = (ax < 1.5) & (np.abs(y - 25.0) < 0.42)
            mix(rgb, teeth & (np.floor((x + 3) * 2) % 2 == 0), '#4a2a1a', 0.7)
            nose = (ax < 0.5 - (26.9 - y) * 0.1) & (y > 25.9) & (y < 26.9)
            mix(rgb, nose, '#1a0a06', 1.0)

    def head_alpha(self, face, rs):
        a = np.full((face.h, face.w), 255, np.uint8)
        if self.f >= 5:
            a[:] = 0
        return a

    # ---- hat: hair volume
    def hat(self, face, rgb, glow, rs):
        hair = HAIR_LEFT[self.f]
        rgb[:] = hexf(HAIR) * hair + hexf('#141010') * (1 - hair)
        for _ in range(max(2, face.w // 3)):
            c = rs.randint(0, face.w)
            mix(rgb, np.zeros(rgb.shape[:2], bool) | (np.arange(face.w)[None, :] == c), HAIR_HI, 0.35 * hair)
        if self.f >= 1:
            singe = rs.uniform(0, 1, rgb.shape[:2]) > (0.9 - 0.3 * self.f)
            mix(rgb, singe, EMBER, 0.6)

    def hat_alpha(self, face, rs):
        x, y, z = face.world()
        a = np.zeros((face.h, face.w), np.uint8)
        hair = HAIR_LEFT[self.f]
        if hair <= 0:
            return a
        if face.name == 'up':
            a[:] = 255
        elif face.name == 'south':
            edge = 25.2 + rs.uniform(0, 1.0, face.w)
            a[y > edge[None, :]] = 255
        elif face.name in ('east', 'west'):
            edge = 28.4 - np.clip(z, 0, 4) * 0.8 + rs.uniform(-0.3, 0.4, face.w)[None, :]
            a[y > edge] = 255
        elif face.name == 'north':
            strands = np.repeat(rs.uniform(29.6, 31.4, face.w // 3 + 1), 3)[:face.w]
            a[y > strands[None, :]] = 255
        if hair < 1.0:
            a[rs.uniform(0, 1, a.shape) > hair] = 0
        return a

    # ---- torso
    def tee(self, face, rgb, glow, rs):
        f = self.f
        x, y, z = face.world()
        rgb[:] = hexf(TEE) + rs.uniform(-4, 4, rgb.shape[:2])[..., None]
        belt = (y > 12.0) & (y < 12.95)
        mix(rgb, belt, '#0e0e10', 1.0)
        mix(rgb, belt & (np.abs(y - 12.48) < 0.12), '#2a2a30', 0.8)
        if face.name == 'north':
            mix(rgb, belt & (np.abs(x) < 0.9), SILVER, 1.0)
            mix(rgb, belt & (np.abs(x) < 0.45) & (np.abs(y - 12.48) < 0.25), '#55585f', 1.0)
            mix(rgb, (y > 22.4) & (np.abs(x) < 1.6) & ~belt, '#2e2e33', 0.7)        # collar shadow
        if f >= 3:
            burnt = patches(face, min(1.0, (f - 2) * 0.35), 3) & ~belt
            mix(rgb, burnt, CHAR, 0.9)
        if f >= 5 and face.name == 'north':
            self.ribs(face, rgb, x, y, belt)

    def ribs(self, face, rgb, x, y, belt):
        open_ = (np.abs(x) < 1.75) & (y > 13.2) & (y < 23.6)
        rgb[open_] = hexf('#160805')
        ribs = np.zeros_like(open_)
        for ry in (22.3, 20.9, 19.5, 18.1, 16.7, 15.3):
            curve = ry - (np.abs(x) ** 2) * 0.12
            ribs |= np.abs(y - curve) < 0.33
        sternum = (np.abs(x) < 0.32) & (y > 17.0)
        bones = open_ & (ribs | sternum)
        rgb[bones] = hexf('#cdbf9f')
        mix(rgb, bones & (np.abs(x) > 1.0), '#6b5a44', 0.55)
        spine = open_ & (np.abs(x) < 0.4) & (y < 17.0)
        rgb[spine] = hexf(BONE_LO)

    def jacket(self, face, rgb, glow, rs):
        f = self.f
        x, y, z = face.world()
        rgb[:] = hexf(LEATHER) + (rs.uniform(-3, 3, rgb.shape[:2]))[..., None]
        if face.name in ('north', 'south', 'east', 'west'):
            mix(rgb, np.abs(y - 21.6) < 0.18, LEATHER_HI, 0.45)                       # shoulder yoke seam
            sheen = (np.sin(x * 1.3 + y * 0.6) > 0.82)
            mix(rgb, sheen, LEATHER_HI, 0.35)
        if face.name == 'north':
            edge = (np.abs(x) >= 1.3) & (np.abs(x) < 1.75)
            mix(rgb, edge, LEATHER_HI, 0.55)                                          # lapel edges
            zip_ = (np.abs(x + 1.5) < 0.16) & (y < 21.0)
            mix(rgb, zip_, SILVER, 0.9)
            mix(rgb, zip_ & (np.floor(y * 4) % 2 == 0), '#5c606a', 0.8)
            lapel = (np.abs(np.abs(x) - (1.75 + (23.6 - y) * 0.55)) < 0.22) & (y > 20.0)
            mix(rgb, lapel, LEATHER_HI, 0.6)
            pocket = (np.abs((x - 2.6) - (y - 16.5) * 0.5) < 0.14) & (y > 15.0) & (y < 18.0) & (x > 1.8)
            mix(rgb, pocket, SILVER, 0.8)
            mix(rgb, (y < 14.0) & (y > 13.2), LEATHER_HI, 0.25)                       # waist band
        if face.name == 'south':
            mix(rgb, (np.abs(x) < 0.15) & (y < 21.4), LEATHER_LO, 0.6)
            mix(rgb, (y < 14.0) & (y > 13.2), LEATHER_HI, 0.25)
        if face.name == 'up':
            mix(rgb, np.ones(rgb.shape[:2], bool), LEATHER_HI, 0.25)
        if SCORCH[f] > 0:
            hem = (y < 13.2 + 3.5 * SCORCH[f]) | ((face.name == 'north') & (np.abs(x) < 1.75 + 2.0 * SCORCH[f]))
            burnt = hem & patches(face, 0.4 + SCORCH[f], 4)
            mix(rgb, burnt, '#0a0706', 0.9)
            mix(rgb, burnt & cracks(face, 0.12), EMBER, 0.6)

    def jacket_alpha(self, face, rs):
        x, y, z = face.world()
        a = np.full((face.h, face.w), 255, np.uint8)
        a[y < 13.2] = 0
        if face.name == 'north':
            a[(np.abs(x) < 1.3) & (y >= 13.2)] = 0
        if face.name in ('down',):
            a[:] = 0
        return a

    # ---- arms
    def arm(self, face, rgb, glow, rs):
        f = self.f
        x, y, z = face.world()
        rgb[:] = hexf(LEATHER)
        hand = y < 15.0
        rgb[hand] = hexf(SKIN)
        mix(rgb, hand & (y > 14.6), SKIN_LO, 0.5)
        if face.name == 'down':
            rgb[:] = hexf(SKIN_LO)
            hand = np.ones_like(hand)
        knuckles = hand & (np.abs(y - 13.7) < 0.2)
        mix(rgb, knuckles, SKIN_LO, 0.5)
        if f <= 4:
            bones = patches(face, BONED[f], 5) & hand
            if f == 4:
                bones |= knuckles
            self.flesh(face, rgb, hand, bones)
            return
        # skeletal hands: bone fingers on the back and palm, one bone on each side, tips underneath
        rgb[hand] = hexf('#1a0c08')
        u = face.grid()[0]
        if face.name in ('north', 'south'):
            col = np.floor(u * 8)
            fingers = hand & (y < 13.55) & ((col % 2) == 0)
            joints = (np.abs(y - 12.55) < 0.1) | (np.abs(y - 13.05) < 0.08)
            rgb[fingers & ~joints] = hexf(BONE)
            rgb[fingers & joints] = hexf(BONE_LO)
            knuck = hand & (np.abs(y - 13.75) < 0.2) & ((col % 2) == 0)
            rgb[knuck] = hexf(BONE)
            meta = hand & (y > 13.95) & (y < 14.7) & ((col % 2) == 0) & (col > 0) & (col < 7)
            rgb[meta] = hexf(BONE_LO)
            rgb[hand & (y >= 14.7)] = hexf(BONE_LO)
        elif face.name in ('east', 'west'):
            one = hand & (np.abs(u - 0.5) < 0.2) & (y < 14.7)
            rgb[one] = hexf(BONE)
            rgb[one & (np.abs(y - 13.0) < 0.1)] = hexf(BONE_LO)
            rgb[hand & (y >= 14.7)] = hexf(BONE_LO)
        if face.name == 'down':
            g = face.grid()
            tips = (np.floor(g[0] * 8) % 2 == 0) & (np.abs(g[1] - 0.5) < 0.3)
            rgb[tips] = hexf(BONE)
        if f == 5:
            strips = patches(face, 0.35, 6) & hand
            rgb[strips] = hexf(CHAR)

    def sleeve(self, face, rgb, glow, rs):
        f = self.f
        x, y, z = face.world()
        rgb[:] = hexf(LEATHER) + rs.uniform(-3, 3, rgb.shape[:2])[..., None]
        cuff = (y > 15.2) & (y < 16.5)
        mix(rgb, cuff, '#0d0d0f', 0.9)
        mix(rgb, np.abs(y - 16.5) < 0.15, LEATHER_HI, 0.7)
        outer = (face.name == 'west' and x.mean() < 0) or (face.name == 'east' and x.mean() > 0)
        if outer:
            u = face.grid()[0]
            zz = cuff & (np.abs(u - 0.5) < 0.09)
            mix(rgb, zz, SILVER, 0.9)
        mix(rgb, np.abs(y - 18.6) < 0.15, LEATHER_HI, 0.4)                            # elbow seam
        if face.name in ('north', 'south', 'east', 'west'):
            mix(rgb, np.sin(y * 1.7 + x) > 0.85, LEATHER_HI, 0.3)
        if face.name == 'up':
            mix(rgb, np.ones(rgb.shape[:2], bool), LEATHER_HI, 0.25)
        if SCORCH[f] > 0:
            burnt = (y < 15.2 + 4.0 * SCORCH[f]) & patches(face, 0.4 + SCORCH[f], 7)
            mix(rgb, burnt, '#0a0706', 0.9)
            mix(rgb, burnt & cracks(face, 0.12), EMBER, 0.6)

    def sleeve_alpha(self, face, rs):
        x, y, z = face.world()
        a = np.where(y > 15.2, 255, 0).astype(np.uint8)
        if face.name == 'down':
            a[:] = 0
        return a

    # ---- legs
    def jeans(self, face, rgb, glow, rs):
        f = self.f
        x, y, z = face.world()
        rgb[:] = hexf(DENIM) + rs.uniform(-5, 5, rgb.shape[:2])[..., None]
        if face.name == 'north':
            fade = np.clip(1.0 - np.abs(y - 8.5) / 3.0, 0, 1) * 0.45
            mix(rgb, np.ones(rgb.shape[:2], bool), DENIM_HI, fade)
            whisk = (np.abs(y - 10.6) < 0.12) & (np.sin(x * 3.0) > 0)
            mix(rgb, whisk, DENIM_HI, 0.5)
        if face.name in ('east', 'west'):
            mix(rgb, np.abs(face.grid()[0] - 0.5) < 0.07, '#4a4038', 0.5)              # side seam stitches
        boot = y < 4.2
        rgb[boot] = hexf(BOOT)
        mix(rgb, boot & (np.sin(x * 2 + y) > 0.8), LEATHER_HI, 0.3)
        rgb[y < 0.6] = hexf(SOLE)
        mix(rgb, np.abs(y - 4.2) < 0.2, '#26262c', 0.8)
        mix(rgb, (np.abs(y - 1.6) < 0.18) & ~(y < 0.6), SILVER, 0.6)
        if face.name == 'down':
            rgb[:] = hexf(SOLE)
        if SCORCH[f] > 0:
            burnt = (y < 4.2 + 6.0 * SCORCH[f]) & (y >= 4.2) & patches(face, 0.35 + SCORCH[f], 8)
            mix(rgb, burnt, '#0d0a0a', 0.85)
            mix(rgb, burnt & cracks(face, 0.1), EMBER, 0.55)

    def boot_shaft(self, face, rgb, glow, rs):
        x, y, z = face.world()
        rgb[:] = hexf(BOOT) + rs.uniform(-3, 3, rgb.shape[:2])[..., None]
        mix(rgb, np.abs(y - 4.4) < 0.2, LEATHER_HI, 0.6)
        mix(rgb, np.sin(x * 2 + y * 1.3) > 0.8, LEATHER_HI, 0.25)

    def boot_alpha(self, face, rs):
        x, y, z = face.world()
        a = np.where(y < 4.6, 255, 0).astype(np.uint8)
        if face.name in ('up', 'down'):
            a[:] = 0
        return a

    # ---- emissive overlay
    def glow(self, face, group, layer):
        f = self.f
        x, y, z = face.world()
        g = np.zeros((face.h, face.w, 4), float)
        if f == 0:
            return g

        def emit(mask, rgb, a=255):
            g[mask] = np.array([rgb[0], rgb[1], rgb[2], a], float)

        if layer == 0:
            if group == 'head' and f <= 4 and face.name != 'up':
                region = np.ones((face.h, face.w), bool)
                emit(cracks(face, CRACK[f]), EMBER, 200 if f < 3 else 255)
                if face.name == 'north':
                    ax = np.abs(x)
                    if f < 4:
                        eye = (ax > 0.9) & (ax < 3.2) & (y > 27.1) & (y < 27.95)
                    else:
                        eye = (ax > 0.8) & (ax < 3.2) & (y > 26.8) & (y < 28.6)
                    emit(eye, EMBER_HOT, 255)
                    core = (ax > 1.5) & (ax < 2.3) & (y > 27.2) & (y < 27.85)
                    emit(core, (255, 250, 210), 255)
            if group == 'head' and f <= 4 and face.name == 'up' and HAIR_LEFT[f] <= 0:
                emit(cracks(face, CRACK[f]), EMBER, 220)
            if group == 'arm':
                hand = y < 15.0
                if f <= 4:
                    emit(cracks(face, CRACK[f]) & hand, EMBER, 220)
                else:
                    joints = hand & ((np.abs(y - 13.05) < 0.08) | (np.abs(y - 12.55) < 0.1) | (np.abs(y - 13.95) < 0.08))
                    if face.name in ('north', 'south'):
                        joints &= (np.floor(face.grid()[0] * 8) % 2) == 0
                    emit(joints, EMBER, 120)
            if group == 'torso' and face.name == 'north' and f >= 5:
                open_ = (np.abs(x) < 1.75) & (y > 13.2) & (y < 23.6)
                ribs = np.zeros_like(open_)
                for ry in (22.3, 20.9, 19.5, 18.1, 16.7, 15.3):
                    ribs |= np.abs(y - (ry - (np.abs(x) ** 2) * 0.12)) < 0.33
                gaps = open_ & ~ribs & ~((np.abs(x) < 0.32) & (y > 17.0))
                emit(gaps, EMBER_RED, 80)
                emit(gaps & (np.abs(x) < 0.9) & (y > 16.0) & (y < 21.0), EMBER, 140)
            if group == 'leg' and SCORCH[f] > 0:
                burnt = (y < 4.2 + 6.0 * SCORCH[f]) & (y >= 4.2) & patches(face, 0.35 + SCORCH[f], 8)
                emit(burnt & cracks(face, 0.1), EMBER, 200)
        else:
            if group == 'torso' and SCORCH[f] > 0 and face.name != 'down':
                hem = (y < 13.2 + 3.5 * SCORCH[f]) | ((face.name == 'north') & (np.abs(x) < 1.75 + 2.0 * SCORCH[f]))
                burnt = hem & patches(face, 0.4 + SCORCH[f], 4) & (y >= 13.2)
                if face.name == 'north':
                    burnt &= np.abs(x) >= 1.3
                emit(burnt & cracks(face, 0.08), EMBER, 160)
            if group == 'arm' and SCORCH[f] > 0 and face.name != 'down':
                burnt = (y < 15.2 + 4.0 * SCORCH[f]) & (y > 15.2) & patches(face, 0.4 + SCORCH[f], 7)
                emit(burnt & cracks(face, 0.08), EMBER, 160)
            if group == 'head' and 1 <= f <= 2:
                emit(np.ones((face.h, face.w), bool) & (_RS.uniform(0, 1, (face.h, face.w)) > (0.93 - 0.05 * f)), EMBER, 200)
        return g


# vanilla skin boxes: (origin, size, uv, layer, bone, group)
BOXES = []
for _layer, _uvs in ((0, dict(head=(0, 0), body=(16, 16), ra=(40, 16), la=(32, 48), rl=(0, 16), ll=(16, 48))),
                     (1, dict(head=(32, 0), body=(16, 32), ra=(40, 32), la=(48, 48), rl=(0, 32), ll=(0, 48)))):
    BOXES += [([-4, 24, -4], [8, 8, 8], _uvs['head'], _layer, 'armorhead', 'head'),
              ([-4, 12, -2], [8, 12, 4], _uvs['body'], _layer, 'armorbody', 'torso'),
              ([-8, 12, -2], [4, 12, 4], _uvs['ra'], _layer, 'armorrightarm', 'arm'),
              ([4, 12, -2], [4, 12, 4], _uvs['la'], _layer, 'armorleftarm', 'arm'),
              ([-3.9, 0, -2], [4, 12, 4], _uvs['rl'], _layer, 'armorrightleg', 'leg'),
              ([-0.1, 0, -2], [4, 12, 4], _uvs['ll'], _layer, 'armorleftleg', 'leg')]
PIVOTS = {'armorhead': [0, 24, 0], 'armorbody': [0, 24, 0], 'armorrightarm': [-5, 22, 0], 'armorleftarm': [5, 22, 0],
          'armorrightleg': [-1.9, 12, 0], 'armorleftleg': [1.9, 12, 0]}


def rects(u, v, w, h, d):
    return {'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'east': (u + d + w, v + d, d, h),
            'south': (u + d + w + d, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}


class _Box:
    def __init__(self, origin, size):
        self.origin, self.size = origin, size


def mats(p):
    return {
        ('head', 0): FlatMat(SKIN, noise=2, edge=0.0, top=1.0, bottom=0.85, fn=p.head, alpha_fn=p.head_alpha),
        ('head', 1): FlatMat(HAIR, noise=6, edge=0.0, top=1.15, fn=p.hat, alpha_fn=p.hat_alpha),
        ('torso', 0): FlatMat(TEE, noise=3, edge=0.0, fn=p.tee),
        ('torso', 1): FlatMat(LEATHER, noise=3, edge=0.0, fn=p.jacket, alpha_fn=p.jacket_alpha),
        ('arm', 0): FlatMat(LEATHER, noise=3, edge=0.0, fn=p.arm),
        ('arm', 1): FlatMat(LEATHER, noise=3, edge=0.0, fn=p.sleeve, alpha_fn=p.sleeve_alpha),
        ('leg', 0): FlatMat(DENIM, noise=4, edge=0.0, fn=p.jeans),
        ('leg', 1): FlatMat(BOOT, noise=3, edge=0.0, fn=p.boot_shaft, alpha_fn=p.boot_alpha),
    }


def paint_frame(frame):
    p = Painter(frame)
    m = mats(p)
    rng = random.Random(666)
    img = np.zeros((64 * D, 64 * D, 4), np.uint8)
    glow = np.zeros((64 * D, 64 * D, 4), np.uint8)
    for origin, size, (u, v), layer, bone, group in BOXES:
        w, h, d = size
        box = _Box(origin, size)
        for name, (x, y, rw, rh) in rects(u, v, w, h, d).items():
            face = Face(box, name, rw * D, rh * D)
            col, _ = m[(group, layer)].paint(face, rng)
            img[y * D:(y + rh) * D, x * D:(x + rw) * D] = col
            gl = p.glow(face, group, layer)
            g = np.clip(gl, 0, 255)
            g[..., :3] *= g[..., 3:4] / 255.0
            g[..., 3] = np.where(g[..., 3] > 0, 255, 0)
            glow[y * D:(y + rh) * D, x * D:(x + rw) * D] = g.astype(np.uint8)
    return img, glow


def glow_geo():
    """Vanilla boxes (both layers) a hair outside the skin, per-face UVs in 64-space."""
    bones = []
    for name, piv in PIVOTS.items():
        cubes = []
        for origin, size, (u, v), layer, bone, group in BOXES:
            if bone != name:
                continue
            w, h, d = size
            infl = (0.03 if layer == 0 else (0.53 if group == 'head' else 0.28))
            uv = {}
            for f, (x, y, rw, rh) in rects(u, v, w, h, d).items():
                uv[f] = {'uv': [x, y], 'uv_size': [rw, rh]}           # same convention as jinwoo_geo.Model
            cubes.append({'origin': list(origin), 'size': list(size), 'inflate': infl, 'uv': uv})
        bones.append({'name': name, 'pivot': piv, 'cubes': cubes})
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.ghost_rider_skin_glow', 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}


# ------------------------------------------------------------------------------------------------ 3D gear
def skull_fn(ctx):
    """Old bone: stains, hairline cracks, soot rising from the crown; the front faces carry the sockets' rims."""
    x, y, z = ctx.x, ctx.y, ctx.z
    n = field(x * 1.3, y * 1.3, z * 1.3, 1)
    ctx.put(n > 0.9, BONE_LO, 0.45)
    ctx.light(n < -0.8, 0.05)
    crk = np.abs(field(x * 2.2, y * 2.2, z * 2.2, 3)) < 0.05
    ctx.put(crk, '#6b5a44', 0.7)
    soot = np.clip((y - 30.6) / 2.6, 0, 1) * 0.55
    ctx.put(np.ones((ctx.h, ctx.w), bool), '#2a1a12', 0.0)
    m = soot > 0
    ctx.rgb[m] = ctx.rgb[m] * (1 - soot[m, None]) + np.array([40, 26, 18], float) * soot[m, None]
    if ctx.name == 'down':
        ctx.light(ctx.all(), -0.2)


def socket_fn(ctx):
    """The back of an eye socket: hellfire burning deep inside (dark rim, red, orange, a white-hot core)."""
    u = ctx.u - 0.5
    v = ctx.v - 0.45
    r = np.sqrt((u / 0.5) ** 2 + (v / 0.42) ** 2)
    ctx.put(ctx.all(), '#120302')
    ctx.flat |= ctx.all()
    ctx.emit(r < 1.0, '#6a1004', 255)
    ctx.emit(r < 0.78, EMBER_RED_HEX, 255)
    ctx.emit(r < 0.55, '#ff8a1a', 255)
    ctx.emit(r < 0.32, '#ffd76a', 255)
    ctx.emit(r < 0.14, '#fff6d0', 255)


def teeth_fn(ctx):
    if ctx.name == 'north' or ctx.name in ('east', 'west'):
        cols = np.floor(ctx.u * (ctx.w / 2.0)) % 2 == 1
        ctx.light(cols & (ctx.v > 0.1), -0.3)
        ctx.put(cols, '#5a4632', 0.6)
        ctx.light(ctx.v > 0.8, -0.12)


def maxilla_fn(ctx):
    skull_fn(ctx)
    if ctx.name == 'north':
        u = ctx.u - 0.5
        v = ctx.v
        nose = (np.abs(u) < 0.13 * (1.0 - (v - 0.05) * 0.5)) & (v < 0.62) & (v > 0.0)
        nose &= ~((np.abs(u) < 0.025) & (v > 0.25))
        ctx.put(nose, '#140604')
        ctx.flat |= nose
        ctx.emit(nose & (v > 0.15), '#7a1a06', 160)


def leather_fn(ctx):
    ctx.light(np.sin(ctx.x * 2 + ctx.y * 1.3 + ctx.z) > 0.8, 0.06)


def steel_fn(ctx):
    if ctx.side and ctx.h >= 2:
        ctx.light(ctx.v < 0.3, 0.12)


def link_fn(ctx):
    ctx.light(ctx.v < 0.35, 0.10)
    ctx.light(ctx.v > 0.7, -0.10)


EMBER_RED_HEX = '#d8320c'
GEAR_MATS = {
    'skull': Mat(BONE, 'paint', skull_fn, ao=0.5, quant=0.05, micro=0.4),
    'maxilla': Mat(BONE, 'paint', maxilla_fn, ao=0.5, quant=0.05, micro=0.4),
    'teeth': Mat('#e4dbc4', 'paint', teeth_fn, ao=0.3, quant=0.05, micro=0.2),
    'socket': Mat('#3a0a02', 'flat', socket_fn, ao=0.0, edges=False, micro=0.0),
    'leather': Mat(LEATHER, 'leather', leather_fn, ao=0.45, quant=0.04),
    'steel': Mat('#7d8089', 'metal', steel_fn, ao=0.3, quant=0.04, micro=0.4),
    'dsteel': Mat('#4a4c53', 'metal', steel_fn, ao=0.3, quant=0.04, micro=0.4),
    'link': Mat('#5f626b', 'metal', link_fn, ao=0.2, quant=0.04, micro=0.2),
    'buckle': Mat(SILVER, 'metal', steel_fn, ao=0.2, quant=0.04, micro=0.4),
}


class Gear:
    def __init__(self):
        self.m = CleanModel('ghost_rider_gear', density=4, width=256, seed=13, light=(33.0, 0.0, 0.08))

    def box(self, bone, a, b, mat, **kw):
        x0, y0, z0 = a
        x1, y1, z1 = b
        return self.m.cube(bone, [min(x0, x1), min(y0, y1), min(z0, z1)], [abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)],
                           GEAR_MATS[mat], **kw)

    def mbox(self, bone, a, b, mat, **kw):
        """Box and its mirror across x = 0."""
        self.box(bone, a, b, mat, **kw)
        self.box(bone, (-a[0], a[1], a[2]), (-b[0], b[1], b[2]), mat, **kw)

    def tube(self, bone, p0, p1, t, mat, ty=None):
        p0, p1 = np.array(p0, float), np.array(p1, float)
        if p1[2] < p0[2]:
            p0, p1 = p1, p0
        d = p1 - p0
        L = float(np.linalg.norm(d))
        n = d / L
        rx = math.degrees(math.asin(max(-1.0, min(1.0, n[1]))))
        ry = math.degrees(math.atan2(n[0], n[2]))
        ty = ty or t
        o = [p0[0] - t / 2.0, p0[1] - ty / 2.0, p0[2]]
        rot = [round(rx, 3), round(ry, 3), 0]
        return self.m.cube(bone, o, [t, ty, L], GEAR_MATS[mat], rotation=rot, pivot=list(p0))

    def spike(self, bone, base, direction, length, width, mat='steel'):
        base = np.array(base, float)
        d = np.array(direction, float)
        d /= np.linalg.norm(d)
        for k in range(3):
            f = k / 3.0
            w = width * (1.0 - 0.3 * k)
            self.tube(bone, base + d * (length * f), base + d * (length * (f + 1 / 3.0) + 0.05), w, mat)


def build_gear():
    g = Gear()
    m = g.m
    for name, piv in PIVOTS.items():
        m.bone(name, None, piv)

    # ---- the skull (a little bigger than the head cube, which is empty in the Rider's skin) ---------------------
    m.bone('skull', 'armorhead', [0, 24, 0])
    g.box('skull', (-3.8, 29.6, -3.0), (3.8, 32.0, 4.0), 'skull')            # cranium above the brow
    g.box('skull', (-3.8, 27.6, -1.5), (3.8, 29.6, 4.0), 'skull')            # cranium behind the sockets
    g.box('skull', (-3.2, 32.0, -2.4), (3.2, 33.0, 3.4), 'skull')            # crown
    g.box('skull', (-4.2, 28.0, -1.4), (4.2, 31.2, 3.2), 'skull')            # parietal bulge
    g.box('skull', (-3.2, 26.4, 3.2), (3.2, 31.0, 4.4), 'skull')             # occiput
    g.box('skull', (-3.5, 29.6, -3.6), (3.5, 32.0, -2.9), 'skull')           # forehead
    g.box('skull', (-3.7, 29.0, -4.05), (3.7, 29.8, -3.3), 'skull')          # brow ridge
    g.box('skull', (-0.55, 26.6, -3.9), (0.55, 29.1, -2.6), 'skull')         # nasal bridge
    g.mbox('skull', (3.05, 26.6, -3.75), (3.8, 29.1, -1.2), 'skull')          # outer orbits
    g.mbox('skull', (0.5, 26.75, -1.95), (3.1, 29.05, -1.5), 'socket')        # sockets (hellfire inside)
    g.box('skull', (-3.8, 26.4, -1.5), (3.8, 27.6, 3.2), 'skull')            # temples
    g.mbox('skull', (1.5, 25.9, -3.65), (4.0, 26.8, -0.6), 'skull')           # cheekbones
    g.mbox('skull', (3.6, 25.9, -0.6), (4.1, 26.6, 1.8), 'skull')             # zygomatic arches
    g.box('skull', (-2.5, 24.9, -3.7), (2.5, 26.8, -1.0), 'maxilla')         # upper jaw with the nose hole
    g.box('skull', (-2.2, 24.3, -3.85), (2.2, 25.0, -2.2), 'teeth')          # upper teeth
    g.box('skull', (-3.0, 24.6, -1.0), (3.0, 26.4, 2.4), 'skull')            # cheek hollows
    g.mbox('skull', (2.2, 30.2, -3.0), (3.4, 31.2, -2.2), 'skull')            # frontal bosses
    # the jaw hinges just below the ears
    m.bone('jaw', 'skull', [0, 25.6, 1.4])
    g.box('jaw', (-2.0, 23.7, -3.75), (2.0, 24.4, -2.2), 'teeth')            # lower teeth
    g.box('jaw', (-2.1, 22.8, -3.5), (2.1, 23.8, -1.6), 'skull')             # chin
    g.mbox('jaw', (1.8, 22.9, -2.6), (3.0, 24.2, 0.8), 'skull')               # mandible
    g.mbox('jaw', (2.6, 23.6, 0.2), (3.3, 26.0, 1.8), 'skull')                # ramus

    # ---- jacket collar (both forms) ------------------------------------------------------------------------
    m.bone('collar', 'armorbody', [0, 24, 0])
    g.box('collar', (-3.4, 23.1, 1.9), (3.4, 25.0, 2.7), 'leather')
    g.mbox('collar', (2.9, 23.1, -1.6), (3.7, 24.8, 2.5), 'leather')
    g.mbox('collar', (1.4, 21.0, -2.75), (3.4, 24.2, -2.25), 'leather', rotation=[0, 0, -12], pivot=[1.4, 24.2, -2.5])
    m.bone('belt', 'armorbody', [0, 24, 0])
    g.box('belt', (-1.0, 12.1, -2.62), (1.0, 13.3, -2.25), 'buckle')
    g.box('belt', (-0.45, 12.4, -2.75), (0.45, 13.0, -2.6), 'dsteel')

    # ---- the Rider's chain, wrapped over the right shoulder down to the left hip ----------------------------
    m.bone('chest_chain', 'armorbody', [0, 24, 0])
    pts = [(-3.4, 23.8, -2.55), (3.6, 13.4, -2.55)]
    n_links = 13
    for k in range(n_links):
        t0, t1 = k / n_links, (k + 1) / n_links
        a = np.array(pts[0]) * (1 - t0) + np.array(pts[1]) * t0
        b = np.array(pts[0]) * (1 - t1) + np.array(pts[1]) * t1
        mid = (a + b) / 2
        if k % 2 == 0:
            g.tube('chest_chain', a, b, 0.95, 'link', ty=0.4)
        else:
            g.tube('chest_chain', a - (b - a) * 0.12, b + (b - a) * 0.12, 0.4, 'link', ty=1.0)
    # over the back
    for k in range(9):
        t0, t1 = k / 9, (k + 1) / 9
        a = np.array((-3.4, 23.8, 2.55)) * (1 - t0) + np.array((3.6, 13.4, 2.55)) * t0
        b = np.array((-3.4, 23.8, 2.55)) * (1 - t1) + np.array((3.6, 13.4, 2.55)) * t1
        g.tube('chest_chain', a, b, 0.75 if k % 2 == 0 else 0.32, 'link', ty=0.32 if k % 2 == 0 else 0.8)
    g.box('chest_chain', (-4.4, 22.6, -2.6), (-3.2, 24.6, 2.6), 'link')       # over the shoulder

    # ---- spiked shoulder pads (the Rider's jacket) ----------------------------------------------------------
    for side, bone in ((-1, 'armorrightarm'), (1, 'armorleftarm')):
        pad = 'r_pad' if side < 0 else 'l_pad'
        m.bone(pad, bone, PIVOTS[bone])
        x0, x1 = (-8.5, -3.7) if side < 0 else (3.7, 8.5)
        g.box(pad, (x0, 22.4, -2.55), (x1, 24.7, 2.55), 'leather')
        g.box(pad, (x0 + (0.3 if side < 0 else 0), 21.6, -2.4), (x1 - (0 if side < 0 else 0.3), 22.4, 2.4), 'leather')
        for dz in (-1.3, 0.0, 1.3):
            bx = -7.2 if side < 0 else 7.2
            g.spike(pad, (bx, 24.6, dz), (side * 0.45, 1.0, dz * 0.12), 2.6 if dz == 0 else 1.9, 0.75)
        sx = -8.5 if side < 0 else 8.5
        g.spike(pad, (sx, 23.4, 0.0), (side * 1.0, 0.3, 0.0), 1.3, 0.6)

    # ---- boots: straps with buckles (both), heel spurs (Rider) ---------------------------------------------
    for side, bone in ((-1, 'armorrightleg'), (1, 'armorleftleg')):
        x0 = -3.9 if side < 0 else -0.1
        strap = 'r_strap' if side < 0 else 'l_strap'
        m.bone(strap, bone, PIVOTS[bone])
        g.box(strap, (x0 - 0.36, 2.5, -2.36), (x0 + 4.36, 3.1, 2.36), 'leather')
        bxo = x0 - 0.56 if side < 0 else x0 + 4.36
        g.box(strap, (bxo, 2.35, -0.45), (bxo + 0.2, 3.25, 0.45), 'buckle')
        spur = 'r_spur' if side < 0 else 'l_spur'
        m.bone(spur, bone, PIVOTS[bone])
        cx = x0 + 2.0
        g.box(spur, (cx - 0.9, 0.9, 2.3), (cx + 0.9, 1.3, 2.7), 'dsteel')
        g.spike(spur, (cx, 1.1, 2.6), (0, 0.15, 1.0), 1.2, 0.45)
    return m


def chain_sprite():
    """One link, 8 x 16: an oval steel ring (transparent hole and outside)."""
    h, w = 16, 8
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    u = (xx + 0.5 - w / 2) / (w / 2)
    v = (yy + 0.5 - h / 2) / (h / 2)
    r = np.sqrt(u ** 2 + v ** 2 * 1.0)
    ring = (r < 1.0) & (r > 0.55)
    img = np.zeros((h, w, 4), np.uint8)
    shade = np.clip(0.75 + 0.35 * (-u * 0.6 - v * 0.4), 0.4, 1.2)
    base = np.array([150, 152, 160], float)
    col = np.clip(base[None, None, :] * shade[..., None], 0, 255)
    img[ring, :3] = col[ring].astype(np.uint8)
    img[ring, 3] = 255
    return img


def preview(out, frames=(0, 3, 5, 6)):
    import jinwoo_preview as jp
    from PIL import Image, ImageDraw
    root = os.path.abspath(os.path.join(HERE, '..', '..'))
    gear = jp.load(os.path.join(RES, GEAR_GEO))
    gt = np.array(Image.open(os.path.join(root, 'build/jinwoo_preview', GEAR_TEX)).convert('RGBA'))
    gg = np.array(Image.open(os.path.join(root, 'build/jinwoo_preview', GEAR_GLOW)).convert('RGBA'))
    body = glow_geo()['minecraft:geometry'][0]
    for b in body['bones']:
        for c in b['cubes']:
            c['inflate'] = 0.0 if c['inflate'] < 0.1 else (0.5 if c['inflate'] > 0.5 else 0.25)
    size = 300
    tiles = []
    for f in frames:
        skin = np.array(Image.open(os.path.join(root, 'build/jinwoo_preview', OUT + 'skin_%d.png' % f)).convert('RGBA'))
        sg = np.array(Image.open(os.path.join(root, 'build/jinwoo_preview', OUT + 'skin_glow_%d.png' % f)).convert('RGBA')) \
            if f > 0 else None
        hide = {}
        if f < 5:
            for bn in ('skull', 'r_pad', 'l_pad', 'chest_chain', 'r_spur', 'l_spur'):
                hide[bn] = {'scale': [0, 0, 0]}
        models = [(body, skin, sg), (gear, gt, gg)]
        for title, yaw, pitch in (('front', 0, 0), ('3/4', 35, 10), ('back', 180, 0)):
            pose = dict(hide)
            q = jp.layered(models, pose)
            im = Image.fromarray(jp.render(q, None, None, yaw, pitch, size, scale=size / 2.4, center=(0, 1.0, 0)))
            ImageDraw.Draw(im).text((4, 4), 'frame %d %s' % (f, title), fill=(255, 255, 255))
            tiles.append(im)
    # skull close-up
    for title, yaw, pitch in (('skull', 0, 0), ('skull 3/4', 40, 10), ('skull side', 90, 0)):
        q = jp.layered([(gear, gt, gg)], {'collar': {'scale': [0, 0, 0]}, 'chest_chain': {'scale': [0, 0, 0]}})
        im = Image.fromarray(jp.render(q, None, None, yaw, pitch, size, scale=size / 0.9, center=(0, 1.78, 0)))
        ImageDraw.Draw(im).text((4, 4), title, fill=(255, 255, 255))
        tiles.append(im)
    cols = 6
    sheet = Image.new('RGB', (cols * size, ((len(tiles) + cols - 1) // cols) * size), (30, 30, 30))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * size, (i // cols) * size))
    sheet.save(out)
    print('preview', out)


def main():
    for f in range(FRAMES + 1):
        img, glow = paint_frame(f)
        write_png(OUT + 'skin_%d.png' % f, img)
        if f > 0:
            write_png(OUT + 'skin_glow_%d.png' % f, glow)
    write_json(os.path.join(RES, GLOW_GEO), glow_geo())
    m = build_gear()
    write_model(m, GEAR_GEO, GEAR_TEX, GEAR_GLOW)
    from ghost_rider_bike import split_b64
    split_b64(GEAR_TEX)
    write_png(CHAIN, chain_sprite())
    n = sum(len(b.cubes) for b in m.bones)
    print('gear cubes', n, 'atlas', m.width, m.height)
    if '--preview' in sys.argv:
        preview('/tmp/ghost_rider_body.png')


if __name__ == '__main__':
    main()
