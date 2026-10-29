#!/usr/bin/env bash
# Puts Photon 2 and its libraries (the Modrinth primary jars, with their nested jars) into run/mods for runClient.
set -euo pipefail
mkdir -p run/mods
fetch() { # project version-id file
	curl -fsSL -o "run/mods/$3" "https://cdn.modrinth.com/data/$1/versions/$2/$3"
}
fetch gzevkJbM XQsukdXk photon-neoforge-1.21.1-2.2.9-all.jar
fetch B1CBVXHX s7Uru4V0 ldlib2-neoforge-1.21.1-2.2.44-all.jar
fetch zZO0N3W6 KwiKbOzM kilagraph-neoforge-1.21.1-21.1.0.16.jar
ls -la run/mods
