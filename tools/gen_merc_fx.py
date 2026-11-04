"""Draws the Mercenaries' painted effect textures (textures/fx/merc_*.png). Needs Pillow: python3 tools/gen_merc_fx.py

merc_seal      the Hunter's Brand's seal: a ring of guild marks, three notches between its parts (drawn plain)
merc_seal_part a quarter of the seal's outer band (90 degrees, from the top clockwise), lit one by one
merc_verdict   the Guild's Verdict: a great warrant seal, rings of marks round crossed blades and a scale
merc_blade     a spectral blade, point up
merc_link      one link of the harpoon's chain, its metal edges bright
merc_hook      the harpoon's head
merc_coin      a pixel coin (8x8)
merc_spark     a pixel spark (8x8)
merc_banner    the Guild Legend's banner cloth: the guild's coin and blade, a golden hem
merc_sigil     a turning circle on the ground (the banner's foot, the Debt's wave)
merc_wave      a thin ring with a soft inner edge
merc_flare     a soft golden flash
merc_door      the Mage's Shop's door: an arch of warm light
merc_contract  a contract's wax seal on its ribbon (the Life Debt round its ward)
Deterministic: the same textures every run.
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/mages_legacy/textures/fx"

# Intensity 0..1 to colour: graphite, bronze, copper, dark gold, pale gold
STOPS = [(0.0, (34, 30, 34)), (0.3, (120, 72, 34)), (0.55, (200, 130, 60)), (0.8, (226, 176, 74)), (1.0, (255, 242, 196))]


def colour(i):
    i = max(0.0, min(1.0, i))
    for (a, ca), (b, cb) in zip(STOPS, STOPS[1:]):
        if i <= b:
            t = (i - a) / (b - a)
            return tuple(int(ca[k] + (cb[k] - ca[k]) * t) for k in range(3))
    return STOPS[-1][1]


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def put(img, fn):
    """fn(x, y) in 0..1 coordinates returns (intensity, alpha)."""
    w, h = img.size
    px = img.load()
    for y in range(h):
        for x in range(w):
            i, a = fn((x + 0.5) / w, (y + 0.5) / h)
            r, g, b = colour(i)
            px[x, y] = (r, g, b, int(255 * max(0.0, min(1.0, a))))


def polar(x, y):
    dx, dy = x - 0.5, y - 0.5
    return math.hypot(dx, dy) * 2, (math.atan2(dx, -dy) / (2 * math.pi)) % 1.0


def band(r, at, width, soft=0.012):
    return smooth(1 - (abs(r - at) - width) / soft) if abs(r - at) > width else 1.0


def marks(a, count, duty):
    return 1.0 if (a * count) % 1.0 < duty else 0.0


def seal():
    img = Image.new("RGBA", (256, 256))

    def fn(x, y):
        r, a = polar(x, y)
        v = 0.0
        v = max(v, band(r, 0.94, 0.012) * 0.9)
        v = max(v, band(r, 0.70, 0.008) * 0.8)
        # Guild marks between the rings
        ring = band(r, 0.82, 0.05, 0.01)
        v = max(v, ring * marks(a + 0.01, 36, 0.35) * 0.75)
        # Three notches cut through everything at 0, 120, 240 degrees
        notch = min(abs(((a * 3) % 1.0) - 0.0), abs(((a * 3) % 1.0) - 1.0))
        if notch < 0.012 and r > 0.66:
            v = 0
        # The heart: a small diamond
        dx, dy = abs(x - 0.5), abs(y - 0.5)
        if dx + dy < 0.07:
            v = max(v, 1 - (dx + dy) / 0.07 * 0.4)
        glow = smooth(1 - abs(r - 0.82) / 0.2) * 0.15 if r < 1 else 0
        return v, max(v, glow)

    put(img, fn)
    return img


def seal_part():
    img = Image.new("RGBA", (256, 256))

    def fn(x, y):
        r, a = polar(x, y)
        if a > 0.25 - 0.01 or a < 0.01:
            return 0, 0
        edge = smooth(min(a - 0.01, 0.25 - 0.01 - a) / 0.02)
        body = band(r, 0.88, 0.045, 0.015)
        core = band(r, 0.88, 0.012, 0.01)
        v = body * 0.75 + core * 0.25
        return v * edge, body * edge

    put(img, fn)
    return img


def verdict():
    img = Image.new("RGBA", (256, 256))

    def fn(x, y):
        r, a = polar(x, y)
        v = 0.0
        for at, wdt, s in ((0.96, 0.01, 1), (0.88, 0.006, 0.8), (0.62, 0.008, 0.85), (0.56, 0.004, 0.6)):
            v = max(v, band(r, at, wdt) * s)
        v = max(v, band(r, 0.92, 0.03, 0.008) * marks(a, 48, 0.5) * 0.7)
        v = max(v, band(r, 0.75, 0.09, 0.01) * marks(a + 0.02, 12, 0.08) * 0.85)
        # Crossed blades
        for ang in (math.radians(40), math.radians(-40)):
            ux, uy = math.sin(ang), -math.cos(ang)
            px, py = x - 0.5, y - 0.5
            along = px * ux + py * uy
            across = abs(px * uy - py * ux)
            if abs(along) < 0.24:
                taper = 0.018 * (1 - max(0, along) / 0.24) + 0.004
                if across < taper:
                    v = max(v, 0.95 - across / taper * 0.3)
                if abs(along + 0.15) < 0.012 and across < 0.05:
                    v = max(v, 0.8)
        # The scale's beam
        if abs(y - 0.40) < 0.006 and abs(x - 0.5) < 0.16:
            v = max(v, 0.9)
        glow = smooth(1 - r) * 0.12
        return v, max(v, glow)

    put(img, fn)
    return img


def blade():
    img = Image.new("RGBA", (64, 256))

    def fn(x, y):
        # y 0 is the point
        cx = abs(x - 0.5)
        if y < 0.72:
            width = 0.04 + 0.3 * smooth(y / 0.18) * (1 - (y - 0.18) * 0.25)
            edge = cx / max(0.01, width)
            if edge > 1:
                return 0, 0
            fuller = 0.25 if cx < 0.03 and y > 0.2 else 0
            return 0.95 - edge * 0.35 + fuller, 0.55 + 0.45 * (1 - edge)
        if y < 0.76:
            return (0.7, 1) if cx < 0.42 else (0, 0)
        if cx < 0.06:
            return 0.5, 0.9
        if y > 0.95 and cx < 0.1:
            return 0.8, 1
        return 0, 0

    put(img, fn)
    return img


def link():
    img = Image.new("RGBA", (128, 64))

    def fn(x, y):
        # An oval ring, its outer edge bright
        dx, dy = (x - 0.5) / 0.46, (y - 0.5) / 0.42
        r = math.hypot(dx, dy)
        if r > 1:
            return 0, 0
        inner = math.hypot((x - 0.5) / 0.30, (y - 0.5) / 0.2)
        if inner < 1:
            return 0, 0
        t = (r - 0.55) / 0.45
        shine = 1 - abs(t - 0.75) * 1.4
        return 0.45 + 0.55 * max(0, shine), 1

    put(img, fn)
    return img


def hook():
    img = Image.new("RGBA", (128, 128))
    d = ImageDraw.Draw(img)
    # Shank, then the curve and the barb, in copper with a pale edge
    for width, c in ((14, colour(0.45)), (8, colour(0.75)), (3, colour(1.0))):
        d.line([(64, 8), (64, 80)], fill=c + (255,), width=width)
        d.arc([28, 52, 100, 122], 0, 180, fill=c + (255,), width=width)
        d.line([(30, 88), (22, 70)], fill=c + (255,), width=width)
    d.ellipse([54, 2, 74, 18], outline=colour(0.9) + (255,), width=4)
    return img


def pixel(size, pattern):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y, row in enumerate(pattern):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = colour({"d": 0.35, "c": 0.6, "g": 0.82, "w": 1.0}[ch]) + (255,)
    return img


def coin():
    return pixel(8, ["..gggg..", ".gwwgcg.", "gwgggccg", "gwgddgcg", "ggcddgcg", "gccggccd", ".gcccdd.", "..dddd.."])


def spark():
    return pixel(8, ["........", "...g....", "...w....", ".gwwwg..", "...w....", "...g....", "........", "........"])


def banner():
    img = Image.new("RGBA", (128, 256))
    rnd = random.Random(7)
    grain = [rnd.random() for _ in range(256)]

    def fn(x, y):
        # A swallowtail cloth: cut in a V at the bottom
        cut = 0.86 + abs(x - 0.5) * 0.28
        if y > cut:
            return 0, 0
        hem = x < 0.06 or x > 0.94 or y < 0.05 or y > cut - 0.035
        if hem:
            return 0.85, 0.95
        cloth = 0.32 + 0.06 * grain[int(y * 255)]
        a = 0.55
        # The guild's coin
        r = math.hypot(x - 0.5, (y - 0.33) * 2)
        if 0.17 < r < 0.21:
            return 0.92, 0.95
        if r < 0.13:
            # and the blade across it
            if abs(x - 0.5) < 0.025 and 0.24 < y < 0.43:
                return 1.0, 1
            return 0.7, 0.85
        if abs(x - 0.5) < 0.02 and 0.2 < y < 0.47:
            return 1.0, 1
        # Lines of marks below
        if 0.66 < y < 0.70 and 0.2 < x < 0.8 and int(x * 20) % 2 == 0:
            return 0.8, 0.9
        return cloth, a

    put(img, fn)
    return img


def sigil():
    img = Image.new("RGBA", (256, 256))

    def fn(x, y):
        r, a = polar(x, y)
        v = 0.0
        v = max(v, band(r, 0.97, 0.008) * 0.9)
        v = max(v, band(r, 0.90, 0.004) * 0.6)
        v = max(v, band(r, 0.935, 0.02, 0.006) * marks(a, 60, 0.45) * 0.7)
        v = max(v, band(r, 0.55, 0.006) * 0.7)
        # Spokes
        if 0.55 < r < 0.9 and min((a * 8) % 1.0, 1 - (a * 8) % 1.0) < 0.012:
            v = max(v, 0.6)
        # A coin rim inside
        v = max(v, band(r, 0.25, 0.01) * 0.8)
        glow = smooth(1 - abs(r - 0.9) / 0.15) * 0.15 if r < 1 else 0
        return v, max(v, glow)

    put(img, fn)
    return img


def wave():
    img = Image.new("RGBA", (256, 256))

    def fn(x, y):
        r, _ = polar(x, y)
        if r > 1:
            return 0, 0
        outer = smooth((1 - r) / 0.03)
        inner = smooth((r - 0.7) / 0.28)
        v = outer * inner
        return 0.6 + 0.4 * v, v

    put(img, fn)
    return img


def flare():
    img = Image.new("RGBA", (64, 64))

    def fn(x, y):
        r, _ = polar(x, y)
        v = max(0.0, 1 - r) ** 2
        return 0.7 + 0.3 * v, v

    put(img, fn)
    return img


def door():
    img = Image.new("RGBA", (128, 256))

    def fn(x, y):
        # An arch: straight sides, a round top
        cx = abs(x - 0.5)
        top = 0.24
        if y < top:
            r = math.hypot(cx, (y - top) * 2) / 0.42
        else:
            r = cx / 0.42
        if r > 1 or y > 0.98:
            return 0, 0
        frame = smooth((r - 0.84) / 0.06)
        light = 0.55 + 0.35 * (1 - r) * (0.5 + y * 0.5)
        v = max(frame * 0.95, light * (1 - frame))
        a = max(frame, 0.55 * (1 - frame) * (0.6 + 0.4 * y))
        return v, a

    put(img, fn)
    return img


def contract():
    img = Image.new("RGBA", (128, 64))

    def fn(x, y):
        # A ribbon across, a round wax seal in its middle
        r = math.hypot((x - 0.5) * 2, y - 0.5)
        if r < 0.36:
            rim = band(r, 0.33, 0.02)
            return 0.55 + 0.4 * rim + (0.3 if r < 0.12 else 0), 1
        if abs(y - 0.5) < 0.12:
            edge = abs(y - 0.5) > 0.09
            return (0.9 if edge else 0.6), 0.9
        return 0, 0

    put(img, fn)
    return img


def pole():
    """A pole (or a frame's bar) of polished gold: shaded across (v), the same along (u)."""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    shades = [(70, 44, 14), (130, 86, 28), (190, 136, 52), (226, 178, 80), (248, 222, 140), (226, 178, 80), (176, 124, 44), (120, 78, 24)]
    for y in range(16):
        c = shades[min(len(shades) - 1, y * len(shades) // 16)]
        for x in range(16):
            px[x, y] = c + (255,)
    return img


def dust():
    """A mote of gold dust: a small warm-gold speck, darker at its edge (dust, not a spark of light)."""
    img = Image.new("RGBA", (8, 8))
    px = img.load()
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            if d < 1.6:
                px[x, y] = (246, 214, 120, 255)
            elif d < 2.8:
                px[x, y] = (200, 146, 52, 255)
            elif d < 3.4:
                px[x, y] = (120, 80, 24, 200)
    return img


def gold_sigil():
    """The circle laid in the ground: lines of dark worked gold, not light."""
    img = sigil()
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a:
                k = (r + g + b) / 765
                px[x, y] = (int(110 + 120 * k), int(70 + 90 * k), int(20 + 40 * k), a)
    return img


def warrant():
    """A rolled-out warrant: parchment between two wooden rods, lines of writing, the guild's red wax seal."""
    img = Image.new("RGBA", (64, 80))
    d = ImageDraw.Draw(img)
    d.rectangle([8, 8, 55, 71], fill=(236, 222, 188, 255))
    d.rectangle([8, 8, 55, 71], outline=(150, 118, 78, 255))
    for i in range(7):
        y = 16 + i * 6
        d.line([14, y, 49 - (i % 3) * 5, y], fill=(110, 84, 58, 255))
    for y in (4, 72):
        d.rectangle([3, y, 60, y + 4], fill=(110, 70, 38, 255))
        d.rectangle([3, y, 60, y], fill=(150, 100, 56, 255))
        d.rectangle([1, y - 1, 4, y + 5], fill=(200, 150, 70, 255))
        d.rectangle([59, y - 1, 62, y + 5], fill=(200, 150, 70, 255))
    d.ellipse([34, 50, 50, 66], fill=(150, 34, 28, 255))
    d.ellipse([37, 53, 47, 63], outline=(210, 80, 60, 255))
    d.line([42, 55, 42, 61], fill=(220, 120, 90, 255))
    return img


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    # The coin, the seal and its parts, the sword (merc_blade), the chain link, the hook, the warrant, the banner and the door
    # leaf are Codex pixel art now (tools/import_merc_art.py); only these are drawn here
    for name, fn in (("verdict", verdict), ("spark", dust), ("sigil", gold_sigil), ("wave", wave), ("flare", flare), ("contract", contract),
                     ("pole", pole)):
        fn().save(OUT / f"merc_{name}.png")
        print("merc_" + name)
