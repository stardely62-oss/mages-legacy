"""Draws the Bloodkin's painted effect textures (textures/fx/blood_*.png). Needs Pillow: python3 tools/gen_blood_fx.py

blood_streak  the Blood Dash's wake and the threads of blood: u runs from a thin broken tail (0) to a full head (1)
blood_sigil   the Harvest's circle on the ground: two rings, marks between them, spokes drawn in to a small heart
blood_wave    a thin ring with a soft inner edge, closing in (the Harvest) or opening out
blood_slash   a double crescent cut (a creature cut by the Dash, the Sacrifice's own cut)
blood_splat   a stain on the ground, dark, no glow
blood_flare   a soft round flash
Deterministic: the same textures every run.
"""
import math
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/mages_legacy/textures/fx"

# Intensity 0..1 to colour: near-black wine, deep blood, bright crimson, a hot pale core
STOPS = [(0.0, (40, 0, 6)), (0.35, (120, 4, 18)), (0.65, (200, 18, 32)), (0.85, (245, 60, 60)), (1.0, (255, 190, 170))]


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


def put(img, w, h, fn):
    """fn(x, y) in 0..1 coordinates returns (intensity, alpha)."""
    px = img.load()
    for y in range(h):
        for x in range(w):
            i, a = fn((x + 0.5) / w, (y + 0.5) / h)
            r, g, b = colour(i)
            px[x, y] = (r, g, b, int(255 * max(0.0, min(1.0, a))))


def noise1d(n, rnd, smooth_pass=2):
    v = [rnd.random() for _ in range(n)]
    for _ in range(smooth_pass):
        v = [(v[k - 1] + 2 * v[k] + v[(k + 1) % n]) / 4 for k in range(n)]
    lo, hi = min(v), max(v)
    return [(x - lo) / (hi - lo) for x in v]


def streak():
    w, h = 256, 64
    rnd = random.Random(11)
    lines = noise1d(h, rnd, 1)
    breaks = noise1d(64, rnd, 2)
    tears = noise1d(h, rnd, 0)
    drops = [(rnd.uniform(0.05, 0.75), rnd.choice([-1, 1]) * rnd.uniform(0.12, 0.42), rnd.uniform(0.012, 0.03)) for _ in range(22)]

    def fn(u, v):
        half = 0.06 + 0.36 * u ** 0.6
        if u > 0.8:
            half *= math.sqrt(max(0.0, 1 - ((u - 0.8) / 0.2) ** 2))
        dy = abs(v - 0.5) / max(1e-4, half)
        body = max(0.0, 1 - dy * dy)
        # Speed lines along it, the tail torn into pieces
        line = lines[min(h - 1, int(v * h))]
        torn = smooth((0.5 * breaks[min(63, int(u * 64))] + 0.5 * tears[min(h - 1, int(v * h))] - (0.8 - u)) * 4)
        i = body ** 0.7 * (0.55 + 0.45 * line) * (0.35 + 0.65 * u) * torn
        i += 0.35 * max(0.0, 1 - dy / 0.22) * u * torn
        a = smooth(body * 1.6) * (0.25 + 0.75 * u) * torn
        for du, dv, r in drops:
            d = math.hypot((u - du) * 4, (v - 0.5 - dv))
            if d < r:
                k = 1 - d / r
                i = max(i, 0.55 + 0.3 * k)
                a = max(a, smooth(k * 3))
        return i, a

    img = Image.new("RGBA", (w, h))
    put(img, w, h, fn)
    return img


def sigil():
    s = 256
    rnd = random.Random(23)
    marks = [rnd.choice("bdvx") for _ in range(36)]
    drips = [(rnd.uniform(0, math.tau), rnd.uniform(0.94, 0.99), rnd.uniform(0.012, 0.03)) for _ in range(26)]

    def ring(r, at, width):
        return math.exp(-((r - at) / width) ** 2)

    def fn(x, y):
        dx, dy = x * 2 - 1, y * 2 - 1
        r = math.hypot(dx, dy)
        th = math.atan2(dy, dx) % math.tau
        wob = 0.006 * math.sin(th * 7) + 0.004 * math.sin(th * 13 + 1)
        i = 0.0
        i = max(i, ring(r, 0.92 + wob, 0.018))
        i = max(i, 0.85 * ring(r, 0.83, 0.009))
        i = max(i, 0.8 * ring(r, 0.46, 0.010))
        i = max(i, 0.9 * ring(r, 0.14, 0.012))
        # Marks between the two outer rings, one of four kinds in each of 36 cells
        cell = th / math.tau * 36
        k = int(cell) % 36
        f = cell - int(cell)
        if 0.85 < r < 0.9:
            m = marks[k]
            rr = (r - 0.85) / 0.05
            if m == "b" and abs(f - 0.5) < 0.09:
                i = max(i, 0.9)
            elif m == "d" and math.hypot((f - 0.5) * 0.17, (rr - 0.5) * 0.05) < 0.012:
                i = max(i, 0.9)
            elif m == "v" and abs(abs(f - 0.5) * 2 - rr) < 0.18 and rr > 0.15:
                i = max(i, 0.8)
            elif m == "x" and (abs(f - rr) < 0.12 or abs(1 - f - rr) < 0.12):
                i = max(i, 0.75)
        # Six spokes from the outer ring to the inner, curling in and thicker outside: blood drawn inward
        if 0.15 < r < 0.83:
            sp = ((th + 1.4 * (r - 0.15)) / math.tau * 6) % 1
            half = 0.004 + 0.022 * (r - 0.15) / 0.68
            off = abs(sp - 0.5) * math.tau / 6 * r
            if off < half:
                i = max(i, 0.75 * (1 - off / half) + 0.15)
        # Drips hanging off the outer ring
        for a, dr, rad in drips:
            px, py = math.cos(a) * dr, math.sin(a) * dr
            d = math.hypot(dx - px, dy - py)
            if d < rad:
                i = max(i, 0.7 + 0.2 * (1 - d / rad))
        # Never the hot core: lines of blood, not of light
        i *= 0.82
        a = smooth(i * 1.6)
        # A faint pool of blood inside it all
        if r < 0.92:
            a = max(a, 0.05 * (1 - r))
            i = max(i, 0.3)
        return i, a

    img = Image.new("RGBA", (s, s))
    put(img, s, s, fn)
    return img


def wave():
    s = 256
    rnd = random.Random(5)
    n = noise1d(128, rnd, 3)

    def fn(x, y):
        r = math.hypot(x * 2 - 1, y * 2 - 1)
        th = math.atan2(y * 2 - 1, x * 2 - 1) % math.tau
        k = 0.7 + 0.3 * n[int(th / math.tau * 128) % 128]
        if r > 0.94:
            e = math.exp(-((r - 0.94) / 0.025) ** 2)
        else:
            e = math.exp(-((0.94 - r) / 0.12) ** 2) * 0.25 + math.exp(-((0.94 - r) / 0.02) ** 2) * 0.6
        return e * k, smooth(e * 1.3) * k

    img = Image.new("RGBA", (s, s))
    put(img, s, s, fn)
    return img


def slash():
    s = 128

    def arc(x, y, cx, cy, radius, a0, a1, width):
        dx, dy = x - cx, y - cy
        th = math.atan2(dy, dx)
        if not a0 < th < a1:
            return 0.0
        t = (th - a0) / (a1 - a0)
        half = width * math.sin(math.pi * t) ** 0.8
        d = abs(math.hypot(dx, dy) - radius)
        return max(0.0, 1 - d / max(1e-4, half))

    def fn(x, y):
        # Two crescents opening downward, one thinner beside the other
        a = arc(x, y, 0.5, 1.0, 0.62, -math.pi * 0.85, -math.pi * 0.15, 0.075)
        b = arc(x, y, 0.5, 1.12, 0.62, -math.pi * 0.78, -math.pi * 0.22, 0.035)
        k = max(a, b * 0.85)
        return 0.45 + 0.55 * k, smooth(k * 2.2)

    img = Image.new("RGBA", (s, s))
    put(img, s, s, fn)
    return img


def splat():
    s = 128
    rnd = random.Random(31)
    ph = [rnd.uniform(0, math.tau) for _ in range(3)]
    drops = []
    for _ in range(16):
        a = rnd.uniform(0, math.tau)
        d = rnd.uniform(0.42, 0.9)
        drops.append((math.cos(a) * d, math.sin(a) * d, rnd.uniform(0.03, 0.09) * (1.1 - d * 0.6)))

    def fn(x, y):
        dx, dy = x * 2 - 1, y * 2 - 1
        r = math.hypot(dx, dy)
        th = math.atan2(dy, dx)
        edge = 0.42 * (1 + 0.16 * math.sin(5 * th + ph[0]) + 0.1 * math.sin(9 * th + ph[1]) + 0.06 * math.sin(14 * th + ph[2]))
        inside = edge - r
        for px, py, rad in drops:
            inside = max(inside, rad - math.hypot(dx - px, dy - py))
        if inside <= 0:
            return 0, 0
        # Dried darker at the rim, wetter in the middle
        rim = smooth(inside / 0.08)
        return 0.18 + 0.2 * rim, 0.92 * smooth(inside / 0.015)

    img = Image.new("RGBA", (s, s))
    put(img, s, s, fn)
    return img


def flare():
    s = 64

    def fn(x, y):
        r = math.hypot(x * 2 - 1, y * 2 - 1)
        e = math.exp(-(r / 0.35) ** 2)
        return 0.55 + 0.45 * math.exp(-(r / 0.15) ** 2), e

    img = Image.new("RGBA", (s, s))
    put(img, s, s, fn)
    return img


if __name__ == "__main__":
    for name, fn in [("streak", streak), ("sigil", sigil), ("wave", wave), ("slash", slash), ("splat", splat), ("flare", flare)]:
        fn().save(OUT / f"blood_{name}.png")
        print("blood_" + name)
