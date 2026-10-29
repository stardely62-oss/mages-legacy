#include mages_legacy:void

uniform float GameTime;

// Seconds, from the day's game time (24000 ticks)
#define FxTime (GameTime * 1200.0)

in vec2 texCoord;
in vec4 params;

out vec4 fragColor;

// A streak of the void along a path: r = how far the tail has drawn in (0..1), g = seed, a = strength.
vec4 ribbon(bool glowOnly) {
    float u = texCoord.x;
    float v = texCoord.y * 2.0 - 1.0;
    float tail = params.r;
    float t = FxTime;
    // Only what is ahead of the tail remains, brightest towards the head
    float along = smoothstep(tail, tail + 0.12, u) * smoothstep(1.0, 0.92, u);
    float flow = vFbm(vec2(u * 9.0 - t * 2.4, v * 1.5 + params.g * 50.0));
    float width = 0.55 + 0.35 * flow;
    float core = exp(-abs(v) * 9.0);
    float body = smoothstep(width, 0.0, abs(v));
    float head = mix(0.4, 1.0, u);
    if (glowOnly) {
        vec3 g = (V_VIOLET * body * 0.9 + V_STAR * core * 0.8) * along * head;
        return vec4(g * params.a, clamp(core * along, 0.0, 1.0) * params.a);
    }
    vec3 col = mix(V_DEEP, V_VIOLET, flow * 0.8) * body + V_STAR * core * 0.7;
    float alpha = clamp(body * 0.75 + core * 0.4, 0.0, 1.0) * along * params.a;
    return vec4(col, alpha);
}
