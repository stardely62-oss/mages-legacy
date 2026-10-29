"""Lays the harness's films (film_<name>_NN.png) out as one sheet each, frames in order, for judging animation."""
import collections
import pathlib
import sys

from PIL import Image

folder = pathlib.Path(sys.argv[1])
films = collections.defaultdict(list)
for path in sorted(folder.glob("film_*_[0-9][0-9].png")):
    films[path.stem.rsplit("_", 1)[0][5:]].append(path)
for name, frames in films.items():
    thumbs = [Image.open(p).convert("RGB").resize((480, 270)) for p in frames]
    cols = 5
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 480, rows * 270), (20, 16, 24))
    for i, thumb in enumerate(thumbs):
        sheet.paste(thumb, ((i % cols) * 480, (i // cols) * 270))
    sheet.save(folder / f"sheet_{name}.png")
    print(f"sheet_{name}.png: {len(thumbs)} frames")
