#include mages_legacy:orb

void main() {
    vec4 c = orb(false);
    if (c.a < 0.004) {
        discard;
    }
    fragColor = c;
}
