#version 150

uniform sampler2D DiffuseSampler;
// YENİ: Bir önceki frame'in çıktısı. post/dash_impact.json'daki "prev" target'ı.
uniform sampler2D PrevSampler;

uniform float DashIntensity;
uniform float PunchIntensity;
uniform float ShakeIntensity;
uniform float GrabIntensity;
uniform float LockIntensity;
uniform float Time;

uniform float ChopIntensity;
uniform float ChopType;
uniform float ChopIsLeft;

uniform float BarrageIntensity;
uniform float BarrageIsLeft;

// YENİ: SCOURGE VIRUS
uniform float ScourgeIntensity;   // 0..1 (amplifier ile 1.5'e kadar)
uniform float ScourgeTurn;        // 0..1, kamera ne kadar hızlı dönüyor

// REGULUS: Lion overheat cracks+noise, heart-loss flash, madness bands/glitch/zoom
uniform float RegulusOverheat;    // 0..1
uniform float RegulusHeartFlash;  // 0..1
uniform float RegulusMadness;     // 0..1
uniform float RegulusPulse;       // 0..1 heartbeat pulse synced with HUD/sound

// World erasure: same chain, all neutral defaults preserve the legacy idle image.
uniform float EraseCrack;
uniform float EraseShatter;
uniform float EraseDark;
uniform float EraseRebuild;
uniform float EraseSeed;

// DIO ZA WARUDO: stopped-time grade (0..1), the inverted flash of the wave (0..1) and the ring radius (0 = off).
uniform float TimeStopGrey;
uniform float TimeStopInvert;
uniform float TimeStopRipple;

in vec2 texCoord;
out vec4 fragColor;

// Six deterministic sites: centre and five seeded impact points.
vec2 eraseSite(int i) {
    if (i == 0) return vec2(0.5);
    float n = float(i) + clamp(EraseSeed, 0.0, 1.0) * 1024.0;
    return vec2(0.08) + 0.84 * fract(sin(vec2(n * 12.9898, n * 78.233)) * 43758.5453);
}
float eraseDistance(vec2 a, vec2 b) {
    vec2 size = vec2(textureSize(DiffuseSampler, 0));
    vec2 metric = vec2(size.x / max(size.y, 1.0), 1.0);
    vec2 delta = (a - b) * metric;
    return dot(delta, delta);
}
int eraseNearest(vec2 uv) {
    float best = 1000.0;
    int cell = 0;
    for (int i = 0; i < 6; i++) {
        float d = eraseDistance(uv, eraseSite(i));
        if (d < best) { best = d; cell = i; }
    }
    return cell;
}
vec3 eraseCracks(vec2 uv, float amount) {
    float first = 1000.0, second = 1000.0;
    int cell = 0;
    for (int i = 0; i < 6; i++) {
        float d = eraseDistance(uv, eraseSite(i));
        if (d < first) { second = first; first = d; cell = i; }
        else if (d < second) second = d;
    }
    float gap = sqrt(second) - sqrt(first);
    float line = 1.0 - smoothstep(0.0015, 0.004, gap);
    float reveal = 1.0 - smoothstep(max(0.0, amount - 0.06), max(0.001, amount), sqrt(first));
    float edge = line * reveal * amount;
    vec2 refract = normalize(uv - eraseSite(cell) + vec2(0.0001)) * edge * 0.006;
    // Steel-blue fracture rim and a narrow white core; all other hero paths stay unchanged.
    float rim = (1.0 - smoothstep(0.004, 0.012, gap)) * reveal * amount;
    vec3 fracture = vec3(0.36, 0.58, 0.85) * rim * 0.35 + vec3(1.0, 0.95, 0.80) * edge * 0.65;
    return texture(DiffuseSampler, clamp(uv + refract, 0.0, 1.0)).rgb + fracture;
}

void main() {
    vec2 baseUv = texCoord;
    vec2 center = vec2(0.5, 0.5);

    float safeDash = clamp(DashIntensity, 0.0, 1.5);
    float safePunch = clamp(PunchIntensity, 0.0, 1.5);
    float safeShake = clamp(ShakeIntensity, 0.0, 1.5);
    float safeGrab = clamp(GrabIntensity, 0.0, 1.0);
    float safeLock = clamp(LockIntensity, 0.0, 1.0);
    float safeChop = clamp(ChopIntensity, 0.0, 1.0);
    float safeBarrage = clamp(BarrageIntensity, 0.0, 1.0);
    float safeScourge = clamp(ScourgeIntensity, 0.0, 1.5);
    float safeTurn = clamp(ScourgeTurn, 0.0, 1.0);
    float safeOverheat = clamp(RegulusOverheat, 0.0, 1.0);
    float safeHeartFlash = clamp(RegulusHeartFlash, 0.0, 1.0);
    float safeMadness = clamp(RegulusMadness, 0.0, 1.0);
    float safePulse = clamp(RegulusPulse, 0.0, 1.0);
    float safeEraseCrack = clamp(EraseCrack, 0.0, 1.0);
    float safeEraseShatter = clamp(EraseShatter, 0.0, 1.0);
    float safeEraseDark = clamp(EraseDark, 0.0, 1.0);
    float safeEraseRebuild = clamp(EraseRebuild, 0.0, 1.0);
    float safeTsGrey = clamp(TimeStopGrey, 0.0, 1.0);
    float safeTsInvert = clamp(TimeStopInvert, 0.0, 1.0);
    float safeTsRipple = clamp(TimeStopRipple, 0.0, 1.5);

    // Hiçbir efekt yoksa boş döndür (Performans)
    if (safeDash <= 0.001 && safePunch <= 0.001 && safeShake <= 0.001 && safeGrab <= 0.001 && safeLock <= 0.001 && safeChop <= 0.001 && safeBarrage <= 0.001 && safeScourge <= 0.001 && safeOverheat <= 0.001 && safeHeartFlash <= 0.001 && safeMadness <= 0.001 && safeEraseCrack + safeEraseShatter + safeEraseDark + safeEraseRebuild == 0.0 && safeTsGrey + safeTsInvert + safeTsRipple == 0.0) {
        vec4 defaultColor = texture(DiffuseSampler, baseUv);
        defaultColor.a = 1.0;
        fragColor = defaultColor;
        return;
    }

    // ==========================================
    // CAMERA SHAKE (Kamera Titremesi)
    // ==========================================
    float shakeAmplitude = (0.015 * safeDash) + (0.032 * safeShake) + (0.005 * safeGrab);
    float shakeX = sin(Time * (80.0 + (safeShake * 40.0) + (safeGrab * 150.0))) * shakeAmplitude;
    float shakeY = cos(Time * (95.0 + (safeShake * 40.0) + (safeGrab * 150.0))) * shakeAmplitude;

    // CHOP KIRBACI
    if (safeChop > 0.0) {
        float baseDir = (ChopIsLeft > 0.5) ? -1.0 : 1.0;
        float typeMultiplier = (ChopType > 0.5) ? 1.0 : -1.0;
        float chopDir = baseDir * typeMultiplier;
        shakeX += sin(safeChop * 3.1415) * 0.025 * chopDir;
    }

    // BARRAGE TOKADI (Sadece vuruş anında ekranı sağa/sola sertçe teper)
    if (safeBarrage > 0.0) {
        float barrageDir = (BarrageIsLeft > 0.5) ? -1.0 : 1.0;
        shakeX += (safeBarrage * 0.0025 * barrageDir); // Yatayda sert darbe
        shakeY += (safeBarrage * 0.0015 * sin(Time * 200.0)); // Dikeyde mikro titreme
    }

    // SCOURGE: sürekli hastalıklı mikro titreşim, kafa çevirince belirginleşir
    if (safeScourge > 0.0) {
        float jitter = safeScourge * (0.35 + 0.65 * safeTurn);
        shakeX += sin(Time * 27.0) * 0.0018 * jitter;
        shakeY += cos(Time * 31.0) * 0.0015 * jitter;
    }

    baseUv.x += shakeX;
    baseUv.y += shakeY;

    // ==========================================
    // REGULUS MADNESS: light zoom + glitch slices (pre-sampling UV work)
    // ==========================================
    if (safeMadness > 0.0) {
        float zoom = 1.0 - 0.06 * safeMadness * (0.55 + 0.45 * safePulse);
        baseUv = center + (baseUv - center) * zoom;

        float glitchRow = floor(baseUv.y * 36.0);
        float glitchSeed = fract(sin(glitchRow * 7.31 + floor(Time * 6.0) * 13.7) * 43758.5453);
        float slice = step(0.972, glitchSeed);
        float sliceDir = (fract(sin(glitchRow * 3.3 + floor(Time * 6.0)) * 24634.6345) - 0.5) * 2.0;
        baseUv.x += slice * sliceDir * 0.018 * safeMadness;
    }

    float dist = distance(baseUv, center);
    float effectRadius = smoothstep(0.1, 0.8, dist);

    float dashPower = safeDash * effectRadius;
    float punchPower = safePunch * effectRadius;

    vec4 finalColor;

    // ==========================================
    // 1. CHOP EFEKTİ
    // ==========================================
    if (safeChop > 0.0) {
        vec2 toCenter = baseUv - center;
        vec4 chopColor = vec4(0.0);

        float baseSwirlDir = (ChopIsLeft > 0.5) ? -1.0 : 1.0;
        float typeSwirlMult = (ChopType > 0.5) ? 1.0 : -1.0;
        float swirlDir = baseSwirlDir * typeSwirlMult;

        float swirlStrength = safeChop * 0.25 * smoothstep(0.1, 0.9, dist) * swirlDir;
        for(int i = 0; i < 5; i++) {
            float angle = swirlStrength * (float(i) / 4.0);
            float s = sin(angle);
            float c = cos(angle);
            vec2 rotUv = vec2(toCenter.x * c - toCenter.y * s, toCenter.x * s + toCenter.y * c) + center;
            chopColor += texture(DiffuseSampler, rotUv);
        }
        chopColor /= 5.0;
        chopColor.r += safeChop * 0.1 * effectRadius;
        chopColor.b -= safeChop * 0.035 * effectRadius;

        finalColor = chopColor;
    }
    // ==========================================
    // 2. BARRAGE (SERİ YUMRUK) EFEKTİ
    // ==========================================
    else if (safeBarrage > 0.0) {
        vec2 toCenter = baseUv - center;

        // Vuruş anında ekranı oyuncuya doğru hafifçe ezer (Impact Zoom)
        vec2 bUv = baseUv + toCenter * (safeBarrage * -0.04);

        vec4 bColor = texture(DiffuseSampler, bUv);

        // Altın sarısı/vahşet tonu ekler
        bColor.r += safeBarrage * 0.15 * effectRadius;
        bColor.g += safeBarrage * 0.05 * effectRadius;
        bColor.b -= safeBarrage * 0.05 * effectRadius;

        finalColor = bColor;
    }
    // ==========================================
    // 3. PUNCH / DASH EFEKTİ
    // ==========================================
    else if ((dashPower + punchPower) > 0.0 && dist > 0.001) {
        vec2 dir = normalize(baseUv - center);
        float caOffset = (0.065 * dashPower) + (0.12 * punchPower);
        float r = texture(DiffuseSampler, baseUv + dir * caOffset).r;
        float g = texture(DiffuseSampler, baseUv).g;
        float b = texture(DiffuseSampler, baseUv - dir * caOffset).b;
        vec4 caColor = vec4(r, g, b, 1.0);

        if (safePunch > 0.0) {
            caColor.r += safePunch * 0.25 * effectRadius;
            caColor.g -= safePunch * 0.05 * effectRadius;
            caColor.b -= safePunch * 0.05 * effectRadius;
        }

        float blurStrength = (0.081 * dashPower) + (0.18 * punchPower);
        vec4 blurColor = vec4(0.0);
        blurColor += texture(DiffuseSampler, baseUv + dir * (blurStrength * 0.00));
        blurColor += texture(DiffuseSampler, baseUv + dir * (blurStrength * 0.25));
        blurColor += texture(DiffuseSampler, baseUv + dir * (blurStrength * 0.50));
        blurColor += texture(DiffuseSampler, baseUv + dir * (blurStrength * 0.75));
        blurColor += texture(DiffuseSampler, baseUv + dir * (blurStrength * 1.00));
        blurColor /= 5.0;

        finalColor = mix(caColor, blurColor, 0.6);
    } else {
        finalColor = texture(DiffuseSampler, baseUv);
    }

    // ==========================================
    // 4. TARGET LOCK VIGNETTE
    // ==========================================
    if (safeLock > 0.0) {
        float vignette = smoothstep(0.85, 0.35, dist);
        finalColor.rgb = mix(finalColor.rgb, finalColor.rgb * vignette, safeLock * 0.85);
    }

    // ==========================================
    // 5. SCOURGE VIRUS
    //
    // En sonda duruyor ki iz (trail) bir önceki frame'in NİHAİ çıktısını okusun.
    // "prev" target'ına yazılan da bu; yani geri besleme döngüsü kapanıyor.
    // ==========================================
    // ==========================================
    // 5. SCOURGE VIRUS
    // ==========================================
    if (safeScourge > 0.0) {
        float s = min(safeScourge, 1.0);

        // --- Hastalıklı yeşil ---
        float lum = dot(finalColor.rgb, vec3(0.299, 0.587, 0.114));
        vec3 sick = vec3(lum * 0.30, lum * 1.10 + 0.04, lum * 0.32);
        finalColor.rgb = mix(finalColor.rgb, sick, s * 0.78);

        // --- Nabız gibi atan vinyet ---
        // Geri beslemede her frame yeniden uygulandığı için birikiyor; o yüzden hafif.
        float pulse = 0.5 + 0.5 * sin(Time * 2.2);
        float vig = smoothstep(1.05, 0.35, dist);
        finalColor.rgb *= mix(1.0, vig, s * (0.12 + 0.08 * pulse));

        // --- Geride kalma izi ---
        // İz EN SONDA, tonlamadan sonra: prev zaten tonlanmış görüntüyü tutuyor,
        // böylece döngü renk olarak tutarlı kalıyor.
        // 0.62 durumda hafif smear, kafa çevirince 0.92'ye çıkıp uzun kuyruk bırakıyor.
        float persistence = (0.72 + 0.30 * safeTurn) * s;
        vec3 prev = texture(PrevSampler, baseUv).rgb;
        finalColor.rgb = mix(finalColor.rgb, prev, persistence);

        // --- Grenli titreşim ---
        float grain = fract(sin(dot(baseUv * 512.0 + Time * 3.0, vec2(12.9898, 78.233))) * 43758.5453);
        finalColor.rgb += (grain - 0.5) * 0.02 * s;
    }

    // ==========================================
    // 6. REGULUS LION OVERHEAT: white noise + screen-edge cracks
    // ==========================================
    if (safeOverheat > 0.0) {
        float noise = fract(sin(dot(baseUv * 731.0 + Time * 29.0, vec2(12.9898, 78.233))) * 43758.5453);
        finalColor.rgb += vec3(noise * 0.20 * safeOverheat);

        // Cell-based crack pattern near the screen edges.
        vec2 cell = floor(baseUv * vec2(18.0, 11.0));
        float crackSeed = fract(sin(dot(cell + floor(Time * 1.5) * 0.37, vec2(26.65, 114.5))) * 24634.6345);
        float crack = step(0.935, crackSeed) * smoothstep(0.35, 0.9, dist);
        finalColor.rgb += vec3(0.75, 0.85, 1.0) * crack * 0.55 * safeOverheat;
    }

    // ==========================================
    // 7. REGULUS HEART LOSS: short red edge flash
    // ==========================================
    if (safeHeartFlash > 0.0) {
        float edge = smoothstep(0.2, 0.85, dist);
        finalColor.rgb = mix(finalColor.rgb, vec3(0.62, 0.04, 0.02), safeHeartFlash * (0.25 + 0.55 * edge));
    }

    // ==========================================
    // 8. REGULUS MADNESS: black bands + heartbeat vignette
    // (zoom + glitch are applied on baseUv before the colour chain)
    // ==========================================
    if (safeMadness > 0.0) {
        float bandEdge = 0.085 * safeMadness;
        if (baseUv.y < bandEdge || baseUv.y > 1.0 - bandEdge) {
            finalColor.rgb *= 0.06 + 0.08 * safeMadness;
        }

        float vig = smoothstep(0.95, 0.35, dist);
        vec3 bloodTint = vec3(0.10, 0.01, 0.01) + finalColor.rgb * vec3(0.9, 0.3, 0.3);
        finalColor.rgb = mix(finalColor.rgb, bloodTint, safeMadness * 0.30 * (0.45 + 0.55 * safePulse));
        finalColor.rgb *= mix(1.0, 0.72 + 0.28 * vig, safeMadness * 0.35 * (0.6 + 0.4 * safePulse));
    }

    // DIO stopped time: a refracting ring sweeps out from the centre, the image inverts under the
    // wave, then everything settles into a cold desaturated grade until colour returns.
    if (safeTsGrey + safeTsInvert + safeTsRipple > 0.0) {
        vec2 fromCenter = baseUv - vec2(0.5);
        vec2 texSize = vec2(textureSize(DiffuseSampler, 0));
        float aspect = texSize.x / max(texSize.y, 1.0);
        float rDist = length(vec2(fromCenter.x * aspect, fromCenter.y));
        if (safeTsRipple > 0.0) {
            float ring = safeTsRipple * 1.1;
            float band = exp(-pow((rDist - ring) / 0.045, 2.0));
            vec2 dir = rDist > 0.0001 ? fromCenter / rDist : vec2(0.0);
            vec3 bent = texture(DiffuseSampler, clamp(baseUv - dir * band * 0.03, 0.0, 1.0)).rgb;
            finalColor.rgb = mix(finalColor.rgb, bent, band);
            if (rDist < ring) finalColor.rgb = mix(finalColor.rgb, vec3(1.0) - finalColor.rgb, safeTsInvert * smoothstep(ring - 0.35, ring, rDist));
            finalColor.rgb += vec3(0.25, 0.55, 0.5) * band * 0.35;
        } else {
            finalColor.rgb = mix(finalColor.rgb, vec3(1.0) - finalColor.rgb, safeTsInvert);
        }

        float luma = dot(finalColor.rgb, vec3(0.299, 0.587, 0.114));
        vec3 cold = vec3(luma) * vec3(0.86, 0.95, 1.04) + vec3(0.02, 0.03, 0.05);
        finalColor.rgb = mix(finalColor.rgb, cold, safeTsGrey * 0.88);
        finalColor.rgb *= mix(1.0, 0.82 + 0.18 * smoothstep(0.95, 0.3, rDist), safeTsGrey);
    }

    // Priority cinematic. Black holes behind displaced cells; inverse mapping
    // samples only the original cell, rather than stretching the whole image.
    if (safeEraseCrack + safeEraseShatter + safeEraseDark + safeEraseRebuild > 0.0) {
        vec3 erased = eraseCracks(texCoord, safeEraseCrack);
        if (safeEraseShatter > 0.0 || safeEraseRebuild > 0.0) {
            erased = vec3(0.0);
            bool rebuilding = safeEraseRebuild > 0.0;
            // Returning displacement decreases cubically: ease-out, reverse gravity.
            float fall = rebuilding ? pow(safeEraseRebuild, 3.0) : safeEraseShatter * safeEraseShatter;
            float rotation = rebuilding ? pow(safeEraseRebuild, 3.0) : safeEraseShatter;
            float shade = 1.0 - 0.65 * (rebuilding ? safeEraseRebuild : safeEraseShatter);
            for (int i = 0; i < 6; i++) {
                vec2 site = eraseSite(i);
                float random = fract(sin(float(i) * 31.7 + clamp(EraseSeed, 0.0, 1.0) * 79.3) * 24634.6345);
                vec2 offset = vec2((site.x - 0.5) * 0.22, -(1.25 + 0.35 * random)) * fall;
                float angle = (random - 0.5) * 0.44 * rotation;
                float s = sin(-angle), c = cos(-angle);
                vec2 local = texCoord - site - offset;
                vec2 sourceUv = site + vec2(local.x*c - local.y*s, local.x*s + local.y*c);
                if (all(greaterThanEqual(sourceUv, vec2(0.0))) && all(lessThanEqual(sourceUv, vec2(1.0))) && eraseNearest(sourceUv) == i) {
                    erased = eraseCracks(sourceUv, safeEraseCrack) * shade;
                }
            }
        }
        finalColor.rgb = mix(erased, vec3(0.0), safeEraseDark);
    }

    finalColor.a = 1.0;
    fragColor = finalColor;
}
