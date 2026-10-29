package com.neutronstar.glidy.logic.tutorial

/** Partie du tutoriel (en-tête « Prévol · 3 / 16 »). */
enum class TourPart(val fr: String, val en: String) { PREVOL("Prévol", "Pre-flight"), PILOTAGE("Pilotage", "Flight"), MES_VOLS("Mes vols", "My flights") }

/**
 * Étape de la visite guidée : capture pleine page [image] (nom de la ressource `tour_*`, largeur 1080 px, hauteur
 * [imageHeight]) et zone mise en valeur [l], [t], [r], [b] en pixels de l'image.
 */
data class TourStep(
    val part: TourPart,
    val image: String,
    val imageHeight: Int,
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
    val titleFr: String,
    val textFr: String,
    val titleEn: String,
    val textEn: String,
    /** Zone en anglais quand la capture anglaise diffère (textes plus longs ou plus courts) ; sinon celle du français. */
    val rectEn: TourRect? = null,
) {
    fun rect(lang: String): TourRect = if (lang != "fr" && rectEn != null) rectEn else TourRect(l, t, r, b)
    fun title(lang: String) = if (lang == "fr") titleFr else titleEn
    fun text(lang: String) = if (lang == "fr") textFr else textEn
    fun header(lang: String, position: Int, total: Int) = "${if (lang == "fr") part.fr else part.en} · $position / $total"
}

/** Zone mise en valeur, en pixels de l'image (largeur 1080). */
data class TourRect(val l: Float, val t: Float, val r: Float, val b: Float)

/**
 * Tutoriel Wind Glider (V19.1b « coach marks », V20 obligatoire au premier lancement) : mêmes étapes et mêmes textes
 * sur Android (Guide.kt) et iOS (Tuto/). Une modification de texte se fait ici, une seule fois.
 */
object Tutorial {
    val steps: List<TourStep> = listOf(
        TourStep(TourPart.PREVOL, "tour_prevol", 4060, 460f, 40f, 1070f, 155f,
            "Votre terrain", "Touchez le terrain en haut de la page pour choisir votre club. La météo suit.",
            "Your airfield", "Tap the airfield at the top of the page to pick your club. The weather follows."),
        TourStep(TourPart.PREVOL, "tour_prevol", 4060, 30f, 204f, 1050f, 690f,
            "Carte hors ligne", "Téléchargez la zone où vous volez : la carte fonctionne ensuite sans réseau.",
            "Offline map", "Download the area you fly in: the map then works without network."),
        TourStep(TourPart.PREVOL, "tour_prevol", 4060, 30f, 3296f, 1050f, 3620f,
            "Appairez votre planeur", "Saisissez l'immatriculation de votre FLARM. Quand il y a du réseau, sa position OGN valide celle du téléphone.",
            "Pair your glider", "Enter your FLARM registration. With network, its OGN position cross-checks the phone's."),
        TourStep(TourPart.PREVOL, "tour_prevol", 4060, 30f, 3630f, 1050f, 3782f,
            "Décollage détecté", "Activez « Détec. auto. décollage » : chrono et trace démarrent seuls dès 50 km/h.",
            "Take-off detection", "Turn on take-off detection: timer and track start by themselves above 50 km/h."),
        TourStep(TourPart.PILOTAGE, "tour_pilotage", 2210, 15f, 10f, 500f, 320f,
            "Marge de sécurité", "L'altitude en plus (vert) ou en moins (orange) pour rejoindre le terrain avec 300 m de réserve. SÉCU : altitude nécessaire.",
            "Safety margin", "Height above (green) or below (orange) what you need to reach the airfield with 300 m to spare. SÉCU: height needed."),
        TourStep(TourPart.PILOTAGE, "tour_pilotage", 2210, 0f, 360f, 1080f, 600f,
            "Coupe jusqu'au terrain", "Le relief entre vous et l'aérodrome choisi, avec votre plané. Un obstacle sur la route apparaît en rouge.",
            "Profile to the airfield", "The terrain between you and the chosen airfield, with your glide. An obstacle on the way shows in red."),
        TourStep(TourPart.PILOTAGE, "tour_pilotage", 2210, 680f, 15f, 1060f, 210f,
            "Finesse", "Choisissez la finesse des calculs. Plus elle est basse, plus la marge est prudente.",
            "Glide ratio", "Pick the glide ratio used in the computations. The lower it is, the more cautious the margin."),
        TourStep(TourPart.PILOTAGE, "tour_terrain", 2210, 290f, 210f, 1050f, 1520f,
            "Choix du terrain", "Touchez le terrain pour en changer. AUTO : votre club, sinon le meilleur terrain rejoignable. En rouge : hors de portée.",
            "Airfield choice", "Tap the airfield to change it. AUTO: your club, otherwise the best reachable field. Red: out of reach."),
        TourStep(TourPart.PILOTAGE, "tour_pilotage", 2210, 20f, 625f, 285f, 745f,
            "Orientation de la carte", "1er bouton : carte qui suit le planeur (AUTO) ou libre. 2e bouton : AUTO, nord en haut (N↑) ou route en haut (RTE).",
            "Map orientation", "1st button: map following the glider (AUTO) or free. 2nd button: AUTO, north up (N↑) or track up (RTE)."),
        TourStep(TourPart.PILOTAGE, "tour_pilotage", 2210, 20f, 1505f, 175f, 1710f,
            "Origine des données", "GPS : position. BARO : baromètre du téléphone pour l'altitude et le vario. DATA : réseau (météo, trafic). Point vert = actif.",
            "Data sources", "GPS: position. BARO: phone barometer for altitude and vario. DATA: network (weather, traffic). Green dot = active."),
        TourStep(TourPart.PILOTAGE, "tour_pilotage", 2210, 440f, 1320f, 640f, 1520f,
            "Pompes partagées", "Les planeurs qui spiralent autour de vous (réseau OGN) révèlent les pompes : un cercle coloré selon la montée.",
            "Shared thermals", "Gliders circling around you (OGN network) reveal thermals: a circle coloured by climb rate."),
        TourStep(TourPart.PILOTAGE, "tour_pilotage", 2210, 0f, 1830f, 920f, 2030f,
            "Vario", "Baromètre + accéléromètre. Vert en descente, orange puis rouge en montée. Moyennes spirale, pompe et jour. Haut-parleur : son du vario.",
            "Vario", "Barometer + accelerometer. Green when sinking, orange then red when climbing. Circle, thermal and day averages. Speaker: vario sound."),
        TourStep(TourPart.PILOTAGE, "tour_pilotage", 2210, 930f, 1930f, 1045f, 2020f,
            "Replier le vario", "La flèche replie le vario pour agrandir la carte.",
            "Fold the vario", "The arrow folds the vario to enlarge the map."),
        TourStep(TourPart.MES_VOLS, "tour_rec", 2210, 460f, 1720f, 815f, 1805f,
            "Traces automatiques", "Avec la détection du décollage, la trace s'enregistre dès que le planeur vole et s'arrête à l'atterrissage, tant que l'app reste ouverte. Sinon : interrupteur REC.",
            "Automatic tracks", "With take-off detection, the track records as soon as the glider flies and stops after landing, as long as the app stays open. Otherwise: REC switch."),
        TourStep(TourPart.MES_VOLS, "tour_vols", 2210, 20f, 1520f, 1060f, 2030f,
            "Votre carnet", "Chaque vol est rangé dans Mes vols, sur votre téléphone.",
            "Your logbook", "Every flight is stored in My flights, on your phone."),
        TourStep(TourPart.MES_VOLS, "tour_3d", 2210, 120f, 560f, 980f, 1480f,
            "Revoir en 3D", "Ouvrez un vol puis « Rejeu 3D » : votre trace sur le relief, spirales comprises.",
            "Replay in 3D", "Open a flight, then « Rejeu 3D »: your track over the terrain, thermals included."),
    )

    /**
     * Tutoriel iOS du nouveau design (JB, 29/10/2026) : deux fois moins d'étapes (8 au lieu de 16), sans le rejeu,
     * ouvert seulement depuis l'onglet Tuto. Captures `tour2_*_fr` / `tour2_*_en` (1080 × 2348) : l'image suit la langue.
     */
    val stepsIos: List<TourStep> = listOf(
        TourStep(TourPart.PREVOL, "tour2_prevol", 2348, 85f, 783f, 387f, 869f,
            "Carte hors ligne", "Touchez Télécharger : relief et terrains de votre région, pour une marge juste même sans réseau. « Mettre à jour » quand les données aéro sont périmées.",
            "Offline map", "Tap Download: terrain and airfields of your region, for a correct margin even without network. “Update” when the aero data is out of date.",
            rectEn = TourRect(85f, 809f, 285f, 895f)),
        TourStep(TourPart.PREVOL, "tour2_prevol", 2348, 42f, 1059f, 1038f, 1530f,
            "Appairez votre planeur", "Saisissez l'immatriculation du planeur, puis Appairer : son FLARM est retrouvé dans la base OGN et votre planeur n'apparaît pas comme un trafic.",
            "Pair your glider", "Enter the glider's registration, then Pair: its FLARM is found in the OGN database and your glider is not shown as traffic.",
            rectEn = TourRect(42f, 1086f, 1038f, 1555f)),
        TourStep(TourPart.PILOTAGE, "tour2_pilotage", 2348, 23f, 178f, 787f, 460f,
            "Marge de sécurité", "L'altitude en plus pour rejoindre le terrain à la finesse choisie (F20), avec la marge d'arrivée réglée en Prévol. Orange sous 50 m, rouge quand elle manque.",
            "Safety margin", "The height to spare to reach the airfield at the chosen glide ratio (F20), with the arrival margin set in Pre-flight. Orange under 50 m, red when it is missing."),
        TourStep(TourPart.PILOTAGE, "tour2_pilotage", 2348, 23f, 561f, 139f, 690f,
            "Déplier la coupe", "Touchez la montagne : la coupe du relief jusqu'au terrain se déplie. Elle s'ouvre aussi seule quand la marge devient faible.",
            "Unfold the profile", "Tap the mountain: the terrain profile to the airfield unfolds. It also opens by itself when the margin gets low."),
        TourStep(TourPart.PILOTAGE, "tour2_coupe", 2348, 23f, 559f, 1059f, 875f,
            "Coupe jusqu'au terrain", "Le relief, votre plané et l'arrivée au-dessus du terrain. Touchez la coupe pour la replier dans le bouton montagne.",
            "Profile to the airfield", "The terrain, your glide and the arrival height above the airfield. Tap the profile to fold it back into the mountain button."),
        TourStep(TourPart.PILOTAGE, "tour2_pilotage", 2348, 857f, 559f, 1062f, 1297f,
            "Vario", "La montée en m/s, aux couleurs de la trace : bleu et vert ça monte, jaune à zéro, rouge ça descend. Affichez-le ou masquez-le dans le menu « ⋯ ».",
            "Vario", "Climb rate in m/s, in the track's colours: blue and green climbing, yellow at zero, red sinking. Show or hide it from the “⋯” menu."),
        TourStep(TourPart.PILOTAGE, "tour2_menu", 2348, 26f, 1100f, 700f, 2101f,
            "Options", "Le bouton « ⋯ » en bas à gauche : vario, fond satellite, zones aéronautiques, fréquence radio du terrain, langue et démo.",
            "Options", "The “⋯” button at the bottom left: vario, satellite map, airspace, airfield radio frequency, language and demo.",
            rectEn = TourRect(26f, 1209f, 700f, 2101f)),
        TourStep(TourPart.PILOTAGE, "tour2_pilotage", 2348, 425f, 1996f, 654f, 2101f,
            "Enregistrer la trace", "REC lance et arrête l'enregistrement IGC, même écran éteint. Avec le départ automatique, il démarre seul en vol et s'arrête après l'atterrissage.",
            "Record the track", "REC starts and stops the IGC recording, even with the screen off. With automatic start, it starts by itself in flight and stops after landing."),
    )

    /** Onglet Tuto (TutoHomeScreen) : phrase d'accueil et deux boutons. */
    fun homeIntro(lang: String) = if (lang == "fr") "Découvrez l'app pas à pas, ou dites-nous ce que vous en pensez."
        else "Discover the app step by step, or tell us what you think."
    fun tutorialButton(lang: String) = if (lang == "fr") "Tutoriel" else "Tutorial"
    fun feedbackButton(lang: String) = if (lang == "fr") "Donner mon avis" else "Give feedback"

    /** Libellés de la visite et de la page « Votre avis » (fr / en). */
    fun skip(lang: String) = if (lang == "fr") "Passer" else "Skip"
    fun previous(lang: String) = if (lang == "fr") "Précédent" else "Back"
    fun next(lang: String) = if (lang == "fr") "Suivant" else "Next"
    fun done(lang: String) = if (lang == "fr") "Terminer" else "Done"
    fun review(lang: String) = if (lang == "fr") "Revoir" else "Back"
    fun thatsAll(lang: String) = if (lang == "fr") "C'est tout !" else "That's it!"
    fun feedbackTitle(lang: String) = if (lang == "fr") "Votre avis" else "Your feedback"
    fun feedbackIntro(lang: String) = if (lang == "fr") "Ce qui marche, ce qui manque, ce que vous aimeriez avoir : écrivez-nous."
        else "What works, what's missing, what you'd like to have: write to us."
    fun feedbackPlaceholder(lang: String) = if (lang == "fr") "Votre commentaire…" else "Your comment…"
    fun send(lang: String) = if (lang == "fr") "Envoyer" else "Send"
    fun sending(lang: String) = if (lang == "fr") "Envoi…" else "Sending…"
    fun sent(lang: String) = if (lang == "fr") "Merci ! Message envoyé." else "Thank you! Message sent."
    fun sendFailed(lang: String) = if (lang == "fr") "Envoi impossible (réseau ?). Réessayez plus tard." else "Could not send (network?). Please try again later."
    fun feedbackPrivacy(lang: String) = if (lang == "fr") "Message anonyme, lié à votre compte si vous êtes connecté. Ne mettez pas d'informations personnelles."
        else "Anonymous message, linked to your account if you are signed in. Please don't include personal information."
    /** Longueur maximale d'un commentaire. */
    const val FEEDBACK_MAX = 2000

    /** Clé du profil invité pour le tutoriel obligatoire (un par téléphone, puis un par nouveau compte). */
    const val GUEST = "guest"
}
