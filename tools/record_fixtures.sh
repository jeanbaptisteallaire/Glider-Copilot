#!/usr/bin/env bash
# Enregistre de vraies réponses d'API pour les tests hors ligne.
set -uo pipefail
OUT=${1:-fixtures}
mkdir -p "$OUT/precog" "$OUT/openaip"
LAT=43.80028; LON=3.78167   # LFNL, CVV Montpellier Pic Saint-Loup
B=https://precog-api.com/v1
get() { # name url [extra curl args]
  local name=$1 url=$2; shift 2
  local code
  code=$(curl -sS -m 90 -D "$OUT/$name.headers" -o "$OUT/$name.json" -w '%{http_code}' "$@" "$url") || code=ERR
  echo "$name $code $(stat -c %s "$OUT/$name.json" 2>/dev/null) $url" | tee -a "$OUT/index.txt"
}
get precog/health "$B/health"
get precog/arpege_runs_latest "$B/arpege/runs/latest"
get precog/arpege_forecast "$B/arpege/forecast?lat=$LAT&lon=$LON"
get precog/arome_forecast "$B/arome/forecast?lat=$LAT&lon=$LON"
get precog/aromepi_forecast "$B/aromepi/forecast?lat=$LAT&lon=$LON"
get precog/arpege_fields "$B/arpege/fields"
get precog/vigilance "$B/vigilance"
get precog/stations "$B/observations/stations?lat=$LAT&lon=$LON&limit=5"
get precog/phealth "$B/phealth/forecast?lat=$LAT&lon=$LON"
get precog/mtg_flashes "$B/mtg-li/flashes?limit=5"
# profils : 3 échéances de la journée
for T in $(python3 - "$OUT/precog/arpege_forecast.json" <<'PY'
import json,sys,datetime
d=json.load(open(sys.argv[1]))
steps=[s['valid_time'] for s in d.get('steps',[])]
out=[]
for s in steps:
    t=datetime.datetime.fromisoformat(s.replace('Z','+00:00'))
    if t.hour in (9,12,15) and len(out)<3: out.append(s)
print(' '.join(out))
PY
); do
  H=$(echo "$T" | cut -c12-13)
  get "precog/arpege_profile_${H}Z" "$B/arpege/profile?lat=$LAT&lon=$LON&at=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$T")"
done
ST=$(python3 -c "import json;d=json.load(open('$OUT/precog/stations.json'));s=d.get('stations') or d.get('items') or [];print(s[0].get('station_id') or s[0].get('id'))" 2>/dev/null)
[ -n "$ST" ] && get precog/station_series "$B/observations/stations/$ST/series?frequency=6m&hours=3"
# revalidation ETag
ET=$(grep -i '^etag:' "$OUT/precog/arpege_runs_latest.headers" | cut -d' ' -f2- | tr -d '\r')
[ -n "$ET" ] && get precog/arpege_runs_latest_304 "$B/arpege/runs/latest" -H "If-None-Match: $ET"
if [ -n "${OPENAIP_KEY:-}" ]; then
  get openaip/airports_lfnl "https://api.core.openaip.net/api/airports?pos=$LAT,$LON&dist=60000&limit=50" -H "x-openaip-api-key: $OPENAIP_KEY"
  get openaip/airspaces_lfnl "https://api.core.openaip.net/api/airspaces?pos=$LAT,$LON&dist=60000&limit=50" -H "x-openaip-api-key: $OPENAIP_KEY"
else
  echo "openaip SKIP (pas de secret OPENAIP_KEY)" | tee -a "$OUT/index.txt"
fi
# OGN Device Database : échantillon des planeurs français (F-C…) pour les tests d'appairage.
mkdir -p "$OUT/ogn"
get ogn/ddb_full "https://ddb.glidernet.org/download/?j=1&t=1"
python3 - "$OUT/ogn/ddb_full.json" "$OUT/ogn/ddb_fc_sample.json" <<'PY2' | tee -a "$OUT/index.txt"
import json,sys
d=json.load(open(sys.argv[1],encoding='utf-8'))
dev=d.get('devices',[])
fc=[x for x in dev if str(x.get('registration','')).upper().startswith('F-C')]
json.dump({'devices':fc[:400]},open(sys.argv[2],'w',encoding='utf-8'),ensure_ascii=False,indent=0)
keys=sorted({k for x in dev[:50] for k in x})
print('ogn ddb total',len(dev),'F-C',len(fc),'keys',keys,'tracked N',sum(1 for x in dev if x.get('tracked')=='N'))
PY2
rm -f "$OUT/ogn/ddb_full.json"
# --- Session 3 : cartographie ---------------------------------------------------------------
mkdir -p "$OUT/carto"
# openAIP : spécification de l'API (énumérations), espaces/navaids avec délai anti-429, exports publics
curl -sS -m 60 https://docs.openaip.net/ -o "$OUT/carto/openaip_docs.html"; echo "openaip docs $(stat -c %s "$OUT/carto/openaip_docs.html")" | tee -a "$OUT/index.txt"
grep -oE '(src|href|url)="?[^" >]+\.(js|json|yaml)' "$OUT/carto/openaip_docs.html" | head -20 | tee -a "$OUT/index.txt"
for u in $(grep -oE '"[^"]+\.(json|yaml)"' "$OUT/carto/openaip_docs.html" | tr -d '"' | head -5); do
  case "$u" in http*) U="$u";; /*) U="https://docs.openaip.net$u";; *) U="https://docs.openaip.net/$u";; esac
  get "carto/spec_$(basename "$u" | tr -c 'A-Za-z0-9._-' _)" "$U"
done
for U in https://api.core.openaip.net/api/system/specs https://docs.openaip.net/openapi.json https://docs.openaip.net/swagger.json https://api.core.openaip.net/api/docs-json; do
  get "carto/spec_try_$(echo "$U" | md5sum | cut -c1-6)" "$U" ${OPENAIP_KEY:+-H "x-openaip-api-key: $OPENAIP_KEY"}
done
if [ -n "${OPENAIP_KEY:-}" ]; then
  for L in airspaces navaids reporting-points; do
    for try in 1 2 3; do
      sleep 8
      get "carto/openaip_${L}_lfnl" "https://api.core.openaip.net/api/$L?pos=$LAT,$LON&dist=80000&limit=100" -H "x-openaip-api-key: $OPENAIP_KEY"
      head -c 1 "$OUT/carto/openaip_${L}_lfnl.json" | grep -q '{' && break
    done
  done
fi
B2=https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f
for F in fr_asp.geojson fr_apt.geojson fr_nav.geojson fr_asp.json fr_apt.json; do
  code=$(curl -sS -m 120 -o "$OUT/carto/export_$F" -w '%{http_code}' "$B2/$F" || echo ERR)
  echo "export $F $code $(stat -c %s "$OUT/carto/export_$F" 2>/dev/null)" | tee -a "$OUT/index.txt"
  [ "$code" = 200 ] && [ "$(stat -c %s "$OUT/carto/export_$F")" -gt 20000000 ] && { head -c 3000 "$OUT/carto/export_$F" > "$OUT/carto/export_${F}.head"; rm -f "$OUT/carto/export_$F"; }
done
# Protomaps : liste des builds quotidiens ; Copernicus GLO-30 : tuile de LFNL
get carto/protomaps_builds "https://build-metadata.protomaps.dev/builds.json"
code=$(curl -sS -m 60 -I -o "$OUT/carto/cop30_head.txt" -w '%{http_code}' "https://copernicus-dem-30m.s3.amazonaws.com/Copernicus_DSM_COG_10_N43_00_E003_00_DEM/Copernicus_DSM_COG_10_N43_00_E003_00_DEM.tif" || echo ERR)
echo "cop30 N43E003 $code" | tee -a "$OUT/index.txt"
# polices de carte (glyphes MapLibre) Protomaps
for R in 0-255 256-511; do
  code=$(curl -sS -m 60 -o "$OUT/carto/glyph_$R.pbf" -w '%{http_code}' "https://raw.githubusercontent.com/protomaps/basemaps-assets/main/fonts/Noto%20Sans%20Regular/$R.pbf" || echo ERR)
  echo "glyph $R $code $(stat -c %s "$OUT/carto/glyph_$R.pbf" 2>/dev/null)" | tee -a "$OUT/index.txt"
done
exit 0
