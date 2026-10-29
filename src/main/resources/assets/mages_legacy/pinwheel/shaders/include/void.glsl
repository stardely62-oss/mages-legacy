// Shared helpers of the Voidborn's effects: hashing, value noise, fbm, a starfield and the palette.
// The effects pass their own numbers through the vertex colour: r, g, b, a mean what each effect says.

float vHash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = vHash(i);
    float b = vHash(i + vec2(1.0, 0.0));
    float c = vHash(i + vec2(0.0, 1.0));
    float d = vHash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float vFbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vNoise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

// Stars on a grid: one at most per cell, twinkling; density 0..1
float vStars(vec2 p, float time, float density) {
    vec2 cell = floor(p);
    vec2 f = fract(p) - 0.5;
    float h = vHash(cell);
    if (h > density) {
        return 0.0;
    }
    vec2 offset = vec2(vHash(cell + 3.1), vHash(cell + 7.7)) - 0.5;
    float d = length(f - offset * 0.7);
    float twinkle = 0.55 + 0.45 * sin(time * (2.0 + h * 5.0) + h * 40.0);
    return smoothstep(0.09, 0.0, d) * twinkle;
}

const vec3 V_DEEP = vec3(0.04, 0.016, 0.09);
const vec3 V_VIOLET = vec3(0.608, 0.498, 0.831);
const vec3 V_BRIGHT = vec3(0.78, 0.62, 1.0);
const vec3 V_STAR = vec3(0.95, 0.92, 1.0);
