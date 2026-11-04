uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 params;

out vec4 fragColor;

// A solid piece of pixel-art metal (an extruded sprite): its pixels cut out sharp, shaded by the vertex colour (the
// face's light), faded as a whole by the vertex alpha
void main() {
    vec4 c = texture(Sampler0, texCoord);
    if (c.a < 0.5 || params.a < 0.01) {
        discard;
    }
    fragColor = vec4(c.rgb * params.rgb, params.a);
}
