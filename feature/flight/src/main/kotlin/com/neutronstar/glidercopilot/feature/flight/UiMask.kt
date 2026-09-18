package com.neutronstar.glidercopilot.feature.flight

/**
 * Masque d'interface (V7.1, demande de JB) : ce qui est retiré de l'écran pour agrandir la carte.
 *
 * **Rien n'est supprimé** — ni le code qui dessine ces éléments, ni les calculs qui les alimentent.
 * Remettre un drapeau à `true` suffit à faire réapparaître l'élément tel qu'il était.
 * Les valeurs masquées restent calculées et restent lues par les tests : c'est un masque d'affichage,
 * pas une amputation.
 */
object UiMask {
    /** Ligne « tendance −1,0 m/s · 2 min : +639 m · calé GPS » sous ALT/SÉCU. */
    const val SHOW_TREND_LINE = false

    /** Barre de titre « Profil de retour au terrain » avec son bouton Réduire/Déployer. */
    const val SHOW_PROFILE_HEADER = false

    /** Légende « COUPE → LFNL · 217° · F20,0 eff. · relief Copernicus » dans la coupe. */
    const val SHOW_PROFILE_CAPTION = false

    /** Graphe des cinq dernières minutes (altitude et sécurité) sous le vario. */
    const val SHOW_ALTITUDE_HISTORY = false

    /**
     * Coupe du relief entre le planeur et le terrain. Gardée : c'est la seule vue du relief franchi.
     * Passer à `false` la retire entièrement et rend encore ~120 dp à la carte.
     */
    const val SHOW_PROFILE_GRAPH = true

    /** Pastille à deux caractères de l'onglet Carte, remplacée par la flèche + immatriculation. */
    const val SHOW_TRAFFIC_DOTS = false
}
