#!/usr/bin/env python3
"""Enregistre le flux OGN (APRS-IS), mesure cadence et latence, et produit un extrait anonymisé pour les tests.

Règles OGN respectées :
  * le flux brut n'est jamais publié dans le dépôt (artefact GitHub conservé 1 jour) ;
  * l'extrait publié est anonymisé : identifiants remplacés, récepteurs masqués, heures rebasées,
    trajectoires déplacées autour de LFNL (la forme des vols est conservée, pas leur lieu ni leur auteur) ;
  * les aéronefs en mode furtif ou « no-tracking » sont écartés.

usage: record.py --seconds 600 [--filter "r/44/4/600"] --out out
"""
import argparse, collections, hashlib, json, math, os, random, re, socket, statistics, time

HOST = "aprs.glidernet.org"
POS = re.compile(
    r"^(?P<call>[A-Z0-9]{3,9})>(?P<dst>[A-Z0-9]+),(?P<path>[^:]*):[/@](?P<hh>\d{2})(?P<mm>\d{2})(?P<ss>\d{2})h"
    r"(?P<lat>\d{4}\.\d{2})(?P<ns>[NS]).(?P<lon>\d{5}\.\d{2})(?P<ew>[EW]).(?:(?P<crs>\d{3})/(?P<spd>\d{3}))?"
    r"(?:/A=(?P<alt>-?\d{6}))?(?P<rest>.*)$")
ID = re.compile(r"\bid([0-9A-F]{2})([0-9A-F]{6})\b")
FPM = re.compile(r"([+-]\d+)fpm")
ROT = re.compile(r"([+-]\d+\.\d)rot")
PREC = re.compile(r"!W(\d)(\d)!")


def record(seconds, flt, raw_path):
    port = 14580 if flt else 10152
    s = socket.create_connection((HOST, port), timeout=30)
    login = f"user GLIDY{random.randint(1000, 9999)} pass -1 vers GLIDY-recorder 0.4" + (f" filter {flt}" if flt else "") + "\n"
    s.sendall(login.encode())
    f = s.makefile("rb")
    end = time.time() + seconds
    last_keep = time.time()
    n = 0
    with open(raw_path, "w", encoding="utf-8") as out:
        while time.time() < end:
            if time.time() - last_keep > 200:
                s.sendall(b"#keepalive\n")
                last_keep = time.time()
            line = f.readline()
            if not line:
                break
            out.write(f"{int(time.time() * 1000)}\t{line.decode(errors='replace').rstrip()}\n")
            n += 1
    s.close()
    return n


def parse(line):
    m = POS.match(line)
    if not m:
        return None
    rest = m.group("rest")
    idm = ID.search(rest)
    if not idm:
        return None
    flags = int(idm.group(1), 16)
    lat = int(m.group("lat")[:2]) + float(m.group("lat")[2:]) / 60
    lon = int(m.group("lon")[:3]) + float(m.group("lon")[3:]) / 60
    p = PREC.search(rest)
    if p:
        lat += int(p.group(1)) * 0.001 / 60
        lon += int(p.group(2)) * 0.001 / 60
    if m.group("ns") == "S": lat = -lat
    if m.group("ew") == "W": lon = -lon
    fpm = FPM.search(rest)
    rot = ROT.search(rest)
    return {
        "call": m.group("call"), "dst": m.group("dst"), "path": m.group("path"),
        "t": int(m.group("hh")) * 3600 + int(m.group("mm")) * 60 + int(m.group("ss")),
        "lat": lat, "lon": lon,
        "trk": int(m.group("crs")) if m.group("crs") else None, "gs": int(m.group("spd")) if m.group("spd") else None,
        "alt": int(m.group("alt")) if m.group("alt") else None,
        "stealth": bool(flags & 0x80), "notrack": bool(flags & 0x40), "type": (flags >> 2) & 0x0F, "addrtype": flags & 0x03,
        "addr": idm.group(2), "fpm": int(fpm.group(1)) if fpm else None, "rot": float(rot.group(1)) if rot else None,
        "raw": line,
    }


def pct(values, q):
    if not values:
        return None
    v = sorted(values)
    return v[min(len(v) - 1, int(q * (len(v) - 1)))]


def analyse(raw_path, out):
    by_ac = collections.defaultdict(list)
    lines = beacons = receivers = other = 0
    dst = collections.Counter()
    for row in open(raw_path, encoding="utf-8"):
        recv_ms, _, line = row.rstrip("\n").partition("\t")
        lines += 1
        if line.startswith("#"):
            continue
        b = parse(line)
        if b is None:
            if ">OGNSDR" in line or "TCPIP*" in line:
                receivers += 1
            else:
                other += 1
            continue
        beacons += 1
        dst[b["dst"]] += 1
        b["recv"] = int(recv_ms) / 1000.0
        by_ac[b["addr"]].append(b)

    intervals, latencies, climb_known, rot_known = [], [], 0, 0
    circlers = []
    hidden = 0
    types = collections.Counter()
    for addr, seq in by_ac.items():
        seq.sort(key=lambda x: x["recv"])
        if seq[0]["stealth"] or seq[0]["notrack"]:
            hidden += 1
            continue
        types[seq[0]["type"]] += 1
        prev_t = None
        for b in seq:
            day = b["recv"] % 86400
            lat = (day - b["t"] + 43200) % 86400 - 43200
            if -60 < lat < 600:
                latencies.append(lat)
            if prev_t is not None and b["t"] != prev_t:
                d = (b["t"] - prev_t) % 86400
                if 0 < d < 600:
                    intervals.append(d)
            prev_t = b["t"]
            climb_known += b["fpm"] is not None
            rot_known += b["rot"] is not None
        turning = [b for b in seq if b["rot"] is not None and abs(b["rot"]) >= 2.0 and (b["gs"] or 0) > 20]
        if len(turning) >= 6:
            circlers.append((len(turning), addr))

    stats = {
        "lines": lines, "aircraft_beacons": beacons, "receiver_or_status": receivers, "other": other,
        "aircraft": len(by_ac), "hidden_stealth_or_notrack": hidden, "types": dict(types), "dstcalls": dict(dst.most_common(12)),
        "interval_s": {"n": len(intervals), "p10": pct(intervals, .1), "median": pct(intervals, .5), "p90": pct(intervals, .9), "mean": round(statistics.mean(intervals), 2) if intervals else None},
        "latency_s": {"n": len(latencies), "p10": pct(latencies, .1), "median": pct(latencies, .5), "p90": pct(latencies, .9)},
        "climb_field_ratio": round(climb_known / beacons, 3) if beacons else None,
        "turn_field_ratio": round(rot_known / beacons, 3) if beacons else None,
        "circling_aircraft": len(circlers),
    }
    with open(os.path.join(out, "stats.json"), "w") as f:
        json.dump(stats, f, indent=1)
    print(json.dumps(stats, indent=1))

    # extrait anonymisé : les aéronefs qui spiralent le plus + quelques trajectoires rectilignes
    circlers.sort(reverse=True)
    chosen = [a for _, a in circlers[:6]]
    straight = sorted(((len(s), a) for a, s in by_ac.items() if a not in chosen and len(s) >= 20 and not s[0]["stealth"] and not s[0]["notrack"]), reverse=True)
    chosen += [a for _, a in straight[:4]]
    rnd = random.Random(42)
    center = (43.80028, 3.78167)   # LFNL
    lines_out = []
    t0 = min((b["t"] for a in chosen for b in by_ac[a]), default=0)
    for i, addr in enumerate(chosen):
        seq = by_ac[addr]
        fake = hashlib.sha1(("glidy" + addr).encode()).hexdigest()[:6].upper()
        # déplacement : premier point à 2–20 km de LFNL, forme conservée
        ang = rnd.uniform(0, 2 * math.pi); dist = rnd.uniform(2, 20)
        tlat = center[0] + dist * math.cos(ang) / 111.32
        tlon = center[1] + dist * math.sin(ang) / (111.32 * math.cos(math.radians(center[0])))
        dlat = tlat - seq[0]["lat"]
        dlon_scale = math.cos(math.radians(seq[0]["lat"])) / math.cos(math.radians(tlat))
        alt_shift = 0
        if seq[0]["alt"] is not None:
            alt_shift = 4000 - min(b["alt"] for b in seq if b["alt"] is not None)   # plancher ramené vers 1 200 m
        for b in seq:
            lat = b["lat"] + dlat
            lon = tlon + (b["lon"] - seq[0]["lon"]) * dlon_scale
            t = (b["t"] - t0) % 86400 + 12 * 3600
            hh, mm, ss = t // 3600 % 24, t // 60 % 60, t % 60
            la = abs(lat); lo = abs(lon)
            lat_s = f"{int(la):02d}{(la - int(la)) * 60:05.2f}{'N' if lat >= 0 else 'S'}"
            lon_s = f"{int(lo):03d}{(lo - int(lo)) * 60:05.2f}{'E' if lon >= 0 else 'W'}"
            flags = (b["type"] << 2) | b["addrtype"]
            crs = f"{b['trk'] or 0:03d}/{b['gs'] or 0:03d}"
            alt = f"/A={max(0, (b['alt'] or 0) + alt_shift):06d}"
            extra = []
            if b["fpm"] is not None: extra.append(f"{b['fpm']:+04d}fpm")
            if b["rot"] is not None: extra.append(f"{b['rot']:+.1f}rot")
            prefix = {1: "ICA", 2: "FLR", 3: "OGN"}.get(b["addrtype"], "RND")
            lines_out.append((t, f"{prefix}{fake}>OGFLR,qAS,RCVR{i:02d}:/{hh:02d}{mm:02d}{ss:02d}h{lat_s}/{lon_s}'{crs}{alt} id{flags:02X}{fake} {' '.join(extra)}"))
    lines_out.sort()
    with open(os.path.join(out, "excerpt_anon.aprs"), "w") as f:
        f.write("# extrait anonymisé GLIDY : identifiants, récepteurs, heures et lieux modifiés — données dérivées d'OGN (ODbL)\n")
        for _, l in lines_out:
            f.write(l + "\n")
    print("extrait :", len(lines_out), "trames,", len(chosen), "aéronefs dont", min(6, len(circlers)), "en spirale")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--seconds", type=int, default=600)
    ap.add_argument("--filter", default="")
    ap.add_argument("--out", default="out")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    raw = os.path.join(a.out, "raw.log")
    n = record(a.seconds, a.filter, raw)
    print("lignes reçues", n)
    analyse(raw, a.out)
