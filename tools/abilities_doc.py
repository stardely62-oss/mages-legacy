#!/usr/bin/env python3
"""Writes docs/ABILITIES.md: every race ability from race/Ability.java (key, level, cost, cooldown) with its Russian
name, description and lore; the Awakenings with their trials. Run after changing abilities: python3 tools/abilities_doc.py
(CI checks the file is up to date)."""
import json, re, pathlib

root = pathlib.Path(__file__).resolve().parent.parent
src = (root / "src/main/java/euf/mageslegacy/race/Ability.java").read_text()
talent_src = (root / "src/main/java/euf/mageslegacy/race/Talent.java").read_text()
lang = json.loads((root / "src/main/resources/assets/mages_legacy/lang/ru_ru.json").read_text())
awakening_level = int(re.search(r'AWAKENING_LEVEL = (\d+);', talent_src).group(1))
keys = re.search(r'KEY_NAMES = \{(.*?)\};', src).group(1).replace('"', "").split(", ")
awakening_of = {"RIFT_ARENA": "awakening_rift", "PASSAGE": "awakening_worlds", "BLACK_HOLE": "awakening_hole"}
races = {"VOIDBORN": "Пустотники"}

def seconds(expr):
    ticks = eval(expr, {}, {})
    s = ticks / 20
    return f"{int(s // 60)} мин" if s >= 60 and s % 60 == 0 else f"{s:g} с"

out = ["# Способности", "",
       "Сгенерировано из `race/Ability.java` и `ru_ru.json` скриптом `tools/abilities_doc.py` — не править руками. "
       "Таланты, которые меняют способности, — в [TALENTS.md](TALENTS.md).", ""]
for race, title in races.items():
    rows = re.findall(r'^\t([A-Z_]+)\(Race\.' + race + r', (-?\d+), ([\w.]+), (\d+), ([\d *]+)\)', src, re.M)
    out += [f"## {title}", "", "Ресурс — **Пустота** (до 100).", "", "### Базовые способности", "",
            "| Способность | Клавиша | С уровня | Цена | Перезарядка |", "|---|---|---|---|---|"]
    basic = [r for r in rows if int(r[1]) >= 0]
    awakenings = [r for r in rows if int(r[1]) < 0]
    for name, slot, level, cost, cd in basic:
        out.append(f"| **{lang[f'mages_legacy.ability.{name.lower()}.name']}** | {keys[int(slot)]} | {level} | {cost} | {seconds(cd)} |")
    out.append("")
    for name, *_ in basic:
        key = f"mages_legacy.ability.{name.lower()}"
        out += [f"**{lang[key + '.name']}** — {lang[key + '.desc']}", ""]
        if key + ".lore" in lang:
            out += [f"> *{lang[key + '.lore']}*", ""]
    out += ["### Пробуждения", "",
            f"Открываются с {awakening_level} уровня расы, каждое — после своего испытания. Можно получить два из трёх: первое встаёт "
            "на клавишу Z, второе (с 45 уровня, через Испытание Пустоты) — на левый Alt; третье остаётся закрытым. "
            "У каждого своя цена и перезарядка, их можно сочетать.", "",
            "| Пробуждение | Цена | Перезарядка |", "|---|---|---|"]
    for name, slot, level, cost, cd in awakenings:
        tid = awakening_of[name]
        out.append(f"| **{lang[f'mages_legacy.talent.{tid}.name']}** | {cost} | {seconds(cd)} |")
    out.append("")
    for name, *_ in awakenings:
        key = f"mages_legacy.talent.{awakening_of[name]}"
        out += [f"**{lang[key + '.name']}** — {lang[key + '.desc']}", ""]
        trial = lang.get(key + ".trial")
        if trial:
            out += ["Испытание: " + trial.split(": ", 1)[-1].replace(" (сейчас: %s мин)", "").replace(" (сейчас: %s)", ""), ""]
        if key + ".lore" in lang:
            out += [f"> *{lang[key + '.lore']}*", ""]
(root / "docs/ABILITIES.md").write_text("\n".join(out) + "\n")
print("docs/ABILITIES.md written")
