"""Turns Codex images (about 1254 px "pixel art", icons on magenta) into the GUI textures Mage's Legacy loads.

Each output pixel is the median colour of the middle half of its cell; cells close to the magenta background become
transparent and the symbol gets a faint rim so it reads on dark panels. Icons become 32x32 (drawn in a 16 GUI cell),
emblems 64x64 (drawn at 32), the legend illustration 144x96 with no transparency.
Needs Pillow:  python3 tools/import_codex_art.py <folder>
"""
import statistics
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
GUI = ROOT / "src/main/resources/assets/mages_legacy/textures/gui"


def is_background(c):
    r, g, b = c
    return r > 150 and b > 150 and g < 110 and abs(r - b) < 90


def crop_to_symbol(img):
    """Codex often leaves wide margins: crop to the non-magenta area, square and centred."""
    # Pad with magenta first, so a crop past the canvas edge stays background instead of turning black
    pad = img.size[0] // 16
    padded = Image.new("RGB", (img.size[0] + 2 * pad, img.size[1] + 2 * pad), (255, 0, 255))
    padded.paste(img, (pad, pad))
    img = padded
    small = img.resize((128, 128), Image.NEAREST)
    px = small.load()
    pts = [(x, y) for y in range(128) for x in range(128) if not is_background(px[x, y])]
    if not pts:
        return img
    x0, x1 = min(p[0] for p in pts), max(p[0] for p in pts) + 1
    y0, y1 = min(p[1] for p in pts), max(p[1] for p in pts) + 1
    side = max(x1 - x0, y1 - y0) + 3
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    kx, ky = img.size[0] / 128, img.size[1] / 128
    return img.crop((int((cx - side / 2) * kx), int((cy - side / 2) * ky), int((cx + side / 2) * kx), int((cy + side / 2) * ky)))


def sample(img, gw, gh, keyed):
    w, h = img.size
    px = img.load()
    out = Image.new("RGBA", (gw, gh), (0, 0, 0, 0))
    for gy in range(gh):
        for gx in range(gw):
            x0, x1 = int((gx + 0.25) * w / gw), max(int((gx + 0.75) * w / gw), int((gx + 0.25) * w / gw) + 1)
            y0, y1 = int((gy + 0.25) * h / gh), max(int((gy + 0.75) * h / gh), int((gy + 0.25) * h / gh) + 1)
            samples = [px[min(x, w - 1), min(y, h - 1)] for y in range(y0, y1, 2) for x in range(x0, x1, 2)]
            fg = [c for c in samples if not is_background(c)] if keyed else samples
            if len(fg) * 2 < len(samples):
                continue
            out.putpixel((gx, gy), tuple(int(statistics.median(c[i] for c in fg)) for i in range(3)) + (255,))
    return out


def reduce(img, colors):
    alpha = img.getchannel("A")
    rgb = img.convert("RGB").quantize(colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGBA")
    rgb.putalpha(alpha)
    return rgb


def add_rim(img, rim=(122, 100, 92, 255)):
    w, h = img.size
    px = img.load()
    out = img.copy()
    opx = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3]:
                continue
            near = ((x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if any(0 <= nx < w and 0 <= ny < h and px[nx, ny][3] for nx, ny in near):
                opx[x, y] = rim
    return out


def spectral(img, tint=(159, 232, 224)):
    """Half the mob's own colour, half the pale spirit cyan: still recognisable, clearly a ghost."""
    out = img.copy()
    px = out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = px[x, y]
            if a:
                lum = (r * 3 + g * 6 + b) / 10 / 255
                px[x, y] = tuple(min(255, int(c * 0.45 + t * lum * 0.75 + 20)) for c, t in zip((r, g, b), tint)) + (a,)
    return out


def convert(path):
    name = path.stem
    img = Image.open(path).convert("RGB")
    if name.startswith("lore_"):
        return reduce(sample(img, 144, 96, False), 32), GUI / f"{name}.png"
    if name.startswith("emblem_"):
        return add_rim(reduce(sample(crop_to_symbol(img), 64, 64, True), 24)), GUI / "emblem" / f"{name[7:]}.png"
    # Bestiary portraits: beasts in their own colours, echoes washed in the spirit cyan they wear in the world
    if name.startswith("beast_"):
        return add_rim(reduce(sample(crop_to_symbol(img), 32, 32, True), 16)), GUI / "beast" / f"{name[6:]}.png"
    if name.startswith("echo_"):
        return add_rim(spectral(reduce(sample(crop_to_symbol(img), 32, 32, True), 16))), GUI / "echo" / f"{name[5:]}.png"
    return add_rim(reduce(sample(crop_to_symbol(img), 32, 32, True), 16)), GUI / "icon" / f"{name}.png"


if __name__ == "__main__":
    for image in sorted(Path(sys.argv[1]).glob("*.png")):
        texture, target = convert(image)
        target.parent.mkdir(parents=True, exist_ok=True)
        texture.save(target)
        print(f"wrote {target.relative_to(ROOT)}")
