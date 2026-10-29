#!/usr/bin/env python3
"""Writes docs/TALENTS.md: every talent of the tree from race/Talent.java with its Russian name, description and lore.
Run after changing talents: python3 tools/talents_doc.py (CI checks the file is up to date)."""
import json, re, pathlib

root = pathlib.Path(__file__).resolve().parent.parent
src = (root / "src/main/java/euf/mageslegacy/race/Talent.java").read_text()
lang = json.loads((root / "src/main/resources/assets/mages_legacy/lang/ru_ru.json").read_text())

rows = re.findall(r'^\t([A-Z_0-9]+)\(Race\.(\w+), Kind\.(\w+), Branch\.(\w+), [\d.f]+, (\d+), (\d+), (\d+), "[^"]*"((?:, "[^"]*")*)\)', src, re.M)

def tier(kind, row):
    # As Talent.tier(): the grid's rows are the tiers
    if kind in ("CORE", "BASIC"):
        return None
    row = int(row)
    return 1 if row <= 1 else 2 if row == 2 else 3 if row <= 4 else 4

branches = {"CORE": "Ядро и базовые способности", "RIFT": "Разлом пространства", "WORLDS": "Сквозь миры", "GRAVITY": "Гравитация",
            "CROSS": "Перекрёстные таланты"}
kinds = {"CORE": "ядро", "BASIC": "базовая", "PASSIVE": "пассивный", "TRANSFORM": "преобразующий", "CROSS": "перекрёстный",
         "AWAKENING": "Пробуждение"}
out = ["# Таланты Пустотников", "",
       "Сгенерировано из `race/Talent.java` и `ru_ru.json` скриптом `tools/talents_doc.py` — не править руками.", "",
       "Ярусы открываются уровнем расы: I — сразу, II — с 10, III — с 20, IV (Пробуждения и их таланты) — с 30. "
       "Цена: ярус I — 1 очко, II — 2, III и IV — 3, Пробуждение — 5. Очков к 50 уровню — 60, всё дерево взять нельзя.", ""]
count = 0
for branch, title in branches.items():
    items = [r for r in rows if r[3] == branch]
    if not items:
        continue
    out += [f"## {title}", "", "| Талант | Тип | Ярус | Цена | Нужен один из | Что делает |", "|---|---|---|---|---|---|"]
    for name, race, kind, br, row, cost, auto, req in items:
        tid = name.lower()
        requires = re.findall(r'"([^"]+)"', req)
        t = tier(kind, row)
        key = f"mages_legacy.talent.{tid}"
        title_ = lang.get(key + ".name", tid)
        desc = lang.get(key + ".desc", "")
        lore = lang.get(key + ".lore")
        if lore:
            desc += f" *{lore}*"
        needs = ", ".join(lang.get(f"mages_legacy.talent.{r}.name", r) for r in requires) or "—"
        if t is None:
            tier_s, cost_s = f"сама на {auto} ур.", "0"
        else:
            tier_s = ["I", "II", "III", "IV"][t - 1]
            cost_s = "5" if kind == "AWAKENING" else str(min(3, t))
        out.append(f"| **{title_}** | {kinds[kind]} | {tier_s} | {cost_s} | {needs} | {desc.replace('|', '/')} |")
        count += 1
    out.append("")
out.insert(4, f"Всего узлов: **{count}**.")
out.insert(5, "")
(root / "docs/TALENTS.md").write_text("\n".join(out) + "\n")
print(f"docs/TALENTS.md: {count} talents")
