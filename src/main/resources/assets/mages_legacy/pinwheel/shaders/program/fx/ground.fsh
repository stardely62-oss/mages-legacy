#include mages_legacy:ground

void main() {
    vec4 c = ground(false);
    if (c.a < 0.004) {
        discard;
    }
    fragColor = c;
}
