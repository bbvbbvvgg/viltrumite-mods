#version 150
// Thanos snap: the visible universe comes apart into glowing cells and comes back behind a golden
// front. World Erasure's model-pixel cells (ErasureDissolveRenderer: 1/16-block cells, each with a
// threshold from its position plus stable noise, a white-hot band at the front, the passed cells
// dropped) carried from one model to the whole frame: every pixel is put back into the world from
// the level depth, cells are face-local squares on the block-texel grid (coarser with distance),
// the passed cells shrink to nothing in the hot band and leave the void. The world itself is never
// touched: this only redraws the frame and writes depth 1 where the void shows, so the scene's own
// figures (drawn after) hang in it.

uniform sampler2D Sampler0; // colour of the finished frame
uniform sampler2D Sampler1; // its depth

uniform mat4 InvViewProj;   // clip -> camera-relative world
uniform vec3 CamToGrid;     // camera - grid anchor (an integer block near the origin)
uniform vec3 Origin;        // dissolve origin - grid anchor
uniform vec3 RestoreOrigin; // restore origin - grid anchor
uniform float Front;        // dissolve front, 0 .. 1.1 (everything gone above 1.05)
uniform float Restore;      // restore front, 0 .. 1.1 (0 = no restore yet)
uniform float Reach;        // blocks from the origin to threshold 1
uniform float Light;        // light of the still-standing world, 1 .. 0.05
uniform float Freeze;       // cold grade of the stopped world, 0 .. 1
uniform vec2 Shock;         // snap shock ring: radius (blocks), strength
uniform float Gold;         // golden light of the restore, 0 .. 1
uniform float Time;         // scene ticks
uniform vec3 HotColor;
uniform vec3 GoldColor;
uniform vec3 VoidColor;

in vec2 texCoord;
out vec4 fragColor;

const float BAND = 0.045;
const float JITTER = 0.16;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

/** Stars and a faint violet haze in the empty universe. */
vec3 voidAt(vec3 dir) {
    vec3 col = VoidColor * (0.7 + 0.5 * clamp(dir.y * 0.5 + 0.5, 0.0, 1.0));
    vec3 a = abs(dir);
    vec2 uv = a.x > a.y && a.x > a.z ? dir.yz / a.x : (a.y > a.z ? dir.xz / a.y : dir.xy / a.z);
    float face = a.x > a.y && a.x > a.z ? sign(dir.x) : (a.y > a.z ? 2.0 * sign(dir.y) : 3.0 * sign(dir.z));
    vec2 cell = floor(uv * 180.0);
    float h = hash13(vec3(cell, face * 17.0));
    if (h > 0.9965) {
        vec2 f = fract(uv * 180.0) - 0.5;
        float tw = 0.55 + 0.45 * sin(Time * 0.07 + h * 400.0);
        col += vec3(0.75, 0.8, 1.0) * tw * smoothstep(0.42, 0.0, max(abs(f.x), abs(f.y)));
    }
    return col;
}

/** Shrinking square of a passed cell: is this pixel still in it? k = 0 whole .. 1 gone. */
bool inSquare(vec2 local, float k) {
    float extent = 0.5 * (1.0 - k);
    return max(abs(local.x), abs(local.y)) < extent;
}

void main() {
    vec4 scene = texture(Sampler0, texCoord);
    float depth = texture(Sampler1, texCoord).r;
    vec4 clip = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 w = InvViewProj * clip;
    vec3 rel = w.xyz / w.w;
    vec3 dir = normalize(rel);
    bool sky = depth >= 0.99999;
    // derivatives outside any branch (undefined in non-uniform control flow)
    vec3 n = normalize(cross(dFdx(rel), dFdy(rel)) + vec3(0.0, 1.0e-6, 0.0));

    // ---- grade of the stopped world: cold, a little drained, dimmer as the light dies ----------
    vec3 col = scene.rgb;
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum) * vec3(0.86, 0.93, 1.08), 0.35 * Freeze);
    col *= mix(vec3(0.10, 0.09, 0.14), vec3(1.0), Light);

    // ---- the cell this pixel belongs to ---------------------------------------------------------
    float threshold;
    float restoreThreshold;
    vec2 local;
    if (sky) {
        vec3 a = abs(dir);
        vec2 uv = a.x > a.y && a.x > a.z ? dir.yz / a.x : (a.y > a.z ? dir.xz / a.y : dir.xy / a.z);
        float face = a.x > a.y && a.x > a.z ? sign(dir.x) : (a.y > a.z ? 2.0 * sign(dir.y) : 3.0 * sign(dir.z));
        vec2 g = uv * 40.0;
        vec2 cell = floor(g);
        local = fract(g) - 0.5;
        float h = hash13(vec3(cell, face * 13.0));
        threshold = 0.58 + 0.42 * h;
        restoreThreshold = 0.45 + 0.5 * hash13(vec3(cell.yx, face * 7.0 + 3.0));
    } else {
        vec3 p = rel + CamToGrid;
        if (dot(n, rel) > 0.0) {
            n = -n;
        }
        float dist = length(rel);
        float cells = dist < 14.0 ? 16.0 : (dist < 36.0 ? 8.0 : (dist < 72.0 ? 4.0 : (dist < 140.0 ? 2.0 : 1.0)));
        vec3 q = (p - n * 0.004) * cells;
        vec3 an = abs(n);
        vec2 uv;
        float layer;
        if (an.x > an.y && an.x > an.z) {
            uv = q.zy;
            layer = floor(q.x);
        } else if (an.y > an.z) {
            uv = q.xz;
            layer = floor(q.y);
        } else {
            uv = q.xy;
            layer = floor(q.z);
        }
        vec2 cell = floor(uv);
        local = fract(uv) - 0.5;
        vec3 id = vec3(cell, layer + cells * 131.0);
        float h = hash13(id);
        vec3 centre = floor(q) / cells + 0.5 / cells;
        threshold = clamp(length(centre - Origin) / Reach, 0.0, 1.0) * (1.0 - JITTER) + h * JITTER;
        restoreThreshold = clamp(length(centre - RestoreOrigin) / Reach, 0.0, 1.0) * (1.0 - JITTER) + hash13(id.yxz + 7.0) * JITTER;
        // the snap's shock ring runs over the ground first
        if (Shock.y > 0.001) {
            float d = length(p - Origin);
            float ring = exp(-pow((d - Shock.x) / 1.6, 2.0));
            col += HotColor * ring * Shock.y * 0.9;
        }
    }

    vec3 voidCol = voidAt(dir);
    bool gone = false;
    if (Restore > 0.0) {
        // ---- coming back: cells grow out of the void behind the golden front ----------------------
        float k = (Restore - restoreThreshold) / BAND;
        if (k <= 0.0) {
            gone = true;
            // the front itself glows ahead of the returning cells
            float ahead = clamp(1.0 + k * 0.25, 0.0, 1.0);
            voidCol += GoldColor * 0.35 * ahead * ahead * Gold;
        } else if (k < 1.0) {
            if (inSquare(local, 1.0 - k)) {
                col = mix(GoldColor * 1.6, col, k * k);
            } else {
                gone = true;
                voidCol += GoldColor * 0.5 * (1.0 - k);
            }
        } else {
            col += GoldColor * 0.25 * Gold * clamp(2.0 - k * 0.1, 0.0, 1.0);
        }
    } else {
        // ---- coming apart: passed cells shrink to nothing in the hot band -------------------------
        float k = (Front - threshold) / BAND;
        if (k >= 1.0) {
            gone = true;
        } else if (k > 0.0) {
            if (inSquare(local, k)) {
                vec3 hot = mix(vec3(1.0, 0.96, 0.88), HotColor, k);
                col = mix(col, hot * 1.4, clamp(0.35 + k, 0.0, 1.0));
            } else {
                gone = true;
                voidCol += HotColor * 0.25 * (1.0 - k);
            }
        } else if (k > -1.5) {
            // just ahead of the front the world already glows
            col += HotColor * 0.22 * (1.0 + k / 1.5);
        }
    }

    if (gone) {
        fragColor = vec4(voidCol, 1.0);
        gl_FragDepth = 1.0;
    } else {
        fragColor = vec4(col, 1.0);
        gl_FragDepth = depth;
    }
}
