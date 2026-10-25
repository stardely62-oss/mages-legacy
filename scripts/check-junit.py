"""Fails unless the GameTest JUnit report exists, has tests, and none failed."""
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1] if len(sys.argv) > 1 else "build/gametest/junit.xml"
try:
    root = ET.parse(path).getroot()
except (OSError, ET.ParseError) as e:
    sys.exit(f"no usable GameTest report at {path}: {e}")

cases = root.findall(".//testcase")
failed = [c for c in cases if c.find("failure") is not None or c.find("error") is not None]
for c in cases:
    status = "FAIL" if c in failed else "OK"
    print(f"{status}  {c.get('classname', '')} {c.get('name')}")
    for f in c.findall("failure") + c.findall("error"):
        print(f"      {f.get('message') or (f.text or '').strip()}")
print(f"{len(cases) - len(failed)}/{len(cases)} GameTests passed")
if not cases or failed:
    sys.exit(1)
