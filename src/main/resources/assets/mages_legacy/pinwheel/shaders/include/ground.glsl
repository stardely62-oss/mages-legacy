#include mages_legacy:void

uniform float GameTime;

// Seconds, from the day's game time (24000 ticks)
#define FxTime (GameTime * 1200.0)

in vec2 texCoord;
in vec4 params;

out vec4 fragColor;

// Marks lying flat on the ground (or tilted, for the disk). b chooses: < 0.2 cracks, < 0.45 a ring wave,
// < 0.7 the gravity lens, else the accretion disk. r = progress or size, g = seed, a = strength.
const float PI = 3.14159265;

vec4 cracks(vec2 uv, bool glowOnly) {
    float grow = params.r;
    float seed = params.g * 113.0;
    float r = length(uv);
    float ang = atan(uv.y, uv.x);
    // Seven jagged fissures out from the middle, each a thin dark gash with violet lips
    float n = 7.0;
    float a = ang / (2.0 * PI) * n + vHash(vec2(seed, 1.0)) * n;
    float idx = floor(a);
    float wobble = (vNoise(vec2(r * 7.0, idx * 3.1 + seed)) - 0.5) * 0.9;
    float f = fract(a) - 0.5 + wobble * 0.35;
    float len = 0.55 + 0.45 * vHash(vec2(idx, seed));
    float reach = step(r, len * grow);
    float w = mix(0.11, 0.02, clamp(r / max(len, 0.01), 0.0, 1.0));
    float dist = abs(f) / n * 2.0 * PI * max(r, 0.05);
    float gash = smoothstep(w, w * 0.4, dist) * reach;
    float lip = exp(-max(dist - w * 0.4, 0.0) * 40.0) * reach;
    float stars = vStars(uv * 22.0 + seed, FxTime, 0.3) * gash;
    if (glowOnly) {
        return vec4((V_VIOLET * lip * 1.6 + V_STAR * stars * 2.0) * params.a, clamp(lip, 0.0, 1.0) * params.a);
    }
    vec3 col = V_DEEP * 0.4 * gash + V_VIOLET * lip * 0.9 + V_STAR * stars;
    return vec4(col, clamp(gash * 0.95 + lip * 0.6, 0.0, 1.0) * params.a);
}

vec4 ringWave(vec2 uv, bool glowOnly) {
    float radius = params.r;
    float r = length(uv);
    float band = exp(-abs(r - radius) * 30.0);
    float trail = smoothstep(radius, radius - 0.25, r) * step(r, radius) * 0.35;
    float wobble = 0.85 + 0.15 * vNoise(vec2(atan(uv.y, uv.x) * 6.0, FxTime * 3.0));
    float v = (band + trail * 0.5) * wobble;
    if (glowOnly) {
        return vec4((V_BRIGHT * band * 1.4 + V_VIOLET * trail * 0.4) * params.a, clamp(band, 0.0, 1.0) * params.a);
    }
    return vec4(V_VIOLET * v + V_STAR * band * 0.4, clamp(v, 0.0, 1.0) * params.a * 0.8);
}

vec4 lens(vec2 uv, bool glowOnly) {
    float r = length(uv);
    float ang = atan(uv.y, uv.x);
    float t = FxTime;
    float inside = smoothstep(1.0, 0.92, r);
    // Rings tightening towards the middle, again and again
    float rings = pow(0.5 + 0.5 * sin((r + t * 0.9) * 20.0), 6.0) * inside * smoothstep(0.0, 0.25, r);
    // The ground's own pattern dragged into a slow spiral
    float swirl = vFbm(vec2(ang * 2.0 + r * 4.0 - t * 0.8, r * 3.0 - t * 0.4) + params.g * 30.0);
    float rim = exp(-abs(r - 0.96) * 35.0);
    float dark = inside * (0.55 + 0.3 * swirl) * smoothstep(0.0, 0.6, r + 0.3);
    if (glowOnly) {
        return vec4((V_VIOLET * (rings * 0.8 + rim * 1.5)) * params.a, clamp(rings * 0.5 + rim, 0.0, 1.0) * params.a);
    }
    vec3 col = V_DEEP * dark + V_VIOLET * (rings * 0.6 + rim * 0.9 + swirl * 0.12 * inside);
    return vec4(col, clamp(dark * 0.8 + rings * 0.5 + rim, 0.0, 1.0) * params.a);
}

vec4 disk(vec2 uv, bool glowOnly) {
    float r = length(uv);
    float ang = atan(uv.y, uv.x);
    float t = FxTime;
    float inner = 0.28;
    float body = smoothstep(inner, inner + 0.04, r) * smoothstep(1.0, 0.6, r);
    // Arms of fire spiralling in: hot white close to the core, violet, then dark at the rim
    float spiral = 0.5 + 0.5 * sin(ang * 3.0 + log(max(r, 0.01)) * 9.0 + t * 4.0);
    float fire = vFbm(vec2(ang * 3.0 + t * 2.0, r * 6.0 - t * 3.0));
    float heat = smoothstep(1.0, inner, r);
    vec3 col = mix(V_VIOLET * 0.6, V_STAR, heat * heat) * (0.5 + 0.8 * spiral * fire);
    float a = body * (0.35 + 0.65 * spiral * fire);
    if (glowOnly) {
        return vec4(col * a * (0.8 + heat) * params.a, clamp(a, 0.0, 1.0) * params.a);
    }
    return vec4(col, clamp(a, 0.0, 1.0) * params.a);
}

vec4 ground(bool glowOnly) {
    vec2 uv = texCoord * 2.0 - 1.0;
    float mode = params.b;
    if (mode < 0.2) {
        return cracks(uv, glowOnly);
    }
    if (mode < 0.45) {
        return ringWave(uv, glowOnly);
    }
    if (mode < 0.7) {
        return lens(uv, glowOnly);
    }
    return disk(uv, glowOnly);
}
