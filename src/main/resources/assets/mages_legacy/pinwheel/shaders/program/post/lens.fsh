uniform sampler2D DiffuseSampler0;
uniform vec2 LensCenter;
uniform float LensRadius;
uniform float LensStrength;
uniform float LensAspect;

in vec2 texCoord;

out vec4 fragColor;

// Gravitational lensing around a black hole: the picture bends round a point on the screen, harder the closer,
// and the very middle swallows the light.
void main() {
    vec2 d = texCoord - LensCenter;
    d.x *= LensAspect;
    float r = length(d);
    float fall = smoothstep(LensRadius, 0.0, r);
    float pull = LensStrength * LensRadius * LensRadius / (r * r + LensRadius * 0.02) * fall;
    vec2 dir = r > 0.0001 ? d / r : vec2(0.0);
    vec2 offset = -dir * min(pull, r) * 0.6;
    offset.x /= LensAspect;
    vec4 c = texture(DiffuseSampler0, texCoord + offset);
    float swallow = smoothstep(LensRadius * 0.16, LensRadius * 0.1, r) * step(0.0001, LensStrength);
    fragColor = vec4(c.rgb * (1.0 - swallow), 1.0);
}
