"""Turns the Codex pictures of the Mercenaries' effects and shop (fx_merc_*, shop_*) into the textures the mod draws.

Effects become square textures/fx/merc_<name>.png at their grid size; the seal also gives its parts (a third of the ring
for a seal of three, a quarter for a seal of four). Shop props become textures/gui/merc_shop/<name>.png cropped to the
prop and sampled to a width (their height follows the prop's own shape); the shop's background keeps its 16:9 frame.
Needs Pillow:  python3 tools/import_merc_art.py <folder>
"""
import math
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from import_codex_art import crop_to_symbol, is_background, reduce, sample  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
FX = ROOT / "src/main/resources/assets/mages_legacy/textures/fx"
SHOP = ROOT / "src/main/resources/assets/mages_legacy/textures/gui/merc_shop"

# Effects: grid size and colours
FX_GRID = {"coin": (16, 16), "shard": (16, 12), "sword": (32, 20), "seal": (64, 24), "warrant": (32, 20), "link": (16, 10),
           "hook": (32, 16), "banner": (64, 32), "door": (64, 28)}
# Shop props: width in pixels (the height follows), colours
SHOP_WIDTH = {"shelf": (160, 32), "counter": (128, 28), "keeper": (64, 28), "sign": (96, 20), "board": (112, 20), "book": (64, 24),
              "lantern": (16, 12), "pouch": (24, 16), "note": (32, 12)}


def crop_box(img):
    """The prop's own box (not squared), with a pixel of margin."""
    small = img.resize((256, 256), Image.NEAREST)
    px = small.load()
    pts = [(x, y) for y in range(256) for x in range(256) if not is_background(px[x, y])]
    if not pts:
        return img
    x0, x1 = min(p[0] for p in pts), max(p[0] for p in pts) + 1
    y0, y1 = min(p[1] for p in pts), max(p[1] for p in pts) + 1
    kx, ky = img.size[0] / 256, img.size[1] / 256
    return img.crop((int(x0 * kx), int(y0 * ky), int(x1 * kx), int(y1 * ky)))


def wedge(img, start, end):
    """Only the part of a ring between two angles (degrees clockwise from the top)."""
    out = img.copy()
    w, h = out.size
    px = out.load()
    for y in range(h):
        for x in range(w):
            a = math.degrees(math.atan2(x + 0.5 - w / 2, -(y + 0.5 - h / 2))) % 360
            if not (start <= a < end):
                px[x, y] = (0, 0, 0, 0)
    return out


def main(folder):
    folder = Path(folder)
    for path in sorted(folder.glob("fx_merc_*.png")):
        name = path.stem[len("fx_merc_"):]
        grid, colours = FX_GRID[name]
        if name in ("door", "banner"):
            # Tall props: cropped to themselves (not squared), so a quad of their shape shows them whole
            prop = crop_box(Image.open(path).convert("RGB"))
            img = reduce(sample(prop, grid, max(1, round(grid * prop.size[1] / prop.size[0])), True), colours)
        else:
            img = reduce(sample(crop_to_symbol(Image.open(path).convert("RGB")), grid, grid, True), colours)
        # The sword keeps the texture name the effects were written for
        img.save(FX / f"merc_{'blade' if name == 'sword' else name}.png")
        print("fx", name)
        if name == "seal":
            # Thirds start just past the top notch; quarters for a seal of four
            wedge(img, 0, 120).save(FX / "merc_seal_part.png")
            wedge(img, 0, 90).save(FX / "merc_seal_quarter.png")
        if name == "coin":
            img.save(FX / "merc_coin_face.png")
        if name == "door":
            # The shop's own pair of doors: the leaf cropped to itself, larger
            SHOP.mkdir(parents=True, exist_ok=True)
            prop = crop_box(Image.open(path).convert("RGB"))
            height = max(1, round(64 * prop.size[1] / prop.size[0]))
            reduce(sample(prop, 64, height, True), 32).save(SHOP / "door.png")
            print("shop door", 64, height)
    SHOP.mkdir(parents=True, exist_ok=True)
    for path in sorted(folder.glob("shop_*.png")):
        name = path.stem[len("shop_"):]
        src = Image.open(path).convert("RGB")
        if name == "bg":
            # The whole frame, 16:9, no transparency
            w, h = src.size
            th = int(w * 9 / 16)
            src = src.crop((0, (h - th) // 2, w, (h - th) // 2 + th)) if th < h else src
            reduce(sample(src, 320, 180, False), 48).save(SHOP / "bg.png")
            print("shop bg")
            continue
        width, colours = SHOP_WIDTH[name]
        prop = crop_box(src)
        height = max(1, round(width * prop.size[1] / prop.size[0]))
        reduce(sample(prop, width, height, True), colours).save(SHOP / f"{name}.png")
        print("shop", name, width, height)


if __name__ == "__main__":
    main(sys.argv[1])
