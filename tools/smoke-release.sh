#!/usr/bin/env bash
# S9 — test de fumée de la version publiée (R8 activé) : installe l'APK release, parcourt les quatre onglets
# avec le vol de démonstration rejoué, et échoue si l'app plante (règle R8 manquante, classe retirée…).
set -x
OUT=${1:-screens}; mkdir -p "$OUT"
PKG=com.neutronstar.glidercopilot
APK=$(ls apk-release/*.apk 2>/dev/null | head -1)
[ -n "$APK" ] || { echo "ECHEC : pas d'APK release" | tee "$OUT/release-smoke.txt"; exit 1; }
adb uninstall $PKG || true
timeout 180 adb install -r "$APK" || { echo "ECHEC : installation" | tee "$OUT/release-smoke.txt"; exit 1; }
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION || true
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb emu geo fix 3.78167 43.80028 183 || true
adb logcat -c || true
tap() {
  rm -f /tmp/ui-r.xml
  timeout 25 adb shell uiautomator dump /sdcard/ui-r.xml >/dev/null 2>&1
  timeout 15 adb pull /sdcard/ui-r.xml /tmp/ui-r.xml >/dev/null 2>&1
  [ -f /tmp/ui-r.xml ] || return 0
  P=$(python3 - "$1" <<'PY'
import re,sys
x=open('/tmp/ui-r.xml',encoding='utf-8').read(); t=sys.argv[1]
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0)
    if f'text="{t}"' in n or f'content-desc="{t}"' in n:
        b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
        if b:
            x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)
  [ -n "$P" ] && timeout 10 adb shell input tap $P
}
adb shell am start -n $PKG/.MainActivity --ez glidy.flight.replay true --ef glidy.flight.speed 6 --ez glidy.flights.demo true
sleep 15
tap "Wait"; tap "J'ai compris"
sleep 20
timeout 20 adb exec-out screencap -p > "$OUT/90-release-prevol.png"
tap "Check-lists"; sleep 6
timeout 20 adb exec-out screencap -p > "$OUT/91-release-checklists.png"
tap "Pilotage"; sleep 25
timeout 20 adb exec-out screencap -p > "$OUT/92-release-pilotage.png"
tap "Carte"; sleep 15
timeout 20 adb exec-out screencap -p > "$OUT/93-release-carte.png"
tap "Mes vols"; sleep 8
timeout 20 adb exec-out screencap -p > "$OUT/94-release-mes-vols.png"
tap "Prévol"; sleep 8
timeout 30 adb logcat -d > "$OUT/release-logcat.txt" || true
ALIVE=$(adb shell pidof $PKG | tr -d '\r')
if grep -q "FATAL EXCEPTION" "$OUT/release-logcat.txt" || [ -z "$ALIVE" ]; then
  { echo "ECHEC : plantage de la version release (R8)"; grep -A25 "FATAL EXCEPTION" "$OUT/release-logcat.txt" | head -60; } | tee "$OUT/release-smoke.txt"
  exit 1
fi
echo "OK : version release (R8) lancée, 5 onglets parcourus, aucun plantage, pid $ALIVE" | tee "$OUT/release-smoke.txt"
