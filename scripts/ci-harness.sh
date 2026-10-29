#!/usr/bin/env bash
# Runs the client under Xvfb with the harness on, then checks harness-result.txt.
set -uo pipefail
rm -rf run/saves run/screenshots run/harness-result.txt
mkdir -p run
# No accessibility onboarding, no tutorial toasts, a fixed GUI scale for comparable screenshots
cat > run/options.txt <<'OPT'
onboardAccessibility:false
tutorialStep:none
guiScale:2
OPT
timeout 900 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient -Pharness=true --stacktrace
code=$?
echo "runClient exited with $code"
if [ ! -f run/harness-result.txt ]; then
	echo "No harness result"
	exit 1
fi
cat run/harness-result.txt
grep -q '^DONE' run/harness-result.txt && ! grep -q '^FAIL' run/harness-result.txt
