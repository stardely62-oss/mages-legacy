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
  "R: медно-золотой снаряд летит на 20 блоков (сквозь стены — нет) и бьёт первого врага на 4 урона. Над врагом появляется печать из трёх частей — "
  "Клеймо на 10 секунд. Каждый ваш удар по нему (оружием, своей стрелой или Гарпуном) зажигает одну часть, не чаще раза в 8 тиков. "
  "Третий удар взрывает Клеймо: ещё 6 урона (по игроку не больше 4), во все стороны летят монеты, а вы получаете 2 поглощения на 4 секунды. "
  "Урон самой печати, взрыва, клинков и знамени частей не зажигает. Союзников и питомцев не метит. Перезарядка 8 секунд.",
  "R: a copper-and-gold bolt flies 20 blocks (never through walls) and hits the first foe for 4. A three-part seal appears over it — the Brand, "
  "for 10 seconds. Each of your blows on it (a weapon, your own arrow or the Harpoon) lights one part, no more often than every 8 ticks. "
  "The third blow bursts the Brand: 6 more damage (4 at most to a player), coins flying every way, and 2 absorption for you for 4 seconds. "
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
  "Пробуждение: враг в 20 блоках осуждён на 15 секунд. Вокруг него кружат 5 спектральных мечей. Каждые два ваших удара по нему один меч "
  "вонзается в цель — 3 урона (по игроку 2). Шестой удар вызывает исполнение: огромный меч падает сверху — 8 урона (по игроку 4), один раз. "
  "Если осуждённый погибнет раньше, Приговор кончается. Перезарядка 2 минуты.",
  "Awakening: a foe within 20 blocks is condemned for 15 seconds. Five spectral swords circle it. Every two of your blows drive one sword "
  "in — 3 damage (2 to a player). The sixth blow calls the execution: a great sword falls from above — 8 damage (4 to a player), once. "
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
    "mages_shop": ("Ядро. Клавиша H открывает Лавку мага: товары, услуги и книгу заказов.", "Core. H opens the Mage's Shop: wares, services and the book of orders."),
    "hunter_brand": ("Способность R (с 3 уровня). Снаряд вешает на врага печать; три ваших удара по нему — взрыв на 6 урона.",
                     "Ability R (from level 3). A bolt hangs a seal on a foe; three of your blows on it — a burst of 6."),
    "guild_harpoon": ("Способность V (с 5 уровня). Гарпун на 26 блоков: лёгкого врага тянет к вам, вас — к тяжёлому врагу или стене.",
                      "Ability V (from level 5). A 26-block harpoon: a light foe is dragged to you, you to a heavy foe or a wall."),
    "con_board": ("В книге заказов 4 предложения вместо 3 — больше выбор.", "The book of orders offers 4 jobs instead of 3."),
    "con_hands": ("Можно держать 3 заказа одновременно вместо 2.", "You can hold 3 jobs at once instead of 2."),
    "con_elite": ("Среди предложений появляются элитные заказы (не больше одного): нужно вдвое больше, платят в 2,5 раза больше.",
                  "Elite jobs reach the book (one at a time): twice the work for two and a half times the pay."),
    "con_bargain": ("Все товары, услуги и новая доска заказов на 15% дешевле.", "All wares, services and a fresh board cost 15% less."),
    "con_scout": ("Цели ваших охотничьих заказов в 32 блоках подсвечиваются — их видно сквозь стены.",
                  "The quarry of your hunts within 32 blocks glows — seen through walls."),
    "con_tracker": ("По целям охотничьих заказов вы бьёте на 20% сильнее (вместо врождённых 10%).",
                    "You hit the quarry of your hunts 20% harder (instead of the innate 10%)."),
    "con_regular": ("В лавке на 2 товара больше, а скидка дня (−30%) действует на два товара вместо одного.",
                    "Two more wares in the shop, and the deal of the day (−30%) is on two wares instead of one."),
    "con_partners": ("Убийства союзников и ваших питомцев в 24 блоках от вас засчитываются в ваши охотничьи заказы.",
                     "Kills by allies and your pets within 24 blocks of you count towards your hunts."),
    "con_bonus": ("Каждый заказ платит на 25% больше жетонов.", "Every job pays 25% more tokens."),
    "con_renown": ("Каждый заказ даёт на 20% больше опыта расы.", "Every job gives 20% more race experience."),
    "con_guide": ("Взяв заказ «дойти до края», вы сразу видите в книге расстояние и сторону света до ближайшего такого края.",
                  "Taking a job to reach a land shows the distance and direction to the nearest such land in the book."),
    "con_connections": ("Первое обновление доски заказов за день бесплатно, следующие вдвое дешевле.", "The first fresh board of a day is free, the rest cost half."),
    "con_streak": ("Заказы подряд (каждый в течение 20 минут после прошлого) платят больше: +10% за каждый в серии, до +30%.",
                   "Jobs in a row (each within 20 minutes of the last) pay more: +10% for each in the streak, up to +30%."),
    "awakening_ransom": ("Пробуждение. Долг жизни: часть урона откладывается, и вы гасите её, сражаясь. Последний выкуп: смерть отменяется за 30 жетонов.",
                         "Awakening. Life Debt: part of the damage is put off and you pay it down by fighting. The Last Ransom: death undone for 30 tokens."),
    "ransom_write_off": ("Когда Долг жизни кончается непогашенным, четверть остатка прощается — расплата меньше.",
                         "When a Life Debt ends unpaid, a quarter of what is left is forgiven — a smaller reckoning."),
    "ransom_guarantor": ("Если договор на союзника, ваши удары гасят его Долг на 30% урона вместо 20%.",
                         "With the Debt signed for an ally, your blows pay it down by 30% of the damage instead of 20%."),
    "ransom_gold": ("Волна погашенного Долга бьёт на 6 вместо 4.", "The wave of a paid-off Debt hits for 6 instead of 4."),
    "ransom_second": ("После Последнего выкупа вы 3 секунды под Сопротивлением I — чтобы не умереть снова сразу же.",
                      "After the Last Ransom you have Resistance I for 3 seconds — so as not to die again at once."),
    "brand_far": ("Снаряд Клейма летит на 28 блоков вместо 20.", "The Brand's bolt flies 28 blocks instead of 20."),
    "brand_collect": ("Если взрыв Клейма добил цель, Клеймо сразу снова готово (перезарядка сбрасывается).",
                      "If a Brand's burst kills its bearer, the Brand is ready again at once."),
    "brand_compound": ("Печать из четырёх частей: взрыв на четвёртом ударе вместо третьего, зато 10 урона вместо 6.",
                       "A four-part seal: it bursts on the fourth blow instead of the third, but for 10 instead of 6."),
    "brand_linger": ("Клеймо держится 15 секунд вместо 10 — есть время на три удара.", "The Brand holds 15 seconds instead of 10 — time for three blows."),
    "brand_spread": ("Взрыв перекидывает новое Клеймо (с одной уже горящей частью) на ближайшего врага в 6 блоках. Перекинутое дальше не перекидывается.",
                     "A burst passes a new Brand (one part already lit) to the nearest foe within 6 blocks. A passed Brand passes no further."),
    "brand_pledge": ("Взрыв даёт 4 поглощения вместо 2 (всего не больше 6).", "A burst gives 4 absorption instead of 2 (6 at most)."),
    "brand_swap": ("Клеймо на новую цель забирает с прошлой метки уже зажжённые части (кроме последней) — не теряете прогресс, переключаясь.",
                   "Branding a new foe carries over the parts already lit on your last one (all but the last) — switching loses nothing."),
    "brand_pierce": ("Снаряд Клейма пробивает первую цель и метит вторую позади неё.", "The Brand's bolt passes through its first mark and brands a second behind it."),
    "brand_finisher": ("Взрыв по цели с запасом здоровья меньше 30% наносит в полтора раза больше.",
                       "A burst on a foe under 30% health deals half again as much."),
    "brand_chain": ("Взрыв задевает и врагов в 3 блоках вокруг — на половину урона.", "A burst also strikes foes within 3 blocks around for half."),
    "awakening_verdict": ("Пробуждение. Приговор одной цели: мечи вокруг неё бьют за каждые два ваших удара, шестой удар — исполнение.",
                          "Awakening. A Verdict on one foe: the swords round it strike for every two of your blows, the sixth blow — the execution."),
    "verdict_long": ("Приговор длится 20 секунд вместо 15.", "The Verdict lasts 20 seconds instead of 15."),
    "verdict_boss": ("Мечи и исполнение бьют боссов на 50% сильнее.", "Swords and the execution hit bosses 50% harder."),
    "verdict_blades": ("Вокруг осуждённого 7 мечей вместо 5 — больше ударов за Приговор.", "Seven swords circle the condemned instead of five."),
    "verdict_refund": ("Если осуждённый погиб от вашей руки, перезарядка Приговора на 40 секунд короче (раз за Приговор).",
                       "If the condemned dies by your hand, the Verdict's cooldown is 40 seconds shorter (once a Verdict)."),
    "verdict_triumph": ("После исполнения — Сила I и Скорость I на 6 секунд.", "After the execution: Strength I and Speed I for 6 seconds."),
    "harp_quick": ("Перезарядка Гарпуна 7 секунд вместо 10.", "The Harpoon's cooldown is 7 seconds instead of 10."),
    "harp_long": ("Гарпун летит на 34 блока вместо 26.", "The Harpoon reaches 34 blocks instead of 26."),
    "harp_double": ("После броска у вас есть 3 секунды на второй бросок без перезарядки (один раз).",
                    "After a throw you have 3 seconds for a second throw without the cooldown (once)."),
    "harp_stun": ("Гарпун бьёт на 4 вместо 2, а притянутое существо 1,5 секунды почти не может двигаться (Медлительность III).",
                  "The Harpoon hits for 4 instead of 2, and a creature dragged in can hardly move for 1.5 seconds (Slowness III)."),
    "harp_precise": ("Первый удар оружием в течение 3 секунд после попадания Гарпуном — на 40% сильнее.",
                     "The first weapon blow within 3 seconds of a Harpoon hit deals 40% more."),
    "craft_cuirass": ("Всегда +2 брони и +1 к твёрдости брони.", "Always +2 armour and +1 armour toughness."),
    "craft_bow": ("Ваши стрелы и другие снаряды наносят на 15% больше.", "Your arrows and other projectiles deal 15% more."),
    "craft_second_wind": ("Когда здоровье падает ниже 30%, вы получаете Скорость II и Регенерацию I на 4 секунды. Раз в минуту.",
                          "When your health drops under 30%, you get Speed II and Regeneration I for 4 seconds. Once a minute."),
    "craft_blade": ("Удары оружием ближнего боя наносят на 12% больше.", "Melee weapon blows deal 12% more."),
    "craft_lifeline": ("Гарпуном можно зацепить союзника: вас тянет к нему, и вы оба получаете 4 поглощения на 6 секунд.",
                       "The Harpoon can catch an ally: you are drawn to them and you both get 4 absorption for 6 seconds."),
    "craft_shoulder": ("Когда ваше Клеймо взрывается, союзники в 8 блоках получают Сопротивление I на 3 секунды.",
                       "When your Brand bursts, allies within 8 blocks get Resistance I for 3 seconds."),
    "awakening_legend": ("Пробуждение. Знамя гильдии: усиливает вас и союзников в круге и рубит врагов клинками.",
                         "Awakening. The guild's banner: strengthens you and your allies in its circle and cuts foes with blades."),
    "legend_wide": ("Круг знамени радиусом 13 блоков вместо 10.", "The banner's circle is 13 blocks across instead of 10."),
    "legend_ward": ("Под знаменем получаемый урон −15% вместо −10%.", "Under the banner you take 15% less damage instead of 10%."),
    "legend_blades": ("Знамя бьёт клинками до 4 врагов за раз вместо 3.", "The banner's blades strike up to 4 foes at a time instead of 3."),
    "legend_long": ("Знамя стоит 18 секунд вместо 15 (убийства по-прежнему продлевают не больше чем на 6).",
                    "The banner stands 18 seconds instead of 15 (kills still lengthen it by 6 at most)."),
    "cross_contract_kill": ("Взрыв Клейма по цели вашего охотничьего заказа наносит в полтора раза больше.",
                            "A Brand bursting on the quarry of your hunt deals half again as much."),
    "cross_tension": ("Попадание Гарпуном по цели с Клеймом продлевает Клеймо на полный срок.", "A Harpoon hit on a branded foe renews its Brand's full time."),
    "cross_guild_grab": ("Попадание Гарпуном по цели охотничьего заказа сокращает перезарядку Клейма на 4 секунды.",
                         "A Harpoon hit on the quarry of a hunt cuts the Brand's cooldown by 4 seconds."),
    "cross_trophy": ("Цель охотничьего заказа с вашим Клеймом, убитая вами, засчитывается за две.", "A branded quarry you kill counts twice towards its hunt."),
    "cross_rhythm": ("Каждый взрыв Клейма сокращает перезарядку Гарпуна на 3 секунды.", "Each Brand burst cuts the Harpoon's cooldown by 3 seconds."),
    "cross_silver": ("Выполнив заказ, вы восстанавливаете 6 здоровья, Гарпун сразу готов, а идущий Долг жизни уменьшается на 4.",
                     "Finishing a job heals you 6, readies the Harpoon at once and cuts a running Life Debt by 4."),
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
k("merc.kind.scout.tip", "Дойдите до отметки в Верхнем мире. Вблизи над ней встаёт золотой столб света; направление — в книге и над панелью способностей.",
  "Reach the mark in the Overworld. Near it a golden pillar of light rises; the way is in the book and over the ability bar.")
k("merc.kind.bounty.tip", "Именной враг со свитой ждёт у отметки (вблизи — красно-золотой столб света). Он крепче обычного: больше здоровья, сильнее бьёт, носит броню. Убейте его сами или с союзниками.",
  "A named foe and its retinue wait at the mark (near it, a red-gold pillar of light). It is tougher than usual: more health, harder blows, armour. Bring it down yourself or with allies.")
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
