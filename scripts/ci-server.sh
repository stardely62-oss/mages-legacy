#!/usr/bin/env bash
# Starts a dedicated server with the mod (a flat world, offline), lets ServerSmoke tick it and stop it, checks server-smoke.txt.
set -uo pipefail
mkdir -p run
rm -rf run/world run/server-smoke.txt
echo "eula=true" > run/eula.txt
cat > run/server.properties <<'PROPS'
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
PROPS
cp run/logs/latest.log run/client-latest.log 2>/dev/null || true
timeout 600 ./gradlew runServer -PserverSmoke=true --stacktrace < /dev/null
echo "runServer exited with $?"
cp run/logs/latest.log run/server-latest.log 2>/dev/null || true
cp run/client-latest.log run/logs/latest.log 2>/dev/null || true
if [ ! -f run/server-smoke.txt ]; then
	echo "No server smoke result"
	exit 1
fi
cat run/server-smoke.txt
grep -q '^DONE' run/server-smoke.txt && ! grep -q '^FAIL' run/server-smoke.txt
