#!/usr/bin/env bash
# Puts the effects engine (Veil, GeckoLib) and the pack's Perfect Advancements into run/mods for runClient.
set -euo pipefail
mkdir -p run/mods
rm -f run/mods/*.jar
fetch() { # project version-id file
	curl -fsSL -o "run/mods/$3" "https://cdn.modrinth.com/data/$1/versions/$2/$3"
}
fetch 3KUWeVhG IpGnlNgK veil-neoforge-1.21.1-4.5.2.jar
fetch 8BmcQJ2H Grwn5rUB geckolib-neoforge-1.21.1-4.9.3.jar
# Perfect Advancements lives on CurseForge only; checked against the pack's sha512
curl -fsSL -o run/mods/perfectadvancements-1.21.1-1.0.0.jar https://mediafilez.forgecdn.net/files/8057/245/perfectadvancements-1.21.1-1.0.0.jar
echo "21de9eafc96ed7696c6b73d5a77c5d0676948935b3586a5a872b0aa7827ad0d1244ba1546fd42db17ee5ec45c2e244b2ba03c73c4549aafed9691f5cee734106  run/mods/perfectadvancements-1.21.1-1.0.0.jar" | sha512sum -c -
ls -la run/mods
