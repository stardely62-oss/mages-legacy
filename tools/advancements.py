#!/usr/bin/env python3
"""Writes the advancements of the Mage's Legacy tab (data/mages_legacy/advancement/legacy/*.json) and docs/ADVANCEMENTS.md
from the table below; titles and descriptions come from the lang files (mages_legacy.advancement.<id>.title/.desc).
Each advancement listens to the deed of the same name (criterion mages_legacy:deed, see race/Deeds.java).
Run after changing them: python3 tools/advancements.py (CI checks the files are up to date)."""
import json, pathlib

root = pathlib.Path(__file__).resolve().parent.parent
out = root / "src/main/resources/data/mages_legacy/advancement/legacy"
ru = json.loads((root / "src/main/resources/assets/mages_legacy/lang/ru_ru.json").read_text())

# id, parent, icon, frame, hidden, announce to chat
TABLE = [
    ("root", None, "minecraft:amethyst_cluster", "task", False, False),
    ("gift", "root", "minecraft:experience_bottle", "task", False, False),
    ("first_choice", "gift", "minecraft:writable_book", "task", False, False),
    ("cross", "first_choice", "minecraft:lodestone", "task", False, False),
    ("first_awakening", "cross", "minecraft:end_crystal", "goal", False, True),
    ("mastery", "first_awakening", "minecraft:beacon", "goal", False, True),
    ("double", "mastery", "minecraft:dragon_breath", "challenge", False, True),
    ("peak", "first_awakening", "minecraft:nether_star", "challenge", False, True),

    ("void_pocket", "root", "minecraft:ender_chest", "task", False, False),
    ("void_pocket_big", "void_pocket", "minecraft:purpur_block", "task", False, False),
    ("void_walls", "root", "minecraft:chorus_fruit", "task", False, False),
    ("void_gravity", "void_walls", "minecraft:anvil", "goal", False, False),
    ("void_arena", "void_gravity", "minecraft:crying_obsidian", "goal", False, False),
    ("void_passage", "void_walls", "minecraft:ender_eye", "goal", False, False),
    ("void_horizon", "void_gravity", "minecraft:echo_shard", "challenge", False, True),
    ("void_cage", "void_horizon", "minecraft:respawn_anchor", "challenge", True, True),

    ("myc_sprout", "root", "minecraft:mycelium", "task", False, False),
    ("myc_roots", "myc_sprout", "minecraft:hanging_roots", "goal", False, False),
    ("myc_road", "myc_sprout", "minecraft:compass", "goal", False, False),
    ("myc_epidemic", "root", "minecraft:brown_mushroom", "task", False, False),
    ("myc_breath", "myc_epidemic", "minecraft:red_mushroom", "goal", False, False),
    ("myc_one", "root", "minecraft:glow_lichen", "task", False, False),
    ("myc_bloom", "myc_one", "minecraft:spore_blossom", "goal", False, False),
    ("myc_rebirth", "myc_bloom", "minecraft:mushroom_stem", "challenge", False, True),
    ("myc_perfect", "myc_bloom", "minecraft:shroomlight", "challenge", True, True),
]

out.mkdir(parents=True, exist_ok=True)
for old in out.glob("*.json"):
    old.unlink()
for aid, parent, icon, frame, hidden, announce in TABLE:
    display = {
        "icon": {"id": icon},
        "title": {"translate": f"mages_legacy.advancement.{aid}.title"},
        "description": {"translate": f"mages_legacy.advancement.{aid}.desc"},
        "frame": frame,
        "show_toast": True,
        "announce_to_chat": announce,
        "hidden": hidden,
    }
    if parent is None:
        display["background"] = "mages_legacy:textures/gui/advancements/legacy.png"
    adv = {}
    if parent is not None:
        adv["parent"] = f"mages_legacy:legacy/{parent}"
    adv["display"] = display
    adv["criteria"] = {"deed": {"trigger": "mages_legacy:deed", "conditions": {"deed": aid}}}
    (out / f"{aid}.json").write_text(json.dumps(adv, indent=2, ensure_ascii=False) + "\n")

frames = {"task": "обычное", "goal": "цель", "challenge": "испытание"}
groups = [("Общий путь", ""), ("Пустотник", "void_"), ("Мицелид", "myc_")]
lines = ["# Достижения Mage's Legacy", "",
         "Одна вкладка в стандартном меню достижений (L). Все выдаёт сервер за подтверждённое действие (критерий `mages_legacy:deed`);",
         "сохраняются и отзываются штатно (`/advancement`). Достижения расы засчитываются только этой расе, в тестовом режиме `nocooldown` — никакие.",
         "Наград, кроме записи и уведомления, нет. В чат сервера пишутся только крупные (отмечены ★).",
         "При входе выдаётся то, что доказывают сохранённые данные (раса, уровни, таланты, Пробуждения, испытание мастерства, Материнская грибница, выросшие нити).", ""]
for name, prefix in groups:
    lines += [f"## {name}", "", "| Достижение | Тип | Условие |", "|---|---|---|"]
    for aid, parent, icon, frame, hidden, announce in TABLE:
        mine = aid.startswith(prefix) if prefix else not (aid.startswith("void_") or aid.startswith("myc_"))
        if not mine:
            continue
        title = ru[f"mages_legacy.advancement.{aid}.title"]
        desc = ru[f"mages_legacy.advancement.{aid}.desc"]
        kind = frames[frame] + (", скрытое" if hidden else "") + (" ★" if announce else "")
        lines.append(f"| {title} | {kind} | {desc} |")
    lines.append("")
(root / "docs/ADVANCEMENTS.md").write_text("\n".join(lines))
print(f"{len(TABLE)} advancements")
