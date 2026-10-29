#include mages_legacy:void

uniform float GameTime;

// Seconds, from the day's game time (24000 ticks)
#define FxTime (GameTime * 1200.0)

in vec2 texCoord;
in vec4 params;

out vec4 fragColor;

// Round things facing the camera. b chooses: < 0.3 a swelling shell (r = radius 0..1), < 0.6 the black hole's
// core with its ring of light, else a shell folding inwards (r = radius). g = seed, a = strength.
vec4 orb(bool glowOnly) {
    vec2 uv = texCoord * 2.0 - 1.0;
    float r = length(uv);
    float mode = params.b;
    float t = FxTime;
    if (mode < 0.3 || mode >= 0.6) {
        float radius = params.r;
        float shell = exp(-abs(r - radius) * 18.0) * smoothstep(1.0, 0.95, r);
        float wisp = vNoise(vec2(atan(uv.y, uv.x) * 5.0 + params.g * 20.0, t * 2.0));
        float v = shell * (0.6 + 0.6 * wisp);
        if (glowOnly) {
            return vec4(V_VIOLET * v * 1.4 * params.a, clamp(v, 0.0, 1.0) * params.a);
        }
        vec3 col = mix(V_VIOLET, V_STAR, shell * 0.4) * v + V_DEEP * smoothstep(radius, radius - 0.3, r) * 0.3;
        return vec4(col, clamp(v * 0.8, 0.0, 1.0) * params.a);
    }
    // The black hole: nothing at all in the middle, light bent into a thin white ring at its edge, a shadow round it
    float core = 0.34;
    float hole = smoothstep(core + 0.01, core - 0.01, r);
    float ring = exp(-abs(r - core * 1.05) * 60.0);
    float flicker = 0.85 + 0.15 * vNoise(vec2(atan(uv.y, uv.x) * 8.0, t * 6.0));
    float shadow = smoothstep(core * 2.2, core, r) * (1.0 - hole);
    float halo = exp(-max(r - core, 0.0) * 6.0) * (1.0 - hole) * 0.5;
    if (glowOnly) {
        return vec4((V_STAR * ring * 2.2 * flicker + V_VIOLET * halo * 0.8) * params.a, clamp(ring + halo * 0.5, 0.0, 1.0) * params.a);
    }
    vec3 col = vec3(0.0) * hole + V_STAR * ring * flicker + V_VIOLET * halo * 0.4;
    float a = clamp(hole + ring + shadow * 0.7 + halo * 0.4, 0.0, 1.0);
    return vec4(col, a * params.a);
}
