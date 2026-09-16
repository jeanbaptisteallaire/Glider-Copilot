#!/usr/bin/env bash
# Captures d'écran sur émulateur : avertissement, Prévol (appairage + météo réelle), Check-lists, Pilotage.
set -x
OUT=${1:-screens}; mkdir -p "$OUT"
exec > >(tee -a "$OUT/trace.txt") 2>&1
date -u
APK=$(ls apk/*.apk | head -1)
timeout 180 adb install -r "$APK"
adb shell pm clear com.neutronstar.glidercopilot || true
adb logcat -c || true
(adb logcat -v time > "$OUT/logcat-live.txt" 2>&1 &)
# localisation accordée d'avance : pas de boîte de dialogue pendant les captures
adb shell pm grant com.neutronstar.glidercopilot android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant com.neutronstar.glidercopilot android.permission.ACCESS_COARSE_LOCATION || true
adb shell am start -n com.neutronstar.glidercopilot/.MainActivity
sleep 12
timeout 20 adb exec-out screencap -p > "$OUT/01-avertissement.png"
tap_text() {
  rm -f /tmp/ui.xml
  timeout 25 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  timeout 15 adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  [ -f /tmp/ui.xml ] || { echo "uiautomator indisponible pour: $1" >&2; return 0; }
  python3 - "$1" <<'PY'
import re,sys
x=open('/tmp/ui.xml',encoding='utf-8').read()
t=sys.argv[1]
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0)
    if f'text="{t}"' in n or f'content-desc="{t}"' in n:
        b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
        if b:
            x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
}
P=$(tap_text "J'ai compris"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 35
timeout 20 adb exec-out screencap -p > "$OUT/02-prevol.png"
P=$(tap_text "Immatriculation du planeur"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 1; adb shell input text "${REG:-F-CPHI}"; sleep 1
adb shell input keyevent 111 || true   # masque le clavier
sleep 1
P=$(tap_text "Valider"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 40
timeout 20 adb exec-out screencap -p > "$OUT/03-prevol-appairage.png"
adb shell input swipe 540 1600 540 500 400; sleep 2
timeout 20 adb exec-out screencap -p > "$OUT/04-prevol-meteo.png"
P=$(tap_text "Check-lists"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 3
timeout 20 adb exec-out screencap -p > "$OUT/05-checklists.png"
P=$(tap_text "Visite prévol ou tour complet du planeur effectué"); [ -n "$P" ] && timeout 10 adb shell input tap $P
P=$(tap_text "Masse et centrage vérifiés"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 1
timeout 20 adb exec-out screencap -p > "$OUT/06-checklists-cases.png"
adb shell input swipe 540 1700 540 400 400; sleep 2
timeout 20 adb exec-out screencap -p > "$OUT/07-checklists-suite.png"
P=$(tap_text "Pilotage"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 8
timeout 20 adb exec-out screencap -p > "$OUT/08-pilotage.png"
P=$(tap_text "Réduire"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 2
timeout 20 adb exec-out screencap -p > "$OUT/09-pilotage-profil-reduit.png"
P=$(tap_text "Masquer le vario"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 2
timeout 20 adb exec-out screencap -p > "$OUT/10-pilotage-vario-replie.png"
timeout 30 adb logcat -d -t 600 > "$OUT/logcat.txt" || true
ls -la "$OUT"
