#!/usr/bin/env python3
"""Writes the advancements of Mage's Legacy (data/mages_legacy/advancement/legacy/*.json), the models of their painted icons
and docs/ADVANCEMENTS.md from the tables below; titles and descriptions come from the lang files
(mages_legacy.advancement.<id>.title/.desc). Each advancement listens to the deed of the same name (criterion
mages_legacy:deed, see race/Deeds.java).

Every race has its own tab: <race>_root and its own copy of the shared path (gift ... peak), then its own deeds; nothing
of one race hangs under another's. The advancements of the first build (one shared tab: root, gift, ...) stay as
technical entries without a display, so old worlds keep what was recorded under those ids.
Run after changing them: python3 tools/advancements.py (CI checks the files are up to date)."""
import json, pathlib

root = pathlib.Path(__file__).resolve().parent.parent
out = root / "src/main/resources/data/mages_legacy/advancement/legacy"
ru = json.loads((root / "src/main/resources/assets/mages_legacy/lang/ru_ru.json").read_text())

RACES = {"void": "Пустотник", "myc": "Мицелид"}
# The shared path, made once for each race: name, parent, vanilla icon (fallback), frame, announce to chat
PATH = [
    ("root", None, "minecraft:amethyst_cluster", "task", False),
    ("gift", "root", "minecraft:experience_bottle", "task", False),
    ("first_choice", "gift", "minecraft:writable_book", "task", False),
    ("cross", "first_choice", "minecraft:lodestone", "task", False),
    ("first_awakening", "cross", "minecraft:end_crystal", "goal", True),
    ("mastery", "first_awakening", "minecraft:beacon", "goal", True),
    ("double", "mastery", "minecraft:dragon_breath", "challenge", True),
    ("peak", "first_awakening", "minecraft:nether_star", "challenge", True),
]
# Each race's own deeds: id, parent, vanilla icon (fallback), frame, hidden, announce to chat
OWN = [
    ("void_pocket", "void_root", "minecraft:ender_chest", "task", False, False),
    ("void_pocket_big", "void_pocket", "minecraft:purpur_block", "task", False, False),
    ("void_walls", "void_root", "minecraft:chorus_fruit", "task", False, False),
    ("void_gravity", "void_walls", "minecraft:anvil", "goal", False, False),
    ("void_arena", "void_gravity", "minecraft:crying_obsidian", "goal", False, False),
    ("void_passage", "void_walls", "minecraft:ender_eye", "goal", False, False),
    ("void_horizon", "void_gravity", "minecraft:echo_shard", "challenge", False, True),
    ("void_cage", "void_horizon", "minecraft:respawn_anchor", "challenge", True, True),

    ("myc_sprout", "myc_root", "minecraft:mycelium", "task", False, False),
    ("myc_roots", "myc_sprout", "minecraft:hanging_roots", "goal", False, False),
    ("myc_road", "myc_sprout", "minecraft:compass", "goal", False, False),
    ("myc_epidemic", "myc_root", "minecraft:brown_mushroom", "task", False, False),
    ("myc_breath", "myc_epidemic", "minecraft:red_mushroom", "goal", False, False),
    ("myc_one", "myc_root", "minecraft:glow_lichen", "task", False, False),
    ("myc_bloom", "myc_one", "minecraft:spore_blossom", "goal", False, False),
    ("myc_rebirth", "myc_bloom", "minecraft:mushroom_stem", "challenge", False, True),
    ("myc_perfect", "myc_bloom", "minecraft:shroomlight", "challenge", True, True),
]
TABLE = []
for race in RACES:
    for name, parent, icon, frame, announce in PATH:
        TABLE.append((f"{race}_{name}", f"{race}_{parent}" if parent else None, icon, frame, False, announce))
    TABLE += [row for row in OWN if row[0].startswith(race + "_")]
# The first build's shared tab: kept without a display (invisible, never granted again) so saved progress stays readable
OLD = ["root", "gift", "first_choice", "cross", "first_awakening", "mastery", "double", "peak"]

# The painted icons: the item mages_legacy:legacy_icon, one model per picture, chosen by custom model data. The numbers
# are this list's places from 1 and never move: new pictures go at the end, so no advancement ever shows another's
ICONS = ["root", "gift", "first_choice", "cross", "first_awakening", "mastery", "double", "peak",
         "void_pocket", "void_pocket_big", "void_walls", "void_gravity", "void_arena", "void_passage", "void_horizon", "void_cage",
         "myc_sprout", "myc_roots", "myc_road", "myc_epidemic", "myc_breath", "myc_one", "myc_bloom", "myc_rebirth", "myc_perfect",
         "void_root", "void_gift", "void_first_choice", "void_cross", "void_first_awakening", "void_mastery", "void_double", "void_peak",
         "myc_root", "myc_gift", "myc_first_choice", "myc_cross", "myc_first_awakening", "myc_mastery", "myc_double", "myc_peak"]
assets = root / "src/main/resources/assets/mages_legacy"
painted = assets / "textures/item/legacy"
models = assets / "models/item"
(models / "legacy").mkdir(parents=True, exist_ok=True)
overrides = []
for n, name in enumerate(ICONS, 1):
    if (painted / f"{name}.png").exists():
        (models / "legacy" / f"{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"mages_legacy:item/legacy/{name}"}}, indent=2) + "\n")
        overrides.append({"predicate": {"custom_model_data": n}, "model": f"mages_legacy:item/legacy/{name}"})
base = "mages_legacy:item/legacy/root" if (painted / "root.png").exists() else "minecraft:item/amethyst_shard"
(models / "legacy_icon.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": base},
                                                       "overrides": overrides}, indent=2) + "\n")

out.mkdir(parents=True, exist_ok=True)
for old in out.glob("*.json"):
    old.unlink()
ids = {row[0] for row in TABLE}
for aid, parent, icon, frame, hidden, announce in TABLE:
    race = aid.split("_")[0]
    # Nothing of one race under another's: every parent is of the same race and in the table
    assert parent is None or parent.startswith(race + "_") and parent in ids, (aid, parent)
    item = {"id": icon}
    if (painted / f"{aid}.png").exists():
        item = {"id": "mages_legacy:legacy_icon", "components": {"minecraft:custom_model_data": ICONS.index(aid) + 1}}
    display = {
        "icon": item,
        "title": {"translate": f"mages_legacy.advancement.{aid}.title"},
        "description": {"translate": f"mages_legacy.advancement.{aid}.desc"},
        "frame": frame,
        "show_toast": True,
        "announce_to_chat": announce,
        "hidden": hidden,
    }
    if parent is None:
        display["background"] = f"mages_legacy:textures/gui/advancements/{race}.png"
    adv = {}
    if parent is not None:
        adv["parent"] = f"mages_legacy:legacy/{parent}"
    adv["display"] = display
    adv["criteria"] = {"deed": {"trigger": "mages_legacy:deed", "conditions": {"deed": aid}}}
    (out / f"{aid}.json").write_text(json.dumps(adv, indent=2, ensure_ascii=False) + "\n")
for aid in OLD:
    (out / f"{aid}.json").write_text(json.dumps({"criteria": {"deed": {"trigger": "mages_legacy:deed", "conditions": {"deed": aid}}}},
                                                indent=2) + "\n")

frames = {"task": "обычное", "goal": "цель", "challenge": "испытание"}
lines = ["# Достижения Mage's Legacy", "",
         "У каждой расы своя вкладка в стандартном меню достижений (L): «Путь Пустоты» у Пустотников, «Сердце Колонии» у Мицелидов.",
         "Вкладка открывается выбором расы; общий путь развития у каждой расы свой, чужих достижений во вкладке нет.",
         "Все выдаёт сервер за подтверждённое действие (критерий `mages_legacy:deed`); сохраняются и отзываются штатно (`/advancement`).",
         "В тестовом режиме `nocooldown` не выдаётся ничего. Наград, кроме записи и уведомления, нет. В чат сервера пишутся только крупные (★).", "",
         "## Вход, смена расы и старые миры", "",
         "- При входе выдаётся то, что доказывают сохранённые данные (раса, уровни, таланты, Пробуждения, испытание мастерства, Материнская грибница, выросшие нити) — без сообщений в чат.",
         "- При смене расы достижения прежней расы уходят в архив игрока (`mages_legacy:archived_advancements`) и снимаются, а достижения новой расы из архива возвращаются: история не теряется, вкладка показывает только активную расу.",
         "- Старые достижения первой сборки с общей вкладкой (`legacy/root`, `legacy/gift` … `legacy/peak`) оставлены невидимыми техническими записями; их этапы выдаются заново в расовой вкладке, когда сохранённые данные это подтверждают. Расовые достижения (`void_*`, `myc_*`) сохранили свои id и прогресс.", ""]
for race, name in RACES.items():
    lines += [f"## {name}", "", "| Достижение | Тип | Условие |", "|---|---|---|"]
    for aid, parent, icon, frame, hidden, announce in TABLE:
        if not aid.startswith(race + "_"):
            continue
        title = ru[f"mages_legacy.advancement.{aid}.title"]
        desc = ru[f"mages_legacy.advancement.{aid}.desc"]
        kind = frames[frame] + (", скрытое" if hidden else "") + (" ★" if announce else "")
        lines.append(f"| {title} | {kind} | {desc} |")
    lines.append("")
(root / "docs/ADVANCEMENTS.md").write_text("\n".join(lines))
print(f"{len(TABLE)} advancements, {len(OLD)} technical")
