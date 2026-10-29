#include mages_legacy:ground

void main() {
    vec4 c = ground(true);
    if (c.a < 0.004) {
        discard;
    }
    fragColor = c;
}
