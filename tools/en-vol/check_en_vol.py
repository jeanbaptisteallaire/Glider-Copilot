#!/usr/bin/env python3
"""GLIDY — garde-fou « En vol » (S15).

JB : « Ne change absolument rien à l'interface En vol » (= onglet Pilotage). Ce script échoue si :
  1. un fichier de l'écran Pilotage diffère de sa référence (empreintes SHA-256 de la V0.9.3/S13) ;
  2. la charte SOMBRE (couleurs, typographie, thème) a bougé dans Theme.kt ;
  3. AppRoot n'affiche plus Pilotage avec le thème sombre d'origine.
Une évolution voulue de Pilotage, décidée par JB, se fait en régénérant les empreintes :
  python3 tools/en-vol/check_en_vol.py --update
"""
import hashlib, json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = pathlib.Path(__file__).with_name("baseline.json")
FLIGHT = "feature/flight/src/main/kotlin/com/neutronstar/glidercopilot/feature/flight/"
FILES = [FLIGHT + f for f in ("FlightScreen.kt", "FlightLive.kt", "FlightMap.kt", "VarioTone.kt", "UiMask.kt")]
THEME = "core/designsystem/src/main/kotlin/com/neutronstar/glidercopilot/designsystem/Theme.kt"
# blocs de la charte sombre utilisés par Pilotage, repérés par leur première ligne
BLOCKS = {
    "GlidyColors": r"val GlidyColors = GcColors\(\n.*?\n\)\n",
    "gcTypeSombre": r"else GcType\(\n.*?\n\)\n|private fun gcType\(c: GcColors\) = GcType\(\n.*?\n\)\n",
    "GlidyTheme": r"fun GlidyTheme\(content: @Composable \(\) -> Unit\) \{\n.*?\n\}\n",
}
APPROOT = "app/src/main/kotlin/com/neutronstar/glidercopilot/AppRoot.kt"
PILOTAGE_LINE = "Tab.PILOTAGE -> FlightScreen(status, map = flightMap, traffic = traffic, live = live, controls = container.flight)"


def sha(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def block(text: str, pattern: str) -> str:
    m = re.search(pattern, text, re.S)
    if not m:
        return "<absent>"
    # seul le contenu compte (la ligne d'ouverture de gcType a légitimement changé en S15)
    return "\n".join(m.group(0).splitlines()[1:])


def current() -> dict:
    theme = (ROOT / THEME).read_text(encoding="utf-8")
    out = {f: sha((ROOT / f).read_text(encoding="utf-8")) for f in FILES}
    out.update({f"{THEME}#{k}": sha(block(theme, p)) for k, p in BLOCKS.items()})
    return out


def main() -> int:
    now = current()
    if "--update" in sys.argv:
        BASE.write_text(json.dumps(now, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"empreintes « En vol » enregistrées ({len(now)})")
        return 0
    ref = json.loads(BASE.read_text(encoding="utf-8"))
    bad = [k for k in ref if now.get(k) != ref[k]]
    if PILOTAGE_LINE not in (ROOT / APPROOT).read_text(encoding="utf-8"):
        bad.append(f"{APPROOT} : l'appel de FlightScreen (thème sombre d'origine) a changé")
    if bad:
        print("❌ « En vol » (Pilotage) modifié — interdit sans accord de JB :")
        for b in bad:
            print("   -", b)
        return 1
    print(f"✅ « En vol » intact ({len(ref)} empreintes + appel Pilotage)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
