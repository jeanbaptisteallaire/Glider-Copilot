#!/usr/bin/env python3
"""Vol de démonstration GLIDY (banc de rejeu S5).

Compose une trace IGC 1 Hz : attente au sol à LFNL, remorqué, puis la trace réelle anonymisée du planeur
« 179719 » de l'extrait OGN (app/src/main/assets/ogn/replay.aprs, déjà déplacé autour de LFNL), retour
en plané, tour de piste descendant, atterrissage et roulage. Les heures commencent à 00:00:00 : l'app les
ramène au présent au lancement du rejeu.
"""
import math, re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
APRS = ROOT / "app/src/main/assets/ogn/replay.aprs"
OUT = ROOT / "app/src/main/assets/flight/demo.igc"
FIELD = (43.80028, 3.78167)
FIELD_ELEV = 183.0
QNH_OFFSET = 35.0        # altitude pression ISA − altitude : journée anticyclonique (~1017 hPa)
R = 6371008.8

def dest(p, brg, d):
    la, lo = map(math.radians, p); b = math.radians(brg); dd = d / R
    la2 = math.asin(math.sin(la) * math.cos(dd) + math.cos(la) * math.sin(dd) * math.cos(b))
    lo2 = lo + math.atan2(math.sin(b) * math.sin(dd) * math.cos(la), math.cos(dd) - math.sin(la) * math.sin(la2))
    return (math.degrees(la2), math.degrees(lo2))

def dist(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (*a, *b))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))

def brg(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (*a, *b))
    y = math.sin(lo2 - lo1) * math.cos(la2)
    x = math.cos(la1) * math.sin(la2) - math.sin(la1) * math.cos(la2) * math.cos(lo2 - lo1)
    return (math.degrees(math.atan2(y, x)) + 360) % 360

def ogn_track(addr="179719"):
    rx = re.compile(r":/(\d{2})(\d{2})(\d{2})h(\d{2})(\d{2}\.\d{2})([NS]).(\d{3})(\d{2}\.\d{2})([EW]).*?/A=(-?\d+)")
    pts = []
    for line in APRS.read_text().splitlines():
        if addr not in line.split(">")[0]:
            continue
        m = rx.search(line)
        if not m:
            continue
        g = m.groups()
        t = int(g[0]) * 3600 + int(g[1]) * 60 + int(g[2])
        lat = (int(g[3]) + float(g[4]) / 60) * (1 if g[5] == "N" else -1)
        lon = (int(g[6]) + float(g[7]) / 60) * (1 if g[8] == "E" else -1)
        alt = int(g[9]) * 0.3048
        if pts and t <= pts[-1][0]:
            continue
        pts.append((t, lat, lon, alt))
    # interpolation 1 Hz
    out = []
    for (t0, la0, lo0, a0), (t1, la1, lo1, a1) in zip(pts, pts[1:]):
        for t in range(t0, t1):
            k = (t - t0) / (t1 - t0)
            out.append(((la0 + k * (la1 - la0), lo0 + k * (lo1 - lo0)), a0 + k * (a1 - a0)))
    out.append(((pts[-1][1], pts[-1][2]), pts[-1][3]))
    return out

def main():
    fixes = []           # (position, altitude)
    pos, alt = FIELD, FIELD_ELEV
    def hold(n):
        for _ in range(n):
            fixes.append((pos, alt))
    def fly(heading, speed_ms, climb_ms, n):
        nonlocal pos, alt
        for _ in range(n):
            pos = dest(pos, heading, speed_ms); alt = max(FIELD_ELEV, alt + climb_ms)
            fixes.append((pos, alt))

    thermal = ogn_track()
    start_pos, start_alt = thermal[0]

    hold(60)                                                   # prévol, verrière fermée
    for i in range(25):                                        # roulage au décollage : 0 → 110 km/h
        v = 30.5 * (i + 1) / 25
        pos = dest(pos, 330, v); fixes.append((pos, alt))
    # remorqué : montée à 3 m/s, 110 km/h, trajet coudé pour arriver au point de départ de la pompe à son altitude
    climb = 3.0; speed = 30.5
    n = int(math.ceil((start_alt - alt) / climb))
    d_total = speed * n
    d_direct = dist(pos, start_pos)
    off = math.sqrt(max(0.0, (d_total / 2) ** 2 - (d_direct / 2) ** 2))
    mid = dest(dest(pos, brg(pos, start_pos), d_direct / 2), brg(pos, start_pos) - 90, off)
    n1 = n // 2
    c = (start_alt - alt) / n
    fly(brg(pos, mid), dist(pos, mid) / n1, c, n1)
    fly(brg(pos, start_pos), dist(pos, start_pos) / (n - n1), c, n - n1)
    # largage puis trace réelle (spirales, transitions)
    for p, a in thermal:
        pos, alt = p, a
        fixes.append((pos, alt))
    # retour en plané vers la verticale terrain : 105 km/h, −1,1 m/s
    while dist(pos, FIELD) > 800:
        fly(brg(pos, FIELD), 29.0, -1.1, 1)
    # descente en orbite au-dessus du terrain (aérofreins) jusqu'à 330 m sol
    heading = brg(pos, FIELD)
    while alt > FIELD_ELEV + 330:
        heading = (heading + 360 / 45) % 360
        fly(heading, 25.0, -2.5, 1)
    # vent arrière, étape de base, finale face au 330, arrondi
    fly(150, 26.0, -1.2, 60)
    fly(240, 25.0, -1.5, 25)
    final = int((alt - FIELD_ELEV) / 1.6)
    fly(330, 24.0, -1.6, final)
    for i in range(22):                                        # roulage : 85 → 0 km/h
        v = 23.5 * (1 - (i + 1) / 22)
        pos = dest(pos, 330, v); alt = FIELD_ELEV
        fixes.append((pos, alt))
    hold(90)

    lines = ["AXXXGLYGLIDY", "HFDTEDATE:010126,01", "HFPLTPILOTINCHARGE:DEMO", "HFGTYGLIDERTYPE:ASK 21",
             "HFGIDGLIDERID:F-DEMO", "HFDTMGPSDATUM:WGS84", "HFALGALTGPS:GEO", "HFALPALTPRESSURE:ISA",
             "LXXXGLIDY vol de demonstration compose : trace OGN anonymisee (ODbL), remorque et retour simules"]
    for s, ((la, lo), a) in enumerate(fixes):
        hh, mm, ss = s // 3600, s // 60 % 60, s % 60
        def dm(v, w):
            d = int(abs(v)); m = round((abs(v) - d) * 60000)
            if m == 60000: d, m = d + 1, 0
            return f"{d:0{w}d}{m:05d}"
        lines.append(f"B{hh:02d}{mm:02d}{ss:02d}{dm(la, 2)}{'N' if la >= 0 else 'S'}{dm(lo, 3)}{'E' if lo >= 0 else 'W'}A"
                     f"{round(a + QNH_OFFSET):05d}{round(a):05d}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\r\n".join(lines) + "\r\n")
    print(f"{OUT.relative_to(ROOT)} : décollage 60 s, largage {60 + 25 + n} s, fin trace OGN {60 + 25 + n + len(thermal)} s, total {len(fixes)} s", file=sys.stderr)

if __name__ == "__main__":
    main()
