import Foundation
import SwiftUI

/// V4 — langue de l'app (français ou anglais), la même que le tutoriel (clé « guideLanguage »). Le bouton FR/EN en haut à
/// droite de chaque page la change ; l'app se redessine aussitôt (RootView reconstruit les écrans sur `lang`).
enum Lang {
    static let key = "guideLanguage"
    static var current: String {
        UserDefaults.standard.string(forKey: key) ?? (Locale.current.language.languageCode?.identifier == "fr" ? "fr" : "en")
    }
    static var en: Bool { current == "en" }
}

/// Texte affiché : la clé est la phrase française ; en anglais, sa traduction (sinon la phrase française, signalée en debug).
/// Les paramètres (%@, %d) suivent `String(format:)`.
func tr(_ fr: String, _ args: CVarArg...) -> String {
    var s = fr
    if Lang.en {
        if let e = EN[fr] { s = e } else {
            #if DEBUG
            print("⚠︎ traduction manquante : \(fr)")
            #endif
        }
    }
    return args.isEmpty ? s : String(format: s, arguments: args)
}

/// Textes produits par le métier commun (Kotlin, en français) : traduction mot à mot des termes récurrents en anglais.
/// Phrases entières connues d'abord, puis vocabulaire ; les nombres et unités restent tels quels.
func trDyn(_ fr: String) -> String {
    guard Lang.en, !fr.isEmpty else { return fr }
    if let e = EN[fr] { return e }
    var s = fr
    for (a, b) in DYN { s = s.replacingOccurrences(of: a, with: b) }
    return s
}

/// Bouton FR / EN, en haut à droite de chaque page.
struct LanguageSwitch: View {
    @AppStorage(Lang.key) private var lang = Lang.current
    var dark = false

    var body: some View {
        Button { lang = lang == "fr" ? "en" : "fr" } label: {
            Text(lang == "fr" ? "EN" : "FR").font(WG.subhead.weight(.semibold))
                .foregroundStyle(dark ? .white : WG.brandInk)
                .frame(minWidth: 40, minHeight: 32)
                .background((dark ? Color.black.opacity(0.85) : Color.white.opacity(0.9)), in: Capsule())
                .overlay(Capsule().stroke(dark ? Color.clear : WG.line))
        }
        .accessibilityLabel(lang == "fr" ? "Switch to English" : "Passer en français")
    }
}

private let DYN: [(String, String)] = [
    ("aéronefs", "aircraft"), ("aéronef", "aircraft"), ("pompes", "thermals"), ("pompe", "thermal"), (" à 15 km", " within 15 km"),
    ("prévu", "forecast"), ("vent", "wind"), ("terrain", "field"), ("indisponible", "unavailable"), ("inconnue", "unknown"),
    ("marge non calculée", "margin not computed"), ("altitude du terrain", "field elevation"), ("relief Copernicus", "Copernicus terrain"),
    ("modèle ARPEGE", "ARPEGE model"), ("calé terrain", "set on field"), ("calé GPS", "set by GPS"), ("alt. pression", "pressure alt."),
    ("Vario baro+accél.", "Baro+accel. vario"), ("Vario baro", "Baro vario"), ("Vario OGN", "OGN vario"), ("Vario indisponible", "Vario unavailable"),
    ("Démo en vol", "Demo flight"), ("pas d'IGC", "no IGC"), ("En vol", "Flying"), ("non enregistré", "not recorded"), ("Au sol", "On ground"),
    ("Enregistrement", "Recording"), ("arrivée", "arrival"), ("m/sol", "m AGL"), ("Faible", "Weak"), ("Moyen", "Moderate"), ("Fort", "Strong"),
    ("Nul", "None"), ("Capteurs", "Sensors"), ("absent", "missing"), ("présent", "present"), ("Accéléromètre", "Accelerometer"),
    ("Baromètre", "Barometer"), ("Hz", "Hz"), ("précision", "accuracy"), ("pas de GPS", "no GPS"), ("en attente", "waiting"),
    ("jaune", "yellow"), ("orange", "orange"), ("rouge", "red"), ("vert", "green"), ("Orages", "Thunderstorms"), ("Vent violent", "Strong wind"),
    ("Pluie-inondation", "Rain-flood"), ("Canicule", "Heatwave"), ("Grand froid", "Extreme cold"), ("Neige-verglas", "Snow-ice"), ("Avalanches", "Avalanches"),
    ("Vagues-submersion", "Waves-flooding"), ("Inondation", "Flood"), ("Pas de vigilance particulière", "No particular warning"),
    (" vol", " flight"), ("Vol du ", "Flight of "), ("Vol ", "Flight "), ("min", "min"),
]

/// Français → anglais. Clé = texte exact affiché en français.
let EN: [String: String] = [
    // accueil, avertissements
    "Continuer avec Google": "Continue with Google",
    "Se connecter avec Apple": "Sign in with Apple",
    "Continuer en invité": "Continue as guest",
    "Conditions": "Terms",
    "Confidentialité": "Privacy",
    "Aide secondaire au vol à voile. Ne remplace ni le vario, ni le calculateur, ni le FLARM, ni une navigation certifiée. Instruments de bord et veille extérieure priment. Le pilote reste seul responsable.":
        "Secondary aid for gliding. It does not replace the vario, the flight computer, FLARM or certified navigation. Onboard instruments and lookout come first. The pilot alone remains responsible.",
    "Données météo : PRECOG — precog-api.com (Source : Météo-France). Relief : Copernicus GLO-30. Terrains : openAIP. Estimations thermiques calculées dans l'app : à confronter au ciel.":
        "Weather data: PRECOG — precog-api.com (Source: Météo-France). Terrain: Copernicus GLO-30. Fields: openAIP. Thermal estimates computed in the app: to be checked against the sky.",
    "J'ai compris": "I understand",
    "Avant de voler": "Before flying",
    "Fixez solidement votre téléphone dans le cockpit, sans gêner les commandes ni la vue dehors.": "Mount your phone securely in the cockpit, without obstructing the controls or the view outside.",
    "Utilisez Wind Glider avec l'accord de votre instructeur.": "Use Wind Glider with your instructor's approval.",
    "Aide secondaire : instruments de bord et veille extérieure d'abord.": "Secondary aid: onboard instruments and lookout first.",
    "Compris": "Got it",
    // onglets et communs
    "OK": "OK", "Annuler": "Cancel", "Fermer": "Close", "Enregistrer": "Save", "Supprimer": "Delete", "Modifier": "Edit", "Réessayer": "Retry",
    // Pilotage
    "Marge sécu.": "Margin", "SÉCU": "REQ", "ALT": "ALT", "Finesse": "Glide ratio", "Fin.": "L/D", "CAP": "TRK", "Dist. ter.": "Field dist.",
    "relief": "terrain", "vario": "vario", "REC": "REC", "DÉMO": "DEMO", "ESSENTIEL": "ESSENTIAL", "COMPLET": "FULL", "Afficher tout Pilotage": "Show the full flight screen", "N'afficher que l'essentiel": "Show essentials only", "Spirale": "Circle", "Pompe": "Lift", "Jour": "Day",
    "vent\n—": "wind\n—", "Coupe disponible dès la première position": "Profile available from the first position fix",
    "Zones aéronautiques": "Airspace",
    "Téléchargez la carte hors ligne de votre région dans Prévol pour afficher les zones aéronautiques (openAIP).": "Download your region's offline map in Pre-flight to show airspace (openAIP).",
    "Automatique (meilleure marge)": "Automatic (best margin)", "Terrain": "Field",
    "Arrêter l'enregistrement": "Stop recording", "Démarrer l'enregistrement": "Start recording",
    "Choisir le terrain de référence": "Choose the reference field",
    "Couper le son du vario": "Mute vario sound", "Activer le son du vario": "Unmute vario sound",
    "Vent pas encore estimé : spiralez": "Wind not estimated yet: circle", "Carte centrée sur le planeur": "Map centred on the glider", "Recentrer la carte": "Recentre the map",
    "Masquer les zones aéronautiques": "Hide airspace", "Afficher les zones aéronautiques": "Show airspace",
    "Passer en mode clair": "Switch to light mode", "Passer en mode sombre": "Switch to dark mode", "Mon planeur": "My glider",
    "Masquer %@": "Hide %@", "Afficher %@": "Show %@", "Trafic OGN : %@": "OGN traffic: %@", "Vent %@": "Wind %@", "nord en haut": "north up",
    "Orientation de la carte : %@": "Map orientation: %@", "%@ actif": "%@ on", "%@ inactif": "%@ off", "Aéronef %@": "Aircraft %@", "Pompe %@ mètres par seconde": "Thermal %@ metres per second",
    // Prévol
    "Prévol": "Pre-flight", "Météo du jour…": "Today's weather…", "Météo": "Weather",
    "DONNÉES D'ESSAI · journée synthétique du 16/09/2026, pas une prévision": "TEST DATA · synthetic day of 16/09/2026, not a forecast",
    "Estimations de l'app sur le profil ARPEGE (maille ~10 km) : plafond par parcelle adiabatique, base des cumulus par Espy, force par w*. Aide secondaire, à confronter au ciel et aux consignes du club.":
        "App estimates from the ARPEGE profile (~10 km grid): ceiling by adiabatic parcel, cumulus base by Espy, strength by w*. Secondary aid, to be checked against the sky and club guidance.",
    "Changer de club": "Change club", "Sources météo": "Weather sources", "Actualiser": "Refresh",
    "La journée": "The day", "Thermique · %@": "Thermals · %@", "Déclenchement": "Trigger", "Plafond": "Ceiling", "Plafond %@": "Ceiling %@", "Fin": "End",
    "faible": "weak", "moyen": "moderate", "fort": "strong", "base Cu": "Cu base",
    "⚠ CAPE élevée sous cumulus : surdéveloppement possible": "⚠ High CAPE under cumulus: overdevelopment possible",
    "Plafond heure par heure": "Ceiling hour by hour", "Pas d'ascendance exploitable": "No usable lift", "Thermique pur (ciel bleu)": "Blue thermals",
    "Thermique sous cumulus": "Thermals under cumulus", " (≥ sommet du profil)": " (≥ profile top)", "Conditions à %@": "Conditions at %@",
    "Base Cu": "Cu base", "Montée": "Climb", "Plafond sol": "Ceiling AGL", "Nature": "Type", "Force estimée": "Estimated strength", "Nébulosité": "Cloud cover",
    " (basse %d %%)": " (low %d %%)",
    "⚠ Couche basse prévue presque couverte : ascendances sans doute étouffées, base à vérifier au ciel.": "⚠ Low layer forecast nearly overcast: lift probably suppressed, check the base in the sky.",
    "Sol du modèle": "Model ground", " · maille à %@ km": " · grid point %@ km away", "Vent par tranches · %@": "Wind by layer · %@",
    "Vent moyen du modèle (direction d'où il vient)": "Model mean wind (direction it blows from)", "Vigilance Météo-France · %@": "Météo-France warnings · %@",
    "Vol": "Flight", "Départ automatique à 50 km/h": "Automatic start at 50 km/h", "Son du vario": "Vario sound", "Annonces vocales": "Voice announcements",
    "Tester le son": "Test sound", "Tester la voix": "Test voice", "présent": "present", "absent": "missing",
    "Sans départ automatique, l'enregistrement se lance avec REC dans Pilotage. Baromètre : %@": "Without automatic start, recording starts with REC in Flight. Barometer: %@",
    "Sigle, OACI, nom ou ville": "Code, ICAO, name or town", "Choix du club": "Choose a club", "réseau de %@": "run of %@", "reçu à %@": "received at %@", "copie locale": "local copy",
    "Aucune donnée reçue": "No data received",
    "Données fournies en l'état : estimation d'un modèle, à confronter à votre propre analyse. Ne remplace pas un briefing météo officiel.": "Data provided as is: a model estimate, to be checked against your own analysis. Not a substitute for an official weather briefing.",
    "Carte hors ligne": "Offline map", "%@ · %d terrains · relief %@": "%@ · %d fields · terrain %@", "chargé": "loaded",
    "Données aéro périmées : mettez la carte à jour avant le vol": "Aero data out of date: update the map before flying",
    "Espaces aériens autour du terrain (%d)": "Airspace around the field (%d)", " · par NOTAM": " · by NOTAM", "%@ : relief et terrains, %d Mo": "%@: terrain and fields, %d MB",
    "Sans carte, la marge ne connaît ni le relief ni les terrains de dégagement.": "Without a map, the margin knows neither the terrain nor alternate fields.",
    "Aucune carte pour cette région": "No map for this region", "Catalogue des cartes injoignable": "Map catalogue unreachable", "Téléchargement %d %%": "Downloading %d %%",
    "Mettre à jour": "Update", "Télécharger (%d Mo)": "Download (%d MB)",
    "Relief Copernicus GLO-30 · terrains openAIP · vérifiez SUP AIP et NOTAM avant le vol": "Copernicus GLO-30 terrain · openAIP fields · check SUP AIP and NOTAM before flying",
    "Capteurs": "Sensors", "Baromètre": "Barometer", "Bruit baro": "Baro noise", "Accéléromètre": "Accelerometer", "Club sans position connue": "Club without a known position",
    // Mes vols
    "Mes vols": "My flights", "Pilote Wind Glider": "Wind Glider pilot", "Crée ton profil pilote": "Create your pilot profile",
    "Nom, pseudo, club et niveau. Tout reste sur ce téléphone tant que tu ne partages rien.": "Name, username, club and level. Everything stays on this phone unless you share it.",
    "Créer mon profil": "Create my profile", "Importer un IGC": "Import an IGC", "Vols": "Flights", "%d vol": "%d flight", "%d vols": "%d flights", "Carnet vide": "Empty logbook",
    "Vos vols apparaissent ici après REC dans Pilotage, ou avec « Importer un IGC ».": "Your flights appear here after REC in Flight, or with “Import an IGC”.",
    "Supprimer le vol": "Delete flight", "« %@ » n'est pas un fichier IGC lisible.": "“%@” is not a readable IGC file.", "Mon profil": "My profile", "Avatar du pilote": "Pilot avatar",
    "Heures": "Hours", "Distance": "Distance", "Trace": "Track", "Durée": "Duration", "Alt. max": "Max alt.", "Revoir le vol en 3D": "Replay the flight in 3D",
    "Rejeu 3D indisponible : vol sans altitude.": "3D replay unavailable: flight without altitude.", "Profil d'altitude": "Altitude profile", "Plage": "Range",
    "%@ · %@ · trace indicative (enregistreur non approuvé)": "%@ · %@ · indicative track (non-approved logger)", "Nom": "Name", "Pseudo": "Username", "Club": "Club", "Niveau": "Level",
    "Tout reste sur ce téléphone : rien n'est envoyé ni partagé.": "Everything stays on this phone: nothing is sent or shared.", "Modifier le profil": "Edit profile",
    "Élève": "Student", "Lâché": "Solo", "Brevet": "Licensed", "Pilote confirmé": "Experienced pilot", "Instructeur": "Instructor",
    "Chargement du rejeu 3D…": "Loading 3D replay…", "Fermer le rejeu": "Close replay", "Erreur du rejeu": "Replay error", "Rejeu indisponible : %@": "Replay unavailable: %@",
    // compte
    "Sauvegarde en ligne": "Online backup", "Bientôt disponible. Wind Glider fonctionne sans compte : vos vols restent sur cet iPhone.": "Coming soon. Wind Glider works without an account: your flights stay on this iPhone.",
    "%d / %d vols sauvegardés": "%d / %d flights backed up", "Sauvegarder": "Back up", "Se déconnecter": "Sign out", "Supprimer mon compte et mes vols en ligne": "Delete my account and online flights",
    "Vos vols sauvegardés, retrouvés sur un autre téléphone ou après une réinstallation.": "Your flights backed up, available on another phone or after reinstalling.",
    "En vous connectant, votre adresse e-mail et vos vols (fichiers IGC : traces GPS, horaires, altitudes) sont enregistrés sur notre serveur sécurisé en Irlande (UE). Vous pouvez tout supprimer à tout moment.":
        "By signing in, your email address and flights (IGC files: GPS tracks, times, altitudes) are stored on our secure server in Ireland (EU). You can delete everything at any time.",
    "Supprimer le compte ?": "Delete the account?", "Le compte %@ et ses vols en ligne seront effacés définitivement. Les vols de cet iPhone sont conservés.": "The account %@ and its online flights will be permanently deleted. Flights on this iPhone are kept.",
    "Connexion Google…": "Signing in with Google…", "Connexion Apple…": "Signing in with Apple…", "Déconnecté. Vos vols restent sur l'iPhone.": "Signed out. Your flights stay on the iPhone.",
    "Suppression du compte…": "Deleting account…", "Compte supprimé. Vos vols restent sur l'iPhone.": "Account deleted. Your flights stay on the iPhone.",
    "Envoi suspendu pendant le vol": "Upload paused during the flight", "Sauvegarde…": "Backing up…", "Échec : %@": "Failed: %@",
    "Connexion Apple indisponible : connectez-vous à votre compte Apple dans Réglages.": "Apple sign-in unavailable: sign in to your Apple account in Settings.",
    "Connexion Apple impossible, réessayez.": "Apple sign-in failed, try again.",
    // check-lists
    "Piste / QFU": "Runway / QFU", "Demi-tour dès": "Turn back from", "Posé devant sous": "Land ahead below", "Dégagement": "Alternate", "Menace": "Threat", "m sol": "m AGL",
    "déplié": "expanded", "replié": "collapsed", "coché": "checked", "non coché": "unchecked",
]
