#include mages_legacy:rift

void main() {
    vec4 c = rift(true);
    if (c.a < 0.004) {
        discard;
    }
    fragColor = c;
}
