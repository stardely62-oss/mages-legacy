#!/usr/bin/env bash
# Puts the effects engine (Veil, GeckoLib) and the renderer the players test with (Sodium, Iris, Iris Veil Compat) into
# run/mods for runClient.
set -euo pipefail
mkdir -p run/mods
rm -f run/mods/*.jar
fetch() { # project version-id file
	curl -fsSL -o "run/mods/$3" "https://cdn.modrinth.com/data/$1/versions/$2/$3"
}
fetch 3KUWeVhG IpGnlNgK veil-neoforge-1.21.1-4.5.2.jar
fetch 8BmcQJ2H Grwn5rUB geckolib-neoforge-1.21.1-4.9.3.jar
fetch AANobbMI uMOpc5uV sodium-neoforge-0.8.13%2Bmc1.21.1.jar
fetch YL57xq9U KduFYu4t iris-neoforge-1.8.14-beta.1%2Bmc1.21.1.jar
fetch owQyCHo4 hWItkPzn irisveil-0.4.0.jar
ls -la run/mods
