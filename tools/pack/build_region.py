#!/usr/bin/env python3
"""Fabrique un pack de carte hors ligne GLIDY pour une région.

Sorties dans OUT/<id>/ :
  <id>-<version>-fond.pmtiles     fond OpenStreetMap vectoriel (extrait Protomaps, ODbL)
  <id>-<version>-relief.pmtiles   modèle numérique de terrain Copernicus GLO-30 encodé Terrarium (tuiles 512 px)
  <id>-<version>-courbes.pmtiles  courbes de niveau tous les 100 m (couche « contours », attribut « ele »)
  <id>-<version>-aero.geojson     espaces aériens, terrains, balises, points de report openAIP (propriétés aplaties)
  <id>-manifest.json    version, emprise, validité des données aéro, tailles et SHA-256

Dépendances : gdal (python3-gdal, gdal-bin), numpy, tippecanoe, pmtiles (go-pmtiles).
La clé openAIP est lue dans OPENAIP_KEY et n'est jamais écrite.
"""
import argparse, datetime as dt, glob, hashlib, json, math, os, shutil, subprocess, sys, time, urllib.error, urllib.parse, urllib.request

UA = "GLIDY-pack-builder/1.0 (+https://github.com/jeanbaptisteallaire/Glider-Copilot)"
AERO_VALIDITY_DAYS = 28   # un cycle AIRAC : au-delà, l'app signale des données aéro à rafraîchir
BASEMAP_MAXZOOM = 12
DEM_MAXZOOM = 10          # tuiles 512 px : résolution effective ≈ 76 m (ombrage et coupe), pack léger
CONTOUR_STEP = 100


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def run(cmd, **kw):
    log("$", " ".join(cmd) if isinstance(cmd, list) else cmd)
    subprocess.run(cmd, check=True, **kw)


def http_get(url, headers=None, timeout=120, retries=6, pause=40):
    last = None
    for attempt in range(retries):
        req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.status, r.read()
        except urllib.error.HTTPError as e:
            last = e
            if e.code == 404:
                return 404, b""
            if e.code in (429, 500, 502, 503, 504):
                wait = pause * (attempt + 1)
                log(f"HTTP {e.code} sur {url.split('?')[0]} : nouvel essai dans {wait} s")
                time.sleep(wait)
                continue
            raise
        except (urllib.error.URLError, TimeoutError) as e:
            last = e
            time.sleep(10)
    raise RuntimeError(f"échec {url.split('?')[0]} : {last}")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


# ------------------------------------------------------------------ fond de carte

def latest_protomaps_build():
    status, body = http_get("https://build-metadata.protomaps.dev/builds.json")
    builds = sorted(json.loads(body), key=lambda b: b["key"])
    return builds[-1]["key"]


def build_basemap(bbox, out):
    key = latest_protomaps_build()
    run(["pmtiles", "extract", f"https://build.protomaps.com/{key}", out,
         f"--bbox={bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}", f"--maxzoom={BASEMAP_MAXZOOM}", "--download-threads=4"])
    return key


# ------------------------------------------------------------------ relief

def cop30_tiles(bbox, work):
    files = []
    for lat in range(math.floor(bbox[1]), math.ceil(bbox[3])):
        for lon in range(math.floor(bbox[0]), math.ceil(bbox[2])):
            ns = f"N{lat:02d}" if lat >= 0 else f"S{-lat:02d}"
            ew = f"E{lon:03d}" if lon >= 0 else f"W{-lon:03d}"
            name = f"Copernicus_DSM_COG_10_{ns}_00_{ew}_00_DEM"
            dest = os.path.join(work, name + ".tif")
            if not os.path.exists(dest):
                status, body = http_get(f"https://copernicus-dem-30m.s3.amazonaws.com/{name}/{name}.tif", timeout=300)
                if status == 404:
                    log("pas de tuile (mer)", name)
                    continue
                with open(dest, "wb") as f:
                    f.write(body)
            files.append(dest)
    return files


def build_relief(bbox, work, out_relief, out_contours):
    from osgeo import gdal
    import numpy as np
    gdal.UseExceptions()
    tiles = cop30_tiles(bbox, work)
    if not tiles:
        raise RuntimeError("aucune tuile Copernicus")
    vrt = os.path.join(work, "dem.vrt")
    gdal.BuildVRT(vrt, tiles)
    clip = os.path.join(work, "dem_4326.tif")
    gdal.Translate(clip, vrt, projWin=[bbox[0], bbox[3], bbox[2], bbox[1]], creationOptions=["COMPRESS=DEFLATE", "TILED=YES"])

    # MNT en Web Mercator à la résolution du niveau DEM_MAXZOOM (512 px), puis encodage Terrarium
    res = 40075016.685578488 / (512 * 2 ** DEM_MAXZOOM)
    merc = os.path.join(work, "dem_3857.tif")
    gdal.Warp(merc, clip, dstSRS="EPSG:3857", xRes=res, yRes=res, targetAlignedPixels=True,
              resampleAlg="bilinear", outputType=gdal.GDT_Float32, dstNodata=-32768,
              creationOptions=["COMPRESS=DEFLATE", "TILED=YES", "BIGTIFF=IF_SAFER"])
    src = gdal.Open(merc)
    h = src.GetRasterBand(1).ReadAsArray().astype("float32")
    h[(h < -500) | ~np.isfinite(h)] = 0.0
    # altitude arrondie au mètre : le canal bleu (fraction) reste à 0 et le PNG se compresse bien mieux
    v = np.rint(h) + 32768.0
    r = np.floor(v / 256.0)
    g = v - r * 256.0
    b = np.zeros_like(v)
    rgb_path = os.path.join(work, "terrarium.tif")
    drv = gdal.GetDriverByName("GTiff")
    dst = drv.Create(rgb_path, src.RasterXSize, src.RasterYSize, 3, gdal.GDT_Byte, ["COMPRESS=DEFLATE", "TILED=YES", "PHOTOMETRIC=RGB"])
    dst.SetGeoTransform(src.GetGeoTransform())
    dst.SetProjection(src.GetProjection())
    for i, band in enumerate((r, g, b), start=1):
        dst.GetRasterBand(i).WriteArray(band.astype("uint8"))
    dst = None
    h = v = r = g = b = None

    mb = os.path.join(work, "relief.mbtiles")
    if os.path.exists(mb):
        os.remove(mb)
    gdal.Translate(mb, rgb_path, format="MBTiles",
                   creationOptions=["TILE_FORMAT=PNG", "BLOCKSIZE=512", "RESAMPLING=NEAREST", "ZOOM_LEVEL_STRATEGY=UPPER", "NAME=relief"])
    ds = gdal.Open(mb, gdal.GA_Update)
    levels = [2 ** i for i in range(1, DEM_MAXZOOM - 5)]   # jusqu'au niveau 6
    ds.BuildOverviews("NEAREST", levels)
    ds = None
    run(["pmtiles", "convert", mb, out_relief])

    # courbes de niveau depuis un MNT allégé (~90 m) pour garder un pack léger
    light = os.path.join(work, "dem_light.tif")
    gdal.Warp(light, clip, xRes=0.0008, yRes=0.0008, resampleAlg="average")
    geojson = os.path.join(work, "contours.geojson")
    if os.path.exists(geojson):
        os.remove(geojson)
    run(["gdal_contour", "-a", "ele", "-i", str(CONTOUR_STEP), "-f", "GeoJSON", light, geojson])
    run(["tippecanoe", "-o", out_contours, "--force", "-l", "contours", "-Z9", "-z12", "-y", "ele",
         "--simplification=4", "--drop-densest-as-needed", "--no-tile-size-limit", geojson])


# ------------------------------------------------------------------ openAIP

def ring_bbox(coords):
    xs = [p[0] for ring in coords for p in ring]
    ys = [p[1] for ring in coords for p in ring]
    return min(xs), min(ys), max(xs), max(ys)


def intersects(b, bbox):
    return not (b[2] < bbox[0] or b[0] > bbox[2] or b[3] < bbox[1] or b[1] > bbox[3])


def fetch_openaip(endpoint, bbox, key):
    lon = (bbox[0] + bbox[2]) / 2
    lat = (bbox[1] + bbox[3]) / 2
    half_w = (bbox[2] - bbox[0]) / 2 * 111.32 * math.cos(math.radians(lat))
    half_h = (bbox[3] - bbox[1]) / 2 * 111.32
    dist_m = int(math.hypot(half_w, half_h) * 1000) + 20000
    items, page = [], 1
    while True:
        q = urllib.parse.urlencode({"pos": f"{lat:.5f},{lon:.5f}", "dist": dist_m, "limit": 500, "page": page})
        time.sleep(12)   # openAIP limite fortement le débit (429)
        status, body = http_get(f"https://api.core.openaip.net/api/{endpoint}?{q}", headers={"x-openaip-api-key": key, "Accept": "application/json"})
        data = json.loads(body)
        items.extend(data.get("items", []))
        log(f"openAIP {endpoint} page {page} : {len(data.get('items', []))} (total {data.get('totalCount')})")
        if not data.get("nextPage"):
            break
        page = data["nextPage"]
    return items


def limit_props(prefix, lim):
    lim = lim or {}
    return {f"{prefix}_v": lim.get("value"), f"{prefix}_u": lim.get("unit"), f"{prefix}_r": lim.get("referenceDatum")}


def build_aero(bbox, out):
    key = os.environ.get("OPENAIP_KEY", "")
    if not key:
        raise RuntimeError("OPENAIP_KEY absent")
    features = []
    for a in fetch_openaip("airspaces", bbox, key):
        geom = a.get("geometry") or {}
        if geom.get("type") != "Polygon" or not intersects(ring_bbox(geom["coordinates"]), bbox):
            continue
        if a.get("type") in (10, 11, 27):   # FIR, UIR, secteurs ACC : sans intérêt sur la carte vélivole
            continue
        p = {"layer": "airspace", "id": a["_id"], "name": a.get("name"), "type": a.get("type"), "cls": a.get("icaoClass"),
             "notam": bool(a.get("byNotam")), "ondemand": bool(a.get("onDemand") or a.get("onRequest"))}
        p.update(limit_props("lo", a.get("lowerLimit")))
        p.update(limit_props("up", a.get("upperLimit")))
        features.append({"type": "Feature", "geometry": geom, "properties": p})
    for a in fetch_openaip("airports", bbox, key):
        c = (a.get("geometry") or {}).get("coordinates")
        if not c or not intersects((c[0], c[1], c[0], c[1]), bbox):
            continue
        freqs = a.get("frequencies") or []
        primary = next((f for f in freqs if f.get("primary")), freqs[0] if freqs else None)
        rwys = a.get("runways") or []
        main = next((r for r in rwys if r.get("mainRunway")), rwys[0] if rwys else None)
        elev = a.get("elevation") or {}
        elev_m = elev.get("value")
        if elev_m is not None and elev.get("unit") == 1:
            elev_m = round(elev_m * 0.3048)
        features.append({"type": "Feature", "geometry": a["geometry"], "properties": {
            "layer": "airport", "id": a["_id"], "name": a.get("name"), "icao": a.get("icaoCode"), "type": a.get("type"),
            "elev": elev_m, "freq": primary.get("value") if primary else None,
            "rwy": main.get("designator") if main else None, "hdg": main.get("trueHeading") if main else None,
            "len": ((main or {}).get("dimension") or {}).get("length", {}).get("value") if main else None}})
    for a in fetch_openaip("navaids", bbox, key):
        c = (a.get("geometry") or {}).get("coordinates")
        if not c or not intersects((c[0], c[1], c[0], c[1]), bbox):
            continue
        f = a.get("frequency") or {}
        features.append({"type": "Feature", "geometry": a["geometry"], "properties": {
            "layer": "navaid", "id": a["_id"], "name": a.get("name"), "ident": a.get("identifier"), "type": a.get("type"), "freq": f.get("value")}})
    try:
        for a in fetch_openaip("reporting-points", bbox, key):
            c = (a.get("geometry") or {}).get("coordinates")
            if not c or not intersects((c[0], c[1], c[0], c[1]), bbox):
                continue
            features.append({"type": "Feature", "geometry": a["geometry"], "properties": {
                "layer": "reporting", "id": a["_id"], "name": a.get("name"), "compulsory": bool(a.get("compulsory"))}})
    except RuntimeError as e:
        log("points de report ignorés :", e)
    fetched = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
    fc = {"type": "FeatureCollection", "fetched": fetched.isoformat().replace("+00:00", "Z"),
          "attribution": "openAIP (CC BY-NC 4.0)", "features": features}
    with open(out, "w", encoding="utf-8") as f:
        json.dump(fc, f, ensure_ascii=False, separators=(",", ":"))
    counts = {}
    for ft in features:
        counts[ft["properties"]["layer"]] = counts.get(ft["properties"]["layer"], 0) + 1
    log("aéro", counts)
    return fetched, counts


# ------------------------------------------------------------------ assemblage

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--region", required=True)
    ap.add_argument("--out", default="out")
    ap.add_argument("--work", default="work")
    ap.add_argument("--base-url", required=True, help="URL de téléchargement des fichiers publiés")
    ap.add_argument("--skip", default="", help="étapes à sauter : fond,relief,aero (tests)")
    args = ap.parse_args()
    regions = json.load(open(os.path.join(os.path.dirname(__file__), "..", "..", "packs", "regions.json"), encoding="utf-8"))["regions"]
    region = next(r for r in regions if r["id"] == args.region)
    rid, bbox = region["id"], region["bbox"]
    out = os.path.join(args.out, rid)
    work = os.path.join(args.work, rid)
    os.makedirs(out, exist_ok=True)
    os.makedirs(work, exist_ok=True)
    skip = set(filter(None, args.skip.split(",")))

    # noms versionnés : un téléphone qui télécharge l'ancienne version n'est jamais pris à revers par une publication
    version = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d%H%M")
    files = {role: f"{rid}-{version}-{role}.{'geojson' if role == 'aero' else 'pmtiles'}" for role in ("fond", "relief", "courbes", "aero")}
    sources = {}
    if "fond" not in skip:
        sources["protomaps"] = build_basemap(bbox, os.path.join(out, files["fond"]))
    if "relief" not in skip:
        build_relief(bbox, work, os.path.join(out, files["relief"]), os.path.join(out, files["courbes"]))
        sources["copernicus"] = "GLO-30 (AWS Open Data)"
    fetched, counts = (None, {})
    if "aero" not in skip:
        fetched, counts = build_aero(bbox, os.path.join(out, files["aero"]))
        sources["openaip"] = fetched.isoformat().replace("+00:00", "Z")

    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
    manifest = {
        "format": 1,
        "id": rid,
        "name": region["name"],
        "version": version,
        "created": now.isoformat().replace("+00:00", "Z"),
        "bbox": bbox,
        "basemapMaxZoom": BASEMAP_MAXZOOM,
        "demMaxZoom": DEM_MAXZOOM,
        "demEncoding": "terrarium",
        "demTileSize": 512,
        "contourStep": CONTOUR_STEP,
        "aeroValidUntil": ((fetched or now) + dt.timedelta(days=AERO_VALIDITY_DAYS)).isoformat().replace("+00:00", "Z"),
        "aeroCounts": counts,
        "sources": sources,
        "attribution": "© OpenStreetMap contributors (ODbL) · Protomaps · openAIP (CC BY-NC 4.0) · Copernicus DEM GLO-30 © DLR, Airbus, ESA/Union européenne",
        "files": [],
    }
    for role, name in files.items():
        path = os.path.join(out, name)
        if os.path.exists(path):
            manifest["files"].append({"role": role, "name": name, "url": f"{args.base_url.rstrip('/')}/{name}",
                                      "size": os.path.getsize(path), "sha256": sha256(path)})
    with open(os.path.join(out, f"{rid}-manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    key = os.environ.get("OPENAIP_KEY")
    if key:
        for p in glob.glob(os.path.join(out, "*.json")) + glob.glob(os.path.join(out, "*.geojson")):
            if key in open(p, encoding="utf-8", errors="ignore").read():
                raise RuntimeError("clé openAIP trouvée dans " + p)
    log("pack prêt", rid, {f["role"]: f["size"] for f in manifest["files"]})


if __name__ == "__main__":
    main()
