#!/usr/bin/env python3
"""Contrôle de format d'une trace IGC GLIDY (CI) : enregistrements A, H, B, heures croissantes, positions plausibles."""
import re, sys

def check(path):
    raw = open(path, "rb").read()
    errors = []
    try:
        text = raw.decode("ascii")
    except UnicodeDecodeError:
        errors.append("caractères non ASCII")
        text = raw.decode("latin-1")
    lines = [l for l in text.split("\r\n") if l]
    if not lines or not lines[0].startswith("A") or len(lines[0]) < 7:
        errors.append("première ligne : enregistrement A attendu")
    if not any(re.fullmatch(r"HFDTEDATE:\d{6},\d{2}", l) for l in lines):
        errors.append("en-tête HFDTEDATE absent")
    b = [l for l in lines if l.startswith("B")]
    rx = re.compile(r"B(\d{2})(\d{2})(\d{2})(\d{2})(\d{5})([NS])(\d{3})(\d{5})([EW])([AV])([-\d]\d{4})([-\d]\d{4})")
    last = -1; day = 0; bad = 0; alts = []
    for l in b:
        m = rx.fullmatch(l)
        if not m:
            bad += 1; continue
        g = m.groups()
        t = int(g[0]) * 3600 + int(g[1]) * 60 + int(g[2]) + day * 86400
        if last >= 0 and t < last - 43200:
            day += 1; t += 86400
        if t <= last:
            errors.append(f"heure non croissante : {l}")
        last = t
        if int(g[4]) >= 60000 or int(g[7]) >= 60000:
            errors.append(f"minutes invalides : {l}")
        alts.append(int(g[11]))
    if bad:
        errors.append(f"{bad} enregistrements B mal formés")
    if len(b) < 60:
        errors.append(f"trop peu de points : {len(b)}")
    print(f"{path} : {len(lines)} lignes, {len(b)} points B, altitude GNSS {min(alts, default=0)}–{max(alts, default=0)} m")
    for e in errors[:10]:
        print("  ERREUR", e)
    print("  OK" if not errors else f"  {len(errors)} erreur(s)")
    return not errors

if __name__ == "__main__":
    files = [f for f in sys.argv[1:] if not f.endswith("*.igc")]
    if not files:
        print("aucune trace IGC trouvée"); sys.exit(1)
    sys.exit(0 if all([check(f) for f in files]) else 1)
