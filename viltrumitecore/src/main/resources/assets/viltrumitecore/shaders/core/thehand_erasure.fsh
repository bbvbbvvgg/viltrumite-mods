#version 150
// Ported from the Roundabout mod (Hydraheads and contributors), The Hand erasure, Roundabout License v1.2.
// See CREDITS.md. Only change: the Scale uniform (sizes in blocks scale with it; Scale = 1 is the original).

// The Hand erasure, see THE_HAND_ERASURE_VISUAL_SPEC.md; drawn by TheHandErasureRenderer.
// Mode 0: the shell, a wider tube around the glass: the space around it (distortion halo, glow and the closing
// seam), written opaque from the scene copy, no depth writes. Mode 2: the glass tube, real geometry with depth
// writes. Mode 1: palm aura, a 3D flame ball.
//
// Since iteration 5 everything is computed in 3D from the real surface: the normal, the view ray and a key light
// from a world direction. Nothing depends on which way the camera faces, so nothing mirrors or flips.
// Colours come from the erasure's TheHandErasureStyle.

uniform sampler2D Sampler0; // mipmapped copy of the finished world colour
uniform sampler2D Sampler1; // copy of the world depth
uniform vec2 ScreenSize;
uniform mat4 ProjMat;
uniform mat4 ModelViewMat;

uniform float Mode;
uniform float Time;         // seconds since the cut started
uniform float Seed;
uniform float Amount;       // aura size, 0..1
uniform vec3 AuraCentre;    // aura centre relative to the camera, world axes
uniform float AuraRadius;   // aura flame radius, blocks
uniform vec3 AuraDrift;     // world direction the aura's flames lick
uniform float Length;       // full path length, blocks
uniform float HeadS;        // path length cut so far, blocks
uniform float CapR;         // radius of the round head cap, blocks
uniform float Decay;        // 0..1 while space closes
uniform float Flash;
uniform float HaloStrength;
uniform float Scale;        // size multiplier of the whole stroke; 1 = original The Hand size
// Style: five colours from darkest to brightest at the head and at the tail, the outline, the closing colours.
uniform vec3 HeadRamp0;
uniform vec3 HeadRamp1;
uniform vec3 HeadRamp2;
uniform vec3 HeadRamp3;
uniform vec3 HeadRamp4;
uniform vec3 TailRamp0;
uniform vec3 TailRamp1;
uniform vec3 TailRamp2;
uniform vec3 TailRamp3;
uniform vec3 TailRamp4;
uniform vec3 EdgeColor;
uniform vec3 DecayColor0;
uniform vec3 DecayColor1;
// Development only, TheHandErasureRenderer.SANDBOX_LAYERS: 0 all, 1 no glow or seam, 2 no halo, 3 no smear,
// 4 normals, 5 geometry only, 6 copy test.
uniform float Debug;

// Glass: UV = (path length, angle around / 2 pi); colour r = tube radius / RADIUS_SCALE, gba = path tangent.
// Shell: UV = (path length of the axis point, tube radius there); colour r = shell radius / SHELL_SCALE,
// gba = path tangent (0 on the round ends). Aura: UV -1..1, white.
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;       // position relative to the camera, view space
in vec3 viewNormal;    // glass: surface normal; shell: direction from the axis point
in vec3 viewTangent;   // path tangent, view space
in vec3 axisPos;       // shell: the axis point, view space
in vec2 screenTangent; // the path tangent on screen

out vec4 fragColor;

// Must match ErasureStroke.
const float RADIUS_SCALE = 0.4;
const float SHELL_MARGIN = 0.13;
const float CLOSE_SPAN = 1.3;
const float ZIP = 0.6;
const float KEY_LIGHT_TOWARD_VIEWER = 1.5;
const float AURA_BOUND = 1.8;    // the flame's tongues stay inside this many AuraRadius
const int AURA_STEPS = 28;
const float PI = 3.14159265;
const float TWO_PI = 6.2831853;

// Tuning. Target: the body at about 100-110 of 255 in daylight; white only in thin highlights and the head flash.
const float BODY_BASE = 0.33;    // ramp position (0 darkest .. 1 brightest) before the terms below
const float BODY_WORLD = 0.25;   // the world's own light and dark patches
const float BODY_LIGHT = 0.70;   // the side facing the key light is lighter, the other side falls into shadow
const float BODY_DEPTH = 0.10;   // the thick middle is a little deeper
const float BODY_FLOW = 0.15;    // slow bands flowing along the length
const float BOUNCE = 0.25;       // light coming back through the glass near the edge turned away from the light
const float TRANSMIT = 0.22;     // refracted world added on top as tinted light, most through the thick middle
const float REFRACT = 0.35;      // like a glass rod, the tube shows the world from its opposite side...
const float LENS_EDGE = 0.6;     // ...more strongly toward its edges, like a lens
const float SHIFT = 0.35;        // and shifted along the cut (x tube width)
const float SMEAR = 0.30;        // length of the smear along the cut (x tube width)
const float SPEC_POWER = 40.0;
const float SPEC_STRENGTH = 0.6;
const float SHEEN_STRENGTH = 0.2; // broad soft highlight around the sharp one
const float FRESNEL = 0.35;      // glass reflects more at grazing angles: a light band just inside the outline
const float RIM_STRENGTH = 0.45;
const float GLOW_BLOCKS = 0.03;  // outer glow falloff in blocks, so it shrinks with distance...
const float GLOW_MAX_PX = 5.0;   // ...but never grows past this many pixels up close
const float GLOW_STRENGTH = 0.3;
const float HALO_BLOCKS = 0.04;  // largest pull of the space around the tube, blocks
const float HALO_CLOSE = 2.5;    // extra pull where space is closing: the sides snap together
const float SEAM_STRENGTH = 0.85; // the bright seam left for a moment where space has closed
const float SEGMENT_HALF = 0.15; // shell: the axis is measured as a segment this long either side of the point
const vec3 WHITE = vec3(0.957, 0.969, 0.988);

bool debugIs(float mode) {
    return abs(Debug - mode) < 0.5;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// Value noise with a quintic fade: smooth, without the square steps of plain value noise.
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Anti-aliased line `width` pixels wide around distance 0.
float line(float distPx, float width) {
    return 1.0 - smoothstep(width * 0.5 - 0.5, width * 0.5 + 0.5, abs(distPx));
}

// Aura and interior patterns change about 12 times per second, like hand-drawn animation on twos.
float steppedTime() {
    return floor(Time * 12.0) / 12.0;
}

vec3 rampOf(float x, vec3 c0, vec3 c1, vec3 c2, vec3 c3, vec3 c4) {
    x = clamp(x, 0.0, 1.0) * 4.0;
    if (x < 1.0) return mix(c0, c1, x);
    if (x < 2.0) return mix(c1, c2, x - 1.0);
    if (x < 3.0) return mix(c2, c3, x - 2.0);
    return mix(c3, c4, x - 3.0);
}

// Smooth ramp through the style: x = 0 darkest, 1 brightest; tail = 0 head colours, 1 tail colours.
vec3 ramp(float x, float tail) {
    return mix(rampOf(x, HeadRamp0, HeadRamp1, HeadRamp2, HeadRamp3, HeadRamp4),
               rampOf(x, TailRamp0, TailRamp1, TailRamp2, TailRamp3, TailRamp4), tail);
}

vec3 hue(float u) {
    return clamp(abs(fract(u + vec3(0.0, 2.0, 1.0) / 3.0) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// Space closing, TheHandErasureRenderer.openness: 0 open, 1 just shut, above 1 the seam fades.
float closing(float u) {
    return Decay * (CLOSE_SPAN + ZIP) - ZIP * u;
}

// The bright seam left for a moment where space has just closed.
float seam(float p) {
    return smoothstep(0.75, 1.0, p) * (1.0 - smoothstep(1.0, 1.3, p));
}

// Fades the effect out when the camera is almost inside it.
float nearFade() {
    return smoothstep(0.25 * Scale, 0.55 * Scale, length(viewPos));
}

// Key light: from above, leaning toward the viewer. View space.
vec3 keyLight() {
    return normalize(mat3(ModelViewMat) * vec3(0.0, 1.0, 0.0) - normalize(viewPos) * KEY_LIGHT_TOWARD_VIEWER);
}

// World colour `offsetPx` pixels away, blurred by mip level `lod`. Samples of things in front of the
// effect are refused, so the glass never shows the foreground. textureLod: these run in non-uniform
// branches, where implicit derivatives are undefined.
vec3 world(vec2 offsetPx, float lod) {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec2 moved = clamp(uv + offsetPx / ScreenSize, 0.0, 1.0);
    if (textureLod(Sampler1, moved, 0.0).r < gl_FragCoord.z) {
        moved = uv;
    }
    return textureLod(Sampler0, moved, lod).rgb;
}

// The world seen through the glass, smeared along the cut: 9 taps close together with Gaussian weights, read
// from a mip level about as blurry as the tap spacing, so shapes flow instead of repeating as separate copies.
vec3 smear(vec2 offsetPx, vec2 alongDir, float tubePx) {
    float spacing = max(SMEAR * tubePx / 8.0, 0.5);
    float lod = clamp(log2(spacing) + 1.0, 0.0, 4.0);
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int i = 0; i < 9; i++) {
        float k = float(i) - 4.0;
        float w = exp(-k * k / 8.0);
        sum += world(offsetPx + alongDir * k * spacing, lod) * w;
        total += w;
    }
    return sum / total;
}

// Mode 0: the space around the tube. The view ray is measured against the tube's axis in 3D, so the halo and
// the glow follow the real outline from any side. The space just outside is pulled toward the tube, untinted;
// a soft glow hugs the outline. While space closes the pull grows at the closing front and a thin bright seam
// is left behind for a moment.
vec4 space() {
    if (debugIs(6.0)) {
        // The scene copy is taken before the effect is drawn: written back unchanged it must leave no trace.
        return vec4(world(vec2(0.0), 0.0), 1.0);
    }
    float s = texCoord0.x;
    float tubeR = texCoord0.y;
    float u = s / max(Length, 0.001);
    vec3 ray = normalize(viewPos);
    // Closest approach of the view ray to a short piece of the axis around this point.
    vec3 halfSegment = viewTangent * SEGMENT_HALF * Scale;
    vec3 a = axisPos - halfSegment;
    vec3 v = 2.0 * halfSegment;
    float b = dot(ray, v);
    float c = dot(v, v);
    float k = c - b * b;
    float lambda = k > 1e-6 ? clamp((b * dot(ray, a) - dot(a, v)) / k, 0.0, 1.0) : 0.5;
    vec3 q = a + v * lambda;
    float along = dot(ray, q);
    float miss = sqrt(max(dot(q, q) - along * along, 0.0));
    float blockPx = ProjMat[1][1] * 0.5 * ScreenSize.y / max(-q.z, 0.05);
    float outsidePx = max(miss - tubeR, 0.0) * blockPx;

    float p = closing(u);
    float shut = clamp(p, 0.0, 1.0);
    float glowPx = clamp(GLOW_BLOCKS * Scale * blockPx, 0.75, GLOW_MAX_PX);
    float glow = debugIs(1.0) ? 0.0 : exp(-outsidePx / glowPx) * GLOW_STRENGTH * (1.0 - shut);
    float seamGlow = debugIs(1.0) ? 0.0 : exp(-outsidePx / (0.6 * glowPx)) * seam(p) * SEAM_STRENGTH;

    // Pull outward from the axis on screen, fading to nothing at the shell's edge.
    float fall = 1.0 - clamp((miss - tubeR) / (SHELL_MARGIN * Scale), 0.0, 1.0);
    vec4 axisClip = ProjMat * vec4(q, 1.0);
    vec2 axisPx = (axisClip.xy / max(axisClip.w, 1e-3) * 0.5 + 0.5) * ScreenSize;
    vec2 outward = gl_FragCoord.xy - axisPx;
    vec2 dir = outward / max(length(outward), 1.0);
    float boost = 1.0 + HALO_CLOSE * 4.0 * shut * (1.0 - shut);
    float pull = debugIs(2.0) ? 0.0 : HaloStrength * boost * fall * fall * HALO_BLOCKS * Scale * blockPx;
    vec3 col = world(dir * pull, 0.0);
    col = mix(col, HeadRamp3, clamp(glow, 0.0, 1.0));
    col = mix(col, mix(HeadRamp4, WHITE, 0.35), clamp(seamGlow, 0.0, 1.0));
    return vec4(col, nearFade());
}

// Liquid glass: the blurred world seen through a glass rod, tinted by a smooth ramp that is deeper where the
// tube is turned away from the key light, with a specular streak, a Fresnel band at grazing angles, light
// bouncing back near the shadowed edge and streaks flowing toward the head.
// n: surface normal and ray: view ray, both view space; c = n . -ray (1 facing the camera, 0 at the outline).
vec3 glass(float s, float u, float theta, vec3 n, vec3 ray, float c, vec3 key, vec2 alongDir, float tubePx, float tubeR,
           float blockPx) {
    float t = steppedTime();
    s /= max(Scale, 1e-3); // surface patterns keep their size relative to the tube
    float facing = dot(n, key);
    float lit = 0.5 + 0.5 * facing;
    vec2 acrossDir = vec2(-alongDir.y, alongDir.x);
    vec2 normalScreen = n.xy;
    float across = dot(normalScreen, acrossDir);

    // A glass rod shows the world behind it taken from its opposite side, more toward its edges; a slow
    // ripple makes it flow.
    float ripple = noise(vec2(s * 2.2 - t * 3.0, across * 1.5 + Seed * 10.0)) - 0.5;
    float lens = REFRACT * (1.0 + LENS_EDGE * dot(normalScreen, normalScreen));
    vec2 offset = alongDir * SHIFT * tubePx - (normalScreen * lens + acrossDir * ripple * 0.3) * 0.5 * tubePx;
    vec3 seen = debugIs(3.0) ? world(vec2(0.0), 0.0) : smear(offset, alongDir, tubePx);
    float lum = dot(seen, vec3(0.299, 0.587, 0.114));

    // Flow bands are fixed to the surface (angle around the tube), so they turn with the tube in 3D.
    vec2 around = vec2(cos(theta), sin(theta));
    float flow = noise(vec2((s - t * 2.5) * 1.3 + around.x, around.y * 1.5 + Seed * 10.0)) * 0.65
               + noise(vec2((s - t * 3.5) * 2.6 - around.y * 2.0, around.x * 3.0 - Seed * 4.0)) * 0.35;
    float rim = 1.0 - c;
    // Light that entered on the lit side comes back out near the opposite edge.
    float bounce = smoothstep(0.35, 0.85, rim) * (1.0 - smoothstep(0.9, 1.0, rim)) * max(-facing, 0.0);
    float value = BODY_BASE + BODY_WORLD * (lum - 0.5) + BODY_LIGHT * (lit - 0.5)
                - BODY_DEPTH * c + BODY_FLOW * (flow - 0.5) + BOUNCE * bounce;
    // The tail takes the tail colours.
    vec3 col = ramp(value, 1.0 - smoothstep(0.15, 0.85, u));
    // The refracted world shows through as tinted light, so the tube reads as glass rather than paint.
    col = min(col + seen * HeadRamp3 * TRANSMIT * (0.6 + 0.6 * c), vec3(1.0));

    // Glass reflects more where it is seen at a grazing angle.
    col = mix(col, HeadRamp4, pow(rim, 4.0) * FRESNEL);
    // Highlights: a broad sheen and a sharp specular streak along the tube, broken into long pieces that flow
    // toward the head.
    float streak = smoothstep(0.3, 0.7, noise(vec2(s * 1.4 - t * 2.0, Seed * 3.0)));
    float ends = smoothstep(0.08, 0.25, u);
    col = mix(col, HeadRamp4, pow(max(facing, 0.0), 6.0) * SHEEN_STRENGTH * ends);
    vec3 halfway = normalize(key - ray);
    col = mix(col, WHITE, pow(max(dot(n, halfway), 0.0), SPEC_POWER) * SPEC_STRENGTH * (0.35 + 0.65 * streak) * ends);

    // Thin streaks on the surface, moving toward the head.
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float lane = hash(vec2(fi, Seed * 7.0)) * TWO_PI;
        float dash = fract((s - t * 3.0) / (0.7 + hash(vec2(fi * 5.3, Seed))) + hash(vec2(fi * 2.7, Seed)));
        float segment = smoothstep(0.0, 0.1, dash) * (1.0 - smoothstep(0.35, 0.5, dash));
        float angle = abs(mod(theta - lane + PI, TWO_PI) - PI);
        float distPx = angle * tubeR * blockPx * max(c, 0.25);
        col = mix(col, HeadRamp3, line(distPx, clamp(tubePx * 0.03, 1.0, 3.0)) * segment * 0.45);
    }

    // While space closes the glass cools toward the closing colours, keeping its light and dark.
    float shut = clamp(closing(u), 0.0, 1.0);
    vec3 cool = mix(DecayColor0, DecayColor1, smoothstep(0.4, 1.0, shut)) * (0.35 + 0.6 * clamp(value, 0.0, 1.0));
    // Only a hint while the tube is still wide; most of it shows in the thin closing part.
    return mix(col, cool, smoothstep(0.3, 0.9, shut) * 0.55);
}

// Rim and outline on every edge: a light line just inside, brighter where the edge faces the key light
// (edgeFacing 0..1), and a thin dark outline. Plus the short pale flash at the head.
vec3 edges(vec3 inside, float dist, float tubePx, float edgeFacing, float s) {
    float rimPx = clamp(tubePx * 0.05, 1.0, 2.5);
    inside = mix(inside, HeadRamp4,
            line(dist - 1.0 - rimPx, rimPx) * RIM_STRENGTH * (0.6 + 0.4 * edgeFacing) * (1.0 - Decay));
    inside = mix(inside, EdgeColor, 0.85 * (1.0 - smoothstep(0.5, 1.5, dist)));
    return mix(inside, mix(HeadRamp4, WHITE, 0.5), Flash * 0.6 * (1.0 - smoothstep(0.0, 2.0 * max(CapR, 1e-3), HeadS - s)));
}

// Mode 2: the glass tube.
vec4 glassTube() {
    vec3 ray = normalize(viewPos);
    vec3 n = normalize(viewNormal);
    float c = dot(n, -ray);
    // Distance to the outline in pixels, from how fast c falls to 0 toward it. Derivatives before any discard.
    vec2 gc = vec2(dFdx(c), dFdy(c));
    if (debugIs(6.0)) {
        return vec4(world(vec2(0.0), 0.0), 1.0);
    }
    if (c <= 0.0) {
        discard; // the far side of the tube
    }
    float dist = 0.5 * c / max(length(gc), 1e-4);
    float coverage = clamp(dist + 0.5, 0.0, 1.0) * nearFade();
    float s = texCoord0.x;
    float u = s / max(Length, 0.001);
    if (debugIs(4.0)) {
        return vec4(n * 0.5 + 0.5, coverage);
    }
    if (debugIs(5.0)) {
        return vec4(hue(u) * (0.55 + 0.45 * c), coverage);
    }
    float tubeR = vertexColor.r * RADIUS_SCALE * Scale;
    float blockPx = ProjMat[1][1] * 0.5 * ScreenSize.y * gl_FragCoord.w;
    float tubePx = 2.0 * tubeR * blockPx;
    vec2 alongDir = length(screenTangent) > 1e-3 ? normalize(screenTangent) : vec2(1.0, 0.0);
    vec3 key = keyLight();
    vec3 inside = glass(s, u, texCoord0.y * TWO_PI, n, ray, c, key, alongDir, tubePx, tubeR, blockPx);
    float edgeFacing = 0.5 + 0.5 * dot(normalize(n.xy + vec2(1e-5)), normalize(key.xy + vec2(1e-5)));
    inside = edges(inside, dist, tubePx, edgeFacing, s);
    return vec4(inside, coverage);
}

float hashOf3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

// 3D value noise with a quintic fade.
float valueNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = mix(hashOf3(i), hashOf3(i + vec3(1.0, 0.0, 0.0)), f.x);
    float b = mix(hashOf3(i + vec3(0.0, 1.0, 0.0)), hashOf3(i + vec3(1.0, 1.0, 0.0)), f.x);
    float c = mix(hashOf3(i + vec3(0.0, 0.0, 1.0)), hashOf3(i + vec3(1.0, 0.0, 1.0)), f.x);
    float d = mix(hashOf3(i + vec3(0.0, 1.0, 1.0)), hashOf3(i + vec3(1.0, 1.0, 1.0)), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

// The aura's flame: signed distance to its boiling surface in units of AuraRadius, negative inside.
// q: offset from the centre / AuraRadius, world axes. The surface boils as the noise scrolls along the drift,
// and tongues reach out toward the drift (up, and back along the stroke while the palm moves).
float flame(vec3 q, float t) {
    vec3 seed = vec3(Seed * 17.0, Seed * 29.0, Seed * 11.0);
    vec3 scroll = AuraDrift * t * 2.4;
    float n = valueNoise3(q * 1.7 - scroll + seed) * 0.65 + valueNoise3(q * 3.6 - scroll * 1.6 - seed) * 0.35;
    float r = length(q);
    float toward = max(dot(q / max(r, 1e-4), AuraDrift), 0.0);
    return r - (0.62 + 0.38 * n + toward * toward * 0.6 * (0.3 + n));
}

// Mode 1: aura burning on the palm, in the style's colours. A flame ball traced in 3D along the view ray inside
// its bounding sphere: the jagged silhouette, the rim, the body and the hot core all come from how deep the ray
// passes into the flame, so it reads as a volume from any side and its shape is fixed in the world. Darker licks
// boil in its body, redrawn in steps; it is see-through enough that the tube's head shows inside, and it fades
// softly where it meets blocks.
vec4 aura() {
    if (debugIs(6.0)) {
        return vec4(world(vec2(0.0), 0.0), 1.0);
    }
    float radius = max(AuraRadius, 1e-3);
    vec3 rayView = normalize(viewPos);
    vec3 ray = transpose(mat3(ModelViewMat)) * rayView; // world axes, from the camera
    float bound = radius * AURA_BOUND;
    float b = dot(ray, AuraCentre);
    float disc = b * b - dot(AuraCentre, AuraCentre) + bound * bound;
    if (disc <= 0.0) {
        discard;
    }
    float t0 = max(b - sqrt(disc), 0.05);
    float t1 = b + sqrt(disc);
    // Blocks inside the bounding sphere cut the flame.
    float sceneDepth = textureLod(Sampler1, gl_FragCoord.xy / ScreenSize, 0.0).r;
    float sceneZ = -ProjMat[3][2] / (sceneDepth * 2.0 - 1.0 + ProjMat[2][2]);
    float sceneT = sceneZ / min(rayView.z, -1e-4);
    t1 = min(t1, sceneT);
    if (t1 <= t0) {
        discard;
    }
    float t = steppedTime();
    float fMin = 1e3;
    float tMin = t0;
    float tIn = -1.0;
    for (int i = 0; i < AURA_STEPS; i++) {
        float ti = mix(t0, t1, (float(i) + 0.5) / float(AURA_STEPS));
        float f = flame((ray * ti - AuraCentre) / radius, t);
        if (f < fMin) {
            fMin = f;
            tMin = ti;
        }
        if (f < 0.0 && tIn < 0.0) {
            tIn = ti;
        }
    }
    float blockPx = ProjMat[1][1] * 0.5 * ScreenSize.y / max(-rayView.z * tMin, 0.05);
    float edgePx = -fMin * radius * blockPx; // how far inside the silhouette, pixels
    if (edgePx < -1.0) {
        discard;
    }
    float depthIn = clamp(-fMin / 0.55, 0.0, 1.0); // 0 at the silhouette, 1 in the core
    vec3 q = (ray * tMin - AuraCentre) / radius;
    float lick = valueNoise3(q * 5.0 + vec3(Seed * 7.0) - AuraDrift * t * 5.0);
    vec3 col = mix(HeadRamp3, HeadRamp2, smoothstep(0.1, 0.45, depthIn));
    col = mix(col, HeadRamp1, smoothstep(0.6, 0.72, lick) * smoothstep(0.15, 0.3, depthIn)
            * (1.0 - smoothstep(0.55, 0.8, depthIn)) * 0.55);
    col = mix(col, mix(HeadRamp4, WHITE, 0.45 + 0.3 * Flash), smoothstep(0.55, 0.95, depthIn));
    col = mix(col, mix(HeadRamp4, WHITE, 0.25), line(edgePx - 1.0, 1.5) * 0.8);
    float soft = tIn < 0.0 ? 1.0 : clamp((sceneT - tIn) / (0.3 * radius), 0.0, 1.0);
    float alpha = clamp(edgePx + 0.5, 0.0, 1.0) * (0.5 + 0.4 * depthIn) * soft * smoothstep(0.0, 0.3, Amount);
    return vec4(col, alpha * nearFade());
}

void main() {
    if (Mode < 0.5) {
        fragColor = space();
    } else if (Mode < 1.5) {
        fragColor = aura();
    } else {
        fragColor = glassTube();
    }
}
