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
# position GPS de l'émulateur sur LFNL (sinon Mountain View : club le plus proche faux dès le premier fix)
adb emu geo fix 3.78167 43.80028 183 || true
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
    pre = t.endswith('*')
    tt = t[:-1] if pre else t
    hit = (f'text="{tt}' in n or f'content-desc="{tt}' in n) if pre else (f'text="{t}"' in n or f'content-desc="{t}"' in n)
    if hit:
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
# carte hors ligne : téléchargement du pack de la région du club
P=$(tap_text "Télécharger*"); [ -n "$P" ] && timeout 10 adb shell input tap $P
for i in $(seq 1 60); do
  sleep 6
  P=$(tap_text "Installée"); [ -n "$P" ] && { echo "pack installé après $((i*6)) s"; break; }
done
timeout 20 adb exec-out screencap -p > "$OUT/04-prevol-carte.png"
# réseau OGN en direct (connexion réelle depuis l'émulateur de la CI)
adb shell input swipe 540 1500 540 700 400; sleep 20
timeout 20 adb exec-out screencap -p > "$OUT/05-prevol-ogn-direct.png"
for i in 1 2 3 4 5 6; do adb shell input swipe 540 1700 540 300 300; sleep 1; done
sleep 2
timeout 20 adb exec-out screencap -p > "$OUT/06-prevol-espaces.png"
P=$(tap_text "Check-lists"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 3
timeout 20 adb exec-out screencap -p > "$OUT/07-checklists.png"
P=$(tap_text "Visite prévol ou tour complet du planeur effectué"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 1
adb shell input swipe 540 1700 540 400 400; sleep 2
timeout 20 adb exec-out screencap -p > "$OUT/07b-checklists-suite.png"
# Pilotage : vol de démonstration rejoué ×6 (capteurs simulés depuis l'IGC) + rejeu OGN anonymisé (trafic réel déplacé autour de LFNL)
adb shell am force-stop com.neutronstar.glidercopilot
T0=$(date +%s)
adb shell am start -n com.neutronstar.glidercopilot/.MainActivity --ez glidy.flight.replay true --ef glidy.flight.speed 6
sleep 8
P=$(tap_text "Pilotage"); [ -n "$P" ] && timeout 10 adb shell input tap $P
# temps réel : décollage ~12 s, largage ~72 s, spirales jusqu'à ~170 s, atterrissage ~330 s
while [ $(( $(date +%s) - T0 )) -lt 105 ]; do sleep 2; done
timeout 20 adb exec-out screencap -p > "$OUT/08-pilotage-vol-rejeu.png"
P=$(tap_text "Zoom arrière"); [ -n "$P" ] && { timeout 10 adb shell input tap $P; sleep 1; timeout 10 adb shell input tap $P; }
sleep 6
timeout 20 adb exec-out screencap -p > "$OUT/09-pilotage-vol-large.png"
P=$(tap_text "Réduire"); [ -n "$P" ] && timeout 10 adb shell input tap $P
P=$(tap_text "Masquer le vario"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 5
timeout 20 adb exec-out screencap -p > "$OUT/10-pilotage-vol-plein.png"
# carte Capteurs & vols pendant le vol
P=$(tap_text "Prévol"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 3
for i in 1 2 3 4 5 6 7 8 9 10; do
  P=$(tap_text "Essai du son"); [ -n "$P" ] && break
  adb shell input swipe 540 1600 540 900 300; sleep 1
done
adb shell input swipe 540 1500 540 1150 300; sleep 2
timeout 20 adb exec-out screencap -p > "$OUT/11-prevol-capteurs-en-vol.png"
while [ $(( $(date +%s) - T0 )) -lt 390 ]; do sleep 5; done
adb shell input swipe 540 1150 540 1400 300; sleep 3
timeout 20 adb exec-out screencap -p > "$OUT/12-prevol-vol-enregistre.png"
# trace IGC du rejeu : récupération et contrôle de format
mkdir -p "$OUT/igc"
timeout 30 adb pull /sdcard/Android/data/com.neutronstar.glidercopilot/files/igc-rejeu/. "$OUT/igc/" || true
if ! ls "$OUT"/igc/*.igc >/dev/null 2>&1; then
  for f in $(adb shell run-as com.neutronstar.glidercopilot ls files/igc-rejeu 2>/dev/null | tr -d '\r'); do
    adb shell run-as com.neutronstar.glidercopilot cat "files/igc-rejeu/$f" > "$OUT/igc/$f"
  done
fi
python3 tools/flight/check_igc.py "$OUT"/igc/*.igc > "$OUT/igc-controle.txt" 2>&1 || true
cat "$OUT/igc-controle.txt"
# S6 : mode démo (vol simulé autour du terrain, trafic OGN réel), choix du terrain, finesse F10
adb shell am force-stop com.neutronstar.glidercopilot
adb shell am start -n com.neutronstar.glidercopilot/.MainActivity
sleep 10
P=$(tap_text "Pilotage"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 4
# S7 : le menu du terrain de repli doit s'ouvrir au sol, avant tout calcul de sécurité
P=$(tap_text "AUTO"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 3
timeout 20 adb exec-out screencap -p > "$OUT/12b-pilotage-terrain-au-sol.png"
close_menu() {   # la touche retour ne doit partir que si le menu est ouvert, sinon elle quitte l'app
  P=$(tap_text "AUTO · terrain du club, sinon le meilleur rejoignable")
  [ -n "$P" ] && { adb shell input keyevent 4; sleep 2; }
}
close_menu
P=$(tap_text "Mode démo"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 50
timeout 20 adb exec-out screencap -p > "$OUT/13-pilotage-mode-demo.png"
P=$(tap_text "AUTO"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 3
timeout 20 adb exec-out screencap -p > "$OUT/14-pilotage-choix-terrain.png"
close_menu
P=$(tap_text "Finesse de sécurité 10"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 1
P=$(tap_text "Finesse de sécurité 10"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 5
timeout 20 adb exec-out screencap -p > "$OUT/15-pilotage-demo-f10.png"
P=$(tap_text "Mode démo"); [ -n "$P" ] && timeout 10 adb shell input tap $P
# S6 : Suivi & debug d'un planeur du club en vol (F-CGXB)
P=$(tap_text "Prévol"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 3
P=$(tap_text "Suivi et debug d'un planeur en vol"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 2
P=$(tap_text "F-CGXB"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 30
timeout 20 adb exec-out screencap -p > "$OUT/16-prevol-suivi-debug.png"
P=$(tap_text "Pilotage"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 20
timeout 20 adb exec-out screencap -p > "$OUT/17-pilotage-suivi.png"
# S7 : onglet Carte (aéronefs du sud de la France), pistes des terrains, fiche d'un aéronef
P=$(tap_text "Carte"); [ -n "$P" ] && timeout 10 adb shell input tap $P
sleep 30
timeout 20 adb exec-out screencap -p > "$OUT/18-carte-sud-france.png"
# fiche d'un aéronef : la carte est une seule vue pour uiautomator, on sonde une grille de points
found=""
for y in 900 1150 1400 700 1650; do
  for x in 270 540 810 400 680; do
    timeout 10 adb shell input tap $x $y; sleep 2
    P=$(tap_text "VITESSE")
    if [ -n "$P" ]; then found="$x,$y"; break; fi
  done
  [ -n "$found" ] && break
done
echo "fiche aéronef ouverte en $found"
timeout 20 adb exec-out screencap -p > "$OUT/19-carte-fiche-aeronef.png"
P=$(tap_text "VITESSE"); [ -n "$P" ] && { timeout 10 adb shell input tap $P; sleep 2; }   # ferme la fiche
# zoom sur le club : pistes de LFNL, LFMT et LFMS à leur orientation réelle
P=$(tap_text "Zoom avant")
if [ -n "$P" ]; then for i in 1 2 3 4; do timeout 10 adb shell input tap $P; sleep 3; done; fi
sleep 8
timeout 20 adb exec-out screencap -p > "$OUT/20-carte-pistes.png"
timeout 30 adb logcat -d -t 600 > "$OUT/logcat.txt" || true
ls -la "$OUT"
