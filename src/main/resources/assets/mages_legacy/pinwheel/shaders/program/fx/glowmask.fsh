uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 params;

out vec4 fragColor;

// Only the glowing parts of a painted texture (bright cyan-green spots, veins, gill light) go into the bloom
void main() {
    vec4 c = texture(Sampler0, texCoord);
    float glow = smoothstep(0.55, 0.9, max(c.g, c.b) - c.r * 0.35) * c.a;
    if (glow < 0.01) {
        discard;
    }
    fragColor = vec4(c.rgb * glow, glow) * params;
}
