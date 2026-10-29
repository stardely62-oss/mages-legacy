#include mages_legacy:ribbon

void main() {
    vec4 c = ribbon(true);
    if (c.a < 0.004) {
        discard;
    }
    fragColor = c;
}
