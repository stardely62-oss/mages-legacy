#!/usr/bin/env python3
"""The Mercenaries' jobs, in one place: who asks, what, how much, at which tier, and the client's own words.

Writes the template table into mercenary/Contracts.java (between the JOBS markers) and the job and client lines into
both lang files. Run after changing a job: python3 tools/merc_jobs.py (CI checks the files are up to date).
Kinds: hunt (entity id), deliver (item id), explore (biome ids: one job line for all of them), scout (a marked place,
target is "min-max" blocks), bounty (entity id of the named target, then its minions as "type*count").
"""
import json
import pathlib
import re
from collections import OrderedDict

root = pathlib.Path(__file__).resolve().parent.parent

CLIENTS = {
    "guild": ("Гильдия", "The Guild"),
    "guard": ("Стража тракта", "The Road Watch"),
    "farmers": ("Фермерская артель", "The Farmers' Guild"),
    "lodge": ("Охотничья ложа", "The Hunting Lodge"),
    "smith": ("Кузница гильдии", "The Guild Forge"),
    "tanner": ("Кожевник", "The Tanner"),
    "bowyer": ("Мастер-лучник", "The Bowyer"),
    "healer": ("Лекарь", "The Healer"),
    "alchemist": ("Алхимик гильдии", "The Guild Alchemist"),
    "cartographer": ("Картограф гильдии", "The Guild Cartographer"),
    "archivist": ("Архивариус мага", "The Mage's Archivist"),
}

# tier, job id, client, kind, target, amount, ru line, en line[, ru bounty name, en bounty name]
JOBS = [
    (1, "road_zombies", "guard", "hunt", "zombie", 8, "Мертвецы по ночам выходят к тракту. Стража платит за каждого.", "The dead walk onto the road at night. The watch pays for each."),
    (1, "road_skeletons", "guard", "hunt", "skeleton", 6, "Стрелки из темноты бьют по караванам. Выбейте их.", "Archers in the dark shoot at caravans. Clear them out."),
    (1, "barn_spiders", "farmers", "hunt", "spider", 6, "Пауки облюбовали амбары, фермеры боятся заходить внутрь.", "Spiders have taken the barns; the farmers won't go in."),
    (1, "field_creepers", "farmers", "hunt", "creeper", 4, "Кто-то взрывает грядки. Найдите и обезвредьте.", "Something keeps blowing up the fields. Find it and stop it."),
    (1, "pier_drowned", "lodge", "hunt", "drowned", 5, "Утопленники утаскивают рыбаков с пристани.", "The drowned drag fishermen off the pier."),
    (1, "caravan_husks", "lodge", "hunt", "husk", 5, "Кадавры нападают на караваны в пустыне.", "Husks attack the desert caravans."),
    (1, "north_strays", "lodge", "hunt", "stray", 4, "Зимогоры засели на северной тропе. Расчистите путь.", "Strays hold the northern trail. Clear the way."),
    (1, "forge_iron", "smith", "deliver", "iron_ingot", 8, "Кузнице не хватает железа на подковы и замки.", "The forge is short of iron for horseshoes and locks."),
    (1, "forge_coal", "smith", "deliver", "coal", 20, "Горн гаснет. Нужен уголь, и побольше.", "The furnace is dying. Coal, and plenty of it."),
    (1, "forge_copper", "smith", "deliver", "copper_ingot", 12, "Медь на гильдейские жетоны и громоотводы.", "Copper for guild tokens and lightning rods."),
    (1, "bakery_wheat", "farmers", "deliver", "wheat", 24, "Пекарне нужна пшеница к ярмарке.", "The bakery needs wheat for the fair."),
    (1, "stable_carrots", "farmers", "deliver", "carrot", 16, "Морковь для конюшен стражи.", "Carrots for the watch's stables."),
    (1, "tanner_leather", "tanner", "deliver", "leather", 6, "Кожа на ремни и сёдла.", "Leather for belts and saddles."),
    (1, "bowyer_string", "bowyer", "deliver", "string", 8, "Мастер перетягивает тетивы всем лукам гильдии.", "Every bow of the guild is being restrung."),
    (1, "bowyer_feathers", "bowyer", "deliver", "feather", 10, "Перья для стрел гильдии.", "Feathers for the guild's arrows."),
    (1, "healer_bones", "healer", "deliver", "bone", 10, "Костная мука для лечебных настоек.", "Bone meal for healing draughts."),
    (1, "map_land_1", "cartographer", "explore", "desert,savanna,taiga,swamp,dark_forest,snowy_plains,jungle,cherry_grove,badlands,birch_forest", 1,
     "На карте гильдии здесь белое пятно. Дойдите до этого края — засчитается вход в него.", "The guild's map is blank here. Reach this land — entering it counts."),
    (1, "scout_near", "cartographer", "scout", "150-250", 1, "Разведчики не вернулись с этой точки. Сходите и посмотрите, что там.", "The scouts never came back from this spot. Go and see."),

    (2, "raid_pillagers", "guard", "hunt", "pillager", 6, "Разбойники с арбалетами грабят обозы.", "Crossbow bandits are robbing the wagons."),
    (2, "swamp_witches", "lodge", "hunt", "witch", 2, "Ведьмы варят что-то недоброе на болоте.", "Witches are brewing something foul in the swamp."),
    (2, "wall_phantoms", "guard", "hunt", "phantom", 3, "Фантомы налетают на часовых на стенах.", "Phantoms swoop on the sentries on the walls."),
    (2, "mine_spiders", "lodge", "hunt", "cave_spider", 8, "Ядовитые пауки в заброшенной шахте.", "Venomous spiders in the abandoned mine."),
    (2, "pearl_endermen", "alchemist", "hunt", "enderman", 3, "Алхимику нужны жемчужины Края, а носят их эндермены.", "The alchemist needs ender pearls, and endermen carry them."),
    (2, "glue_slimes", "alchemist", "hunt", "slime", 6, "Слизь на клей и зелья.", "Slime for glue and potions."),
    (2, "sea_guardians", "lodge", "hunt", "guardian", 3, "Стражи у монумента топят лодки.", "The guardians at the monument sink boats."),
    (2, "fortress_blazes", "alchemist", "hunt", "blaze", 4, "Ифриты сторожат то, что нужно алхимику.", "Blazes guard what the alchemist needs."),
    (2, "jeweller_gold", "smith", "deliver", "gold_ingot", 8, "Ювелиры гильдии ждут золото.", "The guild's jewellers are waiting for gold."),
    (2, "gate_redstone", "smith", "deliver", "redstone", 24, "Механизм для ворот крепости.", "Works for the fortress gate."),
    (2, "tower_obsidian", "smith", "deliver", "obsidian", 6, "Обсидиан на фундамент новой башни.", "Obsidian for the new tower's footing."),
    (2, "brew_wart", "alchemist", "deliver", "nether_wart", 12, "Без адского нароста не сварить ни одного зелья.", "Not one potion brews without nether wart."),
    (2, "lamp_glowstone", "alchemist", "deliver", "glowstone_dust", 12, "Светопыль для фонарей и усилителей.", "Glowstone dust for lamps and boosters."),
    (2, "stand_rods", "alchemist", "deliver", "blaze_rod", 4, "Стержни ифрита для варочных стоек.", "Blaze rods for brewing stands."),
    (2, "ward_carrots", "healer", "deliver", "golden_carrot", 6, "Золотая морковь для раненых стражников.", "Golden carrots for wounded guards."),
    (2, "antidote_honey", "healer", "deliver", "honey_bottle", 4, "Мёд для противоядия.", "Honey for an antidote."),
    (2, "map_land_2", "cartographer", "explore", "ice_spikes,lush_caves,dripstone_caves,mangrove_swamp,bamboo_jungle,crimson_forest,warped_forest,soul_sand_valley,basalt_deltas", 1,
     "Этот край гильдия ещё не наносила на карту. Дойдите до него — засчитается вход.", "The guild hasn't mapped this land yet. Reach it — entering counts."),
    (2, "scout_mid", "cartographer", "scout", "300-450", 1, "Отсюда давно не было вестей. Дойдите до отметки и осмотритесь.", "No word has come from here in a while. Reach the mark and look around."),
    (2, "bounty_captain", "guard", "bounty", "pillager;pillager*2", 1, "Главарь налётчиков ушёл в холмы со своими людьми. Его голова — гильдии.",
     "The raiders' captain fled into the hills with his men. His head belongs to the guild.", "Главарь налётчиков", "Raider Captain"),
    (2, "bounty_witch", "lodge", "bounty", "witch;", 1, "Болотная ведьма прокляла три деревни. Ложа объявила награду.",
     "The swamp witch has cursed three villages. The lodge has posted a reward.", "Болотная ведьма", "The Swamp Witch"),

    (3, "manor_vindicators", "guard", "hunt", "vindicator", 4, "Поборники из особняка вышли на дороги.", "Vindicators from the mansion are on the roads."),
    (3, "village_evoker", "guard", "hunt", "evoker", 1, "Заклинатель насылает досаждателей на деревню.", "An evoker sends vexes against the village."),
    (3, "farm_ravager", "guard", "hunt", "ravager", 1, "Разоритель смял ограду фермы.", "A ravager has flattened a farm's fence."),
    (3, "fortress_skulls", "alchemist", "hunt", "wither_skeleton", 4, "Иссушённые скелеты стерегут крепость Незера.", "Wither skeletons guard the nether fortress."),
    (3, "bastion_brutes", "lodge", "hunt", "piglin_brute", 2, "Громилы бастиона не дают пройти разведке.", "Bastion brutes bar the scouts' way."),
    (3, "trial_breezes", "lodge", "hunt", "breeze", 2, "Вихри в испытательных залах.", "Breezes in the trial chambers."),
    (3, "marsh_bogged", "lodge", "hunt", "bogged", 4, "Болотники отравленными стрелами бьют охотников.", "The bogged shoot hunters with poisoned arrows."),
    (3, "blade_diamonds", "smith", "deliver", "diamond", 3, "Алмазы на гильдейские клинки.", "Diamonds for guild blades."),
    (3, "trade_emeralds", "smith", "deliver", "emerald", 8, "Изумруды для торговых расчётов гильдии.", "Emeralds for the guild's trade."),
    (3, "regen_tears", "alchemist", "deliver", "ghast_tear", 2, "Слёзы гаста для зелья регенерации.", "Ghast tears for a potion of regeneration."),
    (3, "feather_membranes", "alchemist", "deliver", "phantom_membrane", 3, "Мембраны для зелья плавного падения.", "Membranes for a potion of slow falling."),
    (3, "luck_foot", "alchemist", "deliver", "rabbit_foot", 2, "Кроличья лапка на удачу и прыгучесть.", "A rabbit's foot for luck and leaping."),
    (3, "spyglass_amethyst", "archivist", "deliver", "amethyst_shard", 12, "Аметист для подзорных труб архива.", "Amethyst for the archive's spyglasses."),
    (3, "chart_shells", "archivist", "deliver", "nautilus_shell", 2, "Раковины наутилуса для морской карты.", "Nautilus shells for a sea chart."),
    (3, "map_land_3", "cartographer", "explore", "deep_dark,warm_ocean,jagged_peaks,frozen_peaks,eroded_badlands,mushroom_fields", 1,
     "Редкий край, о котором в архиве почти ничего нет. Дойдите до него — засчитается вход.", "A rare land the archive knows almost nothing of. Reach it — entering counts."),
    (3, "scout_far", "cartographer", "scout", "450-650", 1, "Далёкая отметка на старой карте мага. Проверьте, что там осталось.", "A far mark on the mage's old map. See what is left there."),
    (3, "bounty_executioner", "guard", "bounty", "vindicator;vindicator*1", 1, "Палач из особняка рубит путников у переправы.",
     "The mansion's executioner cuts down travellers at the ford.", "Палач из особняка", "The Mansion Executioner"),
    (3, "bounty_marksman", "lodge", "bounty", "skeleton;skeleton*2", 1, "Костяной стрелок со свитой засел на холме. Он не промахивается.",
     "A bone marksman and his retinue hold a hill. He never misses.", "Костяной стрелок", "The Bone Marksman"),

    (4, "end_endermen", "archivist", "hunt", "enderman", 10, "Архиву нужны жемчужины Края — много.", "The archive needs ender pearls — many."),
    (4, "end_shulkers", "archivist", "hunt", "shulker", 3, "Панцири шалкеров на походные сундуки.", "Shulker shells for travel chests."),
    (4, "deep_elder", "lodge", "hunt", "elder_guardian", 1, "Древний страж наслал усталость на шахтёров побережья.", "An elder guardian has laid fatigue on the coast's miners."),
    (4, "raid_evokers", "guard", "hunt", "evoker", 2, "Заклинатели собирают армию.", "Evokers are gathering an army."),
    (4, "raid_ravagers", "guard", "hunt", "ravager", 2, "Разорители ведут рейд на деревню.", "Ravagers lead a raid on a village."),
    (4, "wither_skulls", "alchemist", "hunt", "wither_skeleton", 8, "Черепа иссушителей — товар особого спроса.", "Wither skulls are in great demand."),
    (4, "forge_netherite", "smith", "deliver", "netherite_scrap", 2, "Незеритовые обломки на лучшие клинки гильдии.", "Netherite scrap for the guild's finest blades."),
    (4, "captain_diamonds", "smith", "deliver", "diamond", 8, "Большой заказ алмазов на доспехи капитанов.", "A large order of diamonds for the captains' armour."),
    (4, "teleport_chorus", "alchemist", "deliver", "chorus_fruit", 24, "Хорус для зелий перемещения.", "Chorus fruit for potions of travel."),
    (4, "compass_echo", "archivist", "deliver", "echo_shard", 2, "Осколки эха для компаса восстановления.", "Echo shards for a recovery compass."),
    (4, "chest_shells", "archivist", "deliver", "shulker_shell", 2, "Ещё две раковины шалкера для сундуков.", "Two more shulker shells for chests."),
    (4, "map_land_4", "cartographer", "explore", "end_highlands,end_midlands,small_end_islands,deep_dark,mushroom_fields", 1,
     "Карта Края и глубин пуста. Дойдите до этого края — засчитается вход.", "The map of the End and the deep is empty. Reach this land — entering counts."),
    (4, "scout_edge", "cartographer", "scout", "700-1000", 1, "На краю известного мира что-то горит по ночам. Узнайте что.", "Something burns at night at the edge of the known world. Find out what."),
    (4, "bounty_ravager", "guard", "bounty", "ravager;pillager*2", 1, "Вожак разорителей и его наездники разносят хутора.",
     "The ravager chief and its riders are wrecking the homesteads.", "Вожак разорителей", "The Ravager Chief"),
    (4, "bounty_evoker", "guard", "bounty", "evoker;vindicator*2", 1, "Тёмный заклинатель со стражей готовит большой рейд.",
     "A dark evoker and his guard are preparing a great raid.", "Тёмный заклинатель", "The Dark Evoker"),
]


def java_line(job):
    tier, jid, client, kind, target, amount = job[:6]
    if kind in ("hunt", "deliver"):
        target = "minecraft:" + target
    elif kind == "explore":
        target = ",".join("minecraft:" + b for b in target.split(","))
    elif kind == "bounty":
        head, minions = target.split(";")
        target = "minecraft:" + head + (";" + ",".join("minecraft:" + m for m in minions.split(",")) if minions else ";")
    return f'\t\tjob({tier}, "{jid}", "{client}", Contract.Kind.{kind.upper()}, "{target}", {amount});'


lines = [java_line(j) for j in JOBS]
src = root / "src/main/java/euf/mageslegacy/mercenary/Contracts.java"
text = src.read_text()
text = re.sub(r"(\t\t// JOBS: generated by tools/merc_jobs.py\n).*?(\t\t// END JOBS\n)", lambda m: m.group(1) + "\n".join(lines) + "\n" + m.group(2), text,
              flags=re.S)
src.write_text(text)

for lang, idx in (("ru_ru", 0), ("en_us", 1)):
    path = root / f"src/main/resources/assets/mages_legacy/lang/{lang}.json"
    data = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=OrderedDict)
    for key in list(data):
        if key.startswith("mages_legacy.merc.job.") or key.startswith("mages_legacy.merc.client.") or key.startswith("mages_legacy.merc.bounty."):
            del data[key]
    for cid, names in CLIENTS.items():
        data[f"mages_legacy.merc.client.{cid}"] = names[idx]
    for job in JOBS:
        data[f"mages_legacy.merc.job.{job[1]}"] = job[6 + idx]
        if job[3] == "bounty":
            data[f"mages_legacy.merc.bounty.{job[1]}"] = job[8 + idx]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"{len(JOBS)} jobs")
