#include mages_legacy:spore

void main() {
    vec4 c = spore(false);
    if (c.a < 0.004) {
        discard;
    }
    fragColor = c;
}
