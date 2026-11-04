"""Draws the Mage's Shop's own pieces (textures/gui/merc_shop/*.png), pixel art at its native size: the hanging sign, the
book of orders lying open on the counter, the lantern, the shopkeeper behind the counter, the coin pouch, a price tag, a
pinned note of the services' board. Needs Pillow: python3 tools/gen_merc_shop.py. Deterministic.
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/mages_legacy/textures/gui/merc_shop"

WOOD_D = (46, 28, 18, 255)
WOOD = (92, 58, 34, 255)
WOOD_L = (128, 84, 48, 255)
BRASS = (200, 150, 70, 255)
BRASS_L = (240, 206, 120, 255)
INK = (40, 26, 16, 255)
PAPER = (234, 220, 186, 255)
PAPER_D = (206, 186, 146, 255)
RED = (150, 36, 30, 255)


def sign():
    im = Image.new("RGBA", (96, 26))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 2, 95, 25], fill=WOOD_D)
    d.rectangle([1, 3, 94, 24], fill=WOOD)
    for y in (8, 14, 20):
        d.line([2, y, 93, y], fill=(80, 50, 30, 255))
    d.rectangle([3, 5, 92, 22], outline=BRASS)
    for x in (3, 92):
        for y in (5, 22):
            d.point((x, y), fill=BRASS_L)
    # the ring bolts the chains hang from
    for x in (10, 85):
        d.rectangle([x - 1, 0, x + 1, 2], fill=BRASS)
    return im


def book():
    im = Image.new("RGBA", (64, 40))
    d = ImageDraw.Draw(im)
    # leather cover under the pages
    d.polygon([(2, 12), (32, 8), (62, 12), (60, 38), (32, 35), (4, 38)], fill=(90, 30, 20, 255))
    # two pages
    d.polygon([(5, 10), (31, 6), (31, 32), (6, 35)], fill=PAPER)
    d.polygon([(33, 6), (59, 10), (58, 35), (33, 32)], fill=PAPER_D)
    d.line([32, 6, 32, 33], fill=(150, 120, 80, 255))
    rnd = random.Random(5)
    for i in range(6):
        y = 11 + i * 4
        d.line([9, y, 9 + rnd.randint(12, 19), y - 1], fill=(120, 96, 70, 255))
        d.line([36, y - 1, 36 + rnd.randint(12, 19), y], fill=(120, 96, 70, 255))
    # a wax seal and the ribbon
    d.ellipse([44, 24, 51, 31], fill=RED)
    d.point((47, 27), fill=(210, 80, 60, 255))
    d.line([32, 33, 34, 39], fill=(170, 40, 30, 255), width=2)
    return im


def lantern():
    im = Image.new("RGBA", (12, 26))
    d = ImageDraw.Draw(im)
    d.line([6, 0, 6, 4], fill=(60, 60, 64, 255))
    d.rectangle([3, 4, 8, 5], fill=BRASS)
    d.rectangle([2, 6, 9, 20], fill=(40, 34, 30, 255))
    d.rectangle([3, 7, 8, 19], fill=(255, 196, 90, 255))
    d.rectangle([4, 9, 7, 17], fill=(255, 236, 170, 255))
    d.line([5, 7, 5, 19], fill=(40, 34, 30, 255))
    d.rectangle([2, 20, 9, 22], fill=BRASS)
    return im


def keeper():
    im = Image.new("RGBA", (40, 46))
    d = ImageDraw.Draw(im)
    cloak = (34, 26, 30, 255)
    cloak_l = (54, 40, 44, 255)
    d.polygon([(20, 2), (31, 12), (33, 24), (39, 45), (1, 45), (7, 24), (9, 12)], fill=cloak)
    d.polygon([(20, 4), (28, 12), (29, 22), (11, 22), (12, 12)], fill=(12, 8, 10, 255))
    d.line([20, 2, 31, 12], fill=cloak_l)
    d.line([20, 2, 9, 12], fill=cloak_l)
    # a copper clasp and the eyes (drawn bright; the screen dims them to blink)
    d.rectangle([18, 26, 21, 28], fill=BRASS)
    d.rectangle([15, 15, 16, 15], fill=(255, 200, 90, 255))
    d.rectangle([23, 15, 24, 15], fill=(255, 200, 90, 255))
    return im


def pouch():
    im = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(im)
    d.polygon([(4, 5), (12, 5), (15, 12), (12, 15), (4, 15), (1, 12)], fill=(110, 70, 40, 255))
    d.rectangle([5, 3, 11, 5], fill=(80, 50, 30, 255))
    d.line([5, 6, 11, 6], fill=BRASS)
    d.ellipse([6, 9, 10, 13], fill=BRASS)
    d.point((7, 10), fill=BRASS_L)
    return im


def tag():
    im = Image.new("RGBA", (24, 11))
    d = ImageDraw.Draw(im)
    d.polygon([(4, 0), (23, 0), (23, 10), (4, 10), (0, 5)], fill=PAPER)
    d.polygon([(4, 0), (23, 0), (23, 10), (4, 10), (0, 5)], outline=(150, 120, 84, 255))
    d.point((3, 5), fill=INK)
    return im


def note():
    im = Image.new("RGBA", (48, 34))
    d = ImageDraw.Draw(im)
    d.polygon([(1, 2), (46, 0), (47, 32), (2, 33)], fill=PAPER)
    d.line([(1, 2), (46, 0), (47, 32), (2, 33), (1, 2)], fill=(160, 130, 90, 255))
    for x in (6, 40):
        d.line([x, 28, x + 3, 30], fill=PAPER_D)
    return im


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("sign", sign), ("book", book), ("lantern", lantern), ("keeper", keeper), ("pouch", pouch), ("tag", tag), ("note", note)):
        fn().save(OUT / f"{name}.png")
        print(name)
