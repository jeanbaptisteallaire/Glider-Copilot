#!/usr/bin/env python3
"""Vérifie que le projet autonome ne dépend pas des moteurs protégés de GLIDY."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN = {
    "feature:flight",
    "data:ogn",
    "data:carto",
    "FlightEngine",
    "FlightService",
    "SafetyEngine",
    "OgnLiveRepository",
    "com.neutronstar.glidercopilot.feature.flight",
    "com.neutronstar.glidercopilot.ogn",
    "com.neutronstar.glidercopilot.carto",
}

files = [
    path
    for path in ROOT.rglob("*")
    if path.is_file()
    and path.suffix in {".kt", ".kts"}
    and "build" not in path.parts
]

violations = []
for path in files:
    text = path.read_text(encoding="utf-8")
    for needle in FORBIDDEN:
        if needle in text:
            violations.append(f"{path.relative_to(ROOT)}: référence interdite {needle!r}")

if violations:
    print("\n".join(violations), file=sys.stderr)
    raise SystemExit(1)

print(f"Frontières valides : {len(files)} fichiers Kotlin/Gradle vérifiés.")

