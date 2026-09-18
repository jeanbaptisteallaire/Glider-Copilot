#!/usr/bin/env python3
"""Diagnostic OGN : qui vole vraiment dans le sud de la France, et notre connexion reçoit-elle bien tout ?

Deux mesures en parallèle, sur la même durée :
  1. « scan » : connexion avec un indicatif APRS valide (≤ 9 caractères) et le filtre de zone demandé ;
  2. « app »  : connexion avec l'indicatif tel que l'app le fabrique (option --app-user), pour vérifier
     qu'il est accepté par le serveur.

Sortie : comptage des aéronefs par type, anneaux de distance autour du terrain, plus gros porteurs,
et comparaison des deux connexions. Rien n'est publié : seules des statistiques agrégées le sont.

usage: scan.py --seconds 120 --lat 43.80028 --lon 3.78167 --radius 300 --out out
"""
import argparse, json, math, os, re, socket, threading, time
from collections import defaultdict

HOST = "aprs.glidernet.org"
PORT = 14580

POS = re.compile(
    r"^(?P<call>[A-Za-z0-9]{3,9})>(?P<dst>[A-Z0-9]+),(?P<path>[^:]*):[/@](?P<hh>\d{2})(?P<mm>\d{2})(?P<ss>\d{2})h"
    r"(?P<lat>\d{4}\.\d{2})(?P<ns>[NS]).(?P<lon>\d{5}\.\d{2})(?P<ew>[EW]).(?:(?P<crs>\d{3})/(?P<spd>\d{3}))?"
    r"(?:/A=(?P<alt>-?\d{5,6}))?(?P<rest>.*)$")
ID = re.compile(r"\bid([0-9A-Fa-f]{2})([0-9A-Fa-f]{6})\b")
FPM = re.compile(r"([+-]\d+)fpm")

TYPES = {
    0: "inconnu", 1: "planeur", 2: "remorqueur", 3: "hélicoptère", 4: "parachutiste", 5: "largueur",
    6: "delta", 7: "parapente", 8: "avion", 9: "jet", 10: "ovni", 11: "ballon", 12: "dirigeable",
    13: "drone", 14: "planeur électrique", 15: "objet au sol",
}


def dist_km(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371.0088 * math.asin(math.sqrt(h))


def collect(user, flt, seconds, label, result):
    """Ouvre une connexion APRS-IS et note toutes les lignes reçues."""
    info = {"user": user, "filter": flt, "label": label, "lines": 0, "server": [], "frames": [], "error": None}
    try:
        s = socket.create_connection((HOST, PORT), timeout=30)
        s.settimeout(10)
        login = f"user {user} pass -1 vers GLIDY-scan 0.6" + (f" filter {flt}" if flt else "") + "\r\n"
        info["login"] = login.strip()
        s.sendall(login.encode())
        f = s.makefile("rb")
        end = time.time() + seconds
        while time.time() < end:
            try:
                raw = f.readline()
            except socket.timeout:
                continue
            if not raw:
                info["error"] = "connexion fermée par le serveur"
                break
            line = raw.decode(errors="replace").rstrip()
            info["lines"] += 1
            if line.startswith("#"):
                if len(info["server"]) < 12:
                    info["server"].append(line)
                continue
            info["frames"].append((time.time(), line))
        s.close()
    except Exception as e:  # noqa: BLE001 - diagnostic
        info["error"] = f"{type(e).__name__}: {e}"
    result[label] = info


def summarise(info, centre):
    """Aéronefs distincts, par type et par anneau de distance."""
    seen = {}
    for _, line in info["frames"]:
        m = POS.match(line)
        if not m:
            continue
        idm = ID.search(m.group("rest"))
        if not idm:
            continue
        flags = int(idm.group(1), 16)
        addr = idm.group(2).upper()
        lat = int(m.group("lat")[:2]) + float(m.group("lat")[2:]) / 60
        lon = int(m.group("lon")[:3]) + float(m.group("lon")[3:]) / 60
        if m.group("ns") == "S":
            lat = -lat
        if m.group("ew") == "W":
            lon = -lon
        alt_ft = float(m.group("alt")) if m.group("alt") else None
        spd = float(m.group("spd")) * 1.852 if m.group("spd") else 0.0
        fpm = FPM.search(m.group("rest"))
        a = seen.setdefault(addr, {
            "type": TYPES.get((flags >> 2) & 0x0F, "?"), "type_id": (flags >> 2) & 0x0F,
            "stealth": bool(flags & 0x80), "no_tracking": bool(flags & 0x40),
            "frames": 0, "alt_m": None, "speed_kmh": 0.0, "climb_ms": None, "pos": (lat, lon),
        })
        a["frames"] += 1
        a["pos"] = (lat, lon)
        a["speed_kmh"] = spd
        if alt_ft is not None:
            a["alt_m"] = round(alt_ft * 0.3048)
        if fpm:
            a["climb_ms"] = round(int(fpm.group(1)) * 0.3048 / 60, 1)
    for a in seen.values():
        a["dist_km"] = round(dist_km(centre, a["pos"]), 1)
        a["flying"] = a["speed_kmh"] > 20
    return seen


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--seconds", type=int, default=240)   # 4 min : une trame par aéronef même à faible cadence
    p.add_argument("--lat", type=float, default=43.80028)
    p.add_argument("--lon", type=float, default=3.78167)
    p.add_argument("--radius", type=int, default=300)
    p.add_argument("--app-user", default="GLIDY01234", help="indicatif tel que l'app le fabrique")
    p.add_argument("--out", default="out")
    args = p.parse_args()
    os.makedirs(args.out, exist_ok=True)
    centre = (args.lat, args.lon)
    flt = f"r/{args.lat:.4f}/{args.lon:.4f}/{args.radius}"
    app_flt = f"r/{args.lat:.4f}/{args.lon:.4f}/100"

    result = {}
    threads = [
        threading.Thread(target=collect, args=("GLIDY9042", flt, args.seconds, "scan", result)),
        threading.Thread(target=collect, args=(args.app_user, app_flt, args.seconds, "app", result)),
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    scan = result["scan"]
    app = result["app"]
    aircraft = summarise(scan, centre)
    flying = {k: v for k, v in aircraft.items() if v["flying"]}

    by_type = defaultdict(int)
    for a in flying.values():
        by_type[a["type"]] += 1
    rings = defaultdict(int)
    for a in flying.values():
        for r in (15, 50, 100, 200, args.radius):
            if a["dist_km"] <= r:
                rings[r] += 1

    report = {
        "utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "duree_s": args.seconds,
        "centre": {"lat": args.lat, "lon": args.lon},
        "rayon_km": args.radius,
        "scan": {
            "lignes": scan["lines"], "trames": len(scan["frames"]), "erreur": scan["error"],
            "login": scan.get("login"), "serveur": scan["server"][:4],
        },
        "app": {
            "lignes": app["lines"], "trames": len(app["frames"]), "erreur": app["error"],
            "login": app.get("login"), "serveur": app["server"][:4],
            "aeronefs_en_vol": len([a for a in summarise(app, centre).values() if a["flying"]]),
        },
        "aeronefs_vus": len(aircraft),
        "aeronefs_en_vol": len(flying),
        "par_type": dict(sorted(by_type.items(), key=lambda kv: -kv[1])),
        "par_distance_km": {str(k): rings[k] for k in sorted(rings)},
        "furtifs_ou_no_tracking": len([a for a in aircraft.values() if a["stealth"] or a["no_tracking"]]),
        "exemples": sorted(
            [
                {"type": a["type"], "dist_km": a["dist_km"], "alt_m": a["alt_m"], "vitesse_kmh": round(a["speed_kmh"]),
                 "montee_ms": a["climb_ms"], "trames": a["frames"]}
                for a in flying.values()
            ],
            key=lambda a: a["dist_km"],
        )[:25],
    }
    with open(os.path.join(args.out, "scan.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    print(json.dumps(report, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
