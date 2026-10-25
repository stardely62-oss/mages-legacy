#!/usr/bin/env bash
# Generates the GUI art with Codex image generation (your ChatGPT subscription), one image at a time:
# race emblems, ability and talent icons, the spore icon and the legend illustration.
# Usage: tools/gen_codex_art.sh <output folder> [name ...]; then python3 tools/import_codex_art.py <output folder>
OUT="${1:?usage: tools/gen_codex_art.sh <output folder> [name ...]}"
shift
mkdir -p "$OUT"
ICON="Minecraft-style pixel art GUI ICON. The square canvas is a 32 by 32 grid of large, perfectly square, flat-colored pixels. ONE symbol centered, filling about 85 percent of the canvas, with a crisp 1-pixel near-black outline. The background is a flat, uniform, pure magenta (#FF00FF) everywhere around the symbol, no shadow, no vignette. No text, no frame, no gradients inside a pixel. Dark fantasy mood, readable at small size, strong silhouette."
MYC="Palette of a fungal race: dark brown and umber mushroom flesh, bone white, and glowing bioluminescent spore green (#9CFF8A, #7FB069) as the accent."
EMBLEM="Minecraft-style pixel art HERALDIC EMBLEM. The square canvas is a 64 by 64 grid of large, perfectly square, flat-colored pixels. One round medallion centered, filling about 90 percent of the canvas: a dark carved stone ring with small runes, and the race's symbol inside, with a crisp 1-pixel near-black outline. The background is a flat, uniform, pure magenta (#FF00FF) everywhere around the medallion. No text, no gradients inside a pixel. Ancient, mysterious, dark fantasy."
declare -A P=(
  [emblem_mycelid]="$EMBLEM Symbol: a glowing bioluminescent mushroom with mycelium threads spreading like roots through the ring. Accent colour moss green (#7FB069) and glowing spore green."
  [emblem_shepherd]="$EMBLEM Symbol: a shepherd's crook staff crossed with the ghostly translucent silhouette of a fox spirit. Accent colour warm gold (#D9B26F) and pale spirit cyan."
  [emblem_frozen]="$EMBLEM Symbol: an hourglass frozen inside sharp ice crystals, sand stopped mid-fall. Accent colour pale ice blue (#8FB8DE) and white."
  [emblem_bloodkin]="$EMBLEM Symbol: a single large drop of blood wrapped in thorny black rose vines. Accent colour deep crimson (#B23A48)."
  [emblem_voidborn]="$EMBLEM Symbol: a jagged rift torn open in the middle showing stars, with a single eye inside it. Accent colour violet (#9B7FD4) and black."
  [ability_spore_jump]="$ICON $MYC Subject: a glowing spore flying in a high arc leaving a dotted green trail, a small mushroom sprouting where it lands."
  [ability_spore_cloud]="$ICON $MYC Subject: a dense swirling round cloud of glowing green spores, with darker toxic puffs inside."
  [ability_network]="$ICON $MYC Subject: a big glowing mushroom node in the middle with glowing mycelium threads running to two small mushrooms at the corners."
  [res_spores]="$ICON $MYC Subject: a single round glowing spore orb, bright green core, tiny floating specks around it."
  [swarm_wide]="$ICON $MYC Subject: a wide, billowing cloud of glowing green spores spreading sideways."
  [swarm_trail]="$ICON $MYC Subject: a trail of three poisonous green footprints with toxic drips."
  [swarm_potent]="$ICON $MYC Subject: corrosive acid-green spore droplets sizzling and smoking."
  [swarm_infection]="$ICON $MYC Subject: a bone-white skull with small glowing mushrooms sprouting from its top."
  [swarm_lingering]="$ICON $MYC Subject: a large glowing spore blossom flower in full bloom, petals hanging down, releasing spores."
  [net_chain]="$ICON $MYC Subject: three glowing spore orbs linked by a zigzag chain of green light, like three jumps."
  [net_reach]="$ICON $MYC Subject: a spore flying far along a long dashed arc trajectory toward a distant target mark."
  [net_nodes]="$ICON $MYC Subject: a web of glowing mycelium threads connecting three small mushroom nodes."
  [net_far]="$ICON $MYC Subject: glowing roots reaching deep down through dark soil layers."
  [net_roots]="$ICON $MYC Subject: twisting roots wrapping tightly around a boot, holding it in place."
  [flesh_two_tastes]="$ICON $MYC Subject: a red mushroom and a brown mushroom crossed like an X."
  [flesh_rich]="$ICON $MYC Subject: a wooden bowl of rich mushroom stew, green glowing steam rising."
  [flesh_dark_regen]="$ICON $MYC Subject: a thin crescent moon next to a glowing green heart."
  [flesh_cap]="$ICON $MYC Subject: a big red-and-white spotted mushroom cap shaped like a sturdy helmet."
  [flesh_sunproof]="$ICON $MYC Subject: a yellow sun being eclipsed by a dark mushroom cap that casts a shadow."
  [lore_mage]="Wide landscape Minecraft-style pixel art SCENE, 3:2 aspect, made of a 96 by 64 grid of large, perfectly square, flat-colored pixels, no text. At night under a starry sky: a hooded nameless ancient mage seen from behind stands before a tall black stone obelisk with a glowing green eye in an iron cage at its top. From the eye five streams of light flow down in five colours (moss green, warm gold, ice blue, crimson, violet) toward five tiny silhouettes of people standing apart on the dark grass. Grim, mysterious, dark fantasy, desaturated with the light streams as the only bright colours."
)
NAMES=("$@")
[ ${#NAMES[@]} -eq 0 ] && NAMES=(emblem_mycelid emblem_shepherd emblem_frozen emblem_bloodkin emblem_voidborn ability_spore_jump ability_spore_cloud ability_network res_spores swarm_wide swarm_trail swarm_potent swarm_infection swarm_lingering net_chain net_reach net_nodes net_far net_roots flesh_two_tastes flesh_rich flesh_dark_regen flesh_cap flesh_sunproof lore_mage)
for name in "${NAMES[@]}"; do
  [ -s "$OUT/$name.png" ] && continue
  timeout 400 codex exec --skip-git-repo-check --ephemeral -s workspace-write -C "$OUT" \
    "Use your image generation tool to create ONE image. ${P[$name]} Then copy the generated image into the current directory as $name.png. Do nothing else." \
    < /dev/null > "$OUT/$name.log" 2>&1
  echo "$name: $( [ -s "$OUT/$name.png" ] && echo ok || echo FAILED )"
done
