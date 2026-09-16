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
exit 0
