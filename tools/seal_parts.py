"""Cuts the Brand's seal (textures/fx/merc_seal.png) into the parts a blow fills: a third for a seal of three, a quarter for a
seal of four, each with a narrow gap at both ends so the ring shows exactly as many divisions as blows it needs (the ring's
own engraved notches are cut away with the gaps). Angles run clockwise from the top. Needs Pillow:
python3 tools/seal_parts.py
"""
import math
from pathlib import Path

from PIL import Image

FX = Path(__file__).resolve().parent.parent / "src/main/resources/assets/mages_legacy/textures/fx"
GAP = 8  # degrees cut at each end of a part


def wedge(img, start, end):
    out = img.copy()
    w, h = out.size
    px = out.load()
    for y in range(h):
        for x in range(w):
            a = math.degrees(math.atan2(x + 0.5 - w / 2, -(y + 0.5 - h / 2))) % 360
            if not (start + GAP <= a < end - GAP):
                px[x, y] = (0, 0, 0, 0)
    return out


if __name__ == "__main__":
    seal = Image.open(FX / "merc_seal.png").convert("RGBA")
    wedge(seal, 0, 120).save(FX / "merc_seal_part.png")
    wedge(seal, 0, 90).save(FX / "merc_seal_quarter.png")
    print("seal parts")
