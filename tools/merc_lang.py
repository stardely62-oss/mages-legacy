#!/usr/bin/env python3
"""The Mercenaries' texts (abilities, talents, the shop, the book), Russian and English side by side, written into both lang
files. Run after changing a text: python3 tools/merc_lang.py. The jobs' own lines are in tools/merc_jobs.py."""
import json
import pathlib
from collections import OrderedDict

root = pathlib.Path(__file__).resolve().parent.parent
P = "mages_legacy."
L = OrderedDict()


def k(key, ru, en):
    L[P + key] = (ru, en)


# ---- the race on the choice screen ----
k("race.mercenary.perk.1", "Клеймо охотника: снаряд вешает на врага печать из трёх частей; три ваших удара по нему — и печать взрывается",
  "Hunter's Brand: a bolt hangs a three-part seal on a foe; three of your blows on it and the seal bursts")
k("race.mercenary.perk.2", "Гильдейский гарпун на 26 блоков: лёгкого врага тянет к вам, к тяжёлому, боссу или стене тянет вас",
  "Guild Harpoon, 26 blocks: a light foe is dragged to you; you are drawn to a heavy one, a boss or a wall")
k("race.mercenary.perk.3", "Лавка мага: заказы от гильдии, жетоны за них, товары и услуги разной редкости — с уровнем чаще редкие",
  "The Mage's Shop: jobs from the guild, tokens for them, wares and services of every rarity — rarer ones as you grow")
k("race.mercenary.perk.4", "Маны нет: способности ждут только перезарядку и работают без всякого заказа",
  "No mana: the abilities wait only on their cooldowns and work with no job at all")
k("race.mercenary.perk.5", "Врождённо: +10% урона по целям своих охотничьих заказов и тихий звон с их стороны в 12 блоках",
  "Innate: +10% damage to the quarry of your hunts, and a faint chime from its side within 12 blocks")
k("race.mercenary.weak.1", "Опыт расы — в основном за заказы: случайные убийства учат вчетверо меньше (до 15 опыта за 10 минут)",
  "Race experience comes mostly from jobs: random kills teach a quarter as much (15 per 10 minutes)")
k("race.mercenary.weak.2", "Элитные заказы, скидки, лучшие награды и лишние места под заказы открываются только талантами",
  "Elite jobs, discounts, better pay and room for more jobs open only through talents")
k("race.mercenary.weak.3", "По игрокам слабее: взрыв Клейма — не больше 4 урона, клинки и гарпун — по 2",
  "Weaker against players: a Brand bursts for 4 at most, blades and the harpoon hit for 2")
k("race.mercenary.weak.4", "Последний выкуп стоит 30 жетонов: без накоплений он не спасёт", "The Last Ransom costs 30 tokens: without savings it saves no one")

# ---- the abilities ----
k("ability.hunter_brand.desc",
  "G: медно-золотой снаряд летит на 20 блоков (сквозь стены — нет) и бьёт первого врага на 4 урона. Над врагом появляется пустое кольцо из трёх частей — "
  "Клеймо на 10 секунд. Каждый ваш удар по нему (оружием, своей стрелой или Гарпуном) закрашивает одну часть (удар, пока снаряд ещё летит, тоже считается); сам снаряд ударом не считается. "
  "С талантом «Сложный процент» частей четыре. "
  "Удар, закрывший последнюю часть, взрывает Клеймо: ещё 6 урона (по игроку не больше 4), во все стороны летят монеты, а вы получаете 2 поглощения на 4 секунды. "
  "Урон самой печати, взрыва, клинков и знамени частей не зажигает. Союзников и питомцев не метит. Перезарядка 8 секунд.",
  "G: a copper-and-gold bolt flies 20 blocks (never through walls) and hits the first foe for 4. An empty three-part ring appears over it — the Brand, "
  "for 10 seconds. Each of your blows on it (a weapon, your own arrow or the Harpoon) fills one part (a blow while the bolt is still flying counts too); the bolt itself is no blow. "
  "With Compound Interest it has four parts. "
  "The blow that fills the last part bursts the Brand: 6 more damage (4 at most to a player), coins flying every way, and 2 absorption for you for 4 seconds. "
  "Damage of the seal itself, its burst, blades and the banner lights nothing. Never brands allies or pets. Cooldown 8 seconds.")
k("ability.guild_harpoon.desc",
  "V: гарпун на золотой цепи летит на 26 блоков. Попал в лёгкое существо — 2 урона, и его подтягивает к вам. В тяжёлое, босса, неподвижное "
  "или игрока — 2 урона, и вас подтягивает к нему. В блок — вы летите к точке зацепа. Стены гарпун не пробивает; рывок длится до 24 тиков "
  "и останавливается о препятствие, секунду после него нет урона от падения. Попадание по цели с Клеймом зажигает часть печати. Если "
  "зацепиться не за что, бросок не тратится. Перезарядка 10 секунд.",
  "V: a harpoon on a golden chain flies 26 blocks. A light creature hit takes 2 and is dragged to you. A heavy one, a boss, a fixed one or a "
  "player takes 2 and you are drawn to it. A block — you fly to where it bit. It never goes through walls; the pull lasts up to 24 ticks and "
  "stops against anything in the way, with no fall damage for a second after. A hit on a branded foe lights a part of its seal. Nothing to "
  "bite — nothing spent. Cooldown 10 seconds.")
k("ability.mages_shop.desc",
  "H: вы входите в Лавку мага. На полках — товары дня (по 1–2 из каждой категории и ещё несколько любых), на доске — услуги; у всего своя "
  "редкость, и чем выше уровень расы, тем чаще редкие. На прилавке лежит книга заказов: в ней предложения гильдии и ваши взятые заказы. "
  "Выполненный заказ сразу даёт новое предложение; всё обновляется каждые 20 минут игры (сон не ускоряет).",
  "H: you step into the Mage's Shop. Today's goods stand on the shelves (one or two of every kind and a few of any), services are pinned to "
  "the board; each has a rarity, and the higher your race level, the more often rare ones turn up. The book of orders lies on the counter: "
  "the guild's offers and the jobs you hold. A job done brings a fresh offer at once; everything turns over every 20 minutes of play.")
k("ability.guild_verdict.desc",
  "Пробуждение: враг в 20 блоках осуждён на 15 секунд — его сковывают цепи, над ним висит меч палача. Каждые два ваших удара по нему "
  "в цель вонзается меч — 3 урона (по игроку 2), до 5 мечей за Приговор; меч палача растёт с каждым ударом. Шестой удар вызывает "
  "исполнение: меч палача падает — 8 урона (по игроку 4), один раз. "
  "Если осуждённый погибнет раньше, Приговор кончается. Перезарядка 2 минуты.",
  "Awakening: a foe within 20 blocks is condemned for 15 seconds — chains bind it, the executioner's sword hangs over it. Every two of your "
  "blows drive a sword in — 3 damage (2 to a player), 5 swords at most a Verdict; the executioner's sword grows with every blow. The sixth "
  "blow calls the execution: it falls — 8 damage (4 to a player), once. "
  "If the condemned dies first, the Verdict ends. Cooldown 2 minutes.")
k("ability.life_debt.desc",
  "Пробуждение «Выкуп». Долг жизни на 10 секунд: 40% урона, который до вас дошёл, не наносится сразу, а записывается в Долг (до 12). "
  "Ваши удары по врагам гасят Долг — 20% нанесённого урона (по цели с Клеймом 30%). Погасили до конца — +2 здоровья и золотая волна: "
  "4 урона врагам в 4 блоках. Не погасили — половина остатка бьёт по вам разом (может убить). Shift + клавиша, глядя на союзника в 12 "
  "блоках, — договор на него. Перезарядка 60 секунд. Плюс сам по себе работает Последний выкуп: смертельный удар отменяется за 30 жетонов, "
  "вы остаётесь с 40% здоровья, раз в 20 минут; тотем в руке срабатывает первым.",
  "Ransom Awakening. Life Debt for 10 seconds: 40% of the damage that reaches you isn't taken at once but written into a Debt (12 at most). "
  "Your blows on foes pay it down — 20% of the damage dealt (30% on a branded foe). Paid off: +2 health and a golden wave, 4 damage to foes "
  "within 4 blocks. Not paid: half of what is left strikes you at once (it can kill). Shift + the key, looking at an ally within 12 blocks, "
  "signs it for them. Cooldown 60 seconds. The Last Ransom works by itself: a killing blow is undone for 30 tokens, you keep 40% health, "
  "once in 20 minutes; a totem in hand acts first.")
k("ability.guild_legend.desc",
  "Пробуждение: вы ставите знамя гильдии на 15 секунд, круг радиусом 10 блоков. Вы и союзники в круге наносите на 15% больше урона и "
  "получаете на 10% меньше. Каждые 2 секунды знамя бьёт спектральными клинками до 3 врагов в круге по 3 урона — сперва тех, на ком Клеймо. "
  "Каждый враг, убитый вами в круге, продлевает знамя на 2 секунды, всего не больше чем на 6. Знамёна разных Наёмников не складываются. "
  "Перезарядка 2 минуты.",
  "Awakening: you plant the guild's banner for 15 seconds, a circle of 10 blocks. You and your allies inside deal 15% more damage and take "
  "10% less. Every 2 seconds it cuts up to 3 foes inside with spectral blades for 3 — branded ones first. Every foe you kill inside lengthens "
  "it by 2 seconds, 6 at most. Banners of several Mercenaries don't add up. Cooldown 2 minutes.")

# ---- the tree: what each node does, plainly ----
T = {
    "mages_shop": (
        "Ядро ветки. Клавиша H открывает Лавку мага, где бы вы ни были. На полках — товары дня: по одному-два из каждой категории "
        "(припасы, материалы, зелья, книги, снаряжение) и несколько случайных. На доске — услуги гильдии (временные усиления). "
        "На прилавке — книга заказов: предложения гильдии и ваши взятые заказы. Платите жетонами, которые приносят заказы. "
        "Чем выше уровень расы, тем чаще попадаются редкие и сильные вещи.",
        "The core of the branch. H opens the Mage's Shop wherever you are. Today's wares stand on the shelves: one or two of every "
        "kind (supplies, materials, potions, books, gear) and a few picked at random. Guild services (timed boons) hang on the board. "
        "The book of orders lies on the counter: the guild's offers and the jobs you hold. You pay in tokens, which jobs bring. "
        "The higher your race level, the more often rare and strong things turn up."),
    "hunter_brand": (
        "Способность G (с 3 уровня), перезарядка 8 с. Вы бросаете золотую монету-снаряд на 20 блоков: первый враг на пути получает "
        "4 урона, а над ним появляется печать из трёх частей — Клеймо на 10 секунд. Каждый ваш удар по нему (оружием, своей стрелой "
        "или Гарпуном) зажигает одну часть; удар, нанесённый пока монета ещё летит, тоже засчитывается. Третий удар взрывает печать: "
        "ещё 6 урона (по игроку не больше 4), монеты во все стороны и 2 поглощения вам на 4 секунды. Ровно три удара — без лишних.",
        "Ability G (from level 3), cooldown 8 s. You throw a golden coin-bolt up to 20 blocks: the first foe in its way takes 4 and gets a "
        "three-part seal over its head — the Brand, for 10 seconds. Each of your blows on it (a weapon, your own arrow or the Harpoon) "
        "lights one part; a blow landed while the coin is still in the air counts too. The third blow bursts the seal: 6 more damage "
        "(4 at most to a player), coins flying every way and 2 absorption for you for 4 seconds. Exactly three blows — never more."),
    "guild_harpoon": (
        "Способность V (с 5 уровня), перезарядка 10 с. Гарпун на золотой цепи летит на 26 блоков. Лёгкое существо получает 2 урона "
        "и подтягивается к вам; к тяжёлому, боссу, игроку или стене подтягивает вас самих. Рывок до 24 тиков, после него секунду нет "
        "урона от падения. Попадание по цели с Клеймом зажигает часть печати. Если зацепиться не за что, перезарядка не тратится. "
        "Хорош и чтобы вытащить стрелка к себе, и чтобы самому сократить дистанцию или выбраться на высоту.",
        "Ability V (from level 5), cooldown 10 s. A harpoon on a golden chain flies 26 blocks. A light creature takes 2 and is dragged to "
        "you; a heavy one, a boss, a player or a wall draws you to it instead. The pull lasts up to 24 ticks, with no fall damage for a "
        "second after. A hit on a branded foe lights a part of its seal. Nothing to bite — no cooldown spent. Good both for pulling an "
        "archer to you and for closing in yourself or climbing to a ledge."),
    "con_board": (
        "В книге заказов 4 предложения вместо 3. Больше выбор — легче найти заказ, который удобно выполнить прямо сейчас (охоту рядом, "
        "доставку того, что уже лежит в сундуке), и реже приходится платить за обновление доски.",
        "The book of orders offers 4 jobs instead of 3. More choice makes it easier to find one you can do right now (a hunt nearby, a "
        "delivery of something already in your chest) and you pay for a fresh board less often."),
    "con_hands": (
        "Можно держать 3 заказа одновременно вместо 2. Берите охоту, доставку и путешествие сразу: они выполняются параллельно, а "
        "быстрые бонусы (за выполнение в срок) идут по каждому отдельно.",
        "You can hold 3 jobs at once instead of 2. Take a hunt, a delivery and a journey together: they run side by side, and the quick "
        "bonus (for finishing in time) counts for each on its own."),
    "con_elite": (
        "В книге начинают появляться элитные заказы (не больше одного за раз, помечены отдельно). Работы вдвое больше — вдвое больше "
        "врагов, предметов или пути, — но платят в 2,5 раза больше жетонов. Выгоднее обычных, если есть время на долгую вылазку.",
        "Elite jobs start turning up in the book (one at a time, marked apart). Twice the work — twice the foes, items or road — but two "
        "and a half times the tokens. Better than plain jobs when you have time for a long outing."),
    "con_bargain": (
        "Всё в Лавке на 15% дешевле: товары, услуги гильдии и платное обновление доски заказов. Скидка складывается со скидкой дня, так "
        "что редкие вещи становятся заметно доступнее.",
        "Everything in the Shop costs 15% less: wares, guild services and a paid fresh board. It stacks with the deal of the day, so rare "
        "things get noticeably cheaper."),
    "con_scout": (
        "Цели ваших охотничьих заказов в 32 блоках подсвечиваются контуром — их видно сквозь стены и в темноте. Больше не нужно "
        "бродить по пещере в поисках нужного моба: сразу видно, куда идти.",
        "The quarry of your hunts within 32 blocks glows with an outline — seen through walls and in the dark. No more wandering a cave "
        "looking for the right mob: you see at once where to go."),
    "con_tracker": (
        "По целям охотничьих заказов вы наносите на 20% больше урона вместо врождённых 10%. Бонус работает для любых ваших ударов — "
        "оружием и снарядами — и складывается с другими бонусами Наёмника.",
        "You deal 20% more damage to the quarry of your hunts instead of the innate 10%. It works for all your blows — weapons and "
        "projectiles — and stacks with the Mercenary's other bonuses."),
    "con_regular": (
        "На полках Лавки на 2 товара больше, а скидка дня (−30%) действует сразу на два товара вместо одного. "
        "Больше шансов увидеть редкую вещь в каждом обновлении.",
        "Two more wares on the Shop's shelves, and the deal of the day (−30%) is on two wares instead of one. More "
        "chances to see a rare item in every turnover."),
    "con_partners": (
        "Убийства, совершённые союзниками и вашими питомцами (например, волками) в 24 блоках от вас, засчитываются в ваши "
        "охотничьи заказы, как будто убили вы. Удобно в команде и с прирученными зверями.",
        "Kills made by your allies and your pets (wolves, say) within 24 blocks of you count towards your hunts as if "
        "you had made them. Handy in a team or with tamed beasts."),
    "con_bonus": (
        "Каждый выполненный заказ платит на 25% больше жетонов. Работает для всех видов заказов и складывается с бонусом за быстроту "
        "и элитной оплатой.",
        "Every job done pays 25% more tokens. Works for every kind of job and stacks with the quick bonus and elite pay."),
    "con_renown": (
        "Каждый выполненный заказ даёт на 20% больше опыта расы. Уровни растут быстрее — значит, быстрее открываются таланты, "
        "Пробуждения и редкие товары в Лавке.",
        "Every job done gives 20% more race experience. Levels come faster, and with them talents, Awakenings and rarer wares in the Shop."),
    "con_guide": (
        "Когда вы берёте заказ «дойти до края» (биом или место), книга сразу показывает расстояние и сторону света до ближайшего "
        "подходящего места. Не нужно искать по карте — просто идите по направлению.",
        "When you take a job to reach a land (a biome or a place), the book shows at once the distance and the direction to the nearest "
        "such place. No map hunting — just head that way."),
    "con_connections": (
        "Первое обновление доски заказов за игровой день бесплатно, а все следующие стоят вдвое дешевле. Можно смело "
        "перебирать предложения, пока не попадётся удобный заказ.",
        "The first fresh board of a game day is free, and every one after it costs half. Feel free to reroll "
        "the offers until a handy job turns up."),
    "con_streak": (
        "Если вы сдаёте заказ не позже чем через 20 минут после прошлого, он платит больше — +10% за каждый заказ в "
        "серии, до +30%. Пропустили 20 минут — серия начинается заново.",
        "Hand one in within 20 minutes of the last and it pays more — +10% for each job in the streak, up to +30%. "
        "Let 20 minutes pass and the streak starts over."),
    "awakening_ransom": (
        "Пробуждение «Выкуп» (с 30 уровня, после испытания). Долг жизни: 10 секунд 40% урона по вам не наносится сразу, а "
        "записывается в Долг (до 12) — на груди висит цепь, и на ней прибавляется монета за каждые 2 единицы. Ваши удары по врагам "
        "гасят Долг. Погасили — +2 здоровья и золотая волна; не успели — половина остатка бьёт разом. Shift — договор на союзника. "
        "Последний выкуп: смертельный удар отменяется за 30 жетонов (раз в 20 минут).",
        "The Ransom Awakening (from level 30, after its trial). Life Debt: for 10 seconds 40% of the damage on you isn't taken at once but "
        "written into a Debt (12 at most) — a chain hangs round your chest, and a coin is added to it for every 2 owed. Your blows on foes "
        "pay it down. Paid off — +2 health and a golden wave; too slow — half of what is left strikes at once. Shift signs it for an ally. "
        "The Last Ransom: a killing blow is undone for 30 tokens (once in 20 minutes)."),
    "ransom_write_off": (
        "Если Долг жизни кончается непогашенным, сначала четверть остатка прощается, и только потом половина оставшегося "
        "бьёт по подопечному. Например, при Долге 8 расплата будет 3 вместо 4. Страховка на случай, когда бой затянулся.",
        "When a Life Debt ends unpaid, a quarter of what is left is forgiven first, and only then half the rest strikes the "
        "ward. A Debt of 8, say, costs 3 instead of 4. Insurance for when a fight drags on."),
    "ransom_guarantor": (
        "Если вы подписали Долг жизни на союзника, каждый ваш удар по врагам гасит его Долг на 30% нанесённого урона "
        "вместо 20%. Вы прикрываете товарища и быстрее снимаете с него долг.",
        "With a Life Debt signed for an ally, each of your blows on foes pays their Debt down by 30% of the damage dealt "
        "instead of 20%. You cover your comrade and clear their debt faster."),
    "ransom_gold": (
        "Золотая волна от погашенного Долга бьёт врагов в 4 блоках на 6 урона вместо 4. Погасив Долг посреди толпы, вы заметно "
        "раните всех вокруг.",
        "The golden wave of a paid-off Debt hits foes within 4 blocks for 6 instead of 4. Pay off a Debt in the middle of a crowd and "
        "everyone around takes a real wound."),
    "ransom_second": (
        "После Последнего выкупа вы 3 секунды под Сопротивлением I (−20% урона). Этого хватает, чтобы отскочить или "
        "выпить зелье, а не умереть от следующего же удара.",
        "After the Last Ransom you have Resistance I (−20% damage) for 3 seconds. Enough to back off or drink a potion "
        "instead of dying to the very next blow."),
    "brand_far": (
        "Монета Клейма летит на 28 блоков вместо 20. Можно пометить лучника или убегающую цель издалека и добить её уже вблизи.",
        "The Brand's coin flies 28 blocks instead of 20. Mark an archer or a fleeing foe from afar and finish it up close."),
    "brand_collect": (
        "Если взрыв Клейма добил цель, перезарядка Клейма сразу сбрасывается — можно тут же пометить следующего врага. "
        "В толпе слабых мобов позволяет взрывать печати одну за другой.",
        "If a Brand's burst kills its bearer, the Brand's cooldown resets at once — brand the next foe straight away. "
        "In a crowd of weak mobs it lets you burst seal after seal."),
    "brand_compound": (
        "Печать из четырёх частей вместо трёх (над врагом кольцо делится на четверти). Взрывается на четвёртом ударе, зато наносит 10 урона вместо 6 (по "
        "игроку всё так же не больше 4). Выгодно против крепких врагов и боссов.",
        "A four-part seal instead of three (the ring over the foe is cut in quarters). It bursts on the fourth blow, but for 10 instead of 6 (still 4 at most to "
        "a player). Worth it against tough foes and bosses."),
    "brand_linger": (
        "Клеймо держится 15 секунд вместо 10. Хватает времени на три удара даже медленным оружием, луком или если цель убегает.",
        "The Brand holds 15 seconds instead of 10. Time enough for three blows even with a slow weapon, a bow or a foe that runs."),
    "brand_spread": (
        "Взрыв перекидывает новое Клеймо (с одной уже зажжённой частью) на ближайшего врага в 6 блоках. До его взрыва "
        "остаётся два удара. Перекинутое Клеймо дальше уже не перекидывается.",
        "A burst passes a new Brand (one part already lit) to the nearest foe within 6 blocks — two blows from bursting. A "
        "passed Brand passes no further."),
    "brand_pledge": (
        "Взрыв Клейма даёт 4 поглощения вместо 2 (всего от взрывов не больше 6) на 4 секунды. Чем чаще взрываете — тем "
        "крепче держитесь в ближнем бою.",
        "A Brand's burst gives 4 absorption instead of 2 (6 at most from bursts) for 4 seconds. The more you burst, the "
        "better you hold in close combat."),
    "brand_swap": (
        "Если вы ставите Клеймо на нового врага, пока прошлое ещё держится, уже зажжённые части переходят на новую "
        "печать (кроме последней — сразу она не взорвётся). Переключаясь на опасную цель, вы не теряете прогресс.",
        "Brand a new foe while your last Brand still holds and the parts already lit move to the new seal (all but the "
        "last — it won't burst at once). Switching to a dangerous foe loses nothing."),
    "brand_pierce": (
        "Монета Клейма проходит сквозь первую цель и метит ещё и вторую позади неё. Оба Клейма независимы — "
        "взрывайте их по очереди.",
        "The Brand's coin passes through its first mark and brands a second foe behind it as well. Both Brands are "
        "separate — burst them in turn."),
    "brand_finisher": (
        "Взрыв Клейма по цели, у которой осталось меньше 30% здоровья, наносит в полтора раза больше (9 вместо 6). "
        "Ставьте Клеймо на раненого врага, чтобы закончить бой.",
        "A burst on a foe under 30% health deals half again as much (9 instead of 6). Brand a wounded foe to end the fight."),
    "brand_chain": (
        "Взрыв Клейма задевает и врагов в 3 блоках вокруг цели — каждый получает половину урона взрыва. Хорош против "
        "групп, особенно вместе со Сложным процентом.",
        "A Brand's burst also strikes foes within 3 blocks of its bearer — half the burst's damage each. Good against "
        "groups, above all with Compound Interest."),
    "awakening_verdict": (
        "Пробуждение «Приговор» (с 30 уровня, после испытания). Враг в 20 блоках осуждён на 15 секунд: его сковывают цепи, а над ним "
        "висит меч палача, растущий с каждым вашим ударом. Каждые два удара в цель вонзается меч — 3 урона (по игроку 2), не больше "
        "5 мечей за Приговор. Шестой удар — исполнение: меч палача падает сверху, 8 урона (по игроку 4). Перезарядка 2 минуты.",
        "The Verdict Awakening (from level 30, after its trial). A foe within 20 blocks is condemned for 15 seconds: chains bind it and the "
        "executioner's sword hangs over it, growing with each of your blows. Every two blows drive a sword into it — 3 damage (2 to a "
        "player), 5 swords at most a Verdict. The sixth blow is the execution: the great sword falls, 8 damage (4 to a player). "
        "Cooldown 2 minutes."),
    "verdict_long": (
        "Приговор длится 20 секунд вместо 15. Хватает времени на все шесть ударов и исполнение даже против увёртливой цели или "
        "если приходится отвлекаться на других врагов.",
        "The Verdict lasts 20 seconds instead of 15. Time enough for all six blows and the execution even against a slippery foe or when "
        "other foes pull you away."),
    "verdict_boss": (
        "Мечи Приговора и исполнение бьют боссов (Иссушитель, Эндер-дракон) на 50% сильнее — 4,5 за меч "
        "и 12 за исполнение.",
        "The Verdict's swords and execution hit bosses (the Wither, the Ender Dragon) 50% harder — "
        "4.5 a sword and 12 for the execution."),
    "verdict_blades": (
        "За один Приговор в цель может вонзиться до 7 мечей вместо 5. Мечи продолжают прилетать каждые два удара и "
        "после исполнения, пока Приговор не кончится.",
        "Up to 7 swords may be driven in during one Verdict instead of 5. Swords keep coming every two blows even after the "
        "execution, until the Verdict ends."),
    "verdict_refund": (
        "Если осуждённый погиб от вашей руки, перезарядка Приговора сокращается на 40 секунд (раз за Приговор). "
        "Быстро казнили — скоро снова судите.",
        "If the condemned dies by your hand, the Verdict's cooldown is 40 seconds shorter (once a Verdict). Execute fast "
        "and you judge again soon."),
    "verdict_triumph": (
        "После исполнения вы получаете Силу I и Скорость I на 6 секунд. Используйте их, чтобы добить осуждённого или сразу "
        "переключиться на следующего врага.",
        "After the execution you get Strength I and Speed I for 6 seconds. Use them to finish the condemned or turn straight to "
        "the next foe."),
    "harp_quick": (
        "Перезарядка Гарпуна 7 секунд вместо 10. Чаще подтягиваете врагов и чаще перемещаетесь сами — особенно полезно в пещерах "
        "и на неровной местности.",
        "The Harpoon's cooldown is 7 seconds instead of 10. You drag foes and move yourself more often — handy above all in caves and "
        "rough ground."),
    "harp_long": (
        "Гарпун летит на 34 блока вместо 26. Можно зацепить врага или уступ, до которых раньше было не дотянуться.",
        "The Harpoon reaches 34 blocks instead of 26. Catch a foe or a ledge that was out of reach before."),
    "harp_double": (
        "После броска у вас есть 3 секунды на второй бросок без перезарядки (один раз). Например, подтянуть врага, "
        "а затем самому прыгнуть к стене.",
        "After a throw you have 3 seconds for a second one without the cooldown (once). Pull a foe in, say, then leap "
        "to a wall yourself."),
    "harp_stun": (
        "Попадание бьёт на 4 вместо 2, а притянутое существо 1,5 секунды почти не может двигаться (Медлительность "
        "III). Время на два-три удара по Клейму, пока цель не опомнилась.",
        "A hit deals 4 instead of 2, and a creature dragged in can hardly move for 1.5 seconds (Slowness III). Time "
        "for two or three blows on its Brand before it recovers."),
    "harp_precise": (
        "Первый удар оружием в течение 3 секунд после попадания Гарпуном наносит на 40% больше. Подтянули — и сразу "
        "сильный удар.",
        "The first weapon blow within 3 seconds of a Harpoon hit deals 40% more. Pull it in — and strike hard at once."),
    "craft_cuirass": (
        "У вас всегда +2 брони и +1 к твёрдости брони, в любом снаряжении. Твёрдость особенно помогает против "
        "сильных одиночных ударов.",
        "Always +2 armour and +1 armour toughness, whatever you wear. Toughness helps above all against heavy "
        "single blows."),
    "craft_bow": (
        "Ваши стрелы, трезубцы и другие снаряды наносят на 15% больше. Стрела тоже зажигает часть Клейма, так что "
        "печать можно взорвать издалека.",
        "Your arrows, tridents and other projectiles deal 15% more. An arrow lights a part of a Brand too, so a seal "
        "can be burst from afar."),
    "craft_second_wind": (
        "Когда здоровье падает ниже 30%, вы получаете Скорость II и Регенерацию I на 4 секунды. Не чаще раза в "
        "минуту. Шанс выйти из боя или переломить его.",
        "When your health drops under 30%, you get Speed II and Regeneration I for 4 seconds. Once a minute at most. A "
        "chance to leave the fight or turn it."),
    "craft_blade": (
        "Удары оружием ближнего боя наносят на 12% больше. Работает с любым оружием и складывается с бонусом по "
        "целям заказов.",
        "Melee weapon blows deal 12% more. Works with any weapon and stacks with the bonus on the quarry of "
        "your hunts."),
    "craft_lifeline": (
        "Гарпуном можно зацепить союзника — вас подтягивает к нему, и вы оба получаете 4 поглощения на 6 секунд. "
        "Быстро прийти на помощь или выбраться из окружения к своим.",
        "The Harpoon can catch an ally — you are drawn to them and you both get 4 absorption for 6 seconds. Rush to help, or "
        "break out of a ring of foes to your own."),
    "craft_shoulder": (
        "Когда ваше Клеймо взрывается, союзники в 8 блоках получают Сопротивление I на 3 секунды. Каждый взрыв "
        "немного прикрывает всю команду.",
        "When your Brand bursts, allies within 8 blocks get Resistance I for 3 seconds. Each burst shields the "
        "whole team a little."),
    "awakening_legend": (
        "Пробуждение «Легенда гильдии» (с 30 уровня, после испытания). Знамя падает с неба и встаёт там, где вы стоите, на 15 секунд; "
        "круг радиусом 10 блоков отмечают воткнутые мечи. Вы и союзники в круге наносите на 15% больше урона и получаете на 10% "
        "меньше. Каждые 2 секунды знамя метает клинки в 3 врагов в круге (по 3 урона), сперва в тех, на ком Клеймо. Ваши убийства в "
        "круге продлевают знамя на 2 секунды, всего до 6. Перезарядка 2 минуты.",
        "The Guild Legend Awakening (from level 30, after its trial). The banner drops from the sky and stands where you stand for 15 "
        "seconds; swords driven into the ground mark its circle of 10 blocks. You and your allies inside deal 15% more and take 10% less. "
        "Every 2 seconds it hurls blades at 3 foes inside (3 each), branded ones first. Your kills inside lengthen it by 2 seconds, up to 6. "
        "Cooldown 2 minutes."),
    "legend_wide": (
        "Круг знамени радиусом 13 блоков вместо 10. Удобнее держать в нём всю команду и доставать клинками врагов-стрелков.",
        "The banner's circle reaches 13 blocks instead of 10. Easier to keep the whole team inside and to reach archers with its blades."),
    "legend_ward": (
        "Под знаменем вы и союзники получаете на 15% меньше урона вместо 10%. Хорошо держать оборону у знамени.",
        "Under the banner you and your allies take 15% less damage instead of 10%. Good for holding your ground by it."),
    "legend_blades": (
        "Клинки знамени бьют до 4 врагов за раз вместо 3. Против толпы знамя наносит заметно больше урона.",
        "The banner's blades strike up to 4 foes at a time instead of 3. Against a crowd it deals noticeably more."),
    "legend_long": (
        "Знамя стоит 18 секунд вместо 15. Убийства в круге по-прежнему продлевают его, но всего не больше чем на 6 секунд.",
        "The banner stands 18 seconds instead of 15. Kills inside still lengthen it, by 6 seconds at most in all."),
    "cross_contract_kill": (
        "Перекрёсток веток. Взрыв Клейма по цели вашего охотничьего заказа наносит в полтора раза больше (9 вместо 6). Заказы на "
        "охоту выполняются быстрее.",
        "A crossing of branches. A Brand bursting on the quarry of your hunt deals half again as much (9 instead of 6). Hunts go faster."),
    "cross_tension": (
        "Перекрёсток веток. Попадание Гарпуном по врагу с Клеймом продлевает Клеймо на полный срок (10 или 15 секунд). Можно спокойно "
        "добирать удары, не боясь, что печать погаснет.",
        "A crossing of branches. A Harpoon hit on a branded foe renews its Brand's full time (10 or 15 seconds). Take your time with the "
        "blows without fearing the seal will fade."),
    "cross_guild_grab": (
        "Перекрёсток веток. Попадание Гарпуном по цели охотничьего заказа сокращает перезарядку Клейма на 4 секунды. Подтянули "
        "заказанную цель — и почти сразу метите её снова.",
        "A crossing of branches. A Harpoon hit on the quarry of a hunt cuts the Brand's cooldown by 4 seconds. Pull in a wanted foe — and "
        "brand it again almost at once."),
    "cross_trophy": (
        "Перекрёсток веток. Цель охотничьего заказа с вашим Клеймом, убитая вами, засчитывается в заказ за две. Ставьте Клеймо перед "
        "добиванием — заказ закроется вдвое быстрее.",
        "A crossing of branches. A branded quarry you kill counts twice towards its hunt. Brand before the killing blow and the job "
        "finishes twice as fast."),
    "cross_rhythm": (
        "Перекрёсток веток. Каждый взрыв Клейма сокращает перезарядку Гарпуна на 3 секунды. Взорвали — подтянули следующего — "
        "пометили — взорвали: непрерывный ритм боя.",
        "A crossing of branches. Each Brand burst cuts the Harpoon's cooldown by 3 seconds. Burst, pull the next one in, brand, burst: "
        "an unbroken rhythm of battle."),
    "cross_silver": (
        "Перекрёсток веток. Выполнив заказ, вы восстанавливаете 6 здоровья, Гарпун сразу готов, а идущий Долг жизни уменьшается на 4. "
        "Сдача заказа посреди боя может его спасти.",
        "A crossing of branches. Finishing a job heals you 6, readies the Harpoon at once and cuts a running Life Debt by 4. Handing in a "
        "job mid-fight can save it."),
}
for tid, (ru, en) in T.items():
    k(f"talent.{tid}.desc", ru, en)

# ---- the trials (the kinds of work now count in groups) ----
k("talent.awakening_ransom.trial", "Испытание: %2$s заказов (сейчас: %1$s), среди них доставка (%3$s/1) и путешествие — край или разведка (%4$s/1); потратить в Лавке %6$s жетонов (сейчас: %5$s). Откроется с 30 уровня расы.",
  "Trial: %2$s jobs (so far: %1$s), a delivery (%3$s/1) and a journey — a land or a scouting (%4$s/1) among them; spend %6$s tokens in the Shop (so far: %5$s). Opens from race level 30.")
k("talent.awakening_legend.trial", "Испытание: %2$s заказов (сейчас: %1$s), среди них охота (%3$s/1), доставка (%4$s/1) и путешествие (%5$s/1); нанести %7$s урона целям охотничьих заказов (сейчас: %6$s). Откроется с 30 уровня расы.",
  "Trial: %2$s jobs (so far: %1$s), a hunt (%3$s/1), a delivery (%4$s/1) and a journey (%5$s/1) among them; deal %7$s damage to the quarry of your hunts (so far: %6$s). Opens from race level 30.")
k("talent.awakening_verdict.trial", "Испытание: взорвать Клеймо на %2$s враждебных существах (сейчас: %1$s) и выполнить %4$s охотничьих заказов (сейчас: %3$s). Откроется с 30 уровня расы.",
  "Trial: burst a Brand on %2$s hostile creatures (so far: %1$s) and finish %4$s hunts (so far: %3$s). Opens from race level 30.")
k("mastery.goal.six_contracts.desc", "Выполнить 6 заказов, среди них охоту, доставку и путешествие (край или разведку)",
  "Finish 6 jobs, a hunt, a delivery and a journey (a land or a scouting) among them")

# ---- one word for a job everywhere: «заказ» (the old «контракт» lines, owned here now) ----
k("xp.mercenary.1", "Выполнить заказ I яруса", "Finish a tier I job")
k("xp.mercenary.2", "Выполнить заказ II яруса", "Finish a tier II job")
k("xp.mercenary.3", "Выполнить заказ III яруса", "Finish a tier III job")
k("xp.mercenary.4", "Выполнить заказ IV яруса (элитный — ×2,5)", "Finish a tier IV job (elite ×2.5)")
k("talent.branch.contracts", "Заказы", "Jobs")
k("talent.cross_contract_kill.name", "Казнь по заказу", "Execution by Order")
k("talent.innate_profit.desc", "Врождённо: +10% урона по целям ваших охотничьих заказов; ближайшая такая цель в 12 блоках слышна тихим звоном.",
  "Innate: +10% damage to the quarry of your hunts; the nearest such quarry within 12 blocks can be heard as a faint chime.")
k("talent.innate_profit.effect.1", "Заказ на голову: по существам, на которых у вас взят охотничий заказ, вы бьёте на 10% сильнее. Талант «Метка заказа» поднимает это до 20% — не до 30%.",
  "A price on its head: you hit creatures your hunts are after 10% harder. The talent Marked for the Job raises it to 20%, not 30%.")
k("advancement.merc_first_contract.desc", "Выполнить заказ из Лавки мага", "Finish a job from the Mage's Shop")
k("advancement.merc_elite.desc", "Выполнить элитный заказ", "Finish an elite job")
k("advancement.merc_veteran.desc", "Выполнить 25 заказов", "Finish 25 jobs")
k("merc.no_slot", "Уже взято заказов: %s — сначала выполните или откажитесь от одного", "You already hold %s jobs: finish or give one up first")
k("merc.book.tab.contracts", "Заказы", "Jobs")
k("merc.book.empty_hand", "Вы не взяли ни одного заказа. Возьмите заказ с доски слева — способности работают и без него.",
  "You hold no job. Take one from the board on the left — the abilities work without one too.")
k("merc.book.abandon", "Отказаться от заказа", "Give up the job")
k("merc.shop.tokens", "Жетоны гильдии: %s", "Guild tokens: %s")

# ---- the guild's armory (race weapons and armour, a room of the shop) ----
k("merc.armory.title", "Оружейная гильдии", "The Guild Armory")
k("merc.armory.short", "Оружейная", "Armory")
k("merc.armory.tip", "Оружие и броня, сделанные для вашей расы. Всегда в наличии, редкие — с уровнем расы.",
  "Weapons and armour made for your race. Always in stock; the rare ones open with your race level.")
k("merc.armory.count", "Для вашей расы: %s", "For your race: %s")
k("merc.armory.none_yet", "Пока пусто: оружие и броню рас ещё куют", "Empty for now: the races' weapons and armour are still being forged")
k("merc.armory.weapons", "Оружие", "Weapons")
k("merc.armory.armor", "Броня", "Armour")
k("merc.armory.empty", "Здесь будут оружие и броня вашей расы. Их ещё куют — загляните после обновления.",
  "Your race's weapons and armour will hang here. They are still being forged — look in after an update.")
k("merc.armory.note", "Цены в жетонах, «Торг» действует. Редкое — с уровнем расы.", "Prices in tokens, Bargain applies. Rare pieces open with race level.")
k("merc.armory.level", "С %s уровня расы", "From race level %s")
k("merc.armory.level_short", "ур. %s", "lv %s")
k("merc.armory.too_low", "Нужен %s уровень расы", "Needs race level %s")
k("merc.armory.sold", "Больше не продаётся вам", "No more of it for you")
k("merc.armory.not_yours", "Это не для вашей расы", "Not for your race")

# ---- the shop ----
k("merc.shop.name", "Лавка мага", "The Mage's Shop")
k("merc.shop.services", "Услуги гильдии", "Guild services")
k("merc.shop.orders", "Книга заказов · %s/%s", "Book of orders · %s/%s")
k("merc.shop.orders_tip", "Открыть книгу: предложения гильдии и ваши заказы", "Open the book: the guild's offers and your jobs")
k("merc.shop.odds", "Шансы:", "Odds:")
k("merc.shop.luck", "Удача: +30%% жетонов ×%s · двойная награда ×%s", "Luck: +30%% tokens ×%s · double pay ×%s")
k("merc.shop.left", "Осталось сегодня: %s из %s", "Left today: %s of %s")
for r, ru, en in [("common", "Случайное обычное зачарование", "A random common enchantment"),
                  ("uncommon", "Случайное зачарование III уровня", "A random level III enchantment"),
                  ("rare", "Случайное сильное зачарование (Острота IV, Удача II…)", "A random strong enchantment (Sharpness IV, Fortune II…)"),
                  ("epic", "Случайное отличное зачарование (Удача III, Острота V…)", "A random excellent enchantment (Fortune III, Sharpness V…)"),
                  ("legendary", "Починка, Шёлковое касание или Бесконечность", "Mending, Silk Touch or Infinity")]:
    k(f"merc.shop.book_tip.{r}", ru, en)
for r, ru, en, rs, es in [("common", "Обычное", "Common", "О", "C"), ("uncommon", "Необычное", "Uncommon", "Н", "U"), ("rare", "Редкое", "Rare", "Р", "R"),
                          ("epic", "Эпическое", "Epic", "Э", "E"), ("legendary", "Легендарное", "Legendary", "Л", "L")]:
    k(f"merc.rarity.{r}", ru, en)
    k(f"merc.rarity.short.{r}", rs, es)
for c, ru, en in [("supplies", "Расходники", "Supplies"), ("materials", "Материалы", "Materials"), ("potions", "Зелья", "Potions"),
                  ("books", "Книги", "Books"), ("gear", "Снаряжение", "Gear"), ("boosts", "Услуги", "Services")]:
    k(f"merc.cat.{c}", ru, en)
S = [
    ("hire_swift", "Лёгкий шаг", "Light Step", "Скорость I на 5 минут.", "Speed I for 5 minutes."),
    ("hire_haste", "Сноровка", "Knack", "Спешка I на 5 минут.", "Haste I for 5 minutes."),
    ("hire_night", "Глаза совы", "Owl's Eyes", "Ночное зрение на 8 минут.", "Night vision for 8 minutes."),
    ("hire_meal", "Горячий обед", "Hot Meal", "Полная сытость и насыщение сразу.", "Full hunger and saturation at once."),
    ("hire_strength", "Наёмная сила", "Hired Strength", "Сила I на 5 минут.", "Strength I for 5 minutes."),
    ("hire_guard", "Наёмная защита", "Hired Guard", "Сопротивление I на 5 минут.", "Resistance I for 5 minutes."),
    ("hire_regen", "Перевязка", "Bandages", "Регенерация I на 1 минуту.", "Regeneration I for 1 minute."),
    ("hire_fireward", "Огнеупорная мазь", "Fireproof Salve", "Огнестойкость на 8 минут.", "Fire resistance for 8 minutes."),
    ("learning", "Наука мага", "The Mage's Learning", "+60 опыта расы.", "+60 race experience."),
    ("hire_strength_2", "Ярость наёмника", "Mercenary's Fury", "Сила II на 3 минуты.", "Strength II for 3 minutes."),
    ("hire_guard_2", "Железная шкура", "Iron Hide", "Сопротивление II на 3 минуты.", "Resistance II for 3 minutes."),
    ("hire_vigor", "Второе сердце", "Second Heart", "Прилив здоровья II (+4 сердца) на 10 минут.", "Health boost II (+4 hearts) for 10 minutes."),
    ("learning_2", "Трактат гильдии", "Guild Treatise", "+150 опыта расы.", "+150 race experience."),
    ("fortune", "Расположение гильдии", "The Guild's Favour", "Следующие 3 заказа платят на 30% больше жетонов.", "Your next 3 jobs pay 30% more tokens."),
    ("aegis", "Эгида", "Aegis", "Поглощение IV и Сопротивление I на 2 минуты.", "Absorption IV and Resistance I for 2 minutes."),
    ("learning_3", "Записи мага", "The Mage's Notes", "+400 опыта расы.", "+400 race experience."),
    ("blessing", "Благословение гильдии", "The Guild's Blessing", "Сила II, Сопротивление II, Регенерация II и Скорость II на 2 минуты.",
     "Strength II, Resistance II, Regeneration II and Speed II for 2 minutes."),
    ("lucky_day", "Удачный день", "Lucky Day", "Следующие 3 заказа платят вдвое больше — и жетонов, и опыта.", "Your next 3 jobs pay double — tokens and experience."),
]
for sid, ru_n, en_n, ru_d, en_d in S:
    k(f"shop.{sid}", ru_n, en_n)
    k(f"shop.{sid}.desc", ru_d, en_d)

# ---- the book of orders and the work ----
k("merc.book.back", "← В лавку", "← To the shop")
k("merc.book.quick_left", "Срочно: ещё %s", "Quick: %s left")
k("merc.book.quick_offer", "Успеете быстро — %s жетонов", "Done quickly — %s tokens")
k("merc.book.quick_tip", "Выполните заказ быстро после того, как взяли его, — получите в полтора раза больше жетонов и на четверть больше опыта.",
  "Finish a job soon after taking it for half again the tokens and a quarter more experience.")
k("merc.book.scout_target", "Разведка: отмеченное место", "Scouting: a marked place")
k("merc.book.bounty_target", "Голова: %s", "Bounty: %s")
k("merc.book.other_world", "Отметка в Верхнем мире", "The mark is in the Overworld")
k("merc.book.guide", "≈ %s бл., %s", "≈ %s blocks, %s")
k("merc.kind.scout", "Разведка", "Scouting")
k("merc.kind.bounty", "Охота за головой", "Bounty")
k("merc.kind.hunt.tip", "Убейте этих существ сами (с талантом «Напарники» засчитываются и убийства союзников рядом).",
  "Kill these creatures yourself (with Partners, allies' kills near you count too).")
k("merc.kind.deliver.tip", "Принесите предметы и сдайте их в книге — они спишутся из инвентаря. Можно сдавать частями.",
  "Bring the items and hand them over in the book — they are taken from your inventory. Part by part is fine.")
k("merc.kind.explore.tip", "Войдите в этот край. Если вы уже в нём, выйдите и вернитесь: засчитывается только вход.",
  "Enter this land. If you are already in it, leave and come back: only entering counts.")
k("merc.kind.scout.tip", "Дойдите до отметки в Верхнем мире. Вблизи над ней поднимается столб золотой пыли и висит печать гильдии; направление — в книге и над панелью способностей.",
  "Reach the mark in the Overworld. Near it a column of gold dust rises under the guild's seal; the way is in the book and over the ability bar.")
k("merc.kind.bounty.tip", "Именной враг со свитой ждёт у отметки (вблизи — столб красно-золотой пыли). Он крепче обычного: больше здоровья, сильнее бьёт, носит броню. Убейте его сами или с союзниками.",
  "A named foe and its retinue wait at the mark (near it, a column of red-gold dust). It is tougher than usual: more health, harder blows, armour. Bring it down yourself or with allies.")
for r, ru, en, ru_t, en_t in [("night", "Только ночью", "Only at night", "Засчитываются только убийства в Верхнем мире ночью. Награда +30%.",
                               "Only kills in the Overworld at night count. +30% pay."),
                              ("bow", "Только стрелами", "Only with arrows", "Засчитываются только убийства стрелой, трезубцем или другим снарядом. Награда +30%.",
                               "Only kills with an arrow, a trident or another projectile count. +30% pay."),
                              ("brand", "Добить Клеймом", "Finish with the Brand", "Засчитываются только убийства взрывом вашего Клейма. Награда +30%.",
                               "Only kills by your Brand's burst count. +30% pay.")]:
    k(f"merc.rule.{r}", ru, en)
    k(f"merc.rule.{r}.tip", ru_t, en_t)
k("merc.rule_missed", "Не засчитано: условие заказа — %s", "Not counted: the job's condition is %s")
k("merc.progress", "Заказ: %s/%s", "Job: %s/%s")
k("merc.completed", "Заказ выполнен (%s): +%s жетонов, +%s опыта расы", "Job done (%s): +%s tokens, +%s race experience")
k("merc.completed_quick", "Заказ выполнен быстро (%s): +%s жетонов, +%s опыта расы", "Job done quickly (%s): +%s tokens, +%s race experience")
k("merc.bounty_here", "%s здесь — и не один", "%s is here — and not alone")
k("merc.bounty_lost", "Цель ушла от вас. Она снова будет ждать у отметки.", "The bounty slipped away. It will wait at the mark again.")
k("refuse.no_anchor", "Гарпуну не за что зацепиться в 26 блоках", "Nothing for the harpoon to bite within 26 blocks")

for lang, idx in (("ru_ru", 0), ("en_us", 1)):
    path = root / f"src/main/resources/assets/mages_legacy/lang/{lang}.json"
    data = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=OrderedDict)
    for key in list(data):
        if key.startswith(P + "shop.") or key.startswith(P + "merc.cat.") or key.startswith(P + "merc.rule"):
            del data[key]
    for key, pair in L.items():
        data[key] = pair[idx]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(len(L), "keys")
