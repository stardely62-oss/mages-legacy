#include mages_legacy:mushroom

void main() {
    vec4 c = mushroom(false);
    if (c.a < 0.004) {
        discard;
    }
    fragColor = c;
}
