#include mages_legacy:void

uniform float GameTime;

#define FxTime (GameTime * 1200.0)

in vec2 texCoord;
in vec4 params;

out vec4 fragColor;

// The Great Bloom's mushroom, a smooth mesh. uv.x goes round (0..1), uv.y down the part (0 top .. 1 bottom).
// b is the part: < 0.3 the cap, < 0.6 the gills underneath, else the stem. r is light (how the face turns to the
// sky and the sun, 0..1), g a seed, a the strength (it fades as it withers).
const float PI = 3.14159265;
const vec3 CAP_DARK = vec3(0.07, 0.20, 0.17);
const vec3 CAP_LIGHT = vec3(0.20, 0.45, 0.36);
const vec3 SPOT = vec3(0.70, 1.00, 0.88);
const vec3 VEIN = vec3(0.45, 1.00, 0.80);
const vec3 GILL = vec3(0.86, 0.82, 0.68);
const vec3 GILL_GLOW = vec3(0.50, 1.00, 0.85);
const vec3 STEM = vec3(0.86, 0.82, 0.70);
const vec3 STEM_DARK = vec3(0.55, 0.50, 0.40);

// Round glowing spots scattered over the cap, breathing slowly
float spots(vec2 uv, float seed) {
    vec2 p = vec2(uv.x * 14.0, uv.y * 6.0);
    vec2 cell = floor(p);
    vec2 f = fract(p) - 0.5;
    float h = vHash(cell + seed);
    if (h > 0.55) {
        return 0.0;
    }
    vec2 off = vec2(vHash(cell + 1.3), vHash(cell + 5.1)) - 0.5;
    float size = 0.16 + 0.14 * vHash(cell + 9.7);
    float d = length((f - off * 0.4) * vec2(1.0, 1.6));
    float breathe = 0.7 + 0.3 * sin(FxTime * 1.6 + h * 30.0);
    return smoothstep(size, size * 0.4, d) * breathe;
}

vec4 cap(vec2 uv, bool glow) {
    float seed = params.g * 50.0;
    float mottle = vFbm(vec2(uv.x * 10.0, uv.y * 4.0) + seed);
    float veins = smoothstep(0.03, 0.0, abs(vFbm(vec2(uv.x * 16.0, uv.y * 5.0) + seed * 2.0) - 0.5)) * (0.6 + 0.4 * sin(FxTime * 2.0 + uv.x * 20.0));
    float s = spots(uv, seed);
    float rim = smoothstep(0.85, 1.0, uv.y);
    if (glow) {
        vec3 g = SPOT * s * 1.2 + VEIN * veins * 0.5 + GILL_GLOW * rim * 0.6;
        return vec4(g * params.a, clamp(s + veins * 0.4 + rim * 0.5, 0.0, 1.0) * params.a);
    }
    vec3 col = mix(CAP_DARK, CAP_LIGHT, mottle * 0.7 + (1.0 - uv.y) * 0.3);
    col *= 0.45 + 0.75 * params.r;
    col = mix(col, SPOT, s * 0.85) + VEIN * veins * 0.25 + GILL_GLOW * rim * 0.3;
    return vec4(col, params.a);
}

vec4 gills(vec2 uv, bool glow) {
    float lines = pow(0.5 + 0.5 * cos(uv.x * 2.0 * PI * 90.0), 2.0);
    // Light pulsing outwards along the gills
    float pulse = pow(0.5 + 0.5 * sin((uv.y * 3.0 + FxTime * 0.8) * 2.0 * PI), 6.0);
    if (glow) {
        return vec4(GILL_GLOW * lines * (0.3 + pulse) * params.a, clamp(lines * pulse, 0.0, 1.0) * params.a);
    }
    vec3 col = mix(GILL * 0.55, GILL, lines) + GILL_GLOW * pulse * lines * 0.5;
    return vec4(col * (0.55 + 0.45 * uv.y), params.a);
}

vec4 stem(vec2 uv, bool glow) {
    float fibres = vNoise(vec2(uv.x * 80.0, uv.y * 4.0)) * 0.25 + vNoise(vec2(uv.x * 30.0, uv.y * 20.0)) * 0.1;
    float base = smoothstep(0.75, 1.0, uv.y);
    float ring = exp(-abs(uv.y - 0.2) * 40.0);
    if (glow) {
        return vec4(GILL_GLOW * ring * 0.8 * params.a, clamp(ring, 0.0, 1.0) * params.a);
    }
    vec3 col = mix(STEM, STEM_DARK, base) * (0.8 + fibres);
    col *= 0.5 + 0.6 * params.r;
    col += GILL_GLOW * ring * 0.3;
    return vec4(col, params.a);
}

vec4 mushroom(bool glow) {
    if (params.b < 0.3) {
        return cap(texCoord, glow);
    } else if (params.b < 0.6) {
        return gills(texCoord, glow);
    }
    return stem(texCoord, glow);
}
