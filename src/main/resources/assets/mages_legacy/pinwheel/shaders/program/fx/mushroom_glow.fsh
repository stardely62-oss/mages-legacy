#include mages_legacy:mushroom

void main() {
    vec4 c = mushroom(true);
    if (c.a < 0.004) {
        discard;
    }
    fragColor = c;
}
