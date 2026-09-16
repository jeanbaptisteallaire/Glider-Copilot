#!/usr/bin/env python3
"""Assemble catalog.json (lu par l'app) : packs reconstruits dans OUT + packs déjà publiés non reconstruits.

usage: catalog.py OUT [catalogue_publié.json]
"""
import datetime as dt, glob, json, os, sys

out = sys.argv[1]
previous = {}
if len(sys.argv) > 2 and os.path.exists(sys.argv[2]):
    try:
        previous = {p["id"]: p for p in json.load(open(sys.argv[2], encoding="utf-8")).get("packs", [])}
    except Exception as e:  # catalogue absent ou illisible : on repart de zéro
        print("catalogue précédent ignoré :", e)
keys = ("id", "name", "version", "created", "bbox", "aeroValidUntil", "aeroCounts", "attribution", "files")
for path in sorted(glob.glob(f"{out}/*/*-manifest.json")):
    m = json.load(open(path, encoding="utf-8"))
    previous[m["id"]] = {k: m.get(k) for k in keys}
catalog = {
    "format": 1,
    "generated": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    "packs": [previous[k] for k in sorted(previous)],
}
json.dump(catalog, open(f"{out}/catalog.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(json.dumps({p["id"]: sum(f["size"] for f in p["files"]) for p in catalog["packs"]}))
