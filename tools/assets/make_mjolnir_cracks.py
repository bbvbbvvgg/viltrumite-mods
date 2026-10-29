#!/usr/bin/env python3
"""Stage R3: the ground-crack decal around a planted world Mjolnir.

32 x 32 px over 2 x 2 blocks (16 px per block, the vanilla block grid), the
hammer axis in the middle. Paint-pixel style: a dark dent disc that fades to
the rim, a pit where the head went in, jagged dark cracks with a lighter lip
on one side. Neutral greys: the renderer multiplies them by the ground light,
so they read on grass, stone and sand alike. Written as base64 into
binassets (decoded by Gradle at build time). Deterministic (fixed seed).
Run from the repository root.
"""
import base64
import io
import math
import random

from PIL import Image

SIZE = 32
OUT = "viltrumitecore/src/main/binassets/assets/viltrumitecore/textures/entity/thor/mjolnir_cracks.png.b64"
CENTER = (SIZE - 1) / 2.0


def put(img, x, y, rgba):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        r, g, b, a = img.getpixel((x, y))
        if rgba[3] >= a:
            img.putpixel((x, y), rgba)


def main():
    rnd = random.Random(1946)
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    # Dent: a dark disc, strongest under the head, fading to nothing at r = 9 px.
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - CENTER, y - CENTER)
            if d < 9.0:
                a = int(110 * (1.0 - d / 9.0) ** 1.4)
                if a > 6:
                    img.putpixel((x, y), (20, 16, 12, a))
    # Pit where the head went in (a 6 x 4 slot, like the head's footprint).
    for y in range(14, 18):
        for x in range(13, 19):
            put(img, x, y, (10, 8, 6, 235))
    # Cracks: eight jagged branches out of the pit, two of them forking.
    angles = [i * math.tau / 8 + rnd.uniform(-0.25, 0.25) for i in range(8)]
    for i, ang in enumerate(angles):
        length = rnd.uniform(9.5, 14.5)
        x, y = CENTER, CENTER
        heading = ang
        step = 0.0
        while step < length:
            if int(step) % 4 == 0:
                # Straight runs with sharp kinks: cracks in rock, not roots.
                heading += rnd.uniform(-0.7, 0.7)
            heading = ang + max(-0.6, min(0.6, heading - ang))
            x += math.cos(heading)
            y += math.sin(heading)
            step += 1.0
            fade = 1.0 - step / (length + 2.0)
            px, py = int(round(x)), int(round(y))
            put(img, px, py, (12, 10, 8, int(250 * fade + 40)))
            # Light lip on the sun side, one px off the crack.
            lx, ly = int(round(x - math.sin(heading))), int(round(y + math.cos(heading)))
            put(img, lx, ly, (150, 140, 125, int(120 * fade)))
            if i % 4 == 0 and abs(step - length * 0.5) < 0.5:
                # A short fork.
                fx, fy, fh = x, y, heading + rnd.choice((-0.6, 0.6))
                for k in range(int(length * 0.35)):
                    fx += math.cos(fh)
                    fy += math.sin(fh)
                    put(img, int(round(fx)), int(round(fy)), (14, 12, 9, int(200 * fade * (1.0 - k / (length * 0.35)))))
    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    with open(OUT, "w", encoding="ascii") as fh:
        fh.write(base64.b64encode(buf.getvalue()).decode("ascii") + "\n")
    img.resize((SIZE * 8, SIZE * 8), Image.NEAREST).save("/tmp/mjolnir_cracks_preview.png")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
