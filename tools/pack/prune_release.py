#!/usr/bin/env python3
"""Supprime de la release « cartes » les fichiers de versions remplacées depuis plus de 2 jours.

usage: prune_release.py catalog.json   (GH_TOKEN et GITHUB_REPOSITORY dans l'environnement)
"""
import datetime as dt, json, os, re, subprocess, sys

catalog = json.load(open(sys.argv[1], encoding="utf-8"))
current = {f["name"] for p in catalog["packs"] for f in p["files"]}
repo = os.environ["GITHUB_REPOSITORY"]
rel = json.loads(subprocess.check_output(["gh", "api", f"repos/{repo}/releases/tags/cartes"]))
now = dt.datetime.now(dt.timezone.utc)
pattern = re.compile(r"^[a-z0-9-]+-(\d{12}-)?(fond|relief|courbes)\.pmtiles$|^[a-z0-9-]+-(\d{12}-)?aero\.geojson$")
for a in rel.get("assets", []):
    name = a["name"]
    if name in current or not pattern.match(name):
        continue
    age = now - dt.datetime.fromisoformat(a["created_at"].replace("Z", "+00:00"))
    if age < dt.timedelta(days=2) and re.search(r"-\d{12}-", name):
        print("conservé (récent)", name)
        continue
    print("suppression", name)
    subprocess.run(["gh", "api", "-X", "DELETE", f"repos/{repo}/releases/assets/{a['id']}"], check=True)
