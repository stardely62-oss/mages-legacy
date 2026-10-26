"""Draws the particle sprites of Mage's Legacy (8x8 pixel art) and their particle definitions.

Sand grains, ice shards, spores, spirit wisps, the Song's golden motes and drops of blood, a few variants or frames each.
Needs Pillow:  python3 tools/gen_particles.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/mages_legacy"
TEX = ASSETS / "textures/particle"
DEFS = ASSETS / "particles"


def sprite(rows, palette):
    """rows: 8 strings of 8 characters; each character is a palette key, '.' is transparent."""
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
    return img


SAND = {"s": (232, 201, 138, 255), "d": (176, 140, 84, 255), "h": (255, 240, 200, 255)}
GRAINS = [
    ["........", "........", "........", "...hs...", "...sd...", "........", "........", "........"],
    ["........", "........", "....h...", "...ssd..", "....d...", "........", "........", "........"],
    ["........", "........", "........", "...h....", "...sd...", "....d...", "........", "........"],
]

ICE = {"w": (240, 250, 255, 255), "i": (159, 203, 242, 255), "b": (96, 140, 196, 255), "t": (159, 203, 242, 140)}
SHARDS = [
    ["........", "....w...", "...wi...", "...ii...", "..tib...", "...ib...", "....b...", "........"],
    ["........", "........", "..w.....", "..wii...", "...iib..", ".....bt.", "........", "........"],
    ["........", "........", "...w....", "..wiit..", "..tiib..", "...bb...", "........", "........"],
    ["........", "......w.", ".....wi.", "....ii..", "...ib...", "..tb....", "........", "........"],
]

SPORE = {"c": (230, 255, 210, 255), "g": (156, 255, 138, 255), "m": (110, 190, 100, 200), "f": (90, 150, 80, 90)}
SPORES = [
    ["........", "........", "........", "...gc...", "...cg...", "........", "........", "........"],
    ["........", "........", "...ff...", "..fgcf..", "..fcgf..", "...ff...", "........", "........"],
    ["........", "...ff...", "..fmmf..", ".fmgcmf.", ".fmcgmf.", "..fmmf..", "...ff...", "........"],
    ["...ff...", "..fmmf..", ".fm..mf.", "fm.gc.mf", "fm.cg.mf", ".fm..mf.", "..fmmf..", "...ff..."],
]

WISP = {"w": (235, 255, 252, 255), "c": (159, 232, 224, 255), "b": (90, 170, 175, 200), "f": (70, 130, 140, 100)}
WISPS = [
    ["...f....", "...b....", "..bc....", "..cwc...", ".bcwwb..", ".bcwcb..", "..bcb...", "...f....", ],
    ["....f...", "...bf...", "...cb...", "..cwc...", ".bcwwb..", ".bcwcb..", "..bcb...", "...f....", ],
    ["..f.....", "..b.....", "..cb....", "..cwc...", ".bwwcb..", ".bcwcb..", "..bcb...", "...f....", ],
    ["...f....", "...bf...", "..bcb...", "..cwwc..", ".bcwwb..", ".bcwcb..", "..bcb...", "...f....", ],
]

MOTE = {"w": (255, 248, 220, 255), "g": (240, 200, 120, 255), "o": (200, 150, 80, 180), "f": (170, 120, 60, 80)}
MOTES = [
    ["........", "........", "........", "...gw...", "...wg...", "........", "........", "........"],
    ["........", "........", "...ff...", "..fgwf..", "..fwgf..", "...ff...", "........", "........"],
    ["........", "...f....", "..fof...", ".fowgf..", "..fgwof.", "...fof..", "....f...", "........"],
]

BLOOD = {"h": (255, 120, 120, 255), "r": (196, 24, 40, 255), "d": (110, 8, 20, 255), "p": (130, 10, 24, 200)}
DROPS = [
    ["........", "........", "...h....", "...rr...", "..rrrd..", "..rrdd..", "...dd...", "........"],
    ["........", "........", "........", "...hr...", "...rd...", "........", "........", "........"],
    ["........", "........", "........", "........", "..p..p..", ".prrrdp.", "..pddp..", "........"],
]
BLOOD_GLOW = {"w": (255, 200, 200, 255), "g": (230, 40, 60, 255), "o": (150, 15, 30, 180), "f": (110, 10, 25, 80)}

KINDS = {
    "sand": (GRAINS, SAND),
    "still_sand": (GRAINS, SAND),
    "ice_shard": (SHARDS, ICE),
    "spore": (SPORES, SPORE),
    "wisp": (WISPS, WISP),
    "mote": (MOTES, MOTE),
    "blood": (DROPS, BLOOD),
    "blood_mote": (MOTES, BLOOD_GLOW),
}


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    DEFS.mkdir(parents=True, exist_ok=True)
    drawn = {}
    for kind, (frames, palette) in KINDS.items():
        base = "sand" if kind == "still_sand" else kind
        if base not in drawn:
            for i, rows in enumerate(frames):
                sprite(rows, palette).save(TEX / f"{base}_{i}.png")
            drawn[base] = len(frames)
        textures = [f"mages_legacy:{base}_{i}" for i in range(drawn[base])]
        (DEFS / f"{kind}.json").write_text(json.dumps({"textures": textures}, indent="\t") + "\n")
        print(kind, len(textures))


if __name__ == "__main__":
    main()
