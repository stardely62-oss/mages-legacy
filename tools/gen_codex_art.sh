#!/usr/bin/env bash
# Generates GUI art with Codex image generation (the user's ChatGPT subscription), one image at a time.
# Usage: tools/gen_codex_art.sh <output folder> [name ...]; then python3 tools/import_codex_art.py <output folder>
# Names: talent_<id> become textures/gui/talent/<id>.png; the old prompts (emblems, old abilities) are in
# archive/tools/gen_codex_art.sh.
OUT="${1:?usage: tools/gen_codex_art.sh <output folder> [name ...]}"
shift
mkdir -p "$OUT"
ICON="Minecraft-style pixel art GUI ICON. The square canvas is a 32 by 32 grid of large, perfectly square, flat-colored pixels. ONE symbol centered, filling about 85 percent of the canvas, with a crisp 1-pixel near-black outline. The background is a flat, uniform, pure magenta (#FF00FF) everywhere around the symbol, no shadow, no vignette. No text, no frame, no gradients inside a pixel. Dark fantasy mood. It must read instantly at 16 pixels: ONE bold simple shape with a strong silhouette, at most 6 colours, high contrast against the dark, no tiny noisy details, no thin stringy lines."
VOID="Palette of a race touched by the void between worlds: near-black deep violet, glowing lavender violet (#9B7FD4) as the accent, and tiny white stars."
declare -A P=(
  [talent_pocket_wide_1]="$ICON $VOID Subject: a small square floating stone island in starry darkness with a bold arrow pointing outward from each side."
  [talent_pocket_wide_2]="$ICON $VOID Subject: a medium floating stone island with a tiny house on it, floating among stars, two outward arrows."
  [talent_pocket_wide_3]="$ICON $VOID Subject: a large floating island with a small tree and a house, a glowing violet rim around it, stars around."
  [talent_pocket_cheap]="$ICON $VOID Subject: a light, half-open violet rift door with a white feather in front of it."
  [talent_pocket_guests]="$ICON $VOID Subject: two small blocky figures standing together inside a glowing violet rift doorway, a small green heart above them."
  [talent_pocket_vault]="$ICON $VOID Subject: a tall stone archway vault rising high over a small floating island, stars above."
  [talent_pocket_cradle]="$ICON $VOID Subject: a crescent moon cradling a small sleeping violet orb, with a golden heart-shaped shield glint."
  [talent_awakening_rift]="$ICON $VOID Subject: a huge square arena of violet stone torn out of space, crossed swords above it, a rift in the sky."
  [talent_arena_regen]="$ICON $VOID Subject: a pink healing heart floating over a violet stone arena floor."
  [talent_arena_might]="$ICON $VOID Subject: a raised fist glowing red over a violet stone arena floor."
  [talent_arena_ward]="$ICON $VOID Subject: a stone shield standing in front of a violet arena wall."
  [talent_arena_long]="$ICON $VOID Subject: an hourglass standing in a violet rift, its sand glowing violet."
  [talent_step_far]="$ICON $VOID Subject: a long bold violet dashed arrow flying from a small rift on the left to a small rift on the right."
  [talent_step_far_2]="$ICON $VOID Subject: a violet arrow piercing straight through a grey stone mountain."
  [talent_step_quick]="$ICON $VOID Subject: a short violet lightning-fast arrow between two rifts, with three speed lines."
  [talent_step_trace]="$ICON $VOID Subject: a dark hooded silhouette fading into violet mist, a red monster eye crossed out above it."
  [talent_step_ally]="$ICON $VOID Subject: two blocky figures walking side by side through a glowing violet rift."
  [talent_step_mark]="$ICON $VOID Subject: a glowing violet pin mark planted in starry space with a small rift around it."
  [talent_awakening_worlds]="$ICON $VOID Subject: two glowing violet rifts facing each other, joined by a bright arc of stars."
  [talent_worlds_long]="$ICON $VOID Subject: a stable glowing violet portal ring held open by four small stone pillars."
  [talent_worlds_far]="$ICON $VOID Subject: a violet portal with a distant tiny second portal far behind it on a long starry path."
  [talent_worlds_train]="$ICON $VOID Subject: a horse and a dog walking into a glowing violet portal."
  [talent_grav_wide]="$ICON $VOID Subject: a heavy dark violet sphere pressing on cracked ground, wide circular shockwave rings around it."
  [talent_grav_slow]="$ICON $VOID Subject: a heavy lead weight with violet glow, crushing a small boot."
  [talent_grav_pull]="$ICON $VOID Subject: a dark violet orb with four bold arrows pointing inward toward it."
  [talent_grav_fliers]="$ICON $VOID Subject: a black bat falling downward, pulled by violet gravity lines toward cracked ground."
  [talent_grav_arrows]="$ICON $VOID Subject: an arrow bending and curving down toward the ground inside a violet field."
  [talent_awakening_hole]="$ICON $VOID Subject: a black hole: a pure black core with a bright white-hot ring and a glowing violet accretion disk spiralling in."
  [talent_hole_wide]="$ICON $VOID Subject: a black hole with a very wide faint violet ring around it, like an event horizon."
  [talent_hole_deep]="$ICON $VOID Subject: a black hole collapsing in a bright white-violet starburst explosion."
  [talent_hole_quick]="$ICON $VOID Subject: a small black hole with a lightning bolt beside it."
  [talent_step_door]="$ICON $VOID Subject: an open wooden door standing in starry void, a violet rift glowing inside the door frame."
  [talent_step_heavy]="$ICON $VOID Subject: a violet rift with cracked ground beneath it and a heavy downward arrow."
  [talent_door_recoil]="$ICON $VOID Subject: a rift door bursting open with a violet shockwave pushing outward."
)
NAMES=("$@")
[ ${#NAMES[@]} -eq 0 ] && NAMES=("${!P[@]}")
for name in "${NAMES[@]}"; do
  [ -s "$OUT/$name.png" ] && continue
  timeout 400 codex exec --skip-git-repo-check --ephemeral -s workspace-write -C "$OUT" \
    "Use your image generation tool to create ONE image. ${P[$name]} Then copy the generated image into the current directory as $name.png. Do nothing else." \
    < /dev/null > "$OUT/$name.log" 2>&1
  echo "$name: $( [ -s "$OUT/$name.png" ] && echo ok || echo FAILED )"
done
