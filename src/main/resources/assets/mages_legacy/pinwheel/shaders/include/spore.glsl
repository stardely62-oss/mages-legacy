#include mages_legacy:void

uniform float GameTime;

#define FxTime (GameTime * 1200.0)

in vec2 texCoord;
in vec4 params;

out vec4 fragColor;

// The Mycelid's effects. The vertex colour carries the numbers: b chooses the shape, r its progress or size,
// g a seed, a the strength. uv runs 0..1 over the quad (or along a thread / round a wall).
const float PI = 3.14159265;
const vec3 M_DEEP = vec3(0.03, 0.08, 0.06);
const vec3 M_MOSS = vec3(0.36, 0.74, 0.30);
const vec3 M_TEAL = vec3(0.42, 0.96, 0.80);
const vec3 M_GOLD = vec3(0.98, 0.86, 0.45);

// Glowing specks drifting in a layer
float specks(vec2 p, float t, float density) {
    return vStars(p + vec2(t * 0.15, -t * 0.1), t, density);
}

// A cloud of neurospores lying on the ground: a soft haze thicker at the middle, motes of light in it, a faint edge
vec4 cloud(vec2 uv, bool glow) {
    float r = length(uv);
    float t = FxTime;
    float inside = smoothstep(1.0, 0.75, r);
    float haze = vFbm(uv * 3.0 + vec2(t * 0.2, -t * 0.15) + params.g * 20.0);
    float edge = exp(-abs(r - 0.95) * 25.0) * (0.6 + 0.4 * sin(atan(uv.y, uv.x) * 9.0 + t * 2.0));
    float motes = specks(uv * 14.0 + params.g * 7.0, t, 0.35) * inside;
    float body = inside * (0.35 + 0.65 * haze);
    // Wisps of spore drifting through the haze, brighter than it
    float wisps = smoothstep(0.55, 0.8, vFbm(uv * 5.0 - vec2(t * 0.35, t * 0.25) + params.g * 3.0)) * inside;
    if (glow) {
        return vec4((M_TEAL * motes * 1.6 + M_MOSS * (edge * 0.7 + wisps * 0.35)) * params.a,
                clamp(motes + edge * 0.4 + wisps * 0.25, 0.0, 1.0) * params.a);
    }
    vec3 col = mix(M_MOSS * 0.6, M_TEAL, haze * 0.6 + wisps * 0.4) + M_TEAL * motes;
    return vec4(col, clamp(body * 0.6 + wisps * 0.35 + edge * 0.5 + motes, 0.0, 1.0) * params.a);
}

// A ring of spores running outwards
vec4 ring(vec2 uv, bool glow) {
    float r = length(uv);
    float band = exp(-abs(r - params.r) * 28.0);
    float grain = 0.7 + 0.3 * vNoise(vec2(atan(uv.y, uv.x) * 10.0, FxTime * 4.0));
    if (glow) {
        return vec4(M_TEAL * band * 1.3 * params.a, clamp(band, 0.0, 1.0) * params.a);
    }
    return vec4(mix(M_MOSS, M_TEAL, band) * grain, clamp(band * grain, 0.0, 1.0) * params.a * 0.85);
}

// A living thread between two bodies: a fine glowing fibre with pulses running along it from the Mycelid
vec4 thread(vec2 uv, bool glow) {
    float across = abs(uv.y - 0.5) * 2.0;
    float core = smoothstep(1.0, 0.0, across);
    core *= core;
    float pulse = pow(0.5 + 0.5 * sin((uv.x * 6.0 - FxTime * 3.0) * PI), 8.0);
    float fray = vNoise(vec2(uv.x * 30.0, FxTime * 2.0)) * 0.35;
    float v = core * (0.55 + fray) + core * pulse * 1.2;
    if (glow) {
        return vec4((M_TEAL * core * 0.8 + M_GOLD * pulse * core * 1.4) * params.a, clamp(v, 0.0, 1.0) * params.a);
    }
    return vec4(mix(M_MOSS, M_TEAL, pulse), clamp(v, 0.0, 1.0) * params.a);
}

// A small aura under one sharing the symbiosis: a turning ring of runes made of spores
vec4 aura(vec2 uv, bool glow) {
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float ring = exp(-abs(r - 0.8) * 22.0);
    float runes = step(0.55, fract(a / (2.0 * PI) * 12.0 + FxTime * 0.25)) * exp(-abs(r - 0.62) * 30.0);
    float v = ring + runes * 0.8;
    if (glow) {
        return vec4(M_TEAL * v * params.a, clamp(v, 0.0, 1.0) * params.a);
    }
    return vec4(mix(M_MOSS, M_TEAL, ring), clamp(v, 0.0, 1.0) * params.a * 0.8);
}

// The Spore Storm on the ground: six arms of spores spiralling in, turning
vec4 spiral(vec2 uv, bool glow) {
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float t = FxTime;
    float inside = smoothstep(1.0, 0.85, r) * smoothstep(0.0, 0.15, r);
    float arms = pow(0.5 + 0.5 * sin(a * 6.0 + log(max(r, 0.02)) * 7.0 - t * 3.0), 3.0);
    float haze = vFbm(vec2(a * 3.0 + t * 0.6, r * 5.0 - t) + params.g * 10.0);
    float motes = specks(uv * 18.0, t, 0.3) * inside;
    float v = inside * (arms * 0.7 + haze * 0.35);
    if (glow) {
        return vec4((M_TEAL * arms * 0.6 * inside + M_GOLD * motes * 1.5) * params.a, clamp(arms * inside * 0.5 + motes, 0.0, 1.0) * params.a);
    }
    vec3 col = mix(M_MOSS, M_TEAL, arms) * (0.6 + 0.4 * haze) + M_GOLD * motes;
    return vec4(col, clamp(v * 0.6 + motes, 0.0, 1.0) * params.a);
}

// The storm's wall: a curtain of turning spore-haze round its edge, thinning towards the top. uv.x goes round
vec4 wall(vec2 uv, bool glow) {
    float t = FxTime;
    float h = uv.y;
    float fade = smoothstep(0.0, 0.15, h) * smoothstep(1.0, 0.45, h);
    float streaks = vFbm(vec2(uv.x * 24.0 - t * 2.5 + h * 3.0, h * 4.0 - t * 0.5) + params.g * 5.0);
    float bands = pow(0.5 + 0.5 * sin(uv.x * 2.0 * PI * 6.0 - t * 4.0 + h * 6.0), 4.0);
    float motes = specks(vec2(uv.x * 60.0, h * 12.0), t, 0.25);
    float v = fade * (streaks * 0.6 + bands * 0.4);
    if (glow) {
        return vec4((M_TEAL * bands * fade * 0.5 + M_GOLD * motes * fade * 1.4) * params.a, clamp(bands * fade * 0.4 + motes * fade, 0.0, 1.0) * params.a);
    }
    vec3 col = mix(M_MOSS, M_TEAL, bands) * (0.55 + 0.45 * streaks) + M_GOLD * motes;
    return vec4(col, clamp(v * 0.7 + motes * fade, 0.0, 1.0) * params.a);
}

// The Great Bloom's healing field: its edge glowing on the ground, faint drifting light inside
vec4 field(vec2 uv, bool glow) {
    float r = length(uv);
    float t = FxTime;
    float edge = exp(-abs(r - 0.97) * 60.0) * (0.7 + 0.3 * sin(atan(uv.y, uv.x) * 40.0 + t * 1.5));
    float inner = smoothstep(1.0, 0.9, r) * 0.08 * (0.5 + 0.5 * sin(r * 40.0 - t * 2.0));
    if (glow) {
        return vec4(M_TEAL * edge * params.a, clamp(edge, 0.0, 1.0) * params.a);
    }
    return vec4(M_TEAL, clamp(edge * 0.8 + inner, 0.0, 1.0) * params.a);
}

vec4 spore(bool glow) {
    float b = params.b;
    vec2 uv = texCoord * 2.0 - 1.0;
    if (b < 0.15) {
        return cloud(uv, glow);
    } else if (b < 0.3) {
        return ring(uv, glow);
    } else if (b < 0.45) {
        return thread(texCoord, glow);
    } else if (b < 0.6) {
        return aura(uv, glow);
    } else if (b < 0.75) {
        return spiral(uv, glow);
    } else if (b < 0.9) {
        return wall(texCoord, glow);
    }
    return field(uv, glow);
}
