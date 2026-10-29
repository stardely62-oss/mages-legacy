uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 params;

out vec4 fragColor;

// A particle's sprite (the first version's void motes and stars), tinted by the vertex colour
void main() {
    vec4 c = texture(Sampler0, texCoord) * params;
    if (c.a < 0.01) {
        discard;
    }
    fragColor = c;
}
