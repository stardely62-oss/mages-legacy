#!/usr/bin/env bash
# Runs the scripted client scenario (CI, Linux with Xvfb): generate a world with the dedicated
# server, copy it into the client's saves, play the scenario, then demand a PASS and a fresh screenshot.
set -euo pipefail
cd "$(dirname "$0")/.."

SERVER=build/harness-server
CLIENT=build/harness-client

rm -rf "$SERVER" "$CLIENT/saves/harness" "$CLIENT/screenshots" "$CLIENT/harness-result.txt"
mkdir -p "$SERVER" "$CLIENT/saves"

echo 'eula=true' > "$SERVER/eula.txt"
cat > "$SERVER/server.properties" <<'EOF'
level-name=world
level-type=minecraft\:normal
level-seed=20261025
generate-structures=true
online-mode=false
spawn-protection=0
spawn-monsters=false
gamemode=survival
spawn-animals=false
EOF

timeout 300 ./gradlew runGenworld --stacktrace
test -f "$SERVER/world/level.dat" || { echo 'genworld did not create a world'; exit 1; }
cp -r "$SERVER/world" "$CLIENT/saves/harness"

# JourneyMap, as players have it: our keys must not clash with it out of the box
JM_URL=https://cdn.modrinth.com/data/lfHFW1mp/versions/Dk3AKHGe/journeymap-fabric-1.20.1-6.0.7.jar
JM_SHA512=9a6b9bbdf1d0e7532ede179cb47ca07626d9df3a27d3bb4472b77dfa2ef010ccd2bea40a3f47f048ffe095d103a42dd492b1cc43c0f06349c0e67933b866692e
mkdir -p "$CLIENT/mods"
rm -f "$CLIENT"/mods/journeymap-*.jar
curl -sSfL -o "$CLIENT/mods/journeymap-fabric-1.20.1-6.0.7.jar" "$JM_URL"
echo "$JM_SHA512  $CLIENT/mods/journeymap-fabric-1.20.1-6.0.7.jar" | sha512sum -c -

# Trial VFX libraries, only in the harness (the mod doesn't depend on them): Photon and Lodestone with what it needs
fetch() {
	curl -sSfL -o "$CLIENT/mods/$1" "$2"
	echo "$3  $CLIENT/mods/$1" | sha512sum -c -
}
fetch photon-fabric-1.20.1-1.1.17.jar https://cdn.modrinth.com/data/gzevkJbM/versions/Fh02Gr1l/photon-fabric-1.20.1-1.1.17.jar \
	c549a551fb4839631463f05e93c44eecc3641cff787593e3eac9b61f48733cf49a137b842d9f1ebf1b872a70e4994e004ee5160fb61cde4fb628f39831c2d91c
fetch lodestone-1.20.1-1.6.2.3f-fabric.jar https://cdn.modrinth.com/data/bN3xUWdo/versions/IRyjw8OM/lodestone-1.20.1-1.6.2.3f-fabric.jar \
	55bd6e3bfe397a1ab32b8d8418192bab206f8d5ac94d1f957cb76697b4d977e8928bd6eefe1f0933420baa25f40653e48995e3b8cf273dda4d8c262c75385f5e
fetch cardinal-components-api-5.2.3.jar https://cdn.modrinth.com/data/K01OU20C/versions/Ielhod3p/cardinal-components-api-5.2.3.jar \
	f20484d5bc780bee9b388ff6075e7c3bd130c7f8cae75a425bfd1fb68d03ca19288c09b0729992987fd32f3a2433b49c25162e086de82abd8d06ee45e4e3c917

# Skip first-launch screens and keep running without window focus. The third ability sits on G, as it did before
# JourneyMap's G was known: the mod has to move it back to its free default by itself.
# (version: 1.20.1's data version, or the options datafixer chokes on modern key names and drops the whole file)
cat > "$CLIENT/options.txt" <<'EOF'
version:3465
key_key.mages_legacy.ability_3:key.keyboard.g
onboardAccessibility:false
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
pauseOnLostFocus:false
soundCategory_master:0.0
lang:ru_ru
renderDistance:8
guiScale:2
EOF

timeout 600 xvfb-run -a -s '-screen 0 1280x720x24' ./gradlew runHarness --stacktrace || true

echo '--- harness result ---'
cat "$CLIENT/harness-result.txt" 2>/dev/null || echo '(no result file: the client crashed or never reached the world)'
for shot in lore choose_mycelid choose_voidborn hud talents spore_jump spore_cloud node node_arrived; do
  test -s "$CLIENT/screenshots/$shot.png" || { echo "screenshot $shot.png was not written"; exit 1; }
done
grep -qx 'RESULT PASS' "$CLIENT/harness-result.txt"
