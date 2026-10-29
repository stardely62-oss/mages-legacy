"""Draws the mycelium node textures (standard library only). Run: python3 tools/gen_textures.py"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/mages_legacy/textures/block"

PALETTE = {
    ".": (0, 0, 0, 0),
    # stem: pale, a little bluish, with mycelium threads
    "s": (0xC9, 0xC2, 0xB0, 255),
    "S": (0xDD, 0xD6, 0xC4, 255),
    "z": (0xA8, 0x9F, 0x8C, 255),
    "y": (0x8A, 0x9E, 0xA0, 255),
    # cap: deep violet-brown with glowing teal spots (the node glows, so the spots read at night)
    "c": (0x5A, 0x34, 0x3E, 255),
    "C": (0x73, 0x45, 0x4E, 255),
    "d": (0x40, 0x24, 0x2C, 255),
    "g": (0x7F, 0xE0, 0xC0, 255),
    "G": (0xC8, 0xFF, 0xEC, 255),
    # gills under the cap
    "l": (0x9C, 0x86, 0x7A, 255),
    "L": (0x7A, 0x66, 0x5C, 255),
}

STEM = [
    "sSszsSsyzsSssSzs",
    "szsSsysSszsSyssS",
    "SsysSzsSsysSzsys",
    "sSszsSsySzsSsSzs",
    "zsSsysSzssSysSsz",
    "sSzsSsSyszsSzsSs",
    "ysSzsSzsSsySszsS",
    "sSsysSszsSzsSsys",
    "SzsSsysSzsSsySzs",
    "sysSzsSsySszsSsy",
    "sSzsSysSzsSsysSz",
    "zsSsSzsysSzsSsSs",
    "sSysSzsSszsysSzs",
    "SszsysSzsSsSzsys",
    "sSzsSsysSzsSsySs",
    "zsSsyzsSsSzsSzsS",
]
CAP = [
    "dcccCccdccCcccdc",
    "cCcgcccCcccgcCcc",
    "ccgGgcCccCcgGgcc",
    "cCcgcccccccCgcCc",
    "ccccCccgcCccccdc",
    "cdcCccgGgcccCccc",
    "cccccCcgccCccgcc",
    "cCgccccccccCgGgc",
    "cgGgcCccCcccCgcc",
    "ccgccccgcccCcccc",
    "cCcccCgGgcCccCcd",
    "ccdCcccgcccccccc",
    "cCcccCccccCcgcCc",
    "ccgcccCcdcccgGgc",
    "cgGgccccccCccgcc",
    "ccgcCccdccccCccc",
]
GILLS = [
    "lLlLlLlLlLlLlLlL",
    "LlLlLlLlLlLlLlLl",
] * 8


def png(rows):
    raw = b"".join(b"\x00" + b"".join(bytes(PALETTE[ch]) for ch in row) for row in rows)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    header = struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


TEX.mkdir(parents=True, exist_ok=True)
for name, rows in (("node_stem", STEM), ("node_cap", CAP), ("node_gills", GILLS)):
    assert all(len(r) == 16 for r in rows) and len(rows) == 16, name
    (TEX / f"{name}.png").write_bytes(png(rows))
print("ok")
