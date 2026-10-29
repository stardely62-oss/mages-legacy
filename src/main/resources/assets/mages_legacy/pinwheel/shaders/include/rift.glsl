#include mages_legacy:void

uniform float GameTime;

// Seconds, from the day's game time (24000 ticks)
#define FxTime (GameTime * 1200.0)

in vec2 texCoord;
in vec4 params;

out vec4 fragColor;

// A tear in space on its quad: r = how open (0..1+), g = seed, b = sealing (0..1), a = strength.
// Returns the colour; glowOnly keeps the bright lips for the bloom buffer.
vec4 rift(bool glowOnly) {
    // The quad is wider and taller than the tear itself, for its halo
    vec2 uv = (texCoord * 2.0 - 1.0) * vec2(1.6, 1.15);
    float open = params.r * 1.25;
    float seed = params.g * 97.0;
    float seal = params.b;
    float t = FxTime;
    float y = clamp(uv.y, -1.0, 1.0);
    float profile = pow(max(0.0, 1.0 - y * y), 0.75) * step(abs(uv.y), 1.0);
    // Jagged lips that never stop crawling
    float jagL = (vFbm(vec2(y * 5.0 + seed, t * 0.9)) - 0.5) * 0.45;
    float jagR = (vFbm(vec2(y * 5.0 - seed, t * 0.9 + 3.0)) - 0.5) * 0.45;
    float bend = (vNoise(vec2(y * 1.6 + seed, t * 0.3)) - 0.5) * 0.25 * profile;
    float x = uv.x - bend;
    float halfL = profile * open * (0.85 + jagL);
    float halfR = profile * open * (0.85 + jagR);
    float d = x < 0.0 ? -x - halfL : x - halfR;
    float inside = smoothstep(0.03, -0.03, d) * step(0.001, profile * open);
    float lip = exp(-abs(d) * 26.0) * step(0.001, profile);
    float halo = exp(-max(d, 0.0) * 4.5) * profile * 0.55;
    // As it seals, only a white seam is left along the middle
    float seam = exp(-abs(x) * 40.0) * profile * seal;
    if (glowOnly) {
        vec3 glow = V_VIOLET * (lip * 2.2 + halo * 0.6) + V_STAR * (lip * 0.9 + seam * 2.5);
        float a = clamp(lip + halo * 0.4 + seam, 0.0, 1.0) * params.a;
        return vec4(glow * params.a, a);
    }
    // Inside: the void itself, a slow violet nebula and stars far away, drifting
    vec2 deep = uv * vec2(2.4, 1.6) + vec2(t * 0.05, -t * 0.12);
    float neb = vFbm(deep * 1.7 + seed);
    float stars = vStars(uv * vec2(16.0, 22.0) + vec2(0.0, t * 0.6), t, 0.18)
            + vStars(uv * vec2(30.0, 40.0) + vec2(seed, t * 0.25), t, 0.25) * 0.6;
    vec3 inner = V_DEEP + V_VIOLET * pow(neb, 2.5) * 0.55 + V_STAR * stars;
    vec3 col = inner * inside + V_BRIGHT * lip * 1.4 + V_STAR * lip * 0.6 + V_VIOLET * halo * 0.5 + V_STAR * seam;
    float alpha = clamp(inside * 0.97 + lip + halo * 0.6 + seam, 0.0, 1.0) * params.a;
    return vec4(col, alpha);
}
