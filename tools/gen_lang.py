"""Writes assets/mages_legacy/lang/ru_ru.json and en_us.json. Numbered keys (.1, .2, ...) are read as lists by the mod."""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/assets/mages_legacy/lang"


def numbered(d, key, lines):
    for i, line in enumerate(lines, 1):
        d[f"{key}.{i}"] = line


def build(t):
    d = {}
    d.update(t["fixed"])
    numbered(d, "mages_legacy.lore.intro", t["intro"])
    for race, r in t["races"].items():
        k = f"mages_legacy.race.{race}"
        d[k] = r["name"]
        d[k + ".tagline"] = r["tagline"]
        numbered(d, k + ".lore", r["lore"])
        numbered(d, k + ".perk", r["perk"])
        numbered(d, k + ".weak", r["weak"])
        for i, (name, desc, lore) in enumerate(r["abilities"], 1):
            a = f"mages_legacy.ability.{race}.{i}"
            d[a + ".name"] = name
            d[a + ".desc"] = desc
            d[a + ".lore"] = lore
    for i, (name, lore) in enumerate(t["branches"]):
        d[f"mages_legacy.branch.mycelid.{i}"] = name
        d[f"mages_legacy.branch.mycelid.{i}.lore"] = lore
    for tid, (name, lore, desc) in t["talents"].items():
        k = f"mages_legacy.talent.{tid}"
        d[k] = name
        d[k + ".lore"] = lore
        d[k + ".desc"] = desc
    return d


RU = {
    "fixed": {
        "block.mages_legacy.mycelium_node": "Узел грибницы",
        "category.mages_legacy": "Наследие мага",
        "key.mages_legacy.ability_1": "Способность расы 1",
        "key.mages_legacy.ability_2": "Способность расы 2",
        "key.mages_legacy.ability_3": "Способность расы 3",
        "key.mages_legacy.talents": "Дерево талантов",
        "mages_legacy.lore.intro.title": "Наследие мага",
        "mages_legacy.screen.next": "Дальше",
        "mages_legacy.screen.choose_title": "Чьё наследие ты понесёшь?",
        "mages_legacy.screen.back_to_legend": "Назад к легенде",
        "mages_legacy.screen.choose": "Стать: %s",
        "mages_legacy.screen.soon": "Эта раса ещё спит",
        "mages_legacy.screen.soon_short": "Скоро пробудится",
        "mages_legacy.screen.legend": "Легенда",
        "mages_legacy.screen.strengths": "Сильные стороны",
        "mages_legacy.screen.weaknesses": "Слабости",
        "mages_legacy.screen.abilities": "Способности",
        "mages_legacy.screen.talents": "Дерево талантов",
        "mages_legacy.screen.tree_header": "%s · уровень %s · опыт %s/%s",
        "mages_legacy.screen.points": "Свободных очков: %s",
        "mages_legacy.screen.ability_line": "Клавиша %s · %s спор",
        "mages_legacy.screen.cost": "Цена: %s очк.",
        "mages_legacy.screen.state.owned": "Изучено",
        "mages_legacy.screen.state.available": "Нажмите, чтобы изучить",
        "mages_legacy.screen.state.no_points": "Не хватает очков",
        "mages_legacy.screen.state.needs": "Сначала изучите: %s",
        "mages_legacy.screen.state.tier_2": "Ярус откроется, когда вы побываете в Незере",
        "mages_legacy.screen.state.tier_3": "Ярус откроется, когда вы призовёте Иссушителя",
        "mages_legacy.screen.state.tier_4": "Ярус откроется после победы над драконом",
        "mages_legacy.message.chosen": "Дар мага проснулся: теперь вы из народа «%s». Клавиша K открывает дерево талантов.",
        "mages_legacy.message.level_up": "Наследие крепнет: уровень расы %s, свободных очков: %s",
        "mages_legacy.unlock.ok": "Талант изучен",
        "mages_legacy.unlock.no_race": "Сначала выберите расу",
        "mages_legacy.unlock.unknown": "Такого таланта нет",
        "mages_legacy.unlock.other_race": "Это талант другой расы",
        "mages_legacy.unlock.already": "Уже изучено",
        "mages_legacy.unlock.no_points": "Не хватает очков талантов",
        "mages_legacy.unlock.needs_talent": "Сначала изучите предыдущий талант ветки",
        "mages_legacy.unlock.needs_milestone": "Этот ярус ещё закрыт",
        "mages_legacy.ability.cooldown": "Способность ещё восстанавливается",
        "mages_legacy.ability.no_resource": "Не хватает спор",
        "mages_legacy.ability.no_target": "Посмотрите на блок рядом",
        "mages_legacy.ability.locked": "Откроется, когда вы побываете в Незере",
        "mages_legacy.ability.no_space": "Там негде прорасти",
        "mages_legacy.mycelid.node_alone": "Грибнице некуда вести: других узлов рядом нет",
        "mages_legacy.command.no_race": "Вы ещё не выбрали расу",
        "mages_legacy.command.show": "Раса: %s · уровень %s · свободных очков: %s",
        "mages_legacy.command.unknown_race": "Неизвестная раса",
    },
    "intro": [
        "Когда-то все люди были одним народом: слабым, смертным и одинаковым.",
        "Потом пришёл маг, чьё имя забыли раньше, чем были построены первые города. Он поднял первый обелиск и через его око увидел, кем мог бы стать каждый из нас.",
        "Одних он отдал земле, и грибница проросла сквозь их кости. Другим дал голос, на который откликаются звери и эхо павших. Третьих остановил во времени. Четвёртым велел платить за жизнь кровью. Пятых провёл сквозь пустоту и вернул не целиком.",
        "Так из одного народа стало пять. Маг ушёл, и никто не видел куда. Но его дар не исчез: он спит в крови каждого, кто рождается в этом мире.",
        "Сегодня он проснётся в тебе. Выбери, чьё наследие ты понесёшь. Выбор делается один раз.",
    ],
    "races": {
        "mycelid": {
            "name": "Мицелиды", "tagline": "Споры, грибница и тьма",
            "lore": [
                "Маг отдал их земле. Грибница проросла сквозь их кости и стала второй кровью.",
                "Мицелиды видят во тьме, лечатся на мицелии и сыты грибами, которыми другие травятся.",
                "Говорят, где-то под корнями мира грибница до сих пор помнит голос мага и пересказывает его мицелидам во снах.",
            ],
            "perk": [
                "Споры: ресурс расы, копятся сами, быстрее во тьме, на мицелии и от грибов",
                "Ночное зрение во тьме, регенерация во тьме и на мицелии",
                "Сырые грибы съедобны (ПКМ в воздух) и дают вкус: силу, сопротивление, огнестойкость или скорость",
                "Грибные похлёбки дают споры",
            ],
            "weak": [
                "На прямом солнце слабость и быстрее голод",
                "Огонь жжёт в полтора раза сильнее",
                "Мясо насыщает вдвое хуже",
            ],
            "abilities": [
                ("Споровый прыжок", "Через мгновение прорастаешь там, куда смотришь, до 8 блоков. 15 спор.",
                 "Спора улетает вперёд, а грибница прорастает следом, и ты вместе с ней."),
                ("Облако спор", "Облако радиусом 4 на 6 секунд: монстры слепнут и травятся (нежить гниёт), игроки лечатся. 30 спор.",
                 "Для врагов это удушье, для своих тёплое дыхание старого леса."),
                ("Грибница", "Выращивает узел сети (до 3). ПКМ по узлу переносит к следующему узлу в 150 блоках. Пользоваться могут все. Открывается после Незера. 40 спор.",
                 "Грибница помнит каждый узел, который ты вырастил, и проведёт по ним любого, кому ты доверяешь."),
            ],
        },
        "shepherd": {
            "name": "Пастыри", "tagline": "Звери уз и эхо павших",
            "lore": [
                "Маг дал им голос, на который откликаются звери и эхо тех, кто ушёл.",
                "Пастырь слаб телом, но никогда не бывает один: рядом всегда стая, а за спиной стоит армия теней.",
            ],
            "perk": [
                "Эхо: призыв нежити и монстров, которых вы научились призывать, убивая их",
                "Узы: особое приручение зверей, от собак и кошек до мышей, сов и ежей",
                "Каждый зверь уз полезен: ищет руду, сторожит, лечит, разведывает",
            ],
            "weak": [
                "Меньше здоровья и слабее собственный удар",
                "Тяжёлая броня мешает держать узы",
                "Павший зверь возвращается не сразу",
            ],
            "abilities": [
                ("Зов", "Призвать или отозвать своих существ.", "Они приходят на голос, даже если давно мертвы."),
                ("Клич", "Все ваши существа наносят на 30% больше урона 10 секунд.", "Стая слышит не слова, а сердце пастыря."),
                ("Обмен", "Поменяться местами со своим существом.", "Узы натягиваются, как верёвка, и тянут в обе стороны."),
            ],
        },
        "frozen": {
            "name": "Застывшие", "tagline": "Песок времени",
            "lore": [
                "Ученики мага, застрявшие в мгновении, когда он исчез.",
                "Время течёт сквозь них иначе: они помнят последние секунды и могут в них вернуться.",
            ],
            "perk": [
                "Откат: вернуться туда и с тем здоровьем, что были 5 секунд назад",
                "Купол, в котором враги и снаряды почти замирают",
                "Ускоряют печи, варку зелий и рост растений",
            ],
            "weak": [
                "Медленная естественная регенерация",
                "Зелья действуют короче",
                "Без песка времени накапливается оцепенение",
            ],
            "abilities": [
                ("Откат", "Вернуться на место и со здоровьем 5 секунд назад.", "Мгновение, которое не прошло, можно прожить ещё раз."),
                ("Застывший купол", "Враги и снаряды в куполе замедляются почти до остановки.", "Внутри купола время дышит медленно, как спящий."),
                ("Ускорение", "Блок, на который смотрите, работает в 10 раз быстрее.", "Песок пересыпается быстрее, если его подтолкнуть."),
            ],
        },
        "bloodkin": {
            "name": "Кровники", "tagline": "Кровь за долгую жизнь",
            "lore": [
                "Они предложили магу свою кровь в обмен на долгую жизнь. Он взял и не вернул.",
                "С тех пор кровники живут чужой кровью и слышат живых сквозь стены по стуку сердец.",
            ],
            "perk": [
                "Вампиризм: часть нанесённого урона лечит",
                "Видят сердцебиение живых сквозь стены",
                "Кровавые ритуалы и алтарь крови",
            ],
            "weak": [
                "Горят на солнце без капюшона",
                "Обычная еда почти не насыщает",
                "Жители деревень их боятся",
            ],
            "abilities": [
                ("Кровавый рывок", "Рывок сквозь врага, который оставляет кровотечение.", "Кровь сама знает дорогу к чужому сердцу."),
                ("Жатва", "Вытянуть кровь из всех врагов рядом и вылечиться.", "Долг мага платят все, кто стоит рядом."),
                ("Кровавая жертва", "Отдать своё здоровье, чтобы сразу получить много крови.", "Иногда, чтобы взять, нужно сначала отдать."),
            ],
        },
        "voidborn": {
            "name": "Пустотники", "tagline": "Блинк и разломы",
            "lore": [
                "Маг провёл их сквозь первый обелиск. Вернулись они не целиком: часть каждого до сих пор осталась в пустоте.",
                "Эндермены считают их своими, а обелиски откликаются им сильнее, чем другим.",
            ],
            "perk": [
                "Блинк на 8 блоков по взгляду",
                "Разлом: портал, которым проходит вся команда",
                "Эндер-сундук всегда с собой",
                "Эндер-обелиски для них дальше и быстрее",
            ],
            "weak": [
                "Вода жжёт",
                "Дождь ослабляет",
            ],
            "abilities": [
                ("Блинк", "Мгновенно переместиться на 8 блоков по взгляду.", "Пустота всегда рядом, нужно лишь шагнуть."),
                ("Разлом", "Пара порталов на 10 секунд, проходят все.", "Мир тонок, если знать, где рвать."),
                ("Изгнание", "Забросить врага далеко вверх.", "Пустота забирает то, что ей отдают."),
            ],
        },
    },
    "branches": [
        ("Рой", "Споры, что душат врагов и лечат своих."),
        ("Сеть", "Грибница под всем миром: прыжки, корни и узлы."),
        ("Плоть", "Тело, которое кормится грибами и тьмой."),
    ],
    "talents": {
        "swarm_wide": ("Широкое облако", "Споры не знают границ, только ветер.", "Радиус облака спор 6 блоков вместо 4."),
        "swarm_trail": ("Ядовитый след", "Там, откуда ты прыгнул, остаётся подарок.", "Споровый прыжок оставляет на месте старта маленькое облако спор на 3 секунды."),
        "swarm_potent": ("Едкие споры", "Чем старше грибница, тем горче её дыхание.", "Облако травит сильнее (Отравление II) и замедляет монстров."),
        "swarm_infection": ("Заражение", "Всё мёртвое становится почвой.", "Монстр, умерший в вашем облаке, даёт 10 спор и опыт расы."),
        "swarm_lingering": ("Долгое цветение", "Споры оседают и не спешат уходить.", "Облако держится 12 секунд вместо 6."),
        "net_chain": ("Цепь прыжков", "Грибница прорастает быстрее, чем враг успевает обернуться.", "После прыжка ещё 2 прыжка в течение 3 секунд стоят по 5 спор и без перезарядки."),
        "net_reach": ("Дальний бросок", "Спора летит дальше, если отпустить её вовремя.", "Дальность спорового прыжка 12 блоков вместо 8."),
        "net_nodes": ("Разросшаяся сеть", "Чем больше узлов, тем больше мир похож на дом.", "До 5 узлов грибницы вместо 3."),
        "net_far": ("Глубокие корни", "Под землёй расстояния короче.", "Узлы связаны на 500 блоков вместо 150."),
        "net_roots": ("Хватка корней", "Там, где ты прорастаешь, земля держит чужаков.", "После прыжка монстры в 3 блоках вокруг почти не могут двигаться 2 секунды."),
        "flesh_two_tastes": ("Два вкуса", "Желудок мицелида шире, чем кажется.", "Одновременно действуют два вкуса грибов вместо одного."),
        "flesh_rich": ("Насыщенный вкус", "Мицелид знает, какой гриб сорвать, а какой оставить дозреть.", "Вкусы сильнее на уровень и длятся 2 минуты вместо одной."),
        "flesh_dark_regen": ("Ночная плоть", "Во тьме раны затягиваются, как трещины в коре.", "Регенерация II вместо I во тьме и на мицелии."),
        "flesh_cap": ("Шляпка", "Кожа твердеет, как шляпка старого гриба.", "+4 к броне, пока вы не на солнце."),
        "flesh_sunproof": ("Тень внутри", "Ты носишь тьму с собой, и солнце над тобой больше не властно.", "Солнце больше не вызывает слабость и голод."),
    },
}

EN = {
    "fixed": {
        "block.mages_legacy.mycelium_node": "Mycelium Node",
        "category.mages_legacy": "Mage's Legacy",
        "key.mages_legacy.ability_1": "Race ability 1",
        "key.mages_legacy.ability_2": "Race ability 2",
        "key.mages_legacy.ability_3": "Race ability 3",
        "key.mages_legacy.talents": "Talent tree",
        "mages_legacy.lore.intro.title": "The Mage's Legacy",
        "mages_legacy.screen.next": "Continue",
        "mages_legacy.screen.choose_title": "Whose legacy will you carry?",
        "mages_legacy.screen.back_to_legend": "Back to the legend",
        "mages_legacy.screen.choose": "Become: %s",
        "mages_legacy.screen.soon": "This race still sleeps",
        "mages_legacy.screen.soon_short": "Awakening soon",
        "mages_legacy.screen.legend": "Legend",
        "mages_legacy.screen.strengths": "Strengths",
        "mages_legacy.screen.weaknesses": "Weaknesses",
        "mages_legacy.screen.abilities": "Abilities",
        "mages_legacy.screen.talents": "Talent tree",
        "mages_legacy.screen.tree_header": "%s · level %s · experience %s/%s",
        "mages_legacy.screen.points": "Free points: %s",
        "mages_legacy.screen.ability_line": "Key %s · %s spores",
        "mages_legacy.screen.cost": "Cost: %s pt.",
        "mages_legacy.screen.state.owned": "Learned",
        "mages_legacy.screen.state.available": "Click to learn",
        "mages_legacy.screen.state.no_points": "Not enough points",
        "mages_legacy.screen.state.needs": "Learn first: %s",
        "mages_legacy.screen.state.tier_2": "This tier opens once you have been to the Nether",
        "mages_legacy.screen.state.tier_3": "This tier opens once you summon the Wither",
        "mages_legacy.screen.state.tier_4": "This tier opens once the dragon is slain",
        "mages_legacy.message.chosen": "The mage's gift awakens: you are now one of the %s. Press K for the talent tree.",
        "mages_legacy.message.level_up": "Your legacy grows: race level %s, free points: %s",
        "mages_legacy.unlock.ok": "Talent learned",
        "mages_legacy.unlock.no_race": "Choose a race first",
        "mages_legacy.unlock.unknown": "No such talent",
        "mages_legacy.unlock.other_race": "That talent belongs to another race",
        "mages_legacy.unlock.already": "Already learned",
        "mages_legacy.unlock.no_points": "Not enough talent points",
        "mages_legacy.unlock.needs_talent": "Learn the previous talent of this branch first",
        "mages_legacy.unlock.needs_milestone": "This tier is still closed",
        "mages_legacy.ability.cooldown": "The ability is still recovering",
        "mages_legacy.ability.no_resource": "Not enough spores",
        "mages_legacy.ability.no_target": "Look at a block nearby",
        "mages_legacy.ability.locked": "Opens once you have been to the Nether",
        "mages_legacy.ability.no_space": "Nowhere to sprout there",
        "mages_legacy.mycelid.node_alone": "The mycelium has nowhere to lead: no other node nearby",
        "mages_legacy.command.no_race": "You have not chosen a race yet",
        "mages_legacy.command.show": "Race: %s · level %s · free points: %s",
        "mages_legacy.command.unknown_race": "Unknown race",
    },
    "intro": [
        "Once all people were one folk: weak, mortal and all alike.",
        "Then came a mage whose name was forgotten before the first cities were built. He raised the first obelisk, and through its eye he saw what each of us could become.",
        "Some he gave to the earth, and mycelium grew through their bones. To others he gave a voice that beasts and the echoes of the fallen answer. A third folk he stopped in time. A fourth he made pay for life in blood. The fifth he led through the void and did not bring back whole.",
        "So one folk became five. The mage left, and no one saw where. But his gift did not vanish: it sleeps in the blood of everyone born into this world.",
        "Today it wakes in you. Choose whose legacy you will carry. The choice is made once.",
    ],
    "races": {
        "mycelid": {
            "name": "Mycelids", "tagline": "Spores, mycelium and darkness",
            "lore": [
                "The mage gave them to the earth. Mycelium grew through their bones and became a second blood.",
                "Mycelids see in the dark, heal on mycelium and feed on mushrooms that poison everyone else.",
                "They say that somewhere under the roots of the world the mycelium still remembers the mage's voice and retells it to the Mycelids in their dreams.",
            ],
            "perk": [
                "Spores: the race resource, growing by itself and faster in the dark, on mycelium and from mushrooms",
                "Night vision in the dark, regeneration in the dark and on mycelium",
                "Raw mushrooms are food (right click into the air) and give a taste: strength, resistance, fire resistance or speed",
                "Mushroom stews give spores",
            ],
            "weak": [
                "Weakness and faster hunger in direct sunlight",
                "Fire burns half again as badly",
                "Meat feeds only half as well",
            ],
            "abilities": [
                ("Spore Jump", "A moment later you sprout where you look, up to 8 blocks away. 15 spores.",
                 "The spore flies ahead, the mycelium sprouts after it, and you with it."),
                ("Spore Cloud", "A cloud of radius 4 for 6 seconds: monsters go blind and are poisoned (the undead rot), players heal. 30 spores.",
                 "To enemies it is choking; to friends, the warm breath of an old forest."),
                ("Mycelium", "Grows a node of your network (up to 3). Right click a node to travel to the next one within 150 blocks. Anyone may use it. Opens after the Nether. 40 spores.",
                 "The mycelium remembers every node you grew and will lead anyone you trust along them."),
            ],
        },
        "shepherd": {
            "name": "Shepherds", "tagline": "Bonded beasts and echoes of the fallen",
            "lore": [
                "The mage gave them a voice that beasts and the echoes of the departed answer.",
                "A Shepherd is frail, but never alone: a pack is always near, and an army of shadows stands behind.",
            ],
            "perk": [
                "Echoes: summon undead and monsters you learned to call by slaying them",
                "Bonds: a special taming of beasts, from dogs and cats to mice, owls and hedgehogs",
                "Every bonded beast helps: finds ore, keeps watch, heals, scouts",
            ],
            "weak": ["Less health and a weaker own strike", "Heavy armour strains the bonds", "A fallen beast does not return at once"],
            "abilities": [
                ("Call", "Summon or dismiss your creatures.", "They come to the voice, even long dead."),
                ("Rally", "All your creatures deal 30% more damage for 10 seconds.", "The pack hears not words but the Shepherd's heart."),
                ("Swap", "Trade places with one of your creatures.", "The bond pulls like a rope, both ways."),
            ],
        },
        "frozen": {
            "name": "The Frozen", "tagline": "Sand of time",
            "lore": [
                "The mage's apprentices, caught in the moment he vanished.",
                "Time flows through them differently: they remember the last seconds and can return to them.",
            ],
            "perk": ["Rewind: return to where you were, with the health you had, 5 seconds ago",
                     "A dome where enemies and projectiles nearly stop", "They speed up furnaces, brewing and growing plants"],
            "weak": ["Slow natural regeneration", "Potions last shorter", "Without using sand, a numbness builds up"],
            "abilities": [
                ("Rewind", "Return to your place and health of 5 seconds ago.", "A moment that never passed can be lived again."),
                ("Frozen Dome", "Enemies and projectiles inside slow almost to a stop.", "Inside the dome time breathes slowly, like a sleeper."),
                ("Haste", "The block you look at works ten times faster.", "Sand falls faster when nudged."),
            ],
        },
        "bloodkin": {
            "name": "Bloodkin", "tagline": "Blood for a long life",
            "lore": [
                "They offered the mage their blood for a long life. He took it and never gave it back.",
                "Since then the Bloodkin live on the blood of others and hear the living through walls by their heartbeat.",
            ],
            "perk": ["Lifesteal: part of the damage you deal heals you", "They see the heartbeat of the living through walls", "Blood rituals and the blood altar"],
            "weak": ["They burn in the sun without a hood", "Ordinary food barely feeds them", "Villagers fear them"],
            "abilities": [
                ("Blood Dash", "Dash through an enemy, leaving it bleeding.", "Blood knows its own way to another's heart."),
                ("Harvest", "Draw blood from every enemy nearby and heal.", "Everyone who stands near pays the mage's debt."),
                ("Blood Sacrifice", "Give your own health for a rush of blood.", "Sometimes, to take, you must first give."),
            ],
        },
        "voidborn": {
            "name": "Voidborn", "tagline": "Blinks and rifts",
            "lore": [
                "The mage led them through the first obelisk. They did not return whole: part of each of them is still in the void.",
                "Endermen take them for their own, and obelisks answer them more strongly than anyone else.",
            ],
            "perk": ["Blink 8 blocks along your gaze", "Rift: a portal the whole team can use", "An ender chest always at hand", "Ender obelisks reach further and recharge faster for them"],
            "weak": ["Water burns", "Rain weakens"],
            "abilities": [
                ("Blink", "Instantly move 8 blocks along your gaze.", "The void is always near; you only have to step."),
                ("Rift", "A pair of portals for 10 seconds that anyone may use.", "The world is thin if you know where to tear it."),
                ("Banish", "Throw an enemy far up into the air.", "The void takes what it is given."),
            ],
        },
    },
    "branches": [
        ("Swarm", "Spores that choke enemies and heal friends."),
        ("Network", "Mycelium beneath the whole world: jumps, roots and nodes."),
        ("Flesh", "A body that feeds on mushrooms and darkness."),
    ],
    "talents": {
        "swarm_wide": ("Wide Cloud", "Spores know no borders, only the wind.", "Spore Cloud radius 6 blocks instead of 4."),
        "swarm_trail": ("Toxic Trail", "Where you jumped from, a gift remains.", "Spore Jump leaves a small spore cloud at the start for 3 seconds."),
        "swarm_potent": ("Caustic Spores", "The older the mycelium, the bitterer its breath.", "The cloud poisons harder (Poison II) and slows monsters."),
        "swarm_infection": ("Infection", "Everything dead becomes soil.", "A monster dying in your cloud gives 10 spores and race experience."),
        "swarm_lingering": ("Long Bloom", "Spores settle and are in no hurry to leave.", "The cloud lasts 12 seconds instead of 6."),
        "net_chain": ("Jump Chain", "The mycelium sprouts faster than an enemy can turn.", "After a jump, 2 more jumps within 3 seconds cost 5 spores each and have no cooldown."),
        "net_reach": ("Long Throw", "A spore flies further if let go at the right moment.", "Spore Jump reaches 12 blocks instead of 8."),
        "net_nodes": ("Spreading Network", "The more nodes, the more the world feels like home.", "Up to 5 mycelium nodes instead of 3."),
        "net_far": ("Deep Roots", "Underground, distances are shorter.", "Nodes link across 500 blocks instead of 150."),
        "net_roots": ("Grip of Roots", "Where you sprout, the earth holds strangers.", "After a jump, monsters within 3 blocks can barely move for 2 seconds."),
        "flesh_two_tastes": ("Two Tastes", "A Mycelid's stomach is wider than it looks.", "Two mushroom tastes work at once instead of one."),
        "flesh_rich": ("Rich Taste", "A Mycelid knows which mushroom to pick and which to leave to ripen.", "Tastes are one level stronger and last 2 minutes instead of 1."),
        "flesh_dark_regen": ("Night Flesh", "In the dark, wounds close like cracks in bark.", "Regeneration II instead of I in the dark and on mycelium."),
        "flesh_cap": ("Cap", "The skin hardens like the cap of an old mushroom.", "+4 armour while you are out of the sun."),
        "flesh_sunproof": ("Shade Within", "You carry the dark with you, and the sun has no power over you.", "The sun no longer causes weakness and hunger."),
    },
}

for name, table in (("ru_ru", RU), ("en_us", EN)):
    (OUT / f"{name}.json").write_text(json.dumps(build(table), ensure_ascii=False, indent="\t") + "\n", encoding="utf-8")
print("ok")
