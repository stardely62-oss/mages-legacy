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

# Skip first-launch screens and keep running without window focus
cat > "$CLIENT/options.txt" <<'EOF'
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
for shot in lore choose_mycelid choose_bloodkin hud talents spore_jump spore_cloud node node_arrived; do
  test -s "$CLIENT/screenshots/$shot.png" || { echo "screenshot $shot.png was not written"; exit 1; }
done
grep -qx 'RESULT PASS' "$CLIENT/harness-result.txt"
