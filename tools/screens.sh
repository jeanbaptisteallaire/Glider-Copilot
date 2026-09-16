#!/usr/bin/env bash
# Captures d'écran sur émulateur : avertissement, Prévol (données réelles), Vol.
set -x
OUT=${1:-screens}; mkdir -p "$OUT"
APK=$(ls apk/*.apk | head -1)
adb install -r "$APK"
adb shell pm clear com.neutronstar.glidercopilot || true
adb shell am start -n com.neutronstar.glidercopilot/.MainActivity
sleep 12
adb exec-out screencap -p > "$OUT/01-avertissement.png"
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
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
P=$(tap_text "J'ai compris"); [ -n "$P" ] && adb shell input tap $P
sleep 35
adb exec-out screencap -p > "$OUT/02-prevol.png"
adb shell input swipe 540 1600 540 500 400; sleep 2
adb exec-out screencap -p > "$OUT/03-prevol-suite.png"
adb shell input swipe 540 1600 540 400 400; sleep 2
adb exec-out screencap -p > "$OUT/04-prevol-fin.png"
P=$(tap_text "VOL"); [ -n "$P" ] && adb shell input tap $P
sleep 4
adb exec-out screencap -p > "$OUT/05-vol.png"
P=$(tap_text "Fermer le vario et couper le son"); [ -n "$P" ] && adb shell input tap $P
sleep 2
adb exec-out screencap -p > "$OUT/06-vol-vario-ferme.png"
adb logcat -d -t 400 > "$OUT/logcat.txt" || true
ls -la "$OUT"
